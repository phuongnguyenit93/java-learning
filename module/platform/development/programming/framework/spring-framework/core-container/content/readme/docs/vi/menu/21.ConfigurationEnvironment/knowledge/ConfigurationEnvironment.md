<a id="back-to-top"></a>

# Môi trường cấu hình

## Menu
- [Environment và hai vai trò thuộc tính, profile](#configuration-environment-001)
- [StandardEnvironment và nguồn JVM system properties, biến môi trường](#configuration-environment-002)
- [PropertySource và thứ tự ưu tiên khi trùng khóa](#configuration-environment-003)
- [Đọc thuộc tính bằng getProperty, containsProperty và getRequiredProperty](#configuration-environment-004)
- [Thêm, thay thế và sắp xếp MutablePropertySources](#configuration-environment-005)
- [Nạp tập tin properties bằng @PropertySource](#configuration-environment-006)
- [Giới hạn của @PropertySource và khác biệt với cơ chế cấu hình Spring Boot](#configuration-environment-007)
- [Tính thời điểm khi thêm nguồn thuộc tính trước refresh](#configuration-environment-008)
- [Bảo vệ thông tin nhạy cảm khi ghi log cấu hình](#configuration-environment-009)

## <a id="configuration-environment-001">Environment và hai vai trò thuộc tính, profile</a>

<details>
<summary>Xem chi tiết</summary>

# Environment trong Spring Core Container: hai vai trò Properties và Profiles

## 1. Mục tiêu học tập

`org.springframework.core.env.Environment` là abstraction gắn với Spring IoC Container để mô tả **hai khía cạnh riêng nhưng liên quan đến môi trường ứng dụng**:

1. **Profiles** – kiểm soát **những bean definitions nào đủ điều kiện được đăng ký** tùy môi trường logic đang active. Ví dụ môi trường `dev` có storage in-memory, còn `prod` có storage triển khai thật.
2. **Properties** – cung cấp cơ chế **tìm kiếm, chuyển kiểu và phân giải giá trị cấu hình** theo các nguồn `PropertySource` có độ ưu tiên khác nhau. Ví dụ `service.url`, `service.timeout-ms` hoặc `spring.profiles.active` có thể đến từ JVM properties, OS environment, file cấu hình hay map tùy chỉnh.

Không nên đồng nhất hai vai trò:

- **Profile** không phải một file cấu hình, cũng không phải giá trị property thông thường.
- **Property** không tự khiến bean definition bật/tắt; chỉ khi property đó được Spring/cấu hình của bạn dùng làm điều kiện hoặc đầu vào cho profile selection thì nó mới ảnh hưởng việc chọn bean.
- `Environment` không chỉ dành cho Spring Boot. Đây là khả năng của **Spring Framework Core Container**; Spring Boot mở rộng hệ thống nạp cấu hình và thứ tự ưu tiên nguồn, không thay thế khái niệm cơ bản.

Sơ đồ tổng quát:

```text
ApplicationContext
      |
      v
Environment (PropertyResolver)
      |
      +--> Vai trò 1: Profiles
      |      - active profiles
      |      - default profiles
      |      - matchesProfiles(...)
      |      - @Profile trên class/method
      |      - điều kiện đăng ký BeanDefinition
      |
      +--> Vai trò 2: Properties
             - PropertySources theo thứ tự
             - getProperty(...)
             - getRequiredProperty(...)
             - kiểu đích, defaults
             - resolvePlaceholders(...)
             - @PropertySource, @Value, Environment injection
```

## 2. Các interface quan trọng

`Environment` kế thừa `PropertyResolver`. Những API hữu ích có thể chia thành:

| Nhóm | API điển hình | Ý nghĩa |
| --- | --- | --- |
| Active profiles | `getActiveProfiles()` | Danh sách tên profile được bật tường minh |
| Default profiles | `getDefaultProfiles()` | Các profile fallback khi không có profile active |
| Match profile | `matchesProfiles(String...)` | Kiểm tra profile expression theo active, hoặc default nếu active trống |
| Property lookup | `getProperty(String)` | Trả giá trị String hoặc `null` |
| Typed property | `getProperty(String, Class<T>)` | Chuyển thành kiểu đích nhờ conversion |
| Default value | `getProperty(String, String)` hoặc typed overload | Trả default khi thiếu property |
| Required property | `getRequiredProperty(String)` | Throw nếu không resolve được |
| Placeholder | `resolvePlaceholders(String)` | Thay thế placeholder; có thể giữ placeholder chưa resolve |
| Strict placeholder | `resolveRequiredPlaceholders(String)` | Throw khi placeholder bắt buộc thiếu |

`ConfigurableEnvironment` mở rộng `Environment`, cho phép cấu hình thêm:

```java
void setActiveProfiles(String... profiles);
void addActiveProfile(String profile);
void setDefaultProfiles(String... profiles);
MutablePropertySources getPropertySources();
```

Đây là các **chữ ký API rút gọn**, không phải một interface Java hoàn chỉnh. Ứng dụng đọc trạng thái bằng `Environment`, còn code bootstrap/configuration quản lý `ConfigurableEnvironment`.

Một số implementation:

- `StandardEnvironment`: ứng dụng Java standalone, mặc định chứa JVM system properties và OS environment.
- `StandardServletEnvironment`: thêm các nguồn tương ứng servlet/JNDI nếu môi trường hỗ trợ.
- `MockEnvironment`: hữu ích trong kiểm thử; không nên dùng như container production mặc định.

`ApplicationContext.getEnvironment()` cung cấp đối tượng Environment mà container dùng cho việc chọn profiles và property resolution. Để cấu hình trước `refresh()`, một `AnnotationConfigApplicationContext` mới có thể gọi `getEnvironment().setActiveProfiles(...)` trước khi đăng ký/refresh cấu hình.

## 3. Vai trò thứ nhất: profiles quyết định bean definitions

**Profile** là tên của một nhóm bean definitions. `@Profile` trên một class cấu hình, một component hoặc một `@Bean` method cho Spring biết định nghĩa bean chỉ hợp lệ trong những điều kiện profile nhất định.

Ví dụ với hai implementation chung interface:

```java
interface Storage {
    String load(String key);
}

final class LocalStorage implements Storage {
    @Override
    public String load(String key) {
        return "local:" + key;
    }
}

final class RemoteStorage implements Storage {
    @Override
    public String load(String key) {
        return "remote:" + key;
    }
}
```

Đây là các class Java **minh họa độc lập về cú pháp**. Spring config có thể phân phối chúng:

```java
@Configuration(proxyBeanMethods = false)
class StorageConfig {

    @Bean("storage")
    @Profile("dev")
    Storage localStorage() {
        return new LocalStorage();
    }

    @Bean("storage")
    @Profile("prod")
    Storage remoteStorage() {
        return new RemoteStorage();
    }
}
```

Đây là **mảnh cấu hình** cần các imports `@Configuration`, `@Bean`, `@Profile` và classes ở trên. Hai method **khác tên Java** nhưng cùng đăng ký bean name `storage`, đồng thời mang các điều kiện profile khác nhau.

- Chỉ `dev` active: definition `localStorage` mang tên `storage` đủ điều kiện.
- Chỉ `prod` active: definition `remoteStorage` mang tên `storage` đủ điều kiện.
- Không profile nào active và không có cấu hình fallback: có thể không có bean `storage`.
- **Cả `dev` và `prod` active:** cả hai điều kiện đều khớp. Hai bean definitions tranh cùng tên; hành vi phụ thuộc chính sách override/registration. Đây là cấu hình cần tránh, **không phải Spring tự chọn “prod ưu tiên hơn dev”**.

Nếu một ứng dụng cho phép đồng thời nhiều profile, hãy thiết kế bean names và điều kiện để không có ambiguity, hoặc dùng selector/qualifier rõ ràng.

### @Profile trên class

```java
@Configuration(proxyBeanMethods = false)
@Profile("dev")
class DevelopmentConfig {

    @Bean
    Storage storage() {
        return new LocalStorage();
    }
}
```

Đây là **mảnh code minh họa**, class `Storage`/`LocalStorage` đã có ở phần trước. `@Profile` trên `@Configuration` khiến toàn bộ nội dung của class (bao gồm các `@Bean` methods và imports thuộc class này) bị bỏ qua nếu profile không khớp.

### @Profile trên @Component

```java
@Component
@Profile("prod")
class ProductionService {
}
```

Mảnh này cần imports và việc component scanning được bật. Bean chỉ được đăng ký nếu profile expression phù hợp. **`@Profile` không tự bật component scanning**.

### Điều kiện áp dụng tại registration time

`@Profile` thường được đánh giá khi Spring xử lý bean definitions (ví dụ configuration class parsing/scanning). Nếu context đã refresh và bean definitions đã được quyết định, **đổi active profiles lúc runtime không tự xóa/recreate các singleton đang sống**. Để thay đổi deployment profile, thường phải khởi tạo một context mới theo cấu hình khác.

## 4. Bật profile: trước refresh là nguyên tắc quan trọng

Cách trực tiếp nhất với Java configuration:

```java
AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext();

context.getEnvironment().setActiveProfiles("dev");
context.register(StorageConfig.class);
context.refresh();
```

Mảnh này cần import `AnnotationConfigApplicationContext` và `StorageConfig`, đồng thời caller chịu trách nhiệm `close()` context. `setActiveProfiles("dev")` nên thực hiện **trước `refresh()`** để điều kiện `@Profile` được áp dụng đúng khi đăng ký metadata.

Có thể bật nhiều profiles:

```java
environment.setActiveProfiles("dev", "metrics");
```

Trong trường hợp này cả `dev` và `metrics` đều active. Không có quy tắc “chỉ một profile được phép active”; Spring hỗ trợ nhiều profiles và điều kiện logic phức tạp.

### Từ properties

Spring Core hỗ trợ thuộc tính đặc biệt:

```properties
spring.profiles.active=dev,metrics
```

Có thể đặt qua JVM system property:

```bash
java -Dspring.profiles.active=dev,metrics -jar app.jar
```

Lệnh này **minh họa** ứng dụng Java có jar hợp lệ, không phải lệnh chạy ví dụ Gradle ở phần 11. `StandardEnvironment` đọc JVM system properties và OS environment; những property sources này có thể chứa `spring.profiles.active`.

Trong môi trường biến hệ điều hành, dạng tên tương ứng có thể được `SystemEnvironmentPropertySource` ánh xạ. Quy tắc ánh xạ/canonical naming trong Spring Boot còn có cơ chế riêng; cần xem đúng môi trường triển khai thay vì suy ra mọi key có thể được biến đổi tự động từ mọi nguồn.

### setActiveProfiles và addActiveProfile khác nhau

`setActiveProfiles("dev")` **thay tập active profiles đã cấu hình tường minh** theo tham số truyền vào.

`addActiveProfile("metrics")` **thêm** profile vào tập active hiện có.

Đừng gọi `setActiveProfiles("metrics")` lần hai rồi tưởng `dev` vẫn đang active. Nếu muốn nhiều profiles, truyền cùng lúc `setActiveProfiles("dev","metrics")` hoặc dùng `addActiveProfile`.

Nếu project dùng `spring.profiles.active`, đừng cố quản lý đồng thời nhiều nơi (system property, config file, programmatic API) mà không xác định nguồn nào là authority. Đặc biệt, việc gọi `getActiveProfiles()` có thể khiến Environment đọc/khởi tạo tập active từ cấu hình đã nạp; hãy set và test rõ ràng trong bootstrap code.

## 5. Default profile: chỉ hiệu lực khi không có active profile

Khái niệm **default profiles** là fallback; Spring mặc định dùng tên `default` nếu bạn không thay.

- Nếu **không có profile active**, profile fallback được xem là hợp lệ trong việc match.
- Nếu có **ít nhất một active profile**, các default profiles **không tự cộng thêm** vào tập effective profiles.
- Có thể đổi fallback bằng `setDefaultProfiles(...)` hoặc thuộc tính `spring.profiles.default`.

Ví dụ:

```java
environment.setDefaultProfiles("fallback");
```

Tại context không active profile nào, `@Profile("fallback")` sẽ được chấp nhận. Nếu gọi `setActiveProfiles("dev")` trên context đó, `fallback` ngừng được sử dụng làm effective default, trừ khi chính nó cũng được bật tường minh.

Bảng:

| Active profiles | Default profiles | `@Profile("dev")` | `@Profile("fallback")` |
| --- | --- | --- | --- |
| Trống | `fallback` | Không match | Match |
| `dev` | `fallback` | Match | Không match |
| `fallback` | `fallback` | Không match | Match |
| `dev,metrics` | `fallback` | Match | Không match |

**Không nhầm `getActiveProfiles()` với “effective profile list”**: API này chỉ trả những profile active thực tế, có thể là mảng rỗng dù một `@Profile("default")` hoặc fallback vẫn match.

### @Profile("default") hay @Profile("fallback")?

Nếu không tùy biến default, `@Profile("default")` là cơ chế Spring Core quen thuộc để cung cấp bean fallback. Nếu bootstrap gọi `setDefaultProfiles("fallback")`, bean muốn đóng vai trò fallback phải dùng `@Profile("fallback")`.

Không nên vừa định nghĩa `@Profile("default")` rồi set default profiles thành `fallback` mà kỳ vọng bean `default` vẫn auto-active.

## 6. Profile expressions: AND, OR và NOT

Spring hỗ trợ profile expressions (Spring 6.2), có thể viết:

```java
@Profile("prod & eu")
@Profile("dev | test")
@Profile("!prod")
@Profile("prod & (eu | us)")
```

Đây là **bốn minh họa độc lập** của annotation, không được dán liên tiếp lên cùng một declaration để làm một ví dụ compile được. Ý nghĩa:

| Expression | Diễn giải |
| --- | --- |
| `prod` | Profile `prod` phải match |
| `!prod` | Profile `prod` không match |
| `prod & eu` | Cả `prod` và `eu` match |
| `dev \| test` | Một trong `dev`, `test` match |
| `prod & (eu \| us)` | `prod` và ít nhất một vùng `eu`/`us` match |

Theo Spring Reference, **không trộn toán tử `&` và `|` trong cùng một expression mà không dùng dấu ngoặc**. Ví dụ `prod & eu | us` không hợp lệ; dùng `prod & (eu | us)` hoặc `(prod & eu) | us` tùy mục đích.

`@Profile({"dev","test"})` là **OR** giữa hai chỉ định: một trong các profile match là đủ. `@Profile({"prod", "!cloud"})` cũng là OR, **không** có nghĩa “prod AND not cloud”. Nếu cần AND, viết trong một expression:

```java
@Profile("prod & !cloud")
```

Những mảnh annotation này là ví dụ cú pháp, chỉ có nghĩa khi gắn với class/method Spring thực tế.

### matchesProfiles(...)

Có thể kiểm tra expression bằng:

```java
boolean enabled = environment.matchesProfiles(
        "prod & (eu | us)");
```

Đây là mảnh minh họa cần `environment` kiểu `Environment`. `matchesProfiles` xét active profiles, hoặc default profiles nếu active trống.

Method overload nhiều chuỗi thường có semantics “một expression match là đủ”. Tránh dùng `getActiveProfiles()` rồi tự viết biểu thức parser thủ công nếu Spring đã có `matchesProfiles(...)`.

### @Profile với overloaded @Bean methods

Nếu nhiều `@Bean` methods **cùng tên Java nhưng overload khác signature**, các điều kiện `@Profile` khác nhau trên overloads **không phải cơ chế đáng tin để chọn overload theo profile**. Spring Reference cảnh báo việc chọn factory method overload sử dụng constructor resolution rules; profile condition có thể chỉ được áp dụng từ declaration đầu.

Muốn chọn hai bean theo profile nhưng cùng logical bean name, dùng **hai tên phương thức Java khác nhau** và `@Bean("logicalName")` như phần 3.

## 7. Vai trò thứ hai: Properties là dữ liệu cấu hình

`PropertySource<?>` đại diện một nguồn giá trị có tên. `Environment` tìm cấu hình qua các nguồn được sắp theo **độ ưu tiên**, còn `Profile` dùng để quyết định bean definitions nào đủ điều kiện. Ví dụ nguồn có thể là JVM system properties, biến môi trường hệ điều hành, map ứng dụng, tệp được nạp bằng `@PropertySource` hoặc nguồn web/đăng ký tùy chỉnh.

Trong một `StandardEnvironment` Java thuần, `systemProperties` thường nằm trước `systemEnvironment`; Spring Boot bổ sung nhiều nguồn và quy tắc ưu tiên riêng. Khi cần biết giá trị thật, kiểm tra **property sources của đúng context**, không đoán từ tên tệp.

## 8. Khi trùng khóa: nguồn có giá trị đầu tiên thắng

```text
MutablePropertySources (đầu danh sách có ưu tiên cao nhất)
  overrides         app.timeout=2500
  systemProperties  app.timeout=4000
  systemEnvironment ...
  fallback          app.timeout=1000
```

`environment.getProperty("app.timeout")` trả `"2500"`, không cộng hoặc ghép `4000`, `1000`. Nếu nguồn trên không có giá trị cho một key, resolver tiếp tục tìm ở nguồn dưới. Một nguồn ưu tiên cao **không thay thế toàn bộ** các keys của nguồn thấp.

Để thêm một nguồn thử nghiệm:

```java
MapPropertySource overrides = new MapPropertySource(
        "overrides",
        Map.<String, Object>of("app.timeout", "2500"));
environment.getPropertySources().addFirst(overrides);
```

Snippet cần imports `java.util.Map`, `MapPropertySource` và biến `environment` kiểu `ConfigurableEnvironment`. `addFirst(...)` đặt nguồn ở đầu; `addLast(...)` đặt nguồn ở cuối. Các thao tác thay thế, chèn giữa và di chuyển nguồn là chủ đề riêng của bài `MutablePropertySources`.

## 9. PropertyResolver: đọc tùy chọn, bắt buộc và chuyển kiểu

Giả sử các keys `app.timeout=2500` và `app.url=https://example.test` đã tồn tại:

```java
String url = env.getProperty("app.url");
String missing = env.getProperty("app.not-present");
int timeout = env.getProperty("app.timeout", Integer.class, 1000);
String mode = env.getProperty("app.mode", "safe");
String required = env.getRequiredProperty("app.url");
```

Đoạn minh họa dùng `env` là một `Environment`. Theo giả định trên, kết quả lần lượt là URL đã cấu hình, `null`, `2500`, `"safe"` và URL bắt buộc. `getRequiredProperty` ném lỗi nếu thiếu giá trị; chuyển sang `Integer` có thể thất bại nếu source chứa chuỗi không hợp lệ.

Để kiểm tra đồng thời nhiều khóa bắt buộc, `ConfigurableEnvironment` hỗ trợ `setRequiredProperties(...)` và `validateRequiredProperties()`; những API này kiểm tra sự hiện diện, **không** thay cho validation định dạng URL, số dương hoặc chính sách bảo mật. Bài về `getProperty`/`containsProperty`/`getRequiredProperty` phân tích riêng từng đường lỗi.

## 10. Placeholder resolution với Environment

Spring cho phép nội suy theo cú pháp:

```text
${app.url}
${app.timeout:1000}
${app.mode:dev}
```

Sau khi xây Markdown, các ký hiệu trên là placeholder chuẩn dạng dollar-brace của Spring. `${key:default}` cung cấp giá trị fallback cho một placeholder chưa có.

Các API:

```java
String message = env.resolvePlaceholders(
        "Connected to ${app.url}");

String strict = env.resolveRequiredPlaceholders(
        "Connected to ${app.url}");
```

Đây là mảnh Java minh họa, giả định `env` là `Environment` và `app.url` đã được cấu hình.

- `resolvePlaceholders` có thể **giữ nguyên phần chưa resolve**.
- `resolveRequiredPlaceholders` ném exception nếu không thể resolve placeholder bắt buộc.
- Khi dùng fallback như `${missing:default}`, placeholder có thể resolve thành `default`.
- Prefix/suffix và value separator là chi tiết của placeholder resolver và có thể cấu hình qua API liên quan; ứng dụng nên tránh tự parse placeholder bằng string replacement thủ công.

Một lỗi thường gặp: đặt `${app.url}` trong một String Java mà tưởng Java compiler sẽ tự thay. Đây là cú pháp của **Spring property resolution**, chỉ được xử lý khi bạn đi qua Spring API hoặc infrastructure hỗ trợ.

## 11. Ví dụ chạy được Java 17: quan sát đồng thời Profiles và Properties

Chương trình dưới đây chỉ dùng Spring Framework **6.2.18** và Java 17. Nó tạo **hai `AnnotationConfigApplicationContext` độc lập**:

- Context A bật `dev` tường minh. Bean `storage` ứng với `dev` được đăng ký, `fallback` không effective. Các nguồn property có `overrides` đặt đầu và `defaults` đặt cuối.
- Context B **không bật profile active**, mà đổi default profile thành `fallback`. Bean `storage` tương ứng `fallback` được đăng ký.
- Cả hai dùng `Environment` inject vào `@Bean settings(Environment env)` để đọc URL và timeout, chứng minh **properties** quyết định giá trị cấu hình, còn **profiles** quyết định bean definition nào tồn tại.
- Mỗi context có property sources riêng và được đóng qua `try-with-resources`; không dựa vào OS environment của máy để quyết định output.

### Cấu trúc thư mục

```text
spring-environment-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/EnvironmentDemo.java
```

### settings.gradle

```groovy
rootProject.name = 'spring-environment-demo'
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
    mainClass = 'EnvironmentDemo'
}
```

### src/main/java/EnvironmentDemo.java

```java
import java.util.Arrays;
import java.util.Map;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

public class EnvironmentDemo {

    public interface Storage {
        String name();
    }

    public record NamedStorage(String name) implements Storage {
    }

    public record Settings(String url, int timeoutMillis) {
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {

        @Bean("storage")
        @Profile("dev")
        public Storage developerStorage() {
            return new NamedStorage("local");
        }

        @Bean("storage")
        @Profile("fallback")
        public Storage fallbackStorage() {
            return new NamedStorage("in-memory");
        }

        @Bean
        public Settings settings(Environment env) {
            return new Settings(
                    env.getRequiredProperty("demo.url"),
                    env.getProperty(
                            "demo.timeout", Integer.class, 1000));
        }
    }

    public static void main(String[] args) {
        runDev();
        runFallback();
    }

    private static void runDev() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            ConfigurableEnvironment env = context.getEnvironment();

            // Cố ý bật profile trước khi register/refresh.
            env.setActiveProfiles("dev");

            // Nguồn ưu tiên thấp, dùng khi nơi khác không có key.
            env.getPropertySources().addLast(
                    new MapPropertySource(
                            "defaults",
                            Map.<String, Object>of(
                                    "demo.url",
                                    "https://default.example.test",
                                    "demo.timeout", "1000")));

            // Nguồn cao nhất; chủ động override cả system props.
            env.getPropertySources().addFirst(
                    new MapPropertySource(
                            "overrides",
                            Map.<String, Object>of(
                                    "demo.url",
                                    "https://dev.example.test",
                                    "demo.timeout", "2500")));

            context.register(Config.class);
            context.refresh();

            System.out.println("dev.active="
                    + Arrays.toString(env.getActiveProfiles()));
            System.out.println("dev.matchDev="
                    + env.matchesProfiles("dev"));
            System.out.println("dev.matchFallback="
                    + env.matchesProfiles("fallback"));
            System.out.println("dev.storage="
                    + context.getBean(Storage.class).name());
            System.out.println("dev.url="
                    + context.getBean(Settings.class).url());
            System.out.println("dev.timeout="
                    + context.getBean(Settings.class).timeoutMillis());
        }
    }

    private static void runFallback() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            ConfigurableEnvironment env = context.getEnvironment();
            // Không bật profile active. Chỉ đổi tên default.
            env.setDefaultProfiles("fallback");

            // Đặt đầu để output không phụ thuộc JVM/OS settings.
            env.getPropertySources().addFirst(
                    new MapPropertySource(
                            "fallbackValues",
                            Map.<String, Object>of(
                                    "demo.url",
                                    "https://fallback.example.test",
                                    "demo.timeout", "1000")));

            context.register(Config.class);
            context.refresh();

            System.out.println("fallback.active="
                    + Arrays.toString(env.getActiveProfiles()));
            System.out.println("fallback.default="
                    + Arrays.toString(env.getDefaultProfiles()));
            System.out.println("fallback.match="
                    + env.matchesProfiles("fallback"));
            System.out.println("fallback.storage="
                    + context.getBean(Storage.class).name());
            System.out.println("fallback.url="
                    + context.getBean(Settings.class).url());
            System.out.println("fallback.timeout="
                    + context.getBean(Settings.class).timeoutMillis());
        }
    }
}
```

### Chạy

Yêu cầu JDK 17+ và Gradle 8+. Từ thư mục `spring-environment-demo`:

```bash
gradle run --quiet
# Hoặc ./gradlew run --quiet nếu dự án đã có Gradle Wrapper.
```

### Dòng do chương trình in

```text
dev.active=[dev]
dev.matchDev=true
dev.matchFallback=false
dev.storage=local
dev.url=https://dev.example.test
dev.timeout=2500
fallback.active=[]
fallback.default=[fallback]
fallback.match=true
fallback.storage=in-memory
fallback.url=https://fallback.example.test
fallback.timeout=1000
```

**Giả định về output:** chương trình được chạy không có cấu hình ngoại lai cố tình áp đặt `spring.profiles.active` cho context B. Context A đặt `dev` bằng API; context B đặt default `fallback` nhưng **không gọi `setActiveProfiles`**. Nếu JVM/system environment vốn đã thiết lập `spring.profiles.active`, context B có thể kế thừa active profiles ngoài ý muốn; muốn test hoàn toàn hermetic, bỏ property `spring.profiles.active` từ nguồn environment hoặc vô hiệu active profiles ngoài trước khi refresh (xem mục kiểm thử). Đây là điểm cần chú ý, không phải lỗi trong cách hoạt động của Spring.

### Phân tích kết quả

**Context A:**

- `env.setActiveProfiles("dev")` làm điều kiện `@Profile("dev")` match, còn `@Profile("fallback")` không match.
- `@Bean developerStorage()` cung cấp một `NamedStorage("local")` qua interface `Storage`.
- `overrides` ở đầu `MutablePropertySources` chứa `demo.url` và `demo.timeout`, thắng `defaults` và system property/env đối với các keys đó.
- `getRequiredProperty("demo.url")` trả URL non-null.
- `getProperty("demo.timeout", Integer.class, 1000)` chuyển `"2500"` thành `Integer 2500`.

**Context B:**

- Chưa bật active profile tường minh, nên `getActiveProfiles()` trả mảng rỗng trong cấu hình sạch; default profile `fallback` được dùng để match `@Profile("fallback")`.
- `@Bean fallbackStorage()` cung cấp `NamedStorage("in-memory")`.
- `fallbackValues` được thêm ở đầu nên settings đọc nguồn đó.
- Hai context độc lập, không có bean instance/shared Environment chung được giả định.

### Làm ví dụ hermetic tuyệt đối khi máy đang có spring.profiles.active

Để output context B luôn dùng default profile ngay cả khi môi trường có `spring.profiles.active`, có thể **sửa hàm `runFallback` bằng một trong hai cách**:

- Chủ động remove key đó khỏi các property sources của context B bằng source ưu tiên cao nhất có semantics phù hợp, hoặc
- Chạy lệnh `gradle run` trong môi trường sạch không set `spring.profiles.active`.

Đây là lưu ý về reproducibility. Bản thân `MapPropertySource` đặt đầu trong ví dụ chỉ override `demo.*`, không override key `spring.profiles.active`. Do vậy không nên khẳng định output profile fallback là bất biến trong mọi máy có cấu hình hệ thống không kiểm soát.

## 12. Lưu ý khi dùng @PropertySource

Spring Core hỗ trợ `@PropertySource` để đưa file vào `Environment`:

```java
@Configuration
@PropertySource("classpath:app.properties")
class FileBasedConfig {

    @Bean
    ServiceOptions options(Environment env) {
        return new ServiceOptions(
                env.getRequiredProperty("service.endpoint"));
    }
}
```

Đây là **mảnh cấu hình minh họa**, cần file `app.properties` trên classpath, các imports và class `ServiceOptions` tương ứng.

Nội dung file:

```properties
service.endpoint=https://service.example.test
service.timeout-ms=2500
```

Sau khi config class được xử lý, source từ file được đăng ký vào Environment. Nhưng hãy nhớ:

- `@PropertySource` **không tự giống** toàn bộ config data machinery của Spring Boot.
- Nếu thêm nhiều property sources cùng key, **thứ tự** quyết định giá trị thắng.
- Placeholder trong resource location có thể được resolve từ các property sources đã có.
- Nếu file không tồn tại, mặc định việc nạp có thể gây lỗi startup; có thể dùng thuộc tính `ignoreResourceNotFound` nếu lựa chọn fallback là có chủ ý.
- File encoding, YAML loading và custom resource factories là các vấn đề riêng; không mặc định mọi định dạng đều được `@PropertySource` đọc theo cùng một cách.

Ví dụ placeholder trong path:

```java
@PropertySource(
    "classpath:configs/${app.region:global}/service.properties")
```

Mảnh này là một annotation minh họa, chỉ có nghĩa khi gắn với một class cấu hình và classpath có resource thực sự. Nếu `app.region` có trong một property source được nạp trước, Spring chọn region đó; nếu không, dùng `global`.

## 13. @Value, Environment injection và placeholder configurer

Hai cách lấy properties trong bean:

**Cách 1: dùng Environment như dependency**

```java
@Bean
ClientOptions clientOptions(Environment env) {
    String url = env.getRequiredProperty("client.url");
    int timeout = env.getProperty(
            "client.timeout-ms", Integer.class, 1000);
    return new ClientOptions(url, timeout);
}
```

Đây là mảnh minh họa cần type `ClientOptions` do ứng dụng định nghĩa, với constructor tương ứng. Ưu điểm: logic thiếu property, default và chuyển kiểu hiển thị rõ.

**Cách 2: dùng @Value**

```java
@Component
class ClientSettings {
    @Value("${client.url}")
    private String url;

    @Value("${client.timeout-ms:1000}")
    private int timeoutMillis;
}
```

Đây là **mảnh code minh họa**; cần imports Spring và annotation infrastructure, property sources chứa những keys phù hợp. Đoạn Java sử dụng **chuỗi `"${...}"`** hợp lệ: **không đặt backslash trước ký tự `$`** trong Java string.

### Lưu ý quan trọng trong Spring Core

Khi Spring phải resolve placeholders trong bean definitions và `@Value`, cơ chế `PropertySourcesPlaceholderConfigurer` giúp đăng ký placeholder resolver và áp dụng strict resolution khi cấu hình phù hợp. Trong ứng dụng Spring Core, nên dùng một **`static @Bean`** để đăng ký infrastructure processor này khi muốn kiểm soát chính xác:

```java
@Configuration(proxyBeanMethods = false)
class PlaceholderConfig {
    @Bean
    static PropertySourcesPlaceholderConfigurer
            propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }
}
```

Đây là mảnh cấu hình cần import `PropertySourcesPlaceholderConfigurer`. Spring `ApplicationContext` cũng có một cơ chế embedded value resolver mặc định hỗ trợ `@Value` theo cách **lenient** trong một số trường hợp; nếu placeholder chưa tồn tại, chuỗi placeholder có thể đi qua chưa resolve, thay vì fail-fast như bạn mong muốn.

Với cấu hình quan trọng, hãy dùng API bắt buộc như `getRequiredProperty`, cấu hình strict resolver, và test startup thiếu key. **Đừng suy rằng `@Value("${key}")` luôn ném một loại exception duy nhất trong mọi bản Spring/Core/Boot setup**.

## 14. Profiles và properties tương tác thế nào?

Trong Spring Core, profile được kích hoạt **có thể được biểu diễn bằng property**:

```properties
spring.profiles.active=dev,metrics
spring.profiles.default=fallback
```

Khi Environment cần xác định tập active/default, nó có thể đọc từ các property sources chứa các keys đặc biệt này. Do đó **property resolution cấp dữ liệu cho profile selection**.

Nhưng không có suy luận tự động kiểu:

```properties
app.mode=prod
```

là tương đương với `spring.profiles.active=prod`. Nếu bạn muốn `app.mode` quyết định profile, bạn phải chủ động chuyển mapping thành `setActiveProfiles` trước khi xử lý bean definitions, hoặc dùng cơ chế điều kiện khác.

Đặc biệt, nếu gọi `env.setActiveProfiles("dev")` rồi thay `spring.profiles.active` trong một map sau khi context đã refresh, Spring không tự quét lại @Configuration để đổi tập beans đang tồn tại.

### Profile không phải namespace của property

Không có quy tắc Spring Core tự động: bật `prod` thì tất cả key `app.url` tự chuyển sang một file `app-prod.properties`. Bạn phải:

- Đăng ký `@PropertySource`/PropertySources có điều kiện; hoặc
- Dùng cơ chế Spring Boot config data `application-prod.properties` khi thật sự đang dùng Spring Boot.

Spring Boot có nhiều nguồn cấu hình/profile-specific config và precedence riêng; ứng dụng Spring Core thuần không tự có toàn bộ hành vi đó.

### Một cách thiết kế an toàn

- Dùng **profiles** để chọn implementation/bean graph: fake vs remote client, in-memory vs database, monitoring theo môi trường.
- Dùng **properties** để truyền tham số runtime cho cùng implementation: hostname, timeout, retry, limit.
- Tránh tạo profile mới cho mỗi con số khác nhau. `prod-timeout-1000` không phải profile tốt khi `timeout=1000` là data.
- Tránh quản lý secrets bằng profile names; profile name chỉ là selector logic, không phải nơi chứa secret.

## 15. Property source ordering với ứng dụng web

Trong `StandardServletEnvironment` thông dụng, các sources được khai báo theo thứ tự ưu tiên tham khảo:

1. ServletConfig init parameters khi có.
2. ServletContext init parameters.
3. JNDI environment entries nếu tích hợp JNDI được bật và có.
4. JVM system properties.
5. OS environment variables.

Tập sources thực tế còn phụ thuộc context, môi trường runtime và cấu hình web application. Nếu thêm custom source bằng `addFirst(...)`, nó có thể vượt tất cả các nguồn trên.

Vì vậy không viết một invariant như “JVM system property luôn có priority cao nhất trong mọi Spring app”. Điều đó đúng với **`StandardEnvironment` mặc định cho ứng dụng standalone** khi chưa thêm nguồn khác, nhưng không đúng cho tất cả môi trường.

Với Spring Boot, thứ tự còn khác vì có command-line args, test overrides, config data, random, application properties và những nguồn khác. Khi điều tra một key bất ngờ, phải xem source nào đang **thắng theo thứ tự thực tế**.

## 16. Cách kiểm tra nguồn đã cung cấp property

`MutablePropertySources` có thể duyệt theo thứ tự. Một mảnh diagnostic hữu ích:

```java
for (PropertySource<?> source :
        environment.getPropertySources()) {
    Object value = source.getProperty("demo.url");
    if (value != null) {
        System.out.println(source.getName() + " -> " + value);
    }
}
```

Đây là mảnh minh họa cần `environment` kiểu `ConfigurableEnvironment`, import `PropertySource`. **Cẩn trọng bảo mật**: đừng log trực tiếp secret values (token, password, private key). Khi debug, có thể chỉ log source name và việc key tồn tại thay vì giá trị thật.

### Property source không nhất thiết enumerable

Một số `PropertySource` implementation có thể chứa API truy xuất từng key nhưng không hỗ trợ enumerate mọi key. Vì vậy không dùng `getPropertyNames()` bừa bãi trên mọi loại source. Để phân tích một key cụ thể, `source.getProperty(key)` là điểm bắt đầu hợp lý.

### Tránh tự build một PropertySource hỗn hợp kém rõ ràng

Nếu cùng key xuất hiện nhiều nơi, tốt hơn thiết kế precedence có tài liệu hóa: ví dụ “operator override”, “system properties”, “environment”, “defaults”. Không phụ thuộc vào vị trí ngẫu nhiên của một file import hoặc thứ tự quét class khi giá trị ảnh hưởng production safety.

## 17. Test profiles một cách độc lập

Trong unit/integration tests, cần kiểm tra cả:

1. **Bean registration** theo profile.
2. **Property value** resolve theo thứ tự source.
3. Khi thiếu required key, context fail-fast thay vì chạy với `null`.
4. Không có collisions khi nhiều profiles active.
5. Không dùng lại context đã refresh để giả lập việc đổi profile runtime.

Một test Spring TestContext có thể dùng:

```java
@ActiveProfiles("test")
@ContextConfiguration(classes = TestConfig.class)
class TestProfileConfig {
}
```

Đây là **mảnh minh họa**, yêu cầu `spring-test`, JUnit integration và class `TestConfig`. `@ActiveProfiles` thuộc framework kiểm thử, không phải annotation bạn phải dùng trong production code.

### Test thuần Java không cần JUnit

Có thể tạo `AnnotationConfigApplicationContext` mới cho mỗi kịch bản và assert:

```java
if (!ctx.getEnvironment().matchesProfiles("dev")) {
    throw new AssertionError("dev profile expected");
}
if (!"local".equals(ctx.getBean(Storage.class).name())) {
    throw new AssertionError("local storage expected");
}
```

Mảnh này giả định context/dev config và type `Storage` của ví dụ chính. Không dùng output phụ thuộc event timing/log framework làm assertion khi chỉ cần kiểm tra giá trị.

### Làm test độc lập với spring.profiles.active bên ngoài

Nếu CI hoặc IDE đặt `-Dspring.profiles.active=prod`, một context bạn định thử “không active profile” có thể tự nhận `prod`. Tránh test mơ hồ bằng:

- Tạo `ConfigurableEnvironment` mới và **loại bỏ/ghi đè source ngoài ảnh hưởng tới `spring.profiles.active`** trước khi lookup active profiles.
- Đặt active profiles tường minh cho kịch bản cần profile cụ thể.
- Dùng `MockEnvironment` để kiểm soát property sources trong unit tests.
- Nếu cần kiểm thử default profiles, bảo đảm `spring.profiles.active` thật sự vắng mặt thay vì chỉ bỏ lời gọi `setActiveProfiles`.

Điều đó quan trọng vì **default profile chỉ có hiệu lực khi không có active profile**; test phụ thuộc vào trạng thái máy đang chạy là một test dễ flaky.

## 18. Lỗi thường gặp và cách xử lý

| Hiện tượng | Nguyên nhân khả dĩ | Cách sửa |
| --- | --- | --- |
| Bean `@Profile("dev")` không có | `dev` chưa active trước lúc definitions được xử lý | Kiểm tra `getActiveProfiles()` trước `refresh()` |
| `@Profile("default")` không active | Có profile khác active, hoặc default profiles đã bị đổi | Xem cả `getActiveProfiles()` và `getDefaultProfiles()` |
| Đổi profile rồi bean cũ vẫn tồn tại | Refresh đã quyết định bean graph | Dựng context mới theo cấu hình đúng |
| Cả dev/prod beans cùng tranh tên | Bật đồng thời hai profiles mutually exclusive | Thiết kế điều kiện không chồng lấn |
| `@Profile({"prod","!cloud"})` match quá rộng | Array có logic OR, không phải AND | Dùng `@Profile("prod & !cloud")` |
| Profile expression có `&` và `|` không ngoặc | Biểu thức không hợp lệ | Viết `prod & (eu | us)` |
| URL/timeout khác mong đợi | Nguồn property có priority cao hơn | Duyệt `MutablePropertySources` và tìm source thắng |
| `getProperty` trả `null` | Key chưa có hoặc source không được nạp | `containsProperty`/`getRequiredProperty`, kiểm tra `@PropertySource` |
| String `"abc"` không đổi sang int | Conversion error | Dùng đúng kiểu và validate dữ liệu |
| `${key}` vẫn còn nguyên | Placeholder resolver lenient hoặc key thiếu | Đăng ký configurer strict, dùng `getRequiredProperty` |
| `@Value` không inject đúng | Annotation processor/configurer bị thiếu hoặc nguồn cấu hình không tồn tại | Kiểm tra config, typed property, processor registration |
| `@PropertySource` không thấy file | Sai classpath/path hoặc file chưa được đóng gói | Kiểm tra resource thật và config class có được load không |
| Bật profile nhưng không tự nạp application-dev.properties | Đang chạy Spring Core, không phải Boot config data | Tự cấu hình PropertySources hoặc dùng Boot thực sự |
| Property chứa secret bị in ra logs | Diagnostic dump toàn bộ keys/values | Chỉ log source names/boolean presence, redact secrets |
| Context B default profile test bị prod override | OS/JVM có `spring.profiles.active` | Cô lập test environment |
| Đặt `setActiveProfiles` sau refresh | Đã quá muộn cho `@Profile` selection | Cấu hình profile trước register/refresh |
| Dùng profile để lưu giá trị như timeout | Nhầm selector và data | Dùng properties cho parameterization |

## 19. Checklist trước khi đưa lên production

- App sử dụng Spring Core hay Spring Boot? Đã phân biệt cơ chế nạp cấu hình của hai môi trường?
- Profile hiện tại được set programmatically hay lấy từ `spring.profiles.active`? Có rủi ro hai nguồn cạnh tranh không?
- Profile mặc định đã rõ, hay test/CI vô tình làm nó mất tác dụng?
- Có trường hợp hai profiles active đồng thời gây duplicate bean definitions không?
- Có dùng expression logic chính xác (`&`/`|`/`!`, ngoặc) không?
- Các bean implementations được chọn theo profile có chung interface/qualifier phù hợp không?
- Mọi required property có được validate và fail-fast không?
- Property sources có thứ tự ưu tiên rõ ràng, có operator override và fallback như mong đợi không?
- Các property nhạy cảm có tránh log và lưu trữ không phù hợp không?
- Có test startup với thiếu property, sai type, nhiều profiles cùng active không?
- Có tránh đổi profile sau `refresh()` và kỳ vọng beans tự thay không?
- Có lưu ý rằng `@Value` placeholders trong Spring Core cần processor/configurer phù hợp khi cần strict behavior không?

## 20. Tài liệu chính thức

1. [Spring Framework Reference — Environment Abstraction](https://docs.spring.io/spring-framework/reference/core/beans/environment.html): hai vai trò profiles/properties, `@Profile`, profile expressions, default profile, `PropertySources`, `@PropertySource`.
2. [Spring Framework 6.2 Javadoc — Environment](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/Environment.html): interfaces và `getActiveProfiles/getDefaultProfiles/matchesProfiles`.
3. [Spring Framework 6.2 Javadoc — ConfigurableEnvironment](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/ConfigurableEnvironment.html): active/default profiles và mutable property sources.
4. [Spring Framework 6.2 Javadoc — StandardEnvironment](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/StandardEnvironment.html): property sources mặc định trong ứng dụng standalone.
5. [Spring Framework 6.2 Javadoc — MutablePropertySources](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/MutablePropertySources.html): `addFirst/addLast/addBefore/addAfter` và precedence.
6. [Spring Framework 6.2 Javadoc — PropertyResolver](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/PropertyResolver.html): getProperty, required properties và placeholders.
7. [Spring Framework Reference — Using @Value](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/value-annotations.html): dependency injection bằng placeholders, lenient vs strict resolution.
8. [Spring Framework Reference — Testing with Environment Profiles](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/env-profiles.html): `@ActiveProfiles` và test contexts.

**Ghi nhớ:** `Environment` có **hai vai trò**. **Profiles** trả lời “bean definition nào được đăng ký?”, còn **properties** trả lời “giá trị cấu hình nào được lấy từ source nào?”. Cả hai có thể giao nhau qua `spring.profiles.active`, nhưng khác trách nhiệm và nên được kiểm tra độc lập.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-environment-002">StandardEnvironment và nguồn JVM system properties, biến môi trường</a>

<details>
<summary>Xem chi tiết</summary>

# StandardEnvironment và nguồn JVM system properties, biến môi trường trong Spring Core Container

## 1. Vấn đề cần giải quyết

Một ứng dụng Java thường cần cấu hình khác nhau giữa laptop, máy thử nghiệm, CI và môi trường production: URL dịch vụ, số lần retry, chế độ chạy, feature flags hoặc timeout. Thay vì viết cứng các giá trị vào source code, Spring Core Container cung cấp trừu tượng **`Environment`** và **`PropertySource`** để tra cứu cấu hình nhất quán từ nhiều nguồn.

Trong ứng dụng Java tiêu chuẩn **không có ngữ cảnh servlet**, `StandardEnvironment` là implementation nền tảng. Ngay khi được tạo, nó có hai property sources mặc định:

1. **`systemProperties`** — JVM system properties, thường đặt bằng tham số `-Dkey=value` trước `-jar` hoặc bằng `System.setProperty(...)`.
2. **`systemEnvironment`** — biến môi trường của **tiến trình hệ điều hành**, lấy thông qua `System.getenv()`, có thể được shell, CI, Docker hoặc orchestrator truyền vào khi khởi chạy.

**Thứ tự mặc định: `systemProperties` có độ ưu tiên cao hơn `systemEnvironment`.** Nếu hai nguồn đều cung cấp cùng một property logic, Spring sử dụng giá trị tìm thấy ở `systemProperties` và không ghép hai giá trị lại. Điều này cho phép một JVM cụ thể override giá trị environment dùng chung.

Ví dụ:

```text
JVM system property : -Dedu.demo.mode=from-jvm
OS environment      : EDU_DEMO_MODE=from-env

StandardEnvironment.getProperty("edu.demo.mode")
=> "from-jvm"
```

`StandardEnvironment` cũng hỗ trợ các API cấu hình chung như `getProperty`, chuyển kiểu, placeholder resolution, required properties và profiles. Tuy nhiên, **bản thân nó không phải Spring Boot configuration engine** và không tự tải `application.properties` chỉ vì tệp nằm trên classpath.

## 2. Các khái niệm: Environment, PropertySource và MutablePropertySources

| Thành phần | Vai trò | API tiêu biểu |
|---|---|---|
| `Environment` | Giao diện đọc properties và profiles | `getProperty(...)`, `acceptsProfiles(...)`, `getActiveProfiles()` |
| `PropertyResolver` | Hợp đồng tra cứu, chuyển kiểu và resolve placeholders | `containsProperty`, `getRequiredProperty`, `resolvePlaceholders` |
| `ConfigurableEnvironment` | Cho phép chỉnh property sources/profiles | `getPropertySources()`, `setActiveProfiles(...)` |
| `StandardEnvironment` | Implementation mặc định thích hợp cho ứng dụng Java non-web | Có `systemProperties` và `systemEnvironment` |
| `PropertySource<T>` | Một nguồn có tên, cung cấp `getProperty(key)` | `getName()`, `getProperty(...)` |
| `MutablePropertySources` | Danh sách sources có **thứ tự ưu tiên** | `addFirst`, `addLast`, `addBefore`, `addAfter`, `remove`, `replace` |
| `MapPropertySource` | Property source dựa trên một `Map<String,Object>` | Hữu ích cho override cấu hình/test |
| `SystemEnvironmentPropertySource` | Nguồn dành cho OS environment có hỗ trợ tên biến đặc biệt | Resolve `demo.mode` qua dạng có dấu gạch dưới/viết hoa khi phù hợp |

Một `StandardEnvironment` là một **đối tượng đọc cấu hình**, không phải bean factory tự tạo toàn bộ application beans. Trong `ApplicationContext` được cấu hình chuẩn, context có `Environment` và cung cấp nó cho các cơ chế như `@Value`, điều kiện `@Profile` và những thành phần ứng dụng inject `Environment`.

Đường lookup:

```mermaid
flowchart TD
    A["env.getProperty(key)"] --> B["propertySources theo thứ tự ưu tiên"]
    B --> C{"systemProperties có key?"}
    C -->|Có| D["Dùng JVM system property"]
    C -->|Không| E{"systemEnvironment resolve được key?"}
    E -->|Có| F["Dùng OS environment variable"]
    E -->|Không| G["Trả null hoặc default, hay ném lỗi với getRequiredProperty"]
```

Khi tự thêm `MapPropertySource` ở đầu danh sách, đường lookup sẽ kiểm tra source mới **trước** hai nguồn mặc định. Sơ đồ trình bày thứ tự trong cấu hình mặc định; Spring Boot và những loại `ApplicationContext` khác có thể thêm nhiều sources trước hoặc sau các nguồn này.

## 3. Nguồn thứ nhất: JVM system properties

**JVM system properties** là tập key-value do Java Runtime quản lý trong phạm vi JVM. Chúng xuất hiện khi khởi chạy Java với `-D` hoặc qua API `System`.

Ví dụ khi chạy một JAR:

```bash
java -Dedu.demo.mode=production -Dedu.demo.retries=8 -jar app.jar
```

Ở đây `-Dedu.demo.mode=production` và `-Dedu.demo.retries=8` là **Java launcher options**, không phải args truyền vào `main(String[] args)`.

Trong code Java:

```java
String mode = System.getProperty("edu.demo.mode");
String retries = System.getProperty("edu.demo.retries");

System.setProperty("edu.demo.feature", "enabled");
```

Đây là **snippet Java**, không phải ứng dụng độc lập. `System.getProperty(...)` trả chuỗi hoặc `null` nếu không có property. `System.setProperty(...)` thay đổi map JVM properties của tiến trình hiện tại, không thay OS environment variables, không sửa cấu hình của JVM khác và không tự lưu bền vững giữa các lần chạy.

### 3.1. Nguồn systemProperties của Spring

`StandardEnvironment` đăng ký `PropertySource` tên **`systemProperties`**, truy cập system properties của JVM. Hai cách đọc thường tương đương khi chỉ xét một key nằm ở nguồn này:

```java
String direct = System.getProperty("edu.demo.mode");
String fromSpring = new StandardEnvironment()
        .getProperty("edu.demo.mode");
```

Tuy nhiên `fromSpring` **không nhất thiết bằng `direct` trong mọi cấu hình**: Spring có thể fallback sang OS environment khi JVM property không tồn tại, hoặc có sources được người dùng thêm vào với độ ưu tiên cao hơn.

Tên property source là `systemProperties`, còn **tên key** ví dụ là `edu.demo.mode`. Không nhầm hai khái niệm; API `getProperty("systemProperties")` không đồng nghĩa truy xuất property source đó.

### 3.2. Đặt -D đúng vị trí

```bash
# Đúng: option thuộc java launcher.
java -Dedu.demo.mode=cli -jar app.jar

# Khác nghĩa: đây là argument của ứng dụng sau -jar.
java -jar app.jar -Dedu.demo.mode=cli
```

Lệnh thứ hai có thể đưa chuỗi `-Dedu.demo.mode=cli` vào `main(args)`; nó **không tự thiết lập JVM system property**, trừ khi ứng dụng tự parse argument này.

Với IDE, cần đặt `-D...` trong **VM options** chứ không phải program arguments. Với Maven, `-D...` thường có nghĩa user properties của Maven process/plugin; để kiểm chứng Java launcher chính xác, bài thực hành phía dưới chủ động dùng một **JAR chạy bằng `java -jar`**.

## 4. Nguồn thứ hai: OS environment variables

**Biến môi trường** là dữ liệu được tiến trình nhận từ hệ điều hành/parent process lúc khởi chạy. Khác với Java system properties, chúng không được thêm bằng `System.setProperty()`.

Ví dụ shell:

```bash
# Bash/zsh: đặt biến cho đúng lệnh.
EDU_DEMO_MODE=from-env java -jar app.jar

# Bash/zsh: export cho các lệnh con sau.
export EDU_DEMO_MODE=from-env
java -jar app.jar
```

PowerShell:

```powershell
$env:EDU_DEMO_MODE = "from-env"
java -jar app.jar
```

Windows Command Prompt:

```bat
set "EDU_DEMO_MODE=from-env"
java -jar app.jar
```

Trong Java, ta đọc trực tiếp:

```java
String value = System.getenv("EDU_DEMO_MODE");
```

Đây là snippet Java theo ngữ cảnh. `System.getenv("EDU_DEMO_MODE")` có thể trả `null` nếu shell/host không cung cấp biến đó. Không có một API chuẩn Java tương đương `System.setProperty` để mọi chương trình có thể tùy ý sửa environment map của chính tiến trình hoặc parent process rồi bảo đảm Spring tự nhận.

### 4.1. Tại sao key trong Java khác tên biến shell?

Nhiều shell không thuận tiện hoặc không cho đặt tên environment variable có dấu chấm (`edu.demo.mode`). Spring dùng `SystemEnvironmentPropertySource` để hỗ trợ một số biến thể tên khóa: dấu chấm hoặc gạch ngang có thể được đối chiếu với dấu gạch dưới; tùy hệ điều hành và source, Spring có thể thử dạng chữ hoa.

Ví dụ:

```text
Spring property key : edu.demo.mode
OS environment      : EDU_DEMO_MODE=from-env

env.getProperty("edu.demo.mode") -> "from-env"
```

Đây là một **quy tắc lookup của `SystemEnvironmentPropertySource`**, không phải Java tự đổi tên khi `System.getenv("edu.demo.mode")` được gọi. Với API `System.getenv`, bạn phải dùng **tên biến environment thực tế**.

Các vấn đề cần chú ý:

- Trên Linux/macOS, tên environment variables thường **phân biệt chữ hoa/chữ thường**. `EDU_DEMO_MODE` và `edu_demo_mode` có thể là hai biến khác nhau; không dựa vào cách đặt tên không nhất quán.
- Nếu tồn tại nhiều biến gần tương đương sau chuẩn hóa, ví dụ có cả dấu chấm và gạch dưới, ưu tiên matching có thể khó đoán. Hãy **quy định một convention duy nhất**, thường dùng uppercase và underscore cho OS environment.
- Không cố suy toàn bộ quy tắc **relaxed binding của Spring Boot** từ `SystemEnvironmentPropertySource` trong Spring Framework thuần. Boot có additional binding/configuration rules.
- Không tự động suy “mọi key trong mọi custom `MapPropertySource` đều được viết hoa/đổi dấu gạch dưới”. Đặc tính name adaptation là của **system environment source**, không phải hợp đồng chung của mọi map.

### 4.2. Biến môi trường được truyền khi khởi chạy

Trong Docker hoặc CI, môi trường thường được cấp ở cấp process:

```yaml
services:
  worker:
    image: example/worker:latest
    environment:
      EDU_DEMO_MODE: from-env
      EDU_DEMO_RETRIES: "7"
```

Đây là **Docker Compose snippet minh họa**, giả định image và service tồn tại. Kể cả khi biến được đặt trong container, JVM process chỉ nhận được nó nếu runtime/entrypoint thực sự truyền môi trường vào process. Chạy qua `sudo`, shell wrapper, remote service hoặc CI runner có thể thay đổi tập environment được kế thừa.

**Quan trọng về bảo mật:** không in bừa toàn bộ `System.getenv()` hoặc `System.getProperties()` ra log production. Chúng có thể chứa token, mật khẩu hoặc endpoint nhạy cảm. Ví dụ học tập chỉ in các property vô hại do người học chủ động đặt.

## 5. Thứ tự ưu tiên mặc định: systemProperties thắng systemEnvironment

Theo tài liệu `StandardEnvironment` của Spring, hai default sources được tìm theo thứ tự:

```text
Độ ưu tiên cao
   1. systemProperties
   2. systemEnvironment
Độ ưu tiên thấp
```

Nếu người dùng đặt đồng thời:

```bash
EDU_DEMO_MODE=from-env java -Dedu.demo.mode=from-jvm -jar app.jar
```

thì kết quả:

```text
System.getProperty("edu.demo.mode")  -> "from-jvm"
System.getenv("EDU_DEMO_MODE")        -> "from-env"
env.getProperty("edu.demo.mode")     -> "from-jvm"
```

Property lookup của Spring trả **giá trị từ nguồn đầu tiên chứa key theo resolution logic**. Nó không nối `from-jvm;from-env`, không có “hai giá trị cùng hiệu lực”, và cũng không cộng dồn các giá trị số. Đây là **override/precedence**.

### 5.1. Fallback khi nguồn cao không có key

Nếu JVM **không đặt** `edu.demo.mode` mà OS environment có `EDU_DEMO_MODE`, `StandardEnvironment.getProperty("edu.demo.mode")` trả `from-env`.

Nếu cả hai đều không có:

```java
env.getProperty("edu.demo.mode")                // null
env.getProperty("edu.demo.mode", "development") // "development"
env.containsProperty("edu.demo.mode")           // false, nếu không có ở nguồn khác
```

Đây là **snippet theo ngữ cảnh**, giả định biến `env` kiểu `Environment` và không có custom source chứa key. `getProperty(key, defaultValue)` áp default khi không resolve được giá trị, không phải “ghi default vào systemProperties”.

### 5.2. JVM property per-process, environment inherited

Cùng một OS account có thể khởi chạy nhiều JVM khác nhau với **cùng OS environment** nhưng các `-D` flags khác nhau. Việc Spring đặt `systemProperties` ở mức ưu tiên cao cho phép override theo từng JVM process:

```bash
EDU_DEMO_MODE=from-env java -Dedu.demo.mode=one -jar app.jar
EDU_DEMO_MODE=from-env java -Dedu.demo.mode=two -jar app.jar
```

Hai Java processes riêng sẽ đọc hai JVM overrides khác nhau. Một `System.setProperty` trong process A không tự ảnh hưởng process B.

## 6. Ví dụ chạy được: StandardEnvironment và thứ tự ưu tiên

Ví dụ dưới đây cố ý dùng **`StandardEnvironment` thuần**, không boot web server hay Spring Boot. Nó in:

- Danh sách hai sources mặc định theo thứ tự.
- Giá trị thô đọc từ `System.getProperty` và `System.getenv`.
- Giá trị đã được Spring resolve theo key `edu.demo.mode`.
- Giá trị số được chuyển kiểu từ system property.
- Cách `MapPropertySource` với `addFirst` override hai nguồn mặc định.
- Trạng thái sau khi gỡ override bằng `remove`.

Để bảo đảm `-D` thực sự đến đúng JVM đang chạy, ta đóng gói **fat JAR** bằng Maven Shade và dùng `java -jar`. Không phụ thuộc lifecycle của JVM chứa Maven exec plugin.

### 6.1. Yêu cầu và cấu trúc

JDK 17+, Maven 3.9+.

```text
standard-environment-demo/
├── pom.xml
└── src/main/java/example/StandardEnvironmentDemo.java
```

Tệp `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>standard-environment-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-core</artifactId>
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
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.6.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>example.StandardEnvironmentDemo</mainClass>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

Tệp `src/main/java/example/StandardEnvironmentDemo.java`:

```java
package example;

import java.util.Map;
import java.util.StringJoiner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

public class StandardEnvironmentDemo {

    public static void main(String[] args) {
        var env = new StandardEnvironment();

        StringJoiner names = new StringJoiner(" > ");
        for (PropertySource<?> source : env.getPropertySources()) {
            names.add(source.getName());
        }
        System.out.println("sources: " + names);

        String key = "edu.demo.mode";

        System.out.println("raw JVM: "
                + System.getProperty(key, "absent"));
        System.out.println("raw OS: "
                + System.getenv().getOrDefault("EDU_DEMO_MODE", "absent"));
        System.out.println("resolved: "
                + env.getProperty(key, "fallback"));
        System.out.println("retry as int: "
                + env.getProperty("edu.demo.retries", Integer.class, 3));
        System.out.println("contains mode: " + env.containsProperty(key));

        var temporaryOverride = new MapPropertySource(
                "runtimeOverrides",
                Map.of(key, "from-map"));
        env.getPropertySources().addFirst(temporaryOverride);

        System.out.println("after addFirst: "
                + env.getProperty(key));
        env.getPropertySources().remove("runtimeOverrides");

        System.out.println("after remove: "
                + env.getProperty(key));
        System.out.println("placeholder: "
                + env.resolvePlaceholders("service=${edu.demo.mode}"));
    }
}
```

**Bản demo dùng đúng `spring-core`** vì `StandardEnvironment`, `PropertySource` và `MapPropertySource` nằm ở đó; không cần dependency `spring-context` nếu không tạo `ApplicationContext`.

Build:

```bash
mvn -q clean package
```

### 6.2. Trường hợp A — cả JVM và environment đều có cùng property

Linux/macOS Bash/zsh:

```bash
EDU_DEMO_MODE=from-env java -Dedu.demo.mode=from-jvm -Dedu.demo.retries=7 -jar target/standard-environment-demo-1.0.0.jar
```

PowerShell:

```powershell
$env:EDU_DEMO_MODE = "from-env"
java -Dedu.demo.mode=from-jvm -Dedu.demo.retries=7 -jar target/standard-environment-demo-1.0.0.jar
```

Command Prompt:

```bat
set "EDU_DEMO_MODE=from-env"
java -Dedu.demo.mode=from-jvm -Dedu.demo.retries=7 -jar target/standard-environment-demo-1.0.0.jar
```

**Stdout dự kiến chính xác cho các lệnh này**, không tính các log bổ sung do môi trường hoặc công cụ phát sinh:

```text
sources: systemProperties > systemEnvironment
raw JVM: from-jvm
raw OS: from-env
resolved: from-jvm
retry as int: 7
contains mode: true
after addFirst: from-map
after remove: from-jvm
placeholder: service=from-jvm
```

Giải thích:

1. `StandardEnvironment` có mặc định hai sources; `getPropertySources()` duyệt từ độ ưu tiên cao đến thấp.
2. `System.getProperty(...)` đọc đúng `-Dedu.demo.mode=from-jvm`.
3. `System.getenv(...)` đọc đúng biến được shell đặt là `EDU_DEMO_MODE=from-env`.
4. `env.getProperty("edu.demo.mode")` đọc JVM property trước nên trả `from-jvm`.
5. `getProperty("edu.demo.retries", Integer.class, 3)` chuyển chuỗi `"7"` sang `Integer 7` thông qua conversion service.
6. `containsProperty` trả `true` vì key resolve được.
7. `addFirst` đặt source có key `edu.demo.mode` ở **trước** các sources mặc định nên trả `from-map`.
8. `remove` gỡ đúng source được thêm; lookup lại trả `from-jvm`.
9. `resolvePlaceholders` thay `${edu.demo.mode}` bằng giá trị được resolve hiện thời.

**Không sửa mã để lấy `System.getenv("edu.demo.mode")` rồi mong nhận `EDU_DEMO_MODE`**; việc chuẩn hóa tên là của Spring system-environment source, không phải API `System.getenv`.

### 6.3. Trường hợp B — chỉ OS environment đặt mode

Linux/macOS:

```bash
EDU_DEMO_MODE=from-env java -Dedu.demo.retries=7 -jar target/standard-environment-demo-1.0.0.jar
```

PowerShell:

```powershell
$env:EDU_DEMO_MODE = "from-env"
java -Dedu.demo.retries=7 -jar target/standard-environment-demo-1.0.0.jar
```

Nếu **không có JVM property `edu.demo.mode`** từ launcher/Java tool options, chương trình sẽ in (các dòng khác giống logic trong phần A):

```text
sources: systemProperties > systemEnvironment
raw JVM: absent
raw OS: from-env
resolved: from-env
retry as int: 7
contains mode: true
after addFirst: from-map
after remove: from-env
placeholder: service=from-env
```

Lưu ý quan trọng: trường hợp B yêu cầu không có một `-Dedu.demo.mode` ẩn được đưa qua `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, IDE hoặc launcher wrapper. Nếu có, nguồn JVM vẫn được ưu tiên và output sẽ khác.

### 6.4. Trường hợp C — không cung cấp mode từ bất kỳ nguồn nào

Trên một process sạch, không có `EDU_DEMO_MODE` và không có `edu.demo.mode` JVM property:

```bash
java -jar target/standard-environment-demo-1.0.0.jar
```

Các dòng có ý nghĩa khác biệt:

```text
raw JVM: absent
raw OS: absent
resolved: fallback
retry as int: 3
contains mode: false
after addFirst: from-map
after remove: null
placeholder: service=${edu.demo.mode}
```

`getProperty(key, "fallback")` trả default vì key thiếu. `getProperty("edu.demo.retries", Integer.class, 3)` trả default 3. Sau remove, `getProperty(key)` trả `null` (phép nối chuỗi Java biểu diễn thành chữ `null`). `resolvePlaceholders` **không phải** biến thể bắt buộc: khi placeholder chưa resolve và không có default, nó giữ `${edu.demo.mode}` trong chuỗi kết quả. Nếu cần fail-fast, dùng `resolveRequiredPlaceholders` hoặc `getRequiredProperty`.

Đây là kịch bản **không có biến/property**, không phải lời khẳng định mọi OS/JVM đều mặc định trống những tên đã bị shell/CI cấu hình từ trước.

## 7. getProperty: kiểu dữ liệu, default và fail-fast

Spring `Environment` không chỉ trả `String`. Nó sử dụng conversion service trong đường resolution để chuyển sang type đích thông dụng khi conversion khả thi.

```java
String name = env.getProperty("service.name");
int retries = env.getProperty("edu.demo.retries", Integer.class, 3);
boolean enabled = env.getProperty("service.enabled", Boolean.class, false);
String region = env.getProperty("service.region", "local");
```

Đây là đoạn **tích hợp** với `env` kiểu `Environment`. Trình tự kiểm tra có ý nghĩa:

1. Tìm key trong các `PropertySource` theo priority.
2. Lấy giá trị đầu tiên resolve được theo contract.
3. Chuyển sang type yêu cầu khi cần.
4. Khi key không tồn tại, trả `null` hoặc default theo overload.
5. Khi giá trị tồn tại nhưng **không chuyển đổi được kiểu**, có thể ném exception chuyển đổi; **không** mặc định coi `"not-a-number"` là “missing” rồi dùng default `3`.

Ví dụ:

```bash
java -Dedu.demo.retries=abc -jar target/standard-environment-demo-1.0.0.jar
```

Đây là **lệnh thử lỗi có chủ đích**. Phép đọc `Integer` có thể thất bại vì chuỗi không hợp lệ; không đặt expected stdout thành một kết quả chạy thành công.

### 7.1. containsProperty

```java
boolean present = env.containsProperty("service.endpoint");
```

Nó hỏi Spring có thể tìm được property theo các sources hiện có hay không. Điều đó **không** xác thực rằng endpoint là URL hợp lệ, server online hoặc value không trống theo business semantics.

### 7.2. getRequiredProperty

```java
String endpoint = env.getRequiredProperty("service.endpoint");
```

Nếu không tìm được key, `getRequiredProperty` thường ném `IllegalStateException`. Đây là cơ chế hợp lý khi một thuộc tính thật sự bắt buộc cho startup. Tuy nhiên, để xác thực cấu trúc dữ liệu, giới hạn timeout hoặc cross-field invariants, cần validation ở tầng configuration/business.

### 7.3. Required properties và validateRequiredProperties

Với `ConfigurablePropertyResolver`, có thể cấu hình các key bắt buộc và xác thực:

```java
StandardEnvironment env = new StandardEnvironment();
env.setRequiredProperties("service.endpoint", "service.token");
env.validateRequiredProperties();
```

Đây là **snippet theo ngữ cảnh**; nó sẽ fail khi chưa cung cấp đầy đủ properties và vì vậy không nằm trong demo chạy thành công ở phần 6. `validateRequiredProperties()` thường ném `MissingRequiredPropertiesException` nếu thiếu key. Spring không tự gọi cơ chế này trong mọi ứng dụng chỉ vì bạn định nghĩa một list ở đâu đó; hãy dùng có chủ đích trong luồng bootstrap của mình.

### 7.4. Placeholder resolution

```java
env.resolvePlaceholders("http://${service.host:localhost}:${service.port:8080}");
env.resolveRequiredPlaceholders("token=${service.token}");
```

Đây là snippet tích hợp. `resolvePlaceholders` hỗ trợ default `:` theo cú pháp cấu hình phù hợp và cho phép bỏ qua placeholder không resolve được; `resolveRequiredPlaceholders` yêu cầu resolvable, thường ném `IllegalArgumentException` khi thiếu. Nếu giá trị property là chuỗi expression phức tạp, không mặc định mọi phép toán SpEL đều được `StandardEnvironment` thuần tính toán; Spring bean expression resolution là cơ chế khác.

## 8. Tùy chỉnh thứ tự qua MutablePropertySources

Hai sources mặc định không phải một danh sách cứng không thể thay đổi. `StandardEnvironment.getPropertySources()` trả `MutablePropertySources`.

Ví dụ:

```java
var env = new StandardEnvironment();

var overrides = new MapPropertySource(
        "testOverrides",
        Map.of("edu.demo.mode", "test"));

env.getPropertySources().addFirst(overrides);
String testValue = env.getProperty("edu.demo.mode"); // "test"
```

Đây là đoạn tích hợp, cần import `StandardEnvironment`, `MapPropertySource` và `java.util.Map`.

Các thao tác:

| API | Kết quả |
|---|---|
| `addFirst(source)` | Đặt source có ưu tiên **cao nhất** |
| `addLast(source)` | Đặt source có ưu tiên **thấp nhất** |
| `addBefore(existingName, source)` | Chen source mới ngay trước source được chỉ định |
| `addAfter(existingName, source)` | Chen source mới ngay sau source được chỉ định |
| `replace(name, source)` | Thay source có tên đã có theo contract |
| `remove(name)` | Gỡ source có tên tương ứng |
| `get(name)` | Lấy object PropertySource cụ thể, không phải value của một property key |

**Ví dụ ưu tiên khác nhau:**

```java
// Giả định edu.demo.mode đã có ở JVM -D.
env.getPropertySources().addLast(
        new MapPropertySource(
                "weakDefaults",
                Map.of("edu.demo.mode", "low")));
```

Nếu JVM property đang tồn tại, lookup vẫn trả JVM value vì `weakDefaults` ở cuối. Nếu đặt nguồn `weakDefaults` ở đầu bằng `addFirst`, nó có thể override cả JVM system properties.

### 8.1. Thực hiện trước ApplicationContext.refresh()

Với `ApplicationContext`, hãy tùy chỉnh property sources **trước khi refresh** để các components như `@Value`, `@Profile` và bean factory post-processors có thể nhìn thấy đúng cấu hình lúc bootstrap.

```java
var ctx = new AnnotationConfigApplicationContext();

ctx.getEnvironment().getPropertySources().addFirst(
        new MapPropertySource(
                "integrationOverrides",
                Map.of("service.name", "test-service")));

ctx.register(AppConfig.class);
ctx.refresh();
```

Đây là **snippet tích hợp**, cần imports `AnnotationConfigApplicationContext`, `MapPropertySource`, `Map` và một `AppConfig` thực. Dùng **constructor rỗng** để có khoảng thời gian tùy chỉnh trước `refresh()`. Constructor `new AnnotationConfigApplicationContext(AppConfig.class)` đã refresh ngay, nên không thể giả định bạn còn ở trạng thái “trước bootstrap” khi nó trả về.

Nếu sửa sources **sau refresh**, những lời gọi trực tiếp `env.getProperty` về sau có thể quan sát thứ tự/giá trị mới. Nhưng **các fields đã được `@Value` inject và các bean singleton đã tạo không tự được inject lại**, các điều kiện `@Profile` đã được áp dụng không tự đăng ký lại bean definitions. Muốn runtime refresh thực sự cần cơ chế khác có hợp đồng riêng.

### 8.2. Quy tắc tên PropertySource

Mỗi `PropertySource` có một **name** dùng cho operations trên danh sách sources. Không nên đặt hai sources có tên trùng rồi kỳ vọng danh sách luôn giữ cả hai như hai entry độc lập; Spring quản lý identity/order theo name. Hãy chọn tên mô tả và không trùng, chẳng hạn `testOverrides` hoặc `organizationDefaults`.

Không gọi `getPropertySources().remove("systemProperties")` trong production chỉ vì muốn “bắt buộc dùng env”: bạn có thể làm mất những JVM flags quan trọng của framework. Nếu yêu cầu kiểm soát precedence, hãy thiết kế nguồn override rõ ràng và test đầy đủ.

## 9. @Value và Environment trong ApplicationContext

`StandardEnvironment` thuần có thể đọc properties mà **không cần tạo một Spring bean**. Nhưng khi ứng dụng dùng `@Value` trên field/constructor parameter, cần **Spring-managed bean** và annotation injection infrastructure tương ứng.

Ví dụ:

```java
@Component
public class WorkerSettings {

    private final String mode;
    private final int retries;

    public WorkerSettings(
            @Value("${edu.demo.mode:development}") String mode,
            @Value("${edu.demo.retries:3}") int retries) {
        this.mode = mode;
        this.retries = retries;
    }
}
```

Đây là mã **minh họa tích hợp**, cần import `@Component`, `@Value` và đăng ký class trong `ApplicationContext`. Với `AnnotationConfigApplicationContext`, bean definitions từ component/configuration processing và annotation processors được đăng ký phù hợp.

**Hai tầng xử lý khác nhau:**

- **`Environment`/property sources:** quyết định `${edu.demo.mode}` được resolve ra giá trị nào và theo priority nào.
- **`AutowiredAnnotationBeanPostProcessor` và hạ tầng DI:** phát hiện injection points như `@Value`, resolve dependencies/value, gọi constructor hoặc điền property khi bean được tạo.

Không suy rằng class có `@Value` sẽ được inject khi bạn tự dùng `new WorkerSettings(...)` ngoài Spring. Cũng không suy `@Value` là API để StandardEnvironment tự load Spring Boot `application.properties`.

### 9.1. Inject trực tiếp Environment

Khi component cần đọc properties theo thời điểm sử dụng thay vì cố định ở construction time, có thể inject `Environment`:

```java
@Service
public class RateLimiter {
    private final Environment environment;

    public RateLimiter(Environment environment) {
        this.environment = environment;
    }

    public int maxConcurrent() {
        return environment.getProperty(
                "service.maxConcurrent", Integer.class, 10);
    }
}
```

Đây là snippet tích hợp cần `@Service` và import `org.springframework.core.env.Environment`. Cách này trả cấu hình khi `maxConcurrent()` được gọi, không phải một field giá trị đã resolve đúng một lần. Tuy nhiên **không có cam kết tất cả property sources thay đổi động được quan sát ngay**: tùy nguồn có snapshot/caching, cách framework quản lý sources, và cách ứng dụng cập nhật cấu hình.

Với nhiều settings có quan hệ và validation phức tạp, một typed configuration model thường dễ bảo trì hơn việc gọi `environment.getProperty` rải rác trong business methods.

## 10. Environment và Spring profiles

Ngoài values, `Environment` quản lý **active/default profiles** và điều kiện môi trường. Property names thông dụng:

```text
spring.profiles.active
spring.profiles.default
```

Ví dụ từ JVM:

```bash
java -Dspring.profiles.active=dev -jar app.jar
```

Hoặc từ OS environment theo mapping:

```bash
SPRING_PROFILES_ACTIVE=dev java -jar app.jar
```

Trong `StandardEnvironment`, keys cũng tuân precedence của sources. Ứng dụng có thể đọc:

```java
String[] active = env.getActiveProfiles();
boolean dev = env.acceptsProfiles(
        Profiles.of("dev"));
```

Đây là snippet tích hợp cần import `org.springframework.core.env.Profiles`. `acceptsProfiles(Profiles.of(...))` kiểm tra biểu thức profiles theo API. Profiles thường được `@Profile` sử dụng để quyết định một configuration class/bean definition có được đăng ký khi context bootstrap.

**Lưu ý:** `@Profile` không tự bật/tắt những singleton đã được tạo khi `System.setProperty("spring.profiles.active", "...")` sau refresh. Quyết định bean definition đã áp dụng trong quá trình bootstrap không tự được phát lại.

## 11. StandardEnvironment không tự tải mọi file cấu hình

Một hiểu lầm phổ biến là:

> “Tạo `new StandardEnvironment()` sẽ tự đọc `application.properties` và `application.yml` trong resources.”

**Không đúng với Spring Framework Core thuần.** Theo mặc định, `StandardEnvironment` đăng ký **JVM system properties** và **OS environment variables**. Muốn đọc một file, cần một cơ chế cụ thể đưa file đó thành `PropertySource`, hoặc dùng context/framework có hỗ trợ property loading.

Ví dụ một Java configuration có:

```java
@Configuration(proxyBeanMethods = false)
@PropertySource("classpath:custom.properties")
public class PropertyConfig {
}
```

Đây là **snippet tích hợp**, cần imports `@Configuration`, `@PropertySource`, classpath resource `custom.properties` và container có annotation processing. `@PropertySource` do configuration processing xử lý và thêm property source phù hợp vào `Environment`. Khi thứ tự nhiều file/sources quan trọng, phải kiểm tra thực tế configuration ordering; không mặc định file luôn mạnh hơn `-D`.

### 11.1. Spring Boot thêm nhiều sources và thứ tự riêng

Spring Boot có cơ chế **Config Data** cho `application.properties`, YAML, profile-specific configuration và nhiều nguồn khác. Trong một ứng dụng Boot, `Environment` thực tế có thể có thêm property sources và quy tắc precedence cấp Boot, ví dụ command-line arguments, `SPRING_APPLICATION_JSON`, config data và hệ thống flags tương ứng.

Không lấy **hai-source default order của `new StandardEnvironment()`** rồi tuyên bố đó là **toàn bộ thứ tự property sources trong Spring Boot**. Để chẩn đoán Boot, cần xem môi trường Boot đã được xây dựng và các sources thực tế của nó. Nhưng ý nghĩa nền tảng vẫn đúng: Spring giải quyết key bằng thứ tự `PropertySource` trong `Environment` đang được dùng.

### 11.2. StandardServletEnvironment trong web contexts

`StandardServletEnvironment` mở rộng môi trường tiêu chuẩn cho Servlet applications, có thể thêm servlet config parameters, servlet context parameters và các nguồn web/JNDI thích hợp. Nó không phải thứ mặc định xuất hiện chỉ vì bạn gọi `new StandardEnvironment()`.

Vì phạm vi bài là **JVM system properties và OS environment variables**, nên demo dùng `StandardEnvironment` thuần, không gắn vào HTTP servlet hay Spring Boot auto-configuration.

## 12. JVM system properties và OS environment variables khác nhau ra sao?

| Tiêu chí | JVM system property | OS environment variable |
|---|---|---|
| API Java đọc trực tiếp | `System.getProperty(key)` | `System.getenv(name)` |
| Cách truyền phổ biến | Java launcher `-Dkey=value` trước `-jar` | Shell/CI/Docker đặt biến trước lúc tạo child process |
| Tên thường thấy | Dùng dấu chấm, camel case, ví dụ `edu.demo.mode` | UPPER_SNAKE_CASE, ví dụ `EDU_DEMO_MODE` |
| Sửa từ code Java | `System.setProperty` trong cùng JVM | Không có API Java chuẩn tương đương cho mọi OS |
| Phạm vi thay đổi | Một JVM process | Process environment, thông thường kế thừa từ parent khi spawn |
| Ưu tiên mặc định trong StandardEnvironment | **Cao hơn** | **Thấp hơn** |
| Hỗ trợ key adaptation | Lookup JVM key gần trực tiếp | `SystemEnvironmentPropertySource` hỗ trợ các dạng tên |
| Chứa bí mật được không? | Có thể, nhưng dễ lộ qua process/diagnostics | Có thể, nhưng cũng có rủi ro lộ qua process dumps/config/log |
| Có tự lưu vĩnh viễn không? | Không, trừ cách cấu hình launcher riêng | Không tự vĩnh viễn; phụ thuộc OS/service config |
| Có mặc định thành Spring bean không? | Không; là source cho property resolution | Không; là source cho property resolution |

Không chọn `-D` hay environment variable dựa trên giả định cái nào **tuyệt đối an toàn**: cả hai có thể bị lộ theo môi trường deploy. Secrets cần được kiểm soát theo cơ chế của hạ tầng và không được log/commit vào repository.

## 13. Các tình huống triển khai thực tế

### 13.1. CLI chạy khác chế độ theo môi trường

Một batch tool có thể được chạy:

```bash
EDU_DEMO_MODE=staging java -jar batch.jar
```

hoặc override JVM cho một lần test:

```bash
EDU_DEMO_MODE=staging java -Dedu.demo.mode=debug -jar batch.jar
```

Nếu chỉ có hai nguồn mặc định, mode thứ nhất là `staging`; ở lệnh thứ hai, mode là `debug`. Không cần sửa code và không cần tạo Spring profile mới chỉ để thay một property.

### 13.2. CI cung cấp retry count

Ví dụ runner đưa biến:

```bash
EDU_DEMO_RETRIES=9 java -jar batch.jar
```

`getProperty("edu.demo.retries", Integer.class, 3)` có thể resolve `EDU_DEMO_RETRIES` và chuyển chuỗi `"9"` thành `9`. Nếu CI đặt `EDU_DEMO_RETRIES=abc`, code sẽ không có `Integer` hợp lệ; nên fail-fast với thông báo cấu hình rõ thay vì im lặng bỏ qua giá trị sai.

### 13.3. Test muốn cô lập môi trường máy chạy

Test tích hợp không nên phụ thuộc các biến `PATH`, `HOME` hoặc thông tin ngẫu nhiên trên CI. Thay vì sửa môi trường toàn process, tạo `StandardEnvironment` riêng và thêm `MapPropertySource` ở đầu:

```java
var env = new StandardEnvironment();
env.getPropertySources().addFirst(
        new MapPropertySource(
                "testSettings",
                Map.of("edu.demo.mode", "test")));
```

Đây là snippet tích hợp. Nếu muốn kiểm thử chính semantics của `systemEnvironment`, có thể thay property source tương ứng bằng `SystemEnvironmentPropertySource` giả lập hoặc khởi chạy một process con với environment cụ thể. **Không giả định `MapPropertySource` tự biến `edu.demo.mode` thành `EDU_DEMO_MODE`**.

### 13.4. Môi trường Kubernetes/Docker

Container runtime có thể cấu hình environment variables cho JVM, và startup script có thể truyền JVM flags như `JAVA_TOOL_OPTIONS`. Nếu cả hai đặt cùng logical property, **trong StandardEnvironment mặc định** JVM flag sẽ thắng. Trong ứng dụng Boot đầy đủ, phải kiểm tra toàn bộ order của sources do Boot thêm.

**Operational checklist:** xác nhận môi trường được truyền đến đúng Java PID; xác nhận VM options nằm trước `-jar`; kiểm tra cấu hình có nguồn cao hơn override; không log secrets; và nêu rõ kỳ vọng default/required cho mỗi property.

## 14. Các cạm bẫy thường gặp

| Hiểu nhầm / lỗi | Điều thực tế | Cách xử lý |
|---|---|---|
| “`StandardEnvironment` chỉ đọc biến môi trường” | Nó có cả `systemProperties` và `systemEnvironment` | Kiểm tra hai sources mặc định |
| “Environment variable luôn thắng `-D`” | JVM property thắng theo order mặc định | Đọc `getPropertySources()` và kiểm tra override |
| “`-D` sau `-jar` vẫn là VM option” | Nó trở thành program argument | Đưa `-D` trước `-jar` |
| “`System.setProperty` sửa OS variable” | Chỉ sửa JVM properties | Dùng shell/container config cho OS env |
| “`System.getenv("edu.demo.mode")` tương đương Spring lookup `EDU_DEMO_MODE`” | Key adaptation là của `SystemEnvironmentPropertySource` | Dùng `env.getProperty("edu.demo.mode")` |
| “Mọi PropertySource hỗ trợ dấu chấm sang gạch dưới” | Không; behavior khác theo loại source | Dùng đúng keys của custom source |
| “Lấy `Integer.class` mà value là `abc` sẽ dùng default” | Giá trị sai type có thể gây conversion exception | Validate giá trị tại startup |
| “Sau `addLast`, custom value luôn override JVM flags” | `addLast` là ưu tiên thấp nhất | Dùng `addFirst` nếu có lý do thật sự |
| “`new StandardEnvironment()` tự tải `application.yml`” | Không có Boot Config Data tự động | Khai báo file source/context hoặc dùng Boot |
| “Sửa environment sau refresh làm `@Value` inject lại” | Injection của bean đã tạo không tự chạy lại | Thiết kế runtime refresh/rebinding riêng |
| “`@Profile` tự đăng ký lại bean khi property đổi runtime” | Definition selection chủ yếu ở bootstrap | Đặt profiles trước refresh |
| “Một key luôn chỉ có đúng một value source” | Có thể cùng key trong nhiều sources, nhưng source có ưu tiên cao thắng | In tên sources, test precedence |
| “Cứ đọc tất cả environment variables để debug” | Có thể lộ credentials | Chỉ log whitelist keys an toàn |
| “`containsProperty` bảo đảm cấu hình hợp lệ” | Chỉ có thể tìm thấy key, không validate nội dung | Kiểm tra type và business constraints |
| “`StandardEnvironment` có cùng precedence với mọi Spring Boot app” | Boot thường có nhiều sources và order riêng | Kiểm tra Environment thực tế của Boot |
| “Truyền `-Dmode` từ Maven luôn vào một JVM riêng” | Maven launcher/plugin có thể dùng cùng JVM hoặc cơ chế args khác | Chạy JAR qua `java -jar` để xác nhận |
| “OS environment luôn thay đổi tức thời trong JVM đang chạy” | JVM nhận environment khi process được tạo; không có live reload chuẩn | Restart hoặc dùng cơ chế config động riêng |

### 14.1. Truy nguồn giá trị sai

Khi `env.getProperty("service.region")` cho kết quả không như kỳ vọng:

1. Kiểm tra **key đúng**, kể cả dấu chấm, chữ hoa, underscore.
2. Kiểm tra có `-Dservice.region=...` trong Java VM options không.
3. Kiểm tra OS variable `SERVICE_REGION` **được truyền đến Java process** chưa.
4. In **chỉ tên các PropertySource**, không in toàn bộ values nhạy cảm.
5. Nếu là `StandardEnvironment` thuần, nhớ order mặc định `systemProperties` trước `systemEnvironment`.
6. Nếu source tùy chỉnh được thêm, kiểm tra `addFirst/addLast/addBefore/addAfter` và vị trí thực tế của nó.
7. Nếu là Spring Boot, kiểm tra thêm command-line args, config data, profile files và những external sources thích hợp.
8. Kiểm tra việc thay sources có xảy ra **trước `refresh()`** không nếu giá trị đã được inject vào singleton.
9. Kiểm tra type conversion: value tìm thấy có thể không parse được thành số/boolean.
10. Kiểm tra có process khác, shell khác, container khác hoặc IDE launcher đang chạy bản app cũ không.

### 14.2. Trường hợp secret/config validation

Đối với một token bắt buộc, sử dụng `getRequiredProperty("service.token")` và validation/secret manager thích hợp sẽ minh bạch hơn việc `getProperty("service.token", "changeme")` với một giá trị fallback không an toàn.

Không hiển thị `System.getenv()` hoặc `System.getProperties()` nguyên map trong log. Khi cần hỗ trợ, in tên key và nguồn được chọn; chỉ in value nếu đã được phân loại an toàn. Spring property sources là cơ chế **resolution**, không phải hệ thống mã hóa hay lưu giữ secrets.

## 15. Quy tắc chọn API

- Cần **đọc** một key theo thứ tự sources hiện tại: dùng `Environment.getProperty`.
- Cần **kiểm tra bắt buộc**: dùng `getRequiredProperty` hoặc `setRequiredProperties` + `validateRequiredProperties` ở bootstrap.
- Cần **chuyển sang kiểu**: dùng `getProperty(key, Integer.class, defaultValue)` hoặc type phù hợp và xử lý conversion errors.
- Cần **tìm mặc định**: dùng `getProperty(key, defaultValue)` hoặc `${key:default}` ở placeholder.
- Cần **can thiệp thứ tự sources**: dùng `ConfigurableEnvironment.getPropertySources()` trước `refresh()`.
- Cần **inject giá trị cố định lúc tạo bean**: dùng `@Value`/configuration properties theo context hỗ trợ.
- Cần **đổi cấu hình runtime**: dùng một abstraction/config refresh riêng, không nghĩ việc sửa sources sẽ tự tái tạo bean.
- Cần **đọc OS variable đúng tên** không qua Spring: dùng `System.getenv("NAME")`.
- Cần **đọc JVM property đúng tên** không qua Spring: dùng `System.getProperty("key")`.
- Cần **đọc application.properties/YAML** với precedence mở rộng: cần cơ chế file property loading/Boot Config Data thích hợp, không chỉ `new StandardEnvironment()`.

## 16. Câu hỏi tự kiểm tra

**1. `StandardEnvironment` có hai nguồn cấu hình mặc định nào?**  
`systemProperties` (JVM system properties) và `systemEnvironment` (OS environment variables).

**2. Nếu cùng một key logic xuất hiện cả hai nơi, giá trị nào thắng mặc định?**  
Giá trị ở `systemProperties` thắng vì property source này có độ ưu tiên cao hơn.

**3. Vì sao `EDU_DEMO_MODE` có thể được Spring tìm bằng key `edu.demo.mode`?**  
Vì `SystemEnvironmentPropertySource` hỗ trợ biến thể tên môi trường để phù hợp convention của shell/OS.

**4. Câu lệnh nào đưa system property đúng vào JVM?**  
`java -Dedu.demo.mode=cli -jar app.jar`, với `-D` nằm trước `-jar`.

**5. `System.setProperty("edu.demo.mode","test")` có thay đổi biến `EDU_DEMO_MODE` không?**  
Không; nó chỉ thay JVM system property trong cùng process.

**6. `addFirst` và `addLast` khác nhau thế nào?**  
`addFirst` đặt source ưu tiên cao nhất; `addLast` đặt source ưu tiên thấp nhất.

**7. Nếu `getProperty("retry", Integer.class, 3)` tìm thấy value `abc`, nó có trả 3 không?**  
Không nên kỳ vọng. Type conversion có thể ném exception vì value tồn tại nhưng không hợp lệ.

**8. `new StandardEnvironment()` có tự load `application.properties` không?**  
Không. Cần cơ chế khai báo property source hoặc một framework như Spring Boot thêm config data.

**9. Nếu sửa property source sau khi singleton được tạo, field `@Value` có tự đổi không?**  
Không. Injection của instance đã tạo không tự được chạy lại.

**10. `Environment.containsProperty` có xác nhận URL/credential hợp lệ không?**  
Không. Nó chỉ kiểm tra key có thể resolve; cần validation riêng.

**11. Làm thế nào fail-fast khi thiếu một property bắt buộc?**  
Dùng `getRequiredProperty` hoặc `setRequiredProperties(...)` và `validateRequiredProperties()`.

**12. Vì sao bài thực hành chạy bằng `java -jar` thay vì truyền flags một cách mơ hồ cho Maven?**  
Để kiểm soát trực tiếp JVM process, đảm bảo `-D` là option của Java launcher đang chạy code demo.

## 17. Tài liệu tham khảo chính thức

- [Spring Framework — Environment Abstraction](https://docs.spring.io/spring-framework/reference/core/beans/environment.html): cách `Environment` và `PropertySource` hoạt động, precedence mặc định system properties > environment variables.
- [Spring Framework 6.2 — StandardEnvironment API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/StandardEnvironment.html): tên hai nguồn mặc định và thứ tự lookup.
- [Spring Framework 6.2 — SystemEnvironmentPropertySource API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/SystemEnvironmentPropertySource.html): xử lý tên biến môi trường tương đương dạng dấu chấm/underscore/chữ hoa.
- [Spring Framework 6.2 — ConfigurableEnvironment API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/ConfigurableEnvironment.html): cấu hình `PropertySources` và chỉnh trước `refresh()`.
- [Spring Framework 6.2 — MutablePropertySources API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/MutablePropertySources.html): `addFirst`, `addLast`, `addBefore`, `addAfter`, `replace`, `remove`.
- [Spring Framework 6.2 — PropertyResolver API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/PropertyResolver.html): typed property lookup, default, placeholder resolution.
- [Java SE 17 — System API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html): `getProperty`, `setProperty`, `getenv` và phạm vi JVM/OS.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-environment-003">PropertySource và thứ tự ưu tiên khi trùng khóa</a>

<details>
<summary>Xem chi tiết</summary>

# PropertySource và thứ tự ưu tiên khi trùng khóa trong Spring Framework

## 1. Vấn đề: cùng một khóa cấu hình có thể xuất hiện ở nhiều nơi

Một ứng dụng Spring có thể nhận cấu hình từ JVM system properties, biến môi trường hệ điều hành, file `.properties`, Servlet context, thư viện cấu hình ngoài hoặc một nguồn do ứng dụng tự bổ sung. Không hiếm tình huống cùng khóa có nhiều giá trị:

~~~text
application-defaults:  shop.currency = VND
file-config:           shop.currency = USD
runtime-overrides:     shop.currency = EUR
~~~

Khi gọi:

~~~java
String currency = environment.getProperty("shop.currency");
~~~

Spring phải quyết định lấy giá trị nào. Trong Spring Framework Core Container, câu trả lời đến từ **thứ tự các `PropertySource` trong `Environment`**, không phải thứ tự chữ cái của khóa, thứ tự bean được inject hay một quy tắc “file mới nhất thắng”.

> **Quy tắc trọng tâm:** Spring duyệt `PropertySource` từ nguồn có **precedence cao nhất** xuống thấp nhất. Nguồn đầu tiên trả về giá trị **không phải `null`** cho khóa được hỏi thường quyết định kết quả. Nguồn ưu tiên thấp hơn không được trộn giá trị để ghi đè khóa đó.

Để nắm chắc chủ đề, cần hiểu ba tầng:

1. **`PropertySource`**: một nguồn cấu hình được đặt tên, có phương thức tra cứu giá trị theo key.
2. **`PropertySources` / `MutablePropertySources`**: tập hợp **có thứ tự** các nguồn cấu hình.
3. **`Environment` / `PropertyResolver`**: API ứng dụng dùng để tra cứu thuộc tính và profile, thường dựa trên danh sách nguồn có thứ tự nói trên.

Mục tiêu sau bài: giải thích cơ chế override khi trùng khóa, thao tác `addFirst`/`addLast`/`addBefore`/`addAfter`, hiểu các nguồn mặc định của Spring và sự khác biệt với Spring Boot, viết chương trình Java 17 chạy được để chứng minh kết quả, phân tích lỗi giá trị không như mong đợi mà không làm lộ dữ liệu nhạy cảm.

## 2. `PropertySource` là gì?

Spring định nghĩa abstraction `org.springframework.core.env.PropertySource<T>`. Mỗi nguồn có:

- **`name`**: tên định danh của source trong `PropertySources`, chẳng hạn `systemProperties`, `systemEnvironment`, `config-file`.
- **`source`**: đối tượng lưu trữ dữ liệu thực tế, tùy implementation.
- **`getProperty(String name)`**: trả giá trị ứng với key, hoặc `null` khi không tìm thấy.
- **`containsProperty(String name)`**: kiểm tra key theo hợp đồng của implementation.

Một interface/class minh họa:

~~~java
// Chữ ký được giản lược để diễn giải API.
// Không tự khai báo lại PropertySource trong project.
abstract class PropertySource<T> {
    private final String name;
    private final T source;

    public abstract Object getProperty(String key);
    public boolean containsProperty(String key) { /* ... */ return false; }
}
~~~

Đoạn trên **không phải định nghĩa API đầy đủ và không dùng để chạy**, chỉ giải thích vai trò.

Spring có nhiều implementation:

| Implementation | Dữ liệu phía sau | Khi hữu ích |
| --- | --- | --- |
| `MapPropertySource` | `Map<String, Object>` | Demo, cấu hình programmatic, values do ứng dụng cung cấp |
| `PropertiesPropertySource` | `java.util.Properties` | Nguồn properties kiểu Java |
| `SystemEnvironmentPropertySource` | Biến môi trường hệ điều hành | Phân giải tên biến môi trường phù hợp |
| `CompositePropertySource` | Nhiều sources con | Gom một nhóm nguồn theo một thứ tự nội bộ |
| `ResourcePropertySource` | Resource có định dạng properties | Đọc từ tài nguyên `classpath:` hoặc `file:` |
| `StubPropertySource` | Placeholder source chưa có dữ liệu thực | Hạ tầng thay thế vào source thực theo thời điểm thích hợp |

**Chú ý:** Spring Core `MapPropertySource` không tự hiểu mọi quy tắc YAML, profile activation hay cấu trúc `application.yml` của Spring Boot. Đó là những tầng cấu hình cao hơn.

### Ví dụ tạo một MapPropertySource

~~~java
import java.util.Map;
import org.springframework.core.env.MapPropertySource;

MapPropertySource defaults = new MapPropertySource(
    "defaults",
    Map.of(
        "shop.currency", "VND",
        "shop.retry", "3"
    )
);
~~~

Đoạn này được đặt bên trong method Java có Spring Core trên classpath. Tên `"defaults"` mô tả **nguồn**, không phải tên key; một source có thể chứa nhiều key.

## 3. `Environment` và `PropertyResolver`

`org.springframework.core.env.Environment` kế thừa `PropertyResolver`, cho phép:

~~~java
String region = environment.getProperty("app.region");
String portString = environment.getProperty("app.port", "8080");
Integer port = environment.getProperty("app.port", Integer.class);
boolean exists = environment.containsProperty("app.region");
String required = environment.getRequiredProperty("app.region");
~~~

Đây là đoạn minh họa với biến `environment` kiểu `Environment` được inject hoặc lấy từ context.

| API | Hành vi |
| --- | --- |
| `getProperty(key)` | Tìm theo precedence; thường trả `null` khi không có |
| `getProperty(key, defaultValue)` | Trả default khi không tìm được giá trị |
| `getProperty(key, targetType)` | Yêu cầu conversion sang kiểu cần dùng |
| `containsProperty(key)` | Xác định có giá trị tương ứng không |
| `getRequiredProperty(key)` | Ném lỗi nếu thuộc tính bắt buộc không tồn tại |
| `resolvePlaceholders(text)` | Giải quyết `\${...}` nơi có thể; placeholder không resolve được có thể để nguyên |
| `resolveRequiredPlaceholders(text)` | Yêu cầu placeholder được resolve, nếu thiếu sẽ báo lỗi |

**Giá trị tìm thấy** chịu tác động bởi precedence. **Giá trị đã inject vào object** lại chịu thêm quy tắc vòng đời của bean: nếu một singleton nhận `String` trong constructor, thay đổi PropertySources về sau không tự cập nhật field ấy.

### Phân biệt Environment với nguồn lưu

`Environment` không nhất thiết chứa một `Map<String,String>` đã gộp tất cả sources. Nó giải quyết key dựa trên nhiều nguồn có thứ tự. Vì thế hai nguồn cùng có `app.region` **cùng tồn tại**, nhưng lúc tra cứu chỉ có giá trị ưu tiên cao được trả về.

## 4. Thuật toán tra cứu khi trùng khóa

Sơ đồ đơn giản hóa:

~~~text
Environment.getProperty("shop.currency")
                 |
                 v
Source 1: runtime-overrides
  Có shop.currency không?
      | Có -> EUR -> TRẢ KẾT QUẢ
      |
      Không
      v
Source 2: file-config
  Có shop.currency không?
      | Có -> USD -> TRẢ KẾT QUẢ
      |
      Không
      v
Source 3: application-defaults
  Có shop.currency không?
      | Có -> VND -> TRẢ KẾT QUẢ
      |
      Không
      v
Không tìm thấy -> null hoặc giá trị mặc định đã chỉ định
~~~

**Nguyên tắc “first non-null wins”** áp dụng ở tầng resolver tìm một property: nếu source đầu trả giá trị không `null`, các source sau không được hỏi thêm để “so sánh”. Đối với `MapPropertySource`, tránh chứa entry có value `null` vì điều đó có thể gây ngữ nghĩa `containsProperty` và `getProperty` không đồng nhất; Javadoc khuyến nghị map không chứa null.

### Không phải merge theo key

Giả sử:

~~~properties
# config-file
server.host=localhost
server.port=8080
~~~

Và:

~~~properties
# runtime-overrides
server.port=9090
~~~

Nếu overrides ưu tiên cao, kết quả tra cứu:

~~~text
server.host = localhost
server.port = 9090
~~~

Đây là **kết quả tra cứu từng key**. Nó không có nghĩa Spring thực hiện thao tác ghi đè vật lý hoặc sửa file `config-file`. Source thấp vẫn lưu `server.port=8080`; chỉ có giá trị được resolve là 9090.

Đối với list/map được bind bởi tầng cấu hình cao hơn, quy tắc merge/replace có thể khác. Đừng suy một quy tắc “first key wins” đơn giản thành khẳng định rằng mọi collection trong `@ConfigurationProperties` đều merge từng phần giống nhau.

## 5. Thứ tự có quan trọng hơn tên nguồn

Giả sử source A được đặt trước B:

~~~text
Source A: feature.enabled = true
Source B: feature.enabled = false
~~~

Khi `Environment` tìm khóa, kết quả là `true`, không quan trọng source A mang tên `default` hay `production`.

Nếu đảo thứ tự:

~~~text
Source B: feature.enabled = false
Source A: feature.enabled = true
~~~

thì kết quả là `false`. **Tên nguồn** phục vụ nhận diện và thao tác trong danh sách; **vị trí** quyết định precedence.

Hai nguồn có tên khác nhau có thể chứa key trùng. Đừng lẫn việc “property source name trùng nhau” với “property key trùng nhau”:

- Trùng **key** ở các nguồn khác nhau là tình huống override thông thường.
- Trùng **source name** là vấn đề danh sách nguồn: `MutablePropertySources` dùng name để nhận diện, thay thế hoặc di chuyển nguồn theo API.

## 6. `MutablePropertySources` cho phép điều khiển thứ tự

`ConfigurableEnvironment.getPropertySources()` trả danh sách có thể thay đổi. Vì resolver duyệt từ đầu đến cuối, `addFirst(source)` đặt nguồn ưu tiên cao nhất, `addLast(source)` tạo fallback, `addBefore(name, source)`/`addAfter(name, source)` chèn quanh mốc có tên, `replace(name, source)` đổi source tại vị trí tương ứng và `remove(name)` bỏ nguồn khỏi danh sách.

```text
Ban đầu:                [A, B, C]   (A cao nhất)
addFirst(D):            [D, A, B, C]
addLast(E):             [D, A, B, C, E]
addBefore("B", F):      [D, A, F, B, C, E]
addAfter("B", G):       [D, A, F, B, G, C, E]
```

Đây là mô hình để đọc precedence, chưa phải code chạy. Các tham số `name` dùng **tên của PropertySource**, không dùng property key. Thứ tự cũng không đồng nghĩa thứ tự khởi tạo bean. Bài tiếp theo về `MutablePropertySources` cung cấp API chi tiết và chương trình thay, di chuyển, xóa source; ở đây chỉ cần biết vì sao đổi vị trí sẽ đổi **giá trị thắng khi trùng khóa**.

## 7. Các PropertySource mặc định của Spring Framework

Với ứng dụng Java độc lập dùng `StandardEnvironment`, Spring thường tạo hai nguồn mặc định:

1. `systemProperties` — JVM system properties, chẳng hạn `-Dapp.region=VN`.
2. `systemEnvironment` — environment variables của OS, chẳng hạn `APP_REGION=VN`.

**Mặc định, JVM system properties có precedence cao hơn OS environment variables** trong `StandardEnvironment`. *Đây là thứ tự của Spring Framework `StandardEnvironment`, không phải bảng ưu tiên đầy đủ cho mọi ứng dụng Spring Boot.*

Ví dụ nếu cùng một giá trị logic có ở cả hai nguồn và tên được resolver nhận diện:

~~~text
JVM: -Dapp.region=SG
OS:  APP_REGION=VN
~~~

Trong cấu hình mặc định phù hợp, `environment.getProperty("app.region")` có thể nhận `SG` từ JVM system property trước. Việc biến môi trường viết hoa/underscore được `SystemEnvironmentPropertySource` hỗ trợ theo các quy tắc tìm tên tương ứng; không nên giả định mọi custom source hoặc mọi API đều chuẩn hóa key y hệt Spring Boot Binder.

### Với Servlet web context

`StandardServletEnvironment` có thể thêm những sources như servlet config parameters, servlet context parameters và JNDI (nếu áp dụng), ngoài JVM system properties và OS environment. Danh sách cụ thể phụ thuộc loại context và môi trường triển khai.

**Không đem bảng ưu tiên web context áp dụng nguyên xi cho Spring Framework Java thuần** hoặc ứng dụng Spring Boot không dùng Servlet. Hãy kiểm tra `Environment.getPropertySources()` tại runtime để xác nhận những nguồn thực sự có mặt.

## 8. Ví dụ hoàn chỉnh Java 17 + Spring Context 6.1: trùng khóa và đổi thứ tự

Chương trình dưới đây có ba custom sources được đặt tên:

- `defaults`: nguồn nền, chứa region và retry mặc định.
- `configuration`: nguồn giống file config, override mode và cung cấp timeout.
- `runtime`: nguồn ưu tiên cao nhất lúc đầu, override mode và retry.

Sau khi khởi tạo context, chúng ta **di chuyển runtime source xuống cuối**. `Environment.getProperty(...)` thấy precedence mới, nhưng bean `ModeSnapshot` đã đọc config lúc tạo sẽ giữ giá trị đã được inject vào constructor.

### 8.1. Cấu trúc dự án

~~~text
property-source-order-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── com/
                └── example/
                    └── property/
                        └── PropertySourceOrderDemo.java
~~~

### 8.2. File `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>property-source-order-demo</artifactId>
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
                    <mainClass>com.example.property.PropertySourceOrderDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 8.3. File `PropertySourceOrderDemo.java`

~~~java
package com.example.property;

import java.util.Map;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

public class PropertySourceOrderDemo {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            MutablePropertySources sources =
                context.getEnvironment().getPropertySources();

            MapPropertySource defaults = new MapPropertySource(
                "defaults",
                Map.of(
                    "demo219.mode", "base",
                    "demo219.retry", "3",
                    "demo219.region", "VN"
                )
            );

            MapPropertySource config = new MapPropertySource(
                "configuration",
                Map.of(
                    "demo219.mode", "file",
                    "demo219.timeout", "15"
                )
            );

            MapPropertySource runtime = new MapPropertySource(
                "runtime",
                Map.of(
                    "demo219.mode", "override",
                    "demo219.retry", "9"
                )
            );

            sources.addFirst(defaults);
            sources.addFirst(config);
            sources.addFirst(runtime);

            context.register(AppConfig.class);
            context.refresh();

            Environment env = context.getEnvironment();
            ModeSnapshot bean = context.getBean(ModeSnapshot.class);

            System.out.println(
                "highest source=" + sources.iterator().next().getName());
            System.out.println("mode before=" +
                env.getProperty("demo219.mode"));
            System.out.println("retry before=" +
                env.getProperty("demo219.retry"));
            System.out.println("timeout=" +
                env.getProperty("demo219.timeout"));
            System.out.println("region=" +
                env.getProperty("demo219.region"));
            System.out.println("snapshot before=" + bean.mode());

            sources.remove("runtime");
            sources.addLast(runtime);

            System.out.println("mode after=" +
                env.getProperty("demo219.mode"));
            System.out.println("retry after=" +
                env.getProperty("demo219.retry"));
            System.out.println("snapshot after=" + bean.mode());

            sources.addFirst(runtime);
            System.out.println("mode restored=" +
                env.getProperty("demo219.mode"));

            System.out.println("missing fallback=" +
                env.getProperty("demo219.unknown", "fallback"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {

        @Bean
        public ModeSnapshot modeSnapshot(Environment environment) {
            return new ModeSnapshot(
                environment.getRequiredProperty("demo219.mode"));
        }
    }

    public record ModeSnapshot(String mode) {
    }
}
~~~

**Vì sao chương trình hợp lệ:**

- `AnnotationConfigApplicationContext()` tạo context chưa refresh, cho phép bổ sung PropertySources **trước** khi nạp và tạo beans.
- `getEnvironment().getPropertySources()` trả `MutablePropertySources`.
- Ba `MapPropertySource` dùng keys có prefix `demo219` để tránh xung đột thực tế với cấu hình ngoài.
- `addFirst` ba lần tạo thứ tự cao → thấp: `runtime` → `configuration` → `defaults` → những system sources vốn có.
- `AppConfig.modeSnapshot(Environment)` đọc một `String` vào record khi Spring tạo bean. Record không có cơ chế auto-refresh giá trị khi PropertySources bị reorder.
- Khi source `runtime` được remove rồi addLast, `configuration` đứng trước `defaults`, nên mode đến từ configuration, retry đến từ defaults.
- Khi `runtime` quay lại addFirst, mode trở về giá trị runtime.
- `Map.of(...)` không chứa null values; ví dụ không cần đọc file, network hoặc system env để có các keys trên.

### 8.4. Lệnh chạy

Yêu cầu JDK 17+ và Maven 3.9.x. Trong thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Những dòng `System.out.println` từ chương trình:

~~~text
highest source=runtime
mode before=override
retry before=9
timeout=15
region=VN
snapshot before=override
mode after=file
retry after=3
snapshot after=override
mode restored=override
missing fallback=fallback
~~~

Maven hoặc logging framework có thể in thêm log tùy runtime; các dòng trên là output do chương trình chủ động tạo ra.

### 8.5. Giải thích mấu chốt

**Trước khi di chuyển nguồn:**

~~~text
runtime        demo219.mode=override, demo219.retry=9
configuration  demo219.mode=file,     demo219.timeout=15
defaults       demo219.mode=base,     demo219.retry=3, region=VN
systemProperties
systemEnvironment
~~~

Kết quả mode là `override` vì runtime đứng đầu, retry là 9 cũng từ runtime. Timeout chỉ được configuration định nghĩa nên là 15; region chỉ từ defaults nên là VN.

**Sau khi chuyển runtime xuống cuối:**

~~~text
configuration
defaults
systemProperties
systemEnvironment
runtime
~~~

Kết quả mode là `file`, retry là 3: cả hai được tìm thấy **trước khi tới runtime**. Nhưng `ModeSnapshot.mode()` vẫn là `override`, bởi bean được tạo trước khi thay đổi precedence.

Với một ứng dụng thật, **thay đổi PropertySources sau refresh phải được xem rất cẩn thận**: một số callers đọc Environment trực tiếp có thể thấy giá trị mới, còn singleton fields và nhiều configuration-bound beans vẫn giữ snapshot cũ, khiến hệ thống bất nhất. Demo thay đổi sau refresh chỉ nhằm chứng minh cơ chế; cấu hình production thường nên được chốt **trước refresh**.

## 9. Chọn `addFirst` hay `addLast` trong dự án?

Dùng `addFirst` khi source mới **phải override** những sources trước đó, ví dụ một tập property values đã được operator ủy quyền có precedence cao nhất.

~~~java
sources.addFirst(
    new MapPropertySource(
        "applicationOverrides",
        Map.of("app.threshold", "80")
    )
);
~~~

Đây là snippet bên trong một method đã có biến `sources` kiểu `MutablePropertySources`.

Dùng `addLast` khi source là **giá trị fallback**:

~~~java
sources.addLast(
    new MapPropertySource(
        "libraryDefaults",
        Map.of("app.threshold", "50")
    )
);
~~~

Khi một nguồn phía trên đã định nghĩa `app.threshold`, fallback ở cuối không ghi đè. Nếu không nguồn nào khác có key, giá trị 50 được dùng.

Dùng `addBefore` và `addAfter` khi cần điều khiển mối quan hệ cụ thể:

~~~java
sources.addBefore("systemEnvironment", customSource);
sources.addAfter("systemProperties", anotherSource);
~~~

Đây là snippet minh họa cần biến `customSource` và `anotherSource` phù hợp; các tên tham chiếu phải **tồn tại** trong danh sách khi thực hiện thao tác. Nếu chạy trong context không có `systemEnvironment` hoặc `systemProperties` với tên ấy, API có thể báo lỗi. Kiểm tra `contains(name)` trước khi định vị tương đối.

### Một tiêu chí quan trọng: ai được quyền override ai?

Đừng chọn addFirst chỉ vì “muốn chắc chắn ứng dụng thấy config của mình”. Đặt source ở đầu có nghĩa operator `-D...` hoặc `ENV_VAR` có thể **không còn override được khóa ấy**, trái với kỳ vọng vận hành. Tài liệu hóa rõ chính sách precedence của thư viện và ứng dụng.

## 10. `replace` và trùng tên PropertySource

`MutablePropertySources` quản lý sources theo tên. Nếu muốn thay giá trị của nguồn `libraryDefaults` mà **giữ vị trí hiện tại**, sử dụng:

~~~java
sources.replace(
    "libraryDefaults",
    new MapPropertySource(
        "libraryDefaults",
        Map.of("app.threshold", "60")
    )
);
~~~

Đoạn này giả định source `libraryDefaults` đã tồn tại trong danh sách. `replace` báo lỗi khi tên nguồn cần thay không có.

Điểm cần phân biệt:

- `replace` thay một **source object trong danh sách**.
- `MapPropertySource` chứa nhiều property keys; việc replace không chỉ đổi một key đơn lẻ.
- Vị trí source được thay vẫn giữ nguyên, nên chính sách precedence của các sources khác không bị đảo.
- Nếu muốn thêm cùng source name bằng các API add, Spring có cơ chế nhận diện/move entry theo name; không nên dùng cách này để tạo hai sources cùng tên rồi hy vọng cả hai độc lập.

**Tên PropertySource nên đủ phân biệt**, ví dụ `"companyDefaults"`, `"tenantRuntimeOverrides"` thay vì tạo nhiều sources trùng tên `"settings"`.

## 11. Điều gì xảy ra khi source ưu tiên cao không có khóa?

Giả sử:

~~~text
high-priority:  app.timeout (không tồn tại)
file-source:    app.timeout = 20
defaults:       app.timeout = 10
~~~

Spring bỏ qua source đầu vì không trả giá trị, tiếp tục đến source file, kết quả là 20.

Tức là:

- **Source có thứ tự cao** không thể override một key mà nó hoàn toàn không cung cấp.
- **Source thấp** vẫn có ích cho những keys chưa được định nghĩa phía trên.
- Trùng một key không có nghĩa tất cả keys từ một source đều biến mất; Spring tra cứu **từng key**.

### `null` không phải một cách override ổn định

Với `MapPropertySource`, không nên dùng map có `"app.timeout" -> null` để diễn đạt “xóa giá trị từ source thấp”. Javadoc `MapPropertySource` nhắc tránh null values để giữ ngữ nghĩa `getProperty` và `containsProperty` nhất quán. Resolver thường coi `null` là “chưa có giá trị” và tiếp tục tìm source tiếp theo.

Nếu nghiệp vụ muốn một cấu hình được vô hiệu hóa, dùng **giá trị tường minh** như `"disabled"`, `false` hoặc một policy riêng, thay vì phụ thuộc hành vi null.

### Giá trị rỗng cũng cần phân biệt với vắng mặt

String rỗng `""` là một giá trị **không null**. Nếu source ưu tiên cao có `app.label=`, nó có thể override giá trị `"hello"` ở source thấp bằng chuỗi rỗng.

Không nên suy `getProperty(key, "default")` luôn trả default cho giá trị rỗng; mặc định chỉ áp dụng khi việc lookup không tìm thấy value phù hợp theo contract.

## 12. Giá trị được convert sau khi resolve

`Environment` có overload theo kiểu:

~~~java
Integer timeout =
    environment.getProperty("app.timeout", Integer.class);
~~~

Nếu source ưu tiên cao trả chuỗi `"25"`, Spring có thể convert sang `Integer 25`.

Nhưng giả sử:

~~~text
high-priority: app.timeout=not-a-number
low-priority:  app.timeout=25
~~~

Resolver **không đơn giản bỏ qua một giá trị hợp lệ về mặt tồn tại chỉ vì conversion thất bại** để tự lấy 25 ở source thấp. Giá trị high priority đã thắng ở giai đoạn lookup; yêu cầu chuyển sang Integer có thể gây exception về chuyển đổi.

Vì vậy phải validate cấu hình: giá trị có mặt chưa đủ để đảm bảo đúng type hoặc hợp lệ về business constraints.

### Cấu hình không hợp lệ phải fail fast

Ví dụ:

~~~java
Integer size = environment.getProperty("pool.size", Integer.class);
if (size == null || size <= 0) {
    throw new IllegalArgumentException(
        "pool.size must be a positive integer");
}
~~~

Đây là snippet minh họa, giả định value đã convert được. Nếu input là chuỗi không chuyển sang số được, lỗi conversion có thể phát sinh ngay khi `getProperty` được gọi, trước nhánh `if`.

Trong Spring Boot, typed `@ConfigurationProperties` kết hợp validation thường là lựa chọn tốt cho nhóm cấu hình nhiều trường; nhưng precedence nguồn vẫn là một tầng trước khi binding.

## 13. `@PropertySource` thêm một nguồn từ file như thế nào?

Spring Framework hỗ trợ:

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:service.properties")
public class ServiceConfig {
}
~~~

Đặt file tại `src/main/resources/service.properties`:

~~~properties
service.timeout=20
service.region=VN
~~~

Khi `@Configuration` class được Spring xử lý, `@PropertySource` nạp resource vào `Environment` để các beans tiếp tục dùng property. Đoạn này là một ví dụ cấu hình ngắn, không phải chương trình độc lập có method `main`.

Đọc value:

~~~java
@Bean
ClientSettings clientSettings(Environment env) {
    int timeout = env.getRequiredProperty(
        "service.timeout", Integer.class);
    return new ClientSettings(timeout);
}
~~~

Đoạn factory method minh họa trong class cấu hình, cần class `ClientSettings(int timeout)` và imports liên quan.

### Thứ tự nhiều `@PropertySource`

Nếu có nhiều `@PropertySource` chứa cùng key, giá trị được resolve tùy **thứ tự mà các sources được thêm vào Environment**. Khi các configuration classes được đăng ký/được component scan không có thứ tự cố định mà bạn kiểm soát, việc dựa vào “file khai báo bên dưới sẽ luôn thắng” là mong manh.

Với ứng dụng cần precedence xác định, nên:

- Dùng một configuration entry point với nguồn/thứ tự rõ ràng.
- Tùy biến `MutablePropertySources` có chủ đích trước refresh.
- Kiểm thử giá trị resolved khi nhiều sources chứa cùng key.
- Không dựa vào thứ tự duyệt file hoặc thứ tự component scan ngầm.

### Placeholder trong location

Có thể khai báo:

~~~java
@PropertySource(
    "classpath:config/\${app.stage:default}/service.properties")
~~~

Dòng trên là snippet minh họa trong một `@Configuration` class. Placeholder `\${app.stage:default}` cần được resolve từ những sources **đã sẵn sàng tại thời điểm xử lý annotation**; nếu stage chưa được cung cấp, dùng `default`. Nếu thiếu placeholder bắt buộc và không có default, việc resolve location có thể gây lỗi.

**Đừng tạo vòng phụ thuộc cấu hình:** Không thể kỳ vọng `@PropertySource` tự cung cấp một key cần thiết để resolve chính path của nó nếu key chưa có trong Environment.

## 14. `@PropertySource` không tự đồng nghĩa với `application.properties` của Spring Boot

Một nhầm lẫn lớn là coi file `application.properties` tự động được Spring Framework thuần nạp chỉ vì có tên như thế. Thực tế:

- **Spring Framework thuần**: muốn nạp properties file theo annotation thì thêm `@PropertySource` hoặc đăng ký nguồn theo cách khác.
- **Spring Boot**: có **Config Data infrastructure** chủ động nạp `application.properties`, `application.yml`, file theo profile, vị trí ngoài JAR, imported configuration và nhiều nguồn khác.

Ví dụ Java 17 Spring Context thuần ở mục 8 **không cần và không dùng** Spring Boot, vì ta đăng ký MapPropertySources trực tiếp.

### Vì sao cần phân biệt?

Nếu một chương trình thuần Spring Framework không import file, việc tạo file `src/main/resources/application.properties` rồi hỏi `environment.getProperty("app.mode")` không bảo đảm nhận value từ file đó. Còn trong Spring Boot, Config Data loader có thể tự đăng ký nguồn phù hợp theo conventions của Boot.

## 15. Spring Boot có thứ tự ưu tiên riêng

Spring Boot xây dựng trên Spring `Environment` nhưng bổ sung nhiều `PropertySource` và quy tắc precedence. Ví dụ các nguồn được Boot xử lý có thể gồm:

- Default properties được đặt qua `SpringApplication.setDefaultProperties`.
- `@PropertySource` trên `@Configuration` classes.
- Config data files trong JAR và ngoài JAR, theo profile.
- OS environment variables.
- JVM system properties.
- `SPRING_APPLICATION_JSON`.
- Command-line arguments của ứng dụng Boot.
- Các property overrides của test.
- Một số nguồn đặc thù devtools hoặc Servlet/JNDI tùy môi trường.

**Không áp dụng thứ tự `StandardEnvironment` (chỉ hai nguồn mặc định) như thể đó là bảng đầy đủ của Boot.** Trong Spring Boot 3.x, externalized configuration reference liệt kê **thứ tự precedence từ thấp đến cao**, với các mục sau có thể override mục trước. Trong `MutablePropertySources` của Environment ở runtime, thứ tự duyệt nguồn lại theo **cao đến thấp**.

Ví dụ trong một ứng dụng Spring Boot mặc định:

~~~text
application.properties: app.mode=file
Environment variable:  APP_MODE=env
JVM system property:   -Dapp.mode=jvm
Command-line option:   --app.mode=cli
~~~

Khi các sources ấy được Boot đăng ký theo chính sách mặc định và không có override khác, command-line option thường thắng, nên `app.mode=cli`. Nếu vô hiệu hóa command-line properties hoặc custom reorder sources, kết quả có thể khác.

### Spring Boot imports và profile files

Boot có quy tắc riêng cho:

- `application-{profile}.properties` hoặc YAML tương ứng.
- `spring.config.import`.
- Vị trí config ngoài JAR.
- `spring.config.location` và `spring.config.additional-location`.
- Test properties và profile overrides.

Thứ tự imports có thể ảnh hưởng precedence. Trên Boot, hãy tra đúng **phiên bản tài liệu Boot của dự án**, không đoán dựa trên tài liệu Spring Framework Core hoặc Spring Boot đời khác.

### `@PropertySource` có thể quá muộn cho bootstrap settings

Spring Boot docs lưu ý những sources do `@PropertySource` thêm trên `@Configuration` class chỉ xuất hiện **khi application context refresh**. Một số thiết lập được đọc **trước refresh** (ví dụ một số `logging.*` và `spring.main.*`) có thể không chịu ảnh hưởng như bạn mong đợi nếu chỉ đặt trong `@PropertySource`.

Với cấu hình bootstrap của Boot, dùng cơ chế Externalized Configuration được Boot hỗ trợ theo đúng phase, thay vì ép mọi thứ vào `@PropertySource`.

## 16. Profiles liên quan gì đến property precedence?

Spring `Environment` có hai mảng trách nhiệm chính:

1. **Profiles** — xác định bean definitions/configuration nào được kích hoạt.
2. **Properties** — resolve giá trị từ `PropertySources` theo precedence.

Ví dụ:

~~~java
context.getEnvironment().setActiveProfiles("dev");
~~~

có tác dụng chọn các definitions có `@Profile("dev")` khi Spring xử lý bean registration.

Nhưng việc gọi `setActiveProfiles("dev")` **không tự động thêm một `MapPropertySource` tên dev** hay đảo thứ tự sources của ví dụ thuần Spring Framework.

Trong Spring Boot, profile activation còn ảnh hưởng nguồn Config Data dạng `application-dev.properties`; đó là cơ chế Boot bổ sung.

### Khi có nhiều profile đang active

Đừng đơn giản suy “dev thắng prod” chỉ từ thứ tự chuỗi tên profiles, nhất là khi có imports, profile groups và nhiều files. Cần xem **những PropertySource thực tế đã được đăng ký và thứ tự của chúng** sau khi Boot nạp config.

## 17. Property value trong bean có tự cập nhật khi đổi precedence không?

Không thể coi việc thay `MutablePropertySources` là một cơ chế **tự refresh toàn bộ Spring beans**.

Có ba tình huống:

**Trường hợp A — Caller đọc Environment mỗi lần:**

~~~java
public String currentMode() {
    return environment.getProperty("app.mode");
}
~~~

Nếu PropertySources thay đổi trong một runtime phù hợp, lần gọi sau có thể thấy resolved value mới.

**Trường hợp B — Constructor hoặc `@Bean` đọc một lần:**

~~~java
@Bean
ModeSettings settings(Environment env) {
    return new ModeSettings(env.getProperty("app.mode"));
}
~~~

Đây là snippet minh họa cần class `ModeSettings`. Singleton `ModeSettings` đã được tạo không tự thay state vì source order đổi. Ví dụ `ModeSnapshot` ở mục 8 đã chứng minh.

**Trường hợp C — `@Value` / typed configuration binding:**

~~~java
@Component
class ModeService {
    @Value("\${app.mode:default}")
    private String mode;
}
~~~

Đây là snippet minh họa với import `Component`/`Value`. Giá trị được resolve khi dependency injection diễn ra, không phải một observable property luôn live. Nếu runtime đổi sources, field đã inject không tự cập nhật theo contract Core Spring.

Những cơ chế refresh/rebinding có thể được framework cao hơn cung cấp, nhưng phải được cấu hình rõ ràng. **Không lấy hành vi của Spring Cloud refresh làm mặc định của Spring Core.**

## 18. Debug: xem thứ tự source và nơi giá trị đến từ đâu

Khi một key có giá trị không mong đợi, việc đầu tiên là **xác định thứ tự nguồn tại runtime**.

~~~java
ConfigurableEnvironment configurable =
    (ConfigurableEnvironment) context.getEnvironment();

for (PropertySource<?> source :
        configurable.getPropertySources()) {

    System.out.println("source=" + source.getName());
}
~~~

Đây là snippet trong một method có `ApplicationContext context` thực tế, cần imports `ConfigurableEnvironment` và `PropertySource`. Chỉ in **tên source**, không in toàn bộ values.

Để tìm source đầu tiên chứa giá trị không null cho một key cụ thể:

~~~java
String key = "app.mode";

for (PropertySource<?> source :
        configurable.getPropertySources()) {
    Object value = source.getProperty(key);
    if (value != null) {
        System.out.println("winner source=" + source.getName());
        break;
    }
}
~~~

Đây là snippet minh họa, hợp lý với sources có behavior đơn giản như `MapPropertySource`. Đối với một số nguồn đặc biệt, value resolution có thể bao gồm placeholder conversion, aliasing, origin metadata hoặc custom behavior; việc lấy raw `source.getProperty(key)` không phải lúc nào cũng cho cùng kiểu/chuỗi đã được Environment convert.

### Không lộ secrets khi debug

Đừng in:

~~~java
System.out.println(source.getSource());
~~~

với mọi source trong production: nó có thể chứa credentials, tokens hoặc môi trường nhạy cảm. Tốt hơn là chỉ in:

- Source name.
- Tên key **không nhạy cảm** đang chẩn đoán.
- Có/không có key.
- Giá trị masked khi thật sự cần.
- Vị trí nguồn/metadata an toàn nếu có.

Nếu ứng dụng Spring Boot có Actuator, các endpoint như `env` và `configprops` có thể hỗ trợ chẩn đoán với chính sách sanitization, nhưng phải **bảo vệ endpoint**, cấu hình quyền truy cập và không tự tin rằng mọi secret đã được che ở mọi setup.

## 19. `containsProperty` khác với việc giá trị cuối là gì

Ví dụ:

~~~java
boolean present = environment.containsProperty("service.timeout");
String value = environment.getProperty("service.timeout");
~~~

`present` cho biết có giá trị theo resolver contract, `value` là kết quả resolved. Nhưng muốn **biết source thắng**, phải xem thứ tự PropertySources hoặc cơ chế origin tracing tương ứng.

Một source riêng:

~~~java
PropertySource<?> source =
    context.getEnvironment().getPropertySources()
           .get("configuration");
~~~

Có thể dùng `source.getProperty("service.timeout")` để xem raw value của **một source**. Tuy nhiên:

- `get("configuration")` là lookup source name, không lookup key.
- `getProperty("service.timeout")` là lookup key **bên trong source ấy**.
- `Environment.getProperty("service.timeout")` là lookup toàn bộ sources theo precedence.

Ba thao tác trên có mục đích khác nhau.

## 20. Đặt nguồn cấu hình bằng ApplicationContextInitializer

Trong ứng dụng cần một custom source, nên cài đặt nó **trước khi context refresh**.

Một cách dùng `ApplicationContextInitializer`:

~~~java
import java.util.Map;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

public final class CompanyDefaultsInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        context.getEnvironment().getPropertySources().addLast(
            new MapPropertySource(
                "companyDefaults",
                Map.of("app.retry", "3")
            )
        );
    }
}
~~~

Class này biên dịch được với Spring Context, nhưng **chưa tự được gọi** nếu ứng dụng chưa đăng ký initializer vào bootstrap tương ứng. Với Spring Boot, có cơ chế đăng ký initializers riêng; với Core application có thể gọi `initialize(context)` thủ công trước `refresh()` hoặc gắn vào host framework.

Tại sao phù hợp?

- Có thể thay đổi sources trước khi beans được tạo.
- Tên/precedence được tổ chức ở một điểm bootstrap dễ kiểm thử.
- Tránh khác biệt giữa những beans đã inject config và những beans đọc Environment trực tiếp.

Không nên dùng initializer để mở một HTTP connection dài hạn hoặc chạy nghiệp vụ cần bean graph; nhiệm vụ chính ở đây là **chuẩn bị environment**.

## 21. Dùng `EnvironmentPostProcessor` trong Spring Boot khi cần can thiệp sớm

Nếu yêu cầu là “thêm/chỉnh nguồn cấu hình **trước Boot application context refresh**”, Spring Boot có SPI `EnvironmentPostProcessor`.

Nó có thể hữu ích để thêm một source từ custom configuration provider trong giai đoạn Boot environment setup, trước khi nhiều beans khởi tạo và một số bootstrap settings được đọc.

Cần ghi rõ:

- `EnvironmentPostProcessor` **thuộc Spring Boot**, không thuộc Spring Framework Core Container.
- Cách đăng ký và ordering phụ thuộc phiên bản Boot.
- Không coi `BeanFactoryPostProcessor` hay `@PropertySource` là thay thế hoàn toàn vì timing khác.
- Nếu giá trị đến từ remote config, phải có timeout, chính sách fallback và quản lý secrets.

Nếu dự án chỉ là Spring Framework Core Container, giải pháp đơn giản thường là custom `PropertySource` và `ConfigurableEnvironment.getPropertySources()` trước `refresh()`.

## 22. Các vấn đề với alias, biến môi trường và relaxed binding

### JVM system properties so với OS environment

JVM system properties thường có tên dạng `app.region`, được truyền qua:

~~~bash
java -Dapp.region=VN -jar application.jar
~~~

OS environment variables thường phải dùng dạng `APP_REGION`:

~~~bash
export APP_REGION=VN
java -jar application.jar
~~~

Hai ví dụ shell minh họa cách đặt cấu hình trong môi trường phù hợp; `export` là cú pháp shell Unix, không phải lệnh PowerShell.

`SystemEnvironmentPropertySource` trong Spring Framework hỗ trợ xử lý một số biến thể tên property tương ứng environment variables, ví dụ dấu chấm, dấu gạch ngang và dấu gạch dưới, cũng như uppercase trong nhiều tình huống.

**Đừng lẫn** cơ chế đó với **relaxed binding của Spring Boot `@ConfigurationProperties`**, vì Binder có quy tắc chuyển tên phong phú và riêng biệt, đặc biệt với lists, maps và property names.

### Một lỗi tên key rất khó nhìn

Ví dụ:

~~~text
app.client-url
app.clientUrl
APP_CLIENT_URL
~~~

Không được khẳng định ba chuỗi này tự động là một key giống nhau trong **mọi loại PropertySource**. Với `MapPropertySource` được tạo trực tiếp từ `Map`, key lookup thường chính xác theo key map, trong khi `SystemEnvironmentPropertySource` và Boot Binder có logic thích ứng khác.

Hãy dùng quy ước tên thống nhất và kiểm tra API thực tế đang resolve/bind.

## 23. Thứ tự ưu tiên trong tests

Một integration test có thể cần override config của ứng dụng mà không đổi file production. Các khả năng:

- Dùng `@TestPropertySource` trong Spring Test.
- Dùng property attributes của `@SpringBootTest`.
- Dùng `@DynamicPropertySource` cho động values trong Spring Test.
- Thêm `MapPropertySource` trước `context.refresh()` trong một test thuần Spring.

**Không dùng một Java system property global trong nhiều tests chạy parallel nếu test sau có thể bị ảnh hưởng.** Ví dụ:

~~~java
System.setProperty("app.mode", "test");
~~~

Thao tác này thay đổi `System.getProperties()` toàn JVM; cần cleanup/reset và tránh race trong test suites concurrent. Một MapPropertySource gắn vào context test riêng thường cô lập hơn.

Trong Spring Boot/Spring Test, các nguồn test có precedence được framework định nghĩa. Phải kiểm tra tài liệu đúng phiên bản, tránh đoán vị trí relative chỉ từ tên annotation.

### Một test Core tối giản không phụ thuộc test framework

~~~java
try (var context = new AnnotationConfigApplicationContext()) {
    context.getEnvironment().getPropertySources().addFirst(
        new MapPropertySource(
            "testOverrides",
            Map.of("app.mode", "test")
        )
    );
    context.refresh();

    if (!"test".equals(
            context.getEnvironment().getProperty("app.mode"))) {
        throw new AssertionError("Property override failed");
    }
}
~~~

Đây là snippet Java 17 cần imports `AnnotationConfigApplicationContext`, `MapPropertySource` và `Map`. Nó chứng minh phép tra cứu Environment tại context độc lập.

## 24. Tranh chấp hoặc sai lệch khi cập nhật PropertySources ở runtime

`MutablePropertySources` cho phép chỉnh danh sách nguồn, nhưng điều đó không tự cung cấp một giao thức dynamic config toàn diện:

1. Một bean có thể đã được tạo từ value cũ.
2. Bean khác gọi `Environment.getProperty` mỗi lần sẽ thấy value mới.
3. Các nguồn dùng `Map` mutable có thể thay đổi dưới concurrency.
4. Các readers có thể quan sát state khác nhau nếu cấu hình được cập nhật từng bước.
5. Không phải mọi client/pool có thể thay config đang chạy mà không recreate.
6. Một số setting ảnh hưởng bootstrap không còn cơ hội áp dụng sau refresh.

Nếu phải hỗ trợ runtime reload:

- Thiết kế một configuration snapshot bất biến, được publish atomically.
- Có validation trước khi áp dụng.
- Chỉ cập nhật những properties được hỗ trợ live reload.
- Xác định thứ tự dừng/tạo lại resource nếu thay connection settings.
- Thông báo rõ version/timestamp cấu hình.
- Cân nhắc infrastructure reload/rebind chính thức của framework đang dùng.

Mã demo mục 8 cố ý reorder sau refresh để cho thấy **khác biệt giữa resolved value và snapshot value**. Không sao chép pattern ấy sang production như một live reload solution.

## 25. Lỗi phổ biến và cách xử lý

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| Value từ file không được dùng | Một PropertySource ưu tiên cao hơn chứa cùng key | Duyệt sources từ cao xuống, xác nhận source thắng |
| `-Dapp.mode` bị custom source override | Code gọi `addFirst` cho custom source | Điều chỉnh precedence theo chính sách operator override |
| Environment tìm được value nhưng `@Value` field vẫn cũ | Field được inject một lần trước khi sources đổi | Không kỳ vọng auto refresh; thiết kế reload rõ ràng |
| File `application.properties` không có tác dụng ở Spring Core | Core không tự nạp Boot config conventions | Dùng `@PropertySource` hoặc custom source |
| Boot config không đúng dự kiến | Config Data/profile/import/test precedence riêng | Kiểm tra Boot docs đúng version và live source list |
| `@PropertySource` không ảnh hưởng `logging.*` | Source được thêm quá muộn cho một số bootstrap settings | Dùng Boot-supported early external config |
| Value default không được dùng | Source ưu tiên cao trả chuỗi rỗng hoặc value không null | Phân biệt rỗng với vắng mặt |
| Conversion Integer thất bại | Source thắng chứa chuỗi không parse được | Validate và fail fast; không mong resolver fallback |
| Duplicate source name | Đăng ký sources không có naming policy | Dùng unique names, `replace` khi cần |
| `addBefore` ném exception | Relative source name chưa tồn tại | Kiểm tra `sources.contains(name)` |
| JVM property không thắng OS env | Custom reorder hoặc source khác ưu tiên cao | Xem thứ tự nguồn trong context thực |
| Một key dùng camelCase nhưng không tìm được | `MapPropertySource` không áp dụng mọi relaxed binding | Chuẩn hóa key, kiểm tra đúng resolver/Binder |
| Hai profiles cùng active cho giá trị bất ngờ | Thứ tự config sources/imports khác giả định | Xem nguồn thực tế, không đoán theo tên profile |
| Unit test làm test khác bị đổi value | `System.setProperty` sửa toàn JVM | Dùng sources per-context và cleanup rõ |
| Config được update nhưng pool vẫn dùng config cũ | Singleton/client snapshot không tự recreate | Thiết kế reload resource có lifecycle |
| Log để debug làm lộ password | Dump toàn bộ `PropertySource.getSource()` | Mask secrets; chỉ log tên nguồn/key khi cần |

## 26. Quy trình chẩn đoán một khóa bị trùng

**Bước 1 — Ghi chính xác key mà ứng dụng truy vấn.** Ví dụ `billing.timeout`. Kiểm tra có dấu chấm, gạch ngang, chữ hoa hay khác naming convention không.

**Bước 2 — Xác định API đang sử dụng.** `Environment.getProperty`, `@Value`, `@ConfigurationProperties` Binder hoặc custom `PropertySource` có thể có chi tiết xử lý khác nhau.

**Bước 3 — Xác định context.** Spring Core `StandardEnvironment`, Servlet context hay Spring Boot Config Data? Có parent/child context hoặc multiple application contexts không?

**Bước 4 — Duyệt `getPropertySources()` từ trên xuống.** Ghi tên từng source và thứ tự. Đây là cơ sở thực tế, tránh đoán thứ tự từ tài liệu của phiên bản khác.

**Bước 5 — Tìm source đầu trả value không null.** Khi dùng key không nhạy cảm, có thể kiểm tra raw value; với secrets thì mask.

**Bước 6 — Kiểm tra conversion/binding.** Có thể source thắng nhưng value sai type hoặc danh sách bị binding theo quy tắc riêng.

**Bước 7 — So sánh thời điểm đọc.** Bean đọc property khi tạo hay mỗi lần gọi? Sources có bị mutate sau refresh không?

**Bước 8 — Kiểm tra Boot import/profile overrides nếu có.** `spring.config.import`, external file, profile variants và test configuration có thể tạo nguồn với thứ tự bất ngờ.

**Bước 9 — Tạo integration test tái hiện trùng khóa.** Dùng ba MapPropertySource, assert value sau mỗi thao tác reorder và assert bean snapshot không đổi.

**Bước 10 — Ghi chính sách precedence vào tài liệu dự án.** Ví dụ “command-line vận hành được override defaults thư viện; tenant runtime không được override secrets”.

## 27. Câu hỏi tự kiểm tra

**Câu 1.** Nếu ba PropertySources cùng chứa `app.color`, Spring chọn giá trị nào?

**Đáp án:** Theo thứ tự tìm kiếm từ precedence cao xuống, chọn giá trị không null đầu tiên mà resolver nhận được; không merge ba strings.

**Câu 2.** `sources.addFirst(new MapPropertySource("emergency", ...))` có ý nghĩa gì?

**Đáp án:** Source `emergency` được đặt ở mức ưu tiên cao nhất trong danh sách. Key mà nó cung cấp có thể override cùng key ở sources phía sau.

**Câu 3.** `sources.addLast(...)` có thích hợp cho defaults không?

**Đáp án:** Có. Source ở cuối cung cấp fallback khi sources ưu tiên cao hơn không có key.

**Câu 4.** `StandardEnvironment` mặc định đặt JVM system properties trước hay sau OS environment?

**Đáp án:** JVM system properties thường ở precedence cao hơn OS environment variables. Cấu hình tùy biến có thể thay đổi thứ tự.

**Câu 5.** Một source ưu tiên cao có `timeout="abc"`, source thấp có `timeout="30"`. Khi convert sang Integer, Spring tự bỏ abc để lấy 30 không?

**Đáp án:** Không nên kỳ vọng như vậy. Giá trị abc đã thắng khi lookup, còn conversion có thể báo lỗi. Phải validate cấu hình hoặc sửa source ưu tiên cao.

**Câu 6.** Đổi thứ tự PropertySources sau context refresh có tự cập nhật `@Value` field của singleton không?

**Đáp án:** Không theo contract Spring Core thông thường. Field được inject lúc bean tạo; muốn reload phải có giải pháp cụ thể.

**Câu 7.** `@PropertySource("classpath:app.properties")` và Spring Boot Config Data có giống nhau không?

**Đáp án:** Không. `@PropertySource` là annotation của Spring Framework để thêm resource source trong context processing. Spring Boot Config Data có cơ chế và thứ tự nguồn rộng hơn, được xử lý ở bootstrap.

**Câu 8.** Có thể biết source nào thắng bằng cách chỉ gọi `environment.getProperty("key")` không?

**Đáp án:** Method này trả value, không trực tiếp cung cấp tên source. Cần kiểm tra danh sách PropertySources hoặc dùng origin/diagnostic tooling phù hợp.

**Câu 9.** Tại sao `app.mode=` (chuỗi rỗng) có thể override `app.mode=prod` ở source thấp?

**Đáp án:** Chuỗi rỗng không phải null, nên có thể được chọn bởi resolver. Default chỉ được dùng khi không tìm thấy value theo contract.

**Câu 10.** Nếu một `MapPropertySource` có key `app.maxConnections` còn code hỏi `app.max-connections`, có chắc chắn được nhận diện là cùng key không?

**Đáp án:** Không. `MapPropertySource` không tự cung cấp toàn bộ relaxed binding rules của Boot; dùng canonical keys và đúng API.

## 28. Checklist áp dụng

- [ ] Danh sách PropertySources thực tế đã được xác định từ context đúng chưa?
- [ ] Có ghi rõ nguồn nào được override nguồn nào không?
- [ ] Custom source cần `addFirst` hay `addLast` theo chính sách vận hành?
- [ ] Các source names có phân biệt và không vô tình trùng không?
- [ ] Key naming có thống nhất giữa JVM, OS, MapPropertySource và Binder không?
- [ ] Giá trị bắt buộc có validation/typed conversion và fail-fast không?
- [ ] Có phân biệt chuỗi rỗng, null và key vắng mặt không?
- [ ] Có test khi cùng key xuất hiện ở nhiều nguồn không?
- [ ] Có thay sources sau refresh khiến snapshot beans bất nhất không?
- [ ] Với Spring Boot, có xem đúng tài liệu externalized configuration theo phiên bản không?
- [ ] Có bảo vệ secrets khi debug nguồn giá trị không?
- [ ] Có chọn bootstrap hook phù hợp để thêm sources trước khi beans đọc config không?

## 29. Tóm tắt

- `PropertySource` là **một nguồn key-value có tên**; `MutablePropertySources` là **danh sách có thứ tự** các nguồn.
- `Environment.getProperty(key)` tra theo thứ tự ưu tiên và thường chọn **giá trị không null đầu tiên**.
- `addFirst` đặt source ưu tiên cao nhất; `addLast` đặt thấp nhất; `addBefore`/`addAfter` cho phép định vị chính xác.
- Khi trùng key, source thắng **không ghi đè vật lý** source thua; đây là kết quả resolution tại thời điểm tra cứu.
- `StandardEnvironment` mặc định ưu tiên JVM system properties hơn OS environment variables.
- Spring Boot có **Config Data và precedence riêng**, không nên áp nguyên thứ tự của Core `StandardEnvironment`.
- `@PropertySource` là cách khai báo nguồn file cho Spring Framework, nhưng không phải giải pháp cho mọi cấu hình bootstrap của Boot.
- Đổi precedence runtime có thể làm Environment trả value mới trong khi beans đã inject vẫn giữ value cũ.
- Khi debug, kiểm tra **source order thật và nơi key được định nghĩa**, không dựa vào giả định file nào “mạnh hơn”.
- Duy trì chính sách override được tài liệu hóa, validation, bảo mật secrets và integration tests.

## 30. Tài liệu chính thức

- [Spring Framework — Environment Abstraction](https://docs.spring.io/spring-framework/reference/core/beans/environment.html): PropertySource, Environment, hệ thống property source mặc định và `@PropertySource`.
- [Spring Framework 6.1 — PropertySource Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/env/PropertySource.html): `getProperty`, `containsProperty`, `name` và abstraction nguồn.
- [Spring Framework 6.1 — MapPropertySource Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/env/MapPropertySource.html): nguồn từ map, `getPropertyNames` và lưu ý về null values.
- [Spring Framework 6.1 — MutablePropertySources Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/env/MutablePropertySources.html): `addFirst`, `addLast`, `addBefore`, `addAfter`, `remove`, `replace`.
- [Spring Framework 6.1 — ConfigurableEnvironment Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/env/ConfigurableEnvironment.html): tùy biến sources trước refresh.
- [Spring Framework 6.1 — StandardEnvironment Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/env/StandardEnvironment.html): `systemProperties` và `systemEnvironment`.
- [Spring Boot 3.3 — Externalized Configuration](https://docs.spring.io/spring-boot/3.3/reference/features/external-config.html): precedence riêng của Boot, config data, profile/config imports và test overrides.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-environment-004">Đọc thuộc tính bằng getProperty, containsProperty và getRequiredProperty</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc thuộc tính bằng getProperty, containsProperty và getRequiredProperty trong Spring Framework

## 1. Mục tiêu của bài

Spring Framework cung cấp abstraction `Environment` để ứng dụng đọc cấu hình từ nhiều nguồn mà không cần phụ thuộc trực tiếp vào `System.getProperty`, `System.getenv` hoặc một file cụ thể. Ba phương thức quan trọng của `PropertyResolver`, cũng là thành phần mà `Environment` kế thừa, gồm:

- **`getProperty(...)`**: đọc giá trị, có thể trả `null`, giá trị mặc định hoặc kết quả đã chuyển kiểu.
- **`containsProperty(key)`**: hỏi xem có thể phân giải key thành giá trị **khác `null`** hay không.
- **`getRequiredProperty(...)`**: yêu cầu key phải phân giải được; khi thiếu sẽ **ném `IllegalStateException`**.

Bài thuộc nhóm **Môi trường cấu hình** trong Spring Framework Core Container. Sau khi học, bạn có thể chọn đúng API cho cấu hình bắt buộc/tùy chọn, hiểu thứ tự ưu tiên của `PropertySource`, phân biệt giá trị thiếu và chuỗi rỗng, chuyển kiểu an toàn, viết ví dụ Java 17 chạy độc lập và kiểm thử các đường lỗi.

## 2. Environment, PropertyResolver và PropertySource

Các interface trọng tâm thuộc `org.springframework.core.env`:

~~~text
PropertyResolver
    containsProperty(String)
    getProperty(String)
    getProperty(String, defaultValue)
    getProperty(String, Class<T>)
    getProperty(String, Class<T>, defaultValue)
    getRequiredProperty(String)
    getRequiredProperty(String, Class<T>)
    resolvePlaceholders(...)
          ^
          |
Environment
    getActiveProfiles()
    getDefaultProfiles()
    acceptsProfiles(...)
          ^
          |
ConfigurableEnvironment
    getPropertySources()
    setActiveProfiles(...)
    setRequiredProperties(...)
    validateRequiredProperties()
~~~

Sơ đồ trên chỉ trình bày những API thiết yếu; các interface thực tế còn có superinterfaces, overload và phương thức khác.

**`Environment` có hai mảng trách nhiệm:** profiles xác định nhóm bean definitions được kích hoạt, còn properties phục vụ tra key/value. `containsProperty("spring.profiles.active")` không có nghĩa profile cụ thể nào đó chắc chắn đang active; để kiểm tra profile, dùng `getActiveProfiles()` hoặc `acceptsProfiles(Profiles.of("prod"))`.

**`PropertySource`** là một nguồn đọc cấu hình, ví dụ map, JVM system properties, OS environment variables hay file được Spring nạp. **`MutablePropertySources`** tập hợp các sources có thứ tự. `ConfigurableEnvironment` cho phép sửa danh sách đó, trong khi `Environment` thường là contract đủ hẹp để inject vào service đọc cấu hình.

~~~java
import org.springframework.core.env.Environment;

public final class RemoteEndpointReader {
    private final Environment environment;

    public RemoteEndpointReader(Environment environment) {
        this.environment = environment;
    }

    public String endpoint() {
        return environment.getRequiredProperty("remote.endpoint");
    }
}
~~~

Đây là **class Java minh họa**, không phải ứng dụng độc lập: cần Spring cung cấp bean `RemoteEndpointReader` và property `remote.endpoint` nếu muốn gọi method thành công. Nó cho thấy constructor injection theo contract hẹp thay vì truyền cả `ApplicationContext`.

## 3. Bảng tra nhanh ba phương thức

| API | Có key/giá trị khác null | Thiếu key | Ý nghĩa |
| --- | --- | --- | --- |
| `getProperty("a")` | Trả `String` | `null` | Đọc tùy chọn |
| `getProperty("a", "fallback")` | Trả giá trị thực | `"fallback"` | Fallback khi thiếu |
| `getProperty("a", Integer.class)` | Giá trị chuyển sang `Integer` | `null` | Đọc typed property |
| `getProperty("a", Integer.class, 5)` | Giá trị đã chuyển kiểu | `5` | Typed fallback |
| `containsProperty("a")` | `true` | `false` | Presence, không kiểm tra ý nghĩa |
| `getRequiredProperty("a")` | Trả `String` | Ném `IllegalStateException` | Fail fast |
| `getRequiredProperty("a", Integer.class)` | Trả `Integer` | Ném `IllegalStateException` | Required + conversion |

**Hai lỗi logic dễ gặp:** dùng `containsProperty` để diễn giải `feature.enabled=false` thành true; hoặc tưởng `getProperty(key, fallback)` sẽ dùng fallback nếu value là chuỗi rỗng/sai định dạng. Cả hai giả định đều sai.

Những chữ ký trên có nguồn từ `PropertyResolver` Javadoc. Các overload có `Class<T>` dùng conversion service thích hợp và có thể gây **lỗi chuyển kiểu** riêng nếu property hiện diện nhưng sai định dạng.

## 4. getProperty: optional và default

### 4.1. Trả String hoặc null

~~~java
String host = environment.getProperty("remote.host");

if (host == null) {
    // Không tìm thấy key ở các PropertySources đã đăng ký.
}
~~~

Đoạn này là **snippet trong một method** có biến `Environment environment`. Không gọi `host.isBlank()` khi chưa kiểm tra null.

Khi cấu hình có tính tùy chọn, null có thể có ý nghĩa: tắt một tích hợp, dùng môi trường local hoặc bỏ qua một optional behavior. Khi giá trị là bắt buộc, đừng dùng `getProperty` rồi để null gây `NullPointerException` xa vị trí cấu hình; chọn `getRequiredProperty` để lỗi rõ hơn.

### 4.2. Default value chỉ áp dụng khi thiếu

~~~java
String region =
        environment.getProperty("remote.region", "local");
~~~

Nếu `remote.region` vắng mặt, kết quả `"local"`. Nếu source cung cấp `"asia"`, kết quả `"asia"`. Nếu source cung cấp `""`, kết quả **chuỗi rỗng**, không phải `"local"`.

~~~text
Key vắng:       getProperty(key, "local") -> "local"
Key là "asia":  getProperty(key, "local") -> "asia"
Key là "":      getProperty(key, "local") -> ""
~~~

Với giá trị blank/whitespace, chỉ riêng sự hiện diện trong PropertySource chưa chứng minh nội dung phù hợp. Hãy kiểm tra `String.isBlank()`/`StringUtils.hasText(...)` và áp quy tắc nghiệp vụ tương ứng.

### 4.3. Đọc kiểu số và boolean

~~~java
Integer timeout =
        environment.getProperty("client.timeout", Integer.class);

int retryCount =
        environment.getProperty("client.retries", Integer.class, 3);

Boolean enabled =
        environment.getProperty("feature.enabled", Boolean.class, false);
~~~

Đây là code minh họa, cần `environment` hợp lệ. Khi nguồn chứa `client.timeout=120`, Spring có thể chuyển `"120"` sang `Integer.valueOf(120)`. Khi nguồn chứa `not-number` thay vì một số hợp lệ, conversion có thể ném lỗi; **typed fallback không tự động chữa giá trị sai định dạng**.

Với boolean flag, nên kiểm tra:

~~~java
boolean enabled = Boolean.TRUE.equals(
        environment.getProperty("feature.enabled", Boolean.class, false));
~~~

Không viết `if (environment.containsProperty("feature.enabled"))` nếu ý nghĩa là bật feature. Property `feature.enabled=false` có mặt nên `containsProperty` vẫn true.

### 4.4. Giá trị hợp kiểu vẫn có thể sai nghiệp vụ

~~~java
int port =
        environment.getProperty("client.port", Integer.class, 8080);

if (port < 1 || port > 65535) {
    throw new IllegalArgumentException("client.port is outside 1..65535");
}
~~~

Conversion không biết mọi business constraints. Số âm, port 0 hoặc chuỗi địa chỉ không hợp lệ có thể cần validation riêng. **Đọc, chuyển kiểu và validate là ba trách nhiệm khác nhau.**

## 5. containsProperty: khác null, không phải hợp lệ

`containsProperty(String key)` trả true khi resolver tìm thấy giá trị có thể phân giải cho key, điển hình tương đương tìm được giá trị khác `null`. Theo contract:

| Nguồn chứa | `containsProperty` | Có cần validate thêm? |
| --- | --- | --- |
| Không có key | `false` | Có thể cần fallback/required |
| `key=hello` | `true` | Tùy nghiệp vụ |
| `key=` (chuỗi rỗng) | `true` | Thường cần non-blank check |
| `key=   ` (toàn dấu cách) | `true` | Thường cần non-blank check |
| `key=false` | `true` | Có: đây là text/value, không phải false của phép presence |
| `key=broken-number` | `true` | Có: Integer conversion có thể lỗi |

**Dễ nhầm với Java Map:** `Map.containsKey(key)` có thể true khi entry có value `null`; Spring `PropertySource.getProperty(key)` trả null được hiểu là chưa tìm thấy ở source đó. Resolver có thể tiếp tục tìm các sources ưu tiên thấp hơn. Vì vậy `containsProperty` không phải phép kiểm tra entry vật lý trong một map nhất định.

Nếu code cần đọc value ngay sau khi kiểm tra presence, đôi khi chỉ nên đọc một lần:

~~~java
String value = environment.getProperty("feature.label");
if (value != null) {
    // Giá trị có mặt; có thể vẫn là "".
}
~~~

Hai lời gọi `containsProperty` rồi `getProperty` có thể thừa; nếu underlying sources được cập nhật giữa hai lần gọi, chúng còn có thể quan sát trạng thái khác nhau.

### Khi nào containsProperty thực sự hữu ích?

- Diagnostics: key đã được khai báo hay chưa, độc lập với việc nội dung có phù hợp.
- Optional setting: quyết định có áp một kiểu cấu hình được cung cấp tường minh hay không.
- Logic kiểm tra presence trước khi thực hiện một nhánh code không cần đọc value.

Nếu mục tiêu là required configuration, `getRequiredProperty` truyền đạt ý đồ tốt hơn.

## 6. getRequiredProperty: báo lỗi rõ khi thiếu

~~~java
String databaseUrl =
        environment.getRequiredProperty("database.url");

Integer databasePort =
        environment.getRequiredProperty("database.port", Integer.class);
~~~

Cả hai là snippets trong method có `Environment environment`. Nếu key không phân giải được, `PropertyResolver` ném **`IllegalStateException`**. Điều này hữu ích trong constructor hoặc `@Bean` method khi application **không nên chạy tiếp** nếu cấu hình nền tảng thiếu.

### Required khác validation

Key `service.name` có thể tồn tại với `""`; `getRequiredProperty("service.name")` khi đó vẫn có thể trả chuỗi rỗng. Vì thế:

~~~java
String name =
        environment.getRequiredProperty("service.name");

if (name.isBlank()) {
    throw new IllegalArgumentException("service.name must not be blank");
}
~~~

Đây là một kiểm tra **nghiệp vụ bổ sung**, không phải requirement của `getRequiredProperty`.

Nếu property `database.port` chứa `"abc"`, overload typed không thể chuyển thành `Integer` và có thể phát sinh conversion exception; lỗi này khác với `IllegalStateException` do không tìm được key. Đừng viết catch `IllegalStateException` rồi xem **mọi** vấn đề cấu hình đều là “key chưa khai báo”.

### Nên fail fast ở đâu?

Với configuration bắt buộc, nên tạo/validate settings khi context bootstrap. Một bean phụ thuộc vào typed settings sẽ không được sử dụng trong trạng thái thiếu cấu hình. Tránh đặt `getRequiredProperty` ở mỗi request rồi để request đầu tiên mới phát hiện deploy sai.

Nếu cấu hình thật sự tùy chọn, fallback rõ ràng và được document là lựa chọn hợp lý hơn.

## 7. PropertySources và thứ tự ưu tiên

`Environment` không chỉ có một `Map` duy nhất. Nó tra cứu tuần tự qua `PropertySources`; source xuất hiện trước có độ ưu tiên cao hơn nếu cung cấp value khác null.

~~~text
MutablePropertySources
  1. "runtimeOverride":   service.name = "NewName"
  2. "fileDefaults":      service.name = "BaseName"
                          service.region = "south"
  3. "systemProperties":  JVM -D arguments
  4. "systemEnvironment": OS environment variables

getProperty("service.name")   -> "NewName"
getProperty("service.region") -> "south"
~~~

Trong `StandardEnvironment` Java thuần, các nguồn mặc định là JVM system properties và OS environment variables; **system properties thường có ưu tiên cao hơn environment variables**. Servlet environment bổ sung các nguồn có thứ tự khác. Spring Boot có hệ thống ConfigData/command-line override riêng, không nên lấy thứ tự ví dụ Java thuần làm mọi quy tắc Boot.

Khi dùng `ConfigurableEnvironment`:

~~~java
ConfigurableEnvironment environment = context.getEnvironment();
environment.getPropertySources().addFirst(
        new MapPropertySource(
                "myOverrides",
                Map.<String, Object>of(
                        "service.name", "NewName")));

environment.getPropertySources().addLast(
        new MapPropertySource(
                "fallbacks",
                Map.<String, Object>of(
                        "service.region", "south")));
~~~

Đây là snippet, cần import `ConfigurableEnvironment`, `MapPropertySource` và `java.util.Map`, với `context` hợp lệ. `addFirst` áp precedence cao nhất, `addLast` thấp nhất. Nếu cùng key nằm ở nhiều sources, hãy nghĩ về **thứ tự nguồn**, không nghĩ rằng Spring tự merge mọi giá trị.

## 8. Ví dụ hoàn chỉnh Java 17 + Spring Framework 6.2

Ví dụ chạy độc lập không cần Spring Boot, database hoặc web server. Nó chứng minh:

- Map source `tutorialOverrides` đứng trước `tutorialDefaults`.
- Key `name` có hai giá trị: source ưu tiên cao thắng.
- Key `region` chỉ có trong defaults: resolver tìm xuống source phía sau.
- Chuỗi rỗng vẫn làm `containsProperty` trả true.
- Missing key cho `null`, fallback, `false` và `IllegalStateException` tùy API.
- Typed property `port` chuyển từ `"8088"` sang `Integer`.
- `Environment` được inject vào `@Bean` method để tạo immutable settings record.

### 8.1. Cấu trúc

~~~text
property-reader-demo/
├── pom.xml
└── src/main/java/demo/
    └── PropertyReaderDemo.java
~~~

### 8.2. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>property-reader-demo</artifactId>
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

### 8.3. src/main/java/demo/PropertyReaderDemo.java

~~~java
package demo;

import java.util.Map;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

public class PropertyReaderDemo {

    private static final String P = "learning220.sample.";

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            ConfigurableEnvironment environment =
                    context.getEnvironment();

            environment.getPropertySources().addFirst(
                    new MapPropertySource(
                            "tutorialDefaults",
                            Map.<String, Object>of(
                                    P + "name", "DefaultApp",
                                    P + "region", "south")));

            environment.getPropertySources().addFirst(
                    new MapPropertySource(
                            "tutorialOverrides",
                            Map.<String, Object>of(
                                    P + "name", "OverrideApp",
                                    P + "port", "8088",
                                    P + "empty", "")));

            context.register(AppConfig.class);
            context.refresh();

            String absent = P + "never.set.unknown";

            System.out.println(
                    "name=" + environment.getProperty(P + "name"));
            System.out.println(
                    "region=" + environment.getProperty(P + "region"));
            System.out.println(
                    "containsName=" + environment.containsProperty(P + "name"));
            System.out.println(
                    "containsEmpty=" + environment.containsProperty(P + "empty"));
            System.out.println(
                    "emptyValue=[" + environment.getProperty(P + "empty") + "]");
            System.out.println(
                    "containsMissing=" + environment.containsProperty(absent));
            System.out.println(
                    "missing=" + environment.getProperty(absent));
            System.out.println(
                    "defaultMissing=" +
                            environment.getProperty(absent, "fallback"));
            System.out.println(
                    "portAsInt=" + environment.getProperty(
                            P + "port", Integer.class));
            System.out.println(
                    "requiredName=" + environment.getRequiredProperty(
                            P + "name"));

            try {
                environment.getRequiredProperty(absent);
            } catch (IllegalStateException ex) {
                System.out.println(
                        "requiredMissing=" + ex.getClass().getSimpleName());
            }

            System.out.println(
                    "settings=" + context.getBean(AppSettings.class));
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {

        @Bean
        public AppSettings appSettings(Environment environment) {
            String name = environment.getRequiredProperty(P + "name");
            Integer port =
                    environment.getRequiredProperty(P + "port", Integer.class);
            String region =
                    environment.getProperty(P + "region", "unknown");

            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("port outside 1..65535");
            }
            return new AppSettings(name, port, region);
        }
    }

    public record AppSettings(String name, Integer port, String region) {
    }
}
~~~

### 8.4. Chạy

Trong thư mục `property-reader-demo`:

~~~bash
java -version
mvn -version
mvn -q compile exec:java -Dexec.mainClass=demo.PropertyReaderDemo
~~~

Stdout của chương trình, không tính logging Maven/Spring và giả định không có system property/environment variable cố ý trùng tên `learning220.sample.never.set.unknown`:

~~~text
name=OverrideApp
region=south
containsName=true
containsEmpty=true
emptyValue=[]
containsMissing=false
missing=null
defaultMissing=fallback
portAsInt=8088
requiredName=OverrideApp
requiredMissing=IllegalStateException
settings=AppSettings[name=OverrideApp, port=8088, region=south]
~~~

### 8.5. Giải thích

- **`name=OverrideApp`**: source `tutorialOverrides` được thêm bằng `addFirst` sau cùng, nên có ưu tiên cao nhất.
- **`region=south`**: source đầu không chứa key; resolver tra tiếp `tutorialDefaults` và tìm thấy `"south"`.
- **`containsEmpty=true`** và **`emptyValue=[]`**: chuỗi rỗng khác null; dấu ngoặc vuông bao giá trị để thấy rằng giữa chúng không có ký tự nào.
- **`containsMissing=false`**: key không được phân giải ở sources mặc định và hai custom maps, trong giả định đầu ra.
- **`missing=null`**: `getProperty` trả Java `null`; nối chuỗi khi in tạo chữ `null`.
- **`defaultMissing=fallback`**: overload có default dùng khi value missing.
- **`portAsInt=8088`**: string `"8088"` được chuyển thành `Integer`.
- **`requiredMissing=IllegalStateException`**: required API ném exception; code bắt và chỉ in tên class.
- **`settings=...`**: Spring inject `Environment` vào method `@Bean` và trả Java record; settings object chứa những giá trị đã validate.

### 8.6. Thí nghiệm

1. **Đổi thứ tự addFirst:** nếu thêm `tutorialDefaults` *sau* `tutorialOverrides` bằng `addFirst`, `name` trở thành `DefaultApp`.
2. **Xóa name khỏi cả hai maps:** `AppSettings` không thể được tạo nếu không có source khác chứa key, vì `getRequiredProperty` ném lỗi trong `refresh()`.
3. **Đặt port thành `"abc"`:** typed conversion `Integer.class` không thành công; context startup có thể thất bại.
4. **Đặt port thành `"0"`:** conversion thành công nhưng validation range ném lỗi.
5. **Đặt name thành `""`:** required lookup không xem nó là missing, nhưng `name.isBlank()` chặn cấu hình sai.
6. **Bỏ region khỏi defaults:** settings nhận `"unknown"` nếu không có source nào khác chứa region.
7. **Thêm system property trùng absent key:** kết quả cho missing thay đổi; Spring làm đúng vì các default property sources vẫn được giữ lại trong `AnnotationConfigApplicationContext`.

## 9. Nhiều required keys: setRequiredProperties

`getRequiredProperty` kiểm tra từng key tại lúc code gọi. Khi muốn kiểm tra một tập hợp keys, `ConfigurablePropertyResolver`/`ConfigurableEnvironment` còn có:

~~~java
ConfigurableEnvironment environment = context.getEnvironment();

environment.setRequiredProperties(
        "client.url", "client.username", "client.region");

environment.validateRequiredProperties();
~~~

Đây là snippet trong method có `context`, không phải ứng dụng độc lập. `setRequiredProperties` khai báo danh sách; `validateRequiredProperties` xác nhận chúng phân giải được thành giá trị khác null, và có thể ném `MissingRequiredPropertiesException` nếu thiếu.

Sự khác biệt:

| Cách | Chức năng |
| --- | --- |
| `getRequiredProperty(key)` | Đọc một value và fail khi thiếu |
| `getRequiredProperty(key, Class<T>)` | Đọc typed value và fail khi thiếu |
| `setRequiredProperties(keys...)` | Khai báo danh sách required |
| `validateRequiredProperties()` | Kiểm tra danh sách và báo thiếu |

Không nhầm `setRequiredProperties` với một lệnh **tự chạy toàn bộ validation ngay tại chỗ**. Nếu cần kiểm chứng rõ trong custom bootstrap/test, hãy gọi `validateRequiredProperties` tường minh. Các overload required/presence vẫn không tự kiểm tra độ dài, định dạng URL hoặc khoảng giá trị.

## 10. @Value, @PropertySource và placeholder

### 10.1. @Value khác Environment.getProperty

~~~java
@Component
public class ClientSettings {
    @Value("${client.retries:3}")
    private int retries;
}
~~~

Snippet yêu cầu imports `@Component`, `@Value` và annotation processing phù hợp. Dấu `:3` là default của placeholder expression, khác với Java overload `getProperty("client.retries", Integer.class, 3)`.

`Environment.getProperty` phù hợp khi code chủ động truy xuất key. `@Value` phù hợp khi inject một giá trị được xác định sẵn vào field/constructor/factory method parameter. Với nhiều cấu hình liên quan, một typed settings object thường dễ validation và test hơn việc rải `@Value` ở nhiều classes.

### 10.2. @PropertySource thêm file

~~~java
@Configuration
@PropertySource("classpath:client.properties")
public class ClientConfig {
    @Bean
    public ClientName clientName(Environment environment) {
        return new ClientName(
                environment.getRequiredProperty("client.name"));
    }
}
~~~

Snippet này cần class `ClientName` và file `src/main/resources/client.properties`, ví dụ:

~~~properties
client.name=payment-client
client.timeout=120
~~~

Plain Spring Framework **không mặc nhiên nạp `application.properties` theo convention của Spring Boot**. Cần đăng ký sources qua `@PropertySource` hoặc programmatic `PropertySources`. Trong Spring Boot, ConfigData và property precedence rộng hơn.

### 10.3. Resolve placeholder tường minh

~~~java
String url = environment.resolvePlaceholders(
        "https://${client.host:localhost}:${client.port:8080}/api");
~~~

`resolvePlaceholders` thay những placeholder có thể phân giải, còn placeholder thiếu mà không có default có thể được giữ nguyên. `resolveRequiredPlaceholders` ném `IllegalArgumentException` nếu gặp placeholder không thể phân giải. Đừng nhầm exception này với `IllegalStateException` do `getRequiredProperty` missing.

### 10.4. Thời điểm property source xuất hiện

`@PropertySource` thường được xử lý khi Spring đọc configuration classes. Vì thế nếu một cấu hình phải tồn tại **rất sớm** (trước khi `@Configuration` được phân tích), đừng mặc định annotation đã được áp dụng. Có thể bổ sung `PropertySource` vào `ConfigurableEnvironment` **trước `refresh()`**, hoặc dùng bootstrap hook thích hợp.

## 11. Profiles, runtime changes và source precedence

`Environment` quản lý cả profiles lẫn properties nhưng chúng phục vụ mục đích khác:

~~~java
boolean production =
        environment.acceptsProfiles(
                org.springframework.core.env.Profiles.of("prod"));
~~~

Đây là snippet kiểm tra profile, không phải kiểm tra presence của key.

Nếu cấu hình trong map thay đổi runtime, những lần gọi `environment.getProperty(...)` về sau có thể thấy giá trị mới. Nhưng một bean đã nhận String từ `@Bean` hoặc constructor thường vẫn giữ **giá trị cũ** trong field; Spring Core không tự refresh tất cả singleton beans. Dynamic configuration cần design riêng về consistency, thread safety, refresh/replacement và rollback.

Với `PropertySources` từ nhiều nguồn, key có thể bị override bởi source ở đầu danh sách. Đừng debug bằng cách chỉ mở một file rồi kết luận đó là giá trị đang được dùng. Cần xem source ordering thật trong đúng ApplicationContext.

## 12. Bảo mật và quan sát cấu hình

- Không log toàn bộ `System.getenv()` hoặc full property values khi chúng có thể chứa tokens/passwords.
- Thông báo lỗi required key nên nêu **tên key**, không in secret value.
- Validate URL/port/username/format tại startup; tránh phát hiện khi request đầu tiên chạy.
- Dùng secret-management mechanism phù hợp cho thông tin nhạy cảm; `Environment` là resolver, không phải secret vault.
- Nếu cần debug precedence, quan sát tên các `PropertySource` theo thứ tự; Spring `getProperty` không có overload chuẩn trả kèm “nguồn xuất xứ”.
- Không đọc key names từ untrusted user input nếu điều đó cho phép truy cập secrets thông qua một API của ứng dụng.
- Một default value thuận tiện nhưng quá rộng có thể che lỗi deploy; required API phù hợp hơn với dữ liệu sống còn.

## 13. Lỗi phổ biến

| Triệu chứng | Nguyên nhân | Khắc phục |
| --- | --- | --- |
| `getProperty` trả null | Key không có ở các sources đang đăng ký | Kiểm tra key, nguồn và thời điểm thêm |
| `containsProperty` true nhưng cấu hình vô dụng | Key hiện diện dưới dạng `""`/blank/sai format | Đọc value và validate |
| `getProperty(key, fallback)` không dùng fallback | Key có giá trị rỗng nhưng khác null | Áp policy blank cụ thể |
| `getRequiredProperty` ném IllegalStateException | Không phân giải được key | Bổ sung required config hoặc đổi requirement có chủ ý |
| Typed lookup lỗi | Text không chuyển thành target type | Sửa format hoặc converter |
| Feature `false` vẫn bật | Dùng `containsProperty` như cờ boolean | Dùng `Boolean.class` và so sánh true |
| Giá trị khác file dự kiến | Một PropertySource khác ưu tiên cao hơn | Kiểm tra `addFirst`/`addLast` và thứ tự hiện tại |
| `application.properties` không được nạp trong Java Core | Nhầm cơ chế Spring Boot auto-config | Dùng `@PropertySource`/programmatic registration |
| `@PropertySource` không thấy khi rất sớm | Configuration chưa được xử lý | Thêm source trước refresh hoặc bootstrap |
| `@Value` báo placeholder chưa resolve | Thiếu key hoặc syntax/placeholder processor sai | Kiểm tra source, key và default |
| Port `0` qua required lookup | Required không validate range | Thêm domain validation |
| `setRequiredProperties` nhưng không phát hiện thiếu | Validation chưa chạy | Gọi `validateRequiredProperties` đúng giai đoạn |
| Thay file cấu hình nhưng singleton không đổi | Bean đã cache setting | Có refresh strategy rõ hoặc restart |
| Đọc sai profile từ `containsProperty` | Presence property khác active profile | Dùng `acceptsProfiles`/`getActiveProfiles` |

### Các bước chẩn đoán

1. Đã dùng đúng `Environment` của đúng ApplicationContext chưa?
2. Đúng key, chính tả và tên source chưa?
3. PropertySource cần thiết đã được đăng ký **trước khi đọc** chưa?
4. Source nào ưu tiên cao nhất cung cấp value khác null?
5. Value là missing, blank, whitespace hay sai format?
6. Business cần optional, default hay required?
7. Conversion sang target type có hợp lệ không?
8. Startup đã validate settings chưa hay lỗi phát hiện muộn?
9. Có hệ thống profiles/Boot ConfigData/servlet context ảnh hưởng sources không?
10. Chẩn đoán có làm lộ secret không?

## 14. Kiểm thử các đường quan trọng

### Unit test với StandardEnvironment

~~~java
StandardEnvironment environment = new StandardEnvironment();
environment.getPropertySources().addFirst(
        new MapPropertySource(
                "test",
                Map.<String, Object>of(
                        "demo.name", "UnitTest",
                        "demo.port", "8090",
                        "demo.empty", "")));

if (!"UnitTest".equals(environment.getProperty("demo.name"))) {
    throw new AssertionError("Wrong name");
}
if (!Integer.valueOf(8090).equals(
        environment.getProperty("demo.port", Integer.class))) {
    throw new AssertionError("Wrong typed lookup");
}
if (!environment.containsProperty("demo.empty")) {
    throw new AssertionError("Empty string counts as present");
}
~~~

Đây là **snippet unit test**; cần imports `StandardEnvironment`, `MapPropertySource` và `Map`.

### Test required key thiếu

~~~java
try {
    environment.getRequiredProperty("demo.unused.unique.key");
    throw new AssertionError("Missing key must fail");
} catch (IllegalStateException expected) {
    // Đúng hợp đồng nếu key thực sự không có trong source nào.
}
~~~

Đây là test snippet. Khi viết test đáng tin tuyệt đối, dựng PropertyResolver chỉ với test sources hoặc dùng key test chắc chắn không xuất hiện trong JVM/OS environment. Không đặt name phổ biến như `PATH` làm missing key.

### Integration test với context

Khởi tạo `AnnotationConfigApplicationContext`, thêm sources **trước `refresh()`**, đăng ký một `@Configuration` có settings bean như mục 8. Kiểm tra:

1. Required name và port được inject đúng.
2. Override source thắng default source.
3. Typed String-to-Integer conversion đúng.
4. Missing required key làm bean creation fail.
5. Malformed port làm conversion fail.
6. Port ngoài range làm domain validation fail.
7. Blank string vẫn được resolver nhận diện nhưng bị business validation chặn.
8. Bean dùng immutable record không tự cập nhật nếu PropertySource bị chỉnh sau khi settings được tạo.
9. Context được đóng bằng `try-with-resources` để tránh leak.

## 15. Checklist lựa chọn API

- [ ] Dùng `getProperty(key)` khi null là một kết quả hợp lệ.
- [ ] Dùng `getProperty(key, fallback)` khi có default rõ ràng.
- [ ] Dùng typed overload khi property là số/boolean và xử lý conversion errors.
- [ ] Không dùng `containsProperty` làm boolean feature flag.
- [ ] Dùng `getRequiredProperty` để fail fast với cấu hình bắt buộc.
- [ ] Validate non-blank/range/format riêng, kể cả khi key required.
- [ ] Với nhiều required keys, xem xét `setRequiredProperties` + `validateRequiredProperties`.
- [ ] Thêm PropertySources ở đúng lifecycle stage; chọn precedence tường minh.
- [ ] Inject `Environment` thay vì toàn `ApplicationContext` nếu chỉ cần đọc config.
- [ ] Với settings ổn định, tạo typed immutable settings object.
- [ ] Đừng nhầm profiles với presence của `spring.profiles.active`.
- [ ] Không kỳ vọng Spring Core tự tải file theo toàn bộ convention Spring Boot.
- [ ] Không log secrets/raw property maps.
- [ ] Test missing, empty, malformed, precedence và runtime refresh policy.

## 16. Câu hỏi tự kiểm tra

**1. Nếu key không tồn tại, `getProperty(key)` trả gì?**  
Trả `null`.

**2. `getProperty(key, "local")` khác `getRequiredProperty(key)` ra sao?**  
Một bên dùng default nếu thiếu; bên kia ném `IllegalStateException`.

**3. Key tồn tại với chuỗi rỗng, `containsProperty` trả false đúng không?**  
Không. Chuỗi rỗng là khác null, nên resolver có thể coi là hiện diện.

**4. `getRequiredProperty` có bảo đảm non-blank hay range hợp lệ không?**  
Không. Nó kiểm tra key có giá trị khác null; business validation vẫn cần.

**5. `feature.enabled=false` có khiến `containsProperty` trả false không?**  
Không. Presence khác giá trị Boolean. Dùng `getProperty(key, Boolean.class)`.

**6. `getProperty(key, Integer.class, 8080)` có dùng fallback nếu nguồn chứa `"abc"` không?**  
Không nên kỳ vọng. Fallback dùng khi missing; conversion có thể ném lỗi với chuỗi sai.

**7. Hai sources có cùng key, source nào thắng?**  
Source ưu tiên cao hơn trong `MutablePropertySources`, nếu nó cung cấp value khác null.

**8. `StandardEnvironment` mặc định ưu tiên JVM system property hay OS environment variable?**  
JVM system property đứng trước theo cấu hình mặc định trong ứng dụng Java thuần.

**9. `setRequiredProperties` có phải là `getRequiredProperty` trên nhiều key không?**  
Không. Một bên khai báo keys và cần validation step; bên kia đọc một key ngay tại call.

**10. Plain Spring Core có tự nạp `application.properties` như Spring Boot không?**  
Không. Cần đăng ký property sources phù hợp.

**11. Bean đã đọc Environment một lần, property source đổi thì field có tự refresh không?**  
Không. Field đã gán không tự thay đổi; cần cơ chế refresh/rebuild explicit.

**12. Vì sao cần tránh log giá trị required secret khi báo lỗi?**  
Vì exception/log có thể lộ token hoặc password. Chỉ nêu key và quy tắc validation thất bại.

## Tài liệu chính thức

- [Spring Framework Reference — Environment Abstraction](https://docs.spring.io/spring-framework/reference/core/beans/environment.html).
- [Spring Framework Javadoc — PropertyResolver](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/core/env/PropertyResolver.html), hợp đồng `containsProperty`, `getProperty` và `getRequiredProperty`.
- [Spring Framework Javadoc — Environment](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/core/env/Environment.html).
- [Spring Framework Javadoc — ConfigurableEnvironment](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/core/env/ConfigurableEnvironment.html).
- [Spring Framework Javadoc — ConfigurablePropertyResolver](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/core/env/ConfigurablePropertyResolver.html).
- [Spring Framework Javadoc — PropertySourcesPropertyResolver](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/core/env/PropertySourcesPropertyResolver.html).
- [Spring Framework Javadoc — @PropertySource](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/annotation/PropertySource.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-environment-005">Thêm, thay thế và sắp xếp MutablePropertySources</a>

<details>
<summary>Xem chi tiết</summary>

# Thêm, thay thế và sắp xếp MutablePropertySources trong Spring Core Container

## 1. Mục tiêu

Trong Spring, cấu hình có thể đến từ JVM system properties, biến môi trường, file `.properties`, một `Map` do ứng dụng tạo hoặc hạ tầng tích hợp khác. Khi cùng một khóa xuất hiện ở nhiều nơi, **thứ tự các `PropertySource`** quyết định giá trị thắng. `MutablePropertySources` là collection có thứ tự, hỗ trợ thêm, thay thế, chèn, di chuyển và xóa nguồn cấu hình một cách tường minh.

Sau bài học, người đọc có thể:

- Giải thích được vì sao source ở **đầu danh sách có precedence cao hơn**.
- Dùng `addFirst`, `addLast`, `addBefore`, `addAfter`, `replace` và `remove` đúng ngữ nghĩa.
- Phân biệt **tên source** với **tên property key**, và thay source với sửa một entry trong source.
- Thiết kế override/fallback có thể kiểm thử và không phụ thuộc thứ tự đăng ký tình cờ.
- Cấu hình `ConfigurableEnvironment` trước khi `ApplicationContext.refresh()` để các bean nhận giá trị phù hợp.
- Chẩn đoán xung đột và giới hạn: `@Value` không tự động refresh khi source đổi, ngữ nghĩa profile khác property, Spring Boot có thứ tự nguồn riêng.

Đây là kiến thức **Spring Framework Core Container**; các đoạn API tập trung vào Spring Framework 6.2.18 và Java 17.

## 2. Khái niệm và quan hệ

| Kiểu | Package `org.springframework.core.env` | Nhiệm vụ |
| --- | --- | --- |
| `Environment` | Interface | Đọc property và kiểm tra profile đang hiệu lực |
| `ConfigurableEnvironment` | Interface | Cho phép chỉnh profiles và property-source chain |
| `PropertySource<T>` | Abstract class | Adapter của một nguồn key/value có tên |
| `PropertySources` | Interface | Tập hợp sources có thể duyệt/tra theo tên |
| `MutablePropertySources` | Class | Collection hỗ trợ thêm, thay, xóa, chèn, đổi precedence |
| `PropertyResolver` | Interface | Phân giải key qua các sources, chuyển kiểu và placeholders |
| `MapPropertySource` | Class | Bọc `Map<String,Object>` |
| `PropertiesPropertySource` | Class | Bọc `java.util.Properties` |
| `StandardEnvironment` | Class | Environment cho ứng dụng Java không phải web |

Sơ đồ:

```text
ApplicationContext
    |
    +-- ConfigurableEnvironment
             |
             +-- MutablePropertySources
             |     [0] operatorOverrides
             |     [1] systemProperties
             |     [2] systemEnvironment
             |     [3] appDefaults
             |
             +-- PropertyResolver
                    getProperty("service.timeout")
                    -> tìm [0], [1], [2], [3]
                    -> giá trị non-null đầu tiên thắng
```

Một `PropertySource` mang **tên nguồn** (ví dụ `"operatorOverrides"`), và bên trong nó chứa **nhiều property keys** (ví dụ `"service.timeout"`). `sources.get("operatorOverrides")` lấy source; `env.getProperty("service.timeout")` tìm key xuyên qua các nguồn. Hai dạng get này khác bản chất.

## 3. Thứ tự chính là độ ưu tiên

Giả sử danh sách có ba nguồn:

```text
[0] operator   service.mode=fast
[1] appConfig  service.mode=normal, service.timeout=2000
[2] defaults   service.mode=safe, service.timeout=5000
```

Khi đó:

- `env.getProperty("service.mode")` trả `"fast"` từ `operator`.
- `env.getProperty("service.timeout")` trả `"2000"` từ `appConfig`, vì operator không có khóa đó.
- Xóa operator sẽ làm mode đổi thành `"normal"`.
- Xóa cả appConfig sẽ làm mode đổi thành `"safe"` và timeout trở thành `"5000"`.

Spring **không gộp** những giá trị trùng key. Resolver duyệt sources theo thứ tự, sử dụng kết quả non-null đầu tiên cho từng key. Nếu một nguồn trả `null`, resolver có thể tiếp tục sang nguồn sau. Vì vậy không nên dùng `null` trong một source trên để “chặn” một key bên dưới như tombstone nếu không có resolver tùy chỉnh.

Thứ tự này **không** đồng nghĩa với thứ tự khởi tạo singleton, thứ tự các phương thức `@Bean` hoặc thứ tự alphabet của tên source.

## 4. StandardEnvironment mặc định

Một `StandardEnvironment` mới thường bắt đầu với:

```text
systemProperties  (JVM -Dkey=value)
systemEnvironment (OS environment variables)
```

`systemProperties` có precedence cao hơn `systemEnvironment`. Hằng số chuẩn:

```java
StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME
StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME
```

Đây là hai biểu thức Java minh họa, nhận `"systemProperties"` và `"systemEnvironment"` tương ứng. Dùng hằng số khi thao tác quanh nguồn mặc định để tránh typo.

Cấu hình cơ bản:

```java
StandardEnvironment env = new StandardEnvironment();
MutablePropertySources sources = env.getPropertySources();
```

Mảnh code cần import `StandardEnvironment` và `MutablePropertySources`. `ConfigurableEnvironment` cũng expose `getPropertySources()`, giúp ứng dụng quản lý thứ tự trong lúc bootstrap.

**Đừng suy rộng:** `StandardServletEnvironment` có thể bổ sung servlet/JNDI; Spring Boot đăng ký thêm command-line/config-data/test sources và có thứ tự ưu tiên được tài liệu Boot quy định. Nếu có source custom đứng đầu, nguồn JVM system properties không còn mặc định thắng mọi key.

## 5. API quan trọng

| API | Tác động |
| --- | --- |
| `addFirst(PropertySource<?> source)` | Đặt source ở đầu, precedence cao nhất |
| `addLast(PropertySource<?> source)` | Đặt source ở cuối, precedence thấp nhất |
| `addBefore(String relativeName, PropertySource<?> source)` | Chèn trước source mốc, ưu tiên cao hơn mốc |
| `addAfter(String relativeName, PropertySource<?> source)` | Chèn sau source mốc, ưu tiên thấp hơn mốc |
| `replace(String name, PropertySource<?> replacement)` | Thay source mang tên `name` ở vị trí hiện tại |
| `remove(String name)` | Xóa toàn source; trả object cũ hoặc `null` |
| `contains(String name)` | Kiểm tra **tên source** tồn tại |
| `get(String name)` | Lấy source theo **tên source** hoặc `null` |
| `precedenceOf(PropertySource<?> source)` | Chỉ số precedence (0 ở đầu), `-1` nếu không tìm thấy |
| `size()`, `iterator()` | Đếm và duyệt sources theo thứ tự |

**Tên nguồn cần duy nhất và có ý nghĩa.** Nếu nhiều modules cùng đăng ký tên `"config"`, cấu trúc sẽ khó dự đoán. Tốt hơn dùng tên như `"operatorOverrides"`, `"regionalSettings"` và `"applicationDefaults"`.

## 6. addFirst: override ở đầu danh sách

```java
MapPropertySource operator = new MapPropertySource(
        "operatorOverrides",
        Map.<String, Object>of(
                "service.mode", "fast",
                "service.timeout", "1500"));

sources.addFirst(operator);
```

Đây là đoạn minh họa, cần imports `java.util.Map`, `MapPropertySource` và biến `sources` là `MutablePropertySources`. Sau đó operator được hỏi trước những nguồn đang có.

Nếu gọi:

```java
sources.addFirst(sourceA);
sources.addFirst(sourceB);
```

với hai nguồn có tên khác nhau, **B đứng trước A** vì lời gọi thứ hai lại đặt ở đầu. Thứ tự lời gọi và thứ tự precedence phải được mô tả rõ trong bootstrap code.

`addFirst` phù hợp cho override được chủ động cho phép, ví dụ một bản cấu hình kiểm thử hoặc operator policy. Đừng đặt mọi nguồn ở đầu chỉ vì dễ viết: source thêm sau có thể vô tình phủ quyết các nguồn bạn muốn ưu tiên cao.

## 7. addLast: fallback ở cuối

```java
sources.addLast(new MapPropertySource(
        "applicationDefaults",
        Map.<String, Object>of(
                "service.mode", "safe",
                "service.timeout", "5000")));
```

Nếu source khác cung cấp mode, defaults không thắng key đó. Nếu tất cả sources cao hơn thiếu timeout, fallback sẽ được dùng. `addLast` thường phù hợp với defaults thư viện và giá trị dự phòng an toàn.

Lưu ý: `MapPropertySource` nhận `Map<String,Object>`. Cách viết `Map.<String,Object>of(...)` giúp Java inference đúng khi có chỗ khai báo Map trung gian; không nhầm `Map<String,String>` với `Map<String,Object>` vì generic Java bất biến.

## 8. addBefore và addAfter: chèn quanh mốc đã biết

Ví dụ chèn override trước JVM system properties của `StandardEnvironment`:

```java
sources.addBefore(
        StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
        new MapPropertySource(
                "operatorOverrides",
                Map.<String, Object>of("service.mode", "fast")));
```

Thứ tự dự kiến trên StandardEnvironment chưa bị custom khác làm thay đổi:

```text
operatorOverrides > systemProperties > systemEnvironment
```

Chèn source ngay sau JVM system properties:

```java
sources.addAfter(
        StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
        new MapPropertySource(
                "regionalSettings",
                Map.<String, Object>of("service.region", "apac")));
```

Kết quả:

```text
operatorOverrides > systemProperties > regionalSettings
                  > systemEnvironment
```

**Tên mốc phải tồn tại.** `addBefore("missing", source)` và `addAfter("missing", source)` không có ý nghĩa “không tìm được thì tự addLast”; chúng có thể ném lỗi. Kiểm tra `sources.contains(relativeName)` khi tên mốc là optional. Không chèn source tương đối với **chính nó**; dùng remove và add khi muốn move.

## 9. replace: thay source tại vị trí hiện tại

Giả sử có nguồn `tenantSettings` ở giữa:

```text
operatorOverrides > tenantSettings > applicationDefaults
```

Thay toàn bộ nội dung source:

```java
sources.replace(
        "tenantSettings",
        new MapPropertySource(
                "tenantSettings",
                Map.<String, Object>of(
                        "service.mode", "restricted",
                        "service.timeout", "900")));
```

Thứ tự **không đổi**:

```text
operatorOverrides > tenantSettings (object mới)
                  > applicationDefaults
```

`replace` thay một entry `PropertySource`, **không tự merge từng entry Map**. Nếu source cũ chứa key `service.url` nhưng map thay thế không có key đó, giá trị source cũ mất hiệu lực; resolver có thể lấy `service.url` từ source thấp hơn.

Để cập nhật một key mà vẫn giữ các key khác, tự tạo replacement map đã gộp theo đúng chính sách ứng dụng; hoặc nếu chủ động thiết kế backing map mutable, thay trong map đó với bảo đảm thread safety.

`replace` yêu cầu source name đã tồn tại. Không gọi rồi kỳ vọng nó hoạt động như upsert. Một policy upsert minh họa:

```java
if (sources.contains("tenantSettings")) {
    sources.replace("tenantSettings", replacement);
} else {
    sources.addFirst(replacement);
}
```

Đoạn này giả định `replacement` có tên `"tenantSettings"` và kiểu `PropertySource<?>`. Nhánh `addFirst` là **quyết định của ứng dụng** chứ không phải ngữ nghĩa mặc định của `replace`.

Nên dùng replacement **cùng tên** để tên source tiếp tục là identity ổn định trong log và các phép `get(name)`.

## 10. remove: xóa một nguồn, làm lộ fallback

```java
PropertySource<?> removed = sources.remove("operatorOverrides");
```

Nếu có nguồn đó, Java biến `removed` giữ instance đã xóa; nếu không có thì nhận `null`. Xóa một source **không** xóa các keys trùng trong sources còn lại.

Ví dụ:

```text
Trước: operatorOverrides(mode=fast) > defaults(mode=safe)
Sau remove("operatorOverrides"): defaults(mode=safe)
```

Kết quả `env.getProperty("mode")` có thể đổi từ fast sang safe. Đây là fallback thông thường, không phải bug.

`remove` **xóa cả PropertySource**. Nếu chỉ muốn bỏ một key trong source, dùng replacement map đã lược key hoặc API backing map được quản lý an toàn. Đừng xóa `systemEnvironment` hoặc `systemProperties` trên production context chỉ để xử lý một key, vì việc đó thay policy đọc cấu hình của nhiều bean/thư viện.

## 11. Di chuyển nguồn có sẵn mà không thay content

Muốn đưa một source `"tenantSettings"` lên đầu:

```java
PropertySource<?> moved = sources.remove("tenantSettings");
if (moved != null) {
    sources.addFirst(moved);
}
```

Đẩy xuống cuối:

```java
PropertySource<?> moved = sources.remove("tenantSettings");
if (moved != null) {
    sources.addLast(moved);
}
```

Chèn lại ngay sau một mốc hợp lệ:

```java
if (sources.contains("systemProperties")) {
    PropertySource<?> moved = sources.remove("tenantSettings");
    if (moved != null) {
        sources.addAfter("systemProperties", moved);
    }
}
```

Những đoạn code trên là mảnh minh họa, giả định các source names tương ứng. Cách `remove + add` giữ nguyên **instance PropertySource** và làm rõ rằng thao tác được thực hiện để **đổi vị trí**, còn `replace` dùng để **thay nội dung của một source tại slot cũ**.

## 12. Ví dụ Java 17 chạy được với Spring Framework 6.2.18

Ví dụ tập trung vào thao tác cấu trúc, không cần Spring Boot. Chương trình tạo một `StandardEnvironment` độc lập, loại hai source hệ thống khỏi **chính environment cục bộ này** để output không phụ thuộc máy, rồi lần lượt thêm, chèn, chuyển, thay thế, xóa.

### Cấu trúc dự án

```text
property-sources-order-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/PropertySourcesOrderDemo.java
```

### settings.gradle

```groovy
rootProject.name = 'property-sources-order-demo'
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
    implementation 'org.springframework:spring-core:6.2.18'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'PropertySourcesOrderDemo'
}
```

### src/main/java/PropertySourcesOrderDemo.java

```java
import java.util.Map;
import java.util.StringJoiner;

import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

public class PropertySourcesOrderDemo {

    private static MapPropertySource source(
            String name, String mode, String port) {
        return new MapPropertySource(
                name,
                Map.<String, Object>of(
                        "demo.mode", mode,
                        "demo.port", port));
    }

    private static void show(int stage, StandardEnvironment env) {
        StringJoiner names = new StringJoiner(" > ");
        for (PropertySource<?> ps : env.getPropertySources()) {
            names.add(ps.getName());
        }

        String mode = env.getProperty("demo.mode");
        Integer port = env.getProperty(
                "demo.port", Integer.class, -1);

        System.out.printf(
                "%d [%s] mode=%s port=%d%n",
                stage, names, mode, port);
    }

    public static void main(String[] args) {
        StandardEnvironment env = new StandardEnvironment();
        MutablePropertySources sources = env.getPropertySources();

        // Chỉ xóa adapters khỏi Environment của ví dụ.
        // Không thay System.getProperties() hoặc OS variables.
        sources.remove(
                StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.remove(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);

        // 1. Đặt defaults ở cuối.
        sources.addLast(source("defaults", "base", "7000"));
        show(1, env);

        // 2. Đặt runtime ở đầu.
        sources.addFirst(source("runtime", "runtime", "8000"));
        show(2, env);

        // 3. Regional đứng ngay sau runtime.
        sources.addAfter("runtime",
                new MapPropertySource(
                        "regional",
                        Map.<String, Object>of(
                                "demo.mode", "regional")));
        show(3, env);

        // 4. Emergency đứng ngay trước regional.
        sources.addBefore("regional",
                new MapPropertySource(
                        "emergency",
                        Map.<String, Object>of(
                                "demo.mode", "emergency")));
        show(4, env);

        // 5. Chuyển emergency lên đầu.
        PropertySource<?> moved = sources.remove("emergency");
        if (moved == null) {
            throw new IllegalStateException("Missing emergency");
        }
        sources.addFirst(moved);
        show(5, env);

        // 6. Replace runtime, vẫn giữ cùng slot.
        sources.replace("runtime",
                source("runtime", "runtime-v2", "9000"));
        show(6, env);

        // 7. Bỏ emergency để lộ runtime.
        sources.remove("emergency");
        show(7, env);

        // 8. Bỏ runtime, fallback qua regional/defaults.
        sources.remove("runtime");
        show(8, env);
    }
}
```

### Lệnh chạy

JDK 17+ và Gradle 8+:

```bash
gradle run --quiet
# Hoặc ./gradlew run --quiet khi đã tạo Gradle Wrapper.
```

### Output của System.out

```text
1 [defaults] mode=base port=7000
2 [runtime > defaults] mode=runtime port=8000
3 [runtime > regional > defaults] mode=runtime port=8000
4 [runtime > emergency > regional > defaults] mode=runtime port=8000
5 [emergency > runtime > regional > defaults] mode=emergency port=8000
6 [emergency > runtime > regional > defaults] mode=emergency port=9000
7 [runtime > regional > defaults] mode=runtime-v2 port=9000
8 [regional > defaults] mode=regional port=7000
```

Đây là những dòng do code ví dụ in qua `System.out`; không bao gồm log thư viện do môi trường Gradle hoặc logging framework có thể phát sinh.

### Đọc từng bước

1. `defaults` là nguồn duy nhất, nên cả mode và port lấy từ defaults.
2. `runtime` được addFirst và chứa cả hai keys, thắng defaults.
3. `regional` được chèn sau runtime, có mode nhưng không thể thắng runtime.
4. `emergency` được chèn giữa runtime và regional; runtime vẫn đứng trước.
5. Di chuyển emergency lên đầu khiến mode thành `emergency`. Emergency không có port nên port rơi xuống runtime `8000`.
6. `replace(runtime)` đổi port thành `9000` ở slot thứ hai. Emergency vẫn thắng mode.
7. Xóa emergency khiến `runtime-v2` thắng mode; port vẫn `9000`.
8. Xóa runtime khiến regional thắng mode, còn port rơi xuống defaults `7000`.

**Bài học mấu chốt:** thứ tự không chỉ chọn một nguồn cấu hình duy nhất cho toàn bộ app; **mỗi key được resolve riêng** từ nguồn đầu tiên có giá trị non-null.

### Vì sao dependency chỉ cần spring-core?

`StandardEnvironment`, `MutablePropertySources` và `MapPropertySource` đều thuộc `spring-core`. Ví dụ không cần `ApplicationContext`. Để kết hợp thực tế với Spring IoC bean graph, thêm `spring-context` và thực hiện thao tác source trong bootstrap trước khi context refresh.

## 13. Kiểm tra source nào đang thắng một key

Để debug một key cụ thể:

```java
String key = "service.timeout";
for (PropertySource<?> source : sources) {
    Object value = source.getProperty(key);
    if (value != null) {
        System.out.println("winningSource=" + source.getName());
        break;
    }
}
```

Đây là **mảnh code minh họa**, giả định `sources` đã khai báo và có imports. Trong production, tránh in giá trị thật của password/token/API secret. Ghi tên nguồn hoặc boolean property tồn tại thường đủ để xác định precedence.

Có thể xem thứ tự thực tế:

```java
for (PropertySource<?> source : sources) {
    System.out.println(source.getName());
}
```

Không mặc định mọi loại PropertySource đều enumerable. Tra `getProperty(key)` cho một key cụ thể thường dễ hơn quét toàn bộ keys, nhưng một custom source có thể phải thực hiện I/O để trả value; hãy cẩn trọng khi debug nhiều lần.

### So sánh các API trông giống nhau

| Lệnh | Trả lời câu hỏi |
| --- | --- |
| `sources.get("operator")` | Source tên operator có tồn tại không, lấy object nào? |
| `sources.contains("operator")` | Collection có source tên operator không? |
| `env.getProperty("service.timeout")` | Resolver chọn value nào qua toàn chuỗi? |
| `env.containsProperty("service.timeout")` | Key này resolve được không? |
| `sources.precedenceOf(source)` | Source này nằm ở vị trí nào trong thứ tự ưu tiên? |

Một lỗi thường gặp là dùng tên source ở `getProperty` hoặc dùng tên property key ở `sources.get`.

## 14. Thay toàn source khác với sửa backing map

### Thêm một nguồn mới

```java
sources.addFirst(new MapPropertySource(
        "operator",
        Map.<String, Object>of("feature.enabled", "true")));
```

Thay đổi cấu trúc collection, có thể đổi precedence.

### Replace toàn nguồn

```java
sources.replace("operator", new MapPropertySource(
        "operator",
        Map.<String, Object>of("feature.enabled", "false")));
```

Giữ vị trí nhưng đổi object source. Nếu source cũ có thêm keys không được copy sang source mới, các keys đó mất khỏi nguồn này.

### Chỉnh backing map

```java
Map<String, Object> map = new java.util.HashMap<>();
map.put("feature.enabled", "true");

sources.addFirst(new MapPropertySource("operator", map));
map.put("feature.enabled", "false");
```

`MapPropertySource` dùng map được truyền vào, nên lookup sau có thể thấy giá trị mới. Nhưng:

- Thay đổi trực tiếp một mutable map dùng chung cần concurrency policy rõ ràng.
- Không để mọi business component tùy ý sửa environment toàn cục.
- Không mặc định rằng những bean đã khởi tạo và nhận `@Value` sẽ tự cập nhật field.
- Khi cần live refresh/rebind, thiết kế cơ chế hỗ trợ riêng thay vì coi `MutablePropertySources` là một “bean refresh engine”.

## 15. Mutation trước hay sau refresh()?

Nếu Spring bean dùng `@Value` hoặc `Environment.getProperty` trong constructor/init method, thứ tự sources **ở thời điểm resolution** ảnh hưởng giá trị bean nhận.

Bootstrap đề xuất:

```java
AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext();

ConfigurableEnvironment env = context.getEnvironment();
env.getPropertySources().addFirst(customOverrides);

context.register(AppConfig.class);
context.refresh();
```

Đây là mảnh code minh họa, cần imports, `customOverrides` và `AppConfig` hợp lệ; caller cần đóng context sau sử dụng. Cách làm này đảm bảo các definitions/beans được xử lý với precedence đã thiết lập trước.

Nếu sửa sources **sau refresh**, lệnh `env.getProperty(key)` ở một chỗ được gọi **về sau** có thể thấy giá trị mới. Nhưng một singleton đã đọc cấu hình trong constructor, field `@Value` hoặc init callback sẽ **không tự bị tạo lại**; values đã capture thường giữ nguyên. Cũng không tự đánh giá lại `@Profile` trên các bean definitions.

### Khi app sử dụng PropertySourcesPlaceholderConfigurer

`PropertySourcesPlaceholderConfigurer` có thể trộn property sources trong Environment với local properties của chính configurer. Khi debug placeholder wiring, cần kiểm tra cả precedence của configurer (như chính sách local override) chứ không chỉ `env.getPropertySources()`. Đừng gọi `BeanFactory.getBean()` sớm trong một `BeanFactoryPostProcessor` chỉ để kiểm tra property; hãy đọc Environment/metadata khi phù hợp.

## 16. @PropertySource, @Value và files

`@PropertySource` giúp Spring Framework đưa file resource vào Environment khi xử lý class `@Configuration`:

```java
@Configuration(proxyBeanMethods = false)
@PropertySource("classpath:app.properties")
class AppConfig {
}
```

Đây là mảnh cấu hình, cần imports và file thật trên classpath. `@PropertySource` **không** đồng nghĩa Spring Core sẽ tự động load mọi `application.properties` hoặc `application.yml` như Spring Boot.

Mảnh inject placeholder:

```java
@Component
class ClientSettings {
    @Value("${client.timeout:2000}")
    private int timeout;
}
```

Đây là mảnh minh họa (cần imports). Sau khi xử lý dấu thay thế khi tạo tài liệu, String Java là `"${client.timeout:2000}"` với ký tự dollar và ngoặc nhọn chuẩn của Spring, **không có dấu backslash trước dollar**.

Nếu cần fail-fast khi key thiếu, dùng `Environment.getRequiredProperty` hoặc cấu hình strict placeholder processing đúng cách. `@Value` placeholder có thể được resolve lenient trong một số Spring Core configurations, nên test ứng dụng khi key bắt buộc không có.

## 17. Profiles cũng có thể bị ảnh hưởng gián tiếp

`Environment` có hai vai trò:

- **Properties**: đọc values từ `PropertySources`.
- **Profiles**: chọn các bean definitions được phép tham gia tùy profile condition.

Các key `spring.profiles.active` hoặc `spring.profiles.default` có thể nằm trong sources. Nếu thay precedence của những keys này **trước khi Spring đánh giá cấu hình `@Profile`**, tập bean được đăng ký có thể khác.

Nhưng thay source sau `refresh()` không tự làm lại bean graph. Vì vậy thiết lập profiles và properties trong bootstrap, trước registration/refresh theo vòng đời phù hợp.

Không nhầm profile name với property key: `@Profile("prod")` là selector logic, còn `service.timeout=2000` là dữ liệu. Trong Spring Core, bật `prod` không tự nạp một file `application-prod.properties` nếu bạn không cấu hình cơ chế nạp tương ứng. Spring Boot có config data profile-specific của riêng nó.

## 18. Spring Boot và precedence: giới hạn của ví dụ Core

Trong Spring Boot, Environment có thể bao gồm command-line args, config data, system properties, OS variables, test properties và các custom sources. `MutablePropertySources.addFirst(...)` thay đổi danh sách tại thời điểm gọi, nhưng các bước bootstrapping khác có thể tiếp tục bổ sung/sắp xếp sources sau đó.

Nếu chỉ cần operator override, nên ưu tiên cấu hình được Boot tài liệu hóa. Nếu viết infrastructure bằng `MutablePropertySources`, cần kiểm tra thứ tự cuối cùng **sau khi Boot hoàn tất bootstrap** ở đúng phiên bản, thay vì suy ra chỉ từ `StandardEnvironment`.

Tên source nội bộ của Boot có thể thay đổi qua phiên bản. Tránh hard-code tên source của implementation nếu không có contract public rõ ràng.

## 19. Những lỗi thực tế và checklist

| Hiện tượng | Vì sao xảy ra | Cách xử lý |
| --- | --- | --- |
| Value không đổi dù đã thêm source | Source cũ vẫn nằm trước | Dùng `addFirst` hoặc chèn vị trí phù hợp |
| `addBefore` ném lỗi | Source mốc không tồn tại | `contains(relativeName)` rồi chọn policy fallback |
| `replace` ném lỗi | Tên nguồn chưa tồn tại | Dùng kiểm tra trước, add nếu muốn upsert |
| `replace` xong mất vài keys | Replacement map chỉ có một phần keys | Tạo map đầy đủ hoặc policy merge tường minh |
| `remove` rồi value vẫn có | Source thấp hơn cũng chứa key | Đó là fallback, không phải xóa global key |
| Muốn xóa một key nhưng đã xóa cả source | Nhầm entry với PropertySource | Thay source/map thay vì xóa toàn source |
| JVM system property không thắng | Custom source có precedence cao hơn | Duyệt source chain hiện tại |
| @Value field không đổi theo runtime map | Bean đã resolve cấu hình trước đó | Dùng refresh/rebind có chủ đích |
| Test thay đổi theo CI/IDE | JVM/OS properties khác nhau | Cô lập `StandardEnvironment`/`MockEnvironment` |
| Secret bị lộ trong diagnostic | Print toàn bộ values | Chỉ log source name, redact secrets |
| Bị xung đột source name | Nhiều module dùng cùng tên | Đặt tên duy nhất, ownership rõ ràng |
| Source null không che fallback | Resolver tiếp tục tìm khi trả null | Dùng policy riêng nếu cần chặn |
| Thay active profile không đổi beans | Đã refresh context | Khởi tạo context mới đúng profiles |
| Thao tác thêm lại nhiều lần làm precedence bất ngờ | Bootstrap thiếu idempotence | Kiểm tra tên, dùng `replace` hoặc move theo chủ đích |

**Checklist:** trước khi triển khai, xác định ai được override ai, keys bắt buộc, tên sources, thứ tự mục tiêu, có source mốc hay không, mutation trước refresh chưa, có xử lý secret/concurrency không và có test mỗi key thắng đúng nguồn không.

## 20. Tài liệu chính thức

1. [Spring Framework 6.2.18 Javadoc — MutablePropertySources](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/core/env/MutablePropertySources.html): API add/replace/remove, precedence và lookup.
2. [Spring Framework 6.2.18 Javadoc — StandardEnvironment](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/core/env/StandardEnvironment.html): JVM system properties đứng trước OS environment trong môi trường Java standalone mặc định.
3. [Spring Framework 6.2.18 Javadoc — ConfigurableEnvironment](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/core/env/ConfigurableEnvironment.html): mutable chain, configuration và profiles.
4. [Spring Framework 6.2.18 Javadoc — MapPropertySource](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/core/env/MapPropertySource.html): Map-backed property sources.
5. [Spring Framework 6.2.18 Javadoc — PropertySourcesPropertyResolver](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/core/env/PropertySourcesPropertyResolver.html): giải quyết property theo nguồn và thứ tự.
6. [Spring Framework Reference — Environment Abstraction](https://docs.spring.io/spring-framework/reference/core/beans/environment.html): nguồn cấu hình, precedence và `@PropertySource`.
7. [Spring Framework 6.2.18 source — MutablePropertySources](https://github.com/spring-projects/spring-framework/blob/v6.2.18/spring-core/src/main/java/org/springframework/core/env/MutablePropertySources.java): hành vi cấu trúc của danh sách có thứ tự.

**Kết luận:** `MutablePropertySources` điều khiển **thứ tự tìm kiếm**. `addFirst/addLast` bổ sung ở đầu/cuối; `addBefore/addAfter` chèn quanh một source mốc; `replace` đổi nội dung một source tại vị trí cũ; `remove` khiến key có thể lấy fallback từ nguồn sau. Cấu hình sớm, đặt tên rõ, kiểm thử từng key và tránh nhầm việc thay source với tự động refresh beans.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-environment-006">Nạp tập tin properties bằng @PropertySource</a>

<details>
<summary>Xem chi tiết</summary>

# Nạp tập tin properties bằng @PropertySource trong Spring Core Container

## 1. Mục tiêu: đưa tệp cấu hình vào Spring Environment

Một ứng dụng thường không nên ghi cứng endpoint, timeout, chế độ triển khai hay số lần retry trong Java source. Spring Framework cung cấp **`Environment`** và tập **`PropertySource`** để đọc giá trị cấu hình theo thứ tự ưu tiên. Annotation **`@PropertySource`** cho phép chỉ định một hoặc nhiều **tệp Java `.properties`** để Spring nạp thành nguồn cấu hình khi xử lý **class `@Configuration`**.

Ví dụ tối giản:

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:application-settings.properties")
public class AppConfig {
}
```

Với tệp thực sự nằm tại `src/main/resources/application-settings.properties`:

```properties
service.name=inventory
service.retry-count=4
service.region=ap-southeast
```

Sau khi một `AnnotationConfigApplicationContext` xử lý và refresh `AppConfig`, các giá trị có thể đọc bằng:

```java
Environment env = context.getEnvironment();
String name = env.getProperty("service.name");
Integer retryCount = env.getProperty("service.retry-count", Integer.class);
```

Đây là **snippet tích hợp**, cần import `org.springframework.core.env.Environment`, biến `context` của Spring context đã refresh và tệp resources phù hợp.

**Tóm tắt:** `@PropertySource` **đăng ký một property source vào `Environment`**, để các phương thức `getProperty(...)`, placeholder resolution và cơ chế injection thích hợp có thể tra cứu giá trị. Annotation này **không** tự biến các key thành Java fields, không tự tạo một bean cấu hình từ mỗi dòng, và không phải cơ chế hot reload.

## 2. PropertySource, Environment và tệp .properties

Phân biệt ba tầng:

| Thành phần | Chức năng | Ví dụ |
|---|---|---|
| Tệp `.properties` | Chứa các cặp `key=value` | `service.port=8080` |
| `PropertySource` | Đại diện cho một nguồn giá trị có tên, thực hiện lookup theo key | Nguồn từ classpath resource |
| `Environment` | Tìm key qua nhiều property sources theo thứ tự ưu tiên, hỗ trợ conversion/profiles | `env.getProperty("service.port", Integer.class)` |
| `@PropertySource` | Khai báo resource cần đọc và thêm vào `Environment` khi parse cấu hình Java | `@PropertySource("classpath:settings.properties")` |
| `@Value` | Đánh dấu injection point để Spring resolve một value khi tạo bean | `@Value("${service.port:8080}")` |
| `@ConfigurationProperties` (Spring Boot) | Binding nhiều cấu hình thành typed object theo cơ chế Boot | Không phải tính năng tự động do `@PropertySource` cung cấp |

Đường xử lý:

```mermaid
flowchart TD
    A["Class @Configuration có @PropertySource"] --> B["ConfigurationClassPostProcessor phân tích metadata"]
    B --> C["ResourceLoader tìm tệp qua classpath:/file:/..."]
    C --> D["PropertySourceFactory đọc .properties"]
    D --> E["Đăng ký PropertySource vào Environment"]
    E --> F["PropertyResolver tra khóa theo thứ tự ưu tiên"]
    F --> G["Environment.getProperty / @Value / placeholder"]
```

Việc nạp tệp xảy ra trong quá trình **xử lý configuration metadata**, không phải mỗi lần `getProperty()` đều đọc lại toàn bộ file từ đĩa. Nếu thay nội dung file sau khi context đã refresh, không có bảo đảm `@PropertySource` tự nạp lại.

### 2.1. Cú pháp Java properties

Ví dụ:

```properties
# Đây là comment
app.name=My Spring App
app.timeout-seconds=15
app.enabled=true
app.base-url=https://service.invalid/api
app.description = Co the chua dau cach
```

Java properties cho phép các dòng cấu hình có cú pháp riêng cho dấu `=`, `:`, whitespace, escapes và dấu nối dòng. Một số ký tự như backslash có ý nghĩa escape; URL thường không có vấn đề với `https://...` vì `:` chỉ thuộc **phần value** sau key và dấu bằng.

Đối với các giá trị có dấu tiếng Việt, nên khai báo `encoding = "UTF-8"` rõ ràng thay vì phụ thuộc ngầm vào cơ chế đọc mặc định hoặc encoding của môi trường.

**Không sử dụng cú pháp YAML trong một `.properties` file**:

```yaml
app:
  timeout-seconds: 15
  enabled: true
```

Đây là YAML, không phải Java properties. Một `@PropertySource("classpath:application.yml")` thông thường **không tự parse YAML đúng như Spring Boot Config Data**. Nếu thật sự cần YAML trong Spring Core, phải có custom `PropertySourceFactory` hoặc công cụ đọc tương ứng; trong Spring Boot nên ưu tiên cơ chế Config Data.

## 3. Vai trò của @Configuration và ConfigurationClassPostProcessor

Cơ chế `@PropertySource` trong Java configuration được xử lý bởi hạ tầng như **`ConfigurationClassPostProcessor`** (CCPP). Nó phân tích class `@Configuration`, kiểm tra annotation, resolve resource location, tạo property source rồi thêm vào `Environment`.

Quy trình đơn giản:

```text
new AnnotationConfigApplicationContext(AppConfig.class)
       |
       v
Đăng ký AppConfig rồi refresh
       |
       v
ConfigurationClassPostProcessor
       |-- Phân tích @PropertySource
       |-- Nạp resources và thêm vào Environment
       |-- Đăng ký @Bean definitions nếu có
       v
BeanFactoryPostProcessors khác / đăng ký BeanPostProcessors
       |
       v
Tạo singleton beans, resolve @Value và @Bean parameters
       |
       v
Ứng dụng lấy bean hoặc gọi Environment.getProperty(...)
```

Các chi tiết hạ tầng có thể khác nhau theo loại context, nhưng trật tự học tập quan trọng là: **nguồn thuộc `@PropertySource` có mặt trước khi Spring tạo các bean thông thường cần giá trị cấu hình đó**, khi context được bootstrap đúng cách.

Lưu ý khi tạo context:

```java
// Cách 1: constructor này tự refresh.
var context = new AnnotationConfigApplicationContext(AppConfig.class);
```

```java
// Cách 2: tự đăng ký và refresh.
var context = new AnnotationConfigApplicationContext();
context.register(AppConfig.class);
context.refresh();
```

Cả hai là **snippet theo ngữ cảnh**, cần `AppConfig` hợp lệ. Không gọi `refresh()` lần thứ hai lên `AnnotationConfigApplicationContext` theo kỳ vọng “đọc lại `.properties`”: implementation kế thừa `GenericApplicationContext` thông thường chỉ hỗ trợ một lần refresh.

### 3.1. Tại sao dùng @PropertySource trong @Configuration?

Spring Reference mô tả `@PropertySource` như phần của **Java-based container configuration**. Đặt annotation trên class `@Configuration` được đăng ký đúng cách là cách rõ ràng nhất để bảo đảm parser chú ý đến nó.

Một class Java chỉ được `new` bên ngoài context, hoặc một `DefaultListableBeanFactory` tự dựng mà không đăng ký annotation configuration processors, **không tự nạp tệp** chỉ vì thấy annotation trong source.

### 3.2. Điều gì được nạp, điều gì không?

Sau khi `@PropertySource` chạy, có thêm **các key-value trong môi trường cấu hình**. Nó không tự:

- Tạo một object Java cho mỗi dòng properties.
- Thực hiện validation business cho endpoint hoặc timeout.
- Tạo `@Bean` definitions nếu class không khai báo bean tương ứng.
- Biến mọi property thành field trong class `@Configuration`.
- Khởi động lại singleton khi tệp đổi.
- Mở network connection chỉ vì value trông giống URL.
- Parse `.yaml` thành nested objects một cách tự động.

Muốn dùng value, gọi `Environment.getProperty`, inject bằng `@Value`, hoặc tổ chức typed configuration layer phù hợp.

## 4. Địa chỉ resource: classpath, file và tên đường dẫn

Annotation nhận location string theo cơ chế Spring `ResourceLoader`:

```java
@PropertySource("classpath:service.properties")
```

```java
@PropertySource("classpath:config/service.properties")
```

```java
@PropertySource("file:/opt/myapp/conf/service.properties")
```

Đây đều là **annotation snippets** cần đặt lên configuration class. Ý nghĩa:

| Location | Nơi tìm tệp | Trường hợp dùng |
|---|---|---|
| `classpath:service.properties` | Root của classpath | Tệp ở `src/main/resources/service.properties` |
| `classpath:config/service.properties` | Thư mục `config` trong classpath | Tệp ở `src/main/resources/config/service.properties` |
| `file:/opt/myapp/conf/service.properties` | Đường dẫn filesystem tuyệt đối theo URI thích hợp | Config ngoài JAR trên Linux |
| `file:./config/service.properties` | Đường dẫn filesystem tương đối theo working directory | Tool CLI dùng file ngoài package |
| `classpath:${settings.resource}` | Phần location giải bằng property placeholder | Trường hợp location được quyết định trước khi parse |

Đường dẫn `classpath:` phải có tài nguyên được đóng gói **trên runtime classpath**. Khi Maven build thông thường, `src/main/resources` được copy vào classpath/JAR. Không dùng `src/main/resources/config/service.properties` như runtime location bởi sau đóng JAR đó không nhất thiết còn là một filesystem path tồn tại.

Với `file:`, process phải có quyền đọc và working directory có thể khác khi chạy dưới IDE, Docker, cron hoặc systemd. Đặc biệt không hardcode đường dẫn riêng của máy phát triển vào ứng dụng phân phối.

### 4.1. Location có placeholder

Có thể khai báo:

```java
@Configuration
@PropertySource("classpath:${settings.file}")
class FileSelectedConfig {
}
```

Giả sử `settings.file=service-prod.properties` đã tồn tại trong `Environment` **trước khi `@PropertySource` được parse** (ví dụ JVM `-Dsettings.file=service-prod.properties` hoặc một source đã đăng ký trước), Spring có thể resolve location thành `classpath:service-prod.properties`.

Điểm quan trọng: property dùng để chọn **đường dẫn tệp** phải có sẵn trước khi đọc chính tệp ấy. Không thể đặt `settings.file` **chỉ bên trong file đang cố mở** rồi mong Spring suy ngược để tìm được location.

### 4.2. Tệp ngoài JAR

Một pattern triển khai cho CLI hoặc server:

```java
@Configuration
@PropertySource(
        value = "file:${service.config.path}",
        encoding = "UTF-8")
class ExternalFileConfig {
}
```

Ví dụ runtime:

```bash
java -Dservice.config.path=/etc/myapp/settings.properties -jar app.jar
```

Đây là cấu hình **minh họa**, cần file tồn tại và class chứa annotation được đăng ký. Nếu thiếu file và không bật `ignoreResourceNotFound`, context có thể không refresh được. Với tệp chứa secrets, cần bảo đảm permission và không in values ra log.

## 5. Một hoặc nhiều tệp properties

### 5.1. Một tệp

```java
@Configuration(proxyBeanMethods = false)
@PropertySource(
        value = "classpath:settings/common.properties",
        encoding = "UTF-8")
public class SettingsConfig {
}
```

Spring thêm một property source đọc từ `common.properties` vào môi trường của context tương ứng.

### 5.2. Nhiều locations trong một @PropertySource

```java
@Configuration
@PropertySource({
        "classpath:settings/defaults.properties",
        "classpath:settings/site.properties"
})
class MultiFileConfig {
}
```

Đây là snippet tích hợp. Với những locations được xử lý theo thứ tự, tệp xử lý sau có thể ghi đè những key đã xuất hiện trước đó theo quy tắc ưu tiên nguồn mà Spring áp dụng. Khi thứ tự là điều kiện nghiệp vụ quan trọng, nên kiểm chứng bằng integration test và tránh nguồn trùng tên/đường dẫn bất ngờ.

### 5.3. @PropertySources: container annotation

`@PropertySource` là annotation có thể dùng lặp lại, và Spring có `@PropertySources` làm container annotation.

```java
@Configuration
@PropertySources({
    @PropertySource("classpath:settings/defaults.properties"),
    @PropertySource("classpath:settings/site.properties")
})
class MultiSourceConfig {
}
```

Đây là snippet tích hợp, cần import `@PropertySources`, `@PropertySource`, `@Configuration`. Nếu cùng key xuất hiện ở nhiều `@PropertySource` declarations, Spring Reference/Javadoc mô tả **source được xử lý sau sẽ thắng** source trước cho key trùng, trong điều kiện các sources có tên/ordering như khai báo và không bị nguồn ưu tiên cao hơn ghi đè.

Có thể viết annotations lặp lại trực tiếp:

```java
@Configuration
@PropertySource("classpath:settings/defaults.properties")
@PropertySource("classpath:settings/site.properties")
class RepeatedSourceConfig {
}
```

Cả hai cách giúp biểu đạt “hai tập properties”. Trong ví dụ runnable dưới đây dùng `@PropertySources` để trình bày rõ thứ tự.

### 5.4. Những @Configuration classes khác nhau có thể làm ordering khó đoán

Nếu một context có hai class `@Configuration` độc lập, mỗi class khai báo `@PropertySource` cho cùng một key, thứ tự class được xử lý có thể phụ thuộc việc đăng ký/scanning/imports. **Đừng dựa vào thứ tự file nguồn hoặc tên class** để kết luận chắc chắn source nào thắng.

Nếu cần precedence tuyệt đối:

- Đặt các declarations cần thứ tự trong **một cấu hình xác định**, hoặc
- Chỉnh `ConfigurableEnvironment.getPropertySources()` một cách tường minh trước `refresh()`, hoặc
- Đặt quy ước các keys không chồng lấn giữa modules, hoặc
- Dùng cơ chế Config Data của Boot khi bài toán là nhiều layers externalized configuration trong Boot.

## 6. Vị trí của `@PropertySource` trong chuỗi ưu tiên

Trong `StandardEnvironment` của ứng dụng Spring Framework thuần, JVM system properties thường ưu tiên cao hơn OS environment variables. Các file do `@PropertySource` thêm trong quá trình xử lý Java configuration thường đứng **sau các nguồn đã hiện diện**, nên thứ tự thông dụng là:

```text
systemProperties
systemEnvironment
site.properties
defaults.properties
```

Ví dụ file defaults khai báo `demo.endpoint=https://defaults.invalid/api`, file site khai báo cùng key là `https://site.invalid/api`, còn JVM có `-Ddemo.endpoint=https://jvm.invalid/api`. Trong thứ tự trên, `Environment.getProperty("demo.endpoint")` nhận URL của **JVM**. Nếu không có `-D` nhưng có `DEMO_ENDPOINT` ở OS environment, `SystemEnvironmentPropertySource` có thể map tên tương ứng và chọn biến môi trường trước hai tệp.

Điểm đặc trưng của bài này là **`@PropertySource` đưa file vào chuỗi sources nhưng không tự cấp quyền ưu tiên cao nhất cho file**. Muốn kết quả khác, sắp xếp sources một cách có chủ đích và test context thật.

Spring Boot thêm Config Data, command-line/test sources và có precedence riêng; `@PropertySource` cũng thường được xử lý **trong refresh**, quá muộn cho một số thiết lập sớm như `logging.*` hoặc `spring.main.*`. Phần khác biệt đó được đào sâu ở bài tiếp theo.

## 7. Ví dụ hoàn chỉnh Java 17 + Maven: hai tệp và giá trị được tiêm vào bean

Ví dụ dưới đây sử dụng **Spring Framework Core Container** thuần, không cần Spring Boot, servlet hoặc database. Nó kiểm tra ba hành vi:

1. `@PropertySources` nạp tệp `defaults.properties` rồi `site.properties`.
2. Key trùng được lấy từ tệp có ưu tiên cao hơn trong hai tệp; key chỉ có ở defaults vẫn còn.
3. JVM system properties (`-D`) override tệp theo priority của `StandardEnvironment`, và `@Value`/constructor parameter nhận đúng giá trị đã resolve.

Dùng JAR độc lập để tránh nhầm JVM properties của Maven với JVM thực thi ứng dụng.

### 7.1. Cấu trúc dự án

Yêu cầu: JDK 17+ và Maven 3.9+.

```text
propertysource-demo/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── example/
        │       └── PropertySourceDemo.java
        └── resources/
            └── settings/
                ├── defaults.properties
                └── site.properties
```

### 7.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>propertysource-demo</artifactId>
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
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.6.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>example.PropertySourceDemo</mainClass>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

Tạo đúng hai tệp resources.

### 7.3. src/main/resources/settings/defaults.properties

```properties
demo.endpoint=https://defaults.invalid/api
demo.retries=2
demo.region=ap-southeast
demo.enabled=false
demo.label=Mặc định
```

### 7.4. src/main/resources/settings/site.properties

```properties
demo.endpoint=https://site.invalid/api
demo.retries=5
demo.enabled=true
demo.label=Điểm triển khai
```

`site.properties` cố ý không chứa `demo.region`, để kiểm tra việc Spring fallback về source còn giữ giá trị `ap-southeast`. Cả hai file là **UTF-8**, phù hợp với `encoding = "UTF-8"` trong annotation.

Các domain `.invalid` là dữ liệu ví dụ; demo **không thực hiện HTTP requests**.

### 7.5. src/main/java/example/PropertySourceDemo.java

```java
package example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.core.env.Environment;

public class PropertySourceDemo {

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(SettingsConfig.class)) {
            Environment env = context.getEnvironment();

            System.out.println("endpoint: "
                    + env.getProperty("demo.endpoint"));
            System.out.println("retries: "
                    + env.getProperty("demo.retries", Integer.class));
            System.out.println("region: "
                    + env.getProperty("demo.region"));
            System.out.println("enabled: "
                    + env.getProperty("demo.enabled", Boolean.class));
            System.out.println("label: "
                    + env.getProperty("demo.label"));
            System.out.println("missing with default: "
                    + env.getProperty("demo.not-configured", "fallback"));

            ApiSettings settings = context.getBean(ApiSettings.class);
            System.out.println("injected: "
                    + settings.endpoint() + " / retries=" + settings.retries());
        }
    }

    public record ApiSettings(String endpoint, int retries) {
    }

    @Configuration(proxyBeanMethods = false)
    @PropertySources({
        @PropertySource(
                value = "classpath:settings/defaults.properties",
                encoding = "UTF-8"),
        @PropertySource(
                value = "classpath:settings/site.properties",
                encoding = "UTF-8")
    })
    public static class SettingsConfig {

        @Bean
        public ApiSettings apiSettings(
                @Value("${demo.endpoint}") String endpoint,
                @Value("${demo.retries:3}") int retries) {
            return new ApiSettings(endpoint, retries);
        }
    }
}
```

**Giải thích thiết kế:**

- `SettingsConfig` được Spring parse trong lúc `AnnotationConfigApplicationContext(SettingsConfig.class)` refresh.
- `@PropertySources` đăng ký các tệp theo thứ tự được khai báo; với keys trùng, `site.properties` được xử lý sau `defaults.properties` và có thể override nó.
- `@Bean` method có hai tham số `@Value` được container resolve từ `Environment`.
- `ApiSettings` là Java record bất biến; giá trị được gán khi Spring tạo bean.
- `Environment.getProperty(..., Integer.class)` và `Boolean.class` thực hiện chuyển kiểu; nếu value sai cú pháp, không mặc định dùng fallback như khi thiếu key.
- `try-with-resources` đóng Spring context khi kết thúc chương trình.
- Không có HTTP request, database, scheduling hoặc các side effects không liên quan.

### 7.6. Build và chạy mặc định

Build:

```bash
mvn -q clean package
```

Chạy:

```bash
java -jar target/propertysource-demo-1.0.0.jar
```

Nếu tiến trình không có **JVM system properties hoặc OS environment variables** ghi đè các key `demo.*` được demo sử dụng, stdout là:

```text
endpoint: https://site.invalid/api
retries: 5
region: ap-southeast
enabled: true
label: Điểm triển khai
missing with default: fallback
injected: https://site.invalid/api / retries=5
```

**Đối chiếu từng giá trị:**

- `endpoint`: `site.properties` ghi đè `defaults.properties`.
- `retries`: `site.properties` có `5`, Spring chuyển thành Integer/int.
- `region`: chỉ có trong `defaults.properties`, nên vẫn trả `ap-southeast`.
- `enabled`: `site.properties` có `true`; Spring chuyển thành Boolean.
- `label`: `Điểm triển khai` đọc đúng dấu tiếng Việt vì hai declarations dùng `encoding="UTF-8"`.
- `missing with default`: không có key `demo.not-configured`, nên trả `fallback`.
- `injected`: `@Value` dùng cùng cơ chế property resolution, đưa đúng values vào `ApiSettings`.

### 7.7. JVM override bằng -D

Chạy một Java process với system properties:

```bash
java -Ddemo.endpoint=https://jvm.invalid/api -Ddemo.retries=9 -jar target/propertysource-demo-1.0.0.jar
```

Stdout dự kiến:

```text
endpoint: https://jvm.invalid/api
retries: 9
region: ap-southeast
enabled: true
label: Điểm triển khai
missing with default: fallback
injected: https://jvm.invalid/api / retries=9
```

Đây là minh chứng `systemProperties` mặc định có ưu tiên cao hơn các tệp `@PropertySource`. Hai `-D` flags phải nằm **trước** `-jar`; nếu đặt sau `-jar`, chúng chỉ là args của chương trình và không tự tạo JVM properties.

Trong trường hợp có environment variable trùng key, ví dụ `DEMO_ENDPOINT`, khi không có `-Ddemo.endpoint` thì nó có thể override tệp theo `SystemEnvironmentPropertySource`. Ví dụ điển hình trên Bash:

```bash
DEMO_ENDPOINT=https://env.invalid/api java -jar target/propertysource-demo-1.0.0.jar
```

Trong trường hợp này `endpoint` và `injected` dự kiến dùng `https://env.invalid/api` nếu không có nguồn ưu tiên cao hơn. Lệnh shell này không chạy nguyên dạng trong Windows cmd; cần dùng cú pháp thiết lập environment phù hợp hệ điều hành.

## 8. Giá trị qua Environment và @Value

### 8.1. Đọc trực tiếp bằng Environment

```java
String host = env.getProperty("service.host");
int timeout = env.getProperty("service.timeout", Integer.class, 10);
boolean enabled = env.getProperty(
        "service.enabled", Boolean.class, false);
```

Đây là snippet tích hợp giả định có biến `env` kiểu `Environment`. Các phương thức lần lượt trả:

- `String` hoặc `null` nếu key không tồn tại.
- `Integer` được chuyển đổi, hoặc default `10` khi key không tồn tại.
- `Boolean` được chuyển đổi, hoặc default `false` khi key không tồn tại.

**Không nhầm** giá trị thiếu với giá trị không hợp lệ. Ví dụ `service.timeout=abc` được tìm thấy nhưng không chuyển thành `Integer`; Spring có thể ném conversion exception thay vì trả default.

### 8.2. Inject bằng @Value

```java
@Component
public class RetryClient {
    private final int retries;

    public RetryClient(@Value("${service.retries:3}") int retries) {
        this.retries = retries;
    }
}
```

Đây là snippet tích hợp cần import `@Component`/`@Value` và class được Spring quản lý. Dấu `:` trong placeholder cho phép default. Việc `@Value` hoạt động đòi hỏi Spring annotation injection infrastructure, không xảy ra khi tự `new RetryClient(...)` ngoài container.

Với `@Value("${service.retries}")` không có default, nếu placeholder không resolve được thì lỗi có thể xuất hiện khi bean được tạo, tùy configuration của property placeholder resolution. Không nên cung cấp giá trị mặc định như `"changeme"` cho secret bắt buộc; thay vào đó fail-fast qua validation.

### 8.3. @Value là snapshot tại thời điểm tạo bean

Nếu tệp `settings.properties` thay đổi sau khi singleton được tạo, field/constructor value đã inject **không tự đổi**. Ngay cả khi code chỉnh `Environment.getPropertySources()` runtime, bean đã nhận `@Value` qua constructor không tự được tái tạo.

Nếu ứng dụng cần dynamic configuration, hãy dùng cơ chế reload/rebinding riêng, abstraction đọc giá trị tại thời điểm sử dụng hoặc giải pháp dành riêng cho loại ứng dụng. Không dùng `@PropertySource` như một lời hứa hot reload.

## 9. Encoding: đọc tệp UTF-8 đúng cách

Java properties trong nhiều API truyền thống từng được xử lý theo ISO-8859-1 hoặc Unicode escapes; việc không xác định encoding có thể làm chuỗi tiếng Việt hiển thị lỗi trong một số đường đọc tệp. Spring `@PropertySource` cung cấp attribute `encoding` để người viết chỉ rõ charset.

```java
@PropertySource(
        value = "classpath:settings/messages.properties",
        encoding = "UTF-8")
```

Ví dụ file:

```properties
welcome.message=Chào mừng bạn đến với Spring
```

Với encoding đúng, `env.getProperty("welcome.message")` có thể đọc chữ tiếng Việt không bị mojibake trong hệ thống xử lý UTF-8.

**Phân biệt ba tầng mã hóa:**

1. Mã hóa **source Java** (Maven `project.build.sourceEncoding`).
2. Mã hóa **tệp properties** được Spring đọc (thuộc tính `encoding` của `@PropertySource`).
3. Mã hóa **terminal/stdout** khi in ra log.

Đặt encoding của tệp mà terminal không hiển thị được Unicode vẫn có thể khiến stdout trông sai dù Spring nạp đúng. Ví dụ phần 7 khai báo encoding tệp rõ ràng; stdout giả định terminal hỗ trợ hiển thị UTF-8.

## 10. ignoreResourceNotFound: tùy chọn hay bắt buộc?

Mặc định, nếu một tệp mà `@PropertySource` yêu cầu **không tồn tại**, Spring thường ném lỗi trong quá trình parse configuration và context có thể **không refresh thành công**.

Nếu file mang tính optional, có thể dùng:

```java
@Configuration
@PropertySource(
        value = "classpath:optional-local.properties",
        ignoreResourceNotFound = true,
        encoding = "UTF-8")
class OptionalLocalConfig {
}
```

Đây là snippet tích hợp. Khi resource thiếu và `ignoreResourceNotFound=true`, Spring có thể bỏ qua source đó thay vì coi là lỗi khởi động. Nhưng attribute này **không phải cơ chế che giấu mọi lỗi**: nếu file tồn tại mà chứa dữ liệu cấu hình vô nghĩa, type conversion thất bại hoặc quyền truy cập/phân tích gặp vấn đề khác, bạn vẫn phải xử lý nguyên nhân.

**Quy tắc thiết kế:**

- Cấu hình **bắt buộc**: để mặc định fail-fast hoặc validate ngay khi startup.
- Cấu hình **override tùy chọn**: dùng `ignoreResourceNotFound=true` nếu thật sự có phương án fallback đầy đủ.
- Cấu hình chứa secrets hoặc giá trị an toàn quan trọng: đừng dùng optional flag để ứng dụng chạy ngầm với default không an toàn.

Nếu file optional không tồn tại nhưng key có sẵn từ JVM/OS environment hoặc một file khác, `Environment` vẫn có thể resolve key theo những sources còn lại.

### 10.1. Khác biệt thiếu file và thiếu key

- **Thiếu file resource**: xảy ra trong quá trình Spring cố đọc một location của `@PropertySource`.
- **Có file nhưng thiếu property key**: file nạp bình thường, nhưng `Environment.getProperty("missing")` trả `null` hoặc default.
- **Có key nhưng value sai type**: quá trình conversion hoặc initialization của bean có thể thất bại.
- **Có key nhưng value rỗng**: tùy converter/business rules, có thể được coi là chuỗi rỗng chứ không phải missing.

Vì vậy cần tách trách nhiệm: `ignoreResourceNotFound` điều khiển **file availability**, còn validation/required property APIs điều khiển **giá trị cấu hình**.

## 11. Placeholder trong đường dẫn và trong nội dung property

Hai cách dùng `${...}` có thời điểm rất khác nhau.

### 11.1. Placeholder trong đường dẫn @PropertySource

```java
@Configuration
@PropertySource("classpath:config/${profile.file}")
class DynamicPathConfig {
}
```

Để Spring tìm thấy file, `profile.file` phải được resolve **khi parser đang xử lý annotation** — ví dụ được cung cấp bằng `-Dprofile.file=dev.properties` hoặc nguồn Environment đã có sẵn.

Nếu key chỉ tồn tại trong tệp muốn nạp, sẽ có quan hệ phụ thuộc vòng: cần key để biết tên file nhưng cần file để đọc key. Không chọn giải pháp đó.

### 11.2. Placeholder trong giá trị của file

File:

```properties
service.host=localhost
service.url=http://${service.host}:8080/api
```

Khi yêu cầu Spring resolve giá trị `service.url`, đường property resolver có thể xử lý nested placeholders theo cấu hình. Lưu ý không phải mọi API `PropertySource.getProperty(key)` trực tiếp đều làm cùng một bước placeholder resolution như `Environment.getProperty`/property resolver.

### 11.3. Placeholder trong @Value

```java
@Value("${service.url:http://localhost:8080/api}")
private String endpoint;
```

Đây là snippet tích hợp với Spring-managed bean. Default được áp khi property không resolve được theo cơ chế placeholder tương ứng. Đối với các cấu hình quan trọng, nên kiểm tra cả chuỗi cuối cùng và URL validity, không chỉ mặc định tin rằng placeholder được thay nghĩa là endpoint luôn dùng được.

## 12. PropertySourcesPlaceholderConfigurer có bắt buộc không?

Trong các ứng dụng Spring sử dụng Java configuration, bạn có thể thấy bean:

```java
@Bean
public static PropertySourcesPlaceholderConfigurer
        propertySourcesPlaceholderConfigurer() {
    return new PropertySourcesPlaceholderConfigurer();
}
```

Đây là snippet tích hợp, cần import `org.springframework.context.support.PropertySourcesPlaceholderConfigurer`, và thường đặt trong `@Configuration`. Nó là một `BeanFactoryPostProcessor` chuyên xử lý placeholders của bean definitions và thiết lập embedded value resolver phù hợp.

**Đừng khẳng định mọi `@Value("${...}")` đều bắt buộc tự khai báo processor này.** `ApplicationContext` hiện đại có sẵn cơ chế embedded value resolution cơ bản, còn Spring Boot tổ chức property sources và placeholder handling qua hạ tầng riêng. Nhưng với những trường hợp phức tạp hoặc legacy bean definition placeholders, `PropertySourcesPlaceholderConfigurer` vẫn hữu ích và nên được đăng ký sớm.

Vì là BFPP, method `@Bean` thường được khai báo **`static`** để processor có thể được tạo trước các application beans mà không cần tạo instance class `@Configuration` quá sớm.

### 12.1. @PropertySource khác BFPP

`@PropertySource` khai báo **nguồn dữ liệu**. `PropertySourcesPlaceholderConfigurer` là **processor xử lý placeholders** trong một số cấu hình/bean definitions. Hai khái niệm hỗ trợ nhau nhưng không đồng nghĩa.

Một URL hoặc path file đã được đưa vào Environment không có nghĩa Spring đã tự inject URL đó vào mọi bean. Vẫn cần bean definition, `@Value`, constructor parameters hoặc code đọc Environment.

## 13. Khi nào tùy chỉnh Environment trực tiếp tốt hơn?

`@PropertySource` rất gọn cho một tập resources tĩnh. Khi cần đặt thứ tự rõ ràng hoặc đưa một map được tạo trong test lên độ ưu tiên cao nhất, hãy dùng `ConfigurableEnvironment`.

```java
var context = new AnnotationConfigApplicationContext();

context.getEnvironment().getPropertySources().addFirst(
        new MapPropertySource(
                "testOverrides",
                Map.of("demo.endpoint", "https://test.invalid/api")));

context.register(SettingsConfig.class);
context.refresh();
```

Đây là snippet tích hợp dựa trên demo phần 7, cần import `MapPropertySource` và `java.util.Map`. Source `testOverrides` được thêm **trước refresh**, nên nó có thể thắng `systemProperties`, `systemEnvironment` và các file sources cho key đó.

Các phép quản lý:

| Phương thức | Ý nghĩa |
|---|---|
| `addFirst(source)` | Ưu tiên cao nhất |
| `addLast(source)` | Ưu tiên thấp nhất |
| `addBefore(name, source)` | Chen trước một source cụ thể |
| `addAfter(name, source)` | Chen sau một source cụ thể |
| `replace(name, source)` | Thay source theo tên |
| `remove(name)` | Gỡ source theo tên |

Đừng thay đổi property sources tùy tiện giữa lúc các beans được tạo đồng thời. Một quy tắc an toàn là **chỉnh trước `refresh()`**, test precedence và giữ một owner cho cấu hình runtime nếu thật sự cần thay đổi sau đó.

## 14. Khi dùng với Spring Boot

Spring Framework Core Container và Spring Boot có cùng các nền tảng `Environment`, `PropertySource`, `@Value`, nhưng Boot bổ sung cơ chế **Externalized Configuration / Config Data**:

- Tự tìm và nạp `application.properties`/`application.yml` theo quy tắc của Boot.
- Hỗ trợ profile-specific files, locations, imports, command-line arguments và các sources chuyên biệt.
- Có typed binding qua `@ConfigurationProperties` (thuộc Boot) và validation thích hợp.
- Có một số early application settings được xác định trước khi context refresh.

Nếu chỉ dùng Spring Core:

```java
var context = new AnnotationConfigApplicationContext(AppConfig.class);
```

**Không mặc định** có file `application.properties` được tự đọc chỉ vì tên tệp. Phải dùng `@PropertySource`, API tùy chỉnh nguồn hoặc cơ chế config khác.

Nếu đang dùng Spring Boot, với các file ứng dụng thông thường, cơ chế Config Data mặc định thường là giải pháp rõ ràng hơn so với gắn `@PropertySource("classpath:application.properties")` thủ công.

### 14.1. YAML không được hỗ trợ mặc định qua @PropertySource

Spring Core có các công cụ đọc YAML ở nơi khác trong framework, nhưng `@PropertySource` mặc định không parse YAML như Boot Config Data. Một custom `PropertySourceFactory` có thể được chỉ định để đổi cách đọc resources:

```java
@PropertySource(
        value = "classpath:settings.yaml",
        factory = CustomYamlPropertySourceFactory.class)
```

Đây chỉ là **snippet minh họa API**, yêu cầu bạn tự viết một class `CustomYamlPropertySourceFactory` implement `PropertySourceFactory`. Nó **không phải tên class có sẵn được Spring cung cấp** để copy trực tiếp. Nếu không thực sự cần custom parser trong Spring Core, dùng Java `.properties` hoặc Boot config data sẽ đơn giản hơn.

### 14.2. Không dùng @PropertySource cho early Boot settings

Một số giá trị như `logging.*` hoặc `spring.main.*` được Spring Boot xử lý ở giai đoạn rất sớm. Vì `@PropertySource` thường được parse trong context refresh, nó có thể **quá muộn** để ảnh hưởng những giai đoạn đó. Đặt các settings như vậy vào những property sources mà Boot đọc sớm theo tài liệu Boot (command line, config data, environment, v.v.).

## 15. Debug khi @PropertySource không hoạt động

| Hiện tượng | Nguyên nhân khả dĩ | Cách khắc phục |
|---|---|---|
| `env.getProperty(key)` trả null | Sai key, file chưa được nạp, config class không được register | Kiểm tra `@Configuration`, resource path và tên key |
| `FileNotFoundException` khi refresh | Resource không nằm trên runtime classpath/filesystem | Kiểm tra đường dẫn, build resources, đóng JAR |
| `@Value` không resolve | Không có nguồn, placeholder sai hoặc annotation processing chưa chạy | Kiểm tra `Environment` và DI infrastructure |
| Giá trị từ tệp bị `-D` ghi đè | `systemProperties` ưu tiên cao trong Environment mặc định | Xác nhận Java VM options và thiết kế precedence |
| Giá trị bị OS env ghi đè | `systemEnvironment` có thể ưu tiên cao hơn file | Kiểm tra biến `DEMO_ENDPOINT` và mapping |
| File nạp nhưng tiếng Việt lỗi | Charset tệp/loader/terminal khác nhau | Dùng UTF-8 và `encoding="UTF-8"` |
| Đổi `.properties` nhưng singleton vẫn giữ giá trị cũ | `@Value` không tự inject lại, tệp không tự reload | Thiết kế reload riêng hoặc restart context phù hợp |
| Hai configuration classes khai báo cùng key cho kết quả khó đoán | Thứ tự parse/register khác dự kiến | Tập trung precedence trong một nơi hoặc addFirst rõ ràng |
| `application.yml` bị đọc như properties | `@PropertySource` mặc định không parse YAML | Dùng Boot Config Data hoặc custom factory |
| `@PropertySource` không ảnh hưởng Boot logging | Setting cần được đọc trước refresh | Sử dụng sources được Boot xử lý sớm |
| File tùy chọn thiếu mà startup lỗi | `ignoreResourceNotFound` mặc định false | Đặt true chỉ nếu thật sự có fallback an toàn |
| Key tìm được nhưng `Integer.class` ném lỗi | Giá trị số sai định dạng | Validate và chỉnh tệp, không nuốt lỗi |
| Khai báo tệp dưới `src/test/resources` nhưng chạy production | Test resources không nằm ở runtime classpath production | Đưa tệp cần đóng gói sang `src/main/resources` |
| Dùng `file:./config/x.properties` nhưng IDE chạy lỗi | Working directory khác | Xem process working directory, dùng absolute path/config location rõ |
| Cấu hình trong class không bao giờ được import/scan | CCPP không gặp annotation đó | `register(...)`, `@Import`, component scanning đúng |
| Đặt `${config.file}` trong chính file phải được mở | Vòng phụ thuộc đường dẫn | Cung cấp location property trước bootstrap |

### 15.1. Quy trình chẩn đoán có kiểm soát

1. Xác nhận class `@Configuration` chứa `@PropertySource` thực sự được context đăng ký.
2. Kiểm tra `src/main/resources` có tệp, Maven đóng gói đúng đường dẫn tương đối trong JAR hay chưa.
3. Kiểm tra `@PropertySource` dùng `classpath:` hay `file:` đúng với vị trí resource.
4. Kiểm tra `ignoreResourceNotFound`: nếu true, thiếu tệp có thể bị bỏ qua có chủ đích.
5. Kiểm tra key/value và charset của tệp.
6. Duyệt **tên** các `PropertySource` trong `Environment`, không log toàn bộ nội dung, tránh lộ secrets.
7. Kiểm tra JVM `-D` và OS env có key ưu tiên cao hơn không.
8. Kiểm tra duplicate keys giữa nhiều files và parse order.
9. Nếu `@Value` sai nhưng `Environment` đúng, xem bean có do Spring quản lý và annotation infrastructure có hoạt động không.
10. Nếu có Spring Boot, kiểm tra Boot Config Data và early settings; không chỉ xem hai sources mặc định.
11. Kiểm tra value có convert được sang type đích; `service.timeout=abc` không hợp lệ cho `int`.
12. Viết integration test cho cả trường hợp file có mặt, thiếu file optional, JVM override và default value.

Để quan sát names mà không in secrets:

```java
for (PropertySource<?> source
        : context.getEnvironment().getPropertySources()) {
    System.out.println(source.getName());
}
```

Đây là snippet tích hợp, cần import `org.springframework.core.env.PropertySource` và context có `ConfigurableEnvironment`. Tên sources giúp chẩn đoán cấu trúc, nhưng có thể chứa resource paths riêng của hệ thống; cân nhắc trước khi log nơi công khai.

## 16. Bảo mật và vận hành

`@PropertySource` là cơ chế **nạp/tra cứu cấu hình**, không tự mã hóa nội dung tệp, không thiết lập quyền filesystem và không che giá trị nhạy cảm trước logger hoặc heap dump.

Các thực hành quan trọng:

- Không commit credentials/secret keys vào tệp source control nếu chúng thuộc bí mật production.
- Nếu dùng `file:` bên ngoài JAR, đặt filesystem permissions phù hợp với user chạy Java process.
- Không in toàn bộ `Environment`, `PropertySources` hoặc `Properties` map vào logs.
- Không sử dụng default token giả như `changeme` cho secret thực sự bắt buộc; hãy fail-fast.
- Biết rõ thứ tự override giữa tệp, environment và VM options để tránh khởi động nhầm endpoint production/test.
- Kiểm tra tệp ngoài JAR được mount/present ở đúng thời điểm trước refresh.
- Với giá trị được inject qua `@Value`, cần restart/recreate/rebind đúng cơ chế khi thay cấu hình, không chờ file tự reload.
- Đối với nhiều modules, tránh phân tán `@PropertySource` cùng key ở nhiều configuration classes rồi dựa vào ordering ngầm.

## 17. Checklist quyết định dùng @PropertySource

Dùng `@PropertySource` khi:

- Đang dùng **Spring Framework Core** hoặc Java configuration cần tải một vài tệp `.properties` rõ ràng.
- Muốn các keys từ file hiện diện trong `Environment` trước khi inject values.
- Có class cấu hình được Spring quản lý và resource path xác định.
- Muốn tránh đưa các cặp cấu hình vào Java source.
- Có thể xác định được thứ tự file/property sources và kiểm tra bằng test.

Cân nhắc cơ chế khác khi:

- Đang dùng Spring Boot với `application.properties`/`application.yml` và profiles: ưu tiên **Config Data** có sẵn.
- Cần bind cấu trúc nested config vào validated Java objects trên Boot: dùng `@ConfigurationProperties`.
- Cần JSON/YAML/parser đặc thù: dùng factory/config infrastructure đúng format.
- Cần config refresh động: thiết kế reloading/rebinding có lifecycle riêng.
- Cần override từ CI/secrets manager: thêm sources ở thời điểm thích hợp hoặc dùng tích hợp hạ tầng riêng.
- Cần điều khiển những Boot startup settings được đọc trước context refresh: đặt ở sources sớm, không chỉ `@PropertySource`.

## 18. Câu hỏi tự kiểm tra

**1. `@PropertySource("classpath:config/app.properties")` làm gì?**  
Yêu cầu Spring xử lý resource đó và thêm một property source từ tệp vào `Environment` khi configuration class được parse.

**2. Trong Maven, tệp ở đâu để lookup được bằng `classpath:settings/site.properties`?**  
Ở `src/main/resources/settings/site.properties`, để build copy resource vào runtime classpath/JAR.

**3. `@PropertySource` có tự tạo bean Java cho mỗi key không?**  
Không. Nó chỉ đưa key-values vào `Environment`; phải dùng `getProperty`, `@Value` hoặc cơ chế binding khác.

**4. Vì sao `@Configuration` quan trọng?**  
Vì Spring annotation configuration processing nhận diện và thực hiện khai báo `@PropertySource` khi class được đăng ký vào context đúng cách.

**5. Nếu hai `@PropertySource` definitions cùng có key trùng thì sao?**  
Nguồn được xử lý sau có thể override nguồn trước; cần xem thứ tự thực tế và các sources ưu tiên cao hơn.

**6. Nếu có `-Ddemo.endpoint=...` đồng thời file có cùng key, giá trị nào thắng trong StandardEnvironment mặc định?**  
Giá trị JVM system property do `systemProperties` có ưu tiên cao hơn file sources.

**7. `encoding="UTF-8"` dùng để làm gì?**  
Chỉ rõ charset khi đọc resource, đặc biệt hữu ích cho values chứa ký tự tiếng Việt.

**8. `ignoreResourceNotFound=true` có biến mọi lỗi cấu hình thành an toàn không?**  
Không. Nó chỉ cho phép bỏ qua resource không có theo contract; key thiếu, type sai hoặc cấu hình bất hợp lệ vẫn cần xử lý.

**9. `@PropertySource` có parse YAML tự động không?**  
Không theo cơ chế mặc định. Cần custom `PropertySourceFactory` hoặc Spring Boot Config Data phù hợp.

**10. Thay file sau khi singleton đã nhận `@Value` có tự cập nhật singleton không?**  
Không. `@PropertySource` không cung cấp tự động hot reload/reinjection.

**11. Nếu muốn location động `classpath:${config.file}`, `config.file` cần có từ đâu?**  
Từ một property source đã tồn tại đủ sớm, trước khi Spring cố resolve location.

**12. Tại sao @PropertySource có thể quá muộn cho một số thiết lập logging trong Spring Boot?**  
Vì Boot có thể dùng các early settings trước pha context refresh nơi annotation được xử lý.

## Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — @PropertySource Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/PropertySource.html): resource locations, multiple annotations, precedence, encoding và optional files.
- [Spring Framework 6.2 — @PropertySources Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/PropertySources.html): container cho nhiều declarations.
- [Spring Framework Reference — Using @PropertySource](https://docs.spring.io/spring-framework/reference/core/beans/environment.html#beans-using-propertysource): nguồn property trong môi trường Spring.
- [Spring Framework Reference — Environment Abstraction](https://docs.spring.io/spring-framework/reference/core/beans/environment.html): `Environment`, property sources, precedence và profiles.
- [Spring Framework 6.2 — ConfigurableEnvironment Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/ConfigurableEnvironment.html): thay đổi danh sách property sources trước `refresh()`.
- [Spring Framework 6.2 — PropertySourceFactory Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/support/PropertySourceFactory.html): điểm mở rộng để tự đọc resource.
- [Spring Boot Reference — Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html): Config Data, Boot property precedence và sự khác biệt với Core.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-environment-007">Giới hạn của @PropertySource và khác biệt với cơ chế cấu hình Spring Boot</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn của @PropertySource và khác biệt với cơ chế cấu hình Spring Boot

## 1. Vì sao cùng một file cấu hình có thể hoạt động trong Spring Boot nhưng không hoạt động trong Spring Framework thuần?

Một dự án Spring Framework dùng `AnnotationConfigApplicationContext` có thể tạo file `src/main/resources/application.properties` nhưng gọi `environment.getProperty("app.mode")` vẫn nhận `null`. Trong khi đó một ứng dụng Spring Boot thường tự tìm `application.properties`, `application.yml`, file theo profile và cả các vị trí bên ngoài ứng dụng.

Đây **không phải** lỗi của `Environment` hoặc classpath. Hai hệ thống có trách nhiệm khác nhau:

- **Spring Framework Core Container** cung cấp abstraction `Environment`/`PropertySource` và annotation `@PropertySource` để **khai báo một hoặc nhiều resources cấu hình cần thêm** khi configuration class được xử lý.
- **Spring Boot** thêm hệ thống **Externalized Configuration / Config Data**: tự tìm các file theo convention, tính precedence, xử lý profiles, imports, YAML, command-line, environment variables, typed binding và nhiều điểm mở rộng ở startup.

Vì thế `@PropertySource` **không phải** một phiên bản khác tên của `spring.config.import`, cũng không cung cấp đầy đủ cơ chế Config Data của Boot.

### Điều cần ghi nhớ trước tiên

> `@PropertySource` trong Spring Framework là cơ chế **đăng ký PropertySource từ resource tại lúc xử lý configuration class**. Spring Boot Config Data là cơ chế **bootstrap cấu hình sớm, nhiều nguồn, có quy tắc load/profile/import/precedence riêng**.

Sau bài này bạn có thể:

1. Biết `@PropertySource` nạp gì, vào lúc nào và giới hạn định dạng/resource của nó.
2. Giải thích vì sao `application.properties` không tự động được nạp trong một core context thuần.
3. Phân biệt tài nguyên `.properties`/XML với YAML và cơ chế custom `PropertySourceFactory`.
4. Hiểu `ignoreResourceNotFound`, `encoding`, `name`, placeholder trong đường dẫn và các hạn chế thứ tự.
5. Giải thích vì sao `@PropertySource` có thể **quá muộn** cho những thiết lập Boot đọc trước refresh như `logging.*` hoặc `spring.main.*`.
6. Nắm quy tắc Spring Boot Config Data, `spring.config.import`, `spring.config.location`, profile files và override.
7. Phân biệt resolve bằng `Environment`/`@Value` với typed binding `@ConfigurationProperties` của Boot.
8. Chạy hai ví dụ Java 17: một Spring Core thuần, một Spring Boot 3.3.x, để xác minh hành vi thực.

## 2. Ba tầng cần phân biệt trước khi nói về giới hạn

`PropertySource` là nguồn key/value có tên, `Environment` là API truy vấn các nguồn theo precedence, còn `@PropertySource` là annotation yêu cầu thêm một resource vào chuỗi sources khi class cấu hình được phân tích.

```java
@Configuration
@PropertySource("classpath:client.properties")
class ClientConfig {
    @Bean
    ClientSettings settings(Environment environment) {
        int timeout = environment.getRequiredProperty(
                "client.timeout", Integer.class);
        return new ClientSettings(timeout);
    }
}
```

Đây là cấu hình minh họa, cần imports `Configuration`, `PropertySource`, `Bean`, `Environment`, cùng class `ClientSettings(int timeout)`. Với file `client.properties` có `client.timeout=15`, annotation cung cấp **giá trị cấu hình**; chính method `@Bean` mới tạo **bean object**. Annotation không tự bind file thành một bean, không validate mọi giá trị và không thực hiện hot reload.

Bài trước đã chỉ cách nạp file; phần còn lại của bài này tập trung vào những trường hợp file **không được nạp như kỳ vọng**: định dạng YAML, wildcard resources, encoding, thứ tự config class và pipeline Spring Boot.

## 3. Spring Framework thuần không tự nạp mọi file tên `application.properties`

Một `AnnotationConfigApplicationContext` thông thường không có convention tự dò `application.properties` và `application.yml` giống Boot.

Ví dụ:

~~~java
try (var context =
         new AnnotationConfigApplicationContext(MyConfig.class)) {
    String result = context.getEnvironment()
        .getProperty("demo.mode");
}
~~~

Nếu `MyConfig` không khai báo `@PropertySource`, và không nguồn nào khác chứa `demo.mode`, việc có một file `src/main/resources/application.properties` **không đủ** để bảo đảm `result` nhận value từ file ấy.

Muốn nạp file vào Core context, bạn có thể:

- Khai báo `@PropertySource("classpath:application.properties")`.
- Đăng ký `ResourcePropertySource` hoặc `MapPropertySource` vào `ConfigurableEnvironment` trước `refresh()`.
- Dùng bootstrap/initializer có nhiệm vụ thêm các sources.
- Dùng một framework cao hơn, chẳng hạn Spring Boot, để quản lý conventions.

**Kết luận:** File tồn tại trên classpath chỉ có nghĩa nó có thể được đọc; không có nghĩa Spring Core tự quyết định đọc file đó.

## 4. `@PropertySource` được xử lý ở pha nào?

Trong Java configuration, `@PropertySource` được xử lý khi Spring phân tích `@Configuration` classes trong quá trình `ApplicationContext.refresh()`, thông qua configuration-class infrastructure.

Một mô hình tổng quát:

~~~text
Tạo Environment (systemProperties, systemEnvironment, ...)
          |
          v
Bootstrap/initializer có thể thêm sources sớm
          |
          v
ApplicationContext bắt đầu refresh()
          |
          v
ConfigurationClassPostProcessor phân tích @Configuration
          |
          +-- Xử lý @ComponentScan / @Import / @Bean definitions
          |
          '-- Xử lý @PropertySource, thêm source vào Environment
          |
          v
BeanFactoryPostProcessor/BeanPostProcessor pipeline
          |
          v
Application beans được tạo, @Value và Environment được dùng
          |
          v
Context hoàn tất refresh
~~~

Sơ đồ đã lược bỏ nhiều chi tiết kỹ thuật, nhưng điểm quan trọng là `@PropertySource` **không phải hook thêm config sớm nhất**.

Nếu ứng dụng chỉ cần `client.timeout` để tạo bean `ClientSettings` ở giai đoạn singleton instantiation, `@PropertySource` thường phù hợp. Nếu ứng dụng Boot cần `logging.level.*` để thiết lập logging **trước khi context refresh**, nguồn được thêm qua `@PropertySource` có thể đến quá muộn.

### Vì sao “quá muộn” quan trọng?

Một config setting có thể được đọc tại một trong nhiều thời điểm:

1. Boot chuẩn bị environment, logging, application mode và startup infrastructure.
2. Context refresh và phân tích configuration classes.
3. Bean được tạo và nhận config qua injection.
4. Business method đọc `Environment` ở runtime.

Nếu một nguồn chỉ xuất hiện ở bước 2, các setting được xử lý ở bước 1 **không thể trông chờ** nhận giá trị từ nguồn đó. Không thể chỉ nhìn rằng value cuối cùng có mặt trong `Environment` để khẳng định toàn bộ bootstrap đã áp dụng nó.

## 5. Định dạng được hỗ trợ mặc định: properties và XML properties

Annotation `@PropertySource` có thuộc tính `factory`. Với factory mặc định, Spring hỗ trợ các resource properties thông thường, ví dụ:

~~~java
@PropertySource("classpath:settings.properties")
~~~

hoặc định dạng XML properties chuẩn của Java:

~~~java
@PropertySource("classpath:settings.xml")
~~~

XML properties phải tuân theo định dạng Java `Properties.loadFromXML(...)` phù hợp, không phải XML tùy ý theo schema tự nghĩ ra.

**YAML không được nạp trực tiếp qua `@PropertySource` với factory mặc định.** Ví dụ:

~~~java
@PropertySource("classpath:settings.yml")
~~~

không tương đương với cơ chế nạp YAML của Spring Boot. Nếu resource tồn tại nhưng không đúng định dạng loader mặc định, kết quả có thể lỗi hoặc không resolve được keys như mong đợi.

### Vì sao Boot hỗ trợ YAML nhưng `@PropertySource` mặc định không?

Spring Boot có parser/config data loader dành riêng cho YAML. Nó hiểu tài liệu YAML như:

~~~yaml
shop:
  api:
    timeout: 15
    url: https://example.test
~~~

và đưa dữ liệu đã flatten/được bind theo convention vào hệ thống environment/configuration binding.

Annotation `@PropertySource` của Spring Core **không tự gọi** parser YAML của Boot. Đây là giới hạn cơ chế mặc định, không phải Spring hoàn toàn không có khả năng đọc YAML.

## 6. Có thể dùng YAML với `@PropertySource` qua custom factory không?

Có thể **xây dựng một `PropertySourceFactory` tùy biến** để chuyển YAML thành `Properties` rồi trả về một `PropertySource`. Nhưng đây là **cấu hình thủ công**, không phải chức năng mặc định của annotation.

Spring Framework có những helper liên quan YAML, ví dụ:

- `YamlPropertiesFactoryBean`: chuyển YAML thành `Properties`.
- `YamlMapFactoryBean`: chuyển YAML thành `Map`.
- Một số loader chuyên biệt của Spring Boot cho hệ thống Config Data.

Ví dụ skeleton ý tưởng:

~~~java
public final class YamlSourceFactory implements PropertySourceFactory {
    @Override
    public PropertySource<?> createPropertySource(
            String name, EncodedResource resource)
            throws IOException {

        YamlPropertiesFactoryBean loader =
            new YamlPropertiesFactoryBean();
        loader.setResources(resource.getResource());

        Properties props = loader.getObject();
        String effectiveName = (name != null)
            ? name
            : resource.getResource().getDescription();

        return new PropertiesPropertySource(
            effectiveName, props);
    }
}
~~~

Đây là **snippet framework integration** yêu cầu imports `PropertySourceFactory`, `EncodedResource`, `YamlPropertiesFactoryBean`, `PropertiesPropertySource`, `Properties` và `IOException`; ngoài ra YAML parsing cần dependency tương ứng, ví dụ SnakeYAML compatible với phiên bản Spring. Cần quyết định cách xử lý `props == null`, encoding, resource lỗi, multiple YAML documents và validation trước khi đưa vào production. Không coi đoạn này là toàn bộ một dự án runnable.

Sau đó có thể khai báo:

~~~java
@Configuration
@PropertySource(
    value = "classpath:settings.yml",
    factory = YamlSourceFactory.class)
class YamlConfig {
}
~~~

**Nhưng:** Cách tự viết factory không tái tạo đầy đủ Boot Config Data: profile activation, config imports, profile-specific location resolution, optional location semantics và Boot precedence vẫn cần cơ chế riêng. Với ứng dụng Spring Boot, thường nên để **Config Data** nạp YAML thay vì tự viết factory cho từng file.

## 7. Không hỗ trợ wildcard resource patterns theo cách mặc định

`@PropertySource.value` nhận một hoặc nhiều **resource locations**. Mỗi location phải resolve ra một resource cụ thể; Javadoc không cho phép wildcard như:

~~~java
@PropertySource("classpath*:config/*.properties")
~~~

Đây là ví dụ **không hợp lệ cho use case @PropertySource theo contract resource pattern**, không nên dùng để nạp toàn bộ file trong một thư mục.

Tương tự, không giả định glob `**/*.properties` được Spring tự expand trong annotation này.

Nếu cần nạp nhiều resources đã biết:

~~~java
@PropertySource({
    "classpath:defaults.properties",
    "classpath:service.properties",
    "classpath:region.properties"
})
~~~

đó là cách minh bạch hơn. Đối với một thư viện phát hiện file/plugin động, dùng cơ chế resource scanning/bootstrap riêng rồi đăng ký các sources có thứ tự rõ ràng.

### Điều cần lưu ý khi tìm file

`classpath:` tìm resource theo classpath của ứng dụng, không mặc định theo working directory. `file:` có thể trỏ đến file bên ngoài. Path tương đối trong hệ thống tệp có thể phụ thuộc thư mục process được khởi chạy; nên xây đường dẫn tuyệt đối hoặc convention ổn định cho deployment.

File đã đặt trong `src/main/resources` thường được build tool đưa vào classpath. Nhưng nếu chạy bằng `javac` thủ công mà quên copy resources vào classpath, lookup vẫn thất bại.

## 8. Tùy chọn `ignoreResourceNotFound`: file không tồn tại có được bỏ qua?

Mặc định:

~~~java
@PropertySource("classpath:missing.properties")
~~~

có `ignoreResourceNotFound = false`. Nếu resource cần thiết không tìm được, quá trình phân tích configuration có thể thất bại thay vì tiếp tục âm thầm.

Nếu file thật sự là optional:

~~~java
@PropertySource(
    value = "classpath:optional-overrides.properties",
    ignoreResourceNotFound = true)
~~~

thì Spring có thể bỏ qua trường hợp không tìm được resource theo contract annotation.

**Không lạm dụng `ignoreResourceNotFound=true` cho file cấu hình bắt buộc.** Nếu file chứa connection URL hoặc security config quan trọng, bỏ qua thiếu file có thể khiến ứng dụng chạy với defaults sai.

Trong Spring Boot Config Data, optional locations có cú pháp riêng, chẳng hạn:

~~~properties
spring.config.import=optional:file:./external-overrides.properties
~~~

Đây là thuộc tính **Boot Config Data**, không phải một biến thể của `@PropertySource.ignoreResourceNotFound`. Hai cơ chế thuộc hai thời điểm và hệ thống xử lý khác nhau.

### File thiếu khác file lỗi

Tùy implementation, một source tồn tại nhưng không parse được vì format/encoding/syntax **không nên** được coi là một tình huống “optional file missing”. Một cấu hình production nên fail fast với input hỏng thay vì nuốt lỗi. Việc `ignoreResourceNotFound` có bỏ qua mọi IOException hay chỉ các lỗi resource không truy cập được cần tra theo phiên bản và xử lý thực tế của Spring; không nên sử dụng như chính sách kiểm tra file hợp lệ.

## 9. Tùy chọn `encoding` và tiếng Việt

`@PropertySource` có `encoding` để chỉ định bảng mã cho resource được nạp:

~~~java
@PropertySource(
    value = "classpath:messages.properties",
    encoding = "UTF-8")
~~~

Nếu `messages.properties` lưu các chuỗi tiếng Việt UTF-8, annotation nên khai báo encoding tương ứng để tránh hiện tượng ký tự bị thay đổi trên runtime/library loader cũ hoặc khi chuyển môi trường.

Một snippet:

~~~properties
app.greeting=Xin chào Việt Nam
~~~

Kết quả mong muốn của `environment.getProperty("app.greeting")` là chính chuỗi Unicode đã được lưu.

**Không suy ngược rằng mọi file trong Boot đều được đọc bằng quy tắc encoding giống factory mặc định của `@PropertySource`.** Config Data imports có loader và tùy chọn encoding riêng theo Boot version.

### Các lỗi thường gặp với encoding

- Editor lưu UTF-8 nhưng loader được chỉ định encoding khác.
- File chứa BOM hoặc ký tự escaping không tương thích.
- Dùng custom factory nhưng không chuyển encoding từ `EncodedResource` sang decoder.
- Lẫn `Properties.load(InputStream)` và `Properties.load(Reader)` với semantics khác nhau về ký tự.

Một integration test đọc một property tiếng Việt cụ thể sẽ phát hiện vấn đề sớm hơn test chỉ dùng ký tự ASCII.

## 10. `name` và `factory` là metadata của source, không phải business properties

`@PropertySource` hỗ trợ đặt tên source:

~~~java
@PropertySource(
    name = "companySettings",
    value = "classpath:company.properties")
~~~

Tên `companySettings` là định danh của `PropertySource` trong `MutablePropertySources`, hữu ích khi debug hoặc khi cần `replace`/`addBefore`/`addAfter`.

Ví dụ:

~~~java
MutablePropertySources sources =
    context.getEnvironment().getPropertySources();

PropertySource<?> company = sources.get("companySettings");
~~~

Đây là snippet trong method có `context` kiểu `ConfigurableApplicationContext`; cần imports `MutablePropertySources` và `PropertySource`.

**Không nhầm:** tên PropertySource `companySettings` khác một property key như `app.region`. Một source có thể chứa nhiều keys; ứng dụng vẫn lookup theo key.

`factory = CustomFactory.class` cho phép lựa chọn loader thích hợp, nhưng không tự biến custom loader thành pipeline Config Data của Spring Boot.

## 11. Placeholder trong đường dẫn `@PropertySource`

Có thể viết:

~~~java
@PropertySource(
    "classpath:env/${app.stage:local}/service.properties")
~~~

Spring sẽ resolve `${app.stage:local}` từ các PropertySources **đã được đăng ký trước đó**. Nếu `app.stage` chưa có, dùng `local` làm default.

Ví dụ:

~~~text
-Dapp.stage=prod
-> classpath:env/prod/service.properties
~~~

Nếu không có `app.stage`:

~~~text
-> classpath:env/local/service.properties
~~~

Lưu ý:

1. Placeholder path được resolve khi annotation được xử lý; không phải mỗi lần `getProperty()` sau đó.
2. Không thể dùng một khóa **chỉ có trong chính file chuẩn bị được nạp** để xác định đường dẫn đến file ấy, trừ khi khóa đã có ở nguồn khác từ trước.
3. Placeholder bắt buộc thiếu default có thể khiến refresh thất bại.
4. Đừng đồng nhất cú pháp này với Boot `spring.config.location` hoặc profile-specific file resolver.

**Ví dụ ngắn trên yêu cầu file tương ứng tồn tại trong classpath**. Đây là snippet cho class cấu hình, không phải toàn bộ chương trình.

## 12. Nhiều `@PropertySource` và thứ tự khi trùng khóa

Có thể dùng mảng locations:

~~~java
@Configuration
@PropertySource({
    "classpath:base.properties",
    "classpath:override.properties"
})
public class AppConfig {
}
~~~

Giả sử:

~~~properties
# base.properties
app.mode=base
app.retry=3
~~~

và:

~~~properties
# override.properties
app.mode=override
~~~

Các resources trong `value` được xử lý theo thứ tự khai báo; với việc đăng ký sources theo cơ chế mặc định, source thêm sau có thể thắng cùng key ở source thêm trước. Do đó `app.mode` nhận `override`, còn `app.retry` vẫn là `3`.

### Repeatable annotation và nguy cơ thứ tự bất định

`@PropertySource` là repeatable annotation, có thể có nhiều lần khai báo trên một hoặc nhiều configuration classes. Tuy nhiên, khi nhiều `@Configuration` classes được đăng ký hoặc component-scanned không theo thứ tự tường minh, **đừng dựa vào thứ tự khám phá ngầm** để thiết kế precedence.

Ví dụ hai modules cùng khai báo `@PropertySource` chứa `client.timeout`. Tùy thứ tự configuration classes được xử lý, nguồn đăng ký sau có thể lấn nguồn trước.

Muốn precedence bền vững:

- Gom những resources có quan hệ override trong một configuration entry point với thứ tự rõ.
- Hoặc đặt nguồn qua `ConfigurableEnvironment.getPropertySources()` trước refresh bằng `addFirst`/`addLast`/`addBefore`/`addAfter`.
- Hoặc dùng Boot Config Data có quy tắc precedence và vị trí config được tài liệu hóa.
- Viết integration test khi trùng key, không chỉ test file riêng lẻ.

**Không có một quy tắc “filename theo alphabet thắng”** trong hợp đồng `@PropertySource`.

## 13. Spring Core và Spring Boot có những nguồn mặc định khác nhau

`StandardEnvironment` của Spring Framework Java thuần thường khởi tạo hai sources chính:

1. `systemProperties` (JVM `-Dkey=value`).
2. `systemEnvironment` (OS environment variables).

Theo thứ tự mặc định, JVM system properties có precedence cao hơn environment variables. ApplicationContext cũng có thể có những sources khác tùy web environment và customizations.

Spring Boot **kế thừa Environment nhưng bổ sung nhiều sources**:

- Defaults của `SpringApplication`.
- Sources từ `@PropertySource` khi configuration được xử lý.
- **Config Data** (`application.properties`/`application.yml`, imported files, profile variants).
- OS environment, JVM system properties, command-line arguments.
- `SPRING_APPLICATION_JSON`.
- Test-specific overrides và sources khác theo loại ứng dụng.

Chúng không có cùng thứ tự ưu tiên ở mọi ứng dụng. **Đừng lấy kết quả của `StandardEnvironment` thuần để suy ra kết quả một ứng dụng Boot**.

### Một ví dụ precedence đơn giản

~~~text
@PropertySource:           demo.mode=legacy
application.properties:    demo.mode=file
JVM system property:       -Ddemo.mode=jvm
Spring Boot command-line:  --demo.mode=cli
~~~

Với Boot 3.3 mặc định có command-line property support và không có override/test config cao hơn, `demo.mode=cli` thường thắng.

Nếu chạy ứng dụng Spring Core thuần chỉ có `@PropertySource` và JVM system property, kết quả phụ thuộc vị trí nguồn annotation được thêm vào danh sách, không có Boot command-line integration tự động như trên.

## 14. Điểm đặc biệt của Spring Boot Config Data

Spring Boot có subsystem **Config Data** chịu trách nhiệm phát hiện và nạp nhiều dạng nguồn trước khi context được refresh.

Các khả năng nổi bật:

- Tự tìm `application.properties` và `application.yaml`/`.yml` theo các vị trí mặc định.
- Nạp file theo profile, ví dụ `application-dev.properties`.
- Đọc config bên ngoài JAR và bên trong JAR.
- Nạp `spring.config.import`.
- Xử lý optional locations và các location groups.
- Hỗ trợ nhiều YAML documents và activation metadata.
- Xử lý thứ tự override giữa sources theo chính sách Boot.
- Chuẩn bị Environment trước khi nhiều bước Boot startup sử dụng properties.

**`@PropertySource` không sở hữu các khả năng này như một hệ thống hoàn chỉnh.** Có thể xây thêm code tùy biến bên ngoài annotation, nhưng lúc ấy bạn đang triển khai một loader/bootstrap khác chứ không còn dùng `@PropertySource` mặc định.

## 15. Ví dụ Spring Core hoàn chỉnh: `@PropertySource` nạp hai file và giữ snapshot

Ví dụ đầu tiên chạy **Spring Framework 6.1.13, Java 17** mà không dùng Spring Boot. Hai resource được nạp bằng annotation; một source override source trước. Sau đó thêm một `MapPropertySource` để chứng minh `Environment` có thể thay đổi nhưng object đã nhận config không tự cập nhật.

### 15.1. Cấu trúc project

~~~text
core-property-source-demo/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/example/coreconfig/CorePropertySourceDemo.java
        └── resources/
            ├── defaults223.properties
            └── override223.properties
~~~

### 15.2. File `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>core-property-source-demo</artifactId>
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
                    <mainClass>com.example.coreconfig.CorePropertySourceDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 15.3. `src/main/resources/defaults223.properties`

~~~properties
lesson223.core.mode=base
lesson223.core.retry=3
~~~

### 15.4. `src/main/resources/override223.properties`

~~~properties
lesson223.core.mode=override
lesson223.core.timeout=15
~~~

### 15.5. `src/main/java/com/example/coreconfig/CorePropertySourceDemo.java`

~~~java
package com.example.coreconfig;

import java.util.Map;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

public class CorePropertySourceDemo {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            Environment environment = context.getEnvironment();
            ModeSnapshot snapshot = context.getBean(ModeSnapshot.class);

            System.out.println("core mode=" +
                environment.getRequiredProperty("lesson223.core.mode"));
            System.out.println("core retry=" +
                environment.getRequiredProperty("lesson223.core.retry"));
            System.out.println("core timeout=" +
                environment.getRequiredProperty("lesson223.core.timeout"));
            System.out.println("snapshot=" + snapshot.mode());

            context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource(
                    "runtime-override",
                    Map.of("lesson223.core.mode", "runtime")
                )
            );

            System.out.println("core after override=" +
                environment.getRequiredProperty("lesson223.core.mode"));
            System.out.println("snapshot after override=" + snapshot.mode());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @PropertySource({
        "classpath:defaults223.properties",
        "classpath:override223.properties"
    })
    public static class AppConfig {

        @Bean
        public ModeSnapshot modeSnapshot(Environment environment) {
            return new ModeSnapshot(
                environment.getRequiredProperty("lesson223.core.mode")
            );
        }
    }

    public record ModeSnapshot(String mode) {}
}
~~~

### 15.6. Chạy và output

Cần JDK 17+ và Maven 3.9+. Từ thư mục `core-property-source-demo`:

~~~bash
mvn -q compile exec:java
~~~

Những dòng do `System.out.println` chủ động tạo ra:

~~~text
core mode=override
core retry=3
core timeout=15
snapshot=override
core after override=runtime
snapshot after override=override
~~~

Maven hoặc logging framework có thể in thêm thông tin tùy môi trường.

**Giải thích:**

- `@PropertySource` đưa hai resources vào Environment.
- `override223.properties` được xử lý sau `defaults223.properties` và có cùng key mode, nên `override` thắng.
- Key retry không tồn tại trong override, vì vậy được lấy từ defaults.
- Key timeout chỉ có trong override, nên giá trị là 15.
- `ModeSnapshot` là record được tạo một lần khi bean singleton được khởi tạo, nhận `mode="override"`.
- Khi thêm runtime source ở đầu danh sách, `Environment` thấy `mode="runtime"`.
- Record `ModeSnapshot` không tự đổi state; nó vẫn trả `override`.

**Giới hạn của demonstration:** Việc thêm source sau refresh chỉ minh họa precedence, không phải cơ chế runtime configuration reload hoàn chỉnh. Trong production, nên thiết lập sources cần thiết trước refresh.

## 16. Spring Boot tự nạp `application.properties` và YAML

Trong Boot, bạn có thể tạo:

~~~properties
# src/main/resources/application.properties
shop.mode=standard
shop.port=8080
~~~

hoặc tương đương YAML:

~~~yaml
# src/main/resources/application.yml
shop:
  mode: standard
  port: 8080
~~~

Spring Boot tự tìm các file theo convention và đưa dữ liệu vào `Environment`/Binder khi khởi động. Không cần thêm `@PropertySource("classpath:application.properties")` cho file mặc định.

**Không nên giữ cùng lúc hai file `application.properties` và `application.yml` ở cùng location với các keys trùng rồi dựa vào thứ tự ngẫu nhiên.** Boot docs khuyến nghị chọn một format; khi cả hai cùng tồn tại ở cùng vị trí, `.properties` có precedence cao hơn YAML.

### Những vị trí mặc định phổ biến

Boot tìm config data ở các vị trí trên classpath và bên ngoài ứng dụng, chẳng hạn:

- Classpath root.
- Classpath `/config`.
- Working directory hiện hành.
- `./config/`.
- Một số thư mục con trực tiếp của `./config/` theo conventions được Boot hỗ trợ.

Các nguồn bên ngoài JAR và theo profile có thể ghi đè nguồn bên trong JAR theo precedence của Config Data. **Đừng suy từ danh sách này rằng Boot sẽ quét đệ quy mọi thư mục hoặc mọi tên file**.

## 17. Spring Boot `spring.config.import`: cơ chế khác `@PropertySource`

Một file Boot `application.properties` có thể khai báo:

~~~properties
spring.config.import=optional:file:./deployment.properties
shop.mode=standard
~~~

Nếu `deployment.properties` tồn tại và chứa:

~~~properties
shop.mode=deployment
~~~

thì giá trị từ imported config có thể có precedence cao hơn document khai báo import, theo quy tắc Config Data của Boot.

**Hai chi tiết quan trọng:**

1. `spring.config.import` được xử lý bởi Boot Config Data infrastructure, không bởi `@PropertySource` của Core Spring.
2. Import có thể dùng prefix `optional:` để ứng dụng không fail chỉ vì location optional không tồn tại.

### Import khác `spring.config.location`

`spring.config.import` cho phép một config document **kéo thêm nguồn**.

`spring.config.location` cấu hình **các location dùng để tìm config data** và thường **thay thế các location mặc định**. Khi muốn bổ sung location ngoài defaults, Boot hỗ trợ `spring.config.additional-location`.

Ví dụ tham số bootstrap:

~~~bash
java -jar app.jar --spring.config.additional-location=optional:file:./ops/
~~~

Đây là lệnh minh họa, giả định có Boot JAR runnable `app.jar` và tùy chọn/đường dẫn hợp lệ.

Các thuộc tính ảnh hưởng vị trí config như `spring.config.name`, `spring.config.location` và `spring.config.additional-location` phải có sẵn **đủ sớm** cho Config Data resolver, ví dụ qua OS environment, JVM system properties hoặc command-line. Nếu chỉ đặt chúng vào một file mà Boot cần giá trị đó để tìm chính file ấy, bạn có thể tạo vòng phụ thuộc bootstrap.

### Relative paths và imports

Đường dẫn import có thể được resolve tương đối với config document khai báo nó, tùy loại location. Đừng tự áp quy tắc relative path của `@PropertySource` hoặc Java classpath cho mọi location resolver của Boot.

## 18. Spring Boot hỗ trợ profile-specific config data

Trong Boot, cấu hình thường được chia như:

~~~text
src/main/resources/
  application.properties
  application-dev.properties
  application-prod.properties
~~~

`application.properties`:

~~~properties
app.mode=base
~~~

`application-dev.properties`:

~~~properties
app.mode=development
~~~

`application-prod.properties`:

~~~properties
app.mode=production
~~~

Nếu profile `dev` được active và không có nguồn ưu tiên cao hơn override, Boot nạp file profile-specific phù hợp và `app.mode=development` thường thắng base value.

### Không có nghĩa `@PropertySource` tự nạp file theo tên profile

Nếu dùng Core Spring:

~~~java
@PropertySource("classpath:application.properties")
~~~

điều này **không tự** nạp thêm `application-dev.properties` chỉ vì profile `dev` đang active.

Bạn có thể dùng placeholder trong đường dẫn hoặc dùng `@Profile` để đăng ký configuration khác nhau, nhưng chính bạn phải thiết kế cách load/precedence. Hãy để Boot Config Data xử lý khi đã dùng Spring Boot.

### Nhiều profile active

Boot có quy tắc xử lý thứ tự profile documents, imports và config locations. Không nên suy giá trị bằng cách chỉ nhìn tên các profiles trong `spring.profiles.active` mà bỏ qua source order, imports và external files. Test resolved values bằng profile combinations thật của ứng dụng.

## 19. YAML multi-document và activation là khả năng của Boot Config Data

YAML có thể chứa nhiều documents phân tách bởi `---`. Boot hỗ trợ các khóa kích hoạt config data như `spring.config.activate.on-profile`.

Ví dụ:

~~~yaml
shop:
  mode: base
---
spring:
  config:
    activate:
      on-profile: dev
shop:
  mode: development
~~~

Trong ứng dụng Boot với active profile `dev`, document thứ hai có thể được áp dụng và override base document theo policy phù hợp.

`@PropertySource` mặc định **không parse YAML**, càng không tự làm profile-document activation giống Boot. Ngay cả khi viết custom YAML property source factory, nếu chỉ dùng `YamlPropertiesFactoryBean`, cũng không thể mặc nhiên khẳng định profile activation và Config Data imports được xử lý y hệt Boot.

### Với file `.properties` cũng có multi-document trong Boot

Spring Boot hỗ trợ multi-document properties với dấu phân cách theo quy tắc của Boot. Đừng suy Java `Properties.load(...)` thông thường hiểu profile activation và multi-document; đó là logic của Boot loader.

## 20. Thứ tự ưu tiên trong Spring Boot: khái quát đúng

Spring Boot 3.3 Externalized Configuration Reference mô tả sources theo thứ tự **từ thấp đến cao**, trong đó source xuất hiện sau có thể override source xuất hiện trước.

Một danh sách rút gọn theo độ ưu tiên tăng dần:

1. Default properties của `SpringApplication`.
2. `@PropertySource` từ configuration classes.
3. **Config Data** (file `application.*`, profile variants, imports).
4. Các nguồn khác như random values và OS environment.
5. JVM system properties.
6. `SPRING_APPLICATION_JSON` ở vị trí Boot quy định.
7. Command-line arguments.
8. Một số nguồn test properties/`@DynamicPropertySource`/`@TestPropertySource` và devtools settings, nếu tương ứng có mặt.

**Thứ tự trên là bản rút gọn, không bao gồm đầy đủ Servlet/JNDI/test categories**. Khi cần khẳng định precedence chính xác trong production, xem Boot documentation đúng version và **thứ tự PropertySources tại runtime**.

Một ví dụ giá trị:

~~~text
@PropertySource legacy:   lesson.mode=legacy
application.properties:  lesson.mode=file
JVM -Dlesson.mode=jvm
Boot argument:           --lesson.mode=cli
~~~

Với Boot defaults và không có override nào mạnh hơn command line, kết quả cuối là `cli`.

**Đặc biệt:** Trong `MutablePropertySources` khi duyệt programmatically, thứ tự thường được hiển thị **từ cao xuống thấp**. Tài liệu Boot lại liệt kê các categories theo **thứ tự tăng dần precedence**. Đừng đảo chiều khi debug.

### Config Data nội bộ cũng có precedence

Boot phân biệt file đóng gói trong JAR, file ngoài JAR, file profile-specific, imports và location groups. Ví dụ với mặc định, profile-specific external config có thể override base config đóng trong JAR. Nhưng nếu bạn thay `spring.config.location`, dùng groups hoặc imports, các chi tiết có thể đổi theo cấu hình.

Không cố viết một bảng “mọi file có precedence cố định” khi chưa biết các Boot properties chỉ định vị trí config.

## 21. Ví dụ Spring Boot hoàn chỉnh: Config Data thắng `@PropertySource`

Ví dụ thứ hai chạy Java 17 + **Spring Boot 3.3.5**. Chương trình dùng:

- `application.properties`: Boot tự nạp.
- `spring.config.import=classpath:extra223.properties`: Boot import một file bổ sung.
- `@PropertySource("classpath:legacy223.properties")`: annotation Core Spring thêm một nguồn ưu tiên thấp hơn Config Data trong chính Boot.
- `@ConfigurationProperties`: bind một typed object từ resolved Environment.
- `CommandLineRunner`: in kết quả sau khi application startup chạy đến giai đoạn runners.

### 21.1. Cấu trúc project

~~~text
boot-config-data-demo/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/example/bootconfig/BootConfigDemo.java
        └── resources/
            ├── application.properties
            ├── extra223.properties
            └── legacy223.properties
~~~

### 21.2. File `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>boot-config-data-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 21.3. `src/main/resources/application.properties`

~~~properties
lesson223.boot.mode=application
lesson223.boot.port=8080
spring.config.import=classpath:extra223.properties
~~~

### 21.4. `src/main/resources/extra223.properties`

~~~properties
lesson223.boot.mode=imported
~~~

### 21.5. `src/main/resources/legacy223.properties`

~~~properties
lesson223.boot.mode=legacy
~~~

### 21.6. `src/main/java/com/example/bootconfig/BootConfigDemo.java`

~~~java
package com.example.bootconfig;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

@SpringBootApplication
@PropertySource("classpath:legacy223.properties")
@EnableConfigurationProperties(BootConfigDemo.BootSettings.class)
public class BootConfigDemo {

    public static void main(String[] args) {
        SpringApplication.run(BootConfigDemo.class, args);
    }

    @Bean
    CommandLineRunner report(Environment environment, BootSettings settings) {
        return args -> {
            System.out.println("boot mode=" +
                environment.getRequiredProperty("lesson223.boot.mode"));
            System.out.println("bound mode=" + settings.getMode());
            System.out.println("bound port=" + settings.getPort());
        };
    }

    @ConfigurationProperties(prefix = "lesson223.boot")
    public static class BootSettings {
        private String mode;
        private int port;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }
}
~~~

**Vì sao code chạy được:**

- Spring Boot Starter Parent cung cấp dependency management/plugins phù hợp Java 17.
- `spring-boot-starter` hỗ trợ non-web application, vì project không thêm starter-web.
- `@SpringBootApplication` bật auto-configuration và component scanning cho package chương trình.
- `@EnableConfigurationProperties(BootSettings.class)` đăng ký object cấu hình để Binder gán các fields.
- `BootSettings` là `public static` nested class có public no-arg constructor mặc định và setter/getter; Binder có thể gán mode và port.
- `@PropertySource` vẫn chạy nhưng không có priority cao hơn Config Data của Boot theo precedence mặc định.
- `CommandLineRunner` chỉ in resolved values sau khi Boot đã chuẩn bị Environment và tạo context.

### 21.7. Chạy và stdout

Với JDK 17+ và Maven 3.9+:

~~~bash
mvn -q spring-boot:run
~~~

Các dòng do `System.out.println` chủ động in (trong môi trường không có override bên ngoài cho các keys `lesson223.boot.*`):

~~~text
boot mode=imported
bound mode=imported
bound port=8080
~~~

Spring Boot còn in banner/logs tùy cấu hình, vì vậy đừng mong console chỉ có đúng ba dòng.

**Giải thích:**

1. Boot nạp `application.properties` với `mode=application`, `port=8080`.
2. Config Data processor thấy `spring.config.import=classpath:extra223.properties` và nạp thêm `extra223.properties`.
3. Giá trị imported `mode=imported` có precedence cao hơn document khai báo import.
4. `@PropertySource` cũng thêm `legacy223.properties` với `mode=legacy` trong giai đoạn context refresh, nhưng loại source này có precedence thấp hơn Boot Config Data theo defaults.
5. `Environment` vì vậy trả `mode=imported` và port 8080.
6. `@ConfigurationProperties` bind chính resolved values vào `BootSettings`.

Kết quả này chứng minh **khác biệt về cơ chế và precedence**, không có nghĩa `@PropertySource` bị Boot cấm sử dụng.

### 21.8. Kiểm thử override qua command-line

Dùng một command-line property Boot:

~~~bash
mvn -q spring-boot:run -Dspring-boot.run.arguments=--lesson223.boot.mode=cli
~~~

Các dòng ứng dụng in (khi không có nguồn ưu tiên cao hơn command-line và Maven plugin chuyển tham số đúng):

~~~text
boot mode=cli
bound mode=cli
bound port=8080
~~~

Command-line arguments có precedence cao hơn file config và `@PropertySource` trong Boot mặc định. `@ConfigurationProperties` cũng nhận resolved value `cli`.

**Chú ý cú pháp shell:** Trên Windows PowerShell/các terminal có quy tắc escape riêng, có thể cần đặt toàn bộ `-Dspring-boot.run.arguments=...` trong dấu nháy. Đây là khác biệt cách truyền tham số cho Maven, không phải khác biệt Spring precedence.

## 22. `@Value` và `@ConfigurationProperties` không phải cùng một cơ chế

Cả Spring Framework và Spring Boot đều có thể inject scalar property qua `@Value`:

~~~java
@Component
class ClientAdapter {
    private final String region;

    ClientAdapter(@Value("${client.region:VN}") String region) {
        this.region = region;
    }
}
~~~

Đây là snippet minh họa cần imports `Component` và `Value`. Giá trị được resolve theo `Environment`/placeholder infrastructure tại thời điểm dependency injection.

Spring Boot có abstraction binding mạnh hơn:

~~~java
@ConfigurationProperties(prefix = "client")
public class ClientSettings {
    private String region;
    private int timeout;
    // Getters/setters hoặc constructor binding hợp lệ.
}
~~~

Đây là snippet chưa hoàn chỉnh của một class settings.

### So sánh

| Tiêu chí | `@Value` | `@ConfigurationProperties` |
| --- | --- | --- |
| Thuộc framework | Spring Framework | Spring Boot |
| Phù hợp | Một vài scalar values | Nhóm cấu hình có cấu trúc, nested objects, collections |
| Relaxed binding | Không dùng đúng cùng cơ chế Binder như Boot | Có các quy tắc relaxed binding của Boot |
| Validation | Tự kiểm tra hoặc kết hợp validation riêng | Hỗ trợ tích hợp validation khi cấu hình phù hợp |
| Metadata/IDE | Ít mạnh hơn | Hỗ trợ configuration metadata khi setup phù hợp |
| Source precedence | Dựa trên resolved Environment | Dựa trên cấu hình Boot và Binder trên Environment |

**Không có chuyện đổi `@Value` thành `@ConfigurationProperties` sẽ khiến một `@PropertySource` bất ngờ được nạp sớm hơn.** Đây là hai quyết định độc lập: **lấy dữ liệu từ đâu/khi nào** và **bind dữ liệu vào object như thế nào**.

### Relaxed binding và tên biến môi trường

`@ConfigurationProperties(prefix="client")` có thể bind các cách viết tương ứng của một property theo quy tắc Boot. Nhưng `MapPropertySource` hoặc raw `Environment.getProperty("name")` không nhất thiết xử lý tất cả cách viết y hệt Binder.

Luôn kiểm tra tên key canonical, conventions của Boot version đang chạy và cách source thực tế cung cấp value.

## 23. Những giới hạn khác của `@PropertySource`

### 23.1. Không phải hệ thống runtime reload

Annotation được xử lý khi Spring phân tích configuration. Nếu file được sửa sau khi bean singleton đã tạo, Spring **không tự giám sát file và cập nhật object**. Nếu `@Value` đã inject `timeout=15`, field không tự đổi chỉ vì file trên ổ đĩa được cập nhật.

### 23.2. Không giải quyết Secrets Management

Bạn có thể nạp properties chứa thông tin nhạy cảm, nhưng `@PropertySource` không tự mã hóa, quản lý rotation, cấp quyền truy cập hoặc che dữ liệu trong log. Dùng nguồn bảo mật và cơ chế secret handling phù hợp hệ thống triển khai.

### 23.3. Không tự validate typed config

Nếu file có `timeout=abc` nhưng code yêu cầu `Integer`, lỗi conversion có thể xảy ra khi đọc/bind. `@PropertySource` không tự biết ràng buộc timeout phải dương hoặc tối đa bao nhiêu.

### 23.4. Không tự nạp file theo profile convention

`@PropertySource("classpath:application.properties")` không kéo theo `application-prod.properties` khi profile prod active. Boot Config Data mới có conventions cho profile files.

### 23.5. Không bảo đảm precedence cố định giữa mọi modules

Nhiều configuration classes có `@PropertySource` được xử lý theo discovery/registration ordering. Không nên dựa vào một thứ tự tình cờ của component scanning để quyết định override có ý nghĩa nghiệp vụ.

### 23.6. Không thay thế Config Data import/location bootstrap

`spring.config.import` chỉ có ý nghĩa với Boot Config Data processor. Viết chuỗi này trong một `@PropertySource` file mà **không có Boot** không tự khiến Spring Core đi import thêm resources.

### 23.7. Không tự xử lý wildcard/resource glob

`@PropertySource.value` yêu cầu resources cụ thể, không có built-in scanning wildcard như `classpath*:configs/*.properties` theo contract annotation.

## 24. Khi nào vẫn nên dùng `@PropertySource`?

**Phù hợp:**

- Dự án Spring Framework Core đơn giản, muốn nạp một hoặc vài `.properties` resources đã biết.
- Thư viện Spring cung cấp defaults riêng từ một resource gọn và không cần profile/import phức tạp.
- Cấu hình declarative của `@Configuration` class.
- Một integration module cần properties legacy và có quy tắc precedence đã được kiểm thử.
- Muốn đọc các giá trị qua `Environment` mà không dựng Boot dependency chỉ để tải một file.

**Không phải lựa chọn đầu tiên:**

- Ứng dụng Spring Boot cần đọc các file `application.*` chuẩn.
- Cần profile-specific YAML documents.
- Cần `spring.config.import`, remote config, optional locations hoặc config tree.
- Cần ảnh hưởng logging/`spring.main.*` trước context refresh.
- Cần binding nhiều settings phức tạp qua `@ConfigurationProperties`.
- Cần chính sách externalized config nhất quán giữa dev/staging/prod theo conventions Boot.

Điều này không có nghĩa `@PropertySource` đã “lỗi thời” hay bị deprecate trong mọi trường hợp. Nó là một API có **phạm vi trách nhiệm hẹp**.

## 25. Kiểm tra nguồn nào thắng trong Spring Boot

Khi một key có giá trị lạ, cách chắc chắn nhất là xem **thứ tự PropertySources thực tế** trong context:

~~~java
ConfigurableEnvironment env =
    (ConfigurableEnvironment) context.getEnvironment();

for (PropertySource<?> source : env.getPropertySources()) {
    System.out.println("source=" + source.getName());
}
~~~

Đây là snippet đặt trong method đã có `context` kiểu `ApplicationContext` phù hợp; cần imports `ConfigurableEnvironment` và `PropertySource`.

Nếu key không nhạy cảm:

~~~java
String key = "lesson223.boot.mode";

for (PropertySource<?> source : env.getPropertySources()) {
    Object value = source.getProperty(key);
    if (value != null) {
        System.out.println("first source=" + source.getName());
        break;
    }
}
~~~

Nguồn đầu trả value không null thường quyết định key được resolve. Với composite/origin-aware/configuration-property adapters, việc tra raw values có thể khác tầng Binder, nên diễn giải đúng API đang được dùng.

### Tránh rò secrets

Không nên log toàn bộ `source.getSource()` hoặc mọi resolved property values. Danh sách có thể chứa password, token, private keys và endpoints nhạy cảm. Nên log **tên source**, kiểm tra một key được phép, và mask giá trị nếu cần.

Trong Spring Boot Actuator, endpoint `env`/`configprops` có thể hỗ trợ diagnostics nhưng cần security, exposure policy và sanitization. Không mở các endpoints nội bộ đó cho public traffic chỉ để debug một property.

## 26. Sai lầm: đưa `logging.level.*` vào một `@PropertySource` rồi mong Boot bootstrap dùng nó

Ví dụ:

~~~java
@SpringBootApplication
@PropertySource("classpath:logging-overrides.properties")
public class MyApplication {
}
~~~

`logging-overrides.properties`:

~~~properties
logging.level.com.example=DEBUG
spring.main.banner-mode=off
~~~

Đây là snippet minh họa cho một Boot application, chưa đầy đủ thành dự án runnable. Spring Boot thường đọc một số nhóm `logging.*` và `spring.main.*` **trước** khi `@PropertySource` trên configuration class được thêm trong context refresh.

Vì vậy, có thể quan sát:

- Sau refresh, Environment nhìn thấy các keys trong source annotation.
- Nhưng logging/banner/main configuration đã được Boot xử lý từ giai đoạn sớm hơn.
- Thay đổi `@PropertySource` không nhất thiết tác động ngược lại các subsystems đã initialized.

**Cách đúng:** Đặt những values bootstrap trong Boot Config Data, JVM/system environment, command-line hoặc điểm mở rộng environment bootstrap đúng contract. Đừng coi `@PropertySource` là nguồn có quyền can thiệp mọi setting từ trước startup.

## 27. Cấu hình tùy biến sớm: Core và Boot có extension points khác

Nếu cần nguồn giá trị mới **trước refresh**, trong Core Spring có thể thao tác với `ConfigurableEnvironment`:

~~~java
AnnotationConfigApplicationContext context =
    new AnnotationConfigApplicationContext();

context.getEnvironment().getPropertySources().addFirst(
    new MapPropertySource(
        "myOverrides",
        Map.of("client.timeout", "20")
    )
);

context.register(MyConfig.class);
context.refresh();
~~~

Đây là snippet minh họa yêu cầu `MyConfig` cùng imports và cleanup `context.close()` phù hợp. Điều quan trọng là **thêm source trước khi refresh**.

Spring Boot có thể dùng:

- `EnvironmentPostProcessor` cho việc tùy biến Environment trước phần lớn application startup.
- Custom Config Data `ConfigDataLocationResolver`/`ConfigDataLoader` khi cần tích hợp location schemes riêng với bootstrap loader.
- `SpringApplication.setDefaultProperties(...)` khi chỉ cần defaults thấp ưu tiên.
- `ApplicationContextInitializer` trong những trường hợp phù hợp, nhưng không thay thế những phase Boot cần trước khi context được tạo/refresh.

**Không nhầm:** `BeanFactoryPostProcessor` xử lý bean definitions trong context refresh, thường không phải nơi thích hợp để ép nạp những giá trị Boot logging cần trước startup.

### Remote configuration

Nếu phải tải config từ network, cần thiết kế timeout, retry, fallback, cache, secret handling và thứ tự startup. Một `@PropertySource` mặc định không tự giải quyết các vấn đề này; Boot Config Data custom resolver/loader hoặc nền tảng cấu hình riêng có thể phù hợp hơn.

## 28. Cách chọn giữa YAML, properties và typed binding

Tình huống | Chọn phù hợp | Lý do
--- | --- | ---
Một file nhỏ, Spring Core thuần | `@PropertySource("classpath:settings.properties")` | API có sẵn, rõ resource
Một file legacy XML properties | `@PropertySource("classpath:settings.xml")` | Factory mặc định hỗ trợ XML Java properties
YAML trong Boot | `application.yml` qua Config Data | Loader/conventions của Boot
YAML trong Core thuần | Custom PropertySourceFactory hoặc nạp qua YAML helper phù hợp | Không hỗ trợ trực tiếp mặc định
Nhiều file phụ thuộc profile trong Boot | `application-{profile}.*`, `spring.config.import` | Boot tự xử lý activation/order
Config externalized của vận hành | Boot Config Data locations/environment/CLI | Override rõ ràng, phù hợp deployment
Một vài scalar values | `Environment` hoặc `@Value` | Đơn giản, ít mã
Nhóm cấu hình phức tạp Boot | `@ConfigurationProperties` | Typed binding, validation/nested config
Bootstrap logging/main settings | Nguồn được Boot đọc sớm | `@PropertySource` có thể quá muộn
Cần nguồn cấu hình tự thiết kế | `PropertySource` + bootstrap hook | Kiểm soát loader/precedence
Cần config reload runtime | Thiết kế reload mechanism riêng | Annotation không watch/rebind

Cách chọn nên bắt đầu từ **bạn dùng Spring Framework thuần hay Spring Boot**, rồi xét **thời điểm cần property**, **định dạng nguồn**, **quy tắc precedence**, và **cách bind vào bean**.

## 29. Lỗi thường gặp và cách khắc phục

| Hiện tượng | Nguyên nhân thường gặp | Hướng xử lý |
| --- | --- | --- |
| Core không thấy `application.properties` | Không có loader/convention của Boot | Khai báo `@PropertySource` hoặc đăng ký source |
| `@PropertySource("*.yml")` không nạp đúng key | Factory mặc định không parse YAML | Dùng Boot Config Data hoặc custom factory |
| File properties không tìm được | Resource path/classpath sai | Xác nhận `src/main/resources` được build và path phù hợp |
| Wildcard không nạp nhiều file | Annotation yêu cầu explicit locations | Liệt kê files hoặc bootstrap resource scanner riêng |
| Thiếu file làm context fail | `ignoreResourceNotFound` mặc định false | Chỉ đặt true khi file thật sự optional |
| Giá trị tiếng Việt bị lỗi | Encoding không tương thích | `encoding="UTF-8"` và lưu đúng charset |
| Placeholder trong resource path thất bại | Key cần thiết chưa có trước khi annotation xử lý | Đặt key ở bootstrap source hoặc default rõ |
| Hai `@PropertySource` override khó đoán | Configuration class processing order không được kiểm soát | Quy định order và integration test |
| `logging.*` trong annotation không hoạt động như kỳ vọng | Boot đọc bootstrap settings trước context refresh | Đưa vào Boot Config Data/system/env/CLI |
| `spring.config.import` không có tác dụng trong Core | Đây là Boot Config Data key | Dùng Boot hoặc nạp resource thủ công |
| Boot `application.yml` không được load | Sai location, tên, profile hoặc config location override | Kiểm tra Boot location/import rules |
| Boot config có giá trị khác file | OS/JVM/CLI/test source precedence cao hơn | Duyệt PropertySources và nguồn thắng |
| Profile file không hoạt động trong Core | Core annotation không có Boot profile convention | Explicit `@PropertySource`/`@Profile` hoặc Boot |
| `@Value` field không cập nhật khi file sửa | Injection là snapshot, không watch file | Thiết kế reload/rebind có kiểm soát |
| `@ConfigurationProperties` không bind | Bean chưa được enable/register hoặc prefix/setter không đúng | `@EnableConfigurationProperties`/scan, cấu hình kiểu phù hợp |
| Properties và YAML cùng vị trí cho kết quả khác mong đợi | Boot khuyến nghị chỉ một format, properties có precedence cao hơn khi cùng location | Chọn một format, xác nhận runtime order |
| Custom factory chạy nhưng thiếu profile activation | Custom loader không phải Config Data | Dùng Boot profile-aware loader |
| Debug làm lộ secrets | In toàn bộ PropertySources | Chỉ in source names và mask keys |

## 30. Quy trình chẩn đoán cấu hình sai

**Bước 1: Xác định stack.** Đây là Spring Framework Core thuần hay Spring Boot? Có Spring Cloud, custom sources hoặc parent/child contexts không?

**Bước 2: Xác định key được đọc lúc nào.** Có phải logging/main bootstrap property? Hay được bind vào một singleton khi context refresh?

**Bước 3: Xác nhận resource loader.** File nằm ở classpath/file path nào? Là `.properties`, XML properties hay YAML? Có explicit `@PropertySource` không?

**Bước 4: Nếu dùng `@PropertySource`, kiểm tra annotations.** `value`, `ignoreResourceNotFound`, `encoding`, `factory`, placeholder path và thứ tự resources.

**Bước 5: Nếu dùng Boot, kiểm tra Config Data.** `application.*`, file theo profile, external locations, `spring.config.import` và optional locations.

**Bước 6: Đọc thứ tự sources thực tế.** Duyệt `Environment.getPropertySources()` từ precedence cao xuống thấp, không đoán từ thứ tự file trên disk.

**Bước 7: Kiểm tra source thắng có value hợp lệ.** Cùng key có ở nơi khác không? Giá trị có bị chuỗi rỗng, typo hoặc conversion lỗi?

**Bước 8: Kiểm tra binding.** `@Value` hay `@ConfigurationProperties`? Bean có được Spring tạo không? Type/prefix có phù hợp?

**Bước 9: Kiểm tra runtime changes.** File có bị sửa sau khi bean được tạo không? Có cơ chế refresh riêng hay vẫn dùng snapshot cũ?

**Bước 10: Viết test tái hiện.** Một test Core kiểm tra `@PropertySource`, một test Boot kiểm tra precedence của Config Data và override bằng CLI.

## 31. Câu hỏi tự kiểm tra

**Câu 1.** Chỉ thêm file `application.properties` vào `src/main/resources` trong Spring Framework thuần có bảo đảm Environment tự đọc không?

**Đáp án:** Không. Core Spring cần một cơ chế đăng ký PropertySource như `@PropertySource` hoặc programmatic bootstrap. Boot mới cung cấp convention auto-load `application.*`.

**Câu 2.** `@PropertySource("classpath:app.yml")` có tự parse YAML trong Spring Framework không?

**Đáp án:** Không với factory mặc định. Có thể viết custom `PropertySourceFactory` hoặc dùng Boot Config Data khi ứng dụng dùng Boot.

**Câu 3.** Vì sao `@PropertySource` không nên là nơi duy nhất đặt Boot `logging.level.*`?

**Đáp án:** Annotation được xử lý khi context refresh, còn một số Boot logging/main settings được đọc sớm hơn, nên source có thể xuất hiện quá muộn.

**Câu 4.** `@PropertySource(ignoreResourceNotFound=true)` có tác dụng gì?

**Đáp án:** Cho phép bỏ qua trường hợp resource không tìm được theo hợp đồng annotation. Chỉ nên dùng cho resource optional, không phải để che lỗi cấu hình bắt buộc.

**Câu 5.** `@PropertySource` có tự nạp `application-dev.properties` khi profile dev đang active không?

**Đáp án:** Không theo Boot conventions. Muốn dùng Core, phải chỉ rõ location hoặc một cấu hình conditional khác; Boot Config Data có quy tắc profile-specific files.

**Câu 6.** Boot có tự ưu tiên imported file hơn file khai báo `spring.config.import` không?

**Đáp án:** Theo cơ chế Config Data của Boot, imported document được chèn với precedence có thể override document khai báo import. Cần xét các nguồn khác có precedence cao hơn và policies của Boot version.

**Câu 7.** Một `@PropertySource` có `app.mode=legacy`, Boot `application.properties` có `app.mode=app`. Trong Boot mặc định, giá trị nào thắng?

**Đáp án:** Config Data từ `application.properties` có precedence cao hơn nguồn `@PropertySource`, nên `app` thắng nếu không có nguồn ưu tiên cao hơn khác.

**Câu 8.** Thay `@Value` bằng `@ConfigurationProperties` có khiến source được nạp sớm hơn không?

**Đáp án:** Không. Binder và source registration/timing là hai cơ chế khác nhau.

**Câu 9.** Đổi `MapPropertySource` sau refresh có cập nhật record settings đã được constructor inject không?

**Đáp án:** Không tự động. Record giữ snapshot từ khi bean được tạo; runtime reload cần giải pháp riêng.

**Câu 10.** Nếu cần quản lý config sớm cho một provider tùy biến trong Boot, dùng gì thay vì ép `@PropertySource`?

**Đáp án:** Có thể dùng Boot `EnvironmentPostProcessor` hoặc Config Data custom resolver/loader phù hợp. Cần chú ý lifecycle, precedence, bảo mật và failure policies.

## 32. Checklist code review

- [ ] Dự án là Spring Core hay Boot, và cơ chế loader đã được chọn đúng?
- [ ] Resource có định dạng mà loader hỗ trợ (`.properties`/XML vs YAML)?
- [ ] Đường dẫn file và classpath đã được kiểm chứng sau đóng gói JAR?
- [ ] Những file optional và required có được phân biệt rõ?
- [ ] Encoding đã phù hợp, đặc biệt với văn bản tiếng Việt?
- [ ] Placeholder trong annotation location có source từ trước không?
- [ ] Quy tắc precedence của nhiều nguồn có được xác định và test không?
- [ ] Các nhóm Boot `logging.*`/`spring.main.*` có được cấu hình đủ sớm?
- [ ] `spring.config.import` và `spring.config.location` có được dùng đúng vai trò?
- [ ] Profile-specific files có được kích hoạt qua đúng cơ chế?
- [ ] Có trùng `application.properties` và YAML cùng location không?
- [ ] `@ConfigurationProperties` có được đăng ký/bind/validate đúng không?
- [ ] Thông tin nhạy cảm có được bảo vệ khi dump PropertySources/logs?
- [ ] Runtime update có làm Environment và singleton snapshots lệch nhau không?
- [ ] Đã có integration test xác nhận resolved values và nguồn thắng chưa?

## 33. Tóm tắt ứng dụng

`@PropertySource` là công cụ đơn giản và hữu ích của Spring Framework: nó **đưa tài nguyên properties vào Environment trong quá trình xử lý configuration classes**. Nó phù hợp với những file cụ thể, ít phức tạp và không cần năng lực cấu hình bootstrap mở rộng.

Những **giới hạn cần nhớ** là: factory mặc định không parse YAML; annotation không tự quét wildcard, không tự nạp profile files theo Boot conventions; thứ tự giữa nhiều configuration classes có thể không là chính sách ổn định; không watch/rebind runtime; và **xuất hiện quá muộn đối với một số thuộc tính mà Spring Boot cần trước refresh**.

Spring Boot Config Data bổ sung **file discovery, YAML, profiles, imports, external locations và precedence** được thiết kế cho triển khai nhiều môi trường. Khi dùng Boot, hãy để `application.*`/Config Data quản lý phần cấu hình thông thường và chọn `@ConfigurationProperties` cho typed configuration lớn.

**Quy tắc chọn nhanh:** Một file properties cần nạp trong Spring Core thuần → `@PropertySource`. Một ứng dụng Boot cần cấu hình toàn diện theo môi trường/đường dẫn/profile → **Config Data**, không lạm dụng `@PropertySource`.

## 34. Tài liệu Spring chính thức

- [Spring Framework — Environment Abstraction / Using @PropertySource](https://docs.spring.io/spring-framework/reference/core/beans/environment.html): cơ chế PropertySource và ví dụ annotation với Environment.
- [Spring Framework 6.1 — PropertySource Annotation Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/annotation/PropertySource.html): `value`, `name`, `encoding`, `factory`, `ignoreResourceNotFound`, placeholder và giới hạn location.
- [Spring Framework 6.1 — PropertySourceFactory Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/io/support/PropertySourceFactory.html): extension point loader tùy biến.
- [Spring Framework 6.1 — YamlPropertiesFactoryBean Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/beans/factory/config/YamlPropertiesFactoryBean.html): nạp YAML và flatten sang Properties.
- [Spring Boot 3.3 — Externalized Configuration](https://docs.spring.io/spring-boot/3.3/reference/features/external-config.html): Config Data, YAML, profile locations, imports, precedence và giới hạn `@PropertySource` đối với các thiết lập sớm.
- [Spring Boot 3.3 — Type-safe Configuration Properties](https://docs.spring.io/spring-boot/3.3/reference/features/external-config.html#features.external-config.typesafe-configuration-properties): Binder, JavaBean/constructor binding và cấu hình có cấu trúc.
- [Spring Boot — ConfigDataLocationResolver Javadoc](https://docs.spring.io/spring-boot/3.3/api/java/org/springframework/boot/context/config/ConfigDataLocationResolver.html): tích hợp location resolver cho nguồn config đặc biệt.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-environment-008">Tính thời điểm khi thêm nguồn thuộc tính trước refresh</a>

<details>
<summary>Xem chi tiết</summary>

# Tính thời điểm khi thêm nguồn thuộc tính trước refresh trong Spring Core Container

## 1. Mục tiêu và kết luận cốt lõi

Trong Spring Framework, `Environment` là abstraction giúp container quản lý hai nhóm dữ liệu có liên quan: **profiles** và **properties**. Thông qua `ConfigurableEnvironment.getPropertySources()`, ứng dụng có thể thêm, thay thế, bỏ hoặc đổi độ ưu tiên của các nguồn thuộc tính như system properties, environment variables, file properties hoặc `MapPropertySource` tùy biến.

Vấn đề quan trọng không chỉ là **có thêm property source hay không**, mà là **thêm vào thời điểm nào so với việc container xử lý cấu hình và tạo bean**.

**Quy tắc chính:** khi một `Environment` thuộc `ApplicationContext`, hãy thực hiện mọi thay đổi `PropertySource` cần ảnh hưởng đến **bootstrap** trước khi gọi `ApplicationContext.refresh()`. Tốt hơn nữa, nếu dữ liệu tham gia quyết định chọn bean/configuration hoặc nội dung khi quét component, hãy chuẩn bị Environment **trước cả bước đăng ký/scan những cấu hình phụ thuộc dữ liệu ấy**, để không gặp các bước đọc sớm.

Tài liệu Javadoc của `ConfigurableEnvironment` nêu rõ yêu cầu thao tác PropertySources trước `refresh()` nhằm bảo đảm các nguồn có mặt trong toàn bộ bootstrap, bao gồm các property-placeholder configurers.

Đối lập với điều đó, **thêm PropertySource sau refresh vẫn có thể thay đổi những lần gọi `environment.getProperty(key)` về sau**, nhưng **không tự động rollback rồi chạy lại** việc chọn `@Profile`, đăng ký `@Configuration`, resolving placeholder đã thực hiện hoặc inject lại `@Value` vào singleton đang tồn tại.

### Mốc thời gian để ghi nhớ

~~~text
Tạo ApplicationContext chưa refresh
     │
     ├─ lấy ConfigurableEnvironment
     ├─ thêm MapPropertySource / nguồn external
     ├─ xếp thứ tự ưu tiên nguồn
     ├─ setActiveProfiles / setDefaultProfiles nếu cần
     ├─ register(...) / scan(...) cấu hình
     │
     ▼
refresh()
     ├─ chuẩn bị và xử lý BeanDefinition
     ├─ giải quyết @Configuration, @Profile, @PropertySource
     ├─ chạy BFPP / placeholder configurers
     ├─ đăng ký BeanPostProcessors
     ├─ tạo các eager singleton
     │    └─ @Value, constructor argument, property values được resolve
     └─ hoàn thành lifecycle/events phù hợp
     │
     ▼
Sử dụng ApplicationContext
     ├─ Environment.getProperty(...) đọc theo nguồn hiện có
     ├─ @Value đã inject vào singleton không tự refresh
     └─ Bean tạo mới về sau có thể resolve từ Environment mới
         (tùy đường resolve và cấu hình thực tế)
~~~

Sơ đồ trình bày flow điển hình của Spring Core Container; không có nghĩa **mọi loại giá trị** đều được resolve đúng một lần trong `refresh()`. Một số bean lazy/prototype chỉ được tạo sau đó; custom resolver hoặc external configuration mechanism có các hợp đồng riêng.

## 2. Chỉ cần ba API để hiểu tác động của thời điểm

`Environment` cho phép đọc properties và profiles. `ConfigurableEnvironment` cho phép chỉnh danh sách `MutablePropertySources` và active/default profiles; mỗi `PropertySource` có tên và cung cấp giá trị cho từng key. `MapPropertySource` phù hợp để thêm nguồn in-memory từ một `Map<String,Object>`.

```java
var context = new AnnotationConfigApplicationContext();
context.getEnvironment().getPropertySources().addFirst(
        new MapPropertySource(
                "bootstrapOverrides",
                Map.<String, Object>of("training.endpoint", "https://internal.example")));
context.register(AppConfig.class);
context.refresh();
```

Đây là snippet, cần imports `AnnotationConfigApplicationContext`, `MapPropertySource`, `java.util.Map` và class `AppConfig`; caller cần đóng context khi xong. Vấn đề của bài là **thêm source trước hay sau `refresh()`**, vì nhiều thành phần đã resolve property khi bootstrap.

Các API `addFirst`/`addLast`/`replace`/`addBefore`/`addAfter`/`remove` được giới thiệu và thử nghiệm chi tiết trong bài `MutablePropertySources`; ở đây chú ý thứ tự thao tác so với việc phân tích cấu hình và tạo bean.

## 3. Vì sao phải thêm nguồn trước refresh?

`refresh()` là thời điểm Spring chuyển từ **container đang được cấu hình** sang **container đã xử lý bean definitions và khởi tạo bean**. Nhiều quyết định trong quá trình ấy tiêu thụ property:

1. **Configuration parsing:** những `@Configuration`, `@Import`, `@PropertySource` và condition tương ứng được xem xét theo phase của configuration processor.
2. **Profile matching:** `@Profile` có thể khiến bean definition được đăng ký hoặc bỏ qua. Quyết định đã được xử lý không tự phát lại khi giá trị profile thay đổi.
3. **Placeholder resolution:** `PropertySourcesPlaceholderConfigurer` hoặc embedded value resolver có thể giải quyết `${key}` trong metadata và injection points.
4. **Factory method/constructor arguments:** `@Value` được resolve khi Spring tạo một bean/factory argument tương ứng.
5. **Required property validation:** nếu container/initializer gọi kiểm tra các key bắt buộc, key phải sẵn có tại thời điểm kiểm tra.
6. **Infrastructure initialization:** một số processor, resource loader hoặc SDK clients được tạo sớm có thể đọc Environment để xác định cấu hình.

Một nguồn được thêm sau khi **một trong những bước đó đã hoàn thành** không làm Spring tự quay lại và re-run các quyết định trước đó. Kết luận chính xác là **thời điểm tiêu thụ từng giá trị** quyết định ảnh hưởng của source mới; `refresh()` là ranh giới quan trọng để bảo đảm nguồn được cung cấp kịp cho toàn bộ bootstrap.

### Có cần thêm nguồn trước register(...)?

Trong `AnnotationConfigApplicationContext`, cách an toàn nhất cho cấu hình nhạy với profile/condition là:

~~~java
var context = new AnnotationConfigApplicationContext();

// 1. Chuẩn bị Environment.
context.getEnvironment().getPropertySources().addFirst(customSource);
context.getEnvironment().setActiveProfiles("dev");

// 2. Đăng ký cấu hình.
context.register(AppConfig.class);

// 3. Bắt đầu xử lý/khởi tạo.
context.refresh();
~~~

Đây là snippet, cần `customSource` và `AppConfig`. Đối với một số configuration metadata/conditional paths, quyết định có thể được thực hiện trong quá trình registration/scan chứ không phải chờ đến khi mọi bước của refresh chạy. Vì vậy “**trước refresh**” là mốc bắt buộc chung, còn “**trước register/scan**” là nguyên tắc thận trọng bổ sung để tránh giá trị được đọc sớm.

Nếu thay `getEnvironment()` bằng `setEnvironment(customEnvironment)`, thiết lập Environment trước `register/scan` để các thành phần reader/scanner sử dụng đúng Environment. `AnnotationConfigApplicationContext.setEnvironment(...)` được thiết kế để truyền Environment tới các công cụ cấu hình liên quan.

## 4. Lỗi thường gặp: dùng constructor tự refresh rồi mới thay nguồn

`AnnotationConfigApplicationContext` có nhiều constructor:

~~~java
new AnnotationConfigApplicationContext();
new AnnotationConfigApplicationContext(AppConfig.class);
~~~

Hai cách tạo **khác về thời điểm**:

- Constructor **không tham số** tạo context chưa refresh; ta còn cơ hội cấu hình property sources, profiles, đăng ký bean rồi chủ động `refresh()`.
- Constructor nhận **configuration classes** đăng ký những class đó rồi **tự gọi refresh trước khi constructor trả về**. Khi bạn giữ reference trả về, context đã đi qua bootstrap.

Ví dụ cần tránh nếu property quyết định startup:

~~~java
var context = new AnnotationConfigApplicationContext(AppConfig.class);
context.getEnvironment().getPropertySources().addFirst(source);
// Quá muộn đối với config đã được xử lý trong constructor.
~~~

Đoạn này là phản ví dụ; `source`/`AppConfig` cần được định nghĩa. Thêm nguồn ở đây vẫn làm `Environment` có thể đọc giá trị mới về sau, nhưng không bảo đảm inject lại `@Value` hoặc thay bean definitions đã quyết định.

**Cách sửa:**

~~~java
try (var context = new AnnotationConfigApplicationContext()) {
    context.getEnvironment().getPropertySources().addFirst(source);
    context.register(AppConfig.class);
    context.refresh();
    // Bắt đầu sử dụng bean sau refresh.
}
~~~

Các snippet này minh họa trình tự đúng; khi viết demo độc lập đầy đủ ở mục 8 sẽ cung cấp imports, code, Maven và stdout xác định.

## 5. Chọn đúng precedence **trước khi** refresh

Đưa source vào context sớm chưa đủ nếu nguồn đặt ở sai vị trí. `addFirst(custom)` khiến custom source có precedence cao hơn nguồn đang có; `addLast(custom)` tạo một fallback. Với `StandardEnvironment`, JVM system properties mặc định đứng trước OS environment variables, nhưng các context khác có thể đăng ký thêm sources.

```properties
# Nguồn thấp
feature.timeout-ms=5000
feature.mode=safe
```

```properties
# Nguồn cao
feature.timeout-ms=1000
```

Resolver trả `feature.timeout-ms=1000`, còn `feature.mode=safe` vẫn lấy từ nguồn dưới. Source cao **không xóa toàn bộ keys** ở source thấp. Giá trị `null` không phải tombstone để chặn lookup; nếu cần logic xóa/override tùy chỉnh phải thiết kế resolver tương ứng.

Đặt tên source ổn định như `courseOverrides` hoặc `courseDefaults` để các phép `replace`/`addBefore` được dự đoán. Khi debug, chỉ quan sát tên nguồn hoặc metadata đã duyệt; tuyệt đối tránh in token, mật khẩu, kết nối có credentials hay toàn bộ môi trường ra log.

## 6. Điều gì xảy ra khi thêm PropertySource sau refresh?

Có ít nhất ba nhóm hành vi phải phân biệt:

| Cách tiêu thụ thuộc tính | Đã xảy ra trước thay đổi? | Thêm source sau refresh ảnh hưởng ra sao? |
| --- | --- | --- |
| `environment.getProperty("x")` được gọi **sau** thay đổi | Không | Thông thường đọc theo danh sách nguồn mới |
| Field/constructor arg `@Value("${x}")` của singleton eager đã tạo | Có | Giá trị đã inject **không được tự inject lại** |
| `@Profile`/condition đã quyết định bean definitions | Có | Không tự đăng ký/hủy những beans theo profile mới |
| `@Bean` method của eager singleton đọc Environment khi tạo | Có | Object đã tạo không tự chạy lại factory method |
| Prototype/lazy bean **chưa tạo** và có `@Value` | Chưa | Khi được tạo, có thể resolve bằng nguồn mới nếu pipeline resolver đọc Environment hiện tại |
| BFPP đã thay metadata/resolve placeholders | Có | Metadata đã thay không tự được xử lý lại |
| Service code giữ reference `Environment` và lookup mỗi lần xử lý request | Mỗi lần lookup riêng | Có thể đọc giá trị mới; phải xử lý consistency/concurrency |
| Cache/config snapshot xây từ Environment tại startup | Đã snapshot | Không tự cập nhật cache/snapshot |
| Property resolver do ứng dụng tạo từ một tập source cố định | Tùy implementation | Có thể giữ tập/giá trị cũ; phải hiểu contract cụ thể |

**Không nên tuyên bố chung “sau refresh, mọi property source thay đổi đều vô hiệu”**: Environment live vẫn có thể trả giá trị mới. Nhưng cũng không nói “thay nguồn lập tức đổi toàn bộ bean” vì Spring không có cơ chế automatic rebinding toàn bộ singleton của Core Container.

### Ví dụ ngắn về thời điểm

~~~text
T0: highSource ghi course.mode = "early"
T1: refresh() tạo singleton A, @Value nhận "early"
T2: addFirst(lateSource), course.mode = "late"
T3: environment.getProperty("course.mode") -> "late"
T4: singleton A.value -> "early" (đã inject)
T5: tạo prototype B sau T2; @Value có thể resolve -> "late"
~~~

Chữ “có thể” ở prototype biểu thị **điều kiện resolver thực sự lấy nguồn Environment mới**. Trong ví dụ Java 17 đầy đủ, ta dùng cấu hình chuẩn Spring để kiểm chứng kết quả này.

## 7. @PropertySource và giới hạn của declarative configuration

Annotation `@PropertySource` trên `@Configuration` thêm một source từ file resource:

~~~java
@Configuration
@PropertySource("classpath:course.properties")
public class CourseConfig {
}
~~~

Giả sử `course.properties` có:

~~~properties
course.message=Hello
course.timeout-ms=2500
~~~

Các đoạn trên là snippet; ứng dụng phải đóng gói file resource đúng vị trí trong classpath. Spring xử lý `@PropertySource` trong quá trình xử lý configuration class, **không phải lúc JVM vừa tải class**.

### 7.1. Placeholder ngay trong đường dẫn @PropertySource

~~~java
@Configuration
@PropertySource("classpath:settings/${course.region:default}/course.properties")
public class RegionalConfig {
}
~~~

Spring resolve placeholder `${course.region:default}` dựa trên **PropertySources đã được đăng ký** ở thời điểm xử lý annotation. Do đó nếu `course.region` chỉ được thêm **sau khi RegionalConfig đã được parse**, đường dẫn resource không tự đổi lại.

**Ứng dụng:** các key dùng để chọn **vị trí file cấu hình** nên tồn tại trong Environment đủ sớm. Thực hiện early configuration trong code bootstrap, JVM system properties hoặc cơ chế chuẩn bị environment của framework.

### 7.2. @PropertySource không phải cơ chế cấu hình mọi subsystem sớm

Trong Spring Core Container thông thường, `@PropertySource` hữu ích để bổ sung properties cho bean construction. Nhưng một số dữ liệu được đọc **trước** khi annotation được xử lý. Với Spring Boot, tài liệu nêu cụ thể rằng `@PropertySource` được thêm trong quá trình refresh, nên **quá muộn** cho các property như `logging.*` và `spring.main.*` vốn được tiêu thụ trước refresh.

Điều này không có nghĩa `@PropertySource` luôn sai. Nó sai **thời điểm** khi dùng để thay những settings đã được subsystem khác xử lý từ trước.

### 7.3. Hai annotation có thể tạo conflict thứ tự

Nếu nhiều cấu hình dùng `@PropertySource` và cùng chứa key, thứ tự áp dụng/phát hiện cấu hình có thể ảnh hưởng precedence trong những tình huống cụ thể. Không dùng thứ tự đọc source code để bảo đảm một value thắng. Đối với override quan trọng, dùng một bootstrap step với `MutablePropertySources.addFirst/addBefore` và test precedence rõ ràng.

### 7.4. File không tồn tại

Resource không tồn tại có thể gây lỗi đọc config trong refresh; `@PropertySource(ignoreResourceNotFound = true)` cho phép bỏ qua một số trường hợp theo contract. Cần phân biệt file optional với file critical: nếu cấu hình bắt buộc mà bị bỏ qua, ứng dụng có thể khởi tạo với default không đúng.

## 8. Ví dụ Java 17 chạy được: thêm sớm, thay muộn và prototype

Demo dưới đây dùng **Java 17**, **Maven** và **Spring Framework 6.2.19** thuần, không dùng Spring Boot. Nó minh họa:

1. Tạo `AnnotationConfigApplicationContext` chưa refresh.
2. Thêm hai `MapPropertySource` **trước refresh**, một có ưu tiên thấp và một ưu tiên cao.
3. Tạo singleton `Snapshot` với `@Value`; giá trị được chốt lúc bean tạo.
4. Sau refresh, thêm nguồn cao hơn với giá trị mới.
5. `Environment` đọc giá trị mới, singleton cũ **không tự re-inject**.
6. Bean prototype được tạo sau thao tác và resolve `@Value` bằng Environment hiện hành trong pipeline mặc định của demo.

### Cấu trúc thư mục

~~~text
property-source-timing/
├── pom.xml
└── src/main/java/example/PropertySourceTimingDemo.java
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>property-source-timing</artifactId>
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

### src/main/java/example/PropertySourceTimingDemo.java

~~~java
package example;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

public final class PropertySourceTimingDemo {

    private static final String KEY = "course.demo.message";

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext()) {

            // Environment được cấu hình trước register/refresh.
            MutablePropertySources sources =
                    context.getEnvironment().getPropertySources();

            sources.addLast(new MapPropertySource(
                    "courseFallback",
                    Map.<String, Object>of(KEY, "LOW")));

            sources.addFirst(new MapPropertySource(
                    "courseStartupOverride",
                    Map.<String, Object>of(KEY, "EARLY")));

            System.out.println("Before refresh: "
                    + context.getEnvironment().getProperty(KEY));

            context.register(Config.class);
            context.refresh();

            SingletonSnapshot singleton =
                    context.getBean(SingletonSnapshot.class);

            System.out.println("Injected singleton: " + singleton.value());

            // Thay Environment sau khi singleton đã được tạo.
            sources.addFirst(new MapPropertySource(
                    "courseRuntimeOverride",
                    Map.<String, Object>of(KEY, "LATE")));

            System.out.println("Environment after update: "
                    + context.getEnvironment().getProperty(KEY));

            System.out.println("Existing singleton: "
                    + singleton.value());

            // Prototype chưa được tạo trong refresh, nên resolve muộn hơn.
            PrototypeSnapshot prototype =
                    context.getBean(PrototypeSnapshot.class);
            System.out.println("New prototype: " + prototype.value());
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        public SingletonSnapshot singletonSnapshot(
                @Value("${course.demo.message:DEFAULT}") String value) {
            return new SingletonSnapshot(value);
        }

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public PrototypeSnapshot prototypeSnapshot(
                @Value("${course.demo.message:DEFAULT}") String value) {
            return new PrototypeSnapshot(value);
        }
    }

    public record SingletonSnapshot(String value) {
    }

    public record PrototypeSnapshot(String value) {
    }
}
~~~

### Chạy

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.PropertySourceTimingDemo
~~~

Stdout của riêng `System.out.println` trong chương trình (không tính log của Maven, compiler hoặc các logging frameworks):

~~~text
Before refresh: EARLY
Injected singleton: EARLY
Environment after update: LATE
Existing singleton: EARLY
New prototype: LATE
~~~

### Phân tích chính xác

1. `new AnnotationConfigApplicationContext()` tạo context **chưa refresh**. Chương trình lấy `MutablePropertySources` và thêm hai sources.
2. `courseFallback` có giá trị `LOW` nhưng được thêm cuối; `courseStartupOverride` có giá trị `EARLY` và được thêm đầu. Vì thế `getProperty(KEY)` trả `EARLY`, không phụ thuộc JVM env/system variables trong ví dụ.
3. `context.register(Config.class)` rồi `refresh()`. Spring đăng ký bean definitions và tạo `SingletonSnapshot` (scope singleton mặc định). Factory method parameter `@Value("${course.demo.message:DEFAULT}")` được resolve tại thời điểm tạo bean từ Environment hiện có, nhận `EARLY`.
4. Sau refresh, `courseRuntimeOverride` được thêm ở đầu và chứa cùng key với `LATE`. `Environment.getProperty(KEY)` tra từ đầu danh sách nên trả `LATE`.
5. `SingletonSnapshot` đã là record chứa một final field `value` được tạo với `EARLY`. Không có cơ chế tự gọi lại factory method hoặc set field của object ấy, nên giá trị vẫn `EARLY`.
6. `PrototypeSnapshot` chỉ có definition sau refresh; instance đầu tiên được tạo lúc `context.getBean(PrototypeSnapshot.class)`. Trong đường resolve `@Value` mặc định của demo, property được đọc từ Environment hiện tại, nên nhận `LATE`.
7. `try-with-resources` đóng context khi kết thúc; không có thread/network resource đặc biệt để dọn dẹp.

**Không tổng quát hóa kết quả prototype cho mọi thư viện binder tùy biến.** Một `PropertySourcesPlaceholderConfigurer` hoặc bean definition đã chụp giá trị config theo cơ chế riêng có thể cho behavior khác. Demo cố ý dùng default container path để tách rõ thời điểm tạo bean và thời điểm cập nhật Environment.

### Vì sao không dùng @Value trên static final constant?

Annotation injection dùng container injection point, không tự set các `static final` constants của Java. Một `record` được tạo từ factory method parameter là ví dụ minh bạch về **configuration snapshot tại instantiation time**.

## 9. @Profile, active profile và thời điểm chọn BeanDefinition

Profile và property source liên quan nhưng khác nhau:

- `PropertySource` bổ sung key–value để lookup.
- `setActiveProfiles(...)` đặt active profiles trực tiếp trên `ConfigurableEnvironment`.
- `@Profile(...)` dùng profile state tại lúc Spring **đánh giá điều kiện** để quyết định có đăng ký definition/configuration tương ứng hay không.
- Property `spring.profiles.active` có thể được resolver dùng để xác định active profile khi chưa được đặt tường minh, tùy trình tự đọc/configuration framework.

### Cấu hình sớm

~~~java
var context = new AnnotationConfigApplicationContext();

context.getEnvironment().setActiveProfiles("dev");
context.getEnvironment().getPropertySources().addFirst(customSource);

context.register(ProfiledConfig.class);
context.refresh();
~~~

Đây là snippet với `customSource`/`ProfiledConfig` cần được định nghĩa. Thiết lập profile trước register/refresh bảo đảm việc đánh giá `@Profile` trong bootstrap đọc trạng thái đúng.

### Thêm nguồn chứa spring.profiles.active quá muộn

Một số developers thử:

~~~java
context.refresh();
context.getEnvironment().getPropertySources().addFirst(
        new MapPropertySource(
                "lateProfiles",
                Map.<String, Object>of("spring.profiles.active", "dev")));
~~~

Dù đọc Environment sau đó có thể thấy property mới, **những BeanDefinitions đã bị `@Profile` loại trong bootstrap không tự được đăng ký lại**. Không dùng runtime property source mutation để bật/tắt beans của một ApplicationContext đã refresh.

### @Conditional và resource location cũng có thời điểm

Tương tự, `@Conditional` do condition evaluator quyết định ở phase parse/register thích hợp. Giá trị config thay sau evaluation không tự chạy lại condition.

Nếu source path trong `@PropertySource("classpath:${region}/config.properties")` phụ thuộc key `region`, key ấy phải sẵn có **trước bước parse annotation/resource location**. Nếu thay key sau, đường dẫn file đã chọn không tự thay.

## 10. PropertySourcesPlaceholderConfigurer và placeholder resolution

`PropertySourcesPlaceholderConfigurer` là một `BeanFactoryPostProcessor` chuyên resolve `${...}` trong bean definitions và embedded values. Trong `ApplicationContext`, các configuration annotations/embedded value resolvers cũng tham gia xử lý `@Value` theo pipeline.

Một khai báo rõ ràng:

~~~java
@Configuration(proxyBeanMethods = false)
public class PlaceholderConfig {
    @Bean
    public static PropertySourcesPlaceholderConfigurer placeholders() {
        return new PropertySourcesPlaceholderConfigurer();
    }
}
~~~

Đây là snippet, cần import `org.springframework.context.support.PropertySourcesPlaceholderConfigurer` và annotation Spring.

### Vì sao static @Bean?

BeanFactoryPostProcessor phải được khởi tạo **rất sớm**, trước regular bean instantiation. Dùng static factory method giúp tránh khởi tạo `@Configuration` object quá sớm chỉ để tạo BFPP; đồng thời giảm nguy cơ những `@Autowired`, `@PostConstruct` trên config class không nhận đủ processing.

### Early source được sử dụng ở đâu?

Một source được thêm trước refresh có thể được placeholder configurer và embedded resolver nhìn thấy trong quá trình giải quyết:

~~~text
PropertySources [high → low]
          │
          ▼
resolve "${db.pool.size:8}"
          │
          ├─ bean definition property value
          ├─ @Value factory/constructor parameter
          └─ một số cấu hình hạ tầng tùy cách tích hợp
~~~

Nếu `${db.pool.size}` đã được giải quyết và ghi vào metadata hoặc một bean đã được tạo, việc thay Environment sau đó **không tự yêu cầu configurer chạy lại**. Khi code tạo resolver riêng với một set sources cố định hoặc cấu hình `setPropertySources(...)`, cần xác định resolver có sử dụng Environment live hay một snapshot để không đưa ra lời hứa cập nhật động sai.

### Missing key

Với placeholder có default như `${db.pool.size:8}`, nếu key vắng mặt thì resolver có thể dùng `8`. Với placeholder không có default, missing-key behavior phụ thuộc resolver/configurer và cấu hình `ignoreUnresolvablePlaceholders`; `@Value` có thể giữ literal/chuyển lỗi trong một số cấu hình hoặc ném exception tùy bước type conversion. Không tuyên bố mọi placeholder thiếu luôn gây đúng một exception trong mọi setup. Khi key bắt buộc, validate nó rõ tại bootstrap để fail fast.

## 11. Spring Boot khác Spring Core Container như thế nào?

Spring Boot dùng chung `Environment` và `PropertySource` của Spring, nhưng có **pipeline externalized configuration riêng**, bao gồm config data, application properties/yaml, command-line arguments, test-specific sources và nhiều integration points.

Điểm đáng nhớ từ Boot Reference:

- Boot có precedence nhiều lớp để ghi đè cấu hình một cách dự đoán được.
- `@PropertySource` trên `@Configuration` **không được thêm cho đến lúc ApplicationContext đang refresh**.
- Những key `logging.*` và `spring.main.*` có thể được tiêu thụ **trước refresh**, nên `@PropertySource` quá muộn để cấu hình chúng.
- `@ConfigurationProperties` là cơ chế binding cấu hình có type của Spring Boot; không tự suy ra việc đổi Environment sẽ rebind tất cả các object `@ConfigurationProperties` đã tồn tại.
- `@Value` là feature Spring Core, còn relaxed binding, config data và lifecycle startup cụ thể của Boot bổ sung các quy tắc của Boot.

### Khi cần thêm nguồn sớm trong Spring Boot

Các lựa chọn có thể phù hợp tùy mức độ sớm của thuộc tính:

- Dùng cách cấu hình externalized chính thức của Boot như `application.properties`/`application.yaml`, environment variables, JVM args, command-line args.
- Dùng `EnvironmentPostProcessor` khi cần custom hóa Environment ở giai đoạn chuẩn bị environment của Boot, trước khi ApplicationContext refresh.
- Dùng `ApplicationContextInitializer` để cấu hình context/environment trước refresh, với điều kiện property chưa bị một subsystem sớm hơn tiêu thụ.
- Dùng test utilities như `@TestPropertySource`, `@DynamicPropertySource`, hoặc property overrides của `@SpringBootTest` khi đang thiết kế integration tests.

**Phân biệt rõ:** `ApplicationContextInitializer` xảy ra trước **context refresh**, nhưng chưa chắc đủ sớm cho mọi `spring.main.*` hoặc logging option đã được Boot đọc trong quá trình tạo/chuẩn bị application. Với những settings rất sớm, ưu tiên đúng hook external config/Environment preparation của Boot.

## 12. Source từ file, remote service và thứ tự khởi tạo

Nguồn thuộc tính thực tế có thể đến từ:

- `.properties` file trên classpath hoặc file system.
- Map tạo từ environment-specific deployment parameters.
- Database config table hoặc remote configuration service.
- Servlet context/JNDI trong web runtime phù hợp.
- Vault/secret managers qua tích hợp chuyên dụng.
- Test overlays và in-memory maps.

**Nguyên tắc:** nếu những giá trị phải điều khiển **bean definitions hoặc eager initialization**, nguồn phải được nạp trước phase tiêu thụ. Nhưng không nên để việc nạp một remote config source **chờ vô hạn** trong bootstrap.

### Đọc remote config trước refresh

Nếu deployment yêu cầu remote config bắt buộc:

1. Chọn client để đọc config từ bootstrap layer, với timeout và bounded retry.
2. Parse, validate và bảo đảm data types đúng.
3. Tạo `MapPropertySource` hoặc implementation cụ thể từ snapshot đã xác thực.
4. Thêm source ở vị trí precedence mong muốn trước refresh.
5. Đăng ký/scan configuration và refresh context.
6. Xác định fail-fast hay fallback nếu remote unavailable.

Đừng đưa một bean nghiệp vụ để tải config rồi chỉ sau khi nó đã được khởi tạo mới thêm source cho những beans đã tạo trước đó; trình tự ấy tạo startup race/late resolution.

### Remote update sau refresh

Nếu một remote service thay đổi giá trị trong khi app đang chạy, không phải mọi thuộc tính đều an toàn để live-update. Một database URL hay thread-pool size đã dùng để dựng resource có thể cần restart/recreate resource mới. Đối với feature flags đọc qua `Environment` mỗi lần, cập nhật live có thể khả thi nhưng cần thread-safety và consistency.

Với multi-key configuration (ví dụ hostname + port + token), update từng key rời rạc có thể khiến một request đọc hỗn hợp hai phiên bản. Thay một immutable config snapshot atomically hoặc dùng abstraction quản lý refresh có contract rõ ràng khi thực sự cần dynamic config.

## 13. Thread safety và mutability của sources

`MutablePropertySources` cho phép runtime mutations, nhưng **không nên coi đây là distributed configuration store hoặc cơ chế atomically rebind toàn bộ app**. Cần làm rõ:

- Có thread nào khác đang gọi `Environment.getProperty` khi bạn sắp xếp sources lại?
- Source chứa `Map` có bị mutate trực tiếp từ thread khác?
- Một reader lấy nhiều keys có cần snapshot đồng nhất?
- Khi config đổi, bean instances nào phải recreate, còn bean nào đọc live?
- Resource hiện có (connection pool/client) xử lý update/close như thế nào?
- Có cần publish change event, version hoặc state transition rõ?

Dù container implementation có các biện pháp đồng bộ hóa ở các phần nội bộ, đây không phải lý do để thay Environment tùy tiện từ nhiều threads và cho rằng mọi business operation đọc một snapshot nhất quán. Với runtime config, thường nên dùng **một service riêng** xuất typed immutable snapshot/feature flag và có cơ chế cập nhật rõ ràng.

### Không trộn bootstrap và runtime config

Một ứng dụng ổn định thường chia:

| Loại thuộc tính | Ví dụ | Khi nào chốt? | Cách thay đổi |
| --- | --- | --- | --- |
| Bean graph/profile | Chọn PostgreSQL hay in-memory repository | Bootstrap/definition processing | Refresh/restart theo thiết kế |
| Infrastructure resource | Connection pool size, endpoint TLS | Khi client/pool được tạo | Rebuild resource qua lifecycle |
| Singleton immutable snapshot | `@Value` factory/constructor argument | Khi singleton instantiated | Recreate/reconfigure bean |
| Prototype/lazy instance | Parameter lúc lần đầu cần bean | Khi instance mới được tạo | Source hiện có nếu resolver đọc live |
| Dynamic feature flag | Cho phép tính năng tại request time | Theo lookup/runtime policy | Config service có atomic snapshots |
| Logging/main app settings của Boot | `logging.*`, `spring.main.*` | Có thể trước context refresh | Boot external config/early hooks |

Không nên triển khai “live config” chỉ bằng cách thêm một map ở đầu PropertySources rồi mong tất cả beans tự thay đổi tương ứng.

## 14. Thứ tự cấu hình đúng trong ứng dụng nhiều context

Một hệ thống có thể có parent ApplicationContext và child ApplicationContext. Mỗi context có `Environment` liên quan; khi gắn parent, Spring có cơ chế merge profiles/property sources nhưng **không đồng nghĩa một nguồn thêm ở parent sau này sẽ tự phản ánh trong child**.

Theo Javadoc `ConfigurableEnvironment.merge(parent)`:

- Các nguồn của parent được thêm vào tập nguồn con khi merge theo contract.
- Với cùng tên `PropertySource`, entry của child được giữ để tránh duplicate và cho child override.
- **Những thay đổi Environment của parent xảy ra sau lúc merge không tự được propagate vào child**.
- Active/default profiles cũng được merge theo semantics của API.

### Quy trình cho hierarchy

~~~text
1. Tạo và cấu hình Environment của parent.
2. Thêm sources, profiles cần thiết cho parent.
3. Tạo/refresh parent khi đúng thời điểm.
4. Tạo child, gắn parent, cấu hình overrides của child.
5. Đăng ký child configuration/scan.
6. refresh child.
~~~

Thứ tự chính xác có thể cần tinh chỉnh tùy framework quản lý context hierarchy; nguyên tắc là mọi context phải có sources cần thiết **trước khi chính context ấy xử lý beans/configuration**. Không tạo child rồi đợi thêm sources vào parent **sau khi child đã merge và refresh** để mong child tự cập nhật.

## 15. Kiểm thử hành vi theo thời điểm

### 15.1. Unit test property precedence

Dùng `StandardEnvironment` hoặc `MockEnvironment` để kiểm chứng vị trí:

~~~java
ConfigurableEnvironment env = new StandardEnvironment();
MutablePropertySources sources = env.getPropertySources();

sources.addLast(new MapPropertySource(
        "defaults", Map.<String, Object>of("demo.mode", "safe")));
sources.addFirst(new MapPropertySource(
        "overrides", Map.<String, Object>of("demo.mode", "fast")));

if (!"fast".equals(env.getProperty("demo.mode"))) {
    throw new AssertionError("Wrong precedence");
}
~~~

Đoạn là snippet cần imports `Map` và các types trong `org.springframework.core.env`; không cần ApplicationContext để test riêng property resolution.

### 15.2. Integration test bootstrap

Dùng một `AnnotationConfigApplicationContext` chưa refresh, thêm property source, đăng ký `@Configuration` với `@Value`, rồi refresh. Assert bean nhận đúng giá trị.

Test thêm một case cố ý **sai thời điểm**: tạo singleton, thêm source sau refresh, xác nhận singleton vẫn giữ snapshot nhưng Environment đọc giá trị mới. Đây là kiểm thử regression quan trọng khi refactor startup code.

### 15.3. Test profiles

Dùng hai contexts độc lập:

- Context A đặt active profile phù hợp **trước** registration/refresh; bean được đánh dấu `@Profile` được đăng ký.
- Context B không có profile đó; bean không được đăng ký.
- Sau khi B refresh, thêm nguồn hoặc set active profile; xác nhận BeanDefinition B không tự được tạo thêm.

Đừng dùng việc gọi `env.getActiveProfiles()` lúc cuối làm test duy nhất. Điều cần kiểm tra là **bean graph thật** đã được quyết định khi nào.

### 15.4. Test nguồn được thêm bởi annotation

Nếu `@PropertySource` có placeholder trong path, test với nguồn `course.region` được đưa vào trước khi config parser xử lý class, rồi xác nhận đúng file resource được đọc. Test file thiếu, default fallback, priority với system properties và thứ tự nhiều @PropertySource declarations nếu app dựa vào chúng.

### 15.5. Test late prototype

Tạo bean prototype với `@Value`, đổi PropertySources sau refresh nhưng trước lần lookup đầu; assert prototype thấy giá trị phù hợp trong pipeline tiêu chuẩn. Sau đó kiểm tra một prototype tạo trước thay đổi vẫn giữ snapshot riêng. Không mặc định toàn bộ third-party binders có cùng timing.

## 16. Những lỗi phổ biến và cách sửa

| Triệu chứng | Nguyên nhân khả dĩ | Cách sửa |
| --- | --- | --- |
| `@Value` vẫn lấy giá trị cũ | PropertySource thêm sau bean instantiation | Thêm trước refresh; nếu muốn runtime update, có cơ chế rebind/recreate |
| `@Profile` bean vẫn không xuất hiện | Profile được set sau parse/registration/refresh | Set sớm trước register/scan và refresh |
| `Environment.getProperty` thấy mới nhưng singleton thấy cũ | Environment đọc live, singleton đã chụp giá trị | Đây là semantics bình thường; tách snapshot vs dynamic |
| Custom source có key nhưng không thắng | Dùng `addLast`, nguồn khác ưu tiên cao hơn | Dùng `addFirst`/`addBefore` và kiểm tra source order |
| Source dùng trùng name, thứ tự bị thay ngoài ý muốn | PropertySource được quản lý theo name | Dùng unique names hoặc `replace` chủ đích |
| Đặt `@PropertySource` rồi mong chỉnh logging Boot | Annotation xử lý khi refresh, logging có thể đọc sớm hơn | Boot external config/EnvironmentPostProcessor |
| Config path `@PropertySource` không resolve key | Source chứa path placeholder đến quá muộn | Đưa key vào Environment trước configuration parsing |
| Cùng key ở system property và env variable khác nhau | StandardEnvironment ưu tiên system properties | Xác nhận precedence và đặt override rõ |
| Prototype thấy mới nhưng singleton không | Prototype instantiated muộn, singleton instantiated sớm | Hiểu thời điểm resolve của từng instance |
| BeanDefinition vẫn dùng giá trị cũ | Placeholder/BFPP đã xử lý metadata trước update | Rebuild context/definition qua flow hỗ trợ |
| Bean không đổi sau khi addFirst runtime | Không có automatic re-injection | Dùng dynamic lookup/recreation đúng contract |
| Một request đọc hai phiên bản config | Update các keys rời rạc | Immutable versioned snapshot cho runtime config |
| Remote config làm startup treo | I/O chưa timeout/retry giới hạn trong bootstrap | Bounded fetch + fail-fast/fallback |
| Child context không thấy source thêm muộn ở parent | Environment merge chỉ tại thời điểm gắn/merge | Cấu hình parent trước khi child merge; override child đúng lúc |
| Missing property không ném lỗi như dự đoán | Default placeholder/lenient resolver | Explicit validation hoặc strict configurer |
| Test pass nhưng deployment fail | Boot có source ordering khác plain Spring | Test đúng environment type/Boot startup path |
| Secret lộ trong log khi debug | In toàn bộ properties/Environment | Log key names và source origin, ẩn sensitive values |
| Thêm source trong `@PostConstruct` nhằm cấu hình bean khác | Lúc này container đã bắt đầu tạo beans, thứ tự không chắc chắn | Đưa source setup vào bootstrap trước refresh |

## 17. Checklist triển khai

1. Đã xác định property cần cho **bootstrap** hay chỉ cho **runtime lookup**?
2. Đang dùng plain Spring Core hay Spring Boot? Có hiểu sự khác biệt source precedence/hook không?
3. Có tạo context bằng **no-arg constructor** khi cần thêm source trước refresh không?
4. PropertySources đã được chuẩn bị trước `register/scan` nếu cấu hình phụ thuộc conditions/profiles?
5. Đã gọi `refresh()` **sau** khi addFirst/addLast/replace và setActiveProfiles phù hợp?
6. PropertySource names có unique và có thứ tự chính xác không?
7. Key bắt buộc được validate/typed conversion đúng từ đầu?
8. Các URL, secrets hoặc path placeholders có được xử lý trước khi được subsystem tiêu thụ không?
9. Có hiểu `@PropertySource` được thêm trong configuration processing, không phải trước mọi phase?
10. Có tránh dùng `@PropertySource` để cấu hình những Boot settings đọc rất sớm?
11. Singleton với `@Value` có được coi là snapshot, không phải dynamic binding?
12. Có thiết kế riêng cho runtime-config updates và atomicity khi nhiều keys cùng thay đổi?
13. Với bean lazy/prototype, có kiểm tra resolver đang sử dụng sources mới hay snapshot cũ?
14. Context hierarchy có được cấu hình theo đúng thứ tự parent → child?
15. Test có kiểm tra trước refresh, sau refresh, conflict precedence và missing keys?
16. Logs có tránh lộ secrets hay toàn bộ environment variable values?
17. Khi config cần network, có timeout, fallback/fail-fast và ownership rõ?
18. Không thêm source ở một init callback quá muộn rồi kỳ vọng bean graph tự thay đổi?

## 18. Câu hỏi tự kiểm tra

**Câu 1.** Tại sao tài liệu Spring yêu cầu thay đổi PropertySources trước `refresh()`?

**Trả lời:** Để tất cả nguồn có mặt khi container xử lý definitions, placeholder configurers, profiles/conditions và tạo bean. Nếu thêm muộn, các quyết định đã hoàn tất không tự chạy lại.

**Câu 2.** `AnnotationConfigApplicationContext(MyConfig.class)` và constructor không tham số khác gì về thời điểm?

**Trả lời:** Constructor nhận config class tự đăng ký và refresh; constructor không tham số cho phép thêm sources/profile và register/scan trước khi gọi refresh.

**Câu 3.** Đã tạo singleton với `@Value("${demo.mode}")` = `"A"`, sau đó `addFirst` một source cung cấp `"B"`. `env.getProperty` và field singleton là gì?

**Trả lời:** Environment thường trả `"B"` cho lookup mới; singleton đã inject vẫn giữ `"A"` trừ khi có cơ chế reconfiguration rõ ràng.

**Câu 4.** Một prototype chỉ được tạo sau khi thêm source mới có thể nhìn thấy giá trị mới không?

**Trả lời:** Có, trong pipeline `@Value` mặc định resolve lúc bean được tạo; nhưng cần xét resolver/configurer cụ thể nếu ứng dụng thay đường binding.

**Câu 5.** Muốn nguồn custom override JVM system property trong StandardEnvironment, `addLast` có đủ không?

**Trả lời:** Không nếu JVM system property cũng có key. Dùng `addFirst` hoặc sắp xếp nguồn cao hơn systemProperties đúng chủ đích.

**Câu 6.** `@PropertySource` trong Spring Boot có phù hợp để đặt `logging.level.*` từ file mới không?

**Trả lời:** Thường không cho những settings được Boot đọc trước refresh. Boot Reference lưu ý `@PropertySource` chỉ được thêm trong refresh; nên dùng externalized configuration hoặc early Environment hooks của Boot.

**Câu 7.** Set active profile sau refresh có tự đăng ký bean `@Profile` trước đó đã bị bỏ qua không?

**Trả lời:** Không. Thay profile state không tự thực hiện lại configuration/condition evaluation đã diễn ra.

**Câu 8.** `@PropertySource("classpath:${region}/app.properties")` chọn path dựa vào giá trị tại lúc nào?

**Trả lời:** Khi configuration processor xử lý annotation và resolve resource location, dựa trên PropertySources đã được đăng ký tại thời điểm đó.

**Câu 9.** Thay một source trong parent sau khi child đã merge Environment có tự propagate không?

**Trả lời:** Theo `ConfigurableEnvironment.merge(...)`, các thay đổi parent sau thời điểm merge không tự được phản ánh ở child. Cần chuẩn bị parent trước khi child merge hoặc cấu hình child riêng.

**Câu 10.** Có nên dùng `MutablePropertySources` như cơ chế distributed real-time configuration toàn app không?

**Trả lời:** Không mặc định. API này điều chỉnh property resolution của Environment, không tự rebind beans, thực hiện atomic multi-key snapshot hoặc quản lý resource recreation. Runtime configuration cần hợp đồng riêng.

## 19. Tài liệu tham khảo chính thức

- [Spring Framework — Environment Abstraction](https://docs.spring.io/spring-framework/reference/core/beans/environment.html)
- [Spring Framework 6.2 — ConfigurableEnvironment](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/ConfigurableEnvironment.html)
- [Spring Framework — MutablePropertySources](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/MutablePropertySources.html)
- [Spring Framework — PropertySource và MapPropertySource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/MapPropertySource.html)
- [Spring Framework — AnnotationConfigApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/AnnotationConfigApplicationContext.html)
- [Spring Framework — @PropertySource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/PropertySource.html)
- [Spring Framework — PropertySourcesPlaceholderConfigurer](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/PropertySourcesPlaceholderConfigurer.html)
- [Spring Boot — Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-environment-009">Bảo vệ thông tin nhạy cảm khi ghi log cấu hình</a>

<details>
<summary>Xem chi tiết</summary>

# Bảo vệ thông tin nhạy cảm khi ghi log cấu hình trong Spring Framework Core Container

## 1. Vì sao log cấu hình có thể trở thành lỗ hổng?

Khi triển khai Spring Framework, nhiều ứng dụng ghi log ở startup để xác nhận profile, môi trường, endpoint, feature flag hoặc tham số kết nối. Log rất hữu ích để phân tích sự cố “máy dev chạy được, production không chạy”, nhưng **`Environment` và các `PropertySource` có thể chứa bí mật thật**. Ghi thẳng toàn bộ configuration vào console/log aggregator là con đường rò rỉ thường gặp, dù người viết log chỉ muốn debug.

Ví dụ các dòng **không nên xuất hiện** trong log thực tế:

```text
DEBUG all properties: {db.password=..., api.token=..., app.region=...}
INFO datasource.url=jdbc:postgresql://db/app?user=...&password=...
ERROR client init failed for https://user:pass@api.example.invalid?token=...
DEBUG Authorization: Bearer ...
```

Các dấu ba chấm ở đây là chỗ giá trị nhạy cảm *có thể bị lộ*, không phải hướng dẫn điền credential. Sự cố có thể nảy sinh qua `logger.debug("config={}", environment)`, dump toàn bộ `System.getenv()`/`System.getProperties()`, auto-generated `toString()` của configuration object, log exception chứa connection URI, HTTP headers, Actuator endpoints, hoặc bật DEBUG/TRACE quá rộng.

**Nguyên tắc nền tảng:** *không đưa secret vào sự kiện log ngay từ đầu* (data minimization/allowlist). Masking/redaction ở tầng cuối là phòng vệ bổ sung, không phải giải pháp thay thế việc loại bỏ dữ liệu nhạy cảm tại nguồn.

Mục tiêu của bài:
1. Biết secret nằm ở đâu trong Spring `Environment` và cấu hình.
2. Chọn **metadata an toàn** cần ghi để chẩn đoán mà không lấy giá trị bí mật.
3. Tránh rò rỉ qua startup log, `toString()`, exception, URL, SQL connection string, MDC, request/response logging.
4. Hiểu chỗ nào Spring Core chịu trách nhiệm, chỗ nào Spring Boot/Actuator bổ sung.
5. Triển khai ví dụ Java 17+ có output xác định, kiểm thử cả positive/negative case.
6. Có checklist vận hành, audit, xử lý khi phát hiện secret đã vào log.

## 2. Environment có thể đọc dữ liệu từ những nguồn nào?

Trong Spring Core Container, `org.springframework.core.env.Environment` là abstraction cho **profiles** và **properties**. Khi cần truy vấn cấu hình, `Environment` tìm giá trị qua `PropertySource` theo thứ tự ưu tiên. Với `StandardEnvironment` độc lập, các nguồn thông thường bao gồm Java system properties và OS environment variables; `ConfigurableEnvironment` cho phép đăng ký nguồn tùy chỉnh bằng `MutablePropertySources`.

```text
ConfigurableEnvironment
   |
   +-- PropertySource "testOverride" (ví dụ MapPropertySource)
   +-- PropertySource "systemProperties" (JVM -Dkey=value)
   +-- PropertySource "systemEnvironment" (OS environment)
   +-- các PropertySource khác do framework/ứng dụng thêm
   |
   +-- getProperty("remote.api-token") -> giá trị có thể nhạy cảm
   +-- containsProperty("remote.api-token") -> có/không có key
```

Thứ tự ví dụ trên không phải quy tắc toàn cục: Spring Boot có nhiều nguồn và precedence riêng (command-line args, config data, environment, system properties...), có thể thay đổi theo version và cấu hình; `addFirst`/`addLast` cũng làm đổi thứ tự.

Những nguồn rủi ro:

| Nguồn | Secret có thể xuất hiện ở đâu? | Điều không nên ghi |
| --- | --- | --- |
| OS environment variables | API key, password, database URL, webhook secret | Dump `System.getenv()` hoặc mọi entry trong nguồn |
| JVM system properties | `-Ddb.password=...` hoặc URL có user-info | Dump `System.getProperties()` |
| `application.properties` / YAML | Secret sai cách lưu trong file, credential URI | Nội dung file hoặc bound object toàn bộ |
| Spring Boot config data | Config import, mounted secret, external configuration | Raw property values và đường dẫn nhạy cảm |
| Vault/Secrets Manager adapter | Credentials/keys được resolve theo nhu cầu | Nội dung secret hoặc object accessor result |
| Servlet/JNDI/config tùy biến | Request/application server configuration | Toàn bộ map properties và metadata nhạy cảm |
| Programmatic `MapPropertySource` | Test credentials, runtime injected values | `getSource().toString()` hoặc `getProperty` mọi key |
| Kubernetes Secret/volume | File/token/cert phục vụ container | Secret file contents, key PEM, tên user nhạy cảm |

**Không chỉ dựa vào tên key:** Secret có thể ẩn trong URL, JSON blob, command-line string, HTTP header, webhook URL hoặc object custom có `toString()`. Một key tên `endpoint` vẫn có thể chứa `https://user:password@host/path?access_token=...`. Ngược lại, một key tên `region` có thể mang input độc hại hoặc dữ liệu nhạy cảm theo ngữ cảnh. Cần phân loại cả **giá trị, nguồn và mục đích sử dụng**.

## 3. Phân loại thông tin: tuyệt đối không log, chỉ log có kiểm soát

Bảng dưới là default policy bảo thủ cho một hệ thống sản xuất:

| Loại dữ liệu | Ví dụ | Hành vi log khuyến nghị |
| --- | --- | --- |
| Mật khẩu/passphrase/PIN | `db.password`, basic auth | **Không log giá trị** |
| Token/session/API credential | OAuth bearer, cookie, API key, JWT | **Không log raw token** |
| Private key/chứng thư bí mật | PEM private key, signing seed | **Không log giá trị**, không log nội dung file |
| DB connection string có credential | URL query/userinfo | Không log nguyên URL; phân tách dữ liệu metadata được cho phép |
| User/tenant sensitive identifier | Email, phone, external ID | Không log mặc định; cân nhắc pseudonymization theo chính sách |
| Request/response body | Password form, payment payload, PII | Không log toàn bộ |
| Header nhạy cảm | Authorization, Cookie, Set-Cookie | Không log raw header |
| Profile/region/tier | dev/prod, ap-southeast-1 | Có thể log qua **allowlist + validation** nếu được phép |
| Tên subsystem/status | `payment-client initialized` | Ưu tiên thay vì giá trị cấu hình |
| Secret hiện diện hay chưa | `apiKeyConfigured=true` | Chỉ log khi có nhu cầu và chính sách cho phép; metadata cũng có thể nhạy cảm |
| Version/build/feature flag | Version đã công bố nội bộ | Có thể log một số key đã duyệt, tránh flag tiết lộ kiểm soát bảo mật |

Nguồn tham khảo OWASP Logging Cheat Sheet liệt kê access tokens, authentication passwords, sensitive personal data, session IDs, database connection strings và encryption keys là những dữ liệu **thông thường không nên ghi trực tiếp**. Mức độ cần kiểm soát phụ thuộc threat model, quy định địa phương và chính sách tổ chức.

**Tránh hai cực đoan:** log mọi thứ để tiện debug, hoặc không log gì khiến không thể chẩn đoán. Log **mô tả trạng thái và kết quả kiểm tra** thay vì raw inputs nhạy cảm.

## 4. Nguyên tắc allowlist: chỉ ghi những trường được duyệt

Cách ưu tiên:

1. Định nghĩa trước danh sách **tên trường log được phép** và loại giá trị.
2. Ở mỗi sự kiện, chọn từ danh sách đó; không iterate toàn bộ property sources.
3. Validate giá trị “an toàn” ở đầu vào hoặc trước khi đưa vào log (type, length, charset, enum).
4. Không ghi raw secret ở bất kỳ nhánh lỗi nào; thông báo lỗi dựa trên code/tên key nếu được duyệt.
5. Kiểm thử rằng output **không chứa các sentinel secret** trong nhiều dạng mẫu.
6. Ở log sink/collector, có thể thêm filtering/redaction phòng vệ nhiều lớp; nhưng không coi đó là giấy phép để log dữ liệu thô.

Ví dụ sự kiện mong muốn:

```text
config_checked tier=dev region=ap-southeast-1 endpointConfigured=true tokenConfigured=true
```

Dòng này dùng metadata giả lập, không chứa URI, token hay password. Ngay cả khi đó, cờ `tokenConfigured` chỉ nên log khi việc tiết lộ sự hiện diện không vi phạm threat model của hệ thống.

### Allowlist khác blocklist

**Blocklist** thử ẩn một tập key như `password`, `secret`, `token`, `credential`. Nó dễ bỏ sót `auth`, `apikey`, `privateKey`, URL mang token trong query, key tùy đặt tên, JSON lồng nhau hoặc secret được URL/base64 encode.

**Allowlist** chỉ chấp nhận các trường đã xét duyệt như `tier`, `region`, `startupStatus`, `component`, có validation kiểu/độ dài. Cấu trúc log được thiết kế để những secret không được truy cập/serialize vào logger.

```java
// Không nên:
logger.debug("configuration={}", environment);
logger.info("properties={}", System.getProperties());
logger.info("env={}", System.getenv());

// Nên: lấy value không nhạy cảm đã thẩm định
logger.info("config_checked tier={} region={} apiKeyConfigured={}",
        safeTier, safeRegion, apiKeyConfigured);
```

Đây là các fragment SLF4J minh họa, cần logger/fields được định nghĩa và thẩm định. Parameterized logging giúp giảm việc format string khi không cần nhưng **không hề tự che secret**. Nếu argument là secret hoặc object `toString()` chứa secret, logger vẫn có thể ghi nó.

## 5. Không dump toàn bộ Environment hoặc PropertySources

Với `ConfigurableEnvironment`, một số API cho phép duyệt property sources:

```java
ConfigurableEnvironment env = context.getEnvironment();
for (PropertySource<?> source : env.getPropertySources()) {
    System.out.println("source: " + source.getName());
}
```

Đây là **fragment**, cần imports `ConfigurableEnvironment` và `PropertySource`, cũng như context cụ thể. Ngay cả log tên source có thể tiết lộ đường dẫn mount hoặc tên secret backend nếu không được xem xét. **Không** in `source.getSource()`, `source.toString()` hay loop qua mọi property để ghi giá trị. Một `MapPropertySource` có thể chứa nguyên secret map.

Nếu cần kiểm tra precedence:
- Xây một danh sách các key **không nhạy cảm** và chỉ đo giá trị của chúng.
- Nếu cần xác minh nguồn nào override một key nhạy cảm, làm trong diagnostic tool có quyền hạn rõ, ưu tiên ghi “nguồn đã chọn” dưới dạng định danh đã được duyệt, **không** giá trị secret.
- Không bật dump environment bằng debug toggle chung cho production.
- Để phục vụ audit, có thể log **config schema version**, config release ID đã công bố hoặc trạng thái validate thay vì snapshot raw.

### @Value và @ConfigurationProperties

Bean có `@Value` hoặc object được bind từ configuration thường giữ các secret trong fields. Nếu dùng Lombok `@Data`/`@ToString`, Java `record` hoặc generated `toString()` mặc định, logger có thể vô tình ghi cả fields:

```java
// Không phù hợp để in toString() nếu record chứa secret:
record ClientSettings(String endpoint, String apiKey) {}
logger.debug("settings={}", settings);
```

Snippet chỉ minh họa; field `apiKey` là bí mật. Java record tạo `toString()` tự động có thể bao gồm value. Sửa bằng việc **không log object** hoặc tách “safe diagnostic view” chứa đúng trường đã được duyệt, tránh reliance vào mỗi annotation exclude của một thư viện.

Một DTO “safe view” nên không mang bất kỳ secret nào trong state; formatter và serializer không thể tiết lộ thứ không tồn tại trong object đó.

## 6. URL và connection string: rò rỉ gián tiếp ngay cả khi key có vẻ an toàn

Tên property `remote.endpoint`, `database.url` hoặc `webhook.url` nghe vô hại nhưng value có thể chứa:
- `https://user:pass@host/...`: URI user-info.
- Query `?token=...`, `?api_key=...`, `?signature=...`.
- JDBC URL có `password=...` hoặc thông tin user.
- Cloud storage signed URL/temporary credentials.
- Path chứa account ID, email hoặc access token.
- Fragment hoặc encoded form khó nhận diện.

**Không log URL thô**. Nếu nghiệp vụ chỉ cần biết dịch vụ nào, log logical ID như `component=inventory-client`, trạng thái `configured=true`, region từ enum đã duyệt hoặc hostname được parse và cho phép theo chính sách. Thậm chí hostname có thể nhạy cảm (internal topology); không mặc định log mọi hostname.

### Vì sao regex thay password trong URL chưa đủ?

```java
// Anti-pattern minh họa:
String sanitized = url.replaceAll("password=[^&]+", "password=***");
logger.info("url={}", sanitized);
```

Snippet giả định có `url` và `logger`. Nó bỏ sót `pwd`, `token`, `signature`, user-info, percent-encoding, JSON-encoded params, khác biệt chữ hoa/thường và định dạng JDBC đặc thù. Nếu yêu cầu log endpoint, **tạo một dạng dữ liệu mới từ allowlisted components**, không mutate raw URL bằng vài regex.

Nếu bắt buộc sanitize các nguồn third-party không kiểm soát, hãy dùng parser theo scheme, whitelist fields được giữ lại, mặc định bỏ toàn bộ query/user-info/fragment; giới hạn độ dài và test các dạng encoding. Dù vậy, giải pháp tốt hơn là log **component name + error code**.

## 7. Exception và stack trace cũng là đường rò rỉ

Một số thư viện tạo message chứa username, full endpoint, request target, query parameters, headers hoặc response body. Dòng:

```java
logger.error("Failed to connect", exception);
```

**không phải lúc nào cũng an toàn**: logger thường serialize `exception.getMessage()` và stack trace với nested causes. Nếu exception message chứa secret, việc redaction ở source `logger.info` khác không cứu được.

Ví dụ chính sách an toàn hơn ở boundary có dữ liệu nhạy cảm:

```java
try {
    client.initialize();
} catch (RemoteClientException ex) {
    logger.error("client_initialization_failed component={} category={}",
            "remote-client", "CONNECTIVITY");
    throw new ClientStartupException("Remote client initialization failed");
}
```

Đây là **pseudocode minh họa**, `client`, `RemoteClientException`, `ClientStartupException` cần được định nghĩa theo ứng dụng. Trong thực tế cần giữ nguyên thông tin phục vụ điều tra bằng secure telemetry phù hợp, exception classification, request ID và metrics, nhưng **không tự động log/carry nguyên exception message** khi nó chưa được phân loại an toàn.

Cẩn trọng: nếu exception mới được tạo với `cause=ex` rồi upstream logger in full cause chain, secret vẫn có thể lộ. Do đó ranh giới ghi log của exception phải được kiểm soát xuyên suốt, kể cả global exception handler và crash reporter. Không khuyến nghị xóa toàn bộ traceback trong mọi hoàn cảnh; thay vào đó log **thông tin đã được phân loại an toàn** và lưu chi tiết nhạy cảm trong kênh có chính sách chặt nếu thật sự cần.

### Không chép cấu hình vào exception text

```java
// Không nên:
throw new IllegalStateException("Invalid token: " + token);

// Nên:
throw new IllegalStateException("Required client credential is invalid");
```

Các dòng là fragments, `token` được giả định có. Tránh thông báo ghi nguyên mật khẩu, token, query/credential URI hoặc raw config blob. Nếu cần tên key, chỉ dùng key **đã duyệt**, không ghi value.

## 8. Log injection: dữ liệu không bí mật vẫn có thể gây hại

Một giá trị config không thuộc nhóm secret vẫn có thể chứa newline, carriage return, tab, ký tự điều khiển hoặc chuỗi rất dài nếu đến từ nguồn ngoài. Nếu nối thẳng vào text log, nó có thể làm giả nhiều dòng hoặc giả level/message khác nhau.

```text
region="us-east-1
ERROR payment-authorized=true"
```

Đây là một ví dụ **log forging**, không phải output từ Spring. Nếu dùng region như một **enum hoặc token theo allowlist** (chữ thường, số và dấu gạch ngang, độ dài tối đa), chuỗi bất thường bị từ chối trước khi tới logger.

**Không dùng regex đơn giản để mask secret rồi coi dữ liệu an toàn về cấu trúc.** Với structured logging, dùng API serializer đáng tin và kiểm tra giới hạn field, type/charset. Ở log pipeline, có thể thêm lọc CR/LF và giới hạn record size, nhưng tránh sửa dữ liệu theo cách gây nhầm lẫn debug.

## 9. Demo Spring Core hoàn chỉnh (Java 17+): chỉ log metadata cho phép

Mẫu runnable sau dùng **Java 17+, Maven 3.9+ và Spring Framework 6.2.18**. Không cần Spring Boot, web server, database hay secret manager. Toàn bộ “secret” trong ví dụ là **chuỗi giả, không có giá trị sử dụng**; không copy secret thật của doanh nghiệp vào tài liệu hay test.

Bài dùng `AnnotationConfigApplicationContext` và `MapPropertySource` để mô phỏng cấu hình có chứa API token, JDBC password và endpoint URL mang credential. `ConfigDiagnostics` chỉ đọc:
- `app.tier`, `app.region`: hai metadata được allowlist, validate bằng regex chặt.
- Sự **hiện diện** của `external.endpoint` và `external.api-token` thông qua `containsProperty(...)`.
- Không gọi `getProperty(...)` trên token, database URL hoặc URI, không serialize toàn bộ Environment.

Để output ổn định và dễ kiểm thử không có logger backend/timestamp khác, phần runnable dùng `System.out.println` làm **mô phỏng log event**. Trong production, thay bằng structured SLF4J/Logback/Log4j2 hoặc logging facade của dự án, nhưng **giữ nguyên allowlist và validation**. Việc thay sink không được biến event lại thành dump cấu hình.

### 9.1. Cấu trúc thư mục

```text
spring-safe-config-log/
├── pom.xml
└── src/main/java/edu/example/safelog/
    ├── AppConfig.java
    ├── ConfigDiagnostics.java
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
    <artifactId>spring-safe-config-log</artifactId>
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
                    <mainClass>edu.example.safelog.Main</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**src/main/java/edu/example/safelog/ConfigDiagnostics.java**

```java
package edu.example.safelog;

import org.springframework.core.env.Environment;

public final class ConfigDiagnostics {
    private final Environment environment;

    public ConfigDiagnostics(Environment environment) {
        this.environment = environment;
    }

    public String safeStatus() {
        // Hai khóa cấu hình đã được duyệt cho phép xuất hiện trong log.
        String tier = safeLabel("app.tier");
        String region = safeLabel("app.region");

        // Không lấy giá trị endpoint, token hoặc JDBC URL.
        boolean endpointConfigured =
                environment.containsProperty("external.endpoint");
        boolean tokenConfigured =
                environment.containsProperty("external.api-token");

        return "config_checked tier=" + tier
                + " region=" + region
                + " endpointConfigured=" + endpointConfigured
                + " tokenConfigured=" + tokenConfigured;
    }

    private String safeLabel(String key) {
        String value = environment.getRequiredProperty(key);
        // Chặn newline/log forging và các chuỗi ngoài allowlist.
        if (!value.matches("[a-z0-9-]{1,32}")) {
            // Không đưa value vào exception message.
            throw new IllegalStateException("Invalid diagnostic metadata");
        }
        return value;
    }
}
```

**src/main/java/edu/example/safelog/AppConfig.java**

```java
package edu.example.safelog;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class AppConfig {
    @Bean
    public ConfigDiagnostics configDiagnostics(Environment environment) {
        return new ConfigDiagnostics(environment);
    }
}
```

**src/main/java/edu/example/safelog/Main.java**

```java
package edu.example.safelog;

import java.util.Map;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

public final class Main {
    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {

            // Đều là chuỗi GIẢ cho bài học, tuyệt đối không dùng
            // thông tin xác thực thật trong source code.
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("demo", Map.of(
                            "app.tier", "dev",
                            "app.region", "ap-southeast-1",
                            "external.endpoint",
                            "https://user:fake-pass@example.invalid/v1?token=fake-query",
                            "external.api-token", "fake-token-for-test",
                            "jdbc.url",
                            "jdbc:postgresql://localhost:5432/app?password=fake-db-pass"
                    )));

            context.register(AppConfig.class);
            context.refresh();

            String line = context.getBean(ConfigDiagnostics.class).safeStatus();
            System.out.println(line);

            // Đây là kiểm tra trên log EVENT đã dựng, không ghi raw secrets.
            System.out.println("contains fake token: "
                    + line.contains("fake-token-for-test"));
            System.out.println("contains fake db password: "
                    + line.contains("fake-db-pass"));
        }
    }
}
```

Chạy từ thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java
```

**Những dòng `System.out` được chương trình in theo đúng thứ tự:**

```text
config_checked tier=dev region=ap-southeast-1 endpointConfigured=true tokenConfigured=true
contains fake token: false
contains fake db password: false
```

Các log từ Spring/Maven nếu được bật có thể khác theo môi trường; ba dòng trên chỉ là stdout do `Main` in. Kết quả xác định vì `MapPropertySource` đã được đưa vào `Environment` trước `refresh()`, chứa đầy đủ khóa giả; `safeStatus()` cố ý không lấy các giá trị credential.

### 9.2. Tại sao ví dụ an toàn hơn cách dump Environment?

1. Hai field được log (`tier` và `region`) có allowlist rõ ràng, bị giới hạn độ dài/ký tự để tránh newline hoặc raw arbitrary config.
2. `containsProperty` chỉ trả về `boolean`, không đưa giá trị của `external.endpoint` hoặc `external.api-token` vào event.
3. `jdbc.url` chứa mật khẩu giả nhưng không được tham chiếu khi dựng log event.
4. Nếu `app.tier` hoặc `app.region` chứa dữ liệu không hợp lệ, method ném lỗi với **message tĩnh**, không đưa raw value vào exception.
5. Logger/event receiver chỉ nhìn chuỗi **đã kiểm soát**; không truyền `Environment` hoặc property map vào formatter.
6. Bài test kiểm tra sentinel không xuất hiện trong chính dòng log được dựng.

Giới hạn: bài demo chỉ kiểm output do `ConfigDiagnostics` dựng, **không kiểm mọi log do thư viện/ứng dụng khác tạo**. Kiểm thử production cần capture log backend và quét toàn bộ event, stack traces, structured fields, exception causes và các sink khác.

### 9.3. Câu hỏi về việc log “tokenConfigured=true”

Sự tồn tại của token có thể là metadata nhạy cảm trong một số hệ thống. Nếu policy không cho phép lộ thông tin đó, bỏ luôn field `tokenConfigured` và chỉ log `startup_validation=passed`. Nói cách khác, “không log secret value” là điều kiện cần, **chưa luôn đủ**. Allowlist phải được duyệt dựa trên business classification.

## 10. Chuyển mẫu sang logger thực tế

Với SLF4J, một service có thể ghi sự kiện an toàn sau khi validate:

```java
private static final Logger log =
        LoggerFactory.getLogger(ConfigStartupReporter.class);

public void report(ConfigDiagnostics diagnostics) {
    log.info("{}", diagnostics.safeStatus());
}
```

Snippet là **fragment SLF4J**, không thuộc project Maven runnable ở mục 9. Nó cần `org.slf4j.Logger`, `LoggerFactory` và class `ConfigStartupReporter`; thêm SLF4J API/binding tương thích với logging stack hiện có. Trong Spring Boot thường có logging starter, nhưng bài này dùng Spring Core nên không mặc định có một binding SLF4J thích hợp.

Để structured logging thực sự, ưu tiên field-level API hoặc JSON encoder có schema rõ:

```text
event=config_checked
component=remote-client
tier=dev
region=ap-southeast-1
validation=passed
```

Không đưa credential vào message template, argument list, MDC, marker, exception hoặc một object được serializer tự động đọc toàn fields. Một số logger cấu trúc có thể serialize arbitrary object qua reflection, do đó tránh truyền config bean nhạy cảm ngay cả khi text pattern trông vô hại.

### Vì sao parameterized logging không phải cơ chế bảo mật?

```java
logger.debug("apiKey={}", apiKey);
```

Cách này giảm concatenation khi level bị tắt, nhưng nếu DEBUG bật thì **toàn bộ apiKey vẫn được ghi**. Hơn nữa, argument có thể đã bị tạo hoặc đọc trước khi gọi logger. Kiểm tra level không thay đổi phân loại dữ liệu; đừng dùng `logger.isDebugEnabled()` làm lý do để bỏ data minimization.

## 11. Spring Boot Actuator: /env và /configprops không phải log nhưng cũng rủi ro

Đây là phần **mở rộng để nhận diện đường lộ cấu hình trong Spring Boot**, không phải chức năng mặc định của Spring Core Container.

Spring Boot Actuator có các endpoint chẩn đoán như `/actuator/env` và `/actuator/configprops`. Tài liệu Actuator Spring Boot 3.4 mô tả rằng các giá trị nhạy cảm tại `/env`, `/configprops` và một số endpoint tương tự mặc định được **sanitized**, thay bằng dấu sao khi cấu hình `show-values=never`.

Ví dụ định hướng chính sách (dùng trong Spring Boot project có Actuator):

```properties
management.endpoint.env.show-values=never
management.endpoint.configprops.show-values=never
management.endpoints.web.exposure.include=health,info
```

Mục tiêu là **không phơi bày env/configprops qua HTTP** và **không hiển thị raw values** kể cả khi ai đó bật endpoint theo một cấu hình khác. Endpoint exposure và response sanitization là **hai kiểm soát độc lập**. Kiểm tra policy và chính xác tên property theo phiên bản Spring Boot/Actuator đang chạy.

Một số chế độ khác có thể là:
- `never`: ẩn giá trị.
- `when-authorized`: chỉ người dùng được xác thực/phân quyền phù hợp có thể thấy giá trị.
- `always`: hiển thị cho người dùng có quyền truy cập endpoint, rất nguy hiểm nếu vô tình expose.

**Khuyến nghị bảo thủ:** giữ `never` cho env/configprops trong production trừ khi đã có quy trình bảo mật phê duyệt và lý do thật sự cụ thể. Không dùng `always` để “debug nhanh” trên môi trường chia sẻ. Những thiết lập sanitization này **không tự động che secret do application code gọi `logger.info("password={}", value)`**; log path là một kênh khác.

### Health/metrics cũng cần phân loại

Health details, build information và labels/metrics có thể lộ topology, tên tenant hoặc endpoint nội bộ. Dù không phải “raw credentials”, đó vẫn có thể là thông tin cần hạn chế. Không đưa secret vào Micrometer tags, tracing baggage hay Prometheus labels; chúng thường có nhiều downstream consumers và retention dài.

## 12. Startup failure và Spring property resolution

Spring có thể thất bại khi thiếu một required property hoặc bind/convert sai kiểu. Ví dụ:

```java
String required = environment.getRequiredProperty("remote.api-token");
```

Lời gọi này yêu cầu property tồn tại nhưng **trả secret** cho code. Nếu thất bại, Spring có thể ném exception; nếu code ứng dụng bọc exception rồi thêm toàn bộ config value/URI vào message, thông tin nhạy cảm bị lộ. Khi validate:

- Dùng thông báo tĩnh hoặc key name đã được duyệt, không ghi property value.
- Đừng echo raw `@Value`/constructor parameters vào lỗi startup.
- Với typed configuration, validate shape, length và constraint nhưng **không log giá trị bị từ chối**.
- Với external authentication errors, log component và category thay vì cả URL/token/response body.
- Tránh logger mặc định auto-serialize request payload hoặc config object trong catch-all handler.
- Nếu cần điều tra chi tiết, dùng secure debug workflow với quyền truy cập giới hạn và xử lý secrets riêng.

Có thể log một mã an toàn:

```text
startup_config_invalid component=payment-client reason=missing_required_credential
```

Nội dung này không hiển thị mật khẩu. Nhưng nếu key name hoặc component name tự nó nhạy cảm theo policy, cũng cần giảm độ chi tiết. “An toàn” là thuộc **threat model**, không chỉ do người viết đặt tên biến.

## 13. MDC, trace ID, correlation ID và structured logging

Nhiều ứng dụng gắn `requestId`, `traceId`, `userId`, `tenantId` vào MDC để correlate logs. MDC thường được append vào rất nhiều dòng log và gửi tiếp sang log collector. **Không đặt password, token, cookie, authorization header hoặc raw PII vào MDC**.

```java
// Fragment SLF4J, dùng requestId đã được generate/validate.
try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
    logger.info("operation_started component={}", "inventory-client");
}
```

Snippet cần imports `org.slf4j.MDC` và logger/requestId; không thuộc demo Spring Core. `requestId` phải được generate hoặc validate đủ chặt; nếu lấy raw từ header do client kiểm soát, kẻ tấn công có thể dùng chuỗi giả mạo hoặc tương quan với dữ liệu bí mật.

Trong async/reactive code, MDC có threading/context propagation semantics riêng; nếu không clear, dữ liệu context có thể lẫn giữa requests. Dù chỉ là ID, vẫn phải tuân thủ retention và privacy requirements. Khi cần correlation thay vì định danh người dùng trực tiếp, ưu tiên opaque random IDs và mapping bảo mật riêng.

### Không log toàn bộ HTTP request

Một request logger “tiện debug” có thể ghi:
- `Authorization`/`Cookie`/`Set-Cookie` headers.
- Form fields `password`/`client_secret`/`refresh_token`.
- OAuth callback query, signed URL hoặc password reset token.
- Body chứa PII, payment data, health data.
- Error response chứa thông tin từ hệ thống ngoài.

Hãy cấu hình headers/body allowlist và giới hạn payload logging từ đầu. Không coi HTTP wire logging ở DEBUG là vô hại chỉ vì nó tạm thời: trong production log collector, bản sao có thể tồn tại qua backup/replication/retention rất lâu.

## 14. Secret management, rotation và retention

Bảo vệ log cấu hình không thể tách khỏi toàn bộ vòng đời secret:
1. Secret lấy từ cơ chế được phê duyệt (secret manager, workload identity, mounted secret...), không hardcode vào source/repo.
2. Quyền truy cập theo **least privilege**; nhiều ứng dụng không cần xem toàn bộ bộ secrets của nhau.
3. Startup validation chỉ xác nhận cấu hình hợp lệ, không in secret.
4. Log sink có RBAC riêng, transport encryption, retention và export controls.
5. Log collector/backups được giám sát, tránh sao chép dữ liệu ra dashboard hoặc ticket với quyền rộng.
6. Khi rotate secret, không ghi **old/new value** vào log. Có thể ghi mã thay đổi, trạng thái và version ID đã được policy cho phép.
7. Nếu nghi secret lộ, **rotate/revoke ngay theo quy trình**, đồng thời đánh giá mọi log sink và bản sao lưu. Chỉ xóa một dòng khỏi dashboard không làm token đã lộ trở nên an toàn.

**Đừng log giá trị hash của secret theo thói quen.** Hash không có key của một secret entropy thấp có thể bị brute-force; token fingerprint có thể cho phép liên kết các sự kiện nhạy cảm. Nếu cần định danh bản secret cho audit, dùng identifier/version được secret manager cấp và đã được phép public nội bộ, hoặc một thiết kế keyed digest/HMAC được security team phê duyệt với key riêng và retention phù hợp.

## 15. Redaction ở nhiều tầng và giới hạn của nó

Một hệ thống có thể có ba tầng kiểm soát:

```text
Application code
  - allowlist, không đưa secret vào events
  - typed config; không log toString/config map
              |
              v
Logging framework / encoder
  - structured schema
  - optional masking/sanitization as defense in depth
  - giới hạn ký tự/độ dài; exception handling
              |
              v
Collector / transport / storage
  - ingest rules, DLP scans, RBAC, encryption, retention
  - alert khi phát hiện credential pattern
```

**Không thể trông cậy hoàn toàn vào tầng collector**:
- Logger nội bộ hoặc console stdout có thể đã ghi secret trước khi collector xử lý.
- CR/LF và encoding làm regex bỏ sót.
- Stack traces và nested JSON có thể bypass message-only masking.
- Secret còn nằm trong archive, buffer, APM events hoặc client-side telemetry.
- Một giá trị API key không mang prefix nhận diện sẽ không khớp bộ lọc phổ biến.

Redaction tốt phải có chính sách kiểm thử; nó là hàng rào phụ. Ưu tiên sự kiện “component connection failed” không chứa raw URL, thay vì sự kiện chứa URL rồi cố gắng xóa credential.

## 16. Kiểm thử không rò rỉ bí mật

### 16.1. Dùng sentinel chỉ trong test

Tạo **dữ liệu giả không có giá trị xác thực** với chuỗi dễ nhận biết:
- `fake-token-for-test`.
- `fake-db-pass`.
- `fake-query`.

Chúng **không phải secret thật**. Capture log của ứng dụng rồi assert không chứa các sentinel này. Bài demo mục 9 đã kiểm tra chuỗi event `ConfigDiagnostics.safeStatus()`; bài integration test thực tế nên capture **toàn bộ logging output** từ backend đã dùng, bao gồm stdout, stderr, exception chain và structured fields.

### 16.2. Phép kiểm dương

Không chỉ check thiếu secret; còn phải xác nhận log hữu ích:
- Sự kiện `config_checked` có đúng component/tier/region được allowlist không?
- Thiếu required metadata dẫn đến failure code mà không in raw value?
- Validation có chặn newline, carriage return, ký tự điều khiển và chuỗi quá dài không?
- Debugging vẫn đủ dữ kiện định vị vấn đề bằng request ID/event ID?
- Cấu hình log level ở production có tuân thủ chính sách không?

### 16.3. Phép kiểm âm

Kiểm từng nguồn lộ:
1. Raw `Environment`/`MapPropertySource`.
2. `@ConfigurationProperties` object `toString()` hoặc Java record.
3. JDBC URI query và HTTP URL user-info.
4. Exception message và nested causes.
5. Header `Authorization` và cookie.
6. HTTP request/response body.
7. MDC/context map.
8. Startup diagnostics ở DEBUG/TRACE.
9. Actuator env/configprops exposure/show-values.
10. Archive/collector enrichment fields mà application không nhìn thấy.

### 16.4. Kiểm thử với log injection

Dùng metadata `app.region` chứa newline hoặc ký tự control giả, xác nhận formatter an toàn **từ chối** hoặc escape theo policy. Trong demo `safeLabel` chỉ cho regex `[a-z0-9-]{1,32}`; vì vậy newline và query string đều bị chặn. Không log chính giá trị bị từ chối trong catch block.

### 16.5. Tránh đưa secret thật vào artifact kiểm thử

Không copy token từ vault để test redaction. Nếu một screenshot hoặc test fixture có secret thật, nó có thể được lưu vào CI artifact/log/ticket. Dùng sentinel giả. Nếu cần kiểm integration với secret manager thật, kiểm **trạng thái kết nối** và policy, không log secret content.

## 17. Quy trình xử lý nếu đã lộ thông tin trong log

Khi nghi ngờ token/credential đã vào log:
1. Phân loại loại secret, scope truy cập và khoảng thời gian lộ.
2. Theo incident procedure, revoke/rotate secret bị ảnh hưởng trước hoặc song song với việc ngăn tiếp tục lộ.
3. Xác định mọi sink: console, application files, Docker/Kubernetes logs, APM, centralized collector, backup, dashboards, alerts, ticket attachment, export.
4. Hạn chế quyền truy cập tạm thời và thực hiện containment theo chính sách.
5. Tránh copy nguyên secret vào ticket, chat, incident document hoặc bản tường trình.
6. Sửa tại **nguồn tạo event** (không log raw config), thêm collector rules nếu cần.
7. Bổ sung tests với sentinel và quy trình review `DEBUG`/`TRACE`.
8. Rà soát access logs và audit logs của hệ thống đã đọc được secret.
9. Đánh giá retention, khả năng purge theo chính sách pháp lý/vận hành. Không khẳng định xóa khỏi dashboard là xóa toàn bộ bản sao.
10. Tóm tắt sự cố bằng identifier đã duyệt, không tái tiết lộ giá trị bị lộ.

Nếu credentials từng có thể bị người khác đọc, chỉ mask đoạn đã lưu mà **không rotate** thường chưa đủ: những giá trị đã bị sao chép vẫn có thể còn hợp lệ.

## 18. Những lỗi phổ biến và cách sửa

| Lỗi/triệu chứng | Tại sao nguy hiểm? | Cách sửa |
| --- | --- | --- |
| `logger.debug("env={}", env)` | Object có thể mang hoặc expose nguồn properties | Chỉ log metadata allowlisted |
| In `System.getenv()` | Có thể gồm token/password | Không dump environment variables |
| In `System.getProperties()` | Có thể có JVM `-Dpassword`/credential | Không dump toàn bộ JVM properties |
| Log nguyên JDBC URL | Query/userinfo có thể chứa password | Log component/status, không URI raw |
| Log toàn HTTP headers | Authorization/cookie lộ | Header allowlist, loại bỏ sensitive headers |
| Log body của auth endpoint | Password/token nằm trong body | Tắt raw body logging, dùng event/metrics |
| Log `@ConfigurationProperties` qua `toString()` | Generated toString có thể in secret field | Tạo safe diagnostic DTO |
| SLF4J placeholder chứa `apiKey` | Parameterized logging không mask | Không truyền secret vào argument |
| Log exception kèm root cause raw | Exception message có thể chứa URL/secret | Phân loại lỗi và kiểm soát error serialization |
| Regex chỉ che `password=` | Bỏ sót token, URI, encoding, JSON | Allowlist và parser nếu thật sự cần |
| Bật Spring DEBUG/TRACE trên production lâu dài | Thư viện khác có thể in dữ liệu không kiểm soát | Scope logging level, review và giới hạn thời gian |
| Dùng `management.endpoint.env.show-values=always` | Có thể expose raw config | Duy trì `never`, hạn chế endpoint |
| Gắn token vào MDC | Token bị lặp trong rất nhiều log entries | Chỉ dùng opaque correlation ID |
| Log region lấy raw từ user input | Log forging/injection | Validate enum/regex + max length |
| Dùng `apiKeyConfigured=true` ở nơi không được phép | Metadata cũng có thể nhạy cảm | Log validation status chung hoặc bỏ field |
| Bật file logging rồi bỏ retention | Secret nếu lộ tồn tại lâu | Retention/RBAC/transport control |
| Chỉ xóa dashboard sau leak | Bản sao và secret cũ vẫn hợp lệ | Rotate/revoke và tìm mọi sink |
| Assert “masked output” nhưng không test exceptions | Stack trace vẫn có raw secret | Capture toàn bộ event/cause/fields trong test |

## 19. Câu hỏi tự kiểm tra

1. `Environment` trong Spring Core có thể lấy properties từ những nguồn nào?
2. Vì sao `System.getenv()` là đối tượng không nên in thẳng vào log?
3. Sự khác nhau giữa blocklist và allowlist khi quyết định trường cấu hình được ghi là gì?
4. Một key tên `endpoint` có chắc không chứa credential không?
5. Vì sao chỉ xóa `password=...` bằng regex không đủ để sanitize JDBC/HTTP URL?
6. `@ConfigurationProperties` bean hoặc Java record có thể rò secret qua method nào?
7. `logger.debug("token={}", token)` có an toàn hơn concatenation về **bảo mật dữ liệu** không?
8. Khi một exception có message chứa URL kèm token, `logger.error("failed", ex)` có thể rò secret không?
9. Tại sao không log token fingerprint/hash tùy tiện?
10. Vì sao `containsProperty("api.token")` an toàn hơn log `getProperty("api.token")`, và có giới hạn gì?
11. Kết quả `management.endpoint.env.show-values=never` có che giá trị do application code gọi logger thủ công không?
12. `/actuator/env` được exposure và sanitization điều khiển độc lập như thế nào?
13. Nếu app có `@Lazy` secret provider, log Environment có trở nên an toàn tự động không?
14. Trong demo Java, vì sao `region` cần giới hạn ký tự và độ dài?
15. Nếu production log nhận `traceId` từ HTTP header, cần làm gì trước khi ghi?
16. Vì sao thiếu sentinel trong một event chưa đủ chứng minh không có secret trong mọi logs?
17. Nếu một token thật đã lộ trong log aggregator, hành động quan trọng ngoài việc mask/xóa dashboard là gì?
18. Structured JSON logging có tự động ẩn mọi secret trong object fields không?
19. Trong hệ thống nhiều replica/pod, log sink nào phải được xét khi có incident?
20. Khi kiểm tra startup config, dạng thông tin nào thường hữu ích hơn raw credential?

**Đáp án ngắn:** (1) PropertySource gồm JVM/env/file/map/nguồn custom tùy context. (2) Có thể chứa secrets. (3) Allowlist chỉ cho các field đã duyệt, an toàn hơn blacklist key dễ sót. (4) Không, URL có userinfo/query token. (5) Nhiều tên/format/encoding khác. (6) `toString()`/serialization. (7) Không, chỉ khác cơ chế format. (8) Có, nested cause cũng vậy. (9) Hash/fingerprint có thể gây lộ/khả năng liên kết. (10) Không đọc giá trị, nhưng sự hiện diện đôi khi nhạy cảm. (11) Không. (12) Exposure quyết định ai tới được endpoint; show-values quyết định hiển thị giá trị. (13) Không. (14) Chặn log injection. (15) Validate/generate opaque ID. (16) Log backend/exception/các thành phần khác vẫn có thể chứa secret. (17) Rotate/revoke và truy mọi sink. (18) Không, object serializer có thể serialize mọi field. (19) stdout/files, orchestrator logs, collector, APM, dashboard, backup, exports. (20) Component/status/version/validation code được duyệt.

## 20. Checklist cho code review và production

- [ ] Không có raw secrets trong message template, argument, MDC, throwable và structured fields.
- [ ] Không dump `Environment`, `System.getenv()`, `System.getProperties()` hoặc toàn bộ `PropertySource`.
- [ ] Không log `@ConfigurationProperties`/record chứa credential bằng `toString()`.
- [ ] Chỉ log allowlisted metadata; validate charset, enum/type và max length.
- [ ] Không log JDBC URL hoặc HTTP URL có userinfo/query/signature.
- [ ] Không log `Authorization`, cookies, form credentials hoặc request/response body nhạy cảm.
- [ ] Kiểm soát exception message, nested causes và global exception logger.
- [ ] Khi log status, không tiết lộ sự hiện diện secret nếu policy không cho phép.
- [ ] Parameterized logging được hiểu là formatting, **không phải redaction**.
- [ ] Nếu có `@Value`, constructor-bound config hoặc secret provider, đảm bảo code không echo giá trị.
- [ ] Kiểm tra DEBUG/TRACE trong ứng dụng và thư viện, có quy trình bật/tắt hạn chế.
- [ ] Spring Boot Actuator env/configprops được hạn chế exposure và mặc định hide values.
- [ ] Không đưa secret vào metrics tags, tracing baggage hoặc log enrichment.
- [ ] Logging transport/storage có encryption, RBAC, retention và audit.
- [ ] Collector masking/DLP chỉ là defense in depth, không thay giảm dữ liệu ở nguồn.
- [ ] Integration tests dùng **fake sentinel** và capture log events, kể cả exception chains.
- [ ] Có test newline/log forging và string quá dài ở trường được log.
- [ ] Có incident procedure rotation/revocation khi credentials lọt vào logs.
- [ ] Không đưa secrets thật vào source code, test fixtures, ticket, screenshot hoặc CI artifacts.
- [ ] Đối chiếu chính sách privacy/legal và threat model khi quyết định những metadata nào thực sự công bố.

## 21. Tài liệu tham khảo đáng tin cậy

- Spring Framework — Environment Abstraction (profiles, properties, PropertySources): https://docs.spring.io/spring-framework/reference/core/beans/environment.html
- Spring Framework 6.2 — Environment Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/Environment.html
- Spring Framework 6.2 — ConfigurableEnvironment Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/ConfigurableEnvironment.html
- Spring Framework 6.2 — MapPropertySource Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/MapPropertySource.html
- Spring Boot 3.4 — Actuator Endpoints, Sanitize Sensitive Values: https://docs.spring.io/spring-boot/3.4/reference/actuator/endpoints.html
- OWASP Cheat Sheet Series — Logging (Data to exclude, event sanitization, log injection): https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html
- OWASP Cheat Sheet Series — Secrets Management: https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html
- Maven Central — `org.springframework:spring-context:6.2.18`: https://central.sonatype.com/artifact/org.springframework/spring-context/6.2.18


</details>

- [Quay lại đầu trang](#back-to-top)
