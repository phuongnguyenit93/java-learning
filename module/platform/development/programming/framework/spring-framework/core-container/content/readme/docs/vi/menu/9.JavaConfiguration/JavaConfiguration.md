<a id="back-to-top"></a>

# Cấu hình Java

## Menu
- [Vai trò của @Configuration và phương thức @Bean](#java-configuration-001)
- [Đăng ký đối tượng thư viện không có @Component](#java-configuration-002)
- [Tham số phương thức @Bean biểu diễn phụ thuộc](#java-configuration-003)
- [Tên, bí danh và kiểu trả về của phương thức @Bean](#java-configuration-004)
- [Phương thức @Bean static và thời điểm container phát hiện kiểu](#java-configuration-005)
- [Chế độ đầy đủ của @Configuration và lớp tăng cường CGLIB](#java-configuration-006)
- [Chế độ lite và @Configuration(proxyBeanMethods = false)](#java-configuration-007)
- [Hành vi gọi trực tiếp giữa các phương thức @Bean](#java-configuration-008)
- [Giới hạn final, private và static khi chặn lời gọi @Bean](#java-configuration-009)
- [Tách việc tạo đối tượng khỏi công việc khởi tạo nghiệp vụ](#java-configuration-010)

## <a id="java-configuration-001">Vai trò của @Configuration và phương thức @Bean</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò của @Configuration và phương thức @Bean trong Spring Core Container

## 1. Mục tiêu

**Java-based configuration** cho phép mô tả Spring IoC container bằng mã Java thay vì chỉ dùng XML hay để component scanning tự tìm toàn bộ class. Hai annotation trung tâm là:

- `@Configuration`: đánh dấu **class là nguồn khai báo bean definitions**. Class này thường tập hợp các phương thức `@Bean` và có thể được nạp bằng `AnnotationConfigApplicationContext`, `@Import` hoặc cơ chế quét cấu hình.
- `@Bean`: đánh dấu **phương thức factory** tạo, cấu hình và trả về một đối tượng để Spring đăng ký và quản lý dưới dạng bean.

Điều quan trọng là phân biệt **phương thức Java trả object** với **bean được container quản lý**. Spring đọc cấu hình để xây bean definitions, chọn dependency, áp dụng scope, lifecycle, bean post-processors và các quy tắc khác. Khi tự gọi phương thức factory bên ngoài cơ chế Spring, đối tượng được trả về **không tự động** trở thành bean được container quản lý.

Bài này tập trung vào hai annotation trên trong **Spring Framework Core Container**, gồm:

1. Vai trò riêng của class `@Configuration` và phương thức `@Bean`.
2. Khi nào nên dùng chúng thay cho `@Component`/`@Service`.
3. Cơ chế đặt tên bean, dependency injection, scope và lifecycle.
4. Sự khác nhau giữa **full `@Configuration`** và **lite `@Bean`**, đặc biệt `proxyBeanMethods`.
5. Ví dụ Java 17 + Spring Framework chạy độc lập, với lệnh Maven và stdout dự kiến.
6. Các lỗi thiết kế, cấu hình và kiểm thử thường gặp.

## 2. Đặt hai annotation vào bức tranh IoC

Spring quản lý đối tượng dựa trên **bean definitions**. Một bean definition có thể mô tả tên, kiểu bean, cách khởi tạo, dependency, scope và lifecycle. Java configuration là một **nguồn metadata** để container tạo các bean definitions đó.

Xét cấu hình ngắn:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public MessageService messageService() {
        return new MessageService();
    }
}
```

Đây là **đoạn minh họa**: `MessageService` là class do ứng dụng định nghĩa, không phải API sẵn của Spring; ví dụ runnable đầy đủ ở mục 6.

`@Configuration` áp dụng lên `AppConfig`, diễn đạt "class này định nghĩa cấu hình cho container". `@Bean` áp dụng lên `messageService()`, diễn đạt "giá trị trả về từ factory method này phải được Spring quản lý như một bean". Nếu dùng Spring để đăng ký `AppConfig` đúng cách, container có thể cung cấp bean `messageService` cho những consumer cần kiểu `MessageService`.

Luồng khái niệm:

```text
AppConfig.class
      |
      | đọc @Configuration và các @Bean method
      v
Bean definitions trong IoC container
      |
      | giải quyết dependency, scope và lifecycle
      v
Object instance được Spring quản lý
      |
      | getBean / constructor injection
      v
Consumer sử dụng bean
```

Không nên hiểu `@Bean` là lời gọi `new` tự xảy ra ngay khi JVM đọc annotation. Tạo instance là bước của container, có thể khác nhau tùy scope, lazy initialization và thời điểm giải quyết dependency.

### So với cấu hình XML

Cấu hình:

```java
@Configuration
class Config {
    @Bean
    public MessageService messageService() {
        return new MessageService();
    }
}
```

về mục đích đăng ký bean tương tự định nghĩa XML:

```xml
<bean id="messageService" class="demo.MessageService"/>
```

XML trên chỉ minh họa cấu trúc; `demo.MessageService` phải thực sự tồn tại nếu sử dụng. Java config có thêm lợi ích compiler kiểm tra trực tiếp lời gọi constructor và kiểu dữ liệu.

## 3. Vai trò của @Configuration

`@Configuration` thuộc package:

```java
import org.springframework.context.annotation.Configuration;
```

Nó đánh dấu một **class** là cấu hình. Về mặt stereotype, `@Configuration` cũng được meta-annotate bằng `@Component`, nên có thể được tìm thấy khi component scanning phù hợp. Tuy nhiên, nếu bạn chỉ khởi tạo một class cấu hình bằng `new AppConfig()` thì Spring **không** tự nạp các bean definitions. Class cấu hình cần được **đăng ký** vào container.

### 3.1. Đăng ký class cấu hình

Một cách cơ bản:

```java
// Đoạn minh họa, giả định AppConfig đã tồn tại.
try (var context =
        new AnnotationConfigApplicationContext(AppConfig.class)) {
    MessageService service = context.getBean(MessageService.class);
    service.send("Hello");
}
```

Import `org.springframework.context.annotation.AnnotationConfigApplicationContext`. Constructor nhận `AppConfig.class` đăng ký cấu hình vào ApplicationContext và refresh context. Bean có thể được tra cứu theo kiểu/tên; khi thoát `try-with-resources`, context được đóng và lifecycle callback thích hợp được gọi.

Các cách khác:

- `@Import(OtherConfig.class)` để một cấu hình khai báo import cấu hình khác.
- Component scanning tìm class `@Configuration` trong vùng quét.
- Dùng `AnnotationConfigApplicationContext` rồi gọi `register(Config.class)` và `refresh()`.
- Trong ứng dụng Spring Boot, cơ chế bootstrap xử lý configuration classes thông qua các annotation của Boot; nhưng không cần Boot để hiểu `@Configuration` trong Core Container.

### 3.2. Class cấu hình chứa những gì?

Một class cấu hình có thể chứa:

- Phương thức `@Bean` trả về bean cần container quản lý.
- `@Import` để chia cấu hình thành module.
- `@ComponentScan` để chỉ định package chứa component ứng viên.
- `@PropertySource`, `@Profile`, `@Conditional` hoặc những annotation điều khiển đăng ký phù hợp, khi ứng dụng có nhu cầu.
- Constructor và các dependency của chính class cấu hình, nhưng cần cân nhắc thời điểm khởi tạo và tránh vòng phụ thuộc.

Không nhất thiết mỗi class cấu hình phải chứa nhiều phương thức `@Bean`. Có thể chia file theo feature như `PersistenceConfig`, `WebConfig`, `MessagingConfig` khi chúng thực sự quản lý những nhóm bean khác nhau. Tách theo **trách nhiệm cấu hình** tốt hơn một file khổng lồ chứa tất cả bean toàn hệ thống.

### 3.3. @Configuration là metadata, không phải service nghiệp vụ

Class `@Configuration` nên tập trung vào **wiring**: chọn implementation, xây đối tượng và khai báo dependency. Logic xử lý đơn hàng, tính giá và ghi dữ liệu nên nằm ở service/repository, không nằm trong method cấu hình.

Phản ví dụ:

```java
@Configuration
class PoorDesignConfig {

    @Bean
    PaymentService paymentService() {
        // Không nên tính toàn bộ nghiệp vụ thanh toán tại đây.
        // Chỉ nên xây dựng đối tượng và các collaborator.
        return new PaymentService();
    }
}
```

Đây là ví dụ phác thảo thiết kế, không phải ứng dụng độc lập. Method `paymentService()` là factory bean, không phải endpoint gọi để thanh toán từng đơn hàng.

## 4. Vai trò của @Bean

`@Bean` thuộc package:

```java
import org.springframework.context.annotation.Bean;
```

Annotation này được đặt trên **method**; Spring sử dụng phương thức như một factory để tạo đối tượng bean.

```java
@Configuration
class AppConfig {
    @Bean
    PaymentGateway paymentGateway() {
        return new InMemoryPaymentGateway();
    }
}
```

Trong đoạn minh họa, `PaymentGateway` là interface ứng dụng và `InMemoryPaymentGateway` là implementation. Spring đăng ký bean theo **return type được khai báo** và những metadata có thể phân giải khi chạy. Nếu container yêu cầu tạo bean này, nó gọi factory method trong ngữ cảnh phù hợp rồi quản lý instance trả về.

### 4.1. Tên bean mặc định

Thông thường, bean name mặc định từ phương thức `@Bean` là **tên phương thức**:

```java
@Bean
PaymentGateway paymentGateway() {
    return new InMemoryPaymentGateway();
}
```

Tên bean là `paymentGateway`. Có thể tra cứu:

```java
// Minh họa khi context đã được tạo.
PaymentGateway gateway =
        context.getBean("paymentGateway", PaymentGateway.class);
```

Ngoài tên, Spring vẫn hỗ trợ tiêm bean **theo kiểu** nếu có một ứng viên phù hợp và các quy tắc lựa chọn được thỏa. Tên bean và tên biến Java trong consumer không nhất thiết phải giống nhau để việc constructor injection hoạt động.

### 4.2. Đặt tên và alias tường minh

`@Bean` hỗ trợ `name` hoặc alias `value`:

```java
@Bean(name = {"defaultGateway", "primaryPaymentAdapter"})
PaymentGateway paymentGateway() {
    return new InMemoryPaymentGateway();
}
```

Ở đây `defaultGateway` là tên chính; `primaryPaymentAdapter` là **alias cho cùng bean**, không tạo hai instance riêng. Đây là alias bean name của container, **khác** annotation `@AliasFor` dùng để mô tả alias giữa các thuộc tính annotation.

Khi có nhiều bean cùng interface:

```java
@Bean("fastGateway")
PaymentGateway fastGateway() {
    return new FastGateway();
}

@Bean("safeGateway")
PaymentGateway safeGateway() {
    return new SafeGateway();
}
```

consumer nhận `PaymentGateway` đơn lẻ có thể cần `@Qualifier("safeGateway")` hoặc `@Primary`/quy tắc chọn ứng viên khác. `@Bean` không tự chọn "bean đầu tiên" khi kiểu có nhiều implementation. Hai phương thức trên là ví dụ minh họa yêu cầu các class tương ứng.

### 4.3. Kiểu trả về của factory method

Có thể khai báo kiểu interface:

```java
@Bean
Storage storage() {
    return new FileStorage();
}
```

hoặc class cụ thể:

```java
@Bean
FileStorage fileStorage() {
    return new FileStorage();
}
```

Cả hai đều hợp lệ nếu class tồn tại. Tuy nhiên, **metadata type để Spring dự đoán bean trước khi tạo instance** chịu ảnh hưởng bởi return type được khai báo. Nếu nơi khác muốn inject theo kiểu `FileStorage` nhưng factory chỉ khai báo `Storage`, Spring có thể chưa nhìn thấy implementation type ở một số thời điểm.

Nguyên tắc: khai báo kiểu trả về **đủ cụ thể cho injection points cần thiết**, nhưng không buộc tầng nghiệp vụ phụ thuộc implementation nếu không cần. Với API dựa trên interface, `Storage` thường là hợp đồng đúng; với các cơ chế xử lý đặc thù dựa trên implementation, cần cân nhắc metadata kiểu.

### 4.4. @Bean không chỉ tạo đối tượng mới bằng new

Factory method có thể:

- Trả về đối tượng được xây bằng constructor Java.
- Gọi builder/factory của thư viện.
- Tạo adapter bao quanh tài nguyên bên ngoài.
- Đăng ký đối tượng của thư viện bên thứ ba mà bạn **không sở hữu source** để gắn `@Component`.

```java
@Bean
ExternalClient externalClient(ClientProperties properties) {
    return ExternalClient.builder()
            .endpoint(properties.endpoint())
            .build();
}
```

Đây là đoạn minh họa; `ExternalClient` và `ClientProperties` là các kiểu giả định của ứng dụng/thư viện. Lợi ích ở đây là có thể quản lý bean của class không chứa Spring stereotype và vẫn cấu hình collaborator, scope và lifecycle.

## 5. Dependency injection qua tham số phương thức @Bean

Đây là cách wiring đáng ưu tiên vì rõ ràng và hoạt động với cả full lẫn lite configuration.

```java
@Configuration(proxyBeanMethods = false)
class AppConfig {

    @Bean
    PaymentGateway paymentGateway() {
        return new InMemoryPaymentGateway();
    }

    @Bean
    CheckoutService checkoutService(PaymentGateway paymentGateway) {
        return new CheckoutService(paymentGateway);
    }
}
```

Spring giải quyết tham số `PaymentGateway paymentGateway` tương tự constructor injection rồi truyền bean phù hợp vào `checkoutService(...)`. Bạn **không cần gọi** `paymentGateway()` thủ công bên trong `checkoutService()`.

Lợi ích:

1. Dependency được khai báo **công khai ngay trên chữ ký** phương thức.
2. Container quyết định candidate theo kiểu, qualifier, primary và metadata cần thiết.
3. Cách viết không phụ thuộc vào việc `@Configuration` có bật proxy bean methods hay không.
4. Khi thiếu bean bắt buộc, Spring báo lỗi khi tạo consumer thay vì âm thầm tạo đối tượng ngoài container.
5. Dễ chia cấu hình thành nhiều class bằng `@Import` hoặc component scan.

### 5.1. Phụ thuộc tới bean ở class cấu hình khác

```java
@Configuration(proxyBeanMethods = false)
class RepositoryConfig {

    @Bean
    AccountRepository accountRepository() {
        return new InMemoryAccountRepository();
    }
}

@Configuration(proxyBeanMethods = false)
@Import(RepositoryConfig.class)
class ServiceConfig {

    @Bean
    AccountService accountService(AccountRepository repository) {
        return new AccountService(repository);
    }
}
```

Đây là đoạn minh họa cấu trúc; các interface/implementation do ứng dụng cung cấp. `@Import` nhập cấu hình; method `accountService` nhận dependency do Spring phân giải. Không cần để `ServiceConfig` tự khởi tạo `RepositoryConfig` bằng `new` rồi gọi factory method.

### 5.2. Phân giải nhiều bean

Nếu có hai `PaymentGateway`, method nhận một `PaymentGateway` không luôn xác định được ứng viên. Có thể dùng:

```java
@Bean
CheckoutService checkoutService(
        @Qualifier("safeGateway") PaymentGateway gateway) {
    return new CheckoutService(gateway);
}
```

Nhớ import `org.springframework.beans.factory.annotation.Qualifier`. Nếu thật sự muốn nhận toàn bộ plugin, dùng `List<Plugin>`, `Map<String, Plugin>` hoặc `ObjectProvider<Plugin>` ở injection point thích hợp. Đừng dùng collection chỉ để né lỗi nhập nhằng khi service thật sự cần một bean duy nhất.

### 5.3. Phụ thuộc tùy chọn

Một method `@Bean` cũng có thể khai báo `Optional<AuditSink>` khi audit là tùy chọn hợp lệ:

```java
@Bean
CheckoutService checkoutService(
        PaymentGateway gateway,
        java.util.Optional<AuditSink> auditSink) {
    return new CheckoutService(gateway, auditSink);
}
```

Đây là minh họa, constructor của `CheckoutService` trong dự án cần phù hợp. Phụ thuộc cốt lõi `PaymentGateway` vẫn bắt buộc; `AuditSink` được phép vắng mặt. Không nên chuyển mọi tham số thành `Optional` hoặc `ObjectProvider` chỉ để ứng dụng khởi động khi cấu hình thiếu.

## 6. Ví dụ Java 17 + Spring Framework chạy độc lập

Ví dụ dùng **Spring Framework thuần** (không cần Spring Boot), Java 17 và Maven. Nó cho thấy:

- `@Configuration(proxyBeanMethods = false)` là nguồn các bean definitions.
- `@Bean` tạo gateway và service.
- Method `checkoutService(PaymentGateway gateway)` nhận dependency do Spring tiêm.
- `@Bean(name = {"auditClient", "auditAlias"})` tạo tên chính và alias.
- `initMethod`/`destroyMethod` khiến Spring quản lý vòng đời của object.
- Tra cứu theo kiểu và tên trả cùng singleton trong phạm vi một context.

### 6.1. Cấu trúc

```text
java-config-demo/
├── pom.xml
└── src/main/java/demo/JavaConfigDemo.java
```

### 6.2. POM

Tạo `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>java-config-demo</artifactId>
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

### 6.3. Chương trình

Tạo `src/main/java/demo/JavaConfigDemo.java`:

```java
package demo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class JavaConfigDemo {

    interface PaymentGateway {
        String charge(String orderId);
    }

    static final class DemoGateway implements PaymentGateway {
        @Override
        public String charge(String orderId) {
            return "receipt-" + orderId;
        }
    }

    static final class CheckoutService {
        private final PaymentGateway gateway;

        CheckoutService(PaymentGateway gateway) {
            this.gateway = gateway;
        }

        String checkout(String orderId) {
            return gateway.charge(orderId);
        }
    }

    static final class AuditClient {
        private boolean ready;

        void initialize() {
            ready = true;
            System.out.println("audit.init");
        }

        void shutdown() {
            ready = false;
            System.out.println("audit.shutdown");
        }

        boolean isReady() {
            return ready;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AppConfig {

        @Bean
        PaymentGateway paymentGateway() {
            return new DemoGateway();
        }

        @Bean
        CheckoutService checkoutService(PaymentGateway paymentGateway) {
            return new CheckoutService(paymentGateway);
        }

        @Bean(
                name = {"auditClient", "auditAlias"},
                initMethod = "initialize",
                destroyMethod = "shutdown"
        )
        AuditClient auditClient() {
            return new AuditClient();
        }
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            CheckoutService first =
                    context.getBean(CheckoutService.class);
            CheckoutService second =
                    context.getBean("checkoutService", CheckoutService.class);

            System.out.println("receipt=" + first.checkout("42"));
            System.out.println("sameService=" + (first == second));
            System.out.println("alias=" +
                    (context.getBean("auditClient") ==
                     context.getBean("auditAlias")));
            System.out.println("auditReady=" +
                    context.getBean(AuditClient.class).isReady());
        }
    }
}
```

### 6.4. Chạy và quan sát

Từ thư mục chứa `pom.xml`, cài JDK 17+ và Maven rồi chạy:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.JavaConfigDemo
```

Các dòng **do ứng dụng in ra** (không tính log Maven hoặc môi trường):

```text
audit.init
receipt=receipt-42
sameService=true
alias=true
auditReady=true
audit.shutdown
```

Tại sao output này đáng tin?

1. `AnnotationConfigApplicationContext(AppConfig.class)` tạo/refresh context với ba bean definitions từ ba method `@Bean`. Những singleton thông thường được khởi tạo trong giai đoạn startup; `auditClient` có callback `initialize()` nên in `audit.init` trước khi code trong `main` in receipt.
2. `CheckoutService` nhận cùng `PaymentGateway` mà container đã phân giải theo kiểu. Method factory không gọi thủ công `paymentGateway()`.
3. `checkoutService` mặc định là singleton: hai lời gọi `getBean` theo kiểu và theo tên tham chiếu cùng instance nên `sameService=true`.
4. `auditClient` và `auditAlias` là **hai tên cho cùng một bean**, không phải hai bean riêng, nên `alias=true`.
5. `initMethod = "initialize"` được gọi trước khi bean sẵn sàng cho consumer; `isReady()` trả `true`.
6. Khi thoát `try-with-resources`, context đóng và Spring gọi `shutdown()` trên singleton `AuditClient`, in `audit.shutdown`.

**Điều kiện cần nhớ:** Với singleton non-lazy, bean lifecycle của Spring diễn ra ở startup/close như trong ví dụ. Với prototype hoặc scoped bean, lifecycle destruction không có cùng bảo đảm như singleton; không suy rộng output này sang mọi scope. Chương trình cố ý giữ class đơn giản, không dùng logging framework.

### 6.5. Bài thử nhỏ

Thay:

```java
@Bean
CheckoutService checkoutService(PaymentGateway paymentGateway) {
    return new CheckoutService(paymentGateway);
}
```

bằng:

```java
@Bean("primaryCheckout")
CheckoutService checkoutService(PaymentGateway paymentGateway) {
    return new CheckoutService(paymentGateway);
}
```

Khi đó bean name chính đổi từ `checkoutService` sang `primaryCheckout`. Nếu `main` vẫn gọi `getBean("checkoutService", CheckoutService.class)`, nó sẽ không còn tìm được bean theo tên cũ, trừ khi có alias hoặc định nghĩa bean khác. Lấy theo kiểu `CheckoutService.class` vẫn có thể hoạt động nếu chỉ có một bean phù hợp.

## 7. Full `@Configuration` và cơ chế `proxyBeanMethods`

`@Configuration` mặc định dùng `proxyBeanMethods=true`: Spring tăng cường class cấu hình bằng CGLIB để các lời gọi **instance `@Bean` method** phù hợp trên cấu hình do container quản lý có thể đi qua `BeanFactory`. Với `proxyBeanMethods=false`, lời gọi Java trực tiếp giữ nguyên nghĩa Java. **Cả hai chế độ đều đăng ký bean definitions, hỗ trợ DI và lifecycle.**

### 7.1. Full configuration mặc định

```java
@Configuration
class FullConfig {
    @Bean Repository repository() { return new Repository(); }
    @Bean Service service() { return new Service(repository()); }
}
```

Đoạn này minh họa hai kiểu `Repository` và `Service` đã được định nghĩa. Khi container gọi `service()` trên **enhanced `FullConfig`**, lời gọi `repository()` có thể được chặn để lấy bean theo scope. Với singleton mặc định, service nhận cùng managed reference như `context.getBean(Repository.class)`. Tự `new FullConfig()` ngoài container không có interception.

### 7.2. Lite mode và hướng sửa an toàn

Nếu đổi annotation thành `@Configuration(proxyBeanMethods=false)` mà vẫn gọi `repository()` trong `service()`, Java chạy factory method trực tiếp và có thể tạo **một `Repository` bên ngoài managed bean**. Đây không phải bean `prototype` và cũng không có nghĩa `@Bean` mất hiệu lực.

Cách nên ưu tiên trong cả hai chế độ:

```java
@Configuration(proxyBeanMethods = false)
class ParameterConfig {
    @Bean Repository repository() { return new Repository(); }
    @Bean Service service(Repository repository) {
        return new Service(repository);
    }
}
```

Tham số `Repository` được Spring resolve từ BeanFactory theo type/qualifier/scope, nên không cần gọi một phương thức factory khác để lấy bean.

### 7.3. Bảng đối chiếu

| Điều kiện | Full (mặc định) | Lite (`proxyBeanMethods=false`) |
| --- | --- | --- |
| Container đăng ký và quản lý `@Bean` | Có | Có |
| Direct inter-bean instance call | Có thể được chặn qua enhanced config | Lời gọi Java thông thường |
| Dependency bằng tham số `@Bean` | Có | Có |
| Subclass CGLIB cho method interception | Có | Không cần |

Chỉ chuyển sang lite khi không còn inter-bean calls cần semantics của container; kiểm tra và thay chúng bằng dependency parameters nếu phù hợp.

### 7.4. Giới hạn của subclassing

Full configuration cần class có thể kế thừa và instance `@Bean` methods có thể override: class `final`, method `private`/`final` không phù hợp; `static @Bean` được hỗ trợ nhưng lời gọi static không có interception. Bài về full mode và bài giới hạn `final`/`private`/`static` sẽ phân tích sâu lý do.

## 8. @Bean có thể nằm trong @Component không?

Có. Spring cho phép phương thức `@Bean` trong class `@Component` thông thường. Nhưng đó là **lite @Bean mode**, không phải full configuration processing chỉ vì có bean method.

```java
@Component
class HelperFactory {

    @Bean
    Worker worker() {
        return new Worker();
    }
}
```

Đây là đoạn minh họa (cần `Worker` và `@Component` import). Nếu `HelperFactory` được Spring phát hiện, `worker()` có thể đóng góp bean definition. Tuy nhiên, gọi `worker()` trực tiếp từ phương thức khác trên `HelperFactory` **không** được Spring intercept giống full `@Configuration`.

Hãy phân biệt:

- **Component scanning:** tìm class `@Component` hoặc stereotype meta-present.
- **Cấu hình bean definitions:** nhận diện `@Bean` methods để đăng ký factory definitions.
- **Full configuration enhancement:** hành vi proxy method calls chỉ có khi cấu hình full đúng điều kiện.

Vì mục tiêu của một class là định nghĩa nhiều bean, `@Configuration` thường diễn đạt ý định rõ hơn `@Component`. Nếu không cần interception, `@Configuration(proxyBeanMethods=false)` là cách khai báo minh bạch.

## 9. Scope và thời điểm khởi tạo bean

`@Bean` mặc định tạo **singleton trong mỗi Spring IoC container**, nếu không khai báo scope khác.

```java
@Bean
CacheClient cacheClient() {
    return new CacheClient();
}
```

Một ApplicationContext lấy bean `cacheClient` nhiều lần thường nhận **cùng instance**, nhưng mỗi ApplicationContext độc lập có singleton riêng.

### Prototype scope

```java
@Bean
@Scope("prototype")
TaskWorker taskWorker() {
    return new TaskWorker();
}
```

Đoạn minh họa cần các class/import tương ứng. Spring trả một instance mới theo yêu cầu phân giải prototype bean. Tuy nhiên, nếu `TaskWorker` prototype được **inject trực tiếp một lần** vào constructor của một singleton, singleton đó thường giữ instance đã nhận; việc gọi phương thức trên singleton không tự làm Spring inject prototype mới.

Khi singleton cần **lấy một prototype mới theo từng tác vụ**, có thể dùng `ObjectProvider<TaskWorker>` hoặc factory chuyên dụng; không thay đổi scope hoặc lạm dụng lời gọi trực tiếp `taskWorker()` khi `proxyBeanMethods=false`.

### Lazy bean

```java
@Bean
@Lazy
LargeClient largeClient() {
    return new LargeClient();
}
```

`@Lazy` thay đổi thời điểm khởi tạo trong những điều kiện phù hợp; không có nghĩa bean bị loại khỏi container hay có thể thiếu. Với singleton non-lazy, Spring thường tạo trong quá trình context refresh. Với lazy bean, việc khởi tạo có thể bị trì hoãn đến khi có yêu cầu; nếu một singleton eager khác cần nó ngay, dependency đó vẫn có thể khiến bean được tạo sớm.

**Không nhầm `@Bean` với `@Scope`:** Factory method định nghĩa bean; scope định nghĩa chính sách quản lý số lượng/lifetime instance.

## 10. Vòng đời bean và @Bean(initMethod, destroyMethod)

Vì `@Bean` trả đối tượng được Spring quản lý, Spring có thể áp dụng:

- Dependency injection và callback theo lifecycle.
- BeanPostProcessor và các cơ chế proxy/phụ trợ được bật trong context.
- `@PostConstruct` và `@PreDestroy` nếu các annotation và hỗ trợ liên quan có mặt.
- `InitializingBean` / `DisposableBean`.
- `initMethod` / `destroyMethod` khai báo ngay trong `@Bean`.

Ví dụ:

```java
@Bean(initMethod = "connect", destroyMethod = "close")
ConnectionClient connectionClient() {
    return new ConnectionClient();
}
```

Đây là ví dụ minh họa cần `ConnectionClient.connect()` và `ConnectionClient.close()`. Spring gọi init callback theo lifecycle sau khi tạo và thiết lập bean; destroy callback diễn ra khi container đóng đối với singleton phù hợp.

### Khi bean có close/shutdown method

Spring có cơ chế **suy luận destruction callback** cho một số method như `close` hoặc `shutdown` trên đối tượng phù hợp. Nếu resource **không thuộc quyền sở hữu lifecycle** của context (ví dụ tài nguyên JNDI/managed resource do hạ tầng khác quản lý), có thể cần:

```java
@Bean(destroyMethod = "")
ExternalManagedResource externalResource() {
    return lookupExternalResource();
}
```

Đây là minh họa; `ExternalManagedResource` và `lookupExternalResource` phải được định nghĩa trong ứng dụng. Dùng `destroyMethod=""` để vô hiệu hóa inferred destroy callback khi Spring không nên tự đóng tài nguyên bên ngoài.

Không đóng một resource do Spring quản lý thủ công tại nhiều nơi rồi lại kỳ vọng destroy callback sẽ không gọi nữa, trừ khi implementation thật sự idempotent và lifecycle được thiết kế có chủ đích.

### Prototype destruction

Spring xử lý **khởi tạo** prototype, nhưng thường không quản lý việc **hủy prototype** giống singleton sau khi trả object cho client. Nếu bean prototype giữ connection/file/resource, phía sử dụng phải có chính sách cleanup phù hợp. Không lấy ví dụ `AuditClient` singleton ở mục 6 làm bảo đảm rằng mọi prototype sẽ tự gọi `destroyMethod` khi context đóng.

## 11. Nạp và kết hợp Java configuration

Ở ứng dụng nhiều module, có thể chia cấu hình:

```java
@Configuration(proxyBeanMethods = false)
class AdapterConfig {

    @Bean
    Adapter adapter() {
        return new Adapter();
    }
}

@Configuration(proxyBeanMethods = false)
@Import(AdapterConfig.class)
class BusinessConfig {

    @Bean
    BusinessService businessService(Adapter adapter) {
        return new BusinessService(adapter);
    }
}
```

Đây là các class minh họa, cần import `Configuration`, `Bean`, `Import` và các class ứng dụng. Đăng ký `BusinessConfig` vào context sẽ kéo theo `AdapterConfig`, và `BusinessService` nhận `Adapter` do Spring phân giải. Không cần gọi trực tiếp `new AdapterConfig().adapter()`.

Nếu muốn kết hợp với component scanning:

```java
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.example.application")
class ApplicationConfig {
}
```

Các class mang stereotype trong `com.example.application` và package con có thể được quét. Đồng thời, các method `@Bean` trong `ApplicationConfig` cũng đăng ký các bean factory definitions. Hai cách **bổ sung cho nhau**, không nhất thiết phải chọn chỉ một.

### Đăng ký cấu hình qua context

Có thể dùng:

```java
var context = new AnnotationConfigApplicationContext();
context.register(BusinessConfig.class);
context.refresh();
try {
    // lấy bean và chạy tác vụ
} finally {
    context.close();
}
```

Đây là đoạn minh họa, cần `BusinessConfig` hợp lệ. Phải gọi `refresh()` sau khi `register()` khi dùng constructor rỗng để hoàn tất khởi tạo context. Khi dùng constructor nhận class cấu hình như ví dụ mục 6, Spring thực hiện refresh trong constructor.

## 12. @Configuration và @Bean so với @Component / @Service

| Tiêu chí | `@Component` / `@Service` | `@Configuration` + `@Bean` |
| --- | --- | --- |
| Nơi gắn annotation chính | Trên class ứng viên | `@Configuration` trên class cấu hình, `@Bean` trên factory method |
| Bean được tạo từ đâu | Scanner phát hiện class stereotype và container quản lý quá trình khởi tạo | Container gọi factory method được khai báo |
| Phù hợp | Class ứng dụng tự kiểm soát source, dependency qua constructor | Third-party client, adapter cần builder/cấu hình phức tạp, nhóm wiring |
| Cần component scan? | Thường có, hoặc được đăng ký qua cơ chế khác | Không bắt buộc nếu config được đăng ký trực tiếp |
| Đặt tên bean | Thường dựa class name hoặc stereotype value | Thường tên factory method, có thể dùng `@Bean(name=...)` |
| Xử lý vòng đời | Bean managed bình thường | Bean managed bình thường |
| Cần proxy method calls? | Không phải mục tiêu của stereotype | Chỉ khi `@Configuration` full và có nhu cầu gọi bean method khác |

Không phải cứ có `@Bean` thì Spring tạo ra object "cao cấp" hơn `@Component`. **Cùng vào container thì cả hai đều là bean** và chịu các quy tắc scope/lifecycle phù hợp. Khác biệt chủ yếu là **cách khai báo factory và mức độ kiểm soát cách khởi tạo**.

### Khi nên dùng @Bean?

- Cần đăng ký bean của thư viện bên thứ ba, không thể sửa source để gắn `@Component`.
- Cần truyền config values vào builder/constructor có nhiều tùy chọn.
- Muốn cung cấp nhiều bean cùng class nhưng cấu hình khác nhau.
- Muốn định nghĩa rõ alias, scope, init/destroy callback tại factory boundary.
- Muốn module hóa wiring và thay implementation theo môi trường.

### Khi nên dùng @Component/@Service?

- Class thuộc ứng dụng, có constructor dependency rõ ràng.
- Không cần logic factory phức tạp.
- Component scanning đã được thiết lập có chủ đích.
- Muốn cấu trúc ít boilerplate, tránh liệt kê hàng trăm factory methods đơn giản.

Tránh vừa gắn `@Component` lên một implementation vừa khai báo `@Bean` tạo cùng implementation một cách vô thức. Điều đó có thể tạo **hai bean khác nhau cùng kiểu**, dẫn tới nhập nhằng khi tiêm một dependency đơn lẻ. Nếu thực sự cần nhiều instance cấu hình khác nhau, đặt tên/qualifier và test số ứng viên rõ ràng.

## 13. Những hiểu lầm phổ biến

### "Có @Configuration thì mọi method bên trong tự trở thành bean"

**Sai.** Chỉ những method được Spring nhận diện là `@Bean` (hoặc các cơ chế đăng ký khác) đóng góp bean definitions. Một helper method bình thường không tự trở thành factory bean.

```java
@Configuration
class Config {
    @Bean
    Client client() {
        return createClient();
    }

    private Client createClient() {
        return new Client();
    }
}
```

Đây là minh họa, `Client` là class ứng dụng. `createClient()` chỉ là helper, không đăng ký thêm bean riêng; nó được gọi trong `client()` để tạo bean `client`.

### "Cứ gọi appConfig.beanMethod() là lấy bean Spring"

**Sai nếu không xét ngữ cảnh.** Với class cấu hình tạo bằng `new` bên ngoài context, đây là lời gọi Java bình thường. Trong full `@Configuration` object đã được Spring enhance, inter-bean calls có interception. Trong lite mode, lời gọi trực tiếp vẫn bình thường. Để chắc chắn lấy bean được quản lý, hãy tiêm dependency qua method parameter hoặc tra cứu theo context trong tầng wiring/thử nghiệm khi cần.

### "Đổi @Configuration(proxyBeanMethods=false) chỉ là tối ưu, không đổi hành vi"

**Không luôn đúng.** Nếu cấu hình cũ gọi trực tiếp một phương thức `@Bean` từ phương thức `@Bean` khác, khi tắt proxy, lời gọi không được Spring intercept; có thể tạo thêm object ngoài bean identity/scope đúng dự kiến. Nên thay bằng parameter-based dependency injection trước khi tắt proxy.

### "@Bean luôn tạo singleton mới mỗi lần được gọi"

**Sai.** Singleton/prototype là **scope của bean resolution trong container**. Một factory method có thể được gọi một lần khi container tạo singleton; cũng có thể được gọi mỗi lần Spring phân giải prototype. Nhưng một lời gọi trực tiếp qua Java trên lite config là chuyện khác và không bảo đảm cùng instance với bean.

### "Có @Bean thì không cần đưa config vào context"

**Sai.** Class cấu hình phải được đăng ký/import/scanned đúng cơ chế Spring. Một file Java có annotation nhưng không nằm trong ApplicationContext không tự tạo bean definitions.

### "Bean của thư viện bất kỳ đều nên gắn @Component bằng cách chỉnh source"

**Không cần.** `@Bean` cho phép tạo và quản lý instance thư viện mà không yêu cầu chỉnh sửa class đó. Đây là tình huống dùng Java config rất phù hợp.

### "@Bean(name = {a, b}) tạo hai singleton"

**Sai.** Mảng tên của một bean definition chỉ ra tên chính và alias, không phải hai bean instance riêng. Nếu cần hai instance, viết hai factory methods hoặc đăng ký hai bean definitions riêng, kèm qualifier nếu cần.

### "@Bean được dùng như endpoint nghiệp vụ"

**Sai.** Factory method dùng để **định nghĩa/tạo bean**, không nên được gọi mỗi lần cần xử lý một request thanh toán hoặc thực thi job. Nghiệp vụ thuộc methods của bean đã được tạo, không thuộc phương thức `@Bean`.

## 14. Vấn đề kỹ thuật nâng cao cần nhận biết

### 14.1. @Bean và BeanFactoryPostProcessor

Một số bean thuộc hạ tầng như `BeanFactoryPostProcessor` hoặc `BeanPostProcessor` tham gia vào giai đoạn **rất sớm** của container. Spring tài liệu khuyên các phương thức `@Bean` cho một số post-processor như `BeanFactoryPostProcessor` thường nên là `static` để tránh phải tạo instance class cấu hình quá sớm, ảnh hưởng injection/callback trên chính class cấu hình.

```java
@Configuration
class InfrastructureConfig {

    @Bean
    static MyBeanFactoryPostProcessor myPostProcessor() {
        return new MyBeanFactoryPostProcessor();
    }
}
```

Đây là minh họa; `MyBeanFactoryPostProcessor` là implementation cần định nghĩa riêng. Không chuyển mọi `@Bean` thành `static` một cách máy móc: cần dựa vào vai trò bean và các yêu cầu lifecycle.

### 14.2. @Bean method có tham số phụ thuộc vào scope

Nếu một factory method singleton nhận một dependency prototype qua tham số, prototype có thể được tạo **khi singleton được xây**. Sau đó singleton giữ reference đó, không tự lấy prototype mới cho mỗi method invocation. Muốn lookup theo lượt, nên dùng provider/factory chuyên dụng và hiểu scope.

### 14.3. Chú ý vòng phụ thuộc và @PostConstruct của config

Class cấu hình cũng là bean đặc biệt và được xử lý sớm. Không nên gọi những `@Bean` methods trong `@PostConstruct` của chính configuration class để "ép" tạo các bean khác. Điều này có thể gây early initialization hoặc circular reference phức tạp. Đưa dependency vào method parameters và để Spring quản lý trình tự tạo bean.

### 14.4. Return type và bean prediction

Spring phải suy ra nhiều thông tin về ứng viên trước khi tạo bean. Nếu một method trả interface rộng, Spring có thể không biết mọi interface/concrete type thực của object trong giai đoạn bean type matching. Với các injection points cần implementation-specific type, hãy khai báo return type đủ cụ thể hoặc sửa injection point để phụ thuộc interface ổn định.

### 14.5. Trùng bean name và override

Hai phương thức hoặc hai configuration classes đăng ký cùng tên bean có thể dẫn tới lỗi, hoặc override một definition tùy cấu hình container và chính sách framework. **Không giả định** Spring sẽ luôn chọn method khai báo sau. Chọn tên bean rõ ràng, hạn chế đăng ký trùng và kiểm thử kết quả trong đúng kiểu ApplicationContext/Boot configuration của ứng dụng.

## 15. Chẩn đoán lỗi theo triệu chứng

| Triệu chứng | Nguyên nhân thường gặp | Cách khắc phục |
| --- | --- | --- |
| `NoSuchBeanDefinitionException` khi lấy bean | Class cấu hình chưa được đăng ký/import, factory method không có `@Bean`, qualifier/type không khớp | Kiểm tra metadata, phạm vi config và tên/kiểu bean |
| `UnsatisfiedDependencyException` | Tham số factory method hoặc constructor thiếu bean bắt buộc | Đọc dependency nào thiếu rồi đăng ký/định danh đúng implementation |
| `NoUniqueBeanDefinitionException` | Nhiều bean cùng kiểu, không chọn được duy nhất | Dùng `@Qualifier`, `@Primary` hoặc thiết kế collection đúng nhu cầu |
| `BeanCreationException` trong method factory | Constructor/builder của thư viện ném lỗi; config không hợp lệ | Xem root cause trong stack trace, sửa factory/config values |
| Có hai instance dù định nghĩa singleton | Gọi trực tiếp phương thức `@Bean` trong lite config; tự gọi `new AppConfig()` | Tiêm bean qua factory method parameters; để Spring quản lý |
| Lỗi không enhance được `@Configuration` | Full config hoặc bean methods bị `final`/`private` không phù hợp | Chuyển sang mode lite với wiring đúng hoặc bỏ final/private cần interception |
| Bean name khác dự kiến | Dùng `@Bean(name=...)` hoặc custom naming strategy | Kiểm tra tên khai báo và tra cứu theo type khi phù hợp |
| `destroyMethod` không chạy với prototype | Prototype không được container hủy tự động như singleton | Thiết kế cleanup do consumer sở hữu |
| `initMethod` không chạy như mong đợi | Object được tạo bằng `new` ngoài context hoặc callback chỉ áp dụng instance khác | Xác nhận bạn dùng bean được Spring quản lý |
| Bean được tạo sớm dù có provider | Non-lazy singleton đã được pre-instantiated | Kiểm tra bean scope và lazy configuration |
| Thêm `@Component` và `@Bean` cho cùng class gây ambiguous | Hai bean definitions trùng kiểu | Chọn một cách đăng ký hoặc qualifier rõ |
| Gọi `register()` nhưng vẫn chưa lấy được bean | Context tạo qua constructor rỗng chưa `refresh()` | Gọi `refresh()` rồi tra cứu; nhớ đóng context |

Quy trình debug:

1. Đảm bảo class cấu hình có trong ApplicationContext (truyền class vào constructor, `register`, `@Import` hoặc scan).
2. Đảm bảo factory method mang đúng `@Bean` và return type hợp lệ.
3. Xem bean name mặc định có đổi do `name`/`value` không.
4. Xem mỗi parameter của `@Bean` có bao nhiêu ứng viên và qualifier nào áp dụng.
5. Kiểm tra scope/lazy/lifecycle; phân biệt object do container tạo với object được gọi bằng `new` trực tiếp.
6. Nếu có gọi giữa `@Bean` methods, kiểm tra `proxyBeanMethods`.
7. Đọc exception nguyên nhân gốc, không chỉ exception wrapper ngoài cùng.

## 16. Bài tập và đáp án định hướng

### Bài 1 — Vai trò hai annotation

`@Configuration` và `@Bean` khác nhau ở đâu?

**Đáp án:** `@Configuration` đánh dấu class cấu hình làm nguồn bean definitions; `@Bean` đánh dấu factory method đóng góp một bean definition và trả object container quản lý. Không phải mọi method của class `@Configuration` đều tự biến thành bean.

### Bài 2 — Lấy bean bằng tên

Với `@Bean CheckoutService checkoutService(...)`, tên bean mặc định là gì? Nếu dùng `@Bean(name={"checkoutA","checkoutAlias"})` thì có mấy bean được định nghĩa?

**Đáp án:** Mặc định tên là `checkoutService`. Mảng tên tạo **một bean definition** với tên chính `checkoutA` và alias `checkoutAlias`.

### Bài 3 — Full và lite

Cho đoạn sau:

```java
@Configuration(proxyBeanMethods = false)
class Config {
    @Bean
    Repository repository() { return new Repository(); }

    @Bean
    Service service() { return new Service(repository()); }
}
```

Nếu `service()` gọi `repository()` trực tiếp, `Service` có chắc nhận đúng singleton bean `repository` mà container đang quản lý không?

**Đáp án:** Không. Trong lite mode, `repository()` là lời gọi Java bình thường, có thể tạo instance riêng. Sửa thành `Service service(Repository repository)` để Spring inject dependency.

### Bài 4 — Bean của thư viện

Bạn cần đăng ký `HttpClient` từ JDK hoặc một client của thư viện thứ ba nhưng không thể sửa source class đó. Nên chọn component scan trực tiếp trên client class hay `@Bean`?

**Đáp án:** `@Bean` phù hợp. Factory method có thể xây client bằng builder/constructor và đưa instance vào container. Nếu client giữ tài nguyên, cần cân nhắc lifecycle/ownership.

### Bài 5 — Hai implementation

Có hai bean `MailSender` cùng kiểu. Một service phải dùng chính xác sender cho giao dịch. Nếu parameter `MailSender mailSender` bị nhập nhằng, nên làm gì?

**Đáp án:** Dùng `@Qualifier` tại parameter hoặc `@Primary` khi có một ứng viên mặc định đúng. Không tự chọn theo thứ tự đăng ký và không thay tất cả bằng collection nếu nghiệp vụ cần một sender cụ thể.

### Bài 6 — Vòng đời

Một `@Bean(initMethod="start", destroyMethod="stop")` là singleton. Khi nào callback có thể chạy?

**Đáp án:** `start` chạy theo lifecycle initialization khi bean được tạo/xử lý; `stop` thường chạy khi singleton được container hủy hoặc context đóng. Với prototype, Spring không tự đảm bảo destroy callback khi context đóng như singleton.

### Bài 7 — Viết cấu hình rõ ràng

Cho `Repository` và `ReportService` có constructor nhận `Repository`. Hãy viết hai `@Bean` trong một `@Configuration(proxyBeanMethods=false)` sao cho `ReportService` dùng bean `Repository` do Spring quản lý.

**Đáp án định hướng:**

```java
@Configuration(proxyBeanMethods = false)
class ReportConfig {
    @Bean
    Repository repository() {
        return new Repository();
    }

    @Bean
    ReportService reportService(Repository repository) {
        return new ReportService(repository);
    }
}
```

Đây là mẫu cấu hình; cần thêm các class `Repository` và `ReportService` tương ứng. Điểm chính là `Repository` được đưa vào **tham số factory method**, không gọi `repository()` trực tiếp.

## 17. Checklist áp dụng trong dự án

Trước khi kết luận Java configuration hoạt động đúng, kiểm tra:

- Đã đưa `@Configuration` vào đúng ApplicationContext.
- Mỗi bean cần đăng ký bằng factory method đều có `@Bean`.
- `@Bean` method trả kiểu hữu ích cho các injection points.
- Bean names/aliases không vô tình xung đột.
- Dependency bắt buộc được khai báo bằng constructor hoặc parameter method rõ ràng.
- Nếu tắt `proxyBeanMethods`, không dựa vào inter-bean direct calls để lấy container-managed instance.
- Scope/lazy semantics phù hợp cách sử dụng singleton, prototype hoặc tài nguyên ngoài.
- Init/destroy callback đúng với chủ sở hữu lifecycle của bean.
- Không đăng ký trùng cùng implementation một cách vô tình giữa component scanning và `@Bean`.
- Có smoke test bằng `AnnotationConfigApplicationContext` hoặc integration test xác nhận bean có mặt, đúng identity và lifecycle.

## Tổng kết

**`@Configuration` mô tả nơi chứa cấu hình; `@Bean` mô tả phương thức tạo bean.** Spring dùng chúng để xây bean definitions và quản lý dependency, scope, lifecycle, callback cùng các cơ chế container khác. Khi xây Java config mới, **tiêm dependency qua tham số phương thức `@Bean`** thường rõ ràng và an toàn với cả full lẫn lite mode.

Chỉ dựa vào lời gọi giữa `@Bean` methods nếu bạn hiểu full `@Configuration` và `proxyBeanMethods=true`. Với `proxyBeanMethods=false`, lời gọi đó là Java bình thường, có thể tạo object ngoài managed singleton identity. Khi muốn biểu diễn wiring mà không sửa source của thư viện, `@Bean` là công cụ tự nhiên; khi class ứng dụng chỉ cần được scanner phát hiện, `@Component`/`@Service` có thể đơn giản hơn.

## Tài liệu Spring chính thức

- [Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java.html) — tổng quan Java configuration.
- [Basic Concepts: @Bean and @Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/basic-concepts.html) — định nghĩa hai annotation và full/lite mode.
- [Using the @Bean Annotation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html) — bean name, method parameters, lifecycle và return type.
- [Using the @Configuration Annotation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/configuration-annotation.html) — inter-bean dependencies và enhancement.
- [Composing Java-based Configurations](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html) — `@Import` và wiring giữa các configuration classes.
- [Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html) — singleton, prototype và scope semantics.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-configuration-002">Đăng ký đối tượng thư viện không có @Component</a>

<details>
<summary>Xem chi tiết</summary>

# Đăng ký đối tượng thư viện không có @Component bằng cấu hình Java trong Spring

## 1. Tình huống và kết quả cần đạt

Trong ứng dụng Spring, rất nhiều đối tượng cần sử dụng được cung cấp bởi thư viện bên ngoài, không thuộc mã nguồn của đội phát triển: `com.fasterxml.jackson.databind.ObjectMapper` để xử lý JSON, `java.time.Clock` để kiểm soát thời gian, HTTP client, client SDK, bộ mã hóa, bộ kết nối và các adapter của bên thứ ba.

Những class này thường **không mang `@Component`**. Ta cũng thường không có quyền sửa source code hoặc thêm annotation vào class trong JAR. Vì vậy, nếu chỉ bật `@ComponentScan` và hy vọng Spring tự đăng ký mọi đối tượng thư viện, kết quả thường là thiếu bean.

Cách chuẩn trong **cấu hình Java của Spring Core Container** là khai báo một phương thức **`@Bean`** trong class cấu hình **`@Configuration`**. Phương thức ấy tạo hoặc lấy đối tượng, cấu hình các thuộc tính cần thiết rồi **trả đối tượng cho container quản lý**. Spring đăng ký bean definition của factory method và sử dụng nó để đáp ứng dependency injection.

Sau bài học, bạn có thể:

1. Giải thích vì sao một class thư viện không có `@Component` vẫn trở thành Spring bean bằng `@Bean`.
2. Viết `@Configuration` và factory method `@Bean` trả object từ thư viện ngoài; phân biệt đăng ký bean với tự gọi `new` trong mã nghiệp vụ.
3. Tiêm các dependency vào **tham số của phương thức `@Bean`**, kể cả khi object thư viện nhận dependency qua constructor.
4. Chọn đúng bean khi có nhiều instance cùng kiểu, qua `@Qualifier`, tên bean hoặc `@Primary`.
5. Quản lý scope, lazy initialization, init/destroy callback và ownership của tài nguyên.
6. Hiểu `@Configuration(proxyBeanMethods=false)`, sự khác nhau giữa dependency injection và **gọi trực tiếp phương thức `@Bean`**.
7. Chạy ví dụ Java 17+ dùng **Jackson ObjectMapper** thực tế, không có component scan.

> Ý tưởng trung tâm: **Spring quản lý object vì đã có một bean definition hợp lệ**, không phải vì class của object bắt buộc phải mang annotation Spring.

## 2. Vì sao component scanning chưa giải quyết vấn đề?

Giả sử thư viện cung cấp:

```java
// Thuộc thư viện ngoài; giả sử class không có annotation Spring.
package vendor.sdk;

public final class ExternalClient {
    public ExternalClient(String endpoint, String token) {
        // ...
    }

    public String fetch(String id) {
        // ...
        return id;
    }
}
```

Đây là class phác họa để giải thích API constructor của một SDK giả định; không phải thư viện thật có thể thêm vào Gradle. Class không chứa `@Component`, `@Service` hay meta-annotation `@Component`, và constructor cần thông tin cấu hình. `@ComponentScan` với bộ lọc stereotype mặc định sẽ không tự tạo bean `ExternalClient`.

Để đăng ký, sử dụng factory method:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExternalClientConfig {
    @Bean
    ExternalClient externalClient() {
        return new ExternalClient(
                "https://api.example.test",
                "demo-token"
        );
    }
}
```

Đoạn này tiếp tục là **ví dụ cấu trúc**, giả định có `vendor.sdk.ExternalClient` và import phù hợp; URL/token chỉ mang tính minh họa và không phải cấu hình production. Với dự án thật, endpoint/secret cần đến từ nguồn cấu hình an toàn, không hard-code vào source.

Điều quan trọng là class `ExternalClient` **không cần sửa**. Phương thức `externalClient()` là nơi mã của ứng dụng quyết định cách tạo object; Spring gọi factory method khi cần tạo bean theo scope của nó.

### Component scan và @Bean trả lời hai câu hỏi khác nhau

| Thành phần | Câu hỏi | Trường hợp phù hợp |
| --- | --- | --- |
| `@ComponentScan` | Class nào trên classpath được phát hiện theo package/filter? | Các class do ứng dụng kiểm soát, có thể đánh dấu stereotype Spring. |
| `@Component` / `@Service` | Class này có là ứng viên component trong lượt scan không? | Service, adapter, handler nằm trong mã nguồn của ứng dụng. |
| `@Bean` | Phương thức nào tạo/cấu hình một object rồi đăng ký nó thành bean? | Class thư viện, object cần builder/factory, setup phức tạp, class không mang stereotype. |
| `@Configuration` | Class nào chứa Java configuration và các bean factory methods? | Tách logic lắp ghép hạ tầng khỏi nghiệp vụ. |

Một project có thể đồng thời dùng cả hai đường đăng ký. `@ComponentScan` không bắt buộc tồn tại thì `@Bean` mới hoạt động; ngược lại, `@Bean` không loại bỏ nhu cầu quét các service của ứng dụng.

## 3. Cơ chế thực tế khi Spring xử lý @Bean

Một class cấu hình Java thường có dạng:

```java
@Configuration
public class InfraConfig {
    @Bean
    public ThirdPartyType thirdPartyType() {
        return new ThirdPartyType(/* tham số cấu hình */);
    }
}
```

`ThirdPartyType` chỉ là kiểu giả định, đoạn này chưa thể biên dịch nếu chưa có thư viện cụ thể. Đường đi logic:

```text
Ứng dụng đăng ký InfraConfig
             |
             v
ConfigurationClassPostProcessor
   phân tích @Configuration + @Bean
             |
             v
Đăng ký BeanDefinition:
   beanName = "thirdPartyType"
   factoryBean = InfraConfig
   factoryMethod = thirdPartyType
             |
             v
Spring cần tạo bean theo scope
             |
             v
Gọi factory method -> lấy object
             |
             v
BeanPostProcessor, init callback, lifecycle
             |
             v
Object có thể tiêm vào các bean khác
```

**Các mốc phải hiểu đúng:**

- `@Bean` là annotation **trên method**, không phải annotation buộc phải đặt lên class thư viện.
- Trong Spring 6.2, tên bean mặc định thông dụng là tên method: `thirdPartyType`. Có thể đặt tên cụ thể bằng `@Bean("someName")`.
- Kiểu khai báo ở return type của phương thức có ảnh hưởng đến quá trình Spring tìm bean theo type; nên khai báo kiểu đủ cụ thể cho injection point cần dùng.
- Spring không “biến source thư viện thành component”. Container quản lý **object do factory method cung cấp**.
- Việc đăng ký bean definition, tạo instance và gọi init callback là những bước khác nhau. Bean singleton không lazy thường được tạo khi refresh context; `prototype` được tạo theo mỗi lần yêu cầu.
- Spring vẫn có thể áp dụng dependency injection và lifecycle processing trên object trả về từ `@Bean` nếu annotation/hạ tầng liên quan được cấu hình.

## 4. Viết @Bean đúng cách: từ tối giản đến có dependency

### 4.1. Object không cần dependency

```java
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {
    @Bean
    Clock appClock() {
        return Clock.systemUTC();
    }
}
```

`Clock` thuộc thư viện chuẩn Java; class đó không mang `@Component`. Sau khi `TimeConfig` được nạp vào Spring, bean `appClock` có thể inject vào service:

```java
import java.time.Clock;
import java.time.Instant;

public final class AuditService {
    private final Clock clock;

    public AuditService(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return Instant.now(clock);
    }
}
```

`AuditService` ở đây là class Java bình thường; muốn nó trở thành Spring bean thì cần đăng ký qua `@Bean` hoặc component scan phù hợp. Việc cấu hình `Clock` độc lập giúp test dùng fixed clock và production dùng system clock mà không phải gọi `Instant.now()` trực tiếp ở nhiều chỗ.

### 4.2. Factory method nhận dependency từ container

Nếu một client phải dùng object khác đã được đăng ký, khai báo dependency trong **tham số `@Bean`**:

```java
@Configuration(proxyBeanMethods = false)
public class ClientConfig {
    @Bean
    ClientSettings clientSettings() {
        return new ClientSettings("https://example.test");
    }

    @Bean
    VendorClient vendorClient(ClientSettings settings) {
        return new VendorClient(settings.endpoint());
    }
}
```

`ClientSettings` và `VendorClient` là kiểu minh họa, không phải class Spring dựng sẵn. Spring resolve `ClientSettings` theo dependency injection thông thường, rồi gọi `vendorClient(settings)`. Đây là cách rõ ràng để lắp ghép object, đặc biệt khi sử dụng `proxyBeanMethods=false`.

**Tránh** viết `vendorClient() { return new VendorClient(clientSettings().endpoint()); }` khi đặt `proxyBeanMethods=false`: đó là **lời gọi Java thông thường**, tạo thêm `ClientSettings` thay vì chắc chắn sử dụng singleton container quản lý. Truyền tham số phương thức là cách nhất quán.

### 4.3. Đăng ký đối tượng được tạo bằng builder/factory của thư viện

Không phải thư viện nào cũng có public constructor thích hợp. Factory method có thể gọi builder hoặc static factory:

```java
@Bean
ExternalSdkClient sdkClient(AppProperties settings) {
    return ExternalSdkClient.builder()
            .endpoint(settings.endpoint())
            .timeout(settings.timeout())
            .build();
}
```

Đây là **pseudocode Java API của SDK giả định**. Spring quan tâm object cuối cùng được trả về; cách thư viện tạo object (constructor, builder, static factory, connection pool) là quyết định của chính thư viện.

## 5. Ví dụ hoàn chỉnh: đăng ký Jackson ObjectMapper không có @Component

Ví dụ thật sau đây dùng:

- **Java 17+**.
- **Spring Framework 6.2.9** với `AnnotationConfigApplicationContext`.
- **Jackson Databind 2.18.2**: class `ObjectMapper` của thư viện ngoài **không cần `@Component`**.
- Hai bean `ObjectMapper` khác cấu hình, một bean `Clock`, một bean dịch vụ được lắp qua tham số `@Bean`.
- Hoàn toàn **không khai báo `@ComponentScan`**.

Ví dụ cố định thời gian để kết quả stdout có thể kiểm tra lại mà không phụ thuộc ngày hiện tại.

### 5.1. Cấu trúc file

```text
third-party-bean-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        └── java/
            └── demo/
                └── ThirdPartyBeansDemo.java
```

**`settings.gradle`**

```groovy
rootProject.name = 'third-party-bean-demo'
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
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.18.2'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.ThirdPartyBeansDemo'
}
```

Spring Context có dependency transitive cho các thành phần IoC cần thiết. Jackson Databind mang những dependency Jackson cần thiết theo cơ chế Gradle thông thường. Đây là ví dụ Spring thuần; ứng dụng Spring Boot thường dùng dependency management/BOM của Boot để đồng bộ versions.

### 5.2. Chương trình Java

**`src/main/java/demo/ThirdPartyBeansDemo.java`**

```java
package demo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class ThirdPartyBeansDemo {

    // Đây chỉ là class Java bình thường, không có @Component.
    public static final class ReportService {
        private final ObjectMapper mapper;
        private final Clock clock;

        public ReportService(ObjectMapper mapper, Clock clock) {
            this.mapper = mapper;
            this.clock = clock;
        }

        public String buildReport() throws JsonProcessingException {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("source", "jackson");
            data.put("year", LocalDate.now(clock).getYear());
            return mapper.writeValueAsString(data);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {

        // Jackson ObjectMapper không có @Component trong thư viện.
        @Bean("compactMapper")
        ObjectMapper compactMapper() {
            return new ObjectMapper();
        }

        @Bean("prettyMapper")
        ObjectMapper prettyMapper() {
            return new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT);
        }

        @Bean
        Clock clock() {
            return Clock.fixed(
                    Instant.parse("2024-06-11T00:00:00Z"),
                    ZoneOffset.UTC
            );
        }

        // Hai ObjectMapper cùng kiểu: chọn đúng tên ở injection point.
        @Bean
        ReportService reportService(
                @Qualifier("compactMapper") ObjectMapper mapper,
                Clock clock) {
            return new ReportService(mapper, clock);
        }
    }

    public static void main(String[] args) throws JsonProcessingException {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            System.out.println("compact=" +
                    context.containsBean("compactMapper"));
            System.out.println("pretty=" +
                    context.containsBean("prettyMapper"));
            System.out.println("mapper-count=" +
                    context.getBeansOfType(ObjectMapper.class).size());

            ObjectMapper first = context.getBean(
                    "compactMapper", ObjectMapper.class);
            ObjectMapper second = context.getBean(
                    "compactMapper", ObjectMapper.class);

            System.out.println("singleton=" + (first == second));
            System.out.println("report=" +
                    context.getBean(ReportService.class).buildReport());
        }
    }
}
```

**Chạy từ thư mục dự án:**

```bash
gradle run --quiet
```

Nếu dự án có Gradle Wrapper, trên Linux/macOS có thể dùng `./gradlew run --quiet`; trên Windows dùng `gradlew.bat run --quiet`. Gradle sử dụng cấu hình dependency và toolchain để biên dịch/chạy. Chạy `javac ThirdPartyBeansDemo.java` đơn lẻ sẽ thiếu thư viện Spring/Jackson trừ khi tự cấu hình classpath.

**Các dòng do chương trình in ra:**

```text
compact=true
pretty=true
mapper-count=2
singleton=true
report={"source":"jackson","year":2024}
```

Lý do output cố định:

1. `Config.class` được truyền trực tiếp cho `AnnotationConfigApplicationContext`; Spring xử lý `@Configuration` và bốn factory method `@Bean`. Không có `@ComponentScan`.
2. `compactMapper` và `prettyMapper` là **hai bean definition** cùng kiểu `ObjectMapper`, đều trả một object của thư viện Jackson. Cả hai tên đều tồn tại trong context.
3. `getBeansOfType(ObjectMapper.class)` tìm thấy hai bean, nên `mapper-count=2`.
4. Mặc định `@Bean` có scope singleton. Hai lần `getBean("compactMapper", ObjectMapper.class)` trong cùng context trả cùng instance.
5. Spring chọn `compactMapper` cho phương thức `reportService(...)` nhờ `@Qualifier("compactMapper")`. Nó không nhầm với `prettyMapper` dù cả hai có cùng return type.
6. `Clock.fixed(...)` luôn cung cấp cùng thời điểm ngày 11/6/2024 UTC, nên năm là 2024.
7. `LinkedHashMap` duy trì thứ tự put là `source` rồi `year`; `compactMapper` không bật pretty print, nên JSON một dòng chính xác như stdout.
8. `ReportService` cũng **không mang `@Component`**, nhưng nó vẫn trở thành bean vì method `@Bean ReportService reportService(...)` đã đăng ký.

**Đây là demo về cấu hình Java và IoC**; nó không khởi động web server, không cần Spring MVC/Boot và không truy cập network. Các log của Gradle/môi trường build, nếu có, nằm ngoài năm dòng output chủ động trên.

## 6. Chọn @Bean name, @Qualifier và @Primary

### 6.1. Một thư viện có thể cần nhiều instance cùng kiểu

Một hệ thống thực tế có thể dùng hai `ObjectMapper`:

- Một mapper xuất JSON public theo quy tắc của API.
- Một mapper cho log nội bộ với format khác.

Hoặc có hai HTTP client cùng class nhưng khác endpoint, timeout hay credentials. Khi cùng kiểu có nhiều bean, injection chỉ theo type có thể trở nên mơ hồ.

```java
@Bean("internalMapper")
ObjectMapper internalMapper() {
    return new ObjectMapper();
}

@Bean("externalMapper")
ObjectMapper externalMapper() {
    return new ObjectMapper();
}
```

Đoạn này dùng cùng dependency Jackson của demo và cần nằm trong một class cấu hình. Nếu class consumer nhận duy nhất một `ObjectMapper` mà không đủ thông tin để chọn ứng viên, Spring có thể ném `NoUniqueBeanDefinitionException`.

**Chọn rõ bằng qualifier:**

```java
@Bean
ExternalExporter externalExporter(
        @Qualifier("externalMapper") ObjectMapper mapper) {
    return new ExternalExporter(mapper);
}
```

`ExternalExporter` là class nghiệp vụ minh họa, không có sẵn trong Spring/Jackson. `@Qualifier` gắn tại injection point giúp chọn bean tương ứng.

### 6.2. Chọn một ứng viên mặc định bằng @Primary

```java
@Bean
@Primary
ObjectMapper appMapper() {
    return new ObjectMapper();
}

@Bean
ObjectMapper legacyMapper() {
    return new ObjectMapper();
}
```

Đây là đoạn cấu hình cần import `org.springframework.context.annotation.Primary`. Khi injection theo kiểu `ObjectMapper` mà không có qualifier chi tiết, `@Primary` có thể giúp chọn `appMapper`. Tuy nhiên, đừng dùng `@Primary` để che việc có nhiều cấu hình khác nhau mà nghiệp vụ cần gọi đích xác.

**Phân biệt:** `@Primary` có tác dụng trong quá trình **lựa chọn dependency**; nó không thay tên bean, scope hay loại bỏ bean còn lại khỏi context.

### 6.3. Tên method, tên bean và alias

```java
@Bean({"jsonWriter", "publicJsonWriter"})
ObjectMapper writer() {
    return new ObjectMapper();
}
```

Đoạn này tạo **một** bean với tên `jsonWriter` và alias `publicJsonWriter`, không phải hai object độc lập. Dùng hai method `@Bean` riêng nếu thật sự cần hai cấu hình/instance. Trong bản cấu hình thông thường, `@Bean ObjectMapper objectMapper()` có tên mặc định `objectMapper`.

## 7. Lắp dependency qua tham số phương thức @Bean

Giả sử client thư viện cần `Clock`, `ObjectMapper` và configuration:

```java
@Bean
VendorClient vendorClient(
        @Qualifier("compactMapper") ObjectMapper mapper,
        Clock clock,
        ClientOptions options) {
    return new VendorClient(mapper, clock, options);
}
```

Đây là **ví dụ minh họa**; `VendorClient` và `ClientOptions` là kiểu do ứng dụng/thư viện giả định cung cấp. Spring giải quyết từng tham số method giống như dependency injection thông thường. Cách này có ba ưu điểm:

- Dependency được thấy rõ ở chữ ký factory method.
- Có thể thay object trong test bằng cấu hình khác mà không đụng đến source code thư viện.
- Không phải gọi trực tiếp các `@Bean` methods khác rồi vô tình tạo object ngoài vòng đời dự kiến.

### Nên trả về interface hay concrete type?

Nếu API công khai của bean chỉ cần interface `Client`, có thể khai báo:

```java
@Bean
Client client() {
    return new HttpClientAdapter();
}
```

`Client` và `HttpClientAdapter` là kiểu giả định. Tuy nhiên, Spring có thể dựa trên **declared return type** khi cần dự đoán type của bean **trước khi tạo instance**. Nếu nơi khác inject trực tiếp `HttpClientAdapter` nhưng factory method khai báo chỉ trả `Client`, khả năng type matching sớm có thể bị hạn chế.

Một nguyên tắc hữu ích: **khai báo return type đủ cụ thể** cho những injection point được phép sử dụng bean. Nếu muốn các lớp chỉ biết interface, giữ nhất quán kiểu interface tại injection point và API service.

### Tạo bean qua static factory method của SDK

```java
@Bean
VendorConnection connection(VendorSettings settings) {
    return VendorConnection.open(
            settings.endpoint(),
            settings.accessToken()
    );
}
```

Snippet cấu trúc, không phải API SDK có thật. Trong Spring, `@Bean` có thể gọi bất kỳ cách tạo object hợp lệ nào mà vendor công bố. Không cần Spring biết constructor nội bộ của SDK nếu factory method trả đúng object.

## 8. @Configuration(proxyBeanMethods=false) và gọi @Bean method

Đây là điểm dễ tạo bug khi tích hợp thư viện.

### 8.1. Hai cách lắp ghép bean

**Nên dùng: factory method parameters.**

```java
@Configuration(proxyBeanMethods = false)
class Config {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TimeService timeService(Clock clock) {
        return new TimeService(clock);
    }
}
```

`TimeService` ở đây là kiểu ví dụ giả định có constructor nhận `Clock`. Container giải quyết tham số `Clock` theo scope và rules hiện hành.

**Cần lưu ý: direct method call.**

```java
@Configuration(proxyBeanMethods = false)
class Config {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TimeService timeService() {
        return new TimeService(clock());
    }
}
```

Vì `proxyBeanMethods=false`, lệnh `clock()` trong `timeService()` là một lời gọi Java bình thường. Nó tạo một `Clock` **khác đối tượng đã đăng ký làm bean** (với cách factory ở trên), nếu method thực sự tạo object mới. Trong một số trường hợp, lỗi này gây hai connection pools/HTTP clients hoặc khác lifecycle.

Ở chế độ đầy đủ `@Configuration(proxyBeanMethods=true)`, Spring thường dùng class enhancement để những lời gọi giữa các `@Bean` methods được chuyển qua container theo scope. Nhưng code dùng **tham số injection** vẫn rõ hơn và không phụ thuộc cách proxy hóa class cấu hình.

**Quy tắc:** Với `proxyBeanMethods=false`, không dùng lời gọi chéo `@Bean` methods để giả định lấy singleton container. Chọn injection qua parameters; đây cũng là cách dễ test và ít ngạc nhiên.

### 8.2. Đừng gọi factory method từ business code

Dòng:

```java
new ExternalClientConfig().externalClient();
```

nếu cấu hình và method được truy cập hợp lệ, chỉ là **tạo một object Java**. Nó không tương đương với `applicationContext.getBean(ExternalClient.class)`. Object được tạo bằng cách gọi thủ công vào configuration mới có thể không nhận lifecycle/proxy/configuration từ container.

Muốn sử dụng bean, inject vào constructor của service, hoặc ở điểm khởi động/kiểm thử dùng `ApplicationContext.getBean(...)`. Trong domain/business code, tránh phụ thuộc trực tiếp container khi constructor injection đã đủ.

## 9. Scope và lazy initialization cho bean thư viện

### 9.1. Singleton mặc định

`@Bean` mặc định singleton (trong một container). Hợp lý với những đối tượng có khả năng dùng lại như mapper đã cấu hình, immutable config hoặc client thread-safe theo tài liệu vendor.

```java
@Bean
ObjectMapper objectMapper() {
    return new ObjectMapper();
}
```

Khi object đã được tạo và cấu hình hoàn chỉnh, các lần yêu cầu bean trong cùng context thường nhận cùng instance. Tuy nhiên, **không tự suy luận mọi object thư viện đều thread-safe** chỉ vì Spring cung cấp singleton; xem hợp đồng của từng thư viện và tránh mutate cấu hình sau khi chia sẻ.

Ví dụ `ObjectMapper` của Jackson thường có thể dùng đồng thời an toàn **khi tất cả cấu hình được hoàn tất trước các lời gọi read/write đầu tiên**. Thay đổi cấu hình chung khi nhiều thread đang sử dụng có thể gây hành vi khó đoán; nếu cần các cấu hình khác nhau, đăng ký mapper riêng hoặc dùng cấu hình reader/writer bất biến phù hợp.

### 9.2. Prototype cho object có trạng thái riêng

```java
@Bean
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
VendorRequestBuilder requestBuilder(VendorSettings options) {
    return new VendorRequestBuilder(options);
}
```

Đây là ví dụ với `VendorRequestBuilder`/`VendorSettings` giả định; cần import `Scope` và `ConfigurableBeanFactory`. Prototype phù hợp khi đối tượng giữ state của từng yêu cầu và vendor không cho tái sử dụng an toàn.

Nếu singleton consumer cần một instance mỗi lần xử lý, có thể inject `ObjectProvider<VendorRequestBuilder>` và gọi `getObject()` khi bắt đầu công việc. Đừng inject một prototype trực tiếp vào singleton rồi cho rằng Spring tự đổi object cho mọi method call.

### 9.3. @Lazy chỉ thay thời điểm tạo, không thay scope

```java
@Bean
@Lazy
ObjectMapper expensiveMapper() {
    return new ObjectMapper();
}
```

Cần import `org.springframework.context.annotation.Lazy`. Với bean singleton, `@Lazy` thường trì hoãn tạo bean tới khi thực sự được yêu cầu. Nhưng nếu một singleton eager khác cần mapper trong constructor/factory method, dependency ấy có thể buộc Spring tạo mapper sớm. `@Lazy` không biến singleton thành prototype.

### 9.4. Vòng đời tài nguyên thư viện

Một SDK client có `close()`/`shutdown()` có thể nắm socket, executor hoặc thread riêng. Với `@Bean`, Spring hỗ trợ:

- `initMethod`: gọi method init do tên khai báo nếu thích hợp.
- `destroyMethod`: gọi method hủy theo tên; với cách cấu hình mặc định, Spring có khả năng suy luận `close`/`shutdown` công khai cho bean thích hợp.
- `@PreDestroy` hoặc `DisposableBean` khi bản thân class/adapter hỗ trợ và processor phù hợp.
- `AutoCloseable`/lifecycle API tùy quy tắc của framework và class.

Ví dụ **cấu trúc** khi SDK có method `start()` và `stop()`:

```java
@Bean(initMethod = "start", destroyMethod = "stop")
VendorConnection managedConnection(VendorSettings options) {
    return new VendorConnection(options);
}
```

Đừng giả định mọi SDK có các method này. Nếu vendor trả một singleton toàn cục, client mượn từ container khác, object quản lý bởi app server hoặc lifecycle thuộc tổ chức khác, **Spring không nên đóng tài nguyên ngoài quyền sở hữu**. Khi đó có thể dùng `@Bean(destroyMethod = "")` để tắt suy luận destroy method.

**Lưu ý:** Container thường không tự quản lý đầy đủ callback hủy cho bean prototype sau khi cấp instance; code sử dụng cần đóng tài nguyên đúng thời điểm.

## 10. Lấy cấu hình từ bên ngoài, không hard-code secrets

Class thư viện thường cần host, credential, timeout. Phương thức `@Bean` là điểm lắp ghép tốt, nhưng secret không nên xuất hiện như chuỗi cứng trong Git.

Một ví dụ dùng Spring Environment:

```java
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class ClientConfiguration {
    @Bean
    VendorClient vendorClient(Environment environment) {
        String endpoint = environment.getRequiredProperty("vendor.endpoint");
        String apiKey = environment.getRequiredProperty("vendor.api-key");

        return new VendorClient(endpoint, apiKey);
    }
}
```

`VendorClient` là kiểu minh họa. `Environment` là bean/đối tượng hạ tầng mà Spring có thể cung cấp khi thực thi factory method. `getRequiredProperty` giúp lỗi cấu hình thiếu xuất hiện sớm. Nguồn property có thể đến từ cấu hình ứng dụng, environment variables, secret store hoặc các `PropertySource` phù hợp.

Trong Spring Boot, `@ConfigurationProperties` thường hợp lý hơn cho nhiều field cấu hình, validation và test; đó là tính năng hạ tầng Boot bổ sung. Dù cách đọc cấu hình khác nhau, nguyên tắc `@Bean` vẫn là tạo/configure object SDK từ thông tin đã resolve.

**Đừng log credential** khi debug lỗi bean creation. Ghi context đủ để chẩn đoán endpoint/chế độ, nhưng che token và các giá trị nhạy cảm.

## 11. Import và tổ chức nhiều configuration class

Tách cấu hình theo nhóm là hữu ích:

```java
@Configuration(proxyBeanMethods = false)
class JsonLibraryConfig {
    @Bean
    ObjectMapper apiMapper() {
        return new ObjectMapper();
    }
}

@Configuration(proxyBeanMethods = false)
@Import(JsonLibraryConfig.class)
class ApplicationConfig {
    @Bean
    ReportService reportService(ObjectMapper apiMapper) {
        return new ReportService(apiMapper);
    }
}
```

Đây là snippet cấu trúc khác với demo chạy ở trên: `ReportService` ở đây là kiểu giả định có constructor chỉ nhận `ObjectMapper`; cần import `Bean`, `Configuration`, `Import`, `ObjectMapper`. `@Import` giúp nạp class cấu hình chứa bean definitions. Không cần đặt `@Component` trên `ObjectMapper`.

Với ứng dụng nhiều module, mỗi nhóm thư viện có thể có configuration riêng: JSON, HTTP client, database adapter, cache hoặc message producer. Cấu hình nào được import/scan sẽ quyết định bean có xuất hiện trong context đó hay không.

**Không nhập nhằng:** `@Import(SomeConfig.class)` và `@ComponentScan` khác nhau. `@Import` khai báo trực tiếp nguồn cấu hình/type liên quan, trong khi scan tìm class theo package/filter.

## 12. Đăng ký một thư viện mà không có @Bean cũng có thể, nhưng khi nào?

Có những API đăng ký bean bằng chương trình:

```java
AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext();

context.registerBean(
        "appClock",
        Clock.class,
        Clock::systemUTC
);
context.refresh();
try {
    Clock clock = context.getBean("appClock", Clock.class);
    // Dùng clock.
} finally {
    context.close();
}
```

Đây là code Java hợp lệ khi có import `java.time.Clock` và `org.springframework.context.annotation.AnnotationConfigApplicationContext` cùng Spring dependencies. `registerBean(...)` đăng ký bean definition với supplier; cũng không đòi `@Component` trên `Clock`.

Tuy nhiên, **`@Bean` thường dễ đọc và bảo trì hơn** khi dự án đã theo Java configuration. Đăng ký bằng chương trình phù hợp với bootstrap động, plugin registrar, framework integration hoặc code cần dựng context theo metadata runtime.

Có thể đăng ký instance sẵn có qua API bean factory, nhưng **vòng đời, khởi tạo và việc đóng object** cần hiểu chính xác theo API được chọn; không coi việc gán một instance vào registry như hoàn toàn tương đương với factory method `@Bean`.

## 13. Những lỗi phổ biến và hướng sửa

### 13.1. Quét đúng package của ứng dụng nhưng không thấy class từ JAR

**Triệu chứng:** inject `ObjectMapper` hoặc client SDK gây `NoSuchBeanDefinitionException`/`UnsatisfiedDependencyException`.

**Nguyên nhân:** class thư viện không mang stereotype phù hợp và không có bean definition do `@Bean`/autoconfiguration cung cấp.

**Sửa:** khai báo một method `@Bean` trả đúng type và nạp class `@Configuration` vào context. Đừng mặc định rằng mở rộng `@ComponentScan` tới package vendor sẽ giải quyết được.

### 13.2. Có @Bean nhưng class @Configuration không được nạp

Một file cấu hình chỉ nằm trong source code mà **không được register, import, scan hoặc autoconfigure** thì không tự chạy. Kiểm tra `AnnotationConfigApplicationContext(Config.class)`, `@Import`, vị trí package của Spring Boot application và condition/profile liên quan.

### 13.3. Hai instance cùng kiểu gây lỗi ambiguous dependency

Nếu có hai `ObjectMapper` và một injection point chỉ biết `ObjectMapper`, Spring có thể báo `NoUniqueBeanDefinitionException`.

**Sửa:** `@Qualifier` chọn đúng bean, hoặc `@Primary` khi thật sự có default. Nếu use case cần tất cả instance, inject `Map<String, ObjectMapper>` hoặc collection phù hợp để chủ động lựa chọn.

### 13.4. Gọi @Bean method trực tiếp với proxyBeanMethods=false

Ví dụ `new Client(mapper())` tạo thêm object `ObjectMapper` bên ngoài instance đã đăng ký khi `mapper()` dùng `new ObjectMapper()`. Điều này có thể làm cấu hình, identity hoặc lifecycle bị lệch. **Sửa:** truyền `ObjectMapper mapper` trong tham số của factory method khác.

### 13.5. Tạo client bằng new trong mỗi request

Nếu client hoặc connection pool có chi phí khởi tạo cao, tạo trực tiếp trong request handler có thể tốn tài nguyên và khó shutdown.

**Sửa:** đăng ký bean singleton **nếu class thread-safe và lifecycle phù hợp**, inject client vào service. Không tự chuyển mọi thư viện sang singleton; phải kiểm tra vendor documentation.

### 13.6. Spring đóng object do nơi khác sở hữu

Một object có `close()` công khai có thể bị Spring suy luận destroy callback, trong khi tài nguyên đó do application server hoặc bên khác quản lý. Hệ quả là đóng sớm một connection dùng chung.

**Sửa:** xác định ownership; nếu không thuộc quyền Spring, dùng `@Bean(destroyMethod = "")` hoặc cơ chế đăng ký/lifecycle phù hợp và kiểm thử shutdown.

### 13.7. Quên đóng object do Spring sở hữu

Nếu bean giữ thread/socket và cần `shutdown()` nhưng không được cấu hình destroy callback, shutdown context có thể không giải phóng tài nguyên.

**Sửa:** xác nhận API lifecycle của thư viện, cấu hình `destroyMethod` khi thích hợp, đóng context đúng cách và kiểm thử nguồn lực sau shutdown.

### 13.8. Factory method trả interface quá tổng quát

Spring có thể không dự đoán được concrete type tại các điểm injection xảy ra trước khi singleton được tạo. **Sửa:** khai báo return type đủ cụ thể hoặc thống nhất dependency injection bằng service interface.

### 13.9. Custom mapper được Spring quản lý nhưng bị thay đổi sau khi dùng

Một `ObjectMapper` dùng chung được reconfigure khi nhiều thread đang serialize. Thao tác này có thể gây lỗi khó tái hiện.

**Sửa:** cấu hình hoàn tất trước khi bean được publish cho callers; dùng instance riêng cho mỗi policy, hoặc reader/writer được tạo theo API Jackson thích hợp.

### 13.10. @Bean có rồi nhưng cho rằng @ComponentScan sẽ không tạo bean trùng

Nếu cùng class vừa được scan qua một stereotype, vừa có factory method tạo bean khác cùng type, context có thể có **hai instance khác tên** hoặc xung đột bean names.

**Sửa:** kiểm tra từng đường đăng ký, tránh đăng ký đúp, dùng tên/qualifier rõ ràng. Không dựa vào việc bean definition overriding luôn được bật vì hành vi cấu hình có thể khác theo môi trường.

### 13.11. Dùng @Bean để sửa source của thư viện

`@Bean` không gắn annotation vào class vendor, không biến mọi lời gọi `new VendorType()` trong toàn chương trình thành managed bean, và không tự thay đổi hành vi đối tượng được tạo ngoài container.

**Sửa:** mọi code cần object được cấu hình hãy **inject bean từ Spring**; hạn chế khởi tạo thủ công ở đường nghiệp vụ.

### 13.12. Dùng @ConditionalOnMissingBean trong Spring Framework thuần

`@ConditionalOnMissingBean` là annotation thuộc **Spring Boot autoconfigure**, không phải annotation lõi có sẵn trong project chỉ dependency `spring-context`.

**Sửa:** với Spring thuần, quản lý cấu hình explicit hoặc dùng `@Conditional` và một `Condition` tương ứng nếu có nhu cầu; với Boot, dùng conditional auto-configuration đúng ngữ cảnh để cung cấp default bean mà ứng dụng vẫn có thể override theo policy.

## 14. Kiểm thử và quan sát bean đăng ký

### 14.1. Kiểm tra bean names, identity và type

Ví dụ hoàn chỉnh ở mục 5 đã dùng:

```java
context.containsBean("compactMapper");
context.getBeansOfType(ObjectMapper.class).size();
context.getBean("compactMapper", ObjectMapper.class);
```

Ba phép quan sát kiểm tra sự tồn tại, số lượng và instance. Khi thêm bean cùng kiểu, nên kiểm tra cả tên và cách resolve injection point, vì `getBean(ObjectMapper.class)` có thể mơ hồ dù `containsBean("compactMapper")` trả true.

### 14.2. Kiểm tra bean definition đến từ đâu

```java
var beanFactory = context.getBeanFactory();
var definition = beanFactory.getBeanDefinition("compactMapper");

System.out.println(definition.getFactoryMethodName());
```

Đây là snippet để đặt trong `try` của demo; kết quả từ bean definition `@Bean("compactMapper") ObjectMapper compactMapper()` thông thường là `compactMapper`. Có thể xem `getResourceDescription()` để truy dấu configuration, nhưng chuỗi cụ thể thay đổi theo môi trường.

### 14.3. Kiểm tra đường injection thay vì chỉ getBean()

Một test tốt nên xác nhận `ReportService.buildReport()` thực sự xuất JSON compact. Nếu vô tình tiêm `prettyMapper`, output sẽ có indent/xuống dòng và khác kết quả mong đợi. Đây là tín hiệu trực tiếp rằng `@Qualifier` hoặc assembly đã sai.

### 14.4. Test scope và lifecycle

- Với singleton, hai lời gọi `getBean("compactMapper")` trong cùng context trả cùng instance.
- Với prototype được cấu hình trên method `@Bean`, hai lookup thông thường tạo instance khác; không nên gán trạng thái cho cả hai rồi nghĩ sẽ tự chia sẻ.
- Với client có `close`/`shutdown`, test đóng context và quan sát callback/tài nguyên được giải phóng.
- Với tài nguyên externally owned, test rằng đóng context **không** đóng đối tượng ấy nếu đã tắt destroy callback.

### 14.5. Test thiếu dependency và cấu hình sai

Nếu factory method cần `Environment.getRequiredProperty(...)` mà property không tồn tại, lỗi khi bean được tạo là kỳ vọng hợp lý cho dependency bắt buộc. Nếu secret sai hoặc vendor client không kết nối được, đừng sửa bằng cách catch rộng rồi trả `null` từ `@Bean`; hãy phân loại lỗi cấu hình, lỗi mạng và chính sách retry theo từng thư viện.

## 15. Mẫu thiết kế cho các loại thư viện khác nhau

### 15.1. JSON serializer

`@Bean ObjectMapper` là đối tượng chia sẻ nếu cấu hình được hoàn tất trước khi sử dụng. Có thể đăng ký nhiều mapper cho các policy độc lập và tiêm bằng qualifier, như ví dụ chạy được.

### 15.2. HTTP client

Một HTTP client thường nên dùng lại connection/thread resources khi thư viện khuyến nghị. Tạo bằng builder từ timeout, TLS và endpoint configuration rồi đăng ký `@Bean`; lựa chọn scope và shutdown method theo tài liệu vendor. Không giả định mọi HTTP client đều giống nhau về thread safety và lifecycle.

### 15.3. DataSource/connection pool

Một `DataSource` có thể được tạo bằng factory/builder của thư viện pool và đăng ký qua `@Bean`. Cần cung cấp driver, URL, authentication và xác định rõ ai sở hữu pool. Trong Spring Boot, thường đã có auto-configuration cho `DataSource`; đừng đăng ký thủ công trùng với bean có sẵn nếu không có chủ ý.

### 15.4. SDK client bên thứ ba

Đăng ký qua `@Bean` cho phép tập trung endpoint, timeout, token provider và retry policy tại một chỗ. Nếu SDK có nhiều tài khoản/region, đặt tên từng bean và chọn bằng `@Qualifier`. Đừng nhét token vào constructor trong source; lấy qua secure configuration.

### 15.5. Test doubles

Có thể thay `@Bean` factory method để cấp fake/stub ở test context mà không chỉnh sửa code thư viện:

```java
@Configuration(proxyBeanMethods = false)
class TestClockConfig {
    @Bean
    Clock clock() {
        return Clock.fixed(
            Instant.parse("2030-01-01T00:00:00Z"),
            ZoneOffset.UTC
        );
    }
}
```

Đoạn này là một class cấu hình hợp lệ khi thêm import `Bean`, `Configuration`, `Clock`, `Instant`, `ZoneOffset`. Hãy nạp **một** cấu hình clock phù hợp cho test context thay vì vô tình tạo hai `Clock` cùng type và gây mơ hồ injection. Với các object không dễ fake, hãy thiết kế adapter/interface do ứng dụng sở hữu rồi đăng ký adapter dùng SDK qua `@Bean`.

## 16. Tự luyện và đáp án

**Câu 1.** Vì sao chỉ thêm `@ComponentScan(basePackages="com.fasterxml.jackson")` không phải cách thông thường để tạo `ObjectMapper`?

**Đáp:** Default component scanning tìm các class mang stereotype hoặc marker phù hợp; `ObjectMapper` không cần và không được thiết kế như Spring component. Khai báo `@Bean ObjectMapper ...` cho phép ứng dụng chủ động cấu hình instance.

**Câu 2.** Dự án không có `@ComponentScan` nhưng đã đăng ký `@Configuration` với factory method `@Bean`. Object có được Spring quản lý không?

**Đáp:** Có, nếu configuration được nạp/xử lý và bean được tạo thành công. Component scanning chỉ là một trong các đường đăng ký bean.

**Câu 3.** Trong demo có hai `ObjectMapper`. Vì sao factory method `reportService(@Qualifier("compactMapper") ObjectMapper mapper, ...)` không bị mơ hồ?

**Đáp:** Qualifier thu hẹp candidate theo bean có tên tương ứng; Spring đưa đúng mapper compact cho service.

**Câu 4.** Nếu bỏ `@Qualifier` trong demo, điều gì có thể xảy ra?

**Đáp:** Có hai mapper cùng type mà không có một ứng viên mặc định được phân định rõ, nên dependency injection có thể báo `NoUniqueBeanDefinitionException`. Không dựa vào tên biến để làm quy tắc cấu hình bắt buộc; dùng qualifier rõ ràng.

**Câu 5.** Hai lần `context.getBean("compactMapper")` có tạo hai mapper không?

**Đáp:** Không ở scope singleton mặc định; cùng tên bean trong cùng context trả cùng instance. Đổi scope sang prototype mới làm hai lookup thông thường tạo hai instance.

**Câu 6.** Vì sao `@Configuration(proxyBeanMethods=false)` nên đi cùng parameter injection trên `@Bean` methods?

**Đáp:** Vì gọi trực tiếp method `@Bean` khác là lời gọi Java thông thường, có thể tạo thêm instance ngoài bean đã đăng ký. Tham số method được container resolve theo tên/type/qualifier và scope.

**Câu 7.** Object thư viện có method `close()`. Có thể mặc định Spring được phép đóng nó khi context shutdown không?

**Đáp:** Cần xác định ownership. Spring có cơ chế suy luận destroy callback cho phương thức close/shutdown thích hợp, nhưng với object do môi trường khác quản lý có thể phải tắt qua `destroyMethod=""`. Không coi việc có `close()` là bằng chứng Spring sở hữu tài nguyên.

**Câu 8.** Vì sao một factory method khai báo return type là interface có thể làm một injection point theo concrete class khó resolve sớm?

**Đáp:** Spring dự đoán type từ metadata của factory method trước khi instance được tạo; nếu declared return type quá tổng quát, concrete type có thể chưa biết. Khai báo return type đủ cụ thể hoặc inject nhất quán theo interface.

**Câu 9.** Nếu muốn cấu hình một singleton vendor client khác trong test, có bắt buộc sửa source thư viện không?

**Đáp:** Không. Dùng Java configuration riêng của test để trả fake/đối tượng cấu hình khác, hoặc cấp bean phù hợp theo chính sách test context và tránh đăng ký trùng ngoài ý muốn.

**Câu 10.** `@Bean` có tự khiến method của client thư viện chạy trong transaction hay được cache không?

**Đáp:** Không. `@Bean` chỉ đăng ký/cấu hình object trong IoC Container. Transaction, caching và hành vi proxy cần hạ tầng, annotation, invocation path và dependency thích hợp riêng.

## 17. Nguồn tham khảo

- [Spring Framework 6.2 — Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java.html): tổng quan cách cấu hình bean bằng Java.
- [Spring Framework 6.2 — Basic Concepts: @Bean and @Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/basic-concepts.html): ý nghĩa bean factory methods và full/lite configuration.
- [Spring Framework 6.2 — Using the @Bean Annotation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html): tham số factory method, bean name, scope, lifecycle, init/destroy callbacks.
- [Spring Framework 6.2 — Using the @Configuration Annotation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/configuration-annotation.html): dependency giữa các bean, method interception và `proxyBeanMethods=false`.
- [Spring Framework 6.2 — Container Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies.html): cách container resolve dependency.
- [Jackson Databind — ObjectMapper API](https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-databind/2.18.2/com/fasterxml/jackson/databind/ObjectMapper.html): API mapper dùng trong bài.
- [Spring Framework — Bean Overview](https://docs.spring.io/spring-framework/reference/core/beans/definition.html): bean definition, metadata và cách Spring quản lý instance.

**Tóm tắt kiến thức cốt lõi:** Khi thư viện không có `@Component`, ta tạo bean definition bằng **`@Bean`** trên một configuration được Spring nạp. Factory method tạo/cấu hình object; IoC Container quản lý dependency, scope và lifecycle theo cấu hình. Component scanning dành cho việc tìm các class ứng viên có marker phù hợp, còn `@Bean` là công cụ lắp ghép chủ động các đối tượng thư viện mà ta không sửa source được.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-configuration-003">Tham số phương thức @Bean biểu diễn phụ thuộc</a>

<details>
<summary>Xem chi tiết</summary>

# Tham số phương thức `@Bean` biểu diễn phụ thuộc trong Spring Java Configuration

## 1. Mục tiêu: đưa quan hệ phụ thuộc vào chữ ký phương thức

Trong Spring Core Container, một phương thức gắn `@Bean` là **factory method** để đăng ký một object do Spring quản lý. Khi object mà phương thức tạo ra cần một bean khác, bạn có thể khai báo dependency trực tiếp bằng **tham số phương thức** thay vì tự tìm bean từ `ApplicationContext` hoặc gọi một phương thức `@Bean` khác.

Ví dụ đơn giản:

```java
@Configuration(proxyBeanMethods = false)
class AppConfig {

    @Bean
    Repository repository() {
        return new InMemoryRepository();
    }

    @Bean
    OrderService orderService(Repository repository) {
        return new OrderService(repository);
    }
}
```

Đây là đoạn **minh họa** (cần tự khai báo `Repository`, `InMemoryRepository`, `OrderService` và các import). Khi tạo bean `orderService`, Spring phân giải tham số `Repository repository` từ container rồi truyền instance vào factory method. Người viết cấu hình không phải gọi `repository()` hoặc `context.getBean(...)`.

Chữ ký `orderService(Repository repository)` nói rõ ba điều:

1. Muốn tạo `OrderService`, cần một dependency kiểu `Repository`.
2. Dependency được **Spring resolve** theo kiểu và những quy tắc autowire liên quan.
3. Factory method nhận dependency đã resolve, sau đó thực hiện mã Java thông thường: `new OrderService(repository)`.

Điều này giúp tách **khai báo quan hệ phụ thuộc** khỏi **logic xây object**, thuận lợi cho kiểm thử, refactor và dùng `@Configuration(proxyBeanMethods = false)`.

Mục tiêu học tập của bài:

- Hiểu thời điểm Spring phân giải tham số `@Bean` và khác biệt với tham số của method Java thông thường.
- Sử dụng dependency bắt buộc, `@Qualifier`, `@Primary`, `Optional<T>`, `ObjectProvider<T>` và collection injection đúng ngữ nghĩa.
- Phân biệt tham số `@Bean` với gọi trực tiếp method `@Bean` trong full/lite configuration.
- Xử lý thiếu bean, nhiều ứng viên, dependency cycle, lifecycle/scope, việc khởi tạo sớm.
- Biên dịch và chạy một ví dụ Java 17+ với Spring Framework 6, Gradle và stdout kiểm chứng.

## 2. Spring xử lý tham số `@Bean` như thế nào?

Để bean được tạo từ một phương thức `@Bean`, Spring đăng ký một **bean definition** có thông tin factory method. Khi container cần tạo bean đó, container chọn lời gọi factory thích hợp, xác định từng tham số và phân giải dependency từ BeanFactory.

```text
@Configuration / @Bean được xử lý
             |
             v
  Đăng ký bean definition
     cho factory method
             |
             v
   Khi cần tạo target bean
             |
             v
  Duyệt tham số của @Bean
   - Kiểu dữ liệu / generic
   - Qualifier / annotations
   - Optional / provider / tập hợp
             |
             v
    BeanFactory tìm ứng viên
       /          |          \
     Không có   Một bean    Nhiều bean
       |          |           |
 required?       chọn      Primary/Qualifier/
       |          |         quy tắc resolution
       v          v           v
   Lỗi hoặc   Gọi factory method với dependency đã resolve
   giá trị      |
   hợp lệ       v
             Object được tạo
                |
                v
        Spring quản lý lifecycle
```

Ví dụ `@Bean OrderService orderService(Repository repository)`:

- `Repository` là **dependency bắt buộc** nếu không được diễn đạt là tùy chọn.
- Spring tìm bean phù hợp kiểu `Repository`; nếu chỉ một ứng viên hợp lệ, factory method được gọi.
- Nếu không có bean và không có chính sách optional phù hợp, context thường báo `UnsatisfiedDependencyException` với nguyên nhân liên quan `NoSuchBeanDefinitionException`.
- Nếu có nhiều bean cùng kiểu không thể phân giải còn một ứng viên, thường gặp `NoUniqueBeanDefinitionException` trong chuỗi exception.
- Nếu factory method tự ném lỗi khi chạy, đó là **lỗi thực thi factory**; đừng nhầm với lỗi Spring không tìm được dependency.

**Không cần đặt `@Autowired` lên phương thức `@Bean` chỉ để tham số được inject.** Việc phân giải tham số là một phần của cơ chế tạo bean từ factory method. Trên một constructor Java thông thường, Spring cũng có constructor injection; tuy nhiên tham số `@Bean` thuộc **factory method được container gọi**, không phải constructor của class mà object được tạo.

### 2.1. Bean definition và việc gọi phương thức là hai giai đoạn

Đọc metadata `@Bean` không có nghĩa factory method đã chạy ngay tại thời điểm quét cấu hình. Mặc định, nhiều singleton không lazy được pre-instantiate khi `ApplicationContext` refresh; nếu target bean được khai báo `@Lazy` hoặc dùng scope khác, thời điểm gọi factory có thể khác.

Trong mọi trường hợp, Spring phải resolve tham số **trước khi factory method có thể chạy**. Một lỗi dependency có thể ngăn cả method body thực thi.

## 3. Dependency bắt buộc: truyền theo type

Với:

```java
@Bean
TaxCalculator taxCalculator() {
    return new TaxCalculator(10);
}

@Bean
CheckoutService checkoutService(TaxCalculator calculator) {
    return new CheckoutService(calculator);
}
```

Spring thấy `CheckoutService` được tạo từ `checkoutService(...)` có tham số kiểu `TaxCalculator`, nên phân giải bean phù hợp. Chữ ký diễn đạt quan hệ phụ thuộc. Không cần viết:

```java
// Không khuyến nghị làm mặc định:
@Bean
CheckoutService checkoutService() {
    return new CheckoutService(taxCalculator());
}
```

Hai đoạn đều là **mã minh họa riêng**, dùng class `TaxCalculator` và `CheckoutService` theo logic ứng dụng. Điểm khác nhau quan trọng:

- Với **tham số `@Bean`**, Spring phân giải dependency qua container.
- Với **lời gọi Java `taxCalculator()`**, hành vi phụ thuộc cấu hình class có được proxy hay không; trong chế độ `proxyBeanMethods = false`, đây chỉ là lời gọi Java thông thường và có thể tạo một instance ngoài cơ chế lookup singleton mong đợi.
- Chữ ký có tham số hoạt động tự nhiên ở cả full và lite configuration, không cần dựa vào interception của method call.

Một bản cấu hình sạch thường ưu tiên:

```java
@Configuration(proxyBeanMethods = false)
class CleanConfig {

    @Bean
    TaxCalculator taxCalculator() {
        return new TaxCalculator(10);
    }

    @Bean
    CheckoutService checkoutService(TaxCalculator calculator) {
        return new CheckoutService(calculator);
    }
}
```

Đây là trích đoạn **minh họa**, không phải file Java biên dịch độc lập khi chưa khai báo các kiểu.

### 3.1. Thứ tự khai báo method không phải thứ tự dependency

`@Bean CheckoutService checkoutService(TaxCalculator calculator)` có thể xuất hiện **trước** `@Bean TaxCalculator taxCalculator()` trong file. Spring xây dependency graph dựa trên bean definitions và method parameters. Việc thay đổi vị trí hai method không phải cách thiết kế thứ tự khởi tạo.

Nếu A phụ thuộc B, B cần được chuẩn bị để tạo A. Đó là quan hệ dependency. `@Order` dùng cho ordering trong các collection hoặc extension points nhất định, **không phải công cụ thay constructor/factory dependency** để bảo đảm thứ tự khởi tạo singleton.

## 4. Khi cùng kiểu có nhiều bean: `@Qualifier` và `@Primary`

Giả sử có hai implementation cho `FeePolicy`:

```java
@Bean
@Qualifier("standard")
FeePolicy standardFee() {
    return new FixedFeePolicy("standard", 5);
}

@Bean
@Qualifier("express")
FeePolicy expressFee() {
    return new FixedFeePolicy("express", 15);
}
```

Cả hai method trả về cùng interface `FeePolicy`. Một method:

```java
@Bean
CheckoutService checkoutService(FeePolicy policy) {
    return new CheckoutService(policy);
}
```

có thể gặp lỗi ambiguous dependency nếu Spring không tìm được một ứng viên thắng. Cần xác định **đúng vai trò** của dependency:

```java
@Bean
CheckoutService checkoutService(
        @Qualifier("standard") FeePolicy policy) {
    return new CheckoutService(policy);
}
```

Các đoạn này **chỉ minh họa**: kiểu `FeePolicy` và `FixedFeePolicy` được định nghĩa trong ví dụ hoàn chỉnh ở mục 8. `@Qualifier` ở vị trí tham số là điều kiện lọc: Spring tìm các candidate vừa khớp kiểu, vừa khớp qualifier. Qualifier có thể gắn trên bean factory method và trên tham số cần tiêm; một qualifier không tự tạo được bean sai kiểu.

**Điểm cần nhớ:** tên method `standardFee` và nhãn qualifier `standard` khác nhau; không nên coi hai khái niệm đó là một. Nếu đã có bean name phù hợp, Spring có thể dùng bean name làm fallback qualifier; nhưng dùng annotation `@Qualifier("standard")` ở cả nơi khai báo bean và nơi dùng sẽ thể hiện vai trò nghiệp vụ rõ hơn.

### 4.1. `@Primary` là chính sách mặc định

Có thể gắn:

```java
@Bean
@Primary
FeePolicy standardFee() {
    return new FixedFeePolicy("standard", 5);
}
```

Khi caller tiêm một `FeePolicy` không qualifier và còn nhiều candidate cùng kiểu, `@Primary` ưu tiên bean này nếu nó là primary duy nhất trong tập ứng viên. Với `@Qualifier("express") FeePolicy policy`, Spring vẫn có thể chọn bean express nếu chỉ express khớp qualifier; primary của bean standard không cho phép nó vượt qua qualifier không khớp.

Đánh dấu **hai primary cùng được xét** không tự giải quyết ambiguity. Trên method `@Bean` có một tham số yêu cầu đơn, hãy dùng qualifier khi trách nhiệm rõ, primary cho default thực sự hợp lý.

### 4.2. `@Bean` return type ảnh hưởng khả năng phát hiện ứng viên

Spring dùng thông tin kiểu của bean definition, bao gồm kiểu trả về được khai báo ở method `@Bean`, để phân giải autowiring. Vì vậy khai báo kiểu trả về **đủ cụ thể** và đúng contract:

```java
@Bean
PaymentClient paymentClient() {
    return new DefaultPaymentClient();
}
```

Nếu factory method trả kiểu quá tổng quát như `Object`, một điểm tiêm yêu cầu `PaymentClient` có thể không được Spring nhận diện đầy đủ từ metadata type trước khi target bean được instantiate. Trong nhiều tình huống nên khai báo interface/abstract type có ích cho consumer, hoặc concrete class nếu caller thật sự cần API đó. Đừng dựa vào type thực tế của object được trả về để thay thế hoàn toàn bean definition type metadata.

Đây là đoạn minh họa, cần tự định nghĩa `PaymentClient` và `DefaultPaymentClient`.

## 5. Các tham số không bắt buộc: `Optional` và `ObjectProvider`

Không phải mọi dependency đều cần hiện diện. Một dịch vụ thanh toán có thể gửi audit nếu có module audit, và vẫn hoạt động nếu audit bị tắt.

### 5.1. `Optional<T>`: quyết định tại lúc tạo bean

```java
@Bean
ReportService reportService(Optional<AuditSink> auditSink) {
    return new ReportService(auditSink);
}
```

Với `java.util.Optional`, Spring có thể inject `Optional.empty()` khi không có ứng viên. Nếu có đúng một `AuditSink` được resolve, giá trị là `Optional.of(bean)`. Nếu nhiều bean `AuditSink` không phân giải được, optionality không nên được hiểu thành quy tắc âm thầm chọn một bean ngẫu nhiên; vẫn có thể lỗi ambiguity.

`Optional<T>` phù hợp khi factory method muốn quyết định một lần liệu dependency có sẵn khi tạo bean hay không. Ví dụ `ReportService` có thể giữ `Optional<AuditSink>` như trạng thái cấu hình tại thời điểm xây service.

**Lưu ý:** Trong một singleton, việc inject `Optional<T>` không tự làm service “quan sát” thay đổi bean registry về sau.

### 5.2. `ObjectProvider<T>`: resolve khi cần

```java
@Bean
ReportService reportService(ObjectProvider<AuditSink> auditProvider) {
    return new ReportService(auditProvider);
}
```

Spring có thể inject provider mà chưa cần tìm một `AuditSink` cụ thể ngay tại factory method của `ReportService`. Code nghiệp vụ sau đó chọn cách xử lý:

```java
AuditSink required = auditProvider.getObject();
AuditSink optional = auditProvider.getIfAvailable();
AuditSink unique = auditProvider.getIfUnique();
```

Đây là đoạn minh họa dùng biến `auditProvider` đã được cung cấp; **không độc lập** nếu thiếu class `AuditSink` và nơi khai báo biến.

| Phương thức | Không có bean | Có một ứng viên thắng | Nhiều ứng viên không phân giải được |
| --- | --- | --- | --- |
| `getObject()` | Ném `NoSuchBeanDefinitionException` | Trả bean | Ném `NoUniqueBeanDefinitionException` |
| `getIfAvailable()` | `null` | Trả bean | Ném `NoUniqueBeanDefinitionException` |
| `getIfUnique()` | `null` | Trả bean | `null` |

Nếu có nhiều bean nhưng một bean `@Primary` phù hợp được Spring chọn làm ứng viên thắng, cả ba method có thể trả bean đó. Nếu nghiệp vụ cần lazy lookup hoặc xử lý optional dependency tại thời điểm sử dụng, provider giúp diễn đạt điều đó.

**Không đánh đồng provider với lazy singleton:** non-lazy singleton `AuditSink` khác có thể đã được Spring tạo trong lúc context refresh. Provider chỉ làm thời điểm **lookup từ consumer** linh hoạt; muốn chủ động trì hoãn khởi tạo bean cần xét thêm `@Lazy` và dependency graph.

### 5.3. Dùng `@Nullable` và null có chủ đích

Spring hỗ trợ các cách biểu diễn optional dependency khác tùy annotation nullability và phiên bản. Trong Spring Framework 6.2, `@Nullable` được hỗ trợ ở injection points theo cơ chế phù hợp. Khi viết Java 17, `Optional<T>` hoặc `ObjectProvider<T>` thường diễn đạt ý định rõ hơn một tham số có thể `null` mà method body quên kiểm tra.

Không tự đánh dấu mọi dependency `@Nullable` chỉ để context startup thành công: với dependency bắt buộc, lỗi thiếu bean là một tín hiệu cấu hình quan trọng.

## 6. Tham số collection: một factory method cần cả nhóm bean

Một method có thể nhận nhiều bean cùng kiểu:

```java
@Bean
Pipeline pipeline(List<Handler> handlers) {
    return new Pipeline(handlers);
}
```

Spring tập hợp các beans phù hợp với `Handler` vào một `List<Handler>`; nếu có order metadata, thứ tự list thường được xác định bởi `Ordered`, `@Order` hoặc `@Priority` phù hợp. Method body không cần tự gọi `getBeansOfType` hoặc lặp tên bean.

Cũng có thể dùng:

- `Handler[]`: mảng các bean phù hợp;
- `Set<Handler>`: nhóm bean với semantics set, không nên dựa vào iteration order như một thứ tự pipeline bắt buộc;
- `Map<String, Handler>`: map có **bean name** làm key, bean làm value;
- `@Qualifier("fraud") List<Handler>`: tập các bean kiểu Handler phù hợp qualifier `fraud`.

**`@Primary` không lọc số phần tử của collection**; primary chỉ hỗ trợ lựa chọn một bean khi dependency đơn cần phân giải. Khi một factory method nhận `List<Handler>`, nó nói rằng bean mới cần **cả nhóm handler** chứ không phải một default handler.

### 6.1. Khi collection không có thành viên

Đừng giả định mọi factory method nhận `List<T>` sẽ tự nhận một `List.of()` khi không có bean. Tại một dependency injection point bắt buộc, Spring có thể coi việc không tìm được bean phù hợp là lỗi. Nếu “không có handler” là một trường hợp nghiệp vụ bình thường, hãy thiết kế tùy chọn rõ ràng (chẳng hạn sử dụng `ObjectProvider<Handler>` và `orderedStream()`, rồi tự tạo danh sách) và kiểm thử hành vi của phiên bản Spring đang dùng.

### 6.2. Phân biệt multi-bean collection và collection bean có sẵn

`@Bean Pipeline pipeline(List<Handler> handlers)` trong ví dụ điển hình yêu cầu Spring **thu thập các bean Handler**. Trong trường hợp ứng dụng còn tự khai báo một bean mà bản thân nó có kiểu `List<Handler>`, cơ chế lựa chọn collection bean có thể khác: bạn cần xác định rõ muốn tiêm **một bean kiểu List** hay muốn **nhiều phần tử Handler**. Hãy thiết kế tên, qualifier và generic type đủ rõ, và kiểm thử context thực khi cả hai cách cấu hình cùng tồn tại.

## 7. Tham số factory method so với gọi `@Bean` method trực tiếp

Đây là một trong những khác biệt quan trọng nhất của Java-based configuration.

### 7.1. Full `@Configuration` và `proxyBeanMethods=true`

Với cấu hình thông thường:

```java
@Configuration
class FullConfig {

    @Bean
    Engine engine() {
        return new Engine();
    }

    @Bean
    Car car() {
        return new Car(engine());
    }
}
```

Đây là **đoạn minh họa** cần class `Engine` và `Car`. `@Configuration` mặc định `proxyBeanMethods=true` cho phép Spring enhance configuration class (thường bằng CGLIB) để **chặn các lời gọi qua lại giữa `@Bean` methods**, bảo đảm theo container semantics, ví dụ trả singleton đã đăng ký thay vì `new Engine()` tùy tiện khi gọi qua proxy.

Cơ chế này có ràng buộc đối với việc enhance class và method, và làm mã cấu hình phụ thuộc vào proxy semantics. Vì vậy, ngay cả với full `@Configuration`, truyền dependency qua tham số thường dễ hiểu hơn.

### 7.2. `proxyBeanMethods=false` và lite configuration

Với:

```java
@Configuration(proxyBeanMethods = false)
class LiteConfig {

    @Bean
    Engine engine() {
        return new Engine();
    }

    @Bean
    Car car() {
        return new Car(engine());
    }
}
```

Lời gọi `engine()` trong `car()` là **lời gọi Java thông thường**, có thể tạo một `Engine` mới ngoài instance singleton mà BeanFactory quản lý. `@Bean` method vẫn được Spring xử lý để đăng ký bean, nhưng các lời gọi trực tiếp qua method không được proxy intercept.

Cách viết rõ nghĩa:

```java
@Configuration(proxyBeanMethods = false)
class ParameterConfig {

    @Bean
    Engine engine() {
        return new Engine();
    }

    @Bean
    Car car(Engine engine) {
        return new Car(engine);
    }
}
```

Đây là ba mẫu minh họa **tách biệt**, không ghép cùng một file/class mà không định nghĩa `Engine`/`Car`. Với `ParameterConfig`, dependency của `car` được lấy từ container; không cần engine method tự gọi lại.

### 7.3. Có được gọi method `@Bean` trực tiếp trong Java?

Bạn *có thể* gọi như một method Java thông thường, nhưng hành vi khi ấy phụ thuộc cách class cấu hình được tạo và proxy mode. Đặc biệt, gọi method trên một instance cấu hình tự `new` ngoài Spring không giúp Spring inject tham số và không biến object trả về thành managed bean. Vì vậy đừng coi `@Bean` là một “magic factory” hoạt động theo container semantics trong mọi lời gọi Java.

**Quy tắc thực hành:** khi bean A cần bean B, viết `@Bean A a(B b)`. Đừng dùng `@Bean A a() { return new A(b()); }` như mặc định nếu đã có khả năng truyền B qua tham số.

## 8. Ví dụ đầy đủ Java 17+ chạy được với Spring Context 6

Dự án sau thể hiện năm tính chất:

1. Tham số bắt buộc được resolve từ bean `PriceCalculator`.
2. `@Qualifier("standard")` chọn đúng một `FeePolicy` khi có hai bean cùng kiểu.
3. `ObjectProvider<AuditSink>` biểu diễn khả năng audit tùy chọn.
4. `Optional<AuditSink>` trả giá trị vắng mặt hợp lệ.
5. `List<FeePolicy>` nhận hai policies theo thứ tự `@Order` ở factory methods.

Ví dụ không cần Spring Boot, HTTP, database hoặc cấu hình ngoài. `@Configuration(proxyBeanMethods=false)` được chọn có chủ đích để chứng minh **tham số method là đủ để diễn đạt dependency**.

### 8.1. Cấu trúc dự án

```text
bean-method-parameters-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/
    └── BeanMethodParametersDemo.java
```

`settings.gradle`:

```groovy
rootProject.name = 'bean-method-parameters-demo'
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
    mainClass = 'BeanMethodParametersDemo'
}
```

Dùng JDK 17+ và Gradle tương thích với JDK đang chạy. `spring-context` đem theo các module Spring cần cho `AnnotationConfigApplicationContext`, `@Bean`, `@Qualifier`, `ObjectProvider` và collection autowiring.

### 8.2. Mã nguồn hoàn chỉnh

File `src/main/java/BeanMethodParametersDemo.java`:

```java
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

public class BeanMethodParametersDemo {

    record FeePolicy(String name, int surcharge) {
    }

    record PriceCalculator(int taxPercent) {
        int total(int amount, FeePolicy fee) {
            return amount + amount * taxPercent / 100 + fee.surcharge();
        }
    }

    interface AuditSink {
        void record(int total);
    }

    record CheckoutService(
            PriceCalculator calculator,
            FeePolicy selected,
            ObjectProvider<AuditSink> auditProvider) {

        int quote(int amount) {
            int total = calculator.total(amount, selected);
            auditProvider.ifAvailable(sink -> sink.record(total));
            return total;
        }
    }

    record FeeCatalog(
            List<FeePolicy> all,
            Optional<AuditSink> audit) {

        List<String> names() {
            return all.stream().map(FeePolicy::name).toList();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Config {

        @Bean
        PriceCalculator priceCalculator() {
            return new PriceCalculator(10);
        }

        @Bean
        @Order(10)
        @Qualifier("standard")
        FeePolicy standardFee() {
            return new FeePolicy("standard", 5);
        }

        @Bean
        @Order(20)
        @Qualifier("express")
        FeePolicy expressFee() {
            return new FeePolicy("express", 15);
        }

        @Bean
        CheckoutService checkoutService(
                PriceCalculator calculator,
                @Qualifier("standard") FeePolicy selected,
                ObjectProvider<AuditSink> auditProvider) {
            return new CheckoutService(calculator, selected, auditProvider);
        }

        @Bean
        FeeCatalog feeCatalog(
                List<FeePolicy> allFees,
                Optional<AuditSink> audit) {
            return new FeeCatalog(List.copyOf(allFees), audit);
        }
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(Config.class)) {
            var catalog = context.getBean(FeeCatalog.class);
            var checkout = context.getBean(CheckoutService.class);

            System.out.println("policies = " + catalog.names());
            System.out.println("selected = " + checkout.selected().name());
            System.out.println("quote100 = " + checkout.quote(100));
            System.out.println("optionalAudit = " +
                    catalog.audit().isPresent());
            System.out.println("providerAudit = " +
                    (checkout.auditProvider().getIfAvailable() != null));
            System.out.println("sameCheckout = " +
                    (checkout == context.getBean(CheckoutService.class)));
        }
    }
}
```

### 8.3. Lệnh chạy

Trong thư mục `bean-method-parameters-demo`:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper:

```bash
./gradlew run --quiet
```

Trên Windows PowerShell, thay bằng `.\gradlew.bat run --quiet`.

Stdout do những lệnh `System.out.println` trong code tạo ra:

```text
policies = [standard, express]
selected = standard
quote100 = 115
optionalAudit = false
providerAudit = false
sameCheckout = true
```

Các thông báo của Gradle hoặc logging từ môi trường (nếu có cấu hình thêm) không thuộc stdout nghiệp vụ trên.

### 8.4. Phân tích từng kết quả

**`policies = [standard, express]`**: `feeCatalog(List<FeePolicy> allFees, ...)` nhận cả hai bean `FeePolicy`; hai method factory có `@Order(10)` và `@Order(20)` nên danh sách có thứ tự tương ứng. Hai bean được tạo độc lập nhưng cùng interface/type `FeePolicy`.

**`selected = standard`**: tham số `@Qualifier("standard") FeePolicy selected` của method `checkoutService(...)` lọc đúng policy mang nhãn `standard`. Nếu bỏ qualifier mà vẫn có hai candidates ngang nhau, method có thể thất bại khi Spring cố phân giải một `FeePolicy`.

**`quote100 = 115`**: `PriceCalculator(10)` tính thuế 10% của 100 là 10; fee chuẩn là 5; tổng `100 + 10 + 5 = 115`. `auditProvider.ifAvailable(...)` không thực thi consumer vì context không khai báo bean `AuditSink`.

**`optionalAudit = false`**: `feeCatalog(...)` nhận `Optional.empty()`; việc thiếu `AuditSink` là một phần hợp lệ của thiết kế.

**`providerAudit = false`**: provider được inject thành công, nhưng `getIfAvailable()` tại lúc gọi cho `null` do chưa có AuditSink bean.

**`sameCheckout = true`**: `CheckoutService` do `@Bean` tạo với singleton scope mặc định; hai lần `getBean(...)` nhận cùng instance trong một context.

**Bằng chứng cốt lõi:** `checkoutService(...)` không hề gọi `priceCalculator()` hoặc `standardFee()`, và `feeCatalog(...)` không tự truy vấn bean registry. Spring resolve các tham số như một phần của quá trình gọi factory.

### 8.5. Thử thay đổi và quan sát

**Thử 1 — Thêm audit bean.** Bổ sung trong `Config`:

```java
@Bean
AuditSink auditSink() {
    return total -> System.out.println("audit = " + total);
}
```

Đây là **đoạn thêm vào ví dụ mục 8**, không phải file độc lập. Lúc này `Optional<AuditSink>` có giá trị và provider cũng phân giải được AuditSink; `checkout.quote(100)` sẽ in thêm `audit = 115` từ consumer trước dòng `quote100 = 115` của main.

**Thử 2 — Gỡ `@Qualifier("standard")` ở tham số đơn.** Spring có hai bean `FeePolicy` nhưng không có primary. Khi refresh context, Spring có thể không chọn được một ứng viên thắng và báo ambiguity. Đừng dựa vào việc tham số có tên `selected` để hy vọng chọn một trong hai.

**Thử 3 — Gắn `@Primary` lên `standardFee()`.** Với tham số đơn `FeePolicy selected` không qualifier, Spring có thể chọn standard làm default nếu là primary duy nhất. Nhưng `List<FeePolicy>` của `FeeCatalog` **vẫn chứa cả standard và express**.

**Thử 4 — Thay `@Order`.** Đổi `@Order(10)` của standard thành `@Order(30)`. Với order express là 20, list ở `FeeCatalog` đổi thành `[express, standard]`; qualifier chọn standard cho `CheckoutService` vẫn giữ nguyên. Điều này cho thấy **lọc bean** và **sắp xếp collection** là hai quyết định khác nhau.

## 9. Chu kỳ phụ thuộc và lỗi khởi tạo

Tham số factory method diễn đạt dependency graph. Nếu có vòng A → B → A, Spring không thể tự tạo hai singleton dạng constructor/factory dependencies theo cách tuyến tính bình thường.

Ví dụ cố ý sai:

```java
@Configuration(proxyBeanMethods = false)
class CyclicConfig {

    @Bean
    A a(B b) {
        return new A(b);
    }

    @Bean
    B b(A a) {
        return new B(a);
    }
}
```

Đây là **trích đoạn mô tả lỗi**, cần class `A` và `B`. Khi container cố tạo A, nó cần B; tạo B lại cần A. Với kiểu dependency cứng này, Spring thường báo circular reference / bean currently in creation, có thể được bọc trong `UnsatisfiedDependencyException` hoặc `BeanCreationException` tùy đường đi.

**Giải pháp ưu tiên là sửa thiết kế:** tách trách nhiệm, đảo chiều phụ thuộc hoặc trích xuất interface/event/collaborator không tạo vòng. `ObjectProvider<T>` hoặc `@Lazy` có thể trì hoãn một liên kết trong một số trường hợp, nhưng không bảo đảm vòng logic được giải quyết an toàn; nếu method gọi nhau vô hạn lúc runtime, trì hoãn injection cũng không chữa được.

### 9.1. Thiếu bean do chưa đăng ký configuration

Đặt `@Bean` trong một class chưa được Spring đăng ký/import/scan thì bean definition không tự xuất hiện. Khi method A yêu cầu tham số B, Spring chỉ có thể resolve B từ **container hiện tại** (bao gồm nguồn đăng ký mà context nhìn thấy), không phải từ mọi class Java trong project.

Nếu thiếu dependency, kiểm tra:

- Class configuration đã được đưa vào `AnnotationConfigApplicationContext`, `@Import` hoặc scan chưa?
- Method `@Bean` có được Spring xử lý không, hay chỉ là Java method không thuộc container?
- Có `@Profile`, `@Conditional` hoặc filter loại bean không?
- Kiểu khai báo `@Bean` có khớp kiểu tham số yêu cầu không?
- Có nhiều beans cùng type và thiếu qualifier không?
- Dependency có nằm trên runtime classpath và khả dụng lúc refresh không?

### 9.2. Lỗi trong method body

Spring có thể resolve tất cả tham số thành công nhưng method vẫn ném `IllegalStateException`, gọi API mạng lỗi hoặc trả về object không đáp ứng kỳ vọng. Thông báo lúc đó có thể là `BeanCreationException` liên quan factory method. Để debug, đọc **root cause** và phân biệt “argument resolution failed” với “factory execution failed”.

Không nên bắt exception rộng trong `@Bean` rồi trả `null` để che lỗi: container có những semantics riêng khi factory method trả `null` và caller có thể nhận kết quả rất khác mong đợi. Với bean bắt buộc, nên báo lỗi cấu hình rõ ràng.

## 10. Scope, lazy và thứ tự tạo bean

### 10.1. Singleton không có nghĩa một lần gọi Java bất kỳ

`@Bean` mặc định tạo singleton do container quản lý. Nếu method A có tham số B, container truyền instance B phù hợp. Không đồng nghĩa mỗi lời gọi tự phát đến `config.b()` trong mọi proxy mode đều trả singleton; đó là lý do parameter injection là lối viết đáng tin cậy.

### 10.2. Prototype được resolve tại thời điểm tạo consumer

Nếu bean B có `@Scope("prototype")` và singleton A nhận B như **tham số factory method**, Spring sẽ tạo một prototype instance khi tạo A; A thường giữ chính reference đó về sau, không tự nhận prototype mới ở mỗi business method call.

Muốn mỗi lần sử dụng lấy prototype mới, nhận `ObjectProvider<B>` làm dependency và gọi `getObject()` mỗi lần cần, với scope/contract phù hợp. Đây là chính sách lifecycle, không phải chỉ là cú pháp đặt tham số.

### 10.3. `@Lazy` không thay thế dependency graph

Gắn `@Lazy` lên bean definition có thể hoãn khởi tạo cho đến khi cần. Tuy nhiên, nếu một singleton khác đang được khởi tạo bắt buộc nhận bean lazy **làm tham số `@Bean`**, việc resolve dependency vẫn có thể kích hoạt tạo bean lazy ngay. Để trì hoãn lookup từ consumer, `ObjectProvider<T>` hoặc proxy phù hợp cần được xem xét riêng.

### 10.4. `@DependsOn` dùng cho phụ thuộc thứ tự không biểu diễn qua tham số

Nếu A cần B để xây object, hãy viết `@Bean A a(B b)`. Nếu A không cần reference B nhưng muốn B được khởi tạo trước để chuẩn bị side effect có chủ đích, `@DependsOn("b")` có thể biểu đạt yêu cầu thứ tự này.

```java
@Bean
@DependsOn("migrationRunner")
ReportCache reportCache() {
    return new ReportCache();
}
```

Đây là đoạn minh họa cần bean tên `migrationRunner`, class `ReportCache` và imports. `@DependsOn` dựa vào **bean name**, không phải type; không thay thế constructor/factory parameter injection cho dependency object thực sự.

## 11. So sánh với các cách khác để biểu diễn dependency

| Cách | Dependency được khai báo ở đâu? | Khi nên chọn |
| --- | --- | --- |
| Constructor injection trên `@Component`/`@Service` | Constructor của class | Class thuộc codebase và có thể đặt stereotype; dependency bắt buộc thuộc bản thân object |
| Method parameter của `@Bean` | Chữ ký factory method trong Java configuration | Muốn tạo object bằng factory, tích hợp class thư viện hoặc kiểm soát construction |
| Method `@Bean` tự gọi method `@Bean` khác | Method body | Có thể hợp lệ với full config, nhưng dễ phụ thuộc proxy semantics |
| `ApplicationContext.getBean(...)` trực tiếp | Code imperative lookup | Infrastructure code thật sự cần lookup động; tránh service locator trong business code |
| `@DependsOn` | Metadata tên bean | Cần thứ tự khởi tạo cho side effect, không có object parameter trực tiếp |
| `ObjectProvider<T>` được tiêm | Factory method/constructor nhận provider | Target có thể vắng mặt, cần lookup muộn hoặc cần scope động |

Không có một cách bắt buộc cho tất cả ứng dụng. Nhưng khi dùng `@Bean` factory method, **khai báo tham số là cách diễn đạt dependency tự nhiên**: rõ contract, tránh lấy bean theo string name và ít phụ thuộc khả năng CGLIB interception của configuration class.

## 12. Lỗi thường gặp và cách sửa

| Hiện tượng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| `UnsatisfiedDependencyException` lúc tạo bean từ `@Bean` | Thiếu bean của tham số bắt buộc | Đăng ký/import configuration cung cấp bean; kiểm tra type, profile, condition |
| `NoUniqueBeanDefinitionException` | Nhiều candidate cùng kiểu không có ứng viên thắng | Gắn `@Qualifier` tại tham số, chỉ định `@Primary` phù hợp hoặc thay thiết kế |
| Một bean có `@Primary` nhưng qualifier vẫn chọn bean khác | Qualifier lọc tập candidate trước khi xét ưu tiên | Kiểm tra qualifier ở method khai báo và tham số |
| Method được `@Bean` gọi trực tiếp trả object khác singleton mong đợi | Dùng `proxyBeanMethods=false` hoặc gọi class config ngoài Spring | Dùng tham số `@Bean` thay vì direct call |
| Gắn `@Autowired` cho `@Bean` vì tưởng bắt buộc | Hiểu sai factory method argument resolution | Bỏ annotation dư; Spring resolve tham số `@Bean` tự động |
| Bean ở context không thấy method khác | Configuration chưa được xử lý, nằm ngoài scan/import | Đăng ký configuration vào đúng context |
| Bật `@Lazy` nhưng bean vẫn tạo sớm | Bean khác cần dependency bắt buộc khi khởi tạo | Dùng provider/proxy thích hợp hoặc điều chỉnh dependency graph |
| Prototype không được tạo mới cho mỗi method business call | Prototype chỉ resolve một lần khi singleton được tạo | Inject `ObjectProvider<Prototype>` và lookup khi cần |
| Dùng `@Order` để “bắt bean B init trước A” | `@Order` không biểu đạt dependency khởi tạo singleton | Khai báo `@Bean A a(B b)` hoặc `@DependsOn` cho side effect |
| `@Bean` method nhận `List<T>` nhưng không có phần tử | Dependency bắt buộc không được thỏa | Dùng `ObjectProvider<T>`/stream khi hợp đồng cho phép rỗng |
| Dùng `Optional<T>` và vẫn thấy ambiguity | Optional cho phép thiếu, không chọn ngẫu nhiên từ nhiều bean | Qualifier, primary hoặc xử lý chính sách nhiều ứng viên |
| `ObjectProvider<T>` có mặt nhưng `getObject()` lỗi | Provider chỉ trì hoãn việc phân giải target | Chọn `getIfAvailable()`/`getIfUnique()` nếu chính sách cho phép; sửa cấu hình nếu bắt buộc |
| Cấu hình hai bean tạo vòng dependency | Factory parameter A→B→A | Refactor dependency graph; không dùng proxy để che lỗi logic |
| Factory parameter không resolve theo concrete class | Bean type metadata quá tổng quát (`Object`) hoặc generic không khớp | Dùng return type hợp đồng rõ, kiểm tra `ResolvableType` khi cần |
| Viết `@Bean` rồi gọi method trên `new Config()` | Gọi Java object ngoài Spring | Lấy managed bean từ `ApplicationContext`/inject dependency vào bean cần dùng |

**Kỹ thuật debug hữu ích:** tập trung vào method `@Bean` nào đang được Spring gọi, tham số nào bị lỗi và các bean names/candidate types có trong context. Việc xem riêng method body chưa đủ nếu Spring không thể resolve argument.

## 13. Bài tập thực hành và đáp án

### Bài 1 — Loại bỏ gọi trực tiếp method `@Bean`

Cho:

```java
@Configuration(proxyBeanMethods = false)
class OldConfig {
    @Bean
    ReportRepository repository() {
        return new ReportRepository();
    }

    @Bean
    ReportService service() {
        return new ReportService(repository());
    }
}
```

Viết lại để `ReportService` dùng repository do Spring quản lý.

**Đáp án:**

```java
@Bean
ReportService service(ReportRepository repository) {
    return new ReportService(repository);
}
```

Đây là hai **trích đoạn minh họa**, dùng class tự định nghĩa. Spring resolve repository theo type trong lúc tạo service; method không cần gọi `repository()` trực tiếp.

### Bài 2 — Hai candidate cùng interface

Trong cấu hình có `@Bean @Qualifier("email") Notifier email()` và `@Bean @Qualifier("sms") Notifier sms()`. Bạn muốn method `@Bean AlertManager manager(...)` lấy SMS.

**Đáp án:** khai báo `@Bean AlertManager manager(@Qualifier("sms") Notifier notifier)`, sau đó gọi constructor với `notifier`. Không dựa vào thứ tự method hoặc tự tạo `new SmsNotifier()`.

### Bài 3 — Mở rộng demo với audit

Thêm `@Bean AuditSink auditSink()` như ở mục 8.5. Dự đoán:

- `catalog.audit().isPresent()` trở thành `true`;
- `checkout.auditProvider().getIfAvailable() != null` thành `true`;
- lời gọi `checkout.quote(100)` kích hoạt consumer audit với tổng 115.

Việc truyền `Optional` và provider ở factory method **không cần đổi**.

### Bài 4 — Đổi vị trí hai factory methods

Đưa `checkoutService(...)` lên đầu class `Config` trước `priceCalculator()`. Có làm Spring mất bean calculator không?

**Đáp án:** Không, miễn cả hai `@Bean` được xử lý và dependency graph không có lỗi. Spring resolve theo bean definitions, không theo thứ tự text trong file.

### Bài 5 — Default policy và collection

Gắn `@Primary` vào bean standard nhưng giữ express. Nếu tạo:

```java
@Bean
FeeCatalog feeCatalog(List<FeePolicy> policies) {
    return new FeeCatalog(policies, Optional.empty());
}
```

Đây là **biến thể minh họa** của demo (nếu thay method `feeCatalog`). Danh sách có bao nhiêu phần tử?

**Đáp án:** Cả hai FeePolicy; primary không lọc collection. Có thể sắp thứ tự bằng `@Order` như cũ.

### Bài 6 — Singleton cần prototype

`@Bean A a(B prototypeB)` inject một instance B vào singleton A. Nếu B có scope prototype, mỗi lần gọi `a.run()` có tự nhận B mới không?

**Đáp án:** Không. A giữ B đã được resolve khi A được tạo. Muốn B mới theo mỗi lần sử dụng, thiết kế A nhận `ObjectProvider<B>` rồi lookup.

### Bài 7 — Vòng A/B

Hai factory methods `A a(B b)` và `B b(A a)` đều tạo singleton và không có cơ chế trì hoãn nào. Điều gì xảy ra?

**Đáp án:** Container không thể hoàn thành cả hai dependency cứng; thường thất bại với circular creation/unsatisfied dependency. Cần refactor graph hoặc thay quan hệ đồng bộ bằng thiết kế phù hợp.

### Bài 8 — Phân biệt missing dependency và factory error

Method:

```java
@Bean
ReportService reportService(Repository repository) {
    throw new IllegalStateException("broken factory");
}
```

Nếu Spring có repository hợp lệ, lỗi khởi tạo xảy ra ở đâu?

**Đáp án:** Spring có thể resolve tham số thành công, rồi method body ném `IllegalStateException`. Đây là lỗi factory execution, không phải thiếu bean `Repository`.

### Bài 9 — Optional không có nghĩa chọn tùy ý

Có hai bean `AuditSink` ngang nhau nhưng method nhận `Optional<AuditSink>`. Có chắc nhận `Optional.empty()` không?

**Đáp án:** Không. `Optional.empty()` diễn đạt **vắng mặt dependency**, không phải chính sách bỏ qua ambiguity của nhiều candidate. Hãy lọc bằng qualifier hoặc xem xét một chính sách selection phù hợp.

### Bài 10 — Chọn cách mô tả quan hệ phụ thuộc

Một thư viện cung cấp class `HttpClientAdapter` không mang stereotype; ứng dụng tạo class này bằng `@Bean` và nó cần bean `CredentialsProvider`. Nên tự gọi `context.getBean(CredentialsProvider.class)` trong method hay thêm tham số `CredentialsProvider credentials`?

**Đáp án:** Thêm tham số method `@Bean HttpClientAdapter adapter(CredentialsProvider credentials)`. Cách đó rõ dependency và dễ kiểm thử mà không phụ thuộc Service Locator.

## 14. Checklist khi viết phương thức `@Bean`

- Bean cần tạo có phụ thuộc object nào? Đưa **dependency bắt buộc** vào tham số factory method.
- Có nhiều ứng viên cùng type không? Dùng `@Qualifier` khi vai trò cụ thể, `@Primary` khi cần default.
- Có dependency tùy chọn không? Dùng `Optional<T>` hoặc `ObjectProvider<T>` theo thời điểm resolve và chính sách nghiệp vụ.
- Bean cần cả nhóm implementation không? Dùng `List<T>`/mảng hoặc provider stream; đừng gắn primary rồi mong chỉ còn một phần tử.
- Có đang gọi một `@Bean` method khác trực tiếp không? Cân nhắc thay bằng tham số để không phụ thuộc proxy mode.
- Return type của `@Bean` đã đủ rõ để by-type resolution chưa?
- Có cycle A→B→A, hoặc factory method có side effect/IO gây startup failure không?
- Scope và lazy policy có đúng với thời điểm tạo và dùng object không?
- Class configuration có thật sự được container đăng ký/import/scan không?
- Đã kiểm chứng bằng một `ApplicationContext` nhỏ và kiểm tra kết quả injection chưa?

## 15. Ghi nhớ và nguồn đọc

- **Tham số `@Bean` là dependency được Spring resolve** ngay trước lúc container gọi factory method.
- **Không bắt buộc `@Autowired` trên `@Bean` method** để inject tham số.
- **Dependency đơn** được chọn theo type, qualifier, primary và các quy tắc resolution; thiếu hoặc mơ hồ có thể làm tạo bean thất bại.
- **Optional/provider/collection** cho phép biểu diễn nhu cầu khác nhau: vắng mặt hợp lệ, lookup muộn hoặc nhiều beans cùng type.
- **Tham số phương thức** làm việc rõ ràng với `@Configuration(proxyBeanMethods=false)`; gọi trực tiếp method `@Bean` có semantics khác tùy proxy mode.
- **Scope/lifecycle và dependency graph** quyết định khi nào bean được tạo; `@Order` không thay thế constructor/factory dependencies.
- Một factory method có tham số rõ ràng thường dễ đọc hơn việc inject `ApplicationContext` chỉ để tự đi lấy từng dependency.

Tài liệu chính thức:

- [Spring Framework Reference — Using the `@Bean` Annotation](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html).
- [Spring Framework Reference — Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html).
- [Spring Framework Reference — Using `@Autowired`](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html).
- [Spring Framework Reference — Fine-tuning Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html).
- [Spring Framework Reference — Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java.html).
- [Spring Framework Javadoc — `ObjectProvider`](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/beans/factory/ObjectProvider.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-configuration-004">Tên, bí danh và kiểu trả về của phương thức @Bean</a>

<details>
<summary>Xem chi tiết</summary>

# Tên, bí danh và kiểu trả về của phương thức @Bean trong Spring Core Container

## Mục tiêu bài học

Trong Spring Core Container, phương thức được đánh dấu `@Bean` khai báo một **bean definition** dựa trên factory method Java. Ba khía cạnh rất dễ nhầm với nhau là:

1. **Tên bean (bean name):** định danh chính để container đăng ký và tra cứu bean.
2. **Bí danh (alias):** một hoặc nhiều tên bổ sung trỏ về **cùng bean definition**, không phải các bean độc lập.
3. **Kiểu trả về của phương thức `@Bean`:** metadata kiểu mà Spring có thể dùng để dự đoán ứng viên, giải quyết dependency và làm việc với generic, AOP/proxy hoặc các thành phần hạ tầng.

Bài này giải thích rõ cách khai báo và sử dụng từng khía cạnh, tập trung vào Java configuration bằng `@Configuration` và `@Bean`. Bạn sẽ biết khi nào để tên theo mặc định, khi nào đặt tên tường minh, phân biệt alias với hai factory methods riêng, chọn return type đúng, chẩn đoán `NoSuchBeanDefinitionException`/`NoUniqueBeanDefinitionException`, và thực hành với dự án Java 17 + Spring Framework thuần.

**Nguyên tắc:** Tên bean xác định **bean được gọi là gì**; alias xác định **các tên khác của cùng bean**; return type thể hiện **loại đối tượng mà factory method hứa cung cấp**. Chúng tương tác với dependency resolution nhưng không thay thế cho nhau.

## 1. Phương thức @Bean đăng ký cái gì?

Ví dụ tối giản:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AppConfig {

    @Bean
    PaymentGateway paymentGateway() {
        return new DemoPaymentGateway();
    }
}
```

Đây là **đoạn minh họa**, cần các kiểu `PaymentGateway` và `DemoPaymentGateway` do ứng dụng định nghĩa. Khi class `AppConfig` được đăng ký vào `ApplicationContext`:

- Spring đọc metadata `@Bean` trên phương thức `paymentGateway()`.
- Một bean definition được tạo cho factory method đó.
- Theo mặc định, bean có tên `paymentGateway` (giống tên method).
- Kiểu được công bố bởi method là `PaymentGateway`.
- Khi cần tạo bean theo scope/lifecycle, Spring gọi factory method và quản lý đối tượng trả về.

Bean không phải là chính phương thức Java; bean là **đối tượng do Spring quản lý** theo bean definition. Nếu gọi `new AppConfig().paymentGateway()` bên ngoài container, kết quả là đối tượng Java thường, không tự nhận lifecycle, dependency injection hay các post-processors của Spring.

Điều này khác với đăng ký hai bean definitions: một method `@Bean` với hai alias vẫn chỉ định nghĩa **một bean**, trong khi hai method `@Bean` riêng thường định nghĩa **hai bean** dù return type giống nhau.

## 2. Tên bean mặc định là tên của phương thức @Bean

Khi không khai báo `name` hoặc `value`:

```java
@Configuration(proxyBeanMethods = false)
class PaymentConfig {

    @Bean
    PaymentGateway paymentGateway() {
        return new DemoPaymentGateway();
    }
}
```

Spring đặt **bean name** mặc định là `paymentGateway`, đúng bằng tên Java method. Có thể tra cứu bằng tên:

```java
// Đoạn minh họa bên trong nơi đã có context.
PaymentGateway gateway =
        context.getBean("paymentGateway", PaymentGateway.class);
```

Điều cần ghi nhớ:

- Tên bean `paymentGateway` được dùng làm **định danh trong container**, không phải tên class `DemoPaymentGateway`.
- Đổi tên Java method `paymentGateway()` thành `createGateway()` mà vẫn giữ `@Bean` không tên sẽ **đổi bean name mặc định** thành `createGateway`.
- Nếu code cấu hình khác hoặc thư viện tra cứu bean bằng chuỗi `"paymentGateway"`, việc đổi tên method có thể gây lỗi runtime dù source vẫn biên dịch.
- Tiêm dependency theo kiểu `PaymentGateway` có thể vẫn hoạt động nếu Spring phân giải được một ứng viên duy nhất; tên không phải lúc nào cũng được người tiêu thụ nhắc đến trực tiếp.

**Khác với component scanning:** Một class `@Component` không có tên thường được `AnnotationBeanNameGenerator` đặt tên dựa trên tên class; còn `@Bean` không có tên dùng **tên factory method**. Hai quy tắc này có thể tạo ra cùng chuỗi trong một số trường hợp, nhưng nguồn gốc metadata khác nhau.

### Khi nào để tên mặc định?

Nếu cấu hình nội bộ đơn giản, không có lookup bằng chuỗi hoặc hợp đồng bean name công khai, tên phương thức dễ đọc thường là đủ. Chọn tên như `httpClient`, `orderRepository`, `notificationSender` để phản ánh vai trò bean.

Nếu bean name được tài liệu hóa như một integration contract — ví dụ bean dành cho module khác dùng `@Qualifier("billingGateway")` — nên cân nhắc đặt tên tường minh để refactor method không vô tình phá hợp đồng.

## 3. Đặt tên tường minh bằng @Bean(name) hoặc @Bean(value)

`@Bean` có hai thuộc tính `name` và `value`, là **alias cho nhau trong annotation metadata**. Một số cách viết tương đương về tên:

```java
// Các biến thể minh họa, chỉ chọn một cho một factory method:

@Bean(name = "billingGateway")
PaymentGateway gateway() {
    return new DemoPaymentGateway();
}

@Bean(value = "billingGateway")
PaymentGateway gateway() {
    return new DemoPaymentGateway();
}

@Bean("billingGateway")
PaymentGateway gateway() {
    return new DemoPaymentGateway();
}
```

Các đoạn trên **không** được đặt cả ba method `gateway()` giống hệt nhau vào cùng một class; chúng là những phương án tương đương về cú pháp. Cả ba đều khai báo tên bean là `billingGateway` dù tên phương thức là `gateway`.

Nếu khai báo tên tường minh, đừng mặc định tên phương thức cũ còn dùng để `getBean(...)`. Với:

```java
@Bean("billingGateway")
PaymentGateway createGateway() {
    return new DemoPaymentGateway();
}
```

tên chính là `billingGateway`. Spring **không tự động** bổ sung `createGateway` làm alias. Nếu cần hỗ trợ tên này như một tên thứ hai, hãy khai báo trong mảng tên hoặc đăng ký alias có chủ đích.

**Lưu ý cú pháp:** `@Bean(name = ...)` nhận `String[]`, vì vậy một chuỗi đơn được Java chấp nhận như mảng một phần tử trong ngữ cảnh annotation. Dạng `@Bean("billingGateway")` là cách viết ngắn nhờ thuộc tính `value`.

### @Bean(name) khác @AliasFor

`@Bean.name` và `@Bean.value` được định nghĩa là alias **giữa hai thuộc tính annotation** theo `@AliasFor` của Spring. Còn `@Bean(name = {"main", "legacy"})` tạo ra một **bean name và một bean-name alias trong BeanFactory**.

Hai cách dùng từ "alias" này thuộc **hai cấp độ khác nhau**:

| Loại alias | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| Alias thuộc tính annotation | `@Bean.name` và `@Bean.value` | Hai thuộc tính thể hiện cùng metadata cấu hình |
| Alias tên bean | `@Bean(name = {"main", "legacy"})` | Hai chuỗi tra cứu cùng một bean definition/instance theo scope |

Đừng nghĩ rằng khai báo `name` và `value` là cách tạo hai bean độc lập hoặc dùng chúng làm hai danh sách tên khác nhau.

## 4. Bí danh trong mảng @Bean(name = {...})

Khi một phương thức `@Bean` khai báo nhiều chuỗi tên:

```java
@Bean(name = {"paymentGateway", "legacyGateway", "checkoutGateway"})
PaymentGateway paymentGatewayFactory() {
    return new DemoPaymentGateway();
}
```

Spring dùng **phần tử đầu tiên** làm tên chính `paymentGateway`; những chuỗi còn lại là alias `legacyGateway` và `checkoutGateway` của **cùng bean**.

Ví dụ trong một singleton container:

```java
// Minh họa trong code đã có context.
Object a = context.getBean("paymentGateway");
Object b = context.getBean("legacyGateway");
Object c = context.getBean("checkoutGateway");

System.out.println(a == b && b == c);
```

Nếu bean là singleton mặc định và những tên đó được khai báo như trên, output là:

```text
true
```

Điều đó không nghĩa Spring tạo ba instance rồi trộn lại. Tất cả tên được phân giải đến cùng bean definition, và singleton resolution cho ra cùng reference.

### Dùng alias khi nào?

- **Tương thích ngược:** module cũ tra cứu `legacyGateway`, module mới chuyển sang `paymentGateway`.
- **Đa ngữ nghĩa cùng vai trò:** một bean cần được công bố cho hai API naming conventions có chủ đích.
- **Di trú cấu hình:** đổi tên chính nhưng vẫn hỗ trợ tên cũ trong giai đoạn chuyển đổi.

Cần thận trọng: alias càng nhiều càng khó nhận biết đâu là tên chuẩn; không dùng alias để che sự nhập nhằng thực sự giữa hai implementation khác nhau.

### Alias không tạo thêm bean cùng kiểu

Giả sử:

```java
@Bean(name = {"mainGateway", "oldMainGateway"})
PaymentGateway gateway() {
    return new DemoPaymentGateway();
}
```

Lúc tiêm một `PaymentGateway`, chỉ có **một bean definition** được khai báo bởi method này. Số alias không làm container có "hai ứng viên cùng kiểu". Trong khi đó:

```java
@Bean("firstGateway")
PaymentGateway firstGateway() {
    return new DemoPaymentGateway();
}

@Bean("secondGateway")
PaymentGateway secondGateway() {
    return new DemoPaymentGateway();
}
```

là **hai bean definitions** tách biệt, có thể tạo hai singleton riêng; injection `PaymentGateway` không qualifier có thể trở nên nhập nhằng nếu không có quy tắc chọn.

### Bean name là duy nhất trong vùng BeanFactory

Tên chính và alias có thể xung đột với tên/alias khác. Một container không thể dùng cùng một tên hiệu quả cho hai bean definitions khác nhau mà không có chính sách override/đăng ký alias hợp lệ. Tùy loại container, phiên bản và cấu hình, tên trùng có thể bị từ chối hoặc có quy tắc ghi đè; **đừng** dựa vào thứ tự phương thức `@Bean` để mặc định bean cuối được chọn.

Nên dùng tên duy nhất, có ý nghĩa nghiệp vụ và kiểm thử trường hợp có nhiều module đăng ký bean cùng tên.

## 5. Tên bean khác biệt với việc chọn bean theo kiểu

Khi Spring inject dependency:

```java
@Bean
CheckoutService checkoutService(PaymentGateway gateway) {
    return new CheckoutService(gateway);
}
```

Spring phân giải `PaymentGateway` theo **kiểu và các quy tắc chọn ứng viên**. Tên tham số `gateway` không phải một lệnh luôn phải tìm bean tên `gateway`; tên có thể là tín hiệu hỗ trợ trong một số tình huống chọn ứng viên, nhưng không thay thế cho `@Qualifier` khi cần xác định rõ một bean.

### Khi chỉ có một bean đúng kiểu

Nếu chỉ có một `PaymentGateway` có thể autowire, Spring thường tiêm bean đó bất kể tên tham số là `gateway` hay `paymentClient`.

### Khi có nhiều bean cùng kiểu

```java
@Configuration(proxyBeanMethods = false)
class MultiGatewayConfig {

    @Bean("fastGateway")
    PaymentGateway fastGateway() {
        return new FastPaymentGateway();
    }

    @Bean("safeGateway")
    PaymentGateway safeGateway() {
        return new SafePaymentGateway();
    }

    @Bean
    CheckoutService checkoutService(
            @Qualifier("safeGateway") PaymentGateway gateway) {
        return new CheckoutService(gateway);
    }
}
```

Đây là **đoạn minh họa cần các class ứng dụng tương ứng**. `@Qualifier("safeGateway")` làm injection point yêu cầu ứng viên phù hợp với qualifier/tên đó; tên Java method `checkoutService` xác định bean của consumer, không quyết định gateway.

Có thể đánh dấu `@Primary` trên một bean làm ứng viên ưu tiên khi cần chọn một bean theo kiểu. Nhưng `@Primary` không đổi tên bean và không tạo alias.

### Đừng lạm dụng tên tham số

Spring có một số cơ chế chọn ứng viên dựa trên bean name trùng tên dependency, tùy metadata tên tham số/field và những điều kiện khác. Điều này đôi khi giúp autowiring khi nhiều bean tồn tại, nhưng nó dễ thay đổi sau refactor. Khi selection mang ý nghĩa nghiệp vụ, dùng **`@Qualifier` tường minh**, interface hẹp, hoặc registry đúng mục đích.

### Tra cứu theo tên và kiểu cùng lúc

```java
PaymentGateway gateway =
        context.getBean("safeGateway", PaymentGateway.class);
```

Method này yêu cầu bean có **tên** `safeGateway` đồng thời có **kiểu tương thích** `PaymentGateway`. Nếu tên tồn tại nhưng bean không thể được cast sang kiểu yêu cầu, Spring báo lỗi kiểu; việc biết đúng tên không biến một đối tượng thành kiểu bất kỳ.

## 6. Return type của @Bean là metadata quan trọng

Phương thức factory công bố một **kiểu trả về Java**, ví dụ:

```java
@Bean
PaymentGateway paymentGateway() {
    return new DemoPaymentGateway();
}
```

Bean thực tế là instance `DemoPaymentGateway`, nhưng kiểu được khai báo là **`PaymentGateway`**. Spring sử dụng thông tin về phương thức `@Bean` để dự đoán bean type trong nhiều giai đoạn **trước khi instance được khởi tạo**.

Điều này có hệ quả:

- Những injection points nhận `PaymentGateway` có metadata phù hợp với return type.
- Một injection point đòi `DemoPaymentGateway` có thể **không được Spring nhận diện** ở thời điểm dự đoán type nếu factory method chỉ khai báo `PaymentGateway` và instance chưa được tạo.
- Sau khi bean đã được tạo, container có thể biết thêm runtime type, nhưng việc giải quyết dependency sớm không nên phụ thuộc vào việc bean tình cờ đã khởi tạo trước.
- Scope, proxying, factory object và generic metadata còn ảnh hưởng cách Spring thấy type; không đánh đồng Java runtime class với exposed bean type trong mọi giai đoạn.

### Chọn return type interface hay concrete class?

| Cách khai báo | Lợi điểm | Lưu ý |
| --- | --- | --- |
| `PaymentGateway paymentGateway()` | Giao diện rõ, khuyến khích consumer phụ thuộc abstraction | Không công bố concrete type đầy đủ trong method signature cho early type matching |
| `DemoPaymentGateway paymentGateway()` | Spring có nhiều metadata cụ thể hơn trước khi tạo bean | Consumer không nhất thiết phải inject theo concrete class; tránh coupling |
| `Object paymentGateway()` | Rất rộng, ít thông tin | Dễ làm container khó dự đoán đúng ứng viên; hầu như không nên dùng |
| `Collection<Handler> handlers()` | Công bố một bean có type collection | **Một bean collection**, khác với "nhiều Handler bean" được tiêm thành list |
| `FactoryBean<Product> productFactory()` | Công bố một `FactoryBean` cung cấp product | Spring có hợp đồng phân biệt **factory** và **product**; cần hiểu cú pháp `&name` |

**Khuyến nghị:** Khai báo **kiểu trả về đủ cụ thể để các injection point cần thiết được phân giải một cách tin cậy**. Đây không phải yêu cầu mọi factory method phải trả concrete class; nếu cả ứng dụng chỉ phụ thuộc `PaymentGateway`, việc trả interface này hoàn toàn hợp lý. Nếu bạn dự kiến Spring phải nhận bean theo subtype cụ thể **trước khi tạo**, hãy cân nhắc công bố subtype từ method hoặc thay đổi thiết kế injection.

### Không cố cast ở mọi nơi

Nếu một factory method trả `PaymentGateway` nhưng object là `DemoPaymentGateway`, việc cast ở một consumer để gọi method riêng của concrete type làm tăng coupling. Thường nên mở rộng interface đúng hợp đồng hoặc tạo một interface capability hẹp, thay vì buộc nhiều consumer phụ thuộc implementation.

## 7. Ví dụ hoàn chỉnh Java 17 + Spring Framework: tên, alias, kiểu trả về

Ví dụ sau chạy bằng Java 17 và Maven với Spring Framework thuần; không cần Spring Boot.

Mục tiêu kiểm chứng:

1. `@Bean` không chỉ định tên dùng **tên method**.
2. `@Bean(name = {"mailClient", "legacyMailClient"})` đăng ký **một bean và một alias**.
3. Nếu tên bean được đặt tường minh, tên method factory **không tự trở thành alias**.
4. Factory method có thể khai báo concrete return type `SmtpClient` hoặc interface return type `DeliveryClient`.
5. `@Primary` chọn bean ưu tiên cho single-bean injection mà không đổi tên và không loại các bean khác khỏi collection.
6. `@Qualifier("smsClient")` chọn chính xác một bean khi có hai ứng viên theo interface.

### 7.1. Tạo cấu trúc dự án

```text
bean-name-return-type/
├── pom.xml
└── src/main/java/demo/BeanIdentityDemo.java
```

Tạo `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>bean-name-return-type</artifactId>
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

### 7.2. Mã nguồn Java

Tạo `src/main/java/demo/BeanIdentityDemo.java`:

```java
package demo;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

public class BeanIdentityDemo {

    interface DeliveryClient {
        String channel();
    }

    static final class SmtpClient implements DeliveryClient {
        @Override
        public String channel() {
            return "mail";
        }
    }

    static final class SmsClient implements DeliveryClient {
        @Override
        public String channel() {
            return "sms";
        }
    }

    static final class OrderNotifier {
        private final DeliveryClient client;

        OrderNotifier(DeliveryClient client) {
            this.client = client;
        }

        String notifyOrder(String orderId) {
            return client.channel() + ":" + orderId;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AppConfig {

        @Bean(name = {"mailClient", "legacyMailClient"})
        @Primary
        SmtpClient createMailClient() {
            return new SmtpClient();
        }

        @Bean("smsClient")
        DeliveryClient createSmsClient() {
            return new SmsClient();
        }

        @Bean
        OrderNotifier orderNotifier(
                @Qualifier("smsClient") DeliveryClient client) {
            return new OrderNotifier(client);
        }
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            SmtpClient byMainName =
                    context.getBean("mailClient", SmtpClient.class);
            SmtpClient byAlias =
                    context.getBean("legacyMailClient", SmtpClient.class);
            SmtpClient byConcreteType =
                    context.getBean(SmtpClient.class);
            DeliveryClient byInterfaceType =
                    context.getBean(DeliveryClient.class);

            System.out.println("defaultName=" +
                    context.containsBean("orderNotifier"));
            System.out.println("methodNameIsBean=" +
                    context.containsBean("createMailClient"));
            System.out.println("aliasSame=" +
                    (byMainName == byAlias));
            System.out.println("concreteSame=" +
                    (byMainName == byConcreteType));
            System.out.println("primaryIsMail=" +
                    (byMainName == byInterfaceType));

            OrderNotifier notifier =
                    context.getBean(OrderNotifier.class);
            System.out.println("notifier=" +
                    notifier.notifyOrder("A42"));

            String[] names =
                    context.getBeanNamesForType(DeliveryClient.class);
            Arrays.sort(names);
            System.out.println("deliveryBeans=" +
                    Arrays.toString(names));
        }
    }
}
```

### 7.3. Biên dịch và chạy

Từ thư mục có `pom.xml`, cài JDK 17+ và Maven rồi chạy:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.BeanIdentityDemo
```

Output **do chương trình in**, không tính log của Maven/Spring nếu môi trường cấu hình log khác:

```text
defaultName=true
methodNameIsBean=false
aliasSame=true
concreteSame=true
primaryIsMail=true
notifier=sms:A42
deliveryBeans=[mailClient, smsClient]
```

### 7.4. Giải thích từng kết quả

**`defaultName=true`:** Method `orderNotifier(...)` chỉ có `@Bean` không tên, vì vậy bean mặc định tên `orderNotifier`.

**`methodNameIsBean=false`:** Method `createMailClient()` khai báo tên tường minh `mailClient` và alias `legacyMailClient`. Tên method `createMailClient` **không** tự được đăng ký thêm làm tên bean.

**`aliasSame=true`:** `mailClient` và `legacyMailClient` cùng trỏ tới bean definition singleton của `SmtpClient`, vì vậy chúng cho cùng instance.

**`concreteSame=true`:** Phương thức `createMailClient()` công bố kiểu trả về `SmtpClient`. Spring nhận diện và cung cấp bean này khi lookup theo concrete type `SmtpClient.class`. Bean lấy theo kiểu và tên chính giống nhau trong một context singleton.

**`primaryIsMail=true`:** Có hai bean implement `DeliveryClient`: `mailClient` và `smsClient`. `@Primary` trên factory method mail giúp Spring chọn mail khi lookup một `DeliveryClient` không có qualifier. `@Primary` không đổi tên `mailClient` và không hủy `smsClient`.

**`notifier=sms:A42`:** Factory method `orderNotifier` nhận tham số được đánh dấu `@Qualifier("smsClient")`. Dù mail là primary, qualifier chọn `smsClient` để tạo `OrderNotifier`; kết quả là `sms:A42`.

**`deliveryBeans=[mailClient, smsClient]`:** `getBeanNamesForType(DeliveryClient.class)` trả **bean names chính** của các bean phù hợp kiểu, không liệt kê `legacyMailClient` như bean độc lập. `Arrays.sort` bảo đảm thứ tự output theo tên; không dựa vào thứ tự đăng ký bean.

### 7.5. Tự kiểm chứng alias bằng BeanFactory

Trong `Main` của ví dụ, có thể thêm:

```java
// Đoạn bổ sung bên trong try đã có biến context:
boolean isAlias =
        context.getBeanFactory().isAlias("legacyMailClient");
System.out.println("recognizedAlias=" + isAlias);
```

Output bổ sung:

```text
recognizedAlias=true
```

Method `isAlias` kiểm tra tên đó được đăng ký là alias của một tên bean khác. Cần nhớ `context.getBean("legacyMailClient")` và `context.getBean("mailClient")` bằng reference với bean singleton không có nghĩa chúng là hai bean definitions.

## 8. Return type interface và concrete class khác nhau khi nào?

Xem hai kiểu factory:

```java
@Bean("mailA")
DeliveryClient mailA() {
    return new SmtpClient();
}

@Bean("mailB")
SmtpClient mailB() {
    return new SmtpClient();
}
```

Đây là đoạn minh họa, **không đặt hai bean này cạnh các bean đang có trong ví dụ chính nếu không sửa injection** vì thêm ứng viên có thể ảnh hưởng việc chọn bean.

**Ở runtime Java**, object của cả hai có thể là instance `SmtpClient`. Nhưng metadata lúc container đọc factory method khác nhau:

- `mailA()` công bố `DeliveryClient`.
- `mailB()` công bố `SmtpClient`.

Nếu Spring cần dự đoán bean nào có thể tiêm vào `SmtpClient` **trước khi khởi tạo**, `mailB` là ứng viên rõ ràng hơn vì method signature công bố concrete type. Không nên trông chờ bean `mailA` sẽ được tìm như `SmtpClient` ở mọi thời điểm khởi tạo, dù factory cuối cùng trả đúng subtype ấy.

### Tránh giả định "Spring sẽ tự nhìn vào return statement"

Spring không luôn thực thi factory method trước để biết return type thực tế. Làm vậy có thể phá lazy initialization, tạo resource sớm, hoặc tạo dependency cycle. Trong type prediction, method signature là nguồn metadata quan trọng. Hãy xem khai báo method như **hợp đồng của bean**, không chỉ như cú pháp Java cho compiler.

### Cân bằng abstraction và metadata

Khi các consumer chỉ cần `DeliveryClient`, trả interface là thiết kế tốt. Khi một bean cần cung cấp tính năng cụ thể của `SmtpClient` và Spring phải inject subtype đó, có thể:

1. Công bố concrete return type `SmtpClient` trong factory method.
2. Định nghĩa một interface có ý nghĩa hẹp hơn thay vì phụ thuộc concrete class.
3. Dùng qualifier hoặc bean name rõ ràng nếu mục tiêu là chọn implementation theo vai trò, **không phải** truy cập method đặc thù của concrete class.

Việc công bố concrete return type không buộc tất cả consumer phải inject concrete type: chúng vẫn có thể phụ thuộc `DeliveryClient`.

## 9. Return type và generic type trong dependency matching

Spring có thể dùng **generic type metadata** như một điều kiện lọc ứng viên. Đây là lý do khác để tránh raw types hoặc `Object` trong chữ ký phương thức `@Bean`.

Ví dụ:

```java
// Các đoạn minh họa; cần đặt trong một @Configuration phù hợp.
interface Codec<T> {
    T decode(byte[] input);
}

@Bean
Codec<String> stringCodec() {
    return bytes ->
            new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
}

@Bean
Codec<byte[]> binaryCodec() {
    return byte[]::clone;
}
```

Hai bean trên cùng interface raw `Codec`, nhưng metadata return type phân biệt `Codec<String>` và `Codec<byte[]>`. Một consumer khai báo:

```java
// Đoạn minh họa trong constructor bean khác:
CodecRegistry(Codec<String> stringCodec) {
    this.stringCodec = stringCodec;
}
```

có thể dùng generic như một tiêu chí phân giải `Codec<String>` khi Spring giữ được metadata kiểu tương ứng.

### Vì sao không nên trả raw Codec?

Nếu viết:

```java
// Phản ví dụ về metadata nghèo:
@Bean
@SuppressWarnings("rawtypes")
Codec codec() {
    return bytes -> "decoded";
}
```

method không công bố rõ generic argument. Spring có ít thông tin hơn khi lựa chọn những injection points với `Codec<String>` hoặc `Codec<byte[]>`. Raw type còn làm giảm type safety của Java. Nên khai báo đầy đủ generic khi một BeanFactory phải phân biệt các bean cùng raw type.

### Factory method trả Collection khác collection injection

Hai cấu hình sau phục vụ hai mục đích khác nhau:

```java
@Bean
java.util.List<String> supportedChannels() {
    return java.util.List.of("mail", "sms");
}
```

Đây là **một bean** có kiểu `List<String>`, được tạo từ một factory method.

Ngược lại, constructor nhận:

```java
// Đoạn minh họa trong Spring-managed bean:
Router(java.util.List<DeliveryClient> clients) {
    this.clients = clients;
}
```

thường yêu cầu Spring tổng hợp **nhiều bean `DeliveryClient`** vào một collection khi inject. Đừng nghĩ factory method trả `List<DeliveryClient>` và Spring tổng hợp nhiều `DeliveryClient` sẽ luôn tương đương trong mọi injection context; một cái là bean collection, cái kia là tập các bean ứng viên.

## 10. Kiểu trả về FactoryBean<T> là trường hợp đặc biệt

Spring có abstraction `org.springframework.beans.factory.FactoryBean<T>` để một bean factory object chịu trách nhiệm cung cấp **product bean**. Đây **không phải** cùng khái niệm với một phương thức `@Bean` bình thường, dù tên gọi đều có chữ "factory".

Ví dụ về ý tưởng:

```java
// Minh họa: ProductFactory là một FactoryBean<Product> do ứng dụng viết.
@Bean("product")
FactoryBean<Product> productFactory() {
    return new ProductFactory();
}
```

Cần hiểu khi dùng `FactoryBean<T>`:

- Tra cứu `context.getBean("product")` thông thường lấy **product** do `FactoryBean` tạo/cung cấp.
- Tra cứu `context.getBean("&product")` lấy **chính FactoryBean object** (tiền tố `&` là dereference convention của Spring).
- Generic `FactoryBean<Product>` và thông tin từ `getObjectType()` có ảnh hưởng quan trọng đến **dự đoán product type**; nếu không công bố đúng, autowiring theo product type có thể khó phân giải trước khi tạo factory/product.
- `FactoryBean` có hợp đồng về `getObject()`, `getObjectType()` và `isSingleton()` riêng; không suy ra product được tạo mới từ scope của factory bean mà không xét các method đó.

Đây là ví dụ minh họa API nâng cao, không phải chương trình độc lập. Khi không cần custom FactoryBean, một `@Bean Product product()` đơn giản thường dễ hiểu hơn. Đừng thêm FactoryBean chỉ để đổi tên bean hoặc tạo alias.

## 11. Return type và proxy/AOP/lazy resolution

Một bean có thể được Spring bọc bằng proxy khi bật transaction, AOP hoặc một số scope. Vì vậy, **runtime class nhận từ container có thể khác concrete class được factory method trực tiếp tạo ra**. Ví dụ JDK dynamic proxy thường implement interface mà không extend class thực tế. Đó là lý do constructor injection theo **interface ổn định** thường bền vững hơn kiểu cast concrete class không cần thiết.

Cần phân biệt:

- **Declared return type** trong phương thức `@Bean`.
- **Target class/instance** mà factory method tạo.
- **Exposed type** của bean sau khi Spring post-process/proxy ở thời điểm sử dụng.

Khi một injection point yêu cầu concrete class nhưng bean được proxy theo interface, việc tìm/cast bean có thể không phù hợp. Chọn interface làm hợp đồng chính hoặc điều chỉnh proxy strategy có chủ đích, không dựa vào chi tiết implementation.

### @Lazy không giải quyết metadata kiểu nghèo

`@Lazy` có thể trì hoãn thời điểm khởi tạo singleton nhưng không tự cung cấp generic/concrete type metadata đã bị mất do factory method khai báo `Object` hoặc raw interface. Ngược lại, declared return type rõ ràng càng quan trọng khi Spring phải quyết định ứng viên **trước khi tạo** đối tượng lazy.

### @Primary và @Qualifier cũng không "sửa" return type

Nếu một factory method khai báo `Object`, việc thêm `@Primary` không biến nó thành `PaymentGateway` về mặt Java type signature. `@Qualifier` lọc các ứng viên Spring đã xem là phù hợp; nếu metadata kiểu không thể xác định đúng từ factory method, qualifier không có phép màu để bảo đảm injection subtype sớm sẽ thành công.

## 12. Vai trò của tên bean khi dùng @Qualifier

`@Qualifier` thường được dùng tại constructor parameter, field hoặc tham số của method `@Bean`. Khi một qualifier là chuỗi trùng với bean name, Spring có thể chọn bean theo tên đó.

```java
@Bean
NotificationService notificationService(
        @Qualifier("mailClient") DeliveryClient client) {
    return new NotificationService(client);
}
```

Đoạn trên minh họa một class `NotificationService` phải được định nghĩa. Nó yêu cầu một bean `DeliveryClient` phù hợp với `mailClient`, không yêu cầu tên phương thức factory cũng là `mailClient`.

### Alias có thể dùng để tra cứu như một tên bean hợp lệ

```java
context.getBean("legacyMailClient");
```

lấy được bean đã đăng ký tên chính `mailClient` nếu `legacyMailClient` là alias. Trong autowiring, việc so khớp qualifier và tên/alias còn chịu quy tắc lựa chọn ứng viên của Spring. **Ưu tiên tên chính ổn định trong qualifier mới** và dùng alias cho tương thích khi cần, thay vì phát tán nhiều biệt danh tới mọi consumer.

### @Primary không biến alias thành bean thứ hai

Trong ví dụ `BeanIdentityDemo`, `@Primary` được đặt trên method khai báo `mailClient`. Nó gắn metadata ưu tiên vào **bean definition**, không chọn riêng tên chính mà bỏ alias; tìm theo `legacyMailClient` vẫn trỏ tới bean ấy.

## 13. Tránh nhầm @Bean name với Spring BeanDefinition identity

Một bean definition có các metadata như bean name, aliases, scope, qualifiers và factory method. Khi `@Bean` tạo singleton, BeanFactory còn giữ singleton object tương ứng.

Có thể hình dung:

```text
BeanDefinition "mailClient"
    ├── factoryMethod = AppConfig.createMailClient()
    ├── exposedType = SmtpClient
    ├── primary = true
    └── aliases:
        └── "legacyMailClient"
                 |
                 v
         BeanFactory resolves
                 |
                 v
         cùng managed singleton
```

Đối lập:

```text
BeanDefinition "mailClient" ---> SmtpClient instance A
BeanDefinition "smsClient"  ---> SmsClient instance B
```

Hai bean definitions riêng có thể cùng interface `DeliveryClient`, nhưng chúng có identity, scope, lifecycle và qualifiers độc lập. Để kiểm thử **alias**, hãy so sánh cùng reference và/hoặc gọi `isAlias`; để kiểm thử **hai bean độc lập**, hãy xem danh sách bean names theo type và xác nhận đúng các definitions.

### Singleton identity chỉ trong một container

Hai context khác nhau cùng đăng ký `@Bean("mailClient")` thường có **singleton độc lập**:

```java
// Đoạn minh họa với AppConfig đã định nghĩa:
try (var a = new AnnotationConfigApplicationContext(AppConfig.class);
     var b = new AnnotationConfigApplicationContext(AppConfig.class)) {
    System.out.println(
            a.getBean("mailClient") == b.getBean("mailClient"));
}
```

Output dự kiến:

```text
false
```

Vì mỗi `ApplicationContext` có singleton registry riêng. Bean name chỉ xác định object **trong phạm vi container**, không tự làm instance thành JVM-global singleton.

## 14. Đặt tên khi @Bean có nhiều method cùng tên hoặc inheritance

### Tránh overload factory methods thiếu chủ đích

Java cho phép overload:

```java
// Phản ví dụ minh họa, không đặt vào chương trình chạy chính:
@Bean
Client client() { return new Client(); }

@Bean
Client client(Settings settings) { return new Client(settings); }
```

Đây là nhiều factory methods **cùng tên Java**, dẫn đến khó đoán hoặc sai hợp đồng bean name/factory overload. Spring Framework hiện đại có các quy tắc xử lý/kiểm tra tính duy nhất của `@Bean` methods trong full configuration; khi khai báo overload cần rất thận trọng và có thể không được chấp nhận trong những cấu hình mặc định.

**Khuyến nghị:** Dùng các **tên phương thức khác nhau**, và nếu cần bean name cụ thể, dùng `@Bean("name")`. Đừng phụ thuộc vào overload để chọn factory method theo tập tham số vào lúc runtime nếu muốn Java configuration rõ ràng.

### Inheritance và method override

Nếu các configuration classes kế thừa phương thức `@Bean` hoặc override method, cách Spring xử lý phụ thuộc vào metadata method/class, full/lite mode và những quy tắc override liên quan. Không dựa vào inheritance phức tạp để tạo alias hoặc thay bean một cách ngầm. Với cấu hình nhiều module, `@Import` và các factory methods có tên riêng thường dễ hiểu/test hơn.

### Method rename là thay đổi public bean-name contract

Một dự án có:

```java
@Bean
Client oldClient() { return new Client(); }
```

Nếu đổi thành `createClient()`, tên bean mặc định đổi. Nếu các consumer tiêm bằng `@Qualifier("oldClient")` hoặc gọi `getBean("oldClient")`, cần cập nhật chúng hoặc giữ tên cũ bằng `@Bean("oldClient")` / alias tương thích.

Đừng xem đổi tên factory method luôn là refactor không ảnh hưởng hành vi runtime.

## 15. Tên và kiểu return của @Bean trong cấu hình nhiều module

Một module cấu hình có thể được import vào module khác:

```java
@Configuration(proxyBeanMethods = false)
class ClientConfig {

    @Bean("billingClient")
    PaymentClient paymentClient() {
        return new HttpPaymentClient();
    }
}

@Configuration(proxyBeanMethods = false)
@Import(ClientConfig.class)
class CheckoutConfig {

    @Bean
    CheckoutService checkoutService(
            @Qualifier("billingClient") PaymentClient paymentClient) {
        return new CheckoutService(paymentClient);
    }
}
```

Đây là các class minh họa, cần có `PaymentClient`, `HttpPaymentClient` và `CheckoutService`. `@Import` đưa configuration class vào context, còn tham số `PaymentClient paymentClient` được Spring giải quyết theo kiểu và qualifier. Ta **không cần** tự tạo `new ClientConfig().paymentClient()`.

Trong dự án đa module, đặt tên bean là một phần của thiết kế module nếu bên ngoài module phải tham chiếu nó. Khi đó:

- Ưu tiên tên có ý nghĩa và tránh xung đột với module khác, ví dụ `billingClient` thay vì `client` quá chung.
- Thông báo khi đổi tên chính; cân nhắc alias giữ tương thích trong giai đoạn migration.
- Công bố return type đủ ổn định như `PaymentClient` nếu consumer chỉ cần interface.
- Đừng để framework nội bộ ngầm phụ thuộc vào concrete type của một bean được khai báo trả interface rộng mà chưa có test.

## 16. Những lỗi thường gặp và cách chẩn đoán

| Triệu chứng | Nguyên nhân có thể | Hướng sửa |
| --- | --- | --- |
| `getBean("oldName")` lỗi sau refactor | Đã đổi tên `@Bean` method nhưng không đặt tên tường minh | Đặt `@Bean("oldName")` hoặc khai báo alias migration |
| `containsBean("factoryMethodName")` trả `false` | `@Bean(name=...)` đã đặt tên khác, tên method không tự là alias | Tra đúng primary bean name/alias đã khai báo |
| Có hai bean cùng interface nhưng tưởng một vì alias | Đã viết **hai** `@Bean` methods riêng | Dùng một method với mảng tên khi chỉ cần một bean |
| Nghĩ có hai bean vì `name={"a","b"}` | `b` chỉ là alias của `a` | Kiểm tra `getBeanNamesForType`/`isAlias` |
| `NoUniqueBeanDefinitionException` | Nhiều bean definitions cùng kiểu không có ứng viên thắng | Dùng `@Qualifier`, `@Primary` hoặc thiết kế collection |
| `NoSuchBeanDefinitionException` theo subtype | Return type factory chỉ là interface rộng, subtype chưa dự đoán được; cũng có thể chưa đăng ký bean | Khai báo return type cụ thể khi cần, kiểm tra đăng ký/scan và bean scope |
| `BeanNotOfRequiredTypeException` | Lookup bằng tên tồn tại nhưng kiểu yêu cầu không phù hợp | Dùng đúng interface/exposed type hoặc điều chỉnh proxy/config |
| Bean name collision | Hai methods/modules cố dùng cùng tên chính hoặc alias | Đặt tên duy nhất, kiểm tra BeanFactory và chính sách override |
| `@Primary` không giúp chọn đúng subtype | Primary chỉ giúp chọn trong tập ứng viên đã tương thích kiểu | Sửa return type/generic metadata hoặc injection type |
| Alias không thể được lấy | Alias chưa được khai báo đúng, xung đột tên hoặc config chưa được nạp | Kiểm tra `@Bean(name={...})`, context và bean definitions |
| Generic injection sai/nhập nhằng | Factory method trả raw type hoặc thiếu metadata generic | Khai báo `Codec<String>` thay vì `Codec` |
| FactoryBean không cho object dự kiến | Nhầm product và FactoryBean object hoặc `getObjectType()` sai | Phân biệt `name` với `&name` và hợp đồng FactoryBean |
| Bean type đổi sau khi bật AOP | Proxy expose type khác concrete class | Dùng interface injection hoặc cấu hình proxy phù hợp |
| Factory method dùng `new` ngoài Spring | Object không do Spring quản lý | Lấy bean từ context hoặc constructor-inject trong managed bean |

### Quy trình debug có hệ thống

1. **Xác định bean name chính**: method có `@Bean(name/value)` không? Nếu không, dùng tên method.
2. **Kiểm tra aliases**: chuỗi thứ hai trở đi trong `name={...}` có được đăng ký không?
3. **Xác định số bean definitions**: một factory method hay nhiều factory methods cùng kiểu?
4. **Xem declared return type**: interface, concrete class, generic hay raw type?
5. **Xem injection point**: cần một bean hay nhiều; có `@Qualifier`/`@Primary` không?
6. **Xem lifecycle**: singleton đã được tạo chưa; `@Lazy`/scope/proxy có ảnh hưởng type prediction không?
7. **Kiểm tra context**: configuration class đã được `register`, `@Import` hoặc component scan tìm thấy chưa?
8. **Đọc nguyên nhân gốc** trong exception chain: thiếu tên, thiếu kiểu, nhiều ứng viên, bean creation hay type mismatch?

### Đừng sửa lỗi bằng cách đổi tên ngẫu nhiên

Khi gặp `NoUniqueBeanDefinitionException`, đổi tên `firstGateway` thành `preferredGateway` không nhất thiết giải quyết việc hai bean cùng kiểu. Phải chỉ rõ **cách chọn ứng viên** bằng qualifier/primary hoặc sửa dependency để nhận collection đúng nhu cầu.

Tương tự, thêm alias cho một bean không làm nó có subtype khác. Bean name resolution và Java type compatibility là hai lớp kiểm tra khác nhau.

## 17. Kiểm thử hành vi cần giữ ổn định

Một cấu hình quan trọng nên được kiểm thử bằng Spring ApplicationContext nhỏ thay vì chỉ test Java factory method bằng `new`.

Trong ví dụ `BeanIdentityDemo`, các phép kiểm tra sau được đặt **bên trong `try` đã có biến `context`**, không phải chương trình độc lập:

```java
if (!context.containsBean("mailClient")) {
    throw new AssertionError("Missing primary bean name");
}
if (!context.containsBean("legacyMailClient")) {
    throw new AssertionError("Missing alias");
}
if (context.containsBean("createMailClient")) {
    throw new AssertionError("Unexpected method-name alias");
}
if (context.getBean("mailClient") !=
        context.getBean("legacyMailClient")) {
    throw new AssertionError("Alias must reference same singleton");
}
if (context.getBean(DeliveryClient.class) !=
        context.getBean(SmtpClient.class)) {
    throw new AssertionError("Primary selection is incorrect");
}
```

Nếu toàn bộ điều kiện đúng, đoạn assert không in thêm dòng stdout. Nếu sai, nó ném `AssertionError` với thông báo cụ thể. Một bộ integration tests có thể thực hiện những assert tương tự qua JUnit/AssertJ và kiểm tra cả tình huống thiếu bean hoặc nhập nhằng.

### Test rename and migration

Nếu thư viện khác cần tên bean `legacyMailClient`, một test tra cứu theo alias giúp bắt regression khi người viết vô tình xóa alias. Nếu bạn muốn bỏ tên cũ, test nên được cập nhật có chủ đích cùng kế hoạch chuyển đổi consumer; đừng để alias kéo dài không cần thiết.

### Test bean type prediction

Nếu một injection point cần `SmtpClient` và bạn đổi factory method từ return type `SmtpClient` sang `DeliveryClient`, hãy tạo context trong integration test với dependency đó để phát hiện việc Spring không còn phân giải subtype trước khi tạo bean. Test này có giá trị hơn giả định từ `getBean("mailClient").getClass()` sau khi context đã khởi tạo.

## 18. Bài tập và đáp án định hướng

### Bài 1 — Tên bean mặc định

```java
@Bean
ReportService reportService() {
    return new ReportService();
}
```

Tên bean mặc định là gì?

**Đáp án:** `reportService`. Đây là tên method, miễn không có metadata tên tường minh/chiến lược đăng ký đặc biệt thay đổi định danh.

### Bài 2 — Explicit name khác tên method

```java
@Bean("mainAudit")
AuditSink createAuditSink() {
    return new StdoutAuditSink();
}
```

Có thể mặc định gọi `getBean("createAuditSink")` không?

**Đáp án:** Không. Tên bean chính là `mainAudit`. `createAuditSink` chỉ là tên Java factory method, không tự trở thành bean name hoặc alias.

### Bài 3 — Một bean hay hai bean?

```java
@Bean(name={"mainAudit","legacyAudit"})
AuditSink auditSink() {
    return new StdoutAuditSink();
}
```

Có bao nhiêu bean definitions được phương thức này đóng góp? `legacyAudit` có tạo thêm singleton không?

**Đáp án:** Một bean definition; `legacyAudit` là alias của `mainAudit`, không phải một bean thứ hai. Với singleton mặc định, tra cứu hai tên cho cùng reference.

### Bài 4 — Hai implementation và qualifier

Một context có `@Bean("smsClient") DeliveryClient sms()` và `@Bean("mailClient") DeliveryClient mail()`. Một service phải luôn chọn SMS. Đổi tên method `sms()` thành `buildSms()` có đủ để chọn SMS không?

**Đáp án:** Không. Bean names tường minh vẫn là `smsClient` và `mailClient`; selection phải được xác định bằng `@Qualifier("smsClient")`, hoặc một quy tắc khác thực sự thể hiện yêu cầu nghiệp vụ.

### Bài 5 — Return type interface/concrete

Hai method `@Bean` đều trả instance `SmtpClient`, nhưng một method khai báo `DeliveryClient`, method kia khai báo `SmtpClient`. Khi Spring cần tìm ứng viên kiểu `SmtpClient` trước khi tạo bean, method nào dễ phân giải hơn?

**Đáp án:** Method khai báo return type `SmtpClient` cung cấp metadata subtype tường minh, nên thích hợp hơn cho early type prediction. Method khai báo interface `DeliveryClient` không công bố subtype bằng chữ ký, dù object tạo ra ở runtime có thể là `SmtpClient`.

### Bài 6 — Return type và generic

Vì sao `Codec<String> stringCodec()` tốt hơn raw `Codec stringCodec()` khi có nhiều codec trong container?

**Đáp án:** Spring có thêm generic metadata để chọn bean cho injection point `Codec<String>`; Java compiler cũng kiểm tra kiểu chặt chẽ hơn.

### Bài 7 — FactoryBean và tiền tố &

Một bean tên `product` được tạo bởi `FactoryBean<Product>`. `getBean("product")` và `getBean("&product")` có cùng mục đích không?

**Đáp án:** Không. Truy vấn thường lấy product do FactoryBean cung cấp; tên có tiền tố `&` dereference tới bản thân factory object. Hành vi này đặc biệt của Spring `FactoryBean`, không phải của mọi phương thức `@Bean`.

### Bài 8 — Đổi tên an toàn

Dự án muốn đổi bean name `oldGateway` thành `billingGateway` nhưng consumer cũ còn dùng tên cũ. Gợi ý cấu hình?

```java
@Bean(name = {"billingGateway", "oldGateway"})
PaymentGateway gateway() {
    return new DemoPaymentGateway();
}
```

**Đáp án:** `billingGateway` là tên chính mới, `oldGateway` là alias hỗ trợ tương thích. Cần lên kế hoạch cập nhật consumer, tránh giữ aliases không còn dùng vô thời hạn.

### Bài 9 — Bẫy collection bean

Một `@Bean List<Handler> handlers()` trả một danh sách handler. Điều đó có tương đương tạo N bean `Handler` bằng N phương thức `@Bean` độc lập không?

**Đáp án:** Không. Factory method trả danh sách đăng ký **một bean collection**; N phương thức đăng ký **N bean `Handler`**. Quy tắc injection collection có thể còn chịu ảnh hưởng các ứng viên có sẵn, vì vậy cần xác định chính xác thứ bạn muốn Spring quản lý.

### Bài 10 — Đọc log lỗi đúng tầng

Một consumer cần `@Qualifier("billingGateway") PaymentGateway`, nhưng Spring báo không có bean phù hợp. Bạn kiểm tra những gì?

**Đáp án:** Đầu tiên kiểm tra configuration đã được nạp, bean name/alias có `billingGateway` không, return type có phù hợp `PaymentGateway` không, và bean có bị loại khỏi autowire candidates không. Nếu bean tồn tại nhưng khởi tạo lỗi, đọc exception gốc thay vì chỉ thêm alias. Nếu có nhiều ứng viên và qualifier không đủ phân biệt, kiểm tra metadata lựa chọn.

## 19. Checklist thiết kế và review

- [ ] Tên bean chính là **tên method mặc định** hoặc tên đã khai báo tường minh.
- [ ] Nếu dùng `@Bean(name={"a","b"})`, hiểu `b` là alias, **không phải bean thứ hai**.
- [ ] Không vô tình dựa vào factory method name khi đã đặt tên tường minh khác.
- [ ] Bean names/aliases không trùng với module khác.
- [ ] Khi nhiều bean cùng kiểu, injection points có qualifier/primary hoặc collection contract rõ ràng.
- [ ] Return type đủ thông tin để Spring phân giải các dependency cần thiết.
- [ ] Generic return type được khai báo đầy đủ thay vì raw type.
- [ ] Không nhầm declared type, runtime object class và exposed proxy type.
- [ ] Nếu dùng `FactoryBean<T>`, phân biệt product và factory qua `name` / `&name`.
- [ ] Có integration test cho tên công khai, alias, bean identity và type matching.
- [ ] Việc đổi tên method `@Bean` được đánh giá như thay đổi hợp đồng runtime khi dựa vào tên mặc định.

## Tổng kết

Trong Java configuration của Spring, **`@Bean` đặt tên mặc định bằng method name**. `@Bean(name = ...)` hoặc `@Bean(value = ...)` cho phép chỉ định tên tường minh; khi cung cấp mảng tên, **phần tử đầu là bean name chính**, các phần tử còn lại là **aliases của cùng bean**. Alias không tăng số lượng bean definitions và không tạo thêm instance theo scope.

**Return type là hợp đồng type metadata của factory method**: nó ảnh hưởng cách Spring dự đoán bean type, chọn ứng viên và xử lý generic trước khi object được tạo. Trả interface là thiết kế tốt khi consumer phụ thuộc abstraction; trả concrete class có ích khi Spring cần dự đoán subtype để inject chính xác. Đừng khai báo return type quá rộng chỉ để code biên dịch.

Hãy đánh giá độc lập ba câu hỏi: **bean tên gì, có bao nhiêu bean độc lập, và bean được công bố dưới kiểu nào?** Trả lời rõ sẽ giúp tránh nhập nhằng autowiring, lỗi tên sau refactor và sự cố type matching khó phát hiện.

## Tài liệu tham khảo chính thức

- [Spring Framework — Using the @Bean Annotation (6.2)](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html): naming, aliases, return type, injection và lifecycle.
- [Spring Framework — @Bean Javadoc](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/context/annotation/Bean.html): `name`, `value`, aliases và các thuộc tính của factory method.
- [Spring Framework — Fine-tuning Annotation-based Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-qualifiers.html): `@Qualifier` và chọn ứng viên.
- [Spring Framework — Fine-tuning Annotation-based Autowiring with @Primary](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-primary.html): lựa chọn primary khi có nhiều ứng viên.
- [Spring Framework — Using Generics as Autowiring Qualifiers](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/generics-as-qualifiers.html): generic type trong injection.
- [Spring Framework — Customizing Instantiation Logic with FactoryBean](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-extension.html): hợp đồng của `FactoryBean`, product type và lifecycle.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-configuration-005">Phương thức @Bean static và thời điểm container phát hiện kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức @Bean static và thời điểm container phát hiện kiểu trong Spring Core Container

## 1. Vấn đề thực tế và mục tiêu

Trong Java configuration của Spring, phương thức mang `@Bean` thông thường là **instance method**. Tuy nhiên, Spring cũng hỗ trợ **static `@Bean` method**:

```java
@Configuration
class ApplicationConfig {
    @Bean
    static ServiceSettings serviceSettings() {
        return new ServiceSettings();
    }
}
```

Đây là snippet cấu trúc; `ServiceSettings` là class ví dụ cần được định nghĩa trong dự án, và phải import `@Configuration`, `@Bean`. Tại sao đôi khi cần `static`, nhất là với `BeanFactoryPostProcessor` và `BeanPostProcessor`? Và vì sao **kiểu trả về được khai báo** trên method có thể quyết định container có phát hiện được một bean đặc biệt **trước lúc khởi tạo instance** hay không?

Sau bài này, bạn sẽ:

1. Phân biệt **phát hiện bean definition**, **dự đoán kiểu bean**, **tạo bean instance** và **xử lý bean trong vòng đời context**.
2. Giải thích vì sao một static `@Bean` có thể được gọi **không cần tạo instance class `@Configuration`**.
3. Biết khi nào Spring khuyến nghị static `@Bean` cho `BeanFactoryPostProcessor` (BFPP) và `BeanPostProcessor` (BPP), và hậu quả khi dùng instance method cho các bean đặc biệt đó.
4. Giải thích tại sao factory method cần khai báo return type đủ cụ thể, nhất là các kiểu `BeanPostProcessor`, `BeanFactoryPostProcessor` hoặc `FactoryBean<T>`.
5. Hiểu rằng **static không đồng nghĩa eager**, **static không đồng nghĩa singleton toàn JVM**, và lời gọi trực tiếp static factory method **không được Spring chặn**.
6. Viết, chạy và kiểm chứng ví dụ Spring Framework 6.2 / Java 17+ cho thấy container nhận diện metadata, tạo processor sớm và tạo object nghiệp vụ sau.

## 2. Tách bốn mốc trong vòng đời container

Khi đọc `@Bean`, tránh gộp mọi thứ thành “Spring thấy bean”. Có ít nhất bốn sự kiện khác nhau:

| Mốc | Spring cần làm gì? | Có nhất thiết tạo target object chưa? |
| --- | --- | --- |
| **1. Nhận diện cấu hình** | Xử lý class `@Configuration`, `@Bean`, `@Import` hoặc cơ chế đăng ký khác. | Chưa. |
| **2. Đăng ký định nghĩa bean** | Tạo `BeanDefinition` chứa tên, factory method, scope, dữ liệu hỗ trợ dự đoán type. | Chưa. |
| **3. Dự đoán kiểu bean** | Xác định bean có phải `BeanFactoryPostProcessor`, `BeanPostProcessor`, loại bean cần autowire... | Thường có thể dựa metadata/return type; đôi khi không xác định được sớm. |
| **4. Khởi tạo instance** | Gọi static/instance factory method, inject dependency, thực hiện init callback/post-processing. | Có, lúc target thực sự được yêu cầu. |

Ví dụ:

```java
@Bean
static Formatter formatter() {
    return new Formatter();
}
```

Khi cấu hình được phân tích, Spring biết có một bean do `formatter` tạo ra và return type khai báo là `Formatter`. Điều đó **không yêu cầu `formatter()` chạy ngay khi đọc annotation**. Nếu đây là singleton non-lazy thông thường, Spring thường tạo instance trong giai đoạn pre-instantiation ở cuối refresh; nếu `@Lazy` thì có thể muộn hơn.

Sơ đồ tổng quát:

```text
Đăng ký @Configuration
         |
         v
ConfigurationClassPostProcessor phân tích @Bean
         |
         v
BeanDefinition có factory method + return type
         |
         +--> early type inspection
         |       +--> bean là BFPP / BPP?
         |       +--> resolve dependency bằng type?
         |
         v
Container quyết định thời điểm instantiate
         |
         +--> gọi static factory trực tiếp
         |       (không cần instance @Configuration)
         |
         +--> hoặc gọi instance factory trên bean @Configuration
                 (cần instance configuration phù hợp)
         |
         v
Áp dụng lifecycle / BeanPostProcessor / scope
```

Điểm chính: **Spring có thể cần biết type sớm hơn nhiều so với lúc tạo instance**, đặc biệt khi nhận diện processor tham gia khởi động container.

## 3. Cách một @Bean thông thường được gọi

Xét instance factory method:

```java
@Configuration
class JavaConfig {
    @Bean
    MessageCatalog messageCatalog() {
        return new MessageCatalog("hello");
    }
}
```

`MessageCatalog` là class ví dụ. Spring đăng ký method này làm nguồn tạo bean và thường cần một instance `JavaConfig` để gọi `messageCatalog()`.

Ngược lại:

```java
@Configuration
class JavaConfig {
    @Bean
    static MessageCatalog messageCatalog() {
        return new MessageCatalog("hello");
    }
}
```

Đây là **static factory method**; Spring có thể gọi `JavaConfig.messageCatalog()` mà không cần tạo instance `JavaConfig` chỉ để thực hiện factory method đó.

Một số chi tiết quan trọng:

- Cả hai vẫn tạo **Spring bean**, có tên, scope và lifecycle do container quản lý.
- Cả hai có thể được Spring nhận diện ở **giai đoạn bean definition**, trước khi method chạy.
- `static` chỉ nói về **cách gọi phương thức Java** và sự phụ thuộc vào instance class cấu hình; không thay đổi scope mặc định.
- Static method có thể nhận **tham số được Spring inject** như các `@Bean` factory method khác. Với BFPP/BPP, vẫn nên hạn chế dependencies để tránh kéo theo bean nghiệp vụ quá sớm.
- Một static `@Bean` method không được CGLIB override, nên không thể nhận semantics chặn lời gọi trực tiếp của các instance `@Bean` methods trong full `@Configuration`.

## 4. BeanFactoryPostProcessor cần được tạo sớm

`BeanFactoryPostProcessor` là extension point để đọc hoặc thay đổi **metadata của bean definitions** trước khi container tạo phần lớn bean ứng dụng.

```java
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;

public final class DefinitionInspector implements BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(
            org.springframework.beans.factory.config.ConfigurableListableBeanFactory factory) {
        System.out.println(factory.containsBeanDefinition("myService"));
    }
}
```

Đây là class `DefinitionInspector` có thể biên dịch với dependency `spring-beans` trên classpath. Nội dung callback chỉ quan sát một tên bean, không tạo bean service.

Vì BFPP cần chạy ở **pha rất sớm**, Spring phải tìm được nó **trước khi các singleton nghiệp vụ thông thường được tạo**. Nếu khai báo BFPP bằng instance `@Bean`:

```java
@Configuration
class InfrastructureConfig {
    @Bean
    BeanFactoryPostProcessor definitionInspector() {
        return beanFactory -> {
            // Can thiệp vào BeanDefinition tại startup.
        };
    }
}
```

thì để tạo BFPP, Spring có thể phải **khởi tạo `InfrastructureConfig` sớm** trước khi mọi cơ chế annotation injection/lifecycle đã hoàn tất. Điều này có thể ảnh hưởng tới `@Autowired`, `@Value`, `@PostConstruct` nằm ngay trên configuration class đó hoặc các dependency của nó.

**Cách thường nên dùng:**

```java
@Configuration
class InfrastructureConfig {
    @Bean
    static BeanFactoryPostProcessor definitionInspector() {
        return beanFactory -> {
            // Can thiệp vào BeanDefinition tại startup.
        };
    }
}
```

Factory method ở đây **static**, nên Spring có thể tạo BFPP mà không cần dựng instance `InfrastructureConfig`. Cách viết này đặc biệt hữu ích khi configuration còn có những factory methods nghiệp vụ sẽ được xử lý ở giai đoạn bình thường.

Javadoc `@Bean` của Spring 6.2 nêu rõ nên khai báo static cho các `@Bean` trả về `BeanFactoryPostProcessor`; cấu hình không static có thể có thông báo INFO về rủi ro lifecycle.

### 4.1. Vì sao không đọc @Autowired trong constructor của @Configuration quá sớm?

Một cách viết kém an toàn là để constructor hoặc field của configuration chứa dependency vào bean nghiệp vụ, rồi factory method BFPP cần instance configuration để chạy.

```java
@Configuration
class RiskyConfig {
    @Autowired
    private SomeApplicationService service;

    @Bean
    BeanFactoryPostProcessor earlyProcessor() {
        return factory -> {
            // Dễ kích hoạt vòng phụ thuộc/khởi tạo quá sớm.
        };
    }
}
```

Đây là snippet minh họa: `SomeApplicationService` là kiểu giả định. Vấn đề không chỉ là annotation `@Autowired` có hay không; **giai đoạn** tạo `RiskyConfig` có thể quá sớm so với thời điểm annotation processor sẵn sàng. Static BFPP method giảm nhu cầu tạo configuration instance sớm.

Tuy vậy, nếu static factory method lại khai báo một tham số dependency là service nghiệp vụ nặng, việc giải quyết tham số đó vẫn có thể tạo service sớm. `static` không bảo vệ tự động khỏi mọi initialization side effect.

## 5. BeanPostProcessor cũng cần được nhận diện trước các bean thông thường

`BeanPostProcessor` (BPP) xử lý **bean instances** trong lifecycle: ví dụ kiểm tra marker, inject/lifecycle metadata hoặc wrap object bằng proxy.

Một BPP cần được đăng ký **trước khi những bean mà nó phải xử lý được tạo**. Vì vậy Spring phải có cơ chế **phát hiện bean nào là BPP dựa trên metadata type**.

Một khai báo phù hợp:

```java
@Configuration
class ProcessingConfig {
    @Bean
    static BeanPostProcessor auditProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(
                    Object bean, String name) {
                return bean;
            }
        };
    }
}
```

Đoạn này cần import `org.springframework.beans.factory.config.BeanPostProcessor`, `Bean` và `Configuration`. Static factory tránh phải tạo instance configuration quá sớm.

**Hai nguyên tắc quan trọng, theo Spring 6.2 Javadoc:**

1. **Trả về kiểu thể hiện rõ tính chất BPP**: interface `BeanPostProcessor` hoặc implementation cụ thể của nó, không giấu sau `Object` hoặc interface không có quan hệ type phù hợp.
2. **Đánh dấu factory method là `static`**, lý tưởng **không có application-bean dependencies**, để tránh kéo các bean khác vào pha đăng ký post-processor.

Nếu BPP được tạo quá muộn, những bean đã được khởi tạo trước đó có thể **không còn cơ hội được xử lý bởi toàn bộ post-processors**, nhất là auto-proxying. Spring có thể cảnh báo kiểu “not eligible for getting processed by all BeanPostProcessors”.

### 5.1. Khai báo return type sai làm mất cơ hội phát hiện sớm

Ví dụ phản diện:

```java
@Bean
static Object auditProcessor() {
    return new MyAuditBeanPostProcessor();
}
```

`MyAuditBeanPostProcessor` là một implementation giả định của `BeanPostProcessor`. Vì declared return type chỉ là `Object`, container **không thể luôn xác định trước lúc tạo instance** rằng bean này là BPP. Spring có một số kỹ thuật dự đoán type bổ sung, nhưng không được phụ thuộc vào suy đoán tình cờ để hạ tầng quan trọng hoạt động đúng pha.

Nên viết:

```java
@Bean
static BeanPostProcessor auditProcessor() {
    return new MyAuditBeanPostProcessor();
}
```

hoặc khai báo chính class `MyAuditBeanPostProcessor` nếu kiểu public cụ thể phù hợp.

Với BFPP cũng vậy: khai báo kiểu `BeanFactoryPostProcessor` hoặc implementation cụ thể trong method signature. Một factory method trả kiểu `Object` rồi thực tế tạo BFPP sẽ khiến thông tin type sớm không rõ ràng.

### 5.2. @Bean static và BeanPostProcessor không phải bắt buộc tuyệt đối với mọi bean

Ví dụ một object đơn giản:

```java
@Bean
static Clock clock() {
    return Clock.systemUTC();
}
```

Đoạn này hợp lệ khi import `java.time.Clock`, nhưng dùng `static` không đem lại lợi ích lifecycle tương tự BFPP/BPP. Phần lớn bean nghiệp vụ có thể khai báo bằng instance `@Bean` với tham số injection. **Không cần chuyển mọi `@Bean` thành static**.

## 6. Ví dụ hoàn chỉnh Java 17+: quan sát type sớm và instance muộn

Chúng ta dùng **Spring Framework 6.2.9** với `AnnotationConfigApplicationContext`, không cần Spring Boot hay server. Ví dụ có:

- Một **static `@Bean BeanFactoryPostProcessor`** in kết quả quan sát ở pha xử lý metadata.
- Một **static `@Bean BeanPostProcessor`** theo dõi bean nghiệp vụ sau initialization.
- Một **static `@Bean Catalog`** là bean thông thường.
- Một **instance `@Bean Gateway`** nhận `Catalog` qua factory method parameter.
- Một constructor trên `Config` đếm số lần configuration instance được tạo.
- Một lời gọi Java trực tiếp tới static factory để minh họa nó **không phải lookup container**.

### 6.1. Cấu trúc dự án

```text
static-bean-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        └── java/
            └── demo/
                └── StaticBeanDemo.java
```

**`settings.gradle`**

```groovy
rootProject.name = 'static-bean-demo'
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
    mainClass = 'demo.StaticBeanDemo'
}
```

**`src/main/java/demo/StaticBeanDemo.java`**

```java
package demo;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class StaticBeanDemo {

    public static final class Catalog {
        private final String value;

        public Catalog(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public static final class Gateway {
        private final Catalog catalog;

        public Gateway(Catalog catalog) {
            this.catalog = catalog;
        }

        public String message() {
            return catalog.value() + "-gateway";
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {
        private static final AtomicInteger CREATED = new AtomicInteger();
        private static final AtomicInteger GATEWAYS_SEEN =
                new AtomicInteger();

        public Config() {
            CREATED.incrementAndGet();
        }

        // BFPP phải được nhận diện và tạo ở pha metadata.
        @Bean
        static BeanFactoryPostProcessor earlyInspector() {
            return factory -> {
                System.out.println("early:config-count="
                        + CREATED.get());
                System.out.println("early:catalog-definition="
                        + factory.containsBeanDefinition("catalog"));
                System.out.println("early:catalog-type="
                        + factory.getType("catalog").getSimpleName());
                System.out.println("early:catalog-instantiated="
                        + factory.containsSingleton("catalog"));
            };
        }

        // Return type giúp Spring nhận diện BPP trước khi tạo Gateway.
        @Bean
        static BeanPostProcessor observer() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(
                        Object bean, String beanName) {
                    if ("gateway".equals(beanName)) {
                        GATEWAYS_SEEN.incrementAndGet();
                    }
                    return bean;
                }
            };
        }

        // Đối tượng bình thường cũng có thể được tạo từ static @Bean.
        @Bean
        static Catalog catalog() {
            return new Catalog("demo");
        }

        // Factory method instance; dependency được inject qua tham số.
        @Bean
        Gateway gateway(Catalog catalog) {
            return new Gateway(catalog);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            Catalog first = context.getBean(Catalog.class);
            Catalog second = context.getBean(Catalog.class);
            Gateway gateway = context.getBean(Gateway.class);

            System.out.println("context-ready");
            System.out.println("config-count=" + Config.CREATED.get());
            System.out.println("gateway=" + gateway.message());
            System.out.println("bpp-saw-gateway="
                    + (Config.GATEWAYS_SEEN.get() == 1));
            System.out.println("managed-catalog-same=" + (first == second));

            Catalog direct = Config.catalog();
            System.out.println("direct-call-is-managed=" + (direct == first));
        }
    }
}
```

Chương trình đặt `Config` dưới dạng static nested class để Spring có thể đăng ký nó rõ ràng. `Config.CREATED` được tăng chỉ khi **instance Config** được tạo; gọi static factory `earlyInspector()`, `observer()` hoặc `catalog()` không tự tăng biến đếm.

### 6.2. Chạy

Từ thư mục `static-bean-demo`:

```bash
gradle run --quiet
```

Nếu dự án có Gradle Wrapper, có thể dùng `./gradlew run --quiet` trên Linux/macOS hoặc `gradlew.bat run --quiet` trên Windows. Lệnh `javac StaticBeanDemo.java` đơn lẻ không đủ nếu chưa tự cung cấp Spring jars lên classpath; tác vụ Gradle sẽ xử lý dependencies và classpath.

**Các dòng `System.out.println` của chương trình:**

```text
early:config-count=0
early:catalog-definition=true
early:catalog-type=Catalog
early:catalog-instantiated=false
context-ready
config-count=1
gateway=demo-gateway
bpp-saw-gateway=true
managed-catalog-same=true
direct-call-is-managed=false
```

Chương trình không có thread tạo log riêng; log Gradle hoặc logging framework có thể xuất hiện tùy cấu hình môi trường nhưng không thuộc mười dòng trên.

### 6.3. Giải thích các dòng output

**`early:config-count=0`.** Container tạo `earlyInspector` từ **static `@Bean`** mà không cần khởi tạo instance `Config`. Vì BFPP chạy ở pha sớm, số lần constructor `Config()` được gọi vẫn bằng 0.

**`early:catalog-definition=true`.** Metadata của `@Bean Catalog catalog()` đã được đăng ký như bean definition khi BFPP quan sát registry. **Đăng ký definition không đồng nghĩa target đã được khởi tạo.**

**`early:catalog-type=Catalog`.** Spring có thể dự đoán bean type từ chữ ký static factory method `static Catalog catalog()`. Phương thức `getType("catalog")` trả kiểu dự kiến mà **không cần gọi chính factory method để tạo Catalog**.

**`early:catalog-instantiated=false`.** Trong thời điểm đó `catalog` chưa tồn tại trong singleton cache. Điều này tách rõ **type visibility** với **instance availability**.

**`config-count=1`.** Trong quá trình refresh, container tạo `Config` để gọi factory method instance `gateway(Catalog)`. Static `@Bean` methods đã có thể phục vụ nhu cầu sớm mà không kéo theo instance cấu hình.

**`bpp-saw-gateway=true`.** BeanPostProcessor `observer` đã được tạo/đăng ký trước khi bean `gateway` hoàn tất initialization, nên callback `postProcessAfterInitialization` nhận `gateway` đúng một lần trong ví dụ.

**`managed-catalog-same=true`.** `catalog` là bean singleton mặc định; hai lần `getBean` trả cùng instance trong context.

**`direct-call-is-managed=false`.** Lệnh `Config.catalog()` là **lời gọi static Java trực tiếp**, tạo đối tượng mới không qua Spring container. Static `@Bean` methods không được CGLIB intercept; chỉ lời gọi từ bean factory mới chịu quản lý scope và lifecycle.

> Kết quả minh họa đúng hai trục riêng: **đã biết bean type** không có nghĩa **đã tạo object**, và **static factory method** không có nghĩa kết quả của mọi lời gọi Java tới method ấy đều được container quản lý.

## 7. Tại sao return type phải “lộ” đúng kiểu cho Spring?

Xét factory method:

```java
@Bean
static Object earlyProcessor() {
    return new MySpecialBeanPostProcessor();
}
```

`MySpecialBeanPostProcessor` là class ví dụ implement `BeanPostProcessor`. Container nhìn vào metadata và biết chắc return type tối thiểu chỉ là `Object`. Trước khi instance thực sự tồn tại, Spring có thể **chưa biết** đó là một BPP cần đăng ký trước những bean khác.

Đổi chữ ký:

```java
@Bean
static BeanPostProcessor earlyProcessor() {
    return new MySpecialBeanPostProcessor();
}
```

thì container có bằng chứng từ khai báo rằng bean thuộc loại `BeanPostProcessor`. Khi Spring tìm BPPs sớm, bean definition dễ được nhận diện đúng thời điểm hơn.

**Quy tắc này áp dụng rộng hơn BPP/BFPP:**

```java
@Bean
PaymentGateway gateway() {
    return new DefaultPaymentGateway();
}
```

Nếu code inject `PaymentGateway` ở các bean khác, declared type đã đủ. Nhưng nếu code inject `DefaultPaymentGateway` trước khi bean được tạo và method chỉ khai báo `PaymentGateway`, Spring có thể chưa xác định được concrete type trong quá trình **type prediction**. Một số ứng viên chỉ trở nên rõ khi singleton thực sự đã tạo xong; kết quả autowiring có thể phụ thuộc thời điểm và thứ tự khởi tạo.

Vì vậy, Spring khuyên khai báo factory method return type **cụ thể ít nhất tới mức các injection point thật sự cần**. Không nhất thiết luôn khai báo concrete class nếu hệ thống nhất quán dùng interface; nhưng tránh `Object` cho bean quan trọng và tránh cố ý che loại extension point cần container tìm sớm.

### 7.1. Trường hợp FactoryBean<T> khác với factory method @Bean

Hai tên gần nhau dễ nhầm:

- **`@Bean` factory method:** phương thức tạo object Spring bean.
- **`FactoryBean<T>`:** một interface đặc biệt mà implementation tạo **product object** khi container truy xuất bean; muốn lấy chính factory thường dùng tiền tố `&` trong bean name.

Giả sử:

```java
@Bean
FactoryBean<MyClient> myClientFactoryBean() {
    return new MyClientFactoryBean();
}
```

Đây là snippet cấu trúc, giả định `MyClientFactoryBean implements FactoryBean<MyClient>` và `MyClient` là class nghiệp vụ.

Container có thể dùng thông tin generic `FactoryBean<MyClient>` và `getObjectType()` của factory để suy ra type của **product bean**. Nếu return type của `@Bean` chỉ là `FactoryBean` raw type hoặc `Object`, type prediction có thể khó hơn. Trong một số trường hợp, việc xác định object type của `FactoryBean` còn phụ thuộc metadata/cách triển khai `getObjectType()`, và Spring có thể phải phân biệt lookup không eager (`allowEagerInit=false`) với lookup được phép tạo factory.

**Điểm cần nhớ:** đây là lớp dự đoán type bổ sung, không phải vì `@Bean static` nên mọi product type tự động luôn được xác định sớm.

## 8. static không tự làm bean thành eager hay singleton JVM

Ba phát biểu dưới đây đều sai:

1. “Phương thức `@Bean static` sẽ chạy ngay khi class được load.”
2. “Một static `@Bean` luôn chỉ được gọi một lần trong cả JVM.”
3. “Một static `@Bean` không thể tạo prototype.”

### 8.1. Bean definition khác với class initialization

Java có thể tải class và xử lý static initializer theo quy tắc JVM, còn Spring có vòng đời bean riêng. `@Bean static` không có nghĩa Spring gọi method đó ở thời điểm JVM load class.

Một static initializer:

```java
static {
    System.out.println("java-class-init");
}
```

là cơ chế Java, hoàn toàn khác với việc Spring gọi method mang `@Bean` khi khởi tạo bean. Đừng dùng static initializer để gián tiếp thay thế BeanFactory lifecycle logic.

### 8.2. Scope vẫn do bean definition quyết định

```java
@Bean
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
static TaskToken taskToken() {
    return new TaskToken();
}
```

Đây là snippet minh họa: `TaskToken` là class ví dụ, cần import `Scope` và `ConfigurableBeanFactory`. Container có thể gọi static factory **mỗi lần cần một prototype mới**. Ngược lại, một static `@Bean` singleton chỉ được container tạo theo chính sách singleton của **một context**, không phải biến static toàn JVM.

**Quan trọng:** nếu code ngoài Spring gọi thẳng `Config.taskToken()` thì lời gọi Java đó không tuân theo bean-scope lookup; nó thực thi factory method trực tiếp.

### 8.3. @Lazy vẫn có ý nghĩa đối với singleton

```java
@Bean
@Lazy
static HeavyClient heavyClient() {
    return new HeavyClient();
}
```

`HeavyClient` là kiểu minh họa. Với singleton lazy, container có thể trì hoãn thực thi method đến khi bean được yêu cầu. Việc method là static không tự buộc bean tạo ở refresh. Nếu một eager bean khác phụ thuộc vào `HeavyClient`, Spring vẫn có thể cần tạo client sớm để hoàn thành dependency.

BFPP/BPP là **trường hợp đặc biệt** cần khởi tạo sớm để can thiệp lifecycle; chúng không giống các singleton nghiệp vụ thông thường.

## 9. static @Bean có được inter-bean method interception không?

Trong full `@Configuration(proxyBeanMethods=true)`, Spring có thể dùng CGLIB subclass để chặn các lời gọi **instance** `@Bean` methods giữa chúng, bảo đảm semantics truy xuất bean qua container cho các lời gọi đó.

Nhưng **Java static methods không thể override bởi subclass**, nên static `@Bean` methods **không được chặn**, kể cả khi class cấu hình bật `proxyBeanMethods=true`.

Ví dụ:

```java
@Configuration
class CatalogConfig {
    @Bean
    static Catalog catalog() {
        return new Catalog("demo");
    }

    @Bean
    Gateway gateway() {
        return new Gateway(catalog()); // Lời gọi Java trực tiếp.
    }
}
```

`Catalog`/`Gateway` là hai class từ ví dụ chạy được ở mục 6. Method `gateway()` gọi static `catalog()` và có thể tạo object mới, **không lấy singleton bean đã được quản lý**. Việc bật enhancement cho instance methods của `CatalogConfig` không sửa được tính chất static của `catalog()`.

**Cách đúng để ghép bean:**

```java
@Configuration(proxyBeanMethods = false)
class CatalogConfig {
    @Bean
    static Catalog catalog() {
        return new Catalog("demo");
    }

    @Bean
    Gateway gateway(Catalog catalog) {
        return new Gateway(catalog);
    }
}
```

Spring resolve `Catalog` trong tham số theo bean definition và scope. Cách này hoạt động rõ ràng cả với static và instance `@Bean`, giảm sự phụ thuộc vào proxy/configuration enhancement.

### 9.1. @Configuration(proxyBeanMethods=false) và @Component lite mode

Khi `@Configuration(proxyBeanMethods=false)`, ngay cả lời gọi **instance** giữa các `@Bean` methods cũng có semantics Java thông thường, thay vì chặn qua CGLIB như full mode.

Các phương thức `@Bean` đặt trong class `@Component` (không phải full `@Configuration`) cũng thường được xử lý theo **lite mode** và không nhận interception của full configuration. Vì vậy thói quen dùng **factory method parameters** tốt hơn dựa vào việc gọi trực tiếp method khác.

### 9.2. Không lẫn static @Bean với static field giữ state

`@Bean static Catalog catalog()` là **static method**. Nó không nhất thiết có static field chứa singleton `Catalog`. Spring có thể quản lý nhiều `ApplicationContext` trong một JVM và tạo một singleton `catalog` khác nhau cho từng context; nếu sử dụng static field mutable để cache object toàn cục thì đó là cơ chế riêng, dễ tạo rò rỉ trạng thái/test contamination.

## 10. Hai lỗi startup thường liên quan return type và pha early initialization

### 10.1. BPP giấu dưới Object

```java
@Bean
static Object metricsBean() {
    return new MetricsBeanPostProcessor();
}
```

Nếu `MetricsBeanPostProcessor` implement `BeanPostProcessor` nhưng method chỉ khai báo `Object`, quá trình Spring tìm kiếm BPP theo type có thể bỏ sót bean trước khi nó được instantiate.

**Sửa:**

```java
@Bean
static BeanPostProcessor metricsBean() {
    return new MetricsBeanPostProcessor();
}
```

Đây là snippets minh họa; implementation cần được định nghĩa. Spring docs khuyên khai báo return type là implementation cụ thể hoặc interface `BeanPostProcessor` ít nhất, để early type detection hoạt động tin cậy.

### 10.2. Non-static @Bean tạo BPP làm @Configuration khởi tạo quá sớm

```java
@Configuration
class RiskyConfig {
    @Autowired
    SomeApplicationService service;

    @Bean
    BeanPostProcessor processor() {
        return new CustomProcessor();
    }
}
```

`SomeApplicationService` và `CustomProcessor` là kiểu minh họa. Để tạo BPP qua instance method, Spring phải tạo `RiskyConfig` trước; điều đó có thể kéo theo application service sớm. Bean này có thể **không nhận đầy đủ các post-processor** và Spring có thể ghi WARN rằng bean “not eligible for getting processed by all BeanPostProcessors”.

**Sửa:**

```java
@Configuration
class SaferConfig {
    @Bean
    static BeanPostProcessor processor() {
        return new CustomProcessor();
    }
}
```

Và không inject application beans nặng vào chính processor nếu không cần thiết.

**Không hiểu sai:** biến method thành static không bảo đảm mọi cấu hình sai lập tức được sửa. Nếu custom processor vẫn chủ động `getBean("service")` trong constructor/khởi tạo thì có thể tạo service quá sớm.

## 11. Phát hiện kiểu sớm khác “tạo trước” và “tiêm trước”

Để dự đoán chính xác, cần phân biệt ba API/khả năng:

- `BeanFactory.getType(beanName)`: cố gắng tìm type của bean sẽ trả về khi lookup, có thể dựa metadata, factory method hoặc cơ chế `FactoryBean`. Một số type không đoán chắc được trước khi instance tồn tại.
- `BeanFactory.containsBeanDefinition(beanName)`: cho biết có definition được đăng ký trực tiếp trong bean factory hiện tại, **không nói instance đã tạo**.
- `SingletonBeanRegistry.containsSingleton(beanName)`: kiểm tra cache singleton hiện tại, **không phải kiểm tra bean definition**.

Trong ví dụ chạy được, kết quả đầu startup:

```text
early:catalog-definition=true
early:catalog-type=Catalog
early:catalog-instantiated=false
```

hoàn toàn không mâu thuẫn. Spring biết cách tạo `Catalog` và dự đoán kiểu của nó, nhưng chưa tạo instance. Nếu có một injection point cần `Catalog` trong phase sớm, việc resolve dependency có thể kích hoạt tạo bean và làm trạng thái thứ ba đổi sang true.

### 11.1. Return type quá tổng quát

Giả sử:

```java
@Bean
PaymentProcessor paymentProcessor() {
    return new StripePaymentProcessor();
}
```

`PaymentProcessor` và `StripePaymentProcessor` là kiểu nghiệp vụ giả định. Metadata bảo đảm bean ít nhất đáp ứng interface `PaymentProcessor`. Trước khi factory method chạy, container không phải lúc nào cũng biết concrete implementation để match injection point yêu cầu `StripePaymentProcessor`.

Nếu Spring buộc xác định trước singleton được tạo, một component inject concrete type có thể không được chọn. Ngược lại, khi singleton đã được instantiate, type thực tế có thể được biết và dẫn đến kết quả matching khác. Vì vậy:

- Nếu consumer chỉ cần interface, inject `PaymentProcessor` và method trả interface là rõ ràng.
- Nếu thật sự cần concrete type, cân nhắc return type là `StripePaymentProcessor`, cùng với thiết kế API và proxying hợp lệ.
- Với extension points của Spring, bắt buộc để lộ interface/cụ thể thích hợp (ví dụ `BeanPostProcessor`), không dùng `Object`.

### 11.2. Không “probe” bean bằng getBean() sớm chỉ để biết type

Một BFPP không nên gọi `getBean()` của bean nghiệp vụ chỉ để xem class hay thuộc tính. Làm vậy có thể kích hoạt instantiation quá sớm, trước khi đầy đủ BPP/advisors được đăng ký, gây hành vi sai hoặc proxy bị bỏ lỡ.

Ưu tiên đọc `BeanDefinition`, `getType()` và thông tin metadata; nếu cần xác định FactoryBean product type, cân nhắc đúng API và tác dụng phụ của eager initialization.

## 12. Khai báo post-processor rõ ràng trong dự án thực

Đối với BFPP:

```java
@Configuration(proxyBeanMethods = false)
class InfrastructureConfig {

    @Bean
    static BeanFactoryPostProcessor definitionPolicy() {
        return beanFactory -> {
            // Chỉnh metadata đã đăng ký, nếu thật sự cần.
        };
    }
}
```

Đối với BPP:

```java
@Configuration(proxyBeanMethods = false)
class InfrastructureConfig {

    @Bean
    static BeanPostProcessor decoratorProcessor() {
        return new MyDecoratorPostProcessor();
    }
}
```

`MyDecoratorPostProcessor` là class minh họa cần implement `BeanPostProcessor`. Hai ví dụ là các cấu hình độc lập; trong code thật có thể đặt chung nếu tên methods khác nhau và imports đúng.

**Checklist hạ tầng:**

1. Phương thức trả type cụ thể hoặc ít nhất `BeanFactoryPostProcessor`/`BeanPostProcessor`.
2. Phương thức nên `static` để tránh tạo instance `@Configuration` sớm.
3. Hạn chế tối đa tham số là bean nghiệp vụ.
4. Nếu processor cần property/source thông thường, xem nó có thể dùng metadata/`Environment`/giá trị cấu hình sớm và an toàn hay không; đừng buộc client/network/service khởi tạo trước chu trình BPP hoàn tất.
5. Nếu processor tạo proxy, kiểm tra bean nào thực sự đủ điều kiện nhận proxy và thứ tự các post-processor liên quan.
6. Viết integration test tạo context nhỏ và quan sát bean definition, type prediction, callback và thứ tự sự kiện quan trọng.

### Khi nào không nên viết post-processor tùy chỉnh?

Hầu hết ứng dụng không cần tự tạo BFPP/BPP. Spring đã có nhiều hạ tầng sẵn cho property placeholders, autowiring, lifecycle, transaction, caching, async và validation. Chỉ viết processor mới khi bạn có yêu cầu can thiệp vào metadata hoặc bean instance mà không thể diễn đạt bằng cấu hình sẵn, và bạn hiểu lifecycle để tránh early-instantiation bugs.

## 13. Thực hành biến thể từ ví dụ

### 13.1. Đổi static Catalog sang instance @Bean

Trong `StaticBeanDemo.Config`, thay:

```java
@Bean
static Catalog catalog() {
    return new Catalog("demo");
}
```

bằng:

```java
@Bean
Catalog catalog() {
    return new Catalog("demo");
}
```

BFPP `earlyInspector` vẫn được tạo từ static method và có thể nhận diện kiểu trả về của `catalog` dựa vào metadata configuration/factory method khi Spring đã phân tích cấu hình. Tuy nhiên, việc **tạo Catalog** giờ cần instance `Config`, còn trước đó không cần instance đó. Đây là khác biệt về **phương thức instantiate**, không phải quy tắc scope.

Không gắn kỳ vọng cứng rằng mọi cách probe type trên một instance factory ở mọi biến thể đều hoàn toàn không tạo configuration instance: kết quả có thể phụ thuộc hạ tầng dự đoán method/type, cách factory bean được định nghĩa và thời điểm truy vấn.

### 13.2. Đổi return type của BPP thành Object

Thay `static BeanPostProcessor observer()` thành `static Object observer()`. Về Java, method vẫn trả instance implementing BPP, nhưng Spring có thể không phát hiện bean này như `BeanPostProcessor` trong lượt tìm kiếm **trước** initialization, khiến `bpp-saw-gateway` không còn là bằng chứng được bảo đảm.

Thí nghiệm này chỉ để thấy tác dụng của type metadata; **không dùng kiểu Object trong ứng dụng thật** nếu muốn Spring tự nhận diện processor.

### 13.3. Đổi static BFPP thành instance BFPP

Thay `static BeanFactoryPostProcessor earlyInspector()` bằng instance method. Vì Spring phải gọi method trên `Config`, việc này có thể làm `Config.CREATED` tăng lên trước callback BFPP, khiến `early:config-count` không còn là 0 và sinh lifecycle warning. Đây là minh họa tại sao khuyến nghị static tồn tại; không coi việc thay đổi output là “Spring bug”.

### 13.4. Biến Catalog thành prototype

Thêm `@Scope("prototype")` vào static `@Bean Catalog catalog()`. Khi ấy mỗi lần `context.getBean(Catalog.class)` thông thường tạo instance mới, dù method vẫn static. `managed-catalog-same` sẽ trở thành false. Với `Gateway` singleton, catalog được inject trong `gateway(Catalog)` vẫn là **một instance cụ thể** tại thời điểm tạo Gateway; nó không tự thay đổi theo từng lần gọi `message()`.

### 13.5. Gọi trực tiếp Catalog factory method

Trong `main` đã có:

```java
Catalog direct = Config.catalog();
System.out.println(direct == context.getBean(Catalog.class));
```

Kết quả false trong cấu hình demo vì lời gọi trực tiếp tạo object mới. Đó là **Java call**, không phải Spring bean lookup. Với các phương thức static trả singleton cache tự viết thì Java call có thể tình cờ trả cùng object, nhưng chuyện đó do code factory quyết định và **không** được container bảo đảm.

## 14. Những nhầm lẫn thường gặp

### Sai lầm 1: “static @Bean được tạo ngay lúc startup class”

`static` cho phép gọi method không cần instance class cấu hình. Thời điểm **Spring gọi method** vẫn do bean lifecycle/scope/early-infrastructure quyết định; chỉ riêng static không tạo eager behavior.

### Sai lầm 2: “Static @Bean chỉ có một singleton trên toàn JVM”

Scope singleton thuộc về **mỗi ApplicationContext**, không phải static Java class. Hai context khác nhau có thể gọi cùng static factory và nhận hai bean singleton khác nhau. Khi tự tạo object bằng static method call còn có thể nhận instance hoàn toàn nằm ngoài context.

### Sai lầm 3: “Mọi @Bean nên static để tăng tốc”

Static không tự tối ưu mọi application bean. Instance `@Bean` với constructor/factory-method injection thường đủ rõ. Lợi ích rõ nhất của static là **tránh khởi tạo sớm configuration class khi phải tạo infrastructure bean BFPP/BPP**.

### Sai lầm 4: “Tôi trả Object vẫn ổn vì runtime object là BeanPostProcessor”

Container cần phát hiện BPP **trước khi** runtime object tồn tại. Declared return type quá rộng có thể khiến bean đặc biệt không được đăng ký vào hệ xử lý đúng pha.

### Sai lầm 5: “Có return type đúng thì bean đã được tạo”

Bean definition và type prediction không đồng nghĩa đã instantiate singleton. Dùng `containsSingleton` hoặc quan sát factory/callback để phân biệt thời điểm.

### Sai lầm 6: “Static @Bean hưởng interception nhờ @Configuration”

Static method không được override; direct call tới static factory thực hiện Java bình thường. Chỉ lời gọi do Spring bean factory điều phối mới tuân theo bean scope/lifecycle.

### Sai lầm 7: “Non-static BFPP không thể chạy”

Nó **có thể** chạy nhưng tạo configuration bean sớm và có nguy cơ gây lỗi lifecycle. Spring khuyến nghị static để tránh rủi ro, không phải vì Java cấm instance factory method trả BFPP.

### Sai lầm 8: “Static BPP có dependency vào application service vẫn an toàn”

Factory có thể không cần instance `@Configuration`, nhưng khi Spring resolve tham số dependency, service vẫn có thể bị kéo vào pha sớm. Ưu tiên processor không kéo theo những bean cần proxy/lifecycle processing.

### Sai lầm 9: “Dùng getType() thì chắc chắn không bao giờ có side effect”

Trong trường hợp phổ biến, return type đủ cụ thể giúp Spring dự đoán type qua metadata. Nhưng FactoryBean, proxy, custom factory metadata hoặc lựa chọn eager-init có thể làm kết quả phức tạp hơn. Đừng biến `getType()` thành lời cam kết tuyệt đối rằng không có bean nào bị tạo.

### Sai lầm 10: “Spring nhìn chữ static là biết bean là BFPP/BPP”

Không. Để biết loại bean đặc biệt, Spring cần **thông tin type** phù hợp và bean definition được đăng ký đúng pha. `static` giải quyết **khả năng gọi factory không cần configuration instance**, còn **return type** giải quyết **khả năng nhận diện bean theo kiểu**. Hai thuộc tính phục vụ hai nhu cầu khác nhau.

## 15. Cách debug trong ứng dụng thật

Khi BPP không hoạt động hoặc có cảnh báo bean không đủ điều kiện auto-proxy, kiểm tra:

- Method `@Bean` có khai báo **static** hay Spring buộc tạo configuration instance sớm?
- Return type trên method có **đủ cụ thể** (BFPP/BPP/implementation) để nhận diện trước instantiation?
- Processor có field/constructor/factory parameters yêu cầu **bean nghiệp vụ** không?
- Bean nghiệp vụ có bị yêu cầu bằng `getBean()` trong BFPP hoặc constructor configuration/BPP không?
- Có processor nào được đăng ký rất muộn, sau khi service đã tạo không?
- Cấu hình có đang chạy ở `AnnotationConfigApplicationContext`, web child context hay Spring Boot context nào khác?
- Có tạo bean thủ công bằng `new` hoặc gọi static factory trực tiếp thay vì lấy từ container không?

Kiểm tra bean definition/type khi context đã được nạp:

```java
ConfigurableListableBeanFactory factory = context.getBeanFactory();

System.out.println(factory.containsBeanDefinition("catalog"));
System.out.println(factory.getType("catalog"));
System.out.println(factory.containsSingleton("catalog"));
```

Đây là snippet cần import `org.springframework.beans.factory.config.ConfigurableListableBeanFactory` và biến `context` từ ví dụ. Để quan sát **trước instantiation**, đặt nó trong BFPP giống demo; chạy sau `refresh()` thường thấy bean singleton đã được tạo.

Nếu Spring ghi thông báo rằng một bean không đủ điều kiện được **mọi** BeanPostProcessor xử lý, xem những component nào buộc phải tạo sớm và loại bỏ dependency/call path đó. Không nên tắt toàn bộ auto-proxying hay che log để giải quyết triệu chứng.

## 16. Kiểm thử hành vi sớm mà không suy đoán thứ tự ngoài hợp đồng

Một test integration có thể dựng `AnnotationConfigApplicationContext` chỉ với cấu hình hạ tầng cần kiểm tra, rồi sử dụng recorder/counter:

1. BFPP ghi số lần configuration instance được tạo **khi callback chạy**; với static factory không cần config instance, nó có thể là 0 trong fixture đơn giản.
2. BFPP xem bean definition và `getType()` khi bean đích chưa được tạo.
3. BPP ghi các tên bean đã nhận tại `postProcessAfterInitialization`.
4. Sau `refresh()`, test kiểm tra bean singleton và dependency injection.
5. Một lời gọi trực tiếp static factory method được so với `context.getBean(...)` để chứng minh khác biệt.

Ví dụ kiểm tra trong chương trình chính:

```java
if (Config.GATEWAYS_SEEN.get() != 1) {
    throw new AssertionError("Gateway was not observed by BPP");
}
```

Đoạn trên dùng `Config` của `StaticBeanDemo`, chỉ hợp lệ khi đặt trong outer class/nơi có quyền đọc field theo luật Java. Điều kiện này bảo vệ kỳ vọng quan trọng là bean mục tiêu được BPP nhận. Khi test production, nên dùng JUnit assertions và fixture tách biệt, tránh phụ thuộc những log chưa được bảo đảm ổn định bởi Spring.

**Cảnh báo về experiment:** nếu đổi BFPP/BPP của fixture từ static sang instance hoặc đổi declared return type thành `Object`, đừng tiếp tục áp nguyên output gốc như chuẩn tuyệt đối. Mục đích của biến thể là quan sát **khác biệt lifecycle**, không tìm cách làm nó vẫn giống hệt bản đúng.

## 17. Câu hỏi tự luyện và đáp án

**Câu 1.** Static `@Bean` trả `Catalog` có bắt buộc tạo Catalog trước mọi bean khác không?

**Đáp:** Không. Static chỉ cho phép factory method chạy mà không cần instance cấu hình. Thời điểm tạo Catalog phụ thuộc scope, lazy và dependency.

**Câu 2.** Vì sao BFPP `@Bean` thường nên static?

**Đáp:** BFPP được container tìm và tạo rất sớm. Static factory có thể được gọi mà không khởi tạo configuration class sớm, nhờ đó tránh một số xung đột với annotation injection/lifecycle.

**Câu 3.** Vì sao BPP factory method không nên khai báo return type `Object`?

**Đáp:** Container cần phát hiện BPP theo type trước khi tạo bean bình thường. Return type quá rộng có thể khiến type prediction không nhận ra nó là BPP.

**Câu 4.** Static `@Bean` có phải static singleton toàn JVM không?

**Đáp:** Không. Spring scope mặc định singleton thuộc về mỗi context. Direct Java method invocation vẫn có thể tạo object độc lập.

**Câu 5.** Lời gọi `Config.catalog()` và `context.getBean(Catalog.class)` khác nhau thế nào?

**Đáp:** Lời gọi đầu là Java static call thông thường, không áp dụng Spring scope/lifecycle. Lời gọi thứ hai lấy bean do container quản lý.

**Câu 6.** Trong demo, vì sao `early:catalog-type=Catalog` và `early:catalog-instantiated=false` cùng đúng?

**Đáp:** Container đã có definition với return type xác định được nhưng chưa chạy factory để tạo singleton target.

**Câu 7.** Return type `PaymentGateway` trả về implementation `StripePaymentGateway` có vấn đề gì?

**Đáp:** Không vấn đề nếu consumers chỉ cần `PaymentGateway`. Nếu một consumer yêu cầu concrete implementation ngay ở pha type matching sớm, container có thể chưa nhận diện type đó trước khi instance được tạo.

**Câu 8.** Đổi static BFPP sang non-static có luôn ném exception không?

**Đáp:** Không nhất thiết. Nhưng có thể buộc tạo configuration instance sớm và phát sinh INFO/warning hoặc lifecycle side effect; dùng static theo khuyến nghị Spring.

**Câu 9.** BPP static method nhận dependency `MyService` qua tham số có hoàn toàn tránh early bean creation không?

**Đáp:** Không. Spring có thể phải khởi tạo `MyService` để cung cấp tham số, gây đúng rủi ro mà static định tránh. Hạn chế application dependencies cho early processors.

**Câu 10.** Phân biệt `BeanFactoryPostProcessor` và `BeanPostProcessor`?

**Đáp:** BFPP xử lý bean definition/metadata trước khi tạo phần lớn bean ứng dụng. BPP xử lý bean instance trong quá trình initialization và có thể wrap/proxy instance.

## 18. Tài liệu chính thức

- [Spring Framework 6.2 — @Bean Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Bean.html): static BFPP/BPP factory methods, type visibility, full/lite mode, scoping và lifecycle.
- [Spring Framework 6.2 — BeanPostProcessor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/config/BeanPostProcessor.html): giai đoạn đăng ký BPP, khuyến nghị static factory.
- [Spring Framework — Container Extension Points](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html): BFPP/BPP, phát hiện type sớm, warning về early initialization.
- [Spring Framework — Using @Bean Annotation](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html): return type của factory method và type prediction.
- [Spring Framework — Using @Configuration Annotation](https://docs.spring.io/spring-framework/reference/core/beans/java/configuration-annotation.html): full configuration, inter-bean calls và proxy semantics.
- [Spring Framework — Bean Overview](https://docs.spring.io/spring-framework/reference/core/beans/definition.html): bean definitions, factory method và `BeanFactory.getType`.

**Điểm cốt lõi:** `static @Bean` giúp Spring tạo một bean **không phải instantiate class `@Configuration` trước**, đặc biệt quan trọng cho **BFPP/BPP cần được tạo rất sớm**. **Declared return type** giúp container **nhận diện bean theo kiểu trước lúc nó được tạo**. Cả hai yếu tố bổ sung nhau: static giải quyết thời điểm gọi factory, return type giải quyết khả năng tìm đúng extension point. Không yếu tố nào tự biến mọi bean thành eager hoặc singleton toàn JVM.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-configuration-006">Chế độ đầy đủ của @Configuration và lớp tăng cường CGLIB</a>

<details>
<summary>Xem chi tiết</summary>

# Chế độ đầy đủ của `@Configuration` và lớp tăng cường CGLIB trong Spring Framework

## 1. Vấn đề: gọi phương thức `@Bean` có phải luôn tạo object mới?

Hãy xem một lớp cấu hình Java:

```java
@Configuration
class ApplicationConfig {

    @Bean
    Engine engine() {
        return new Engine();
    }

    @Bean
    Vehicle car() {
        return new Vehicle(engine());
    }

    @Bean
    Vehicle truck() {
        return new Vehicle(engine());
    }
}
```

Đây là **đoạn minh họa**; `Engine` và `Vehicle` là class ứng dụng chưa khai báo. Trong Java thông thường, hai lời gọi `engine()` bên trong `car()` và `truck()` sẽ chạy thân method hai lần và tạo hai object `new Engine()` khác nhau. Nhưng khi `ApplicationConfig` được Spring xử lý như một lớp `@Configuration` **đầy đủ (full mode)**, các lời gọi qua lại giữa phương thức `@Bean` được can thiệp: Spring có thể chuyển hướng sang BeanFactory, nhờ đó cả `car` và `truck` cùng nhận **bean `engine` singleton**.

Cơ chế đó là **CGLIB enhancement**: Spring tạo một lớp con runtime của configuration class rồi chặn các lời gọi phù hợp tới `@Bean` methods. Đây là hành vi được bật mặc định bởi:

```java
@Configuration(proxyBeanMethods = true)
```

Thông thường chỉ cần viết `@Configuration` vì `proxyBeanMethods` mặc định là `true`.

Tuy nhiên, khi đổi sang:

```java
@Configuration(proxyBeanMethods = false)
```

Spring **không tăng cường các `@Bean` methods để chặn lời gọi trực tiếp**. Lời gọi `engine()` từ `car()` là lời gọi Java thông thường, có thể tạo thêm object **không phải** singleton bean `engine` do container đăng ký. Cả hai chế độ đều cho phép Spring xử lý `@Bean` và đăng ký các beans; khác biệt nằm ở **ngữ nghĩa của lời gọi method Java**, không phải ở việc `@Bean` có còn hoạt động hay không.

Bài học tập trung vào:

- Full `@Configuration`, lớp con CGLIB được sinh động và việc gọi giữa các `@Bean` methods;
- cơ chế singleton/scope, vòng đời và AOP khi tham chiếu bean qua phương thức;
- giới hạn của full mode (class/method `final`, `private`, `static`), so với lite mode;
- lựa chọn `proxyBeanMethods=false` và dependency injection qua tham số `@Bean`;
- cách không nhầm configuration enhancement với Spring AOP proxy;
- ví dụ Java 17+ thực chạy với Spring Context, phân tích stdout, lỗi thường gặp và bài tập.

## 2. `@Configuration` là nguồn bean definitions

`@Configuration` thuộc package `org.springframework.context.annotation`. Nó đánh dấu một class chứa thông tin cấu hình Java, thường thông qua các method gắn `@Bean`:

```java
@Configuration
public class BillingConfiguration {

    @Bean
    PaymentClient paymentClient() {
        return new PaymentClient("local");
    }
}
```

Đoạn này minh họa, chưa chạy độc lập khi thiếu class `PaymentClient`. Spring cần biết đến `BillingConfiguration` qua đăng ký trực tiếp, component scan, `@Import` hoặc cơ chế cấu hình khác. Bản thân sự xuất hiện của annotation trong source không tự khởi động container.

Khi cấu hình được xử lý, Spring đăng ký **bean definitions** từ `@Bean` methods. Sau đó container tạo object khi cần theo scope, dependency, lazy flag và quá trình refresh. Mặc định scope của một `@Bean` là **singleton trong một BeanFactory**.

Điểm khác biệt cần phân biệt:

1. Spring gọi factory method `@Bean` khi tạo managed bean.
2. Code ứng dụng **tự gọi** method `@Bean` trên một configuration object.

Trong full mode, lời gọi loại 2 trên **đối tượng configuration đã được Spring tăng cường và quản lý** có thể được chuyển thành lookup container; trong lite mode lời gọi ấy vẫn là Java bình thường. Đây chính là lý do tồn tại `proxyBeanMethods`.

## 3. Full mode hoạt động như thế nào?

### 3.1. Lớp con được tăng cường

Giả sử class cấu hình tên `DemoConfig`. Khi xử lý full mode, Spring có thể sinh một kiểu runtime có quan hệ kế thừa tương tự:

```text
                DemoConfig
                    ^
                    |
          DemoConfig$$SpringCGLIB$$...
                    |
                    v
        Configuration bean trong Spring
```

Tên class được sinh là **chi tiết triển khai**, không nên hard-code, parse hoặc dùng như API. Spring sử dụng CGLIB được đóng gói trong module `spring-core` (namespace `org.springframework.cglib`), không cần khai báo một dependency CGLIB riêng trong ví dụ Spring Context thông thường.

Để hình dung cơ chế, mã giả **không phải mã nguồn Spring thật**:

```java
// Mã giả giải thích ý tưởng, KHÔNG chạy độc lập.
@Override
Engine engine() {
    if (thisCallIsBeingUsedByBeanFactoryToCreateEngine()) {
        return super.engine(); // Factory body thật tạo bean.
    }
    return beanFactory.getBean("engine", Engine.class);
}
```

Mục đích của interceptor là phân biệt **lời gọi được BeanFactory dùng để tạo bean** với **lời gọi tham chiếu bean đã được quản lý**. Nếu mọi lời gọi đều đơn giản tự quay lại BeanFactory mà không có phân biệt, việc factory đang tạo chính bean đó có thể dẫn đến đệ quy. Cơ chế thực tế còn xử lý scoping, trạng thái bean, type metadata và những tình huống đặc thù khác.

### 3.2. Luồng tạo singleton và inter-bean call

```text
Spring đăng ký:
  "engine" -> DemoConfig.engine()
  "car"    -> DemoConfig.car()
  "truck"  -> DemoConfig.truck()

Khi Spring tạo "car":
  BeanFactory gọi factory car()
    |
    v
  Car factory gọi engine()
    |
    v
  CGLIB interceptor kiểm tra "engine"
    |
    +-- Đã có managed singleton? -> dùng bean đã có
    |
    +-- Chưa có? -> yêu cầu BeanFactory tạo/lấy "engine"
    |
    v
  car nhận tham chiếu Engine do Spring quản lý

Khi Spring tạo "truck":
  truck factory gọi engine()
    |
    v
  CGLIB redirect tới cùng singleton "engine"
```

Đây là sơ đồ **ngữ nghĩa với singleton mặc định**, không mô tả máy trạng thái nội bộ đầy đủ. Kết quả quan trọng là `car.engine() == truck.engine()` **và** cả hai bằng `context.getBean(Engine.class)` khi cấu hình phù hợp.

### 3.3. Full mode không phải caching chung mọi lời gọi

CGLIB enhancement tôn trọng **bean scope** và container semantics, không đơn giản cache mọi giá trị method Java.

- `singleton`: với các reference thông thường, cùng managed instance được tái sử dụng trong một container.
- `prototype`: BeanFactory tạo instance theo mỗi lần resolve; một inter-bean call có thể nhận một prototype mới.
- web/custom scope: kết quả phụ thuộc scope đang active và khả năng sử dụng bean trong ngữ cảnh đó.
- nếu BeanFactory tạo proxy AOP hay scoped proxy cho bean, cross-method lookup có thể nhận **reference do container công bố**, không nhất thiết raw object được tạo trực tiếp bằng `new` trong factory.

Vì thế đừng hiểu full mode là “mọi `@Bean` method chỉ chạy đúng một lần” trong mọi scope. Phát biểu đó chỉ tương ứng với tình huống singleton bình thường và không có nguyên nhân khác kích hoạt factory theo cơ chế riêng.

## 4. Các lời gọi cần và không cần được chặn

| Tình huống | Full `@Configuration` (`proxyBeanMethods=true`) | `proxyBeanMethods=false` / lite |
| --- | --- | --- |
| BeanFactory gọi method để tạo bean | Method chạy để tạo managed instance | Method chạy để tạo managed instance |
| `car()` gọi `engine()` trong cùng config | Inter-bean call được chặn nếu method đủ điều kiện; trả reference theo BeanFactory | Lời gọi Java thường; có thể `new Engine()` thêm |
| `context.getBean("engine")` | Trả managed bean theo scope | Trả managed bean theo scope |
| `config.engine()` trên configuration bean được Spring quản lý | Có thể được chặn và trả bean từ container | Chạy method Java thường |
| `new Config().engine()` ngoài Spring | Không có interceptor do container tạo | Chạy Java thường |
| Bean được khai báo bằng `@Bean` có singleton scope | Có | Có |
| Constructor/method parameter injection | Có | Có |
| Lớp configuration có thể `final` | Không ở full mode vì cần subclass | Có thể, nếu không còn các ràng buộc khác |
| Interception qua `static @Bean` method | Không áp dụng kiểu virtual override | Không áp dụng |
| Có lớp con CGLIB vì bean method interception | Có | Không cần vì mục đích này |

**Đừng nói `proxyBeanMethods=false` tắt Spring bean management.** Nó chỉ tắt **proxying của `@Bean` methods** tại configuration class. Bean definitions, dependency resolution và lifecycle vẫn do Spring quản lý như thông thường.

### 4.1. Lời gọi từ config khác

Nếu một bean được truy xuất thông qua **managed instance của full configuration class**, lời gọi `@Bean` method có thể được interceptor chuyển thành lookup container ngay cả khi code gọi từ nơi khác. Tuy nhiên, truyền trực tiếp dependency vào method `@Bean` hoặc constructor vẫn dễ theo dõi và ít ràng buộc hơn giữa các configuration classes.

Không lấy configuration bean làm **service locator** rồi gọi `config.someBean()` khắp ứng dụng: điều đó làm business code phụ thuộc vào class cấu hình và proxy mode.

### 4.2. Khi self-invocation khác Spring AOP

Một hiểu nhầm rất thường gặp: “Spring AOP proxy không intercept self-invocation; tại sao `car()` gọi `engine()` lại được chặn?”

Hai cơ chế không giống nhau:

- **CGLIB enhancement của full `@Configuration`**: Spring tạo subclass của configuration class để chặn các `@Bean` method calls, bao gồm inter-bean calls xảy ra trong configuration code phù hợp.
- **Spring AOP** với `@Transactional`, `@Cacheable`, v.v.: thường là một proxy khác bao quanh target bean. Khi method của target gọi một method khác trên chính target qua `this`, self-invocation thông thường không đi qua AOP proxy.

Không thể suy ra ngữ nghĩa Spring AOP từ cơ chế `@Bean` interception. Full configuration không tự bật transaction, caching hoặc HTTP handling.

## 5. Cấu hình lite: khi nào và vì sao dùng `proxyBeanMethods=false`?

Từ Spring Framework 5.2, `@Configuration` cung cấp tùy chọn:

```java
@Configuration(proxyBeanMethods = false)
public class SimpleConfiguration {
    @Bean
    Formatter formatter() {
        return new Formatter();
    }
}
```

Trong cấu hình này, Spring vẫn phát hiện method `formatter()` và có thể tạo singleton bean `formatter`. Nhưng phương thức **không được override để thực thi inter-bean interception**.

Khi các factory methods **độc lập** và không dùng lời gọi Java `someBean()` sang method `@Bean` khác, proxying mang lại ít giá trị. Tắt interception:

- bỏ nhu cầu tạo CGLIB subclass cho mục đích chặn `@Bean` methods;
- loại bỏ những giới hạn subclassing tương ứng đối với configuration class/method;
- có thể giảm một phần chi phí cấu hình ban đầu, nhưng **không bảo đảm** mọi ứng dụng sẽ startup nhanh hơn đáng kể;
- làm semantics của lời gọi Java rõ ràng: gọi method nghĩa là chạy method body.

Một ví dụ nên dùng:

```java
@Configuration(proxyBeanMethods = false)
class AppConfig {

    @Bean
    Engine engine() {
        return new Engine();
    }

    @Bean
    Vehicle car(Engine engine) {
        return new Vehicle(engine);
    }

    @Bean
    Vehicle truck(Engine engine) {
        return new Vehicle(engine);
    }
}
```

Đây là **đoạn minh họa** cần class `Engine`/`Vehicle`. BeanFactory **resolve `Engine` làm tham số** trước khi gọi `car(...)`/`truck(...)`, nên cả hai có thể nhận cùng singleton được quản lý mà không cần tự gọi `engine()` qua CGLIB.

### 5.1. Lite mode không chỉ có `@Configuration(proxyBeanMethods=false)`

Theo quy ước Spring, `@Bean` methods khai báo trong các class **không đánh dấu `@Configuration` đầy đủ**, ví dụ một class `@Component`, cũng có semantics “lite” khi được xử lý. Những method này là factory methods được container sử dụng; inter-bean calls **không** được bảo đảm chuyển qua container.

```java
@Component
class UtilityFactories {

    @Bean
    Formatter formatter() {
        return new Formatter();
    }

    @Bean
    Printer printer() {
        return new Printer(formatter()); // Plain Java call.
    }
}
```

Đây là mã **minh họa**, cần `Formatter`/`Printer` và scanning. Để tránh object ngoài managed lifecycle, viết `printer(Formatter formatter)` rồi tạo `new Printer(formatter)`.

Khi một class chứa `@Bean` methods vừa mang `@Component` vừa `@Configuration`, semantics liên quan configuration processing cần xác định theo annotations thực tế; đừng chỉ nhìn tên class để suy luận.

## 6. Ràng buộc của full mode và CGLIB

CGLIB tăng cường bằng cách sinh subclass và override methods phù hợp. Những quy tắc Java về inheritance/override vì vậy rất quan trọng.

### 6.1. Configuration class không được `final` trong full mode

Ví dụ sai:

```java
@Configuration
public final class FinalConfig {
    @Bean
    Engine engine() {
        return new Engine();
    }
}
```

Đây là **ví dụ lỗi cấu hình**, không phải file chạy độc lập. Java `final` chặn kế thừa nên Spring không thể sinh lớp con phục vụ CGLIB enhancement. Trong full mode, configuration class phải có khả năng subclass. Có thể đổi class không `final` hoặc dùng `@Configuration(proxyBeanMethods=false)` nếu không cần inter-bean interception.

### 6.2. `@Bean` method trong full mode phải override được

Ví dụ không phù hợp:

```java
@Configuration
class InvalidConfig {

    @Bean
    private Engine engine() {
        return new Engine();
    }

    @Bean
    public final Vehicle car() {
        return new Vehicle(engine());
    }
}
```

Đây là **ví dụ lỗi**, không nên copy vào dự án chạy. Method `private` không thể override; method `final` không thể override. Full-mode `@Bean` methods cần đáp ứng ràng buộc để Spring có thể intercept. Dùng access modifier phù hợp và bỏ `final`, hoặc chuyển sang cấu hình lite với các method factory độc lập.

### 6.3. Static `@Bean` methods là trường hợp riêng

`static` methods thuộc class, không có virtual dispatch theo instance như instance methods; CGLIB không thể override theo cách tương tự. Spring hỗ trợ static `@Bean` factory methods cho những trường hợp hợp lệ, đặc biệt một số bean hạ tầng cần được khai báo static để khởi tạo đúng giai đoạn.

**Không giả định gọi `staticBeanMethod()` từ code Java sẽ được full configuration interceptor trả managed singleton.** Nếu bean khác cần dependency do static method tạo, truyền nó vào tham số method factory của bean tiêu thụ.

Một static `@Bean` method phù hợp khi cần đăng ký hạ tầng kiểu `BeanFactoryPostProcessor` trong các kịch bản nhất định, nhưng không có nghĩa mọi factory method nên static.

### 6.4. `private` constructors và Java modules

Tạo subclass động cũng chịu ảnh hưởng visibility, class loader và **Java Platform Module System** khi module path được sử dụng. Đừng gộp mọi `InaccessibleObjectException` vào lỗi “thiếu bean”. Nếu JPMS chặn reflection/enhancement, xem `exports`/`opens` cần thiết cho Spring.

Spring hỗ trợ nhiều kiểu constructor injection vào configuration class, nên **không bắt buộc phải có constructor public no-arg** chỉ để làm full `@Configuration`. Trường hợp có constructor dependency cần được container phân giải như các bean khác.

### 6.5. Tránh dựa vào nội bộ của CGLIB

Không lấy chuỗi `$$SpringCGLIB$$` từ class name để quyết định nghiệp vụ; tên class tăng cường có thể khác giữa phiên bản hoặc hoàn cảnh. Không dùng Java reflection bằng tên method sinh nội bộ. Hãy kiểm tra behavior qua managed bean identity, scopes và dependency contract.

## 7. Ví dụ Java 17+ hoàn chỉnh: ba kiểu configuration

Ví dụ dùng **Spring Framework 6.2.19**, chỉ cần `spring-context`. Chúng ta tạo **ba ApplicationContext riêng**:

1. **FULL**: `@Configuration` mặc định, `@Bean` methods gọi qua lại; tất cả tham chiếu Engine về cùng singleton.
2. **LITE_DIRECT**: `proxyBeanMethods=false` nhưng vẫn gọi `engine()` trực tiếp; tạo thêm Engine ngoài bean factory.
3. **LITE_PARAMETER**: `proxyBeanMethods=false` và truyền `Engine` qua tham số factory methods; trở lại dùng chung singleton managed bean.

### 7.1. Sơ đồ file

```text
spring-config-cglib-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        └── java/
            └── ConfigurationModesDemo.java
```

File `settings.gradle`:

```groovy
rootProject.name = 'spring-config-cglib-demo'
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
    implementation 'org.springframework:spring-context:6.2.19'
}

application {
    mainClass = 'ConfigurationModesDemo'
}
```

Dùng JDK 17+ và phiên bản Gradle tương thích JDK. Không cần khai báo `cglib` riêng; CGLIB được đóng gói lại bên trong `spring-core` là dependency của Spring Context.

### 7.2. Mã Java hoàn chỉnh

Lưu file `src/main/java/ConfigurationModesDemo.java`:

```java
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class ConfigurationModesDemo {

    public static final class Engine {
        private static int constructions;

        public Engine() {
            constructions++;
        }

        public static void resetCount() {
            constructions = 0;
        }

        public static int created() {
            return constructions;
        }
    }

    public record Vehicle(Engine engine) {
    }

    // Full configuration: @Configuration có proxyBeanMethods = true.
    @Configuration
    public static class FullConfig {

        @Bean
        public Engine engine() {
            return new Engine();
        }

        @Bean
        public Vehicle car() {
            return new Vehicle(engine());
        }

        @Bean
        public Vehicle truck() {
            return new Vehicle(engine());
        }
    }

    // Lite: các method gọi nhau như Java thông thường.
    @Configuration(proxyBeanMethods = false)
    public static class LiteDirectConfig {

        @Bean
        public Engine engine() {
            return new Engine();
        }

        @Bean
        public Vehicle car() {
            return new Vehicle(engine());
        }

        @Bean
        public Vehicle truck() {
            return new Vehicle(engine());
        }
    }

    // Lite nhưng dùng Spring DI tại tham số @Bean.
    @Configuration(proxyBeanMethods = false)
    public static class LiteParameterConfig {

        @Bean
        public Engine engine() {
            return new Engine();
        }

        @Bean
        public Vehicle car(Engine engine) {
            return new Vehicle(engine);
        }

        @Bean
        public Vehicle truck(Engine engine) {
            return new Vehicle(engine);
        }
    }

    private static void inspect(
            String label, Class<?> configurationClass) {

        Engine.resetCount();
        try (var context =
                new AnnotationConfigApplicationContext(configurationClass)) {
            Engine managed = context.getBean(Engine.class);
            Vehicle car = context.getBean("car", Vehicle.class);
            Vehicle truck = context.getBean("truck", Vehicle.class);

            System.out.println("== " + label + " ==");
            System.out.println("managed==car: " +
                    (managed == car.engine()));
            System.out.println("managed==truck: " +
                    (managed == truck.engine()));
            System.out.println("car==truck: " +
                    (car.engine() == truck.engine()));
            System.out.println("enginesCreated: " + Engine.created());
        }
    }

    public static void main(String[] args) {
        inspect("FULL", FullConfig.class);
        inspect("LITE_DIRECT", LiteDirectConfig.class);
        inspect("LITE_PARAMETER", LiteParameterConfig.class);
    }
}
```

Đây là một file Java hợp lệ: chỉ `ConfigurationModesDemo` là top-level public class; ba configuration classes, `Engine` và record `Vehicle` là nested public static classes. Các method `@Bean` có tên `engine`, `car`, `truck` và được đăng ký riêng trong từng context.

`Engine.constructions` là counter phục vụ học tập; ví dụ chạy một thread duy nhất. Trong hệ thống concurrent, counter thật nên dùng công cụ thread-safe nếu cần đo số lần tạo object. Ta reset counter **trước khi tạo mỗi context**; mỗi lần `inspect` đóng context cũ trước khi bắt đầu lượt mới.

### 7.3. Lệnh chạy

Từ thư mục gốc dự án:

```bash
gradle run --quiet
```

Nếu sử dụng Gradle Wrapper:

```bash
./gradlew run --quiet
```

Trên Windows PowerShell:

```powershell
.\gradlew.bat run --quiet
```

Các dòng do chương trình in:

```text
== FULL ==
managed==car: true
managed==truck: true
car==truck: true
enginesCreated: 1
== LITE_DIRECT ==
managed==car: false
managed==truck: false
car==truck: false
enginesCreated: 3
== LITE_PARAMETER ==
managed==car: true
managed==truck: true
car==truck: true
enginesCreated: 1
```

Đây là stdout từ `System.out.println`. Nếu môi trường cấu hình thêm logging, Gradle có thể hiển thị output khác không thuộc những dòng trên.

### 7.4. Vì sao FULL tạo đúng một Engine?

`FullConfig` mang `@Configuration` mặc định, nên Spring xử lý nó ở full mode, tăng cường bằng CGLIB. Với singleton `engine`:

- BeanFactory tạo managed `engine` một lần.
- `car()` gọi `engine()` qua đối tượng cấu hình đã tăng cường; interceptor trả reference managed bean.
- `truck()` cũng nhận cùng managed reference.
- `getBean(Engine.class)` trả cùng bean đó.

Vì vậy cả ba phép so sánh identity `==` là `true` và constructor `new Engine()` chạy đúng **một lần trong context FULL**.

### 7.5. Vì sao LITE_DIRECT tạo ba Engine?

`LiteDirectConfig` tắt interception. Container vẫn tạo một managed singleton `engine`, nhưng:

- `car()` thực hiện lời gọi Java `engine()` bình thường → một Engine mới **không phải managed singleton `engine`**.
- `truck()` gọi Java `engine()` bình thường → một Engine khác.
- BeanFactory vẫn giữ singleton `engine` riêng theo bean definition, vì `engine` được khai báo là `@Bean` và không lazy.

Tổng cộng có **ba Engine objects** được tạo trong context này. Car, truck và managed bean giữ ba references khác nhau nên cả ba phép `==` đều `false`.

Đáng chú ý: hai `Vehicle` vẫn là **Spring beans** vì được BeanFactory tạo từ các `@Bean` factory methods. Chỉ có các `Engine` được tạo bởi direct Java calls bên trong hai factory methods là các object riêng, không phải chính instance singleton `engine` từ BeanFactory.

### 7.6. Vì sao LITE_PARAMETER chỉ tạo một Engine?

`LiteParameterConfig` không dùng inter-bean direct call. Spring resolve dependency `Engine` trước khi gọi `car(Engine engine)` và `truck(Engine engine)`, truyền cùng managed singleton. Không cần tạo subclass CGLIB cho việc chặn `@Bean` methods. Do đó behavior sử dụng bean giống FULL trong ví dụ, mà phương pháp khai báo quan hệ phụ thuộc vẫn rõ ràng.

Đây là khuyến nghị chính trong ứng dụng hiện đại: **nếu đã chọn `proxyBeanMethods=false`, hãy truyền bean dependency qua tham số `@Bean` thay vì gọi method `@Bean` trực tiếp.**

### 7.7. Vì sao ví dụ dùng ba context?

Mỗi context đăng ký bean names `engine`/`car`/`truck` riêng; nếu trộn cả ba configs vào một context, sẽ xuất hiện tên trùng và semantics khác. Tách context cho phép so sánh đúng một biến: interception và cách diễn đạt dependency.

Cả ba context đều có singleton scopes. Ví dụ cố tình không dùng prototype vì khi đó số object mới theo scope sẽ làm so sánh kém rõ.

## 8. Tham số `@Bean` hay inter-bean call: chọn cách nào?

| Tiêu chí | Full `@Configuration` + gọi trực tiếp | `proxyBeanMethods=false` + tham số |
| --- | --- | --- |
| Cách viết | `return new Car(engine());` | `Car car(Engine engine) { return new Car(engine); }` |
| Ai resolve bean dependency | CGLIB interceptor chuyển qua BeanFactory | BeanFactory resolve method argument |
| Cần CGLIB enhancement cho config | Có | Không |
| Hạn chế `final` với config | Có | Không do bean method interception |
| Đọc dependency từ chữ ký | Không rõ bằng tham số | Rất rõ |
| Dùng được trong lite mode | Không có đảm bảo singleton/managed lookup với direct calls | Có |
| Di chuyển factory method sang config khác | Có thể cần đổi cách gọi | Thường ít coupling hơn |
| AOP/scoped bean semantics | BeanFactory bảo đảm theo lookup | BeanFactory bảo đảm khi resolve argument |
| Khả năng sai do tự gọi method | Nếu full thực sự được xử lý đúng, được bảo vệ | Nếu luôn dùng tham số, tránh vấn đề |

Không có quy tắc “full mode luôn xấu” hoặc “lite mode luôn tốt”: full mode có giá trị khi một cấu hình cố ý dùng inter-bean references qua các method Java. Khi không cần đặc tính đó, lite mode với method-parameter injection thường đơn giản hơn và tránh hạn chế subclassing.

### 8.1. Một số lưu ý với `@Bean` method trên config khác

Với hai configuration classes, gọi qua `otherConfig.engine()` có thể phụ thuộc việc `otherConfig` là enhanced managed bean hay chỉ là Java object thường. Thay vì wire chính configuration class vào service hoặc một configuration khác, truyền `Engine` trực tiếp vào factory method tiêu thụ.

```java
@Configuration(proxyBeanMethods = false)
class FeatureConfig {

    @Bean
    FeatureService featureService(Engine engine) {
        return new FeatureService(engine);
    }
}
```

Đoạn trên **chỉ minh họa**: cần định nghĩa `FeatureService` và có bean kiểu `Engine` trong context.

## 9. Scope, `@Lazy` và các trường hợp không thể chỉ đếm constructor

### 9.1. Prototype: tạo mới là đúng thiết kế

Nếu `engine` có `@Scope("prototype")`, mỗi lần container thực hiện lookup prototype thông thường sẽ tạo instance mới. Full `@Configuration` khi inter-bean call thông qua BeanFactory cũng phải tôn trọng prototype scope; mục tiêu không phải luôn giữ một object cho mọi caller.

Trong khi đó, một singleton `Car` nhận prototype `Engine` qua factory method parameter thường giữ **một prototype đã resolve lúc tạo Car**. Muốn tạo Engine mới theo từng business operation, thiết kế `Car` nhận `ObjectProvider<Engine>` và gọi `getObject()` khi cần.

### 9.2. Lazy: trì hoãn tạo bean

`@Lazy` trên bean definition có thể ngăn pre-instantiate singleton không bắt buộc ở lúc context refresh. Nhưng nếu một singleton khác đang khởi tạo cần bean ấy, nó vẫn có thể phải được tạo. Full mode chặn inter-bean reference qua BeanFactory không có nghĩa mọi bean luôn eager; lite mode không có nghĩa mọi bean luôn lazy.

### 9.3. Proxy AOP và lifecycle

Khi BeanFactory áp dụng `BeanPostProcessor` để tạo AOP proxy, container có thể công bố một reference proxy thay vì raw instance. Inter-bean lookup qua full config giữ semantics của BeanFactory, điều này quan trọng cho những beans có transaction/security/caching aspects được cấu hình phù hợp.

Không nên gọi `new Service()` hoặc direct lite `@Bean` method rồi tự nhận object là cùng managed bean; các object tạo ngoài container không tự được inject/proxy/khởi tạo/hủy theo cùng managed lifecycle.

### 9.4. `FactoryBean` là một cơ chế khác

Spring có interface `FactoryBean<T>` dùng để tạo object do container quản lý theo quy tắc factory-specific. Một `@Bean` method trả `FactoryBean` có ngữ nghĩa lookup/production đặc biệt. Điều đó không đồng nghĩa CGLIB enhancement chỉ đơn giản cache raw return value. Nếu làm việc với `FactoryBean`, cần học riêng sự phân biệt factory bean và object do factory tạo.

## 10. Lỗi thường gặp và cách chẩn đoán

| Hiện tượng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| `@Configuration` class là `final` và context báo lỗi | Full mode cần subclass CGLIB | Bỏ `final` hoặc dùng `proxyBeanMethods=false` khi không cần interception |
| `@Bean` method là `private`/`final` | Không override được trong full mode | Dùng method overridable hoặc lite mode với independent factory methods |
| Hai beans dùng hai đối tượng dependency khác nhau dù mong singleton | Lite config đang gọi trực tiếp `otherBean()` | Đổi sang method parameter injection |
| `getBean(Engine.class)` khác object tạo từ `new Config().engine()` | Configuration instance được tạo ngoài Spring | Chỉ lookup managed bean qua container hoặc injection |
| `@Configuration(proxyBeanMethods=false)` bị hiểu là tắt `@Bean` | Nhầm interception với bean definition processing | Spring vẫn đăng ký `@Bean` methods; chỉ không chặn direct calls |
| Full mode vẫn thấy nhiều objects | Target scope prototype hoặc code tự tạo ngoài container | Kiểm tra bean scope, lookup và call sites trước khi kết luận CGLIB hỏng |
| Bean singleton có method chạy nhiều lần vì gọi thủ công | Dùng lite method như factory Java | Không gọi direct method khi muốn container-managed reference |
| Thay bằng full mode để chữa mọi vấn đề DI | Sai dependency graph/thiếu bean/qualifier | Sửa cấu hình DI; proxyBeanMethods không tạo dependency còn thiếu |
| Spring AOP `@Transactional` không chạy khi self-call | AOP proxy self-invocation khác config CGLIB enhancement | Thiết kế lời gọi qua proxy/interface/service boundaries phù hợp |
| `@Bean` static method không bị CGLIB chặn | Static method không phải virtual method | Dùng BeanFactory/injection để lấy bean |
| Java JPMS báo lỗi reflection/enhancement | Module/package chưa mở đúng | Kiểm tra `opens`/`exports` trong module descriptor |
| Unit test `new AppConfig().car()` cho kết quả khác integration test | Unit test đang gọi config như Java object | Dùng `AnnotationConfigApplicationContext` cho test Spring semantics |
| Circular bean dependency xuất hiện | Bean A cần B trong lúc B cần A | Refactor dependency graph; không chỉ đổi proxy mode |
| CGLIB được thêm thủ công nhưng vẫn lỗi | Hạn chế class/method hoặc semantics config, không phải thiếu JAR | Dùng CGLIB Spring đóng gói; kiểm tra đúng lỗi và code |
| Startup chậm | Nhiều nguyên nhân khác (scanning, bean creation, network, post-processors) | Đo profile cụ thể, không hứa `proxyBeanMethods=false` tự chữa mọi thứ |

### 10.1. Kiểm tra đúng configuration object

Nếu cần xác minh full enhancement, tạo context thật và inspect behavior:

```java
try (var context =
        new AnnotationConfigApplicationContext(FullConfig.class)) {
    Engine engine = context.getBean(Engine.class);
    Vehicle car = context.getBean("car", Vehicle.class);
    System.out.println(engine == car.engine());
}
```

Đây là **trích đoạn minh họa** dùng `FullConfig`, `Engine` và `Vehicle` của mục 7. Nó phải được chạy trong code có access tới các class ấy; `AnnotationConfigApplicationContext` là tên đúng từ `org.springframework.context.annotation`.

Đừng quyết định có enhance hay không chỉ qua tên class runtime; **bean identity và scope** mới phản ánh behavior bạn cần.

### 10.2. Checklist debug khi object bị tạo dư

1. Xác nhận `@Configuration` đã được Spring xử lý trong context thực hay chưa.
2. Kiểm tra `proxyBeanMethods` mặc định `true` hay đã tắt.
3. Tìm nơi gọi `otherBean()` trực tiếp; thay bằng tham số method nếu có thể.
4. Xác định target scope là singleton, prototype hay custom.
5. Kiểm tra object có được tạo bởi `new` ngoài BeanFactory không.
6. Kiểm tra các beans có tên trùng hoặc cùng type khiến chọn sai ứng viên không.
7. Nếu có AOP, phân biệt proxy reference và raw target.
8. Thử trên context nhỏ để tái hiện identity như mục 7.
9. Không dùng log constructor đơn lẻ để kết luận về lifetime nếu có `FactoryBean`/proxies.
10. Khi chọn lite mode, đảm bảo không còn inter-bean direct calls cần container semantics.

## 11. Bài tập kiểm tra kiến thức

### Bài 1 — Dự đoán số Engine

Với `FullConfig` ở mục 7, `engine` là singleton, hai factory methods gọi `engine()` trực tiếp. Bao nhiêu Engine được tạo?

**Đáp án:** một managed Engine trong bối cảnh ví dụ; hai inter-bean references được chặn và quay về BeanFactory.

### Bài 2 — Tắt interception

Đổi `FullConfig` thành `@Configuration(proxyBeanMethods=false)` mà không đổi method bodies. Kết quả ba phép so sánh identity thế nào?

**Đáp án:** trong ví dụ, đều `false`. Có một managed Engine singleton và hai Engine do direct calls tạo ra trong `car()` và `truck()`.

### Bài 3 — Không dùng proxy nhưng vẫn chia sẻ singleton

Muốn giữ `proxyBeanMethods=false`, làm sao để `car`/`truck` nhận cùng managed `engine`?

**Đáp án:** dùng `@Bean Vehicle car(Engine engine)` và `@Bean Vehicle truck(Engine engine)` rồi truyền tham số vào constructor; Spring phân giải argument từ BeanFactory.

### Bài 4 — `final class`

Tại sao `@Configuration final class AppConfig` không hợp lệ trong full mode?

**Đáp án:** CGLIB cần sinh subclass để override các `@Bean` methods. `final` chặn kế thừa.

### Bài 5 — Singleton và prototype

`@Bean @Scope("prototype") Engine engine()` trong full config. Mỗi lookup thông thường có phải đều trả một Engine duy nhất như singleton?

**Đáp án:** không; container tôn trọng scope. CGLIB inter-bean lookup không biến prototype thành singleton.

### Bài 6 — Direct method gọi từ Java `new`

`new FullConfig().engine()` có tự đi qua BeanFactory không?

**Đáp án:** không. Đây là một instance được tạo ngoài container và không phải CGLIB-enhanced configuration bean được quản lý.

### Bài 7 — So sánh với `@Transactional` self-invocation

Lý do `car()` gọi `engine()` có thể được chặn trong full config có chứng minh `@Transactional` trên một method service luôn được kích hoạt khi service method này gọi service method khác qua `this`?

**Đáp án:** không. Hai cơ chế khác nhau; self-invocation qua target thường bypass Spring AOP proxy.

### Bài 8 — Tình huống thư viện

Ứng dụng có ba independent factory methods không gọi qua lại, chỉ nhận tham số được Spring resolve. Có nhất thiết bật `proxyBeanMethods=true` không?

**Đáp án:** không. `@Configuration(proxyBeanMethods=false)` là lựa chọn hợp lý khi không cần inter-bean calls; các `@Bean` methods vẫn tạo managed beans.

### Bài 9 — Một bean cần prototype mới mỗi lần sử dụng

Nếu một singleton `JobRunner` cần `JobTask` prototype mỗi lần chạy, chỉ truyền `JobTask` làm tham số `@Bean JobRunner runner(JobTask task)` có đủ không?

**Đáp án:** chưa; injection trực tiếp vào singleton chỉ resolve một task khi runner được tạo. Dùng `ObjectProvider<JobTask>` hoặc factory phù hợp rồi lookup mỗi lần cần.

### Bài 10 — Kiểm tra lỗi đúng tầng

Một `@Bean` method không thể resolve tham số `Repository`. Đổi `proxyBeanMethods=true` sang `false` có giải quyết chắc chắn không?

**Đáp án:** không. Đó là lỗi thiếu dependency hoặc candidate resolution; cần đăng ký repository, sửa qualifier/scan/condition, chứ không chỉ đổi cách chặn method.

## 12. Checklist thiết kế

Trước khi chọn full hay lite configuration:

- Các `@Bean` methods **có thật sự tự gọi lẫn nhau** và cần lookup theo BeanFactory semantics không?
- Nếu có, có thể thay bằng **method parameter injection** để giảm coupling không?
- Bean đang dùng singleton hay scope khác? Có cơ chế `@Lazy`, prototype, scoped proxy, AOP nào ảnh hưởng identity không?
- Có cần class `final`, record hoặc những thành viên không override được không? Nếu có, tránh full-mode interception và kiểm tra cấu hình phù hợp.
- Có đang gọi method `@Bean` trên `new Config()` ngoài Spring không?
- Có đang phân biệt đủ **managed singleton instance** với những raw object được tạo bằng direct Java call không?
- Với Spring AOP, đã kiểm tra self-invocation theo proxy AOP riêng, không dùng CGLIB config làm suy luận thay thế chưa?
- Context test đã xác nhận identity, lifecycle và bean registration thực tế chưa?

## 13. Tóm tắt

**Full `@Configuration`** có `proxyBeanMethods=true` mặc định. Spring tăng cường configuration class bằng **CGLIB subclass** để các lời gọi giữa `@Bean` methods phù hợp được chuyển về BeanFactory, nhờ đó giữ singleton/scope/AOP semantics của managed beans. Nó cần class/method có thể được subclass/override; không áp dụng interception tương tự cho static factory methods.

**Lite mode** với `@Configuration(proxyBeanMethods=false)` vẫn đăng ký và quản lý `@Bean`, nhưng lời gọi Java `otherBean()` không được chặn, nên có thể tạo object ngoài managed bean mà caller mong đợi. Cách làm rõ ràng và tương thích cả hai mode là dùng **factory-method parameter injection**: `@Bean Car car(Engine engine)`.

Khi cần inter-bean direct calls trong full configuration, dùng cơ chế đó có chủ đích. Khi các factory methods độc lập hoặc đã khai báo tham số phụ thuộc, lite mode thường đơn giản và dễ bảo trì hơn. Dù chọn chế độ nào, hãy kiểm chứng behavior bằng Spring context thật thay vì chỉ gọi Java method trên cấu hình tự `new`.

### Tài liệu chính thức

- [Spring Framework 6.2 — Using the `@Configuration` Annotation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/configuration-annotation.html).
- [Spring Framework 6.2 — Using the `@Bean` Annotation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html).
- [Spring Framework 6.2 — Basic Concepts: `@Bean` and `@Configuration`](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/basic-concepts.html).
- [Spring Framework 6.2 — `Configuration` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Configuration.html).
- [Spring Framework 6.2 — `Bean` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Bean.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-configuration-007">Chế độ lite và @Configuration(proxyBeanMethods = false)</a>

<details>
<summary>Xem chi tiết</summary>

# Chế độ lite và @Configuration(proxyBeanMethods = false) trong Spring Core Container

## 1. Mục tiêu học tập

Khi viết Java configuration cho Spring, hai cách khai báo sau đều tạo bean definitions nhưng **khác nhau về hành vi khi một phương thức `@Bean` gọi phương thức `@Bean` khác**:

```java
@Configuration
class FullConfig {
    // Các phương thức @Bean
}
```

```java
@Configuration(proxyBeanMethods = false)
class LiteConfig {
    // Các phương thức @Bean
}
```

Mặc định, `@Configuration` có `proxyBeanMethods = true`. Spring xử lý class này theo **full configuration mode**: tăng cường (*enhance*) đối tượng cấu hình bằng subclass dựa trên CGLIB để một lời gọi tới phương thức `@Bean` của đối tượng cấu hình đã được Spring quản lý có thể được chuyển thành yêu cầu lấy bean từ container, qua đó giữ đúng scope và các hành vi liên quan.

Khi viết `@Configuration(proxyBeanMethods = false)`, bạn **tắt interception của các lời gọi phương thức `@Bean`**. Spring vẫn đăng ký các bean definitions và quản lý những bean nó tạo, nhưng nếu trong Java code bạn tự gọi `anotherBean()` thì đó chỉ là **lời gọi Java bình thường**, không phải `BeanFactory.getBean(...)` ngầm. Spring gọi đây là **lite mode**; các phương thức `@Bean` nằm trong class `@Component` thông thường cũng thuộc nhóm lite.

Sau bài này, bạn sẽ:

- Phân biệt **full `@Configuration`**, **`@Configuration(proxyBeanMethods=false)`** và **`@Bean` trong plain `@Component`**.
- Hiểu chính xác lúc nào Spring bảo toàn singleton identity, scope và bean lifecycle, lúc nào một lời gọi trực tiếp tạo đối tượng ngoài container.
- Biết vì sao **tham số phương thức `@Bean`** là cách biểu diễn inter-bean dependency an toàn trong cả hai chế độ.
- Biết ưu, nhược điểm và ràng buộc kỹ thuật của CGLIB enhancement.
- Phân biệt `proxyBeanMethods` với proxy Spring AOP, `@Lazy`, `@Scope` và `ObjectProvider`.
- Chạy ví dụ Java 17 + Spring Framework thuần và đọc output có thể kiểm chứng.
- Chuyển cấu hình full sang lite mà không âm thầm thay đổi hành vi.

**Thông điệp trọng tâm:** Lite mode **không tắt quản lý bean của Spring**. Nó chỉ không intercept **lời gọi Java trực tiếp giữa những `@Bean` methods**. Muốn truyền đúng bean do container quản lý, khai báo dependency qua **tham số factory method** hoặc cơ chế dependency injection phù hợp.

## 2. `@Configuration` và `@Bean` vẫn hoạt động trong lite mode

`@Configuration` đánh dấu nguồn Java configuration; `@Bean` công bố factory method mà container dùng để tạo và quản lý bean. Ví dụ minh họa (cần định nghĩa `Catalog` và `CatalogConsumer`):

```java
@Configuration(proxyBeanMethods = false)
class CatalogConfig {
    @Bean Catalog catalog() { return new Catalog(); }
    @Bean CatalogConsumer consumer(Catalog catalog) {
        return new CatalogConsumer(catalog);
    }
}
```

Spring vẫn đăng ký hai bean definitions và resolve tham số `Catalog` qua BeanFactory. Cần phân biệt **đăng ký definition**, **container gọi factory method**, và **Java code tự gọi factory method**; chỉ trường hợp cuối chịu ảnh hưởng trực tiếp của `proxyBeanMethods`.

## 3. Full mode: cơ sở để hiểu lite mode

`@Configuration` mặc định `proxyBeanMethods=true`; Spring có thể tạo subclass CGLIB để chặn các lời gọi instance `@Bean` methods phù hợp trên enhanced configuration object.

```java
@Configuration
class FullConfig {
    @Bean Catalog catalog() { return new Catalog(); }
    @Bean CatalogConsumer consumer() {
        return new CatalogConsumer(catalog());
    }
}
```

Ví dụ minh họa dùng hai kiểu `Catalog`/`CatalogConsumer`. Trong full mode, `catalog()` được gọi từ `consumer()` trên enhanced config có thể được chuyển thành lookup bean `catalog`, bảo toàn singleton reference hoặc scope phù hợp. Trong lite mode, đó là lời gọi Java bình thường và có thể tạo một object khác.

### Điều kiện cần để interception xảy ra

Interception gắn với **configuration bean được Spring quản lý và tăng cường**; `new FullConfig().catalog()` ngoài context không có ngữ nghĩa ấy. `static @Bean` không có virtual dispatch để CGLIB override. Bài này tiếp tục ở thao tác dùng lite và refactor dependency; bài full mode và giới hạn factory methods đào sâu CGLIB.

## 4. Lite mode là gì?

Trong Spring, phương thức `@Bean` được xử lý theo **lite mode** khi:

- Nó nằm trong class **không có `@Configuration`**, nhưng class đó là thành phần được Spring xử lý, ví dụ một bean `@Component`.
- Hoặc nó nằm trong class có `@Configuration(proxyBeanMethods = false)`.

Ví dụ lite rõ nhất:

```java
@Configuration(proxyBeanMethods = false)
class LiteConfig {

    @Bean
    Catalog catalog() {
        return new Catalog();
    }

    @Bean
    CatalogConsumer consumer() {
        return new CatalogConsumer(catalog());
    }
}
```

Spring vẫn đăng ký hai bean `catalog` và `consumer`. Nhưng bên trong `consumer()`, lời gọi `catalog()` không được intercept. Khi Java chạy `new CatalogConsumer(catalog())`, phương thức `catalog()` có thể tạo **một `Catalog` riêng**, ngoài instance Spring quản lý dưới bean name `catalog`.

Điều đáng chú ý:

- Container vẫn có **một singleton bean `catalog`** nếu mặc định scope singleton.
- Object do lời gọi trực tiếp `catalog()` tạo ra trong `consumer()` có thể **không phải** singleton đó.
- Object tạo trực tiếp không tự được áp dụng `BeanPostProcessor`, các proxy, init/destroy callbacks hoặc scope của bean definition `catalog`.
- Việc hai object cùng class `Catalog` không làm chúng trở thành cùng một managed bean.
- `@Configuration(proxyBeanMethods=false)` vẫn là annotation cấu hình, vẫn có thể được component scan tìm thấy và vẫn có thể khai báo `@Import`, `@Bean` hoặc metadata liên quan như bình thường.

Spring dùng từ “lite” để nói về **xử lý factory methods không có CGLIB inter-bean interception**, không phải Spring tắt dependency injection hoặc giảm bean thành “object không quản lý”.

### Lite @Bean trong @Component thông thường

```java
@Component
class CatalogFactoryComponent {

    @Bean
    Catalog catalog() {
        return new Catalog();
    }

    @Bean
    CatalogConsumer consumer() {
        return new CatalogConsumer(catalog());
    }
}
```

Các đoạn trên dùng annotation `org.springframework.stereotype.Component` và phải được **đăng ký hoặc phát hiện** trong context mới có hiệu lực. Vì class chỉ là `@Component` thông thường, factory methods là **lite `@Bean`**. Việc gọi `catalog()` từ `consumer()` có nghĩa Java thông thường giống ví dụ `LiteConfig`.

Không cần biến mọi class chứa `@Bean` thành `@Configuration` nếu chúng có ý định làm component thông thường, nhưng khi một class chủ yếu đóng vai trò cấu hình bean definitions, `@Configuration(proxyBeanMethods=false)` làm ý định rõ hơn.

## 5. So sánh full và lite bằng hợp đồng cụ thể

| Khía cạnh | Full `@Configuration` | Lite `@Configuration(proxyBeanMethods=false)` hoặc plain `@Component` |
| --- | --- | --- |
| Phương thức `@Bean` được đăng ký vào container | Có | Có |
| Các bean được Spring quản lý khi container tạo | Có | Có |
| Scoped/lifecycle semantics của `context.getBean(...)` | Có | Có |
| CGLIB enhance class cấu hình để intercept bean methods | Có | Không dùng enhancement này |
| Một `@Bean` method gọi trực tiếp `otherBean()` | Có thể được intercept theo BeanFactory | Chỉ là lời gọi Java bình thường |
| Có thể tạo thêm object ngoài container khi gọi method trực tiếp | Có nếu gọi trên **instance không được Spring enhance** hoặc trường hợp không intercept | Có, ngay cả khi method được gọi từ một factory method khác |
| Factory method nhận tham số dependency do Spring tiêm | Có | Có |
| Cần tuân thủ ràng buộc subclassing `final`/`private` cho intercepted methods | Có | Được nới lỏng vì không cần inter-bean subclass |
| Dùng để định nghĩa những bean độc lập không gọi lẫn nhau | Có | Có, thường là lựa chọn gọn |
| Phù hợp với code cố ý dùng direct inter-bean method calls | Có, nếu hiểu rõ điều kiện full | Không |

**Lưu ý về tối ưu:** `proxyBeanMethods=false` có thể tránh chi phí tạo CGLIB subclass cho class cấu hình, giảm footprint/enhancement overhead. Đây thường là tối ưu cấu hình hợp lý, nhưng **không nên** đổi chỉ vì muốn “nhanh hơn” trước khi rà soát mọi lời gọi trực tiếp giữa các `@Bean` methods.

## 6. Cách viết đúng trong cả full và lite: tiêm dependency qua tham số @Bean

Đây là mẫu nên ưu tiên:

```java
@Configuration(proxyBeanMethods = false)
class SafeConfig {

    @Bean
    Catalog catalog() {
        return new Catalog();
    }

    @Bean
    CatalogConsumer consumer(Catalog catalog) {
        return new CatalogConsumer(catalog);
    }
}
```

`Catalog` là class ứng dụng minh họa. Khi Spring gọi factory method `consumer(Catalog catalog)`, Spring **giải quyết tham số `Catalog` thông qua container**:

1. Container biết method `consumer` cần bean kiểu `Catalog`.
2. Spring tìm ứng viên `Catalog` theo kiểu, qualifier, primary, generic và các quy tắc khác.
3. Nếu dùng singleton mặc định, nó truyền singleton bean `catalog` đã có hoặc tạo bean đó đúng cơ chế.
4. `CatalogConsumer` nhận reference tới **managed bean**, không phải một object khác do method call tự tạo.

Cách viết này hoạt động trong:

- Full `@Configuration(proxyBeanMethods=true)`.
- Lite `@Configuration(proxyBeanMethods=false)`.
- Lite `@Bean` methods trong `@Component` thông thường.
- Các cấu hình nhiều class khi dependency bean được đăng ký/import vào cùng context.

**Ưu điểm lớn:** dependency hiện ngay trong chữ ký method, dễ tìm, dễ test, không cần dựa vào CGLIB để duy trì bean identity.

### Mẫu nên tránh trong lite mode

```java
@Bean
CatalogConsumer consumer() {
    return new CatalogConsumer(catalog()); // Lời gọi Java thuần.
}
```

### Mẫu nên dùng

```java
@Bean
CatalogConsumer consumer(Catalog catalog) {
    return new CatalogConsumer(catalog); // Spring inject bean.
}
```

Ngay cả khi code hiện đang chạy full mode, chuyển từ direct call sang method parameter trước giúp cấu hình an toàn hơn nếu sau này cần chuyển sang lite mode.

## 7. Ví dụ Java 17 chạy được: so sánh full, lite và @Component

Ví dụ độc lập dưới đây dùng Spring Framework 6.2 và không cần Spring Boot. Mỗi chế độ được tạo trong **ApplicationContext riêng** để tránh trùng bean names và trùng kiểu.

Chương trình sẽ kiểm tra ba kết quả:

- Bao nhiêu lần method factory `catalog()` thực sự chạy.
- `directConsumer()` có nhận đúng `Catalog` singleton do container quản lý hay không.
- `injectedConsumer(Catalog catalog)` có nhận đúng singleton do Spring inject hay không.

### 7.1. Cấu trúc dự án

```text
spring-lite-configuration/
├── pom.xml
└── src/main/java/demo/LiteConfigurationDemo.java
```

### 7.2. POM cho Java 17

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-lite-configuration</artifactId>
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

### 7.3. Mã nguồn đầy đủ

Tạo `src/main/java/demo/LiteConfigurationDemo.java`:

```java
package demo;

import java.util.function.IntSupplier;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

public class LiteConfigurationDemo {

    static final class Catalog {
    }

    static final class CatalogConsumer {
        private final Catalog catalog;

        CatalogConsumer(Catalog catalog) {
            this.catalog = catalog;
        }

        Catalog catalog() {
            return catalog;
        }
    }

    private static int fullCalls;
    private static int liteCalls;
    private static int componentCalls;

    @Configuration
    static class FullConfig {

        @Bean
        Catalog catalog() {
            fullCalls++;
            return new Catalog();
        }

        @Bean
        CatalogConsumer directConsumer() {
            return new CatalogConsumer(catalog());
        }

        @Bean
        CatalogConsumer injectedConsumer(Catalog catalog) {
            return new CatalogConsumer(catalog);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LiteConfig {

        @Bean
        Catalog catalog() {
            liteCalls++;
            return new Catalog();
        }

        @Bean
        CatalogConsumer directConsumer() {
            return new CatalogConsumer(catalog());
        }

        @Bean
        CatalogConsumer injectedConsumer(Catalog catalog) {
            return new CatalogConsumer(catalog);
        }
    }

    @Component
    static class ComponentFactory {

        @Bean
        Catalog catalog() {
            componentCalls++;
            return new Catalog();
        }

        @Bean
        CatalogConsumer directConsumer() {
            return new CatalogConsumer(catalog());
        }

        @Bean
        CatalogConsumer injectedConsumer(Catalog catalog) {
            return new CatalogConsumer(catalog);
        }
    }

    private static void report(
            String label, Class<?> configurationClass, IntSupplier calls) {

        try (var context = new AnnotationConfigApplicationContext(
                configurationClass)) {

            Catalog managed = context.getBean(Catalog.class);
            CatalogConsumer direct =
                    context.getBean("directConsumer", CatalogConsumer.class);
            CatalogConsumer injected =
                    context.getBean("injectedConsumer", CatalogConsumer.class);

            System.out.println(label + ".factoryCalls=" + calls.getAsInt());
            System.out.println(label + ".directIsManaged=" +
                    (direct.catalog() == managed));
            System.out.println(label + ".parameterIsManaged=" +
                    (injected.catalog() == managed));
        }
    }

    public static void main(String[] args) {
        report("full", FullConfig.class, () -> fullCalls);
        report("lite", LiteConfig.class, () -> liteCalls);
        report("component", ComponentFactory.class, () -> componentCalls);
    }
}
```

**Giải thích cấu trúc:** Mỗi context đăng ký một class cấu hình/component và có đúng một bean `Catalog`, hai bean `CatalogConsumer` mang tên `directConsumer` và `injectedConsumer`. Ta tra các consumer **bằng tên kèm kiểu** để tránh nhập nhằng vì có hai bean cùng type `CatalogConsumer`.

`FullConfig` không `final`, và các `@Bean` instance methods cũng không `final`/`private`; như vậy có thể được Spring enhance cho full mode. `LiteConfig` tắt enhancement, còn `ComponentFactory` được đăng ký trực tiếp vào context để Spring xử lý các `@Bean` methods theo lite mode, dù không cần bật component scan trong ví dụ.

### 7.4. Lệnh chạy

Chạy trong thư mục có `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.LiteConfigurationDemo
```

Cần JDK 17+ và Maven. **Những dòng do chính chương trình in ra** (không tính log của Spring/Maven nếu môi trường có cấu hình logging khác):

```text
full.factoryCalls=1
full.directIsManaged=true
full.parameterIsManaged=true
lite.factoryCalls=2
lite.directIsManaged=false
lite.parameterIsManaged=true
component.factoryCalls=2
component.directIsManaged=false
component.parameterIsManaged=true
```

### 7.5. Vì sao full.factoryCalls = 1?

Trong full mode, `catalog()` được Spring intercept ở lời gọi từ `directConsumer()` để lấy bean singleton. Container chỉ cần thực thi factory logic để tạo một bean `Catalog` được quản lý. Việc tạo `injectedConsumer(Catalog catalog)` cũng chỉ phân giải singleton đang có.

Kết quả:

- `full.directIsManaged=true`.
- `full.parameterIsManaged=true`.
- `full.factoryCalls=1`.

Điểm quan trọng: giá trị `true` của direct call chỉ đúng vì nó diễn ra trên **Spring-enhanced configuration instance** theo full semantics. Nếu tự gọi `new FullConfig().catalog()` thì CGLIB không intercept instance đó.

### 7.6. Vì sao lite.factoryCalls = 2?

Trong lite mode:

1. Spring tạo bean `catalog` thông qua factory method một lần theo singleton scope.
2. Khi tạo bean `directConsumer`, code trong method gọi `catalog()` trực tiếp theo Java, chạy factory logic thêm một lần để tạo một `Catalog` **khác**.
3. Khi tạo `injectedConsumer`, Spring truyền bean `Catalog` container-managed qua tham số.

Do đó:

- `lite.directIsManaged=false`: consumer tạo bằng direct call giữ object ngoài managed bean.
- `lite.parameterIsManaged=true`: consumer qua parameter giữ singleton đúng.
- `lite.factoryCalls=2`: một lần do BeanFactory tạo bean, một lần do direct Java call.

Ngay cả khi Spring khởi tạo các singleton theo thứ tự khác nhau, tổng số lần gọi factory method vẫn là 2 trong ví dụ này, vì có một bean definition `Catalog` và đúng một direct call từ `directConsumer`.

### 7.7. Vì sao @Component cũng cho kết quả lite?

`ComponentFactory` không có `@Configuration`. Tuy nó có các phương thức `@Bean` và đã được đăng ký vào context, Spring không enhance nó để intercept những lời gọi giữa các bean methods. Vì thế nó có cùng hành vi direct Java call như `LiteConfig`:

- `component.factoryCalls=2`.
- `component.directIsManaged=false`.
- `component.parameterIsManaged=true`.

Ví dụ này chứng minh **lite mode không có nghĩa “không tìm thấy @Bean”**. Cả ba context đều tạo bean `Catalog` và hai bean `CatalogConsumer`; điều khác biệt nằm ở cách phân giải reference trong method call trực tiếp.

## 8. Rà soát mã trước khi chuyển full sang lite

Giả sử cấu hình hiện tại là:

```java
@Configuration
class OriginalConfig {

    @Bean
    DataSource dataSource() {
        return createDataSource();
    }

    @Bean
    UserRepository userRepository() {
        return new JdbcUserRepository(dataSource());
    }

    @Bean
    UserService userService() {
        return new UserService(userRepository());
    }
}
```

Đây là **đoạn minh họa**, cần khai báo các kiểu `DataSource`, `JdbcUserRepository`, `UserRepository` và `UserService`. Nó hoạt động theo full inter-bean method references nếu được Spring manage đúng cách.

Nếu bạn chỉ thêm `proxyBeanMethods = false` mà **không sửa** hai lời gọi `dataSource()` và `userRepository()`, có nguy cơ tạo:

- `UserRepository` ngoài container trong khi Spring vẫn đăng ký bean `userRepository` riêng.
- `DataSource` ngoài container cho instance repository tạo thủ công.
- Những object bị bỏ qua việc áp dụng lifecycle, transaction/proxy, init/destroy callbacks hoặc scope của bean definitions.
- Nhiều connection pools hay clients ngoài ý định, gây tốn tài nguyên và cleanup sai.

### Cách refactor an toàn

```java
@Configuration(proxyBeanMethods = false)
class SafeConfig {

    @Bean
    DataSource dataSource() {
        return createDataSource();
    }

    @Bean
    UserRepository userRepository(DataSource dataSource) {
        return new JdbcUserRepository(dataSource);
    }

    @Bean
    UserService userService(UserRepository userRepository) {
        return new UserService(userRepository);
    }
}
```

Các object phụ thuộc được tiêm qua tham số. Đây cũng là thiết kế ít phụ thuộc vào cơ chế interception hơn. Nếu một bean chỉ cần tham số từ cấu hình khác, Spring vẫn phân giải theo type/qualifier trong **cùng context**, miễn các bean definitions đã được đăng ký.

### Checklist tìm inter-bean direct calls

Trước khi bật lite mode:

- Tìm tất cả lời gọi `anotherBeanMethod()` bên trong các `@Bean` methods.
- Tìm lời gọi tương tự trong helper methods được gọi từ `@Bean`.
- Tìm cách dùng `this.anotherBeanMethod()`, method reference hoặc gọi qua reference tới config object.
- Kiểm tra bean methods trả tài nguyên như `DataSource`, `Executor`, `Client`, `SessionFactory`, `ConnectionFactory`, vì một instance phụ có thể gây hậu quả vận hành lớn.
- Đổi các dependency thành **tham số `@Bean`**, constructor injection, factory hoặc `ObjectProvider` khi có nhu cầu lookup theo thời điểm.
- Tạo integration test xác nhận managed bean identity, lifecycle, số instance và các failure paths.
- Chỉ sau đó đổi `@Configuration` thành `@Configuration(proxyBeanMethods=false)`.

Chú ý: không phải mọi lời gọi method trong class cấu hình là lỗi. Gọi **helper method không phải `@Bean`** để parse thuộc tính hoặc build một value object bên trong factory thường hợp lệ; điểm rủi ro là gọi **phương thức được dùng làm Spring bean factory** rồi tưởng kết quả là managed bean.

## 9. Scope: tại sao direct call nguy hiểm hơn chỉ tạo hai singleton?

Bean scope là hợp đồng BeanFactory. Dù Java method luôn trả `new Catalog()`, container có thể quản lý nó như singleton, prototype hoặc scoped proxy tùy bean definition.

### 9.1. Singleton

```java
@Bean
Catalog catalog() {
    return new Catalog();
}
```

Mặc định, bean này có singleton scope trong **một ApplicationContext**. `context.getBean(Catalog.class)` hai lần thường trả cùng instance. Nhưng `catalog()` được gọi trực tiếp trong lite mode là một method call và có thể tạo object mới ngoài managed singleton.

Vì vậy đừng kết luận: "Method có `@Bean` thì gọi thế nào cũng là singleton". Singleton là quy tắc **lấy bean qua container**, không phải lời hứa rằng mọi lần thực thi factory method từ Java đều trả cùng object.

### 9.2. Prototype

```java
@Bean
@Scope("prototype")
TaskWorker taskWorker() {
    return new TaskWorker();
}
```

Đây là **đoạn minh họa** cần `TaskWorker`, `@Scope` import và cấu hình hợp lệ. Khi Spring phân giải prototype bean qua container, nó tạo instance theo yêu cầu. Lite direct call `taskWorker()` cũng có thể tạo một object mới, nhưng đối tượng được gọi trực tiếp **không tự nhận quy trình quản lý bean**.

Sự trùng hợp “mỗi lần gọi đều ra object mới” **không** có nghĩa hai cơ chế tương đương. Chẳng hạn, post-processing, dependency injection, bean init callback, scope-aware proxy hoặc type conversion của Spring không được tự áp dụng lên object tạo trực tiếp.

Nếu một singleton service cần prototype worker mới tại mỗi lần xử lý, hãy xét `ObjectProvider<TaskWorker>` hoặc factory chuyên dụng, không dựa vào lời gọi trực tiếp `@Bean` trong lite mode.

### 9.3. Request/session scope

Khi dùng web scope hoặc scoped proxy, việc phân giải bean qua container có thể phụ thuộc request/session hiện hành. Direct method call trong lite mode bỏ qua bước tìm target/proxy được Spring quản lý. Nó không chỉ gây khác identity mà còn có thể sai **context nghiệp vụ và lifecycle**, đặc biệt khi đối tượng chứa trạng thái phiên làm việc.

Với các scope phức tạp, luôn viết code nhận dependency bằng injection/`ObjectProvider` theo đúng scope contract.

## 10. Lifecycle và BeanPostProcessor

Khi Spring tạo bean qua `@Bean` method, nó có thể:

- Giải quyết dependencies và apply các callback phù hợp.
- Chạy `BeanPostProcessor` trước/sau initialization.
- Áp dụng AOP proxying nếu infrastructure tương ứng được bật.
- Gọi `initMethod` hoặc callback khởi tạo theo cấu hình.
- Với singleton phù hợp, gọi `destroyMethod` khi context đóng.
- Áp dụng các scope và lifecycle rules liên quan.

Xem:

```java
@Configuration(proxyBeanMethods = false)
class ResourceConfig {

    @Bean(initMethod = "open", destroyMethod = "close")
    ResourceClient resourceClient() {
        return new ResourceClient();
    }

    @Bean
    WorkService workService(ResourceClient client) {
        return new WorkService(client);
    }
}
```

Đây là minh họa cần định nghĩa `ResourceClient.open()/close()` và `WorkService`. Khi Spring tạo `resourceClient`, bean được chạy init callback rồi có thể được đưa vào `workService`. Sau đó Spring gọi destroy callback khi container đóng nếu bean là singleton thuộc lifecycle container.

**Phản ví dụ:**

```java
@Bean
WorkService workService() {
    return new WorkService(resourceClient());
}
```

Trong lite mode, `resourceClient()` bên trong `workService()` tự chạy factory code, object tạo ra **không được BeanFactory tự động xử lý như bean `resourceClient`**. Nếu `open` phải được gọi trước khi sử dụng, object phụ có thể chưa được init; nếu có tài nguyên phải đóng, object đó có thể không nhận destroy callback của bean definition. Đây là lý do lifecycle bug thường khó nhận ra hơn lỗi singleton identity.

### Có phải lite tắt lifecycle cho @Bean?

**Không.** Khi Spring trực tiếp gọi `resourceClient()` để tạo bean definition `resourceClient`, instance đó **vẫn nhận** lifecycle đúng theo cấu hình. Chỉ những object được tạo qua **lời gọi Java không do container phân giải như một bean** mới không tự được quản lý theo bean definition ấy.

## 11. proxyBeanMethods không phải công tắc Spring AOP

Tên thuộc tính `proxyBeanMethods` rất dễ khiến người học hiểu nhầm là "bật/tắt mọi proxy". Thực tế nó chỉ kiểm soát **việc proxy các phương thức `@Bean` của class cấu hình**, không trực tiếp quyết định:

- Spring AOP có tạo proxy cho service hay không.
- `@Transactional` có được áp dụng lên service không.
- Scoped proxies được tạo ra hay không.
- JDK dynamic proxy hay class-based proxy được dùng cho một bean nghiệp vụ.
- Method security có được bật không.

Ví dụ một `@Service` nhận đối tượng do `@Bean` cung cấp:

```java
@Configuration(proxyBeanMethods = false)
class DatabaseConfig {

    @Bean
    DataSource dataSource() {
        return createDataSource();
    }
}
```

Bản thân việc tắt configuration method proxy **không tắt** Spring AOP/transaction proxy trên một service khác. Những proxy đó còn phụ thuộc annotation, advisor, transaction manager và infrastructure thực tế.

Ngược lại, dù cấu hình full bật `proxyBeanMethods=true`, không thể suy ra mỗi business method trong mọi bean đều được AOP intercept. Đây là **hai cơ chế proxy có mục đích khác nhau**.

### Self-invocation ở hai ngữ cảnh khác nhau

- Trong **full `@Configuration`**, lời gọi từ một `@Bean` method sang một `@Bean` method khác trên enhanced configuration instance có thể được intercept bởi CGLIB cấu hình để lấy managed bean.
- Trong **Spring AOP proxy mode** thông thường, self-invocation từ một method target sang method khác trên cùng target có thể **bỏ qua advice** vì lời gọi không đi qua external proxy.

Không nên lấy quy tắc self-invocation của AOP rồi khẳng định full `@Configuration` cũng luôn bỏ qua interception; ngược lại cũng không lấy interception của `@Bean` để suy ra self-invocation của `@Transactional` sẽ hoạt động.

## 12. CGLIB: lợi ích và ràng buộc

Full mode cần subclassing/method interception nên có những ràng buộc đáng lưu ý:

### Class cấu hình không final

```java
// Phản ví dụ trong full mode:
@Configuration
public final class FinalFullConfig {
    @Bean
    Catalog catalog() {
        return new Catalog();
    }
}
```

CGLIB không thể subclass class `final`. Nếu thật sự cần full interception, class cấu hình và những instance `@Bean` methods cần override phải đáp ứng giới hạn của Spring/CGLIB.

### Phương thức @Bean private/final

```java
// Phản ví dụ về method cần full interception:
@Configuration
class FullConfig {
    @Bean
    private final Catalog catalog() {
        return new Catalog();
    }
}
```

Một method `private` hoặc `final` không thể được CGLIB override theo cách cần cho intercepted bean method; ở full mode cấu hình có thể không hợp lệ. Không dùng chúng với giả định Spring vẫn có thể intercept method call bình thường.

### Lite mode giảm ràng buộc subclassing

Khi tắt `proxyBeanMethods`, Spring **không cần subclass class cấu hình để intercept bean methods**, nên một số hạn chế do enhancement được nới lỏng. Điều đó hữu ích cho class immutable/final hoặc code do compiler khác tạo, miễn vẫn thỏa các điều kiện của Java, `@Bean` factory methods và cấu hình container.

Ví dụ có thể dùng:

```java
@Configuration(proxyBeanMethods = false)
public final class ImmutableConfig {
    @Bean
    public Catalog catalog() {
        return new Catalog();
    }
}
```

Đây là **minh họa riêng**, không nằm trong file Java chạy đầy đủ. Lite mode cho phép `final` ở góc nhìn không cần CGLIB subclassing. Tuy nhiên, không nên dùng điểm này để hợp thức hóa lời gọi giữa các bean factory methods; ràng buộc lifecycle/DI vẫn như phần trước.

### Không cần tự thêm dependency CGLIB ngoài

Spring Framework đã tích hợp/repackage các thành phần CGLIB cần dùng trong `spring-core`. Bạn không phải thêm một thư viện CGLIB riêng chỉ để `@Configuration` full mode hoạt động. Điều kiện quan trọng là class/method hợp lệ cho enhancement và phiên bản Spring/JDK tương thích.

## 13. Lite mode và static @Bean

Spring cho phép khai báo một số `@Bean` methods là `static`. Method static không cần instance config để gọi, cũng không phải đối tượng cho instance-method interception:

```java
@Configuration(proxyBeanMethods = false)
class InfrastructureConfig {

    @Bean
    static CustomRegistryPostProcessor customRegistryPostProcessor() {
        return new CustomRegistryPostProcessor();
    }
}
```

Đoạn minh họa cần định nghĩa `CustomRegistryPostProcessor`, không phải chương trình độc lập. Static `@Bean` có ích cho một số bean infrastructure được cần **rất sớm** trong quá trình container bootstrap, đặc biệt các loại `BeanFactoryPostProcessor`/`BeanDefinitionRegistryPostProcessor` thích hợp.

Lý do: Spring có thể gọi static factory method **mà không buộc tạo instance cấu hình quá sớm**, qua đó tránh một số vấn đề về dependency injection hoặc BeanPostProcessor trên chính class cấu hình.

Nhưng `static` không tự động làm bean singleton/prototype hoặc biến nó thành đối tượng không được quản lý. Container vẫn dựa trên bean definition, scope, lifecycle. Chỉ lời gọi `InfrastructureConfig.customRegistryPostProcessor()` do code Java tự thực hiện sẽ là direct static factory call, không tự lấy managed singleton.

Không chuyển tất cả bean methods thành static chỉ vì dùng lite mode. Đây là kỹ thuật hạ tầng dành cho tình huống cụ thể.

## 14. @Import, @ComponentScan và nhiều class cấu hình

Lite mode không giới hạn việc ghép cấu hình bằng `@Import`:

```java
@Configuration(proxyBeanMethods = false)
class RepositoryConfig {

    @Bean
    OrderRepository orderRepository() {
        return new InMemoryOrderRepository();
    }
}

@Configuration(proxyBeanMethods = false)
@Import(RepositoryConfig.class)
class ServiceConfig {

    @Bean
    OrderService orderService(OrderRepository repository) {
        return new OrderService(repository);
    }
}
```

Đây là ví dụ minh họa, cần interface/implementation tương ứng và import `org.springframework.context.annotation.Import`. Khi đăng ký `ServiceConfig`, Spring import `RepositoryConfig` và có thể tiêm `OrderRepository` qua tham số `orderService(...)`.

Tại đây, **không cần** viết:

```java
// Phản ví dụ với config không được quản lý:
@Bean
OrderService orderService() {
    return new OrderService(new RepositoryConfig().orderRepository());
}
```

Đoạn đó tạo `RepositoryConfig` ngoài container và gọi factory trực tiếp, có nguy cơ tạo repository ngoài managed bean identity/lifecycle.

`@ComponentScan` cũng tiếp tục có ý nghĩa:

```java
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.example.application")
class ApplicationConfig {
}
```

Các class mang stereotype nằm trong vùng quét có thể được đăng ký như bình thường; lite mode **không tắt quét component**. Cần phân biệt class cấu hình có được **đăng ký vào context** hay không với việc Spring có intercept các phương thức `@Bean` trên class đó hay không.

## 15. Phụ thuộc tùy chọn và nhiều bean trong lite mode

Tham số của `@Bean` method được Spring giải quyết bằng dependency injection tiêu chuẩn. Có thể dùng:

### Dependency bắt buộc

```java
@Bean
PaymentService paymentService(PaymentGateway gateway) {
    return new PaymentService(gateway);
}
```

Thiếu bean `PaymentGateway` phù hợp là lỗi cấu hình; Spring thường thất bại khi tạo `paymentService`.

### Dependency tùy chọn

```java
@Bean
ReportService reportService(
        java.util.Optional<AuditSink> auditSink) {
    return new ReportService(auditSink);
}
```

Nếu không có bean `AuditSink`, Optional có thể rỗng, miễn dependency thực sự là tùy chọn theo nghiệp vụ. Lite mode không làm mọi dependency tự chuyển thành optional.

### Nhiều ứng viên

```java
@Bean
Pipeline pipeline(java.util.List<PipelineStep> steps) {
    return new Pipeline(steps);
}
```

Spring có thể tập hợp nhiều bean `PipelineStep` thành danh sách theo các quy tắc collection injection. Nếu pipeline cần thứ tự rõ, cấu hình `@Order`/`Ordered` và kiểm thử; nếu cần lookup động 0..n bean, cân nhắc `ObjectProvider<PipelineStep>`.

**Điểm chính:** Constructor/factory-method parameter injection hoạt động trong full và lite. `proxyBeanMethods=false` không làm Spring bỏ qua `@Qualifier`, generic type, `@Primary`, dependency resolution hoặc lifecycle.

## 16. Hiểu đúng hiệu năng và bootstrapping

Lý do phổ biến để dùng lite configuration là tránh enhancement CGLIB cho class cấu hình khi không cần inter-bean calls. Điều này có thể:

- Giảm lượng lớp proxy/enhanced class cần tạo lúc khởi động.
- Giảm overhead ở giai đoạn cấu hình.
- Tránh ràng buộc subclassing với final class/method.
- Làm hợp đồng wiring nhất quán hơn khi luôn dùng method parameter injection.

Tuy nhiên, **không có một con số tăng tốc chung** cho mọi ứng dụng. Thời gian startup thường còn phụ thuộc việc scan classpath, số bean, tạo connection pool, I/O và các phần khác. Một ứng dụng chỉ có vài `@Bean` methods có thể không cảm nhận được lợi ích đáng kể.

Quan trọng hơn, chuyển full sang lite **có thể thay đổi hành vi** nếu code đang dựa vào inter-bean method interception. Hãy ưu tiên đúng semantics trước; đo hiệu năng sau bằng benchmark thực tế.

### Về Spring Boot

Spring Boot dùng nhiều Java configurations, và một số auto-configuration được khai báo với `proxyBeanMethods=false` vì các bean factory methods có thể thiết kế độc lập và nhận dependencies qua tham số. Điều đó không làm toàn bộ ứng dụng Boot tự thành lite mode; mỗi configuration class có metadata riêng.

`@SpringBootApplication(proxyBeanMethods = false)` là một tùy chọn ở Boot để cấu hình cách xử lý bean methods trên **class ứng dụng được chú giải**, không đồng nghĩa bật/tắt toàn bộ Spring AOP hay áp đặt cờ này lên mọi configuration class nhập vào. Đây là ranh giới khái niệm; ví dụ Maven chính ở mục 7 chỉ dùng Spring Framework thuần.

## 17. Các lỗi thường gặp và cách chẩn đoán

| Triệu chứng | Nguyên nhân thường gặp | Cách khắc phục |
| --- | --- | --- |
| Hai object có cùng class nhưng `==` false dù bean singleton | Lite config gọi trực tiếp `@Bean` method bên trong factory khác | Dùng method parameter injection và so với `context.getBean(...)` |
| Init callback chỉ chạy trên một instance | Instance còn lại do direct Java method call tạo, không qua BeanFactory | Đảm bảo consumer dùng managed bean |
| Tài nguyên bị mở hai lần hoặc không được đóng hết | Direct call trong lite mode tạo resource không do context quản lý | Refactor dependency qua tham số/factory, kiểm tra lifecycle |
| `@Configuration` không enhance được | Full mode nhưng class/method `final` hoặc `private` không phù hợp | Loại ràng buộc cản enhancement hoặc dùng lite với wiring đúng |
| Chuyển `proxyBeanMethods=false` rồi test thất bại | Code cũ dựa vào CGLIB redirect inter-bean calls | Rà soát và thay lời gọi trực tiếp bằng injection |
| `@Bean` trong `@Component` tạo bean nhưng direct call sai identity | Plain component đã là lite mode | Không gọi một bean method từ bean method khác để lấy managed bean |
| `NoSuchBeanDefinitionException` khi parameter injection | Bean dependency chưa đăng ký/import/scanned | Kiểm tra configuration class, type, qualifier, bean definitions |
| `NoUniqueBeanDefinitionException` | Có nhiều ứng viên cùng type và thiếu quy tắc chọn | Dùng `@Qualifier`/`@Primary` hoặc nhận collection đúng nhu cầu |
| Bean không lazy dù dùng provider | Singleton được Spring eager-initialize bởi cơ chế khác | Kiểm tra `@Lazy`, scope, eager dependencies |
| Tưởng `proxyBeanMethods=false` tắt `@Transactional` | Nhầm configuration method proxy với AOP proxy | Kiểm tra AOP/transaction infrastructure riêng |
| Prototype direct call có vẻ tạo mới đúng nhưng lifecycle sai | Java method trả object mới ngoài bean post-processing | Lấy prototype qua BeanFactory/`ObjectProvider` |
| Test chỉ gọi `new LiteConfig().catalog()` | Bỏ qua container nên không kiểm tra được managed identity | Dùng `AnnotationConfigApplicationContext` trong integration test |

Quy trình debug có thứ tự:

1. Xác định class đang dùng **full** (`@Configuration` mặc định), **lite** (`proxyBeanMethods=false`), hay plain `@Component`.
2. Tìm tất cả factory methods `@Bean` và các lời gọi trực tiếp giữa chúng.
3. Kiểm tra bean lấy bằng `context.getBean(...)` có cùng reference với dependency được consumer giữ không.
4. Phân biệt **bean singleton của container** với object Java tự tạo bằng `new` hoặc direct method call.
5. Kiểm tra lifecycle callbacks và khả năng bean bị proxy/scoped.
6. Đưa inter-bean dependencies vào factory method parameters.
7. Chạy lại integration test trước khi đo tối ưu startup.

## 18. Kiểm thử để ngăn regression

Các test quan trọng khi chuyển full sang lite:

### Kiểm tra bean identity

Với ví dụ mục 7, assert:

```java
// Đoạn kiểm tra bên trong report(...) khi context còn mở:
Catalog managed = context.getBean(Catalog.class);

CatalogConsumer injected =
        context.getBean("injectedConsumer", CatalogConsumer.class);

if (injected.catalog() != managed) {
    throw new AssertionError(
            "Factory method parameter did not inject managed Catalog");
}
```

Đây là đoạn bổ sung, không phải class độc lập. Nó chứng minh consumer nhận **đúng instance** container quản lý, không chỉ một object cùng kiểu.

### Kiểm tra lifecycle callback

Tạo test resource bean có `initMethod` và `destroyMethod`, kiểm tra số lần mở/đóng của managed bean khi context startup/close. Nếu code tạo thêm resource từ direct call, kết quả sẽ chỉ ra rủi ro leak hoặc thiếu initialization.

### Kiểm tra bean definition và scope

Dùng `context.getBean(Catalog.class)` hai lần để xác nhận singleton trong context. Điều này **không thay thế** test identity của object nằm trong consumer: container singleton vẫn đúng ngay cả khi consumer tình cờ giữ unmanaged object khác.

### Kiểm tra cấu hình nhiều module

Nếu một `@Configuration(proxyBeanMethods=false)` phụ thuộc bean từ `@Import` khác, integration test nên khởi tạo đúng class cấu hình root, tránh mock bằng `new` rồi nghĩ rằng dependency resolution hoạt động.

### Kiểm tra fail-fast của dependency bắt buộc

Với tham số `@Bean Consumer consumer(RequiredRepository repo)`, context phải phát hiện thiếu `RequiredRepository` khi tạo consumer. Đây là hành vi tốt; đừng chuyển dependency bắt buộc thành `getIfAvailable()` rồi bỏ qua chỉ để test startup xanh.

## 19. Bài tập và đáp án

### Bài 1: hai chế độ khác nhau ở đâu?

`@Configuration` và `@Configuration(proxyBeanMethods=false)` đều có method `@Bean`. Cả hai có tạo bean definitions không? Khác biệt cốt lõi?

**Đáp án:** Cả hai đều tạo bean definitions; bean do container tạo được quản lý bình thường. Khác biệt cốt lõi là full configuration có thể intercept inter-bean method calls trên enhanced config instance, còn lite thì direct calls chỉ là Java method invocations.

### Bài 2: dự đoán identity

```java
@Configuration(proxyBeanMethods = false)
class Config {
    @Bean
    Worker worker() { return new Worker(); }

    @Bean
    Manager manager() { return new Manager(worker()); }
}
```

`Manager` có chắc giữ đúng bean `worker` singleton do `context.getBean(Worker.class)` trả về không?

**Đáp án:** Không. `worker()` được gọi trực tiếp trong `manager()`, tạo object Java riêng theo body method. Sửa thành `Manager manager(Worker worker) { return new Manager(worker); }`.

### Bài 3: làm việc trong @Component

Một class `@Component` có hai `@Bean` methods và một method gọi method kia. Có được Spring tự intercept như full `@Configuration` không?

**Đáp án:** Không. Đây là lite `@Bean` processing. Dùng injection qua method parameter nếu consumer cần một managed bean.

### Bài 4: thay đổi proxyBeanMethods có làm mất @Bean không?

Bạn đổi `@Configuration` thành `@Configuration(proxyBeanMethods=false)` và không thay gì khác. Các `@Bean` methods còn được đăng ký chứ?

**Đáp án:** Có, nếu configuration class vẫn được nạp đúng. Nhưng **hành vi direct method call** có thể đổi, nên cần rà soát code thay vì kết luận không có tác động.

### Bài 5: full mode và new Config()

`new FullConfig().catalog()` có chắc nhận bean singleton của context không?

**Đáp án:** Không. Instance tạo bằng `new` không phải enhanced configuration bean được container quản lý.

### Bài 6: prototype

Một prototype bean được gọi qua method `@Bean` trực tiếp trong lite mode có chắc nhận lifecycle và post-processors của Spring không?

**Đáp án:** Không. Nó chỉ là object do Java method tạo. Muốn managed prototype, phân giải bean qua container/`ObjectProvider`.

### Bài 7: so sánh với Spring AOP

`proxyBeanMethods=false` có đồng nghĩa tắt proxy của `@Transactional` không?

**Đáp án:** Không. Nó chỉ tắt CGLIB interception dành cho bean factory methods của configuration class. AOP transaction proxy là cơ chế khác với điều kiện kích hoạt riêng.

### Bài 8: tối ưu startup

Bạn có một config không có direct inter-bean calls, mọi dependency đều được inject qua factory method parameters. Có thể cân nhắc cờ nào?

**Đáp án:** `@Configuration(proxyBeanMethods=false)` có thể giảm enhancement overhead mà vẫn giữ đúng wiring. Cần test cả managed instance và behavior quan trọng sau khi chuyển.

### Bài 9: static @Bean

Một `static @Bean` method có được CGLIB override/intercept giống instance method trong full configuration không?

**Đáp án:** Không. Static method không phải virtual instance method. Container vẫn có thể quản lý bean tạo qua bean definition, nhưng direct static call là Java thường.

### Bài 10: sửa cấu hình nhiều tầng

```java
@Configuration(proxyBeanMethods = false)
class Config {
    @Bean
    A a() { return new A(); }

    @Bean
    B b() { return new B(a()); }

    @Bean
    C c() { return new C(b()); }
}
```

Hãy sửa để `B` và `C` dùng đúng managed dependencies.

**Đáp án định hướng:**

```java
@Configuration(proxyBeanMethods = false)
class Config {
    @Bean
    A a() { return new A(); }

    @Bean
    B b(A a) { return new B(a); }

    @Bean
    C c(B b) { return new C(b); }
}
```

Các class `A`, `B`, `C` là kiểu minh họa; bài tập cần bổ sung định nghĩa thực nếu muốn biên dịch.

## 20. Checklist trước khi áp dụng vào dự án

- [ ] Xác định có thật sự cần CGLIB inter-bean references không.
- [ ] Nếu chọn lite, không viết `@Bean x() { return new X(y()); }` chỉ để lấy bean `y`.
- [ ] Chuyển các dependency thành factory method parameters hoặc constructor injection.
- [ ] Dùng `@Qualifier` khi có nhiều bean cùng type.
- [ ] Với prototype/scoped beans, truy vấn thông qua container/provider thay vì gọi factory method trực tiếp.
- [ ] Không giả định `proxyBeanMethods=false` tắt AOP proxies hoặc các lifecycle callbacks.
- [ ] Kiểm tra class cấu hình vẫn được import/scanned/registered vào context.
- [ ] Khi cần dùng final class/method, xác minh config được xử lý lite và các ràng buộc khác vẫn hợp lệ.
- [ ] Viết test cho identity, initialization, destruction và các bean quan trọng.
- [ ] Xác minh không tạo trùng các resource tốn kém như connection pool, HTTP client hoặc executor.
- [ ] Chỉ báo cải thiện hiệu năng khi có đo đạc, không suy đoán một mức tăng tốc cố định.

## Kết luận

**Full `@Configuration`** dùng CGLIB để bảo toàn inter-bean method references khi gọi `@Bean` methods trên enhanced configuration bean do Spring quản lý. **Lite mode**, gồm `@Configuration(proxyBeanMethods=false)` và `@Bean` methods trong plain `@Component`, không intercept các direct calls như vậy: chúng hành xử như phương thức Java thường.

Hai mode vẫn có **bean definitions, dependency resolution, singleton/prototype scopes và lifecycle đầy đủ cho các object được Spring tạo và quản lý**. Sự cố xuất hiện khi code vô tình tạo thêm object bằng direct factory-method call rồi coi chúng là managed beans.

Quy tắc áp dụng bền vững nhất: **mỗi `@Bean` method nên tự làm nhiệm vụ factory của nó, còn dependency cần lấy từ container nên truyền qua tham số**. Khi tuân thủ điều này, bạn có thể dùng lite mode một cách rõ ràng, giảm nhu cầu CGLIB enhancement mà không thay đổi identity và vòng đời các dependency.

## Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Basic Concepts: @Bean and @Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/basic-concepts.html): full/lite mode và inter-bean method calls.
- [Spring Framework 6.2 — Using the @Configuration Annotation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/configuration-annotation.html): CGLIB enhancement, scope và điều kiện sử dụng.
- [Spring Framework 6.2 — Using the @Bean Annotation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html): factory methods, lifecycle, scope và lite mode.
- [Spring Framework 6.2 — @Configuration Javadoc](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/context/annotation/Configuration.html): hợp đồng `proxyBeanMethods` và ràng buộc subclass.
- [Spring Framework 6.2 — @Bean Javadoc](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/context/annotation/Bean.html): cách `@Bean` được xử lý trong full/lite classes.
- [Spring Framework 6.2 — Classpath Scanning and Managed Components](https://docs.spring.io/spring-framework/reference/6.2/core/beans/classpath-scanning.html): `@Bean` trong plain `@Component` và khác biệt với `@Configuration`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-configuration-008">Hành vi gọi trực tiếp giữa các phương thức @Bean</a>

<details>
<summary>Xem chi tiết</summary>

# Hành vi gọi trực tiếp giữa các phương thức @Bean trong Spring Core Container

## 1. Mục tiêu và tình huống

Trong Java configuration của Spring, chúng ta có thể thấy một phương thức `@Bean` gọi trực tiếp một phương thức `@Bean` khác:

```java
@Configuration
public class ShopConfig {

    @Bean
    StockRepository stockRepository() {
        return new StockRepository();
    }

    @Bean
    StockService stockService() {
        return new StockService(stockRepository());
    }
}
```

`StockRepository` và `StockService` trong đoạn đầu là hai class nghiệp vụ minh họa, cần định nghĩa và import để biên dịch. Nhìn như Java thuần, `stockRepository()` sẽ chạy mỗi lần được gọi, có thể tạo thêm object. Nhưng với **full `@Configuration`**, Spring thay đổi hành vi lời gọi này bằng cơ chế **CGLIB method interception**, để tham chiếu qua phương thức `@Bean` vẫn đi qua container và tuân theo scope. Khi **tắt interception** hoặc dùng **lite `@Bean`**, cùng cú pháp Java đó có thể thực sự tạo object mới, không phải đối tượng do Spring quản lý.

Đây là một trong những khác biệt quan trọng nhất khi học Java configuration.

Sau bài học, bạn có thể:

1. Giải thích full `@Configuration` và lite `@Bean` khác nhau thế nào khi một factory method gọi factory method khác.
2. Dự đoán identity, số lần tạo object, phạm vi `singleton` và `prototype` trong từng chế độ.
3. Hiểu điều kiện áp dụng interception, những ràng buộc với `final`, `private`, `static` và CGLIB.
4. Phân biệt **container gọi method để tạo bean** với **người dùng gọi method trong Java**.
5. Dùng **tham số của `@Bean` method** thay cho lời gọi chéo để biểu diễn dependency nhất quán với `@Configuration(proxyBeanMethods=false)`.
6. Nhận ra lỗi khó tìm như hai datasource, hai mapper, hai connection pool hoặc callback lifecycle không được áp dụng cho object phụ.
7. Chạy ví dụ Spring Framework 6.2.x, Java 17+ so sánh full configuration, lite configuration và injection qua tham số, có stdout xác định.

**Phạm vi:** cơ chế Spring Core Container đối với các **lời gọi trực tiếp giữa phương thức `@Bean`**. Không nhầm với việc method nghiệp vụ của bean gọi method khác, Spring AOP self-invocation hoặc dependency injection vào một field thông thường.

## 2. @Bean là factory method, nhưng Spring có thể can thiệp lời gọi

Annotation `@Bean` yêu cầu Spring đăng ký một bean definition được tạo bởi phương thức tương ứng:

```java
@Bean
Clock clock() {
    return Clock.systemUTC();
}
```

Đoạn này cần import `org.springframework.context.annotation.Bean` và `java.time.Clock`, đặt trong class cấu hình được Spring nạp. Container biết có bean tên `clock` (nếu không cấu hình tên khác), có return type `Clock` và có thể gọi factory method khi cần tạo instance.

Hai hoạt động khác nhau:

- **Container-managed lookup:** `context.getBean("clock", Clock.class)` yêu cầu bean do Spring quản lý. Singleton thông thường được trả lại từ cache sau lần khởi tạo đầu tiên.
- **Direct Java call:** `clock()` hoặc `config.clock()` trông như gọi method Java bình thường. Nó có được đưa qua container hay không phụ thuộc vào **chế độ xử lý configuration class** và phương thức có được override/intercept hay không.

Vì thế, đừng trả lời đơn giản rằng “cứ gọi một `@Bean` method là luôn tạo object mới” hoặc “cứ gọi `@Bean` method là luôn lấy singleton”. Cả hai đều có thể sai.

## 3. Phân loại lời gọi trực tiếp giữa factory methods

### 3.1. Full `@Configuration`

Với `@Configuration` mặc định (`proxyBeanMethods=true`), Spring tăng cường instance cấu hình bằng CGLIB. Một lời gọi `otherBean()` **đủ điều kiện trên enhanced config** có thể chuyển về `BeanFactory`: singleton được chia sẻ và những scope khác được áp dụng theo quy tắc container.

### 3.2. Lite `@Configuration(proxyBeanMethods=false)`

Bean definitions vẫn được đăng ký và quản lý, nhưng method `otherBean()` được gọi trực tiếp sẽ chạy body như Java thông thường. Nếu body tạo object mới, object đó có thể nằm ngoài managed singleton. Đừng nhầm object này với bean prototype.

### 3.3. `@Bean` trong plain `@Component`

Spring có thể phát hiện factory methods trong class `@Component` đã được scan/đăng ký, nhưng chúng mang semantics **lite** cho inter-bean calls. `@Component` không đồng nghĩa full `@Configuration`.

### 3.4. Bảng đường đi của lời gọi

| Lời gọi | BeanFactory semantics? |
| --- | --- |
| `context.getBean("repository")` | Có, bất kể full/lite |
| `repository()` trên enhanced full config | Có nếu instance method được chặn hợp lệ |
| `repository()` trên lite config / plain `@Component` | Không, chạy body trực tiếp |
| `staticRepository()` trên mọi config | Không chặn lời gọi static |
| `new FullConfig().repository()` ngoài Spring | Không có enhancement |

Chỉ nhìn thấy annotation `@Bean` trên method chưa đủ để kết luận method call sẽ nhận bean từ container; phải xét **receiver và đường đi lời gọi**.

## 4. Vì sao full configuration dùng CGLIB?

Spring cần phân biệt **factory invocation để tạo bean** và **lời gọi tham chiếu bean** khi method `@Bean` này gọi method `@Bean` khác. Với full config, CGLIB tạo subclass và override method hợp lệ để chuyển lời gọi tham chiếu sang BeanFactory:

```text
BeanFactory tạo serviceA
    -> enhancedConfig.serviceA()
    -> repository() được intercept
    -> BeanFactory lấy bean 'repository' theo scope
    -> serviceA nhận managed reference
```

Nếu bean là singleton, những consumer dùng cùng bean; nếu là prototype hoặc custom scope, container áp dụng chính scope ấy. Nếu bean được bọc AOP/scoped proxy, reference do container trả có thể là proxy chứ không phải raw object do factory body tạo. Đó là khác biệt có thể thấy bằng identity và lifecycle.

### 4.1. Factory call không phải lúc nào cũng là lookup

Khi BeanFactory đang thật sự tạo bean tương ứng, interceptor phải cho phép chạy factory body; khi enhanced configuration gọi method để **tham chiếu bean khác**, interceptor thực hiện lookup. Nếu không phân biệt hai trường hợp, dễ hiểu sai thành một vòng đệ quy vô hạn hoặc cache Java cho mọi lần gọi. Đây là mô hình khái niệm, không phải source code nội bộ CGLIB.

### 4.2. Giới hạn full configuration

Subclassing/overriding yêu cầu configuration class không `final`, instance `@Bean` methods không `private`/`final`; `static @Bean` không được override. Tạo `new Config()` ngoài Spring không có enhancement. Khi cần cấu hình lite, **truyền dependency bằng tham số `@Bean`** là cách ổn định hơn.

## 5. Cách an toàn: inject dependency qua tham số @Bean

Thay:

```java
@Configuration(proxyBeanMethods = false)
class Config {
    @Bean
    Repository repository() {
        return new Repository();
    }

    @Bean
    Service service() {
        return new Service(repository()); // Java direct call!
    }
}
```

bằng:

```java
@Configuration(proxyBeanMethods = false)
class Config {
    @Bean
    Repository repository() {
        return new Repository();
    }

    @Bean
    Service service(Repository repository) {
        return new Service(repository); // Spring resolve dependency.
    }
}
```

`Repository` và `Service` là kiểu minh họa. Trong biến thể thứ hai, Spring giải quyết tham số `Repository` từ bean definitions, dùng scope/qualifier/primary như các injection point khác. Không cần enhancement hoặc gọi chéo phương thức để chia sẻ bean.

**Lợi ích:**

- Hoạt động cùng nguyên tắc ở full mode và lite mode.
- Dependency graph rõ trong method signature.
- Dễ phân định nhiều implementation bằng `@Qualifier`.
- Dễ phát hiện lỗi dependency thiếu hoặc mơ hồ.
- Không vô tình tạo thêm client/connection pool/mapper ngoài container.
- Giảm phụ thuộc vào CGLIB và cách Spring xử lý lời gọi method.
- Cho phép `@Configuration(proxyBeanMethods=false)` mà không thay semantics mong đợi của dependency.

Trong Java configuration hiện đại, đây thường là lựa chọn dễ đọc và ít ngạc nhiên nhất.

## 6. Ví dụ hoàn chỉnh Java 17+: so sánh ba chế độ

Ví dụ thực hành không dùng Spring Boot, JDBC hoặc web server. Nó sử dụng **Spring Framework 6.2.9**, **JDK 17+** và `AnnotationConfigApplicationContext`.

Chúng ta thử ba context **riêng biệt**:

1. **FullConfig:** `@Configuration` mặc định intercept lời gọi chéo `widget()`.
2. **LiteConfig:** `@Configuration(proxyBeanMethods=false)` để lời gọi `widget()` trong `pair()` chạy như Java thuần.
3. **ParamConfig:** cũng tắt interception, nhưng `pair(Widget widget)` nhận tham số từ container nên vẫn dùng singleton đã đăng ký.

`Widget` là một đối tượng đơn giản có id. Mỗi khi factory body thực sự tạo object, counter tăng. `Pair` giữ hai tham chiếu tới Widget. So sánh bằng toán tử `==` để kiểm tra **object identity**, không phụ thuộc `equals()`.

### 6.1. Cấu trúc dự án

```text
interbean-calls-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        └── java/
            └── demo/
                └── InterBeanCallsDemo.java
```

**`settings.gradle`**

```groovy
rootProject.name = 'interbean-calls-demo'
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
    mainClass = 'demo.InterBeanCallsDemo'
}
```

**`src/main/java/demo/InterBeanCallsDemo.java`**

```java
package demo;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class InterBeanCallsDemo {
    private static final AtomicInteger FULL_CALLS = new AtomicInteger();
    private static final AtomicInteger LITE_CALLS = new AtomicInteger();
    private static final AtomicInteger PARAM_CALLS = new AtomicInteger();

    // Java 17 record: immutable value giữ mã nhận diện factory invocation.
    record Widget(int id) {}
    record Pair(Widget first, Widget second) {}

    // Full mode: method interception bật theo mặc định.
    @Configuration
    public static class FullConfig {
        @Bean
        public Widget widget() {
            return new Widget(FULL_CALLS.incrementAndGet());
        }

        @Bean
        public Pair pair() {
            return new Pair(widget(), widget());
        }
    }

    // Lite mode: lời gọi chéo là lời gọi Java thông thường.
    @Configuration(proxyBeanMethods = false)
    public static class LiteConfig {
        @Bean
        public Widget widget() {
            return new Widget(LITE_CALLS.incrementAndGet());
        }

        @Bean
        public Pair pair() {
            return new Pair(widget(), widget());
        }
    }

    // Không intercept nhưng dùng method argument injection.
    @Configuration(proxyBeanMethods = false)
    public static class ParamConfig {
        @Bean
        public Widget widget() {
            return new Widget(PARAM_CALLS.incrementAndGet());
        }

        @Bean
        public Pair pair(Widget widget) {
            return new Pair(widget, widget);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(FullConfig.class)) {
            Widget managed = context.getBean(Widget.class);
            Pair pair = context.getBean(Pair.class);

            System.out.println("full.calls=" + FULL_CALLS.get());
            System.out.println("full.pairSame="
                    + (pair.first() == pair.second()));
            System.out.println("full.managedSame="
                    + (pair.first() == managed));
            System.out.println("full.externalCallManaged="
                    + (context.getBean(FullConfig.class).widget() == managed));
        }

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(LiteConfig.class)) {
            Widget managed = context.getBean(Widget.class);
            Pair pair = context.getBean(Pair.class);

            System.out.println("lite.calls=" + LITE_CALLS.get());
            System.out.println("lite.pairSame="
                    + (pair.first() == pair.second()));
            System.out.println("lite.managedSame="
                    + (pair.first() == managed));
            System.out.println("lite.externalCallManaged="
                    + (context.getBean(LiteConfig.class).widget() == managed));
            System.out.println("lite.callsAfterExternal=" + LITE_CALLS.get());
        }

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ParamConfig.class)) {
            Widget managed = context.getBean(Widget.class);
            Pair pair = context.getBean(Pair.class);

            System.out.println("param.calls=" + PARAM_CALLS.get());
            System.out.println("param.pairSame="
                    + (pair.first() == pair.second()));
            System.out.println("param.managedSame="
                    + (pair.first() == managed));
        }
    }
}
```

Các nested class `FullConfig`, `LiteConfig`, `ParamConfig` được khai báo `public static` nên dùng được làm Java configuration class. Chỉ có `InterBeanCallsDemo` là public top-level class; file trùng tên class, code không cần package khác.

`Widget`/`Pair` là nested records, thích hợp với Java 17. Vì các class configuration không `final` và `@Bean` methods ở full mode là `public`, Spring có thể enhanced `FullConfig`.

### 6.2. Chạy

Trong thư mục gốc `interbean-calls-demo`:

```bash
gradle run --quiet
```

Nếu dự án có Gradle Wrapper, dùng `./gradlew run --quiet` (Linux/macOS) hoặc `gradlew.bat run --quiet` (Windows). Lệnh `javac InterBeanCallsDemo.java` không hoạt động đơn lẻ nếu chưa cung cấp dependency Spring trên classpath; Gradle thực hiện điều này.

**Stdout do chính chương trình in ra:**

```text
full.calls=1
full.pairSame=true
full.managedSame=true
full.externalCallManaged=true
lite.calls=3
lite.pairSame=false
lite.managedSame=false
lite.externalCallManaged=false
lite.callsAfterExternal=4
param.calls=1
param.pairSame=true
param.managedSame=true
```

Nếu Gradle hoặc logging framework tạo log phụ, chúng không thuộc nội dung `System.out.println` trên.

### 6.3. Phân tích FullConfig

`widget` là singleton mặc định. BeanFactory tạo một instance `Widget` qua factory body, khiến `FULL_CALLS` tăng tới 1.

Khi `pair()` được gọi trong enhanced configuration, hai lời gọi `widget()` được intercept để yêu cầu bean `widget` từ container. Vì singleton đã được tạo hoặc sẽ được tạo theo lookup, hai tham chiếu trong `Pair` trỏ về **cùng bean**. Vì vậy:

```text
full.calls=1
full.pairSame=true
full.managedSame=true
```

Khi code ngoài configuration lấy **chính managed FullConfig bean** rồi gọi `widget()`, method cũng được enhanced. Nó trả bean container quản lý:

```text
full.externalCallManaged=true
```

**Ranh giới quan trọng:** nếu bạn tự `new FullConfig()` rồi gọi method, đó là object không qua Spring enhancement, nên không được kỳ vọng có cùng hành vi.

### 6.4. Phân tích LiteConfig

Container vẫn có bean definition `widget` và `pair`, và khi refresh sẽ tạo singleton thường. Nhưng `pair()` gọi trực tiếp `widget()` hai lần trong body, tạo hai object khác nhau. BeanFactory còn tạo một `Widget` riêng cho bean definition `widget`, nên tổng số invocation của factory body sau refresh là **3**:

```text
lite.calls=3
lite.pairSame=false
lite.managedSame=false
```

Ở đây `pair.first()` không phải managed widget singleton, dù class/return type giống hệt. Nó được tạo bởi **direct Java call** trong method factory khác.

Khi lấy managed `LiteConfig` bean rồi gọi `widget()`, do không có interception, lời gọi ấy tạo thêm object, làm counter tăng lên 4:

```text
lite.externalCallManaged=false
lite.callsAfterExternal=4
```

Điều này không có nghĩa Spring “không quản lý `widget`” ở lite mode. Spring **vẫn quản lý instance mà BeanFactory tạo cho bean `widget`**. Vấn đề chỉ nằm ở **những object phụ được tạo bởi direct method call**.

### 6.5. Phân tích ParamConfig

`ParamConfig` cũng ở lite mode nhưng `pair(Widget widget)` yêu cầu dependency qua chữ ký factory method. Spring resolve `Widget` từ context, cung cấp managed singleton vào tham số. Không cần method interception:

```text
param.calls=1
param.pairSame=true
param.managedSame=true
```

Đây là lý do thực tế nên ưu tiên **method parameter injection** thay cho gọi trực tiếp một `@Bean` method khác khi không cần full mode.

### 6.6. Ý nghĩa của bộ đếm

Counter chỉ đếm số lần **factory method body** `widget()` thật sự thực thi trong mỗi cấu hình. Nó không đếm số lần lookup container nếu lookup trả bean đã cache. `==` kiểm tra cùng tham chiếu object. Vì source dùng các `AtomicInteger` khác nhau cho từng cấu hình và mỗi context tạo/đóng tuần tự, kết quả có thể xác định từ code mà không dựa vào thứ tự BeanFactory trả map.

**Không nên suy luận từ ví dụ rằng Spring luôn khởi tạo các singleton theo một thứ tự cố định ngoài hợp đồng.** Dù `widget` hay `pair` được tạo trước trong lite context, vẫn có ba invocation factory body sau refresh: một để tạo managed `widget` và hai direct calls từ `pair()`.

## 7. Scope ảnh hưởng trực tiếp đến kết quả của inter-bean calls

Full mode bảo toàn **scope**, không bảo đảm hai lời gọi `@Bean` luôn trả cùng object. Ví dụ:

```java
@Configuration
class ScopeConfig {
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    Widget widget() {
        return new Widget(1);
    }

    @Bean
    Pair pair() {
        return new Pair(widget(), widget());
    }
}
```

Đây là ví dụ cấu trúc dùng các record `Widget`/`Pair` của chương trình đầy đủ; cần import `Scope` và `ConfigurableBeanFactory`, hoặc đặt trong cùng package với chúng nếu tách ra kiểu public. Với `widget` là prototype, **mỗi lookup do hai lời gọi `widget()` đã intercept yêu cầu bean prototype mới**. Vì vậy `pair.first() != pair.second()` có thể đúng, ngay trong full mode.

Bảng tổng quát:

| Bean được gọi chéo | Full mode, gọi qua enhanced config | Lite mode, direct call | Factory method parameter |
| --- | --- | --- | --- |
| `singleton` | Lookup tới singleton Spring quản lý, thường cùng instance | Chạy method body, có thể tạo instance mới ngoài container | Spring inject cùng singleton theo type/qualifier |
| `prototype` | Mỗi lookup yêu cầu prototype theo BeanFactory | Chạy method body, có thể tạo instance mới nhưng **không theo lifecycle đầy đủ của prototype bean** | Spring resolve một prototype khi tạo bean nhận tham số |
| `request`/`session` | Lookup tuân theo scope đang hoạt động | Method body trực tiếp có thể bỏ qua semantics scope | Spring resolve dependency tại lúc tạo bean nhận tham số; có thể cần scoped proxy/provider |
| Bean `@Lazy` singleton | Lookup có thể kích hoạt tạo bean khi cần | Chạy body bất kể `@Lazy` trên definition | Spring resolve theo dependency; eager consumer có thể khiến lazy target được tạo sớm |

**Đặc biệt với prototype và singleton consumer:** nếu một singleton `Pair` nhận tham số prototype `Widget` ở lúc khởi tạo, nó giữ **một instance prototype đã được giải quyết tại thời điểm đó**. Để có prototype mới theo mỗi lời gọi nghiệp vụ, dùng `ObjectProvider<Widget>`, `Provider<Widget>` hoặc cơ chế lookup phù hợp. Đừng gọi method `@Bean` trong business service để thử giải quyết scope.

### 7.1. Vì sao direct call tới prototype cũng là vấn đề?

Một người có thể lập luận: “Prototype vốn phải mới mỗi lần, nên lite direct call `widget()` vẫn tạo object mới, có gì sai?”. Sai lệch nằm ở chỗ **tạo object mới không đồng nghĩa nhận một bean prototype do Spring quản lý**.

Object do direct call tạo có thể bỏ qua:

- Autowiring lên instance trả về;
- `BeanPostProcessor` và lifecycle initialization;
- Bean-specific proxying/AOP;
- Scope resolution hoặc các dependency đặc biệt;
- Những quy tắc container khác gắn với bean definition.

Cho dù số instance tình cờ giống nhau, **semantics không tương đương**.

## 8. Interception và lifecycle của object ngoài container

Một example quen thuộc là `DataSource`:

```java
@Bean
DataSource dataSource() {
    return buildPooledDataSource();
}

@Bean
JdbcTemplate jdbcTemplate() {
    return new JdbcTemplate(dataSource());
}
```

Đây là ví dụ cấu trúc, cần dependency `spring-jdbc`, interface `javax.sql.DataSource` và factory `buildPooledDataSource()` do ứng dụng cung cấp. Nếu dùng full config, lời gọi `dataSource()` trong `jdbcTemplate()` có thể được intercept và dùng managed datasource singleton.

Nếu chuyển class sang `@Configuration(proxyBeanMethods=false)` nhưng giữ nguyên lời gọi, Spring có thể tạo:

1. Một connection pool cho bean `dataSource` quản lý bởi container.
2. Một connection pool khác từ lời gọi Java trực tiếp bên trong `jdbcTemplate()`.

Pool thứ hai không được đăng ký theo bean definition `dataSource`; quản lý tài nguyên trở nên nguy hiểm: credentials, properties, connection metrics, shutdown callback có thể không thống nhất.

**Cách đáng tin cậy:**

```java
@Bean
JdbcTemplate jdbcTemplate(DataSource dataSource) {
    return new JdbcTemplate(dataSource);
}
```

Lúc này Spring resolve managed datasource cho tham số. Đoạn code này yêu cầu bean `DataSource` được đăng ký hợp lệ; nếu nhiều bean `DataSource`, dùng `@Qualifier` hoặc quy tắc lựa chọn ứng viên tương ứng.

### 8.1. Lifecycle callback không tự áp vào object direct call

Giả sử factory method:

```java
@Bean(initMethod = "start", destroyMethod = "stop")
VendorClient vendorClient() {
    return new VendorClient();
}
```

`VendorClient` là class giả định có `start()`/`stop()`. Spring có thể gọi init/destroy callback cho **instance được BeanFactory tạo và quản lý**. Nếu code lite mode gọi `vendorClient()` thủ công, method body có thể trả một `VendorClient` mới nhưng **container không tự ghi nhận object phụ đó là bean có callback tương ứng**. Kết quả là tài nguyên có thể không được start đúng quy trình hoặc không được stop khi context đóng.

Với SDK bên ngoài, đặc biệt connection pool/thread client, dùng parameter injection và xác định rõ quyền sở hữu tài nguyên.

### 8.2. Proxy và AOP có thể bị bypass

Khi bean đích được wrap bởi Spring proxy, lookup qua context thường trả về proxy. Lời gọi trực tiếp factory method trong lite mode tạo raw object không được đưa qua auto-proxy creator theo đường bean đã đăng ký. Gọi method nghiệp vụ trên raw object này có thể bỏ qua transaction/caching/metrics tùy cấu hình.

Đây là vấn đề **khác nhưng có liên hệ** với Spring AOP self-invocation:

- **Inter-bean call**: method `@Bean` gọi một factory method `@Bean` khác; full configuration CGLIB có cơ chế can thiệp riêng.
- **AOP self-invocation**: method nghiệp vụ `this.inner()` gọi method khác cùng service; trong proxy mode thông dụng nó có thể bỏ qua AOP advice.

Hai cơ chế đều nhắc tới proxy, nhưng **không được đánh đồng** hoặc dùng quy tắc của cái này để thay thế cái kia.

## 9. Khi nào @Configuration(proxyBeanMethods=false) là lựa chọn tốt?

Đây không phải một tùy chọn “bật lên sẽ phá Spring”. Nó là lựa chọn thiết kế hợp lệ khi các factory methods độc lập và **không cần inter-bean Java calls**.

Ví dụ:

```java
@Configuration(proxyBeanMethods = false)
class ApplicationConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    GreetingService greetingService(Clock clock) {
        return new GreetingService(clock);
    }

    @Bean
    AuditService auditService(GreetingService greetingService) {
        return new AuditService(greetingService);
    }
}
```

Đây là ví dụ cấu trúc với `GreetingService` và `AuditService` do ứng dụng định nghĩa. Mọi dependency đều đi qua factory method parameters. Nó có những lợi ích:

- Tránh dynamic subclass CGLIB cho configuration chỉ làm nhiệm vụ factory.
- Cho phép thiết kế configuration class có thể là `final` trong các trường hợp phù hợp.
- Tối giản semantics: direct method call luôn là direct method call; bean lookup luôn qua container.
- Dễ đọc dependency graph qua signatures.
- Giảm nguy cơ hidden behavior do một method trong class gọi method khác.

Nhưng `false` không tự động tốt hơn `true` cho mọi mã. Một cấu hình cũ dựa vào inter-bean calls sẽ phải refactor trước khi đổi; nếu không, có thể tạo object phụ ngoài container.

### 9.1. Không được đổi flag theo kiểu “tối ưu mù”

Trước khi thay:

```java
@Configuration
class OldConfig { ... }
```

bằng:

```java
@Configuration(proxyBeanMethods = false)
class OldConfig { ... }
```

hãy đọc toàn bộ factory methods trong class đó để tìm các direct calls tới `@Bean` methods khác, các calls từ nơi ngoài class vào configuration bean, hoặc các assumptions về scope/lifecycle.

Nếu có, sửa thành method parameter injection trước và bổ sung test **identity/lifecycle/proxy**. Sau đó mới cân nhắc tắt interception.

## 10. Ranh giới: direct calls ở đâu được intercept?

### 10.1. Trong chính full @Configuration class

Trường hợp phổ biến được Spring bảo đảm: method instance `@Bean` gọi một instance `@Bean` method khác trên **enhanced configuration bean**. Spring hướng tham chiếu tới managed bean theo scope.

### 10.2. Từ bên ngoài, qua configuration bean của Spring

```java
FullConfig config = context.getBean(FullConfig.class);
Widget widget = config.widget();
```

Đây là snippet dựa trên ví dụ chạy ở mục 6. `config` là instance Spring-managed và đã được enhanced; vì thế method `widget()` có thể được intercept như ví dụ `full.externalCallManaged=true`.

Tuy nhiên, việc gọi configuration `@Bean` methods từ code nghiệp vụ vẫn làm kiến trúc gắn với factory; constructor injection của `Widget` vào consumer là cách nên dùng hơn.

### 10.3. Từ một object tự new bên ngoài Spring

```java
FullConfig config = new FullConfig();
Widget widget = config.widget();
```

Đây là Java direct call trên object không được enhanced, nên method body chạy thường. Đừng nhầm “class có annotation `@Configuration`” với “mọi instance của class ấy đều được Spring proxy”.

### 10.4. Static @Bean method

```java
@Configuration
class SettingsConfig {
    @Bean
    static Settings settings() {
        return new Settings();
    }

    @Bean
    Client client() {
        return new Client(settings());
    }
}
```

`Settings` và `Client` là kiểu minh họa. Static method không override được bằng CGLIB, nên `settings()` trong `client()` là lời gọi Java thường, ngay cả khi configuration class ở full mode.

Nên sửa thành `Client client(Settings settings)` để Spring cấp bean đã đăng ký.

### 10.5. Các class @Configuration khác

Nếu một config class khác lấy **managed enhanced bean** của full configuration và gọi method đủ điều kiện, interception vẫn có thể áp dụng. Nhưng **gọi `new OtherConfig().beanMethod()`** không có hạ tầng đó.

Thực tế, ghép dependency qua factory method parameter, `@Import` và bean injection rõ ràng tốt hơn truyền configuration class qua lại chỉ để gọi factory.

### 10.6. @Bean method trên plain @Component

Các `@Bean` methods của plain `@Component` class (lite mode) không có CGLIB interception inter-bean calls theo semantics full `@Configuration`, ngay cả khi instance component được Spring quản lý. Vì thế **managed component** và **enhanced configuration** không phải cùng một điều.

## 11. Bean name, @Qualifier và @Primary khi chuyển sang parameter injection

Nếu cấu hình có hai bean cùng type:

```java
@Bean("primaryClock")
Clock primaryClock() {
    return Clock.systemUTC();
}

@Bean("testClock")
Clock testClock() {
    return Clock.fixed(
            java.time.Instant.parse("2024-01-01T00:00:00Z"),
            java.time.ZoneOffset.UTC
    );
}
```

Đây là snippet đặt trong một class configuration, cần import `java.time.Clock`. Lời gọi direct `primaryClock()` trong một factory method khác có thể bị intercept ở full mode, nhưng trong lite mode chạy thân method như Java. Phương án nhất quán:

```java
@Bean
TimeReporter timeReporter(
        @Qualifier("primaryClock") Clock clock) {
    return new TimeReporter(clock);
}
```

`TimeReporter` là kiểu nghiệp vụ giả định; cần import `org.springframework.beans.factory.annotation.Qualifier`. Spring sẽ resolve ứng viên thích hợp theo qualifier và scope. Nếu ứng dụng thật có một bean mặc định, `@Primary` có thể giúp phân định dependency theo type, nhưng qualifier vẫn rõ khi nghiệp vụ cần tên cụ thể.

Không dùng việc gọi một `@Bean` method theo tên như một chiến lược ngầm để chọn bean khi codebase đã chọn lite mode. **Đặt điều kiện chọn ngay tại injection point**.

## 12. Circular dependency và inter-bean calls

Có lúc người học dùng inter-bean calls để lắp một vòng phụ thuộc:

```java
@Bean
A a() {
    return new A(b());
}

@Bean
B b() {
    return new B(a());
}
```

`A` và `B` là kiểu nghiệp vụ giả định. Đây là một vòng constructor dependency. Full mode interception **không tự phá vòng**: BeanFactory có thể phát hiện việc bean đang được tạo lại khi chưa hoàn tất, và ném lỗi vòng phụ thuộc/bean currently in creation.

Lite mode càng có nguy cơ gọi Java đệ quy vô hạn giữa `a()` và `b()`, không qua BeanFactory kiểm soát vòng phụ thuộc theo cách mong đợi.

Phải thiết kế lại dependency: tách trách nhiệm, tách interface giao tiếp, xử lý sau khởi tạo khi hợp lệ, dùng sự kiện hoặc provider/lazy lookup có chủ đích. Không dùng direct `@Bean` method calls như cách né kiểm tra vòng phụ thuộc của container.

### 12.1. Gọi cùng method trong body của nó

Nếu một factory method tự gọi chính nó:

```java
@Bean
A a() {
    return a();
}
```

Đây là ví dụ không hợp lệ về mặt thiết kế: trong full mode, Spring có thể gặp tình huống yêu cầu bean đang được tạo; trong lite mode có thể đệ quy không dừng. Tránh hoàn toàn mô hình tự gọi factory method để khai báo dependency.

## 13. Inter-bean calls và thời điểm khởi tạo

`@Bean` methods được Spring phân tích thành bean definitions trước khi phần lớn singleton thông thường được tạo. Khi full config intercept `b()` từ `a()`, nó có thể **kích hoạt bean `b` được tạo sớm hơn** nếu nó chưa tồn tại.

Điều này không có nghĩa Spring tạo bean đúng theo thứ tự source code mọi lúc. Spring phải xử lý dependency, `@Lazy`, scope, post-processors và nhiều yếu tố lifecycle.

### 13.1. @Lazy và lookup

```java
@Bean
@Lazy
ExpensiveClient expensiveClient() {
    return new ExpensiveClient();
}

@Bean
Service service() {
    return new Service(expensiveClient());
}
```

`ExpensiveClient`/`Service` là kiểu minh họa; import `Lazy`. Với full mode, nếu `service` được khởi tạo eager và gọi `expensiveClient()`, lookup có thể buộc Spring tạo `expensiveClient` **ngay khi cần để lắp service**, dù bean ấy khai báo lazy.

Trong lite mode, method body `expensiveClient()` được gọi trực tiếp từ `service()`, không thông qua bean definition lazy và có thể tạo object khác ngoài container. Không coi `@Lazy` trên factory method là rào chắn đối với các Java calls thủ công.

### 13.2. Post-processors và early instantiation

Các bean đặc biệt như `BeanFactoryPostProcessor`/`BeanPostProcessor` có thể phải được tạo rất sớm. Với static `@Bean` dành cho các processor này, Spring thường khuyến nghị factory method static để tránh tạo configuration instance sớm.

Điều này bổ sung cho chủ đề hiện tại: static `@Bean` methods **không intercept direct calls**, nên đừng gọi static processor factory chỉ để lấy processor trong code nghiệp vụ. Spring cần được tự phát hiện chúng theo return type và đăng ký đúng giai đoạn.

## 14. Ví dụ so sánh số object và lifecycle khi dùng SDK bên ngoài

Giả sử một cấu hình:

```java
@Configuration(proxyBeanMethods = false)
class VendorConfig {
    @Bean
    VendorClient vendorClient() {
        return VendorClient.create();
    }

    @Bean
    VendorService vendorService() {
        return new VendorService(vendorClient());
    }
}
```

`VendorClient`/`VendorService` và `VendorClient.create()` là SDK giả định. Có thể tạo **hai client**: một theo bean `vendorClient`, một do call trong `vendorService`. Nếu mỗi client khởi tạo connection pool hoặc background threads, dự án có thể tốn gấp tài nguyên và không đóng object phụ.

Chuyển sang:

```java
@Configuration(proxyBeanMethods = false)
class VendorConfig {
    @Bean
    VendorClient vendorClient() {
        return VendorClient.create();
    }

    @Bean
    VendorService vendorService(VendorClient vendorClient) {
        return new VendorService(vendorClient);
    }
}
```

Khi đó Spring thực hiện dependency resolution trên managed `VendorClient`. Nếu SDK client cần `close()`, dùng `@Bean(destroyMethod="close")` khi đúng hợp đồng và xác định ownership để container đóng tài nguyên được quản lý. Không giả định mọi loại SDK client có cùng method/lifecycle.

**Phép test thiết thực:** tiêm `VendorClient` vào một test bean và kiểm tra reference bên trong `VendorService` chính là managed bean, hoặc dùng spy/counter/factory test double để assert chỉ một pool được tạo.

## 15. Những lỗi hiểu sai phổ biến

### Lỗi 1: “@Bean gọi @Bean khác thì luôn singleton”

Không. Full mode trả bean theo scope: singleton cùng instance, prototype có thể khác instance mỗi lookup, và scoped proxies hoạt động theo scope. Lite mode direct call không tuân theo semantics lookup container.

### Lỗi 2: “proxyBeanMethods=false khiến Spring bỏ qua @Bean”

Không. Các factory methods vẫn được xử lý để đăng ký/tạo bean. Chỉ **interception của Java calls** không còn.

### Lỗi 3: “Cứ đặt @Configuration thì phương thức static cũng được CGLIB chặn”

Không. Static methods không override được; lời gọi trực tiếp tới static factory vẫn chạy body bình thường.

### Lỗi 4: “@Component chứa @Bean giống full @Configuration”

Không. `@Bean` trên plain `@Component` ở lite mode. Nếu method A gọi method B, đó là Java direct call, không phải container lookup.

### Lỗi 5: “Nếu object direct call có cùng class thì cũng là Spring bean”

Không. Spring bean được xác định qua **bean definition và quá trình quản lý của container**. Một object có cùng class nhưng tạo bên ngoài có thể không nhận DI, post-processing, init/destroy callback hoặc proxy.

### Lỗi 6: “Đổi true thành false để tối ưu sẽ an toàn tuyệt đối”

Không. Trước khi đổi, phải tìm mọi direct calls giữa `@Bean` methods và từ bên ngoài vào config bean, rồi chuyển sang parameter injection hoặc lookup container phù hợp.

### Lỗi 7: “Nhận prototype mới nhờ direct call cũng giống lấy bean prototype”

Không. Mặc dù có instance mới, direct call có thể bỏ qua lifecycle/proxy/dependency resolution theo bean definition; chỉ container lookup mới thực sự tuân scope.

### Lỗi 8: “Gọi new FullConfig() thì CGLIB vẫn hoạt động”

Không. Enhancement nằm trên Spring-managed config bean; object tự `new` không được Spring thay bằng enhanced subclass.

### Lỗi 9: “Có full interception nên vòng phụ thuộc constructor được giải quyết”

Không. Circular dependency vẫn là vấn đề cần thiết kế, không biến mất nhờ CGLIB method interception.

### Lỗi 10: “Gọi @Bean method trong business code là cách DI phù hợp”

Thông thường không. Consumer nên nhận dependency qua constructor; factory method và configuration class thuộc layer wiring/composition, không phải service locator cho code nghiệp vụ.

### Lỗi 11: “Spring chắc chắn tạo bean theo thứ tự các methods trong source”

Không nên dựa vào thứ tự source làm hợp đồng. Dependency graph, `@Lazy`, bean scope, post-processor và bean definition ordering có thể tác động tới timing; use case phải đúng độc lập với thứ tự không được bảo đảm.

### Lỗi 12: “Method parameter injection chỉ dùng được nếu proxyBeanMethods=true”

Không. Parameter injection là dependency resolution bình thường của Spring, hoạt động tốt ở cả full và lite mode, và chính là cách nên dùng khi tắt interception.

## 16. Checklist kiểm thử trước khi chuyển sang lite mode

Trước khi đặt `@Configuration(proxyBeanMethods=false)` trong project thật:

1. **Xác định nơi cấu hình:** class thực sự được Spring đăng ký hay chỉ là Java object thủ công?
2. **Liệt kê các factory methods:** method nào mang `@Bean`, method nào trực tiếp gọi method khác?
3. **Nhận diện direct inter-bean calls:** `beanB()` trong `beanA()`, gọi từ method helper, gọi từ configuration class khác hoặc gọi qua managed config bean.
4. **Sửa dependency expression:** chuyển call thành factory method parameters, dùng qualifier khi cần.
5. **Kiểm tra scope:** singleton, prototype, request/session và lazy behavior có còn đúng không?
6. **Kiểm tra identity:** object trong consumer có cùng managed singleton được container trả về không?
7. **Kiểm tra lifecycle:** init/destroy callbacks, proxying, autowiring và resource cleanup có được áp dụng đúng instance không?
8. **Kiểm tra tài nguyên đắt tiền:** chỉ tạo một connection pool/HTTP client/mapper theo policy đặt ra.
9. **Kiểm tra static methods:** không trông chờ interception cho static `@Bean`.
10. **Kiểm tra code test:** tests dựng configuration bằng `new` không mô phỏng full Spring context.
11. **Chạy integration test:** dùng `AnnotationConfigApplicationContext` hoặc context thực tế tương ứng để assert bean graph.
12. **Quan sát warning:** nếu có bean creation/processor warning sau refactor, đừng bỏ qua vì “chỉ thay flag”.

## 17. Cách kiểm thử nhanh và điều tra một bean bị tạo thừa

### 17.1. Đếm lần tạo factory body

Trong test fixture, dùng `AtomicInteger` tăng tại lúc factory method thực sự trả object:

```java
private final AtomicInteger created = new AtomicInteger();

@Bean
Widget widget() {
    return new Widget(created.incrementAndGet());
}
```

Đây là snippet dựa trên kiểu `Widget` từ demo; muốn chạy độc lập cần class chứa bean definition và imports tương ứng. So sánh counter khi tạo context full và lite giúp quan sát việc direct call có chạy body hay không.

### 17.2. Kiểm tra identity

```java
Widget managed = context.getBean(Widget.class);
Pair pair = context.getBean(Pair.class);

System.out.println(pair.first() == managed);
System.out.println(pair.first() == pair.second());
```

Đây là snippet trong scope context của ví dụ. Khi singleton được resolve đúng, các reference có thể trùng nhau. Với lite mode và direct calls trong factory body, chúng có thể khác nhau.

### 17.3. Xem bean definition

```java
var factory = context.getBeanFactory();
var def = factory.getBeanDefinition("widget");

System.out.println(def.getFactoryMethodName());
System.out.println(factory.containsSingleton("widget"));
```

Snippet cần context `AnnotationConfigApplicationContext`. `getFactoryMethodName()` thông thường là `widget` trong ví dụ; `containsSingleton("widget")` chỉ nói cache singleton có instance hay chưa. Nó **không thể phát hiện những object phụ** do direct Java call tạo bên ngoài container. Kết hợp kiểm tra bean definition với factory-body counter và identity để chẩn đoán.

### 17.4. Cách test bằng mock/spy

Nếu thư viện trả một object giữ socket/thread mà test không tiện so identity trực tiếp, hãy bọc việc tạo object trong factory dễ spy, hoặc dùng một provider/test counter độc lập. Test các invariant:

- Số lần factory được gọi theo policy.
- Bean consumer nhận đúng managed dependency.
- Context shutdown đóng tài nguyên do Spring sở hữu và **không** đóng object thuộc ownership khác.
- Không có hai connection pools/clients bị tạo bất ngờ.

Không cần mock trực tiếp CGLIB interceptor nội bộ của Spring; test behavior quan sát được sẽ ổn định và hữu ích hơn.

## 18. Bài tập và đáp án

**Câu 1.** Trong full `@Configuration`, `@Bean A a()` gọi `b()` hai lần; `b` là singleton. Hai kết quả có luôn là hai object khác nhau không?

**Đáp:** Không. Với lời gọi đủ điều kiện được intercept qua enhanced configuration, cả hai lookup thường trả cùng managed singleton `b`.

**Câu 2.** Thay full config bằng `@Configuration(proxyBeanMethods=false)` mà giữ lời gọi `b()` trong `a()`, điều gì thay đổi?

**Đáp:** `b()` trở thành lời gọi Java bình thường, có thể thực thi factory body mỗi lần và tạo object ngoài bean `b` mà Spring đã/sẽ quản lý.

**Câu 3.** Làm sao viết lại `a()` để dùng singleton đúng trong lite mode?

**Đáp:** `@Bean A a(B b) { return new A(b); }`. Spring giải quyết dependency parameter theo bean definition/scope.

**Câu 4.** Nếu bean `b` là prototype ở full mode, hai lời gọi chéo qua enhanced configuration có phải cùng instance không?

**Đáp:** Không. Mỗi container lookup tới prototype thông thường tạo một instance mới theo scope, nên hai kết quả có thể khác nhau.

**Câu 5.** Vì sao static `@Bean` không được intercept, kể cả trong full configuration?

**Đáp:** CGLIB dùng subclass và override method; static Java methods không được override.

**Câu 6.** Nếu Spring đã đăng ký bean `clock` thì lời gọi `new MyConfig().clock()` có lấy singleton ấy không?

**Đáp:** Không. Object configuration tự tạo ngoài container không được enhanced; method call chỉ là Java thường.

**Câu 7.** Vì sao cùng một lời gọi `new VendorClient()` trong lite config có thể gây leak tài nguyên?

**Đáp:** Nếu nó được gọi từ một factory method khác, object phụ có thể không được đăng ký làm bean và không nhận destroy callback của managed `VendorClient`. Cần inject client từ container.

**Câu 8.** `@Bean` methods trong plain `@Component` có full inter-bean interception không?

**Đáp:** Không. Đó là lite mode, nên lời gọi chéo không được chuyển sang Spring-managed lookup.

**Câu 9.** Có nên dùng `@Primary` thay cho tham số `@Bean` khi muốn chuyển sang lite mode không?

**Đáp:** `@Primary` giải quyết việc lựa chọn ứng viên theo type; nó không làm một direct Java call được intercept. Vẫn cần parameter injection, có qualifier nếu cần chọn một bean cụ thể.

**Câu 10.** Vì sao test với `new FullConfig()` có thể cho kết quả khác với test tạo `AnnotationConfigApplicationContext(FullConfig.class)`?

**Đáp:** Chỉ context thực sự tạo/enhance configuration bean và can thiệp bean lookup. Object tự new không có CGLIB enhancement.

**Câu 11.** Một service singleton cần prototype mới mỗi request nghiệp vụ. Có nên gọi trực tiếp factory method `prototypeBean()` từ service?

**Đáp:** Không nên. Hãy inject `ObjectProvider<PrototypeType>` hoặc cơ chế lookup tương đương, rồi yêu cầu bean mới từ container đúng thời điểm.

**Câu 12.** Có nên đổi mọi class `@Configuration` thành `proxyBeanMethods=false` mà không cần test không?

**Đáp:** Không. Chỉ an toàn khi không còn phụ thuộc vào inter-bean interception và đã kiểm tra scope/identity/lifecycle/proxy behavior.

## 19. Tài liệu chính thức

- [Spring Framework 6.2 — Basic Concepts: @Bean and @Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/basic-concepts.html): full `@Configuration` và lite `@Bean`, cảnh báo về cross-method calls khi không proxy.
- [Spring Framework 6.2 — Using the @Configuration Annotation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/configuration-annotation.html): inter-bean dependencies, CGLIB subclassing, quy tắc scope và hạn chế final methods.
- [Spring Framework 6.2 — Using the @Bean Annotation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html): factory methods, return type, lifecycle và scope.
- [Spring Framework 6.2 API — Configuration](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/annotation/Configuration.html): `proxyBeanMethods` và full/lite semantics.
- [Spring Framework 6.2 API — Bean](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/annotation/Bean.html): inter-bean references, static `@Bean` methods, scope và vòng đời.
- [Spring Framework — Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html): singleton, prototype và các scope khác.

**Ghi nhớ cốt lõi:** Trong full `@Configuration(proxyBeanMethods=true)`, lời gọi **instance `@Bean` method** trên Spring-enhanced configuration được đưa qua BeanFactory để tuân theo bean scope/lifecycle. Trong lite mode (`proxyBeanMethods=false` hoặc `@Bean` trên plain `@Component`), cùng lời gọi là Java thông thường. **Tham số của phương thức `@Bean` là cách biểu diễn dependency rõ ràng, an toàn và ít phụ thuộc chế độ cấu hình nhất.**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-configuration-009">Giới hạn final, private và static khi chặn lời gọi @Bean</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn `final`, `private` và `static` khi Spring chặn lời gọi `@Bean`

## 1. Bài toán: vì sao có phương thức `@Bean` bị chặn, có phương thức không?

Trong Spring Java Configuration, annotation `@Bean` đánh dấu một factory method để Spring IoC Container đăng ký và quản lý object trả về. Tuy nhiên, không phải mọi **lời gọi Java trực tiếp** tới method được gắn `@Bean` đều có cùng ngữ nghĩa như một lệnh tra cứu bean từ container. Sự khác biệt nằm ở chế độ xử lý class cấu hình và ở khả năng **override method bằng CGLIB**.

Ví dụ:

```java
@Configuration
class AppConfig {
    @Bean
    Engine engine() {
        return new Engine();
    }

    @Bean
    Car car() {
        return new Car(engine());
    }
}
```

Đây là mã **minh họa**, chưa phải chương trình độc lập vì chưa định nghĩa `Engine`/`Car` và các import. Khi `AppConfig` được Spring đăng ký ở **full configuration mode** (mặc định `proxyBeanMethods = true`), lời gọi `engine()` từ `car()` trên instance được Spring tăng cường thường được chuyển về `BeanFactory`. Nếu `engine` là singleton, `Car` nhận reference của bean `engine` đã được container quản lý.

Nhưng có ba nhóm ràng buộc quan trọng:

- **`final`**: Java không cho subclass kế thừa class `final` hoặc override method `final`.
- **`private`**: method `private` không được override bằng method của subclass.
- **`static`**: method thuộc class, không tham gia virtual dispatch/override trên instance; vì vậy lời gọi trực tiếp `static @Bean` method **không được interceptor CGLIB chuyển sang BeanFactory**.

Điều này **không đồng nghĩa** `@Bean` với `static` luôn sai: static `@Bean` là một trường hợp được Spring hỗ trợ, đặc biệt hữu ích để tạo sớm một số post-processor. Còn `final` và `private` trên instance `@Bean` method là ràng buộc đáng chú ý ở **full `@Configuration`**, không nên bê nguyên các quy tắc đó sang **lite mode**.

Mục tiêu bài học:

1. Hiểu rõ full vs. lite configuration và lý do có CGLIB.
2. Phân biệt hạn chế ở **class** và ở **instance factory method**.
3. Giải thích được vì sao static `@Bean` hợp lệ nhưng không có inter-bean call interception.
4. Sử dụng `proxyBeanMethods = false` và factory-method parameter injection đúng cách.
5. Hiểu khi nào cần static `@Bean` cho `BeanFactoryPostProcessor`/`BeanPostProcessor`.
6. Chạy ví dụ Java 17+ đối chiếu identity/số instance để nhận diện lỗi cấu hình.

## 2. Full và lite khác nhau ở khả năng override

Spring đăng ký và quản lý beans từ `@Bean` ở cả hai mode. Full mode (`@Configuration` mặc định) dùng subclass CGLIB để chặn instance factory methods phù hợp trên configuration bean đã được enhance; lite mode (`proxyBeanMethods=false`) để direct method call chạy theo nghĩa Java.

### 2.1. Full mode cần instance method override được

```java
@Configuration
class FullConfig {
    @Bean Engine engine() { return new Engine(); }
    @Bean Car car() { return new Car(engine()); }
}
```

Đoạn minh họa cần kiểu `Engine` và `Car`. Khi Spring gọi `car()` qua enhanced configuration, `engine()` có thể được chuyển sang BeanFactory để lấy singleton. Để làm vậy CGLIB phải subclass configuration và override method.

### 2.2. Lite mode cho phép Java direct call

Nếu đổi annotation thành `@Configuration(proxyBeanMethods=false)`, `engine()` trong `car()` chạy trực tiếp và có thể tạo object khác managed `engine`. Chuyển sang `car(Engine engine)` để Spring resolve dependency từ BeanFactory.

### 2.3. Bảng giới hạn

| Thành phần | Full | Lite |
| --- | --- | --- |
| Configuration class `final` | Không hợp lệ vì cần subclass | Có thể dùng |
| Instance `@Bean` method `private`/`final` | Không hợp lệ vì cần override | Có thể dùng nếu Spring có thể gọi method |
| Static `@Bean` | Hợp lệ, không có interception | Hợp lệ, không có interception |
| Tham số của `@Bean` | Spring resolve | Spring resolve |

`proxyBeanMethods=false` không tắt DI, lifecycle hay singleton management của các bean được container thực sự tạo. Các mục sau giải thích **vì sao từng modifier Java làm thay đổi khả năng interception**.

## 3. `final`: cấm kế thừa và cấm override

Java `final` áp dụng cho class hoặc instance method:

- `final class A`: không class nào kế thừa `A`.
- `final void action()`: subclass không thể override `action()`.
- `final` trên field/biến cục bộ có nghĩa khác và **không phải** ràng buộc trực tiếp của CGLIB method interception.

### 3.1. Class `@Configuration` không được `final` trong full mode

Ví dụ **cố ý sai** với full mode:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public final class InvalidFinalConfig {
    @Bean
    String serviceName() {
        return "billing";
    }
}
```

Đây là nội dung minh họa cho file `InvalidFinalConfig.java`, có thể biên dịch về mặt Java nhưng **không phù hợp cho full Spring configuration processing**. Khi Spring cần tạo subclass của `InvalidFinalConfig`, modifier `final` ngăn kế thừa; context có thể thất bại trong giai đoạn xác minh/xử lý configuration class, trước khi caller lấy được bean.

Không giải quyết vấn đề bằng cách tự viết subclass hoặc đưa annotation lên một class khác rồi trông đợi class final vẫn được enhance. Có hai hướng hợp lệ:

1. Bỏ `final` khỏi configuration class nếu thực sự cần inter-bean calls theo full mode.
2. Chuyển sang `@Configuration(proxyBeanMethods = false)` **nếu các factory methods độc lập** hoặc dependency được nhận qua tham số.

Ví dụ lite hợp lệ về nguyên tắc:

```java
@Configuration(proxyBeanMethods = false)
public final class FinalLiteConfig {
    @Bean
    String serviceName() {
        return "billing";
    }
}
```

Đây là mã minh họa cho file `FinalLiteConfig.java`, phải có import `@Configuration` và `@Bean` khi tách ra file riêng. Bean `serviceName` vẫn do Spring quản lý; modifier `final` chỉ có ý nghĩa là class không được subclass.

### 3.2. Instance method `@Bean` không được `final` trong full mode

Ví dụ **cố ý sai**:

```java
@Configuration
class InvalidFinalMethodConfig {
    @Bean
    public final Engine engine() {
        return new Engine();
    }
}
```

Class `InvalidFinalMethodConfig` không `final` nhưng method `engine()` không thể override. Vì full mode muốn chặn lời gọi method đó, Spring coi đây là cấu hình không hợp lệ. Lỗi thường được báo trong quá trình xử lý bean definitions, ví dụ “`@Bean method ... must not be private or final`” hoặc thông điệp tương đương theo phiên bản.

**Không dựa vào Java compiler để phát hiện lỗi này.** Cú pháp Java cho phép một method vừa có annotation vừa `final`; điều vi phạm là hợp đồng **Spring full configuration processing**. Hãy test tạo `AnnotationConfigApplicationContext` thay vì chỉ chạy `javac`.

### 3.3. `final` trên class nghiệp vụ được trả về từ `@Bean` thì sao?

Ví dụ:

```java
public final class PricingRules {
    public int fee() {
        return 5;
    }
}

@Configuration
class ValidConfig {
    @Bean
    PricingRules pricingRules() {
        return new PricingRules();
    }
}
```

Đây là hai **trích đoạn minh họa**, tương ứng file Java riêng. Config `ValidConfig` không `final` và factory method có thể override. **Class `PricingRules` được tạo làm bean có thể `final`** mà không cản **CGLIB enhancement của configuration class**: Spring đang tăng cường class `ValidConfig`, không nhất thiết phải subclass `PricingRules`.

Cần phân biệt với một nhu cầu **Spring AOP class-based proxy** trên chính `PricingRules`: target class `final` có thể gây hạn chế proxy AOP vì không subclass được. Hai chủ đề liên quan cơ chế proxy nhưng **không phải cùng một đối tượng cần được subclass**.

### 3.4. Java records và Kotlin

Java record là class có tính `final`, nên **không dùng record làm full `@Configuration` class cần CGLIB subclass**. Với `@Configuration(proxyBeanMethods=false)`, các hạn chế subclassing này không còn là lý do từ bean method interception; vẫn cần bảo đảm framework có thể xây dựng configuration object đúng cách.

Trong Kotlin, classes và methods mặc định final; plugin Kotlin Spring/`all-open` thường được sử dụng để mở những classes/annotations cần proxy. Nhưng đừng áp dụng mẹo Kotlin để che một dependency graph sai; lựa chọn lite mode và method-parameter injection vẫn hữu ích.

## 4. `private`: method tồn tại nhưng subclass không override được

Một method `private` chỉ truy cập được trong phạm vi khai báo theo quy tắc Java, không được kế thừa như một virtual method có thể override. Do đó:

```java
@Configuration
class InvalidPrivateConfig {
    @Bean
    private Engine engine() {
        return new Engine();
    }
}
```

Đây là ví dụ **cố ý sai** trong full mode. Vì `engine()` là private instance method, CGLIB subclass không thể override để chặn những lời gọi theo ngữ nghĩa container. Spring full configuration validation không chấp nhận factory method dạng này.

Không nên “chữa” bằng cách chỉ thêm `@Autowired` trên method, bật bean definition overriding hoặc tăng phạm vi component scan: vấn đề nằm ở **khả năng override của method**, không phải việc container tìm thấy dependency.

### 4.1. `private` trong lite mode

Khi `proxyBeanMethods=false`, Spring không cần subclass để chặn các `@Bean` method calls. Vì vậy Spring cho phép factory methods lite sử dụng các access modifiers linh hoạt hơn, bao gồm `private` trong điều kiện phương thức có thể được gọi bằng reflection tương ứng.

```java
@Configuration(proxyBeanMethods = false)
class LitePrivateConfig {

    @Bean
    private Engine engine() {
        return new Engine();
    }

    @Bean
    Car car(Engine engine) {
        return new Car(engine);
    }
}
```

Đây là **mã minh họa**, cần các class `Engine`/`Car`. Spring có thể gọi `engine()` như một factory method để đăng ký bean, còn `car(Engine engine)` nhận chính bean đó nhờ **dependency resolution tại tham số**. Không cần gọi `engine()` từ `car()` nên không cần CGLIB inter-bean interception.

**Cẩn trọng:** nếu module Java chạy theo JPMS với `module-info.java`, việc Spring truy cập reflective private factory method có thể bị giới hạn bởi module openness; cần cấu hình `opens` đúng chỗ. Ví dụ đơn giản dùng classpath thông thường nên không áp dụng module descriptor.

### 4.2. `private @Bean` không đồng nghĩa “bean riêng tư”

`private` là modifier của method Java, **không phải bean visibility**. Một bean được Spring đăng ký từ private factory method ở lite mode vẫn có thể được lookup từ cùng `ApplicationContext` nếu bean definition hợp lệ và scope cho phép:

```java
Engine engine = context.getBean(Engine.class);
```

Đây là đoạn minh họa với biến `context` đã khởi tạo. Method private không khiến bean bị giấu khỏi container hay biến thành private dependency theo Java access control.

### 4.3. Package-private và protected trong full mode

Một instance `@Bean` method không bắt buộc phải là `public` để được chặn trong full mode. Các method package-private hoặc `protected` **có thể được override theo luật Java** nếu chúng không `final` và không bị những ràng buộc visibility khác của JVM/JPMS chặn. Nhiều ví dụ Spring JavaConfig sử dụng method package-private:

```java
@Configuration
class ValidPackagePrivateConfig {
    @Bean
    Engine engine() {
        return new Engine();
    }
}
```

Ở đây method `engine()` không khai báo `public` nhưng cũng không `private` hoặc `final`; điều này khác với hai ví dụ lỗi ở trên.

Để giảm rủi ro reflection/visibility trong môi trường phức tạp, các phương thức factory hạ tầng có thể được để package-private hoặc public có chủ đích. Không thay mọi method bằng public chỉ vì Spring CGLIB nếu không cần.

## 5. `static`: hợp lệ để tạo bean, nhưng không có interception

### 5.1. Vì sao CGLIB không chặn static method?

Java phân giải method `static` theo class, không theo virtual dispatch của instance. CGLIB chặn inter-bean calls của full `@Configuration` bằng **subclassing và method overriding**; một static method không thể override như instance method.

Ví dụ:

```java
@Configuration
class StaticBeanConfig {

    @Bean
    static Engine engine() {
        return new Engine();
    }

    @Bean
    Car car() {
        return new Car(engine()); // Trực tiếp gọi static method Java.
    }
}
```

Đây là mã minh họa. Spring đăng ký **bean `engine`** từ static factory method. Tuy nhiên, lời gọi `engine()` từ `car()` là **static Java call** trực tiếp: thực thi `new Engine()` một lần nữa, có thể tạo một object **ngoài** managed singleton `engine`.

Vì vậy:

- `context.getBean(Engine.class)` trả bean `engine` được container quản lý.
- `context.getBean(Car.class).engine()` có thể trỏ tới một object `Engine` **khác** tạo từ lời gọi static trong `car()`.
- Điều đó xảy ra ngay cả khi `StaticBeanConfig` dùng **full `@Configuration`**.
- Chỉ vì `@Bean` được viết trên static method **không có nghĩa** factory method không được Spring quản lý; sự khác nhau nằm giữa **BeanFactory invocation** và **lời gọi Java trực tiếp**.

### 5.2. Static `@Bean` không có ý nghĩa là singleton “toàn JVM”

Từ khóa Java `static` trong `static @Bean` chỉ định method thuộc class, **không phải scope của bean**. Scope mặc định `singleton` của Spring vẫn là **một instance trên mỗi BeanFactory/container**:

- Hai `ApplicationContext` độc lập có thể có hai singleton instances tương ứng.
- `@Scope("prototype")` trên static `@Bean` vẫn có thể yêu cầu tạo object mới theo scope khi container resolve.
- Một lời gọi Java trực tiếp đến static method luôn chạy method body theo semantics Java; không được container tự đổi thành lookup.

Đừng dùng biến static để giữ “singleton toàn hệ thống” rồi lẫn với Spring singleton. Trong đa ứng dụng/context hoặc test chạy song song, static state có thể làm dữ liệu rò giữa tests và gây lỗi khó tái hiện.

### 5.3. Cách sửa inter-bean reference tới static `@Bean`

Thay:

```java
@Bean
Car car() {
    return new Car(engine()); // Direct static method call.
}
```

bằng:

```java
@Bean
Car car(Engine engine) {
    return new Car(engine);   // BeanFactory cung cấp Engine.
}
```

Hai đoạn trên là phương án **minh họa**, dùng cùng class `Car`/`Engine` của ví dụ. Spring phân giải tham số `Engine` từ container trước khi gọi `car(...)`; cách này bảo toàn scope/managed reference phù hợp và không phụ thuộc method static có được chặn hay không.

Đây là quy tắc thực hành đáng nhớ: **truyền dependency qua tham số `@Bean`** có tính ổn định cao hơn trong full mode, lite mode và khi dependency được sản sinh bởi một static factory method.

## 6. Vì sao một số post-processor dùng `static @Bean`?

### 6.1. `BeanFactoryPostProcessor` tham gia rất sớm

`BeanFactoryPostProcessor` (BFPP) xử lý metadata trước khi phần lớn singleton được tạo. Nếu BFPP do một **instance `@Bean` method** tạo, BeanFactory phải khởi tạo configuration object sớm để gọi method, có thể xảy ra trước khi injection/lifecycle của chính config object hoàn tất. Static method tránh yêu cầu ấy:

```java
@Configuration
class InfraConfig {
    @Bean
    static PropertySourcesPlaceholderConfigurer placeholders() {
        return new PropertySourcesPlaceholderConfigurer();
    }
}
```

Đây là cú pháp minh họa, cần import `org.springframework.context.support.PropertySourcesPlaceholderConfigurer`. Spring khuyến nghị static factory cho BFPP khi phù hợp vì thời điểm khởi tạo đặc biệt.

### 6.2. `BeanPostProcessor` cũng cần sẵn sàng sớm

`BeanPostProcessor` (BPP) xử lý bean **instances** trước/sau khởi tạo, có thể wrap proxy hoặc áp dụng lifecycle callbacks. Non-static factory tạo BPP có nguy cơ khởi tạo config hoặc dependencies quá sớm; static factory có thể tránh một phần nguy cơ:

```java
@Configuration(proxyBeanMethods = false)
class InfrastructureConfiguration {
    @Bean
    static BeanPostProcessor auditPostProcessor() {
        return new BeanPostProcessor() {};
    }
}
```

Ví dụ cần import `org.springframework.beans.factory.config.BeanPostProcessor`; processor rỗng chỉ minh họa declaration, **không** ghi audit. Một BPP thực phải triển khai callback và kiểm soát thứ tự; bài về static `@Bean` và early type discovery trình bày sâu quá trình này.

### 6.3. Đánh đổi và giới hạn

Static factory **không** được CGLIB chặn khi gọi trực tiếp, không truy cập instance state của config và không biến bean thành singleton toàn JVM. Phương thức vẫn có thể nhận dependencies được Spring resolve; chính các dependencies đó có thể kéo theo khởi tạo sớm ngoài dự kiến. Vì vậy không nên đặt `static` hàng loạt, đặc biệt với bean nghiệp vụ thường.

## 7. Ví dụ chạy được Java 17+: full, lite và static

Ví dụ tạo **bốn `ApplicationContext` độc lập**, mỗi context so sánh một `Part` bean do container quản lý với `Part` mà một `Product` nhận:

1. **FULL**: instance method có thể override, inter-bean call được CGLIB chặn.
2. **LITE_DIRECT**: configuration class `final`, factory method `private`/`final`, direct call chạy như Java thường.
3. **LITE_PARAMETER**: configuration class `final` nhưng factory parameter nhận managed dependency; không có object dư.
4. **STATIC_DIRECT**: static `@Bean` hợp lệ trong full configuration nhưng direct static call không bị CGLIB chặn.

Các class được đăng ký lần lượt trong **những context khác nhau** nên không có xung đột bean names. Ví dụ dùng JDK 17+ và Spring Framework 6.2.19.

### 7.1. Cấu trúc

```text
bean-method-modifiers-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        └── java/
            └── BeanMethodModifiersDemo.java
```

File `settings.gradle`:

```groovy
rootProject.name = 'bean-method-modifiers-demo'
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
    implementation 'org.springframework:spring-context:6.2.19'
}

application {
    mainClass = 'BeanMethodModifiersDemo'
}
```

Không khai báo CGLIB riêng: Spring Core bao gồm phiên bản CGLIB được repackaged. Dùng một bản Gradle tương thích với JDK 17+.

### 7.2. File Java đầy đủ

Tạo `src/main/java/BeanMethodModifiersDemo.java`:

```java
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class BeanMethodModifiersDemo {

    private static final AtomicInteger CREATED = new AtomicInteger();

    public record Part(int id) {
        public Part() {
            this(CREATED.incrementAndGet());
        }
    }

    public record Product(Part part) {
    }

    // Full mode: các instance @Bean methods đều override được.
    @Configuration
    public static class FullConfig {

        @Bean
        public Part part() {
            return new Part();
        }

        @Bean
        public Product product() {
            return new Product(part());
        }
    }

    // Lite: final configuration class và private/final factory methods.
    @Configuration(proxyBeanMethods = false)
    public static final class LiteDirectConfig {

        @Bean
        private Part part() {
            return new Part();
        }

        @Bean
        public final Product product() {
            return new Product(part());
        }
    }

    // Lite: các modifier giữ nguyên, nhưng dùng method parameter DI.
    @Configuration(proxyBeanMethods = false)
    public static final class LiteParameterConfig {

        @Bean
        private Part part() {
            return new Part();
        }

        @Bean
        public final Product product(Part part) {
            return new Product(part);
        }
    }

    // Full configuration nhưng static @Bean không được override.
    @Configuration
    public static class StaticDirectConfig {

        @Bean
        public static Part part() {
            return new Part();
        }

        @Bean
        public Product product() {
            return new Product(part());
        }
    }

    private static void inspect(
            String label, Class<?> configurationClass) {

        CREATED.set(0);

        try (var context =
                     new AnnotationConfigApplicationContext(configurationClass)) {

            Part managed = context.getBean(Part.class);
            Product product = context.getBean(Product.class);

            System.out.println("== " + label + " ==");
            System.out.println("samePart = " + (managed == product.part()));
            System.out.println("instances = " + CREATED.get());
        }
    }

    public static void main(String[] args) {
        inspect("FULL", FullConfig.class);
        inspect("LITE_DIRECT", LiteDirectConfig.class);
        inspect("LITE_PARAMETER", LiteParameterConfig.class);
        inspect("STATIC_DIRECT", StaticDirectConfig.class);
    }
}
```

Các class `FullConfig` và `StaticDirectConfig` không `final`; các instance `@Bean` methods có thể override. Hai class lite được đánh dấu `final` và cố ý có private/final methods để minh họa các ràng buộc về CGLIB **không còn áp dụng cho interception** khi `proxyBeanMethods=false`.

`AtomicInteger` chỉ làm bộ đếm số lần constructor `new Part()` được gọi. `CREATED.set(0)` chạy trước khi tạo mỗi context, và trong mỗi context main tạo/đọc beans trên một luồng. Điều này giúp kết quả đếm ổn định; không dùng bộ đếm toàn cục kiểu này làm state nghiệp vụ.

### 7.3. Chạy và stdout

Trong thư mục dự án:

```bash
gradle run --quiet
```

Nếu dùng Gradle Wrapper trên Linux/macOS:

```bash
./gradlew run --quiet
```

Trên Windows PowerShell:

```powershell
.\gradlew.bat run --quiet
```

Các dòng `System.out.println` mà chương trình in:

```text
== FULL ==
samePart = true
instances = 1
== LITE_DIRECT ==
samePart = false
instances = 2
== LITE_PARAMETER ==
samePart = true
instances = 1
== STATIC_DIRECT ==
samePart = false
instances = 2
```

Output này nói về chương trình đơn giản ở trên, với singleton mặc định và không có component/factory nào khác tạo `Part`. Gradle hoặc hệ thống logging có thể phát sinh thêm dòng không thuộc `System.out.println` của chương trình.

### 7.4. Giải thích từng trường hợp

**FULL:** Spring enhancement có thể chặn `product() -> part()`. Vì `part` là singleton managed bean, `product.part()` và `context.getBean(Part.class)` là cùng object. Chỉ một lần tạo `Part`.

**LITE_DIRECT:** `LiteDirectConfig` là class `final`, method `part()` là `private`, method `product()` là `final`. Chúng được BeanFactory gọi như factory methods trong lite mode; khi `product()` gọi `part()` thì đó là Java call thông thường. Context cũng tạo managed singleton `part` riêng. Tổng hai object, identity `false`.

**LITE_PARAMETER:** Không cần intercept `part()`; BeanFactory phân giải tham số `Part part` cho `product(Part part)`. Factory nhận managed singleton `part` và giữ reference đó, tổng một object dù config class vẫn `final` và factory methods private/final.

**STATIC_DIRECT:** `StaticDirectConfig.part()` được đăng ký như một static factory method; container tạo một managed singleton. Nhưng trong `product()`, lời gọi `part()` là static Java call không được CGLIB override, tạo một object khác. Tổng hai object, identity `false`. Việc configuration class không `final` cũng không thay đổi hạn chế static dispatch.

### 7.5. Tại sao ví dụ không đưa những cấu hình sai vào `main`?

Các ví dụ ở phần 8 cố ý vi phạm full configuration constraints. Nếu đăng ký chúng cùng với các cấu hình hợp lệ, context có thể thất bại ngay ở giai đoạn parsing/enhancement, không tạo được stdout dùng để so sánh. Tách trường hợp lỗi ra thành minh họa riêng giúp người học thấy **lỗi khởi động** khác **lỗi identity do direct calls**.

## 8. Các ví dụ cấu hình cố ý sai trong full mode

Không đặt các snippets ở mục này vào chương trình hoàn chỉnh mục 7. Chúng là các **đầu vào có chủ đích để kiểm tra thông báo lỗi Spring**, không phải cấu hình nên triển khai.

### 8.1. `final @Configuration` class

```java
@Configuration
final class BadFinalConfiguration {
    @Bean
    Part part() {
        return new Part();
    }
}
```

Khi xử lý full mode, Spring cần tạo CGLIB subclass nhưng class `BadFinalConfiguration` không thể được kế thừa. Hướng khắc phục: bỏ `final` hoặc chọn lite mode với DI qua tham số.

### 8.2. `final` instance `@Bean` method

```java
@Configuration
class BadFinalMethodConfiguration {
    @Bean
    final Part part() {
        return new Part();
    }
}
```

Method `part()` là `final` nên không thể được subclass override. Dù chỉ có một bean definition, đây vẫn là vấn đề tại configuration validation của full mode; không phụ thuộc vào việc hiện tại đã có inter-bean call hay chưa.

### 8.3. `private` instance `@Bean` method

```java
@Configuration
class BadPrivateMethodConfiguration {
    @Bean
    private Part part() {
        return new Part();
    }
}
```

Spring full mode không thể override một private instance method để cung cấp bean lookup semantics. Nếu chuyển sang lite, có thể dùng factory method private, nhưng hãy chú ý reflective access/JPMS và tránh direct calls khi muốn reference managed bean.

### 8.4. Có phải static `@Bean` tự báo lỗi như final/private?

**Không.** Spring hỗ trợ static factory methods trong configuration classes, kể cả full configuration. Điều cần hiểu là **static calls không được intercept**. Do đó lỗi thường biểu hiện ở **số instance/identity/lifecycle không như kỳ vọng**, chứ không phải vì cú pháp `static @Bean` bị Spring cấm.

| Khai báo | Full `@Configuration` | Lite mode |
| --- | --- | --- |
| `final class Config` | Không hợp lệ vì cần subclass | Có thể |
| `@Bean final T bean()` (instance) | Không hợp lệ vì không override được | Có thể |
| `@Bean private T bean()` (instance) | Không hợp lệ vì không override được | Có thể |
| `@Bean static T bean()` | Hợp lệ, nhưng direct call không intercept | Hợp lệ, direct call Java thường |
| `@Bean T bean()` (package-private, overridable) | Hợp lệ khi visibility/khả năng override phù hợp | Hợp lệ |
| `@Bean T bean(Dependency dependency)` | Spring resolve tham số | Spring resolve tham số |

## 9. Những nhầm lẫn cần tránh

### 9.1. “Nếu `@Bean` method là private thì bean cũng private”

Sai. Modifier `private` điều khiển khả năng truy cập Java method; nó không tạo một “bean private” trong Spring. Nếu method được Spring chấp nhận (ví dụ lite configuration trên classpath phù hợp), bean có tên và scope như các beans khác và có thể được lookup từ container.

### 9.2. “Static `@Bean` = Spring singleton toàn ứng dụng”

Sai. Java `static` và Spring `singleton` thuộc hai khái niệm khác nhau. Singleton của Spring thường gắn với **một container**, trong khi static method là method của Java class. Một Spring singleton bean được tạo từ static factory method vẫn có thể khác giữa hai contexts.

### 9.3. “Chuyển sang lite mode thì Spring không làm DI”

Sai. Lite mode vẫn đăng ký `@Bean` và phân giải các tham số của factory methods. Dùng `@Bean Product product(Part part)` để nhận dependency từ BeanFactory thay vì tự gọi `part()`.

### 9.4. “Chỉ cần `new Config()` là nhận được interception”

Sai. Một object do `new Config()` bên ngoài Spring là Java object bình thường; không phải configuration instance do Spring tăng cường. Nếu tự gọi `new Config().product()`, bạn không được bảo đảm Spring-managed singleton semantics. Tương tự, static direct calls không thể được intercepted chỉ vì được viết trong một class mang `@Configuration`.

### 9.5. “Mọi method `final` của configuration class đều bị cấm”

Không chính xác. Quy tắc full mode tập trung vào **instance `@Bean` factory methods cần override**; một helper Java method khác không có `@Bean` không phải đối tượng chặn inter-bean references theo cùng quy tắc. Dù vậy, thiết kế full configuration class nên tránh những thủ thuật inheritance/phân quyền truy cập gây khó hiểu.

### 9.6. “CGLIB enhancement của `@Configuration` chính là Spring AOP”

Không hoàn toàn. Hai cơ chế đều có thể dùng CGLIB subclassing, nhưng mục tiêu khác:

- Full configuration enhancement can thiệp vào **lời gọi `@Bean` methods** để giữ BeanFactory semantics.
- Spring AOP can thiệp vào **lời gọi tới business bean** qua proxy cho các concern như transaction, caching, security khi được cấu hình.

Trong proxy-based AOP, **self-invocation** trên target thường bypass proxy. Không suy diễn quy tắc AOP self-invocation từ ví dụ full `@Configuration`.

### 9.7. “Static factory method không nhận được dependency”

Không đúng. Spring vẫn có thể resolve các tham số của static `@Bean` method theo khả năng BeanFactory, ví dụ `@Bean static Service service(Repository repository)` khi Repository bean phù hợp tồn tại. Dù vậy, với BFPP/BPP cần khởi tạo sớm, dependencies quá phức tạp có thể làm nhiều beans khác bị tạo sớm, gây ảnh hưởng post-processing.

### 9.8. “Bỏ `final` là luôn hết lỗi”

Không nhất thiết. Một class không final vẫn có thể gặp lỗi nếu các `@Bean` instance methods private/final, bean candidates thiếu, vòng phụ thuộc không giải quyết được, module reflection bị giới hạn, hoặc factory method tự ném exception. Hãy phân biệt lỗi enhancement/validation với lỗi autowiring/bean creation.

## 10. Phân biệt lỗi validation, lỗi DI và lỗi lifecycle

| Hiện tượng | Có thể do | Hướng điều tra |
| --- | --- | --- |
| Context fail sớm khi xử lý config full | Config class final hoặc instance `@Bean` method private/final | Kiểm tra `proxyBeanMethods`, access modifiers, nested class |
| Context refresh thành công nhưng số object tăng | Lite direct calls, static direct calls, hoặc bean scope prototype | Dò caller `otherBean()`, so với managed `getBean(...)` |
| `BeanCreationException` ở bean khác | Factory method logic lỗi, dependency thiếu hoặc vòng phụ thuộc | Đọc root cause, xem chữ ký `@Bean` và DI graph |
| `UnsatisfiedDependencyException` | Không có hoặc không chọn được dependency | Kiểm tra bean definitions, types, qualifiers, profiles |
| Một object không được AOP/lifecycle xử lý | Nó được tạo bằng direct Java call ngoài BeanFactory | Chuyển sang container lookup/parameter injection |
| Không thấy `@Value`/`@Autowired` trên config | Một BFPP/BPP non-static ép config khởi tạo quá sớm | Cân nhắc static factory cho post-processor |
| JPMS `InaccessibleObjectException` | Module chưa mở package cần reflection/enhancement | Kiểm tra module descriptor và `opens` |
| Test với `new Config()` không khớp ứng dụng | Không có Spring-enhanced managed configuration instance | Dùng `AnnotationConfigApplicationContext` trong integration test |

### 10.1. Cách kiểm tra nhanh bằng context thật

Khi có class `FullConfig` và `Product`/`Part` từ mục 7:

```java
try (var context =
        new AnnotationConfigApplicationContext(FullConfig.class)) {
    Part managed = context.getBean(Part.class);
    Product product = context.getBean(Product.class);
    System.out.println(managed == product.part());
}
```

Đây là đoạn **minh họa** sử dụng các class của chương trình đầy đủ; nó không tự chạy như một file `.java` riêng. Với full mode và singleton, kết quả là `true`. Thử đổi configuration sang lite direct rồi lặp phép kiểm tra để nhìn thấy `false`.

**Lưu ý:** quan sát tên class kiểu `$$SpringCGLIB$$...` chỉ là kiểm tra chi tiết triển khai. Không viết logic nghiệp vụ dựa vào các phần tên class sinh động; test behavior theo bean identity, scope và semantics DI.

## 11. Bài tập có đáp án

### Bài 1 — Configuration class final

Cho `@Configuration final class FinalConfig` với một instance `@Bean` method thường. Nó có phù hợp full mode không?

**Đáp án:** Không, vì full mode cần tạo CGLIB subclass. Có thể bỏ `final` hoặc dùng `@Configuration(proxyBeanMethods=false)` với factory methods độc lập/DI đúng cách.

### Bài 2 — Method private

Một method `@Bean private Part part()` có thể được subclass override trong full mode không?

**Đáp án:** Không; private instance methods không overridable. Trong lite mode, factory method có thể private và được container gọi qua reflection khi môi trường cho phép.

### Bài 3 — Method final

Lớp `@Configuration` không final nhưng method `@Bean final Product product()`. Spring full mode có hợp lệ không?

**Đáp án:** Không; restriction áp dụng ở cả factory method. Không sửa bằng cách chỉ bỏ final ở class nếu method vẫn final.

### Bài 4 — Static factory

`@Configuration` có `@Bean static Part part()`, một `@Bean Product product()` gọi `part()` trực tiếp. Có phải lời gọi đó được interceptor CGLIB trả managed singleton không?

**Đáp án:** Không. Static call là lời gọi Java bình thường. Muốn managed Part, khai báo `@Bean Product product(Part part)`.

### Bài 5 — Static và vòng đời sớm

Vì sao `@Bean static PropertySourcesPlaceholderConfigurer placeholders()` thường được khuyến nghị?

**Đáp án:** Vì factory tạo BFPP có thể được gọi sớm **mà không phải instantiate enclosing configuration class**, giảm nguy cơ những injection/lifecycle annotations trên config chưa được xử lý đầy đủ.

### Bài 6 — Hai context

Hai `ApplicationContext` riêng đều đăng ký singleton bean từ `@Bean static Part part()`. Có bắt buộc trả đúng một instance Part chia sẻ toàn JVM không?

**Đáp án:** Không. Singleton Spring là theo container; static chỉ là thuộc tính của method. Nếu code tự thêm static cache riêng thì đó là logic khác, không phải mặc định của Spring.

### Bài 7 — Lite class final nhưng injection đúng

`@Configuration(proxyBeanMethods=false) final class Config` có `@Bean private Part part()` và `@Bean final Product product(Part part)`. Cần CGLIB enhancement để chia sẻ Part singleton không?

**Đáp án:** Không. BeanFactory resolve tham số từ container. Hành vi được kiểm chứng trong trường hợp `LITE_PARAMETER` ở mục 7.

### Bài 8 — Scope prototype

Nếu bean `Part` có scope prototype, có chắc `FULL` sẽ chỉ tạo một instance không?

**Đáp án:** Không. Full inter-bean call bảo toàn **scope semantics** của container, và prototype không chia sẻ một instance duy nhất như singleton. Ví dụ đếm ở mục 7 dành cho singleton mặc định.

### Bài 9 — Đổi proxy mode để chữa lỗi thiếu bean

Một factory method cần `Repository` nhưng context không có bean kiểu đó. Bật `proxyBeanMethods=true` có tự tạo Repository không?

**Đáp án:** Không. Đây là lỗi dependency resolution, cần đăng ký bean hoặc sửa qualifier/configuration; enhancement không tạo một dependency chưa được định nghĩa.

### Bài 10 — Full configuration và `@Transactional`

Nếu `@Bean` inter-bean call được CGLIB chặn thì `this.transfer()` trên một Spring AOP service có tự động bật transaction không?

**Đáp án:** Không thể suy ra như vậy. CGLIB configuration enhancement và AOP proxying là hai cơ chế khác nhau; self-invocation của AOP target thường bypass proxy.

## 12. Checklist trước khi chọn modifier và proxy mode

- Class có phải một **full `@Configuration`** không? `proxyBeanMethods` có đang là `true` mặc định không?
- Full config class có `final` không? Nested config có được khai báo `static` khi cần không?
- Các **instance `@Bean` factory methods** có `private` hay `final` không?
- Có static `@Bean` method nào được **gọi trực tiếp** ở một `@Bean` method khác không?
- Nếu cần managed dependency, đã dùng **factory-method parameters** thay vì direct calls chưa?
- Có BFPP/BPP cần khởi tạo sớm không, và method tạo nó có nên `static` không?
- Bean scope có phải singleton không? Có prototype, request, session, lazy hoặc AOP proxy cần xét không?
- Có object nào được tự `new` ngoài BeanFactory nhưng lại được giả định là managed bean không?
- Đã tạo một Spring context thật để kiểm tra registration/identity/lifecycle chưa?
- Nếu dùng Java modules, package có được mở phù hợp cho reflection/proxy không?

## 13. Tóm tắt

**Giới hạn của full `@Configuration` đến từ cách chặn inter-bean method calls bằng CGLIB subclass**: class `final` không thể subclass, instance method `final` hoặc `private` không thể override. Vì thế những khai báo này không phù hợp ở full mode. `@Configuration(proxyBeanMethods=false)` bỏ nhu cầu interception đó, nên có thể dùng class/method với các modifier ấy trong lite mode, miễn method là một factory hợp lệ và container có khả năng gọi nó.

**`static @Bean` hợp lệ trong Spring**, nhưng lời gọi trực tiếp đến static method không được interceptor chặn ở bất kỳ chế độ nào. Nó đặc biệt hữu ích để tạo sớm các bean hạ tầng như `BeanFactoryPostProcessor` và `BeanPostProcessor` mà không phải instantiate configuration class. Mặt trái là direct static calls có thể tạo object ngoài managed singleton.

**Cách viết ít phụ thuộc proxy mode nhất** là biểu diễn dependency qua tham số factory method:

```java
@Bean
Product product(Part part) {
    return new Product(part);
}
```

Khi đó Spring resolve `Part` từ BeanFactory, dù bean `part` được tạo bằng instance factory method hay static factory method, và dù configuration class sử dụng full hay lite mode.

### Tài liệu chính thức

- [Spring Framework 6.2 — `@Bean` Javadoc: Full mode, Lite mode và static post-processors](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Bean.html).
- [Spring Framework 6.2 — `@Configuration` Javadoc: `proxyBeanMethods` và các ràng buộc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Configuration.html).
- [Spring Framework Reference — Basic Concepts: `@Bean` and `@Configuration`](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/basic-concepts.html).
- [Spring Framework Reference — Using `@Bean`](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html).
- [Spring Framework Reference — Composing Java-based Configurations](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-configuration-010">Tách việc tạo đối tượng khỏi công việc khởi tạo nghiệp vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Tách việc tạo đối tượng khỏi công việc khởi tạo nghiệp vụ trong Spring Java Configuration

## 1. Mục tiêu và ý tưởng cốt lõi

Trong Spring Core Container, `@Configuration` và `@Bean` cho phép bạn nói với IoC container **đối tượng cần được tạo như thế nào** và **phụ thuộc vào những bean nào**. Nhưng một đối tượng tồn tại trong container không đồng nghĩa ứng dụng đã hoàn thành mọi **công việc khởi tạo nghiệp vụ** (business bootstrap), chẳng hạn nạp dữ liệu nền, kiểm tra điều kiện khai trương dịch vụ, lập danh mục tham chiếu, đăng ký với hệ thống ngoài hoặc kích hoạt một quy trình phục vụ vận hành.

Một lỗi thiết kế thường gặp là đưa những thao tác nghiệp vụ này vào ngay body của `@Bean`:

```java
// Phản ví dụ: không phải chương trình độc lập.
@Bean
CustomerService customerService(CustomerRepository repository) {
    CustomerService service = new CustomerService(repository);
    service.importSeedCustomers(); // Thao tác nghiệp vụ xảy ra khi tạo bean.
    return service;
}
```

Factory method giờ vừa **wiring** vừa **chạy một use case có side effect**. Hậu quả có thể là startup phụ thuộc I/O, giao dịch xảy ra quá sớm, test context gây thay đổi dữ liệu, khó retry, khó kiểm soát thứ tự và hành vi bị lặp lại khi bean/context được tạo thêm.

Nên tách ba trách nhiệm:

1. **Object construction / bean wiring:** tạo object, gán dependency, cấu hình immutable values. Đây là việc của constructor và `@Bean` factory methods.
2. **Bean-local initialization:** xác thực cấu hình, chuẩn bị cấu trúc nội bộ, mở tài nguyên thuộc sở hữu chính bean khi thật sự cần. Đây là việc của `@PostConstruct`, `@Bean(initMethod=...)` hoặc các lifecycle callback tương ứng.
3. **Application/business bootstrap:** điều phối hành động nghiệp vụ sau khi các cộng tác viên cần thiết đã sẵn sàng; có chính sách idempotency, transaction, lỗi và quan sát. Đây thường là việc của một **startup coordinator** hoặc một entry point nghiệp vụ rõ ràng, có thể được kích hoạt qua `SmartInitializingSingleton`, `ContextRefreshedEvent`, `SmartLifecycle` hoặc cơ chế runtime tương ứng.

**Nguyên tắc:** `@Bean` khai báo **cách dựng bean**; service nghiệp vụ khai báo **hành vi**; bootstrap coordinator quyết định **khi nào chạy hành vi khởi tạo**. Không biến constructor/factory method thành nơi thực thi workflow nghiệp vụ.

Bài học này thuộc **Cấu hình Java — Spring Framework Core Container**. Các cơ chế `ApplicationRunner`, `CommandLineRunner` và `ApplicationReadyEvent` của Spring Boot chỉ được nêu để so sánh tại ranh giới, không được dùng trong ví dụ Spring Framework thuần.

## 2. Ba giai đoạn khác nhau cần phân biệt

### 2.1. Giai đoạn A — tạo đối tượng và nối dependency

```java
@Configuration(proxyBeanMethods = false)
class AppConfig {

    @Bean
    OrderRepository orderRepository() {
        return new InMemoryOrderRepository();
    }

    @Bean
    OrderService orderService(OrderRepository repository) {
        return new OrderService(repository);
    }
}
```

Đây là **minh họa cần định nghĩa** `OrderRepository`, `InMemoryOrderRepository`, `OrderService`. Vai trò của `@Bean` chỉ là dựng đối tượng và biểu diễn dependency:

- `orderRepository()` cung cấp object triển khai repository.
- `orderService(OrderRepository repository)` yêu cầu Spring phân giải repository rồi truyền vào service.
- Spring quyết định thời điểm tạo bean dựa trên scope, lazy/eager semantics và dependency graph.
- Không cần gọi nghiệp vụ như `service.placeOrder(...)` trong factory method.

Khi viết `@Configuration(proxyBeanMethods=false)`, truyền dependency qua **tham số factory method** còn giúp tránh direct call từ một `@Bean` method sang method khác gây tạo object ngoài bean identity. Đây là một nguyên tắc wiring, tách biệt với việc bootstrap có nên chạy hay không.

### 2.2. Giai đoạn B — bean-local initialization

Một số bean chỉ hoạt động được sau khi kiểm tra thuộc tính hoặc chuẩn bị trạng thái **của chính nó**:

```java
final class RulesIndex {
    private boolean ready;

    void initialize() {
        // Xây chỉ mục từ cấu hình đã có sẵn trong object.
        ready = true;
    }

    boolean isReady() {
        return ready;
    }
}
```

Có thể khai báo:

```java
@Bean(initMethod = "initialize")
RulesIndex rulesIndex() {
    return new RulesIndex();
}
```

Spring sẽ gọi `initialize()` theo lifecycle khi nó tạo và khởi tạo **managed bean**. Đây là phần chuẩn bị cục bộ, không phải chạy một quy trình tạo hóa đơn, phát email hay sửa dữ liệu kinh doanh.

`@PostConstruct` và `InitializingBean.afterPropertiesSet()` cũng phục vụ cùng nhóm vấn đề. Tuy nhiên, **các init callback thường chạy trong giai đoạn tạo singleton, dưới khóa khởi tạo singleton**. Spring khuyến cáo giữ chúng gọn, tập trung validate trạng thái/chuẩn bị cấu trúc cục bộ; không nên thực hiện tác vụ nặng, gọi lung tung sang các bean khác hoặc I/O phức tạp, vì có nguy cơ kéo dài startup hay gặp deadlock trong quá trình khởi tạo.

### 2.3. Giai đoạn C — business/application bootstrap

Ví dụ một ứng dụng cần bảo đảm dữ liệu khách hàng mẫu có mặt trước khi chấp nhận tác vụ. Đây là một **use case**: cần repository, service, chính sách chống tạo trùng, và có thể liên quan transaction/hệ thống ngoài.

Không nên viết:

```java
@Bean
CustomerService customerService(CustomerRepository repository) {
    CustomerService service = new CustomerService(repository);
    service.register("seed-001"); // Lẫn provisioning bean với use case.
    return service;
}
```

Nên có một coordinator riêng:

```java
// Minh họa: một Spring-managed startup bean.
final class SeedCustomers implements SmartInitializingSingleton {
    private final CustomerService customers;

    SeedCustomers(CustomerService customers) {
        this.customers = customers;
    }

    @Override
    public void afterSingletonsInstantiated() {
        customers.register("seed-001");
    }
}
```

`SmartInitializingSingleton` nằm trong `org.springframework.beans.factory`. Callback `afterSingletonsInstantiated()` được Spring gọi sau khi **hoàn tất pre-instantiation của các singleton thông thường, không lazy** trong bean factory. Nó phù hợp với công việc sau giai đoạn khởi tạo singleton; không nên nhầm là tất cả lazy/prototype beans đã được khởi tạo hoặc toàn bộ hệ thống phân tán đã sẵn sàng.

Ví dụ đầy đủ có thể chạy ở mục 6.

## 3. Vì sao trộn logic nghiệp vụ vào @Bean là vấn đề?

### Factory method bị biến thành lệnh nghiệp vụ

Một factory method lý tưởng chủ yếu có trách nhiệm:

```java
@Bean
InvoiceService invoiceService(InvoiceRepository repository,
                              InvoicePolicy policy) {
    return new InvoiceService(repository, policy);
}
```

Đoạn này chỉ wiring. Nếu thay bằng:

```java
@Bean
InvoiceService invoiceService(InvoiceRepository repository,
                              InvoicePolicy policy) {
    InvoiceService service = new InvoiceService(repository, policy);
    service.generateMonthlyInvoices(); // Side effect nghiệp vụ.
    return service;
}
```

người dùng không cần gọi `generateMonthlyInvoices()` mà công việc đã chạy trong lúc context dựng bean. Những tác hại thường gặp:

- **Startup khó dự đoán:** DB/network chậm có thể kéo dài hoặc làm hỏng context initialization.
- **Dễ chạy lặp:** integration tests, context refresh, nhiều ứng dụng/replica hoặc một scope tạo bean lại có thể khiến nghiệp vụ chạy nhiều lần.
- **Side effect ngoài ý muốn:** chạy test khởi tạo context có thể tự gửi email hoặc thay đổi dữ liệu thật.
- **Transaction không rõ:** gọi method ngay trong factory/constructor không tự bảo đảm nó đi qua proxy `@Transactional` mong muốn.
- **Khó vận hành:** không có điểm riêng cho idempotency, retry, metrics, logging, readiness hoặc rollback.
- **Khó kiểm thử:** chỉ muốn test wiring nhưng lại phải giả lập cả external services và dữ liệu.

Các class `InvoiceRepository`, `InvoicePolicy`, `InvoiceService` ở hai đoạn trên là kiểu giả định; đây là **phản ví dụ minh họa**, không phải project tự biên dịch.

### Constructor cũng không phải entry point nghiệp vụ

```java
final class InvoiceService {
    InvoiceService(InvoiceRepository repository) {
        this.repository = repository;
        generateMonthlyInvoices(); // Thiết kế khó kiểm soát.
    }
}
```

Việc constructor gọi business workflow có cùng vấn đề: gây side effect trước khi object được dựng xong, khó kiểm thử, có thể phụ thuộc bean khác chưa sẵn sàng, và khó xác định retry/transaction.

Constructor nên ghi nhận/kiểm tra dependency và thiết lập invariant nội bộ, không làm các tác vụ tác động ra bên ngoài trừ khi việc mở một resource nội bộ là một phần hợp đồng xây đối tượng được cân nhắc kỹ.

### Đừng "chữa" bằng cách gọi thủ công @Bean method khác

```java
@Configuration(proxyBeanMethods = false)
class BadConfig {
    @Bean
    InvoiceRepository repository() {
        return new JdbcInvoiceRepository();
    }

    @Bean
    InvoiceService invoiceService() {
        InvoiceService service = new InvoiceService(repository());
        service.generateMonthlyInvoices();
        return service;
    }
}
```

Có **hai** rủi ro khác nhau:

1. `repository()` được gọi trực tiếp trong lite mode, có thể tạo repository **ngoài** bean do container quản lý.
2. `generateMonthlyInvoices()` bị chạy trong giai đoạn tạo bean.

Cách sửa: inject `InvoiceRepository` qua **tham số `@Bean`**, đồng thời dời việc chạy use case sang startup coordinator hoặc một lệnh nghiệp vụ được gọi có chủ đích.

## 4. Dùng cơ chế nào cho loại công việc nào?

| Việc cần làm | Vị trí thường phù hợp | Vì sao |
| --- | --- | --- |
| Dựng object, truyền dependency | Constructor, `@Bean` method | Factory/wiring rõ trách nhiệm |
| Kiểm tra tham số cấu hình cục bộ | Constructor hoặc bean init callback | Phát hiện cấu hình sai sớm |
| Chuẩn bị map/cache nhỏ từ dữ liệu đã có sẵn trong bean | `@PostConstruct`/`@Bean(initMethod)` | Bean-local initialization |
| Giải phóng tài nguyên thuộc sở hữu bean | `@PreDestroy`/`@Bean(destroyMethod)`/`close()` | Bean lifecycle cleanup |
| Công việc cần tất cả non-lazy singleton thông thường đã dựng xong | `SmartInitializingSingleton` | Callback sau giai đoạn pre-instantiation singleton |
| Việc phải diễn ra khi context đã refresh | `ApplicationListener<ContextRefreshedEvent>` hoặc `@EventListener` | Sự kiện context lifecycle |
| Thành phần cần start/stop theo phase, hỗ trợ shutdown | `SmartLifecycle` | Managed lifecycle, có start/stop |
| Tác vụ chỉ chạy khi người dùng/job kích hoạt | Service method gọi từ entry point thích hợp | Không ép tác vụ nghiệp vụ chạy khi startup |
| Boot application cần startup command theo thứ tự và tích hợp lifecycle Boot | `ApplicationRunner`/`CommandLineRunner` | **Spring Boot**, ngoài API core |
| Boot cần biết toàn application đã sẵn sàng để phục vụ | `ApplicationReadyEvent` hoặc cơ chế readiness | **Spring Boot**, không thay cho callback core |

Bảng này không nói rằng **mọi ứng dụng phải có bootstrap job**. Nhiều ứng dụng chỉ cần đăng ký bean rồi chờ request hoặc message; trong trường hợp đó **không chạy nghiệp vụ tại startup** là thiết kế đúng nhất.

## 5. Lifecycle của container: các mốc quan trọng

Một ApplicationContext Spring thuần thường đi qua những giai đoạn khái niệm sau:

```text
Đăng ký / đọc các bean definitions
        |
        v
Xử lý bean factory/post-processor
        |
        v
Khởi tạo các singleton non-lazy thông thường
  + gọi factory/constructor
  + dependency injection
  + @PostConstruct / initMethod
  + BeanPostProcessor
        |
        v
SmartInitializingSingleton.afterSingletonsInstantiated()
        |
        v
Hoàn tất refresh, quản lý lifecycle
        |
        v
ContextRefreshedEvent
        |
        v
Ứng dụng thực hiện các tác vụ được yêu cầu
        |
        v
Context đóng: stop/destroy callbacks thích hợp
```

Đây là **sơ đồ khái niệm**, không phải toàn bộ trình tự nội bộ của mọi ApplicationContext hay framework extension. Sự kiện refresh có thể được phát lại khi context được refresh lần nữa, và cấu trúc parent-child có thể tạo nhiều sự kiện từ các context khác nhau.

### @PostConstruct có phải "sau toàn bộ application startup"?

**Không.** `@PostConstruct` chạy trong vòng đời khởi tạo của **một bean cụ thể**. Nó không bảo đảm tất cả bean khác đã được khởi tạo; một số còn lazy, prototype hoặc phụ thuộc config/condition khác.

Ví dụ:

```java
// Đoạn minh họa, cần Jakarta Annotations trong classpath.
@PostConstruct
void init() {
    validateLocalSettings();
}
```

Một tác vụ có nhiều external calls, dài hạn, cần transaction hoặc phối hợp toàn hệ thống thường không phù hợp ở đây. Spring còn cảnh báo init callbacks dưới singleton creation lock có thể gây rủi ro deadlock nếu thao tác nặng/tra cứu bean ngoài luồng.

### SmartInitializingSingleton có phải "ứng dụng đã sẵn sàng phục vụ"?

**Không hoàn toàn.** Nó báo rằng BeanFactory đã hoàn thành **pre-instantiation của các singleton non-lazy thông thường**, nhưng một số hoạt động hoàn tất refresh/lifecycle có thể diễn ra sau đó. Nếu công việc thật sự phụ thuộc vào context refresh hoàn tất, hãy chọn `ContextRefreshedEvent`; nếu phụ thuộc web server readiness trong Spring Boot, cân nhắc cơ chế Boot tương ứng.

### ContextRefreshedEvent có thể lặp

Sự kiện `ContextRefreshedEvent` phát khi một `ApplicationContext` được khởi tạo/refresh. Nó không phải giao kèo "chạy nghiệp vụ đúng một lần duy nhất trong vòng đời công ty". Trong hệ thống parent-child contexts, một listener có thể cần kiểm tra sự kiện từ context nào; khi refresh lại, sự kiện có thể phát tiếp.

**Giả định một callback == exactly-once nghiệp vụ là sai.** Nếu tác vụ tạo dữ liệu hoặc tác động ra hệ thống ngoài, cần idempotency/điều phối phù hợp.

## 6. Ví dụ chạy được: factory thuần, init cục bộ và business bootstrap riêng

Ví dụ dùng **Java 17 + Spring Framework 6.2** thuần với `AnnotationConfigApplicationContext`. Không có Spring Boot, database hoặc mạng ngoài.

Ta xây:

- `CustomerRepository`: lưu định danh khách hàng trong bộ nhớ; có phép thêm nếu chưa tồn tại.
- `Rules`: một bean có initialization callback nhỏ để đánh dấu sẵn sàng, và destroy callback khi context đóng.
- `CustomerService`: chứa nghiệp vụ đăng ký khách hàng, không tự chạy gì khi được dựng.
- `SeedCustomers`: coordinator implement `SmartInitializingSingleton`, thực hiện một thao tác seed sau khi các singleton thông thường đã được khởi tạo.
- `AppConfig`: các phương thức `@Bean` **chỉ tạo/wire bean**, không gọi `customers.register(...)` trong method factory.

### 6.1. Cấu trúc thư mục

```text
spring-init-separation/
├── pom.xml
└── src/main/java/demo/InitSeparationDemo.java
```

### 6.2. File pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>spring-init-separation</artifactId>
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

### 6.3. File src/main/java/demo/InitSeparationDemo.java

```java
package demo;

import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class InitSeparationDemo {

    interface CustomerRepository {
        boolean saveIfAbsent(String id);
        boolean contains(String id);
    }

    static final class MemoryCustomerRepository implements CustomerRepository {
        private final Set<String> ids = new HashSet<>();

        @Override
        public boolean saveIfAbsent(String id) {
            return ids.add(id);
        }

        @Override
        public boolean contains(String id) {
            return ids.contains(id);
        }
    }

    static final class Rules {
        private boolean ready;

        void initialize() {
            ready = true;
            System.out.println("rules.initialize");
        }

        boolean isReady() {
            return ready;
        }

        void close() {
            System.out.println("rules.close");
        }
    }

    static final class CustomerService {
        private final CustomerRepository repository;
        private final Rules rules;

        CustomerService(CustomerRepository repository, Rules rules) {
            this.repository = repository;
            this.rules = rules;
        }

        String register(String id) {
            if (!rules.isReady()) {
                throw new IllegalStateException("Rules not initialized");
            }
            return repository.saveIfAbsent(id)
                    ? "created:" + id
                    : "existing:" + id;
        }

        boolean exists(String id) {
            return repository.contains(id);
        }
    }

    static final class SeedCustomers implements SmartInitializingSingleton {
        private final CustomerService customers;

        SeedCustomers(CustomerService customers) {
            this.customers = customers;
        }

        @Override
        public void afterSingletonsInstantiated() {
            // Công việc khởi tạo nghiệp vụ có entry point riêng.
            System.out.println("bootstrap=" +
                    customers.register("seed-001"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AppConfig {

        @Bean
        CustomerRepository customerRepository() {
            return new MemoryCustomerRepository();
        }

        @Bean(initMethod = "initialize", destroyMethod = "close")
        Rules rules() {
            return new Rules();
        }

        @Bean
        CustomerService customerService(
                CustomerRepository repository, Rules rules) {
            return new CustomerService(repository, rules);
        }

        @Bean
        SeedCustomers seedCustomers(CustomerService customers) {
            return new SeedCustomers(customers);
        }
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            CustomerService customers =
                    context.getBean(CustomerService.class);

            System.out.println("main.seedFound=" +
                    customers.exists("seed-001"));
            System.out.println("main.new=" +
                    customers.register("c-100"));
            System.out.println("main.repeat=" +
                    customers.register("seed-001"));
        }
    }
}
```

### 6.4. Cách chạy

Trong thư mục chứa `pom.xml`, với JDK 17+ và Maven:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.InitSeparationDemo
```

Các dòng **do chương trình in ra** (không bao gồm thông điệp log mà Maven/Spring có thể ghi tùy môi trường):

```text
rules.initialize
bootstrap=created:seed-001
main.seedFound=true
main.new=created:c-100
main.repeat=existing:seed-001
rules.close
```

### 6.5. Diễn giải output và trách nhiệm của từng nơi

1. Spring nạp `AppConfig` và bốn `@Bean` definitions. Những factory methods chỉ trả instance hoặc truyền dependency. Không method nào gọi `CustomerService.register` để thực hiện business bootstrap.
2. Khi container tạo managed bean `Rules`, nó gọi `Rules.initialize()` vì `@Bean(initMethod = "initialize")`. Dòng `rules.initialize` xuất hiện ở giai đoạn bean-local initialization. Không cần toàn bộ ứng dụng đã refresh để set flag này.
3. Sau khi hoàn thành khởi tạo các singleton non-lazy thông thường, Spring gọi `SeedCustomers.afterSingletonsInstantiated()`. Coordinator gọi một use case `CustomerService.register("seed-001")` và in `bootstrap=created:seed-001`.
4. Khi constructor `AnnotationConfigApplicationContext(AppConfig.class)` trả về, chương trình `main` lấy `CustomerService`. Seed đã chạy, nên `main.seedFound=true`.
5. Gọi đăng ký ID mới `c-100` trả `created:c-100`; gọi lại ID seed trả `existing:seed-001` do repository dùng `Set.add(...)` để chống trùng ở **mức bộ nhớ trong cùng context**.
6. Khi thoát `try-with-resources`, Spring đóng context và gọi callback `Rules.close()` trên singleton `Rules`, nên xuất hiện `rules.close`.

**Lưu ý về tính idempotent:** `Set.add` trong ví dụ là demo in-memory đơn luồng. Nó không thay thế database uniqueness/transaction, phân tán lock hay idempotency keys trong môi trường có nhiều process/replica. Trong sản phẩm thực, seed phải có chính sách chống chạy trùng ở cấp dữ liệu/hệ thống, không chỉ một biến static hoặc boolean trong instance.

### 6.6. Lợi ích của việc tách như ví dụ

- `AppConfig` dễ đọc: chỉ có "tạo repository", "tạo rules", "tạo service", "tạo bootstrap coordinator".
- `Rules.initialize()` mô tả trạng thái sẵn sàng cục bộ của một bean và không gọi ra use case.
- `CustomerService.register` chứa nghiệp vụ, có thể test riêng bằng fake repository/rules.
- `SeedCustomers` có entry point rõ cho việc **kích hoạt** một tác vụ lúc startup; có thể đổi chính sách kích hoạt mà không sửa constructor/factory.
- Consumer runtime không cần biết startup callback nào đã tạo dữ liệu seed.
- Nếu muốn bỏ hoàn toàn seed khỏi một môi trường, có thể **không đăng ký** `SeedCustomers` trong cấu hình môi trường đó (ví dụ chia `@Configuration` hoặc dùng `@Profile` phù hợp), thay vì phải đặt `if` vào constructor service.

## 7. @PostConstruct, @Bean(initMethod) và InitializingBean: dùng đúng phạm vi

Spring hỗ trợ nhiều cách khai báo callback **khởi tạo bean**:

### 7.1. @PostConstruct

```java
import jakarta.annotation.PostConstruct;

final class TokenFormat {
    private String prefix;

    @PostConstruct
    void validate() {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalStateException("Missing token prefix");
        }
    }
}
```

Đây là **đoạn minh họa**, `prefix` phải được cấu hình/đưa vào object trong mã thực. Annotation `@PostConstruct` từ `jakarta.annotation` yêu cầu API Jakarta Annotations có trên classpath và post-processor của Spring phù hợp. Với Spring Framework 6.x, dùng Jakarta namespace, không dùng `javax.annotation.PostConstruct` của thế hệ cũ.

Phù hợp: kiểm tra/truyền đạt invariant hoặc chuẩn bị trạng thái nội bộ. Không phù hợp: quét dữ liệu lớn, gọi API nhiều hệ thống, thực hiện vòng lặp nghiệp vụ kéo dài trong init callback.

### 7.2. @Bean(initMethod = "...")

```java
@Bean(initMethod = "initialize")
TokenParser tokenParser() {
    return new TokenParser();
}
```

Đây là đoạn minh họa cần `TokenParser.initialize()` thực sự tồn tại. Phù hợp khi bạn cấu hình object của thư viện không muốn sửa để thêm `@PostConstruct`. Method init được Spring gọi sau bước tạo/thiết lập bean theo lifecycle, không cần phải gọi `tokenParser.initialize()` ngay trong factory method.

Với bean dùng `@Bean`, bạn có thể đặt `destroyMethod` để cleanup tài nguyên; nếu object cung cấp `close` hoặc `shutdown` phù hợp, Spring cũng có cơ chế suy luận destroy method. Nên hiểu rõ bean có thật sự **sở hữu** tài nguyên đó không trước khi để container tự đóng.

### 7.3. InitializingBean.afterPropertiesSet()

```java
final class LocalIndex implements org.springframework.beans.factory.InitializingBean {
    @Override
    public void afterPropertiesSet() {
        // Chỉ kiểm tra và chuẩn bị state của chính bean.
    }
}
```

Cách này trực tiếp phụ thuộc Spring API. Nó vẫn là một **bean-local lifecycle callback**, không tự trở thành ứng dụng-level startup hook.

### 7.4. Không gọi callback init bằng tay rồi lại khai báo với Spring

Nếu:

```java
@Bean(initMethod = "initialize")
Rules rules() {
    Rules rules = new Rules();
    rules.initialize();
    return rules;
}
```

thì `initialize()` có thể chạy **hai lần**: một lần do body factory tự gọi, một lần do lifecycle callback của BeanFactory. Nếu init không idempotent, có thể tạo lỗi hoặc mở resource hai lần.

Hãy chọn **một cơ chế sở hữu việc init**, không vừa tự gọi vừa bảo Spring gọi lặp. Nếu constructor đã bảo đảm object hợp lệ và không cần init callback, đừng thêm callback chỉ để "có đủ lifecycle annotations".

### 7.5. @PostConstruct có gọi được @Transactional method không?

Đừng giả định init callback là entry point hợp lệ cho transaction AOP. Bean có thể chưa qua các proxy/post-processors cần thiết, và self-invocation tới method cùng instance thường không đi qua proxy trong proxy-based AOP.

Khi bootstrap cần transaction, để một **managed coordinator** gọi một **service khác** qua proxy phù hợp sau khi application infrastructure đã sẵn sàng, với `@Transactional` được bật/cấu hình đúng. Cũng cần xác định chính xác thời điểm transaction có thể được mở, rollback và retry; không lấy sự tồn tại của annotation làm bằng chứng chắc chắn transaction hoạt động.

## 8. SmartInitializingSingleton: sau khởi tạo singleton nhưng trước khi coi hệ thống sẵn sàng

`SmartInitializingSingleton` là interface Spring Core có method:

```java
void afterSingletonsInstantiated();
```

Nó được gọi ở cuối giai đoạn pre-instantiation singleton của `BeanFactory`. Lợi ích so với `@PostConstruct`:

- Bean coordinator đã có các dependency được inject.
- Các singleton thông thường, không lazy, đã qua khởi tạo, giảm nguy cơ đụng vào bean khác khi nó còn ở giữa vòng đời khởi tạo.
- Callback được kích hoạt qua cơ chế BeanFactory, không cần viết lệnh gọi nghiệp vụ trong `@Bean` factory method.
- Phù hợp với setup hậu khởi tạo cần nhiều singleton nhưng chưa đòi event context refresh hoàn tất.

Giới hạn quan trọng:

- Không có bảo đảm mọi **lazy singleton**, prototype hoặc external resource đã sẵn sàng.
- Callback chạy đồng bộ trong đường khởi tạo/refresh thông thường; tác vụ nặng có thể kéo dài startup.
- Không mặc định có retry, timeout, transaction hay cơ chế phân tán.
- Nhiều callbacks cùng tồn tại không nên dựa vào thứ tự tình cờ nếu có dependency thứ tự nghiệp vụ giữa chúng; hãy điều phối tường minh.
- Callback không phải sự kiện exactly-once across replicas.
- Nếu bean coordinator cũng bị đánh `@Lazy` và không được khởi tạo trong giai đoạn pre-instantiation, không nên mặc định callback chắc chắn đã chạy.

### Khi cần thứ tự giữa nhiều tác vụ

Giả sử ứng dụng phải **tạo schema hợp lệ**, sau đó **nạp danh mục tham chiếu**, rồi mới **bật một adapter phục vụ traffic**. Đừng tạo ba bean `SmartInitializingSingleton` rồi mặc định Spring sẽ gọi chúng theo một thứ tự kinh doanh cụ thể dựa vào thứ tự viết `@Bean` methods.

Nên có một **coordinator** sở hữu chuỗi bước:

```java
// Đoạn minh họa; từng action là interface/bean ứng dụng.
final class StartupCoordinator implements SmartInitializingSingleton {
    private final ValidateSchema validateSchema;
    private final SeedReferenceData seedReferenceData;

    StartupCoordinator(ValidateSchema validateSchema,
                       SeedReferenceData seedReferenceData) {
        this.validateSchema = validateSchema;
        this.seedReferenceData = seedReferenceData;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validateSchema.run();
        seedReferenceData.run();
    }
}
```

Đây không phải file độc lập. `ValidateSchema` và `SeedReferenceData` phải được định nghĩa trong ứng dụng; coordinator thể hiện rõ trình tự và tạo một nơi để quản lý idempotency/logging/failure policy.

## 9. ContextRefreshedEvent: khi cần ngữ cảnh đã refresh

Spring phát `ContextRefreshedEvent` khi `ApplicationContext` đã được khởi tạo hoặc refresh. Có thể lắng nghe qua:

```java
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

@Component
final class StartupListener {
    private final ReferenceDataService referenceData;

    StartupListener(ReferenceDataService referenceData) {
        this.referenceData = referenceData;
    }

    @EventListener(ContextRefreshedEvent.class)
    void onRefresh() {
        referenceData.ensureSeeded();
    }
}
```

Đây là **đoạn minh họa**, cần import `@Component`, định nghĩa `ReferenceDataService` và đảm bảo scanner tìm thấy listener. Method được Spring gọi khi event phù hợp được dispatch; không nên coi event listener là phương tiện duy nhất để giải quyết mọi startup task.

Điểm cần cẩn trọng:

- Context có thể **refresh nhiều lần** nếu kiểu context/luồng vận hành hỗ trợ, nên listener có thể chạy lại.
- Nếu có **parent-child context**, một listener có thể thấy sự kiện ở các context khác nhau tùy cấu trúc sự kiện; khi cần, lọc `event.getApplicationContext()`.
- Event multicaster thường dispatch đồng bộ theo mặc định, nhưng có thể được cấu hình bất đồng bộ; không kết luận event handling là barrier readiness nếu kiến trúc đã đổi cách dispatch.
- Nếu callback thất bại, tác động lên startup còn phụ thuộc cơ chế dispatch, exception handling và context lifecycle; cần test đường lỗi thực tế.
- `ContextRefreshedEvent` nói về **Spring context**, không mặc nhiên đảm bảo external database, broker và dịch vụ từ xa đều sẵn sàng.

### @EventListener và ApplicationListener

Hai dạng tương đương về ý định lắng nghe event, không bắt buộc viết cả hai:

```java
// Minh họa dạng interface:
final class Listener implements
        org.springframework.context.ApplicationListener<ContextRefreshedEvent> {

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // Xử lý một lần phát sự kiện context refresh.
    }
}
```

Dạng class này cũng phải được đăng ký làm bean/listener trong context. Dùng `@EventListener` khi muốn method-based listener; dùng interface khi phù hợp với infrastructure contract. Dù chọn dạng nào, chính sách idempotency vẫn nằm ở nghiệp vụ.

## 10. SmartLifecycle: khi bootstrap là một thành phần có start/stop

Một thành phần chạy nền như polling consumer, listener adapter hoặc scheduler có vòng đời **bắt đầu — hoạt động — dừng**. Việc xây object và khởi tạo bean chỉ là một phần; chạy nền nên được điều khiển theo lifecycle.

`SmartLifecycle` của Spring hỗ trợ:

- `start()`: bắt đầu hoạt động.
- `stop()` hoặc `stop(Runnable callback)`: dừng theo cơ chế đồng bộ/bất đồng bộ phù hợp.
- `isRunning()`: báo trạng thái.
- `getPhase()`: định nghĩa phase ordering giữa những lifecycle components.
- `isAutoStartup()`: xác định có tự start khi context refresh hay không.

Sơ đồ ý tưởng:

```text
@Bean factory: new MessageConsumer(...)
            |
            v
Bean đã được Spring quản lý
            |
            v
Lifecycle processor gọi start()
            |
            v
Consumer lắng nghe message theo phase
            |
            v
Context stop/close -> stop(callback)
            |
            v
Destroy callbacks và tài nguyên được cleanup
```

Không nên mở thread dài hạn hoặc chạy vòng lặp vô hạn trong constructor, `@Bean` factory, hay `@PostConstruct`. Điều đó có thể chặn context initialization, làm trạng thái runtime khó dừng đúng thứ tự hoặc gây rò rỉ tài nguyên.

Một implementation `SmartLifecycle` phải xử lý concurrency, shutdown timeout và callback hoàn tất đúng hợp đồng; chỉ nên dùng khi bạn thật sự có **một thành phần có vòng đời hoạt động**, không phải cho thao tác seed dữ liệu đơn giản chạy một lần.

## 11. Phân biệt Spring Framework Core và Spring Boot

Trong ứng dụng **Spring Framework thuần**, bạn thường chọn:

- `SmartInitializingSingleton` khi cần hành động sau pre-instantiation singleton.
- `ContextRefreshedEvent` khi cần một hook sau refresh.
- `SmartLifecycle` khi có component khởi động/dừng theo phase.
- Entry point ứng dụng gọi service trực tiếp khi công việc không thuộc startup.

Trong **Spring Boot**, ngoài các lựa chọn của Spring Framework còn có:

- `ApplicationRunner` và `CommandLineRunner`: hooks thực hiện khi Boot application đã bắt đầu theo lifecycle runner của Boot; có thể dùng `@Order`/`Ordered` theo chính sách Boot.
- `ApplicationReadyEvent`: tín hiệu Boot đã hoàn thành quá trình startup và chuẩn bị phục vụ request, phát sau các runners theo lifecycle thông thường.
- Readiness/liveness và các cấu hình vận hành khác của Boot.

Cần phân biệt: **`ApplicationReadyEvent` không thuộc Spring Core Container**, và dù nó biểu diễn readiness ở cấp ứng dụng Boot, cũng không hứa các hệ thống ngoài hay một cluster phân tán đã sẵn sàng. Không copy `CommandLineRunner` vào một ví dụ chỉ khai báo dependency `spring-context` rồi mong code biên dịch.

### Khi nào không nên bootstrap tự động?

Nếu một tác vụ có thể chạy theo lịch, được kích hoạt bởi admin, phải chờ approval hoặc cần quota/lock phân tán, đừng buộc nó chạy mỗi lần một instance ứng dụng khởi động. Hãy để entry point tương ứng gọi một service, có trạng thái/tín hiệu riêng. Tách factory khỏi nghiệp vụ giúp bạn đổi **trigger** mà không phải viết lại object wiring.

## 12. Tính idempotent và giao dịch của bootstrap nghiệp vụ

Một tác vụ business bootstrap có thể xảy ra nhiều lần:

- Khởi động lại ứng dụng sau lỗi.
- Nhiều replica cùng khởi động.
- Tạo nhiều Spring contexts trong integration tests.
- Context được refresh hoặc listener nhận nhiều sự kiện.
- Retry do cơ chế quản trị/triển khai.
- Chạy lệnh seed thủ công song song với bootstrapping.

Vì thế **đúng một lần trong một JVM** thường chưa đủ. Với dữ liệu quan trọng, cần invariant có thể kiểm tra trong data store.

### Tạo nếu chưa có, không phải tạo lại vô điều kiện

Thay vì:

```java
// Phản ví dụ:
void seedRoles() {
    roleRepository.insert("ADMIN"); // Chạy lại có thể lỗi/trùng.
}
```

hãy thiết kế thao tác idempotent:

```java
// Đoạn minh họa cấp ứng dụng:
void ensureDefaultRole() {
    roleRepository.insertIfAbsent("ADMIN");
}
```

Method `insertIfAbsent` là API ứng dụng giả định. Ở database thật, cần unique constraint/UPSERT hoặc transaction phù hợp để tránh race condition giữa nhiều instance. Một thao tác kiểu "query trước rồi insert" tách biệt không tự an toàn trong cạnh tranh nếu thiếu ràng buộc dữ liệu.

### Transaction phải nằm ở ranh giới thích hợp

Khi cần transaction, coordinator có thể gọi một service Spring-managed khác:

```java
// Đoạn minh họa: hàm transaction thuộc bean được proxy hợp lệ.
final class SeedCoordinator {
    private final RoleBootstrapService roleService;

    SeedCoordinator(RoleBootstrapService roleService) {
        this.roleService = roleService;
    }

    void execute() {
        roleService.ensureDefaultRoles();
    }
}
```

`RoleBootstrapService` có thể định nghĩa transaction boundary với `@Transactional` khi hạ tầng transaction đã bật. Không giả định gọi method `@Transactional` từ constructor hoặc `@PostConstruct` của **chính bean đó** sẽ đi qua transaction proxy. Tương tự, không nên tự gọi một method transaction khác trong cùng class rồi nghĩ proxy-based advice luôn được áp dụng.

### Chính sách khi bootstrap thất bại

Phải xác định rõ:

| Loại tác vụ | Khi thất bại nên làm gì? |
| --- | --- |
| Bắt buộc để hoạt động an toàn | Startup thất bại hoặc không công bố readiness, cần báo rõ nguyên nhân |
| Dữ liệu nền có thể nạp lại | Retry có kiểm soát, không tạo trùng, có metrics |
| Bổ trợ như warm-up cache | Cho phép degraded mode nếu nghiệp vụ chịu được; ghi log và giám sát |
| Lệnh nghiệp vụ kích hoạt thủ công | Trả kết quả/lỗi cho caller, không giấu trong lifecycle hook |
| External resource không có sẵn | Có timeout, circuit breaker/retry hoặc ràng buộc readiness theo yêu cầu |

Đừng bắt `Exception` rồi âm thầm bỏ qua một bước seed bắt buộc để container vẫn báo healthy. Ngược lại, một cache warm-up không bắt buộc có thể không đáng làm cả ứng dụng fail startup — nhưng đó phải là **quyết định nghiệp vụ**, không phải side effect ngẫu nhiên của `@Bean`.

## 13. Quan sát, testability và side-effect budget

Việc tách trách nhiệm giúp kiểm thử theo ba tầng.

### Test construction riêng

Kiểm tra `CustomerService` bằng repository fake:

```java
// Đoạn minh họa trong test unit; cần fake repository và Rules hợp lệ.
CustomerService service = new CustomerService(fakeRepository, preparedRules);
String result = service.register("customer-1");
```

Không cần dựng Spring context chỉ để kiểm tra business method; đây là lợi thế của constructor injection rõ ràng.

### Test bean initialization riêng

Một test Spring nhỏ xác minh `Rules.initialize()` được gọi khi bean được tạo qua BeanFactory và trạng thái `isReady()` là `true`. Không tự gọi `new AppConfig().rules()` rồi cho rằng đã kiểm thử Spring-managed init callbacks.

### Test bootstrap riêng

Tạo `SeedCustomers` với fake `CustomerService` hoặc dùng ApplicationContext tích hợp rồi kiểm tra dữ liệu seed đã được tạo trước khi context constructor trả về. Với ví dụ đầy đủ, có thể thêm assert sau bên trong `main`:

```java
CustomerService customers =
        context.getBean(CustomerService.class);

if (!customers.exists("seed-001")) {
    throw new AssertionError("Startup seed was not created");
}
```

Đoạn bổ sung này không in thêm stdout khi đúng. Nó kiểm tra hiệu quả thật của coordinator, không chỉ kiểm tra callback được khai báo trong source.

### Integration test kiểm tra context không kích hoạt nghiệp vụ ngoài ý muốn

Nếu một test chỉ cần repository/service bean để kiểm tra wiring, tốt nhất có một cấu hình test **không đăng ký bootstrap coordinator**, hoặc thay thế external gateway bằng fake. Khi factory thuần construction, việc mở ApplicationContext không tự chạy những use case không liên quan trừ khi bạn cố ý đăng ký startup components.

### Observability cho startup job

Trong hệ thống thật, ghi nhận:

- Tên bước và thời điểm bắt đầu/kết thúc.
- Thành công/thất bại và nguyên nhân gốc.
- Số lượng bản ghi đã tạo/cập nhật/bỏ qua.
- Trạng thái idempotency/retry/lock.
- Tổng thời gian và timeout.
- Version/schema/data migration tương ứng.
- Readiness hoặc degraded mode nếu có.

Đừng log secrets/token hoặc nội dung dữ liệu nhạy cảm chỉ để thấy bootstrap chạy. Metric/log cũng không thay thế tính đúng của unique constraints/transaction.

## 14. Những anti-pattern quan trọng

### Anti-pattern A: gửi email ngay trong factory

```java
@Bean
EmailService emailService(EmailGateway gateway) {
    EmailService service = new EmailService(gateway);
    service.sendWelcomeEmails(); // Side effect lặp khi context dựng.
    return service;
}
```

**Sửa:** factory chỉ tạo `EmailService`; một job/command được kích hoạt đúng thời điểm gọi `sendWelcomeEmails()` với idempotency và quan sát riêng. Nếu gửi email phải được người dùng kích hoạt, hoàn toàn không chạy ở startup.

### Anti-pattern B: nạp dữ liệu lớn trong @PostConstruct

```java
@PostConstruct
void init() {
    externalApi.downloadEverythingAndWriteDatabase();
}
```

**Sửa:** bean-local init chỉ validate/precompute nhỏ. Đưa tác vụ nặng vào coordinator sau giai đoạn singleton creation hoặc job bất đồng bộ có policy riêng; nếu cần readiness gating, thiết kế rõ ràng.

### Anti-pattern C: tạo thread trong constructor

```java
public PollingConsumer() {
    new Thread(this::pollForever).start();
}
```

**Sửa:** constructor chỉ lưu config/dependencies; sử dụng `SmartLifecycle` hoặc một scheduler/managed executor phù hợp để start/stop; xử lý shutdown và exception đúng hợp đồng.

### Anti-pattern D: dùng @DependsOn để điều phối tất cả công việc nghiệp vụ

```java
@Bean
@DependsOn("seedData")
ReportService reportService() {
    return new ReportService();
}
```

`@DependsOn` thể hiện **ràng buộc trình tự khởi tạo/hủy bean**, không biến `seedData` thành hệ thống orchestration nghiệp vụ có retry, transaction và idempotency. Nếu cần A→B→C business steps, hãy mô tả các bước trong coordinator/test rõ ràng.

### Anti-pattern E: chạy tác vụ trong @Configuration constructor

```java
@Configuration
class StartupConfig {
    StartupConfig(ReportingService reporting) {
        reporting.generateMonthlyReport();
    }
}
```

**Sửa:** class config làm wiring, không chạy workflow; tránh ép container làm việc ngoài nhiệm vụ khi chính class config còn đang khởi tạo. Đặc biệt nguy hiểm nếu constructor cần những bean mà factory definitions của class cấu hình này đang cung cấp, dễ tạo vòng phụ thuộc.

### Anti-pattern F: hiểu @Bean(initMethod) như "application ready"

```java
@Bean(initMethod = "startAllJobs")
JobCoordinator jobCoordinator() {
    return new JobCoordinator();
}
```

Init callback là **bean lifecycle**, không tự bảo đảm mọi application-level dependency/external service đã sẵn sàng. Nếu `startAllJobs` cần nhiều bean, DB, transaction hoặc dữ liệu nghiệp vụ, cần xem lại trigger và lifecycle orchestration.

## 15. Chẩn đoán lỗi theo triệu chứng

| Triệu chứng | Nguyên nhân có thể | Hướng xử lý |
| --- | --- | --- |
| Mỗi lần test context mở đều gửi email/tạo dữ liệu | Logic nghiệp vụ nằm trong constructor, `@Bean` hoặc init callback | Tách use case khỏi wiring; đăng ký startup coordinator có chủ đích |
| Context không khởi động khi DB chậm | Factory/init callback gọi I/O bên ngoài quá sớm | Dời tác vụ sang lifecycle phù hợp; timeout/retry/readiness |
| Bean khác chưa sẵn mà @PostConstruct đã gọi | Init callback chạy trong khởi tạo bean, không đợi toàn bộ singleton | Chuẩn bị state nội bộ ở init; chuyển orchestration sang callback muộn hơn |
| Deadlock hoặc refresh bị treo | Callback init giữ singleton creation lock và tra cứu/call ngoài luồng | Giảm init work; dùng `SmartInitializingSingleton`/refresh event phù hợp |
| Method @Transactional không có transaction | Self-invocation hoặc gọi trong init trước proxy hoàn chỉnh | Gọi qua service/proxy phù hợp ở ranh giới muộn hơn |
| Dữ liệu seed trùng sau scale-out | Chỉ dùng cờ boolean trong một JVM/context | Dùng unique constraint/UPSERT và idempotency đa instance |
| Resource bị mở hai lần | Vừa gọi init trong factory vừa khai báo `initMethod`; hoặc direct @Bean call trong lite config | Chọn một callback, inject managed bean qua tham số |
| Một task chạy nhiều lần sau refresh | Listener nhận nhiều `ContextRefreshedEvent` | Lọc context đúng và thiết kế idempotency |
| Startup callback không chạy ở bean lazy | Bean callback không thuộc non-lazy singleton pre-instantiation | Đăng ký coordinator non-lazy nếu thật sự cần startup hook |
| Tưởng SmartInitializingSingleton đồng nghĩa mọi external service sẵn | Callback chỉ báo giai đoạn trong BeanFactory | Dùng explicit health/readiness/dependency checks |
| Test unit bắt buộc dựng database | Constructor/factory tự thực thi nghiệp vụ | Constructor injection, fake dependencies, tách bootstrap |
| Shutdown rò rỉ thread/pool | Mở tài nguyên hoặc thread mà không quản lý lifecycle | `destroyMethod`, `SmartLifecycle`, managed executor |
| Không có log nguyên nhân bootstrap fail | Lỗi bị catch rồi bỏ qua ở init | Exception/metrics/logging theo chính sách vận hành |

### Quy trình debug từng bước

1. Xác định code thực hiện side effect nằm ở **constructor**, **@Bean**, **init callback** hay **startup coordinator**.
2. Kiểm tra class `@Configuration` có đang dùng lite mode; nếu gọi `@Bean` method trực tiếp, object có thể nằm ngoài BeanFactory.
3. Kiểm tra dependency bắt buộc có được inject qua constructor/factory parameters hay không.
4. Xác định callback chạy trước/sau pre-instantiation singleton, context refresh hoặc Boot readiness.
5. Xác định mỗi lần khởi động/context refresh/replica có thể kích hoạt tác vụ bao nhiêu lần.
6. Xem tác vụ có idempotent và transaction boundary đúng không.
7. Đọc exception gốc và kiểm tra resource/connection/shutdown policy.
8. Viết integration test tái hiện một lần startup và một lần startup lặp.

## 16. Bài tập thực hành và đáp án

### Bài 1 — Phân loại công việc

Trong một `@Bean DataClient dataClient()`, có các thao tác: (a) xây builder với URL; (b) kiểm tra URL không rỗng; (c) ghi 1.000 hóa đơn vào DB; (d) truyền dependency sang service khác. Thao tác nào thuộc wiring/local setup?

**Đáp án:** (a), (b) và (d) có thể thuộc construction/configuration khi không thực hiện I/O nghiệp vụ. (c) là workflow có side effect và cần entry point/policy riêng; không nên chạy trong factory.

### Bài 2 — @PostConstruct có phải sau tất cả bean?

Một `@PostConstruct` của `CustomerService` gọi mọi handler trong hệ thống. Có bảo đảm tất cả singleton/lazy beans đã khởi tạo không?

**Đáp án:** Không. Init callback thuộc vòng đời bean hiện tại, không phải barrier sau toàn bộ container; nó còn có thể chạy dưới singleton creation lock. Nên điều phối hậu khởi tạo đúng cơ chế và tránh khối lượng công việc nặng ở đây.

### Bài 3 — SmartInitializingSingleton có thay @Bean không?

Nếu `SeedCustomers` implements `SmartInitializingSingleton`, có cần đăng ký nó thành bean không?

**Đáp án:** Có. Spring chỉ có thể gọi callback cho bean mà BeanFactory quản lý. Trong ví dụ đầy đủ, method `@Bean SeedCustomers seedCustomers(CustomerService customers)` đăng ký coordinator.

### Bài 4 — Dự đoán stdout

Trong chương trình `InitSeparationDemo` ở mục 6, tại sao `rules.initialize` chạy trước `bootstrap=created:seed-001`?

**Đáp án:** `Rules` là dependency của `CustomerService`; Spring hoàn thành init callback của managed `Rules` trong giai đoạn tạo singleton. Sau khi các singleton non-lazy đã pre-instantiate, nó gọi `SeedCustomers.afterSingletonsInstantiated()`, khi ấy use case mới chạy.

### Bài 5 — Chạy nhiều instance

Hai replica cùng seed role `ADMIN` bằng lệnh "nếu không tồn tại thì insert" mà không có unique constraint. Có an toàn không?

**Đáp án:** Không. Hai replica có thể cùng đọc "chưa tồn tại" rồi cùng insert. Cần unique constraint/UPSERT/transaction hoặc phối hợp phân tán thích hợp, tùy yêu cầu.

### Bài 6 — @DependsOn có thay coordinator không?

Bạn cần nạp danh mục A rồi tạo chỉ mục B rồi bật listener C. Chỉ đặt `@DependsOn` trên ba beans đã đủ mô tả retry/idempotency/readiness chưa?

**Đáp án:** Chưa. `@DependsOn` nói về dependency của lifecycle bean, không định nghĩa policy orchestration nghiệp vụ. Dùng coordinator/startup lifecycle phù hợp, test thứ tự và các tình huống lỗi.

### Bài 7 — Khi nào dùng SmartLifecycle?

Một message consumer cần tự start ở giai đoạn phù hợp và tự stop trước khi giải phóng pool. Đặt vòng lặp `while(true)` trong `@PostConstruct` hay dùng `SmartLifecycle`?

**Đáp án:** `SmartLifecycle` hoặc một abstraction managed listener/scheduler phù hợp giúp mô tả start/stop và phase; không khởi chạy vòng lặp vô hạn trong init callback.

### Bài 8 — ContextRefreshedEvent có thể chạy hai lần?

Một listener seed dữ liệu dựa trên `ContextRefreshedEvent`; app có parent-child contexts. Có nên giả định listener chỉ thấy đúng một event rồi không cần idempotency?

**Đáp án:** Không. Event có thể phát theo từng context và khi refresh; listener cần lọc context nếu yêu cầu, đồng thời use case seed phải an toàn khi chạy lặp.

### Bài 9 — Transaction nằm ở đâu?

Một bean tự gọi method `@Transactional` từ constructor. Vì sao không nên dùng cách đó?

**Đáp án:** Constructor chạy trước khi object/proxy hoàn chỉnh; self-invocation trong proxy-based Spring AOP thường bỏ qua proxy. Hãy dùng managed service/proxy làm transaction boundary, gọi từ coordinator ở giai đoạn thích hợp.

### Bài 10 — Thiết kế lại @Bean

Refactor đoạn sau:

```java
@Bean
InventoryService inventoryService(InventoryRepository repository) {
    InventoryService service = new InventoryService(repository);
    service.importCatalog();
    return service;
}
```

**Đáp án định hướng:**

```java
@Bean
InventoryService inventoryService(InventoryRepository repository) {
    return new InventoryService(repository);
}

@Bean
CatalogBootstrap catalogBootstrap(InventoryService inventoryService) {
    return new CatalogBootstrap(inventoryService);
}
```

`CatalogBootstrap` là class coordinator ứng dụng: nó có thể implement `SmartInitializingSingleton` hoặc nhận trigger từ entry point phù hợp, tùy thời điểm nghiệp vụ. Đoạn này là ví dụ cấu trúc, cần định nghĩa `InventoryRepository`, `InventoryService` và `CatalogBootstrap` tương ứng. Điểm quan trọng là factory **không gọi `importCatalog()`**.

## 17. Checklist áp dụng vào dự án

- [ ] Mỗi `@Bean` factory method chủ yếu **tạo/wire instance**, không thực thi workflow nghiệp vụ.
- [ ] Constructor của service không tự gửi email, migrate dữ liệu hay gọi external API không cần thiết.
- [ ] Bean-local validation/preparation được đặt trong constructor hoặc lifecycle callback nhỏ, có hợp đồng rõ.
- [ ] Tác vụ nhiều bean hoặc I/O nặng không bị ép vào `@PostConstruct`.
- [ ] Với startup hook, chọn mốc `SmartInitializingSingleton`, `ContextRefreshedEvent`, `SmartLifecycle` hay Boot runner đúng nhu cầu.
- [ ] Nếu không cần tự động startup task, **không đăng ký** một callback chạy nghiệp vụ không cần thiết.
- [ ] Thứ tự giữa các bước nghiệp vụ được điều phối **tường minh**, không dựa vào thứ tự bean definitions tình cờ.
- [ ] Startup task có chính sách idempotency, transaction, retry/timeout và failure/readiness đúng với business requirements.
- [ ] Với nhiều replica hoặc refresh, đã xử lý trường hợp tác vụ chạy lặp.
- [ ] Các dependency vào `@Bean` dùng method parameters thay vì direct bean-method calls khi dùng lite mode.
- [ ] Test đơn vị có thể gọi business service mà không cần mở Spring context, nếu không phụ thuộc infrastructure.
- [ ] Integration test kiểm tra lifecycle/seed/identity và phát hiện side effects không mong muốn.
- [ ] Tài nguyên hoặc background task có cleanup/stop callbacks tương ứng.
- [ ] Logging/metrics đủ quan sát mà không lộ dữ liệu nhạy cảm.

## Ghi nhớ cuối bài

**Tạo bean, khởi tạo bean và khởi tạo nghiệp vụ là ba trách nhiệm khác nhau.** Spring có nhiều lifecycle extension points vì những giai đoạn này không tương đương:

- `@Bean` và constructor: **tạo object + nối dependency**.
- `@PostConstruct` hoặc `@Bean(initMethod)`: **xác thực/chuẩn bị trạng thái cục bộ của bean**.
- `SmartInitializingSingleton` hoặc `ContextRefreshedEvent`: **giai đoạn hậu khởi tạo để điều phối nếu ứng dụng thực sự cần**.
- `SmartLifecycle`: **start/stop của thành phần chạy theo lifecycle**.
- Use case/service method: **nghiệp vụ thực sự**, được gọi từ trigger có chủ đích.

Không cố "làm hết mọi thứ" trong `@Configuration`. Một Java configuration tốt giúp người đọc biết **bean nào tồn tại và nối với nhau ra sao**, còn một bootstrap coordinator tốt giúp người đọc biết **tác vụ nghiệp vụ nào được chạy, lúc nào, với chính sách lỗi và lặp lại thế nào**.

## Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Customizing the Nature of a Bean](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html): lifecycle callbacks, lưu ý singleton creation lock, `SmartInitializingSingleton`/context refresh event và `SmartLifecycle`.
- [Spring Framework 6.2 — Using the @Bean Annotation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html): factory method parameters và `initMethod`/`destroyMethod`.
- [Spring Framework 6.2 — SmartInitializingSingleton Javadoc](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/beans/factory/SmartInitializingSingleton.html): ý nghĩa callback sau pre-instantiation singleton.
- [Spring Framework 6.2 — ContextRefreshedEvent Javadoc](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/context/event/ContextRefreshedEvent.html): sự kiện ApplicationContext được initialized/refreshed.
- [Spring Framework 6.2 — SmartLifecycle Javadoc](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/context/SmartLifecycle.html): start/stop, auto-startup và lifecycle phases.
- [Spring Framework 6.2 — Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java.html): ranh giới factory configuration.


</details>

- [Quay lại đầu trang](#back-to-top)
