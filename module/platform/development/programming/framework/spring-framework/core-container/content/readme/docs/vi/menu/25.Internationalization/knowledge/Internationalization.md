<a id="back-to-top"></a>

# Thông điệp đa ngôn ngữ

## Menu
- [MessageSource và vai trò trong ApplicationContext](#internationalization-001)
- [Quy ước tên bean messageSource](#internationalization-002)
- [ResourceBundleMessageSource và ReloadableResourceBundleMessageSource](#internationalization-003)
- [Basename, Locale và tham số định dạng thông điệp](#internationalization-004)
- [Thông điệp mặc định và xử lý khóa không tồn tại](#internationalization-005)
- [Thứ tự tra cứu, kế thừa và bộ nhớ đệm thông điệp](#internationalization-006)
- [Kiểm chứng mã hóa tiếng Việt và cơ chế dự phòng ngôn ngữ](#internationalization-007)

## <a id="internationalization-001">MessageSource và vai trò trong ApplicationContext</a>

<details>
<summary>Xem chi tiết</summary>

# MessageSource và vai trò trong ApplicationContext — thông điệp đa ngôn ngữ với Spring Framework

## 1. Mục tiêu bài học

Trong một ứng dụng phục vụ nhiều quốc gia, cùng một **mã thông điệp** có thể cần trả về văn bản khác nhau tùy ngôn ngữ của người dùng. Ví dụ mã `welcome` trả về “Welcome, Lan!” cho tiếng Anh và “Xin chào, Lan!” cho tiếng Việt. Nếu lưu trực tiếp các chuỗi đã dịch trong business code, chúng khó cập nhật, khó kiểm thử và dễ xuất hiện những nhánh `if (locale...)` rải rác.

Spring Framework cung cấp `org.springframework.context.MessageSource` — abstraction giải quyết **message code + arguments + Locale → văn bản đã bản địa hóa**. `ApplicationContext` triển khai giao diện này và tự phát hiện một bean tên chính xác `messageSource` khi khởi tạo.

Sau bài học, bạn có thể:

- Phân biệt **message code**, **Locale**, **arguments**, **default message** và **resource bundle**.
- Giải thích vì sao `ApplicationContext` vừa là IoC container vừa cung cấp `MessageSource`.
- Đăng ký đúng bean tên `messageSource` bằng Java configuration.
- Dùng ba overload `getMessage(...)` và xử lý `NoSuchMessageException`.
- Chọn `ResourceBundleMessageSource`, `ReloadableResourceBundleMessageSource` hoặc `StaticMessageSource` theo nhu cầu.
- Biết cách đặt file `messages.properties`, `messages_en.properties` và `messages_vi.properties`.
- Hiểu lookup theo locale, fallback, `MessageFormat`, bộ nhớ đệm và Unicode.
- Sử dụng `MessageSourceAware` hoặc constructor injection để cấp khả năng dịch cho bean.
- Hiểu **parent–child ApplicationContext** và việc một child message source fallback về parent.
- Tự chạy ví dụ Java 17/Spring Framework 6.2.18 với hai context, file tiếng Anh/Việt, arguments và exception.

## 2. MessageSource giải quyết vấn đề gì?

Một thông điệp thường có bốn phần:

| Thành phần | Ví dụ | Trách nhiệm |
| --- | --- | --- |
| `code` | `welcome` | Khóa ổn định, không chứa văn bản hiển thị |
| `args` | `new Object[]{"Lan"}` | Giá trị thay vào các vị trí `{0}`, `{1}`... |
| `Locale` | `Locale.ENGLISH`, `Locale.forLanguageTag("vi")` | Ngôn ngữ/vùng mong muốn |
| `defaultMessage` | `"Welcome!"` | Nội dung thay thế nếu không tìm được mã |

Minh họa:

```text
MessageSource
   |
   +-- code = "welcome"
   +-- args = ["Lan"]
   +-- locale = vi
   |
   v
messages_vi.properties
welcome=Xin chào, {0}!
   |
   v
Xin chào, Lan!
```

`MessageSource` chủ yếu xử lý **internationalization (i18n)** và message resolution. Nó không tự phát hiện ngôn ngữ người dùng từ một request, không phiên dịch bằng AI và không đảm bảo những bản dịch được biên tập chính xác. Bạn vẫn cần cung cấp các chuỗi đúng ngôn ngữ.

**Không nhầm với `Environment`:** `Environment.getProperty("app.timeout")` là tra cấu hình; `MessageSource.getMessage("welcome", ..., locale)` là tra văn bản bản địa hóa với format và fallback. Hai hệ thống đều có thể đọc properties nhưng khác mục đích, lifecycle và contract API.

## 3. ApplicationContext mở rộng BeanFactory như thế nào?

`org.springframework.context.ApplicationContext` kế thừa thêm nhiều giao diện bên cạnh `BeanFactory`. Trong đó có:

```text
BeanFactory
     ^
     |
ApplicationContext ----> MessageSource
            |---------> ResourcePatternResolver
            |---------> ApplicationEventPublisher
            |---------> EnvironmentCapable
            |---------> các khả năng context khác
```

Sơ đồ thể hiện **quan hệ khả năng**, không phải toàn bộ cây interface Java.

Do `ApplicationContext extends MessageSource`, bạn có thể gọi trực tiếp:

```java
String text = applicationContext.getMessage(
        "welcome",
        new Object[]{"Lan"},
        Locale.ENGLISH);
```

Mảnh này cần biến `applicationContext` và import `java.util.Locale`. Không phải ép kiểu context về `MessageSource` trước, mặc dù có thể phụ thuộc giao diện `MessageSource` trong service để hạn chế coupling.

Nếu dùng `BeanFactory` thuần, đừng tự giả định có toàn bộ cơ chế tìm bean `messageSource`, kế thừa message context, resolve locale và event/resource capabilities như một `ApplicationContext`.

## 4. Ba overload chính của MessageSource

Interface `MessageSource` khai báo ba dạng `getMessage` quan trọng:

```java
String getMessage(
        String code,
        Object[] args,
        String defaultMessage,
        Locale locale);

String getMessage(
        String code,
        Object[] args,
        Locale locale) throws NoSuchMessageException;

String getMessage(
        MessageSourceResolvable resolvable,
        Locale locale) throws NoSuchMessageException;
```

Đây là **chữ ký rút gọn** từ API để diễn giải, không phải một interface độc lập có đủ imports và nullability annotations.

### Dạng có default

```java
String title = messages.getMessage(
        "screen.title",
        null,
        "Untitled",
        Locale.ENGLISH);
```

Nếu `screen.title` không resolve được và không có fallback từ parent, trả `"Untitled"`. Giá trị default có thể được format với arguments theo cơ chế của `MessageSource`. Nếu truyền `null` cho default và code không tồn tại, overload này có thể trả `null`.

### Dạng không default

```java
String title = messages.getMessage(
        "screen.title",
        null,
        Locale.ENGLISH);
```

Nếu không tìm thấy, ném `NoSuchMessageException`. Dạng này phù hợp với thông điệp bắt buộc, nơi thiếu bản dịch là một cấu hình sai cần nhìn thấy thay vì âm thầm trả text fallback.

### Dạng MessageSourceResolvable

Một đối tượng `MessageSourceResolvable` có thể chứa **nhiều codes theo thứ tự ưu tiên**, arguments và default. Ví dụ lỗi validation có thể thử trước `user.email.required`, sau đó `field.required`, cuối cùng một default message.

```java
DefaultMessageSourceResolvable error =
        new DefaultMessageSourceResolvable(
                new String[]{
                        "user.email.required",
                        "field.required"
                },
                new Object[]{"email"},
                "Field is required");

String message = messages.getMessage(
        error, Locale.ENGLISH);
```

Đây là mảnh minh họa với import `org.springframework.context.support.DefaultMessageSourceResolvable`, biến `messages` kiểu `MessageSource` và `Locale`. Spring thử các codes theo thứ tự; không bắt buộc một object chỉ có một code.

## 5. Bean đặc biệt tên messageSource

Khi `ApplicationContext.refresh()` chuẩn bị khả năng i18n, Spring tìm một **bean cục bộ tên chính xác `messageSource`** và dùng nó làm strategy cho `context.getMessage(...)`. Nó không tự chọn một bean chỉ vì kiểu `MessageSource` hoặc vì bean được đánh dấu `@Primary`.

```java
@Bean("messageSource")
MessageSource messages() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("i18n/messages");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return source;
}
```

Snippet này nằm trong một configuration class có imports tương ứng. **`messageSource` là bean name; `i18n/messages` là basename** của resource bundles. Nếu không có bean local đúng tên, context có thể ủy quyền lên parent; khi cả parent không có, nó sử dụng `DelegatingMessageSource` rỗng, vẫn nhận lời gọi `getMessage` nhưng không tự tạo bản dịch.

Một bean tên `translations` có thể được inject hoặc gọi trực tiếp, nhưng `context.getMessage` không tự sử dụng nó. Bài 258 dành trọn một ví dụ runnable để phân biệt các trường hợp đúng tên, sai tên, không có bean và hierarchy.

## 6. Ba implementation điển hình

Ba implementation cung cấp cùng hợp đồng `MessageSource`, nhưng nguồn dữ liệu khác nhau:

| Implementation | Cách dùng điển hình | Điều không nên suy diễn |
| --- | --- | --- |
| `ResourceBundleMessageSource` | Bundles `.properties` đóng gói trên classpath/JAR theo cơ chế JDK | Không phải file watcher hoặc công cụ sửa bản dịch runtime |
| `ReloadableResourceBundleMessageSource` | Spring `Resource`, nhất là `file:` bên ngoài JAR và cache timeout | Không bảo đảm hot reload đối với entry bên trong JAR |
| `StaticMessageSource` | Message patterns được nạp bằng Java code cho demo/test | Không tự đọc catalog từ resources |

Lúc học, ưu tiên `ResourceBundleMessageSource` cho dữ liệu phát hành cùng ứng dụng và `StaticMessageSource` cho unit test. Bài 259 so sánh riêng hai implementation dùng bundles, cache intervals, packaged JAR và ví dụ runnable trên cùng files.

## 7. Cấu trúc resource bundles

Với basename `messages`, root classpath có thể chứa:

```text
src/main/resources/
├── messages.properties
├── messages_en.properties
├── messages_vi.properties
└── messages_en_GB.properties
```

Ví dụ root: `welcome=Welcome, {0}!`; Vietnamese: `welcome=Xin chào, {0}!`. Spring dùng `Locale` để tìm biến thể theo ngôn ngữ/vùng. Request `vi-VN` có thể lấy `messages_vi.properties` khi không có bản `vi_VN`. File gốc cung cấp các keys còn thiếu nếu policy cho phép.

Các files là **bản dịch do ứng dụng cung cấp**, không phải Spring tự dịch. Kiểm tra basename, tên file/encoding và việc resources được đóng gói; tên `messageSource` của bean hoàn toàn độc lập với basename. Bài 260 hướng dẫn cấu trúc basename, Locale suffix, MessageFormat và các trục fallback.

## 8. Đăng ký ResourceBundleMessageSource bằng Java configuration

Ví dụ:

```java
@Configuration(proxyBeanMethods = false)
class I18nConfig {

    @Bean("messageSource")
    MessageSource messageSource() {
        ResourceBundleMessageSource source =
                new ResourceBundleMessageSource();

        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);

        return source;
    }
}
```

Đây là class cấu hình minh họa, cần imports `@Configuration`, `@Bean`, `MessageSource` và `ResourceBundleMessageSource`. File tương ứng nằm tại `src/main/resources/messages*.properties`.

Các options:

- `setBasename("messages")`: chỉ định **basename**, không kèm suffix `.properties` hoặc `_vi`.
- `setBasenames("messages", "errors", "labels")`: hỗ trợ nhiều nhóm; thứ tự và chính sách lookup quan trọng nếu các bundles chứa cùng key.
- `setDefaultEncoding("UTF-8")`: đọc các file properties chứa Unicode; đặc biệt nên khai báo rõ khi dùng tiếng Việt.
- `setFallbackToSystemLocale(false)`: không fallback theo default locale của máy chủ trước khi đến bundle chung.

`ResourceBundleMessageSource` dựa trên cơ chế Java `ResourceBundle`; lưu ý classpath/module path có thể ảnh hưởng encoding và lookup. Trên Spring 6.2 classpath, thiết lập encoding có ý nghĩa trực tiếp; module path trên JDK hiện đại có những giới hạn `ResourceBundle.Control` riêng.

## 9. Locale là lựa chọn của caller, không phải suy đoán

`Locale` được truyền cho `MessageSource.getMessage`, ví dụ `Locale.ENGLISH`, `Locale.UK`, `Locale.forLanguageTag("vi-VN")`. Nó không tự đọc IP/quốc gia/ngôn ngữ của request. Tầng web (như Spring MVC `LocaleResolver`) hoặc ứng dụng phải **chọn một locale phù hợp với người dùng** và truyền vào lookup.

Với batch/email/background jobs, truyền Locale của người nhận tường minh; `Locale.getDefault()` là trạng thái JVM và `LocaleContextHolder` không mặc nhiên được propagate sang mọi thread. Đừng lẫn `Locale` với `@Profile`: cái đầu chọn message variant, cái sau tham gia lựa chọn bean definitions.

## 10. Placeholder tham số: MessageFormat không phải String.format

Spring dùng `java.text.MessageFormat` cho arguments: `{0}`, `{1}`, `{0,number}`, `{0,date,short}` và các pattern theo Locale.

```properties
welcome=Xin chào, {0}!
order.summary=Đơn {0} có {1,number,integer} sản phẩm.
```

```java
messages.getMessage("order.summary",
        new Object[]{"A-10", 3}, Locale.forLanguageTag("vi-VN"));
```

Kết quả: `Đơn A-10 có 3 sản phẩm.`. Đây là syntax `MessageFormat`, khác `%s` của `String.format`. Apostrophe `'` là dấu quote của pattern; thường cần `''` để hiển thị apostrophe literal nếu message được format. Những trường hợp no-argument còn phụ thuộc `alwaysUseMessageFormat`.

Bài 260 đi sâu vào basename/format types, quote/braces, number/date, Locale/timezone và test arguments.

## 11. Các kiểu fallback cần phân biệt

Từ “fallback” có thể chỉ các tầng độc lập: **Locale candidates** (ví dụ vi_VN → vi → root bundle), **thứ tự basenames**, **parent MessageSource**, **danh sách code candidates** của `MessageSourceResolvable`, rồi **defaultMessage/null/`NoSuchMessageException`** tùy overload. Không phải mọi implementation, default Locale và cache configuration dùng cùng một thứ tự tuyệt đối.

Một snippet phân biệt default và strict:

```java
messages.getMessage("missing.key", null, "Dự phòng", locale);
// Nếu không nguồn nào giải quyết mã: "Dự phòng".
messages.getMessage("missing.key", null, locale);
// Nếu thiếu và không có chế độ fallback riêng: NoSuchMessageException.
```

Bật `useCodeAsDefaultMessage(true)` có thể làm UI lộ mã kỹ thuật và ảnh hưởng chuỗi candidate codes trong hierarchy; tránh dùng như production policy mặc định. Bài 261 phân tích missing-key và bài 262 giải thích thứ tự basename/parent/cache bằng ví dụ đầy đủ.

## 12. MessageSourceAware: cung cấp message source cho bean

Spring hỗ trợ interface:

```java
public interface MessageSourceAware extends Aware {
    void setMessageSource(MessageSource messageSource);
}
```

Đây là đoạn chữ ký API minh họa. Bean được Spring ApplicationContext quản lý và implements `MessageSourceAware` sẽ được inject message source trong quá trình initialization callbacks.

Ví dụ:

```java
public final class GreetingService implements MessageSourceAware {

    private MessageSource messages;

    @Override
    public void setMessageSource(MessageSource messages) {
        this.messages = messages;
    }

    public String greet(String name, Locale locale) {
        return messages.getMessage(
                "welcome",
                new Object[]{name},
                locale);
    }
}
```

Đây là class minh họa, cần imports `MessageSource`, `MessageSourceAware`, `Locale`. Nếu bạn tự `new GreetingService()` ở ngoài container mà không gọi setter, field messages sẽ null; Aware callbacks chỉ là một phần của Spring bean lifecycle.

### Constructor injection có được không?

Có. Khi muốn unit test dễ hơn, một service có thể nhận `MessageSource` qua constructor và không implement `MessageSourceAware`. Tuy nhiên, chú ý **context cũng implements MessageSource** và một bean tên `messageSource` cũng có thể hiện diện. Trong cấu hình phức tạp có nhiều beans cùng type, constructor injection phải được cấu hình đủ rõ (qualifier hoặc lựa chọn candidate) để không vô tình nhận nhầm strategy.

Nếu class chỉ cần một API dịch thông điệp, phụ thuộc `MessageSource` thay vì toàn bộ `ApplicationContext` sẽ giữ coupling nhỏ hơn.

## 13. Parent–child ApplicationContext và HierarchicalMessageSource

Spring cho phép cây contexts:

```text
Parent ApplicationContext
  messageSource:
      common.title = Parent title
          |
          v
Child ApplicationContext
  messageSource:
      screen.name = Child screen
          |
          v
child.getMessage(...)
    - nếu child giải được mã: dùng bản child
    - nếu không: thử message source của parent
    - nếu không đâu có: dùng default / throw
```

Cơ chế này thường xuất hiện trong các ứng dụng có root context và child web context, nhưng cũng có thể minh họa với hai `AnnotationConfigApplicationContext`.

Các implementations như `ResourceBundleMessageSource`, `ReloadableResourceBundleMessageSource`, `StaticMessageSource` hỗ trợ `HierarchicalMessageSource`. Khi context con có messageSource cục bộ, Spring có thể gắn parent message source để lookup các mã thiếu.

**Quan trọng:** context hierarchy không có nghĩa mọi file messages của hai contexts tự merge thành một file duy nhất. Nó là **fallback giữa hai message source objects**. Nếu child đã có một code cho requested locale/fallback bundle, child có thể thắng parent code tương ứng.

Nếu child **không có** bean `messageSource`, container cung cấp `DelegatingMessageSource` nội bộ để chuyển lookup tới parent khi khả dụng.

## 14. Ví dụ Java 17 đầy đủ: tiếng Anh, tiếng Việt và fallback context cha

Ví dụ sử dụng **Spring Framework 6.2.18**, không dùng Spring Boot. Nó tạo:

- Parent context: bean `messageSource` kiểu `StaticMessageSource` với hai thông điệp `parent.info` (English/Vietnamese).
- Child context: bean `messageSource` kiểu `ResourceBundleMessageSource` đọc `messages_en.properties` và `messages_vi.properties`; bean `Greeter` implements `MessageSourceAware`.
- Các lời gọi trực tiếp `child.getMessage(...)` minh họa hai ngôn ngữ, argument substitution, default, exception và parent fallback.
- Trình tự tạo contexts rõ ràng; child đóng trước parent; đầu ra không phụ thuộc `Locale.getDefault()` của máy vì fallback system locale bị tắt và các locales được truyền tường minh.

### Thư mục dự án

```text
message-source-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        ├── java/
        │   └── MessageSourceDemo.java
        └── resources/
            ├── messages_en.properties
            └── messages_vi.properties
```

### settings.gradle

```groovy
rootProject.name = 'message-source-demo'
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
    mainClass = 'MessageSourceDemo'
}
```

### src/main/resources/messages_en.properties

```properties
welcome=Hello, {0}!
```

### src/main/resources/messages_vi.properties

```properties
welcome=Xin chào, {0}!
```

Lưu hai files ở UTF-8 và **không thêm dấu nháy quanh `{0}`**. Trong ví dụ, basename `"messages"` tương ứng hai files này. Không có file gốc `messages.properties`, cũng không có mã `parent.info` trong child files; điều này giúp minh họa parent fallback một cách rõ ràng.

### src/main/java/MessageSourceDemo.java

```java
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.support.StaticMessageSource;

public class MessageSourceDemo {

    public static final class Greeter implements MessageSourceAware {
        private MessageSource messages;

        @Override
        public void setMessageSource(MessageSource messages) {
            this.messages = messages;
        }

        public String greet(String name, Locale locale) {
            return messages.getMessage(
                    "welcome", new Object[]{name}, locale);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ParentConfig {

        @Bean("messageSource")
        public StaticMessageSource messageSource() {
            StaticMessageSource messages = new StaticMessageSource();
            messages.addMessage(
                    "parent.info",
                    Locale.ENGLISH,
                    "From parent");
            messages.addMessage(
                    "parent.info",
                    Locale.forLanguageTag("vi"),
                    "Từ cha");
            return messages;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ChildConfig {

        @Bean("messageSource")
        public MessageSource messageSource() {
            ResourceBundleMessageSource messages =
                    new ResourceBundleMessageSource();
            messages.setBasename("messages");
            messages.setDefaultEncoding("UTF-8");
            messages.setFallbackToSystemLocale(false);
            return messages;
        }

        @Bean
        public Greeter greeter() {
            return new Greeter();
        }
    }

    public static void main(String[] args) {
        Locale vi = Locale.forLanguageTag("vi");

        try (AnnotationConfigApplicationContext parent =
                     new AnnotationConfigApplicationContext()) {

            parent.register(ParentConfig.class);
            parent.refresh();

            try (AnnotationConfigApplicationContext child =
                         new AnnotationConfigApplicationContext()) {

                child.setParent(parent);
                child.register(ChildConfig.class);
                child.refresh();

                System.out.println("english="
                        + child.getMessage(
                                "welcome",
                                new Object[]{"Lan"},
                                Locale.ENGLISH));

                System.out.println("vietnamese="
                        + child.getMessage(
                                "welcome",
                                new Object[]{"Lan"},
                                vi));

                System.out.println("parent.en="
                        + child.getMessage(
                                "parent.info", null, Locale.ENGLISH));

                System.out.println("parent.vi="
                        + child.getMessage(
                                "parent.info", null, vi));

                System.out.println("default="
                        + child.getMessage(
                                "missing.code",
                                null,
                                "Fallback value",
                                Locale.ENGLISH));

                try {
                    child.getMessage(
                            "missing.code", null, Locale.ENGLISH);
                    throw new AssertionError(
                            "Expected missing-message failure");
                } catch (NoSuchMessageException ex) {
                    System.out.println(
                            "missing=NoSuchMessageException");
                }

                System.out.println("aware="
                        + child.getBean(Greeter.class)
                                .greet("Minh", vi));
            }
        }
    }
}
```

### Chạy

Yêu cầu Java 17+ và Gradle 8+:

```bash
gradle run --quiet
# Hoặc ./gradlew run --quiet nếu đã tạo Gradle Wrapper
```

### System.out dự kiến

```text
english=Hello, Lan!
vietnamese=Xin chào, Lan!
parent.en=From parent
parent.vi=Từ cha
default=Fallback value
missing=NoSuchMessageException
aware=Xin chào, Minh!
```

Các dòng trên là output do chương trình tự in; log Gradle hoặc logger của Spring, nếu có, không nằm trong bảng này.

### Vì sao từng dòng xuất hiện?

**english/vietnamese:** child `ResourceBundleMessageSource` resolve code `welcome` từ `messages_en.properties` hoặc `messages_vi.properties`, rồi `MessageFormat` thay `{0}` bằng tên `Lan`.

**parent.en/parent.vi:** child bundle không định nghĩa `parent.info`; Spring nối message source của child với source thuộc parent context, nên `StaticMessageSource` ở parent cung cấp bản dịch đúng locale.

**default:** cả child và parent không định nghĩa `missing.code`, nhưng caller dùng overload có `defaultMessage="Fallback value"`.

**missing:** cùng code thiếu nhưng caller dùng overload **không có default**, vì thế `NoSuchMessageException` được bắt và in tên loại exception.

**aware:** bean `Greeter` do container tạo được inject `MessageSource` thông qua `MessageSourceAware.setMessageSource`. Khi gọi `greet("Minh",vi)`, bean này dùng cùng cơ chế tra message của ApplicationContext.

**Lifecycle:** `child.refresh()` xảy ra sau khi `child.setParent(parent)` và parent đã refresh, nên cả hai context có quan hệ hierarchy hợp lệ. `try-with-resources` đóng child trước parent, tránh bỏ sót cleanup.

## 15. ReloadableResourceBundleMessageSource khác gì?

`ReloadableResourceBundleMessageSource` không dựa hoàn toàn vào JDK `ResourceBundle`; nó đọc Spring resources và có các options cache/refresh linh hoạt.

Ví dụ minh họa dành cho trường hợp messages nằm ngoài JAR:

```java
@Bean("messageSource")
MessageSource messageSource() {
    ReloadableResourceBundleMessageSource src =
            new ReloadableResourceBundleMessageSource();

    src.setBasename("file:/opt/myapp/i18n/messages");
    src.setDefaultEncoding("UTF-8");
    src.setCacheSeconds(30);
    src.setFallbackToSystemLocale(false);

    return src;
}
```

Đây là **mảnh cấu hình**, cần import `ReloadableResourceBundleMessageSource`, `MessageSource` và `@Bean`. Đường dẫn ví dụ cần file vật lý thực tế, không phải đường dẫn bắt buộc của mọi triển khai.

Sự khác biệt:

- `ResourceBundleMessageSource` phù hợp với resources đóng trong classpath/JAR, tối ưu caching theo ResourceBundle.
- `ReloadableResourceBundleMessageSource` có thể nạp từ Spring resource locations như `classpath:`, `file:` và hỗ trợ kiểm tra thay đổi theo cache policy.
- Để reload thực sự, resource phải có khả năng đọc dữ liệu mới. **File nằm trong JAR không nên được coi là có thể hot reload ổn định**; một file ngoài JAR với `file:` phù hợp hơn.
- Cache timeout **không bảo đảm** mọi request ngay lập tức thấy bản dịch mới: có thời gian giữ cache, thay đổi file, timestamp checks và race conditions.
- Nếu nhiều nodes chạy sau load balancer, cần cơ chế triển khai bản dịch nhất quán; message source riêng trên từng node không tự đồng bộ.

## 16. Mã message tốt và tính ổn định

Message code nên có tên ổn định, thể hiện ngữ nghĩa:

```properties
account.login.title=Sign in
account.login.error.invalidCredentials=Invalid email or password.
validation.user.email.required=Email is required.
order.confirmation.summary=Order {0} has {1} items.
```

Tránh dùng nguyên câu tiếng Anh làm key hoặc ghép code trực tiếp từ input người dùng. Khi thay nội dung dịch, code có thể giữ nguyên để không phải sửa Java source. Với nhiều modules, prefix theo domain/module giúp tránh va chạm: `billing.invoice.paid`, `auth.login.failed`, `catalog.search.empty`.

Tách **thông điệp hiển thị** khỏi **dữ liệu nghiệp vụ**. `MessageSource` không thích hợp để lưu URL endpoint, DB password hay các tham số vận hành; `Environment`/configuration facilities mới dành cho loại dữ liệu đó.

### Placeholder chính xác giữa các ngôn ngữ

Nếu tiếng Anh có `{0}` và `{1}`, bản dịch tiếng Việt phải giữ arguments tương ứng nhưng được phép đảo thứ tự phù hợp ngữ pháp. Đừng tự nối chuỗi `"Hello " + name` trong Java vì các ngôn ngữ có thể đặt tên ở vị trí khác.

Sử dụng tools/CI để kiểm tra:
- Code tồn tại ở locale mặc định.
- Không có duplicate keys không được mong muốn.
- Các MessageFormat placeholder indices hợp lệ.
- Không có syntax apostrophe/curly brace sai.
- Không để lộ secret values vào message arguments/logs.

## 17. Cách xử lý lỗi: fail-fast hay fallback?

| Tình huống | Hướng xử lý |
| --- | --- |
| Chuỗi UI không bắt buộc | Có thể dùng defaultMessage rõ nghĩa |
| Message code của lỗi validation quan trọng | Có thể thử một tập codes qua MessageSourceResolvable |
| Thiếu key cốt lõi mà đáng ra luôn có | Dùng overload không default và test để fail |
| Locale cụ thể không có translation | Tận dụng fallback tới language/bundle chung theo chính sách |
| Parent có mã dùng chung | Dùng HierarchicalMessageSource/context hierarchy |
| File bundle không được đóng gói | Kiểm tra classpath và basename trong build artifact |
| MessageFormat pattern sai | Test render với arguments để phát hiện |
| Locale từ request không tin cậy | Validate/chọn danh sách locales được hỗ trợ trước khi lookup |

Không nên dùng `catch (NoSuchMessageException) { return code; }` vô điều kiện cho mọi trường hợp production. Nó biến sai sót localization thành mã kỹ thuật hiển thị với người dùng và có thể gây nhầm lẫn.

Nếu defaultMessage dùng tiếng Anh trong một app đa ngôn ngữ, đó là **chính sách sản phẩm** cần được công bố: người dùng thiếu bản dịch sẽ thấy tiếng Anh hay một ngôn ngữ mặc định khác.

## 18. MessageSource, BeanFactory và các hệ thống xung quanh

**MessageSource vs LocaleResolver:** MessageSource giải mã theo `Locale`; ở Spring MVC, lớp web có thể quyết định Locale từ request và đưa vào quá trình render. Không nhầm hai nhiệm vụ.

**MessageSource vs ResourceLoader:** ResourceLoader mở file/stream; MessageSource biết về code, Locale, message arguments và fallback. Một message source implementation có thể tự dùng resources ở phía dưới.

**MessageSource vs PropertySources:** file `messages_vi.properties` là resource bundle bản địa hóa, không tự được đưa vào `Environment.getPropertySources()` để cấu hình `@Value`. Cấu hình ứng dụng và ngôn ngữ giao diện cần được quản lý độc lập.

**MessageSource vs Validation:** error object hoặc binding errors có thể biểu diễn bằng `MessageSourceResolvable` và danh sách codes; MessageSource chỉ **render** nội dung, không quyết định bản thân một field có hợp lệ hay không.

**MessageSource vs Spring Boot:** Spring Boot thường có auto-configuration cho message source và basename conventions. Nhưng ví dụ ở đây là **Spring Framework thuần**; không khẳng định file `messages.properties` tự được đăng ký vào một `AnnotationConfigApplicationContext` nếu bạn chưa định nghĩa bean `messageSource`.

**Parent context:** fallback của parent xảy ra theo context hierarchy khi message source hỗ trợ hợp đồng; không phải mọi bean tên giống nhau giữa parent/child đều được merge.

## 19. Troubleshooting và checklist

| Hiện tượng | Nguyên nhân thường gặp | Cách kiểm tra |
| --- | --- | --- |
| `NoSuchMessageException` | Code không có tại locale/fallback/parent | Kiểm tra key, basename, files, locale |
| Context trả default dù có file messages.properties | Chưa có bean tên `messageSource` | Đăng ký bean đúng tên, đảm bảo context refresh |
| Bean message source có tên `translations` | Không được nhận diện theo special bean name | Đổi thành `messageSource` hoặc cấu hình đúng strategy |
| `welcome` thành văn bản nguyên có `{0}` | Không truyền args, pattern/caching config | Truyền `new Object[]{...}`, test MessageFormat |
| Dấu nháy đơn mất hoặc placeholder không đổi | Quy tắc quote của MessageFormat | Escape apostrophe bằng hai dấu `''` khi cần |
| Văn bản Việt lỗi ký tự | Encoding của file không đúng hoặc loader sai | Lưu UTF-8, `setDefaultEncoding("UTF-8")` |
| Chuỗi tiếng Anh xuất hiện khi yêu cầu tiếng Việt | Thiếu bundle vi, fallback tới default | Kiểm tra tên file, Locale, system locale fallback |
| Thay file mà nội dung không đổi | Cache/JAR resource không tự reload | Restart hoặc dùng reloadable source với file ngoài JAR |
| Child không nhận mã từ parent | Không gắn parent, source không hierarchical, parent chưa refresh | Kiểm tra `setParent` trước `refresh` |
| getMessage trả mã kỹ thuật thay vì lỗi | `useCodeAsDefaultMessage` được bật | Kiểm tra chính sách fallback |
| Không tìm thấy file bên trong JAR | File không nằm trong resources của build | Kiểm tra nội dung artifact và basename |
| Code dựa vào LocaleContextHolder sai khi async | Thread-local locale không được propagated | Truyền Locale tường minh hoặc propagation policy |

Checklist trước production:

1. Bean `messageSource` có đúng tên và kiểu `MessageSource` không?
2. Tất cả bundles có đúng basename, đường dẫn và ngôn ngữ không?
3. File encoding có thống nhất UTF-8 và được đóng vào JAR đúng không?
4. Default/system locale fallback có đúng chính sách sản phẩm không?
5. Các arguments/MessageFormat patterns được kiểm thử ở từng locale không?
6. Thiếu code sẽ trả default hay fail? Có monitoring thích hợp không?
7. Các child contexts cần chia sẻ messages của parent đã được kết nối đúng chưa?
8. Không kỳ vọng file trong JAR hot reload nếu implementation không hỗ trợ.
9. Có test trên runtime đóng gói, không chỉ IDE classpath?
10. Dịch có được phân biệt khỏi dữ liệu cấu hình/validation/routing?
11. Có cơ chế phát hiện missing translation keys và duplicate keys khi build không?
12. Có tránh lộ dữ liệu riêng tư/secret vào log khi format arguments không?

## 20. Bài tập tự luyện

**Bài tập 1:** Bổ sung `messages_fr.properties` với `welcome=Bonjour, {0}!`, gọi `getMessage` với `Locale.FRENCH`. Kiểm tra không phải sửa code gọi `welcome`.

**Bài tập 2:** Thêm code `welcome` vào parent và vào child ở một locale; minh họa **child ưu tiên message của chính nó**, còn khi code không có ở child thì fallback parent.

**Bài tập 3:** Thêm message `order.total={0,number} items`; gọi với số `12500` trên hai locales và quan sát định dạng. Tránh assert một chuỗi số cụ thể nếu locale provider/JDK của môi trường có khác biệt về grouping.

**Bài tập 4:** Xóa `messages_vi.properties`, tắt system-locale fallback và xem behavior trong từng trường hợp có/không có `messages.properties` và parent messages.

**Bài tập 5:** Dùng `DefaultMessageSourceResolvable` với hai codes `error.email.required` và `error.required`. Kiểm tra code thứ hai được dùng khi code đầu không tồn tại.

**Bài tập 6:** Chuyển sang `ReloadableResourceBundleMessageSource` với file ngoài JAR, cấu hình cache và kiểm chứng thay đổi được nhìn thấy theo policy đã đặt, không giả định refresh tức thời.

## 21. Tài liệu chính thức

1. [Spring Framework Reference — Additional Capabilities of the ApplicationContext / MessageSource](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html): bean đặc biệt tên `messageSource`, ba overload, nested contexts và ResourceBundle semantics.
2. [Spring Framework 6.2 Javadoc — MessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/MessageSource.html): giao diện lookup, overload có/không default và `MessageSourceResolvable`.
3. [Spring Framework 6.2 Javadoc — AbstractApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/AbstractApplicationContext.html): `MESSAGE_SOURCE_BEAN_NAME`, delegate và context hierarchy.
4. [Spring Framework 6.2 Javadoc — ResourceBundleMessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/ResourceBundleMessageSource.html): JDK ResourceBundle, basename, encoding, caching.
5. [Spring Framework 6.2 Javadoc — ReloadableResourceBundleMessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/ReloadableResourceBundleMessageSource.html): Spring Resource locations và cache/reload.
6. [Spring Framework 6.2 Javadoc — StaticMessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/StaticMessageSource.html): thêm message programmatically trong tests.
7. [Spring Framework 6.2 Javadoc — HierarchicalMessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/HierarchicalMessageSource.html): parent message source.
8. [Spring Framework 6.2 Javadoc — MessageSourceAware](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/MessageSourceAware.html): lifecycle callback.
9. [Java SE 17 — MessageFormat](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/MessageFormat.html): arguments, number/date formatting và apostrophe quoting.

**Ghi nhớ:** `ApplicationContext` là một `MessageSource`. Khi khởi tạo, nó tìm bean tên **`messageSource`** và dùng bean đó để resolve mã theo Locale; nếu thiếu, nó có thể ủy quyền cho parent hoặc dùng một `DelegatingMessageSource` rỗng. Dùng resource bundles để tách nội dung dịch khỏi Java, `MessageFormat` để thay arguments, `MessageSourceAware`/injection để dùng trong bean và đặt chính sách fallback/thiếu mã rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="internationalization-002">Quy ước tên bean messageSource</a>

<details>
<summary>Xem chi tiết</summary>

# Quy ước tên bean messageSource trong Spring Framework

## 1. Mục tiêu và điểm cốt lõi

Spring Core Container không chỉ tạo và tiêm phụ thuộc cho các object. Interface `ApplicationContext` còn kế thừa `MessageSource` để cung cấp các thông điệp theo ngôn ngữ/vùng miền (i18n), thường dùng cho nhãn giao diện, thông báo lỗi, validation và nội dung phản hồi.

Một câu hỏi rất quan trọng là: **Spring chọn bean nào làm nguồn thông điệp của toàn bộ ApplicationContext?** Câu trả lời không đơn thuần là “bất kỳ bean có kiểu `MessageSource`”. Theo quy ước của Spring Framework, container **tìm bean mang tên chính xác `messageSource`**.

~~~text
ApplicationContext.refresh()
          |
          v
Có bean tên "messageSource" trong context hiện tại?
          |
     +----+----+
     |         |
    Có        Không
     |         |
     v         v
Dùng bean     Tìm nguồn thông điệp của parent context
MessageSource  |
               +---- Có ----> Dùng parent làm nguồn fallback
               |
               +---- Không --> DelegatingMessageSource rỗng
          |
          v
ApplicationContext.getMessage(...) sử dụng nguồn đã chọn
~~~

Nếu bạn viết:

~~~java
@Bean("translations")
public MessageSource translationService() { ... }
~~~

thì bean đó vẫn có thể được inject hoặc gọi trực tiếp, **nhưng Spring không tự xem nó là `messageSource` của ApplicationContext** chỉ vì cùng Java type.

Mục tiêu của bài thuộc nhóm **Thông điệp đa ngôn ngữ**, module **Spring Framework - core-container**:

1. Hiểu chính xác ý nghĩa **tên bean đặc biệt `messageSource`**, phân biệt bean name với Java class/method name và type.
2. Khai báo đúng bằng `@Bean`, `@Component` hoặc XML.
3. Nắm cơ chế tự nhận diện khi refresh, fallback sang parent và `DelegatingMessageSource` nếu không có nguồn.
4. Biết dùng `getMessage`, xử lý mã thông điệp thiếu, `Locale` và `MessageFormat`.
5. Chọn `ResourceBundleMessageSource`, `ReloadableResourceBundleMessageSource` hoặc `StaticMessageSource` theo tình huống.
6. Thấy tác động thực tế khi đặt **đúng/sai tên** qua chương trình Java 17 + Maven chạy độc lập.
7. Tránh nhầm lẫn giữa quy ước Spring Core và Spring Boot auto-configuration.
8. Kiểm thử các trường hợp không có bean, bean sai tên, context phân cấp, thiếu key và fallback.

## 2. MessageSource là gì?

`MessageSource` giải quyết **message code + arguments + Locale** và có ba overload cơ bản:

```java
getMessage(code, args, defaultMessage, locale);
getMessage(code, args, locale);
getMessage(resolvable, locale);
```

Đây là pseudocode API, không phải method calls độc lập có đầy đủ biến/kiểu. Dạng có default có thể trả fallback hoặc `null`; dạng strict có thể ném `NoSuchMessageException`; dạng nhận `MessageSourceResolvable` có thể thử nhiều codes theo thứ tự. Ví dụ `getMessage("hello", new Object[]{"Linh"}, Locale.ENGLISH)` sử dụng bản dịch của `hello` và chèn `Linh` vào `{0}` nếu catalog cung cấp pattern tương ứng.

**Điều bài này cần nhớ:** Spring tích hợp message service trong `ApplicationContext` qua **bean name** `messageSource`; các overload trên không làm Spring tự chọn bất kỳ bean cùng type. Bài 261 phân biệt đầy đủ missing codes/default/strict semantics.

## 3. Vì sao bắt buộc tên messageSource?

`ApplicationContext` kế thừa `MessageSource`. Khi context được làm mới (`refresh()`), phần khởi tạo nội bộ tìm một bean theo **tên dành riêng `messageSource`** để tích hợp vào chính context.

Trường hợp đúng:

~~~java
@Configuration
public class I18nConfig {

    @Bean("messageSource")
    public MessageSource translations() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage(
                "hello", Locale.ENGLISH, "Hello!");
        return source;
    }
}
~~~

Đây là **snippet cấu hình**; cần imports `Configuration`, `Bean`, `MessageSource`, `StaticMessageSource` và `Locale`.

Tuy Java method tên `translations`, `@Bean("messageSource")` quy định bean name là `messageSource`. Container tìm được bean đó và `context.getMessage(...)` sẽ dùng nó.

### Cách đúng thứ hai: method tên messageSource

~~~java
@Configuration
public class I18nConfig {

    @Bean
    public ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source =
                new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }
}
~~~

Đây là snippet; cần imports `@Configuration`, `@Bean` và `ResourceBundleMessageSource`. Khi `@Bean` không ghi name riêng, tên mặc định là **Java factory method name** `messageSource`. Vì vậy cách này cũng đúng.

### Cách dễ nhầm: chỉ đúng kiểu, sai tên

~~~java
@Bean
public MessageSource translations() {
    StaticMessageSource source = new StaticMessageSource();
    source.addMessage("hello", Locale.ENGLISH, "Hello!");
    return source;
}
~~~

Snippet ở trong configuration class, nhưng Java method name là `translations` nên bean name mặc định cũng là `translations`. Nó là một `MessageSource` hợp lệ, có thể lấy bằng `getBean("translations", MessageSource.class)`, nhưng **không được tự động chọn làm context message source**.

Spring **không tìm theo kiểu duy nhất** để thay thế hợp đồng tên `messageSource`. Kể cả khi chỉ có đúng một bean `MessageSource` dưới tên khác, convention vẫn không được đáp ứng.

### Phân biệt ba tên khác nhau

| Khái niệm | Ví dụ | Có quyết định cơ chế tự nhận diện? |
| --- | --- | --- |
| Java class name | `ResourceBundleMessageSource` | Không, trừ việc object phải hợp contract `MessageSource` |
| Java `@Bean` method name | `translations()` | Chỉ khi không đặt `@Bean(name=...)` |
| Spring bean name | `messageSource` | **Có, là tên đặc biệt Spring tra cứu** |

**Không thay `messageSource` bằng `messages`, `translationSource`, `messageSources` hoặc `i18nMessageSource`** rồi kỳ vọng `ApplicationContext.getMessage(...)` tự nhận.

## 4. Cách khai báo đúng theo từng kiểu cấu hình

### 4.1. Java @Bean tường minh

~~~java
@Configuration(proxyBeanMethods = false)
public class LocalizationConfig {

    @Bean(name = "messageSource")
    public MessageSource createMessages() {
        ResourceBundleMessageSource source =
                new ResourceBundleMessageSource();

        source.setBasenames("i18n/messages", "i18n/errors");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }
}
~~~

Snippet này yêu cầu các resource bundles `i18n/messages*.properties`, `i18n/errors*.properties` ở runtime classpath.

Tại đây:

- Bean name đúng là `messageSource`.
- Method `createMessages` khác tên bean nhưng không sao.
- `setBasenames(...)` nhận **basename** không có extension `.properties`.
- `setFallbackToSystemLocale(false)` làm locale lookup bớt bị ảnh hưởng bởi locale hệ điều hành, hữu ích để cấu hình hành vi fallback có chủ đích.

### 4.2. @Component với tên bean rõ

~~~java
@Component("messageSource")
public class InMemoryMessages extends StaticMessageSource {
    public InMemoryMessages() {
        addMessage("app.title", Locale.ENGLISH, "Demo application");
    }
}
~~~

Đây là snippet class; cần imports `Component`, `StaticMessageSource` và `Locale`. **Component scanning phải bao gồm package của class**. Chỉ đặt `@Component("messageSource")` trên một class ngoài scan scope không làm bean tự xuất hiện.

Cách này hữu ích khi implementation là một reusable component. Với các source dựa trên file và có setters, `@Bean` thường dễ cấu hình hơn.

### 4.3. XML bean id

~~~xml
<bean id="messageSource"
      class="org.springframework.context.support.ResourceBundleMessageSource">
    <property name="basename" value="i18n/messages"/>
    <property name="defaultEncoding" value="UTF-8"/>
</bean>
~~~

Đây là **XML snippet** trong tài liệu `beans.xml` có root namespace/schema thích hợp. Điểm bắt buộc là `id="messageSource"` và class thực hiện `MessageSource`. Tên file XML không quyết định việc nhận diện.

### 4.4. Bean được khai báo ở một configuration class có @Profile

~~~java
@Configuration
@Profile("prod")
public class ProductionMessagesConfig {

    @Bean("messageSource")
    public MessageSource source() {
        return new ResourceBundleMessageSource();
    }
}
~~~

Đây chỉ là snippet về **kết hợp profile**, chưa đủ cấu hình basename cho ứng dụng thực tế. Khi `prod` không active, configuration bị loại, nên **không có bean `messageSource` từ class này**; context có thể dùng parent/fallback source.

Cấu hình production cần set basename/encoding phù hợp, và integration test phải xác nhận bean được đăng ký ở profile dự kiến. Không coi `@Profile` hay `@Conditional` không khớp như một lỗi “bean sai tên”.

## 5. Quy trình Spring tự chọn MessageSource khi refresh

Theo Spring Framework Reference, khi `ApplicationContext` được khởi tạo, container:

1. Tìm một bean **tên `messageSource`** trong context hiện tại.
2. Nếu tìm thấy và kiểu hợp lệ, dùng bean đó làm delegate cho lời gọi `context.getMessage(...)`.
3. Nếu không có, thử tìm message source ở parent context (nếu có).
4. Nếu không có nguồn phù hợp, tạo một `DelegatingMessageSource` rỗng để context vẫn hỗ trợ `getMessage(...)`.

~~~text
BeanFactory hiện tại
    |
    +-- messageSource : StaticMessageSource? → dùng ngay
    |
    +-- messageSource : ResourceBundleMessageSource? → dùng ngay
    |
    +-- messageSource : [không có]
             |
             +-- parent context có MessageSource? → dùng parent
             |
             +-- không có parent → DelegatingMessageSource rỗng
~~~

**Quan trọng:** nguồn “rỗng” không có nghĩa Spring bỏ luôn interface `MessageSource`. `ApplicationContext` vẫn triển khai `getMessage`; chỉ là không thể tìm được code tùy biến trong message catalog khi không có nguồn.

### Nhận diện theo tên, không theo @Primary

`@Primary` tham gia chọn candidate trong **type-based dependency injection**. Nó không thay thế yêu cầu Spring tìm bean tên `messageSource`. Một bean khác tên được đánh dấu `@Primary` vẫn không tự thành internal message source của context.

Tương tự, `@Qualifier("translations")` cho phép một consumer chọn bean khác tên khi inject, nhưng **không đổi cách `ApplicationContext` chọn nguồn thông điệp của chính nó**.

### Sai tên khác sai kiểu

Nếu một bean **tên đúng `messageSource` nhưng object không thực hiện `MessageSource`**, quá trình khởi tạo message service có thể thất bại do sai kiểu bean. Đây không phải trường hợp fallback như “không tìm được bean”. Kiểm tra cả **name** lẫn **type**.

### Tên này thuộc hợp đồng cấu hình, không chỉ style

Các bean names thông thường theo camelCase có thể do team tự đặt. Nhưng `messageSource` là **convention đặc biệt được framework nhận diện**, tương tự những bean names chuyên biệt khác của ApplicationContext. Đổi tên mà không có chiến lược tương đương sẽ thay behavior runtime.

## 6. ResourceBundleMessageSource, ReloadableResourceBundleMessageSource, StaticMessageSource

Một bean đúng tên `messageSource` có thể dùng nhiều implementation:

| Lớp | Nguồn | Basename điển hình |
| --- | --- | --- |
| `ResourceBundleMessageSource` | Classpath/JDK `ResourceBundle` | `i18n/messages` |
| `ReloadableResourceBundleMessageSource` | Spring Resource, phù hợp với external files khi cần refresh | `classpath:i18n/messages` hoặc `file:/opt/app/i18n/messages` |
| `StaticMessageSource` | In-memory patterns thêm qua `addMessage` | Không cần basename |

```java
@Bean("messageSource")
MessageSource messageSource() {
    StaticMessageSource source = new StaticMessageSource();
    source.addMessage("hello", Locale.ENGLISH, "Hello, {0}!");
    return source;
}
```

Ví dụ này dùng implementation tối giản để tập trung vào **quy ước tên bean**, không phụ thuộc resources. File tên `messages_vi.properties`, bean name `messageSource` và basename `i18n/messages` là ba thứ khác nhau. Bài 259 giải thích khác biệt cache/reload, và bài 263 kiểm tra charset thực tế.

## 7. Locale và tên file message bundles

`Locale` biểu diễn ngôn ngữ, quốc gia/vùng và biến thể khi cần.

Ví dụ:

~~~java
Locale english = Locale.ENGLISH;
Locale vietnamese = Locale.forLanguageTag("vi");
Locale us = Locale.US;
Locale uk = Locale.UK;
~~~

Một `ResourceBundleMessageSource` có basename `i18n/messages` sẽ tìm các file có hậu tố locale tương ứng, ví dụ `messages_en_US.properties` rồi fallback theo quy tắc tài nguyên JDK/Spring. `setFallbackToSystemLocale(false)` giúp tránh lấy locale mặc định JVM như fallback thứ cấp không mong muốn trong nhiều tình huống.

### Ví dụ content

`src/main/resources/i18n/messages.properties`:

~~~properties
app.title=Demo application
greeting=Hello, {0}!
~~~

`src/main/resources/i18n/messages_vi.properties`:

~~~properties
app.title=Ứng dụng minh họa
greeting=Xin chào, {0}!
~~~

`src/main/resources/i18n/messages_en.properties`:

~~~properties
app.title=Example application
greeting=Hello, {0}!
~~~

Khi `Locale.forLanguageTag("vi")` được dùng, Spring/JDK có thể chọn `messages_vi.properties` và thay tham số `{0}`.

### MessageFormat và dấu nháy đơn

`MessageSource` sử dụng cơ chế `MessageFormat` cho những messages có tham số. Trong `MessageFormat`, dấu nháy đơn `'` có tác dụng escaping/quoting đặc biệt. Nếu thông điệp cần chứa literal apostrophe, phải viết pattern theo quy tắc `MessageFormat` (thường dùng hai dấu nháy đơn liên tiếp).

Ví dụ:

~~~properties
item.required=The {0} argument is required.
~~~

Đối với nội dung có format placeholders, kiểm thử với đủ arguments và Locale; đừng nối chuỗi bằng tay khi có plural/date/number formatting phức tạp.

### Không nhầm Locale với profile

`@Profile("prod")` quyết định **bean definitions nào được đăng ký**. `Locale.forLanguageTag("vi")` quyết định **message variant nào được tra**. Tên profile `prod` không tự biến locale thành tiếng Anh, và chọn locale `vi` không tự kích hoạt một `@Profile("vi")`.

## 8. Bean name và phương thức getMessage trong ApplicationContext

`ApplicationContext` đồng thời là một `MessageSource`:

~~~java
try (AnnotationConfigApplicationContext context =
             new AnnotationConfigApplicationContext(I18nConfig.class)) {

    String value = context.getMessage(
            "greeting",
            new Object[]{"Linh"},
            Locale.ENGLISH);

    System.out.println(value);
}
~~~

Snippet yêu cầu `I18nConfig` hợp lệ. Ở đây không cần tự lấy `context.getBean("messageSource")` để tra từng code: `context.getMessage(...)` trực tiếp đi qua nguồn đã được Spring tích hợp.

### Direct bean lookup vẫn có thể hoạt động khi sai tên

Giả sử một bean tên `translations` vẫn là `StaticMessageSource`:

~~~java
MessageSource direct =
        context.getBean("translations", MessageSource.class);

String value = direct.getMessage(
        "greeting",
        new Object[]{"Linh"},
        Locale.ENGLISH);
~~~

Code này **có thể hoạt động** nếu bean `translations` chứa đúng message. Nhưng `context.getMessage("greeting",...)` không tự trỏ tới direct bean kia khi không có bean chuẩn tên `messageSource`.

Sự khác biệt này là nguyên nhân của một bug khó phát hiện: developer thử `getBean("translations")` thấy dịch đúng, nhưng phần validation/UI framework dùng context message source lại không tìm được key.

### Kiểm tra bean definition khác containsBean

Khi không có bean definition do ứng dụng khai báo tên `messageSource`, `AbstractApplicationContext` có thể đăng ký nội bộ một singleton `DelegatingMessageSource` để hỗ trợ API. Vì vậy, **không chỉ dựa vào `containsBean("messageSource")`** để chứng minh bạn đã cấu hình message bundle.

Với `AnnotationConfigApplicationContext`, có thể dùng:

~~~java
boolean declared =
        context.containsBeanDefinition("messageSource");
~~~

để phân biệt **definition được đăng ký từ cấu hình** với fallback singleton được framework tạo nội bộ. Đối với chương trình có parent/alias/registration đặc biệt, cần kiểm tra tầng registry phù hợp; đây không phải một phép đánh giá chất lượng catalog đầy đủ.

## 9. Khi không khai báo messageSource: fallback và lỗi

Một ApplicationContext không có source tùy biến **vẫn cung cấp `getMessage(...)`** thông qua `DelegatingMessageSource` rỗng (hoặc nguồn từ parent nếu có).

~~~java
try (AnnotationConfigApplicationContext context =
             new AnnotationConfigApplicationContext()) {

    context.refresh();

    String defaulted = context.getMessage(
            "unknown.code",
            null,
            "fallback",
            Locale.ENGLISH);

    System.out.println(defaulted);
}
~~~

Snippet này có thể in `fallback` khi không tìm được code. Nó không phải bằng chứng file messages đã load.

Với overload **không default**:

~~~java
context.getMessage("unknown.code", null, Locale.ENGLISH);
~~~

nó ném `NoSuchMessageException` khi không có nguồn message nào giải quyết code.

### Parent context

Spring hỗ trợ `HierarchicalMessageSource` để cấp fallback từ parent. Nếu child context không khai báo `messageSource`, nó có thể kế thừa nguồn từ parent. Nếu child có `messageSource` riêng và implementation hỗ trợ `HierarchicalMessageSource` (các implementations chuẩn có hỗ trợ), những codes không tìm thấy ở child có thể được thử ở parent.

~~~text
Parent ApplicationContext
  messageSource
  parent.only = "from-parent"
  shared      = "parent-shared"
               |
               v
Child ApplicationContext
  messageSource
  shared      = "child-shared"

child.getMessage("shared")      -> child-shared
child.getMessage("parent.only") -> from-parent
~~~

Tuy nhiên **đừng dùng parent context như một cách che lỗi thiếu bundles**: nếu deployment kỳ vọng child có catalog riêng, phải test rằng các codes bắt buộc ở đúng tầng.

## 10. MessageSourceAware và dependency injection

Một Spring-managed bean có thể dùng `MessageSourceAware`:

~~~java
public class NoticeService implements MessageSourceAware {

    private MessageSource messages;

    @Override
    public void setMessageSource(MessageSource messageSource) {
        this.messages = messageSource;
    }

    public String hello(Locale locale) {
        return messages.getMessage(
                "greeting", new Object[]{"Linh"}, locale);
    }
}
~~~

Đây là class snippet; cần imports `MessageSourceAware`, `MessageSource`, `Locale` và đăng ký bean trong ApplicationContext. Callback giúp bean nhận message source mà context cung cấp, tránh hardcode global static reference.

Với constructor injection, cũng có thể nhận `MessageSource` từ context theo khả năng resolvable dependencies. Trong những tình huống có **nhiều beans MessageSource theo type**, nên nêu rõ ý định hoặc inject một abstraction chuyên dụng để tránh ambiguity. Đừng nhầm việc type-based injection chọn một bean với **quy tắc lựa chọn nội bộ `messageSource` theo tên**.

### Đừng lạm dụng ApplicationContextAware

Khi service chỉ cần dịch thông điệp, nhận `MessageSource` sẽ hẹp và dễ test hơn nhận cả `ApplicationContext` để gọi `getMessage`. `MessageSourceAware` là một callback phù hợp cho infrastructure hoặc legacy components, nhưng constructor injection thường đơn giản hơn với POJO mới.

### Ngôn ngữ đến từ đâu?

Web application có thể xác định locale từ HTTP request/user preference/locale resolver, còn CLI job có thể truyền `Locale` trực tiếp. `MessageSource` không tự biết một user cụ thể muốn đọc tiếng nào nếu caller không cung cấp locale hoặc context xác định locale cho luồng xử lý tương ứng.

## 11. Ví dụ hoàn chỉnh Java 17 + Spring Framework 6.2.9

Ví dụ này **không cần messages.properties** để kết quả hoàn toàn xác định. Nó dùng `StaticMessageSource`, mỗi case tạo context riêng:

1. **Đúng tên `messageSource`**: `context.getMessage(...)` thấy hai locale.
2. **Sai tên `translations`**: gọi bean trực tiếp được, nhưng context lấy fallback.
3. **Không khai báo messageSource**: context có empty source, default works, strict lookup ném `NoSuchMessageException`.
4. **Parent-child contexts**: child không có source dùng parent; child có source riêng override code hiện có và fallback cho code chỉ parent biết.

### 11.1. Cấu trúc thư mục

~~~text
message-source-name-demo/
├── pom.xml
└── src/main/java/demo/
    └── MessageSourceNameDemo.java
~~~

### 11.2. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>message-source-name-demo</artifactId>
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

### 11.3. src/main/java/demo/MessageSourceNameDemo.java

~~~java
package demo;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.StaticMessageSource;

public class MessageSourceNameDemo {

    private static final Locale VI = Locale.forLanguageTag("vi");

    public static void main(String[] args) {
        // 1. Đúng bean name: ApplicationContext nhận diện.
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(NamedConfig.class)) {

            System.out.println("named.en=" + context.getMessage(
                    "greeting", new Object[]{"Linh"}, Locale.ENGLISH));

            System.out.println("named.vi=" + context.getMessage(
                    "greeting", new Object[]{"Linh"}, VI));

            System.out.println("named.definition="
                    + context.containsBeanDefinition("messageSource"));
        }

        // 2. Sai bean name: bean dùng trực tiếp được,
        //    nhưng không trở thành context message source.
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(WrongNameConfig.class)) {

            MessageSource direct =
                    context.getBean("translations", MessageSource.class);

            System.out.println("renamed.direct=" + direct.getMessage(
                    "greeting", new Object[]{"Linh"}, Locale.ENGLISH));

            System.out.println("renamed.context=" + context.getMessage(
                    "greeting", null, "default-used", Locale.ENGLISH));

            System.out.println("renamed.definition="
                    + context.containsBeanDefinition("messageSource"));
        }

        // 3. Không có custom messageSource.
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            context.refresh();

            System.out.println("missing.fallback=" + context.getMessage(
                    "greeting", null, "default-used", Locale.ENGLISH));

            try {
                context.getMessage("greeting", null, Locale.ENGLISH);
            } catch (NoSuchMessageException ex) {
                System.out.println("missing.strict="
                        + ex.getClass().getSimpleName());
            }
        }

        // 4. Parent có MessageSource; child có thể kế thừa
        //    hoặc dùng own source rồi fallback về parent.
        try (AnnotationConfigApplicationContext parent =
                     new AnnotationConfigApplicationContext(ParentConfig.class)) {

            try (AnnotationConfigApplicationContext child =
                         new AnnotationConfigApplicationContext()) {

                child.setParent(parent);
                child.refresh();

                System.out.println("parent.inherited=" + child.getMessage(
                        "parent.only", null, Locale.ENGLISH));
            }

            try (AnnotationConfigApplicationContext child =
                         new AnnotationConfigApplicationContext()) {

                child.setParent(parent);
                child.register(ChildConfig.class);
                child.refresh();

                System.out.println("child.override=" + child.getMessage(
                        "shared", null, Locale.ENGLISH));

                System.out.println("child.fallback=" + child.getMessage(
                        "parent.only", null, Locale.ENGLISH));
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class NamedConfig {

        @Bean("messageSource")
        public StaticMessageSource createCatalog() {
            StaticMessageSource source = new StaticMessageSource();

            source.addMessage(
                    "greeting", Locale.ENGLISH, "Hello, {0}!");

            source.addMessage(
                    "greeting", VI, "Xin chào, {0}!");

            return source;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class WrongNameConfig {

        @Bean("translations")
        public StaticMessageSource otherCatalog() {
            StaticMessageSource source = new StaticMessageSource();

            source.addMessage(
                    "greeting", Locale.ENGLISH, "From custom bean, {0}!");

            return source;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ParentConfig {

        @Bean("messageSource")
        public StaticMessageSource parentMessages() {
            StaticMessageSource source = new StaticMessageSource();

            source.addMessage(
                    "shared", Locale.ENGLISH, "from-parent-shared");

            source.addMessage(
                    "parent.only", Locale.ENGLISH, "from-parent");

            return source;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ChildConfig {

        @Bean("messageSource")
        public StaticMessageSource childMessages() {
            StaticMessageSource source = new StaticMessageSource();

            source.addMessage(
                    "shared", Locale.ENGLISH, "from-child");

            return source;
        }
    }
}
~~~

### 11.4. Lệnh chạy

Từ thư mục `message-source-name-demo` với JDK 17+ và Maven:

~~~bash
java -version
mvn -version
mvn -q compile exec:java -Dexec.mainClass=demo.MessageSourceNameDemo
~~~

Với source code không đổi, **stdout do chương trình in**, không tính Maven hoặc logging của Spring, là:

~~~text
named.en=Hello, Linh!
named.vi=Xin chào, Linh!
named.definition=true
renamed.direct=From custom bean, Linh!
renamed.context=default-used
renamed.definition=false
missing.fallback=default-used
missing.strict=NoSuchMessageException
parent.inherited=from-parent
child.override=from-child
child.fallback=from-parent
~~~

### 11.5. Giải thích từng nhóm output

| Dòng/nhóm | Điều chứng minh |
| --- | --- |
| `named.en`, `named.vi` | `@Bean("messageSource")` được context dùng, locale chọn message tương ứng |
| `named.definition=true` | Có bean definition tên `messageSource` do `NamedConfig` đăng ký |
| `renamed.direct` | Bean `translations` vẫn là `MessageSource` có thể gọi trực tiếp |
| `renamed.context=default-used` | ApplicationContext **không tự nhận bean khác tên** và dùng fallback |
| `renamed.definition=false` | Không có bean definition ứng dụng tên `messageSource` trong context đó |
| `missing.fallback` | Empty/Delegating message source vẫn hỗ trợ overload có default |
| `missing.strict` | Không default và không có code → `NoSuchMessageException` |
| `parent.inherited` | Child không có custom source nên có thể tra parent |
| `child.override` | Message code cùng tên được ưu tiên ở child source |
| `child.fallback` | Code chỉ có ở parent vẫn được tra thông qua hierarchy |

**Điểm cần chú ý:** Case wrong-name cho thấy cùng một context có thể chứa một bean `MessageSource` đúng kiểu nhưng kết quả `context.getMessage` không sử dụng catalog đó. Đừng sửa bằng cách thay `getMessage` calls ở mọi service thành `getBean("translations")`; tốt hơn là sửa bean name về `messageSource` nếu đây là nguồn i18n dùng chung.

### 11.6. Thử sửa và dự đoán

**Thử A — đổi đúng thành sai.** Trong `NamedConfig`, đổi `@Bean("messageSource")` thành `@Bean("catalog")`. `named.en` dùng overload strict sẽ ném `NoSuchMessageException` nếu không có parent source; chứng minh cơ chế dựa vào tên.

**Thử B — giữ method name, đổi bean annotation.** Đặt Java method tên `messageSource()` nhưng `@Bean("catalog")`. Tên thực của bean vẫn là `catalog`; annotation explicit thắng tên method.

**Thử C — bỏ name explicit và đổi method name.** Nếu dùng `@Bean` không có name và tên method là `messageSource`, context nhận diện vì bean name mặc định là method name.

**Thử D — thêm greeting vào missing context.** Đăng ký một `@Configuration` có `@Bean("messageSource")` trước `refresh()`. Lúc đó `getMessage("greeting",...)` có thể lấy message nếu source thực sự chứa code.

**Thử E — đổi locale.** Khi request là `Locale.FRENCH` mà StaticMessageSource không có French message, overload strict có thể ném `NoSuchMessageException`; overload có default sẽ trả fallback.

**Thử F — xóa message `parent.only` khỏi ParentConfig.** Child fallback tới parent vẫn không thể tìm được code, nên strict call `child.getMessage("parent.only",...)` sẽ ném `NoSuchMessageException`.

**Thử G — thêm child catalog cùng code.** Nếu child có code `parent.only`, child sẽ thắng parent vì local source được tra trước.

## 12. Spring Boot có thay quy tắc này không?

Spring Boot có thể tự cấu hình `MessageSource` khi tìm thấy những bundle phù hợp và không có cấu hình tùy biến ngăn nó thực hiện. Boot hỗ trợ các properties như:

~~~properties
spring.messages.basename=messages
spring.messages.encoding=UTF-8
~~~

hoặc đặt bundles:

~~~text
src/main/resources/
├── messages.properties
├── messages_en.properties
└── messages_vi.properties
~~~

Tuy nhiên:

- **Spring Core**: bạn phải đăng ký bean tên `messageSource` hoặc để ApplicationContext dùng parent/default delegate.
- **Spring Boot**: có auto-configuration tạo bean phù hợp khi các điều kiện hiện diện thỏa mãn; không có nghĩa **Spring Core** tự scan mọi `messages.properties`.
- Nếu tự định nghĩa bean `messageSource` trong Boot, các điều kiện auto-configuration có thể back off; cần xem behavior đúng phiên bản Boot đang dùng.
- Các thiết lập `spring.messages.*` thuộc cấu hình **Spring Boot**, không phải setters tự động áp dụng lên mọi `ResourceBundleMessageSource` được tự tạo bằng Java Core.
- Nếu có message bean tên khác, behavior không nhất thiết giống bean tên chuẩn; kiểm tra actual context definitions và Boot auto-configuration report khi debug.

Một source file `messages.properties` nằm trong JAR **không tự chứng minh context đã có bean messageSource** trong mọi dạng ứng dụng.

## 13. Những lỗi dễ gặp khi dùng tên messageSource

| Dấu hiệu | Nguyên nhân thường gặp | Hướng sửa |
| --- | --- | --- |
| `context.getMessage` luôn trả default | Bean MessageSource đặt tên `translations`/`messages` | Đổi bean name thành `messageSource` |
| Inject `MessageSource` trực tiếp hoạt động nhưng context không dịch | Type injection chọn một bean khác với internal named lookup | Kiểm tra tên bean và context delegate |
| `@Bean public MessageSource translations()` không được nhận | Bean name mặc định là `translations` | Dùng `@Bean("messageSource")` |
| Class `@Component` đúng tên nhưng context không thấy | Class không thuộc component scan | Bổ sung scan/import/register |
| Bean tên `messageSource` nhưng startup lỗi | Bean không implement MessageSource hoặc tạo bean lỗi | Kiểm tra type/constructor/dependencies |
| `messageSource` có mà không tìm được code | Sai basename/key/locale hoặc file không có trên classpath | Kiểm tra bundle files và lookup |
| `messages_vi.properties` bị bỏ qua | Sai path, basename hoặc locale tag | Kiểm tra `i18n/messages` và `Locale.forLanguageTag("vi")` |
| Tiếng Việt bị lỗi ký tự | Charset/bundle encoding không đúng | UTF-8, cấu hình encoding, kiểm tra file packaging |
| Source của child không thấy message parent | Parent không có source/code, hoặc custom source không hỗ trợ hierarchy | Kiểm tra `HierarchicalMessageSource` và parents |
| Child dùng parent dù tưởng có catalog riêng | Conditional configuration bị loại, bean tên sai hoặc không đăng ký | Kiểm tra bean definitions ở child |
| Không có source nhưng `containsBean("messageSource")` true | Internal DelegatingMessageSource singleton | Kiểm tra definition và actual type, không chỉ presence |
| Có nhiều catalog cùng type | Type-based injection ambiguous | Dùng qualifier/thiết kế rõ; context internal lookup vẫn theo tên |
| Dùng `@Primary` nhưng context không chọn | @Primary không thay internal named lookup | Đặt đúng `messageSource` |
| `NoSuchMessageException` | Không tìm được code và overload không có default | Bổ sung code hoặc dùng fallback có chủ đích |
| Reload không thấy file thay đổi | Bundle nằm trong JAR hoặc cache config không phù hợp | Dùng resource ngoài JAR và cấu hình reload hợp lý |
| Boot chạy khác Core test | Boot auto-configuration tạo/không tạo bean | Kiểm tra Boot properties, conditions và actual definitions |
| Tạo hai bean cùng tên `messageSource` | Bean name conflict/overriding phụ thuộc cấu hình | Chỉ chọn một nguồn canonical hoặc cấu trúc hierarchy rõ |

### Điều tra theo thứ tự

1. Lấy **tên bean thực tế**, không chỉ Java class/method.
2. Có definition tên `messageSource` trong **đúng ApplicationContext** không?
3. Bean đúng kiểu `MessageSource` không?
4. Nếu không, có parent context message source không?
5. Nếu không, có phải đang dùng `DelegatingMessageSource` rỗng?
6. Nếu có đúng source, basename/path, file name, key và locale đã đúng chưa?
7. Đang dùng overload có default hay overload strict?
8. Các arguments có `MessageFormat` syntax đúng không?
9. Nếu dùng Spring Boot, auto-configuration và `spring.messages.*` có được áp dụng đúng không?
10. Có test context khác production khiến component scanning hoặc parent hierarchy khác không?

## 14. Kiểm thử chuẩn đặt tên và fallback

### 14.1. Assert bean definition tên messageSource

~~~java
try (AnnotationConfigApplicationContext context =
             new AnnotationConfigApplicationContext(NamedConfig.class)) {

    if (!context.containsBeanDefinition("messageSource")) {
        throw new AssertionError("messageSource bean definition missing");
    }

    String message = context.getMessage(
            "greeting", new Object[]{"Linh"}, Locale.ENGLISH);

    if (!"Hello, Linh!".equals(message)) {
        throw new AssertionError("Wrong messageSource wiring");
    }
}
~~~

Đây là **test snippet**, với `NamedConfig` tham chiếu tới nested class `MessageSourceNameDemo.NamedConfig` hoặc import đúng type. Nó vừa kiểm tra **name registration** vừa kiểm tra **message resolution**, tốt hơn chỉ nhìn tên bean.

### 14.2. Assert wrong-name behavior

~~~java
try (AnnotationConfigApplicationContext context =
             new AnnotationConfigApplicationContext(WrongNameConfig.class)) {

    if (context.containsBeanDefinition("messageSource")) {
        throw new AssertionError("Unexpected messageSource definition");
    }

    String defaulted = context.getMessage(
            "greeting", null, "fallback", Locale.ENGLISH);

    if (!"fallback".equals(defaulted)) {
        throw new AssertionError("Wrong-name bean should not be delegated");
    }
}
~~~

Đây là test snippet trong môi trường không có parent, cùng types của demo. Nó ngăn regression khi một developer vô tình đổi bean name.

### 14.3. Assert không có source vẫn xử lý default

Với context không đăng ký custom source, `getMessage(code, args, default, locale)` trả default khi code thiếu; còn overload không default ném `NoSuchMessageException`. Kiểm thử cả hai để người đọc không nhầm “không tìm thấy message” với “context không khởi tạo được”.

### 14.4. Test parent-child priority

Tạo parent và child với catalogs khác nhau:

- Code `shared` ở cả hai → child thắng.
- Code `parent.only` chỉ ở parent → child fallback về parent.
- Code `absent` không có ở đâu → strict lookup ném exception.
- Child không có `messageSource` → context lấy parent source.

Dùng contexts độc lập theo từng case, đóng chúng bằng `try-with-resources` và không phụ thuộc global Locale state.

### 14.5. Test real properties bundles

Tạo files `messages.properties`, `messages_en.properties`, `messages_vi.properties` trong `src/test/resources/i18n/` rồi cấu hình `ResourceBundleMessageSource` basename `i18n/messages`. Assert ít nhất:

1. English message và Vietnamese message đều được load.
2. Một code có argument `{0}` format đúng.
3. Unknown code dùng fallback hoặc ném `NoSuchMessageException` theo overload.
4. File thực sự có mặt trong packaged artifact nếu ứng dụng sẽ chạy dưới JAR.
5. Locale fallback không vô tình phụ thuộc locale của máy CI; dùng `setFallbackToSystemLocale(false)` nếu đúng policy.

Chỉ test `StaticMessageSource` chưa chứng minh file path/encoding thực tế đúng trong ứng dụng sản xuất.

## 15. Bảo mật và thiết kế thông điệp

Thông điệp thường không chứa secrets, nhưng arguments truyền vào có thể là dữ liệu người dùng hoặc dữ liệu nhạy cảm. Lưu ý:

- Không format password, access token, personal identifier không cần thiết vào error message/log.
- Không để message code đầu vào không tin cậy truy cập các catalog ngoài ý muốn mà thiếu kiểm soát.
- Dùng stable keys như `error.user.notFound` thay vì hardcode text ở mọi service.
- Đối với validation, map các codes/arguments cụ thể; đừng coi raw exception message là nội dung đa ngôn ngữ.
- Tránh để một ngôn ngữ thiếu code trong production; có fallback policy và kiểm tra catalog completeness.
- Nếu chuỗi là HTML hoặc JSON, xử lý escaping tại **output layer**. `MessageSource` không phải HTML sanitizer hay JSON encoder.
- Tách message lookup và locale resolution: locale từ request/user preference phải được xác thực theo danh sách locale hỗ trợ.
- Với nhiều tenant/brand, cân nhắc basename hoặc message source strategy được thiết kế rõ; không bắt buộc mỗi tenant tạo một bean tên `messageSource` cùng context.

## 16. Checklist triển khai

- [ ] Có **đúng một nguồn i18n canonical** cho mỗi context nơi cần.
- [ ] Bean name canonical viết chính xác **`messageSource`** (đúng chữ hoa/thường).
- [ ] Bean thực hiện `MessageSource`.
- [ ] `@Bean("messageSource")` hoặc Java factory method `messageSource()` khi không ghi name.
- [ ] Nếu dùng `@Component("messageSource")`, class thật sự được scan/register.
- [ ] Nếu dùng XML, `id="messageSource"`.
- [ ] Basename không có `.properties` và trỏ tới resource đúng classpath.
- [ ] Các file `messages_en.properties`, `messages_vi.properties` được đóng gói đúng.
- [ ] Locale selection và fallback policy có test.
- [ ] Messages có arguments tuân theo `MessageFormat`.
- [ ] Unknown keys có cách xử lý rõ: default hoặc strict exception.
- [ ] Không nhầm một bean MessageSource **khác tên** với internal source của context.
- [ ] Không dựa vào `containsBean` đơn thuần khi Spring có internal fallback singleton.
- [ ] Test parent/child contexts nếu có kiến trúc phân tầng.
- [ ] Với Spring Boot, kiểm tra auto-configuration và `spring.messages.*`.
- [ ] Không giả định hot reload file bên trong JAR.
- [ ] Đóng contexts trong demo/tests để tránh rò lifecycle/resources.

## 17. Câu hỏi tự kiểm tra

**1. Spring chọn MessageSource bean dựa trên Java type hay bean name?**  
Container ưu tiên nhận diện theo **bean name chính xác `messageSource`** khi khởi tạo message service.

**2. `@Bean public MessageSource translations()` có tự được nhận diện không?**  
Không, vì bean name mặc định là `translations`.

**3. `@Bean("messageSource") public MessageSource translations()` có được không?**  
Có. Explicit bean name quyết định.

**4. Nếu không có messageSource trong child nhưng parent có, điều gì xảy ra?**  
Child có thể delegate/fallback sang message source của parent.

**5. Nếu cả child và parent có cùng message code thì chọn nguồn nào?**  
Thông thường child source được tra trước; parent dùng cho code không có ở child theo hierarchy semantics.

**6. Nếu không có nguồn thông điệp nào, ApplicationContext.getMessage còn tồn tại không?**  
Có, vì context vẫn thực hiện `MessageSource`; framework dùng `DelegatingMessageSource` rỗng.

**7. Không tìm được code với overload có default thì sao?**  
Trả `defaultMessage` theo contract.

**8. Không tìm được code với overload không default thì sao?**  
Ném `NoSuchMessageException`.

**9. Có phải bean name `messageSource` quyết định file phải tên messageSource.properties?**  
Không. Bundle basename độc lập, có thể là `i18n/messages`.

**10. @Primary có thay cho @Bean("messageSource") không?**  
Không. `@Primary` giải quyết type-based injection, không đổi internal convention.

**11. ResourceBundleMessageSource khác ReloadableResourceBundleMessageSource ở đâu?**  
Một bên dựa trên JDK bundles từ classpath, bên kia dùng Spring resource locations và có cơ chế refresh/caching thích hợp với files cập nhật được.

**12. StaticMessageSource có nên dùng cho test không?**  
Có, đặc biệt để tránh phụ thuộc file/locale và chứng minh logic wiring.

**13. Trong Spring Core thuần, đặt messages.properties vào classpath đã đủ để context tự tạo messageSource chưa?**  
Không. Phải đăng ký nguồn hoặc dùng framework tích hợp như Spring Boot auto-configuration.

**14. Vì sao context trả fallback dù có một bean MessageSource type duy nhất?**  
Có thể bean đó đặt **sai tên** nên context không dùng làm message source nội bộ.

**15. MessageSourceAware giúp gì?**  
Spring cung cấp một `MessageSource` cho bean qua callback `setMessageSource`.

## Tài liệu chính thức

- [Spring Framework Reference — Additional Capabilities of the ApplicationContext / Internationalization using MessageSource](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html): quy ước tên `messageSource`, parent fallback, `DelegatingMessageSource`, API và examples.
- [Spring Framework 6.2 Javadoc — MessageSource](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/MessageSource.html).
- [Spring Framework 6.2 Javadoc — AbstractApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/support/AbstractApplicationContext.html): lifecycle khởi tạo MessageSource.
- [Spring Framework 6.2 Javadoc — ResourceBundleMessageSource](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/support/ResourceBundleMessageSource.html).
- [Spring Framework 6.2 Javadoc — ReloadableResourceBundleMessageSource](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/support/ReloadableResourceBundleMessageSource.html).
- [Spring Framework 6.2 Javadoc — StaticMessageSource](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/support/StaticMessageSource.html).
- [Spring Framework 6.2 Javadoc — MessageSourceAware](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/MessageSourceAware.html).
- [Spring Framework 6.2 Javadoc — HierarchicalMessageSource](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/HierarchicalMessageSource.html).
- [Spring Framework Reference — Bean Overview and Bean Naming Conventions](https://docs.spring.io/spring-framework/reference/core/beans/definition.html).
- [Spring Boot Reference — Internationalization](https://docs.spring.io/spring-boot/reference/features/internationalization.html): Boot-specific message source auto-configuration, basenames và properties.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="internationalization-003">ResourceBundleMessageSource và ReloadableResourceBundleMessageSource</a>

<details>
<summary>Xem chi tiết</summary>

# ResourceBundleMessageSource và ReloadableResourceBundleMessageSource trong Spring Framework

## 1. Tại sao cần MessageSource?

Trong ứng dụng hỗ trợ nhiều ngôn ngữ, cùng một hành động có thể cần các thông điệp khác nhau theo ngôn ngữ của người dùng: "Đăng nhập" ở tiếng Việt, "Sign in" ở tiếng Anh, lỗi biểu mẫu hoặc thông báo xác nhận có định dạng tùy `Locale`. Không nên hard-code tất cả bản dịch trong Java `if/else`.

Spring Framework Core Container dùng abstraction `org.springframework.context.MessageSource` để tách **mã thông điệp** (`message code`), **các tham số** và **ngôn ngữ mong muốn** ra khỏi nghiệp vụ. Hai implementation được sử dụng phổ biến là:

- **`ResourceBundleMessageSource`:** tích hợp cơ chế `java.util.ResourceBundle` của JDK, đọc resource bundles từ classpath. Lựa chọn tốt cho messages **được đóng gói trong ứng dụng**, thường thay đổi cùng phiên bản phần mềm.
- **`ReloadableResourceBundleMessageSource`:** đọc qua Spring `Resource`/`PropertiesPersister`, cho phép basename là resource location linh hoạt hơn và kiểm tra thay đổi tệp theo cache policy. Lựa chọn phù hợp cho **tệp dịch nằm ngoài JAR**, cần nạp lại mà không triển khai lại ứng dụng.

Cả hai triển khai cùng `MessageSource`; một service chuyên gửi lời chào không cần biết hệ thống đang dùng bundle JDK hay Spring Resource.

### Mục tiêu của bài

Hiểu đúng `messageSource` bean name, `getMessage(...)` API, biến thể file theo locale, basename/encoding, fallback key, MessageFormat, cache/reload, parent-child context, cách inject, test và lỗi khi triển khai. Có một ví dụ Java 17/Maven tự chứa dùng cùng bộ tệp properties với **cả hai implementation**, cho stdout kiểm chứng được.

## 2. MessageSource và ApplicationContext

`ApplicationContext` triển khai `MessageSource` và khi refresh tìm bean tên **`messageSource`** để delegate `context.getMessage(...)`. Nếu thiếu nguồn local, nó có thể ủy quyền parent hoặc dùng delegate rỗng. Dù là `ResourceBundleMessageSource` hay `ReloadableResourceBundleMessageSource`, code sử dụng chỉ cần API:

```java
public final class GreetingService {
    private final MessageSource source;

    public GreetingService(MessageSource source) {
        this.source = source;
    }

    public String greet(String name, Locale locale) {
        return source.getMessage("greeting", new Object[]{name}, locale);
    }
}
```

Đây là class minh họa cần import `MessageSource` và `Locale`. Đặc điểm riêng ở bài này là **source đọc file thế nào, chọn implementation nào và cache/reload ra sao**, không phải khác biệt về API của consumer. `Environment.getProperty` dùng để tra config, khác việc resolve bản dịch theo Locale.

## 3. Bundle là gì? Basename và tên file

Một basename xác định nhóm bundles. Với `i18n/messages` trên classpath, những tệp hợp lệ gồm `messages.properties`, `messages_en.properties`, `messages_vi.properties`, `messages_vi_VN.properties` trong thư mục `i18n/`.

```properties
# messages.properties
status=Status: {0}
# messages_vi.properties (file riêng)
greeting=Xin chào, {0}!
```

Đây là hai nội dung file minh họa, không nên đặt comment biểu thị file thứ hai vào file thứ nhất khi tạo fixture. Lookup `vi-VN` có thể thử biến thể `vi_VN` rồi `vi` và bundle gốc tùy cấu hình, trong khi system/default Locale và parent là các tầng fallback riêng. Basename không chứa phần mở rộng `.properties` hoặc suffix Locale. Phần sâu hơn về naming, multi-basename precedence và MessageFormat nằm ở bài 260/262.

## 4. ResourceBundleMessageSource: tải theo chuẩn JDK

`org.springframework.context.support.ResourceBundleMessageSource` dùng `java.util.ResourceBundle` dưới lớp trừu tượng của Spring. Đây là cách đơn giản và hiệu quả khi messages được build vào JAR và không cần hot reload trong runtime.

Cấu hình Java mẫu:

~~~java
@Bean("messageSource")
public static MessageSource messageSource() {
    var source = new ResourceBundleMessageSource();
    source.setBasename("i18n/messages");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return source;
}
~~~

Đây là snippet; đặt bên trong `@Configuration` class và import `Bean`, `MessageSource`, `ResourceBundleMessageSource`.

**Basename đúng:** `"i18n/messages"`, không có `classpath:` hay `.properties`. `ResourceBundleMessageSource` sử dụng convention của JDK ResourceBundle, không chấp nhận mọi kiểu Spring Resource URI làm basename theo cùng cách với implementation Reloadable.

### Ưu điểm

- Bộ dịch nằm trong classpath, đóng gói cùng bản release.
- Tận dụng JDK ResourceBundle và Spring caching của message formats.
- Không phụ thuộc filesystem location hay deployment path tùy ý.
- Giữ logic nghiệp vụ đơn giản: service gọi MessageSource interface.
- Có thể đặt nhiều basenames khi ứng dụng chia nhóm bản dịch.

### Giới hạn

- Không phù hợp để đọc tùy ý file nằm bên ngoài classpath thông qua `file:` với cùng API basename.
- Có caching ở tầng JDK/classloader, nên `setCacheSeconds` không bảo đảm các thay đổi trong JAR hay classpath sẽ được phát hiện tin cậy.
- Không tự gộp mọi tệp trùng logical basename từ các JAR khác nhau; classloader thường trả bundle được ưu tiên theo lookup của JDK.
- Phụ thuộc runtime/classloader đối với classpath order, encoding và caching behavior.

### UTF-8

Trong Java 17, JDK ResourceBundle có cơ chế encoding khác với Java 8 cũ. Vẫn nên ghi `setDefaultEncoding("UTF-8")` và lưu source files thật ở UTF-8, nhất là có chữ Việt. Khi review build pipeline, cần kiểm tra resource filtering/plugin không vô tình biến đổi encoding.

## 5. ReloadableResourceBundleMessageSource: Spring Resource và reload

`org.springframework.context.support.ReloadableResourceBundleMessageSource` đọc message files qua `Resource` và `PropertiesPersister` của Spring. Khác với ResourceBundle implementation, basename có thể là một **Spring resource location**, chẳng hạn:

~~~text
classpath:i18n/messages
file:/opt/myapp/i18n/messages
file:/C:/apps/example/i18n/messages
WEB-INF/i18n/messages        (nếu có web context tương ứng)
~~~

Nó hỗ trợ các file `.properties` và `.xml` theo resource resolution strategy. Cấu hình khi messages được đóng gói trong classpath:

~~~java
@Bean("messageSource")
public static MessageSource messageSource() {
    var source = new ReloadableResourceBundleMessageSource();
    source.setBasename("classpath:i18n/messages");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    source.setCacheSeconds(-1);
    return source;
}
~~~

Đây là snippet. `-1` nghĩa là cache không cần kiểm tra hết hạn định kỳ, phù hợp với messages bất biến nằm trong JAR.

### Khi cần cập nhật từ filesystem

~~~java
@Bean("messageSource")
public static MessageSource messageSource() {
    var source = new ReloadableResourceBundleMessageSource();
    source.setBasename("file:/opt/myapp/i18n/messages");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    source.setCacheSeconds(5);
    return source;
}
~~~

Phải có file `/opt/myapp/i18n/messages.properties` và các biến thể theo Locale. `cacheSeconds=5` là thời gian cache/kiểm tra tương đối, **không** có nghĩa mọi người dùng sẽ thấy thay đổi trong đúng 5 giây.

### Vì sao classpath reload không đáng tin?

Khi file nằm trong JAR hoặc được đọc qua classloader cache, Spring không nhất thiết nhận được last-modified timestamp cập nhật như một file hệ thống. Spring Javadoc nêu rõ caching với `classpath:` khác `-1` có thể không hoạt động như mong đợi. Muốn hot reload, nên dùng tệp **ngoài classpath/JAR** và kiểm thử với đúng deployment filesystem.

### Không phải file watcher realtime

ReloadableMessageSource không tự là một service lắng nghe file changes rồi broadcast đến client. Nó duy trì caches và kiểm tra theo policy lúc truy cập. Các replicas/JVMs có thể có caches khác nhau; nếu deployment nhiều node, việc thay file và đồng bộ cache phải có quy trình riêng.

## 6. So sánh theo từng nhu cầu

| Tiêu chí | ResourceBundleMessageSource | ReloadableResourceBundleMessageSource |
| --- | --- | --- |
| API phía client | `MessageSource` | `MessageSource` |
| Cách nạp | JDK `ResourceBundle` | Spring `Resource`/Properties |
| Basename | `"i18n/messages"` | `"classpath:i18n/messages"` hoặc `"file:/.../messages"` |
| File resource dưới classpath | Có | Có |
| Tệp ngoài classpath | Không phải đường dùng chính | Được hỗ trợ bằng resource location phù hợp |
| `.properties` | Có | Có |
| `.xml` message resources | Theo conventions/JDK | Có support qua Spring properties persister |
| Hot reload từ external file | Không phải lựa chọn nên dùng | Được thiết kế hỗ trợ |
| Hot reload trong JAR | Không nên kỳ vọng | Không nên kỳ vọng |
| Cache | Có, chịu ảnh hưởng JDK/classloader | Có, kiểm tra update theo intervals |
| Locale fallback | Có | Có |
| MessageFormat | Có | Có |
| UTF-8 | Cấu hình encoding, chịu tác động JDK | Cấu hình default/file encodings |
| Nên chọn khi | Dịch đi cùng release | Dịch nằm ngoài JAR cần cập nhật |

Không suy ra rằng class có chữ `Reloadable` thì luôn reload tốt hơn trong mọi trường hợp. Khi dùng `classpath:` bên trong executable JAR, nó có thể giống ResourceBundle về mặt vận hành: muốn đổi bản dịch ổn định thì build/deploy phiên bản mới.

## 7. Cache policy: -1, 0 và số dương

`AbstractResourceBasedMessageSource` có API `setCacheSeconds(int)` và `setCacheMillis(long)`. Với ReloadableResourceBundleMessageSource, những setting này ảnh hưởng interval kiểm tra file:

| Giá trị | Diễn giải | Đánh đổi |
| --- | --- | --- |
| `-1` | Cache không hết hạn theo thời gian | Nhanh, phù hợp immutable resources; không tự nhận file mới |
| `0` | Kiểm tra rất thường xuyên khi lookup | Có thể tăng filesystem access; tránh áp production không kiểm thử |
| `5` | Kiểm tra lại sau khoảng 5 giây | Cân bằng freshness/overhead; không phải thời hạn cứng |
| `60` | Kiểm tra cách khoảng phút | Ít I/O hơn, nhưng cập nhật chậm hơn |

Khi interval hết, cache refresh có thể dựa vào `lastModified` của resource trước khi đọc toàn bộ file. Nó không nhất thiết load file lại nếu timestamp không thay đổi.

### Một timeline thực tế

~~~text
T0: messages_vi.properties chứa greeting=Xin chào, {0}!
T1: messageSource cache nội dung đó
T2: Operations thay file bằng greeting=Chào bạn, {0}!
T3: Một request tra message sau cache interval
T4: MessageSource kiểm tra mtime, reload nếu điều kiện phù hợp
T5: Các lookup tiếp theo có thể thấy Chào bạn, Lan!
~~~

Không biến timeline thành cam kết hard realtime. `T5` phụ thuộc file timestamp resolution, cache state, concurrent refresh, hệ thống tập tin và lớp cache khác.

### clearCache()

`ReloadableResourceBundleMessageSource` có `clearCache()` để giải phóng cached properties; đây là thao tác quản trị trên **một instance**, không phải update tất cả node. Không cấu hình service nghiệp vụ trực tiếp phải biết kiểu Reloadable chỉ để có quyền clearCache: tách trách nhiệm quản trị khỏi lookup bình thường.

### concurrentRefresh

Setting `setConcurrentRefresh(boolean)` điều chỉnh hành vi trong thời gian một thread tải lại resources. Nó liên quan trade-off giữa độ trễ request, việc dùng cache cũ trong lúc refresh và concurrency. Chỉ thay đổi sau khi đo/use case yêu cầu.

### Cache trong ResourceBundleMessageSource

Implementation JDK có cơ chế `ResourceBundle` cache riêng, nên việc tùy chỉnh expiration có thêm ràng buộc từ classloader. Nếu yêu cầu update ngoài JAR mà không restart là thiết yếu, chọn Reloadable và resource file phù hợp thay vì cố ép JDK ResourceBundle reload.

## 8. Locale, fallback và root bundle

`Locale` do ứng dụng truyền vào quyết định thứ tự tên file ứng viên.

~~~text
Request: Locale vi_VN
    │
    ├─ messages_vi_VN.properties
    ├─ messages_vi.properties
    ├─ (tùy policy) default/system-locale fallback
    ├─ messages.properties
    ├─ (tùy config) parent message source
    └─ default text hoặc exception nếu không tìm thấy code
~~~

Trong demo, cả hai config đều gọi:

~~~java
source.setFallbackToSystemLocale(false);
~~~

Điều này tránh fallback tùy ngôn ngữ mặc định JVM. Ví dụ máy developer có `Locale.getDefault()=en_US` nhưng server là `vi_VN`, không nên để cùng requested locale cho ra một ngôn ngữ khác vì môi trường.

### Root bundle có ích gì?

`messages.properties` nên chứa các keys tham chiếu hoặc thông điệp fallback. Nếu bản `vi` thiếu `status`, key có thể lấy từ root. Nếu code cũng không có trong root và không ở parent source, hệ thống sẽ dùng default text hoặc `NoSuchMessageException` tùy overload và config.

### @Profile không liên quan đến Locale

Spring `@Profile("dev")` chọn bean definitions theo deployment environment. `Locale("vi-VN")` chọn bản dịch cho người dùng. Đừng tạo profile `vi`/`en` để đổi language bundle theo từng HTTP request: một application context xử lý nhiều users thường có chung beans nhưng lookup messages theo nhiều Locales.

### setDefaultLocale

Một số API message source cho phép đặt default Locale khi không tìm được bản theo requested locale. Cần phân biệt requested Locale, JVM default và application default. Định nghĩa policy rõ và test đủ các tình huống, đặc biệt khi hệ thống phục vụ đa quốc gia.

## 9. MessageFormat: {0}, {1} và escape dấu nháy

`MessageSource` hỗ trợ arguments theo `java.text.MessageFormat`:

~~~properties
welcome=Xin chào, {0}!
items=Đơn {0} có {1} sản phẩm.
~~~

Java:

~~~java
String text = messageSource.getMessage(
        "items", new Object[]{"A-100", 3},
        Locale.forLanguageTag("vi-VN"));
~~~

Kết quả:

~~~text
Đơn A-100 có 3 sản phẩm.
~~~

**Không phải placeholder Environment:** `{0}` được MessageFormat thay bằng argument; `${config.key:default}` là property placeholder từ Spring. `#{expression}` là SpEL. Ba syntax khác nhau hoàn toàn.

### MessageFormat có định dạng number/date

~~~properties
price=Total: {0,number}
deadline=Due: {0,date,short}
~~~

Output phụ thuộc `Locale` và đối tượng được truyền, và date formatting còn có thể phụ thuộc timezone. Khi viết unit test, xác định Locale và timezone để không có sự khác biệt ngoài mong muốn.

### Cạm bẫy dấu nháy đơn

`MessageFormat` dùng `'` để quote pattern. Nếu một message có arguments mà cần hiển thị apostrophe:

~~~properties
alert=Don''t close {0}.
~~~

Thường nó được format thành:

~~~text
Don't close window.
~~~

Nếu chỉ ghi `Don't`, dấu apostrophe có thể bị coi là dấu mở quote của MessageFormat. Một option liên quan là `setAlwaysUseMessageFormat(true)`: mọi message sẽ được xử lý theo MessageFormat syntax, kể cả không có args, giúp semantics quote nhất quán nhưng yêu cầu translation files cũng phải escape đúng.

### Bảo mật và message arguments

Một parameter do người dùng nhập không tự được escape cho HTML/SQL/shell chỉ vì nó đi qua MessageSource. Tầng render phải escape theo output context; không dùng câu dịch làm một chuỗi HTML tin cậy nếu nó chứa user data. MessageSource là giải pháp **i18n**, không phải security encoder.

## 10. Chính sách missing code

Có ba cách phổ biến:

### (a) Default message tường minh

~~~java
String result = source.getMessage(
        "unknown.code", null, "Dự phòng", locale);
~~~

Nếu không tìm thấy code, overload có default trả `Dự phòng`.

### (b) Không default, yêu cầu có message

~~~java
String result = source.getMessage(
        "unknown.code", null, locale);
~~~

Nếu không tìm thấy, có thể ném `NoSuchMessageException`. Đây là hành vi hữu ích khi translation là bắt buộc, giúp test phát hiện thiếu keys.

### (c) useCodeAsDefaultMessage

~~~java
source.setUseCodeAsDefaultMessage(true);
~~~

Trong trường hợp thiếu, có thể trả luôn code `unknown.code`. Cách này đôi khi tiện khi development nhưng có thể khiến người dùng production nhìn thấy key nội bộ. Tốt hơn nên phát hiện thiếu key qua test/CI và có fallback text phù hợp.

### DefaultMessage và parent contexts

Khi dùng parent MessageSource hoặc nhiều basenames, phải hiểu cả thứ tự lookup trước khi kết luận code bị thiếu. Tránh bật `useCodeAsDefaultMessage=true` trong một child source để rồi đánh mất hành vi fallback sang parent theo như mong muốn; test hierarchy thực tế.

## 11. Java 17 runnable: hai implementation, cùng bundle

Ví dụ độc lập dùng **Java 17**, **Maven** và **Spring Framework 6.2.19** thuần (không cần Spring Boot, web server hay kết nối HTTP). Hai contexts độc lập tạo hai bean cùng tên `messageSource` nhưng khác class.

### Cấu trúc thư mục

~~~text
spring-message-source-demo/
├── pom.xml
└── src/main/
    ├── java/example/MessageSourceDemo.java
    └── resources/i18n/
        ├── messages.properties
        ├── messages_en.properties
        └── messages_vi.properties
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-message-source-demo</artifactId>
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

### src/main/resources/i18n/messages.properties

~~~properties
greeting=Hello, {0}!
farewell=Goodbye!
status=Status: {0}
~~~

### src/main/resources/i18n/messages_en.properties

~~~properties
greeting=Hello, {0}!
farewell=Goodbye!
~~~

### src/main/resources/i18n/messages_vi.properties

~~~properties
greeting=Xin chào, {0}!
farewell=Tạm biệt!
~~~

Lưu cả ba files bằng **UTF-8** để ký tự tiếng Việt không biến thành chuỗi mojibake.

### src/main/java/example/MessageSourceDemo.java

~~~java
package example;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

public final class MessageSourceDemo {

    public static void main(String[] args) {
        run("ResourceBundleMessageSource", JdkBundleConfig.class);
        run("ReloadableResourceBundleMessageSource", ReloadableConfig.class);
    }

    private static void run(String label, Class<?> configClass) {
        try (var context =
                     new AnnotationConfigApplicationContext(configClass)) {

            MessageSource source = context.getBean(MessageSource.class);
            Locale vi = Locale.forLanguageTag("vi-VN");

            System.out.println(label + ":");
            System.out.println("vi: " + source.getMessage(
                    "greeting", new Object[]{"Lan"}, vi));
            System.out.println("en: " + source.getMessage(
                    "greeting", new Object[]{"Lan"}, Locale.ENGLISH));
            System.out.println("root fallback: " + source.getMessage(
                    "status", new Object[]{"READY"}, vi));
            System.out.println("default: " + source.getMessage(
                    "unknown.code", null, "Dự phòng", vi));

            try {
                source.getMessage("unknown.code", null, vi);
                System.out.println("missing: unexpected success");
            } catch (NoSuchMessageException ex) {
                System.out.println("missing: NoSuchMessageException");
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class JdkBundleConfig {
        @Bean("messageSource")
        public static MessageSource messageSource() {
            var source = new ResourceBundleMessageSource();
            source.setBasename("i18n/messages");
            source.setDefaultEncoding("UTF-8");
            source.setFallbackToSystemLocale(false);
            return source;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ReloadableConfig {
        @Bean("messageSource")
        public static MessageSource messageSource() {
            var source = new ReloadableResourceBundleMessageSource();
            source.setBasename("classpath:i18n/messages");
            source.setDefaultEncoding("UTF-8");
            source.setFallbackToSystemLocale(false);
            source.setCacheSeconds(-1);
            return source;
        }
    }
}
~~~

### Lệnh chạy

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.MessageSourceDemo
~~~

Stdout do chính `System.out.println` tạo ra, không tính các thông báo riêng của Maven/logging:

~~~text
ResourceBundleMessageSource:
vi: Xin chào, Lan!
en: Hello, Lan!
root fallback: Status: READY
default: Dự phòng
missing: NoSuchMessageException
ReloadableResourceBundleMessageSource:
vi: Xin chào, Lan!
en: Hello, Lan!
root fallback: Status: READY
default: Dự phòng
missing: NoSuchMessageException
~~~

### Phân tích từng dòng

1. Context thứ nhất đăng ký `ResourceBundleMessageSource` với logical basename `i18n/messages`.
2. Context thứ hai đăng ký `ReloadableResourceBundleMessageSource` với Spring location `classpath:i18n/messages`. Cả hai sources cùng tìm ba files đã đóng gói.
3. `getBean(MessageSource.class)` trả bean đang có, service không cần ép kiểu sang implementation.
4. `vi-VN` tìm file Vietnamese `messages_vi.properties` vì không có `messages_vi_VN.properties`; `greeting` được format với argument `Lan`.
5. `Locale.ENGLISH` dùng `messages_en.properties`, nên trả câu tiếng Anh.
6. `status` không có trong file `messages_vi.properties`, nhưng có ở root, vì vậy trả `Status: READY`.
7. `unknown.code` thiếu ở mọi file: overload có default trả `Dự phòng`; overload strict ném `NoSuchMessageException`.
8. `setFallbackToSystemLocale(false)` khiến ví dụ không phụ thuộc JVM default locale. Root fallback vẫn hoạt động.
9. Đây là demo **bản dịch đóng gói**, không dùng filesystem hot reload. Muốn kiểm tra reload cần làm test khác trên file ngoài JAR.

### Khi chạy từ JAR

Tệp trong `src/main/resources` được Maven đưa vào classpath build artifact theo quy trình chuẩn. Trong môi trường đóng gói runnable JAR cần có launcher/manifest/dependency packaging tương ứng; lệnh `mvn exec:java` ở đây giúp thực thi ngay trên classpath Maven. Dù cách đóng gói thế nào, đừng dựa vào `new File("src/main/resources/...")` để đọc bundle khi chạy production.

## 12. Hot reload file ngoài JAR

Một ứng dụng cho phép operator chỉnh tệp bản dịch bên ngoài artifact có thể dùng:

~~~text
/opt/app/i18n/messages.properties
/opt/app/i18n/messages_vi.properties
/opt/app/i18n/messages_en.properties
~~~

Cấu hình:

~~~java
@Bean("messageSource")
static MessageSource messages() {
    var source = new ReloadableResourceBundleMessageSource();
    source.setBasename("file:/opt/app/i18n/messages");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    source.setCacheSeconds(5);
    return source;
}
~~~

Đây là snippet; đường dẫn ngoài JAR phải tồn tại và JVM cần quyền đọc. Basename **không** ghi đuôi `.properties`/`_vi`.

### Quy trình update an toàn

1. Chuẩn bị các bản dịch và kiểm tra key/format.
2. Ghi một tệp tạm hoặc một revision mới.
3. Sử dụng phương án publish atomically rename/swap khi filesystem hỗ trợ, tránh request đọc file đang ghi dở.
4. Kiểm tra last-modified timestamp thay đổi nếu reload implementation dựa vào nó.
5. Sau interval và một lookup mới, quan sát source có load bản mới không.
6. Đặt monitoring cho lỗi parse, missing key và inconsistency giữa replicas.
7. Có kế hoạch rollback một revision khi bản dịch bị lỗi.

Một cấu hình `cacheSeconds=5` **không đảm bảo SLA cập nhật đúng 5 giây**. Timestamp granularity, resource caching, concurrent refresh và thời điểm tra message tác động vào thời gian phát hiện.

### Test reload đúng cách

Dùng thư mục tạm **ngoài classpath**. Tạo file bản đầu, gọi `getMessage` và assert. Ghi bản thứ hai với timestamp khác, sau đó polling có deadline bounded cho đến khi thấy message mới. Không dùng duy nhất `Thread.sleep(5000)` rồi assert tức thì, vì test dễ flaky ở CI. Đóng context và dọn tệp tạm sau test.

## 13. Fallback system locale và default locale

Theo policy mặc định, có những trường hợp JVM's `Locale.getDefault()` ảnh hưởng candidate fallback. Trên server nhiều ngôn ngữ, đây là nguồn không xác định: container production có thể dùng một locale khác máy phát triển.

~~~java
source.setFallbackToSystemLocale(false);
~~~

Dùng config này khi muốn lookup theo **requested locale**, sau đó theo language/root rules do app định nghĩa, không tự chèn JVM locale làm nhánh bất ngờ.

Một policy tường minh nên xác định:

- Nếu `vi-VN` không có region-specific file: thử `vi`.
- Nếu `vi` thiếu key: fallback về bundle gốc hoặc application default theo config.
- Nếu code không có ở mọi bundle: hiển thị default tường minh hoặc fail test.
- Nếu app sử dụng parent message source: xác định thứ tự giữa child/parent và các default modes.

Không đánh đồng `setDefaultLocale(...)` với `setFallbackToSystemLocale(false)`: default locale của ứng dụng có thể là policy riêng, còn system locale là trạng thái global của JVM.

## 14. Multiple basenames, trùng keys và parent-child contexts

Có thể chia bundle theo module:

~~~text
i18n/common.properties
i18n/errors.properties
i18n/checkout.properties
~~~

Cấu hình ResourceBundle:

~~~java
source.setBasenames(
        "i18n/common", "i18n/errors", "i18n/checkout");
~~~

Cấu hình Reloadable tương tự nhưng prefix file/classpath theo resource location:

~~~java
source.setBasenames(
        "classpath:i18n/common",
        "classpath:i18n/errors",
        "classpath:i18n/checkout");
~~~

Theo Spring, thứ tự basenames có ảnh hưởng khi các bundles có key trùng nhau. Để tránh lỗi tinh vi, đặt **namespaced message codes**: `common.save`, `checkout.pay`, `error.required`. Không để module bất kỳ override key chung mà không có quy ước được test.

### Không tự động merge các JAR có cùng bundle name

Nếu hai thư viện có cùng basename `messages.properties`, classloader/JDK ResourceBundle không đảm bảo bạn nhận được hợp nhất key từ cả hai. Để aggregate nhiều bundles, dùng **distinct basenames** hoặc cơ chế collection/composition tường minh.

### HierarchicalMessageSource

Trong kiến trúc parent-child contexts, Spring có `HierarchicalMessageSource` cho phép child message source tra parent khi key vắng mặt theo hierarchy policy.

~~~text
Parent context: messageSource cho common keys
          |
          v
Child context: messageSource cho feature keys
          |
          +-- tra feature bundle trước
          +-- nếu thiếu, thử parent theo config
          +-- cuối cùng default hoặc exception
~~~

Không coi parent/child hierarchy là một tệp Properties được merge tự động. Nếu child bật `useCodeAsDefaultMessage`, có trường hợp fallback sang parent không còn đúng như mong đợi. Dùng integration test thể hiện rõ precedence mong muốn.

### commonMessages

Một số message source classes có option `setCommonMessages(Properties)` làm nguồn thông điệp chung không phụ thuộc locale. Đây có thể là fallback hữu ích, nhưng không thay localized basename nếu UI phải hỗ trợ nhiều ngôn ngữ thực sự.

## 15. Locale trong web app, jobs và async

Một `MessageSource` không tự đoán locale từ HTTP request nếu caller trực tiếp dùng Java API. Thông thường web layer chọn locale dựa trên policy của ứng dụng, rồi truyền tới service khi tạo thông điệp.

Có thể lựa chọn:

- `Locale.forLanguageTag("vi-VN")` từ một setting đã validate.
- Locale gắn với authenticated user profile.
- Locale được web framework resolve từ request.
- Một Locale explicit cho batch job/email notification.
- `LocaleContextHolder.getLocale()` trong luồng đã được framework thiết lập context thích hợp.

**Không gọi `Locale.setDefault(requestLocale)` trên mỗi request**: đây là JVM-wide mutable state, có thể ảnh hưởng các requests đồng thời. Với async/thread pool/reactive processing, `LocaleContextHolder` dạng thread-local có thể không tự propagate; truyền `Locale` explicit sẽ an toàn và dễ test hơn.

### Ngôn ngữ UI và ngôn ngữ log

Không phải log kỹ thuật nào cũng cần dịch. UI/customer-facing messages thường cần i18n; logs/metrics nên có codes và structure ổn định để vận hành. Nếu biến log lỗi thành message đã dịch theo từng request, search và aggregation có thể khó; nên lưu error codes riêng cùng những human-readable localized messages ở tầng presentation.

## 16. XML configuration

XML messageSource của JDK implementation:

~~~xml
<bean id="messageSource"
      class="org.springframework.context.support.ResourceBundleMessageSource">
    <property name="basename" value="i18n/messages"/>
    <property name="defaultEncoding" value="UTF-8"/>
    <property name="fallbackToSystemLocale" value="false"/>
</bean>
~~~

XML của Reloadable với file ngoài JAR:

~~~xml
<bean id="messageSource"
      class="org.springframework.context.support.ReloadableResourceBundleMessageSource">
    <property name="basename"
              value="file:/opt/app/i18n/messages"/>
    <property name="defaultEncoding" value="UTF-8"/>
    <property name="fallbackToSystemLocale" value="false"/>
    <property name="cacheSeconds" value="5"/>
</bean>
~~~

Đây là các **XML snippets**, cần bọc trong một file `<beans>` hoàn chỉnh có namespace/schema. Cấu hình chỉ khác implementation và nguồn file, còn beans dùng `MessageSource` interface không cần đổi.

## 17. Integration test và unit test

### Unit test service

Với service `GreetingService` chỉ phụ thuộc `MessageSource`, có thể dùng `StaticMessageSource` trong unit test và thêm messages programmatically, không cần tạo JAR hoặc đọc tệp properties:

~~~java
StaticMessageSource fake = new StaticMessageSource();
fake.addMessage(
        "greeting", Locale.forLanguageTag("vi-VN"),
        "Xin chào, {0}!");

GreetingService service = new GreetingService(fake);
String actual = service.greet(
        "Lan", Locale.forLanguageTag("vi-VN"));
~~~

Đây là snippet cần import `StaticMessageSource`, class `GreetingService` ở mục 2 và đúng test assertions; nếu lookup thành công, `actual` là `Xin chào, Lan!`.

### Integration test thực sự

- Tạo ApplicationContext chứa bean tên `messageSource`.
- Chỉ định locale `vi-VN`, `en` và một locale không có file dịch.
- Assert kết quả cụ thể từ `messages_vi.properties` và `messages_en.properties`.
- Assert key chỉ có trong root được fallback đúng.
- Assert overload có default trả default; overload strict ném `NoSuchMessageException`.
- Kiểm tra file encoding UTF-8, đặc biệt từ bản JAR đóng gói.
- Nếu nhiều modules/basenames, test key precedence và coverage.
- Nếu external reload, test bằng file ngoài classpath và quan sát lastModified/reload interval.

### Kiểm tra toàn bộ keys

Một job CI có thể lấy root bundle làm tập key chuẩn rồi so sánh với từng locale:

| Trường hợp | Nên làm gì? |
| --- | --- |
| Bản dịch thiếu key bắt buộc | Báo lỗi CI |
| Bản dịch thiếu key được phép fallback | Ghi nhận như warning theo policy |
| Key chỉ tồn tại ở locale mà root không có | Review tính nhất quán |
| `{0}` / `{1}` không khớp giữa các bản dịch | Báo lỗi format |
| Apostrophe sai quy tắc MessageFormat | Chạy format test thực |
| Trùng key trong một bundle | Bảo đảm scanner/build validation phát hiện |
| Dịch có HTML/script không mong muốn | Review nội dung và escape khi render |

Bộ kiểm thử trên quan trọng không kém implementation vì lỗi message code thường chỉ xuất hiện với một locale cụ thể.

## 18. Lỗi thường gặp và cách khắc phục

| Lỗi | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| Context không nhận MessageSource | Bean không tên `messageSource` | Đặt đúng tên |
| `NoSuchMessageException` | Code không có hoặc basename/locale sai | Kiểm tra files, keys, fallback |
| Message hiển thị dạng key | `useCodeAsDefaultMessage` hoặc client tự dùng code | Bổ sung dịch/test |
| `ResourceBundleMessageSource` không load `classpath:...` | Sử dụng Spring Resource prefix sai convention | Dùng `"i18n/messages"` |
| `Reloadable...` không tìm thấy file | Location/prefix/permissions sai | Kiểm tra `file:` hoặc `classpath:` và basename |
| File đúng nhưng thiếu bản tiếng Việt | Sai suffix `_vi`, resource không đóng gói | Kiểm tra filename/classpath |
| UTF-8 mất dấu | Toolchain hoặc encoding mismatch | Set encoding, lưu/test UTF-8 |
| Local dev có message nhưng server khác | JVM locale fallback khác | Đặt `fallbackToSystemLocale=false` |
| Cache 5s mà thay đổi chưa hiện | lastModified/classloader cache/lookup timing | Test external file & đúng cache policy |
| Reload classpath trong JAR thất bại | File JAR không hỗ trợ hot modify | Deploy ngoài JAR hoặc restart |
| `cacheSeconds=0` làm lookup chậm | Kiểm tra timestamp quá thường | Tăng interval, benchmark |
| Lookup `{0}` không thay | Args chưa truyền hoặc format pattern sai | Kiểm tra MessageFormat |
| Dấu nháy đơn bị mất | Apostrophe là quote trong MessageFormat | Escape bằng `''` |
| Số/ngày khác giữa máy | Locale/timezone khác | Chọn Locale/timezone rõ |
| Multiple basenames che key | Trùng names, precedence | Namespace codes |
| Dịch từ parent không được lấy | Hierarchy/useCodeAsDefaultMessage policy | Test parent-child resolution |
| Code dùng `Environment` cho i18n | Hai abstractions khác nhau | Dùng MessageSource |
| HTTP request khác nhau nhưng cùng tiếng | Lấy JVM default locale | Truyền request/user locale |
| Async job dịch sai | Thread-local Locale không truyền | Truyền Locale explicit |
| Update chỉ thấy ở một node | File/cache khác nhau theo replica | Đồng bộ deployment/cache |
| Số bản dịch thiếu chỉ phát hiện production | Không test key coverage | CI locale matrix |
| Dịch chứa user input gây XSS | Không escape output | Escape theo context render |

## 19. Câu hỏi tự kiểm tra

**1.** `ResourceBundleMessageSource` và `ReloadableResourceBundleMessageSource` có cùng API nào?

**Đáp án:** `MessageSource`; đều hỗ trợ `getMessage(...)` theo Locale và arguments.

**2.** Basename nào dùng cho `ResourceBundleMessageSource` khi files nằm tại `src/main/resources/i18n/messages_vi.properties`?

**Đáp án:** `i18n/messages`, không có `classpath:`, `.properties` hay suffix locale.

**3.** Basename nào dùng cho `Reloadable...` để đọc file cùng vị trí?

**Đáp án:** `classpath:i18n/messages`.

**4.** Có nên dùng `cacheSeconds=5` để mong Spring tự đọc lại resource nằm trong JAR sau khi sửa JAR?

**Đáp án:** Không. Classpath/JAR caching không hỗ trợ hot reload đáng tin; dùng file ngoài JAR khi cần reload.

**5.** `getMessage("missing",null,"Fallback",locale)` khác gì overload không default?

**Đáp án:** Trả `Fallback` nếu thiếu; overload không default có thể ném `NoSuchMessageException`.

**6.** Tại sao `fallbackToSystemLocale=false` có thể hữu ích?

**Đáp án:** Tránh JVM OS locale làm nhánh fallback ngầm, giúp kết quả nhất quán.

**7.** File `messages_vi.properties` có phục vụ `Locale.forLanguageTag("vi-VN")` không?

**Đáp án:** Có, nếu chưa có bản region-specific thích hợp và lookup theo language fallback.

**8.** `MessageSource` có tự dịch mọi đoạn text tiếng Anh sang tiếng Việt không?

**Đáp án:** Không. Nó chỉ lấy messages được cung cấp bởi các bundles/config sources.

**9.** `{0}` trong properties khác gì `${key}`?

**Đáp án:** `{0}` là tham số MessageFormat; `${key}` là property placeholder cho externalized config.

**10.** Dấu `'` trong message template có tác dụng gì?

**Đáp án:** Nó là quote marker của MessageFormat; cần `''` để thể hiện apostrophe literal trong trường hợp format.

**11.** Vì sao không gọi `Locale.setDefault(...)` theo từng request?

**Đáp án:** Nó thay locale cho toàn JVM và có thể tạo race/cross-request effects.

**12.** Có thể dùng Reloadable với nhiều files dịch ngoài JAR không?

**Đáp án:** Có; cấu hình `file:` basename và cache interval, đồng thời kiểm tra filesystem permissions/mtime/cache theo deployment.

## 20. Checklist áp dụng

1. Đã đăng ký bean **tên `messageSource`**?
2. Service inject interface `MessageSource` thay vì implementation?
3. Basename đúng convention riêng của từng implementation?
4. Files có trong classpath/JAR hoặc external folder thích hợp?
5. Có root bundle và các locale bundles được đặt tên đúng?
6. Files được lưu/đọc ở UTF-8?
7. Locale do caller truyền vào có đúng user locale?
8. Fallback system/default locale được lựa chọn tường minh?
9. Missing code policy: default, exception hoặc useCodeAsDefaultMessage?
10. Đã test `MessageFormat` arguments và apostrophe?
11. Có tránh dùng MessageSource thay cho Environment config?
12. Nếu chọn Reloadable, có xác định cần refresh từ **external files** thật không?
13. Cache interval có phù hợp performance/freshness?
14. Có giải pháp publish atomically và rollback external translations?
15. Có monitoring discrepancies giữa replicas?
16. Multiple basenames có tránh key collisions?
17. Parent-child message hierarchy đã được test?
18. Async tasks có truyền Locale explicit?
19. HTML/UI escaping có làm ở rendering layer?
20. CI có kiểm tra key coverage, encoding và format patterns?

## 21. Tài liệu tham khảo

- [Spring Framework Reference — Internationalization using MessageSource](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html)
- [Spring Framework 6.2 Javadoc — MessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/MessageSource.html)
- [Spring Framework 6.2 Javadoc — ResourceBundleMessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/ResourceBundleMessageSource.html)
- [Spring Framework 6.2 Javadoc — ReloadableResourceBundleMessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/ReloadableResourceBundleMessageSource.html)
- [Spring Framework 6.2 Javadoc — AbstractResourceBasedMessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/AbstractResourceBasedMessageSource.html)
- [Spring Framework 6.2 Javadoc — HierarchicalMessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/HierarchicalMessageSource.html)
- [Java SE 17 — ResourceBundle](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ResourceBundle.html)
- [Java SE 17 — MessageFormat](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/MessageFormat.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="internationalization-004">Basename, Locale và tham số định dạng thông điệp</a>

<details>
<summary>Xem chi tiết</summary>

# Basename, Locale và tham số định dạng thông điệp trong Spring Core Container

## 1. Bài toán: cùng một mã thông điệp, nhiều ngôn ngữ và dữ liệu động

Ứng dụng phục vụ người dùng ở nhiều quốc gia cần dịch thông điệp mà không đặt hàng loạt `if (language.equals("vi"))` vào business code. Một màn hình có thể cần:

- Tiếng Việt: **Xin chào, An!**
- Tiếng Anh: **Hello, An!**
- Một dòng động: **Bạn có 3 sản phẩm.**
- Các thông báo lỗi, labels và cảnh báo có cùng **message code**, nhưng câu chữ, cách sắp xếp tham số và quy tắc định dạng khác nhau theo ngôn ngữ.

Spring Framework cung cấp `org.springframework.context.MessageSource`: giao diện tra cứu thông điệp theo **code**, **tham số** và **`Locale`**. Với tệp Java `.properties` đóng gói trong classpath, implementation quen thuộc là `ResourceBundleMessageSource`.

Ba khái niệm trung tâm:

1. **Basename**: tên **nhóm resource bundles**, không chứa hậu tố ngôn ngữ hoặc `.properties`, ví dụ `i18n/messages`.
2. **Locale**: mô tả ngôn ngữ/khu vực cho lần tra cứu, ví dụ `Locale.ENGLISH`, `Locale.forLanguageTag("vi-VN")`, `Locale.JAPAN`.
3. **Arguments**: mảng các giá trị đưa vào thông điệp theo `java.text.MessageFormat`, với chỉ số `{0}`, `{1}`, và định dạng như `{0,number,integer}`.

**Nguyên tắc quan trọng:** Spring không tự dịch nội dung. Bạn **cung cấp các bản dịch**, Spring chọn key theo `Locale`, tìm qua các bundles và áp dụng tham số định dạng. Không có `Locale` thích hợp hoặc không có bản dịch không tự biến thông điệp tiếng Anh thành tiếng Việt nhờ một dịch vụ AI nào đó.

## 2. MessageSource là gì?

`MessageSource` là interface i18n của Spring, nhận **code, arguments và Locale** để trả thông điệp. Ba overload dùng trong ứng dụng:

```java
String translated = messages.getMessage(
        "welcome", new Object[]{"An"}, locale);
String optional = messages.getMessage(
        "missing.code", null, "Thông báo dự phòng", locale);
String resolved = messages.getMessage(resolvable, locale);
```

Đây là snippet cần biến `messages`/`resolvable`/`locale`. Dạng strict có thể ném `NoSuchMessageException`; dạng có default trả fallback khi không có bản dịch; `MessageSourceResolvable` đưa danh sách codes/arguments/default vào một object. Bài 261 phân tích kỹ khi nào dùng mỗi dạng.

`ApplicationContext` cũng là một `MessageSource`. Trong Spring Core thuần, cần đăng ký bean đúng tên `messageSource` để `context.getMessage(...)` sử dụng catalog mong muốn:

```java
@Bean("messageSource")
MessageSource messageSource() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("i18n/messages");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return source;
}
```

Đây là factory method minh họa trong `@Configuration`. Bài 258 trình bày trường hợp tên sai và nguồn fallback khi không có bean. Nếu chỉ cần dịch trong service, constructor-inject `MessageSource` thay vì giữ cả `ApplicationContext`; tầng web chọn request Locale, còn Spring Core không tự suy ngôn ngữ của user.

## 3. Basename: tệp nào thuộc cùng một nhóm thông điệp?

Một **basename** là tên gốc mà `ResourceBundleMessageSource` dùng để tìm các biến thể theo locale. Cấu trúc Maven:

```text
src/main/resources/
└── i18n/
    ├── messages.properties
    ├── messages_en.properties
    ├── messages_en_GB.properties
    ├── messages_vi.properties
    └── messages_vi_VN.properties
```

Nếu cấu hình:

```java
source.setBasename("i18n/messages");
```

thì Spring/Java ResourceBundle API có thể thử những candidate bundle phù hợp với locale và cơ chế fallback. Tên basename **không kèm `.properties`**, và không kèm `_vi`, `_en`, `_VN`.

| Thành phần | Ý nghĩa |
|---|---|
| `i18n/messages` | Basename, nhóm bundles nằm trong classpath |
| `messages.properties` | Bundle gốc, không gắn ngôn ngữ |
| `messages_en.properties` | Bản dịch theo language code `en` |
| `messages_en_GB.properties` | Bản dịch chuyên biệt cho English/United Kingdom |
| `messages_vi.properties` | Bản dịch theo language code `vi` |
| `messages_vi_VN.properties` | Bản dịch chuyên biệt cho Vietnamese/Vietnam |
| `welcome` | Code bên trong một hay nhiều tệp |

Một project không nhất thiết phải tạo **đủ mọi** biến thể. Chỉ tạo tệp thực sự cần; việc không có `messages_vi_VN.properties` không gây lỗi nếu có thể fallback sang `messages_vi.properties` hoặc bundle gốc cho key cần tra.

### 3.1. Tên đường dẫn basename đúng

Với tệp:

```text
src/main/resources/i18n/messages_vi.properties
```

khai báo:

```java
source.setBasename("i18n/messages");
```

Không dùng:

```java
source.setBasename("src/main/resources/i18n/messages_vi.properties");
```

Dòng thứ hai là **anti-example**, vì đó là đường dẫn nguồn lúc phát triển kèm language suffix và extension, không phải basename resource bundle ở runtime. Sau khi Maven đóng JAR, `src/main/resources` không còn là một đường dẫn filesystem cố định bên ngoài artifact.

### 3.2. Dấu chấm hay dấu slash?

`ResourceBundleMessageSource` sử dụng convention của JDK `ResourceBundle`, theo đó basename có thể viết dạng fully qualified classpath name. Ví dụ `i18n.messages` thường tương ứng resource dưới `i18n/messages` vì dấu chấm được đối chiếu với package separators. Tuy nhiên để thống nhất cùng đường dẫn trong Java/configuration, một codebase nên chọn **một convention nhất quán**, tránh vừa copy paths vừa suy diễn thành filesystem path.

Với `ReloadableResourceBundleMessageSource`, basename được hiểu theo Spring resource locations (thường gồm `classpath:`, `file:`) và cơ chế tải không giống hệt `ResourceBundleMessageSource` của JDK. **Không tự áp prefix `classpath:` vào mọi basename của mọi implementation**. Cách cấu hình theo đúng class là quan trọng.

### 3.3. Resource trên classpath so với file ngoài JAR

`ResourceBundleMessageSource` phù hợp với bundles được đóng gói trên classpath. Nếu muốn đọc những files ngoài JAR và cập nhật theo chính sách cache cụ thể, cân nhắc `ReloadableResourceBundleMessageSource` và đường dẫn Spring Resource tương ứng.

Nhưng chữ “reloadable” **không bảo đảm** classpath resources bên trong JAR có thể hot reload tin cậy. Với deploy production, thường phải đóng gói lại/restart hoặc dùng external filesystem/remote configuration được hỗ trợ đúng cách.

## 4. Locale: ngôn ngữ, quốc gia và fallback

`java.util.Locale` mô tả nhiều khía cạnh ngôn ngữ/khu vực, có thể gồm language, script, region và variant. Ví dụ:

```java
Locale en = Locale.ENGLISH;
Locale enGB = Locale.UK;
Locale viVN = Locale.forLanguageTag("vi-VN");
Locale jaJP = Locale.JAPAN;
```

Đây là snippet Java theo ngữ cảnh. `Locale.UK` đại diện `en_GB`; `Locale.JAPAN` đại diện `ja_JP`. Một `Locale` không phải tên language-only profile và không tự chứa câu dịch.

### 4.1. Fallback theo mức độ cụ thể

Với `Locale.forLanguageTag("vi-VN")`, trong trường hợp đơn giản có các bundles:

```text
messages_vi_VN.properties   (cụ thể hơn)
messages_vi.properties      (language)
messages.properties         (root)
```

Spring/JDK cố tìm message code theo chuỗi candidates và fallback phù hợp. Nếu file `messages_vi_VN.properties` không có code nhưng `messages_vi.properties` có, code có thể được resolve từ language-level bundle.

Ví dụ:

```properties
# messages.properties
app.brand=Spring I18n Demo
welcome=Welcome, {0}!
```

```properties
# messages_vi.properties
welcome=Xin chào, {0}!
```

Với locale `vi-VN`:

- Code `welcome` được chọn từ `messages_vi.properties`.
- Code `app.brand` không có trong bản dịch vi, nên lấy từ bundle gốc `messages.properties`.
- Code không có ở bất kỳ bundle nào vẫn được coi là **missing** và xử lý theo overload/default policy.

### 4.2. System locale fallback khác resource bundle fallback

`ResourceBundleMessageSource` có option:

```java
source.setFallbackToSystemLocale(false);
```

Nó ngăn cố gắng fallback qua **system/default locale** khi locale được yêu cầu không có bản dịch phù hợp, giúp kết quả ổn định hơn giữa máy phát triển và CI. **Nó không cấm fallback hợp lệ về bundle gốc `messages.properties`**.

Nếu để hệ thống tự fallback tới default locale, một request tiếng Nhật chưa có bundle `ja` có thể nhận bản dịch tiếng Anh, tiếng Việt hoặc một ngôn ngữ khác tùy default locale của JVM. Với product localization, cần chủ động quyết định policy fallback, thay vì phụ thuộc locale của server đang deploy.

### 4.3. Locale không tự xuất hiện từ Spring Core context

Trong ứng dụng Spring Core CLI, **caller chọn Locale** hoặc truyền một locale từ request/domain context. Spring Core `MessageSource` không tự biết locale người dùng đang mở trình duyệt, tài khoản nào đang sử dụng hay profile `dev` nào đang active.

Trong Spring MVC, có cơ chế `LocaleResolver`/`LocaleContextResolver` và `LocaleContextHolder` để liên kết locale với web request/thread context theo cấu hình. Đó là tầng web bổ sung, không phải behavior tự động của `new ResourceBundleMessageSource()` thuần.

### 4.4. Locale và time zone không đồng nhất

`Locale` ảnh hưởng cách trình bày ngôn ngữ, con số, ngày/tháng và đơn vị trong các formatter. Nhưng một date/time message có thể còn phụ thuộc **time zone** hoặc lựa chọn kiểu thời gian (`Instant`, `Date`, `LocalDate`).

Đừng kết luận `Locale.forLanguageTag("vi-VN")` tự đặt JVM time zone thành `Asia/Ho_Chi_Minh`. Nếu kết quả date/time cần deterministic, cung cấp time zone rõ ràng qua formatter/định dạng phù hợp hoặc giữ các test không phụ thuộc clock/time zone.

## 5. MessageFormat: tham số {0}, {1} và format style

`ResourceBundleMessageSource` dùng `java.text.MessageFormat` cho **thông điệp có arguments**. Ví dụ:

```properties
# messages_vi.properties
welcome=Xin chào, {0}!
order.status=Đơn hàng {0} của {1} đã tạo thành công.
basket.count=Bạn có {0,number,integer} sản phẩm.
```

Java:

```java
String hello = messageSource.getMessage(
        "welcome",
        new Object[]{"An"},
        Locale.forLanguageTag("vi-VN"));

String status = messageSource.getMessage(
        "order.status",
        new Object[]{"OD-007", "An"},
        Locale.forLanguageTag("vi-VN"));

String basket = messageSource.getMessage(
        "basket.count",
        new Object[]{3},
        Locale.forLanguageTag("vi-VN"));
```

Đây là snippets tích hợp cần `MessageSource messageSource` đã cấu hình. Kết quả tương ứng:

```text
Xin chào, An!
Đơn hàng OD-007 của An đã tạo thành công.
Bạn có 3 sản phẩm.
```

Các chỉ số **bắt đầu từ 0**:

- `{0}` lấy `args[0]`.
- `{1}` lấy `args[1]`.
- `{0,number,integer}` lấy `args[0]`, cố định dạng theo kiểu **số nguyên** theo locale.
- `{0,number,currency}` yêu cầu một giá trị số và sử dụng quy tắc currency locale.
- `{0,date,short}` cần argument tương thích với date formatter.
- `{0,time,short}` dùng time formatter.

**Đừng dùng cú pháp `%s` của `String.format` hoặc `{name}` của một template engine khác rồi mong `MessageFormat` tự hiểu.** Đây là ba định dạng khác nhau.

### 5.1. Vì sao tham số có chỉ số thay vì nối chuỗi?

Sai về localization:

```java
String result = "Hello " + name + ", you have " + count + " items.";
```

Chuỗi này ghép theo thứ tự tiếng Anh, khiến translator khó thay cấu trúc câu theo ngôn ngữ. Dùng message pattern:

```properties
order.ready=Order {0} is ready for {1}.
```

Và bản dịch:

```properties
order.ready=Đơn {0} đã sẵn sàng cho {1}.
```

Đây là snippets minh họa trong files tiếng Anh và Việt tương ứng. Translator có thể đảo thứ tự các placeholders nếu văn phạm yêu cầu, ví dụ dùng `{1}` trước `{0}`, mà Java code không cần thay đổi.

### 5.2. Định dạng số theo Locale

MessageFormat nhận format types như `number`, `date`, `time`, `choice`. Tập styles phổ biến:

| Pattern | Ý nghĩa |
|---|---|
| `{0}` | Giá trị được trình bày theo quy tắc chung |
| `{0,number}` | Số theo locale hiện thời |
| `{0,number,integer}` | Số nguyên theo locale |
| `{0,number,currency}` | Tiền tệ theo locale |
| `{0,number,percent}` | Phần trăm theo locale |
| `{0,date,short}` | Date dạng short của locale |
| `{0,date,long}` | Date dạng long của locale |
| `{0,time,short}` | Time dạng short của locale |

Với giá trị **3**, `{0,number,integer}` in `3` trong cả tiếng Anh và tiếng Việt. Với số lớn như 12.345, dấu ngăn cách hàng nghìn có thể khác giữa locales/CLDR versions; không hardcode expected punctuation của mọi JDK khi test đa môi trường.

### 5.3. Đối số sai kiểu có thể gây lỗi

Nếu message có:

```properties
basket.count=Bạn có {0,number,integer} sản phẩm.
```

mà code truyền:

```java
new Object[]{"not-a-number"}
```

`MessageFormat` có thể không chuyển được argument sang format type yêu cầu và ném `IllegalArgumentException` hoặc exception liên quan trong đường message resolution. Đây là **lỗi input/format**, không tự fallback sang bundle gốc hay default code.

Ưu tiên truyền một `Number` thích hợp:

```java
new Object[]{Integer.valueOf(3)}
```

Không lạm dụng formatting pattern để thay thế validation domain như số lượng âm, giới hạn giỏ hàng hoặc định dạng mã đơn.

### 5.4. Quote và apostrophe: bẫy đặc trưng của MessageFormat

Trong `MessageFormat`, dấu nháy đơn `'` là ký tự **quote cú pháp**. Muốn hiển thị một apostrophe đúng nghĩa trong pattern được format, cần viết **hai apostrophes**: `''`.

Ví dụ file tiếng Anh:

```properties
turn.message=It's {0}'s turn.
```

Nếu được parse bằng `MessageFormat`, chuỗi này **không** được bảo đảm giữ apostrophe như mong muốn. Pattern nên viết:

```properties
turn.message=It''s {0}''s turn.
```

và khi gọi với `new Object[]{"An"}`, kết quả:

```text
It's An's turn.
```

Các dấu ngoặc nhọn literal cũng cần được quote đúng cách theo cú pháp `MessageFormat`. Với tài liệu có JSON hoặc code snippets chứa `{` và `}`, không giả định có thể đặt nguyên văn vào pattern mà không escaping.

**Bẫy khó hơn:** `AbstractMessageSource` thường có tối ưu **thông điệp không arguments**, có thể bỏ qua `MessageFormat` để lấy raw text; cùng key với chuỗi chứa apostrophe có thể biểu hiện khác nếu đôi lúc gọi với args, đôi lúc gọi không args. Nếu muốn cùng semantics format cả no-arg messages, có thể xem `setAlwaysUseMessageFormat(true)` và áp thống nhất quy tắc escaping toàn bộ bundles.

### 5.5. Date/time formatting nên được test có time zone

Ví dụ minh họa:

```properties
event.at=Event starts on {0,date,short} at {0,time,short}.
```

và:

```java
String text = messageSource.getMessage(
        "event.at",
        new Object[]{new java.util.Date()},
        locale);
```

Đây là snippet tích hợp, cần `messageSource` và `locale`. **Không có stdout cố định**, vì kết quả chịu ảnh hưởng thời điểm hiện tại, locale, timezone/format data của JVM.

Trong hệ thống dùng `java.time`, kiểm tra hỗ trợ kiểu được formatter chấp nhận: `MessageFormat`/JDK date subformat truyền thống làm việc tự nhiên với `java.util.Date`; không suy rằng mọi `LocalDate`/`Instant` tự được format theo cùng cách. Với API mới, tách time zone và `DateTimeFormatter` theo `Locale` sẽ dễ kiểm soát.

### 5.6. Pluralization phức tạp

`MessageFormat` cổ điển có `choice` để chọn đoạn text theo number thresholds, nhưng plural grammar giữa các ngôn ngữ phức tạp hơn chỉ kiểm tra số bằng 1. Không lạm dụng `{0,choice,...}` để tuyên bố hỗ trợ toàn bộ ICU plural rules.

Nếu ứng dụng cần **pluralization/gender/case** tinh vi theo nhiều ngôn ngữ, xem thư viện i18n có ICU MessageFormat/CLDR rules, và quy định rõ loại pattern mà các tệp translation dùng. Spring `ResourceBundleMessageSource` mặc định dùng **JDK `MessageFormat`**, không tự hiểu toàn bộ ICU syntax.

## 6. Fallback khi thiếu thông điệp

Một mã chưa có bản dịch không luôn đồng nghĩa exception ngay. Kết quả tùy **bundle candidate**, **basename order**, **parent source** và **overload/API policy**.

### 6.1. Có defaultMessage

```java
String value = messageSource.getMessage(
        "unknown.code",
        new Object[]{"An"},
        "Fallback for {0}",
        locale);
```

Nếu không tìm thấy code trong bất kỳ source hợp lệ, implementation có thể format default message với arguments và trả:

```text
Fallback for An
```

Đây là snippet tích hợp và expected output với một source cấu hình như bài này. Có default làm ứng dụng thân thiện khi một label tùy chọn chưa được dịch; nhưng với thông điệp pháp lý, cảnh báo an toàn hoặc lỗi quan trọng, fallback tùy tiện có thể che thiếu bản dịch cần được kiểm soát.

### 6.2. Không có defaultMessage

```java
String value = messageSource.getMessage(
        "unknown.code",
        null,
        locale);
```

Overload này **yêu cầu** tìm được thông điệp; nếu không, Spring ném `NoSuchMessageException`. Đây là snippet minh họa lỗi có chủ đích.

Tận dụng cơ chế đó trong test hoặc startup validation để phát hiện missing message codes, thay vì chạy production lâu rồi mới thấy trống trên màn hình.

### 6.3. useCodeAsDefaultMessage

`AbstractMessageSource` có:

```java
source.setUseCodeAsDefaultMessage(true);
```

Khi bật, code thiếu có thể được hiển thị như fallback, ví dụ `unknown.code`, thay vì ném `NoSuchMessageException`. Hữu ích cho **development/debugging** để nhận diện keys bị thiếu, nhưng thường **không nên dùng như policy production** vì người dùng nhìn thấy technical code.

Một số tình huống có parent message sources và `MessageSourceResolvable` với nhiều codes cần thận trọng: dùng code-as-default ở parent có thể ngăn child tiếp tục thử các mã ưu tiên thấp hơn như mong đợi. Javadoc Spring khuyến nghị cân nhắc hành vi này, thay vì bật tùy tiện ở nhiều tầng.

### 6.4. MessageSourceResolvable có nhiều codes

`MessageSourceResolvable` diễn đạt **danh sách các codes theo thứ tự ưu tiên**. Ví dụ:

```java
MessageSourceResolvable resolvable =
        new DefaultMessageSourceResolvable(
                new String[]{"missing.specific", "screen.title"});

String label = messageSource.getMessage(resolvable, locale);
```

Đây là snippet tích hợp, cần imports `MessageSourceResolvable`, `DefaultMessageSourceResolvable` và source có `screen.title`. Spring thử các codes theo thứ tự, lấy mã đầu tiên có thể resolve, rồi mới dùng default của resolvable khi phù hợp. Cơ chế này được dùng rộng rãi trong validation/binding errors khi framework tạo nhiều candidate codes như `NotNull.user.name`, `NotNull.name`, `NotNull`.

Không nhầm nhiều code candidates với mảng arguments. `codes` là **các khóa thử theo thứ tự**; `args` là **giá trị nhét vào pattern** sau khi tìm được một khóa.

## 7. Nhiều basename: chia bundles và ưu tiên

Một source có thể cấu hình nhiều basenames:

```java
source.setBasenames("i18n/messages", "i18n/labels");
```

Ví dụ:

```text
i18n/messages.properties
  shared.key=PRIMARY

i18n/labels.properties
  shared.key=SECONDARY
  screen.title=Product catalog
```

Trong `ResourceBundleMessageSource`, các basenames **được kiểm tra theo thứ tự cấu hình**; basename đứng trước có độ ưu tiên cao hơn khi cùng message code xuất hiện ở nhiều nhóm.

Với hai bundles trên:

```java
messageSource.getMessage("shared.key", null, Locale.ENGLISH)
```

có thể trả `"PRIMARY"`; còn `screen.title` chỉ có ở second basename sẽ được tìm thấy ở `labels`.

### 7.1. Basename order và Locale fallback là hai trục khác nhau

Khi có nhiều basenames và nhiều locales, cần phân biệt:

- **Trục basename**: thứ tự ưu tiên nhóm resources, ví dụ `messages` rồi `labels`.
- **Trục locale**: tìm biến thể phù hợp (`_vi_VN`, `_vi`, root) **bên trong mỗi basename**, tùy implementation.
- **Parent MessageSource**: nếu local message source không tìm thấy, nó có thể ủy quyền lên parent theo cấu hình hierarchy.

Đừng tưởng tất cả files của tất cả basenames được trộn đơn giản rồi chọn file có locale “gần nhất” không xét nguồn. Trong thiết kế có collisions, **cần test tổ hợp code/locale thực tế**, không chỉ dựa vào tên file.

### 7.2. Nên chia bundles theo lĩnh vực khi nào?

Một project vừa đủ có thể dùng duy nhất `messages*.properties`. Project lớn có thể tách:

```text
i18n/
  common.properties
  common_vi.properties
  orders.properties
  orders_vi.properties
  validation.properties
  validation_vi.properties
```

Khi split, quy định ownership key và tránh collisions. Tên code có namespace như `orders.created`, `user.login.failed`, `common.save` giúp hạn chế xung đột.

Không mặc định thứ tự load giữa nhiều `@Configuration` classes luôn ổn định nếu mỗi class tự cấu hình một message source. Nên có một owner/configuration rõ ràng cho bean `messageSource`.

## 8. Encoding, cache và reload

Với text tiếng Việt, lưu `.properties` dưới **UTF-8**, cấu hình `source.setDefaultEncoding("UTF-8")` và test trên artifact đã build. `ResourceBundleMessageSource` dựa trên JDK `ResourceBundle`; `ReloadableResourceBundleMessageSource` dùng Spring resources, như `file:/opt/app/i18n/messages`. Hai lớp có semantics cache/load khác nhau, đặc biệt trên module path hoặc classloader.

**Cache không phải cơ chế dịch mới theo thời gian thực.** Bundles trong JAR được đóng cùng artifact; đổi chúng thường cần deploy/restart. Với external files, `ReloadableResourceBundleMessageSource` có `setCacheSeconds(10)` hoặc `setCacheMillis(10000)` để kiểm tra refresh khi lookup phù hợp, nhưng interval không bảo đảm cập nhật đồng thời trên mọi JVM/node.

Encoding chỉ chuyển **bytes thành ký tự**; `MessageFormat` xử lý **pattern và arguments** sau đó. File đọc đúng UTF-8 vẫn có thể bị lỗi nếu apostrophe/braces trong format pattern sai. Bài 259/262 đi sâu cache và bài 263 đưa kiểm thử byte-level/NFC cho tiếng Việt.

## 9. Ví dụ Java 17+ Spring Core Container: basename, Locale và parameters

Demo hoàn chỉnh sau sử dụng **Spring Framework 6.2.0**, **JDK 17+**, Maven, **không dùng Spring Boot**. Nó xác nhận:

1. Spring nhận bean đúng tên `messageSource`.
2. `Locale.UK` fallback về English bundle `messages_en.properties`.
3. `vi-VN` fallback về `messages_vi.properties`.
4. `Locale.JAPAN` (không có JA bundle) dùng message từ root bundle nhờ `setFallbackToSystemLocale(false)`.
5. Indexed arguments và `{0,number,integer}` được format.
6. Có hai basenames; key collision ưu tiên basename đầu, key riêng của basename thứ hai vẫn tìm được.
7. `MessageSourceResolvable` thử nhiều codes.
8. Khi thiếu code, overload có defaultMessage trả fallback, còn overload strict ném `NoSuchMessageException`.

### 9.1. Cấu trúc thư mục

```text
message-source-demo/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── example/
        │       └── MessageSourceDemo.java
        └── resources/
            └── i18n/
                ├── messages.properties
                ├── messages_en.properties
                ├── messages_vi.properties
                └── labels.properties
```

### 9.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>message-source-demo</artifactId>
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

### 9.3. src/main/resources/i18n/messages.properties

Đây là root bundle để mọi locale còn fallback khi chưa có translation chuyên biệt:

```properties
app.brand=Spring I18n Demo
welcome=Welcome, {0}!
basket.count=There are {0,number,integer} items.
shared.key=PRIMARY
```

### 9.4. src/main/resources/i18n/messages_en.properties

```properties
welcome=Hello, {0}!
basket.count=You have {0,number,integer} items.
turn.message=It''s {0}''s turn.
```

`turn.message` cố ý dùng hai dấu `''` ở mỗi apostrophe để JDK `MessageFormat` render đúng khi truyền arguments.

### 9.5. src/main/resources/i18n/messages_vi.properties

Tệp này phải được lưu bằng UTF-8:

```properties
welcome=Xin chào, {0}!
basket.count=Bạn có {0,number,integer} sản phẩm.
```

Không có `app.brand` tại đây: nó sẽ fallback sang root bundle. Không có `messages_vi_VN.properties`: locale `vi-VN` vẫn tìm được bản dịch tiếng Việt `messages_vi.properties`.

### 9.6. src/main/resources/i18n/labels.properties

```properties
screen.title=Product catalog
shared.key=SECONDARY
```

`screen.title` chỉ xuất hiện ở basename thứ hai. `shared.key` tồn tại ở cả hai để minh họa **ưu tiên basename đầu tiên**.

### 9.7. src/main/java/example/MessageSourceDemo.java

```java
package example;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.context.support.ResourceBundleMessageSource;

public class MessageSourceDemo {

    public static void main(String[] args) {
        Locale english = Locale.UK; // en_GB; fallback to en
        Locale vietnamese = Locale.forLanguageTag("vi-VN");
        Locale japanese = Locale.JAPAN; // ja_JP; no ja bundle

        try (var context = new AnnotationConfigApplicationContext(
                I18nConfig.class)) {

            // ApplicationContext implements MessageSource:
            System.out.println("english: " + context.getMessage(
                    "welcome", new Object[]{"An"}, english));

            System.out.println("vietnamese: " + context.getMessage(
                    "welcome", new Object[]{"An"}, vietnamese));

            System.out.println("count: " + context.getMessage(
                    "basket.count", new Object[]{3}, vietnamese));

            System.out.println("japanese-root: " + context.getMessage(
                    "welcome", new Object[]{"An"}, japanese));

            System.out.println("root-key: " + context.getMessage(
                    "app.brand", null, vietnamese));

            System.out.println("second-basename: " + context.getMessage(
                    "screen.title", null, vietnamese));

            System.out.println("basename-priority: " + context.getMessage(
                    "shared.key", null, vietnamese));

            var candidateCodes = new DefaultMessageSourceResolvable(
                    new String[]{"not.present", "screen.title"});

            System.out.println("resolvable: " + context.getMessage(
                    candidateCodes, vietnamese));

            System.out.println("default: " + context.getMessage(
                    "missing.message",
                    new Object[]{"An"},
                    "Fallback for {0}",
                    vietnamese));

            System.out.println("apostrophe: " + context.getMessage(
                    "turn.message", new Object[]{"An"}, english));

            try {
                context.getMessage(
                        "missing.message", null, vietnamese);
                System.out.println("strict-missing: false");
            } catch (NoSuchMessageException expected) {
                System.out.println("strict-missing: true");
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class I18nConfig {

        @Bean("messageSource")
        public MessageSource messageSource() {
            var messages = new ResourceBundleMessageSource();
            messages.setBasenames("i18n/messages", "i18n/labels");
            messages.setDefaultEncoding("UTF-8");
            messages.setFallbackToSystemLocale(false);
            return messages;
        }
    }
}
```

### 9.8. Lệnh chạy

Đứng ở thư mục chứa `pom.xml` và bảo đảm bốn files properties nằm đúng path:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.MessageSourceDemo
```

**Stdout dự kiến của chương trình**, không tính log ngoài do Maven/Spring nếu có:

```text
english: Hello, An!
vietnamese: Xin chào, An!
count: Bạn có 3 sản phẩm.
japanese-root: Welcome, An!
root-key: Spring I18n Demo
second-basename: Product catalog
basename-priority: PRIMARY
resolvable: Product catalog
default: Fallback for An
apostrophe: It's An's turn.
strict-missing: true
```

### 9.9. Tại sao chính xác những dòng này?

1. **`english`**: `Locale.UK` có language `en`, region `GB`. Không có `messages_en_GB.properties`, nhưng `messages_en.properties` tồn tại, nên kết quả `Hello, An!`.
2. **`vietnamese`**: `vi-VN` tìm thấy `messages_vi.properties`, thay `{0}` bằng `An`.
3. **`count`**: `basket.count` trong bundle vi có `{0,number,integer}`, số nguyên `3` được trình bày thành `3`.
4. **`japanese-root`**: không có `messages_ja*.properties`; `fallbackToSystemLocale(false)` giúp chọn root message `Welcome, {0}!`, không phụ thuộc default locale của máy.
5. **`root-key`**: key `app.brand` không có ở vi, lấy từ `messages.properties`.
6. **`second-basename`**: `screen.title` chỉ có ở `labels.properties`, nên vẫn được tìm thấy từ basename thứ hai.
7. **`basename-priority`**: `shared.key` ở cả `messages.properties` và `labels.properties`; basename `i18n/messages` đứng trước nên `PRIMARY` thắng.
8. **`resolvable`**: candidate `not.present` thiếu; Spring thử `screen.title` và tìm thấy `Product catalog`.
9. **`default`**: `missing.message` không tồn tại ở mọi bundle, nhưng overload có default pattern `Fallback for {0}`, nên in `Fallback for An`.
10. **`apostrophe`**: `MessageFormat` nhận `It''s {0}''s turn.` và output `It's An's turn.`.
11. **`strict-missing`**: overload strict `getMessage(code,args,locale)` không có default ném `NoSuchMessageException`, được catch và in true.

**Mức độ xác định của demo:** `vi-VN`/ `en-GB`/ `ja-JP` đều được chọn tường minh, bundles và utf8 encoding được cấu hình rõ ràng, số đưa vào format là số nhỏ tránh khác biệt grouping. Vì thế stdout không phụ thuộc máy đang đặt ngôn ngữ hay múi giờ gì.

### 9.10. Tự kiểm tra sau khi build

Trong Maven build, kiểm tra resources có mặt:

```bash
ls target/classes/i18n/
```

Kỳ vọng có `messages.properties`, `messages_en.properties`, `messages_vi.properties`, `labels.properties` ở thư mục tương ứng. Với Windows, dùng `dir target\classes\i18n` thay cho `ls`.

Nếu Maven resource filtering được bật riêng, kiểm tra plugin không vô ý thay patterns `{0}` hoặc làm biến dạng bytes Unicode. Trong cấu hình demo mặc định không cần lọc resources.

## 10. Truy cập từ Bean và từ web layer

### 10.1. Inject MessageSource vào một service

`ApplicationContext` và `MessageSource` có thể được dùng qua dependency injection:

```java
@Component
public class InvoicePresenter {

    private final MessageSource messages;

    public InvoicePresenter(MessageSource messages) {
        this.messages = messages;
    }

    public String itemCount(int count, Locale locale) {
        return messages.getMessage(
                "basket.count", new Object[]{count}, locale);
    }
}
```

Đây là snippet tích hợp; cần imports `@Component`, `MessageSource` và component scanning hoặc registration. Về kiến trúc, service nhận `Locale` tường minh giúp test thuần và không phụ thuộc thread-local locale của web framework.

Nếu domain logic không cần wording người dùng, cân nhắc để domain trả **message code + arguments** hoặc một domain event, rồi trình bày bản dịch ở application/presentation layer. Như vậy domain không phải biết ngôn ngữ ở thời điểm xử lý transaction.

### 10.2. Locale của request HTTP là trách nhiệm web layer

Trong Spring MVC, bạn có thể nhận `Locale` qua controller parameter hoặc truy cập locale resolution hạ tầng sau khi framework đã chọn locale từ request/preferences/headers theo cấu hình.

```java
@GetMapping("/greeting")
public String greeting(Locale locale) {
    return messageSource.getMessage(
            "welcome", new Object[]{"An"}, locale);
}
```

Đây là snippet **Spring MVC**, không biên dịch chỉ với `spring-context`. Nó cần `spring-webmvc`, `@GetMapping`, một controller managed bean và `messageSource` dependency. Cách locale được chọn phụ thuộc `LocaleResolver`/`LocaleContextResolver` đang hoạt động; không mặc định mọi ứng dụng cho user tùy đổi language bằng query parameter.

### 10.3. Không dựa vào Locale.getDefault() như lựa chọn người dùng

`Locale.getDefault()` trả locale mặc định của **JVM process**, không chắc phản ánh từng user/request. Nếu dùng nó trong một server đa người dùng, hai clients với ngôn ngữ khác nhau có thể nhận cùng output không phù hợp.

Đối với CLI chạy cho một user, JVM locale có thể là default hợp lý; với web/multi-tenant apps, locale nên đến từ authenticated user preferences, request negotiation hoặc application context rõ ràng.

### 10.4. MessageSource không phải template engine HTML

Thông điệp trả về là **String**; nếu chèn trực tiếp arguments từ user vào HTML rồi render không escaping, có thể gây XSS. `MessageFormat` **không tự bảo đảm HTML escaping/encoding theo output context**.

Cần escape theo đích output (HTML body, HTML attributes, JavaScript, URL, JSON, SQL...); không dùng MessageSource hoặc MessageFormat như một security sanitizer. Với email HTML, tách template rendering và localization để xử lý markup an toàn.

## 11. Các trường hợp lỗi và kỹ thuật chẩn đoán

| Hiện tượng | Nguyên nhân thường gặp | Cách kiểm tra |
|---|---|---|
| `NoSuchMessageException` | Code thiếu ở tất cả sources và không default | Kiểm tra tên code và bundles |
| Code lộ ra nguyên văn trên UI | `useCodeAsDefaultMessage(true)` hoặc default policy | Bỏ policy debug, bổ sung translations |
| Tiếng Việt thành ký tự lỗi | Encoding file/bundle decoding sai | Lưu UTF-8, `setDefaultEncoding("UTF-8")`, test packaging |
| Không tìm thấy bundles | Basename sai, thêm `.properties`, location sai | Dùng basename không extension/locale, kiểm tra runtime classpath |
| `messages_vi_VN` không tồn tại | Không nhất thiết là lỗi | Dùng `messages_vi` hoặc root fallback |
| Locale JA ra ngôn ngữ hệ điều hành | System-locale fallback có hiệu lực | `setFallbackToSystemLocale(false)` khi cần deterministic |
| `{0}` in nguyên văn | Không truyền arguments hoặc no-arg optimization | Truyền args phù hợp, xem `alwaysUseMessageFormat` |
| Dấu nháy đơn bị mất | Pattern chưa escape cho MessageFormat | Dùng `''`, test có arguments |
| `{0,number,...}` lỗi | Argument type không thích hợp | Truyền Number/validate input |
| Date/time không ổn định | Locale, clock/time zone/CLDR khác | Tách time zone, fixed clock, formatter rõ |
| Một code lấy từ file không mong muốn | Nhiều basenames/key collision | Kiểm tra basename order |
| Sửa properties mà UI vẫn như cũ | Cache, JAR immutable, không reload | Chọn deployment/reload strategy đúng |
| `ApplicationContext.getMessage()` không thấy translations | Bean không tên `messageSource` hoặc context khác | Kiểm tra bean name, context hierarchy |
| Mã validation không khớp | `MessageSourceResolvable` có nhiều codes | Log danh sách codes đúng thứ tự |
| Dịch chưa đủ mọi trường hợp số nhiều | Chỉ dùng JDK MessageFormat | Xem ICU/CLDR pluralization theo yêu cầu |
| Dịch text chứa HTML không an toàn | Input được format nhưng không escape | Escape theo output context |
| Bản dịch thay vị trí argument sai | Translator đổi index không đúng | Unit test với nhiều args khác nhau |
| Hai JVM chạy hai bản translations khác nhau | Versions/deployments không đồng bộ | Kiểm tra artifact checksum/version |

### 11.1. Quy trình debug theo từng lớp

1. Xác nhận source service: `MessageSource` được tạo bằng implementation nào? Có bean tên `messageSource` không?
2. Đọc basename **chính xác**, không dùng path `src/main/resources` như runtime location.
3. Kiểm tra các file `.properties` được đóng gói ở đâu trong JAR/classpath.
4. Xác nhận locale request **thật sự** được truyền vào `getMessage`: `vi-VN`, `en-GB` hay JVM default?
5. Nếu không có bundle `_language_REGION`, kiểm tra bundle `_language` và root.
6. Nếu source có nhiều basenames, kiểm tra priority và duplicate keys.
7. Nếu message thiếu, thử overload có explicit default để phân biệt lỗi lookup với lỗi formatting.
8. Nếu translation đúng nhưng placeholders sai, xác nhận `Object[] args` và indexing bắt đầu từ 0.
9. Kiểm tra dấu apostrophe `''` và curly braces theo `MessageFormat`.
10. Với số/ngày, xác nhận argument type, Locale, time zone và version dữ liệu format của JDK.
11. Nếu nội dung thay đổi không phản ánh, kiểm tra cache/reload/deploy và classloader đang sử dụng.
12. Nếu có Spring Boot/Web, kiểm tra message source auto-configuration/LocaleResolver, nhưng đừng quy tất cả hành vi đó cho Spring Core.
13. Viết tests có dữ liệu nhỏ đủ phân biệt languages, thay vì chỉ kiểm tra khác `null`.

### 11.2. Thiết kế message codes

Một hệ thống lớn nên có quy ước:

```text
screen.login.title
screen.login.invalidCredentials
order.created
order.cancelled
validation.user.email.required
validation.user.age.range
error.technical.unavailable
```

Các code minh họa này nên ổn định khi translator thay câu chữ. Nếu đổi text nhưng vẫn cùng ý nghĩa, giữ code. Nếu ý nghĩa nghiệp vụ thay đổi đáng kể, tạo code mới và cập nhật consumers rõ ràng để không tái sử dụng sai wording.

Tránh đưa thông tin nhạy cảm như account number, access token hoặc toàn bộ exception stack trace vào `args` hiển thị cho user. Localization không thay chính sách phân loại dữ liệu.

## 12. Kiểm thử message bundles

Đối với code có nhiều ngôn ngữ, kiểm thử không chỉ là khởi chạy ứng dụng.

### 12.1. Test đủ mọi code cần thiết

Xây danh sách code quan trọng và gọi:

```java
for (Locale locale : List.of(
        Locale.ENGLISH,
        Locale.forLanguageTag("vi-VN"))) {

    String text = messageSource.getMessage(
            "welcome", new Object[]{"Test"}, locale);

    if (text.isBlank()) {
        throw new AssertionError("Empty translated greeting");
    }
}
```

Đây là snippet tích hợp với `messageSource`/imports. Một test production-grade nên liệt kê tất cả keys cần thiết, kiểm tra missing translations theo locale và **không chỉ chấp nhận fallback root** nếu product yêu cầu bản dịch bản địa đầy đủ.

### 12.2. Test arguments và types

Kiểm tra một pattern có hai arguments bằng hai chuỗi **khác nhau**, ví dụ `"ORDER-7"` và `"Lan"`, để bắt lỗi translator đảo nhầm indexes. Với số, kiểm tra cả 0, 1, số lớn, số âm và case không hợp lệ theo validation.

Với date/time, dùng fixed `Date` hoặc fixed `Instant` đã được convert sang loại formatter hỗ trợ, và thiết lập time zone/test locale có chủ đích.

### 12.3. Test encoding và punctuation

Dùng những câu thử có dấu Việt như **“Cập nhật thành công”**, ký tự apostrophe trong English/French và các punctuation khác. Nếu chỉ test ASCII, cấu hình decoding sai có thể bị bỏ qua.

### 12.4. Test missing policies

- Với overload có explicit default: assert đúng fallback.
- Với overload strict: assert `NoSuchMessageException`.
- Với `MessageSourceResolvable`: assert thử candidates đúng thứ tự.
- Với source không root bundle: assert fallback như chủ đích, không chịu ảnh hưởng system locale ngoài mong muốn.

### 12.5. Test cả packaged runtime

Resource bundles được nạp qua classpath. Khi ứng dụng đóng executable JAR hoặc chạy trong Java module path, cơ chế loader có thể khác so với IDE. Kiểm tra tệp được packaged đúng, charset, classloader và `ResourceBundle` behavior trên runtime thật.

## 13. Best practices cho môi trường production

- Định nghĩa **một owner** cho bean `messageSource`, dùng basename order có chủ đích.
- Lưu thông điệp dưới **message codes ổn định**, không nối hardcode đa ngôn ngữ trong services.
- Gắn `Locale` với **người dùng/yêu cầu**, không mặc định ngôn ngữ JVM là ngôn ngữ mọi user.
- Dùng **UTF-8** và kiểm thử Unicode trên artifact đã đóng gói.
- Dùng `MessageFormat` với arguments **đúng types**; escape `''` và literal braces đúng cú pháp.
- Xem **missing translation** là một vấn đề chất lượng dữ liệu; defaultMessage hữu ích nhưng không nên che toàn bộ thiếu sót.
- Chỉ dùng `useCodeAsDefaultMessage(true)` cho debugging hoặc bối cảnh được chấp nhận rõ.
- Không mặc định classpath/JAR bundles được reload khi sửa file.
- Khi cần pluralization phức tạp, xem thêm ICU/CLDR thay vì tưởng JDK `MessageFormat` xử lý mọi ngữ pháp.
- Không để bản dịch hoặc args trở thành nguồn XSS/HTML injection; encode theo output context.
- Chạy tests cho **cả tiếng gốc và mỗi ngôn ngữ được hỗ trợ**, đặc biệt số, ngày giờ và dấu nháy đơn.
- Với parent/child contexts, xác định tầng nào sở hữu translations và cách fallback parent hoạt động.
- Khi hỗ trợ nhiều basenames, tránh đặt cùng message code ở hai bundles trừ khi có policy override xác định.
- Đừng đồng nhất message translation với `Environment.getProperty`; cả hai có thể đọc `.properties`, nhưng **giao diện/semantics** hoàn toàn khác.

## 14. Các lỗi hiểu nhầm thường gặp

| Hiểu nhầm | Điều đúng |
|---|---|
| “Basename là tên file `messages_vi.properties`” | Basename bỏ locale suffix và extension, ví dụ `i18n/messages` |
| “Một basename tự dịch văn bản” | Bạn phải cung cấp bundles/strings đã dịch |
| “Chỉ có `vi-VN` mới đọc được `messages_vi.properties`” | Locale fallback cho phép vi-VN sử dụng bundle vi |
| “`setFallbackToSystemLocale(false)` bỏ cả root bundle” | Nó tắt system locale fallback, không loại root fallback hợp lệ |
| “Locale quyết định timezone JVM” | Locale và timezone là hai khái niệm khác |
| “`{0}` là cú pháp String.format” | Đây là JDK MessageFormat, không phải `%s` |
| “`{0,number,integer}` chấp nhận mọi String” | Cần argument type tương thích với number format |
| “Text có dấu nháy đơn cứ giữ nguyên” | MessageFormat dùng apostrophe như syntax; cần `''` |
| “Code missing lúc nào cũng trả null” | Overload strict ném `NoSuchMessageException`, overload có default trả fallback |
| “`MessageSourceResolvable` chỉ cho một key” | Có thể thử nhiều codes theo thứ tự |
| “Mọi custom messageSource bean tự được context phát hiện theo type” | Bean name `messageSource` có vai trò đặc biệt trong context |
| “Nếu sửa messages.properties thì JAR đổi ngay” | Caching và packaging ngăn expectation hot reload tùy tiện |
| “Reloadable resource source luôn thấy files trong JAR thay đổi” | Classpath/JAR thường không thể reload đáng tin |
| “Spring Core tự chọn ngôn ngữ theo Accept-Language” | Locale resolution theo request thuộc web stack |
| “MessageSource tự xử lý HTML escaping” | Không; phải encode theo output context |
| “Dùng `Locale.getDefault()` là luôn đúng” | Default JVM có thể khác locale từng user |
| “Nhiều basenames không bao giờ có collision” | Có thể trùng code; thứ tự source có ý nghĩa |
| “MessageFormat hỗ trợ ICU plural syntax” | JDK MessageFormat và ICU có capability khác nhau |

## 15. Câu hỏi tự kiểm tra

**1. Basename là gì trong `ResourceBundleMessageSource`?**  
Tên nhóm bundles không kèm `_locale` hay `.properties`, ví dụ `i18n/messages`.

**2. Tệp `messages_vi.properties` được chọn khi caller truyền locale nào?**  
Một locale language `vi` phù hợp, như `Locale.forLanguageTag("vi-VN")`, khi không có bản chuyên biệt ghi đè.

**3. Để context.getMessage dùng cấu hình của bạn, Spring tìm bean tên gì?**  
`messageSource`.

**4. `Locale.ENGLISH` tự tạo bản dịch tiếng Anh không?**  
Không; nó chỉ hướng lookup/format. Bundle vẫn phải chứa message.

**5. Placeholder `{0}` lấy dữ liệu từ đâu?**  
Phần tử `args[0]` trong array truyền vào `getMessage`.

**6. `{0,number,integer}` có tác dụng gì?**  
Định dạng một đối số số theo integer format của locale.

**7. Vì sao dùng `new Object[]{3}` thay vì chuỗi `"three"` cho number format?**  
Vì formatter cần loại `Number` tương thích.

**8. Cách biểu diễn dấu apostrophe trong pattern đã được parse bằng MessageFormat?**  
Viết hai dấu nháy đơn `''`.

**9. Overload nào ném `NoSuchMessageException` khi thiếu code?**  
`getMessage(code, args, locale)` hoặc overload nhận `MessageSourceResolvable` khi không có resolution/default phù hợp.

**10. Overload nào cho phép fallback text do caller chỉ định?**  
`getMessage(code, args, defaultMessage, locale)`.

**11. `MessageSourceResolvable` giải quyết bài toán gì?**  
Cho phép thử nhiều candidate codes, với arguments và default khi phù hợp.

**12. Khi có hai basenames cùng key, basename nào ưu tiên trong `ResourceBundleMessageSource`?**  
Basename được cấu hình trước.

**13. `setFallbackToSystemLocale(false)` có vô hiệu hóa `messages.properties` không?**  
Không; root bundle vẫn là fallback hợp lệ.

**14. Vì sao không hardcode `Locale.getDefault()` trong web application nhiều users?**  
Nó phản ánh JVM default, không bảo đảm là ngôn ngữ riêng của từng user/request.

**15. `ReloadableResourceBundleMessageSource` có tự reload nested classpath JAR resources không?**  
Không nên kỳ vọng; cần external files và kiểm thử cache/deployment phù hợp.

**16. MessageSource có bảo vệ HTML injection không?**  
Không. Escape/encode theo output context là trách nhiệm tầng hiển thị.

**17. Khi nào cần xem ICU thay vì JDK MessageFormat?**  
Khi bài toán cần plural/gender/grammar rules vượt quá khả năng của JDK MessageFormat mặc định.

## 16. Tài liệu tham khảo chính thức

- [Spring Framework Reference — Internationalization using MessageSource](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-messagesource): đăng ký bean tên `messageSource`, các implementations và message lookup.
- [Spring Framework 6.2 API — MessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/MessageSource.html): các overload `getMessage`, arguments, default và Locale.
- [Spring Framework 6.2 API — ResourceBundleMessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/ResourceBundleMessageSource.html): basename conventions, caching, encoding/JDK ResourceBundle.
- [Spring Framework 6.2 API — ReloadableResourceBundleMessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/ReloadableResourceBundleMessageSource.html): Spring Resource-based loading, cache và reload.
- [Spring Framework 6.2 API — AbstractMessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/AbstractMessageSource.html): missing code handling, useCodeAsDefaultMessage và parent messages.
- [Spring Framework 6.2 API — DefaultMessageSourceResolvable](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/DefaultMessageSourceResolvable.html): danh sách codes, arguments, default.
- [Java SE 17 — ResourceBundle](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ResourceBundle.html): bundle naming, locale candidate resolution và fallback.
- [Java SE 17 — MessageFormat](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/MessageFormat.html): patterns `{0}`, number/date formats, apostrophe escaping, locale rules.
- [Java SE 17 — Locale](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Locale.html): language tags và locale construction.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="internationalization-005">Thông điệp mặc định và xử lý khóa không tồn tại</a>

<details>
<summary>Xem chi tiết</summary>

# Thông điệp mặc định và xử lý khóa không tồn tại trong Spring Framework Core Container

## 1. Vấn đề: MessageSource phải làm gì khi thiếu một mã thông điệp?

Trong ứng dụng đa ngôn ngữ, một thông báo thường được truy xuất bằng **mã (message code)** thay vì viết trực tiếp vào Java:

```java
String text = messageSource.getMessage(
        "order.completed",
        new Object[] { orderCode },
        locale);
```

Đây là fragment: `messageSource`, `orderCode` và `locale` phải được khai báo bởi ứng dụng. Ý nghĩa là “tìm nội dung cho mã `order.completed` theo ngôn ngữ của người dùng, rồi điền tham số”. Nếu file ngôn ngữ có khóa đó, Spring trả bản dịch tương ứng. Nhưng điều gì xảy ra nếu:
- Bản tiếng Việt thiếu khóa, còn file gốc có?
- Mã không tồn tại trong **bất kỳ** bundle hoặc nguồn message nào?
- Người gọi muốn hiện thông báo thân thiện thay vì fail?
- Người gọi cần phát hiện ngay lỗi thiếu bản dịch để sửa?
- Có nhiều mã từ cụ thể đến tổng quát, như `error.user.email.required` rồi `error.required`?
- Một bundle có khóa với giá trị **rỗng**, khác với khóa hoàn toàn không có?
- JVM chạy trên máy locale tiếng Việt còn request là tiếng Pháp?

Các câu hỏi này là nội dung trọng tâm của **thông điệp mặc định (default message)** và **xử lý khóa không tồn tại (missing message code)** trong Spring Framework.

**Nguyên tắc:** luôn phân biệt ba cấp fallback:
1. **Locale fallback**: tìm cùng một message code trong các file/ngôn ngữ phù hợp và bundle gốc.
2. **Code fallback**: nếu code đầu tiên không có, thử mã tiếp theo qua `MessageSourceResolvable`.
3. **Default/error policy**: nếu không có mã nào phù hợp, trả default string, trả `null`, trả chính code hoặc ném `NoSuchMessageException` theo API/cấu hình.

Chúng không đồng nghĩa nhau và cần được kiểm thử độc lập.

## 2. MessageSource và các overload có semantics khác nhau

`org.springframework.context.MessageSource` là interface chuẩn hỗ trợ thông điệp đa ngôn ngữ. Ba overload cốt lõi:

```java
// Chữ ký API giản lược, không viết lại interface khi dùng Spring.
String getMessage(
        String code, Object[] args, String defaultMessage, Locale locale);

String getMessage(
        String code, Object[] args, Locale locale)
        throws NoSuchMessageException;

String getMessage(
        MessageSourceResolvable resolvable, Locale locale)
        throws NoSuchMessageException;
```

Ở Spring 6.x, các tham số/kiểu trả về được đánh dấu nullability phù hợp trong Javadoc. Đặc biệt overload **có `defaultMessage`** có thể trả **`null`** khi code không tìm thấy và default là `null`. Không khai báo biến nhận là “luôn non-null” mà không có điều kiện.

| Cách gọi | Code được tìm thấy | Không tìm thấy code |
| --- | --- | --- |
| `getMessage(code, args, locale)` | Trả message đã resolve/format | Ném `NoSuchMessageException` theo cấu hình mặc định |
| `getMessage(code, args, "Fallback", locale)` | Trả message từ bundle (không dùng fallback) | Trả `"Fallback"`, được format nếu có args |
| `getMessage(code, args, null, locale)` | Trả message từ bundle | Thường trả `null`, trừ khi có cấu hình fallback khác như use-code-as-default |
| `getMessage(resolvable, locale)` | Thử các mã theo thứ tự và trả mã đầu tiên có nội dung | Trả default của resolvable hoặc ném exception nếu không có fallback |
| `setUseCodeAsDefaultMessage(true)` trên `AbstractMessageSource` | Không thay bản dịch đã tìm được | Có thể trả **chính message code** thay vì ném lỗi |

Bảng trình bày hành vi chuẩn của `ResourceBundleMessageSource`/`AbstractMessageSource`. Một custom `MessageSource` có thể cài chính sách riêng; vì vậy hãy dựa vào contract interface và implementation thực tế.

### Overload explicit default có tính ưu tiên rất cao

```java
String label = messageSource.getMessage(
        "action.submit",
        null,
        "Gửi",
        Locale.forLanguageTag("vi-VN"));
```

Nếu `action.submit` tồn tại trong bundle, Spring dùng **bản dịch**, không dùng chuỗi “Gửi”. Default string chỉ được sử dụng khi không giải được message code. Đừng dùng fallback như một cách override nội dung đã dịch.

### Khi nào chọn từng overload?

- **Bắt buộc có bản dịch**: overload không có default, để fail và được phát hiện trong test/startup phù hợp.
- **Thông báo UI không được phép crash vì thiếu bản dịch ít quan trọng**: overload có default string thân thiện.
- **Phân biệt missing với empty**: overload trả `null` (default `null`) rồi xử lý rõ; có thể kết hợp audit metrics.
- **Validation nhiều mức mã**: truyền `MessageSourceResolvable` chứa chuỗi codes và một default hợp lý.
- **Debug thiếu mã**: có thể tạm dùng `useCodeAsDefaultMessage` ở môi trường phát triển; đừng mặc định đưa nguyên technical key cho người dùng production.

## 3. Fallback theo Locale: thiếu bản dịch chưa phải thiếu message code

Giả sử có ba file:

```text
src/main/resources/i18n/
├── messages.properties
├── messages_vi.properties
└── messages_en.properties
```

`messages.properties` là bundle gốc; các file `messages_vi.properties` và `messages_en.properties` phục vụ ngôn ngữ tương ứng. Với `Locale.forLanguageTag("vi-VN")`, một implementation dựa trên `ResourceBundle` thường xét các candidates theo độ đặc hiệu:

```text
messages_vi_VN.properties  (nếu tồn tại)
            |
messages_vi.properties     (nếu tồn tại)
            |
messages.properties        (bundle gốc, nếu có)
```

Khi `messages_vi.properties` thiếu `app.brand` nhưng `messages.properties` có `app.brand`, việc tra `app.brand` **thành công nhờ bundle gốc**, chứ không phải vì `getMessage(..., defaultMessage, ...)` đã được sử dụng.

Ví dụ:

**messages.properties**

```properties
app.brand=Spring Workshop
welcome=Hello, {0}!
```

**messages_vi.properties**

```properties
welcome=Xin chào, {0}!
```

Với locale Việt Nam:
- `welcome` → “Xin chào, An!” khi argument là “An”.
- `app.brand` → “Spring Workshop” dù file tiếng Việt không có key này.
- `never.defined` → không tìm được nếu mọi nguồn đều thiếu, mới xét explicit default/exception/code-as-default.

### FallbackToSystemLocale khác bundle gốc

`ResourceBundleMessageSource` có tùy chọn `setFallbackToSystemLocale(boolean)`. Theo mặc định, việc fallback sang system locale có thể tham gia quá trình chọn resource trong Java ResourceBundle semantics. Máy server có system locale khác user locale, khiến kết quả gây ngạc nhiên.

Trong ví dụ runnable, cấu hình:

```java
source.setFallbackToSystemLocale(false);
```

Cách này **tắt fallback sang Locale mặc định của JVM**, chứ **không tắt bundle gốc `messages.properties`**. Vì vậy một key thiếu ở `messages_vi.properties` vẫn có thể được lấy từ `messages.properties`.

Với hệ thống đa tenant hoặc có yêu cầu locale fallback riêng, hãy thiết kế default locale tường minh và viết tests tương ứng. Không dựa vào cấu hình ngôn ngữ của máy chủ production.

### Locale không có bundle chuyên biệt

Nếu request mang `fr-FR` mà không có `messages_fr.properties`, bundle gốc vẫn có thể phục vụ message. Điều này thường có chủ đích để tránh blank UI. Nhưng với sản phẩm yêu cầu bản dịch đầy đủ, cần kiểm thử coverage để phát hiện những key chỉ còn ở bundle gốc.

## 4. Fallback explicit: getMessage(code, args, defaultMessage, locale)

Đây là kỹ thuật xử lý missing code rõ ràng nhất tại **điểm gọi**:

```java
String message = messageSource.getMessage(
        "notification.retry.later",
        null,
        "Vui lòng thử lại sau.",
        Locale.forLanguageTag("vi-VN"));
```

Nếu code không có trong bất kỳ source nào, kết quả là default “Vui lòng thử lại sau.”. Chương trình không cần catch `NoSuchMessageException` cho trường hợp missing code này.

Có thể kết hợp arguments:

```java
String message = messageSource.getMessage(
        "greeting.visitor",
        new Object[] {"Mai"},
        "Xin chào khách {0}",
        Locale.forLanguageTag("vi-VN"));
```

Với `ResourceBundleMessageSource` chuẩn và không có code `greeting.visitor`, default cũng đi qua xử lý `MessageFormat` thích hợp để cho “Xin chào khách Mai”.

**Default string không phải locale translation tự động**. Nếu chính bạn truyền chuỗi tiếng Việt, request ngôn ngữ tiếng Anh nhưng thiếu code vẫn nhận tiếng Việt. Để đa ngôn ngữ tốt:
- Dùng bundle gốc bằng ngôn ngữ fallback được sản phẩm quy định;
- Có chính sách default ngắn và trung tính khi cần;
- Hoặc thử một **mã fallback khác** trong `MessageSourceResolvable` để vẫn có bản dịch theo locale;
- Đưa key còn thiếu vào pipeline dịch thay vì coi default là giải pháp lâu dài.

### Default message có thể chứa tham số

`{0}`, `{1}` và các mẫu như `{0,number}` là cấu trúc của `java.text.MessageFormat`. Các arguments phải được truyền dưới dạng `Object[]` đúng vị trí. Không dùng `String.format` với `%s` rồi mong `MessageSource` tự xử lý.

### Null default là một tín hiệu hợp lệ

```java
String maybeMessage = messageSource.getMessage(
        "optional.caption",
        null,
        null,
        locale);

if (maybeMessage == null) {
    // Chủ động chọn ẩn caption hoặc ghi metric về missing key.
}
```

Đây là fragment cần biến `messageSource` và `locale`. Với cấu hình mặc định, không tìm thấy code trả `null`. Dùng cách này khi caller muốn **phân biệt không có nội dung** với **có nội dung rỗng**.

Một key `optional.caption=` với value rỗng có thể được coi là **đã được định nghĩa** và trả `""`, không phải `null`. Đừng viết `if (message.isBlank())` rồi kết luận chắc chắn key missing; đó là chuyện validation riêng.

## 5. Chính sách strict: NoSuchMessageException

Khi một message bắt buộc tồn tại, sử dụng overload **không default**:

```java
String text = messageSource.getMessage(
        "payment.authorization.failed",
        new Object[] {"TX-42"},
        locale);
```

Nếu code không giải được, implementation chuẩn ném `org.springframework.context.NoSuchMessageException`. Ví dụ xử lý có chủ đích:

```java
try {
    String text = messageSource.getMessage(
            "payment.authorization.failed", null, locale);
    render(text);
} catch (NoSuchMessageException ex) {
    recordMissingTranslation("payment.authorization.failed");
    render("Không thể hiển thị thông báo.");
}
```

Đây là pseudocode cần imports và hai method `render(...)`/`recordMissingTranslation(...)` của ứng dụng. Không đưa raw exception hay technical code ra giao diện người dùng nếu policy không cho phép.

### Vì sao fail-fast đôi khi tốt?

Nếu message có ý nghĩa bắt buộc — thông báo lỗi giao dịch, bản đồng ý pháp lý, hướng dẫn vận hành nhạy cảm — việc âm thầm hiện một mã như `consent.legal.v3` có thể nghiêm trọng hơn một lỗi được phát hiện trong kiểm thử. Overload strict giúp:
- Phát hiện key sai chính tả hoặc bị xóa.
- Ngăn ứng dụng che các lỗ hổng bản dịch quan trọng.
- Tạo điều kiện cho integration tests kiểm tra coverage.
- Phân biệt lỗi cấu hình catalog với lỗi logic domain.

Tuy nhiên exception ngay trong một request production có thể làm trải nghiệm người dùng tệ đi. Giải pháp là **kiểm tra catalog từ CI/startup** và thiết kế fallback riêng ở boundary UI, không tắt mọi exception vô điều kiện.

### Không catch chung Exception để “dịch”

```java
try {
    return messageSource.getMessage(code, args, locale);
} catch (Exception ignored) {
    return code;
}
```

Đây là anti-pattern: nó biến lỗi parsing, lỗi đọc bundle, lỗi conversion và lỗi lập trình thành “missing key”, đồng thời rò code tới người dùng. Nếu thật sự cần fallback, bắt **`NoSuchMessageException`** cho nhánh missing-key hoặc dùng overload có default rõ.

### Ngoại lệ không đồng nghĩa file ngôn ngữ vắng mặt

Ngay cả khi file bundle tồn tại, code cụ thể vẫn có thể vắng mặt. Ngược lại, nếu `basename` khai báo sai và Spring không load bundle nào, nhiều codes sẽ missing. Hãy xác minh path file, tên key, encoding và locale fallback trước khi kết luận “người dùng chọn ngôn ngữ sai”.

## 6. useCodeAsDefaultMessage: trả mã kỹ thuật thay vì exception

`ResourceBundleMessageSource` kế thừa `AbstractMessageSource` và hỗ trợ:

```java
ResourceBundleMessageSource source =
        new ResourceBundleMessageSource();
source.setBasename("i18n/messages");
source.setUseCodeAsDefaultMessage(true);
```

Đây là fragment, cần imports. Với mã `unknown.code` không tồn tại và overload strict, implementation có thể trả chính `"unknown.code"` thay vì ném `NoSuchMessageException`.

| Cấu hình | Khi mã không có | Ưu điểm | Rủi ro |
| --- | --- | --- | --- |
| `useCodeAsDefaultMessage=false` (mặc định) | Strict overload ném exception | Phát hiện catalog thiếu | Cần handling/coverage |
| `useCodeAsDefaultMessage=true` | Có thể trả code string | Tiện debug key bị thiếu | Lộ mã nội bộ, che thiếu dịch |
| Explicit default string | Trả default mà caller chọn | Tự kiểm soát UX | Có thể không đúng locale |
| `MessageSourceResolvable` nhiều codes | Thử code tổng quát sau code cụ thể | Giảm key duplication và vẫn localize | Cần thống nhất thứ tự codes |

**Không khuyến khích bật `useCodeAsDefaultMessage(true)` rộng rãi ở production.** Nó có thể khiến giao diện hiển thị `validation.user.email.required` hoặc một key nội bộ khác. Một hệ thống có nhiều `MessageSource` cha/con còn có nguy cơ **mã đầu tiên bị trả như default ở nguồn cha**, khiến mã cụ thể tiếp theo không được thử theo dự kiến. Javadoc khuyến cáo chỉ coi cơ chế này là tiện ích development/debug.

Nếu mục đích là hiển thị fallback cho khách, đặt default string an toàn ngay tại call site hoặc dùng chuỗi codes có thông điệp fallback thực. Nếu mục đích là phát hiện missing keys, để strict mode và kiểm thử catalog.

### Không coi một string bằng code là đủ bằng chứng

Một bản dịch hợp lệ có thể cố ý bằng chính code text. Và khi `useCodeAsDefaultMessage=true`, không có exception để phân biệt. Vì vậy logic `if (message.equals(code))` không phải một cách đáng tin để đo missing key. Với yêu cầu audit coverage, dùng test/trình kiểm catalog thay vì so chuỗi kết quả.

## 7. MessageSourceResolvable: thử nhiều code từ cụ thể đến tổng quát

`MessageSourceResolvable` đại diện một lỗi/thông điệp có:
- Danh sách `String[] codes` theo thứ tự ưu tiên;
- `Object[] arguments` dùng cho formatting;
- Một `defaultMessage` nếu không code nào match.

Implementation thường dùng là `org.springframework.context.support.DefaultMessageSourceResolvable`.

```java
MessageSourceResolvable resolvable =
        new DefaultMessageSourceResolvable(
                new String[] {
                    "validation.user.email",
                    "validation.generic"
                },
                new Object[] {"email"},
                "Giá trị chưa hợp lệ: {0}");

String text = messageSource.getMessage(resolvable, locale);
```

Đây là fragment cần imports, `messageSource` và `locale`. Spring thử codes theo thứ tự:
1. `validation.user.email`.
2. Nếu thiếu, `validation.generic`.
3. Nếu cả hai thiếu, dùng `"Giá trị chưa hợp lệ: {0}"` (được format với args) nếu implementation hỗ trợ như chuẩn.
4. Nếu không có default và không code nào tồn tại, có thể ném `NoSuchMessageException`.

**Ví dụ giá trị:** nếu bundle tiếng Việt có:

```properties
validation.generic=Trường chưa hợp lệ: {0}
```

thì dù code `validation.user.email` thiếu, kết quả vẫn là “Trường chưa hợp lệ: email”. Đây là **fallback theo code** và vẫn localize theo tiếng Việt; nó không phải default message Việt viết cứng trong Java.

### Thứ tự mã rất quan trọng

```text
validation.user.email.required
validation.email.required
validation.required
validation.generic
```

Các codes trong `MessageSourceResolvable` nên theo thứ tự từ **chuyên biệt đến tổng quát**. Nếu để `validation.generic` đầu, nó có thể match sớm và không thử thông báo chuyên biệt dù có bản dịch.

### Hệ sinh thái Spring Validation

`FieldError`/`ObjectError` trong Spring Validation triển khai `MessageSourceResolvable`, nên framework có thể tạo chuỗi codes theo quy tắc của `MessageCodesResolver` (ví dụ lỗi cho field cụ thể rồi lỗi chung). Không nhất thiết hardcode mỗi chuỗi fallback trong UI layer; hãy hiểu hợp đồng của `MessageSourceResolvable` và thứ tự codes mà resolver cung cấp.

**Chú ý bảo mật:** arguments có thể chứa giá trị người dùng nhập. Đừng tự động đưa password, API token, số tài khoản hoặc nội dung nhạy cảm làm `{0}` trong validation messages; danh sách codes và default không tự sanitize dữ liệu.

## 8. MessageFormat: default cũng có quy tắc định dạng

`MessageSource` sử dụng `java.text.MessageFormat` cho patterns có placeholders. Ví dụ:

```properties
cart.count=Bạn có {0,number,integer} sản phẩm.
welcome=Xin chào, {0}!
```

Cách dùng:

```java
String text = messageSource.getMessage(
        "cart.count",
        new Object[] { 3 },
        locale);
```

Với locale tiếng Việt và pattern trên, kết quả chứa số nguyên 3. Cách biểu diễn dấu phân tách hàng nghìn, ngày tháng và số thập phân có thể thay đổi theo `Locale`; đừng assert formatting phức tạp theo JVM/CLDR mà không kiểm soát environment.

### Dấu nháy đơn là ký tự đặc biệt trong MessageFormat

Trong `MessageFormat`, dấu `'` điều khiển quoting pattern và dấu `''` biểu thị một dấu apostrophe thực theo quy tắc format. Ví dụ thông điệp tiếng Anh:

```properties
message.welcome=You''re welcome, {0}!
```

Nếu bị MessageFormat parse với arguments, kết quả mong đợi là `You're welcome, An!`. Nhưng khi **không có arguments**, Spring có thể tối ưu và trả raw string tùy `alwaysUseMessageFormat`; do đó việc dùng apostrophe trong bundle cần chính sách nhất quán.

Tùy chọn:

```java
source.setAlwaysUseMessageFormat(true);
```

buộc áp dụng MessageFormat rules cả với messages không arguments, giúp rule escaping nhất quán, đổi lại chi phí parse/format và yêu cầu quản lý các dấu nháy trong mọi text. Không bật vô điều kiện nếu bundle chứa nhiều apostrophes chưa được escape theo MessageFormat syntax.

### Không nhầm placeholder của hai hệ

- `{0}`: vị trí argument của MessageFormat.
- `${config.key}`: property placeholder Spring để lấy cấu hình.
- `#{expression}`: SpEL expression.

Trong file `messages.properties`, pattern `Xin chào, {0}!` là cú pháp MessageFormat, không phải placeholder lấy từ `Environment`. Không tự động kỳ vọng `MessageSource` lấy các khóa Spring properties bên trong bản dịch giống cách `@Value` hoạt động.

### Default text có thể được format

```java
String text = source.getMessage(
        "message.unavailable",
        new Object[] {"An"},
        "Xin chào, {0}!",
        locale);
```

Với `AbstractMessageSource` thông thường, fallback string được render qua `MessageSourceSupport.renderDefaultMessage`; result là “Xin chào, An!”. Như vậy default không đơn thuần luôn là một string raw chưa xử lý arguments.

## 9. Ví dụ runnable Java 17+: bundle fallback, default, null, exception, code list

Demo dùng **Java 17+, Maven 3.9+, Spring Framework 6.2.18** với `spring-context`. Không cần Spring Boot, database, network hoặc servlet. Nội dung được chuẩn bị để chạy với locale explicit và không phụ thuộc locale của OS bằng `setFallbackToSystemLocale(false)`.

Trong demo:
- `ResourceBundleMessageSource` là bean **tên chính xác `messageSource`**; Spring `ApplicationContext` tự dùng bean có tên này cho `context.getMessage(...)`.
- `GreetingFacade` nhận `MessageSource` bằng constructor injection.
- `messages.properties` là bundle gốc (English).
- `messages_vi.properties` có bản dịch tiếng Việt và cố tình **không** khai `app.brand`.
- Thử explicit default, default có arguments, `null` default, strict exception.
- Thử `MessageSourceResolvable` với hai codes và một default text.
- Cuối cùng demo dùng `StaticMessageSource` độc lập để cho thấy `useCodeAsDefaultMessage=true` trả code.

### 9.1. Cấu trúc project

```text
spring-messages-default/
├── pom.xml
└── src/main/
    ├── java/edu/example/messages/
    │   ├── AppConfig.java
    │   ├── GreetingFacade.java
    │   └── Main.java
    └── resources/i18n/
        ├── messages.properties
        └── messages_vi.properties
```

**pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>spring-messages-default</artifactId>
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
                    <mainClass>edu.example.messages.Main</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**src/main/resources/i18n/messages.properties**

```properties
welcome=Hello, {0}!
farewell=Goodbye!
app.brand=Spring Workshop
validation.generic=Invalid field: {0}
```

**src/main/resources/i18n/messages_vi.properties**

```properties
welcome=Xin chào, {0}!
farewell=Tạm biệt!
validation.generic=Trường chưa hợp lệ: {0}
```

Không có `app.brand` trong bản tiếng Việt là **chủ đích** để chứng minh fallback sang bundle gốc. Không có `missing.key`, `greeting.visitor`, `validation.user.email` hay `validation.unknown` trong cả hai file, để các nhánh missing có kết quả rõ ràng.

**src/main/java/edu/example/messages/AppConfig.java**

```java
package edu.example.messages;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

@Configuration(proxyBeanMethods = false)
public class AppConfig {

    @Bean("messageSource")
    public static MessageSource messageSource() {
        ResourceBundleMessageSource source =
                new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    @Bean
    public GreetingFacade greetingFacade(MessageSource messageSource) {
        return new GreetingFacade(messageSource);
    }
}
```

`setBasename("i18n/messages")` **không có extension `.properties`** và không có tiền tố `classpath:` trong API của `ResourceBundleMessageSource`. Base name phản ánh vị trí trong classpath. Nếu dùng `ReloadableResourceBundleMessageSource`, basename conventions có thể khác; xem mục 14.

**src/main/java/edu/example/messages/GreetingFacade.java**

```java
package edu.example.messages;

import java.util.Locale;
import org.springframework.context.MessageSource;

public final class GreetingFacade {
    private final MessageSource messages;

    public GreetingFacade(MessageSource messages) {
        this.messages = messages;
    }

    public String greet(String name, Locale locale) {
        return messages.getMessage(
                "welcome", new Object[] {name}, locale);
    }

    public String farewell(Locale locale) {
        return messages.getMessage("farewell", null, locale);
    }
}
```

`GreetingFacade` nhận dependency qua constructor, không tự đọc bundle. Nó phù hợp để unit test bằng `StaticMessageSource` hoặc mock MessageSource, cũng tránh hardcode locale hệ thống bên trong service.

**src/main/java/edu/example/messages/Main.java**

```java
package edu.example.messages;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.context.support.StaticMessageSource;

public final class Main {
    public static void main(String[] args) {
        Locale vi = Locale.forLanguageTag("vi-VN");
        Locale en = Locale.US;

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            GreetingFacade greeting =
                    context.getBean(GreetingFacade.class);
            MessageSource source = context.getBean(MessageSource.class);

            System.out.println("vi welcome: " + greeting.greet("An", vi));
            System.out.println("en welcome: " + greeting.greet("An", en));
            System.out.println("vi farewell: " + greeting.farewell(vi));

            System.out.println("vi brand fallback: "
                    + context.getMessage("app.brand", null, vi));

            System.out.println("missing explicit: " + source.getMessage(
                    "missing.key", null, "Không tìm thấy nội dung", vi));

            System.out.println("missing formatted: " + source.getMessage(
                    "greeting.visitor", new Object[] {"Mai"},
                    "Xin chào khách {0}", vi));

            String nullable = source.getMessage(
                    "missing.key", null, null, vi);
            System.out.println("missing null: " + (nullable == null));

            try {
                source.getMessage("missing.key", null, vi);
                System.out.println("missing strict: not thrown");
            } catch (NoSuchMessageException exception) {
                System.out.println("missing strict: NoSuchMessageException");
            }

            MessageSourceResolvable severalCodes =
                    new DefaultMessageSourceResolvable(
                            new String[] {
                                "validation.user.email", "validation.generic"
                            },
                            new Object[] {"email"},
                            "Giá trị mặc định: {0}");
            System.out.println("codes fallback: "
                    + source.getMessage(severalCodes, vi));

            MessageSourceResolvable onlyUnknown =
                    new DefaultMessageSourceResolvable(
                            new String[] {"validation.unknown"},
                            new Object[] {"email"},
                            "Lỗi dữ liệu: {0}");
            System.out.println("resolvable default: "
                    + source.getMessage(onlyUnknown, vi));

            StaticMessageSource debug = new StaticMessageSource();
            debug.setUseCodeAsDefaultMessage(true);
            System.out.println("debug as code: "
                    + debug.getMessage("missing.key", null, vi));
        }
    }
}
```

### 9.2. Chạy

Trong thư mục `spring-messages-default`:

```bash
mvn -q compile exec:java
```

**Các dòng do `Main` in, theo thứ tự chính xác:**

```text
vi welcome: Xin chào, An!
en welcome: Hello, An!
vi farewell: Tạm biệt!
vi brand fallback: Spring Workshop
missing explicit: Không tìm thấy nội dung
missing formatted: Xin chào khách Mai
missing null: true
missing strict: NoSuchMessageException
codes fallback: Trường chưa hợp lệ: email
resolvable default: Lỗi dữ liệu: email
debug as code: missing.key
```

Maven/Spring có thể có logging riêng, không phải các dòng `System.out.println` trên. `Locale` được chỉ định explicit, system-locale fallback bị tắt, nên kết quả không phụ thuộc locale mặc định của process. Toàn bộ message properties là các ví dụ không nhạy cảm.

### 9.3. Giải thích toàn bộ nhánh fallback

1. `context.refresh()` thấy bean tên `messageSource` và tích hợp nó làm message source của context.
2. `greeting.greet("An", vi)` tra `welcome` trong `messages_vi.properties` và format `{0}` thành “An”.
3. `greeting.greet("An", en)` tra `messages_en` (không tồn tại) rồi dùng bundle gốc `messages.properties`.
4. `farewell` có trong bản tiếng Việt nên trả “Tạm biệt!”.
5. `app.brand` không có trong file vi nhưng có trong file gốc; kết quả thành công **qua locale fallback**, không qua explicit default.
6. `missing.key` không tồn tại, nên overload có default trả “Không tìm thấy nội dung”.
7. `greeting.visitor` không tồn tại, nên default pattern `Xin chào khách {0}` được format thành “Xin chào khách Mai”.
8. Cùng code missing với explicit `defaultMessage = null` trả Java `null` trong cấu hình đã đặt.
9. Strict overload `getMessage("missing.key", null, vi)` ném `NoSuchMessageException`; catch chỉ in class name an toàn.
10. `DefaultMessageSourceResolvable` thử `validation.user.email` rồi `validation.generic`. Mã thứ hai tồn tại trong file vi, nên trả “Trường chưa hợp lệ: email”.
11. Resolvable chỉ có mã không tồn tại sử dụng `defaultMessage` của chính nó, format argument thành “Lỗi dữ liệu: email”.
12. Một `StaticMessageSource` riêng bật `useCodeAsDefaultMessage` trả `missing.key`, minh họa behavior debug mà không đổi chính sách production messageSource.

### 9.4. Tại sao dùng hai implementations?

`ResourceBundleMessageSource` đọc bundle file thật từ classpath; `StaticMessageSource` lưu message patterns in-memory và phù hợp test/chứng minh policy. Cả hai thuộc Spring, không liên quan đến một API bên ngoài. Demo dùng `StaticMessageSource` chỉ để minh họa fallback trả code, không làm nhiễm policy của context.

### 9.5. Khi đóng gói thành JAR

Các file `i18n/messages.properties` và `i18n/messages_vi.properties` nằm dưới `src/main/resources`, được Maven đóng gói vào classpath artifact. `ResourceBundleMessageSource` dùng class loader/resource-bundle mechanism để đọc được cả resource trong JAR. **Không dùng `getFile()`** để ép các bundle entry thành filesystem file. Kiểm packaged distribution bằng smoke test nếu ứng dụng chạy từ JAR hoặc Spring Boot executable JAR.

## 10. Nhiều chiến lược fallback: chọn theo loại thông điệp

Không phải thông điệp nào cũng cần cùng một chính sách:

| Loại thông điệp | Chiến lược khuyến nghị | Lý do |
| --- | --- | --- |
| Label UI ít rủi ro | Explicit default có chủ đích | Không làm trang trắng vì thiếu một label |
| Validation field | `MessageSourceResolvable` nhiều codes + default thân thiện | Cụ thể trước, tổng quát sau |
| Lỗi giao dịch bắt buộc | Strict lookup + kiểm thử catalog | Thiếu code là lỗi cấu hình cần được phát hiện |
| Thông điệp pháp lý | Catalog đã được review đầy đủ theo locale | Không cho fallback tùy tiện làm đổi nghĩa |
| Diagnostic cho developer | Tạm dùng code-as-default hoặc tooling | Dễ nhận diện key thiếu |
| Public error page | Default chung, không lộ technical code | Giữ UX và bảo mật |
| Email gửi khách hàng | Có locale fallback policy và kiểm coverage | Tránh nội dung trộn ngôn ngữ không kiểm soát |
| Log/audit event | Stable event code/structured fields, message dịch riêng | Không đổi khả năng tương quan logs theo locale |

Một chính sách phổ biến:
- Core error codes luôn có trong base bundle.
- Locale-specific bundles nên đạt tỷ lệ coverage đã định.
- Từng call site biết được phép fallback hay phải fail.
- CI so key sets giữa bundle gốc và các locale được hỗ trợ.
- Tránh bật `useCodeAsDefaultMessage` như “vá mọi missing key”.

### Default message và UX

Nếu locale mong muốn là tiếng Nhật nhưng explicit default lại là tiếng Việt, hệ thống vẫn trả tiếng Việt. Điều đó có thể chấp nhận cho một fallback cực hiếm và có chủ đích, nhưng về lâu dài nên dùng một code tổng quát đã được dịch hoặc base bundle ngôn ngữ fallback được sản phẩm quyết định.

### Default message và accessibility

Thông điệp fallback cần có nội dung rõ, không chỉ “Error”. Với validation, chỉ ra loại lỗi/field ở mức không tiết lộ dữ liệu nhạy cảm; accessibility labels và screen reader announcements không nên chỉ hiển thị technical key. Có test UI/ARIA behavior nếu ứng dụng có giao diện.

## 11. Phân biệt missing key, empty value và malformed message

Ba trạng thái khác nhau:

```properties
# Không có khóa: missing.key hoàn toàn không xuất hiện.
caption.empty=
caption.normal=Nội dung hợp lệ
caption.pattern=Hello, {0}!
```

- `missing.key`: lookup không tìm được ở các bundles/parent sources, dùng fallback policy.
- `caption.empty`: key được định nghĩa nhưng value rỗng, lookup có thể trả `""` — **không phải missing**.
- `caption.normal`: key có nội dung thông thường.
- `caption.pattern`: nếu arguments không phù hợp hoặc cú pháp MessageFormat sai, có thể phát sinh formatting issue **dù key tồn tại**.

Nếu ứng dụng không muốn empty messages, cần validate bundle values trong CI hoặc lúc startup thay vì âm thầm coi `""` như missing. Quy tắc “blank value có được phép?” là policy sản phẩm, không được giao cho bản thân MessageSource quyết định.

### MessageFormat syntax errors

Một pattern như `Bad {0` có thể không parse được khi format. Đó **không phải** `NoSuchMessageException` do missing key. Tránh catch-all rồi trả code, vì nó che việc bản dịch đã bị viết sai. Dùng test format tất cả keys có parameters và quy định escaping apostrophe nhất quán.

### Trùng mã giữa nhiều bundles

Nếu `ResourceBundleMessageSource` có nhiều basenames, thứ tự khai báo, locale specificity và resolution precedence có thể khiến một code được lấy từ bundle khác dự kiến. Kiểm thứ tự basenames, không chỉ nội dung một file. Một default string của caller chỉ được xét khi **tất cả nguồn theo resolution policy đều không giải được code**.

## 12. Parent ApplicationContext và HierarchicalMessageSource

Trong Spring có thể có parent/child ApplicationContexts, như root context với một child context cho web module. `MessageSource` có thể được tổ chức theo quan hệ cha/con, ví dụ child ưu tiên message catalog của module, parent cung cấp messages chung.

```text
Child ApplicationContext
  messageSource (module bundle)
        |
        +-- thử code tại child
        |
        +-- nếu không thấy, thử parent MessageSource
                 |
                 +-- common bundle
                 |
                 +-- nếu không có, xử lý default/exception
```

Đây là sơ đồ khái niệm; thứ tự và việc ghép nguồn phụ thuộc Spring configuration. Khi dùng `AbstractMessageSource` với parent, đặc biệt **không bật `useCodeAsDefaultMessage(true)` bừa bãi trên parent**, vì trong một số trường hợp multi-code resolvable, parent trả luôn code đầu tiên và không còn thử codes tiếp.

### Ví dụ thiết kế

Một bộ mã:
- Child: `order.status.paid`, `order.status.pending`.
- Parent: `action.ok`, `action.cancel`, `validation.generic`.
- Nếu child thiếu một code chung, parent cung cấp bản dịch.
- Nếu cả child/parent thiếu, dùng chính sách default của call site.

Không dùng parent message source như một cách “tắt kiểm tra coverage”: những mã quan trọng vẫn nên có test theo nhóm locale/module.

### Không bắt buộc có parent context

Trong một Spring application nhỏ, chỉ một `ResourceBundleMessageSource` đủ cho bài học. Đừng tự thêm hierarchy phức tạp khi không có yêu cầu module hóa/tenant hóa.

## 13. Locale và người gọi: không dựa tùy tiện vào Locale.getDefault()

Overload `MessageSource.getMessage(...)` nhận `Locale` tường minh. Spring web infrastructure có thể quản lý locale cho request qua các abstraction như `LocaleResolver` hoặc `LocaleContextHolder`, nhưng **ứng dụng Spring Core thuần không tự có một HTTP request locale**.

Ví dụ constructor injection:

```java
public final class NotificationText {
    private final MessageSource messages;

    public NotificationText(MessageSource messages) {
        this.messages = messages;
    }

    public String title(Locale recipientLocale) {
        return messages.getMessage(
                "notification.title",
                null,
                "Thông báo",
                recipientLocale);
    }
}
```

Fragment class chỉ cần imports `MessageSource` và `Locale`; không tự chạy nếu chưa được đăng ký. Lấy locale từ recipient preference hoặc message/job context **đã được xác thực** thay vì từ system default của worker.

### LocaleContextHolder và MessageSourceAccessor

`LocaleContextHolder` là holder gắn locale với thread/context theo Spring. `MessageSourceAccessor` là helper có API thuận tiện để lấy message theo locale mặc định/explicit; ứng dụng web có thể dùng nó với locale đang bound.

```java
MessageSourceAccessor accessor =
        new MessageSourceAccessor(messageSource);
String label = accessor.getMessage("action.save", "Save", locale);
```

Đây là fragment minh họa helper API, cần imports `MessageSourceAccessor`, `MessageSource`, `Locale` và biến đã khai báo. Khi dùng overload helper, đọc đúng signature của Spring version (một số overload có args/default theo thứ tự khác), không nhầm `getMessage(String code, String defaultMessage, Locale locale)` với overload của interface `MessageSource`.

**Cẩn trọng ở async/background threads:** locale của HTTP request không tự lan qua mọi executor thread nếu không có cơ chế context propagation. Ở worker gửi email, hãy truyền locale của recipient explicit.

### Không tin Accept-Language vô điều kiện

Một locale từ HTTP header do client kiểm soát có thể không thuộc tập supported locales. Cần normalize/chọn fallback từ danh sách hỗ trợ, tránh để mọi giá trị lạ tạo hành vi bất ngờ. MessageSource không phải authorization engine; locale không được dùng để xác định quyền hay loại dữ liệu người dùng được xem.

## 14. ResourceBundleMessageSource, ReloadableResourceBundleMessageSource và StaticMessageSource

Ba implementation dùng chung hợp đồng `MessageSource` nhưng nhận dữ liệu theo cách khác:

| Implementation | Nguồn và lựa chọn | Ranh giới |
| --- | --- | --- |
| `ResourceBundleMessageSource` | JDK bundles trên classpath/JAR | Không mặc định reload tệp đóng gói |
| `ReloadableResourceBundleMessageSource` | Spring `Resource`, đặc biệt external `file:` | TTL không phải cam kết refresh tức thì hoặc đồng bộ nhiều node |
| `StaticMessageSource` | Map thông điệp lập trình bằng `addMessage` | Phù hợp fixtures và kiểm thử, không tự load properties |

Khi dùng `ApplicationContext.getMessage(...)`, bean phù hợp cần tên **`messageSource`** trong context hiện tại; nếu không có, Spring có thể delegate tới parent hoặc tạo empty source. Spring Boot có auto-configuration riêng với `spring.messages.*` tùy điều kiện phiên bản, khác plain Spring Core.

**Trong bài này, điểm quyết định là chính sách *missing code***: default/null, strict `NoSuchMessageException`, nhiều codes của `MessageSourceResolvable` và `useCodeAsDefaultMessage`, bất kể source dùng implementation nào. Bài 259 dành riêng cho cache/encoding/implementation.

## 15. Quy trình kiểm tra catalog và missing keys trong CI

Để tránh mất bản dịch ngoài ý muốn, đừng chỉ dựa vào việc người dùng phát hiện trên giao diện. Nên có các bước:

1. Xác định **base bundle** làm nguồn chuẩn cho các keys bắt buộc.
2. Thu thập key sets cho `messages.properties` và từng locale file.
3. Kiểm tra code trùng, key bị xóa và key mới chưa được dịch theo policy.
4. Phân biệt **missing key** với value rỗng.
5. Kiểm tra MessageFormat placeholders, braces và apostrophe escaping.
6. Kiểm tra arg indexes (`{0}`, `{1}`) nhất quán giữa translations.
7. Kiểm thử fallback locale: vi-VN, en-US, một locale không có bundle và system locale bất lợi.
8. Kiểm thử strict overload phải ném exception khi intentionally missing.
9. Kiểm thử explicit default trả đúng default, kể cả `{0}` formatting.
10. Kiểm thử resolvable nhiều codes đúng thứ tự.
11. Nếu bật code-as-default ở dev, bảo đảm cấu hình production tắt.
12. Không đưa những message chứa PII/secret sang CI logs khi thử argument formatting.

### Không yêu cầu mọi locale file phải có tất cả key trong mọi sản phẩm

Nếu chính sách dự án cho phép fallback sang bundle gốc, một locale thiếu vài keys không nhất thiết fail build. Tuy nhiên nên báo **coverage metric** và fail với nhóm key quan trọng. Đừng âm thầm biến missing translation thành behavior không kiểm soát.

### Test key coverage từ code có giới hạn

Quét source để tìm mọi literal `getMessage("...")` chỉ bắt được key tĩnh; mã được xây động, `MessageSourceResolvable`, validation codes từ framework hoặc messages của thư viện có thể bị bỏ sót. Bổ sung integration test catalog và danh sách key bắt buộc theo feature, không xem regex scan source là công cụ đầy đủ.

## 16. Quan sát production: log/metric an toàn khi không tìm thấy code

Một số hệ thống chọn fail-safe ở UI nhưng vẫn cần biết catalog đang thiếu gì. Thiết kế quan sát:

```text
i18n_missing_code component=checkout locale=vi-VN keyCategory=validation
```

Đây là log minh họa. Không mặc nhiên log raw code/arguments nếu chúng có thể chứa identifier hoặc input nhạy cảm; ưu tiên keyCategory, component và normalized locale đã duyệt. Nếu code là literal được đội phát triển kiểm soát, có thể log key theo chính sách bảo mật, nhưng vẫn giới hạn cardinality metrics.

### Lỗi phổ biến khi đo metric

- Dùng **message đã format** làm metric label: cardinality bùng nổ theo username/orderId.
- Log `Object[] args` từ validation: có thể lộ password/token/PII.
- Gộp missing code với lỗi MessageFormat syntax: chẩn đoán sai.
- Chỉ ghi warning mà không có test coverage: production phát hiện muộn.
- Dùng `useCodeAsDefaultMessage` rồi không có cơ chế đếm missing: thiếu bản dịch bị che.
- Log exception stack trace trong từng request thiếu key: gây log flood.
- Không normalize locale: có thể tạo hàng nghìn series metrics do input lạ.

### Lỗi đa ngôn ngữ trong exception messages

`NoSuchMessageException` thường có message chứa code và locale; không trả nguyên exception text ra response công cộng. UI error boundary có thể render một default an toàn, còn logs/metrics nội bộ ghi event và category được kiểm soát.

## 17. Lỗi thường gặp và cách sửa

| Triệu chứng | Nguyên nhân phổ biến | Giải pháp |
| --- | --- | --- |
| `NoSuchMessageException` với code hợp lệ | Sai bundle basename, thiếu file/locale, key bị xóa | Kiểm classpath/basename và fallback chain |
| Bản vi bị tiếng Anh dù file vi tồn tại | Khóa thiếu ở file vi, base fallback hoạt động | Bổ sung bản dịch hoặc chấp nhận fallback có policy |
| Code missing trả Java `null` | Gọi overload có default `null` | Xử lý nullable, hoặc cung cấp default rõ |
| Code missing ném exception | Gọi strict overload | Dùng explicit default/Resolvable khi phù hợp |
| UI hiện `validation.email.required` | Bật `useCodeAsDefaultMessage` hoặc literal tương tự | Tắt ở prod, có fallback thân thiện |
| Multi-code resolvable dừng tại code đầu | Parent dùng code-as-default hoặc thứ tự codes sai | Sửa parent policy, ưu tiên mã cụ thể |
| Default tiếng Việt hiện với locale en | Fallback được viết cố định bằng tiếng Việt | Dùng mã fallback đa ngôn ngữ hoặc default locale policy |
| `{0}` vẫn xuất hiện trong nội dung | Args thiếu hoặc pattern không được format như dự kiến | Kiểm Object[] args và MessageFormat |
| Apostrophe biến mất | MessageFormat quote syntax | Escape `''`, policy `alwaysUseMessageFormat` |
| Key có giá trị rỗng nhưng không dùng default | Empty string là value được tìm thấy | Validate bundle completeness |
| `messages_vi_VN.properties` không được tìm | Sai locale suffix hoặc packaging | Kiểm path/candidates/resource trong JAR |
| `spring.messages.basename` không có tác dụng | Ứng dụng Spring Core, không phải Boot | Cấu hình `@Bean messageSource` tường minh |
| `context.getMessage` không dùng source tự khai báo | Bean không tên `messageSource` | Đặt đúng bean name |
| Bundle cập nhật nhưng process không thấy | Cache/packaged JAR immutable | Redeploy/restart hoặc reloadable file strategy |
| Reloadable source không refresh JAR file | Archive/resource metadata/caching | External filesystem resource hoặc redeploy |
| Tests chỉ pass trên máy dev | Dựa system locale | `setFallbackToSystemLocale(false)`, explicit Locale |
| Log lộ email/password khi format lỗi | Truyền args nhạy cảm vào message/log | Dùng metadata an toàn, không log credentials |
| Error fallback thành technical key | Return code-as-default cho user | Tách debug mode khỏi public UX |
| Default chung che lỗi pháp lý quan trọng | Dùng default quá rộng | Strict/coverage bắt buộc cho key trọng yếu |
| Cache locale bị lẫn | Cache key bỏ qua Locale | Dùng key chứa locale/version hoặc không cache thủ công |
| Controller trả thẳng stack trace | Không có error boundary an toàn | Trả generic localized error, log metadata nội bộ |
| Map keys trùng nhau | Build merge/overlay ngoài ý muốn | Kiểm duplicate/precedence trong bundle toolchain |

## 18. Bài tập thực hành

**Bài 1 — explicit fallback:** Thêm call `getMessage("cart.empty", null, "Giỏ hàng trống", vi)` nhưng không thêm key vào bundle. Quan sát default. Sau đó thêm key dịch và xác nhận bản dịch thắng.

**Bài 2 — strict missing:** Dùng overload ba tham số `(code,args,locale)` với một code không có; assert `NoSuchMessageException`, không catch `Exception` chung.

**Bài 3 — null vs empty:** Thêm `caption.empty=` vào bundle. So sánh kết quả lookup với `getMessage("missing.key",null,null,vi)` bằng `== null` và `isEmpty()`.

**Bài 4 — fallback locale:** Xóa `farewell` khỏi file vi, để trong base. Gọi `farewell` với vi-VN và en-US, kiểm khác biệt trước/sau.

**Bài 5 — default có tham số:** Dùng default `"Yêu cầu {0} chưa có bản dịch"` với arg `"REQ-1"`, xác nhận MessageFormat substitution.

**Bài 6 — resolvable chain:** Tạo ba codes với một mã đầu không có và mã thứ hai có. Thay thứ tự codes và giải thích vì sao kết quả đổi.

**Bài 7 — debug code-as-default:** Tạo `StaticMessageSource`, bật `setUseCodeAsDefaultMessage(true)`, quan sát missing key trả code. Viết test bảo đảm cấu hình production không bật.

**Bài 8 — escaping:** Thêm pattern tiếng Anh có apostrophe, kiểm trước/sau khi `setAlwaysUseMessageFormat(true)` và escaping `''`. Test cả trường hợp có arguments và không có.

**Bài 9 — system locale:** Thử bỏ `setFallbackToSystemLocale(false)`, chạy với các JVM default locales khác nhau, rồi đánh giá vì sao kết quả có thể khó dự đoán.

**Bài 10 — CI:** Viết một tool nhỏ so key sets các bundle, báo key dịch thiếu và invalid MessageFormat patterns, loại trừ danh sách key optional có tài liệu.

## 19. Câu hỏi tự đánh giá kèm đáp án

1. Ba overload của `MessageSource.getMessage` khác nhau về missing code thế nào?
2. Nếu file vi thiếu một key nhưng base bundle có, có dùng explicit default không?
3. `messages.properties` thường đóng vai trò gì?
4. `setFallbackToSystemLocale(false)` có ngăn fallback sang base bundle không?
5. `getMessage(code,args,default,locale)` làm gì khi code đã có?
6. Nếu default là null và code không có, trả gì với configuration mặc định?
7. `getMessage(code,args,locale)` thường ném exception loại nào nếu thiếu?
8. `useCodeAsDefaultMessage` có mặc định bật không?
9. Vì sao không nên bật code-as-default rộng ở prod?
10. `MessageSourceResolvable` có thể cung cấp những metadata nào?
11. Spring thử nhiều codes theo thứ tự nào?
12. Nếu mã thứ hai có và mã đầu thiếu, defaultMessage có được dùng không?
13. Nếu mọi mã thiếu nhưng resolvable có default thì sao?
14. Khi parent MessageSource dùng code-as-default, rủi ro với multi-code resolvable là gì?
15. Pattern `{0}` thuộc SpEL hay MessageFormat?
16. `{0,number,integer}` có thể phụ thuộc Locale thế nào?
17. Vì sao apostrophe cần chú ý trong MessageFormat?
18. `caption.empty=` có phải key missing không?
19. Khi có `@Bean("messageSource")`, ApplicationContext có thể dùng nó làm gì?
20. `spring.messages.basename` là quy ước Spring Core hay Boot?
21. Trong ví dụ, `vi brand fallback` từ đâu?
22. Trong ví dụ, kết quả `codes fallback` là gì và tại sao?
23. Trong ví dụ, `debug as code` in gì?
24. Có nên log args chứa mật khẩu nếu lookup lỗi không?
25. Một bản dịch thiếu trong các locale bắt buộc nên được phát hiện ở đâu?
26. Nếu chỉ quét source tìm literal `getMessage("x")`, có thể bỏ sót mã nào?
27. Có nên tự động hiện raw exception text cho khách hàng?
28. Nếu message trong bundle có dấu nháy chưa escape, lỗi thuộc missing key hay format?
29. Thay đổi bundle đóng gói trong JAR có tự đổi string đã tạo không?
30. Vì sao Java service gửi email cần truyền locale người nhận explicit?

**Đáp án ngắn:** (1) Có default → fallback/nullable; strict → exception; resolvable → nhiều codes/default. (2) Không, base giải được. (3) Bundle gốc. (4) Không. (5) Dùng bản dịch. (6) `null`. (7) `NoSuchMessageException`. (8) Không. (9) Lộ code, che lỗi thiếu dịch. (10) Codes, arguments, default. (11) Theo thứ tự được cung cấp. (12) Không. (13) Render default. (14) Parent có thể trả mã đầu sớm. (15) MessageFormat. (16) Định dạng số khác locale. (17) Apostrophe là quote char của pattern. (18) Không. (19) Resolve context messages. (20) Boot. (21) Bundle gốc. (22) “Trường chưa hợp lệ: email”, vì mã thứ hai có trong bundle vi. (23) `missing.key`. (24) Không. (25) CI/catalog tests. (26) Codes động/validation/resolvable/framework. (27) Không. (28) Format/quoting. (29) Không nên giả định; cần cache/redeploy policy. (30) System locale không đại diện người nhận.

## 20. Checklist áp dụng trong ứng dụng

- [ ] Dùng `MessageSource` như abstraction, không hardcode text phân tán trong Java.
- [ ] Tên bean `messageSource` đúng khi muốn `ApplicationContext.getMessage` sử dụng.
- [ ] Basename đúng đường classpath, không thêm `.properties` không cần thiết.
- [ ] Bundle gốc có đầy đủ codes nền tảng hoặc có coverage policy.
- [ ] Tắt fallback sang system locale khi muốn kết quả không phụ thuộc máy chủ.
- [ ] Phân biệt locale fallback, code fallback và default/error policy.
- [ ] Chọn explicit default chỉ cho messages được phép fallback.
- [ ] Chọn strict overload cho messages bắt buộc và kiểm thử thiếu mã.
- [ ] Xử lý `null` nếu truyền default null.
- [ ] Kiểm empty value riêng với missing key.
- [ ] Sử dụng `MessageSourceResolvable` cho chuỗi mã cụ thể → tổng quát.
- [ ] Không bật `useCodeAsDefaultMessage` tràn lan ở production.
- [ ] Parent/child MessageSource không làm ngắn mạch các codes hợp lệ.
- [ ] Format arguments theo `MessageFormat`, không theo `String.format`.
- [ ] Escape apostrophe và kiểm `alwaysUseMessageFormat` có chủ đích.
- [ ] Truyền Locale người dùng/người nhận explicit ở background jobs.
- [ ] Không dựa `Locale.getDefault()` cho kết quả server-side bắt buộc.
- [ ] Spring Core và Boot message-source auto-configuration được phân biệt.
- [ ] Có tests locale vi, en, locale không có bundle và missing key.
- [ ] Có test default string có arguments và strict exception.
- [ ] Không đưa token/password/PII vào args/logs hoặc raw exception response.
- [ ] Có metric an toàn và CI coverage cho localization catalog.
- [ ] Kiểm packaged JAR có đúng resource bundles.
- [ ] Có chính sách reload/redeploy và cache khi bản dịch thay đổi.
- [ ] Không coi bản dịch rỗng là missing nếu chưa có rule validation.
- [ ] Không dùng một default tùy tiện thay thông điệp có giá trị pháp lý.
- [ ] Với UI, ưu tiên fallback thân thiện và dễ truy cập; với mã trọng yếu, fail/alert sớm.

## 21. Tài liệu tham khảo chính thức

- Spring Framework — ApplicationContext và MessageSource: https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html
- Spring Framework 6.2 — `MessageSource` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/MessageSource.html
- Spring Framework 6.2 — `NoSuchMessageException` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/NoSuchMessageException.html
- Spring Framework 6.2 — `AbstractMessageSource` Javadoc (`useCodeAsDefaultMessage`, parent messages): https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/AbstractMessageSource.html
- Spring Framework 6.2 — `ResourceBundleMessageSource` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/ResourceBundleMessageSource.html
- Spring Framework 6.2 — `DefaultMessageSourceResolvable` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/DefaultMessageSourceResolvable.html
- Spring Framework 6.2 — `MessageSourceSupport` Javadoc (renderDefaultMessage): https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/MessageSourceSupport.html
- Spring Framework 6.2 — `StaticMessageSource` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/StaticMessageSource.html
- Spring Framework Reference — Internationalization using MessageSource: https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-messagesource
- Oracle Java 17 — `java.text.MessageFormat`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/MessageFormat.html
- Spring Boot Reference — Internationalization/MessageSourceAutoConfiguration: https://docs.spring.io/spring-boot/reference/features/internationalization.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="internationalization-006">Thứ tự tra cứu, kế thừa và bộ nhớ đệm thông điệp</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự tra cứu, kế thừa và bộ nhớ đệm thông điệp trong Spring Framework

## 1. Bài toán: cùng một mã thông điệp có thể được định nghĩa ở nhiều nơi

Một ứng dụng đa ngôn ngữ thường đặt nội dung hiển thị trong những resource bundles như:

~~~text
src/main/resources/
└── i18n/
    ├── messages.properties
    ├── messages_vi.properties
    ├── messages_vi_VN.properties
    ├── secondary.properties
    └── secondary_vi_VN.properties
~~~

Khi gọi:

~~~java
String text = messageSource.getMessage(
    "greeting", new Object[] {"Lan"}, Locale.forLanguageTag("vi-VN"));
~~~

Spring phải quyết định **file nào thắng** nếu cùng mã `greeting` xuất hiện ở nhiều bản dịch; nếu trong context con không thấy mã thì có chuyển sang context cha hay không; và nếu bạn sửa file lúc ứng dụng chạy thì kết quả có thay đổi ngay hay còn nằm trong cache.

Đây là ba cơ chế liên quan nhưng **không giống nhau**:

1. **Thứ tự tra cứu:** mã message được tìm trong bản dịch cụ thể, bản dịch ít cụ thể hơn, file gốc và các basename theo chính sách của implementation.
2. **Kế thừa thông điệp:** `HierarchicalMessageSource` có thể chuyển việc tìm một mã chưa có sang parent `MessageSource`, thường tương ứng parent `ApplicationContext`.
3. **Bộ nhớ đệm:** `ResourceBundleMessageSource` và `ReloadableResourceBundleMessageSource` giữ bundles/properties và `MessageFormat` nhằm tránh đọc/parse file liên tục; các thiết lập cache quyết định khi nào kiểm tra nạp lại.

> Điểm nhớ nhanh: **Locale fallback** nằm trong việc tìm bản dịch của một source; **parent MessageSource fallback** chuyển sang source của context cha; **defaultMessage** chỉ là nội dung trả về khi không giải quyết được mã theo API. Thứ tự basename và cache lại là những trục độc lập.

Bài này tập trung vào **thứ tự tra cứu, kế thừa và bộ nhớ đệm thông điệp** trong Spring Framework Core Container, có chương trình **Java 17 + Spring Context 6.1.13** chạy bằng Maven và kết quả stdout cố định.

## 2. Các khái niệm cần phân biệt

| Khái niệm | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `MessageSource` | API tra mã thông điệp cho một `Locale` | `getMessage("greeting", ...)` |
| `message code` | Khóa định danh logic, không phải câu dịch | `order.created` |
| `Locale` | Ngôn ngữ/khu vực/biến thể mong muốn | `vi`, `vi_VN`, `en_US` |
| `basename` | Tên gốc của nhóm resource bundles, không có hậu tố locale hoặc `.properties` | `i18n/messages` |
| `ResourceBundleMessageSource` | Tải bundles theo `java.util.ResourceBundle` | Classpath bundles |
| `ReloadableResourceBundleMessageSource` | Tải Spring resources, hỗ trợ kiểm tra nạp lại | File ngoài JAR |
| `StaticMessageSource` | Nguồn thông điệp tạo bằng Java code | Test hoặc parent source nhỏ |
| `HierarchicalMessageSource` | Hợp đồng gắn parent message source | Child → parent |
| `MessageSourceResolvable` | Mang một hoặc nhiều mã, arguments, default | Validation error |
| `MessageFormat` | Format nội dung có `{0}`, `{1,number}`… | `"Hello {0}"` |
| `cacheMillis` | Thời gian giữ dữ liệu trước một lần kiểm tra refresh | `-1`, `0`, `5000` |

`MessageSource` là một abstraction của tầng i18n. Nó **không phải** `Environment.PropertySource`: resource bundle message codes không tự được coi là các property keys dùng cho `@Value`.

## 3. Ba overload của `MessageSource.getMessage`

Ba dạng tra cứu cơ bản:

| API | Khi code không được giải quyết |
| --- | --- |
| `getMessage(code, args, defaultMessage, locale)` | Dùng default có thể là `null` |
| `getMessage(code, args, locale)` | Thường ném `NoSuchMessageException` |
| `getMessage(resolvable, locale)` | Thử danh sách candidate codes và default trong resolvable |

Mảng **codes** của `MessageSourceResolvable` là thứ tự thử các **khóa khác nhau**; Locale fallback và basename precedence lại giải quyết **cùng một khóa** qua nguồn/biến thể khác nhau. Bài 261 giải thích overloads/default policies; ở đây cần quan sát nhiều chiều precedence phối hợp với parent context và cache.

## 4. Locale trong tên bundle

Với basename `i18n/messages`, các file phổ biến:

~~~text
i18n/messages.properties
i18n/messages_vi.properties
i18n/messages_vi_VN.properties
i18n/messages_en.properties
i18n/messages_en_US.properties
~~~

Một request `Locale.forLanguageTag("vi-VN")` có thể dẫn tới các ứng viên theo mức cụ thể:

~~~text
messages_vi_VN.properties
messages_vi.properties
messages.properties
~~~

Nếu `greeting` có trong `messages_vi_VN.properties`, nó thường được ưu tiên hơn `messages_vi.properties` và file gốc trong cùng một bundle.

Nếu `greeting` thiếu ở file vi_VN nhưng có trong `messages_vi.properties`, cấp ngôn ngữ `vi` có thể cung cấp giá trị. Nếu cả hai thiếu, file gốc là một nguồn fallback.

**Lưu ý:** Chuỗi candidate locales và việc fallback sang system/default Locale còn bị ảnh hưởng bởi implementation, `ResourceBundle` control và cấu hình `fallbackToSystemLocale`/`defaultLocale`. Trình tự ba cấp trên là mô hình thông dụng cho một bundle đơn giản với system-locale fallback bị tắt, không phải cam kết duy nhất cho mọi tổ hợp language/script/country/variant.

## 5. Locale fallback khác parent MessageSource

Xét hai tình huống:

**Fallback theo Locale trong cùng source:**

~~~text
Child MessageSource, basename i18n/messages
  messages_vi_VN.properties: greeting=Chao VN
  messages_vi.properties:    greeting=Chao
  messages.properties:       greeting=Hello
~~~

Khi tra `greeting` với `vi_VN`, kết quả là `Chao VN`. Không cần parent context.

**Fallback sang parent MessageSource:**

~~~text
Child MessageSource:
  Không có code "notice.parent" trong mọi bundle phù hợp

Parent MessageSource:
  code "notice.parent" -> "PARENT-ONLY"
~~~

Khi child không giải quyết được mã `notice.parent`, `HierarchicalMessageSource` có thể nhờ parent. Đây là kế thừa thông điệp **giữa hai source/context**.

Sự khác nhau rất quan trọng: **thiếu message trong một locale-specific file chưa đồng nghĩa phải hỏi parent ngay**. Implementation của child vẫn có thể tìm được mã ở locale chung hơn, root bundle hoặc basename khác.

## 6. Thứ tự nhiều basename

Spring hỗ trợ:

~~~java
ResourceBundleMessageSource source =
    new ResourceBundleMessageSource();

source.setBasenames(
    "i18n/messages",
    "i18n/secondary"
);
~~~

Các basenames được tra **theo thứ tự khai báo**. Với mã bị trùng, giá trị trong một bundle đứng trước thường **lấn** giá trị của bundle đứng sau.

Giả sử:

~~~properties
# i18n/messages.properties
priority=PRIMARY-ROOT
~~~

và:

~~~properties
# i18n/secondary_vi_VN.properties
priority=SECONDARY-VN
~~~

Trong `ResourceBundleMessageSource`, khi basename đầu có mã `priority` nhờ bundle gốc, nó có thể trả `PRIMARY-ROOT` mà **không chuyển sang basename sau**, ngay cả khi basename sau có biến thể `vi_VN`.

Điều này dễ bị hiểu nhầm là “file có locale cụ thể nhất trên **toàn bộ** các basenames luôn thắng”. Không đúng trong tình huống nhiều basenames: **thứ tự basename cũng là một lớp precedence**. **Lưu ý:** Không suy ngoại lệ ở implementation khác thành quy tắc tuyệt đối cho mọi loader.

### `addBasenames` và `setBasenames`

- `setBasenames(...)` thay thế danh sách basename đã cấu hình.
- `addBasenames(...)` bổ sung basename mới ở cuối; basename đã tồn tại giữ nguyên vị trí.
- `getBasenameSet()` cho phép xem các mục đã đăng ký theo thứ tự.

Nếu đặt default library messages ở đầu và application overrides ở sau, kết quả có thể ngược chính sách override bạn muốn. Hãy định nghĩa và test **basename ưu tiên cao đứng trước** đối với `ResourceBundleMessageSource`.

## 7. Tra cứu qua các mức — mô hình dễ nhớ

Một flow khái niệm cho `child.getMessage("notice.x", args, locale)`:

~~~text
Yêu cầu: code + args + Locale
            |
            v
Child MessageSource
  - thử basename 1 với locale và candidate bundles
  - nếu chưa tìm được, thử basename 2
  - thử các mã common độc lập locale nếu được cấu hình
            |
            v
Không giải quyết được trong child?
            |
            +---- Có parent MessageSource?
            |         |
            |         v
            |    Parent giải quyết code với Locale phù hợp
            |
            v
Vẫn thiếu?
  - overload có defaultMessage: trả default (có thể null)
  - overload strict: NoSuchMessageException
  - useCodeAsDefaultMessage nếu được bật: có thể dùng code
~~~

Đây là **mô hình hành vi**, không phải call graph từng method, vì các implementations có đường tối ưu riêng (ví dụ tìm giá trị không arguments nhanh hơn, cache `MessageFormat`, properties merging).

**Đừng đặt common message, parent và default vào một danh sách tùy ý**: `AbstractMessageSource` có logic xử lý rõ ràng; nếu yêu cầu kiểm soát thứ tự tuyệt đối cho nhiều loại fallback, phải test đúng class và version trong dự án.

## 8. Message arguments dùng `java.text.MessageFormat`

Nếu bundle có `greeting=Xin chào, {0}!` thì `getMessage("greeting", new Object[]{"Lan"}, locale)` thay `{0}` bằng `Lan`. `{0,number,currency}` và các number/date patterns phụ thuộc locale và loại argument.

`MessageFormat` dùng apostrophe `'` làm cú pháp quote. Với câu có apostrophe literal khi format, dùng hai dấu `''`; khi không truyền arguments, `AbstractMessageSource` có thể dùng đường tối ưu bỏ qua MessageFormat trừ khi `alwaysUseMessageFormat` được bật. Đây là lý do test cả no-arg và has-arg variants. Bài 260 đi sâu grammar, types, escaping và hệ quả timezone.

## 9. Context tự tích hợp `MessageSource` như thế nào?

`ApplicationContext` kế thừa interface `MessageSource`. Khi refresh, nó tìm một bean có **tên chính xác `messageSource`**.

~~~java
@Configuration
public class LocalizationConfig {

    @Bean("messageSource")
    public MessageSource messages() {
        ResourceBundleMessageSource source =
            new ResourceBundleMessageSource();
        source.setBasenames("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }
}
~~~

Đây là snippet minh họa, cần imports `Configuration`, `Bean`, `MessageSource` và `ResourceBundleMessageSource`.

Nếu bean tên `messageSource` được tìm thấy, ApplicationContext dùng nó để phục vụ `getMessage(...)`. Nếu không có bean đó, context có thể sử dụng nguồn của parent context; nếu không tìm được source nào, Spring cung cấp một `DelegatingMessageSource` rỗng làm fallback infrastructure.

**Tên bean quan trọng:** `@Bean("localMessages")` chỉ trả một `MessageSource` type nhưng không mang tên `messageSource` sẽ không tự trở thành nguồn mặc định của toàn ApplicationContext. Muốn dùng API `context.getMessage(...)` theo nguồn ấy, đăng ký đúng tên, hoặc gọi chính bean riêng khi đó là ý đồ.

### MessageSourceAware

Một bean implement `MessageSourceAware` có thể nhận message source khi Spring khởi tạo nó. Nhưng constructor injection của `MessageSource` thường dễ kiểm thử và tường minh hơn nếu không cần aware callback.

## 10. Kế thừa từ parent ApplicationContext

Spring có kiến trúc parent–child ApplicationContext, hay gặp ở các hệ thống nhiều tầng/mô-đun.

~~~text
Parent ApplicationContext
  messageSource: messages chung của hệ thống
           |
           v
Child ApplicationContext
  messageSource: messages dành cho module
~~~

Khi child có một bean `messageSource` implement `HierarchicalMessageSource` mà chưa được gắn parent, Spring có thể gắn parent message source của parent context cho nó trong giai đoạn initialization.

Vì vậy:

- Mã có ở **child** → child thắng.
- Mã không có ở child nhưng có ở **parent** → parent cung cấp.
- Mã không có cả hai → dùng default message hoặc ném `NoSuchMessageException` theo overload/cấu hình.

**Không lẫn với dependency inheritance:** Parent-child context còn có quy tắc lookup beans khác. MessageSource hierarchy là một hợp đồng chuyên biệt, không phải “mọi file của parent tự động được copy vào danh sách basename của child”.

### Không thiết lập quan hệ parent sau refresh tùy tiện

Thông thường:

~~~java
child.setParent(parent);
child.register(ChildConfig.class);
child.refresh();
~~~

được thực hiện **trước khi child refresh**. Nếu thay parent sau khi context đã khởi tạo, nhiều infrastructure registrations/caches và bean references có thể không được xây lại như kỳ vọng. Hãy quyết định hierarchy ở bootstrap.

## 11. `HierarchicalMessageSource` và `setParentMessageSource`

Giao diện:

~~~java
public interface HierarchicalMessageSource extends MessageSource {
    void setParentMessageSource(MessageSource parent);
    MessageSource getParentMessageSource();
}
~~~

Đây là chữ ký **rút gọn để giải thích**, không phải file bạn cần định nghĩa lại.

Một custom source độc lập cũng có thể thiết lập parent bằng:

~~~java
StaticMessageSource child = new StaticMessageSource();
StaticMessageSource parent = new StaticMessageSource();

child.setParentMessageSource(parent);
~~~

Snippets cần import `StaticMessageSource`. Việc gắn parent hợp lệ khi source con chưa có parent và không tạo chu trình tham chiếu. `AbstractMessageSource` hỗ trợ delegation khi mã không được giải quyết ở source hiện tại.

### Cảnh báo tránh chu trình parent

Đừng gắn A → B và B → A. Cơ chế parent fallback cần tạo một hierarchy không chu trình. Trong cấu hình phức tạp, tránh can thiệp parent bằng các BeanFactoryPostProcessors tùy tiện sau khi ApplicationContext đã quản lý mối quan hệ ấy.

## 12. `useCodeAsDefaultMessage` — thuận tiện nhưng có bẫy

`AbstractMessageSource.setUseCodeAsDefaultMessage(true)` cho phép dùng **code** làm message fallback trong một số đường lookup thay vì ném `NoSuchMessageException`.

Ví dụ một code `"validation.required"` không được định nghĩa có thể hiển thị đúng chuỗi tên code để developer biết bản dịch còn thiếu.

Nhưng nếu **parent** source bật tùy chọn này, parent có thể trả code quá sớm và chặn những lựa chọn code tiếp theo trong `MessageSourceResolvable` có nhiều candidates. Javadoc khuyên chỉ dùng tùy chọn này để **development/debugging**, không dựa vào nó như chính sách production.

### So sánh

| Cách | Khi thiếu code | Nên dùng khi |
| --- | --- | --- |
| `getMessage(code,args,"Fallback",locale)` | Trả default được truyền | Text optional hoặc fallback có chủ đích |
| `getMessage(code,args,locale)` | Ném `NoSuchMessageException` theo thông thường | Message bắt buộc |
| `useCodeAsDefaultMessage(true)` | Có thể trả tên code | Chẩn đoán dev, không lạm dụng production |
| `MessageSourceResolvable` nhiều codes | Thử codes theo thứ tự | Validation/framework-specific messages |

Không coi “thiếu bản dịch thì hiển thị code” là policy tốt cho người dùng cuối nếu hệ thống cần UX đa ngôn ngữ chuẩn.

## 13. `MessageSourceResolvable` và thứ tự nhiều mã

Một validation error có thể cung cấp các mã từ cụ thể đến tổng quát, ví dụ:

~~~text
NotBlank.registration.email
NotBlank.email
NotBlank.java.lang.String
NotBlank
~~~

Khi gọi `getMessage(resolvable, locale)`, các code candidates được thử theo thứ tự mà resolvable cung cấp. Message source vẫn áp dụng locale/basename/parent rules cho từng mã.

Đây là một chiều precedence **bổ sung** với thứ tự resource bundles.

Ví dụ tự tạo:

~~~java
MessageSourceResolvable problem =
    new DefaultMessageSourceResolvable(
        new String[] {"account.email.required", "field.required"},
        null,
        "Please enter an email");

String display = source.getMessage(problem, Locale.ENGLISH);
~~~

Đoạn này cần imports `MessageSourceResolvable`, `DefaultMessageSourceResolvable`, `Locale`, và `source` kiểu `MessageSource`. Nó có thể dùng default nếu không mã nào tìm thấy.

### Vì sao parent code-as-default có thể nguy hiểm?

Nếu source parent biến mã đầu tiên thành text ngay cả khi không có bản dịch, vòng thử các mã tiếp theo có thể bị ảnh hưởng. Với error messages nhiều mức cụ thể, hãy để source báo “không tìm thấy” để tiếp tục thử mã khác, hoặc dùng default cuối cùng có chủ đích.

## 14. `commonMessages` là gì?

`AbstractMessageSource` cho phép cấu hình `setCommonMessages(Properties)`: các message chung **không phụ thuộc Locale**.

Ví dụ:

~~~java
Properties common = new Properties();
common.setProperty("button.cancel", "Cancel");
source.setCommonMessages(common);
~~~

Đây là snippet trong một method có `source` là `AbstractMessageSource` hoặc subclass và `Properties` đã import.

Phù hợp cho giá trị chung không cần dịch. Tuy nhiên nếu một code đáng lẽ có bản dịch theo Locale mà bị đưa vào commonMessages một cách thiếu kiểm soát, có thể khiến fallback khó lý giải.

**Phân biệt với file gốc:** `messages.properties` là base bundle **của một basename**; `commonMessages` là một tập messages độc lập locale gắn trực tiếp vào AbstractMessageSource. Chúng không phải một đối tượng.

## 15. ResourceBundleMessageSource — nạp theo Java ResourceBundle

`ResourceBundleMessageSource` dựa trên cơ chế `java.util.ResourceBundle` để giải quyết tên bundle, các locale candidates và fallback.

Cấu hình:

~~~java
ResourceBundleMessageSource source =
    new ResourceBundleMessageSource();

source.setBasenames("i18n/messages", "i18n/secondary");
source.setDefaultEncoding("UTF-8");
source.setFallbackToSystemLocale(false);
source.setCacheSeconds(-1);
~~~

Các điểm:

- Basenames theo convention Java, **không kèm phần mở rộng `.properties`**.
- Hỗ trợ bố trí `messages.properties`, `messages_vi.properties`, `messages_vi_VN.properties`…
- `setFallbackToSystemLocale(false)` giúp tránh kết quả phụ thuộc máy chủ khi không có message đúng requested Locale.
- `setDefaultEncoding("UTF-8")` hữu ích cho nội dung Unicode với các classic properties files.
- Cache mặc định `-1` giữ bundles lâu dài; cache refresh khác còn phụ thuộc behavior của JDK `ResourceBundle`/ClassLoader.

### Java module path/classloader caveat

Một số class loader hoặc JPMS/module layouts có hành vi tải bundles khác plain classpath. `ResourceBundleMessageSource` thường phù hợp với classic classpath/JAR bundles. Khi đóng gói bằng custom loaders/nested JARs, kiểm thử trên packaging thực thay vì chỉ IDE.

## 16. ReloadableResourceBundleMessageSource — dành cho yêu cầu nạp lại

`ReloadableResourceBundleMessageSource` tải resources theo `ResourceLoader`, quản lý cache file-based và có khả năng kiểm tra thay đổi.

~~~java
ReloadableResourceBundleMessageSource source =
    new ReloadableResourceBundleMessageSource();

source.setBasename("file:/opt/app/i18n/messages");
source.setDefaultEncoding("UTF-8");
source.setFallbackToSystemLocale(false);
source.setCacheMillis(5_000);
~~~

Cấu hình trên **minh họa** đường dẫn Unix khi file thực tế có tên `/opt/app/i18n/messages.properties` và các biến thể locale tương ứng.

### Các đặc điểm cần nhớ

- Có thể dùng `classpath:` hoặc `file:` location theo cơ chế Spring Resource, khác tên basename trần của `ResourceBundleMessageSource`.
- Khi cấu hình thời gian cache hữu hạn, source có thể kiểm tra timestamp rồi nạp lại.
- Thường nên đặt **file ngoài classpath/JAR** nếu cần hot reload đáng tin cậy.
- Các file đóng trong JAR thường không phù hợp cho cập nhật tại runtime hoặc kiểm tra last-modified ổn định.
- Reloadable message source không phải filesystem watcher event-driven; nó kiểm tra khi phù hợp với caching policy.
- Nó có `clearCache()` và `clearCacheIncludingAncestors()` để xóa cache bằng lệnh rõ ràng.

Không coi chữ “Reloadable” là lời hứa mọi sửa file ở mọi classloader sẽ được phát hiện tức thì.

## 17. `cacheMillis` và `cacheSeconds`

Cả hai implementations resource-based dùng một cấu hình khoảng cache tương đương:

| Giá trị | Ý nghĩa | Khuyến nghị |
| --- | --- | --- |
| `-1` | Cache không hết hạn theo chính sách mặc định | Tốt cho messages đóng cùng build artifact |
| `0` | Kiểm tra thay đổi tại mỗi lần truy cập phù hợp | Chủ yếu development; không dùng bừa trong production |
| `>0` | Sau khoảng thời gian này, lần truy cập kế tiếp có thể kiểm tra refresh | Khi có yêu cầu reload ở external files |
| `setCacheSeconds(5)` | Tương đương khoảng 5 giây | Dễ đọc trong cấu hình |
| `setCacheMillis(5000)` | Tương đương khoảng 5000 ms | Chính xác hơn khi cần ngưỡng millisecond |

### Không phải polling timer

`setCacheMillis(5000)` **không có nghĩa cứ 5 giây Spring tạo một background task** đi quét toàn bộ file. Tùy implementation, lần truy cập sau khi cache hết hạn sẽ kích hoạt quá trình refresh/check.

### Cache vĩnh viễn

`-1` là default và rất phù hợp khi messages đi kèm version ứng dụng. Nếu file thay đổi ở đĩa nhưng cache đang giữ kết quả cũ, cần explicit cache clearing hoặc lifecycle/configuration reload thích hợp.

### Tại sao `0` không phù hợp tải cao?

Kiểm tra last-modified hoặc đọc lại metadata liên tục có thể tăng I/O, lock contention và latency khi nhiều requests đồng thời. Không dùng `cacheMillis(0)` như giải pháp chung cho live editing production.

## 18. `clearCache()` và `clearCacheIncludingAncestors()`

`ReloadableResourceBundleMessageSource` có:

~~~java
source.clearCache();
~~~

Gọi method này xóa cache của **source hiện tại**; lần resolve sau có thể tải lại resource files.

Với hierarchy:

~~~java
source.clearCacheIncludingAncestors();
~~~

có thể xóa cache trên current source và ancestors thuộc loại được hỗ trợ tương ứng.

### Đừng đánh đồng với việc thay locale

Đổi `Locale` trong `getMessage(...)` yêu cầu tìm chuỗi theo locale mới, nhưng **không tự xóa tất cả cache**. Một message source có thể giữ cache riêng cho nhiều locale/bundle/`MessageFormat` nhằm phục vụ chúng nhanh hơn.

### Đừng xóa cache toàn bộ sau mỗi request

Nếu hệ thống có một admin action “reload translations”, nên xác thực quyền gọi, log audit phù hợp rồi xóa cache trong một điểm kiểm soát. Xóa cache mỗi request phá lợi ích caching và có thể gây I/O áp lực.

## 19. Ví dụ đầy đủ: thứ tự Locale, basename, parent và cache

Project dưới đây **chạy được với Java 17 + Spring Context 6.1.13** mà không cần Spring Boot/web server.

Nó có hai `AnnotationConfigApplicationContext`:

- Parent đăng ký `StaticMessageSource` với bean name `messageSource`.
- Child đăng ký `ResourceBundleMessageSource` cũng có bean name `messageSource`.
- Child gắn parent context **trước refresh** để Spring kết nối message source hierarchy.
- Child có hai basenames `i18n/messages` và `i18n/secondary`, giúp minh họa basename precedence.

Phần thứ hai tạo **một external properties file tạm thời**, đọc bằng `ReloadableResourceBundleMessageSource` với cache `-1`, thay file và gọi `clearCache()`. Vì xóa cache tường minh, demo không phụ thuộc sleep hoặc độ phân giải modification timestamp của filesystem.

### 19.1. Cấu trúc project

~~~text
message-lookup-cache-demo/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/example/messages/MessageLookupDemo.java
        └── resources/
            └── i18n/
                ├── messages.properties
                ├── messages_vi.properties
                ├── messages_vi_VN.properties
                ├── secondary.properties
                └── secondary_vi_VN.properties
~~~

### 19.2. File `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>message-lookup-cache-demo</artifactId>
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
                    <mainClass>com.example.messages.MessageLookupDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 19.3. File `src/main/resources/i18n/messages.properties`

~~~properties
greeting=Hello, {0}!
title=Default title
root.only=ROOT-ONLY
shared=CHILD-ROOT
priority=PRIMARY-ROOT
~~~

### 19.4. File `src/main/resources/i18n/messages_vi.properties`

~~~properties
greeting=Chao, {0}!
title=Tieu de
~~~

### 19.5. File `src/main/resources/i18n/messages_vi_VN.properties`

~~~properties
greeting=Xin chao VN, {0}!
shared=CHILD-VN
~~~

### 19.6. File `src/main/resources/i18n/secondary.properties`

~~~properties
secondary.only=SECONDARY-ONLY
priority=SECONDARY-ROOT
~~~

### 19.7. File `src/main/resources/i18n/secondary_vi_VN.properties`

~~~properties
priority=SECONDARY-VN
~~~

### 19.8. File `src/main/java/com/example/messages/MessageLookupDemo.java`

~~~java
package com.example.messages;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.support.StaticMessageSource;

public class MessageLookupDemo {

    private static final Locale VI_VN = Locale.forLanguageTag("vi-VN");

    public static void main(String[] args) throws IOException {
        try (AnnotationConfigApplicationContext parent =
                 new AnnotationConfigApplicationContext(ParentConfig.class);
             AnnotationConfigApplicationContext child =
                 new AnnotationConfigApplicationContext()) {

            child.setParent(parent);
            child.register(ChildConfig.class);
            child.refresh();

            System.out.println("child-greeting=" +
                child.getMessage("greeting", new Object[] {"Lan"}, VI_VN));
            System.out.println("child-title=" +
                child.getMessage("title", null, VI_VN));
            System.out.println("root-only=" +
                child.getMessage("root.only", null, VI_VN));
            System.out.println("basename-priority=" +
                child.getMessage("priority", null, VI_VN));
            System.out.println("secondary-only=" +
                child.getMessage("secondary.only", null, VI_VN));
            System.out.println("child-overrides-parent=" +
                child.getMessage("shared", null, VI_VN));
            System.out.println("parent-only=" +
                child.getMessage("parent.only", null, VI_VN));

            System.out.println("missing-default=" +
                child.getMessage("missing", null, "FALLBACK", VI_VN));

            boolean strictMissing = false;
            try {
                child.getMessage("missing", null, VI_VN);
            } catch (NoSuchMessageException expected) {
                strictMissing = true;
            }
            System.out.println("missing-strict-error=" + strictMissing);
        }

        demonstrateExplicitCacheClear();
    }

    private static void demonstrateExplicitCacheClear()
            throws IOException {
        Path dir = Files.createTempDirectory("spring-message-262-");
        Path file = dir.resolve("dynamic.properties");

        try {
            Files.writeString(
                file, "live.value=first\n", StandardCharsets.UTF_8);

            ReloadableResourceBundleMessageSource source =
                new ReloadableResourceBundleMessageSource();

            String location = file.toUri().toString();
            String suffix = ".properties";
            String basename =
                location.substring(0, location.length() - suffix.length());

            source.setBasename(basename);
            source.setDefaultEncoding("UTF-8");
            source.setFallbackToSystemLocale(false);
            source.setCacheMillis(-1);

            System.out.println("cache-initial=" +
                source.getMessage("live.value", null, VI_VN));

            Files.writeString(
                file, "live.value=second\n", StandardCharsets.UTF_8);

            // Với cache -1, value đã resolve được giữ lại.
            System.out.println("cache-before-clear=" +
                source.getMessage("live.value", null, VI_VN));

            source.clearCache();

            System.out.println("cache-after-clear=" +
                source.getMessage("live.value", null, VI_VN));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ParentConfig {

        @Bean("messageSource")
        public StaticMessageSource messageSource() {
            StaticMessageSource source = new StaticMessageSource();

            source.addMessage("shared", VI_VN, "PARENT-SHARED");
            source.addMessage("parent.only", VI_VN, "PARENT-ONLY");
            return source;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ChildConfig {

        @Bean("messageSource")
        public ResourceBundleMessageSource messageSource() {
            ResourceBundleMessageSource source =
                new ResourceBundleMessageSource();

            source.setBasenames("i18n/messages", "i18n/secondary");
            source.setDefaultEncoding("UTF-8");
            source.setFallbackToSystemLocale(false);
            source.setCacheMillis(-1);
            return source;
        }
    }
}
~~~

### 19.9. Lệnh chạy

Yêu cầu JDK 17+ và Maven 3.9.x. Tại thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Những dòng do chương trình chủ động in, trong classpath chỉ có các fixtures `i18n/...` tương ứng:

~~~text
child-greeting=Xin chao VN, Lan!
child-title=Tieu de
root-only=ROOT-ONLY
basename-priority=PRIMARY-ROOT
secondary-only=SECONDARY-ONLY
child-overrides-parent=CHILD-VN
parent-only=PARENT-ONLY
missing-default=FALLBACK
missing-strict-error=true
cache-initial=first
cache-before-clear=first
cache-after-clear=second
~~~

Maven và logging framework có thể in thêm thông tin; đây là các dòng `System.out.println` của chương trình.

### 19.10. Giải thích phần tra cứu

**`child-greeting`:** Locale `vi_VN` tìm được `greeting` trong `messages_vi_VN.properties`. Argument `Lan` được đưa vào `{0}` bằng MessageFormat.

**`child-title`:** File vi_VN không có `title`, nhưng `messages_vi.properties` có `title=Tieu de`, nên dùng bản dịch cấp ngôn ngữ.

**`root-only`:** Mã `root.only` chỉ ở file gốc `messages.properties`. Nó vẫn có thể được trả về cho request `vi_VN`.

**`basename-priority`:** Basename `i18n/messages` được đăng ký trước. Dù `i18n/secondary_vi_VN.properties` cũng định nghĩa `priority`, basename đầu đã cung cấp mã từ root bundle nên kết quả là `PRIMARY-ROOT`.

**`secondary-only`:** Basename đầu không có mã `secondary.only`; basename `i18n/secondary` cung cấp nó.

**`child-overrides-parent`:** Cả parent StaticMessageSource và child bundles có mã `shared`. Child giải quyết được `CHILD-VN` nên parent không thắng.

**`parent-only`:** Child không có mã `parent.only` trong bundles; Spring delegate sang parent MessageSource và nhận `PARENT-ONLY`.

**`missing-default`:** Không có `missing` trong cả hai contexts; overload có fallback trả `FALLBACK`.

**`missing-strict-error`:** Overload strict không có fallback, vì vậy ném `NoSuchMessageException`, được bắt để minh họa.

### 19.11. Giải thích phần cache

1. Chương trình tạo một file tạm `dynamic.properties` với `live.value=first`.
2. `ReloadableResourceBundleMessageSource` dùng basename suy từ `file.toUri()`, **không gồm phần đuôi `.properties`**.
3. `cacheMillis=-1` làm source giữ dữ liệu đã tải lâu dài.
4. Sửa file thành `live.value=second` nhưng chưa xóa cache → vẫn đọc `first`.
5. `clearCache()` xóa cache trong source.
6. Lần lookup kế tiếp nạp lại và trả `second`.
7. `finally` xóa file và thư mục tạm.

**Không cần `Thread.sleep()`:** Để kiểm thử ý nghĩa cache, explicit clear tránh phụ thuộc độ phân giải timestamp và timing scheduler. Trong môi trường không có quyền tạo file tạm hoặc cập nhật tệp, phần demo sẽ ném `IOException` thay vì giả vờ thành công.

## 20. Vì sao ví dụ tách parent/child thành hai contexts?

Một ApplicationContext không thể có hai bean definitions cùng tên `messageSource` mà vẫn tự động tạo ra hierarchy giữa chúng. Parent–child contexts là cách minh họa sát integration contract.

~~~text
Parent bean "messageSource":
  shared=PARENT-SHARED
  parent.only=PARENT-ONLY

Child bean "messageSource":
  shared=CHILD-VN
  greeting=...
  priority=...

Lookup trên child:
  child resolves shared -> CHILD-VN
  child misses parent.only -> parent resolves -> PARENT-ONLY
~~~

Nếu bạn chỉ gọi trực tiếp `parent.getMessage("shared",...)` thì đương nhiên nhận parent value. Kế thừa chỉ xảy ra khi lookup trên source/context con và mã chưa tìm thấy ở đó.

### Dùng ApplicationContextAware hoặc constructor injection?

Một service có thể nhận `MessageSource` qua constructor:

~~~java
public GreetingService(MessageSource messages) {
    this.messages = messages;
}
~~~

Đây là snippet minh họa cần field và import `MessageSource`. Nếu một child có source bean đúng tên, container thường có thể inject source trong phạm vi child. Tuy nhiên khi có nhiều beans cùng type ở hierarchy hoặc ứng dụng tự đăng ký nguồn khác, cần kiểm tra dependency candidate resolution và qualifier.

Không dùng global static MessageSource như singleton toàn JVM nếu ứng dụng có nhiều contexts với resource bundles khác nhau.

## 21. `defaultLocale` và `fallbackToSystemLocale`

Nếu requested Locale không có bundle đủ thông tin, hệ thống có thể fallback sang default locale đã cấu hình hoặc default locale của JVM.

~~~java
source.setFallbackToSystemLocale(false);
~~~

Thường hữu ích trong web app/container: locale hệ điều hành của server **không nên vô tình quyết định ngôn ngữ hiển thị của người dùng**. Khi tắt, file gốc vẫn có thể là fallback.

Có thể chọn một locale mặc định rõ:

~~~java
source.setDefaultLocale(Locale.ENGLISH);
~~~

Đây là snippet có import `Locale`; cần xem contract của implementation khi kết hợp với `fallbackToSystemLocale`.

### Locale nhận từ đâu trong ứng dụng web?

Spring MVC có `LocaleResolver`/`LocaleContextResolver` và các điểm tích hợp khác; chúng quyết định locale của request trước khi dịch message. Đó là tầng riêng. `MessageSource` không tự xác định locale của user khi caller đã truyền một `Locale` cụ thể.

### Không nên gọi ngẫu nhiên `Locale.getDefault()`

Default JVM Locale có thể bị thay bởi startup flags, image/container configuration hoặc tests. Hãy định nghĩa rõ fallback locale cho hệ thống nhiều ngôn ngữ và test các locales bạn hỗ trợ.

## 22. Thứ tự source không giống thứ tự key trong file

Trong một file `.properties`:

~~~properties
a=First
b=Second
c=Third
~~~

Thứ tự các dòng không phải thứ tự ưu tiên giữa các **message codes** khác nhau. Spring tra theo **code key**, không đọc file từ trên xuống mỗi request để chọn dòng đầu tiên có ý nghĩa.

Khi một key trùng lặp ngay trong cùng một properties file, Java `Properties` loader thường giữ assignment sau cùng theo luật load. Nhưng đây là vấn đề dữ liệu trong một file, khác với **basename precedence** hoặc **locale precedence** của MessageSource.

**Khuyến nghị:** Không khai báo duplicate keys trong cùng một file. Dùng kiểm tra lint/build để phát hiện bản dịch bị trùng hoặc thiếu.

## 23. UTF-8 và MessageFormat là hai giai đoạn khác nhau

**Giai đoạn 1:** Load bytes từ files theo charset (ví dụ UTF-8) để có chuỗi Java Unicode.

**Giai đoạn 2:** Nếu cần arguments, `MessageFormat` xử lý `{0}`, `{1,number}`… dựa trên locale.

~~~properties
account.welcome=Chào {0}!
~~~

Nếu loader đọc sai encoding, bạn có thể nhận ký tự lỗi ngay cả khi MessageFormat format arguments đúng. Nếu charset đúng nhưng pattern sai, nội dung có thể lỗi/biến dạng tại bước format.

### Khác biệt JDK ResourceBundle với Spring

`ResourceBundleMessageSource` sử dụng Java ResourceBundle infrastructure; `ReloadableResourceBundleMessageSource` dùng Spring resource/properties loading. Mặc dù cả hai cung cấp các API tương tự, việc charset và caching nội bộ có những khác biệt. Cấu hình `setDefaultEncoding("UTF-8")` nhất quán khi muốn làm chủ cách đọc dữ liệu classic properties.

Ví dụ runnable dùng text ASCII để stdout dễ đối chiếu trên mọi terminal. Trong ứng dụng thật, file tiếng Việt có dấu có thể được lưu UTF-8 khi loader được cấu hình tương thích.

## 24. Cache message patterns khác cache file contents

Một message source có thể cache:

- Dữ liệu properties/resource bundles đã load.
- Kết quả lookup theo bundle và locale.
- Đối tượng `MessageFormat` để tránh parse lại pattern cho mỗi request.
- Metadata/timestamp về lần tải gần nhất.

Không nên kết luận cache chỉ là một `Map<String,String>` toàn cục. Điều đó không phản ánh đầy đủ các structures mà các implementations Spring/JDK có thể dùng.

### Với messages có parameters

`{0}` và `{1,number}` được xử lý qua MessageFormat. Nếu hàng ngàn requests cùng format một pattern, caching MessageFormat thích hợp giúp giảm chi phí parse; Spring có cơ chế cache/đồng bộ cần thiết cho implementation đó.

Đừng tự dùng chung một `MessageFormat` mutable giữa nhiều threads nếu chưa có synchronization/copy policy phù hợp. Khi sử dụng `MessageSource.getMessage(...)` chuẩn, hãy để implementation quản lý tính nhất quán của việc formatting.

## 25. Khi file thay đổi nhưng chưa thấy thông điệp mới

Một checklist chẩn đoán:

1. **Bạn đã sửa file nào?** `messages_vi_VN.properties`, `messages_vi.properties` hay một file của basename khác?
2. **Mã có bị override bởi basename trước không?** File mới có thể đã load đúng nhưng mã trùng ở source ưu tiên cao hơn.
3. **Locale đang được yêu cầu là gì?** `vi`, `vi_VN` và `en_US` có các candidate bundles khác nhau.
4. **Message source là implementation nào?** `ResourceBundleMessageSource` hay `ReloadableResourceBundleMessageSource`?
5. **Cache đang là `-1` hay một khoảng hữu hạn?** `-1` không tự reload khi file sửa.
6. **File nằm trong JAR/classpath hay filesystem ngoài JAR?** JAR resources thường không thể hot edit.
7. **Có proxy/class loader/JAR caching không?** Một cache timeout hữu hạn có thể chưa đủ để thấy content mới trong một số packaging.
8. **Có gọi clearCache() trên đúng instance không?** Xóa cache của child không nhất thiết xóa cache của parent.
9. **File có đúng encoding/pattern không?** Sai ký tự hoặc MessageFormat có thể tạo output không mong muốn.
10. **ApplicationContext đang giữ phiên bản cũ?** Hot reload code không đồng nghĩa tự tạo MessageSource mới.
11. **File timestamp có cập nhật như kỳ vọng?** Một số filesystem độ phân giải thấp hoặc copy-preserve-timestamp ảnh hưởng refresh checks.
12. **Có thứ tự nhiều resource basenames khác với bạn nghĩ?** Xem `getBasenameSet()` nếu implementation hỗ trợ.

**Không giải quyết bằng việc luôn set cacheMillis=0 ở production**. Hãy xác định lại nguồn, thứ tự và packaging trước khi lựa chọn TTL.

## 26. Lỗi thiếu mã thông điệp

Một mã có thể không tìm được do:

- Viết sai key giữa Java code và file properties.
- Khai báo sai basename hoặc path.
- Resource chưa được copy vào JAR.
- Dùng `ResourceBundleMessageSource` với prefix `classpath:` không đúng convention của class này.
- Truyền Locale không khớp các file và không có fallback root.
- Mã chỉ có ở child context khác, không nằm trên parent chain.
- Parent MessageSource không được gắn vì đặt context hierarchy quá muộn.
- Các file trong thư viện có cùng basename nhưng class loader chọn nguồn khác dự kiến.
- MessageFormat pattern không hợp lệ khiến quá trình format lỗi, dù key có thể tồn tại.

### Catch đúng exception

~~~java
try {
    return source.getMessage("critical.error", null, locale);
} catch (NoSuchMessageException ex) {
    throw new IllegalStateException(
        "Missing translation: critical.error", ex);
}
~~~

Đây là snippet minh họa trong method có biến `source`/`locale`, cần import `NoSuchMessageException`. Bảo toàn `cause` để biết root issue.

Đừng catch mọi exception rồi hiển thị `"undefined"` cho lỗi có ý nghĩa nghiệp vụ; việc thiếu bản dịch quan trọng nên được phát hiện bằng test hoặc báo lỗi hữu ích.

## 27. Test matrix cần có trong hệ thống đa ngôn ngữ

| Case | Cần kiểm tra |
| --- | --- |
| Code trong locale cụ thể | `messages_vi_VN` thắng `messages_vi` |
| Code chỉ ở language bundle | `messages_vi` được sử dụng cho `vi_VN` |
| Code chỉ ở root bundle | Root fallback phù hợp |
| Hai basenames cùng có key | Thứ tự basename đúng |
| Code chỉ ở basename thứ hai | Chuyển sang basename tiếp theo |
| Child và parent cùng có key | Child thắng |
| Code chỉ có ở parent | Parent fallback hoạt động |
| Code không có ở đâu | Default message hoặc NoSuchMessageException |
| Message có arguments | `{0}` được thay chính xác |
| Unicode UTF-8 | Không lỗi tiếng Việt, dấu apostrophe/MessageFormat |
| Locale khác nhau | Không rò translation từ system locale không mong muốn |
| Cache `-1` | File sửa không tự reflect |
| Explicit clearCache | File ngoài classpath được nạp lại |
| Cache hữu hạn | Reload sau interval và timestamp change |
| Nhiều MessageSourceResolvable codes | Thứ tự code candidates đúng |
| Parent useCodeAsDefaultMessage | Không che mã fallback khác |
| Đóng gói JAR | Bundle availability đúng ở runtime |
| Concurrency | Không dùng MessageFormat tùy biến không thread-safe |
| Context hierarchy | Parent được set trước child refresh |

Các tests nên tạo context mới cho những biến thể cấu hình khác nhau. Với cache tests, dùng external files trong temp directory và xóa cache tường minh để không phụ thuộc thời gian chờ khi chỉ cần chứng minh cache có hoạt động.

## 28. Các lựa chọn thiết kế

| Tình huống | Gợi ý | Vì sao |
| --- | --- | --- |
| Messages đóng cố định trong ứng dụng | `ResourceBundleMessageSource` | Dựa trên ResourceBundle chuẩn |
| Messages thay đổi ngoài JAR | `ReloadableResourceBundleMessageSource` | Quản lý cache và refresh checks |
| Unit test với một vài câu ngắn | `StaticMessageSource` | Không cần file/resource loading |
| Modules có parent/child contexts | `HierarchicalMessageSource` | Rõ quy tắc child override |
| Nhiều bundles cùng layer | `setBasenames` theo thứ tự đã định | Đảm bảo precedence |
| App nhiều ngôn ngữ | Tắt system locale fallback không mong muốn | Giảm phụ thuộc máy chủ |
| Thông điệp bắt buộc | Overload strict + tests | Phát hiện thiếu bản dịch |
| Thông điệp optional | Overload có defaultMessage | Fallback có chủ đích |
| Hot reload rất thường xuyên | External translation service/cache policy rõ | Tránh quét filesystem mỗi request |
| Nội dung đến từ nguồn không tin cậy | Validate keys, pattern/encoding, giới hạn file | Tránh lỗi format và rò dữ liệu |

## 29. Vấn đề với hot reload trong production

Không nên biến message source thành dịch vụ hot reload không kiểm soát chỉ bằng cách đặt cache time cực nhỏ.

**Các rủi ro:**

- Files thay đổi không nguyên tử: một reader có thể thấy bộ bundles chưa đồng bộ.
- Nhiều instances trong cluster có cache/TTL khác nhau và trả nội dung khác nhau.
- Một node cache message cũ trong khi node khác vừa refresh.
- File bị sửa nhưng timestamp không thay đúng cách.
- Java class loader giữ bản cached resource, đặc biệt với bundles trong JAR.
- Lỗi encoding hoặc MessageFormat xuất hiện giữa các phiên bản.
- Nhiều request cùng kích hoạt refresh có thể làm I/O tăng mạnh.

**Hướng tốt:**

1. Đặt translation files ngoài JAR nếu yêu cầu reload.
2. Xuất bản files **atomically** theo version (ghi file mới và rename theo chính sách filesystem thích hợp).
3. Cung cấp endpoint/admin command có xác thực để invalidate cache.
4. Kiểm thử format, schema/code coverage trước khi áp dụng.
5. Ghi log version và thời điểm reload, không log thông tin nhạy cảm.
6. Xác định behavior khi reload thất bại: giữ cache cũ hay fail có kiểm soát.
7. Đồng bộ/triển khai version nhất quán trên nhiều instances khi cần.

Tùy yêu cầu, deploy version mới với cache mặc định thường đơn giản và an toàn hơn hot-reload translations.

## 30. Phân biệt Spring Core với Spring Boot message configuration

Spring Boot có auto-configuration liên quan message source, thường dựa trên những settings như `spring.messages.basename`, `spring.messages.encoding` và cache duration.

Tuy nhiên, trọng tâm cơ chế vẫn là các implementations `MessageSource` của Spring Framework.

### Spring Core thuần

Bạn thường khai báo bean tên `messageSource` thủ công:

~~~java
@Bean("messageSource")
ResourceBundleMessageSource messageSource() {
    ResourceBundleMessageSource source =
        new ResourceBundleMessageSource();
    source.setBasenames("i18n/messages");
    return source;
}
~~~

### Spring Boot

Boot có thể tạo message source tự động nếu các điều kiện auto-configuration phù hợp, dựa trên properties/settings của Boot version. Đừng giả định Boot sẽ tự nạp mọi bundle tùy ý nếu không cấu hình đúng basename hoặc không có resource files mà nó yêu cầu.

**Không nhầm** messages bundles với `application.properties`: Spring Boot Config Data tải configuration properties, còn `MessageSource` tải những **câu thông điệp i18n** từ basename để hiển thị theo Locale.

## 31. FAQ và tình huống suy luận

**Câu 1: Nếu `messages_vi_VN.properties` không chứa key, Spring có hỏi parent ngay không?**

Không. Nó có thể tìm key trong `messages_vi.properties`, root bundle hoặc basename khác trong child. Parent là một cấp fallback khác, khi child không giải quyết được mã.

**Câu 2: Nếu basename 1 root bundle chứa key nhưng basename 2 có bản vi_VN, cái nào thắng?**

Với `ResourceBundleMessageSource` và cấu hình nhiều basenames thường gặp, basename 1 được kiểm tra trước; nó có thể thắng dù key chỉ nằm ở root bundle.

**Câu 3: Có thể dựa vào thứ tự dòng trong file properties để chọn translation không?**

Không. Mỗi code là một key. Với key khác nhau, thứ tự dòng không phải precedence; nếu trùng key trong cùng file, đó là vấn đề chất lượng dữ liệu.

**Câu 4: Khi không có bean `messageSource` trong child, con có mất hết thông điệp không?**

Không nhất thiết. ApplicationContext có thể kế thừa message source từ parent hoặc dùng DelegatingMessageSource infrastructure nếu không có source nào.

**Câu 5: `setCacheSeconds(5)` có tạo scheduler quét file mỗi 5 giây không?**

Không nên hiểu như vậy. Nó xác định khoảng giữ cache/kiểm tra khi có lookup phù hợp, không phải một nhiệm vụ polling nền có lịch độc lập.

**Câu 6: Tại sao sửa file classpath trong JAR nhưng message không thay?**

Resource đóng trong JAR thường không được cập nhật tại runtime, và caches/class loader có thể giữ dữ liệu. Để reload, dùng file ngoài JAR và ReloadableResourceBundleMessageSource phù hợp.

**Câu 7: Nên dùng `clearCache()` hay `clearCacheIncludingAncestors()`?**

Nếu chỉ cập nhật child source, `clearCache()` thường đủ. Nếu parent cũng dùng reloadable source và vừa sửa các bundles của ancestor, cần chính sách invalidation cả hierarchy.

**Câu 8: `getMessage(code,args,locale)` thiếu mã khác gì overload có default?**

Overload strict có thể ném `NoSuchMessageException`; overload có default message trả fallback nếu không tìm thấy mã.

**Câu 9: Có nên bật `useCodeAsDefaultMessage` trong parent production không?**

Thường không. Nó có thể che missing translations và gây vấn đề với resolvables nhiều codes. Dùng trong debugging nếu thật sự cần.

**Câu 10: Khi nào dùng `StaticMessageSource`?**

Khi test hoặc cần khai báo một số messages đơn giản bằng code. Nó không phải lựa chọn lý tưởng cho nhiều bản dịch lớn được biên tập ngoài chương trình.

## 32. Checklist code review

- [ ] Bean `messageSource` có đúng tên và được Spring register trong context dự kiến?
- [ ] Các basenames có đúng path conventions và không kèm extension không?
- [ ] Với nhiều basenames, thứ tự ưu tiên đã được ghi rõ và test chưa?
- [ ] Có hiểu locale-specific, language và root bundle fallbacks không?
- [ ] Có xác định có fallback system/default locale hay không?
- [ ] Parent context đã được set trước child refresh chưa?
- [ ] Child MessageSource có parent hợp lệ và không có cycle?
- [ ] Mã child có được ưu tiên hơn parent khi cả hai định nghĩa?
- [ ] Required messages có test thiếu mã/NoSuchMessageException?
- [ ] Optional messages có fallback text phù hợp và đã kiểm tra arguments?
- [ ] Có tránh bật useCodeAsDefaultMessage ở parent không cần thiết?
- [ ] UTF-8 và MessageFormat apostrophe escaping có đúng không?
- [ ] Cache `-1` hay TTL được chọn theo yêu cầu deployment thực?
- [ ] Nếu yêu cầu reload, file có ở external filesystem, không nằm cố định trong JAR?
- [ ] Có invalidate đúng source (và parent) khi thay đổi translations?
- [ ] Có performance tests và giới hạn I/O khi cache TTL nhỏ?
- [ ] Có kiểm thử trên JAR/classloader thực tế thay vì chỉ IDE?
- [ ] Có triển khai rõ chiến lược đồng bộ cache giữa các instances?

## 33. Tóm tắt

- `MessageSource` chuyển **message code + arguments + Locale** thành text đã bản địa hóa.
- Trong một basename, các bản dịch **cụ thể hơn cho Locale** thường có precedence cao hơn các bundles chung và root bundle.
- Khi có **nhiều basenames**, thứ tự khai báo quan trọng; basename đứng trước có thể override cùng code ở basename đứng sau.
- `HierarchicalMessageSource` hỗ trợ **child → parent**: child có mã thì child thắng; child không có thì parent có thể cung cấp.
- `defaultMessage` và `NoSuchMessageException` là hai kết quả khác nhau của các overload `getMessage`.
- `MessageSourceResolvable` có thêm thứ tự **nhiều code candidates**, khác thứ tự bundles.
- `ResourceBundleMessageSource` phù hợp với JDK/classpath bundles; `ReloadableResourceBundleMessageSource` phù hợp khi cần refresh từ resources, nhất là file ngoài JAR.
- `cacheMillis=-1` giữ dữ liệu lâu dài; `0` thường quá tốn kém; giá trị dương kiểm tra sau một khoảng khi lookup.
- `clearCache()` là cách invalidate current source rõ ràng; `clearCacheIncludingAncestors()` phục vụ hierarchy tương ứng.
- Muốn hành vi ổn định phải test đủ **locale, basename, parent, cache và deployment packaging**.

## 34. Tài liệu chính thức

- [Spring Framework — ApplicationContext và quốc tế hóa MessageSource](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html): `messageSource` bean, child/parent context, implementations và message resolution.
- [Spring Framework 6.1 — MessageSource Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/MessageSource.html): ba overload `getMessage` và hợp đồng fallback.
- [Spring Framework 6.1 — HierarchicalMessageSource Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/HierarchicalMessageSource.html): parent delegation.
- [Spring Framework 6.1 — AbstractMessageSource Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/support/AbstractMessageSource.html): `commonMessages`, parent handling và `useCodeAsDefaultMessage`.
- [Spring Framework 6.1 — AbstractResourceBasedMessageSource Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/support/AbstractResourceBasedMessageSource.html): basenames ordering, defaultLocale, encoding và caching semantics.
- [Spring Framework 6.1 — ResourceBundleMessageSource Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/support/ResourceBundleMessageSource.html): standard ResourceBundle strategy.
- [Spring Framework 6.1 — ReloadableResourceBundleMessageSource Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/support/ReloadableResourceBundleMessageSource.html): `cacheMillis`, reload behavior, `clearCache`.
- [Spring Framework 6.1 — StaticMessageSource Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/support/StaticMessageSource.html): programmatic test messages.
- [Java SE 17 — ResourceBundle API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ResourceBundle.html): locale candidates, bundle fallback và JDK caching.
- [Java SE 17 — MessageFormat API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/MessageFormat.html): parameters, escaping và locale-specific formatting.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="internationalization-007">Kiểm chứng mã hóa tiếng Việt và cơ chế dự phòng ngôn ngữ</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm chứng mã hóa tiếng Việt và cơ chế dự phòng ngôn ngữ trong Spring Framework

## 1. Mục tiêu và vấn đề thực tế

Một ứng dụng Spring hỗ trợ tiếng Việt có thể hoạt động bình thường ở máy phát triển nhưng hiển thị `"Xin chÃ o"`, `"Nguyá»…n"`, mất dấu hoặc trả tiếng Anh khi được triển khai. Những hiện tượng đó không phải lúc nào cũng cùng nguyên nhân:

1. **Sai mã hóa byte:** file `.properties` được lưu UTF-8 nhưng được đọc bằng ISO-8859-1, hoặc file được lưu bằng một charset khác.
2. **Sai quá trình build:** Maven/resource filtering, trình soạn thảo, bước đóng gói hoặc một script đã chuyển mã hóa hoặc phá Unicode escapes.
3. **Sai nguồn bundle:** `basename`, suffix `_vi`/`_vi_VN`, classpath hoặc file location không đúng.
4. **Sai lựa chọn Locale:** request muốn `vi-VN` nhưng code truyền `Locale.ENGLISH` hoặc dùng `Locale.getDefault()` của server.
5. **Sai cơ chế dự phòng (fallback):** key không có ở bản tiếng Việt, Spring chuyển sang root bundle hoặc default locale theo chính sách không mong muốn.
6. **Sai MessageFormat:** dấu nháy đơn, `{0}`, dấu ngoặc nhọn hoặc kiểu dữ liệu tham số khiến bản dịch có lỗi dù Unicode được đọc đúng.
7. **Cache dữ liệu cũ:** một file được cập nhật nhưng nguồn thông điệp chưa reload, đặc biệt khi nằm trong JAR/classpath.

Mục tiêu của README này là giúp bạn **kiểm chứng** từng tầng với Java 17 và Spring Framework Core Container, từ bytes trong file đến nội dung `MessageSource.getMessage(...)` cuối cùng.

**Nguyên tắc trọng tâm:** dùng UTF-8 **tường minh**, lưu tài nguyên đúng encoding, cấu hình fallback rõ ràng, kiểm thử từng `Locale` bằng chuỗi Unicode chính xác, và không dùng kết quả hiển thị “trông có vẻ đúng” làm bằng chứng duy nhất.

### Những điều sẽ thực hành

- Tạo `messages.properties`, `messages_en.properties` và `messages_vi.properties`.
- Cấu hình `ResourceBundleMessageSource` và `ReloadableResourceBundleMessageSource` với `setDefaultEncoding("UTF-8")`.
- Tắt `fallbackToSystemLocale` để không phụ thuộc máy chủ.
- Kiểm tra file nguồn là UTF-8 hợp lệ, không có BOM ngoài ý muốn và có chuỗi dấu tiếng Việt thực sự.
- Kiểm tra chuỗi đầu ra theo `Locale.forLanguageTag("vi-VN")`, `en-US` và `fr-FR`.
- Kiểm tra thiếu key ở locale cụ thể dẫn tới root bundle, còn thiếu mọi bundle dẫn tới default message hoặc `NoSuchMessageException`.
- Giải thích Unicode normalization, MessageFormat, nguồn fallback riêng của ứng dụng, cache và kiểm thử CI.

## 2. MessageSource nằm ở đâu trong Spring Core Container?

`MessageSource` nhận message code + arguments + `Locale`, còn `ApplicationContext` triển khai API này và khi refresh tìm bean tên **`messageSource`** để cung cấp nguồn dịch. Một request `vi-VN` có thể tra biến thể `_vi_VN`, `_vi`, bundle gốc và parent source theo policy. Nếu vẫn thiếu, dùng explicit default hoặc ném `NoSuchMessageException` theo overload.

| Implementation | Cách nạp bundles | Liên quan đến kiểm chứng encoding |
| --- | --- | --- |
| `ResourceBundleMessageSource` | Java `ResourceBundle`/classpath, phụ thuộc classloader và một số nhánh JDK module path | Chú ý charset của Spring/JDK và byte assets |
| `ReloadableResourceBundleMessageSource` | Spring resources `classpath:`/`file:` | Chú ý default/file encoding, cache và thay đổi file ngoài JAR |

Bài này tập trung **chứng minh bytes → Java Unicode String → message lookup → HTTP/email rendering**, thay vì trình bày lại toàn bộ cách sử dụng MessageSource.

## 3. Hiểu đúng UTF-8, Unicode và file .properties

### 3.1. Unicode không phải là encoding của file

Unicode xác định các ký tự/mã điểm như `Đ`, `ă`, `ơ`, `ễ`. UTF-8 là **quy tắc biến những ký tự đó thành bytes** để ghi vào tệp. Một ký tự tiếng Việt có thể sử dụng nhiều bytes UTF-8.

Ví dụ chữ `Đ` và chữ `ắ` không phải cùng một byte; cần dùng đúng decoder. Nếu file được lưu bằng UTF-8 nhưng loader dùng ISO-8859-1, các bytes vẫn có thể được chuyển thành các ký tự Latin-1 hợp lệ nhưng cho ra **mojibake**.

~~~text
Tệp được lưu ở UTF-8
     │ bytes hợp lệ
     ├── UTF-8 decoder -> "Tiếng Việt"        (đúng)
     └── ISO-8859-1 decoder -> chữ bị lỗi dấu  (sai)
~~~

Đặc biệt, ISO-8859-1 có thể decode mọi giá trị byte trong phạm vi của nó. Vì thế **không ném exception không có nghĩa là đọc đúng tiếng Việt**. Phải so sánh nội dung Unicode sau khi decode.

### 3.2. Java Properties.load(InputStream) và Properties.load(Reader) khác nhau

Đừng dùng hai API này như thể tương đương:

~~~java
Properties a = new Properties();
try (InputStream in = resource.getInputStream()) {
    a.load(in);
}
~~~

Đây là snippet; overload `Properties.load(InputStream)` theo hợp đồng Java truyền thống giả định ISO-8859-1 và hỗ trợ Unicode escapes. Nó **không** tự suy từ file rằng nội dung là UTF-8.

Với UTF-8 explicit:

~~~java
Properties b = new Properties();
try (Reader reader = new InputStreamReader(
        resource.getInputStream(), StandardCharsets.UTF_8)) {
    b.load(reader);
}
~~~

Đây là snippet; cần imports `Properties`, `Reader`, `InputStreamReader`, `StandardCharsets` và Spring `Resource`. Reader giải mã bytes sang Unicode theo charset đã chọn.

**Lưu ý:** Spring `ResourceBundleMessageSource` và `ReloadableResourceBundleMessageSource` có cơ chế loader riêng. Không suy rằng chúng luôn dùng nguyên dạng `Properties.load(InputStream)`.

### 3.3. Java 9+/Java 17 PropertyResourceBundle

Theo JDK 17 Javadoc, `java.util.PropertyResourceBundle(InputStream)` mặc định thử **UTF-8**, nếu gặp input không thể decode thì có thể thử lại **ISO-8859-1**. Hành vi có thể bị điều khiển bởi JVM property `java.util.PropertyResourceBundle.encoding`, đọc khi class khởi tạo.

Điều này **không cho phép bỏ qua `setDefaultEncoding("UTF-8")` trên Spring MessageSource**:

- `ResourceBundleMessageSource` khi chạy trên classpath thường dùng cơ chế load cấu hình theo charset của Spring, và mặc định có thể là ISO-8859-1 theo Javadoc Spring 6.2.
- Trên JDK 9+ **module path** ở những nơi `ResourceBundle.Control` không được hỗ trợ, implementation có thể dùng `ResourceBundle.getBundle` và semantics của JDK, không giống classpath thông thường.
- `ReloadableResourceBundleMessageSource` sử dụng Spring properties persister và cấu hình encoding riêng.

Vì vậy, quy tắc portable cho project là **lưu file UTF-8, cấu hình `setDefaultEncoding("UTF-8")`, test thực tế với đúng kiểu đóng gói/runtime**.

### 3.4. File UTF-8 có BOM?

UTF-8 BOM là chuỗi byte `EF BB BF` ở đầu file. Một số editor có thể thêm BOM dù bản thân UTF-8 không cần nó. BOM có thể được xử lý khác nhau tùy parser/decoder; nếu không được loại bỏ, nó có thể bị xem là ký tự `U+FEFF` trước tên key đầu tiên.

Để tránh bất ngờ, lưu `.properties` **UTF-8 without BOM**. Trong kiểm thử, kiểm tra ba byte đầu để từ chối BOM ngoài ý muốn. Nếu hệ thống bắt buộc phải chấp nhận BOM vì nguồn bên ngoài, hãy dùng một bước đọc/normalize có hợp đồng rõ thay vì mặc định rằng mọi parser bỏ BOM.

### 3.5. Unicode escapes và tiếng Việt dạng chữ thật

Java properties hỗ trợ escape như `\u00E9`. Vì vậy một số dự án cũ lưu ký tự non-ASCII thành `\uXXXX`. Với toolchain hiện đại hỗ trợ UTF-8, có thể lưu trực tiếp:

~~~properties
welcome=Xin chào, {0}!
quality=Đường phố, Phượng, Trường, Nguyễn.
~~~

Cách lưu trực tiếp tăng khả năng đọc và review nội dung. Nhưng nếu có file legacy dùng Unicode escapes, kiểm tra công cụ dịch/chuyển đổi trước khi đổi encoding, tránh escape lại dấu backslash hoặc biến escape thành chuỗi literal.

Một số ký tự ngoài Basic Multilingual Plane cần **surrogate pair** trong dạng Java Unicode escape. Đừng tự viết converter chỉ xử lý từng Java `char` khi nội dung có emoji hoặc ký tự Unicode bổ sung; hãy dùng API hoạt động trên code points hoặc encoding UTF-8 chuẩn.

## 4. Tổ chức bundles: basename và Locale suffix

Trong `src/main/resources/i18n/`, đặt `messages.properties`, `messages_en.properties` và `messages_vi.properties`. Cấu hình cho hai implementation khác nhau:

```java
ResourceBundleMessageSource jdk = new ResourceBundleMessageSource();
jdk.setBasename("i18n/messages");
jdk.setDefaultEncoding("UTF-8");

ReloadableResourceBundleMessageSource spring =
        new ReloadableResourceBundleMessageSource();
spring.setBasename("classpath:i18n/messages");
spring.setDefaultEncoding("UTF-8");
```

Các snippet này cần imports Spring. Basename không có `_vi` hay `.properties`; suffix cho `Locale.forLanguageTag("vi-VN")` dùng underscore trong filename: `messages_vi_VN.properties`. Nếu thiếu region bundle có thể lấy `messages_vi.properties`; nếu bản dịch thiếu key thì root/parent/default tùy policy. **Locale có dấu gạch ngang trong BCP 47 tag, nhưng bundle filename dùng dấu gạch dưới**. Phần còn lại của bài kiểm tra bytes và fallback thật trên các locale cụ thể.

## 5. Fallback là gì? Không phải lỗi encoding

Giả sử messages có ba file:

~~~properties
# messages.properties
welcome=Hello, {0}!
status=Status: {0}
~~~

~~~properties
# messages_en.properties
welcome=Hello, {0}!
~~~

~~~properties
# messages_vi.properties
welcome=Xin chào, {0}!
quality=Đường phố, Phượng, Trường, Nguyễn.
~~~

Khi gọi:

~~~java
source.getMessage(
        "welcome", new Object[]{"Lan"},
        Locale.forLanguageTag("vi-VN"));
~~~

nếu không có file `messages_vi_VN.properties`, message có thể được lấy từ `messages_vi.properties` và kết quả `Xin chào, Lan!`.

Khi gọi `status` ở `vi-VN`, file tiếng Việt không có key `status`. Root bundle có `status=Status: {0}`, nên kết quả:

~~~text
Status: READY
~~~

Đây là **fallback hợp lệ** theo thiết kế ví dụ, không phải dấu hiệu loader đã đọc tiếng Việt sai encoding.

### Ba tình huống khác nhau

| Hiện tượng | Nguyên nhân khả dĩ | Điều cần kiểm tra |
| --- | --- | --- |
| `Xin chÃ o, Lan!` | UTF-8 bytes bị giải mã sai | Encoding và build pipeline |
| `Hello, Lan!` khi hỏi `vi-VN` | Fallback root/en hoặc file `vi` không được tìm | Locale, basename, classpath, keys |
| `welcome` hiện nguyên như key | `useCodeAsDefaultMessage` hoặc UI fallback khi thiếu code | Missing-key policy và tests |

**Đừng sửa encoding khi lỗi thật là không tìm thấy file**, hoặc tắt fallback khi lỗi thật là mojibake. Trước hết xác định layer gây sai.

## 6. Tắt fallback về Locale mặc định của JVM

Server có thể chạy với `Locale.getDefault()` khác nhau trên laptop, CI, container hoặc cloud VM. `MessageSource` mặc định có thể cho phép fallback về system Locale.

Để kết quả ổn định khi không muốn dùng OS locale:

~~~java
source.setFallbackToSystemLocale(false);
~~~

Theo Javadoc Spring 6.2, khi tắt flag này và không thiết lập default locale khác, fallback locale system được loại bỏ; **root bundle** vẫn có thể cung cấp nội dung chung.

### Ví dụ sai kỳ vọng

Một người dùng chọn `fr-FR`. Ứng dụng không có file French. Laptop của developer có system locale `vi-VN` nên người dùng nhìn thấy tiếng Việt; production JVM lại có `en-US` nên người dùng thấy tiếng Anh. Nếu yêu cầu sản phẩm là **French chưa hỗ trợ thì luôn dùng bản root tiếng Anh**, hãy đặt policy explicit và test.

### setDefaultLocale khác setFallbackToSystemLocale

`AbstractResourceBasedMessageSource.setDefaultLocale(Locale)` cho phép đặt một locale dự phòng riêng của ứng dụng, thay thế cách dựa vào system default locale theo cấu hình. Điều này khác với:

- Requested Locale: do method `getMessage` nhận vào.
- JVM default locale: `Locale.getDefault()`, có thể thay giữa môi trường.
- Root properties file: `messages.properties`, không có suffix locale.
- Parent message source: một nguồn dự phòng khác trong context hierarchy.

Không tự suy rằng `setDefaultLocale(Locale.ENGLISH)` và `setFallbackToSystemLocale(false)` đồng nghĩa. Chọn **một chính sách dễ giải thích**, đặt rõ trong config và kiểm thử thứ tự root/default/parent dưới implementation đang dùng.

### Không gọi Locale.setDefault theo từng request

`Locale.setDefault(Locale)` thay trạng thái toàn JVM. Trong web server phục vụ nhiều người dùng đồng thời, thay global locale cho mỗi request có thể làm request khác bị ảnh hưởng. Hãy truyền Locale dưới dạng tham số hoặc dùng locale resolution được framework quản lý đúng context; tránh phụ thuộc ThreadLocal ngoài phạm vi mà framework bảo đảm.

## 7. Bốn lớp kiểm chứng tiếng Việt

Một chương trình kiểm thử nên tách các lớp như sau.

### Lớp A: byte và charset của file nguồn

Kiểm tra:

- File `messages_vi.properties` tồn tại trong classpath thực.
- Ba byte đầu không phải BOM nếu project yêu cầu UTF-8 without BOM.
- Decoder UTF-8 dùng `CodingErrorAction.REPORT` không gặp malformed/unmappable sequence.
- Kết quả giải mã chứa một chuỗi có nhiều dấu tiếng Việt **chính xác theo kỳ vọng**.

Tại sao cần kiểm tra chuỗi? Một file ASCII-only có thể decode UTF-8 hợp lệ nhưng chưa chứng minh nội dung tiếng Việt được giữ nguyên.

### Lớp B: MessageSource parser

Tạo `ResourceBundleMessageSource` và `ReloadableResourceBundleMessageSource` đã `setDefaultEncoding("UTF-8")`, gọi `getMessage`. Nếu file bytes đúng nhưng output sai, xem lại basename, suffix, classloader hoặc source precedence.

### Lớp C: fallback theo Locale

Với các trường hợp `vi-VN`, `en-US`, `fr-FR` và `setFallbackToSystemLocale(false)`, test kết quả rõ. Đặc biệt, một key **chỉ có ở root** phải trả root text ở mọi Locale nếu không bị override, còn key thiếu mọi nguồn sẽ đi theo default/exception.

### Lớp D: rendering cuối cùng

Nếu `MessageSource` trả String đúng nhưng trình duyệt/email lại hiển thị sai, vấn đề có thể ở HTTP `Content-Type; charset`, HTML meta, mail MIME, template engine hoặc database connection. Không quy lỗi cho Spring MessageSource khi bản thân `getMessage` đã trả Unicode chuẩn.

## 8. Unicode normalization: NFC và NFD

Có những chuỗi tiếng Việt nhìn giống nhau nhưng Unicode representation khác nhau: một ký tự có thể là **precomposed code point** hoặc tổ hợp base character với **combining mark**.

Ví dụ:

~~~java
String composed = "ế";
String decomposed = Normalizer.normalize(
        composed, Normalizer.Form.NFD);

boolean sameText = composed.equals(decomposed); // thường false
boolean sameNfc = Normalizer.normalize(
        composed, Normalizer.Form.NFC).equals(
        Normalizer.normalize(decomposed, Normalizer.Form.NFC));
~~~

Đây là snippet, cần import `java.text.Normalizer`. `sameNfc` là true vì hai chuỗi được đưa về cùng dạng normalization.

### Vì sao cần test NFC?

- Translator dùng bộ gõ hoặc editor khác nhau có thể tạo dữ liệu NFD/NFC.
- So sánh String byte/code-unit exact có thể fail dù UI nhìn giống nhau.
- Hệ thống search/sort/integration APIs có thể xử lý normalization khác nhau.
- Nếu test cố định kỳ vọng Unicode **NFC**, cần enforce/normalize ở input pipeline và trong assets theo chính sách.

Tuy nhiên, đừng áp `Normalizer.normalize` cho mọi input rồi coi như đã xử lý tất cả security/Unicode confusables. NFC không tự phòng homoglyph spoofing hoặc bảo đảm kết quả hợp lệ cho domain cụ thể.

### Cách đặt contract

- File translation trong repository lưu UTF-8 **không BOM**.
- Nếu team chọn NFC, enforce NFC trên content bằng CI.
- Output user-facing có thể giữ Unicode chuẩn sau khi MessageSource resolve.
- Không tự trim/cắt ký tự Unicode khi địa phương hóa nếu nó thay nghĩa.
- So sánh `String` trong tests dựa trên dạng normalization thống nhất.

## 9. MessageFormat còn có thể gây lỗi mà không liên quan encoding

`MessageSource` có thể thay `{0}`, `{1}` theo `java.text.MessageFormat`:

~~~properties
welcome=Xin chào, {0}!
order=Đơn {0} có {1} sản phẩm.
~~~

~~~java
String value = source.getMessage(
        "order", new Object[]{"A-101", 3},
        Locale.forLanguageTag("vi-VN"));
~~~

Kết quả:

~~~text
Đơn A-101 có 3 sản phẩm.
~~~

### Ký tự apostrophe

Trong MessageFormat, dấu nháy đơn `'` có ý nghĩa **quote syntax**. Nếu muốn hiển thị một apostrophe trong pattern có argument:

~~~properties
warning=Don''t close {0}.
~~~

Kết quả sau khi format với argument `window`:

~~~text
Don't close window.
~~~

Không nên nhận mọi hiện tượng dấu nháy mất là lỗi UTF-8. Cần test formatter riêng.

### Dấu ngoặc nhọn

`{0}` là placeholder; nếu bản dịch cần hiển thị các dấu ngoặc theo nghĩa literal, phải tuân cú pháp quoting/escaping của MessageFormat. Translator không nên tự ý thay `{0}` thành `{1}` mà không kiểm tra tập arguments hiện có.

### Number/date formatting

`{0,number}` hoặc `{0,date,short}` dùng Locale để định dạng giá trị. Kết quả có thể khác theo locale, timezone/CLDR/JDK version. Nếu test muốn stdout không thay đổi giữa các máy, dùng string args trong demo hoặc cố định các tham số định dạng/UTC/timezone.

## 10. Chương trình Java 17 tự chứa: kiểm tra UTF-8, hai MessageSource và fallback

Chương trình dưới đây dùng **Java 17**, **Maven** và **Spring Framework 6.2.19** thuần. Nó không dùng Spring Boot, web server hay network endpoint.

Nó thực hiện:

1. Đọc trực tiếp bytes của `messages_vi.properties` bằng `ClassPathResource`.
2. Từ chối UTF-8 BOM và lỗi UTF-8 decoding.
3. So sánh một chuỗi nguyên bản nhiều dấu tiếng Việt với kỳ vọng.
4. Tạo hai ApplicationContext độc lập có bean `messageSource`, mỗi bean là một implementation.
5. Kiểm tra lời chào tiếng Việt, tiếng Anh, fallback tiếng Pháp về root, fallback key `status` về root.
6. Kiểm tra default message, missing-code exception và Unicode NFC.
7. Chỉ in output sau khi những assertions nội bộ đều thành công.

### Cấu trúc thư mục

~~~text
spring-vi-encoding-fallback/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── example/
        │       └── VietnameseMessageDemo.java
        └── resources/
            └── i18n/
                ├── messages.properties
                ├── messages_en.properties
                └── messages_vi.properties
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-vi-encoding-fallback</artifactId>
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

### src/main/resources/i18n/messages.properties

~~~properties
welcome=Hello, {0}!
status=Status: {0}
~~~

### src/main/resources/i18n/messages_en.properties

~~~properties
welcome=Hello, {0}!
~~~

### src/main/resources/i18n/messages_vi.properties

~~~properties
welcome=Xin chào, {0}!
quality=Đường phố, Phượng, Trường, Nguyễn.
~~~

**Quan trọng:** tạo ba file này bằng **UTF-8 without BOM**; không đặt code fences Markdown vào nội dung file. Khi copy bằng editor, tránh smart quotes hoặc tool tự chuyển encoding.

### src/main/java/example/VietnameseMessageDemo.java

~~~java
package example;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.io.ClassPathResource;

public final class VietnameseMessageDemo {

    private static final Locale VI = Locale.forLanguageTag("vi-VN");
    private static final Locale EN = Locale.forLanguageTag("en-US");
    private static final Locale FR = Locale.forLanguageTag("fr-FR");
    private static final String EXACT =
            "Đường phố, Phượng, Trường, Nguyễn.";

    public static void main(String[] args) throws IOException {
        checkUtf8File();
        run("resource-bundle", JdkConfig.class);
        run("reloadable", SpringConfig.class);
    }

    private static void checkUtf8File() throws IOException {
        byte[] bytes;
        var resource = new ClassPathResource(
                "i18n/messages_vi.properties");

        try (InputStream in = resource.getInputStream()) {
            bytes = in.readAllBytes();
        }

        boolean bom = bytes.length >= 3
                && (bytes[0] & 0xff) == 0xef
                && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf;

        if (bom) {
            throw new IllegalStateException("UTF-8 BOM not permitted");
        }

        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalStateException(
                    "Invalid UTF-8 bytes", ex);
        }

        if (!text.contains("quality=" + EXACT)) {
            throw new IllegalStateException(
                    "Vietnamese text was changed");
        }

        System.out.println("UTF-8 source: PASS");
    }

    private static void run(
            String label, Class<?> configurationClass) {

        try (var context =
                     new AnnotationConfigApplicationContext(
                             configurationClass)) {

            MessageSource messages = context.getBean(
                    "messageSource", MessageSource.class);

            String vi = messages.getMessage(
                    "welcome", new Object[]{"Lan"}, VI);
            String en = messages.getMessage(
                    "welcome", new Object[]{"Lan"}, EN);
            String fr = messages.getMessage(
                    "welcome", new Object[]{"Lan"}, FR);
            String root = messages.getMessage(
                    "status", new Object[]{"READY"}, VI);
            String quality = messages.getMessage(
                    "quality", null, VI);
            String missingDefault = messages.getMessage(
                    "unknown.code", null, "Dự phòng", VI);

            check("Xin chào, Lan!", vi);
            check("Hello, Lan!", en);
            check("Hello, Lan!", fr);
            check("Status: READY", root);
            check(EXACT, quality);
            check("Dự phòng", missingDefault);

            boolean noSuchMessage = false;
            try {
                messages.getMessage("unknown.code", null, VI);
            } catch (NoSuchMessageException ex) {
                noSuchMessage = true;
            }
            if (!noSuchMessage) {
                throw new IllegalStateException(
                        "Expected NoSuchMessageException");
            }

            boolean nfc = Normalizer.isNormalized(
                    quality, Normalizer.Form.NFC);
            if (!nfc) {
                throw new IllegalStateException(
                        "Vietnamese content is not NFC");
            }

            System.out.println(label + " vi: " + vi);
            System.out.println(label + " en: " + en);
            System.out.println(label + " fr fallback: " + fr);
            System.out.println(label + " root key: " + root);
            System.out.println(label + " accents: " + quality);
            System.out.println(label + " missing default: "
                    + missingDefault);
            System.out.println(label + " missing exception: "
                    + noSuchMessage);
            System.out.println(label + " NFC: " + nfc);
        }
    }

    private static void check(
            String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "Expected [" + expected
                    + "], actual [" + actual + "]");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class JdkConfig {

        @Bean("messageSource")
        public static MessageSource messages() {
            var source = new ResourceBundleMessageSource();
            source.setBasename("i18n/messages");
            source.setDefaultEncoding("UTF-8");
            source.setFallbackToSystemLocale(false);
            return source;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class SpringConfig {

        @Bean("messageSource")
        public static MessageSource messages() {
            var source = new ReloadableResourceBundleMessageSource();
            source.setBasename("classpath:i18n/messages");
            source.setDefaultEncoding("UTF-8");
            source.setFallbackToSystemLocale(false);
            source.setCacheSeconds(-1);
            return source;
        }
    }
}
~~~

### Chạy

Từ thư mục `spring-vi-encoding-fallback`, có Java 17 và Maven:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.VietnameseMessageDemo
~~~

Stdout do **chính chương trình** tạo ra, không kể những thông báo của Maven hoặc logging trên classpath:

~~~text
UTF-8 source: PASS
resource-bundle vi: Xin chào, Lan!
resource-bundle en: Hello, Lan!
resource-bundle fr fallback: Hello, Lan!
resource-bundle root key: Status: READY
resource-bundle accents: Đường phố, Phượng, Trường, Nguyễn.
resource-bundle missing default: Dự phòng
resource-bundle missing exception: true
resource-bundle NFC: true
reloadable vi: Xin chào, Lan!
reloadable en: Hello, Lan!
reloadable fr fallback: Hello, Lan!
reloadable root key: Status: READY
reloadable accents: Đường phố, Phượng, Trường, Nguyễn.
reloadable missing default: Dự phòng
reloadable missing exception: true
reloadable NFC: true
~~~

### Giải thích vì sao từng phép kiểm tra có ý nghĩa

**UTF-8 source: PASS**

`checkUtf8File()` đọc raw bytes từ classpath thực, từ chối BOM và dùng `CharsetDecoder` với `CodingErrorAction.REPORT`. Nó cũng kiểm tra có đúng dòng `quality=...` sau khi UTF-8 decode. Nếu resource bị lưu theo charset sai, bị mất dấu hoặc đã bị resource filtering sửa nội dung, kiểm tra này có cơ hội bắt lỗi trước khi vào MessageSource.

**resource-bundle vi/en**

`ResourceBundleMessageSource` dùng basename `i18n/messages`. Với `vi-VN`, fallback từ variant specific tới `messages_vi.properties` và dùng value `Xin chào, {0}!`. Với `en-US`, file tiếng Anh cung cấp `Hello, {0}!`.

**fr fallback**

Không có `messages_fr_FR.properties` hoặc `messages_fr.properties`. Vì `fallbackToSystemLocale=false` và root bundle có key `welcome`, kết quả là `Hello, Lan!`, không phụ thuộc ngôn ngữ của JVM.

**root key**

`status` chỉ ở `messages.properties` nên khi gọi với Vietnamese locale vẫn trả `Status: READY`. Đây là mô hình fallback theo key chứ không phải lỗi encoding.

**accents và NFC**

`quality` nằm ở bundle Vietnamese và chứa nhiều chữ có dấu. Assert exact Unicode ngăn lỗi mojibake khó phát hiện bằng mắt. Kiểm tra `Normalizer.Form.NFC` bảo đảm file và expected text tuân chính sách Unicode normalization đã chọn.

**missing default và exception**

Với code hoàn toàn không tồn tại, overload có default trả `Dự phòng`. Overload strict ném `NoSuchMessageException`, được chương trình bắt và in `true`.

**reloadable**

Source thứ hai dùng Spring `classpath:` location nhưng cùng UTF-8 policy. Nó cho kết quả giống source JDK trong ví dụ bất biến này. Code **không tuyên bố đã kiểm thử runtime hot reload** trong JAR; hot reload cần test riêng với external files.

### Điều kiện để đầu ra chính xác

- File names, basenames và contents giống hệt các đoạn đã cung cấp.
- Không có resource bundle khác cùng basename ưu tiên hơn trên classpath.
- Files được lưu UTF-8 không BOM và không bị Maven filtering thay đổi.
- Cấu hình Java dùng đúng Spring Framework 6.2.x API, không có BeanFactoryPostProcessor khác sửa message source.
- `setFallbackToSystemLocale(false)` và root bundle tồn tại đúng path.
- Không thêm file `messages_fr.properties` hoặc `messages_vi_VN.properties` với nội dung khác.
- Chỉ tính stdout của `System.out.println`.

Nếu một assert fail, chương trình dừng với exception; đây là **phản hồi cần thiết khi kiểm chứng thất bại**, không phải lỗi phải che bằng default.

## 11. Phân tích từ raw bytes tới chữ Việt đúng

Hãy hình dung từng bước của pipeline:

~~~text
Developer nhập:
quality=Đường phố, Phượng, Trường, Nguyễn.
        │
        ▼
Editor lưu .properties ở UTF-8 không BOM
        │
        ▼
Maven copy resources vào target/classes hoặc JAR
        │
        ▼
MessageSource đọc file bằng UTF-8
        │
        ▼
Java String chứa đúng Unicode code points
        │
        ▼
MessageFormat nếu key có arguments
        │
        ▼
Locale fallback/Code resolution chọn message đúng
        │
        ▼
Render ở terminal/HTTP/email có encoding tương thích
~~~

Mỗi bước có thể gây lỗi độc lập:

- Nếu raw bytes bị sai ngay từ editor, sửa file nguồn.
- Nếu file nguồn đúng nhưng `target/classes` sai, xem resource filtering/build.
- Nếu file bytes đúng nhưng MessageSource trả mojibake, kiểm tra loader encoding.
- Nếu trả tiếng Anh hợp lệ thay vì tiếng Việt, kiểm tra locale/basename/fallback.
- Nếu Java String đúng nhưng UI sai, kiểm tra HTTP response/HTML/email charset và client decoding.
- Nếu chỉ một câu bị mất apostrophe hoặc các giá trị `{0}` không được chèn, kiểm tra MessageFormat.

Không nên dùng `System.out.println` hiển thị trên terminal đang cấu hình sai charset làm nguồn chứng cứ duy nhất; unit test so sánh Java strings ở runtime là đáng tin hơn.

## 12. Maven resource filtering: lưu ý tiếng Việt

Maven mặc định copy `src/main/resources` vào classpath. Khi bật **resource filtering**, plugin có thể thay placeholders hoặc đọc/ghi file theo encoding tùy cấu hình.

Ví dụ cấu hình encoding có chủ đích:

~~~xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
</properties>
~~~

Các thuộc tính này hữu ích, nhưng **không tự đảm bảo mọi resources plugin và external translator tool đều dùng đúng charset**. Khi project bật filtering, hãy xác nhận tham số `encoding` của maven-resources-plugin theo version và test output sau build.

### Nguy cơ với placeholder syntax

Tệp `.properties` của MessageSource có `{0}`, `{1}` cho `MessageFormat`. Một số filtering/tool/template systems có cú pháp placeholder riêng như `${name}` hoặc `@...@`. Nếu không thống nhất escape/config, build tool có thể thay ngoài ý định nội dung các bản dịch.

Không dùng user-facing translations làm nơi mang deployment secrets để rồi áp filtering tràn lan. Nếu cần tách các biến build/deployment, xác định rõ file nào được filter, file nào copy nguyên văn.

### Một kiểm thử build output

Sau `mvn resources:resources` hoặc `mvn test`, chạy chương trình từ classpath build như demo; nó dùng `ClassPathResource` nên kiểm tra **file đã copy** thay vì chỉ đọc source tree. Trong CI có thể thêm script so sánh byte/normalized Unicode của resources gốc với bản trong `target/classes` nếu yêu cầu tài nguyên bất biến.

## 13. Kiểm tra toàn bộ locale bằng một ma trận

Bảng này phù hợp với ba files ở demo:

| Requested Locale | Key `welcome` | Key `quality` | Key `status` | Ý nghĩa |
| --- | --- | --- | --- | --- |
| `vi-VN` | Xin chào, Lan! | Có tiếng Việt đầy đủ dấu | Status: READY | `vi` match; `status` fallback root |
| `vi` | Xin chào, Lan! | Có tiếng Việt đầy đủ dấu | Status: READY | Language bundle trực tiếp |
| `en-US` | Hello, Lan! | Không có bản riêng | Status: READY | `en` match; `quality` chỉ ở `vi` nên không tự được lấy |
| `fr-FR` | Hello, Lan! | Không có bản riêng | Status: READY | `welcome`/`status` lấy root |
| Locale tùy ý chưa hỗ trợ | Theo root nếu key có | Không có | Theo root | Policy root/default/exception |

Với `quality` ở `en-US` hoặc `fr-FR`, vì key **không nằm trong root**, một overload strict có thể ném `NoSuchMessageException`. Nếu UI bắt buộc luôn có nội dung cho mọi ngôn ngữ, đặt key ở root hoặc cung cấp default message rõ ràng. Đừng tự kỳ vọng framework fallback sang tiếng Việt chỉ vì Vietnamese bundle có key.

### Vì sao test cả `vi` và `vi-VN`?

Nếu team chỉ có một file `messages_vi.properties`, cả hai phải giải quyết cùng bản dịch tiếng Việt theo quy tắc language fallback. Nếu `vi` pass nhưng `vi-VN` fail, kiểm tra đã đặt nhầm suffix, default locale policy, classloader, hoặc một region-specific file đang ghi đè.

### Vì sao cần test locale không được hỗ trợ?

Đây là trường hợp dễ lộ system-locale fallback ngầm. Nếu developer dùng JVM tiếng Việt, `fr-FR` có thể bất ngờ nhận bản tiếng Việt khi thiếu French bundle. Kiểm thử locale `fr-FR` cùng chính sách `setFallbackToSystemLocale(false)` xác nhận ứng dụng chọn root fallback đã định nghĩa.

## 14. Fallback key, fallback locale, default message: không phải một

Ba khái niệm thường bị nhầm:

1. **Fallback theo key trong bundle hierarchy:** requested locale tìm không thấy key ở file cụ thể nhưng root bundle chứa key đó.
2. **Fallback sang một Locale khác:** ứng dụng được cấu hình với default/system locale, có thể thử các bundles thuộc locale thay thế theo implementation.
3. **Fallback text theo phương thức:** caller truyền `defaultMessage` vào overload `getMessage(...)`; được dùng khi các nguồn message không trả được code.

Ví dụ:

~~~java
String rootValue = messages.getMessage(
        "status", new Object[]{"READY"}, VI);

String defaultValue = messages.getMessage(
        "unknown.code", null, "Dự phòng", VI);
~~~

`rootValue` đến từ **tệp root**. `defaultValue` là **chuỗi fallback do caller cấp**, không phải một key có sẵn ở root.

### useCodeAsDefaultMessage

Cấu hình:

~~~java
source.setUseCodeAsDefaultMessage(true);
~~~

có thể khiến key không tồn tại được trả dưới dạng chính tên key, ví dụ `"unknown.code"`. Điều này làm một số test tưởng là success dù thật ra translation thiếu. Khi muốn strict coverage, không bật tùy tiện ở production; hãy dùng test/CI để phát hiện missing code.

### Parent MessageSource

Trong cấu trúc parent-child ApplicationContexts, một child có thể tra parent message source nếu thiếu key. Parent có thể có bản dịch chung; child có bundle theo module. Cần test precedence khi hai nguồn đều có một code và tránh default-value policy làm che parent lookup ngoài ý muốn.

## 15. Spring Boot, HTTP response và email

Trong Spring Boot, có những conventions về `spring.messages.basename`, `spring.messages.encoding`, fallback behavior và auto-configured MessageSource. Không nên sao chép nguyên cấu hình plain Spring rồi mặc định mọi precedence/bean registration vẫn giống hệt Boot. Nếu ứng dụng Boot tự định nghĩa bean `messageSource`, nó có thể thay/bỏ qua auto-configuration tương ứng; hãy inspect cấu hình thực.

### HTTP

Một controller có thể trả Java String đúng nhưng response header/client parser lại diễn giải bytes sai charset. Với JSON, cần bảo đảm serializer/HTTP stack và content type chuẩn. Với HTML, bảo đảm charset theo response và document metadata. Không “sửa” lỗi transport bằng cách thay translation file từ UTF-8 sang Latin-1.

### Email

Nếu gửi email tiếng Việt, body/subject cần MIME charset phù hợp như UTF-8. `MessageSource` chỉ trả text Unicode; mail library chịu trách nhiệm encode nội dung thành MIME bytes. Tests cần kiểm tra thư nhận cuối cùng, không chỉ String trong service.

### Database

Nếu có một hệ thống dịch tải message từ database thay vì .properties, phải kiểm tra column collation/encoding, client driver và database connection. Nhưng cấu hình trực tiếp `ResourceBundleMessageSource` không tự biết đến bảng DB; đó là integration riêng cần thiết kế.

## 16. Kiểm thử âm tính: chủ động gây lỗi để xác nhận test có ích

Một bộ kiểm thử tốt phải fail khi có vấn đề thật. Có thể thử trên bản copy của project demo trong môi trường test:

### Ca 1: Xóa dòng Vietnamese welcome

Xóa `welcome` khỏi `messages_vi.properties` rồi chạy. Vì root có `welcome=Hello, {0}!`, assertion tiếng Việt fail: test phát hiện **fallback ngoài mong muốn**, không phải lỗi charset.

### Ca 2: Thay một ký tự thành mojibake

Sửa `quality` thành text bị hỏng và chạy. `checkUtf8File()` có thể vẫn decode được bytes nếu mojibake đã được lưu như UTF-8 hợp lệ, nhưng kiểm tra **exact content** sẽ fail. Đây là lý do chỉ kiểm tra decoder success là chưa đủ.

### Ca 3: Lưu properties với BOM

Thêm UTF-8 BOM vào đầu `messages_vi.properties`. Byte-level check phải fail trước khi tạo context. Đây là một policy test, không khẳng định mọi parser Spring đều không chấp nhận BOM.

### Ca 4: Bỏ root bundle

Xóa `messages.properties`. Với requested `fr-FR` không có French bundle, lời gọi `welcome` sẽ không lấy được root text và test fail. Đây là dấu hiệu **missing fallback resource**.

### Ca 5: Tắt setFallbackToSystemLocale(false)

Dùng default system fallback và chạy test trên JVM có các `user.language`/`user.country` khác nhau. Một số fallback cases có thể đổi. Lưu ý root bundle tồn tại có thể che sự khác biệt; để test chính sách JVM fallback, tạo một fixture riêng với bundles chứa keys/variants thích hợp, không làm sai assumptions của demo.

### Ca 6: Sai MessageFormat

Sửa `welcome=Xin chào, {0}!` thành `welcome=Xin chào, {1}!`. Assertion sẽ fail, vì args chỉ chứa một phần tử hoặc output không được thay đúng. Đây không phải lỗi UTF-8.

### Ca 7: Thay đổi normalization

Chuyển một chuỗi NFC thành NFD. Nó có thể nhìn vẫn đúng trên màn hình nhưng `Normalizer.isNormalized(..., NFC)` có thể fail. CI nên xử lý theo policy team đã chọn.

## 17. Những nguyên tắc khi reload bản dịch tiếng Việt

Với `ReloadableResourceBundleMessageSource`:

~~~java
source.setBasename("file:/opt/app/i18n/messages");
source.setDefaultEncoding("UTF-8");
source.setFallbackToSystemLocale(false);
source.setCacheSeconds(5);
~~~

Đây là snippet. File ngoài JAR cần tồn tại và JVM có quyền đọc. Tải lại không phải phần bắt buộc để xác minh mã hóa, nhưng khi translations được chỉnh ở runtime thì phải giữ cùng UTF-8/fallback policy cho **mọi phiên bản** file.

### Trình tự publish an toàn

1. Export translation files từ hệ thống dịch với UTF-8 no BOM.
2. Validate Unicode, keys, placeholders, NFC policy.
3. Ghi vào tệp tạm và chuyển thành tên chính thức bằng atomic rename khi filesystem cho phép.
4. Bảo đảm timestamp thay đổi để cơ chế refresh nhận biết.
5. Xác minh từng locale trên một staging JVM với đúng file mount path.
6. Triển khai đồng bộ nhiều replicas, lưu version/revision để rollback.
7. Kiểm tra cache interval và quan sát kết quả sau cache refresh.

**Không dựa vào `classpath:` trong executable JAR để hứa hot reload.** JAR/classloader resource caching có thể làm cập nhật không đáng tin cậy. Với external file, `cacheSeconds` là khoảng kiểm tra tương đối, không phải SLA cập nhật tức thì.

### Concurrency

Nếu đang ghi file không atomically, một request có thể đọc bản chưa hoàn chỉnh. Cần tránh tình huống này bằng publish policy; nếu nhiều keys trong cùng tập dịch phải nhất quán, deploy theo revision thay vì ghi rời rạc tùy ý.

## 18. Chẩn đoán một lỗi i18n có hệ thống

Sử dụng chuỗi kiểm tra ngắn sau khi một message tiếng Việt bị lỗi.

| Câu hỏi | Dấu hiệu | Hành động |
| --- | --- | --- |
| File nguồn bytes đúng? | Editor xem đã hỏng, BOM, invalid UTF-8 | Sửa export/encoding |
| File build output đúng? | Source đúng nhưng `target/classes` sai | Kiểm tra Maven resources/filtering |
| Spring đang tìm đúng bundle? | Không có Vietnamese dù bytes đúng | Xác nhận basename, suffix, packaging |
| Loader dùng UTF-8? | Mojibake ngay ở `getMessage` | Đặt `setDefaultEncoding("UTF-8")` |
| Locale đúng? | Tất cả người dùng nhìn tiếng Anh | Log locale identifier, không log dữ liệu cá nhân |
| Fallback system có bật? | Dev/prod khác nhau | `setFallbackToSystemLocale(false)` khi policy yêu cầu |
| Key tồn tại nơi nào? | Root trả tiếng Anh | So sánh key coverage các bundles |
| MessageFormat đúng? | `{0}` còn nguyên/dấu nháy mất | Test args và quoting |
| Cache đã refresh? | Tệp mới nhưng app vẫn trả cũ | Kiểm tra location và cache interval |
| UI/transport charset đúng? | Java String đúng, browser sai | Xem HTTP/email/HTML charset |

### Log thế nào để không lộ nội dung nhạy cảm?

Log message code, requested Locale, basename/source version và loại lỗi; tránh log toàn bộ message contents nếu chứa dữ liệu người dùng. Nếu message args có họ tên, email hoặc nội dung đơn hàng, không dump chúng ra production logs chỉ để chẩn đoán.

### Một lỗi do shell

Trên Windows terminal có thể hiển thị ký tự sai vì code page/font dù Java String thực tế đúng. Hãy dùng assertions/Unicode code points hoặc output file UTF-8 thay vì chỉ nhìn terminal. Tránh tự suy rằng Maven/spring đã đọc sai nếu một terminal không hỗ trợ glyph tiếng Việt.

## 19. CI gate: tiêu chí chất lượng bản dịch

Một pipeline có thể kiểm tra:

| Tiêu chí | Kiểm tra |
| --- | --- |
| UTF-8 | Decode strict, không malformed bytes |
| BOM | Không có BOM ngoài ý muốn |
| Unicode form | NFC hoặc chính sách đã chọn |
| Key coverage | Bản địa hóa các codes bắt buộc |
| MessageFormat | Placeholders đúng tên/chỉ số/format |
| Fallback | Root có các keys thiết yếu |
| Locale mapping | vi-VN, vi, en-US, fr-FR ... |
| Packaged assets | Resources có trong JAR/classpath build |
| Encoding consistency | UTF-8 từ repository tới artifact |
| Regression | So sánh output Vietnamese chứa nhiều dấu |
| Security | Escape nội dung ở rendering layer |
| Cache | Không dùng hot reload giả với resource JAR |

Nếu một locale thiếu bản dịch cho một key tùy chọn, fallback về root có thể là ý đồ sản phẩm. CI cần phân biệt **missing được cho phép** và **missing không chấp nhận** theo mức độ quan trọng. Đừng bắt 100% key coverage mà không có quyết định sản phẩm; cũng đừng để mọi key thiếu âm thầm fallback đến lúc người dùng báo lỗi.

### Các boundary cần chú ý

- Dấu tiếng Việt phức hợp như `ắ`, `ễ`, `ỵ`, `Đ`.
- Tên riêng có dấu: `Nguyễn`, `Đặng`, `Thảo`.
- Dấu nháy đơn trong MessageFormat.
- Ký tự `{0}` và `{1}` trong nhiều locale.
- Ký tự ngoặc, ký hiệu %, &, và escape backslash trong properties.
- Khoảng trắng đầu/cuối và newline quan trọng trong UI.
- Ký tự ngoài BMP nếu messages chứa emoji.
- Tài nguyên được build từ nhiều repos/translations systems.

## 20. Câu hỏi tự kiểm tra

**Câu 1.** Một file `messages_vi.properties` được lưu UTF-8, tại sao Spring vẫn có thể hiển thị mojibake?

**Trả lời:** Vì loader có thể đang dùng encoding khác, hoặc build output bị chuyển mã. Cần kiểm tra raw bytes và `setDefaultEncoding("UTF-8")` của message source, không chỉ editor.

**Câu 2.** Tại sao `Properties.load(InputStream)` khác `Properties.load(Reader)`?

**Trả lời:** InputStream overload của java.util.Properties theo contract truyền thống đọc ISO-8859-1; Reader cho phép caller chọn decoder UTF-8 tường minh.

**Câu 3.** Trên Java 17, `PropertyResourceBundle(InputStream)` có luôn giống hành vi của mọi Spring MessageSource không?

**Trả lời:** Không. JDK17 hỗ trợ UTF-8 với fallback Latin-1 theo policy, nhưng Spring implementations có logic load/encoding riêng. Kiểm tra Javadoc đúng implementation/runtime.

**Câu 4.** Với `vi-VN` và file `messages_vi.properties` nhưng không có `messages_vi_VN.properties`, Spring có thể trả bản tiếng Việt không?

**Trả lời:** Có; resource bundle lookup hỗ trợ fallback từ locale cụ thể về language-level bundle.

**Câu 5.** Vì sao `status` ở bundle root có thể được trả khi requested Locale là `vi-VN`?

**Trả lời:** Nếu file `vi` thiếu key và root file có code tương ứng, root có thể cung cấp fallback theo bundle hierarchy.

**Câu 6.** `setFallbackToSystemLocale(false)` có loại bỏ luôn `messages.properties` không?

**Trả lời:** Không; nó loại nhánh system-locale fallback, root bundle vẫn có thể là nguồn dự phòng.

**Câu 7.** Một message tiếng Anh xuất hiện thay tiếng Việt có luôn là lỗi encoding không?

**Trả lời:** Không. Có thể locale sai, không tìm được `messages_vi` hoặc key tiếng Việt thiếu và fallback root hoạt động đúng.

**Câu 8.** Decoder UTF-8 thành công có bảo đảm tiếng Việt hiển thị đúng không?

**Trả lời:** Không. Một chuỗi mojibake đã được lưu lại thành bytes UTF-8 cũng decode hợp lệ. Cần assert nội dung Unicode exact.

**Câu 9.** NFC và NFD có thể hiển thị giống nhau không?

**Trả lời:** Có, nhưng Java String equality có thể khác. Chọn normalization policy và test/normalize có chủ đích.

**Câu 10.** Có nên dùng `Locale.setDefault` theo từng HTTP request không?

**Trả lời:** Không. Đây là trạng thái toàn JVM; truyền Locale explicit hoặc sử dụng framework locale resolution.

**Câu 11.** Muốn test reload runtime thì dùng bundle bên trong JAR với cacheSeconds=5 có đáng tin không?

**Trả lời:** Không. Dùng file external với `file:` và kiểm tra last-modified/cache interval trong môi trường thật.

**Câu 12.** `MessageFormat` làm mất dấu nháy đơn có phải lỗi UTF-8 không?

**Trả lời:** Không. Nó dùng dấu apostrophe để quote pattern. Cần escape theo grammar, ví dụ `Don''t`.

**Câu 13.** Tại sao sample chỉ in output sau khi check expected values?

**Trả lời:** Để không in kết quả “trông đúng” trong khi một phần encoding/fallback đã fail. Assertions kiểm tra chính xác Unicode/string values.

**Câu 14.** Nếu code không có trong mọi bundle, hai overload getMessage xử lý giống nhau không?

**Trả lời:** Không; overload có default message trả text mặc định, còn overload strict thường ném `NoSuchMessageException` nếu không có fallback policy đặc biệt.

## 21. Checklist trước khi triển khai

1. Tất cả translation properties được lưu UTF-8 without BOM?
2. Có tài liệu hóa chính sách NFC/NFD?
3. Có kiểm tra raw bytes sau build, không chỉ file source?
4. Có `setDefaultEncoding("UTF-8")` cho source cụ thể?
5. Basename không có `.properties`/locale suffix?
6. ResourceBundle vs Reloadable dùng đúng syntax basename riêng?
7. Files `messages_vi.properties`/`messages_en.properties` có nằm trong classpath?
8. `Locale.forLanguageTag("vi-VN")` có match nguồn tiếng Việt?
9. Có test `vi`, `vi-VN` và ít nhất một locale chưa hỗ trợ?
10. Có root `messages.properties` chứa keys fallback?
11. `setFallbackToSystemLocale` đã được chọn theo policy?
12. Có phân biệt defaultLocale, JVM locale và requested locale?
13. Có assert exact string với dấu tiếng Việt khó?
14. Có kiểm tra Unicode normalization nếu team yêu cầu?
15. MessageFormat placeholders/quotes đã được test?
16. Không dùng `Locale.setDefault` per request?
17. Có kiểm tra browser/email JSON/HTML charset khi Java String đúng?
18. Với Maven filtering, encoding có được cấu hình và kiểm chứng?
19. Không lộ user arguments/secrets qua logs?
20. Có kiểm tra key coverage và thiếu bản dịch quan trọng trong CI?
21. Nếu cần reload, files ở ngoài JAR và cache policy rõ?
22. Có test trên packaged artifact và platform đích?

## 22. Tài liệu tham khảo chính thức

- [Spring Framework — ApplicationContext MessageSource và internationalization](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html)
- [Spring Framework 6.2 — MessageSource API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/MessageSource.html)
- [Spring Framework 6.2 — ResourceBundleMessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/ResourceBundleMessageSource.html)
- [Spring Framework 6.2 — ReloadableResourceBundleMessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/ReloadableResourceBundleMessageSource.html)
- [Spring Framework 6.2 — AbstractResourceBasedMessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/AbstractResourceBasedMessageSource.html)
- [JDK 17 — PropertyResourceBundle](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PropertyResourceBundle.html)
- [JDK 17 — Properties](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Properties.html)
- [JDK 17 — java.text.Normalizer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Normalizer.html)
- [JDK 17 — MessageFormat](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/MessageFormat.html)


</details>

- [Quay lại đầu trang](#back-to-top)
