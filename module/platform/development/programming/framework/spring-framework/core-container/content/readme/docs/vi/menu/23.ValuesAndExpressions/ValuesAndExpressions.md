<a id="back-to-top"></a>

# Giá trị và biểu thức

## Menu
- [Tiêm cấu hình bằng @Value trên tham số, trường và setter](#values-and-expressions-001)
- [Placeholder ${...}, giá trị mặc định và placeholder lồng nhau](#values-and-expressions-002)
- [Vai trò PropertySourcesPlaceholderConfigurer](#values-and-expressions-003)
- [Phát hiện placeholder chưa được giải quyết](#values-and-expressions-004)
- [Chuyển đổi từ chuỗi sang kiểu đích bằng ConversionService](#values-and-expressions-005)
- [PropertyEditor và giới hạn của cách chuyển đổi cũ](#values-and-expressions-006)
- [Phân biệt placeholder ${...} với biểu thức SpEL #{...}](#values-and-expressions-007)
- [Tham chiếu bean, thuộc tính và tập hợp trong SpEL](#values-and-expressions-008)
- [Giới hạn biểu thức động và kiểm tra giá trị cấu hình đầu vào](#values-and-expressions-009)

## <a id="values-and-expressions-001">Tiêm cấu hình bằng @Value trên tham số, trường và setter</a>

<details>
<summary>Xem chi tiết</summary>

# Tiêm cấu hình bằng @Value trên tham số, trường và setter trong Spring Framework Core Container

## 1. Mục tiêu của @Value và phạm vi bài học

`@Value` là annotation thuộc `org.springframework.beans.factory.annotation.Value`, dùng để khai báo một **giá trị cần đưa vào một điểm injection** của bean được Spring quản lý. Giá trị có thể là chuỗi cố định, placeholder lấy từ `Environment`/`PropertySource`, biểu thức Spring Expression Language (SpEL), hoặc kết quả đã được chuyển đổi sang kiểu đích.

Ba tình huống chính:
- **Tham số constructor**: lấy giá trị khi Spring dựng bean; phù hợp cấu hình bắt buộc và trạng thái bất biến.
- **Trường (field)**: Spring gán giá trị sau khi constructor chạy; cú pháp gọn nhưng khó test và làm dependency kém hiển lộ.
- **Setter/method parameter**: Spring gọi method trong quá trình property population; phù hợp cấu hình tùy chọn hoặc bean có setter cần validation/logic thay đổi giá trị.

Một ví dụ rút gọn:

```java
public final class WorkerSettings {
    @Value("${worker.region:local}")
    private String region;

    private final int retries;
    private boolean enabled;

    public WorkerSettings(@Value("${worker.retries:3}") int retries) {
        this.retries = retries;
    }

    @Autowired
    public void setEnabled(@Value("${worker.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }
}
```

Đây là **fragment minh họa**, cần imports `@Value` và `@Autowired`, bean definition và container tương ứng để Spring thực hiện injection. Đối tượng tạo bằng `new WorkerSettings(3)` **không tự nhận** field injection hoặc setter injection của Spring.

Nội dung trọng tâm của bài: cú pháp và nơi đặt `@Value`, cách Spring resolve placeholder/SpEL, chuyển đổi kiểu, thứ tự lifecycle, thiết lập nguồn config trong Spring Core, ví dụ Java 17+ chạy được, lỗi và cách kiểm thử. Không nhầm `@Value` với `@Autowired` để chọn service bean, `@PropertySource` để nạp nguồn properties, hoặc `@ConfigurationProperties` là tiện ích binding cấu hình có cấu trúc trong Spring Boot.

## 2. Cơ chế @Value được thực thi như thế nào?

`@Value` không phải câu lệnh Java tự động đọc file khi một object được tạo. Nó là **metadata** được Spring xử lý khi bean thuộc quyền quản lý của IoC Container. Trong `AnnotationConfigApplicationContext`, cơ chế annotation injection liên quan `AutowiredAnnotationBeanPostProcessor`, `BeanFactory`, embedded value resolver, `Environment` và khi có biểu thức, `BeanExpressionResolver`/SpEL.

Mô hình điển hình:

```text
Nguồn cấu hình:
  classpath:demo.properties | JVM -D... | OS environment | custom PropertySource
                 |
                 v
        Environment/PropertySources
                 |
       placeholder: ${key:default}
                 |
       SpEL (nếu là #{...})
                 |
       convert theo kiểu đích
                 |
                 v
Constructor parameter → tạo object → field injection → setter/method injection
                                           |
                                           v
                                  initialization callbacks
                                           |
                                           v
                                    Bean sẵn sàng
```

Sơ đồ mang tính khái niệm: constructor arguments được resolve **trước khi instance tồn tại**, còn field và method injection diễn ra **sau khi tạo instance và trước initialization**. Trình tự chính xác giữa nhiều field và nhiều method injection không nên biến thành giả định nghiệp vụ. Với `@Bean` method parameter, Spring resolve các parameter khi gọi factory method, tương tự parameter dependency resolution.

Các bước quan trọng:
1. Container đọc bean definition và annotations.
2. Ở một constructor được chọn, Spring resolve các tham số có `@Value`, chuyển đổi kiểu rồi gọi constructor.
3. Spring tiếp tục xử lý field/method injection trên instance đã được tạo.
4. Các post-processors trước initialization và `@PostConstruct` (nếu đã cấu hình hỗ trợ), `InitializingBean.afterPropertiesSet()`, custom init callbacks chạy sau khi dependencies đã được cung cấp.
5. Bean sẵn sàng sử dụng hoặc có thể được wrap thành proxy.

**Với field/setter injection, đừng đọc giá trị @Value ngay trong constructor.** Khi constructor chạy, các field chưa được gán qua field injection và setter chưa được gọi. Nếu cần dùng cấu hình để thiết lập invariant trong constructor, sử dụng constructor parameter `@Value`.

## 3. Cú pháp chuỗi cố định, placeholder và SpEL

Ba nhóm cú pháp thường gặp:

| Cú pháp | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `@Value("literal")` | Đưa literal | `@Value("ready")` |
| `@Value("${key}")` | Resolve một property | `@Value("${demo.name}")` |
| `@Value("${key:default}")` | Resolve property, dùng default nếu không có | `@Value("${demo.retries:3}")` |
| `@Value("#{expression}")` | Đánh giá SpEL | `@Value("#{2 * 3}")` |
| `@Value("#{${key:6} * 2}")` | Placeholder được xử lý rồi dùng trong biểu thức | `@Value("#{${demo.batch.base:6} * 2}")` |

**Khác biệt quan trọng:**
- `${...}` là **property placeholder**.
- `#{...}` là **SpEL expression**.
- `@Value` chỉ là nơi chú thích để Spring biết điểm cần nhận giá trị.
- Dấu hai chấm bên trong placeholder ngăn key và default trong cấu hình thông dụng.
- Việc ghép nhiều placeholder hoặc expression với string literal có thể thực hiện nhưng cần chú ý parsing, escape và chuyển đổi kiểu.

Ví dụ:

```java
@Value("service-a")
private String serviceName;

@Value("${demo.region:local}")
private String region;

@Value("${demo.retries:3}")
private int retries;

@Value("${demo.enabled:false}")
private boolean enabled;

@Value("#{2 * 3}")
private int fixedBatchSize;
```

Đây là class-field fragment, không chạy độc lập. Với `demo.retries=4`, target `int` nhận số nguyên 4. Với `demo.enabled=true`, target `boolean` nhận true. Nếu giá trị không chuyển sang kiểu đích được, bean creation có thể thất bại vì conversion/type mismatch.

### Key bắt buộc và key có giá trị mặc định

`@Value("${demo.name}")` là một placeholder **không có default trong cú pháp**. Nhưng việc thiếu key có gây lỗi ngay không còn phụ thuộc resolver được cấu hình. Spring Framework mặc định có một **lenient embedded value resolver** có thể giữ lại chuỗi literal `${demo.name}` khi không resolve được.

Để **fail-fast** với unresolved placeholders trong Java configuration, khai báo `PropertySourcesPlaceholderConfigurer` bằng **`static @Bean`**. Khi đó một placeholder bắt buộc không resolve được thường làm context initialization thất bại thay vì âm thầm đưa literal placeholder vào code. Đây là cấu hình của ví dụ runnable ở mục 9.

`@Value("${demo.retries:3}")` không có nghĩa giá trị cấu hình sai sẽ được thay bởi 3: default xử lý key **không tồn tại**, còn chuỗi `demo.retries=oops` là **giá trị tồn tại nhưng không chuyển được sang int**, nên có thể gây lỗi.

### Giá trị rỗng và null

`@Value("${demo.note:}")` là một ví dụ cung cấp chuỗi mặc định rỗng cho `String`; nó không tương đương Java `null`. Có thể dùng SpEL `@Value("#{null}")` nếu thực sự muốn kết quả null ở một reference type, nhưng hãy xét liệu một type `Optional`/typed config và validation có rõ nghĩa hơn không.

Đừng coi mọi chuỗi trống là “không có cấu hình”: `getProperty`/placeholder có thể tìm thấy key nhưng value rỗng, dẫn tới khác biệt giữa “missing”, “empty” và “invalid”.

## 4. @Value trên tham số constructor: cấu hình bắt buộc và immutable

Constructor injection làm dependency hiển lộ và dễ kiểm thử. Với bean chỉ cần các giá trị cố định sau khi tạo, đây thường là lựa chọn ưu tiên:

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class RetryPolicy {
    private final int maxAttempts;
    private final boolean enabled;

    public RetryPolicy(
            @Value("${retry.max-attempts:3}") int maxAttempts,
            @Value("${retry.enabled:true}") boolean enabled) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.maxAttempts = maxAttempts;
        this.enabled = enabled;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public boolean enabled() {
        return enabled;
    }
}
```

Đây là Java class có thể biên dịch nếu có `spring-context`; để nó trở thành Spring bean cần `@ComponentScan` tương ứng hoặc đăng ký/import trực tiếp. `@Value` nằm trên **tham số**, không phải trên constructor declaration.

Ưu điểm:
- Fields có thể là `final`, không có trạng thái “constructor xong nhưng chưa injected”.
- Validation được thực hiện ngay khi bean được tạo.
- Unit test có thể gọi `new RetryPolicy(3, true)` mà không khởi động Spring.
- Không thể vô tình quên một tham số bắt buộc khi gọi constructor bằng Java code.
- Class biểu lộ chính xác những cấu hình mà nó dùng, thay vì giữ toàn bộ `Environment`.

Hạn chế:
- Khi số key nhiều, constructor dài và khó duy trì; dùng một cấu hình typed object/`@ConfigurationProperties` (nếu dùng Boot) hoặc một riêng `@Bean` config object.
- Trong Java API của service, `@Value` là dependency vào Spring; muốn domain class thuần Java, tốt hơn tạo bean bằng `@Bean` và truyền primitive đã resolve.
- Constructor injection không hỗ trợ thay giá trị runtime đơn giản: singleton thường giữ snapshot lúc tạo.

### @Value và @Autowired trên cùng constructor

Khi class có một constructor, Spring thường tự sử dụng constructor đó mà không cần `@Autowired`. Với nhiều constructors, annotation `@Autowired` trên constructor có thể cần thiết để xác định lựa chọn phù hợp. `@Value` trên parameter cung cấp **giá trị của tham số**, không phải tín hiệu duy nhất để chọn overload.

Ví dụ fragment:

```java
@Autowired
public RetryPolicy(@Value("${retry.max-attempts:3}") int attempts,
                   MeterRegistry registry) {
    // @Autowired chọn constructor; @Value resolve attempts;
    // MeterRegistry được inject như bean dependency.
}
```

Fragment này chỉ minh họa, `MeterRegistry` thuộc Micrometer (không nằm trong dự án runnable), và class cần khai báo fields/constructor phù hợp. Không nhầm một `@Value` parameter với annotation khởi tạo mọi tham số còn lại.

## 5. @Value trên field: cú pháp gọn nhưng có đánh đổi

Field injection:

```java
@Component
public class EmailGateway {
    @Value("${mail.sender:no-reply@example.invalid}")
    private String sender;

    public String sender() {
        return sender;
    }
}
```

Đây là fragment Java cần imports và component scanning. Spring gán field bằng cơ chế phản chiếu phù hợp, sau khi constructor chạy.

**Điểm mạnh:** ngắn, dễ minh họa, ít boilerplate trong component đơn giản. **Điểm yếu đáng kể:**
- Dependency cấu hình không nằm trong constructor signature.
- Object tạo bằng `new EmailGateway()` giữ field `sender` là `null`.
- Test thuần Java khó cấp giá trị nếu không dùng reflection hoặc test context.
- Trạng thái có thể chưa initialized khi constructor cố đọc field.
- Nếu class có nhiều field rải rác, việc thấy cấu hình bắt buộc/optional trở nên khó.
- Trường `static` không phải dependency instance thích hợp và không nên mong Spring inject như thông thường.
- Dùng reflection để gán `final` field là thiết kế dễ gây rủi ro; với giá trị bất biến, chọn constructor.

### Constructor truy cập field chưa injected

```java
public class BadGateway {
    @Value("${mail.sender}")
    private String sender;

    public BadGateway() {
        System.out.println(sender); // Thường vẫn null ở constructor.
    }
}
```

Đây là anti-pattern minh họa; class phải là Spring bean và có config phù hợp mới xét được lifecycle. Spring chưa thực hiện field injection tại thời điểm constructor chạy. **Không** sửa bằng cách tự gọi BeanFactory trong constructor. Đưa `sender` vào tham số constructor, hoặc dùng `@PostConstruct` khi việc kiểm tra thực sự phải diễn ra sau injection.

### Visibility và tính bất biến

Một private field vẫn có thể được Spring gán khi annotation infrastructure chạy. Điều này không làm nó tốt hơn constructor dependency với field `final`. Với unit testing, test public behavior quan trọng hơn assert private field bằng reflection; thử nghiệm hành vi tốt nhất nên cấp cấu hình qua constructor hoặc khai báo rõ test context.

## 6. @Value trên setter hoặc tham số phương thức

Setter injection xảy ra sau khi object được tạo. Một cách đáng tin cậy để yêu cầu Spring gọi setter là dùng `@Autowired` trên method và `@Value` trên tham số:

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class FeatureFlags {
    private boolean enabled;

    @Autowired
    public void setEnabled(@Value("${feature.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
```

Đây là fragment class, cần bean registration. `@Autowired` trên setter yêu cầu method injection; `@Value` trên parameter yêu cầu property resolution cho argument. `@Autowired` và `@Value` **không làm một nhiệm vụ giống nhau**.

Có thể dùng `@Value` trực tiếp trên method setter theo annotation contract; khi viết tài liệu/đoạn code có nhiều tham số, việc đánh dấu điểm nhận value trên **tham số** kết hợp `@Autowired` làm ý định ít mơ hồ hơn và dễ review hơn.

### Setter có thể validate hoặc chuẩn hóa giá trị

```java
@Autowired
public void setBatchLimit(@Value("${batch.limit:100}") int limit) {
    if (limit < 1 || limit > 10000) {
        throw new IllegalArgumentException("Invalid batch limit");
    }
    this.batchLimit = limit;
}
```

Fragment này cần field `batchLimit` và import annotations. Nếu cấu hình ngoài đặt `batch.limit=abc`, quá trình chuyển `String` sang `int` có thể thất bại **trước khi vào thân setter**. Nếu đặt `-5`, conversion thành công rồi logic validation sẽ reject.

Setter injection thích hợp khi:
- Cấu hình có optional default và không cần để thiết lập invariant constructor.
- Bean hạ tầng có JavaBean setter mà framework khác sử dụng.
- Setter cần làm normalization hoặc validation sau khi constructor hoàn tất.
- Có method nhận nhiều config arguments (cần hiểu method injection semantics).

Nhưng setter mutable được public có thể bị gọi **sau startup**, làm thay đổi bean singleton đang được nhiều thread dùng. `@Value` chỉ gọi injection theo bean lifecycle; nó **không** làm giá trị tự refresh runtime hoặc làm setter tự thread-safe.

### Setter không chạy trên object tạo bằng new

```java
FeatureFlags flags = new FeatureFlags();
System.out.println(flags.isEnabled()); // false theo Java default, không phải inject.
```

Đây là code fragment và không chứng minh Spring đã cung cấp default `false`. Nó chỉ cho thấy Java object tạo thủ công không được Spring inject. Nếu cần test logic setter, gọi setter bằng Java trong unit test hoặc tạo context integration để kiểm annotation injection.

## 7. @Value trên tham số của @Bean factory method

Trong Java configuration, Spring còn có thể resolve value cho **tham số của `@Bean` method**, để code domain object không cần biết `@Value`.

```java
@Configuration(proxyBeanMethods = false)
public class ServiceConfig {
    @Bean
    public ClientOptions clientOptions(
            @Value("${client.timeout-ms:1500}") int timeoutMs,
            @Value("${client.retry-enabled:false}") boolean retryEnabled) {
        return new ClientOptions(timeoutMs, retryEnabled);
    }
}
```

Fragment cần imports `@Configuration`, `@Bean`, `@Value` và class `ClientOptions` do ứng dụng định nghĩa. Spring resolve method arguments trước khi gọi factory method. Đây là cách hữu ích để giữ domain class thuần Java:

```java
public record ClientOptions(int timeoutMs, boolean retryEnabled) {
    public ClientOptions {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive");
        }
    }
}
```

Class record này là Java 17 thuần, không phụ thuộc Spring. Trong test có thể tạo `new ClientOptions(1500, true)` và assert validation.

**Tách mối quan tâm:** `@Value` thuộc **composition/configuration layer**, object nghiệp vụ chỉ nhận tham số Java thông thường. Khi service cần nhiều cấu hình, pattern này thường rõ và linh hoạt hơn field injection.

Không nhầm factory method parameter với **method parameter khi gọi một phương thức nghiệp vụ bình thường**. Spring không tự “tiêm” `@Value` vào mọi method call do code của bạn thực hiện. Chỉ những method/constructor do cơ chế injection của Spring quản lý mới được xử lý.

## 8. PropertySource, Environment và thứ tự ưu tiên

`@Value("${demo.name}")` không định nghĩa nguồn của key. Nó yêu cầu resolver hiện hành của container tìm key đó. Đối với Spring Framework Core, bạn có thể thêm:

```java
@Configuration
@PropertySource("classpath:demo.properties")
public class AppConfig {
}
```

File `demo.properties` được đưa vào environment property sources khi Spring xử lý configuration class. Tùy phiên bản và cấu hình, các nguồn ưu tiên cao hơn như JVM system properties hoặc custom `MapPropertySource` có thể override giá trị từ file.

Trong chương trình thuần Spring Core, **`application.properties` không mặc nhiên được Spring Boot-style auto-load** chỉ vì nó nằm trên classpath. Dùng `@PropertySource`, thêm `PropertySource` programmatically hoặc tự cấu hình nguồn.

### Một ví dụ precedence đơn giản

Giả sử:
- `demo.region=ap-southeast-1` trong file properties.
- JVM có `-Ddemo.region=eu-central-1`.
- `StandardEnvironment` có `systemProperties` trước nguồn file.

Khi đó resolver có thể lấy `eu-central-1` thay vì giá trị file. Nếu bạn đưa `MapPropertySource` lên đầu bằng `addFirst`, nó có thể thắng cả JVM property.

```java
context.getEnvironment().getPropertySources().addFirst(
        new MapPropertySource("overrides",
                Map.of("demo.region", "eu-west-1")));
```

Đây là fragment, cần imports `java.util.Map` và `MapPropertySource`, nên gọi trước khi bean values được resolve. Nó không phải tác vụ runtime reconfiguration cho singleton đã được inject trước đó.

### Không đọc secret vào log để kiểm tra precedence

`Environment` có thể chứa password, API token và connection string. Đừng dump toàn bộ environment hoặc bound config bean. Khi debug, log các key/status được allowlist (nếu chính sách cho phép), không ghi mật khẩu/token và URL chứa credentials. Giá trị một field được `@Value` inject có thể nhạy cảm ngang với value đọc qua `Environment.getProperty`.

## 9. Ví dụ Java 17+ Spring Core hoàn chỉnh: constructor, field, setter và @Bean parameter

Ví dụ dưới đây dùng **Java 17+, Maven 3.9+, Spring Framework 6.2.18**. Không có Spring Boot, web server hay database. Nó chứng minh bốn injection locations:
1. Constructor parameters với `@Value`.
2. Field với `@Value`.
3. Setter/method parameter với `@Autowired` + `@Value`.
4. Parameter của `@Bean` method với `@Value`.

Ngoài ra, ví dụ xử lý `${...}`, default values, conversion `String → int/boolean` và biểu thức `#{...}`. Để phát hiện cấu hình bắt buộc bị thiếu, demo khai báo `PropertySourcesPlaceholderConfigurer` qua **static `@Bean`**.

### 9.1. Cấu trúc dự án

```text
spring-value-demo/
├── pom.xml
└── src/main/
    ├── java/edu/example/value/
    │   ├── ConfigView.java
    │   ├── StartupBanner.java
    │   ├── AppConfig.java
    │   └── Main.java
    └── resources/
        └── demo.properties
```

**pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>spring-value-demo</artifactId>
    <version>1.0.0</version>
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
                <configuration>
                    <mainClass>edu.example.value.Main</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**src/main/resources/demo.properties**

```properties
demo.name=learning-core
demo.region=ap-southeast-1
demo.retries=4
demo.enabled=true
demo.batch.base=6
demo.banner=Core Ready
```

Không lưu password/token thật trong file demo. `demo.name` là key bắt buộc trong ví dụ; `demo.retries`/`demo.enabled`/`demo.batch.base` có default nếu key bị thiếu.

**src/main/java/edu/example/value/ConfigView.java**

```java
package edu.example.value;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public final class ConfigView {
    private final String name;
    private final int retries;

    @Value("${demo.region:local}")
    private String region;

    private boolean enabled;
    private int batchSize;

    public ConfigView(
            @Value("${demo.name}") String name,
            @Value("${demo.retries:3}") int retries) {
        if (retries < 1) {
            throw new IllegalArgumentException("Retries must be positive");
        }
        this.name = name;
        this.retries = retries;
    }

    @Autowired
    public void setEnabled(
            @Value("${demo.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Autowired
    public void setBatchSize(
            @Value("#{${demo.batch.base:6} * 2}") int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        this.batchSize = batchSize;
    }

    public String name() {
        return name;
    }

    public int retries() {
        return retries;
    }

    public String region() {
        return region;
    }

    public boolean enabled() {
        return enabled;
    }

    public int batchSize() {
        return batchSize;
    }
}
```

Lớp `ConfigView` không được đánh dấu `@Component`; nó sẽ được **`@Import(ConfigView.class)`** đăng ký vào ApplicationContext trong `AppConfig`. Đây là cơ chế Java config hợp lệ cho một class độc lập, nhờ đó không cần component scanning.

**src/main/java/edu/example/value/StartupBanner.java**

```java
package edu.example.value;

public record StartupBanner(String text) {
}
```

Record Java 17 này là một value object thuần Java, không chứa Spring annotation.

**src/main/java/edu/example/value/AppConfig.java**

```java
package edu.example.value;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

@Configuration(proxyBeanMethods = false)
@Import(ConfigView.class)
@PropertySource("classpath:demo.properties")
public class AppConfig {

    @Bean
    public static PropertySourcesPlaceholderConfigurer placeholders() {
        // Strict resolution: unresolved ${required.key} fails initialization.
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    public StartupBanner startupBanner(
            @Value("${demo.banner:All systems ready}") String text) {
        return new StartupBanner(text);
    }
}
```

Vì `PropertySourcesPlaceholderConfigurer` là một `BeanFactoryPostProcessor`, Spring khuyến nghị khai báo bằng **static `@Bean`** để không phải tạo instance `AppConfig` quá sớm. `@PropertySource` tải file classpath, `@Import` đăng ký `ConfigView`, `@Bean` method nhận một parameter có `@Value` và tạo `StartupBanner`.

**src/main/java/edu/example/value/Main.java**

```java
package edu.example.value;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class Main {
    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            ConfigView config = context.getBean(ConfigView.class);
            StartupBanner banner = context.getBean(StartupBanner.class);

            System.out.println("name: " + config.name());
            System.out.println("retries: " + config.retries());
            System.out.println("region: " + config.region());
            System.out.println("enabled: " + config.enabled());
            System.out.println("batchSize: " + config.batchSize());
            System.out.println("banner: " + banner.text());
        }
    }
}
```

### 9.2. Chạy và output

Từ thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java
```

**Sáu dòng `System.out.println` do chương trình tự in:**

```text
name: learning-core
retries: 4
region: ap-southeast-1
enabled: true
batchSize: 12
banner: Core Ready
```

Đây là output xác định với các giá trị trong `demo.properties` và khi không có nguồn ưu tiên cao hơn override các key tương ứng. Maven/Spring có thể ghi log khác tùy môi trường; không trộn chúng với sáu dòng stdout này.

### 9.3. Vì sao có kết quả trên?

1. `AnnotationConfigApplicationContext(AppConfig.class)` tạo context, register configuration và refresh.
2. `@PropertySource("classpath:demo.properties")` đưa các values trong resource vào `Environment`.
3. `PropertySourcesPlaceholderConfigurer` xử lý placeholder nghiêm ngặt, giúp `${demo.name}` phải resolve được.
4. Khi dựng `ConfigView`, Spring resolve `demo.name` và `demo.retries` từ file, chuyển `"4"` thành primitive `int 4`, rồi gọi constructor.
5. Field `region` được Spring gán `ap-southeast-1` sau constructor.
6. Hai `@Autowired` setter methods được Spring gọi; `enabled` nhận boolean `true`, còn `batchSize` nhận kết quả SpEL `6 * 2 = 12`.
7. `startupBanner(...)` là bean factory method; Spring resolve tham số text thành `Core Ready` rồi tạo record.
8. Sau khi context hoàn thành, `Main` truy vấn bean và in các getters. Không dựa vào một thứ tự gọi giữa field và setter trong thân class để tính kết quả.

### 9.4. Các biến thể để tự thử

**Bỏ một property có default:** xóa `demo.retries=4`. Lúc đó `@Value("${demo.retries:3}")` cung cấp default 3, giả sử không có nguồn khác override. Dòng output `retries: 3` thay thế `retries: 4`.

**Bỏ property bắt buộc:** xóa `demo.name=learning-core`. Với strict configurer, context có thể fail khi không tìm được `demo.name` (trừ khi nguồn ngoài cung cấp key). Không kỳ vọng in đủ sáu dòng main.

**Giá trị sai kiểu:** đặt `demo.retries=oops`. Placeholder được resolve thành chuỗi, nhưng conversion sang `int` thất bại; không phải trường hợp default `3`.

**Dùng override qua property source:** trước khi register/refresh, thêm `MapPropertySource` có `demo.region=eu-west-1` ở đầu. Khi bean được tạo, `region` có thể trở thành `eu-west-1`; không sửa file gốc. Cần tạo context bằng constructor không tham số để cấu hình trước refresh:

```java
try (AnnotationConfigApplicationContext ctx =
             new AnnotationConfigApplicationContext()) {
    ctx.getEnvironment().getPropertySources().addFirst(
            new MapPropertySource("test",
                    Map.of("demo.region", "eu-west-1")));
    ctx.register(AppConfig.class);
    ctx.refresh();
}
```

Đây là **fragment** cần imports `MapPropertySource`, `Map` và các class demo, và cần gọi `ctx.getBean(...)` nếu muốn assert. Nó cho thấy cùng annotation `@Value` có thể nhận value khác khi source precedence thay đổi.

## 10. SpEL với @Value: sức mạnh và giới hạn

Ngoài `${key}`, `@Value` có thể nhận biểu thức Spring Expression Language `#{...}`:

```java
@Value("#{2 * 3}")
private int six;

@Value("#{environment.getProperty('demo.region', 'local')}")
private String region;
```

Phép kết hợp `@Value("#{${demo.batch.base:6} * 2}")` chỉ nên dùng khi giá trị cấu hình được tin cậy, có định dạng số hợp lệ: Spring thay placeholder trước, sau đó SpEL mới đánh giá. Nếu property chứa text lạ hoặc mã biểu thức ngoài ý định, evaluation có thể lỗi hoặc mang tác dụng phụ. Với giá trị từ người dùng, hãy đọc thành kiểu đích, validate rồi tính trong Java (ví dụ `Math.multiplyExact`). Bài SpEL và bài kiểm soát biểu thức động trình bày sâu cách resolve, reference bean và giới hạn bảo mật.

## 11. Chuyển kiểu: String sang int, boolean, array, collection

`${demo.retries:4}` thường được resolve thành text `"4"` rồi convert theo kiểu của injection point. Spring hỗ trợ nhiều chuyển đổi cơ bản, ví dụ String sang `int`, `boolean` hoặc `String[]` ngăn cách bởi dấu phẩy khi converter phù hợp.

```java
@Value("${demo.retries:4}")
private int retries;

@Value("${demo.roles:read}")
private String[] roles;
```

Phân biệt **key thiếu** (resolution/default), **value sai kiểu** như `oops` cho `int` (conversion failure), và **value đổi kiểu thành công nhưng ngoài phạm vi nghiệp vụ** (validation). Generic collections/custom types phụ thuộc metadata và `ConversionService` được đăng ký; `@Value` không tự tạo `Converter<String, TimeoutPolicy>`. Bài chuyển kiểu chuyên sâu hướng dẫn Converter SPI, TypeDescriptor, bean `conversionService` và phép thử dữ liệu sai.

## 12. Strict placeholder resolution và static PropertySourcesPlaceholderConfigurer

Với resolver lenient mặc định, `@Value("${unknown.key}")` có thể nhận nguyên chuỗi marker nếu key không tồn tại. Trong plain Spring Core, muốn fail-fast khi placeholder bắt buộc không resolve được có thể đăng ký:

```java
@Configuration
class PlaceholderConfig {
    @Bean
    static PropertySourcesPlaceholderConfigurer placeholders() {
        return new PropertySourcesPlaceholderConfigurer();
    }
}
```

Cấu hình cần imports `Configuration`, `Bean` và `PropertySourcesPlaceholderConfigurer`. Configurer là một `BeanFactoryPostProcessor` nên factory method `static` giúp tránh tạo configuration instance quá sớm. Strict placeholder check không thay thế kiểm tra blank, kiểu số, port range hay credential. Phần chuyên sâu về configurer và unresolved placeholders kiểm tra các trường hợp này tách biệt.

## 13. Lifecycle của @Value và thời điểm có dữ liệu

Bảng so sánh:

| Điểm injection | Thời điểm | Có thể dùng trong constructor? | Có thể là final? | Cách test thuận lợi |
| --- | --- | --- | --- | --- |
| Constructor parameter `@Value` | Khi tạo instance | Có | Có (`final` field) | `new Class(value)` |
| Field `@Value` | Sau constructor, trong populate | Không, trước injection là Java default | Không nên | Spring integration context hoặc reflection (hạn chế) |
| Setter parameter `@Value` | Sau constructor, khi method injection | Không, trước setter | Field thường mutable | Gọi setter/test Spring |
| `@Bean` method parameter `@Value` | Khi Spring gọi factory method | Giá trị có sẵn để truyền vào object | Product có thể immutable | Unit test factory/config hoặc Spring context |

Các chặng liên quan:
- `@PostConstruct` chạy sau quá trình dependency injection thông thường, nếu annotation processor tương ứng được đăng ký.
- `InitializingBean.afterPropertiesSet()` cũng thấy giá trị đã inject.
- Constructor chỉ thấy giá trị **tham số constructor**; không chờ field/setter.
- Bean `prototype` nhận value mỗi lần target được tạo khi Spring thực hiện injection; giá trị lấy từ property sources tại thời điểm resolve có thể khác nếu nguồn bị mutate, nhưng không nên dựa vào đó như cơ chế live configuration.
- Bean singleton không tự refresh field `@Value` khi OS env, file properties hoặc system property thay đổi sau initialization.

### @Value và init callbacks

```java
@Component
class AuditConfig {
    @Value("${audit.enabled:false}")
    private boolean enabled;

    @PostConstruct
    void check() {
        System.out.println("audit enabled: " + enabled);
    }
}
```

Đây là fragment cần `jakarta.annotation.PostConstruct`, Spring Context, annotation API dependency và registration. Nó thể hiện cách kiểm tra sau field injection; **không** thêm nó vào project runnable mục 9 nếu không muốn thay output. Với Spring Framework 6+, dùng package `jakarta.annotation`, không phải `javax.annotation` của thế hệ cũ.

## 14. Các kiểu injection khác và lựa chọn thiết kế

### @Value so với @Autowired

- `@Value` mô tả **giá trị** (literal/property/SpEL) cho một injection point.
- `@Autowired` thường mô tả **dependency injection** theo bean type và có thể đánh dấu constructor/method/field cần xử lý.
- Trên setter trong ví dụ, `@Autowired` giúp Spring coi method là method injection; `@Value` trên tham số chỉ định giá trị thay vì một bean cùng type.

Không dùng `@Value("paymentService")` để inject bean `PaymentService` theo tên: đó là literal string (trừ khi dùng SpEL bean reference `#{@paymentService}`, vốn làm dependency graph kém rõ hơn). Với service bean, ưu tiên constructor injection thông thường bằng type và `@Qualifier` nếu cần.

### @Value so với EnvironmentAware/Environment injection

Nếu class cần hàng chục key động, có thể inject `Environment` rồi dùng `getProperty`, nhưng điều này giấu dependency cụ thể trong object. Nếu chỉ cần 1–3 giá trị, constructor `@Value` hoặc typed config bean rõ ràng hơn.

`EnvironmentAware` là callback cung cấp Environment cho bean framework; nó không cần thiết chỉ để nhận một chuỗi. Không biến một domain service thành container-aware object nếu có thể truyền config đã parse qua constructor.

### @Value so với @ConfigurationProperties

Trong Spring Boot, `@ConfigurationProperties` hỗ trợ binding một nhóm properties thành type có cấu trúc, nested objects, validation và tooling; thích hợp khi có nhiều keys. `@Value` vẫn hợp lý cho một vài values đơn giản.

**Lưu ý phạm vi:** `@ConfigurationProperties`/`@Validated` Boot binding có hạ tầng khác với Spring Core bare `spring-context`. Không dùng annotation Boot trong chương trình Spring Core không có dependency/autoconfiguration tương ứng.

### @Value so với property placeholders trong XML

Legacy XML có thể dùng placeholder/configurer để đặt bean property values. `@Value` là cách annotation-oriented, nhưng nguyên lý PropertySource/placeholder resolution tương tự. Chỉ cần dùng một cơ chế nhất quán trong dự án thay vì trộn ba cách mà không có chính sách.

## 15. Field injection, setter injection và constructor injection: ra quyết định

| Tình huống | Lựa chọn nên cân nhắc | Lý do |
| --- | --- | --- |
| Giá trị bắt buộc, không đổi sau tạo | Constructor `@Value` | Required dependency, final, validate sớm |
| Một module hạ tầng JavaBean có setter | Setter method `@Autowired` + parameter `@Value` | Phù hợp API của bean, có logic setter |
| Bài demo nhỏ hoặc legacy bean | Field `@Value` | Ngắn nhưng cần chú ý unit test/lifecycle |
| Cấu hình tạo một domain object thuần Java | `@Bean` parameter `@Value` | Domain class không phụ thuộc Spring |
| Nhiều key cùng prefix | Typed settings bean / Boot `@ConfigurationProperties` | Tập trung, dễ validate/test |
| Dynamic lookup thật sự khi thực thi | Inject `Environment`/provider phù hợp | Đọc khi cần, có policy cache/refresh riêng |
| Secret/password/token | Inject vào component cần thiết, không log | Tránh lan truyền và serialization |
| Giá trị phải refresh runtime | Cơ chế refresh/config management chuyên biệt | `@Value` singleton mặc định không tự cập nhật |

**Từ góc độ thiết kế:** mục tiêu không phải “có thể đặt `@Value` ở bao nhiêu nơi”, mà là chọn injection point diễn đạt invariant, vòng đời và khả năng test tốt nhất.

## 16. Lỗi thường gặp và cách xử lý

| Triệu chứng | Nguyên nhân điển hình | Cách khắc phục |
| --- | --- | --- |
| Field `@Value` là null trong constructor | Field injection chạy sau constructor | Dùng constructor parameter hoặc init callback |
| Setter `@Value` không chạy | Bean tạo bằng `new` hoặc method không được processor nhận diện | Đăng ký Spring bean; dùng `@Autowired` method + `@Value` parameter rõ ràng |
| `${demo.name}` bị inject nguyên văn | Lenient resolver, key missing | Dùng `PropertySourcesPlaceholderConfigurer` strict và kiểm nguồn |
| Startup fail vì unresolved placeholder | Required key không có trong PropertySources | Bổ sung nguồn/key hoặc default có chủ đích |
| `NumberFormatException`/type mismatch | `String` không convert được sang `int` | Kiểm config format và conversion |
| Có default nhưng vẫn lỗi giá trị `oops` | Default chỉ xử lý key missing | Validate value đã cung cấp |
| `@PropertySource` không tìm thấy file | Resource không nằm trên classpath/path sai | Đặt vào `src/main/resources`, kiểm packaging |
| Field không thay sau sửa properties file | Singleton chỉ inject lúc khởi tạo | Restart/recreate context hoặc giải pháp refresh chuyên biệt |
| `@Value` trên object tạo thủ công không có tác dụng | Không do Spring quản lý | Constructor injection hoặc để container tạo bean |
| `@Value` trên static field bị bỏ qua | Injection cơ chế instance, không hỗ trợ như field thông thường | Không giữ cấu hình mutable trong static state |
| Setter public bị đổi runtime gây race | Mutable singleton bị đổi ngoài lifecycle | Immutable config/đồng bộ nếu bắt buộc |
| `@Value("#{${base} * 2}")` parse lỗi | Property không có numeric value hoặc syntax sai | Validate typed value, cân nhắc Java calculation |
| Dependency bean không được inject do dùng @Value literal | Lẫn property injection với bean reference | Dùng constructor type injection/`@Qualifier` |
| `@Value` trên private field khó unit test | Không có constructor contract | Refactor sang constructor |
| Key env của OS không như mong đợi | Source precedence và name mapping | Kiểm `Environment`/PropertySources ở context |
| Dùng file YAML với @PropertySource mặc định | Spring Core không mặc nhiên parse YAML kiểu Boot | Dùng parser/PropertySourceFactory tương ứng hoặc Boot |
| Unexpected value từ JVM -D | System property ưu tiên hơn file | Kiểm deployment params, order nguồn |
| Secret xuất hiện khi log config object | `toString()`/structured serializer lộ value | Log allowlisted metadata, không log secret |
| `@Bean` BFPP method tạo config class sớm | Non-static method tạo processor | Khai báo static `@Bean` |
| Ký tự đặc biệt trong value bị SpEL hiểu | Ghép property vào expression không an toàn | Giữ typed config, không ghép untrusted expression |
| Nhiều constructors và không rõ cái nào được chọn | Bean instantiation constructor resolution | Dùng một constructor hoặc đánh dấu `@Autowired` hợp lý |

## 17. Unit test, integration test và kiểm tra hồi quy

### 17.1. Unit test cho class constructor-injected

`RetryPolicy` ở mục 4 có constructor thuần Java:

```java
RetryPolicy retryPolicy = new RetryPolicy(5, true);
if (retryPolicy.maxAttempts() != 5 || !retryPolicy.enabled()) {
    throw new AssertionError("Unexpected retry policy");
}
```

Fragment cần class `RetryPolicy` từ mục 4. Không cần Spring context, vì values đã là parameters.

### 17.2. Integration test cho ba injection locations

Với project runnable ở mục 9:

```java
try (AnnotationConfigApplicationContext context =
         new AnnotationConfigApplicationContext(AppConfig.class)) {
    ConfigView value = context.getBean(ConfigView.class);
    if (!"learning-core".equals(value.name())) {
        throw new AssertionError("Constructor injection failed");
    }
    if (!"ap-southeast-1".equals(value.region())) {
        throw new AssertionError("Field injection failed");
    }
    if (!value.enabled() || value.batchSize() != 12) {
        throw new AssertionError("Setter injection failed");
    }
    if (!"Core Ready".equals(
            context.getBean(StartupBanner.class).text())) {
        throw new AssertionError("@Bean parameter injection failed");
    }
}
```

Đây là Java **test fragment**, cần imports và classes từ project mục 9; có thể đặt trong JUnit test với `spring-test` hoặc method Java main riêng. Nó kiểm đúng behavior, không chỉ log annotation.

### 17.3. Test default và required property

Tạo context riêng với một property source override, đặt key khác hoặc thiếu key, rồi assert:
- Missing key có default → giá trị default được inject.
- Missing key bắt buộc, strict configurer → startup thất bại.
- Invalid numeric value → startup thất bại với nguyên nhân conversion.
- Value hợp lệ nhưng ngoài domain constraint → validation thất bại.
- Đổi file sau bean đã tạo → singleton không tự cập nhật.
- Bean tạo bằng `new` không nhận field/setter injection nếu không có Spring.
- Không có secret nào được ghi ra log khi xử lý lỗi.

Dùng **giá trị giả không có giá trị xác thực** trong tests; không cần token thật để kiểm cơ chế.

### 17.4. Tránh rò JVM/global config giữa tests

`System.setProperty(...)` thay đổi trạng thái toàn JVM. Nếu test buộc chỉnh, lưu giá trị cũ rồi restore trong `finally`. Với tests chạy song song, nên cô lập hoặc dùng `MapPropertySource` riêng trên context:

```java
context.getEnvironment().getPropertySources().addFirst(
        new MapPropertySource("test",
                Map.of("demo.retries", "9")));
```

Fragment cần imports và context chưa refresh. Đừng sửa global System property trong một test rồi kỳ vọng không ảnh hưởng tests khác.

## 18. Câu hỏi tự đánh giá

1. `@Value` nằm trong package nào?
2. Ba vị trí chính mà đề bài yêu cầu dùng `@Value` là gì?
3. Vì sao field injection chưa sẵn sàng trong constructor?
4. Khi có một constructor nhận `@Value` parameter, field `final` có được không?
5. Làm sao đánh dấu setter để Spring gọi nó và resolve `@Value` trên parameter?
6. Object tạo bằng `new` có tự được Spring inject không?
7. Phân biệt `${demo.limit:10}` và `#{2 * 5}`.
8. Default sau dấu hai chấm xử lý missing key hay invalid numeric value?
9. Trong Spring Core, làm sao nạp `demo.properties` từ classpath qua Java config?
10. Vì sao nên dùng static `@Bean PropertySourcesPlaceholderConfigurer`?
11. Nếu thiếu `demo.name` và có strict configurer, điều gì xảy ra?
12. Nếu không có strict configurer, unresolved placeholder chuỗi có thể bị xử lý ra sao?
13. Một `@Bean` method có nhận parameter được annotate `@Value` không?
14. Khi nào dùng `@Bean` parameter để giữ domain object không phụ thuộc Spring?
15. `@Value` có tự lấy config mới sau khi singleton đã khởi tạo không?
16. Vì sao nên validate value ngay trong constructor/setter?
17. `@Value("#{${demo.batch.base:6} * 2}")` sẽ trả gì khi value là 6?
18. Vì sao không được ghép raw user input vào SpEL expression?
19. Khi nào `@ConfigurationProperties` hợp lý hơn nhiều field `@Value`?
20. Trong demo, vì sao `banner: Core Ready` xuất hiện?
21. Nếu xóa `demo.retries=4` khỏi file và không có override, output retries là bao nhiêu?
22. Nếu đặt `demo.retries=oops`, default 3 có cứu được không?
23. Một `@Value` String field bị lộ qua `toString()` có an toàn nếu chứa token không?
24. Khi Spring context chứa nhiều PropertySources, value được chọn thế nào?
25. Vì sao setter injection cần cẩn trọng với mutable singleton?

**Đáp án:** (1) `org.springframework.beans.factory.annotation.Value`. (2) Constructor/method parameter, field, setter/method. (3) BeanPostProcessor inject sau constructor. (4) Có. (5) `@Autowired` trên setter và `@Value` trên parameter theo mẫu. (6) Không. (7) Placeholder property vs SpEL. (8) Missing key. (9) `@PropertySource`. (10) BFPP cần chạy rất sớm, tránh tạo config class. (11) Initialization fail nếu không nguồn nào cung cấp. (12) Có thể inject literal placeholder. (13) Có. (14) Khi domain object chỉ nhận plain Java parameters. (15) Không. (16) Fail fast và giữ invariant. (17) 12. (18) Rủi ro expression injection/hành vi ngoài ý định. (19) Khi có nhiều key/nested types/validation. (20) `@Bean` factory method parameter được resolve từ property. (21) 3. (22) Không; conversion fail. (23) Không, tuyệt đối không log secret. (24) Theo resolver và precedence nguồn đã cấu hình. (25) Setter có thể bị gọi ngoài lifecycle và cần thread-safety.

## 19. Checklist triển khai

- [ ] Giá trị bắt buộc/immutable ưu tiên constructor parameter `@Value`.
- [ ] Field injection chỉ dùng khi hiểu nhược điểm test và lifecycle.
- [ ] Setter injection đánh dấu đúng method/parameter và validate input.
- [ ] Không đọc field/setter value trong constructor.
- [ ] Bean phải do Spring quản lý; `new` không tự nhận `@Value`.
- [ ] Cấu hình nạp source rõ, ví dụ `@PropertySource("classpath:demo.properties")`.
- [ ] Dùng strict placeholder configurer nếu cần fail-fast missing keys.
- [ ] Khai báo `PropertySourcesPlaceholderConfigurer` bằng static `@Bean`.
- [ ] Dùng `${key:default}` có chủ đích; không che missing required config tùy tiện.
- [ ] Chú ý khác biệt missing, empty, invalid type.
- [ ] Kiểm converter thích hợp khi target không phải `String`.
- [ ] Không ghép dữ liệu không tin cậy thành SpEL.
- [ ] Giá trị SpEL giữ expression nhỏ, rõ và test được.
- [ ] Không giả định `@Value` là cơ chế live configuration refresh.
- [ ] Với nhiều key cùng prefix, cân nhắc typed config bean.
- [ ] Không dùng `@Value` literal thay constructor bean dependency injection.
- [ ] Không giữ secrets trong static fields hoặc ghi raw config vào log.
- [ ] Test cả constructor, field, setter và `@Bean` method parameter paths.
- [ ] Có test khi thiếu property, sai kiểu, ngoài biên, nguồn override.
- [ ] So sánh behavior sau injection, không dựa vào thứ tự callback phụ thuộc implementation.
- [ ] Kiểm tra classpath resource và Java package paths chính xác khi build.

## 20. Tài liệu tham khảo chính thức

- Spring Framework 6.2 — Using `@Value`: https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/value-annotations.html
- Spring Framework — `@Value` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/annotation/Value.html
- Spring Framework — Using `@Autowired`: https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html
- Spring Framework — Using `@Bean` Annotation / Method Parameters: https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html
- Spring Framework — Environment Abstraction: https://docs.spring.io/spring-framework/reference/core/beans/environment.html
- Spring Framework — Expression Language / Expressions in Bean Definitions: https://docs.spring.io/spring-framework/reference/core/expressions/beandef.html
- Spring Framework — PropertySourcesPlaceholderConfigurer Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/PropertySourcesPlaceholderConfigurer.html
- Spring Framework — Type Conversion: https://docs.spring.io/spring-framework/reference/core/validation/convert.html
- Spring Boot — Type-safe Configuration Properties (khi dùng Boot): https://docs.spring.io/spring-boot/reference/features/external-config.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="values-and-expressions-002">Placeholder ${...}, giá trị mặc định và placeholder lồng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Placeholder ${...}, giá trị mặc định và placeholder lồng nhau trong Spring Framework

## 1. Vì sao cần hiểu placeholder trong cấu hình Spring?

Khi ứng dụng chạy ở các môi trường khác nhau, những giá trị như host, port, đường dẫn resource, timeout hoặc endpoint thường không nên được viết cứng trong class Java. Spring cung cấp cú pháp placeholder `${...}` để **tham chiếu giá trị cấu hình** từ `Environment` và các `PropertySource`.

Ví dụ:

~~~java
@Value("${app.region}")
private String region;

@Value("${app.timeout:30}")
private int timeout;
~~~

Nếu trong nguồn cấu hình có `app.region=VN`, Spring có thể inject `"VN"`. Nếu `app.timeout` không có giá trị, `30` đóng vai trò mặc định rồi được chuyển sang `int`.

Tuy nhiên, placeholder không chỉ là phép tìm chuỗi đơn giản. Nó còn có thể:

- Có **giá trị mặc định** sau dấu hai chấm.
- Lồng placeholder trong **giá trị mặc định**.
- Lồng placeholder ngay trong **tên khóa** cần tra cứu.
- Xuất hiện trong **giá trị của một thuộc tính khác**.
- Trả giá trị khác nhau do **thứ tự ưu tiên của PropertySource**.
- Được xử lý theo chế độ **linh hoạt** hoặc **bắt buộc resolve**.
- Tương tác với `@Value`, `@PropertySource`, bean definition và đôi khi `#{...}` của Spring Expression Language.

Bài này giải thích đúng cơ chế **placeholder ${...}, giá trị mặc định và placeholder lồng nhau** trong Spring Framework Core Container, với các ví dụ Java 17 chạy độc lập bằng Maven.

**Quy tắc ngắn nhất:**

> `${key:default}` nghĩa là tìm `key` trong Environment; nếu không tìm thấy giá trị phù hợp, dùng `default`. Nếu `key` hoặc `default` lại có một placeholder khác, Spring có thể resolve tiếp phần lồng nhau.

Không nên coi `${...}` là biểu thức Java hoặc thay thế hoàn toàn Spring Expression Language.

## 2. Ba thành phần liên quan

### 2.1. PropertySource: nơi chứa giá trị

Một `PropertySource` có tên và dữ liệu key-value, ví dụ `MapPropertySource`:

~~~java
MapPropertySource source = new MapPropertySource(
    "demo-config",
    Map.of(
        "app.host", "localhost",
        "app.port", "8080"
    )
);
~~~

Đây là snippet Java trong một method, cần import `Map` và `MapPropertySource`. Source chỉ chứa dữ liệu; bản thân nó không tự inject vào các bean.

### 2.2. Environment: nơi tra cứu giá trị

`Environment` extends `PropertyResolver`, cung cấp:

~~~java
String host = environment.getProperty("app.host");
String optional = environment.getProperty("app.mode", "local");
String required = environment.getRequiredProperty("app.host");
~~~

Các đoạn này cần biến `environment` kiểu `Environment`. Giá trị được tìm qua những PropertySources theo thứ tự ưu tiên, từ source ưu tiên cao nhất đến thấp hơn.

### 2.3. Placeholder resolution: thay thế chuỗi

Các API đáng chú ý:

~~~java
String a = environment.resolvePlaceholders(
    "server=${app.host:localhost}");

String b = environment.resolveRequiredPlaceholders(
    "server=${app.host}");
~~~

- `resolvePlaceholders`: xử lý **linh hoạt**; nếu một placeholder thiếu và không có default, thường giữ nguyên `${...}` chưa được resolve.
- `resolveRequiredPlaceholders`: xử lý **nghiêm ngặt**; nếu placeholder thiếu và không có default, ném `IllegalArgumentException`.
- `getRequiredProperty("key")`: yêu cầu **property key** có giá trị không null; khác với xử lý toàn bộ một chuỗi template.

Quan trọng: cả hai method resolve chuỗi đều có thể gặp ngoại lệ với **placeholder circular reference** hoặc cấu hình sai. “Linh hoạt” không có nghĩa bỏ qua mọi lỗi.

## 3. Cú pháp cơ bản của placeholder

Mẫu chuẩn:

~~~text
${property.key}
~~~

Nếu `property.key=value`, placeholder được thay bằng `value`.

Ví dụ nguồn:

~~~properties
app.name=inventory
app.version=1.0
~~~

Template:

~~~text
Service: ${app.name}, version ${app.version}
~~~

Kết quả:

~~~text
Service: inventory, version 1.0
~~~

Spring không thay đổi file `.properties`; nó chỉ tạo ra chuỗi đã resolve tại thời điểm sử dụng API hoặc lúc dependency injection.

### Cú pháp có giá trị mặc định

~~~text
${property.key:default-value}
~~~

Nếu `property.key` không resolve được, sử dụng `default-value`.

Ví dụ:

~~~text
${app.region:VN}        -> VN, nếu app.region không tồn tại
${app.timeout:30}       -> 30, nếu app.timeout không tồn tại
${app.enabled:false}    -> false, nếu app.enabled không tồn tại
~~~

Khi template được inject vào một tham số `int` hay `boolean`, Spring còn cần **chuyển đổi kiểu dữ liệu** từ giá trị sau resolution.

**Dấu `:` ngăn giữa key và default**, theo cấu hình mặc định của `AbstractPropertyResolver`. Nó không phải ký tự bắt buộc trong tên property.

## 4. Khi nào giá trị mặc định được dùng?

Giả sử nguồn cấu hình:

~~~properties
app.name=orders
app.timeout=20
~~~

Với:

~~~text
${app.timeout:30}  -> 20
${app.region:VN}   -> VN
${app.name:demo}   -> orders
~~~

Default chỉ được dùng **khi không tìm được giá trị phù hợp** của khóa theo resolver contract.

Có ba trường hợp khác nhau:

| Tình trạng source | `${app.mode:local}` | Giải thích |
| --- | --- | --- |
| Không có khóa `app.mode` | `local` | Dùng default |
| Có `app.mode=prod` | `prod` | Property thắng default |
| Có `app.mode=` (chuỗi rỗng) | Chuỗi rỗng | Rỗng không đồng nghĩa mất khóa |

### Chuỗi rỗng không phải “không có”

Một `PropertySource` trả `""` vẫn trả một đối tượng String **không phải null**, nên có thể thắng những nguồn ưu tiên thấp hơn và **ngăn dùng default**.

Nếu nghiệp vụ muốn coi rỗng là thiếu, đừng mặc định tin `${key:default}` sẽ làm thay. Bạn cần validation/normalization ở tầng cấu hình có trách nhiệm.

### Default không bảo đảm cấu hình hợp lệ

~~~java
@Value("${worker.threads:4}")
int threads;
~~~

Dù Spring inject được số 4, logic nghiệp vụ vẫn cần xác nhận số luồng nằm trong giới hạn hợp lệ. Nếu property thực tế chứa `worker.threads=abc`, Spring có thể ném lỗi conversion chứ không tự bỏ `abc` rồi lấy default 4.

## 5. Placeholder lồng nhau trong giá trị mặc định

Một kiểu sử dụng quan trọng là **chuỗi fallback nhiều cấp**:

~~~text
${app.primary:${app.secondary:offline}}
~~~

Spring ưu tiên:

1. Lấy `app.primary` nếu có.
2. Nếu thiếu, lấy `app.secondary` nếu có.
3. Nếu cả hai thiếu, lấy literal `offline`.

Ví dụ nguồn:

~~~properties
app.secondary=backup-server
~~~

Kết quả:

~~~text
${app.primary:${app.secondary:offline}} -> backup-server
~~~

Nếu thêm:

~~~properties
app.primary=main-server
~~~

thì kết quả là `main-server`.

### Lồng nhiều tầng

~~~text
${app.primary:${app.secondary:${app.tertiary:local}}}
~~~

Đây là một fallback chain ba property keys, cuối cùng là literal `local`. Dù Spring hỗ trợ nested placeholders, một chuỗi quá sâu sẽ khó đọc và khó bảo trì. Với cấu hình phức tạp, cân nhắc đặt quy tắc fallback trong typed configuration hoặc logic Java có validation.

### Giá trị mặc định có chứa dấu hai chấm

Ví dụ URL:

~~~text
${api.url:https://localhost:8443}
~~~

Dấu `:` đầu tiên theo parser mặc định phân cách key và default; những dấu `:` nằm trong **phần default** có thể là một phần URL.

Tuy nhiên, nếu **tên property key thật sự chứa dấu hai chấm**, cú pháp default có thể gây nhập nhằng. Spring cho phép cấu hình `setValueSeparator(...)` trên resolver/configurer trong những trường hợp đặc biệt. Thường tốt hơn là chọn canonical property names không gây xung đột.

## 6. Placeholder lồng nhau ngay trong tên khóa

Ngoài fallback, Spring có thể resolve **placeholder nằm trong phần key**:

~~~text
${service.url.${app.env}}
~~~

Nếu:

~~~properties
app.env=dev
service.url.dev=https://dev.example.test
service.url.prod=https://prod.example.test
~~~

Spring resolve `${app.env}` trước để tạo key `service.url.dev`, rồi tìm giá trị `https://dev.example.test`.

Quá trình:

~~~text
${service.url.${app.env}}
       |
       v
${service.url.dev}
       |
       v
https://dev.example.test
~~~

Đây là cách chọn giữa nhiều **khóa cấu hình** theo một khóa khác, không phải chọn Spring bean theo `@Profile`.

### Key lồng nhau kết hợp default

~~~text
${service.url.${app.env:local}:http://localhost:8080}
~~~

Ý nghĩa:

- `${app.env:local}` chọn tên môi trường: property thực hoặc `local`.
- Spring xây key `service.url.<env>`.
- Nếu key được xây không có giá trị, dùng URL mặc định.

Điều kiện cần: placeholder parser hỗ trợ nested syntax, các dấu ngoặc cân bằng, và key được tạo ra phải tương thích với `PropertySource` thực tế.

### Không lạm dụng dynamic property names

Tên key lồng nhau phù hợp khi có một tập biến thể nhỏ và rõ ràng. Nếu bạn có hàng trăm tenants hoặc mapping runtime phức tạp, việc biểu diễn toàn bộ logic routing bằng `${...}` lồng nhiều tầng có thể gây khó debug. Khi ấy typed configuration map và một service chọn strategy thường dễ kiểm thử hơn.

## 7. Placeholder nằm trong giá trị một PropertySource

Lồng không chỉ xảy ra khi viết template trong `@Value`. Một property value cũng có thể tham chiếu property khác:

~~~properties
app.host=localhost
app.port=8080
app.base-url=http://${app.host}:${app.port}
~~~

Khi gọi:

~~~java
String url = environment.getProperty("app.base-url");
~~~

`PropertySourcesPropertyResolver` thường resolve nested placeholders trong value và trả:

~~~text
http://localhost:8080
~~~

Như vậy có hai lớp:

1. Tìm `app.base-url` theo precedence.
2. Resolve các `${app.host}` và `${app.port}` xuất hiện **bên trong giá trị** đã tìm được.

Một cấu hình thực tế:

~~~properties
service.host=api.internal
service.port=443
service.base-url=https://${service.host}:${service.port}
service.health-url=${service.base-url}/health
~~~

Khi resolve `service.health-url`, Spring có thể nối qua nhiều tham chiếu lồng nhau để thu được URL cuối cùng.

**Nhưng:** Nếu bạn tạo vòng tham chiếu giữa properties, resolver không thể tính ra giá trị đúng; xem phần lỗi circular placeholder bên dưới.

## 8. Placeholder resolution khác việc chỉ đọc raw value

Một `PropertySource` có thể lưu raw string:

~~~text
app.base-url = http://${app.host}:${app.port}
~~~

Đọc trực tiếp nguồn:

~~~java
Object raw = source.getProperty("app.base-url");
~~~

có thể trả nguyên chuỗi chứa `${...}`, vì `PropertySource` đóng vai trò nơi lưu dữ liệu.

Nhưng:

~~~java
String result = environment.getProperty("app.base-url");
~~~

đi qua `PropertyResolver` và có thể resolve nested placeholders.

**Không nhầm** giữa:

- `PropertySource.getProperty(key)`: giá trị thô của một nguồn.
- `Environment.getProperty(key)`: tra nhiều nguồn theo precedence và có thể resolve placeholder lồng trong value.
- `Environment.resolvePlaceholders(text)`: resolve các placeholders xuất hiện trong **một chuỗi được cung cấp**.
- `@Value("${...}")`: resolution xảy ra trong quá trình Spring inject dependency hoặc cấu hình bean.

Nếu code của bạn bỏ qua Environment để lấy raw map trực tiếp, kết quả có thể còn nguyên placeholder.

## 9. `resolvePlaceholders()`: linh hoạt với placeholder không tìm thấy

`environment.resolvePlaceholders("Hello ${app.user}, ${app.unknown}")` thay những key có thật và giữ nguyên token `${app.unknown}` khi key đó vắng mặt. Nếu `app.user=Lan`, kết quả là `Hello Lan, ${app.unknown}`.

Đây là contract **lenient**, phù hợp khi downstream sẽ xử lý template còn dang dở. Với DB URL, service endpoint hoặc queue name, marker còn nguyên thường phải được coi là lỗi cấu hình. Bài phát hiện placeholder thiếu chứng minh các đường xử lý lenient/strict và nguyên nhân exception bằng ví dụ riêng.

## 10. `resolveRequiredPlaceholders()`: yêu cầu resolve đầy đủ

`environment.resolveRequiredPlaceholders("server=${app.host}")` ném `IllegalArgumentException` nếu thiếu key không có fallback. Mẫu `${app.host:localhost}` vẫn hợp lệ khi thiếu key vì có default.

| API | Khi key bắt buộc không tồn tại |
| --- | --- |
| `getRequiredProperty("key")` | Ném `IllegalStateException` |
| `resolveRequiredPlaceholders("${key}")` | Ném `IllegalArgumentException` |
| `@Value("${key}")` với strict configurer | Bean creation/refresh có thể thất bại, exception có thể được Spring wrap |

Ba cách kiểm tra áp dụng cho ba ngữ cảnh khác nhau: đọc một property, resolve template hoàn chỉnh hoặc inject bean. Không dựa vào tên exception ngoài cùng để bỏ qua root cause.

## 11. Vai trò của `@Value` trong bean Spring

`@Value` dùng để truyền resolved value vào field, constructor parameter, method parameter hoặc factory-method parameter.

Ví dụ:

~~~java
@Component
public class ClientSettings {

    private final String endpoint;
    private final int timeout;

    public ClientSettings(
            @Value("${client.endpoint:http://localhost:8080}")
                String endpoint,
            @Value("${client.timeout:30}")
                int timeout) {
        this.endpoint = endpoint;
        this.timeout = timeout;
    }
}
~~~

Đây là class minh họa cần imports `Component` và `Value`, cùng Spring component scanning/bean registration để constructor injection hoạt động.

`@Value("${client.timeout:30}")` không yêu cầu property phải là số ngay trong chuỗi annotation; Spring resolution trả text và conversion infrastructure chuyển thành `int` nếu conversion hợp lệ.

### Constructor injection thường rõ hơn field injection

Cùng cấu hình có thể viết:

~~~java
@Value("${client.endpoint}")
private String endpoint;
~~~

nhưng field injection khiến class không thể được tạo dễ dàng bằng constructor thuần Java trong unit test. Với configuration có một vài scalar values, constructor parameter injection giúp dependencies hiện rõ. Với nhiều properties, một typed configuration class có validation thường tốt hơn rất nhiều `@Value` riêng lẻ.

### `@Value` không phải runtime dynamic config

Một singleton nhận `endpoint` tại constructor có thể giữ giá trị ấy suốt vòng đời object. Việc thay `PropertySources` sau refresh không tự inject lại tất cả fields/constructor parameters.

Nếu cần hot reload cấu hình, phải thiết kế rõ cơ chế refresh/rebinding hoặc truy xuất Environment theo nhu cầu với concurrency/validation phù hợp. **Placeholder resolution không đồng nghĩa live binding.**

## 12. Spring Core: vì sao nên biết `PropertySourcesPlaceholderConfigurer`?

Trong plain `ApplicationContext`, embedded resolver có thể giữ nguyên `${missing.key}`. `PropertySourcesPlaceholderConfigurer` là `BeanFactoryPostProcessor` cho phép policy strict với bean definitions và `@Value`. Trong một `@Configuration` class:

```java
@Bean
static PropertySourcesPlaceholderConfigurer placeholders() {
    return new PropertySourcesPlaceholderConfigurer();
}
```

Method `static @Bean` được khuyến nghị vì configurer phải hoạt động trước regular bean creation. Nó không đổi contract của `Environment.resolvePlaceholders` thành strict; đó vẫn là API lenient. Bài configurer trình bày đầy đủ precedence, `localOverride` và các property sources riêng.

## 13. Ví dụ hoàn chỉnh Java 17 + Spring Context 6.1

Ví dụ sau dùng **một chương trình Spring Framework thuần** và `MapPropertySource`; không cần Spring Boot, file ngoài, database hay mạng.

Nó chứng minh:

1. Một placeholder thường từ property có thật.
2. `${key:default}` khi thiếu key.
3. Dynamic property key dùng placeholder lồng nhau.
4. Fallback nested `${first:${second:literal}}`.
5. Một giá trị property chứa placeholders khác.
6. Sự khác biệt giữa lenient và strict resolution.
7. `@Value` constructor parameter inject endpoint, timeout, name và base URL với `PropertySourcesPlaceholderConfigurer` đã đăng ký.

### 13.1. Cấu trúc project

~~~text
placeholder-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── com/
                └── example/
                    └── placeholders/
                        └── PlaceholderDemo.java
~~~

### 13.2. File `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>placeholder-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.1.13</version>
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
                <configuration>
                    <mainClass>com.example.placeholders.PlaceholderDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 13.3. File `src/main/java/com/example/placeholders/PlaceholderDemo.java`

~~~java
package com.example.placeholders;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

public class PlaceholderDemo {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("demo238", Map.of(
                    "demo238.name", "java",
                    "demo238.env", "dev",
                    "demo238.endpoint.dev", "https://dev.example.test",
                    "demo238.endpoint.prod", "https://prod.example.test",
                    "demo238.host", "localhost",
                    "demo238.port", "8080",
                    "demo238.base",
                        "https://${demo238.host}:${demo238.port}",
                    "demo238.fallback", "offline"
                ))
            );

            context.register(AppConfig.class);
            context.refresh();

            Settings settings = context.getBean(Settings.class);
            Environment env = context.getEnvironment();

            System.out.println("client=" + settings.name());
            System.out.println("endpoint=" + settings.endpoint());
            System.out.println("timeout=" + settings.timeout());
            System.out.println("base=" + settings.base());

            System.out.println("nested-key=" +
                env.resolveRequiredPlaceholders(
                    "${demo238.endpoint.${demo238.env}}"));

            System.out.println("nested-default=" +
                env.resolveRequiredPlaceholders(
                    "${demo238.primary:${demo238.fallback:OFF}}"));

            System.out.println("optional=" +
                env.resolvePlaceholders("${demo238.absent}"));

            boolean missingRequired = false;
            try {
                env.resolveRequiredPlaceholders("${demo238.absent}");
            } catch (IllegalArgumentException expected) {
                missingRequired = true;
            }
            System.out.println("missing-required=" + missingRequired);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {

        @Bean
        public static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        public Settings settings(
                @Value("${demo238.name}") String name,
                @Value("${demo238.endpoint.${demo238.env}:https://fallback.example.test}")
                    String endpoint,
                @Value("${demo238.timeout:30}") int timeout,
                @Value("${demo238.base}") String base) {
            return new Settings(name, endpoint, timeout, base);
        }
    }

    public record Settings(
            String name, String endpoint, int timeout, String base) {
    }
}
~~~

### 13.4. Chạy

Yêu cầu JDK 17+ và Maven 3.9.x. Đứng tại thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do `System.out.println` trong chương trình tạo:

~~~text
client=java
endpoint=https://dev.example.test
timeout=30
base=https://localhost:8080
nested-key=https://dev.example.test
nested-default=offline
optional=${demo238.absent}
missing-required=true
~~~

Maven hoặc logging framework có thể in thêm dòng khác; đây là **stdout do code ví dụ chủ động in**.

### 13.5. Giải thích từng kết quả

- `client=java`: `${demo238.name}` được thay bằng property tồn tại.
- `endpoint=https://dev.example.test`: inner placeholder `${demo238.env}` cho `dev`, rồi outer lookup tìm `demo238.endpoint.dev`. Default URL không cần dùng.
- `timeout=30`: `demo238.timeout` không có trong map, nên `${demo238.timeout:30}` lấy 30 và Spring convert sang `int`.
- `base=https://localhost:8080`: property `demo238.base` chứa hai placeholders khác, Environment tiếp tục resolve.
- `nested-key=...`: minh họa nested key resolution trực tiếp bằng `resolveRequiredPlaceholders`.
- `nested-default=offline`: thiếu primary, tồn tại fallback nên giá trị `offline` được lấy từ `demo238.fallback`.
- `optional=${demo238.absent}`: lenient resolver giữ nguyên marker chưa resolve.
- `missing-required=true`: strict resolver ném `IllegalArgumentException` và code bắt lỗi có chủ đích.

**Đảm bảo độ ổn định:** Keys mang prefix `demo238.` được demo source đưa vào Environment bằng `addFirst` trước `refresh()`; chúng không phụ thuộc vào các file cấu hình bên ngoài của máy chạy.

### 13.6. Một sửa đổi để thực hành

Trong map, bổ sung:

~~~java
"demo238.timeout", "45"
~~~

Khi thêm entry, hãy đặt dấu phẩy đúng với `Map.of`. Kết quả `timeout=45` vì property thực tế thắng default `30`.

Thay:

~~~java
"demo238.env", "prod"
~~~

thì endpoint được chọn là `https://prod.example.test`.

Nếu đặt env thành `qa` mà không có `demo238.endpoint.qa`, expression trên `@Value` có URL default và có thể sử dụng `https://fallback.example.test`.

**Đừng quên:** `Map.of` không cho phép duplicate keys. Muốn thêm giá trị đã có, sửa entry cũ thay vì khai báo trùng key.

## 14. Placeholder nested không bắt buộc đồng nghĩa “default nested”

Hai cú pháp:

~~~text
${service.url.${app.env}}
${service.url:${fallback.url:http://localhost}}
~~~

có hai ý nghĩa khác nhau:

1. **Nested key:** phần trong `${app.env}` quyết định **tên key** của placeholder ngoài.
2. **Nested default:** phần trong `${fallback.url:...}` chỉ được dùng **khi key chính service.url không có giá trị**.

Có thể kết hợp:

~~~text
${service.url.${app.env:dev}:${service.fallback-url:http://localhost:8080}}
~~~

Đọc theo từng tầng:

- Chọn env hoặc dev.
- Tra key `service.url.<env>`.
- Nếu không có, tra `service.fallback-url`.
- Nếu cũng không có, dùng literal URL.

Cú pháp kết hợp hữu ích khi lựa chọn environment nhỏ và quy tắc fallback có thể hiểu ngay. Nhưng đừng tạo chuỗi 5–6 tầng khi một typed configuration rõ ràng hơn.

## 15. Thứ tự ưu tiên của PropertySources ảnh hưởng mọi mức lồng nhau

Giả sử:

~~~text
Nguồn HIGH:
  app.env=prod
  app.timeout=40

Nguồn LOW:
  app.env=dev
  app.timeout=20
  service.url.prod=https://prod.example.test
  service.url.dev=https://dev.example.test
~~~

Với:

~~~text
${service.url.${app.env}}
~~~

inner key `app.env` lấy `prod` từ HIGH, rồi outer key `service.url.prod` lấy URL từ LOW.

**PropertySources resolution là theo từng key**. Không có quy tắc “một nested placeholder phải dùng cùng PropertySource với outer placeholder”.

Điều đó có nghĩa một URL hoàn chỉnh có thể được ghép từ nhiều nguồn. Đây là tính linh hoạt mạnh nhưng cũng gây khó debug nếu những giá trị môi trường/chân đế do operator hoặc test override khác nhau.

### Default không ưu tiên hơn property ở nguồn thấp

Nếu `app.timeout` vắng mặt tại HIGH nhưng tồn tại với giá trị `20` tại LOW, `${app.timeout:40}` lấy `20` từ LOW, **không** lấy default `40` chỉ vì HIGH thiếu key.

Default được dùng sau khi resolver đã xem xét các nguồn hiện có theo thứ tự.

### Lưu ý về masking

Một nguồn HIGH có `app.timeout=""` có thể ngăn truy cập `app.timeout=20` ở LOW, vì chuỗi rỗng là giá trị không null. Hãy kiểm tra cấu hình trùng khóa với giá trị rỗng thật kỹ.

## 16. `getProperty(key, default)` so với `${key:default}`

Có hai cách cung cấp fallback:

~~~java
String region = environment.getProperty("app.region", "VN");
~~~

và:

~~~java
String region = environment.resolveRequiredPlaceholders(
    "${app.region:VN}");
~~~

Trong ví dụ đơn giản, nếu property thiếu, cả hai cho `VN`. Nhưng **chúng là hai API khác nhau**:

- `getProperty(key, default)` tìm đúng key đã truyền và trả default Java argument nếu thiếu.
- `resolveRequiredPlaceholders(text)` phân tích cả template theo cú pháp placeholder, có thể bao gồm nhiều keys, lồng default/key và literal text.
- Khi giá trị property được tham chiếu chứa placeholders lồng trong value, resolver có thể xử lý tiếp với strictness được cấu hình.
- Nếu string template cần chứa chính ký tự `${...}` mà không resolve, phải cân nhắc escape/nguồn dữ liệu phù hợp.

**Gợi ý:** Khi application code chỉ cần đọc một key, `getProperty(key, default)` thường trực tiếp và dễ test. Khi cần template có nhiều placeholders hoặc inject bằng annotation, cú pháp `${...}` tiện hơn.

## 17. `@Value` placeholder khác với Spring Expression Language `#{...}`

`${app.timeout:30}` tra configuration key và dùng default khi thiếu, còn `#{2 + 3}` là SpEL đánh giá biểu thức thành 5. Cả hai có thể xuất hiện trong `@Value`:

```java
@Value("${app.timeout:30}")
int timeout;

@Value("#{2 + 3}")
int five;
```

Một expression như `@Value("#{${app.factor:2} * 10}")` thay placeholder trước rồi mới đánh giá SpEL; chỉ an toàn khi giá trị có nguồn gốc và định dạng đã kiểm soát. Cú pháp default `:` của placeholder khác toán tử Elvis trong SpEL. Bài so sánh `${...}` với `#{...}` tiếp theo đi sâu vào bean references, evaluation context và an toàn.

## 18. Placeholder trong `@PropertySource` location

Một configuration class có thể dùng placeholder để chọn resource:

~~~java
@Configuration
@PropertySource(
    "classpath:config/${app.stage:local}/service.properties")
class ServiceConfig {
}
~~~

Spring resolve `${app.stage:local}` từ những nguồn **đã tồn tại tại thời điểm configuration class được xử lý**.

Nếu `-Dapp.stage=prod`, path trở thành:

~~~text
classpath:config/prod/service.properties
~~~

Nếu property thiếu, default path là:

~~~text
classpath:config/local/service.properties
~~~

### Vòng phụ thuộc cần tránh

Bạn không thể kỳ vọng rằng **chính file chưa được tìm thấy** cung cấp value để chọn đường dẫn của nó. Ví dụ path cần `app.stage`, nhưng key này chỉ nằm bên trong file ứng với stage ấy — configuration sẽ bế tắc.

Hãy đặt `app.stage` vào source bootstrap sớm như system property, environment variable hoặc một source được thêm trước refresh.

### Khi thiếu resource

`@PropertySource` có thuộc tính `ignoreResourceNotFound` cho trường hợp file optional. Đặt default trong location placeholder **khác** với cho phép thiếu resource: chọn ra `local/service.properties` nhưng file đó không tồn tại vẫn có thể làm context refresh thất bại.

## 19. Không phải mọi chỗ viết `${...}` đều tự được Spring xử lý

Cùng chuỗi ký tự `${app.host}` nhưng đặt ở đâu sẽ quyết định có được resolve không.

**Trường hợp được Spring xử lý trong lifecycle phù hợp:**

- `@Value("${app.host}")` trên injection point của một Spring-managed bean.
- Metadata bean definition do `PropertySourcesPlaceholderConfigurer` xử lý.
- `Environment.resolvePlaceholders("${app.host}")` gọi trực tiếp.
- Giá trị property lồng nhau được `PropertyResolver` đọc.
- Location của `@PropertySource` được configuration processor resolve.

**Trường hợp không được tự xử lý:**

~~~java
String x = "${app.host}";
System.out.println(x);
~~~

Đây là Java code thuần. Nếu không gọi Spring API xử lý, stdout vẫn là literal `${app.host}`.

Tương tự:

~~~java
MySettings s = new MySettings("${app.host}");
~~~

Tạo object bằng `new` ngoài container không làm Spring tự thay chuỗi constructor argument ấy.

### Annotation trên object ngoài Spring

Một class có `@Value` nhưng được gọi `new MyClass()` thủ công không đương nhiên được Spring inject giá trị. Annotation là metadata cần hạ tầng container xử lý.

## 20. Phân biệt default trong placeholder và default profile

Spring cũng có profile `default`:

~~~java
@Profile("default")
@Bean
Service fallbackService() {
    return new Service();
}
~~~

Đây là snippet minh họa có class `Service`.

**Không nhầm** với:

~~~java
@Value("${service.mode:default}")
String mode;
~~~

`@Profile("default")` quyết định bean definition có được đăng ký khi không có active profiles (theo cấu hình default profiles). `${service.mode:default}` chỉ cho string fallback nếu không có property `service.mode`.

Một source có key `spring.profiles.active` hoặc `app.env` có thể ảnh hưởng lựa chọn profiles ở bootstrap, nhưng giá trị default của placeholder không tự kích hoạt một Spring profile.

Nếu cần tạo implementation bean theo môi trường, dùng `@Profile`/`@Conditional`. Nếu chỉ cần chọn property value cho một bean, placeholder/default thường là lựa chọn đơn giản hơn.

## 21. Chuỗi literal trông giống placeholder và escaping

Có những tình huống ứng dụng phải giữ nguyên chuỗi `${token}` để một template engine phía sau xử lý. Nếu cho chuỗi này qua Spring resolver, nó có thể được thay ngoài ý muốn hoặc gây lỗi strict resolution.

Một số API placeholder của Spring hỗ trợ cấu hình **escape character**, prefix, suffix và value separator. Chi tiết escape có thể khác giữa Spring minor versions và giữa `PropertySourcesPlaceholderConfigurer` với các resolver khác. Đặc biệt, đừng giả định một cách ghi backslash trong file `.properties` cũng đúng nguyên xi trong Java string literal, YAML và XML.

Nguyên tắc an toàn:

1. Biết chuỗi được xử lý qua **API nào**.
2. Nếu muốn literal, tránh đưa nó qua strict placeholder resolver hoặc dùng escape mechanism có tài liệu cho đúng phiên bản.
3. Với Java string literal, nhớ Java cũng có quy tắc escape riêng (ví dụ `\\` là một dấu backslash thực trong chuỗi).
4. Thử bằng unit/integration test cho cả source encoding và resolver path thực tế.
5. Nếu đang chuyển placeholder cho engine khác, cân nhắc giữ dữ liệu ở một field/source không tham gia property resolution.

### Tùy chỉnh value separator

`ConfigurablePropertyResolver` có:

~~~java
resolver.setValueSeparator("?");
~~~

Nếu biến `resolver` implement interface phù hợp, default syntax sau thay đổi sẽ không còn dùng dấu `:` như trước. Đây là ví dụ API tùy chỉnh; **không nên thay separator tùy tiện** khi ứng dụng hoặc thư viện đang dùng cú pháp `${key:default}` phổ biến.

Việc tùy chỉnh prefix/suffix cũng có thể gây xung đột với frameworks khác. Chỉ dùng khi có yêu cầu tương thích đặc biệt và đã có tests.

## 22. Vòng tham chiếu placeholder: lỗi cần fail fast

Nếu properties:

~~~properties
a=${b}
b=${a}
~~~

thì resolver không thể tìm giá trị cuối cùng bằng cách lần theo tham chiếu mãi. Spring placeholder parsing có kiểm tra vòng tham chiếu và có thể ném `IllegalArgumentException` về circular placeholder reference.

Tương tự với default chain tự quay lại:

~~~properties
app.primary=${app.secondary}
app.secondary=${app.primary}
~~~

Không xử lý bằng cách thêm nhiều tầng fallback khiến vòng phụ thuộc bị che khuất. Hãy sửa dependency graph của configuration:

~~~properties
app.primary=https://api.example.test
app.secondary=${app.primary}
~~~

Điều thứ hai có thể phù hợp như một alias property. Điều thứ nhất cần có giá trị cuối thực sự để chain kết thúc.

### Cách debug

- Tìm nơi định nghĩa mỗi key.
- Duyệt chuỗi tham chiếu từ key đang lỗi.
- Ghi chú những key trùng ở nhiều PropertySources.
- Xác định giá trị nguồn **được precedence chọn** trước khi resolve nested placeholders.
- Kiểm tra default chain có quay lại một key cũ không.
- Viết test gọi `resolveRequiredPlaceholders` để fail nhanh ngay trong CI.

## 23. Một placeholder tồn tại nhưng sai kiểu

Giả sử:

~~~properties
app.pool-size=not-a-number
~~~

và:

~~~java
@Value("${app.pool-size:10}")
int poolSize;
~~~

Giá trị `not-a-number` **tồn tại**, nên `10` không được dùng. Khi Spring cố convert sang `int`, bean creation có thể thất bại.

Các loại lỗi khác:

- `${feature.enabled:true}` nhưng property là `perhaps`, không convert được thành boolean theo converter.
- `${app.timeout:30}` nhưng business rule yêu cầu timeout lớn hơn 60; conversion thành số thành công nhưng validation nghiệp vụ thất bại.
- `${app.endpoint:https://example.test}` resolve chuỗi URL nhưng URI thực tế không hợp lệ cho client.
- `${app.threads:4}` nhận số rất lớn gây quá tải nếu không validate.

**Placeholder không thay validation.** Với nhiều cấu hình liên quan, dùng một typed settings object có validation và rõ nguồn lỗi hơn hàng loạt `@Value` fields.

## 24. Bảo mật khi placeholder chứa secrets

Cấu hình có thể chứa:

~~~properties
db.password=${DB_PASSWORD}
service.api-key=${EXTERNAL_TOKEN}
~~~

Đây là ví dụ file properties mang tính minh họa. Tùy `PropertySource`/resolver và naming convention, những strings `DB_PASSWORD`/`EXTERNAL_TOKEN` có thể được resolve từ OS environment khi keys khớp.

Cảnh báo:

- Đừng in toàn bộ `Environment` hoặc `PropertySources` trong log.
- Không đưa secrets vào message lỗi được gửi tới client.
- Không log URL đã thay placeholder nếu nó chứa userinfo/token.
- Nếu thiếu secret bắt buộc, nên fail fast thay vì dùng default yếu như mật khẩu mẫu.
- Phân biệt placeholder có default cho một **giá trị không nhạy cảm** và một bí mật bắt buộc.
- Kiểm tra precedence: system property/test source ưu tiên cao có thể override secret source theo cấu hình.
- Khi dùng Vault/Kubernetes Secrets/remote config, phải xét bootstrap timing và secret rotation riêng.

`${db.password:changeme}` có thể khiến ứng dụng chạy sai môi trường với credential yếu. Thường tránh default bí mật như vậy trong production.

## 25. Spring Boot và placeholder: chung nền tảng, nhiều tiện ích hơn

Spring Boot vẫn dùng Spring `Environment` và các cơ chế placeholder, nhưng có thêm:

- Config Data tự nạp `application.properties`, `application.yml` và file theo profile.
- Thứ tự ưu tiên externalized configuration riêng.
- `@ConfigurationProperties` cho type-safe binding, nested objects và collections.
- Relaxed binding cho các dạng tên như kebab-case/environment variables.
- Các integration points cấu hình startup.

Ví dụ trong Spring Boot:

~~~properties
app.host=localhost
app.port=8080
app.base-url=http://${app.host}:${app.port}
~~~

`app.base-url` vẫn là property value chứa nested placeholders khi được resolve.

### Không áp đặt relaxed binding của Boot cho mọi MapPropertySource

Một key `app.max-connections` trong `MapPropertySource` không nhất thiết giống `app.maxConnections` theo raw lookup. Spring Boot Binder xử lý relaxed binding cho `@ConfigurationProperties` theo convention riêng, nhưng không phải mọi API `Environment.getProperty(...)` hay custom source đều tương đương.

Nếu dùng `@Value` trong Boot, nên chọn dạng canonical kebab-case cho property key khi phù hợp với khuyến nghị Boot để tối ưu resolution nhất quán.

### Typed config khi nhiều placeholders trở nên phức tạp

Thay vì:

~~~java
@Value("${app.api.${app.env:dev}:${app.default-api:http://localhost}}")
String endpoint;
~~~

một Boot ứng dụng lớn có thể bind map of endpoints và active environment vào một `@ConfigurationProperties` class, sau đó chọn endpoint bằng Java logic có validation. Đây là chuyện **thiết kế dễ hiểu**, không phải vì Spring không hỗ trợ nested syntax.

## 26. Debug khi giá trị placeholder không như dự kiến

Quy trình chẩn đoán:

**Bước 1 — Xác định template gốc.** Nó là `${key}`, `${key:default}`, nested key hay nested default?

**Bước 2 — Xác nhận source.** Property nằm ở JVM system properties, OS env, `@PropertySource`, Boot Config Data hay custom MapPropertySource?

**Bước 3 — Duyệt theo precedence.** Một key trùng ở nguồn ưu tiên cao có thể thắng giá trị bạn nhìn thấy trong file khác.

**Bước 4 — Kiểm tra inner placeholders trước.** Với `${service.url.${app.env}}`, xem `app.env` resolve thành gì rồi mới tìm `service.url.<env>`.

**Bước 5 — Xác định default có thực sự được dùng không.** Property chứa chuỗi rỗng hoặc value không hợp lệ có thể vẫn thắng default.

**Bước 6 — Kiểm tra strictness.** Đường gọi có dùng `resolvePlaceholders`, `resolveRequiredPlaceholders`, hoặc `PropertySourcesPlaceholderConfigurer` không?

**Bước 7 — Kiểm tra conversion.** Sau khi resolve, giá trị có convert được sang `int`/`boolean`/`URI` không?

**Bước 8 — Kiểm tra vòng lặp.** Chuỗi properties có một key dẫn trở lại chính nó hay không?

**Bước 9 — Kiểm tra thời điểm inject.** Bean đã được tạo trước khi bạn thêm source/đổi thứ tự chưa?

**Bước 10 — Test một context nhỏ.** Dùng MapPropertySource với một bộ keys tối thiểu, assert kết quả mà không cần network hoặc database.

### Log chỉ những dữ liệu an toàn

Khi cần debug, in **tên PropertySources**, không dump nguyên source map. Nếu cần xác định source thắng:

~~~java
for (PropertySource<?> source :
        configurableEnvironment.getPropertySources()) {
    Object value = source.getProperty("app.mode");
    if (value != null) {
        System.out.println("winner=" + source.getName());
        break;
    }
}
~~~

Đây là snippet minh họa cần imports `PropertySource` và biến `configurableEnvironment` kiểu `ConfigurableEnvironment`. Không in secrets hoặc raw values nhạy cảm.

## 27. Các lỗi phổ biến

| Biểu hiện | Nguyên nhân có thể | Hướng xử lý |
| --- | --- | --- |
| `${app.name}` vẫn nguyên trong output | Dùng lenient resolver hoặc string chưa được Spring xử lý | Dùng required resolution / đúng injection infrastructure |
| Application không fail khi thiếu `@Value` key | Embedded resolver mặc định có thể giữ marker chưa resolve | Đăng ký static `PropertySourcesPlaceholderConfigurer` |
| Có default nhưng vẫn lỗi NumberFormat/Conversion | Key tồn tại với value sai type | Validate và sửa nguồn |
| Default không được dùng khi value rỗng | Chuỗi rỗng khác null | Xử lý empty string tường minh |
| Nested key không tìm thấy | Inner placeholder resolve thành suffix sai | In/kiểm tra inner value và PropertySources precedence |
| Placeholder trong file tham chiếu vòng | Circular reference giữa keys | Sửa graph cấu hình |
| `resolveRequiredPlaceholders` ném lỗi | Thiếu một nested hoặc outer key không có default | Bổ sung source/default có chủ đích |
| `getProperty` trả chuỗi đã thay nhưng raw source chưa thay | Environment có bước resolution, source là raw data | Dùng API đúng mục tiêu |
| `@Value` trên object dùng `new` không có value | Object không do Spring quản lý | Constructor DI hoặc đăng ký bean |
| Thay source sau refresh nhưng field cũ | Constructor/field injection là snapshot | Thiết kế reload/rebinding rõ ràng |
| Default URL có dấu `:` bị nghi sai | Parser dùng separator đầu tiên phân tách key/default | Test URL template; đổi separator chỉ khi cần |
| Đổi separator làm annotation cũ lỗi | `setValueSeparator` thay convention mặc định | Giữ syntax thống nhất, test integrations |
| `${token}` bị resolve dù muốn literal | Chuỗi đi qua resolver không mong muốn | Chọn cách escape/không resolve phù hợp |
| Một key khác được chọn dù file có đúng value | Source ưu tiên cao chứa key trùng | Xem Environment source order |
| Không có property từ `application.properties` trong Core | Core không auto-load Boot file conventions | Dùng `@PropertySource` hoặc custom source |
| Placeholder được resolve nhưng client không hoạt động | Giá trị đúng cú pháp nhưng sai nghiệp vụ | Validate endpoint/timeout, không chỉ parse |

## 28. Kiểm thử placeholder bằng Spring Context

Ví dụ một integration test:

~~~java
try (var context = new AnnotationConfigApplicationContext()) {
    context.getEnvironment().getPropertySources().addFirst(
        new MapPropertySource("test", Map.of(
            "app.env", "test",
            "service.url.test", "https://test.example.test"
        ))
    );

    context.refresh();

    String resolved =
        context.getEnvironment().resolveRequiredPlaceholders(
            "${service.url.${app.env}}");

    if (!"https://test.example.test".equals(resolved)) {
        throw new AssertionError("Nested key not resolved");
    }
}
~~~

Đây là một snippet Java 17 cần imports `AnnotationConfigApplicationContext`, `MapPropertySource` và `Map`. Nó dùng Environment trực tiếp, không tạo application configuration class.

### Những test cases nên có

- Key tồn tại: `${a:b}` trả value của `a`.
- Key thiếu: `${a:b}` trả default `b`.
- Key thiếu không có default: lenient giữ marker, strict báo lỗi.
- Nested default: `${a:${b:c}}` giải quyết theo thứ tự.
- Nested key: `${url.${stage}}` xây đúng key.
- Nested value: property `url=http://${host}:${port}` resolve đúng.
- Trùng key ở hai sources: source ưu tiên cao thắng.
- Value rỗng: default không tự thay rỗng.
- Value sai type: conversion báo lỗi thay vì fallback.
- Circular reference: không tạo recursion vô hạn.
- Required configuration khi thiếu: application startup fail-fast với configurer.

Không chỉ assert string sau resolution; nếu bean có `@Value` và conversion, viết thêm integration test tạo **bean thực tế**.

## 29. Lựa chọn giải pháp theo tình huống

| Nhu cầu | Cách dùng phù hợp | Vì sao |
| --- | --- | --- |
| Một scalar có fallback | `@Value("${key:default}")` | Gọn, dễ hiểu |
| Một scalar bắt buộc | `@Value("${key}")` + strict configurer | Fail-fast khi config thiếu |
| Tạo chuỗi nhiều placeholders | `Environment.resolveRequiredPlaceholders` | Kiểm soát strictness rõ |
| Chuỗi template có thể giữ markers chưa resolve | `resolvePlaceholders` | Lenient contract |
| Dùng một key khác làm tên key | `${prefix.${env}}` | Nested key |
| Fallback nhiều cấp | `${a:${b:c}}` | Nested default |
| Chỉ đọc một property từ Java | `Environment.getProperty(key, default)` | Không cần template |
| Nhóm cấu hình nhiều fields | Typed configuration object, Boot `@ConfigurationProperties` nếu có | Rõ kiểu/validation |
| Chọn bean implementation theo môi trường | `@Profile` / `@Conditional` | Kiểm soát bean graph |
| Runtime config thay đổi liên tục | Service đọc nguồn được quản lý, explicit refresh | `@Value` không tự cập nhật |
| Giá trị cần bảo mật | Secret source + strict validation + masking | Tránh default yếu/lộ bí mật |

Không phải mọi fallback đều nên nhồi vào một expression dài. Nếu logic cấu hình có nhiều nhánh, Java code rõ ràng thường dễ kiểm thử hơn.

## 30. Câu hỏi tự kiểm tra

**Câu 1.** `${app.timeout:30}` nhận gì khi key không tồn tại?

**Đáp án:** String `"30"` ở bước placeholder resolution, rồi có thể được convert thành số 30 nếu injection target là `int`/`Integer`.

**Câu 2.** Key `app.timeout` tồn tại với value `""`, default 30 có được dùng không?

**Đáp án:** Thường không. Chuỗi rỗng là một value không null, nên khác việc key không tồn tại.

**Câu 3.** `${primary:${secondary:offline}}` dùng secondary khi nào?

**Đáp án:** Khi primary không resolve được; nếu secondary cũng thiếu thì dùng literal `offline`.

**Câu 4.** `${service.url.${env}}` có nghĩa gì?

**Đáp án:** Resolve inner `env` để xây tên key ngoài, rồi tra giá trị của `service.url.<env>`.

**Câu 5.** `resolvePlaceholders("${missing}")` có chắc chắn ném exception không?

**Đáp án:** Không. Contract lenient thường giữ nguyên marker chưa resolve, trừ các tình huống lỗi khác như vòng tham chiếu.

**Câu 6.** `resolveRequiredPlaceholders("${missing}")` làm gì khi không có default?

**Đáp án:** Ném `IllegalArgumentException` vì không resolve được placeholder bắt buộc.

**Câu 7.** `@Value("${missing}")` trong Core context luôn khiến app fail không?

**Đáp án:** Không nên mặc định như vậy vì embedded resolver mặc định có thể giữ nguyên unresolved placeholder. Dùng `PropertySourcesPlaceholderConfigurer` với strict handling để fail-fast.

**Câu 8.** Có thể đặt placeholder trong property value khác không?

**Đáp án:** Có. Ví dụ `base-url=http://${host}:${port}` được resolver xử lý khi đọc property thích hợp.

**Câu 9.** Có nên dùng `${feature.enabled:true}` thay cho kiểm tra quyền truy cập người dùng không?

**Đáp án:** Không. Đó là startup/config property, không phải authorization policy.

**Câu 10.** Cùng key xuất hiện ở hai PropertySources, default trong placeholder có được ưu tiên hơn source thấp không?

**Đáp án:** Không. Resolver tìm theo precedence qua các sources; default chỉ dùng khi không tìm được giá trị phù hợp.

**Câu 11.** Vì sao `@Bean` trả `PropertySourcesPlaceholderConfigurer` nên `static`?

**Đáp án:** Vì nó là BeanFactoryPostProcessor được cần sớm; static method giúp Spring không phải khởi tạo configuration class trước khi các post-processors khác có cơ hội chạy.

**Câu 12.** `${...}` và `#{...}` giống nhau ở đâu?

**Đáp án:** Chúng đều có thể xuất hiện trong cấu hình/injection Spring, nhưng khác cơ chế: `${...}` resolve property; `#{...}` đánh giá SpEL.

## 31. Checklist áp dụng trong code review

- [ ] Placeholder cần đọc value có thật hay có default hợp lý?
- [ ] Có phân biệt default value và empty string chưa?
- [ ] Có dùng đúng `${key:default}` thay vì nhầm với SpEL không?
- [ ] Các nested key/default có thứ tự dễ hiểu và dấu ngoặc đúng không?
- [ ] Có kiểm tra precedence của các PropertySources khi trùng key không?
- [ ] Có phân biệt `resolvePlaceholders` lenient và `resolveRequiredPlaceholders` strict không?
- [ ] Nếu dùng `@Value` cho config bắt buộc, đã cấu hình fail-fast hợp lý chưa?
- [ ] `PropertySourcesPlaceholderConfigurer` có được đăng ký đủ sớm bằng `static @Bean` không?
- [ ] Property values lồng nhau có tạo circular references không?
- [ ] Các giá trị đã resolve có được convert/validate đúng kiểu và business constraints không?
- [ ] Có secrets nào bị dùng default yếu hoặc ghi ra logs không?
- [ ] Bean có đang phụ thuộc vào một giá trị snapshot nhưng kỳ vọng live reload không?
- [ ] Có đoạn code tạo object bằng `new` rồi trông chờ `@Value` tự chạy không?
- [ ] Có kiểm thử placeholder literal/escaping trên đúng Spring version đang chạy không?
- [ ] Nested expression có đang quá phức tạp và cần chuyển sang typed configuration không?

## 32. Tóm tắt cần nhớ

- `${key}` là **property placeholder**: Spring tìm value của key trong `Environment`/`PropertySources`.
- `${key:default}` cung cấp fallback khi key không có giá trị phù hợp; default không ghi đè value thực.
- **Nested default:** `${primary:${secondary:offline}}`.
- **Nested key:** `${service.url.${env}}`.
- **Nested property value:** một property có thể chứa những placeholders tham chiếu properties khác.
- `resolvePlaceholders` là lenient; `resolveRequiredPlaceholders` strict khi thiếu placeholder.
- `@Value` hỗ trợ placeholder injection, nhưng default embedded resolver trong Spring Core không nhất thiết fail khi unresolved; `PropertySourcesPlaceholderConfigurer` giúp áp dụng strict policy cho bean config.
- `PropertySourcesPlaceholderConfigurer` nên đăng ký bằng **static `@Bean`** trong Java configuration.
- `${...}` **khác** SpEL `#{...}`.
- Empty string, sai kiểu, vòng tham chiếu, precedence và injection snapshot là những nguồn lỗi phổ biến.
- Đừng dùng placeholder thay validation, quản lý secrets hoặc logic feature flag runtime.

## 33. Tài liệu Spring chính thức

- [Spring Framework — Using @Value](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/value-annotations.html): placeholder injection, default embedded resolver, strict resolution bằng `PropertySourcesPlaceholderConfigurer` và static `@Bean`.
- [Spring Framework — Environment Abstraction](https://docs.spring.io/spring-framework/reference/core/beans/environment.html): `Environment`, `PropertySource`, property resolution và `@PropertySource`.
- [Spring Framework 6.1 — PropertyResolver Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/env/PropertyResolver.html): `getProperty`, `getRequiredProperty`, `resolvePlaceholders` và `resolveRequiredPlaceholders`.
- [Spring Framework 6.1 — AbstractPropertyResolver Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/env/AbstractPropertyResolver.html): default separator `:`, nested placeholders, cấu hình prefix/suffix và ignore-unresolvable-nested policy.
- [Spring Framework 6.1 — PropertySourcesPropertyResolver Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/env/PropertySourcesPropertyResolver.html): resolver dựa trên các sources theo precedence.
- [Spring Framework 6.1 — PropertySourcesPlaceholderConfigurer Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/support/PropertySourcesPlaceholderConfigurer.html): xử lý bean definition placeholders trên Environment.
- [Spring Framework — Container Extension Points](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html): property placeholder configurer như một BeanFactoryPostProcessor và các cấu hình nâng cao.
- [Spring Boot — Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html): cơ chế Boot bổ sung về Config Data và type-safe properties.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="values-and-expressions-003">Vai trò PropertySourcesPlaceholderConfigurer</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò của PropertySourcesPlaceholderConfigurer trong Spring Core Container

## 1. Vấn đề cần giải quyết

Trong ứng dụng Spring, cấu hình như địa chỉ dịch vụ, timeout, số lần thử lại và tên môi trường nên nằm ngoài mã nghiệp vụ. Một bean có thể nhận giá trị qua placeholder:

~~~java
@Value("${service.timeout-ms:3000}")
private int timeoutMillis;
~~~

Spring phải biết **nguồn key–value nào được phép tìm kiếm**, thứ tự ưu tiên của các nguồn, việc xử lý key không tồn tại và thời điểm thay chuỗi `${...}` bằng giá trị thực.

`PropertySourcesPlaceholderConfigurer` (viết tắt **PSPC**) giải quyết nhu cầu này. Nó nằm ở package `org.springframework.context.support`, kế thừa `PlaceholderConfigurerSupport` và triển khai `BeanFactoryPostProcessor`. Theo Javadoc Spring Framework 6.2, nó chuyên **resolve placeholder trong bean definition property values và các `@Value` injection points, dựa trên `Environment` và `PropertySources` được cấu hình**.

Đừng nhầm PSPC với một object đọc cấu hình trực tiếp mỗi khi request đến. Vai trò chính của nó là **chuẩn bị metadata và embedded value resolver để Spring tạo bean với giá trị đúng**.

### Phân biệt các thành phần

| Thành phần | Vai trò |
| --- | --- |
| `Environment` | API đọc properties và kiểm tra active/default profiles |
| `PropertySource` | Một nguồn key–value có tên, như JVM property, OS environment, map hoặc resource |
| `MutablePropertySources` | Danh sách sources theo thứ tự ưu tiên có thể sửa |
| `PropertyResolver` | Logic tìm key, resolve placeholders và convert values |
| `PropertySourcesPlaceholderConfigurer` | BeanFactoryPostProcessor thay placeholders trong bean definitions và đăng ký embedded value resolver |
| `@PropertySource` | Annotation thêm một nguồn properties vào Environment trong quá trình đọc config |
| `@Value` | Injection point chỉ định literal, placeholder hoặc SpEL expression |
| `@ConfigurationProperties` | Binder typed configuration của Spring Boot, không phải PSPC |

Một service chỉ cần một giá trị cấu hình nên nhận nó như constructor argument hoặc typed configuration object. Không cần inject toàn bộ `ApplicationContext` để làm service locator.

## 2. PSPC hoạt động vào giai đoạn nào?

~~~text
ApplicationContext chưa refresh
   |
   +-- Chuẩn bị Environment + PropertySources
   |       ├-- systemProperties
   |       ├-- systemEnvironment
   |       ├-- @PropertySource resources
   |       └-- custom MapPropertySource
   |
   +-- Đăng ký và parse BeanDefinitions / @Configuration
   |
   ▼
ApplicationContext.refresh()
   |
   +-- Invoke BeanFactoryPostProcessors
   |       |
   |       └-- PropertySourcesPlaceholderConfigurer
   |             ├-- lấy Environment sources
   |             ├-- gộp local properties do PSPC sở hữu
   |             ├-- chọn thứ tự ưu tiên
   |             ├-- sửa placeholder trong bean definitions
   |             └-- đăng ký embedded value resolver
   |
   +-- Register BeanPostProcessors
   |
   +-- Instantiate regular beans
           ├-- Constructor/factory method arguments
           ├-- Resolve @Value theo embedded resolver
           ├-- Convert String -> int, boolean, ...
           └-- Chạy init callbacks
~~~

Đây là sơ đồ khái niệm, không liệt kê mọi bước `refresh()`. PSPC chạy trong **metadata phase** do `BeanFactoryPostProcessor` điều phối. Nó không phải một `BeanPostProcessor` để thay object sau initialization.

Hệ quả:

- Nó xử lý placeholder trong cấu hình được Spring quản lý, không dò mọi chuỗi Java trong bộ nhớ.
- Nếu code tự tạo `String s = "${service.url}"` rồi đưa vào một thuật toán Java bên ngoài Spring, PSPC không tự thay giá trị đó.
- Những bean đã được tạo trước khi processor xử lý metadata có thể không nhận được cách giải quyết property mong muốn.
- Một update sau refresh không tự chạy lại mọi callback BFPP và constructor của singleton đã tạo.

### Vì sao không dùng BeanPostProcessor cho việc này?

Giá trị constructor và bean property values phải được biết **trước hoặc trong quá trình tạo object**. `BeanPostProcessor` được gọi khi instance đã tồn tại, do đó không phải extension point thích hợp để resolve những giá trị quyết định constructor. `BeanFactoryPostProcessor` mới là công cụ xử lý bean definition metadata đủ sớm.

## 3. PSPC lấy properties từ đâu?

Theo Spring 6.2 Javadoc, configurer có thể sử dụng:

1. **Environment property sources**: thường gồm JVM system properties, OS environment variables và những sources thêm vào qua `ConfigurableEnvironment.getPropertySources()`.
2. **Local properties** của chính configurer: được thêm qua `setProperties(Properties)`, `setLocations(Resource...)` và những methods inherited từ `PropertiesLoaderSupport`.
3. **Một bộ PropertySources tùy chỉnh** qua `setPropertySources(PropertySources)`, với semantics đặc biệt là thay các nguồn mặc định.

Ví dụ thêm source vào Environment trước khi refresh:

~~~java
var context = new AnnotationConfigApplicationContext();
context.getEnvironment().getPropertySources().addFirst(
        new MapPropertySource(
                "appOverrides",
                Map.<String, Object>of(
                        "service.name", "checkout",
                        "service.timeout-ms", "1500")));
context.register(AppConfig.class);
context.refresh();
~~~

Đoạn này là snippet; cần import `AnnotationConfigApplicationContext`, `MapPropertySource`, `Map` và khai báo `AppConfig`. `addFirst` đặt nguồn mới lên đầu danh sách: nếu các sources khác có cùng key, source này thường thắng.

Ví dụ đưa local properties trực tiếp vào PSPC:

~~~java
@Bean
static PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
    Properties local = new Properties();
    local.setProperty("service.name", "local");
    local.setProperty("service.timeout-ms", "3000");

    var p = new PropertySourcesPlaceholderConfigurer();
    p.setProperties(local);
    return p;
}
~~~

Đây là snippet cho một `@Configuration` class hợp lệ. Cần import `@Bean`, `Properties` và PSPC.

**Điểm rất quan trọng:** `local` ở trên được đưa vào **PSPC**, không có nghĩa những key đó đã được thêm vào chính `Environment` chung. `Environment.getProperty(...)` và `@Value` được PSPC xử lý có thể cho kết quả khác nếu cấu hình precedence khác nhau.

### Property file do configurer nạp

~~~java
@Bean
static PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
    var p = new PropertySourcesPlaceholderConfigurer();
    p.setLocation(new ClassPathResource("service.properties"));
    return p;
}
~~~

Đây là snippet; `src/main/resources/service.properties` phải tồn tại và cần import `ClassPathResource`. `setLocation(...)` tạo nhóm local properties của PSPC; khác với `@PropertySource` vốn bổ sung resource vào `Environment` khi configuration class được xử lý.

## 4. Thứ tự ưu tiên: localOverride

PSPC kế thừa flag `localOverride`. Theo Javadoc chính thức:

- Mặc định `setLocalOverride(false)`: **Environment được xét trước**, local PSPC properties được tìm sau, làm fallback.
- `setLocalOverride(true)`: local PSPC properties được xét trước Environment.
- `setPropertySources(...)`: thay tập sources dùng cho configurer, bỏ qua tập environment/local mặc định nếu không tự đưa chúng vào danh sách.

Giả sử Environment và local có các keys:

~~~properties
# Environment
course.label=ENV
course.limit=23
course.region=VN
~~~

~~~properties
# PSPC local
course.label=LOCAL
course.limit=17
~~~

| PSPC setting | Giá trị inject của `course.label` | `course.limit` | `course.region` |
| --- | --- | --- | --- |
| `localOverride=false` | ENV | 23 | VN |
| `localOverride=true` | LOCAL | 17 | VN |

**Không được suy ra** từ `localOverride=true` rằng:

~~~java
context.getEnvironment().getProperty("course.label");
~~~

phải trả `LOCAL`. PSPC local properties không tự được đưa vào Environment. Nếu Environment chỉ có `ENV` thì lời gọi ấy vẫn có thể trả `ENV`, trong khi:

~~~java
@Value("${course.label}")
String name;
~~~

được resolve thành `LOCAL` bởi configurer.

### Nên thiết kế cấu hình nhất quán thế nào?

Nếu app có nhiều cách đọc cùng một key, hãy cân nhắc để nguồn cấu hình trong **Environment chung**, sắp precedence một lần rồi inject các value objects. Dùng local properties của PSPC khi có lý do rõ về module/legacy integration hoặc kiểm soát riêng. Việc Environment và `@Value` nhìn thấy khác nhau không nhất thiết là bug của Spring; đó có thể là hậu quả chính xác của cấu hình.

## 5. Strict placeholder resolution và key bị thiếu

Spring Framework có default embedded value resolver trong `ApplicationContext`, ngay cả khi ứng dụng không khai báo explicit PSPC. Theo Spring Reference, default resolver có thể **lenient**: nếu `${catalog.name}` không resolve được, chuỗi placeholder có thể được giữ nguyên làm giá trị cho injection point phù hợp.

Nếu cần strict control, đăng ký explicit PSPC:

~~~java
@Configuration(proxyBeanMethods = false)
public class StrictConfig {

    @Bean
    public static PropertySourcesPlaceholderConfigurer placeholders() {
        return new PropertySourcesPlaceholderConfigurer();
    }
}
~~~

Spring Reference yêu cầu method `@Bean` tạo PSPC trong JavaConfig nên là **static**. Với cấu hình thông thường, `ignoreUnresolvablePlaceholders=false` khiến unresolved placeholders được xem là lỗi thay vì để nguyên đi tiếp.

Ví dụ:

~~~java
@Bean
OutboundClient outboundClient(
        @Value("${client.endpoint}") String endpoint) {
    return new OutboundClient(endpoint);
}
~~~

Đây là snippet, cần type `OutboundClient`. Nếu không có `client.endpoint` trong các nguồn configurer dùng và không có default value, Spring có thể không tạo được bean, với nguyên nhân gốc liên quan placeholder chưa resolve.

### Có default value thì sao?

~~~java
@Value("${client.retry-count:3}")
int retryCount;
~~~

Nếu key vắng mặt, resolver có thể dùng `3`. Strict PSPC không tự biến tất cả keys thành required; việc có default chính là cho phép fallback theo thiết kế.

### setIgnoreUnresolvablePlaceholders(true)

PSPC hỗ trợ:

~~~java
pspc.setIgnoreUnresolvablePlaceholders(true);
~~~

Khi đó nó có thể để nguyên placeholder chưa giải quyết. **Đây không đảm bảo bean tạo thành công**: nếu field cần `int` mà chuỗi còn là `${client.port}`, conversion có thể thất bại. Với key quan trọng, nên cung cấp rõ nguồn hoặc validation thay vì che lỗi bằng ignore.

### Phân biệt lỗi missing key và lỗi conversion

- Missing key: không resolve được `${client.timeout}`.
- Giá trị sai kiểu: key có giá trị `"abc"` nhưng injection point là `int`.
- Lỗi resource: file properties không tồn tại hoặc không parse được.
- Lỗi SpEL: expression `#{...}` sai cú pháp hoặc thiếu dependency.

Những lỗi này cần hướng sửa khác nhau. Không kết luận mọi `BeanCreationException` khi có `@Value` đều là do PSPC.

## 6. @Value, @PropertySource, Environment và PSPC khác nhau

### @Value là vị trí tiêu thụ

~~~java
@Component
class PaymentAdapter {
    private final String endpoint;

    PaymentAdapter(@Value("${payments.endpoint}") String endpoint) {
        this.endpoint = endpoint;
    }
}
~~~

Đây là snippet; cần import `@Component` và `@Value`. Annotation chỉ ra input cần inject, không tự tạo một properties file hoặc quản lý source precedence. Hạ tầng annotation injection, embedded value resolving và type conversion tham gia để truyền giá trị vào constructor.

### @PropertySource bổ sung nguồn

~~~java
@Configuration
@PropertySource("classpath:settings.properties")
class SettingsConfig {
}
~~~

Spring xử lý annotation này trong configuration parsing và thêm resource dưới dạng PropertySource vào Environment. PSPC có thể nhìn thấy nguồn đó khi chạy BFPP. Còn `setLocation(...)` trực tiếp trên PSPC tạo local properties chỉ cho resolver của chính configurer.

Điều này ảnh hưởng `@PropertySource` có path placeholder:

~~~java
@PropertySource("classpath:settings/${region:default}/app.properties")
~~~

`region` phải sẵn có trong Environment **khi configuration parser xử lý annotation**. Local PSPC properties chưa chạy ở pha BFPP không tự làm resolver ở pha parse tìm được giá trị đường dẫn. Nếu key chọn path rất sớm, thêm nó vào Environment trước registration/refresh.

### Environment là API đọc

~~~java
String url = environment.getProperty("payments.endpoint");
~~~

Một service nhận `Environment` có thể lookup ở thời điểm gọi method. PSPC chủ yếu quyết định giá trị trong pipeline bean metadata/embedded values. Trộn hai cơ chế cần hiểu nguồn dữ liệu và thời điểm đọc.

### SpEL không phải PSPC

~~~java
@Value("#{2 * 3}")
private int calculated;
~~~

Kết quả `6` đến từ **Spring Expression Language**, không phải property placeholder lookup. Spring có thể phối hợp placeholder `${...}` và SpEL `#{...}` trong một injection expression, nhưng PSPC **không phải** SpEL evaluator.

Ví dụ minh họa:

~~~java
@Value("#{'${feature.regions:vn,sg}'.split(',')}")
private String[] regions;
~~~

Một expression phức tạp vẫn phải qua cả resolver tương ứng; đừng đặt logic nghiệp vụ dài trong expression khi constructor nhận typed configuration object sẽ rõ ràng hơn.

## 7. Lý do @Bean tạo PSPC nên là static

PSPC là một `BeanFactoryPostProcessor`. Nó phải được tạo và gọi sớm trong `refresh()`, **trước** regular bean instantiation.

Cấu hình khuyến nghị:

~~~java
@Configuration(proxyBeanMethods = false)
class AppConfig {

    @Bean
    static PropertySourcesPlaceholderConfigurer pspc() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    RetryPolicy retryPolicy(
            @Value("${retry.max-attempts:3}") int maxAttempts) {
        return new RetryPolicy(maxAttempts);
    }
}
~~~

Đây là snippet, cần type `RetryPolicy` và imports. Static factory method không buộc container instantiate `AppConfig` sớm chỉ để tạo BFPP. Nếu method BFPP không static, Spring có thể phải tạo configuration instance trước khi những BPP hỗ trợ `@Autowired` hoặc `@PostConstruct` của config class được áp dụng đầy đủ; Spring có thể phát warning.

**Return type nên rõ**: `PropertySourcesPlaceholderConfigurer` hoặc ít nhất một BFPP type nhận diện được. Đừng khai báo return type `Object` chỉ để tạo processor nếu container cần tự phát hiện type của nó trước instantiation.

### Có phải lúc nào cũng cần explicit PSPC?

**Không.** Với `ApplicationContext` chuẩn, default embedded value resolver thường đủ cho `@Value("${key:default}")` đọc Environment. Explicit PSPC được khuyến nghị nếu cần strict missing-key behavior, local properties riêng, custom placeholder syntax hoặc control đặc thù cho legacy BeanDefinition XML.

Spring Boot có cơ chế externalized configuration và hạ tầng placeholder riêng, nhiều ứng dụng Boot không cần thêm custom PSPC chỉ để `@Value` chạy. Thêm trùng các configurers có thể làm precedence khó suy đoán.

## 8. Ví dụ Java 17 hoàn chỉnh: localOverride làm @Value khác Environment

Ví dụ chạy bằng **Java 17**, **Maven** và **Spring Framework 6.2.19**. Hai `AnnotationConfigApplicationContext` độc lập được tạo với **cùng Environment source** nhưng hai chế độ PSPC:

- `EnvironmentFirst`: `setLocalOverride(false)`, Environment thắng.
- `LocalFirst`: `setLocalOverride(true)`, local PSPC properties thắng với những keys trùng.
- `Environment.getProperty` vẫn chỉ thấy nguồn của Environment.
- `getAppliedPropertySources()` xác nhận local properties thực sự tham gia.

### Cấu trúc tệp

~~~text
pspc-demo/
├── pom.xml
└── src/main/java/example/PlaceholderConfigurerDemo.java
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>pspc-demo</artifactId>
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
~~~

### src/main/java/example/PlaceholderConfigurerDemo.java

~~~java
package example;

import java.util.Map;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;

public final class PlaceholderConfigurerDemo {

    public static void main(String[] args) {
        run("env-first", EnvironmentFirst.class);
        run("local-first", LocalFirst.class);
    }

    private static void run(String label, Class<?> configClass) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource(
                            "courseEnvironment",
                            Map.<String, Object>of(
                                    "course.demo.label", "ENV",
                                    "course.demo.limit", "23",
                                    "course.demo.region", "VN")));

            context.register(CommonBeans.class, configClass);
            context.refresh();

            Snapshot bean = context.getBean(Snapshot.class);
            PropertySourcesPlaceholderConfigurer pspc =
                    context.getBean(PropertySourcesPlaceholderConfigurer.class);

            System.out.println(label + ": injected="
                    + bean.label() + "/" + bean.limit()
                    + "/" + bean.region()
                    + "; environment="
                    + context.getEnvironment()
                            .getProperty("course.demo.label")
                    + "; local-source="
                    + pspc.getAppliedPropertySources().contains(
                            PropertySourcesPlaceholderConfigurer
                                    .LOCAL_PROPERTIES_PROPERTY_SOURCE_NAME));
        }
    }

    private static PropertySourcesPlaceholderConfigurer create(
            boolean localFirst) {

        Properties local = new Properties();
        local.setProperty("course.demo.label", "LOCAL");
        local.setProperty("course.demo.limit", "17");

        PropertySourcesPlaceholderConfigurer pspc =
                new PropertySourcesPlaceholderConfigurer();
        pspc.setProperties(local);
        pspc.setLocalOverride(localFirst);
        return pspc;
    }

    public record Snapshot(String label, int limit, String region) {
    }

    @Configuration(proxyBeanMethods = false)
    public static class CommonBeans {
        @Bean
        public Snapshot snapshot(
                @Value("${course.demo.label}") String label,
                @Value("${course.demo.limit}") int limit,
                @Value("${course.demo.region:GLOBAL}") String region) {
            return new Snapshot(label, limit, region);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class EnvironmentFirst {
        @Bean
        public static PropertySourcesPlaceholderConfigurer placeholders() {
            return create(false);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class LocalFirst {
        @Bean
        public static PropertySourcesPlaceholderConfigurer placeholders() {
            return create(true);
        }
    }
}
~~~

### Chạy

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.PlaceholderConfigurerDemo
~~~

Stdout do chính các lệnh `System.out.println` tạo ra, không tính các log tùy môi trường:

~~~text
env-first: injected=ENV/23/VN; environment=ENV; local-source=true
local-first: injected=LOCAL/17/VN; environment=ENV; local-source=true
~~~

### Phân tích kết quả

**Lượt env-first:**

1. Context được tạo bằng constructor không tham số, chưa refresh.
2. `courseEnvironment` cung cấp `label=ENV`, `limit=23`, `region=VN`.
3. `EnvironmentFirst.placeholders()` cung cấp PSPC local `label=LOCAL` và `limit=17` nhưng giữ `localOverride=false`.
4. Environment đứng trước local nên `@Value` trong factory method `snapshot(...)` được resolve thành `ENV`, `23`, `VN`.
5. Type conversion chuyển String `"23"` thành `int 23`.
6. `getAppliedPropertySources().contains("localProperties")` là true vì resolver đã nhận local source, dù local không thắng.

**Lượt local-first:**

1. Tạo context riêng, không tái sử dụng beans của lượt trước.
2. `LocalFirst.placeholders()` bật `setLocalOverride(true)`, local sources đứng trước.
3. `@Value` của hai keys bị trùng lấy `LOCAL` và `17`; key `region` chỉ có ở Environment nên lấy `VN`.
4. `Environment.getProperty("course.demo.label")` vẫn trả `ENV`, vì local PSPC data không được tự chèn vào Environment.
5. Hai ngữ cảnh đóng sau khi in xong; chương trình không dùng network hoặc resource bên ngoài.

**Điều ví dụ không chứng minh:** PSPC là live rebinding engine, hoặc việc thay localOverride sau refresh khiến singleton tự tạo lại. Hai contexts cố tình được khởi tạo riêng để kiểm tra bootstrap behavior với hai policy.

## 9. setPropertySources có semantics khác addFirst

So sánh:

~~~java
environment.getPropertySources().addFirst(customSource);
~~~

và:

~~~java
configurer.setPropertySources(customPropertySources);
~~~

Thao tác thứ nhất **bổ sung/đổi ưu tiên nguồn trong Environment chung**. Thao tác thứ hai **chỉ định bộ sources riêng mà PSPC sẽ sử dụng**, và theo Javadoc PSPC, nếu đã gọi `setPropertySources` thì **không tự thêm Environment sources hoặc local properties** nữa.

Ví dụ:

~~~java
MutablePropertySources onlyThese = new MutablePropertySources();
onlyThese.addLast(new MapPropertySource(
        "explicit",
        Map.<String, Object>of("client.region", "VN")));

PropertySourcesPlaceholderConfigurer pspc =
        new PropertySourcesPlaceholderConfigurer();
pspc.setPropertySources(onlyThese);
~~~

Đây là snippet cần imports `Map`, `MutablePropertySources`, `MapPropertySource` và PSPC. Nếu `client.timeout` chỉ tồn tại trong Environment mặc định mà không có trong `onlyThese`, resolver của configurer sẽ không tự đọc giá trị ấy.

**Khuyến nghị:** dùng `setPropertySources` khi muốn **cô lập hoặc kiểm soát tuyệt đối** những nguồn configurer sử dụng. Nếu chỉ muốn thêm một override vào application config, `Environment.getPropertySources().addFirst` thường dễ hiểu và nhất quán hơn.

## 10. getAppliedPropertySources giúp debug

Method:

~~~java
PropertySources sources = configurer.getAppliedPropertySources();
~~~

cho biết những sources đã được PSPC áp dụng trong quá trình post-processing. Nếu gọi quá sớm, trước khi `postProcessBeanFactory(...)`, method có thể ném `IllegalStateException`.

Spring cung cấp các tên wrapper phổ biến:

- `PropertySourcesPlaceholderConfigurer.ENVIRONMENT_PROPERTIES_PROPERTY_SOURCE_NAME` = `"environmentProperties"`.
- `PropertySourcesPlaceholderConfigurer.LOCAL_PROPERTIES_PROPERTY_SOURCE_NAME` = `"localProperties"`.

Wrapper names trong PSPC không nhất thiết xuất hiện như hai entries cùng tên trong `context.getEnvironment().getPropertySources()`. Nếu ứng dụng cần debug precedence, hãy inspect đúng resolver chain.

**Bảo mật:** không dump toàn bộ values của các property sources vào log production. Những nguồn ấy có thể chứa token, password, thông tin kết nối. Có thể in source names và sự hiện diện của key, nhưng redact sensitive values.

## 11. XML bean definitions và PSPC

Trong cấu hình XML, placeholders có thể nằm ở constructor arguments và property values:

~~~xml
<bean id="remoteClient"
      class="example.RemoteClient"
      destroy-method="close">
    <constructor-arg value="${remote.endpoint}"/>
    <property name="timeoutMillis"
              value="${remote.timeout-ms:2000}"/>
</bean>
~~~

Đây là XML snippet, không phải XML document standalone; class phải có constructor/setter/destroy method tương ứng.

Với XML namespace cấu hình đầy đủ, có thể sử dụng:

~~~xml
<context:property-placeholder
        location="classpath:remote.properties"/>
~~~

Spring xử lý property placeholders ở metadata phase thông qua hạ tầng placeholder configurer. Trong Spring hiện đại, `PropertySourcesPlaceholderConfigurer` là lựa chọn phù hợp với Environment/PropertySources. Tệp XML hoàn chỉnh cần đúng namespace và schemaLocation; snippets chỉ minh họa hai thành phần.

Điểm chính: PSPC không chỉ giúp `@Value` field injection; nó còn resolve placeholders trong bean definitions đọc từ XML.

## 12. Điều gì xảy ra khi thêm nguồn quá muộn?

Một luồng điển hình:

~~~text
T0   Đăng ký Environment source có timeout=1000
T1   refresh(), PSPC xử lý metadata và singleton được tạo
T2   Singleton nhận timeout=1000
T3   Thêm PropertySource ưu tiên cao có timeout=2000
T4   Environment.getProperty("timeout") -> có thể nhận 2000
T5   Singleton field/constructor value -> vẫn 1000
~~~

PSPC **không tự re-run BeanFactoryPostProcessor phase** sau khi mọi singleton đã tạo, và `@Value` không tự cập nhật một field khi Environment thay đổi.

Đối với bean `prototype` hoặc lazy bean chưa tạo, việc lấy giá trị mới phụ thuộc đường resolution: nếu value được giải quyết tại lúc tạo bean từ resolver đang nhìn thấy Environment live, có thể thấy source mới; nếu placeholder đã được thay vào BeanDefinition trước đó thì không tự đảo ngược. Cần test chính trường hợp application đang sử dụng.

### Profile/condition thường được xét sớm hơn PSPC

Một số `@Profile`, `@Conditional` hoặc path của `@PropertySource` được đánh giá trong **configuration parsing**, có thể trước BFPP. Vì vậy local properties chỉ có trong PSPC **không phải cơ chế đáng tin** để quyết định mọi condition đã được xử lý trước nó.

Nếu key quyết định cấu trúc bean graph, thêm nó vào `Environment` **trước khi register/scan và refresh** các cấu hình liên quan.

## 13. Tùy chỉnh cú pháp placeholder

PSPC kế thừa nhiều options từ `PlaceholderConfigurerSupport` và `PropertiesLoaderSupport`:

| API | Công dụng |
| --- | --- |
| `setPlaceholderPrefix(...)` | Thay ký hiệu bắt đầu placeholder |
| `setPlaceholderSuffix(...)` | Thay ký hiệu kết thúc |
| `setValueSeparator(...)` | Thay dấu phân cách key/default; thường là dấu hai chấm |
| `setEscapeCharacter(...)` | Chọn ký tự escape trong phiên bản hỗ trợ |
| `setTrimValues(...)` | Trim whitespace của values |
| `setNullValue(...)` | Marker đặc biệt đại diện null |
| `setIgnoreUnresolvablePlaceholders(...)` | Cho phép/không cho phép unresolved placeholders |
| `setLocalOverride(...)` | Chọn local source priority |
| `setProperties(...)` | Cung cấp local key–value |
| `setLocations(...)` | Nạp local files |
| `setPropertySources(...)` | Thay bộ sources configurer sử dụng |
| `getAppliedPropertySources()` | Quan sát sources sau xử lý |

Không đổi syntax `${...}` tùy tiện nếu app không có xung đột thực tế. Thay prefix/suffix làm code khó tương thích ví dụ Spring chuẩn và có thể ảnh hưởng những expressions đã tồn tại.

Type conversion là một giai đoạn khác: PSPC có thể resolve `"42"` từ key, nhưng nếu target type `int` mà value là `"forty-two"` thì bean vẫn thất bại tại conversion. Khi debug, phân biệt lỗi **lookup**, **placeholder parsing** và **conversion**.

## 14. ApplicationContext khác BeanFactory thuần

`ApplicationContext` tự phát hiện các `BeanFactoryPostProcessor` beans trong `refresh()` khi chúng được đăng ký đúng lúc. Một `static @Bean` PSPC có thể được Spring tạo và invoke tự động.

`DefaultListableBeanFactory` thuần thì khác: chỉ đăng ký BeanDefinition cho PSPC **không tự chạy** processor. Nếu tự bootstrap:

~~~java
DefaultListableBeanFactory factory = new DefaultListableBeanFactory();

RootBeanDefinition bean =
        new RootBeanDefinition(RemoteClient.class);
bean.getPropertyValues().add("endpoint", "${remote.endpoint}");
factory.registerBeanDefinition("client", bean);

Properties props = new Properties();
props.setProperty("remote.endpoint", "https://example.invalid");

PropertySourcesPlaceholderConfigurer pspc =
        new PropertySourcesPlaceholderConfigurer();
pspc.setProperties(props);
pspc.postProcessBeanFactory(factory);

// Tạo bean sau khi metadata đã được xử lý.
RemoteClient client = factory.getBean("client", RemoteClient.class);
~~~

Đây là snippet, cần class `RemoteClient` có setter `setEndpoint(...)` và imports phù hợp. Với plain BeanFactory, những processors phục vụ `@Autowired`/`@Value` annotation cũng cần được thiết lập riêng nếu muốn annotation-driven injection tương đương ApplicationContext; PSPC **không thay thế** toàn bộ annotation infrastructure.

### Programmatic PSPC trong ConfigurableApplicationContext

Có thể gọi `context.addBeanFactoryPostProcessor(pspc)` trước `refresh()`. Nhưng PSPC được thêm như **instance programmatic** không mặc nhiên đi qua managed-bean Aware callbacks, nên không giả định nó đã nhận `Environment` giống một processor bean do context tạo. Hãy truyền `Environment`/`PropertySources` thích hợp một cách tường minh, hoặc ưu tiên JavaConfig `static @Bean` khi có thể.

## 15. PSPC trong Spring Boot và typed config

Spring Boot bổ sung nhiều nguồn externalized configuration: `application.properties`/`application.yaml`, environment variables, command-line arguments, config data locations, test overrides và các nguồn do extension bổ sung. Boot cũng thường cấu hình hạ tầng placeholder sẵn.

Đừng mặc định:

- Mỗi Boot application cần tự thêm một PSPC bean.
- `@Value` có cùng đầy đủ relaxed binding semantics như `@ConfigurationProperties`.
- Thay Environment ở runtime tự rebind mọi `@ConfigurationProperties` objects.
- `@PropertySource` thích hợp để chỉnh những settings Boot đã đọc rất sớm như logging hoặc `spring.main.*`.

Với nhiều keys liên quan, `@ConfigurationProperties` tạo typed configuration object với group structure/validation thường dễ maintain hơn hàng chục `@Value`. PSPC vẫn có vai trò cho placeholder resolution, đặc biệt khi khai báo bean definitions hoặc cần policy resolution rõ ràng.

## 16. Các lỗi phổ biến và chẩn đoán

| Hiện tượng | Nguyên nhân có thể | Hướng xử lý |
| --- | --- | --- |
| `@Value` nhận nguyên chuỗi `${key}` | Default lenient resolver/không có key | Cung cấp key hoặc explicit strict PSPC |
| Context fail khi thiếu một key | Strict PSPC và không có default | Thêm source, default hợp lệ hoặc validation rõ |
| PSPC local không thấy trong Environment | Local properties chỉ thuộc PSPC | Thêm chúng vào Environment nếu cần lookup chung |
| `localOverride=true` mà Environment vẫn trả giá trị cũ | Flag không thay Environment | Phân biệt hai resolver paths |
| Giá trị @Value khác Environment | Local priority/custom PropertySources | Inspect `getAppliedPropertySources` |
| setPropertySources làm key hệ thống biến mất | Nó bỏ Environment/local mặc định | Gộp sources tường minh hoặc bỏ override |
| PSPC configurer bị tạo quá sớm | `@Bean` không static | Dùng `static @Bean` và ít dependency |
| `getAppliedPropertySources()` ném exception | Gọi trước post-processing | Gọi sau refresh |
| BeanFactory thuần không xử lý placeholder | Chưa invoke BFPP | Gọi `postProcessBeanFactory` trước getBean |
| Property file không được load | Resource path sai hoặc thiếu | Kiểm tra classpath và API đọc file |
| Key có nhưng int injection fail | String không chuyển được sang int | Kiểm tra value/conversion |
| @PropertySource path không tìm thấy key PSPC local | Path parsed trước BFPP | Đưa key vào Environment đủ sớm |
| Bean không thay đổi sau khi addFirst source mới | Instance đã tạo, giá trị đã inject | Thiết kế runtime config/recreate bean rõ |
| Hai PSPC gây precedence lạ | Processors xử lý metadata nhiều lần | Một owner configurer, sắp nguồn tập trung |
| Chạy được khi test nhưng deploy sai | OS env/JVM/Boot precedence khác | Test context/source order thực |
| Lộ token trong log | Dump toàn bộ PropertySources | Redact giá trị nhạy cảm |
| Placeholder mặc định che lỗi cấu hình | Dùng `:default` cho required property | Fail fast với strict policy |
| Service inject ApplicationContext chỉ để đọc 1 key | Coupling container không cần thiết | Constructor value/typed config object |

### Một quy trình debug ngắn

1. Ghi lại **key**, injection point và kiểu dữ liệu.
2. Xác định property được đọc qua **@Value/PSPC** hay **Environment.getProperty**.
3. In **tên** PropertySources theo thứ tự; không in secrets.
4. Kiểm tra local properties, `localOverride` và custom `setPropertySources`.
5. Xác nhận configurer có được phát hiện/đăng ký trước bean creation.
6. Với key trong `@PropertySource` path hoặc `@Conditional`, xác minh source có sẵn trước configuration parsing.
7. Kiểm tra default syntax, missing key và conversion riêng.
8. Tái hiện bằng integration test với context tối giản và các nguồn nhân tạo.

## 17. Kiểm thử những hành vi cần bảo đảm

### 17.1. Test precedence

Tạo hai ApplicationContexts với cùng Environment map và PSPC local values, khác `setLocalOverride`. Xác minh kết quả `@Value` và `Environment.getProperty`. Ví dụ mục 8 là một bộ kiểm chứng trực tiếp.

### 17.2. Test strict missing key

Với explicit PSPC strict, khai báo bean cần `@Value("${required.key}")` nhưng không cung cấp key. Assert startup/bean creation thất bại, sau đó bổ sung key **trước refresh** để kiểm chứng nhánh thành công. Root exception wrapper có thể thay đổi theo cấu hình, nên ưu tiên assert root cause có nghĩa.

### 17.3. Test local source không thay Environment

Gọi `getAppliedPropertySources()` sau refresh xác minh có local source; kiểm tra Environment direct lookup vẫn chỉ trả sources của nó. Đây là regression test hữu ích khi team thay `localOverride`.

### 17.4. Test file loading

Đặt `test.properties` trong test classpath và cấu hình `setLocation(new ClassPathResource("test.properties"))`. Assert key được resolve và kiểm tra trường hợp resource thiếu. Một test chỉ assert context starts có thể không bắt đúng key bị fallback ngoài ý muốn.

### 17.5. Test type conversion

Sử dụng `@Value("${pool.max-connections}") int count` với value `"12"` rồi giá trị sai `"twelve"` ở test khác. Việc key tồn tại chưa đảm bảo type hợp lệ.

### 17.6. Test timing

Tạo eager singleton với `@Value`, thêm source sau refresh và xác nhận singleton không tự đổi dù Environment đọc giá trị mới. Nếu app dùng lazy/prototype, thêm test riêng theo đúng resolver path để không suy ra từ singleton.

## 18. Câu hỏi tự kiểm tra

**Câu 1.** PSPC thuộc loại BeanFactoryPostProcessor hay BeanPostProcessor?

**Đáp án:** BeanFactoryPostProcessor; nó xử lý placeholder trong metadata và embedded values trước khi regular beans được tạo.

**Câu 2.** Environment có `mode=ENV` và PSPC local có `mode=LOCAL`. Với mặc định `localOverride=false`, `@Value("${mode}")` thường nhận gì?

**Đáp án:** ENV, bởi Environment được xét trước local properties.

**Câu 3.** Nếu gọi `setLocalOverride(true)`, Environment.getProperty("mode") có chắc trả LOCAL không?

**Đáp án:** Không. Local properties thuộc resolver PSPC, không tự được thêm vào Environment.

**Câu 4.** Khi nào nên đăng ký explicit PSPC?

**Đáp án:** Khi cần strict missing-key handling, local file/properties, custom syntax, precedence hoặc behavior resolver đặc thù. Với ApplicationContext chuẩn, default embedded resolver có thể đủ cho @Value đơn giản.

**Câu 5.** Vì sao `@Bean PropertySourcesPlaceholderConfigurer` nên static?

**Đáp án:** Processor phải được tạo sớm ở BFPP phase; static factory method tránh tạo @Configuration instance trước khi đủ lifecycle processors có sẵn.

**Câu 6.** `setPropertySources(custom)` có tự merge Environment mặc định không?

**Đáp án:** Không. Theo PSPC Javadoc, custom PropertySources thay những nguồn mặc định của resolver configurer; phải gộp rõ nếu muốn.

**Câu 7.** `getAppliedPropertySources()` gọi trước refresh có an toàn không?

**Đáp án:** Chưa chắc. Trước khi PSPC chạy postProcessBeanFactory, method có thể ném IllegalStateException.

**Câu 8.** Một key nằm trong PSPC local properties có được dùng để chọn path trong @PropertySource khi parse config không?

**Đáp án:** Không nên kỳ vọng. Configuration parsing có thể xảy ra trước BFPP; key chọn resource path nên có trong Environment sớm.

**Câu 9.** Có thể dùng PSPC để tự đổi tất cả singleton khi Environment bị mutate sau refresh không?

**Đáp án:** Không. PSPC là startup metadata/embedded value resolver, không phải automatic live rebinding.

**Câu 10.** PSPC có tự evaluate `#{1 + 2}` không?

**Đáp án:** Không. Cú pháp ấy thuộc SpEL. PSPC xử lý placeholders `${...}`, còn Spring expression infrastructure đánh giá SpEL trong những injection paths phù hợp.

**Câu 11.** Local properties từ `setLocation` và properties được thêm bởi @PropertySource có phạm vi y hệt nhau không?

**Đáp án:** Không. `setLocation` nạp local properties cho configurer; `@PropertySource` bổ sung PropertySource vào Environment khi cấu hình được xử lý.

**Câu 12.** Trong DefaultListableBeanFactory thuần, chỉ register BeanDefinition PSPC có đủ để nó tự được invoke không?

**Đáp án:** Không. Plain BeanFactory không tự orchestration BFPP như ApplicationContext; phải invoke đúng phase hoặc dùng ApplicationContext.

## 19. Checklist triển khai

- Có phân biệt `Environment`, `PropertySources`, PSPC và `@Value`?
- PSPC chỉ được dùng khi thật sự cần strict/configurer-specific behavior?
- Những sources phục vụ bootstrap được thêm **trước refresh**?
- `@Bean` trả PSPC có `static` và return type rõ?
- `localOverride` đã được xác định theo precedence có chủ đích?
- Nếu dùng `setPropertySources`, có biết Environment/local mặc định bị loại?
- Nếu cần Environment và @Value nhất quán, đã đặt sources chung vào Environment?
- Có default values phù hợp và strict handling với keys bắt buộc?
- Có phân biệt lỗi placeholder, file resource, type conversion và SpEL?
- Có tránh gọi getBean nghiệp vụ từ metadata post-processor?
- Có tránh đăng ký trùng nhiều PSPC với policies mâu thuẫn?
- Có hiểu @PropertySource path và @Profile/@Conditional có thể được xử lý trước BFPP?
- Có hiểu sửa Environment sau refresh không tự re-inject singleton?
- Với plain BeanFactory, đã gọi BFPP đúng lúc và thiết lập annotation infrastructure cần thiết?
- Có bảo đảm resource files tồn tại trong classpath deployment?
- Có viết test precedence, strict-missing, conversion, source timing và local-vs-Environment?
- Log debug có tránh in password/token hoặc dump toàn bộ sources?
- Với Spring Boot, có tận dụng external config và typed binder thay vì tự viết nhiều @Value rải rác?

## 20. Tài liệu chính thức

- [Spring Framework 6.2 — PropertySourcesPlaceholderConfigurer Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/PropertySourcesPlaceholderConfigurer.html)
- [Spring Framework — Using @Value](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/value-annotations.html)
- [Spring Framework — Environment Abstraction](https://docs.spring.io/spring-framework/reference/core/beans/environment.html)
- [Spring Framework — Container Extension Points](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html)
- [Spring Framework — PlaceholderConfigurerSupport](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/config/PlaceholderConfigurerSupport.html)
- [Spring Framework — MutablePropertySources](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/MutablePropertySources.html)
- [Spring Framework — BeanFactoryPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/config/BeanFactoryPostProcessor.html)
- [Spring Boot — Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="values-and-expressions-004">Phát hiện placeholder chưa được giải quyết</a>

<details>
<summary>Xem chi tiết</summary>

# Phát hiện placeholder chưa được giải quyết trong Spring Framework

## 1. Bài toán và mục tiêu

Khi ứng dụng Spring chuyển cấu hình ra ngoài mã nguồn, ta thường dùng **property placeholder** như `${service.host}`, `${service.port:8080}` hoặc `${database.url}`. Spring tra từng key trong các nguồn cấu hình và thay placeholder bằng value. Nếu nguồn thiếu hoặc quá trình resolution chưa diễn ra, ứng dụng có thể vẫn cầm một chuỗi chứa nguyên cú pháp placeholder:

~~~text
Mong đợi: https://api.internal:8443/v1
Thực tế : https://${service.host}:${service.port}/v1
~~~

Với URL, tên file, định danh tài nguyên hoặc configuration quan trọng, việc dùng một chuỗi chưa được resolve có thể gây lỗi chỉ lộ ra sau khi ứng dụng khởi động. Trong khi đó, có trường hợp **cố ý** để một template chưa đầy đủ cho hệ thống downstream. Spring cung cấp cả hai lựa chọn: **lenient** (giữ token chưa tìm thấy) và **strict** (báo lỗi).

Bài học thuộc nhóm **Giá trị và biểu thức** của Spring Framework Core Container. Sau bài này, bạn có thể:

- Hiểu placeholder, property key, property value và nguồn cấu hình là bốn khái niệm khác nhau.
- Dùng `Environment.resolvePlaceholders(String)` và `resolveRequiredPlaceholders(String)` đúng hợp đồng.
- Phân biệt `getProperty`, `getRequiredProperty` và các exception.
- Xử lý fallback `${key:default}`, value rỗng, nested placeholders và cyclic references.
- Hiểu `@Value` có thể lenient theo cấu hình mặc định và bật strict bằng `PropertySourcesPlaceholderConfigurer`.
- Cấu hình `@PropertySource` và xử lý thứ tự nạp file/precedence.
- Thực hành với Java 17 + Maven + Spring Framework 6.2, gồm stdout rõ ràng.
- Kiểm thử, chẩn đoán và fail fast mà không làm lộ thông tin nhạy cảm.

## 2. Placeholder được giải quyết từ đâu?

`Environment` thuộc contract `PropertyResolver` và `ConfigurableEnvironment` giữ danh sách `PropertySources` theo thứ tự ưu tiên. Chẳng hạn source ưu tiên cao đặt `service.host=api.internal`, source thấp đặt `service.port=8443`; template `https://${service.host}:${service.port}/v1` được thay thành `https://api.internal:8443/v1`.

`getProperty("service.host")` tra một key; `resolveRequiredPlaceholders("https://${service.host}/v1")` thay một template. Các nguồn có thể đến từ JVM properties, OS environment, `MapPropertySource`, `@PropertySource` hoặc Spring Boot Config Data trong Boot. Bài placeholder giải thích sâu precedence/nested forms; trọng tâm hiện tại là phát hiện key **không resolve được**.

## 3. Hai chế độ resolvePlaceholders và resolveRequiredPlaceholders

### 3.1. API lenient

~~~java
String result = environment.resolvePlaceholders(
        "endpoint=https://${service.host}:${service.port}/v1");
~~~

Đây là snippet trong method có biến `Environment environment`.

Hợp đồng `PropertyResolver.resolvePlaceholders`: thay những placeholders phân giải được; **placeholder không phân giải được, không có default, sẽ được giữ nguyên**.

Ví dụ khi `service.host` thiếu nhưng `service.port=8443` có mặt:

~~~text
endpoint=https://${service.host}:8443/v1
~~~

API trả về string, không ném exception chỉ vì một placeholder thiếu. Hành vi này có ích nếu template được xử lý tiếp ở bước khác; **không phù hợp nếu đây là endpoint bắt buộc hoàn chỉnh**.

### 3.2. API strict

~~~java
String result = environment.resolveRequiredPlaceholders(
        "endpoint=https://${service.host}:${service.port}/v1");
~~~

Khi một key bắt buộc trong template không tìm thấy, hàm ném **`IllegalArgumentException`**. Nếu có đủ keys, kết quả được thay tương tự `resolvePlaceholders`.

Với một endpoint phải sẵn sàng ở startup, strict API diễn đạt requirement chính xác hơn việc lấy lenient result rồi hy vọng nội dung đúng.

### 3.3. Bảng so sánh

| API | Khi tìm thấy mọi key | Khi thiếu key không có default |
| --- | --- | --- |
| `resolvePlaceholders(text)` | Trả string đã thay | Giữ token chưa tìm thấy |
| `resolveRequiredPlaceholders(text)` | Trả string đã thay | Ném `IllegalArgumentException` |
| `getProperty(key)` | Trả property value | Trả `null` |
| `getRequiredProperty(key)` | Trả property value | Ném `IllegalStateException` |

Hai lỗi *required* khác nhau: strict template resolution dùng `IllegalArgumentException`, còn required **property-key lookup** dùng `IllegalStateException`.

Đầu vào `null` cho hai API resolve text cũng không hợp lệ và có thể gây `IllegalArgumentException`. Khi debug, cần đọc exception message/cause để phân biệt **input null** với **placeholder thiếu**.

### 3.4. Khi nào chọn API nào?

Dùng `resolvePlaceholders` nếu downstream được phép nhận các placeholders còn lại, ví dụ một template chưa hoàn thiện có chủ đích. Dùng `resolveRequiredPlaceholders` khi output phải là một string đã resolve đủ tất cả placeholders ở giai đoạn hiện tại. Dùng `getRequiredProperty` khi thứ cần kiểm tra là **một key cấu hình bắt buộc**, không phải chuỗi chứa nhiều placeholders.

## 4. Giá trị mặc định trong placeholder

`${service.port:8080}` chỉ dùng 8080 khi resolver không tìm thấy value. Một strict template `https://${service.host:localhost}:${service.port:8080}/v1` vẫn hoàn thành nếu cả hai keys thiếu vì mỗi key có fallback.

Cần kiểm tra riêng ba tình huống: **key thiếu** (có thể dùng default), **key tồn tại nhưng rỗng** (thường không dùng default, URL có thể thành `https://:8080/v1`), và **key có text sai kiểu** như `service.port=oops` (placeholder resolve được nhưng conversion sang số có thể thất bại). Đừng đặt fallback giả cho endpoint production bắt buộc. Validation cuối cùng cần kiểm tra nonblank, port range và URI.

## 5. `getProperty` và nested placeholders

`Environment.getProperty("service.url")` có thể resolve value lồng placeholder:

```properties
service.host=api.internal
service.port=8443
service.url=https://${service.host}:${service.port}/v1
```

`containsProperty("service.url")` hoặc key ngoài có tồn tại **không bảo đảm** inner keys đầy đủ. Resolver có thể ném lỗi theo strict nested policy. Vòng `alpha=${beta}`, `beta=${alpha}` là circular reference, khác missing key và phải sửa graph cấu hình.

Để debug, kiểm tra key ngoài, các inner key, rồi cuối cùng tính hợp lệ của domain value. Bài placeholder trước đó đã giải thích nested keys, defaults và escaping.

## 6. @Value đôi khi không báo lỗi ngay khi thiếu property

`@Value` hỗ trợ placeholder:

~~~java
@Component
public class GatewaySettings {
    private final String host;

    public GatewaySettings(@Value("${gateway.host}") String host) {
        this.host = host;
    }
}
~~~

Đây là snippet một Spring-managed component; cần import `@Component`, `@Value` và scan/register đúng class.

Theo Spring Framework Reference, có một **embedded value resolver lenient** mặc định trong các cấu hình thích hợp. Nó cố resolve placeholder; nếu không tìm được, **chuỗi placeholder chưa giải quyết có thể được inject như string nguyên vẹn**.

Ví dụ:

~~~text
gateway.host không tồn tại
   ↓
@Value("${gateway.host}") là String
   ↓
host có thể trở thành literal "${gateway.host}"
   ↓
HTTP client sau đó dùng hostname sai
~~~

Đây là lý do một ứng dụng có thể refresh thành công nhưng đến lần request đầu mới phát hiện URL/host chưa được resolve.

### Không kết luận mọi @Value thiếu đều không lỗi

Hành vi còn phụ thuộc:

- Embedded resolver/configurer thực tế được đăng ký.
- `@Value` injection target là String, int, URI hay một type khác.
- Bean được tạo eager hay `@Lazy`.
- Đã có `PropertySourcesPlaceholderConfigurer` strict chưa.
- Có placeholder default không.

Ví dụ khi `@Value("${port}")` inject vào `int` và placeholder còn nguyên text, **conversion** có thể báo lỗi ngay. Không thể chỉ nhìn annotation mà đoán mọi trường hợp đều lenient.

## 7. Bật strict @Value với PropertySourcesPlaceholderConfigurer

Spring Reference hướng dẫn dùng `PropertySourcesPlaceholderConfigurer` để fail khi placeholder không resolve được:

~~~java
@Configuration
public class StrictPlaceholderConfig {

    @Bean
    public static PropertySourcesPlaceholderConfigurer
            placeholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }
}
~~~

Đây là snippet cấu hình, cần các imports Spring tương ứng.

`PropertySourcesPlaceholderConfigurer` là **BeanFactoryPostProcessor**: nó xử lý bean definition placeholders sớm và cài embedded resolver. `PlaceholderConfigurerSupport.setIgnoreUnresolvablePlaceholders(...)` mặc định là **false** — unresolved placeholder sẽ gây exception khi đi qua strict processing.

Nếu muốn tường minh:

~~~java
@Bean
public static PropertySourcesPlaceholderConfigurer
        strictPlaceholderConfigurer() {

    PropertySourcesPlaceholderConfigurer configurer =
            new PropertySourcesPlaceholderConfigurer();

    configurer.setIgnoreUnresolvablePlaceholders(false);
    return configurer;
}
~~~

### Vì sao factory method phải static?

Container cần tạo BeanFactoryPostProcessor trước khi ordinary beans/annotation injection hoàn tất. `static @Bean` cho phép Spring tạo configurer mà không cần instantiate `@Configuration` class quá sớm. Đây là khuyến nghị Spring cho infrastructure post-processors, không có nghĩa mọi `@Bean` nghiệp vụ phải static.

### Thử missing @Value với strict configurer

~~~java
@Configuration(proxyBeanMethods = false)
class StrictFailureConfig {

    @Bean
    static PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    String requiredClientId(
            @Value("${required.client.id}") String clientId) {
        return clientId;
    }
}
~~~

Đây là **snippet thử nghiệm trong một context riêng**, không phải cấu hình chạy thành công của ví dụ mục 9. Nếu không có `required.client.id` và Spring cố tạo `requiredClientId`, strict resolution khiến bean creation thất bại. Exception cấp ngoài có thể là `BeanCreationException` hoặc `UnsatisfiedDependencyException`, còn root cause thường cho biết không resolve được placeholder.

Nếu bean lazy, một số lỗi injection chỉ được phát hiện khi `getBean()` thực sự tạo bean. **Strict configurer không có nghĩa mọi @Value trên tất cả lazy beans đều được đánh giá đồng thời tại startup**. Muốn fail fast cho toàn bộ mandatory configuration, chủ động tạo và validate một settings bean trong startup phase.

### Tại sao không chỉ setIgnoreUnresolvablePlaceholders(true)?

~~~java
configurer.setIgnoreUnresolvablePlaceholders(true);
~~~

Thiết lập này cho phép giữ placeholder chưa resolve ở một số bước xử lý, thường để processor khác xử lý tiếp. Nó **không** bổ sung missing configuration; chỉ chuyển trách nhiệm phát hiện lỗi sang nơi khác. Không chọn ignore=true chỉ để làm biến mất exception khi deployment thiếu host/token.

### Một hay nhiều configurers?

Nếu nhiều placeholder configurers cùng prefix/suffix, thứ tự và nguồn dữ liệu có thể làm behavior khó chẩn đoán. Spring Reference khuyến nghị tổ chức property sources nhất quán và thường gom vào một configurer thay vì tạo nhiều processors trùng cú pháp khi không cần thiết.

## 8. Ví dụ Java 17 / Spring 6.2 có thể chạy độc lập

Chương trình sẽ chứng minh:

1. `resolvePlaceholders` và `resolveRequiredPlaceholders` cùng thành công khi đủ key.
2. Khi thiếu, lenient giữ token; strict ném `IllegalArgumentException`.
3. Default value `${missing:guest}` hoạt động.
4. Blank property không bị coi là missing.
5. `getProperty` có thể resolve một nested URL.
6. `getRequiredProperty` thiếu ném `IllegalStateException`.
7. `@Value` lấy hai settings hợp lệ trong context có strict `PropertySourcesPlaceholderConfigurer`.

Không cần Spring Boot, database hoặc web server.

### 8.1. Thư mục

~~~text
placeholder-detection/
├── pom.xml
└── src/main/java/demo/
    └── PlaceholderDetectionDemo.java
~~~

### 8.2. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>placeholder-detection</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.9</version>
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
~~~

### 8.3. src/main/java/demo/PlaceholderDetectionDemo.java

~~~java
package demo;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

public class PlaceholderDetectionDemo {

    private static final String HOST = "example240.host";
    private static final String PORT = "example240.port";
    private static final String URL = "example240.url";
    private static final String MISSING =
            "example240.unset.unique.key";

    private static Map<String, Object> demoProperties() {
        return Map.<String, Object>of(
                HOST, "api.internal",
                PORT, "8443",
                URL, "https://${example240.host}:${example240.port}/v1",
                "example240.blank", "");
    }

    public static void main(String[] args) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("examples", demoProperties()));

        String template = "https://${example240.host}:${example240.port}/v1";
        String absentTemplate = "user=${" + MISSING + "}";

        System.out.println("lenientKnown="
                + environment.resolvePlaceholders(template));
        System.out.println("strictKnown="
                + environment.resolveRequiredPlaceholders(template));
        System.out.println("lenientMissing="
                + environment.resolvePlaceholders(absentTemplate));
        System.out.println("fallback="
                + environment.resolveRequiredPlaceholders(
                        "user=${" + MISSING + ":guest}"));
        System.out.println("blankFallback=["
                + environment.resolveRequiredPlaceholders(
                        "${example240.blank:other}")
                + "]");
        System.out.println("nestedUrl="
                + environment.getProperty(URL));
        System.out.println("containsMissing="
                + environment.containsProperty(MISSING));
        System.out.println("getMissing="
                + environment.getProperty(MISSING));

        try {
            environment.resolveRequiredPlaceholders(absentTemplate);
        } catch (IllegalArgumentException ex) {
            System.out.println("strictMissing="
                    + ex.getClass().getSimpleName());
        }

        try {
            environment.getRequiredProperty(MISSING);
        } catch (IllegalStateException ex) {
            System.out.println("requiredKeyMissing="
                    + ex.getClass().getSimpleName());
        }

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("examples", demoProperties()));

            context.register(StrictConfig.class);
            context.refresh();

            System.out.println("injected="
                    + context.getBean(Client.class));
        }
    }

    public record Client(String host, int port) {
    }

    @Configuration(proxyBeanMethods = false)
    public static class StrictConfig {

        @Bean
        public static PropertySourcesPlaceholderConfigurer
                placeholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        public Client client(
                @Value("${example240.host}") String host,
                @Value("${example240.port}") int port) {
            return new Client(host, port);
        }
    }
}
~~~

Toàn bộ Java code nằm trong **một file**; nested classes/record công khai đủ để Spring gọi factory methods. `AnnotationConfigApplicationContext` được tạo chưa refresh, thêm `MapPropertySource` trước `register` và `refresh`.

### 8.4. Chạy

Từ thư mục `placeholder-detection`:

~~~bash
java -version
mvn -version
mvn -q compile exec:java -Dexec.mainClass=demo.PlaceholderDetectionDemo
~~~

Stdout của **chương trình**, không tính logging Maven/Spring, và giả sử JVM/OS không cố ý cung cấp key `example240.unset.unique.key`:

~~~text
lenientKnown=https://api.internal:8443/v1
strictKnown=https://api.internal:8443/v1
lenientMissing=user=${example240.unset.unique.key}
fallback=user=guest
blankFallback=[]
nestedUrl=https://api.internal:8443/v1
containsMissing=false
getMissing=null
strictMissing=IllegalArgumentException
requiredKeyMissing=IllegalStateException
injected=Client[host=api.internal, port=8443]
~~~

### 8.5. Tại sao những dòng này xuất hiện?

| Dòng | Giải thích |
| --- | --- |
| `lenientKnown=...` | Tìm đủ hai keys, thay hết |
| `strictKnown=...` | Strict cũng thành công khi đủ keys |
| `lenientMissing=user=${...}` | Key vắng, lenient để nguyên placeholder |
| `fallback=user=guest` | Default `guest` đáp ứng cả strict resolver |
| `blankFallback=[]` | Value rỗng khác null, không kích hoạt fallback |
| `nestedUrl=...` | Đọc `example240.url` và resolve placeholders bên trong property value |
| `containsMissing=false` | Không có value cho key |
| `getMissing=null` | Optional key lookup không ném |
| `strictMissing=IllegalArgumentException` | Strict **template** resolution phát hiện thiếu |
| `requiredKeyMissing=IllegalStateException` | Required **key** lookup phát hiện thiếu |
| `injected=Client[...]` | `@Value` inject thành công, port chuyển sang `int` |

### 8.6. Các thí nghiệm có thể làm ngay

**Thí nghiệm A: Thêm missing key.** Trong `demoProperties()` thêm entry `MISSING, "alice"`. Lenient không còn giữ token, fallback bị bỏ qua, `containsMissing` thành true và hai khối catch không in exception.

**Thí nghiệm B: Sai kiểu port.** Đổi `"8443"` thành `"not-a-number"`. URL string vẫn có thể được resolve thành `...:not-a-number/v1`, nhưng `@Value` inject vào `int` khiến bean creation thất bại ở conversion phase.

**Thí nghiệm C: Bỏ HOST.** Với không có source nào khác chứa host, lenient template giữ token, strict template ném exception trước khi chạy những dòng sau. Nếu chỉ thay ở Spring context, strict `@Value` cũng có thể làm context startup thất bại.

**Thí nghiệm D: Bỏ PORT nhưng dùng default.** Thay expression thành `${example240.port:8080}`. Strict template vẫn hoạt động nếu thiếu property, kết quả dùng `8080`. Nếu port hiện diện với blank string, fallback không tự áp dụng.

**Thí nghiệm E: Đổi @Value thành required lookup.** Trong method tạo `Client`, inject `Environment` và gọi `getRequiredProperty("example240.host")`, `getRequiredProperty("example240.port", Integer.class)`. Cách này cũng phù hợp cho settings bắt buộc, nhưng missing exception thuộc hợp đồng `IllegalStateException`.

### 8.7. Lưu ý tính tái lập

`MapPropertySource` được thêm bằng `addFirst`, nên các keys có mặt trong map thắng JVM/OS sources trùng tên. Riêng `MISSING` được đặt tên riêng; nếu người chạy **cố ý** thêm system property/env var cùng tên, kết quả missing sẽ thay đổi đúng theo resolver semantics. Trong integration test cần tính xác định tuyệt đối, có thể dựng một resolver chỉ gồm sources của test hoặc mock environment.

## 9. Khi muốn fail fast với @Value thiếu

Tạo một configuration riêng có:

~~~java
@Configuration(proxyBeanMethods = false)
class MissingValueConfig {

    @Bean
    static PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    String requiredClientId(
            @Value("${required.client.id}") String value) {
        return value;
    }
}
~~~

Đây là **snippet cho test context riêng**, không phải phần cần đưa vào chương trình thành công ở mục 8. Nếu key `required.client.id` không có, Spring có thể thất bại khi tạo bean `requiredClientId`. Exception được bọc qua `BeanCreationException`/`UnsatisfiedDependencyException` tùy con đường dependency injection; hãy đọc **root cause** để xác định unresolved placeholder.

**Nếu bean là lazy** và chưa được tạo, lỗi injection-level có thể chỉ lộ ra khi gọi `getBean()`. Không đánh đồng “refresh xong” với “mọi @Value ở tất cả lazy beans đã được resolve”.

### Khi nào fail fast là hợp lý?

- URL của một dependency bắt buộc.
- Tên database/schema hoặc port phải xác định.
- Key tham chiếu secret bắt buộc cho service.
- Identity/region bắt buộc để ứng dụng vận hành đúng.

Với optional feature flag, dùng default rõ ràng hoặc không đăng ký bean khi thiếu configuration, miễn policy được thể hiện tường minh.

## 10. Kiểm tra một tập required properties

Nếu ứng dụng đòi nhiều keys, `ConfigurablePropertyResolver` hỗ trợ:

~~~java
ConfigurableEnvironment environment = context.getEnvironment();

environment.setRequiredProperties(
        "service.host", "service.port", "service.region");

environment.validateRequiredProperties();
~~~

Đây là snippet trong method có biến `context` đúng type. `validateRequiredProperties()` có thể ném `MissingRequiredPropertiesException` (thuộc họ `IllegalStateException`) khi thiếu required key.

Tuy nhiên kiểm tra này tập trung vào **presence**, không phải toàn bộ string đã được validate. Ví dụ:

~~~java
String host = environment.getRequiredProperty("service.host");
Integer port = environment.getRequiredProperty(
        "service.port", Integer.class);

if (host.isBlank()) {
    throw new IllegalArgumentException("service.host must not be blank");
}
if (port < 1 || port > 65535) {
    throw new IllegalArgumentException("service.port out of range");
}
~~~

Đây là snippet validation. Một settings object hoàn chỉnh nên kiểm tra:

1. Key có không (presence).
2. Có placeholder bên trong chưa resolve không (resolution).
3. Value có chuyển được sang type đúng không (conversion).
4. Value có thỏa domain constraints không (validation).
5. Bean có được tạo vào đúng lifecycle phase không (wiring).

## 11. Nested placeholders, escaping và literal templates

Spring xử lý placeholder với prefix `${`, suffix `}`, value separator thường là `:`. Có thể tùy chỉnh bằng `PropertySourcesPlaceholderConfigurer`:

~~~java
configurer.setPlaceholderPrefix("${");
configurer.setPlaceholderSuffix("}");
configurer.setValueSeparator(":");
configurer.setIgnoreUnresolvablePlaceholders(false);
~~~

Đây là snippet khi đã có một biến `configurer`. Trong đa số ứng dụng không cần viết lại các defaults.

Spring 6.2 còn có hỗ trợ tùy chỉnh **escape character** ở `PlaceholderConfigurerSupport` và resolver. Một placeholder có thể được cố ý escape để giữ literal text. Nếu debug thấy `${token}` còn nguyên, trước khi kết luận thiếu property hãy kiểm tra **đó có phải token được escape/cố ý giữ lại hay không**.

### Đừng dò thiếu placeholder chỉ bằng String.contains

~~~java
if (value.contains("${")) {
    throw new IllegalStateException("Possible unresolved placeholder");
}
~~~

Đây chỉ là heuristic:

- Có thể báo lỗi khi value cố ý chứa literal `${...}`.
- Có thể bỏ sót prefix/suffix đã được tùy biến.
- Không hiểu escaping hoặc parser semantics.
- Không phân biệt placeholder thiếu với blank value hay sai kiểu.

Nếu **muốn bắt buộc toàn bộ template được thay**, dùng `resolveRequiredPlaceholders`. Nếu muốn biết **một key bắt buộc có value**, dùng `getRequiredProperty`. Dò pattern chỉ hữu ích như phép kiểm tra bổ sung có giới hạn, không thay thế chính sách resolution.

### Cyclic references

~~~properties
a=${b}
b=${a}
~~~

đặt một vòng tham chiếu. Resolver có thể phát hiện cycle và ném exception; đây không đơn thuần là một key vắng. Phải phá vòng trong nguồn cấu hình. Không kỳ vọng `setIgnoreUnresolvablePlaceholders(true)` khiến vòng tham chiếu trở thành một cấu hình hợp lệ.

## 12. Phân biệt placeholder và Spring Expression Language

Hai cú pháp khác nhau:

~~~java
@Value("${service.port:8080}")
int configuredPort;

@Value("#{2 * 3}")
int computedValue;
~~~

Câu đầu dùng property placeholder, câu sau là **SpEL** (Spring Expression Language). SpEL có parser/evaluator và quy tắc lỗi riêng. Đừng nhầm `IllegalArgumentException` của strict placeholder resolver với một SpEL parsing exception hoặc type conversion error.

Khi viết application config, có thể có nesting giữa placeholders và SpEL, nhưng nên giữ ngữ nghĩa dễ đọc. Nếu biểu thức quá phức tạp, tạo configuration bean và code validation rõ ràng thay vì nhồi logic vào annotation.

## 13. Lỗi @PropertySource resource location

Spring có thể resolve placeholder trong location của `@PropertySource`:

~~~java
@Configuration
@PropertySource(
        "classpath:/conf/${deployment.region:local}/service.properties")
public class ServicePropertyConfig {
}
~~~

Đây là snippet, và các file thực tế phải nằm trong classpath tương ứng. Trong giai đoạn xử lý configuration, Spring resolve location theo **những property sources đã được đăng ký trước thời điểm đó**.

Nếu `deployment.region` chưa có, fallback `local` được dùng. Nếu location chỉ có `${deployment.region}` mà key không phân giải được và không có fallback, Spring có thể ném **`IllegalArgumentException`**.

### Vòng phụ thuộc bootstrap

Không đặt `deployment.region` chỉ **trong file cần chọn bằng chính deployment.region**. Resolver cần biết path trước khi mở file; key phải có ở source sớm (JVM property, environment, bootstrap config).

### Spring Core khác Spring Boot

Plain `AnnotationConfigApplicationContext` không tự nạp mọi `application.yml` hay profile-specific config file theo conventions của Spring Boot. Với Core hãy chủ động dùng `@PropertySource` hoặc đăng ký `MapPropertySource`/các sources phù hợp. Với Boot, phải xét ConfigData và precedence của phiên bản đang dùng.

## 14. Chẩn đoán theo hiện tượng

| Dấu hiệu | Nguyên nhân khả dĩ | Hướng xử lý |
| --- | --- | --- |
| String @Value còn nguyên `${key}` | Lenient embedded resolver, key thiếu | Dùng strict configurer hoặc required lookup |
| `resolvePlaceholders` giữ nguyên token | API lenient theo thiết kế | Chuyển sang strict nếu output phải hoàn chỉnh |
| `resolveRequiredPlaceholders` ném IllegalArgumentException | Template có key thiếu, không default | Sửa nguồn key hoặc fallback có chủ ý |
| `getRequiredProperty` ném IllegalStateException | Required property key thiếu | Bổ sung key hoặc thay requirement |
| `getProperty("url")` có key nhưng vẫn lỗi | Value chứa nested placeholder thiếu | Kiểm tra keys được tham chiếu bên trong |
| Chuỗi rỗng không dùng default | Blank khác missing | Thêm validation nonblank |
| @Value vào int lỗi | Literal unresolved hoặc text sai kiểu | Kiểm tra key, resolver, conversion |
| Đã khai báo key trong file nhưng không thấy | File chưa được nạp/không đúng context | Kiểm tra @PropertySource và bootstrap |
| @PropertySource location unresolved | Location phụ thuộc key chưa có sớm | Đưa key sang bootstrap source hoặc fallback |
| Lỗi chỉ khi getBean() | Bean lazy, injection chưa diễn ra | Eager validate required settings |
| strict configurer có nhưng app vẫn chấp nhận key thiếu trên lazy bean | Chưa tạo bean chứa @Value | Test lazy bean/validate settings từ startup |
| Đặt ignoreUnresolvable=true làm lỗi biến mất | Lỗi bị hoãn hoặc literal được giữ nguyên | Đánh giá lại fail-fast policy |
| URI tạo ra không hợp lệ sau resolution | Blank/malformed value dù keys đủ | Validate URL/port sau resolution |
| Spring Boot đọc value khác với Core demo | PropertySource precedence/ConfigData khác | Inspect sources và profile activation |
| Text literal placeholder bị đánh dấu lỗi | Escaping hoặc deliberate template | Định nghĩa rõ phạm vi resolution |
| Có vòng tham chiếu giữa properties | Cyclic dependency trong config | Phá vòng và test nested refs |
| Lỗi trong `#{...}` | SpEL chứ không phải placeholder resolver | Chẩn đoán expression evaluator |
| Log lộ secret | In raw property values | Chỉ log key/cause; mask secret |

### Quy trình gỡ lỗi

1. Xác nhận chuỗi `${...}` có phải placeholder cần resolve **ở giai đoạn hiện tại** không.
2. Xác định API: lenient, strict, key lookup hay `@Value`.
3. Kiểm tra context có `PropertySourcesPlaceholderConfigurer` không và đang dùng strict hay ignore.
4. Đối chiếu key name, default và nested dependencies.
5. Xem `Environment.getPropertySources()` theo thứ tự ưu tiên.
6. Kiểm tra missing khác blank khác malformed.
7. Đọc exception cause chain, không chỉ top-level `BeanCreationException`.
8. Xem lifecycle: key/source được đăng ký trước refresh chưa, bean có lazy không.
9. Viết integration test với source được kiểm soát.
10. Không log secrets trong quá trình điều tra.

## 15. Kiểm thử đúng cách

### 15.1. Test strict vs lenient

~~~java
StandardEnvironment env = new StandardEnvironment();
env.getPropertySources().addFirst(
        new MapPropertySource(
                "test",
                Map.<String, Object>of("demo.host", "localhost")));

String resolved = env.resolveRequiredPlaceholders(
        "host=${demo.host}");

if (!"host=localhost".equals(resolved)) {
    throw new AssertionError("Resolution incorrect");
}
~~~

Snippet này cần imports `StandardEnvironment`, `MapPropertySource`, `Map`. Để test missing chắc chắn, dùng namespace unique hoặc resolver với nguồn test cô lập thay vì key phổ biến như `PATH`.

### 15.2. Kiểm tra missing

~~~java
String input = "missing=${demo.unused.unique}";

if (!input.equals(env.resolvePlaceholders(input))) {
    throw new AssertionError("Lenient should preserve missing token");
}

boolean failed = false;
try {
    env.resolveRequiredPlaceholders(input);
} catch (IllegalArgumentException expected) {
    failed = true;
}

if (!failed) {
    throw new AssertionError("Strict should reject missing token");
}
~~~

Đây là snippet unit test tiếp theo, không phải file standalone. Nó test đúng sự khác biệt về contract.

### 15.3. Test blank và default

Tạo một source với `"demo.blank" → ""`, assert:

~~~java
String value = env.resolveRequiredPlaceholders(
        "${demo.blank:fallback}");

if (!value.isEmpty()) {
    throw new AssertionError("Blank should not become fallback");
}
~~~

Nếu policy của ứng dụng muốn coi blank như missing, cần thêm validation/transformation ngoài resolver.

### 15.4. Integration test @Value

Dựng một `AnnotationConfigApplicationContext` có static `PropertySourcesPlaceholderConfigurer` và một bean `@Value("${required.key}")`. Với key đầy đủ, assert bean được tạo và value đúng. Với key thiếu, assert startup hoặc `getBean()` (nếu bean lazy) ném exception; inspect cause chain để xác định unresolved placeholder.

### 15.5. Test nested/cyclic and resource location

Test property `a=${b}`, `b=ready` phải resolve thành `ready`; `a=${b}`, `b=${a}` phải bị xem là cấu hình lỗi. Với `@PropertySource("classpath:${region}/x.properties")`, đặt `region` trước parse và kiểm tra resource được chọn; thử thiếu region không default để xác nhận failure.

## 16. Quy tắc áp dụng trong production

- [ ] Chỉ dùng lenient resolution khi downstream được phép nhận placeholder còn nguyên.
- [ ] Với endpoint/config quan trọng, dùng strict resolver hoặc required properties.
- [ ] Dùng `static @Bean PropertySourcesPlaceholderConfigurer` nếu muốn strict embedded `@Value`.
- [ ] Không bật ignore=true chỉ để che cấu hình thiếu.
- [ ] Đăng ký property sources trước khi dùng, kiểm tra precedence.
- [ ] Validate missing, nonblank, type, range và định dạng riêng.
- [ ] Truy vết nested placeholders và vòng tham chiếu.
- [ ] Đừng dùng String.contains như một placeholder parser.
- [ ] Phân biệt `${property}` và `#{SpEL}`.
- [ ] Hiểu escaped literal có thể cố ý không resolve.
- [ ] Với lazy beans, tạo/validate settings sớm nếu thật sự cần fail fast.
- [ ] Kiểm tra @PropertySource locations ở bootstrap.
- [ ] Test cả happy path lẫn missing/blank/wrong-type.
- [ ] Không đưa secret values vào logs.
- [ ] Đừng giả định Spring Core plain tự tải Boot ConfigData.

## 17. Câu hỏi tự kiểm tra

**1. `resolvePlaceholders("${x}")` trả gì nếu x không có?**  
Giữ nguyên placeholder `${x}` nếu không có fallback.

**2. `resolveRequiredPlaceholders("${x}")` có behavior nào khi thiếu?**  
Ném `IllegalArgumentException`.

**3. `getRequiredProperty("x")` khi thiếu dùng exception gì?**  
`IllegalStateException`.

**4. @Value String có luôn fail nếu placeholder thiếu không?**  
Không. Embedded resolver lenient có thể inject literal chưa được thay, tùy configurer.

**5. Làm cách nào để strict @Value?**  
Đăng ký `PropertySourcesPlaceholderConfigurer` dưới dạng `static @Bean`; mặc định không ignore missing placeholders.

**6. Khi key có value rỗng, `${key:default}` có tự dùng default không?**  
Không nên kỳ vọng; blank khác key missing.

**7. Có key `service.url` nhưng getProperty vẫn lỗi vì sao?**  
Value của nó có thể chứa nested placeholders thiếu hoặc vòng tham chiếu.

**8. Nếu @Value inject vào Integer và placeholder chưa resolve thì sao?**  
Có thể lỗi type conversion/bean creation chứ không chỉ giữ string.

**9. Strict configurer có bảo đảm mọi lazy bean được inject ngay khi refresh không?**  
Không. Injection trên lazy bean có thể diễn ra khi bean được yêu cầu.

**10. `@PropertySource` có thể dùng key được định nghĩa trong chính file đang chọn không?**  
Không nếu key ấy chưa có ở một source sẵn trước khi path được resolve.

**11. `#{2 + 3}` và `${port}` có giống nhau không?**  
Không; một là SpEL, một là property placeholder.

**12. Regex tìm dấu `${` có thay strict resolver được không?**  
Không. Nó không hiểu parser semantics, escaping và policy của ứng dụng.

**13. Fallback có giải quyết value sai format không?**  
Không; fallback dành cho missing. Chuyển kiểu và validation vẫn có thể lỗi.

**14. Tại sao nên ghi nhận key name nhưng không in raw value khi debug?**  
Vì value có thể chứa password/token; log có thể rò rỉ bí mật.

## Tài liệu chính thức

- [Spring Framework Reference — Using @Value](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/value-annotations.html) — embedded lenient resolution, strict configurer và type conversion.
- [Spring Framework Reference — Container Extension Points](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html) — `PropertySourcesPlaceholderConfigurer`.
- [Spring Framework Reference — Environment Abstraction](https://docs.spring.io/spring-framework/reference/core/beans/environment.html) — property sources và @PropertySource.
- [Spring Framework Javadoc — PropertyResolver](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/PropertyResolver.html) — hai resolve methods và exceptions.
- [Spring Framework Javadoc — AbstractPropertyResolver](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/AbstractPropertyResolver.html) — nested placeholders.
- [Spring Framework Javadoc — PropertySourcesPlaceholderConfigurer](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/PropertySourcesPlaceholderConfigurer.html).
- [Spring Framework Javadoc — PlaceholderConfigurerSupport](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/config/PlaceholderConfigurerSupport.html).
- [Spring Framework Javadoc — @PropertySource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/PropertySource.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="values-and-expressions-005">Chuyển đổi từ chuỗi sang kiểu đích bằng ConversionService</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển đổi từ chuỗi sang kiểu đích bằng ConversionService trong Spring Core Container

## 1. Tổng quan

Trong Spring, cấu hình thường đến từ các chuỗi: tệp properties, JVM system properties, biến môi trường, placeholder của `@Value`, XML bean definitions hoặc đầu vào người dùng. Nhưng một dependency có thể cần `Integer`, `boolean`, `enum`, `List<Integer>` hoặc một value object riêng. `org.springframework.core.convert.ConversionService` giúp chuyển đổi giữa **kiểu nguồn** và **kiểu đích**, giảm việc viết `Integer.parseInt` và code chuyển kiểu thủ công khắp ứng dụng.

Bài học có bốn mục tiêu thực hành: sử dụng bộ converter sẵn có; tạo `Converter<String,T>` cho kiểu domain; giữ metadata của generics bằng `TypeDescriptor`; tích hợp ConversionService vào BeanFactory để `@Value` cung cấp giá trị đúng kiểu.

Cần phân biệt ba nhiệm vụ:

1. **Property lookup:** `Environment.getProperty("app.port")` tìm giá trị trong các nguồn và thường trả chuỗi.
2. **Placeholder resolution:** biểu thức `${app.port}` được thay bằng chuỗi `"8443"`.
3. **Type conversion:** chuỗi `"8443"` được chuyển thành `int` hay `ServerPort` theo kiểu đích.

`ConversionService` không tự đi tìm một key cấu hình. Nó nhận giá trị cần chuyển và kiểu đích, rồi chọn converter phù hợp.

## 2. Kiến trúc chung

```text
.properties / OS environment / JVM system properties
                        |
                        v
            Environment / PropertyResolver
                        |
                        v
              placeholder -> String
                        |
                        v
            BeanFactory / BeanWrapper
                        |
                        v
                ConversionService
                        |
          chọn Converter<S,T> phù hợp
                        |
                        v
               object kiểu đích
                        |
                        v
           constructor / setter / field
```

Sơ đồ khái niệm trên không khẳng định mọi đường đi đều dùng các lớp theo một thứ tự y hệt. Spring còn có `PropertyEditor`, `TypeConverter`, formatters và các conversion services riêng cho MVC hoặc data binding.

## 3. API của ConversionService

Giao diện ở package `org.springframework.core.convert` có các method cốt lõi:

```java
boolean canConvert(Class<?> sourceType, Class<?> targetType);

<T> T convert(Object source, Class<T> targetType);

boolean canConvert(
        TypeDescriptor sourceType,
        TypeDescriptor targetType);

Object convert(
        Object source,
        TypeDescriptor sourceType,
        TypeDescriptor targetType);
```

Đây là **trích đoạn chữ ký API**, không phải mã Java độc lập. `canConvert` hỏi xem có cơ chế chuyển kiểu hay không; `convert` thử chuyển **giá trị cụ thể**. Ví dụ:

```java
ConversionService conversionService =
        new DefaultConversionService();

Integer retries = conversionService.convert(
        "4", Integer.class);
Boolean enabled = conversionService.convert(
        "true", Boolean.class);
```

Mảnh Java trên cần imports và phải đặt trong method/class hợp lệ. Kết quả là `retries == 4`, `enabled == true`.

`canConvert(String.class, Integer.class)` có thể trả true, nhưng `convert("four", Integer.class)` vẫn thất bại: **khả năng theo kiểu không chứng minh mọi giá trị nguồn đều hợp lệ**.

## 4. ConversionService và ConverterRegistry khác nhau

| Loại | Trách nhiệm |
| --- | --- |
| `ConversionService` | Facade gọi `canConvert`/`convert` |
| `ConverterRegistry` | Đăng ký converter, converter factory, generic converter |
| `ConfigurableConversionService` | Kết hợp API convert và registry |
| `GenericConversionService` | Implementation chung có registry và thuật toán chọn converter |
| `DefaultConversionService` | GenericConversionService với bộ converters thông dụng |
| `DefaultFormattingConversionService` | Kết hợp conversion và formatting infrastructure |
| `Converter<S,T>` | Một quan hệ chuyển từ source `S` sang target `T` |
| `ConverterFactory<S,R>` | Converter cho cả họ target types dưới `R` |
| `GenericConverter` | Conversion theo nhiều cặp type và `TypeDescriptor` |
| `ConditionalGenericConverter` | GenericConverter được chọn theo điều kiện metadata |

Code nghiệp vụ chỉ cần chuyển đổi nên phụ thuộc `ConversionService`, không cần thao tác registry. Bootstrap/configuration mới cần `addConverter`. Thêm/xóa converter khi nhiều thread đang xử lý values sẽ khiến policy khó kiểm soát; thiết lập registry khi khởi động rồi dùng chung.

## 5. DefaultConversionService: các conversion cơ bản

`DefaultConversionService` ở `org.springframework.core.convert.support` đã đăng ký nhiều rules thường dùng: String→number, String→Boolean, String→enum và các trường hợp numbers, arrays, collections, maps cùng element conversion khi có rule thích hợp.

```java
enum Mode { DEV, PROD }

DefaultConversionService cs = new DefaultConversionService();
Integer timeout = cs.convert("1500", Integer.class);
Mode mode = cs.convert("PROD", Mode.class);
```

Mảnh minh họa với `timeout == 1500` và `mode == Mode.PROD`. Không mặc định rằng chuỗi chữ thường `"prod"` sẽ tự thành `PROD`. Nếu cần chuyển không phân biệt hoa/thường, định nghĩa chính sách rõ và test nó.

### Shared instance

`DefaultConversionService.getSharedInstance()` là một shared fallback service. Theo Javadoc Spring, nên tạo **instance riêng** khi cần đăng ký converter tùy biến. Đừng mutate một shared instance dùng rộng khắp chỉ để phục vụ một feature.

### Không phải mọi String đều chuyển được

Một custom record `ServerPort` không tự có converter chỉ vì constructor nhận `int`. Với domain types, đăng ký `Converter<String, ServerPort>` hoặc giải pháp phù hợp khác.

## 6. canConvert, dữ liệu sai và ngoại lệ

Hai mệnh đề có thể cùng đúng:

```java
boolean capability = cs.canConvert(
        String.class, Integer.class); // true

Integer result = cs.convert(
        "not-a-number", Integer.class); // lỗi
```

Đây là mảnh minh họa. `canConvert` báo khả năng theo cặp type; `convert` còn phải parse từng giá trị.

Các lỗi liên quan:

- `ConverterNotFoundException`: không có converter phù hợp.
- `ConversionFailedException`: converter được chọn nhưng conversion của giá trị bị lỗi.
- `ConversionException`: lớp cha của nhiều lỗi conversion.

Đối với arrays/collections, kể cả khi converter cho collection type được phát hiện, một phần tử cụ thể vẫn có thể không convert được. Không chỉ unit test `canConvert` mà bỏ qua giá trị biên.

## 7. Tạo value object có invariant

Giả sử port chỉ hợp lệ từ 1 đến 65535:

```java
public record ServerPort(int value) {
    public ServerPort {
        if (value < 1 || value > 65535) {
            throw new IllegalArgumentException(
                    "Port must be in range 1..65535");
        }
    }
}
```

Đây là class đầy đủ cho `ServerPort.java` dùng Java 17. Nó thay cho việc truyền một `int` vô danh giữa các modules và làm rõ invariant.

Converter tương ứng:

```java
import org.springframework.core.convert.converter.Converter;

public final class StringToServerPort
        implements Converter<String, ServerPort> {

    @Override
    public ServerPort convert(String source) {
        try {
            int number = Integer.parseInt(source.trim());
            return new ServerPort(number);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Invalid numeric port: " + source, ex);
        }
    }
}
```

Đây là mã class cho `StringToServerPort.java`, giả định `ServerPort` đã được định nghĩa trong cùng package.

Đặc điểm:

- `Converter<String,ServerPort>` biểu đạt source/target bằng generic types.
- `source` được Converter SPI bảo đảm không null khi Spring gọi bình thường.
- `trim()` cho phép khoảng trắng quanh con số.
- Dữ liệu không parse được ném `IllegalArgumentException` với cause.
- Dữ liệu parse thành số nhưng ngoài khoảng cũng bị constructor record từ chối.
- Converter không lưu mutable state nên an toàn để dùng lại giữa nhiều threads.

Converter **không nên mở kết nối mạng** hoặc tải tài nguyên nặng chỉ để xác minh port: việc convert có thể xảy ra ngay trong quá trình tạo bean; kiểm tra readiness là trách nhiệm khác.

## 8. Đăng ký Converter vào DefaultConversionService

```java
DefaultConversionService cs = new DefaultConversionService();
cs.addConverter(new StringToServerPort());

ServerPort port = cs.convert(
        " 8080 ", ServerPort.class);
```

Mảnh code trên cần classes đã định nghĩa ở mục 7. `DefaultConversionService` vừa là ConversionService, vừa có khả năng `addConverter` qua registry API.

Giá trị không hợp lệ:

| Input | Kết quả |
| --- | --- |
| `"8080"` | `ServerPort(8080)` |
| `" 8080 "` | `ServerPort(8080)` |
| `"abc"` | Lỗi cú pháp số |
| `"0"` | Lỗi domain ngoài khoảng |
| `"65536"` | Lỗi domain ngoài khoảng |
| `""` | Lỗi blank |
| Java `null` | ConversionService xử lý null theo API; Converter không mặc nhiên được gọi với null |

Không dùng default cho mọi lỗi. Fallback chỉ có nghĩa khi giá trị không được cung cấp và ứng dụng cho phép default; chuỗi có giá trị sai nên được báo lỗi.

## 9. ConverterFactory và cả họ target types

`ConverterFactory<S,R>` cung cấp converter cho các target type `T extends R`:

```java
public interface ConverterFactory<S,R> {
    <T extends R> Converter<S,T> getConverter(Class<T> targetType);
}
```

Chữ ký này là trích đoạn interface để giải thích. Spring dùng một converter factory cho String→Enum: source là String, target cụ thể có thể là các enum classes khác nhau.

Nếu chỉ có một value object `ServerPort`, dùng `Converter<String,ServerPort>`. Không cần dựng factory phức tạp cho một cặp type duy nhất.

## 10. GenericConverter và metadata của kiểu

`GenericConverter` hữu ích khi conversion dựa trên nhiều cặp source-target hoặc phải nhìn metadata:

```java
public interface GenericConverter {
    Set<ConvertiblePair> getConvertibleTypes();

    Object convert(
            Object source,
            TypeDescriptor sourceType,
            TypeDescriptor targetType);
}
```

Đây là trích đoạn API, không phải một file class độc lập.

`TypeDescriptor` mang thông tin element type của array/collection/map và annotation context khi nó được tạo từ field/method parameter. `ConditionalGenericConverter` còn có `matches` để giới hạn converter theo annotation hoặc đặc điểm target. Đừng dùng các SPI sâu này khi converter đơn giản đã đủ.

## 11. Chuyển List<String> sang List<Integer>

Do Java generic type erasure, chỉ truyền `List.class` không đủ diễn đạt **List có phần tử Integer**. Muốn các phần tử được convert đúng, dùng `TypeDescriptor`:

```java
TypeDescriptor from = TypeDescriptor.collection(
        List.class, TypeDescriptor.valueOf(String.class));

TypeDescriptor to = TypeDescriptor.collection(
        List.class, TypeDescriptor.valueOf(Integer.class));

Object converted = cs.convert(
        List.of("1", "2", "3"), from, to);
```

Mảnh code này giả định `cs` là `DefaultConversionService` và đã import `List`/`TypeDescriptor`. Converter container có sẵn cùng String→Integer sẽ đổi từng phần tử.

Nếu chỉ gọi `cs.convert(source, List.class)`, Spring có thể trả một list nhưng chưa có thông tin generic type để bắt buộc chuyển các phần tử String sang Integer.

`TypeDescriptor` cũng có các cách tạo từ field, method parameter, array và map descriptors. Khi conversion có kiểu generic lồng nhau, xây descriptors tương ứng thay vì giả định raw `Map.class` luôn đủ.

## 12. Kết nối ConversionService với Spring IoC Container

Một service được bạn tạo thủ công bằng `new DefaultConversionService()` không tự thay thế conversion service trong BeanFactory. Spring Framework Reference hướng dẫn định nghĩa bean có tên đặc biệt **`conversionService`**:

```java
@Bean("conversionService")
ConversionService conversionService() {
    DefaultFormattingConversionService service =
            new DefaultFormattingConversionService();
    service.addConverter(new StringToServerPort());
    return service;
}
```

Đây là mảnh method đặt trong `@Configuration` class cùng imports và converter phù hợp.

Trong đường `ApplicationContext.refresh()` của Spring, BeanFactory có thể nhận diện bean `conversionService` theo tên/kiểu, và dùng nó ở các giai đoạn injection/property binding. Tên bean quan trọng: một bean tên `otherConversions` chỉ tồn tại để inject thủ công không chắc được framework chọn theo convention.

Ở mức BeanFactory thấp, `ConfigurableBeanFactory.setConversionService(service)` có thể được gọi trực tiếp bởi infrastructure. Không tự gọi trong một bean constructor hay trong lúc các processors chưa được đăng ký đầy đủ.

**Không kết luận rằng thiếu bean conversionService khiến mọi int/boolean injection thất bại.** Spring vẫn có những cơ chế conversion/editors mặc định.

## 13. @Value và các bước trước conversion

Nếu config có:

```properties
app.port=8443
app.retries=4
app.enabled=true
```

thì injection point:

```java
@Component
class EndpointSettings {
    @Value("${app.port}")
    private ServerPort port;
}
```

là một ví dụ minh họa, cần imports/bean class khác phù hợp. Trong Markdown cuối, chuỗi Java có dạng `"${app.port}"` với placeholder Spring đúng cú pháp, **không chứa backslash trước ký tự dollar**.

Cơ chế liên quan:

1. `Environment` lấy `app.port` từ nguồn có precedence cao nhất.
2. Embedded value resolver/placeholder configurer biến placeholder thành chuỗi `"8443"`.
3. BeanFactory dùng ConversionService phù hợp để chuyển chuỗi đó thành `ServerPort`.

Nếu key thiếu, lỗi là lookup/placeholder resolution; nếu key tồn tại mà là `"nope"`, converter được gọi nhưng không parse được.

### Default trong placeholder

`@Value("${app.retries:3}")` dùng chuỗi `"3"` khi thiếu key. Nếu key tồn tại với giá trị `"abc"` thì quá trình conversion sang `int` vẫn phải từ chối.

### Strict placeholder configuration

Spring Core có embedded resolver mặc định có thể lenient trong một số cấu hình. Khi cần fail-fast vì key bắt buộc thiếu, dùng `PropertySourcesPlaceholderConfigurer` qua static `@Bean`:

```java
@Bean
public static PropertySourcesPlaceholderConfigurer
        propertySourcesPlaceholderConfigurer() {
    return new PropertySourcesPlaceholderConfigurer();
}
```

Import chính xác là `org.springframework.context.support.PropertySourcesPlaceholderConfigurer`, không phải package bean factory. `static` phù hợp vì processor này cần được tạo sớm.

## 14. Ví dụ Java 17 chạy được: ConversionService và @Value

Ví dụ Spring Framework 6.2.18, không dùng Spring Boot, minh họa cả **API conversion trực tiếp** và **container injection** với custom converter.

Cấu trúc:

```text
conversion-service-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/ConversionServiceDemo.java
```

### settings.gradle

```groovy
rootProject.name = 'conversion-service-demo'
```

### build.gradle

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

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'ConversionServiceDemo'
}
```

### src/main/java/ConversionServiceDemo.java

```java
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.env.MapPropertySource;
import org.springframework.format.support.DefaultFormattingConversionService;

public class ConversionServiceDemo {

    public enum Mode {
        DEV, PROD
    }

    public record ServerPort(int value) {
        public ServerPort {
            if (value < 1 || value > 65535) {
                throw new IllegalArgumentException(
                        "Port must be in 1..65535");
            }
        }
    }

    public record Settings(
            ServerPort port, int retries, boolean enabled) {
    }

    public static final class StringToServerPort
            implements Converter<String, ServerPort> {

        @Override
        public ServerPort convert(String source) {
            try {
                int n = Integer.parseInt(source.trim());
                return new ServerPort(n);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid numeric port: " + source, ex);
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {

        @Bean("conversionService")
        public ConversionService conversionService() {
            DefaultFormattingConversionService cs =
                    new DefaultFormattingConversionService();
            cs.addConverter(new StringToServerPort());
            return cs;
        }

        @Bean
        public static PropertySourcesPlaceholderConfigurer
                propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        public Settings settings(
                @Value("${app.port}") ServerPort port,
                @Value("${app.retries:3}") int retries,
                @Value("${app.enabled:false}") boolean enabled) {
            return new Settings(port, retries, enabled);
        }
    }

    public static void main(String[] args) {

        DefaultConversionService cs = new DefaultConversionService();
        cs.addConverter(new StringToServerPort());

        System.out.println("direct.int="
                + cs.convert("42", Integer.class));
        System.out.println("direct.boolean="
                + cs.convert("true", Boolean.class));
        System.out.println("direct.enum="
                + cs.convert("PROD", Mode.class));
        System.out.println("direct.port="
                + cs.convert(" 8080 ", ServerPort.class).value());

        TypeDescriptor from = TypeDescriptor.collection(
                List.class, TypeDescriptor.valueOf(String.class));
        TypeDescriptor to = TypeDescriptor.collection(
                List.class, TypeDescriptor.valueOf(Integer.class));

        Object list = cs.convert(
                List.of("1", "2", "3"), from, to);
        System.out.println("direct.list=" + list);

        System.out.println("direct.canConvertPort="
                + cs.canConvert(String.class, ServerPort.class));

        try {
            cs.convert("invalid", ServerPort.class);
            throw new AssertionError("Conversion must fail");
        } catch (ConversionException ex) {
            System.out.println("direct.invalidPort=rejected");
        }

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource(
                            "demoValues",
                            Map.<String, Object>of(
                                    "app.port", "8443",
                                    "app.retries", "4",
                                    "app.enabled", "true")));

            context.register(Config.class);
            context.refresh();

            Settings settings = context.getBean(Settings.class);
            System.out.println("context.port="
                    + settings.port().value());
            System.out.println("context.retries="
                    + settings.retries());
            System.out.println("context.enabled="
                    + settings.enabled());
            System.out.println("context.converterInstalled="
                    + (context.getBeanFactory()
                            .getConversionService() != null));
        }
    }
}
```

### Chạy

Cần Java 17+ và Gradle 8+. Trong thư mục dự án:

```bash
gradle run --quiet
# Nếu đã tạo Gradle Wrapper: ./gradlew run --quiet
```

Các dòng do Java in bằng `System.out`:

```text
direct.int=42
direct.boolean=true
direct.enum=PROD
direct.port=8080
direct.list=[1, 2, 3]
direct.canConvertPort=true
direct.invalidPort=rejected
context.port=8443
context.retries=4
context.enabled=true
context.converterInstalled=true
```

Log framework hoặc Gradle (nếu có) không nằm trong các dòng `System.out` trên.

### Giải thích theo từng nhóm

**Nhóm direct:** `DefaultConversionService` có converter số, Boolean, enum, collection; chúng ta đăng ký thêm String→ServerPort. Chuỗi `" 8080 "` được trim, record chứa 8080. `TypeDescriptor` giữ generic element types; kết quả list có các Integer, hiển thị qua `toString`.

**Nhóm lỗi:** input `"invalid"` đi qua converter rồi ném `IllegalArgumentException`, được conversion facade bọc trong `ConversionException` tương ứng. Code in “rejected” thay vì stack trace.

**Nhóm context:** `MapPropertySource` được thêm trước `register/refresh`, vì vậy `${app.port}` resolve thành `"8443"`. Bean tên `conversionService` cung cấp custom converter cho BeanFactory; parameter `ServerPort` của `@Bean settings(...)` được chuyển kiểu. Hai parameters primitive cũng được chuyển từ chuỗi.

**Lifecycle:** bean `Settings` là singleton mặc định của context. Tạo `DefaultConversionService` riêng ở phần direct không tự gắn instance direct đó vào BeanFactory.

### Phạm vi của bảo đảm

Đây là ví dụ **Spring Core Container**. Spring Boot có các conversion services hoặc binder riêng; không nên áp thẳng mọi hiệu ứng sang MVC hoặc Boot configuration-properties binding mà không kiểm tra đúng extension point.

## 15. Sử dụng trong BeanWrapper và setter injection

Ví dụ một JavaBean nhận property kiểu domain:

```java
public final class ConnectionOptions {
    private ServerPort serverPort;

    public void setServerPort(ServerPort serverPort) {
        this.serverPort = serverPort;
    }

    public ServerPort getServerPort() {
        return serverPort;
    }
}
```

Đây là mảnh class, cần `ServerPort` ở cùng package. Khi BeanFactory có một bean definition với property value dạng chuỗi và property setter nhận `ServerPort`, nó có thể dùng ConversionService để chuẩn bị đúng kiểu trước khi gọi setter.

Nhưng một object bạn tự `new ConnectionOptions()` ngoài container sẽ không tự nhận converter đã đăng ký. Nếu code tự gán property, phải gọi conversion service hoặc đưa việc xây dựng object vào một factory/bean definition mà Spring quản lý.

## 16. FormattingConversionService, Formatter và locale

`ConversionService` không đồng nhất với formatting. `DefaultFormattingConversionService` có thêm infrastructure để parse/print dữ liệu theo format và locale tại những nơi framework dùng formatters.

Ví dụ ngày `31/12/2026` không mặc nhiên đổi thành `LocalDate` trong mọi Spring application mà không cần pattern hoặc formatter phù hợp. Với UI data binding, các annotation như `@DateTimeFormat` và `@NumberFormat` có thể ảnh hưởng parsing khi integration point hỗ trợ.

Nếu chỉ chuyển String→ServerPort, `DefaultConversionService` đã đủ. Ví dụ `@Bean conversionService` dùng `DefaultFormattingConversionService` để minh họa cách Spring Reference hướng dẫn custom `@Value` type conversion.

## 17. PropertyEditor và TypeConverter là gì?

Spring còn hỗ trợ hệ thống `PropertyEditor` của JavaBeans, `TypeConverter` và `BeanWrapper`. Chúng liên quan nhưng không nên bị gọi chung là ConversionService:

| Thành phần | Nhiệm vụ |
| --- | --- |
| `PropertyEditor` | Parse/format value theo API JavaBeans truyền thống |
| `PropertyEditorRegistrar` | Đăng ký các custom editors |
| `BeanWrapper` | Truy cập setters/property descriptors và hỗ trợ type conversion |
| `TypeConverter` | Giao diện chuyển value sang target type tại binding |
| `ConversionService` | Facade chuyển kiểu tổng quát, dùng qua nhiều tầng |
| `Formatter` | Parse/print text với thông tin locale/format |

Không có custom ConversionService chưa chắc làm `String→Integer` trong Spring hỏng vì hệ thống còn editors và các converters built-in khác. Nhưng custom domain type cần một rule được đăng ký đúng chỗ.

## 18. Khác biệt với Spring Boot và MVC

Spring Boot binder, Spring MVC formatting/binding, SpEL và Core BeanFactory có thể dùng **những ConversionService instances khác nhau** hoặc thiết lập theo mục đích khác nhau.

Nếu mục tiêu là injection `@Value`/BeanFactory trong Spring Core, hãy đăng ký bean `conversionService` như phần trước và test qua context. Nếu mục tiêu là parse một request parameter ở MVC, hãy tìm cơ chế đăng ký `FormatterRegistry`/`WebMvcConfigurer` tương ứng. Nếu mục tiêu là `@ConfigurationProperties` binding của Boot, kiểm tra rules của Boot binder.

Không suy ra rằng đăng ký một converter vào bất kỳ instance nào sẽ tự ảnh hưởng mọi subsystem.

## 19. Kiểm thử và lỗi thường gặp

Nên kiểm tra: chuyển đổi giá trị hợp lệ, blank, invalid number, domain range, enum casing, collection elements, integration `@Value` trong context, missing property, parser lỗi, thread safety và converter registration timing.

| Triệu chứng | Nguyên nhân | Cách khắc phục |
| --- | --- | --- |
| `ConverterNotFoundException` | Thiếu converter ở service thực sự được dùng | Đăng ký đúng cặp source/target |
| `ConversionFailedException` | Giá trị sai hoặc converter ném lỗi | Xem cause, chỉnh dữ liệu/quy tắc |
| `canConvert` true nhưng convert fail | Capability theo type không đảm bảo input | Test values cụ thể |
| String enum chữ thường không khớp | Không có normalization policy | Viết converter rõ ràng |
| List elements không đổi kiểu | Chỉ cung cấp raw `List.class` | Dùng `TypeDescriptor.collection` |
| Custom @Value field không convert | BeanFactory không dùng custom service | Kiểm tra bean tên `conversionService` |
| Placeholder còn nguyên | Property resolver/configurer chưa resolve | Kiểm tra nguồn, strict configurer |
| Giá trị singleton không đổi sau update property | Đã resolve tại initialization | Thiết kế refresh/rebind riêng |
| Converter dùng network/DB | Side effect không phù hợp lúc khởi tạo | Giữ converter stateless và thuần |
| Test direct pass nhưng context fail | Chưa test container wiring | Integration test context thật |

Checklist nhanh: chọn đúng boundary để convert; kiểm tra target generic type; giữ converter thread-safe; dùng mặc định khi có; đảm bảo missing key không bị che bằng conversion fallback; thiết lập BeanFactory conversion service trước khi tạo bean; tách I/O và validation phức tạp ra khỏi converter; kiểm thử ngoại lệ và message không tiết lộ secrets.

## 20. Tài liệu tham khảo chính thức

1. [Spring Framework 6.2 Reference — Spring Type Conversion](https://docs.spring.io/spring-framework/reference/6.2/core/validation/convert.html): Converter SPI, ConversionService, TypeDescriptor và registration.
2. [Spring Framework 6.2 Reference — Using @Value](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/value-annotations.html): placeholders và custom ConversionService cho injection.
3. [Spring 6.2 Javadoc — ConversionService](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/convert/ConversionService.html): `canConvert`/`convert`.
4. [Spring 6.2 Javadoc — Converter](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/convert/converter/Converter.html): contract source non-null, failure semantics.
5. [Spring 6.2 Javadoc — DefaultConversionService](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/convert/support/DefaultConversionService.html): built-in converters và shared instance.
6. [Spring 6.2 Javadoc — TypeDescriptor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/convert/TypeDescriptor.html): generics và typed conversion.
7. [Spring Framework Reference — Validation, Data Binding and Type Conversion](https://docs.spring.io/spring-framework/reference/6.2/core/validation.html): BeanWrapper, PropertyEditor, conversion/formatting.

**Kết luận:** `ConversionService` biến giá trị **đã tìm được** thành **kiểu Java mà consumer yêu cầu**. Dùng `DefaultConversionService` trước, thêm `Converter<String,T>` cho domain value object, dùng `TypeDescriptor` cho collection generics và gắn bean `conversionService` ở đúng container khi cần custom `@Value` injection.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="values-and-expressions-006">PropertyEditor và giới hạn của cách chuyển đổi cũ</a>

<details>
<summary>Xem chi tiết</summary>

# PropertyEditor và giới hạn của cách chuyển đổi cũ trong Spring Core Container

## 1. Vấn đề: cấu hình dạng chuỗi, Java bean cần kiểu dữ liệu

Khi container tạo một bean, cấu hình từ XML, bean definition, tệp properties hay các nguồn ngoài thường biểu diễn giá trị bằng **chuỗi**. Nhưng Java object cần những kiểu mạnh hơn: `int`, `boolean`, `java.net.URL`, `java.time.Duration`, enum, hoặc domain value object như `Money` và `Tag`.

Ví dụ một bean Java có setter:

```java
public void setTag(Tag tag) {
    this.tag = tag;
}
```

trong khi definition chứa giá trị dạng văn bản:

```text
tag = "spring"
```

Spring cần một **cơ chế chuyển đổi `String → Tag`** trước khi gọi setter. Trong thiết kế lịch sử dựa trên JavaBeans, cơ chế đó thường là **`java.beans.PropertyEditor`**. Spring hỗ trợ nhiều editor tích hợp, cho phép đăng ký editor tùy biến và kết nối chúng với bean property population hoặc data binding.

Ngày nay, Spring có hệ thống thay thế giàu tính mở rộng hơn: **`Converter<S,T>`, `ConverterFactory`, `GenericConverter`, `ConversionService`** và, cho trường hợp có locale, **`Formatter<T>`**. Hiểu PropertyEditor giúp đọc ứng dụng XML/legacy, chẩn đoán chuyển kiểu và biết lý do Spring khuyên dùng conversion SPI hiện đại cho mã mới.

> **Tư tưởng chính:** `PropertyEditor` là bộ chuyển đổi/biên tập một giá trị theo mô hình JavaBeans; nó thường **giữ trạng thái đang được chỉnh sửa**. `Converter<S,T>` mô tả phép chuyển kiểu mạnh, thường có thể dùng lại an toàn giữa các lời gọi nếu implementation stateless/thread-safe.

## 2. PropertyEditor là gì?

`java.beans.PropertyEditor` là một **interface của JavaBeans trong JDK**, không phải interface do Spring tự đặt ra. Nó gắn với khái niệm *property* của một Java bean và hỗ trợ biểu diễn/chỉnh một giá trị ở dạng văn bản, ngoài các khả năng JavaBeans editor khác.

Hai nhóm method quan trọng khi học Spring:

- `setAsText(String text)`: nhận chuỗi và chuyển thành object đích, lưu object đó làm **giá trị hiện tại của editor**.
- `getValue()`: lấy object hiện editor đang giữ.
- `setValue(Object value)`: đặt giá trị hiện tại.
- `getAsText()`: biểu diễn giá trị hiện tại thành chuỗi nếu editor hỗ trợ.

```java
PropertyEditor editor = new TagEditor();
editor.setAsText("  java  ");
Tag tag = (Tag) editor.getValue();
String presentation = editor.getAsText();
```

Đây là **snippet minh họa**, cần import `java.beans.PropertyEditor` và class `TagEditor`/record `Tag` như ví dụ chạy đầy đủ ở phần 7. Không tự nhiên có một `TagEditor` trong JDK; ứng dụng phải implement.

### 2.1. PropertyEditorSupport giúp đỡ điều gì?

Thay vì implement hàng loạt methods của interface `PropertyEditor`, lập trình viên thường **kế thừa `java.beans.PropertyEditorSupport`**, rồi override hai methods cần thiết:

```java
public final class TagEditor extends PropertyEditorSupport {
    @Override
    public void setAsText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("tag must not be blank");
        }
        setValue(new Tag(text.trim().toUpperCase(Locale.ROOT)));
    }

    @Override
    public String getAsText() {
        Tag tag = (Tag) getValue();
        return tag == null ? "" : tag.code();
    }
}
```

Đây là **đoạn class minh họa**, cần import `java.beans.PropertyEditorSupport`, `java.util.Locale` và record `Tag`. Cấu trúc này mô tả cách convert từ text và trả text. `PropertyEditorSupport` vẫn có các hành vi JavaBeans liên quan như quản lý listeners/change events; Spring thường chỉ quan tâm đến conversion hợp đồng cụ thể.

**Không nhầm** tên `PropertyEditor` với một trình soạn thảo giao diện người dùng. JavaBeans ra đời cùng mô hình tool/editor GUI, nhưng trong Spring IoC, `PropertyEditor` thường hoạt động **headless** để chuyển dữ liệu cấu hình sang object.

### 2.2. Editor giữ một value đang chỉnh sửa

Một instance của `PropertyEditorSupport` có thể được dùng như sau:

```java
editor.setAsText("alpha");
Object first = editor.getValue();

editor.setAsText("beta");
Object second = editor.getValue();
```

Lúc này instance **không đại diện đồng thời cho cả hai phép chuyển đổi độc lập**. `setAsText` lần sau cập nhật state bên trong editor. Đó là lý do không nên chia sẻ một editor object duy nhất giữa nhiều threads hoặc nhiều lần binding song song.

`PropertyEditor` không có generic type parameters `<S,T>`; phần lớn typing nằm trong cast, registration metadata và conventions. Trái lại, `Converter<String,Tag>` biểu đạt trực tiếp kiểu nguồn và kiểu đích.

## 3. PropertyEditor tham gia vào Spring BeanFactory như thế nào?

Trong một bean definition, Spring lưu constructor arguments và property values theo metadata. Khi tạo bean:

1. Chọn constructor hoặc factory method để tạo instance.
2. Giải quyết các dependencies và **điền bean properties** theo definition.
3. Nếu một property có giá trị kiểu `String` nhưng setter yêu cầu kiểu khác, Spring dùng **type conversion** phù hợp.
4. Với đường JavaBeans cũ, `TypeConverter`/`BeanWrapper` có thể sử dụng một `PropertyEditor` đã đăng ký cho target type/property path hoặc editor built-in.
5. Spring gọi setter bằng giá trị đã convert, rồi tiếp tục initialization lifecycle (`@PostConstruct`, `InitializingBean`, init-method theo cấu hình).

```mermaid
flowchart TD
    A["BeanDefinition: property tag = String"] --> B["Spring tạo bean instance"]
    B --> C["BeanWrapper / TypeConverter cần target type Tag"]
    C --> D{"Có converter/editor phù hợp?"}
    D -->|Có| E["Chuyển chuỗi thành Tag"]
    D -->|Không| F["Lỗi conversion hoặc property population"]
    E --> G["Gọi setTag(Tag)"]
    G --> H["Init callbacks / bean sẵn sàng"]
```

Điểm cần nhớ là editor **không tự quét toàn bộ classpath** để chuyển bất kỳ chuỗi nào sang bất kỳ class nào. Spring cần một đường chuyển đổi mà nó biết, từ editors mặc định, một `ConversionService`, custom editor registration hoặc cơ chế binder đã cấu hình.

### 3.1. BeanFactory không phải DataBinder

Trong Spring có hai ngữ cảnh dễ bị nhập nhằng:

- **BeanFactory property population**: điền property values khi tạo application bean theo `BeanDefinition`.
- **`DataBinder`**: binding một tập property values lên một target object, thường dùng trong Spring MVC và các bài toán nhập dữ liệu.

`CustomEditorConfigurer` là cơ chế đăng ký editor cho **BeanFactory**. Theo Javadoc, **custom editors được đăng ký bằng configurer này không tự động áp dụng cho một `DataBinder` độc lập**. Muốn dùng editor khi data binding, phải đăng ký editor cho chính binder hoặc thông qua cơ chế binder initialization chung của framework.

Đây là một giới hạn quan trọng: không phải cứ có `CustomEditorConfigurer` trong context thì mọi REST controller, mọi form binder hoặc mọi phép chuyển kiểu ở nơi khác đều tự nhận editor đó.

## 4. Các PropertyEditor có sẵn trong Spring

Spring có nhiều built-in property editors và hỗ trợ JavaBeans conversion lịch sử. Nhóm phổ biến giúp chuyển chuỗi thành hoặc từ:

- Kiểu số và boolean như `Integer`, `Long`, `Boolean` và các primitive wrappers trong ngữ cảnh thích hợp.
- Kiểu `Class`, `Locale`, `Charset`, `URL`, `URI`, `File`, `Resource` và một số collection/property types phù hợp.
- Kiểu ngày giờ cổ điển trong những hoàn cảnh cần editor đã khai báo pattern, ví dụ `CustomDateEditor`.

**Không suy rằng tất cả các kiểu đều luôn tự động convert chỉ vì một class PropertyEditor tương tự tồn tại.** Có editor là một chuyện; nó có được đăng ký vào `BeanFactory`/`BeanWrapper`/`DataBinder` cụ thể hay không là chuyện khác. Các default editors, converters và integration rules thay đổi theo loại context và cấu hình.

Ví dụ XML legacy:

```xml
<bean id="clientSettings" class="example.ClientSettings">
    <property name="timeout" value="15" />
    <property name="enabled" value="true" />
</bean>
```

Đây là **XML minh họa**, giả định `ClientSettings` có các writable JavaBeans properties `int timeout` và `boolean enabled`. Spring có thể convert hai chuỗi sang kiểu được setters khai báo. Nhưng với `Tag` domain object không có converter/editor mặc định, cần một cơ chế custom.

### 4.1. Vì sao JavaBeans property cần setter?

Với một bean property `tag`, Spring thường xác định writable property bằng method tương ứng như:

```java
public void setTag(Tag tag) { ... }
```

Nếu chỉ có field `private Tag tag;` mà không có setter, cách điền **JavaBeans property values** từ definition trong demo dưới đây không tự biến thành phép field injection. Spring cũng hỗ trợ các cơ chế injection khác như constructor injection, `@Autowired` field injection; chúng không cần cùng signature nhưng có contract riêng.

Nếu property không writable hoặc không thể convert, container có thể báo lỗi trong quá trình creation. Không sửa lỗi ấy bằng cách tạo một editor rồi kỳ vọng JavaBeans property sẽ tự xuất hiện.

## 5. Đăng ký PropertyEditor legacy trong Spring

### 5.1. CustomEditorConfigurer cho bean property population

Spring cung cấp class `org.springframework.beans.factory.config.CustomEditorConfigurer`, một **`BeanFactoryPostProcessor`** để cấu hình các editors cho bean factory.

Ví dụ với `@Configuration`:

```java
@Bean
public static CustomEditorConfigurer customEditors() {
    CustomEditorConfigurer configurer = new CustomEditorConfigurer();

    Map<Class<?>, Class<? extends PropertyEditor>> editors = new HashMap<>();
    editors.put(Tag.class, TagEditor.class);
    configurer.setCustomEditors(editors);

    return configurer;
}
```

Đây là **snippet tích hợp** cần imports `@Bean`, `CustomEditorConfigurer`, `Map`, `HashMap`, `PropertyEditor` và các class `Tag`, `TagEditor`. Bean name của configurer không mang ngữ nghĩa đặc biệt; method `static` giúp Spring nhận diện/tạo `BeanFactoryPostProcessor` đủ sớm mà không cần khởi tạo configuration instance trước hạ tầng.

**Điểm tốt của cách đăng ký này:** đưa **class editor** `TagEditor.class` vào map, thay vì đưa **một object `new TagEditor()` dùng chung**. Theo Javadoc, Spring tạo instances editor mới cho các lần editing khi đăng ký qua `customEditors` với editor class. Điều này phù hợp vì editor giữ mutable value state.

### 5.2. PropertyEditorRegistrar khi cần reuse cấu hình editor

Spring có interface `org.springframework.beans.PropertyEditorRegistrar`:

```java
public interface PropertyEditorRegistrar {
    void registerCustomEditors(PropertyEditorRegistry registry);
}
```

Đây là **chữ ký trích lược**, không cần copy định nghĩa interface của Spring. Một registrar có thể đăng ký các editors mới theo target type hoặc property path:

```java
public final class TagEditorRegistrar implements PropertyEditorRegistrar {
    @Override
    public void registerCustomEditors(PropertyEditorRegistry registry) {
        registry.registerCustomEditor(Tag.class, new TagEditor());
    }
}
```

Đây là snippet tích hợp, cần imports `PropertyEditorRegistrar`, `PropertyEditorRegistry` và `TagEditor`. **Tạo `new TagEditor()` mỗi lần `registerCustomEditors` được gọi**, không đặt một instance editor dùng chung làm field trong singleton registrar.

Đăng ký registrar cho BeanFactory qua `CustomEditorConfigurer`:

```java
@Bean
public static CustomEditorConfigurer tagRegistrars() {
    CustomEditorConfigurer c = new CustomEditorConfigurer();
    c.setPropertyEditorRegistrars(
            new PropertyEditorRegistrar[]{new TagEditorRegistrar()});
    return c;
}
```

Đây là snippet minh họa **thay thế** cách dùng `setCustomEditors` ở trên, không đăng ký cả hai cho cùng type mà không có lý do. Một registrar phù hợp khi cần logic khởi tạo editors phức tạp hoặc dùng cùng policies trên nhiều registry implementations.

### 5.3. Đăng ký trực tiếp trên DataBinder

```java
ClientSettings target = new ClientSettings();
DataBinder binder = new DataBinder(target);

binder.registerCustomEditor(Tag.class, new TagEditor());
binder.bind(new MutablePropertyValues(
        Map.of("tag", "spring")));

Tag tag = target.getTag();
```

Đây là **snippet tích hợp** cần imports `DataBinder` từ `org.springframework.validation`, `MutablePropertyValues` từ `org.springframework.beans`, `Map`, `Tag`, `TagEditor`, `ClientSettings` và setter `setTag`. Binder dùng editor đã đăng ký để convert chuỗi trước khi gọi setter.

Với binder gắn vào request web, nên để binder được tạo/cấu hình theo request/binding operation thích hợp; đừng chia sẻ cùng `DataBinder` stateful cho toàn bộ các requests.

### 5.4. @InitBinder dành cho web forms

Trong Spring MVC, controller có thể dùng `@InitBinder` để tùy biến binder cho request model:

```java
@InitBinder
public void initBinder(WebDataBinder binder) {
    binder.registerCustomEditor(Tag.class, new TagEditor());
}
```

Đây là snippet **của Spring MVC**, không runnable chỉ với `spring-context`. Cần imports `@InitBinder`, `WebDataBinder`, controller configuration và dependencies web liên quan.

Trong Spring MVC hiện đại, **`Formatter`/`ConversionService`** thường tốt hơn cho format và parse field values, nhất là khi có locale. Nếu đang dùng Spring Boot, MVC conversion service có thể được cấu hình qua cơ chế Boot/MVC; đừng mặc định lấy configurer của BeanFactory làm binder configurer.

## 6. Những giới hạn của PropertyEditor

### 6.1. Stateful và khó chia sẻ giữa nhiều threads

Điểm yếu nổi bật của `PropertyEditor` là **có `setValue`/`getValue`**; `setAsText` thường ghi vào chính instance.

```text
Thread A: editor.setAsText("A")
Thread B: editor.setAsText("B")
Thread A: editor.getValue()      <-- có thể nhận giá trị "B"
```

Đây là mô phỏng race condition, không phải stdout chắc chắn xảy ra. Vì vậy:

- **Không** đăng ký cùng một `PropertyEditor` object làm singleton dùng chung cho mọi requests.
- Khi viết `PropertyEditorRegistrar`, tạo **editor instance mới cho mỗi lần registrar áp dụng**.
- Khi cấu hình `CustomEditorConfigurer`, ưu tiên đăng ký **class** hoặc registrar thay vì chia sẻ object editor stateful.
- Một `Converter` thường không cần state “giá trị hiện tại”, nên dễ dùng như service singleton nếu implementation thread-safe.

Không đồng nghĩa mọi custom `Converter` tự động thread-safe: nếu converter tự giữ mutable state hoặc không đồng bộ, vẫn có nguy cơ tương tự.

### 6.2. Interface không type-safe theo generic

`PropertyEditor.setValue(Object)` và `getValue(): Object` khiến compiler khó phát hiện nhiều lỗi type trước runtime. Các casts như:

```java
Tag tag = (Tag) editor.getValue();
```

có thể gây `ClassCastException` nếu implementation lưu object sai type. `Converter<String,Tag>` thể hiện rõ `S` và `T`; compiler hỗ trợ hơn khi phát triển custom conversion.

### 6.3. Ràng buộc kiểu chuỗi và JavaBeans

`PropertyEditor` có lịch sử gắn với **biểu diễn text/object cho bean properties** và những công cụ JavaBeans. Nó không biểu đạt linh hoạt mọi phép chuyển giữa hai kiểu object khác nhau như `Converter<Money,BigDecimal>`, `Converter<String,URI>` hay converter collections có generic type descriptors.

Spring hiện đại cung cấp `ConversionService` với `TypeDescriptor`, `ConverterFactory`, `GenericConverter` và collection conversion thích hợp. Không nên tự tạo một loạt editors khác nhau để làm công việc một conversion service có thể thể hiện tốt hơn.

### 6.4. Locale và formatting không rõ ràng

Nhu cầu như đọc ngày `31/12/2026`, tiền tệ theo locale hoặc số có dấu thập phân khác nhau cần biết **`Locale`** và format pattern. Với `PropertyEditor`, code thường phải tự quản lý pattern/locale và state editor.

Spring Formatting SPI cung cấp:

```java
public interface Formatter<T> extends Printer<T>, Parser<T> {
}
```

Đây là chữ ký rút gọn. `Formatter<T>` hỗ trợ `parse(String, Locale)` và `print(T, Locale)`, giúp diễn đạt định dạng locale-aware. Trong UI/form binding, nên dùng `FormattingConversionService` hoặc cơ chế formatting của Spring MVC nếu phù hợp.

### 6.5. Phạm vi đăng ký không thống nhất

Một editor đăng ký cho `BeanFactory` **không mặc nhiên** được dùng bởi `DataBinder` khác. Một editor đăng ký trên `DataBinder` cho một field **không mặc nhiên** được dùng bởi `Environment.getProperty(...)` hoặc mọi `ConversionService` khác.

Điều này khiến ứng dụng legacy có thể convert được ở XML bean definition nhưng fail khi binding HTTP parameter — hoặc ngược lại. Khi chẩn đoán, phải xác định **ai đang yêu cầu chuyển kiểu** và registry/service nào đang được dùng tại điểm đó.

### 6.6. Error reporting và validation phải được tách riêng

Một editor có thể ném `IllegalArgumentException` nếu text invalid; Spring có thể wrap thành lỗi type mismatch/bean creation hoặc binding error tùy đường xử lý. **Conversion không phải validation nghiệp vụ.**

Ví dụ chuỗi `"5000"` có thể chuyển thành số nguyên thành công, nhưng một timeout 5000 phút vẫn có thể vi phạm policy. Nên tách:

1. **Parsing/conversion**: chuỗi có biến thành target type hợp lệ không?
2. **Validation**: target value có thỏa điều kiện nghiệp vụ, giới hạn, cross-field invariants không?
3. **Execution**: consumer có thể dùng cấu hình đó an toàn không?

### 6.7. Không sửa bean instance đã init bằng cách đăng ký editor muộn

Đăng ký editor và sửa `BeanDefinition` sau khi một singleton đã được tạo **không tự chạy lại** setter/constructor hoặc `@PostConstruct`. Đặc biệt, `CustomEditorConfigurer` là một `BeanFactoryPostProcessor`; nó phải được chuẩn bị vào giai đoạn bean factory bootstrap thích hợp.

`PropertyEditor` không phải cơ chế runtime config reload, event listener hay feature flag.

## 7. Ví dụ đầy đủ Java 17 + Spring Framework 6.2

Ví dụ này minh họa **ba đường chuyển kiểu**:

1. Gọi `TagEditor` trực tiếp theo JavaBeans.
2. Một `LegacyConfig` dùng `CustomEditorConfigurer` để Spring convert **String property value → Tag** khi tạo bean.
3. Một `ModernConfig` độc lập sử dụng bean tên `conversionService`, chứa `Converter<String,Tag>`, để Spring convert cùng loại property value.

Hai Spring contexts riêng giúp so sánh rõ đường legacy với modern, **không cho một custom editor đè lên converter cho cùng target type**.

**Yêu cầu:** JDK 17+, Maven 3.9+, `spring-context:6.2.0`; không cần Spring Boot, servlet, database hoặc JPA.

### 7.1. Cấu trúc

```text
property-editor-demo/
├── pom.xml
└── src/main/java/example/PropertyEditorDemo.java
```

### 7.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>property-editor-demo</artifactId>
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

### 7.3. src/main/java/example/PropertyEditorDemo.java

```java
package example;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorSupport;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.CustomEditorConfigurer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.validation.DataBinder;

public class PropertyEditorDemo {

    public static void main(String[] args) {
        TagEditor direct = new TagEditor();
        direct.setAsText("  java  ");
        System.out.println("direct editor: " + direct.getAsText());

        try (var legacy = new AnnotationConfigApplicationContext(
                LegacyConfig.class)) {
            ClientSettings settings =
                    legacy.getBean("settings", ClientSettings.class);
            System.out.println("legacy bean: " + settings.getTag().code());
        }

        try (var modern = new AnnotationConfigApplicationContext(
                ModernConfig.class)) {
            ClientSettings settings =
                    modern.getBean("settings", ClientSettings.class);
            System.out.println("modern bean: " + settings.getTag().code());

            ConversionService service = modern.getBean(
                    "conversionService", ConversionService.class);
            System.out.println("modern canConvert: "
                    + service.canConvert(String.class, Tag.class));
        }

        // DataBinder độc lập không dùng ngầm customEditors của BeanFactory.
        ClientSettings form = new ClientSettings();
        DataBinder binder = new DataBinder(form);
        binder.registerCustomEditor(Tag.class, new TagEditor());
        binder.bind(new MutablePropertyValues(
                Map.of("tag", "  spring  ")));
        System.out.println("binder: " + form.getTag().code());
    }

    public record Tag(String code) {
        public Tag {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("tag code required");
            }
        }
    }

    public static final class ClientSettings {
        private Tag tag;

        public void setTag(Tag tag) {
            this.tag = tag;
        }

        public Tag getTag() {
            return tag;
        }
    }

    public static final class TagEditor extends PropertyEditorSupport {
        @Override
        public void setAsText(String text) {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("tag must not be blank");
            }
            setValue(new Tag(
                    "ED:" + text.trim().toUpperCase(Locale.ROOT)));
        }

        @Override
        public String getAsText() {
            Tag value = (Tag) getValue();
            return value == null ? "" : value.code();
        }
    }

    public static final class TagConverter
            implements Converter<String, Tag> {

        @Override
        public Tag convert(String source) {
            String cleaned = source.trim();
            if (cleaned.isEmpty()) {
                throw new IllegalArgumentException("tag must not be blank");
            }
            return new Tag("CV:" + cleaned.toLowerCase(Locale.ROOT));
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class LegacyConfig {

        @Bean
        public static CustomEditorConfigurer legacyEditors() {
            CustomEditorConfigurer configurer =
                    new CustomEditorConfigurer();
            Map<Class<?>, Class<? extends PropertyEditor>> types =
                    new HashMap<>();
            types.put(Tag.class, TagEditor.class);
            configurer.setCustomEditors(types);
            return configurer;
        }

        @Bean
        public static BeanFactoryPostProcessor legacyProperty() {
            return factory -> factory.getBeanDefinition("settings")
                    .getPropertyValues().add("tag", "  JAVA  ");
        }

        @Bean("settings")
        public ClientSettings settings() {
            return new ClientSettings();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ModernConfig {

        // Tên conversionService là quy ước Spring BeanFactory nhận diện.
        @Bean("conversionService")
        public static ConversionService conversionService() {
            var service = new DefaultConversionService();
            service.addConverter(
                    String.class, Tag.class, new TagConverter());
            return service;
        }

        @Bean
        public static BeanFactoryPostProcessor modernProperty() {
            return factory -> factory.getBeanDefinition("settings")
                    .getPropertyValues().add("tag", "  JAVA  ");
        }

        @Bean("settings")
        public ClientSettings settings() {
            return new ClientSettings();
        }
    }
}
```

### 7.4. Lệnh chạy

Đứng tại thư mục có `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.PropertyEditorDemo
```

Các dòng `System.out.println` dự kiến, không tính các thông báo của Maven hoặc logging hệ thống:

```text
direct editor: ED:JAVA
legacy bean: ED:JAVA
modern bean: CV:java
modern canConvert: true
binder: ED:SPRING
```

### 7.5. Tại sao kết quả như vậy?

- **`direct editor`**: gọi `TagEditor.setAsText("  java  ")`, trim và uppercase; `getAsText()` trả `ED:JAVA`.
- **`legacy bean`**: `legacyProperty` (một `BeanFactoryPostProcessor`) đặt property value dạng chuỗi trong bean definition `settings`. `CustomEditorConfigurer` đăng ký editor class cho `Tag`. Khi BeanFactory tạo `ClientSettings`, nó convert `"  JAVA  "` bằng `TagEditor` và gọi setter.
- **`modern bean`**: context độc lập khai báo một `ConversionService` tên `conversionService` có `TagConverter`. Khi tạo `ClientSettings`, hệ thống chuyển cùng chuỗi thành `Tag("CV:java")` qua converter.
- **`modern canConvert`**: service có converter được đăng ký từ `String` sang `Tag`, nên trả `true`.
- **`binder`**: một `DataBinder` độc lập được đăng ký editor trực tiếp, nên chuỗi `spring` thành `Tag("ED:SPRING")`.

Cả hai bean definitions `settings` đều nằm trong **hai contexts khác nhau**, nên không có xung đột tên. `CustomEditorConfigurer` và `BeanFactoryPostProcessor` methods là **`static @Bean`** để Spring chuẩn bị sớm. Dữ liệu chuỗi được thêm vào **bean definition metadata trước khi tạo instance**, không phải gọi setter trên object đã tồn tại.

### 7.6. Lưu ý về thứ tự processor và factory method

Không cần dựa vào việc `legacyEditors` chạy trước hay sau `legacyProperty`, vì một processor **đăng ký editor**, còn processor kia **chỉnh metadata property values**; cả hai hoàn tất trước khi container pre-instantiate `settings`. Khi bean được tạo, Spring áp metadata đã chuẩn bị để gọi setter.

`@Bean("settings")` gọi `new ClientSettings()` qua factory method, nhưng Spring vẫn có thể áp `BeanDefinition.propertyValues` trong property-population phase sau khi lấy instance. Tuy nhiên, nếu bạn chỉ có constructor parameter mà không có writable `setTag(Tag)`, thêm property value tên `tag` như demo **không tự biến thành constructor argument**.

### 7.7. Cách tái hiện một lỗi conversion có chủ đích

Trong `LegacyConfig`, nếu bỏ `legacyEditors` mà vẫn giữ `legacyProperty` cung cấp chuỗi cho `Tag` và không có custom converter nào khác, Spring có thể thất bại khi tạo `settings`: không có đường `String → Tag` phù hợp.

Trong `ModernConfig`, nếu đổi bean name `conversionService` thành `randomService` rồi không gắn service vào BeanFactory bằng cách khác, conversion service đó **không tự trở thành cơ chế conversion chung** của bean factory chỉ vì một bean có kiểu `ConversionService` tồn tại.

Đây là các **thí nghiệm thay thế có chủ đích**, không phải cách sửa chương trình chính. Kết quả exception có thể gồm `BeanCreationException`, `TypeMismatchException`, `ConversionFailedException` hoặc exception được wrap theo đường execution, nên cần xem root cause và target property.

## 8. ConversionService — hướng tiếp cận hiện đại

`PropertyEditor` giữ state hiện thời thông qua `setAsText`/`getValue`. `ConversionService` diễn đạt trực tiếp cặp source-target và trả object mới:

```java
ConversionService cs = modern.getBean(
        "conversionService", ConversionService.class);
Tag tag = cs.convert("  SPRING  ", Tag.class);
```

Trong demo, `Converter<String, Tag>` được đăng ký trả một `Tag` với `code() = "CV:spring"`. Converter nên stateless/thread-safe khi được dùng chung; không cần lưu một current value như editor. `ConverterFactory` dùng cho họ target types, `GenericConverter` và `TypeDescriptor` cho metadata, còn `Formatter` hỗ trợ locale khi có FormattingConversionService phù hợp.

Bài ConversionService trước đã giảng đầy đủ SPI và custom registration. Phần so sánh này giúp ra quyết định giữ lại editor legacy hay chuyển sang converter có hợp đồng type rõ và kiểm thử được.

## 9. BeanFactory editor, DataBinder và ConversionService: dùng cái nào ở đâu?

| Vị trí conversion | API lịch sử | API được khuyến nghị cân nhắc |
|---|---|---|
| Spring bean definition property values | `PropertyEditor`, `CustomEditorConfigurer` | `ConversionService` hoặc constructor/factory-method nhận đúng type |
| Form/request data binding | `DataBinder.registerCustomEditor`, `@InitBinder` | `FormattingConversionService`, MVC converters/formatters |
| Service cần chuyển kiểu programmatically | Tự tạo editor, gọi `setAsText/getValue` | Inject `ConversionService`, dùng `convert` |
| Domain object từ một `String` | `PropertyEditorSupport` | `Converter<String,T>` hoặc factory/value-object constructor |
| Locale-aware parse/print | Custom editor tự quản state/locale | `Formatter<T>` |
| Typed nested data binding | Editor path-specific hoặc binder behavior | ConversionService + formatters + validation theo use case |
| Validation sau conversion | `PropertyEditor` báo parse error | Bean Validation/domain validation, không đồng nhất với converter |

**Không có nghĩa PropertyEditor đã bị xóa khỏi Spring.** Hệ sinh thái còn API legacy để bảo đảm backward compatibility và hỗ trợ các đường cấu hình còn sử dụng. Chọn giải pháp dựa trên dự án đang bảo trì: sửa code cũ với một editor đã đăng ký có thể hợp lý, nhưng khi thiết kế API mới nên tránh tăng nợ kỹ thuật bằng editor stateful nếu `Converter` giải quyết được rõ hơn.

### 9.1. Vì sao không đăng ký đồng thời hai converter cho cùng target type để “đảm bảo”?

Một `CustomEditorConfigurer` cho `Tag` và một `ConversionService` cũng có `String → Tag` có thể tạo ra **quy tắc chọn conversion phụ thuộc đường gọi**. Property editor path/type registration có thể được ưu tiên trong một số đường property binding; nhưng public contract của conversion service trực tiếp vẫn dùng converter đã đăng ký.

Tránh yêu cầu nghiệp vụ “giá trị phải convert y hệt dù đi qua editor hoặc converter” nếu hai implementations đang có policies khác nhau. Nếu cần migration song song, hãy viết compatibility tests và chủ động xóa editor cũ khỏi các registries thích hợp khi chuyển sang converter.

### 9.2. Khi nào không cần editor hay converter?

Nếu cấu hình bean bằng Java, phương pháp đơn giản có thể là truyền **đúng kiểu** từ đầu:

```java
@Bean
ClientSettings settings() {
    ClientSettings s = new ClientSettings();
    s.setTag(new Tag("SPRING"));
    return s;
}
```

Đây là snippet tích hợp, yêu cầu các classes từ demo. Không có String → Tag conversion tại runtime vì code đã xây `Tag` trực tiếp. Constructor injection với typed dependency thường càng rõ ràng hơn. Đừng thêm framework conversion layer khi vấn đề chỉ là một object dễ tạo bằng Java thuần.

## 10. PropertyEditorRegistrar và thread safety sâu hơn

Javadoc của `PropertyEditorRegistrar` nói rõ implementation được kỳ vọng tạo **editor instances mới cho mỗi lần registrar được gọi**, vì `PropertyEditor` không thread-safe.

Sai:

```java
public final class UnsafeTagRegistrar
        implements PropertyEditorRegistrar {

    private final TagEditor shared = new TagEditor();

    @Override
    public void registerCustomEditors(PropertyEditorRegistry registry) {
        registry.registerCustomEditor(Tag.class, shared);
    }
}
```

Đây là **anti-pattern minh họa**. Nếu registrar được dùng qua nhiều registries/binding operations, tất cả có thể nhận cùng một object editor stateful; race conditions hoặc rò rỉ value giữa những operations trở thành nguy cơ.

Đúng:

```java
public final class SafeTagRegistrar
        implements PropertyEditorRegistrar {

    @Override
    public void registerCustomEditors(PropertyEditorRegistry registry) {
        registry.registerCustomEditor(Tag.class, new TagEditor());
    }
}
```

Đây là snippet tích hợp thay thế. Các snippets cần imports `PropertyEditorRegistrar`, `PropertyEditorRegistry`, `TagEditor`; không nên dán cùng lúc cả hai public classes vào một file Java.

**Một điểm tinh tế:** đăng ký **class** của editor bằng `CustomEditorConfigurer.setCustomEditors` làm Spring có thể tạo fresh editors; đăng ký editor **instance** lên một `DataBinder` cụ thể phù hợp nếu binder sống trong một binding operation và không bị share giữa threads.

### 10.1. Thread-safe Converter có nghĩa gì?

`Converter<String,Tag>` trong demo không có mutable fields, chỉ xử lý giá trị `source` và tạo `Tag`. Như vậy nó phù hợp để được `ConversionService` dùng lại.

Nếu một converter chứa `SimpleDateFormat` dùng chung không đồng bộ, nó vẫn có thể không thread-safe. Khi xử lý ngày giờ, ưu tiên `java.time.format.DateTimeFormatter` bất biến hoặc tạo per-call state phù hợp. **Không đánh giá thread safety chỉ từ tên interface.**

## 11. Lỗi thường gặp và cách gỡ

| Hiểu nhầm / lỗi | Thực tế | Hướng xử lý |
|---|---|---|
| “PropertyEditor là class của Spring” | Interface `java.beans.PropertyEditor` thuộc JDK/JavaBeans | Import đúng; dùng `PropertyEditorSupport` khi implement |
| “Editor stateless như Converter” | Nó thường lưu current value qua `setValue/getValue` | Không share editor instance giữa threads |
| “Chỉ cần viết class TagEditor là Spring tự biết dùng” | Cần editor registration/path hợp lệ | Dùng `CustomEditorConfigurer` hoặc binder registration |
| “CustomEditorConfigurer áp dụng cho mọi DataBinder” | Nó cấu hình BeanFactory; binder có registry riêng | Register trên binder hoặc dùng registrar chung |
| “Lấy Tag bằng String property thì Spring sẽ tự gọi Tag(String)” | Không có bảo đảm tự suy đoán mọi constructor | Khai báo converter/editor/typed factory |
| “Đặt PropertyEditor thành singleton bean là an toàn” | Mutable value có thể gây race/leak | Register editor class, tạo instances riêng |
| “Custom editor phải được gọi đúng một lần trong mọi bean lifecycle” | Có thể nhiều property conversions hoặc phases | Không phụ thuộc invocation count |
| “ConversionService tự phát hiện Converter beans bất kỳ” | Cần đăng ký converters trong một service/registry phù hợp | Khai báo `conversionService` đúng quy ước/cấu hình |
| “MapPropertySource tự biến String thành domain object” | Property source cung cấp value, conversion là tầng khác | Phân biệt lookup và conversion |
| “@Value sẽ thấy dữ liệu mới ngay khi editor registration đổi” | Injection của singleton đã tạo không tự chạy lại | Thiết kế reload/rebinding riêng |
| “PropertyEditor và Validator là một” | Conversion và validation khác trách nhiệm | Validate sau parsing |
| “Mọi bean property đều writable dù thiếu setter” | JavaBeans population yêu cầu property phù hợp | Thêm setter hoặc đổi sang constructor/factory args |
| “String bad value sẽ luôn fallback về default” | Giá trị có nhưng sai format có thể gây exception | Fail-fast, báo đúng input/key |
| “Formatter chỉ là tên khác của PropertyEditor” | Formatter có typed parse/print và Locale | Dùng `FormattingConversionService` |
| “Converter tự thread-safe bởi interface” | Implementation vẫn có thể stateful | Thiết kế stateless/immutable hoặc đồng bộ đúng |
| “Cùng custom editor và converter sẽ luôn có cùng precedence ở mọi nơi” | Tùy registry và đường chuyển đổi cụ thể | Test path, tránh registration trùng |
| “CustomEditorConfigurer cấu hình muộn cũng ảnh hưởng bean đã tạo” | BFPP áp dụng ở bootstrap; không retroactively rebind | Đăng ký trước singleton creation |
| “Chuyển String sang int thành công là business value hợp lệ” | Chưa có range/cross-field validation | Áp validation riêng |

### 11.1. Checklist debug theo thứ tự

1. **Target bean có thực sự được Spring quản lý không?** Nó có `BeanDefinition`/factory method và được container tạo, hay code đang tự `new`?
2. **Property có writable JavaBeans setter không?** Kiểm tra `setTag(Tag)` hoặc binding path phù hợp.
3. **Nguồn là text hay object đúng type?** Không giả định mọi `String` cần editor nếu factory method đã nhận typed object.
4. **Conversion xảy ra ở đâu?** BeanFactory property population, constructor/`@Value`, `DataBinder`, Spring MVC, hay service gọi `ConversionService` trực tiếp?
5. **Editor có được đăng ký vào đúng registry không?** `CustomEditorConfigurer` cho factory, `registerCustomEditor` cho binder.
6. **Nếu dùng configurer, có đăng ký editor class/registrar, tránh shared instance không?**
7. **Nếu dùng ConversionService, service có được gắn vào bean factory không?** Với Java Config, name `conversionService` có ý nghĩa trong đường bootstrap chuẩn.
8. **Target/source types có khớp không?** `Converter<String,Tag>` không đồng nghĩa converter `Integer → Tag`.
9. **Conversion thất bại do giá trị trống, format sai hay classpath dependency?** Kiểm tra root cause và property name.
10. **Có editor và converter trùng nhau không?** Xem registry và path precedence.
11. **Có race condition do tái sử dụng một PropertyEditor instance không?** Tách editor theo binding operation.
12. **Có kỳ vọng thay config sau refresh khiến singleton được convert lại không?** Điều đó không tự xảy ra.
13. **Có locale hoặc time zone cần xét không?** Dùng Formatter/chính sách explicit nếu cần.
14. **Sau conversion, validation đã được áp dụng chưa?** Đừng bỏ qua lỗi domain invariants.

## 12. Hướng migration từ PropertyEditor sang Converter

Nếu một ứng dụng Spring cũ có nhiều custom editors, thay thế nên có kiểm soát:

**Bước 1 — Khoanh vùng đường sử dụng.** Ghi lại editor nào chỉ áp dụng cho `BeanFactory`, editor nào cho `DataBinder`/MVC, editor nào được dùng trực tiếp. Kiểm tra các registration như `CustomEditorConfigurer`, `@InitBinder`, `PropertyEditorRegistrar`.

**Bước 2 — Viết hợp đồng conversion.** Với `TagEditor`, xác định input hợp lệ, normalization (`trim`/uppercase), xử lý null/blank, error messages và output. Không tự thay đổi semantics trong lúc migration nếu ứng dụng dựa vào case normalization cũ.

**Bước 3 — Tạo typed Converter hoặc Formatter.** Nếu dữ liệu là chuyển kiểu không có locale, viết `Converter<String,Tag>`. Nếu là date/currency/user-facing text theo locale, chọn `Formatter<Tag>`.

**Bước 4 — Đăng ký vào đúng ConversionService.** Với BeanFactory, dùng bean `conversionService` trong context chuẩn hoặc cấu hình trực tiếp qua APIs phù hợp. Với MVC, dùng registry/formatter setup của MVC hoặc Spring Boot.

**Bước 5 — Viết tests đường cũ và mới.** Test whitespace, empty input, invalid format, case, escaping, collections và bindings. Kiểm tra cả exception semantics nếu caller cần hiển thị thông báo hợp lệ.

**Bước 6 — Loại bỏ editor registration trùng mục tiêu.** Đừng để editor cũ và converter mới chạy hai chính sách khác nhau ở hai path mà không biết. Migration từng đường rõ ràng sẽ giảm lỗi âm thầm.

**Bước 7 — Kiểm tra thread safety và performance.** Converter thường được share, nên không lưu mutable parser hay “giá trị hiện tại” trong instance. Đối với xử lý text format, cân nhắc caches immutable hoặc per-call objects.

**Bước 8 — Giữ documentation thực tế.** Nếu code legacy vẫn dùng `PropertyEditor` vì thư viện bắt buộc, không ép xóa bằng mọi giá; ghi rõ phạm vi và giới hạn để tránh lỗi khi phần mới dùng `ConversionService`.

## 13. Câu hỏi tự kiểm tra

**1. `PropertyEditor` thuộc package Spring nào?**  
Không thuộc Spring; nó là `java.beans.PropertyEditor` của JavaBeans trong JDK.

**2. Vì sao `PropertyEditor` được gọi là cơ chế cũ có trạng thái?**  
Vì `setAsText` thường ghi một object vào current value của editor và caller đọc lại bằng `getValue()`.

**3. `PropertyEditorSupport` hỗ trợ gì?**  
Nó là base class implement các hành vi cơ bản của `PropertyEditor`, cho phép override `setAsText/getAsText` thay vì tự implement mọi method.

**4. Làm thế nào để Spring BeanFactory chuyển `String` sang domain `Tag` trong bean property population?**  
Đăng ký một editor/converter phù hợp, ví dụ `CustomEditorConfigurer` với `TagEditor.class` hoặc một `ConversionService` có `Converter<String,Tag>`.

**5. `CustomEditorConfigurer` là `BeanPostProcessor` hay `BeanFactoryPostProcessor`?**  
Nó là `BeanFactoryPostProcessor`, đăng ký editors trong quá trình chuẩn bị bean factory, trước khi các application beans thông thường được tạo.

**6. Vì sao không nên lưu `TagEditor` thành một singleton và dùng cho mọi request?**  
Editor lưu mutable current value, nên nhiều request/thread có thể ghi đè state nhau.

**7. `CustomEditorConfigurer` có tự đăng ký editor cho mọi `DataBinder` không?**  
Không. DataBinder có registry/binding context riêng; cần đăng ký trực tiếp hoặc thông qua common registrar/binder configuration thích hợp.

**8. `Converter<String,Tag>` khác `PropertyEditor` chủ yếu ở đâu?**  
Converter type-safe qua generics và trả output trực tiếp mà không cần giữ “current value” trong editor state.

**9. Có thể dùng `ConversionService` bên ngoài BeanFactory không?**  
Có. Đây là API công khai; inject service và gọi `convert` trong ứng dụng hoặc dùng với binder/MVC khi được cấu hình.

**10. Khi nào nên dùng `Formatter<T>` thay vì `Converter`?**  
Khi cần parse/print giá trị trong ngữ cảnh locale, ví dụ số và ngày giờ hiển thị cho người dùng.

**11. Một editor chuyển `"5000"` thành số thành công đã chứng minh timeout hợp lệ chưa?**  
Chưa. Cần validation range và chính sách nghiệp vụ.

**12. Nếu `setTag(Tag)` không tồn tại, custom editor có tự biến field `tag` thành writable property không?**  
Không. Cơ chế JavaBeans property population cần writable path phù hợp hoặc phải dùng một cách injection/creation khác.

**13. Vì sao hai contexts trong demo có cùng bean name `settings` mà không xung đột?**  
Mỗi `ApplicationContext` có bean factory/registry riêng; chúng không chia sẻ definition name space ở cấp JVM.

**14. Khi nào editor được gọi trong lifecycle Spring?**  
Khi container cần convert giá trị để điền property hoặc khi binder thực hiện conversion; không phải một callback được gọi mỗi lần business method chạy.

**15. Editor và ConversionService có tự đọc `application.properties` không?**  
Không. Chúng giải bài toán **type conversion**; việc nạp/resolve property sources là trách nhiệm của `Environment` và cấu hình liên quan.

## Tài liệu chính thức

- [Spring Framework 6.2 — Spring Type Conversion](https://docs.spring.io/spring-framework/reference/6.2/core/validation/convert.html): SPI `Converter`, `ConversionService`, default conversions và recommended type-conversion model.
- [Spring Framework 6.2 — Data Binding / BeanWrapper and PropertyEditors](https://docs.spring.io/spring-framework/reference/6.2/core/validation/beans-beans.html): Spring BeanWrapper, JavaBeans property editing và binding concepts.
- [Spring Framework 6.2 — PropertyEditors](https://docs.spring.io/spring-framework/reference/6.2/core/validation/beans-beans.html#beans-beans-conversion): cấu hình property editor trong các use cases legacy.
- [Spring Framework 6.2 API — CustomEditorConfigurer](https://docs.spring.io/spring-framework/docs/6.2.0/javadoc-api/org/springframework/beans/factory/config/CustomEditorConfigurer.html): đăng ký classes/registrars, lưu ý stateful và phạm vi khác với `DataBinder`.
- [Spring Framework 6.2 API — PropertyEditorRegistrar](https://docs.spring.io/spring-framework/docs/6.2.0/javadoc-api/org/springframework/beans/PropertyEditorRegistrar.html): tạo fresh editor cho registry.
- [Spring Framework 6.2 API — DataBinder](https://docs.spring.io/spring-framework/docs/6.2.0/javadoc-api/org/springframework/validation/DataBinder.html): `registerCustomEditor`, conversion và data binding.
- [Spring Framework 6.2 — Spring Field Formatting](https://docs.spring.io/spring-framework/reference/6.2/core/validation/format.html): `Formatter<T>`, parser/printer và `FormattingConversionService`.
- [Java SE 17 API — PropertyEditor](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/java/beans/PropertyEditor.html): giao diện JavaBeans và các methods.
- [Java SE 17 API — PropertyEditorSupport](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/java/beans/PropertyEditorSupport.html): base class hỗ trợ editor.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="values-and-expressions-007">Phân biệt placeholder ${...} với biểu thức SpEL #{...}</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt placeholder ${...} với biểu thức SpEL #{...} trong Spring Framework

## 1. Hai cú pháp trông giống nhau nhưng khác chức năng

Trong Spring IoC Container, cả property placeholder và Spring Expression Language đều có thể xuất hiện trong annotation `@Value`:

~~~java
@Value("${app.port:8080}")
private int port;

@Value("#{2 + 3 * 4}")
private int computed;
~~~

Dòng thứ nhất tra **thuộc tính cấu hình** có tên `app.port`, sử dụng default `8080` khi không có thuộc tính. Dòng thứ hai đánh giá **biểu thức số học** trong SpEL rồi trả số `14`. Hai cơ chế không thay thế nhau.

> **Cần nhớ:** `${...}` chủ yếu dành cho **property resolution**; `#{...}` chủ yếu dành cho **expression evaluation**. Nếu cần một giá trị cấu hình, ưu tiên placeholder. Nếu thật sự cần tính toán hoặc truy cập object graph qua expression, dùng SpEL một cách có kiểm soát.

Bài này giải thích hai cơ chế trong ngữ cảnh Spring Framework Core Container: syntax, nguồn giá trị, conversion, fallback, nested expressions, evaluation context, bean references, cách phối hợp, lỗi thường gặp và lưu ý bảo mật. Một project **Java 17 + Spring Context 6.1.13** ở phần sau có thể chạy độc lập qua Maven mà không cần Spring Boot.

## 2. Bảng so sánh trực tiếp

| Tiêu chí | Placeholder `${...}` | SpEL `#{...}` |
| --- | --- | --- |
| Chức năng | Tìm key và thay giá trị | Phân tích và đánh giá biểu thức |
| Ví dụ đơn giản | `${app.name:demo}` | `#{2 + 3}` |
| Dữ liệu | `Environment`, `PropertySource` | Evaluation context: objects, literals, methods, types, bean resolver |
| Giá trị mặc định | `${key:default}` | Ternary `condition ? a : b` hoặc Elvis `expr ?: fallback` |
| Số học | Không tự tính `1 + 2` | Có toán học và nhiều operators |
| Truy cập bean qua tên | Không, trừ khi property source thực sự chứa key tương ứng | `@beanName` khi context có BeanResolver |
| Gọi Java method | Không | Có, nếu evaluation context cho phép |
| Loại kết quả | Giá trị resolved, thường là String cần conversion | Object/number/boolean/collection... theo expression |
| Xử lý trực tiếp | `PropertyResolver` | `ExpressionParser` |
| Dùng ở `@Value` | Có | Có |
| Có tự chạy trong Java String literal? | Không | Không |
| Mối nguy chính | Thiếu key, precedence, default sai | Parse/evaluation error, side effects, expression injection |

Ví dụ trọng yếu:

~~~text
${2 + 3}     tìm một property có key tên "2 + 3"
#{2 + 3}     tính 2 + 3 -> 5
~~~

Dấu ngoặc giống nhau không có nghĩa chúng cùng một parser.

## 3. Placeholder lấy giá trị từ đâu?

`${app.port:8080}` là property lookup từ `Environment` / `PropertySources`, không phải phép tính. Nếu `app.port=9090` thì kết quả là 9090; khi key vắng mặt, default có thể là 8080; nếu value tồn tại nhưng rỗng, fallback thường không tự thay thế.

`${3 * 4:0}` tìm key mang tên `3 * 4` (hoặc trả default), trong khi `#{3 * 4}` mới đánh giá phép nhân ra 12. Đây là sự khác biệt cần giữ trong bài so sánh. Các nguồn ưu tiên và nested placeholder đã được trình bày ở bài trước.

## 4. Default syntax `${key:default}`

Dấu `:` trong `${server.port:8080}` là value separator của placeholder, không phải ternary/Elvis trong SpEL. Nested fallback `${primary.url:${backup.url:http://localhost:8080}}` và nested key `${service.url.${app.env:dev}:http://localhost}` vẫn chỉ là thao tác **tạo/tra property key**, không gọi object methods.

Nếu nhiều cấp nested form làm khó hiểu chính sách cấu hình, hãy dùng typed configuration bean và Java logic. Bài placeholder chuyên sâu giữ ví dụ đầy đủ về nested default, key và property values.

## 5. API liên quan property resolution

`getProperty("app.mode")` đọc key; `getRequiredProperty("app.name")` đòi value tồn tại. `resolvePlaceholders("mode=${app.mode}")` có thể giữ token còn thiếu, còn `resolveRequiredPlaceholders(...)` ném `IllegalArgumentException` nếu thiếu key bắt buộc.

`PropertySource.getProperty(key)` lấy raw value từ một source; `Environment.getProperty(key)` tra nhiều sources theo precedence và có thể resolve placeholder nằm trong value. Không API nào trong nhóm này tự gọi SpEL method. Bài phát hiện placeholder chưa được giải quyết trình bày rõ exception và strict configurer.

## 6. SpEL tính toán thay vì chỉ tra property

SpEL có cú pháp bean expression `#{...}`:

~~~text
#{2 + 3 * 4}                     -> 14
#{10 > 3}                        -> true
#{'spring'.toUpperCase()}        -> SPRING
#{true ? 'yes' : 'no'}           -> yes
#{null ?: 'fallback'}            -> fallback
#{T(java.lang.Math).max(5, 9)}   -> 9
~~~

Nó hỗ trợ literal, toán tử, type references, Java method invocation, object property access, bean references, variables, collections, selection/projection và template expressions. Các tính năng có thể khác đôi chút giữa Spring Framework versions; ví dụ trong bài dùng những khả năng được hỗ trợ ở Spring 6.1.

### SpEL không tự nhìn thấy mọi Environment key

`#{app.port}` không phải cú pháp thay thế cho `${app.port}`. SpEL có thể hiểu `app.port` là truy cập thuộc tính `port` của đối tượng `app` trong evaluation context. Nếu không có object/path như vậy, nó có thể báo lỗi.

Dùng `${app.port:8080}` để tra một configuration key. Nếu muốn tính thêm từ giá trị đó, có thể phối hợp placeholder và SpEL theo cơ chế được mô tả sau.

## 7. Fallback của SpEL: ternary và Elvis

Trong SpEL, toán tử điều kiện:

~~~text
#{10 > 5 ? 'high' : 'low'}   -> high
~~~

Toán tử Elvis:

~~~text
#{null ?: 'fallback'}       -> fallback
~~~

Với một expression có value `null`, Elvis dùng fallback. Spring SpEL còn có xử lý empty String theo hợp đồng Elvis của framework; điều này khác với placeholder default vốn thường chỉ dùng khi không tìm thấy property value.

Ví dụ sử dụng một object `systemProperties` mà Spring bean evaluation context expose:

~~~java
@Value("#{systemProperties['app.mode'] ?: 'local'}")
private String mode;
~~~

Cần import `Value` và Spring-managed bean. Expression trên **đọc JVM system properties map**, không tương đương tra tất cả `Environment.PropertySources` như `${app.mode:local}`.

| Biểu thức | Câu hỏi |
| --- | --- |
| `${app.mode:local}` | Environment có key `app.mode` không? |
| `#{systemProperties['app.mode'] ?: 'local'}` | Object/map systemProperties có giá trị phù hợp không? |
| `#{10 > 3 ? 'large' : 'small'}` | Condition đúng hay sai? |

Không suy luận rằng dấu `:` trong placeholder là SpEL ternary operator.

## 8. Tham chiếu bean bằng SpEL `@beanName`

Khi expression context có `BeanResolver`, `@Value("#{@pricingService.baseRate()}")` có thể tìm bean và gọi method của nó. `@Value("${pricingService.baseRate}")` chỉ tìm key trong `Environment`: cùng tên nhìn thấy, nhưng khác cơ chế.

Gọi method từ SpEL khi bean consumer được tạo có thể kéo các dependency khởi tạo sớm hoặc side effects. Với logic nghiệp vụ, constructor injection nhận `PricingService` thường bộc lộ dependency rõ hơn. Bài SpEL tiếp theo đi sâu vào bean names, List/Map navigation, property selection và standalone `BeanFactoryResolver`.

## 9. Tham chiếu type và method trong SpEL

SpEL hỗ trợ type reference khi evaluation context cho phép:

~~~java
@Value("#{T(java.lang.Math).max(5, 9)}")
private int maxValue;

@Value("#{'catalog'.toUpperCase()}")
private String upper;
~~~

Kết quả là `9` và `"CATALOG"`.

Đây là lý do SpEL **không phải một công cụ thay chuỗi vô hại**. Nó có thể có quyền gọi method/static method và truy cập object graph. Nếu bạn evaluate SpEL không đáng tin bằng `StandardEvaluationContext` đầy quyền, có thể tạo rủi ro thực thi thao tác ngoài ý muốn hoặc truy cập dữ liệu nhạy cảm.

**Khuyến nghị bảo mật:** Không lấy raw expression từ request/user input rồi gọi `parseExpression(...).getValue(...)` với context mạnh. Dùng whitelist hoặc `SimpleEvaluationContext` được cấu hình hạn chế phù hợp; nếu yêu cầu chỉ là nhập số/config, ưu tiên parser dữ liệu thông thường và validation.

## 10. Kết hợp hai cơ chế trong một biểu thức

Với Spring-managed `@Value`, có thể viết:

~~~java
@Value("#{${worker.base:2} * 3}")
private int threads;
~~~

Nếu property `worker.base=4`, embedded placeholder resolver thay giá trị vào chuỗi expression, ví dụ:

~~~text
#{4 * 3}
~~~

Sau đó SpEL đánh giá thành `12` và Spring inject vào field `int`.

### Mô hình xử lý

~~~text
@Value("#{${worker.base:2} * 3}")
     |
     v
Property resolver tìm worker.base hoặc default
     |
     v
"#{4 * 3}"
     |
     v
SpEL BeanExpressionResolver
     |
     v
Number 12
     |
     v
Conversion / injection vào target bean
~~~

Đây là pipeline tiêu biểu cho `@Value` trong Spring context đúng cấu hình. **Không phải mọi API nhận String đều tự động chạy cả hai resolver.**

### Rủi ro trộn raw config vào expression

Nếu `worker.base` chứa text không phải số hợp lệ, nó có thể gây SpEL parse error hoặc thay đổi ý nghĩa expression.

An toàn hơn khi logic quan trọng:

~~~java
public WorkerSettings(
        @Value("${worker.base:2}") int base) {
    this.threads = Math.multiplyExact(base, 3);
}
~~~

Đoạn constructor minh họa cần field `threads` và class `WorkerSettings`. Spring convert/validate base như số trước; Java phép nhân thể hiện ý đồ rõ ràng, có xử lý tràn số.

**Không đưa giá trị do người dùng không tin cậy kiểm soát trực tiếp vào expression source để SpEL evaluate.** Kiểm soát cả quyền của evaluation context, type conversion và điều kiện input.

## 11. `@Value` hỗ trợ cả hai cú pháp

Ví dụ một class được Spring quản lý:

~~~java
@Component
public class ServerInfo {
    private final String address;
    private final int capacity;

    public ServerInfo(
            @Value("${server.address:localhost}") String address,
            @Value("#{2 * 8}") int capacity) {
        this.address = address;
        this.capacity = capacity;
    }
}
~~~

Đây là class minh họa cần import `Component`, `Value` và component scanning/bean registration phù hợp.

`address` đến từ Environment. `capacity` đến từ SpEL.

### Conversion và target type

Trong:

~~~java
@Value("${server.port:8080}")
int port;
~~~

property value thường được resolve dưới dạng String rồi chuyển sang `int`. Nếu source có `server.port=not-a-number`, default không được dùng và conversion có thể thất bại.

Trong:

~~~java
@Value("#{2 * 4}")
int port;
~~~

SpEL tạo numeric value 8; Spring có thể inject trực tiếp hoặc chuyển kiểu theo target parameter.

Kết quả field có cùng kiểu không làm hai cơ chế giống nhau. Cần phân biệt **dữ liệu lấy từ đâu** và **thao tác nào được áp dụng**.

## 12. Ví dụ Java 17 đầy đủ: hai cú pháp, một Spring Context

Chương trình sau dùng Spring Context 6.1.13, Maven và `MapPropertySource`. Nó minh họa:

- Placeholder bình thường và default.
- Placeholder trong key và trong value một property khác.
- SpEL toán học, gọi method String và bean method.
- Kết hợp giá trị placeholder vào SpEL.
- Elvis fallback.
- Sự khác biệt giữa Environment API và standalone SpEL parser.

### 12.1. Cấu trúc

~~~text
placeholder-spel-demo/
├── pom.xml
└── src/main/java/com/example/expression/PlaceholderSpelDemo.java
~~~

### 12.2. `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>placeholder-spel-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.1.13</version>
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
                <configuration>
                    <mainClass>com.example.expression.PlaceholderSpelDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 12.3. `PlaceholderSpelDemo.java`

~~~java
package com.example.expression;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

public class PlaceholderSpelDemo {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx =
                 new AnnotationConfigApplicationContext()) {

            ctx.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("lesson243", Map.of(
                    "lesson243.name", "catalog",
                    "lesson243.port", "8080",
                    "lesson243.stage", "dev",
                    "lesson243.endpoint.dev", "https://dev.example.test",
                    "lesson243.multiplier", "4",
                    "lesson243.raw", "server-${lesson243.port}"
                ))
            );

            ctx.register(AppConfig.class);
            ctx.refresh();

            View view = ctx.getBean(View.class);

            System.out.println("placeholder-name=" + view.name());
            System.out.println("placeholder-default=" + view.mode());
            System.out.println("nested-key=" + view.endpoint());
            System.out.println("nested-value=" + view.raw());
            System.out.println("spel-math=" + view.math());
            System.out.println("spel-method=" + view.upper());
            System.out.println("spel-bean=" + view.fromBean());
            System.out.println("mixed-value=" + view.mixed());
            System.out.println("spel-elvis=" + view.elvis());

            Environment env = ctx.getEnvironment();
            System.out.println("api-placeholder=" +
                env.resolveRequiredPlaceholders("${lesson243.name}"));

            boolean missingIsError = false;
            try {
                env.resolveRequiredPlaceholders("${lesson243.missing}");
            } catch (IllegalArgumentException expected) {
                missingIsError = true;
            }
            System.out.println("api-required-error=" + missingIsError);

            ExpressionParser parser = new SpelExpressionParser();
            Integer standalone = parser
                .parseExpression("2 + 3 * 4")
                .getValue(Integer.class);
            System.out.println("api-spel=" + standalone);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {

        @Bean
        public static PropertySourcesPlaceholderConfigurer
                propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean("numberSource")
        public NumberSource numberSource() {
            return new NumberSource(6);
        }

        @Bean
        public View view(
                @Value("${lesson243.name}") String name,
                @Value("${lesson243.optional:local}") String mode,
                @Value("${lesson243.endpoint.${lesson243.stage}}")
                    String endpoint,
                @Value("${lesson243.raw}") String raw,
                @Value("#{2 + 3 * 4}") int math,
                @Value("#{'catalog'.toUpperCase()}") String upper,
                @Value("#{@numberSource.doubleValue()}") int fromBean,
                @Value("#{${lesson243.multiplier:2} * 10}") int mixed,
                @Value("#{null ?: 'fallback-spel'}") String elvis) {

            return new View(name, mode, endpoint, raw,
                            math, upper, fromBean, mixed, elvis);
        }
    }

    public static class NumberSource {
        private final int base;

        public NumberSource(int base) {
            this.base = base;
        }

        public int doubleValue() {
            return base * 2;
        }
    }

    public record View(String name, String mode, String endpoint,
                       String raw, int math, String upper,
                       int fromBean, int mixed, String elvis) {
    }
}
~~~

**Lưu ý runnable:** `AppConfig` là nested static configuration class; `@Bean` factory method trả `PropertySourcesPlaceholderConfigurer` được đánh dấu static để tạo infrastructure sớm. Tên bean `numberSource` được tham chiếu trong SpEL. Project chỉ thêm `spring-context`, tự kéo `spring-beans` và `spring-expression` transitively; không cần viết servlet hoặc Spring Boot config.

### 12.4. Lệnh chạy và stdout

Với JDK 17+ và Maven 3.9.x, chạy ở thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Kết quả các dòng do ứng dụng chủ động in:

~~~text
placeholder-name=catalog
placeholder-default=local
nested-key=https://dev.example.test
nested-value=server-8080
spel-math=14
spel-method=CATALOG
spel-bean=12
mixed-value=40
spel-elvis=fallback-spel
api-placeholder=catalog
api-required-error=true
api-spel=14
~~~

Maven/logging có thể xuất thêm dòng tùy môi trường. Các dòng trên không phụ thuộc kết nối mạng, vì URL chỉ là text và project không gửi HTTP request.

### 12.5. Phân tích các output

- `placeholder-name=catalog`: property `lesson243.name` có value `catalog`.
- `placeholder-default=local`: không có `lesson243.optional` nên default được dùng.
- `nested-key=https://dev.example.test`: `lesson243.stage=dev` tạo outer key `lesson243.endpoint.dev`.
- `nested-value=server-8080`: `lesson243.raw` chứa placeholder của `lesson243.port`.
- `spel-math=14`: thứ tự toán tử nhân trước cộng.
- `spel-method=CATALOG`: SpEL gọi `String.toUpperCase()` trên literal.
- `spel-bean=12`: SpEL tìm `numberSource`, method `doubleValue()` trả 12.
- `mixed-value=40`: placeholder `lesson243.multiplier=4` được resolve, SpEL tính `4 * 10`.
- `spel-elvis=fallback-spel`: Elvis dùng fallback khi expression trái là `null`.
- `api-placeholder=catalog`: Environment API xử lý property placeholder.
- `api-required-error=true`: strict Environment resolution ném lỗi khi key không có, chương trình bắt có chủ đích.
- `api-spel=14`: standalone raw parser đánh giá expression body `2 + 3 * 4`, không cần wrapper `#{...}`.

### 12.6. Bài tập nhỏ sau khi chạy

Đổi `lesson243.multiplier` từ `4` thành `7` trong MapPropertySource; kết quả `mixed-value` thành 70. Đổi `lesson243.stage` thành `prod` nhưng **không thêm** `lesson243.endpoint.prod`; do method argument dùng strict placeholder, context có thể fail khi tạo bean. Khi thêm đúng key `lesson243.endpoint.prod`, project chạy lại bình thường.

Bạn có thể tự thêm một property tên `lesson243.optional` với value `"testing"` để thấy placeholder default `local` không còn được dùng. Một empty String nên được test riêng.

## 13. Raw SpEL parser khác `@Value("#{...}")`

Khi gọi API trực tiếp:

~~~java
ExpressionParser parser = new SpelExpressionParser();

Integer result = parser
    .parseExpression("2 + 3")
    .getValue(Integer.class);
~~~

`result` là 5.

**Không nên** truyền nguyên wrapper:

~~~java
parser.parseExpression("#{2 + 3}");
~~~

và giả định parser raw sẽ xử lý như bean expression. `#{...}` là syntax wrapper trong bean expression/template contexts, còn `parseExpression(String)` thường nhận **SpEL expression body**.

### TemplateParserContext khi cần literal + expression

~~~java
var parser = new SpelExpressionParser();

String message = parser.parseExpression(
    "Total=#{2 + 3}",
    new TemplateParserContext()
).getValue(String.class);
~~~

Với imports `SpelExpressionParser` và `TemplateParserContext`, `message` là `"Total=5"`. ParserContext chỉ ra rằng chuỗi là một template chứa SpEL segments thay vì một raw expression.

### Raw SpEL parser không tự resolve property placeholders

~~~java
parser.parseExpression("${app.port:8080}");
~~~

không phải lời gọi thích hợp để tra Environment key. Nếu thật sự cần cả hai, bạn phải dùng resolver phù hợp trước hoặc thiết kế pipeline tương ứng, không trông chờ raw SpEL parser “tự hiểu” syntax Spring property placeholders.

## 14. EvaluationContext và biến SpEL

SpEL có `EvaluationContext` để cung cấp root object, variables, functions, BeanResolver và những quyền truy cập được phép.

Ví dụ:

~~~java
var parser = new SpelExpressionParser();
var context = new StandardEvaluationContext();
context.setVariable("count", 4);

Integer doubled = parser.parseExpression("#count * 2")
    .getValue(context, Integer.class);
~~~

`doubled` là 8. Đây là snippet cần imports `SpelExpressionParser` và `StandardEvaluationContext`.

**Phân biệt ba cách viết có dấu `#`:**

- `#{2 + 3}`: wrapper Spring bean expression/template syntax.
- `#count`: SpEL **variable** tên `count` bên trong expression.
- `${count}`: property placeholder tìm key `count`.

Ba thứ này không phải alias.

### Standalone context không tự nhìn thấy Spring beans

`new StandardEvaluationContext()` không tự biết toàn bộ beans của một `ApplicationContext`. Muốn dùng `@beanName` trong một SpEL expression standalone, cần cấu hình BeanResolver đúng contract; còn Spring injection pipeline đã có bean expression resolver phù hợp trong context tiêu chuẩn.

### Restricted evaluation

Với bài toán chỉ đọc property trên data object, `SimpleEvaluationContext` giới hạn những tính năng như type references và constructor invocation so với `StandardEvaluationContext`. Tuy nhiên “simple” không miễn trừ trách nhiệm validation: vẫn phải kiểm soát input và data exposure.

## 15. Giới hạn tự động resolve trong Spring Core

Hai chuỗi này trong Java code bình thường:

~~~java
String a = "${app.name}";
String b = "#{2 + 3}";
System.out.println(a);
System.out.println(b);
~~~

chỉ in:

~~~text
${app.name}
#{2 + 3}
~~~

Spring không thay Java String literal tại thời điểm compile. Chuỗi chỉ được xử lý khi nó **đi qua API hoặc hạ tầng có trách nhiệm resolve/evaluate**.

Tương tự, annotation `@Value` trên một object do `new` tạo bên ngoài Spring không tự được áp dụng. Cần Spring-managed bean có injection processing hoặc phải gọi API tương ứng.

### Trong `Environment.getProperty`

Nếu một property có text `"#{2 + 3}"`, `environment.getProperty(key)` có thể trả **đúng chuỗi ấy**, chứ không tính thành 5. `Environment` là property resolver; nó không tự chạy full SpEL evaluation trên mọi String value.

Ngược lại, nếu một `@Value("${expression.key}")` được embedded resolver thay bằng text `#{2 + 3}`, Spring bean expression evaluation stage có thể xử lý SpEL tiếp. Vì thế phải xác định **đường đi của chuỗi** trước khi kết luận.

## 16. `PropertySourcesPlaceholderConfigurer` và missing values

Spring Framework cung cấp embedded value resolver cho `@Value` trong `ApplicationContext`. Theo Spring Reference, default resolver có thể giữ nguyên placeholder chưa resolve thay vì lỗi. Nếu muốn **strict placeholder resolution** cho bean configuration, dùng:

~~~java
@Configuration
public class Config {

    @Bean
    public static PropertySourcesPlaceholderConfigurer
            propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }
}
~~~

Đây là cấu hình minh họa, cần các Spring imports.

`PropertySourcesPlaceholderConfigurer` là một `BeanFactoryPostProcessor`. Method `@Bean` trả nó nên `static` để Spring không phải instantiate `@Configuration` object quá sớm.

**Nhưng:** Configurer này chủ yếu xử lý property placeholders; nó không phải parser SpEL và không sửa các biểu thức SpEL sai syntax. Trong code có thể sử dụng đồng thời strict placeholders và bean SpEL evaluation.

### Strict và lenient Environment APIs vẫn khác nhau

~~~java
environment.resolvePlaceholders("${missing}");
environment.resolveRequiredPlaceholders("${missing}");
~~~

Lệnh đầu có thể giữ placeholder; lệnh sau ném `IllegalArgumentException`. Việc đăng ký configurer không biến mọi lời gọi lenient thành strict.

## 17. Những hiểu nhầm về default

### Trường hợp 1: property value tồn tại nhưng sai kiểu

~~~properties
app.port=not-a-number
~~~

Dùng:

~~~java
@Value("${app.port:8080}")
int port;
~~~

Default 8080 **không tự được dùng** vì `app.port` có value. Conversion sang `int` có thể lỗi.

### Trường hợp 2: Elvis khác fallback của placeholder

~~~java
@Value("#{null ?: 'fallback'}")
String result;
~~~

SpEL nhìn **kết quả biểu thức**, không tra Environment theo key.

### Trường hợp 3: SpEL ternary không phải placeholder default

~~~java
@Value("#{10 > 5 ? 'high' : 'low'}")
String level;
~~~

Biểu thức dùng condition để chọn nhánh. Còn `${app.level:high}` chỉ chọn property hay default.

### Trường hợp 4: nested key không phải bean property access

`${service.url.${env}}` xây key string rồi tra PropertySources. `#{@service.url}` truy cập thuộc tính `url` của bean `service`. Cả hai có dấu chấm nhưng không có cùng semantics.

## 18. Chẩn đoán lỗi khi SpEL gọi bean quá sớm

SpEL reference `@beanName` trong `@Value` có thể khiến Spring lookup một bean khác ngay khi bean hiện tại được tạo. Với dependency graph phức tạp, điều này có thể:

- Tạo dependency sớm hơn bạn nghĩ.
- Gây circular dependency nếu hai beans phụ thuộc nhau.
- Gọi một method khởi tạo network/resource ngay trong bean configuration.
- Khiến lỗi method/bean lookup làm application startup fail.

Đừng dùng SpEL bean references để che dependencies cần thiết. Constructor parameters rõ type và Spring DI là cách phổ biến hơn.

Trong metadata infrastructure callbacks như `BeanFactoryPostProcessor`, tránh gọi `getBean()` hoặc khiến application beans được tạo sớm chỉ để phục vụ biểu thức. Bean expressions nên được dùng đúng lifecycle injection, không biến registry processing thành runtime logic.

## 19. Debug từng bước khi có biểu thức hỗn hợp

Với:

~~~text
#{${worker.base:2} * 3}
~~~

làm theo thứ tự:

1. Xác nhận key `worker.base` chính xác.
2. Duyệt `Environment.getPropertySources()` theo precedence.
3. Kiểm tra property có thực sự thiếu hay chỉ rỗng/sai format.
4. Tính riêng placeholder `${worker.base:2}` bằng `resolveRequiredPlaceholders`.
5. Xác nhận kết quả có phải numeric literal hợp lệ cho SpEL không.
6. Đánh giá SpEL body tương ứng trong một test context thích hợp.
7. Kiểm tra conversion sang target type và giới hạn nghiệp vụ.

Nếu outer SpEL tham chiếu bean:

~~~text
#{@pricingService.rate() * 2}
~~~

thì phải kiểm tra bean `pricingService` được register không, proxy/bean scope có phù hợp không, method có accessible không và có side effects không.

### Không log bí mật

Tránh in toàn bộ PropertySources hoặc expression result nếu có password/token. Khi debug production, ưu tiên log tên source và key không nhạy cảm; mask secrets trước khi ghi log.

## 20. Lỗi thường gặp và cách khắc phục

| Triệu chứng | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| `${2+3}` không ra 5 | Placeholder tìm key | Dùng `#{2+3}` |
| `#{app.port}` không đọc config | SpEL hiểu object property path | Dùng `${app.port}` |
| `${app.port:8080}` không lấy default | Key tồn tại nhưng rỗng/sai type | Validate property |
| `#{key:default}` báo parse error | Không phải SpEL fallback syntax | Dùng `${key:default}` hoặc Elvis/ternary đúng |
| Expression đứng yên dạng `#{...}` | Chuỗi không qua SpEL-aware resolver | Dùng đúng `@Value` hoặc TemplateParserContext |
| Placeholder còn nguyên `${missing}` | Lenient resolver | Cấu hình strict khi cần |
| Property đọc ra `#{2+3}` chưa tính | `Environment` không tự chạy SpEL | Dùng expression resolver phù hợp |
| `@beanName` không resolve | Không có BeanResolver/bean definition phù hợp | Kiểm tra evaluation context |
| Raw parser không hiểu wrapper `#{...}` | `parseExpression` nhận expression body | Bỏ wrapper hoặc dùng template parser context |
| Mixed expression báo lỗi | Raw property chèn vào SpEL không hợp lệ | Convert typed config trước, tính trong Java |
| SpEL method gây startup chậm | Method bị gọi khi @Value injection | Di chuyển logic sang service runtime |
| Security incident từ expression | Evaluate input không đáng tin | Giới hạn context, whitelist dữ liệu |
| SpEL result sai type | Conversion/evaluation rules không match target | Test type, sửa expression |
| Bean field không cập nhật | Singleton snapshot đã inject | Thiết kế explicit runtime reload |
| Placeholder circular reference | Các property values tham chiếu vòng | Sửa graph properties |

## 21. Unit/integration test cho từng lớp resolver

Một chương trình kiểm thử độc lập có thể xác nhận:

~~~java
try (var context =
         new AnnotationConfigApplicationContext()) {

    context.getEnvironment().getPropertySources().addFirst(
        new MapPropertySource("test", Map.of(
            "sample.env", "dev",
            "sample.url.dev", "https://dev.example.test"
        ))
    );
    context.refresh();

    String result =
        context.getEnvironment().resolveRequiredPlaceholders(
            "${sample.url.${sample.env}}");

    if (!"https://dev.example.test".equals(result)) {
        throw new AssertionError("Nested placeholder failed");
    }
}
~~~

Snippet cần imports `AnnotationConfigApplicationContext`, `MapPropertySource` và `Map`. Nó test **property resolution**; chưa test SpEL bean reference.

Test SpEL độc lập:

~~~java
var parser = new SpelExpressionParser();

Integer result = parser
    .parseExpression("(2 + 3) * 4")
    .getValue(Integer.class);

if (result == null || result != 20) {
    throw new AssertionError("SpEL arithmetic failed");
}
~~~

Snippet này cần import `SpelExpressionParser`. Nó test arithmetic **không phụ thuộc Environment**.

Để test cơ chế **kết hợp** giống `@Value`, khởi tạo full Spring context như mục 12 và assert các fields `view.mixed()`, `view.endpoint()`. Đừng chỉ unit test raw SpEL parser rồi cho rằng mọi @Value injection path cũng đúng.

### Các cases nên có

- Placeholder key có thật, thiếu, default và chuỗi rỗng.
- Nested property key/default/value.
- Hai sources có key trùng và precedence khác.
- SpEL literals, toán học, Elvis, bean reference.
- Mixed expression với số hợp lệ và dữ liệu sai type.
- Missing required placeholder fail-fast.
- Object ngoài Spring có `@Value` không tự được inject.
- Input không đáng tin không được đưa vào full SpEL evaluation.
- Snapshot singleton không tự đổi khi property sources thay đổi.

## 22. Khi nào không nên dùng SpEL dù viết được một dòng ngắn?

Hãy cân nhắc chuyển sang Java code nếu:

- Biểu thức có nhiều nhánh nested ternary.
- Dữ liệu đầu vào cần nhiều phép validation/normalization.
- Method được gọi có I/O hoặc side effects.
- Biểu thức tham chiếu nhiều beans và ẩn dependency graph.
- Một thay đổi nhỏ trong property source có thể đổi luôn SpEL syntax.
- Logic cần dễ debug bằng IDE hoặc unit test.
- Runtime property updates đòi thread-safe state management.
- SpEL string đến từ user input, configuration không tin cậy hoặc remote sources chưa được kiểm soát.

`@Value("#{...}")` hữu ích cho expression nhỏ, ổn định. Nó không phải một giải pháp thay thế mọi design trong service layer.

## 23. Câu hỏi tự kiểm tra

**Câu 1:** `${app.max:10}` có nghĩa gì?

**Đáp án:** Tra config key `app.max` qua Environment, dùng default 10 nếu không tìm thấy giá trị phù hợp.

**Câu 2:** `#{app.max}` có cùng ý nghĩa không?

**Đáp án:** Không. SpEL có thể đọc object property path, không tự lookup Environment key cùng tên.

**Câu 3:** Muốn tính `3 * 4`, dùng gì?

**Đáp án:** `#{3 * 4}` hoặc Java code, không dùng `${3 * 4}`.

**Câu 4:** SpEL có thể tham chiếu bean như thế nào?

**Đáp án:** `@beanName` trong một evaluation context có BeanResolver, ví dụ `#{@numberSource.doubleValue()}`.

**Câu 5:** `${key:default}` có phải SpEL Elvis không?

**Đáp án:** Không. Elvis viết `expr ?: fallback` trong SpEL, có semantics của kết quả expression.

**Câu 6:** `#{${count:2} * 3}` có thể cho 12 khi nào?

**Đáp án:** Khi property `count` resolve thành 4 trong pipeline placeholder→SpEL và SpEL tính 4 * 3.

**Câu 7:** `SpelExpressionParser.parseExpression("2+3")` có cần wrapper `#{...}` không?

**Đáp án:** Không. Raw parser nhận expression body; dùng TemplateParserContext khi có literal text + `#{...}` segments.

**Câu 8:** Gọi `Environment.getProperty` trên một value chứa `#{2+3}` có chắc chắn nhận 5 không?

**Đáp án:** Không. Environment làm property lookup/resolution, không tự đánh giá SpEL trên mọi raw property value.

**Câu 9:** `@Value` có được áp dụng trên object do `new` tạo ngoài Spring không?

**Đáp án:** Không tự động; cần Spring-managed bean/injection infrastructure.

**Câu 10:** Vì sao không dùng SpEL với input không đáng tin?

**Đáp án:** SpEL có thể gọi methods, truy cập types và objects tùy context; đánh giá source expression động không kiểm soát tạo rủi ro bảo mật.

## 24. Checklist áp dụng

- [ ] Đã xác định đây là property lookup hay expression evaluation?
- [ ] Cú pháp `${...}` và `#{...}` được dùng đúng ngữ cảnh?
- [ ] Có phân biệt fallback khi thiếu key và fallback Elvis theo expression value?
- [ ] PropertySources precedence và empty string được hiểu đúng?
- [ ] Nested property key/default/value có hợp lý và không circular?
- [ ] `@Value` thuộc bean thực sự do Spring quản lý?
- [ ] Strict placeholder policy được cấu hình khi có key bắt buộc?
- [ ] Có hạn chế SpEL method calls gây side effects lúc startup?
- [ ] Có test raw parser khác với bean expression syntax?
- [ ] Có conversion/validation đúng target type?
- [ ] Có tránh raw untrusted value trở thành SpEL source?
- [ ] Có đánh giá việc chuyển expression phức tạp sang Java/typed config không?
- [ ] Có bảo vệ secrets trong config và logs không?

## 25. Tóm tắt

- **`${...}` = placeholder** để tra configuration key, hỗ trợ `${key:default}`, nested default và nested key.
- **`#{...}` = SpEL** để đánh giá expressions, gồm toán học, boolean, method calls, bean references, type references và nhiều tính năng khác.
- **Placeholder default `:` khác SpEL Elvis `?:`**: một bên phụ thuộc key lookup, bên kia phụ thuộc expression result.
- **Spring `@Value` có thể kết hợp** hai cơ chế, ví dụ `@Value("#{${base:2} * 3}")`, nhưng không nên chèn raw user input làm expression source.
- **Raw `SpelExpressionParser` nhận expression body**, còn `#{...}` wrapper được dùng trong bean expression hoặc template parsing context.
- **`Environment.getProperty` không đồng nghĩa SpEL evaluation**.
- **`@Value` không tự hoạt động trên object tạo bằng `new`**.
- **Typed configuration + Java logic** thường an toàn và dễ bảo trì hơn những SpEL expressions dài.
- **Expression evaluation có quyền mạnh hơn placeholder lookup**: quản lý evaluation context và nguồn input chặt chẽ.

## 26. Tài liệu chính thức

- [Spring Framework Reference — Using @Value](https://docs.spring.io/spring-framework/reference/6.1/core/beans/annotation-config/value-annotations.html): property placeholders, strict configurer và Spring expressions khi inject.
- [Spring Framework Reference — Spring Expression Language](https://docs.spring.io/spring-framework/reference/6.1/core/expressions.html): cú pháp, API, SpEL evaluation và bean expressions.
- [Spring Framework Reference — Expression Support for Defining Bean Definitions](https://docs.spring.io/spring-framework/reference/6.1/core/expressions/expressions-in-bean-definitions.html): biểu thức `#{...}` trong bean metadata.
- [Spring Framework 6.1 — PropertyResolver Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/env/PropertyResolver.html): `getProperty` và hai placeholder-resolution APIs.
- [Spring Framework 6.1 — PropertySourcesPlaceholderConfigurer Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/support/PropertySourcesPlaceholderConfigurer.html): xử lý placeholders trong Spring bean definitions.
- [Spring Framework 6.1 — SpelExpressionParser Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/expression/spel/standard/SpelExpressionParser.html): direct parser API.
- [Spring Framework 6.1 — SimpleEvaluationContext Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/expression/spel/support/SimpleEvaluationContext.html): giới hạn khả năng expression evaluation.
- [Spring Framework — Elvis Operator](https://docs.spring.io/spring-framework/reference/core/expressions/language-ref/operator-elvis.html): fallback semantics của `?:`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="values-and-expressions-008">Tham chiếu bean, thuộc tính và tập hợp trong SpEL</a>

<details>
<summary>Xem chi tiết</summary>

# Tham chiếu bean, thuộc tính và tập hợp trong SpEL — Spring Framework Core Container

## 1. SpEL là gì và vì sao cần tham chiếu tới bean?

**Spring Expression Language (SpEL)** là ngôn ngữ biểu thức của Spring, hỗ trợ đọc thuộc tính object, gọi method, chọn bean theo tên, truy cập phần tử list/array/map, lọc và biến đổi collection, toán tử điều kiện và nhiều tính năng khác. Trong Spring IoC Container, biểu thức thường được khai báo trong `@Value("#{...}")`, trong định nghĩa bean kiểu XML hoặc qua API `ExpressionParser` khi thật sự cần.

Giả sử một bean `Catalog` có danh sách gói dịch vụ, bean `PricingRules` chứa ngưỡng giá, còn một bean báo cáo phải lấy tên gói đầu tiên, các gói đang bật và những gói có giá cao hơn ngưỡng. Thay vì lấy cả hai bean rồi viết tất cả thao tác ở constructor, một cấu hình có thể biểu diễn những phép truy vấn nhỏ, ổn định:

```java
@Value("#{@catalog.plans[0].name}")
private String firstPlan;

@Value("#{@catalog.plans.?[enabled].![name]}")
private List<String> enabledNames;

@Value("#{@catalog.plans.?[price > @'pricing.rules'.minimumPrice].![name]}")
private List<String> premiumNames;
```

Đây là **fragment** dùng type `Catalog`/`PricingRules` sẽ được khai báo trong ví dụ hoàn chỉnh ở phần 10. Những biểu thức này lần lượt **tham chiếu bean**, **đọc thuộc tính**, **index vào list**, **lọc** và **projection** từ object sang list thuộc tính.

Cần nhớ ba giới hạn:

- SpEL ở đây là **ngôn ngữ cấu hình/biểu thức**, không thay thế các thành phần domain/service của ứng dụng.
- Một biểu thức `@otherBean` có thể **yêu cầu Spring tạo bean được tham chiếu**; nó không chỉ đọc metadata vô hại.
- Không dùng dữ liệu người dùng hoặc chuỗi từ nguồn không tin cậy làm expression text để eval trực tiếp; biểu thức có khả năng điều hướng object graph, gọi methods và truy cập types tùy evaluation context.

Mục tiêu: nắm `@bean`, `@'bean.name'`, `&factoryBean`, property navigation, `[]`, map/list literals, `.?[]`, `.^[]`, `.$[]`, `.![]`, null-safety, root/variables, áp dụng trong `@Value` và cả API programmatic, cùng hướng dẫn debug và test.

## 2. Phân biệt `#{...}` và `${...}`

`${app.region:local}` là property lookup; `#{2 * 3}` là expression evaluation. Bên trong **SpEL**, một số cú pháp có mục đích cụ thể:

| Cú pháp | Chức năng |
| --- | --- |
| `@catalog` / `@'order.service'` | Tham chiếu bean qua BeanResolver |
| `#root` / `#this` / `#prices` | Root/current object hoặc variable |
| `T(java.lang.Math)` | Java type reference khi được phép |
| `{1,2,3}` / `{'key':123}` | Inline List/Map |

`@Value("#{@catalog.plans[0].name}")` lấy bean, list item và property. Nếu dùng SpEL parser độc lập, `@beanName` chỉ hoạt động với `BeanResolver` phù hợp. Biểu thức `@Value("#{${app.batch.base:5} * 2}")` phối hợp cả hai cơ chế và chỉ nên dùng với input tin cậy. Bài trước so sánh các syntax; trọng tâm bài này là **bean references, object graph và collections**.

## 3. Bean reference: @beanName và @'tên.phức-tạp'

### 3.1. Bean name đơn giản

Trong SpEL có `BeanResolver`, tiền tố `@` tham chiếu **bean theo tên**:

```text
@catalog
@catalog.plans
@catalog.plans[0]
@catalog.plans[0].name
```

Các expression tương ứng lấy bean `catalog`, gọi property `plans`, lấy phần tử đầu và đọc `name`. Spring IoC bean expression evaluation có sẵn bean resolution; nếu tự dùng `SpelExpressionParser` với một `StandardEvaluationContext` mới, **phải cấu hình BeanResolver** nếu muốn `@bean` hoạt động. Xem phần 12.

Spring bean name **không luôn bằng Java class name**. Ví dụ:

```java
@Bean("catalog")
public Catalog createCatalog() {
    return new Catalog();
}
```

Đây là fragment trong class `@Configuration`; bean name là `catalog`, không phải `createCatalog` sau khi đổi tên tường minh. `@Catalog` hay `@ClassName` không có nghĩa đặc biệt như tham chiếu bean.

### 3.2. Tên có dấu chấm hoặc ký tự đặc biệt

Theo tài liệu Spring SpEL, khi bean name chứa dấu chấm hoặc ký tự đặc biệt, dùng string literal sau `@`:

```java
@Value("#{@'pricing.rules'.minimumPrice}")
private int threshold;
```

`@'pricing.rules'` trỏ tới bean có **tên chính xác** `pricing.rules`. Nếu viết `@pricing.rules.minimumPrice` thì parser có thể hiểu bean `pricing` rồi truy cập thuộc tính `rules.minimumPrice`, không phải tên bean mong muốn.

Dùng single quotes bên trong Java double quotes như trên; không cần escape dấu nháy đơn. Với bean name phức tạp, luôn tham chiếu bằng literal name rõ ràng.

### 3.3. FactoryBean: tham chiếu product khác tham chiếu factory

`FactoryBean<T>` là extension point tạo một product cho bean name. `@myFactory` thường resolve **product**, còn `&myFactory` dùng cú pháp dereference để lấy **factory object**:

```text
@myFactory             -> product thông thường của FactoryBean
&myFactory             -> chính FactoryBean instance
@'service.name'        -> bean tên có dấu chấm
```

Cú pháp `&myFactory` cần `BeanResolver` hỗ trợ `BeanFactory`; Spring cung cấp `BeanFactoryResolver`. Không lẫn ký tự `&` với phép AND trong expression. Không nhầm `FactoryBean` với `BeanFactoryPostProcessor`, hai hợp đồng Spring hoàn toàn khác.

Một `FactoryBean.getObject()` có thể mở kết nối, tạo object đắt tiền hoặc phát sinh side effects. Do đó SpEL `@myFactory` không phải một thao tác “chỉ xem thông tin” luôn an toàn để chạy trong bootstrap hoặc logger.

### 3.4. Sử dụng @otherBean trong SpEL khác injection theo kiểu

```java
public ReportService(PricingRules rules) { ... }
```

Constructor injection dùng bean type/candidate resolution; `@Value("#{@'pricing.rules'.minimumPrice}")` lại đọc **một giá trị** thông qua bean name/SpEL. Nếu class cần hành vi từ `PricingRules` nhiều nơi, hãy inject hẳn `PricingRules` bằng constructor; không rải tên bean string vào nhiều biểu thức.

## 4. Property navigation: đọc field/getter và object lồng nhau

SpEL dùng dấu chấm để truy cập property:

```text
@catalog.plans
@catalog.plans[0].name
@'pricing.rules'.minimumPrice
@settings.network.timeoutMillis
```

Khi bean theo JavaBeans conventions có `getPlans()`, `getName()`, `getMinimumPrice()` hoặc `isEnabled()`, biểu thức `plans`, `name`, `minimumPrice`, `enabled` tương ứng có thể được resolve. Không cần viết `getPlans()` trong mọi expression, dù SpEL cũng hỗ trợ method invocation.

Ví dụ class:

```java
public final class Feature {
    private final String name;
    private final boolean enabled;

    public Feature(String name, boolean enabled) {
        this.name = name;
        this.enabled = enabled;
    }

    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
}
```

Đây là class Java thuần; khi là phần tử list của Spring bean hoặc root object, SpEL có thể đọc `name` và `enabled` qua các getter. Đừng dựa vào một field private không có accessor sẽ luôn đọc được trong mọi context bị hạn chế.

### Root object, #root và #this

Trong API programmatic, evaluation context có thể được tạo với một root object. Khi expression là `plans[0].name`, parser có thể điều hướng từ root mà **không dùng `@bean`**.

- `#root`: object gốc của lần evaluation.
- `#this`: object **đang là current evaluation object** trong một subexpression, đặc biệt hữu ích trong selection/projection.
- `@bean`: lấy bean qua bean resolver.
- `#myVar`: lấy variable đã đặt trong context.

Ví dụ conceptual:

```java
StandardEvaluationContext context =
        new StandardEvaluationContext(catalog);
String first = parser.parseExpression("plans[0].name")
        .getValue(context, String.class);
```

Fragment cần biến `catalog` và `parser`. Ở expression này, `Catalog` là root object, nên không cần bean resolver để đọc thuộc tính của nó. Ngược lại, `@catalog.plans[0].name` cần BeanResolver nếu gọi parser độc lập.

### Getter có thể có side effects

SpEL property access thông thường có thể invoke getters. Nếu `getPlans()` lười truy vấn database hoặc `getSecrets()` làm network I/O, một biểu thức trông như đọc metadata lại có thể kích hoạt I/O. Thiết kế getter dùng cho configuration nên thuần, nhanh, không làm secret leak. Không gọi method nghiệp vụ có side effect từ `@Value` nếu có thể dùng constructor wiring rõ ràng.

## 5. Truy cập List và array bằng [index]

Index của list/array bắt đầu bằng **0**:

```java
@Value("#{@catalog.plans[0].name}")
private String firstName;

@Value("#{@catalog.plans[1].price}")
private int secondPrice;
```

Với list [basic, pro, enterprise], kết quả lần lượt là `basic` và 200 trong demo phần 10.

Các điểm lưu ý:
1. Chỉ số zero-based, không phải vị trí thứ nhất = 1.
2. Nếu list quá ngắn, có thể gặp `SpelEvaluationException` do index ngoài biên.
3. List có thể là immutable; SpEL đọc được không đồng nghĩa nên dùng expression gán để mutate.
4. Truy cập một `Set` theo vị trí iteration có thể có semantics phụ thuộc thứ tự; đừng coi HashSet là list đã sắp xếp.
5. Bean expose `List<Plan>` với getters rõ ràng giúp navigation predictable hơn raw/object type.

### Array và length

```java
@Value("#{new int[]{10,20,30}[1]}")
private int middle;
```

Đây là fragment SpEL minh họa syntax tạo array, áp dụng trong context bean expression phù hợp. Không nhầm với cú pháp tạo Java array ở ngoài SpEL. Để tránh phụ thuộc biểu thức phức tạp trong config, có thể khai báo array bằng Java hoặc inject typed list.

### Truy cập nested chain

```text
@service.settings.locations[0].region
```

Một mắt xích null làm đường truy cập mặc định thất bại. Hãy xem null-safe navigation ở phần 8. Nếu collection rỗng, null-safe navigation không mặc nhiên biến index ngoài biên thành kết quả hợp lệ.

## 6. Truy cập Map bằng ['key'] và property chaining

Một bean có getter trả `Map<String, String>`:

```java
public Map<String, String> getRoutes() {
    return Map.of(
        "primary", "/v1/orders",
        "backup", "/v1/backup"
    );
}
```

SpEL có thể lấy value:

```java
@Value("#{@catalog.routes['primary']}")
private String orderPath;
```

Phần `routes` là JavaBeans property của `Catalog`, còn `['primary']` là map key. Dấu nháy đơn biểu thị key dạng string literal của expression.

Đừng viết `@catalog.routes.primary` rồi mặc định nó giống `['primary']` trong mọi implementation. Property notation đôi khi có thể làm việc với map, nhưng **bracket key** rõ và ít mơ hồ hơn, nhất là key có dấu chấm/hyphen, keyword hoặc ký tự đặc biệt.

Nếu key là `auth.provider`, dùng:

```text
@catalog.routes['auth.provider']
```

Không dùng `@catalog.routes.auth.provider` rồi mong framework hiểu đó là một key duy nhất.

### Khi map value là object

```text
@registry.clients['payments'].timeoutMillis
```

SpEL lấy entry theo key rồi truy cập property của object entry. Đây là fragment giả định `registry`, `clients`, `timeoutMillis` đã tồn tại và không null. Với cấu hình nhiều tầng hoặc nhiều key không cố định, Java code với typed DTO sẽ dễ validate và test hơn một expression dài.

### Inline map literal

```text
#{ {'basic':100, 'pro':200}['pro'] }
```

Với parser hỗ trợ inline maps, expression trên trả 200. Đây là **data literal** trong expression, không phải Spring bean hay Java `Map.of(...)`. Hãy dùng inline map cho biểu thức nhỏ, không hardcode bảng cấu hình lớn hoặc secret vào annotation.

## 7. Inline List, Map, array và expression literal

SpEL có thể tạo collections trực tiếp:

```text
{1,2,3}                          // inline list
{{'red','blue'},{'green'}}       // nested list
{'x':10,'y':20}                 // inline map
{:}                              // empty map
{}                               // empty list
new int[]{5,10,15}              // array
```

Các biểu thức trong khối trên là **SpEL fragments**, chưa có `#{...}` wrapper vì chúng được đưa trực tiếp vào `SpelExpressionParser.parseExpression(...)`.

Dùng trong `@Value`:

```java
@Value("#{{'read','write'}}")
private List<String> scopes;

@Value("#{{'timeout':1000,'limit':10}['timeout']}")
private int timeout;

@Value("#{new int[]{5,10,15}[2]}")
private int third;
```

Không nên giả định object list literal luôn mutable: tài liệu Spring cho biết SpEL có thể tối ưu constant lists/maps. Nếu code cần thay đổi list, tạo copy trong Java.

**Giới hạn convert:** khi injection target là `List<String>`, Spring dùng conversion/evaluation metadata để đưa giá trị vào target; với type phức tạp hoặc nested generics, hãy test framework version cụ thể. `@Value` không mạnh bằng typed config binding khi có hàng chục keys và nested structures.

### Literal, variable và bean là ba thứ khác nhau

| Expression | Ý nghĩa |
| --- | --- |
| `'catalog'` | String literal |
| `@catalog` | Bean tên catalog qua BeanResolver |
| `#catalog` | Variable tên catalog trong EvaluationContext |
| `catalog` | Property hoặc object reference trong evaluation context |
| `T(java.lang.Integer)` | Type reference |
| `#{@catalog.plans[0].name}` | Bean expression hoàn chỉnh khi dùng @Value |

Chúng không thể thay thế cho nhau. Một lỗi phổ biến là dùng `#beanName` để lấy Spring bean khi chưa `setVariable`; đúng cú pháp trong bean-definition SpEL thường là `@beanName`.

## 8. Null-safe navigation, Elvis và default value

Nếu một property có thể null:

```text
customer?.address?.city
```

`?.` là safe navigation: khi target trước toán tử là null, truy cập phần đó trả null thay vì ném exception do dereference null. Tuy nhiên an toàn của **toàn chuỗi** phụ thuộc mọi bước: nếu phần sau còn truy cập không safe, vẫn có thể thất bại.

Dùng Elvis để cấp fallback:

```text
customer?.address?.city ?: 'unknown'
```

Trong `@Value` (fragment):

```java
@Value("#{ @settings.optionalLabel ?: 'unnamed' }")
private String label;
```

Snippet giả định bean `settings` có property `optionalLabel`. Đây là SpEL Elvis, không phải default syntax `${key:default}`; một cái xử lý kết quả expression null/empty theo semantics của SpEL, một cái xử lý property placeholder chưa tìm thấy.

### Null-safe collection operators

Spring Framework 6.2 hỗ trợ nhiều cú pháp safe navigation với index/selection/projection, ví dụ `?.[]`, `?.?[]`, `?.![]` khi context và version phù hợp. Không trộn lẫn cú pháp mới vào một ứng dụng Spring cũ mà không kiểm version:

```text
members?.[0]
members?.?[enabled]
members?.![name]
```

Các dòng chỉ là **fragment cú pháp**, giả định `members` nullable collection và evaluation root thích hợp. Nếu member list null, safe operator trả null theo contract; **nếu list rỗng**, index 0 vẫn có thể lỗi. Với dữ liệu nghiệp vụ, đôi khi Java `Optional`, empty immutable list và validation trong constructor dễ kiểm soát hơn.

### Selection first/last không đồng nghĩa Optional

Nếu `members.^[condition]` không có phần tử phù hợp, kết quả selection first có thể là null. Việc truy cập tiếp `.name` mà không null-safe có thể ném lỗi; nếu cần optional, dùng safe navigation và fallback có chủ đích hoặc xử lý trong Java.

Không đặt expression quá nhiều toán tử null-safe chỉ để che cấu hình thiếu bắt buộc. Với key required, **fail fast** và chỉ ra tên cấu hình an toàn thường tốt hơn trả chuỗi rỗng.

## 9. Collection selection: .?[...], .^[...], .$[...]

Selection lọc một tập hợp dựa trên điều kiện evaluated trên từng phần tử.

```text
@catalog.plans.?[enabled]
@catalog.plans.?[price > 150]
@catalog.plans.^[enabled]
@catalog.plans.$[enabled]
```

Theo Spring Reference:
- `.?[predicate]`: chọn **tất cả** phần tử thỏa điều kiện.
- `.^[predicate]`: chọn **phần tử đầu tiên** thỏa điều kiện.
- `.$[predicate]`: chọn **phần tử cuối cùng** thỏa điều kiện.

Nếu collection ban đầu là `List` thì kết quả all-selection là một collection chứa các phần tử phù hợp theo thứ tự nguồn. Với nguồn không có thứ tự có nghĩa như `HashSet`, đừng khẳng định “first” hay “last” ổn định.

Ví dụ với danh sách:

```text
plans:
  0: { name: basic,      price: 100, enabled: true }
  1: { name: pro,        price: 200, enabled: false }
  2: { name: enterprise, price: 300, enabled: true }
```

Kết quả:
- `plans.?[enabled]` → [basic, enterprise] dưới dạng objects.
- `plans.?[price > 150]` → [pro, enterprise] dưới dạng objects.
- `plans.^[enabled].name` → `basic`.
- `plans.$[enabled].name` → `enterprise`.

Trong predicate, SpEL dùng **current element** (`#this`), nên `enabled` có nghĩa property của từng `Plan`. Để explicit:

```text
plans.?[#this.enabled]
```

Điều này có thể giúp người mới hiểu khi expression có nhiều bean references hoặc variables.

### Selection trên Map

SpEL có thể lọc `Map`. Predicate được đánh giá trên `Map.Entry` với property `key` và `value`:

```text
#prices.?[value > 150]
```

Giả sử `#prices` là variable map prices, expression chọn các entries có value lớn hơn 150; **kết quả là một map**, không phải list thuần. Không kiểm định thứ tự map bằng stdout khi nguồn không có insertion order ổn định.

### Lưu ý predicate không có side effect

```text
plans.?[price > 150]
```

là truy vấn declarative, nhưng SpEL cho phép method invocation. Đừng dùng predicate như `chargeCustomer()` hoặc `createRecord()`: selection có thể chạy method trên từng phần tử, tạo nhiều side effects khó nhận biết và làm startup chậm. Selection nên chỉ đọc property và so sánh đơn giản.

## 10. Collection projection: .![...] và kết hợp selection

Projection biến mỗi element của collection thành một giá trị mới:

```text
@catalog.plans.![name]
@catalog.plans.![price]
@catalog.plans.![price * 2]
```

Ví dụ:
- `plans.![name]` → [basic, pro, enterprise].
- `plans.![price]` → [100, 200, 300].
- `plans.![price * 2]` → [200, 400, 600].

Projection không mutate bản thân `Plan`. Với input `List<Plan>`, kết quả projection là một danh sách các giá trị thu được.

### Lọc rồi chiếu

```text
@catalog.plans.?[enabled].![name]
```

Hai bước:
1. `.?[enabled]` lấy Plan objects đang bật.
2. `.![name]` lấy String name của từng object đã chọn.

Output dự kiến trong demo: `[basic, enterprise]`.

### Tham chiếu bean khác bên trong predicate

```text
@catalog.plans.?[price > @'pricing.rules'.minimumPrice].![name]
```

Đây là một biểu thức thực tế kết hợp cả ba chủ đề bài:
- `@catalog` lấy bean chứa list.
- `@'pricing.rules'` lấy bean rule có tên dấu chấm.
- `.minimumPrice` đọc JavaBean getter từ rule.
- `.?[...]` lọc theo giá.
- `.![name]` tạo danh sách tên kết quả.

Với threshold 150 và các prices [100,200,300], output `[pro, enterprise]`. Không nhầm result `List<String>` với bean list `List<Plan>` ban đầu.

### Projection trên Map

Khi projection một Map, SpEL evaluate biểu thức trên từng `Map.Entry` và kết quả là **list**, không phải map:

```text
#prices.![key + ':' + value]
```

Thứ tự có thể phụ thuộc iteration order của map. Nếu cần output ổn định theo key, sắp xếp bằng Java sau khi lấy kết quả hoặc dùng map có ordering contract rõ ràng.

## 11. Ví dụ Java 17+ chạy được: bean, property, list, map, selection và projection

Dự án dưới đây chỉ dùng **Spring Framework 6.2.18** qua Maven, **Java 17+** và không yêu cầu Spring Boot, web server hoặc database. Tất cả dữ liệu là in-memory; bean getters không truy cập mạng.

Mục tiêu:
1. `@catalog` tham chiếu một bean bằng tên.
2. `@'pricing.rules'` tham chiếu bean có dấu chấm trong tên.
3. `.plans[0].name` truy cập bean property, list index và element property.
4. `.routes['primary']` truy cập map entry.
5. `.?[enabled].![name]` lọc và projection.
6. `.^[enabled]` và `.$[enabled]` lấy phần tử đầu/cuối thỏa điều kiện.
7. `@Value` trên `@Bean` factory method parameter nhận kết quả và tạo immutable summary.

### 11.1. Cấu trúc

```text
spel-collection-demo/
├── pom.xml
└── src/main/java/edu/example/spel/
    ├── Plan.java
    ├── Catalog.java
    ├── PricingRules.java
    ├── Summary.java
    ├── AppConfig.java
    └── Main.java
```

**pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>spel-collection-demo</artifactId>
    <version>1.0.0</version>
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
                <configuration>
                    <mainClass>edu.example.spel.Main</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**src/main/java/edu/example/spel/Plan.java**

```java
package edu.example.spel;

public final class Plan {
    private final String name;
    private final int price;
    private final boolean enabled;

    public Plan(String name, int price, boolean enabled) {
        this.name = name;
        this.price = price;
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public int getPrice() {
        return price;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
```

**src/main/java/edu/example/spel/Catalog.java**

```java
package edu.example.spel;

import java.util.List;
import java.util.Map;

public final class Catalog {
    private final List<Plan> plans = List.of(
            new Plan("basic", 100, true),
            new Plan("pro", 200, false),
            new Plan("enterprise", 300, true)
    );

    private final Map<String, String> routes = Map.of(
            "primary", "/v1/orders",
            "backup", "/v1/backup"
    );

    public List<Plan> getPlans() {
        return plans;
    }

    public Map<String, String> getRoutes() {
        return routes;
    }
}
```

**src/main/java/edu/example/spel/PricingRules.java**

```java
package edu.example.spel;

public final class PricingRules {
    private final int minimumPrice;

    public PricingRules(int minimumPrice) {
        this.minimumPrice = minimumPrice;
    }

    public int getMinimumPrice() {
        return minimumPrice;
    }
}
```

**src/main/java/edu/example/spel/Summary.java**

```java
package edu.example.spel;

import java.util.List;

public record Summary(
        String firstPlan,
        String route,
        List<String> enabledNames,
        String firstEnabled,
        String lastEnabled,
        List<String> premiumNames,
        int threshold) {

    public Summary {
        enabledNames = List.copyOf(enabledNames);
        premiumNames = List.copyOf(premiumNames);
    }
}
```

Record `Summary` giữ reference danh sách immutable copy từ kết quả selection/projection; consumer không thể thay các giá trị đã dùng để tạo summary. Đây là một object Java thuần, không cần annotation injection trong record.

**src/main/java/edu/example/spel/AppConfig.java**

```java
package edu.example.spel;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AppConfig {
    @Bean("catalog")
    public Catalog catalog() {
        return new Catalog();
    }

    @Bean("pricing.rules")
    public PricingRules pricingRules() {
        return new PricingRules(150);
    }

    @Bean
    public Summary summary(
            @Value("#{@catalog.plans[0].name}") String firstPlan,
            @Value("#{@catalog.routes['primary']}") String route,
            @Value("#{@catalog.plans.?[enabled].![name]}")
                    List<String> enabledNames,
            @Value("#{@catalog.plans.^[enabled].name}") String firstEnabled,
            @Value("#{@catalog.plans.$[enabled].name}") String lastEnabled,
            @Value("#{@catalog.plans.?[price > @'pricing.rules'.minimumPrice].![name]}")
                    List<String> premiumNames,
            @Value("#{@'pricing.rules'.minimumPrice}") int threshold) {
        return new Summary(
                firstPlan, route, enabledNames,
                firstEnabled, lastEnabled, premiumNames, threshold);
    }
}
```

Ở `AppConfig`, các parameter values được Spring giải trước khi gọi factory method `summary(...)`. Các expressions không tham chiếu ngược tới bean `summary` nên không tạo dependency cycle. Nếu `Catalog` hoặc `PricingRules` là lazy singleton, việc expression truy cập chúng có thể khiến chúng được tạo khi `Summary` được khởi tạo.

**src/main/java/edu/example/spel/Main.java**

```java
package edu.example.spel;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class Main {
    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            Summary summary = context.getBean(Summary.class);

            System.out.println("first: " + summary.firstPlan());
            System.out.println("route: " + summary.route());
            System.out.println("enabled: " + summary.enabledNames());
            System.out.println("first enabled: " + summary.firstEnabled());
            System.out.println("last enabled: " + summary.lastEnabled());
            System.out.println("premium: " + summary.premiumNames());
            System.out.println("threshold: " + summary.threshold());
        }
    }
}
```

### 11.2. Chạy

Trong thư mục `spel-collection-demo`:

```bash
mvn -q compile exec:java
```

**Các dòng do `Main` in, theo thứ tự:**

```text
first: basic
route: /v1/orders
enabled: [basic, enterprise]
first enabled: basic
last enabled: enterprise
premium: [pro, enterprise]
threshold: 150
```

Kết quả từ `List.of(...)` có thứ tự cố định; map chỉ được truy vấn theo key nên không phụ thuộc map iteration order. Maven/Spring có thể xuất thêm logging riêng, không thuộc bảy dòng stdout trên.

### 11.3. Giải thích diễn tiến

1. `new AnnotationConfigApplicationContext(AppConfig.class)` đăng ký configuration rồi tự `refresh()`.
2. Bean definitions `catalog`, `pricing.rules`, `summary` có mặt.
3. Container cần resolve từng parameter của `summary(...)`; `@Value` chứa SpEL được bean expression resolver xử lý.
4. `@catalog` kích hoạt lookup bean `catalog` và đọc `getPlans()` hoặc `getRoutes()`.
5. `@'pricing.rules'` lookup bean rule, đọc `getMinimumPrice()` = 150.
6. `plans[0].name` lấy basic. `routes['primary']` lấy chuỗi /v1/orders.
7. `plans.?[enabled]` lọc basic và enterprise; `.![name]` chiếu thành list String.
8. `plans.^[enabled]` lấy basic, `plans.$[enabled]` lấy enterprise.
9. `price > threshold` chọn pro/enterprise và projection lấy tên.
10. Constructor record `Summary` copy lists và giữ các values đã resolved. `Main` in kết quả sau khi context khởi tạo.

### 11.4. Các thay đổi để tự thử

- Đổi `new PricingRules(150)` thành `new PricingRules(250)`. `premiumNames` khi đó còn `[enterprise]`.
- Đổi `enabled` của pro thành true. `enabledNames` thành `[basic, pro, enterprise]` nhưng `firstEnabled`/`lastEnabled` không đổi.
- Đổi danh sách thành rỗng: index `[0]` và first/last selection sẽ không còn trả giá trị như demo; cần xử lý absent/empty.
- Thay `Map.of(...)` key `primary` bằng `main` mà không sửa expression sẽ gây null/invalid required value theo vị trí nhận.
- Đổi bean name `pricing.rules` mà không sửa `@'pricing.rules'` sẽ làm expression resolution lỗi vì bean reference không tồn tại.
- Thử inject thẳng `PricingRules` qua constructor thay vì SpEL property nếu component cần nhiều behavior từ rules.

## 12. Dùng SpelExpressionParser độc lập và cấu hình BeanResolver

SpEL cũng hoạt động **ngoài annotation @Value**. Nhưng `@bean` chỉ hoạt động nếu EvaluationContext có `BeanResolver`.

### 12.1. Chỉ điều hướng root object: không cần bean resolver

```java
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

Catalog catalog = new Catalog();
ExpressionParser parser = new SpelExpressionParser();
StandardEvaluationContext context = new StandardEvaluationContext(catalog);

String first = parser.parseExpression("plans[0].name")
        .getValue(context, String.class);
System.out.println(first); // basic
```

Đây là **fragment** dùng `Catalog` từ project mục 11 và imports SpEL. `plans` được resolve trên **root object Catalog**, không lookup Spring bean. Nếu tự truyền root object thì vẫn có thể test SpEL mà không khởi động ApplicationContext.

### 12.2. Tham chiếu @bean từ parser độc lập

```java
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

try (AnnotationConfigApplicationContext context =
         new AnnotationConfigApplicationContext(AppConfig.class)) {
    ExpressionParser parser = new SpelExpressionParser();
    StandardEvaluationContext evaluation = new StandardEvaluationContext();
    evaluation.setBeanResolver(new BeanFactoryResolver(context));

    String name = parser.parseExpression("@catalog.plans[0].name")
            .getValue(evaluation, String.class);
    System.out.println(name);
}
```

Đây là fragment chạy được khi đặt trong method cùng imports `AnnotationConfigApplicationContext`, class `AppConfig` và project mục 11. `BeanFactoryResolver` trong package `org.springframework.context.expression` thực thi `BeanResolver` qua Spring `BeanFactory`. Không phải mọi `StandardEvaluationContext` mặc định đều tự biết `@catalog`.

### 12.3. Variables với #name

```java
StandardEvaluationContext evaluation = new StandardEvaluationContext();
evaluation.setVariable("limit", 150);
Boolean expensive = new SpelExpressionParser()
        .parseExpression("200 > #limit")
        .getValue(evaluation, Boolean.class);
```

Fragment cần imports tương ứng. `#limit` là variable; không cần một bean tên `limit`. Variables dùng để cấp dữ liệu đã biết cho expression, có thể làm rõ data flow khi viết expression trong framework.

### 12.4. Standard vs SimpleEvaluationContext

`StandardEvaluationContext` có bộ khả năng rộng để đọc properties, gọi methods, tham chiếu types và bean (nếu resolver được cấu hình). `SimpleEvaluationContext` cung cấp subset được giới hạn để dùng trong các kịch bản đánh giá biểu thức có kiểm soát, có thể cấu hình property accessors và hạn chế method/type/constructor features.

Nếu phải đánh giá **expression do người dùng đưa vào**, không đơn giản dùng `StandardEvaluationContext` rồi nghĩ chỉ có truy vấn đọc. Thiết kế whitelist đầu vào, giới hạn grammar/capabilities, cung cấp root object chứa dữ liệu không nhạy cảm và cân nhắc `SimpleEvaluationContext` với quyền hạn tối thiểu. Với config nội bộ đã version/review, vẫn nên tránh expression phức tạp không cần thiết.

## 13. Lúc nào expressions được đánh giá? Có tự cập nhật không?

SpEL trong `@Value` thường được đánh giá khi Spring cần resolve injection value cho **một bean instance đang tạo**. Nếu bean là singleton, value đã được cấp **không tự cập nhật mỗi khi collection/property đổi**.

Ví dụ:
- Khi `Summary` được tạo, `enabledNames` là kết quả projection tại thời điểm đó.
- Nếu ai đó sau đó thay đổi danh sách của bean `Catalog` (trong demo list immutable nên không thay được), `Summary.enabledNames()` không tự recompute.
- Nếu muốn kết quả luôn mới ở runtime, inject `Catalog` và dùng Java query method khi gọi nghiệp vụ, hoặc thiết kế service caching/refresh rõ ràng.

```java
public final class CatalogQuery {
    private final Catalog catalog;

    public CatalogQuery(Catalog catalog) {
        this.catalog = catalog;
    }

    public List<String> enabledPlanNames() {
        return catalog.getPlans().stream()
                .filter(Plan::isEnabled)
                .map(Plan::getName)
                .toList();
    }
}
```

Đây là class Java phụ trợ có thể biên dịch với imports `java.util.List` và types từ demo. Nó tính toán theo yêu cầu chứ không chụp snapshot một lần trong `@Value`. Với danh sách thay đổi đồng thời ở production, cần thread-safe snapshot/collection strategy riêng.

**Không nhầm với SpEL được dùng trong các công nghệ khác** như Spring Security expressions hoặc Spring Data queries: thời điểm đánh giá có thể là **mỗi request/mỗi method invocation** theo framework. Bài này nói về **bean definition/@Value injection** trong Spring Core.

### Early bean creation/circular dependency

`@Value("#{@otherBean.someProperty}")` tạo dependency gián tiếp theo **tên bean**. Nếu bean `A` cần expression tham chiếu bean `B`, còn `B` lại cần `A` trong constructor, Spring có thể phát hiện circular dependency. Refactor theo constructor/typed config bean thay vì dựa vào SpEL để “giấu” quan hệ.

## 14. Những lỗi cú pháp và runtime thường gặp

| Triệu chứng | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| `@catalog` báo không tìm được bean | Bean chưa register/đổi tên/sai context | Kiểm tra `@Bean("catalog")` và context |
| Standalone parser không hiểu `@catalog` | Chưa cấu hình `BeanResolver` | Dùng `BeanFactoryResolver` với BeanFactory đúng |
| `@pricing.rules` bị hiểu sai | Bean name chứa dấu chấm không được quote | Dùng `@'pricing.rules'` |
| `#catalog` null | Chưa có variable tên catalog | Dùng `@catalog` để lấy bean hoặc `setVariable` |
| `catalog.plans` không tìm ra bean | Thiếu prefix `@` nếu không có root property | Dùng `@catalog`/BeanResolver |
| `plans[1]` lỗi | List null/thiếu phần tử | Validate size hoặc Java code |
| `routes.primary` sai với map key đặc biệt | Dotted property khác bracket key | Dùng `routes['primary']` |
| `enabled` không được resolve | Element không có getter/property hoặc type sai | Implement `isEnabled()`/`getEnabled()` |
| `.?[enabled]` không lọc đúng | Giá trị không boolean/null | Kiểm type và predicate |
| `.![name]` báo lỗi | Property name không tồn tại trên element | Kiểm getter/field visibility |
| `.^[enabled].name` lỗi khi không match | First selection không có object | Null-safe/fallback hoặc Java xử lý |
| `@catalog.plans.` syntax error | Expression kết thúc sau dot | Kiểm toàn biểu thức và brackets |
| Java `@Value` string chứa nháy kép chưa escape | Java string syntax | Dùng single quotes cho map key trong expression |
| `#{...}` được đọc như literal | Expression resolver chưa được cấu hình hoặc bean không do Spring quản lý | Dùng ApplicationContext/annotation processing phù hợp |
| `${...}` unresolved | Thiếu property source/placeholder config | Kiểm `Environment` và strict placeholder policy |
| SpEL access chạy query DB ngoài ý muốn | Getter có side effect | Getter thuần/cache đúng và tránh SpEL ngoài mục đích |
| Annotation @Value trỏ về bean đang tạo | Indirect circular dependency | Refactor graph; không giấu dependency qua string |
| Java `ClassCastException`/conversion fail | Result type khác injection point | Dùng typed result/ConversionService thích hợp |
| Projection map trả list, code mong map | Nhầm contract Map selection vs projection | Phân biệt `.?[]` và `.![]` |
| Kết quả không đổi sau cập nhật collection | @Value là snapshot khi inject singleton | Inject service và tính lại theo nhu cầu |
| Dữ liệu từ request được eval như expression | Rủi ro expression injection | Không eval untrusted expression; giới hạn context/grammar |

## 15. Tối ưu readability và bảo trì

Một expression ngắn như `@catalog.routes['primary']` có thể rõ. Nhưng expression dài trộn nhiều references, ternary, method calls, nested projections và conversion khó debug:

```text
@catalog.plans.?[enabled and price > @'pricing.rules'.minimumPrice].![name]
```

Dù biểu thức hợp lệ, cân nhắc:
- Đặt tên getter/bean rõ.
- Chia logic tính toán thành method Java thuần như `CatalogQuery.enabledPremiumNames(minimumPrice)` nếu có unit tests.
- Inject type qua constructor thay vì magic bean name string ở nhiều class.
- Đặt các expressions ở configuration composition layer thay vì domain objects.
- Với dữ liệu lớn, collection filtering/projection ở SpEL có chi phí runtime và khó đo hơn Java stream hoặc SQL query chuyên dụng.
- Không lặp cùng expression ở nhiều nơi; một typed config bean hoặc factory method có thể cung cấp kết quả duy nhất.

**Code smell:** expression “thông minh” trông ngắn hơn Java nhưng yêu cầu developer hiểu một mini-language để sửa business rule. SpEL không thay thế cách đóng gói nghiệp vụ rõ ràng.

### Khi cần chỉ đọc metadata

Nếu chỉ muốn biết Spring có bean nào hoặc type gì để xây registry, không nhất thiết lấy object qua `@bean`. Dùng API `BeanFactory.containsBean(...)`, `getType(...)` hoặc `getBeanNamesForType(...)` theo chính sách eager-init thích hợp. `@bean` có thể dẫn tới instantiate object, kéo tài nguyên/side effects sớm.

### Có nên trộn SpEL với nhiều property placeholders?

Nếu chỉ cần thay region, timeout, URL, prefer `${...}`/typed config và validation. SpEL phù hợp để tham chiếu bean/object graph hoặc tính toán nhỏ, nhưng nhiều placeholder chồng nhau làm precedence/config injection khó hiểu, khó test.

## 16. An toàn khi sử dụng SpEL và bean references

SpEL là biểu thức có khả năng mạnh, có thể truy cập bean methods, types và object graph tùy evaluation context. Việc eval chuỗi SpEL **không tin cậy** trong context có BeanFactoryResolver rộng có thể trở thành một rủi ro bảo mật nghiêm trọng.

Nguyên tắc:
1. Chỉ sử dụng **expression literal trong mã/config do team tin cậy review**.
2. Không ghép trực tiếp query parameter/header/request body vào template expression để parse/eval.
3. Khi phải hỗ trợ biểu thức do user cấu hình, xác định rõ **ngôn ngữ con** được phép, không mở toàn bộ `StandardEvaluationContext`/bean factory.
4. Không expose `BeanFactoryResolver` tới untrusted input nếu không có mô hình bảo mật chuyên biệt.
5. Dùng `SimpleEvaluationContext` với property accessors/method features bị giới hạn khi phù hợp; vẫn cần review threat model.
6. Không cho expression đọc secret bean, environment credentials hoặc log kết quả expression có thể chứa token/password.
7. Không để getter `getSecret()` vô tình được truy cập bởi câu lệnh log/debug.
8. Tránh method invocation có side effects trong selection/projection.
9. Với biểu thức lấy danh sách, đặt giới hạn kích thước nguồn; collection lớn làm startup chậm hoặc memory overhead.
10. Có test lỗi null/missing key/bean name và type mismatch để fail-fast với config sai.

### Secret và @Value

```java
@Value("#{ @credentialStore.currentToken }")
private String token;
```

Đây chỉ là anti-pattern minh họa nguy cơ coupling/secret exposure, cần bean `credentialStore`. Nó có thể khiến Spring gọi getter lấy secret khi bean được tạo và giữ secret dưới dạng `String`. Nếu ứng dụng chỉ cần token để gọi API, dùng abstraction chuyên dụng có quyền truy cập rõ thay vì lan truyền secret qua nhiều `@Value` expressions; tuyệt đối không log object chứa token.

### Log and exception safety

Không log raw expression hoặc evaluation result khi nó chứa cấu hình nhạy cảm. Stack traces của parser có thể lộ expression text được xây từ user data; giảm quyền truy cập logs, sanitize và tránh chép secret vào expression ngay từ đầu. Test các trường hợp value null, invalid path, missing bean và conversion errors bằng dữ liệu giả.

## 17. Debug SpEL với phương pháp từng bước

Khi expression phức tạp thất bại, hãy chia nó thành các đoạn trong test:

```text
1. @catalog
2. @catalog.plans
3. @catalog.plans[0]
4. @catalog.plans[0].name
5. @catalog.plans.?[enabled]
6. @catalog.plans.?[enabled].![name]
7. @'pricing.rules'
8. @'pricing.rules'.minimumPrice
9. @catalog.plans.?[price > @'pricing.rules'.minimumPrice].![name]
```

Mỗi bước xác nhận một điều:
- Bean name thực tế có tồn tại không?
- Property/JavaBean getter có tên đúng không?
- Collection có null/rỗng không?
- Predicate có trả boolean không?
- Map/list index trả kiểu nào?
- Result có tương thích với injection target type không?
- Có side effects khi lookup bean hoặc gọi getter không?

**Lỗi parsing** thường là `SpelParseException` khi syntax sai. **Lỗi evaluation** có thể là `SpelEvaluationException` khi thuộc tính/bean/index không hợp lệ. Khi nằm trong startup injection, exception thường được Spring wrap trong `BeanCreationException`/`UnsatisfiedDependencyException`; đọc **root cause** và expression path, không chỉ dòng ngoài cùng.

### Kiểm tra bằng ApplicationContext thật

Trong một integration test đơn giản:

```java
try (AnnotationConfigApplicationContext context =
         new AnnotationConfigApplicationContext(AppConfig.class)) {
    Summary value = context.getBean(Summary.class);
    if (!value.enabledNames().equals(List.of("basic", "enterprise"))) {
        throw new AssertionError("Selection/projection mismatch");
    }
    if (value.threshold() != 150) {
        throw new AssertionError("Bean reference mismatch");
    }
}
```

Fragment cần imports `java.util.List`, `AnnotationConfigApplicationContext` và demo classes mục 11; không phải source file hoàn chỉnh độc lập. Nó kiểm kết quả SpEL thực đã dùng để xây `Summary`.

### Kiểm tra expression ngoài @Value

Dùng `SpelExpressionParser`, root object và BeanResolver ở phần 12. Khi muốn test chỉ JavaBean navigation thì không cần tạo Spring context; khi kiểm `@bean` hoặc SpEL trong `@Value`, phải có đúng Spring BeanFactory resolver và lifecycle thực.

## 18. Những bài tập thực hành

**Bài 1 — truy cập bean property:** Đăng ký một bean `settings` có getters `getRegion()` và `getTimeoutMillis()`. Dùng `@Value("#{@settings.region}")` để inject String và `@Value("#{@settings.timeoutMillis}")` để inject int. Thử đổi tên bean để quan sát lỗi dependency theo name.

**Bài 2 — List indexing:** Từ `Catalog` trong demo, lấy tên Plan thứ ba bằng expression. Kiểm `plans[2].name` trả enterprise và `plans[3].name` không phải truy vấn an toàn.

**Bài 3 — Map:** Thêm một key `"auth.provider"` vào `getRoutes()` rồi dùng `@catalog.routes['auth.provider']`. Giải thích tại sao property chain với dấu chấm không tương đương.

**Bài 4 — selection:** Lọc những `Plan` có `price >= 200`. Phân biệt object result từ `.?[]` với name result sau projection.

**Bài 5 — projection:** Tạo `List<Integer>` giá gốc và giá nhân đôi bằng `.![price]` và `.![price * 2]`; so sánh với Java Stream.

**Bài 6 — cross-bean:** Đổi ngưỡng ở bean `pricing.rules` và xem premium result thay đổi. Nếu không muốn tên bean xuất hiện trong business object, chuyển logic sang factory method Java nhận bean đã inject theo type.

**Bài 7 — BeanResolver:** Gọi `new SpelExpressionParser().parseExpression("@catalog").getValue(new StandardEvaluationContext())`; phân tích vì sao thiếu BeanResolver. Thêm `BeanFactoryResolver` rồi thử lại.

**Bài 8 — null:** Sử dụng nullable getter `getOptionalSettings()`, thử `?.` và Elvis fallback. Test cả null object lẫn empty collection riêng.

**Bài 9 — security:** Nêu ba nguyên nhân không được eval untrusted expression với `BeanFactoryResolver`. Viết policy chỉ cho phép chọn một số property names, không để client upload nguyên SpEL expression.

**Bài 10 — lifecycle:** Tạo một bean lazy có constructor in log rồi tham chiếu nó qua `@Value("#{@lazyBean.someProperty}")` ở eager singleton. Quan sát bean lazy có thể bị tạo sớm theo dependency thật. Không để demo đó mở kết nối production.

## 19. Câu hỏi tự đánh giá và đáp án

1. `@beanName` trong SpEL khác `#beanName` ra sao?
2. Vì sao dùng `@'order.service'` khi bean name có dấu chấm?
3. `&factoryBean` có ý nghĩa gì khác `@factoryBean`?
4. SpEL standalone parser cần gì để hiểu `@myBean`?
5. `property.nestedValue` thường dựa vào getter nào?
6. Index của `List` bắt đầu ở 0 hay 1?
7. Cú pháp nào đọc key `primary` từ map `routes`?
8. Inline list và inline map có syntax gì?
9. `.?[condition]` làm gì?
10. `.![expression]` làm gì?
11. `.^[condition]` khác `.$[condition]` thế nào?
12. Selection trên Map trả collection có hình dạng gì?
13. Projection trên Map trả list hay map?
14. Trong `.?[enabled]`, `enabled` là property của bean Catalog hay mỗi phần tử Plan?
15. Trong demo, vì sao premium names gồm pro và enterprise?
16. Trong demo, vì sao enabled names không gồm pro?
17. `getBean("catalog")` và `@catalog` có thể cùng khiến bean được instantiate không?
18. `@Value` trên singleton tự động cập nhật khi List bị sửa không?
19. `#root` và `#this` khác nhau khi selection?
20. Vì sao truyền untrusted SpEL expression vào StandardEvaluationContext rộng là nguy hiểm?
21. `@Value("#{${base:5} * 2}")` và `@Value("${base:5}")` khác nhau thế nào?
22. Nếu list rỗng, `plans[0]` có được null-safe bảo đảm an toàn không?
23. Bean name `pricing.rules` có giống Java field path `pricing.rules` không?
24. Với config gồm một phép lọc business phức tạp, vì sao Java Stream/query service có thể phù hợp hơn?
25. Tại sao không log nguyên SpEL result khi nó đọc token từ secret bean?

**Đáp án ngắn:** (1) `@` lookup bean qua resolver, `#` là variable. (2) Quote giúp parser coi cả tên là một bean. (3) `&` dereference FactoryBean, `@` lấy product. (4) BeanResolver, ví dụ BeanFactoryResolver. (5) JavaBean getter/property accessor. (6) 0. (7) `routes['primary']`. (8) `{1,2}` và `{'key':value}`. (9) Lọc all matching items. (10) Biến mỗi item thành value, tạo list. (11) First vs last matching element. (12) Map các entries phù hợp. (13) List. (14) Property trên từng Plan. (15) Giá 200,300 đều >150. (16) pro.enabled=false. (17) Có. (18) Không; giá trị inject một lần lúc tạo singleton. (19) Root object vs current item của subexpression. (20) Expression có thể điều hướng object graph/gọi methods/types, rủi ro truy cập trái phép. (21) SpEL arithmetic vs placeholder String/convert. (22) Không. (23) Không. (24) Readability/test/performance/maintenance. (25) Có thể rò secret qua logs.

## 20. Checklist khi dùng SpEL trong Core Container

- [ ] Dùng `#{...}` cho SpEL, `${...}` cho placeholder.
- [ ] Dùng `@beanName` khi có BeanResolver; không nhầm `#variable`.
- [ ] Bean name có dấu chấm/ký tự đặc biệt phải quote `@'bean.name'`.
- [ ] Phân biệt `@factoryBean` product và `&factoryBean` factory.
- [ ] Bean references có thể tạo bean sớm; hiểu dependency graph.
- [ ] JavaBean getter/property types rõ và không có side effects.
- [ ] Dùng `[0]` cho first list item khi chắc chắn list không rỗng.
- [ ] Dùng `['key']` cho Map, đặc biệt key phức tạp.
- [ ] Phân biệt list literal `{...}` và map literal `{k:v}`.
- [ ] `.?[]` là filter, `.![]` là projection.
- [ ] `.^[]`/`.$[]` yêu cầu hiểu thứ tự nguồn và trường hợp không match.
- [ ] Với Map: selection có thể trả Map, projection trả List.
- [ ] Xét null/empty collection/invalid index riêng biệt.
- [ ] Không giả định @Value singleton tự refresh khi source thay đổi.
- [ ] Test result type/ConversionService khi inject generic collections.
- [ ] Không eval untrusted expression với StandardEvaluationContext/BeanFactoryResolver rộng.
- [ ] Không đưa secrets vào expression/logs/stack traces.
- [ ] Với business rules phức tạp, chuyển sang Java method/service có unit tests.
- [ ] Kiểm tra Spring version khi dùng null-safe collection operators.
- [ ] Có integration test cho references, selection, projection và lifecycle.
- [ ] Tránh magical string bean names tràn lan; ưu tiên type injection trong component nghiệp vụ.

## 21. Tài liệu chính thức

- Spring Framework 6.2 — Spring Expression Language overview: https://docs.spring.io/spring-framework/reference/6.2/core/expressions.html
- Spring Framework 6.2 — Bean references (`@name`, `@'bean.name'`, `&factory`): https://docs.spring.io/spring-framework/reference/6.2/core/expressions/language-ref/bean-references.html
- Spring Framework 6.2 — Properties, arrays, lists, maps, indexers: https://docs.spring.io/spring-framework/reference/6.2/core/expressions/language-ref/properties-arrays.html
- Spring Framework 6.2 — Inline lists: https://docs.spring.io/spring-framework/reference/6.2/core/expressions/language-ref/inline-lists.html
- Spring Framework 6.2 — Inline maps: https://docs.spring.io/spring-framework/reference/6.2/core/expressions/language-ref/inline-maps.html
- Spring Framework 6.2 — Collection selection: https://docs.spring.io/spring-framework/reference/6.2/core/expressions/language-ref/collection-selection.html
- Spring Framework 6.2 — Collection projection: https://docs.spring.io/spring-framework/reference/6.2/core/expressions/language-ref/collection-projection.html
- Spring Framework 6.2 — Safe Navigation Operator: https://docs.spring.io/spring-framework/reference/6.2/core/expressions/language-ref/operator-safe-navigation.html
- Spring Framework 6.2 — Expressions in Bean Definitions: https://docs.spring.io/spring-framework/reference/6.2/core/expressions/beandef.html
- Spring Framework 6.2 — Using @Value: https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/value-annotations.html
- Spring Framework 6.2.18 — BeanFactoryResolver API: https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/context/expression/BeanFactoryResolver.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="values-and-expressions-009">Giới hạn biểu thức động và kiểm tra giá trị cấu hình đầu vào</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn biểu thức động và kiểm tra giá trị cấu hình đầu vào trong Spring Core Container

## 1. Mục tiêu

Trong Spring Framework, ứng dụng có thể đưa dữ liệu cấu hình vào bean qua `Environment`, `@Value`, placeholder `${...}` hoặc biểu thức Spring Expression Language (SpEL) `#{...}`. Cơ chế này rất tiện, nhưng một biểu thức **được thực thi** không giống một giá trị cấu hình **được đọc như dữ liệu**. Nếu không xác định ranh giới, ứng dụng dễ gặp những vấn đề:

- Giá trị số sai miền cho phép nhưng vẫn được chuyển kiểu thành công, ví dụ `workerCount=100000`.
- Chuỗi sai enum, URL không hợp lệ, URL dùng giao thức không mong muốn, hostname hoặc user-info nguy hiểm.
- Cấu hình thiếu key bắt buộc bị thay bằng default quá rộng, khiến môi trường production chạy sai.
- Một input không đáng tin bị ghép thành chuỗi SpEL rồi parse/evaluate bằng ngữ cảnh có quyền truy cập method, constructor, type, bean reference.
- Biểu thức quá dài, lặp hoặc truy cập collection lớn gây tốn CPU, bộ nhớ, đánh giá chậm và rủi ro từ chối dịch vụ.
- Một property được dùng trước `refresh()` nhưng được kiểm tra quá muộn trong `@PostConstruct`.
- Nhiều thành phần nhận cấu hình giống tên nhưng khác source priority hoặc khác thời điểm snapshot.

**Nguyên tắc trung tâm:** dùng **placeholder để nhận dữ liệu**, dùng **typed config + validation để kiểm tra dữ liệu**, và chỉ dùng **SpEL expression được ứng dụng kiểm soát** khi thực sự cần diễn đạt phép tính/điều kiện. Không coi SpEL là ngôn ngữ cho người dùng cuối nhập tự do.

Bài này tập trung vào Spring Framework **Core Container** (Java 17+) và cách xử lý giá trị, biểu thức, lifecycle. Các tính năng riêng của Spring Boot sẽ được phân biệt rõ.

## 2. Phân biệt ba lớp: placeholder, SpEL, validation

| Lớp | Cú pháp/API | Công việc | Điều nó không tự bảo đảm |
| --- | --- | --- | --- |
| Property placeholder | `${service.timeout:3000}` | Tra key, áp dụng default, phục vụ injection | Giá trị phù hợp với miền nghiệp vụ hay an toàn |
| Spring Expression Language | `#{2 * 3}`, `#{@someBean.value}` | Đánh giá biểu thức theo quyền của EvaluationContext | Input tự do từ nguồn không tin cậy sẽ an toàn |
| Type conversion và validation | Constructor, enum, `@Validated`/Bean Validation, custom checks | Kiểm tra kiểu và invariant: range, URL, allowlist, quan hệ nhiều field | Tự diễn đạt mọi công thức nghiệp vụ hoặc tự refresh config |
| Environment | `environment.getProperty(key)` | Đọc các nguồn cấu hình theo precedence | Rebind toàn bộ singleton đã khởi tạo |
| BeanFactoryPostProcessor | `PropertySourcesPlaceholderConfigurer` | Resolve placeholders trong metadata/embedded values | Thay mọi chuỗi Java hay validate mọi domain rule |

Ví dụ:

~~~java
@Value("${job.workers:4}")
private int workers;
~~~

Chuỗi `"4"` có thể được chuyển thành `int 4`. Nhưng nếu key chứa `999999`, conversion vẫn thành công; **Spring không tự biết hệ thống chỉ cho phép 1–16 workers**. Ngay cả khi property chứa một URL syntactically hợp lệ, Spring không tự biết app chỉ cho phép `https` hoặc chỉ định domain của một provider.

Ngược lại:

~~~java
@Value("#{2 * 3}")
private int six;
~~~

Đây là SpEL, không phải lấy key từ Environment. Khi expression được Spring xử lý, nó tạo giá trị `6`. SpEL có tập ngôn ngữ rộng hơn tra cứu thuộc tính, vì vậy nó cần đánh giá riêng về quyền hạn, chi phí và nguồn expression.

**Phân biệt input:** người vận hành có thể cung cấp giá trị `job.workers=4`, nhưng không nên được trao quyền cung cấp cả expression `T(...).someMethod()` để ứng dụng đưa vào `SpelExpressionParser` với full evaluation privileges.

## 3. Hai kiểu “động” khác nhau

Từ **động** trong chủ đề này thường bị dùng cho ít nhất hai yêu cầu:

1. **Dữ liệu cấu hình thay đổi theo deployment:** URL, port, flag, threshold được nạp từ file, env variable hoặc ConfigMap trước khi context khởi động. Giá trị có thể thay đổi **giữa những lần start**, nhưng object thường snapshot cấu hình khi được tạo.
2. **Biểu thức có thể thay đổi trong runtime:** một policy/filter/condition string được parse và đánh giá khi ứng dụng chạy. Nếu chuỗi ấy đến từ người dùng hoặc service bên ngoài, phải coi là **code-like input** có rủi ro đặc biệt.

Thứ ba là **dynamic configuration refresh**: nguồn cấu hình đổi khi app vẫn chạy. `MutablePropertySources` có thể thay giá trị mà `Environment` đọc được ở lần lookup sau, nhưng điều đó không có nghĩa `@Value` trên singleton đã được inject lại hay SpEL trong `@Bean` method được evaluate lại.

~~~text
Deployment config (dữ liệu) ──> PropertySources/Environment
                                          │
                                          ▼
                           Placeholder + type conversion
                                          │
                                          ▼
                              Validation/invariants
                                          │
                                          ▼
                               Managed bean instance

Policy expression (nếu thật cần) ──> Input trust boundary
                                          │
                                          ▼
                             Parse với resource limits
                                          │
                                          ▼
                         Restricted EvaluationContext
                                          │
                                          ▼
                            Boolean/value hợp lệ
~~~

Hai nhánh có thể dùng một số dữ liệu giống nhau, nhưng đừng dùng một chuỗi cấu hình bình thường làm mã SpEL nếu mục tiêu chỉ là so sánh số, enum hoặc bật/tắt feature.

## 4. Quy tắc an toàn số một: không parse SpEL từ input chưa tin cậy

SpEL là một ngôn ngữ expression tương đối mạnh. `StandardEvaluationContext` có đầy đủ nhiều khả năng: gọi method trên object, tham chiếu type qua `T(...)`, gọi constructors, bean references nếu cấu hình resolver, truy cập properties và những chức năng khác theo setup.

**Phản mẫu:**

~~~java
String userSupplied = request.getParameter("rule");
Expression expression = new SpelExpressionParser()
        .parseExpression(userSupplied);
Object result = expression.getValue(new StandardEvaluationContext(root));
~~~

Đây là **snippet phản ví dụ**, không phải HTTP application hoàn chỉnh. `request`/`root` là các biến giả định. Nếu cho phép một request gửi nguyên SpEL và đánh giá với `StandardEvaluationContext`, người dùng có thể thực hiện các thao tác ngoài ý định nếu các capability tương ứng được cho phép.

**Hướng khuyến nghị:** không cho client gửi expression. Cho client gửi **dữ liệu** trong một schema cố định, ví dụ:

~~~json
{
  "field": "workers",
  "operator": "<=",
  "value": 8
}
~~~

Một validator/rule engine chỉ chấp nhận `field` thuộc allowlist `{"workers","timeoutMs"}`, `operator` thuộc tập hạn chế, `value` đúng kiểu/range. Code Java thực hiện so sánh trực tiếp. Đây không phải “đã sandbox SpEL”; đó là **không biến untrusted input thành ngôn ngữ có khả năng thực thi**.

Nếu sản phẩm thực sự cần một DSL biểu thức, hãy thiết kế grammar tối thiểu, trình phân tích cú pháp có giới hạn, allowlist biến/toán tử, budget xử lý, logging và contract rõ. Không mở full SpEL chỉ vì tiện.

### Dữ liệu chứa ký tự giống expression chưa chắc phải được execute

Một property value là text, không phải lúc nào cũng phải được parse như SpEL. Nếu nhận `"T(java.lang.System)"` như một nhãn từ external config, nên giữ nó ở dạng chuỗi **dữ liệu** và validate length/character set theo domain. Không tự nối nó vào `"#{...}"` hay gọi `parseExpression(...)`.

Với `@Value`, hãy hiểu pipeline có thể resolve placeholders rồi đánh giá SpEL trong cấu hình framework; vì vậy **không chủ động lồng input chưa tin cậy vào expression syntax**. Với dữ liệu yêu cầu bảo toàn nguyên văn, có thể đọc trực tiếp qua `Environment.getProperty` vào typed factory method hoặc service của ứng dụng, rồi validate trước khi sử dụng.

## 5. StandardEvaluationContext và SimpleEvaluationContext

Spring cung cấp hai implementation quan trọng của `EvaluationContext`.

| Đặc điểm | StandardEvaluationContext | SimpleEvaluationContext |
| --- | --- | --- |
| Mục đích | Ngữ cảnh SpEL tính năng đầy đủ và có thể tùy biến | Ngữ cảnh hỗ trợ **tập con** SpEL có kiểm soát |
| Java type references `T(...)` | Có thể dùng | Không hỗ trợ |
| Constructor invocation `new ...` | Có thể dùng | Không hỗ trợ |
| Bean references `@beanName` | Có thể dùng khi đăng ký BeanResolver | Không hỗ trợ |
| Đọc properties | Theo configured accessors/reflection | Chỉ theo accessors/binding được chọn |
| Ghi properties | Có thể có tùy setup | Chọn read-only hoặc read/write builder rõ ràng |
| Method invocation | Có thể dùng theo resolvers | Phải chọn mức hỗ trợ rõ, không tự coi là unrestricted |
| Phù hợp policy input | Không phù hợp với expression untrusted | Hạn chế surface, nhưng **không là sandbox an toàn tuyệt đối** |

Spring Reference nói `SimpleEvaluationContext` được thiết kế cho những expression categories cần subset, ví dụ data binding và property-based filtering. Builder tiêu biểu:

~~~java
SimpleEvaluationContext readOnly =
        SimpleEvaluationContext.forReadOnlyDataBinding().build();
~~~

Builder này cho phép data-binding **read-only** thông qua `DataBindingPropertyAccessor`. Đánh giá expression có thể đọc getters/properties phù hợp của root object nhưng không hỗ trợ những tính năng rộng như type references/constructors/bean references.

Nếu cần các custom `PropertyAccessor`, có thể dùng `forPropertyAccessors(...)` rồi cấu hình cụ thể. Quyền nào được bật cần có lý do. Tránh chọn `forReadWriteDataBinding()` cho một use case chỉ muốn đọc.

### SimpleEvaluationContext có làm mọi SpEL input an toàn không?

**Không.** Nó làm giảm khả năng biểu thức nhưng chưa đồng nghĩa mọi expression được kiểm chứng an toàn hoặc có chi phí luôn nhỏ. Một getter tùy biến có thể tự thực hiện I/O, cấp phát object lớn, truy vấn DB hoặc tiết lộ dữ liệu nhạy cảm. Nếu root object để lộ getter không nên truy cập, read-only binding vẫn có thể mở ra dữ liệu ấy.

Cách tốt hơn là tạo một **rule input DTO chuyên dụng**, chỉ có những getters thuần dữ liệu được phép đọc, không gắn trực tiếp toàn bộ `ApplicationContext`, `Environment`, service, repository hay object graph nhạy cảm làm root.

## 6. Giới hạn độ dài và chi phí SpEL

Spring Framework 6.2 có giới hạn tích hợp liên quan đến biểu thức. Theo Spring 6.2 Reference:

- **Độ dài expression mặc định tối đa 10.000 ký tự** cho SpEL parser.
- Khi tạo `SpelExpressionParser` bằng mã, có thể cấu hình `maxExpressionLength` qua `SpelParserConfiguration`.
- Với biểu thức do `ApplicationContext` parse (XML, `@Value`...), có thể cấu hình property `spring.context.expression.maxLength` nếu cần điều chỉnh mức mặc định.
- Từ Spring Framework **6.2.19**, `SpelParserConfiguration` còn có hỗ trợ cấu hình `maximumOperations`; mặc định tối đa **10.000 operations** theo tài liệu phiên bản tương ứng. Property chung `spring.expression.maxOperations` cũng được tài liệu Spring 6.2 đề cập.

Đừng tự suy rằng “giới hạn 10.000 ký tự” nghĩa là 10.000 ký tự đầu biểu thức được phép có tác dụng rồi phần còn lại bị cắt. Với parser, expression quá giới hạn **bị từ chối**, không được truncate để tiếp tục evaluate.

### Giới hạn khi tự tạo parser

Với constructor hỗ trợ trong Spring 6.2:

~~~java
SpelParserConfiguration config = new SpelParserConfiguration(
        SpelCompilerMode.OFF, // Không dùng compiled mode trong demo
        null,                 // ClassLoader mặc định
        false,                // Không tự tạo object cho null path
        false,                // Không tự tăng collection
        16,                   // Auto-grow max size nếu bật
        128                   // Expression dài tối đa 128 ký tự
);
SpelExpressionParser parser = new SpelExpressionParser(config);
~~~

Đây là snippet cần import `SpelCompilerMode`, `SpelParserConfiguration`, `SpelExpressionParser`. Trong demo runnable ở dưới có code đầy đủ.

**Cảnh báo:** `maximumExpressionLength`/`maximumOperations` là **resource limits**, không phải authorization rules. Một expression ngắn có thể gọi một getter hoặc method tốn nhiều CPU/I/O nếu context cho phép. Vì vậy vẫn cần hạn chế evaluation context, giới hạn root data, timeout/budget ở tầng ứng dụng nếu cần và tránh untrusted expressions.

### Có cần tăng ngưỡng expression length không?

Không nên tăng chỉ vì một expression dynamic dài bất thường. Biểu thức hàng nghìn ký tự thường là dấu hiệu rằng logic nên chuyển vào Java method/test riêng, typed configuration hoặc rule DSL rõ ràng hơn. Nếu có nhu cầu thực sự, chọn ngưỡng theo độ phức tạp đã đo lường, kiểm tra memory/CPU và version API, không đặt một giá trị khổng lồ vô hạn.

### Phân biệt compiler mode

SpEL có interpreted và compiled modes. Compiler có thể cải thiện performance của những expression ổn định khi types phù hợp, nhưng không được xem là cơ chế an toàn cho input tùy ý. Với policy expressions cần kiểm soát chặt, trước hết giữ grammar đơn giản và evaluation context hạn chế; tối ưu hóa chỉ sau khi benchmark và test.

## 7. Chặn auto-grow và mutation ngoài ý muốn

`SpelParserConfiguration` có những cờ liên quan đến expression navigation:

- `autoGrowNullReferences`: khi bật, parser/evaluator có thể tạo object cho null path trong một số tình huống được hỗ trợ.
- `autoGrowCollections`: khi bật, có thể mở rộng collections khi truy cập index chưa tồn tại.
- `maximumAutoGrowSize`: đặt trần số phần tử auto-grow.

Nếu mục tiêu là **đánh giá một điều kiện chỉ đọc**, thường nên giữ auto-grow **tắt**. Việc tự cấp phát collections hoặc thay object graph trong lúc đánh giá policy có thể gây side effects khó thấy và resource exhaustion nếu input không được kiểm soát.

~~~java
new SpelParserConfiguration(
        SpelCompilerMode.OFF, null,
        false, false, 16, 128);
~~~

Bộ flag trong ví dụ là **defense-in-depth**: ngăn một số hành vi auto-growing không mong muốn, hạn chế chiều dài cú pháp và không dùng compiler. Nó không thay thế rule validation hoặc giới hạn thời gian xử lý ở tầng ứng dụng.

## 8. Các kiểu validation khác nhau

Không phải chỉ cần kiểm tra “string có parse được thành kiểu Java mong muốn” là đủ.

### 8.1. Kiểm tra sự hiện diện

Với một property bắt buộc như endpoint:

~~~java
@Value("${api.endpoint}")
String endpoint;
~~~

Nên fail early nếu key vắng mặt, thay vì dùng một default giả cho production. `PropertySourcesPlaceholderConfigurer` có thể được khai báo `static @Bean` để strict unresolved placeholder behavior:

~~~java
@Configuration(proxyBeanMethods = false)
class Config {
    @Bean
    static PropertySourcesPlaceholderConfigurer placeholders() {
        return new PropertySourcesPlaceholderConfigurer();
    }
}
~~~

Trong JavaConfig method trả BFPP nên là `static` để Spring không instantiate configuration class quá sớm.

### 8.2. Kiểm tra kiểu

Spring có conversion service và nhiều converters mặc định: String thành `int`, boolean, enum và nhiều target types tùy setup. Nhưng:

~~~properties
course.workers=100000
~~~

hoàn toàn có thể convert thành `int`; giá trị vẫn có thể sai business rule. Type conversion là **một bước cần nhưng chưa đủ**.

### 8.3. Kiểm tra miền giá trị

Một số invariant phổ biến:

| Loại giá trị | Kiểm tra cần thiết | Ví dụ |
| --- | --- | --- |
| Thread/worker count | Min, max theo tài nguyên được cấp | `1 ≤ workers ≤ 16` |
| Retry count | Không âm, giới hạn nhỏ | `0 ≤ retries ≤ 5` |
| Timeout | Dương, bounded, đúng đơn vị | `100 ≤ timeoutMs ≤ 30000` |
| Port | Range hợp lệ, tránh reserved nếu cần | `1..65535` |
| Mode | Enum/allowlist | `SAFE` hoặc `FAST` |
| Endpoint URL | URI syntax, scheme allowlist, host, user-info, redirects/DNS policy | Chỉ `https` cho một số clients |
| Feature flag | Boolean đúng grammar, default rõ | `true/false` |
| Path | Canonical path, dưới thư mục cho phép, no traversal | Không dùng input để đọc file tùy ý |
| File size/concurrency | Upper bounds dựa trên memory/capacity | Không cho override vô hạn |
| Expression | Allowlisted grammar, length/ops, context capabilities | Không có type/method/bean references ngoài ý định |

Miền giá trị nên được mô tả trong Java code, typed config class, validation annotations hoặc validator riêng; không dựa vào SpEL cho mọi invariant.

### 8.4. Kiểm tra tương quan nhiều keys

Hai properties hợp lệ riêng rẽ vẫn có thể mâu thuẫn:

~~~text
retry.timeout-ms = 20000
request.deadline-ms = 10000
~~~

Nếu một request deadline là 10 giây nhưng một lượt retry có timeout 20 giây, code phải xác định điều gì đúng. Kiểm tra mối quan hệ giữa các settings ở **constructor/factory** hoặc class-level validator, không chỉ validate từng field.

### 8.5. Fail fast

Một config sai ở connection pool, endpoint hoặc authorization mode tốt nhất nên bị từ chối **trước khi ứng dụng phục vụ traffic**, không âm thầm chọn giá trị khác rồi để lỗi ở request đầu tiên. Khi log lỗi, nêu key và quy tắc bị vi phạm, nhưng không in secret/token.

## 9. Kiểm tra URI/URL cần nhiều hơn một phép parse

Một String như `"https://example.test"` có thể chuyển thành `URI`, nhưng `URI.create` thành công **không chứng minh** endpoint an toàn cho request mạng.

Ví dụ checklist:

1. Parse bằng `URI.create(...)`, bắt lỗi syntax.
2. Kiểm tra scheme allowlist `https` (hoặc `http` chỉ khi dev/test cho phép).
3. Host phải có, không được là chuỗi trống.
4. Không cho phép embedded credentials trong `userInfo`.
5. Nếu ứng dụng không dùng fragment, từ chối fragment.
6. Nếu có port, kiểm tra range phù hợp.
7. Với **URL từ input bên ngoài**, phải có biện pháp chống SSRF: allowlist host/service, kiểm soát DNS resolution/rebinding, private/internal IP ranges, redirect behavior và egress rules. Chỉ kiểm tra scheme `https` **không chống được SSRF**.
8. Không log full URI nếu query chứa token, hoặc làm lộ credentials.

Đối với endpoint được operator kiểm soát trong deployment, schema và validation vẫn giúp tránh lỗi config. Nhưng nếu người dùng bên ngoài được quyết định destination, cần thêm các kiểm soát network/security chặt hơn.

~~~java
URI endpoint = URI.create(raw);
if (!"https".equalsIgnoreCase(endpoint.getScheme())
        || endpoint.getHost() == null
        || endpoint.getUserInfo() != null) {
    throw new IllegalArgumentException("Invalid endpoint");
}
~~~

Đây là snippet validation sơ bộ, **không là implementation chống SSRF toàn diện**.

## 10. Không nhầm @Value, SpEL và typed configuration

### Property placeholder

~~~java
@Value("${course.workers:4}")
private int workers;
~~~

Spring tra key và dùng default `4` nếu key thiếu. Sau đó thực hiện conversion thành `int`. Lúc bean đã tạo, thay Environment source thông thường **không tự re-inject** giá trị.

### SpEL expression

~~~java
@Value("#{2 * 3}")
private int six;
~~~

SpEL tính `6` trong evaluation context do framework chuẩn bị. Đừng cho người dùng tự viết biểu thức để được đánh giá với cùng toàn bộ quyền của container.

### SpEL + placeholder lồng nhau

~~~java
@Value("#{'${course.regions:vn,sg}'.split(',')}")
private String[] regions;
~~~

Trong đường xử lý hỗ trợ, property placeholder có thể được resolve trước rồi nội dung tham gia SpEL. Nhưng **không nên xây expression String bằng việc ghép các value chưa tin cậy**. Nếu mục tiêu chỉ là split danh sách, một converter hoặc typed configuration parser với validation rõ là dễ kiểm soát hơn.

### BeanFactory-level vs runtime evaluation

- PSPC chạy ở pha `BeanFactoryPostProcessor` để xử lý metadata và cung cấp embedded resolver.
- Bean được tạo sau đó nhận giá trị constructor/field/`@Value` qua pipeline injection.
- Một `SpelExpressionParser` do code application tự tạo có evaluation context và giới hạn **do chính application cấu hình**, không nhất thiết giống context dùng cho `@Value` trong BeanFactory.
- Không đánh đồng việc cấu hình một parser riêng với việc đổi toàn bộ parser/evaluator được Spring IoC container sử dụng.

## 11. Cấu hình thời điểm đúng

Thứ tự an toàn khi một value có thể quyết định bean graph:

~~~java
var ctx = new AnnotationConfigApplicationContext();

// 1. Thêm nguồn dữ liệu đã được kiểm tra.
ctx.getEnvironment().getPropertySources().addFirst(safeSource);

// 2. Đặt profile nếu cần.
// ctx.getEnvironment().setActiveProfiles("prod");

// 3. Register config sau khi Environment được chuẩn bị.
ctx.register(AppConfig.class);

// 4. Bootstrap container.
ctx.refresh();
~~~

Đây là snippet; `safeSource` và `AppConfig` phải được khai báo. Nếu dùng constructor `new AnnotationConfigApplicationContext(AppConfig.class)` thì constructor **tự refresh**; việc thêm source sau constructor thường quá muộn đối với những singleton, bean conditions và resolver đã chạy.

**Không đặt bước kiểm tra config quyết định creation policy trong một `@PostConstruct` bất kỳ rồi cho phép các bean khác sử dụng config trước đó.** Nếu các service có khả năng khởi tạo resources nguy hiểm trong constructor, phải validate trước khi tạo resource. Dùng factory method trả một typed, validated object mà service phụ thuộc.

### Thêm sources sau refresh có tác dụng gì?

`Environment.getProperty(key)` đọc sau update có thể thấy value mới. Nhưng:

- `@Value` đã inject vào singleton không tự cập nhật.
- `@Profile`/`@Conditional` đã quyết định bean definitions không tự được xét lại.
- Resource/pool đã được tạo không tự đổi endpoint/timeout.
- Một prototype được tạo về sau có thể resolve mới, tùy đường resolver và metadata đã được xử lý như thế nào.

Nếu yêu cầu runtime refresh, cần một cơ chế reconfiguration riêng với ownership, thread safety và invariant consistency. PSPC và SpEL không tự cung cấp semantics atomic rebind toàn ứng dụng.

## 12. Ví dụ Java 17 hoàn chỉnh: validate đầu vào và đánh giá SpEL hạn chế

Demo chạy với **Java 17**, **Maven**, **Spring Framework 6.2.19**, không dùng Boot, HTTP server hay kết nối mạng. Nó cho thấy:

1. Đọc properties từ `MapPropertySource` trước `refresh()`.
2. Dùng `static @Bean PropertySourcesPlaceholderConfigurer` để strict unresolved placeholder handling.
3. Inject `@Value` vào `@Bean` factory method, chuyển kiểu `int`.
4. Tạo immutable typed `Settings` **chỉ khi values qua validation**.
5. Dùng `SpelExpressionParser` có giới hạn 128 ký tự và auto-grow tắt.
6. Dùng `SimpleEvaluationContext.forReadOnlyDataBinding()` và root DTO chỉ chứa getters an toàn.
7. Từ chối type reference `T(...)` trong restricted context.
8. Từ chối expression dài hơn giới hạn.
9. Chứng minh invalid workers bị từ chối mà không cần khởi tạo resource.

### Cấu trúc

~~~text
safe-config-expressions/
├── pom.xml
└── src/main/java/example/SafeConfigExpressionDemo.java
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>safe-config-expressions</artifactId>
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
~~~

### src/main/java/example/SafeConfigExpressionDemo.java

~~~java
package example;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

public final class SafeConfigExpressionDemo {

    public static void main(String[] args) {
        Settings settings;

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource(
                            "validatedDeployment",
                            Map.<String, Object>of(
                                    "course.workers", "4",
                                    "course.mode", "SAFE",
                                    "course.endpoint",
                                    "https://api.example.test")));

            context.register(Config.class);
            context.refresh();

            settings = context.getBean(Settings.class);
        }

        System.out.println("Settings: "
                + settings.mode() + "/"
                + settings.workers() + "/"
                + settings.endpoint());

        var parser = new SpelExpressionParser(
                new SpelParserConfiguration(
                        SpelCompilerMode.OFF, null,
                        false, false, 16, 128));
        var readOnly = SimpleEvaluationContext
                .forReadOnlyDataBinding()
                .build();

        var inputs = new RuleInputs(
                settings.mode().name(), settings.workers());

        Boolean allowed = parser.parseExpression(
                "mode == 'SAFE' and workers <= 8")
                .getValue(readOnly, inputs, Boolean.class);
        System.out.println("Allowed: "
                + Boolean.TRUE.equals(allowed));

        boolean blockedType = false;
        try {
            parser.parseExpression(
                    "T(java.lang.System).getProperty('user.home')")
                    .getValue(readOnly, inputs);
        } catch (EvaluationException ex) {
            blockedType = true;
        }
        System.out.println("Type access blocked: " + blockedType);

        boolean blockedLength = false;
        try {
            parser.parseExpression("x".repeat(129));
        } catch (ParseException ex) {
            blockedLength = true;
        }
        System.out.println("Long expression blocked: " + blockedLength);

        boolean invalidRejected = false;
        try {
            validateSettings(
                    100, "SAFE", "https://api.example.test");
        } catch (IllegalArgumentException ex) {
            invalidRejected = "workers must be 1..16"
                    .equals(ex.getMessage());
        }
        System.out.println("Invalid range rejected: "
                + invalidRejected);
    }

    public enum Mode {
        SAFE, FAST
    }

    public record Settings(
            int workers, Mode mode, URI endpoint) {
    }

    public static final class RuleInputs {
        private final String mode;
        private final int workers;

        public RuleInputs(String mode, int workers) {
            this.mode = mode;
            this.workers = workers;
        }

        public String getMode() {
            return mode;
        }

        public int getWorkers() {
            return workers;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {
        @Bean
        public static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        public Settings settings(
                @Value("${course.workers}") int workers,
                @Value("${course.mode}") String mode,
                @Value("${course.endpoint}") String endpoint) {
            return validateSettings(workers, mode, endpoint);
        }
    }

    private static Settings validateSettings(
            int workers, String rawMode, String rawEndpoint) {

        if (workers < 1 || workers > 16) {
            throw new IllegalArgumentException(
                    "workers must be 1..16");
        }

        String normalizedMode = rawMode.trim()
                .toUpperCase(Locale.ROOT);
        if (!Set.of("SAFE", "FAST").contains(normalizedMode)) {
            throw new IllegalArgumentException(
                    "mode must be SAFE or FAST");
        }

        URI endpoint = URI.create(rawEndpoint);
        if (!"https".equalsIgnoreCase(endpoint.getScheme())
                || endpoint.getHost() == null
                || endpoint.getUserInfo() != null
                || endpoint.getFragment() != null) {
            throw new IllegalArgumentException(
                    "endpoint must be an HTTPS host URI");
        }

        return new Settings(
                workers, Mode.valueOf(normalizedMode), endpoint);
    }
}
~~~

### Lệnh chạy

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.SafeConfigExpressionDemo
~~~

Stdout của các `System.out.println` trong demo (không tính Maven/logging tùy môi trường):

~~~text
Settings: SAFE/4/https://api.example.test
Allowed: true
Type access blocked: true
Long expression blocked: true
Invalid range rejected: true
~~~

### Giải thích kết quả

- `Settings` là record immutable được tạo từ `@Bean` factory method chỉ sau khi `workers` thuộc `1..16`, `mode` thuộc allowlist và endpoint là HTTPS URI có host phù hợp với checks của demo.
- `SimpleEvaluationContext.forReadOnlyDataBinding()` có thể đọc hai JavaBean getters `getMode()` và `getWorkers()` từ `RuleInputs`. Biểu thức `mode == 'SAFE' and workers <= 8` trả Boolean `true`.
- Với same restricted context, expression sử dụng `T(java.lang.System)` bị từ chối ở evaluation: SimpleEvaluationContext không hỗ trợ Java type references.
- Parser có `maximumExpressionLength=128`; chuỗi `"x".repeat(129)` không qua giới hạn cú pháp nên bị từ chối khi parse.
- `validateSettings(100,...)` bị chặn bởi domain range, **không phải SpEL parser**. Đây là minh họa hai lớp bảo vệ khác nhau.
- URL `https://api.example.test` chỉ là một giá trị; demo không thực hiện HTTP request.
- Demo tách việc đọc cấu hình ở bootstrap khỏi rule evaluation. Trong hệ thống thật, root DTO nên được thiết kế nhỏ và không cung cấp getters có side effects.

**Giới hạn của ví dụ:** nó minh họa restricted SpEL evaluation và kiểm tra URI cơ bản, không chứng minh chống SSRF cho một URL do người dùng đưa vào, không tạo timeout cho getter tùy biến và không biến SpEL thành sandbox đủ an toàn cho expression không tin cậy. Đó là các yêu cầu cần thiết kế riêng nếu input threat model lớn hơn.

## 13. Nếu muốn giảm nữa bề mặt SpEL

Ngay cả `SimpleEvaluationContext` vẫn cần được dùng đúng cách. Có thể bỏ hẳn SpEL khi rule chỉ là vài phép so sánh:

~~~java
boolean allowed =
        settings.mode() == Mode.SAFE
        && settings.workers() <= 8;
~~~

Đây là Java code có type checking ở compile time, dễ đọc, ít bất ngờ và dễ test. Nếu rule được cấu hình bởi developer qua một số enum strategies, hãy dùng pattern strategy/factory hoặc một finite rules registry.

Nếu bắt buộc phải hỗ trợ expression:

1. Chỉ cho phép một grammar subset do ứng dụng kiểm soát.
2. Dùng DTO có data getters thuần, không đặt service hay ApplicationContext vào root.
3. Dùng `SimpleEvaluationContext` read-only và custom accessors khi cần.
4. Đặt max expression length phù hợp.
5. Tắt auto-grow nếu không cần.
6. Với phiên bản có max operations, đặt budget đủ thấp theo nghiệp vụ.
7. Hạn chế kích thước collections được đem vào evaluation.
8. Không hỗ trợ arbitrary method invocations, type references hoặc bean references nếu không có lý do bắt buộc.
9. Rate-limit và audit nếu rule submissions vẫn đến từ khách hàng.
10. Không đánh giá expression trong transaction/lock kéo dài nếu có nguy cơ blocking hoặc resource exhaustion.

**Không nên xem việc thêm regex kiểm tra chuỗi trước `parseExpression` là một sandbox đủ tin cậy.** Cú pháp ngôn ngữ phức tạp; allowlist parser hoặc tránh ngôn ngữ thực thi thường an toàn hơn blacklist một vài substring.

## 14. Bean Validation và Spring Boot typed properties

Spring Core có thể tích hợp Bean Validation khi có provider/thành phần validation thích hợp. Một ứng dụng dùng Jakarta Validation có thể biểu đạt invariants bằng annotations như:

~~~java
public record ClientProperties(
        @Min(1) @Max(16) int workers,
        @NotBlank String mode,
        @NotNull URI endpoint) {
}
~~~

Đây là **snippet minh họa**, cần dependencies `jakarta.validation-api`, provider validation (ví dụ Hibernate Validator) và quá trình gọi validator/integration đúng. **Chỉ gắn annotation lên record không bảo đảm Spring Core tự động validate mọi @Bean record constructor**.

Trong Spring Boot, `@ConfigurationProperties` kết hợp `@Validated` có thể bind một nhóm external properties và thực hiện Bean Validation nếu đã có validation infrastructure phù hợp. Nó khác với việc rải `@Value` vào nhiều field và kỳ vọng mọi constraint tự chạy.

Nếu không muốn thêm provider, validation trong constructor/factory method như ví dụ đầy đủ vẫn là một lựa chọn Java thuần, rõ ràng. Có thể viết một config record nhỏ kèm factory `from(Environment)` và trả thông báo key nào vi phạm invariant.

### Kiểm tra enum/allowlist

Nhận một mode từ nguồn bên ngoài, chuẩn hóa có chủ đích (trim/case) trước khi convert. Nếu muốn **case-sensitive** để tránh nhập nhầm, không tự uppercase. Quy tắc chấp nhận cụ thể phải được ghi trong contract cấu hình.

Đối với properties dạng URL hoặc file path, allowlist và validation theo domain thường quan trọng hơn việc chỉ kiểm tra @NotBlank.

## 15. Property precedence và tính nhất quán

`Environment` có thể chứa nhiều nguồn:

~~~text
high priority → deploymentOverride  : course.workers=4
                systemProperties    : course.workers=8
                systemEnvironment   : COURSE_WORKERS=16
low priority  → applicationDefaults : course.workers=2
~~~

Trong một `StandardEnvironment` thông thường, JVM system properties được ưu tiên hơn OS environment variables; custom `MapPropertySource` `addFirst` có thể ưu tiên hơn cả hai. Nhưng Boot/web contexts có thể bổ sung nhiều sources; cần kiểm tra đúng loại container.

### Giá trị hợp lệ ở nguồn ưu tiên thấp chưa đủ

Một source ưu tiên cao có thể override giá trị hợp lệ bằng một giá trị nguy hiểm. **Validate kết quả cuối sau khi áp dụng precedence**, không chỉ validate file default trước khi deploy.

### Local properties của PropertySourcesPlaceholderConfigurer

`PropertySourcesPlaceholderConfigurer` có local properties riêng; mặc định `localOverride=false` để Environment thường thắng. Nếu `localOverride=true`, local configurer values có thể thắng khi resolve `@Value`, trong khi `Environment.getProperty(...)` vẫn thấy source khác. Điều này tạo nguy cơ validation và injection đi qua **hai đường resolution khác nhau**.

Nếu một typed config factory validate `Environment` nhưng bean khác inject cùng key qua PSPC local override, chúng có thể bất đồng. Cố gắng thiết kế **một bộ sources và precedence chung**. Khi phải duy trì nhiều resolvers, test cả các nguồn và đường injection thật.

## 16. Missing key và default value

Default value giúp trải nghiệm phát triển dễ dàng:

~~~java
@Value("${course.workers:4}")
int workers;
~~~

Nhưng trong production, một missing key có thể báo lỗi deployment đáng lẽ cần dừng. Ví dụ `payment.provider.url` không nên rơi về endpoint giả có thể gửi dữ liệu nhầm nơi.

Chính sách khuyến nghị:

- **Required:** thiếu thì fail fast, nêu tên key/điều kiện (không tiết lộ secret).
- **Optional:** có default được document, validate cả default.
- **Secret:** không log literal hoặc resolve value nguyên văn trong exception messages.
- **External input:** áp dụng schema và whitelist, kể cả khi property file được vận hành bởi deployment pipeline.
- **Deprecated key:** có versioning/migration policy; không âm thầm đọc hai keys với precedence khó dự đoán.
- **Unknown key:** với config nghiêm ngặt có thể báo warning/error thay vì silently ignore.

PSPC strict placeholder resolution và typed object validation giải quyết **hai lớp lỗi khác nhau**: key có tồn tại không, và nếu tồn tại thì value có được ứng dụng chấp nhận không.

## 17. Thứ tự fail-fast theo vòng đời

Một pipeline khởi động an toàn:

~~~text
Load raw config
     ↓
Resolve priority / placeholders
     ↓
Parse basic types
     ↓
Validate ranges/enum/URI + cross-field invariants
     ↓
Create typed immutable Settings
     ↓
Create network clients, thread pools, repositories
     ↓
Start serving traffic
~~~

Không nên khởi động thread pool với `workers=100000` rồi chỉ sau đó một `@PostConstruct` khác mới ném lỗi về domain range. Một bean factory `@Bean Settings` được inject vào clients có thể buộc validation xảy ra trước các clients trong dependency graph.

Nếu có một số config đọc **trước** ApplicationContext, chẳng hạn Boot logging/main settings, validation cần thực hiện ở đúng early hook. Một validator bean chạy sau refresh chưa chắc đủ sớm cho những settings ấy.

## 18. Lỗi thường gặp và cách sửa

| Hiện tượng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Worker count là int nhưng gây cạn tài nguyên | Type conversion không kiểm tra range | Validate min/max trước resource allocation |
| Timeout âm hoặc lớn bất thường | Thiếu domain invariant | Check đơn vị, min/max và cross-field budget |
| @Value nhận literal `${key}` | Lenient resolver hoặc key thiếu | Required property + explicit PSPC strict nếu cần |
| SpEL có quyền gọi method không mong muốn | Dùng StandardEvaluationContext với expression từ input | Không parse untrusted expression; subset context/DSL |
| Expression ngắn nhưng tốn CPU | Getter/operation có chi phí cao | Pure DTO, operation budget, timeout/rate limit |
| Expression rất dài | Không giới hạn input hoặc maxExpressionLength quá lớn | Giới hạn parser và kích thước request |
| Type reference `T(...)` hoạt động ngoài ý định | Context quá rộng | Dùng SimpleEvaluationContext + không nhận arbitrary expressions |
| Expression sửa state lúc chỉ cần đọc | Cho phép assignment/writable property accessor | Read-only context, DTO immutable, tắt auto-grow |
| Expression không đánh giá được `@beanName` trong simple context | SimpleEvaluationContext không hỗ trợ bean references | Đó là giới hạn có chủ đích |
| Expression thay đổi khi runtime nhưng bean vẫn giữ giá trị cũ | @Value đã snapshot trong singleton | Dùng runtime config mechanism rõ |
| @Profile không đổi khi set property muộn | Condition đã được xét ở bootstrap | Cấu hình Environment trước register/refresh |
| URL parse được nhưng trỏ host nội bộ | Chỉ validate syntax/scheme | SSRF protections + network allowlist/egress |
| Config đúng ở file nhưng sai sau deploy | Source ưu tiên cao override | Validate final effective values |
| PSPC và Environment trả hai giá trị khác | Local override/custom sources | Đồng nhất source policy/test đúng injection path |
| Validation annotation không chạy | Chưa có validation provider/integration | Cấu hình validator hoặc manual factory validation |
| Mode lạ được chấp nhận dưới default | Default quá rộng hoặc parser silent fallback | Explicit allowlist và fail-fast |
| Log in nguyên endpoint/token | Diagnostic không redact | Log key/rule, ẩn sensitive value |
| Regex blacklist SpEL bị vượt qua | Cú pháp linh hoạt, blacklist thiếu | Không dùng executable expressions từ input |
| Test chỉ kiểm tra conversion | Không test giá trị biên | Test invalid range, null, blank, size, protocol |
| Thêm parser limit rồi coi là an toàn tuyệt đối | Length/ops không giới hạn quyền truy cập | Kết hợp capability restrictions và data validation |

## 19. Ma trận kiểm thử nên có

| Kịch bản | Input | Kỳ vọng |
| --- | --- | --- |
| Workers hợp lệ | 1, 8, 16 | Chấp nhận |
| Workers sai | 0, -1, 17, 100000 | Từ chối |
| Workers sai kiểu | `abc` | Conversion/bootstrap failure |
| Mode hợp lệ | `SAFE`, `FAST` | Chấp nhận |
| Mode không có trong allowlist | `UNRESTRICTED` | Từ chối |
| Endpoint hợp lệ | `https://api.example.test` | Chấp nhận |
| Endpoint không hợp lệ | `file:///etc/passwd` | Từ chối theo scheme policy |
| Endpoint nhúng credentials | `https://user:pass@host.test` | Từ chối |
| Thiếu required key | không có `course.endpoint` | Context fail trong strict mode |
| Expression hợp lệ | `mode == 'SAFE' and workers <= 8` | Boolean đúng |
| Type reference trong SimpleContext | `T(java.lang.System)` | Evaluation bị chặn |
| Expression quá dài | > parser max length | Parsing bị chặn |
| Mutable root object | Getter có side effect | Phải thay root bằng DTO thuần dữ liệu |
| PropertySource override | High source có workers=100000 | Final effective config bị từ chối |
| Late update sau refresh | workers đổi từ 4 sang 8 | Singleton cũ không tự re-inject |

### Test cả đường tích hợp

Unit test `validateSettings(...)` cho phép kiểm tra nhanh domain invariants. Integration test với `AnnotationConfigApplicationContext` còn cần xác nhận:

- PropertySources được thêm trước `refresh()`.
- `@Bean Settings` thực sự được chọn và validate.
- PSPC strict xử lý missing keys như mong muốn.
- Dependency khác nhận **cùng typed Settings** thay vì đọc raw property theo một resolver khác.
- Không có resource bên ngoài nào được tạo khi config invalid.
- Context được đóng theo lifecycle khi test kết thúc.

Không chỉ assert expression parser return value rồi coi cả ứng dụng an toàn. Vùng rủi ro còn gồm I/O, external clients, concurrency và configuration precedence.

## 20. Câu hỏi tự kiểm tra

**Câu 1.** `@Value("${workers}") int workers` có tự bảo đảm workers nằm trong 1–16 không?

**Đáp án:** Không. Nó có thể chuyển kiểu thành int nhưng không biết miền giá trị của nghiệp vụ. Cần validation riêng.

**Câu 2.** `${...}` và `#{...}` khác nhau thế nào?

**Đáp án:** `${...}` là property placeholder để tra giá trị cấu hình; `#{...}` là SpEL expression được đánh giá. Chúng có thể phối hợp trong pipeline phù hợp nhưng không đồng nhất.

**Câu 3.** Có nên lấy một expression String trực tiếp từ HTTP request rồi dùng `StandardEvaluationContext` đánh giá không?

**Đáp án:** Không. Đây là trust boundary nguy hiểm. Nên dùng dữ liệu typed/allowlist hoặc một DSL hạn chế với evaluation contract rõ.

**Câu 4.** SimpleEvaluationContext có hỗ trợ Java type references, constructors và bean references không?

**Đáp án:** Không, theo Spring Reference. Nhưng restricted context không tự biến mọi expression untrusted thành an toàn tuyệt đối.

**Câu 5.** Giới hạn expression length có thay cho authorization không?

**Đáp án:** Không. Nó giới hạn tài nguyên parser, không kiểm soát đầy đủ những tác vụ object getter/method có thể làm nếu context cho phép.

**Câu 6.** Một URI parse thành công và có scheme HTTPS đã đủ chống SSRF chưa?

**Đáp án:** Chưa. Cần kiểm soát hostname/DNS/IP ranges, redirects, egress và origin policy khi URL đến từ untrusted input.

**Câu 7.** Tại sao validate config trước khi tạo client/thread pool?

**Đáp án:** Để fail fast trước side effects/resources và bảo đảm những settings được dùng đã thỏa domain invariants.

**Câu 8.** Sau refresh, đổi Environment property có tự cập nhật @Value trong singleton không?

**Đáp án:** Không. @Value của singleton thông thường được resolve khi bean tạo, cần cơ chế runtime reconfiguration riêng.

**Câu 9.** Chỉ dùng `@Min`/`@Max` annotations trên record có tự động validate khi Spring Core tạo mọi record không?

**Đáp án:** Không. Cần provider/integration thích hợp hoặc chủ động gọi validator.

**Câu 10.** Nếu local PSPC source và Environment source cho cùng key khác nhau thì validation của một bên bảo đảm bên kia không?

**Đáp án:** Không. Hãy thống nhất precedence/resolution path hoặc kiểm thử cả hai đường.

**Câu 11.** Khi nào nên dùng SpEL thay vì Java?

**Đáp án:** Khi cần một expression được ứng dụng kiểm soát, ngắn, đọc dễ và ngữ cảnh evaluation có capability phù hợp. Nếu chỉ là vài phép so sánh hay routing, Java typed logic thường an toàn/dễ test hơn.

**Câu 12.** Có nên tăng maxExpressionLength lên hàng triệu khi một rule quá dài không?

**Đáp án:** Không mặc định. Xem lại thiết kế; chia logic thành rules/code có kiểm thử và áp resource budgets phù hợp.

## 21. Checklist triển khai

1. Xác định giá trị đang là **dữ liệu** hay **biểu thức được evaluate**.
2. Không parse/evaluate expression nguyên bản từ input chưa tin cậy.
3. Nếu không cần SpEL, dùng Java typed logic hoặc rule DTO/allowlist.
4. Với SpEL cần hạn chế, dùng `SimpleEvaluationContext` read-only và root object chỉ có getters an toàn.
5. Đặt expression length và operation budgets phù hợp Spring version/use case.
6. Tắt auto-grow nếu use case chỉ cần read-only.
7. Không đặt service/resource manager/Environment chứa secrets làm root expression untrusted.
8. Validate required keys, types, min/max, enum, URI, domain allowlist và cross-field invariants.
9. Với endpoint từ client, có SSRF protections ngoài scheme syntax.
10. Không dùng default value để che missing config bắt buộc.
11. Thêm PropertySources trước config parse/refresh khi key ảnh hưởng bootstrap.
12. Xác nhận precedence cuối cùng và đường resolve @Value/PSPC/Environment.
13. Tạo immutable typed Settings trước các resources cần settings ấy.
14. Không coi thay PropertySource sau refresh là live rebinding tự động.
15. Phân biệt Bean Validation annotations với thực tế validator integration.
16. Test valid, missing, type error, range error, malformed URL và extreme input.
17. Giới hạn size/CPU/thread/time ở tầng request nếu hỗ trợ dynamic rules.
18. Không log secrets/raw expressions tùy ý; redact sensitive data.
19. Dùng version Spring tương thích với những parser configuration constructors/limits đã chọn.
20. Bảo đảm errors nêu rõ key/rule vi phạm, nhưng không rò dữ liệu nhạy cảm.

## 22. Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — SpEL Evaluation and EvaluationContext](https://docs.spring.io/spring-framework/reference/6.2/core/expressions/evaluation.html)
- [Spring Framework 6.2 — SpEL Language Reference](https://docs.spring.io/spring-framework/reference/6.2/core/expressions/language-ref.html)
- [Spring Framework 6.2 — SpelParserConfiguration Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/expression/spel/SpelParserConfiguration.html)
- [Spring Framework 6.2 — SimpleEvaluationContext Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/expression/spel/support/SimpleEvaluationContext.html)
- [Spring Framework — Using @Value](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/value-annotations.html)
- [Spring Framework — Environment Abstraction](https://docs.spring.io/spring-framework/reference/core/beans/environment.html)
- [Spring Framework — Container Extension Points](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html)
- [Spring Framework — Bean Validation](https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html)
- [Spring Boot — Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)


</details>

- [Quay lại đầu trang](#back-to-top)
