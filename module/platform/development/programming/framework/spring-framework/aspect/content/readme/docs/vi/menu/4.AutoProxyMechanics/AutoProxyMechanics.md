<a id="back-to-top"></a>

# Cơ chế Auto-proxy

## Menu
- [Vai trò BeanPostProcessor trong việc bọc bean bằng proxy](#auto-proxy-mechanics-001)
- [Nhận diện advisor phù hợp với lớp và phương thức đích](#auto-proxy-mechanics-002)
- [Luồng tạo bean đích, tạo proxy và trả tham chiếu cho bên phụ thuộc](#auto-proxy-mechanics-003)
- [Chuỗi interceptor khi một phương thức được gọi qua proxy](#auto-proxy-mechanics-004)
- [Vai trò của auto-proxy creator trong ApplicationContext](#auto-proxy-mechanics-005)
- [Thống nhất cấu hình khi nhiều cơ chế Spring cùng yêu cầu proxy](#auto-proxy-mechanics-006)
- [Bean khởi tạo quá sớm và tình trạng không đủ điều kiện auto-proxy](#auto-proxy-mechanics-007)
- [Lời gọi trong quá trình khởi tạo và giới hạn của @PostConstruct](#auto-proxy-mechanics-008)
- [Phụ thuộc vòng và nguy cơ tham chiếu sớm chưa được tăng cường](#auto-proxy-mechanics-009)
- [Phân biệt proxy AOP với proxy do cơ chế khác trong Spring tạo ra](#auto-proxy-mechanics-010)

## <a id="auto-proxy-mechanics-001">Vai trò BeanPostProcessor trong việc bọc bean bằng proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò của BeanPostProcessor trong việc bọc Spring bean bằng AOP proxy

## 1. Câu hỏi trung tâm

Khi khai báo một aspect bằng `@Aspect`, bật `@EnableAspectJAutoProxy` và lấy service qua `ApplicationContext`, tại sao service nhận được có thể là một **proxy** thay vì đối tượng gốc? Cơ chế nối Spring IoC với Spring AOP chính là **BeanPostProcessor**.

Trong Spring, `BeanPostProcessor` (BPP) là điểm mở rộng cho phép container xử lý **instance của bean** trong quá trình tạo bean. Một BPP có thể trả lại đối tượng đã nhận hoặc trả về một đối tượng khác — chẳng hạn proxy — để container sử dụng làm bean được cung cấp cho các caller khác. Hạ tầng **auto-proxy creator** của Spring AOP triển khai cơ chế đó: nó tìm advisor/advice phù hợp và thay instance được xuất ra ngoài bằng AOP proxy khi cần.

Phạm vi ở đây là cách Spring thực hiện auto-proxying và ý nghĩa của `BeanPostProcessor` trong Spring AOP. Nội dung tập trung vào Spring bean, advisor, proxy và thời điểm xử lý; không trình bày lại lý thuyết AspectJ weaving độc lập.

## 2. BeanPostProcessor can thiệp ở thời điểm nào?

Giao diện tối thiểu:

~~~java
package org.springframework.beans.factory.config;

public interface BeanPostProcessor {
    default Object postProcessBeforeInitialization(Object bean, String beanName)
            throws org.springframework.beans.BeansException {
        return bean;
    }

    default Object postProcessAfterInitialization(Object bean, String beanName)
            throws org.springframework.beans.BeansException {
        return bean;
    }
}
~~~

Đoạn trên là **minh họa hai callback quan trọng**, không phải mã nguồn sao chép nguyên bản của Spring và không phải tệp cần đưa vào project. Spring cung cấp sẵn giao diện này. Từng callback có thể trả cùng instance hoặc một instance thay thế; không phải mọi BPP đều tạo proxy.

Ở luồng tạo bean thông thường, Spring thực hiện gần như sau:

~~~text
BeanDefinition đã được đăng ký trong BeanFactory
        |
        v
Tạo instance target (constructor / factory method)
        |
        v
Populate dependencies / properties
        |
        v
BeanPostProcessor.postProcessBeforeInitialization(...)
        |
        v
Khởi tạo bean: @PostConstruct, InitializingBean, init method
        |
        v
BeanPostProcessor.postProcessAfterInitialization(...)
        |
        |-- không cần advice --> trả bean ban đầu
        |
        +-- có advisor phù hợp --> trả AOP proxy bọc target
                                      |
                                      v
                     Container lưu / cung cấp bean reference
                                      |
                                      v
                        Caller -> proxy -> advice -> target
~~~

Đây là **mô hình chuẩn đã đơn giản hóa**, không phải khẳng định mọi bean luôn trải qua mọi nhánh theo cùng cách. Spring có các kiểu `InstantiationAwareBeanPostProcessor` và `SmartInstantiationAwareBeanPostProcessor` hỗ trợ hook sớm; `FactoryBean`, lazy bean và dependency cycle có thể khiến đường đi thực tế khác sơ đồ. Với bài cơ bản, hãy nhớ rằng **Spring AOP thường tạo proxy khi xử lý bean lúc kết thúc giai đoạn khởi tạo**, trước khi caller bình thường nhận reference đó.

Các điểm dễ nhầm:

- `BeanPostProcessor` xử lý **đối tượng bean đã được tạo**. `BeanFactoryPostProcessor`/`BeanDefinitionRegistryPostProcessor` xử lý cấu hình/định nghĩa bean trước giai đoạn tạo instance. Hệ thống auto-proxy có bước đăng ký định nghĩa hạ tầng, nhưng thao tác **bọc instance bằng proxy** xảy ra ở BPP.
- Callback `postProcessBeforeInitialization` thường phù hợp để xử lý trước init. Auto-proxy creator của Spring AOP chủ yếu dùng `postProcessAfterInitialization` để trả về proxy khi xác định bean cần advice.
- Khi một BPP trả về proxy, bean trong container được tiêu thụ bằng **reference proxy**; target thường vẫn là đối tượng khác mà proxy ủy quyền đến. Do đó `service.getClass()` có thể là lớp proxy, và `service == target` không phải giả định an toàn.
- Trả `null` từ một callback BPP không có nghĩa “bỏ qua bean”; trong quy trình xử lý BPP của Spring, việc này có thể làm dừng chuỗi processor tại giai đoạn ấy. Trong BPP tùy biến, thông thường **trả lại `bean`** nếu không cần thay đổi.

## 3. Auto-proxy creator: BeanPostProcessor chuyên dụng của Spring AOP

Với cấu hình `@EnableAspectJAutoProxy`, Spring đăng ký hạ tầng auto-proxy creator. Một triển khai quan trọng là `org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator`, kế thừa nhánh `AbstractAutoProxyCreator` và triển khai giao diện `SmartInstantiationAwareBeanPostProcessor` (bản mở rộng của `BeanPostProcessor`).

Trong Spring AOP sử dụng kiểu khai báo `@AspectJ`, auto-proxy creator thực hiện các nhiệm vụ:

1. **Nhận diện aspect/advisor:** lấy các Spring `Advisor` và chuyển các advice khai báo bằng `@Aspect`, `@Before`, `@Around`… từ aspect bean thành advisor mà Spring AOP có thể sử dụng.
2. **Xác định bean cần proxy:** xét từng bean đủ điều kiện, so khớp candidate advisor/pointcut với kiểu bean và các method join point Spring AOP hỗ trợ. Một bean không có advisor áp dụng thì thường được giữ nguyên.
3. **Bỏ qua lớp hạ tầng:** tránh proxy hóa các thành phần AOP nội bộ, advisor/advice/aspect không phù hợp hoặc bean bị loại theo quy tắc xử lý. Không phải cứ có `@EnableAspectJAutoProxy` là **mọi** bean đều được bọc.
4. **Tạo proxy với advisor thích hợp:** sử dụng cơ chế proxy của Spring (JDK dynamic proxy hoặc CGLIB class proxy, tùy cấu hình và kiểu đích). Proxy chứa cách điều phối advice cho lời gọi method.
5. **Trả về proxy thay target** qua đường đi của BeanPostProcessor (thường là `postProcessAfterInitialization`). Spring container và dependency injection sẽ cung cấp reference đã qua xử lý cho các bean khác.

### Chuỗi lớp có ích khi đọc mã nguồn Spring

~~~text
BeanPostProcessor
        ^
SmartInstantiationAwareBeanPostProcessor
        ^
AbstractAutoProxyCreator
        ^
AbstractAdvisorAutoProxyCreator
        ^
AspectJAwareAdvisorAutoProxyCreator
        ^
AnnotationAwareAspectJAutoProxyCreator
~~~

Mũi tên thể hiện quan hệ mở rộng/triển khai phục vụ giải thích kiến trúc; `AnnotationAwareAspectJAutoProxyCreator` là lớp Spring hỗ trợ kiểu aspect `@AspectJ`. Từ góc nhìn method nội bộ, hãy chú ý:

- `AbstractAutoProxyCreator.postProcessAfterInitialization(...)`: điểm xử lý hậu khởi tạo, có thể bọc một bean bằng proxy.
- `wrapIfNecessary(...)`: xem xét bean có cần tạo proxy hay không.
- `getAdvicesAndAdvisorsForBean(...)` và các phương thức tìm advisor trong nhánh `AbstractAdvisorAutoProxyCreator`: chọn advice/advisor có thể áp dụng.
- `createProxy(...)`: tạo AOP proxy với cấu hình và advisor đã chọn.
- `getEarlyBeanReference(...)`: đường xử lý tham chiếu sớm cho một số tình huống vòng phụ thuộc; xem phần lưu ý bên dưới.

Tên các method này phục vụ **đọc hiểu/debug framework**, không phải API mà ứng dụng bình thường cần gọi hoặc override. Không nên tự kế thừa `AbstractAutoProxyCreator` chỉ để bật một `@Before` advice đơn giản.

## 4. Từ @EnableAspectJAutoProxy đến lời gọi runtime

Đăng ký cấu hình:

~~~java
@Configuration
@EnableAspectJAutoProxy
class AopConfig {
}
~~~

Trong Java configuration, `@EnableAspectJAutoProxy` dùng cơ chế import/registrar của Spring để đăng ký auto-proxy creator. Khi container chuẩn bị BeanPostProcessor, auto-proxy creator được quản lý như một processor chuyên dụng. Aspect cũng phải có trong `ApplicationContext` dưới dạng bean, ví dụ `@Component` (với component scanning) hoặc `@Bean`. **Chỉ thêm `@Aspect` vào một class thường không tự đăng ký bean.**

Sau đó:

~~~text
@EnableAspectJAutoProxy
   |
   +--> đăng ký auto-proxy creator trong Spring context
                      |
       bean checkoutService được khởi tạo
                      |
                      v
          BeanPostProcessor chain
                      |
                      v
          Xét các Advisor phù hợp
                      |
                  +---+---+
                  |       |
                Có       Không
                  |       |
               Proxy     Target gốc
                  |
               Container trao proxy cho caller
                  |
             caller.checkout()
                  |
              interceptor chain
                  |
              @Before advice
                  |
              target.checkout()
~~~

Cần tách hai **thời điểm**:

- **Lúc tạo bean:** BPP quyết định tạo wrapper nào để Spring cung cấp cho caller.
- **Lúc gọi method qua proxy:** interceptor/advice trong wrapper được thực thi ở các join point khớp. BPP **không cần chạy lại mỗi lần method được gọi**. Nếu một singleton service được gọi 1.000 lần, không suy ra BPP cũng bọc service lại 1.000 lần.

Proxy từ Spring AOP không biến bytecode service thành code advice theo AspectJ weaving; method invocation đi qua đối tượng proxy. Điều này giải thích vì sao `this.otherMethod()` gọi trực tiếp trên target có thể bỏ qua advice trên method nội bộ.

## 5. Project Java 17+ quan sát BeanPostProcessor và proxy

Ví dụ chạy độc lập bằng **Spring Framework 6.2.0, AspectJ Weaver 1.9.22, Maven và Java 17+**. Dự án chỉ khởi tạo một `ApplicationContext`, không dùng Spring Boot và không bật weaving.

Cấu trúc:

~~~text
spring-auto-proxy-bpp/
├── pom.xml
└── src/main/java/example/
    ├── App.java
    ├── CheckoutService.java
    └── AuditAspect.java
~~~

Tạo `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-auto-proxy-bpp</artifactId>
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

`spring-context` chứa lifecycle/container, `spring-aop` chứa hạ tầng AOP/proxy và `AopUtils`. `aspectjweaver` cung cấp các kiểu AspectJ dùng để phân tích pointcut `@AspectJ` trong Spring AOP. **Dependency này không tự kích hoạt AspectJ bytecode weaving.**

### 5.1. CheckoutService có bước init rõ ràng

Tạo `src/main/java/example/CheckoutService.java`:

~~~java
package example;

import org.springframework.beans.factory.InitializingBean;

public class CheckoutService implements InitializingBean {
    @Override
    public void afterPropertiesSet() {
        System.out.println("INIT: checkoutService");
    }

    public void checkout() {
        System.out.println("TARGET: checkout");
    }
}
~~~

`afterPropertiesSet()` là lifecycle callback sau khi dependency/properties đã được gắn vào bean. Dòng `INIT` giúp nhìn thấy ranh giới giữa các giai đoạn BeanPostProcessor. `checkout()` là business method sẽ được advice, không phải method init.

### 5.2. Aspect sẽ chạy khi gọi checkout qua proxy

Tạo `src/main/java/example/AuditAspect.java`:

~~~java
package example;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    @Before("execution(* example.CheckoutService.checkout(..))")
    public void beforeCheckout() {
        System.out.println("ADVICE: before checkout");
    }
}
~~~

Spring dùng `@Aspect`/`@Before` để tạo advisor. `execution(* example.CheckoutService.checkout(..))` khớp **method execution** của `checkout`. Aspect chỉ chạy khi method qua Spring AOP proxy khớp advisor đó; không phải vì BeanPostProcessor quan sát tự gọi advice.

### 5.3. Cấu hình hai loại BeanPostProcessor khác nhau

Tạo `src/main/java/example/App.java`:

~~~java
package example;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class App {
    @Bean
    public CheckoutService checkoutService() {
        return new CheckoutService();
    }

    @Bean
    public AuditAspect auditAspect() {
        return new AuditAspect();
    }

    @Bean
    public static BeanPostProcessor lifecycleObserver() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(
                    Object bean, String beanName) {
                if ("checkoutService".equals(beanName)) {
                    System.out.println("BPP_BEFORE: checkoutService");
                }
                return bean;
            }

            @Override
            public Object postProcessAfterInitialization(
                    Object bean, String beanName) {
                if ("checkoutService".equals(beanName)) {
                    System.out.println("BPP_AFTER: checkoutService");
                }
                return bean;
            }
        };
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(App.class)) {
            CheckoutService service = context.getBean(CheckoutService.class);
            System.out.println("PROXY: " + AopUtils.isAopProxy(service));
            service.checkout();
        }
    }
}
~~~

Trong cùng một `ApplicationContext` có hai loại processor với vai trò **khác nhau**:

1. `lifecycleObserver()` trả về **BPP do ta viết** để in dấu mốc lifecycle. Nó trả nguyên `bean` ở cả hai callback, **không tự tạo proxy**. Vì Spring `ApplicationContext` phát hiện bean `BeanPostProcessor` và gọi nó trong quá trình tạo những bean phù hợp, observer nhìn thấy quá trình khởi tạo `checkoutService`.
2. `@EnableAspectJAutoProxy` đăng ký **auto-proxy creator nội bộ của Spring**, cũng là BeanPostProcessor. Processor này tìm advisor sinh từ `AuditAspect` và bọc `CheckoutService` bằng AOP proxy khi có method phù hợp.

Đặt `@Bean` của BPP tùy biến ở dạng **`public static`, không có dependency** để Spring phát hiện/đăng ký sớm mà không phải tạo instance configuration class trước hạn. Đây là thực hành quan trọng: các BPP cần hoạt động đủ sớm để xử lý các bean được tạo sau chúng. Khai báo BPP bằng `@Bean` có return type `BeanPostProcessor` (hoặc lớp triển khai) giúp container nhận diện đúng.

Trong ví dụ, `proxyTargetClass = true` chọn **class-based proxy** (CGLIB), nên `context.getBean(CheckoutService.class)` trả kiểu có thể gán cho `CheckoutService`. Thuộc tính `proxyBeanMethods = false` trên `@Configuration` chỉ quyết định cách xử lý các phương thức `@Bean` của configuration class; nó **không vô hiệu hóa auto-proxy AOP**.

### 5.4. Chạy và giải thích stdout

Trong thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng mà chương trình tạo ra:

~~~text
BPP_BEFORE: checkoutService
INIT: checkoutService
BPP_AFTER: checkoutService
PROXY: true
ADVICE: before checkout
TARGET: checkout
~~~

Các thông điệp Maven/logger khác, nếu có, không thuộc sáu dòng từ mã ví dụ. Thứ tự trên phản ánh lifecycle của `CheckoutService` ở project đã cho:

- `BPP_BEFORE`: callback `postProcessBeforeInitialization` của observer chạy trước `afterPropertiesSet()`.
- `INIT`: Spring thực hiện initialization callback của service.
- `BPP_AFTER`: callback `postProcessAfterInitialization` của observer chạy sau initialization callback. **Dòng này không tự chứng minh bean đã được bọc**: observer chỉ in tên bean và trả về đối tượng được nhận.
- `PROXY: true`: reference Spring đưa cho caller là Spring AOP proxy, được tạo bởi auto-proxy creator nội bộ nếu phù hợp advisor.
- `ADVICE` trước `TARGET`: lời gọi đi qua proxy, kích hoạt `@Before` rồi chuyển tiếp tới `checkout()` của service.

Chú ý: **không kết luận kiểu của đối tượng trong từng BPP callback chỉ từ tên log**. Các BeanPostProcessor có thứ tự xử lý khác nhau; một BPP tùy biến có thể quan sát target hoặc wrapper tùy thời điểm và thứ tự thực thi. `AopUtils.isAopProxy(context.getBean(...))` trong `main` đo trực tiếp **reference cuối cùng** Spring cung cấp cho caller.

## 6. Vì sao việc “bọc bằng proxy” làm thay đổi hành vi của caller?

Khi auto-proxy creator thấy advisor áp dụng được, về ý tưởng nó trả một wrapper:

~~~text
Trước auto-proxying:
caller --> CheckoutService.checkout()

Sau auto-proxying:
caller --> CheckoutServiceProxy.checkout()
                 |
                 +--> AuditAspect.beforeCheckout()
                 |
                 +--> CheckoutService.checkout()
~~~

Không cần sửa class `CheckoutService` để chèn lệnh gọi `AuditAspect.beforeCheckout()`. Các bean khác trong Spring được inject service thông thường sẽ nhận reference mà container công bố, tức proxy nếu đã được tạo.

Các hệ quả quan trọng:

- **Không phải mọi bean đều là proxy:** nếu pointcut không khớp hoặc không có advisor áp dụng, auto-proxy creator có thể trả target. `@EnableAspectJAutoProxy` chỉ bật cơ chế xét/proxy.
- **Không phải mọi lời gọi đều qua proxy:** `this.checkout()` bên trong target là self-invocation; lời gọi nội bộ có thể bỏ qua advice. Tách responsibility sang bean khác để gọi qua Spring reference là cách xử lý thường dễ hiểu.
- **Không phải mọi method đều intercept được:** class-based proxy không override được `final`/`private` method hoặc `final` class. Với JDK proxy, caller tương tác qua interface proxy; lựa chọn kiểu proxy có ảnh hưởng trực tiếp đến method nào được expose.
- **Initialization không phải invocation business qua proxy:** các callback init thường chạy trên target trong quá trình tạo bean trước khi bean proxy cuối cùng được công bố; đừng xem việc `@Before` không chạy cho method gọi ở init là bằng chứng Spring AOP không hoạt động.
- **Không phải AOP weaving:** Spring tạo đối tượng proxy; AspectJ compile/load-time weaving sửa bytecode theo công cụ riêng. Cú pháp `@Aspect`/`@Before` không biến Spring auto-proxy thành weaving.

## 7. Những trường hợp BPP không áp dụng như mong đợi

### 7.1. BPP được đăng ký quá muộn hoặc tạo phụ thuộc sớm

`ApplicationContext` chủ động tìm và khởi tạo BPP trong giai đoạn đặc biệt trước khi nhiều bean thông thường được tạo. Bản thân các BPP, cùng các bean được chúng kéo vào khởi tạo quá sớm, **không thể mặc định hưởng toàn bộ chuỗi BPP** và có thể không đủ điều kiện auto-proxying. Spring có cảnh báo dạng:

~~~text
Bean '...' is not eligible for getting processed by all BeanPostProcessors
(for example: not eligible for auto-proxying)
~~~

Nếu gặp cảnh báo này:

1. Kiểm tra BPP do ứng dụng tự khai báo có được tạo **sớm** qua static `@Bean` và return type nhận diện được là BPP hay không.
2. Tránh inject dịch vụ nghiệp vụ nặng hoặc tạo graph phụ thuộc lớn trong chính constructor/factory method của BPP; các dependency đó có thể bị khởi tạo trước khi auto-proxy creator sẵn sàng.
3. Không tự gọi `getBean()` để khởi tạo hàng loạt service trong lúc đăng ký BPP. Hãy để container hoàn thành đúng pha khởi tạo.

Không cố advice chính một `BeanPostProcessor` và kết luận Spring AOP hỏng khi nó không nhận proxy như business bean bình thường.

### 7.2. Proxy xuất hiện sớm để hỗ trợ circular references

`AbstractAutoProxyCreator` là `SmartInstantiationAwareBeanPostProcessor` và có `getEarlyBeanReference(...)` để Spring có thể cấp **reference đã bọc** sớm ở một số đường xử lý phụ thuộc vòng. Điều này giúp tránh chuyện một bean nhận target thô trong khi caller khác sau đó nhận proxy.

Tuy vậy, **không dựa vào circular dependencies như một pattern thiết kế**. Constructor cycle thường không giải được theo cách này; trong Spring Boot nhiều cấu hình còn từ chối circular references theo mặc định. Refactor vòng phụ thuộc là giải pháp bền vững. Đừng hiểu rằng BPP luôn được gọi đúng một lần trên mọi bean hoặc việc gọi callback sớm luôn thành công.

### 7.3. Nhiều context và nhiều auto-proxy setting

BPP có phạm vi theo **container**. BPP đăng ký trong một `ApplicationContext` không tự trở thành post-processor cho mọi bean thuộc một context khác, đặc biệt trong mô hình parent/child. Khi advice không chạy ở bean được tạo bởi context con, kiểm tra auto-proxy creator và aspect có được đăng ký trong đúng context chứa bean hay không.

Các khai báo auto-proxy AOP/transaction có thể được Spring hợp nhất về một auto-proxy creator và áp dụng thiết lập proxy mạnh hơn khi cấu hình chồng nhau. Không suy diễn rằng hai annotation đồng nghĩa chắc chắn có hai lớp proxy độc lập. Kiểm tra proxy thực tế qua `AopUtils.isAopProxy(...)` và các advisor nếu cần.

### 7.4. Tự viết BPP để tạo proxy cho mọi bean

Đừng thay `AnnotationAwareAspectJAutoProxyCreator` bằng một BPP tùy biến chỉ để làm logging hoặc method advice thông thường. Việc tự tạo proxy trùng lặp dễ gây double wrapping, thay đổi thứ tự advice, rối lifecycle và làm sai hành vi của transaction/cache. Khi mục tiêu là Spring AOP, hãy khai báo `@Aspect`/advisor và để hạ tầng Spring đảm nhiệm proxy creation.

## 8. Phân biệt nhanh các thành phần liên quan

| Thành phần | Nó xử lý gì? | Vai trò trong Spring AOP |
| --- | --- | --- |
| `BeanDefinitionRegistryPostProcessor` | Registry/định nghĩa bean | Có thể tham gia đăng ký thêm bean definition trước khởi tạo |
| `BeanFactoryPostProcessor` | Cấu hình/metadata của BeanFactory | Chỉnh định nghĩa; không phải bước bọc từng instance bằng proxy |
| `BeanPostProcessor` | Instance của bean quanh các callback init | Điểm mở rộng cho phép trả wrapper/proxy thay bean |
| `SmartInstantiationAwareBeanPostProcessor` | Thêm hook sớm vào quá trình tạo instance | Cho auto-proxy creator khả năng cung cấp proxy reference sớm trong một số trường hợp |
| `AnnotationAwareAspectJAutoProxyCreator` | Bean phù hợp với Advisor/`@AspectJ` | BPP cụ thể tìm advisor, chọn bean và tạo AOP proxy |
| `Advisor` / `Pointcut` / `Advice` | Điều kiện chọn join point và thao tác thực thi | Được auto-proxy creator đưa vào interceptor chain của proxy |
| JDK dynamic proxy / CGLIB | Wrapper runtime | Cơ chế thực thi lời gọi phương thức qua proxy |
| `@EnableAspectJAutoProxy` | Cấu hình bật hỗ trợ AOP kiểu `@AspectJ` | Đăng ký hạ tầng auto-proxy cho context đang cấu hình |

## 9. Chẩn đoán khi service không nhận AOP proxy

Một thứ tự kiểm tra thực tế:

1. **Có đúng bean Spring không?** `new CheckoutService()` không tự đi qua `BeanPostProcessor` của container. Lấy bean từ context đang sở hữu service.
2. **Auto-proxy creator có bật chưa?** Xác nhận `@EnableAspectJAutoProxy` nằm trong configuration đã nạp hoặc Boot AOP auto-configuration đang hoạt động.
3. **Aspect có là bean không?** `@Aspect` không đủ nếu Spring không đăng ký class aspect dưới dạng bean.
4. **Advisor có khớp method không?** Pointcut `execution(...)` phải khớp kiểu và chữ ký của method thực tế. Không khớp thì service có thể được giữ nguyên, không nhất thiết có proxy.
5. **BPP có bị khởi tạo quá muộn không?** Tìm log “not eligible for auto-proxying”, BPP khai báo qua non-static `@Bean` hoặc BPP inject quá nhiều service sớm.
6. **Lời gọi có đi qua proxy không?** Proxy tồn tại nhưng self-invocation hoặc object ngoài container sẽ không kích hoạt advice như lời gọi xuyên bean.
7. **Loại proxy có phù hợp không?** Xem interface/JDK proxy, CGLIB class proxy, `final` class/method, visibility và kiểu bean cần inject.
8. **Có chồng nhiều BPP/proxy/contexts không?** Kiểm tra nơi định nghĩa aspect, advisor và thứ tự BPP. BPP tùy biến cần trả đúng object nhận vào hoặc wrapper hợp lệ.

Để kiểm tra nhanh trong một ứng dụng đã có Spring container:

~~~java
Object bean = context.getBean("checkoutService");
System.out.println(org.springframework.aop.support.AopUtils.isAopProxy(bean));
System.out.println(org.springframework.aop.support.AopUtils.isJdkDynamicProxy(bean));
System.out.println(org.springframework.aop.support.AopUtils.isCglibProxy(bean));
~~~

Đây là **đoạn chèn vào chương trình đang có biến `context`**, không phải file Java độc lập; với ví dụ phần 5, nó trả `true`, `false`, `true` theo thứ tự nếu class-based proxy được cấu hình như đã nêu. Nếu `isAopProxy` là `false`, hãy kiểm tra cả điều kiện proxy/advisor trước khi quy lỗi do BPP.

## 10. Tự kiểm tra hiểu bài

1. **BeanPostProcessor thay đổi cái gì?** — Nó xử lý instance bean; có thể trả wrapper thay instance gốc sau khởi tạo.
2. **Spring AOP dựa vào BPP nào để tự động tạo proxy cho `@AspectJ`?** — Nhóm auto-proxy creator, tiêu biểu `AnnotationAwareAspectJAutoProxyCreator`.
3. **`@EnableAspectJAutoProxy` làm mọi bean thành proxy?** — Không. Bean cần phù hợp với advisor và cơ chế proxy.
4. **Vì sao `BPP_AFTER` không tương đương `ADVICE`?** — BPP chạy trong vòng đời tạo bean, còn advice chạy khi một method qua proxy khớp pointcut.
5. **Vì sao `@Bean static BeanPostProcessor` quan trọng?** — Giúp Spring phát hiện, khởi tạo BPP đủ sớm mà hạn chế tạo sớm configuration/dependencies.
6. **Đã có proxy sao `this.checkout()` có thể không chạy advice?** — Lời gọi nội bộ bỏ qua wrapper Spring AOP.
7. **Khi nào nên tránh sửa lõi auto-proxy creator?** — Khi yêu cầu chỉ là logging/audit/transaction thông thường, hãy dùng aspect/advisor chuẩn và để Spring quản lý proxy.

## Tài liệu Spring Framework để tra cứu

- [Container Extension Points — BeanPostProcessor](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-extension.html): lifecycle BPP và các lưu ý auto-proxy eligibility.
- [Using the auto-proxy facility](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/autoproxy.html): các auto-proxy creator trong Spring AOP.
- [Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html): cách bật auto-proxy cho aspect.
- [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK proxy, CGLIB và giới hạn self-invocation.
- [AnnotationAwareAspectJAutoProxyCreator API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/aspectj/annotation/AnnotationAwareAspectJAutoProxyCreator.html) và [AbstractAutoProxyCreator API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/framework/autoproxy/AbstractAutoProxyCreator.html): lớp/callback hạ tầng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="auto-proxy-mechanics-002">Nhận diện advisor phù hợp với lớp và phương thức đích</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện advisor phù hợp với lớp và phương thức đích trong Spring AOP

## 1. Bài toán mà Spring auto-proxy phải giải quyết

Trong một ứng dụng Spring, có thể tồn tại nhiều **advisor** từ các `@Aspect` và từ những thành phần AOP khác, nhưng không phải advisor nào cũng áp dụng lên mọi bean và mọi phương thức. Spring cần trả lời hai câu hỏi khác nhau:

1. **Khi chuẩn bị một Spring bean:** những advisor nào *có khả năng* tác động đến ít nhất một phương thức của lớp đích? Nếu có advisor phù hợp và không bị các quy tắc loại trừ khác ngăn cản, bean có thể được bọc bằng AOP proxy.
2. **Khi client gọi phương thức qua proxy:** trong các advisor đã gắn với proxy, advisor nào thực sự khớp phương thức đang gọi và — nếu pointcut yêu cầu — **đối số runtime** của chính lời gọi đó?

Ví dụ: một `OrderService` có ba phương thức `checkout()`, `status()`, `cancel()`, nhưng advisor chỉ áp dụng cho `checkout()`. Spring có thể proxy **bean OrderService**, trong khi chỉ **lời gọi `checkout()`** đi qua advice của advisor ấy. Sự tồn tại của proxy không đồng nghĩa mọi method đều chạy mọi advice.

Đây là bài về **cơ chế nhận diện advisor trong Spring AOP proxy-based**; tập trung vào Spring auto-proxy, class/method matching, điểm quyết định tạo proxy và thời điểm chạy advice. Kiến thức Aspect tổng quát không được lặp lại.

## 2. Các thành phần tham gia

| Thành phần trong Spring | Chức năng đối với bước matching |
| --- | --- |
| `Advisor` (`org.springframework.aop.Advisor`) | Đại diện cho advice và thông tin bổ sung cần để sử dụng advice. |
| `PointcutAdvisor` | Một dạng advisor có `Pointcut` để xác định **nơi áp dụng advice**. |
| `Pointcut` | Cung cấp hai phần: `ClassFilter` để xét lớp đích, `MethodMatcher` để xét phương thức và đôi khi đối số runtime. |
| `ClassFilter` | Trả lời một lớp `Class<?>` có nằm trong phạm vi pointcut không. |
| `MethodMatcher` | Trả lời method cụ thể trên target class có thể khớp không, và có phải kiểm tra thêm từng lời gọi không. |
| `AopUtils.canApply(...)` | Tiện ích Spring AOP để đánh giá một pointcut/advisor **có thể áp dụng** cho một target class hay không. |
| `AopUtils.findAdvisorsThatCanApply(...)` | Lọc danh sách advisor ứng viên để lấy những advisor có khả năng áp dụng cho target class. |
| `AnnotationAwareAspectJAutoProxyCreator` | Một auto-proxy creator của Spring nhận diện cả advisor bean và advice khai báo trong các Spring-managed `@Aspect` bean. |
| `AopProxy` | Proxy JDK hoặc class-based proxy (CGLIB) thực thi chuỗi interceptor khi một lời gọi đi qua nó. |

Định nghĩa API chính của Spring (chỉ minh họa chữ ký; các interface thật có thêm chi tiết khác):

```java
interface Pointcut {
    ClassFilter getClassFilter();
    MethodMatcher getMethodMatcher();
}

interface ClassFilter {
    boolean matches(Class<?> clazz);
}

interface MethodMatcher {
    boolean matches(Method method, Class<?> targetClass);
    boolean isRuntime();
    boolean matches(Method method, Class<?> targetClass, Object... args);
}
```

Phân biệt rõ:

- `ClassFilter.matches(...)` xét **lớp đích**; chưa khẳng định một phương thức cụ thể sẽ nhận advice.
- `MethodMatcher.matches(method, targetClass)` là bước **kiểm tra tĩnh**, có thể thực hiện trước khi lời gọi chạy.
- Nếu `isRuntime()` trả về `true`, Spring còn có thể gọi `matches(method, targetClass, args)` cho từng lần gọi, vì kết quả còn phụ thuộc đối số thực tế.
- Kết quả `canApply(advisor, targetClass) == true` nghĩa là advisor **có khả năng áp dụng cho một phần của lớp**, chưa chứng minh lời gọi hiện tại chắc chắn kích hoạt advice.

Spring có thể tối ưu/cache các phần matching tĩnh. Không nên hiểu rằng Spring luôn quét lại toàn bộ các advisor beans cho mọi lời gọi.

## 3. Spring chọn advisor như thế nào khi tạo bean proxy?

Sơ đồ dưới là luồng khái niệm dành cho cấu hình Spring AOP có auto-proxy. Tên class/method giúp định hướng khi đọc source Spring; đây **không phải cam kết rằng mọi phiên bản Spring luôn chạy đúng từng bước nội bộ theo một thứ tự bất biến**.

```text
ApplicationContext có Spring AOP auto-proxy creator
        |
        +-- Advisor beans đã đăng ký
        |
        +-- @Aspect beans đã đăng ký
                  -> chuyển các advice @AspectJ thành advisors
        |
        v
Danh sách candidate advisors
        |
        v
Bean đích đang được xem xét: tên bean + target class
        |
        +-- Bỏ qua đối tượng không thuộc phạm vi auto-proxy
        |
        +-- Lọc advisor có khả năng áp dụng cho target class:
        |      ClassFilter + MethodMatcher (kiểm tra tĩnh)
        |
        +-- Sắp xếp / hoàn thiện các advisor phù hợp
        |
        +-- Nếu có advisor phù hợp và đủ điều kiện proxy:
        |      tạo AOP proxy cho bean đích
        |
        +-- Nếu không có advisor phù hợp:
               thường trả về bean gốc, không tạo proxy vì advisor đó
        |
        v
Client gọi method qua proxy
        |
        +-- Chọn interceptor chain cho method
        +-- Kiểm tra điều kiện runtime nếu pointcut yêu cầu
        +-- Chạy advice khớp rồi gọi target method
```

Trong họ `AbstractAdvisorAutoProxyCreator`, các tên phương thức có ích khi cần đọc source hoặc debug là:

```text
findCandidateAdvisors()
findEligibleAdvisors(beanClass, beanName)
findAdvisorsThatCanApply(candidateAdvisors, beanClass, beanName)
sortAdvisors(...)
```

Với hỗ trợ `@AspectJ`, Spring còn đọc advice trên **aspect beans** để đưa chúng vào danh sách advisor ứng viên. Các bước này diễn ra trong vòng đời tạo bean/post-processing của Spring, trước khi caller thông thường nhận tham chiếu proxy.

### 3.1. Candidate advisor khác eligible advisor

- **Candidate advisor** là advisor đã được Spring tìm thấy từ cấu hình AOP, có thể nhắm tới nhiều bean khác nhau.
- **Eligible advisor** là advisor có khả năng áp dụng cho **lớp bean đang xét**, dựa vào pointcut và các điều kiện Spring liên quan.
- **Advice thực thi tại lời gọi** là lớp hẹp hơn nữa: chỉ những interceptor/advice match với method (và điều kiện runtime) của invocation mới chạy.

Một advisor cho `OrderService.checkout()` vẫn là candidate khi Spring đang xét `InvoiceService`, nhưng không trở thành eligible cho lớp không có method phù hợp với biểu thức đó.

### 3.2. Đừng nhầm lọc theo lớp với lọc theo phương thức

Giả sử pointcut là:

```java
@Before("execution(* example.OrderService.checkout(..))")
```

Với `OrderService` chứa cả `checkout()` và `status()`:

- **Ở cấp bean/class:** có ít nhất một method được pointcut nhắm đến, nên advisor *có thể* áp dụng lên bean.
- **Ở cấp method:** `checkout()` khớp, `status()` không khớp.
- **Ở cấp call:** lời gọi `checkout()` phải đi **qua proxy** và thỏa các điều kiện thực thi mới nhận advice.

Spring AOP không tự chèn advice vào mọi phương thức của `OrderService` chỉ vì proxy đã tồn tại.

## 4. Ví dụ Java 17+ chạy được: một bean có proxy, một bean không có

Ví dụ Spring Framework thuần với `@EnableAspectJAutoProxy`. Dùng **Spring Framework 6.2.19** và **AspectJ Weaver 1.9.25.1**. Bản mẫu ngắn này minh họa đồng thời: cách Spring xác định lớp có advisor phù hợp, cách match method và kết quả gọi qua proxy.

### 4.1. Cấu trúc dự án

```text
advisor-matching-demo/
├── pom.xml
└── src/main/java/example/
    ├── AppConfig.java
    ├── AuditAspect.java
    ├── OrderService.java
    ├── InvoiceService.java
    └── Main.java
```

**`pom.xml`**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>advisor-matching-demo</artifactId>
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

**`OrderService.java`**

```java
package example;

public class OrderService {
    public void checkout() {
        System.out.println("order: checkout");
    }

    public void status() {
        System.out.println("order: status");
    }
}
```

**`InvoiceService.java`**

```java
package example;

public class InvoiceService {
    public void checkout() {
        System.out.println("invoice: checkout");
    }
}
```

**`AuditAspect.java`**

```java
package example;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    @Before("execution(* example.OrderService.checkout(..))")
    public void beforeCheckout() {
        System.out.println("audit: before checkout");
    }
}
```

**`AppConfig.java`**

```java
package example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy
public class AppConfig {
    @Bean
    public OrderService orderService() {
        return new OrderService();
    }

    @Bean
    public InvoiceService invoiceService() {
        return new InvoiceService();
    }

    @Bean
    public AuditAspect auditAspect() {
        return new AuditAspect();
    }
}
```

**`Main.java`**

```java
package example;

import java.lang.reflect.Method;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) throws Exception {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            var order = context.getBean(OrderService.class);
            var invoice = context.getBean(InvoiceService.class);

            System.out.println("order proxy: " + AopUtils.isAopProxy(order));
            System.out.println("invoice proxy: " + AopUtils.isAopProxy(invoice));

            var pointcut = new AspectJExpressionPointcut();
            pointcut.setExpression(
                "execution(* example.OrderService.checkout(..))"
            );

            System.out.println("order class eligible: "
                + AopUtils.canApply(pointcut, OrderService.class));
            System.out.println("invoice class eligible: "
                + AopUtils.canApply(pointcut, InvoiceService.class));

            Method checkout = OrderService.class.getMethod("checkout");
            Method status = OrderService.class.getMethod("status");
            System.out.println("checkout matches: "
                + pointcut.getMethodMatcher().matches(checkout, OrderService.class));
            System.out.println("status matches: "
                + pointcut.getMethodMatcher().matches(status, OrderService.class));

            order.checkout();
            order.status();
            invoice.checkout();
        }
    }
}
```

Tại thư mục có `pom.xml`, chạy:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.Main
```

Những dòng do chương trình in ra theo thứ tự:

```text
order proxy: true
invoice proxy: false
order class eligible: true
invoice class eligible: false
checkout matches: true
status matches: false
audit: before checkout
order: checkout
order: status
invoice: checkout
```

### 4.2. Phân tích kết quả theo từng tầng

1. `AuditAspect` được đăng ký là Spring bean qua `@Bean`; `@EnableAspectJAutoProxy` bật Spring AOP. `@Aspect` một mình không thay được bước đăng ký bean.
2. Spring thấy advisor từ `@Before` có pointcut khớp `OrderService.checkout()`. `OrderService` có advisor phù hợp nên Spring tạo proxy cho bean đó.
3. `InvoiceService` cũng có một method tên `checkout()`, nhưng **không phải class `example.OrderService`** nên pointcut trong ví dụ không match; `invoice proxy: false` là kết quả khi không có advisor nào khác áp dụng lên nó.
4. `AopUtils.canApply(pointcut, OrderService.class)` cho thấy ở cấp class tồn tại ít nhất một method có khả năng khớp. Kết quả `true` **không có nghĩa** toàn bộ method trên lớp đều khớp.
5. `getMethodMatcher().matches(checkout, OrderService.class)` là `true`, còn với `status()` là `false`.
6. Lời gọi `order.checkout()` qua bean proxy chạy advice, rồi đến method nghiệp vụ. Lời gọi `order.status()` vẫn đi qua cùng proxy nhưng **không có advice tương ứng**. `invoice.checkout()` gọi bean InvoiceService không có proxy trong cấu hình này, nên cũng không có dòng audit.

Ở bước chẩn đoán, `AspectJExpressionPointcut` được tạo thêm để minh họa phép kiểm tra tĩnh theo **cùng biểu thức** với `@Before`; đây là đối tượng kiểm tra độc lập, không phải chính advisor object đang nằm trong proxy. Không nên đồng nhất kết quả `AopUtils.canApply` với việc truy xuất danh sách advisor đã cài đặt lên một proxy cụ thể.

## 5. Quy tắc lọc tĩnh và động trong `MethodMatcher`

### 5.1. Static matching

Một method matcher không cần dữ liệu từng lời gọi có thể quyết định dựa trên method signature và target class. Với pointcut:

```text
execution(* example.OrderService.checkout(..))
```

Spring đủ dữ liệu để nhận diện sự phù hợp của phương thức `checkout()` từ lớp/method. Việc xét kiểu và method trước khi xây proxy giúp tránh làm các phép kiểm tra không cần thiết trên từng invocation.

Trong Spring AOP API, `MethodMatcher.matches(Method, Class)` được dùng để kiểm tra một method **có thể** khớp; `isRuntime() == false` nghĩa là không có bước `matches(Method, Class, Object... args)` trên mỗi lời gọi.

### 5.2. Dynamic matching: điều kiện phụ thuộc đối số thực tế

Một số pointcut phụ thuộc dữ liệu lúc chạy, chẳng hạn ràng buộc `args(...)`. Ví dụ sau chỉ là **đoạn minh họa bổ sung**, không thuộc chương trình độc lập ở mục 4:

```java
// Trong một target bean có phương thức:
public void handle(Object value) {
    System.out.println("handling: " + value);
}
```

Aspect tương ứng:

```java
// Bên trong class @Aspect đã được Spring đăng ký bean:
@Before("execution(* example.OrderService.handle(..)) && args(java.lang.String)")
public void beforeStringValue() {
    System.out.println("argument is String");
}
```

Với phương thức có tham số khai báo là `Object`, `handle("A")` có đối số runtime là `String`, còn `handle(42)` có đối số runtime là `Integer`. Nếu cả hai lời gọi đi qua Spring proxy, phần điều kiện `args(java.lang.String)` có thể chọn chỉ lời gọi đầu tiên. **Một method được chọn làm ứng viên lúc tạo proxy chưa chắc mọi lần gọi đều thỏa điểm cắt.**

Cần phân biệt:

- `execution(...)` thường mô tả method signature và loại method join point.
- `args(...)` liên quan loại đối số **thực tế** lúc gọi và có thể yêu cầu runtime test.
- `@args(...)` liên quan annotation trên kiểu runtime của đối số, khác với `@annotation(...)` trên **phương thức** đang thực thi.
- Nếu pointcut gồm phép `&&`, các phần ràng buộc cùng phải thỏa; với `||`, một nhánh khớp có thể đủ.

Không nên tự suy ra mọi pointcut chứa `args` đều nhất thiết mất đúng một phép so khớp động trong mọi tối ưu nội bộ; hãy dùng `MethodMatcher.isRuntime()` khi thực sự cần khảo sát một instance matcher.

## 6. Những biểu thức Spring AOP thường dùng và ảnh hưởng lên matching

Các biểu thức dưới đây là **mẫu để đặt trong annotation advice như `@Before("...")`**, với các class/package/annotation thực sự tồn tại trong dự án. Không phải các chương trình Java độc lập.

| Biểu thức | Yếu tố dùng để hạn chế phạm vi |
| --- | --- |
| `execution(* com.acme.order.OrderService.checkout(..))` | Method tên `checkout` trên `OrderService` với bất kỳ số tham số; là phép khớp method execution |
| `execution(public * com.acme.order..*Service.*(..))` | Method `public` của các loại có tên kết thúc `Service` trong package phù hợp |
| `within(com.acme.order..*)` | Method execution nằm trong các loại thuộc package tương ứng |
| `@annotation(com.acme.audit.Audited)` | Method execution mang annotation `@Audited` |
| `@within(com.acme.audit.AuditedType)` | Method execution thuộc kiểu có annotation `@AuditedType` theo ngữ nghĩa PCD |
| `target(com.acme.order.OrderService)` | Target object thuộc loại chỉ định |
| `this(com.acme.order.OrderService)` | **Proxy** đáp ứng kiểu chỉ định; khác với target nếu dùng JDK proxy |
| `args(java.lang.String)` | Kiểu của đối số runtime phù hợp |
| `bean(orderService)` | Spring bean **có tên phù hợp**; `bean()` là Spring-specific PCD |

Một số hệ quả đáng nhớ:

- `execution(* ...)` nhắm tới **method execution** trên bean do Spring quản lý; Spring proxy không hỗ trợ mọi join point của AspectJ như constructor/field access.
- `bean(orderService)` có thể quyết định phạm vi dựa vào **tên bean** khi chọn advisor cho một bean cụ thể. Nó không chỉ là kiểm tra Java class.
- Với `this(...)`, phép khớp dựa trên kiểu **proxy**, còn `target(...)` dựa trên target object; hai kết quả có thể khác nhau khi dùng JDK dynamic proxy.
- Pointcut khớp về biểu thức chưa chắc chạy advice khi lời gọi **không đi qua proxy** hoặc method không thể được proxy can thiệp.

### 6.1. Bean name có thể quyết định khả năng áp dụng

Giả sử hai Spring beans đều trả về instance của cùng một class `OrderService`, nhưng mang tên `importantOrderService` và `backupOrderService`. Với advice:

```java
@Before("bean(importantOrderService) && execution(* *.checkout(..))")
public void beforeImportantCheckout() {
    // ...
}
```

Spring có thể chỉ áp dụng advisor cho bean có tên khớp, dù target class giống nhau. Vì vậy ở giai đoạn chọn advisor của auto-proxy, **bean name cũng có thể là một phần ngữ cảnh cần thiết**. Đừng chỉ gọi `AopUtils.canApply(..., OrderService.class)` rồi kết luận mọi bean cùng class sẽ nhận chính xác danh sách advisor giống nhau: phép thử tiện ích theo `Class` riêng lẻ không mang đầy đủ ngữ cảnh bean name.

## 7. Matching không đảm bảo proxy luôn can thiệp

Một advisor có thể được xác định là phù hợp ở cấp class/method, nhưng lời gọi thực tế vẫn không chạy advice nếu vướng ranh giới proxy Spring AOP.

### 7.1. Self-invocation

```java
// Minh họa trong một target bean, không phải chương trình độc lập.
public void checkout() {
    this.finalizeOrder();
}

public void finalizeOrder() {
    // ...
}
```

Nếu caller gọi `proxy.checkout()`, lời gọi ngoài đi qua proxy. Nhưng khi thân `checkout()` gọi `this.finalizeOrder()`, đó là lời gọi trực tiếp bên trong target, **không quay lại proxy**. Do đó advice chỉ nhắm `finalizeOrder()` sẽ không tự chạy cho lời gọi nội bộ đó, dù pointcut xét method `finalizeOrder()` là phù hợp.

Một cách xử lý thông dụng: tách trách nhiệm cần được advice thành một Spring bean riêng, inject bean này và gọi qua tham chiếu Spring cung cấp.

### 7.2. Khả năng proxy hóa phương thức

Với **JDK dynamic proxy**, caller chủ yếu gọi các public method khai báo trên interface của proxy. Với **class-based proxy/CGLIB**, Spring có thể proxy những method phù hợp có thể được override, nhưng:

- `final` class không thể subclass để tạo class-based proxy.
- `final` method không thể override để advice theo cơ chế đó.
- `private` method không thể được advice qua cơ chế proxy method override.
- Constructor, field access và lời gọi `static` không phải method-execution join point của Spring AOP proxy.

Những giới hạn này **tách biệt** với việc pointcut expression có đúng cú pháp hoặc advisor có được tìm thấy hay không.

### 7.3. Spring bean và aspect bean

Spring AOP auto-proxy áp dụng lên các bean phù hợp trong Spring container. Một object được tự tạo bằng `new OrderService()` bên ngoài container không tự nhận proxy của bean cùng lớp. Mặt khác, các lớp `@Aspect` chính chúng không phải target bình thường cho auto-proxying bằng các aspect khác; đừng dùng `AopUtils.isAopProxy(aspectBean)` như phép thử duy nhất để kết luận aspect đã được đăng ký hay chưa.

## 8. Khác biệt giữa “có proxy”, “có advisor” và “advice đã chạy”

| Quan sát | Điều có thể kết luận | Điều **chưa** kết luận được |
| --- | --- | --- |
| `AopUtils.isAopProxy(bean) == true` | Tham chiếu đang cầm là một Spring AOP proxy | Mọi method/mọi lời gọi đều chạy một advice cụ thể |
| `AopUtils.canApply(pointcut, Target.class) == true` | Pointcut **có thể** áp dụng lên ít nhất một method của target class | Pointcut đã được cài vào proxy cho một bean cụ thể hoặc match mọi đối số runtime |
| Một method có static match `true` | Có thể khớp theo chữ ký và class | Lời gọi đi qua proxy và các dynamic conditions đều đúng |
| `AopUtils.isAopProxy(bean) == false` | Tham chiếu không phải Spring AOP proxy | Vì sao bean chưa có proxy; có thể do không có advisor, cấu hình hoặc đối tượng ngoài container |
| Log trong advice xuất hiện | Advice đã thực thi trong đường gọi được quan sát | Những lời gọi khác cũng luôn chạy advice |
| Một bean không có advisor khớp | Không được proxy **bởi tập advisor đó** | Có thể vẫn được proxy do advisor/cơ chế infrastructure khác |

Điểm cần nhấn mạnh khi debug: **Spring quyết định proxy ở cấp bean**, còn **lời gọi chạy advice ở cấp method/invocation**. Hai quyết định liên quan nhưng không đồng nhất.

## 9. Kiểm tra advisor thực tế trên một proxy

Sau khi lấy một bean từ `ApplicationContext`, có thể kiểm tra xem proxy có cho phép truy xuất cấu hình advice hay không bằng `org.springframework.aop.framework.Advised`. Trong cấu hình Spring AOP proxy thông thường, proxy có thể triển khai interface này.

Đoạn minh họa dùng đối tượng `order` của ví dụ mục 4:

```java
// Đoạn bổ sung vào Main.java (không chạy độc lập):
import org.springframework.aop.Advisor;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.Advised;

if (order instanceof Advised advised) {
    for (Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName());

        if (advisor instanceof PointcutAdvisor pcAdvisor) {
            System.out.println(pcAdvisor.getPointcut().getClass().getName());
        }
    }
}
```

Ví dụ trên có thể in tên class của advisor/pointcut tùy phiên bản và cấu hình; **không nên cố định stdout** của cấu trúc hạ tầng Spring trong bài học. Đây là công cụ quan sát, còn nguồn xác nhận hành vi cuối cùng vẫn là việc gọi method qua proxy và thấy advice thực thi.

Một số proxy được cấu hình để không expose `Advised` hoặc không cho phép thay đổi advice; do đó nên dùng kiểm tra `instanceof Advised`, không ép kiểu mù quáng.

### Kiểm tra bằng log và debugger

Nếu muốn theo dõi quá trình chọn advisor trong Spring Framework, có thể dùng debugger đặt breakpoint tại các điểm như:

```text
AbstractAdvisorAutoProxyCreator.findEligibleAdvisors(...)
AbstractAdvisorAutoProxyCreator.findAdvisorsThatCanApply(...)
AopUtils.findAdvisorsThatCanApply(...)
AopUtils.canApply(...)
```

Chú ý:

- Một Spring Boot application có thể có nhiều loại advisor và các bean infrastructure khác; việc thấy nhiều advisor candidates là bình thường.
- Các phiên bản Spring có thể thay đổi chi tiết phương thức nội bộ, cache hoặc thứ tự gọi. Chỉ dùng breakpoint để quan sát phiên bản **thật sự đang chạy**.
- Trong trường hợp có `@Order` hoặc `Ordered`, Spring có thể sắp xếp advisor để xác định thứ tự advice; **sắp xếp không biến một advisor không khớp thành khớp**.
- Nếu bạn chỉ thấy proxy được tạo mà không thấy dòng advice, hãy phân biệt lỗi **advisor chưa được chọn cho bean** và lỗi **method/runtime invocation không match**.

## 10. Chẩn đoán advisor không khớp

Thực hiện kiểm tra có thứ tự để tìm đúng tầng phát sinh vấn đề.

### Bước 1. Xác nhận aspect/advisor được Spring đăng ký

- Với `@Aspect`, kiểm tra class aspect là Spring bean (`@Component` nằm trong phạm vi scan hoặc `@Bean`).
- Với `@EnableAspectJAutoProxy`, kiểm tra annotation nằm trong context phù hợp; trên Spring Boot, kiểm tra starter AOP/AspectJ và auto-configuration.
- Không nhầm aspect class tồn tại trên classpath với advisor đã được Spring tìm ra.

### Bước 2. Xác nhận target bean và type đúng

- `context.getBean(OrderService.class)` có lấy đúng bean không?
- Có nhiều bean cùng interface, bean name hoặc nhiều `ApplicationContext` không?
- Pointcut `execution(* example.OrderService.checkout(..))` sẽ không tự mở rộng sang một class cùng tên method ở package khác.

### Bước 3. Kiểm tra khớp lớp rồi khớp phương thức

- Dùng `AopUtils.canApply(pointcut, Target.class)` để xác định có **khả năng** match method nào của class.
- Dùng `pointcut.getMethodMatcher().matches(method, Target.class)` để xét **một method cụ thể** khi dùng Spring `Pointcut` API.
- Nếu pointcut dùng `bean(name)`, phải kiểm tra chính **bean name/context**, không chỉ type.
- Với `args(...)` hay điều kiện runtime, kiểm tra argument type/value tại thời điểm gọi, thay vì chỉ chữ ký method.

### Bước 4. Kiểm tra proxy và đường đi của lời gọi

- `AopUtils.isAopProxy(context.getBean(...))` kiểm tra tham chiếu từ container.
- Xem code có tự `new`, gọi `this.method()`, gọi method `private`/`final` hoặc truy cập method ngoài interface của JDK proxy không.
- Nếu biểu thức match nhưng lời gọi bỏ qua proxy thì Spring AOP không chạy advice.

### Bước 5. Trường hợp có nhiều advisor

- Advisor khác có thể tạo proxy ngay cả khi advisor đang quan tâm **không khớp**.
- Hãy xác định **advisor nào** đã được chọn, sau đó xem method matcher của advisor ấy.
- Nếu advisor có dynamic match, cùng một method có thể chạy advice ở lời gọi này nhưng không chạy ở lời gọi khác.
- `@Order` chủ yếu điều chỉnh **thứ tự chạy** các advice phù hợp; nó không sửa pointcut sai.

### Bảng lỗi phổ biến

| Triệu chứng | Khả năng cần kiểm tra |
| --- | --- |
| `@Aspect` có advice nhưng mọi bean đều không được proxy | Aspect chưa thành bean; auto-proxy chưa bật; dependency AOP chưa đủ |
| Bean có proxy, `checkout()` chạy advice, `status()` không chạy | Hoàn toàn đúng nếu advisor chỉ match `checkout()` |
| Hai bean cùng method name nhưng chỉ một bean nhận advice | Pointcut hạn chế theo package/class, annotation, target type hoặc bean name |
| Cùng method nhưng advice có lúc chạy có lúc không | Pointcut có điều kiện theo runtime arguments; hoặc đường gọi/bean reference khác nhau |
| Pointcut khớp method, nhưng gọi từ `this` không chạy advice | Self-invocation bỏ qua proxy |
| Proxy hiện hữu nhưng advice của bạn không chạy | Proxy có thể do advisor khác tạo; advisor đang xét có thể không eligible hoặc method không match |
| Khớp `ClassFilter` nhưng `MethodMatcher` không khớp | Lớp nằm trong phạm vi rộng, method cụ thể không đúng chữ ký/phạm vi |
| `ClassCastException` khi ép JDK proxy về class implementation | Proxy dạng interface; gọi qua interface hoặc xem chiến lược proxy đang cấu hình |

## 11. Tình huống kiểm tra kiến thức

**Tình huống 1:** Một `PaymentService` có các method `pay()`, `refund()`; chỉ có advisor `execution(* example.PaymentService.pay(..))`. Bean được proxy; gọi `refund()` có chạy advice không?

**Đáp án:** Không chạy advice này nếu `refund()` không khớp. Spring có thể proxy cả bean vì có ít nhất một method phù hợp, nhưng advice phải khớp riêng phương thức thực sự được gọi.

**Tình huống 2:** Hai beans cùng class `PaymentService`, tên `primaryPayment` và `secondaryPayment`. Advice dùng `bean(primaryPayment) && execution(* *.pay(..))`. Hai bean có giống danh sách advisor không?

**Đáp án:** Không nhất thiết. Spring-specific pointcut `bean(...)` xét bean name; advisor có thể eligible cho một bean và không eligible cho bean còn lại dù class giống nhau.

**Tình huống 3:** Method `handle(Object value)` có pointcut `args(java.lang.String)`. Lần lượt gọi qua proxy với `"a"` và `Integer.valueOf(5)`. Có thể có kết quả khác nhau không?

**Đáp án:** Có. Loại đối số runtime khác nhau có thể khiến dynamic matcher chỉ cho phép advice ở lời gọi truyền `String`.

**Tình huống 4:** `AopUtils.canApply(pointcut, PaymentService.class)` là `true`, nhưng `new PaymentService().pay()` không chạy advice. Có mâu thuẫn không?

**Đáp án:** Không. `canApply` chỉ kiểm tra khả năng phù hợp của pointcut với lớp; instance tự tạo ngoài container không tự có Spring AOP proxy.

## 12. Tài liệu Spring chính thức

- [Spring Framework — Pointcut API](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html): `ClassFilter`, `MethodMatcher`, static/dynamic matching và caching.
- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html): những biểu thức `execution`, `within`, `target`, `this`, `args`, `@annotation`, `bean` Spring hỗ trợ.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): cơ chế proxy và giới hạn về method/self-invocation.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): cấu hình auto-proxy cho `@Aspect`.
- [Spring Javadoc — AopUtils](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/support/AopUtils.html): `canApply`, `findAdvisorsThatCanApply`.
- [Spring Javadoc — AbstractAdvisorAutoProxyCreator](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/framework/autoproxy/AbstractAdvisorAutoProxyCreator.html): cơ chế tìm candidate/eligible advisors khi tạo proxy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="auto-proxy-mechanics-003">Luồng tạo bean đích, tạo proxy và trả tham chiếu cho bên phụ thuộc</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP Auto-proxy: luồng tạo bean đích, tạo proxy và trả tham chiếu cho bên phụ thuộc

## 1. Vấn đề cần hiểu

Khi một Spring bean có phương thức phù hợp với advice, Spring AOP có thể tự tạo **proxy**. Một bean khác khai báo constructor injection thường nhận **proxy được container cung cấp**, chứ không giữ trực tiếp target object bên trong proxy. Từ đó, lời gọi qua dependency được chuyển qua advice rồi đến logic nghiệp vụ.

Ba đối tượng/khái niệm cần tách bạch:

- **Target bean**: đối tượng thực hiện nghiệp vụ, được tạo từ bean definition (ví dụ class `@Service` hoặc factory method `@Bean`).
- **AOP proxy**: đối tượng đại diện cho target để chặn các lời gọi phương thức phù hợp; Spring AOP dùng JDK dynamic proxy hoặc class-based proxy (CGLIB).
- **Dependent/consumer bean**: bean sử dụng target qua dependency injection; nó nhận tham chiếu mà Spring công bố cho tên bean, có thể là proxy khi auto-proxying áp dụng.

Trong ví dụ, một `CheckoutWorkflow` cần `OrderService`. Spring nhận yêu cầu này, bảo đảm `OrderService` được tạo và khởi tạo; nếu advisor cần chặn `OrderService.process()` thì container có thể cung cấp AOP proxy. Khi `CheckoutWorkflow` gọi `service.process()`, invocation qua proxy mới cho advice cơ hội thực thi.

**Phạm vi bài:** Spring AOP dựa trên proxy và lifecycle/IoC của Spring. Không suy rộng thành AspectJ bytecode weaving, cũng không coi mỗi method trong một Spring bean đều tự động được advice.

## 2. Sơ đồ thời gian của một singleton thường gặp

```text
Đăng ký @Configuration, @Service, @Component, @Aspect, @Bean
                           |
                           v
              ApplicationContext khởi động
                           |
                           v
           Hạ tầng BeanPostProcessor được đăng ký
          (gồm auto-proxy creator cho Spring AOP)
                           |
                           v
        Container cần tạo CheckoutWorkflow (consumer)
                           |
               Resolve constructor parameter
                           |
                    cần OrderService
                           |
             Tìm bean definition OrderService
                           |
                           v
                  TẠO TARGET INSTANCE
                   (constructor/factory)
                           |
                 điền cấu hình/dependencies
                           |
              before-initialization processors
                           |
                  lifecycle init callbacks
                           |
              after-initialization processors
                           |
         Auto-proxy creator xem xét advisor/pointcut
                   /                 \
           có advice phù hợp       không phù hợp
                   |                 |
             tạo AOP proxy       giữ target
                   \                 /
                    \               /
                     v             v
                Công bố bean reference
                           |
                           v
      Inject reference vào constructor CheckoutWorkflow
                           |
                           v
        Workflow --> proxy --> advice --> target.process()
```

Đây là **mô hình thông thường**, không phải thứ tự callback bất biến với mọi loại bean. Một `BeanPostProcessor` có thể áp dụng logic khác trong `postProcessBeforeInitialization`; auto-proxy creator phổ biến tạo proxy ở `postProcessAfterInitialization` hoặc theo đường đặc biệt khi cần tham chiếu sớm. Trường hợp circular dependencies, custom scopes, `FactoryBean`, post-processors tạo bean quá sớm, hoặc cấu hình đặc thù có đường đi phức tạp hơn.

Điểm quan trọng là **consumer nhận bean reference đã đi qua quá trình quản lý của Spring**. Kết quả không nhất thiết cùng Java object identity với target nằm sau proxy.

## 3. Từ bean definition đến instance của target

### 3.1. Bean definition chưa phải là object

Spring có thể biết sự tồn tại của một bean nhờ:

```java
@Service
public class OrderServiceImpl implements OrderService {
    // ...
}
```

hoặc thông qua một phương thức `@Bean`:

```java
@Configuration
class OrderConfiguration {
    @Bean
    OrderService orderService() {
        return new OrderServiceImpl();
    }
}
```

Đây là hai cách **thay thế** để đăng ký cùng vai trò bean, không phải hai snippet để bật đồng thời cho cùng một target. Chúng chỉ cho thấy nơi Spring lấy bean definition. Class có `@Service` phải nằm trong phạm vi component scanning; `@Bean` tạo và trả về object để container quản lý.

Không nên nhầm việc Spring biết bean definition với thời điểm target được tạo. Singleton thông thường thường được pre-instantiate khi context khởi động, nhưng cũng có thể chỉ được tạo khi được yêu cầu nếu có cấu hình lazy. Với prototype hoặc bean có scope khác, vòng đời khởi tạo và số instance có thể thay đổi.

### 3.2. Container tạo và cấu hình target

Khi cần một `OrderService`, Spring chọn constructor hoặc factory method thích hợp để tạo đối tượng:

1. Giải quyết những dependency cần thiết cho việc gọi constructor/factory method.
2. Tạo target object.
3. Điền field/setter dependencies và cấu hình thuộc tính mà Spring chịu trách nhiệm.
4. Gọi các callback `BeanPostProcessor.postProcessBeforeInitialization(...)`.
5. Thực hiện initialization callbacks thích hợp, chẳng hạn `@PostConstruct`, `InitializingBean.afterPropertiesSet()` hoặc custom init method.
6. Gọi `BeanPostProcessor.postProcessAfterInitialization(...)`. Auto-proxy creator có thể trả **một proxy khác instance target**.

Ở đây, thứ tự callback là logic của bean lifecycle; không có ý nghĩa rằng method nghiệp vụ chạy qua AOP trong constructor hoặc `@PostConstruct`. Target đang được xây dựng thường chưa được caller khác sử dụng thông qua proxy cuối cùng.

### 3.3. Tại sao `new OrderServiceImpl()` trong `@Bean` vẫn hoạt động?

Trong một factory method được container gọi, `new` tạo đối tượng mà phương thức **trả về cho Spring**. Spring tiếp tục quản lý object ấy, chạy hậu xử lý và có thể xuất bản proxy. Nhưng nếu nghiệp vụ tự viết:

```java
OrderService x = new OrderServiceImpl();
x.process("OUTSIDE");
```

thì biến `x` là tham chiếu đến object không tự đăng ký với container; Spring không tự đan AOP proxy vào lệnh gọi đó. Vì vậy vấn đề nằm ở **cách reference được cấp và sử dụng**, không phải chỉ ở sự xuất hiện của toán tử `new`.

## 4. Auto-proxy creator chọn bean và xây dựng proxy

### 4.1. Auto-proxy creator là thành phần nào?

Spring AOP sử dụng hạ tầng `BeanPostProcessor`. Với khai báo `@Aspect`, `@EnableAspectJAutoProxy` hoặc auto-configuration tương ứng sẽ đăng ký cơ chế tự tìm advisor/advice và tạo proxy khi phù hợp. Các implementation thuộc họ `AbstractAutoProxyCreator` thực hiện phần quan trọng này; đây là thành phần hạ tầng Spring, ứng dụng thường không tự gọi nó.

Một sequence khái quát:

```text
target object + bean name + bean class
                    |
         auto-proxy creator kiểm tra
                    |
     Có cần áp dụng advisor cho bean này?
              /             \
            không            có
             |                |
      trả target cũ      chọn advisors
                              |
                         chọn proxy type
                              |
                         tạo AOP proxy
                              |
                         trả proxy
```

Advisor chứa điều kiện áp dụng (pointcut) và advice. Nếu không có advisor phù hợp, hoặc bean không thuộc nhóm có thể auto-proxy, container có thể công bố **chính target**. Không thể suy ra cứ có `@Service`, `@Aspect` hoặc bật Spring AOP là mọi bean đều được proxy.

### 4.2. Khi nào chọn JDK proxy hoặc CGLIB?

Trong Spring Framework theo mặc định `@EnableAspectJAutoProxy(proxyTargetClass = false)`:

- Nếu target triển khai interface phù hợp, Spring có thể tạo **JDK dynamic proxy** thực hiện các interface đó.
- Nếu target không có interface để proxy, Spring tạo class-based proxy (CGLIB).
- Với `proxyTargetClass = true`, Spring chọn class-based proxy khi class có thể được proxy; `final class` không thể tạo subclass, `final`/`private` method không thể bị advice theo cơ chế override.

Spring Boot auto-configured AOP thường đặt `spring.aop.proxy-target-class=true` theo mặc định. Vì vậy cần kiểm tra cấu hình thực tế khi so sánh ứng dụng Spring Framework thuần và Spring Boot.

Sự khác biệt cũng quan trọng với injection: JDK proxy triển khai interface nên consumer nên inject theo interface; nếu consumer yêu cầu concrete class mà Spring trả JDK proxy, có thể lỗi tương thích kiểu. CGLIB proxy là subclass của target nên có thể tương thích với concrete class nếu class không bị giới hạn proxy.

### 4.3. Spring xuất bản **proxy reference** ra sao?

Giả sử target có tên bean `orderServiceImpl`. Thông thường, auto-proxy creator trả đối tượng proxy như kết quả xử lý bean. Khi bean khác tìm dependency theo kiểu hoặc tên, `BeanFactory` cấp **tham chiếu bean sau hậu xử lý**. Với singleton thông thường, các lần lấy lại bean từ cùng context/definition sẽ nhận tham chiếu singleton đã được công bố, không tự tái tạo proxy mới ở mỗi lần inject.

```java
OrderService service = context.getBean(OrderService.class);
System.out.println(AopUtils.isAopProxy(service));
```

Nếu `OrderService` bị advice và đã được proxy, kết quả là `true`. Nếu không có advisor phù hợp thì có thể là `false`, dù `OrderService` vẫn là Spring bean hợp lệ.

**Không đồng nhất proxy và target bằng object identity.** Với AOP proxy, `service.getClass()` có thể là class proxy của JDK hoặc class do CGLIB tạo; việc so sánh `service.getClass() == OrderServiceImpl.class` không phải phép kiểm tra đáng tin cho AOP. Hãy dùng `AopUtils.isAopProxy` và kiểm tra kiểu proxy khi cần.

## 5. Consumer nhận proxy qua Dependency Injection

### 5.1. Constructor injection làm luồng phụ thuộc rõ ràng

```java
@Component
class CheckoutWorkflow {
    private final OrderService service;

    CheckoutWorkflow(OrderService service) {
        this.service = service;
    }

    void checkout(String orderId) {
        service.process(orderId);
    }
}
```

Đây là snippet để minh họa; chương trình hoàn chỉnh nằm ở mục 7.

Khi Spring khởi tạo `CheckoutWorkflow`, nó phải resolve tham số `OrderService` trước khi gọi constructor. Nếu singleton target chưa được tạo, Spring xây dựng nó trước; sau bước auto-proxy, container dùng reference cuối cùng để inject. Vì vậy biến `service` của consumer có thể trỏ đến **proxy**.

### 5.2. Thứ tự thực tế phụ thuộc việc bean nào được tạo trước

Không nhất thiết mọi bean có thứ tự khởi tạo toàn cục cố định. Nếu consumer chưa được tạo nhưng Spring đã pre-instantiate target singleton, DI chỉ lấy reference có sẵn. Nếu consumer là bean khởi tạo sớm và cần target trước đó, thao tác resolve dependency khiến target được tạo trước consumer. Trong cả hai tình huống thông thường, mục tiêu của Spring là cấp dependency đã được container xử lý phù hợp.

### 5.3. Khi consumer gọi method

```text
CheckoutWorkflow.checkout()
          |
          | service.process("A-001")
          v
   AOP proxy của OrderService
          |
       [advice]
          |
   OrderServiceImpl.process()
```

Chỉ **lời gọi qua proxy** đi vào Spring AOP advice. Nếu bên trong `OrderServiceImpl.process()` có `this.anotherMethod()`, lời gọi nội bộ đó đi thẳng trên target nên advice dành cho `anotherMethod()` không tự chạy. Đây là hệ quả proxy-based AOP, ngay cả khi target là bean đúng chuẩn và consumer nhận proxy đúng cách.

Tương tự, một tham chiếu target bị giữ ngoài Spring, ví dụ từ `new`, không tự được thay đổi thành proxy chỉ vì class tương ứng cũng có một Spring bean.

## 6. Phân biệt bean lifecycle callbacks với lời gọi nghiệp vụ qua proxy

| Thời điểm | Diễn ra trên đối tượng nào? | Có nên chờ advice chạy? |
| --- | --- | --- |
| Constructor/factory method của target | Target đang được tạo | Không |
| Setter injection / field injection | Target đang được cấu hình | Không |
| `@PostConstruct`, `afterPropertiesSet` hoặc custom init callback | Thông thường target trước khi xuất bản proxy cuối cùng | Không nên |
| `postProcessAfterInitialization` trong auto-proxy creator | Nhận bean/target; có thể trả proxy | Đây là bước chuẩn bị proxy, chưa phải method nghiệp vụ qua proxy |
| Consumer gọi `service.process()` trên reference do Spring inject | Proxy, sau đó target | Có, nếu advisor/pointcut phù hợp |
| Target gọi `this.process()` | Chính target | Không, vì bỏ qua proxy |
| `@PreDestroy`/destroy callback | Do container quản lý lifecycle của bean | Không nên dựa vào advice để dọn dẹp |

Việc bean đã có một proxy sau initialization không có nghĩa mọi callback trong constructor, `@PostConstruct` hay `@PreDestroy` đều sẽ được AOP intercept. Đừng đặt logic bắt buộc dùng `@Transactional`, security advice hoặc caching advice bên trong chính lifecycle callback và kỳ vọng chúng hoạt động như lời gọi ngoài qua proxy.

Hạ tầng `BeanPostProcessor` phải được khởi tạo sớm. Những post-processor và một số dependency trực tiếp được tạo sớm có thể **không đủ điều kiện để trải qua toàn bộ post-processing/auto-proxying**; Spring có thể cảnh báo rằng bean không đủ điều kiện để tất cả post-processors xử lý. Khi tự viết `BeanPostProcessor`, hãy kiểm tra dependency và tránh khởi tạo không cần thiết các bean nghiệp vụ quá sớm.

## 7. Ví dụ chạy được: thấy target init, proxy và dependency injection

Ví dụ sử dụng **Java 17 + Spring Framework 6.2.x + Maven**, không cần Spring Boot. Hạ tầng `@EnableAspectJAutoProxy` tìm aspect, chọn bean service và công bố proxy cho `CheckoutWorkflow`.

Cấu trúc dự án:

```text
spring-auto-proxy-flow/
├── pom.xml
└── src/main/java/demo/autoproxy/
    ├── AppConfig.java
    ├── OrderService.java
    ├── OrderServiceImpl.java
    ├── TraceAspect.java
    ├── CheckoutWorkflow.java
    └── Main.java
```

### 7.1. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-auto-proxy-flow</artifactId>
    <version>1.0-SNAPSHOT</version>

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

### 7.2. `AppConfig.java` — đăng ký scanner và auto-proxy

```java
package demo.autoproxy;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = AppConfig.class)
@EnableAspectJAutoProxy
public class AppConfig {
}
```

`@ComponentScan` tìm các Spring beans trong `demo.autoproxy`, còn `@EnableAspectJAutoProxy` cung cấp hạ tầng phân tích `@Aspect` và tự tạo proxy. Với cấu hình Spring Framework thuần này, service có interface nên proxy thường là JDK dynamic proxy.

### 7.3. `OrderService.java` — hợp đồng mà consumer cần

```java
package demo.autoproxy;

public interface OrderService {
    void process(String orderId);
}
```

### 7.4. `OrderServiceImpl.java` — target và init callback

```java
package demo.autoproxy;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl implements OrderService, InitializingBean {
    @Override
    public void afterPropertiesSet() {
        System.out.println("[target] initialized");
    }

    @Override
    public void process(String orderId) {
        System.out.println("[target] process " + orderId);
    }
}
```

Chọn `InitializingBean` để thể hiện lifecycle callback mà không cần cấu hình `@PostConstruct`. Method `afterPropertiesSet()` do container gọi trong bước initialization của target. Nó không phải lời gọi business qua proxy.

### 7.5. `TraceAspect.java` — aspect được Spring quản lý

```java
package demo.autoproxy;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TraceAspect {
    @Before("execution(* demo.autoproxy..*.process(..))")
    public void beforeProcess() {
        System.out.println("[advice] before process");
    }
}
```

`@Aspect` mô tả aspect; `@Component` làm Spring phát hiện và quản lý aspect như bean. Pointcut chỉ chọn phương thức `process` trong package demo này, nên target `OrderServiceImpl` được advice.

### 7.6. `CheckoutWorkflow.java` — bean nhận tham chiếu do Spring cấp

```java
package demo.autoproxy;

import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

@Component
public class CheckoutWorkflow {
    private final OrderService service;

    public CheckoutWorkflow(OrderService service) {
        this.service = service;
        System.out.println("[consumer] injected proxy="
                + AopUtils.isAopProxy(service));
    }

    public void checkout() {
        System.out.println("[consumer] checkout");
        service.process("A-001");
    }
}
```

Consumer nhận `OrderService` thông qua constructor. Không tạo `new OrderServiceImpl()`, nên nó sử dụng reference Spring cung cấp. `AopUtils.isAopProxy(service)` chỉ dùng để chẩn đoán trong ví dụ.

### 7.7. `Main.java` — tạo context và gọi consumer

```java
package demo.autoproxy;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            context.getBean(CheckoutWorkflow.class).checkout();
        }
    }
}
```

Chạy ở thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.autoproxy.Main
```

**Những dòng stdout được chương trình in ra** (các log Maven/Spring có thể xuất hiện tùy môi trường):

```text
[target] initialized
[consumer] injected proxy=true
[consumer] checkout
[advice] before process
[target] process A-001
```

Diễn giải:

1. `OrderServiceImpl` được Spring tạo, đi qua lifecycle, chạy `afterPropertiesSet()` và in `[target] initialized`.
2. Khi `CheckoutWorkflow` được tạo và constructor nhận `OrderService`, hạ tầng AOP đã bọc bean service; Spring inject proxy nên in `[consumer] injected proxy=true`.
3. Gọi `checkout()` trên consumer dẫn đến lời gọi `service.process("A-001")`. Lời gọi qua Spring AOP proxy chạy `@Before`, sau đó thực thi target `process()`.
4. `CheckoutWorkflow` không có advice khớp nên nó **không nhất thiết** bị proxy; Spring có thể inject một proxy cho dependency của nó trong khi bản thân consumer vẫn là bean thường.
5. Số lần tạo target và proxy phụ thuộc bean scope và cấu hình. Với singleton thông thường trong ví dụ, Spring xuất bản một reference singleton của service được xử lý cho consumer.

**Về tính xác định của thứ tự log:** Không có tác vụ concurrent, aspect không in gì ở giai đoạn khởi tạo, và consumer constructor cần resolve `OrderService`. Do đó dòng target initialization đứng trước dòng consumer injected; hai dòng advice/business được in khi gọi `checkout()`.

## 8. Thử thay đổi ví dụ để quan sát ranh giới proxy

### 8.1. Tự tạo target bằng `new` trong consumer

Đổi phương thức `checkout()` thành:

```java
public void checkout() {
    System.out.println("[consumer] checkout");
    OrderService unmanaged = new OrderServiceImpl();
    unmanaged.process("B-002");
}
```

Đây là đoạn **thay thế method** trong `CheckoutWorkflow`, không phải class độc lập. Lần gọi `unmanaged.process("B-002")` trực tiếp trên object tự tạo nên không xuất hiện `[advice] before process` cho `B-002`. Nó cũng không đi qua initialization callback của Spring; `afterPropertiesSet()` không tự chạy do object chỉ được tạo bằng toán tử Java `new`.

Trong khi đó field `service` được inject lúc constructor vẫn là proxy. Sự tồn tại của field này không tự biến biến `unmanaged` thành proxy.

### 8.2. Bỏ aspect

Bỏ `@Component` trên `TraceAspect` và không đăng ký aspect đó bằng `@Bean`. Khi không còn advisor nào khác cần bọc service, service có thể được công bố dưới dạng target thô; khi đó `AopUtils.isAopProxy(service)` cho `false`. Service **vẫn là Spring bean**, nhưng không có advice tương ứng.

### 8.3. Dùng factory method `@Bean` thay vì `@Service`

Có thể bỏ `@Service` khỏi `OrderServiceImpl` rồi thêm vào `AppConfig`:

```java
@Bean
OrderService orderService() {
    return new OrderServiceImpl();
}
```

Đây là fragment trong class `@Configuration`, cần import `org.springframework.context.annotation.Bean`. Object được trả về là target **do container quản lý**; Spring vẫn có cơ hội chạy lifecycle, auto-proxy và inject reference cho consumer. Việc factory dùng `new` không khiến nó thành object ngoài Spring.

### 8.4. Đổi sang class-based proxy

Thay `@EnableAspectJAutoProxy` thành:

```java
@EnableAspectJAutoProxy(proxyTargetClass = true)
```

Khi service class có thể được subclass, Spring sẽ dùng CGLIB proxy. Với ví dụ này, `AopUtils.isAopProxy(service)` vẫn trả `true`. Nếu cần phân biệt loại proxy, in thêm:

```java
System.out.println(AopUtils.isJdkDynamicProxy(service));
System.out.println(AopUtils.isCglibProxy(service));
```

Hai dòng code trên là phần chèn minh họa vào consumer hoặc một method có biến `service`. Đối với ví dụ cấu hình ban đầu, kết quả kỳ vọng tương ứng là `true`, `false`; sau khi ép class-based proxy là `false`, `true`. Việc đổi loại proxy không làm self-invocation được advice.

## 9. Các ngoại lệ và giới hạn cần hiểu chính xác

### 9.1. Circular dependency và “early bean reference”

Nếu bean A cần B và B lại cần A, Spring có thể gặp **circular dependency**. Với một số trường hợp singleton sử dụng setter/field injection, BeanFactory có cơ chế cung cấp một **early reference** trước khi đối tượng hoàn tất initialization để giải quyết vòng phụ thuộc. Hạ tầng AOP có thể tham gia qua `SmartInstantiationAwareBeanPostProcessor.getEarlyBeanReference(...)` nhằm bảo đảm reference sớm phù hợp với proxy cuối cùng.

Tuy nhiên, đây là **đường đặc biệt**, không phải quy trình thông thường của mọi bean:

- Không nên dạy rằng Spring luôn tạo proxy **sau** khi đã inject hết vào tất cả các bean; early proxy có thể xuất hiện trước khi quá trình khởi tạo target hoàn tất.
- Không phải mọi circular dependency đều giải quyết được. Đặc biệt, vòng phụ thuộc constructor không thể được giải quyết như vòng setter thông thường.
- Spring Boot hiện đại mặc định hạn chế circular references. Không nên bật cấu hình cho phép vòng phụ thuộc chỉ để che vấn đề thiết kế.
- Nếu một bean đã nhận target thô nhưng sau đó target được proxy, Spring có thể báo lỗi liên quan raw injection dù bean cuối cùng là proxy. Giải pháp tốt là loại bỏ cycle và để các bean nhận reference cuối cùng ổn định.

Vì vậy sơ đồ mục 2 là **happy path** cho bean không có vòng phụ thuộc; khi debug vòng dependency phải xem việc cung cấp early reference và actual proxy.

### 9.2. `BeanPostProcessor` không thể kỳ vọng được auto-proxy như bean nghiệp vụ

`BeanPostProcessor` cần được tạo rất sớm để hậu xử lý những bean đến sau. Chính các post-processor và một số dependency mà chúng cần khởi tạo sớm thường không đủ điều kiện nhận toàn bộ xử lý AOP. Dòng cảnh báo Spring thường chứa nội dung tương tự `Bean '...' is not eligible for getting processed by all BeanPostProcessors`.

Nếu viết `@Bean` trả `BeanPostProcessor`, cần chú ý khai báo kiểu trả về để Spring phát hiện sớm; cách khai báo `static @Bean` ít dependency thường tránh việc tạo sớm nhiều bean không cần thiết. Đừng đặt business dependency phức tạp vào chính auto-proxy-related processors nếu không có lý do.

### 9.3. Scope và proxy khác nhau

- **Singleton**: một Spring bean instance/reference cho mỗi bean definition trong container theo singleton scope; với AOP, reference được công bố có thể là singleton proxy.
- **Prototype**: mỗi lần container xử lý yêu cầu bean có thể tạo một target mới và một proxy mới nếu cần; việc inject prototype trực tiếp vào singleton thường chỉ resolve một lần lúc xây dựng singleton.
- **Scoped proxy**: dùng để trì hoãn resolution đến đúng scope, là một hình thức proxy khác; có thể kết hợp với Spring AOP. Thấy tên class dạng proxy chưa đủ để khẳng định aspect advice đang chạy.

### 9.4. `FactoryBean` khác `@Bean`

`FactoryBean<T>` là một contract của Spring cung cấp sản phẩm từ `getObject()`; factory method gắn `@Bean` chỉ là một cách khai báo bean definition. Hai khái niệm không tương đương. Khi xử lý `FactoryBean`, container có thêm lớp tra cứu sản phẩm và có đường hậu xử lý riêng; đừng áp sơ đồ singleton thuần một cách máy móc.

### 9.5. Cùng class, hai `ApplicationContext`

Một bean thuộc context A không mặc nhiên được advice chỉ vì aspect tồn tại ở context B. Hạ tầng auto-proxy và khả năng tìm thấy advisor có phạm vi theo container. Ứng dụng có parent/child contexts cần kiểm tra nơi khai báo target, aspect, auto-proxy creator và nơi consumer lấy reference.

### 9.6. Không phải mọi method đều có thể bị advice

Với Spring AOP dựa trên proxy, chỉ các method invocation đi qua proxy mới có cơ hội chạy advice; CGLIB không override được `private` hay `final` method, còn JDK proxy phơi bày interface contract. Pointcut phải khớp phương thức thực tế. Self-invocation `this.method()` bypass proxy trong cả hai kiểu.

## 10. Checklist chẩn đoán “đã inject rồi mà aspect không chạy”

| Câu hỏi | Tác động đến luồng |
| --- | --- |
| Target đã đăng ký thành Spring bean? | Nếu tự `new` bên ngoài container, không có auto-proxy chuẩn |
| Component scan có phủ package của service và aspect? | Không tìm được bean definition hoặc advisor nếu scan sai |
| `@Aspect` có được đăng ký qua `@Component`/`@Bean`? | `@Aspect` đơn lẻ không bảo đảm aspect thành Spring bean |
| Spring AOP đã bật chưa? | Cần `@EnableAspectJAutoProxy` hoặc auto-configuration phù hợp |
| Advisor/pointcut có khớp service method? | Không khớp thì có thể trả target trực tiếp |
| Consumer nhận bean qua Spring DI? | Reference được inject mới có cơ hội là proxy |
| `AopUtils.isAopProxy(injectedService)` trả gì? | Phân biệt reference proxy và target |
| Đường gọi là `consumer -> injectedService.method()` hay `this.method()`? | Chỉ đường qua proxy được intercept |
| Kiểu proxy tương thích kiểu injection? | JDK proxy không phải implementation class; CGLIB bị giới hạn `final` |
| Có circular dependency/early bean hoặc khởi tạo quá sớm? | Có thể nhận reference sớm hoặc không được hậu xử lý đầy đủ |
| Có nhiều context hoặc scope đặc biệt? | Có thể đang sử dụng bean/reference ngoài phạm vi advisor |
| Code kỳ vọng advice trong `@PostConstruct`? | Callback khởi tạo target không tương đương external invocation qua proxy |

Một cách kiểm tra có hệ thống là: xác định bean definition → xem điều kiện/giới hạn auto-proxy → lấy reference thực tế từ consumer → kiểm tra proxy với `AopUtils` → quan sát lời gọi qua reference đó → kiểm tra pointcut và self-invocation. Đường đi này giúp phân biệt lỗi tạo bean, lỗi tạo proxy và lỗi gọi sai reference.

## 11. Câu hỏi ôn tập

**1. Spring tạo target rồi mới tạo proxy trong mọi trường hợp?** Với singleton bình thường, proxy thường được tạo sau initialization callback. Có ngoại lệ early proxy khi cần giải quyết một số circular dependency hoặc khi hạ tầng post-processing đặc biệt tham gia.

**2. Vì sao constructor của consumer có thể nhận AOP proxy dù khai báo kiểu interface của service?** Khi resolve dependency, Spring cấp bean reference do container công bố. Với JDK proxy, reference đó triển khai interface nên đáp ứng constructor parameter.

**3. Spring AOP có tạo proxy cho mọi bean không?** Không. Auto-proxy creator xét advisor/pointcut và các quy tắc áp dụng; bean không cần interception có thể được công bố nguyên trạng.

**4. Lệnh `new ServiceImpl()` bên trong phương thức `@Bean` có bị mất AOP không?** Không. Object được **trả về** từ phương thức `@Bean` được Spring quản lý và có thể được auto-proxy.

**5. Tại sao `@PostConstruct` không chắc kích hoạt advice?** Vì nó thuộc bước initialization trên target, thông thường xảy ra trước khi consumer dùng reference proxy để gọi business method.

**6. Làm sao chắc consumer đã nhận proxy?** Kiểm tra ngay reference được Spring inject bằng `AopUtils.isAopProxy(...)` và loại proxy bằng `isJdkDynamicProxy` / `isCglibProxy` nếu cần; cũng cần pointcut thực sự khớp phương thức được gọi.

**7. Nếu một bean đã nhận target thô trong circular dependency thì sao?** Reference đó có thể khác proxy cuối và khiến behavior không nhất quán hoặc bean creation thất bại; giải pháp bền vững là bỏ vòng phụ thuộc và đưa việc gọi qua reference bean ổn định.

## Tài liệu chính thức của Spring

- [Spring Framework — Container Extension Points / BeanPostProcessor](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html)
- [Spring Framework — @AspectJ support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html)
- [Spring Framework — Proxying Mechanisms và self-invocation](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework 6.2 — Using the Auto-proxy Facility](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/autoproxy.html)
- [Spring Framework — Bean Lifecycle Callbacks](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html)
- [Spring Framework — Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="auto-proxy-mechanics-004">Chuỗi interceptor khi một phương thức được gọi qua proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Chuỗi interceptor khi một phương thức được gọi qua Spring AOP proxy

## 1. Kết quả cần nắm

Khi một client gọi phương thức **qua Spring AOP proxy**, Spring có thể thực hiện nhiều interceptor/advice trước khi chuyển tới phương thức thực trên target. Mỗi interceptor trong chuỗi quyết định **có gọi tiếp hay không**, đồng thời có cơ hội quan sát hoặc xử lý kết quả/exception khi lời gọi phía trong quay trở ra.

Mô hình tư duy cốt lõi:

```text
Client
  |
  v
Spring AOP proxy
  |
  v
Interceptor A: trước
  |
  +--> Interceptor B: trước
         |
         +--> Interceptor C: trước
                |
                +--> Target.method()
                |
         <-- Interceptor C: sau / xử lý lỗi
  <-- Interceptor B: sau / xử lý lỗi
<-- Interceptor A: sau / xử lý lỗi
  |
  v
Client nhận kết quả hoặc exception
```

Hình dạng gọi lồng nhau tương đương:

```java
return interceptorA.invoke(() ->
        interceptorB.invoke(() ->
                interceptorC.invoke(() ->
                        target.method())));
```

Đoạn này **chỉ là mã giả**, không phải API Java của Spring. Điều cần ghi nhớ là Spring xử lý chuỗi theo cơ chế **lồng lời gọi**: vào từ ngoài đến trong, rồi trở ra từ trong đến ngoài. Do đó interceptor có ưu tiên cao thường chạy logic “trước” sớm hơn và logic “sau” muộn hơn.

**Giới hạn của bài:** giải thích chuỗi interceptor trong cơ chế auto-proxy của **Spring AOP** đối với *method execution* trên Spring bean. Bài không mở rộng sang AspectJ compile-time/load-time weaving hoặc lý thuyết Aspect chung.

## 2. Spring chuẩn bị chuỗi trước khi lời gọi được thực hiện như thế nào?

Trong lúc khởi tạo container, auto-proxy creator của Spring tìm các advisor áp dụng cho bean. Một advisor kết hợp một điều kiện chọn join point (pointcut, với advisor phù hợp) và một advice. Với cấu hình `@AspectJ`, Spring đọc các method advice khai báo trong bean `@Aspect`, dựng advisor tương ứng và có thể tạo proxy cho target bean.

Khi client thực sự gọi method qua proxy, luồng được mô tả như sau:

1. **Proxy tiếp nhận lời gọi**. Có thể là JDK dynamic proxy hoặc class-based proxy (CGLIB), tùy target và cấu hình. Loại proxy khác nhau nhưng nguyên tắc interceptor chain tương tự.
2. **Spring xác định target và method**, rồi thu thập các interceptor/advice phù hợp với phương thức hiện tại. Tập ứng viên có thể được tính/cached theo cấu hình; với một số pointcut động, điều kiện còn được kiểm tra theo lời gọi.
3. **Các advisor được sắp thứ tự** theo độ ưu tiên đã cấu hình. Advice dạng `@Before`, `@AfterReturning`, `@AfterThrowing`, `@After` cũng có thể được Spring thích ứng thành interceptor để tham gia chuỗi.
4. **Đối tượng đại diện cho lời gọi** giữ thông tin method, target, tham số và vị trí hiện tại trong chuỗi interceptor.
5. **Lời gọi tiếp tục qua từng interceptor** nhờ `proceed()`. Đến cuối chuỗi, Spring mới gọi method thật trên target.
6. **Giá trị trả về hoặc exception đi ngược ra ngoài** qua những interceptor đã tiếp tục lời gọi. Mỗi interceptor có thể chạy logic sau, xử lý lỗi, trả kết quả hoặc ném exception.

Chú ý: đây là mô hình hành vi, **không phải cam kết** Spring lúc nào cũng tái dựng nguyên danh sách advisor từ đầu cho mọi method call; triển khai có thể cache kết quả tra cứu và tối ưu nhiều bước. Từ góc nhìn ứng dụng, điều quan trọng là thứ tự advice được chọn, điều kiện pointcut tại invocation và đường đi của `proceed()`.

### Một hình dung gần với Spring AOP API

Ở mức API thấp, Spring hỗ trợ interface:

```java
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

class TraceInterceptor implements MethodInterceptor {
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        System.out.println("enter " + invocation.getMethod().getName());
        try {
            Object result = invocation.proceed();
            System.out.println("return " + result);
            return result;
        } catch (Throwable error) {
            System.out.println("throw " + error.getClass().getSimpleName());
            throw error;
        }
    }
}
```

Đây là **đoạn minh họa API, không phải ứng dụng độc lập**. Spring gọi `invoke(MethodInvocation)` cho interceptor được cài trong chuỗi. Khi `invocation.proceed()` chạy, điều khiển chuyển sang **interceptor kế tiếp**, hoặc tới target nếu đã hết chuỗi. Với `@Around` dùng annotation, bạn thường thao tác qua `ProceedingJoinPoint.proceed()`; Spring nối cơ chế đó với chuỗi lời gọi dưới tầng proxy.

## 3. `proceed()` thực chất có ý nghĩa gì?

Trong Spring AOP, `proceed()` **không có nghĩa “gọi ngay target bất chấp advice khác”**. Nó tiếp tục phần **còn lại của chain** ở bên trong interceptor hiện tại.

Nếu thứ tự là `A → B → C → target` thì:

- Khi A gọi `proceed()`, điều khiển vào B.
- Khi B gọi `proceed()`, điều khiển vào C.
- Khi C gọi `proceed()`, Spring tiếp tục tới target nếu không còn interceptor nào nữa.
- Sau khi target trả về, C quay lại đoạn code sau `proceed()` của C; lần lượt B rồi A tiếp tục phần logic sau.
- Nếu target hoặc một interceptor phía trong ném exception, exception đi ngược qua các khung gọi đang mở, trừ khi một interceptor xử lý hoặc thay đổi nó.

Với `@Around`, lời gọi này là tùy chọn do advice quyết định:

| Hành vi trong around advice | Tác động lên lời gọi |
| --- | --- |
| Gọi `proceed()` một lần, trả lại kết quả | Cách thông thường: đi tiếp qua chain rồi trả kết quả |
| Không gọi `proceed()` | **Short-circuit**: các interceptor phía trong và target không được gọi qua nhánh này |
| Gọi `proceed()` nhiều lần | Có thể khiến phần chain còn lại và target thực thi nhiều lần; chỉ nên làm khi chủ đích và an toàn |
| `return` một giá trị khác | Caller nhận giá trị do around advice quyết định, miễn tương thích với hợp đồng method |
| `catch` exception rồi `throw` lại | Caller vẫn nhận lỗi, interceptor phía ngoài có thể quan sát |
| `catch` exception và trả fallback | Caller nhận fallback, interceptor phía ngoài nhìn thấy kết thúc thành công thay vì lỗi |

Đối với `@Before`, Spring chạy advice trước và sau đó tự tiếp tục tới phần còn lại nếu advice không ném exception; người viết `@Before` **không cần và không có quyền gọi `proceed()` trực tiếp**. Tương tự, `@AfterReturning` hoặc `@AfterThrowing` không sử dụng `proceed()` như một `@Around`.

## 4. Ví dụ độc lập: hai around interceptor bao nhau

Ví dụ dưới đây dùng **Java 17+, Maven, Spring Framework 6.2.6**. Ta khai báo hai aspect với thứ tự rõ ràng:

- `OuterAspect`: `@Order(1)`, ưu tiên cao hơn.
- `InnerAspect`: `@Order(2)`, ưu tiên thấp hơn.
- Cả hai `@Around` khớp method `execute(...)` của cùng một Spring service.

Ví dụ in ra toàn bộ đường đi vào/ra, gồm trường hợp method ném exception. Không cần Spring Boot; sử dụng `AnnotationConfigApplicationContext` để quan sát Spring AOP trực tiếp.

Cấu trúc:

```text
spring-interceptor-chain/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── InterceptorChainDemo.java
```

**Tệp `pom.xml`:**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-interceptor-chain</artifactId>
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
                    <mainClass>demo.InterceptorChainDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**Tệp `src/main/java/demo/InterceptorChainDemo.java`:**

```java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;

public class InterceptorChainDemo {
    public interface WorkService {
        String execute(String value);
    }

    public static class WorkServiceImpl implements WorkService {
        @Override
        public String execute(String value) {
            System.out.println("TARGET " + value);
            if ("fail".equals(value)) {
                throw new IllegalArgumentException("bad request");
            }
            return "DONE:" + value;
        }
    }

    @Aspect
    @Order(1)
    public static class OuterAspect {
        @Around("execution(* demo..*.execute(..))")
        public Object wrap(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("OUTER enter");
            try {
                Object result = pjp.proceed();
                System.out.println("OUTER return " + result);
                return result;
            } catch (Throwable error) {
                System.out.println("OUTER throw " + error.getMessage());
                throw error;
            } finally {
                System.out.println("OUTER finally");
            }
        }
    }

    @Aspect
    @Order(2)
    public static class InnerAspect {
        @Around("execution(* demo..*.execute(..))")
        public Object wrap(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("INNER enter");
            try {
                Object result = pjp.proceed();
                System.out.println("INNER return " + result);
                return result;
            } catch (Throwable error) {
                System.out.println("INNER throw " + error.getMessage());
                throw error;
            } finally {
                System.out.println("INNER finally");
            }
        }
    }

    @Configuration
    @EnableAspectJAutoProxy
    public static class Config {
        @Bean
        public WorkService workService() {
            return new WorkServiceImpl();
        }

        @Bean
        public OuterAspect outerAspect() {
            return new OuterAspect();
        }

        @Bean
        public InnerAspect innerAspect() {
            return new InnerAspect();
        }
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(Config.class)) {
            WorkService service = context.getBean(WorkService.class);
            System.out.println("AOP_PROXY=" + AopUtils.isAopProxy(service));

            System.out.println("=== SUCCESS ===");
            String result = service.execute("ok");
            System.out.println("CLIENT " + result);

            System.out.println("=== FAILURE ===");
            try {
                service.execute("fail");
            } catch (IllegalArgumentException ex) {
                System.out.println("CLIENT caught " + ex.getMessage());
            }
        }
    }
}
```

**Chạy từ thư mục chứa `pom.xml`:**

```bash
mvn -q compile exec:java
```

**Các dòng stdout do chương trình in, theo thứ tự** (không tính thông báo từ Maven hoặc logging của môi trường):

```text
AOP_PROXY=true
=== SUCCESS ===
OUTER enter
INNER enter
TARGET ok
INNER return DONE:ok
INNER finally
OUTER return DONE:ok
OUTER finally
CLIENT DONE:ok
=== FAILURE ===
OUTER enter
INNER enter
TARGET fail
INNER throw bad request
INNER finally
OUTER throw bad request
OUTER finally
CLIENT caught bad request
```

Vì sao thứ tự lại như vậy?

1. `@EnableAspectJAutoProxy` bật cơ chế auto-proxy; các `@Bean` cung cấp cả target service và hai aspect. Các `@Aspect` phải được đăng ký thành bean để Spring phát hiện.
2. Spring tạo proxy cho `WorkService` vì cả hai pointcut khớp `execute(..)`. Với cấu hình Spring Framework và interface trong ví dụ, proxy thường là JDK dynamic proxy.
3. `@Order(1)` làm `OuterAspect` ưu tiên hơn `@Order(2)`: trên **đường vào**, outer chạy trước inner. Cả hai gọi `proceed()` nên target mới được thực thi.
4. Trong nhánh thành công, target trả `DONE:ok`. Inner xử lý return và finally trước, rồi outer xử lý return và finally, sau cùng client nhận kết quả.
5. Trong nhánh lỗi, target ném `IllegalArgumentException`. Inner bắt, in thông tin rồi ném lại; outer tiếp tục bắt và ném lại; client mới nhận exception. Các khối `finally` vẫn chạy theo thứ tự đi ra.

Ví dụ cố ý có **một around advice trong mỗi aspect** để tránh phụ thuộc vào thứ tự nhiều advice cùng loại hoặc nhiều loại trong cùng aspect.

## 5. `@Order` quyết định thứ tự nào?

Giả sử hai aspect cùng advice một method:

```java
@Aspect
@Order(10)
class MetricsAspect { /* ... */ }

@Aspect
@Order(20)
class AuditAspect { /* ... */ }
```

Đây là **mã minh họa, không phải lớp chạy độc lập**. Trong Spring, giá trị `@Order` **nhỏ hơn có mức ưu tiên cao hơn**. Khi hai around advice này đều khớp cùng method:

```text
metrics.enter          <- ưu tiên cao: vào trước
    audit.enter        <- ưu tiên thấp hơn: vào sau
        target()
    audit.exit         <- ưu tiên thấp hơn: ra trước
metrics.exit           <- ưu tiên cao: ra sau
```

Vì cơ chế lồng nhau, không được hiểu “aspect ưu tiên cao luôn in mọi dòng trước aspect ưu tiên thấp”. Chỉ có **đường vào** là từ ưu tiên cao xuống thấp; **đường ra** diễn ra ngược lại.

Nếu hai aspect khác nhau cùng áp dụng và không chỉ rõ thứ tự, **không có bảo đảm về thứ tự tương đối**. Có thể dùng `org.springframework.core.annotation.Ordered` thay cho `@Order`. Đừng dựa vào thứ tự khai báo method hay thứ tự Spring tình cờ khởi tạo bean để xác lập hợp đồng nghiệp vụ.

### Nhiều advice trong cùng một `@Aspect`

Trong Spring Framework hiện đại, các loại advice của cùng aspect có thứ tự ưu tiên theo loại: `@Around`, `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`. Tuy vậy `@After` mang ý nghĩa **finally** nên thực tế được gọi sau `@AfterReturning` hoặc `@AfterThrowing` cùng aspect khi các advice đó áp dụng. Nhiều advice **cùng loại** trong một class `@Aspect` không có thứ tự ổn định chỉ dựa trên thứ tự dòng nguồn Java.

Nếu kết quả phụ thuộc nghiêm ngặt vào thứ tự, ưu tiên tách trách nhiệm sang các aspect riêng có `@Order` rõ ràng, hoặc dùng một `@Around` điều phối trong phạm vi nhỏ. Đừng “khắc phục” bằng cách ngẫu nhiên đổi vị trí khai báo method.

## 6. Các dạng advice đi vào chuỗi như thế nào?

Mỗi advice có hợp đồng khác nhau. Trong Spring AOP, các dạng thường gặp:

| Dạng advice | Thời điểm hiệu lực | Tác động điển hình |
| --- | --- | --- |
| `@Before` | Trước khi chuyển đến target | Kiểm tra, log, xác thực tiền điều kiện; có thể ném exception khiến lời gọi dừng |
| `@Around` | Bao quanh phần còn lại của chain | Quyết định gọi `proceed()`, đổi tham số/giá trị trả về, đo thời gian, retry hoặc short-circuit |
| `@AfterReturning` | Khi execution kết thúc bình thường | Quan sát return value, ghi metric kết quả; không thay được reference trả về chỉ bằng cách `return` từ advice |
| `@AfterThrowing` | Khi execution thoát bằng exception phù hợp | Quan sát exception từ join point; không phải callback tổng quát cho mọi lỗi do các advice khác gây ra |
| `@After` | Kết thúc method execution theo cả đường thường lẫn lỗi | Cleanup kiểu `finally` |

Ví dụ advice được tách thành những đoạn ngắn trong Spring bean `@Aspect`:

```java
@Before("execution(* demo..*.execute(..))")
public void beforeExecution() {
    System.out.println("BEFORE");
}

@AfterReturning(
    pointcut = "execution(* demo..*.execute(..))",
    returning = "result"
)
public void afterSuccess(Object result) {
    System.out.println("RETURNED " + result);
}

@AfterThrowing(
    pointcut = "execution(* demo..*.execute(..))",
    throwing = "error"
)
public void afterFailure(Throwable error) {
    System.out.println("THREW " + error.getClass().getSimpleName());
}

@After("execution(* demo..*.execute(..))")
public void finallyExecuted() {
    System.out.println("AFTER finally");
}
```

Các method trên là **đoạn minh họa**, chưa phải file Java biên dịch độc lập; cần class `@Aspect`, import các annotation AspectJ, bean registration và context. Nếu đặt tất cả vào cùng một aspect, không nên dự đoán thứ tự stdout chỉ bằng cách đọc thứ tự khai báo. Dùng ví dụ hai aspect ở phần 4 khi cần một trace thực sự xác định.

Lưu ý khác: khi một `@Around` phía trong bắt exception và trả fallback bình thường, advice phía ngoài có thể thấy **kết quả thành công**. Khi `@Around` ngoài short-circuit mà không gọi `proceed()`, các interceptor ở trong nó, kể cả `@Before` và `@After` ở phần trong, đều không có cơ hội chạy.

## 7. Giá trị trả về và ngoại lệ xuyên qua các interceptor

Một interceptor không nhận “bản sao độc lập” của lời gọi. Nó tham gia vào **cùng đường thực thi logic** và tạo nên kết quả mà client nhận.

Ví dụ Spring around advice trả fallback khi service lỗi:

```java
@Around("execution(* demo..*.load(..))")
public Object recover(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (IllegalStateException ex) {
        return "FALLBACK";
    }
}
```

Đây là **một advice method minh họa**; kiểu trả về thực tế của target phải tương thích với giá trị fallback. Nếu advice này ở trong một `@Around` khác, interceptor ngoài có thể nhận chuỗi `FALLBACK` như một return bình thường, mặc dù target đã ném exception.

Điều gì xảy ra khi interceptor đổi kết quả?

```text
Caller
  |
  v
Outer interceptor
  |
  v
Recovery interceptor
  |
  v
Target -> throws IllegalStateException
  |
  v
Recovery interceptor catches -> returns "FALLBACK"
  |
  v
Outer interceptor sees normal return "FALLBACK"
  |
  v
Caller receives "FALLBACK"
```

**Không nên nuốt lỗi bừa bãi.** Các interceptor phía ngoài (ví dụ transaction, audit, retry) có thể dựa vào kết quả/exception để quyết định hành vi. Một advice tự chuyển exception thành thành công sẽ thay đổi tín hiệu mà phần còn lại của chain nhìn thấy. Phân tích ranh giới rollback, retry, logging và HTTP error mapping khi đưa fallback vào nghiệp vụ thực tế.

Với `@AfterReturning`, bạn có thể quan sát giá trị trả về, nhưng việc `return` từ method advice **không thay thế** giá trị method target trả cho caller. Muốn thay return value một cách chủ đích, dùng `@Around` và trả object phù hợp.

## 8. Pointcut và method matching nằm ở đâu trong chuỗi?

Khi client gọi proxy, Spring chỉ cần thực thi những interceptor được xác định là phù hợp với method. Hai chuyện phải phân biệt:

- **Có proxy hay không:** auto-proxy creator quyết định bean có cần proxy dựa vào advisor/điều kiện phù hợp.
- **Lời gọi hiện tại khớp advisor nào:** proxy xác định interceptor chain cho method cụ thể; một số pointcut có thể kiểm tra thêm args vào thời điểm gọi.

Với pointcut tĩnh dựa trên method signature, các interceptor có thể được chọn theo method và cached. Với các dạng dynamic matching, bộ kiểm tra có thể sử dụng tham số của chính invocation. Không vì một bean có proxy mà mọi method trên bean đều chạy toàn bộ advisor.

Ví dụ:

```java
@Around("execution(* demo..*.execute(..))")
public Object aroundExecute(ProceedingJoinPoint pjp) throws Throwable {
    return pjp.proceed();
}
```

Đây là **advice minh họa**: biểu thức `execution` khớp *method execution* có tên `execute` trong phạm vi package phù hợp. Nó không mặc nhiên khớp method `cancel` hay `status` của cùng bean. Nếu method không khớp advisor nào, Spring có thể gọi target mà không chạy logic advice đó.

**Spring AOP proxy không chặn constructor, field read/write hay mọi điểm gọi**. Giới hạn chính là *method execution join point* đi qua Spring proxy. Khi target tự gọi `this.anotherMethod()`, lời gọi bên trong chạy trên chính target và thường bỏ qua advice chỉ khớp `anotherMethod()`, vì nó không đi qua proxy.

## 9. Hai hình thức proxy, một nguyên tắc chain

Spring AOP có hai con đường proxy phổ biến:

| Kiểu proxy | Cách client đi vào | Interceptor chain |
| --- | --- | --- |
| JDK dynamic proxy | Gọi qua interface được proxy triển khai | Áp dụng các interceptor/advisor khớp lời gọi |
| CGLIB class-based proxy | Gọi method phù hợp trên subclass proxy | Áp dụng các interceptor/advisor khớp lời gọi |

Cách tạo proxy tác động khả năng intercept method và cách inject bean, nhưng **không thay đổi ý nghĩa `proceed()`**: tiếp tục chain cho tới target.

Một số giới hạn khi dùng class-based proxy: `final class` không thể được subclass để tạo CGLIB proxy; `final method` hoặc `private method` không thể được advice theo cơ chế override. Với JDK proxy, caller tương tác qua interface của proxy; ép kiểu sang implementation có thể thất bại.

Dù chọn JDK hay CGLIB, lời gọi trực tiếp tới một object tự `new` (không lấy qua Spring container) không tự động đi qua Spring AOP interceptor chain. Khi cùng class gọi method của chính nó bằng `this`, đây cũng là lời gọi nằm ngoài entry point proxy.

## 10. Chuỗi interceptor trong Spring transaction, cache và async

Ở ứng dụng Spring thực tế, bạn có thể gặp chuỗi interceptor nhiều nguồn chứ không chỉ các `@Aspect` bạn tự viết:

- **Transaction:** interceptor liên quan `@Transactional` có thể mở hoặc tham gia transaction trước khi phần bên trong chain chạy; khi đi ra, nó commit/rollback theo kết quả, exception và các quy tắc cấu hình.
- **Cache:** interceptor của `@Cacheable` có thể trả kết quả cache mà không gọi target; khi cache miss, nó có thể tiếp tục chain để lấy giá trị.
- **Async:** cơ chế `@Async` có thể chuyển execution qua executor/thread khác; loại interceptor và cách gọi tiếp còn tùy cấu hình, nên không suy thứ tự đồng bộ giống ví dụ console nếu async được bật.

Tất cả đều liên quan đến việc caller tương tác với Spring bean qua proxy, nhưng mỗi tính năng có semantics riêng. **Không nên mặc định** Spring đặt transaction/cache/async và application `@Aspect` của bạn theo một thứ tự cố định trong mọi project: phải xem `@Order`, `Ordered` và cấu hình hiệu lực của các advisor hạ tầng.

Ví dụ nếu around advice có priority cao hơn transaction interceptor, code trước `proceed()` của around sẽ chạy **trước khi** transaction bên trong bắt đầu; code sau `proceed()` của around sẽ chạy **sau khi** transaction bên trong xử lý xong. Nếu muốn metric nằm trong transaction hay ngoài transaction, cần chỉ rõ thứ tự và kiểm tra trace thực tế.

## 11. Gỡ lỗi một chuỗi interceptor chạy không như dự kiến

Khi log không xuất hiện hoặc đảo thứ tự, kiểm tra theo thứ tự sau:

1. **Client có gọi qua Spring proxy không?** Inject hoặc lấy bean từ context; tránh `new Implementation()` và nhận biết self-invocation qua `this`.
2. **Có thực sự là AOP proxy không?** Dùng `AopUtils.isAopProxy(bean)`. Đây chỉ là bước đầu: có proxy không có nghĩa method nào cũng có advice.
3. **Advisor khớp method đang gọi không?** Rà package, method name, access modifier, parameter types, annotation, target type, tên bean và runtime arguments.
4. **Các aspect có là Spring bean không?** `@Aspect` phải được Spring container đăng ký; Spring Framework thuần có thể cần `@EnableAspectJAutoProxy`, Boot có thể tự bật với starter và điều kiện phù hợp.
5. **Thứ tự `@Order` đã rõ chưa?** Số càng thấp vào càng sớm, ra càng muộn. Nếu không quy định thứ tự giữa các aspect khác nhau thì không nên dự đoán stdout cố định.
6. **Có interceptor short-circuit không?** Kiểm tra các `@Around`, cache hit hoặc code trả sớm trước `proceed()`; interceptor/target bên trong sẽ không chạy.
7. **Exception có bị bắt hoặc chuyển thành fallback?** Outer interceptor có thể thấy normal return khi inner đã xử lý exception. Ngược lại, interceptor ném exception mới có thể che khuất kết quả target.
8. **Có gọi `proceed()` nhiều lần không?** Những lần chạy lặp có thể dẫn đến ghi dữ liệu nhiều lần; chỉ dùng có chủ đích như retry có kiểm soát.
9. **Có bao nhiêu advice trong cùng aspect?** Với nhiều advice cùng loại, đừng trông chờ thứ tự dựa vào vị trí source Java.
10. **Có phải lời gọi bất đồng bộ?** Quan sát thread/context và ranh giới executor; một trace nhiều thread không phản ánh call stack tuần tự duy nhất như ví dụ đơn giản.

Nếu cần kiểm tra bằng Java, có thể dùng API `org.springframework.aop.framework.Advised` cho các Spring AOP proxy hỗ trợ để xem advisors:

```java
Object bean = context.getBean(WorkService.class);
if (bean instanceof org.springframework.aop.framework.Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println(advisor);
    }
}
```

Đây là **đoạn bổ sung** cho ứng dụng có Spring context và `WorkService`, không phải ví dụ standalone. Nó giúp xem advisor đã gắn trên proxy nhưng không thay thế việc xác định **advisor nào thực sự khớp method invocation** hay dynamic runtime condition. Với proxy bị cấu hình frozen/opaque hoặc các proxy khác, cách quan sát có thể thay đổi.

## 12. Tình huống tự kiểm tra

**A.** Chain gồm A `@Order(1)` và B `@Order(5)`, cả hai đều `@Around`, đều gọi `proceed()` một lần. Target trả bình thường. Thứ tự?  
**Đáp án:** A-enter, B-enter, target, B-exit, A-exit.

**B.** A vào trước B nhưng A trả giá trị ngay mà không gọi `proceed()`. B và target có chạy không?  
**Đáp án:** Không. A short-circuit phần chain phía trong.

**C.** Target ném exception, B bắt và trả fallback bình thường. A phía ngoài sẽ nhìn thấy gì khi `proceed()` của A trả về?  
**Đáp án:** Một return value bình thường do B trả; exception đã bị B xử lý. A không tự nhìn thấy lỗi gốc trừ khi được truyền theo cách khác.

**D.** Tại sao một `@AfterReturning` không thay thế return value bằng cách `return "new"`?  
**Đáp án:** Hợp đồng `@AfterReturning` là chạy sau execution thành công và quan sát kết quả; muốn thay kết quả, dùng around advice với giá trị trả về phù hợp.

**E.** Method `process()` trên target gọi `this.save()`, còn advice chỉ khớp `save()`. Advice có chạy nhờ chuỗi interceptor của `process()` không?  
**Đáp án:** Không cho lời gọi nội bộ này. Lời gọi `this.save()` không vào lại Spring proxy.

**F.** Hai aspect có nhiều loại advice nhưng không khai báo `@Order`. Có thể lấy thứ tự khai báo source Java làm thứ tự thực thi tin cậy không?  
**Đáp án:** Không. Đặt thứ tự giữa các aspect và thiết kế rõ việc có nhiều advice cùng loại.

## 13. Tài liệu Spring Framework chính thức

- [Spring AOP — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): `@Around`, `proceed()`, advice theo return/exception và quy tắc ordering.
- [Spring AOP APIs — Advice](https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html): `MethodInterceptor` và `MethodInvocation.proceed()`.
- [Spring AOP — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB, đường đi qua proxy, self-invocation.
- [Spring AOP — Pointcuts](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html): method-execution pointcuts và các điều kiện áp dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="auto-proxy-mechanics-005">Vai trò của auto-proxy creator trong ApplicationContext</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò của auto-proxy creator trong ApplicationContext

## 1. Auto-proxy creator là gì và giải quyết vấn đề gì?

Trong một ứng dụng Spring, `OrderService`, `PaymentService` hoặc `InventoryService` thường được lấy qua dependency injection hoặc `ApplicationContext.getBean()`. Khi lập trình viên định nghĩa một aspect để ghi log, đo thời gian hay áp dụng chính sách chung, họ thường **không tự tạo proxy cho từng service**. Spring thực hiện việc đó nhờ thành phần hạ tầng gọi là **auto-proxy creator**.

**Auto-proxy creator** là một loại **`BeanPostProcessor` của Spring** có khả năng quan sát các bean được container tạo ra, xác định liệu bean có cần advisor/advice hay không, rồi cung cấp một **AOP proxy** thay cho đối tượng bean gốc khi cần. Nhờ vậy:

- Các bean đích không phải biết cụ thể về `@Aspect`, advisor, interceptor hoặc cách tạo proxy.
- Một chính sách AOP có thể áp dụng nhất quán lên nhiều Spring-managed beans theo pointcut.
- Caller nhận **tham chiếu do container trả về**; nếu bean đủ điều kiện, tham chiếu đó là proxy có chuỗi interceptor cần thiết.
- Bean không khớp advisor nào có thể được trả về bình thường, không cần proxy AOP do cấu hình đó tạo ra.

Phạm vi bài này là **auto-proxy creator của Spring AOP bên trong `ApplicationContext`**, đặc biệt khi dùng khai báo `@Aspect` và `@EnableAspectJAutoProxy`. Đây là cơ chế proxy trong Spring, **không phải compile-time hay load-time weaving của AspectJ**.

Một quy tắc cần nhớ ngay từ đầu:

> **Auto-proxy creator chọn và tạo proxy trong vòng đời quản lý bean; advice chạy khi một phương thức phù hợp được gọi qua proxy.** Hai thời điểm này khác nhau.

## 2. Nó đứng ở đâu trong `ApplicationContext`?

Sơ đồ giản lược:

```text
@Configuration / <aop:aspectj-autoproxy/> / Boot AOP configuration
                         |
                         v
           Đăng ký auto-proxy creator
           như một BeanPostProcessor
                         |
                         v
ApplicationContext khởi tạo và đăng ký các BeanPostProcessor
                         |
                         v
             Tạo target bean từ definition
                         |
                         v
            Inject dependency, init callbacks
                         |
                         v
Auto-proxy creator xem xét bean sau khởi tạo
         |                               |
         | Có advisor phù hợp            | Không có advisor phù hợp
         v                               v
   Tạo AOP proxy                  Giữ bean không có AOP proxy
         |                               |
         +---------------+---------------+
                         |
                         v
            Expose bean từ ApplicationContext
                         |
                         v
Caller gọi qua proxy -> advice -> target method
```

Sơ đồ mô tả đường thông thường khi bean khởi tạo hoàn tất. Spring còn có các hook hỗ trợ tham chiếu sớm cho tình huống tạo bean phức tạp; vì thế **không nên hiểu đây là một thuật toán cố định áp dụng nguyên xi cho mọi bean**. Điểm cần nắm là auto-proxy creator được đăng ký đủ sớm với container để tham gia xử lý những bean được tạo sau đó.

### 2.1. Phân biệt các đối tượng

| Đối tượng | Được ai quản lý? | Trách nhiệm |
| --- | --- | --- |
| `ApplicationContext` | Spring runtime | Điều phối bean definitions, post-processors, scopes, lifecycle và lookup. |
| **Auto-proxy creator** | Đăng ký như bean `BeanPostProcessor` trong context | Tìm candidate advisors/aspects, đánh giá khả năng áp dụng, tạo proxy khi cần. |
| **Aspect bean** | Spring IoC | Chứa `@Aspect`, pointcut/advice cho hành vi chung. |
| **Advisor** | Spring AOP infrastructure | Kết hợp advice với điều kiện áp dụng/pointcut; là dữ liệu để auto-proxy creator lựa chọn. |
| **Target bean** | Spring IoC | Chứa mã nghiệp vụ thực sự. |
| **AOP proxy** | Spring AOP tạo và trao cho caller | Chuyển lời gọi phương thức qua interceptor chain trước khi đến target. |

Auto-proxy creator **không thay thế** bean factory. Bean factory vẫn tạo/inject/khởi tạo target theo hợp đồng của Spring; auto-proxy creator tham gia giai đoạn post-processing để có thể bọc bean đó.

## 3. Vì sao auto-proxy creator lại là `BeanPostProcessor`?

`BeanPostProcessor` là điểm mở rộng cho phép Spring xử lý **đối tượng bean** ở thời điểm container tạo ra nó, thay vì chỉ chỉnh bean definition. Hai callback cơ bản là:

```java
Object postProcessBeforeInitialization(Object bean, String beanName);
Object postProcessAfterInitialization(Object bean, String beanName);
```

Đây là **chữ ký được giản lược để học**, không phải một class Java độc lập. Trong quy trình thông thường, auto-proxy creator kế thừa hạ tầng `AbstractAutoProxyCreator`, sử dụng các callback thích hợp để quyết định trả về proxy hay giữ bean hiện tại.

Khi một `@Bean` hoặc `@Component` được tạo:

1. Spring xử lý việc tạo instance, inject dependency và callbacks theo lifecycle.
2. Auto-proxy creator xem tên/class/metadata và các advisor có thể áp dụng.
3. Nếu có advice tương ứng, nó xây dựng proxy phù hợp, gắn các advisor/interceptor đã chọn.
4. Spring công bố tham chiếu cuối cùng cho những caller lấy bean từ container.

Về mặt kết quả, các client nhận bean qua DI hoặc `context.getBean()` có thể chỉ thấy **proxy**; việc gọi một method qua tham chiếu này kích hoạt advisor chain. Nếu caller tự `new` target object hoặc giữ raw target reference từ bên ngoài, Spring không thể tự áp dụng proxy của `ApplicationContext` lên reference đó.

**Không nên nhầm với `BeanFactoryPostProcessor`:** loại latter xử lý **metadata/bean definitions**, còn `BeanPostProcessor` xử lý **instances**. Auto-proxy creator thuộc nhóm xử lý bean instances, không phải một bước thay đổi source code của class.

## 4. Auto-proxy creator lấy thông tin advice từ đâu?

Tùy cấu hình, Spring có thể có các **`Advisor` beans** hoặc **aspect beans** có `@Aspect`. Với cấu hình `@EnableAspectJAutoProxy`, Spring đăng ký lớp hỗ trợ kiểu `@AspectJ` để nhận diện aspect beans trong chính context, chuyển các khai báo advice thành advisor và xem advisor nào thích hợp với bean đang được tạo.

Ví dụ:

```java
@Aspect
public class AuditAspect {
    @Before("execution(public * com.example.shop.*Service.*(..))")
    public void audit() {
        // Ghi sự kiện trước khi service method thực thi.
    }
}
```

Đây là **fragment minh họa**, chưa gồm import/đăng ký bean. Nếu `AuditAspect` được đăng ký vào Spring context và chế độ `@AspectJ` được kích hoạt, Spring có thể tìm ra advice `audit()` và áp dụng nó cho các method khớp biểu thức. Một `InventoryService.update()` nằm trong package ví dụ có thể được proxy; một `HealthStatus.ping()` không khớp pointcut sẽ không bị proxy chỉ bởi aspect đó.

Một số tiêu chí đánh giá:

- **Bean có được Spring quản lý?** Annotation `@Aspect` một mình không tự tạo bean; thường khai báo `@Aspect` + `@Component` hoặc `@Bean`.
- **Aspect/advisor có thuộc context liên quan?** Auto-proxy creator hoạt động trong miền container cụ thể.
- **Class/method có khớp pointcut?** Một aspect tồn tại không đồng nghĩa mọi bean đều bị proxy.
- **Bean có thuộc nhóm cần bỏ qua?** Advice, advisor, aspect class và những bean hạ tầng nhất định không được chính cơ chế Spring AOP này tự proxy.
- **Proxy strategy có hợp lệ?** Interface-based proxy và class-based proxy có giới hạn khác nhau; class/method `final` không thể được override bởi CGLIB.

Trong Spring Framework, lớp thường dùng cho `@AspectJ` auto-proxying là:

```text
org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator
```

Nó kế thừa hạ tầng `AspectJAwareAdvisorAutoProxyCreator` / `AbstractAdvisorAutoProxyCreator`, xử lý cả **Spring Advisor** và các aspect khai báo bằng annotation. Đây là **class hạ tầng**, thông thường người học không cần tự khai báo `@Bean` hay `new AnnotationAwareAspectJAutoProxyCreator()`; cứ dùng cấu hình Spring cung cấp.

## 5. Kích hoạt auto-proxy creator trong một ứng dụng Spring

### 5.1. Java configuration — `@EnableAspectJAutoProxy`

```java
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
}
```

Đây là **fragment cấu hình**. Annotation yêu cầu Spring bật hỗ trợ auto-proxy dựa trên các aspect `@Aspect` được quản lý. Trong ứng dụng Spring Framework thuần, bạn còn cần `spring-context`/Spring AOP và thư viện `aspectjweaver` để phân tích cú pháp `@AspectJ` (ví dụ thông qua Maven/Gradle).

Annotation **không biến mọi class mang `@Aspect` thành bean**. Aspect phải thực sự được đăng ký, chẳng hạn `@Aspect` + `@Component` nằm trong component scan hoặc `@Bean` trả về class aspect.

Có thể cấu hình:

```java
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = false)
```

- `proxyTargetClass = true`: ưu tiên **class-based proxy** (CGLIB).
- `proxyTargetClass = false`: sử dụng chiến lược proxy mặc định; khi target có interface phù hợp thường chọn JDK dynamic proxy.
- `exposeProxy = true`: cho phép truy cập proxy hiện hành qua `AopContext.currentProxy()` trong lời gọi AOP được expose. Đây là tùy chọn nâng cao có chi phí và coupling, **không phải cách sửa self-invocation mặc định**.

### 5.2. XML configuration

```xml
<aop:aspectj-autoproxy proxy-target-class="true"/>
```

Dòng trên cần đặt trong XML hợp lệ đã khai báo namespace `aop`; đây chỉ là **fragment**, không phải toàn bộ tài liệu XML. Nó yêu cầu hỗ trợ auto-proxy cho `@AspectJ` bằng Spring tương tự Java annotation.

Một cấu hình XML khác có `<aop:config>` hoặc một tính năng dựa vào proxy như `<tx:annotation-driven>` cũng có thể đăng ký/nâng cấp hạ tầng auto-proxy. Spring thống nhất các cấu hình hạ tầng chuẩn để tránh tạo một auto-proxy creator riêng cho mỗi dòng XML.

### 5.3. Spring Boot

Với Spring Boot, thông thường starter AOP và auto-configuration đã đảm nhiệm việc bật hạ tầng phù hợp. Ví dụ Boot 3.x dùng `spring-boot-starter-aop`; Boot 4.x có `spring-boot-starter-aspectj`. Không cần thêm `@EnableAspectJAutoProxy` chỉ để “bật thêm một lần” nếu Boot đã cấu hình AOP đúng.

Khi thay đổi chế độ proxy ở Boot, kiểm tra property `spring.aop.proxy-target-class` và các cấu hình proxy khác thật sự được nạp. **Property của Boot không thay thế việc hiểu cơ chế auto-proxy creator trong Spring container**, và không nên suy từ starter rằng AspectJ weaving đang hoạt động.

## 6. Ví dụ hoàn chỉnh: nhìn thấy auto-proxy creator trong `ApplicationContext`

Ví dụ dùng **Java 17+, Spring Framework 6.1.14, AspectJ 1.9.22.1 và Maven**, không cần Spring Boot hoặc máy chủ web. Mục tiêu quan sát được:

1. `ApplicationContext` có bean auto-proxy creator nội bộ.
2. Bean `OrderService` khớp pointcut được tạo proxy.
3. Bean `CatalogInfo` không khớp pointcut nên không bị aspect này yêu cầu proxy.
4. Gọi `OrderService.place()` qua bean lấy từ context chạy advice chính xác một lần.
5. Aspect `AuditAspect` được Spring quản lý nhưng không bị Spring AOP tự proxy.

### 6.1. Cấu trúc project

```text
spring-auto-proxy-demo/
├── pom.xml
└── src/main/java/com/example/apc/
    ├── DemoApp.java
    ├── AppConfig.java
    ├── OrderService.java
    ├── CatalogInfo.java
    └── AuditAspect.java
```

### 6.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-auto-proxy-demo</artifactId>
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
                <version>3.13.0</version>
                <artifactId>maven-compiler-plugin</artifactId>
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

`spring-context` cung cấp container và kéo các module Spring liên quan, bao gồm `spring-aop`; `aspectjweaver` cung cấp phần AspectJ cần thiết cho cách khai báo `@Aspect`. Tất cả code bên dưới chạy với dependency của ví dụ; không yêu cầu AspectJ compiler/weaving agent.

### 6.3. `src/main/java/com/example/apc/OrderService.java`

```java
package com.example.apc;

public class OrderService {
    public String place(String orderId) {
        System.out.println("[ORDER] " + orderId);
        return "accepted:" + orderId;
    }
}
```

### 6.4. `src/main/java/com/example/apc/CatalogInfo.java`

```java
package com.example.apc;

public class CatalogInfo {
    public String version() {
        return "v1";
    }
}
```

### 6.5. `src/main/java/com/example/apc/AuditAspect.java`

```java
package com.example.apc;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    private final AtomicInteger invoked = new AtomicInteger();

    @Before("execution(public * com.example.apc.OrderService.place(..))")
    public void beforePlace(JoinPoint joinPoint) {
        invoked.incrementAndGet();
        System.out.println("[AUDIT] " + joinPoint.getSignature().getName());
    }

    public int calls() {
        return invoked.get();
    }
}
```

Aspect này chỉ có một advice dành riêng cho `OrderService.place(..)`. `AtomicInteger` giúp phép đếm an toàn khi có lời gọi đồng thời; một aspect singleton thường được nhiều target calls sử dụng. Code minh họa dùng `println` để quan sát; ứng dụng thực tế nên dùng logging/metrics phù hợp.

### 6.6. `src/main/java/com/example/apc/AppConfig.java`

```java
package com.example.apc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public AuditAspect auditAspect() {
        return new AuditAspect();
    }

    @Bean
    public OrderService orderService() {
        return new OrderService();
    }

    @Bean
    public CatalogInfo catalogInfo() {
        return new CatalogInfo();
    }
}
```

`@Bean auditAspect()` đưa `AuditAspect` vào context. `@EnableAspectJAutoProxy` kích hoạt auto-proxy creator; `proxyTargetClass=true` buộc class-based proxy cho `OrderService` không triển khai interface. `CatalogInfo` cũng là Spring bean, nhưng không có advisor nào khớp nó trong cấu hình này.

### 6.7. `src/main/java/com/example/apc/DemoApp.java`

```java
package com.example.apc;

import org.springframework.aop.config.AopConfigUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApp {
    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            Object creator =
                    context.getBean(AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME);
            OrderService orderService = context.getBean(OrderService.class);
            CatalogInfo catalogInfo = context.getBean(CatalogInfo.class);
            AuditAspect aspect = context.getBean(AuditAspect.class);

            System.out.println("creatorIsBPP="
                    + (creator instanceof BeanPostProcessor));
            System.out.println("orderIsAopProxy=" + AopUtils.isAopProxy(orderService));
            System.out.println("catalogIsAopProxy=" + AopUtils.isAopProxy(catalogInfo));
            System.out.println("aspectIsAopProxy=" + AopUtils.isAopProxy(aspect));

            String result = orderService.place("O-42");
            System.out.println("result=" + result);
            System.out.println("catalog=" + catalogInfo.version());
            System.out.println("adviceCalls=" + aspect.calls());
        }
    }
}
```

Ở ví dụ Spring Framework này, `AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME` là tên hạ tầng nội bộ do Spring dùng cho auto-proxy creator. **Đây là API/chi tiết chẩn đoán thuộc cấu hình cụ thể**, không phải tên một application service để hệ thống bên ngoài dựa vào. Trong ứng dụng Spring Boot hoặc khi cấu hình AOP khác, không nên hardcode một class implementation duy nhất làm điều kiện hoạt động của hệ thống.

### 6.8. Chạy và đọc kết quả

Yêu cầu: JDK 17+, Maven, kết nối để lấy dependencies. Tại thư mục có `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.apc.DemoApp
```

Các dòng do chính chương trình in ra (không tính thông báo riêng của Maven/SLF4J nếu có):

```text
creatorIsBPP=true
orderIsAopProxy=true
catalogIsAopProxy=false
aspectIsAopProxy=false
[AUDIT] place
[ORDER] O-42
result=accepted:O-42
catalog=v1
adviceCalls=1
```

**Vì sao chương trình cho kết quả đó?**

- `creatorIsBPP=true`: Spring đã đăng ký một bean hạ tầng auto-proxy creator triển khai `BeanPostProcessor`.
- `orderIsAopProxy=true`: advice khớp `OrderService.place(..)`; Spring tạo proxy và trả proxy cho `context.getBean(OrderService.class)`.
- `catalogIsAopProxy=false`: `CatalogInfo.version()` không được advisor này chọn; không có lý do tạo AOP proxy cho `CatalogInfo` trong ví dụ.
- `aspectIsAopProxy=false`: class `@Aspect` được dùng để cung cấp advice, không phải target được Spring AOP tự proxy bằng chính hạ tầng `@AspectJ`.
- `[AUDIT]` xuất hiện **trước** `[ORDER]`: caller gọi qua proxy, `@Before` chạy rồi target method thực thi. `adviceCalls=1` xác nhận số lần chạy advice.

Điều được chứng minh ở đây là **sự tồn tại và kết quả của auto-proxy creator trong một Spring context cụ thể**. Không được suy diễn rằng mọi bean trong Spring đều bị proxy hoặc các giá trị `isAopProxy` trên luôn đúng khi thêm advisor/proxy khác vào application.

## 7. Cách auto-proxy creator chọn bean nào cần proxy

Trong mô hình hỗ trợ `@AspectJ`, luồng quyết định có thể hình dung:

```text
Bean vừa được tạo
    |
    v
Có phải bean hạ tầng/aspect/advisor cần loại trừ?
    | Có
    +--> Bỏ qua auto-proxy kiểu này
    |
    | Không
    v
Tìm advisor có thể áp dụng với class/method của bean
    |
    +--> Không có advisor phù hợp
    |        |
    |        +--> Giữ bean gốc
    |
    +--> Có advisor phù hợp
             |
             v
     Xác định chiến lược proxy
     (interface hoặc class-based)
             |
             v
     Gắn advisor/interceptor vào proxy
             |
             v
     Trả tham chiếu proxy cho caller
```

`AnnotationAwareAspectJAutoProxyCreator` nhận diện advisor beans và các `@Aspect` beans trong context. Nó sử dụng metadata pointcut để biết phương thức nào có thể được advice; không cần chạy thử mọi method rồi mới biết có match.

### 7.1. Không phải mọi bean đều được proxy

Một aspect chỉ áp dụng cho `execution(* com.example..service.*.*(..))` không nên tạo proxy cho toàn bộ lớp cấu hình, DAO hoặc plain DTO chỉ vì các class đó cũng là Spring beans.

**Có bean không được proxy là trạng thái đúng.** Khi xem log startup, đừng kết luận auto-proxy creator không chạy chỉ vì không thấy class mọi bean đổi thành tên proxy.

### 7.2. Auto-proxy creator không quyết định “gọi method nào” thay caller

Spring auto-proxy creator bọc bean trong quá trình quản lý. Sau đó **caller vẫn là nơi gọi method**, và interceptor chain thực hiện advice khi lời gọi đi qua proxy, pointcut phù hợp. Nếu gọi method khác không khớp, proxy có thể chuyển tiếp mà không chạy advice đó.

Với class-based proxy, một đối tượng có thể có nhiều method; việc class đủ điều kiện được bọc proxy không chứng minh rằng mọi method đều có advice. Dùng kiểm thử trên từng method/policy thay vì chỉ `AopUtils.isAopProxy()`.

### 7.3. Hỗ trợ advisor ngoài `@Aspect`

Trong Spring, các chính sách như transaction interception có thể dùng hạ tầng advisor và proxy tương tự. Tùy hạ tầng được bật, auto-proxy creator có thể phối hợp những advisor này; không nên tự tạo một proxy AOP khác ngoài Spring chỉ để thêm logging mà chưa xem advisor hiện hữu.

Cũng cần phân biệt:

- **`BeanNameAutoProxyCreator`**: auto-proxy dựa trên tên bean được cấu hình.
- **`DefaultAdvisorAutoProxyCreator`**: chọn advisors phù hợp cho target beans.
- **`AnnotationAwareAspectJAutoProxyCreator`**: chọn advisors và nhận diện aspects khai báo bằng `@Aspect`.

Đây là các cơ chế lựa chọn trong Spring AOP. Ứng dụng tiêu chuẩn nên cấu hình bằng annotation/XML/starter Spring cung cấp, không tự đăng ký nhiều auto-proxy creators mà không có kiến trúc rõ ràng.

## 8. Một auto-proxy creator hợp nhất không đồng nghĩa chỉ có một advice

Khi dùng nhiều nơi khai báo AOP (Java config, XML, transaction management), Spring có quy tắc **hợp nhất các auto-proxy creator chuẩn trong cùng context**. Ví dụ:

```java
@EnableAspectJAutoProxy(proxyTargetClass = false)
```

và XML:

```xml
<aop:aspectj-autoproxy proxy-target-class="true"/>
```

Khi cùng hiệu lực trong một container, hạ tầng Spring có thể áp dụng lựa chọn proxy mạnh hơn, ví dụ **class-based proxy** đã được yêu cầu bằng `true`. Không thể kết luận rằng câu lệnh annotation với `false` sẽ luôn thắng nếu XML hoặc tính năng Spring khác đã đặt `true`.

Nhưng hợp nhất creator **không loại bỏ các advice trùng chức năng**. Ví dụ:

- Có hai bean khác tên cùng mang class `@Aspect` với một `@Before` y hệt.
- Một Java `@Aspect` và một advisor XML cùng ghi một audit event vào chính method đó.
- Hai aspect khác nhau cố ý ghi log ở cùng join point.

Những trường hợp này có thể chạy nhiều advice. Hãy **đếm các aspect beans/advisors và quan sát method invocation**; không đếm số lượng dòng `@EnableAspectJAutoProxy` rồi suy ra số lần advice chạy.

**Phạm vi Spring Framework:** quy tắc hợp nhất áp dụng cho những hạ tầng proxy tham gia cơ chế chuẩn trong cùng context. Nếu ứng dụng có parent/child contexts, third-party `BeanPostProcessor`, scoped proxy hoặc những proxy được tạo thủ công, cần kiểm tra từng cơ chế và đường gọi cụ thể.

## 9. Những ranh giới của auto-proxy creator trong container

### 9.1. Chỉ nhìn thấy những bean trong phạm vi xử lý của nó

Spring đăng ký `BeanPostProcessor` **theo từng `ApplicationContext`**. Một creator ở parent context không tự post-process những bean chỉ được khởi tạo trong child context, và ngược lại. Với ứng dụng web hoặc hệ thống nhiều context, việc bật `@EnableAspectJAutoProxy` ở một tầng không mặc nhiên áp dụng advice cho mọi controller/service ở tầng khác.

Vì thế khi advice không chạy, hãy kiểm tra **context nào sở hữu target bean và aspect bean** trước khi đổi pointcut.

### 9.2. Bean được tạo quá sớm có thể bỏ lỡ post-processing

Khi `ApplicationContext` khởi động, nó cần tạo và đăng ký `BeanPostProcessor` sớm để áp dụng cho các bean thông thường. Những bean được khởi tạo **trong giai đoạn rất sớm**, đặc biệt chính các `BeanPostProcessor` và dependency trực tiếp của chúng, có thể **không đủ điều kiện để được mọi post-processor xử lý**, bao gồm auto-proxy creator. Log Spring thường cho biết bean không đủ điều kiện được xử lý bởi tất cả post-processors.

Các nguyên nhân hay gặp:

- Tự viết `BeanPostProcessor` và inject service nghiệp vụ vào constructor của nó, khiến service được tạo trước khi toàn bộ hậu xử lý sẵn sàng.
- Một factory method khai báo `BeanPostProcessor` theo cách buộc `@Configuration` class khởi tạo quá sớm.
- Chủ động `getBean()` trong giai đoạn chỉnh bean definitions, làm phá thứ tự lifecycle bình thường.

Cách xử lý là **tránh kéo bean nghiệp vụ vào giai đoạn đăng ký post-processors**. Khi tự viết `@Bean` trả về `BeanPostProcessor`, Spring khuyến nghị method `static` và ít dependency khi phù hợp; đừng áp dụng mẹo này tùy tiện cho mọi `@Bean`.

### 9.3. Self-invocation không đi qua proxy

```java
public class OrderService {
    public void checkout() {
        validate(); // this.validate(), gọi nội bộ trên target.
    }

    public void validate() {
        // Kiểm tra nghiệp vụ.
    }
}
```

Đây là **class fragment minh họa**. Nếu caller gọi `proxy.checkout()`, Spring có thể advice `checkout()`; nhưng lời gọi `validate()` từ bên trong target không đi qua proxy nên advice dành riêng cho `validate()` không chạy nhờ auto-proxy creator.

Creator đã làm nhiệm vụ **bọc bean**; nó không sửa mọi lời gọi `this.method()` trong bytecode. Cách đơn giản thường là chuyển phần được advice sang một bean khác để lời gọi đi qua dependency Spring quản lý.

### 9.4. Constructor và initialization callback không phải “lời gọi đã đi qua proxy”

Spring thường chạy `@PostConstruct`, `InitializingBean.afterPropertiesSet()` hoặc init method trên raw bean trong giai đoạn khởi tạo, trước khi tham chiếu proxy thông thường được trả cho caller. Một lời gọi nội bộ từ init callback không tự chạy Spring AOP advice.

Nếu cần thực hiện nghiệp vụ sau khi context được khởi tạo và đã có proxy, hãy chọn điểm khởi chạy phù hợp với lifecycle ứng dụng (ví dụ một component khác gọi qua bean Spring sau khi context sẵn sàng). Không dùng `@Before`/`@After` để thay thế `@PostConstruct`/`@PreDestroy`.

### 9.5. Object tự `new` hoặc final method

`new OrderService()` bên ngoài Spring container **không được creator xử lý**. Class-based proxy không chặn được method `final`; class `final` cũng không thể được subclass bởi CGLIB. Với JDK dynamic proxy, caller đi qua interface; class-only method không tự có mặt trên proxy interface. Các giới hạn này ảnh hưởng hiệu quả của auto-proxy creator ngay cả khi `@Aspect` và pointcut đúng.

## 10. Cách kiểm tra auto-proxy creator trong dự án đang chạy

### 10.1. Kiểm tra bean hạ tầng trong context

Với cấu hình Spring AOP chuẩn được kích hoạt, có thể dùng:

```java
boolean present = context.containsBean(
    org.springframework.aop.config.AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME
);
System.out.println("auto-proxy creator present = " + present);
```

Đây là **mã chẩn đoán đặt bên trong chương trình đang có biến `context`**, không phải Java file độc lập. Nếu không có bean theo tên hạ tầng này, hãy kiểm tra `@EnableAspectJAutoProxy`, XML hoặc auto-configuration của phiên bản Spring dùng trong ứng dụng. Không dựa duy nhất vào internal bean name cho monitoring sản xuất.

### 10.2. Kiểm tra proxy cho target cụ thể

```java
Object bean = context.getBean("orderService");
boolean isProxy = org.springframework.aop.support.AopUtils.isAopProxy(bean);
System.out.println("proxied = " + isProxy);
```

Nhận diện proxy là bước một. Nếu `true`, cần xác định **advice nào nằm trong proxy và có khớp method cần kiểm tra không**; một bean có thể được proxy bởi transaction hoặc scope mà không có audit advice cụ thể.

### 10.3. Quan sát advisors khi cần

```java
Object bean = context.getBean("orderService");

if (bean instanceof org.springframework.aop.framework.Advised advised) {
    for (org.springframework.aop.Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName());
    }
}
```

Đoạn trên là **fragment kiểm tra**, không có stdout cố định vì advisor types/order tùy ứng dụng và Spring version. Advisor list không đồng nghĩa số lần chạy advice: một advisor có thể có pointcut không khớp method đang gọi, và advisor chain có thể chứa cả hạ tầng Spring.

### 10.4. Kiểm tra hành vi thay vì chỉ kiểm tra cấu trúc

Một phép thử thuyết phục nên chứng minh:

1. Aspect bean được đăng ký đúng một lần theo thiết kế.
2. Target bean được lấy từ `ApplicationContext`, proxy strategy phù hợp.
3. Một method khớp pointcut chạy đúng số advice kỳ vọng.
4. Một method/bean không khớp pointcut **không** chạy advice này.
5. Exception và giá trị trả về của nghiệp vụ được giữ đúng.
6. Chế độ transaction, security hoặc async khác vẫn hoạt động theo cấu hình dự án.

Nếu advice bị thiếu, xử lý theo thứ tự **bean/context → auto-proxy setup → advisor/pointcut → proxy type → caller đi qua proxy**. Nếu chạy hai lần, kiểm tra có nhiều aspect beans/advisors hay hai lần gọi method; không tự kết luận do có hai dòng bật AOP.

## 11. Các lỗi cấu hình thường gặp

| Dấu hiệu | Nguyên nhân có thể | Cách kiểm tra |
| --- | --- | --- |
| `@Aspect` có trong source nhưng không có advice | Aspect chưa đăng ký thành Spring bean; chưa kích hoạt auto-proxy. | Kiểm tra `@Component`/`@Bean`, context và `@EnableAspectJAutoProxy`/Boot starter. |
| Bean được inject nhưng `AopUtils.isAopProxy() = false` | Không có advisor phù hợp, creator chưa được bật, hoặc bean đã tạo quá sớm. | Kiểm tra bean scope, điểm tạo bean, advisor và startup warnings. |
| AOP proxy có nhưng `@Before` không chạy | Pointcut không khớp method, self-invocation hoặc khác context. | Thử một lời gọi public qua proxy trực tiếp rồi đối chiếu pointcut. |
| Bean có tên class `$$SpringCGLIB$$` | Class-based proxy được tạo; đây có thể là kết quả bình thường. | Đối chiếu `proxyTargetClass` và lý do bean được proxy, không dựa vào tên class để đo advice. |
| `ClassCastException` khi ép proxy thành class triển khai | Đang dùng JDK dynamic proxy theo interface. | Inject interface hoặc điều chỉnh proxy strategy theo yêu cầu thật sự. |
| Aspect bị gọi hai lần trên một method | Nhiều aspect beans/advisors cùng khớp, hoặc caller thực sự gọi method hai lần. | Đếm aspect beans, advisor chain và correlation id từng invocation. |
| `Bean ... not eligible for auto-proxying` | Bean sinh quá sớm trong quá trình đăng ký `BeanPostProcessor`. | Kiểm tra các dependency/factory methods của post-processor. |
| AOP trong root context chạy, controller child context không chạy | Creator và target nằm ở context khác nhau. | Kiểm tra cấu trúc parent-child và cấu hình AOP cục bộ. |
| Thay đổi property Boot nhưng proxy vẫn khác kỳ vọng | Java/XML hoặc hạ tầng khác đang yêu cầu proxy strategy mạnh hơn. | Kiểm kê toàn bộ cấu hình proxy trong context. |
| Method `final` không được advice bởi CGLIB | Proxy subclass không override được method. | Thiết kế lại method/class hoặc dùng interface proxy nếu phù hợp. |

## 12. Quy trình triển khai và kiểm chứng thực tế

Trong một ứng dụng Spring mới hoặc khi đưa Spring AOP vào codebase cũ:

1. **Xác định `ApplicationContext` sở hữu bean đích.** Xem nơi tạo context, package scan và các XML được import.
2. **Bật đúng loại auto-proxy support.** Spring thuần với `@AspectJ` thường dùng `@EnableAspectJAutoProxy`; Spring Boot ưu tiên auto-configuration phù hợp dòng Boot.
3. **Đăng ký aspect beans.** `@Aspect` + `@Component` hoặc `@Bean`; kiểm tra không trùng đăng ký.
4. **Giới hạn pointcut.** Chỉ áp dụng advisor vào methods của beans cần hành vi dùng chung.
5. **Kiểm tra proxy policy.** Xác định JDK dynamic proxy hay class-based proxy; không gán `proxyTargetClass=true` một cách máy móc.
6. **Quan sát bean và lời gọi thực tế.** Dùng `AopUtils`, advisor inspection khi cần và một test kiểm tra chính xác advice count.
7. **Đánh giá lifecycle.** Tránh khởi tạo service quá sớm từ custom post-processors, tránh gọi method được advice trong constructor/init callback của chính target.
8. **Kiểm tra cấu hình chồng chéo.** Spring có thể hợp nhất creator, nhưng không tự hợp nhất nội dung của hai advice cùng trách nhiệm.
9. **Kiểm tra sau khi đổi module/context.** Nếu di chuyển service sang child context, đổi cách inject bean hoặc dùng scoped proxy, phải chạy lại test AOP.

### Tài liệu Spring chính thức

- Spring Framework — Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework — Declaring an Aspect: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/at-aspectj.html
- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — Auto-proxy facility: https://docs.spring.io/spring-framework/reference/core/aop-api/autoproxy.html
- Spring Framework — Container Extension Points / BeanPostProcessor: https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html
- Spring Framework — AnnotationAwareAspectJAutoProxyCreator API: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/aspectj/annotation/AnnotationAwareAspectJAutoProxyCreator.html
- Spring Framework — AbstractAutoProxyCreator API: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/framework/autoproxy/AbstractAutoProxyCreator.html

**Vai trò của auto-proxy creator trong `ApplicationContext` là biến cấu hình aspect/advisor của Spring thành các tham chiếu bean có khả năng interception, ngay trong quá trình container tạo và công bố beans.** Spring vẫn quản lý target bean và lifecycle, còn lời gọi thông qua proxy quyết định khi nào advice thực sự được thực thi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="auto-proxy-mechanics-006">Thống nhất cấu hình khi nhiều cơ chế Spring cùng yêu cầu proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Thống nhất cấu hình khi nhiều cơ chế Spring cùng yêu cầu proxy

## 1. Tại sao cần thống nhất auto-proxy trong Spring?

Một Spring bean có thể đồng thời chịu tác động của nhiều khả năng dựa trên AOP proxy:
- `@Aspect` với `@Before` hoặc `@Around` để quan sát lời gọi phương thức.
- `@Transactional`, được Spring gắn với `TransactionInterceptor` khi bật transaction management theo `AdviceMode.PROXY`.
- `@Cacheable`, `@CacheEvict` hoặc `@CachePut`, được Spring xử lý bằng cache interceptor khi bật caching theo chế độ proxy.
- Một số khả năng proxy khác, ví dụ `@Async`, có đường đăng ký hạ tầng riêng tùy phiên bản.

Ví dụ một method `placeOrder()` vừa có `@Transactional`, vừa khớp logging aspect. Nếu Spring tạo proxy mà không phối hợp các tính năng, mỗi cấu hình có thể cạnh tranh cách bọc bean, kiểu proxy hoặc vị trí chạy advice.

**Trong Spring Framework 6.x, các cấu hình proxy tiêu chuẩn như `@EnableAspectJAutoProxy`, `@EnableTransactionManagement` và `@EnableCaching` ở `AdviceMode.PROXY` dùng hạ tầng auto-proxy creator có khả năng hợp nhất trong cùng một `ApplicationContext`.** Các cơ chế vẫn giữ advisor riêng nhưng có thể cùng được áp dụng cho Spring proxy của bean. Đặc biệt, yêu cầu `proxyTargetClass=true` từ một cấu hình có thể quyết định kiểu proxy chung, ngay cả khi cấu hình khác chỉ định `false`.

Phạm vi của bài này là **Spring AOP theo proxy và Spring IoC**. Ta sẽ đi từ hạ tầng auto-proxy creator, quy tắc áp dụng tùy chọn, chuỗi advisor, ví dụ có thể chạy, đến cách kiểm tra khi Spring AOP cùng hoạt động với caching, transaction và async.

## 2. Ba thành phần cần phân biệt rõ

### 2.1. Auto-proxy creator: thành phần tạo proxy khi khởi tạo bean

Auto-proxy creator là một loại `BeanPostProcessor`. Khi container tạo Spring bean, nó kiểm tra các advisor phù hợp rồi trả về proxy nếu bean cần được advice. Trong hạ tầng chuẩn, Spring sử dụng tên bean nội bộ `org.springframework.aop.config.internalAutoProxyCreator` để phối hợp các đăng ký auto-proxy.

Các implementation thường gặp:
- `InfrastructureAdvisorAutoProxyCreator` xử lý infrastructure advisors.
- `AspectJAwareAdvisorAutoProxyCreator` bổ sung các quy tắc có liên quan đến AspectJ advisor.
- `AnnotationAwareAspectJAutoProxyCreator` có khả năng nhận diện các bean khai báo `@Aspect` rồi tạo advisor.

Khi một tính năng khác yêu cầu auto-proxy creator có năng lực cao hơn, Spring thực hiện cơ chế **escalation**: dùng implementation đáp ứng yêu cầu cao hơn cho hạ tầng chia sẻ. Vì thế, một auto-proxy creator có thể xử lý cả advisor của Spring transaction/cache và các advisor sinh từ `@Aspect`.

**Auto-proxy creator là bean hạ tầng**, không phải proxy của `ProductService`. Việc có một auto-proxy creator trong context và việc bao nhiêu Spring bean cần proxy là hai câu hỏi khác nhau.

### 2.2. Advisor và interceptor: logic từng tính năng

Một advisor thường liên kết điều kiện áp dụng (pointcut/method matching) với advice/interceptor. Các tính năng vẫn có hành vi độc lập:

| Cơ chế | Kích hoạt | Thành phần AOP tiêu biểu | Ý nghĩa khi method được gọi |
| --- | --- | --- | --- |
| Spring `@AspectJ` | `@EnableAspectJAutoProxy` hoặc AOP auto-configuration tương ứng | Advisor/advice được tạo từ `@Aspect` | Advice chạy khi method match pointcut và đi qua proxy |
| Transaction | `@EnableTransactionManagement` ở `PROXY` | Transaction advisor + `TransactionInterceptor` | Mở/tham gia/kết thúc transaction theo cấu hình |
| Caching | `@EnableCaching` ở `PROXY` | Cache advisor + `CacheInterceptor` | Cache miss gọi tiếp target, cache hit có thể trả luôn kết quả |
| Async trong Spring 6.1 | `@EnableAsync` ở `PROXY` | `AsyncAnnotationBeanPostProcessor` + `AsyncAnnotationAdvisor` | Thực hiện method qua task executor khi lời gọi qua proxy |

Không thể suy ra từ **một auto-proxy creator dùng chung** rằng cả caching, transaction và logging được thực hiện bởi cùng một interceptor. Chúng là các advisor/advice riêng, được chọn khi thích hợp cho method cụ thể.

### 2.3. Proxy thực tế: nơi client gọi method

```text
Một ApplicationContext
   |
   +-- @EnableAspectJAutoProxy --> Spring @Aspect advisors
   +-- @EnableCaching ----------> cache advisor/interceptor
   +-- @EnableTransactionManagement --> transaction advisor/interceptor
   |
   +-- Spring auto-proxy infrastructure dùng chung
          |
          +-- tạo bean OrderService
          +-- tìm các advisor phù hợp với bean/method
          +-- tạo proxy khi có advisor thích hợp
                           |
Client ----> Spring AOP proxy
                 |
                 +-- advice A (nếu match)
                 +-- advice B (nếu match)
                 +-- advice C (nếu match)
                 |
                 +-- target method (nếu chuỗi tiếp tục)
```

Một proxy có thể giữ nhiều advisor, nhưng **không phải mọi advisor đều chạy cho mọi method**. Spring tính chuỗi interceptor dựa trên method hiện được gọi, target class và điều kiện runtime khi cần.

Ngược lại, nếu một bean không khớp advisor nào thì `proxyTargetClass=true` chưa đủ lý do để Spring tạo AOP proxy cho bean ấy.

## 3. Tại sao `proxyTargetClass=true` có tác dụng trên nhiều cơ chế?

### 3.1. Những annotation cùng quyết định cấu hình

Giả sử một context đăng ký:

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
@EnableCaching(proxyTargetClass = true)
@EnableTransactionManagement(proxyTargetClass = false)
public class InfrastructureConfig {
    // Đây chỉ là đoạn minh họa lựa chọn proxy type.
    // Để cache/transaction thật sự hoạt động, cần đăng ký
    // CacheManager và TransactionManager phù hợp.
}
```

Mặc dù AspectJ AOP và transaction đặt `false`, caching đặt `true`. Spring áp dụng **yêu cầu proxy mạnh hơn**, tức class-based proxy, cho việc auto-proxy trong context. `false` nghĩa là không ép proxy theo lớp, chứ không phải yêu cầu tuyệt đối “cấm class-based proxy”.

| `@EnableAspectJAutoProxy` | `@EnableCaching` | `@EnableTransactionManagement` | Khuynh hướng kiểu proxy chung |
| --- | --- | --- | --- |
| `false` | `false` | `false` | JDK proxy khi bean có interface phù hợp; class proxy vẫn có thể cần nếu thiếu interface |
| `true` | `false` | `false` | Class-based proxy khi bean cần được proxy |
| `false` | `true` | `false` | Class-based proxy, kể cả bean được advise bởi aspect |
| `false` | `false` | `true` | Class-based proxy, kể cả bean chỉ có caching hoặc aspect advisor |
| `true` | `true` | `false` | Class-based proxy |
| `false` | `true` | `true` | Class-based proxy |

Bảng áp dụng khi các cấu hình nói trên cùng hoạt động ở **proxy mode và trong cùng context**, trong Spring Framework 6.x. Nếu có hạ tầng proxy tùy chỉnh hoặc framework version khác, cần kiểm tra runtime.

### 3.2. Class-based proxy và interface-based proxy không tương đương

- **JDK dynamic proxy** triển khai interface của target. Khi client lấy bean qua interface thì hợp lý; khi lấy theo lớp triển khai, có thể thất bại vì JDK proxy không là subclass của lớp đó.
- **Class-based proxy (CGLIB)** kế thừa lớp target, do đó thường có thể lấy bằng kiểu class. Tuy nhiên, class `final` không thể subclass để proxy, method `final` không thể override, method `private` không thể advice theo phương thức proxy này.
- **Self-invocation** `this.otherMethod()` trên target vẫn bỏ qua Spring AOP proxy, bất kể JDK hay CGLIB.

Không nên bật class-based proxy toàn cục chỉ để giải quyết một method nội bộ: bản chất self-invocation không được proxy tự động sửa.

### 3.3. `exposeProxy` cũng liên quan hạ tầng, nhưng có mục đích khác

`@EnableAspectJAutoProxy` còn có:

```java
@EnableAspectJAutoProxy(
    proxyTargetClass = true,
    exposeProxy = true
)
```

Khi `exposeProxy=true`, mã đang thực thi trong một lời gọi qua AOP proxy có thể truy cập proxy hiện hành qua `AopContext.currentProxy()`. Đó là cơ chế hỗ trợ đặc biệt, thường thông qua `ThreadLocal`. Nó **không khiến self-invocation đi qua proxy tự động**; lập trình viên phải chủ động thay đổi cách gọi và chấp nhận phụ thuộc Spring AOP.

Các lựa chọn bật thêm khả năng như class-based proxy/expose proxy có thể được tích hợp vào cấu hình auto-proxy chung trong context. Đừng nhầm `exposeProxy` với một lựa chọn thay đổi số lượng aspect bean, phạm vi transaction hay loại cache.

## 4. Unified auto-proxy creator không có nghĩa thứ tự advice được tự giải quyết

Một lời gọi đến `OrderService.place(...)` có thể khớp cả logging, transaction và cache advisor.

```text
Caller
  |
  v
proxy.place(...)
  |
  +-- logging @Around?           (thứ tự phụ thuộc @Order)
  +-- TransactionInterceptor?    (thứ tự phụ thuộc transaction order)
  +-- CacheInterceptor?          (thứ tự phụ thuộc caching order)
  |
  +-- target.place(...)          (chỉ chạy nếu chuỗi được tiếp tục)
```

Nếu `CacheInterceptor` đứng **ngoài** `TransactionInterceptor` và gặp cache hit, nó có thể trả kết quả trước khi đi tới transaction interceptor. Nếu transaction interceptor đứng **ngoài** cache, một transaction vẫn có thể được bắt đầu ngay cả khi cache hit.

Tương tự, một aspect dùng `@Before` đứng ngoài cache interceptor có thể log mọi lời gọi, kể cả cache hit. Nếu advice nằm sau cache interceptor trong chain, cache hit có thể trả kết quả trước khi advice được thực thi.

**Thứ tự annotation trên `@Configuration` không quyết định advice order.** Cần dùng `@Order`/`Ordered` cho aspect và tham số `order` của từng tính năng.

Ví dụ cấu hình precedence:

```java
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceAspect {

    @Before("execution(* com.example.service..*.*(..))")
    public void beforeCall() {
        // Ưu tiên cao, có thể nằm ngoài advisor thấp hơn.
    }
}
```

```java
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE)
```

Mã trên là snippet minh họa (cần import, bean registration và service thật nếu đưa vào project). Advisor có số `order` **nhỏ hơn** thường có precedence cao hơn; advice ở phía ngoài được vào trước và thoát sau khi chuỗi tiếp tục bình thường. Khi nhiều advisor có cùng precedence hoặc có cấu hình đặc biệt, cần xác minh thứ tự thực tế bằng integration test.

### Ba sự thật khác nhau

1. **Auto-proxy creator được thống nhất:** Spring phối hợp hạ tầng để tạo proxy.
2. **Các advisor được tổng hợp:** Một proxy có thể chứa nhiều advisor tương ứng những tính năng khác nhau.
3. **Kết quả invocation phụ thuộc thứ tự:** Advice nào chạy, việc có gọi target không và exception đi theo đường nào còn phụ thuộc pointcut, order và cách interceptor gọi tiếp chain.

Không dùng việc nhìn thấy `AopUtils.isAopProxy(bean) == true` để khẳng định cả ba tính năng đã thực thi đúng.

## 5. Ví dụ chạy được: kết hợp `@Aspect` và `@Cacheable`

Ví dụ này cô lập hai cơ chế Spring thường cùng tạo proxy, dùng **Java 17, Spring Framework 6.1.14 và Gradle**. Không dùng Spring Boot nên ta chủ động chỉ định chính xác các lựa chọn `proxyTargetClass`.

Mục tiêu kiểm tra:
- `@EnableAspectJAutoProxy(proxyTargetClass=false)` không phủ quyết `@EnableCaching(proxyTargetClass=true)`.
- Bean triển khai interface vẫn trở thành **class-based AOP proxy**.
- Aspect logging có precedence cao có thể quan sát hai lời gọi, trong khi cache làm target chạy một lần.

### 5.1. Tạo project

```text
spring-aop-combined/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/combined/
    ├── CatalogApi.java
    ├── CatalogService.java
    ├── TraceAspect.java
    ├── AppConfig.java
    └── DemoApp.java
```

`settings.gradle`:

```groovy
rootProject.name = 'spring-aop-combined'
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
    mainClass = 'demo.combined.DemoApp'
}
```

Ở đây `spring-context` cung cấp `ApplicationContext`, cache API và `ConcurrentMapCacheManager`; `spring-aop` cung cấp proxying; `aspectjweaver` cung cấp các thành phần cần thiết để xử lý biểu thức trong `@Aspect`.

### 5.2. Interface và target

`src/main/java/demo/combined/CatalogApi.java`:

```java
package demo.combined;

public interface CatalogApi {
    String find(String sku);
}
```

`src/main/java/demo/combined/CatalogService.java`:

```java
package demo.combined;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.cache.annotation.Cacheable;

public class CatalogService implements CatalogApi {
    private final AtomicInteger calls = new AtomicInteger();

    @Override
    @Cacheable(cacheNames = "catalog")
    public String find(String sku) {
        int count = calls.incrementAndGet();
        System.out.println("target#" + count);
        return "item:" + sku;
    }

    public int targetCallCount() {
        return calls.get();
    }
}
```

`find(...)` trả kết quả cố định theo SKU. Cache manager có thể lưu `"item:sku-7"` sau lần đầu. `targetCallCount()` chỉ đo số lần logic method trong target thực sự chạy.

### 5.3. Aspect có precedence cao

`src/main/java/demo/combined/TraceAspect.java`:

```java
package demo.combined;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceAspect {

    @Before("execution(public * demo.combined.CatalogService.find(..))")
    public void beforeFind() {
        System.out.println("aspect:before");
    }
}
```

`@Aspect` khai báo Spring AOP aspect; `@Before` có pointcut chỉ khớp method `CatalogService.find(..)`. `@Order` được cố định để advice đứng phía ngoài cache advice trong ví dụ; ở cache hit, logging vẫn có thể chạy dù target không được gọi.

### 5.4. Một context bật hai cơ chế

`src/main/java/demo/combined/AppConfig.java`:

```java
package demo.combined;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
@EnableCaching(
    proxyTargetClass = true,
    order = Ordered.LOWEST_PRECEDENCE
)
public class AppConfig {

    @Bean
    public CatalogService catalogService() {
        return new CatalogService();
    }

    @Bean
    public TraceAspect traceAspect() {
        return new TraceAspect();
    }

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("catalog");
    }
}
```

Ba bean được đăng ký tường minh để không bị ảnh hưởng bởi component scanning. `TraceAspect` không cần `@Component` vì đã là một `@Bean`; một lớp chỉ được gắn `@Aspect` sẽ không tự thành Spring bean nếu không được đăng ký.

`@EnableCaching` hoạt động ở proxy mode mặc định, yêu cầu class proxy và đăng ký cache advisor. `@EnableAspectJAutoProxy` bật nhận diện aspect. Tùy chọn `true` của caching thắng lựa chọn `false` của aspect trong hạ tầng auto-proxy chung.

### 5.5. Chạy chương trình

`src/main/java/demo/combined/DemoApp.java`:

```java
package demo.combined;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApp {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            CatalogApi byInterface = context.getBean(CatalogApi.class);
            CatalogService byClass = context.getBean(CatalogService.class);

            System.out.println("sameBean=" + (byInterface == byClass));
            System.out.println("isAopProxy=" +
                               AopUtils.isAopProxy(byInterface));
            System.out.println("isClassProxy=" +
                               AopUtils.isCglibProxy(byInterface));

            System.out.println("first=" + byInterface.find("sku-7"));
            System.out.println("second=" + byInterface.find("sku-7"));
            System.out.println("targetCalls=" + byClass.targetCallCount());
        }
    }
}
```

Dùng một Gradle installation có thể chạy project, tại thư mục chứa `build.gradle`:

```bash
gradle run
```

Nếu project có Gradle Wrapper, dùng `./gradlew run` trên Linux/macOS hoặc `gradlew.bat run` trên Windows.

Các dòng **do chương trình Java in ra** (bỏ qua thông tin build/logging từ công cụ):

```text
sameBean=true
isAopProxy=true
isClassProxy=true
aspect:before
target#1
first=item:sku-7
aspect:before
second=item:sku-7
targetCalls=1
```

Ý nghĩa:
1. `sameBean=true`: lấy theo interface hay class đều trả về đúng một Spring-managed singleton/proxy.
2. `isAopProxy=true`: bean đã được Spring AOP bọc proxy.
3. `isClassProxy=true`: dù aspect khai báo `proxyTargetClass=false`, caching đã yêu cầu `true`.
4. `aspect:before` xuất hiện **hai lần** vì aspect có precedence cao hơn cache advisor, quan sát cả cache hit.
5. `target#1` xuất hiện **một lần**: caching trả kết quả của lời gọi thứ hai từ `ConcurrentMapCacheManager`, không gọi lại method target.
6. `targetCalls=1` chứng thực điều đó. Kiểm tra bộ đếm target khác với việc chỉ kiểm tra hai string trả về bằng nhau.

Đây là ví dụ kiểm chứng cách Spring phối hợp hai cơ chế. Nó không mặc định rằng mọi proxy do người dùng tự tạo đều được hợp nhất.

## 6. Thử biến thể để thấy cấu hình nào quyết định proxy

Dựa trên mã ví dụ, bạn có thể thay **một cấu hình tại một thời điểm**.

### Biến thể A: bỏ class-based request từ caching

Đổi:

```java
@EnableCaching(proxyTargetClass = false)
```

Nếu không có cấu hình khác yêu cầu class-based proxy, `CatalogService` triển khai `CatalogApi` thường được bọc bởi **JDK dynamic proxy**. Khi đó:
- `context.getBean(CatalogApi.class)` vẫn hợp lệ.
- `context.getBean(CatalogService.class)` có thể không lấy được JDK proxy vì proxy không kế thừa `CatalogService`.
- `AopUtils.isJdkDynamicProxy(bean)` là phép kiểm tra đúng, sau khi sửa `DemoApp` để không lookup qua kiểu class.

Không dùng lại chương trình gốc không chỉnh sửa để kết luận JDK proxy “bị lỗi” khi lookup bằng class.

### Biến thể B: chỉ bật `@EnableCaching`

Giữ cache manager và bean nhưng bỏ `@EnableAspectJAutoProxy`. Caching infrastructure vẫn xử lý `@Cacheable`; nó không tự bảo đảm diễn giải bean `@Aspect` thành aspect advisor. Không nên kỳ vọng log `aspect:before` vẫn có nếu không còn hạ tầng xử lý `@AspectJ` nào khác.

### Biến thể C: chỉ bật `@EnableAspectJAutoProxy`

Bỏ `@EnableCaching`. Aspect vẫn có thể advice `find` qua proxy, nhưng annotation `@Cacheable` trên target không tự kích hoạt caching. Hai lần gọi target sẽ tăng bộ đếm lên hai.

### Biến thể D: đổi thứ tự advice

Nếu cache interceptor ở phía ngoài và có cache hit, advice của aspect phía trong **có thể không chạy** lần thứ hai vì cache trả kết quả trước khi tiếp tục chuỗi. Vì thế thứ tự interceptor là hành vi cần thiết kế và kiểm chứng, không phải sự tình cờ của cách Spring hợp nhất hạ tầng.

## 7. Kiểm tra auto-proxy creator và advisor tại runtime

Nếu đang có biến `context` là `AnnotationConfigApplicationContext` trong ví dụ trên, có thể đọc bean hạ tầng (đoạn mã kiểm tra bổ sung, không phải chương trình độc lập):

```java
import org.springframework.aop.config.AopConfigUtils;

String autoProxyCreatorName =
    AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME;
Object creator = context.getBean(autoProxyCreatorName);
System.out.println(creator.getClass().getName());
```

Trong cấu hình có `@AspectJ`, có thể thấy `AnnotationAwareAspectJAutoProxyCreator` vì nó hỗ trợ phát hiện các advisor khai báo từ `@Aspect` ngoài infrastructure advisors. Đây là chi tiết hạ tầng dành cho chẩn đoán, không phải lớp nên phụ thuộc trong business code.

Để xem advisors trên proxy, khi có biến `byInterface`:

```java
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;

if (byInterface instanceof Advised advised) {
    for (Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName());
    }
}
```

Có thể nhìn thấy advisor liên quan tới cache và aspect. Thứ tự được in phản ánh cấu hình proxy thực tế; danh sách không phải hợp đồng API bất biến giữa các phiên bản. Một advisor trong proxy không nhất thiết chạy trên mọi method; pointcut và method matcher tiếp tục quyết định invocation.

**Lưu ý về double-proxy:** Trong hạ tầng Spring chuẩn, thống nhất auto-proxy creator giúp hạn chế việc mỗi khai báo tạo một lớp proxy mới. Nhưng các `BeanPostProcessor` riêng hoặc proxy factory tự viết vẫn có thể tạo thêm một lớp proxy. Đừng lấy `AopUtils.isAopProxy` làm bằng chứng không có proxy lồng; cần inspect `Advised`/target source nếu chẩn đoán tình huống này.

## 8. Thêm `@Transactional`: advisor chung, logic giao dịch độc lập

Khi bật `@EnableTransactionManagement(mode=AdviceMode.PROXY)`, Spring đăng ký advisor cho `@Transactional`.

Ví dụ minh họa cấu hình, **không phải chương trình chạy độc lập** vì thiếu `TransactionManager` và datasource thích hợp:

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
@EnableCaching(proxyTargetClass = false)
@EnableTransactionManagement(
    mode = AdviceMode.PROXY,
    proxyTargetClass = true,
    order = 200
)
class ServiceAopConfig {
    // Cần có CacheManager và TransactionManager thật.
}
```

`proxyTargetClass=true` ở transaction áp dụng yêu cầu class-based proxy trong cùng hạ tầng, kể cả bean được advise bởi aspect hoặc cache. Tuy nhiên:
- **Không có transaction manager** thì proxy/advisor không thể tự thực hiện một database transaction hợp lệ.
- `order=200` có ý nghĩa ordering của transaction advisor, không đổi thứ tự tạo Spring bean tùy ý.
- Thứ tự transaction/cache quan trọng: nếu cache interceptor đứng ngoài và trả ở cache hit, phần chuỗi bên trong có thể không mở transaction. Nếu transaction interceptor ở ngoài cache, transaction có thể bắt đầu trước khi caching quyết định kết quả.
- Cache invalidation sau commit thành công là một yêu cầu nghiệp vụ riêng; không được đảm bảo chỉ bởi việc cùng dùng Spring proxy.

**Chế độ AspectJ khác chế độ PROXY:** `@EnableTransactionManagement(mode=AdviceMode.ASPECTJ)` dùng transaction aspect với weaving khi hạ tầng tương ứng được cấu hình; khi đó `proxyTargetClass` của nhánh transaction này không có tác dụng. Không áp dụng quy tắc hợp nhất auto-proxy của `PROXY` để suy diễn rằng weaving cũng đi qua cùng proxy.

Tương tự, `@EnableCaching(mode=AdviceMode.ASPECTJ)` là một mô hình khác, cần `spring-aspects` và cấu hình weaving. Ví dụ trên chỉ dùng proxy mode.

## 9. `@Async` và thay đổi theo phiên bản Spring

Trong **Spring Framework 6.1**, `@EnableAsync` không hoàn toàn là cùng một đường đăng ký auto-proxy creator với `@EnableAspectJAutoProxy`/cache/transaction.

- `@EnableAsync` thường dùng `AsyncAnnotationBeanPostProcessor`.
- Bộ xử lý này có thể thêm `AsyncAnnotationAdvisor` vào **AOP proxy đã có**, hoặc tạo proxy mới khi cần.
- `order` mặc định của post-processor hướng tới việc xử lý sau các post-processor khác để có thể bổ sung advisor vào proxy thay vì bọc lại một lớp mới.
- `@EnableAsync(proxyTargetClass=true)` vẫn có tác động về lựa chọn class-based proxy đối với các bean cần proxy khác trong context theo Javadoc Spring 6.1, nhưng không vì thế mà kết luận **mọi BeanPostProcessor đều chính là một instance auto-proxy creator**.

**Theo tài liệu Spring Framework 7.0**, cơ chế thống nhất global default proxy settings đã được mở rộng cho các proxy processor riêng lẻ, ví dụ `@EnableAsync`. Do đó nếu ứng dụng dùng Spring 7.0, đọc reference/Javadoc của **đúng release** trước khi suy luận hành vi chi tiết giống Spring 6.1.

`@EnableScheduling` lại thiên về đăng ký method chạy theo lịch và không mặc nhiên đồng nghĩa tạo thêm AOP auto-proxy creator cho mọi scheduled method. Tương tự, các cấu hình security hoặc interceptor do người dùng viết có thể dùng cách tích hợp riêng; cần kiểm tra thật trước khi kết luận số lượng proxy.

## 10. Trong Spring Boot, ai quyết định proxy type?

Spring Boot có auto-configuration Spring AOP khi dependency phù hợp được nạp (thường qua `spring-boot-starter-aop`). Ở **Spring Boot 3.3**, class-based proxy thường là mặc định của AOP auto-configuration khi không đổi property:

```properties
spring.aop.proxy-target-class=true
```

Có thể đổi hướng bằng:

```properties
spring.aop.proxy-target-class=false
```

Tuy nhiên, nếu ứng dụng cùng context còn có `@EnableCaching(proxyTargetClass=true)` hay `@EnableTransactionManagement(proxyTargetClass=true)`, yêu cầu class proxy mạnh hơn có thể vẫn chi phối việc auto-proxy. Vì vậy chỉ nhìn một Boot property chưa đủ để kết luận proxy cuối cùng thuộc JDK hay CGLIB.

Ngoài ra, ứng dụng Boot có thể có thêm auto-configured advisors (transaction, cache, security...). Muốn hiểu một method cần xem cả dependency, auto-configurations, context sở hữu bean, advisor và method thật sự được gọi. Khi giải thích behavior nên ghi rõ phiên bản Boot/Spring đang chạy.

## 11. Lỗi phổ biến và cách kiểm chứng

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý/kiểm tra |
| --- | --- | --- |
| `@EnableAspectJAutoProxy(proxyTargetClass=false)` nhưng kết quả vẫn là CGLIB | Caching, transaction hoặc Boot đã yêu cầu `true` | Tập hợp mọi cấu hình proxy của cùng context; dùng `AopUtils.isCglibProxy`. |
| Bean có proxy nhưng `@Before` không chạy | Aspect chưa là bean, AOP chưa bật, pointcut không khớp hoặc lời gọi không qua proxy | Kiểm tra aspect bean, advisor, pointcut và đường gọi. |
| Aspect chạy hai lần, target chỉ chạy một lần | Aspect ngoài caching; cache hit không gọi target | Đây có thể là hành vi đúng, tùy nhu cầu logging. |
| Aspect chỉ chạy lần đầu khi gọi method được cache | Cache interceptor đứng ngoài aspect và trả ở cache hit | Kiểm tra `@Order` và `order` của cache advisor. |
| Lấy bean theo class thất bại | Bean đang có JDK dynamic proxy | Inject interface hoặc cấu hình class-based proxy khi thật sự cần. |
| Transaction không hoạt động dù `@Transactional` hiện diện | Thiếu/sai `TransactionManager`, method không khớp hoặc self-invocation | Kiểm tra manager, advisor, transaction mode và lời gọi qua proxy. |
| Caching không có tác dụng | Chưa bật `@EnableCaching`, thiếu `CacheManager`, self-invocation hoặc method không phù hợp | Xác minh cấu hình cache, key, số lần chạy target. |
| Sau khi bật CGLIB, một bean `final` gặp lỗi | Không tạo được subclass proxy | Thiết kế class có thể proxy hoặc giữ interface-based proxy thích hợp. |
| Cấu hình AOP nằm ở parent mà bean ở child không nhận proxy | Auto-proxy creator là hạ tầng **theo context** | Bật cơ chế ở đúng context tạo bean; kiểm tra bean ownership. |
| Có nhiều proxy lồng | Custom post-processors hoặc cơ chế đặc biệt bọc proxy thêm | Kiểm tra `Advised`, target source và thứ tự BeanPostProcessor. |
| Behavior async khác tài liệu | So sánh nhầm phiên bản hoặc nhầm processor async với auto-proxy creator tiêu chuẩn | Đối chiếu Spring Framework 6.x/7.0 và `AsyncAnnotationBeanPostProcessor`. |

### Quy trình debug gọn nhưng đủ bằng chứng

1. **Context:** Xác định `ApplicationContext` nào tạo bean và nhận các `@Enable...`.
2. **Hạ tầng:** Có `internalAutoProxyCreator` không? Có `AsyncAnnotationBeanPostProcessor` hoặc custom processors khác không?
3. **Proxy:** Spring cấp JDK proxy hay CGLIB? `AopUtils.isAopProxy`, `isJdkDynamicProxy`, `isCglibProxy`.
4. **Advisor:** Trên proxy có advisor từ `@Aspect`, cache và transaction đúng như dự kiến không?
5. **Order:** Advice nào ở ngoài/trong? Cache hit có dừng chuỗi trước target/transaction không?
6. **Target:** Số lần chạy method nghiệp vụ có phù hợp không? Có self-invocation hoặc method `final`/`private` không?
7. **Phiên bản:** Với Spring 6.x, Spring 7.0 hoặc Boot khác nhau, xác minh defaults và cơ chế processor riêng.

## 12. Kết luận áp dụng

Spring thống nhất các đăng ký **auto-proxy creator tiêu chuẩn trong một context** để nhiều cơ chế dựa trên proxy có thể cùng làm việc với một bean. Việc “thống nhất” có hai kết quả thực tế: hạ tầng đủ khả năng nhận diện các advisor khác nhau và những tùy chọn mạnh như `proxyTargetClass=true` được áp dụng nhất quán.

Tuy nhiên, ba điều phải luôn được tách riêng: **ai tạo proxy**, **advisor nào áp dụng**, và **interceptor chạy theo thứ tự nào**. Việc dùng cùng proxy không thay thế yêu cầu có `CacheManager`, `TransactionManager`, aspect bean hay việc lời gọi đi qua proxy. Thứ tự logging–cache–transaction và đặc thù `@Async` vẫn phải được xác minh theo context và phiên bản Spring cụ thể.

### Tài liệu Spring chính thức

- [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): unified auto-proxy creator và class/interface proxy.
- [@AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): bật `@Aspect`-based proxy.
- [Advice Ordering](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): precedence của advice.
- [Declarative Annotation-based Caching](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html): `@EnableCaching`, proxy mode, order và cache hit.
- [@EnableTransactionManagement Javadoc (6.1.14)](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/transaction/annotation/EnableTransactionManagement.html): transaction advisor, `proxyTargetClass` và `AdviceMode`.
- [@EnableAsync Javadoc (6.1.8)](https://docs.spring.io/spring-framework/docs/6.1.8/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html): `AsyncAnnotationBeanPostProcessor`, `proxyTargetClass` và thứ tự.
- [AsyncAnnotationBeanPostProcessor Javadoc (6.1.2)](https://docs.spring.io/spring-framework/docs/6.1.2/javadoc-api/org/springframework/scheduling/annotation/AsyncAnnotationBeanPostProcessor.html): thêm async advisor lên proxy đã có.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="auto-proxy-mechanics-007">Bean khởi tạo quá sớm và tình trạng không đủ điều kiện auto-proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Bean khởi tạo quá sớm và tình trạng không đủ điều kiện auto-proxy trong Spring AOP

## 1. Vì sao Spring báo bean “not eligible for auto-proxying”?

Khi ứng dụng dùng Spring AOP, một số Spring bean phải được **tạo sau khi hệ thống BeanPostProcessor (BPP) đã sẵn sàng** để auto-proxy creator có thể xem xét và bọc bean bằng proxy. Nếu một bean bị khởi tạo **quá sớm**, trong lúc Spring còn đang tìm, khởi tạo hoặc đăng ký các BPP, bean ấy có thể chỉ đi qua một phần chuỗi BPP. Kết quả là bean đã tạo có thể **không được bọc bằng Spring AOP proxy**, dù kiểu hoặc phương thức của nó có vẻ khớp một `@Aspect` advice.

Thông báo thường gặp:

~~~text
Bean 'someBean' of type [example.SomeBean] is not eligible for getting processed
by all BeanPostProcessors (for example: not eligible for auto-proxying)
~~~

Thông điệp trên là **dạng minh họa**, không phải bản ghi được cam kết chính xác từng chữ ở mọi phiên bản Spring: bean name, class name và thông tin bổ sung có thể khác. Nó nói rằng bean đã được tạo khi toàn bộ các post-processor dự kiến chưa được đăng ký. **Không tự động đồng nghĩa bean có lỗi, hoặc chắc chắn thiếu mọi loại proxy**; cần kiểm tra proxy thực tế và chuỗi khởi tạo. Tuy nhiên, với Spring AOP, đây là dấu hiệu rất đáng chú ý khi advice không chạy.

Phạm vi bài tập trung vào **auto-proxying của Spring Framework**, thứ tự đăng ký BeanPostProcessor, quan hệ phụ thuộc khiến bean được tạo sớm, bằng chứng quan sát và cách xử lý. Không cần mở rộng sang kiến thức AspectJ weaving độc lập.

## 2. Cơ chế gây ra tình trạng này

### 2.1. Auto-proxy creator cũng là BeanPostProcessor

Đối với Spring AOP kiểu `@AspectJ`, `@EnableAspectJAutoProxy` đăng ký hạ tầng auto-proxy creator của Spring (tiêu biểu `AnnotationAwareAspectJAutoProxyCreator`, kế thừa `AbstractAutoProxyCreator`). Hạ tầng này là một **BeanPostProcessor** chuyên trách: khi một Spring bean được tạo, nó tìm các advisor phù hợp rồi có thể trả về proxy thay cho target.

Luồng chuẩn, rút gọn:

~~~text
Đăng ký BeanDefinition
       |
       v
Spring tìm và khởi tạo các BeanPostProcessor
       |
       v
Spring đăng ký đầy đủ BPP, gồm auto-proxy creator
       |
       v
Tạo các application bean thông thường
       |
       v
postProcessBeforeInitialization(...)
       |
       v
@PostConstruct / InitializingBean / custom init method
       |
       v
postProcessAfterInitialization(...)
       |
       +--> advisor không áp dụng: bean gốc
       |
       +--> advisor áp dụng: Spring AOP proxy
                                    |
                                    v
                         Bean nhận bởi caller
                                    |
                                    v
                        proxy -> advice -> target
~~~

Đây là luồng thường gặp cho singleton bean thông thường. Spring còn có các extension point hỗ trợ tham chiếu sớm, ví dụ `SmartInstantiationAwareBeanPostProcessor`, và các tình huống dependency đặc biệt; sơ đồ không được hiểu là thứ tự gọi tuyệt đối cho mọi loại bean.

Auto-proxy creator đánh giá bean trong **quá trình tạo bean**, không chạy lại mỗi lần có method invocation. Sau khi có proxy, một lời gọi đi qua proxy mới vào chuỗi advice. Khi bean được khởi tạo trước lúc auto-proxy creator tham gia đầy đủ, việc gọi method về sau **không tự làm Spring bọc lại instance đó**.

### 2.2. Giai đoạn đặc biệt để tạo và đăng ký BPP

`ApplicationContext` cần chuẩn bị BPP trước khi tạo hàng loạt application beans. Để làm được vậy, Spring chủ động tìm những bean có kiểu `BeanPostProcessor`, khởi tạo chúng **sớm** và đăng ký vào container. Những BPP đã khởi tạo, cùng bean được kéo vào làm dependency của chúng, có thể tồn tại **trước lúc mọi BPP khác, bao gồm auto-proxy creator, được đăng ký đầy đủ**.

Một chuỗi dễ gây lỗi:

~~~text
ApplicationContext.refresh()
          |
          v
Bắt đầu đăng ký BeanPostProcessor
          |
          v
Khởi tạo customAuditPostProcessor
          |
          +--> constructor/ @Bean parameter yêu cầu paymentService
                              |
                              v
                     paymentService bị tạo ngay
                              |
                              v
                     Chưa có đủ BPP để xét auto-proxy
                              |
                              v
                     Bean có thể ở dạng target gốc
          |
          v
Các BPP còn lại (bao gồm auto-proxy creator) được đăng ký
          |
          v
ApplicationContext tiếp tục tạo các bean khác
          |
          v
paymentService đã tồn tại từ trước; không được tự tạo lại
~~~

Trong một số tình huống, dependency của BPP có thể bị tạo sớm do tìm kiếm theo kiểu, `@Autowired`, `@Resource`, lời gọi thủ công tới `getBean()`, hoặc method `@Bean` tạo BPP cần một service. Không nhất thiết phải có một explicit call `new Service()` trong cấu hình để vấn đề xuất hiện.

Điểm cốt lõi: **Spring AOP cũng phụ thuộc vào BeanPostProcessor**. Bean sinh ra trong chính giai đoạn bootstrap các BPP có thể không có cơ hội được xử lý bởi toàn bộ auto-proxy pipeline.

### 2.3. BeanPostProcessor và các dependency của nó

Theo tài liệu Spring, chính các instance `BeanPostProcessor` và bean chúng trực tiếp tham chiếu thường **không đủ điều kiện auto-proxy như những application bean được tạo bình thường sau đó**. Một ví dụ thường thấy là BPP nghiệp vụ được inject một service vốn lại cần `@Before`/`@Around` hoặc `@Transactional` dạng proxy.

Tuy nhiên, cần đọc cảnh báo theo logic thay vì kết luận quá mức:

- Nó chủ yếu phát hiện **thời điểm và phạm vi post-processing**: bean đã được tạo khi số lượng BPP đăng ký thấp hơn mục tiêu dự kiến.
- Nó **không** khẳng định một pointcut cụ thể sẽ khớp hay không; còn phải kiểm tra aspect và advisor.
- Không phải mọi BPP đều thiết kế để trở thành đối tượng được advice; một số bean hạ tầng Spring được cố ý loại khỏi auto-proxy.
- Nếu ứng dụng còn cơ chế proxy khác hoặc tạo proxy trực tiếp, tình trạng cụ thể cần xác nhận bằng loại bean thực nhận và advisor của nó.
- Đừng cố “sửa” bằng cách bật AspectJ weaver vì vấn đề đang nằm ở **Spring container lifecycle và Spring AOP proxy**.

## 3. Các nguyên nhân thực tế trong cấu hình Spring

| Nguyên nhân | Tại sao gây khởi tạo sớm? | Dấu hiệu |
| --- | --- | --- |
| Constructor của custom BPP nhận application service | Container phải dựng service để dựng BPP | Service bị khởi tạo trong giai đoạn BPP registration |
| Static `@Bean` tạo BPP có tham số service | Static tránh khởi tạo config class nhưng **không tránh dependency service được inject sớm** | Cảnh báo cho service được truyền vào |
| Non-static `@Bean` trả `BeanPostProcessor` | Muốn gọi factory method, Spring phải tạo cả configuration object sớm | Configuration class và các dependency liên quan có thể không đủ post-processing |
| BPP tự `getBean("orderService")` lúc construction | Kéo business bean ra trước thời điểm sẵn sàng của auto-proxy creator | BPP startup làm service xuất hiện bất thường |
| `@Resource`/autowire trong BPP khiến Spring dò bean theo kiểu | Một số kiểu dependency lookup có thể kéo thêm bean ngoài dự định | Bean không liên quan trực tiếp theo tên nhưng vẫn bị tạo sớm |
| Custom `BeanFactoryPostProcessor` gọi `getBean(...)` | Force instance creation trong pha xử lý definition, trước giai đoạn BPP thường lệ | Bean không đi qua đầy đủ các post-processors |
| BPP đăng ký muộn trong runtime | Các bean đã sinh ra không tự động được áp dụng processor mới | Một số bean có proxy, một số cùng loại lại không |

Một nuance quan trọng: **`static @Bean` là khuyến nghị cho BPP, nhưng không phải thuốc chữa mọi eager dependency**. Khi method vẫn khai báo tham số là một business service, việc inject tham số đó có thể kéo service vào quá trình tạo BPP quá sớm.

## 4. Ví dụ Spring Framework tái lập lỗi và đối chứng

Ví dụ dưới đây cố ý tạo **hai bean cùng kiểu**: `earlyService` được inject vào một `PriorityOrdered BeanPostProcessor` trong giai đoạn bootstrap; `normalService` được tạo sau và có cơ hội nhận AOP proxy. Một aspect `@Before` khớp method của cả hai. Kết quả làm rõ vì sao một bean không nhận advice mặc dù cùng class và pointcut.

Yêu cầu: **Java 17+**, Maven, Spring Framework **6.2.0**. Đây là bài chạy độc lập, không dùng Spring Boot, không dùng AspectJ weaving.

Cấu trúc project:

~~~text
spring-early-bpp/
├── pom.xml
└── src/main/java/demo/
    ├── App.java
    ├── TrackedService.java
    ├── TraceAspect.java
    └── EarlyReferenceBpp.java
~~~

### 4.1. Maven dependencies

Tạo `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-early-bpp</artifactId>
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

`spring-context` lo container và BPP registration; `spring-aop` hỗ trợ AOP proxy; `aspectjweaver` cung cấp kiểu annotation/pointcut để Spring hiểu `@AspectJ`. **Thêm dependency này không kích hoạt bytecode weaving**.

### 4.2. Service dùng chung cho cả hai bean

Tạo `src/main/java/demo/TrackedService.java`:

~~~java
package demo;

public class TrackedService {
    private final String name;

    public TrackedService(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void execute() {
        System.out.println("TARGET: " + name);
    }
}
~~~

Class `TrackedService` và method `execute` đều không `final`, cho phép Spring tạo subclass proxy khi `proxyTargetClass = true`. Các bean `earlyService` và `normalService` chỉ khác nhãn tên, giúp đối chứng cùng một advisor.

### 4.3. Aspect chung cho cả hai service

Tạo `src/main/java/demo/TraceAspect.java`:

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    private final AtomicInteger calls = new AtomicInteger();

    @Before("execution(* demo.TrackedService.execute(..))")
    public void beforeExecute(JoinPoint joinPoint) {
        TrackedService target = (TrackedService) joinPoint.getTarget();
        calls.incrementAndGet();
        System.out.println("ADVICE: " + target.getName());
    }

    public int count() {
        return calls.get();
    }
}
~~~

Advice chỉ chọn method execution `TrackedService.execute()`. Nếu cả hai bean đều được proxy, mỗi lần gọi `execute()` qua bean reference sẽ khớp. Khi chỉ `normalService` được proxy, chỉ lời gọi tới bean đó kích hoạt advice và tăng `calls`.

### 4.4. BeanPostProcessor cố tình kéo một service vào bootstrap

Tạo `src/main/java/demo/EarlyReferenceBpp.java`:

~~~java
package demo;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

public class EarlyReferenceBpp implements BeanPostProcessor, PriorityOrdered {
    private final TrackedService capturedService;

    public EarlyReferenceBpp(TrackedService capturedService) {
        this.capturedService = capturedService;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
~~~

Ở Spring 6.2, hai callback `BeanPostProcessor` có default implementation, nên ví dụ không cần override `postProcessBeforeInitialization` hoặc `postProcessAfterInitialization` chỉ để giữ nguyên bean. BPP này **không tạo proxy, không gọi aspect, không sửa business logic**. Nó cố ý giữ reference tới một application service trong constructor để minh họa **dependency bị kéo vào quá sớm**.

`PriorityOrdered` làm processor tùy biến thuộc nhóm được Spring chuẩn bị ưu tiên trước nhóm BPP chỉ triển khai `Ordered`. Auto-proxy creator Spring 6.2 kế thừa `AbstractAutoProxyCreator` triển khai `Ordered`. Trong project này, việc resolve dependency của `EarlyReferenceBpp` diễn ra khi các BPP còn đang được đăng ký, trước khi auto-proxy creator xử lý `earlyService` theo luồng thông thường.

Không nên dùng thứ tự `PriorityOrdered` để thiết kế một BPP nghiệp vụ phụ thuộc vào service. Nó được dùng ở đây **chỉ để tái lập rõ tình huống lỗi**.

### 4.5. Cấu hình và mã chạy

Tạo `src/main/java/demo/App.java`:

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class App {
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class Config {
        @Bean
        public TrackedService earlyService() {
            return new TrackedService("early");
        }

        @Bean
        public TrackedService normalService() {
            return new TrackedService("normal");
        }

        @Bean
        public TraceAspect traceAspect() {
            return new TraceAspect();
        }

        @Bean
        public static EarlyReferenceBpp earlyReferenceBpp(
                @Qualifier("earlyService") TrackedService earlyService) {
            return new EarlyReferenceBpp(earlyService);
        }
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            TrackedService early =
                    (TrackedService) context.getBean("earlyService");
            TrackedService normal =
                    (TrackedService) context.getBean("normalService");
            TraceAspect aspect = context.getBean(TraceAspect.class);

            boolean earlyProxy = AopUtils.isAopProxy(early);
            boolean normalProxy = AopUtils.isAopProxy(normal);

            System.out.println("EARLY_PROXY: " + earlyProxy);
            System.out.println("NORMAL_PROXY: " + normalProxy);

            early.execute();
            normal.execute();

            System.out.println("ADVICE_COUNT: " + aspect.count());

            if (earlyProxy || !normalProxy || aspect.count() != 1) {
                throw new IllegalStateException(
                        "Unexpected Spring AOP auto-proxy behavior");
            }
            System.out.println("CHECK: PASS");
        }
    }
}
~~~

Giải thích chi tiết các quyết định:

- `@EnableAspectJAutoProxy(proxyTargetClass = true)` kích hoạt Spring AOP, yêu cầu class-based proxy. Không phụ thuộc việc hai service implement interface hay không.
- `@Bean earlyService()` và `@Bean normalService()` cùng trả `TrackedService`. `@Bean traceAspect()` tạo Spring bean chứa advisor từ `@Before`.
- `@Bean public static EarlyReferenceBpp earlyReferenceBpp(...)` cho phép Spring tìm/khởi tạo BPP mà không phải khởi tạo `Config` chỉ vì factory method. **Tuy nhiên**, tham số `@Qualifier("earlyService")` buộc Spring tạo `earlyService` để đưa vào constructor BPP, ngay trong pha đăng ký processor. Việc khai báo `static` không chặn được eager dependency này.
- Kiểu trả về **cụ thể** `EarlyReferenceBpp` cho Spring biết factory method tạo BPP thuộc nhóm `PriorityOrdered` ngay từ lúc kiểm tra kiểu; việc trả một kiểu quá chung có thể làm phát hiện và thứ tự xử lý thiếu chính xác.
- `normalService` không phải dependency của BPP, nên được tạo theo quá trình singleton initialization thông thường, sau khi Spring đã đăng ký auto-proxy creator.
- Chương trình lấy bean **qua Spring context**, kiểm tra trực tiếp bằng `AopUtils.isAopProxy` rồi gọi cùng method `execute()` ở hai bean. Biến đếm của aspect giúp xác minh có chính xác một advice chạy.

### 4.6. Chạy và quan sát kết quả

Trong thư mục `spring-early-bpp`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng **do chương trình Java tự in ra** trong project Spring 6.2.0 được cấu hình ở trên:

~~~text
EARLY_PROXY: false
NORMAL_PROXY: true
TARGET: early
ADVICE: normal
TARGET: normal
ADVICE_COUNT: 1
CHECK: PASS
~~~

Có thể có WARN từ logger Spring báo `earlyService` chưa được tất cả BPP xử lý. **Cảnh báo không nằm trong bảy dòng do chương trình in**; tên logger, nội dung và thứ tự log phụ thuộc cấu hình logging. Mục tiêu ở đây là quan sát `AopUtils` và `TraceAspect.count()`.

Đọc kết quả theo hai nhánh:

- **Nhánh early:** `earlyService` được tạo để khởi tạo `EarlyReferenceBpp` khi auto-proxy creator chưa có cơ hội xử lý đầy đủ bean đó. Khi gọi `early.execute()`, chỉ có `TARGET: early`, không có `ADVICE: early`.
- **Nhánh normal:** `normalService` được tạo sau giai đoạn đăng ký BPP, auto-proxy creator tìm thấy advisor `TraceAspect` phù hợp, tạo proxy; `normal.execute()` đi qua proxy nên in `ADVICE: normal` trước `TARGET: normal`.
- Cả hai cùng class và dùng cùng pointcut. Điều khác biệt là **thời điểm Spring tạo bean so với quá trình đăng ký các processor**.
- Nếu stdout/proxy status khác kỳ vọng, chương trình ném exception thay vì báo thành công. Khi chạy với phiên bản Spring hoặc cấu hình khác, hãy kiểm tra log bootstrap và các BPP được đăng ký thay vì ép dữ liệu thành giống bản mẫu.

## 5. Cách khắc phục: đừng yêu cầu service trong lúc tạo BeanPostProcessor

Cách khắc phục tốt nhất là bỏ quan hệ BPP → business service ở thời điểm khởi tạo. Nếu BPP chỉ cần quan sát thông tin bean, viết callback nhận `Object bean, String beanName`; nếu cần logic nghiệp vụ, đẩy nó sang service hoặc advice chạy **sau khi context hoàn thành khởi tạo**.

Trong project trên, một bản sửa tối thiểu để tự đối chứng là thay `EarlyReferenceBpp` bằng processor không giữ `TrackedService`:

~~~java
package demo;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

public class EarlyReferenceBpp implements BeanPostProcessor, PriorityOrdered {
    public EarlyReferenceBpp() {
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
~~~

Đồng thời thay factory method BPP trong `App.Config` bằng:

~~~java
@Bean
public static EarlyReferenceBpp earlyReferenceBpp() {
    return new EarlyReferenceBpp();
}
~~~

Hai code fence này là **những đoạn thay thế cho hai phần tương ứng trong project đã có**, không phải các tệp Java độc lập. Bây giờ `earlyService` không còn bị tạo bởi dependency của BPP. Nếu không có nguyên nhân khởi tạo sớm khác, cả hai service đều đi qua auto-proxy creator:

~~~text
EARLY_PROXY: true
NORMAL_PROXY: true
ADVICE: early
TARGET: early
ADVICE: normal
TARGET: normal
ADVICE_COUNT: 2
~~~

Cần sửa cả kiểm tra trong `App.main()` cho kịch bản mới:

~~~java
if (!earlyProxy || !normalProxy || aspect.count() != 2) {
    throw new IllegalStateException(
            "Expected both service beans to be proxied");
}
~~~

Sau khi thay cả ba đoạn trên, chương trình tiếp tục in `CHECK: PASS`. Nếu chỉ thay BPP nhưng giữ điều kiện cũ `earlyProxy || ... count != 1`, kết quả `CHECK` sẽ lỗi **đúng theo phép kiểm tra cũ**, không phải vì Spring AOP hỏng.

### 5.1. Khi BPP thực sự cần một dependency

Phân biệt ba nhu cầu:

1. **Cần cấu hình đơn giản:** truyền literal, cấu hình thuần hoặc bean definition metadata vào BPP, tránh inject service có advice/transaction.
2. **Cần tra cứu bean khi xử lý mỗi callback:** cân nhắc thiết kế lại để không biến BPP thành thành phần điều phối nghiệp vụ; nếu phải dùng lookup, chỉ tra cứu ở thời điểm an toàn và quản lý dependency/recursion cẩn thận. Dùng `ObjectProvider`/`Provider` có thể **trì hoãn** việc resolve dependency, nhưng **không tự bảo đảm an toàn** nếu gọi `getObject()` ngay trong constructor hoặc trong bootstrap callback.
3. **Cần xử lý nghiệp vụ bình thường:** không dùng BPP làm nơi gọi service. Dùng `@Aspect` advice, một service khác hoặc listener chạy sau context refresh tùy nhiệm vụ. Với Spring AOP, đây thường là lựa chọn đơn giản nhất.

### 5.2. Khai báo `@Bean` cho BPP đúng cách

Một khai báo nên ưu tiên:

~~~java
@Bean
public static CustomBeanPostProcessor customBeanPostProcessor() {
    return new CustomBeanPostProcessor();
}
~~~

`CustomBeanPostProcessor` là tên class **minh họa**, không phải class đã định nghĩa trong project thực hành. Hai điểm chính:

- **`static`:** container có thể gọi factory method mà không phải tạo sớm configuration instance.
- **Không có dependency mạnh đến business service:** tránh khởi tạo service trong giai đoạn BPP registration.

Không nên khai báo:

~~~java
@Bean
public BeanPostProcessor myProcessor(PaymentService paymentService) {
    return new MyProcessor(paymentService);
}
~~~

Đây là **ví dụ về cấu hình cần xem lại**, không phải snippet biên dịch độc lập. `PaymentService` và `MyProcessor` chỉ là tên minh họa. Factory method non-static có thể kích hoạt config class sớm, còn tham số service kéo dependency business bean vào bootstrap.

Hơn nữa, factory method tạo BPP cần return type thể hiện rõ kiểu BPP; nếu khai báo return type quá rộng như `Object`, Spring có thể không phát hiện chính xác BPP đủ sớm trong quá trình lấy danh sách post-processor.

## 6. Những tình huống gần giống nhưng nguyên nhân khác

### 6.1. Proxy có tồn tại nhưng advice vẫn không chạy

`AopUtils.isAopProxy(bean) == true` là bằng chứng proxy tồn tại, không phải bằng chứng advisor cụ thể sẽ chạy. Nếu proxy đã có, cần xem:

- Pointcut `execution(...)` có thực sự khớp method không?
- Caller có gọi qua Spring bean proxy hay gọi `this.method()` trong target?
- Method có khả năng proxy được không (JDK interface, CGLIB class/method `final`, visibility)?
- Có nhiều advisor/proxy khác chỉ bao một số method hay không?

Ngược lại, nếu bean không nhận proxy và xuất hiện WARN “not eligible for auto-proxying”, hãy ưu tiên điều tra **lịch khởi tạo và dependency của BPP** trước khi thay đổi biểu thức pointcut.

### 6.2. @PostConstruct gọi method có advice

Trong Spring AOP proxy-based, method được gọi ngay trong constructor hoặc `@PostConstruct` của target thường không đi qua proxy cuối cùng, kể cả bean sau đó sẽ trở thành proxy. Điều này **không nhất thiết** là “bean không đủ điều kiện auto-proxying”. Phải phân biệt:

| Tình huống | Proxy sau khi context khởi tạo? | Advice trên lời gọi đang quan sát? |
| --- | --- | --- |
| Bean được tạo đúng pha, gọi method qua proxy | Có, nếu advisor khớp | Có khi match |
| Bean được tạo đúng pha, gọi method nội bộ trong `@PostConstruct` | Về sau có thể có | Lời gọi ở init thường không qua proxy |
| Bean bị khởi tạo quá sớm vì BPP dependency | Có thể không có | Lời gọi vào target không được AOP intercept |
| Gọi object tự tạo bằng `new` ngoài Spring | Không phải Spring proxy | Không, trừ cơ chế độc lập khác |

Việc chuyển logic khỏi `@PostConstruct` hoặc tách service có thể giải quyết vấn đề gọi method lúc init, nhưng nguyên nhân của cảnh báo *not eligible* cần xét riêng.

### 6.3. Circular dependency và early reference

Auto-proxy creator có cơ chế như `getEarlyBeanReference(...)` để hỗ trợ một số trường hợp dependency vòng, nhằm tránh trao target raw cho một số bean rồi sau đó dùng proxy ở nơi khác. Tuy nhiên, khả năng này **không phải bảo đảm mọi bean khởi tạo quá sớm sẽ được proxy**, đặc biệt khi toàn bộ BPP chưa đăng ký. Đừng dùng circular reference làm phương án chữa thông báo early initialization. Refactor dependencies để phá vòng là cách đáng tin hơn.

### 6.4. Context cha/con và proxy configuration

Các BeanPostProcessor đăng ký với một `ApplicationContext` có phạm vi theo container đó. BPP trong parent không tự xử lý mọi bean sinh trong child. Trong ứng dụng nhiều context, cần kiểm tra nơi aspect, auto-proxy creator và service được tạo; đừng quy tất cả việc thiếu advice cho early initialization.

### 6.5. Spring Boot auto-configuration

Spring Boot có thể tự bật AOP khi có dependency starter tương ứng, nhưng cơ chế proxy Spring vẫn dựa vào post-processing của container. Nếu một service bị tạo quá sớm vì chính BPP tự khai báo hoặc dependency của nó, chỉ thêm `spring-boot-starter-aop` hay `@EnableAspectJAutoProxy` thường **không giải quyết thời điểm khởi tạo**. Kiểm tra logs, bean graph và vị trí processor trước.

## 7. Quy trình chẩn đoán thực tế

Khi thấy cảnh báo trong log hoặc advice bỗng không chạy, thực hiện có thứ tự:

1. **Ghi lại chính xác tên và kiểu bean** được nêu trong WARN; không đoán theo symptom. Nếu log chỉ báo “not eligible ...”, tra nguyên nhân bootstrap của chính bean đó.
2. **Xác nhận proxy sau khi context sẵn sàng**: gọi `AopUtils.isAopProxy(context.getBean("beanName"))`. Với Spring bean có proxy, cân nhắc kiểm tra tiếp `isCglibProxy`/`isJdkDynamicProxy`.
3. **Kiểm tra BPP registration:** xem các class triển khai `BeanPostProcessor`, đặc biệt custom BPP, `BeanPostProcessor` trả về qua `@Bean`, và auto-proxy creator do `@EnableAspectJAutoProxy` đăng ký.
4. **Tìm quan hệ phụ thuộc:** constructor tham số service, `@Autowired`/`@Resource` trong BPP, factory method BPP non-static, `getBean()` gọi sớm hoặc `BeanFactoryPostProcessor` làm instantiate object.
5. **Đọc thứ tự startup** bằng logger framework/trace debug phù hợp môi trường. Đối chiếu việc bean được tạo trước hay sau khi auto-proxy creator đã được đăng ký và hạ tầng aspect sẵn sàng.
6. **Tách eager dependency khỏi BPP:** static `@Bean`, giảm dependency hoặc trì hoãn giải quyết dependency đến thời điểm phù hợp, nhưng không giải quyết bean business ngay trong bootstrap callback.
7. **Chạy lại và kiểm tra hành vi thật:** log WARN có còn không, AOP proxy có xuất hiện không, advice có chạy qua external caller hay không. Đo proxy và advice độc lập, không chỉ dựa vào hết cảnh báo.
8. **Kiểm tra nguyên nhân thứ hai nếu vẫn lỗi:** pointcut, self-invocation, object ngoài container, final method, nhiều context hoặc advisor khác.

### Dùng AopUtils để đo mà không suy luận từ tên lớp proxy

Đoạn minh họa sau đặt **bên trong mã đã có biến `context`**, không phải tệp Java độc lập:

~~~java
Object bean = context.getBean("normalService");

System.out.println("AOP=" + org.springframework.aop.support.AopUtils.isAopProxy(bean));
System.out.println("JDK=" + org.springframework.aop.support.AopUtils.isJdkDynamicProxy(bean));
System.out.println("CGLIB=" + org.springframework.aop.support.AopUtils.isCglibProxy(bean));
~~~

Ở ví dụ nguyên bản, `normalService` là class-based proxy, nên ba dòng tương ứng là `AOP=true`, `JDK=false`, `CGLIB=true`. Nếu một application bean không có proxy, hãy quay lại kiểm tra cả lifecycle lẫn advisor/pointcut; không thể kết luận chỉ từ tên class chứa `$$SpringCGLIB$$` hoặc mỗi một WARN.

## 8. Những điều không nên làm để “chữa” cảnh báo

- **Không tự xóa log hoặc đổi logging level để coi là đã khắc phục.** Cảnh báo là dấu hiệu cho thấy một đường tạo bean bất thường; cần hiểu bean nào và BPP nào liên quan.
- **Không dùng `@DependsOn` một cách máy móc để ép thứ tự.** Nếu tạo bean trước khi processor sẵn sàng, `@DependsOn` có thể làm lỗi nặng hơn, không bảo đảm Spring AOP proxy xuất hiện.
- **Không cố gọi `getBean()` thêm một lần với hy vọng container tự tạo lại proxy.** Singleton đã sinh có thể được giữ lại; lookup lại thường lấy đúng instance đó.
- **Không tự bọc proxy thứ hai trong một custom BPP nếu chỉ cần advice chuẩn.** Dễ gây double-proxy, thay đổi thứ tự advisor và xung đột transaction/cache.
- **Không nhầm `static @Bean` với loại bỏ dependency.** Static giúp tránh khởi tạo configuration object sớm; tham số factory method vẫn được Spring resolve khi dựng processor.
- **Không mặc định phải chuyển sang AspectJ weaving.** Hãy sửa chuỗi khởi tạo bean và giữ Spring AOP nếu phạm vi method trên managed Spring bean vẫn phù hợp.

## 9. Bài tập tự kiểm tra

1. Tại sao một `BeanPostProcessor` lại làm service nhận thiếu Spring AOP advice?  
   **Vì việc khởi tạo BPP kéo service vào container trước khi toàn bộ auto-proxy creator/BPP sẵn sàng; service không đi qua đủ post-processing để thành proxy.**

2. Vì sao khai báo `@Bean static EarlyReferenceBpp earlyReferenceBpp(TrackedService earlyService)` vẫn có thể lỗi?  
   **Vì `static` không ngăn Spring phải khởi tạo service dùng làm tham số.**

3. Vì sao hai bean cùng class, cùng pointcut nhưng chỉ một bean có proxy?  
   **Vì thời điểm tạo bean khác nhau so với giai đoạn đăng ký BPP, không phải vì cú pháp aspect khác nhau.**

4. Thông báo “not eligible for auto-proxying” có khẳng định chắc chắn bean sẽ không bao giờ có proxy không?  
   **Không. Nó cho biết thiếu một phần quá trình post-processing; phải đo trạng thái bean và cấu hình thực tế.**

5. Làm cách nào xác nhận lỗi đã được giải quyết?  
   **Sau khi loại bỏ dependency gây khởi tạo sớm, kiểm tra bean lấy từ context bằng `AopUtils.isAopProxy(...)` và xác minh advice thực sự chạy trên lời gọi qua proxy.**

## 10. Tài liệu Spring Framework

- [Container Extension Points — BeanPostProcessor, early initialization](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-extension.html): chuỗi xử lý BPP, bean bị khởi tạo sớm, WARN và khuyến nghị `static @Bean`.
- [Spring @Bean Javadoc — BeanPostProcessor-returning methods](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Bean.html): return type cụ thể, `static` và tránh dependency lúc bootstrap.
- [Spring @AspectJ support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj.html): Spring diễn giải cú pháp `@AspectJ` bằng hạ tầng proxy.
- [Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html): cách kích hoạt cơ chế tự tạo proxy cho Spring bean.
- [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): loại proxy và giới hạn method invocation trong Spring AOP.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="auto-proxy-mechanics-008">Lời gọi trong quá trình khởi tạo và giới hạn của @PostConstruct</a>

<details>
<summary>Xem chi tiết</summary>

# Lời gọi trong quá trình khởi tạo và giới hạn của @PostConstruct trong Spring AOP

## 1. Vấn đề thực tế: cùng một method nhưng advice chỉ chạy sau khi bean sẵn sàng

Trong Spring AOP sử dụng auto-proxy, một phương thức của Spring bean có thể **không kích hoạt advice khi được gọi từ constructor hoặc callback khởi tạo `@PostConstruct`**, dù sau đó cùng phương thức ấy được advice khi gọi qua bean lấy từ `ApplicationContext`.

Cần phân biệt hai điều kiện riêng:

1. **Vòng đời:** tại thời điểm gọi, Spring đã tạo và công bố proxy hoàn chỉnh của bean đích hay chưa?
2. **Đường đi lời gọi:** code đang gọi **chính instance đích (`this`)** hay một **tham chiếu đến proxy** của Spring?

Chỉ gắn `@Aspect`, viết pointcut đúng và bật `@EnableAspectJAutoProxy` chưa bảo đảm mọi lời gọi trong thời gian khởi tạo được intercept. Tình huống này liên quan trực tiếp đến **thứ tự BeanPostProcessor và auto-proxy của Spring**.

Trong bài, `@PostConstruct` luôn là `jakarta.annotation.PostConstruct` của Spring Framework 6.x / Spring Boot 3.x trở lên. Nếu học một dự án Spring 5 / Boot 2 cũ, thường gặp `javax.annotation.PostConstruct`; không hoán đổi package theo cảm tính giữa các thế hệ.

## 2. Thứ tự lifecycle liên quan đến auto-proxy

Với một singleton bean bình thường trong Spring Framework dùng `@EnableAspectJAutoProxy`, sơ đồ khái quát:

```text
Spring ApplicationContext
        |
        v
Đăng ký BeanDefinition và BeanPostProcessor, bao gồm AOP auto-proxy creator
        |
        v
Tạo target instance (constructor / factory method)
        |
        v
Dependency injection và các Aware callbacks
        |
        v
BeanPostProcessor.postProcessBeforeInitialization(...)
  └─ xử lý @PostConstruct (khi có processor tương ứng)
        |
        v
Các init callbacks khác (InitializingBean, @Bean(initMethod), ...)
        |
        v
BeanPostProcessor.postProcessAfterInitialization(...)
  └─ AOP auto-proxy creator có thể wrap target bằng proxy
        |
        v
Đưa bean hoàn tất/proxy vào container cho client sử dụng
        |
        v
Lời gọi qua proxy -> advisor/pointcut -> advice -> target method
```

Sơ đồ cố ý trình bày **đường đi thông thường**, không phải tuyên bố mọi bean đều qua đúng một quy trình không ngoại lệ. Spring hỗ trợ các hook tạo bean sớm, `FactoryBean`, xử lý circular dependency và một số kiểu proxy đặc biệt; những trường hợp ấy có thể điều chỉnh thời điểm tạo proxy. Tuy nhiên, **không được dựa vào ngoại lệ lifecycle để bảo đảm AOP trên callback khởi tạo**.

Điều quan trọng trong Spring là:

- `CommonAnnotationBeanPostProcessor` (kế thừa cơ chế xử lý lifecycle annotations) thực hiện `@PostConstruct` **trước khi** bean hoàn tất initialization.
- `BeanPostProcessor#postProcessAfterInitialization` là điểm thông thường nơi AOP auto-proxy creator có thể trả về **proxy thay cho target gốc**.
- Theo tài liệu Spring Framework về initialization callbacks, callback khởi tạo được gọi trên **raw bean**, do đó các AOP interceptors thường **chưa áp dụng** cho invocation của init method.
- Sau khi bean hoàn tất, các **caller dùng tham chiếu proxy** mới kích hoạt advice cho method được pointcut chọn.
- `@PostConstruct` dùng để thiết lập invariant/cấu trúc nội bộ của bean; Spring 6.2 lưu ý callback chạy trong giai đoạn singleton creation lock và **không phù hợp để kích hoạt công việc nặng, truy cập thêm nhiều bean hoặc khởi động luồng nền phức tạp**.

### 2.1. Vị trí tương đối của các init callbacks

Đối với một bean có nhiều callback với các method khác nhau, Spring mô tả thứ tự thông thường:

1. Method gắn `@PostConstruct`.
2. `InitializingBean.afterPropertiesSet()`, nếu bean implement interface.
3. Custom initialization method, chẳng hạn `@Bean(initMethod = "init")`.

Các bước này thuộc **khởi tạo target**, không phải lời gọi public business method từ một consumer qua proxy. Vì vậy, việc callback được Spring gọi một lần không có nghĩa nó được tính như một method invocation đã được AOP advice.

Nếu method init bị lỗi và ném exception, quá trình tạo bean/context có thể thất bại. Advice `@AfterThrowing` trên business methods không mặc nhiên bắt hoặc chuyển đổi lỗi initialization: chúng chỉ quan sát method calls đi qua proxy đúng phạm vi.

## 3. Phân biệt bốn trường hợp thường bị đánh đồng

| Trường hợp | Đối tượng/đường gọi trong Spring | Advice Spring AOP proxy |
| --- | --- | --- |
| Constructor của bean gọi `this.process()` | Chạy trực tiếp trên object đang tạo; chưa có proxy bean hoàn chỉnh cho caller | **Không** chặn được lời gọi nội bộ ấy qua Spring AOP. |
| `@PostConstruct` của **chính target** gọi `this.process()` | Callback chạy trên raw target; `process()` là self-invocation | **Không** chặn lời gọi `process()` qua proxy. |
| Spring gọi **chính method gắn `@PostConstruct`** trên target | Lifecycle callback do post-processor gọi trong khởi tạo | **Không nên kỳ vọng** advice áp dụng cho callback ấy trong vòng đời thông thường. |
| `@PostConstruct` của **bean A** gọi `targetProxy.process()` của bean B đã init xong và được inject | Invocation đi qua **proxy của bean B** | **Có thể có**, nếu B đã proxy, pointcut match và method chặn được. |
| `main`/service/controller sau context refresh gọi `targetProxy.process()` | Lời gọi vào proxy của Spring-managed target | **Có**, nếu advisor match và proxy có thể intercept method. |

Hàng thứ tư là điểm rất dễ bị hiểu sai: **không phải mọi code nằm trong `@PostConstruct` đều bỏ qua AOP**. Nếu bean A được inject một bean B đã hoàn tất initialization và B có proxy, lời gọi **A → proxy B** vẫn có thể đi qua advice. Dẫu vậy, không nên lấy điều này làm lý do chạy một workflow nặng ngay trong `@PostConstruct` của A: các bean còn lại có thể chưa khởi tạo và nguy cơ deadlock/đụng lifecycle vẫn tồn tại.

### 3.1. Self-invocation và lifecycle là hai giới hạn khác nhau

Ngay cả khi target **đã được Spring proxy hoàn chỉnh**, lời gọi nội bộ `this.process()` từ một method khác trên cùng target vẫn có thể bỏ qua proxy. Vì thế cần tránh suy luận rằng “sau khi context refresh, mọi self-call đều được advice”.

```java
// Minh họa: code trong một Spring service đã được proxy
public void outer() {
    this.inner();  // không quay lại Spring proxy
}

public void inner() {
    // chỉ được advice khi được gọi qua proxy ở ranh giới phù hợp
}
```

Đây là đoạn minh họa, không phải class Java đầy đủ. Đổi `proxyTargetClass=true` hay dùng CGLIB **không chữa được** self-invocation trong mô hình Spring AOP proxy thông thường. `private` và `final` cũng không trở thành interceptable chỉ vì đổi loại proxy.

## 4. Demo độc lập: quan sát constructor, @PostConstruct, lời gọi giữa các bean và sau refresh

Ví dụ sử dụng:

- **Java 17+**, **Spring Framework 6.2.19** và **AspectJ Weaver 1.9.25.1**.
- `jakarta.annotation-api:2.1.1` để khai báo rõ `@PostConstruct`.
- Spring Framework Java configuration thuần để thấy auto-proxy được bật bằng `@EnableAspectJAutoProxy`.
- Hai bean `WorkService` (target) và `StartupCaller` (consumer), một `TraceAspect` với pointcut giới hạn rõ đến public methods của `WorkService`.

Ví dụ **không dùng Spring Boot** để loại bỏ sự phụ thuộc vào Boot AOP auto-configuration khi lý giải vòng đời. Các dependency/framework versions cần đồng bộ theo project thật.

### 4.1. Cấu trúc thư mục và Gradle

```text
spring-aop-init-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/com/example/initdemo/
    ├── Main.java
    ├── AppConfig.java
    ├── WorkService.java
    ├── StartupCaller.java
    └── TraceAspect.java
```

**`settings.gradle`**

```gradle
rootProject.name = 'spring-aop-init-demo'
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
    implementation 'jakarta.annotation:jakarta.annotation-api:2.1.1'
}

application {
    mainClass = 'com.example.initdemo.Main'
}
```

Spring Context mang theo hạ tầng annotation configuration; `@EnableAspectJAutoProxy` kích hoạt Spring AOP auto-proxy creator. Trong `AnnotationConfigApplicationContext`, các annotation processors chuẩn được đăng ký cho vòng đời `@PostConstruct`. Không cần đặt JVM agent `-javaagent`: bài sử dụng **Spring proxy-based AOP**, không phải AspectJ load-time weaving.

### 4.2. Target bean: chủ động tạo ba loại lời gọi

**`WorkService.java`**

```java
package com.example.initdemo;

import jakarta.annotation.PostConstruct;

public class WorkService {
    public WorkService() {
        System.out.println("constructor:begin");
        process("from-constructor");
        System.out.println("constructor:end");
    }

    @PostConstruct
    public void initialize() {
        System.out.println("postConstruct:begin");
        process("from-postConstruct");
        System.out.println("postConstruct:end");
    }

    public String process(String source) {
        System.out.println("target:" + source);
        return "done:" + source;
    }
}
```

Trong constructor và `initialize()`, phương thức `process()` được gọi trực tiếp trên **chính object**, không qua Spring proxy. Mặc dù `initialize()` là public và có thể được biểu thức `execution(...)` match về mặt signature, **Spring đang gọi nó như một lifecycle callback của target**, không phải client gọi qua AOP proxy. Không gọi `initialize()` trực tiếp như business method chỉ để bắt advice.

### 4.3. Aspect chặn public method trên WorkService

**`TraceAspect.java`**

```java
package com.example.initdemo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    @Before("execution(public * com.example.initdemo.WorkService.*(..))")
    public void beforeWork(JoinPoint point) {
        System.out.println("advice:" + point.getSignature().getName());
    }
}
```

Pointcut áp dụng cho public methods được chọn của `WorkService`. Trong demo, `process()` và `initialize()` về mặt biểu thức đều thuộc lớp này. Không có yêu cầu sử dụng `@Component` vì `TraceAspect` được đăng ký bằng `@Bean` trong cấu hình phía dưới.

**Điều cần quan sát:** việc `@PostConstruct` được container gọi **không** tự làm xuất hiện `advice:initialize`. Khi method `process()` được gọi **qua proxy từ bean khác hoặc từ main**, advice sẽ in `advice:process`.

### 4.4. Caller bean có @PostConstruct của riêng nó

**`StartupCaller.java`**

```java
package com.example.initdemo;

import jakarta.annotation.PostConstruct;

public class StartupCaller {
    private final WorkService workService;

    public StartupCaller(WorkService workService) {
        this.workService = workService;
    }

    @PostConstruct
    public void initializeCaller() {
        System.out.println("callerPostConstruct:begin");
        workService.process("from-other-bean");
        System.out.println("callerPostConstruct:end");
    }
}
```

Ở đây `workService` là dependency **được Spring inject**, không phải `new WorkService()`. Do dependency cần được tạo và init trước khi `StartupCaller` có thể gọi nó, `StartupCaller.initializeCaller()` sẽ nhận bean `WorkService` sau bước hoàn tất thường lệ và có thể gọi **qua proxy**.

**Không biến đoạn này thành quy tắc cho production:** demo cố ý sử dụng lời gọi cross-bean nhẹ để chứng minh sự khác biệt. Trong ứng dụng thật, `@PostConstruct` không phải nơi khởi động request, transaction dài, gọi remote service hoặc lập lịch task nặng khi toàn bộ context có thể chưa sẵn sàng.

### 4.5. Cấu hình Spring tạo các bean theo cách tường minh

**`AppConfig.java`**

```java
package com.example.initdemo;

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
    WorkService workService() {
        return new WorkService();
    }

    @Bean
    StartupCaller startupCaller(WorkService workService) {
        return new StartupCaller(workService);
    }
}
```

Spring tạo `WorkService`, thực hiện constructor/`@PostConstruct` trên target, rồi có thể đặt AOP proxy trước khi inject vào `StartupCaller`. Class `WorkService` không có interface, và cấu hình yêu cầu class-based proxy để lấy bean theo kiểu cụ thể. Cả target và aspect đều do container quản lý nhờ các phương thức `@Bean`; không cần `@ComponentScan`.

### 4.6. Gọi qua bean sau khi context refresh

**`Main.java`**

```java
package com.example.initdemo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            WorkService workService = context.getBean(WorkService.class);
            System.out.println("isProxy=" + AopUtils.isAopProxy(workService));
            System.out.println(
                "mainResult=" + workService.process("after-refresh")
            );
        }
    }
}
```

### 4.7. Chạy và đối chiếu stdout

Tại thư mục project, với Java 17+ và Gradle đã cài:

```bash
gradle run --quiet
```

Nếu dự án có Gradle Wrapper, dùng `./gradlew run --quiet` (Linux/macOS) hoặc `gradlew.bat run --quiet` (Windows).

Các dòng do mã ứng dụng in, theo thứ tự đối với project và cấu hình nêu trên:

```text
constructor:begin
target:from-constructor
constructor:end
postConstruct:begin
target:from-postConstruct
postConstruct:end
callerPostConstruct:begin
advice:process
target:from-other-bean
callerPostConstruct:end
isProxy=true
advice:process
target:after-refresh
mainResult=done:after-refresh
```

Đoạn stdout chỉ gồm các lệnh `System.out.println` đã mô tả; thông báo khởi động/cảnh báo riêng của JVM hoặc build tool có thể xuất hiện ngoài các dòng này.

**Phân tích bằng chứng:**

1. Sau `constructor:begin` là `target:from-constructor`, **không có `advice:process`**. Đây là lời gọi từ object đang được tạo.
2. Sau `postConstruct:begin` là `target:from-postConstruct`, **không có `advice:initialize` hay `advice:process`**. Callback chạy trên raw target; lời gọi `process` trong callback là self-invocation.
3. Sau `callerPostConstruct:begin` **có** `advice:process` trước `target:from-other-bean`. `StartupCaller` đã được inject tham chiếu proxy của `WorkService`. Điều này bác bỏ kết luận quá rộng rằng mọi method được gọi trong một `@PostConstruct` đều không thể AOP.
4. `isProxy=true` xác nhận `WorkService` sau context refresh đã là Spring AOP proxy.
5. Gọi `workService.process("after-refresh")` từ `Main` cũng in `advice:process` vì đi qua proxy đã hoàn tất.

Không có dòng `advice:initialize` vì callback `@PostConstruct` của target được gọi trực tiếp trong lifecycle. Nếu một developer cố gọi thủ công `workService.initialize()` sau refresh thì đó là **lời gọi khác**, có thể bị advice vì đi qua proxy; nhưng sẽ **khởi tạo lại** target theo cách không đúng contract. Không dùng thủ thuật này để “kiểm chứng” callback ban đầu.

## 5. Vì sao @PostConstruct không phải nơi khởi động workflow cần advice?

`@PostConstruct` phù hợp với bước local: kiểm tra cấu hình được inject, thiết lập cấu trúc dữ liệu nội bộ, xác nhận invariant đơn giản. Nó **không phải sự kiện toàn bộ ApplicationContext đã sẵn sàng**.

Những lý do cụ thể trong Spring:

- **Target có thể còn raw:** callback chạy trước lúc proxy cuối cùng được công bố; advice của chính target không đáng tin ở thời điểm này.
- **Self-invocation:** constructor/init gọi `this.method()` không đi qua Spring proxy dù method public và pointcut match.
- **Các singleton khác có thể chưa tạo:** truy cập tùy tiện bean khác từ `@PostConstruct` có thể làm Spring tạo sớm cả chuỗi dependency.
- **Creation lock:** Spring 6.2 nhấn mạnh callback init chạy trong giai đoạn singleton creation lock; gọi chuỗi bean phức tạp, chờ thread hoặc tác vụ DB/network dài có nguy cơ deadlock.
- **Transaction/cache/security proxy semantics:** nếu logic init cần `@Transactional`, `@Cacheable`, `@Async` hoặc method security thì gọi nội bộ ở phase này có thể bỏ qua interception tương ứng. Không suy rằng chỉ thêm annotation nghiệp vụ là transaction/async đã vận hành.
- **Khả năng test/giám sát:** lỗi xảy ra trong startup có thể khiến context không tạo xong; hook post-refresh thường có contract rõ hơn cho việc “khởi động công việc”.

**Ví dụ sai giả định:**

```java
@Service
public class ImportService {
    @PostConstruct
    public void onCreate() {
        importBatch(); // self-invocation trong init callback
    }

    @Transactional
    public void importBatch() {
        // Ghi nhiều bảng với kỳ vọng transaction bao quanh
    }
}
```

Đây là **đoạn minh họa** không phải ứng dụng đầy đủ. Kể cả khi `@EnableTransactionManagement` được bật, lời gọi `importBatch()` trực tiếp trên target từ `onCreate()` không bảo đảm đi qua transaction interceptor; cần tránh tin rằng annotation đã bao transaction trong trường hợp này. Transaction management còn đòi hỏi manager thích hợp và đúng ranh giới dữ liệu.

**Thiết kế rõ hơn:** `ImportService.importBatch()` là method của Spring bean B, còn bean điều phối khởi động A gọi **qua reference proxy B** sau thời điểm startup phù hợp, ví dụ sau context refresh. Nếu bắt buộc import sớm, vẫn phải đánh giá transaction manager, idempotency, thứ tự startup, lỗi/retry và môi trường nhiều replica.

## 6. Cơ chế nên dùng thay thế khi công việc phải chạy sau initialization

Chọn callback theo **mốc readiness** thật sự cần, rồi để bean điều phối **inject target bean qua Spring**.

| Nhu cầu | Cơ chế Spring phù hợp | Bảo đảm/giới hạn cần hiểu |
| --- | --- | --- |
| Thiết lập thuộc tính, validate dependency của **chính bean** | `@PostConstruct` | Chỉ local initialization. Không yêu cầu advice trên chính callback hay self-call. |
| Chờ các **non-lazy singleton** được container tạo xong | `SmartInitializingSingleton.afterSingletonsInstantiated()` | Chạy cuối giai đoạn tạo các non-lazy singleton; không nói rằng mọi lazy bean hoặc toàn bộ context events đã hoàn tất. |
| Chạy một bước sau **ApplicationContext refresh** | `@EventListener(ContextRefreshedEvent.class)` / `ApplicationListener<ContextRefreshedEvent>` | Context đã refresh; event có thể phát lại khi refresh, và có thể phát ở parent/child contexts. Thiết kế idempotent. |
| Khởi động/đóng tài nguyên cần phối hợp lifecycle | `SmartLifecycle` | Có start/stop và phase ordering, thường phù hợp component nền. |
| Spring Boot: chạy một công việc khi context đã khởi động và runners được gọi | `ApplicationRunner` hoặc `CommandLineRunner` | Hook của Boot; code gọi service được inject qua proxy. Có thể chặn startup nếu công việc lâu/lỗi. |
| Spring Boot: phản ứng sau khi startup hoàn tất | `@EventListener(ApplicationReadyEvent.class)` | Event từ Boot sau runners; **không chứng minh** downstream external dependency khỏe hoặc mọi task async đã hoàn tất. |

### 6.1. Ví dụ Spring Framework: điều phối sau ContextRefreshedEvent

Đoạn sau là **biến thể minh họa riêng**, không thêm vào project demo trên nếu muốn giữ nguyên stdout đã kiểm chứng.

```java
package com.example.initdemo;

import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

public class AfterRefreshCoordinator {
    private final WorkService workService;

    public AfterRefreshCoordinator(WorkService workService) {
        this.workService = workService;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onRefreshed() {
        workService.process("after-context-refreshed");
    }
}
```

Đăng ký từ config:

```java
@Bean
AfterRefreshCoordinator afterRefreshCoordinator(WorkService workService) {
    return new AfterRefreshCoordinator(workService);
}
```

Khi Spring phát `ContextRefreshedEvent`, `AfterRefreshCoordinator` gọi `WorkService.process` qua reference được inject từ container, nên method có thể đi qua advice nếu pointcut match. Trong ứng dụng có parent/child contexts, nhiều refresh hoặc nhiều node, cần lọc context/đảm bảo idempotency; đừng giả định sự kiện chỉ xảy ra đúng một lần trong suốt đời hệ thống.

**Điểm then chốt:** Mốc “sau refresh” giúp điều phối công việc ở phase phù hợp, nhưng **nếu event handler đặt ngay trong `WorkService` rồi gọi `this.process()`, self-invocation vẫn bypass advice**. Giữ bean điều phối tách khỏi bean nghiệp vụ khi cần gọi qua proxy.

### 6.2. Ví dụ Spring Boot: ApplicationRunner gọi qua bean khác

Với Spring Boot, một `ApplicationRunner` nhận dependency `WorkService` là lựa chọn sáng sủa cho startup task:

```java
package com.example.initdemo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupRunner implements ApplicationRunner {
    private final WorkService workService;

    public StartupRunner(WorkService workService) {
        this.workService = workService;
    }

    @Override
    public void run(ApplicationArguments args) {
        workService.process("boot-runner");
    }
}
```

Đây là **đoạn minh họa chỉ dành cho Spring Boot**, không biên dịch trong project Spring Framework thuần vì chưa khai báo dependency Boot. Boot có thể tự cấu hình AOP khi có starter phù hợp và không tắt auto-configuration; dù dùng Boot hay Framework, advice vẫn phụ thuộc pointcut và lời gọi qua proxy.

`ApplicationRunner` chạy khi khởi động ứng dụng Boot, trước khi Boot phát `ApplicationReadyEvent`. Nếu runner làm việc lâu, startup cũng chậm; các task nên có thiết kế timeout, lỗi và idempotency. Nếu cần quan hệ start/stop của một background component, xem `SmartLifecycle`.

### 6.3. SmartInitializingSingleton có giúp tránh self-invocation không?

Không. `afterSingletonsInstantiated()` chạy tại mốc muộn hơn `@PostConstruct`, nhưng **callback tự gọi method khác trên chính object** vẫn không tự động biến thành lời gọi qua AOP proxy. Nếu cần advice, dùng một **coordinator bean riêng** nhận proxy của service tương ứng.

Thứ tự startup callback giải quyết **khi nào công việc bắt đầu**; Spring AOP proxy quyết định **lời gọi đi qua đâu**. Phải thỏa cả hai điều kiện.

## 7. Khi constructor hoặc @PostConstruct buộc phải gọi code chung

Không phải mọi method được gọi từ constructor/init đều cần Spring AOP. Với logic thuần túy như validate input, xây bộ nhớ đệm local hoặc chuẩn hóa cấu hình, giữ thành **private helper** gọi trực tiếp là hợp lý:

```java
@PostConstruct
public void initialize() {
    validateConfiguration();
    createLocalIndex();
}

private void validateConfiguration() {
    // Chỉ kiểm tra dữ liệu đã được inject; không cần proxy.
}

private void createLocalIndex() {
    // Tạo cấu trúc dữ liệu local và bất biến sau khởi tạo.
}
```

Đoạn trên minh họa, không phải tệp runnable độc lập. Những private helper ấy **cố ý không cần AOP**, và `private` không được Spring subclass proxy override/intercept. Tách đúng mục tiêu:

- **Initialization local:** trực tiếp, không phụ thuộc proxy.
- **Use case nghiệp vụ cần transaction/security/audit/metric:** nằm trên method chặn được ở một Spring bean; caller có reference proxy.
- **Startup orchestration:** bean riêng chạy sau mốc phù hợp và gọi qua bean service đã sẵn sàng.

Đối với công việc cần giữ trạng thái hoặc tài nguyên ngoài, xây dựng cơ chế start/stop/retry riêng; đừng đặt nó trong constructor vì constructor có thể chạy trước khi dependency injection hoàn tất.

## 8. Các trường hợp nâng cao và giới hạn của phép suy luận

### 8.1. Không dùng ObjectProvider hoặc ApplicationContext.getBean trong constructor để “lấy proxy của chính mình”

Có thể tìm thấy cách cố lấy bean từ context, dùng `ObjectProvider`, self-injection hoặc `AopContext.currentProxy()` để gọi method của chính mình. Trong lúc constructor/`@PostConstruct` chạy, **proxy có thể chưa sẵn sàng**, hoặc bạn tạo ra phụ thuộc vòng gây khởi tạo sớm. `AopContext.currentProxy()` chỉ có khi `exposeProxy=true` và đang ở trong một lời gọi qua Spring AOP proxy; nó không biến constructor/init callback trở thành lời gọi được proxy.

Trong trường hợp method thật sự phải được intercept, ưu tiên **tách bean** và chọn mốc startup muộn hơn.

### 8.2. Early singleton references và circular dependencies

Spring có các bước xử lý early bean references trong những tình huống dependency vòng, và auto-proxy creator có thể tham gia qua các hook như `getEarlyBeanReference`. Không vì thế mà có thể kết luận “proxy đã tồn tại trong mọi `@PostConstruct`”. Thực tế việc khởi tạo sớm làm rủi ro nhận raw target/bean chưa được xử lý đầy đủ cao hơn. Cách ổn định nhất là **phá vòng dependency**, giữ constructor/init local và gọi nghiệp vụ sau khi dependency graph hoàn tất.

### 8.3. BeanPostProcessor và chính các bean được tạo sớm

Auto-proxy creator của Spring là một loại `BeanPostProcessor`. Một `BeanPostProcessor` khác và các bean nó trực tiếp phụ thuộc có thể bị tạo từ rất sớm, trước khi toàn bộ BeanPostProcessors được đăng ký. Tài liệu Spring cảnh báo một số bean thuộc giai đoạn này **không đủ điều kiện để được auto-proxy đầy đủ**, thường có log đại ý “not eligible for getting processed by all BeanPostProcessors”.

Không nhét nghiệp vụ cần AOP vào BeanPostProcessor hoặc dependency được tạo trong phase hạ tầng. Để hạ tầng BPP hoạt động đúng, các `@Bean` định nghĩa BeanPostProcessor thường được khuyến nghị khai báo `static` với ít dependency; đây là vấn đề cấu hình hạ tầng Spring, khác hẳn `@PostConstruct` của một service thông thường.

### 8.4. Cấu hình proxy và phương thức không chặn được

Không phải proxy nào cũng cho phép chặn mọi method:

- JDK dynamic proxy chặn các lời gọi thông qua interface của proxy.
- Class-based proxy (CGLIB) không override được `private`/`final` methods hoặc subclass một class `final`.
- Self-invocation trên target bỏ qua proxy trong cả hai cơ chế.
- Constructor **không phải Spring AOP method execution join point**; Spring proxy không cung cấp cách intercept các câu lệnh constructor tùy ý.

Nếu yêu cầu bắt constructor/field/đường gọi bên trong ở mức bytecode, cần xem phương án thiết kế khác hoặc AspectJ weaving riêng; `@EnableAspectJAutoProxy` không tự bật weaving.

## 9. Chẩn đoán khi advice không xuất hiện trong lúc startup

| Hiện tượng | Cách giải thích trong Spring | Kiểm chứng và xử lý |
| --- | --- | --- |
| Method gọi trong constructor không có advice | Target đang xây dựng; lời gọi là `this.method()` | Không dựa vào proxy trong constructor; chỉ làm initialization local. |
| Method được gọi từ `@PostConstruct` của chính target không có advice | Callback chạy trước proxy hoàn chỉnh; lời gọi nội bộ bỏ qua proxy | Chuyển use case cần AOP ra một service boundary; gọi từ coordinator sau startup. |
| `@PostConstruct` tự nó không có advice dù pointcut khớp | Lifecycle gọi trên raw bean trong chu kỳ initialization | Không dùng advice để điều khiển init callback; dùng hook lifecycle đúng nhiệm vụ. |
| `@PostConstruct` của bean A gọi method bean B và có advice | B đã init/proxy, A giữ reference của proxy | Hành vi có thể đúng; nhưng tránh lạm dụng post-construct cho công việc nặng. |
| Spring context tạo xong nhưng method vẫn không có advice | Aspect chưa là bean, thiếu auto-proxy support, pointcut không match hoặc caller dùng raw reference | Kiểm tra `@Aspect`+`@Component`/`@Bean`, `@EnableAspectJAutoProxy`, runtime proxy và actual invocation. |
| Self-call vẫn không có advice sau startup | `this` không quay lại proxy | Tách method sang bean khác, gọi qua injected proxy. |
| Tự tiêm bean của chính mình dẫn đến lỗi vòng dependency | Bean bị tham chiếu khi đang khởi tạo | Bỏ self-injection, phá vòng và thay bằng coordinator bean. |
| Có log “not eligible for auto-proxying” | Bean được tạo quá sớm trong quá trình bootstrap BeanPostProcessor | Kiểm tra BPP dependencies, `@Bean` static và thời điểm tạo bean. |
| Startup treo khi `@PostConstruct` truy cập nhiều bean hoặc chờ thread | Gọi hoạt động phức tạp trong singleton creation lock | Chuyển sang `SmartInitializingSingleton`/`ContextRefreshedEvent`/Boot runner tùy readiness. |
| `@Transactional`/`@Async` không tác dụng trong init | Proxy boundary hoặc cơ chế kích hoạt riêng của annotation chưa đáp ứng | Chẩn đoán proxy/service boundary và chọn hook sau initialization. |

### Một phép đo tối thiểu đáng tin

```java
WorkService bean = context.getBean(WorkService.class);
System.out.println(AopUtils.isAopProxy(bean));
bean.process("probe");
```

Đây là đoạn minh họa đặt **sau context refresh**, có import `AopUtils` và context/bean tồn tại. Nó kiểm tra hai việc:

1. `AopUtils.isAopProxy(bean)` xác nhận **reference hiện tại** có phải Spring AOP proxy.
2. Output/advice của lời gọi `process("probe")` chứng minh method cụ thể thực sự được advisor xử lý.

Chỉ xem `isAopProxy=true` là chưa đủ: proxy có thể dùng advisor khác và pointcut của aspect cần kiểm tra không match method `process`.

Để test lifecycle, đặt một recorder thread-safe ghi các mốc constructor/init/advice/target rồi assert thứ tự, thay vì dựa vào log framework trộn lẫn log ứng dụng. Nếu chạy bất đồng bộ, thứ tự giữa các thread không còn tất định; tránh ghi một bảng stdout cố định cho trường hợp ấy.

## 10. Quy tắc chọn nơi đặt khởi tạo

Hãy trả lời từng câu theo thứ tự:

1. **Hành động chỉ thay đổi trạng thái nội bộ của chính bean?** Nếu có, `@PostConstruct` phù hợp. Không ép nó đi qua Spring AOP.
2. **Hành động là một use case cần advice hoặc Spring interceptor?** Đặt nó ở một method service được proxy, rồi gọi từ một bean khác qua DI.
3. **Hành động cần chắc mọi singleton bình thường đã tạo?** Xem `SmartInitializingSingleton`, nhưng vẫn giữ đường gọi qua proxy.
4. **Hành động phải chạy sau ApplicationContext refresh?** Xem `ContextRefreshedEvent`, chú ý khả năng có nhiều contexts và re-publish.
5. **Đang ở Spring Boot và cần runner/ready event?** Sử dụng `ApplicationRunner` hoặc `ApplicationReadyEvent` theo thời điểm mong muốn.
6. **Công việc cần vận hành dài hạn, stop/restart?** Xem `SmartLifecycle`.
7. **Công việc cần gọi remote/DB hoặc async?** Xác định timeout, transaction, retry, idempotency, lỗi startup và khả năng chạy nhiều replicas.

Không nên coi **`@PostConstruct` + self-call đến method có annotation AOP** là mô hình gọi nghiệp vụ đáng tin, dù project đôi lúc cho thấy một vài advice vẫn được in ra trong các cấu hình đặc biệt.

## 11. Checklist QA thực hành

- [ ] Đã xác định rõ callback `@PostConstruct` thuộc **target nào** và method cần advice thuộc **target nào**.
- [ ] Với cùng target, constructor/`@PostConstruct` có gọi `this.method()` không? Nếu có, không kỳ vọng proxy AOP intercept.
- [ ] Aspect đã được đăng ký thành Spring bean, AOP auto-proxy được bật, pointcut match method?
- [ ] Với Spring Framework 6.x đã dùng `jakarta.annotation.PostConstruct` và dependency tương ứng?
- [ ] Đã phân biệt bean đang initialization với bean đã hoàn tất và proxy được inject vào một bean khác?
- [ ] Dùng `AopUtils.isAopProxy` **sau context refresh** để kiểm tra reference; gọi method qua chính reference ấy.
- [ ] Không dùng `AopContext.currentProxy()` hoặc self-injection trong constructor/init để né lifecycle.
- [ ] Không đặt remote call, transaction dài, scheduler nặng hay thao tác chờ các bean khác trong init callback?
- [ ] Khi công việc cần startup, đã chọn `SmartInitializingSingleton`, event, lifecycle hook hoặc Boot runner/ready event đúng mốc?
- [ ] Đã test đường gọi qua proxy và đường gọi nội bộ riêng biệt, so kết quả advice với kỳ vọng?
- [ ] Đã tính đến context hierarchy, circular dependency, lỗi startup và khả năng sự kiện được phát lại?

**Điểm nhớ:** Với Spring AOP proxy, `@PostConstruct` chạy trong vòng đời của **raw target** và không phải một lời gọi nghiệp vụ qua proxy; gọi `this.method()` từ đó sẽ bỏ qua advice. Nhưng `@PostConstruct` của một **bean khác** vẫn có thể gọi qua proxy của bean đã được tạo xong. Nếu yêu cầu thực sự cần transaction/security/audit/async advice, hãy **chọn đúng ranh giới bean và mốc startup**, đừng dựa vào interception trong lúc bean còn được khởi tạo.

## Tài liệu Spring chính thức

- [Spring Framework 6.2 — Customizing the Nature of a Bean (lifecycle, raw bean, creation lock)](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html)
- [Spring Framework 6.2 — Container Extension Points (BeanPostProcessor và auto-proxy)](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-extension.html)
- [Spring Framework 6.2 — Using @PostConstruct and @PreDestroy](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/postconstruct-and-predestroy-annotations.html)
- [Spring Framework 6.2 — Proxying Mechanisms (self-invocation)](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Spring Framework 6.2 — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="auto-proxy-mechanics-009">Phụ thuộc vòng và nguy cơ tham chiếu sớm chưa được tăng cường</a>

<details>
<summary>Xem chi tiết</summary>

# Phụ thuộc vòng và nguy cơ tham chiếu sớm chưa được tăng cường trong Spring AOP

## 1. Vấn đề cần hiểu

Spring AOP thường tạo một **proxy** để client gọi method của Spring bean qua advice trước hoặc sau khi vào đối tượng đích (**target**). Khi các bean có **phụ thuộc vòng** (circular dependency), Spring có thể bị yêu cầu đưa một bean cho đối tượng khác **trước khi bean ấy hoàn tất vòng đời khởi tạo**. Đối với một bean có thể được Spring AOP bọc proxy, đây là thời điểm nhạy cảm: client có thể cần **proxy** nhưng container mới tạo xong **target thô (raw target)**.

Ba câu hỏi trung tâm:

1. Trong chu trình Spring tạo bean, **tham chiếu sớm (early reference)** xuất hiện khi nào?
2. Spring AOP phối hợp với cơ chế đó ra sao để tránh việc một nơi giữ raw target còn nơi khác giữ proxy?
3. Tại sao cách sửa đáng tin cậy nhất là **loại bỏ phụ thuộc vòng**, thay vì cố cho phép tiêm một bean chưa hoàn tất khởi tạo?

**Phạm vi:** Bài chỉ phân tích các trường hợp liên quan **Spring Framework, Spring bean lifecycle và Spring AOP auto-proxy**; không mở rộng sang lý thuyết AOP độc lập, compile-time weaving hoặc các framework IoC khác.

## 2. Hai vấn đề dễ bị trộn lẫn

Có ít nhất hai loại sự cố khác nhau.

### 2.1. Vòng phụ thuộc khiến Spring không tạo nổi bean

Ví dụ:

~~~text
PaymentService
    |
    | constructor cần AuditService
    v
AuditService
    |
    | constructor cần PaymentService
    +---------------------------> PaymentService
~~~

Nếu cả hai constructor yêu cầu instance đã tồn tại, Spring không thể hoàn thành đối tượng nào trước. Với vòng phụ thuộc constructor như trên, container có thể ném **BeanCurrentlyInCreationException** (thường nằm trong chuỗi nguyên nhân của BeanCreationException hoặc UnsatisfiedDependencyException).

Spring AOP không phải công cụ phá vòng constructor. Việc cấu hình **@EnableAspectJAutoProxy** không làm cho loại vòng này tự biến mất.

### 2.2. Vòng phụ thuộc có tham chiếu sớm nhưng tham chiếu khác proxy cuối cùng

Một vòng phụ thuộc dựa trên property/setter injection giữa **singleton beans** đôi khi có thể được Spring giải quyết bằng cách đưa ra một **early singleton reference** sau khi đối tượng được cấp phát, trước khi toàn bộ dependency, initialization callback và BeanPostProcessor hoàn tất.

Nhưng nếu Bean A sau cùng phải được bọc thành Spring AOP proxy:

~~~text
A raw target --------------------------> A final proxy
      |                                        ^
      | tham chiếu sớm                          |
      v                                        |
B giữ tham chiếu tới A raw          Client khác nhận A proxy
~~~

Khi B gọi trực tiếp A raw, đường gọi có thể **bỏ qua advice**. Chúng ta có hai tham chiếu không tương đương về hành vi. Đây là nguy cơ **raw bean injected despite wrapping**.

Quan trọng: **không phải mọi circular dependency đều gây tiêm raw target**. Hạ tầng Spring AOP chuẩn có hook để cung cấp **early proxy** nếu có thể. Nguy cơ xuất hiện khi không thể bảo đảm tham chiếu sớm chính là wrapper sẽ được expose cuối cùng, hoặc bean bị tạo quá sớm nên không được áp dụng đủ BeanPostProcessor.

## 3. Ôn đúng những bước cần thiết của Spring bean lifecycle

Đối với một singleton bean điển hình, có thể hình dung:

~~~text
Đăng ký BeanDefinition
        |
        v
Tạo instance / gọi constructor
        |
        v
Populate properties / tiêm dependency
        |
        v
Initialization (@PostConstruct, InitializingBean, init method...)
        |
        v
BeanPostProcessor, gồm hạ tầng Spring AOP auto-proxy
        |
        v
Expose bean cuối cùng (có thể là proxy)
~~~

Thứ tự chi tiết có nhiều hook, nên sơ đồ cố ý giản lược. Việc tạo proxy của Spring AOP được thực hiện qua **BeanPostProcessor**; với auto-proxy creator tiêu biểu, bean có thể được bọc ở bước xử lý sau initialization. Trong một vòng singleton phù hợp, Spring có thêm con đường cung cấp **early singleton reference** giữa giai đoạn tạo instance và hoàn tất khởi tạo.

Vì vậy:

- **Raw bean:** instance target được tạo bằng constructor; chưa thể mặc định coi là object đã qua AOP.
- **Early reference:** tham chiếu được container xuất ra khi một singleton đang được tạo và bean khác cần nó; giá trị có thể là raw object hoặc wrapper/proxy do post-processor cấp.
- **Final exposed bean:** tham chiếu người dùng bình thường nhận từ `ApplicationContext.getBean(...)` sau khi khởi tạo thành công.
- **AOP proxy:** wrapper có thể thực thi interceptor/advice cho các method phù hợp khi client gọi qua nó.

Một bean đã được cấp phát không có nghĩa bean đó đã có đầy đủ dependency, đã chạy initialization hoặc đã có AOP proxy. Tránh gọi logic nghiệp vụ trên một early reference chỉ vì biến tham chiếu đã khác `null`.

## 4. Vì sao Spring từng có thể giải quyết vòng qua setter?

Xét hai singleton:

~~~java
class Alpha {
    private Beta beta;

    void setBeta(Beta beta) {
        this.beta = beta;
    }
}

class Beta {
    private Alpha alpha;

    void setAlpha(Alpha alpha) {
        this.alpha = alpha;
    }
}
~~~

Đây là **fragment mô tả graph**, chưa có annotation đăng ký bean. Khi container được phép thử giải quyết vòng singleton, một chuỗi thực thi có thể là:

1. Bắt đầu tạo **Alpha**: constructor không cần Beta nên có instance raw.
2. Container ghi nhận Alpha đang được tạo và có thể cung cấp **singleton factory** cho yêu cầu tham chiếu sớm.
3. Trong lúc tiêm `beta`, Spring cần tạo **Beta**.
4. Khi tiêm `alpha` cho Beta, Spring phát hiện Alpha đang được tạo; nếu thỏa điều kiện, nó lấy **early reference của Alpha**.
5. Hoàn thành Beta và gán Beta cho Alpha.
6. Chạy initialization, BeanPostProcessor và quyết định tham chiếu Alpha cuối cùng.

Cơ chế này **không áp dụng được khi constructor Alpha cần Beta và constructor Beta cần Alpha**, vì ở điểm vòng quay lại chưa có instance nào được tạo hoàn chỉnh đến mức có thể đưa vào early singleton cache.

Một hệ quả: khả năng giải quyết vòng phụ thuộc phụ thuộc vào **loại dependency, bean scope, container settings và post-processors**; không nên đọc “Spring hỗ trợ circular references” thành bảo đảm mọi vòng đều tạo được bean.

## 5. Cơ chế ba tầng tham chiếu singleton, ở mức cần cho AOP

Trong triển khai Spring, quá trình giải quyết tham chiếu singleton có thể được hiểu bằng ba nhóm nơi lưu:

| Tầng / tên thường gặp trong triển khai | Chứa gì | Vai trò |
| --- | --- | --- |
| `singletonObjects` | Singleton đã được expose hoàn chỉnh | Đường lấy bean thông thường |
| `earlySingletonObjects` | Tham chiếu sớm đã được tạo | Tái sử dụng cùng early reference nếu có yêu cầu sau |
| `singletonFactories` | Factory có thể cung cấp early reference | Cho phép BeanPostProcessor tạo wrapper sớm khi cần |

Khi bean vẫn đang khởi tạo, Spring có thể đăng ký một factory. Nếu bean khác cần nó, factory được dùng để lấy early reference. Đối tượng nhận được từ factory **không bắt buộc là raw target**. Hạ tầng Spring AOP có thể tham gia để trả về proxy.

~~~text
                  Bean A đang khởi tạo
                           |
                    tạo raw target
                           |
                  singleton factory A
                           |
                  B cần dependency A
                           |
                           v
                 getEarlyBeanReference
                           |
             +-------------+-------------+
             |                           |
       early proxy                  raw target
     (nếu tạo được)             (nếu không được bọc)
             |                           |
             v                           v
        B giữ proxy                 B giữ raw target
             |                           |
             v                           v
   có thể nhất quán với            nguy cơ khác hành vi
   final exposed bean              so với final proxy
~~~

Đây là sơ đồ mô tả **khả năng**, không phải một lời hứa rằng mọi aspect đều được Spring xác định kịp thời để tạo early proxy. Vòng phụ thuộc có thể bị từ chối trước khi có early reference, hoặc phát sinh lỗi khi final proxy khác tham chiếu đã tiêm.

## 6. Hook quan trọng: getEarlyBeanReference(...)

Hạ tầng Spring định nghĩa callback của **SmartInstantiationAwareBeanPostProcessor**:

~~~java
Object getEarlyBeanReference(Object bean, String beanName)
~~~

Đây là **chữ ký API minh họa**, không phải một chương trình Java độc lập. Callback cho post-processor cơ hội cung cấp một wrapper trước khi target hoàn toàn initialized.

**AbstractAutoProxyCreator** của Spring AOP triển khai đường đi này. Về mặt ý tưởng:

~~~text
getEarlyBeanReference(rawA, "a")
      |
      +-- xác định bean cần proxy hay không
      |
      +-- nếu cần, có thể trả AOP proxy của rawA
      |
      +-- lưu trạng thái để tránh tạo một proxy khác
          không nhất quán ở cuối initialization
~~~

Sau đó, post-processor xử lý phần còn lại của lifecycle với thông tin rằng bean có thể đã được cấp early proxy. Mục tiêu là **early reference và final exposed reference tương thích**, không để B giữ target thô trong khi client khác nhận proxy khác hành vi.

Cần diễn đạt chính xác hai giới hạn:

- Callback này là cơ chế hỗ trợ **có điều kiện**. Nó **không** làm cho vòng constructor injection được giải quyết.
- `SmartInstantiationAwareBeanPostProcessor` có triển khai mặc định trả bean gốc; một custom post-processor hoặc một tình huống khởi tạo không phù hợp vẫn có thể để lại **raw reference**.

Không dựa vào việc “Spring tự bọc tất cả tham chiếu sớm” để thiết kế graph phụ thuộc vòng.

## 7. Khi raw target được tiêm nhưng final bean trở thành proxy

Giả sử A là service phải được Spring AOP proxy, còn B tham chiếu sớm tới A:

~~~text
B.a ----------------------------------> A raw target

context.getBean(AService.class) ------> A final proxy
                                           |
                                           v
                                        A raw target
~~~

Nếu B gọi `a.process()` qua raw target, advice của Spring AOP có thể không chạy. Các caller khác gọi qua `A final proxy` thì advice chạy. Hai đường gọi có khả năng sinh kết quả phụ trợ khác nhau (ví dụ thiếu audit hoặc tracing), ngay cả khi cùng đi tới nghiệp vụ của một target.

Để tránh trạng thái này, Spring theo mặc định **không cho phép raw injection bất chấp bean về sau bị wrapper**. Trong trường hợp phát hiện sự không nhất quán và có các dependent beans thực sự nhận raw instance, Spring có thể ném **BeanCurrentlyInCreationException** với mô tả theo ý nghĩa:

~~~text
Bean ... has been injected into other beans [...] in its raw version
as part of a circular reference, but has eventually been wrapped.
~~~

Thông báo trên là **trích đoạn dạng mẫu**, không phải expected stdout của một chương trình cụ thể. Chữ của thông báo thực tế phụ thuộc phiên bản Spring và đường tạo bean.

Trong API của `AbstractAutowireCapableBeanFactory` có hai lựa chọn khác nhau:

- **`setAllowCircularReferences(boolean)`**: container có thử xử lý một số vòng dependency hay không.
- **`setAllowRawInjectionDespiteWrapping(boolean)`**: cho phép chấp nhận raw instance đã được inject dù final bean về sau là wrapper hay không; mặc định **false**.

Bật raw injection despite wrapping để che lỗi là rủi ro vì khiến mã gọi có thể vượt qua AOP proxy. Đây không phải biện pháp sửa thiết kế đáng dùng cho service cần advice.

## 8. Chương trình chạy được: thiết kế không vòng, proxy nhất quán

Ta xây một ví dụ **Spring Framework 6.2.12, Java 17+, Maven 3.9+**. Client dùng `PaymentService`. Aspect ghi log qua một bean `AuditRecorder` độc lập; aspect **không inject ngược PaymentService**.

~~~text
spring-aop-early-reference/
├── pom.xml
└── src/main/java/demo/
    ├── PaymentService.java
    ├── PaymentServiceImpl.java
    ├── AuditRecorder.java
    ├── LoggingAspect.java
    ├── CheckoutClient.java
    ├── AppConfig.java
    ├── CycleProbe.java
    └── App.java
~~~

### 8.1. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-early-reference</artifactId>
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

`spring-context` cung cấp ApplicationContext và mang theo Spring AOP cần dùng; `aspectjweaver` cung cấp các kiểu pointcut/advice theo cú pháp `@AspectJ`. Cách thực hiện ở đây **vẫn là Spring AOP proxy**, không phải weaving class bằng AspectJ agent.

### 8.2. Service interface và implementation

**src/main/java/demo/PaymentService.java**

~~~java
package demo;

public interface PaymentService {
    String pay(String orderId);
}
~~~

**src/main/java/demo/PaymentServiceImpl.java**

~~~java
package demo;

public class PaymentServiceImpl implements PaymentService {

    @Override
    public String pay(String orderId) {
        System.out.println("SERVICE " + orderId);
        return "paid:" + orderId;
    }
}
~~~

Implementation không phụ thuộc vào aspect hoặc recorder. Vì service có interface, client lấy bean theo `PaymentService`; điều này phù hợp với JDK dynamic proxy mặc định của cấu hình Spring Framework thuần trong ví dụ.

### 8.3. Collaborator của aspect

**src/main/java/demo/AuditRecorder.java**

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;

public class AuditRecorder {

    private final AtomicInteger count = new AtomicInteger();

    public void record(String orderId) {
        count.incrementAndGet();
        System.out.println("AUDIT " + orderId);
    }

    public int count() {
        return count.get();
    }
}
~~~

`AuditRecorder` độc lập, không inject `PaymentService`. Trường AtomicInteger giúp ví dụ không mắc lỗi tăng `int` không an toàn trong môi trường đồng thời. Đây chỉ là bộ đếm trong bộ nhớ; hệ thống thật cần giải pháp audit phù hợp và chính sách lỗi rõ ràng.

### 8.4. Aspect chỉ phụ thuộc một chiều vào recorder

**src/main/java/demo/LoggingAspect.java**

~~~java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class LoggingAspect {

    private final AuditRecorder recorder;

    public LoggingAspect(AuditRecorder recorder) {
        this.recorder = recorder;
    }

    @Before("execution(* demo.PaymentServiceImpl.pay(..))")
    public void beforePay(JoinPoint joinPoint) {
        String orderId = (String) joinPoint.getArgs()[0];
        recorder.record(orderId);
    }
}
~~~

Lớp có **@Aspect** để Spring AOP đọc advice. Instance `LoggingAspect` sẽ được đăng ký bằng `@Bean`, nên không cần `@Component`. Aspect nhận `AuditRecorder` bằng constructor, **không** nhận service đang bị advice và không sinh graph phụ thuộc vòng.

### 8.5. Client chỉ nhận service từ Spring

**src/main/java/demo/CheckoutClient.java**

~~~java
package demo;

public class CheckoutClient {

    private final PaymentService paymentService;

    public CheckoutClient(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public void checkout(String orderId) {
        String result = paymentService.pay(orderId);
        System.out.println("CLIENT " + result);
    }
}
~~~

Client không dùng `new PaymentServiceImpl()`. Spring resolve `PaymentService` từ container và có thể inject **proxy** vào client.

### 8.6. Cấu hình bean rõ ràng, không vòng

**src/main/java/demo/AppConfig.java**

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy
public class AppConfig {

    @Bean
    public AuditRecorder auditRecorder() {
        return new AuditRecorder();
    }

    @Bean
    public LoggingAspect loggingAspect(AuditRecorder recorder) {
        return new LoggingAspect(recorder);
    }

    @Bean
    public PaymentService paymentService() {
        return new PaymentServiceImpl();
    }

    @Bean
    public CheckoutClient checkoutClient(PaymentService paymentService) {
        return new CheckoutClient(paymentService);
    }
}
~~~

Các phụ thuộc tạo thành một graph một chiều:

~~~text
CheckoutClient ---> PaymentService (proxy) ---> PaymentServiceImpl
LoggingAspect ----> AuditRecorder
                       ^
                       |
              advice gọi khi pay(...)
~~~

Về dependency injection, **LoggingAspect không phụ thuộc PaymentService**; điều kiện pointcut để can thiệp method service **không tương đương với dependency Java của aspect trên service**.

`@EnableAspectJAutoProxy` bật hạ tầng tự tạo proxy và xử lý aspect. `@Bean` đăng ký những instance vào Spring. `proxyBeanMethods = false` ở `@Configuration` chỉ kiểm soát interception của lời gọi giữa các `@Bean` factory methods; **không vô hiệu hóa Spring AOP proxy của PaymentService**.

### 8.7. Thí nghiệm vòng constructor bị từ chối

Ta tạo thêm một context **độc lập** chỉ để chứng minh constructor cycle không thể được cứu bởi early singleton reference. Nó không chứa `AppConfig` hoặc service/bean của kịch bản thành công, để tránh lẫn nguyên nhân lỗi.

**src/main/java/demo/CycleProbe.java**

~~~java
package demo;

import org.springframework.beans.BeansException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class CycleProbe {

    private CycleProbe() {
    }

    static class A {
        A(B b) {
        }
    }

    static class B {
        B(A a) {
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CycleConfig {
        @Bean
        A a(B b) {
            return new A(b);
        }

        @Bean
        B b(A a) {
            return new B(a);
        }
    }

    public static void demonstrate() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setAllowCircularReferences(false);
            context.register(CycleConfig.class);

            try {
                context.refresh();
                System.out.println("CYCLE unexpectedly created");
            } catch (BeansException exception) {
                System.out.println("CYCLE rejected");
            }
        }
    }
}
~~~

`context.setAllowCircularReferences(false)` được gọi trước `refresh()` để phép thử nhất quán và loại bỏ mọi kỳ vọng rằng container sẽ thử resolve vòng. Ngay cả khi cho phép vòng, **constructor A(B) ↔ B(A)** không thể được hoàn thành bằng early reference thông thường.

Ta chỉ in câu cố định **CYCLE rejected** trong catch để tránh gắn bài học với thông điệp stack trace dài, có thể thay đổi giữa các bản Spring. Việc catch ở đây chỉ dùng cho **chương trình minh họa lỗi**, không phải cách ứng dụng production nên nuốt lỗi startup.

### 8.8. Chạy cả trường hợp bình thường và vòng lỗi

**src/main/java/demo/App.java**

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            PaymentService service =
                    context.getBean(PaymentService.class);
            CheckoutClient client =
                    context.getBean(CheckoutClient.class);
            AuditRecorder recorder =
                    context.getBean(AuditRecorder.class);

            System.out.println(
                    "IS_PROXY " + AopUtils.isAopProxy(service));
            client.checkout("ORD-59");
            System.out.println("AUDIT_COUNT " + recorder.count());
        }

        CycleProbe.demonstrate();
    }
}
~~~

Trong thư mục chứa `pom.xml`, chạy:

~~~bash
mvn -q clean compile exec:java
~~~

**Các dòng logic do chính ví dụ in ra:**

~~~text
IS_PROXY true
AUDIT ORD-59
SERVICE ORD-59
CLIENT paid:ORD-59
AUDIT_COUNT 1
CYCLE rejected
~~~

Spring hoặc Maven có thể in cảnh báo/lỗi startup của context thử nghiệm lên stderr; những dòng đó không thuộc stdout logic liệt kê phía trên.

Ý nghĩa:

- **IS_PROXY true**: bean service thông thường từ context đã được Spring AOP expose dưới dạng proxy.
- **AUDIT** đứng trước **SERVICE**: advice `@Before` thực sự được gọi trước target.
- **AUDIT_COUNT 1**: recorder được Spring quản lý ghi chính xác một sự kiện.
- **CYCLE rejected**: context thứ hai gặp vòng phụ thuộc constructor và fail khi `refresh()`; lỗi được bắt có chủ đích để chương trình tiếp tục kết thúc bình thường.

Ví dụ không cố ép một vòng setter “thành công bằng mọi giá”. Mục đích là chỉ ra giải pháp có thể chứng minh **cùng một proxy đi qua advice** và **graph dependency không vòng**.

## 9. Một lỗi thiết kế AOP thường xuất hiện: aspect phụ thuộc vào chính service được advise

Xét cấu hình nguy hiểm sau, **chỉ là ví dụ phản diện**, không thêm trực tiếp vào project chạy ở mục 8:

~~~java
@Aspect
@Component
class TraceAspect {
    private final OrderService orderService;

    TraceAspect(OrderService orderService) {
        this.orderService = orderService;
    }

    @Before("execution(* example.OrderService.submit(..))")
    public void beforeSubmit() {
        // advice
    }
}

@Service
class OrderServiceImpl implements OrderService {
    private final TraceAspect traceAspect;

    OrderServiceImpl(TraceAspect traceAspect) {
        this.traceAspect = traceAspect;
    }

    public void submit() {
        // business logic
    }
}
~~~

Để biên dịch fragment trên cần thêm interface `OrderService`, import Spring/AspectJ và package tương ứng; đây **không phải ví dụ độc lập có thể chạy ngay**. Graph là:

~~~text
OrderServiceImpl -> TraceAspect -> OrderServiceImpl
~~~

Cả hai bên đều đòi dependency constructor. Khi Spring cố tạo proxy cho service, nó còn phải xét các advisor từ aspect, khiến thứ tự khởi tạo có thể trở nên khó chẩn đoán hơn. Nhưng nguyên nhân thiết kế cơ bản vẫn là **vòng phụ thuộc giữa các bean**.

**Không có quy tắc cấm tuyệt đối aspect inject service mà nó advise** trong mọi trường hợp. Tuy nhiên, cách đó dễ dẫn đến graph vòng hoặc tái nhập advice nếu aspect lại gọi method thuộc pointcut. Một cách rõ ràng hơn là để aspect phụ thuộc vào **AuditRecorder**, **EventPublisher** hoặc collaborator độc lập; service chỉ mang nghiệp vụ, không inject aspect.

## 10. Tham chiếu sớm đã là proxy thì còn nguy cơ gì?

Ngay cả khi một `AbstractAutoProxyCreator` có thể trả **early proxy**, vẫn phải hiểu những giới hạn sau:

1. **Target chưa hoàn tất initialization**. Proxy có thể trỏ tới object mà field/property hoặc init callback chưa hoàn thành. Nếu caller gọi method quá sớm, method có thể đọc trạng thái chưa sẵn sàng.
2. **Lựa chọn advisor có thể bị ảnh hưởng bởi bean đang được tạo**. Aspect hoặc dependency của auto-proxy creator cũng có vòng phụ thuộc, khiến việc xác định wrapper khó bảo đảm.
3. **Thứ tự BeanPostProcessor rất quan trọng**. Những custom BeanPostProcessor không biết xử lý early reference, hoặc tạo wrapper khác nhau ở đầu/cuối, có thể làm final object khác early object.
4. **Một số bean khởi tạo quá sớm để được auto-proxying đầy đủ**. Đặc biệt, các BeanPostProcessor và bean chúng trực tiếp phụ thuộc có thể được tạo trong giai đoạn container đang đăng ký post-processor. Log kiểu *not eligible for getting processed by all BeanPostProcessors* là dấu hiệu cần kiểm tra.
5. **Proxy vẫn chỉ intercept những method invocation đủ điều kiện**. Self-invocation trên target không qua proxy, bất kể proxy được tạo sớm hay muộn.

Vì vậy, câu “Spring xử lý được vòng setter” không đồng nghĩa “mọi advice của Spring AOP vẫn an toàn và toàn bộ dependency đã initialized”.

## 11. Cách sửa: phá vòng phụ thuộc tại thiết kế

### 11.1. Tách bean dùng chung độc lập

Graph lỗi:

~~~text
OrderService -> AuditService -> OrderService
~~~

Refactor thành:

~~~text
OrderService --> PaymentRepository
TraceAspect  --> AuditRecorder
OrderService <-- Spring AOP proxy/advice matching (không phải DI arrow)
~~~

Đây là giải pháp khuyến nghị. Đưa chức năng ghi audit vào `AuditRecorder` hay một event publisher độc lập, **không inject ngược OrderService** chỉ để biết mã đơn hoặc kết quả. Advice có thể lấy argument, signature và return value từ `JoinPoint`/các kiểu advice tương ứng mà không tạo dependency ngược vào service.

Khi cần tham khảo dữ liệu của một lần gọi, truyền chính dữ liệu đó từ tham số method/advice; không cần service bean tự tham chiếu lại vào aspect.

### 11.2. Dùng ObjectProvider khi thực sự cần lookup muộn

Spring có thể tiêm `ObjectProvider<T>` để **trì hoãn việc lấy bean**:

~~~java
class DeferredCaller {
    private final org.springframework.beans.factory.ObjectProvider<OrderService>
            serviceProvider;

    DeferredCaller(
            org.springframework.beans.factory.ObjectProvider<OrderService>
                    serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    void callLater() {
        OrderService service = serviceProvider.getObject();
        service.submit();
    }
}
~~~

Đây là fragment minh họa, cần định nghĩa đúng `OrderService.submit()` và đăng ký bean trong context. `ObjectProvider` làm dependency được resolve **lúc `getObject()`**, không phải lúc constructor nhận provider.

Đây **không phải thuốc chữa phụ thuộc vòng tự động**. Nếu gọi `getObject()` ngay trong constructor hoặc `@PostConstruct` khi graph chưa ổn định, lỗi vẫn có thể xảy ra. Nếu thật sự cần lookup muộn, phải có ranh giới lifecycle rõ ràng và lý do kiến trúc hợp lệ.

### 11.3. @Lazy có thể dời thời điểm, không thay được thiết kế

Khai báo minh họa:

~~~java
SomeClient(@Lazy OrderService orderService) {
    this.orderService = orderService;
}
~~~

Import `org.springframework.context.annotation.Lazy` khi dùng. Với dependency injection, `@Lazy` có thể tạo **lazy-resolution proxy** cho điểm tiêm và chỉ resolve target bean khi phương thức được sử dụng. Proxy trì hoãn resolve dependency này **không nhất thiết là Spring AOP proxy** thực thi advice.

Nếu phương thức được gọi quá sớm trong lúc vòng bean vẫn tồn tại, vấn đề quay lại. Dùng `@Lazy` chỉ sau khi hiểu rõ dependency cần được dùng ở giai đoạn nào; với graph AOP quan trọng, refactor loại vòng vẫn là hướng ưu tiên.

### 11.4. Không dùng cấu hình cho phép vòng làm giải pháp mặc định

Trong **Spring Framework thuần**, bean factory có thể được cấu hình để thử giải quyết circular references và mặc định của `AbstractAutowireCapableBeanFactory` cho `allowCircularReferences` là **true**. Trong **Spring Boot** các bản hiện đại, property `spring.main.allow-circular-references` mặc định **false**.

Việc cố bật property dưới đây trong Boot:

~~~properties
spring.main.allow-circular-references=true
~~~

không giải quyết được vòng constructor bất khả thi và không bảo đảm Spring AOP proxy nhất quán cho mọi trường hợp. Đây là cấu hình hỗ trợ tạm thời khi di chuyển hệ thống cũ, cần đánh giá, thử nghiệm và có kế hoạch loại bỏ vòng. Không chọn nó làm sửa lỗi đầu tiên.

Cũng không khuyến nghị bật `setAllowRawInjectionDespiteWrapping(true)` để buộc container chấp nhận tham chiếu raw; điều đó có thể khiến dependency ở một bean không chạy các advice mong đợi.

## 12. Cách điều tra khi ứng dụng khởi tạo lỗi

### Bước 1: Vẽ graph dependency từ chuỗi exception

Ví dụ log có thể xuất hiện nhiều tầng:

~~~text
UnsatisfiedDependencyException
  caused by BeanCurrentlyInCreationException
    "Requested bean is currently in creation ..."
~~~

Đọc **bean names** trong exception và tìm đường vòng: service → collaborator → aspect → service; hoặc service A → service B → service A. Đừng chỉ đọc class cuối cùng trong stack trace.

### Bước 2: Xác định vòng constructor hay vòng có khả năng tham chiếu sớm

- **Constructor ↔ constructor**: refactor, thường không thể resolve bằng cơ chế early reference.
- **Setter/property vòng singleton**: có thể tồn tại tham chiếu sớm, nhưng phải phân tích điều kiện container và auto-proxy creator.
- **Prototype hoặc scope đặc biệt**: không suy ra có khả năng resolve như singleton.
- **Aspect ↔ advised service**: kiểm tra graph DI và khả năng aspect gọi ngược vào service.

### Bước 3: Kiểm tra bean nào đáng lẽ được proxy

Khi context có thể khởi tạo, xác minh bean lấy qua context:

~~~java
Object service = context.getBean("paymentService");

boolean isProxy =
        org.springframework.aop.support.AopUtils.isAopProxy(service);
System.out.println("IS_PROXY " + isProxy);
~~~

Đây là fragment debug, chỉ chạy khi đã có biến `context` và bean tên `paymentService`. Không suy ra **mọi method** đều được chặn chỉ từ `isProxy == true`; phải thử invocation đúng pointcut.

### Bước 4: Tìm dấu hiệu raw injection hoặc khởi tạo quá sớm

Những chuỗi cần chú ý:

~~~text
Bean ... injected ... in its raw version
... but has eventually been wrapped

Bean ... is not eligible for getting processed
by all BeanPostProcessors
~~~

Đây là **mẫu thông điệp**, không phải dữ liệu log từ hệ thống đang chạy. Thông điệp raw injection gợi ý final wrapper khác tham chiếu đã cấp cho dependent bean. Thông điệp về BeanPostProcessor gợi ý có bean được tạo quá sớm nên không đi qua toàn bộ post-processing.

Đừng cố sửa bằng cách bỏ exception guard. Hãy xác định bean nào kéo theo việc khởi tạo sớm và thay đổi dependency graph hoặc cấu hình phase tạo bean.

### Bước 5: Kiểm tra lời gọi thực tế có đi qua proxy

Nếu context khởi tạo thành công nhưng advice không chạy:

- Kiểm tra method và signature có khớp pointcut hay không.
- Kiểm tra caller dùng Spring-managed proxy hay trực tiếp `new` target.
- Kiểm tra có self-invocation bằng `this.method()` trong target hay không.
- Nếu có custom wrapper/BeanPostProcessor, kiểm tra client đã giữ **final proxy** hay giữ raw/early object.
- Với JDK proxy, inject theo interface nếu triển khai interface; đừng ép kiểu proxy về class implementation tùy tiện.

## 13. Những lỗi thường gặp và tác động

| Triệu chứng | Nguyên nhân khả dĩ | Hành động |
| --- | --- | --- |
| Startup báo `BeanCurrentlyInCreationException` | Constructor cycle hoặc bean raw đã được inject trước khi bị bọc | Đọc nguyên nhân gốc, dựng dependency graph, phá vòng |
| Vòng setter từng chạy ở Framework thuần nhưng lỗi sau chuyển Spring Boot | Boot mặc định từ chối circular references | Refactor graph, không mặc định bật property cho phép vòng |
| Client A được advice nhưng client B không | B đang giữ raw target hoặc đi đường trực tiếp không qua proxy | Xác minh identity proxy/target và điểm inject, loại bỏ vòng |
| Aspect giữ tham chiếu tới service mà chính nó advise | Dependency graph khó khởi tạo hoặc advice gọi ngược service | Tách collaborator độc lập, dùng dữ liệu của JoinPoint |
| Bean báo “not eligible for auto-proxying” | Khởi tạo sớm trong giai đoạn BeanPostProcessor | Kiểm tra custom BPP và dependency bị khởi tạo quá sớm |
| `@Lazy` hết lỗi lúc startup nhưng lỗi lúc gọi | Lookup muộn vẫn tạo vòng trong runtime | Thiết kế lại quan hệ phụ thuộc, xem thời điểm `getObject()` |
| Cho phép raw injection rồi mất advice | Dependent bean giữ raw target thay vì final proxy | Không dùng đường raw; refactor và kiểm thử proxy behavior |
| `AopUtils.isAopProxy` trả true nhưng advice mong đợi không chạy | Proxy do advisor khác hoặc pointcut không khớp | Kiểm tra aspect bean, advisor và invocation thực |
| Tham chiếu đến bean trước khi init xong làm lỗi trạng thái | Early reference được gọi quá sớm | Đừng gọi nghiệp vụ ở constructor/init callback của graph vòng |

## 14. Những phân biệt cần nhớ

**Phụ thuộc vòng** là một vấn đề **dependency graph**. **Raw bean được inject nhưng cuối cùng được bọc proxy** là một vấn đề **tham chiếu không nhất quán theo lifecycle**. Hai vấn đề có liên quan nhưng không đồng nghĩa: có vòng không nhất thiết có raw mismatch, và việc có early proxy không làm vòng constructor biến mất.

**AOP proxy** có thể giúp lời gọi đi qua advice, nhưng chỉ khi caller thật sự giữ và sử dụng proxy phù hợp. Tham chiếu sớm có thể là proxy nếu hạ tầng Spring AOP cấp được; không phải cứ gọi early reference là chắc chắn bypass AOP.

**Bật circular references** chỉ cho phép container thử một số đường khởi tạo. Nó không làm cho mọi dependency graph hợp lệ, cũng không phải chính sách bảo đảm bean đã hoàn tất initialization khi được gọi.

**Đọc exception sớm** thường đáng tin cậy hơn việc tìm cách tắt exception: nếu container báo một bean đã được inject raw rồi wrap, mục tiêu của cảnh báo là tránh hành vi AOP không đồng nhất giữa các caller.

## 15. Câu hỏi tự đánh giá

**1. Vì sao vòng A(B) và B(A) qua constructor không thể dùng early singleton reference để giải quyết?**

Vì cả hai constructor cần instance của phía kia trước khi một instance có thể được tạo để đăng ký làm early reference.

**2. Early reference có luôn là raw target không?**

Không. Spring AOP auto-proxy creator có thể cung cấp wrapper sớm qua `getEarlyBeanReference`. Điều đó tùy tình huống và post-processors, không phải bảo đảm chung.

**3. Nếu một dependent bean giữ raw target còn `context.getBean(...)` trả proxy, nguy cơ gì?**

Những lời gọi qua raw target có thể bypass advice trong khi lời gọi qua proxy chạy advice, dẫn tới hành vi phụ trợ không nhất quán.

**4. `setAllowCircularReferences(true)` có tương đương `setAllowRawInjectionDespiteWrapping(true)` không?**

Không. Cái thứ nhất cho phép thử xử lý circular references; cái thứ hai cho phép raw bean được inject dù final bean bị bọc, một tình huống Spring mặc định tránh.

**5. Tại sao aspect inject ngược service nó advise có thể tạo vấn đề?**

Vì aspect và auto-proxy creator cần tham gia xác định việc advice cho service, trong khi graph dependency có thể yêu cầu service được tạo xong trước khi aspect tạo được. Nếu chiều ngược lại hiện diện, dễ thành vòng hoặc khởi tạo sớm.

**6. Sử dụng `ObjectProvider` hay `@Lazy` có sửa tận gốc vòng không?**

Không. Chúng dời thời điểm resolve dependency. Nếu logic gọi quá sớm hoặc graph vẫn có vòng không thể giải quyết, lỗi vẫn xuất hiện.

**7. Sau khi refactor, phải kiểm tra gì để chứng minh AOP hoạt động?**

Bean được khởi tạo thành công, service được lấy từ context là proxy phù hợp, advice được gọi trước/sau method mong muốn, và dependency không còn giữ raw target đi ngoài proxy.

## 16. Tài liệu Spring Framework chính thức

- **Spring Framework — Dependency Injection (Circular dependencies)**: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html
- **Spring Framework — BeanPostProcessor và auto-proxying**: https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html
- **Spring Framework API — AbstractAutoProxyCreator#getEarlyBeanReference**: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/framework/autoproxy/AbstractAutoProxyCreator.html
- **Spring Framework API — AbstractAutowireCapableBeanFactory#setAllowCircularReferences / setAllowRawInjectionDespiteWrapping**: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/support/AbstractAutowireCapableBeanFactory.html
- **Spring Framework — Proxying Mechanisms**: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- **Spring Boot — Common Application Properties (allow-circular-references)**: https://docs.spring.io/spring-boot/appendix/application-properties/index.html

**Kết luận của bài:** Phụ thuộc vòng có thể buộc Spring cấp tham chiếu sớm khi bean chưa hoàn tất vòng đời. Hạ tầng Spring AOP có thể cấp **early proxy** để giữ nhất quán với proxy cuối cùng, nhưng không phải mọi vòng hay mọi custom post-processor đều an toàn. Thiết kế bean dependency một chiều, đăng ký aspect rõ ràng và kiểm tra lời gọi qua Spring proxy là cách đáng tin cậy để tránh việc tham chiếu raw âm thầm bỏ qua advice.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="auto-proxy-mechanics-010">Phân biệt proxy AOP với proxy do cơ chế khác trong Spring tạo ra</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt proxy AOP với proxy do cơ chế khác trong Spring tạo ra

## 1. Vì sao không nên kết luận “có proxy là có `@Aspect`”?

Spring Framework sử dụng cơ chế **ủy quyền và tăng cường hành vi** ở nhiều nơi. Khi debug một Spring bean, bạn có thể thấy class sinh động chứa `$$SpringCGLIB$$`, một JDK dynamic proxy hoặc một instance trả về `true` khi gọi `AopUtils.isAopProxy(...)`. Các quan sát ấy **không tự chứng minh** rằng bean đang chạy advice từ `@Aspect` do ứng dụng viết.

Trong chủ đề **Cơ chế Auto-proxy**, bài này trả lời bốn câu hỏi cụ thể:

1. Proxy của **Spring AOP** dùng advisor/advice để chặn phương thức khác gì proxy hỗ trợ **scope** và proxy do các tiện ích khác tạo ra?
2. Vì sao `@Transactional`, `@Cacheable` và `@Async` cũng có thể dùng hạ tầng AOP dù ứng dụng không viết một lớp `@Aspect` nào?
3. Vì sao class `@Configuration` được Spring **CGLIB-enhance** không đồng nghĩa với việc nó là Spring AOP proxy?
4. Dựa trên API nào để xác minh **loại proxy, advisor, target và hành vi thực tế** thay vì chỉ đoán từ tên class?

Phạm vi tập trung vào **Spring Framework/Spring Boot và Spring AOP proxy-based**; không trình bày lại lý thuyết Aspect tổng quát.

## 2. Tách ba trục để nhận diện đúng proxy

Một proxy nên được phân loại theo **ai tạo**, **mục đích** và **hạ tầng kỹ thuật**. Ba điều này có quan hệ nhưng không đồng nhất.

| Trục | Câu hỏi cần hỏi | Ví dụ |
| --- | --- | --- |
| Nguồn gốc | BeanPostProcessor/FactoryBean/cơ chế nào tạo hoặc tăng cường instance? | Spring AOP auto-proxy creator, `ScopedProxyFactoryBean`, `ConfigurationClassEnhancer`, Spring Data repository factory |
| Mục đích | Proxy giải quyết chức năng gì? | Gọi `@Before`, mở transaction, cache kết quả, tìm target đúng scope, giữ ngữ nghĩa gọi `@Bean` |
| Công nghệ | Wrapper được tạo bằng cách nào? | JDK dynamic proxy, CGLIB subclass, bytecode enhancement, interface adapter |
 
Cả Spring AOP lẫn scoped proxy có thể sử dụng cùng **hạ tầng `spring-aop`** và cùng công nghệ JDK/CGLIB. Ngược lại, `@Configuration` cũng dùng CGLIB nhưng phục vụ một cơ chế riêng. Vì vậy không thể phân loại chỉ bằng chữ `CGLIB` hoặc `Proxy` trong tên class.

### 2.1. Hiểu đúng cụm từ “proxy AOP”

Cụm từ này có hai mức độ:

- **Spring AOP proxy về kỹ thuật:** object tạo bởi hạ tầng Spring AOP, có marker `SpringProxy`; có thể phát hiện bằng `AopUtils.isAopProxy(object)`. Nó có thể phục vụ advisor nghiệp vụ, transaction/cache, **hoặc scoped delegation**.
- **Proxy có advice từ `@Aspect` cụ thể:** hẹp hơn. Cần chứng minh advisor của aspect đó được gắn vào proxy và lời gọi phương thức **khớp pointcut** nên advice thực sự chạy.

Do đó **`isAopProxy == true` không đồng nghĩa “advice của tôi đang chạy”**, và **`isAopProxy == false` không đồng nghĩa object không được tăng cường bằng cách khác**.

## 3. So sánh các cơ chế phổ biến trong Spring

| Trường hợp | Ai tạo/cấu hình hành vi | Thường dùng Spring AOP proxy? | Có cần `@Aspect` tự viết? | Ý nghĩa quan trọng |
| --- | --- | --- | --- | --- |
| `@Aspect` + `@EnableAspectJAutoProxy` | Spring AOP auto-proxy creator chọn advisors từ aspect bean | **Có** | Có, nếu chính bạn định nghĩa advice dạng `@AspectJ` | Phương thức trên target bean nhận advice khi gọi qua proxy và pointcut khớp |
| `@Transactional` với `@EnableTransactionManagement(mode = PROXY)` | Transaction advisor + `TransactionInterceptor` | **Có** | **Không** | Proxy điều phối transaction quanh method khớp metadata |
| `@Cacheable` với `@EnableCaching(mode = PROXY)` | Cache advisor + interceptor của cache abstraction | **Có** | **Không** | Proxy kiểm tra/lưu cache, có thể bỏ qua việc chạy target ở cache hit |
| `@Async` với `@EnableAsync` | Async annotation processing/advisor | Thường **có** ở chế độ proxy; chi tiết tích hợp có thể tùy phiên bản | **Không** | Chuyển lời gọi phù hợp sang executor, chịu giới hạn proxy |
| `@Scope(proxyMode = TARGET_CLASS)` hoặc `<aop:scoped-proxy/>` | Scoped proxy factory/target source | **Có thể nhận diện bằng `AopUtils`** vì dùng hạ tầng Spring AOP | **Không** | Tra cứu target đúng scope vào thời điểm gọi |
| `@Configuration(proxyBeanMethods = true)` | Spring `ConfigurationClassEnhancer` | **Không mặc nhiên** | **Không** | CGLIB subclass giúp lời gọi `@Bean` được điều phối qua container |
| `@Lookup` method injection | Spring bean method override/subclass enhancement | **Không mặc nhiên** | **Không** | Thay thế lời gọi method lookup bằng truy xuất bean |
| JDK proxy được trả về từ một `@Bean` tùy biến | Mã dùng `java.lang.reflect.Proxy` | **Không mặc nhiên** | **Không** | Bean có thể là JDK proxy nhưng không mang marker Spring AOP |
| Spring Data repository | Repository factory tạo implementation/proxy và interceptors | Thường dùng cơ sở `ProxyFactory`/AOP trong Spring Data | **Không** | Repository API được dispatch đến query/implementation; không suy ra có advice `@Aspect` |

**Lưu ý về `@Transactional`, `@Cacheable`, `@Async`:** Chúng không phải ví dụ “không dùng Spring AOP” khi ở **proxy mode**; chúng là minh chứng hạ tầng **AOP còn phục vụ các tính năng Spring khác**, không cần một lớp `@Aspect` của người dùng. Một bean có thể đồng thời nhận nhiều advisor từ nhiều nguồn. Với chế độ AspectJ weaving nếu được cấu hình riêng, những cơ chế này có thể hoạt động khác; bảng tập trung vào proxy mode.

**Lưu ý về scoped proxy:** Đây là trường hợp dễ gây bất ngờ. Proxy scope có thể trả về `true` với `AopUtils.isAopProxy(...)` nhưng mục đích là **chọn target từ scope**, không phải tự động gọi một `@Before` của ứng dụng. Sự khác biệt là **advisor/target source và hành vi**, chứ không chỉ loại hạ tầng proxy.

## 4. Spring AOP auto-proxy tạo proxy khi nào?

Trong Spring AOP dựa trên `@AspectJ`, cần các điều kiện điển hình:

1. Aspect đã thành **Spring bean** (`@Aspect @Component` trong vùng scan hoặc một `@Bean` trả về instance `@Aspect`).
2. Hỗ trợ Spring AOP hoạt động (ví dụ `@EnableAspectJAutoProxy`; với Spring Boot có thể do auto-configuration của starter AOP/AspectJ).
3. Advisor/pointcut phù hợp với target bean.
4. Bean đủ điều kiện proxy và client gọi phương thức **qua proxy**.

Đường gọi minh họa:

```text
Client -> Spring bean reference (AOP proxy)
            -> interceptor/advice matching method
            -> target.method()
            -> trả kết quả
```

Spring AOP có hai chiến lược tạo wrapper cơ bản:

- **JDK dynamic proxy** khi proxy theo interface: đối tượng triển khai các interface được expose, không phải instance trực tiếp của implementation class.
- **CGLIB class-based proxy** khi proxy theo lớp: đối tượng là subclass sinh động của target class, chịu các giới hạn `final`, `private` và khả năng override method.

Chính **nguồn advisor và đường gọi** mới giải thích tại sao advice chạy. Tên class được sinh từ JDK hoặc CGLIB chỉ mô tả kỹ thuật tạo proxy.

## 5. Proxy transaction/cache/async: AOP nhưng không nhất thiết có `@Aspect`

### 5.1. `@Transactional`

Với annotation-driven transaction management ở **proxy mode**, Spring thường tạo một advisor sử dụng `TransactionInterceptor` và `TransactionAttributeSource`, kết hợp với `TransactionManager`. Client gọi method cần transaction qua bean proxy; interceptor bắt đầu/tham gia transaction, gọi method và commit/rollback theo cấu hình.

```java
// Snippet để chèn vào ứng dụng có cấu hình transaction manager phù hợp.
// Không phải chương trình độc lập.
@Service
public class PaymentService {
    @Transactional
    public void pay() {
        // Thực thi nghiệp vụ trong transaction.
    }
}
```

Bạn không cần viết `@Aspect` để có transaction advice. Ngược lại, chỉ thêm `@Transactional` mà thiếu transaction infrastructure/manager và cấu hình cần thiết không đảm bảo transaction hoạt động.

`@Transactional` ở **proxy mode** cũng chịu giới hạn self-invocation: `this.pay()` bên trong target có thể bỏ qua interceptor transaction. Điều này xảy ra vì cơ chế dựa trên Spring AOP proxy.

### 5.2. `@Cacheable`

Với `@EnableCaching` ở proxy mode, Spring tạo cache-related advisor/interceptor cho bean phù hợp:

```java
// Snippet minh họa, cần CacheManager và caching đã được bật.
@Cacheable("products")
public Product findProduct(String id) {
    return repository.findById(id).orElseThrow();
}
```

Cache hit có thể trả kết quả ngay, **không gọi method target**. Một lần không thấy log bên trong target không đồng nghĩa proxy/pointcut không hoạt động. Cần kiểm tra `CacheManager`, cache name, key và cache advice. Một proxy tạo cho caching có thể cho `AopUtils.isAopProxy(bean) == true` mà ứng dụng hoàn toàn không khai báo một `@Aspect` tự viết.

### 5.3. `@Async`

Trong cấu hình proxy, `@Async` dùng Spring infrastructure để xử lý invocation và chuyển việc thực thi phù hợp sang executor. Nó không yêu cầu `@Aspect` của người dùng. Với Spring Framework, việc bật `@EnableAsync`, cung cấp executor và xử lý return type nằm trong cấu hình chức năng async; ở đây chỉ quan tâm rằng **proxy có thể sinh ra để phục vụ mục đích khác nhau**.

**Không đồng nhất ba tính năng:** Có proxy transaction không có nghĩa cache advice đã được bật; có proxy async không có nghĩa mọi method đều chạy ngoài thread hiện tại. Muốn kết luận phải xem advisor và metadata annotation thực tế.

## 6. Scoped proxy: dùng hạ tầng AOP để tìm target theo scope

Ví dụ: một singleton cần gọi một bean có scope ngắn hơn (request/session) hoặc một `prototype`. Thay vì giữ cố định một target không đúng scope, Spring có thể inject **scoped proxy**. Proxy này tìm instance target phù hợp từ container khi method được gọi.

```java
@Bean
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE,
       proxyMode = ScopedProxyMode.TARGET_CLASS)
public ScopedWorker scopedWorker() {
    return new ScopedWorker();
}
```

Đây là **đoạn minh họa trong class `@Configuration`**, cần import `@Bean`, `@Scope`, `ConfigurableBeanFactory` và `ScopedProxyMode`. Nó không phải chương trình độc lập; chương 8 sẽ có project chạy được.

Đối với `prototype` scoped proxy:

```text
Singleton caller -> cùng một scoped proxy
                      |
            mỗi lần gọi method
                      |
                  BeanFactory
                      |
               prototype target mới
                      |
                 target.method()
```

Với `request` hoặc `session` scope, Spring tra target tương ứng với request/session hiện tại; nếu gọi khi scope không hoạt động, có thể nhận exception liên quan scope. Không dùng một `request` scoped bean trực tiếp trong ứng dụng console không có request context.

**Điểm then chốt:** scoped proxy có thể là một **Spring AOP proxy thật** xét về marker/hạ tầng (`ScopedProxyFactoryBean` dùng Spring AOP proxy machinery), nhưng **không đồng nghĩa** nó có `@Aspect` advice. Nếu có advisor phù hợp khác, nhiều chức năng proxy có thể cùng xuất hiện hoặc được phối hợp.

## 7. `@Configuration` CGLIB enhancement khác AOP proxy

Một class `@Configuration(proxyBeanMethods = true)` (mặc định trong chế độ full configuration) được Spring tăng cường bằng một subclass CGLIB để lời gọi giữa các method `@Bean` tuân theo container và scope của bean:

```java
@Configuration(proxyBeanMethods = true)
public class SampleConfig {
    @Bean
    public Repository repository() {
        return new Repository();
    }

    @Bean
    public Service service() {
        return new Service(repository());
    }
}
```

Đây là **đoạn minh họa**, giả định các class `Repository`, `Service` tồn tại. Với singleton beans, lời gọi `repository()` từ `service()` được subclass tăng cường can thiệp để lấy bean từ context phù hợp, thay vì luôn tạo một instance mới độc lập.

So sánh:

| CGLIB-enhanced `@Configuration` | Class-based Spring AOP proxy |
| --- | --- |
| Tạo bởi hạ tầng `ConfigurationClassEnhancer` | Tạo bởi hạ tầng Spring AOP proxy |
| Mục tiêu là bảo toàn ngữ nghĩa method `@Bean` và inter-bean references | Mục tiêu là định tuyến lời gọi qua interceptor/advisor |
| Không tự động có marker `SpringProxy` chỉ vì CGLIB | Spring AOP proxy có marker `SpringProxy` |
| `AopUtils.isAopProxy(configBean)` thường là `false` | `AopUtils.isAopProxy(aopProxy)` là `true` |
| `configBean.getClass() != SampleConfig.class` có thể `true` | Target class cũng có thể khác runtime class vì proxy |

Khi dùng `@Configuration(proxyBeanMethods = false)`, Spring **không dùng kiểu tăng cường gọi chéo `@Bean` này**. Nhưng đặt `false` **không phải cách tắt Spring AOP** cho các service beans; đó là hai cơ chế riêng.

Cũng cần phân biệt `@Lookup` method injection: Spring có thể dùng subclass để override method lookup lấy bean, nhưng mục đích là **bean lookup**, không phải tự động thực thi `@Aspect` advice.

## 8. Ví dụ Java 17+ chạy được: so sánh ba cơ chế Spring và một JDK proxy

Chương trình sau sử dụng Spring Framework 6.2.19, Java 17+, Maven và không cần Spring Boot, HTTP server, database hoặc transaction manager. Nó chứng minh:

- `BillingService` được proxy bởi advisor từ một `@Aspect`.
- `ScopedCounter` được proxy để cung cấp target `prototype` phù hợp dù **không có advice `@Aspect` nhắm tới nó**.
- `AppConfig` là CGLIB-enhanced configuration class nhưng **không phải Spring AOP proxy** trong cấu hình này.
- Một `Runnable` dùng `java.lang.reflect.Proxy` được trả về từ `@Bean` là **Spring-managed bean có JDK proxy**, nhưng không phải Spring AOP proxy.

### 8.1. Cấu trúc thư mục

```text
proxy-origin-demo/
├── pom.xml
└── src/main/java/example/
    ├── AppConfig.java
    ├── BillingService.java
    ├── BillingAspect.java
    ├── ScopedCounter.java
    └── Main.java
```

### 8.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>proxy-origin-demo</artifactId>
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

### 8.3. `BillingService.java`

```java
package example;

public class BillingService {
    public void charge() {
        System.out.println("billing: charge");
    }
}
```

### 8.4. `BillingAspect.java`

```java
package example;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class BillingAspect {
    @Before("execution(* example.BillingService.charge(..))")
    public void beforeCharge() {
        System.out.println("aspect: before charge");
    }
}
```

### 8.5. `ScopedCounter.java`

```java
package example;

import java.util.concurrent.atomic.AtomicInteger;

public class ScopedCounter {
    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private final int id = NEXT_ID.incrementAndGet();

    public int id() {
        return id;
    }
}
```

Mỗi instance `ScopedCounter` giữ một `id` riêng. Nếu hai lần gọi `id()` được ủy quyền đến hai prototype targets mới, kết quả sẽ khác nhau.

### 8.6. `AppConfig.java`

```java
package example;

import java.lang.reflect.Proxy;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

@Configuration(proxyBeanMethods = true)
@EnableAspectJAutoProxy
public class AppConfig {
    @Bean
    public BillingService billingService() {
        return new BillingService();
    }

    @Bean
    public BillingAspect billingAspect() {
        return new BillingAspect();
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE,
           proxyMode = ScopedProxyMode.TARGET_CLASS)
    public ScopedCounter scopedCounter() {
        return new ScopedCounter();
    }

    @Bean
    public Runnable plainJdkProxy() {
        Runnable delegate = () -> System.out.println("jdk: delegated");
        return (Runnable) Proxy.newProxyInstance(
            AppConfig.class.getClassLoader(),
            new Class<?>[] { Runnable.class },
            (proxy, method, args) -> method.invoke(delegate, args)
        );
    }
}
```

Tại sao có `new` trong các method `@Bean` vẫn hợp lệ? Vì **giá trị trả về** được Spring đăng ký làm bean và có thể được các cơ chế post-processing/proxy xử lý. `new` bên ngoài container cho một instance riêng thì không tự nhận cơ chế đó.

### 8.7. `Main.java`

```java
package example;

import java.lang.reflect.Proxy;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            BillingService billing = context.getBean(BillingService.class);
            ScopedCounter counter = context.getBean(ScopedCounter.class);
            AppConfig config = context.getBean(AppConfig.class);
            Runnable jdk = context.getBean("plainJdkProxy", Runnable.class);

            System.out.println("billing Spring AOP proxy: "
                + AopUtils.isAopProxy(billing));
            System.out.println("scope Spring AOP proxy: "
                + AopUtils.isAopProxy(counter));
            System.out.println("scope targets differ: "
                + (counter.id() != counter.id()));
            System.out.println("config subclassed: "
                + (config.getClass() != AppConfig.class));
            System.out.println("config Spring AOP proxy: "
                + AopUtils.isAopProxy(config));
            System.out.println("plain JDK dynamic proxy: "
                + Proxy.isProxyClass(jdk.getClass()));
            System.out.println("plain Spring AOP proxy: "
                + AopUtils.isAopProxy(jdk));

            billing.charge();
            jdk.run();
        }
    }
}
```

Chạy tại thư mục chứa `pom.xml` (yêu cầu Maven/JDK phù hợp):

```bash
mvn -q compile exec:java -Dexec.mainClass=example.Main
```

Các dòng **do chương trình** in ra, theo thứ tự:

```text
billing Spring AOP proxy: true
scope Spring AOP proxy: true
scope targets differ: true
config subclassed: true
config Spring AOP proxy: false
plain JDK dynamic proxy: true
plain Spring AOP proxy: false
aspect: before charge
billing: charge
jdk: delegated
```

### 8.8. Vì sao các kết quả khác nhau?

**`billing Spring AOP proxy: true`**: `BillingAspect` đã là bean qua `@Bean`, `@EnableAspectJAutoProxy` đã bật; advisor từ `@Before` khớp `BillingService.charge()`. Spring trả về AOP proxy cho bean `billingService`, và khi `billing.charge()` chạy qua proxy, advice in trước dòng nghiệp vụ.

**`scope Spring AOP proxy: true`**: `scopedCounter` được khai báo `prototype` với scoped proxy. Hạ tầng Spring AOP tạo wrapper để tra target đúng scope. `BillingAspect` chỉ nhắm `BillingService.charge()`, vì vậy **không có `@Before` nào của ví dụ áp dụng cho `ScopedCounter.id()`**. Hai lần gọi `counter.id()` lấy hai prototype targets khác nhau nên so sánh trả `true`. Một lời gọi method **khác** trên scoped proxy cũng có thể tra target mới; đừng lưu trạng thái dài hạn trong prototype target với kỳ vọng nó được dùng cho mọi lần gọi qua scoped proxy.

**`config subclassed: true` nhưng `config Spring AOP proxy: false`**: `@Configuration(proxyBeanMethods = true)` khiến class cấu hình được CGLIB-enhance để Spring điều phối method `@Bean`. Không có advisor AOP nào match configuration class trong ví dụ, và CGLIB enhancement bản thân không phải Spring AOP proxy.

**`plain JDK dynamic proxy: true` nhưng `plain Spring AOP proxy: false`**: `@Bean` `plainJdkProxy()` tạo một dynamic proxy bằng Java reflection và trả về cho Spring quản lý. JDK proxy **không tự có marker Spring AOP**, không có Spring advisors trong cấu hình này. Nếu một advisor khác về sau bọc tiếp bean ấy, quan sát của *tham chiếu mới* có thể thay đổi.

Đây là bốn nhóm khác nhau về **mục đích và nguồn tạo**, dù có thể cùng sử dụng proxy class hoặc cùng được Spring container trả về.

## 9. Bộ công cụ chẩn đoán phù hợp

### 9.1. `AopUtils`: phát hiện Spring AOP proxy

Các API phổ biến của `org.springframework.aop.support.AopUtils`:

```java
// Snippet đặt trong mã có các tham chiếu bean đã khởi tạo.
boolean springAop = AopUtils.isAopProxy(bean);
boolean jdkSpringAop = AopUtils.isJdkDynamicProxy(bean);
boolean cglibSpringAop = AopUtils.isCglibProxy(bean);
Class<?> targetClass = AopUtils.getTargetClass(bean);
```

- `isAopProxy` nhận diện **Spring AOP proxy**; kiểm tra thêm marker `SpringProxy`, không chỉ dựa vào class name.
- `isJdkDynamicProxy` và `isCglibProxy` phân biệt **hai chiến lược Spring AOP**.
- `getTargetClass` giúp tìm target type nếu thông tin có sẵn; không mặc định cho rằng nó tháo được mọi lớp proxy tùy biến hoặc chuỗi proxy lồng nhau.

**Không dùng `Proxy.isProxyClass(...)` một mình để chứng minh Spring AOP**: đó là phép kiểm tra **JDK dynamic proxy nói chung**, không cần có Spring. Tương tự, chuỗi `$$` hoặc `$$SpringCGLIB$$` trong class name có thể thuộc một dạng CGLIB enhancement khác.

### 9.2. `Advised`: nhìn advisor và target source (khi khả dụng)

Một Spring AOP proxy điển hình có thể triển khai `org.springframework.aop.framework.Advised`. Trong ví dụ mục 8, có thể thêm đoạn sau vào cuối `main` trước khi đóng context để quan sát:

```java
// Snippet, không phải chương trình độc lập:
if (billing instanceof org.springframework.aop.framework.Advised advised) {
    System.out.println("billing advisors: " + advised.getAdvisors().length);
    System.out.println("billing target source: "
        + advised.getTargetSource().getClass().getSimpleName());
}
if (counter instanceof org.springframework.aop.framework.Advised advised) {
    System.out.println("scope advisors: " + advised.getAdvisors().length);
    System.out.println("scope target source: "
        + advised.getTargetSource().getClass().getSimpleName());
}
```

Kết quả số lượng advisor hoặc tên class target source phụ thuộc phiên bản/cấu hình, vì vậy **không nên cố định stdout** của snippet chẩn đoán này. Nội dung quan trọng là:

- **Advisor** mô tả chuỗi advice/interceptor gắn với proxy được quan sát.
- **TargetSource** cung cấp target mà proxy sẽ gọi. Với scoped proxy, target source dùng để lấy bean từ scope; không đồng nhất với target source tĩnh thông thường.
- Nếu proxy được cấu hình `opaque`, không phải lúc nào cũng có thể cast `Advised`; hãy dùng `instanceof`.
- `Advised.getAdvisors()` cho biết các advisor lắp trên **lớp proxy quan sát được**, nhưng chưa tự chứng minh một advice đã chạy cho lời gọi cụ thể. Cần kiểm tra method match và hành vi.

Không gọi `advised.getTargetSource().getTarget()` tùy tiện khi debug scope/request/prototype: thao tác này có thể tạo/lấy target hoặc cần scope đang active, dẫn tới tác động không cần thiết. Ưu tiên quan sát type target source và thực thi method trong ngữ cảnh đã kiểm soát.

### 9.3. Xác nhận advice đến từ đâu

Một bean được `AopUtils.isAopProxy(...)` xác nhận có thể chứa:

- Advisor khai báo từ `@Aspect` do ứng dụng đăng ký.
- Infrastructure advisor tạo bởi Spring transaction, caching, async hoặc các feature khác.
- Interceptor phục vụ việc định tuyến tới target theo scope.
- Advisor/đường ủy quyền từ các framework extension.

Vì thế, khi hỏi “Advice của **aspect X** có được dùng không?”, phải kiểm tra **`@Aspect` bean tồn tại, advisor của aspect có áp dụng cho target, method có khớp và lời gọi có đi qua proxy hay không**. Không chỉ in một cờ boolean.

### 9.4. Proxy lồng nhau hoặc kết hợp

Trong dự án phức tạp, bean có thể đi qua nhiều lớp post-processing hoặc được bọc qua nhiều proxy. Ví dụ một object vốn do factory trả về dưới dạng proxy rồi tiếp tục được Spring AOP bọc. Khi đó:

```text
Client
  -> outer proxy / interceptor A
      -> inner proxy / interceptor B
          -> target
```

Không suy ra **chính xác** thứ tự các interceptor hoặc lớp proxy chỉ từ tên class; hãy quan sát cấu hình `Advised`, target source và trace invocation của phiên bản đang chạy. Spring cũng có cơ chế hợp nhất/điều phối auto-proxy settings nên hai annotation cấu hình không mặc nhiên tạo hai proxy tách biệt.

## 10. Cây quyết định khi gặp một bean “trông giống proxy”

```text
Object được lấy từ Spring ApplicationContext?
|
+-- Không: cần xác định ai tạo object (new, JDK Proxy, mock, factory ngoài Spring)
|
+-- Có: AopUtils.isAopProxy(bean)?
    |
    +-- false:
    |   +-- Proxy.isProxyClass(bean.getClass())?
    |   |      -> Có thể là JDK proxy không phải Spring AOP
    |   +-- getClass() khác class gốc / CGLIB enhancement?
    |          -> Kiểm tra @Configuration, @Lookup hoặc cơ chế tăng cường khác
    |
    +-- true:
        +-- isJdkDynamicProxy / isCglibProxy để biết hình thức
        +-- instanceof Advised? Kiểm tra advisor + target source nếu có
        +-- Có advisor @Aspect bạn cần không?
        |      -> Có: tiếp tục kiểm tra pointcut/method/invocation
        |      -> Không: xem transaction/cache/async/scope/feature khác
        +-- Gọi method qua bean proxy, xác minh hành vi thực tế
```

**Một proxy “do Spring tạo” không nhất thiết là “do Spring AOP auto-proxy từ `@Aspect` tạo”.** Đồng thời, scoped proxy là ví dụ phản chứng cho cách phân loại giản lược “proxy AOP = advice do developer viết”: scoped proxy có thể dùng đúng hạ tầng Spring AOP, nhưng mục đích là scope.

## 11. Lỗi chẩn đoán thường gặp

| Dấu hiệu | Kết luận vội dễ sai | Cách kiểm tra đáng tin |
| --- | --- | --- |
| `bean.getClass().getName()` chứa `$$SpringCGLIB$$` | Chắc chắn có `@Aspect` advice | Xác định `AopUtils.isAopProxy`, class `@Configuration`, advisor và hành vi |
| `Proxy.isProxyClass(bean.getClass()) == true` | Đây là Spring AOP proxy | Kiểm tra `AopUtils.isAopProxy`; JDK proxy thuần có thể không phải Spring AOP |
| `AopUtils.isAopProxy(bean) == true` | Mọi method chạy advice của tôi | Kiểm tra eligible advisor, pointcut, method runtime và proxy call |
| Bean có `@Transactional`, `@Cacheable` hoặc `@Async` | Bắt buộc phải có `@Aspect` class | Kiểm tra Spring annotation infrastructure và configuration/manager tương ứng |
| Scoped proxy cho `prototype` có AOP marker | Nó phải chạy aspect advice | Kiểm tra target source, scope và advisor; mục đích chính là chọn target |
| `@Configuration(proxyBeanMethods = true)` tạo subclass | Cấu hình class đang bị aspect chặn | Xác định enhancement `@Bean` so với advisor AOP |
| Bean có AOP proxy nhưng self-invocation không có log | Pointcut chắc chắn sai | Kiểm tra `this.method()`; lời gọi nội bộ đi thẳng trên target |
| Không thấy log của target trong lần gọi `@Cacheable` | Không có proxy | Có thể cache hit; kiểm tra cache metrics/interceptor |
| Cần đọc target của `request` scope khi không có request | Có thể gọi bất kỳ lúc nào để kiểm tra | Chỉ debug trong scope phù hợp; tránh ép lấy scoped target sớm |
| Class `final` với CGLIB proxy | Annotation AOP chắc chắn được áp dụng | CGLIB không subclass được `final` class; xem kiểu proxy và thông báo lỗi |

## 12. Bài tập vận dụng

**Tình huống A.** `@Configuration(proxyBeanMethods = true)` khiến bean cấu hình có class runtime khác class mã nguồn. `AopUtils.isAopProxy(config)` bằng `false`. Có mâu thuẫn không?

**Đáp án:** Không. CGLIB subclass enhancement cho ngữ nghĩa `@Bean` khác Spring AOP proxy. Đừng suy từ việc subclassed sang việc advisor `@Aspect` có mặt.

**Tình huống B.** Singleton inject một scoped proxy cho prototype bean. `AopUtils.isAopProxy(scoped)` là `true`, nhưng dự án không có `@Aspect` nào. Có hợp lệ không?

**Đáp án:** Có. Scoped proxy có thể dùng Spring AOP machinery để chuyển mỗi lần gọi tới target đúng scope; không cần advice `@Aspect` do người dùng khai báo.

**Tình huống C.** Service có `@Transactional`, không có custom `@Aspect`, nhưng `AopUtils.isAopProxy(service) == true`. Tại sao?

**Đáp án:** Transaction management ở proxy mode tự đăng ký infrastructure advisor/interceptor để điều phối transaction. Spring AOP không giới hạn ở `@Aspect` do người dùng viết.

**Tình huống D.** Một bean được `@Bean` trả về từ `java.lang.reflect.Proxy.newProxyInstance(...)`. `Proxy.isProxyClass(...)` trả `true` nhưng `AopUtils.isAopProxy(...)` trả `false`. Có phải Spring AOP bị lỗi?

**Đáp án:** Không. Đây là JDK dynamic proxy thông thường, không có marker Spring AOP; Spring quản lý bean không đồng nghĩa Spring tạo hoặc bổ sung AOP proxy cho bean ấy.

**Tình huống E.** AOP proxy của `PaymentService` có transaction advisor và `@Aspect` advisor. Gọi `pay()` từ ngoài chạy cả hai; `this.refund()` trong target không kích hoạt advice riêng của `refund()`. Giải thích?

**Đáp án:** Hai advisor có thể cùng gắn lên proxy, nhưng self-invocation qua `this` không quay lại proxy. Đây là giới hạn **proxy-based interception**, độc lập với việc advisor đến từ `@Transactional` hay `@Aspect`.

## 13. Nguồn Spring chính thức

- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB AOP proxy và giới hạn self-invocation.
- [Spring Framework — Bean Scopes / Scoped Proxies](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html): scoped proxy, proxy target theo request/session/prototype.
- [Spring Framework — Using the @Configuration Annotation](https://docs.spring.io/spring-framework/reference/core/beans/java/configuration-annotation.html): CGLIB enhancement và `proxyBeanMethods`.
- [Spring Framework — Transaction Implementation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html): transaction interceptor sử dụng Spring AOP.
- [Spring Framework — Cache Annotations](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html): cache annotation proxy mode.
- [Spring Framework — AopUtils API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/support/AopUtils.html): `isAopProxy`, `isJdkDynamicProxy`, `isCglibProxy`, `getTargetClass`.
- [Spring Framework — Advised API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/framework/Advised.html): các advisors/target source có thể quan sát trên Spring AOP proxy.


</details>

- [Quay lại đầu trang](#back-to-top)
