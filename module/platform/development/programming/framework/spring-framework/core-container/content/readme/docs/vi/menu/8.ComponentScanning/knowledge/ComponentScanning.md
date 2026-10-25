<a id="back-to-top"></a>

# Quét thành phần

## Menu
- [Đăng ký lớp bằng @Component và các stereotype chuyên biệt](#component-scanning-001)
- [Ý nghĩa @Service, @Repository và @Controller trong container](#component-scanning-002)
- [Phạm vi quét của @ComponentScan và vị trí gói](#component-scanning-003)
- [Khai báo basePackages và basePackageClasses](#component-scanning-004)
- [Bộ lọc include, exclude và useDefaultFilters](#component-scanning-005)
- [Quy tắc sinh tên và tùy biến BeanNameGenerator](#component-scanning-006)
- [Tạo stereotype ghép bằng meta-annotation và @AliasFor](#component-scanning-007)
- [Phân biệt component scanning với xử lý annotation trên bean đã có](#component-scanning-008)
- [Xung đột do quét trùng và giới hạn quét theo mô-đun](#component-scanning-009)

## <a id="component-scanning-001">Đăng ký lớp bằng @Component và các stereotype chuyên biệt</a>

<details>
<summary>Xem chi tiết</summary>

# Đăng ký lớp bằng `@Component` và các stereotype chuyên biệt trong Spring Core Container

## 1. Từ Java object đến Spring-managed bean

Trong Java thuần, tạo một đối tượng thường bắt đầu từ `new`:

```java
GreetingService service = new GreetingService(repository);
```

Người viết code phải quyết định khi nào khởi tạo, truyền dependency nào, giữ instance ở đâu và tự quản lý vòng đời của nó. Spring IoC Container chuyển trách nhiệm tạo và kết nối nhiều đối tượng sang container thông qua **bean definitions**. Một cách đăng ký bean là khai báo phương thức `@Bean`; cách khác là **đánh dấu lớp bằng stereotype annotation** và cho Spring quét classpath để phát hiện lớp đó.

Ví dụ:

```java
import org.springframework.stereotype.Component;

@Component
class TextFormatter {
    String format(String text) {
        return "[" + text + "]";
    }
}
```

Khi Spring quét đúng package chứa `TextFormatter`, lớp này có thể trở thành một bean với tên mặc định `textFormatter`. Sau đó bean khác có thể nhận `TextFormatter` qua constructor injection. **Annotation trên class không tự làm đối tượng xuất hiện trong container**: phải có một cơ chế đăng ký, ví dụ `@ComponentScan` hoặc component scanning do Spring Boot cấu hình.

Đặt tên đúng cho các vai trò rất quan trọng:

- **Class**: định nghĩa kiểu và hành vi trong mã nguồn Java.
- **Bean definition**: metadata mà container dùng để quản lý bean (class/factory, scope, tên, qualifiers, v.v.).
- **Spring bean**: đối tượng do Spring container tạo, cấu hình và quản lý theo bean definition.
- **Stereotype annotation**: nhãn trên lớp giúp nhận diện vai trò kiến trúc và, khi được scan, đăng ký bean.

Mục tiêu bài này là sử dụng đúng `@Component` và các chuyên biệt `@Service`, `@Repository`, `@Controller`, `@Configuration`; hiểu đăng ký qua scanning, tên bean, constructor injection, custom stereotype và những lỗi thường gặp.

## 2. `@Component` và các stereotype chuyên biệt

Ở góc nhìn **đăng ký bean**, `@Component` là stereotype tổng quát; các stereotype chuyên biệt phân loại vai trò của class nhưng đều được component scanner mặc định nhận diện qua meta-annotation `@Component`. Chọn annotation theo **trách nhiệm thực tế**, vì annotation không tự khởi động các cơ chế của layer.

| Annotation | Vai trò nên dùng | Điều kiện không được suy diễn |
| --- | --- | --- |
| `@Component` | Helper, adapter, formatter hoặc component kỹ thuật chung | Không phải mọi Java object đều cần làm bean |
| `@Service` | Business use case và điều phối nghiệp vụ | Không tự mở transaction, cache, retry hoặc AOP |
| `@Repository` | Repository/DAO, truy cập dữ liệu | Exception translation cần post-processor và translator phù hợp; `JdbcTemplate` có đường dịch lỗi riêng |
| `@Controller` | Presentation/web controller | Chưa có HTTP endpoint nếu thiếu web stack, handler mapping và route |
| `@Configuration` | Java configuration, thường chứa `@Bean` methods | Có semantics cấu hình riêng, gồm lựa chọn `proxyBeanMethods` |

Một bean utility có thể cần dependency injection hoặc vòng đời của Spring:

```java
import org.springframework.stereotype.Component;

@Component
class SlugGenerator {
    String slug(String text) {
        return text.trim().toLowerCase(java.util.Locale.ROOT)
                .replace(' ', '-');
    }
}
```

`SlugGenerator` được scanner phát hiện khi nằm trong đúng base package. Nếu chỉ cần hàm thuần, một utility Java không thuộc container vẫn có thể phù hợp.

`@Service` biểu đạt nghiệp vụ và hỗ trợ constructor injection như component khác:

```java
@Service
class CheckoutService {
    private final PaymentRepository payments;

    CheckoutService(PaymentRepository payments) {
        this.payments = payments;
    }

    String summary() { return payments.status(); }
}
```

Đây là snippet minh họa, giả định có interface/class `PaymentRepository` và bean tương ứng; với một constructor, Spring không bắt buộc `@Autowired` trên constructor. `@Service` không tự thiết lập giao dịch.

Tương tự, `@Repository` có thể đánh dấu bean cần được hạ tầng persistence exception translation xử lý, nhưng không tự đổi mọi ngoại lệ. `@Controller` có thể được scan vào một `ApplicationContext` không có HTTP server; chỉ khi Spring MVC/WebFlux phù hợp chạy mới có xử lý request. `@RestController` ghép `@Controller` và `@ResponseBody` trong Spring Web.

`@Configuration(proxyBeanMethods = false)` phù hợp khi các factory methods `@Bean` hoạt động độc lập. Nếu muốn gọi trực tiếp một `@Bean` method khác mà vẫn nhận semantics singleton của container, cần cân nhắc full configuration. Ví dụ chạy được ở mục 5 chứng minh năm stereotype, cách inject, tên bean và custom stereotype trong cùng một context.

## 3. Cơ chế quét và đăng ký component

Component scanning hoạt động theo lược đồ:

```text
Classpath/package được yêu cầu quét
               |
               v
Spring đọc metadata của các class ứng viên
               |
               v
Áp dụng stereotype mặc định và include/exclude filters
               |
               v
Tạo và đăng ký bean definitions cho ứng viên hợp lệ
               |
               v
Khi container cần/khởi tạo bean:
resolve dependency -> tạo instance -> inject -> lifecycle
```

Quan trọng: **quét class và tạo instance là hai giai đoạn khác nhau**. Scanner đăng ký bean definition; thời điểm instance được tạo còn phụ thuộc scope, lazy flag, dependency và quy trình khởi tạo context.

Mặc định, component scan phát hiện các class mang `@Component`, `@Service`, `@Repository`, `@Controller`, `@Configuration`, hoặc annotation tự định nghĩa được meta-annotated bằng `@Component`. Có thể tùy chỉnh bộ lọc ứng viên với `includeFilters` và `excludeFilters` nếu cần.

### 3.1. Bật scan bằng `@ComponentScan`

```java
@Configuration
@ComponentScan(basePackages = "com.example.orders")
class OrderConfiguration {
}
```

Đây là đoạn minh họa. Khi đăng ký `OrderConfiguration` với `AnnotationConfigApplicationContext`, Spring sẽ quét package `com.example.orders` và các subpackage của nó để tìm component candidates.

Biến thể an toàn hơn khi đổi tên package:

```java
@Configuration
@ComponentScan(basePackageClasses = OrderApplication.class)
class OrderConfiguration {
}
```

Ở đây `OrderApplication.class` chỉ đóng vai trò **type-safe marker** để xác định package; class marker bản thân không cần mang stereotype. Cần có `OrderApplication` trong package muốn quét.

Nếu để `@ComponentScan` không khai báo package, Spring mặc định sử dụng package của **configuration class khai báo annotation**, cùng các subpackage. Việc đặt class cấu hình trong package hẹp hoặc không đúng chỗ sẽ khiến component nằm ở package ngoài phạm vi bị bỏ sót.

### 3.2. Spring Boot và quét package mặc định

Trong dự án Spring Boot phổ biến:

```java
@SpringBootApplication
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
```

Đây là **đoạn minh họa ứng dụng Boot**, không phải dự án Spring Context thuần ở mục 5. `@SpringBootApplication` có tính năng tương đương sự kết hợp của `@SpringBootConfiguration`, `@EnableAutoConfiguration` và `@ComponentScan`. Mặc định, component scanning bắt đầu từ package đặt class application. Vì vậy hãy đặt application class ở **root package** chứa các package con cần scan, ví dụ:

```text
com.example.orders/
├── OrderApplication.java
├── api/OrderController.java
├── service/OrderService.java
└── persistence/OrderRepository.java
```

Một class ở package ngang cấp hoặc ở package cha **ngoài** vùng scan có thể không được phát hiện, dù đã gắn `@Service`.

### 3.3. Candidate không đồng nghĩa mọi class đều được tạo

- Một **interface** có gắn stereotype không có constructor cụ thể để tự tạo instance; nên đánh dấu stereotype trên **implementation** muốn đăng ký. Các annotation/repository mechanisms đặc thù, ví dụ Spring Data repositories, có quy trình tạo proxy riêng.
- Một class **abstract** thường không trở thành bean thông thường có thể khởi tạo trực tiếp chỉ nhờ `@Component`. Nếu cần đăng ký một bean, cung cấp concrete implementation hoặc factory phù hợp.
- **Nested non-static class** mang implicit outer-instance dependency; không nên trông đợi scanner đăng ký như một independent component. Với nested class được scan, dùng `static` nếu thực sự cần.
- **Bean có điều kiện** (ví dụ profile không active) có thể bị bỏ qua; kiểu class có trên classpath chưa đủ để khẳng định bean đã có trong container.
- Một class có stereotype nhưng thiếu dependency ở constructor vẫn có thể được đăng ký thành bean definition rồi thất bại khi khởi tạo.

## 4. Đặt tên bean và phạm vi sử dụng

### 4.1. Tên mặc định của component

Spring thường dùng `AnnotationBeanNameGenerator` cho bean phát hiện qua scan. Nếu không đặt tên tường minh, tên mặc định được suy ra bằng cách bỏ viết hoa ký tự đầu của **tên class ngắn** theo quy tắc JavaBeans:

| Khai báo | Tên bean thông thường |
| --- | --- |
| `@Component class TextFormatter` | `textFormatter` |
| `@Service class CheckoutService` | `checkoutService` |
| `@Repository class OrderRepositoryImpl` | `orderRepositoryImpl` |
| `@Controller class OrderController` | `orderController` |

Có ngoại lệ với tên class bắt đầu bằng nhiều ký tự viết hoa (ví dụ `URLService`), nên đừng coi quy tắc mặc định đơn giản là luôn lowercase ký tự đầu tiên.

Có thể đặt tên rõ ràng:

```java
@Service("checkoutWorkflow")
class CheckoutService {
    // ...
}
```

Khi đó bean name mong đợi là `checkoutWorkflow`, không phải `checkoutService`. Tên bean cần duy nhất trong một container; hai component dùng cùng tên có thể gây lỗi đăng ký xung đột. Đừng giải quyết xung đột bằng cách âm thầm bật bean definition overriding nếu chưa hiểu tại sao hai bean có cùng tên.

### 4.2. Bean name, bean type và qualifier

Tên bean phục vụ nhận diện trong container; type phục vụ autowiring theo kiểu. Nếu constructor cần một interface và có hai bean implementation phù hợp, `@Service`/`@Repository` không tự chọn đúng một implementation. Có thể dùng `@Qualifier`, `@Primary` hoặc thiết kế lại dependency.

```java
// Đoạn minh họa: hai implementation đều được đăng ký.
@Service("localSender")
class LocalSender implements Sender { }

@Service("remoteSender")
class RemoteSender implements Sender { }

// Tại constructor của một bean khác:
NotificationService(@Qualifier("remoteSender") Sender sender) {
    this.sender = sender;
}
```

Đây không phải đoạn Java độc lập: cần định nghĩa `Sender`, `NotificationService` và imports. `@Qualifier("remoteSender")` hướng Spring đến ứng viên có qualifier hoặc bean name phù hợp; cụm này **không liên quan đến việc hai class có cùng stereotype**.

### 4.3. Scope và lifecycle

Component scan không cố định scope là prototype. Mặc định, một Spring bean được đăng ký bằng `@Component`, `@Service`, `@Repository` hoặc `@Controller` có scope **singleton trong phạm vi container**. Điều đó nghĩa là Spring thường cung cấp cùng một instance cho các lần lookup qua container:

```java
@Service
@Scope("prototype")
class ReportTask {
    // Mỗi lần container resolve prototype bean sẽ tạo instance mới.
}
```

Đây là đoạn minh họa; cần import `org.springframework.context.annotation.Scope`. Dùng scope phù hợp với lifecycle chứ không chọn prototype chỉ vì một method cần chạy nhiều lần. Với singleton, Spring có thể tạo instance sớm lúc refresh context; `@Lazy` thay đổi chính sách eager/lazy khi thích hợp, nhưng dependency khác cần bean đó vẫn có thể kích hoạt khởi tạo.

Bean được Spring quản lý có thể nhận dependency injection, các post-processor, initialization/destruction callbacks và proxy theo cấu hình. **Một object tạo bằng `new` không tự trở thành cùng bean mà scanner đăng ký**, dù class của nó có `@Component`.

## 5. Dự án Java 17+ chạy được: đăng ký năm loại stereotype

Ví dụ dưới đây dùng **Spring Framework 6.2.18**, không cần Spring Boot hoặc servlet container. Mọi file Java đều nằm trong cùng một package. Để giữ ví dụ dễ sao chép, các class không `public` được đặt chung với main class trong một file; compiler Java vẫn tạo từng `.class` độc lập và component scanner phát hiện các concrete top-level class phù hợp.

### 5.1. Cấu trúc tệp

```text
component-stereotypes-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/components/
    └── ComponentStereotypesDemo.java
```

`settings.gradle`:

```groovy
rootProject.name = 'component-stereotypes-demo'
```

`build.gradle`:

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.18'
}

application {
    mainClass = 'demo.components.ComponentStereotypesDemo'
}
```

`spring-context` kéo theo các module Spring Beans/Core cần thiết. Ví dụ không dùng servlet, HTTP hoặc database; `@Controller` và `@Repository` ở đây chỉ thể hiện vai trò và hành vi quét.

### 5.2. Mã Java hoàn chỉnh

`src/main/java/demo/components/ComponentStereotypesDemo.java`:

```java
package demo.components;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

public class ComponentStereotypesDemo {

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(DemoConfig.class)) {
            System.out.println("component = " +
                    context.containsBean("textDecorator"));
            System.out.println("service = " +
                    context.containsBean("greetingService"));
            System.out.println("repository = " +
                    context.containsBean("inMemoryGreetingRepository"));
            System.out.println("controller = " +
                    context.containsBean("greetingController"));
            System.out.println("configuration = " +
                    context.containsBean("demoConfig"));
            System.out.println("custom = " +
                    context.containsBean("featureInfo"));

            GreetingController controller =
                    context.getBean(GreetingController.class);
            System.out.println("greeting = " + controller.render());

            GreetingService first = context.getBean(GreetingService.class);
            GreetingService second = context.getBean(GreetingService.class);
            System.out.println("sameService = " + (first == second));
        }
    }
}

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = ComponentStereotypesDemo.class)
class DemoConfig {
}

interface GreetingRepository {
    String load();
}

@Repository
class InMemoryGreetingRepository implements GreetingRepository {

    @Override
    public String load() {
        return "Xin chào từ repository";
    }
}

@Service
class GreetingService {

    private final GreetingRepository repository;

    GreetingService(GreetingRepository repository) {
        this.repository = repository;
    }

    String greeting() {
        return repository.load() + " qua service";
    }
}

@Component
class TextDecorator {

    String decorate(String input) {
        return "[" + input + "]";
    }
}

@Controller
class GreetingController {

    private final GreetingService service;
    private final TextDecorator decorator;

    GreetingController(GreetingService service, TextDecorator decorator) {
        this.service = service;
        this.decorator = decorator;
    }

    String render() {
        return decorator.decorate(service.greeting());
    }
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
@interface Feature {
    @AliasFor(annotation = Component.class, attribute = "value")
    String value() default "";
}

@Feature("featureInfo")
class FeatureInfo {
}
```

Trong một file Java, chỉ `ComponentStereotypesDemo` là top-level `public`; các class và annotation còn lại có package-private visibility, hợp lệ vì nằm trong cùng package và được trình biên dịch tạo thành các `.class` độc lập. Constructor của những class được scanner tạo bean ở đây có thể là package-private; Spring có thể truy cập phù hợp trong ứng dụng classpath thông thường. Khi dùng Java Platform Module System với `module-info.java`, cần xét riêng cấu hình `opens` cho reflection; ví dụ này không sử dụng module descriptor.

### 5.3. Lệnh chạy và output

Dùng JDK 17 trở lên và bản Gradle tương thích; tại thư mục dự án:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper, sử dụng `./gradlew run --quiet` trên Linux/macOS hoặc `.\gradlew.bat run --quiet` trên Windows PowerShell. Các dòng do chương trình in:

```text
component = true
service = true
repository = true
controller = true
configuration = true
custom = true
greeting = [Xin chào từ repository qua service]
sameService = true
```

`System.out.println` chỉ in những dòng trên; log của Spring hoặc Gradle, nếu môi trường cấu hình thêm logging, không phải output nghiệp vụ của chương trình.

### 5.4. Đọc cơ chế từng bước

1. `new AnnotationConfigApplicationContext(DemoConfig.class)` đăng ký configuration class và refresh context.
2. `@ComponentScan(basePackageClasses = ComponentStereotypesDemo.class)` yêu cầu scan package `demo.components`.
3. Spring phát hiện `InMemoryGreetingRepository`, `GreetingService`, `TextDecorator`, `GreetingController` và `FeatureInfo` nhờ các stereotype, đồng thời `DemoConfig` được đăng ký là configuration bean.
4. Container chọn `InMemoryGreetingRepository` để thỏa interface `GreetingRepository` vì ở đây chỉ có một implementation được đăng ký.
5. Container truyền `GreetingRepository` vào `GreetingService`, sau đó `GreetingService` và `TextDecorator` vào `GreetingController` qua constructor. Không cần `@Autowired` vì mỗi class chỉ có một constructor.
6. `@Feature("featureInfo")` được scan vì `@Feature` mang meta-annotation `@Component`. `@AliasFor` chuyển giá trị `value` của `@Feature` sang logical component name, nên bean có tên `featureInfo`.
7. `controller.render()` trả một chuỗi định dạng. Đây là lời gọi Java bình thường đến bean `@Controller`; **không có HTTP request**.
8. `first == second` trả `true` vì `GreetingService` có scope singleton mặc định trong cùng context.

Mỗi dòng output là một kiểm tra hẹp của behavior đã khai báo. Nếu xóa `@ComponentScan` nhưng vẫn đăng ký `DemoConfig`, scanner không còn được cấu hình để tìm các stereotype trong package, các bean như `GreetingService` không được tạo tự động.

## 6. Custom stereotype: mở rộng `@Component` đúng cách

Các dự án lớn thường cần stereotype riêng thể hiện mục đích kiến trúc như `@DomainService`, `@UseCase`, `@InboundAdapter`. Spring hỗ trợ điều này bằng cách **meta-annotate annotation tự định nghĩa với `@Component`**:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface DomainService {
    @AliasFor(annotation = Component.class, attribute = "value")
    String value() default "";
}
```

Đoạn này minh họa **nội dung file `DomainService.java`**, cần imports như ở mục 5. Annotation `@DomainService` dùng được trên class:

```java
@DomainService("orderUseCase")
public class OrderUseCase {
}
```

Đoạn thứ hai minh họa **file `OrderUseCase.java`** trong package được scan. Bean được đặt tên `orderUseCase` thông qua alias. Các quy tắc:

- `@Target(ElementType.TYPE)` cho phép gắn annotation trên class.
- `@Retention(RetentionPolicy.RUNTIME)` bảo đảm metadata annotation có thể được đọc lúc runtime.
- `@Component` trên annotation tự định nghĩa cho scanner biết đây là stereotype.
- `@AliasFor(annotation = Component.class, attribute = "value")` khai báo **alias tường minh** cho tên bean. Từ Spring 6.1, cách custom stereotype chỉ khai báo `String value()` dựa vào convention cũ đã bị deprecate; dùng `@AliasFor` giúp semantics rõ ràng và bền vững hơn.
- Nếu không cần đặt tên từ custom annotation, có thể chỉ định nghĩa stereotype marker đơn giản, không khai báo thuộc tính `value`; scanner vẫn suy ra tên mặc định từ class.

**Không nên custom quá nhiều stereotype** nếu chúng không mang ý nghĩa rõ ràng. Những nhãn chỉ lặp lại `@Service` bằng tên khác mà không có quy ước nào bổ sung có thể khiến code khó hiểu.

## 7. `@Component` khác `@Bean` như thế nào?

Cả hai đều có thể đăng ký bean nhưng **nguồn định nghĩa** khác nhau:

| Tiêu chí | Stereotype + component scanning | `@Bean` trong configuration |
| --- | --- | --- |
| Nơi khai báo | Trên class | Trên method của configuration class hoặc lớp cấu hình phù hợp |
| Đối tượng thường dùng | Class ứng dụng do team kiểm soát | Class thư viện, đối tượng cần factory logic, object tùy biến |
| Cơ chế đăng ký | Scanner tìm metadata trong package | Configuration processor đọc factory method |
| Bean name mặc định | Từ tên class, ví dụ `paymentService` | Từ tên method, ví dụ `paymentClient` |
| Injection | Có, do Spring container quản lý | Có, do Spring container quản lý |
| Scope mặc định | Singleton | Singleton |
| Có thể kết hợp? | Có | Có |

Ví dụ với class bên thứ ba không thể sửa source:

```java
@Configuration(proxyBeanMethods = false)
class ExternalConfig {
    @Bean
    ExternalClient externalClient() {
        return new ExternalClient("endpoint");
    }
}
```

Đây là đoạn minh họa, thiếu định nghĩa `ExternalClient` và imports. `@Bean` dễ sử dụng hơn việc thêm `@Component` vào class thư viện mà bạn không kiểm soát.

Đừng vừa scan một class bằng `@Component` **vừa khai báo một `@Bean` tạo cùng instance/class** mà không có ý định rõ ràng; bạn có thể tạo ra hai bean cùng kiểu và khiến injection đơn bị mơ hồ. Một dự án có thể kết hợp hai cách đăng ký, nhưng mỗi bean cần vai trò, scope và tên có chủ đích.

## 8. Phạm vi scan, exclusions và hiệu năng khởi động

Component scanning xét các class có trên runtime classpath bên trong base package và các subpackage. Class mang `@Service` ở một package ngang cấp hoặc nằm ngoài root sẽ không được tìm thấy bởi lượt scan ấy. Với Spring Boot, `@SpringBootApplication` thường quét từ package đặt application class xuống các nhánh con.

Dùng `@ComponentScan(basePackages = "...")` hoặc `basePackageClasses = Marker.class` để xác định root, giữ phạm vi **đủ nhưng không quá rộng**. Bộ lọc `includeFilters` bổ sung tiêu chí nhận diện; `excludeFilters` loại ứng viên của một lượt scan; `useDefaultFilters=false` tắt quy tắc nhận diện stereotype mặc định. Một bean còn có thể được tạo qua `@Bean`, `@Import` hoặc config khác dù không được scan. Khi mở rộng vùng quét, xác minh classpath, ứng viên, cấu hình có điều kiện và xung đột tên trước khi đưa vào sản phẩm.

## 9. Những lỗi thường gặp

Các lỗi khi dùng stereotype thường nằm ở các pha **đăng ký**, **khởi tạo bean**, hoặc **hạ tầng tích hợp** khác nhau:

| Hiện tượng | Kiểm tra trước |
| --- | --- |
| Bean `@Service` không có dù class tồn tại | Runtime classpath, package root, include/exclude, `useDefaultFilters`, profile/condition |
| `NoUniqueBeanDefinitionException` | Nhiều bean cùng type, cần lựa chọn rõ qua `@Qualifier`, `@Primary` hoặc collection |
| Hai component khác package gây xung đột | Bean name mặc định giống nhau, explicit name hoặc naming generator |
| `@Repository` không dịch ngoại lệ | Có `PersistenceExceptionTranslationPostProcessor`, translator phù hợp và lời gọi qua proxy hay không |
| Controller có trong context nhưng HTTP trả 404 | MVC/web infrastructure, `DispatcherServlet`, mapping và HTTP path/method |
| `@Service` nhưng method không rollback | `@Transactional`, transaction manager, proxy/call path và self-invocation |
| Đối tượng tạo bằng `new` thiếu `@Autowired` | Object chưa đi qua bean registration và lifecycle của Spring |
| Bean definition có nhưng ứng dụng không khởi động | Constructor/dependency, circular dependency, lỗi factory hoặc cấu hình điều kiện |

**Debug theo thứ tự:** (1) xác minh package và classpath; (2) class configuration có được đăng ký không; (3) kiểm tra annotation/filter/condition; (4) đọc tên và kiểu bean bằng `containsBean`/`getBeanNamesForType`; (5) tìm root cause khởi tạo; (6) kiểm tra web/transaction/persistence hạ tầng phù hợp. Đừng mở scan toàn classpath chỉ để che lỗi dependency hoặc naming.

## 10. Bài tập tự thực hành

### Bài tập 1 — Đổi tên component

Đổi:

```java
@Component
class TextDecorator { /* ... */ }
```

thành:

```java
@Component("messageDecorator")
class TextDecorator { /* ... */ }
```

Trong đoạn kiểm tra thuộc mục 5, thay `containsBean("textDecorator")` thành `containsBean("messageDecorator")`. Hành vi dependency injection theo **type** vẫn có thể hoạt động nếu chỉ có một bean `TextDecorator`; tuy nhiên tên bean mặc định `textDecorator` không còn là tên đăng ký.

### Bài tập 2 — Thay stereotype theo vai trò

Nếu chuyển `GreetingService` từ `@Service` thành `@Component`, chương trình mục 5 còn hoạt động không? **Có**, miễn component vẫn được scan và không có cấu hình bổ sung dựa trên annotation `@Service`. Nhưng code không còn diễn đạt rõ layer nghiệp vụ, và tooling/aspect/filter tùy chỉnh nhắm vào `@Service` có thể thay đổi hành vi.

Nếu chuyển `InMemoryGreetingRepository` từ `@Repository` thành `@Component`, bean vẫn có thể được scan, nhưng metadata repository phục vụ exception translation và quy ước kiến trúc không còn nữa. Đây là lý do không nên thay mọi stereotype bằng `@Component` một cách máy móc.

### Bài tập 3 — Đổi package không quét

Chuyển `TextDecorator` sang package `demo.util` nhưng giữ `@ComponentScan(basePackageClasses = ComponentStereotypesDemo.class)` trong package `demo.components`. Dự đoán gì xảy ra? Spring sẽ không scan `demo.util`; khi tạo `GreetingController` sẽ thiếu dependency `TextDecorator`, trừ khi có cấu hình đăng ký bean khác. Hướng sửa là mở rộng đúng phạm vi scan hoặc đưa utility về package được scan.

### Bài tập 4 — Thêm repository thứ hai

Khai báo thêm `@Repository class RemoteGreetingRepository implements GreetingRepository`. Nếu cả hai bean đều hợp lệ và không có primary hoặc qualifier làm rõ lựa chọn, constructor `GreetingService(GreetingRepository repository)` có thể thất bại với `NoUniqueBeanDefinitionException` (thường xuất hiện trong chuỗi lỗi tạo context). Hướng sửa là gắn `@Qualifier` đúng vai trò hoặc lựa chọn primary có chủ đích.

### Bài tập 5 — Custom stereotype

Tạo annotation `@DomainService` theo ví dụ mục 6 rồi đánh dấu `@DomainService("invoiceUseCase")` lên một concrete class trong package được scan. Hãy xác nhận bean name bằng `containsBean("invoiceUseCase")` và giải thích vì sao `@AliasFor` quan trọng trên Spring Framework 6.1+.

### Bài tập 6 — Vòng đời

Gọi hai lần `context.getBean(GreetingService.class)` trên cùng context, rồi so sánh bằng `==`. Vì sao `true`? Vì `GreetingService` là singleton mặc định. Nếu bean được khai báo `@Scope("prototype")` và không bị giữ trong singleton khác, mỗi lời gọi resolve prototype có thể tạo instance khác nhau. Đừng suy luận rằng chỉ đổi scope của một dependency là mọi client đang giữ reference sẽ tự cập nhật nó.

## 11. Checklist trước khi thêm stereotype

- Lớp có **cần** được Spring quản lý không? Nếu có, trách nhiệm của nó thuộc `@Component`, `@Service`, `@Repository` hay `@Controller`?
- Package đã nằm trong đúng phạm vi `@ComponentScan` hoặc default scan của Spring Boot chưa?
- Có nhiều implementation của interface được inject không? Nếu có, selection policy đã rõ chưa?
- Bean name có ý nghĩa, có nguy cơ đụng tên với bean khác không?
- Có cần `@Bean` factory method vì không thể sửa class hoặc cần logic cấu hình không?
- Có phụ thuộc vào cơ chế web, transaction, exception translation, AOP hay scope mà stereotype **một mình không kích hoạt** không?
- Nếu dùng custom stereotype, đã meta-annotate `@Component` và khai báo alias tên bean bằng `@AliasFor` khi cần chưa?

## 12. Ghi nhớ

`@Component` và các stereotype chuyên biệt giúp Spring **phát hiện class và đăng ký bean definition qua component scanning**. `@Service` biểu đạt nghiệp vụ, `@Repository` biểu đạt truy cập dữ liệu, `@Controller` biểu đạt tầng web, còn `@Configuration` là component với semantics cấu hình. Custom stereotype có thể được tạo bằng meta-annotation `@Component`; khi muốn đặt bean name qua thuộc tính riêng, dùng `@AliasFor` tường minh. Dù sử dụng stereotype nào, bean chỉ có mặt khi **được scan hoặc được đăng ký bằng cơ chế phù hợp**; các annotation vai trò không tự bật web server, transaction hay exception translation.

### Tài liệu chính thức

- [Spring Framework 6.2 — Classpath Scanning and Managed Components](https://docs.spring.io/spring-framework/reference/6.2/core/beans/classpath-scanning.html).
- [Spring Framework — Using `@Autowired`](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html).
- [Spring Framework — Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java.html).
- [Spring Framework — DAO Support and `@Repository`](https://docs.spring.io/spring-framework/reference/data-access/dao.html).
- [Spring Framework — Exception Translation](https://docs.spring.io/spring-framework/reference/data-access/orm/general.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="component-scanning-002">Ý nghĩa @Service, @Repository và @Controller trong container</a>

<details>
<summary>Xem chi tiết</summary>

# Ý nghĩa @Service, @Repository và @Controller trong Spring Core Container

## 1. Mục tiêu và bối cảnh

Khi viết ứng dụng Spring Framework, chúng ta thường chia code thành các lớp xử lý HTTP, nghiệp vụ và truy cập dữ liệu. Ba annotation `@Controller`, `@Service`, `@Repository` giúp mô tả những vai trò ấy trong mã nguồn và cho phép Spring phát hiện lớp khi **quét thành phần (component scanning)**.

Sau bài học, bạn có thể:

- Giải thích vì sao cả ba annotation đều tạo **ứng viên bean** thông qua cơ chế `@Component`.
- Phân biệt **vai trò kiến trúc** và **hành vi runtime** của từng stereotype, tránh suy diễn rằng annotation tự tạo endpoint, transaction hoặc kết nối cơ sở dữ liệu.
- Mô tả quá trình `@ComponentScan` tìm lớp, đăng ký bean definition, khởi tạo bean và thực hiện dependency injection.
- Viết một ví dụ Spring Framework thuần Java có `@Repository`, `@Service`, `@Controller` được tìm thấy và tiêm đúng vào nhau.
- Phân tích điều kiện **dịch ngoại lệ persistence** của `@Repository` và điều kiện **định tuyến HTTP** của `@Controller`.
- Sử dụng tên bean, qualifier, scope và phạm vi scan đúng chỗ; xử lý các lỗi cấu hình thực tế.

Bài học tập trung vào **Spring Core Container và component scan**. Những ví dụ Spring MVC, dữ liệu và transaction được sử dụng để giải thích vai trò của stereotype, không thay thế cho khóa học chuyên sâu về web hoặc data access.

## 2. Stereotype là gì?

**Stereotype** là annotation phân loại vai trò một lớp trong ứng dụng. Spring cung cấp stereotype tổng quát `@Component` và các stereotype chuyên biệt:

- `@Controller`: lớp chịu trách nhiệm tại ranh giới tiếp nhận yêu cầu/presentation, thường là web controller.
- `@Service`: lớp thể hiện một dịch vụ xử lý nghiệp vụ hoặc điều phối use case.
- `@Repository`: lớp chịu trách nhiệm với thao tác lưu trữ, truy xuất và tìm kiếm dữ liệu (repository/DAO).

Điểm chung về khả năng được Spring quản lý là **cả ba đều được meta-annotate bằng `@Component`**.

```java
// Sơ đồ ý tưởng quan hệ annotation; không phải định nghĩa đầy đủ của Spring.
@Component
public @interface Service {}

@Component
public @interface Repository {}

@Component
public @interface Controller {}
```

Cú pháp ở trên chỉ minh họa ý tưởng về meta-annotation; định nghĩa thật còn có `@Target`, `@Retention`, `@Documented` và các thuộc tính khác. **Không copy đoạn phác họa này để thay thế annotation thật.**

Khi bật component scanning cho package phù hợp, Spring nhận diện các lớp đánh dấu `@Component` hoặc stereotype meta-annotated bởi `@Component`, rồi đăng ký bean definition cho chúng.

```text
Classpath có các class đã biên dịch
             |
             v
       @ComponentScan
             |
             +--> @Controller --> bean definition --> controller bean
             |
             +--> @Service ----> bean definition --> service bean
             |
             +--> @Repository -> bean definition --> repository bean
             |
             v
     IoC Container quản lý lifecycle, dependency, scope
```

Một annotation đánh dấu lớp làm ứng viên; việc bean có thực sự xuất hiện hay không còn tùy **phạm vi scan**, các bộ lọc, conditional configuration, xung đột tên và sự thành công của quá trình tạo bean. Không phải mọi class trên classpath đều tự động trở thành bean.

## 3. So sánh nhanh ba annotation

| Khía cạnh | `@Controller` | `@Service` | `@Repository` |
| --- | --- | --- | --- |
| Ý nghĩa chính | Presentation / đầu vào web | Nghiệp vụ, use case, orchestration | Truy cập và lưu trữ dữ liệu |
| Đều là specialization của | `@Component` | `@Component` | `@Component` |
| Được tìm qua component scan | Có | Có | Có |
| Có thể dùng constructor injection | Có | Có | Có |
| Scope mặc định | Singleton | Singleton | Singleton |
| Tự mở HTTP endpoint chỉ vì có annotation? | **Không** | Không | Không |
| Tự bắt đầu transaction? | Không | **Không** | Không |
| Dấu hiệu được hỗ trợ để dịch persistence exception | Không phải mục đích chính | Không phải mục đích chính | **Có, khi cấu hình hạ tầng dịch ngoại lệ tương ứng** |
| Thường được lớp nào gọi? | Dispatcher/handler hoặc caller khác | Controller, service khác, listener | Service hoặc thành phần nghiệp vụ khác |
| Lợi ích ngoài tạo bean | Nhận diện web component, tích hợp web infrastructure | Diễn đạt ý định kiến trúc, điểm cắt AOP | Diễn đạt lớp persistence, điểm cắt AOP, hỗ trợ exception translation |

**Kết luận quan trọng:** ba annotation giúp Spring **nhận diện, đăng ký và phân loại bean**, nhưng không hình thành ba loại IoC Container khác nhau. Cùng một container vẫn quản lý chúng theo cơ chế bean definition, dependency injection và scope.

## 4. Cơ chế component scanning phía sau

Ba stereotype đều đi qua **cùng một** đường quét class của Spring:

```java
@Configuration
@ComponentScan(basePackages = "com.example.catalog")
class CatalogConfig { }
```

Sau khi `CatalogConfig` được đăng ký vào context, Spring xét các class trong `com.example.catalog` và các package con. Bộ lọc mặc định nhận `@Controller`, `@Service` và `@Repository` vì chúng có meta-annotation `@Component`. Với ứng viên hợp lệ, scanner tạo bean definition; container sau đó tạo instance và resolve dependency theo scope/lifecycle. Một class mang stereotype nhưng nằm ngoài root, bị condition/filter loại hoặc thiếu dependency vẫn có thể vắng mặt/thất bại.

`basePackageClasses = CatalogPackage.class` cũng cấu hình vùng quét, thông qua **package chứa class marker** thay vì chỉ quét marker. Phần đáng chú ý ở ba stereotype không phải các thuật toán quét khác nhau, mà là **trách nhiệm từng layer và hạ tầng chuyên biệt có thể diễn giải metadata ấy**.

## 5. @Service: đánh dấu lớp nghiệp vụ

### 5.1. Ý nghĩa

`@Service` là một specialization của `@Component`, thích hợp để thể hiện **service layer**: các hành động nghiệp vụ, use case, quy tắc domain hoặc việc điều phối nhiều cộng tác viên.

Một service có thể:

- Kiểm tra và áp dụng quy tắc nghiệp vụ.
- Điều phối nhiều repository, domain service hoặc cổng tích hợp.
- Tạo kết quả use case phục vụ controller, listener hoặc scheduled job.
- Là điểm đặt các chính sách cross-cutting thông qua annotation/aspect khác nếu được cấu hình.

Ví dụ cấu trúc:

```java
import org.springframework.stereotype.Service;

@Service
public class CheckoutService {
    private final StockRepository stock;
    private final PaymentGateway payments;

    public CheckoutService(StockRepository stock, PaymentGateway payments) {
        this.stock = stock;
        this.payments = payments;
    }

    public Receipt checkout(Cart cart) {
        stock.reserve(cart);
        return payments.charge(cart);
    }
}
```

`StockRepository`, `PaymentGateway`, `Receipt`, `Cart` là các kiểu nghiệp vụ minh họa; đoạn này không phải chương trình độc lập. `@Service` làm cho lớp trở thành ứng viên bean khi được scan; những phụ thuộc vẫn cần bean tương ứng để inject.

### 5.2. @Service không tự tạo transaction, retry hoặc caching

Những kỳ vọng dưới đây **không đúng chỉ từ `@Service`**:

- Mỗi lời gọi method tự nằm trong transaction.
- Spring tự retry khi service ném lỗi.
- Giá trị trả về tự được cache.
- Method tự chạy bất đồng bộ.
- Mọi lần gọi method được AOP chặn để ghi log.

Các khả năng đó cần hạ tầng/annotation/cấu hình riêng, ví dụ `@Transactional` kết hợp transaction management, `@Cacheable` với caching infrastructure hoặc `@Async` với async execution. Việc đặt các annotation này trên service phải đáp ứng yêu cầu của cơ chế proxy, method invocation và hạ tầng tương ứng.

Ví dụ **không phải chương trình độc lập**:

```java
@Service
public class TransferService {
    @org.springframework.transaction.annotation.Transactional
    public void transfer(long fromId, long toId, long amount) {
        // Cần DAO, transaction manager và logic nghiệp vụ thực tế.
    }
}
```

`@Transactional` biểu đạt giao dịch nếu hạ tầng Spring transaction được kích hoạt đúng, không phải đặc quyền tự nhiên của `@Service`. Thậm chí không có `@Service`, một bean `@Component` hoặc `@Bean` vẫn có thể tham gia transaction khi được cấu hình tương ứng.

### 5.3. Vì sao không dùng @Component cho tất cả?

Cơ chế quét và dependency injection có thể vẫn hoạt động, nhưng `@Service` làm trách nhiệm code dễ hiểu, thuận tiện cho phân tích kiến trúc, tooling và pointcut AOP nhắm đến tầng service. Spring cũng có thể hỗ trợ thêm semantics dành cho các stereotype trong các phiên bản tương lai.

Dùng `@Service` khi class thực sự chịu trách nhiệm nghiệp vụ; đừng gắn lên data adapter hoặc request controller chỉ vì muốn Spring inject nó.

## 6. @Repository: đánh dấu lớp persistence / truy cập dữ liệu

### 6.1. Ý nghĩa

`@Repository` dành cho một thành phần phụ trách **lưu trữ, truy vấn, tìm kiếm và truy xuất dữ liệu**. Nhiều đội dùng nó cho DAO; trong Domain-Driven Design, repository thường biểu diễn một tập hợp đối tượng domain và che chi tiết persistence khỏi tầng nghiệp vụ.

```java
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCustomerRepository {
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public JdbcCustomerRepository(
            org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String findName(long id) {
        return jdbc.queryForObject(
            "select name from customer where id = ?",
            String.class,
            id
        );
    }
}
```

Đây là ví dụ DAO minh họa cần dependency `spring-jdbc`, `JdbcTemplate` đã cấu hình, `DataSource` và bảng `customer` trong DB thực tế; không phải chương trình chạy độc lập. `@Repository` cho phép component scan phát hiện class và cho biết nó thuộc lớp truy cập dữ liệu.

### 6.2. Exception translation: điểm khác đáng nhớ

Một vai trò đặc biệt của `@Repository` là **đánh dấu repository đủ điều kiện** cho cơ chế chuyển đổi persistence exception sang hệ ngoại lệ truy cập dữ liệu thống nhất của Spring, tiêu biểu là `org.springframework.dao.DataAccessException` và các subclass.

Ví dụ vendor persistence API có thể phát sinh ngoại lệ phụ thuộc công nghệ. Hệ thống dịch ngoại lệ cho phép tầng gọi phía trên xử lý theo các nhóm lỗi trung lập hơn như dữ liệu không tìm thấy, vi phạm toàn vẹn, khóa tranh chấp, v.v. Cách ánh xạ cụ thể do translator tương ứng quyết định.

Nhưng cần nhớ rõ **điều kiện**:

1. Bean repository có stereotype phù hợp, như `@Repository`.
2. Hạ tầng dịch ngoại lệ, tiêu biểu `PersistenceExceptionTranslationPostProcessor`, được đăng ký.
3. Có `PersistenceExceptionTranslator` phù hợp với công nghệ persistence để chuyển loại exception cần xử lý.
4. Lời gọi thực sự đi qua proxy/interceptor (khi cơ chế triển khai dùng proxy) và loại exception đó được translator hỗ trợ.

```text
Caller
  |
  v
Repository proxy / interceptor
  |
  v
@Repository target -> persistence API -> vendor exception
  |
  v
PersistenceExceptionTranslator phù hợp
  |
  +--> DataAccessException (nếu dịch được)
  |
  +--> exception gốc (nếu không có bản dịch phù hợp)
```

Ví dụ đăng ký cơ chế post-processing trong cấu hình phù hợp:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;

@Configuration
class DataAccessConfig {
    @Bean
    static PersistenceExceptionTranslationPostProcessor
            persistenceExceptionTranslationPostProcessor() {
        return new PersistenceExceptionTranslationPostProcessor();
    }
}
```

Ví dụ này cần `spring-tx` trên classpath và một translator thích hợp từ stack data-access; riêng class cấu hình **không bảo đảm dịch được mọi exception**. Việc khai báo `static @Bean` cho một `BeanPostProcessor` giúp đăng ký sớm trong vòng đời container. Nếu framework/data starter đã cấu hình translation, không đăng ký dư thừa mà không kiểm tra.

**Phân biệt thêm:**

- `JdbcTemplate` đã có cơ chế chuyển đổi nhiều loại `SQLException` sang `DataAccessException` từ thao tác của chính nó. Kết quả đó **không phải vì** người dùng đặt `@Repository` trên class DAO.
- `@Repository` không tự tạo `DataSource`, `EntityManager`, `JdbcTemplate`, schema hay connection pool.
- Một class `@Repository` không trở thành Spring Data repository tự động sinh truy vấn chỉ nhờ annotation. Spring Data repository interface và proxy factory của Spring Data là **cơ chế khác**.
- Không nên giả định mọi `RuntimeException` hay mọi lỗi SQL đều được dịch; chính hạ tầng và translator quyết định.

### 6.3. Ranh giới giữa repository và service

Thông thường repository định nghĩa thao tác như `findById`, `save` hoặc truy vấn phù hợp với nhu cầu nghiệp vụ; service kết hợp những thao tác ấy để giải quyết use case. Repository không nên nắm logic HTTP, response headers hay trực tiếp quyết định status code.

Ví dụ liên kết:

```text
OrderService.placeOrder()
    |
    +-- inventoryRepository.checkStock()
    +-- orderRepository.save()
    +-- paymentGateway.authorize()
    |
    +-- trả kết quả nghiệp vụ
```

Các method ở đây là hình minh họa quan hệ cộng tác, không phải API cụ thể có sẵn trong Spring.

## 7. @Controller: đánh dấu lớp presentation/web

### 7.1. Ý nghĩa trong Core Container

`@Controller` cũng là stereotype của `@Component`. Khi được scan, controller được đăng ký làm Spring bean, tham gia injection và lifecycle như các bean khác.

```java
import org.springframework.stereotype.Controller;

@Controller
public class CatalogController {
    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    public String lookup(int id) {
        return service.findTitle(id);
    }
}
```

`CatalogService` là kiểu nghiệp vụ minh họa. Trong **ApplicationContext thuần**, method `lookup` chỉ là method Java. Đặt `@Controller` không tự tạo HTTP endpoint hay bắt buộc mở server.

### 7.2. Tại sao Spring MVC có thể nhận diện controller?

Trong ứng dụng đã bật hạ tầng Spring MVC, các bean `@Controller` có thể được xem là **handler**. Spring MVC sử dụng các thành phần như `RequestMappingHandlerMapping` và `RequestMappingHandlerAdapter` để tìm request mapping và gọi handler method.

Muốn ánh xạ HTTP, còn cần cấu hình web MVC/`DispatcherServlet` và annotation định tuyến chẳng hạn `@RequestMapping`, `@GetMapping`, `@PostMapping`.

```java
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/catalog")
public class CatalogPageController {
    private final CatalogService catalogService;

    public CatalogPageController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable int id, Model model) {
        model.addAttribute("title", catalogService.findTitle(id));
        return "catalog/detail";
    }
}
```

Đây là ví dụ MVC minh họa, **không chạy độc lập** nếu thiếu `CatalogService`, Spring MVC servlet infrastructure và view resolver/template. Với controller MVC trả về `String` như trên, chuỗi `"catalog/detail"` thường được hiểu là **tên view**. Không phải mọi chuỗi trả về từ method trong `@Controller` đều được đưa nguyên văn vào HTTP response body.

### 7.3. @RestController liên hệ thế nào với @Controller?

`@RestController` trong Spring MVC là một annotation tổng hợp mang semantics `@Controller` **và `@ResponseBody`**. Nó thường phù hợp cho API trả dữ liệu vào response body, thay vì một tên view:

```java
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class CatalogApiController {
    private final CatalogService catalogService;

    public CatalogApiController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/{id}")
    public CatalogDto detail(@PathVariable int id) {
        return new CatalogDto(id, catalogService.findTitle(id));
    }

    public record CatalogDto(int id, String title) {}
}
```

Đoạn minh họa cần `CatalogService` và hạ tầng Spring Web MVC. Khi được cấu hình đúng, response body được ghi qua các `HttpMessageConverter` thích hợp. JSON thường được tạo nếu có converter (chẳng hạn Jackson) và negotiation phù hợp. Bản thân `@RestController` **không tự tải thư viện JSON, mở cổng HTTP hay cấu hình server**.

### 7.4. Cẩn thận ranh giới web

Một bean `@Controller` vẫn có thể được tạo trong context chỉ chứa Spring Core, nhưng sẽ **không xử lý HTTP** nếu không có DispatcherServlet hoặc handler infrastructure phù hợp. Ngược lại, chỉ thêm `@RequestMapping` trên một class không được đăng ký vào context cũng không tự khiến ứng dụng nhận được request.

Trong Spring WebFlux, annotated controllers cũng tồn tại, nhưng luồng dispatch/handler thuộc hạ tầng reactive và không nên đánh đồng với MVC Servlet. Câu hỏi về stereotype ở Core Container chỉ yêu cầu hiểu **cách bean được phát hiện** và **vai trò mà hạ tầng web có thể dùng tiếp**.

## 8. Ví dụ chạy được: ba stereotype cùng một Spring ApplicationContext

Để quan sát cơ chế component scan mà không phải dựng HTTP server hay database, ví dụ này dùng **Spring Framework 6.2.19**, **Java 17+**, và Java configuration `AnnotationConfigApplicationContext`.

Ta có ba lớp: repository cung cấp dữ liệu giả lập, service gọi repository, controller gọi service. Tên bean được khai báo rõ để kết quả không phụ thuộc quy tắc đặt tên của **nested class** trong Java.

Cấu trúc dự án:

```text
spring-stereotype-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        └── java/
            └── demo/
                └── StereotypeDemo.java
```

**`settings.gradle`**

```groovy
rootProject.name = 'spring-stereotype-demo'
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
    implementation 'org.springframework:spring-context:6.2.19'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.StereotypeDemo'
}
```

`spring-context` đủ cho demo Core Container: ba annotation thuộc `org.springframework.stereotype`; không cần dependency MVC, JDBC hoặc Spring Boot chỉ để quan sát bean scanning.

**`src/main/java/demo/StereotypeDemo.java`**

```java
package demo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

public class StereotypeDemo {

    @Configuration
    @ComponentScan(basePackageClasses = StereotypeDemo.class)
    public static class Config {
    }

    @Repository("catalogRepository")
    public static class CatalogRepository {
        public String findTitle(int id) {
            return "Product-" + id;
        }
    }

    @Service("catalogService")
    public static class CatalogService {
        private final CatalogRepository repository;

        public CatalogService(CatalogRepository repository) {
            this.repository = repository;
        }

        public String findTitle(int id) {
            return repository.findTitle(id);
        }
    }

    @Controller("catalogController")
    public static class CatalogController {
        private final CatalogService service;

        public CatalogController(CatalogService service) {
            this.service = service;
        }

        public String show(int id) {
            return service.findTitle(id);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            System.out.println("repository-bean="
                    + context.containsBean("catalogRepository"));
            System.out.println("service-bean="
                    + context.containsBean("catalogService"));
            System.out.println("controller-bean="
                    + context.containsBean("catalogController"));

            CatalogController controller =
                    context.getBean(CatalogController.class);
            System.out.println("result=" + controller.show(7));

            CatalogService first = context.getBean(CatalogService.class);
            CatalogService second = context.getBean(CatalogService.class);
            System.out.println("service-singleton=" + (first == second));
        }
    }
}
```

**Chạy** từ thư mục `spring-stereotype-demo` bằng Gradle có JDK 17+:

```bash
gradle run --quiet
```

Nếu dự án đã có Gradle Wrapper, chạy `./gradlew run --quiet` (Linux/macOS) hoặc `gradlew.bat run --quiet` (Windows). Không chạy `javac StereotypeDemo.java` riêng lẻ nếu chưa đưa `spring-context` và dependency transitives lên classpath; Gradle tự xử lý compile và runtime dependencies.

**Các dòng từ chương trình (stdout):**

```text
repository-bean=true
service-bean=true
controller-bean=true
result=Product-7
service-singleton=true
```

**Giải thích:**

1. `@ComponentScan(basePackageClasses = StereotypeDemo.class)` quét package `demo`, nơi các lớp `static` có stereotype của ví dụ được biên dịch.
2. Scanner nhận `CatalogRepository` qua `@Repository`, `CatalogService` qua `@Service`, `CatalogController` qua `@Controller`; các tên bean explicit tương ứng được đăng ký.
3. Spring constructor-inject `CatalogRepository` vào service, rồi service vào controller. Constructor đơn không cần thêm `@Autowired`.
4. `context.containsBean("...")` trả `true` cho cả ba bean, chứng minh chúng cùng hiện diện trong container.
5. Lời gọi Java trực tiếp `controller.show(7)` trả `Product-7`. **Đây không phải HTTP request** và chương trình không dựng Spring MVC.
6. Hai lời gọi `getBean(CatalogService.class)` trả cùng instance, phù hợp singleton scope mặc định.

Ví dụ có thể mở rộng bằng `@Scope("prototype")` trên service để quan sát các lần `getBean` cho instance khác nhau. Tuy vậy, nếu prototype đã được inject vào singleton controller, tham chiếu controller giữ vẫn là đối tượng được giải quyết cho controller tại thời điểm khởi tạo. Đây là quy tắc scope chung, không phụ thuộc việc lớp được đánh dấu `@Service` hay `@Component`.

## 9. Từ một request thực tế đến chuỗi controller → service → repository

Ở ứng dụng Spring Web MVC đã cấu hình đúng, luồng xử lý thường là:

```text
HTTP Client
    |
    v
DispatcherServlet / HandlerMapping
    |
    v
@Controller / @RestController
    |  nhận request, bind/validate dữ liệu và chuyển lời gọi
    v
@Service
    |  thực thi use case, điều phối quy tắc nghiệp vụ
    v
@Repository
    |  truy cập storage thông qua JDBC/JPA/... hoặc adapter riêng
    v
Database / external persistence
```

Chiều trả về là repository → service → controller → HTTP response. Đây là **mẫu tổ chức thường dùng**, không phải chuỗi bắt buộc do container ép cho mọi dự án. Ví dụ có service gọi service khác, controller gọi query service, repository truy cập cache hoặc các lớp domain riêng.

Một ứng dụng không có web vẫn có thể dùng `@Service` và `@Repository`; một ứng dụng không dùng cơ sở dữ liệu vẫn có thể có `@Controller`, và service không nhất thiết phải gọi repository. Spring IoC quản lý bean theo dependency thật, không tự tạo cạnh dependency chỉ vì tên annotation.

**Vai trò thiết kế hợp lý:**

- Controller: chuyển request và biểu diễn kết quả cho client; giữ logic nghiệp vụ chính trong service.
- Service: quyết định nghiệp vụ/use case, có thể ràng buộc giao dịch khi cấu hình.
- Repository: ẩn cơ chế tìm kiếm/truy xuất/lưu dữ liệu khỏi tầng trên.

Việc chia trách nhiệm giúp kiểm thử từng tầng và giảm phụ thuộc trực tiếp từ nghiệp vụ vào framework web hoặc chi tiết persistence.

## 10. Bean naming và injection có khác giữa các stereotype?

`@Controller`, `@Service` và `@Repository` dùng cơ chế bean naming/injection/scope như component thông thường. `@Service class CheckoutService` thường nhận tên `checkoutService`, trừ khi đặt `@Service("checkoutWorkflow")` hoặc dùng `BeanNameGenerator` tùy chỉnh. Class dạng acronym `URLClient` có thể giữ nguyên chữ đầu theo JavaBeans naming; kiểm tra tên thật bằng context thay vì đoán.

Khi constructor cần một interface có nhiều implementations, cần `@Qualifier`, `@Primary` hoặc tập hợp phù hợp. Cả ba stereotype đều mặc định singleton trong một container; không lưu mutable state theo request vào field singleton nếu chưa có thiết kế concurrency/scope đúng. Bean naming là danh tính trong registry, còn annotation stereotype diễn đạt vai trò kiến trúc.

## 11. Phân biệt stereotype với @Bean và @Configuration

Component scanner đăng ký definition từ **class có stereotype**; `@Bean` đăng ký definition từ **factory method** của configuration được Spring xử lý. Class bên ngoài codebase có thể thành bean mà không mang stereotype:

```java
@Configuration(proxyBeanMethods = false)
class IntegrationConfig {
    @Bean
    ExternalClient externalClient() {
        return new ExternalClient("endpoint");
    }
}
```

`ExternalClient` là kiểu giả định trong đoạn minh họa; bean name mặc định ở đây là `externalClient` theo method. Một class được scan rồi còn được `@Bean` tạo lần nữa có thể gây trùng registration hoặc nhiều bean cùng type, tùy tên/cấu hình. Dù từ scanner hay factory method, instance được Spring quản lý vẫn có thể nhận annotation injection và lifecycle khi có processor phù hợp. `@Service` không phải điều kiện bắt buộc để một bean được inject.

## 12. Stereotype, AOP và các cơ chế mở rộng

Stereotype giúp các công cụ và aspect phân loại bean theo trách nhiệm. Ví dụ muốn ghi metric cho các method service, một aspect có thể chọn các lớp gắn `@Service` bằng pointcut phù hợp.

Tuy nhiên, hãy tách ba tầng khái niệm:

1. **Phát hiện bean:** component scanning nhìn thấy stereotype/meta-annotation.
2. **Tạo bean và injection:** IoC Container quản lý object và dependency.
3. **Hành vi bổ sung:** cơ chế khác, như post-processor, Spring MVC hoặc AOP, diễn giải stereotype/method annotation nếu chúng được bật.

`@Service` không trực tiếp sinh proxy; `@Repository` không bảo đảm một proxy dịch ngoại lệ nếu thiếu post-processor/translator; `@Controller` không tự thiết lập DispatcherServlet.

### Tại sao phân biệt ba tầng này quan trọng?

Giả sử một ứng dụng chạy được `context.getBean(MyController.class)` nhưng đường dẫn HTTP trả 404. Điều đó chứng minh có bean trong context nhưng **không chứng minh** Spring MVC đã quét mapping, có handler hoặc đường dẫn khớp.

Ngược lại, một repository được inject thành công nhưng exception vendor không đổi thành `DataAccessException` có thể do thiếu translator, post-processor hoặc lời gọi không qua proxy. Việc bean có trong context và việc behavior tích hợp hoạt động là **hai phép kiểm tra khác nhau**.

## 13. Lỗi thường gặp và cách chẩn đoán

### Lỗi 1 — Đặt @Service rồi tưởng Spring tự tìm dù ngoài package scan

**Triệu chứng:** `NoSuchBeanDefinitionException` hoặc `UnsatisfiedDependencyException` khi inject service.

Kiểm tra package chứa class, `@ComponentScan(basePackages=...)`, lớp cấu hình đang được load và các include/exclude filters. Đừng sửa bằng cách tùy tiện mở rộng scan đến toàn bộ classpath; nên chọn package gốc nhất quán.

### Lỗi 2 — Đặt @Controller rồi chờ HTTP endpoint xuất hiện

**Triệu chứng:** Bean tồn tại nhưng HTTP route không có.

Kiểm tra đây có phải ứng dụng Spring MVC/WebFlux đang chạy đúng hạ tầng hay không, có `@RequestMapping`/`@GetMapping` tương ứng, có servlet/reactive handler mapping, context chứa bean đúng và path/method có khớp request không. `@Controller` trong `AnnotationConfigApplicationContext` thuần chỉ là managed bean.

### Lỗi 3 — Đánh dấu @Repository là mọi SQLException sẽ được dịch

**Triệu chứng:** Caller vẫn thấy exception gốc hoặc không có hạ tầng translation.

Kiểm tra data-access library có cơ chế dịch riêng không; nếu dùng `PersistenceExceptionTranslationPostProcessor` thì bảo đảm post-processor và `PersistenceExceptionTranslator` phù hợp đã đăng ký và lời gọi qua bean/proxy. Không bắt mọi exception rồi tự đổi thành `DataAccessException` mà bỏ mất nguyên nhân lỗi.

### Lỗi 4 — Nhầm @Service với @Transactional

**Triệu chứng:** Operation service không rollback dù class có `@Service`.

Spring chỉ dùng transaction khi được cấu hình đúng (`@Transactional`, transaction manager, interceptor/proxy phù hợp, v.v.). Kiểm tra transaction boundary, phương thức được gọi qua Spring proxy và những giới hạn như self-invocation với proxy mode mặc định. Không thêm `@Service` lần nữa để giải quyết.

### Lỗi 5 — Có hai bean cùng interface, không xác định được candidate

**Triệu chứng:** `NoUniqueBeanDefinitionException`.

Đặt qualifier rõ ràng cho injection point hoặc dùng `@Primary` khi một implementation thật sự là mặc định; trong trường hợp nghiệp vụ cần tất cả implementations, inject collection `List<T>`. Không đổi hết `@Repository` thành `@Component` vì việc đó không giải quyết xung đột theo type.

### Lỗi 6 — Hai class khác package nhưng sinh cùng bean name

**Triệu chứng:** lỗi xung đột bean definition/name trong quá trình scan.

Ví dụ `catalog.CustomerService` và `billing.CustomerService` có thể cùng sinh tên mặc định `customerService`. Dùng tên explicit, bean name generator phù hợp hoặc điều chỉnh ranh giới scan nếu thực sự có hai bean độc lập.

### Lỗi 7 — Nghĩ @Controller mặc định trả JSON

`@Controller` trả một `String` trong Spring MVC có thể biểu đạt tên view. Để trả response body, dùng `@ResponseBody` trên method/type hoặc `@RestController` khi muốn mọi handler method theo mô hình ấy; cần message converter tương ứng để serialize đối tượng.

### Lỗi 8 — Viết annotation trên class rồi tự tạo đối tượng bằng new

```java
CatalogService service = new CatalogService(new CatalogRepository());
```

Đây là Java object được tạo thủ công, **không tự tham gia** container injection, lifecycle, proxy hoặc BeanPostProcessor chỉ vì class mang `@Service`/`@Repository`. Muốn sử dụng managed bean, nhận nó qua dependency injection hoặc `context.getBean(...)` trong mã hạ tầng/test.

### Lỗi 9 — Đưa business state của từng request vào singleton service/controller

Các stereotype đều là singleton mặc định. Không lưu mutable data của request/caller vào field singleton nếu có nhiều request đồng thời. Đưa trạng thái vào biến cục bộ, tham số hoặc bean đúng scope và thiết kế đồng bộ hóa khi cần.

### Lỗi 10 — Thấy annotation là cho rằng kiến trúc đã được thực thi

Stereotype giúp người đọc và tooling hiểu role, nhưng Spring không mặc định kiểm tra rằng controller không gọi trực tiếp SQL hay repository không xây HTTP response. Duy trì ranh giới kiến trúc bằng thiết kế package, code review, test kiến trúc và các quy tắc dự án.

## 14. Kiểm thử và quan sát thực tế

### 14.1. Test sự hiện diện của bean

Đối với container thuần:

```java
try (AnnotationConfigApplicationContext ctx =
        new AnnotationConfigApplicationContext(Config.class)) {
    assert ctx.containsBean("catalogRepository");
    assert ctx.containsBean("catalogService");
    assert ctx.containsBean("catalogController");
}
```

Đây là snippet dựa trên `Config` của `StereotypeDemo`; khi dùng ở file khác cần khai báo/import đúng tên class và bật assertion `-ea`, hoặc dùng JUnit assertions. Để test độc lập và thông báo lỗi rõ, `assertTrue` của JUnit thường phù hợp hơn Java `assert`.

### 14.2. Test luồng constructor injection

Ví dụ đầy đủ ở mục 8 cho phép quan sát `CatalogController` gọi `CatalogService` và `CatalogRepository` mà không khởi tạo thủ công từng collaborator trong `main`. Trong bài kiểm thử, thay repository bằng fake/test bean khi muốn cô lập nghiệp vụ khỏi dữ liệu thật.

### 14.3. Test riêng hành vi web và persistence

- Để xác thực HTTP mapping, chạy integration test trong **web application context** có MVC/WebFlux infrastructure; với MVC có thể dùng `MockMvc`. Kiểm tra path, HTTP method, response body, status và validation.
- Để xác thực exception translation, tạo test với persistence technology và translator/post-processor đúng, gọi qua Spring-managed repository bean, rồi assert ngoại lệ đầu ra phù hợp.
- Để xác thực transaction của service, dùng test có transaction manager và tài nguyên transactional thật hoặc integration fixture phù hợp; chỉ test bean scanning sẽ không chứng minh commit/rollback.

Một phép thử `containsBean` chứng minh đăng ký bean, **không chứng minh** một HTTP endpoint chạy hay persistence exception đã được dịch.

## 15. Checklist thiết kế

Trước khi đánh dấu một class:

1. Class này chịu trách nhiệm chính là **presentation, nghiệp vụ hay data access**? Chọn `@Controller`, `@Service` hoặc `@Repository` tương ứng; nếu không thuộc nhóm ấy, `@Component` tổng quát có thể phù hợp hơn.
2. Package của class có nằm trong phạm vi `@ComponentScan` không? Có filter hoặc condition làm loại bean không?
3. Dependency được inject qua constructor và có một bean hợp lệ cho mỗi tham số chưa? Nếu nhiều candidate, đã dùng qualifier/primary đúng chưa?
4. Bean có state mutable không? Scope mặc định singleton có phù hợp tính đồng thời và vòng đời không?
5. Nếu dùng `@Repository` để kỳ vọng exception translation, đã có infrastructure và translator cần thiết chưa?
6. Nếu dùng `@Controller` để xử lý HTTP, ứng dụng đã bật đúng web stack, handler mapping và route annotations chưa?
7. Nếu dùng `@Service` với transaction/cache/async, từng tính năng đó đã được bật, cấu hình và kiểm thử riêng chưa?

## 16. Câu hỏi ôn tập và đáp án

**Câu 1.** Tại sao Spring có thể scan cùng lúc `@Service`, `@Repository` và `@Controller`?

**Đáp:** Vì chúng là các stereotype chuyên biệt, meta-annotated bằng `@Component`. Bộ lọc mặc định của component scanner tìm các lớp phù hợp trong package được cấu hình.

**Câu 2.** Nếu `@Controller` được phát hiện trong một `AnnotationConfigApplicationContext` thuần thì đã có HTTP endpoint chưa?

**Đáp:** Chưa. Bean tồn tại trong IoC Container; cần hạ tầng web và mapping cho HTTP request. Method có thể được gọi như method Java bình thường.

**Câu 3.** `@Service` có tự áp transaction khi gọi method không?

**Đáp:** Không. Transaction là cơ chế khác cần annotation/hạ tầng thích hợp, chẳng hạn `@Transactional` và transaction manager. Stereotype service chỉ diễn đạt vai trò và hỗ trợ được scan.

**Câu 4.** `@Repository` có dịch mọi exception thành `DataAccessException` không?

**Đáp:** Không. Nó là marker cho việc dịch persistence exception khi post-processor, translator và lời gọi qua cơ chế phù hợp đã được thiết lập. Chỉ những exception được translator hỗ trợ mới được chuyển đổi.

**Câu 5.** Khi hai class ở hai package cùng tên `CustomerService` được scan, điều gì có thể xảy ra?

**Đáp:** Tên bean mặc định có thể trùng. Có thể giải quyết bằng tên explicit, bean name generator phù hợp hoặc tổ chức phạm vi scan hợp lý. Việc cả hai dùng `@Service` không tự phân biệt tên.

**Câu 6.** Một class có thể trở thành Spring bean mà không có stereotype không?

**Đáp:** Có. Ví dụ method `@Bean` trong `@Configuration` trả về đối tượng tương ứng. Component scanning chỉ là một trong các cơ chế đăng ký bean.

**Câu 7.** Vì sao không nên chỉ dựa vào `containsBean("catalogController")` để khẳng định GET `/catalog/7` hoạt động?

**Đáp:** Vì phép kiểm tra chỉ xác nhận bean đã đăng ký; route hoạt động đòi hỏi context web, handler mapping, mapping annotation, request method/path và các thành phần MVC tương ứng.

**Câu 8.** Spring có bắt buộc controller gọi service, rồi service gọi repository không?

**Đáp:** Không. Đây là mô hình phân tầng thường dùng để quản lý trách nhiệm và dependency, không phải luật runtime của IoC Container.

## 17. Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Classpath Scanning and Managed Components](https://docs.spring.io/spring-framework/reference/6.2/core/beans/classpath-scanning.html): `@Component`, các stereotype, meta-annotation, bean naming và scope scan.
- [Spring Framework 6.2 — DAO Support](https://docs.spring.io/spring-framework/reference/6.2/data-access/dao.html): repository, Spring `DataAccessException` và exception translation.
- [Spring Framework 6.2 — Spring MVC Annotated Controllers](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann.html): `@Controller` trong MVC và điều kiện ứng dụng web.
- [Spring Framework 6.2 — Mapping Requests](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-requestmapping.html): các mapping annotation và điều kiện match request.
- [Spring Framework 6.2 — Spring Beans](https://docs.spring.io/spring-framework/reference/6.2/core/beans.html): định nghĩa bean, quản lý dependency, phạm vi trách nhiệm IoC Container.
- [Spring Framework 6.2 API — Repository](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/stereotype/Repository.html): hợp đồng stereotype và điều kiện liên quan `PersistenceExceptionTranslationPostProcessor`.

**Ghi nhớ:** `@Service`, `@Repository` và `@Controller` trước hết xác định **vai trò class** để Spring Core Container phát hiện và quản lý bean. `@Repository` có điểm tích hợp exception translation trong hạ tầng persistence thích hợp; `@Controller` được web framework dùng để nhận diện handler. Những tính năng như HTTP routing, transaction và SQL processing vẫn cần các thành phần của chúng được cấu hình rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="component-scanning-003">Phạm vi quét của @ComponentScan và vị trí gói</a>

<details>
<summary>Xem chi tiết</summary>

# Phạm vi quét của `@ComponentScan` và vị trí package trong Spring Core Container

## 1. Vì sao class có `@Component` nhưng Spring vẫn không tìm thấy bean?

Giả sử một ứng dụng có service sau:

```java
package com.example.shop.service;

import org.springframework.stereotype.Service;

@Service
public class OrderService {
    public String status() {
        return "ready";
    }
}
```

Một class mang `@Service` (vốn là một dạng chuyên biệt của `@Component`) có **đủ metadata để được phát hiện**, nhưng chưa chắc đã có bean `OrderService` trong Spring IoC Container. Để Spring đăng ký class thành bean theo component scanning, package của class phải nằm trong **phạm vi quét** của một cấu hình đã được kích hoạt, class phải là ứng viên hợp lệ và không bị các filter/condition loại bỏ.

Ví dụ nếu ứng dụng chỉ quét `com.example.billing`, Spring không tự đi sang `com.example.shop.service`. Lỗi thường bộc lộ khi constructor cần `OrderService` mà không có bean tương ứng:

```text
No qualifying bean of type 'com.example.shop.service.OrderService' available
```

Đây là ví dụ về **nội dung thông báo lỗi** có thể gặp; stack trace đầy đủ thay đổi theo điểm tiêm và phiên bản Spring.

Mục tiêu của bài này là nắm chính xác:

1. `@ComponentScan` chọn gói gốc và các gói con ra sao;
2. sự khác biệt giữa package mặc định, `basePackages` và `basePackageClasses`;
3. vị trí đặt configuration class hoặc `@SpringBootApplication` ảnh hưởng việc phát hiện component như thế nào;
4. cách quét có kiểm soát qua nhiều package/module mà không mở phạm vi tùy tiện;
5. cách chẩn đoán bean bị thiếu và tránh các sai lầm về package, classpath, filter và condition.

## 2. `@ComponentScan` thực sự quét gì?

`@ComponentScan` thuộc package `org.springframework.context.annotation`. Khi một class cấu hình được xử lý, annotation này yêu cầu Spring tìm các **component candidates** trong một hoặc nhiều base package. Scanner chủ yếu đọc metadata các file `.class` khả dụng trên classpath (hoặc trong module path với điều kiện thích hợp) và đăng ký **bean definitions** cho các ứng viên hợp lệ.

Theo mặc định, ứng viên là class được chú thích bằng `@Component` hoặc một stereotype có meta-annotation `@Component`, như:

- `@Service` cho lớp nghiệp vụ;
- `@Repository` cho lớp truy cập dữ liệu;
- `@Controller` cho lớp web/controller;
- `@Configuration` cho lớp cấu hình;
- stereotype tự định nghĩa được meta-annotated bằng `@Component`.

Component scan **không** tự tìm mọi Java class trong package rồi tạo tất cả thành bean. Một class bình thường không mang stereotype phù hợp vẫn không trở thành bean chỉ vì classpath chứa nó. Ngoài ra, class ứng viên phải có thể được Spring tạo và không bị bỏ bởi filter hoặc điều kiện cấu hình.

Quy trình khái quát:

```text
Configuration được đăng ký vào ApplicationContext
                     |
                     v
        Đọc metadata @ComponentScan
                     |
                     v
   Chọn một hoặc nhiều base package cụ thể
                     |
                     v
   Duyệt class trong package và các package con
                     |
                     v
  Đánh giá stereotype + include/exclude filters
                     |
                     v
     Đăng ký bean definitions hợp lệ
                     |
                     v
Spring tạo bean, resolve dependency và quản lý vòng đời
```

**Hai giai đoạn khác nhau:** scanner đăng ký *định nghĩa bean*; việc tạo *instance* phụ thuộc vào scope, lazy initialization, dependency và tiến trình refresh của context. `@ComponentScan` không định nghĩa trình tự chạy method nghiệp vụ và không tự khởi động HTTP server.

## 3. Package của class cấu hình là mốc mặc định

Cấu hình sau **không chỉ định** `basePackages`:

```java
package com.example.shop;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class ShopConfig {
}
```

Với cấu hình này, Spring mặc định bắt đầu quét tại **package của class khai báo `@ComponentScan`**, tức `com.example.shop`, rồi đi vào các **subpackage** như `com.example.shop.service` và `com.example.shop.persistence`.

Ví dụ cấu trúc Java:

```text
com.example/
├── shop/
│   ├── ShopConfig.java                  <-- có @ComponentScan
│   ├── service/
│   │   └── OrderService.java            <-- được quét
│   └── persistence/
│       └── OrderRepository.java         <-- được quét
├── shared/
│   └── TextConverter.java               <-- KHÔNG được quét
└── billing/
    └── InvoiceService.java              <-- KHÔNG được quét
```

Package `com.example.shared` và `com.example.billing` là **package ngang cấp** với `com.example.shop`. Scanner không tự lên package cha `com.example` để đi sang các nhánh ngang cấp.

| Class/package | So với base package `com.example.shop` | Được xét bởi lượt scan này? |
| --- | --- | --- |
| `com.example.shop.ShopConfig` | Cùng package | Có |
| `com.example.shop.service.OrderService` | Package con | Có |
| `com.example.shop.persistence.OrderRepository` | Package con | Có |
| `com.example.shared.TextConverter` | Nhánh ngang cấp | Không |
| `com.example.billing.InvoiceService` | Nhánh ngang cấp | Không |
| `com.example.BaseHelper` | Package cha | Không |

“Được xét bởi lượt scan” không đồng nghĩa “chắc chắn có bean”: class vẫn phải đáp ứng stereotype, bộ lọc, điều kiện và quy tắc đăng ký. Hơn nữa một bean ngoài vùng scan **vẫn có thể được đăng ký bằng cách khác**, chẳng hạn `@Bean` hoặc `@Import`.

### Package Java khác đường dẫn ngẫu nhiên trên máy

Với source `src/main/java/com/example/shop/service/OrderService.java`, phần quan trọng ở đầu file là:

```java
package com.example.shop.service;
```

Compiler tạo tên đầy đủ `com.example.shop.service.OrderService`. Component scanner dựa vào metadata class và package trên **runtime classpath/module path**, chứ không dò mọi thư mục có file `.java` trong workspace. Quy tắc tổ chức thư mục theo package rất hữu ích, nhưng gốc `src/main/java` là source root của build tool, không phải base package mặc định của `@ComponentScan`.

**Đặt configuration class vào default package** (file Java không khai báo `package`) là cấu trúc cần tránh. Khi không có base package cụ thể, quét từ gốc có thể khiến Spring tìm quá rộng trên classpath, gây hiệu năng kém và cấu hình ngoài ý muốn. Luôn dùng package có tên rõ ràng.

## 4. Chỉ rõ phạm vi bằng `basePackages`

Class cấu hình ở `com.example.app` vẫn có thể yêu cầu quét các package khác:

```java
@Configuration
@ComponentScan(basePackages = {"com.example.shop", "com.example.shared"})
class AppConfig { }
```

Đây là snippet cấu hình minh họa với imports thích hợp. Scanner xét hai package và mọi subpackage. Giá trị `basePackages` là **Java package name**, không phải đường dẫn `src/main/java` hoặc fully qualified tên class. Cú pháp `@ComponentScan("com.example.shop")` dùng thuộc tính `value`, là alias cho `basePackages`; tránh cấu hình hai alias với giá trị mâu thuẫn. Chuỗi package có thể bị bỏ sót khi refactor.

## 5. Chỉ rõ phạm vi bằng `basePackageClasses`

`basePackageClasses` chọn **package chứa một class hoặc interface làm marker** và các package con:

```java
@Configuration
@ComponentScan(basePackageClasses = {ShopPackage.class, SharedPackage.class})
class AppConfig { }
```

Giả định `ShopPackage` ở `com.example.shop` và `SharedPackage` ở `com.example.shared`. Marker không cần `@Component`, vì nó chỉ chọn gốc quét. Đặt marker tại **root feature**; dùng nhầm `OrderService.class` ở `com.example.shop.service` sẽ bỏ qua sibling `com.example.shop.persistence`. Khai báo `basePackages` và `basePackageClasses` cùng lúc sẽ chọn **hợp các vùng** đã liệt kê. Các ví dụ đầy đủ và đánh đổi về refactor được trình bày ngay ở bài chuyên sâu tiếp theo.

## 6. Ví dụ đầy đủ Java 17+: một package được quét, một package bị bỏ sót

Ứng dụng chạy với **Spring Framework 6.2.x** qua `spring-context`, không dùng Spring Boot. Hai `ApplicationContext` được tạo lần lượt:

- `ShopOnlyConfig` quét đúng `demo.shop`. Bean `OrderService` và `OrderRepository` được phát hiện, còn `SharedEncoder` ở `demo.shared` không xuất hiện.
- `ExpandedConfig` sử dụng `basePackageClasses` với hai marker tại `demo.shop` và `demo.shared`. Cả ba bean được phát hiện.

Mã Java trong ví dụ là chương trình thực, tách thành các file và package tương ứng; các đoạn rời ở các mục khác chỉ có tính minh họa.

### 6.1. Cấu trúc thư mục

```text
component-scan-packages/
├── settings.gradle
├── build.gradle
└── src/main/java/
    └── demo/
        ├── app/
        │   └── ScanPackagesDemo.java
        ├── shop/
        │   ├── ShopPackage.java
        │   ├── service/
        │   │   └── OrderService.java
        │   └── persistence/
        │       └── OrderRepository.java
        └── shared/
            ├── SharedPackage.java
            └── text/
                └── SharedEncoder.java
```

`settings.gradle`:

```groovy
rootProject.name = 'component-scan-packages'
```

`build.gradle`:

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.18'
}

application {
    mainClass = 'demo.app.ScanPackagesDemo'
}
```

Dự án cần JDK 17+ và bản Gradle tương thích với JDK đang dùng.

### 6.2. Class ứng dụng và hai cấu hình scan

File `src/main/java/demo/app/ScanPackagesDemo.java`:

```java
package demo.app;

import demo.shop.ShopPackage;
import demo.shop.service.OrderService;
import demo.shared.SharedPackage;
import demo.shared.text.SharedEncoder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

public class ScanPackagesDemo {

    public static void main(String[] args) {
        try (var shop =
                new AnnotationConfigApplicationContext(ShopOnlyConfig.class)) {
            System.out.println("shop.orderService = " +
                    shop.containsBean("orderService"));
            System.out.println("shop.sharedEncoder = " +
                    shop.containsBean("sharedEncoder"));
            System.out.println("shop.message = " +
                    shop.getBean(OrderService.class).message());
        }

        try (var expanded =
                new AnnotationConfigApplicationContext(ExpandedConfig.class)) {
            System.out.println("expanded.orderService = " +
                    expanded.containsBean("orderService"));
            System.out.println("expanded.sharedEncoder = " +
                    expanded.containsBean("sharedEncoder"));

            String message =
                    expanded.getBean(OrderService.class).message();
            String decorated =
                    expanded.getBean(SharedEncoder.class).encode(message);
            System.out.println("expanded.message = " + decorated);
        }
    }
}

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "demo.shop")
class ShopOnlyConfig {
}

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        ShopPackage.class,
        SharedPackage.class
})
class ExpandedConfig {
}
```

Chỉ có `ScanPackagesDemo` là top-level `public` trong file; `ShopOnlyConfig` và `ExpandedConfig` là package-private class hợp lệ trong cùng package `demo.app`. Việc đăng ký trực tiếp class cấu hình vào `AnnotationConfigApplicationContext` khiến Spring xử lý `@ComponentScan` của chúng, dù bản thân package `demo.app` không phải vùng scan của hai lượt quét.

### 6.3. Marker tại package root

File `src/main/java/demo/shop/ShopPackage.java`:

```java
package demo.shop;

public final class ShopPackage {
    private ShopPackage() {
    }
}
```

File `src/main/java/demo/shared/SharedPackage.java`:

```java
package demo.shared;

public final class SharedPackage {
    private SharedPackage() {
    }
}
```

Hai class này **không có `@Component`**. Chúng chỉ đánh dấu package root cho `basePackageClasses`; Spring không tạo bean `ShopPackage` hoặc `SharedPackage` từ scanning mặc định.

### 6.4. Các class trong `demo.shop`

File `src/main/java/demo/shop/persistence/OrderRepository.java`:

```java
package demo.shop.persistence;

import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {
    public String load() {
        return "ok";
    }
}
```

File `src/main/java/demo/shop/service/OrderService.java`:

```java
package demo.shop.service;

import demo.shop.persistence.OrderRepository;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public String message() {
        return "order:" + repository.load();
    }
}
```

Hai component này ở **hai subpackage ngang cấp** (`demo.shop.service` và `demo.shop.persistence`). Quét từ root `demo.shop` sẽ lấy cả hai. Constructor duy nhất của `OrderService` không cần `@Autowired`; Spring chọn `OrderRepository` theo kiểu để inject.

### 6.5. Component ở nhánh `demo.shared`

File `src/main/java/demo/shared/text/SharedEncoder.java`:

```java
package demo.shared.text;

import org.springframework.stereotype.Component;

@Component
public class SharedEncoder {
    public String encode(String value) {
        return "[" + value + "]";
    }
}
```

Bean này nằm **ngoài** `demo.shop` nên không có trong `ShopOnlyConfig`. Nó chỉ xuất hiện trong context `ExpandedConfig` vì cấu hình này bổ sung root `demo.shared`.

### 6.6. Chạy và kiểm tra stdout

Tại thư mục chứa `build.gradle`:

```bash
gradle run --quiet
```

Nếu dự án có Gradle Wrapper, dùng `./gradlew run --quiet` trên Linux/macOS hoặc `.\gradlew.bat run --quiet` trên Windows PowerShell. Các dòng do `System.out.println` in ra là:

```text
shop.orderService = true
shop.sharedEncoder = false
shop.message = order:ok
expanded.orderService = true
expanded.sharedEncoder = true
expanded.message = [order:ok]
```

**Giải thích chính xác đầu ra:**

1. `shop.orderService = true`: package root `demo.shop` chứa `demo.shop.service` nên `OrderService` được phát hiện.
2. `shop.sharedEncoder = false`: `demo.shared.text` là nhánh ngang cấp, không thuộc `demo.shop`.
3. `shop.message = order:ok`: `OrderService` nhận `OrderRepository` qua constructor; cả hai nằm dưới root được quét.
4. `expanded.orderService = true`: marker `ShopPackage.class` đặt root ở `demo.shop`.
5. `expanded.sharedEncoder = true`: marker `SharedPackage.class` bổ sung root `demo.shared`.
6. `expanded.message = [order:ok]`: lấy trực tiếp hai bean từ context và gọi phương thức Java; không có HTTP hay web controller.

Hai context độc lập có bean registry riêng. Một bean được đăng ký trong `expanded` không thể tự làm bean tương ứng xuất hiện trong `shop`.

## 7. Đặt `@SpringBootApplication` ở package nào?

Trong Spring Boot, `@SpringBootApplication` bao gồm chức năng của `@SpringBootConfiguration`, `@EnableAutoConfiguration` và `@ComponentScan`. Khi không đặt thuộc tính scan tùy chỉnh, component scanning dùng **package chứa class `@SpringBootApplication`** làm root và tìm trong các subpackage.

Cấu trúc nên dùng cho dự án thông thường:

```text
com.acme.shop/
├── ShopApplication.java              <-- @SpringBootApplication
├── api/
│   └── OrderController.java          <-- @RestController
├── service/
│   └── OrderService.java             <-- @Service
└── persistence/
    └── OrderRepositoryImpl.java      <-- @Repository
```

`ShopApplication` đặt ở `com.acme.shop` giúp tìm các component trong ba nhánh con mà không cần khai báo tên từng nhánh. Nếu chuyển `ShopApplication` xuống `com.acme.shop.api`, scanner mặc định sẽ chủ yếu tìm `com.acme.shop.api` và các nhánh dưới đó; service/repository ở sibling packages có nguy cơ không được phát hiện.

Mã minh họa class Boot:

```java
package com.acme.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ShopApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }
}
```

Đoạn này cần dependency Spring Boot thích hợp và là **minh họa riêng**, không phải phần mã chạy của dự án Spring Context ở mục 6.

### Chỉnh package scan trong Boot

Khi component thuộc nhiều root package, có thể chọn rõ:

```java
@SpringBootApplication(scanBasePackages = {
        "com.acme.shop",
        "com.acme.shared"
})
public class ShopApplication {
}
```

Hoặc type-safe:

```java
@SpringBootApplication(scanBasePackageClasses = {
        ShopMarker.class,
        SharedMarker.class
})
public class ShopApplication {
}
```

Hai đoạn Boot là **biến thể minh họa độc lập**, cần class marker/package tương ứng. Trong Spring Boot, `scanBasePackages` và `scanBasePackageClasses` là alias tới cơ chế **`@ComponentScan`**.

**Giới hạn quan trọng:** các thuộc tính `scanBasePackages` của `@SpringBootApplication` điều chỉnh **component scanning**, không tự điều chỉnh phạm vi quét **JPA entity** hay **Spring Data repository interface**. Những cơ chế này có quy tắc cấu hình khác nhau; khi cần, dùng `@EntityScan` và `@EnableJpaRepositories` (hoặc cơ chế tương ứng với Spring Data module). Mở rộng component scan không bảo đảm entity/repository proxy ở package ngoài root Boot sẽ tự được nhận diện.

## 8. Nhiều module và thư viện Java: điều kiện classpath

Một ứng dụng nhiều Gradle module có thể tổ chức:

```text
shop-app
  └── package com.acme.shop
common-library
  └── package com.acme.common
```

Giả sử `common-library` xuất bản một `@Component` trong `com.acme.common`. Để ứng dụng nhận bean qua component scan, **đồng thời** cần:

1. Artifact/module chứa class phải có trên runtime classpath (hoặc được nhìn thấy từ module system tương ứng).
2. Base package scan phải bao phủ `com.acme.common`.
3. Class phải là component candidate hợp lệ và không bị filter/condition loại trừ.
4. Các dependency cần để tạo bean phải thỏa mãn.

Ví dụ trong `build.gradle` của `shop-app`:

```groovy
dependencies {
    implementation project(':common-library')
}
```

Đây là **minh họa cho một multi-project Gradle build** đã khai báo `:common-library`; dòng trên không tự đủ tạo một dự án độc lập. Nó chỉ giải quyết sự hiện diện của module thư viện trên classpath; scanner vẫn phải quét đúng package hoặc thư viện phải cung cấp cơ chế đăng ký riêng.

**Không đặt base package chỉ theo tên Gradle module.** Spring quét Java package trên classpath, không trực tiếp hiểu tên project Gradle. Một module Gradle có thể chứa nhiều package, hoặc nhiều module cùng sử dụng một package prefix.

### Java Module System (JPMS)

Nếu ứng dụng chạy theo Java module path với `module-info.java`, việc Spring tìm metadata, gọi constructor hoặc inject bằng reflection có thể cần cấu hình `exports`/`opens` phù hợp. Lỗi truy cập reflection do JPMS không đồng nghĩa package không được scan. Hãy phân biệt “không tìm thấy bean definition” với “tìm thấy bean nhưng không thể tạo instance hoặc truy cập thành viên”.

## 9. Quét có kiểm soát bằng bộ lọc

`includeFilters` và `excludeFilters` chỉ chọn/loại candidates **bên trong base package**. Ví dụ loại một implementation khỏi lượt scan:

```java
@Configuration
@ComponentScan(
    basePackages = "com.example.shop",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyOrderService.class))
class AppConfig { }
```

Đây là snippet cần import `FilterType` và định nghĩa `LegacyOrderService`. Exclude filter không ngăn class được đăng ký qua `@Bean` hoặc scanner khác. Với `useDefaultFilters=false`, stereotype mặc định không được chọn trừ khi có include filter thích hợp; filter cũng không mở rộng vùng package scan.

## 10. `@ComponentScan` kết hợp với `@Import` và `@Bean`

`@Import` đưa class/configuration được hỗ trợ vào quá trình đăng ký, còn `@Bean` đăng ký definition từ factory method. Chúng có thể đưa bean vào context ngay cả khi class nằm ngoài root của component scan:

```java
@Configuration(proxyBeanMethods = false)
class UtilityConfig {
    @Bean
    JsonHelper jsonHelper() { return new JsonHelper(); }
}
```

`JsonHelper` là kiểu giả định trong snippet. Đừng mở scan cả thư viện chỉ để tạo một bean cần dùng; nhưng cũng phải bảo đảm configuration chứa `@Bean` thực sự đã được đăng ký/import. Dependency JAR có trên runtime classpath và annotation được scan là hai điều kiện riêng.

## 11. Các lỗi về phạm vi quét và cách chẩn đoán

| Hiện tượng | Nguyên nhân thường gặp | Cách xác minh/sửa |
| --- | --- | --- |
| `@Service` có trên class nhưng thiếu bean | Class ở package ngoài root scan | So sánh `package` của class với `@ComponentScan`/package class Boot |
| Quét `com.acme.shop.service` nhưng không thấy repository | Repository ở `com.acme.shop.persistence` là sibling | Dùng marker ở `com.acme.shop` hoặc bổ sung root persistence |
| `@ComponentScan` để trống quét nhầm nơi | Class cấu hình nằm trong package hẹp | Đặt cấu hình tại root phù hợp hoặc chỉ rõ `basePackageClasses` |
| `@SpringBootApplication` đặt sâu trong package web | Default scan chỉ phủ nhánh web | Đưa class Boot lên root package hoặc mở scan rõ ràng |
| Mở `scanBasePackages` mà JPA repository vẫn không có | Component scan khác Spring Data repository scanning | Kiểm tra `@EnableJpaRepositories`/auto-config packages của Spring Data |
| `@Entity` không được quản lý | Entity scanning khác component scanning | Kiểm tra `@EntityScan`/cấu hình JPA |
| Module thư viện có class nhưng bean vẫn không thấy | Thiếu dependency runtime hoặc thiếu package root scan | Kiểm tra dependency graph/runtime classpath và package |
| Class ngoài scan vẫn thành bean | Có `@Import`, `@Bean`, auto-configuration hoặc registration khác | Kiểm tra mọi nguồn bean definition thay vì kết luận scan sai |
| `@ComponentScan` có root đúng nhưng class biến mất | Bị `excludeFilters`, `@Profile`/`@Conditional` hoặc không là candidate | Kiểm tra metadata/filter/condition |
| Dùng `useDefaultFilters=false` rồi `@Service` không còn | Đã tắt stereotype filter mặc định | Thêm include filter đúng hoặc bỏ cấu hình tắt |
| Có bean definition nhưng tạo bean lỗi | Constructor dependency thiếu, circular dependency, lỗi factory, JPMS reflection | Phân biệt registration với instantiation, đọc exception chain |
| Hai bean cùng tên sau khi mở rộng scan | Các class khác package trùng tên mặc định/explicit name | Chọn tên có chủ đích hoặc giới hạn vùng quét |
| Quét quá rộng gây bean lạ/xung đột | Base package là root chung quá lớn | Thu hẹp package roots, dùng marker/filters/explicit registration |
| Gõ `basePackages="...OrderService"` | Truyền tên class thay vì package | Đổi thành package hoặc dùng `basePackageClasses` |
| Đặt component ở thư mục source nhưng không xuất hiện runtime | Class chưa compile hoặc chưa nằm trên runtime classpath | Kiểm tra build output, source set và dependencies |

### Quy trình debug theo bằng chứng

**Bước 1 — Xác định loại lỗi.** Nếu `getBeanNamesForType(OrderService.class)` rỗng, kiểm tra đăng ký/candidate. Nếu tên có mặt nhưng tạo bean thất bại, điều tra constructor, factory, scope, post-processor hoặc module accessibility. Nếu nhiều tên xuất hiện, xử lý ambiguity bằng thiết kế injection/qualifier thích hợp.

**Bước 2 — So sánh các package thật.** Mở khai báo `package` của class component và package của configuration/Boot main. Vẽ sơ đồ root → subpackage. Đừng chỉ dựa vào vị trí file hiển thị trong IDE.

**Bước 3 — Đọc cấu hình scan đã kích hoạt.** Xác định `@ComponentScan` nằm trên class thực sự đã đăng ký/import hay chưa. Nếu có nhiều configuration class, kiểm tra từng lượt scan, không suy ra tất cả theo một annotation.

**Bước 4 — Kiểm tra điều kiện.** Xem `@Profile`, `@Conditional` và exclude/include filters; với Boot, điều kiện auto-config và profile active cũng cần được xét nếu bean phụ thuộc chúng.

**Bước 5 — Dùng context để kiểm tra thay vì đoán.** Ví dụ, trong context thử nghiệm:

```java
String[] names = context.getBeanNamesForType(OrderService.class);
System.out.println(java.util.Arrays.toString(names));
```

Đây là **đoạn minh họa** khi có sẵn biến `context` và class `OrderService`. Nó giúp xác định các bean đã đăng ký/nhìn thấy theo kiểu mà container có thể resolve. Với Spring Boot, báo cáo condition evaluation hoặc `/actuator/beans` (khi Actuator được thêm và endpoint được bật/phân quyền) có thể hỗ trợ, nhưng không phải điều kiện tiên quyết để debug Spring Core Container.

## 12. Những sai lầm về cấu trúc package cần tránh

### Sai lầm A: mọi class ở chung source root thì cùng được scan

`src/main/java` là một thư mục nguồn của build; `com.example.shop` và `org.example.shared` vẫn là hai Java package khác nhau. Chúng chỉ được Spring phát hiện trong **các root scan đã cấu hình**, không do cùng nằm dưới `src/main/java`.

### Sai lầm B: `basePackageClasses` quét “theo kiểu” của class

`basePackageClasses = OrderService.class` **không yêu cầu Spring chỉ tạo bean có kiểu `OrderService`**. Nó yêu cầu quét package chứa `OrderService`. Muốn chọn cụ thể một bean theo class, có thể đăng ký rõ với `@Import` hoặc `@Bean` khi phù hợp.

### Sai lầm C: quét package cha càng rộng càng tốt

Mở `@ComponentScan("com")` có thể đi qua nhiều dependency hoặc package ứng dụng không liên quan. Kết quả có thể là startup chậm, nhiều bean definition ngoài ý muốn, xung đột tên hoặc duplicate dependency. Chọn **package roots hẹp nhưng đủ bao phủ** là thiết kế tốt hơn.

### Sai lầm D: mở rộng scan nghĩa là đã cấu hình đủ mọi framework

JPA entity scanning, Spring Data repository scanning, MVC mapping, transaction management và security configuration có những cơ chế riêng. Một class `@RestController` được scan thành bean chưa có nghĩa URL mapping chạy nếu web context chưa cấu hình. Mở rộng component scan cũng không tự bật repository proxy hoặc quét entity.

### Sai lầm E: annotation luôn tạo bean tức thì

Component scanning có thể phát hiện class, nhưng instance chỉ được tạo theo lifecycle/scope và dependency resolution. `@Lazy`, prototype scope, điều kiện profile và những bean được tạo qua factory method có thể thay đổi thời điểm có instance. Khi debug, phân biệt **bean definition không có** với **bean có nhưng không tạo được**.

## 13. Bài tập thực hành có đáp án

**Bài 1 — Dời marker xuống package con.** Trong ví dụ mục 6, thay `ShopPackage.class` trong `ExpandedConfig` bằng `OrderService.class`, vẫn giữ `SharedPackage.class`. Dự đoán kết quả.

*Đáp án:* Spring chỉ quét `demo.shop.service` và `demo.shared`. `OrderRepository` ở `demo.shop.persistence` không được quét; tạo `OrderService` sẽ thiếu dependency `OrderRepository` và context refresh thường thất bại với `UnsatisfiedDependencyException`. Không thể chạy đến các dòng output của `expanded` nếu không bổ sung đăng ký repository.

**Bài 2 — Thêm package root.** Đổi `ShopOnlyConfig` thành:

```java
@ComponentScan(basePackages = {"demo.shop", "demo.shared"})
```

*Đáp án:* `shop.sharedEncoder = true` và có thể lấy bean `SharedEncoder` từ context `shop`. Các đoạn code khác không đổi; đây là ví dụ đổi phạm vi quét mà không di chuyển class.

**Bài 3 — Đưa configuration class sang package khác.** Giả sử `ShopOnlyConfig` chuyển từ `demo.app` sang `demo.config` nhưng vẫn giữ `@ComponentScan(basePackages="demo.shop")`. Nó còn quét `OrderService` không?

*Đáp án:* **Có**. Vì thuộc tính `basePackages` ghi rõ root scan, vị trí mới của configuration class không thay đổi root đã khai báo.

**Bài 4 — Xóa `basePackages`.** Giữ `ShopOnlyConfig` ở `demo.app` và đổi annotation thành `@ComponentScan` không tham số. Bean `OrderService` có được đăng ký tự động không?

*Đáp án:* **Không qua lượt scan này**. Scanner mặc định đi từ `demo.app` và các subpackage, trong khi `demo.shop` là sibling. Nếu không có cách đăng ký khác, việc `getBean(OrderService.class)` sẽ thất bại.

**Bài 5 — Chỉ thêm dependency Gradle.** Ứng dụng phụ thuộc module chứa `@Component` ở `com.example.payments` nhưng `@ComponentScan` chỉ quét `com.example.orders`. Dependency mới đủ để Spring tạo bean payment chưa?

*Đáp án:* **Chưa**. Dependency đảm bảo class khả dụng trên classpath; Spring còn cần base package phù hợp hoặc một cơ chế registration độc lập.

**Bài 6 — Entity/repository scanning trong Boot.** Đặt `@SpringBootApplication(scanBasePackages={"com.example.app","com.example.data"})` nhưng một `@Entity` dưới `com.example.data.model` chưa được JPA nhận diện. Có thể kết luận `@ComponentScan` hỏng không?

*Đáp án:* **Không**. `@Entity` không phải Spring component stereotype để tự được quét như `@Service`. Kiểm tra JPA entity scanning và cấu hình `@EntityScan` hoặc auto-configuration package; tương tự repository interface thuộc quy trình Spring Data.

**Bài 7 — Debug lỗi “bean có nhưng không inject được”.** Nếu `getBeanNamesForType(PaymentClient.class)` có hai tên, liệu mở rộng `@ComponentScan` hơn nữa có chắc sửa được lỗi?

*Đáp án:* **Không**. Khi đã có nhiều ứng viên, cần kiểm tra `@Qualifier`, `@Primary` hoặc thiết kế dependencies; mở rộng scan có thể làm ambiguity lớn hơn.

## 14. Checklist thiết kế và ghi nhớ

Trước khi khai báo `@ComponentScan`:

1. Ghi rõ **package root nào được quét** và package con nào chứa các component cần thiết.
2. Dùng `@ComponentScan` không tham số khi configuration class thật sự nằm ở root mong muốn; nếu không, khai báo `basePackages` hoặc ưu tiên `basePackageClasses` với marker đặt đúng root.
3. Giữ Boot main class ở package root phù hợp; chỉ mở rộng scan khi có module/package ngoài ranh giới mặc định.
4. Kiểm tra runtime classpath, stereotype, profile/condition và filters; việc quét đúng package chưa bảo đảm bean tạo được.
5. Phân biệt component scan với `@Bean`/`@Import`, Spring Data repository scanning và JPA entity scanning.
6. Đừng đặt scan root quá cao chỉ để tránh thiết kế package; quét hẹp, có chủ đích sẽ dễ bảo trì và kiểm thử hơn.

Quy tắc cốt lõi: **`@ComponentScan` quét từ một hoặc nhiều base package xuống các package con, không tự sang package cha hoặc nhánh ngang cấp.** Vị trí của class khai báo annotation chỉ quyết định root mặc định khi chưa chỉ rõ package. `basePackageClasses` cho phép dùng marker type-safe; `basePackages` dùng tên package bằng chuỗi. Quét đúng vùng classpath là một điều kiện để đăng ký bean, còn stereotype và các điều kiện khác quyết định class có thật sự được đăng ký hay không.

### Tài liệu Spring chính thức

- [Spring Framework Reference — Classpath Scanning and Managed Components](https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html).
- [Spring Framework Javadoc — `@ComponentScan`](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/context/annotation/ComponentScan.html).
- [Spring Framework Reference — Using `@Autowired`](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html).
- [Spring Boot Reference — Locating the Main Application Class](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html).
- [Spring Boot Javadoc — `@SpringBootApplication`](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/autoconfigure/SpringBootApplication.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="component-scanning-004">Khai báo basePackages và basePackageClasses</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo basePackages và basePackageClasses trong Spring Component Scan

## Mục tiêu

Spring Core Container có thể tìm các class được đánh dấu `@Component`, `@Service`, `@Repository`, `@Controller` hoặc stereotype tương thích, rồi tạo bean definition tương ứng mà không cần viết phương thức `@Bean` cho từng class. Cơ chế này gọi là **component scanning**. Tuy nhiên, Spring cần biết **bắt đầu tìm trong những package nào**.

Annotation `org.springframework.context.annotation.ComponentScan` cung cấp hai thuộc tính thường dùng để xác định phạm vi:

- `basePackages`: chỉ định **tên package dưới dạng chuỗi** (ví dụ `"com.example.orders"`).
- `basePackageClasses`: chỉ định **các class/interface làm mốc**; Spring lấy **package chứa các kiểu đó** làm điểm bắt đầu quét.

Bài này giúp bạn chọn đúng thuộc tính, phối hợp nhiều package, hiểu quét đệ quy qua package con, dùng marker interface an toàn khi refactor, khắc phục trường hợp thiếu bean và kiểm tra kết quả bằng ví dụ Java 17 + Spring Framework chạy được.

**Phạm vi:** Đây là cách cấu hình `@ComponentScan` trong **Spring Core Container**. Các cơ chế quét entity của JPA, Spring Data repository, hoặc đăng ký bean thủ công có quy tắc riêng; chúng chỉ được nhắc ngắn khi dễ gây nhầm.

## 1. Component scanning giải quyết vấn đề gì?

Xem cấu trúc package:

```text
com.example.shop
├── app
│   └── AppConfig.java
├── orders
│   ├── OrderService.java
│   └── internal
│       └── OrderValidator.java
└── shipping
    ├── ShippingService.java
    └── ShippingPackage.java
```

Giả sử `AppConfig` mang annotation `@Configuration` và `@ComponentScan`. Nếu không cấu hình thêm package, Spring sẽ bắt đầu quét tại **package của class khai báo `@ComponentScan`**, tức `com.example.shop.app`, bao gồm các package con của nó. Vì `orders` và `shipping` là package **anh em**, chúng không được tìm thấy bằng quét mặc định từ `app`.

Một nguyên nhân phổ biến của `NoSuchBeanDefinitionException` hoặc `UnsatisfiedDependencyException` là **component có thật nhưng nằm ngoài vùng quét**.

Bạn có thể giải quyết bằng một trong hai cách:

```java
// Khai báo bằng chuỗi tên package:
@ComponentScan(basePackages = {
    "com.example.shop.orders",
    "com.example.shop.shipping"
})
```

hoặc:

```java
// Dùng hai marker type đặt trong hai package đích:
@ComponentScan(basePackageClasses = {
    OrdersPackage.class,
    ShippingPackage.class
})
```

Đây là **các đoạn minh họa annotation**. Để sử dụng được, cần có `@Configuration` trên class cấu hình, import `org.springframework.context.annotation.ComponentScan` và các marker tương ứng.

Cả hai cách yêu cầu Spring quét **hai vùng package**; chúng không có nghĩa là lập danh sách cố định hai bean phải tạo.

## 2. Ý nghĩa chính xác của `basePackages`

Chữ ký thuộc tính trong `@ComponentScan`:

```java
String[] basePackages() default {};
```

Một số cách viết đúng:

```java
@Configuration
@ComponentScan(basePackages = "com.example.shop")
class ShopConfig {
}
```

```java
@Configuration
@ComponentScan(basePackages = {
    "com.example.shop.orders",
    "com.example.shop.shipping"
})
class ShopConfig {
}
```

Trong cả hai đoạn, import cần có là `org.springframework.context.annotation.Configuration` và `org.springframework.context.annotation.ComponentScan`. Các đoạn chỉ minh họa cấu hình; ví dụ chạy độc lập nằm ở mục 6.

### Package là tên Java, không phải đường dẫn file

Sử dụng `"com.example.shop.orders"`, không viết `"src/main/java/com/example/shop/orders"` hay `"com/example/shop/orders"`. Package trong annotation là tên logic được class khai báo và có mặt trên classpath, không phụ thuộc thư mục gốc của IDE.

Tên package **phân biệt chữ hoa và chữ thường**. Nếu code khai báo `package com.example.shop.orders;` thì chuỗi `"com.example.shop.Orders"` không trỏ vào package đó.

### Quét đệ quy qua package con

Khi chỉ định `"com.example.shop.orders"`, Spring xem cả những candidate dưới:

```text
com.example.shop.orders
com.example.shop.orders.internal
com.example.shop.orders.api
com.example.shop.orders.impl
```

Do đó, một package cha đủ hẹp thường bao phủ các subpackage thuộc cùng feature. **Package cùng cấp** như `com.example.shop.shipping` không được bao phủ nếu chỉ quét `com.example.shop.orders`.

Quét `"com.example.shop"` cũng bao gồm `orders`, `shipping` và `app`. Cách này tiện cho ứng dụng nhỏ, nhưng ở hệ thống lớn có thể đăng ký thêm những component không mong muốn nếu mọi module cùng nằm dưới package đó.

### `value` là alias của `basePackages`

`@ComponentScan` còn có thuộc tính `value()`, là alias của `basePackages()`. Khi chỉ ghi một đối số không tên:

```java
@ComponentScan("com.example.shop.orders")
```

nó có nghĩa tương đương:

```java
@ComponentScan(basePackages = "com.example.shop.orders")
```

Vì `value` và `basePackages` là **hai tên cho cùng một thuộc tính**, không nên đặt chúng thành hai danh sách độc lập hoặc khác nhau trên cùng annotation. Nếu muốn nhiều package, truyền một mảng vào **một** trong hai tên.

### Khi nào dùng `basePackages`?

Phù hợp khi:

- Package cần quét được quy định qua hợp đồng cấu hình hoặc cấu trúc ổn định.
- Muốn mô tả rõ từng tên package trong annotation.
- Cấu hình tích hợp với các vùng package mà project không có marker interface thuận tiện.

Hạn chế chính: tên package là **chuỗi**, nên công cụ refactor/đổi package không phải lúc nào cũng cập nhật chuỗi. Nếu class chuyển từ `com.example.shop.orders` sang `com.example.shop.sales.orders` mà annotation vẫn giữ chuỗi cũ, Spring có thể không tìm được bean mong muốn.

## 3. Ý nghĩa chính xác của `basePackageClasses`

Chữ ký thuộc tính trong `@ComponentScan`:

```java
Class<?>[] basePackageClasses() default {};
```

Với:

```java
@ComponentScan(basePackageClasses = ShippingPackage.class)
```

Spring tìm **package của `ShippingPackage`** và quét package đó cùng các package con. Nếu class marker nằm tại:

```java
package com.example.shop.shipping;

public interface ShippingPackage {
}
```

thì cơ chế có hiệu quả như chỉ định `"com.example.shop.shipping"`, miễn các kiểu đó tồn tại trên classpath. Marker interface **không cần** gắn `@Component`, vì mục đích của nó chỉ là làm mốc package.

Điều quan trọng: `basePackageClasses = ShippingService.class` **không nói rằng Spring chỉ đăng ký riêng `ShippingService`**. Nó yêu cầu quét **toàn bộ package chứa `ShippingService` và các subpackage**, theo điều kiện của scanner.

### Vì sao marker interface hữu ích?

Spring Javadoc gợi ý dùng một class hoặc interface không có hành vi, chuyên làm marker:

```java
package com.example.shop.orders;

/** Điểm neo package cho component scanning. */
public interface OrdersPackage {
}
```

Nếu có hai module feature tách biệt:

```java
@Configuration
@ComponentScan(basePackageClasses = {
    OrdersPackage.class,
    ShippingPackage.class
})
class ApplicationConfig {
}
```

Với import đầy đủ cho `OrdersPackage` và `ShippingPackage`, compiler kiểm tra rằng marker type tồn tại. Khi refactor package và di chuyển marker bằng công cụ IDE, các tham chiếu class có cơ hội được cập nhật/được compiler phát hiện, thay vì để một chuỗi package âm thầm trỏ sai.

Nên đặt marker ở **gốc package muốn quét**, ví dụ `com.example.shop.shipping.ShippingPackage`. Nếu marker vô tình nằm trong `com.example.shop.shipping.internal`, scanner bắt đầu từ `internal` và có thể bỏ sót những component tại `com.example.shop.shipping.api`.

### Có bắt buộc dùng marker riêng không?

Không. Bạn có thể chỉ định bất cứ class hoặc interface nào nằm trong package mục tiêu:

```java
@ComponentScan(basePackageClasses = {
    OrderService.class,
    ShippingService.class
})
```

Điều này hợp lệ nếu hai class đó tồn tại và được import; scanner lấy package của chúng làm mốc. Tuy nhiên, nếu `OrderService` chuyển xuống `orders.impl`, vùng quét có thể vô tình thu hẹp. Một marker đặt cố định ở package gốc thường truyền đạt rõ hơn rằng **đây là ranh giới của một feature**, không phải một class ngẫu nhiên.

### Những điều `basePackageClasses` không làm

- Không tự tạo bean cho marker interface không có stereotype.
- Không tự thêm `@Component` vào mọi class trong package.
- Không "tiêm" các class được liệt kê vào một service; việc tiêm dependency là bước khác.
- Không giới hạn scanner chỉ tới các class xuất hiện trong mảng.
- Không yêu cầu marker là implementation cụ thể; một interface trống cũng hợp lệ.

Nhiều marker ở cùng một package chỉ làm bạn khai báo thừa; thông thường một marker cho mỗi gốc package cần quét là đủ.

## 4. Chọn thuộc tính nào? Có được dùng cùng lúc?

Cả hai thuộc tính **có thể xuất hiện cùng nhau** trên một `@ComponentScan`:

```java
@Configuration
@ComponentScan(
    basePackages = "com.example.legacy",
    basePackageClasses = { OrdersPackage.class, ShippingPackage.class }
)
class ApplicationConfig {
}
```

Spring xây danh sách các package nguồn từ **cả hai thuộc tính**, và quét các vùng tương ứng. Hãy hiểu đây là **tập hợp các root package**, không phải điều kiện "package phải vừa trùng chuỗi, vừa chứa marker".

| Tiêu chí | `basePackages` | `basePackageClasses` |
| --- | --- | --- |
| Dạng giá trị | `String[]` | `Class<?>[]` |
| Ví dụ | `"com.example.orders"` | `OrdersPackage.class` |
| Chọn vùng quét | Tên package được chỉ định | Package chứa kiểu mốc |
| Package con | Có | Có |
| Type safety khi đổi cấu trúc source | Phụ thuộc việc cập nhật chuỗi | Compiler/type reference hỗ trợ nhận diện |
| Dễ đọc khi liệt kê package độc lập | Rõ tên trực tiếp | Rõ hơn khi đã có marker |
| Có thể kết hợp với thuộc tính kia | Có | Có |

**Gợi ý lựa chọn:** Với package thuộc codebase bạn kiểm soát, `basePackageClasses` cùng marker interface thường đáng ưu tiên vì ít phụ thuộc vào chuỗi tên package. Với gốc package ổn định, đơn giản và tường minh, `basePackages` cũng hoàn toàn phù hợp. Tránh biến lựa chọn này thành mục tiêu tự thân: điều quan trọng là vùng quét **đủ**, **đúng**, **dễ kiểm thử** và **không quá rộng**.

### Ví dụ nhiều package: ba cách tương đương về vùng quét

Giả sử `OrdersPackage` nằm trong `com.example.orders` và `ShippingPackage` nằm trong `com.example.shipping`:

```java
// Cách 1: toàn chuỗi.
@ComponentScan(basePackages = {
    "com.example.orders", "com.example.shipping"
})

// Cách 2: toàn marker.
@ComponentScan(basePackageClasses = {
    OrdersPackage.class, ShippingPackage.class
})

// Cách 3: phối hợp.
@ComponentScan(
    basePackages = "com.example.orders",
    basePackageClasses = ShippingPackage.class
)
```

Ba đoạn trên là **những phương án annotation riêng biệt**, không phải ba annotation phải đặt nối tiếp vào một class. Khi dùng phương án nào, các gốc package được chọn sẽ quyết định class nào được scanner xem xét, sau đó mới xét stereotype/filter và các điều kiện đăng ký bean khác.

## 5. Mặc định khi không khai báo gốc package

Khi một class cấu hình trong `com.example.shop.app` mang `@ComponentScan` không tham số, Spring quét từ **`com.example.shop.app` và các subpackage**. Nó không tự tìm sibling `com.example.shop.orders`. `@ComponentScan(basePackages={})` cũng dùng package mặc định khi không có root khác. Với dự án chứa nhiều feature, nên đặt configuration tại gốc phù hợp hoặc chọn roots tường minh qua chuỗi/marker. Tránh class cấu hình ở default package Java vì scan có thể quá rộng.

## 6. Ví dụ runnable Java 17: kết hợp hai thuộc tính

Ví dụ này dùng Spring Framework 6.2, không phụ thuộc Spring Boot. Ta cố ý đặt cấu hình trong `demo.app.config`, còn hai `@Service` ở hai package anh em. Cấu hình sử dụng:

- `basePackages = "demo.orders"` để tìm `OrderService`;
- `basePackageClasses = ShippingPackage.class` để tìm `ShippingService` trong `demo.shipping`.

### 6.1. Cấu trúc dự án

```text
component-scan-demo/
├── pom.xml
└── src/main/java/
    └── demo/
        ├── app/
        │   ├── Main.java
        │   └── config/
        │       └── AppConfig.java
        ├── orders/
        │   └── OrderService.java
        └── shipping/
            ├── ShippingPackage.java
            └── ShippingService.java
```

### 6.2. Maven POM

Tạo `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>component-scan-demo</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.18</version>
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

### 6.3. Class cấu hình

Tệp `src/main/java/demo/app/config/AppConfig.java`:

```java
package demo.app.config;

import demo.shipping.ShippingPackage;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(
        basePackages = "demo.orders",
        basePackageClasses = ShippingPackage.class
)
public class AppConfig {
}
```

Class `AppConfig` không phải là một service trong hai package cần scan. Nó được **đăng ký tường minh** khi tạo context ở `Main`. Annotation `@ComponentScan` mới chỉ định vùng để tìm các component còn lại.

### 6.4. Service thứ nhất được tìm bằng `basePackages`

Tệp `src/main/java/demo/orders/OrderService.java`:

```java
package demo.orders;

import org.springframework.stereotype.Service;

@Service
public class OrderService {
    public String label() {
        return "orders";
    }
}
```

Vì `demo.orders` là gốc package được chỉ định bằng chuỗi, scanner tìm được `OrderService` gắn `@Service`. `@Service` là stereotype của Spring và được component scan nhận diện theo cấu hình mặc định.

### 6.5. Marker và service thứ hai được tìm bằng `basePackageClasses`

Tệp `src/main/java/demo/shipping/ShippingPackage.java`:

```java
package demo.shipping;

/** Marker xác định gốc package shipping cho @ComponentScan. */
public interface ShippingPackage {
}
```

Tệp `src/main/java/demo/shipping/ShippingService.java`:

```java
package demo.shipping;

import org.springframework.stereotype.Service;

@Service
public class ShippingService {
    public String label() {
        return "shipping";
    }
}
```

`ShippingPackage` là interface trống, không mang `@Component` và không trở thành bean service chỉ vì được dùng trong `basePackageClasses`. Điều quan trọng là interface này nằm trong `demo.shipping`; Spring từ đó tìm `ShippingService` đã mang stereotype `@Service`.

### 6.6. Main class và kết quả

Tệp `src/main/java/demo/app/Main.java`:

```java
package demo.app;

import demo.app.config.AppConfig;
import demo.orders.OrderService;
import demo.shipping.ShippingService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService orders = context.getBean(OrderService.class);
            ShippingService shipping = context.getBean(ShippingService.class);

            System.out.println("orders=" + orders.label());
            System.out.println("shipping=" + shipping.label());
            System.out.println("markerBean=" +
                    context.containsBean("shippingPackage"));
        }
    }
}
```

Từ thư mục chứa `pom.xml`, chạy với JDK 17+ và Maven:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.app.Main
```

Các dòng do chương trình in ra (không tính logging của môi trường Maven/Spring):

```text
orders=orders
shipping=shipping
markerBean=false
```

**Vì sao output chính xác?**

1. `AppConfig` được truyền vào `AnnotationConfigApplicationContext`, nên không cần scanner tự phát hiện nó.
2. `@ComponentScan(basePackages = "demo.orders")` bao phủ `OrderService`.
3. `@ComponentScan(basePackageClasses = ShippingPackage.class)` lấy package `demo.shipping` làm gốc, nên bao phủ `ShippingService`.
4. `context.getBean(OrderService.class)` và `context.getBean(ShippingService.class)` đều thành công vì có bean đúng kiểu.
5. Marker interface không có stereotype và không có bean definition tường minh nên `containsBean("shippingPackage")` là `false`.

Bạn có thể thay hoàn toàn `AppConfig` bằng phương án marker cho cả hai package nếu tạo thêm `OrdersPackage` trong `demo.orders`:

```java
// Phương án minh họa, cần khai báo interface OrdersPackage
// nằm trong demo.orders, và import cả hai marker.
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        OrdersPackage.class, ShippingPackage.class
})
public class AppConfig {
}
```

Hoặc thay bằng mảng chuỗi:

```java
// Phương án minh họa khác, thay thế AppConfig ở ví dụ:
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = {
        "demo.orders", "demo.shipping"
})
public class AppConfig {
}
```

Các phương án này có cùng ý định chọn **hai gốc package**; không dùng cùng lúc nhiều class `AppConfig` trùng tên trong một project.

## 7. Nếu bỏ một thuộc tính thì chuyện gì xảy ra?

Thử sửa `AppConfig` để chỉ còn:

```java
@ComponentScan(basePackages = "demo.orders")
```

Khi đó scanner không tìm `ShippingService` ở `demo.shipping`, trừ khi bạn đăng ký nó bằng cơ chế khác. Nếu vẫn chạy `Main` như cũ, lời gọi:

```java
context.getBean(ShippingService.class);
```

sẽ không tìm được bean tương ứng và gây `NoSuchBeanDefinitionException`. Đây là hành vi mong đợi: component nằm ngoài vùng quét. Nó không có nghĩa Spring không hiểu `@Service` hoặc Java class không tồn tại.

Ngược lại, nếu chỉ có:

```java
@ComponentScan(basePackageClasses = ShippingPackage.class)
```

`OrderService` nằm ngoài gốc `demo.shipping`; vì vậy `getBean(OrderService.class)` thất bại nếu bean không được đăng ký bằng đường khác.

Thử nghiệm này cho thấy hai thuộc tính **bổ sung vùng quét**. Khi cùng xuất hiện, cả hai tập package được xét; khi bỏ một bên, vùng quét tương ứng biến mất.

### Kiểm tra chỉ bằng BeanFactory

Nếu muốn quan sát bean đã đăng ký nhưng không muốn để `getBean` ném lỗi:

```java
// Đoạn minh họa trong main/context đã tạo:
boolean hasOrders = context.containsBean("orderService");
boolean hasShipping = context.containsBean("shippingService");
```

Với `@Service` không ghi tên cụ thể, Spring thường dùng bean name mặc định theo class đơn giản đã chuyển chữ đầu thành thường: `orderService` và `shippingService`. Nhưng tên có thể khác khi dùng `@Service("customName")` hoặc `BeanNameGenerator` tùy chỉnh. Vì vậy khi kiểm tra ứng viên theo kiểu, `getBeanNamesForType` hoặc `getBeansOfType` thường phản ánh mục tiêu rõ hơn:

```java
// Đoạn minh họa: dùng khi context còn mở.
String[] shippingNames =
        context.getBeanNamesForType(ShippingService.class);
```

Khi không tìm thấy bean, mảng có thể rỗng. **Đừng chỉ dựa vào tên** nếu mục tiêu là xác nhận class đúng kiểu đã được đăng ký.

## 8. Scanner phát hiện class nào?

`basePackages` và `basePackageClasses` chỉ quyết định **nơi tìm**, không buộc mọi class trở thành bean. Với `useDefaultFilters=true`, Spring xét stereotype `@Component`, `@Service`, `@Repository`, `@Controller` và composed stereotypes trên concrete, independent classes. Marker interface không mang stereotype chỉ là mốc package.

Khi thiếu bean, kiểm tra runtime classpath, filters, `@Profile`/`@Conditional`, dependency, lỗi bean name và context đang dùng. Bean ngoài vùng scan vẫn có thể xuất hiện qua `@Bean`/`@Import`. Tắt `useDefaultFilters` yêu cầu bổ sung include filter rõ ràng; chỉ gắn `@Service` không còn đủ để được scanner chọn.

## 9. Quét vừa đủ: tránh quá hẹp hoặc quá rộng

Nếu module checkout có các nhánh `com.company.checkout.api`, `application`, `domain`, `infrastructure`, marker ở `com.company.checkout` sẽ bao phủ toàn bộ feature. Marker đặt sâu dưới `...checkout.application.internal` lại bỏ các package ngang cấp; scan từ `com.company` có thể kéo vào nhiều module không liên quan.

Chọn **root package đủ rộng cho dependency hợp lệ và đủ hẹp cho đúng phạm vi module**. Trong multi-project Gradle/JAR, scanner vẫn đọc Java packages trên runtime classpath, không đọc tên Gradle module để thiết lập ranh giới. Khi library công bố một adapter trong số nhiều component nội bộ, explicit `@Import`/`@Bean` có thể diễn đạt lựa chọn rõ hơn một package scan rộng.

## 10. Những chi tiết quan trọng về refactor và module

### Khi đổi package trong IDE

Với chuỗi:

```java
@ComponentScan(basePackages = "com.company.oldname")
```

nếu package được đổi thành `com.company.newname` mà annotation chưa cập nhật, mã vẫn **biên dịch** vì chuỗi bất kỳ đều hợp lệ về cú pháp Java. Vấn đề chỉ lộ khi context chạy và bean mong muốn thiếu.

Với marker:

```java
@ComponentScan(basePackageClasses = NewModulePackage.class)
```

compiler kiểm tra tham chiếu `NewModulePackage`. Nếu marker di chuyển và import chưa được sửa, mã sẽ lỗi biên dịch. Nếu marker được chuyển cùng package và import được cập nhật, scanner theo package mới. Đây là lợi thế "type-safe" của cách khai báo này — dù nó **không** tự bảo đảm mọi bean cần thiết nằm trong vùng mới.

### Tách package qua nhiều module/JAR

Nếu `OrdersPackage` ở module phụ thuộc, `basePackageClasses = OrdersPackage.class` chỉ làm mốc khi type đó **có mặt trên classpath** của ứng dụng. Việc quét package sẽ tìm các candidate trong classpath theo cơ chế Spring; nó **không tự thêm dependency Maven/Gradle** tới module chứa class hoặc tự tải JAR từ network.

Vì vậy, với dự án đa module, cần cả:

1. Runtime/build dependency đúng để các class có trên classpath.
2. Cấu hình scan đúng package.
3. Điều kiện để các class thực sự là component ứng viên.

Nếu marker đã import được mà một implementation nằm trong module chưa đưa lên runtime classpath, việc quét vẫn không thể phát hiện implementation đó.

### Đặt marker ở đâu?

Một convention đơn giản:

```text
com.company.module.orders
├── OrdersPackage.java       <-- marker, không @Component
├── api/...
├── application/...
└── infrastructure/...
```

Marker ít thay đổi, nằm ở package cha chung và có tên thể hiện ý định. Không đặt marker trong package `impl` chỉ vì ở đó hiện có một service, trừ khi chỉ muốn quét đúng package đó.

## 11. Lưu ý khi sử dụng Spring Boot

Spring Boot đã tích hợp component scan vào `@SpringBootApplication`. Để tùy chỉnh gốc, dùng tên thuộc tính riêng:

| `@ComponentScan` | `@SpringBootApplication` | Mốc lựa chọn |
| --- | --- | --- |
| `basePackages` | `scanBasePackages` | Chuỗi package |
| `basePackageClasses` | `scanBasePackageClasses` | Marker type |

```java
@SpringBootApplication(scanBasePackageClasses = {
    OrdersPackage.class, ShippingPackage.class
})
public class ShopApplication { }
```

Snippet cần imports, marker types và dependency Boot. Khi không tùy chỉnh, Spring Boot thường quét package chứa class ứng dụng và các subpackage. **`scanBasePackages` không đổi phạm vi JPA entity scanning hay Spring Data repository scanning**: cần kiểm tra `@EntityScan`, `@EnableJpaRepositories` hoặc cơ chế tương ứng khi dùng các công nghệ đó. Việc thêm `@ComponentScan` trực tiếp lên class Boot có thể thay đổi composed scan filters mặc định; với nhu cầu đổi root đơn giản, dùng thuộc tính sẵn có của `@SpringBootApplication`.

## 12. Những lỗi phổ biến và cách sửa

| Hiện tượng | Nguyên nhân khả dĩ | Cách xử lý |
| --- | --- | --- |
| `NoSuchBeanDefinitionException` cho service có `@Service` | Service nằm ngoài package gốc | Xác minh package và mở rộng `basePackages`/`basePackageClasses` có chủ đích |
| Context khởi tạo thất bại với `UnsatisfiedDependencyException` | Một bean phụ thuộc vào service chưa được đăng ký, hoặc dependency nội bộ của service bị thiếu | Đọc exception chain, xác định **bean nào** thực sự thiếu |
| Chỉ quét được package shipping, thiếu orders | Chỉ khai báo marker trong shipping | Thêm gốc orders bằng chuỗi hoặc marker tương ứng |
| Dùng `basePackageClasses = SomeService.class` nhưng thiếu class cùng feature | `SomeService` nằm trong subpackage hẹp | Đặt marker ở gốc feature |
| Dùng `"src/main/java/..."` | Dùng đường dẫn thay vì tên Java package | Đổi thành `"com.example...."` |
| Refactor package xong mất bean dù vẫn biên dịch | Chuỗi `basePackages` cũ | Cập nhật chuỗi hoặc dùng marker type |
| Marker không được tạo thành bean | Marker interface không có stereotype | Đây là chủ đích; marker chỉ định package quét, không tự là component |
| Có nhiều bean cùng interface hơn dự kiến | Root scan quá rộng, quét cả module ngoài phạm vi | Thu hẹp gốc, điều chỉnh cấu trúc/qualifier thích hợp |
| Bị xung đột bean name | Hai component cùng tên bean sau khi quét rộng | Đặt tên rõ ràng, xử lý cấu hình/module, không chỉ bỏ qua lỗi |
| Không tìm thấy entity/repository JPA trong Boot | Nhầm component scan với entity/repository scan | Cấu hình cơ chế scan riêng phù hợp từng công nghệ |
| Không thấy bean dù package đúng | Thiếu runtime JAR, thiếu stereotype, exclude filter, condition | Kiểm tra classpath và metadata component |
| Scan thay đổi khi di chuyển class cấu hình | Dựa vào gốc scan mặc định | Đặt `@ComponentScan` tại package gốc ổn định hoặc chỉ định root rõ ràng |

### Quy trình debug ngắn

1. Xác định **tên package thật** từ dòng `package ...;` trong file Java.
2. Xác định **class khai báo `@ComponentScan` có được đưa vào ApplicationContext** không.
3. Tính các root scan: từ `basePackages`, package của từng type trong `basePackageClasses`, hoặc mặc định package của config nếu không chỉ định.
4. Kiểm tra service có thuộc đúng root/subpackage nào không.
5. Kiểm tra class có stereotype và được đưa lên runtime classpath không.
6. Nếu vẫn thất bại, đọc exception gốc và kiểm tra filter, condition, dependency của bean và xung đột tên.

Để chẩn đoán sâu, có thể bật log mức DEBUG cho các package của Spring context/scanner trong môi trường phát triển. Tránh đọc một thông điệp "không tìm được bean" như bằng chứng duy nhất rằng package scan sai: dependency bên trong class hoặc điều kiện đăng ký cũng có thể là nguyên nhân.

## 13. Thực hành và tự kiểm tra

### Bài 1 — Mặc định quét ở đâu?

`AppConfig` nằm ở `com.example.shop.app` và có:

```java
@Configuration
@ComponentScan
class AppConfig { }
```

`OrderService` ở `com.example.shop.orders`. Nó có được tìm thấy chỉ nhờ `@ComponentScan` này không?

**Đáp án:** Không, vì `orders` là package anh em với `app`. Gốc mặc định là `com.example.shop.app` và các package con của nó. Muốn quét `orders`, cần chuyển gốc scan thích hợp hoặc khai báo package này.

### Bài 2 — Marker có được đưa thành bean?

`ShippingPackage` là interface trống ở `demo.shipping`, không có stereotype. `@ComponentScan(basePackageClasses = ShippingPackage.class)` có tạo bean tên `shippingPackage` không?

**Đáp án:** Không. Annotation lấy **package của marker** làm mốc quét; marker không trở thành component chỉ vì được tham chiếu ở thuộc tính đó.

### Bài 3 — Kết hợp hai thuộc tính

`@ComponentScan(basePackages = "demo.orders", basePackageClasses = ShippingPackage.class)` quét những vùng nào, nếu `ShippingPackage` nằm trong `demo.shipping`?

**Đáp án:** `demo.orders`, `demo.shipping` và các package con của từng vùng. Không cần service nằm trong **cả hai** package.

### Bài 4 — Thay marker bằng implementation con

Nếu đổi `basePackageClasses = ShippingPackage.class` thành `basePackageClasses = InternalShippingService.class`, nhưng `InternalShippingService` thuộc `demo.shipping.internal`, vùng quét còn tìm `demo.shipping.ShippingService` không?

**Đáp án:** Không chỉ từ cấu hình này, vì gốc mới bị thu hẹp xuống `demo.shipping.internal`. Nếu có một gốc scan khác bao phủ `demo.shipping` thì có thể vẫn thấy bean. Để ổn định, đặt marker tại đúng package gốc.

### Bài 5 — Boot và JPA repository

Có `@SpringBootApplication(scanBasePackages = "com.example.orders")` nhưng entity hoặc repository Spring Data ở một vùng khác vẫn không được phát hiện. Có thể chỉ thêm package vào `scanBasePackages` để bảo đảm entity và repositories được quét không?

**Đáp án:** Không. Thuộc tính đó ánh xạ vào `@ComponentScan`; phạm vi quét entity và Spring Data repository có cơ chế riêng. Cần kiểm tra cấu hình tương ứng như `@EntityScan` và `@Enable...Repositories`.

### Bài 6 — Chọn phương án dễ refactor

Một project có module `com.company.checkout` chứa nhiều subpackage và bạn sở hữu toàn bộ source. Nên dùng `@ComponentScan(basePackages = "com.company.checkout")` hay marker `CheckoutPackage.class`?

**Đáp án:** Cả hai hợp lệ. Marker tại gốc module hữu ích khi refactor vì tham chiếu class được compiler kiểm tra và IDE thường hỗ trợ di chuyển package. Nếu dùng chuỗi, cần bảo đảm test cập nhật phạm vi khi đổi package.

## 14. Checklist cấu hình thực tế

Trước khi hoàn tất thay đổi `@ComponentScan`, hãy kiểm tra:

- Đã xác định đúng **root package** tương ứng với module/feature muốn quét.
- `basePackages` là tên Java package hợp lệ về mặt dự án, không phải path filesystem.
- Marker của `basePackageClasses` nằm trong **package cha đúng mong muốn**.
- Khi cần nhiều package, đã khai báo đủ các root hoặc root cha thích hợp.
- Không vô tình quét cả các module/test component không thuộc ứng dụng.
- Các class cần dùng có stereotype và có trên runtime classpath.
- Class `@Configuration` được đăng ký/import vào container.
- Nếu ứng dụng Boot, sử dụng tên thuộc tính tương ứng và không nhầm component scan với JPA/repository scan.
- Có integration test hoặc một lần tạo `AnnotationConfigApplicationContext` xác minh bean cốt lõi thực sự có mặt.

## Ghi nhớ

**`basePackages` chọn bằng chuỗi; `basePackageClasses` chọn bằng type làm mốc.** Cả hai đều cấu hình vùng package cho `@ComponentScan` và có thể dùng kết hợp. `basePackageClasses` lấy **package chứa class/interface**, không chỉ quét class đó và không tự biến marker thành bean. Nếu không khai báo root, Spring lấy package của class mang `@ComponentScan` làm gốc.

Một cấu hình tốt chọn đúng module, bao phủ đủ package con, tránh root quá rộng và có kiểm chứng bean thực sự được đăng ký. Khi source thường xuyên refactor, marker interface đặt tại gốc feature là lựa chọn đáng cân nhắc.

## Tài liệu chính thức

- [Spring Framework 6.2 — Classpath Scanning and Managed Components](https://docs.spring.io/spring-framework/reference/6.2/core/beans/classpath-scanning.html): cách bật component scanning và các stereotype được nhận diện.
- [Spring Framework 6.2 — `@ComponentScan` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/context/annotation/ComponentScan.html): `basePackages`, `basePackageClasses`, alias `value` và mặc định package.
- [Spring Framework 6.2 — Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java.html): `@Configuration`, `@Bean` và `AnnotationConfigApplicationContext`.
- [Spring Boot — `@SpringBootApplication` Javadoc](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/autoconfigure/SpringBootApplication.html): `scanBasePackages`/`scanBasePackageClasses` và phạm vi áp dụng của chúng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="component-scanning-005">Bộ lọc include, exclude và useDefaultFilters</a>

<details>
<summary>Xem chi tiết</summary>

# Bộ lọc include, exclude và useDefaultFilters trong Spring Component Scan

## 1. Mục tiêu bài học

Trong Spring Core Container, `@ComponentScan` không nhất thiết phải đăng ký mọi class được đánh dấu `@Component`. Ta có thể **bổ sung loại class được tìm thấy**, **loại bỏ loại class không muốn đăng ký**, hoặc **tắt hoàn toàn bộ lọc stereotype mặc định**.

Sau bài này, bạn có thể:

- Giải thích ý nghĩa của `includeFilters`, `excludeFilters` và `useDefaultFilters` trên `@ComponentScan`.
- Dự đoán chính xác một class có được scan thành bean hay không khi các filter cùng áp dụng.
- Sử dụng `FilterType.ANNOTATION`, `ASSIGNABLE_TYPE`, `REGEX`, `ASPECTJ` và `CUSTOM` đúng phạm vi.
- Hiểu khác biệt giữa “lọc ứng viên được component scanner phát hiện” và “ngăn mọi cách đăng ký bean”.
- Viết cấu hình Java 17+ chạy được, trong đó class có annotation riêng được chọn, một class khác bị loại, và `@Service` thông thường không được đăng ký vì `useDefaultFilters=false`.
- Chẩn đoán lỗi thiếu bean, scan quá rộng, regex sai tên đầy đủ, custom `TypeFilter` lỗi và trùng bean.

**Phạm vi:** bài học chỉ nói đến bộ lọc của **component scanning** trong Spring IoC Container. Nó không thay thế `@Profile`, `@Conditional`, cấu hình Spring Data repository scanning hoặc JPA entity scanning.

## 2. Bộ lọc mặc định của component scan là gì?

Khi sử dụng:

```java
@Configuration
@ComponentScan(basePackages = "com.example.shop")
public class AppConfig {}
```

Spring tìm các class trong package `com.example.shop` và các package con, rồi áp dụng **bộ lọc mặc định**. Theo cấu hình Spring Framework thông thường, bộ lọc này nhận diện `@Component` và các annotation được meta-annotate bởi `@Component`, tiêu biểu:

- `@Service`;
- `@Repository`;
- `@Controller`;
- `@RestController` (thông qua chuỗi meta-annotation);
- `@Configuration`;
- annotation do ứng dụng tạo với `@Component` làm meta-annotation.

Nếu ứng dụng có Jakarta Inject phù hợp, Spring còn có thể hỗ trợ annotation `@Named` thông qua bộ lọc mặc định tương ứng. Trong bài, ta tập trung vào mô hình `@Component`.

**Phân biệt hai bước:**

1. **Phát hiện ứng viên:** scanner dùng package và các bộ lọc để xác định class nào phù hợp.
2. **Đăng ký/tạo bean:** container xử lý bean definition, scope, điều kiện cấu hình, phụ thuộc và vòng đời. Class phù hợp filter vẫn cần là ứng viên hợp lệ và phải có thể được container đăng ký.

Một `interface` thuần khớp `ASSIGNABLE_TYPE` không có nghĩa Spring tự khởi tạo interface làm component. Type filter còn phải đi qua các kiểm tra ứng viên hợp lệ.

## 3. Ba thuộc tính quan trọng của @ComponentScan

Giả sử cấu hình:

```java
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(
    basePackages = "com.example.shop",
    useDefaultFilters = false,
    includeFilters = @ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ANNOTATION,
        classes = SpecialModule.class
    ),
    excludeFilters = @ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = LegacyAdapter.class
    )
)
public class AppConfig {}
```

Đây là ví dụ **cấu trúc**: `SpecialModule` là annotation tự định nghĩa và `LegacyAdapter` là class có thực trong ứng dụng, chưa được khai báo ở đây. Đừng copy riêng đoạn này rồi kỳ vọng biên dịch khi thiếu hai kiểu.

Ý nghĩa:

- `basePackages`: giới hạn **vùng cần quét**. Filter không tìm class ở ngoài vùng này.
- `useDefaultFilters=false`: không tự động coi stereotype `@Component`, `@Service`, `@Repository`, `@Controller`,… là đủ để được chọn.
- `includeFilters`: thêm tiêu chí cho class **đủ điều kiện được nhận diện**. Trong ví dụ, class mang `@SpecialModule` có thể được đưa vào tập ứng viên kể cả khi annotation này không meta-annotated bởi `@Component`.
- `excludeFilters`: loại class khớp điều kiện; trong ví dụ, class có kiểu `LegacyAdapter` bị loại khỏi đợt scan đó.
- `@Configuration` và `@Bean` được đăng ký rõ ràng vẫn có thể tạo bean độc lập với việc class đó có được scanner chọn hay không.

### Bảng hành vi quan trọng

| `useDefaultFilters` | `includeFilters` | `excludeFilters` | Kết quả cơ bản |
| --- | --- | --- | --- |
| `true` (mặc định) | Không khai báo | Không khai báo | Nhận component/stereotype mặc định trong package scan. |
| `true` | Có | Không | Nhận **ứng viên mặc định HOẶC ứng viên match include**. |
| `true` | Có | Có | Nhận ứng viên từ mặc định/include, **trừ** những loại match exclude. |
| `false` | Không | Không | Không có tiêu chí include mặc định để tìm component thông thường. |
| `false` | Có | Không | Chỉ nhận ứng viên match include trong package scan. |
| `false` | Có | Có | Chỉ nhận ứng viên match include **và không match exclude**. |

Điểm rất hay nhầm là `includeFilters` **không tự biến scan thành whitelist** khi `useDefaultFilters` vẫn là `true`. Nó **mở rộng** tập mà filter mặc định tìm thấy. Muốn danh sách dựa hẳn trên tiêu chí do bạn khai báo, cần cân nhắc `useDefaultFilters=false`.

## 4. Mô hình lựa chọn ứng viên

Đối với mỗi class nằm trong phạm vi scan và được xem xét bởi scanner, có thể hình dung điều kiện filter:

```text
Bắt đầu với một class trong base package
               |
               v
      Có match exclude?
         /           \
       Có             Không
       |                |
  Bỏ qua class          v
                   Match include?
                    /        \
                  Có         Không
                  |             |
               Chọn*            v
                       useDefaultFilters=true
                       và match mặc định?
                         /         \
                        Có          Không
                        |              |
                      Chọn*          Bỏ qua

* Sau đó còn kiểm tra điều kiện ứng viên bean hợp lệ.
```

Sơ đồ thể hiện kết quả logic: **exclude thắng include/default** trong cùng quá trình scan. Ở bản triển khai, scanner có thể kiểm tra exclude trước và dừng sớm. Đừng hiểu sơ đồ là thứ tự gọi từng matcher theo đúng hình vẽ.

Ta có thể viết công thức mô hình:

```text
eligibleByFilters =
    inBasePackage
    AND NOT excluded
    AND (
        matchesAnyInclude
        OR (useDefaultFilters AND matchesDefault)
    )
```

Công thức chỉ mô tả bộ lọc, **chưa** bao gồm các bước kiểm tra concrete/independent candidate, các điều kiện như `@Conditional`, xử lý bean name hay bean definition đã đăng ký.

### Nhiều include và exclude

Trong một mảng `includeFilters`, nhiều bộ lọc thường kết hợp theo phép **OR**: class chỉ cần khớp một bộ lọc include. Tương tự, nhiều loại trừ có tác dụng loại class nếu **bất kỳ** exclude filter nào khớp.

Do đó:

```java
includeFilters = {
    @ComponentScan.Filter(
        type = FilterType.ANNOTATION, classes = FeatureA.class),
    @ComponentScan.Filter(
        type = FilterType.ANNOTATION, classes = FeatureB.class)
}
```

có nghĩa “FeatureA **hoặc** FeatureB”, không phải “phải đồng thời mang cả hai”. Đây là đoạn annotation minh họa, cần import `ComponentScan`, `FilterType` và khai báo hai annotation nghiệp vụ.

Nếu cần điều kiện phức tạp “A **và** B”, có thể dùng `FilterType.CUSTOM` để kiểm tra cả hai metadata trong một `TypeFilter`, hoặc sắp xếp thiết kế package/annotation sao cho rõ ràng hơn.

## 5. useDefaultFilters: true và false

### 5.1. Mặc định true: vẫn quét các stereotype

```java
@Configuration
@ComponentScan(
    basePackages = "com.example.shop",
    includeFilters = @ComponentScan.Filter(
        type = FilterType.ANNOTATION,
        classes = Experimental.class
    )
)
class AppConfig {}
```

Giả định `Experimental` là annotation do ứng dụng định nghĩa. Với `useDefaultFilters=true` mặc định:

- `@Service StandardService` trong package vẫn là ứng viên;
- `@Repository StandardRepository` vẫn là ứng viên;
- class `@Experimental PreviewEngine` có thể được nhận nếu filter khớp, **dù `@Experimental` không phải meta-annotation của `@Component`**;
- class không khớp cả default lẫn include sẽ bị bỏ qua.

**Sai lầm:** “Thêm `includeFilters = Experimental` là Spring chỉ còn quét class Experimental.” Điều này sai khi chưa tắt mặc định.

### 5.2. useDefaultFilters=false: chỉ giữ tiêu chí tự khai báo

```java
@Configuration
@ComponentScan(
    basePackages = "com.example.shop",
    useDefaultFilters = false,
    includeFilters = @ComponentScan.Filter(
        type = FilterType.ANNOTATION,
        classes = Experimental.class
    )
)
class AppConfig {}
```

Lúc này, class chỉ có `@Service` hoặc `@Component` mà không mang `@Experimental` sẽ không được scanner này chọn. Lưu ý, không nhất thiết **mọi bean** trong context đều mất: bean từ `@Bean`, `@Import`, class cấu hình đăng ký trực tiếp và cơ chế khác vẫn có thể tồn tại.

### 5.3. Tắt mặc định mà quên include

```java
@ComponentScan(
    basePackages = "com.example.shop",
    useDefaultFilters = false
)
```

Với cách này, scanner không có bộ lọc stereotype mặc định và không được cấp include filter nào; kết quả là **không tự tìm thấy các component thông thường** trong vùng quét. Cấu hình này hiếm khi là ý định của ứng dụng. Nếu `@Configuration` được đưa trực tiếp vào `AnnotationConfigApplicationContext`, bản thân nó vẫn tồn tại; đừng nhầm điều đó với việc scanner tìm thấy service.

### 5.4. Khi nào nên giữ mặc định?

Phần lớn ứng dụng nên để `useDefaultFilters=true` vì cấu trúc package và stereotype đã mô tả rõ các component. Chỉ dùng `false` khi muốn một cơ chế chọn thành phần chủ động, ví dụ triển khai plugin với annotation chuyên biệt, tách bộ adapter thử nghiệm khỏi thành phần thông thường, hoặc dựng context rất hẹp phục vụ công cụ.

Đừng dùng bộ lọc phức tạp chỉ để thay thế việc tổ chức package rõ ràng.

## 6. Ví dụ Java 17+ hoàn chỉnh: include + exclude + tắt default

Chương trình này **không cần Spring Boot**. Nó dùng Spring Framework 6.2.9, Java 17 và `AnnotationConfigApplicationContext`. Tất cả class đặt trong cùng một file Java để dễ chạy và quan sát chính xác.

Mục đích:

1. `PilotJob` mang annotation `@Pilot` **không** có `@Component` vẫn được đưa vào context do include filter.
2. `DirectJob` **không có annotation stereotype** vẫn được chọn do `ASSIGNABLE_TYPE`.
3. `BlockedJob` mang `@Pilot` nhưng bị exclude, chứng minh exclude thắng include.
4. `RegularJob` có `@Service` nhưng bị bỏ qua vì `useDefaultFilters=false`.

### 6.1. Cấu trúc dự án

```text
filter-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/FilterDemo.java
```

**`settings.gradle`**

```groovy
rootProject.name = 'filter-demo'
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
    implementation 'org.springframework:spring-context:6.2.9'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.FilterDemo'
}
```

**`src/main/java/demo/FilterDemo.java`**

```java
package demo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.TreeSet;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Service;

public class FilterDemo {
    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(ScanConfig.class)) {

            System.out.println("pilot=" + ctx.containsBean("pilotJob"));
            System.out.println("direct=" + ctx.containsBean("directJob"));
            System.out.println("blocked=" + ctx.containsBean("blockedJob"));
            System.out.println("regular=" + ctx.containsBean("regularJob"));

            var jobs = ctx.getBeansOfType(Job.class);
            System.out.println("selected=" +
                    String.join(",", new TreeSet<>(jobs.keySet())));

            System.out.println("run=" +
                    jobs.get("pilotJob").run() + "|" +
                    jobs.get("directJob").run());
        }
    }
}

interface Job {
    String run();
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@interface Pilot {
}

// Chọn qua ANNOTATION, dù @Pilot không meta-annotated @Component.
@Pilot
class PilotJob implements Job {
    @Override
    public String run() {
        return "pilot";
    }
}

// Chọn qua ASSIGNABLE_TYPE, không cần @Component.
class DirectJob implements Job {
    @Override
    public String run() {
        return "direct";
    }
}

// Match @Pilot nhưng bị exclude theo class.
@Pilot
class BlockedJob implements Job {
    @Override
    public String run() {
        return "blocked";
    }
}

// Có @Service nhưng bộ lọc default đang tắt.
@Service
class RegularJob implements Job {
    @Override
    public String run() {
        return "regular";
    }
}

@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackageClasses = FilterDemo.class,
    useDefaultFilters = false,
    includeFilters = {
        @ComponentScan.Filter(
            type = FilterType.ANNOTATION,
            classes = Pilot.class
        ),
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = DirectJob.class
        )
    },
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = BlockedJob.class
    )
)
class ScanConfig {
}
```

Các class `Job`, `PilotJob`, `DirectJob`, `BlockedJob`, `RegularJob` và `ScanConfig` là các top-level type không `public` trong file; Java cho phép điều này. Lớp `FilterDemo` duy nhất là `public` và tên file đúng tên class. Spring có thể phát hiện các class top-level độc lập trong package `demo`; việc không ghi `public` không biến chúng thành inner class.

`basePackageClasses=FilterDemo.class` chỉ dùng package của `FilterDemo` làm gốc quét. Class `FilterDemo` không tự được chọn vì không có filter phù hợp.

### 6.2. Chạy chương trình

Từ thư mục `filter-demo`, với Gradle và JDK 17+:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper, dùng `./gradlew run --quiet` trên Linux/macOS hoặc `gradlew.bat run --quiet` trên Windows. `javac FilterDemo.java` riêng lẻ không đủ vì thiếu Spring JAR; Gradle sẽ resolve dependency rồi biên dịch/chạy class `demo.FilterDemo`.

**Stdout dự kiến do chương trình in ra:**

```text
pilot=true
direct=true
blocked=false
regular=false
selected=directJob,pilotJob
run=pilot|direct
```

Các log tải dependency hoặc cấu hình Gradle, nếu có, nằm ngoài các dòng do `System.out.println` này tạo.

### 6.3. Phân tích từng ứng viên

| Class | Khớp default stereotype? | Khớp include? | Khớp exclude? | Kết quả |
| --- | --- | --- | --- | --- |
| `PilotJob` | Không; `@Pilot` không là `@Component` | Có, `ANNOTATION Pilot` | Không | **Được chọn** |
| `DirectJob` | Không | Có, `ASSIGNABLE_TYPE DirectJob` | Không | **Được chọn** |
| `BlockedJob` | Không | Có, `ANNOTATION Pilot` | Có, `ASSIGNABLE_TYPE BlockedJob` | **Bị loại** |
| `RegularJob` | Có, `@Service` | Không | Không | **Bị loại** vì default tắt |
| `ScanConfig` | Có, `@Configuration` | Không | Không | Không được *scan thêm*; được đăng ký trực tiếp khi tạo context |

Tập `getBeansOfType(Job.class)` chứa `directJob` và `pilotJob`. `TreeSet` chỉ dùng để **sắp tên khi in stdout ổn định**; không chứng minh thứ tự Spring tìm bean trên classpath. `run=pilot|direct` được viết theo thứ tự lấy bean bằng key, nên không phụ thuộc vào thứ tự map.

### 6.4. Thử các thay đổi có ý nghĩa

**Thay `useDefaultFilters = false` thành `true`.** Khi ấy `RegularJob` trở thành ứng viên vì mang `@Service`; các include vẫn hợp lệ. `ScanConfig` cũng có thể được scanner nhận diện do `@Configuration`, nhưng việc nó đã được đăng ký trực tiếp không tạo thêm một `Job`. Đừng mặc định rằng đổi flag chỉ thêm đúng một bean của *mọi kiểu*.

**Bỏ exclude cho `BlockedJob`.** `BlockedJob` sẽ được thêm vào vì khớp `@Pilot`. Đây là bằng chứng exclude đang chặn một class vốn được include.

**Bỏ include cho `DirectJob`.** Với default vẫn tắt, bean `directJob` không được scan. Lời gọi `jobs.get("directJob").run()` của demo sẽ thất bại vì mã mẫu vẫn yêu cầu key này. Nếu thử biến thể, cần điều chỉnh phần in kết quả hoặc chỉ kiểm tra `containsBean` để quan sát một cách có kiểm soát.

**Đặt `@Component` trên `RegularJob` nhưng giữ `useDefaultFilters=false`.** Bean vẫn không xuất hiện nếu không khớp include. Thêm stereotype không vượt qua công tắc đã tắt bộ lọc mặc định.

## 7. FilterType.ANNOTATION

`ANNOTATION` là kiểu filter mặc định của `@ComponentScan.Filter`. Nó nhận các class mang một annotation được chỉ định hoặc meta-annotation tương ứng.

```java
@ComponentScan(
    basePackages = "com.example.shop",
    useDefaultFilters = false,
    includeFilters = @ComponentScan.Filter(
        type = FilterType.ANNOTATION,
        classes = ExperimentalFeature.class
    )
)
```

Đây là đoạn annotation minh họa đặt trên một class cấu hình; `ExperimentalFeature` là annotation do ứng dụng định nghĩa. Spring có thể đưa class như sau vào container khi scan:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExperimentalFeature {}

@ExperimentalFeature
public class PreviewDiscountPolicy {}
```

Đoạn minh họa cần import `ElementType`, `Target`, `Retention`, `RetentionPolicy`. Nếu muốn code tự chạy, đặt mỗi public top-level type vào file tên tương ứng.

**Hai trường hợp phải phân biệt:**

- Annotation tùy chỉnh **không** có `@Component`: scanner mặc định thường không chọn, nhưng `includeFilters=ANNOTATION` có thể chọn.
- Annotation tùy chỉnh **có** `@Component` là meta-annotation: scanner mặc định đã có thể chọn, ngay cả khi không thêm filter.

Ví dụ meta-stereotype:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@org.springframework.stereotype.Component
public @interface BusinessComponent {}
```

Đây là ví dụ cấu trúc; cần import các annotation type của Java. Với `@BusinessComponent`, nhiều ứng dụng không cần include filter riêng. Trong trường hợp quy tắc chọn quá đặc thù, include filter vẫn hữu ích khi kết hợp `useDefaultFilters=false`.

**Lưu ý:** ANNOTATION dùng để tìm metadata trên **type** được quét, không tìm method `@GetMapping` hay các method mang annotation riêng để tự tạo bean tương ứng. Method-level scanning là vấn đề khác.

## 8. FilterType.ASSIGNABLE_TYPE

`ASSIGNABLE_TYPE` chọn các class có thể gán cho một kiểu mục tiêu, tức là class ấy chính là kiểu cần xét, kế thừa superclass hoặc hiện thực interface.

```java
@ComponentScan(
    basePackages = "com.example.shop.handlers",
    useDefaultFilters = false,
    includeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = CommandHandler.class
    )
)
```

Với giả định `CommandHandler` là interface nghiệp vụ, scanner có thể chọn các implementation trong package được quét, kể cả khi chưa đánh dấu `@Component`:

```java
interface CommandHandler {
    void execute();
}

class CreateOrderHandler implements CommandHandler {
    public void execute() {}
}

class CancelOrderHandler implements CommandHandler {
    public void execute() {}
}
```

Đây là ví dụ hai đoạn độc lập cần đặt trong cùng package hoặc import kiểu phù hợp. Scanner vẫn phải kiểm tra tính hợp lệ của candidate; nó không khởi tạo interface `CommandHandler`.

**Cảnh báo về số lượng:** khi lọc bằng một base interface rất rộng, có thể scan nhiều implementation và gây `NoUniqueBeanDefinitionException` lúc injection đơn theo interface. Nếu muốn nhiều bean, injection `List<CommandHandler>` là lựa chọn rõ ràng. Nếu muốn một, cân nhắc `@Qualifier`, `@Primary` hoặc giới hạn phạm vi package/type hợp lý.

Bộ lọc theo type là một phép tìm trên **metadata của class**, không bảo đảm mọi kiểu có thể gán đều có constructor hay dependencies phù hợp để Spring tạo thành công.

## 9. FilterType.REGEX

`REGEX` chọn class theo **tên đầy đủ (fully qualified class name)**, tức là bao gồm package và tên class. Nó không phân tích giá trị nghiệp vụ của object và không áp regex lên tên bean sau khi tạo.

```java
@ComponentScan(
    basePackages = "com.example.shop",
    useDefaultFilters = false,
    includeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.example\\.shop\\.adapter\\..*Adapter"
    )
)
```

Ở đây:

- `com\\.example\\.shop\\.adapter\\.` trong literal Java dùng `\\.` để trở thành dấu chấm thật trong regex.
- `.*Adapter` khớp các tên kết thúc bằng `Adapter` trong package đang mô tả.
- Regex kiểm tra tên class đầy đủ; `basePackages` vẫn quyết định nơi scanner tìm các class để thử.

Ví dụ `com.example.shop.adapter.SqlAdapter` khớp pattern. `com.example.shop.service.StockService` không khớp. `com.example.shop.adapter.SqlAdapterV2` cũng không khớp vì không kết thúc bằng `Adapter`.

**Lỗi Java string phổ biến:** viết `pattern="com\.example\..*"` sẽ gây lỗi escape không hợp lệ trong mã Java vì `\.` không phải escape hợp lệ của Java string. Cần viết `"com\\.example\\..*"` trong Java source để engine regex nhận `com\.example\..*`.

**Không dùng regex khi type/annotation đã đủ:** tên package và class có thể thay đổi khi refactor, khiến bộ lọc không còn bắt đúng class mà IDE/compiler ít có khả năng cảnh báo. Nếu hệ thống có annotation mô tả capability, lọc bằng `ANNOTATION` thường rõ ý định hơn.

## 10. FilterType.ASPECTJ

`ASPECTJ` nhận một **AspectJ type pattern expression** để xét kiểu. Ví dụ dạng tham khảo theo tài liệu Spring:

```java
@ComponentScan(
    basePackages = "com.example.shop",
    useDefaultFilters = false,
    includeFilters = @ComponentScan.Filter(
        type = FilterType.ASPECTJ,
        pattern = "com.example..*Service+"
    )
)
```

Đây là đoạn cấu hình minh họa sử dụng **cú pháp pattern của AspectJ**, không phải Java regex. Dấu `..` và `+` có ý nghĩa theo AspectJ; cần kiểm tra kiểu thực tế khớp theo cú pháp AspectJ và có dependency hỗ trợ AspectJ type pattern (thường `aspectjweaver`) khi dùng filter này.

`ASPECTJ` ở đây **chỉ dùng để lựa chọn class lúc scan**, không tự động bật Spring AOP, không tạo aspect và không can thiệp vào lời gọi method. Cũng không nên nhầm với annotation `@Aspect`. Với đa số bài toán chọn component theo danh mục, `ANNOTATION`, `ASSIGNABLE_TYPE` hoặc `REGEX` dễ hiểu hơn.

## 11. FilterType.CUSTOM và TypeFilter

Khi các kiểu mặc định không biểu diễn được tiêu chí mong muốn, có thể tự triển khai `org.springframework.core.type.filter.TypeFilter` (giao diện thực nằm trong package `org.springframework.core.type.filter`).

```java
package com.example.shop.config;

import java.io.IOException;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

public final class EndsWithGatewayFilter implements TypeFilter {
    @Override
    public boolean match(
            MetadataReader metadataReader,
            MetadataReaderFactory metadataReaderFactory) throws IOException {

        String className =
                metadataReader.getClassMetadata().getClassName();

        return className.endsWith("Gateway");
    }
}
```

Đây là một class `TypeFilter` Java hợp lệ khi có `spring-core` trên classpath (được Spring Context mang vào). Có thể sử dụng như sau ở cấu hình cùng dự án:

```java
@ComponentScan(
    basePackages = "com.example.shop",
    useDefaultFilters = false,
    includeFilters = @ComponentScan.Filter(
        type = FilterType.CUSTOM,
        classes = EndsWithGatewayFilter.class
    )
)
```

Đoạn cấu hình minh họa cần import `ComponentScan`, `FilterType` và `EndsWithGatewayFilter`, đặt trên class phù hợp.

**Vì sao dùng `MetadataReader`?** Scanner đọc metadata .class bằng cơ chế phù hợp, tránh buộc phải tạo instance của tất cả type để xem chúng có cần đăng ký hay không. Custom filter nên được thiết kế **nhanh, dễ dự đoán, không side effect**. Không kết nối database, gửi request mạng hoặc tải những class nặng chỉ để quyết định include/exclude.

`MetadataReaderFactory` hỗ trợ truy cập metadata của class liên quan, chẳng hạn superclass hoặc interface khi tiêu chí nâng cao cần; hãy tránh tự tạo classloader hay đọc tài nguyên tùy tiện cho mỗi file .class.

Nếu match nhiều class không thể khởi tạo thành component thì hệ thống vẫn cần xử lý tính hợp lệ của candidate; TypeFilter không bỏ qua toàn bộ nguyên tắc tạo bean.

## 12. Cách kết hợp include và exclude trong tình huống thật

### 12.1. Vẫn quét mặc định nhưng loại những class cũ

```java
@Configuration
@ComponentScan(
    basePackages = "com.example.shop",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ANNOTATION,
        classes = LegacyComponent.class
    )
)
class AppConfig {}
```

Giả định `LegacyComponent` là annotation tự định nghĩa. Khi `useDefaultFilters` giữ `true`, Spring tiếp tục tìm các stereotype thông thường nhưng loại class được đánh dấu legacy. Điều này phù hợp nếu phần lớn kiến trúc vẫn tuân theo `@Component`.

### 12.2. Chọn một nhóm plugin có annotation riêng

```java
@Configuration
@ComponentScan(
    basePackages = "com.example.plugins",
    useDefaultFilters = false,
    includeFilters = @ComponentScan.Filter(
        type = FilterType.ANNOTATION,
        classes = PluginComponent.class
    ),
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ANNOTATION,
        classes = DisabledPlugin.class
    )
)
class PluginConfig {}
```

Đây là đoạn minh họa cần hai annotation nghiệp vụ. Với bộ lọc này, class mang `@PluginComponent` được chọn trừ khi đồng thời mang `@DisabledPlugin`. **Tính bật/tắt tại thời điểm runtime** không được tự động cập nhật liên tục: đây là quyết định đăng ký bean khi Spring dựng context. Muốn bật/tắt tính năng động cần cơ chế khác phù hợp.

### 12.3. Đổi implementation trong test

```java
@Configuration
@ComponentScan(
    basePackages = "com.example.shop.data",
    includeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = ".*Stub.*Repository"
    ),
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ANNOTATION,
        classes = org.springframework.stereotype.Repository.class
    )
)
class TestDataConfig {}
```

Ý tưởng: include những class có tên như `StubCustomerRepository` và exclude các class mang `@Repository` trong lượt scan này. Nếu **stub cũng mang `@Repository`**, nó cũng sẽ bị loại vì exclude thắng include; vì vậy cần thiết kế marker/stub rõ ràng.

Cấu hình test còn phụ thuộc các configuration khác đã được import hoặc autoconfigure. Chỉ exclude ở một lượt scan **không chứng minh** rằng bean gốc không thể được đăng ký qua lượt scan khác, `@Bean`, `@Import` hoặc auto-configuration.

### 12.4. Điều gì xảy ra khi include và exclude cùng khớp một class?

```java
@ComponentScan(
    basePackages = "com.example.shop",
    useDefaultFilters = false,
    includeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = OrderProcessor.class
    ),
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = ".*DeprecatedOrderProcessor"
    )
)
```

Nếu `DeprecatedOrderProcessor implements OrderProcessor` và tên đầy đủ kết thúc bằng `DeprecatedOrderProcessor`, bean đó bị loại. Những `OrderProcessor` khác đủ điều kiện sẽ được đưa vào tập ứng viên. Đây là quy tắc loại trừ trong **lượt scanner**.

## 13. Cấu hình XML tương đương khi gặp dự án cũ

Nhiều dự án legacy dùng `context:component-scan` thay vì annotation. Cùng ý tưởng:

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/context
           https://www.springframework.org/schema/context/spring-context.xsd">

    <context:component-scan
            base-package="com.example.shop"
            use-default-filters="false">
        <context:include-filter
                type="annotation"
                expression="com.example.shop.PluginComponent"/>
        <context:exclude-filter
                type="regex"
                expression="com\.example\.shop\..*Legacy.*"/>
    </context:component-scan>
</beans>
```

Đây là ví dụ XML có tên annotation nghiệp vụ giả định `com.example.shop.PluginComponent`. Trong XML regex dùng dấu `\.` trực tiếp; không cần escape thêm theo quy tắc Java string literal. Spring XML và `@ComponentScan` đều diễn đạt cùng ý tưởng chọn/loại type bằng filter.

Các giá trị type ở XML gồm `annotation`, `assignable`, `aspectj`, `regex`, `custom`; trong Java API là các hằng của `FilterType`.

## 14. Phân biệt bộ lọc scan với các cơ chế khác

| Cơ chế | Mục đích | Quan hệ với component scan |
| --- | --- | --- |
| `basePackages` / `basePackageClasses` | Xác định **vùng classpath được quét** | Filter chỉ hoạt động bên trong vùng ấy. |
| `includeFilters` | Bổ sung class có thể được chọn | Có thể chọn class chưa mang `@Component`. |
| `excludeFilters` | Loại class khỏi **lượt scan** | Không chặn đăng ký qua các đường khác. |
| `useDefaultFilters` | Bật/tắt phát hiện stereotype mặc định | Không xóa bean đã được đăng ký bằng `@Bean` hoặc `@Import`. |
| `@Profile` | Giới hạn đăng ký theo profile active | Điều kiện cấu hình, không phải filter từ `@ComponentScan`. |
| `@Conditional` | Giới hạn đăng ký theo điều kiện cụ thể | Có thể làm bean không được đăng ký dù metadata đã được scanner nhận diện. |
| `@Primary` | Ưu tiên ứng viên khi autowire đơn | Không quyết định scanner có đưa type vào context hay không. |
| `@Qualifier` | Thu hẹp candidate khi injection | Áp dụng trong giải quyết dependency, sau chuyện scan. |
| `@Bean` | Đăng ký bean từ factory method | Đường đăng ký độc lập, không bị xóa bởi một exclude filter. |
| `@Import` | Import configuration/selector/registrar | Đường đăng ký khác với chọn component theo scanner. |

**Sai lầm nguy hiểm:** xem `excludeFilters` như “cấm class này xuất hiện trong toàn ApplicationContext”. Nếu một `@Configuration` khác khai báo bean cùng type bằng `@Bean`, exclude filter không triệt tiêu bean đó. Cần nhìn toàn bộ các đường đăng ký khi điều tra bean ngoài dự kiến.

## 15. Spring Boot và giới hạn quét

Trong Spring Boot, `@SpringBootApplication` thường bao gồm `@ComponentScan`. Các thuộc tính như `scanBasePackages` và `scanBasePackageClasses` cấu hình **component scanning**, nhưng không mặc nhiên thay mọi cơ chế scan khác, ví dụ JPA entity hoặc Spring Data repository.

Ví dụ khái niệm:

```java
@SpringBootApplication(scanBasePackages = "com.example.shop")
public class ShopApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }
}
```

Đây là snippet Spring Boot, yêu cầu dependencies Spring Boot và class trong package phù hợp. Với ứng dụng tự thêm nhiều `@ComponentScan`, hãy cẩn thận vì mỗi cấu hình scan có thể góp thêm bean definition. Một exclude filter áp dụng cho lượt scan này không đảm bảo một lượt scan khác không đăng ký type đó.

Nếu chỉ muốn loại một bean dựa trên feature flag ở Boot, có thể cấu hình bean điều kiện (`@ConditionalOnProperty`, v.v.) phù hợp với nguồn cấu hình hơn là lọc tên class bằng regex. Nhưng đây là lựa chọn thiết kế bên ngoài cơ chế filter cơ bản của Spring Framework.

## 16. Lỗi thường gặp và cách kiểm tra

### Lỗi 1 — “Có includeFilters rồi nhưng Spring vẫn scan mọi @Service”

Nguyên nhân: `useDefaultFilters` mặc định **true**, nên `@Service` vẫn được chọn. Muốn chỉ lấy class theo include filter, đặt `useDefaultFilters=false` và kiểm tra lại tất cả dependency cần thiết.

### Lỗi 2 — “Tắt useDefaultFilters thì mất toàn bộ service”

Nếu không khai báo include phù hợp, các stereotype bình thường không còn được tìm qua lượt scan này. Thêm bộ lọc include, hoặc giữ mặc định nếu muốn dùng cấu trúc stereotype thông thường.

### Lỗi 3 — “Exclude một class nhưng bean vẫn có mặt”

Cần xác định bean được đăng ký từ đâu: một `@Bean`, `@Import`, một context khác, một lần `@ComponentScan` khác hoặc auto-configuration. `excludeFilters` chỉ áp dụng với scanner được cấu hình. Trong trường hợp này, xem bean definition và nguồn cấu hình thay vì thêm hàng loạt exclude trùng nhau.

### Lỗi 4 — “REGEX không match class mong đợi”

Xác minh **tên đầy đủ** của class (`package.ClassName`), không so với tên bean. Trong Java string, nhớ dấu `\\.` để thể hiện regex literal `\.`. Kiểm tra pattern bằng dữ liệu tên class thực và lưu ý regex matcher nhận toàn bộ tên theo quy tắc matching của loại filter.

### Lỗi 5 — “ASSIGNABLE_TYPE tìm được interface nhưng không tạo được bean”

Interface hoặc abstract class không tự trở thành component có thể khởi tạo nhờ khớp filter. Chỉ concrete, independent class hợp lệ mới thường được đưa vào danh sách component candidates, ngoài những hỗ trợ riêng như lookup-method có điều kiện. Chọn implementation rõ ràng và cung cấp constructor/dependencies có thể giải quyết.

### Lỗi 6 — “Exclude filter và include filter cùng khớp; mong include thắng”

Trong một lượt scan, class khớp **exclude** bị loại ngay cả khi cũng khớp include hoặc stereotype mặc định. Đổi thiết kế điều kiện hoặc xem lại thứ tự ưu tiên nghiệp vụ trước khi viết filter.

### Lỗi 7 — “Filter CUSTOM có side effect hoặc load class tùy tiện”

Custom `TypeFilter` được gọi trong quá trình scanning; IO hoặc reflection đắt đỏ khiến startup chậm và có thể gây lỗi ngoài ý muốn. Dùng `MetadataReader` khi đủ, tránh service call, connection hay global mutable state trong matcher.

### Lỗi 8 — “Scan package quá rộng nên có bean ngoài ý muốn”

Nếu dùng `basePackages="com"` hoặc package gốc quá chung, scanner có thể đụng vô số class từ module không liên quan. Thu hẹp package quét, tách annotation nghiệp vụ hợp lý và kiểm tra import cấu hình từ nơi khác.

### Lỗi 9 — “Đưa filter của một context vào và tưởng áp dụng cho tất cả context”

Ứng dụng có thể có root ApplicationContext, child WebApplicationContext, test slice hoặc context độc lập. Bean trong context nào phụ thuộc vào scan và registration của context đó; không áp dụng filter một cách toàn cục vượt ranh giới context.

### Lỗi 10 — “Chọn bean bằng scanner nhưng constructor vẫn lỗi”

Filter có thể chọn một bean đủ điều kiện scan nhưng bean ấy cần dependency chưa tồn tại, constructor không phù hợp hoặc bean name xung đột. `includeFilters` không bypass dependency injection hay lỗi tạo bean. Khi cần, kiểm tra `UnsatisfiedDependencyException`, `BeanCreationException` và danh sách ứng viên.

### Lỗi 11 — “Đổi tên class mà không cập nhật REGEX”

Regex dựa vào tên class đầy đủ dễ bị ảnh hưởng bởi refactor. Các phép lọc dựa theo annotation hoặc interface là lựa chọn bền vững hơn khi tên class không phải hợp đồng nghiệp vụ.

### Lỗi 12 — “Nghĩ scan filter là cơ chế bật/tắt bean động sau khi context chạy”

Filter quyết định lúc scan/đăng ký; nó không tự theo dõi file cấu hình để thêm/xóa bean runtime. Với tính năng có thể bật/tắt động, nên thiết kế abstraction hoặc runtime strategy riêng, không dựa vào việc sửa `includeFilters` khi ứng dụng đang chạy.

## 17. Quan sát và kiểm thử filter

### 17.1. Kiểm tra trực tiếp bean name

```java
try (AnnotationConfigApplicationContext ctx =
         new AnnotationConfigApplicationContext(ScanConfig.class)) {
    System.out.println(ctx.containsBean("pilotJob"));
    System.out.println(ctx.containsBean("blockedJob"));
}
```

Đoạn này tái sử dụng `ScanConfig` trong ví dụ `FilterDemo`, có thể đặt trong package `demo` với import context tương ứng. Kết quả `true` rồi `false` xác minh **sự có mặt theo bean name**, không tự chứng minh mọi logic chọn đúng nếu tên bean đã được đổi.

### 17.2. Kiểm tra theo type

```java
var names = ctx.getBeanNamesForType(Job.class);
java.util.Arrays.sort(names);
for (String name : names) {
    System.out.println(name);
}
```

Đây là snippet để đặt bên trong vùng còn sống của `ctx` và dùng `Job` từ demo. Sắp xếp chỉ giúp test deterministic. Với cấu hình ví dụ gốc, tên kết quả là `directJob` rồi `pilotJob`.

### 17.3. Đọc nguồn gốc bean definition

Trong các test điều tra lỗi, có thể dùng:

```java
var factory = ctx.getBeanFactory();
var definition = factory.getBeanDefinition("pilotJob");

System.out.println(definition.getBeanClassName());
System.out.println(definition.getResourceDescription());
```

Đây là đoạn minh họa trong cùng context `AnnotationConfigApplicationContext` của demo. `getResourceDescription()` có thể trả mô tả resource hoặc `null` tùy cách đăng ký, nên không nên đặt stdout cố định cho nó. Với bean được component scan, mô tả resource thường gợi ra file/class đã được phát hiện. Giá trị này hữu ích để truy nguồn, không phải hợp đồng output nghiệp vụ.

### 17.4. Test theo ma trận cấu hình

| Thao tác | `PilotJob` | `DirectJob` | `BlockedJob` | `RegularJob` |
| --- | --- | --- | --- | --- |
| Bản gốc: default=false, 2 include, 1 exclude | Có | Có | Không | Không |
| Bỏ exclude | Có | Có | Có | Không |
| Chuyển default=true, giữ include/exclude | Có | Có | Không | Có |
| Bỏ include `DirectJob`, giữ default=false | Có | Không | Không | Không |
| Xóa mọi include, giữ default=false | Không | Không | Không | Không |

Ma trận mô tả **sự phát hiện bởi lượt scan đang xét**. `ScanConfig` vẫn được đăng ký trực tiếp, và những bean từ cấu hình khác (nếu thêm) có thể thay đổi danh sách thực tế.

**Nên test ở hai mức:** đơn vị cho custom `TypeFilter` (tập metadata nào match), và integration với `AnnotationConfigApplicationContext` để kiểm tra bean definition và dependency thật. Nếu test sửa cấu hình scan, dựng **context mới** cho mỗi biến thể thay vì kỳ vọng context đã refresh tự quét lại.

## 18. Câu hỏi tự luyện

**Câu 1.** Một cấu hình khai báo `includeFilters = @Filter(Experimental.class)` nhưng không ghi `useDefaultFilters`. Vì sao bean `@Service` thường vẫn xuất hiện?

**Đáp:** Vì default là `true`. Include filter **bổ sung** ứng viên vào các stereotype mặc định thay vì thu hẹp chúng.

**Câu 2.** Cần quét duy nhất class mang `@Pilot`, kể cả class không có `@Component`. Cấu hình nào thể hiện đúng ý định?

**Đáp:** `useDefaultFilters=false` và `includeFilters=@Filter(type=ANNOTATION,classes=Pilot.class)`, với base package phù hợp. Class vẫn phải đủ điều kiện là component candidate và được đăng ký thành công.

**Câu 3.** Nếu class vừa match include vừa match exclude, điều gì xảy ra?

**Đáp:** Class bị loại khỏi lượt scan vì exclude có quyền phủ quyết.

**Câu 4.** Vì sao `excludeFilters` không bảo đảm không bao giờ có một bean cùng type trong container?

**Đáp:** Bean có thể được đăng ký qua `@Bean`, `@Import`, auto-configuration hoặc lượt scan khác. Exclude chỉ giới hạn một quá trình scan.

**Câu 5.** `FilterType.REGEX` so sánh với `getBean("beanName")` hay `package.ClassName`?

**Đáp:** So sánh với **tên class đầy đủ** ở metadata, không phải tên bean do Spring đặt.

**Câu 6.** `ASSIGNABLE_TYPE` với một interface có tự tạo instance interface?

**Đáp:** Không. Nó có thể chọn các class implementation đủ điều kiện trong base packages; interface tự nó không thể được constructor-instantiate như concrete component.

**Câu 7.** Nếu muốn điều kiện “class có cả hai annotation A và B” thì thêm hai include filters có đủ không?

**Đáp:** Không, các include được kết hợp theo OR. Có thể viết một custom `TypeFilter` kiểm tra đồng thời A và B hoặc tổ chức lại marker annotation.

**Câu 8.** Trong `FilterDemo`, vì sao `RegularJob` mang `@Service` nhưng `containsBean("regularJob")` trả false?

**Đáp:** `useDefaultFilters=false` tắt stereotype mặc định, còn hai include chỉ chọn `Pilot` và `DirectJob`. `RegularJob` không khớp include.

**Câu 9.** Filter `ASPECTJ` có tự biến class thành aspect không?

**Đáp:** Không. Nó dùng cú pháp type pattern để lựa chọn class lúc scan, không bật Spring AOP hay áp lời khuyên lên method.

**Câu 10.** Khi một custom filter gây thời gian startup dài, nên kiểm tra gì?

**Đáp:** Xem `TypeFilter.match()` có thực hiện IO, network, nạp class, reflection tốn kém hoặc xử lý lại metadata liên tục không; ưu tiên xử lý thông tin từ `MetadataReader` một cách nhanh, không side effect.

## 19. Tài liệu chính thức để đối chiếu

- [Spring Framework 6.2 — Classpath Scanning and Managed Components](https://docs.spring.io/spring-framework/reference/6.2/core/beans/classpath-scanning.html): các filter types, ví dụ include/exclude, `useDefaultFilters` và cấu hình XML.
- [Spring Framework 6.2 — ComponentScan API](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/annotation/ComponentScan.html): `includeFilters`, `excludeFilters`, `useDefaultFilters`, phạm vi scan và mặc định.
- [Spring Framework 6.2 — ComponentScan.Filter API](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/annotation/ComponentScan.Filter.html): `classes`, `pattern`, `type` và quy tắc nhiều type.
- [Spring Framework 6.2 — FilterType API](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/annotation/FilterType.html): năm lựa chọn ANNOTATION, ASSIGNABLE_TYPE, ASPECTJ, REGEX, CUSTOM.
- [Spring Framework — TypeFilter API](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/core/type/filter/TypeFilter.html): hợp đồng triển khai matcher tự tạo.

**Ghi nhớ:** Với `@ComponentScan`, **package xác định phạm vi**, `includeFilters` bổ sung cách nhận diện, `excludeFilters` loại ứng viên và `useDefaultFilters=false` tắt phát hiện component/stereotype mặc định. Muốn dự đoán chính xác một bean có xuất hiện hay không, phải nhìn cả **filter**, **điều kiện ứng viên** và **mọi đường đăng ký bean** khác trong context.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="component-scanning-006">Quy tắc sinh tên và tùy biến BeanNameGenerator</a>

<details>
<summary>Xem chi tiết</summary>

# Quy tắc sinh tên bean và tùy biến `BeanNameGenerator` trong Spring Core Container

## 1. Vì sao tên bean quan trọng?

Một Spring IoC Container quản lý các **bean definition** được định danh bằng tên. Khi viết `@Component`, `@Service`, `@Repository` hoặc các stereotype chuyên biệt, ta có thể cung cấp tên rõ ràng hoặc để Spring tự sinh. Tên bean xuất hiện trong nhiều thao tác:

- `applicationContext.getBean("orderService")`: lookup bằng tên;
- `@Qualifier("orderService")`: có thể dùng bean name làm giá trị qualifier mặc định khi phân giải dependency;
- `@Resource(name = "...")`: một cách chọn dependency theo tên trong ngữ cảnh hỗ trợ annotation đó;
- các tập hợp `Map<String, T>` được Spring autowire: khóa thường là bean name;
- các hệ thống quan sát/diagnostics, configuration hoặc integration tham chiếu bean bằng tên.

Ví dụ, hai thành phần sau cùng được scan:

```java
@Component
class InvoiceFormatter {
}

@Service("checkoutWorkflow")
class CheckoutService {
}
```

Với naming strategy mặc định, bean đầu thường có tên `invoiceFormatter`; bean thứ hai có tên **`checkoutWorkflow`** do đã khai báo rõ. Đây là **đoạn minh họa**, không phải file Java chạy độc lập.

Tại sao cần học `BeanNameGenerator`? Trong codebase lớn, nhiều package có thể chứa class trùng **tên ngắn**: `com.acme.sales.ReportService` và `com.acme.audit.ReportService`. Nếu cả hai được component scanning phát hiện và đều nhận tên mặc định `reportService`, Spring có nguy cơ báo xung đột bean definition. Có thể đặt tên riêng trên các stereotype hoặc dùng strategy sinh tên dựa vào tên class đầy đủ.

Mục tiêu của bài:

1. Hiểu quy tắc tên mặc định, tên tường minh, JavaBeans decapitalization và ngoại lệ với chữ viết hoa;
2. Nắm giao diện `BeanNameGenerator` cùng `AnnotationBeanNameGenerator` và `FullyQualifiedAnnotationBeanNameGenerator`;
3. Biết cấu hình `@ComponentScan(nameGenerator = ...)`, thời điểm áp dụng và tác động khi refactor;
4. Tự viết custom generator an toàn, ưu tiên giữ tên khai báo tường minh;
5. Nhận biết cách đặt tên của component scan khác `@Bean` và cấu hình import;
6. Kiểm thử bằng một dự án Java 17+ và Spring Framework 6.

## 2. Spring đặt tên cho component được scan như thế nào?

Khi Spring quét một base package bằng `@ComponentScan`, scanner tìm class ứng viên và đăng ký bean definition. Tại thời điểm đăng ký, nó cần tên cho từng definition. Chiến lược mặc định của **component scanner** là `org.springframework.context.annotation.AnnotationBeanNameGenerator`.

Logic có thể hiểu theo thứ tự:

1. Scanner phát hiện một component candidate có metadata annotation.
2. `AnnotationBeanNameGenerator` thử lấy **tên khai báo tường minh** từ stereotype thích hợp.
3. Nếu không có tên tường minh, nó sinh tên mặc định dựa vào **tên class ngắn** theo quy tắc JavaBeans.
4. Bean definition được đăng ký với tên đó; nếu tên đã dùng cho definition khác không tương thích, quá trình scan có thể thất bại vì xung đột.

Sơ đồ:

```text
Class được @ComponentScan phát hiện
                  |
                  v
       BeanNameGenerator
                  |
       +----------+-----------+
       |                      |
 @Component("explicit")      Không có tên tường minh
 / @Service("explicit")                  |
       |                                 v
       v                      Sinh từ class name
 "explicit"                   (mặc định tên ngắn)
       |                                 |
       +--------------+------------------+
                      v
         Đăng ký bean definition
                      |
           Tên bị trùng, xung đột?
             /               \
           Có               Không
           |                  |
     Lỗi đăng ký        Bean sẵn sàng resolve
```

Tên bean là **định danh trong registry**, không đồng nghĩa Java class name, package name, qualifier bất kỳ hoặc loại dependency. Một bean có thể có nhiều alias, nhưng vẫn chỉ có một primary bean name dùng khi đăng ký.

### 2.1. Tên mặc định: JavaBeans `decapitalize`

Với một class `com.acme.billing.InvoiceService`, Spring thường sinh tên `invoiceService`: lấy phần **simple class name** rồi áp dụng quy tắc bỏ viết hoa đầu theo JavaBeans.

| Class được scan | Tên mặc định với `AnnotationBeanNameGenerator` |
| --- | --- |
| `com.acme.billing.InvoiceService` | `invoiceService` |
| `com.acme.billing.MyJdbcDao` | `myJdbcDao` |
| `com.acme.api.UserController` | `userController` |
| `com.acme.util.URLFormatter` | `URLFormatter` |
| `com.acme.util.XMLParser` | `XMLParser` |

**Ngoại lệ cần nhớ:** `java.beans.Introspector.decapitalize` giữ nguyên chuỗi nếu **hai ký tự đầu đều viết hoa**. Vì vậy `URLFormatter` không trở thành `uRLFormatter`. Đây là lý do không nên tự triển khai tên mặc định bằng `Character.toLowerCase(name.charAt(0))`.

Đoạn Java độc lập sau kiểm tra được quy tắc decapitalize mà Spring sử dụng:

```java
import java.beans.Introspector;

public class BeanNameRuleDemo {
    public static void main(String[] args) {
        System.out.println(Introspector.decapitalize("InvoiceService"));
        System.out.println(Introspector.decapitalize("URLFormatter"));
        System.out.println(Introspector.decapitalize("XMLParser"));
    }
}
```

Lưu vào `BeanNameRuleDemo.java` rồi chạy bằng JDK 17+:

```bash
javac BeanNameRuleDemo.java
java BeanNameRuleDemo
```

Stdout:

```text
invoiceService
URLFormatter
XMLParser
```

Trong ví dụ này JavaBeans `Introspector` chỉ minh họa quy tắc đặt tên. Component scanning thật còn xử lý annotation, registry, bean definition và các điều kiện phát hiện class.

### 2.2. Tên khai báo tường minh

Spring stereotype hỗ trợ tên do developer chỉ định:

```java
@Component("auditWriter")
class AuditWriter {
}

@Service("paymentGateway")
class CardGateway {
}

@Repository("orderStorage")
class JpaOrderRepository {
}
```

Đây là **mã minh họa**; với lớp nằm trong package được scan, các tên được đăng ký lần lượt là `auditWriter`, `paymentGateway`, `orderStorage`. Tên do annotation cung cấp có mức ưu tiên hơn tên mặc định của generator.

`@Component(value = "auditWriter")` và `@Component("auditWriter")` tương đương. Các stereotype chuyên biệt như `@Service` có thuộc tính `value` thích hợp để khai báo tên.

Khi cần gọi bean theo tên từ code hoặc external configuration, **tên tường minh là một hợp đồng ổn định hơn tên suy ra từ class**. Nếu đổi tên class `CardGateway` thành `StripeGateway`, bean được khai báo `@Service("paymentGateway")` vẫn có tên `paymentGateway`. Ngược lại, tên mặc định tự đổi từ `cardGateway` sang `stripeGateway`.

### 2.3. Bean name không mặc nhiên là qualifier duy nhất

Ví dụ có hai bean implement `PaymentGateway`, một được đặt tên `cardGateway`, một tên `bankGateway`. Constructor cần `PaymentGateway` **không có qualifier** có thể gặp `NoUniqueBeanDefinitionException` nếu không có lựa chọn thắng theo quy tắc khác.

`@Qualifier("cardGateway")` thường có thể chọn bean tương ứng vì Spring xem bean name như giá trị qualifier mặc định trong trường hợp phù hợp. Nhưng khi nhóm nghiệp vụ có nhiều bean cùng qualifier, qualifier không bắt buộc phải trùng bean name và không nhất thiết là duy nhất.

Đừng dùng `BeanNameGenerator` để thay cho giải pháp rõ nghĩa khi dependency đơn có nhiều implementation. Generator giải quyết **định danh đăng ký**; `@Qualifier`, `@Primary` và các quy tắc autowire giải quyết **chọn bean**.

## 3. Các API chính của `BeanNameGenerator`

`org.springframework.beans.factory.support.BeanNameGenerator` là interface chiến lược sinh tên. Phương thức trọng tâm:

```java
String generateBeanName(
        BeanDefinition definition,
        BeanDefinitionRegistry registry
);
```

Đây là chữ ký API giản lược, cần imports `BeanDefinition` và `BeanDefinitionRegistry` từ `org.springframework.beans.factory.config` / `org.springframework.beans.factory.support`. Một implementation nhận metadata của bean và registry, trả **tên bean** để caller đăng ký.

Ba khái niệm liên quan:

| API | Vai trò | Đặc điểm |
| --- | --- | --- |
| `BeanNameGenerator` | Interface chiến lược | Có thể tự thực thi logic riêng |
| `AnnotationBeanNameGenerator` | Strategy mặc định cho component scanning | Đọc explicit stereotype name; fallback tên class ngắn kiểu JavaBeans |
| `FullyQualifiedAnnotationBeanNameGenerator` | Strategy tích hợp sẵn | Giữ explicit stereotype name; fallback tên class đầy đủ gồm package |

`AnnotationBeanNameGenerator` cung cấp phương thức protected `buildDefaultBeanName(BeanDefinition)` để subclass tùy biến cách sinh **tên mặc định**. Đây là extension point phù hợp nếu bạn muốn giữ cách Spring đọc tên từ stereotype nhưng đổi fallback format.

### 3.1. `AnnotationBeanNameGenerator`

Với `@Component class LocalProcessor` trong package `demo.catalog`, default name là `localProcessor`. Cùng một class name trong `demo.billing` cũng trở thành `localProcessor`. Nếu quét cả hai vào **cùng một BeanDefinitionRegistry**, xảy ra xung đột trừ khi có cách phân biệt tên.

Nó phù hợp với dự án nhỏ hoặc kiến trúc có convention bảo đảm tên class component không trùng trong phạm vi scan.

### 3.2. `FullyQualifiedAnnotationBeanNameGenerator`

Class `org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator` mở rộng `AnnotationBeanNameGenerator` và đổi fallback thành **tên class đầy đủ**:

| Component | Default generator | Fully-qualified generator |
| --- | --- | --- |
| `demo.catalog.LocalProcessor` | `localProcessor` | `demo.catalog.LocalProcessor` |
| `demo.billing.LocalProcessor` | `localProcessor` | `demo.billing.LocalProcessor` |
| `demo.catalog.URLFormatter` | `URLFormatter` | `demo.catalog.URLFormatter` |
| `@Service("invoiceWorkflow") demo.catalog.InvoiceService` | `invoiceWorkflow` | `invoiceWorkflow` |

Các tên có dấu chấm `.` hoàn toàn có thể là bean name. Đây không phải Java expression, và `context.getBean("demo.catalog.LocalProcessor")` hoạt động như lookup bằng chuỗi khi bean đã được đăng ký dưới tên đó.

**Lưu ý:** `FullyQualifiedAnnotationBeanNameGenerator` vẫn **tôn trọng tên khai báo tường minh**. `@Service("invoiceWorkflow")` không bị đổi thành `demo.catalog.InvoiceService`. Nếu hai class ở hai package cùng được gắn explicit name `"invoiceWorkflow"`, đổi generator sang fully-qualified **không tự xử lý được xung đột tên tường minh**; phải sửa cấu hình đặt tên.

Theo Javadoc của Spring Framework 6.2, mặc định **component scanning** dùng `AnnotationBeanNameGenerator`, còn cơ chế **configuration-level import** có thể sử dụng `FullyQualifiedAnnotationBeanNameGenerator` mặc định. Không vì thế mà toàn bộ bean trong Spring context đều được sinh tên fully-qualified.

### 3.3. Khi nào tự triển khai một generator?

Một custom generator phù hợp khi codebase có naming convention rõ: ví dụ thêm prefix `site_`, thêm namespace theo domain hoặc tạo định danh từ metadata. Nhưng quy tắc phải ổn định, dễ kiểm thử và không làm hỏng bean name được cấu hình có chủ đích.

Nếu mục tiêu đơn thuần là tránh xung đột tên class ngắn từ nhiều package, **ưu tiên strategy fully-qualified sẵn có** trước khi viết custom generator. Điều đó giúp giảm code hạ tầng không cần thiết.

## 4. Cấu hình generator cho component scanner

`@ComponentScan` cung cấp thuộc tính `nameGenerator` nhận một class triển khai `BeanNameGenerator`:

```java
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

@Configuration
@ComponentScan(
        basePackages = {"com.example.sales", "com.example.audit"},
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class
)
public class AppConfig {
}
```

Đây là **đoạn minh họa** với các package giả định. Khi `AppConfig` được đăng ký vào context, scanner sử dụng generator chỉ định để đặt tên cho các component do lượt scan này phát hiện.

Bạn cũng có thể dùng class tự viết:

```java
@Configuration
@ComponentScan(
        basePackages = "com.example.sales",
        nameGenerator = PrefixBeanNameGenerator.class
)
class PrefixedConfig {
}
```

Đoạn này chỉ minh họa cú pháp; cần class `PrefixBeanNameGenerator` thật sự triển khai `BeanNameGenerator`, có constructor không tham số và được Spring tạo được tại lúc xử lý scan. Một generator khai báo qua annotation là **hạ tầng cấu hình scanner**, không phải bean nghiệp vụ bắt buộc phải được component scanner quét.

**Phạm vi tác động:** `nameGenerator` trên một `@ComponentScan` ảnh hưởng các component definitions **được đăng ký qua lượt scan đó**. Nó không mặc định đổi tên mọi bean do `@Bean` method, `@Import`, auto-configuration hoặc API đăng ký bean trực tiếp tạo ra. Nếu nhiều lượt scan có chiến lược khác nhau, cần hiểu chúng có thể cùng ghi vào một registry và cùng tạo xung đột tên.

### 4.1. Cấu hình bằng API chương trình

Nếu dựng `AnnotationConfigApplicationContext` bằng code, có thể cấu hình naming strategy trên context trước khi đăng ký các configuration class:

```java
AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext();
context.setBeanNameGenerator(
        new FullyQualifiedAnnotationBeanNameGenerator());
context.register(AppConfig.class);
context.refresh();
try {
    // Sử dụng context...
} finally {
    context.close();
}
```

Đây là **mã minh họa**; cần imports và `AppConfig` thực tế. Đặt generator **trước** `register`/`scan` và `refresh` để việc đăng ký bean definition sử dụng đúng strategy. Tùy API đăng ký và mức cấu hình, `@ComponentScan(nameGenerator = ...)` cụ thể có thể chọn generator riêng cho lượt scan, nên khi cần kiểm soát chặt, hãy đặt policy thống nhất tại các điểm scan.

### 4.2. Spring Boot

Ứng dụng Spring Boot thông thường có component scanning nhờ `@SpringBootApplication`. Có thể cấu hình một generator cho scanner bằng `@ComponentScan` được khai báo tường minh, nhưng cần cẩn trọng: khi đặt `@ComponentScan` trực tiếp cùng `@SpringBootApplication`, bạn đang bổ sung hoặc thay đổi cách quét và cần kiểm tra các exclude filters mặc định mà Boot cung cấp qua composed annotation.

Một hướng ít mơ hồ là cấu hình naming strategy qua API context hoặc cấu hình hạ tầng thống nhất của ứng dụng, và **kiểm thử bean registry cuối cùng**. Với phạm vi bài học này, chương trình chạy ở mục 7 dùng Spring Context thuần để minh họa chính xác hành vi của `@ComponentScan`.

## 5. Custom `BeanNameGenerator`: thêm prefix nhưng tôn trọng tên khai báo

Một cách viết ngắn và an toàn là kế thừa `AnnotationBeanNameGenerator`, chỉ override logic tạo tên mặc định:

```java
package demo.app;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;

public class PrefixBeanNameGenerator extends AnnotationBeanNameGenerator {

    public PrefixBeanNameGenerator() {
    }

    @Override
    protected String buildDefaultBeanName(BeanDefinition definition) {
        return "site_" + super.buildDefaultBeanName(definition);
    }
}
```

Đây là một class **hoàn chỉnh**, dùng trực tiếp trong dự án mẫu mục 7. Phương thức `buildDefaultBeanName(...)` chỉ được gọi khi generator không tìm được tên stereotype tường minh; do đó:

- `@Component class LocalProcessor` → `site_localProcessor`;
- `@Component class URLFormatter` → `site_URLFormatter`;
- `@Service("invoiceWorkflow") class InvoiceService` → **`invoiceWorkflow`**, giữ nguyên explicit name.

Một prefix như `site_` không đủ để giải quyết xung đột giữa **hai class cùng tên ngắn** trong hai package. Cả hai vẫn có thể nhận `site_localProcessor`. Nếu mục tiêu là hỗ trợ đồng thời `demo.catalog.LocalProcessor` và `demo.billing.LocalProcessor`, chọn fully-qualified generator hoặc đưa thêm package identifier vào policy đặt tên.

### 5.1. Khi implement interface từ đầu

Có thể tự implement `BeanNameGenerator` và truy cập `definition.getBeanClassName()`, rồi tạo tên từ class name. Nhưng cách này cần tự xử lý metadata stereotype, bean definition không có class name trực tiếp, naming collision và các quy tắc đặc biệt. Nếu chỉ muốn thay format của **tên mặc định**, subclass `AnnotationBeanNameGenerator` thường ít lỗi hơn.

Nếu vẫn tự implement hoàn toàn, hãy xác định:

1. Tên có được tạo khác nhau cho mọi bean trong registry đang đăng ký không?
2. Explicit stereotype name và custom composed annotation được ưu tiên ra sao?
3. Khi bean definition có factory method hoặc class name chưa resolved, policy xử lý thế nào?
4. Khi rename package/class, hợp đồng về bean name thay đổi ra sao?
5. Có cần tham chiếu `registry.containsBeanDefinition(name)` hay chiến lược uniqueness rõ ràng không?

Không nên âm thầm tự tăng hậu tố `1`, `2` dựa trên thứ tự scan nếu tên bean là hợp đồng ổn định. Nếu có xung đột logic, sửa convention lành mạnh hơn.

## 6. Các kiểu xung đột bean name thường gặp

### 6.1. Hai component có cùng simple class name

`com.acme.sales.ReportService` và `com.acme.audit.ReportService` cùng có `@Service` không đặt tên:

```java
// File: com/acme/sales/ReportService.java
package com.acme.sales;
@Service
public class ReportService { }

// File: com/acme/audit/ReportService.java
package com.acme.audit;
@Service
public class ReportService { }
```

Hai đoạn này minh họa, cần imports. Default generator đều đặt tên `reportService`. Khi scan cả hai vào một registry, Spring thường phát hiện bean definitions không tương thích cùng tên và báo lỗi đăng ký, thường là `ConflictingBeanDefinitionException`. Tùy nguồn đăng ký, cấu hình overriding và thời điểm xử lý, loại lỗi có thể khác; đừng cố dựa vào đúng một message exception.

Cách xử lý:

- đặt tên explicit khác nhau (`@Service("salesReportService")` và `@Service("auditReportService")`);
- hoặc dùng `FullyQualifiedAnnotationBeanNameGenerator`;
- hoặc đổi cấu trúc package/đăng ký có chủ đích để chỉ có một bean phù hợp.

### 6.2. Explicit names trùng nhau

`@Service("sharedService")` trên hai class khác nhau là xung đột dù dùng generator fully-qualified, vì explicit name được giữ. Giải pháp là đổi tên cụ thể theo role hoặc không đăng ký đồng thời hai bean không tương thích.

### 6.3. Hai bean khác tên nhưng cùng kiểu

Đổi tên đã giúp **đăng ký** cả hai bean, nhưng dependency kiểu `ReportService` hoặc interface chung vẫn có thể mơ hồ khi inject một bean đơn. Khi đó cần `@Qualifier`, `@Primary`, collection injection hoặc điều chỉnh thiết kế. Tránh nhầm “xung đột tên bean” với “nhiều ứng viên cùng type”.

### 6.4. Scan trùng và cấu hình registration khác

Một class vừa được quét qua component scanning, vừa được tạo thêm bằng `@Bean` có thể dẫn đến bean trùng kiểu, trùng tên hoặc ghi đè theo chính sách registry. `BeanNameGenerator` trên scanner chỉ xử lý tên cho phần **scanner đăng ký**; không đảm bảo đồng bộ tên với `@Bean`/`@Import`.

### 6.5. Alias và tên chính

Một bean có thể có **alias** để tham chiếu cùng instance dưới tên khác, nhưng một alias không biến object thành bean mới. Ví dụ `@Bean({"primaryWriter", "writerAlias"})` tạo bean với tên chính `primaryWriter` và alias `writerAlias` (khi cấu hình xử lý annotation phù hợp). Khi inject `Map<String, Writer>`, khóa thường là primary bean name, không phải tự thêm mỗi alias như một bean phần tử riêng.

## 7. Dự án Java 17+ chạy được: default, fully qualified và prefix

Dự án dưới đây dùng **Spring Framework 6.2.18**, không cần Spring Boot. Ba `AnnotationConfigApplicationContext` độc lập chứng minh:

1. Default scan một package: tạo tên ngắn `localProcessor`, giữ `URLFormatter` viết hoa và tên explicit `invoiceWorkflow`.
2. Fully-qualified scan hai package: hai class cùng tên ngắn `LocalProcessor` cùng được đăng ký nhưng có **tên đầy đủ khác nhau**.
3. Prefix generator: đổi fallback thành `site_*` nhưng giữ tên explicit.

### 7.1. Cấu trúc

```text
bean-name-generator-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/
    └── demo/
        ├── app/
        │   ├── BeanNamingDemo.java
        │   └── PrefixBeanNameGenerator.java
        ├── catalog/
        │   ├── CatalogPackage.java
        │   ├── LocalProcessor.java
        │   ├── URLFormatter.java
        │   └── InvoiceService.java
        └── billing/
            ├── BillingPackage.java
            └── LocalProcessor.java
```

File `settings.gradle`:

```groovy
rootProject.name = 'bean-name-generator-demo'
```

File `build.gradle`:

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.18'
}

application {
    mainClass = 'demo.app.BeanNamingDemo'
}
```

### 7.2. Main và các cấu hình scanner

File `src/main/java/demo/app/BeanNamingDemo.java`:

```java
package demo.app;

import demo.billing.BillingPackage;
import demo.catalog.CatalogPackage;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

public class BeanNamingDemo {

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(DefaultConfig.class)) {
            System.out.println("== DEFAULT ==");
            System.out.println("localProcessor = " +
                    context.containsBean("localProcessor"));
            System.out.println("URLFormatter = " +
                    context.containsBean("URLFormatter"));
            System.out.println("invoiceWorkflow = " +
                    context.containsBean("invoiceWorkflow"));
        }

        try (var context =
                     new AnnotationConfigApplicationContext(QualifiedConfig.class)) {
            System.out.println("== QUALIFIED ==");
            System.out.println("catalog = " +
                    context.containsBean("demo.catalog.LocalProcessor"));
            System.out.println("billing = " +
                    context.containsBean("demo.billing.LocalProcessor"));
            System.out.println("explicit = " +
                    context.containsBean("invoiceWorkflow"));
            System.out.println("shortName = " +
                    context.containsBean("localProcessor"));
        }

        try (var context =
                     new AnnotationConfigApplicationContext(PrefixConfig.class)) {
            System.out.println("== PREFIX ==");
            System.out.println("prefixed = " +
                    context.containsBean("site_localProcessor"));
            System.out.println("acronym = " +
                    context.containsBean("site_URLFormatter"));
            System.out.println("explicit = " +
                    context.containsBean("invoiceWorkflow"));
        }
    }
}

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = CatalogPackage.class)
class DefaultConfig {
}

@Configuration(proxyBeanMethods = false)
@ComponentScan(
        basePackageClasses = {CatalogPackage.class, BillingPackage.class},
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class
)
class QualifiedConfig {
}

@Configuration(proxyBeanMethods = false)
@ComponentScan(
        basePackageClasses = CatalogPackage.class,
        nameGenerator = PrefixBeanNameGenerator.class
)
class PrefixConfig {
}
```

`DefaultConfig` chỉ quét `demo.catalog`, nên không gặp bean `demo.billing.LocalProcessor` trùng tên ngắn. `QualifiedConfig` quét hai package và sử dụng tên fully-qualified. `PrefixConfig` quét một package để chứng minh hành vi prefix. Ba context riêng biệt không chia sẻ bean registry.

File `src/main/java/demo/app/PrefixBeanNameGenerator.java`:

```java
package demo.app;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;

public class PrefixBeanNameGenerator extends AnnotationBeanNameGenerator {

    public PrefixBeanNameGenerator() {
    }

    @Override
    protected String buildDefaultBeanName(BeanDefinition definition) {
        return "site_" + super.buildDefaultBeanName(definition);
    }
}
```

Public constructor không tham số bảo đảm generator có thể được Spring khởi tạo khi đọc `@ComponentScan`. Subclass chỉ tùy biến fallback name, còn metadata tên explicit vẫn do `AnnotationBeanNameGenerator` xử lý.

### 7.3. Marker classes

File `src/main/java/demo/catalog/CatalogPackage.java`:

```java
package demo.catalog;

public final class CatalogPackage {
    private CatalogPackage() {
    }
}
```

File `src/main/java/demo/billing/BillingPackage.java`:

```java
package demo.billing;

public final class BillingPackage {
    private BillingPackage() {
    }
}
```

Các marker không mang `@Component`, chỉ xác định package root qua `basePackageClasses`.

### 7.4. Components trong package catalog

File `src/main/java/demo/catalog/LocalProcessor.java`:

```java
package demo.catalog;

import org.springframework.stereotype.Component;

@Component
public class LocalProcessor {
    public String id() {
        return "catalog";
    }
}
```

File `src/main/java/demo/catalog/URLFormatter.java`:

```java
package demo.catalog;

import org.springframework.stereotype.Component;

@Component
public class URLFormatter {
    public String format(String value) {
        return "[" + value + "]";
    }
}
```

File `src/main/java/demo/catalog/InvoiceService.java`:

```java
package demo.catalog;

import org.springframework.stereotype.Service;

@Service("invoiceWorkflow")
public class InvoiceService {
    public String name() {
        return "invoice";
    }
}
```

Class `URLFormatter` cố ý bắt đầu bằng hai chữ in hoa để biểu diễn ngoại lệ JavaBeans. `InvoiceService` đặt tên tường minh để kiểm tra generator không đổi tên đã định.

### 7.5. Component trùng tên ngắn ở package billing

File `src/main/java/demo/billing/LocalProcessor.java`:

```java
package demo.billing;

import org.springframework.stereotype.Component;

@Component
public class LocalProcessor {
    public String id() {
        return "billing";
    }
}
```

Hai class `demo.catalog.LocalProcessor` và `demo.billing.LocalProcessor` có tên class ngắn giống nhau nhưng **kiểu Java khác nhau** vì khác package. Java cho phép cả hai tồn tại trên classpath. Bean name mặc định kiểu tên ngắn mới là nguồn xung đột khi quét chung.

### 7.6. Chạy và stdout

Dùng JDK 17+ và một phiên bản Gradle tương thích với JDK:

```bash
gradle run --quiet
```

Nếu dự án có Gradle Wrapper thì dùng `./gradlew run --quiet` trên Linux/macOS hoặc `.\gradlew.bat run --quiet` trên Windows PowerShell. Các dòng chương trình in ra:

```text
== DEFAULT ==
localProcessor = true
URLFormatter = true
invoiceWorkflow = true
== QUALIFIED ==
catalog = true
billing = true
explicit = true
shortName = false
== PREFIX ==
prefixed = true
acronym = true
explicit = true
```

**Phân tích:**

- Default: `LocalProcessor` thành `localProcessor`; `URLFormatter` giữ tên `URLFormatter`; `@Service("invoiceWorkflow")` giữ explicit name.
- Qualified: hai `LocalProcessor` được sinh tên `demo.catalog.LocalProcessor` và `demo.billing.LocalProcessor`, không còn tranh cùng `localProcessor`. Tên explicit `invoiceWorkflow` vẫn giữ nguyên.
- Prefix: `LocalProcessor` thành `site_localProcessor` và `URLFormatter` thành `site_URLFormatter`; `InvoiceService` không bị thêm prefix vì có tên stereotype khai báo rõ.
- `containsBean(...)` kiểm tra sự hiện diện của tên bean/alias trong context. Ví dụ dùng các tên không khai báo alias, nên kết quả cũng chứng minh tên được đăng ký.

**Giới hạn của ví dụ:** các class không có dependency phức tạp, để tách riêng hành vi bean naming khỏi vấn đề autowiring. Chương trình không dùng XML, web server, database hoặc repository khác.

## 8. Custom stereotype và `@AliasFor` ảnh hưởng bean name

Một nhóm muốn thay `@Service` bằng stereotype riêng `@UseCase` để thể hiện vai trò use case. Annotation tự định nghĩa có thể meta-annotate `@Component`:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface UseCase {
    @AliasFor(annotation = Component.class, attribute = "value")
    String name() default "";
}
```

Đây là **nội dung minh họa file `UseCase.java`**, không phải phần của dự án chạy ở mục 7. Cách sử dụng:

```java
@UseCase(name = "fulfillOrder")
public class FulfillOrderUseCase {
}
```

Khi được scan, tên tường minh là `fulfillOrder`. Nếu không cung cấp `name`, strategy được chọn sẽ sinh fallback name như các component khác.

Từ Spring Framework 6.1, việc custom stereotype ngầm hiểu một thuộc tính `value` theo convention mà không có khai báo alias tường minh đã bị deprecate. Dùng `@AliasFor(annotation = Component.class, attribute = "value")` giúp annotation tùy biến công bố metadata tên rõ ràng.

Khi dùng custom generator kế thừa `AnnotationBeanNameGenerator` như ở mục 5, explicit name của custom stereotype có alias hợp lệ tiếp tục được tôn trọng. Nếu viết `BeanNameGenerator` từ đầu, đừng mặc định rằng tự nhìn `definition.getBeanClassName()` sẽ nhận ra mọi composed stereotype và alias.

## 9. `@Bean`, `@Import` và component scan có quy tắc khác nhau

Điểm quan trọng: không có một quy tắc naming duy nhất áp dụng bất biến cho mọi bean definition.

### 9.1. `@Bean` method

Tên mặc định của một bean do `@Bean` method tạo thường là **tên method**:

```java
@Configuration
class InfraConfig {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean("utcClock")
    Clock configuredClock() {
        return Clock.systemUTC();
    }
}
```

Đây là **đoạn minh họa**, cần imports `java.time.Clock` cùng `@Configuration` và `@Bean`. Bean từ method đầu có tên mặc định `systemClock`; method thứ hai có tên explicit `utcClock`. Thêm `FullyQualifiedAnnotationBeanNameGenerator` vào `@ComponentScan` **không tự đổi** `systemClock` thành tên fully-qualified.

### 9.2. `@Import` class cấu hình

Spring Framework 6.2 có trường hợp sử dụng `FullyQualifiedAnnotationBeanNameGenerator` làm mặc định cho **configuration-level import**. Vì vậy một class được nhập bởi `@Import` có thể mang tên khác với chính class ấy khi được phát hiện bằng scanner mặc định. Cần kiểm tra chính đường đi đăng ký khi mã phụ thuộc vào tên bean.

Không nên đưa tên của import-generated bean thành một string contract nếu framework có API tham chiếu theo type hoặc nếu không có nhu cầu chọn cụ thể tên. Khi gặp cấu hình có cả scan và import, hãy dùng `containsBean`/`getBeanDefinitionNames` để xác minh tên thực trong context.

### 9.3. Đăng ký trực tiếp

Các API như `GenericApplicationContext.registerBean(...)` có chiến lược đặt tên và overload riêng. Nếu caller cung cấp tên, tên đó có thể không liên quan đến `AnnotationBeanNameGenerator` của scanner. Đừng kỳ vọng thay một `@ComponentScan(nameGenerator = ...)` sẽ đổi tất cả bean name do mọi extension point tạo ra.

## 10. Đổi naming policy trong hệ thống đang chạy: tác động cần biết

Đổi naming generator từ tên class ngắn sang fully-qualified là một **thay đổi giao diện cấu hình** khi ứng dụng đang tham chiếu tên bằng string. Các điểm sau có thể bị ảnh hưởng:

| Thành phần sử dụng tên | Ví dụ trước | Ví dụ sau khi đổi sang fully-qualified |
| --- | --- | --- |
| `getBean("localProcessor")` | Lookup thành công | Có thể không tồn tại; cần `getBean("demo.catalog.LocalProcessor")` |
| `@Qualifier("localProcessor")` | Phân giải theo tên mặc định | Có thể cần qualifier tường minh trên bean hoặc cập nhật tên |
| `@Resource(name="localProcessor")` | Chọn bean theo tên | Có thể thất bại nếu không còn alias tương ứng |
| `Map<String, Processor>` | Key `localProcessor` | Key `demo.catalog.LocalProcessor` |
| Logging/monitoring định danh bean | `localProcessor` | `demo.catalog.LocalProcessor` |
| Tên explicit `@Service("invoiceWorkflow")` | `invoiceWorkflow` | Giữ nguyên nếu generator tôn trọng stereotype name |

Đổi generator **không tự tạo alias** cho tên cũ. Nếu tương thích ngược quan trọng, cần xác định rõ tên nào là hợp đồng; cân nhắc đặt explicit names cho bean đang được tham chiếu, hoặc đăng ký alias có kiểm soát thay vì hy vọng Spring hỗ trợ cả hai tên mặc định.

Tương tự, prefix generator làm đổi tên component không khai báo tên. Một tiền tố dễ đọc có thể giúp log hoặc namespace, nhưng không nên coi nó là cách thay cho dependency injection theo type/qualifier.

## 11. Lỗi thường gặp và cách xử lý

| Triệu chứng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| Hai component ở hai package gây `ConflictingBeanDefinitionException` | Trùng simple class name, đều sinh cùng tên ngắn | Đặt explicit names khác nhau hoặc dùng fully-qualified generator |
| `@Component("foo")` vẫn tên `foo` sau khi dùng fully-qualified | Tên tường minh được ưu tiên | Đây là hành vi đúng; thay annotation nếu muốn đổi tên |
| Custom prefix không xử lý hai `LocalProcessor` | Prefix tạo cùng `site_localProcessor` | Dùng package-qualified policy hoặc FQ built-in |
| `URLFormatter` thành `URLFormatter` thay vì `uRLFormatter` | Quy tắc JavaBeans giữ hai ký tự đầu viết hoa | Kiểm tra `Introspector.decapitalize`, không tự lowercase một ký tự |
| `@Bean` method không đổi tên dù đã cấu hình `@ComponentScan(nameGenerator=...)` | Generator chỉ tham gia luồng component scan | Đặt `@Bean(name=...)` hoặc dùng registration policy phù hợp |
| `context.getBean("localProcessor")` không còn tìm thấy | Đã chuyển sang tên fully-qualified hoặc prefix | In bean names thực tế; cập nhật string reference/alias |
| `@Qualifier` bắt đầu lỗi sau đổi tên | Qualifier dựa vào bean name trước đó | Dùng qualifier annotation rõ nghĩa hoặc đồng bộ reference |
| Custom stereotype không dùng được explicit name | Thiếu `@AliasFor` trong composed annotation | Khai báo alias tới `@Component.value` |
| Generator custom bị lỗi khởi tạo | Constructor không tham số không khả dụng, class không load được hoặc exception | Cung cấp public no-arg constructor; kiểm tra classpath |
| Có hai bean type giống nhau dù tên khác | Xung đột ở autowiring, không phải registry naming | Dùng `@Qualifier`/`@Primary` hoặc tiêm collection |
| Mở rộng scan rồi xuất hiện xung đột | Bây giờ có thêm component mang cùng tên ngắn | So sánh package/classes và xem lại chiến lược naming |
| Đặt tên bean theo thứ tự quét | Registry có thể được xử lý theo trình tự thay đổi | Dùng quy tắc xác định được, có test và ổn định khi refactor |
| Spring Boot config kết quả lạ | Thêm `@ComponentScan` đã đổi root/filters hoặc có scan khác | Kiểm tra composition của `@SpringBootApplication` và registry cuối |

**Phân biệt lỗi:** tên bean bị trùng là vấn đề registration; thiếu bean là vấn đề scanning/registration/condition; nhiều bean type là vấn đề dependency resolution. Ba loại lỗi cần những hướng khắc phục khác nhau.

## 12. Kiểm tra bean names bằng code thay vì suy đoán

Một đoạn kiểm tra đơn giản trong ứng dụng đã có `ApplicationContext context`:

```java
String[] names = context.getBeanNamesForType(MyService.class);
java.util.Arrays.sort(names);
System.out.println(java.util.Arrays.toString(names));
```

Đây là **đoạn minh họa**, cần class `MyService` và biến `context` thực tế. Việc sort mảng giúp output ổn định khi so sánh. Nếu chỉ muốn xem một bean name đã biết:

```java
boolean present = context.containsBean("demo.catalog.LocalProcessor");
```

Khi cần hiểu một bean có alias nào:

```java
String[] aliases = context.getAliases("invoiceWorkflow");
```

Một số hướng kiểm chứng:

1. **Phân biệt context**: Bean ở child context không mặc nhiên nằm trong mọi parent context. Debug đúng context đang thực hiện injection/lookup.
2. **Đối chiếu config thực sự được sử dụng**: `@ComponentScan` với `nameGenerator` phải nằm trên configuration được đăng ký/import.
3. **Quan sát registration path**: Bean được scan, tạo qua `@Bean` hay được đăng ký bằng API khác?
4. **Đọc cả explicit name**: Tên khai báo trong stereotype hoặc custom annotation có alias sẽ thắng fallback name trong các generator built-in.
5. **Kiểm tra package root**: Fully-qualified generator không làm scanner quét rộng hơn; nó chỉ đặt tên các component đã được phát hiện.
6. **Kiểm tra dependency consumer**: Có `@Qualifier("...")` hoặc string lookup hard-code phụ thuộc tên trước đó hay không?

Một unit/integration test tạo `AnnotationConfigApplicationContext` với cấu hình scan nhỏ như mục 7 là cách kiểm chứng hiệu quả. Nếu đổi generator trong ứng dụng hiện hữu, nên có test riêng cho tên bean được xem là giao diện ổn định.

## 13. Bài tập tự thực hành

### Bài tập 1 — Dự đoán tên mặc định

Cho các class trong package `com.acme.sales` mang `@Component` mà không khai báo tên: `TaxService`, `XMLClient`, `ApiGateway`. Dưới `AnnotationBeanNameGenerator`, tên của chúng là gì?

**Đáp án:** `taxService`, `XMLClient`, `apiGateway`. `XMLClient` giữ chữ đầu nguyên dạng vì hai ký tự đầu đều viết hoa.

### Bài tập 2 — Xung đột khi scan hai nhánh

Hai class `com.acme.billing.ReportHandler` và `com.acme.audit.ReportHandler` đều có `@Component` không tên. Scanner mặc định quét cả hai thì có nguy cơ gì?

**Đáp án:** cả hai tên `reportHandler`; Spring có thể báo conflict khi đăng ký hai bean definitions không tương thích. Sử dụng `@Component("billingReportHandler")`/`@Component("auditReportHandler")` hoặc `FullyQualifiedAnnotationBeanNameGenerator`.

### Bài tập 3 — Thay một bean name mà không sửa generator

Dự án dùng `@Service class InvoiceService`, một file khác lookup `"invoiceService"`. Nếu muốn đổi class thành `BillingService` nhưng không đổi lookup, làm thế nào?

**Đáp án:** đặt `@Service("invoiceService")` trên `BillingService` (nếu tên cũ vẫn thể hiện đúng hợp đồng nghiệp vụ) hoặc chủ động cập nhật mọi nơi tham chiếu. Đổi class đơn lẻ làm tên mặc định thay đổi.

### Bài tập 4 — Hai tên explicit giống nhau

Hai class khác package đều khai báo `@Component("sharedProcessor")`. Đổi sang fully-qualified generator có xử lý xung đột không?

**Đáp án:** **Không**. Built-in generator ưu tiên tên tường minh; phải đổi ít nhất một explicit name hoặc chỉ đăng ký các bean có chủ đích.

### Bài tập 5 — Prefix generator và composed stereotype

Class `@UseCase(name="fulfillOrder") FulfillOrderUseCase` dùng custom stereotype đã gắn `@AliasFor`, scanner cấu hình `PrefixBeanNameGenerator`. Bean name nào được chọn?

**Đáp án:** `fulfillOrder`, vì tên tường minh được đọc trước khi gọi fallback `buildDefaultBeanName`. Nếu không khai báo name, generator sẽ dùng `site_fulfillOrderUseCase`.

### Bài tập 6 — FQ name nhưng autowiring vẫn mơ hồ

Hai class implement `Notifier` có tên bean fully-qualified khác nhau. Một constructor inject `Notifier notifier` và container vẫn báo `NoUniqueBeanDefinitionException`. Tại sao?

**Đáp án:** tên đăng ký đã phân biệt, nhưng có hai ứng viên cùng type cho **dependency đơn**. Chọn qua `@Qualifier`, `@Primary` hoặc thiết kế dependency rõ ràng.

### Bài tập 7 — Thay `@Bean` method

`@Configuration` có method `@Bean Clock systemClock()`. Cấu hình `@ComponentScan(nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)` có tự đổi tên bean `systemClock` không?

**Đáp án:** **Không**. Bean factory method có quy tắc đặt tên riêng; muốn đổi tên, dùng `@Bean("newName")` và rà soát các tham chiếu.

## 14. Checklist cho thiết kế naming policy

Trước khi thay generator trên một dự án:

- Xác định các bean name nào được dùng trong `@Qualifier`, `@Resource`, `getBean(String)`, map keys, config hoặc integration.
- Liệt kê xem những tên đang dựa vào class name ngầm định hay đã được khai báo tường minh.
- Xác định vấn đề: trùng tên ngắn giữa package, nhu cầu namespace hay chỉ muốn tên dễ đọc.
- Cân nhắc `FullyQualifiedAnnotationBeanNameGenerator` trước khi tự viết generator để giải quyết xung đột tên ngắn.
- Nếu viết custom generator, bảo đảm constructor không tham số, naming deterministic và behavior với explicit stereotype names được kiểm tra.
- Kiểm tra những path đăng ký nằm ngoài component scan: `@Bean`, `@Import` và API registration.
- Dùng context test/inspection để xác nhận tên chính, alias, type và kết quả injection trước khi đổi policy trong ứng dụng đang vận hành.

## 15. Ghi nhớ

- **Default component scan:** `AnnotationBeanNameGenerator` sinh tên ngắn theo JavaBeans khi không có explicit stereotype name.
- **Tên tường minh:** `@Component("x")`, `@Service("x")` và stereotype composed có `@AliasFor` thường giữ tên `x` dù đổi built-in generator.
- **Tên class viết tắt:** `URLFormatter` vẫn là `URLFormatter` ở default strategy, không phải `uRLFormatter`.
- **Xung đột package:** hai `LocalProcessor` ở hai package đều thành `localProcessor` dưới default generator; dùng tên tường minh hoặc `FullyQualifiedAnnotationBeanNameGenerator`.
- **Custom generator:** kế thừa `AnnotationBeanNameGenerator` và override `buildDefaultBeanName` nếu chỉ muốn tùy biến fallback.
- **Đúng phạm vi:** `@ComponentScan(nameGenerator=...)` không thay đổi mọi nguồn đăng ký bean và không mở rộng package scan.
- **Tên bean khác lựa chọn bean:** naming giải quyết bean registry identity; qualifier/primary/selection policy giải quyết injection.

### Tài liệu chính thức

- [Spring Framework Reference — Classpath Scanning: Naming Autodetected Components](https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html).
- [Spring Framework 6.2 Javadoc — `BeanNameGenerator`](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/support/BeanNameGenerator.html).
- [Spring Framework 6.2 Javadoc — `AnnotationBeanNameGenerator`](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/AnnotationBeanNameGenerator.html).
- [Spring Framework 6.2 Javadoc — `FullyQualifiedAnnotationBeanNameGenerator`](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/FullyQualifiedAnnotationBeanNameGenerator.html).
- [Spring Framework 6.2 Javadoc — `@ComponentScan`](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/ComponentScan.html).
- [Spring Framework — Bean Naming Conventions](https://docs.spring.io/spring-framework/reference/core/beans/definition.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="component-scanning-007">Tạo stereotype ghép bằng meta-annotation và @AliasFor</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo stereotype ghép bằng meta-annotation và @AliasFor trong Spring Core Container

## Mục tiêu học tập

Trong Spring, một class có `@Component`, `@Service` hoặc `@Repository` có thể được phát hiện qua **component scanning** và đăng ký thành bean. Nhưng trong dự án lớn, ta thường muốn diễn đạt vai trò của class bằng annotation riêng như `@DomainService`, `@PaymentHandler` hoặc `@ApplicationUseCase`. Một annotation riêng chỉ có ích cho container nếu Spring hiểu nó là **stereotype** và đọc đúng metadata liên quan.

Hai cơ chế giúp giải quyết bài toán này là:

1. **Meta-annotation:** đặt một annotation lên định nghĩa của annotation khác. Ví dụ, đặt `@Component` lên `@DomainService` để lớp mang `@DomainService` cũng được coi là ứng viên component scan.
2. **`@AliasFor`:** khai báo một thuộc tính của annotation là alias cho thuộc tính khác, có thể nằm trong **cùng annotation** hoặc ở **meta-annotation**. Ví dụ cho phép `@DomainService(beanName = "invoiceProcessor")` chỉ định tên bean thông qua `@Component.value`.

Sau bài học, bạn sẽ:

- Tạo một custom stereotype được Spring phát hiện mà không cần đặt thêm `@Component` trên từng class.
- Dùng `@Target`, `@Retention` và `@Documented` đúng mục đích.
- Ánh xạ tên bean qua `@AliasFor(annotation = Component.class, attribute = "value")`.
- Phân biệt explicit alias, alias cho thuộc tính meta-annotation, và implicit alias.
- Hiểu yêu cầu về kiểu trả về, giá trị mặc định và phạm vi meta-annotation.
- Biết cách kiểm tra bean name và annotation đã merge bằng Spring API.
- Phát hiện lỗi phổ biến khi annotation xuất hiện trên class nhưng component scan không đăng ký bean.

**Giới hạn phạm vi:** Bài tập trung vào custom stereotype của **Spring Core Container**, không xây cơ chế xử lý annotation hoàn toàn mới hay dùng AOP làm trọng tâm. `@Transactional`, `@Qualifier` và custom filter chỉ được nhắc khi cần phân biệt trách nhiệm.

## 1. St​​ereotype là gì?

Spring gọi những annotation phân loại vai trò của một class thành **stereotype annotations**. `@Component` là stereotype tổng quát. `@Service`, `@Repository` và `@Controller` chuyên biệt hơn, nhưng đều được xây dựa trên cơ chế `@Component`.

Một phiên bản lược giản của `@Service` có dạng:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Service {
    // Có thể khai báo các thuộc tính riêng.
}
```

Đây là **đoạn mô tả cấu trúc**, không phải mã để định nghĩa lại `org.springframework.stereotype.Service` trong dự án. Spring phát hiện class gắn `@Service` vì trong định nghĩa `@Service` đã có meta-annotation `@Component`.

Mối quan hệ:

```text
@Component
    ↑ meta-annotation
@DomainService
    ↑ annotation áp dụng lên class
InvoiceProcessor
    ↓ component scan
BeanDefinition cho InvoiceProcessor
    ↓
Spring container quản lý bean
```

Việc một class được coi là **ứng viên** không tự bảo đảm ApplicationContext khởi động thành công: class có thể bị loại bởi filter, thiếu dependency, có lỗi khi tạo bean, hoặc nằm ngoài package được scan.

Một custom stereotype hữu ích khi cùng một vai trò xuất hiện lặp lại trong mã:

```java
@DomainService
public class InvoiceProcessor {
    // ...
}
```

Người đọc nhận ra đây là dịch vụ miền nghiệp vụ, trong khi Spring hiểu nó như một component bình thường. Có thể thêm metadata có ý nghĩa khác cho annotation tùy nhu cầu, nhưng cần xác định rõ annotation đó ảnh hưởng **đăng ký bean**, **đặt tên**, **scope** hay **hành vi runtime**.

## 2. Ba khái niệm annotation cần nắm

### 2.1. Annotation thường

Java cho phép đánh dấu class:

```java
@SomeMarker
public class Example {
}
```

Nếu `@SomeMarker` chỉ là annotation tự tạo mà không có meta-annotation `@Component`, Spring mặc định **không** coi class này là component ứng viên chỉ vì có marker. Có thể viết custom include filter để quét annotation bất kỳ, nhưng đó là một cấu hình khác.

### 2.2. Meta-annotation

Một annotation đặt trên **định nghĩa annotation khác** được gọi là meta-annotation:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface DomainService {
}
```

Sau khi custom stereotype có meta-annotation `@Component`, những class đánh dấu `@DomainService` nằm trong vùng component scan mặc định có thể được Spring phát hiện.

Về mặt phân loại, `@Service` cũng là một stereotype meta-annotated bằng `@Component`. Nếu custom annotation được meta-annotate bằng `@Service`, `@Component` xuất hiện **gián tiếp** qua một tầng nữa. Spring có khả năng đọc chuỗi meta-annotation phù hợp, nhưng khi chỉ cần ví dụ đặt tên bean cơ bản, meta-annotate trực tiếp bằng `@Component` giúp cấu trúc dễ kiểm tra hơn.

### 2.3. Composed annotation

Một **composed annotation** (annotation ghép) tích hợp những meta-annotation và có thể **mở lại một số thuộc tính** cho người sử dụng. Chẳng hạn annotation dành cho feature có thể kết hợp ý nghĩa "đây là component" với một quy tắc tên bean, scope hoặc qualifier.

Cơ chế ghép không đồng nghĩa với Java tự sao chép thuộc tính giữa annotations. Spring phải đọc **merged annotation model** để áp dụng override và alias.

Ví dụ:

```java
@DomainService(beanName = "invoiceProcessor")
public class InvoiceProcessor {
}
```

Ở đây `beanName` là thuộc tính tự đặt tên của `@DomainService`. Để Spring dùng nó làm tên bean, cần định nghĩa alias tường minh tới `@Component.value`.

## 3. Định nghĩa custom stereotype chuẩn

Một annotation custom điển hình:

```java
package demo.stereotype;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface DomainService {

    @AliasFor(annotation = Component.class, attribute = "value")
    String beanName() default "";
}
```

Ý nghĩa các thành phần:

| Thành phần | Vai trò |
| --- | --- |
| `@Target(ElementType.TYPE)` | Annotation áp dụng cho class/interface hoặc loại declaration thuộc `TYPE`. Trong component scan, đối tượng cần đăng ký là class ứng viên. |
| `@Retention(RetentionPolicy.RUNTIME)` | Annotation còn trong class file và có thể được đọc khi chạy. |
| `@Documented` | Đưa annotation vào tài liệu Javadoc của phần tử được đánh dấu; không làm Spring đăng ký bean. |
| `@Component` | Nói với component scanner rằng annotation này là một stereotype, khi có trong chuỗi meta-annotation. |
| `@AliasFor(...)` | Nói với Spring rằng `DomainService.beanName` ghi đè/ánh xạ sang `Component.value`. |
| `String beanName() default ""` | Người dùng có thể cung cấp tên bean hoặc để trống để dùng cơ chế tạo tên mặc định. |

**Quan trọng:** `@AliasFor` phải nằm **trên phương thức thuộc tính của annotation** (`beanName()`). Không đặt `@AliasFor` trên class thực thi `InvoiceProcessor` hoặc trên field Java thông thường.

`@Target` và `@Retention` đến từ `java.lang.annotation`; `@Component` đến từ `org.springframework.stereotype`; `@AliasFor` đến từ `org.springframework.core.annotation`. Lẫn package import sẽ khiến ví dụ không biên dịch hoặc hoạt động không đúng mục tiêu.

## 4. Vì sao cần @AliasFor để đặt tên bean?

`@Component` định nghĩa thuộc tính `String value() default ""`. Khi gọi:

```java
@Component("invoiceProcessor")
public class InvoiceProcessor {
}
```

Spring có thể dùng `invoiceProcessor` làm tên bean.

Giả sử bạn tạo:

```java
@Component
public @interface DomainService {
    String beanName() default "";
}
```

rồi viết:

```java
@DomainService(beanName = "invoiceProcessor")
public class InvoiceProcessor {
}
```

**Nếu không có `@AliasFor`**, không thể giả định Spring tự hiểu thuộc tính tùy ý tên `beanName` là `Component.value`. Vì vậy, dù class có thể vẫn được quét nhờ `@Component`, tên bean tùy chỉnh có thể **không được áp dụng**.

Thêm:

```java
@AliasFor(annotation = Component.class, attribute = "value")
String beanName() default "";
```

để khai báo rõ quan hệ "`DomainService.beanName` là bản ghi đè cho `Component.value`". Từ Spring Framework 6.1, cơ chế hỗ trợ tên stereotype với thuộc tính không gọi là `value` dựa trên alias tường minh này. Cũng từ 6.1, cách dựa vào quy ước **trùng tên `value` nhưng không có `@AliasFor`** đã bị đánh dấu deprecated; khi tạo annotation mới, nên khai báo alias rõ ràng.

### Khi để trống tên

Nếu dùng:

```java
@DomainService
public class ReportService {
}
```

`beanName()` dùng mặc định `""`. Khi không có tên tường minh khác, `AnnotationBeanNameGenerator` mặc định có thể tạo bean name `reportService` từ tên class `ReportService` theo quy tắc decapitalize của Spring.

Nếu cấu hình scanner dùng `BeanNameGenerator` tùy chỉnh, tên thực tế có thể khác. Đừng xây logic quan trọng dựa trên một tên bean mặc định khi ứng dụng có thể đổi chiến lược đặt tên.

## 5. Ví dụ runnable Java 17 và Spring Framework 6.2

Ví dụ bên dưới có hai class được đánh dấu cùng một custom stereotype:

- `InvoiceProcessor` dùng tên bean tường minh `invoiceProcessor` thông qua `beanName`.
- `ReportService` không chỉ định tên, dùng bean name mặc định `reportService`.

Spring dùng `@ComponentScan` để tìm hai class trong package `demo.app`. Chúng ta sẽ kiểm tra bean có thể được lấy theo tên và theo kiểu.

### 5.1. Cấu trúc thư mục

```text
composed-stereotype-demo/
├── pom.xml
└── src/main/java/
    └── demo/
        ├── Main.java
        ├── config/
        │   └── AppConfig.java
        ├── stereotype/
        │   └── DomainService.java
        └── app/
            ├── billing/
            │   └── InvoiceProcessor.java
            └── report/
                └── ReportService.java
```

### 5.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>composed-stereotype-demo</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.18</version>
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

### 5.3. Tạo annotation @DomainService

Tệp `src/main/java/demo/stereotype/DomainService.java`:

```java
package demo.stereotype;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface DomainService {

    @AliasFor(annotation = Component.class, attribute = "value")
    String beanName() default "";
}
```

Annotation này không có code khởi tạo bean theo cách thủ công. Khi component scanner gặp class mang `@DomainService` ở package được quét, Spring nhận ra `@Component` là meta-annotation và đăng ký class đó như ứng viên component. Alias ảnh hưởng đến **metadata tên bean**.

### 5.4. Service có tên bean tường minh

Tệp `src/main/java/demo/app/billing/InvoiceProcessor.java`:

```java
package demo.app.billing;

import demo.stereotype.DomainService;

@DomainService(beanName = "invoiceProcessor")
public class InvoiceProcessor {

    public String execute() {
        return "invoice-created";
    }
}
```

Class không trực tiếp gắn `@Component` hoặc `@Service`. Custom annotation đã đủ để component scanner nhận ra nó, nếu package `demo.app.billing` nằm trong vùng quét và không có filter loại bỏ.

### 5.5. Service dùng tên mặc định

Tệp `src/main/java/demo/app/report/ReportService.java`:

```java
package demo.app.report;

import demo.stereotype.DomainService;

@DomainService
public class ReportService {

    public String render() {
        return "report-ready";
    }
}
```

Vì không truyền `beanName`, mặc định là chuỗi rỗng. Bean name mặc định tương ứng class `ReportService` thường là `reportService`.

### 5.6. Bật component scan

Tệp `src/main/java/demo/config/AppConfig.java`:

```java
package demo.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "demo.app")
public class AppConfig {
}
```

Việc class cấu hình `AppConfig` ở `demo.config` không cản trở khả năng quét `demo.app`, vì `basePackages` đã chỉ định gốc rõ ràng. Class cấu hình được đưa vào context tường minh trong `Main`.

### 5.7. Truy vấn container và kiểm tra kết quả

Tệp `src/main/java/demo/Main.java`:

```java
package demo;

import demo.app.billing.InvoiceProcessor;
import demo.app.report.ReportService;
import demo.config.AppConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            System.out.println("named=" +
                    context.containsBean("invoiceProcessor"));
            System.out.println("default=" +
                    context.containsBean("reportService"));

            InvoiceProcessor invoice =
                    context.getBean("invoiceProcessor", InvoiceProcessor.class);
            ReportService report =
                    context.getBean(ReportService.class);

            System.out.println("invoice=" + invoice.execute());
            System.out.println("report=" + report.render());
        }
    }
}
```

### 5.8. Chạy chương trình

Từ thư mục chứa `pom.xml`, dùng JDK 17+ và Maven:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.Main
```

Các dòng do chương trình in ra (không tính log Maven hoặc logging của môi trường):

```text
named=true
default=true
invoice=invoice-created
report=report-ready
```

**Giải thích kết quả:**

1. `AppConfig` yêu cầu scanner quét `demo.app` và các package con, nên cả `demo.app.billing` lẫn `demo.app.report` đều được xem xét.
2. Hai class mang `@DomainService`. Vì annotation này được meta-annotated bằng `@Component`, Spring nhận chúng là component ứng viên.
3. `InvoiceProcessor` có `beanName = "invoiceProcessor"`. Alias tường minh giúp Spring hiểu đây là giá trị cho `Component.value` và đăng ký bean với tên `invoiceProcessor`.
4. `ReportService` để `beanName` trống, do đó mặc định nhận tên `reportService`.
5. `getBean("invoiceProcessor", InvoiceProcessor.class)` và `getBean(ReportService.class)` đều lấy được bean. Hai phương thức ứng dụng trả chuỗi cố định, vì vậy output có thể dự đoán.

**Lưu ý:** Ví dụ này trình bày một chương trình độc lập đầy đủ gồm 5 file Java và 1 POM. Các ví dụ annotation nhỏ ở các mục khác có thể chỉ là **đoạn minh họa cần đặt vào project thích hợp**.

## 6. @AliasFor hoạt động theo ba kiểu quan hệ

`@AliasFor` được khai báo trên **attribute method** bên trong annotation. Trong Spring annotation programming model, có ba kiểu quan hệ đáng phân biệt.

### 6.1. Explicit alias giữa hai thuộc tính của cùng annotation

Giả sử bạn muốn `value` và `name` là hai cách viết tương đương:

```java
// Đoạn minh họa khai báo riêng một annotation.
import org.springframework.core.annotation.AliasFor;

public @interface LocalAlias {

    @AliasFor("name")
    String value() default "";

    @AliasFor("value")
    String name() default "";
}
```

Khi annotation được Spring xử lý bằng **MergedAnnotations** hoặc các API tương đương, hai thuộc tính là explicit aliases. Các điều kiện quan trọng:

- Cả hai phải có **cùng kiểu trả về** — trong ví dụ đều là `String`.
- Cả hai phải có **giá trị mặc định giống nhau** — trong ví dụ đều là `""`.
- Nên khai báo `@AliasFor` ở **cả hai phía** để tài liệu rõ ràng và tương thích tốt.
- Trong trường hợp cùng annotation, không chỉ định `annotation = ...` vì mặc định nó trỏ về annotation hiện tại.
- Nếu cung cấp hai giá trị **khác nhau** tại cùng một annotation, Spring có thể phát hiện xung đột alias khi tổng hợp metadata.

Ví dụ dùng:

```java
@LocalAlias("workerA")
class Alpha {
}

@LocalAlias(name = "workerB")
class Beta {
}
```

Tuy nhiên, `@LocalAlias` trong ví dụ này **không có `@Component`**, nên chỉ riêng alias không làm `Alpha` hay `Beta` trở thành Spring bean. Mục đích là minh họa quan hệ alias nội bộ; không phải một ví dụ component scan hoàn chỉnh.

### 6.2. Explicit alias tới meta-annotation

Đây là dạng chính của bài:

```java
@Component
public @interface DomainService {

    @AliasFor(annotation = Component.class, attribute = "value")
    String beanName() default "";
}
```

`DomainService.beanName` là một **explicit override** cho `Component.value`. Giá trị từ annotation ở class sẽ được Spring truyền xuống cách nhìn metadata hợp nhất của `@Component`.

Các điều kiện cần hiểu:

1. `Component.class` phải là **meta-annotation được tìm thấy** trên định nghĩa của `@DomainService` (trực tiếp hoặc meta-present qua tầng hợp lệ).
2. Thuộc tính đích `value` phải tồn tại trên `@Component`.
3. Kiểu trả về của nguồn và đích phải tương thích/cùng loại: ở đây cả hai đều là `String`.
4. Ghi rõ `annotation = Component.class` và `attribute = "value"` để Spring biết thuộc tính nào được override.
5. Đảm bảo target annotation có retention/target phù hợp để metadata có thể được sử dụng.

Không thể dùng:

```java
// PHẢN VÍ DỤ — @Component không nằm trong chuỗi meta-annotation.
@Deprecated
public @interface BrokenService {
    @AliasFor(annotation = Component.class, attribute = "value")
    String name() default "";
}
```

rồi kỳ vọng Spring tự động hiểu `BrokenService` là stereotype. Ở đây `@Component` không meta-present trên `@BrokenService`; alias tham chiếu sai hệ meta-annotation và có thể khiến Spring phát hiện cấu hình annotation không hợp lệ khi đọc metadata. `@Deprecated` cũng không phải stereotype.

### 6.3. Implicit aliases khi hai thuộc tính cùng override một meta-attribute

Bạn có thể mở cả `value` lẫn `beanName`, mỗi thuộc tính cùng override `Component.value`:

```java
// Đoạn minh họa một custom stereotype khác.
// Cần import java.lang.annotation.*,
// org.springframework.core.annotation.AliasFor và
// org.springframework.stereotype.Component.

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface NamedDomainService {

    @AliasFor(annotation = Component.class, attribute = "value")
    String value() default "";

    @AliasFor(annotation = Component.class, attribute = "value")
    String beanName() default "";
}
```

Hai thuộc tính trên trở thành **implicit aliases** của nhau vì cùng trỏ đến một meta-attribute `Component.value`. Chúng cần **cùng kiểu** và **cùng giá trị mặc định**; bạn có thể dùng `@NamedDomainService("a")` hoặc `@NamedDomainService(beanName = "a")`. Nếu khai báo hai giá trị khác nhau ở cùng một vị trí, Spring có thể báo xung đột khi xử lý merged attributes.

Không cần khai báo cả hai tên nếu dự án không có yêu cầu cú pháp tương đương. Một thuộc tính `beanName()` tường minh như ví dụ đầy đủ thường đơn giản và ít gây hiểu nhầm hơn.

### 6.4. Alias qua nhiều tầng meta-annotation

Spring hỗ trợ tổng hợp metadata qua nhiều tầng. Ví dụ `@SpecializedService` được meta-annotated bằng `@DomainService`, còn `@DomainService` được meta-annotated bằng `@Component`. Một alias của `@SpecializedService` có thể nhắm vào thuộc tính meta-annotation `@DomainService` rồi được truyền tiếp sang `@Component` nếu khai báo đúng chuỗi alias.

```java
// Đoạn minh họa: DomainService là annotation đã định nghĩa.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DomainService
public @interface SpecializedService {

    @AliasFor(annotation = DomainService.class, attribute = "beanName")
    String name() default "";
}
```

Ở đây `SpecializedService.name` trỏ tới `DomainService.beanName`; `DomainService.beanName` lại trỏ tới `Component.value`. Ví dụ cho thấy khả năng **transitive meta-annotation override**, nhưng không nên tạo nhiều tầng annotation chỉ vì có thể. Mỗi tầng làm người đọc khó thấy metadata cuối cùng hơn; trong hệ thống thực, cần integration test xác nhận tên bean và phần metadata áp dụng.

## 7. Java reflection và Spring merged annotation model

Một hiểu lầm phổ biến: khai báo `@AliasFor` sẽ khiến **Java reflection thuần** tự động đọc một alias như alias kia.

Thực tế, `@AliasFor` là **metadata cho cơ chế đọc annotation của Spring**. Java reflection như `SomeClass.class.getAnnotation(...)` chỉ phản ánh annotation theo mô hình Java, không tự thực thi quy tắc override giữa meta-annotations.

Ví dụ với `InvoiceProcessor`:

```java
// Đoạn minh họa trong dự án ví dụ đầy đủ:
DomainService direct =
        InvoiceProcessor.class.getAnnotation(DomainService.class);

System.out.println(direct.beanName());
```

`direct.beanName()` trả chuỗi `"invoiceProcessor"` vì giá trị này đã được khai báo trực tiếp trên annotation. Nhưng điều đó **không chứng minh** Java reflection tự tính ra `Component.value`.

Khi cần đọc kết quả tổng hợp:

```java
// Đoạn bổ sung trong project ví dụ.
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

Component merged = AnnotatedElementUtils.findMergedAnnotation(
        InvoiceProcessor.class, Component.class);

System.out.println(merged.value());
```

Output dự kiến của đoạn bổ sung:

```text
invoiceProcessor
```

`findMergedAnnotation` dùng mô hình merged annotations của Spring, nên thấy thuộc tính `Component.value` đã được custom stereotype override. Nó cũng có thể trả `null` nếu annotation cần tìm không hiện diện; với `InvoiceProcessor` trong ví dụ này, `@Component` hiện diện ở tầng meta-annotation nên có kết quả.

Các API như `MergedAnnotations` và `AnnotatedElementUtils` là công cụ thích hợp khi tự viết infrastructure cần truy vấn composed metadata. Tránh viết một vòng reflection thủ công chỉ tìm `@Component` trực tiếp trên class rồi kết luận mọi custom stereotype đều không hợp lệ: `@Component` có thể **meta-present**.

## 8. @ComponentScan phát hiện custom stereotype ra sao?

Với:

```java
@Configuration
@ComponentScan(basePackages = "demo.app")
class AppConfig {
}
```

khi `useDefaultFilters` giữ giá trị mặc định `true`, scanner tìm những class được gắn `@Component` **trực tiếp hoặc qua meta-annotation**; `@Service` và custom `@DomainService` đều thuộc nhóm này.

Quy trình khái niệm:

```text
Quét classpath dưới demo.app
        |
        v
Đọc metadata annotation của class
        |
        v
Class có @Component trực tiếp / meta-present?
        | Có
        v
Kiểm tra bộ lọc và điều kiện ứng viên
        |
        v
Xác định bean name từ stereotype/alias hoặc generator mặc định
        |
        v
Đăng ký BeanDefinition
        |
        v
Tạo bean theo lifecycle/scope khi cần
```

Một class có `@DomainService` **không được tìm thấy** nếu nó nằm ngoài root `demo.app`, dù annotation được định nghĩa chính xác. Việc thay annotation từ `@Component` sang `@DomainService` cũng không tự mở rộng phạm vi scan.

Nếu scanner sử dụng:

```java
@ComponentScan(
    basePackages = "demo.app",
    useDefaultFilters = false
)
```

các default stereotype filters bị tắt. Khi ấy `@DomainService` không tự được nhận diện theo mặc định nữa; muốn lấy lại cần cấu hình `includeFilters` phù hợp. Vì vậy cần phân biệt **annotation được định nghĩa đúng** với **scanner có bật bộ lọc để nhận nó hay không**.

Cũng cần phân biệt một class annotation với bean definition: định nghĩa annotation chỉ làm class trở thành **candidate**; các vấn đề như dependency không thỏa, xung đột tên bean hoặc configuration conditions có thể ngăn việc đăng ký/khởi tạo thành công.

## 9. Composed stereotype và @Service/@Repository/@Controller

`@Component` là cơ sở đơn giản nhất, nhưng không phải mọi custom stereotype đều nên mang ý nghĩa chung giống nhau. Nếu muốn tạo annotation cho tầng dịch vụ, bạn có thể meta-annotate bằng `@Service`; vì `@Service` tự meta-annotated bằng `@Component`, class vẫn là component ứng viên. Khi đó cấu trúc metadata có nhiều tầng.

Tuy nhiên, cần hiểu:

- `@Service` thể hiện vai trò dịch vụ; bản thân annotation không tự bổ sung transaction hoặc security.
- `@Repository` có thể tham gia cơ chế dịch exception ở tầng persistence khi hạ tầng xử lý tương ứng được bật. Không dùng nó cho class chỉ vì muốn component scan.
- `@Controller` dành cho lớp điều khiển trong stack web; một custom stereotype meta-annotated bằng `@Controller` không tự định nghĩa các request mapping.
- `@RestController` của Spring MVC là ví dụ thực tế của annotation ghép từ `@Controller` và `@ResponseBody`, nhưng thuộc phạm vi web, không phải ví dụ core của bài.

Chọn meta-stereotype dựa trên **vai trò thật** của component. Khi mục tiêu là một annotation do ứng dụng sở hữu và có tên bean tùy chỉnh, `@Component` trực tiếp giúp xác định ý nghĩa tối thiểu rồi mở rộng khi có nhu cầu.

## 10. Ghép thêm metadata khác: không nhầm với @AliasFor

Custom stereotype có thể đặt nhiều meta-annotation nhưng mỗi annotation có hợp đồng riêng. Chẳng hạn, nếu một nhóm bean thực sự phải dùng scope prototype:

```java
// Đoạn minh họa annotation khác; cần đủ imports.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
@Scope("prototype")
public @interface PrototypeComponent {

    @AliasFor(annotation = Component.class, attribute = "value")
    String beanName() default "";
}
```

`@Scope("prototype")` quy định scope, còn `@AliasFor` ở trên chỉ quy định thuộc tính tên bean. Không suy ra rằng `@AliasFor` tự kích hoạt scope prototype. Nếu muốn công khai cả tùy chọn scope hoặc proxy mode, cần những alias đúng tới thuộc tính của `@Scope`, với kiểu trả về đúng và metadata meta-present hợp lệ.

Tương tự:

- `@Qualifier` dùng để gắn thông tin lựa chọn ứng viên tại injection point; không phải một cách đặt tên bean chung.
- `@Primary` ưu tiên một ứng viên trong single-bean autowiring; không phải alias hay yêu cầu tạo bean.
- `@Order` ảnh hưởng thứ tự ở các điểm cần sắp xếp; không làm bean tự được đăng ký.
- `@Transactional` cần hạ tầng quản lý giao dịch phù hợp để tạo hành vi transaction. Chỉ ghép annotation không bảo đảm proxy/transaction manager đã được bật.
- `@Lazy` ảnh hưởng lazy initialization hoặc lazy dependency resolution theo vị trí áp dụng; không đồng nghĩa custom stereotype là optional dependency.

Đừng gom tất cả metadata vào một annotation "vạn năng". Mỗi meta-annotation bổ sung một quyết định cấu hình và có thể tạo hiệu ứng khó quan sát khi áp dụng rộng.

## 11. Quy tắc @AliasFor cần nhớ khi thiết kế API annotation

### 11.1. Alias nội bộ phải đồng nhất kiểu và default

Đối với:

```java
@AliasFor("name")
String value() default "";

@AliasFor("value")
String name() default "";
```

hai thuộc tính có cùng kiểu `String` và cùng default `""`. Nếu một thuộc tính default là `"main"` còn thuộc tính kia là `""`, cặp alias không đáp ứng hợp đồng của Spring. Nếu một bên là `String` còn bên kia là `String[]`, kiểu cũng không khớp.

### 11.2. Alias tới meta-annotation phải trỏ đúng thuộc tính

```java
@AliasFor(annotation = Component.class, attribute = "value")
String beanName() default "";
```

`Component.value` là `String`, vì thế `beanName` cũng phải là `String`. Viết `attribute = "name"` nếu `@Component` không có `name()` là sai.

### 11.3. Không nên lạm dụng alias khi tên thuộc tính không có ý nghĩa

Nếu custom annotation chỉ để thay `@Component` bằng một tên miền nghiệp vụ và không cần đặt tên bean tùy chỉnh, có thể viết:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface DomainService {
}
```

Annotation này vẫn là custom stereotype hợp lệ. **`@AliasFor` là công cụ để khai báo quan hệ thuộc tính**, không phải bước bắt buộc cho mọi custom stereotype. Chỉ thêm khi cần công khai thuộc tính của meta-annotation hoặc tạo các tên đồng nghĩa.

### 11.4. Đừng dựa vào quy ước ngầm cũ

Một custom stereotype có `String value() default ""` nhưng không khai báo `@AliasFor` từng được một số cơ chế của Spring xử lý theo convention đặt tên. Từ Spring Framework 6.1, support đặt tên component theo quy ước kiểu này bị deprecated. Với code mới, khai báo explicit alias tới `Component.value` để ý định và khả năng tương thích rõ ràng.

### 11.5. @AliasFor không thay Java annotation semantics

Các giá trị annotation là phần của Java class metadata, không phải biến mutable. Không thể thay đổi một alias trong lúc ứng dụng chạy rồi mong mọi bean được đổi tên. Tên bean được quyết định khi Spring đăng ký bean definition; việc đọc annotation qua Spring merged model phục vụ cơ chế xử lý metadata.

## 12. Kiểm tra bằng integration test thay vì chỉ nhìn annotation

Một custom stereotype phải được kiểm tra ở **hai mức**:

1. Mức **metadata**: Spring đọc được `@Component` meta-present, và alias `beanName` được tổng hợp vào `Component.value`.
2. Mức **container**: component scanner phát hiện class ở đúng package, bean name đúng và bean có thể được inject/tra cứu theo kiểu.

Ví dụ đoạn kiểm tra thêm vào `Main` của dự án ở mục 5:

```java
// Đoạn minh họa thêm vào main, cần imports tương ứng.
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

// Sau khi context được tạo:
Component merged = AnnotatedElementUtils.findMergedAnnotation(
        InvoiceProcessor.class, Component.class);

if (merged == null ||
        !"invoiceProcessor".equals(merged.value())) {
    throw new AssertionError("Incorrect merged component name");
}
if (!context.containsBean("invoiceProcessor")) {
    throw new AssertionError("Custom stereotype bean not registered");
}
if (!context.containsBean("reportService")) {
    throw new AssertionError("Default component name not registered");
}
```

Đây là **đoạn kiểm thử bổ sung**, không in thêm stdout khi đúng. Nếu tên bean bị thay đổi do alias lỗi hoặc package scan sai, nó giúp chỉ ra sự cố. Mục đích là test **kết quả thật của container**, không phải chỉ assert annotation có mặt qua reflection.

Nếu bạn triển khai một custom annotation có nhiều alias, nên thêm test cho trường hợp dùng từng tên alias và trường hợp khai báo xung đột. Nếu alias trỏ qua nhiều tầng meta-annotation, cần test thông tin được truyền đúng đến annotation đích.

Trong một dự án có bean naming strategy riêng, test nên được viết theo chiến lược đó, không dựa cứng vào `reportService` nếu đây không phải default name generator.

## 13. Lỗi thường gặp

| Triệu chứng | Nguyên nhân thường gặp | Cách khắc phục |
| --- | --- | --- |
| Class mang `@DomainService` nhưng không có bean | Thiếu meta-annotation `@Component`, class ngoài vùng quét, hoặc scanner bị đổi filter | Kiểm tra `@Component` trên định nghĩa annotation, root scan, `useDefaultFilters` và classpath |
| `beanName` bị bỏ qua | Không khai báo alias tường minh tới `Component.value` hoặc alias cấu hình sai | Thêm `@AliasFor(annotation = Component.class, attribute = "value")` |
| Spring báo lỗi annotation configuration | Alias trỏ thuộc tính không tồn tại, meta-annotation đích không có mặt, kiểu/default không hợp lệ | Đọc exception gốc và Javadoc `@AliasFor` |
| Tên bean mặc định khác dự kiến | Dùng bean name generator riêng hoặc class name có quy tắc đặc biệt | Truy vấn tên thực tế và cấu hình generator |
| Hai component có tên bean giống nhau | Alias gán trùng tên hoặc component scan rộng hơn dự định | Chọn tên duy nhất, thiết kế namespace/scan hợp lý |
| `@AliasFor` đặt trên class sử dụng | Không hiểu nơi khai báo alias | Đặt annotation trên method `String beanName()` **bên trong định nghĩa @interface** |
| Thiếu `@Retention(RUNTIME)` | Annotation không còn runtime metadata như cần thiết | Khai báo retention runtime |
| Chỉ thấy `@DomainService` qua reflection nhưng không thấy `@Component` trực tiếp | Java reflection không tự trả meta-annotation như annotation trực tiếp | Dùng `MergedAnnotations`/`AnnotatedElementUtils` của Spring |
| Annotation xuất hiện nhưng chức năng transaction không chạy | Meta-annotate `@Transactional` mà thiếu cơ chế transaction phù hợp | Cấu hình transaction manager/proxy theo nhu cầu, phân biệt bean discovery và behavior |
| Trùng alias trong cùng annotation có giá trị khác nhau | Vi phạm hợp đồng alias khi Spring tổng hợp metadata | Chỉ cung cấp một tên alias hoặc bảo đảm các giá trị thống nhất |
| `@DomainService` dùng được trong một module nhưng không trong module khác | Classpath, root scan hoặc component filter khác nhau | Kiểm tra cấu hình ApplicationContext và dependencies |
| Marker interface bị hiểu là component | Nhầm marker kiểu `basePackageClasses` với stereotype | Marker chỉ làm mốc quét package nếu không có `@Component` |

### Quy trình debug gọn

1. Xác nhận `@DomainService` có `@Target(TYPE)`, `@Retention(RUNTIME)` và meta-annotation `@Component`.
2. Kiểm tra bean class nằm trong package mà `@ComponentScan` thật sự quét.
3. Xác định scanner còn dùng default filters hay có custom include/exclude filters.
4. Kiểm tra thuộc tính alias: annotation đích, tên thuộc tính, kiểu trả về và default.
5. Dùng `AnnotatedElementUtils.findMergedAnnotation` để xem Spring tổng hợp được `@Component` và `value` như mong muốn không.
6. Tạo ApplicationContext nhỏ rồi kiểm tra `containsBean` và `getBean`; nếu lỗi, đọc root exception trước khi sửa cấu hình.

Không nên sửa bằng cách gắn thêm cả `@Component` lên mọi class sử dụng custom stereotype chỉ để làm hệ thống "chạy được"; cách này có thể che vấn đề nằm ở chính định nghĩa annotation hoặc scan configuration.

## 14. Bài tập tự kiểm tra

### Bài 1: bỏ @Component

Nếu xóa `@Component` khỏi định nghĩa `@DomainService` nhưng giữ `@AliasFor(annotation = Component.class, attribute = "value")`, scanner mặc định có còn nhận `InvoiceProcessor` không?

**Đáp án:** Không. `@DomainService` không còn là stereotype dựa trên `@Component`; alias cũng không thể thay thế meta-annotation đích. Hơn nữa quan hệ alias tới `Component.value` không còn có chuỗi meta-annotation hợp lệ, dễ dẫn tới lỗi khi Spring xử lý metadata.

### Bài 2: đổi beanName

Đổi `@DomainService(beanName = "invoiceProcessor")` thành `@DomainService(beanName = "billingInvoice")`. Cần thay đổi lệnh lấy bean theo tên thế nào?

**Đáp án:** `context.getBean("billingInvoice", InvoiceProcessor.class)`. Kiểu class và method `execute()` không đổi, nhưng tên bean được đăng ký thay đổi. Với consumer tiêm theo type khi chỉ có một ứng viên, việc đổi bean name có thể không ảnh hưởng; với lookup bằng chuỗi thì có.

### Bài 3: quét sai package

Nếu `@ComponentScan(basePackages = "demo.app.report")`, còn `InvoiceProcessor` nằm trong `demo.app.billing`, custom stereotype có làm InvoiceProcessor vẫn được phát hiện không?

**Đáp án:** Không. Meta-annotation quyết định class có phải ứng viên stereotype; package scan quyết định **class có được xem xét**. Phải quét thêm đúng gốc billing hoặc đăng ký bean bằng cơ chế khác.

### Bài 4: alias trong cùng annotation

Với:

```java
@AliasFor("name")
String value() default "";

@AliasFor("value")
String name() default "";
```

hai thuộc tính có thể có kiểu khác nhau hoặc default khác nhau không?

**Đáp án:** Không. Explicit aliases trong cùng annotation cần cùng kiểu và cùng default. Nếu muốn cho phép hai tùy chọn độc lập, đừng khai báo chúng là alias.

### Bài 5: alias nào ảnh hưởng bean name?

Một annotation có `@Component` và một thuộc tính `String category() default "core";` nhưng không có `@AliasFor`. `category` có tự trở thành tên bean không?

**Đáp án:** Không. Nó là metadata riêng do annotation định nghĩa; để ánh xạ tên bean, cần explicit alias tới `Component.value` hoặc một bean name generator/custom scanner thực hiện chính sách riêng.

### Bài 6: thiết kế custom stereotype

Bạn muốn class ở tầng application được đánh dấu `@ApplicationUseCase`, cho phép `@ApplicationUseCase(name = "orderCreation")` đặt tên bean. Hãy nêu những thành phần thiết yếu.

**Đáp án định hướng:** Khai báo một `@interface ApplicationUseCase` có `@Target(TYPE)`, `@Retention(RUNTIME)`, `@Documented` và meta-annotation `@Component`. Thêm `@AliasFor(annotation = Component.class, attribute = "value") String name() default "";`. Quét đúng package, rồi kiểm tra container có bean `orderCreation`. Chỉ thêm scope/transaction/qualifier nếu đó là yêu cầu thực sự của role.

## Ghi nhớ

- **Meta-annotation `@Component`** biến custom annotation thành stereotype mà component scanner mặc định có thể phát hiện.
- **`@AliasFor`** mô tả quan hệ giữa các attribute: alias nội bộ, override tới meta-attribute, hoặc implicit aliases giữa nhiều attribute cùng đích.
- Với custom stereotype có thuộc tính đặt tên riêng, dùng **`@AliasFor(annotation = Component.class, attribute = "value")`** để đặt bean name một cách tường minh và tương thích hướng phát triển Spring 6.1+.
- `@Target(TYPE)`, `@Retention(RUNTIME)` và phạm vi scan phải đúng; định nghĩa annotation hợp lệ chưa bảo đảm bean được đăng ký nếu scanner không tìm tới class.
- Java reflection thông thường không thay thế cơ chế **Spring merged annotations**. Khi cần xác minh alias, kiểm tra cả metadata được tổng hợp và bean thực tế trong ApplicationContext.
- Giữ custom stereotype nhỏ, đúng ý nghĩa nghiệp vụ và có integration test; không biến một annotation thành nơi gom mọi chính sách của Spring.

## Tài liệu chính thức

- [Spring Framework 6.2 — Classpath Scanning and Managed Components](https://docs.spring.io/spring-framework/reference/6.2/core/beans/classpath-scanning.html): stereotype, meta-annotation, annotation ghép, tên bean và component scanner.
- [Spring Framework 6.2 — @AliasFor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/core/annotation/AliasFor.html): các loại alias, điều kiện kiểu và default, explicit/implicit overrides.
- [Spring Framework 6.2 — @Component Javadoc](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/stereotype/Component.html): stereotype và convention naming từ Spring 6.1.
- [Spring Framework 6.2 — @ComponentScan Javadoc](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/context/annotation/ComponentScan.html): phạm vi quét và các filter.
- [Spring Framework — Annotation Programming Model](https://github.com/spring-projects/spring-framework/wiki/Spring-Annotation-Programming-Model): giải thích sâu về merged annotation và aliases.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="component-scanning-008">Phân biệt component scanning với xử lý annotation trên bean đã có</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt component scanning với xử lý annotation trên bean đã có trong Spring Core Container

## 1. Mục tiêu học tập

Spring Framework sử dụng annotation ở nhiều thời điểm khác nhau. Hai quá trình thường bị nhầm lẫn nhất là:

1. **Component scanning:** tìm các class đủ điều kiện trên classpath và **đăng ký bean definition** vào IoC Container.
2. **Xử lý annotation trên bean đã được đăng ký:** khi bean được tạo và quản lý, các thành phần hạ tầng đọc annotation để **tiêm dependency, chạy callback vòng đời hoặc áp dụng hành vi bổ sung**.

Hai việc liên hệ nhưng **không phải một**. Một class có `@Component` mà chưa được scan hoặc đăng ký bằng cách khác thì không tự nhiên trở thành bean. Ngược lại, một bean được khai báo bằng `@Bean` hoặc XML vẫn có thể được xử lý `@Autowired`, `@PostConstruct` và nhiều annotation khác nếu có hạ tầng tương ứng.

Sau bài này, bạn có thể:

- Phân biệt **bean definition discovery/registration** với **bean instance processing**.
- Giải thích tại sao `@ComponentScan` và `@Autowired` giải quyết hai vấn đề khác nhau.
- Nhận diện vai trò của `ConfigurationClassPostProcessor`, `AutowiredAnnotationBeanPostProcessor`, `CommonAnnotationBeanPostProcessor` và các `BeanPostProcessor` khác.
- Chạy ví dụ Java 17+ minh họa một bean được đăng ký qua `@Bean`, có field `@Autowired` và callback `@PostConstruct`/`@PreDestroy` dù không có `@ComponentScan`.
- Xác định chính xác nguyên nhân một annotation không có tác dụng: **chưa có bean**, **chưa đăng ký post-processor**, **không có hạ tầng tích hợp**, hoặc **lời gọi không đi qua proxy**.
- Đọc đúng log và kiểm tra bean definition thay vì suy đoán rằng cứ thấy annotation là Spring tự xử lý.

**Ngữ cảnh:** nội dung nằm trong nhóm **Quét thành phần** của Spring Core Container, nhưng có liên hệ với lifecycle và các extension point của IoC Container. Những ví dụ transaction, caching, web mapping chỉ dùng để xác định ranh giới cơ chế.

## 2. Hai câu hỏi độc lập: “bean từ đâu ra?” và “Spring làm gì với bean ấy?”

Một lớp Java không tự là Spring bean chỉ vì classpath chứa nó:

```java
import org.springframework.stereotype.Component;

@Component
public class GreetingService {
    public String greet() {
        return "hello";
    }
}
```

Đây là class biên dịch được khi có Spring Context trên classpath, nhưng để lấy được `GreetingService` qua `ApplicationContext`, trước hết phải có **bean definition** tương ứng hoặc một cơ chế đăng ký khác.

**Câu hỏi thứ nhất — Đăng ký bean:** Bean definition được đưa vào registry bằng cách nào? Ví dụ: `@ComponentScan`, `@Bean` trong configuration, `@Import`, XML, đăng ký chương trình hoặc cơ chế autoconfiguration.

**Câu hỏi thứ hai — Xử lý bean:** Khi container tạo instance từ bean definition, nó có tiêm field/method `@Autowired`, gọi `@PostConstruct`, chạy init callback, tạo proxy AOP hoặc đăng ký các metadata liên quan không? Việc này cần các post-processor và hạ tầng thích hợp.

Sơ đồ tư duy:

```text
                         CLASS / METADATA
                                |
                     Các cách đăng ký bean
                    /           |           \
          @ComponentScan      @Bean        XML / @Import / ...
                    \           |           /
                     \          v          /
                         BeanDefinition
                               |
                Spring BeanFactory / ApplicationContext
                               |
                   tạo và cấu hình bean instance
                               |
                các BeanPostProcessor phù hợp
                   /           |            \
            @Autowired   @PostConstruct    AOP proxy ...
                               |
                        bean sẵn sàng dùng
```

Không nên diễn giải hình này rằng Spring luôn thực thi các bộ xử lý đúng một thứ tự đơn giản cho tất cả annotation. Một số thành phần hoạt động **trên bean definition trước khi instance tồn tại**, một số can thiệp trước/sau khởi tạo và một số thuộc hạ tầng runtime khác. Phần sau phân tích từng nhóm.

## 3. Component scanning thực sự làm gì?

### 3.1. Tìm class candidate trong phạm vi cấu hình

Trong cấu hình Spring thuần:

```java
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.example.catalog")
public class CatalogConfig {
}
```

`@ComponentScan` hướng dẫn Spring quét package `com.example.catalog` và package con, áp dụng các bộ lọc nhận diện ứng viên (mặc định gồm `@Component` và các stereotype như `@Service`, `@Repository`, `@Controller`), rồi đăng ký bean definition.

**Nó không có nghĩa** Spring tự quét mọi package, và cũng không có nghĩa scanner lập tức tạo tất cả instance ngay khi đọc class file.

Ví dụ:

```text
com.example.catalog
  ├── CatalogConfig       @Configuration + @ComponentScan
  ├── CatalogService      @Service
  ├── CatalogRepository   @Repository
  └── Helper              class thuần, không có marker

com.example.external
  └── ExternalService     @Service
```

Với cấu hình trên và không có registration khác, scanner có thể phát hiện `CatalogService`, `CatalogRepository` ở package mục tiêu; `Helper` không có marker mặc định, còn `ExternalService` nằm ngoài phạm vi scan. `CatalogConfig` được đưa vào context theo cách bạn nạp nó hoặc scan phù hợp.

### 3.2. Điều kiện được scan không đồng nghĩa có thể tạo thành công

Sau khi class được chọn, Spring còn phải đáp ứng các điều kiện bean definition, tên bean, scope, constructor, dependency, conditional metadata và hạ tầng khởi tạo. Một candidate có thể bị loại theo filter/condition, hoặc bean có thể khởi tạo thất bại vì thiếu dependency.

```text
Tìm thấy class trên classpath
        !=
Class khớp component-scan filter
        !=
Đăng ký bean definition thành công
        !=
Khởi tạo bean instance thành công
        !=
Có mọi hành vi runtime đã mong đợi
```

Đây là các tầng cần kiểm tra riêng.

### 3.3. Những gì component scanner không tự làm

- Không tự tiêm `@Autowired` vào một object mà bạn tự tạo bằng `new` ngoài container.
- Không bắt buộc phải tồn tại thì `@Bean` mới hoạt động.
- Không khiến mỗi class có annotation bất kỳ thành bean; default scanner chỉ nhận diện các annotation theo bộ lọc của nó.
- Không tự mở cổng HTTP, bắt đầu transaction hoặc đăng ký caching.
- Không thể bảo đảm một bean không xuất hiện nếu cùng bean đó được đăng ký qua `@Bean` hay cấu hình khác.

**Ghi nhớ:** component scanning chủ yếu phục vụ **khám phá và đăng ký bean definition**.

## 4. Xử lý annotation trên bean đã có là gì?

Giả sử một bean được đăng ký trực tiếp bằng Java configuration:

```java
@Configuration
class AppConfig {
    @Bean
    ReportService reportService() {
        return new ReportService();
    }

    @Bean
    ReportRepository reportRepository() {
        return new ReportRepository();
    }
}
```

Đây là ví dụ cấu trúc; trong ứng dụng thực cần khai báo/import `@Configuration`, `@Bean` và hai class. Không có `@ComponentScan` trong cấu hình này, nhưng container vẫn có bean `reportService` và `reportRepository` do factory methods `@Bean` đăng ký.

Nếu `ReportService` có:

```java
import org.springframework.beans.factory.annotation.Autowired;

class ReportService {
    @Autowired
    private ReportRepository repository;

    public String run() {
        return repository.read();
    }
}
```

thì khi Spring tạo bean từ `@Bean` và có `AutowiredAnnotationBeanPostProcessor` hoạt động, field có thể được tiêm trước lúc sử dụng.

**Cơ chế thực tế không phải component scan tìm thấy field `@Autowired` để đăng ký ReportService.** ReportService đã được đăng ký bằng `@Bean`; post-processor sau đó xử lý metadata injection trên instance đang được tạo.

Tương tự, `@PostConstruct` yêu cầu hạ tầng lifecycle annotation xử lý bean được quản lý. Đối tượng tạo thủ công:

```java
ReportService ordinaryObject = new ReportService();
```

không tự được Spring tiêm `@Autowired` hay gọi `@PostConstruct` chỉ vì source code mang annotation. **Về mặt Java**, annotation là metadata; phải có một framework hay công cụ thực sự đọc và thực thi metadata đó.

## 5. Hai nhóm extension point cần phân biệt

Spring có các extension point xử lý **định nghĩa** và **instance** ở những pha khác nhau.

| Nhóm | Đối tượng tác động | Thời điểm chính | Ví dụ và vai trò |
| --- | --- | --- | --- |
| `BeanDefinitionRegistryPostProcessor` | Registry các bean definition | Trước khi instantiate các bean nghiệp vụ thông thường | Có thể đăng ký thêm definition; `ConfigurationClassPostProcessor` xử lý cấu hình class, `@Bean`, `@ComponentScan`. |
| `BeanFactoryPostProcessor` | Metadata của bean definition | Trước khi tạo các bean thông thường | Thay đổi/cấu hình metadata, ví dụ placeholder/property settings. |
| `BeanPostProcessor` | Instance của bean được quản lý | Trong quá trình tạo/khởi tạo bean | Xử lý annotation injection/lifecycle, áp dụng proxy hoặc custom initialization logic. |
| Hạ tầng bên ngoài bean creation | Bean metadata và/hoặc runtime request/call | Khi context setup hoặc ứng dụng chạy | MVC handler mappings, scheduled task registration, AOP advisor/interceptor. |

`BeanDefinitionRegistryPostProcessor` mở rộng `BeanFactoryPostProcessor`, nhưng có giai đoạn làm việc trên **registry**. `ConfigurationClassPostProcessor` có nhiệm vụ phân tích cấu hình annotation, từ đó việc quét class và việc đăng ký `@Bean` có thể diễn ra ở cùng quá trình xử lý metadata trước khi các bean ứng dụng được tạo.

`BeanPostProcessor` có hai callback cơ bản:

```java
Object postProcessBeforeInitialization(Object bean, String beanName);
Object postProcessAfterInitialization(Object bean, String beanName);
```

Đây là chữ ký ý tưởng của interface `org.springframework.beans.factory.config.BeanPostProcessor` (API thực còn có default implementation). Processor có thể trả chính instance hoặc instance đã được thay thế/wrap. Spring AOP thường tạo proxy thông qua hạ tầng post-processing, nhưng **không phải mọi BeanPostProcessor đều là proxy**.

**Điểm mấu chốt:** một bean đã tồn tại trong definition registry sẽ được xem xét trong vòng đời bean creation. Việc scan package không phải là điều kiện để các BeanPostProcessor này chạy.

## 6. Ai xử lý những annotation thường gặp?

| Annotation / metadata | Giai đoạn hoặc hạ tầng thường liên quan | Điều kiện cần nhớ |
| --- | --- | --- |
| `@Component`, `@Service`, `@Repository`, `@Controller` | Component scan / bean definition registration | Có scan đúng package/filter, hoặc bean được đăng ký qua đường khác. |
| `@Configuration` và `@Bean` | `ConfigurationClassPostProcessor` | Configuration class phải được đưa vào context qua một đường đăng ký hợp lệ. |
| `@Autowired`, `@Value`, Jakarta `@Inject` | `AutowiredAnnotationBeanPostProcessor` và cơ chế resolve dependency/value | Bean được Spring quản lý; processor phải được đăng ký. |
| Jakarta `@Resource`, `@PostConstruct`, `@PreDestroy` | `CommonAnnotationBeanPostProcessor` / lifecycle support | Processor và Jakarta Annotation API phải hiện diện đúng. |
| `@Transactional` | Transaction annotation source, AOP/interceptor, transaction manager | Không tự bật transaction chỉ vì bean có annotation. |
| `@Async` | Hạ tầng async, thường `AsyncAnnotationBeanPostProcessor` | Cần bật async support, executor và lời gọi phù hợp. |
| `@Cacheable` | Hạ tầng caching/AOP | Cần bật caching và cấu hình cache manager phù hợp. |
| `@Scheduled` | `ScheduledAnnotationBeanPostProcessor` / scheduling infrastructure | Cần kích hoạt scheduling và phù hợp execution context. |
| `@RequestMapping`, `@GetMapping` trên controller | Spring MVC handler mapping/adapter | Phải có web application context và MVC infrastructure thích hợp. |

Một annotation có thể được nhiều thành phần đọc hoặc dùng metadata ở nhiều pha; bảng thể hiện **đầu mối chính** để phân tích, không khẳng định tất cả annotation chỉ có một processor hoặc luôn hoạt động như nhau trong mọi ứng dụng.

### 6.1. @Autowired không phải “cơ chế quét class”

Khi Spring có một bean definition của class `A` và đang tạo `A`, `AutowiredAnnotationBeanPostProcessor` có thể phân tích constructor, field hoặc method mang `@Autowired` để hỗ trợ lựa chọn constructor và inject dependency. Tùy kiểu đăng ký và thời điểm, chi tiết injection khác nhau.

Ví dụ class được đăng ký bằng `@Bean` có constructor được **gọi trực tiếp bên trong factory method**: container không tự chọn lại constructor cho đối tượng `new A()` đó. Tuy nhiên, field/method `@Autowired` của instance trả về vẫn có thể được post-process. Ngược lại, bean được tạo trực tiếp theo bean class có thể được processor hỗ trợ chọn constructor mang `@Autowired`.

### 6.2. @PostConstruct chạy sau injection, trước khi bean sẵn sàng dùng

Với processor được cấu hình đúng, callback `@PostConstruct` thường chạy khi instance đã được inject dependency cơ bản, trong giai đoạn khởi tạo bean, trước khi bean hoàn tất quá trình startup.

`@PreDestroy` chạy trong vòng đời hủy bean do Spring quản lý, thường khi đóng context đối với singleton. Container **không tự quản lý toàn bộ destruction callback của prototype sau khi cấp instance**; vì vậy đừng suy luận prototype luôn nhận `@PreDestroy` khi xử lý xong.

## 7. Ví dụ chạy được: KHÔNG component scan nhưng annotation vẫn hoạt động

Đây là bài thực hành quan trọng nhất. Chúng ta cố ý **không khai báo `@ComponentScan`**, nhưng vẫn:

- Đăng ký `Formatter` và `ManualService` qua hai factory methods `@Bean`.
- Dùng `@Autowired` để tiêm field `Formatter` vào bean `ManualService`.
- Dùng `@PostConstruct` để in trạng thái injection trước khi logic chính chạy.
- Dùng `@PreDestroy` để in sự kiện hủy khi đóng `ApplicationContext`.
- Đặt `@Service` trên `ManualService` để chứng minh class có stereotype nhưng **được đăng ký nhờ `@Bean`**.
- Đặt `@Component("unscannedComponent")` trên một class khác nhưng không hề đăng ký nó, để chứng minh **annotation không tự biến class thành bean**.

### 7.1. Tạo dự án

Cấu trúc:

```text
annotation-processing-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        └── java/
            └── demo/
                └── AnnotationProcessingDemo.java
```

**`settings.gradle`**

```groovy
rootProject.name = 'annotation-processing-demo'
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
    implementation 'org.springframework:spring-context:6.2.9'
    implementation 'jakarta.annotation:jakarta.annotation-api:2.1.1'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.AnnotationProcessingDemo'
}
```

Spring Framework 6.2.x yêu cầu Java 17+. Đặt Jakarta Annotation API trực tiếp trong dependencies để project biên dịch ổn định các import `jakarta.annotation.PostConstruct` và `jakarta.annotation.PreDestroy`. Không dùng `javax.annotation` của Java EE cũ với Spring 6.

**`src/main/java/demo/AnnotationProcessingDemo.java`**

```java
package demo;

import java.util.Locale;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

public class AnnotationProcessingDemo {

    public static class Formatter {
        public String format(String input) {
            return input.toUpperCase(Locale.ROOT);
        }
    }

    @Service
    public static class ManualService {
        // Chủ đích dùng field injection để quan sát annotation processing.
        // Trong thiết kế production, thường nên ưu tiên constructor injection.
        @Autowired
        private Formatter formatter;

        @PostConstruct
        void afterInjection() {
            System.out.println("post-construct=" + (formatter != null));
        }

        public String work(String input) {
            return formatter.format(input);
        }

        @PreDestroy
        void beforeDestroy() {
            System.out.println("pre-destroy");
        }
    }

    @Component("unscannedComponent")
    public static class UnscannedComponent {
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {
        @Bean
        Formatter formatter() {
            return new Formatter();
        }

        @Bean
        ManualService manualService() {
            return new ManualService();
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            System.out.println("context-ready");
            System.out.println("manual-service="
                    + context.containsBean("manualService"));
            System.out.println("unscanned-component="
                    + context.containsBean("unscannedComponent"));

            ManualService service = context.getBean(ManualService.class);
            System.out.println("work=" + service.work("hello"));
        }
    }
}
```

**Chạy** trong thư mục gốc của dự án:

```bash
gradle run --quiet
```

Nếu dự án có Gradle Wrapper, dùng `./gradlew run --quiet` trên Linux/macOS, hoặc `gradlew.bat run --quiet` trên Windows. Lệnh `javac AnnotationProcessingDemo.java` riêng lẻ không đủ nếu không khai báo classpath Spring/Jakarta; Gradle sẽ biên dịch và chạy với dependencies.

**Các dòng được chương trình in ra (stdout):**

```text
post-construct=true
context-ready
manual-service=true
unscanned-component=false
work=HELLO
pre-destroy
```

Các công cụ build có thể ghi log khác tùy môi trường; các dòng trên là kết quả từ `System.out.println` của chương trình.

### 7.2. Vì sao kết quả như vậy?

1. `new AnnotationConfigApplicationContext(Config.class)` đăng ký class cấu hình `Config` và refresh context. `Config` có `@Configuration`, **không có `@ComponentScan`**.
2. `ConfigurationClassPostProcessor` xử lý phương thức `@Bean`, tạo bean definitions cho `formatter` và `manualService`.
3. Container tạo bean `ManualService` từ factory method `manualService()`. Trong factory method, đối tượng được dựng bởi `new ManualService()`; việc gọi `new` này xảy ra **trong cơ chế tạo bean do Spring kiểm soát**.
4. Bean instance trả về được các post-processor liên quan xử lý. `AutowiredAnnotationBeanPostProcessor` tìm field `formatter` và inject bean `Formatter` từ context.
5. `CommonAnnotationBeanPostProcessor` nhận callback `@PostConstruct`; lúc đó `formatter != null` nên in `post-construct=true` **trước** `context-ready`.
6. `@Service` trên `ManualService` không phải nguyên nhân bean xuất hiện trong ví dụ. `@Bean` mới là đường đăng ký thực tế, nhưng điều này không cản các processor xử lý instance.
7. `UnscannedComponent` có `@Component` nhưng không được đăng ký trực tiếp và không có component scan, vì vậy `containsBean("unscannedComponent")` trả `false`.
8. `try-with-resources` đóng `AnnotationConfigApplicationContext`. Callback `@PreDestroy` của singleton `ManualService` chạy và in `pre-destroy`.

Bài thực hành chứng minh hai sự kiện riêng:

- **Có bean definition không cần component scan:** `manualService=true`.
- **Annotation trên bean đã có vẫn được xử lý:** `post-construct=true` và `work=HELLO`, dù không có `@ComponentScan`.

Nó cũng chứng minh chiều ngược lại: **`@Component` tự đứng trên class chưa tạo bean** nếu class không được scan/đăng ký.

### 7.3. Điều gì xảy ra nếu tự new ngoài container?

Xét đoạn độc lập:

```java
AnnotationProcessingDemo.ManualService manual =
        new AnnotationProcessingDemo.ManualService();
manual.work("hello");
```

Đây không phải cách tạo Spring bean: field `formatter` vẫn là `null`, do đó `work` sẽ ném `NullPointerException` tại `formatter.format(...)`. Không có tự động `@PostConstruct` hoặc `@PreDestroy`. Đừng chạy biến thể này như một test mong đợi thành công.

**Điểm tinh tế:** `@Bean` có thể trả về đối tượng được tạo bằng `new` và **vẫn** được Spring xử lý vì factory method nằm trong vòng đời container. Việc “có từ khóa `new`” không đủ để kết luận Spring có quản lý bean hay không; phải xem đối tượng ấy được đưa vào container bằng cơ chế nào.

## 8. So sánh với component scan bằng ví dụ đối chiếu

Từ chương trình trước, nếu muốn tự động nhận diện các class có stereotype, có thể tạo một context cấu hình scan:

```java
@Configuration
@ComponentScan(basePackageClasses = AnnotationProcessingDemo.class)
class ScanConfig {
}
```

Đoạn này là **minh họa cấu hình riêng**, không thay thế trực tiếp trong demo mà không xem lại package. Nếu scan toàn bộ package `demo` chứa cả `Config` có `@Bean`, Spring cũng có thể nhận `Config`; các bean được khai báo thủ công có thể xuất hiện đồng thời với bean scan cùng type và gây trùng tên, nhiều candidate hoặc xung đột định nghĩa. Khi thử nghiệm, nên tách package fixture scan và package cấu hình thủ công hoặc exclude/import đúng mục đích.

Một bài test scan độc lập có thể dùng:

```java
@Configuration
@ComponentScan(basePackages = "com.example.scandemo")
class ScanOnlyConfig {
}
```

Giả sử package `com.example.scandemo` chứa `@Service OrderService` và `@Component AuditSink` cùng dependencies hợp lệ. Scanner sẽ phát hiện chúng và đăng ký definitions. Sau đó, **cùng hệ thống post-processor** vẫn có thể tiêm `@Autowired` và xử lý lifecycle trên các instance được tạo.

### Hai luồng dẫn đến một cơ chế bean creation

```text
Luồng A:
@Component trên class
     |
@ComponentScan đúng package
     |
BeanDefinition
     |
Container tạo bean
     |
BeanPostProcessor: @Autowired, @PostConstruct, ...

Luồng B:
@Configuration + @Bean
     |
ConfigurationClassPostProcessor
     |
BeanDefinition
     |
Container tạo bean
     |
BeanPostProcessor: @Autowired, @PostConstruct, ...
```

Khác nhau ở **đường đăng ký bean definition**; giai đoạn xử lý instance dùng cơ sở hạ tầng chung của container. Đôi khi cách tạo object cụ thể khác nhau (constructor do container chọn so với factory method tạo object), nên không khẳng định mọi annotation áp dụng giống hệt cho mọi đường đăng ký.

## 9. BeanFactory và ApplicationContext khác gì trong câu chuyện này?

**`BeanFactory`** là giao diện nền tảng để truy xuất/quản lý bean; một số triển khai cấp thấp không tự đăng ký mọi annotation post-processor. **`ApplicationContext`** là giao diện mức cao, thường tự phát hiện và đăng ký `BeanFactoryPostProcessor`, `BeanPostProcessor` thích hợp trong chu trình refresh.

`AnnotationConfigApplicationContext` đặc biệt thuận tiện cho Java annotation configuration: nó đăng ký các annotation config processor thông dụng khi khởi tạo, nên ví dụ ở mục 7 hoạt động mà không cần tự viết `@Bean` cho `AutowiredAnnotationBeanPostProcessor`.

Phân biệt các môi trường:

| Môi trường | Bean registration | Annotation processing |
| --- | --- | --- |
| `new AnnotationConfigApplicationContext(AppConfig.class)` | Đọc configuration class và `@Bean`; chỉ scan nếu được yêu cầu | Đã chuẩn bị sẵn nhiều annotation processor cốt lõi. |
| `new ClassPathXmlApplicationContext("beans.xml")` | Đọc XML bean definitions | Thường khai báo `<context:annotation-config/>` hoặc `<context:component-scan/>` khi muốn bật annotation processing tương ứng trong cấu hình XML. |
| `new DefaultListableBeanFactory()` độc lập | Có thể đăng ký definitions bằng API | Không mặc nhiên tương đương đầy đủ hạ tầng annotation của `AnnotationConfigApplicationContext`; cần thiết lập thích hợp. |
| Spring Boot application context | Auto-configuration, Java configuration, component scanning mặc định theo package setup | Boot thường tự lắp hạ tầng cần thiết, nhưng mỗi tính năng có điều kiện kích hoạt riêng. |

Bảng mô tả hành vi thông dụng; cấu hình tùy chỉnh, module/classpath hoặc extension point có thể thay đổi.

### 9.1. <context:annotation-config/> và <context:component-scan/> trong XML

Ví dụ XML annotation processing cho bean **đã khai báo**:

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/context
           https://www.springframework.org/schema/context/spring-context.xsd">

    <context:annotation-config/>

    <bean id="formatter" class="com.example.Formatter"/>
    <bean id="reportService" class="com.example.ReportService"/>
</beans>
```

Nếu `ReportService` có `@Autowired Formatter` và có processor phù hợp, bean XML vẫn được tiêm. `<context:annotation-config/>` **không thực hiện component scan**; chỉ khai báo post-processor cho annotation trên các bean được đăng ký trong cùng context.

Ngược lại, XML:

```xml
<context:component-scan base-package="com.example"/>
```

kích hoạt scan trong package và cũng **bao hàm kích hoạt annotation-config processors thông dụng** theo mặc định. Vì vậy trong nhiều cấu hình XML có `<context:component-scan/>`, không cần thêm `<context:annotation-config/>` riêng chỉ để tiêm `@Autowired`.

**Ngữ cảnh quan trọng:** các post-processor thường có hiệu lực trong **chính context** được khai báo; ở ứng dụng nhiều context (root/child web context), không tự suy ra mọi processor ở child sẽ xử lý bean của parent.

## 10. BeanDefinitionRegistryPostProcessor khác BeanPostProcessor ở đâu?

### 10.1. Cấu hình metadata trước khi tạo instance

`ConfigurationClassPostProcessor` là một ví dụ hạ tầng quan trọng, có khả năng xử lý `@Configuration`, `@ComponentScan`, `@Bean`, `@Import` và đăng ký các definitions/điều kiện liên quan. Nó hoạt động **trước phần lớn việc tạo bean ứng dụng**.

Điều này giải thích vì sao `@Bean` khai báo trong class cấu hình vẫn được Spring đọc dù không có component scanning. ApplicationContext đã biết class cấu hình vì bạn truyền nó vào constructor hoặc đăng ký class đó bằng cách khác.

Một `BeanFactoryPostProcessor` tự viết thường dùng để đọc hoặc chỉnh sửa `BeanDefinition` trước khi tạo instance, chứ không phải nơi sửa object đã được tạo:

```java
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MetadataConfig {
    @Bean
    static BeanFactoryPostProcessor inspectDefinitions() {
        return beanFactory -> {
            if (beanFactory.containsBeanDefinition("exampleBean")) {
                var definition =
                        beanFactory.getBeanDefinition("exampleBean");
                System.out.println("scope=" + definition.getScope());
            }
        };
    }
}
```

Đây là ví dụ minh họa, chỉ in kết quả nếu có bean definition `exampleBean`. `getScope()` có thể trả chuỗi rỗng khi bean dùng scope mặc định, không nhất thiết in `singleton`. Với `BeanFactoryPostProcessor` khai báo từ `@Bean`, phương thức `static` giúp tránh phải khởi tạo sớm configuration instance.

### 10.2. Xử lý instance khi container tạo bean

Một `BeanPostProcessor` tùy chỉnh có thể quan sát bean được đăng ký từ cả component scan lẫn `@Bean`:

```java
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TraceConfig {
    @Bean
    static BeanPostProcessor traceProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(
                    Object bean, String name) {
                if (name.equals("exampleBean")) {
                    System.out.println("initialized=" + name);
                }
                return bean;
            }
        };
    }
}
```

Đây là snippet chỉ được chạy khi có bean tên `exampleBean` trong cùng context và processor được đăng ký đủ sớm. `postProcessAfterInitialization` không quyết định scanner chọn class nào; nó nhận **instance được Spring tạo** và trả về object để container tiếp tục sử dụng.

**Lưu ý sớm-muộn:** Một `BeanPostProcessor` tự tạo cũng là bean đặc biệt, được dựng sớm. Nếu nó kéo theo nhiều bean ứng dụng lúc khởi tạo, những bean ấy có thể không được hưởng đầy đủ mọi post-processor, đặc biệt auto-proxying. Khi tự khai báo `@Bean` trả `BeanPostProcessor`/`BeanFactoryPostProcessor`, nên cân nhắc `static` và hạn chế dependencies theo hướng dẫn framework.

## 11. @Autowired, @PostConstruct và vòng đời bean

Sơ đồ đơn giản cho một singleton thông thường:

```text
BeanDefinition tồn tại
        |
Container xác định cách tạo đối tượng
        |
Tạo instance (constructor / factory method)
        |
Property/field/method injection thích hợp
        |
BeanPostProcessor trước khởi tạo
        |
@PostConstruct và callback init theo cấu hình/thứ tự phù hợp
        |
BeanPostProcessor sau khởi tạo (có thể tạo proxy)
        |
Bean sẵn sàng sử dụng
        |
Context đóng / scope kết thúc với destruction support
        |
@PreDestroy, DisposableBean, destroy callback tương ứng
```

Sơ đồ là mô hình học tập: `@PostConstruct` có thể được một `BeanPostProcessor` xử lý trong giai đoạn trước các callback init khác, và hạ tầng AOP/creation có nhiều biến thể. Không dùng sơ đồ như một hợp đồng thứ tự cho mọi extension point trong tất cả phiên bản.

### 11.1. Tiêm dependency trong @Bean-created object

Đối tượng do `@Bean` factory method trả về vẫn được container đưa qua các bước property population và initialization thông thường. Điều này giải thích ví dụ có:

```java
@Bean
SomeService service() {
    return new SomeService();
}
```

trong khi `SomeService` có field `@Autowired`. Nếu field injection processor có mặt, field được xử lý sau khi object đã được factory method tạo.

### 11.2. Constructor injection vẫn là lựa chọn ưu tiên

Ví dụ trước dùng field injection để cho thấy *annotation processing trên instance*. Trong thiết kế production, constructor injection thường giúp dependency bắt buộc được khai báo rõ và object dễ test:

```java
@Service
public class CheckoutService {
    private final PaymentGateway gateway;

    public CheckoutService(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    public String checkout() {
        return gateway.pay();
    }
}
```

Đây là ví dụ cấu trúc với `PaymentGateway` là interface nghiệp vụ giả định. Với class được scan và một constructor, Spring có thể resolve dependency khi tạo bean. Nếu class được tạo trong `@Bean` bằng `new CheckoutService(gateway)`, factory method chịu trách nhiệm cấp argument; Spring không thay constructor call đã thực hiện.

### 11.3. Lưu ý bean prototype

Spring thường gọi callback khởi tạo như `@PostConstruct` cho prototype khi tạo nó, nhưng sau khi cấp prototype cho caller, container không quản lý trọn destruction của instance giống singleton. Nếu prototype nắm tài nguyên, caller phải có kế hoạch cleanup phù hợp.

## 12. Không phải annotation nào cũng do BeanPostProcessor xử lý

Đây là phân biệt sâu hơn giúp tránh học thuộc sai:

### 12.1. @Transactional cần AOP và transaction management

Một class có `@Service` hay được tạo bởi `@Bean` đều có thể tham gia transaction nếu hạ tầng transaction được bật đúng, annotation được nhận diện và lời gọi đi qua interceptor/proxy thích hợp:

```java
@Service
public class MoneyTransferService {
    @org.springframework.transaction.annotation.Transactional
    public void transfer(long from, long to, long amount) {
        // Nghiệp vụ và persistence thật được triển khai tại đây.
    }
}
```

Đoạn này minh họa class; không phải chương trình đầy đủ. `@Transactional` không hoạt động chỉ vì scanner nhận `@Service` hoặc vì `@Bean` đăng ký class. Cần transaction manager, transaction attribute source/advisor/interceptor và điều kiện proxy/AspectJ phù hợp. Trong proxy mode thường gặp, **self-invocation** (method trong cùng bean gọi trực tiếp method khác của chính nó) không đi qua transaction proxy.

### 12.2. @RequestMapping thuộc hạ tầng web

Một controller có thể là Spring bean hợp lệ nhưng chưa có HTTP endpoint:

```java
@Controller
class HelloController {
    @GetMapping("/hello")
    @ResponseBody
    String hello() {
        return "hello";
    }
}
```

Đây là snippet web MVC cần import annotation, web application context và servlet/handler mapping tương ứng. Component scan chỉ đưa controller vào registry; Spring MVC `RequestMappingHandlerMapping` cần xem bean controller, tìm mapping và gắn với dispatcher. Một `AnnotationConfigApplicationContext` thuần không tự biến `@GetMapping` thành HTTP endpoint.

### 12.3. @Scheduled cần scheduling infrastructure

`@Scheduled` trên bean đã có cũng không chạy theo thời gian nếu không có scheduling support phù hợp. Trong cấu hình thông thường dùng `@EnableScheduling` (hoặc hạ tầng tương đương) để đăng ký processor cần thiết. Đăng ký bean thông qua scan hay `@Bean` là một chuyện; scheduling processor mới nhận diện lịch và thực thi.

### 12.4. @Repository và exception translation

`@Repository` là specialization của `@Component`, nhưng hành vi dịch persistence exception không được bảo đảm chỉ bằng marker. Khi `PersistenceExceptionTranslationPostProcessor` và `PersistenceExceptionTranslator` phù hợp có mặt, Spring có thể tạo interceptor/proxy thích hợp và chuyển exception sang họ `DataAccessException`. Một số công nghệ như `JdbcTemplate` cũng có cơ chế dịch ngoại lệ riêng. Cần phân biệt marker, post-processor và công nghệ data access.

**Nguyên tắc chung:** muốn biết một annotation có tác dụng hay không, luôn xác định **ai đọc annotation ấy**, **đọc lúc nào** và **cần điều kiện hạ tầng nào**.

## 13. Bảng so sánh có thể dùng khi debug

| Câu hỏi | Component scanning | Xử lý annotation trên bean |
| --- | --- | --- |
| Trả lời vấn đề gì? | Class nào có bean definition được tự động đăng ký? | Bean instance/metadata đã đăng ký được cấu hình và vận hành ra sao? |
| Đầu vào cốt lõi | Classpath, base package, metadata, include/exclude filter | Bean definition/instance đang được container quản lý, annotation trên class/field/method |
| Cơ chế tiêu biểu | `@ComponentScan`, `ClassPathBeanDefinitionScanner` | `AutowiredAnnotationBeanPostProcessor`, `CommonAnnotationBeanPostProcessor` và processor tích hợp khác |
| Pha điển hình | Trước tạo bean, khi đăng ký definitions | Khi tạo bean hoặc khi hạ tầng xử lý runtime |
| Kết quả | BeanDefinition và tên/scope metadata | Dependency được inject, callback được gọi, proxy/hành vi được lắp |
| Có bắt buộc lẫn nhau? | Không; scan tạo definitions nhưng không thay thế BPP | Không; BPP xử lý bean từ `@Bean`, XML và các đường đăng ký khác |
| Áp dụng với object tạo bởi `new` ngoài container? | Không | Không tự động |
| Tất cả annotation được xử lý ở cùng một chỗ? | Không | Không; có BFPP, BPP và web/AOP/runtime infrastructure |

### Ma trận tình huống

| Tình huống | Có bean? | `@Autowired` / `@PostConstruct` có thể chạy? |
| --- | --- | --- |
| `@Component` ở package không được scan, không đăng ký cách khác | Không | Không |
| `@Component` được scan và đăng ký đúng, annotation processor hoạt động | Có | Có |
| Class không mang `@Component`, được đăng ký qua `@Bean`, processor hoạt động | Có | Có |
| Class mang `@Component`, được đăng ký qua `@Bean`, không bật scan | Có | Có |
| Class được đăng ký XML, khai báo processor tương ứng | Có | Có |
| Object được code nghiệp vụ `new` mà không giao cho container | Không phải Spring bean | Không tự động |
| Bean có trong context nhưng thiếu annotation processor tương ứng | Có | Một phần annotation có thể không được xử lý |
| Controller bean có `@GetMapping` nhưng không có Spring MVC infrastructure | Có | Không có HTTP endpoint chỉ nhờ controller bean |

Từ “có thể” ở cột cuối nhằm nhấn mạnh **phải có post-processor đúng** và dependency/callback hợp lệ; không phải bảo đảm mọi bean luôn được xử lý thành công.

## 14. Các hiểu lầm phổ biến

### 14.1. “@Autowired chỉ có tác dụng với class được scan”

Sai. `@Autowired` được xử lý trên bean do Spring quản lý nếu processor được đăng ký; bean có thể được tạo từ `@Bean` hoặc XML. Ví dụ chạy được đã chứng minh trường hợp này.

### 14.2. “Có @Component nhưng getBean không tìm thấy nghĩa là @Component hỏng”

Chưa chắc. Kiểm tra package scan, include/exclude, profile/condition, cấu hình có được load, bean name và lỗi đăng ký. Nếu class không nằm trong vùng scan và không được đăng ký ở nơi khác, việc nó vắng mặt là đúng.

### 14.3. “Bật @ComponentScan là tất cả annotation đều hoạt động”

Sai. Component scan giúp đăng ký bean và trong nhiều ngữ cảnh cũng kích hoạt processor cốt lõi, nhưng annotation như `@Transactional`, `@Cacheable`, `@Async`, `@Scheduled` hay `@RequestMapping` cần hạ tầng chuyên biệt.

### 14.4. “Thêm @PostConstruct cho một object tự new sẽ gọi callback”

Sai. Nếu code tự tạo object ngoài container, annotation không tự chạy. Hãy để container quản lý instance hoặc tự gọi logic init do ứng dụng kiểm soát; không trông chờ vào callback Spring.

### 14.5. “@Bean sẽ bỏ qua mọi annotation trên class được trả về”

Sai. Object `@Bean` trả về được xử lý như Spring-managed bean, bao gồm post-processors thông thường. Nhưng constructor invocation đã diễn ra trong factory method nên không thể suy diễn rằng Spring chọn constructor `@Autowired` thay cho lời gọi `new` của method ấy.

### 14.6. “BeanFactoryPostProcessor và BeanPostProcessor giống nhau”

Sai. `BeanFactoryPostProcessor` xử lý **metadata/definitions** trước khi tạo bean thông thường; `BeanPostProcessor` xử lý **instance** trong chu trình khởi tạo. Làm bean creation quá sớm trong BFPP có thể bỏ qua một số BPP.

### 14.7. “@Service được scan rồi thì @Transactional chạy chắc chắn”

Sai. Transaction cần transaction manager và AOP/interceptor thích hợp. Có bean không đồng nghĩa có transaction boundary. Self-invocation cũng có thể bỏ qua proxy trong chế độ thông thường.

### 14.8. “Có BeanPostProcessor là mọi object trong JVM đều được xử lý”

Sai. Processor được đăng ký trong phạm vi **container cụ thể** và được áp dụng cho bean do container quản lý. Object tự tạo hoặc bean trong context khác không tự được xử lý bởi processor ấy.

### 14.9. “@ComponentScan loại class thì @Bean không thể tạo instance class đó”

Sai. Exclude filter chỉ chặn một đường khám phá. `@Bean` hoặc cấu hình khác vẫn có thể đăng ký class tương ứng.

### 14.10. “@PostConstruct và @PreDestroy luôn xuất hiện theo cùng một lịch trong mọi scope”

Không đúng. Callback khởi tạo của prototype có thể được chạy khi bean được tạo, nhưng container không tự quản lý toàn bộ destruction như singleton. Các context có shutdown không đúng cách cũng có thể khiến callback hủy không được gọi như dự kiến.

## 15. Phương pháp chẩn đoán theo từng tầng

Khi một annotation có vẻ “không hoạt động”, đừng sửa bằng cách thêm `@ComponentScan` vào mọi configuration. Hãy lần lượt tách ba câu hỏi.

### 15.1. Tầng thứ nhất: bean definition có tồn tại?

Trong một `AnnotationConfigApplicationContext` hoặc context có API tương ứng:

```java
System.out.println(context.containsBeanDefinition("manualService"));
System.out.println(context.containsBean("manualService"));
```

Đoạn này dựa trên biến `context` ở ví dụ mục 7. `containsBeanDefinition` tra **definition trực tiếp trong factory hiện tại**, còn `containsBean` có thể phản ánh bean có sẵn và trong một số bối cảnh tìm qua parent factory. Khi điều tra context phân cấp, cần hiểu sự khác nhau này.

Nếu bean không tồn tại, kiểm tra đường đăng ký: `@ComponentScan`, `@Bean`, `@Import`, XML, profile hoặc filter. **Đừng debug field injection khi class còn chưa là bean.**

### 15.2. Tầng thứ hai: instance đã được xử lý chưa?

Nếu bean tồn tại nhưng field `@Autowired` bị `null`, kiểm tra:

- Có phải object thực sự nhận từ container, hay caller tự `new` một instance khác?
- Context có đăng ký `AutowiredAnnotationBeanPostProcessor` không?
- Field/method injection có phù hợp, dependency bean có tồn tại, có đủ candidate duy nhất không?
- Bean có bị khởi tạo sớm trong lúc post-processor chưa sẵn sàng không?
- Trong trường hợp `@Bean`, phần nào được tạo bởi factory method và phần nào do post-processor xử lý?

Với callback `@PostConstruct`, kiểm tra có Jakarta Annotation API và `CommonAnnotationBeanPostProcessor` phù hợp, callback signature/lifecycle có hợp lệ không.

### 15.3. Tầng thứ ba: hạ tầng riêng có được bật?

Nếu `@Transactional` không hoạt động, kiểm tra transaction manager, transaction advisor, proxy và call path. Nếu `@GetMapping` không có route, kiểm tra web application context và handler mapping. Nếu `@Scheduled` không chạy, kiểm tra scheduling infrastructure. Đây không phải lỗi component scan nếu bean đã được đăng ký và nhìn thấy từ context.

### 15.4. Đọc bean definition để biết đường đăng ký

```java
var factory = context.getBeanFactory();
var definition = factory.getBeanDefinition("manualService");

System.out.println(definition.getFactoryMethodName());
System.out.println(definition.getResourceDescription());
```

Đặt snippet này trong scope của `context` ở mục 7. Với bean định nghĩa bằng `@Bean`, `getFactoryMethodName()` thường trả `manualService`. `getResourceDescription()` có thể mô tả nguồn class/configuration; giá trị chính xác thay đổi theo cách đăng ký, không nên khẳng định stdout cố định cho mọi environment.

Với bean được component scan, bean definition thường liên quan đến class source/resource của ứng viên, chứ không phải một `@Bean` factory method. Khi xem thông tin này, kết hợp với danh sách post-processor để biết tại sao một annotation được hoặc không được xử lý.

## 16. Kiểm thử để khẳng định sự khác biệt

### 16.1. Kiểm thử registration

Dựng context chỉ với `Config` ở mục 7:

- `containsBean("manualService")` phải là `true`.
- `containsBean("unscannedComponent")` phải là `false`.

Đây là phép thử **đường đăng ký**. Nó không đủ để khẳng định injection hoặc callback lifecycle đã hoạt động.

### 16.2. Kiểm thử injection và lifecycle

Dùng bean từ context:

```java
var service = context.getBean(AnnotationProcessingDemo.ManualService.class);
String result = service.work("hello");
if (!"HELLO".equals(result)) {
    throw new AssertionError("Annotation injection failed: " + result);
}
```

Đây là snippet dựa trên `context` từ chương trình đầy đủ; nó chứng minh dependency `Formatter` đã sẵn sàng khi `work` chạy. Có thể dùng JUnit assertions trong dự án có test dependency tương ứng. Để kiểm tra `@PostConstruct` và `@PreDestroy` tự động, có thể ghi callback vào một recorder/test fixture và assert trước/sau khi close context, tránh test phụ thuộc chặt vào stdout.

### 16.3. Kiểm thử bằng hai context tách biệt

Có thể tạo hai fixture:

- **Fixture A:** chỉ `@Bean`, không có `@ComponentScan`; assert bean được đăng ký và xử lý annotation.
- **Fixture B:** cấu hình `@ComponentScan` đúng một package chứa các stereotype; assert scanner đăng ký class tương ứng, rồi post-processor xử lý chúng.

Nếu muốn test tác động khi thiếu processor, sử dụng một `BeanFactory` cấp thấp với registration rõ ràng. Tuy nhiên, đừng giả định một `AnnotationConfigApplicationContext` đã tắt component scan cũng đồng thời tắt `AutowiredAnnotationBeanPostProcessor`: hai điều đó là độc lập.

### 16.4. Kiểm thử proxy behavior

Khi test `@Transactional` hoặc `@Async`, việc `containsBean` trả `true` và injection hoạt động không chứng minh interceptor/async đã kích hoạt. Cần test observable behavior của transaction/async và đường gọi thực sự đi qua Spring-managed proxy hoặc mode phù hợp.

## 17. Bài tập tình huống

**Tình huống 1.** Class `EmailSender` có `@Component` nhưng nằm ngoài tất cả base package được scan. Một `@Bean` khác trong configuration trả về `new EmailSender()`. Class có trở thành bean không?

**Đáp:** Có, nếu configuration chứa `@Bean` được xử lý và tạo bean thành công. Nó xuất hiện từ factory method; `@Component` không phải nguyên nhân trực tiếp.

**Tình huống 2.** `EmailSender` ở trên có field `@Autowired Client client`. Có component scan hay không quyết định injection?

**Đáp:** Không trực tiếp. Nếu `EmailSender` là Spring-managed bean và `AutowiredAnnotationBeanPostProcessor` hoạt động, field injection vẫn có thể xảy ra. Dependency `Client` phải có bean phù hợp.

**Tình huống 3.** Một class `@Service` được quét thành bean nhưng method `@Scheduled` không chạy. Cần kiểm tra ở đâu trước?

**Đáp:** Tầng scheduling infrastructure: `@EnableScheduling`/processor tương ứng, cấu hình scheduler và method hợp lệ; component scan đã hoàn thành nhiệm vụ nếu bean thực sự tồn tại.

**Tình huống 4.** Developer thêm `@PostConstruct` vào một object tạo thủ công bằng `new` trong một method của service. Callback sẽ tự chạy chứ?

**Đáp:** Không. Đây là instance ngoài vòng đời bean của container. Chỉ annotation trên class không làm Spring tự xử lý object đó.

**Tình huống 5.** `@Controller` được Spring tạo thành bean, nhưng `GET /hello` trả 404. Có chắc lỗi do component scan không?

**Đáp:** Không. Cần kiểm tra web stack, handler mapping, mapping annotation, HTTP method/path và context chứa controller. Có bean chỉ chứng minh registration/creation, không chứng minh routing.

**Tình huống 6.** `@Autowired` không hoạt động trên object được factory method `@Bean` trả về. Những yếu tố cần kiểm tra?

**Đáp:** Processor được đăng ký chưa, object nhận có đúng instance container quản lý không, injection metadata hợp lệ không, dependency có giải quyết được không, hoặc bean có bị khởi tạo sớm trước khi BPP hoạt động đầy đủ không.

**Tình huống 7.** `@Bean SomeService service() { return new SomeService(); }` tạo bean, trong khi `SomeService` có constructor `@Autowired SomeService(A a)` mà không có no-arg constructor. Spring có tự thay câu lệnh `new SomeService()` không?

**Đáp:** Không. Câu lệnh Java ở factory method phải biên dịch được; Spring không thay constructor call trong method. Có thể khai báo `@Bean SomeService service(A a) { return new SomeService(a); }` để container resolve `A` và chuyển vào factory method.

**Tình huống 8.** Một `BeanFactoryPostProcessor` gọi `getBean()` để lấy service quá sớm, sau đó service không được AOP auto-proxy. Vì sao?

**Đáp:** Bởi bean được instantiate trước giai đoạn đăng ký đầy đủ các `BeanPostProcessor`/auto-proxy creators, nên có thể không đủ điều kiện được xử lý. BFPP chủ yếu nên thao tác metadata thay vì yêu cầu bean instance nghiệp vụ quá sớm.

## 18. Checklist kiến thức và thực hành

Khi nhìn thấy annotation trong Spring, hãy trả lời theo thứ tự:

1. **Đang nói đến class hay bean instance?** Class có trên classpath không đồng nghĩa bean definition đã đăng ký.
2. **Bean đăng ký bằng đường nào?** `@ComponentScan`, `@Bean`, XML, `@Import` hay cơ chế khác?
3. **Processor nào chịu trách nhiệm?** Configuration parser/registry processor, autowiring/lifecycle BPP, AOP advisor, MVC handler mapping hay scheduler?
4. **Processor hoạt động lúc nào?** Trước bean creation, lúc injection/initialization, hay khi runtime request/method call diễn ra?
5. **Thiếu điều kiện gì?** Package scan, post-processor, dependency bean, transaction manager, web infrastructure, scope, proxy path hoặc callback lifecycle?
6. **Có bằng chứng quan sát được không?** Bean definition tồn tại, field đã inject, callback đã chạy, proxy thực thi, request mapping hoạt động hoặc transaction rollback thực tế?
7. **Có đường đăng ký khác không?** Loại khỏi component scan không loại khỏi `@Bean`/`@Import`.

## 19. Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Annotation-based Container Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config.html): nền tảng annotation injection, `AutowiredAnnotationBeanPostProcessor`, `CommonAnnotationBeanPostProcessor` và `<context:annotation-config/>`.
- [Spring Framework 6.2 — Classpath Scanning and Managed Components](https://docs.spring.io/spring-framework/reference/6.2/core/beans/classpath-scanning.html): phát hiện candidate class, stereotype, đăng ký bean definition và `@ComponentScan`.
- [Spring Framework 6.2 — Container Extension Points](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-extension.html): phân biệt `BeanFactoryPostProcessor` và `BeanPostProcessor`, thời điểm chạy và lưu ý early initialization.
- [Spring Framework 6.2 — Using @PostConstruct and @PreDestroy](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/postconstruct-and-predestroy-annotations.html): điều kiện lifecycle callback và `CommonAnnotationBeanPostProcessor`.
- [Spring Framework — Bean Overview](https://docs.spring.io/spring-framework/reference/core/beans/definition.html): bean definition và metadata.
- [Spring Framework 6.2 — ConfigurationClassPostProcessor API](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/annotation/ConfigurationClassPostProcessor.html): đăng ký và xử lý metadata của configuration class.

**Ghi nhớ:** **Component scanning** trả lời câu hỏi “Spring tự tìm và đăng ký class nào thành bean?”. **Annotation processing** trả lời câu hỏi “Khi đã có bean, hạ tầng Spring đọc metadata nào và làm gì với bean ấy?”. Một bean từ `@Bean` vẫn được xử lý injection/lifecycle, còn một class có `@Component` nằm ngoài mọi đường đăng ký vẫn không phải Spring bean.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="component-scanning-009">Xung đột do quét trùng và giới hạn quét theo mô-đun</a>

<details>
<summary>Xem chi tiết</summary>

# Xung đột do quét trùng và giới hạn quét theo mô-đun trong Spring Core Container

## 1. Bài toán: mở rộng component scan trong dự án nhiều mô-đun

Giả sử hệ thống có ba Gradle module: `catalog` quản lý danh mục sản phẩm, `audit` ghi nhận sự kiện, `app` lắp ghép hai khả năng đó. Cả `catalog` lẫn `audit` đều định nghĩa một class tên ngắn `ReportService` và đánh dấu `@Service`:

```text
catalog -> demo.catalog.service.ReportService
audit   -> demo.audit.service.ReportService
app     -> cấu hình ApplicationContext
```

Mỗi class Java có tên đầy đủ khác nhau, nên **không có xung đột ở trình biên dịch Java**. Nhưng nếu component scanner phát hiện cả hai class trong cùng một Spring `BeanDefinitionRegistry`, chiến lược tên mặc định có thể đặt cho cả hai bean name `reportService`. Container khi đó không biết đăng ký hai bean definitions không tương thích dưới cùng một tên và sẽ báo lỗi.

Một tình huống khác: ứng dụng quét `demo.catalog` và đồng thời quét `demo.catalog.service`. Một component bên trong `demo.catalog.service` xuất hiện trong cả hai vùng. Có phải cứ **quét chồng lấn** là tạo hai instance hoặc lỗi? **Không nhất thiết.** Spring scanner kiểm tra bean definition đã đăng ký; nếu candidate thứ hai là cùng source hoặc tương thích, scanner thường **bỏ qua đăng ký lặp**, không tạo bean thứ hai.

Vì vậy cần phân biệt:

- **Quét chồng lấn:** cùng một class được tìm qua nhiều lượt hoặc nhiều package root giao nhau.
- **Xung đột tên:** các bean definitions không tương thích dùng cùng bean name.
- **Trùng kiểu:** nhiều bean có kiểu tương thích với một dependency đơn, gây ambiguity khi autowire.
- **Đăng ký qua nhiều cơ chế:** cùng/khác class xuất hiện qua `@ComponentScan`, `@Bean`, `@Import`, auto-configuration hoặc code đăng ký bean; kết quả phụ thuộc nguồn và chính sách override.

Mục tiêu bài học là nắm quy tắc, chọn ranh giới scan đúng theo Java package và Gradle module, phân tích lỗi thực tế, đồng thời xây ví dụ Java 17+ với Spring Framework để quan sát cả trường hợp **không lỗi**, **có xung đột**, và **cách sửa**.

## 2. Mô hình quét: classpath, package và bean registry

`@ComponentScan` từ `org.springframework.context.annotation` yêu cầu Spring khám phá component candidates trong một hoặc nhiều base package. Mặc định, scanner nhận `@Component` và các stereotype như `@Service`, `@Repository`, `@Controller`, `@Configuration` hoặc custom stereotype có meta-annotation `@Component`.

Khi `@ComponentScan(basePackages = "demo.catalog")` được xử lý:

```text
Runtime classpath có các .class của module phụ thuộc
                     |
                     v
Spring scan demo.catalog và package con của nó
                     |
                     v
Áp dụng stereotype + exclude/include filters/condition
                     |
                     v
BeanNameGenerator tạo tên cho mỗi bean definition
                     |
                     v
BeanDefinitionRegistry kiểm tra tên / tương thích
                     |
                     v
  Đăng ký mới, bỏ qua bản trùng tương thích,
  hoặc báo lỗi nếu đụng definition không tương thích
                     |
                     v
ApplicationContext khởi tạo bean theo scope/lifecycle
```

**Ba không gian tên khác nhau:**

1. **Java package**: ví dụ `demo.catalog.service`. Nó xác định namespace Java và phạm vi tìm class của component scanner.
2. **Build module**: ví dụ Gradle project `:catalog`. Nó quyết định class nào được compile và có trên runtime classpath của module khác.
3. **Spring bean name**: ví dụ `reportService`. Đây là key để đăng ký/lookup bean trong một BeanFactory/registry cụ thể.

Spring **không lấy tên Gradle module làm phạm vi scan tự động**. Hai Gradle modules có thể dùng cùng Java package prefix; một module Gradle có thể chứa nhiều package; một package có thể trải trên nhiều JAR. Component scanner làm việc với package + classpath, chứ không nhận lệnh “chỉ quét Gradle module A” qua `@ComponentScan`.

### 2.1. Một class trên classpath chưa chắc là một bean

Để một class được đăng ký nhờ scanning, cần đồng thời:

- Artifact chứa `.class` có mặt trong runtime classpath (hoặc module path phù hợp).
- Package của class nằm trong một base package được quét.
- Class là candidate hợp lệ (concrete, independent, đúng stereotype/filter).
- Không bị `@Profile`, `@Conditional` hoặc exclude filter loại bỏ.
- Bean name/metadata có thể đăng ký mà không gây xung đột không tương thích.

Việc Gradle có `implementation project(':audit')` chỉ khiến mã audit khả dụng cho `app`; nó **không tự mở component scan** sang `demo.audit`.

### 2.2. Registry của từng `ApplicationContext`

Bean name có tính duy nhất trong phạm vi **một registry**, không phải toàn JVM. Hai `ApplicationContext` độc lập đều có thể có bean tên `reportService` mà không xung đột. Xung đột xuất hiện khi hai definitions không tương thích cùng đi vào **một** registry dưới cùng tên.

Với quan hệ parent-child context, khả năng lookup bean từ parent/child và hiện tượng shadowing phụ thuộc tên/vị trí bean. Đừng kết luận rằng hai bean cùng tên ở **hai context khác nhau** luôn giống một xung đột đăng ký trong một registry.

## 3. Khi quét trùng không gây lỗi: candidate tương thích

Spring `ClassPathBeanDefinitionScanner` có bước `checkCandidate(beanName, definition)`. Ý nghĩa của bước này:

- Chưa có bean definition trùng tên: candidate có thể đăng ký.
- Có definition tương thích: scanner **bỏ qua candidate lặp**.
- Có definition không tương thích: scanner có thể ném lỗi, thường liên quan `ConflictingBeanDefinitionException`.

Với hai root giao nhau:

```java
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = {
        "demo.catalog",
        "demo.catalog.service"
})
class OverlappingConfig {
}
```

Nếu `demo.catalog.service.ReportService` là một bean class cụ thể có stereotype và không có đăng ký xung đột, root `demo.catalog` đã bao phủ class này; scan tiếp root `demo.catalog.service` sẽ gặp cùng component. Mặc định Spring nhận ra duplicate tương thích nên không tạo hai bean độc lập chỉ vì hai root giao nhau.

Đây là **đoạn minh họa cấu hình**, chạy đầy đủ trong ví dụ mục 7.

**Đừng suy luận quá mức:** sự tương thích không có nghĩa mọi loại duplicate đều an toàn. Nếu hai source đăng ký bean definitions khác nhau dưới cùng tên, chính sách check/override còn phụ thuộc kiểu definition, cấu hình registry và thứ tự đăng ký. Với hai class khác nhau cùng được scanner phát hiện, xung đột tên là vấn đề khác hẳn so với quét hai lần *cùng một class*.

### 3.1. Quét một class hai lần không đồng nghĩa hai singleton

Một component default singleton được đăng ký vào registry **một lần** dưới cùng bean name trong tình huống scan trùng tương thích. Một lần lookup có thể trả cùng singleton, không xuất hiện “singleton thứ hai” do lặp scan.

Nếu bạn thật sự cần hai bean instances của cùng class với cấu hình khác nhau, hãy khai báo hai bean definitions có chủ đích (ví dụ hai `@Bean` methods với tên và tham số khác nhau) và quản lý scope/lifecycle; đừng dùng scan chồng lấn để cố tạo hai instances.

### 3.2. Không dựa vào thứ tự scan để giải quyết conflict

Thứ tự filesystem/classpath scanning không nên là hợp đồng nghiệp vụ. Trường hợp các bean definitions thực sự mâu thuẫn phải được sửa bằng naming, scan boundaries hoặc explicit registration. Không thiết kế logic “class nào được quét trước thì class đó thắng” vì thay đổi JAR, build hoặc package layout có thể thay đổi hành vi.

## 4. Khi trùng tên của hai class khác nhau gây conflict

Mặc định, `AnnotationBeanNameGenerator` thường lấy **tên class ngắn** theo quy tắc JavaBeans, ví dụ `ReportService` → `reportService`.

Giả sử:

```java
// catalog/src/main/java/demo/catalog/service/ReportService.java
package demo.catalog.service;

import org.springframework.stereotype.Service;

@Service
public class ReportService {
}
```

và:

```java
// audit/src/main/java/demo/audit/service/ReportService.java
package demo.audit.service;

import org.springframework.stereotype.Service;

@Service
public class ReportService {
}
```

Hai file hợp lệ vì Java package khác nhau. Nhưng quét cả hai:

```java
@Configuration
@ComponentScan(basePackages = {"demo.catalog", "demo.audit"})
class ConflictConfig {
}
```

có thể khiến cả hai cùng được đăng ký dưới bean name `reportService`. Vì bean classes khác nhau, scanner gặp hai definitions không tương thích và báo `ConflictingBeanDefinitionException` (đôi khi được bọc trong exception cấp cao hơn).

Câu báo lỗi thường chứa tên `reportService`, tên đầy đủ hai class và thông tin “conflicts with existing ... bean definition”. Khi chẩn đoán, đọc **hai class names** và **bean name** trong exception thay vì chỉ tìm một từ “duplicate”.

### 4.1. Cách giải quyết theo mức độ cần thiết

**Cách A — Quét đúng module cần dùng.** Nếu `app` chỉ sử dụng catalog, giới hạn scanner vào `demo.catalog`. Đừng kéo audit component vào registry chỉ vì audit library cũng có mặt trong classpath.

**Cách B — Đặt tên riêng trên component.** Nếu cần cả hai services:

```java
@Service("catalogReportService")
public class ReportService {
}
```

ở catalog và:

```java
@Service("auditReportService")
public class ReportService {
}
```

ở audit. Hai đoạn là **biến thể minh họa**; chúng thuộc hai file/package khác nhau như trên. Tên explicit giúp phân biệt và ổn định khi đổi class name, nhưng cần sửa các tham chiếu string nếu đã phụ thuộc tên cũ.

**Cách C — Fully-qualified bean names.** Spring cung cấp `FullyQualifiedAnnotationBeanNameGenerator` để sinh fallback bean names theo class name có package:

```java
@Configuration
@ComponentScan(
    basePackages = {"demo.catalog", "demo.audit"},
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class
)
class QualifiedConfig {
}
```

Khi đó hai bean có thể tên `demo.catalog.service.ReportService` và `demo.audit.service.ReportService`. Generator này vẫn giữ explicit stereotype names; nếu hai class đều khai báo `@Service("reportService")`, **nó không tự xóa được xung đột explicit name**.

**Cách D — Đăng ký tường minh.** Nếu không muốn scan toàn bộ audit, có thể import một `@Configuration` module audit cung cấp bean qua `@Bean(name = "...")`, hoặc dùng `@Import` để đăng ký class/cấu hình cần thiết. Cách này tạo hợp đồng ranh giới module rõ ràng hơn, nhất là thư viện có nhiều component nội bộ.

### 4.2. Đổi generator không giải quyết ambiguity theo type

Hai bean khác tên nhưng cùng implement interface `ReportPort` có thể cùng được đăng ký thành công. Nếu một constructor inject **một** `ReportPort` mà không có chính sách lựa chọn, Spring vẫn có thể ném `NoUniqueBeanDefinitionException`.

| Vấn đề | Dấu hiệu | Cách sửa |
| --- | --- | --- |
| Trùng bean name | Hai definitions không tương thích cùng key; conflict khi register | Tên explicit khác nhau, FQ generator, giới hạn scan |
| Trùng candidates theo type | Hai bean names khác nhau nhưng caller cần một bean type chung | `@Qualifier`, `@Primary`, hoặc thiết kế injection tập hợp |
| Thiếu bean | Không được scan/import, thiếu runtime dependency hoặc bị condition loại | Sửa package/classpath/cấu hình |
| Cùng class quét lặp tương thích | Scanner gặp cùng definition qua vùng giao nhau | Thường bỏ qua đăng ký lặp; vẫn nên đơn giản hóa scan khi có thể |

## 5. Ranh giới quét khi dự án chia mô-đun

Một dự án nhiều mô-đun có thể tổ chức theo trách nhiệm:

```text
workspace/
├── app/           -> demo.app
├── catalog/       -> demo.catalog
├── audit/         -> demo.audit
└── contracts/     -> demo.contracts (interface/DTO)
```

Về build, `app` có thể phụ thuộc `catalog` và `audit`; `catalog`, `audit` có thể cùng phụ thuộc `contracts`. Nhưng **component scanning không suy ra ranh giới module từ dependency graph**.

Khi cấu hình:

```java
@ComponentScan(basePackages = "demo")
```

Spring sẽ xét tất cả component candidates trong `demo` và các subpackage **có trên runtime classpath**. Điều này có thể kéo vào container các adapter, service nội bộ, configuration class demo/test support hoặc optional integration không mong muốn, và làm phát sinh conflict.

Thay vì quét quá rộng, nên xác định rõ “application composition root”:

- Module nào thực sự cung cấp beans cho ứng dụng hiện tại?
- Các package **API công khai** hoặc **configuration entry point** của module là gì?
- Module nào cần tách biệt internal components, chỉ public bean qua `@Bean`/`@Import`?
- Có test hoặc profile nào dự định tạo context chỉ với một phần các module?
- Khi một dependency mới xuất hiện trên classpath, có nên tự động khiến toàn bộ component bên trong nó tham gia runtime không?

Một module library có thể cung cấp **configuration class công khai** để application `@Import` thay vì yêu cầu quét hết package nội bộ. Cách thiết kế này giúp việc tích hợp có mục tiêu hơn và hạn chế xung đột tên ngoài dự kiến.

### 5.1. `basePackageClasses` giúp giới hạn theo package root

`@ComponentScan(basePackageClasses = CatalogMarker.class)` lấy package chứa `CatalogMarker` làm root quét. Nếu marker nằm tại `demo.catalog`, Spring quét `demo.catalog` và các subpackage.

```java
@Configuration
@ComponentScan(basePackageClasses = {
        CatalogMarker.class,
        AuditMarker.class
})
class ModuleComposition {
}
```

Đây là đoạn minh họa cần các marker trong package root tương ứng. Marker class không cần mang stereotype. Cách này **type-safe hơn chuỗi package khi refactor**, nhưng nó vẫn là chọn **Java package root** chứ không phải giới hạn chính xác theo Gradle module/JAR. Nếu hai module chia sẻ package `demo.catalog`, scanner có thể đọc class từ cả hai nếu chúng nằm trên classpath.

Nếu muốn chỉ đưa ra **một vài bean** của module mà package chứa nhiều internal components, `basePackageClasses` không đủ; nên dùng explicit configuration, filters phù hợp hoặc tách package theo kiến trúc.

### 5.2. Quét chồng lên package con

`@ComponentScan(basePackages = {"demo.catalog", "demo.catalog.service"})` không cần thiết nếu đã scan `demo.catalog`. Package con đã được bao phủ. Khuyến nghị bỏ root thứ hai để cấu hình dễ đọc, dù scan lặp tương thích thường không gây lỗi.

Một root `demo.catalog.service` **không bao phủ** sibling `demo.catalog.persistence`. Nếu service phụ thuộc repository ở sibling, cấu hình hẹp có thể gây `UnsatisfiedDependencyException`. Đừng sửa bằng cách vội vàng mở sang `demo`: chọn đúng common root `demo.catalog` hoặc import/đăng ký chính xác những bean cần thiết.

### 5.3. Spring Boot và module boundaries

Trong Spring Boot, `@SpringBootApplication` bao gồm cơ chế component scan; mặc định root thường là package chứa class application. Đặt class tại root ứng dụng giúp scan các subpackage của ứng dụng, nhưng nếu root quá cao (ví dụ `demo`), các module library chia sẻ prefix có thể đều được quét.

Có thể cấu hình:

```java
@SpringBootApplication(
    scanBasePackageClasses = {
        CatalogMarker.class,
        AuditMarker.class
    }
)
public class MainApplication {
}
```

Đây là đoạn minh họa Boot; cần dependencies/imports thích hợp. Trong Boot, `scanBasePackages`/`scanBasePackageClasses` điều khiển **component scanning**, không thay thế cấu hình **JPA entity scanning** hoặc **Spring Data repository scanning** (`@EntityScan`, `@EnableJpaRepositories` trong bối cảnh phù hợp). Một Spring Data repository interface không được component scanner tạo proxy chỉ vì có package phù hợp; Spring Data có cơ chế riêng.

**Lưu ý khi tùy chỉnh:** nếu thêm `@ComponentScan` thủ công lên class Boot, cần kiểm tra những filter mặc định Boot cung cấp qua composed annotation và các cấu hình đang hoạt động. Đừng coi việc thay annotation quét là thay đổi hoàn toàn vô hại.

### 5.4. Điều kiện để module xuất hiện

| Điều kiện | Ảnh hưởng |
| --- | --- |
| Dependency vào module có trên runtime classpath | Class khả dụng để scanner tìm |
| Package của module nằm trong scan roots | Các component có cơ hội thành candidates |
| Có đúng stereotype/filters | Candidate được xét tiếp |
| `@Profile`/`@Conditional` phù hợp | Bean được đăng ký trong môi trường tương ứng |
| Bean name không xung đột | Registry có thể hoàn tất đăng ký |
| Dependency của bean đầy đủ | Có thể khởi tạo instance bình thường |

**Mấu chốt:** “mô-đun nằm trong build” và “bean của mô-đun có trong container” là hai việc khác nhau.

## 6. Các nguồn duplicate khác ngoài hai lượt scan

### 6.1. `@Component` và `@Bean` cùng đăng ký một trách nhiệm

```java
@Component
class MessageSender {
}

@Configuration
class SenderConfig {
    @Bean
    MessageSender messageSender() {
        return new MessageSender();
    }
}
```

Đây là **đoạn minh họa**. Nếu component được scan và method `@Bean` cũng được xử lý, bạn có thể gặp chồng chéo registration: trùng tên, một definition bị bỏ qua/ghi đè trong một số cấu hình, hoặc nhiều bean cùng kiểu nếu tên khác nhau. Kết quả cụ thể phụ thuộc tên và chính sách registry; **không giả định mỗi con đường luôn tạo một bean riêng**.

Đối với một component ứng dụng, thường chỉ cần một cách đăng ký. Với class bên thứ ba không chỉnh source, `@Bean` là lựa chọn rõ ràng.

### 6.2. `@Import` và `@ComponentScan` cùng phát hiện cấu hình

Một `@Configuration` vừa được component-scanned vừa được `@Import` có thể tham gia hai đường đăng ký. Spring configuration class processor có cơ chế xử lý metadata/configuration candidates, nhưng trường hợp có nhiều tên/nguồn không tương thích vẫn cần rà soát. Tránh phụ thuộc vào việc import/scan cùng một class nhiều lần để tăng số instance hoặc “đảm bảo” được xử lý.

### 6.3. Bean overriding và “ẩn” lỗi

`BeanDefinitionRegistry` có chính sách bean definition overriding tùy implementation/cấu hình. **Bật overriding không phải cách chữa mặc định** cho conflict:

- Với collision giữa hai scanned definitions không tương thích, scanner có thể ném `ConflictingBeanDefinitionException` thay vì cho definition thứ hai ghi đè.
- Với các nguồn đăng ký khác, ghi đè có thể khiến một bean mong đợi bị thay đổi âm thầm.
- Spring Boot hiện đại thường cấu hình chính sách chống override theo mặc định trừ khi cho phép tường minh; không dùng một setting override toàn cục chỉ để né lỗi chưa hiểu rõ.

Hướng đúng là làm rõ **ai sở hữu bean name**, package nào được scan, và nơi nào tạo bean. Nếu chủ ý thay thế một bean qua profile/condition, hãy chọn extension point hoặc policy chính thức thay vì dựa vào thứ tự đăng ký.

### 6.4. Component scanning không tự tạo Spring Data repository

Một interface `JpaRepository<Order, Long>` thường được Spring Data đăng ký dưới dạng repository proxy, theo cơ chế scan riêng. Nếu tìm xung đột bean name, cần phân biệt tên do component scanner, repository scanner và factory method tạo ra. Đổi `@ComponentScan` chưa chắc tác động tới các registry extension khác.

## 7. Ví dụ hoàn chỉnh Java 17+ và Spring 6: ba Gradle module

Ví dụ này chứng minh ba trường hợp:

1. Quét chồng lấn package catalog: một component vẫn chỉ có một bean definition tương thích.
2. Quét cả catalog và audit với tên mặc định: hai class `ReportService` khác package gây xung đột tên `reportService`.
3. Quét cả hai nhưng dùng `FullyQualifiedAnnotationBeanNameGenerator`: đăng ký thành công với hai tên riêng.

Dự án dùng Spring Framework `6.2.18` và Gradle multi-project, không cần Spring Boot, web server hoặc database.

### 7.1. Cấu trúc dự án

```text
component-scan-modules/
├── settings.gradle
├── build.gradle
├── catalog/
│   ├── build.gradle
│   └── src/main/java/demo/catalog/service/ReportService.java
├── audit/
│   ├── build.gradle
│   └── src/main/java/demo/audit/service/ReportService.java
└── app/
    ├── build.gradle
    └── src/main/java/demo/app/ScanConflictDemo.java
```

File `settings.gradle`:

```groovy
rootProject.name = 'component-scan-modules'
include 'catalog', 'audit', 'app'
```

File `build.gradle` tại root:

```groovy
subprojects {
    apply plugin: 'java'

    repositories {
        mavenCentral()
    }

    tasks.withType(JavaCompile).configureEach {
        options.release = 17
    }
}
```

Mỗi subproject đều có plugin Java, Maven Central và compile target Java 17. Cần chạy Gradle bằng một JDK tương thích; với ví dụ này sử dụng JDK 17 trở lên.

File `catalog/build.gradle`:

```groovy
dependencies {
    implementation 'org.springframework:spring-context:6.2.18'
}
```

File `audit/build.gradle`:

```groovy
dependencies {
    implementation 'org.springframework:spring-context:6.2.18'
}
```

File `app/build.gradle`:

```groovy
plugins {
    id 'application'
}

dependencies {
    implementation project(':catalog')
    implementation project(':audit')
    implementation 'org.springframework:spring-context:6.2.18'
}

application {
    mainClass = 'demo.app.ScanConflictDemo'
}
```

Từ `app`, hai module được đưa lên runtime classpath nhờ dependency Gradle. Nhưng mỗi context vẫn có quyền quyết định package nào được component-scanned.

### 7.2. Component của catalog

File `catalog/src/main/java/demo/catalog/service/ReportService.java`:

```java
package demo.catalog.service;

import org.springframework.stereotype.Service;

@Service
public class ReportService {
    public String report() {
        return "catalog";
    }
}
```

Bean name mặc định trong một lượt scan thông thường là `reportService`.

### 7.3. Component của audit

File `audit/src/main/java/demo/audit/service/ReportService.java`:

```java
package demo.audit.service;

import org.springframework.stereotype.Service;

@Service
public class ReportService {
    public String report() {
        return "audit";
    }
}
```

Mặc dù class name ngắn trùng với catalog, tên Java đầy đủ `demo.audit.service.ReportService` là một kiểu khác. Default bean name vẫn là `reportService` nếu class được scanner tìm thấy.

### 7.4. Main class với ba context

File `app/src/main/java/demo/app/ScanConflictDemo.java`:

```java
package demo.app;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

public class ScanConflictDemo {

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = {
            "demo.catalog",
            "demo.catalog.service"
    })
    static class OverlapConfig {
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = {
            "demo.catalog",
            "demo.audit"
    })
    static class CollisionConfig {
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackages = {"demo.catalog", "demo.audit"},
            nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class
    )
    static class QualifiedConfig {
    }

    private static boolean hasNameConflict(Throwable error) {
        for (Throwable current = error;
                current != null;
                current = current.getCause()) {
            if ("ConflictingBeanDefinitionException"
                    .equals(current.getClass().getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(OverlapConfig.class)) {
            String[] catalogNames = context.getBeanNamesForType(
                    demo.catalog.service.ReportService.class);
            System.out.println("overlap.beanCount = " + catalogNames.length);
            System.out.println("overlap.defaultName = " +
                    context.containsBean("reportService"));
        }

        boolean conflictFound = false;
        try (var context =
                     new AnnotationConfigApplicationContext(CollisionConfig.class)) {
            // Không vào nhánh này khi hai bean definitions xung đột.
        } catch (RuntimeException exception) {
            if (!hasNameConflict(exception)) {
                throw exception;
            }
            conflictFound = true;
        }
        System.out.println("default.conflict = " + conflictFound);

        try (var context =
                     new AnnotationConfigApplicationContext(QualifiedConfig.class)) {
            System.out.println("qualified.catalog = " +
                    context.containsBean("demo.catalog.service.ReportService"));
            System.out.println("qualified.audit = " +
                    context.containsBean("demo.audit.service.ReportService"));
            System.out.println("qualified.shortName = " +
                    context.containsBean("reportService"));

            String catalog = context.getBean(
                    demo.catalog.service.ReportService.class).report();
            String audit = context.getBean(
                    demo.audit.service.ReportService.class).report();
            System.out.println("qualified.reports = " + catalog + "," + audit);
        }
    }
}
```

Class cấu hình lồng nhau là `static`, độc lập với instance của `ScanConflictDemo`, được đưa trực tiếp vào `AnnotationConfigApplicationContext`. Ba context **không dùng chung registry**.

Method `hasNameConflict` kiểm tra exception chain theo tên class exception, chỉ nhằm trình bày output xác định mà không in toàn bộ stack trace; nếu lỗi khác xảy ra, chương trình ném lại exception để tránh che lỗi cấu hình không liên quan.

### 7.5. Lệnh chạy

Tại thư mục root `component-scan-modules`:

```bash
gradle :app:run --quiet
```

Với Gradle Wrapper: `./gradlew :app:run --quiet` (Linux/macOS) hoặc `.\gradlew.bat :app:run --quiet` (Windows PowerShell).

Các dòng **do chính chương trình in ra**:

```text
overlap.beanCount = 1
overlap.defaultName = true
default.conflict = true
qualified.catalog = true
qualified.audit = true
qualified.shortName = false
qualified.reports = catalog,audit
```

**Lưu ý về log:** Spring có thể ghi thêm cảnh báo/lỗi vào stderr khi context có collision; bảng trên chỉ mô tả các dòng `System.out.println`, không khẳng định toàn bộ output của process chỉ có các dòng này.

### 7.6. Giải thích từng kết quả

- `overlap.beanCount = 1`: root `demo.catalog` đã bao phủ `demo.catalog.service`. Quét lại package con tìm đúng class ban đầu, nên scanner không đăng ký thêm một definition không tương thích.
- `overlap.defaultName = true`: class catalog được đăng ký dưới tên mặc định `reportService`.
- `default.conflict = true`: trong `CollisionConfig`, catalog và audit cùng sinh tên `reportService` dù class khác nhau; scanner phát hiện collision.
- `qualified.catalog = true` và `qualified.audit = true`: generator fully-qualified đặt hai tên tách biệt.
- `qualified.shortName = false`: hai component không khai báo tên tường minh; strategy này không đăng ký alias `reportService` mặc định.
- `qualified.reports = catalog,audit`: có thể lấy hai bean qua **class đầy đủ** tương ứng và gọi method của từng module mà không cần cast.

**Giới hạn:** ví dụ chỉ minh họa component scanning và naming. Khi cả hai bean implement cùng một interface, việc inject interface đơn vẫn có thể mơ hồ dù tên đã phân biệt; đó là bài toán autowiring cần `@Qualifier`/`@Primary` riêng.

## 8. Tách ranh giới bằng configuration entry point

Nếu `audit` là library, ứng dụng có thể muốn **dùng đúng một adapter** nhưng không muốn scan mọi class nội bộ thuộc `demo.audit`.

Một lựa chọn là module audit công bố class cấu hình:

```java
package demo.audit.config;

import demo.audit.service.ReportService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AuditModuleConfig {

    @Bean("auditReportService")
    ReportService auditReportService() {
        return new ReportService();
    }
}
```

Đây là **biến thể thiết kế minh họa**, không nằm trong mã dự án chạy mục 7. Nếu giữ `@Service` trên `ReportService` và đồng thời scan `demo.audit`, có thể tạo thêm definition hoặc chồng đăng ký tùy tên/cấu hình; khi chuyển hoàn toàn sang explicit configuration thì nên **không scan implementation ấy** hoặc bỏ stereotype khỏi class để tránh đăng ký hai lần.

Ứng dụng có thể sử dụng:

```java
@Configuration
@Import(AuditModuleConfig.class)
@ComponentScan(basePackages = "demo.catalog")
class AppComposition {
}
```

Đoạn trên cần imports `@Configuration`, `@Import`, `@ComponentScan` và `AuditModuleConfig`. Phương thức `@Bean("auditReportService")` định nghĩa chính xác một bean audit theo hợp đồng module. Lợi ích:

- Application chọn rõ module/configuration nào tham gia context.
- Không cần scan tất cả internal package của library.
- Bean names/roles công bố qua configuration được kiểm soát.
- Tránh vô tình đưa các component thử nghiệm hoặc optional adapter vào ứng dụng chỉ vì chúng có trên classpath.

Tuy nhiên, `@Import` class configuration vẫn cho phép configuration đó đăng ký nhiều bean definitions và import khác. Cần xem nó như **API tích hợp module**, và review nội dung cấu hình như các dependency khác.

### 8.1. Cân nhắc auto-configuration

Trong Spring Boot, một thư viện có thể cung cấp auto-configuration đăng ký bean theo `@ConditionalOnClass`, `@ConditionalOnMissingBean` hoặc những điều kiện tương ứng. Đây là extension point riêng, không cần scanner quét tất cả package của thư viện. Khi xây Spring Boot starter, nên dùng auto-configuration theo quy ước Boot thay vì bắt người dùng thêm scan root rộng.

Không nhầm `@ComponentScan` với auto-configuration: một library JAR có mặt trên classpath chưa chắc bean của nó được đăng ký bằng component scan, và một bean do auto-configuration tạo cũng không cần class implementation mang `@Component`.

## 9. Trùng bean name và tên tường minh: lưu ý khi sửa

Dùng `FullyQualifiedAnnotationBeanNameGenerator` là giải pháp tốt cho **tên mặc định** của hai class khác package. Nhưng nếu chúng khai báo:

```java
@Service("sharedReport")
class CatalogReportService {
}

@Service("sharedReport")
class AuditReportService {
}
```

Hai đoạn minh họa trên là hai class khác nhau với **cùng tên explicit**. Fully-qualified generator vẫn tôn trọng `"sharedReport"`, nên conflict vẫn có thể xuất hiện. Hãy đặt tên tường minh riêng theo vai trò, hoặc điều chỉnh phạm vi scan.

Một hệ thống đang dùng `@Qualifier("reportService")`, `@Resource(name="reportService")`, `getBean("reportService")` hay `Map<String, ReportPort>` cũng sẽ chịu ảnh hưởng khi chuyển sang fully-qualified names. Bean name là một phần hợp đồng nếu code/config bên ngoài tham chiếu bằng string.

**Không chủ động đổi generator toàn hệ thống** chỉ để sửa một conflict lẻ nếu ứng dụng phụ thuộc nhiều vào bean names hiện có. Khi đó một cách sửa hẹp như đặt tên explicit cho hai component xung đột hoặc giới hạn scan có thể dễ kiểm soát hơn.

## 10. Lỗi thường gặp và phương án điều tra

| Biểu hiện | Khả năng xảy ra | Cách kiểm tra/sửa |
| --- | --- | --- |
| `ConflictingBeanDefinitionException` với `reportService` | Hai class khác nhau cùng short name ở hai package được scan | Đối chiếu FQCN của hai classes; dùng explicit names, FQ generator hoặc giảm scan |
| Quét cùng package hai lần mà không lỗi | Scanner bỏ qua definition tương thích từ cùng class | Đây là hành vi có thể mong đợi; không giả định hai instances |
| Thêm `implementation project(':audit')` rồi startup lỗi conflict | Class audit nay có trên runtime classpath và root scan đủ rộng | Thu hẹp root/explicit registration thay vì scan mọi module |
| `NoUniqueBeanDefinitionException` sau khi sửa tên trùng | Hai bean khác names nhưng cùng implement interface được inject đơn | Chọn qua `@Qualifier`/`@Primary` hoặc inject collection |
| `NoSuchBeanDefinitionException` sau khi thu hẹp scan | Bean cần thiết nằm ở package ngoài roots | Thêm root phù hợp hoặc import configuration module |
| Bean có mặt dù package không được scan | Được tạo qua `@Bean`, `@Import`, Spring Data, auto-config hoặc registration khác | Tìm nguồn bean definition; đừng chỉ đọc `@ComponentScan` |
| Gắn `@Component` và `@Bean` cùng class | Trùng đường đăng ký hoặc có nhiều beans cùng type | Chọn một chiến lược rõ ràng, kiểm tra registry |
| Bật bean overriding nhưng scanner vẫn báo conflict | Hai scanned definitions không tương thích có thể bị chặn trước override bình thường | Sửa naming/scan boundaries; không dựa vào override |
| Xung đột xuất hiện chỉ ở test | Test context quét rộng, import nhiều configs hoặc profile khác | So sánh test configuration, package roots, active profiles |
| Một module dùng chung package root với module khác | Scan theo package có thể kéo class từ cả hai JAR | Tách package theo module responsibility hoặc dùng explicit config |
| Component thư viện không được quét | Thiếu dependency runtime hoặc package không trong roots | Kiểm tra Gradle runtimeClasspath và annotation/filter |
| Bean được đăng ký nhưng tạo thất bại | Constructor dependency thiếu, circular dependency, scope hoặc factory lỗi | Đọc exception chain, phân biệt registration với instantiation |
| Conflict explicit name dù dùng FQ generator | Built-in generator giữ tên tường minh | Sửa các `@Service("...")`/custom stereotype aliases |
| Spring Data repository chưa thấy dù component scan rộng | Repository interface dùng cơ chế Spring Data riêng | Kiểm tra `@EnableJpaRepositories`/repository bootstrap |
| Scan `demo` gây rất nhiều beans | Root quá cao bao gồm module ngoài ý muốn | Chọn từng package root bằng marker hoặc import module config |

### 10.1. Cách debug có hệ thống

**Bước 1 — Xác định loại exception.** `ConflictingBeanDefinitionException` liên quan bean definition names trong lúc scan; `NoUniqueBeanDefinitionException` liên quan nhiều candidates lúc resolve dependency; `NoSuchBeanDefinitionException` thường nghĩa là không có bean phù hợp.

**Bước 2 — Đọc hai phía của conflict.** Nếu thông báo liệt kê hai class với tên đầy đủ, hãy xác định chúng đến từ module/JAR nào và vì sao cả hai được scan.

**Bước 3 — Xác định package roots đang hoạt động.** Kiểm tra `@ComponentScan` trên các cấu hình thực sự đăng ký, vị trí `@SpringBootApplication` và các import/auto-config liên quan. Không giả định cấu hình chỉ có đúng một scanner.

**Bước 4 — Kiểm tra runtime classpath.** Trong Gradle, từ module app có thể dùng lệnh:

```bash
gradle :app:dependencies --configuration runtimeClasspath
```

Nếu một module bất ngờ xuất hiện, kiểm tra khai báo dependency. Công cụ dependency graph cho biết **sự hiện diện của JAR/module**, không trả lời trực tiếp những bean nào đã được scan.

**Bước 5 — Xem bean definitions với context chạy được.** Ví dụ trong mã có `AnnotationConfigApplicationContext context`:

```java
String[] names = context.getBeanNamesForType(
        demo.catalog.service.ReportService.class);
java.util.Arrays.sort(names);
System.out.println(java.util.Arrays.toString(names));
```

Đây là đoạn minh họa, cần class trong runtime classpath. Nếu context đã thất bại khi refresh do conflict, bạn phải **sửa hoặc thu hẹp cấu hình trước**, rồi mới inspect registry cuối cùng. Không thể gọi `getBean...` trên một context không khởi tạo thành công.

**Bước 6 — Rà soát hợp đồng bean name.** Sau khi đổi generator, kiểm tra `@Qualifier`, `@Resource`, bean name string và map keys; test lại chức năng thực tế. Bean registry tạo được không đảm bảo mọi consumer đã được cập nhật.

## 11. Kiến trúc quét có kiểm soát cho dự án nhiều mô-đun

### 11.1. Ranh giới rõ ràng hơn package root toàn cục

Một quy ước hữu ích:

```text
com.example.app               -> composition root / main app
com.example.catalog           -> catalog public config + internal packages
com.example.audit             -> audit public config + internal packages
com.example.integration.*     -> integrations tùy chọn theo profile/condition
```

Thay vì `@ComponentScan("com.example")`, application chỉ scan package thuộc trách nhiệm trực tiếp, và dùng `@Import`/module configuration hoặc Boot auto-configuration cho library. Điều này tránh việc thêm một dependency Gradle lập tức đưa mọi component của thư viện vào registry.

### 11.2. Một module, nhiều package

Nếu một Gradle module `:catalog` có cả `com.example.catalog` và `com.example.sharedcatalog`, một marker `CatalogMarker` ở package đầu chỉ bao phủ một nhánh. Lúc đó hãy dùng nhiều root markers hoặc tổ chức lại package nếu điều đó phản ánh đúng kiến trúc. Đừng tưởng Spring suy ra toàn bộ module build từ class marker.

### 11.3. Một package nằm ở nhiều JAR

Nếu hai Gradle module cùng khai báo classes dưới `com.example.catalog`, scanner quét package này sẽ xem các class khả dụng từ cả hai JAR khi runtime classpath chứa chúng. Nếu kiến trúc cần cách ly, phải thay đổi packaging/classpath hoặc chọn explicit bean configuration; không thể chỉ dùng string package root để ràng buộc scanner chính xác theo JAR.

### 11.4. Filters và conditions

`@ComponentScan` có `excludeFilters`, `includeFilters`, `useDefaultFilters`, nhưng chúng vận hành **bên trong phạm vi package đã chọn**. Ví dụ:

```java
@Configuration
@ComponentScan(
    basePackages = "com.example",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyReportService.class
    )
)
class AppConfig {
}
```

Đây là đoạn minh họa, cần imports và class `LegacyReportService` tương ứng. Exclude filter có thể loại một class khỏi **lượt scan này**, nhưng không cấm một `@Bean` khác đăng ký class ấy. Khi số lượng exclusions tăng nhiều, đó có thể là dấu hiệu base package đã chọn quá rộng.

`@Profile` và `@Conditional` còn có thể loại các component theo môi trường/cấu hình. Để bảo đảm test/prod cùng đúng, kiểm thử phạm vi scan với các active profiles và điều kiện quan trọng.

### 11.5. Naming convention

Đặt tên bean theo **vai trò** khi thật sự cần tham chiếu theo tên. Nếu hai modules có `ReportService`, tên `catalogReportService` và `auditReportService` diễn đạt intent rõ hơn hai tên ngắn `reportService`. FQ generator giúp tránh collision quy mô lớn nhưng khiến tên phụ thuộc vào package; refactor package có thể làm thay đổi bean name. Khi một bean name là external contract, ưu tiên explicit name có chủ đích và tests.

## 12. Bài tập có đáp án

### Bài 1 — Scan chồng lấn

Config quét `demo.catalog` và `demo.catalog.service`; class `demo.catalog.service.ReportService` có `@Service`. Có chắc sẽ sinh hai singleton không?

**Đáp án:** Không. Đây là trường hợp cùng class xuất hiện trong hai root giao nhau, scanner có cơ chế kiểm tra candidate tương thích và thường chỉ giữ một bean definition.

### Bài 2 — Trùng class name ở hai modules

Hai module chứa `demo.catalog.service.ReportService` và `demo.audit.service.ReportService`, mỗi class có `@Service`. Config quét cả hai package, dùng default generator. Nguyên nhân conflict chính là gì?

**Đáp án:** Hai types khác nhau cùng sinh **bean name** `reportService`. Java class names vẫn khác, nhưng registry không đăng ký hai definitions không tương thích dưới cùng tên.

### Bài 3 — Fully-qualified generator có phải luôn giải quyết collision?

Giả sử cả hai class đều có `@Service("reportService")`. Thay `AnnotationBeanNameGenerator` bằng `FullyQualifiedAnnotationBeanNameGenerator` có đủ không?

**Đáp án:** Không. Built-in FQ generator giữ explicit name. Cần sửa tên explicit hoặc giới hạn registration.

### Bài 4 — Tên đã phân biệt nhưng injection vẫn lỗi

Hai bean `catalogReportService` và `auditReportService` đều implement `ReportPort`. Constructor `Reporter(ReportPort port)` bị `NoUniqueBeanDefinitionException`. Có phải scanner vẫn đang tạo trùng tên?

**Đáp án:** Không. Đây là ambiguity **theo kiểu**, không phải duplicate name. Sửa bằng `@Qualifier`/`@Primary` hoặc nhận collection khi nghiệp vụ thực sự cần cả hai.

### Bài 5 — Gradle dependency và root scan

`:app` có `implementation project(':audit')` nhưng chỉ quét `demo.catalog`. Bean `demo.audit.service.ReportService` có tự xuất hiện không?

**Đáp án:** Không qua lượt scan đó. Gradle thêm class vào classpath; để là bean cần scan/registration phù hợp và các điều kiện khác.

### Bài 6 — Marker được đặt ở package quá sâu

`@ComponentScan(basePackageClasses = CatalogServiceMarker.class)` với marker nằm trong `demo.catalog.service`. Repo bean ở `demo.catalog.persistence` có được quét không?

**Đáp án:** Không vì package persistence là sibling. Chọn marker tại `demo.catalog` hoặc nhập cấu hình repo phù hợp.

### Bài 7 — Quét package toàn cục

App trước đây chỉ có `catalog`; sau khi thêm dependency `audit` thì startup có conflict. Có nên bật bean override toàn hệ thống ngay không?

**Đáp án:** Không. Hãy xem component scan root có đang bao gồm cả audit package, xác định hai bean definitions xung đột, rồi thu hẹp scan, đổi naming hoặc đăng ký tường minh. Bật override có thể che vấn đề và không đảm bảo scanner chấp nhận definitions không tương thích.

### Bài 8 — Lựa chọn cấu hình mô-đun

Một module chứa 40 internal components nhưng app chỉ cần một adapter. Quét toàn bộ package module hay import một configuration công khai với một `@Bean`?

**Đáp án:** Import configuration công khai thường diễn đạt ranh giới rõ hơn khi module được thiết kế như một thư viện tích hợp. Cần bảo đảm class không đồng thời được scan ở con đường khác để tránh chồng registration.

### Bài 9 — Hai contexts

Context A scan catalog, context B scan audit; mỗi context có bean name `reportService`. Có phải chắc chắn sẽ gặp `ConflictingBeanDefinitionException`?

**Đáp án:** Không. Hai registry độc lập có thể dùng cùng tên; conflict đăng ký xảy ra khi definitions không tương thích tranh một tên trong cùng registry.

## 13. Checklist trước khi sửa một lỗi do quét trùng

1. Đọc exception chain để phân biệt **duplicate bean name**, **missing bean** và **ambiguous type**.
2. Xác định **package/class name đầy đủ** của từng bean liên quan.
3. Xác định **runtime module/classpath** chứa các bean ấy và các Gradle dependencies.
4. Liệt kê các root của **component scan thực sự hoạt động**, kể cả scan mặc định của Boot và config nhập thêm.
5. Kiểm tra đó là **cùng class quét lặp** hay **hai class khác nhau cùng bean name**.
6. Kiểm tra các đường đăng ký khác: `@Bean`, `@Import`, library auto-configuration, Spring Data.
7. Chọn một giải pháp có chủ đích: thu hẹp root, explicit names, FQ generator, module configuration hoặc filters.
8. Sau khi đổi tên, kiểm tra `@Qualifier`, `@Resource`, map keys và `getBean(String)`.
9. Kiểm thử context tương ứng với profiles/test/prod quan trọng; xác nhận đủ bean và không còn conflict.
10. Tránh dựa vào scan order hoặc bật global bean override để giấu vấn đề.

## 14. Ghi nhớ

- **Quét chồng lấn cùng component** thường được scanner nhận diện là cùng/compatible bean definition và bỏ qua đăng ký lặp; không đồng nghĩa tự sinh duplicate singleton.
- **Xung đột thực sự** thường bắt nguồn từ **hai bean definitions không tương thích cùng tên**, đặc biệt hai class khác package có simple class name giống nhau dưới default naming.
- **Gradle module** quyết định classpath, **Java package** quyết định phạm vi component scan, **bean name** quyết định định danh trong registry. Đây là ba ranh giới riêng.
- **Component scan root không phải module boundary theo JAR**. Thêm dependency có thể khiến scanner quét thêm component nếu package roots đã bao phủ nó.
- **Tên khác nhau không giải quyết tự động ambiguity theo type**. Sau khi đăng ký thành công, dependency đơn có thể cần `@Qualifier` hoặc `@Primary`.
- Khắc phục bằng scan scope rõ ràng, explicit naming, `FullyQualifiedAnnotationBeanNameGenerator` hoặc configuration entry point riêng cho module. Không dùng bean overriding như cách sửa mặc định.

### Tài liệu chính thức

- [Spring Framework — Classpath Scanning and Managed Components](https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html).
- [Spring Framework 6.2 — `ClassPathBeanDefinitionScanner` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/context/annotation/ClassPathBeanDefinitionScanner.html).
- [Spring Framework 6.2 — `@ComponentScan` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/context/annotation/ComponentScan.html).
- [Spring Framework 6.2 — `FullyQualifiedAnnotationBeanNameGenerator` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/context/annotation/FullyQualifiedAnnotationBeanNameGenerator.html).
- [Spring Framework — Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java.html).
- [Spring Boot — Structuring Your Code](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html).


</details>

- [Quay lại đầu trang](#back-to-top)
