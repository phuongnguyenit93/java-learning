<a id="back-to-top"></a>

# Aspect và Bean

## Menu
- [Khai báo lớp @Aspect và đưa vào Spring container](#aspect-beans-001)
- [Đăng ký aspect bằng @Component hoặc phương thức @Bean](#aspect-beans-002)
- [Tiêm phụ thuộc vào aspect qua Spring](#aspect-beans-003)
- [Đăng ký bean đích và tránh đối tượng tạo bằng new ngoài container](#aspect-beans-004)
- [Phân biệt annotation @Aspect với annotation đăng ký component](#aspect-beans-005)
- [Aspect như bean hạ tầng và giới hạn áp dụng advice lên chính aspect](#aspect-beans-006)
- [Vòng đời của aspect và vòng đời của bean đích](#aspect-beans-007)
- [Scope của bean Spring khác mô hình khởi tạo aspect như thế nào](#aspect-beans-008)
- [Mô hình singleton, perthis và pertarget được Spring hỗ trợ](#aspect-beans-009)
- [Kiểm tra số lượng instance aspect và trạng thái được chia sẻ](#aspect-beans-010)

## <a id="aspect-beans-001">Khai báo lớp @Aspect và đưa vào Spring container</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo lớp @Aspect và đưa vào Spring container

## 1. Kết quả cần đạt

Trong Spring AOP, khai báo một lớp với `@Aspect` mới **mô tả** rằng lớp đó có thể chứa pointcut và advice; để Spring AOP thực sự sử dụng lớp ấy, Spring phải **biết đến instance của aspect như một bean** trong `ApplicationContext` có bật hỗ trợ `@AspectJ`.

Sau bài học, bạn cần làm được ba việc độc lập:

1. Viết một lớp `@Aspect` có advice hợp lệ.
2. Đăng ký lớp đó thành **Spring bean**, thường bằng `@Component` và component scanning, hoặc bằng `@Bean` trong `@Configuration`.
3. Bật hỗ trợ Spring AOP và kiểm tra lời gọi phương thức trên **bean đích** có đi qua proxy để kích hoạt advice.

Đây là bài về **cách Spring phát hiện, tạo và quản lý aspect bean**. Chỉ sử dụng lượng kiến thức pointcut/advice cần để chứng minh việc đăng ký hoạt động; phần lý thuyết Aspect tổng quát không thuộc phạm vi bài này.

## 2. `@Aspect` là gì đối với Spring container?

Hãy phân biệt ba vai trò thường gặp:

| Thành phần | Annotation / cách khai báo | Điều Spring làm |
| --- | --- | --- |
| **Aspect class** | `@Aspect` từ `org.aspectj.lang.annotation.Aspect` | Khi lớp là bean trong context có hỗ trợ `@AspectJ`, Spring đọc các pointcut/advice của nó để tạo các advisor |
| **Aspect bean** | `@Component` + được scan, hoặc khai báo `@Bean`, XML, bean definition | Container tạo/cung cấp instance aspect, thực hiện dependency injection và quản lý lifecycle tương ứng |
| **Target bean** | Ví dụ `@Service`, `@Repository`, `@Bean` | Nếu method khớp advisor và có thể proxy, Spring trả về AOP proxy cho caller để advice có cơ hội chạy |

Điểm quan trọng:

- `@Aspect` **không phải** stereotype của Spring như `@Component`; chỉ đánh dấu `@Aspect` **không đủ** để component scanner đưa lớp vào container.
- `@Component` đưa lớp vào diện được scan (nếu package nằm trong phạm vi scan); bản thân `@Component` **không bật** AOP và không làm Spring hiểu lớp là aspect nếu thiếu `@Aspect`.
- `@EnableAspectJAutoProxy` bật bộ phận Spring AOP nhận diện aspect beans, tạo advisor và auto-proxy những target beans phù hợp. Nó **không tự scan** mọi lớp nằm ngoài bean definitions.
- Với Spring Framework thông thường, `org.aspectj:aspectjweaver` phải có trong classpath cho xử lý biểu thức `@AspectJ`. Tên thư viện không đồng nghĩa với việc ứng dụng đang chạy load-time weaving: cơ chế minh họa ở đây vẫn là **Spring AOP proxy**.

Luồng hoạt động:

```text
Lớp @Aspect
    |
    +-- @Component + component scan --------+
    |                                       |
    +-- @Bean / khai báo bean khác ---------+--> aspect bean trong ApplicationContext
                                                  |
                             @EnableAspectJAutoProxy / Boot AOP auto-configuration
                                                  |
                                             nhận diện advice
                                                  |
                                        tạo advisor và xét target beans
                                                  |
Caller --> target Spring bean proxy --> advice --> target method
```

Nếu thiếu một trong các bước **đăng ký aspect** hoặc **bật hỗ trợ AOP**, việc annotation `@Before` nằm trong mã nguồn không tự khiến lời gọi nghiệp vụ bị chặn.

## 3. Cách 1: `@Aspect` kết hợp `@Component`

Đây là cách thông dụng khi dự án tổ chức các aspect như các lớp thuộc package được Spring quét:

```java
package example;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {

    @Before("execution(* example.OrderService.submit(..))")
    public void beforeSubmit() {
        System.out.println("[audit] before submit");
    }
}
```

- `@Aspect`: cho phép Spring nhận diện điểm khai báo advice khi instance đã là bean.
- `@Component`: cho phép component scanner đăng ký `AuditAspect` như một Spring bean.
- `@Before(...)`: phương thức advice chạy trước lời gọi `OrderService.submit(..)` đi qua AOP proxy và khớp pointcut.

`@Aspect` và `@Component` làm hai nhiệm vụ khác nhau. Đừng giả định `@Aspect` tự tìm được lớp, ngay cả khi package được scan.

Ví dụ ở đây dùng `@Component`; một custom stereotype được meta-annotate đúng cách bằng `@Component` cũng có thể làm nhiệm vụ đăng ký.

## 4. Ví dụ chạy được với Spring Framework thuần và Java 17+

Ví dụ này trình bày toàn bộ chuỗi **khai báo aspect → component scan → dependency injection → kích hoạt proxy → lời gọi nhận advice**. Cấu hình sử dụng Spring Framework 6.2.x, và không yêu cầu Spring Boot.

### 4.1. Cấu trúc dự án

```text
spring-aspect-bean-demo/
├── pom.xml
└── src/main/java/example/
    ├── AppConfig.java
    ├── AuditSink.java
    ├── AuditAspect.java
    ├── OrderService.java
    └── Main.java
```

### 4.2. `pom.xml`

Dùng cùng phiên bản Spring Framework cho `spring-context` và `spring-aop`; ví dụ chọn 6.2.19. Đối với ứng dụng thực tế, nên để Spring BOM hoặc Spring Boot quản lý phiên bản thay vì ghim tùy tiện từng module.

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-aspect-bean-demo</artifactId>
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

### 4.3. Spring bean được inject vào aspect

**`AuditSink.java`**

```java
package example;

import org.springframework.stereotype.Component;

@Component
public class AuditSink {
    public void record(String event) {
        System.out.println("[audit] " + event);
    }
}
```

**`AuditAspect.java`**

```java
package example;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {
    private final AuditSink sink;

    public AuditAspect(AuditSink sink) {
        this.sink = sink;
    }

    @Before("execution(* example.OrderService.submit(..))")
    public void beforeSubmit() {
        sink.record("before submit");
    }
}
```

**`OrderService.java`**

```java
package example;

import org.springframework.stereotype.Service;

@Service
public class OrderService {
    public void submit(String orderId) {
        System.out.println("order submitted: " + orderId);
    }
}
```

**`AppConfig.java`**

```java
package example;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "example")
@EnableAspectJAutoProxy
public class AppConfig {
}
```

**`Main.java`**

```java
package example;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            AuditAspect aspect = context.getBean(AuditAspect.class);
            OrderService service = context.getBean(OrderService.class);

            System.out.println("aspect bean exists: " + (aspect != null));
            System.out.println("service is proxy: " + AopUtils.isAopProxy(service));
            System.out.println("aspect is proxy: " + AopUtils.isAopProxy(aspect));

            service.submit("A-100");
        }
    }
}
```

Chạy trong thư mục có `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.Main
```

Các dòng do chương trình trên in ra:

```text
aspect bean exists: true
service is proxy: true
aspect is proxy: false
[audit] before submit
order submitted: A-100
```

Diễn giải chính xác:

1. `AnnotationConfigApplicationContext` tải `AppConfig`.
2. `@ComponentScan` tìm các lớp có Spring stereotype trong package `example`. Nhờ `@Component`, `AuditAspect` trở thành bean; `AuditSink` và `OrderService` cũng thành bean.
3. Spring inject `AuditSink` vào constructor của `AuditAspect`. Vì aspect là bean nên nó có thể dùng constructor injection như các bean Spring khác.
4. `@EnableAspectJAutoProxy` kích hoạt Spring AOP auto-proxy, nhận diện aspect có `@Aspect` và advice khớp `OrderService.submit(..)`.
5. Spring cung cấp proxy cho `OrderService`. Khi client gọi `service.submit("A-100")`, advice chạy trước rồi mới vào method nghiệp vụ.
6. `aspect is proxy: false` là kết quả **đúng**: Spring AOP loại các `@Aspect` bean khỏi việc auto-proxy bởi aspect khác; aspect bean **không cần phải là AOP proxy** để advice do nó khai báo chạy.

Đối với ứng dụng có log framework, dòng log của framework có thể xuất hiện ngoài những dòng ví dụ; các dòng trên là kết quả từ các `System.out.println` trong mã.

## 5. Cách 2: Khai báo `@Bean` trong `@Configuration`

Không phải lúc nào lớp aspect cũng phù hợp để đánh dấu `@Component`. Khi cần cấu hình tường minh, tạo aspect bằng factory method `@Bean` rất hữu ích.

**Cách chuyển ví dụ mục 4 sang `@Bean`:** bỏ riêng annotation `@Component` khỏi `AuditAspect` (vẫn giữ `@Aspect` và constructor), sau đó thay toàn bộ `AppConfig.java` bằng:

```java
package example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "example")
@EnableAspectJAutoProxy
public class AppConfig {

    @Bean
    public AuditAspect auditAspect(AuditSink sink) {
        return new AuditAspect(sink);
    }
}
```

Phần còn lại trong mục 4 giữ nguyên; chạy lệnh Maven tương tự và nhận các dòng kết quả tương đương.

Giải thích:

- `@Bean` khiến **giá trị trả về từ `auditAspect(...)`** được Spring đăng ký và quản lý.
- Tham số `AuditSink sink` được resolve từ container rồi truyền vào constructor aspect.
- Không cần đặt `@Component` trên `AuditAspect`: Spring nhận diện `@Aspect` trên **class của bean** đã đăng ký.
- Tránh vừa để `@Component` quét `AuditAspect`, vừa tạo thêm `@Bean AuditAspect` mà không chủ ý; điều này có thể tạo **hai aspect beans**, dẫn tới advice chạy trùng và ambiguity khi inject theo kiểu. Nếu có nhiều bean cùng kiểu thì `getBean(AuditAspect.class)` có thể ném `NoUniqueBeanDefinitionException`.

**Cần phân biệt:** `new AuditAspect(sink)` bên trong một `@Bean` factory method là hợp lệ vì **instance trả về được Spring đăng ký**. Ngược lại, tự `new AuditAspect(...)` trong code ứng dụng mà không đưa instance đó vào container **không đăng ký** aspect với Spring AOP.

### 5.1. Khai báo aspect bean bằng XML

Trong ứng dụng Spring XML cũ, một class chỉ có `@Aspect` có thể được khai báo bằng bean definition:

```xml
<!-- Đoạn minh họa nằm trong phần tử <beans> có khai báo namespace aop. -->
<bean id="auditAspect" class="example.AuditAspect">
    <constructor-arg ref="auditSink"/>
</bean>

<aop:aspectj-autoproxy/>
```

Ở cách này, `auditSink` cũng phải được khai báo là bean. `<aop:aspectj-autoproxy/>` là cách bật Spring AOP proxy tương đương với Java configuration; XML snippet trên không phải một file XML độc lập. Phần lớn ứng dụng mới dùng Java configuration hoặc Spring Boot.

## 6. Nếu sử dụng Spring Boot

Với Spring Boot, `@SpringBootApplication` kết hợp component scanning từ package của application class; nếu lớp `@Aspect` nằm trong phạm vi scan và có `@Component`, nó được đăng ký giống một bean bình thường.

Ví dụ đoạn khai báo aspect trong ứng dụng Boot:

```java
package example.app.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RequestAuditAspect {
    @Before("execution(* example.app.service.OrderService.submit(..))")
    public void beforeOrder() {
        System.out.println("[audit] submitting order");
    }
}
```

**Đây là một lớp minh họa để chèn vào ứng dụng Boot hiện có**, giả định có service `example.app.service.OrderService` với phương thức `submit`. Khi chạy thật cần `@SpringBootApplication` nằm trong package cha `example.app` hoặc phải cấu hình `scanBasePackages` thích hợp.

Về dependency:

- **Spring Boot 3.x**: thông thường dùng `org.springframework.boot:spring-boot-starter-aop`.
- **Spring Boot 4.x**: starter này đã được đổi tên thành `org.springframework.boot:spring-boot-starter-aspectj`.
- Dùng starter/BOM phù hợp để đưa `spring-aop` và `aspectjweaver` vào classpath. Không tự chọn hai phiên bản xung đột với Spring Boot BOM.

Khi AspectJ có trên classpath, Spring Boot mặc định có AOP auto-configuration và thường **không cần** tự thêm `@EnableAspectJAutoProxy`. Nếu dự án tắt auto-configuration liên quan hoặc tùy chỉnh `spring.aop.auto=false`, phải rà soát cấu hình trước khi kỳ vọng advice chạy. Mặc định Boot thường chọn **class-based proxies (CGLIB)**; có thể dùng `spring.aop.proxy-target-class=false` để ưu tiên interface-based proxies khi phù hợp.

**Không nhầm hai điều kiện:** Spring Boot tự bật cơ chế AOP không đồng nghĩa nó tự đăng ký mọi class chỉ có `@Aspect`; lớp đó vẫn cần vào container qua `@Component`, `@Bean` hoặc cách đăng ký bean hợp lệ.

## 7. Quản lý aspect bean: phạm vi và đặc điểm

### 7.1. Aspect là bean, nhưng có vai trò đặc biệt

Spring có thể inject dependency vào aspect, áp dụng các callback/lifecycle tương ứng với bean definition và quản lý singleton như bean thông thường. Tuy nhiên, đối với Spring AOP:

- Aspect bean là **nguồn khai báo advisor**, còn proxy thông thường được tạo cho **target bean** phù hợp.
- Spring loại chính các lớp `@Aspect` khỏi việc trở thành target auto-proxy của các aspect khác. Do đó lời gọi lên instance aspect không nên được kỳ vọng kích hoạt advice do aspect khác khai báo.
- Theo mặc định, Spring dùng mô hình **một aspect instance trong một ApplicationContext** (singleton aspect). Các mô hình instantiation đặc biệt như `perthis` và `pertarget` thuộc chủ đề nâng cao, không cần cho bước khai báo cơ bản.

### 7.2. Giữ state an toàn trong aspect

Một singleton aspect thường phục vụ nhiều lời gọi đồng thời từ nhiều bean/threads. Vì vậy:

- Ưu tiên aspect **không giữ mutable state theo từng request** trong field.
- Với thông tin theo từng lời gọi, sử dụng biến cục bộ trong advice, tham số được Spring/AspectJ bind vào advice hoặc abstraction chuyên dụng phù hợp với ngữ cảnh.
- Nếu cần ghi log/sự kiện, inject một bean có trách nhiệm rõ ràng như `AuditSink`, thay vì tích lũy dữ liệu request trong field mutable không đồng bộ.

Ví dụ **không nên** trong singleton aspect:

```java
// Minh họa sai về thiết kế khi chạy đa luồng; không phải chương trình độc lập.
private String currentOrderId; // các request có thể ghi đè lẫn nhau
```

### 7.3. Nhiều ApplicationContext

Một aspect được đăng ký trong một context không có nghĩa nó sẽ advice mọi object trong mọi context của ứng dụng. Cơ chế auto-proxy và phạm vi bean phụ thuộc context nơi nó được cấu hình. Với ứng dụng có context cha/con hoặc nhiều context riêng, hãy xác định **aspect bean** và **target bean** thuộc context nào, proxy creator nào đang vận hành và tham chiếu client nhận được đến từ đâu.

## 8. Những lỗi thường gặp khi khai báo `@Aspect`

| Hiện tượng | Nguyên nhân phổ biến | Cách kiểm tra / sửa |
| --- | --- | --- |
| Lớp có `@Aspect` nhưng advice không chạy | Không có `@Component`/`@Bean`, nên lớp chưa vào context | Tìm bean type `AuditAspect` bằng `context.getBeanNamesForType(...)`; đăng ký rõ ràng |
| Đã có `@Component` nhưng Spring không tìm thấy aspect | Lớp nằm ngoài `@ComponentScan` | Kiểm tra package gốc của `@SpringBootApplication` hoặc `@ComponentScan` |
| Aspect bean tồn tại nhưng target method không nhận advice | Chưa bật Spring AOP hoặc thiếu thư viện, pointcut không khớp, lời gọi bỏ qua proxy | Kiểm tra `@EnableAspectJAutoProxy`/Boot auto-config, `aspectjweaver`, `AopUtils.isAopProxy(target)` |
| Advice chạy hai lần bất ngờ | Hai aspect beans giống nhau cùng được đăng ký bằng `@Component` và `@Bean`, hoặc hai aspect độc lập cùng match | In danh sách bean theo type; kiểm tra cấu hình đăng ký trùng |
| `NoSuchBeanDefinitionException` với aspect | Bean chưa được đăng ký hoặc nằm ở context khác | Kiểm tra điều kiện tạo bean, package scan, profile, context thực tế |
| `NoUniqueBeanDefinitionException` khi inject aspect | Có nhiều aspect beans cùng type | Loại bỏ khai báo trùng hoặc chỉ định bean mong muốn bằng `@Qualifier` |
| `AopUtils.isAopProxy(aspect)` trả về `false` | **Hành vi bình thường**: class `@Aspect` không phải target auto-proxy | Kiểm tra `AopUtils.isAopProxy(targetService)` và kết quả chạy advice |
| Aspect có dependency `null` hoặc thiếu dependency | Tạo object aspect ngoài Spring, không được DI; hoặc bean wiring có lỗi | Để container tạo/inject aspect thông qua `@Component` hoặc `@Bean` |
| Advice không chạy khi service tự gọi `this.inner()` | Self-invocation không đi qua proxy | Gọi qua bean/proxy hoặc tách xử lý sang bean khác |

### Kiểm tra trực tiếp trong context

Đoạn Java sau minh họa cách kiểm tra trong một phương thức có sẵn `ApplicationContext context`:

```java
// Đoạn minh họa, yêu cầu các bean/type tương ứng đã khai báo.
String[] names = context.getBeanNamesForType(AuditAspect.class);
System.out.println(java.util.Arrays.toString(names));

OrderService target = context.getBean(OrderService.class);
System.out.println(org.springframework.aop.support.AopUtils.isAopProxy(target));
```

Với ví dụ mục 4, danh sách thường chứa bean tên `auditAspect`; target có proxy vì pointcut khớp. Kiểm tra `context.containsBean("auditAspect")` chỉ cho biết một tên bean có được đăng ký hay không, không thay cho việc xác minh advice chạy.

## 9. Các tình huống đối chiếu

| Tình huống | `@Aspect` trở thành nguồn advice cho Spring AOP? | Vì sao |
| --- | --- | --- |
| `@Aspect @Component`, package được scan, hỗ trợ AOP đã bật | **Có** | Được đăng ký thành Spring bean và Spring nhận diện metadata aspect |
| Chỉ có `@Aspect`, dù package được scan, không có khai báo bean nào khác | **Không** | `@Aspect` tự nó không phải dấu hiệu component |
| Class `@Aspect` không có `@Component`, nhưng được `@Bean` trả về | **Có** | Instance đã được đăng ký qua factory method |
| `@Component` có method `@Before` nhưng không có `@Aspect` | **Không theo mô hình `@AspectJ` này** | Spring không nhận lớp là `@AspectJ` aspect |
| `@Aspect @Component` nhưng AOP auto-proxy không được bật | **Không tự động có Spring AOP advice** | Có bean nhưng thiếu bước xử lý aspect/proxy |
| Tạo `new MyAspect()` ở ngoài Spring | **Không** | Instance không thuộc tập aspect beans mà proxy creator đọc |
| Aspect bean hợp lệ, target là object tự `new` ngoài container | Aspect **đã đăng ký**, nhưng **target đó không được advice bằng Spring proxy** | Spring AOP chỉ can thiệp lời gọi qua proxy của managed bean |

**Tự kiểm tra:** Nếu đổi `AuditAspect` ở ví dụ chạy được thành chỉ có `@Aspect` và không khai báo `@Bean`, điều gì xảy ra? Spring sẽ không đăng ký `AuditAspect` qua component scan, nên `context.getBean(AuditAspect.class)` không tìm được bean; đoạn chương trình sẽ lỗi tại bước lấy bean và không đến bước gọi `submit`. Nếu bỏ dòng lấy aspect rồi vẫn gọi service, `OrderService` sẽ không nhận advice của lớp aspect chưa đăng ký.

## 10. Nguồn tham khảo Spring chính thức

- [Spring Framework — Declaring an Aspect](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/at-aspectj.html): `@Aspect`, đăng ký bằng `@Bean` hoặc component scan, lưu ý không auto-proxy aspect bean.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): `@EnableAspectJAutoProxy`, thư viện AspectJ trên classpath và quá trình tạo proxy.
- [Spring Framework — Aspect Instantiation Models](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/instantiation-models.html): mặc định singleton, các mô hình nâng cao.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): lý do advice cần lời gọi qua proxy và giới hạn self-invocation.
- [Spring Boot — Aspect-Oriented Programming](https://docs.spring.io/spring-boot/reference/features/aop.html): auto-configuration Spring AOP và lựa chọn proxy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-beans-002">Đăng ký aspect bằng @Component hoặc phương thức @Bean</a>

<details>
<summary>Xem chi tiết</summary>

# Đăng ký aspect bằng @Component hoặc phương thức @Bean trong Spring

## 1. Vấn đề cần giải quyết

Trong Spring AOP với kiểu khai báo `@AspectJ`, một class có `@Aspect` **chưa tự động trở thành Spring bean**. Để Spring đọc các advice và dùng chúng khi tạo proxy cho bean đích, ta phải **đăng ký instance của class aspect vào đúng Spring `ApplicationContext`**. Hai cách Java configuration thường dùng là:

1. **`@Aspect` + `@Component`**, cho phép `@ComponentScan` tìm và đăng ký aspect theo convention.
2. **`@Aspect` + phương thức `@Bean`**, cho phép cấu hình Spring chủ động tạo, đặt tên và cung cấp dependency cho aspect.

Cả hai đều phục vụ **cùng một hạ tầng Spring AOP**. Chọn một cách phù hợp với cách quản lý bean của ứng dụng; chỉ cần một instance aspect được đăng ký cho một cấu hình mẫu, tránh tạo hai instance có cùng advice rồi khiến advice chạy lặp.

Ranh giới bài: tập trung vào **đăng ký bean aspect trong Spring**, tương tác với component scan, cấu hình và dependency injection, kiểm chứng proxy/advice. Không lặp lại AOP tổng quát hoặc chuyển sang cơ chế weaving của AspectJ.

## 2. Phân biệt ba vai trò dễ nhầm

| Thành phần | Chức năng chính trong Spring | Có đủ để advice chạy không? |
| --- | --- | --- |
| `@Aspect` trên class | Khai báo class chứa pointcut/advice theo kiểu `@AspectJ`. | **Không**. Không phải stereotype mà component scan mặc định xem là ứng viên bean. |
| `@Component` trên class aspect | Đánh dấu class là một ứng viên Spring component để scan và tạo bean. | **Chưa đủ** nếu không có `@Aspect`, hạ tầng auto-proxy hoặc pointcut/lời gọi phù hợp. |
| `@Bean` trên method của `@Configuration` | Đăng ký object do factory method trả về làm Spring bean, không cần class của object có `@Component`. | **Chưa đủ** nếu object không thuộc class `@Aspect` hoặc chưa bật @AspectJ support. |
| `@EnableAspectJAutoProxy` | Trong Spring Framework Java config, kích hoạt nhận diện advisor của aspect bean và tạo proxy cho target khi match. | Cần cấu hình đúng context, AspectJ Weaver trên classpath và target/lời gọi phù hợp. |
| `@Service`, `@Repository`, `@Controller` trên target | Đưa nghiệp vụ/adapter vào container qua component scan tương ứng. | Target phải được Spring quản lý và lời gọi cần đi qua proxy để được intercept. |

**Hai chiều kiểm tra tách biệt:**

- **Đã có aspect bean chưa?** `context.getBeansOfType(AuditAspect.class)` hoặc danh sách bean names chứng minh container đã biết aspect.
- **Hạ tầng Spring AOP có sử dụng aspect đó chưa?** Một target có thể được proxy và lời gọi method khớp advice chạy. Cần quan sát hành vi, không suy từ việc tạo bean một cách đơn độc.

Ngay cả khi aspect là Spring bean, method trên target vẫn có thể không được advice vì không match pointcut, do lời gọi nội bộ, do target không phải Spring bean hoặc do cấu hình AOP chưa bật.

## 3. Cách 1: `@Aspect` kết hợp `@Component`

Dùng khi aspect là một lớp do ứng dụng sở hữu, phù hợp cơ chế component scanning vốn đã dùng để đăng ký `@Service` và các Spring components khác.

```java
package com.example.aspectdemo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {
    @Before("execution(public * com.example.aspectdemo.OrderService.place(..))")
    public void beforePlace() {
        System.out.println("audit:place");
    }
}
```

Điều kiện quan trọng:

1. `@Aspect` làm Spring nhận diện metadata pointcut/advice trên class.
2. `@Component` cho phép **component scan mặc định** đăng ký class đó như một bean.
3. Cần bật `@ComponentScan` cho đúng package hoặc, với Spring Boot, đặt class dưới package được `@SpringBootApplication` quét.
4. `@EnableAspectJAutoProxy` trong Spring Framework thuần (hoặc auto-configuration tương ứng trong Boot) giúp chuyển metadata từ aspect bean thành advisor của Spring AOP.
5. Target phải là Spring bean, điểm cắt đúng và caller phải đi qua Spring proxy.

### Phạm vi quét là điều kiện thực tế

Ví dụ:

```java
@Configuration
@EnableAspectJAutoProxy
@ComponentScan(basePackages = "com.example.aspectdemo")
class AppConfig {
}
```

Giả sử `AuditAspect` thuộc `com.example.aspectdemo`, config trên cho phép tìm aspect. Nếu chỉ quét `com.example.api`, aspect ở `com.example.aspectdemo` không tự xuất hiện. Khi chạy Spring Boot, package chứa class `@SpringBootApplication` quyết định vùng scan mặc định; aspect nằm ngoài phạm vi đó cần khai báo scan/import rõ ràng.

**Lưu ý:** `@Aspect` **không** là meta-annotation của `@Component`; một class chỉ có `@Aspect` không được component scan đăng ký theo mặc định. Có thể định nghĩa stereotype tùy biến dựa trên `@Component`, nhưng không cần làm thế cho ví dụ thông thường.

### Dependency injection khi dùng `@Component`

Aspect là Spring bean nên có thể nhận dependency bằng **constructor injection** giống các component khác:

```java
@Aspect
@Component
public class AuditAspect {
    private final AuditSink sink;

    public AuditAspect(AuditSink sink) {
        this.sink = sink;
    }

    @Before("execution(public * com.example.aspectdemo.OrderService.place(..))")
    public void beforePlace() {
        sink.record("place");
    }
}
```

Ở đây `AuditSink` cũng phải được Spring đăng ký. Spring giải constructor khi tạo bean aspect. Không cần tạo aspect bằng `new` trong code nghiệp vụ. Constructor injection giúp dependency rõ ràng, dễ test và tránh field injection mơ hồ.

## 4. Cách 2: khai báo aspect bằng phương thức `@Bean`

Dùng khi muốn **quản lý việc tạo aspect một cách tường minh**: cần constructor phức tạp, dependency cấu hình, chọn aspect theo profile/condition, hoặc class aspect không muốn/không thể gắn `@Component`.

```java
package com.example.aspectdemo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    private final AuditSink sink;

    public AuditAspect(AuditSink sink) {
        this.sink = sink;
    }

    @Before("execution(public * com.example.aspectdemo.OrderService.place(..))")
    public void beforePlace() {
        sink.record("place");
    }
}
```

Class trên **cố ý không gắn `@Component`**. Trong Java configuration:

```java
package com.example.aspectdemo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
    @Bean
    public AuditAspect auditAspect(AuditSink sink) {
        return new AuditAspect(sink);
    }
}
```

Đây là đoạn **cấu hình minh họa**, giả sử `AuditSink` và target service đã được đăng ký ở chính context này. Spring sẽ inject `AuditSink` vào **tham số factory method** khi gọi `auditAspect(...)`. Object trả về trở thành Spring bean có tên mặc định là `auditAspect`; Spring đọc `@Aspect` trên class của object để phát hiện các advisor.

Chú ý: phương thức `@Bean` phải được Spring xử lý trong context, ví dụ nằm trong một `@Configuration` đã được đăng ký. Việc gọi `new AuditAspect(sink)` **bên trong factory method `@Bean`** là hợp lệ vì **giá trị trả về được container đăng ký**. Ngược lại, tự chạy `new AuditAspect(sink)` ngoài container rồi không đăng ký bean sẽ không khiến Spring AOP tự nhận diện aspect đó.

### Dependency của `@Bean` cũng phải là Spring bean

Hai lựa chọn hợp lệ:

- Component scan tạo `AuditSink` và `OrderService`, còn `@Bean` chỉ tạo `AuditAspect`.
- Không dùng component scan; khai báo cả sink, service và aspect bằng các `@Bean` method riêng.

Ví dụ cách thứ hai, trong một `@Configuration`:

```java
@Bean
AuditSink auditSink() {
    return new AuditSink();
}

@Bean
OrderService orderService() {
    return new OrderService();
}

@Bean
AuditAspect auditAspect(AuditSink sink) {
    return new AuditAspect(sink);
}
```

Khi Spring tạo `AuditAspect`, nó lấy `AuditSink` bean để truyền vào tham số. Với `@Configuration` đầy đủ, các factory method có thể được container quản lý về scope và dependency như bean definitions khác; ưu tiên method-parameter injection để mối phụ thuộc minh bạch.

### Scope, bean name, lifecycle

Cả `@Component` và `@Bean` mặc định tạo **singleton theo bean definition** của Spring. Tên bean mặc định thường là tên class với chữ đầu thường cho component (`auditAspect`), và tên phương thức cho `@Bean` (`auditAspect`). Có thể chỉ định tên rõ:

```java
@Component("businessAuditAspect")
@Aspect
class AuditAspect {
    // ...
}
```

hoặc:

```java
@Bean("businessAuditAspect")
AuditAspect auditAspect(AuditSink sink) {
    return new AuditAspect(sink);
}
```

Hai ví dụ là **các phương án độc lập**; không áp dụng đồng thời để tạo hai bean có tên giống nhau. Tên bean tác động đến việc tra cứu và một số lựa chọn pointcut theo bean name, nhưng không làm `@Aspect` được chấp nhận nếu chưa được container tạo. Nếu aspect giữ trạng thái mutable, singleton có thể gây race condition trong ứng dụng đa luồng; trong đa số trường hợp aspect nên **stateless** và delegate lưu trữ/audit sang service riêng. Đừng đổi scope sang prototype tùy tiện: Spring AOP có cách xử lý aspect instantiation đặc thù, cần kiểm tra rõ behavior.

## 5. Demo đầy đủ, có thể chạy: đăng ký bằng `@Component` rồi đổi sang `@Bean`

Ví dụ dùng **Spring Framework 6.2.15**, **AspectJ Weaver 1.9.24**, Gradle và **Java 17+**. Không dùng Spring Boot để thấy rõ vai trò của `@EnableAspectJAutoProxy`, `@ComponentScan` và `@Bean`.

Mục tiêu test: với đúng **một** aspect bean, một method target được chặn và method khác không được chặn. Hai cách đăng ký cho **cùng một kết quả** khi các bean khác và pointcut giữ nguyên.

### 5.1. Cây thư mục

```text
aspect-registration-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/com/example/aspectdemo/
    ├── Application.java
    ├── AppConfig.java
    ├── OrderService.java
    ├── AuditSink.java
    └── AuditAspect.java
```

**`settings.gradle`**

```gradle
rootProject.name = 'aspect-registration-demo'
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
    implementation 'org.springframework:spring-context:6.2.15'
    implementation 'org.springframework:spring-aop:6.2.15'
    implementation 'org.aspectj:aspectjweaver:1.9.24'
}

application {
    mainClass = 'com.example.aspectdemo.Application'
}
```

Spring Framework 6.x yêu cầu Java 17 trở lên. Những dependency được khóa để ví dụ độc lập và dễ tái lập; dự án Spring Boot thực tế nên dựa trên dependency management/BOM của Boot để chọn phiên bản tương thích.

### 5.2. Target service

**`OrderService.java`**

```java
package com.example.aspectdemo;

import org.springframework.stereotype.Service;

@Service
public class OrderService {
    public String place(String orderId) {
        return "placed:" + orderId;
    }

    public String status() {
        return "status:ok";
    }
}
```

Service là Spring bean do component scan tạo. Method `place` khớp pointcut; method `status` không khớp. Hai method đều public để dễ chứng minh interception qua class proxy.

### 5.3. Dependency của aspect

**`AuditSink.java`**

```java
package com.example.aspectdemo;

import org.springframework.stereotype.Component;

@Component
public class AuditSink {
    public void record(String operation) {
        System.out.println("audit:" + operation);
    }
}
```

Tách sink ra khỏi aspect cho thấy một Spring-managed aspect cũng có dependency như bean bình thường.

### 5.4. Aspect đăng ký qua `@Component`

**`AuditAspect.java`**

```java
package com.example.aspectdemo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {
    private final AuditSink sink;

    public AuditAspect(AuditSink sink) {
        this.sink = sink;
    }

    @Before("execution(public * com.example.aspectdemo.OrderService.place(..))")
    public void beforePlace() {
        sink.record("place");
    }
}
```

Spring quét được class này nhờ `@Component`. Spring AOP phân tích advice nhờ `@Aspect`. Không cần tạo `AuditAspect` bằng một factory method khác.

**`AppConfig.java`**

```java
package com.example.aspectdemo;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = OrderService.class)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
}
```

`basePackageClasses = OrderService.class` làm Spring quét **package chứa `OrderService`**, cũng là package của `AuditSink` và `AuditAspect` trong demo. `@EnableAspectJAutoProxy` bật xử lý aspect bean thành các Spring AOP advisors; `proxyTargetClass=true` cho phép tra `OrderService` qua kiểu concrete class. Trong demo đơn giản này, lựa chọn subclass proxy giữ kết quả dễ dự đoán.

### 5.5. Bootstrap, kiểm tra số aspect và hiệu lực proxy

**`Application.java`**

```java
package com.example.aspectdemo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Application {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService service = context.getBean(OrderService.class);

            System.out.println(
                "aspectBeans=" + context.getBeansOfType(AuditAspect.class).size()
            );
            System.out.println(
                "serviceProxied=" + AopUtils.isAopProxy(service)
            );
            System.out.println(service.place("INV-1"));
            System.out.println(service.status());
        }
    }
}
```

Chạy tại thư mục project sau khi có Gradle và JDK 17+:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper, dùng `./gradlew run --quiet` (Linux/macOS) hoặc `gradlew.bat run --quiet` (Windows). Những dòng do ứng dụng in ra **theo thứ tự chính xác** là:

```text
aspectBeans=1
serviceProxied=true
audit:place
placed:INV-1
status:ok
```

Giải thích:

- `aspectBeans=1`: context nhận diện **một** bean `AuditAspect` từ component scan.
- `serviceProxied=true`: target `OrderService` khớp advisor của aspect nên được Spring trả về dưới dạng AOP proxy.
- `audit:place` xuất hiện **trước** `placed:INV-1` vì advice `@Before` chạy khi gọi `place` qua proxy.
- `status:ok` không có thêm dòng `audit` vì pointcut chỉ match `place(..)`.
- `System.out` của demo không bao gồm log framework hay Gradle phát sinh riêng bởi môi trường.

**Điểm chứng minh:** Aspect chạy vì **đã được đăng ký thành Spring bean**, hạ tầng AOP được bật và caller gọi một target method phù hợp **qua Spring proxy**.

### 5.6. Chuyển cùng demo sang cách `@Bean`

Giữ nguyên `Application.java`, `OrderService.java`, `AuditSink.java`, `build.gradle` và `settings.gradle`. Thay **toàn bộ** hai tệp sau; đây là **biến thể thay thế**, không chạy đồng thời với biến thể `@Component`.

**`AuditAspect.java` — bỏ `@Component`, vẫn giữ `@Aspect`:**

```java
package com.example.aspectdemo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    private final AuditSink sink;

    public AuditAspect(AuditSink sink) {
        this.sink = sink;
    }

    @Before("execution(public * com.example.aspectdemo.OrderService.place(..))")
    public void beforePlace() {
        sink.record("place");
    }
}
```

**`AppConfig.java` — thêm một factory method `@Bean`:**

```java
package com.example.aspectdemo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = OrderService.class)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
    @Bean
    public AuditAspect auditAspect(AuditSink sink) {
        return new AuditAspect(sink);
    }
}
```

`@ComponentScan` vẫn tạo `OrderService` và `AuditSink`; **không tạo `AuditAspect` qua scan** vì class aspect không có `@Component`. Ngược lại, `@Bean auditAspect(...)` tạo chính xác một aspect bean, được container quản lý và inject `AuditSink`. Câu lệnh `new AuditAspect(sink)` ở trong bean factory method hoàn toàn đúng.

Chạy lại:

```bash
gradle run --quiet
```

**Stdout do chương trình in không thay đổi**:

```text
aspectBeans=1
serviceProxied=true
audit:place
placed:INV-1
status:ok
```

Như vậy, hai phương án chỉ khác **cơ chế đăng ký aspect bean**. Điểm cắt, advice, dependency, loại proxy và lời gọi nghiệp vụ vẫn giữ nguyên nên hiệu ứng quan sát giống nhau.

## 6. So sánh và chọn cách đăng ký

| Tiêu chí | `@Aspect` + `@Component` | `@Aspect` + `@Bean` |
| --- | --- | --- |
| Ai khởi tạo bean? | Spring container tạo từ class được component scan phát hiện. | Spring container dùng giá trị trả về của factory method. |
| Cần class aspect có `@Component`? | **Có**, hoặc custom stereotype meta-annotated `@Component`. | **Không**; chỉ cần aspect class được đánh dấu `@Aspect`. |
| Điều kiện tìm thấy aspect | Scan phải bao phủ package và không loại trừ class. | `@Configuration`/`@Bean` phải được context nạp. |
| Constructor injection | Spring tự resolve constructor parameters. | Spring resolve parameters của factory method; method có thể truyền các dependency vào constructor. |
| Kiểm soát cách tạo instance | Thường dựa vào constructor và component model. | Tường minh tại factory method, dễ chọn factory/dependency dựa trên cấu hình. |
| Bean name mặc định | Thường là tên class với chữ cái đầu hạ thấp. | Theo tên factory method. |
| Điều kiện/feature flag | Có thể dùng `@Profile`, `@Conditional` trên component khi phù hợp. | Có thể đặt `@Profile`/`@Conditional` trên `@Bean` hoặc config, dễ theo dõi khi tạo bean. |
| Nhúng aspect từ module/library khác | Cần package được scan hoặc import. | Phù hợp khi người dùng library chủ động đăng ký một class `@Aspect` bằng `@Bean`. |
| Thường dùng khi | Aspect là mã ứng dụng và dự án có convention scanning nhất quán. | Muốn wiring rõ ràng, có nhiều cấu hình hoặc muốn tránh phụ thuộc vào package scan. |

**Chọn `@Component`** nếu aspect là một component bình thường của module, nằm trong vùng scan có kiểm soát và dependency injection đơn giản.

**Chọn `@Bean`** nếu muốn Spring configuration nêu rõ “aspect nào được kích hoạt”, muốn cấu hình constructor theo từng môi trường, hoặc đang tích hợp một aspect class không nên gắn Spring stereotype.

Hai cách **không khác nhau về mức độ chặn của Spring AOP**: sau khi đã đăng ký đúng cách, Spring proxy vẫn có các giới hạn như self-invocation, method `private`/`final`, proxy interfaces/class, pointcut và thời điểm target được gọi. Không nên dùng việc đổi từ `@Component` sang `@Bean` để cố sửa một lỗi tự gọi nội bộ.

## 7. Những trường hợp gây đăng ký trùng hoặc mất aspect

### 7.1. Gắn `@Component` và đồng thời viết `@Bean`

Ví dụ **không nên làm** trong cùng context đã scan package:

```java
@Aspect
@Component
class AuditAspect {
    // advice...
}

@Configuration
@ComponentScan("com.example.aspectdemo")
class AppConfig {
    @Bean
    AuditAspect customAuditAspect() {
        return new AuditAspect();
    }
}
```

Đây là **mã minh họa sai cách đăng ký**, không phải project runnable từ demo vì class có constructor khác. Nếu factory method tạo bean tên `customAuditAspect`, đồng thời component scan tạo `auditAspect`, context có **hai bean** có cùng loại aspect. Nếu cả hai cùng có advice match target, có thể khiến side effect chạy hai lần. Nếu chọn cùng một tên, kết quả lại phụ thuộc chính sách bean-definition overriding/collision và có thể làm startup lỗi. Không trông cậy vào tên trùng để loại bỏ một bean. Hãy chọn đúng một nguồn đăng ký và kiểm tra số instance.

### 7.2. Chỉ gắn `@Aspect`

```java
@Aspect
public class AuditAspect {
    // advice...
}
```

Class này không tự động xuất hiện khi component scan mặc định chạy. Cần thêm stereotype `@Component` **hoặc** khai báo `@Bean`. Điều này có thể gây tình huống application startup bình thường nhưng không có advice.

### 7.3. Aspect nằm ngoài vùng scan

`@ComponentScan("com.example.web")` không tự quét `com.example.audit`. Cân nhắc:

- Điều chỉnh package được scan để bao gồm aspect mà không quét bừa vùng ngoài dự kiến.
- Đăng ký aspect bằng `@Import` cấu hình hoặc `@Bean`.
- Trong Spring Boot, kiểm tra package gốc của `@SpringBootApplication` và vùng scan mặc định trước khi sửa.

### 7.4. `@Bean` nằm trong config không được import

Viết factory method trên một class cấu hình nhưng không đưa class đó vào ApplicationContext cũng không tạo ra aspect bean. Kiểm tra nơi khai báo `@SpringBootApplication`, `@Import` hoặc các lớp cấu hình đã đăng ký qua `AnnotationConfigApplicationContext`.

### 7.5. Gọi method qua `this` hoặc object ngoài container

Ngay cả khi `AuditAspect` được đăng ký đúng:

```java
@Service
class OrderService {
    public String batch() {
        return place("INV-2");  // self-invocation: bỏ qua proxy của place()
    }

    public String place(String id) {
        return "placed:" + id;
    }
}
```

Đây là **đoạn minh họa**, không thay các file demo trước đó. Điểm cắt vào `place(..)` sẽ không chạy cho lời gọi từ `batch()` trên target. Tương tự, `new OrderService().place("INV-2")` không được tự động chặn. Nếu cần advice, hãy gọi qua Spring bean/proxy (thường qua một bean khác), không thêm thêm bean aspect để hy vọng sửa self-invocation.

### 7.6. Nhiều context

`@ComponentScan` và `@Bean` đăng ký bean trong ApplicationContext mà chúng chạy. Auto-proxy creator của `@EnableAspectJAutoProxy` cũng có phạm vi context cục bộ. Với ứng dụng có root/child contexts, cần kiểm tra aspect, target bean và hạ tầng AOP có cùng context phù hợp. Aspect tạo ở một context không có nghĩa tự áp dụng cho mọi target bean ở context khác.

### 7.7. Quá nhiều state trong aspect singleton

Aspect bean mặc định singleton giống các Spring bean thường. Không lưu `currentUser`, `currentOrderId` hay dữ liệu request mutable trực tiếp trong field của aspect singleton, vì nhiều request có thể cùng gọi advice. Nếu cần ngữ cảnh request, dùng API request/security/observation tương ứng; nếu cần ghi audit, giao cho một service/sink an toàn.

## 8. Spring Boot và tự động đăng ký aspect

Ở Spring Boot có AOP auto-configuration và dependency tương ứng, bạn thường không cần viết `@EnableAspectJAutoProxy` thủ công; nhưng **vẫn phải tạo aspect bean** bằng `@Component` (được scan) hoặc `@Bean` (trong config được nạp).

Ví dụ Boot:

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

Nếu `AuditAspect` có cả `@Aspect` và `@Component`, nằm trong phạm vi component scan, Boot AOP có thể nhận diện nó khi dependency/hạ tầng phù hợp. Với phương án `@Bean`, một `@Configuration` được Boot nạp sẽ trả về `AuditAspect` và hạ tầng xử lý tương tự.

**Dependency tùy major version:**

```gradle
// Spring Boot 3.x, với dependency management của Boot
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aop'
}
```

```gradle
// Spring Boot 4.x, với dependency management của Boot
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aspectj'
}
```

Hai khối là **lựa chọn theo phiên bản**, không dùng đồng thời. Tên dependency không thay thế yêu cầu **đăng ký một aspect bean**. Spring Boot có thể mặc định dùng class proxy; kiểm tra `spring.aop.auto` và `spring.aop.proxy-target-class` khi chẩn đoán, đồng thời nhớ rằng các cấu hình proxy khác có thể ảnh hưởng kết quả. Aspect vẫn là bean Spring, không phải bytecode weaving chỉ vì module có `aspectjweaver`.

## 9. Cách QA: phân biệt “bean tồn tại” và “advice thực thi”

Khi cần xác minh, kiểm tra theo thứ tự sau:

### Bước 1 — aspect đã được đăng ký đúng một lần chưa?

```java
String[] aspectBeanNames = context.getBeanNamesForType(AuditAspect.class);
System.out.println(java.util.Arrays.toString(aspectBeanNames));
```

Trong demo, tên mặc định của cả hai cách đăng ký là `auditAspect` nên dòng minh họa là:

```text
[auditAspect]
```

Nếu không có tên: quên `@Component`, scan sai package hoặc `@Bean` config chưa được nạp. Nếu có hai tên: có nguy cơ đăng ký trùng.

### Bước 2 — target có được Spring proxy không?

```java
OrderService service = context.getBean(OrderService.class);
System.out.println(AopUtils.isAopProxy(service));
```

Trong demo kết quả là:

```text
true
```

Nếu false, xem lại cấu hình AOP, AspectJ Weaver classpath, pointcut và việc lấy target qua container. Proxy true chỉ nói có **ít nhất một** advisor áp dụng lên bean, chưa đủ chứng minh method cụ thể sẽ chạy advice.

### Bước 3 — method khớp pointcut có side effect không?

Gọi `service.place("INV-1")` qua reference lấy từ Spring container; xác nhận `audit:place` in ra đúng một lần trước kết quả, và `status()` không kích hoạt audit. Nếu dùng cả hai phương án đồng thời, số lần advice có thể khác; đó là lỗi đăng ký, không phải vấn đề Java method.

### Bước 4 — kiểm tra wiring như một integration test

Viết test tạo context thật và assert:

- Có đúng một bean `AuditAspect`.
- `OrderService` là AOP proxy.
- Gọi method có pointcut phát sinh đúng một audit event.
- Method ngoài pointcut không ghi audit.
- Khi đổi từ component scan sang `@Bean`, các xác nhận vẫn như nhau.
- Nếu có exception/transaction, kiểm tra thời điểm advice chạy và lỗi không bị nuốt.

Test chỉ dùng `new AuditAspect(...)` hay `new OrderService()` không thay cho integration test Spring proxy. Để test gọn, `AuditSink` nên có mock/test implementation thay cho in console.

## 10. Câu hỏi chẩn đoán nhanh

| Hiện tượng | Nguyên nhân Spring có khả năng | Cách xác minh |
| --- | --- | --- |
| Không có bean `AuditAspect` | Chỉ dùng `@Aspect`, thiếu `@Component` hoặc cấu hình `@Bean` chưa nạp. | `getBeanNamesForType(AuditAspect.class)`; kiểm tra scan/import. |
| Có aspect bean, advice không chạy | Thiếu AOP enable/auto-config, thiếu AspectJ Weaver, pointcut không match, self-invocation hoặc target không là bean. | `AopUtils.isAopProxy`, gọi ngoài qua proxy và quan sát advice. |
| Advice chạy hai lần | Đăng ký trùng bean aspect hoặc có advisor khác cùng áp dụng. | Liệt kê các bean names và advisor chain. |
| Spring báo `NoUniqueBeanDefinitionException` khi inject `AuditAspect` | Có nhiều beans cùng kiểu, thường từ scan cộng thêm `@Bean`. | Xem `getBeanNamesForType` và bỏ nguồn đăng ký thừa; `@Qualifier` chỉ giải quyết việc chọn bean khi thật sự cần nhiều instance. |
| Có `@Bean` nhưng thiếu `AuditSink` | Factory method có dependency chưa được tạo trong context. | Kiểm tra `@ComponentScan`/`@Bean` tạo sink, lỗi startup. |
| Đổi sang `@Bean` nhưng bean name/ordering khác | Method name hoặc `@Bean("...")` thay đổi; có nhiều aspect và `@Order`. | Kiểm tra tên bean, thứ tự advisor và pointcut. |
| Khi inject target theo concrete type bị lỗi | JDK interface proxy không gán được vào implementation type. | Inject bằng interface hoặc cấu hình class-based proxy phù hợp. |
| Advice không chạy từ call nội bộ | Lời gọi đi trên `this` của target. | Thử gọi method qua injected Spring bean từ class khác. |

## 11. Checklist trước khi hoàn tất

- [ ] Aspect class có `@Aspect`, chọn **một** nguồn đăng ký: `@Component` **hoặc** `@Bean`.
- [ ] Nếu chọn `@Component`: đúng package scan, không bị filter loại trừ.
- [ ] Nếu chọn `@Bean`: config có trong context; constructor/factory method inject dependency từ Spring.
- [ ] Có đúng số lượng aspect instance chủ đích (thường là một), tránh side effect advice trùng.
- [ ] Spring AOP `@AspectJ` support đã bật: `@EnableAspectJAutoProxy` với Framework thuần hoặc Boot AOP auto-configuration phù hợp.
- [ ] Có AspectJ Weaver trên classpath và phiên bản Spring tương thích.
- [ ] Target bean lấy từ container, pointcut match method, lời gọi ngoài đi qua proxy.
- [ ] Kiểu proxy, khả năng intercept method, bean scope và thứ tự advisor đáp ứng yêu cầu.
- [ ] Aspect không giữ mutable request state thiếu an toàn; dependency/sink dùng qua DI.
- [ ] Integration test xác nhận bean count **và** advice chạy đúng một lần, không chỉ kiểm tra class có annotation.

**Điểm nhớ:** Trong Spring AOP, `@Aspect` xác định **aspect là gì**, còn `@Component` hoặc `@Bean` quyết định **Spring quản lý instance aspect bằng cách nào**. Sau khi đã là Spring bean, hạ tầng `@AspectJ` của Spring mới dùng advice đó để tạo proxy cho những target bean phù hợp. Hai cách đăng ký là hai lựa chọn wiring, không thay đổi bản chất interception qua Spring proxy.

## Tài liệu chính thức

- [Spring Framework — Declaring an Aspect](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/at-aspectj.html)
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html)
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — Using @Bean](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html)
- [Spring Framework — Component Scanning](https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-beans-003">Tiêm phụ thuộc vào aspect qua Spring</a>

<details>
<summary>Xem chi tiết</summary>

# Tiêm phụ thuộc vào aspect qua Spring

## 1. Mục tiêu và phạm vi

Một Spring aspect thường không tự thực hiện mọi công việc. Ví dụ, advice cần gửi sự kiện đến một bean ghi audit, dùng một bộ đếm dùng chung, hoặc gọi một dịch vụ hạ tầng. Spring có thể **tiêm các bean đó vào chính aspect** như với những Spring bean thông thường. Để việc này hoạt động, **aspect phải được đăng ký và quản lý trong ApplicationContext**, và hạ tầng Spring AOP phải được kích hoạt để sử dụng advice của nó.

Sau bài này, bạn có thể:

- Đăng ký một lớp `@Aspect` thành Spring bean bằng `@Component` hoặc `@Bean`.
- Dùng **constructor injection** để cung cấp dependency cho aspect.
- Giải thích thứ tự quan hệ giữa Spring container, aspect bean, collaborator bean, advisor và proxy của service.
- Quan sát cùng một bean dependency được sử dụng từ advice và từ client lấy bean trong context.
- Phân biệt **tiêm dependency vào aspect** với **tiêm service proxy vào một client**.
- Chẩn đoán lỗi thiếu bean, trùng bean, vòng phụ thuộc, khởi tạo aspect bằng `new`, và vấn đề scope hoặc dữ liệu chia sẻ.

**Giới hạn:** Bài chỉ nói về dependency injection trong **Spring AOP dựa trên proxy**. Các chủ đề AOP tổng quát và AspectJ weaving ngoài Spring không được triển khai ở đây.

## 2. Aspect là bean được Spring quản lý

Cần phân biệt ba vai trò:

| Thành phần | Spring sử dụng thế nào? |
| --- | --- |
| Lớp `@Aspect` | Khai báo pointcut/advice; riêng annotation này **không tự đăng ký bean** |
| Bean aspect | Instance do `ApplicationContext` quản lý; có thể nhận dependency qua constructor, `@Autowired`, `@Value`, v.v. |
| Service proxy | Đối tượng Spring AOP cung cấp để lời gọi service đi qua chuỗi interceptor/advice thích hợp |

Nếu lớp `AuditAspect` có `@Aspect` và `@Component`, component scan có thể đăng ký nó. Spring sẽ giải quyết constructor dependency trước khi aspect được sử dụng. Khi Spring AOP đọc advice từ aspect bean và tạo proxy cho một service phù hợp, lời gọi qua proxy có thể thực thi advice; advice sẽ sử dụng dependency đã được tiêm từ trước.

~~~text
                  ApplicationContext
                           |
          +----------------+----------------+
          |                |                |
    AuditStore bean   AuditAspect bean   OrderServiceImpl bean
          |                ^                |
          +-- inject ------+                |
                           |                |
                  @Before advice            |
                           +----- advisor --+
                                             |
                                         Spring proxy
                                             |
                                  App -> OrderService.submit()
~~~

`@Aspect` phục vụ nhận diện các khai báo AOP; `@Component` hoặc `@Bean` phục vụ **đăng ký aspect là bean**. Dependency injection được Spring container thực hiện; advice chạy khi **phương thức khớp pointcut và lời gọi đi qua Spring proxy**. Việc inject thành công không tự bảo đảm advice sẽ chạy.

## 3. Ví dụ chạy được: aspect nhận một AuditStore

Giả sử `OrderService` xử lý mã đơn. Mỗi lần client gọi `submit(...)`, Spring AOP kích hoạt advice `@Before`; advice nhận `AuditStore` qua constructor và yêu cầu bean đó ghi một sự kiện. Đây là ví dụ Spring Framework thuần với **Java 17+**, **Maven 3.9+**, **Spring Framework 6.2.12**, không cần Spring Boot hoặc cơ sở dữ liệu.

Cấu trúc dự án:

~~~text
spring-aop-aspect-injection/
├── pom.xml
└── src/main/java/demo/
    ├── App.java
    ├── AppConfig.java
    ├── AuditStore.java
    ├── AuditAspect.java
    ├── OrderService.java
    └── OrderServiceImpl.java
~~~

### 3.1. Maven dependencies

Tạo **pom.xml**:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-aspect-injection</artifactId>
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

`spring-context` cung cấp container annotation và kéo theo các module Spring cần thiết, trong đó có Spring AOP. `aspectjweaver` cung cấp những kiểu của AspectJ và hỗ trợ phân tích biểu thức pointcut dạng `@AspectJ`. Ứng dụng **không bật AspectJ weaving**; Spring vẫn chặn lời gọi qua proxy.

### 3.2. Tạo bean phụ thuộc mà aspect sẽ dùng

**src/main/java/demo/AuditStore.java**

~~~java
package demo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

@Component
public class AuditStore {

    private final List<String> events = new CopyOnWriteArrayList<>();

    public void record(String message) {
        events.add(message);
        System.out.println("AUDIT " + message);
    }

    public int size() {
        return events.size();
    }
}
~~~

`@Component` làm `AuditStore` trở thành ứng viên của component scanning. Nó có tên bean mặc định `auditStore`. Instance này chịu trách nhiệm lưu sự kiện minh họa trong bộ nhớ. `CopyOnWriteArrayList` cho phép thao tác thêm/đọc an toàn về mặt đồng thời cho ví dụ nhỏ; trong ứng dụng sản xuất có nhiều sự kiện, nên chọn cơ chế lưu trữ và đồng bộ phù hợp thay vì giữ mọi sự kiện vô hạn trong singleton.

Hãy để ý: `AuditStore` là một **Spring bean thông thường**, không cần biết mình đang được gọi từ advice. Aspect là nơi phụ thuộc vào nó, không phải ngược lại.

### 3.3. Tạo hợp đồng và bean service

**src/main/java/demo/OrderService.java**

~~~java
package demo;

public interface OrderService {
    String submit(String orderId);
}
~~~

**src/main/java/demo/OrderServiceImpl.java**

~~~java
package demo;

import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl implements OrderService {

    @Override
    public String submit(String orderId) {
        System.out.println("SERVICE submit " + orderId);
        return "created:" + orderId;
    }
}
~~~

`@Service` giúp Spring đăng ký `OrderServiceImpl` qua component scan. Phương thức nghiệp vụ không tự biết đến audit. Việc ghi audit sẽ xuất hiện từ advice áp dụng cho lời gọi service qua Spring proxy.

### 3.4. Tiêm dependency bằng constructor của aspect

**src/main/java/demo/AuditAspect.java**

~~~java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {

    private final AuditStore auditStore;

    public AuditAspect(AuditStore auditStore) {
        this.auditStore = auditStore;
    }

    @Before("execution(* demo.OrderServiceImpl.submit(..))")
    public void beforeSubmit(JoinPoint joinPoint) {
        Object orderId = joinPoint.getArgs()[0];
        auditStore.record("submit " + orderId);
    }
}
~~~

Đây là trọng tâm bài học:

1. **`@Aspect`** khai báo class có advice cho Spring AOP đọc.
2. **`@Component`** cho component scanner phát hiện `AuditAspect` và đăng ký bean tên mặc định `auditAspect`.
3. **`private final AuditStore auditStore`** là dependency của instance aspect. Lớp không tự tạo `AuditStore`.
4. Constructor **`AuditAspect(AuditStore auditStore)`** cho Spring biết instance cần bean loại `AuditStore`. Vì đây là constructor duy nhất, Spring có thể tự chọn nó mà không cần `@Autowired`.
5. **`@Before(...)`** chạy trước target method nếu lời gọi khớp và đi qua proxy. Advice dùng **chính trường `auditStore` đã được inject** để ghi sự kiện.

Pointcut `execution(* demo.OrderServiceImpl.submit(..))` nhắm vào phương thức triển khai `submit` của service. Việc inject một dependency không ảnh hưởng trực tiếp đến điều kiện match pointcut.

Phương thức `beforeSubmit` nhận `JoinPoint` do hạ tầng advice cung cấp khi thực thi, **không phải một constructor dependency do container tiêm**. `JoinPoint` chứa dữ liệu của lời gọi hiện tại như đối số, signature; còn `AuditStore` là Spring bean có vòng đời riêng.

### 3.5. Cấu hình Spring AOP và component scan

**src/main/java/demo/AppConfig.java**

~~~java
package demo;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackages = "demo")
@EnableAspectJAutoProxy
public class AppConfig {
}
~~~

- `@Configuration`: đánh dấu nguồn cấu hình Java của Spring.
- `@ComponentScan(basePackages = "demo")`: đăng ký `AuditStore`, `AuditAspect`, `OrderServiceImpl` bằng các stereotype annotation.
- `@EnableAspectJAutoProxy`: bật hạ tầng Spring AOP tự phát hiện advice từ những aspect đã được đăng ký và tạo proxy cho bean service có advisor thích hợp.

Trong ví dụ Spring Framework thuần này, nếu thiếu `@EnableAspectJAutoProxy`, constructor injection vào `AuditAspect` vẫn có thể hoạt động vì aspect vẫn là một component; tuy nhiên advice của nó không tự áp dụng vào `OrderServiceImpl` nếu không có hạ tầng AOP tương đương. Ngược lại, chỉ bật auto-proxy mà không đăng ký aspect hoặc `AuditStore` cũng không đủ.

### 3.6. Chạy ứng dụng và kiểm chứng cả injection lẫn advice

**src/main/java/demo/App.java**

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            System.out.println(
                    "ASPECT_BEAN " + context.containsBean("auditAspect"));
            System.out.println(
                    "STORE_BEAN " + context.containsBean("auditStore"));

            OrderService service = context.getBean(OrderService.class);
            AuditStore store = context.getBean(AuditStore.class);

            System.out.println("IS_PROXY " + AopUtils.isAopProxy(service));
            System.out.println("COUNT_BEFORE " + store.size());

            String result = service.submit("ORD-42");
            System.out.println("RESULT " + result);
            System.out.println("COUNT_AFTER " + store.size());
        }
    }
}
~~~

Chạy trong thư mục chứa `pom.xml`:

~~~bash
mvn -q clean compile exec:java
~~~

**Đầu ra logic kỳ vọng**, không tính các thông báo riêng của Maven hoặc hệ thống logging:

~~~text
ASPECT_BEAN true
STORE_BEAN true
IS_PROXY true
COUNT_BEFORE 0
AUDIT submit ORD-42
SERVICE submit ORD-42
RESULT created:ORD-42
COUNT_AFTER 1
~~~

Đọc từng giai đoạn:

- **`ASPECT_BEAN true`**: aspect đã là bean, nên Spring có cơ hội inject `AuditStore`.
- **`STORE_BEAN true`**: dependency `AuditStore` cũng được đăng ký.
- **`IS_PROXY true`**: đối tượng service lấy từ context được Spring bọc AOP proxy; ví dụ này có advisor khớp `submit(...)`.
- **`COUNT_BEFORE 0`**: bean store chưa ghi sự kiện.
- **`AUDIT submit ORD-42`**: proxy kích hoạt `@Before`; advice gọi phương thức trên dependency đã được inject.
- **`SERVICE submit ORD-42`**: phương thức nghiệp vụ chạy sau advice.
- **`COUNT_AFTER 1`**: cùng bean `AuditStore` mà `App` lấy từ context đã nhận thêm một sự kiện.

Với cấu hình mặc định, các `@Component` này thuộc **singleton scope**, nên Spring cung cấp một shared instance `AuditStore` cho `AuditAspect` và cho `App`. Khi thay đổi scope, không thể mặc định suy ra hai điểm sử dụng luôn thấy chính cùng một instance.

## 4. Bản chất của constructor injection trong aspect

Spring khởi tạo aspect giống như một bean có dependency:

~~~text
@Component AuditStore
        |
        v
Spring có thể tạo AuditStore bean
        |
        v
@Component @Aspect AuditAspect(AuditStore)
        |
        +-- resolve constructor argument theo bean type
        +-- truyền AuditStore vào constructor
        +-- lưu dependency ở trường final
        |
        v
Spring AOP đọc advice / advisor từ aspect bean
        |
        v
Service proxy gọi advice đã cấu hình
        |
        v
AuditAspect.beforeSubmit(...) sử dụng auditStore
~~~

Sơ đồ diễn giải **quan hệ phụ thuộc**, không bắt buộc toàn bộ bean trong mọi ứng dụng được khởi tạo theo thứ tự cứng. Container có thể tạo các singleton khác trước hoặc sau tùy graph dependency và configuration.

**Vì sao nên dùng constructor injection?**

- Aspect có dependency bắt buộc và được khởi tạo ở trạng thái đầy đủ; không có trạng thái đã tạo xong nhưng trường nghiệp vụ bắt buộc chưa được gán.
- Trường `final` thể hiện tham chiếu không bị thay đổi sau constructor.
- Khi test phần logic bên trong aspect bằng Java, có thể truyền một fake/stub `AuditStore` rõ ràng.
- Constructor mô tả chính xác dependency graph, giúp lỗi thiếu bean xuất hiện khi container khởi tạo.

`@Autowired` trên constructor duy nhất là **không bắt buộc** với cơ chế constructor injection hiện đại của Spring. Nếu có nhiều constructor, cần xác định constructor nào được chọn; có thể dùng `@Autowired` theo quy tắc resolve constructor của Spring.

### Constructor injection khác với phương thức advice

Không nên nhầm:

~~~java
public AuditAspect(AuditStore auditStore) { ... }
~~~

với:

~~~java
@Before("execution(* demo.OrderServiceImpl.submit(..))")
public void beforeSubmit(JoinPoint joinPoint) { ... }
~~~

Constructor được Spring gọi để **tạo bean aspect** và cung cấp `AuditStore`. Advice được AOP interceptor gọi **mỗi lần lời gọi phù hợp đi qua proxy**; đối tượng `JoinPoint` phản ánh invocation hiện tại.

Không thể kỳ vọng Spring tự tiêm một service bean tùy ý vào mọi tham số của phương thức `@Before`. Muốn dependency bền vững, hãy dùng constructor hoặc cơ chế injection bean. Muốn dữ liệu invocation, dùng `JoinPoint` hoặc argument binding phù hợp.

## 5. Cách khác: đăng ký aspect và dependency bằng @Bean

Nếu không dùng component scanning, Spring vẫn tiêm dependency vào aspect được đăng ký bằng Java configuration. Để thử riêng cách này, **thay hẳn nội dung** file `src/main/java/demo/AppConfig.java` ở ví dụ bằng:

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
public class AppConfig {

    @Bean
    public AuditStore auditStore() {
        return new AuditStore();
    }

    @Bean
    public AuditAspect auditAspect(AuditStore auditStore) {
        return new AuditAspect(auditStore);
    }

    @Bean
    public OrderService orderService() {
        return new OrderServiceImpl();
    }
}
~~~

Trong biến thể này, `@ComponentScan` đã được gỡ bỏ, nên việc còn giữ `@Component` hoặc `@Service` trên các class **không khiến chúng bị đăng ký hai lần**. Chỉ có ba phương thức `@Bean` ở trên tạo ra ba bean tương ứng. Vì tên bean `auditStore` và `auditAspect` giữ nguyên, và `App` lấy service theo **OrderService.class**, **không cần sửa App.java**. Chạy lại:

~~~bash
mvn -q clean compile exec:java
~~~

Đầu ra logic vẫn là:

~~~text
ASPECT_BEAN true
STORE_BEAN true
IS_PROXY true
COUNT_BEFORE 0
AUDIT submit ORD-42
SERVICE submit ORD-42
RESULT created:ORD-42
COUNT_AFTER 1
~~~

Ở đây:

1. Spring gọi factory `auditStore()` để có dependency bean.
2. Khi cần `AuditAspect`, Spring resolve tham số `AuditStore` của factory method `auditAspect(...)`; method truyền bean đó vào constructor.
3. Bean `AuditAspect` có `@Aspect` trong định nghĩa class nên Spring AOP vẫn nhận diện advice của nó.
4. Bean service được Spring quản lý dù factory sử dụng `new OrderServiceImpl()`; instance đó được trả **về cho container**, có thể trải qua các bước xử lý như auto-proxy trước khi client nhận.
5. `@EnableAspectJAutoProxy` vẫn cần thiết trong cấu hình Spring Framework thuần.

Điều quan trọng là **ai quản lý instance và làm sao dependency được resolve**. `new AuditAspect(...)` trong phương thức `@Bean` hợp lệ vì instance sau đó được đăng ký thành Spring bean. Nếu ứng dụng tạo `new AuditAspect(...)` ở ngoài context mà không đăng ký, Spring AOP không tự dùng instance ấy làm aspect.

Có thể bỏ `@Component` khỏi `AuditAspect` trong biến thể `@Bean` mà aspect vẫn chạy vì class vẫn có `@Aspect` và instance được Spring quản lý qua factory method.

Sau thí nghiệm, nếu muốn trở về phiên bản scanner, khôi phục `AppConfig` ban đầu. Không giữ đồng thời hai đường đăng ký cùng service/aspect khi chưa chủ đích dùng nhiều bean; điều này có thể gây ambiguity, xung đột bean name hoặc advice được áp dụng nhiều lần.

## 6. Các kiểu dependency có thể inject vào aspect

### 6.1. Một bean dịch vụ thông thường

Đây là trường hợp đã chạy: `AuditAspect` nhận `AuditStore` từ Spring. Cũng có thể inject một interface thay vì implementation, ví dụ:

~~~java
public interface AuditWriter {
    void write(String event);
}
~~~

Nếu aspect khai báo constructor `AuditAspect(AuditWriter writer)`, Spring cần tìm được một bean thực thi `AuditWriter`. Khi nhiều implementation cùng tồn tại, phải giải quyết ambiguity, chẳng hạn bằng **`@Qualifier`** hoặc **`@Primary`** tùy thiết kế. Đoạn interface này chỉ là **minh họa API**, chưa thay thế file nào trong ví dụ chạy được.

Nên ưu tiên interface khi collaborator có nhiều cách triển khai hoặc cần test double. Không nhất thiết tạo interface cho mọi class nhỏ chỉ vì dependency injection hỗ trợ việc đó.

### 6.2. Thuộc tính cấu hình với @Value

Aspect có thể nhận giá trị do Spring resolve trong constructor:

~~~java
@Aspect
@Component
public class AuditAspect {

    private final AuditStore auditStore;
    private final boolean enabled;

    public AuditAspect(
            AuditStore auditStore,
            @org.springframework.beans.factory.annotation.Value(
                    "${audit.enabled:true}") boolean enabled) {
        this.auditStore = auditStore;
        this.enabled = enabled;
    }

    @Before("execution(* demo.OrderServiceImpl.submit(..))")
    public void beforeSubmit(org.aspectj.lang.JoinPoint joinPoint) {
        if (enabled) {
            auditStore.record("submit " + joinPoint.getArgs()[0]);
        }
    }
}
~~~

Đây là **phiên bản thay thế minh họa** cho toàn bộ nội dung `AuditAspect.java`, không dùng đồng thời với class trùng tên ở mục 3. Ví dụ đọc `audit.enabled`, mặc định `true`, từ các property sources mà Spring context có sẵn. Khi dùng cấu hình riêng, cần bảo đảm property source được nạp đúng cách; trong Spring Boot có thể dùng cơ chế configuration properties phù hợp. **`@Value` chỉ lấy giá trị cấu hình**, không quyết định có proxy hay pointcut có match.

### 6.3. Dependency có vòng đời ngắn hơn aspect

Aspect được đăng ký bằng `@Component` thường là singleton, còn một collaborator có thể dùng `request` hoặc `prototype` scope. Nếu inject thẳng một bean prototype vào singleton bằng constructor, dependency thường được resolve **một lần lúc singleton được tạo**; không tự tạo instance prototype mới trên mỗi advice invocation.

Nếu cần tra cứu bean theo mỗi lời gọi, Spring có **`ObjectProvider<T>`**. Ví dụ minh họa:

~~~java
private final org.springframework.beans.factory.ObjectProvider<AuditContext>
        contextProvider;

public AuditAspect(
        org.springframework.beans.factory.ObjectProvider<AuditContext>
                contextProvider) {
    this.contextProvider = contextProvider;
}
~~~

Trong advice, dùng `contextProvider.getObject()` tại thời điểm thật sự cần. Đoạn mã chỉ minh họa cách khai báo dependency: lớp `AuditContext` và bean tương ứng phải được định nghĩa; **không phải mã chạy độc lập**. Đối với request-scoped dependency cần request scope đang hoạt động, hoặc một scoped proxy thích hợp. Gọi trong thread không có HTTP request thường dẫn tới lỗi scope; `ObjectProvider` không tự tạo ra request context.

Không lưu dữ liệu biến đổi của từng request vào trường instance của singleton aspect. Nếu advice chạy đồng thời ở nhiều thread, dữ liệu đó dễ lẫn giữa các invocation.

## 7. Cùng một ApplicationContext và điều kiện proxy

Trong ví dụ, `AppConfig` bật AOP trong context chứa cả `AuditAspect`, `AuditStore` và `OrderServiceImpl`. Đây là tình huống dễ quan sát nhất.

Ở ứng dụng có **nhiều ApplicationContext** (ví dụ parent/child), cần kiểm tra:

- Aspect bean và dependency của nó có nhìn thấy nhau qua cơ chế bean resolution của context hay không.
- Service được xử lý bởi hạ tầng auto-proxy của context nào.
- Cấu hình bật Spring AOP có được đặt ở context chứa bean cần advice hay không.

`@EnableAspectJAutoProxy` áp dụng cho **context nơi nó được khai báo**. Không thể tự suy luận việc bật AOP ở một context sẽ làm tất cả bean trong mọi context liên quan đều trở thành proxy.

Trong Spring Framework thuần, nếu service có interface, Spring AOP thường dùng **JDK dynamic proxy** khi phù hợp. Ví dụ gọi `context.getBean(OrderService.class)` để dùng interface. Không giả định proxy là đối tượng có runtime type `OrderServiceImpl`. Cấu hình `proxyTargetClass = true` có thể chọn class-based proxy; việc này không thay đổi cách constructor injection diễn ra trong aspect.

**Hai kiểm tra độc lập cần có**:

1. **Injection**: bean `AuditAspect` khởi tạo được, nhận bean `AuditStore` và có thể gọi `record(...)`.
2. **AOP**: bean `OrderService` trả về là proxy phù hợp và lời gọi đến `submit(...)` thực sự chạy advice.

Kết quả `AopUtils.isAopProxy(service)` bằng `true` một mình vẫn chưa chứng minh **AuditAspect** được áp dụng: proxy có thể được tạo bởi một advisor khác. Kiểm tra hành vi output hoặc kiểm thử trạng thái của dependency là bằng chứng cụ thể hơn.

## 8. Các lỗi thường gặp khi inject vào aspect

| Hiện tượng | Nguyên nhân trong Spring | Cách xử lý |
| --- | --- | --- |
| `NoSuchBeanDefinitionException` hoặc `UnsatisfiedDependencyException` khi tạo aspect | Chưa đăng ký `AuditStore`, package nằm ngoài vùng scan hoặc thiếu `@Bean` | Kiểm tra `@Component`, package scan và Java configuration |
| `NoUniqueBeanDefinitionException` cho constructor dependency | Có nhiều bean phù hợp cùng kiểu, không xác định được bean cần dùng | Dùng `@Qualifier`, `@Primary`, hoặc cấu hình một bean đúng mục đích |
| Aspect được tạo nhưng advice không chạy | Thiếu `@EnableAspectJAutoProxy`, pointcut không khớp, hoặc lời gọi không qua proxy | Kiểm tra AOP config, biểu thức pointcut và client lấy service từ Spring |
| `@Aspect` có tồn tại nhưng bean không có trong context | Chỉ gắn `@Aspect` mà không có đăng ký component hoặc factory `@Bean` | Thêm `@Component` trong vùng scan hoặc đăng ký aspect bằng `@Bean` |
| Dùng `new AuditAspect(...)` trong mã ứng dụng rồi mong advice tự chạy | Instance nằm ngoài Spring container | Đăng ký aspect như bean trong context |
| Xảy ra `BeanCurrentlyInCreationException` hoặc lỗi vòng phụ thuộc | Aspect và collaborator/service phụ thuộc vòng qua lại | Thiết kế lại hướng phụ thuộc, tách collaborator audit khỏi service đang được advise |
| Đếm audit sai khi nhiều lời gọi đồng thời | Mutable field chung trong singleton aspect/collaborator không an toàn thread | Dùng cấu trúc đồng thời thích hợp, giữ dữ liệu invocation ở biến cục bộ |
| Không lấy được request-scoped bean trong advice | Scope không active ở thread hiện tại | Xem lại nơi kích hoạt lời gọi, dùng scoped proxy/provider đúng yêu cầu |
| Inject `null` khi unit test dùng `new AuditAspect()` sai cách | Test đã bỏ qua constructor injection và container | Truyền dependency qua constructor hoặc tạo Spring test context |
| Advice log trước nhưng nghiệp vụ không chạy | Advice ném exception; với `@Before`, lỗi của advice có thể ngắt lời gọi | Kiểm soát ngoại lệ và xác định chính sách lỗi của hạ tầng audit |

Vòng phụ thuộc cần chú ý hơn trong AOP vì quá trình tạo proxy có thể cần đọc advisor và aspect bean. Ví dụ graph `AuditAspect → AuditStore → OrderService → (advisor cần AuditAspect)` là một thiết kế dễ trở nên rắc rối. Hãy giữ các collaborator mà aspect dùng **đơn giản, độc lập với service đang bị chặn** khi có thể.

## 9. Phân biệt dependency injection với self-invocation và thứ tự advice

Đặt được dependency vào aspect không làm thay đổi giới hạn **self-invocation** của Spring AOP. Nếu một phương thức của `OrderServiceImpl` tự gọi `this.submit(...)`, cuộc gọi nội bộ đó bỏ qua proxy nên advice không được thực thi riêng cho `submit(...)`. Đây là vấn đề **đường đi của lời gọi**, không phải lỗi constructor injection.

Nếu có nhiều aspect cùng dùng `AuditStore`, các aspect có thể được inject cùng một singleton store. Tuy nhiên thứ tự advice có thể ảnh hưởng thứ tự sự kiện. Trong Spring có thể dùng `@Order` để kiểm soát mức ưu tiên khi cần, và cần thử hành vi thực tế. Không lấy thứ tự khai báo class hoặc tên bean làm bằng chứng về thứ tự chạy advice.

Đối với **`@Before`**, nếu `AuditStore.record(...)` ném exception, phương thức service có thể không được gọi. Khi audit chỉ là quan sát, quyết định có bắt/ghi/lờ lỗi audit hay không phải dựa trên yêu cầu nghiệp vụ; tránh tự động nuốt mọi lỗi làm che giấu sự cố.

## 10. Cách kiểm tra bằng test ngắn

Có thể kiểm thử logic tích hợp mà không cần phụ thuộc vào kết quả in log, bằng cách tạo lại context và assert trực tiếp trên `AuditStore`:

~~~java
try (var context =
         new org.springframework.context.annotation
             .AnnotationConfigApplicationContext(demo.AppConfig.class)) {

    demo.OrderService service =
            context.getBean(demo.OrderService.class);
    demo.AuditStore store = context.getBean(demo.AuditStore.class);

    if (store.size() != 0) {
        throw new AssertionError("Store must begin empty");
    }

    String result = service.submit("T-1");

    if (!"created:T-1".equals(result)) {
        throw new AssertionError("Unexpected result: " + result);
    }
    if (store.size() != 1) {
        throw new AssertionError("Aspect did not record exactly once");
    }
}
~~~

Đoạn này là **fragment kiểm thử bổ sung**, để đặt vào một phương thức test hoặc `main` khác trong dự án ở mục 3; nó không phải file Java hoàn chỉnh đứng độc lập. Kết quả thành công: không ném `AssertionError`. Test kiểm tra cả kết quả service và hiệu ứng ở collaborator. Nếu bỏ `@EnableAspectJAutoProxy` trong ví dụ Spring Framework thuần, điều kiện `store.size() == 1` sẽ thất bại do advice không chạy.

Khi unit test riêng `AuditAspect`, có thể tạo `new AuditAspect(fakeStore)` và gọi trực tiếp phương thức `beforeSubmit` với một `JoinPoint` giả lập. Kiểu test đó xác nhận logic advice nhưng **không xác nhận Spring đã tạo proxy**; cần ít nhất một integration test qua `ApplicationContext` cho cơ chế Spring AOP.

## 11. Nguyên tắc áp dụng trong ứng dụng Spring

- Dùng **constructor injection** cho dependency bắt buộc của aspect, ưu tiên `final` và dependency rõ ràng.
- Đăng ký aspect qua **`@Component` + component scan** hoặc **`@Bean`**; không coi `@Aspect` là cách đăng ký Spring bean.
- Bảo đảm các collaborator được tiêm có bean definition hợp lệ và scope phù hợp với vòng đời aspect.
- Đặt logic nghiệp vụ ở service; aspect chỉ thực hiện trách nhiệm phụ trợ như quan sát hoặc áp dụng quy tắc cắt ngang có mục đích.
- Tránh để aspect phụ thuộc ngược vào service đang bị chính aspect chặn nếu tạo vòng phụ thuộc hoặc khiến logic audit tái nhập bất ngờ.
- Hạn chế mutable state theo request trong singleton aspect; dùng tham số advice hoặc collaborator được thiết kế thread-safe.
- Kiểm tra **cả dependency injection lẫn AOP advice thực tế**. Có bean, có proxy và có hiệu ứng advice là ba trạng thái có quan hệ nhưng không đồng nhất.

## 12. Câu hỏi tự đánh giá

**1. Vì sao `@Aspect` không đủ để Spring inject `AuditStore`?**

Vì `@Aspect` chỉ khai báo aspect. Spring phải đăng ký `AuditAspect` như một bean (ví dụ `@Component` trong vùng quét hoặc `@Bean`) trước khi container thực hiện dependency injection.

**2. Constructor của `AuditAspect` có cần `@Autowired` không?**

Không bắt buộc khi lớp chỉ có một constructor. Spring có thể tự chọn constructor đó và resolve dependency theo kiểu, rồi áp dụng quy tắc chọn bean nếu có nhiều ứng viên.

**3. Tại sao `COUNT_AFTER` là 1 dù `App` không trực tiếp gọi `store.record(...)`?**

Vì lời gọi service qua proxy kích hoạt advice. Advice gọi `record(...)` trên singleton `AuditStore` đã được inject; `App` lấy cùng bean đó từ context để kiểm tra kết quả.

**4. Cấu hình `@Bean` dùng `new AuditAspect(auditStore)` có mất khả năng inject hoặc advice không?**

Không. Instance được trả về từ factory `@Bean` là Spring-managed bean. Spring AOP có thể đọc khai báo `@Aspect` và dùng advice nếu hạ tầng AOP hoạt động.

**5. Nếu `AuditStore` không được đăng ký, liệu aspect có chạy mà bỏ qua audit không?**

Với dependency constructor bắt buộc, context sẽ thất bại khi cần tạo `AuditAspect` vì không resolve được tham số; Spring không tự truyền `null` để bỏ qua dependency.

**6. Một aspect singleton có thể lưu mã đơn hiện tại trong field `lastOrderId` không?**

Không nên dùng field đó để biểu diễn dữ liệu của từng invocation, vì nhiều thread có thể ghi đè lẫn nhau. Dùng biến cục bộ trong advice hoặc cơ chế context phù hợp.

**7. Nếu `IS_PROXY true` nhưng không thấy audit, nên kiểm tra gì?**

Kiểm tra có `AuditAspect` bean thật sự không, pointcut có khớp method không, lời gọi có qua proxy không và liệu proxy có được tạo từ advisor khác không.

## 13. Tài liệu Spring chính thức

- Spring Framework — Dependency Injection: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html
- Spring Framework — Classpath Scanning and Managed Components: https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html
- Spring Framework — `@Bean` methods: https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html
- Spring Framework — `@AspectJ` support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html

**Ghi nhớ:** Trong Spring, aspect nhận dependency bằng cơ chế bean injection thông thường khi đã là Spring-managed bean. Việc advice của aspect chạy lại phụ thuộc vào cấu hình Spring AOP, pointcut và lời gọi qua proxy. Hai cơ chế phối hợp để aspect có thể sử dụng các dịch vụ của `ApplicationContext` mà không tự khởi tạo dependency.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-beans-004">Đăng ký bean đích và tránh đối tượng tạo bằng new ngoài container</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP: đăng ký bean đích và tránh đối tượng tạo bằng `new` ngoài container

## 1. Vấn đề thực tế: cùng một class, vì sao lời gọi có hoặc không chạy advice?

Giả sử ứng dụng có một `ReceiptService` và một aspect ghi log trước phương thức `issue()`. Hai cách sử dụng sau có thể dẫn đến kết quả khác nhau:

```java
// Đối tượng Spring quản lý: có cơ hội nhận AOP proxy khi advisor khớp.
ReceiptService managed = applicationContext.getBean(ReceiptService.class);
managed.issue("INV-01");

// Đối tượng Java được tự tạo: không tự đi qua hạ tầng AOP của Spring.
ReceiptService unmanaged = new ReceiptService();
unmanaged.issue("INV-02");
```

Đoạn code này chỉ minh họa cách lấy đối tượng, không phải chương trình độc lập. Với Spring AOP dựa trên proxy:

- `applicationContext.getBean(...)` trả về **bean do Spring quản lý**, có thể là một **AOP proxy** nếu hạ tầng AOP đã bật và có advisor phù hợp.
- `new ReceiptService()` tạo ra một **đối tượng Java độc lập**, không được Spring tự động đăng ký và xử lý như một bean chỉ vì class của nó được gắn `@Service`.
- Advice chạy khi lời gọi **đi qua proxy**, chứ không phải vì có annotation nào đó hiện diện trên class hoặc vì object được tạo từ cùng một source code.

Điều kiện **cần** để Spring AOP tự động advice một service thông thường là đối tượng đó được Spring quản lý và lời gọi tới nó đi qua proxy phù hợp. Nhưng **đăng ký bean không tự động bảo đảm có advice**: bạn vẫn cần kích hoạt Spring AOP, có aspect/advisor được đăng ký, pointcut khớp và lời gọi qua proxy.

### Đường đi của hai đối tượng

```text
                           ApplicationContext
                                |
                     Bean definition cho service
                                |
                     Tạo + cấu hình target bean
                                |
                   Auto-proxy creator (nếu khớp)
                                |
                  Spring bean reference: proxy
                                |
           Controller / Workflow được DI service
                                |
               proxy -> advice -> target.issue()


             Code bên ngoài Spring gọi:
                   new ReceiptService()
                                |
                      object.issue() trực tiếp
                                |
                     Không có Spring AOP proxy
```

Đây là cơ chế **Spring AOP proxy** trong Spring IoC container. Không suy diễn cho cơ chế AspectJ compile-time/load-time weaving, vốn hoạt động ở mức bytecode và có phạm vi khác.

## 2. Đăng ký target bean đúng cách

### Cách A — Component scanning với `@Component` / `@Service`

Khai báo class nghiệp vụ là Spring component:

```java
package demo.springaop;

import org.springframework.stereotype.Service;

@Service
public class ReceiptService {
    public void issue(String id) {
        System.out.println("[service] issue " + id);
    }
}
```

Bật component scanning cho đúng package:

```java
package demo.springaop;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = AppConfig.class)
@EnableAspectJAutoProxy
public class AppConfig {
}
```

Hai tệp này là phần của ví dụ đầy đủ ở mục 5. `@Service` là specialization của `@Component` nên Spring component scanner có thể tìm thấy. `@Repository`, `@Controller`, `@Component` cũng là các stereotype liên quan, nhưng nên chọn stereotype đúng vai trò.

**Phạm vi scan rất quan trọng:** nếu class có `@Service` nhưng package của nó nằm ngoài phạm vi `@ComponentScan`, Spring không tạo bean từ class đó bằng đường scan. Trong Spring Boot, `@SpringBootApplication` bao gồm component scanning bắt đầu từ package chứa application class và các package con mặc định; đặt main class ở package cha phù hợp, hoặc khai báo `scanBasePackages`/`scanBasePackageClasses` khi cấu trúc dự án yêu cầu.

`@Component` hay `@Service` không biến mọi lần gọi `new ReceiptService()` thành bean. Annotation là thông tin cho **container xử lý khi phát hiện class**, không can thiệp vào toán tử `new` của Java.

### Cách B — Đăng ký target với `@Bean`

Khi class không tiện gắn `@Service` (thư viện bên ngoài, code cần dựng qua factory, cấu hình cụ thể), có thể dùng phương thức `@Bean`:

```java
@Configuration
class ReceiptConfig {
    @Bean
    ReceiptService receiptService() {
        return new ReceiptService();
    }
}
```

Đây là **phương án thay thế**: hãy bỏ `@Service` hoặc loại class khỏi component scan nếu đồng thời dùng cách `@Bean` để tránh vô tình đăng ký hai bean cùng kiểu, khiến việc DI theo kiểu bị mơ hồ.

Sự khác biệt nằm ở **ai giữ quyền quản lý instance**:

- Bên trong `@Bean`, việc viết `new ReceiptService()` là hoàn toàn bình thường: Spring gọi phương thức factory, lấy object được trả về, đưa nó vào vòng đời bean và áp dụng các `BeanPostProcessor`/auto-proxy liên quan.
- Nếu business code tự viết `new ReceiptService()` và giữ riêng reference đó, Spring không biết object mới ấy thuộc bean definition nào.
- Một helper do bạn `new` bên trong một bean khác cũng không tự trở thành **bean riêng** chỉ vì bean chứa nó được Spring quản lý.

Nói cách khác, lời khuyên “tránh `new` ngoài container” áp dụng cho **các dependency cần IoC/AOP**, không phải cấm mọi toán tử `new`. Bạn vẫn có thể tạo DTO, record, value object, collection và đối tượng tạm thời thông thường bằng `new`.

### Cách C — Đăng ký programmatically và XML

Spring cũng hỗ trợ đăng ký bean definition bằng API của container hoặc cấu hình XML. Ví dụ XML minh họa:

```xml
<bean id="receiptService" class="demo.springaop.ReceiptService"/>
```

Fragment này chưa phải file XML hoàn chỉnh. Về mặt AOP, cách đăng ký cụ thể không phải trọng tâm: điều quan trọng là container có **bean definition**, quản lý quá trình khởi tạo và có thể xuất bản reference proxy.

### Điều kiện bổ sung: Aspect và hạ tầng AOP cũng phải được đăng ký

Có target bean chưa đủ. Với kiểu khai báo Spring `@AspectJ`:

```java
@Aspect
@Component
public class ReceiptAspect {
    @Before("execution(* demo.springaop.ReceiptService.issue(..))")
    public void beforeIssue() {
        System.out.println("[aop] before issue");
    }
}
```

Đoạn này minh họa aspect; các import đầy đủ có trong mục 5. `@Aspect` **không tự khiến class được component scanner đăng ký**; dùng thêm `@Component` hoặc tạo aspect qua `@Bean`. Hạ tầng auto-proxy cũng phải được bật, ví dụ `@EnableAspectJAutoProxy` khi dùng Spring Framework thuần. Có thể dùng Spring Boot với `spring-boot-starter-aop` để nhận auto-configuration AOP thích hợp.

**Phân biệt:** `@Aspect` mô tả aspect, còn `@Service`/`@Component`/`@Bean` đưa các instance vào container. Target bean và aspect bean là hai vai trò khác nhau trong Spring AOP.

## 3. Vì sao container có thể trả về proxy?

Quy trình thường gặp với một singleton:

1. Spring biết bean definition của target nhờ component scan, `@Bean` hoặc cơ chế đăng ký tương đương.
2. Container tạo instance, resolve dependency, tiêm cấu hình và thực hiện initialization callbacks.
3. Hạ tầng `BeanPostProcessor` của Spring AOP xem xét các advisor; khi pointcut khớp và proxy được yêu cầu, nó có thể bọc target bằng proxy.
4. Bean khác yêu cầu dependency từ Spring sẽ nhận **reference được container công bố**; nếu đã được AOP bọc, reference này là proxy.
5. Lời gọi method qua proxy đi qua interceptor/advice trước hoặc sau khi đến target, tùy loại advice và pointcut.

```text
Spring-managed consumer
        |
        | constructor injection
        v
   service reference
        |
        v
   Spring AOP proxy
        |
        +----> advice
        |
        v
   target method
```

Thời điểm tạo proxy thực tế có thể bị ảnh hưởng bởi scope, circular dependency hoặc các custom post-processor. Bản chất không đổi: **caller phải dùng reference proxy**, không phải một target riêng tự tạo.

### Cùng kiểu class nhưng không nhất thiết cùng object

Hai dòng sau không có nghĩa tương đương:

```java
ReceiptService fromSpring = context.getBean(ReceiptService.class);
ReceiptService fromNew = new ReceiptService();
```

Với một Spring-managed singleton được AOP bọc, `fromSpring` thường là reference proxy. `fromNew` là instance độc lập. Những phép kiểm tra như `fromSpring == fromNew` thường sẽ cho `false`; cũng không nên dựa vào `fromSpring.getClass() == ReceiptService.class` khi Spring đã tạo subclass proxy.

Dùng API Spring để chẩn đoán:

```java
boolean proxied = AopUtils.isAopProxy(fromSpring);
boolean jdkProxy = AopUtils.isJdkDynamicProxy(fromSpring);
boolean cglibProxy = AopUtils.isCglibProxy(fromSpring);
```

Ba lệnh chỉ minh họa API, không phải chương trình độc lập. Trong ví dụ hoàn chỉnh, service không triển khai interface nên Spring AOP thường tạo class-based proxy. Nếu target có interface, kiểu proxy phụ thuộc vào cấu hình, như `proxyTargetClass`; luôn kiểm tra **bean thực tế nhận được**.

## 4. Dependency Injection là cách tiêu chuẩn đưa proxy đến nơi gọi

Thay vì:

```java
public class CheckoutWorkflow {
    private final ReceiptService service = new ReceiptService();

    public void checkout(String id) {
        service.issue(id);
    }
}
```

hãy biến workflow thành Spring bean và khai báo constructor injection:

```java
@Component
public class CheckoutWorkflow {
    private final ReceiptService service;

    public CheckoutWorkflow(ReceiptService service) {
        this.service = service;
    }

    public void checkout(String id) {
        service.issue(id);
    }
}
```

Các đoạn trong mục này minh họa khác biệt, không phải tệp Java độc lập. Khi `CheckoutWorkflow` được Spring tạo, container resolve `ReceiptService` và có thể inject proxy phù hợp. Với một constructor duy nhất, Spring tự động chọn constructor injection mà không cần `@Autowired`.

**Lưu ý khi gọi từ code ngoài Spring:** Controller, scheduled task, message listener, startup runner, test hoặc object do framework khác tạo cần nhận service từ container bằng cơ chế tích hợp thích hợp. Không tự `new` service trong callback rồi kỳ vọng aspect Spring chặn method call. Khi ở ranh giới ứng dụng cần lấy bean, dùng DI là ưu tiên; lấy trực tiếp qua `ApplicationContext.getBean(...)` là lựa chọn hợp lý tại điểm tích hợp, nhưng không nên biến toàn bộ business code thành service locator.

### Hai lỗi khác nhau dễ bị nhầm

- **Target tự tạo bằng `new`:** object không có Spring proxy ngay từ đầu. Dù method được gọi từ bên ngoài object, Spring AOP không có reference proxy để intercept.
- **Self-invocation trên target đã được Spring quản lý:** bean đã có proxy, nhưng `this.otherMethod()` bên trong target vẫn gọi trực tiếp trên target, bỏ qua proxy. Đây là vấn đề đường gọi, không phải thiếu bean registration.

Ví dụ:

```java
@Service
class InvoiceService {
    public void outer() {
        this.inner(); // Không đi qua Spring AOP proxy.
    }

    public void inner() {
        // Advice dành cho inner() sẽ không tự chạy khi gọi từ outer().
    }
}
```

Đây là đoạn minh họa trong một hệ thống có advisor cho `inner()`. Cách xử lý phù hợp là tách phần được advice vào một Spring bean khác rồi inject bean đó, để lời gọi đi qua reference do container cấp.

## 5. Ví dụ Maven + Java 17 chạy được: managed bean và object tự `new`

Ví dụ dùng **Spring Framework 6.2.x**, Java 17 và Maven. Không cần Spring Boot. Có 4 tệp Java trong package `demo.springaop` và một `pom.xml`. Aspect ghi log trước `ReceiptService.issue()`; main so sánh lời gọi trên bean do Spring quản lý và object tự tạo.

Cấu trúc:

```text
spring-aop-managed-target/
├── pom.xml
└── src/main/java/demo/springaop/
    ├── AppConfig.java
    ├── ReceiptService.java
    ├── ReceiptAspect.java
    ├── CheckoutWorkflow.java
    └── DemoMain.java
```

**Tệp `pom.xml`:**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-managed-target</artifactId>
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

**Tệp `src/main/java/demo/springaop/AppConfig.java`:**

```java
package demo.springaop;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = AppConfig.class)
@EnableAspectJAutoProxy
public class AppConfig {
}
```

`@ComponentScan(basePackageClasses = AppConfig.class)` tìm các Spring component trong package `demo.springaop`, nơi có target, workflow và aspect. `@EnableAspectJAutoProxy` cho phép Spring tự tạo proxy khi advice khớp.

**Tệp `src/main/java/demo/springaop/ReceiptService.java`:**

```java
package demo.springaop;

import org.springframework.stereotype.Service;

@Service
public class ReceiptService {
    public void issue(String id) {
        System.out.println("[service] issue " + id);
    }
}
```

Service này được Spring phát hiện qua `@Service`. Class và method không `final` nên có thể được bọc bằng class-based proxy trong ví dụ không có interface.

**Tệp `src/main/java/demo/springaop/ReceiptAspect.java`:**

```java
package demo.springaop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ReceiptAspect {
    @Before("execution(* demo.springaop.ReceiptService.issue(..))")
    public void beforeIssue() {
        System.out.println("[aop] before issue");
    }
}
```

Aspect là một Spring bean nhờ `@Component`, đồng thời có advice `@Before` áp dụng lên phương thức `issue()`.

**Tệp `src/main/java/demo/springaop/CheckoutWorkflow.java`:**

```java
package demo.springaop;

import org.springframework.stereotype.Component;

@Component
public class CheckoutWorkflow {
    private final ReceiptService receiptService;

    public CheckoutWorkflow(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    public void checkout(String id) {
        System.out.println("[workflow] checkout " + id);
        receiptService.issue(id);
    }
}
```

Workflow cũng do Spring tạo. Dependency `receiptService` trong constructor là reference được container cấp; nếu Spring đã tạo AOP proxy cho service thì workflow sử dụng proxy đó.

**Tệp `src/main/java/demo/springaop/DemoMain.java`:**

```java
package demo.springaop;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoMain {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            ReceiptService managed = context.getBean(ReceiptService.class);
            System.out.println("managedProxy=" + AopUtils.isAopProxy(managed));

            context.getBean(CheckoutWorkflow.class).checkout("INV-01");

            ReceiptService unmanaged = new ReceiptService();
            System.out.println("manualProxy=" + AopUtils.isAopProxy(unmanaged));
            unmanaged.issue("INV-02");
        }
    }
}
```

Chạy trong thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.springaop.DemoMain
```

**Stdout từ các câu lệnh `System.out` của chương trình** (Maven/Spring có thể có log khác tùy môi trường):

```text
managedProxy=true
[workflow] checkout INV-01
[aop] before issue
[service] issue INV-01
manualProxy=false
[service] issue INV-02
```

Phân tích từng điểm:

1. `managedProxy=true`: Spring đã nhận diện `ReceiptService` là target, `ReceiptAspect` có pointcut khớp, nên container cung cấp proxy.
2. `CheckoutWorkflow` dùng constructor injection. Khi gọi `receiptService.issue("INV-01")`, invocation qua proxy kích hoạt dòng `[aop] before issue` trước dòng business.
3. `new ReceiptService()` trong `DemoMain` tạo object độc lập, không phải Spring bean; `AopUtils.isAopProxy(unmanaged)` cho `false`.
4. `unmanaged.issue("INV-02")` gọi trực tiếp object tự tạo nên **không có dòng `[aop]`** cho `INV-02`.
5. Không có gì sai với method `issue()` của object unmanaged: Java vẫn gọi method và in ra kết quả. Phần khác biệt là **hạ tầng proxy AOP không tham gia**.

Nếu bỏ `@Component` khỏi `ReceiptAspect` mà không đăng ký aspect bằng `@Bean`, aspect không được phát hiện; target có thể vẫn là Spring bean nhưng không có advice tương ứng. Nếu bỏ `@Service` khỏi `ReceiptService` mà không thay bằng `@Bean`, ứng dụng sẽ không có bean để inject vào `CheckoutWorkflow` và quá trình khởi tạo context sẽ lỗi khi cần dependency.

### Cùng một class, thay đường đăng ký bằng `@Bean`

Để sử dụng factory method thay component scan, **bỏ `@Service` khỏi `ReceiptService`** và thêm phương thức sau vào `AppConfig`:

```java
@Bean
public ReceiptService receiptService() {
    return new ReceiptService();
}
```

Đoạn trên là **fragment đặt bên trong class `@Configuration`**, cần import `org.springframework.context.annotation.Bean`. Dù chứa từ khóa `new`, object trả về chính là bean được container quản lý, nên Spring AOP vẫn có cơ hội bọc nó. Đây là ngoại lệ quan trọng giúp phân biệt “dùng `new` trong bean factory” và “tự `new` một service rồi gọi bên ngoài container”.

## 6. Các trường hợp khó hơn cần nhận diện

### 6.1 Một class có hai instance: một managed, một unmanaged

Ví dụ một constructor của Spring bean vẫn tự tạo dependency:

```java
@Component
class ProcessingHandler {
    private final ReceiptService badService = new ReceiptService();

    void handle() {
        badService.issue("BAD");
    }
}
```

`ProcessingHandler` được Spring quản lý **không đồng nghĩa** với `badService` bên trong nó được container quản lý. Đây chỉ là một field tham chiếu đến object Java mới. Cách sửa là đưa `ReceiptService` vào constructor như ở `CheckoutWorkflow`.

### 6.2 `@Autowired` trên một object tự tạo không tự hoạt động

```java
class OutsideContainer {
    @Autowired
    ReceiptService service;
}

// Tạo bên ngoài container:
OutsideContainer object = new OutsideContainer();
```

Ví dụ này là fragment minh họa. Field `service` sẽ không tự được Spring inject chỉ vì có `@Autowired`. Lý do cùng gốc với AOP: object không đi qua cơ chế tạo/cấu hình bean thông thường của container.

### 6.3 Có hai bean cùng kiểu

Nếu vừa dùng `@Service` vừa dùng `@Bean` để tạo thêm một `ReceiptService`, injection theo kiểu có thể trở nên không xác định. Nên tránh đăng ký trùng không chủ đích. Khi thực sự cần nhiều implementation hoặc nhiều bean cùng kiểu, đặt tên rõ ràng và dùng `@Qualifier` hay `@Primary` thích hợp. Đối với Spring AOP, mỗi bean được container chọn sẽ có thể có proxy riêng; đừng đánh đồng “một class” với “một Spring bean duy nhất”.

### 6.4 Bean scope và object được tạo nhiều lần

`singleton` mặc định có một bean instance cho mỗi Spring container/bean definition theo scope; không có nghĩa toàn bộ JVM chỉ có một object kiểu đó. `prototype` cho phép container tạo instance mới khi bean được yêu cầu và Spring vẫn xử lý initialization/AOP tương ứng. Nhưng mỗi `new ReceiptService()` ngoài container vẫn không được quản lý chỉ vì có một prototype bean definition cùng class.

### 6.5 Hai `ApplicationContext` hoặc ứng dụng có parent/child context

Một target bean được quản lý trong context A chưa chắc có proxy bởi advisor/aspect cấu hình trong context B. `@EnableAspectJAutoProxy` có phạm vi theo context; ứng dụng có nhiều context cần đăng ký cấu hình tại đúng nơi. Vì thế khi debug phải kiểm tra **reference và context thực tế**, không chỉ tên class.

### 6.6 Lấy target thô rồi gọi trực tiếp

Khi Spring đã tạo proxy, không nên cố lấy target nội bộ từ proxy để gọi business methods (ví dụ qua các API debug nội bộ) rồi thắc mắc advice không chạy. Đường gọi bỏ qua proxy sẽ bỏ qua advice. Nguyên tắc tương tự áp dụng cho self-invocation.

### 6.7 Có ngoại lệ công nghệ nào để xử lý object tạo ngoài container không?

Spring có các API tích hợp cấp thấp như `AutowireCapableBeanFactory` để cấu hình đối tượng được tạo sẵn; `initializeBean(...)` có thể trả về object đã qua hậu xử lý, thậm chí được bọc. Tuy nhiên **bạn phải dùng reference được trả về và quản lý vòng đời cẩn thận**: việc autowire một instance bên ngoài không biến mọi reference đã trỏ tới instance đó thành AOP proxy.

Spring cũng có tích hợp AspectJ weaving cho một số trường hợp domain object tạo ngoài container, nhưng đó **không phải** Spring AOP proxy tiêu chuẩn và đòi hỏi cấu hình riêng. Trong bài này, giải pháp mặc định là đăng ký service thành bean và inject đúng reference từ Spring.

## 7. Checklist khi “aspect không chạy”

| Triệu chứng | Kiểm tra cụ thể |
| --- | --- |
| Service có `@Service` nhưng advice không chạy | Service có được component scan đúng package không? Có thực sự được Spring inject/getBean? |
| Business code dùng `new SomeService()` | Có object unmanaged; thay bằng constructor injection của Spring-managed service. |
| `@Bean` chứa `new` nên lo không có AOP | Object **được trả về từ method @Bean** là managed bean; xem advisor/pointcut và reference consumer dùng. |
| Aspect có `@Aspect` nhưng chưa được nhận diện | Thêm `@Component` hoặc một `@Bean` đăng ký aspect, và bật auto-proxy. |
| Target có bean nhưng `AopUtils.isAopProxy` là `false` | Kiểm tra có advisor nào thực sự khớp và auto-proxy có đang hoạt động trong context đó. |
| Proxy tồn tại nhưng một method không được advice | Xem pointcut, loại proxy, method `final`/`private`, và đường gọi qua `this`. |
| Injection theo concrete class lỗi khi AOP bật | JDK dynamic proxy chỉ thực hiện interface; inject theo interface hoặc cân nhắc class-based proxy phù hợp. |
| Vừa `@Service` vừa `@Bean` cùng kiểu | Kiểm tra bean trùng, `@Qualifier`, `@Primary` và phương thức đăng ký mong muốn. |
| Có nhiều `ApplicationContext` | Kiểm tra target, aspect và auto-proxy cùng thuộc context thích hợp. |
| Self-invocation `this.other()` | Gọi thẳng target, bỏ qua proxy; tách method cần intercept sang Spring bean khác. |

Để xác định nguyên nhân theo thứ tự hợp lý:

1. Tìm nơi object được tạo. Nếu là `new` trực tiếp trong business code, xem đó có phải dependency cần AOP hay không.
2. Nếu đã là bean, kiểm tra bean definition, component scan hoặc `@Bean` và cấu hình của context.
3. Kiểm tra aspect cũng được đăng ký là Spring bean, và auto-proxy đã bật.
4. Trên reference được inject hoặc trả về từ context, dùng `AopUtils.isAopProxy(...)`.
5. Nếu đã là proxy, xác nhận phương thức được gọi **qua reference này** và pointcut khớp.
6. Nếu vẫn không có advice, kiểm tra self-invocation, method accessibility/finality, kiểu proxy và những khác biệt giữa contexts.

## 8. Câu hỏi ôn tập

**1. Gắn `@Service` rồi viết `new Service()` có được AOP tự động không?** Không. `@Service` chỉ giúp Spring phát hiện class khi component scanning; object tạo riêng bằng `new` không tự được Spring quản lý.

**2. Dùng `new` trong phương thức `@Bean` có sai không?** Không. Đây là cách hợp lệ để tạo một instance cho Spring quản lý. Container có thể hậu xử lý và proxy object trả về.

**3. Bean đã được đăng ký có chắc chắn chạy advice?** Không. Còn phải có AOP hạ tầng được bật, aspect/advisor phù hợp, pointcut khớp và lời gọi đi qua proxy.

**4. Controller được Spring quản lý thì mọi field do nó tạo bằng `new` có được Spring quản lý không?** Không. Chỉ những dependency được đăng ký và resolve qua container mới có vòng đời bean tương ứng; object tạo trực tiếp vẫn là object Java.

**5. `this.inner()` trong một bean đã proxy khác gì `new Service().inner()`?** Cả hai có thể bỏ qua advice, nhưng lý do khác nhau: `this.inner()` là self-invocation bypass proxy, còn object tạo bằng `new` ban đầu đã không có proxy.

**6. Nên tổ chức code như thế nào để hạn chế lỗi?** Đăng ký target và aspect thành Spring beans, để Spring tạo consumer, dùng constructor injection cho dependency cần AOP, và đặt ranh giới gọi giữa các bean cho những method cần interception.

## Tài liệu Spring chính thức

- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — @AspectJ support và đăng ký aspects](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/at-aspectj.html)
- [Spring Framework — Classpath Scanning and Managed Components](https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html)
- [Spring Framework — Sử dụng @Bean](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html)
- [Spring Framework — Annotation-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config.html)
- [Spring Framework — Integrating AspectJ with Spring Applications](https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-beans-005">Phân biệt annotation @Aspect với annotation đăng ký component</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `@Aspect` với annotation đăng ký component trong Spring AOP

## 1. Vấn đề cần phân biệt

Trong Spring AOP, một lớp có `@Aspect` **chưa chắc được Spring container quản lý**. Ngược lại, một lớp có `@Component` là ứng viên được Spring component scan đăng ký làm bean nhưng **không tự động trở thành aspect**. Hai annotation giải quyết **hai câu hỏi khác nhau**:

- **`@Aspect`** trả lời: *Lớp này có khai báo các advice/pointcut để Spring AOP nhận diện không?*
- **`@Component`** (hoặc stereotype tương đương) trả lời: *Spring component scanner có nên phát hiện và đăng ký lớp này thành bean không?*

Để advice từ một lớp `@Aspect` được Spring AOP sử dụng, Spring cần thấy lớp đó **dưới dạng bean trong `ApplicationContext`**, đồng thời ứng dụng cần bật hỗ trợ `@AspectJ` auto-proxy. Bean của aspect và bean đích phải được cấu hình để cơ chế AOP áp dụng được.

Bài học chỉ tập trung vào annotation, bean registration và cơ chế phát hiện aspect **trong Spring Framework/Spring Boot**. Những khái niệm Aspect mang tính tổng quát nằm ngoài phạm vi ở đây.

## 2. Hai nhóm annotation có trách nhiệm khác nhau

| Tiêu chí | `@Aspect` | `@Component` / annotation đăng ký component |
| --- | --- | --- |
| Thuộc thư viện | `org.aspectj.lang.annotation.Aspect` (AspectJ) | `org.springframework.stereotype.Component` (Spring) |
| Vai trò chính | Đánh dấu class chứa khai báo aspect để Spring AOP phân tích | Đánh dấu class là ứng viên Spring bean khi component scan |
| Tự làm Spring scan thấy class? | **Không** | **Có**, khi class nằm trong phạm vi scan và đáp ứng điều kiện |
| Tự định nghĩa advice? | Cho Spring biết cần xét các method `@Before`, `@Around`,… bên trong class | **Không** |
| Có đủ để lời gọi target chạy advice? | Không, phải có aspect bean, cơ chế AOP và target proxy phù hợp | Không, phải có advisor/pointcut khớp và lời gọi qua proxy |
| Có thể dùng một mình không? | Có thể khi aspect đã được khai báo bean bằng `@Bean` hoặc cách khác | Có; một component bình thường không nhất thiết là aspect |
| Có thể dùng đồng thời? | **Có** | **Có** |

Hai import cần nhớ:

```java
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
```

**Lưu ý quan trọng:** Spring Framework documentation nói rõ `@Aspect` **không đủ cho autodetection qua classpath scanning**. Bạn cần thêm `@Component` hoặc một custom stereotype hợp lệ theo quy tắc component scanner, **hoặc** đăng ký instance aspect bằng `@Bean`, XML/bean definition. Đây là quy tắc Spring, không phải khác biệt cú pháp giữa hai annotation.

## 3. Ba trường hợp thường gặp

### Trường hợp A: Chỉ có `@Aspect`

```java
package example.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    @Before("execution(* example.service.OrderService.submit(..))")
    public void beforeSubmit() {
        System.out.println("[audit] before submit");
    }
}
```

Nếu cấu hình ứng dụng chỉ dùng `@ComponentScan` và **không có bean definition khác cho lớp này**, thì scanner không đăng ký `AuditAspect`. Kết quả là:

- `AuditAspect` là lớp Java có metadata `@Aspect`.
- **Không có `AuditAspect` bean** trong context do component scan cung cấp.
- Advice trong đó **không được Spring AOP sử dụng** chỉ vì lớp có mặt trên classpath.
- `@EnableAspectJAutoProxy` cũng **không tự biến class ngoài container thành bean**.

Nghĩa là cách viết chỉ có `@Aspect` **không sai**: nó hoạt động nếu bạn **đăng ký lớp thành bean bằng cách khác**, như `@Bean`. Sai lầm là cho rằng annotation ấy tự đăng ký bean.

### Trường hợp B: `@Aspect` + `@Component`

```java
package example.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {
    @Before("execution(* example.service.OrderService.submit(..))")
    public void beforeSubmit() {
        System.out.println("[audit] before submit");
    }
}
```

Đây là mẫu thông dụng:

1. `@Component` cho phép component scanner phát hiện lớp và Spring đăng ký `AuditAspect` thành bean (nếu package được scan).
2. `@Aspect` cho phép Spring AOP phân tích bean ấy để tìm advice, pointcut và advisor.
3. Nếu bật AOP, một bean đích như `OrderService` khớp pointcut có thể nhận proxy; gọi method thông qua proxy khiến advice chạy.

### Trường hợp C: Chỉ có `@Component`, không có `@Aspect`

```java
package example.aop;

import org.springframework.stereotype.Component;

@Component
public class AuditHelper {
    public void log(String event) {
        System.out.println("[helper] " + event);
    }
}
```

`AuditHelper` là Spring bean thông thường, có thể được inject và gọi bình thường. Nó **không được nhận là một `@AspectJ` aspect** chỉ vì class là component. Việc có một method tên `beforeSubmit` cũng không khiến Spring AOP coi nó là advice.

> Quy tắc thực hành: **`@Aspect` mô tả vai trò AOP của class; `@Component` cho phép scanner đăng ký class làm bean.** Spring sử dụng cả hai thông tin ở những bước khác nhau.

## 4. `@Component`, `@Service`, `@Repository` và custom stereotype

Các annotation Spring như `@Service`, `@Repository`, `@Controller` là stereotype có quan hệ meta-annotation với `@Component`; chúng dùng để đánh dấu vai trò bean trong từng tầng ứng dụng. Nếu bạn viết:

```java
@Aspect
@Service
public class AuditAspect {
    // ...
}
```

Thì về **khả năng component scan**, một stereotype được Spring công nhận như `@Service` vẫn có thể khiến lớp được đăng ký thành bean. Tuy nhiên, **`@Component` là lựa chọn diễn đạt rõ hơn** cho một aspect kỹ thuật không phải nghiệp vụ service. Đừng dùng `@Service` chỉ để chữa lỗi Spring không tìm thấy aspect.

Ngược lại, bean đích thường được định nghĩa bằng annotation thể hiện đúng vai trò:

```java
@Service
public class OrderService {
    public void submit() {
        System.out.println("order submitted");
    }
}
```

Sự có mặt của `@Service` không làm `OrderService` thành aspect. Nó là **target bean** có thể được advice can thiệp sau khi Spring AOP tạo proxy phù hợp.

### Custom stereotype

Bạn cũng có thể định nghĩa stereotype riêng được meta-annotate bằng `@Component`:

```java
package example.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.stereotype.Component;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface InfrastructureComponent {
}
```

Và sử dụng:

```java
// Minh họa lớp dùng custom stereotype; các method advice được lược bớt.
@Aspect
@InfrastructureComponent
public class AuditAspect {
}
```

Khi scanner nhận diện `InfrastructureComponent` theo meta-annotation, lớp là ứng viên bean. `@Aspect` vẫn có trách nhiệm riêng. Custom stereotype chỉ nên tạo khi dự án có ngữ nghĩa thực sự cần dùng lại, không cần thiết cho ví dụ nhỏ.

## 5. Ví dụ Java 17+ chạy được: so sánh trực tiếp các trường hợp

Ví dụ Spring Framework thuần sử dụng Maven, Spring 6.2.19 và AspectJ Weaver 1.9.25.1. Mục đích là quan sát chính xác **aspect class nào trở thành bean, service có phải proxy và advice có chạy hay không**.

### 5.1. Cấu trúc project

```text
spring-aspect-component-demo/
├── pom.xml
└── src/main/java/example/
    ├── AppConfig.java
    ├── OrderService.java
    ├── AuditAspect.java
    ├── InvisibleAspect.java
    ├── AuditHelper.java
    └── Main.java
```

### 5.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-aspect-component-demo</artifactId>
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

import org.springframework.stereotype.Service;

@Service
public class OrderService {
    public void submit() {
        System.out.println("service: submit");
    }
}
```

**`AuditAspect.java`: có cả hai annotation**

```java
package example;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {
    @Before("execution(* example.OrderService.submit(..))")
    public void beforeSubmit() {
        System.out.println("registered aspect: before");
    }
}
```

**`InvisibleAspect.java`: chỉ có `@Aspect`**

```java
package example;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class InvisibleAspect {
    @Before("execution(* example.OrderService.submit(..))")
    public void beforeSubmit() {
        System.out.println("invisible aspect: before");
    }
}
```

**`AuditHelper.java`: chỉ có `@Component`**

```java
package example;

import org.springframework.stereotype.Component;

@Component
public class AuditHelper {
    public String label() {
        return "helper bean";
    }
}
```

**`AppConfig.java`**

```java
package example;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "example")
@EnableAspectJAutoProxy
public class AppConfig {
}
```

**`Main.java`**

```java
package example;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            System.out.println("AuditAspect beans: "
                + context.getBeanNamesForType(AuditAspect.class).length);
            System.out.println("InvisibleAspect beans: "
                + context.getBeanNamesForType(InvisibleAspect.class).length);
            System.out.println("AuditHelper beans: "
                + context.getBeanNamesForType(AuditHelper.class).length);

            OrderService service = context.getBean(OrderService.class);
            System.out.println("service is AOP proxy: " + AopUtils.isAopProxy(service));
            service.submit();
        }
    }
}
```

Chạy tại thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.Main
```

Các dòng `System.out.println` của ví dụ, theo thứ tự:

```text
AuditAspect beans: 1
InvisibleAspect beans: 0
AuditHelper beans: 1
service is AOP proxy: true
registered aspect: before
service: submit
```

**Giải thích output:**

1. `AuditAspect` vừa có `@Aspect` vừa có `@Component`. Scanner đăng ký một bean, rồi Spring AOP dùng metadata aspect để tạo advice. Khi `submit()` được gọi qua service proxy, dòng `registered aspect: before` xuất hiện.
2. `InvisibleAspect` nằm ngay trong package `example` nhưng không có `@Component` hoặc khai báo `@Bean`. Vì vậy `InvisibleAspect beans: 0`; advice của nó không được đăng ký và dòng `invisible aspect: before` không xuất hiện.
3. `AuditHelper` có `@Component` nên có một bean, nhưng không có `@Aspect` và không tạo advice.
4. `@EnableAspectJAutoProxy` bật tính năng tìm aspect beans và tạo proxy. `service is AOP proxy: true` chứng minh target đang được Spring can thiệp.

Kết quả dựa trên **đúng các file/cấu hình** trong ví dụ. Nếu thêm bean definition cho `InvisibleAspect` hoặc sửa scan/configuration thì output sẽ thay đổi.

## 6. Không dùng `@Component` vẫn đăng ký được aspect bằng `@Bean`

Trong nhiều dự án, không muốn class aspect phụ thuộc vào annotation `@Component`, hoặc cần tạo aspect qua factory để inject các tham số cấu hình. `@Bean` cũng là cách Spring container quản lý aspect.

Để thử **biến thể** từ ví dụ mục 5, giữ `InvisibleAspect.java` (chỉ có `@Aspect`). Thay `AppConfig.java` bằng:

```java
package example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "example")
@EnableAspectJAutoProxy
public class AppConfig {
    @Bean
    public InvisibleAspect invisibleAspect() {
        return new InvisibleAspect();
    }
}
```

Các lớp còn lại giữ nguyên. Chạy lại chương trình mục 5 sẽ cho:

```text
AuditAspect beans: 1
InvisibleAspect beans: 1
AuditHelper beans: 1
service is AOP proxy: true
invisible aspect: before
registered aspect: before
service: submit
```

**Lưu ý về thứ tự advice:** Khi nhiều aspect có cùng loại advice và không cấu hình `@Order`, **thứ tự của hai dòng advice không được bảo đảm**. Hai dòng có thể đảo vị trí, còn ý nghĩa quan trọng là cả hai đều xuất hiện trước dòng `service: submit`. Đừng viết test phụ thuộc thứ tự ngẫu nhiên đó.

Lý do thay đổi: `@Bean` đăng ký instance `InvisibleAspect` thành Spring bean; `@Aspect` trên class vẫn được Spring AOP nhận diện. `new InvisibleAspect()` **trong factory method `@Bean`** khác với việc tùy ý tạo `new InvisibleAspect()` bên ngoài container: chỉ instance được trả về từ `@Bean` mới là bean đã đăng ký.

Nếu bạn chỉ muốn một aspect, hãy bỏ hoặc vô hiệu hóa `AuditAspect` khi chuyển sang `@Bean`. **Không** khai báo trùng cùng một aspect qua cả `@Component` và `@Bean` nếu không có chủ đích; hai instance có thể tạo advice lặp và gây nhập nhằng khi inject.

## 7. Bật Spring AOP là bước riêng

Đăng ký aspect bean và bật hệ thống AOP là hai bước khác nhau.

Trong Spring Framework thuần, bạn thường dùng:

```java
@Configuration
@ComponentScan("example")
@EnableAspectJAutoProxy
public class AppConfig {
}
```

- `@ComponentScan` tìm những class đủ điều kiện đăng ký bean.
- `@EnableAspectJAutoProxy` kích hoạt xử lý Spring AOP dạng `@AspectJ` và tự động tạo proxy cho bean phù hợp.
- AspectJ Weaver cần có trên classpath để Spring xử lý biểu thức pointcut.
- `@EnableAspectJAutoProxy` **không tự đăng ký** class chỉ có `@Aspect` nằm ngoài bean definitions.

Với ứng dụng **Spring Boot**, khi dependency AOP/AspectJ và auto-configuration tương ứng hoạt động, Boot có thể bật hỗ trợ AspectJ auto-proxy mặc định mà không cần bạn thêm `@EnableAspectJAutoProxy`. Tuy nhiên, vẫn cần **đăng ký aspect làm bean**.

Tên starter thường dùng:

```xml
<!-- Spring Boot 3.x, bên trong <dependencies>; version lấy từ Boot BOM. -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

```xml
<!-- Spring Boot 4.x, bên trong <dependencies>; version lấy từ Boot BOM. -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aspectj</artifactId>
</dependency>
```

Đây là hai **snippet phụ thuộc theo phiên bản Boot**, không phải một POM hoàn chỉnh và không nên thêm đồng thời chỉ để “chắc chắn”. Boot BOM/parent chịu trách nhiệm giữ dependency Spring/AspectJ tương thích.

### Package scan trong Spring Boot

Giả sử main class đặt tại `com.acme.app.Application`, còn aspect đặt tại `com.acme.infrastructure.AuditAspect`. Component scan mặc định khởi đầu từ package chứa main class và các package con; `com.acme.infrastructure` **không thuộc** phạm vi `com.acme.app`. Việc class có cả `@Aspect @Component` vẫn không làm nó thành bean nếu không quét đúng package.

Hai cách giải quyết thường gặp là đặt aspect vào `com.acme.app.aop` (thuộc phạm vi scan), hoặc cấu hình scan rõ ràng qua `@SpringBootApplication(scanBasePackages = {"com.acme.app", "com.acme.infrastructure"})` khi có lý do tổ chức package. Với ứng dụng lớn, cũng có thể dùng `@Import` hoặc `@Bean` để đăng ký aspect một cách tường minh.

## 8. Những annotation khác không thay thế `@Aspect`

Phân biệt tiếp một số annotation có thể gặp:

| Annotation | Có đăng ký bean không? | Có định nghĩa vai trò aspect không? | Lưu ý |
| --- | --- | --- | --- |
| `@Aspect` | Không tự đăng ký qua scan | **Có** | Cần có aspect bean |
| `@Component` | Có, nếu được scan | Không | Dùng cùng `@Aspect` là cách phổ biến |
| `@Service` | Có, nếu được scan | Không | Dùng cho service nghiệp vụ; cũng là một component |
| `@Repository` | Có, nếu được scan | Không | Dùng cho tầng lưu trữ, có cơ chế exception translation liên quan |
| `@Configuration` | Được Spring nhận diện như configuration class khi đăng ký/quét hợp lệ | Không | Dùng để khai báo `@Bean`/cấu hình context |
| `@Bean` | **Có**, đối với giá trị trả về từ method `@Bean` | Không | Có thể tạo bean từ một class vốn có `@Aspect` |
| `@ComponentScan` | Chỉ thị quét package để tìm ứng viên | Không | Không có nghĩa mọi class trên classpath đều được đăng ký |
| `@EnableAspectJAutoProxy` | Kích hoạt cơ chế Spring AOP auto-proxy | Không tự biến class thành aspect bean | Cần aspect bean, pointcut khớp, target proxy |

`@Aspect` **không phải** một annotation `@Component` được meta-annotate: khác nhau ngay ở nguồn thư viện và trách nhiệm. Ngay cả khi một custom annotation được meta-annotate bằng `@Component`, nó chỉ thay phần **component registration**, không thay phần đánh dấu `@Aspect`.

## 9. Aspect bean không đồng nghĩa với target proxy

Một nhầm lẫn khác: sau khi thêm `@Component`, nhiều người kiểm tra xem **aspect bean** có phải AOP proxy hay không. Spring AOP có một quy tắc riêng:

- Lớp có `@Aspect` **không được Spring auto-proxy như target của các aspect khác**.
- Việc `AopUtils.isAopProxy(context.getBean(AuditAspect.class))` thường trả về `false` là **bình thường**.
- Cần kiểm tra **target bean** (ví dụ `OrderService`) để xác minh proxy và quan sát lời gọi advice.

Ví dụ chẩn đoán trong hàm có `ApplicationContext context`:

```java
// Đoạn minh họa dùng các lớp đã được khai báo ở ví dụ mục 5.
Object aspect = context.getBean(AuditAspect.class);
Object service = context.getBean(OrderService.class);

System.out.println(AopUtils.isAopProxy(aspect));  // false: expected
System.out.println(AopUtils.isAopProxy(service)); // true: nếu pointcut khớp
```

Đừng suy ra `@Aspect` “không hoạt động” chỉ vì **chính aspect bean không phải proxy**.

Ngoài ra, nếu một target được tạo bằng `new OrderService()` ở code bình thường, instance đó không tự động được proxy dù một Spring-managed `OrderService` khác đã có proxy. Đây là giới hạn của **Spring AOP proxy-based**, không liên quan tới việc aspect có `@Component` hay không.

## 10. Chẩn đoán khi aspect không được Spring nhận diện

Khi advice không chạy, kiểm tra từng tầng theo thứ tự để tránh sửa nhầm:

1. **Có aspect bean chưa?** Dùng `context.getBeanNamesForType(AuditAspect.class)`; nếu mảng rỗng, kiểm tra `@Component`/`@Bean`, `@ComponentScan`, package, profile, điều kiện tạo bean và đúng `ApplicationContext`.
2. **Có đánh dấu `@Aspect` đúng trên class không?** Import `org.aspectj.lang.annotation.Aspect`; một component chứa method tên `before` không tự được Spring coi là advice.
3. **Đã bật hỗ trợ AOP chưa?** Kiểm tra `@EnableAspectJAutoProxy` trong Spring Framework thuần, hoặc cấu hình auto-configuration/AspectJ dependency của Spring Boot.
4. **Target có phải bean được proxy không?** Lấy target qua Spring và dùng `AopUtils.isAopProxy(target)`; đừng kiểm tra aspect thay target.
5. **Lời gọi có thực sự đi qua proxy không?** Lời gọi nội bộ `this.method()` trong target bỏ qua advice áp dụng riêng cho method được gọi nội bộ.
6. **Pointcut có match đúng không?** Kiểm tra method, package, signature, annotation và điều kiện khả năng proxy phương thức (ví dụ method `final` không thể bị CGLIB subclass override).
7. **Có aspect bean trùng không?** Nếu đăng ký cùng aspect class qua `@Component` và `@Bean`, hoặc có nhiều cấu hình scan chồng nhau, hãy đọc danh sách bean và đếm số lần advice chạy.

Một kiểm tra gọn với context của ví dụ:

```java
String[] names = context.getBeanNamesForType(AuditAspect.class);
System.out.println(java.util.Arrays.toString(names));

OrderService target = context.getBean(OrderService.class);
System.out.println(AopUtils.isAopProxy(target));
```

Nếu `names` rỗng, chưa nên chỉ sửa biểu thức pointcut. Nếu có aspect bean và target proxy nhưng advice không hiện, khi đó mới xem pointcut/đường gọi/thứ tự xử lý.

### Bảng tình huống nhanh

| Khai báo | Component scan tìm được aspect bean? | Advice có thể chạy qua Spring AOP? |
| --- | --- | --- |
| Chỉ `@Aspect`, không `@Bean` | **Không** | Không, vì chưa đăng ký aspect bean |
| `@Aspect @Component`, nằm trong package được scan | **Có** | Có, nếu bật AOP và pointcut/target phù hợp |
| `@Aspect @Component`, nằm ngoài package scan | **Không** nếu không có cách đăng ký khác | Không, trừ khi đăng ký bean theo cách khác |
| `@Aspect` + `@Bean` trả về instance aspect | Không cần component scan | Có, nếu bật AOP và target phù hợp |
| Chỉ `@Component`, không `@Aspect` | Có | Không tạo advice theo `@AspectJ` từ class ấy |
| Có aspect bean nhưng không bật AOP | Có | Không có Spring `@AspectJ` auto-proxy tương ứng |
| Có aspect bean, target tự `new` ở ngoài container | Có | Không can thiệp target đó bằng Spring proxy |

## 11. Tự kiểm tra

**Câu 1.** `@Aspect` có làm Spring component scan tự nhận một lớp không?

**Đáp án:** Không. Nếu không có `@Component`/stereotype tương đương hoặc bean definition khác, Spring sẽ không tự đăng ký lớp như một bean chỉ vì `@Aspect`.

**Câu 2.** Tại sao `@Aspect @Component` vẫn có thể không chạy advice?

**Đáp án:** Có thể lớp nằm ngoài phạm vi scan, thiếu hỗ trợ AOP, thiếu AspectJ trên classpath, pointcut không khớp, target không được proxy hoặc lời gọi không qua proxy. Hai annotation chỉ giải quyết hai bước đầu về metadata aspect và việc đăng ký bean (khi scan thực sự diễn ra).

**Câu 3.** Có thể bỏ `@Component` khỏi aspect nhưng vẫn dùng Spring AOP không?

**Đáp án:** Có. Giữ `@Aspect` và đăng ký aspect bằng `@Bean` (hoặc bean definition hợp lệ); bật Spring AOP.

**Câu 4.** `@Service` thay `@Component` trên lớp aspect có được không?

**Đáp án:** Với component scanner, `@Service` cũng là Spring stereotype và có thể đăng ký bean. Tuy vậy, nên chọn `@Component` cho aspect cơ sở hạ tầng để thể hiện trách nhiệm chính xác.

**Câu 5.** Bean aspect đã được đăng ký nhưng `AopUtils.isAopProxy(aspect)` là `false`. Có phải lỗi không?

**Đáp án:** Không. Spring AOP loại aspect class khỏi auto-proxying như target của aspect khác. Hãy kiểm tra proxy của **service target** và việc advice thực thi.

## 12. Tài liệu chính thức

- [Spring Framework — Declaring an Aspect](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/at-aspectj.html): giải thích rõ `@Aspect` không đủ để autodetect qua classpath; yêu cầu `@Component` hoặc bean definition tương đương và việc aspect không là target auto-proxy.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): cách bật `@EnableAspectJAutoProxy` và yêu cầu classpath.
- [Spring Framework — Classpath Scanning and Managed Components](https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html): stereotype `@Component`, `@Service`, `@Repository` và custom stereotype.
- [Spring Boot — Aspect-Oriented Programming](https://docs.spring.io/spring-boot/reference/features/aop.html): Spring Boot auto-configuration hỗ trợ AOP và proxy.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): cơ chế proxy và các giới hạn lời gọi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-beans-006">Aspect như bean hạ tầng và giới hạn áp dụng advice lên chính aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Aspect như bean hạ tầng và giới hạn áp dụng advice lên chính aspect

## 1. Hiểu đúng hai vai trò của một `@Aspect` trong Spring

Trong Spring AOP, một lớp mang `@Aspect` có thể được đăng ký **như một Spring bean** để Spring tìm, tạo, inject dependency và lấy các khai báo advice/pointcut của nó. Tuy nhiên, khi Spring AOP tự động chọn đối tượng để tạo proxy, **bean được đánh dấu `@Aspect` được loại khỏi danh sách target của auto-proxying**. Vì vậy, một aspect không thể được advice của một aspect khác chặn theo cơ chế **Spring AOP @AspectJ auto-proxy**.

Đây là một khác biệt dễ bỏ sót:

- **Là bean:** aspect có thể được Spring container quản lý vòng đời, cấu hình, inject dependency, và được lấy ra bằng `getBean(...)` như các bean thông thường.
- **Có vai trò hạ tầng AOP:** aspect cung cấp advisor/advice để Spring chèn vào **proxy của các target bean khác**; nó không trở thành đối tượng được Spring AOP auto-proxy chỉ vì pointcut của aspect khác khớp tên method trong aspect.
- **Không đồng nghĩa với annotation `@Infrastructure` hoặc interface `AopInfrastructureBean`:** gọi aspect là “bean hạ tầng” ở đây nhằm mô tả **vai trò**. Một class `@Aspect` không bắt buộc phải implements marker interface nào mới bị loại khỏi auto-proxying.

**Quy tắc cần nhớ:** *Aspect bean được Spring quản lý và cung cấp advice; nhưng Spring AOP không tự áp advice của aspect khác lên chính bean `@Aspect` đó.* Quy tắc này thuộc cơ chế proxy Spring AOP, không phải khẳng định về mọi công nghệ bytecode weaving hoặc mọi proxy được tạo thủ công.

Bài tập trung hoàn toàn vào Spring Framework và Spring AOP; không mở rộng sang lý thuyết Aspect tổng quát.

## 2. `@Aspect` không tự đăng ký thành Spring bean

Mã sau mô tả một aspect có advice:

```java
@Aspect
public class AuditAspect {
    @Before("execution(* com.example.app.service..*.*(..))")
    public void audit() {
        System.out.println("AUDIT");
    }
}
```

Đặt `@Aspect` lên class **chỉ khai báo vai trò aspect**, chưa bảo đảm Spring container tạo instance và sử dụng nó.

Có hai cách phổ biến để đăng ký:

**Cách A — component scanning:**

```java
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {
    @Before("execution(* com.example.app.service..*.*(..))")
    public void audit() {
        System.out.println("AUDIT");
    }
}
```

Với cách này, package của class phải nằm trong vùng Spring component-scan. `@Component` giúp Spring phát hiện bean; `@Aspect` giúp cơ chế @AspectJ nhận ra các khai báo advice.

**Cách B — đăng ký bằng `@Bean`:**

```java
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
    @Bean
    public AuditAspect auditAspect() {
        return new AuditAspect();
    }
}
```

Đây là đoạn cấu hình minh họa, giả định class `AuditAspect` đã định nghĩa như ví dụ và các import Spring phù hợp. Trong cấu hình Spring Framework thuần, `@EnableAspectJAutoProxy` bật xử lý aspect bean kiểu `@AspectJ`. Ứng dụng Spring Boot có `spring-boot-starter-aop` thường được Boot tự bật hỗ trợ AOP khi đủ điều kiện classpath; không nhất thiết phải tự thêm `@EnableAspectJAutoProxy`.

**Không đặt `@Aspect` lên method `@Bean` để kỳ vọng nó biến object thành aspect.** Spring nhận diện loại aspect từ class thực tế mang `@Aspect`. Tương tự, `@Aspect` đơn lẻ không thay thế `@Component` khi dùng component scanning.

## 3. Spring xử lý aspect bean khác target bean ở đâu?

Một cách nhìn đơn giản về chu kỳ khởi tạo Spring:

```text
ApplicationContext đăng ký bean definitions
             |
             v
Bật auto-proxy @AspectJ (ví dụ @EnableAspectJAutoProxy)
             |
             v
Tìm các bean class có @Aspect để tạo danh sách advisors
             |
             +-------------------------------+
             |                               |
             v                               v
       Aspect bean                      Target bean khác
       (@Aspect)                        (ví dụ OrderService)
             |                               |
             |                               v
             |                        Có advice áp dụng?
             |                               |
             v                         Có ---+--- Không
   Spring AOP loại khỏi                     |         |
   @AspectJ auto-proxying                   v         v
             |                            Proxy   Bean bình thường
             v                              |
   Instance aspect thông thường             v
   trong Spring context                Client gọi proxy
                                              |
                                              v
                                    advice -> target method
```

Về mặt kỹ thuật, Spring Framework có auto-proxy creator, chẳng hạn `AnnotationAwareAspectJAutoProxyCreator`, làm công việc tìm advisor, đánh giá target bean và tạo AOP proxy khi cần. Trong việc xử lý aspect, Spring nhận diện class mang `@Aspect` là nguồn khai báo advice và **bỏ qua việc auto-proxy chính aspect đó**. Cơ chế này ngăn việc một aspect bị lời khuyên của aspect khác tác động như business target.

**Điều gì không đổi?** Spring vẫn inject constructor dependencies cho aspect, vẫn có thể gọi các method public thường của aspect qua reference tới bean, và vẫn sử dụng advice của nó khi method execution trên target bean phù hợp pointcut.

**Điều gì không được kỳ vọng?** Nếu `LoggingAspect` có pointcut khớp method `AuditAspect.maintenance()`, Spring AOP auto-proxy thông thường sẽ không biến `AuditAspect` thành target proxy để chạy advice của `LoggingAspect`.

### Ba khái niệm cần phân biệt

| Đối tượng | Vai trò | Auto-proxy Spring AOP thường làm gì? |
| --- | --- | --- |
| `@Aspect` bean | Nguồn cung cấp pointcut/advice | Được nhận diện để tạo advisor; **không trở thành target cho advice của aspect khác** |
| Service/repository/controller bean bình thường | Target tiềm năng của advice | Có thể được proxy nếu advisor phù hợp và bean đủ điều kiện |
| Hạ tầng đặc biệt như `BeanPostProcessor` hoặc `AopInfrastructureBean` | Hỗ trợ vận hành container/AOP | Có các quy tắc không eligible cho auto-proxy riêng; không được đánh đồng hoàn toàn với mọi aspect bean |

Chữ “hạ tầng” mô tả vai trò vận hành. Đừng suy rằng mọi bean do infrastructure sử dụng đều có cùng cơ chế hoặc cùng nguyên nhân bị loại khỏi proxy.

## 4. Ví dụ runnable: advice chạy trên service nhưng không chạy trên method của aspect

Ví dụ Java 17+ chạy bằng Maven, Spring Framework 6.2.6. Chương trình đăng ký **hai aspect bean**:

- `AuditAspect` có advice chạy trước method `doWork()` của `WorkService`; đồng thời có method bình thường `healthCheck()`.
- `ObserveAspect` có advice nhắm vào mọi method `healthCheck()` phù hợp pointcut.
- Khi client gọi `WorkService.doWork()` qua context, `AuditAspect` advice chạy.
- Khi client gọi `AuditAspect.healthCheck()` qua context, advice của `ObserveAspect` **không chạy**, dù biểu thức pointcut phù hợp tên method. Lý do là bản thân `AuditAspect` không được Spring AOP auto-proxy.

Cấu trúc project:

```text
aspect-bean-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── AspectBeanDemo.java
```

**Tệp `pom.xml`:**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>aspect-bean-demo</artifactId>
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
                    <mainClass>demo.AspectBeanDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**Tệp `src/main/java/demo/AspectBeanDemo.java`:**

```java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class AspectBeanDemo {

    public static class WorkService {
        public void doWork() {
            System.out.println("SERVICE doWork");
        }
    }

    @Aspect
    public static class AuditAspect {
        @Before("execution(* demo..*.doWork(..))")
        public void beforeWork() {
            System.out.println("AUDIT before doWork");
        }

        public void healthCheck() {
            System.out.println("ASPECT healthCheck");
        }
    }

    @Aspect
    public static class ObserveAspect {
        @Before("execution(* demo..*.healthCheck(..))")
        public void beforeAspectMethod() {
            System.out.println("OBSERVE before healthCheck");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    public static class Config {
        @Bean
        public WorkService workService() {
            return new WorkService();
        }

        @Bean
        public AuditAspect auditAspect() {
            return new AuditAspect();
        }

        @Bean
        public ObserveAspect observeAspect() {
            return new ObserveAspect();
        }
    }

    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(Config.class)) {

            WorkService service = context.getBean(WorkService.class);
            AuditAspect aspect = context.getBean(AuditAspect.class);

            System.out.println("SERVICE_PROXY="
                    + AopUtils.isAopProxy(service));
            System.out.println("ASPECT_PROXY="
                    + AopUtils.isAopProxy(aspect));

            service.doWork();
            aspect.healthCheck();
        }
    }
}
```

Chạy tại thư mục `aspect-bean-demo`:

```bash
mvn -q compile exec:java
```

Các dòng do chương trình in, theo thứ tự:

```text
SERVICE_PROXY=true
ASPECT_PROXY=false
AUDIT before doWork
SERVICE doWork
ASPECT healthCheck
```

Nhận xét:

1. `SERVICE_PROXY=true` cho biết `WorkService` đã nhận Spring AOP proxy vì advice trong `AuditAspect` khớp `doWork()`.
2. `ASPECT_PROXY=false` cho biết bean `AuditAspect` không được @AspectJ auto-proxy, dù `ObserveAspect` có pointcut khớp tên `healthCheck()`.
3. Lệnh `service.doWork()` đi qua proxy nên in `AUDIT before doWork` trước khi target in `SERVICE doWork`.
4. Lệnh `aspect.healthCheck()` là lời gọi method thông thường trên aspect bean; chỉ in `ASPECT healthCheck`. **Không xuất hiện** dòng `OBSERVE before healthCheck`.
5. Cả hai aspect đều hiện diện trong container qua `@Bean`. Việc advice không chạy trên aspect **không phải** do quên đăng ký `ObserveAspect`.

Trong Maven hoặc Spring runtime có thể còn thông báo từ tool/logging ngoài các dòng do ví dụ tự in; chúng không làm thay đổi ý nghĩa các dòng nêu trên.

**Thử mở rộng:** tạo một Spring bean bình thường tên `HealthService` có method `healthCheck()` public, đăng ký `@Bean`, lấy từ context và gọi method này. Nếu pointcut `ObserveAspect` khớp method, lời gọi sẽ được advice, bởi bean đó là target bình thường. Đây là cách kiểm tra độc lập rằng pointcut hợp lệ và aspect đã đăng ký.

## 5. Vì sao gọi aspect là “bean hạ tầng” mà vẫn cần inject dependency?

Aspect nhiều khi cần một cộng tác viên như `AuditSink`, `Clock` hoặc `MetricsRecorder`. Spring vẫn có thể tạo bean và inject dependency qua constructor:

```java
public interface AuditSink {
    void record(String event);
}

@Aspect
@Component
public class AuditAspect {
    private final AuditSink sink;

    public AuditAspect(AuditSink sink) {
        this.sink = sink;
    }

    @Before("execution(* com.example.app.service..*.*(..))")
    public void beforeServiceCall() {
        sink.record("service-called");
    }
}
```

Đoạn này **minh họa một thành phần trong ứng dụng**, không phải file chạy độc lập: cần implementation của `AuditSink`, các import, và cấu hình scan phù hợp.

Spring quản lý `AuditAspect` tương tự nhiều bean khác ở khâu tạo và cấp dependency. Nhưng khi `sink.record(...)` được gọi, lời gọi tới `sink` **có thể** đi qua Spring AOP proxy của bean `AuditSink` nếu bean ấy được advice và được inject qua proxy. Điều đó **khác** với việc đưa chính `AuditAspect` vào proxy để advice chặn method của nó.

### Nên đặt trách nhiệm nào ở aspect?

Trong Spring AOP, aspect nên chủ yếu điều phối hành vi cắt ngang ở ranh giới lời gọi: xác định join point, đọc tham số cần thiết, gọi cộng tác viên, tiếp tục gọi target khi dùng around advice. Nếu phải làm xử lý nghiệp vụ hoặc IO dài, nên cân nhắc tách logic sang Spring bean chuyên trách và inject vào aspect.

Một lợi ích của cách tách này là bạn có thể kiểm thử, thay thế, hoặc áp các interceptor phù hợp cho **bean chuyên trách**, thay vì cố gắng dùng một aspect khác để chặn method trên `@Aspect` bean.

### Vấn đề recursion và pointcut quá rộng

Nếu một advice gọi `auditSink.record()`, trong khi pointcut của chính advice lại bao trùm cả `AuditSink.record()`, ứng dụng có thể phát sinh chuỗi gọi lặp không mong muốn. Với các bean cộng tác, chọn pointcut đủ hẹp, tách package theo trách nhiệm hoặc dùng điều kiện `!within(...)` / `!bean(...)` phù hợp trong Spring AOP.

Đây là vấn đề thiết kế **đường đi lời gọi đến bean được proxy**, khác với quy tắc Spring bỏ qua chính `@Aspect` bean khi auto-proxy.

## 6. Lưu ý về trạng thái và vòng đời của aspect bean

Spring thường đăng ký `@Aspect` bean dưới singleton scope, trừ khi ứng dụng cấu hình khác. Vì singleton được nhiều lời gọi cùng sử dụng, lưu trạng thái thay đổi theo request trong field của aspect có thể dẫn tới race condition hoặc lẫn dữ liệu giữa các thread:

```java
@Aspect
@Component
public class RequestTraceAspect {
    private String currentUser; // DỄ SAI nếu nhiều request chạy đồng thời
}
```

Field `currentUser` ở đây thuộc instance aspect chung, không tự động là dữ liệu riêng cho mỗi request. Lấy dữ liệu từ method arguments, Spring request context/security context khi phù hợp, hoặc dùng cộng tác viên có scope và cách truyền context đúng yêu cầu. Với async/virtual thread, không mặc định rằng thread-local context được tự động truyền qua mọi boundary.

Điều này không thay đổi khi aspect không được auto-proxy: nó vẫn là đối tượng có state và lifecycle do container quản lý.

Lưu ý riêng về `@Aspect` và `@Component`: nếu class mang cả hai, `@Component` phục vụ việc scan; `@Aspect` phục vụ nhận diện advisor và áp dụng quy tắc không auto-proxy aspect. Không dùng `@Repository` hoặc `@Service` thay đổi bản chất một `@Aspect` để mong advice của aspect khác chạy trên nó.

## 7. Các nhầm lẫn thường gặp về lời gọi trên aspect

### 7.1. “Aspect là bean thì advice khác chắc chắn phải áp được”

Sai trong Spring AOP @AspectJ auto-proxy. Điều kiện **bean được quản lý** chỉ là một phần của quá trình tạo proxy. Aspect được nhận diện là nguồn advice và chủ động bị loại khỏi tập target khi auto-proxy.

### 7.2. “Cứ viết pointcut đúng tên method của aspect là đủ”

Sai. Pointcut khớp method là cần thiết khi target eligible, nhưng không đảo ngược được quyết định Spring bỏ qua auto-proxy của aspect.

### 7.3. “Đặt `@Order(1)` lên aspect thì aspect sẽ được advice của aspect khác”

Sai. `@Order` điều khiển thứ tự ưu tiên của các aspect/advice cùng tham gia chuỗi interceptor trên **target bean đủ điều kiện**. Nó không biến aspect thành target cho các advice khác.

### 7.4. “Bật `exposeProxy=true` hoặc đổi sang CGLIB sẽ giải quyết”

Sai về bản chất. `exposeProxy` có liên quan đến truy xuất proxy hiện hành trong một invocation đang đi qua proxy; `proxyTargetClass=true` điều khiển lựa chọn proxy JDK/CGLIB. Không lựa chọn nào khiến aspect bean thoát khỏi quy tắc loại trừ @AspectJ auto-proxy.

### 7.5. “Chỉ có `@Before` mới bị giới hạn”

Sai. Vấn đề nằm ở **target bean không được auto-proxy**. Cho dù khai báo `@Around`, `@After`, `@AfterReturning` hay `@AfterThrowing` trong aspect khác, chúng cũng không tự chặn method của `@Aspect` bean khi không có proxy intercept call.

### 7.6. “Aspect không thể dùng một service được advice”

Sai. Aspect có thể inject và gọi Spring service bean. Nếu service nhận được Spring AOP proxy và pointcut khớp, interceptor của service có thể chạy bình thường. **Bean service** được advice, không phải **aspect**.

### 7.7. “Đã mang `@Aspect` thì không thể nào có proxy dưới bất kỳ hình thức nào”

Không nên kết luận rộng như vậy. Tài liệu Spring đang nói đến tính **không eligible cho @AspectJ auto-proxying của Spring AOP**. Proxy được tạo thủ công, cơ chế khác hoặc AspectJ weaving có phạm vi/hành vi riêng; chúng nằm ngoài quyết định auto-proxy đang xét. Trong ví dụ của bài, kết quả `AopUtils.isAopProxy(aspect) == false` phản ánh đúng đường thiết lập đã sử dụng.

## 8. Thiết kế và kiểm thử: đặt logic đúng bean

Giả sử bạn muốn đo thời gian ghi audit do `AuditAspect` khởi tạo. Cách cố viết một aspect thứ hai chặn `AuditAspect.writeAudit()` sẽ không đạt mục tiêu với Spring AOP auto-proxy. Hãy chuyển thao tác cần đo sang một collaborator bình thường:

```text
Client -> proxy(BusinessService) -> AuditAspect advice
                                      |
                                      v
                              proxy(AuditWriter)
                                      |
                                      v
                         TimingAspect advice -> AuditWriter.write()
```

Ví dụ khung API:

```java
public interface AuditWriter {
    void write(String message);
}

@Service
public class DatabaseAuditWriter implements AuditWriter {
    @Override
    public void write(String message) {
        // Ghi dữ liệu audit, ví dụ theo cơ chế được ứng dụng lựa chọn.
    }
}

@Aspect
@Component
public class AuditAspect {
    private final AuditWriter writer;

    public AuditAspect(AuditWriter writer) {
        this.writer = writer;
    }

    @Before("execution(* com.example.app.service..*.*(..))")
    public void auditCall() {
        writer.write("Business method invoked");
    }
}

@Aspect
@Component
public class TimingAspect {
    @Around("execution(* com.example.app.audit.DatabaseAuditWriter.write(..))")
    public Object timeAudit(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            System.out.println("AUDIT_WRITE_NS=" +
                    (System.nanoTime() - start));
        }
    }
}
```

Đây là **mẫu kiến trúc, không phải chương trình standalone**: còn cần package declarations, imports và context Spring thích hợp. Điểm cần nắm là `DatabaseAuditWriter` là một Spring bean **không mang `@Aspect`**, nên Spring có thể tạo proxy cho bean này để `TimingAspect` advice `write(...)`. Nếu thay `AuditWriter` bằng method `writeAudit()` đặt trực tiếp trong `AuditAspect`, advice của `TimingAspect` không tự chặn method đó theo Spring AOP @AspectJ auto-proxy.

**Chú ý:** class `DatabaseAuditWriter` sử dụng annotation `@Service`, vì vậy phải nằm trong vùng component scan hoặc được đăng ký tương đương. Với kiểu proxy JDK, collaborator thường được inject theo interface `AuditWriter` như minh họa. Ví dụ `timeAudit` dành cho method `void` vẫn dùng `Object` theo chữ ký around advice; Spring xử lý return value phù hợp khi tiếp tục invocation. Giá trị thời gian phụ thuộc máy và workload, nên đoạn này không có stdout cố định.

Một cách khác khi chỉ muốn quan sát nội bộ `AuditAspect` là gọi trực tiếp một timer/helper trong code của chính aspect, hoặc đo trong test. Việc đo đó không cần phải được thực hiện bằng advice của aspect khác.

## 9. Checklist khi thấy aspect khác không chặn được method của aspect

1. **Đúng bean và đúng annotation?** Class đang gọi mang `@Aspect` hay chỉ là Spring service bình thường? Nếu mang `@Aspect`, kiểm tra quy tắc loại trừ khỏi Spring AOP auto-proxy trước khi kiểm pointcut.
2. **Đã đăng ký aspect?** Có `@Bean`, `@Component` kèm component-scan, hoặc khai báo tương đương chưa? Chỉ có `@Aspect` chưa đủ để đăng ký.
3. **Đã bật @AspectJ support?** Với Spring thuần, cấu hình `@EnableAspectJAutoProxy` và dependency liên quan; với Boot, xác định starter/auto-configuration hiệu lực.
4. **Có đang kiểm tra bean thật từ context?** Dùng `AopUtils.isAopProxy(context.getBean(...))` để biết có Spring AOP proxy trên reference thực tế.
5. **Pointcut có chặn target bean khác không?** Thử một service bình thường khớp cùng loại biểu thức. Nếu advice chạy trên service nhưng không chạy trên aspect, nguyên nhân có thể là quy tắc loại trừ aspect.
6. **Logic được đặt đúng chỗ chưa?** Nếu muốn advice trên hành vi do aspect khởi xướng, chuyển logic có thể tái sử dụng sang Spring collaborator bean và để aspect inject qua proxy.
7. **Có tạo vòng gọi vô hạn?** Nếu aspect gọi collaborator đã được aspect khác advice, kiểm tra lại pointcut của tất cả advisor liên quan.
8. **Có nhầm Spring AOP với cơ chế weaving khác không?** Kết luận về proxy của bài chỉ áp dụng cho Spring AOP; nếu project dùng AspectJ weaving thực sự, hãy đánh giá theo runtime/cấu hình riêng.

## 10. Câu hỏi tự kiểm tra

**1.** Một class có `@Aspect` nhưng không có `@Component` và không được khai báo bằng `@Bean`. Spring sẽ tự dùng advice chỉ nhờ `@Aspect` không?  
**Đáp án:** Không. Spring phải đăng ký aspect thành bean phù hợp để @AspectJ auto-proxy hỗ trợ phát hiện.

**2.** Hai aspect `A` và `B` đều là Spring bean. `B` khai báo `@Before` khớp method `A.ping()`. Spring AOP có tự proxy `A` để chạy `B` không?  
**Đáp án:** Không. Bean `@Aspect` bị loại khỏi auto-proxying của Spring AOP.

**3.** Nếu `A` gọi một Spring service `S` mà `B` có pointcut áp dụng, `B` có thể chạy không?  
**Đáp án:** Có, khi `S` là bean đủ điều kiện, nhận proxy, và lời gọi của `A` đi qua proxy đó.

**4.** Tại sao `@Order` không sửa được trường hợp `B` không advice `A`?  
**Đáp án:** `@Order` sắp xếp advice trên target đã có chuỗi interceptor; nó không biến aspect thành target eligible.

**5.** `AopUtils.isAopProxy(auditAspect)` trả `false` nhưng `AuditAspect` vẫn log được lời gọi `PaymentService.pay()`. Có mâu thuẫn không?  
**Đáp án:** Không. `AuditAspect` là nguồn advice; Spring AOP proxy nằm trên `PaymentService`, không cần nằm trên `AuditAspect`.

**6.** Cách thiết kế nào phù hợp khi muốn timing một thao tác IO do aspect gọi?  
**Đáp án:** Tách thao tác IO vào Spring collaborator bean được inject và có thể được proxy; hoặc đo trực tiếp ở code của aspect tùy yêu cầu, tránh kỳ vọng advice khác chặn method nội bộ của aspect.

## Tài liệu chính thức

- [Spring Framework — Declaring an Aspect](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/at-aspectj.html): aspect bean được khai báo như bean thường, `@Aspect` không đủ cho component-scan và ghi chú chính thức rằng aspect không được advice bởi aspect khác qua Spring AOP.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html): bật xử lý @AspectJ auto-proxy bằng Java configuration hoặc XML.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): proxy/target, giới hạn method interception và self-invocation.
- [Spring Framework — Using the Auto-Proxy Facility](https://docs.spring.io/spring-framework/reference/core/aop-api/autoproxy.html): vai trò auto-proxy creator và hạ tầng bean post-processing.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-beans-007">Vòng đời của aspect và vòng đời của bean đích</a>

<details>
<summary>Xem chi tiết</summary>

# Vòng đời của aspect và vòng đời của bean đích trong Spring AOP

## 1. Câu hỏi trọng tâm

Trong Spring AOP, **aspect** và **bean đích (target bean)** đều có thể do Spring IoC container quản lý, nhưng chúng **không mặc nhiên có cùng scope, cùng số instance hoặc cùng thời điểm được tạo/hủy**.

Một `@Aspect` mặc định thường là **một Spring singleton trong mỗi `ApplicationContext`**, trong khi cùng aspect đó có thể áp dụng advice cho nhiều bean đích: singleton, prototype hoặc bean thuộc scope web được cấu hình phù hợp. Ngược lại, các phương thức vòng đời của target bean không nhất thiết là những lần gọi đi qua AOP proxy, nên advice không tự chạy chỉ vì Spring đang khởi tạo/hủy bean.

Hiểu hai vòng đời độc lập này giúp trả lời các câu hỏi thực tế:

- Spring tạo instance aspect khi nào, và có tạo lại aspect mỗi khi service được gọi không?
- Bean singleton và prototype được proxy như thế nào? Lấy hai lần `getBean()` cho kết quả gì?
- Vì sao `@Before` không chạy khi target tự gọi method trong `@PostConstruct` hoặc `afterPropertiesSet()`?
- Ai gọi `@PreDestroy`/`destroy()` và điều gì xảy ra khi `ApplicationContext` đóng?
- Làm sao tránh lưu trạng thái riêng của từng request hoặc từng lần gọi trong một aspect singleton dùng chung?

Bài tập trung vào **vòng đời của aspect do Spring dùng cho Spring AOP** và quan hệ với **Spring bean đang được proxy**. Các mô hình AspectJ độc lập với Spring chỉ được nhắc khi cần phân biệt rõ cơ chế.

## 2. Phân biệt ba đối tượng trong một lời gọi Spring AOP

Khi Spring áp dụng advice cho một target bean theo cơ chế proxy, ta cần phân biệt:

| Thành phần | Vai trò | Quan hệ với vòng đời |
| --- | --- | --- |
| **Aspect bean** | Đối tượng chứa `@Aspect` và advice như `@Before`, `@Around`. | Thường singleton theo context; có lifecycle callback như các Spring bean khác. |
| **Target bean** | Đối tượng chứa nghiệp vụ, ví dụ `OrderService`. | Scope và lifecycle tùy bean definition: singleton, prototype, request, session... |
| **AOP proxy** | Đối tượng đại diện Spring có thể trao cho caller thay vì trao raw target, nhằm áp dụng advisor/advice khi method khớp. | Thường được tạo trong quy trình hậu xử lý bean; vòng đời/khả năng tái sử dụng phụ thuộc cách cấu hình target và target source. |

Mô hình gọi đơn giản:

```text
Spring ApplicationContext
  |
  +-- TraceAspect bean (thường 1 instance / context)
  |      └── advice / pointcut
  |
  +-- OrderService bean definition (ví dụ singleton)
  |      └── raw target -> Spring AOP proxy -> caller
  |
  +-- InvoiceService bean definition (ví dụ prototype)
         ├── lần getBean #1: target A -> proxy A
         └── lần getBean #2: target B -> proxy B

caller -> proxy phù hợp -> advice của aspect -> target.method()
```

Hình này mô tả **cấu hình proxy thông thường**. Với scoped proxy hoặc custom `TargetSource`, có thể có một proxy dùng chung trỏ tới các target khác nhau theo lần gọi; không được tổng quát hóa rằng mọi proxy luôn giữ một target cố định.

**Cốt lõi:** một aspect singleton vẫn có thể chạy cùng lời advice trên hàng trăm target beans hoặc nhiều lần gọi; **số lần advice thực thi không phải số lần aspect được khởi tạo**.

## 3. Vòng đời thường gặp của aspect `@Aspect`

### 3.1. Spring phải đăng ký aspect thành bean

`@Aspect` cho Spring biết class mang khai báo aspect, nhưng chỉ riêng annotation này không tự tạo một Spring bean. Có hai cách phổ biến:

```java
@Aspect
@Component
public class AuditAspect {
    // Advice...
}
```

Hoặc:

```java
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
    @Bean
    public AuditAspect auditAspect() {
        return new AuditAspect();
    }
}
```

Trong cấu hình thứ hai, class `AuditAspect` vẫn phải có `@Aspect`, và phương thức `@Bean` đăng ký nó với container. Ví dụ chỉ minh họa cách khai báo, không phải tệp Java độc lập.

Ở Spring Framework thuần, thường bật `@EnableAspectJAutoProxy` hoặc XML `<aop:aspectj-autoproxy/>`. Spring Boot có thể tự cấu hình Spring AOP khi starter/điều kiện thích hợp đã có. Nếu aspect không phải bean hoặc hạ tầng proxy chưa bật, việc target và advice cùng tồn tại trong source code không đủ để bảo đảm lời advice chạy.

### 3.2. Singleton aspect có nghĩa gì?

Mặc định, Spring bean có scope `singleton`. Với `@Aspect` thông thường dùng mô hình singleton, có **một instance aspect ứng với bean definition trong cùng context**, không phải một instance trên từng service bean, từng thread hoặc từng request.

Ví dụ `AuditAspect` được tạo một lần và giữ `AtomicInteger` đếm số lời gọi; khi có 1.000 lần gọi qua proxy, counter tăng 1.000 lần nhưng constructor của `AuditAspect` không chạy 1.000 lần.

Cần phân biệt:

- **Singleton theo Spring context**, không phải singleton toàn JVM. Hai `ApplicationContext` độc lập có thể có hai instance aspect tương ứng.
- Nếu đăng ký **hai bean khác nhau** cùng class `@Aspect`, có thể có hai instance aspect và hai tập advisor; đó là hai bean definitions, không phải Spring tự nhân bản aspect trên mỗi lời gọi.
- Scope không tự bảo đảm thread safety: aspect singleton có thể được nhiều thread gọi đồng thời. Các field ghi trạng thái phải được thiết kế như shared state an toàn hoặc loại bỏ.

### 3.3. Lifecycle callback của aspect vẫn là callback Spring bean

Aspect là một Spring bean nên có thể tham gia các cơ chế lifecycle phổ biến:

- Khởi tạo instance và inject dependency.
- Callback khởi tạo như `@PostConstruct`, `InitializingBean.afterPropertiesSet()`, hoặc `initMethod` khi được cấu hình.
- Được Spring dùng để xây dựng advisor áp dụng lên các target phù hợp.
- Callback hủy `@PreDestroy`, `DisposableBean.destroy()` hoặc `destroyMethod` khi scope/context thuộc diện Spring quản lý hủy.

**Không có quy tắc bảo đảm constructor của aspect luôn chạy trước constructor của mọi target bean.** Thứ tự khởi tạo phụ thuộc dependency graph, lazy initialization, proxy/advisor discovery và cách tạo context. Khi thứ tự thật sự quan trọng, xác lập dependency và kiểm thử; đừng dựa vào thứ tự tình cờ quan sát ở log.

### 3.4. Aspect thường không bị proxy bởi chính Spring AOP

Spring Framework loại class mang `@Aspect` khỏi tập target cần auto-proxy bằng chính cơ chế Spring `@AspectJ`. Không thiết kế hệ thống với giả định advice của aspect A sẽ tự chặn method nội bộ trên aspect B thông qua Spring AOP. Trách nhiệm chung cần được gắn vào **bean nghiệp vụ được proxy**, không gắn vào vòng đời callback hoặc class aspect như một target mặc định.

## 4. Vòng đời bean đích và vị trí AOP proxy xuất hiện

Trong luồng Spring container thông thường, sơ đồ khái quát cho một bean singleton được áp dụng Spring AOP là:

```text
Đọc bean definition / đăng ký hạ tầng auto-proxy
            |
            v
Tạo raw target instance
            |
            v
Inject dependencies và gọi callbacks trước khởi tạo
            |
            v
Gọi @PostConstruct / afterPropertiesSet / init method nếu có
            |
            v
BeanPostProcessor hậu khởi tạo
(có thể trả về Spring AOP proxy cho bean phù hợp)
            |
            v
Đưa bean/proxy đã hoàn thiện cho caller
            |
            v
Caller gọi method qua proxy -> advice có thể chạy
            |
            v
Khi kết thúc scope/context: Spring gọi cleanup callback
theo lifecycle scope tương ứng
```

Đây là **trật tự khái quát của quy trình chuẩn**, không phải cam kết mọi extension đều làm giống nhau; proxy cũng có thể được xử lý sớm trong tình huống đặc biệt như circular references. Điều quan trọng cho bài này là:

**Các initialization callbacks được gọi trên raw bean trước khi một proxy AOP thông thường được áp dụng.** Vì thế, nếu target gọi một phương thức được advice từ `@PostConstruct`/`afterPropertiesSet()`, lời gọi nội bộ đó không đi qua proxy của Spring AOP.

Ví dụ minh họa:

```java
public class OrderService implements org.springframework.beans.factory.InitializingBean {
    @Override
    public void afterPropertiesSet() {
        placeOrder(); // Trực tiếp gọi trên raw target (this).
    }

    public void placeOrder() {
        // Nghiệp vụ.
    }
}
```

Nếu pointcut khớp `placeOrder()`, Spring không tự áp dụng advice cho lời gọi trong `afterPropertiesSet()` như cho `context.getBean(OrderService.class).placeOrder()` sau khi proxy đã được cung cấp. Tương tự, `this.placeOrder()` trong một method khác của target là **self-invocation**, không quay lại proxy.

**Phân biệt:** `@Before` và `@After` là advice của một **lần thực thi phương thức qua proxy**, không phải cặp callback vòng đời Spring `@PostConstruct`/`@PreDestroy`.

## 5. Scope bean đích tác động thế nào tới việc tạo proxy?

| Scope target | Tạo target | Điều thường thấy khi lấy bean qua Spring | Hủy do container |
| --- | --- | --- | --- |
| `singleton` (mặc định) | Một target cho bean definition trong một context; thường eager khi context refresh, trừ khi lazy hoặc điều kiện khác. | Các lần `getBean()` cùng tên thường trả về cùng tham chiếu Spring bean (proxy nếu có). | Spring gọi destruction callbacks khi đóng context, trừ các ngoại lệ cấu hình/vòng đời. |
| `prototype` | Tạo một target mới mỗi lần container giải quyết yêu cầu bean tương ứng. | Khi dùng auto-proxy AOP thông thường, mỗi lần lấy prototype bean được xử lý tạo proxy cho target mới. | **Không tự gọi destruction callback** khi context đóng; client phải tự giải phóng resource nếu cần. |
| `request`/`session` trong web app | Tạo trong request/session scope theo cơ chế web-aware context. | Có thể inject scoped proxy để một dependency dài hạn luôn truy cập target hiện hành theo scope. | Cleanup tùy scope và cơ chế kết thúc request/session. |
| Custom scope/`TargetSource` | Theo logic cấu hình cụ thể. | Một proxy có thể chuyển tiếp tới target thay đổi theo lookup/lần gọi. | Phải kiểm tra contract scope/target source cụ thể. |

Quan hệ giữa **scope aspect** và **scope target** được quyết định riêng. Đặc biệt, `@Scope("prototype")` trên target **không khiến một singleton aspect trở thành prototype**.

### 5.1. Prototype target được tạo nhiều lần nhưng vẫn có thể dùng cùng singleton aspect

Ví dụ: target `ReportService` có scope prototype. Hai lần `context.getBean(ReportService.class)` trả về hai bean instances riêng, mỗi instance có thể được Spring bọc bằng proxy; một `@Aspect` singleton dùng chung vẫn đưa advice lên cả hai.

Ngược lại, nếu một singleton bean được inject trực tiếp với một prototype dependency tại thời điểm khởi tạo, nó thường **giữ đúng prototype instance đã nhận**. Nó không tự nhận một prototype mới ở mỗi lần gọi. Muốn lookup target mới nhiều lần, có thể dùng `ObjectProvider`, method injection hoặc scoped proxy được cấu hình đúng.

### 5.2. Scoped proxy khác proxy AOP của advice

Một bean request/session/prototype có thể được gắn scoped proxy để inject vào bean sống lâu hơn. Scoped proxy quyết định **chọn target hiện hành**; AOP proxy/advisor quyết định **lời gọi nào chạy advice**. Hai vai trò có thể phối hợp trong một cấu hình, thậm chí tạo nhiều tầng proxy khi thích hợp.

Vì vậy thấy `AopUtils.isAopProxy(bean) == true` chưa đủ kết luận proxy đó đã chứa advice mình mong muốn. Cần xét advisor và chạy thử method có pointcut khớp.

## 6. Ví dụ hoàn chỉnh: một aspect singleton, hai loại target scope

Ví dụ này dùng **Java 17+, Spring Framework 6.1.14, AspectJ weaver 1.9.22.1, Maven** để giữ dependency rõ ràng. Không cần Spring Boot hoặc web server.

Ta cố ý làm cho `SingletonService.afterPropertiesSet()` gọi `run()` trên raw bean: **đây là lời gọi nghiệp vụ không qua AOP proxy**. Sau khi container khởi động, ứng dụng lấy bean qua context và gọi `run()` từ bên ngoài để so sánh.

### 6.1. Cấu trúc project

```text
spring-aop-lifecycle/
├── pom.xml
└── src/main/java/com/example/lifecycle/
    ├── AppConfig.java
    ├── TraceAspect.java
    ├── SingletonService.java
    ├── PrototypeService.java
    └── DemoApp.java
```

### 6.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-aop-lifecycle</artifactId>
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

`spring-context` cung cấp container và kéo các module Spring nền tảng, bao gồm Spring AOP theo cây dependency phù hợp với bản mẫu; `aspectjweaver` cung cấp hỗ trợ cần thiết để Spring xử lý `@Aspect`/pointcut. Đây vẫn là **Spring proxy AOP**.

### 6.3. `src/main/java/com/example/lifecycle/TraceAspect.java`

```java
package com.example.lifecycle;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

@Aspect
public class TraceAspect implements InitializingBean, DisposableBean {
    private static final AtomicInteger initialized = new AtomicInteger();
    private static final AtomicInteger destroyed = new AtomicInteger();

    private final AtomicInteger adviceCalls = new AtomicInteger();

    @Override
    public void afterPropertiesSet() {
        initialized.incrementAndGet();
    }

    @Before("execution(public void com.example.lifecycle.*Service.run())")
    public void beforeRun() {
        adviceCalls.incrementAndGet();
    }

    public int calls() {
        return adviceCalls.get();
    }

    public static int initCount() {
        return initialized.get();
    }

    public static int destroyCount() {
        return destroyed.get();
    }

    @Override
    public void destroy() {
        destroyed.incrementAndGet();
    }
}
```

`TraceAspect` có một counter trên instance để đếm số lần advice chạy, và counter `static` để quan sát callback khởi tạo/hủy. Counter `static` chỉ phục vụ ví dụ một lần chạy trong JVM; code sản xuất không nên dùng static field làm cơ chế xác định lifecycle nghiệp vụ.

`AtomicInteger` cho phép counter được tăng an toàn nếu có nhiều thread; bản thân việc dùng singleton aspect không tự bảo đảm mọi loại mutable field đều thread-safe.

### 6.4. `src/main/java/com/example/lifecycle/SingletonService.java`

```java
package com.example.lifecycle;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public class SingletonService implements InitializingBean, DisposableBean {
    private static final AtomicInteger initialized = new AtomicInteger();
    private static final AtomicInteger destroyed = new AtomicInteger();
    private static final AtomicInteger runs = new AtomicInteger();

    @Override
    public void afterPropertiesSet() {
        initialized.incrementAndGet();
        run(); // Gọi trực tiếp trên raw bean trước khi proxy được công bố.
    }

    public void run() {
        runs.incrementAndGet();
    }

    public static int initCount() {
        return initialized.get();
    }

    public static int destroyCount() {
        return destroyed.get();
    }

    public static int runCount() {
        return runs.get();
    }

    @Override
    public void destroy() {
        destroyed.incrementAndGet();
    }
}
```

`SingletonService` có đúng một lần gọi `run()` **trong initialization callback** và sẽ có thêm lời gọi `run()` từ chương trình chính qua proxy.

### 6.5. `src/main/java/com/example/lifecycle/PrototypeService.java`

```java
package com.example.lifecycle;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public class PrototypeService implements InitializingBean, DisposableBean {
    private static final AtomicInteger initialized = new AtomicInteger();
    private static final AtomicInteger destroyed = new AtomicInteger();
    private static final AtomicInteger runs = new AtomicInteger();

    @Override
    public void afterPropertiesSet() {
        initialized.incrementAndGet();
    }

    public void run() {
        runs.incrementAndGet();
    }

    public static int initCount() {
        return initialized.get();
    }

    public static int destroyCount() {
        return destroyed.get();
    }

    public static int runCount() {
        return runs.get();
    }

    @Override
    public void destroy() {
        destroyed.incrementAndGet();
    }
}
```

Class này có `destroy()` để chứng minh rằng **Spring không tự gọi destruction callback của prototype bean khi đóng context**, dù đã chạy callback khởi tạo mỗi khi tạo một prototype.

### 6.6. `src/main/java/com/example/lifecycle/AppConfig.java`

```java
package com.example.lifecycle;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Scope;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public TraceAspect traceAspect() {
        return new TraceAspect();
    }

    @Bean
    public SingletonService singletonService() {
        return new SingletonService();
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public PrototypeService prototypeService() {
        return new PrototypeService();
    }
}
```

`@Bean` mặc định singleton áp dụng cho `TraceAspect` và `SingletonService`. `PrototypeService` được đánh dấu prototype nên mỗi lần lookup từ container tạo một target mới. `@EnableAspectJAutoProxy(proxyTargetClass = true)` yêu cầu class-based proxy để lấy các service không có interface bằng chính kiểu lớp.

### 6.7. `src/main/java/com/example/lifecycle/DemoApp.java`

```java
package com.example.lifecycle;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApp {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfig.class);
        try {
            TraceAspect aspect = context.getBean(TraceAspect.class);

            SingletonService singletonA = context.getBean(SingletonService.class);
            SingletonService singletonB = context.getBean(SingletonService.class);
            PrototypeService prototypeA = context.getBean(PrototypeService.class);
            PrototypeService prototypeB = context.getBean(PrototypeService.class);

            System.out.println("singleton same=" + (singletonA == singletonB));
            System.out.println("prototype same=" + (prototypeA == prototypeB));
            System.out.println("singleton proxy=" + AopUtils.isAopProxy(singletonA));
            System.out.println("prototype proxy=" + AopUtils.isAopProxy(prototypeA));
            System.out.println("initialized: aspect=" + TraceAspect.initCount()
                    + ", singleton=" + SingletonService.initCount()
                    + ", prototype=" + PrototypeService.initCount());
            System.out.println("before external: singletonRuns="
                    + SingletonService.runCount() + ", adviceCalls=" + aspect.calls());

            singletonA.run();
            prototypeA.run();
            prototypeB.run();

            System.out.println("after external: singletonRuns="
                    + SingletonService.runCount()
                    + ", prototypeRuns=" + PrototypeService.runCount()
                    + ", adviceCalls=" + aspect.calls());
        } finally {
            context.close();
        }

        System.out.println("after close: aspectDestroyed=" + TraceAspect.destroyCount()
                + ", singletonDestroyed=" + SingletonService.destroyCount()
                + ", prototypeDestroyed=" + PrototypeService.destroyCount());
    }
}
```

Chạy trong thư mục chứa `pom.xml` với Maven và JDK 17+:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.lifecycle.DemoApp
```

Các dòng do **chương trình** in ra, theo đúng thứ tự (không tính log của Maven/Spring nếu môi trường tự bật log):

```text
singleton same=true
prototype same=false
singleton proxy=true
prototype proxy=true
initialized: aspect=1, singleton=1, prototype=2
before external: singletonRuns=1, adviceCalls=0
after external: singletonRuns=2, prototypeRuns=2, adviceCalls=3
after close: aspectDestroyed=1, singletonDestroyed=1, prototypeDestroyed=0
```

**Giải thích đầu ra:**

1. Hai lần lấy `SingletonService` trả cùng tham chiếu bean/proxy, trong khi hai lần lấy `PrototypeService` trả hai tham chiếu khác nhau.
2. Cả singleton target lẫn prototype targets đều khớp pointcut `execution(public void ...*Service.run())` nên được Spring xử lý thành AOP proxies.
3. Có một `TraceAspect` được khởi tạo, một singleton service và hai prototype services. `TraceAspect` **không bị nhân bản** để theo số prototype instances.
4. Trong `SingletonService.afterPropertiesSet()`, `run()` đã chạy một lần trên raw bean nhưng advice vẫn bằng 0. Khi caller bên ngoài dùng proxy gọi ba lần (`singletonA`, `prototypeA`, `prototypeB`), advice tăng đúng 3 lần.
5. Sau `context.close()`, Spring hủy singleton aspect và singleton target. Hai prototype targets đã được tạo và khởi tạo vẫn **không có** lời gọi `destroy()` tự động do container; ví dụ không tự giải phóng chúng để quan sát khác biệt.

Đây là thí nghiệm về Spring container lifecycle và Spring AOP proxy. Không nên diễn giải kết quả thành “mọi method trong constructor hoặc `@PostConstruct` luôn bị bỏ qua vì pointcut không khớp”: vấn đề thực sự là **không đi qua proxy** ở thời điểm đó.

## 7. `@PostConstruct` và `@PreDestroy` có phải advice không?

**Không.** Hai annotation lifecycle callback quản lý **chu trình sống của bean**, còn Spring AOP advice quản lý **lần thực thi method qua proxy**.

| Tình huống | Ai kích hoạt? | Có mặc nhiên chạy `@Before`/ `@Around`? |
| --- | --- | --- |
| `@PostConstruct` trên target bean | Spring trong lúc khởi tạo raw bean | Không; callback chạy trước khi proxy thông thường được áp dụng. |
| `InitializingBean.afterPropertiesSet()` | Spring trong init phase | Không; tương tự. |
| Caller dùng bean đã inject từ context và gọi method khớp pointcut | Caller đi qua AOP proxy | Có, nếu proxy/advisor và điểm gọi hợp lệ. |
| `this.someMethod()` từ một method của target | Raw target tự gọi nội bộ | Không đi qua proxy, thường không kích hoạt advice của lời gọi nội bộ. |
| `@PreDestroy`/`DisposableBean.destroy()` | Container trong cleanup của bean được quản lý hủy | Không được xem là lời gọi có advice theo mặc định; không dùng advice để thay callback hủy. |
| Method trên đối tượng tự `new` ngoài Spring | Code ứng dụng | Không có AOP proxy Spring để áp dụng. |

Một callback `@PostConstruct` muốn thực hiện nghiệp vụ sau khi proxy đã sẵn sàng thường là tín hiệu cần thiết kế lại điểm kích hoạt: có thể đẩy công việc sang bean khác, dùng `ApplicationRunner` trong Spring Boot hoặc callback/event sau khi context refresh phù hợp. Không nên giả định gọi method được advice từ trong constructor/initialization callback sẽ kích hoạt advice.

**Quan trọng với `@Transactional`:** Spring transaction interceptor cũng thường dựa trên proxy. Nếu gọi method `@Transactional` từ chính target trong `@PostConstruct`, transaction proxy không có cơ hội chặn lời gọi. Đây là một lỗi lifecycle/proxy thường gặp, không phải lỗi syntax của annotation.

## 8. Các mô hình khởi tạo aspect nâng cao trong Spring

### 8.1. Singleton là mặc định, nhưng không phải mô hình duy nhất

Tài liệu Spring Framework về `@AspectJ` nêu các **aspect instantiation models** như:

- **Singleton:** một aspect instance mặc định trong application context.
- **`perthis(...)`:** conceptually một aspect instance gắn với mỗi đối tượng proxy `this` phù hợp tại join point được chọn.
- **`pertarget(...)`:** aspect instance gắn với mỗi đối tượng target phù hợp.
- **`pertypewithin(...)`:** được tài liệu các phiên bản Spring mới liệt kê, nhưng cần kiểm tra hỗ trợ thực tế theo dòng Framework ứng dụng đang dùng.
- **`percflow` và `percflowbelow`:** không được Spring proxy AOP hỗ trợ theo tài liệu hiện hành.

Các mô hình không-singleton này là **quy tắc tạo aspect instances**, khác với `@Scope("prototype")` của **target bean**. Chúng không được bật chỉ vì target là prototype, và không được suy luận đơn giản từ số lần `getBean()` của target.

Ví dụ khai báo mang tính minh họa:

```java
@Aspect("perthis(execution(* com.example..service.*.*(..)))")
public class PerProxyUsageAspect {
    private int callsForAssociatedProxy;

    @Before("execution(* com.example..service.*.*(..))")
    public void record() {
        callsForAssociatedProxy++;
    }
}
```

Đây là **một class fragment minh họa**, cần thêm imports, đăng ký bean và cấu hình scope tương thích với kiểu khởi tạo aspect (thường dùng prototype-scoped aspect bean cho mô hình per-clause phù hợp), không phải một chương trình độc lập. Tránh dùng `perthis` hoặc `pertarget` chỉ để đếm metric chung: tính phức tạp và lifecycle sẽ khó kiểm soát hơn singleton stateless.

Spring AOP chỉ chặn **method executions trên Spring beans qua proxy**. Ngay cả khi dùng `perthis`/`pertarget`, nó không biến thành AspectJ compile-time weaving, và không tự sửa giới hạn self-invocation hoặc đối tượng ngoài container.

### 8.2. Đừng nhầm `@Scope("prototype")` của aspect với `perthis`/`pertarget`

`@Scope` là metadata cho Spring bean definition; `perthis`/`pertarget` là các quy tắc instantiation của `@Aspect`. Với nhu cầu cơ bản, **giữ aspect singleton và không mang mutable state theo từng request** là lựa chọn dễ kiểm soát hơn. Nếu cần aspect per-object, phải chọn model được hỗ trợ theo phiên bản Spring và bảo đảm bean definition tương thích.

Ngoài ra, Spring schema-based AOP qua XML `<aop:aspect>` chỉ hỗ trợ **singleton instantiation model** theo tài liệu, nên không được chuyển cơ học một aspect `perthis` thành cấu hình schema và mong có cùng vòng đời.

## 9. Thiết kế trạng thái trong singleton aspect: tránh lỗi chia sẻ dữ liệu

Một aspect singleton có thể bị gọi đồng thời từ nhiều request/threads. Ví dụ dễ lỗi:

```java
@Aspect
public class UnsafeDurationAspect {
    private long startedAtNanos;

    @Before("execution(* com.example..service.*.*(..))")
    public void before() {
        startedAtNanos = System.nanoTime();
    }

    @After("execution(* com.example..service.*.*(..))")
    public void after() {
        System.out.println(System.nanoTime() - startedAtNanos);
    }
}
```

Đây là **ví dụ phản diện**, chưa phải class chạy độc lập. Nếu thread A và thread B gọi cùng lúc, field `startedAtNanos` bị ghi chồng khiến kết quả đo sai. Kể cả thêm `volatile` chỉ giải quyết tính nhìn thấy của giá trị, không biến state đó thành riêng cho từng lời gọi.

Hướng phù hợp hơn là dùng biến cục bộ trong một advice `@Around`:

```java
@Around("execution(* com.example..service.*.*(..))")
public Object measure(org.aspectj.lang.ProceedingJoinPoint pjp) throws Throwable {
    long startedAtNanos = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        long elapsedNanos = System.nanoTime() - startedAtNanos;
        // Gửi elapsedNanos sang metric recorder thread-safe.
    }
}
```

Đây cũng là **method fragment** trong một aspect có import `org.aspectj.lang.annotation.Around`. `finally` đo cả trường hợp method ném exception; `proceed()` chuyển tiếp lời gọi đến target. Biến `startedAtNanos` cục bộ trên invocation nên không chia sẻ giữa threads.

Đối với counter tổng hợp, `AtomicInteger`/`LongAdder` hoặc metric registry phù hợp có thể dùng; đối với state gắn với request, dùng request-scoped context hay tracing context theo kiến trúc ứng dụng, không giữ trong instance field mutable của singleton aspect.

## 10. Quan sát vòng đời đúng cách khi kiểm thử Spring AOP

Một test phục vụ bài học nên xác minh cả **quan hệ instance** lẫn **hành vi advice**, thay vì chỉ in log constructor:

1. **Context và scope:** kiểm tra `getBean()` hai lần với singleton/prototype; nếu web scope, tạo request/session test context tương ứng.
2. **Proxy:** kiểm tra `AopUtils.isAopProxy(bean)` và advisor/pointcut áp dụng, không kết luận chỉ dựa vào class name chứa `$$SpringCGLIB$$`.
3. **Lifecycle callback:** đếm `afterPropertiesSet()`, `@PostConstruct`, `destroy()` tùy scope và thời điểm close.
4. **Đường gọi:** gọi method từ bên ngoài qua proxy, rồi so với lời gọi nội bộ từ target/initialization phase.
5. **Aspect instances:** đo số bean aspect trong context và số lần advice chạy; hai con số độc lập.
6. **Phạm vi context:** thử parent/child context nếu có để tránh nhầm singleton theo context với singleton theo toàn JVM.
7. **Thread safety:** nếu advice lưu state, kiểm thử dưới tải đồng thời và lỗi/exception, không chỉ một lần gọi tuần tự.

Có thể dùng `context.getBeanNamesForType(MyAspect.class)` để quan sát aspect beans, `AopUtils.isAopProxy(target)` để nhận diện proxy, và `Advised.getAdvisors()` khi cần kiểm tra advisor chain. Đây là các phép kiểm tra bổ trợ: **bean bị proxy** không đồng nghĩa **mỗi method đều được advice**, và **một aspect bean** không có nghĩa advice chỉ chạy một lần.

## 11. Các lỗi thường gặp và cách chẩn đoán

| Triệu chứng | Nguyên nhân có thể | Hướng kiểm tra/sửa |
| --- | --- | --- |
| Advice không chạy từ `@PostConstruct` | Target đang chạy init trước khi proxy hoàn thiện, hoặc gọi nội bộ trên `this`. | Đưa lời gọi cần interception qua bean/proxy từ ngoài sau khởi tạo; không cố vá bằng cách bật lại AOP. |
| Advice chạy nhiều lần dù aspect chỉ khởi tạo một lần | Singleton aspect được chia sẻ và advice chạy cho mỗi lời gọi phù hợp — hành vi bình thường. | Phân biệt instance count với invocation count; chỉ báo lỗi nếu vượt số lần dự kiến. |
| Có hai instance của cùng class aspect | Đăng ký aspect qua `@Component`, `@Bean`, XML trùng hoặc nhiều context. | Đếm bean name/contexts và thống nhất nơi đăng ký. |
| `prototype` target bị giữ lâu trong singleton client | Prototype được inject một lần khi singleton client khởi tạo. | Sử dụng `ObjectProvider`, method injection hoặc scoped proxy phù hợp. |
| Prototype có `destroy()` nhưng không thấy chạy khi đóng context | Container không tự quản lý destruction của prototype sau khi giao cho client. | Client quản lý cleanup/resource; nếu cần, gọi thủ tục giải phóng tương ứng. |
| Scope request/session không hoạt động ngoài web context | Scope web chưa được đăng ký/không có request đang hoạt động. | Dùng web-aware context, test request/session setup và scoped proxy đúng cách. |
| Aspect singleton có kết quả đo thời gian lẫn lộn | Shared mutable field giữa các lời gọi đồng thời. | Dùng local variable trong `@Around`, metric collector thread-safe. |
| Aspect được tạo sớm hơn dự kiến | Spring cần khởi tạo advisor/aspect, có dependency hoặc eager bean creation. | Kiểm tra `@Lazy`, bean graph và hạ tầng AOP; không giả định callback thứ tự chỉ bằng source order. |
| Không thấy `@PreDestroy` trên bean ngoài Spring | Đối tượng không thuộc quản lý container hoặc không thuộc scope được quản lý hủy. | Phân biệt `new` với `context.getBean()` và bảo đảm close context khi cần. |
| Thấy `@Aspect` nhưng không có advice | Aspect chưa là bean, auto-proxy chưa bật, pointcut không khớp hoặc caller không qua proxy. | Kiểm tra lần lượt đăng ký bean, enablement, proxy, pointcut và đường gọi. |

## 12. Kết luận thiết kế trong Spring AOP

Đối với đa số Spring services, cách thiết kế dễ bảo trì nhất là:

- **Aspect ít trạng thái, mặc định singleton**, chỉ chứa chính sách kỹ thuật dùng chung.
- **Target bean chọn scope theo trách nhiệm nghiệp vụ** (singleton cho đa số stateless services; prototype hoặc web scope khi có nhu cầu rõ ràng).
- **Advice bắt các lời gọi thông qua Spring proxy** sau khi bean được đưa vào sử dụng, không dùng advice thay cho init/destroy callbacks.
- **Dọn resource theo lifecycle của target scope**, đặc biệt không mong container tự hủy prototype.
- **Khi cần thay đổi aspect instantiation model**, đối chiếu đúng phiên bản Spring, mô hình `@AspectJ` và loại bean definition.

### Tài liệu chính thức để đối chiếu

- Spring Framework — Aspect Instantiation Models: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/instantiation-models.html
- Spring Framework — Declaring an Aspect: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/at-aspectj.html
- Spring Framework — Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework — Bean Scopes: https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html
- Spring Framework — Customizing the Nature of a Bean: https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html
- Spring Framework — Container Extension Points: https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html
- Spring Framework — Schema-based AOP Support: https://docs.spring.io/spring-framework/reference/core/aop/schema.html

**Một singleton aspect có thể đồng hành với nhiều target beans và nhiều lời gọi; điều quyết định advice có chạy là lời gọi có đi qua Spring proxy phù hợp hay không.** Vòng đời Spring bean và vòng đời lời gọi AOP có liên hệ, nhưng phải được quan sát và kiểm thử như hai cơ chế riêng biệt.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-beans-008">Scope của bean Spring khác mô hình khởi tạo aspect như thế nào</a>

<details>
<summary>Xem chi tiết</summary>

# Scope của Spring bean khác mô hình khởi tạo aspect như thế nào?

## 1. Hai câu hỏi tưởng giống nhưng Spring trả lời bằng hai cơ chế khác nhau

Trong Spring AOP, khi thấy một lớp có cả `@Aspect` và `@Component`, rất dễ nghĩ rằng `@Scope` trên lớp đó và tham số của `@Aspect(...)` là hai cách viết của cùng một quy tắc. Thực tế, chúng giải quyết **hai cấp độ khác nhau**.

- **Spring bean scope** trả lời: *BeanFactory/ApplicationContext cung cấp và quản lý đối tượng của một bean definition theo vòng đời nào?* Những scope phổ biến là `singleton`, `prototype`, `request`, `session`. Cơ chế này áp dụng cho bean nói chung, kể cả service bean và bean dùng để khai báo aspect.
- **Aspect instantiation model** trả lời: *Spring AOP tạo hoặc liên kết instance aspect với những join point/proxy/target nào khi thực thi advice?* Mặc định một aspect là mô hình **singleton**; với cú pháp `@AspectJ` được Spring hỗ trợ còn có các mô hình như `perthis` và `pertarget`.

Do đó, cùng một ứng dụng có thể có **hàng trăm target bean prototype** nhưng chỉ **một aspect instance singleton** quan sát các lần gọi qua proxy. Ngược lại, một aspect dạng `pertarget` có thể sở hữu các instance trạng thái riêng cho từng target được khớp, ngay cả khi bạn không thay đổi cách tạo target bằng `@Scope`.

Bài này tập trung vào **Spring Framework và Spring AOP theo proxy**: cách hai cơ chế phối hợp, cấu hình đúng và các ngộ nhận gây lỗi. Nó không mở rộng sang mô hình weaving độc lập ngoài Spring.

### Mục tiêu học tập

Sau bài này, bạn có thể:
1. Phân biệt *bean definition / bean scope / Spring AOP proxy / target object / aspect instance*.
2. Giải thích vì sao `@Scope("prototype")` trên service không tự tạo một aspect cho mỗi service, và tại sao `@Scope("prototype")` trên aspect cũng chưa đủ để biểu đạt `perthis` hoặc `pertarget`.
3. Chọn `@Aspect` mặc định hay `@Aspect("perthis(...)")` / `@Aspect("pertarget(...)")` theo nhu cầu trạng thái.
4. Chạy ví dụ Java 17 với Spring để kiểm tra số instance của target và aspect.
5. Nhận diện tương tác khó giữa Spring scoped proxy, vòng đời web bean và lời gọi qua AOP proxy.

## 2. Bảng đối chiếu: Spring bean scope và aspect instantiation

| Tiêu chí | Spring bean scope | Mô hình khởi tạo aspect trong Spring AOP |
| --- | --- | --- |
| Cấu hình chính | `@Scope`, `@RequestScope`, `@SessionScope`, hoặc scope của `@Bean` | `@Aspect` và giá trị per-clause trong `@Aspect("...")` |
| Đối tượng quản lý | Bean definition do Spring IoC quản lý, gồm service, repository, aspect bean | Những instance **của một aspect** được advice framework sử dụng |
| Quy tắc mặc định | `singleton`: một bean instance trên mỗi bean definition trong container | `@Aspect` không tham số: singleton instantiation model, thường một aspect instance trong context |
| Nhân bản instance | `prototype`: mỗi lần container cung cấp bean mới thường có instance mới | `perthis`: theo đối tượng `this` tại join point khớp; `pertarget`: theo target object tại join point khớp |
| Đơn vị gắn vòng đời | Context/bean lookup; hoặc request, session, scope tùy chỉnh | Proxy/target liên quan đến lời gọi và **điều kiện pointcut tạo aspect** |
| Điều kiện áp dụng | Có bean definition và scope đã đăng ký hợp lệ | AOP được bật; aspect được đăng ký làm Spring bean; advisor/pointcut có lời gọi phù hợp **qua proxy** |
| Thời điểm tạo | Theo việc khởi tạo bean: singleton thường eager, prototype được tạo theo yêu cầu, web scope theo request/session | Singleton aspect theo vòng đời bean; `perthis`/`pertarget` khởi tạo khi có join point phù hợp đầu tiên |
| Thứ gì được chia sẻ? | Phụ thuộc scope của từng bean, không phụ thuộc trực tiếp annotation AOP trên target | Phụ thuộc per-clause, không mặc định bằng scope của target |
| Mối quan hệ với proxy | Scoped proxy có thể giúp inject bean sống ngắn hơn vào bean sống dài hơn | AOP proxy chặn method execution và gọi advice trước/sau target |

**Hai chữ “singleton” không đồng nhất mọi đối tượng:**
- Spring bean singleton nghĩa là **một instance cho mỗi bean definition trong một container**, không phải một instance Java duy nhất trong toàn JVM.
- Singleton aspect mặc định là quy tắc Spring AOP sử dụng một aspect instance chung trong context để thực thi các advice của aspect đó. Nó có thể advise nhiều bean, nhiều phương thức, nhiều lời gọi và nhiều thread.
- Nếu có nhiều `ApplicationContext` hoặc nhiều bean definition khác nhau, không suy ra chỉ có một instance aspect trong toàn ứng dụng.

### Bốn đối tượng cần nhìn tách biệt

```text
ApplicationContext
│
├── Bean definition: auditAspect
│     └── aspect instance (thường singleton)
│                  │
│                  └── advice / advisor của Spring AOP
│
├── Bean definition: orderService [scope=prototype]
│     ├── target instance #1 ──> Spring AOP proxy #1
│     └── target instance #2 ──> Spring AOP proxy #2
│
└── Client:
      service1.call() ──> proxy #1 ──> advice trên aspect ──> target #1
      service2.call() ──> proxy #2 ──> advice trên aspect ──> target #2
```

Ở đây, các proxy và target là hai đối tượng khác nhau. Cả hai lời gọi có thể đi qua **cùng instance của singleton aspect**, mặc dù hai target là hai instance prototype. Không nên đếm số proxy rồi kết luận số instance aspect.

## 3. Bean scope tác động thế nào khi bean được Spring AOP advise?

### 3.1. `singleton` target

Ví dụ định nghĩa service:

```java
@Bean
public OrderService orderService() {
    return new OrderService();
}
```

Bean có scope mặc định `singleton`. Với một context và một bean definition:
- Các lần `context.getBean("orderService")` thường nhận cùng tham chiếu Spring-managed bean. Nếu Spring AOP cần proxy, tham chiếu đó có thể là **cùng một proxy singleton**.
- Tất cả lần gọi `public` khớp pointcut và đi qua proxy đều có thể dùng cùng instance aspect singleton.
- Mỗi lời gọi không tạo lại service hoặc aspect theo mặc định.

Lưu ý: Spring proxy không phải bằng chứng rằng toàn bộ logic trong đối tượng đã trở thành immutable hoặc thread-safe. Cả target singleton và aspect singleton đều có thể được nhiều thread truy cập.

### 3.2. `prototype` target

Nếu khai báo:

```java
@Bean
@Scope("prototype")
public OrderService orderService() {
    return new OrderService();
}
```

mỗi lần **BeanFactory được yêu cầu cấp bean** sẽ tạo một target instance mới. Nếu có advice khớp, Spring xử lý và trả một proxy ứng với bean được tạo đó. Tuy nhiên:
- **Aspect singleton vẫn có thể là cùng một instance** dùng chung cho mọi target prototype.
- Nếu inject prototype vào constructor một singleton, việc inject bình thường chỉ xảy ra khi singleton được tạo; singleton giữ tham chiếu đó cho các lần gọi tiếp theo. Muốn lấy prototype mới lúc runtime, cân nhắc `ObjectProvider<OrderService>` hoặc method injection.
- Gọi nhiều lần qua *một* tham chiếu prototype đã được lấy ra không khiến Spring tự cấp target mới cho mỗi method invocation. Đừng nhầm với `@Scope(proxyMode = ...)` hoặc một provider lấy bean mỗi lần.

### 3.3. `request`, `session` và scoped proxy

Bean `request` hoặc `session` thuộc phạm vi web-aware `ApplicationContext`. Ví dụ:

```java
@RequestScope
@Component
public class RequestMetadata {
    // Một target bean theo HTTP request trong Spring web context.
}
```

Khi cần đưa bean sống theo request vào một singleton, Spring thường dùng **scoped proxy** để tra cứu target ứng với request hiện tại. Scoped proxy giải bài toán *tham chiếu và vòng đời bean*, còn AOP proxy giải bài toán *áp dụng advice cho lời gọi method*. Hai loại proxy có thể cùng xuất hiện trong một đường gọi nhưng phục vụ mục đích khác nhau.

**Không tự suy ra** rằng cứ một HTTP request là Spring tạo một aspect mới. Nếu aspect đang ở mô hình singleton, nó vẫn có thể quan sát nhiều request bằng một instance. Nếu ứng dụng cần dữ liệu riêng theo request, hãy lấy từ method arguments, các dependency request-scoped phù hợp hoặc context của lời gọi, thay vì đưa dữ liệu của từng request vào field mutable dùng chung của singleton aspect.

## 4. Cách Spring AOP tạo aspect instance

### 4.1. Mặc định: `@Aspect` có singleton instantiation model

```java
@Aspect
@Component
public class AuditAspect {
    @Before("execution(* com.example.service..*.*(..))")
    public void audit() {
        // Được chạy cho các bean/method khớp pointcut,
        // nhưng không vì mỗi bean khớp mà có aspect instance mới.
    }
}
```

`@Component` là cách đăng ký lớp thành Spring bean. `@Aspect` mô tả các advice/pointcut để Spring phân tích khi AOP đã được kích hoạt (ví dụ qua `@EnableAspectJAutoProxy` hoặc Spring Boot AOP auto-configuration). Một mình `@Aspect` **không tự đăng ký bean**.

Nếu aspect bean dùng scope mặc định `singleton`, một aspect instance sẽ được framework dùng để xử lý những lời gọi khớp trên nhiều target/proxy. Điều này phù hợp nhất với aspect **stateless** hoặc có trạng thái dùng chung được thiết kế thread-safe (ví dụ bộ đếm `AtomicLong`).

**Cạm bẫy:** field `int currentUserCalls` trong singleton aspect có thể bị cập nhật đồng thời bởi nhiều thread và trộn dữ liệu của nhiều người dùng. Khi advice chỉ cần đo thời gian từng lời gọi, biến cục bộ trong `@Around` thường phù hợp hơn field của aspect.

### 4.2. `perthis`: một aspect instance theo đối tượng `this` được khớp

Với Spring AOP, cú pháp:

```java
@Aspect("perthis(execution(* com.example.service..*.*(..)))")
@Component
@Scope("prototype")
public class PerProxyAuditAspect {
    @Before("execution(* com.example.service..*.*(..))")
    public void audit() {
        // Trạng thái của instance aspect được gắn với this phù hợp.
    }
}
```

Ý nghĩa:
1. Phần `perthis(...)` là **instantiation pointcut** xác định lần đầu một đối tượng `this` tham gia lời gọi phù hợp.
2. Khi phát sinh lời gọi khớp đầu tiên, Spring có thể tạo một aspect instance liên kết với đối tượng `this` đó; các advice của instance này tiếp tục áp dụng cho những join point tương ứng.
3. Trong Spring AOP dựa trên proxy, `this` trong pointcut nói đến **proxy object**. Vì vậy có thể hiểu trực quan `perthis` là theo *proxy identity* thích hợp.
4. Spring cần có khả năng tạo nhiều instance aspect; vì vậy aspect bean phải có scope không phải singleton (mẫu thông dụng là `@Scope("prototype")`).

Đây là **đoạn minh họa cú pháp**, giả sử package `com.example.service` và các bean liên quan đã tồn tại, Spring AOP được bật. Không ghép thẳng đoạn này vào ví dụ chạy ở phần 6 vì hai ví dụ minh họa **hai chế độ khởi tạo khác nhau**.

### 4.3. `pertarget`: một aspect instance theo target object được khớp

```java
@Aspect("pertarget(execution(* com.example.service..*.*(..)))")
@Component
@Scope("prototype")
public class PerTargetAuditAspect {
    @Before("execution(* com.example.service..*.*(..))")
    public void audit() {
        // Trạng thái riêng cho target object đã khớp.
    }
}
```

`pertarget` cũng dựa vào lần đầu join point phù hợp xuất hiện, nhưng khóa liên kết instance aspect là **target object** thay cho `this`. Trong một AOP proxy đơn giản bao quanh một target cố định, `perthis` và `pertarget` thường cho kết quả số lượng instance giống nhau. Chúng khác nhau về ý nghĩa khi proxy và target không có quan hệ một-một (ví dụ cấu hình target source động hoặc có scoped proxy), nên **không được coi là hai tên khác nhau cho cùng một bean scope**.

Cẩn thận với cụm “mỗi target”: đây là từng **đối tượng target thực tế khớp** trong cơ chế AOP, không phải mỗi lần gọi method, mỗi tên bean, mỗi thread hoặc mỗi HTTP request theo mặc định.

### 4.4. Bảng các mô hình và hỗ trợ theo phiên bản Spring

| Cú pháp | Đơn vị instance aspect | Lưu ý trong Spring AOP |
| --- | --- | --- |
| `@Aspect` | Mặc định singleton trong context | Thích hợp cho cross-cutting logic stateless và phổ biến nhất. |
| `@Aspect("perthis(<pointcut>)")` | Theo `this` của matched join point (Spring proxy) | Cần aspect bean có scope có thể cấp instance mới, ví dụ `prototype`. |
| `@Aspect("pertarget(<pointcut>)")` | Theo target object của matched join point | Cần aspect bean non-singleton như `prototype`. |
| `@Aspect("pertypewithin(<type-pattern>)")` | Theo type phù hợp | **Phụ thuộc phiên bản:** tài liệu Spring Framework mới có ghi hỗ trợ, nhưng tài liệu của những phiên bản cũ không hỗ trợ. Phải kiểm tra đúng phiên bản Spring đang dùng trước khi áp dụng. |
| `percflow(...)` / `percflowbelow(...)` | Theo luồng điều khiển trong AspectJ | Không được hỗ trợ trong Spring AOP dựa trên proxy; cần cân nhắc cơ chế khác nếu thật sự cần. |

Ví dụ chạy trong bài dùng **Spring Framework 6.1.14** và chỉ minh họa mô hình aspect singleton, tương thích chắc chắn với phiên bản này. `perthis` và `pertarget` là các mô hình được tài liệu Spring 6.1 mô tả; `pertypewithin` đã thay đổi tình trạng hỗ trợ giữa các thế hệ tài liệu, nên không xây bài thực hành dựa vào nó.

### Vì sao `@Scope("prototype")` trên aspect không tương đương `perthis`?

Đây là một điểm đặc biệt quan trọng:

- `@Scope("prototype")` nói với **Spring BeanFactory** rằng khi được yêu cầu cấp bean theo definition này, nó có thể cấp instance mới.
- `@Aspect("perthis(...)")` hoặc `@Aspect("pertarget(...)")` nói với **Spring AOP** khi nào cần liên kết một instance aspect mới với đối tượng nào.
- Với aspect per-instance, hai cấu hình phối hợp: AOP xác định quy tắc liên kết, BeanFactory cung cấp instance mỗi khi cần.

Chỉ sửa:

```java
@Aspect
@Component
@Scope("prototype")
public class SomeAspect {
    // Không khai báo perthis/pertarget.
}
```

**không có nghĩa** Spring sẽ tự suy ra “mỗi target một aspect” hay “mỗi lời gọi một aspect”. Đây vẫn là **singleton instantiation model** vì `@Aspect` không có per-clause. Khi cần định lượng số instance thật sự, phải hiểu cách Spring sử dụng aspect instance factory theo phiên bản và kiểm tra runtime, không suy đoán từ tên scope.

Ở chiều ngược lại, khai báo `@Aspect("pertarget(...)")` nhưng để aspect bean singleton mặc định cũng là **cấu hình không phù hợp**: Spring cần khả năng tạo instance aspect độc lập và có thể báo lỗi khi phát hiện bean singleton với non-singleton instantiation model. Mẫu an toàn là phối hợp per-clause với `@Scope("prototype")`.

## 5. Ma trận tình huống: target scope không quyết định mô hình aspect

Giả sử mọi bean được Spring tạo, Spring AOP được bật, pointcut khớp các lời gọi công khai qua proxy và không có proxy/target source bất thường:

| Target bean | Aspect bean / instantiation | Số instance target nhìn thấy | Số instance aspect dùng cho các target |
| --- | --- | --- | --- |
| Singleton | `@Aspect` + aspect bean singleton | Một target trên mỗi bean definition | Một instance aspect singleton chung cho các lời gọi khớp |
| Prototype | `@Aspect` + aspect bean singleton | Nhiều target qua nhiều lần `getBean` | **Vẫn** một instance aspect dùng chung trong context |
| Hai singleton bean definition khác nhau | `@Aspect` + aspect bean singleton | Mỗi definition có target riêng | Một aspect instance có thể advise cả hai nếu pointcut khớp |
| Prototype | `@Aspect("perthis(...)")` + aspect bean prototype | Nhiều target/proxy riêng | Một aspect instance theo từng proxy phù hợp, không bắt buộc theo lần gọi |
| Prototype | `@Aspect("pertarget(...)")` + aspect bean prototype | Nhiều target riêng | Một aspect instance theo từng target phù hợp |
| Request/session với scoped proxy | `@Aspect` + aspect bean singleton | Target thay theo scope runtime | Aspect vẫn có thể dùng chung giữa các request/session |
| Request/session với scoped proxy | `perthis` hoặc `pertarget` | Proxy và target có thể khác vòng đời | Phải khảo sát proxy identity, target source và pointcut; không được suy đoán từ HTTP scope |

Số instance trong cột cuối mang tính **mô hình**: một aspect không khớp method nào sẽ không nhất thiết có cùng hành vi khởi tạo như khi có join point khớp; kết quả thực tế cũng phụ thuộc vào cấu hình scope, proxy và lifecycle.

## 6. Ví dụ chạy được: hai prototype target và một singleton aspect

Để nhìn rõ hai cơ chế độc lập, ta xây một ứng dụng **Spring Framework thuần**, không cần web server. Ví dụ dùng:
- Java 17+;
- Spring Framework `6.1.14`;
- `spring-context` và `spring-aop`;
- AspectJ Weaver trên classpath để Spring xử lý biểu thức trong `@Before`.

### 6.1. Cấu trúc project và Gradle

```text
spring-aop-scope/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/aop/
    ├── ScopeDemo.java
    ├── JobService.java
    └── AuditAspect.java
```

`settings.gradle`:

```groovy
rootProject.name = 'spring-aop-scope'
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
    mainClass = 'demo.aop.ScopeDemo'
}
```

### 6.2. Target service có ID riêng theo mỗi instance

`src/main/java/demo/aop/JobService.java`:

```java
package demo.aop;

import java.util.concurrent.atomic.AtomicInteger;

public class JobService {
    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private final int id = NEXT_ID.incrementAndGet();

    public void run(String name) {
        System.out.println("target#" + id + ":" + name);
    }
}
```

Biến `NEXT_ID` là công cụ quan sát **số instance `JobService`**, không phải biến của aspect. Với hai lần gọi `getBean(JobService.class)` trên definition `prototype`, hai target được tạo riêng và nhận `id` lần lượt là `1`, `2` trong chương trình này.

### 6.3. Aspect singleton với bộ đếm lời gọi chung

`src/main/java/demo/aop/AuditAspect.java`:

```java
package demo.aop;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    private final AtomicInteger calls = new AtomicInteger();

    @Before("execution(public void demo.aop.JobService.run(String))")
    public void beforeRun() {
        int value = calls.incrementAndGet();
        System.out.println("advice#" + value);
    }

    public int count() {
        return calls.get();
    }
}
```

Spring thấy `@Aspect` để tạo advisor. `AuditAspect` sẽ được đăng ký thành singleton bean trong config, nên **cùng đối tượng aspect** tăng bộ đếm khi hai prototype service chạy. `AtomicInteger` làm phép tăng thread-safe; ví dụ vẫn chạy tuần tự để stdout xác định. `count()` giúp kiểm tra aspect giữ trạng thái tích lũy.

### 6.4. Khởi tạo Spring context và so sánh các instance

`src/main/java/demo/aop/ScopeDemo.java`:

```java
package demo.aop;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Scope;

public class ScopeDemo {

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    public static class Config {

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public JobService jobService() {
            return new JobService();
        }

        @Bean
        public AuditAspect auditAspect() {
            return new AuditAspect();
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(Config.class)) {

            JobService first = context.getBean(JobService.class);
            JobService second = context.getBean(JobService.class);

            AuditAspect one = context.getBean(AuditAspect.class);
            AuditAspect two = context.getBean(AuditAspect.class);

            System.out.println("differentTargets=" + (first != second));
            System.out.println("sameAspect=" + (one == two));
            System.out.println("firstProxied=" + AopUtils.isAopProxy(first));
            System.out.println("secondProxied=" + AopUtils.isAopProxy(second));

            first.run("alpha");
            second.run("beta");

            System.out.println("adviceCount=" + one.count());
        }
    }
}
```

**Giải thích cấu hình:**
1. `@EnableAspectJAutoProxy(proxyTargetClass = true)` bật cơ chế Spring AOP và ưu tiên class-based proxy, nên có thể lấy proxy bằng kiểu `JobService`.
2. `@Bean @Scope(PROTOTYPE)` làm mỗi lần `context.getBean(JobService.class)` tạo service khác nhau.
3. `@Bean AuditAspect` không có `@Scope` nên mặc định là singleton bean. `@Aspect` trên lớp chỉ định mô hình khởi tạo aspect singleton.
4. `@Before` áp dụng cho `JobService.run(String)` khi lời gọi đi qua proxy. Cả hai proxy dùng cùng aspect bean.
5. `AopUtils.isAopProxy` phân biệt bean được Spring trả về với target gốc. Test `first != second` đo tham chiếu bean được trả về; ID trong `run` cho thấy target thực sự khác nhau.

### 6.5. Lệnh chạy và kết quả mong đợi

Trong thư mục dự án, dùng một Gradle installation có thể chạy project:

```bash
gradle run
```

Nếu đã tạo Gradle Wrapper trong dự án, có thể dùng `./gradlew run` (Linux/macOS) hoặc `gradlew.bat run` (Windows).

Những dòng do **chương trình Java** in ra (bỏ qua banner/log của Gradle hoặc môi trường):

```text
differentTargets=true
sameAspect=true
firstProxied=true
secondProxied=true
advice#1
target#1:alpha
advice#2
target#2:beta
adviceCount=2
```

Nhận xét:
- `differentTargets=true`: hai yêu cầu bean prototype tạo hai Spring-managed instances riêng.
- `sameAspect=true`: hai lần xin aspect singleton đều được cấp cùng tham chiếu.
- Hai kết quả `firstProxied=true` và `secondProxied=true`: target prototype cũng có thể được Spring AOP proxy.
- Bộ đếm advice tăng `1 → 2` **trên cùng instance aspect**, không reset về `1` khi chuyển sang target thứ hai.
- Advice chạy *trước* target vì là `@Before`. Nếu dùng `@Around` với `proceed()`, thứ tự log tùy logic bao quanh được viết.

Đây là minh chứng cụ thể cho kết luận **“target prototype” không có nghĩa “aspect pertarget”**.

## 7. Cấu hình aspect gắn với target: hai cơ chế phối hợp

Nếu nghiệp vụ thực sự cần **state riêng theo từng target**, Spring AOP hỗ trợ `@Aspect("pertarget(...)")`. Bean definition của aspect phải cho phép Spring cấp nhiều instance, thường bằng `prototype`; chỉ đổi scope hoặc chỉ thêm per-clause riêng lẻ không đủ để có mô hình đúng.

```java
@Aspect("pertarget(execution(public void demo.aop.JobService.run(String)))")
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PerTargetAuditAspect {
    @Before("execution(public void demo.aop.JobService.run(String))")
    public void beforeRun() {
        // Advice thuộc aspect instance liên kết với target tương ứng.
    }
}
```

Đoạn trên **minh họa cấu hình**, cần thêm imports và component scan phù hợp; không ghép vào demo singleton ở mục 6. Nếu đăng ký aspect bằng phương thức `@Bean` thay cho `@Component`, đặt `@Scope(PROTOTYPE)` trên bean definition tương ứng. Với `perthis(...)`, Spring AOP liên kết instance theo **proxy** thay vì target. Bài kế tiếp thực hành riêng cả ba mô hình `singleton`, `perthis`, `pertarget` và cách quan sát identity của các đối tượng.

Per-clause theo proxy/target **không phải** scope request hay thread-local: nhiều lời gọi đồng thời trên cùng target vẫn có thể dùng cùng aspect instance. Khi chỉ cần dữ liệu theo request, ưu tiên tham số advice hoặc collaborator có scope phù hợp.

## 8. Những phép suy luận sai hay gặp trong Spring

| Câu nói sai | Vì sao sai | Cách hiểu đúng |
| --- | --- | --- |
| “Service `prototype` nên mỗi service có aspect riêng.” | Target bean scope và aspect instantiation là hai trục. | Aspect `@Aspect` mặc định có thể được chia sẻ giữa nhiều prototype target. |
| “`@Aspect` là bean dù không đăng ký gì.” | `@Aspect` không phải annotation đăng ký component. | Đăng ký aspect qua `@Component`, `@Bean` hoặc cách tương đương. |
| “`@Scope("prototype")` trên aspect có nghĩa một aspect trên mỗi method call.” | Scope chỉ quy định cách BeanFactory cấp bean theo request tạo bean; không diễn đạt quy tắc AOP per-method. | Dùng đúng per-clause nếu muốn instance gắn với proxy/target. |
| “`@Aspect("pertarget(...)")` một mình là đủ.” | Instance model per-target cần aspect bean non-singleton. | Kết hợp với scope cho phép tạo nhiều aspect instances, thường `prototype`. |
| “Có hai bean definition singleton cùng lớp thì chỉ một object.” | Spring singleton thuộc **mỗi bean definition**, không phải class trong JVM. | Hai definition có thể có hai instance độc lập. |
| “Hai HTTP request luôn có hai aspect.” | HTTP request scope của **target** không quyết định aspect instance. | Xem mô hình của aspect và cấu hình scoped proxy. |
| “Một aspect singleton có field đếm tự an toàn khi concurrent.” | Nhiều thread có thể đồng thời dùng chung một aspect. | Giữ aspect stateless hoặc dùng dữ liệu đồng bộ/thread-safe đúng nhu cầu. |
| “`@Lazy` biến singleton thành prototype.” | `@Lazy` đổi thời điểm tạo, không đổi scope. | Scope vẫn là singleton trừ khi được cấu hình khác. |
| “AOP proxy và scoped proxy luôn là một.” | Một proxy dùng để chặn advice; proxy còn lại đại diện bean theo scope và chọn target. | Xác định cả hai lớp proxy khi debug. |
| “Gọi `this.run()` bên trong target vẫn đi qua advice.” | Self-invocation thường bỏ qua Spring AOP proxy. | Gọi qua Spring proxy từ bên ngoài hoặc thiết kế lại boundary. |
| “`perthis` cũng là mỗi thread.” | `this` của Spring AOP là proxy tại join point, không phải thread. | Chọn instance model theo đối tượng, không theo suy đoán concurrency. |

### Vì sao scoped proxy khiến `perthis` và `pertarget` phức tạp hơn?

Lấy một bean `request` được inject vào singleton qua **một scoped proxy**. Singleton thường giữ nguyên **proxy reference**, còn scoped proxy có thể chọn **target khác nhau tùy request**.

Trong cấu hình đó:
- Nếu pointcut `perthis` nhìn thấy cùng proxy identity, không thể suy ra có một aspect instance mới cho từng request chỉ vì target request thay đổi.
- `pertarget` được định nghĩa theo target object của join point, nhưng tương tác cụ thể với chuỗi nhiều proxy và target source cần kiểm tra đúng runtime/version.
- `@RequestScope` hoặc `@Scope(proxyMode = ...)` giải quyết vòng đời và lookup; chúng **không phải cú pháp thay thế** cho `@Aspect("perthis(...)")` hoặc `@Aspect("pertarget(...)")`.

Vì vậy, khi logic cần xử lý dữ liệu request, giải pháp bền vững thường là **dịch chuyển trạng thái request vào bean request-scoped rõ ràng** hoặc truyền thông tin vào advice bằng `JoinPoint`/parameters, thay vì dựa vào việc “aspect chắc sẽ có số lượng instance đúng bằng số request”.

## 9. Checklist chẩn đoán số lượng instance ở runtime

Khi quan sát aspect/service không như dự đoán:

1. **Xác định bean definition**: service được đăng ký bằng `@Component` hay `@Bean`? Có nhiều definition trùng class không? Có context cha–con không?
2. **Kiểm tra bean scope**: `singleton`, `prototype`, `request`, `session`? Nếu inject prototype vào singleton, prototype được cấp lúc nào?
3. **Kiểm tra aspect bean**: nó đã được Spring đăng ký chưa, scope là gì và được hạ tầng AOP phát hiện chưa?
4. **Kiểm tra per-clause**: `@Aspect` mặc định, `perthis`, `pertarget` hay một cú pháp không được phiên bản hiện tại hỗ trợ?
5. **Kiểm tra proxy và target**: `AopUtils.isAopProxy(bean)`, `AopUtils.isJdkDynamicProxy(bean)`, `AopUtils.isCglibProxy(bean)`; xem có thêm scoped proxy hay `TargetSource` động không.
6. **Kiểm tra lời gọi**: client có lấy bean từ context và gọi qua proxy? Điểm cắt có khớp method execution thực tế? Có self-invocation không?
7. **Kiểm tra concurrent state**: advice có dùng field mutable dùng chung không? Bộ đếm có cần `AtomicInteger`/`AtomicLong` hoặc tránh trạng thái không?
8. **Đọc lỗi khởi tạo**: nếu per-clause cần nhiều aspect instance nhưng bean bị cấu hình singleton, xử lý nguyên nhân scope/model; đừng tắt AOP chỉ để bỏ lỗi.

Ví dụ đoạn kiểm tra proxy (giả sử `service` là một biến Spring bean có sẵn, không phải chương trình chạy độc lập):

```java
System.out.println(AopUtils.isAopProxy(service));
System.out.println(AopUtils.isJdkDynamicProxy(service));
System.out.println(AopUtils.isCglibProxy(service));
```

Ba giá trị trên nói về **đối tượng proxy**, chưa đủ kết luận số lượng aspect instance. Muốn kiểm tra aspect, đối chiếu thêm các bean definition/aspect scope và theo dõi instance identity khi cần.

## 10. Kết luận và nguyên tắc thiết kế

Trong Spring, có hai bước cần tách bạch:

```text
Spring IoC:
  Bean definition + @Scope
        -> tạo, cấp, quản lý target bean / aspect bean theo scope

Spring AOP:
  @Aspect + per-clause + pointcut + advice
        -> chọn proxy/target phù hợp, gắn aspect instance,
           thực thi advice khi method được gọi qua Spring proxy
```

**Quy tắc thực hành:** Chọn bean scope theo vòng đời dependency/target; chọn aspect instantiation model theo *vòng đời trạng thái của advice*. Với phần lớn Spring AOP như logging, metrics và kiểm tra boundary, dùng aspect singleton không giữ trạng thái của từng lời gọi. Chỉ dùng `perthis`/`pertarget` khi thật sự cần trạng thái theo proxy/target và đã kiểm chứng trong bối cảnh Spring proxy thực tế.

### Tài liệu đối chiếu

- [Spring Framework — Aspect Instantiation Models](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/instantiation-models.html): mô hình singleton, `perthis`, `pertarget` và khả năng hỗ trợ phụ thuộc phiên bản.
- [Spring Framework — Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html): `singleton`, `prototype`, web scope, dependency và scoped proxy.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): kích hoạt Spring AOP, đăng ký aspect và yêu cầu dependency.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): proxy, `this`/target và self-invocation.
- [Spring Framework 6.1 — BeanFactoryAspectJAdvisorsBuilder Javadoc](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/aop/aspectj/annotation/BeanFactoryAspectJAdvisorsBuilder.html): cách Spring tìm các `@Aspect` bean và xây dựng advisor.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-beans-009">Mô hình singleton, perthis và pertarget được Spring hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# Mô hình singleton, perthis và pertarget được Spring AOP hỗ trợ

## 1. Học gì trong bài này?

Trong Spring AOP, aspect được Spring quản lý như một bean, nhưng **số lượng instance của aspect** có thể được quyết định theo những mô hình khác nhau. Ba mô hình quan trọng cần hiểu là:

- **singleton**: một instance aspect dùng chung cho nhiều proxy/lời gọi trong phạm vi bean và ApplicationContext.
- **perthis**: một instance aspect được gắn với từng đối tượng **Spring AOP proxy** phù hợp.
- **pertarget**: một instance aspect được gắn với từng đối tượng **target** phía sau proxy phù hợp.

Đây là **aspect instantiation models**, tức chính sách liên kết và tạo instance aspect trong Spring AOP. Chúng khác với các khái niệm bean scope như singleton, prototype, request hoặc session. Đặc biệt, với perthis/pertarget, ta phải cấu hình bean aspect theo **prototype** để Spring có thể tạo các instance theo từng proxy hoặc target.

Mục tiêu thực hành:

1. Viết annotation **@Aspect** đúng cho từng mô hình.
2. Đăng ký aspect bean và bật **@EnableAspectJAutoProxy**.
3. Thấy khác biệt giữa một bộ đếm duy nhất và bộ đếm riêng theo từng đối tượng được advise.
4. Giải thích rõ Spring AOP phân biệt **this = proxy** và **target = đối tượng được proxy bọc**.
5. Tránh lỗi cấu hình singleton bean cho perthis/pertarget, cùng các nhầm lẫn về số bean, số lời gọi và dữ liệu đồng thời.

**Phạm vi:** Chỉ xem xét hành vi của **Spring AOP với @AspectJ annotation style** trong **ApplicationContext**. Không giảng lại lý thuyết AspectJ nói chung hoặc chuyển sang weaving bằng trình biên dịch/Java agent.

## 2. Vì sao mô hình tạo instance quan trọng?

Giả sử có hai bean service độc lập:

~~~text
ApplicationContext
  |
  +-- first  --> Spring proxy P1 --> JobImpl target T1
  |
  +-- second --> Spring proxy P2 --> JobImpl target T2
  |
  +-- aspect definition (advice cho Job.execute)
~~~

Client lần lượt gọi:

~~~text
first.execute()
second.execute()
first.execute()
second.execute()
~~~

Nếu aspect có trường **counter**, câu hỏi đặt ra là counter dùng chung hay riêng?

| Mô hình | Gắn instance aspect vào | Với P1→T1 và P2→T2 | Bộ đếm nếu mỗi lời gọi khớp chạy advice |
| --- | --- | --- | --- |
| singleton | Một instance aspect dùng chung | 1 instance aspect | 1, 2, 3, 4 |
| perthis | Từng proxy P1, P2 | 2 instance aspect | P1: 1→2; P2: 1→2 |
| pertarget | Từng target T1, T2 | 2 instance aspect | T1: 1→2; T2: 1→2 |

**Bảng trên chỉ minh họa hai proxy trỏ đến hai target khác nhau.** Vì vậy perthis và pertarget có kết quả giống nhau trong tình huống này. Điểm làm hai mô hình khác nhau là **mối quan hệ proxy–target**, đặc biệt khi nhiều proxy dùng chung một target.

Không được hiểu perthis là “một instance cho từng request”, pertarget là “một instance cho từng lời gọi”, hoặc prototype bean là “đương nhiên tạo mới mỗi lần advice chạy”. Đây là ba nhầm lẫn phổ biến.

## 3. Mô hình mặc định: singleton

Khai báo:

~~~java
@Aspect
public class SingletonCounter {
    // Các trường của instance này được dùng chung
}
~~~

Spring AOP mặc định sử dụng mô hình **singleton aspect**. Khi aspect được khai báo như Spring bean mặc định qua **@Component** hoặc **@Bean**, ta thường có một instance aspect trong context cho bean definition đó.

Nếu cùng aspect được dùng để advise nhiều service, advice sẽ chạy trên cùng instance aspect. Điều này hữu ích với aspect không giữ trạng thái theo đối tượng như logging, metrics, xác thực chính sách hoặc đánh dấu tracing. Tuy nhiên, lưu dữ liệu của từng invocation trong mutable field của singleton aspect có thể tạo race condition khi nhiều thread cùng gọi.

Phân biệt hai ý:

- **Mô hình singleton aspect**: một instance aspect cho những join point liên quan đến aspect đó.
- **Singleton Spring bean scope**: cùng bean name trong một container thường luôn trả về cùng instance.

Trong cấu hình thông dụng, hai khái niệm đi cùng nhau, nhưng không nên coi chúng là một annotation duy nhất. Spring có thể hỗ trợ aspect model singleton trong những trường hợp bean definition không khai báo singleton; lúc đó vòng đời Spring bean và vòng đời aspect cần được phân tích riêng. Cách dễ kiểm soát cho ví dụ cơ bản vẫn là một bean aspect singleton.

## 4. Mô hình perthis: một instance theo proxy

Khai báo:

~~~java
@Aspect("perthis(execution(* demo.Job.execute(..)))")
public class PerThisCounter {
    // Mỗi proxy phù hợp có state của aspect riêng
}
~~~

Trong Spring AOP, **this** tại join point chỉ tới **đối tượng proxy** mà client gọi. **perthis(...)** liên kết instance aspect với đối tượng proxy phù hợp với biểu thức bên trong mệnh đề perthis.

Với sơ đồ:

~~~text
P1 (proxy) ---> T1 (target)  ---> aspect A1
P2 (proxy) ---> T2 (target)  ---> aspect A2
~~~

A1 và A2 có thể giữ trạng thái riêng. Instance liên kết được khởi tạo khi đối tượng tương ứng lần đầu tham gia một join point khớp mệnh đề perthis; nó không cần được tạo sẵn chỉ vì ApplicationContext khởi động. Trước khi aspect instance được tạo, advice thuộc instance đó chưa thể chạy; sau khi đã tạo, advice chạy ở các join point phù hợp của đối tượng liên kết. Không được mặc định rằng mọi method trên cùng service sẽ đều khớp mệnh đề hoặc mọi lời gọi Java đều bị Spring AOP chặn.

**Điều kiện Spring quan trọng:** Bean definition của **PerThisCounter** phải là **prototype**, ví dụ:

~~~java
@Bean
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public PerThisCounter perThisCounter() {
    return new PerThisCounter();
}
~~~

Chỉ dùng **@Aspect("perthis(...)")** trên một **@Component** singleton mặc định là cấu hình sai. Spring không thể cấp một instance aspect khác nhau cho từng proxy nếu bean aspect luôn trả về một singleton; có thể xuất hiện lỗi về aspect instantiation model không tương thích với singleton bean.

## 5. Mô hình pertarget: một instance theo target

Khai báo:

~~~java
@Aspect("pertarget(execution(* demo.Job.execute(..)))")
public class PerTargetCounter {
    // Mỗi target phù hợp có state của aspect riêng
}
~~~

Spring AOP bọc **target** bằng một proxy. **pertarget(...)** liên kết aspect instance với target tương ứng tại những join point phù hợp. Với một proxy cho mỗi target, số aspect instance có vẻ giống perthis. Khác biệt rõ ràng ở trường hợp hai proxy P1 và P2 cùng bọc một đối tượng T:

~~~text
PERTHIS                         PERTARGET

P1 ------> T                    P1 ------> T
  |                               |        ^
  +---- aspect A1                 |        |
                                 +------ aspect A
P2 ------> T                      |        ^
  |                               |        |
  +---- aspect A2                 P2 ------> T

2 instance aspect                  1 instance aspect
nếu P1/P2 đều khớp                 cho cùng target T
~~~

Đây là **mô hình khái niệm** để phân biệt identity, không phải chương trình độc lập tạo hai proxy thủ công. Khi Spring tạo proxy, các cấu hình wrapper/advisor có thể khiến cách tổ chức target trở nên phức tạp; không được suy ra chắc chắn số proxy/target chỉ từ số bean name.

Giống perthis, bean aspect pertarget phải là bean **không phải singleton**, thực tế nên khai báo **prototype**:

~~~java
@Bean
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public PerTargetCounter perTargetCounter() {
    return new PerTargetCounter();
}
~~~

**Prototype ở đây là cơ chế cung cấp các instance aspect mới theo yêu cầu Spring AOP.** Không có nghĩa Spring tạo một aspect mới trên mỗi lần gọi `execute()`. Với cùng target đã được liên kết, các lời gọi phù hợp tiếp tục dùng instance aspect của target đó.

## 6. @Scope("prototype") và per-clause không thay thế nhau

Đây là phần đặc biệt quan trọng khi làm việc với **Spring bean** và **Spring AOP**.

| Cấu hình | Ý nghĩa | Kết luận |
| --- | --- | --- |
| **@Aspect** với bean singleton | Mô hình aspect mặc định | Một instance dùng chung |
| **@Aspect("perthis(...)")** với bean prototype | Aspect instance gắn theo proxy | Phù hợp mô hình perthis |
| **@Aspect("pertarget(...)")** với bean prototype | Aspect instance gắn theo target | Phù hợp mô hình pertarget |
| **@Aspect("perthis(...)")** nhưng aspect bean singleton | Per-clause cần nhiều instance, bean chỉ có một | Sai cấu hình; Spring có thể báo lỗi |
| **@Scope("prototype")** nhưng chỉ **@Aspect** không có per-clause | Mỗi lần lấy prototype bean có thể tạo instance mới, nhưng aspect model vẫn mặc định singleton | Không tự tạo ngữ nghĩa perthis/pertarget |

Lưu ý kỹ thuật: **@Scope** áp dụng cho Spring bean definition, còn **perthis/pertarget** nằm trong metadata mô tả **aspect instantiation model**. Spring AOP kiểm tra hai mặt cấu hình để xây dựng aspect instance factory phù hợp.

Nếu dùng **@Component** thay **@Bean**, cần đặt scope trực tiếp trên lớp:

~~~java
@Aspect("pertarget(execution(* demo.Job.execute(..)))")
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PerTargetCounter {
    // ...
}
~~~

Đoạn trên là **phương án component scan**, không dùng đồng thời với phương án đăng ký cùng aspect bằng **@Bean**. Phải có **@ComponentScan** bao phủ package chứa class và **@EnableAspectJAutoProxy** trong context liên quan.

## 7. Dự án Java 17+ để quan sát cả ba mô hình

Ví dụ dùng **Spring Framework 6.2.12**, **Java 17+** và **Maven 3.9+**. Ta tạo ba ApplicationContext **độc lập**, mỗi context đăng ký **một loại aspect** để log không bị trộn với advice của mô hình khác.

Cấu trúc:

~~~text
spring-aop-instantiation/
├── pom.xml
└── src/main/java/demo/
    ├── Job.java
    ├── JobImpl.java
    ├── SingletonCounter.java
    ├── PerThisCounter.java
    ├── PerTargetCounter.java
    └── App.java
~~~

### 7.1. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-instantiation</artifactId>
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

**spring-context** cung cấp Java-based ApplicationContext và kéo theo Spring AOP. **aspectjweaver** cung cấp các kiểu annotation/pointcut mà Spring dùng để xử lý phong cách **@AspectJ**. Ví dụ vẫn dùng **proxy-based Spring AOP**, không dùng weaving agent.

### 7.2. Service interface và target

**src/main/java/demo/Job.java**

~~~java
package demo;

public interface Job {
    void execute();
}
~~~

**src/main/java/demo/JobImpl.java**

~~~java
package demo;

public class JobImpl implements Job {

    private final String label;

    public JobImpl(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public void execute() {
        System.out.println("SERVICE " + label);
    }
}
~~~

Tạo hai instance khác nhau **JobImpl("A")** và **JobImpl("B")** bằng hai phương thức **@Bean**. Client chỉ lấy kiểu **Job**, vì với interface và cấu hình mặc định, Spring Framework thuần có thể tạo **JDK dynamic proxy**.

### 7.3. Singleton aspect

**src/main/java/demo/SingletonCounter.java**

~~~java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class SingletonCounter {

    private int calls;

    @Before("execution(* demo.Job.execute(..))")
    public void before(JoinPoint joinPoint) {
        calls++;
        String label = ((JobImpl) joinPoint.getTarget()).label();
        System.out.println("ASPECT singleton count=" + calls
                + " target=" + label);
    }
}
~~~

Một instance được đăng ký vào context. Mọi advice invocation **ở context đó** làm tăng cùng một trường **calls**. Bộ đếm `int` cố tình đơn giản vì chương trình chạy tuần tự; nó không an toàn khi nhiều thread đồng thời cập nhật.

### 7.4. Perthis aspect

**src/main/java/demo/PerThisCounter.java**

~~~java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect("perthis(execution(* demo.Job.execute(..)))")
public class PerThisCounter {

    private int calls;

    @Before("execution(* demo.Job.execute(..))")
    public void before(JoinPoint joinPoint) {
        calls++;
        String label = ((JobImpl) joinPoint.getTarget()).label();
        System.out.println("ASPECT perthis count=" + calls
                + " target=" + label);
    }
}
~~~

Mệnh đề **perthis(...)** quyết định **lúc nào và với proxy nào** một instance được liên kết. Advice **@Before(...)** là logic chạy tại các join point phù hợp sau khi instance đã có thể được dùng. Trong ví dụ, cả hai đều nói tới **Job.execute()** để dễ kiểm tra.

Chỉ dùng lớp này khi bean definition **PerThisCounter** được cấu hình prototype như ở mục 7.6.

### 7.5. Pertarget aspect

**src/main/java/demo/PerTargetCounter.java**

~~~java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect("pertarget(execution(* demo.Job.execute(..)))")
public class PerTargetCounter {

    private int calls;

    @Before("execution(* demo.Job.execute(..))")
    public void before(JoinPoint joinPoint) {
        calls++;
        String label = ((JobImpl) joinPoint.getTarget()).label();
        System.out.println("ASPECT pertarget count=" + calls
                + " target=" + label);
    }
}
~~~

Dòng **joinPoint.getTarget()** dùng để lấy **target object** tại invocation. Đây không phải API quyết định pertarget; chính metadata của **@Aspect("pertarget(...)")** chỉ định mô hình. Khi một instance target tiếp tục được gọi qua proxy phù hợp, state trong aspect liên kết với nó được tái sử dụng.

### 7.6. Ba cấu hình Spring riêng biệt

**src/main/java/demo/App.java**

~~~java
package demo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;

public class App {

    @Configuration(proxyBeanMethods = false)
    static class ServiceBeans {

        @Bean("first")
        Job first() {
            return new JobImpl("A");
        }

        @Bean("second")
        Job second() {
            return new JobImpl("B");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ServiceBeans.class)
    @EnableAspectJAutoProxy
    static class SingletonConfig {

        @Bean
        SingletonCounter singletonCounter() {
            return new SingletonCounter();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ServiceBeans.class)
    @EnableAspectJAutoProxy
    static class PerThisConfig {

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        PerThisCounter perThisCounter() {
            return new PerThisCounter();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ServiceBeans.class)
    @EnableAspectJAutoProxy
    static class PerTargetConfig {

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        PerTargetCounter perTargetCounter() {
            return new PerTargetCounter();
        }
    }

    private static void run(String mode, Class<?> config) {
        try (var context =
                     new AnnotationConfigApplicationContext(config)) {
            Job first = context.getBean("first", Job.class);
            Job second = context.getBean("second", Job.class);

            System.out.println("=== " + mode + " ===");
            first.execute();
            second.execute();
            first.execute();
            second.execute();
        }
    }

    public static void main(String[] args) {
        run("singleton", SingletonConfig.class);
        run("perthis", PerThisConfig.class);
        run("pertarget", PerTargetConfig.class);
    }
}
~~~

**Đọc kỹ cấu hình:**

1. **ServiceBeans** tạo hai bean tên **first** và **second**. Không phải một bean hai tên alias: mỗi phương thức tạo **target JobImpl khác nhau**.
2. Mỗi context đều dùng **@Import(ServiceBeans.class)** và **@EnableAspectJAutoProxy**.
3. Mỗi context chỉ có **một** aspect bean definition; không cần component scan và không vô tình đăng ký cả ba aspect.
4. **SingletonConfig** tạo bean aspect singleton theo mặc định; không có per-clause.
5. **PerThisConfig** tạo bean aspect prototype, có perthis trong class metadata.
6. **PerTargetConfig** cũng tạo bean aspect prototype, có pertarget trong class metadata.
7. Ba context được tạo và đóng tuần tự. **Không gộp số instance giữa ba context**; số instance phụ thuộc từng ApplicationContext.
8. Các phương thức **@Bean** dùng package-private trong ví dụ cùng package. Lớp cấu hình được khai báo **static nested class** để Spring có thể nạp không cần instance của outer App.

Việc **@Bean** gọi **new** không làm mất Spring AOP: object được trả cho container quản lý. Ngược lại, nếu client tự tạo **new JobImpl("A")** và gọi trực tiếp, Spring proxy không nằm trên đường gọi nên advice không chạy.

### 7.7. Chạy chương trình

Từ thư mục **spring-aop-instantiation**, chạy:

~~~bash
mvn -q clean compile exec:java
~~~

**Các dòng logic theo thứ tự gọi trong chương trình:**

Đối với **singleton**, mỗi lời gọi đều dùng cùng một aspect instance, vì vậy đầu ra phần này là:

~~~text
=== singleton ===
ASPECT singleton count=1 target=A
SERVICE A
ASPECT singleton count=2 target=B
SERVICE B
ASPECT singleton count=3 target=A
SERVICE A
ASPECT singleton count=4 target=B
SERVICE B
~~~

Trong hai phần **perthis** và **pertarget**, vì **first** và **second** có hai proxy và hai target độc lập, state advice sẽ được quản lý theo từng đối tượng tương ứng. Ta kiểm tra rằng dòng log **ASPECT perthis/perttarget** xuất hiện trước dòng **SERVICE** của những lời gọi đã được advice, và thấy bộ đếm của A và B tăng **độc lập** (các lần advice trên A: 1, 2; các lần advice trên B: 1, 2).

**Lưu ý về lần gọi đầu:** Mệnh đề per-clause dùng join point đầu tiên để kích hoạt việc tạo instance; tài liệu Spring mô tả rằng trước khi instance được tạo, advice của instance đó chưa chạy. Vì vậy không nên dựa vào một dòng log duy nhất của lần gọi đầu để đo số lần aspect được khởi tạo. Khi kiểm thử lifecycle sâu, hãy kiểm tra đủ chuỗi invocation và quan sát trạng thái sau khi instance đã gắn với proxy/target.

Để quan sát chính xác hơn khi debug, đặt breakpoint vào constructor của **PerThisCounter** và **PerTargetCounter**, ghi lại identity của aspect instance, sau đó đặt breakpoint ở **before(...)**. Bạn có thể phân biệt **thời điểm khởi tạo** với **thời điểm một advice invocation bắt đầu**.

> Các kết quả so sánh số lượng instance trong bài là **mô hình expected behavior** theo cấu hình. Những đoạn code minh họa chỉ dựa trên stdout được nêu rõ là quan sát logic; khi chương trình chạy trong IDE/Maven, các thông báo framework riêng không nằm trong phần expected output.

## 8. Phân biệt chính xác proxy (this) và target

Trong Spring AOP, đối tượng mà client giữ thường là proxy. Tại join point:

- **this**: tham chiếu tới **Spring AOP proxy**.
- **target**: tham chiếu tới **đối tượng ứng dụng** nằm phía sau proxy.

Hình dung hai đối tượng proxy cùng trỏ đến target T:

| Identity | perthis | pertarget |
| --- | --- | --- |
| P1 → T | Aspect instance A1 | Aspect instance A |
| P2 → T | Aspect instance A2 | Vẫn aspect instance A |
| Gọi lại P1 → T | Dùng A1 | Dùng A |
| Gọi lại P2 → T | Dùng A2 | Dùng A |

Nếu P1 và P2 là hai **alias bean name cùng trả về một proxy**, không có hai proxy identity khác nhau; perthis không tạo thêm instance chỉ vì đổi tên alias. Ngược lại, hai target khác nhau thường dẫn tới hai liên kết pertarget dù cùng lớp triển khai.

**Proxy tạo bằng JDK hay class-based proxy** chỉ quyết định cách Spring tạo đối tượng trung gian và phương thức nào có thể được intercept. Quy tắc phân biệt proxy identity/target identity vẫn cần được áp dụng khi phân tích perthis/pertarget.

### Phân biệt biến this trong Java và this trong pointcut

Trong class **JobImpl**, **this** của Java là object target khi phương thức đang chạy. Trong biểu thức Spring AOP **this(...)**, ngữ nghĩa chọn/ghép đối tượng lại nói tới **proxy** của Spring AOP. Không suy luận ngược chỉ từ từ khóa giống nhau.

**joinPoint.getThis()** trả proxy; **joinPoint.getTarget()** trả target. Đoạn kiểm tra minh họa bên trong advice:

~~~java
Object proxy = joinPoint.getThis();
Object target = joinPoint.getTarget();
System.out.println("SAME_OBJECT " + (proxy == target));
~~~

Đoạn này là **fragment thêm vào advice**, chưa phải file Java độc lập. Với proxy thực sự bọc target riêng biệt, giá trị thường là **false**. Kết quả này chưa tự chứng minh đang dùng perthis hay pertarget; phải kết hợp với mô hình aspect đã khai báo và identity của các đối tượng.

## 9. Mối quan hệ giữa per-clause và advice pointcut

Ví dụ:

~~~java
@Aspect("perthis(execution(* demo.Job.execute(..)))")
public class DemoAspect {

    @Before("execution(* demo.Job.execute(..))")
    public void beforeExecution() {
        // ...
    }
}
~~~

Có **hai biểu thức**:

1. **Trong perthis(...)**: mô tả các join point kích hoạt/liên kết một aspect instance với proxy.
2. **Trong @Before(...)**: mô tả những join point mà logic advice muốn xử lý.

Hai biểu thức có thể khác nhau. Tuy nhiên, nếu chọn một per-clause quá hẹp so với advice, advice có thể không có instance gắn với proxy/target cho lời gọi mong muốn. Nếu quá rộng, nhiều service có thể được gắn instance không cần thiết.

Trong Spring AOP, chỉ các **method-execution join point trên Spring bean** được proxy hỗ trợ. Việc dùng **call(...)**, truy cập field, constructor execution hoặc những designator không được Spring AOP hỗ trợ sẽ không trở thành hợp lệ chỉ nhờ perthis hoặc pertarget. Với JDK proxy, các lời gọi public method trên interface của proxy là đường đi chuẩn.

**Self-invocation** vẫn là giới hạn: nếu phương thức trên target gọi **this.execute()** ngay bên trong, lời gọi Java trực tiếp đó không quay qua Spring proxy; không có invocation advisor mới chỉ vì aspect dùng perthis hoặc pertarget.

## 10. Khi nào aspect instance được tạo và hủy?

### 10.1. Singleton

Spring container thường tạo aspect singleton khi khởi tạo các singleton cần thiết trong context (trừ khi có lazy initialization hoặc cấu hình đặc biệt). Một instance đó tồn tại cùng vòng đời Spring bean, và Spring quản lý các callback thích hợp khi đóng context.

Khi aspect là singleton, **không tạo mới per-proxy hoặc per-method-call** chỉ vì hai service khác nhau được advise.

### 10.2. Perthis và pertarget

Spring AOP sử dụng một aspect instance factory có khả năng cung cấp nhiều instance khi cần. Mỗi proxy hoặc target phù hợp được gắn một instance riêng sau khi per-clause khớp. Instance đó gắn với đối tượng tương ứng; không phải lifecycle request/session hay đơn vị thread.

Với **Spring prototype scope**, container tạo và inject bean instance nhưng theo mặc định **không đảm bảo gọi destroy callback cho tất cả các instance prototype**. Do đó, không nên đặt các tài nguyên cần cleanup phức tạp trực tiếp vào từng aspect perthis/pertarget rồi mặc nhiên tin rằng đóng context sẽ hủy từng instance như singleton. Nếu aspect cần kết nối, pool, cache hoặc tài nguyên sống dài, nên inject một Spring bean quản lý vòng đời riêng, tránh mỗi aspect instance tự sở hữu tài nguyên khó thu hồi.

Việc tạo nhiều instance cũng không có nghĩa aspect giữ state của **mỗi yêu cầu HTTP**. Nhiều request có thể gọi cùng proxy và cùng target; khi đó vẫn truy cập một aspect instance tương ứng và cần thiết kế thread-safe.

## 11. Vì sao không dùng @Scope("request") hoặc @Scope("prototype") thay perthis?

Nhiều Spring bean scope thường gây nhầm với AOP instantiation model:

| Cơ chế | Quyết định theo | Câu hỏi phù hợp |
| --- | --- | --- |
| Bean **singleton** | Bean name trong Spring container | Mỗi lần lấy cùng bean trả object nào? |
| Bean **prototype** | Mỗi lần container được yêu cầu tạo/lấy bean | Có thể tạo một bean instance mới không? |
| Bean **request** | Web request scope đang hoạt động | Mỗi request dùng object nào? |
| Aspect **perthis** | Proxy identity ở join point khớp | Một proxy liên kết với aspect instance nào? |
| Aspect **pertarget** | Target identity ở join point khớp | Một target liên kết với aspect instance nào? |

**@Scope("prototype") trên aspect perthis/pertarget là điều kiện để Spring cấp instance theo mô hình aspect**, không có nghĩa hai thuật ngữ tương đương. Nếu yêu cầu thật sự là đếm giao dịch theo HTTP request, perthis/pertarget có thể không phù hợp: cùng service proxy singleton phục vụ nhiều request sẽ vẫn dùng chung instance aspect. Khi đó cần xử lý dữ liệu theo request bằng dependency/scope thích hợp, thay vì gán ý nghĩa request cho perthis.

## 12. Lỗi cấu hình và cách chẩn đoán

### 12.1. Khai báo perthis hoặc pertarget trên aspect bean singleton

Ví dụ sai:

~~~java
@Aspect("perthis(execution(* demo.Job.execute(..)))")
@Component
public class BrokenAspect {
    // ...
}
~~~

Trong component scan mặc định, **BrokenAspect** được đăng ký singleton. Mô hình perthis cần nhiều aspect instance nên không tương thích. Cách sửa:

~~~java
@Aspect("perthis(execution(* demo.Job.execute(..)))")
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FixedAspect {
    // ...
}
~~~

Hai snippet trên **chỉ minh họa khác biệt cấu hình**; chúng không phải các file trong dự án chạy ở mục 7. Khi copy, cần import đúng **Aspect**, **Component**, **Scope**, **ConfigurableBeanFactory** và có phương thức advice cần thiết. Lỗi có thể xuất hiện trong quá trình Spring xây dựng advisor hoặc proxy, với thông báo đại ý rằng bean singleton nhưng aspect instantiation model không phải singleton.

### 12.2. Có @Aspect và @Scope nhưng không có bean

**@Aspect** không tự đăng ký Spring bean. Nếu không dùng **@Component** và không có **@Bean**, aspect có thể không được tìm thấy. Trong ví dụ ở mục 7, aspect được đăng ký qua **@Bean** nên các class aspect không cần **@Component**.

### 12.3. Có bean aspect nhưng chưa bật Spring AOP

Trong Spring Framework thuần, cần **@EnableAspectJAutoProxy** hoặc cấu hình hạ tầng tương đương. Việc khởi tạo một instance aspect thông thường không có nghĩa advice sẽ tự chạy.

### 12.4. Nhầm bean alias với nhiều target/proxy

Hai bean name có thể alias tới cùng object, hoặc hai bean khác nhau có cùng implementation class. Muốn suy ra số instance perthis/pertarget cần quan sát **identity thực**, không chỉ tên hoặc lớp. Công cụ gợi ý: **AopUtils.isAopProxy(...)**, **System.identityHashCode(...)** khi debug, và so sánh **==** cho hai tham chiếu lấy từ Spring context.

### 12.5. Có mutable state nhưng nhiều thread cùng sử dụng

Ngay cả perthis/pertarget cũng **không phải thread-local**. Nếu nhiều thread dùng cùng proxy hoặc target, chúng có thể đồng thời cập nhật trường **calls** của cùng một aspect instance.

Khi cần đếm chính xác trong môi trường đồng thời, thay `int calls` bằng **AtomicInteger**:

~~~java
private final java.util.concurrent.atomic.AtomicInteger calls =
        new java.util.concurrent.atomic.AtomicInteger();

int count = calls.incrementAndGet();
~~~

Đây là **fragment thay thế cho trường và phép tăng trong aspect**, không phải một chương trình độc lập. AtomicInteger chỉ giải quyết thao tác tăng đơn lẻ; logic phối hợp nhiều state khác vẫn phải được thiết kế cho concurrency.

### 12.6. Advice không chạy trong một số lời gọi

Phải kiểm tra từng điều kiện: service thật sự là Spring-managed bean, có proxy phù hợp, biểu thức advice có match, per-clause có khớp đối tượng và lời gọi có đi qua proxy không. Không nên đổ mọi trường hợp thiếu log cho sai instantiation model.

## 13. Bảng lựa chọn cho bài toán thực tế

| Mục đích | Hướng dùng ban đầu | Lý do |
| --- | --- | --- |
| Ghi log, đo thời gian, theo dõi call count toàn hệ thống | **singleton** + collaborator thread-safe | Đơn giản và ít instance |
| Giữ state liên quan đến một **proxy cụ thể** | **perthis** | Identity là Spring proxy |
| Giữ state liên quan đến một **target cụ thể** | **pertarget** | Identity là object nằm sau proxy |
| Giữ dữ liệu riêng từng HTTP request | Request-scoped bean/context thích hợp | Không đánh đồng request và proxy/target |
| Muốn dữ liệu riêng cho từng method invocation | Biến cục bộ trong advice | Tránh giữ state không cần thiết trong aspect |
| Muốn tạo bean phụ thuộc mới mỗi lần dùng | ObjectProvider, lookup hoặc factory phù hợp | Khác với aspect instantiation model |

Trong phần lớn ứng dụng Spring dùng aspect để logging, validation phụ trợ hoặc tracing, **singleton stateless aspect** là lựa chọn dễ bảo trì nhất. Chỉ chuyển sang perthis/pertarget khi thực sự có nhu cầu **lưu trạng thái gắn với identity của proxy/target**, và đã hiểu quan hệ giữa các đối tượng trong container.

## 14. Câu hỏi tự đánh giá

**1. Tại sao @Aspect("perthis(...)") thường phải đi cùng @Scope("prototype")?**

Vì Spring cần khả năng tạo nhiều aspect instance để gắn theo các proxy khác nhau. Bean singleton mặc định chỉ có một instance và không thỏa mô hình perthis.

**2. Một ApplicationContext có ba service bean thì có chắc pertarget tạo ba aspect instance ngay khi startup không?**

Không. Số instance phụ thuộc số **target phù hợp được kích hoạt qua join point khớp**, không chỉ số bean definition. Các instance có thể được tạo theo nhu cầu khi lần đầu join point tương ứng xuất hiện.

**3. Có hai proxy P1 và P2 cùng bọc target T. perthis và pertarget khác nhau thế nào?**

Nếu cả hai proxy đều kích hoạt per-clause: perthis có thể liên kết **hai** aspect instance theo P1 và P2; pertarget liên kết **một** aspect instance theo target T.

**4. Vì sao hai bean service singleton khác nhau có thể tạo hai pertarget aspect instance?**

Vì mỗi service bean có thể là một target object độc lập. Singleton scope của **mỗi service bean** không nói rằng tất cả service dùng chung một object.

**5. Perthis có nghĩa một instance aspect cho mỗi thread không?**

Không. Nó gắn với proxy identity. Nhiều thread gọi cùng proxy có thể cùng chạm vào một aspect instance.

**6. Chỉ thêm @Scope("prototype") cho một @Aspect thông thường đã tương đương pertarget chưa?**

Chưa. Prototype kiểm soát cách container cung cấp bean; pertarget là metadata cụ thể trong **@Aspect("pertarget(...)")**.

**7. Vì sao hai lời gọi từ cùng client có thể không chạy advice dù bean đã được tạo?**

Vì chỉ những method execution khớp pointcut và đi qua Spring proxy mới được advise. Self-invocation trên target hoặc method không khớp không tự chạy advice mới.

**8. Có nên dùng instance field của singleton aspect để lưu mã request hiện tại không?**

Không. Singleton aspect có thể được dùng đồng thời bởi nhiều request và thread. Dữ liệu invocation nên nằm ở biến cục bộ hoặc một cơ chế context được thiết kế rõ ràng.

## 15. Checklist cấu hình Spring AOP

- Xác định **ApplicationContext** chứa target bean và aspect bean.
- Bật **@EnableAspectJAutoProxy** trong Spring Framework thuần, hoặc xác minh cấu hình AOP tương ứng đã có.
- Đăng ký aspect bằng **@Component/@Bean**, không chỉ thêm **@Aspect**.
- Chọn **@Aspect** mặc định cho singleton, **@Aspect("perthis(...)")** hoặc **@Aspect("pertarget(...)")** khi muốn instance gắn theo đối tượng.
- Với perthis/pertarget, cấu hình aspect bean **prototype**, tránh bean singleton mặc định.
- Giữ per-clause và advice pointcut rõ ràng, test đúng các invocation cần chặn.
- Kiểm tra **proxy identity**, **target identity** và chuyện nhiều bean name có thể cùng object.
- Thiết kế mutable state an toàn khi nhiều thread có thể dùng cùng aspect instance.
- Không suy ra advice chạy chỉ vì Spring có aspect bean hoặc service bean; kiểm tra hành vi thực.

## 16. Tài liệu chính thức để đối chiếu

- **Spring Framework — Aspect Instantiation Models**: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/instantiation-models.html
- **Spring Framework — Declaring a Pointcut** (khác nhau giữa this và target trong Spring AOP): https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- **Spring Framework — @AspectJ Support**: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- **Spring Framework — Proxying Mechanisms**: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- **Spring Framework — Bean Scopes** (singleton/prototype của container): https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html

**Điểm cần ghi nhớ:** Spring AOP hỗ trợ các aspect instantiation model singleton, perthis và pertarget. Singleton dùng chung một instance; perthis liên kết state của aspect với Spring AOP proxy; pertarget liên kết với target object. Cấu hình **@Scope("prototype")** là điều kiện quan trọng để perthis/pertarget hoạt động với Spring-managed aspect, nhưng phạm vi prototype của bean và mô hình tạo instance aspect không phải cùng một khái niệm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-beans-010">Kiểm tra số lượng instance aspect và trạng thái được chia sẻ</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra số lượng instance aspect và trạng thái được chia sẻ trong Spring AOP

## 1. Mục tiêu

Khi một `@Aspect` được đăng ký làm Spring bean, những câu hỏi quan trọng khi chạy ứng dụng là:

- Trong `ApplicationContext` này đang có **bao nhiêu bean aspect** thuộc một class?
- Hai lần lấy bean có trả về **cùng một object** không?
- Một aspect bean có được dùng chung cho **nhiều target service bean**, nhiều lời gọi hay nhiều thread không?
- Một trường mutable trong aspect có tạo ra lỗi **lẫn dữ liệu giữa các lời gọi** không?
- Số **bean definition**, số **aspect instance tại runtime** và số **lần advice được thực thi** khác nhau ra sao?

Bài tập trung vào **cách Spring quản lý instance của bean `@Aspect`**, cách xác nhận tại runtime và cách bảo vệ trạng thái khi advice chạy trên nhiều bean/luồng. Đây là câu chuyện cụ thể của Spring AOP proxy trong `ApplicationContext`; không cần ôn lại AOP tổng quát.

**Kết luận cần kiểm chứng bằng chương trình:** với cấu hình thông dụng **một `@Aspect` bean singleton** trong một Spring context, nhiều target bean có method khớp pointcut có thể dùng **cùng một aspect instance**. Bởi vậy, một trường instance mutable trong aspect cũng được chia sẻ giữa các lời gọi. **Một instance aspect không đồng nghĩa một lần advice, một target, một request hay một thread.**

## 2. Ba loại “số lượng” khác nhau

| Đại lượng | Ý nghĩa đúng trong Spring | Cách kiểm tra | Điều không suy ra được |
| --- | --- | --- | --- |
| Số **bean names / bean definitions** của class aspect trong context | Context đã đăng ký mấy bean cho kiểu `MyAspect` | `context.getBeanNamesForType(MyAspect.class)` hoặc `getBeansOfType(MyAspect.class)` trong trường hợp singleton đơn giản | Không trực tiếp đo số lần advice chạy. Với prototype hoặc mô hình aspect instantiation nâng cao, đếm bean còn không phản ánh tổng instance đang được tạo trong runtime. |
| Số **object identity** được chia sẻ | Hai references có chỉ đến cùng instance không | `first == second`; có thể quan sát thêm `System.identityHashCode` chỉ để debug | Không biết đã có bao nhiêu proxy/target hoặc số lần advice. |
| Số **advice invocations** | Advice đã chạy bao nhiêu lần tại những join point phù hợp | Counter thread-safe, recorder hoặc integration test | Không đồng nhất với số aspect bean, target, request hay số business transaction thành công. |

**Ví dụ cơ bản:** Spring tạo một bean `countingAspect` có scope singleton. Một `OrderService` và một `PaymentService` được tạo hai proxy khác nhau; cả hai được aspect áp dụng. Sau ba lời gọi service qua proxy, kết quả có thể là **1 aspect bean, 1 aspect object, 2 target proxies, 3 lần advice chạy**.

```text
ApplicationContext
│
├── countingAspect  ───────────────┐
│   singleton CountingAspect      │ cùng một object, có AtomicInteger
│                                  │
├── orderService (Spring proxy) ───┼─> advisor -> advice trên CountingAspect
│                                  │
└── paymentService (Spring proxy) ─┘

Caller A và caller B gọi qua hai proxy:
cùng có thể làm advice thay đổi trạng thái của countingAspect.
```

Sơ đồ mô tả một cấu hình Spring AOP thường gặp; không có ý khẳng định Spring tạo một instance advice riêng cho mỗi proxy.

## 3. Scope mặc định: một singleton aspect bean trong mỗi context

Khi khai báo `@Aspect` dưới dạng Spring bean bằng `@Component` hoặc `@Bean` mà không chỉ định scope khác, Spring mặc định dùng **singleton scope**.

```java
@Aspect
@Component
public class CountingAspect {
    // Một Spring bean singleton trong context được scan
}
```

hoặc:

```java
@Configuration
@EnableAspectJAutoProxy
class AopConfig {
    @Bean
    CountingAspect countingAspect() {
        return new CountingAspect();
    }
}
```

Đây là **hai phương án đăng ký riêng**, không áp dụng đồng thời để tạo bean trùng. Với một bean definition singleton, Spring thường tạo một object và dùng lại khi inject/tra cứu bean đó trong **cùng context**. Aspect này có thể chứa các advice áp dụng cho nhiều target methods; Spring không tạo một copy riêng của aspect chỉ vì có thêm proxy.

Spring singleton có phạm vi **theo container và theo bean definition**, không phải singleton toàn JVM hay toàn bộ process:

- Hai bean definitions khác nhau cùng class có thể tạo **hai instance** trong một context; tùy advisor/pointcut, điều này có thể gây advice chạy hai lần.
- Hai `ApplicationContext` độc lập tạo hai bean singleton độc lập; mỗi context có instance riêng.
- Hai tiến trình ứng dụng hoặc hai replica cũng không tự chia sẻ Java object, dù bean trùng tên/class.
- Một context có thể có nhiều target beans/proxies, còn aspect bean singleton vẫn chỉ là một.

**Thận trọng:** Spring hỗ trợ những **mô hình khởi tạo aspect khác singleton**; vì vậy mọi kết luận “một aspect bean = một object” trong bài trước hết áp dụng cho cấu hình singleton mặc định. Mục 8 trình bày phạm vi của các mô hình nâng cao.

## 4. Kiểm tra Spring bean trước khi kiểm tra advice

Trong `ApplicationContext` hiện tại, bắt đầu từ bean registry:

```java
String[] names = context.getBeanNamesForType(CountingAspect.class);
System.out.println(java.util.Arrays.toString(names));

CountingAspect a = context.getBean(CountingAspect.class);
CountingAspect b = context.getBean("countingAspect", CountingAspect.class);
System.out.println(a == b);
```

Đây là **đoạn minh họa**, giả định context có đúng một singleton bean `CountingAspect` tên `countingAspect`. Khi ấy:

```text
[countingAspect]
true
```

Cách hiểu:

1. `getBeanNamesForType` trả về tên các bean tương ứng với type theo tiêu chí tra cứu của Spring container.
2. `getBean(MyAspect.class)` cần một bean duy nhất theo kiểu; nếu có hai bean cùng kiểu và không có phương án lựa chọn phù hợp, Spring có thể ném `NoUniqueBeanDefinitionException`.
3. `a == b` kiểm tra **identity chính xác**; với singleton bean và cùng tên/definition thì kết quả true. `a.equals(b)` không thích hợp để kết luận object identity vì class có thể override `equals`.
4. `getBeansOfType(MyAspect.class).size()` tiện để kiểm tra số bean của singleton phổ biến, nhưng có thể **khởi tạo bean không phải singleton** khi truy vấn. Với các scope nâng cao, tránh coi kết quả này là phép đếm mọi instance đã được tạo/đang được lưu trong AOP runtime.
5. `System.identityHashCode(bean)` hữu ích cho log/debug khi theo dấu object, nhưng mã hash có thể trùng; khi có thể, dùng so sánh `==` để khẳng định hai reference có phải chính xác cùng object.

Một bean tồn tại **không có nghĩa mọi target đã được proxy**. Sau bước đếm bean, kiểm tra riêng:

```java
OrderService order = context.getBean(OrderService.class);
boolean proxied = org.springframework.aop.support.AopUtils.isAopProxy(order);
System.out.println(proxied);
```

`isAopProxy` chỉ xác nhận object là AOP proxy, chưa xác nhận **advice của aspect đang xét** khớp một method cụ thể. Cần gọi method phù hợp và quan sát lời khuyên (advice) thực sự chạy.

## 5. Demo chạy được: một aspect, hai service, ba lời gọi

Ví dụ dùng **Java 17+**, **Spring Framework 6.2.19**, **AspectJ Weaver 1.9.25.1**, và Gradle có plugin `application`. Đây là ứng dụng Spring Framework thuần nhằm làm rõ instance và scope, không dựa vào Spring Boot auto-configuration.

### 5.1. Cấu trúc project

```text
spring-aspect-instance-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/com/example/aspectstate/
    ├── AppConfig.java
    ├── Counted.java
    ├── CountingAspect.java
    ├── OrderService.java
    ├── PaymentService.java
    └── Main.java
```

**`settings.gradle`**

```gradle
rootProject.name = 'spring-aspect-instance-demo'
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
    mainClass = 'com.example.aspectstate.Main'
}
```

Đây là các versions cố định để có thể chép dự án thành một ví dụ độc lập; trong dự án Spring Boot thực tế nên để BOM/dependency management quản lý các versions Spring/AspectJ tương thích.

### 5.2. Annotation chọn method cần đếm

**`Counted.java`**

```java
package com.example.aspectstate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Counted {
}
```

Chọn bằng annotation giúp pointcut tập trung vào method có chủ đích, tránh vô tình chặn các method của infrastructure.

### 5.3. Hai target Spring service khác nhau

**`OrderService.java`**

```java
package com.example.aspectstate;

public class OrderService {
    @Counted
    public String submit(String id) {
        return "order:" + id;
    }
}
```

**`PaymentService.java`**

```java
package com.example.aspectstate;

public class PaymentService {
    @Counted
    public String pay(String id) {
        return "payment:" + id;
    }
}
```

Ở đây hai class **không cần** `@Service` bởi chúng sẽ được đăng ký trực tiếp bằng `@Bean` trong cấu hình Spring. Điều quyết định Spring AOP là target phải được container quản lý và được caller gọi qua proxy.

### 5.4. Aspect giữ một counter được chia sẻ

**`CountingAspect.java`**

```java
package com.example.aspectstate;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class CountingAspect {
    private final AtomicInteger invocations = new AtomicInteger();

    @Before("@annotation(com.example.aspectstate.Counted)")
    public void beforeCountedMethod() {
        int number = invocations.incrementAndGet();
        System.out.println("advice#" + number);
    }

    public int getInvocationCount() {
        return invocations.get();
    }
}
```

Đây là một **stateful aspect có chủ đích để minh họa**. `AtomicInteger` làm thao tác increment/read an toàn khi advice bị gọi đồng thời. Nó **không chứng minh mọi trạng thái mutable đều an toàn**, cũng không biến counter trong một JVM thành hệ thống thống kê nhiều replica. Trong production, metric đếm invocation thường thích hợp hơn khi đưa vào Micrometer/Observation; aspect nên tránh tích lũy dữ liệu nghiệp vụ của từng request.

Annotation `@Aspect` mô tả aspect. Class cố ý **không có `@Component`** để bảo đảm chỉ có một nguồn đăng ký trong `AppConfig`.

### 5.5. Chỉ một bean aspect, hai beans target

**`AppConfig.java`**

```java
package com.example.aspectstate;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
    @Bean
    public CountingAspect countingAspect() {
        return new CountingAspect();
    }

    @Bean
    public OrderService orderService() {
        return new OrderService();
    }

    @Bean
    public PaymentService paymentService() {
        return new PaymentService();
    }
}
```

Các bean `countingAspect`, `orderService`, `paymentService` mặc định là singleton. `@EnableAspectJAutoProxy` nhận diện `CountingAspect` và áp dụng pointcut `@annotation` cho hai service. `proxyTargetClass=true` giúp target proxy truy cập được bằng kiểu class concrete trong ví dụ; không ảnh hưởng đến việc singleton aspect được dùng chung.

### 5.6. Đếm và so sánh chính instance Spring đã đăng ký

**`Main.java`**

```java
package com.example.aspectstate;

import java.util.Arrays;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            String[] names = context.getBeanNamesForType(CountingAspect.class);
            CountingAspect first = context.getBean(CountingAspect.class);
            CountingAspect second =
                    context.getBean("countingAspect", CountingAspect.class);

            OrderService order = context.getBean(OrderService.class);
            PaymentService payment = context.getBean(PaymentService.class);

            System.out.println("aspectBeanNames=" + Arrays.toString(names));
            System.out.println("sameAspectInstance=" + (first == second));
            System.out.println("orderProxy=" + AopUtils.isAopProxy(order));
            System.out.println("paymentProxy=" + AopUtils.isAopProxy(payment));

            System.out.println(order.submit("ORD-1"));
            System.out.println(payment.pay("PAY-1"));
            System.out.println(order.submit("ORD-2"));

            System.out.println("adviceInvocations=" + first.getInvocationCount());
        }
    }
}
```

Chạy với Gradle đã cài:

```bash
gradle run --quiet
```

Nếu có Gradle wrapper, dùng `./gradlew run --quiet` trên Linux/macOS hoặc `gradlew.bat run --quiet` trên Windows.

**Các dòng `System.out` của chương trình theo thứ tự chính xác** (không tính log JVM/Spring/Gradle khác nếu môi trường bật logging):

```text
aspectBeanNames=[countingAspect]
sameAspectInstance=true
orderProxy=true
paymentProxy=true
advice#1
order:ORD-1
advice#2
payment:PAY-1
advice#3
order:ORD-2
adviceInvocations=3
```

### 5.7. Đọc đúng kết quả

- `aspectBeanNames=[countingAspect]`: có một Spring bean đăng ký dưới kiểu `CountingAspect`.
- `sameAspectInstance=true`: hai lần lấy bean qua type/name thực sự trả về **cùng object singleton**.
- `orderProxy=true` và `paymentProxy=true`: Spring tạo proxy cho **hai target khác nhau** vì method của cả hai khớp pointcut.
- `advice#1` và `advice#3` thuộc hai lời gọi `OrderService.submit`; `advice#2` thuộc lời gọi `PaymentService.pay`.
- `adviceInvocations=3`: counter trong **cùng một aspect object** nhận tất cả ba sự kiện. Counter không tự reset khi đổi từ target `OrderService` sang `PaymentService`.

Nếu mục tiêu học là “mỗi service có bộ đếm riêng” thì cấu hình singleton aspect này **không đáp ứng**. Không nên nhầm scope bean target với scope instance aspect; hai service khác nhau vẫn có thể dùng chung counter.

## 6. Kiểm chứng trạng thái được chia sẻ

### 6.1. Trạng thái dùng chung giữa các target

Từ demo, mô tả trạng thái sau từng lời gọi:

| Thời điểm | Bean target được gọi | Trạng thái `CountingAspect.invocations` |
| --- | --- | --- |
| Ngay sau khi lấy bean | Chưa gọi | 0 |
| Sau `order.submit("ORD-1")` | `orderService` proxy | 1 |
| Sau `payment.pay("PAY-1")` | `paymentService` proxy | 2 |
| Sau `order.submit("ORD-2")` | `orderService` proxy | 3 |

Điều này đến từ **aspect singleton được chia sẻ**, không phải vì `OrderService` và `PaymentService` dùng chung cùng một target instance. Khi ứng dụng có nhiều caller, tất cả caller đi vào những method match pointcut đều có thể sử dụng cùng aspect bean.

### 6.2. Trạng thái dùng chung giữa nhiều thread

Một singleton aspect không được Spring tạo riêng theo từng thread. Khi những request hoặc task chạy trên nhiều thread cùng gọi các Spring service beans có method được advice, **chúng có thể cùng truy cập trường instance trong aspect**.

Đoạn code **có lỗi race condition**, dùng để nhận diện anti-pattern:

```java
@Aspect
public class UnsafeCountingAspect {
    private int total;

    @Before("@annotation(com.example.aspectstate.Counted)")
    public void beforeCall() {
        total++; // read-modify-write không atomic
    }

    public int count() {
        return total;
    }
}
```

Đây là **đoạn minh họa**, không thay tệp `CountingAspect.java` trong demo chạy được. Với hai thread tăng `total` đồng thời, các phép đọc/ghi có thể bị chồng lấn và mất cập nhật; giá trị cuối nhỏ hơn số advice thực thi. `volatile int total` vẫn không làm `total++` thành thao tác atomic; `volatile` chỉ có ngữ nghĩa visibility/happens-before phù hợp, không tự bảo vệ chuỗi read-modify-write.

Phiên bản cho **một counter độc lập** như ví dụ `AtomicInteger`:

```java
private final AtomicInteger invocations = new AtomicInteger();

@Before("@annotation(com.example.aspectstate.Counted)")
public void beforeCountedMethod() {
    invocations.incrementAndGet();
}
```

`AtomicInteger` phù hợp khi thực sự muốn đếm tổng số invocation **trong một instance aspect**, nhưng không bảo vệ một tập hợp nhiều field phải cập nhật cùng nhau. Ví dụ “ghi `currentUser` và `currentOrder` rồi đọc cả hai trong advice” không thể sửa chỉ bằng cách biến một field thành atomic.

### 6.3. Lỗi lẫn dữ liệu request trong aspect singleton

Ví dụ nguy hiểm:

```java
@Aspect
@Component
public class UnsafeAuditAspect {
    private String currentOrderId;

    @Before("@annotation(com.example.aspectstate.Counted) && args(id)")
    public void record(String id) {
        currentOrderId = id;
        // Một thread khác có thể thay currentOrderId ngay sau lệnh gán.
    }
}
```

Đây là **mã minh họa độc lập**, giả sử target method có một tham số `String id` và có component scan. Thứ tự xen kẽ có thể là:

```text
Thread A: record("ORD-A") -> currentOrderId = "ORD-A"
Thread B: record("ORD-B") -> currentOrderId = "ORD-B"
Thread A: đọc currentOrderId -> nhìn thấy "ORD-B" (sai request)
```

Lỗi **không xuất phát từ `@Aspect`**, mà từ việc lưu trạng thái request trong object Spring singleton được nhiều thread dùng chung.

Thay bằng tham số/lokal variables của chính advice:

```java
@Aspect
@Component
public class SafeAuditAspect {
    @Before("@annotation(com.example.aspectstate.Counted) && args(id)")
    public void record(String id) {
        String orderId = id; // Giá trị local của mỗi invocation
        System.out.println("audit:" + orderId);
    }
}
```

Hai class `UnsafeAuditAspect`/`SafeAuditAspect` chỉ là các phương án giảng giải. Không thêm cả hai vào project demo vì sẽ làm lời gọi có thêm advisor/log. Với production, loại bỏ thông tin nhạy cảm khỏi log và chuyển công việc audit sang một service/repository/sink phù hợp nếu có persistence, retry, transaction hoặc yêu cầu bảo mật.

### 6.4. Dữ liệu cần per-request: đừng chuyển sang field aspect

Một aspect singleton có thể inject một **service an toàn** để xử lý dữ liệu request, nhưng không lưu request data vào field mutable của chính nó:

```java
@Aspect
@Component
public class AuditAspect {
    private final AuditWriter writer;

    public AuditAspect(AuditWriter writer) {
        this.writer = writer;
    }

    @Before("@annotation(com.example.aspectstate.Counted) && args(id)")
    public void record(String id) {
        writer.write(id); // id là tham số của lần gọi hiện tại
    }
}
```

Đây là **đoạn minh họa**, cần một Spring bean `AuditWriter` do ứng dụng tự định nghĩa. Việc `writer` là `final` chỉ bảo đảm reference của dependency không bị thay; chính `AuditWriter` vẫn phải được thiết kế thread-safe phù hợp. Nếu dùng request-scoped bean/proxy hoặc context dữ liệu bảo mật, cần hiểu request scope và cách truyền ngữ cảnh khi async; không giả định nó hoạt động trong mọi thread hay non-web execution.

Đôi khi `ThreadLocal` được dùng cho context tại một thread, nhưng phải xóa đúng bằng `finally` và không tự truyền qua `@Async`/task executors. Cách an toàn dễ kiểm tra nhất là đưa dữ liệu theo tham số method/advice hoặc công cụ context/observation của chính framework.

## 7. Một bài kiểm tra concurrent có thể lặp lại

Để tránh nhận xét “singleton chắc chỉ chạy một thread”, ta có thể thực hiện thêm một phép thử **gọi cùng service proxy từ nhiều thread**. Sử dụng đúng `AppConfig`/`CountingAspect` của demo, ví dụ dưới đây là một **class Java chạy độc lập** đặt thêm ở `src/main/java/com/example/aspectstate/ConcurrentProbe.java`.

**`ConcurrentProbe.java`**

```java
package com.example.aspectstate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class ConcurrentProbe {
    public static void main(String[] args) throws Exception {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService order = context.getBean(OrderService.class);
            CountingAspect aspect = context.getBean(CountingAspect.class);

            ExecutorService executor = Executors.newFixedThreadPool(4);
            try {
                List<Future<String>> futures = new ArrayList<>();
                for (int i = 0; i < 100; i++) {
                    final int number = i;
                    futures.add(executor.submit(
                            (Callable<String>) () -> order.submit("N-" + number)
                    ));
                }

                for (Future<String> future : futures) {
                    future.get(); // đợi hoàn tất và lan truyền failure nếu có
                }

                System.out.println("completed=" + futures.size());
                System.out.println("count=" + aspect.getInvocationCount());
            } finally {
                executor.shutdown();
            }
        }
    }
}
```

Để chạy class riêng này với cùng Gradle project, thêm task sau vào cuối `build.gradle`:

```gradle
tasks.register('concurrentProbe', JavaExec) {
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'com.example.aspectstate.ConcurrentProbe'
}
```

Chạy:

```bash
gradle concurrentProbe --quiet
```

Advice từ `CountingAspect` sẽ in **100 dòng `advice#...` theo lịch chạy không cố định**. **Hai dòng cuối do `ConcurrentProbe` in ra** sau khi tất cả futures kết thúc có kết quả chính xác:

```text
completed=100
count=100
```

Mẫu output phía trên chỉ nêu **hai dòng kết thúc**, không phải toàn bộ stdout của phép thử. Thứ tự số trong các dòng advice và thời điểm method từng thread hoàn thành không bảo đảm; `AtomicInteger` bảo đảm tổng count = 100 trong context mới. Đây là đối chứng rằng nhiều thread cùng thay đổi trạng thái của **một aspect bean** và phép tăng counter được atomic.

Để kiểm tra race của `int total++` có thể dùng nhiều vòng lặp đồng thời và quan sát mất cập nhật, nhưng kết quả race **không tất định**; một lần tình cờ đúng 100 không chứng minh code an toàn. Đối với kiểm thử phần mềm, nên dùng concurrency tests/stress tests, và lý giải thiết kế thread safety độc lập với kết quả một lần chạy.

## 8. Scope phức tạp: không tự đồng nhất aspect bean với instance

Trong **cấu hình Spring AOP @AspectJ thông dụng**, mô hình khởi tạo mặc định là **singleton aspect instance trong application context**. Spring còn có những **mô hình aspect instantiation theo đối tượng/loại**, ví dụ `perthis` và `pertarget`, được tài liệu Spring mô tả là tính năng nâng cao.

Ví dụ **cú pháp minh họa**, không ghép vào demo singleton:

```java
@Aspect("perthis(execution(* com.example.aspectstate..*Service.*(..)))")
public class PerProxyAspect {
    // trạng thái gắn theo mỗi proxy object phù hợp pointcut
}
```

- `perthis`: gắn aspect instance theo từng object được ràng buộc với `this` tại join point khớp; với Spring AOP proxy, `this` thường biểu thị proxy object. Instance được tạo theo điều kiện khởi tạo của mô hình này, không phải đơn giản tạo một instance mới trên **mỗi** lần gọi method.
- `pertarget`: gắn theo từng target object phù hợp; khác biệt `this`/`target` quan trọng trong cơ chế Spring proxy.
- Các khả năng nâng cao thay đổi theo phiên bản Spring: ví dụ tài liệu Spring Framework **7.x** còn liệt kê `pertypewithin`, trong khi các tài liệu Spring **6.x** cũ có danh sách hạn chế hơn. Vì vậy, trước khi sử dụng, kiểm tra **tài liệu đúng phiên bản** và thử với context thực của dự án.
- `percflow`/`percflowbelow` không thuộc các mô hình Spring AOP proxy được tài liệu hiện hành hỗ trợ.

**Không kết luận “gắn `@Scope("prototype")` vào một `@Aspect` singleton sẽ tạo một instance theo mỗi request”.** Bean scope Spring và mô hình khởi tạo aspect @AspectJ có hợp đồng riêng; với per-clause, cần kiểm tra cách đăng ký/scope aspect tương thích trong phiên bản Spring của bạn. Kể cả có một aspect instance theo từng target, **nhiều thread gọi vào cùng target vẫn có thể dùng chung aspect instance đó**. Per-target không tương đương request scope hoặc thread isolation.

Nếu bài toán là đếm theo từng user/request/order, một singleton aspect stateless + một service lưu trạng thái được key đúng theo ID thường dễ kiểm soát hơn cố tạo nhiều aspect instances. Trước khi chọn per-clause, cần bằng chứng rõ ràng rằng **lifecycle aspect theo target** là mong muốn nghiệp vụ.

### Context độc lập: mỗi nơi một singleton riêng

Tình huống khác hoàn toàn với `perthis` là tạo hai `ApplicationContext` độc lập:

```java
try (var firstContext = new AnnotationConfigApplicationContext(AppConfig.class);
     var secondContext = new AnnotationConfigApplicationContext(AppConfig.class)) {
    CountingAspect first = firstContext.getBean(CountingAspect.class);
    CountingAspect second = secondContext.getBean(CountingAspect.class);
    System.out.println("sameAcrossContexts=" + (first == second));
}
```

Đây là **đoạn minh họa** đặt trong class có import `AnnotationConfigApplicationContext` và được khai báo `AppConfig` như demo. Nó in:

```text
sameAcrossContexts=false
```

Mỗi context đăng ký một singleton aspect bean riêng. Trong mô hình parent/child context, còn phải xét nơi target/proxy/advisor được tạo, nên không suy “một aspect cho toàn ứng dụng” chỉ dựa vào tên class.

## 9. Quy trình kiểm tra một ứng dụng Spring thực tế

Khi nghi vấn advice trùng, state bị chia sẻ hoặc kết quả không đúng, theo thứ tự:

1. **Xác nhận context đang xem:** lấy từ `ApplicationContext` đúng nơi target bean được tạo; nếu có root/child, xác định parent/child rõ ràng.
2. **Đếm bean names thuộc class aspect:** `getBeanNamesForType(MyAspect.class)`; nếu >1, xem lại `@Component` kết hợp `@Bean`, `@Import` hoặc cấu hình module bị đưa vào context nhiều lần.
3. **Xem scope của bean definition:** `context.isSingleton("beanName")` cho câu hỏi bean scope singleton, hoặc dùng `getBeanFactory().getBeanDefinition("beanName").getScope()` trên context/configurable bean factory phù hợp. Chuỗi scope rỗng ở bean definition thường biểu thị scope mặc định singleton.
4. **So sánh identity:** gọi `getBean("beanName")` hai lần và so `==` khi đang kiểm tra singleton. Đừng nhầm với `equals` hay số proxy objects.
5. **Kiểm tra target proxy:** `AopUtils.isAopProxy(targetBean)`, kiểu JDK/class proxy và pointcut match. Một aspect bean hiện diện không đủ chứng minh advice thực thi.
6. **Đếm advice bằng thread-safe recorder:** tại method cụ thể, chạy tuần tự trước, sau đó thử concurrent và kiểm tra mỗi lời gọi ghi đúng số sự kiện.
7. **Audit mọi mutable field trong aspect:** phân loại config immutable, dependency service, counter, cache, request context, dữ liệu người dùng. Field mutable chứa state mỗi request là dấu hiệu cần thiết kế lại.
8. **Kiểm tra môi trường production:** nhiều threads, async/reactive hand-off, nhiều replicas, tác động của transaction/advisor order và nguồn quan sát/log thực tế.

**Bảng đối chiếu triệu chứng:**

| Hiện tượng | Giải thích có thể xảy ra | Cách xác nhận |
| --- | --- | --- |
| `getBeansOfType(MyAspect.class)` trả về 0 | Aspect chỉ có `@Aspect`, chưa có `@Component`/`@Bean` hoặc scan sai context | Kiểm tra cách đăng ký, package scan và config được import |
| Có một aspect bean nhưng advice chạy 0 lần | Không bật Spring AOP, thiếu dependencies, pointcut không match, target không qua proxy, self-invocation | Kiểm tra `@EnableAspectJAutoProxy`/Boot auto-config, `AopUtils` và actual method call |
| Advice chạy hai lần trên một method | Hai beans/aspects cùng advice hoặc nhiều advisors cùng match | Liệt kê bean names, advisor chain và nơi đăng ký aspect |
| Hai service khác nhau tăng cùng counter | Aspect singleton dùng chung giữa nhiều target beans | Lấy aspect bean, quan sát identity và count sau mỗi lời gọi |
| Dữ liệu của request B xuất hiện trong log request A | Field mutable giữ context request trong aspect singleton | Kiểm tra field, hai lời gọi concurrent và cách truyền id |
| Counter thấp hơn số lời gọi concurrent | `int++` bị race hoặc có calls không thực sự đi qua proxy | Tách kiểm tra match/proxy; dùng `AtomicInteger` hay recorder concurrency-safe |
| Mỗi context có counter khác nhau | Singleton scope là per-bean/per-container | So sánh aspect bean object của từng context |
| Dùng prototype nhưng kết quả instance khó dự đoán | Nhầm Spring bean scope với aspect instantiation model | Kiểm tra `@Aspect` per-clause và lifecycle thực tế trong phiên bản Spring |

### Không nên đếm bằng output console duy nhất

Các dòng log có thể bị interleave khi concurrent; logger có async buffering; một advice có thể log nhiều sự kiện hoặc bị filter; retry và transaction rollback có thể thay đổi quan sát. Với mục tiêu QA, dùng **counter/test sink có xác nhận chính xác** và assert theo số method thực sự đi qua proxy. Counter có thể đếm **attempted method executions** thay vì business successes nếu dùng `@Before`; đừng gọi số này là số giao dịch đã commit.

## 10. Checklist trước khi chấp nhận thiết kế

- [ ] Đã nêu rõ sẽ đếm **bean aspect**, **aspect instance** hay **advice invocation**, không gom ba khái niệm.
- [ ] Aspect được Spring đăng ký bằng `@Component` hoặc `@Bean` và có đúng số lượng bean definitions dự kiến.
- [ ] Đã xác nhận scope mặc định singleton hoặc mô hình instantiation nâng cao với tài liệu đúng version.
- [ ] Đã phân biệt một context, nhiều contexts, nhiều threads và nhiều replicas.
- [ ] Có Spring AOP proxy trên target cần advice; pointcut khớp và lời gọi đi qua proxy.
- [ ] Trường của aspect chứa cấu hình/dependency an toàn; không giữ request-specific mutable data trong singleton.
- [ ] Counter hoặc collection chia sẻ được đồng bộ/thread-safe đúng mức; composite state cần cách bảo vệ đầy đủ.
- [ ] Ví dụ/test sử dụng context thật, so object identity bằng `==`, đếm advice bằng recorder/counter đáng tin.
- [ ] Đã kiểm tra self-invocation, lỗi đăng ký trùng aspect và trường hợp @Aspect không là Spring bean.
- [ ] Nếu cần per-request/per-target state, đã chọn một cơ chế lifecycle phù hợp, không tự suy từ `@Scope("prototype")`.

**Điểm nhớ:** Trong Spring AOP mặc định, **một bean aspect singleton có thể phục vụ nhiều proxy, nhiều lời gọi và nhiều thread**. Phải kiểm tra số bean và object identity ở **đúng context**, kiểm tra lời gọi advice riêng và chủ động bảo vệ hoặc loại bỏ state mutable trong aspect. Không dùng số lần advice hay số target proxy để suy ra số aspect instances.

## Tài liệu chính thức

- [Spring Framework — Aspect Instantiation Models](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/instantiation-models.html)
- [Spring Framework — Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)
- [Spring Framework — Declaring an Aspect](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/at-aspectj.html)
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html)


</details>

- [Quay lại đầu trang](#back-to-top)
