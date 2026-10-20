<a id="back-to-top"></a>

# Định nghĩa bean

## Menu
- [BeanDefinition mô tả cách tạo và quản lý một bean](#bean-definition-001)
- [Kiểu lớp, hàm khởi tạo và phương thức nhà máy](#bean-definition-002)
- [Tên bean, bí danh và quy ước đặt tên](#bean-definition-003)
- [Một lớp được đăng ký thành nhiều bean độc lập](#bean-definition-004)
- [Thuộc tính scope, lazy-init, primary và depends-on](#bean-definition-005)
- [Giá trị cấu hình và tham chiếu giữa các bean](#bean-definition-006)
- [Phân biệt kế thừa định nghĩa bean với kế thừa lớp Java](#bean-definition-007)
- [Định nghĩa trừu tượng và hợp nhất cấu hình kế thừa](#bean-definition-008)
- [Xung đột tên và chính sách ghi đè định nghĩa](#bean-definition-009)

## <a id="bean-definition-001">BeanDefinition mô tả cách tạo và quản lý một bean</a>

<details>
<summary>Xem chi tiết</summary>

# BeanDefinition mô tả cách tạo và quản lý một bean — Spring Framework Core Container

## 1. Mục tiêu bài học

Khi nhìn một đoạn Spring `@Bean` hoặc `@Component`, bạn có thể tưởng container chỉ “thấy annotation rồi gọi `new`”. Thực tế, Spring trước hết đăng ký **thông tin mô tả một bean** để biết *bean tên gì, có thể tạo bằng cách nào, nhận dependency nào, có scope/lifecycle ra sao*. Bản mô tả đó được biểu diễn bằng giao diện **`BeanDefinition`** trong Spring IoC container.

`BeanDefinition` không phải object nghiệp vụ. Nó là **metadata/công thức** mà `BeanFactory` dùng khi tạo và quản lý bean. Việc Spring biết metadata của một bean **không đồng nghĩa** bean instance đã tồn tại.

Kết thúc bài, bạn có thể:

1. Giải thích đúng quan hệ giữa `BeanDefinition`, `BeanDefinitionRegistry`, `BeanFactory`, `ApplicationContext` và bean instance.
2. Đọc các trường metadata quan trọng: bean name, class/factory method, scope, lazy initialization, dependency, init/destroy callbacks, constructor/property values.
3. Phân biệt *đăng ký definition*, *giải quyết dependency*, *tạo instance*, *khởi tạo* và *hủy bean*.
4. Hiểu vì sao `@Bean` và component scanning cũng tạo definition, không chỉ các thao tác đăng ký bằng API.
5. Chạy một ví dụ **Spring Framework thuần + Java 17** có cả `@Configuration`/`@Bean` và `BeanDefinitionBuilder` để quan sát metadata, singleton/prototype, injection và callback hủy.
6. Biết khi nào đăng ký bean definitions bằng API, và tránh các lỗi liên quan tên, kiểu, scope, thời điểm đăng ký, truy cập metadata.

**Phạm vi:** Đây là bài về **ý nghĩa và cách dùng BeanDefinition ở Core Container**. Không cần Spring Boot, server web, AOP, database hoặc tự viết BeanFactory để hiểu được nó.

## 2. Bốn đối tượng/cơ chế dễ nhầm

| Khái niệm | Nó là gì? | Câu hỏi trả lời |
| --- | --- | --- |
| `BeanDefinition` | Metadata/cấu hình mô tả cách Spring tạo và quản lý bean | “Bean này được định nghĩa thế nào?” |
| `BeanDefinitionRegistry` | Nơi đăng ký, tra cứu, xóa định nghĩa bean theo tên | “Container có những definition nào?” |
| `BeanFactory` | Cơ chế truy xuất/tạo/resolve bean theo quy tắc Spring | “Khi cần bean, làm sao có instance phù hợp?” |
| Bean instance | Object Java thật do container quản lý theo definition/scope | “Dịch vụ đang dùng object nào?” |
| `ApplicationContext` | Giao diện container cấp cao với registration, lifecycle, event, môi trường... tùy implementation | “Ứng dụng khởi động và làm việc với toàn bộ context thế nào?” |

Ví dụ hai tầng:

~~~text
Metadata:
  Bean name: "greetingService"
  Factory:   AppConfig.greetingService(TextFormatter)
  Scope:     singleton (mặc định)
  Depends:   TextFormatter

Runtime:
  Bean instance: GreetingService #1
     |
     +-- field formatter -> UppercaseFormatter #1
~~~

**Không phải một BeanDefinition = một object trong mọi scope.** Một định nghĩa singleton thông thường tương ứng một instance trong container; một definition prototype có thể tạo nhiều object khi mỗi lần được yêu cầu.

**Không phải mọi object Java là Spring bean.** Nếu trong method nghiệp vụ có `new StringBuilder()` hay `new Helper()`, object đó không tự nhiên trở thành managed bean chỉ vì được tạo trong ứng dụng Spring.

### Tưởng tượng BeanDefinition là công thức

- Công thức ghi: cần nguyên liệu nào, cách thực hiện, số lần tạo, bước dọn dẹp.
- Bean instance là kết quả thực tế khi công thức được áp dụng.
- BeanFactory là bộ máy sử dụng công thức để cung cấp kết quả.
- Registry giữ các công thức dưới tên phân biệt.

Phép so sánh này giúp hiểu mô hình, nhưng nhớ rằng **container còn có cơ chế mở rộng**, post-processors, factory methods, proxies, scopes và nhiều quy tắc lựa chọn bean. Không suy ra quá trình tạo mọi bean chỉ bằng một thao tác `new` đơn giản.

## 3. BeanDefinition lưu thông tin gì?

Các thuộc tính điển hình (không phải tất cả bắt buộc có giá trị không rỗng):

| Nhóm metadata | Ví dụ | Dùng để làm gì? |
| --- | --- | --- |
| Bean name | `"greetingService"` | Registry/lookup dùng làm khóa đăng ký; **tên thường được quản lý bởi registry**, không phải một field bean name cố định bên trong mọi `BeanDefinition` |
| Bean class hoặc cách tạo | `HealthProbe.class` hoặc factory method `greetingService` | Chỉ dẫn Spring khởi tạo/nhận object |
| Scope | `singleton`, `prototype` | Quy tắc giữ/dùng lại instance hoặc tạo theo yêu cầu |
| Lazy initialization | `true/false` | Có hoãn tạo bean cho tới lúc cần hay không, theo ngữ cảnh |
| Constructor arguments | Các giá trị hoặc bean references | Cung cấp tham số khi constructor được sử dụng |
| Property values | Giá trị cho setter/property phù hợp | Cấu hình instance sau khi tạo |
| Depends-on | `@DependsOn("bootstrap")` | Ràng buộc khởi tạo/hủy có chủ đích theo tên bean |
| Autowire candidate / primary | Quy tắc ứng viên injection | Giúp container chọn collaborator khi có nhiều lựa chọn |
| Init/destroy method | `init`, `shutdown` | Lifecycle callbacks khi bean được tạo/hủy phù hợp |
| Role/source/description | Metadata về nguồn khai báo | Hỗ trợ tooling, chẩn đoán hoặc infrastructure |

**Điều quan trọng:** `BeanDefinition` hỗ trợ nhiều **hình thức tạo bean**. Với method `@Bean`, Spring có thể ghi **factory bean/factory method** thay vì một bean class thông thường. Vì vậy đừng giả sử `getBeanClassName()` của mọi definition đều là tên class instance thực tế. Với `FactoryBean` hoặc proxy, thông tin kiểu runtime có thể phức tạp hơn.

### Mặc định scope không nhất thiết được ghi thành chữ singleton

Khi một definition không ghi scope tường minh, `BeanDefinition.getScope()` có thể chứa chuỗi rỗng/metadata mặc định tùy đường đăng ký. Spring **hiểu scope mặc định là singleton** cho bean ứng dụng thông thường. Đừng dùng kiểm tra `"singleton".equals(def.getScope())` để kết luận mọi bean mặc định không phải singleton.

Trong chương trình runnable, ta dùng:

~~~java
context.isSingleton("greetingService");
context.isPrototype("healthProbe");
~~~

Đây là **snippet** lấy từ một chương trình có biến `context` hợp lệ. Hai method trên hỏi về **scope thực tế của bean theo container**; `getScope()` vẫn hữu ích để đọc giá trị cấu hình tường minh trong metadata.

### Constructor injection và metadata

Với:

~~~java
@Bean
GreetingService greetingService(TextFormatter formatter) {
    return new GreetingService(formatter);
}
~~~

Spring ghi nhận factory method và phải **resolve một bean `TextFormatter`** để truyền vào tham số khi tạo `GreetingService`. Điều đó khác việc code gọi trực tiếp `greetingService(new UppercaseFormatter())` như phương thức Java thường. BeanDefinition hướng dẫn container làm việc; constructor vẫn là cơ chế Java bình thường.

## 4. BeanDefinition từ đâu xuất hiện?

Ba con đường phổ biến:

**(A) Java configuration:** Spring xử lý các method có `@Bean` trong một lớp cấu hình được đăng ký, từ đó sinh bean definitions cho factory methods.

~~~java
@Configuration(proxyBeanMethods = false)
class AppConfig {
    @Bean
    TextFormatter textFormatter() {
        return new UppercaseFormatter();
    }
}
~~~

**(B) Component scanning:** `@Component`, `@Service`, `@Repository` và các stereotype được scan theo cấu hình phù hợp cũng được chuyển thành bean definitions. Annotation trên một class ở package không được quét **không** tự động làm class đó xuất hiện trong context.

**(C) Đăng ký bằng code:** gọi `registerBeanDefinition(beanName, definition)` trên registry/container hỗ trợ. Bạn tạo metadata bằng `BeanDefinitionBuilder`, `RootBeanDefinition` hoặc các API phù hợp rồi đăng ký dưới tên rõ ràng.

~~~java
BeanDefinition definition = BeanDefinitionBuilder
        .genericBeanDefinition(HealthProbe.class)
        .setScope(BeanDefinition.SCOPE_PROTOTYPE)
        .setLazyInit(true)
        .getBeanDefinition();

context.registerBeanDefinition("healthProbe", definition);
~~~

Đây là **snippet**, cần import và các class ở ví dụ chạy đầy đủ bên dưới. Nó không gọi `new HealthProbe()`; nó chỉ đăng ký công thức. Khi bean prototype được yêu cầu, Spring mới sử dụng definition để tạo object.

~~~mermaid
flowchart TD
    A["Nguồn metadata"] --> B["BeanDefinitionRegistry"]
    A1["@Bean / JavaConfig"] --> A
    A2["@ComponentScan"] --> A
    A3["Đăng ký bằng API"] --> A
    B --> C["BeanDefinition metadata"]
    C --> D["BeanFactory: resolve và create"]
    D --> E["Bean instance"]
    E --> F["Init / sử dụng / destruction theo scope"]
~~~

**Lưu ý thứ tự:** Các thay đổi đăng ký/metadata cho ứng dụng cần được thực hiện **trước khi context refresh** (hoặc tại extension point được thiết kế cho việc đó). Đăng ký động sau khi bean đã được tạo không tự tái khởi tạo các singleton cũ và dễ dẫn đến trạng thái khó dự đoán.

## 5. Chương trình Java 17+ chạy độc lập

Đây là ví dụ Spring Framework Core Container thuần sử dụng **JDK 17+, Maven và `spring-context` phiên bản `6.2.19`** (ghim cho tính tái lập, không phải khẳng định mới nhất). Không cần Spring Boot hoặc server web.

Ứng dụng có ba bean từ `@Bean`:

- `textFormatter`: một `TextFormatter` được triển khai bởi `UppercaseFormatter`.
- `greetingService`: một service nhận `TextFormatter` qua tham số factory method.
- `traceSink`: một bean có method `shutdown` để Spring gọi khi context đóng.

Một bean khác `healthProbe` được đăng ký **bằng API**, cấu hình `prototype` và `lazyInit=true`. Ta sẽ kiểm tra **definition metadata và scope** trước/sau khi lấy instance.

~~~text
bean-definition-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── BeanDefinitionDemo.java
~~~

### 5.1. File `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>bean-definition-demo</artifactId>
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
                <version>3.14.1</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.1</version>
                <configuration>
                    <mainClass>demo.BeanDefinitionDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Chỉ cần khai báo trực tiếp `spring-context` trong `pom.xml`; Maven sẽ giải quyết thêm những dependency transitively cần thiết. `spring-beans` (nơi định nghĩa `BeanDefinition` và `BeanDefinitionBuilder`) được kéo về qua phụ thuộc của Spring Context.

### 5.2. File `src/main/java/demo/BeanDefinitionDemo.java`

~~~java
package demo;

import java.util.Locale;
import java.util.Objects;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class BeanDefinitionDemo {
    private BeanDefinitionDemo() {}

    public interface TextFormatter {
        String normalize(String input);
    }

    public static final class UppercaseFormatter
            implements TextFormatter {
        @Override
        public String normalize(String input) {
            return input.strip().toUpperCase(Locale.ROOT);
        }
    }

    public static final class GreetingService {
        private final TextFormatter formatter;

        public GreetingService(TextFormatter formatter) {
            this.formatter = Objects.requireNonNull(formatter);
        }

        public String greet(String name) {
            return "HELLO " + formatter.normalize(name);
        }

        public TextFormatter formatter() {
            return formatter;
        }
    }

    public static final class HealthProbe {
        public HealthProbe() {}
    }

    public static final class TraceSink {
        public void shutdown() {
            System.out.println("destroy=traceSink");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        public TextFormatter textFormatter() {
            return new UppercaseFormatter();
        }

        @Bean
        public GreetingService greetingService(TextFormatter formatter) {
            return new GreetingService(formatter);
        }

        @Bean(destroyMethod = "shutdown")
        public TraceSink traceSink() {
            return new TraceSink();
        }
    }

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext();

        try (context) {
            context.register(AppConfig.class);

            BeanDefinition probeDefinition = BeanDefinitionBuilder
                    .genericBeanDefinition(HealthProbe.class)
                    .setScope(BeanDefinition.SCOPE_PROTOTYPE)
                    .setLazyInit(true)
                    .getBeanDefinition();

            context.registerBeanDefinition("healthProbe", probeDefinition);
            context.refresh();

            BeanDefinition serviceDefinition = context.getBeanFactory()
                    .getBeanDefinition("greetingService");
            BeanDefinition savedProbeDefinition = context.getBeanFactory()
                    .getBeanDefinition("healthProbe");

            System.out.println("serviceDefinition=" +
                    context.containsBeanDefinition("greetingService"));
            System.out.println("factoryMethod=" +
                    serviceDefinition.getFactoryMethodName());
            System.out.println("serviceSingleton=" +
                    context.isSingleton("greetingService"));
            System.out.println("probePrototype=" +
                    context.isPrototype("healthProbe"));
            System.out.println("probeLazyMetadata=" +
                    savedProbeDefinition.isLazyInit());

            HealthProbe first = context.getBean(HealthProbe.class);
            HealthProbe second = context.getBean(HealthProbe.class);
            System.out.println("differentProbes=" + (first != second));

            GreetingService service = context.getBean(GreetingService.class);
            TextFormatter formatter = context.getBean(TextFormatter.class);
            System.out.println("injectedSame=" +
                    (service.formatter() == formatter));
            System.out.println("greeting=" + service.greet(" Lan "));
        }

        System.out.println("afterActive=" + context.isActive());
    }
}
~~~

**Vì sao ví dụ đáng học:**

- `context.register(AppConfig.class)` chỉ ra nguồn JavaConfig; `context.registerBeanDefinition("healthProbe", ...)` đưa metadata đăng ký bằng code vào cùng registry **trước `refresh()`**.
- `BeanDefinitionBuilder` tạo mô tả bean `HealthProbe` chứ **không tạo HealthProbe instance** ngay trong câu lệnh.
- `serviceDefinition.getFactoryMethodName()` trả tên method `@Bean`: `greetingService`. Đừng đánh đồng nó với constructor của object runtime.
- `savedProbeDefinition.isLazyInit()` đọc metadata do bạn đặt trong builder; còn `context.isPrototype("healthProbe")` kiểm tra scope hiệu lực theo container.
- Hai lần `getBean(HealthProbe.class)` trả **hai instances khác nhau**, bởi probe có scope `prototype`. Chương trình chỉ so identity, không dựa vào mã hash ngẫu nhiên.
- Bean `greetingService` mặc định singleton; `TextFormatter` được truyền vào factory method, nên service giữ đúng instance mà context trả về.
- Khi `try (context)` kết thúc, Spring xử lý shutdown và gọi `TraceSink.shutdown()` do `@Bean(destroyMethod="shutdown")`. Sau đó mã ngoài khối mới in `afterActive=false`.

### 5.3. Lệnh Maven và expected stdout

Từ thư mục `bean-definition-demo`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

**Đúng 10 dòng stdout do mã ứng dụng in khi chạy thành công:**

~~~text
serviceDefinition=true
factoryMethod=greetingService
serviceSingleton=true
probePrototype=true
probeLazyMetadata=true
differentProbes=true
injectedSame=true
greeting=HELLO LAN
destroy=traceSink
afterActive=false
~~~

Các lệnh kiểm tra version và các log do Maven/framework có thể sinh ra không nằm trong 10 dòng stdout được in bởi chương trình này. `-q` giúp giảm output Maven, không làm thay đổi mã `System.out.println`.

### 5.4. Chạy trực tiếp bằng `javac` và `java`

Vì source import các class Spring, cần classpath chứa Spring JAR và dependencies transitive. Có thể nhờ Maven tạo file classpath:

~~~bash
mvn -q dependency:build-classpath -Dmdep.outputFile=classpath.txt
~~~

**Linux/macOS (bash/zsh):**

~~~bash
mkdir -p out
javac --release 17 -encoding UTF-8 \
  -cp "$(cat classpath.txt)" \
  -d out src/main/java/demo/BeanDefinitionDemo.java
java -cp "out:$(cat classpath.txt)" demo.BeanDefinitionDemo
~~~

**Windows PowerShell:**

~~~powershell
$deps = (Get-Content -Raw classpath.txt).Trim()
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -encoding UTF-8 -cp "$deps" -d out src/main/java/demo/BeanDefinitionDemo.java
java -cp "out;$deps" demo.BeanDefinitionDemo
~~~

Dấu phân cách classpath là `:` trên Unix, `;` trên Windows. Hai cách chạy dùng **cùng một `main`** và in 10 dòng tương ứng.

## 6. Diễn giải kết quả theo từng loại metadata

| Output | Kiểm chứng điều gì? | Không suy ra được điều gì? |
| --- | --- | --- |
| `serviceDefinition=true` | Registry có bean definition mang tên `greetingService` | Không có nghĩa mọi bean đều đã được sử dụng |
| `factoryMethod=greetingService` | Metadata bean service chỉ ra factory method do Spring cấu hình | Không phải service có method nghiệp vụ cùng tên |
| `serviceSingleton=true` | Bean service trong context này có scope singleton | Không phải toàn JVM chỉ có một GreetingService |
| `probePrototype=true` | Probe là bean prototype | Không chứng minh mọi bean đều có scope prototype |
| `probeLazyMetadata=true` | Metadata lazy-init được đặt thành true | Không có nghĩa mọi dependency sẽ mãi không được khởi tạo |
| `differentProbes=true` | Hai lần yêu cầu probe tạo ra hai reference khác nhau | Không dựa vào `equals()` do class tự cài |
| `injectedSame=true` | Collaborator service giữ trùng reference bean formatter hiện có | Không đảm bảo business logic ở mọi input là đúng |
| `greeting=HELLO LAN` | Service sử dụng formatter để chuẩn hóa dữ liệu | Không có logic dịch vụ nào tự chạy chỉ nhờ definition |
| `destroy=traceSink` | Callback được Spring gọi khi hủy managed singleton | Không có nghĩa prototype cũng được tự hủy |
| `afterActive=false` | Context đã đóng sau khi rời try-with-resources | Không có nghĩa object Java đã bị GC ngay |

**Đọc metadata khác đọc instance:** Trong ví dụ, probe definition có thể được tra cứu từ registry trước khi ứng dụng gọi `getBean(HealthProbe.class)`. Metadata tồn tại độc lập với việc một instance cụ thể đã được tạo.

### Có nhất thiết phải tạo thử bean để xem BeanDefinition?

**Không.** Bạn có thể dùng `getBeanDefinition("beanName")` trên registry/factory để đọc metadata. Dù vậy, một số thao tác tra cứu loại bean hoặc ứng viên trong Spring có thể kích hoạt các cơ chế động (ví dụ `FactoryBean`), nên không nên khẳng định *mọi API dò bean đều không bao giờ gây instantiation*.

**Không nên sửa metadata tùy tiện sau khi context đang phục vụ ứng dụng.** Metadata ban đầu có thể đã được xử lý/merge/cache, và instance singleton đã tạo sẽ không tự đổi vì definition bị sửa. Hãy ưu tiên cấu hình rõ lúc bootstrap.

## 7. Tên bean, class và factory method

Có ba tên cần phân biệt:

- **Bean name:** khóa registry, ví dụ `greetingService`.
- **Java class của instance:** `GreetingService`.
- **Factory method:** `AppConfig.greetingService(TextFormatter)`.

Trong Spring JavaConfig, bean name mặc định thường lấy từ tên method `@Bean`; có thể đặt tên rõ:

~~~java
@Bean("primaryFormatter")
TextFormatter textFormatter() {
    return new UppercaseFormatter();
}
~~~

**Snippet minh họa, không thêm đồng thời** method trùng chữ ký vào cấu hình chính. Nếu đổi tên definition, lookup theo string name phải dùng `primaryFormatter`; injection theo `TextFormatter` vẫn phụ thuộc quy tắc chọn ứng viên phù hợp.

Với bean từ constructor trực tiếp bằng `genericBeanDefinition(HealthProbe.class)`, metadata có thể biết class tạo object. Với method `@Bean` hoặc các factory mechanisms khác, nó có thể chứa factory bean/method name và kiểu được suy luận. Không được suy ra mọi definition sẽ in chính xác class runtime qua `getBeanClassName()`.

**BeanDefinitionRegistry không quản lý chỉ các bean ứng dụng.** Context có thể có definitions nội bộ do Spring cần xử lý cấu hình. Vì vậy `context.getBeanDefinitionCount()` có thể lớn hơn số method `@Bean` mà bạn viết; kiểm tra các **tên mục tiêu** để biết cấu hình bạn muốn có mặt hay chưa.

## 8. Các metadata bổ sung: constructor, property, depends-on và lifecycle

### Constructor arguments và property values

Khi đăng ký definition programmatically, một builder có thể mang **constructor arguments** hoặc **property values**. Ví dụ minh họa:

~~~java
BeanDefinition workerDefinition = BeanDefinitionBuilder
        .genericBeanDefinition(JobWorker.class)
        .addConstructorArgValue("daily")
        .getBeanDefinition();

// registry.registerBeanDefinition("jobWorker", workerDefinition);
~~~

**Mã minh họa không độc lập:** `JobWorker` chưa định nghĩa và `registry` cần là đối tượng `BeanDefinitionRegistry`. Cần class có constructor phù hợp kiểu/giá trị mà bạn khai báo. Metadata chỉ dẫn cách Spring tạo object, nhưng nếu constructor không khớp thì việc tạo bean sẽ thất bại.

Tương tự, `addPropertyValue("enabled", true)` có ý nghĩa khi bean có property writable phù hợp. Đừng tự hiểu property values là thao tác gán trực tiếp vào mọi field private của Java.

### Phụ thuộc theo tên và injection theo kiểu

`@DependsOn("startupLogger")` đặt **ràng buộc khởi tạo** của một bean với bean tên `startupLogger`; nó không tự động inject bean đó như constructor argument. Trong khi đó, tham số `TextFormatter formatter` ở `@Bean greetingService(...)` là một **điểm injection thật sự**: Spring phải chọn được instance thích hợp.

Hai quan hệ có thể ảnh hưởng thứ tự tạo bean, nhưng khác mục đích. Dùng dependency khai báo công khai nếu consumer thực sự cần collaborator; dùng depends-on cho trường hợp cần side effect init trước, không thay thế dependency bị giấu.

### Init/destroy method

Có thể khai báo:

~~~java
@Bean(initMethod = "start", destroyMethod = "stop")
ResourceManager resourceManager() {
    return new ResourceManager();
}
~~~

**Snippet không độc lập:** cần `ResourceManager` có các method đúng chữ ký. Khi container tạo và khởi tạo bean thích hợp, callback init chạy theo lifecycle; khi singleton được hủy lúc context đóng, callback destroy chạy theo điều kiện phù hợp.

Đối với `prototype`, Spring thường chỉ quản lý các bước tới khi giao bean cho caller, **không tự theo dõi và thực hiện trọn destruction callback** của tất cả prototype sau đó. Code sử dụng prototype có tài nguyên phải có chiến lược đóng riêng.

Một bean được tạo bằng `new` ngoài container không tự nhận bất cứ lifecycle callback nào chỉ vì nó giống class của managed bean.

## 9. Những điều BeanDefinition KHÔNG tự bảo đảm

1. **Không bảo đảm có instance ngay lập tức.** Lazy/prototype và thời điểm request chi phối lúc tạo.
2. **Không tự chứng minh dependency hợp lệ.** Bean cần formatter nhưng không có candidate thì việc tạo có thể thất bại.
3. **Không bảo đảm lựa chọn type duy nhất.** Nhiều `TextFormatter` có thể gây ambiguity; dùng qualifier/primary hoặc thiết kế lại.
4. **Không tự làm method nghiệp vụ chạy.** Chỉ có bean được tạo; `GreetingService.greet` chạy vì caller gọi.
5. **Không tự quản lý object tạo ngoài container.** Một `new HealthProbe()` trong code thường không thuộc registry.
6. **Không bảo đảm toàn bộ bean có cùng scope.** Singleton/prototype/custom scopes khác nhau.
7. **Không bảo đảm mọi hủy tài nguyên hoàn tất.** Destroy callbacks phụ thuộc scope/ownership và mã bean.
8. **Không nhất thiết mô tả “class instance cuối cùng” giản đơn.** Factory methods, proxies, `FactoryBean` có semantics riêng.
9. **Không phải quy ước thứ tự khởi tạo theo tên.** Cạnh dependency và cơ chế khởi tạo quan trọng hơn thứ tự alphabet/khai báo.
10. **Không đồng nghĩa annotation.** XML, programmatic registration và component scanning cũng tạo metadata.

## 10. Các lỗi thường gặp và cách chẩn đoán

| Triệu chứng | Nguyên nhân khả dĩ | Cách kiểm tra |
| --- | --- | --- |
| `NoSuchBeanDefinitionException` | Không đăng ký definition hoặc tra sai tên/type | Kiểm tra registry, `@Bean`/scan, bean name/condition |
| `UnsatisfiedDependencyException` | Bean definition tồn tại nhưng dependency không giải quyết được | Xác định factory/constructor parameter nào thiếu ứng viên |
| `NoUniqueBeanDefinitionException` | Nhiều ứng viên cho injection một bean | `@Qualifier`, `@Primary`, giảm bean trùng |
| `BeanCreationException` | Constructor/factory/init method thất bại | Đọc chuỗi `Caused by`, xác định bean và metadata |
| `BeanCurrentlyInCreationException` | Vòng constructor/factory dependency | Vẽ A→B→A, tách trách nhiệm |
| Sai scope khi nhìn `getScope()` | Metadata mặc định có thể để rỗng | Hỏi container `isSingleton`/`isPrototype` cho tên bean cụ thể |
| Method `@Bean` trả một class nhưng `getBeanClassName()` trống/khác kỳ vọng | Definition có thể dựa trên factory method thay vì bean class trực tiếp | Xem `getFactoryMethodName()` và loại registration |
| `getBeanDefinitionCount()` lớn hơn số bean tự viết | Spring thêm infrastructure definitions | Chỉ kiểm tra các tên ứng dụng quan tâm |
| Registration API gọi sau khi refresh | Thời điểm không phù hợp với lifecycle và caches | Đăng ký trước `refresh()` hoặc dùng extension point đúng |
| Một prototype trông giống singleton | Consumer singleton giữ dependency inject từ khi nó được tạo | Dùng provider/lookup theo mỗi thao tác nếu thật sự cần |
| Bean có `@Bean(destroyMethod=...)` nhưng không thấy callback | Context chưa được đóng, bean chưa tạo hoặc scope khác | Kiểm tra lifecycle và tên method |
| `package org.springframework... does not exist` | Classpath thiếu dependency compile | Kiểm tra Maven `spring-context` và `javac -cp` |

**Đừng sửa trực tiếp BeanDefinition sau mỗi request** để “cấu hình lại” service đang chạy. Với nhiều trường hợp, instance đã được tạo/cached, và thay đổi metadata không làm Spring tái inject hay xây lại toàn bộ graph.

## 11. Khi nào nên thao tác BeanDefinition bằng API?

Phần lớn ứng dụng có thể dùng `@Configuration`, `@Bean` và component scanning. API của BeanDefinition phù hợp khi:

- Viết framework starter/infrastructure tích hợp class do người dùng cung cấp.
- Sinh nhiều bean definitions từ metadata/lược đồ khác, nhưng vẫn muốn Spring quản lý lifecycle.
- Tạo các registration động có kiểm soát **trước bootstrap** khi không tiện biểu đạt bằng vài method `@Bean`.
- Viết tooling cần kiểm tra tên/role/source/loại/thuộc tính definition.
- Tùy biến metadata trước instantiation qua extension points được thiết kế, chẳng hạn `BeanFactoryPostProcessor` hoặc `BeanDefinitionRegistryPostProcessor`.

**Phân biệt hai extension point nổi bật:**

| Extension point | Thao tác mục tiêu | Khi nào |
| --- | --- | --- |
| `BeanDefinitionRegistryPostProcessor` | Có thể đăng ký/sửa thêm bean definitions | Giai đoạn bootstrap trước tạo bean ứng dụng |
| `BeanFactoryPostProcessor` | Điều chỉnh metadata các bean definitions đã được đăng ký | Trước giai đoạn khởi tạo bean bình thường |
| `BeanPostProcessor` | Tác động lên **bean instances** khi khởi tạo | Không phải công cụ chỉ để sửa metadata |

Thứ tự/cách gọi chính xác phụ thuộc cơ chế của Spring; bài này chỉ cần nhận diện **registry metadata** và **instance processing** là hai cấp khác nhau.

Nếu chỉ cần kết nối hai service trong app, việc dùng trực tiếp `BeanDefinitionBuilder` thường **phức tạp hơn cần thiết**. Cấu hình Java đơn giản sẽ rõ ràng và dễ bảo trì hơn.

## 12. Bài tập có đáp án

**Bài 1 — Definition và instance.** Đăng ký một bean `prototype` mà chưa gọi `getBean`. Có phải bạn đã tạo instance không?

**Đáp án:** Chưa, registration chủ yếu tạo/lưu metadata. Instance prototype thường được tạo khi có yêu cầu phù hợp.

**Bài 2 — Vì sao scope trống?** `@Bean GreetingService greetingService(...)` không gắn `@Scope`. Nếu `definition.getScope()` là chuỗi rỗng, bean có phải prototype?

**Đáp án:** Không. Default scope đối với bean thông thường là singleton. Kiểm tra scope có hiệu lực bằng `context.isSingleton("greetingService")` thay vì kết luận từ chuỗi rỗng.

**Bài 3 — Hai lần lấy prototype.** Nếu `healthProbe` là prototype, `context.getBean(HealthProbe.class)` hai lần có chung reference không?

**Đáp án:** Không trong ví dụ này: Spring tạo hai instances, vì vậy `differentProbes=true`.

**Bài 4 — Method factory.** Vì sao service definition có `getFactoryMethodName()=="greetingService"`?

**Đáp án:** Bean service được đăng ký từ `@Bean greetingService(TextFormatter)`, nên metadata biết factory method cung cấp bean. Đây không phải tên method nghiệp vụ `greet`.

**Bài 5 — Destroy callback.** Tại sao `destroy=traceSink` xuất hiện trước `afterActive=false`?

**Đáp án:** Rời `try (context)` gọi `close()`. Spring hủy bean singleton có `destroyMethod="shutdown"` trước khi chương trình tiếp tục tới câu lệnh sau khối.

**Bài 6 — Thiếu collaborator.** Nếu xóa bean `textFormatter` nhưng giữ `greetingService(TextFormatter)`, điều gì xảy ra?

**Đáp án:** Context có thể thất bại khi tạo service vì không resolve được tham số bắt buộc. Definition có mặt không tự bảo đảm được instantiation.

**Bài 7 — Registry vs lookup.** `containsBeanDefinition("name")` có hoàn toàn giống `containsBean("name")`?

**Đáp án:** Không. Câu đầu hỏi bean definition được đăng ký trực tiếp trong registry tương ứng; câu sau hỏi container có nhận diện bean theo tên qua cơ chế tra cứu hay không (có thể tính cả alias/parent/registration khác tùy context). Trong các trường hợp đơn giản hai kết quả trùng, nhưng chúng không phải cùng API.

## 13. Checklist hoàn thành

- [ ] Phân biệt `BeanDefinition`, registry, factory và instance.
- [ ] Nắm bean name là khóa registry và biết đường tạo bean (class vs factory method).
- [ ] Biết scope singleton/prototype và tác dụng của lazy metadata.
- [ ] Hiểu constructor/factory args, property values và lifecycle callbacks.
- [ ] Biết `@Bean`/component scanning/registration API đều tạo metadata.
- [ ] Tạo context bằng JavaConfig và đăng ký programmatic definition trước refresh.
- [ ] Chạy Java 17+ với `mvn -q clean compile exec:java`, đối chiếu đúng 10 dòng stdout.
- [ ] Nếu tự chạy `javac`/`java`, chuẩn bị đầy đủ Spring classpath.
- [ ] Phân biệt đọc metadata `getBeanDefinition` với lấy instance `getBean`.
- [ ] Không suy đoán scope mặc định chỉ từ `definition.getScope()` có thể rỗng.
- [ ] Không chỉnh metadata tùy tiện sau khi bean đã khởi tạo.
- [ ] Biết đọc lỗi missing/ambiguous bean, vòng dependency, factory/init failures.
- [ ] Phân biệt bean lifecycle managed với object do code Java tạo ngoài container.

**Kết luận:** `BeanDefinition` là **hợp đồng metadata** mô tả Spring biết về một bean: cách tạo, cách resolve dependency và các thiết lập scope/lifecycle. Container dùng metadata để cung cấp **bean instance thực tế** khi cần. Nắm được điểm này giúp bạn hiểu vì sao cùng một Spring API có thể tạo singleton, prototype, lazy bean hoặc bean qua factory method — và vì sao **đăng ký definition khác với khởi tạo object**.

### Tài liệu đọc thêm

- [Spring IoC Container](https://docs.spring.io/spring-framework/reference/core/beans/introduction.html)
- [Bean Overview / Definition](https://docs.spring.io/spring-framework/reference/core/beans/definition.html)
- [Java Configuration — @Bean](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html)
- [Spring BeanDefinition API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/config/BeanDefinition.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-definition-002">Kiểu lớp, hàm khởi tạo và phương thức nhà máy</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu lớp, hàm khởi tạo và phương thức nhà máy khi định nghĩa bean trong Spring

## 1. Một bean được tạo từ những thông tin nào?

Trong Spring Core Container, **bean definition** là metadata cho biết container phải tạo và quản lý một bean như thế nào. Một bean definition thường gắn với **lớp triển khai**, **cách tạo đối tượng**, **các dependency/đối số cần cung cấp**, **scope** và **cấu hình vòng đời**. Bài này tập trung vào ba cách xác định *cơ chế tạo*:

1. **Bean class + constructor:** Spring trực tiếp gọi constructor của một class, đồng thời giải quyết những tham số constructor cần inject.
2. **Static factory method:** Spring gọi một phương thức `static` có nhiệm vụ trả về object cần đăng ký làm bean.
3. **Instance factory method:** Spring gọi một phương thức trên **một object factory đã có** để tạo object cần đăng ký làm bean.

Với Java configuration, `@Bean` thường là cách biểu diễn factory method trực quan. Với bean definition kiểu XML, có các thuộc tính `class`, `factory-method` và `factory-bean` diễn đạt những đường tạo tương ứng. Chọn đúng cơ chế giúp quản lý cách tạo, cấu hình và vòng đời object mà không rải `new` vào nhiều lớp nghiệp vụ.

~~~text
                    Bean definition / cấu hình
                              |
         +--------------------+--------------------+
         |                    |                    |
       class              static factory       instance factory
         |                    |                    |
    constructor()       SomeFactory.create()   factoryBean.create()
         |                    |                    |
         +--------------------+--------------------+
                              |
                        bean instance
                              |
                scope / dependency / lifecycle
~~~

Sơ đồ thể hiện **ba con đường tạo bean**, không có nghĩa mỗi bean phải dùng cả ba. Spring vẫn có thể áp dụng post-processors, proxy và lifecycle callbacks tùy những tính năng đã được cấu hình.

## 2. Phân biệt bean class, Java object và bean definition

Ví dụ class thuần Java:

~~~java
public final class GreetingService {
    private final Formatter formatter;

    public GreetingService(Formatter formatter) {
        this.formatter = formatter;
    }

    public String greet(String name) {
        return formatter.format(name);
    }
}
~~~

Đây là **đoạn minh họa** chưa độc lập vì interface `Formatter` không được định nghĩa ở đây; ví dụ runnable đầy đủ nằm ở mục 6.

- **Bean class** là kiểu class mà Spring có thể dùng để tạo object, như `GreetingService`. Nó không tự trở thành bean chỉ vì class có mặt trên classpath.
- **Bean definition** là thông tin đăng ký với container để Spring biết tạo object đó bằng constructor hay factory và cách nối các dependency.
- **Bean instance** là object thực tế mà `getBean(...)` trả về. Object runtime có thể là proxy nếu kích hoạt một số cơ chế mở rộng.
- **Bean name** là định danh đăng ký, ví dụ `greetingService`; **bean type** là kiểu Java dùng cho kiểm tra và autowiring. Tên bean và tên class không nhất thiết giống nhau.

`new GreetingService(formatter)` tự tạo object Java, nhưng object đó **không tự trở thành Spring-managed bean** nếu bạn không đăng ký instance hoặc định nghĩa tương ứng vào container.

Bean metadata cũng có thể chứa `scope`, `lazy-init`, `init-method`, `destroy-method` hoặc nguồn cấu hình tương đương. Những thông tin đó **không thay thế** constructor/factory method mà bổ sung ngữ cảnh container quản lý object.

## 3. Cách 1 — Spring tạo trực tiếp bằng constructor

### 3.1. Constructor injection

Giả sử đã có bean `Formatter` và bean definition cho `GreetingService`. Với một constructor:

~~~java
public GreetingService(Formatter formatter) {
    this.formatter = java.util.Objects.requireNonNull(formatter);
}
~~~

Spring có thể giải quyết tham số kiểu `Formatter` rồi tạo `GreetingService` bằng constructor đó. Trong một class có **chỉ một constructor**, từ Spring Framework 4.3 trở đi thường không cần gắn `@Autowired` lên constructor để constructor injection hoạt động.

Có thể tạo bean definition bằng nhiều đường:

- `@Component` + component scanning: Spring phát hiện class và tạo bean từ constructor thích hợp.
- `AnnotationConfigApplicationContext.register(GreetingService.class)`: đăng ký class rõ ràng trước khi refresh; không cần dựa vào việc quét package.
- XML `<bean class="...">` với cấu hình constructor arguments.
- API `BeanDefinitionRegistry` và `RootBeanDefinition`/`BeanDefinitionBuilder` ở tầng thấp.

**Quan trọng:** constructor injection là việc **cung cấp dependency cho constructor**. Nó có thể được dùng dù không có Spring; Spring chỉ đảm nhiệm khâu chọn/cấp argument và gọi constructor thay composition root thủ công.

### 3.2. Constructor mặc định và nhiều constructor

Một class không nhất thiết phải có constructor không tham số. Spring có thể gọi constructor có tham số khi có cách xác định constructor và giải quyết dependency phù hợp. Tuy nhiên:

- Nếu class có **nhiều constructor**, quy tắc chọn có thể cần `@Autowired`, cấu hình bean definition rõ ràng hoặc một phương án thiết kế dễ hiểu hơn.
- Nếu thiếu bean dependency, hoặc tồn tại nhiều ứng viên mà không phân giải được, Spring có thể báo lỗi ngay khi tạo bean.
- Nếu constructor ném exception, bean creation thất bại và thường được bọc trong chuỗi lỗi liên quan tới `BeanCreationException`.
- Constructor `private`, class `abstract`, kiểu không thể khởi tạo và quyền truy cập có những quy tắc/giới hạn riêng; đừng suy rằng mọi class đều được container tạo như nhau.
- `@Autowired(required = false)` hoặc các overload constructor không nên trở thành cách che dependency bắt buộc; một constructor rõ ràng thường dễ bảo trì hơn.

**Không trộn trách nhiệm:** constructor nên thiết lập invariant và nhận dependency; tránh thực hiện I/O phức tạp hoặc tác dụng phụ kéo dài trong bước khởi tạo bean. Một singleton eager có thể được khởi tạo khi context refresh, trước cả khi method nghiệp vụ được gọi.

### 3.3. XML bean definition dựa trên constructor

Đoạn XML **minh họa cấu trúc**, với `demo.GreetingService` và `demo.Formatter` giả định là kiểu hợp lệ trong một project khác:

~~~xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
                           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="formatter" class="demo.SimpleFormatter"/>

    <bean id="greetingService" class="demo.GreetingService">
        <constructor-arg ref="formatter"/>
    </bean>
</beans>
~~~

`class` ở đây chỉ class được Spring tạo; `constructor-arg ref` biểu thị dependency là bean `formatter`. XML không phải lựa chọn bắt buộc: `@Configuration`, `@Bean` hoặc component registration có thể thể hiện cùng ý đồ bằng Java.

## 4. Cách 2 — static factory method

Đôi khi một class cần được tạo qua phương thức `static` thay vì gọi constructor trực tiếp. Những tình huống phổ biến:

- Class có constructor ẩn và public static factory, ví dụ `SomeClient.create(config)`.
- Phương thức tạo cần chọn một trong nhiều implementation nhưng trả về một contract ổn định.
- Ta muốn dùng factory API của thư viện mà không đổi mã thư viện.

Ví dụ Java thuần:

~~~java
public final class Formatter {
    private final String prefix;

    private Formatter(String prefix) {
        this.prefix = prefix;
    }

    public static Formatter of(String prefix) {
        return new Formatter(prefix);
    }

    public String format(String name) {
        return prefix + " " + name;
    }
}
~~~

Đây là **đoạn minh họa**. Với cách tạo thông thường của mã Java, gọi `Formatter.of("Hello")` thay vì `new Formatter("Hello")`. Nếu dùng XML bean definition tương ứng:

~~~xml
<bean id="formatter"
      class="demo.Formatter"
      factory-method="of">
    <constructor-arg value="Hello"/>
</bean>
~~~

Ở cấu hình này, `class` là **class chứa static factory method**, `factory-method` là tên phương thức tạo, và `constructor-arg` có thể được dùng để truyền argument cho phương thức factory. Object trả về mới là bean được quản lý theo bean definition này.

### Static `@Bean` trong Java configuration

Spring cho phép khai báo phương thức `@Bean` dạng `static`:

~~~java
@Configuration(proxyBeanMethods = false)
class AppConfig {
    @Bean
    public static Formatter formatter() {
        return Formatter.of("Hello");
    }
}
~~~

Đây là **ví dụ minh họa** dùng `Formatter` ở đoạn trên; trong chương trình runnable mục 6 sẽ có phiên bản đầy đủ. Phương thức `@Bean static` thuộc class cấu hình, không cần một instance `AppConfig` để được gọi như phương thức Java. Spring vẫn đăng ký object **được trả về** thành bean.

**Đừng nhầm hai việc:**

1. **Factory method của bean definition XML:** dùng `class + factory-method` chỉ tới static method của class tạo.
2. **Static method có `@Bean`:** Spring đọc annotation trong `@Configuration` và sử dụng phương thức đó làm bean factory method.

Chúng thể hiện ý tưởng factory nhưng là **hai kiểu khai báo cấu hình** khác nhau. Một `@Bean` method có thể trực tiếp gọi static factory method của thư viện, như `return Formatter.of("Hello")`.

## 5. Cách 3 — instance factory method

Thay vì phương thức static, ta có một object factory đã được tạo:

~~~java
public final class FormatterFactory {
    private final String prefix;

    public FormatterFactory(String prefix) {
        this.prefix = prefix;
    }

    public Formatter createFormatter() {
        return Formatter.of(prefix);
    }
}
~~~

Đoạn này **minh họa**, giả định `Formatter` đã có. Lúc này phương thức tạo phụ thuộc vào **trạng thái/configuration của factory instance**.

Ví dụ XML:

~~~xml
<bean id="formatterFactory" class="demo.FormatterFactory">
    <constructor-arg value="Hello"/>
</bean>

<bean id="formatter"
      factory-bean="formatterFactory"
      factory-method="createFormatter"/>
~~~

Ý nghĩa:

1. Spring tạo `formatterFactory` như một bean riêng.
2. Khi cần `formatter`, Spring gọi phương thức `createFormatter()` **trên bean factory đó**.
3. Object mà factory trả về được quản lý dưới bean name `formatter`.

Trong lối khai báo instance factory này, **`factory-bean` trỏ tới *tên bean chứa factory instance***, khác với `class` của static factory. Không nên viết `factory-bean="demo.FormatterFactory"` nếu không có bean mang tên chuỗi đó.

### Instance method có `@Bean`

Trong Java configuration, cách thường gặp hơn là:

~~~java
@Configuration(proxyBeanMethods = false)
class AppConfig {
    @Bean
    Formatter formatter() {
        return Formatter.of("Hello");
    }
}
~~~

Method `formatter()` là **instance factory method của class cấu hình**. Spring quản lý việc gọi nó và đăng ký object trả về. Đây không giống việc ta tự gắn tên phương thức trong XML, nhưng cùng đóng vai trò tạo bean.

Một `@Bean` method cũng có thể nhận dependency là parameter:

~~~java
@Bean
GreetingService greetingService(Formatter formatter) {
    return new GreetingService(formatter);
}
~~~

Spring giải quyết bean `Formatter` rồi truyền vào **tham số factory method**. Cách này cũng là dependency injection, dù việc gọi constructor diễn ra bên trong thân `@Bean`.

### So sánh ba cách

| Cách tạo bean | Cấu hình xác định gì? | Ai chạy lệnh tạo? |
| --- | --- | --- |
| Constructor trực tiếp | Bean class + constructor arguments | Container chọn/gọi constructor |
| Static factory | Class có static method + method/arguments | Container gọi static factory |
| Instance factory | Factory bean có sẵn + instance method/arguments | Container lấy factory bean, gọi method |
| `@Bean` Java method | Phương thức trong configuration, có thể static hoặc instance | Container gọi `@Bean` method, quản lý giá trị trả về |

Không có cơ chế nào “đúng” cho mọi bean. **Hãy chọn cách tạo đơn giản nhất phù hợp API class cần sử dụng.**

## 6. Chương trình Spring Framework 6.2 / Java 17 chạy hoàn chỉnh

Ví dụ duy nhất này cố ý sử dụng **cả ba con đường** trong một context:

- `GreetingService` được Spring tạo trực tiếp qua **constructor**.
- `Phrase` được cung cấp qua **static `@Bean` factory method**.
- `GreetingFormatter` được cung cấp qua **instance `@Bean` factory method**, bên trong có gọi một static factory Java bình thường.

Không có Spring Boot, web server, database hoặc AOP. Lệnh chạy dùng Maven để tải `spring-context`.

### 6.1. Cấu trúc thư mục

~~~text
bean-creation-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── BeanCreationDemo.java
~~~

Tạo file `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>bean-creation-demo</artifactId>
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
                    <mainClass>demo.BeanCreationDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 6.2. File Java đầy đủ

Tạo `src/main/java/demo/BeanCreationDemo.java`:

~~~java
package demo;

import java.util.Objects;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class BeanCreationDemo {
    private BeanCreationDemo() {
    }

    public record Phrase(String prefix) {
        public Phrase {
            Objects.requireNonNull(prefix, "prefix");
            if (prefix.isBlank()) {
                throw new IllegalArgumentException("prefix must not be blank");
            }
        }
    }

    public static final class GreetingFormatter {
        private final Phrase phrase;

        private GreetingFormatter(Phrase phrase) {
            this.phrase = Objects.requireNonNull(phrase, "phrase");
        }

        // Static factory của Java: constructor ẩn với bên gọi.
        public static GreetingFormatter create(Phrase phrase) {
            return new GreetingFormatter(phrase);
        }

        public String format(String name) {
            Objects.requireNonNull(name, "name");
            return phrase.prefix() + " " + name;
        }
    }

    public static final class GreetingService {
        private final GreetingFormatter formatter;

        // Chỉ một constructor: Spring có thể inject dependency này.
        public GreetingService(GreetingFormatter formatter) {
            this.formatter = Objects.requireNonNull(formatter, "formatter");
        }

        public String greet(String name) {
            return formatter.format(name);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        // @Bean static factory method.
        @Bean
        public static Phrase phrase() {
            return new Phrase("Hello");
        }

        // @Bean instance factory method, có tham số được Spring inject.
        @Bean
        public GreetingFormatter greetingFormatter(Phrase phrase) {
            return GreetingFormatter.create(phrase);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            // Đăng ký class thông thường để Spring tạo qua constructor.
            context.register(AppConfig.class, GreetingService.class);
            context.refresh();

            GreetingService service = context.getBean(GreetingService.class);
            System.out.println("MESSAGE " + service.greet("Lan"));
            System.out.println("PHRASE "
                    + context.getBean(Phrase.class).prefix());
            System.out.println("FACTORY "
                    + context.getBean(GreetingFormatter.class).format("Mai"));
            System.out.println("SINGLETON "
                    + (service == context.getBean(GreetingService.class)));
        }
    }
}
~~~

### 6.3. Lệnh chạy và stdout

Từ thư mục `bean-creation-demo` có `pom.xml`, yêu cầu JDK 17+ và Maven:

~~~bash
mvn -version
mvn -q compile exec:java
~~~

**Các dòng stdout do `BeanCreationDemo` in chính xác theo thứ tự:**

~~~text
MESSAGE Hello Lan
PHRASE Hello
FACTORY Hello Mai
SINGLETON true
~~~

`mvn -version` và Maven/plugin có thể in thêm thông tin môi trường hoặc log. Bốn dòng trên là những dòng do `System.out.println` trong code demo tạo ra; không cam kết toàn bộ terminal luôn chỉ có bốn dòng.

### 6.4. Theo dõi đường tạo từng bean

1. `context.register(AppConfig.class, GreetingService.class)` đăng ký hai class trước khi gọi `refresh()`. Không cần component scan.
2. Spring đọc `@Configuration` và thấy hai `@Bean` methods.
3. Bean `phrase` được tạo bằng phương thức `static Phrase phrase()` trả một record `Phrase("Hello")`.
4. Bean `greetingFormatter` được tạo bằng phương thức **instance** `AppConfig.greetingFormatter(Phrase)`. Spring truyền `Phrase` đã đăng ký vào tham số, rồi method gọi `GreetingFormatter.create(phrase)`.
5. Bean `GreetingService` được tạo bằng **constructor injection**, vì đã đăng ký trực tiếp class và class có một constructor nhận `GreetingFormatter`.
6. `getBean(GreetingService.class)` trả service. `greet("Lan")` gọi formatter đã inject và tạo `Hello Lan`.
7. `getBean(Phrase.class)` và `getBean(GreetingFormatter.class)` đều tra những bean đã được tạo; lệnh in thứ ba minh họa factory trả về đúng kiểu sản phẩm.
8. Vì scope mặc định là `singleton` theo từng context, lần lookup thứ hai cho `GreetingService` trả đúng instance, in `SINGLETON true`.
9. `try-with-resources` đóng context; các callback destroy phù hợp của managed beans được Spring xử lý.

Đây là lý do ví dụ có thể phân biệt **cơ chế tạo** mà không dựa vào thứ tự println trong constructor. Đầu ra business được in sau khi context refresh, nên không phụ thuộc thứ tự tạo các singleton trong quá trình khởi động.

### Tại sao `GreetingService` không cần `@Component`?

`context.register(GreetingService.class)` đăng ký trực tiếp class với `AnnotationConfigApplicationContext`. Class đã là một bean candidate rõ ràng, dù không có `@Component`. Nếu bạn bỏ lời gọi đăng ký đó và không dùng một cơ chế đăng ký khác, Spring sẽ không tự tạo bean `GreetingService` chỉ vì class có trong project.

Một phương án khác là đánh dấu `@Component` rồi bật component scan cho package thích hợp, hoặc thêm một `@Bean GreetingService service(GreetingFormatter formatter)`. Không nên dùng cả ba cách đăng ký cùng một đối tượng một cách vô ý, vì có thể tạo trùng bean/nhầm tên.

## 7. Điểm dễ nhầm: `@Bean` factory method khác `FactoryBean<T>`

Hai khái niệm có tên gần giống nhưng không đồng nhất.

### `@Bean`

Đây là annotation đánh dấu một phương thức factory trong configuration. Spring gọi method và quản lý object mà method trả về:

~~~java
@Bean
GreetingFormatter greetingFormatter(Phrase phrase) {
    return GreetingFormatter.create(phrase);
}
~~~

Mã là trích đoạn từ chương trình mục 6. Bản thân `@Bean` **không yêu cầu** class sản phẩm phải implement một giao diện Spring đặc biệt.

### `FactoryBean<T>`

`org.springframework.beans.factory.FactoryBean<T>` là một **SPI chuyên biệt**. Một bean implement `FactoryBean<T>` có thể cung cấp *product bean* qua `getObject()`, đồng thời thông báo `getObjectType()` và `isSingleton()`.

Ví dụ **minh họa ý tưởng, không phải chương trình độc lập**:

~~~java
class FormatterFactoryBean
        implements org.springframework.beans.factory.FactoryBean<GreetingFormatter> {

    public GreetingFormatter getObject() {
        return GreetingFormatter.create(new Phrase("Hello"));
    }

    public Class<?> getObjectType() {
        return GreetingFormatter.class;
    }

    public boolean isSingleton() {
        return true;
    }
}
~~~

Nếu một Spring bean được đăng ký là `FactoryBean` với tên `formatterFactoryBean`, thì:

- `getBean("formatterFactoryBean")` thông thường trả **product** của factory.
- `getBean("&formatterFactoryBean")` lấy **chính factory bean**.

Tiền tố `&` là quy ước dereference của Spring `FactoryBean`. Nó **không** có liên quan tới toán tử bitwise Java trong trường hợp này.

Đặc biệt, một **instance factory method** như `factoryBean.createFormatter()` trong XML **không bắt buộc** object factory phải implement `FactoryBean<T>`. Đó là hai API/mẫu cấu hình khác nhau.

**Khi nào cần `FactoryBean`?** Khi bạn viết integration/factory logic có tính framework và muốn Spring quản lý một đối tượng trung gian tạo product theo SPI. Với ứng dụng bình thường, `@Bean` hoặc constructor injection thường đủ và dễ hiểu hơn.

## 8. Return type, bean name và phụ thuộc khi tạo

### Bean type không nhất thiết là kiểu cụ thể

~~~java
@Bean
MessageSender sender() {
    return new ConsoleSender();
}
~~~

Đây là ví dụ **minh họa**, chưa định nghĩa `MessageSender` và `ConsoleSender`. Phương thức `@Bean` trả về interface `MessageSender` nhưng instance có thể là `ConsoleSender`. Code sử dụng nên tra/inject theo **hợp đồng** khi phù hợp.

Khi lựa chọn bean theo type, Spring dựa vào metadata từ bean definition/factory method và có thể cần tạo object để xác định type cụ thể. Đừng mặc định lookup trước khởi tạo luôn biết mọi subtype implementation bằng cách phản chiếu return type cụ thể.

### Bean name

Mặc định với `@Bean`, tên bean là tên method: `phrase`, `greetingFormatter`. Với class đăng ký trực tiếp, Spring đặt tên theo quy tắc của đăng ký component; mã demo không dựa vào bean name mà lookup theo type.

### Đối số constructor/factory method

Tương tự constructor injection, Spring có thể cấp tham số cho `@Bean` factory method từ những bean khác:

~~~java
@Bean
GreetingFormatter greetingFormatter(Phrase phrase) {
    return GreetingFormatter.create(phrase);
}
~~~

Ở đây `Phrase` phải có một bean phù hợp. Nếu không có, tạo bean `GreetingFormatter` thất bại. Nếu có hai `Phrase` khác nhau và không có cơ chế phân giải hợp lệ, injection có thể mơ hồ.

Nên khai báo dependency bằng tham số thay vì tự dùng `ApplicationContext.getBean(...)` tràn lan trong body factory method. Trong `@Configuration(proxyBeanMethods = false)`, **gọi trực tiếp một `@Bean` method khác như một lời gọi Java thông thường không nhất thiết tham chiếu tới managed singleton**, nên tham số method injection là lựa chọn rõ ràng.

## 9. Scope, vòng đời và lỗi lúc khởi tạo

- **`singleton` (mặc định):** một bean instance theo bean definition trong **một container**, thường được khởi tạo khi `ApplicationContext.refresh()` nếu không lazy. Factory method thường được gọi trong lúc tạo instance đó, không phải mỗi lần lookup.
- **`prototype`:** thường tạo instance mới mỗi khi container nhận yêu cầu tạo bean; factory method hoặc constructor có thể được gọi nhiều lần theo số lần yêu cầu.
- **`@Lazy`:** có thể hoãn việc tạo bean. Nếu bean lazy trở thành dependency bắt buộc của một singleton eager khác, thời điểm khởi tạo vẫn tùy cấu hình injection.
- **Lifecycle:** container có thể chạy initialization/destruction callbacks và bean post-processors theo quy tắc. Không thể suy rằng mọi object do factory trả về đều nhận đầy đủ mọi tiện ích nếu object đó không được quản lý như bean.
- **Proxy:** khi bật AOP hoặc giao dịch, `getBean` có thể trả proxy, nên runtime type của bean có thể khác class tạo ban đầu.

### Khi constructor/factory ném exception

Ví dụ:

~~~java
@Bean
GreetingFormatter greetingFormatter(Phrase phrase) {
    if (phrase.prefix().isBlank()) {
        throw new IllegalArgumentException("Blank prefix");
    }
    return GreetingFormatter.create(phrase);
}
~~~

Đây là **minh họa**, không phải đoạn code trong ví dụ chạy mục 6. Nếu factory method ném exception, Spring không thể tạo bean `greetingFormatter` và thường báo lỗi tạo bean với nguyên nhân gốc kèm theo. Với singleton eager, lỗi có thể làm `refresh()` thất bại trước khi `getBean` được gọi.

### Hai anti-pattern thường gặp

1. **Tạo bean cùng class nhiều cách mà không có chủ đích:** vừa component-scan `GreetingService`, vừa đăng ký `@Bean GreetingService`, vừa register class. Điều này có thể tạo nhiều candidate, làm injection/lookup theo type mơ hồ.
2. **Factory method chứa business workflow:** body factory nên tập trung tạo/cấu hình object. Quy trình xử lý nghiệp vụ nên nằm ở service, không chạy khi container khởi động.

## 10. Lỗi phổ biến và cách xử lý

| Triệu chứng | Cần kiểm tra |
| --- | --- |
| `NoSuchBeanDefinitionException` khi `getBean` | Class/`@Bean` đã được đăng ký đúng context chưa? Có profile/condition cản không? |
| `NoUniqueBeanDefinitionException` | Có nhiều candidate cùng type? Chỉ rõ `@Primary`, `@Qualifier` hoặc tên bean |
| `UnsatisfiedDependencyException` | Constructor/factory method đang cần bean type nào mà thiếu? |
| `BeanCreationException` | Constructor/factory/init method ném lỗi gì? Xem nguyên nhân gốc |
| Không tìm được constructor phù hợp | Có nhiều constructor? Cấu hình autowire/đối số đã đúng? |
| Factory method không tìm thấy | Static hay instance? Method name/access/signature đúng? |
| XML `factory-bean` bị dùng như class name | Thuộc tính này là **bean name** của factory instance |
| Nhầm `@Bean` với `FactoryBean` | Cái thứ nhất là annotation cho factory method, cái thứ hai là SPI tạo product |
| Trả về class có constructor private | Có thể tạo thông qua public static factory nếu API cho phép |
| Gọi `@Bean` method thủ công trong lite mode | Lời gọi trực tiếp là Java call, có thể tạo object ngoài singleton managed |
| Gọi `new` trên class rồi chờ Spring quản lý | Instance ngoài container không tự có metadata/lifecycle Spring |
| Tưởng bean singleton là thread-safe | Scope không bảo đảm an toàn trạng thái thay đổi đồng thời |
| Tưởng Spring xóa hết `new` | Constructor và factory method vẫn tạo object; Spring tổ chức nơi tạo và vòng đời |

**Gỡ lỗi đúng thứ tự:** kiểm tra bean definition đã đăng ký → cách tạo (constructor/factory) → dependency và scope → lỗi gốc khi khởi tạo → cuối cùng mới xem lookup. Đừng sửa dependency bằng cách thêm một bean trùng lặp nếu chưa hiểu nguyên nhân.

## 11. Bài tập có đáp án

**Bài 1.** Class `GreetingService` có duy nhất constructor nhận `GreetingFormatter`. Nếu đã đăng ký class và có bean `GreetingFormatter`, có nhất thiết phải viết constructor không tham số không?

**Đáp án:** Không. Spring có thể chọn constructor duy nhất và cung cấp dependency tương ứng. Constructor không tham số không phải quy tắc bắt buộc của bean.

**Bài 2.** Vì sao `Formatter.of("Hello")` có thể tạo object dù constructor của `Formatter` private?

**Đáp án:** Static factory là method nằm trong class, nên có quyền gọi constructor private của chính class đó. Container có thể gọi static factory được cấu hình thay vì trực tiếp gọi constructor private.

**Bài 3.** XML `factory-bean="formatterFactory"` nghĩa là gọi method trên class tên `formatterFactory`?

**Đáp án:** Không. `factory-bean` là **tên một bean factory instance đã đăng ký**; Spring gọi instance method trên object đó.

**Bài 4.** Vì sao không dùng `@Bean` và `FactoryBean` như hai từ đồng nghĩa?

**Đáp án:** `@Bean` đánh dấu method cung cấp object. `FactoryBean<T>` là interface SPI để một factory bean cung cấp product qua `getObject()` và có quy tắc dereference riêng.

**Bài 5.** Trong ví dụ, `GreetingService` không có `@Component` mà vẫn có thể lookup vì sao?

**Đáp án:** `context.register(GreetingService.class)` đăng ký class làm bean definition trước `refresh()`. Component scan không phải cách đăng ký duy nhất.

**Bài 6.** Khi đổi `@Bean GreetingFormatter greetingFormatter(...)` thành `@Bean GreetingFormatter createFormatter(...)`, tác động nào đáng lưu ý?

**Đáp án:** Bean name mặc định có thể đổi từ `greetingFormatter` sang `createFormatter`. Code lookup theo type có thể vẫn hoạt động nếu duy nhất ứng viên; code dùng bean name cũ có thể lỗi.

**Bài 7.** Nếu bean `GreetingFormatter` có scope prototype, mọi lần `service.greet(...)` đều tự nhận formatter mới không?

**Đáp án:** Không. `GreetingService` singleton được inject một dependency khi tạo, nên thường giữ reference đó. Muốn cấp prototype mới theo mỗi lời gọi cần cách thiết kế/provider phù hợp.

## 12. Checklist lựa chọn và định nghĩa bean

- [ ] Xác định đúng **class sản phẩm** và khả năng gọi constructor/static/instance factory.
- [ ] Chọn constructor injection khi class thuộc quyền kiểm soát và dependency rõ ràng.
- [ ] Chọn static factory nếu API class yêu cầu hoặc lợi ích rõ.
- [ ] Chọn instance factory nếu việc tạo dựa trên một factory object đã được cấu hình.
- [ ] Nếu dùng `@Bean`, hiểu method có thể là static hoặc instance, và kết quả mới là managed bean.
- [ ] Không nhầm `factory-method`/`factory-bean` trong XML với interface `FactoryBean<T>`.
- [ ] Đăng ký class hoặc configuration vào context; annotation/source code một mình không tự tạo bean.
- [ ] Làm rõ bean name, return type, dependency arguments và khi nào type được nhận diện.
- [ ] Kiểm tra scope singleton/prototype và những ảnh hưởng tới số lần chạy constructor/factory.
- [ ] Tránh chạy logic nghiệp vụ phức tạp khi container đang tạo bean.
- [ ] Rà lỗi missing/ambiguous dependency và exception gốc.
- [ ] Dùng `getBean(Class<T>)` ở biên ứng dụng và constructor injection bên trong service.
- [ ] Viết test cho bean configuration khi nhiều đường tạo/triển khai cùng tồn tại.

**Kết luận:** **Bean class** mô tả loại object, còn **constructor hoặc factory method** quyết định đường tạo object. Spring lưu lựa chọn đó trong bean definition, nối dependency, quản lý scope/vòng đời và trả về bean cho nơi dùng. Phân biệt constructor, static factory, instance factory và `FactoryBean` giúp cấu hình Spring Core Container chính xác, dễ test và không bị phụ thuộc vào cách tạo object của một thư viện cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-definition-003">Tên bean, bí danh và quy ước đặt tên</a>

<details>
<summary>Xem chi tiết</summary>

# Tên bean, bí danh và quy ước đặt tên trong Spring Core Container

## 1. Bean name dùng để nhận diện điều gì?

Trong Spring Framework, **bean** là đối tượng được IoC container quản lý dựa trên một đăng ký/cấu hình thích hợp. Mỗi đăng ký bean có một **tên chính (primary bean name)**, và có thể có thêm **bí danh (aliases)** để cùng tra cứu một bean ấy bằng các tên khác nhau.

Hãy phân biệt các thứ thường bị gọi chung là “tên”:

| Thành phần | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| Java class | `TextFormatter`, `GreetingService` | Kiểu dữ liệu; có thể có nhiều bean cùng class |
| Bean name chính | `textFormatter` | Tên được dùng để đăng ký một bean definition |
| Bean alias | `mainFormatter`, `legacyFormatter` | Tên thay thế trỏ đến bean đã đăng ký; **không phải bean mới** |
| Tên phương thức `@Bean` | `textFormatter()` | Thông thường sinh tên mặc định nếu không chỉ định tên khác |
| Tên từ component scan | `orderHandler` | Tên sinh ra cho class `@Component` được scan |
| Qualifier | `@Qualifier("mainFormatter")` | Thông tin hỗ trợ **lựa chọn candidate** khi inject dependency; không phải lệnh tạo bean |

**Quy tắc quan trọng:** Các tên dùng làm định danh bean phải được quản lý nhất quán trong phạm vi container. Hai **bean definitions khác nhau** không được giả định sẽ cùng tồn tại độc lập dưới một tên chính. Nếu cùng tên xuất hiện trong cấu hình, hành vi có thể là conflict, bị chặn hoặc có cơ chế override theo môi trường/setting; **không được dựa vào “cấu hình khai báo sau sẽ luôn thắng”**.

Bí danh không tăng số lượng bean definitions và cũng không bắt singleton tạo thêm instance. Khi bean có scope `singleton`, `getBean("textFormatter")` và `getBean("mainFormatter")` cho cùng bean, nghĩa là cùng reference trong một context.

**Mục tiêu của bài:** biết cách đặt tên tường minh, đọc tên mặc định, tạo alias, dùng API quan sát/tra cứu, tránh tên va chạm và viết quy ước nhất quán trong dự án Spring Core Container không dùng Spring Boot.

## 2. Quy ước đặt tên: tên phản ánh vai trò, không phản ánh cú pháp

Tài liệu Spring khuyến nghị quy ước giống **tên biến instance trong Java**: bắt đầu bằng chữ thường, dùng **lowerCamelCase** và thể hiện vai trò.

| Tên bean | Đánh giá | Lý do |
| --- | --- | --- |
| `orderService` | Tốt | Ngắn, thể hiện trách nhiệm và dễ truy tìm |
| `paymentClient` | Tốt | Nhìn tên đoán được thành phần tích hợp |
| `primaryDataSource` | Tốt khi cần phân biệt | Cho biết đây là data source nào |
| `userRepository` | Tốt | Trùng ngôn ngữ nghiệp vụ rõ ràng |
| `bean1` / `obj` | Nên tránh | Không nói trách nhiệm hay hợp đồng |
| `serviceImpl` | Thường nên tránh | Tên mô tả kỹ thuật, ít thông tin về vai trò |
| `UserService` | Có thể tồn tại nhưng không nhất quán | Chữ cái đầu viết hoa không theo convention mặc định thông thường |
| `my-awesome-service` | Có thể là tên bean hợp lệ trong bối cảnh phù hợp | Dấu gạch ngang làm cú pháp reference phức tạp; không phải lựa chọn mặc định cho Java config |

Tên bean **không phải Java identifier**, nên không nên lẫn quy định cú pháp biến Java với khả năng chấp nhận tên của container. Tuy nhiên, việc dùng **lowerCamelCase ASCII**, tên ổn định và có nghĩa sẽ thuận lợi cho `@Qualifier`, cấu hình, log, bean name matching và code review.

**Tên phản ánh vai trò** chứ không buộc phải phản ánh implementation: `notificationSender` thường dễ giữ ổn định hơn `smtpNotificationSenderV2Impl` khi implementation thay đổi. Nếu thực sự có nhiều implementation đồng thời, hãy dùng tên phân biệt rõ (`emailNotificationSender`, `smsNotificationSender`) và một cơ chế chọn candidate nhất quán.

Không tạo alias chỉ để có nhiều tên “cho tiện”. Mỗi alias là một định danh công khai có thể trở thành dependency của cấu hình khác, khiến việc đổi tên về sau khó hơn.

## 3. Tên mặc định của `@Bean`, tên chỉ định và alias

Trong Java configuration, mặc định thông dụng là **lấy tên phương thức `@Bean`**:

~~~java
// Đoạn cú pháp minh họa, chưa phải file độc lập.
@Configuration(proxyBeanMethods = false)
class AppConfig {
    @Bean
    GreetingService greetingService(TextFormatter formatter) {
        return new GreetingService(formatter);
    }
}
~~~

Nếu dùng cơ chế sinh tên mặc định thông thường, bean trên có tên **`greetingService`**. Từ Spring Framework 7.0, có thể tùy biến `ConfigurationBeanNameGenerator`; do đó khi dự án chủ động thay đổi naming strategy, **đừng tuyệt đối hóa** quy tắc method name cho mọi tình huống.

### Đặt tên chính tường minh

~~~java
@Bean("auditRecorder")
AuditRecorder audit() {
    return new AuditRecorder();
}
~~~

Bean được đăng ký với tên chính **`auditRecorder`**, **không phải `audit`** chỉ vì phương thức Java có tên `audit()`. `@Bean("auditRecorder")` tương đương `@Bean(name = "auditRecorder")`.

### Một tên chính và nhiều alias

~~~java
@Bean(name = {"textFormatter", "mainFormatter", "legacyFormatter"})
TextFormatter textFormatter() {
    return new PlainTextFormatter();
}
~~~

Tên đầu tiên là tên đăng ký chính; các tên còn lại là **aliases cho cùng một bean**. Ở scope singleton mặc định, tra cứu bằng cả ba tên cho cùng đối tượng đã được quản lý.

**Alias khác bean riêng:** Ba tên trong array **không** tương đương với ba `@Bean` methods hay ba bean definitions. Nếu bạn muốn ba instance có scope/metadata khác nhau thì phải cấu hình các registrations thích hợp, không chỉ thêm alias.

Thực tế nên đặt tên tường minh khi có liên kết từ hệ khác, nhiều implementation cùng type, hoặc khi tên bean là một phần của hợp đồng cấu hình. Còn nếu mọi dependency được resolve theo type và không có yêu cầu định danh công khai, tên mặc định rõ ràng thường đã đủ.

## 4. Tên mặc định khi `@Component` được phát hiện bởi scanning

Spring có thể tự tìm và đăng ký các class mang stereotype như `@Component`, `@Service`, `@Repository`, `@Controller` **khi chúng nằm trong phạm vi component scan phù hợp**.

Với naming generator mặc định phổ biến `AnnotationBeanNameGenerator`:

| Class được scan | Tên bean mặc định | Tại sao |
| --- | --- | --- |
| `OrderHandler` | `orderHandler` | Viết thường chữ đầu của tên class |
| `PaymentClient` | `paymentClient` | Theo JavaBeans decapitalize |
| `URLParser` | `URLParser` | **Hai ký tự đầu viết hoa** nên Spring giữ nguyên chữ hoa theo quy tắc `Introspector.decapitalize` |
| `XMLGateway` | `XMLGateway` | Trường hợp chữ viết tắt tương tự |

Đây là **quy tắc mặc định**, không phải lời hứa với những project đã thay bean name generator. Khi class đã có tên định danh tường minh như:

~~~java
@Component("outboundGateway")
public class GatewayClient {
    // ...
}
~~~

bean sẽ sử dụng tên được chỉ định (nếu class được scan/đăng ký đúng). Việc thêm `@Component` **một mình** vào class không tự thêm bean vào mọi context: phải có `@ComponentScan`, `context.scan(...)` hoặc cơ chế cấu hình tương đương.

**Tránh bất ngờ với acronym:** Nếu team thường đặt class như `URLParser`, đừng suy luận chắc chắn tên sẽ là `uRLParser`. Chỉ định `@Component("urlParser")` nếu muốn một tên dễ đọc, ổn định và đúng naming convention của nhóm.

Trong Spring Framework 7, một số naming strategy có thể được tùy biến để sử dụng **fully qualified bean names**, nhất là khi nhiều component có cùng simple class name. Không tự ý chuyển toàn dự án sang một strategy khác nếu cấu hình cũ đang tham chiếu bean bằng tên.

## 5. Demo hoàn chỉnh Java 17: tên chính, alias và component names

Demo là **Spring Framework thuần**, không cần Spring Boot, HTTP hay database. Nó thực thi cùng lúc:
- `@Bean` có nhiều tên;
- `@Bean` có tên mặc định;
- `@Bean` có tên tường minh khác tên method;
- tên mặc định `@Component` sau khi scan;
- tra cứu theo tên, theo type và phép so reference;
- kiểm tra alias **không có bean definition riêng**.

### 5.1. Chuẩn bị Maven

Yêu cầu **JDK 17+**, **Maven 3.9+** và repository Maven Central/mirror cho lần tải thư viện đầu. Dùng Spring Framework **7.0.9** để khóa bản trong bài thực hành.

~~~text
bean-names-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                ├── BeanNamesDemo.java
                └── scanned/
                    ├── OrderHandler.java
                    └── URLParser.java
~~~

Tệp `pom.xml`:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>bean-names-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>7.0.9</version>
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
                    <mainClass>demo.BeanNamesDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Maven kéo các dependencies cần thiết của `spring-context`, gồm nền bean factory và annotation configuration. Không dùng `javac` trần khi chưa thiết lập classpath cho những JAR Spring; `mvn compile` sử dụng `javac` và dependency classpath qua Maven.

### 5.2. Tệp `src/main/java/demo/BeanNamesDemo.java`

~~~java
package demo;

import java.util.Arrays;

import demo.scanned.OrderHandler;
import demo.scanned.URLParser;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

public final class BeanNamesDemo {
    private BeanNamesDemo() {
    }

    interface TextFormatter {
        String format(String name);
    }

    static final class PlainTextFormatter implements TextFormatter {
        @Override
        public String format(String name) {
            return "Hello, " + name + "!";
        }
    }

    static final class GreetingService {
        private final TextFormatter formatter;

        GreetingService(TextFormatter formatter) {
            this.formatter = formatter;
        }

        String greet(String name) {
            return formatter.format(name);
        }
    }

    static final class AuditRecorder {
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = "demo.scanned")
    public static class AppConfig {

        @Bean(name = {
                "textFormatter", "mainFormatter", "legacyFormatter"
        })
        public TextFormatter textFormatter() {
            return new PlainTextFormatter();
        }

        @Bean
        public GreetingService greetingService(TextFormatter formatter) {
            return new GreetingService(formatter);
        }

        @Bean("auditRecorder")
        public AuditRecorder audit() {
            return new AuditRecorder();
        }
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            TextFormatter first =
                    context.getBean("textFormatter", TextFormatter.class);
            TextFormatter aliasA =
                    context.getBean("mainFormatter", TextFormatter.class);
            TextFormatter aliasB =
                    context.getBean("legacyFormatter", TextFormatter.class);

            String[] aliases = context.getAliases("textFormatter");
            Arrays.sort(aliases); // Đầu ra ổn định, không dựa vào thứ tự đăng ký.

            System.out.println("primary.exists="
                    + context.containsBeanDefinition("textFormatter"));
            System.out.println("alias.main.exists="
                    + context.containsBean("mainFormatter"));
            System.out.println("alias.legacy.exists="
                    + context.containsBean("legacyFormatter"));
            System.out.println("alias.hasDefinition="
                    + context.containsBeanDefinition("mainFormatter"));
            System.out.println("alias.list=" + String.join(",", aliases));
            System.out.println("aliases.sameInstance="
                    + (first == aliasA && first == aliasB));

            System.out.println("method.defaultName="
                    + context.containsBean("greetingService"));
            System.out.println("explicit.name="
                    + context.containsBean("auditRecorder"));
            System.out.println("old.method.name.isBean="
                    + context.containsBean("audit"));

            GreetingService greeting =
                    context.getBean(GreetingService.class);
            System.out.println("service.message=" + greeting.greet("Lan"));

            OrderHandler order = context.getBean(
                    "orderHandler", OrderHandler.class);
            URLParser url = context.getBean("URLParser", URLParser.class);

            System.out.println("component.camelCase="
                    + context.containsBean("orderHandler"));
            System.out.println("component.acronym="
                    + context.containsBean("URLParser"));
            System.out.println("component.classes="
                    + order.getClass().getSimpleName() + ","
                    + url.getClass().getSimpleName());
        }
    }
}
~~~

### 5.3. Tệp `src/main/java/demo/scanned/OrderHandler.java`

~~~java
package demo.scanned;

import org.springframework.stereotype.Component;

@Component
public class OrderHandler {
}
~~~

### 5.4. Tệp `src/main/java/demo/scanned/URLParser.java`

~~~java
package demo.scanned;

import org.springframework.stereotype.Component;

@Component
public class URLParser {
}
~~~

Hai class có `@Component` nằm đúng package được `@ComponentScan` chỉ định. Tên `URLParser` được giữ nguyên theo quy tắc default JavaBeans decapitalize; class `OrderHandler` thành `orderHandler`.

**Tại sao chương trình không có tên gây nhiễu?** Bean `textFormatter`, `greetingService`, `auditRecorder` khai báo bằng `@Bean` trong `AppConfig`, còn `OrderHandler` và `URLParser` là hai component từ scan package riêng. Chúng không trùng tên; các bean Spring nội bộ có thể được đăng ký thêm nhưng không ảnh hưởng các phép kiểm tra cụ thể này.

## 6. Lệnh chạy và expected stdout

Mở terminal tại thư mục `bean-names-demo` (chứa `pom.xml`):

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

Ba lệnh đầu kiểm tra phiên bản môi trường, **không có output chung cố định**. Lệnh cuối biên dịch bằng Maven Compiler Plugin (release 17), sau đó chạy `demo.BeanNamesDemo`.

**13 dòng stdout do `System.out.println` trong chương trình tạo ra**, không tính các dòng do Java/Maven/logging backend có thể xuất thêm:

~~~text
primary.exists=true
alias.main.exists=true
alias.legacy.exists=true
alias.hasDefinition=false
alias.list=legacyFormatter,mainFormatter
aliases.sameInstance=true
method.defaultName=true
explicit.name=true
old.method.name.isBean=false
service.message=Hello, Lan!
component.camelCase=true
component.acronym=true
component.classes=OrderHandler,URLParser
~~~

### Giải thích các dòng quan trọng

1. `primary.exists=true` cho thấy `textFormatter` là tên của một bean definition đã được đăng ký.
2. Hai dòng `alias.*.exists=true` cho thấy tra cứu alias có hiệu lực trong context.
3. `alias.hasDefinition=false` là bằng chứng **alias không tự sở hữu bean definition thứ hai**.
4. `alias.list=...` là kết quả `getAliases("textFormatter")` được **sắp xếp** bằng `Arrays.sort` để có đầu ra ổn định.
5. `aliases.sameInstance=true` chứng minh ba tên trả **cùng object** trong singleton scope mặc định.
6. `method.defaultName=true` minh họa method `greetingService()` tạo bean với tên mặc định trong cấu hình này.
7. `explicit.name=true`, `old.method.name.isBean=false` chứng minh `@Bean("auditRecorder")` ghi đè tên mặc định `audit`; Spring không tự tạo alias `audit`.
8. `component.camelCase` và `component.acronym` chứng minh hai trường hợp tên bean component-scan mặc định.
9. `component.classes` xác nhận lookup theo tên **và** expected Java type đều trả đúng.

**Lưu ý về thời điểm tạo:** `containsBeanDefinition` kiểm tra metadata, không bắt buộc gọi constructor của bean. `getBean` trả bean instance; trong `ApplicationContext`, singleton không lazy thường đã được tạo trong lúc refresh. Alias không tạo thêm instance kể cả khi các tên được lookup nhiều lần.

## 7. Các API nên dùng để tra cứu và chẩn đoán

| API/biểu thức | Trả lời câu hỏi | Điểm cần tránh |
| --- | --- | --- |
| `context.containsBean("name")` | Container có nhận biết bean qua tên/alias đó không? | Không phân biệt tên chính và alias |
| `context.containsBeanDefinition("name")` | Có **bean definition cục bộ** được đăng ký dưới đúng tên đó không? | Với alias, thường `false`; không phải bằng chứng bean không truy cập được |
| `context.getBean("name")` | Tra bean theo tên, nhận `Object` | Phải cast hoặc chọn overload theo type |
| `context.getBean("name", Type.class)` | Tra theo tên và kiểm tra type | Sai type có thể ném exception |
| `context.getBean(Type.class)` | Tìm bean theo type | Có thể mơ hồ nếu nhiều candidate |
| `context.getAliases("name")` | Lấy các tên alias cho bean | Đừng giả định thứ tự aliases dùng làm business rule |
| `context.getBeanNamesForType(Type.class)` | Danh sách bean names tương thích type | Không dùng để khẳng định có bao nhiêu aliases |
| `context.getBeanDefinitionNames()` | Các tên bean definitions đã đăng ký | Có thể gồm bean framework nội bộ; không hardcode số lượng/thứ tự |

Trong ví dụ, `containsBean("mainFormatter")` là `true` còn `containsBeanDefinition("mainFormatter")` là `false`. **Hai kết quả hoàn toàn không mâu thuẫn**: cái đầu hỏi khả năng tra cứu theo alias, cái sau hỏi có definition dưới tên đó hay không.

`getBean(Type.class)` và `@Qualifier` giải quyết câu hỏi **cần bean nào** theo type/metadata; alias chỉ là một dạng tên dùng để tham chiếu. Đừng dùng alias để thay thế thiết kế rõ ràng của các dependency khi hệ thống có nhiều bean cùng interface.

### Lấy theo tên hay inject theo constructor?

Ưu tiên **constructor injection** ở service, với dependency type rõ ràng; dùng `@Qualifier` hoặc `@Primary` khi có nhiều candidate thích hợp. Tra `context.getBean("name")` hữu ích cho bootstrap, công cụ quản trị hay bài học về bean naming, nhưng rải lệnh đó khắp business code dễ biến thành **service locator** khó kiểm thử.

Alias có thể hữu ích cho chuyển tên từ một cấu hình cũ sang tên mới mà không nhân đôi bean, nhưng một alias quá rộng hoặc thiếu quy tắc dễ gây nhầm lẫn khi debug.

## 8. Alias ngoài method `@Bean`: XML và registration

Cấu hình XML vẫn có thể định nghĩa alias mà **không** tạo bean thứ hai:

~~~xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
                           https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="paymentClient" class="demo.PaymentClient"/>
    <alias name="paymentClient" alias="legacyPaymentClient"/>
</beans>
~~~

Đây là **snippet XML minh họa cú pháp**, không phải file dùng trực tiếp trong project mục 5. Nó giả định class `demo.PaymentClient` có constructor phù hợp. Sau khi XML được nạp, `legacyPaymentClient` là bí danh của bean `paymentClient`, chứ không tạo một bean definition mới.

Trong cấu hình programmatic, một `BeanDefinitionRegistry` phù hợp có thể cung cấp `registerAlias("beanName", "aliasName")` và các thao tác registry khác. Đây là API cấu hình nâng cao; chỉ đăng ký/đổi alias trong giai đoạn bootstrap phù hợp, **không** coi registry của context đã refresh như một bảng đổi tên tùy ý trong lúc ứng dụng phục vụ request.

**Phân biệt alias với tên khác cho một bean khác:** Nếu đăng ký hai bean definitions `primaryPaymentClient` và `backupPaymentClient` (cùng Java type), chúng có thể có hai instance riêng. Nếu thêm alias `legacyPaymentClient` cho `primaryPaymentClient`, alias ấy **không** biến backup thành cùng object.

## 9. Trùng tên, nhiều bean cùng type và cách chọn dependency

Có hai loại vấn đề khác nhau:

### A. Nhiều definitions muốn dùng **cùng tên**

Ví dụ hai `@Bean("paymentClient")` ở các nguồn cấu hình khác nhau. Với application context/bean factory cấu hình ngăn override, container có thể từ chối startup hoặc báo lỗi bean-definition-overriding. Trong cấu hình cho phép override, một registration có thể thay thế registration trước — nhưng việc phụ thuộc vào thứ tự nạp cấu hình rất dễ gây lỗi.

**Giải pháp tốt hơn:** đặt tên riêng có ý nghĩa, xóa trùng nguồn cấu hình, kiểm tra package scan và tập cấu hình import. Không dùng alias để che giấu hai bean definitions mâu thuẫn.

### B. Nhiều definitions **khác tên** nhưng cùng interface/type

Ví dụ có bean `stripePaymentClient` và `bankPaymentClient`, đều implement `PaymentClient`. Hai tên hoàn toàn hợp lệ, nhưng inject chỉ một `PaymentClient` theo type có thể bị mơ hồ.

Bạn có thể quy định lựa chọn bằng:
- `@Qualifier("stripePaymentClient")` tại điểm inject;
- `@Primary` trên candidate mặc định phù hợp;
- tham số factory method được cấu hình rõ, hoặc lựa chọn chủ động theo tên tại composition root;
- nếu cần cả hai, inject một collection/map of beans phù hợp và xử lý minh bạch.

**Lưu ý:** `@Qualifier` dùng metadata chọn candidate, không tự tạo bean hay alias mới. **`@Primary` cũng không đổi bean name chính**, chỉ ảnh hưởng thứ tự ưu tiên lựa chọn trong điều kiện resolve thích hợp.

Trong một ứng dụng, bạn có thể dùng **hai beans khác nhau cùng Java class** nhưng hai names, scopes hoặc dependency sets khác nhau. Vì vậy số tên, số definitions, số instances và số Java classes **không phải lúc nào cũng bằng nhau**.

## 10. Khi nào nên dùng alias và quy ước đặt tên ổn định?

### Tình huống alias hợp lý

- **Chuyển đổi tên bean:** một module cũ yêu cầu `legacyFormatter` trong khi module mới gọi `textFormatter`. Alias giữ tương thích trong giai đoạn chuyển đổi.
- **Tên địa phương theo subsystem:** một datasource dùng chung có thể được tham chiếu theo tên riêng từng subsystem mà vẫn là một bean thực.
- **Cấu hình tích hợp bên ngoài:** một framework extension point yêu cầu bean với tên đã được quy định; bạn có thể khai báo tên tường minh hoặc alias một bean hiện có khi phù hợp.

### Tình huống không nên thêm alias

- Chỉ để tránh chọn rõ implementation khi có nhiều beans cùng type.
- Để tạo cảm giác có nhiều instances hoặc nhiều cấu hình khác nhau.
- Để che việc hai class/component scan mặc định va chạm tên.
- Để “tạo một bean thử nghiệm mới” nhưng chỉ thêm alias của bean production.

### Quy ước đội nhóm đề xuất

1. Mặc định **lowerCamelCase**, đặt theo vai trò nghiệp vụ.
2. Nếu contract/cấu hình ngoài tham chiếu bean bằng tên, hãy **khai báo tên tường minh** để tránh bất ngờ khi đổi tên class/method.
3. Nếu dùng tên mặc định, ghi nhận naming generator thực tế của project, nhất là các class viết tắt kiểu `URLParser`.
4. Chỉ thêm alias khi có nhu cầu tương thích hoặc tích hợp cụ thể; ghi lại alias nào là legacy và kế hoạch bỏ nó.
5. Không trộn lẫn các quy tắc đặt tên của Java class, file, Maven artifact và bean registration.
6. Tránh đổi tên chính/alias tùy tiện sau khi ứng dụng đã cấu hình xong hoặc trong runtime; cân nhắc ảnh hưởng tới injection và external references.
7. Tại code review, kiểm tra `@ComponentScan`, `@Bean`, `@Qualifier` và danh sách bean names khi xảy ra conflict.

## 11. Bài tập Java 17 để tự kiểm chứng

Hãy thực hiện **từng thay đổi độc lập** trên bản sao và khôi phục source gốc trước khi so expected stdout ở mục 6.

1. **Bỏ một alias:** xóa `"legacyFormatter"` khỏi array `@Bean(name=...)`. Dự đoán `containsBean("legacyFormatter")` và `getAliases("textFormatter")`.
2. **Đổi tên chính:** đổi `"textFormatter"` thành `"primaryFormatter"` và vẫn giữ `"mainFormatter"`; xác định phải sửa những lệnh lookup nào.
3. **Đổi method nhưng giữ tên bean:** đổi tên Java method `textFormatter()` thành `createFormatter()` nhưng giữ `@Bean(name=...)`. Bean name có thay đổi không?
4. **Đặt tên component tường minh:** đổi `@Component` của `URLParser` thành `@Component("urlParser")`, sửa code lookup, giải thích thay đổi default acronym behavior.
5. **Đổi package scan:** thay `@ComponentScan(basePackages="demo.scanned")` bằng package không chứa hai component; dự đoán `getBean("orderHandler", OrderHandler.class)` có thành công không.
6. **Tạo bean thứ hai cùng type:** thêm `@Bean("backupFormatter") TextFormatter ...` rồi gọi `getBean(TextFormatter.class)`. Quan sát ambiguity khi không có chính sách chọn; thực nghiệm `@Primary` hoặc `@Qualifier`.
7. **Kiểm tra alias/definition:** in `getBeanDefinitionNames()` và `getAliases("textFormatter")`. Vì sao không được coi aliases là additional bean definitions?
8. **So identity:** dùng `==` giữa hai lần `getBean` qua alias; giải thích kết quả nếu bean đổi sang scope prototype (lúc đó mỗi lookup có thể tạo instance mới).
9. **Hai contexts:** khởi tạo hai context cùng `AppConfig`, so `getBean("textFormatter")` của hai contexts. Vì sao singleton không có nghĩa chia sẻ object trên toàn JVM?
10. **Khắc phục va chạm:** tạo thêm một component class có cùng simple name `OrderHandler` trong package khác, cho cả hai vào cùng scan; xem vấn đề naming generator và chọn cách đặt tên tường minh phù hợp.

### Checklist hoàn thành bài

- [ ] Phân biệt bean name chính, alias, Java class name và method name.
- [ ] Nêu được quy ước lowerCamelCase và ngoại lệ `URLParser`.
- [ ] Đọc đúng `@Bean(name={"primary","aliasA","aliasB"})`.
- [ ] Giải thích vì sao alias không tạo thêm bean definition hoặc singleton instance.
- [ ] Phân biệt `containsBean` với `containsBeanDefinition`.
- [ ] Dùng được `getAliases`, `getBean(name,Type.class)` và lookup theo type.
- [ ] Biết xử lý nhiều bean cùng type bằng `@Qualifier`/`@Primary`, không nhầm với tên trùng.
- [ ] Dự đoán chính xác 13 dòng stdout Java17 của chương trình nguyên bản.

## 12. Tóm tắt và tài liệu chính thức

**Ghi nhớ:** Bean name là định danh trong IoC container, còn alias là **tên phụ dẫn tới cùng đăng ký**. Một `@Bean` có thể công khai nhiều tên mà **không** tạo thêm definitions; component scan dùng một quy tắc sinh tên mặc định có thể khác trường hợp viết tắt hoa. Đặt tên có nghĩa, nhất quán sẽ giảm lỗi wiring và xung đột khi dự án lớn lên.

Tài liệu chính thức:
- [Spring – Bean Overview: Naming Beans and Aliasing](https://docs.spring.io/spring-framework/reference/core/beans/definition.html)
- [Spring – Using @Bean: Customizing Bean Naming and Aliasing](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html)
- [Spring – Classpath Scanning and Managed Components](https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html)
- [Spring – Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)
- [Spring – Fine-tuning Annotation-based Autowiring](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html)
- [Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)

**Kết luận:** Khi nhìn một cấu hình Spring, hãy hỏi **bean definition có tên chính gì, aliases nào, type nào và scope nào** trước khi suy luận số object được tạo. Hiểu rõ những lớp nhận diện ấy là nền tảng để dùng `@Bean`, component scanning, `@Qualifier` và debug Core Container chính xác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-definition-004">Một lớp được đăng ký thành nhiều bean độc lập</a>

<details>
<summary>Xem chi tiết</summary>

# Một lớp được đăng ký thành nhiều bean độc lập — Spring Framework Core Container

## 1. Mục tiêu và câu hỏi cốt lõi

Một **Java class không tương đương một Spring bean**. Trong Spring Core Container, bean được đăng ký dưới **tên định danh** và gắn với metadata xác định cách tạo, phạm vi tồn tại và các dependency. Do đó, **cùng một lớp Java có thể được dùng để tạo nhiều bean độc lập**, có tên riêng, cấu hình riêng và — với hai singleton được khởi tạo riêng — những object khác nhau.

Ví dụ: ứng dụng muốn đếm yêu cầu của hai kênh `alpha` và `beta`. Cả hai bộ đếm đều dùng class `Counter`, nhưng yêu cầu là:

- Trạng thái đếm của kênh alpha không ảnh hưởng trạng thái beta.
- Lấy lại bean `alphaCounter` trong cùng context cho cùng instance nếu nó là singleton.
- Lấy bean `betaCounter` cũng là singleton riêng, không phải alias của alpha.
- Service cần một bộ đếm cụ thể phải **chọn đúng bean**, không chỉ nói “tôi cần một `Counter`”.

Sau bài, bạn sẽ hiểu:

1. Khác biệt giữa **class**, **bean definition**, **bean name**, **alias**, **bean instance** và **scope**.
2. Cách khai báo nhiều `@Bean` cùng trả về một kiểu class.
3. Khi nào hai bean cùng kiểu là **hai object độc lập**, khi nào chỉ là **hai tên của một bean**.
4. Vì sao `getBean(Counter.class)` hoặc injection một `Counter` có thể bị mơ hồ khi nhiều candidate cùng kiểu.
5. Cách chọn cụ thể bằng `@Qualifier`, cách dùng `@Primary`, tên bean, hoặc collection injection đúng mục đích.
6. Cách đăng ký cùng class thành nhiều definitions bằng `BeanDefinitionBuilder` và tác động của singleton/prototype.
7. Chạy chương trình Java 17+ với Spring Framework, Maven, lệnh `javac`/`java` và stdout đối chiếu được.

**Phạm vi:** bài tập trung vào **nhiều bean độc lập xuất phát từ cùng một class**. Không cần Spring Boot, Spring MVC, database hoặc component scanning để minh họa cơ chế này.

## 2. Class và bean không có quan hệ 1–1

Ba khái niệm:

| Khái niệm | Đại diện | Ý nghĩa |
| --- | --- | --- |
| Java class | `Counter.class` | Định nghĩa kiểu và hành vi của mọi object `Counter` |
| Bean definition | Metadata có tên `alphaCounter` hoặc `betaCounter` | Quy tắc tạo/quản lý một bean ở trong container |
| Bean instance | Object do một definition cung cấp | Đối tượng thật được ứng dụng sử dụng |

Đối với cấu hình:

~~~java
@Bean("alphaCounter")
Counter alphaCounter() {
    return new Counter("alpha");
}

@Bean("betaCounter")
Counter betaCounter() {
    return new Counter("beta");
}
~~~

**Đây là snippet nằm trong lớp `@Configuration`**, chưa phải file Java độc lập. Hai factory methods trả về **cùng class `Counter`**, nhưng Spring có **hai bean definitions riêng** và các factory methods thực hiện hai lệnh `new` khác nhau.

~~~mermaid
flowchart TD
    C["Java class: Counter"] -.-> A["Bean definition: alphaCounter"]
    C -.-> B["Bean definition: betaCounter"]
    A --> IA["Instance A: Counter(alpha)"]
    B --> IB["Instance B: Counter(beta)"]
    IA -.->|class giống nhau| IB
~~~

Mũi tên nét đứt từ class sang definition chỉ **cùng kiểu Java**, không chỉ ra hai bean phụ thuộc nhau. Mỗi definition có thể có:

- Tên và alias riêng.
- Factory/constructor và constructor arguments riêng.
- Scope/lazy flag/initialization/destruction metadata riêng.
- Qualifier hoặc ưu tiên khác nhau.
- Instance được tạo riêng, nếu factory/constructor thực sự cung cấp object riêng.

**Điểm then chốt:** `singleton` trong Spring mặc định là **một object được container quản lý trên mỗi bean definition trong một container**, không phải một object duy nhất cho mọi bean có cùng class, và cũng không phải singleton toàn JVM.

### Hai singleton cùng class vẫn là hai singleton

Giả sử ta gọi:

~~~java
Counter a1 = context.getBean("alphaCounter", Counter.class);
Counter a2 = context.getBean("alphaCounter", Counter.class);
Counter b1 = context.getBean("betaCounter", Counter.class);
~~~

Nếu hai definitions đều là singleton và mỗi factory tạo riêng object, kết quả:

- `a1 == a2` → `true`: hai lần tra cứu **cùng bean name**.
- `a1 == b1` → `false`: hai bean **khác nhau**, dù cùng class.
- `a1.getClass() == b1.getClass()` → `true`: hai object thuộc **cùng Java class**.

Chú ý: một bean definition mang scope singleton không buộc **hai definitions khác nhau** trả object khác nhau nếu nhà phát triển cố ý cho hai factory cùng trả một reference dùng chung. Bài học “bean độc lập” yêu cầu **hai factory thực sự tạo object riêng**, không chỉ hai tên trong code.

## 3. Bean độc lập khác alias

Hai biến thể tưởng giống nhau nhưng kết quả khác:

**Hai definitions độc lập:**

~~~java
@Bean("alphaCounter")
Counter alphaCounter() {
    return new Counter("alpha");
}

@Bean("betaCounter")
Counter betaCounter() {
    return new Counter("beta");
}
~~~

Hai definitions có tên khác nhau. Với cấu hình trên, mỗi method khởi tạo instance khác.

**Một bean có nhiều tên (alias):**

~~~java
@Bean(name = {"alphaCounter", "alphaAlias"})
Counter alphaCounter() {
    return new Counter("alpha");
}
~~~

Trong snippet thứ hai, `alphaCounter` là **tên chính**, `alphaAlias` là **alias** của cùng bean. Lấy bằng hai tên dẫn tới **cùng bean**, không tạo một definition singleton thứ hai.

| Tình huống | Số bean definitions ứng dụng | Số object singleton từ các factory (khi tạo riêng như mã) |
| --- | ---: | ---: |
| Hai `@Bean` với hai tên khác nhau và hai `new Counter(...)` | 2 | 2 |
| Một `@Bean(name={"alphaCounter","alphaAlias"})` | 1 | 1 |
| Hai `@Bean` khác tên nhưng **cùng trả một instance có sẵn** | 2 | Có thể chỉ 1 object được chia sẻ |
| Một definition `prototype` lấy hai lần | 1 | Hai object, nếu mỗi lần tạo mới |

Không dùng tổng `context.getBeanDefinitionCount()` để đếm riêng hai counter của ứng dụng: Spring có thể đăng ký thêm infrastructure definitions. Hãy hỏi **tên bean cụ thể**, hoặc lấy danh sách bean theo kiểu rồi lọc/kiểm chứng.

### Tại sao hai method cùng tên không tạo hai bean?

Java không cho phép khai báo hai method trong cùng class chỉ khác kiểu trả về. Đối với nhiều beans cùng type, đặt **method name/bean name khác nhau**. Tên bean không nhất thiết trùng class name.

Nếu component scanning chỉ phát hiện một class `@Component`, mặc định bạn có một definition cho class ấy; muốn nhiều definitions cần đăng ký thêm một cách có chủ đích, chẳng hạn những `@Bean` methods khác nhau hoặc đăng ký bằng API. Tránh vô ý scan class rồi đồng thời đăng ký chính class đó thêm bằng `@Bean` nếu không muốn có nhiều ứng viên.

## 4. Dùng @Bean để tạo hai bean độc lập

Cấu hình chính:

~~~java
@Configuration(proxyBeanMethods = false)
public static class AppConfig {
    @Bean("alphaCounter")
    public Counter alphaCounter() {
        return new Counter("alpha");
    }

    @Bean("betaCounter")
    public Counter betaCounter() {
        return new Counter("beta");
    }

    @Bean
    public Dashboard dashboard(
            @Qualifier("betaCounter") Counter selected) {
        return new Dashboard(selected);
    }
}
~~~

Đây là **phần trích từ ví dụ runnable ở mục 6**, với `AppConfig` là static nested configuration class trong một top-level class.

- Cả hai method đầu **trả `Counter`**, nên Spring thấy hai candidates tương thích kiểu `Counter`.
- Hai bean được đăng ký theo tên riêng: `alphaCounter` và `betaCounter`.
- Spring gọi hai factory methods khi cần, mỗi method tạo một `Counter` với nhãn và trạng thái riêng.
- `@Qualifier("betaCounter")` trên tham số của method `dashboard(...)` yêu cầu chọn candidate phù hợp có qualifier/tên đó, thay vì bất kỳ `Counter` nào.
- `Dashboard` chỉ nhận `Counter` bằng constructor; không cần trực tiếp gọi `context.getBean(...)` trong logic nghiệp vụ.

**Tại sao `proxyBeanMethods = false` hợp lệ?** Các factory methods **không gọi trực tiếp lẫn nhau** để tạo dependency, mà dùng tham số được Spring resolve. Nếu một method trực tiếp gọi `alphaCounter()` ở chế độ này, đó là lời gọi Java thường và có thể tạo object mới **không phải bean singleton** tương ứng.

### Tính độc lập của trạng thái

Giả sử:

~~~java
Counter alpha = context.getBean("alphaCounter", Counter.class);
Counter beta = context.getBean("betaCounter", Counter.class);

System.out.println(alpha.hit()); // alpha:1
System.out.println(beta.hit());  // beta:1
System.out.println(alpha.hit()); // alpha:2
~~~

Nếu `Counter` giữ số lần gọi trong một field **instance** (không static), alpha đã đếm 2 trong khi beta mới đếm 1. Đây là dấu hiệu mạnh hơn việc chỉ có hai bean names: **đối tượng và trạng thái thật sự tách biệt**.

Trong mã chạy đầy đủ, ta dùng `AtomicInteger` để tăng bộ đếm thread-safe. Điều đó không đồng nghĩa mọi logic và toàn bộ trạng thái bean singleton đều tự động thread-safe; bản thân class vẫn phải được thiết kế đúng theo yêu cầu đồng thời.

## 5. Cách giải quyết mơ hồ khi có nhiều bean cùng type

Trong context có hai bean `Counter` mà không có `@Primary`:

~~~java
Counter counter = context.getBean(Counter.class);
~~~

**Đây là snippet minh họa về tình huống thất bại**. Lệnh tra cứu chỉ theo kiểu thường không chọn được một bean duy nhất và ném `NoUniqueBeanDefinitionException` khi có đúng hai ứng viên không phân giải được. Trong chương trình đầy đủ, ta bắt đúng exception đó chỉ để in một dòng chẩn đoán ổn định.

### Ba cách chọn chính

1. **Chỉ rõ tên tại điểm inject bằng `@Qualifier`**, ví dụ `@Qualifier("betaCounter") Counter selected`. Đây là cách thích hợp khi collaborator phải thuộc một kênh cụ thể.
2. **Đặt `@Primary` cho một bean**, khi phần lớn điểm injection đơn trị nên ưu tiên ứng viên này và có một mặc định hợp lý.
3. **Lấy bean bằng tên rõ ràng ở composition root**, ví dụ `context.getBean("alphaCounter", Counter.class)`. Không biến service nghiệp vụ thành service locator gọi context khắp nơi.

Ví dụ về `@Primary` **chỉ minh họa một cấu hình khác**:

~~~java
@Bean("alphaCounter")
@Primary
Counter alphaCounter() {
    return new Counter("alpha");
}
~~~

Khi có một candidate primary thích hợp và không có ràng buộc khác ưu tiên khác, injection theo kiểu có thể chọn alpha làm mặc định. Tuy nhiên, `@Primary` **không gộp các bean**, và `@Qualifier("betaCounter")` vẫn có thể chỉ định beta ở một điểm injection cụ thể.

**Không nên nói “cứ có hai bean cùng class là Spring chắc chắn lỗi”.** Spring phân giải theo nhiều quy tắc: qualifier, primary, khả năng match tên parameter/field và các thiết lập khác. Ví dụ type-only `getBean(Counter.class)` ở cấu hình bài này **cố ý không có quy tắc lựa chọn**, nên mới có lỗi mơ hồ.

### Nếu bài toán thật sự cần cả hai

Có thể inject:

~~~java
@Bean
public CounterReport counterReport(Map<String, Counter> counters) {
    return new CounterReport(counters);
}
~~~

Snippet trên **không độc lập** vì `CounterReport` chưa khai báo. Trong Spring, một collection/map injection phù hợp có thể nhận nhiều bean `Counter`, với map key thường là bean name. Đây là lựa chọn tốt cho plugin/strategy registry, **không phù hợp nếu service chỉ cần đúng một kênh xác định**.

Dùng `List<Counter>` hoặc `ObjectProvider<Counter>` khi hợp đồng thật sự cần nhiều candidates/truy xuất động; đừng dùng chúng chỉ để lẩn tránh cấu hình thiếu rõ ràng.

## 6. Chương trình Java 17+ hoàn chỉnh và output

Dùng Spring Framework **6.2.19** và JDK 17+ qua Maven. Đây là phiên bản ghim cho ví dụ, không phải khẳng định bản mới nhất. Spring Boot, database và web server đều không cần thiết.

Cấu trúc project:

~~~text
multiple-beans-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── MultipleBeansDemo.java
~~~

### 6.1. File `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>multiple-beans-demo</artifactId>
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
                <version>3.14.1</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.1</version>
                <configuration>
                    <mainClass>demo.MultipleBeansDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Maven tự resolve các dependency transitively của `spring-context`. Khi chạy `javac` riêng, cần các Spring JAR trong classpath; không chỉ source Java mà đủ để compiler tự tìm Spring.

### 6.2. File `src/main/java/demo/MultipleBeansDemo.java`

~~~java
package demo;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class MultipleBeansDemo {
    private MultipleBeansDemo() {}

    public static final class Counter {
        private final String channel;
        private final AtomicInteger hits = new AtomicInteger();

        public Counter(String channel) {
            this.channel = channel;
        }

        public String hit() {
            return channel + ":" + hits.incrementAndGet();
        }

        public int count() {
            return hits.get();
        }
    }

    public static final class Dashboard {
        private final Counter selected;

        public Dashboard(Counter selected) {
            this.selected = selected;
        }

        public Counter counter() {
            return selected;
        }

        public String record() {
            return selected.hit();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean("alphaCounter")
        public Counter alphaCounter() {
            return new Counter("alpha");
        }

        @Bean("betaCounter")
        public Counter betaCounter() {
            return new Counter("beta");
        }

        @Bean
        public Dashboard dashboard(
                @Qualifier("betaCounter") Counter selected) {
            return new Dashboard(selected);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            String[] counterNames =
                    context.getBeanNamesForType(Counter.class);
            Arrays.sort(counterNames);
            System.out.println("counterBeans=" +
                    String.join(",", counterNames));

            Counter alpha =
                    context.getBean("alphaCounter", Counter.class);
            Counter beta =
                    context.getBean("betaCounter", Counter.class);
            Dashboard dashboard = context.getBean(Dashboard.class);

            System.out.println("definitions=" +
                    (context.containsBeanDefinition("alphaCounter")
                     && context.containsBeanDefinition("betaCounter")));
            System.out.println("sameClass=" +
                    (alpha.getClass() == beta.getClass()));
            System.out.println("differentInstances=" + (alpha != beta));
            System.out.println("alphaSingleton=" +
                    (alpha == context.getBean("alphaCounter", Counter.class)));
            System.out.println("betaSingleton=" +
                    (beta == context.getBean("betaCounter", Counter.class)));

            System.out.println("alphaFirst=" + alpha.hit());
            System.out.println("betaFirst=" + beta.hit());
            System.out.println("alphaSecond=" + alpha.hit());
            System.out.println("alphaCount=" + alpha.count());
            System.out.println("betaCountBeforeDashboard=" + beta.count());

            System.out.println("dashboardUsesBeta=" +
                    (dashboard.counter() == beta));
            System.out.println("dashboardRecord=" + dashboard.record());
            System.out.println("betaCountAfterDashboard=" + beta.count());

            try {
                context.getBean(Counter.class);
                System.out.println("typeLookup=UNEXPECTED");
            } catch (NoUniqueBeanDefinitionException ex) {
                System.out.println("typeLookup=AMBIGUOUS");
            }
        }
    }
}
~~~

**Vì sao mã chạy độc lập:**

- Một `public top-level class` có đúng tên file `MultipleBeansDemo.java`.
- Các class `Counter`, `Dashboard`, `AppConfig` là nested static classes, nên không cần tạo file riêng.
- `@Configuration(proxyBeanMethods=false)` hợp lệ vì hai factory methods không gọi trực tiếp nhau; factory method `dashboard(...)` nhận bean nhờ `@Qualifier`.
- Trạng thái `hits` là **field instance**, mỗi `new Counter(...)` tạo riêng một `AtomicInteger`.
- `Arrays.sort(counterNames)` bảo đảm dòng danh sách tên bean có thứ tự ổn định, không phụ thuộc iteration order của container.
- Context được đóng khi rời `try-with-resources`, nhưng chương trình không gắn destroy callback in thêm dòng, nên số dòng stdout từ `main` được xác định như dưới đây.

### 6.3. Biên dịch và chạy bằng Maven

Tại thư mục `multiple-beans-demo`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

**Stdout chính xác do phương thức `main` in, gồm 15 dòng:**

~~~text
counterBeans=alphaCounter,betaCounter
definitions=true
sameClass=true
differentInstances=true
alphaSingleton=true
betaSingleton=true
alphaFirst=alpha:1
betaFirst=beta:1
alphaSecond=alpha:2
alphaCount=2
betaCountBeforeDashboard=1
dashboardUsesBeta=true
dashboardRecord=beta:2
betaCountAfterDashboard=2
typeLookup=AMBIGUOUS
~~~

Các lệnh kiểm tra version và log Maven/Spring nếu xuất hiện **không tính** vào 15 dòng do `main` trực tiếp in. Hai counter độc lập từ cùng class giúp nhìn rõ `alpha:2` trong khi `betaCountBeforeDashboard=1`.

### 6.4. Chạy trực tiếp bằng `javac` / `java`

Vì Java source import Spring, cần các JAR tương ứng trên compile và runtime classpath. Nhờ Maven tạo danh sách:

~~~bash
mvn -q dependency:build-classpath -Dmdep.outputFile=classpath.txt
~~~

**Linux/macOS (bash/zsh):**

~~~bash
mkdir -p out
javac --release 17 -encoding UTF-8 \
  -cp "$(cat classpath.txt)" \
  -d out src/main/java/demo/MultipleBeansDemo.java
java -cp "out:$(cat classpath.txt)" demo.MultipleBeansDemo
~~~

**Windows PowerShell:**

~~~powershell
$deps = (Get-Content -Raw classpath.txt).Trim()
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -encoding UTF-8 -cp "$deps" -d out src/main/java/demo/MultipleBeansDemo.java
java -cp "out;$deps" demo.MultipleBeansDemo
~~~

Unix dùng `:` ngăn các classpath entries, Windows dùng `;`. Cả hai chạy một `main` và in 15 dòng output ở trên. Không thể biên dịch với `javac` thiếu các dependencies mà source đang import.

## 7. Đọc stdout: những gì đã được chứng minh

| Output | Kết luận chính xác |
| --- | --- |
| `counterBeans=alphaCounter,betaCounter` | Có hai tên bean thuộc kiểu `Counter` sau khi sắp xếp; không nhầm với tổng số bean toàn context |
| `definitions=true` | Hai tên `alphaCounter` và `betaCounter` được đăng ký như bean definitions |
| `sameClass=true` | Hai object có cùng Java class |
| `differentInstances=true` | Hai bean trả về **hai reference khác nhau** |
| `alphaSingleton=true`, `betaSingleton=true` | Mỗi tên singleton được tra lại thành cùng reference |
| `alphaFirst=alpha:1`, `betaFirst=beta:1` | Mỗi bean có trạng thái counter bắt đầu độc lập |
| `alphaSecond=alpha:2`, `alphaCount=2` | Chỉ alpha đã tăng tới 2 |
| `betaCountBeforeDashboard=1` | Beta vẫn bằng 1 trước khi dashboard ghi |
| `dashboardUsesBeta=true` | `@Qualifier("betaCounter")` đã chọn chính bean beta |
| `dashboardRecord=beta:2` | Dashboard làm tăng trạng thái của beta, không phải alpha |
| `betaCountAfterDashboard=2` | Beta hiện đã đếm 2 |
| `typeLookup=AMBIGUOUS` | Lookup theo class `Counter` không xác định một candidate duy nhất ở cấu hình không `@Primary` |

Một sự kiện không được chứng minh chỉ bằng hai tên bean: rằng **object độc lập**. Ví dụ này kiểm tra rõ bằng `differentInstances=true` và các trạng thái đếm riêng; đó là khác biệt giữa “đăng ký nhiều tên” và “nhiều bean độc lập” thực sự.

## 8. Cùng class với nhiều bean definitions bằng API

Ngoài `@Bean`, bạn có thể **đăng ký hai bean definitions** cho cùng `Counter.class` bằng `BeanDefinitionBuilder` hoặc `RootBeanDefinition`. Ví dụ sau là **đoạn minh họa**, không phải toàn bộ file runnable; giả định `Counter` ở mục 6 có constructor nhận `String`:

~~~java
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext();

context.registerBeanDefinition("redCounter",
        BeanDefinitionBuilder.genericBeanDefinition(Counter.class)
                .addConstructorArgValue("red")
                .getBeanDefinition());

context.registerBeanDefinition("blueCounter",
        BeanDefinitionBuilder.genericBeanDefinition(Counter.class)
                .addConstructorArgValue("blue")
                .getBeanDefinition());

try (context) {
    context.refresh();
    Counter red = context.getBean("redCounter", Counter.class);
    Counter blue = context.getBean("blueCounter", Counter.class);
    System.out.println(red == blue); // false.
}
~~~

**Ý nghĩa:** cùng `Counter.class` nhưng hai definitions đăng ký dưới tên khác và có constructor argument khác; với scope singleton mặc định, Spring tạo một object trên mỗi definition, mỗi object được cấu hình riêng.

Đăng ký definitions **trước `refresh()`** là thời điểm rõ ràng nhất. Đăng ký thêm definitions hoặc sửa metadata khi những singleton khác đã được tạo có thể gây hành vi phức tạp; đừng coi đó là cách bình thường để đổi dependency cho một ứng dụng đang chạy.

**Đừng chạy snippet này ngay trong cấu hình demo chính** rồi vẫn đối chiếu danh sách chỉ có hai counter: khi có thêm red/blue trong cùng context, số ứng viên `Counter` tăng và các điều kiện lookup/type selection cũng thay đổi.

## 9. Scope: độc lập giữa definitions và tạo mới theo lần tra cứu

### Singleton mặc định

Hai definitions `alphaCounter` và `betaCounter` trong cùng context có cùng class và cùng scope singleton mặc định. Điều này nghĩa là:

- Mỗi definition có thể quản lý một instance riêng do factory của mình tạo.
- Lấy bean theo **cùng tên** nhiều lần trả cùng instance.
- Khác **tên definition** không có nghĩa chung một instance.
- Trạng thái mutable của mỗi object vẫn phải được thiết kế thread-safe nếu có truy cập đồng thời.

### Prototype

Nếu thay một definition bằng:

~~~java
@Bean("alphaCounter")
@Scope("prototype")
Counter alphaCounter() {
    return new Counter("alpha");
}
~~~

Đây là **snippet minh họa thay thế**, không thêm vào cùng config gốc khi đang đối chiếu stdout. Khi context yêu cầu bean prototype, Spring tạo instance mới theo yêu cầu; hai lần `getBean("alphaCounter")` có thể là hai object khác nhau **ngay cả với cùng một tên definition**. Vì thế:

| Cấu hình | So sánh lặp theo cùng tên | So sánh giữa hai tên |
| --- | --- | --- |
| Hai singleton được tạo riêng | Cùng reference | Khác reference |
| Một bean prototype | Mỗi lookup tạo object mới | Không phải tiêu chí chính để suy ra số definitions |
| Một bean singleton với alias | Cùng reference dưới tên chính/alias | Hai tên vẫn cùng bean |
| Hai definitions trả cùng object từ shared factory | Cùng reference theo mỗi definition | Có thể cùng reference dù definition khác |

Spring container không tự quản lý đầy đủ destruction của prototype sau khi giao object; nếu object giữ tài nguyên cần cleanup, owner phải có chiến lược xử lý phù hợp.

### Scope không đồng nghĩa với số class

Một class có thể được dùng trong nhiều bean definitions với scope khác nhau. Bạn có thể có một bean `Counter` singleton và một bean `Counter` prototype; việc **cùng class** không quyết định scope của từng definition.

Ngược lại, cùng một bean definition không nhất thiết tạo “một instance cho mỗi class”; container áp dụng scope được cấu hình. Vì thế luôn hỏi: **definition nào, tên nào, scope nào, factory nào?**

## 10. Đăng ký nhiều bean cùng kiểu và thiết kế API

Khi hợp lý:

- Hai datasource client cùng class nhưng trỏ tới hai cơ sở dữ liệu khác nhau.
- Hai HTTP API clients cùng class nhưng URL/credentials cấu hình khác nhau.
- Hai chiến lược gửi thông báo cùng class nhưng có channel/tenant settings riêng.
- Hai worker executors cùng class nhưng xử lý các loại công việc khác nhau.
- Hai caches cùng implementation nhưng namespace/dữ liệu lưu riêng.

Các ví dụ trên mang tính mô hình; cần cân nhắc transaction, pool, lifecycle và bảo mật tùy môi trường thực. Một class chung có thể biểu diễn **hợp đồng và hành vi giống nhau**, còn từng bean mang **cấu hình và trạng thái khác nhau**.

### Đặt tên bean có ý nghĩa

Tên `alphaCounter` và `betaCounter` dễ hiểu hơn `counter1`, `counter2` khi chúng đại diện kênh cụ thể. Nếu ứng dụng có nhiều bên thứ ba cấu hình bean, dùng `@Qualifier` rõ ràng hoặc custom qualifier phù hợp thay vì phụ thuộc vào tên tham số Java một cách tình cờ.

### Khi không nên tạo thêm bean

Không tạo nhiều bean chỉ vì có thể: nếu hai consumer thực sự chia sẻ một collaborator **stateless, thread-safe và có cấu hình giống nhau**, dùng chung một singleton có thể đơn giản và ít tài nguyên hơn. Ngược lại, nếu collaborator chứa trạng thái cho hai kênh riêng, chia bean rõ ràng giúp cô lập nhưng vẫn phải xét **concurrency, lifecycle và resource usage**.

## 11. Lỗi thường gặp và cách xử lý

| Triệu chứng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| `getBean(Counter.class)` thất bại | Hai candidates cùng type không có lựa chọn duy nhất | Dùng tên bean hoặc `@Qualifier`/`@Primary` đúng thiết kế |
| Injection một `Counter` nhận sai kênh | Dựa vào quy tắc tên/primary không đúng ý định | Chỉ định qualifier tường minh và test wiring |
| Tưởng hai alias là hai bean độc lập | Alias trỏ cùng bean definition | Đăng ký hai definitions khác nhau |
| Tưởng hai definitions luôn khác object | Hai factory methods có thể trả cùng shared object | Tạo object riêng, kiểm tra identity trong test |
| Dùng `@Bean` methods gọi trực tiếp nhau với `proxyBeanMethods=false` | Lời gọi Java thường có thể tạo object ngoài bean singleton | Khai báo dependency bằng tham số factory method |
| Hai bean cùng class dùng chung biến `static` | Trạng thái static thuộc class, không thuộc từng instance | Đặt state cần cô lập vào instance field |
| Singleton mutable không an toàn khi đa luồng | Nhiều caller dùng chung một bean | Dùng cấu trúc thread-safe/đồng bộ hoặc thay thiết kế state |
| Prototype được inject vào singleton nhưng muốn tự làm mới mỗi lượt | Dependency được resolve khi singleton tạo | Dùng `ObjectProvider<T>`/lookup theo lượt thật cần |
| Nhiều beans bị đăng ký vì `@Component` và `@Bean` cùng tồn tại | Đăng ký trùng không chủ đích | Rà nguồn đăng ký, chỉ giữ ứng viên mong muốn |
| `NoSuchBeanDefinitionException` | Bean name sai hoặc chưa đăng ký | Kiểm tra `@Bean`, configuration loading, package scan |
| `NoUniqueBeanDefinitionException` | Nhiều ứng viên và không có cách phân giải | Kiểm tra qualifier/primary/type; xem điểm injection |
| Context có ít/many definitions hơn dự kiến | Đếm toàn bộ infrastructure beans thay cho app beans | Kiểm tra danh sách theo type/tên cần thiết |
| Source Java compile báo thiếu Spring package | Thiếu Maven deps hoặc `javac -cp` | Sửa build/classpath trước khi chẩn đoán IoC |
| Test có lúc đúng lúc sai khi dùng state singleton | Test dùng chung context/bean mutable | Reset dữ liệu/test isolation phù hợp và tránh state không kiểm soát |

**Đọc lỗi đúng lớp:** lỗi `NoUniqueBeanDefinitionException` là lỗi **phân giải lựa chọn candidate**, không phải hai object có cùng class là bất hợp pháp trong Java. Bean names/qualifiers là công cụ để thiết kế quyết định lựa chọn rõ ràng.

## 12. Tự kiểm tra bằng bài tập và đáp án

**Bài 1.** Hai method `@Bean("one") Counter one()` và `@Bean("two") Counter two()` đều trả `new Counter(...)`. Cùng class có hai bean singleton độc lập được không?

**Đáp án:** Được. Hai names/definitions riêng và hai object được factory tạo riêng. Mỗi tên singleton lưu instance của chính nó.

**Bài 2.** `@Bean(name={"one","alias"})` tạo mấy bean độc lập?

**Đáp án:** Một bean có thêm alias; lookup hai tên trỏ tới cùng object với singleton. Không phải hai independent definitions.

**Bài 3.** `getBean(Counter.class)` gặp hai candidate, đều không `@Primary`, không selection metadata khác. Có tự động chọn một ngẫu nhiên không?

**Đáp án:** Không. Lookup chỉ theo type trong trường hợp đó báo không có lựa chọn duy nhất, ví dụ `NoUniqueBeanDefinitionException`. Chỉ rõ tên/qualifier hoặc một primary theo thiết kế.

**Bài 4.** Dashboard cần bean beta nhưng class field chỉ có kiểu `Counter`. Làm thế nào?

**Đáp án:** Inject `@Qualifier("betaCounter") Counter selected` ở constructor/factory parameter tương ứng, hoặc thiết kế qualifier riêng. Code domain không cần tra context bằng string.

**Bài 5.** Hai bean cùng class có field `static int counter`. Hai bean có thống kê độc lập không?

**Đáp án:** Không với field static ấy; static gắn với class và dùng chung. Muốn state riêng thì dùng instance fields (trong demo là `AtomicInteger hits`).

**Bài 6.** Đổi alpha thành `prototype`, lấy `getBean("alphaCounter")` hai lần, có giữ cùng reference không?

**Đáp án:** Theo scope prototype bình thường, mỗi lần được yêu cầu Spring tạo instance mới. Kỳ vọng identity sẽ khác, không còn đúng điều kiện singleton trong stdout gốc.

**Bài 7.** Hai beans dùng cùng một singleton shared object trả từ hai factory methods. Tên và definitions khác nhau thì reference có khác không?

**Đáp án:** Không nhất thiết. Tên/definitions độc lập không tự chứng minh object identity; nếu factory trả cùng reference thì hai lookups có thể trỏ cùng object. Đây chính là lý do kiểm chứng `alpha != beta` có ý nghĩa.

## 13. Checklist áp dụng

- [ ] Phân biệt class, bean definition, bean name, alias và bean instance.
- [ ] Hai bean cùng class cần hai definitions thực sự, không chỉ thêm alias.
- [ ] Factory methods tạo/cung cấp đúng instances và cấu hình riêng.
- [ ] Mỗi bean có tên mang ý nghĩa vai trò/kênh, không dùng số thứ tự vô nghĩa.
- [ ] Hiểu singleton theo bean definition trong cùng container.
- [ ] Biết rằng prototype tạo instance mỗi lần yêu cầu bean theo scope phù hợp.
- [ ] Khi có nhiều candidate cùng type, dùng qualifier/primary hoặc tên để giải quyết đúng mục đích.
- [ ] Không dựa vào việc Spring “chọn ngẫu nhiên” khi mơ hồ.
- [ ] Nếu cần tất cả candidates, cân nhắc collection/map injection với hợp đồng rõ.
- [ ] Không gọi trực tiếp các `@Bean` methods với `proxyBeanMethods=false` như cách lấy singleton.
- [ ] Không để state phân tách kênh trong static field.
- [ ] Kiểm tra identity và trạng thái riêng trong test, không chỉ đếm bean names.
- [ ] Chạy Java 17+ với Maven hoặc `javac`/`java` có Spring classpath, đối chiếu đúng **15 dòng stdout**.
- [ ] Không lạm dụng nhiều beans khi một singleton stateless có thể chia sẻ an toàn.

**Kết luận:** **Một class Java có thể đứng sau nhiều Spring beans.** Điều làm mỗi bean độc lập là **bean definition/name được đăng ký riêng cùng cách factory/scope cung cấp instance và trạng thái riêng**. Bean alias chỉ là tên bổ sung; dependency theo type có thể trở nên mơ hồ, vì vậy hãy thiết kế điểm injection rõ bằng `@Qualifier`/`@Primary` hoặc tên khi cần.

### Tài liệu chính thức

- [Spring Bean Overview and Names](https://docs.spring.io/spring-framework/reference/core/beans/definition.html)
- [Spring JavaConfig — @Bean Annotation](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html)
- [Spring Autowiring Collaborators and Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html)
- [Spring Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-definition-005">Thuộc tính scope, lazy-init, primary và depends-on</a>

<details>
<summary>Xem chi tiết</summary>

# Thuộc tính `scope`, `lazy-init`, `primary` và `depends-on` trong Spring Bean Definition

## 1. Bốn thuộc tính điều khiển bốn quyết định khác nhau

Khi đã biết định nghĩa bean bằng `@Bean` hoặc `@Component`, ta cần hiểu **metadata** quyết định **số lượng và vòng đời instance**, **thời điểm khởi tạo**, **cách chọn dependency** và **thứ tự khởi tạo của các bean có quan hệ đặc biệt**.

| Thuộc tính | Câu hỏi nó giải quyết | Mặc định/thông dụng | Java configuration |
| --- | --- | --- | --- |
| **`scope`** | Một bean instance được cấp và tồn tại theo phạm vi nào? | `singleton` theo từng Spring container | `@Scope("singleton")`, `@Scope("prototype")` |
| **`lazy-init`** | Bean singleton có bị tạo sớm khi khởi động context không? | Non-lazy singleton thường eager | `@Lazy` |
| **`primary`** | Khi có nhiều bean cùng type, ứng viên nào được ưu tiên cho **một dependency**? | Không có bean mặc định được ưu tiên trừ khi khai báo | `@Primary` |
| **`depends-on`** | Bean nào phải được **khởi tạo trước** bean hiện tại dù không là constructor argument? | Spring dựa trên những dependency được khai báo thực sự | `@DependsOn("beanName")` |

Các metadata trên **không thay thế nhau**:

- `@Scope("prototype")` **không** tự làm bean thành lazy singleton; nó đổi scope.
- `@Lazy` **không** tạo instance mới mỗi lần `getBean`; điều đó liên quan tới scope.
- `@Primary` **không** đặt thứ tự tạo bean và không đổi scope.
- `@DependsOn` **không** chọn bean khi inject theo interface và **không tự inject** đối tượng phụ thuộc.

Một bean definition có thể kết hợp nhiều thuộc tính, nhưng cần lý do thực tế. Ví dụ bài này sẽ kết hợp `@Lazy` và `@DependsOn` trên một service để thấy thứ tự tạo khi service được yêu cầu.

## 2. `scope`: phạm vi cấp phát và vòng đời

### 2.1. `singleton` — mặc định phổ biến

Trong Spring, `singleton` nghĩa là **một bean instance cho mỗi bean definition trong mỗi IoC container** (trong phạm vi thông thường). Vì vậy hai lần lấy cùng một singleton bean bằng `getBean` trong cùng context thường trả về cùng reference:

~~~java
Object first = context.getBean("catalogService");
Object second = context.getBean("catalogService");
System.out.println(first == second); // true với singleton bean
~~~

Đây là trích đoạn minh họa, giả sử `catalogService` đã được đăng ký trong `context`. Nó không đồng nghĩa Singleton pattern cổ điển giữ **một** object duy nhất trên toàn JVM: hai context riêng có thể chứa hai instance khác nhau; hai bean definition riêng cũng có thể tạo hai singleton khác nhau của cùng class.

Vì Spring thường pre-instantiate những singleton **không lazy** khi `ApplicationContext.refresh()`, constructor và factory methods có thể được gọi từ lúc khởi động, **trước** lệnh `getBean` đầu tiên.

**Singleton không tự thread-safe.** Nếu một bean singleton giữ `HashMap`, bộ đếm hoặc trạng thái mutable mà nhiều thread truy cập, vẫn phải xem xét concurrency, đồng bộ hoặc thiết kế stateless. Việc có một instance dùng chung khiến vấn đề state càng cần chú ý.

### 2.2. `prototype` — yêu cầu bean thường cho instance mới

Một prototype bean thường được tạo **mỗi lần container nhận yêu cầu lấy instance**:

~~~java
@Bean
@Scope("prototype")
RequestToken requestToken() {
    return new RequestToken();
}
~~~

Đây là ví dụ minh họa, `RequestToken` chưa được định nghĩa ở đoạn này. Với prototype, hai lần `getBean("requestToken")` thông thường cho hai object khác nhau, nhưng **không nên dùng một random ID để chứng minh `==`**; hãy so identity trực tiếp hoặc giữ ID đếm ổn định như ví dụ chạy ở mục 6.

Khác biệt lifecycle cần nhớ:

- Spring quản lý giai đoạn khởi tạo và cấp instance của prototype.
- Spring **không tự gọi đầy đủ destroy callbacks cho mọi prototype** như đối với singleton khi đóng context. Bên yêu cầu/giữ object thường phải chịu trách nhiệm dọn tài nguyên tương ứng.
- Prototype không phải “bean cho từng HTTP request”. Nó chỉ nói về mỗi lần container được yêu cầu tạo bean.
- Một **prototype dependency inject trực tiếp vào singleton** thường được giải quyết **một lần khi singleton được tạo**. Vì thế singleton giữ nguyên reference đó trong các lời gọi về sau. Muốn có prototype mới theo nhu cầu, cân nhắc `ObjectProvider<T>` hoặc factory phù hợp.

### 2.3. Scope khác và ngữ cảnh web

Một số scope được cung cấp khi dùng môi trường web có hỗ trợ tương ứng:

| Scope | Ý nghĩa thông thường | Bối cảnh |
| --- | --- | --- |
| `singleton` | Một bean instance mỗi definition/context | Core Container |
| `prototype` | Instance mới theo yêu cầu lấy bean | Core Container |
| `request` | Bean sống theo một HTTP request | Web-aware application context |
| `session` | Bean theo một HTTP session | Web-aware application context |
| `application` | Bean gắn với ServletContext | Ứng dụng Servlet |
| `websocket` | Bean theo một WebSocket session | WebSocket integration |

Không thể lấy `@Scope("request")` hoạt động đúng chỉ bằng việc dùng `AnnotationConfigApplicationContext` console. Nó cần web scope được đăng ký và request context hợp lệ. Khi inject bean ngắn hạn vào bean sống lâu hơn, đôi khi cần **scoped proxy** hoặc provider, chứ không chỉ đổi tên scope.

Với các ứng dụng không có HTTP, thường tập trung hiểu `singleton` và `prototype` trước.

### 2.4. Cú pháp XML tương ứng

~~~xml
<bean id="accountService"
      class="demo.AccountService"
      scope="singleton"/>

<bean id="requestToken"
      class="demo.RequestToken"
      scope="prototype"/>
~~~

Đây là **đoạn XML minh họa**, các class được giả định có sẵn và có constructor phù hợp. XML và annotation là hai cách cung cấp metadata cho cùng Core Container, không phải hai loại container khác nhau.

## 3. `lazy-init`: thời điểm khởi tạo singleton

### 3.1. Tại sao có lazy initialization?

`ApplicationContext` thường tạo trước **singleton non-lazy** trong quá trình `refresh()`. Lợi ích là lỗi thiếu bean/khởi tạo thường được phát hiện ngay khi startup. Nhưng có bean rất nặng hoặc hiếm khi dùng, và bạn muốn hoãn chi phí tạo cho tới lần cần đầu tiên.

Trong XML, có thuộc tính:

~~~xml
<bean id="reportGenerator"
      class="demo.ReportGenerator"
      lazy-init="true"/>
~~~

Trong Java configuration, dùng `@Lazy`:

~~~java
@Bean
@Lazy
ReportGenerator reportGenerator() {
    return new ReportGenerator();
}
~~~

Hai đoạn **minh họa**, `ReportGenerator` chưa được khai báo ở đây. Khi không bị dependency eager khác buộc tạo sớm, `@Lazy` khiến singleton bean thường chỉ được tạo khi có yêu cầu đầu tiên, như `getBean(ReportGenerator.class)`.

**Không nhầm:** `@Lazy` trên **bean definition** nhằm trì hoãn tạo bean; `@Lazy` ở **injection point** trong một số cấu hình `@Autowired` có thể tạo proxy giải quyết dependency trì hoãn. Cùng annotation nhưng nơi đặt ảnh hưởng cách xử lý.

### 3.2. Bẫy thường gặp: lazy bean bị một eager singleton cần dùng

Giả sử:

~~~java
@Bean
@Lazy
ExpensiveClient expensiveClient() {
    return new ExpensiveClient();
}

@Bean
PaymentService paymentService(ExpensiveClient client) {
    return new PaymentService(client);
}
~~~

Đây là mã minh họa. `paymentService` là singleton eager, constructor/factory method của nó cần `ExpensiveClient`. Spring thường phải tạo `ExpensiveClient` để cấp argument, **dù bean đó đánh dấu `@Lazy`**. Vì vậy không thể chỉ nhìn `@Lazy` rồi khẳng định client chắc chắn chưa được tạo sau `refresh()`.

Muốn trì hoãn truy cập cho đến lúc sử dụng, có thể thiết kế `ObjectProvider<ExpensiveClient>`, một factory hoặc lazy-resolution proxy tùy nhu cầu. Nên cân nhắc rõ vòng đời và lỗi sẽ chuyển sang thời điểm sử dụng đầu tiên.

### 3.3. Lazy không đồng nghĩa hiệu năng luôn tốt

Đặt nhiều bean lazy có thể làm thời gian startup giảm, nhưng:

- Lỗi cấu hình có thể chỉ xuất hiện khi bean được yêu cầu, khó nhận ra sớm.
- Lần sử dụng đầu tiên chịu chi phí tạo và có thể gây latency.
- Bean được dùng thường xuyên thường không được lợi nhiều.
- Dependency graph vẫn quyết định thời điểm tạo thực tế.
- Có thể khiến hành vi init/lifecycle khó theo dõi hơn nếu bật lazy đại trà.

Nên tối ưu sau khi biết yêu cầu thật và đo đạc, không đánh dấu mọi bean `@Lazy` chỉ vì tưởng như nhanh hơn.

## 4. `primary`: chọn bean khi có nhiều ứng viên cùng kiểu

### 4.1. Bài toán autowiring theo type

Giả sử có interface:

~~~java
interface Formatter {
    String format(String value);
}
~~~

Có hai bean cùng kiểu `Formatter`:

~~~java
@Bean
Formatter basicFormatter() {
    return value -> "BASIC:" + value;
}

@Bean
Formatter decoratedFormatter() {
    return value -> "DECORATED:" + value;
}
~~~

Nếu một factory method/constructor yêu cầu **một** tham số `Formatter`, Spring cần quyết định chọn bean nào. Nếu không có quy tắc chọn rõ, có thể xảy ra `NoUniqueBeanDefinitionException`.

`@Primary` đánh dấu **ứng viên ưu tiên** cho dependency một giá trị:

~~~java
@Bean
@Primary
Formatter basicFormatter() {
    return value -> "BASIC:" + value;
}
~~~

Với đúng một primary candidate phù hợp, tham số `Formatter formatter` được inject từ `basicFormatter`, thay vì bean còn lại.

Đây là các đoạn minh họa. Chương trình chạy đầy đủ ở mục 6 sử dụng hai implementation rõ ràng và cho output chứng minh bean được chọn.

### 4.2. `@Primary` không làm được những gì?

- **Không xóa/khóa bean khác.** Bean không primary vẫn có thể truy cập theo tên hoặc `@Qualifier`.
- **Không có nghĩa chạy trước khi startup.** Thứ tự khởi tạo không do `@Primary` điều khiển.
- **Không đổi scope** hoặc tạo thêm singleton.
- **Không chọn một phần tử duy nhất cho injection dạng collection.** Inject `List<Formatter>`, `Formatter[]`, map hoặc stream thường vẫn thấy các bean phù hợp, bao gồm cả bean primary; cần kiểm tra chính xác quy tắc thứ tự khi dùng collection.
- **Không bảo đảm mọi lookup mơ hồ được giải quyết** nếu có nhiều primary cùng phù hợp hoặc các qualifier/priority khác chi phối.

Nếu muốn một triển khai cụ thể tại một injection point, thường có thể dùng `@Qualifier("decoratedFormatter")`. `@Primary` phù hợp cho **mặc định của một nhóm ứng viên**, còn `@Qualifier` biểu đạt lựa chọn rõ tại nơi tiêu thụ. Tránh gắn primary tràn lan không có chiến lược.

### 4.3. XML tương ứng

~~~xml
<bean id="basicFormatter"
      class="demo.BasicFormatter"
      primary="true"/>

<bean id="decoratedFormatter"
      class="demo.DecoratedFormatter"/>
~~~

XML chỉ minh họa cấu trúc. Hai class cần được định nghĩa trong project nếu muốn chạy.

## 5. `depends-on`: thứ tự khởi tạo trước khi sử dụng

### 5.1. Khi nào cần `depends-on`?

Dependency thường được Spring suy ra từ constructor hoặc `@Bean` method parameters:

~~~java
@Bean
OrderService orderService(OrderRepository repository) {
    return new OrderService(repository);
}
~~~

Spring buộc phải cung cấp `OrderRepository` trước khi tạo `OrderService`. Không cần thêm `@DependsOn` chỉ để lặp lại quan hệ này.

Tuy nhiên, đôi lúc một bean cần **một hoạt động khởi tạo bên ngoài dependency trực tiếp** được thực hiện trước; chẳng hạn đăng ký driver/cấu hình cơ sở, nạp metadata hoặc khởi tạo một subsystem cần cho static API. Khi đó dùng `@DependsOn`:

~~~java
@Bean
@DependsOn("startupGate")
JobRunner jobRunner() {
    return new JobRunner();
}
~~~

Đây là ví dụ minh họa. Spring yêu cầu **bean có tên `startupGate` khởi tạo trước** bean `jobRunner`. Không có nghĩa Spring tự truyền instance `startupGate` làm constructor argument cho `JobRunner`.

Quan hệ này được khai báo bằng **bean name**, không phải class name. Nếu `startupGate` không tồn tại trong container, việc tạo `jobRunner` sẽ gặp lỗi cấu hình; nếu có vòng `depends-on`, container không thể hoàn tất khởi tạo.

### 5.2. Thứ tự hủy cho singleton

Với singleton beans có quan hệ `depends-on`, Spring còn có thể dùng quan hệ ngược trong thứ tự hủy: **bean phụ thuộc bị hủy trước bean mà nó depends-on**, để đối tượng nền tảng không bị đóng quá sớm. Quy tắc này áp dụng trong lifecycle được container quản lý; không suy ra tương tự cho prototype mà Spring không quản lý đầy đủ destruction.

**Không coi `@DependsOn` là scheduler.** Nó không lập lịch chạy nghiệp vụ, không thực hiện retry, không bảo đảm order cho các event bất đồng bộ và không thay `@Order` cho thứ tự trong collection hay post-processing.

### 5.3. XML tương ứng

~~~xml
<bean id="jobRunner"
      class="demo.JobRunner"
      depends-on="startupGate"/>

<bean id="startupGate"
      class="demo.StartupGate"/>
~~~

Bean `startupGate` được tạo trước `jobRunner`. Đừng nhầm thuộc tính `depends-on` với `constructor-arg ref`: cái đầu cưỡng chế thứ tự init, cái sau là **truyền dependency thật**.

## 6. Ví dụ Spring 6.2 / Java 17 runnable: kiểm tra cả bốn thuộc tính

Ví dụ console này không dùng Spring Boot, web server hay database. Nó chủ đích tạo **hai bean cùng interface `Formatter`**, một service lazy có `@DependsOn`, và một prototype có ID được cấp tăng dần.

### 6.1. Cấu trúc project

Yêu cầu JDK 17+, Maven, tải `spring-context` khi chạy lần đầu:

~~~text
bean-metadata-demo/
├── pom.xml
└── src/main/java/demo/BeanMetadataDemo.java
~~~

Tạo file `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>bean-metadata-demo</artifactId>
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
                    <mainClass>demo.BeanMetadataDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 6.2. Mã nguồn Java đầy đủ

Tạo `src/main/java/demo/BeanMetadataDemo.java`:

~~~java
package demo;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;

public final class BeanMetadataDemo {
    private BeanMetadataDemo() {}

    public interface Formatter {
        String format(String text);
    }

    public static final class MainFormatter implements Formatter {
        @Override
        public String format(String text) {
            return "MAIN:" + text;
        }
    }

    public static final class AlternateFormatter implements Formatter {
        @Override
        public String format(String text) {
            return "ALT:" + text;
        }
    }

    public static final class StartupGate {
        // Demo không có dependency bắt buộc phải truyền vào TaskRunner.
    }

    public record RequestToken(int id) {}

    public static final class TaskRunner {
        private final Formatter formatter;

        public TaskRunner(Formatter formatter) {
            this.formatter = Objects.requireNonNull(formatter);
        }

        public String run(String id) {
            return formatter.format(id);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        private static final AtomicInteger SEQUENCE = new AtomicInteger();

        @Bean
        @Primary
        public Formatter mainFormatter() {
            return new MainFormatter();
        }

        @Bean
        public Formatter alternateFormatter() {
            return new AlternateFormatter();
        }

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public RequestToken requestToken() {
            return new RequestToken(SEQUENCE.incrementAndGet());
        }

        @Bean
        @Lazy
        public StartupGate startupGate() {
            System.out.println("CREATE gate");
            return new StartupGate();
        }

        @Bean
        @Lazy
        @DependsOn("startupGate")
        public TaskRunner taskRunner(Formatter formatter) {
            System.out.println("CREATE runner");
            return new TaskRunner(formatter);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            System.out.println("BEFORE runner="
                    + context.getBeanFactory().containsSingleton("taskRunner"));

            TaskRunner runner = context.getBean(TaskRunner.class);
            System.out.println("RESULT " + runner.run("A01"));

            System.out.println("SAME runner="
                    + (runner == context.getBean(TaskRunner.class)));

            Formatter chosen = context.getBean(Formatter.class);
            Formatter alternate =
                    context.getBean("alternateFormatter", Formatter.class);

            System.out.println("PRIMARY " + chosen.format("B02"));
            System.out.println("ALTERNATE " + alternate.format("B02"));

            RequestToken one = context.getBean(RequestToken.class);
            RequestToken two = context.getBean(RequestToken.class);
            System.out.println("TOKENS " + one.id() + "," + two.id());
            System.out.println("TOKEN_DIFFERENT " + (one != two));

            System.out.println("AFTER runner="
                    + context.getBeanFactory().containsSingleton("taskRunner"));
        }
    }
}
~~~

### 6.3. Lệnh chạy và stdout

Từ thư mục `bean-metadata-demo`:

~~~bash
mvn -version
mvn -q compile exec:java
~~~

Các dòng stdout **do `BeanMetadataDemo` tự in** theo đúng thứ tự:

~~~text
BEFORE runner=false
CREATE gate
CREATE runner
RESULT MAIN:A01
SAME runner=true
PRIMARY MAIN:B02
ALTERNATE ALT:B02
TOKENS 1,2
TOKEN_DIFFERENT true
AFTER runner=true
~~~

`mvn -version` và Maven/plugin có thể phát sinh thêm phiên bản/log riêng. Khối trên chỉ mô tả đúng output của các `println` trong Java demo; không cam kết terminal chỉ có những dòng này.

### 6.4. Giải thích vì sao kết quả như vậy

1. `AnnotationConfigApplicationContext(AppConfig.class)` tạo và refresh context. Hai formatter singleton non-lazy có thể được tạo trong startup; chúng không tự in gì.
2. `taskRunner` là singleton `@Lazy`, không có bean eager nào yêu cầu nó. Ngay sau refresh, `containsSingleton("taskRunner")` trả `false`, in `BEFORE runner=false`.
3. Khi `getBean(TaskRunner.class)` yêu cầu tạo `taskRunner`, Spring thấy `@DependsOn("startupGate")`. Vì `startupGate` cũng đánh dấu `@Lazy` và trước đó chưa có bean nào cần nó, **Spring tạo gate trước**, in `CREATE gate`.
4. Sau gate, Spring giải quyết tham số `Formatter` của `taskRunner`. Có hai formatter, nhưng `mainFormatter` là ứng viên `@Primary` duy nhất, nên được chọn. Factory method `taskRunner` chạy và in `CREATE runner`.
5. `runner.run("A01")` dùng formatter primary, trả `MAIN:A01`. `TaskRunner` là singleton nên lần `getBean` thứ hai trả cùng object: `SAME runner=true`.
6. `getBean(Formatter.class)` trả `mainFormatter` được ưu tiên; lookup rõ tên `alternateFormatter` vẫn lấy formatter thứ hai. Vì vậy có hai dòng `PRIMARY MAIN:B02` và `ALTERNATE ALT:B02`.
7. `RequestToken` có scope prototype, nên hai lần `getBean(RequestToken.class)` chạy factory method hai lần. Bộ đếm trong demo tăng từ 0 tạo ID 1 rồi 2, và so identity cho `TOKEN_DIFFERENT true`.
8. Đến cuối block, singleton `taskRunner` đã tồn tại trong container nên `containsSingleton("taskRunner")` trả `true`.
9. `try-with-resources` đóng Spring context. Demo không có callbacks hủy cần in log nên kết quả stdout kết thúc ở dòng `AFTER runner=true`.

**Điểm mấu chốt của demo:** dòng `CREATE gate` xuất hiện **trước** `CREATE runner` vì `depends-on`. Chúng không hiện từ lúc startup vì hai bean lazy và không bị bean eager khác buộc khởi tạo.

Đừng suy từ output rằng mọi bean được tạo đúng theo thứ tự source code trong `AppConfig`: Spring chỉ bảo đảm những quan hệ dependency/cấu hình tương ứng; thứ tự không liên quan không nên được dùng làm logic nghiệp vụ.

## 7. Kết hợp bốn thuộc tính trong ứng dụng thực tế

### 7.1. Một service singleton cần prototype theo từng tác vụ

Nếu `TaskRunner` singleton cần một `RequestToken` mới trên mỗi lần gọi, **không** inject `RequestToken` trực tiếp vào constructor và kỳ vọng nó đổi tự động.

Thay vào đó, có thể inject `ObjectProvider<RequestToken>`:

~~~java
import org.springframework.beans.factory.ObjectProvider;

final class ProcessingService {
    private final ObjectProvider<RequestToken> tokens;

    ProcessingService(ObjectProvider<RequestToken> tokens) {
        this.tokens = tokens;
    }

    void process() {
        RequestToken token = tokens.getObject();
        // Dùng token mới cho lần xử lý hiện tại.
    }
}
~~~

Đây là **đoạn minh họa**, sử dụng record `RequestToken` của mục 6; không phải file runnable độc lập. `ObjectProvider.getObject()` yêu cầu bean từ container khi được gọi, nên với prototype thông thường sẽ nhận object mới.

### 7.2. Chọn primary nhưng vẫn truy cập bean khác

Một ứng dụng có thể có bean email và SMS cùng interface, đặt email làm mặc định `@Primary`. Tuy nhiên khi một use case cần SMS rõ ràng, `@Qualifier("smsNotifier")` ở injection point là cách nói rõ ý định. Không dùng `@Primary` để quyết định logic nghiệp vụ per-request, ví dụ chọn kênh từ dữ liệu người dùng; đó là yêu cầu thiết kế strategy/router khác.

### 7.3. Không nhầm `depends-on` với dependency injection

Nếu một service cần gọi phương thức của `ClientPool`, hãy khai báo `ClientPool` trong constructor hoặc factory method parameter. `@DependsOn("clientPool")` **không cung cấp tham chiếu `ClientPool`**. Nó chỉ buộc init trước. Dùng `depends-on` khi có **ràng buộc thứ tự ngoài quan hệ inject thông thường**, và ghi rõ vì sao.

### 7.4. Lazy với bean có tác dụng phụ khởi tạo

Cân nhắc:

- Bean làm việc nặng lúc startup có thực sự cần tạo ngay?
- Nếu hoãn, request đầu tiên có chịu latency/exception khó chấp nhận không?
- Có một singleton eager khác cần trực tiếp bean này không?
- Có thể thiết kế khởi tạo nền, preload sau startup hoặc factory/provider thay vì lazy chung chung không?

Đừng đặt side effect nghiệp vụ chính vào constructor chỉ để tận dụng `depends-on`; thứ tự xử lý nghiệp vụ thuộc method rõ ràng hoặc lifecycle hợp lý, không nên ẩn hoàn toàn trong việc tạo bean.

## 8. Các hiểu nhầm và lỗi thường gặp

| Hiểu nhầm / lỗi | Cách hiểu hoặc xử lý đúng |
| --- | --- |
| “`singleton` là một object cho cả JVM.” | Singleton của Spring thường theo bean definition trong **mỗi container**. |
| “`singleton` luôn thread-safe.” | Scope không tạo đồng bộ; cần tự thiết kế state/concurrency. |
| “`prototype` được destroy khi đóng context.” | Spring thường không quản lý đầy đủ destroy callbacks của prototype. |
| “Inject prototype vào singleton thì mỗi method nhận prototype mới.” | Injection thường xảy ra một lần; dùng provider/factory khi cần. |
| “`@Lazy` bảo đảm bean không khởi tạo ở startup.” | Eager dependency có thể buộc nó được tạo trước. |
| “`@Lazy` tương đương `@Scope("prototype")`.” | Một thuộc tính quyết định thời điểm, một thuộc tính quyết định scope. |
| “`@Primary` khiến bean được khởi tạo đầu tiên.” | Primary quyết định ưu tiên resolution, không phải init order. |
| “`@Primary` làm biến mất các bean cùng interface khác.” | Chúng vẫn tồn tại và có thể tra theo tên/qualifier. |
| “Hai primary cùng type chắc chắn chọn được một.” | Có thể vẫn ambiguous; cấu hình lựa chọn phải không mơ hồ. |
| “`@DependsOn` tự truyền dependency cho constructor.” | Không; nó buộc init order theo bean name. |
| “`@DependsOn` là cách phổ biến để giải quyết mọi vòng phụ thuộc.” | Không; có thể tạo hoặc bộc lộ vòng dependency, cần thiết kế lại. |
| “`@DependsOn` điều khiển mọi event/job theo thứ tự.” | Không; chỉ liên quan lifecycle bean tương ứng. |
| “Scope request/session chạy trong console context.” | Cần web-aware scope và request/session context thích hợp. |
| “Prototype và singleton luôn có cùng cơ chế hủy.” | Vòng đời destroy prototype khác singleton. |
| “Lỗi lazy luôn lộ lúc startup.” | Có thể chỉ lộ lúc lần đầu bean được yêu cầu. |
| “Có `@Bean` là đã có bean.” | Class cấu hình phải được đăng ký/scan, metadata được container xử lý. |

### Đọc lỗi liên quan

- `NoSuchBeanDefinitionException`: `@DependsOn` trỏ sai tên bean, hoặc dependency không đăng ký.
- `NoUniqueBeanDefinitionException`: nhiều ứng viên cho một dependency mà không chọn được; cân nhắc `@Primary` hoặc `@Qualifier`.
- `BeanCreationException`: lỗi constructor/factory/lifecycle, có thể xảy ra muộn với bean lazy.
- `BeanCurrentlyInCreationException` hoặc lỗi vòng phụ thuộc: graph tạo bean có vòng không giải quyết được.
- `ScopeNotActiveException`: cố truy cập scoped bean ngoài context scope hoạt động.

Khi debug, xem **nguyên nhân gốc** và bean name trong stack trace. Đừng giải quyết bằng cách đổi tất cả sang prototype hoặc thêm `@Lazy` tràn lan nếu graph thiết kế có vấn đề.

## 9. Bài tập tự kiểm tra (có đáp án)

**1.** Hai lần `getBean("catalogService")` nhận cùng reference. Có thể kết luận object duy nhất trong JVM không?

**Đáp án:** Không. Với singleton, thường là cùng instance của bean trong **context đang xét**. Context khác hoặc bean definition khác có thể tạo instance khác.

**2.** Một `@Lazy` bean được inject trực tiếp vào eager singleton. Nó có luôn đợi đến lúc được gọi mới tạo không?

**Đáp án:** Không. Để tạo eager singleton, Spring có thể phải giải quyết và tạo dependency lazy trước. Nếu muốn hoãn, sử dụng provider/lazy proxy có chủ đích.

**3.** Hai bean cùng interface `Formatter`; một bean có `@Primary`. Còn lấy được bean kia không?

**Đáp án:** Có. Có thể lấy theo tên hoặc dùng `@Qualifier` rõ ràng. Primary chủ yếu giải quyết một injection point khi nhiều ứng viên.

**4.** Một bean `jobRunner` có `@DependsOn("startupGate")`. `JobRunner` có tự nhận tham số `StartupGate` không?

**Đáp án:** Không. Đó là một ràng buộc thứ tự khởi tạo. Nếu cần object để gọi method thì khai báo dependency qua constructor/factory parameter.

**5.** Lý do `CREATE gate` xuất hiện trước `CREATE runner` trong demo?

**Đáp án:** `taskRunner` có `@DependsOn("startupGate")`. Khi runner được yêu cầu, Spring tạo gate trước rồi mới tạo runner.

**6.** Vì sao hai lần `getBean(RequestToken.class)` cho ID 1 và 2?

**Đáp án:** Bean `RequestToken` là prototype, nên factory được gọi cho mỗi yêu cầu. `AtomicInteger` dùng để cấp ID tăng dần trong một lần chạy JVM demo. Hai instance khác nhau theo `==`.

**7.** Nếu service singleton có tham số constructor `RequestToken token` là prototype, mỗi lần gọi `service.run()` có token mới không?

**Đáp án:** Không theo thiết kế injection trực tiếp thông thường. Singleton giữ instance đã inject lúc tạo; cần `ObjectProvider<RequestToken>` hoặc factory khi muốn cấp mới cho mỗi lần chạy.

**8.** Với singleton A `@DependsOn("B")`, nếu có callback destroy, B có bị hủy trước A không?

**Đáp án:** Không trong cơ chế hủy phụ thuộc singleton được Spring quản lý: bean phụ thuộc **A thường bị hủy trước B**, giúp B còn sẵn khi A dọn dẹp.

**9.** Nên dùng `@Primary` để chọn phương án thanh toán dựa trên loại đơn hàng ở runtime?

**Đáp án:** Không trực tiếp. Primary là metadata resolution một candidate mặc định khi lắp bean; lựa chọn theo dữ liệu nghiệp vụ tại runtime cần strategy/router hoặc kiến trúc phù hợp.

## 10. Checklist dùng metadata bean có chủ đích

- [ ] Bean được đăng ký qua `@Configuration`/`@Bean`, component scan hoặc bean definition đúng context.
- [ ] Xác định bean nào nên stateless singleton và bean nào cần instance ngắn hạn.
- [ ] Không để mutable state không an toàn trong singleton dùng nhiều thread.
- [ ] Với prototype, có kế hoạch sở hữu/dọn tài nguyên khi cần.
- [ ] Nếu singleton cần prototype nhiều lần, chọn provider/factory, không mong injection trực tiếp tự đổi.
- [ ] Chỉ đặt `@Lazy` khi thực sự cần trì hoãn tạo và đã xem xét eager dependencies.
- [ ] Có kế hoạch quan sát lỗi có thể xảy ra lúc sử dụng đầu tiên đối với lazy bean.
- [ ] Với nhiều bean cùng type, chỉ định primary/qualifier theo quy tắc rõ ràng.
- [ ] Không coi primary là cơ chế lập lịch init hoặc loại trừ bean khác.
- [ ] Đặt `@DependsOn` bằng **đúng bean name**, chỉ khi thứ tự khởi tạo không thể biểu đạt bằng dependency trực tiếp.
- [ ] Hiểu thứ tự hủy singleton phụ thuộc và không áp dụng máy móc cho prototype.
- [ ] Không dùng `@DependsOn` để thay truyền dependency trong constructor.
- [ ] Kiểm thử output/lifecycle bằng ứng dụng nhỏ trước khi dùng cho graph lớn.
- [ ] Khi mở rộng sang request/session scope, dùng đúng web-aware context.
- [ ] Không đồng nhất bốn thuộc tính với AOP proxy, transaction management hay HTTP routing.

## 11. Tài liệu tham khảo Spring

- [Bean scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)
- [Lazy-initialized beans](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-lazy-init.html)
- [Using depends-on](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-dependson.html)
- [Classpath scanning, @Scope và @Primary](https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html)

**Kết luận:** `scope` quyết định **phạm vi instance**, `lazy-init` quyết định **khi nào tạo** (trong điều kiện dependency graph), `primary` chọn **ứng viên được ưu tiên** cho một dependency, và `depends-on` buộc **khởi tạo bean khác trước**. Biết đúng giới hạn từng thuộc tính giúp thiết kế bean graph dự đoán được, tránh lỗi vòng đời, tránh mơ hồ autowiring và không biến metadata thành nơi giấu luồng nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-definition-006">Giá trị cấu hình và tham chiếu giữa các bean</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị cấu hình và tham chiếu giữa các bean — Spring Framework Core Container

## 1. Mục tiêu: giá trị và tham chiếu không phải một khái niệm

Khi tạo một bean trong Spring, đôi khi bạn cần truyền **dữ liệu cấu hình** như tên kênh, URL, số lần thử, tiền tố thông báo hoặc timeout. Lúc khác, bạn cần nối bean ấy với **một bean khác** do Spring quản lý. Hai nhu cầu này có thể cùng xuất hiện ở constructor, factory method hoặc setter, nhưng phải phân biệt rõ:

- **Configuration value:** dữ liệu String, số, boolean, enum, URI hoặc một value object phù hợp. Nó có thể là literal hoặc được lấy từ `Environment`/property source.
- **Bean reference:** yêu cầu Spring lấy **object bean** theo tên hoặc phân giải một dependency tương thích kiểu để truyền cho bean hiện tại.
- **Bean name:** chuỗi định danh một bean trong registry. Bản thân chuỗi tên **không tự trở thành object reference** nếu chỉ truyền vào một property String.
- **BeanDefinition:** metadata cho biết cách Spring tạo và cấu hình bean, trong đó có thể chứa **literal value** và **RuntimeBeanReference** trước khi object thực sự được lắp ghép.

Sau bài, bạn có thể:

1. Phân biệt value, reference, bean name, bean definition và managed object.
2. Sử dụng `@Bean`, constructor injection và `Environment.getRequiredProperty(...)`.
3. Đăng ký definition bằng `BeanDefinitionBuilder.addPropertyValue(...)` và `addPropertyReference(...)`.
4. Quan sát metadata `RuntimeBeanReference` trước khi đối chiếu object thực tế đã được inject.
5. Biết vai trò của `@Value`, `@PropertySource`, constructor arguments, property conversion và thứ tự property sources.
6. Xử lý các lỗi thiếu key, thiếu bean, sai setter/type và nhiều ứng viên.
7. Chạy chương trình Spring Framework **Java 17+** độc lập, đối chiếu Maven/`javac`/`java` và stdout.

**Phạm vi:** tập trung vào cách Spring Core Container cung cấp **giá trị cấu hình** và **tham chiếu giữa bean**. Không cần Spring Boot, MVC, database hoặc hệ thống cấu hình phân tán.

## 2. Một đồ thị nhỏ có hai loại cạnh

Ứng dụng gửi thông báo có hai thành phần:

- `MessageGateway` cần **giá trị** `notify.channel = MAIL`.
- `NotificationService` cần **bean object** `MessageGateway` và **giá trị** `notify.prefix = Xin chao`.
- `ManualSender` cần giá trị `label = manual` cùng tham chiếu tới bean `messageGateway`.

~~~mermaid
flowchart LR
    E1["Environment: notify.channel=MAIL"] -->|"String value"| G["Bean: messageGateway"]
    E2["Environment: notify.prefix=Xin chao"] -->|"String value"| N["Bean: notificationService"]
    G -->|"Bean object"| N
    V["BeanDefinition: label=manual"] -->|"Literal"| M["Bean: manualSender"]
    G -->|"Bean reference"| M
~~~

| Đầu vào | Spring/Java thực hiện | Kết quả |
| --- | --- | --- |
| Chuỗi literal `"manual"` | Gán vào property kiểu `String` | Một giá trị, không phải bean |
| `Environment.getRequiredProperty("notify.prefix")` | Tìm key trong property sources | String `"Xin chao"` |
| Tham số `MessageGateway gateway` ở `@Bean` | Giải quyết bean candidate phù hợp | Managed object `MessageGateway` |
| `addPropertyReference("gateway", "messageGateway")` | Tra registry, resolve reference | Object bean để gọi setter |
| `RuntimeBeanReference.getBeanName()` | Đọc metadata | Tên bean đích, không phải chính object |

**Lưu ý ngữ nghĩa:** cả `"MAIL"` và `"messageGateway"` có thể được viết dưới dạng chuỗi trong cấu hình, nhưng chuỗi thứ nhất là dữ liệu nghiệp vụ; chuỗi thứ hai dùng làm **địa chỉ reference** khi đặt vào API `addPropertyReference`.

## 3. Truyền value và bean object bằng @Bean factory methods

JavaConfig có thể viết:

~~~java
@Configuration(proxyBeanMethods = false)
class AppConfig {
    @Bean
    MessageGateway messageGateway(Environment environment) {
        return new MessageGateway(
                environment.getRequiredProperty("notify.channel"));
    }

    @Bean
    NotificationService notificationService(
            MessageGateway gateway, Environment environment) {
        String prefix =
                environment.getRequiredProperty("notify.prefix");
        return new NotificationService(gateway, prefix);
    }
}
~~~

**Đây là snippet minh họa**, chưa có import/định nghĩa các kiểu. File Java hoàn chỉnh ở mục 5.

- Tham số `Environment` là khả năng Spring có thể cung cấp khi gọi factory method; bạn dùng nó đọc dữ liệu từ property sources.
- Tham số `MessageGateway gateway` là **dependency đối tượng**, cần bean instance phù hợp trong container.
- `NotificationService` nhận cả `gateway` (object) và `prefix` (String) qua constructor; nó không cần trực tiếp biết property key hay gọi container.
- `@Configuration(proxyBeanMethods = false)` phù hợp khi các `@Bean` methods không gọi trực tiếp nhau. Nếu tự gọi `messageGateway(env)` bên trong method khác, đó là lời gọi Java bình thường có thể tạo object ngoài bean singleton đúng ý định.

### Có bắt buộc mọi giá trị đều xuất phát từ Environment không?

Không. Một cấu hình đơn giản có thể chứa:

~~~java
@Bean
MessageGateway messageGateway() {
    return new MessageGateway("MAIL");
}
~~~

Chữ `"MAIL"` là literal value hợp lệ. Nhưng nếu kênh khác nhau giữa môi trường, dùng `Environment` hoặc nguồn cấu hình bên ngoài giúp không phải sửa code factory mỗi lần deployment.

Một value thuần như `"MAIL"` không cần đăng ký thành một bean riêng, trừ khi bạn có **value object/hợp đồng quản lý** thực sự có ích.

## 4. BeanDefinitionBuilder: phân biệt property value và property reference

Cùng ý tưởng có thể biểu đạt bằng metadata programmatically:

~~~java
BeanDefinition senderDefinition = BeanDefinitionBuilder
        .genericBeanDefinition(ManualSender.class)
        .addPropertyValue("label", "manual")
        .addPropertyReference("gateway", "messageGateway")
        .getBeanDefinition();

context.registerBeanDefinition("manualSender", senderDefinition);
~~~

**Snippet**, cần `ManualSender` có no-argument constructor và hai setter phù hợp. Nó chưa phải tệp Java độc lập.

Hai builder methods không tương đương:

- `addPropertyValue("label", "manual")` lưu literal, để container gán value cho setter `setLabel(String)`.
- `addPropertyReference("gateway", "messageGateway")` lưu thông tin **tham chiếu tới bean**; khi tạo `manualSender`, Spring resolve bean theo tên và gán object vào setter `setGateway(MessageGateway)`.

Một sai lầm thường gặp:

~~~java
.addPropertyValue("gateway", "messageGateway")
~~~

Đây **không** là cách đúng để yêu cầu Spring inject managed `MessageGateway`. Nó đang cung cấp String literal cho property cần object kiểu `MessageGateway`; hệ thống conversion thông thường không tự biến tên bean bất kỳ thành reference mong muốn. Hãy dùng `addPropertyReference(...)`.

### RuntimeBeanReference là metadata, không phải object bean

Có thể đọc lại:

~~~java
BeanDefinition definition =
        context.getBeanFactory().getBeanDefinition("manualSender");
PropertyValue reference =
        definition.getPropertyValues().getPropertyValue("gateway");

RuntimeBeanReference ref =
        (RuntimeBeanReference) reference.getValue();
System.out.println(ref.getBeanName()); // messageGateway
~~~

Đây là đoạn minh họa cần import `PropertyValue` và `RuntimeBeanReference`; ví dụ đầy đủ ở phần sau có các import đó.

Khi Spring **chưa gán property**, definition có thể giữ metadata `RuntimeBeanReference`. Sau khi tạo instance, `manualSender.gateway()` phải là **object** thật. Để chắc chắn wiring đúng, kiểm tra cả definition lẫn identity/runtime behavior.

### Setter injection khác constructor injection

Mã builder ở đây cố ý dùng setters để thấy rõ `addPropertyValue` và `addPropertyReference`. Setter injection phù hợp khi property cần thiết có hợp đồng cấu hình rõ; tuy nhiên, **constructor injection thường thích hợp hơn với dependency bắt buộc**, vì instance được tạo đã có collaborator cần thiết. Bạn cũng có thể mô tả constructor args trong builder:

~~~java
BeanDefinition definition = BeanDefinitionBuilder
        .genericBeanDefinition(AlertWorker.class)
        .addConstructorArgValue("batch")
        .addConstructorArgReference("messageGateway")
        .getBeanDefinition();
~~~

Đây là **snippet minh họa không độc lập**: `AlertWorker` chưa được định nghĩa, và cần constructor tương thích như `AlertWorker(String mode, MessageGateway gateway)`. Về ngữ nghĩa, `addConstructorArgValue` đưa literal; `addConstructorArgReference` yêu cầu resolve một bean object.

## 5. Project Java 17+ runnable với @Bean và BeanDefinitionBuilder

Dùng **Spring Framework 6.2.19**, JDK 17+ và Maven. Phiên bản được ghim cho ví dụ, **không khẳng định là bản Spring mới nhất**. Không cần Spring Boot, web server hoặc database.

~~~text
bean-values-references-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── BeanValuesReferencesDemo.java
~~~

### 5.1. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>bean-values-references-demo</artifactId>
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
                <version>3.14.1</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.1</version>
                <configuration>
                    <mainClass>demo.BeanValuesReferencesDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Module `spring-context` kéo theo Spring dependencies liên quan. Khi chạy `javac` trực tiếp, cần classpath đủ các JAR đó; không thể chỉ đưa file nguồn Java cho compiler rồi chờ nó tự tìm Spring.

### 5.2. BeanValuesReferencesDemo.java

Tạo file `src/main/java/demo/BeanValuesReferencesDemo.java`:

~~~java
package demo;

import java.util.Map;
import java.util.Objects;

import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

public final class BeanValuesReferencesDemo {
    private BeanValuesReferencesDemo() {}

    public static final class MessageGateway {
        private final String channel;

        public MessageGateway(String channel) {
            this.channel = Objects.requireNonNull(channel);
        }

        public String channel() {
            return channel;
        }
    }

    public static final class NotificationService {
        private final MessageGateway gateway;
        private final String prefix;

        public NotificationService(MessageGateway gateway, String prefix) {
            this.gateway = Objects.requireNonNull(gateway);
            this.prefix = Objects.requireNonNull(prefix);
        }

        public MessageGateway gateway() {
            return gateway;
        }

        public String notification(String name) {
            return prefix + " " + name + " (" + gateway.channel() + ")";
        }
    }

    public static final class ManualSender {
        private String label;
        private MessageGateway gateway;

        public ManualSender() {}

        public void setLabel(String label) {
            this.label = Objects.requireNonNull(label);
        }

        public void setGateway(MessageGateway gateway) {
            this.gateway = Objects.requireNonNull(gateway);
        }

        public MessageGateway gateway() {
            return gateway;
        }

        public String send(String name) {
            return label + ":" + gateway.channel() + ":" + name;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        public MessageGateway messageGateway(Environment env) {
            return new MessageGateway(
                    env.getRequiredProperty("notify.channel"));
        }

        @Bean
        public NotificationService notificationService(
                MessageGateway gateway, Environment env) {
            return new NotificationService(gateway,
                    env.getRequiredProperty("notify.prefix"));
        }
    }

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext();

        try (context) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("lessonProperties",
                            Map.<String, Object>of(
                                    "notify.channel", "MAIL",
                                    "notify.prefix", "Xin chao")));

            context.register(AppConfig.class);

            BeanDefinition senderDefinition = BeanDefinitionBuilder
                    .genericBeanDefinition(ManualSender.class)
                    .addPropertyValue("label", "manual")
                    .addPropertyReference("gateway", "messageGateway")
                    .getBeanDefinition();

            context.registerBeanDefinition("manualSender", senderDefinition);
            context.refresh();

            MessageGateway gateway = context.getBean(MessageGateway.class);
            NotificationService service =
                    context.getBean(NotificationService.class);
            ManualSender manual = context.getBean(ManualSender.class);

            BeanDefinition stored =
                    context.getBeanFactory()
                            .getBeanDefinition("manualSender");
            PropertyValue label =
                    stored.getPropertyValues().getPropertyValue("label");
            PropertyValue gatewayReference =
                    stored.getPropertyValues().getPropertyValue("gateway");
            RuntimeBeanReference ref =
                    (RuntimeBeanReference) gatewayReference.getValue();

            System.out.println("propertyPrefix=" +
                    context.getEnvironment().getProperty("notify.prefix"));
            System.out.println("gatewayChannel=" + gateway.channel());
            System.out.println("literalValue=" + label.getValue());
            System.out.println("referenceBean=" + ref.getBeanName());
            System.out.println("sameGateway=" +
                    (service.gateway() == gateway
                     && manual.gateway() == gateway));
            System.out.println("notification=" +
                    service.notification("Lan"));
            System.out.println("manualSend=" + manual.send("Lan"));
            System.out.println("senderSingleton=" +
                    (manual == context.getBean(ManualSender.class)));
        }
    }
}
~~~

**Tính tự chứa của ví dụ:** một public top-level class khớp tên tệp, nested types phù hợp. `Map.<String, Object>of(...)` tạo property source với kiểu map phù hợp; Java 17 hỗ trợ. Các bean definitions và nguồn property được đăng ký **trước `refresh()`**.

`MessageGateway` được cấu hình từ value `"MAIL"`; `NotificationService` lấy một reference và một value; `ManualSender` nhận một literal qua `setLabel` và một bean object qua `setGateway`. Chương trình đối chiếu metadata và runtime để không nhầm hai tầng này.

### 5.3. Chạy bằng Maven

Trong thư mục `bean-values-references-demo`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

**Đúng tám dòng stdout do phương thức main in khi chạy thành công:**

~~~text
propertyPrefix=Xin chao
gatewayChannel=MAIL
literalValue=manual
referenceBean=messageGateway
sameGateway=true
notification=Xin chao Lan (MAIL)
manualSend=manual:MAIL:Lan
senderSingleton=true
~~~

Các dòng phiên bản JDK/Maven và log Maven có thể xuất hiện tùy máy, **không** thuộc tám dòng do code demo in. Ví dụ không dùng random, đồng hồ hoặc thứ tự iteration để quyết định output.

### 5.4. Biên dịch và chạy trực tiếp bằng javac/java

Tạo classpath dependency bằng Maven:

~~~bash
mvn -q dependency:build-classpath -Dmdep.outputFile=classpath.txt
~~~

**Linux/macOS (bash/zsh):**

~~~bash
mkdir -p out
javac --release 17 -encoding UTF-8 \
  -cp "$(cat classpath.txt)" \
  -d out src/main/java/demo/BeanValuesReferencesDemo.java
java -cp "out:$(cat classpath.txt)" demo.BeanValuesReferencesDemo
~~~

**Windows PowerShell:**

~~~powershell
$deps = (Get-Content -Raw classpath.txt).Trim()
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -encoding UTF-8 -cp "$deps" -d out src/main/java/demo/BeanValuesReferencesDemo.java
java -cp "out;$deps" demo.BeanValuesReferencesDemo
~~~

Dấu phân tách classpath là `:` trên Unix, `;` trên Windows. Chạy `javac` mà thiếu Spring JAR dẫn tới lỗi compile kiểu `package org.springframework... does not exist`, không phải bằng chứng cấu hình bean sai.

## 6. Diễn giải tám dòng output

| Output | Kết luận |
| --- | --- |
| `propertyPrefix=Xin chao` | `Environment` đã cung cấp String từ property source |
| `gatewayChannel=MAIL` | Bean Gateway được tạo bằng giá trị `notify.channel` |
| `literalValue=manual` | BeanDefinition lưu literal property value |
| `referenceBean=messageGateway` | BeanDefinition lưu tên bean đích qua `RuntimeBeanReference` |
| `sameGateway=true` | Hai consumer thực tế được nối tới **cùng object bean singleton** |
| `notification=Xin chao Lan (MAIL)` | Service kết hợp data value và bean collaborator |
| `manualSend=manual:MAIL:Lan` | Setter cho value và reference đã được áp dụng |
| `senderSingleton=true` | Cùng bean name/type trong một context cho cùng instance singleton |

Nếu chỉ nhìn `referenceBean=messageGateway`, ta mới biết **metadata định hướng tới bean nào**; `sameGateway=true` mới kiểm chứng collaborator object ở runtime. BeanDefinition không đồng nghĩa bean instance.

## 7. Environment, property sources và thứ tự ưu tiên

Spring `Environment` tập hợp nhiều nguồn property như system properties, environment variables hoặc property sources mà ứng dụng đăng ký. Việc **nguồn nào thắng khi trùng key** là một phần của chính sách cấu hình.

Trong chương trình, mã:

~~~java
context.getEnvironment().getPropertySources().addFirst(
        new MapPropertySource("lessonProperties",
                Map.<String, Object>of(
                        "notify.channel", "MAIL",
                        "notify.prefix", "Xin chao")));
~~~

đặt dữ liệu demo lên đầu danh sách, nên nếu máy chạy có key trùng trong nguồn khác, hai giá trị trong bộ nhớ vẫn được ưu tiên. Đây là **quyết định phục vụ tính tái lập**, không có nghĩa ứng dụng thật luôn nên ghi đè mọi cấu hình triển khai.

### Đưa properties ra file

Một ứng dụng khác có thể dùng `src/main/resources/app.properties`:

~~~properties
notify.channel=MAIL
notify.prefix=Xin chao
~~~

Và đăng ký resource:

~~~java
@Configuration
@PropertySource("classpath:app.properties")
class AppConfig {
    // Những factory methods @Bean cần thiết.
}
~~~

**Đây là minh họa, không phải phiên bản thứ hai của file runnable.** Muốn chạy phải tạo file properties và viết config đầy đủ. `@PropertySource` cần nguồn resource tồn tại; không phải chỉ có annotation là dữ liệu tự xuất hiện.

### @Value thay thế Environment trong một số điểm injection

Một cách biểu đạt khác:

~~~java
@Bean
NotificationService notificationService(
        MessageGateway gateway,
        @Value("${notify.prefix}") String prefix) {
    return new NotificationService(gateway, prefix);
}
~~~

Đoạn này chỉ minh họa cú pháp và cần context cấu hình placeholder resolution phù hợp. `@Value` có thể inject value từ property placeholder hoặc biểu thức theo môi trường Spring. Trong bài runnable, dùng `Environment.getRequiredProperty` để **thiếu key bắt buộc thì lỗi rõ ràng**, không phụ thuộc việc placeholder chưa được giải quyết có thể được xử lý khác nhau theo cấu hình.

Không cần biến toàn bộ property thành `@Bean String` chỉ để dùng trong một factory method.

## 8. Bean reference theo tên và injection theo kiểu

Có hai phong cách cấu hình khác nhau:

- `@Bean NotificationService notificationService(MessageGateway gateway, ...)`: yêu cầu một candidate theo **type** và quy tắc autowiring.
- `addPropertyReference("gateway", "messageGateway")`: reference được chỉ ra **theo tên bean** trong metadata.

Khi có **hai bean cùng type** như `messageGatewayA` và `messageGatewayB`, injection theo kiểu đơn trị có thể không chọn được một ứng viên. Spring có các cơ chế:

- `@Qualifier("messageGatewayA")` khi điểm injection yêu cầu một bean cụ thể.
- `@Primary` khi cần một ứng viên mặc định có lý do rõ ràng.
- Tên bean tại composition root như `context.getBean("messageGatewayA", MessageGateway.class)`.
- Injection collection hoặc `ObjectProvider<MessageGateway>` khi thực sự cần nhiều candidates hoặc truy xuất linh hoạt.

Không diễn giải “hai bean cùng kiểu” là lỗi Java; vấn đề là **điểm injection cần một đối tượng mà Spring chưa chọn được**.

Nếu metadata reference chỉ tới một bean không tồn tại, Spring sẽ thất bại khi tạo object cần reference đó. Nó **không tự dùng String bean name làm giá trị** thay thế.

## 9. Value conversion và kiểm tra dữ liệu cấu hình

Không phải mọi configuration value đều là String trong object cuối cùng. Ví dụ:

~~~java
int retries =
        environment.getRequiredProperty("notify.retries", Integer.class);
~~~

Đây là snippet hợp lệ trong method có biến `Environment environment` và key được cung cấp. Spring có thể chuyển value dạng số sang kiểu `Integer` nhờ conversion phù hợp; nếu thiếu key hoặc không convert được, thường ném exception. **Conversion thành công không chứng minh dữ liệu phù hợp nghiệp vụ.**

Các quy tắc quan trọng:

- **Required value**: fail fast khi thiếu; không âm thầm dùng giá trị mặc định che lỗi triển khai.
- **Optional value**: chỉ dùng default khi hợp đồng cho phép thiếu cấu hình.
- **Validation**: kiểm tra timeout > 0, tên kênh không rỗng, địa chỉ hợp lệ, số lần thử trong giới hạn; làm rõ exception ở ranh giới cấu hình.
- **Type clarity**: property String tên bean không tự trở thành reference; hãy dùng đúng API.
- **Secrets**: tránh ghi token/password vào README thực tế, mã nguồn hoặc stdout. Dùng nguồn cấu hình bí mật được quản lý phù hợp.
- **Runtime changes**: việc cập nhật property source sau khi singleton đã tạo không tự tái inject/rebuild các object đó; cần cơ chế cấu hình động có chủ đích nếu muốn thay đổi runtime.

Đối với `BeanDefinitionBuilder.addPropertyValue`, Spring còn có thể dùng type conversion khi gán setter; nhưng không được kỳ vọng nó suy ra dependency object từ chuỗi tên bean khi người lập trình chưa tạo reference.

## 10. Scope và vòng đời liên quan tới tham chiếu

| Trường hợp | Điều xảy ra thường thấy |
| --- | --- |
| Gateway singleton mặc định | Service và ManualSender trong demo cùng nhận một object |
| Gateway prototype | Mỗi lần container tạo/cung cấp prototype theo yêu cầu có thể là instance khác |
| Prototype inject trực tiếp vào singleton | Thông thường resolve **một lần khi singleton được tạo**, không tự tạo object mới mỗi method call |
| Bean lazy | Có thể hoãn khởi tạo bean; logic dependency vẫn tồn tại |
| Reference tới alias | Alias dẫn tới bean được đặt tên tương ứng; không tự tạo definition mới |
| Bean tạo bằng new ngoài container | Không tự nhận DI/lifecycle của Spring |
| Context đã close | Không dùng context như một nguồn lấy bean mới |

Nếu service thực sự muốn mỗi lần gọi một Gateway mới, phải thiết kế provider/lookup theo scope thích hợp, **không chỉ đổi Gateway thành prototype và giữ trường reference mãi trong singleton**.

Vòng constructor/factory dependency A→B→A là dấu hiệu vấn đề kiến trúc; chuyển reference từ type sang String bean name không tự giải quyết chu trình.

## 11. Các lỗi thường gặp

| Lỗi/triệu chứng | Khả năng nguyên nhân | Cách kiểm tra |
| --- | --- | --- |
| `getRequiredProperty(...)` thất bại | Thiếu required key hoặc property source không được nạp | Kiểm tra key, thứ tự source, profile |
| Property sai định dạng | Không convert được tới kiểu setter/constructor | Kiểm tra type/converter và validation |
| `NoSuchBeanDefinitionException` | Bean name trong reference chưa được đăng ký | Kiểm tra `messageGateway` và registration |
| `UnsatisfiedDependencyException` | Factory/constructor cần bean mà Spring không cung cấp | Kiểm tra kiểu, qualifier, scan, config |
| `NoUniqueBeanDefinitionException` | Nhiều candidates cùng type, không rõ lựa chọn | Dùng qualifier/primary hoặc sửa wiring |
| `NotWritablePropertyException` | Programmatic property thiếu setter đúng tên/kiểu | Kiểm tra JavaBean getter/setter và public accessibility |
| Truyền `addPropertyValue("gateway","messageGateway")` | Nhầm chuỗi tên với bean reference | Dùng `addPropertyReference` |
| Trông như setter không chạy | Chưa tạo instance, bean lazy hoặc tên property sai | Lấy bean/đọc lỗi tạo bean, kiểm tra setters |
| `@Value` không resolve property như dự định | Thiếu property source/configurer phù hợp | Kiểm tra Environment và placeholder resolution |
| Programmatic registration sau refresh | Metadata không được xử lý đúng thời điểm mong muốn | Đăng ký trước `refresh()` |
| Runtime value mới không có tác dụng | Singleton đã tạo với value cũ | Dùng cơ chế reconfiguration được hỗ trợ |
| `package org.springframework... does not exist` | Classpath Maven/`javac` thiếu Spring | Kiểm tra pom và classpath |
| Bean mutable singleton có hành vi đua luồng | Nhiều caller chia sẻ một instance | Thiết kế thread-safe/scope phù hợp |

Cách debug: tách **lớp cấu hình (metadata)** khỏi **lớp đối tượng (runtime)**. Hỏi *property này là value hay reference?*, kiểm tra đúng key/bean name, xem type/setter, lần theo `Caused by` rồi đối chiếu object thực tế.

## 12. Kiểm thử đúng mục tiêu

**Unit test không cần Spring:** tạo Gateway và NotificationService bằng constructor:

~~~java
MessageGateway gateway = new MessageGateway("TEST");
NotificationService service = new NotificationService(gateway, "Hello");
System.out.println(service.notification("An")); // Hello An (TEST)
~~~

Đoạn này là snippet trong method/class có quyền truy cập nested types, không phải file độc lập. Nó kiểm tra logic Java mà không xác nhận bean registration.

**Integration test cấu hình:** tạo Context như ví dụ chính, rồi:

- Xác nhận metadata label literal và gateway RuntimeBeanReference đúng.
- Kiểm tra `service.gateway() == context.getBean(MessageGateway.class)`.
- Kiểm tra `manual.gateway()` cũng là object mong muốn.
- Test trường hợp thiếu `notify.prefix` hoặc thiếu bean `messageGateway` trong **cấu hình test riêng**; không phá demo happy path rồi so stdout cũ.
- Test property conversion/validation cho những key bắt buộc.

Đừng dùng Java `assert` như bảo đảm runtime bắt buộc vì mặc định có thể bị tắt khi thiếu `-ea`. Dùng exception hoặc test assertions thực sự được chạy.

## 13. Câu hỏi tự kiểm tra

**1.** Vì sao `addPropertyValue("gateway", "messageGateway")` không giống `addPropertyReference("gateway", "messageGateway")`?

**Trả lời:** Một cái chỉ cung cấp literal String, cái kia yêu cầu container resolve managed bean theo tên và inject object.

**2.** Vì sao tham số `MessageGateway gateway` của `@Bean notificationService(...)` là bean reference dù không có chữ `ref`?

**Trả lời:** Spring resolve tham số factory method theo kiểu/quy tắc autowiring rồi truyền bean object phù hợp.

**3.** `RuntimeBeanReference.getBeanName()` trả gì và khác object thật thế nào?

**Trả lời:** Trả tên bean đích, ở đây `"messageGateway"`. Field của `ManualSender` sau khi được tạo là object `MessageGateway`, không phải chuỗi tên.

**4.** Giá trị `notify.prefix` có cần trở thành bean String không?

**Trả lời:** Không. Factory method có thể đọc value trực tiếp từ Environment và truyền vào constructor; tạo value object/bean riêng chỉ khi có lý do thiết kế.

**5.** Vì sao ví dụ dùng `MapPropertySource.addFirst`?

**Trả lời:** Để dữ liệu trong bộ nhớ có ưu tiên cao, giúp output tái lập khi máy có property trùng tên. Ứng dụng thật cần thiết kế chính sách ưu tiên theo môi trường.

**6.** Nếu Gateway chuyển sang prototype, `sameGateway=true` còn chắc chắn không?

**Trả lời:** Không. Scope quyết định cách cấp phát instance; các injection/lookup có thể nhận những instance khác nhau.

**7.** Vì sao không gọi trực tiếp factory method `messageGateway(...)` từ method `notificationService(...)` khi đặt `proxyBeanMethods=false`?

**Trả lời:** Đó là lời gọi Java bình thường và có thể tạo object không phải managed bean được mong đợi; dùng tham số để Spring resolve.

## 14. Checklist vận dụng

- [ ] Xác định mỗi đầu vào là **value** hay **bean reference**.
- [ ] Đặt rõ tên bean và tên property, không nhầm bean name với dữ liệu.
- [ ] Dùng Environment/PropertySource cho externalized values phù hợp.
- [ ] Dùng `getRequiredProperty` cho thông số bắt buộc và validate domain.
- [ ] Dùng `@Bean` method parameters cho collaborator managed.
- [ ] Dùng `addPropertyValue` cho literal, `addPropertyReference` cho object bean.
- [ ] Đảm bảo setter/constructor nhận đúng kiểu và bean được đăng ký.
- [ ] Đăng ký programmatic definitions trước context refresh.
- [ ] Kiểm tra `RuntimeBeanReference` trong metadata và object identity sau wiring.
- [ ] Xử lý thiếu bean/nhiều bean bằng đăng ký và qualifier/primary đúng ý định.
- [ ] Đánh giá scope singleton/prototype/lazy và ảnh hưởng identity.
- [ ] Chạy Java 17+ và so **đúng tám dòng stdout**.
- [ ] Dùng classpath Spring đầy đủ cho `javac`/`java`.
- [ ] Không log secrets, không lấy default che cấu hình thiếu và không bỏ qua lỗi wiring.

**Ghi nhớ:** configuration value trả lời **“đưa dữ liệu gì vào bean?”**, còn bean reference trả lời **“nối bean này với object managed nào?”**. BeanDefinition giữ hai loại thông tin khác nhau; container chuyển đổi value và phân giải reference khi tạo object. Việc nhìn đúng ranh giới **metadata ↔ instance** giúp cấu hình Spring rõ ràng, an toàn và dễ kiểm thử.

### Tài liệu tham khảo chính thức

- [Spring Dependency Injection: Properties and Collaborators](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)
- [Spring Environment Abstraction](https://docs.spring.io/spring-framework/reference/core/beans/environment.html)
- [Spring Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java.html)
- [Spring BeanDefinition API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/config/BeanDefinition.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-definition-007">Phân biệt kế thừa định nghĩa bean với kế thừa lớp Java</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt kế thừa định nghĩa bean với kế thừa lớp Java

## 1. Hai loại “kế thừa” ở hai tầng khác nhau

Trong Spring Core Container, cụm **kế thừa định nghĩa bean** (*bean definition inheritance*) nói đến việc **một bean definition con kế thừa metadata cấu hình từ một bean definition cha**. Đây là cơ chế **tái sử dụng cấu hình của IoC container**: property values, constructor arguments và một số thiết lập khởi tạo có thể được kế thừa rồi bổ sung hoặc ghi đè.

Ngược lại, **kế thừa lớp Java** là quan hệ kiểu được compiler hiểu từ cú pháp `extends` (hoặc quan hệ `implements` với interface). Nó quyết định khả năng tái sử dụng thành viên, subtype và gán tham chiếu theo hệ thống kiểu Java. **Spring không tạo quan hệ `extends` chỉ vì XML có `parent="..."`.**

| Tiêu chí | Kế thừa bean definition | Kế thừa lớp Java |
| --- | --- | --- |
| Thứ được kế thừa | **Metadata** để Spring tạo/cấu hình bean | Thành viên/hành vi và **quan hệ subtype** của kiểu Java |
| Khai báo thường gặp | XML `<bean parent="template">...` hoặc BeanDefinition parent name | `class Child extends Parent` |
| Ai xử lý? | **Spring IoC container** khi merge definitions | **Compiler/JVM** theo ngôn ngữ Java |
| Có cần quan hệ `extends` giữa hai bean classes? | **Không** | Có `extends` cho kế thừa class |
| Có bắt buộc parent definition tạo ra một object? | Không; parent có thể là template `abstract="true"` | Java superclass không nhất thiết được khởi tạo riêng, nhưng constructor superclass tham gia quá trình dựng subclass |
| Có làm bean con chia sẻ object với bean cha? | **Không**; kế thừa cấu hình khác với inject/share instance | Hai instances vẫn có danh tính độc lập, dù thuộc lớp trong cùng cây kiểu |
| Tên “parent” có ý nghĩa gì? | **Bean name** của một definition khác | **Java superclass** được xác định bằng tên kiểu |

**Hai quan hệ độc lập có thể xuất hiện đồng thời, tách rời hoặc không xuất hiện:** Một bean class con của Java có thể được đăng ký bằng bean definition không có `parent`; và hai classes **không liên quan bằng `extends`** có thể cùng kế thừa property metadata từ một parent bean definition.

Mục tiêu bài này: đọc đúng XML `parent`/`abstract`, quan sát cấu hình đã merge, phân biệt việc `getBean` tạo **object** với `getBeanDefinition` trả **metadata**, rồi tự kiểm chứng một Java subclass không kế thừa bean definition.

## 2. Kế thừa bean definition hoạt động như một mẫu cấu hình

Một definition chứa thông tin để container tạo và thiết lập bean. Trong Spring XML, bạn có thể đặt các property dùng chung trong một **template bean definition**:

~~~xml
<bean id="sharedTemplate" abstract="true">
    <property name="endpoint" value="https://api.example.invalid/v1"/>
    <property name="retries" value="3"/>
</bean>
~~~

Đây là **định nghĩa cấu hình**, không phải object Java `SharedTemplate`. Nó cố ý không khai báo `class` và được đánh dấu `abstract="true"`, vì chỉ dùng làm nguồn metadata cho các bean definitions con.

Hai child definitions tham chiếu bằng `parent="sharedTemplate"`:

~~~xml
<bean id="emailSettings" parent="sharedTemplate"
      class="demo.EmailSettings">
    <property name="sender" value="learn@example.invalid"/>
</bean>

<bean id="smsSettings" parent="sharedTemplate"
      class="demo.SmsSettings">
    <property name="retries" value="5"/>
    <property name="provider" value="ExampleSMS"/>
</bean>
~~~

Spring hiểu:

- `emailSettings` nhận `endpoint` và `retries=3` từ template, thêm `sender`.
- `smsSettings` nhận cùng `endpoint`, nhưng **ghi đè `retries` thành 5**, thêm `provider`.
- Class `EmailSettings` và `SmsSettings` **có thể hoàn toàn không có quan hệ kế thừa Java**, miễn mỗi class hỗ trợ các property được áp dụng cho mình.
- Khi container tạo bean con, nó xử lý **merged bean definition**. Parent không trở thành một dependency/object được tiêm vào con.

Điểm khác với mô hình Java: `parent="sharedTemplate"` trỏ đến **tên bean definition**, không trỏ đến `class` hay Java superclass. Bạn có thể giữ nguyên Java classes mà đổi parent/template metadata, và ngược lại.

### Vì sao phải `abstract="true"`?

Một parent definition dùng **chỉ làm mẫu** cần được đánh dấu trừu tượng. Khi parent **không có class**, `abstract="true"` là bắt buộc để nó không bị container xem như bean cần tạo. Ngay cả khi template có `class`, vẫn nên đặt `abstract="true"` nếu bạn **không định cho container tạo instance của parent**.

Với template abstract:
- Container **giữ metadata** để children tham chiếu;
- `containsBeanDefinition("sharedTemplate")` có thể trả `true`;
- **không** thể lấy template như một bean instance bằng `getBean("sharedTemplate")`;
- template không được pre-instantiate như singleton thông thường;
- `abstract="true"` **không tự tạo** một Java `abstract class`.

Trái lại, `abstract class Base` trong Java là ràng buộc của ngôn ngữ; object phải thuộc một concrete subclass. Hai loại `abstract` nằm ở **hai tầng nghĩa khác nhau**.

## 3. Java class inheritance: không cần Spring, không cần parent bean definition

Xem Java thuần:

~~~java
class EmailSettings {
    private int retries;

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }
}

class UrgentEmailSettings extends EmailSettings {
    private String priority;

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }
}
~~~

`UrgentEmailSettings extends EmailSettings` nghĩa là một `UrgentEmailSettings` **cũng là** một `EmailSettings` theo hệ thống kiểu. Nó kế thừa các member có thể kế thừa, như `getRetries()`/`setRetries()`. Nếu có một object `UrgentEmailSettings`, biểu thức `urgent instanceof EmailSettings` đúng.

Không có XML `parent` nào trong ví dụ Java trên. Spring **không cần** bean definition inheritance để tạo instance của một Java subclass:

~~~xml
<bean id="urgentEmail" class="demo.UrgentEmailSettings">
    <property name="retries" value="2"/>
    <property name="priority" value="HIGH"/>
</bean>
~~~

Nó là một bean definition **độc lập**, dùng trực tiếp class là subclass Java. Property `retries` được thiết lập nhờ method Java kế thừa. Khi đọc raw metadata, `getParentName()` của `urgentEmail` sẽ là `null`.

**Hai phản ví dụ giúp tránh nhầm lẫn:**

- `emailSettings` và `smsSettings` cùng parent **metadata** nhưng `EmailSettings` không extends `SmsSettings` và ngược lại.
- `urgentEmail` dùng Java class `UrgentEmailSettings extends EmailSettings`, nhưng definition của nó **không** có `parent` metadata.

Quan hệ kế thừa Java vẫn tuân theo access control, constructor, override/overload và luật đa hình của Java. Không vì Spring sử dụng reflection hoặc injection mà các luật ấy thay đổi.

## 4. Cái gì được merge, cái gì được override?

Theo Spring Framework, child definition có thể kế thừa **các giá trị constructor arguments, bean properties, method overrides** từ parent và ghi đè/bổ sung chúng. Một số container settings như scope, init method, destroy method hoặc static factory method, khi khai báo rõ ở child, **thay thế** giá trị tương ứng của parent.

| Thuộc tính/metadata | Hành vi cơ bản khi parent/child definitions | Cần thận trọng |
| --- | --- | --- |
| Bean class | Nếu child không chỉ định class, có thể dùng class từ parent; child có thể chỉ định class khác | Class thực phải chấp nhận các property/constructor args đã merge |
| Property values | Kế thừa tên/property từ parent; child có thể ghi đè cùng property và thêm property mới | Nếu setter/type không tương thích, bean creation có thể lỗi |
| Constructor arguments | Có thể được kế thừa, thêm hoặc override theo cơ chế merge | Xác định index/type/name rõ khi nhiều overload |
| Method overrides | Có thể được merge từ parent | Phụ thuộc kiểu và method thực tế |
| Scope | Có thể được kế thừa hoặc ghi đè theo cấu hình | `prototype` không có nghĩa subclass Java |
| Initialization/destroy method | Các settings được child khai báo có thể override parent | Chỉ có ý nghĩa khi method tồn tại trên class thực |
| Static factory method setting | Child có thể thay thế setting của parent | Cần đúng factory/class và chữ ký |
| Một số flags khác | Không phải **mọi cờ** của parent đều tự thừa hưởng: lazy-init, autowire mode, depends-on và các thiết lập khác có quy tắc riêng | Đọc documentation/API theo phiên bản, đừng suy diễn “copy nguyên toàn bộ” |

**Điểm mấu chốt:** Container **không thực thi constructor của một “parent bean object” rồi biến nó thành child object**. Nó giải quyết metadata để biết cách tạo một object cụ thể theo bean definition con. Với parent chỉ là template abstract, **không tồn tại parent instance** để bị “kế thừa”.

**Lưu ý scope và lifecycle:** Một child definition vẫn là một registration có tên và scope riêng. Nếu child dùng singleton, container thường cấp một instance theo **child bean definition trong một context**. Nếu hai child definitions cùng thừa hưởng parent template, điều đó **không** buộc chúng chia sẻ object.

### Raw bean definition và merged bean definition

Spring có hai góc nhìn:

- `getBeanDefinition("smsSettings")`: xem **metadata được đăng ký cục bộ** cho bean con, thường có `parentName="sharedTemplate"` và property `retries=5`, nhưng **không có** property `endpoint` ở chính declaration của child.
- `getMergedBeanDefinition("smsSettings")`: xem **metadata sau khi Spring kết hợp** parent+child. Lúc này property `endpoint` từ template **có mặt** và `retries` phản ánh giá trị override của child.

Các API này phục vụ **debug container configuration**, không phải cách thường dùng trong business service. Business code nên nhận dependency qua constructor/`@Bean` parameters và làm việc với object thực.

## 5. Một dự án Java 17 + Maven quan sát cả hai loại inheritance

Bài chạy trên **Spring Framework 7.0.9**, dùng JDK **17+**, Maven **3.9+**. Chúng ta cố ý dùng **Spring XML** vì nó thể hiện `parent` và `abstract` trực quan. Spring XML vẫn là lựa chọn cấu hình hợp lệ trong Framework; nó **không** bắt buộc dùng Spring Boot và không có HTTP server ở đây.

Cấu trúc dự án:

~~~text
bean-inheritance-demo/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── demo/
        │       ├── BeanInheritanceDemo.java
        │       ├── EmailSettings.java
        │       ├── SmsSettings.java
        │       └── UrgentEmailSettings.java
        └── resources/
            └── beans.xml
~~~

Đây là **một project hoàn chỉnh**: mọi source Java phải nằm dưới `src/main/java` đúng package, còn XML dưới `src/main/resources` để Maven đưa `beans.xml` lên runtime classpath.

### 5.1. `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>bean-inheritance-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>7.0.9</version>
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
                    <mainClass>demo.BeanInheritanceDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` mang theo các Spring artifacts nền để chạy `ClassPathXmlApplicationContext` và bean factory. Maven Compiler Plugin dùng `javac` với `--release 17`, Exec Plugin chạy class `main` với đầy đủ dependency classpath. Không cần `spring-boot-starter` hoặc `@SpringBootApplication`.

### 5.2. `src/main/resources/beans.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
                           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- Chỉ chứa metadata dùng chung; không được tạo bean instance. -->
    <bean id="sharedTemplate" abstract="true">
        <property name="endpoint" value="https://api.example.invalid/v1"/>
        <property name="retries" value="3"/>
    </bean>

    <!-- Không cần extends nhau trong Java để kế thừa metadata. -->
    <bean id="emailSettings" parent="sharedTemplate"
          class="demo.EmailSettings">
        <property name="sender" value="learn@example.invalid"/>
    </bean>

    <bean id="smsSettings" parent="sharedTemplate"
          class="demo.SmsSettings">
        <property name="retries" value="5"/>
        <property name="provider" value="ExampleSMS"/>
    </bean>

    <!-- Có Java extends, nhưng KHÔNG có parent bean definition. -->
    <bean id="urgentEmail" class="demo.UrgentEmailSettings">
        <property name="endpoint" value="https://urgent.example.invalid/v1"/>
        <property name="retries" value="2"/>
        <property name="sender" value="urgent@example.invalid"/>
        <property name="priority" value="HIGH"/>
    </bean>
</beans>
~~~

**Cách đọc:**
- Template `sharedTemplate` không có `class`, vì **child cung cấp class cụ thể**. Nó phải `abstract="true"`.
- Hai bean definitions `emailSettings` và `smsSettings` đều nói `parent="sharedTemplate"`: chúng chia sẻ **metadata**, không phải class Java hoặc object.
- `smsSettings` thay `retries=3` thành `5` trong metadata con.
- `urgentEmail` dùng Java subclass nhưng cố ý không có `parent="..."`. Mọi property trong example được khai báo **trực tiếp** cho bean này.

**Chú ý URL:** Domain `.invalid` là địa chỉ minh họa, không có HTTP request nào được thực hiện. Property `endpoint` chỉ lưu chuỗi ký tự để kiểm tra việc kế thừa/override.

### 5.3. `src/main/java/demo/EmailSettings.java`

~~~java
package demo;

public class EmailSettings {
    private String endpoint;
    private int retries;
    private String sender;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }
}
~~~

### 5.4. `src/main/java/demo/SmsSettings.java`

~~~java
package demo;

public class SmsSettings {
    private String endpoint;
    private int retries;
    private String provider;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
~~~

`SmsSettings` **không extends** `EmailSettings`; hai class chỉ cùng có một số setter/property tương thích với mẫu metadata. Đó chính là bằng chứng bean definition inheritance không tạo một cây kiểu Java.

### 5.5. `src/main/java/demo/UrgentEmailSettings.java`

~~~java
package demo;

public class UrgentEmailSettings extends EmailSettings {
    private String priority;

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }
}
~~~

Class này kế thừa các setter `setEndpoint`, `setRetries`, `setSender` từ `EmailSettings` bằng **Java `extends`**, và có thêm property `priority`. Nó không cần `parent` ở XML để các setter Java ấy hoạt động.

### 5.6. `src/main/java/demo/BeanInheritanceDemo.java`

~~~java
package demo;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public final class BeanInheritanceDemo {
    private BeanInheritanceDemo() {
    }

    public static void main(String[] args) {
        try (var context = new ClassPathXmlApplicationContext("beans.xml")) {
            var factory = context.getBeanFactory();

            BeanDefinition template =
                    factory.getBeanDefinition("sharedTemplate");
            BeanDefinition emailDefinition =
                    factory.getBeanDefinition("emailSettings");
            BeanDefinition smsDefinition =
                    factory.getBeanDefinition("smsSettings");
            BeanDefinition smsMerged =
                    factory.getMergedBeanDefinition("smsSettings");
            BeanDefinition urgentDefinition =
                    factory.getBeanDefinition("urgentEmail");

            EmailSettings email =
                    context.getBean("emailSettings", EmailSettings.class);
            SmsSettings sms =
                    context.getBean("smsSettings", SmsSettings.class);
            UrgentEmailSettings urgent =
                    context.getBean("urgentEmail", UrgentEmailSettings.class);

            System.out.println("template.registered="
                    + context.containsBeanDefinition("sharedTemplate"));
            System.out.println("template.abstract=" + template.isAbstract());
            System.out.println("email.parent=" + emailDefinition.getParentName());
            System.out.println("sms.parent=" + smsDefinition.getParentName());
            System.out.println("sms.raw.hasEndpoint="
                    + (smsDefinition.getPropertyValues()
                            .getPropertyValue("endpoint") != null));
            System.out.println("sms.merged.hasEndpoint="
                    + (smsMerged.getPropertyValues()
                            .getPropertyValue("endpoint") != null));

            System.out.println("email.endpoint=" + email.getEndpoint());
            System.out.println("email.retries=" + email.getRetries());
            System.out.println("email.sender=" + email.getSender());
            System.out.println("sms.endpoint=" + sms.getEndpoint());
            System.out.println("sms.retries=" + sms.getRetries());
            System.out.println("sms.provider=" + sms.getProvider());

            System.out.println("emailSms.javaInheritance="
                    + EmailSettings.class.isAssignableFrom(SmsSettings.class));
            System.out.println("urgent.javaExtendsEmail="
                    + (urgent instanceof EmailSettings));
            System.out.println("urgent.definitionParent="
                    + (urgentDefinition.getParentName() != null));
            System.out.println("urgent.priority=" + urgent.getPriority());
        }
    }
}
~~~

API liên quan:
- `getBeanDefinition("smsSettings")` lấy **raw definition** có `parentName`, nơi `endpoint` không được khai báo tại child.
- `getMergedBeanDefinition("smsSettings")` cung cấp **merged definition**, có property `endpoint` từ parent template.
- `getBean(...)` lấy **Java object thực** đã được khởi tạo/cấu hình.
- `EmailSettings.class.isAssignableFrom(SmsSettings.class)` kiểm tra subtype **trong Java**; kết quả khác hoàn toàn với quan hệ parent metadata.
- `urgent instanceof EmailSettings` đúng nhờ **Java extends**, trong khi raw bean definition của `urgentEmail` không có parent.

Đây là ví dụ cố ý quan sát API bean factory để giảng dạy. Khi viết code nghiệp vụ thông thường, tránh rải việc đọc `BeanDefinition` hoặc `getBean()` khắp services; ưu tiên dependency injection và cấu hình rõ ràng.

## 6. Chạy Maven và đối chiếu stdout

Chạy trong thư mục `bean-inheritance-demo` chứa `pom.xml`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

Các lệnh `java -version`/`mvn -version` hiển thị thông tin phụ thuộc máy; **không có stdout chuẩn cố định**. Lệnh Maven cuối biên dịch Java bằng `javac` qua Compiler Plugin và chạy main class với thư viện Spring trên classpath.

**16 dòng stdout do chương trình in** (không tính thông báo Maven/logging backend):

~~~text
template.registered=true
template.abstract=true
email.parent=sharedTemplate
sms.parent=sharedTemplate
sms.raw.hasEndpoint=false
sms.merged.hasEndpoint=true
email.endpoint=https://api.example.invalid/v1
email.retries=3
email.sender=learn@example.invalid
sms.endpoint=https://api.example.invalid/v1
sms.retries=5
sms.provider=ExampleSMS
emailSms.javaInheritance=false
urgent.javaExtendsEmail=true
urgent.definitionParent=false
urgent.priority=HIGH
~~~

### Đọc bằng chứng theo nhóm

1. `template.registered=true`, `template.abstract=true`: template tồn tại như metadata; không phải bean object để lấy bằng `getBean("sharedTemplate")`.
2. `email.parent` và `sms.parent`: cả hai definitions chỉ tới **cùng một parent name**.
3. `sms.raw.hasEndpoint=false` nhưng `sms.merged.hasEndpoint=true`: property `endpoint` được **merge từ template**, dù XML child không khai báo lại.
4. Email nhận `retries=3`, SMS nhận `retries=5`: **cùng kế thừa metadata nhưng override độc lập**.
5. `emailSms.javaInheritance=false`: `EmailSettings` và `SmsSettings` không có quan hệ class inheritance mặc dù definitions có cùng parent.
6. `urgent.javaExtendsEmail=true` cùng `urgent.definitionParent=false`: Java inheritance tồn tại **mà không** cần bean definition inheritance.

Lưu ý: `emailSettings` và `smsSettings` có thể được pre-instantiate khi context refresh nếu là singleton không lazy. Đây là **hai registrations riêng**, không có “instance cha” bị chia sẻ. `getBeanDefinition` và `getMergedBeanDefinition` là thao tác trên metadata, không phải truy vấn Java superclass.

## 7. Java `extends` và XML `parent`: các trường hợp thường gặp

| Tình huống | Quan hệ Java `extends`? | Bean definition `parent`? | Kết quả |
| --- | --- | --- | --- |
| `EmailSettings` và `SmsSettings` trong demo | Không | Cùng trỏ đến `sharedTemplate` | Hai classes độc lập, kế thừa chung values cấu hình |
| `UrgentEmailSettings extends EmailSettings`, bean `urgentEmail` | Có | Không | Java subtype; bean definition độc lập |
| Child bean class cùng Java class với template có class | Không cần quan hệ subtype riêng | Có | Có thể tái sử dụng metadata khi class tương thích |
| Child bean class là Java subclass của class template | Có | Có | Có cả hai quan hệ, nhưng là **hai cơ chế khác nhau** |
| Một class có `extends`, không đăng ký Spring bean nào | Có | Không | Java inheritance hoạt động, **không có Spring bean** |
| Một definition child override `class` không chấp nhận parent properties | Không yêu cầu extends | Có, nhưng metadata không phù hợp | Bean creation có thể thất bại vì setter/constructor mismatch |

**Không viết:** “Child bean class bắt buộc extends parent bean class.” Điều kiện đúng là **class dùng để tạo bean con phải tương thích với metadata đã merge**. Nếu template yêu cầu `setEndpoint(String)`, child class cần cách tiếp nhận property `endpoint` phù hợp. Nó có thể làm vậy bằng setter riêng, bằng setter Java kế thừa hoặc cách cấu hình khác được container hỗ trợ.

### Parent bean definition không phải parent `ApplicationContext`

Spring còn có khái niệm **parent application context** trong hệ phân cấp contexts. Đó là **mối quan hệ giữa hai containers**, khác hoàn toàn `parent="sharedTemplate"` trong XML. Đừng thấy từ `parent` rồi suy ra hai cơ chế có cùng cách tra bean hay quản lý scope.

### Bean definition inheritance không đồng nghĩa Java property inheritance

Khi child definition kế thừa một property `retries=3`, nó nhận **giá trị cấu hình** mà container sẽ áp dụng cho object con. Java class của nó **không tự có thêm trường `retries`**: class vẫn phải khai báo/hỗ trợ property ấy. Nếu `SmsSettings` không có `setRetries` hoặc cơ chế binding tương ứng, không thể kỳ vọng XML tạo thêm field vào bytecode.

## 8. Thêm một ví dụ không cần XML để hiểu ranh giới

Kế thừa lớp Java tự hoạt động trong chương trình hoàn toàn không dùng Spring:

~~~java
// File Java độc lập: PureJavaInheritance.java
public class PureJavaInheritance {
    static class BasePolicy {
        int retries() {
            return 3;
        }
    }

    static class UrgentPolicy extends BasePolicy {
        @Override
        int retries() {
            return 5;
        }
    }

    public static void main(String[] args) {
        BasePolicy ordinary = new BasePolicy();
        BasePolicy urgent = new UrgentPolicy();

        System.out.println("ordinary=" + ordinary.retries());
        System.out.println("urgent=" + urgent.retries());
        System.out.println("isSubclass=" + (urgent instanceof BasePolicy));
    }
}
~~~

**Lệnh chạy Java 17+ không cần Maven hay Spring** (đứng tại thư mục chứa file):

~~~bash
javac PureJavaInheritance.java
java PureJavaInheritance
~~~

**Stdout chính xác:**

~~~text
ordinary=3
urgent=5
isSubclass=true
~~~

Ở đây Java `extends` và **dynamic method dispatch** giải thích toàn bộ kết quả. Không có `ApplicationContext`, `BeanDefinition`, `parent="..."`, XML hay container. Nếu muốn sử dụng `UrgentPolicy` như một Spring bean, bạn **còn phải đăng ký nó** bằng một cơ chế phù hợp.

**Liên hệ hai ví dụ:** Java inheritance chủ yếu phục vụ quan hệ kiểu/hành vi; Spring bean definition inheritance chủ yếu phục vụ tái sử dụng metadata khi tạo một hoặc nhiều bean. Tránh dùng một cơ chế như thể nó tự thực hiện cả hai.

## 9. Những lỗi thường gặp khi cấu hình kế thừa bean definition

| Triệu chứng hoặc suy luận sai | Nguyên nhân/cách hiểu đúng |
| --- | --- |
| `getBean("sharedTemplate")` báo lỗi | Template đã `abstract="true"`, không phải bean để instantiate |
| Quên `abstract="true"` trên template không có class | Context có thể cố xử lý một bean thiếu class/factory, dẫn đến lỗi khi bootstrap |
| Child chỉ định `parent="sharedTemplate"` nhưng tên không tồn tại | Parent bean definition chưa được đăng ký hoặc tên tham chiếu sai |
| Child đổi class nhưng thiếu setter của property kế thừa | Metadata sau merge không tương thích với Java class được tạo |
| Nghĩ `parent="..."` tạo Java `extends` | XML không sửa type hierarchy hay bytecode |
| Nghĩ Java `extends` tự truyền bean properties từ một Spring definition khác | Java kế thừa member, còn Spring metadata cần khai báo/merge riêng |
| Nghĩ hai children cùng parent nên chia sẻ instance | Chúng có bean definitions/scopes riêng |
| Đọc raw `getBeanDefinition` thấy thiếu property rồi kết luận config sai | Kiểm tra **merged definition** hoặc instance; child có thể nhận property từ parent |
| Cho rằng mọi flag như lazy-init đều kế thừa y nguyên | Mỗi nhóm metadata có quy tắc merge/override riêng |
| Dùng `abstract="true"` mà đòi gọi `getBean` trên template | Template abstract được thiết kế để kế thừa, không dùng như một instance |
| Nhầm parent definition với parent context | Một cái là metadata relationship; cái kia là hierarchy giữa IoC containers |
| Lạm dụng inheritance XML nhiều tầng | Cấu hình khó đọc/khó dự đoán override, nên cân nhắc cách biểu đạt đơn giản hơn |

**Khi debug:** kiểm tra tên parent, cách đăng ký XML, `getParentName()`, dữ liệu raw/merged, class thực và setter tương thích, scope/lazy và toàn bộ exception cause. Tránh sửa nhiều thứ cùng lúc hoặc tự bật bean-definition overriding để che lỗi.

### Khi nào nên dùng, khi nào không?

Bean definition inheritance hữu ích khi nhiều beans chia sẻ phần cấu hình **thực sự giống nhau**, đặc biệt ở hệ thống XML có metadata lặp lại. Nhưng nó tạo quan hệ cấu hình ngầm: thay đổi template có thể ảnh hưởng tất cả children, nên cần tài liệu và test.

Với dự án Java configuration hiện đại, có thể tái sử dụng cấu hình bằng factory methods, tham số hóa và component composition thay vì cố ép một mô hình XML inheritance vào mọi chỗ. Các phương thức `@Bean` trong Java configuration **không tự** có `parent` metadata chỉ vì Java config class sử dụng `extends`. Chọn cơ chế rõ ràng nhất cho nhóm và yêu cầu hệ thống.

## 10. Bài tập dự đoán trước khi chạy

Thực hiện từng thay đổi trên bản sao sạch, khôi phục XML/source gốc giữa các bài để so stdout:

1. Đổi `sharedTemplate.retries` từ `3` thành `8`. Dự đoán `email.retries=8` nhưng `sms.retries=5` vẫn giữ override.
2. Thêm property `enabled=true` vào template nhưng **không** thêm setter tương ứng trong `EmailSettings` và `SmsSettings`. Giải thích vì sao bean creation có thể fail thay vì Java classes tự sinh thêm field.
3. Bỏ `parent="sharedTemplate"` của `emailSettings`. Dự đoán `email.endpoint` và `email.retries` nếu chúng không được khai báo riêng (Java defaults).
4. Bỏ `abstract="true"` khỏi template không có class. Quan sát lỗi cấu hình khi context cố xử lý template.
5. Đổi `SmsSettings` thành Java subclass của `EmailSettings` nhưng giữ nguyên XML. Nêu rõ phần nào trong kết quả là thay đổi của Java subtype, phần nào vẫn là metadata merge.
6. Cho `urgentEmail` thêm `parent="sharedTemplate"` và bỏ các property trùng. Dự đoán cả hai cơ chế kế thừa hoạt động đồng thời, nhưng parent XML vẫn không phải Java superclass.
7. Đổi `smsSettings` thành scope `prototype`, gọi `getBean` hai lần; phân biệt **số instance** và metadata template dùng chung.
8. Dùng `getMergedBeanDefinition("smsSettings")` quan sát giá trị `retries` sau override so với raw definition.
9. Tạo một child khác dùng `parent="sharedTemplate"` nhưng class không có `setEndpoint`. Dự đoán exception khi apply inherited property.
10. Đổi `UrgentEmailSettings extends EmailSettings` thành một class không extends nhưng vẫn khai báo setters giống nhau; dự đoán kết quả `instanceof EmailSettings` thay đổi, còn parent metadata của definition (không có) vẫn giữ nguyên.

### Checklist đạt bài

- [ ] Nói được **bean definition inheritance = kế thừa metadata cấu hình**, Java inheritance = kế thừa thành viên/quan hệ subtype.
- [ ] Giải thích được XML `parent`, `abstract="true"`, property override, raw và merged definitions.
- [ ] Chỉ ra vì sao hai classes không `extends` nhau vẫn cùng kế thừa bean template.
- [ ] Chỉ ra một Java subclass có thể làm Spring bean **không có** parent bean definition.
- [ ] Biết parent abstract template **không** được lấy làm bean instance.
- [ ] Biết Spring không tự thêm Java setter/field khi kế thừa property metadata.
- [ ] Phân biệt `getBeanDefinition`, `getMergedBeanDefinition` và `getBean`.
- [ ] Chạy được cả Maven demo Spring (16 dòng stdout) và Java thuần (3 dòng stdout) theo Java 17+.

## 11. Tóm tắt và tài liệu tra cứu

**Một câu phân biệt:** Bean definition inheritance tái sử dụng **“công thức cấu hình”**; Java class inheritance tái sử dụng **“kiểu và hành vi chương trình”**. Chúng có thể kết hợp nhưng **không thay thế nhau**.

- XML `parent="sharedTemplate"` kế thừa property values và các metadata phù hợp để tạo **bean con**.
- `abstract="true"` trên template dành cho definition **không được instantiate riêng**; nó không có nghĩa bean class là Java `abstract class`.
- `class UrgentEmailSettings extends EmailSettings` là quan hệ **Java subtype**, không bắt buộc có parent bean definition.
- Child có thể override giá trị cấu hình mà **không** override một phương thức Java nào; ngược lại, Java override method không tự đổi XML property.
- Dùng `getMergedBeanDefinition` khi cần quan sát metadata sau hợp nhất, thay vì nhầm raw child metadata là cấu hình cuối cùng.

Tài liệu chính thức:
- [Spring Framework – Bean Definition Inheritance](https://docs.spring.io/spring-framework/reference/core/beans/child-bean-definitions.html)
- [Spring Framework – Bean Overview](https://docs.spring.io/spring-framework/reference/core/beans/definition.html)
- [Spring Framework – XML-based Metadata](https://docs.spring.io/spring-framework/reference/core/beans/basics.html)
- [Spring – Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)
- [Spring – Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java.html)
- [Java Language Specification – Classes](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html)
- [Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)

**Kết luận:** Khi thấy một `parent` trong cấu hình Spring, hãy hỏi đó là **parent bean definition** hay **Java superclass**. Chỉ khi tách đúng hai mối quan hệ, bạn mới dự đoán chính xác cách Spring merge cấu hình, tạo bean, áp dụng setters và phân biệt metadata với object thực.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-definition-008">Định nghĩa trừu tượng và hợp nhất cấu hình kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

# Định nghĩa trừu tượng và hợp nhất cấu hình kế thừa trong Spring Core Container

## 1. Vấn đề: nhiều bean có chung cấu hình nhưng vẫn phải khác nhau

Trong cấu hình Spring XML, đôi khi nhiều bean có các thông số nền giống nhau: cách khởi tạo, các giá trị property, một số argument hoặc thiết lập vòng đời. Nếu sao chép toàn bộ mỗi `<bean>`, khi sửa một thông số chung rất dễ quên cập nhật một bản.

Spring Core Container hỗ trợ **kế thừa bean definition** (*bean definition inheritance*). Một bean definition con khai báo `parent="tênBeanCha"` để dùng lại metadata của định nghĩa cha, sau đó có thể bổ sung hoặc ghi đè những phần riêng. Một bean definition cha dùng thuần túy làm **mẫu cấu hình** thường được đánh dấu `abstract="true"`.

Điểm cần nhớ ngay từ đầu:

- **Parent bean definition** là **mẫu metadata** dùng để tạo ra các bean definition hoàn chỉnh.
- **Child bean definition** kế thừa và hợp nhất metadata từ cha khi container xử lý cấu hình.
- **Bean instance** của child là một object được tạo theo metadata cuối cùng đã hợp nhất.
- `abstract="true"` trên XML `<bean>` **không** đồng nghĩa class Java của bean phải được khai báo `abstract class`.
- `parent` trong XML **không** có nghĩa `extends` trong Java, và cũng **không** phải thuộc tính `parent ApplicationContext`.

Ví dụ ý tưởng:

~~~xml
<bean id="baseConfig" abstract="true"
      class="demo.AlertConfiguration">
    <property name="prefix" value="BASE"/>
</bean>

<bean id="emailConfig" parent="baseConfig">
    <property name="channel" value="email"/>
</bean>

<bean id="smsConfig" parent="baseConfig">
    <property name="channel" value="sms"/>
</bean>
~~~

Đây là **XML minh họa**, tên class trong đoạn này không thuộc chương trình chạy ở mục 6. Khi bean definition được hợp nhất, cả hai child kế thừa `prefix=BASE` nhưng có `channel` khác nhau. Cha đóng vai trò template, không phải một object nghiệp vụ được inject.

### Mô hình xử lý

~~~text
        parent bean definition
         id=baseTemplate
         abstract=true
         prefix=BASE
         tags=[audit, core]
                  |
          +-------+---------+
          |                 |
          v                 v
   emailConfig child   smsConfig child
   channel=email       channel=sms
   tags merge=true     tags replace
          |                 |
          v                 v
  merged definition   merged definition
          |                 |
          v                 v
  emailConfig object  smsConfig object
~~~

Spring hợp nhất **định nghĩa trước**, rồi sử dụng định nghĩa hiệu lực để tạo instance con. Mỗi child có thể có scope và lifecycle của riêng nó; **không có một instance cha bắt buộc phải được tạo trước**.

## 2. Phân biệt ba loại “kế thừa” hay bị nhầm

| Khái niệm | Quan hệ | Điều được kế thừa/chia sẻ |
| --- | --- | --- |
| **Java class inheritance** | `class Child extends Parent` | Trường/method và quy tắc Java OOP |
| **Spring bean definition inheritance** | `<bean parent="baseTemplate">` | Cấu hình container: constructor args, property values, scope và các metadata thích hợp |
| **ApplicationContext hierarchy** | context con có parent context | Khả năng tìm bean trong context cha và quan hệ container theo cấu hình |

Không được dùng chúng như ba cách viết cùng một việc.

Ví dụ class `EmailConfig` có thể hoàn toàn **không extends** class `BaseConfig` nhưng bean definition của nó vẫn có `parent="baseTemplate"`, miễn class cuối cùng có thể chấp nhận các property/argument được kế thừa. Ngược lại, `class EmailConfig extends BaseConfig` **không tự tạo** quan hệ XML `parent`.

Kế thừa metadata còn có thể xảy ra khi cả cha và con đều dùng **cùng class Java**, tạo ra hai bean với cấu hình khác nhau. Đây chính là cách bài thực hành dùng: hai child đều tạo class `AlertConfig`, không cần viết các Java subclass chỉ để đổi property.

**Không nhầm với DI:** `parent="baseTemplate"` không truyền bean cha vào constructor của bean con. Nếu một service cần collaborator thật, hãy dùng `<constructor-arg ref="..."/>`, `<property ref="..."/>` hoặc constructor injection tương ứng.

## 3. Khai báo parent bean definition trừu tượng

### 3.1. Ý nghĩa của `abstract="true"`

~~~xml
<bean id="baseTemplate"
      class="demo.AlertConfig"
      abstract="true">
    <property name="prefix" value="BASE"/>
</bean>
~~~

Ở đây `baseTemplate` là một **bean definition trừu tượng**, dùng làm template. Container giữ metadata của nó, nhưng không cho phép tra cứu đối tượng cha như một bean có thể khởi tạo:

~~~java
context.getBean("baseTemplate"); // Không hợp lệ đối với abstract bean definition.
~~~

Dòng trên là minh họa; nó có thể ném `BeanIsAbstractException`. Đây không phải một `null` hay một instance được tạo rỗng để dùng tạm. Khi Spring pre-instantiate singleton trong quá trình `ApplicationContext.refresh()`, **abstract bean definitions không được tạo như singleton thông thường**.

Nếu chỉ muốn template mà **không** đặt `abstract="true"`, container có thể cố gắng khởi tạo bean cha như một bean thông thường, đặc biệt khi đó là singleton non-lazy. Nếu template thiếu các property/argument thiết yếu, khởi tạo sẽ thất bại; nếu có thể khởi tạo thì lại có thêm một instance không mong muốn.

### 3.2. Parent có thể không có class

~~~xml
<bean id="commonDefaults" abstract="true">
    <property name="prefix" value="BASE"/>
    <property name="channel" value="general"/>
</bean>

<bean id="childConfig"
      parent="commonDefaults"
      class="demo.AlertConfig"/>
~~~

Đoạn XML minh họa một template chỉ mang metadata, **không chỉ định `class`**. Trong trường hợp đó **bắt buộc khai báo parent là abstract**, bởi parent tự thân không đủ thông tin để được tạo. Child chỉ định class phù hợp để cung cấp cách dựng instance.

Nếu parent đã có class, child có thể **kế thừa class ấy** mà không khai báo lại `class`. Child cũng có thể chỉ định class khác, nhưng class mới phải hỗ trợ những property/constructor arguments mà metadata cuối cùng yêu cầu.

**Abstract bean definition khác abstract Java class.** Một Java `abstract class` không thể được khởi tạo trực tiếp; XML `abstract="true"` quyết định một **bean definition** chỉ được dùng làm template, kể cả khi bean class Java là concrete.

### 3.3. Có thể dùng cha không abstract không?

Về mặt cơ chế, một bean definition bình thường cũng có thể là parent. Tuy nhiên nếu mục tiêu của nó **chỉ** là mẫu chung, nên đánh dấu abstract để tránh container tạo một singleton parent không cần thiết. Khi class cha có side effect khởi tạo, việc quên đánh dấu abstract có thể gây kết nối, đọc file hoặc lỗi khởi động không mong muốn.

Không tự ý chuyển một bean nghiệp vụ thực sự đang được dùng thành abstract: mọi chỗ `getBean("parent")` hoặc `ref` đến bean ấy sẽ không còn hoạt động.

## 4. Spring hợp nhất những phần metadata nào?

Một child declaration được container **hợp nhất** với định nghĩa cha. Không nên hình dung đơn giản là copy XML nguyên văn.

### 4.1. Những phần child thường kế thừa hoặc ghi đè

Các phần quan trọng có thể được kế thừa từ parent, sau đó child có thể bổ sung/ghi đè:

- **Bean class**: nếu child không chỉ định, có thể lấy class từ parent.
- **Constructor argument values**: child có thể kế thừa những giá trị định nghĩa ở cha, hoặc định nghĩa thêm/ghi đè phù hợp với chỉ số, tên và cách Spring giải quyết argument.
- **Property values**: property chưa khai báo ở child được giữ từ parent; nếu child định nghĩa lại cùng property, giá trị child thường có ưu tiên.
- **Method override metadata** ở bean definition và một số thiết lập phù hợp khác.
- **Scope**: child có thể kế thừa scope nếu không chỉ định; nếu chỉ định `scope` khác thì child dùng scope mới.
- **Lifecycle/factory settings**: phương thức khởi tạo/hủy hoặc factory method có thể được khai báo ở child để ghi đè metadata tương ứng.

Spring có các quy tắc riêng cho metadata luôn lấy từ child definition, ví dụ cấu hình `depends-on`, autowire mode, lazy initialization và các thiết lập khác tùy loại bean definition. **Không tự suy diễn rằng mọi thuộc tính XML đều được inherit/merge bằng một quy tắc giống nhau.** Đặc biệt, hãy kiểm tra lại khi trộn `lazy-init` hoặc `depends-on` với parent-child.

### 4.2. Cấu hình property đơn giản: kế thừa và ghi đè

~~~xml
<bean id="parentTemplate"
      abstract="true"
      class="demo.AlertConfig">
    <property name="prefix" value="BASE"/>
    <property name="channel" value="general"/>
</bean>

<bean id="emailConfig" parent="parentTemplate">
    <property name="channel" value="email"/>
</bean>
~~~

Bean `emailConfig` sau merge có `prefix=BASE` từ cha nhưng `channel=email` từ con. Child không tạo một object cha rồi sửa trực tiếp object đó; container xây dựng **metadata cuối** cho bean con.

### 4.3. Constructor arguments cần nhất quán

Với XML như:

~~~xml
<bean id="baseClient" abstract="true"
      class="demo.Client">
    <constructor-arg index="0" value="localhost"/>
</bean>

<bean id="remoteClient" parent="baseClient">
    <constructor-arg index="1" value="443"/>
</bean>
~~~

Đây là **ví dụ minh họa**, giả định class `demo.Client` có constructor tương ứng. Child kế thừa argument index 0, thêm argument index 1, nên constructor phù hợp phải chấp nhận cả hai. Khi parent/child khai báo argument theo type/name/index khác nhau, cần kiểm tra chính xác cách argument được hợp nhất và lựa chọn overload; không nên trông chờ Spring tự hiểu ý nghiệp vụ nếu constructor mơ hồ.

### 4.4. Bảng cách hiểu metadata

| Thuộc tính | Cách diễn giải an toàn |
| --- | --- |
| `class` | Child có thể dùng class cha hoặc thay class phù hợp |
| `constructor-arg` | Kế thừa/bổ sung/ghi đè argument theo thông tin định danh phù hợp |
| `property` | Giá trị property cha còn hiệu lực nếu child không ghi đè |
| `scope` | Có thể kế thừa scope hoặc đặt scope khác ở child |
| `init-method` / `destroy-method` | Có thể ghi đè cấu hình lifecycle phù hợp |
| `abstract` | Parent dùng làm template không được instantiable; child concrete cần hoàn chỉnh |
| `depends-on`, `lazy-init`, `autowire` | Không mặc định coi là kế thừa như property; tuân quy tắc metadata bean definition của Spring |
| `<list>`/`<set>`/`<map>`/`<props>` | Nếu cùng property được khai báo lại, muốn merge collection cần cấu hình `merge="true"` **ở child** |

## 5. Collection merging: phần khác biệt cần học riêng

### 5.1. Mặc định: thay thế, không tự ghép

Giả sử parent định nghĩa:

~~~xml
<property name="tags">
    <list>
        <value>audit</value>
        <value>core</value>
    </list>
</property>
~~~

Child cũng khai báo property `tags`:

~~~xml
<property name="tags">
    <list>
        <value>sms</value>
    </list>
</property>
~~~

Nếu không bật merge ở child, định nghĩa list mới **ghi đè** list cũ: child nhận `[sms]`, không phải `[audit, core, sms]`.

**Lưu ý:** nếu child **không khai báo lại** property `tags`, nó đơn giản **kế thừa nguyên property từ cha**; đó không phải hành vi merge hai collection.

### 5.2. Bật `merge="true"` ở collection của child

~~~xml
<property name="tags">
    <list merge="true">
        <value>mail</value>
    </list>
</property>
~~~

Khi parent có `[audit, core]` và child có `[mail]` với `merge="true"`, Spring tạo kết quả `[audit, core, mail]`. **Thứ tự List được giữ**, parent elements xuất hiện trước child elements.

Với `<set>`, `<map>` và `<props>`, Spring cũng hỗ trợ merge cùng loại:

- **List**: nối các phần tử parent trước, child sau; vị trí có ý nghĩa.
- **Set**: hợp nhất phần tử theo semantics Set, loại trùng theo equality; không nên dựa vào thứ tự không được hứa.
- **Map**: hợp nhất các entry; khi cùng key, giá trị child ghi đè giá trị parent.
- **Properties (`<props>`)**: như map key–value string; key ở child ghi đè key trùng từ parent.

Ví dụ:

~~~xml
<property name="routes">
    <props merge="true">
        <prop key="support">support-new@demo.test</prop>
        <prop key="sales">sales@demo.test</prop>
    </props>
</property>
~~~

Nếu parent có `support=support-old@demo.test` và `ops=ops@demo.test`, child sẽ có ba key: `ops` kế thừa, `support` ghi đè, `sales` được thêm.

### 5.3. Các giới hạn quan trọng

1. `merge="true"` phải đặt trên **phần tử collection ở định nghĩa con**. Đặt ở parent không có ý nghĩa “tự merge mọi child” mong muốn.
2. Hai collection cần thuộc **kiểu tương thích** để merge; không thể trộn `<map>` với `<list>` rồi kỳ vọng Spring chuyển đổi tùy ý.
3. Merge không áp dụng chung cho mọi property scalar. Với `<value>email</value>`, child đơn giản ghi đè giá trị scalar cha.
4. Đừng gọi đây là “deep merge tổng quát mọi object”. Spring áp dụng merge theo cơ chế managed collection metadata của bean definition.
5. Không trông chờ thứ tự duyệt `Properties`, `Map` hoặc `Set` giống một List. Nếu stdout cần xác định, **lấy từng key cụ thể**, không in nguyên map.
6. Merge theo parent-child bean definitions có tác dụng tại lúc container dựng metadata/instance, không phải thuật toán đồng bộ danh sách runtime giữa hai object.

Ví dụ runnable sau minh họa **cả replacement của List lẫn merging List/Properties** trong cùng một context.

## 6. Ví dụ Java 17 + Spring Framework 6.2: parent abstract, child và merge thực tế

Ví dụ chạy bằng **XML bean definitions**, vì `parent`, `abstract` và `merge` được thể hiện trực tiếp bằng cú pháp XML của Spring. Trong dự án Java configuration hiện đại, `@Configuration`/`@Bean` thường biểu đạt composition hoặc dùng chung factory khác; không nên giả định có một annotation `@Bean(parent="...")` tương đương.

### 6.1. Cấu trúc thư mục

Yêu cầu JDK 17+, Maven và quyền tải dependency Maven lần đầu:

~~~text
bean-inheritance-demo/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── demo/
        │       └── TemplateInheritanceDemo.java
        └── resources/
            └── beans.xml
~~~

Tệp `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>bean-inheritance-demo</artifactId>
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
                    <mainClass>demo.TemplateInheritanceDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 6.2. File Java đầy đủ

Lưu `src/main/java/demo/TemplateInheritanceDemo.java`:

~~~java
package demo;

import java.util.List;
import java.util.Properties;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public final class TemplateInheritanceDemo {
    private TemplateInheritanceDemo() {}

    public static final class AlertConfig {
        private String prefix;
        private String channel;
        private List<String> tags = List.of();
        private Properties routes = new Properties();

        // Constructor công khai cho Spring property injection qua setter.
        public AlertConfig() {}

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public void setRoutes(Properties routes) {
            this.routes = routes;
        }

        public String summary() {
            return channel + "|" + prefix + "|" + tags;
        }

        public String route(String key) {
            return routes.getProperty(key);
        }
    }

    public static void main(String[] args) {
        try (ClassPathXmlApplicationContext context =
                     new ClassPathXmlApplicationContext("beans.xml")) {

            boolean parentAbstract = context.getBeanFactory()
                    .getBeanDefinition("baseTemplate").isAbstract();
            System.out.println("PARENT_ABSTRACT " + parentAbstract);

            AlertConfig email =
                    context.getBean("emailConfig", AlertConfig.class);
            AlertConfig sms =
                    context.getBean("smsConfig", AlertConfig.class);

            System.out.println("EMAIL " + email.summary());
            System.out.println("EMAIL_ROUTES "
                    + email.route("ops") + "|"
                    + email.route("support") + "|"
                    + email.route("sales"));

            System.out.println("SMS " + sms.summary());
            System.out.println("SMS_ROUTES "
                    + sms.route("ops") + "|"
                    + sms.route("support"));

            System.out.println("DIFFERENT " + (email != sms));
        }
    }
}
~~~

`AlertConfig` có các setter public tương ứng các property khai báo trong XML. Class này **không kế thừa class Java nào được định nghĩa trong ví dụ**; chỉ chính bean definitions mới có quan hệ parent–child.

### 6.3. File XML hoàn chỉnh

Lưu `src/main/resources/beans.xml`:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- Chỉ là template: không tạo baseTemplate instance -->
    <bean id="baseTemplate"
          class="demo.TemplateInheritanceDemo$AlertConfig"
          abstract="true">

        <property name="prefix" value="BASE"/>
        <property name="channel" value="general"/>

        <property name="tags">
            <list>
                <value>audit</value>
                <value>core</value>
            </list>
        </property>

        <property name="routes">
            <props>
                <prop key="ops">ops@demo.test</prop>
                <prop key="support">support-old@demo.test</prop>
            </props>
        </property>
    </bean>

    <!-- Kế thừa class, prefix; override channel;
         merge list tags và Properties routes. -->
    <bean id="emailConfig" parent="baseTemplate">
        <property name="channel" value="email"/>

        <property name="tags">
            <list merge="true">
                <value>mail</value>
            </list>
        </property>

        <property name="routes">
            <props merge="true">
                <prop key="support">support-new@demo.test</prop>
                <prop key="sales">sales@demo.test</prop>
            </props>
        </property>
    </bean>

    <!-- Kế thừa class và routes; override scalar;
         list mới KHÔNG merge nên thay list của cha. -->
    <bean id="smsConfig" parent="baseTemplate">
        <property name="channel" value="sms"/>
        <property name="prefix" value="SHORT"/>
        <property name="tags">
            <list>
                <value>sms</value>
            </list>
        </property>
    </bean>
</beans>
~~~

### 6.4. Lệnh biên dịch, chạy

Mở PowerShell/CMD hoặc terminal Linux/macOS tại thư mục `bean-inheritance-demo`:

~~~bash
mvn -version
mvn -q compile exec:java
~~~

**Stdout do chương trình in theo đúng thứ tự:**

~~~text
PARENT_ABSTRACT true
EMAIL email|BASE|[audit, core, mail]
EMAIL_ROUTES ops@demo.test|support-new@demo.test|sales@demo.test
SMS sms|SHORT|[sms]
SMS_ROUTES ops@demo.test|support-old@demo.test
DIFFERENT true
~~~

Maven và thư viện có thể phát sinh log riêng, đặc biệt khi tải dependency; khối trên chỉ là **stdout từ các lệnh `System.out.println` do chương trình tạo**, không khẳng định toàn bộ terminal luôn chỉ có sáu dòng.

### 6.5. Từng dòng stdout chứng minh điều gì?

1. `PARENT_ABSTRACT true`: metadata của `baseTemplate` có cờ abstract; `getBeanFactory().getBeanDefinition(...)` chỉ kiểm tra metadata, **không tạo instance parent**.
2. `EMAIL email|BASE|[audit, core, mail]`: `emailConfig` ghi đè channel từ `general` thành `email`, kế thừa prefix `BASE` và merge list parent trước child.
3. `EMAIL_ROUTES ...`: key `ops` được kế thừa, key `support` lấy giá trị mới từ child, key `sales` được bổ sung. Code truy cập đúng key nên không phụ thuộc thứ tự duyệt `Properties`.
4. `SMS sms|SHORT|[sms]`: `smsConfig` ghi đè prefix và channel, khai báo list mới **không `merge="true"`**, do đó list cha bị thay.
5. `SMS_ROUTES ...`: child không khai báo property routes, nên kế thừa trực tiếp các giá trị routes của parent, trong đó `support` vẫn là giá trị cũ.
6. `DIFFERENT true`: hai bean con là **hai instance khác nhau**. Cùng sử dụng class `AlertConfig` và một parent template không khiến chúng trở thành cùng một bean.

Đầu ra được thiết kế **không phụ thuộc thứ tự tạo singleton**: không có `println` trong constructor/setter, và cả hai child đều được tra cứu rõ bằng bean name.

### 6.6. Thử biến thể để tự quan sát

- Xóa `merge="true"` ở list của `emailConfig`: list email dự kiến thành `[mail]`.
- Đặt `merge="true"` ở `<props>` của parent nhưng bỏ ở child: không đạt hiệu ứng merge mong muốn; child properties mới thay định nghĩa cũ.
- Bỏ `abstract="true"` ở parent: container có thể tạo thêm bean `baseTemplate` vì class có thể khởi tạo; bạn đã đánh mất vai trò template-only.
- Thử `context.getBean("baseTemplate")` **trong khối try/catch ở bản thực hành**: sẽ thất bại vì đó là abstract definition. Không thêm lệnh này vào ví dụ chính nếu vẫn muốn giữ stdout đúng như đã công bố.
- Đổi `class` của child sang một class khác thiếu `setTags`: metadata vẫn kế thừa `tags` nhưng quá trình set property có thể thất bại. Spring không tự biến class mới thành compatible với cấu hình cũ.

## 7. Định nghĩa chương trình bằng API BeanDefinition

Cấu hình XML là hình thức trực quan. Ở mức API, Spring dùng `BeanDefinition` và các kiểu cài đặt như `ChildBeanDefinition` để biểu diễn quan hệ parent–child trong những trường hợp thích hợp.

Ví dụ minh họa tạo metadata:

~~~java
import org.springframework.beans.factory.support.ChildBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

RootBeanDefinition parent =
        new RootBeanDefinition(TemplateInheritanceDemo.AlertConfig.class);
parent.setAbstract(true);
parent.getPropertyValues().add("prefix", "BASE");

ChildBeanDefinition child = new ChildBeanDefinition("baseTemplate");
child.getPropertyValues().add("channel", "email");
~~~

Đoạn này **không độc lập**: muốn chạy cần thêm bean factory/registry để đăng ký `parent` dưới tên `baseTemplate`, đăng ký `child` dưới tên riêng, rồi lấy bean. Nó được đưa ra để giúp nhận ra rằng XML `parent` cuối cùng cũng ánh xạ thành **metadata container**, không phải cây instance objects.

Lưu ý `ChildBeanDefinition` không nên được coi là yêu cầu bắt buộc của mọi cấu hình Java annotation. Khi dùng `@Bean`, developer thường ưu tiên factor shared setup vào factory/helper rõ ràng, thay vì tái tạo nguyên vẹn XML bean-definition inheritance bằng nhiều tầng Java class.

## 8. Dùng khi nào và tránh lạm dụng

**Phù hợp khi:**

- Bảo trì hoặc thiết kế các cấu hình Spring XML có nhiều child bean cùng nhóm thông số chuẩn.
- Một số bean có chung cấu hình rất lớn nhưng chỉ khác vài property hoặc constructor arguments.
- Muốn áp dụng một policy config chung cho nhiều bean implementation tương thích.
- Cần merge danh sách hoặc properties mà các child chỉ bổ sung một phần theo định nghĩa rõ.

**Cân nhắc cách khác khi:**

- Các child khác nhau đến mức template chứa hàng loạt property không áp dụng cho một số class.
- Có nhiều cấp `parent` lồng nhau khiến nguồn gốc của giá trị cuối khó lần vết.
- Cần thay đổi hành vi theo dữ liệu nghiệp vụ runtime — bean definition inheritance là cấu hình startup, không phải strategy selection của từng request.
- Đang dùng Java configuration thuần: có thể khai báo `@Bean` factory chung, helper construction hoặc một class config dùng chung có contract rõ, tránh cố ép cú pháp `parent="..."` của XML vào Java.
- Muốn tái sử dụng *code Java*: dùng composition/kế thừa class khi thật sự hợp lý, đừng dùng bean definition inheritance để giải quyết tất cả bài toán OOP.

Một template tốt nên chứa **thiết lập ổn định có ý nghĩa chung**. Nếu bất kỳ child nào cũng phải ghi đè gần như toàn bộ, template ít còn giá trị và làm giảm tính minh bạch.

## 9. Lỗi phổ biến và cách khắc phục

| Triệu chứng / hiểu nhầm | Giải thích và cách xử lý |
| --- | --- |
| `getBean("baseTemplate")` lỗi | Parent `abstract="true"` chỉ là metadata template, không được instantiable. |
| Quên `abstract="true"` | Parent có class có thể bị tạo thành singleton không mong muốn khi refresh. |
| Parent không có class nhưng không abstract | Definition không đủ cách tạo, cần đánh dấu abstract và child phải hoàn chỉnh. |
| Nghĩ `parent` là Java `extends` | Đó là kế thừa cấu hình, không thay class hierarchy. |
| Nghĩ child giữ tham chiếu tới một parent object | Container hợp nhất metadata; không bắt buộc tạo object cha. |
| Child đổi class rồi lỗi property | Class mới không hỗ trợ setter/constructor được kế thừa. |
| Child khai báo list nhưng mất phần tử parent | Mặc định collection property mới thay list cũ, cần `merge="true"` ở child. |
| Đặt `merge="true"` trên parent | Merge phải được bật ở collection trong child definition. |
| Merge `<list>` với `<map>` | Không cùng loại collection, Spring không thể merge theo ý tưởng đó. |
| Key Properties trùng nhưng không lấy giá trị con | Kiểm tra `merge="true"` ở child và key cụ thể; child có thể đang replace. |
| Lỗi constructor sau merge | Kiểm tra argument indexes/names/type và overload thực tế của bean class. |
| Nghĩ tất cả metadata được kế thừa | `lazy-init`, `depends-on` và những thuộc tính khác có quy tắc riêng; không tự suy diễn. |
| Đổi `abstract` để sửa lỗi thiếu bean | Xác định bean đó là template hay bean nghiệp vụ thật, không xóa ranh giới này tùy tiện. |
| Nhiều tầng parent-child khó debug | Giảm độ sâu, dùng bean names rõ, kiểm tra merged bean definition và viết test cấu hình. |
| Quên khai báo beans.xml ở resources | `ClassPathXmlApplicationContext("beans.xml")` không tìm thấy resource, kiểm tra đường dẫn và classpath. |
| In nguyên Properties rồi lệch stdout | Properties không đảm bảo thứ tự in; lấy theo key khi cần output xác định. |

### Đọc lỗi trong Spring

- `BeanIsAbstractException`: cố lấy một bean definition trừu tượng.
- `BeanDefinitionStoreException` hoặc lỗi bean definition: XML, parent reference hay cấu hình không hợp lệ.
- `BeanCreationException`: tạo bean thất bại, có thể do constructor hoặc setter nhận cấu hình sau merge không hợp lệ.
- `NotWritablePropertyException`: bean class không có setter/property tương ứng với metadata đã khai báo.
- `NoSuchBeanDefinitionException`: tham chiếu sai bean name hoặc lấy bean không được đăng ký.

Hãy đọc **nguyên nhân gốc** trong exception chain và tên bean con đang tạo. Không thêm một `new` bên ngoài container chỉ để né lỗi cấu hình.

## 10. Bài tập tự học (có đáp án)

**Bài 1.** Vì sao `abstract="true"` không đồng nghĩa class Java `abstract`?

**Đáp án:** Cờ abstract áp dụng cho **bean definition của Spring**: parent chỉ làm template, không được lookup như instance. Class Java có thể là concrete, như `AlertConfig` trong chương trình chính.

**Bài 2.** Bean con không có thuộc tính `class` trong XML, vậy nó lấy class nào?

**Đáp án:** Nếu parent cung cấp bean class thì child có thể kế thừa class đó; nếu parent không có class, child cần cung cấp cách tạo phù hợp.

**Bài 3.** Child viết lại `<list>` cho cùng property tags, kết quả có tự nối hai danh sách không?

**Đáp án:** Không. Mặc định child thay property collection cha. Muốn nối dùng `merge="true"` ở list child.

**Bài 4.** List parent `[a,b]` và list child `[c]` với `merge="true"`. Kết quả là gì?

**Đáp án:** `[a,b,c]`, thứ tự phần tử parent được giữ trước child.

**Bài 5.** Parent `support=old`, child `support=new` và `sales=sales` trong `<props merge="true">`. Kết quả ra sao?

**Đáp án:** Key `support` lấy `new`, key `sales` được thêm; những key parent không ghi đè vẫn được giữ.

**Bài 6.** Có thể dùng `<bean parent="baseTemplate">` để tự động gọi phương thức cha Java bằng `super` không?

**Đáp án:** Không. XML parent-child chỉ là metadata; `super` thuộc Java class inheritance và không liên quan trực tiếp.

**Bài 7.** Trong ví dụ, `emailConfig` và `smsConfig` có dùng chung một instance không?

**Đáp án:** Không. Chúng là hai bean definitions độc lập, cùng kế thừa một template metadata và cùng bean class nhưng tạo hai object khác nhau.

**Bài 8.** Nếu hai `ApplicationContext` riêng tải cùng `beans.xml`, các bean singleton có phải cùng instance không?

**Đáp án:** Không theo mặc định. Singleton scope thuộc từng container; cùng XML không có nghĩa chia sẻ object giữa hai context.

**Bài 9.** Nên chuyển toàn bộ ứng dụng Java-config mới sang parent-child XML vì “ít dòng hơn” không?

**Đáp án:** Không nhất thiết. Cần cân nhắc tính minh bạch, khả năng test và cách cấu hình đang dùng. Với Java config, composition/factory methods thường dễ đọc hơn nhiều tầng metadata kế thừa.

## 11. Checklist kiểm tra cấu hình

- [ ] Parent được dùng làm template thì khai báo `abstract="true"`.
- [ ] Khi parent không có class, child phải bổ sung class hoặc cách tạo phù hợp.
- [ ] Phân biệt inheritance của bean definition với Java extends và hierarchy của context.
- [ ] Kiểm tra bean class cuối cùng có các setter/constructor ứng với metadata được kế thừa.
- [ ] Ghi đè scalar property chỉ ở child thật sự cần khác biệt.
- [ ] Với collection, quyết định rõ muốn **replace** hay **merge**.
- [ ] Đặt `merge="true"` trên list/set/map/props của child, không chỉ parent.
- [ ] Không merge những collection type không tương thích.
- [ ] Không dựa vào thứ tự duyệt Map/Properties cho các phép kiểm tra stdout.
- [ ] Không tự suy rằng scope/lazy/depends-on đều inherit giống scalar property.
- [ ] Không lookup trực tiếp một abstract parent bean.
- [ ] Không vô tình đăng ký trùng bean name/class hoặc nhiều cấp parent khó kiểm soát.
- [ ] Đảm bảo tệp XML có trên classpath và cấu hình hợp lệ.
- [ ] Viết integration test cho child merged definitions và các property quan trọng.
- [ ] Tránh sử dụng kế thừa metadata để giấu quy tắc nghiệp vụ runtime.

### Tài liệu Spring chính thức

- [Bean Definition Inheritance](https://docs.spring.io/spring-framework/reference/core/beans/child-bean-definitions.html)
- [Collection Merging](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-properties-detailed.html)
- [Bean Overview](https://docs.spring.io/spring-framework/reference/core/beans/definition.html)

**Kết luận:** `abstract="true"` biến bean definition cha thành **mẫu cấu hình không khởi tạo**. Child `parent="..."` kế thừa và ghi đè các metadata phù hợp; với collection, `merge="true"` đặt ở child giúp hợp nhất theo đúng semantics của List, Set, Map hoặc Properties. Đây là kỹ thuật **tái sử dụng cấu hình Spring**, không phải kế thừa code Java hay việc inject một parent object.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-definition-009">Xung đột tên và chính sách ghi đè định nghĩa</a>

<details>
<summary>Xem chi tiết</summary>

# Xung đột tên và chính sách ghi đè định nghĩa — Spring Framework Core Container

## 1. Mục tiêu: cùng tên bean là một xung đột về định danh

Trong Spring IoC container, mỗi **bean definition** được đăng ký với một **bean name**. Nếu hai nguồn cấu hình cố đăng ký **hai định nghĩa khác nhau dưới cùng một tên** trong cùng registry, xảy ra **xung đột tên bean definition**. Spring có thể cho phép đăng ký sau thay thế định nghĩa cũ, hoặc từ chối thao tác ghi đè, tùy **chính sách của container**.

Điểm then chốt:

- **Xung đột tên:** nhiều bean definitions cố dùng cùng **tên**, dù class có thể khác hoặc giống nhau.
- **Nhiều bean cùng kiểu:** hai bean **khác tên** cùng kiểu Java là hợp lệ, nhưng một injection point theo kiểu có thể gặp lỗi **không chọn được một candidate**. Đây là vấn đề *autowiring ambiguity*, không phải lỗi trùng tên.
- **Bean alias:** tên phụ trỏ tới **một bean**; không tạo thêm một bean definition độc lập. Alias có thể liên quan tới kiểm tra xung đột tên, nhưng không được nhầm với ghi đè hai definitions.
- **Ghi đè definition:** nếu được cho phép, definition mới thay definition cũ dưới **cùng registry key**. Không phải phép “hợp nhất hai bean” và cũng không tạo hai instance cạnh tranh cùng tên.

Kết thúc bài, bạn có thể:

1. Phân biệt `BeanDefinitionOverrideException` với `NoUniqueBeanDefinitionException`.
2. Hiểu `DefaultListableBeanFactory.setAllowBeanDefinitionOverriding(boolean)` và cách điều khiển chính sách qua `AnnotationConfigApplicationContext`.
3. Giải thích sự khác nhau giữa **mặc định của Spring Framework** và **mặc định của Spring Boot**.
4. Chạy chương trình Java 17+ kiểm chứng hai nhánh **REJECTED** khi cấm và **SECOND** khi cho phép ghi đè, với output xác định.
5. Hiểu ảnh hưởng của registration order, alias, bean name, `@Bean`/component scan, bean scope và thời điểm refresh.
6. Biết cách chọn chính sách và gỡ lỗi trong dự án thật; không bật ghi đè tùy tiện chỉ để ứng dụng khởi động.

**Phạm vi:** tập trung vào bean name và bean definition overriding trong Spring Core Container. Không cần Spring Boot để chạy ví dụ chính.

## 2. Bean name, bean definition, instance và alias

Một bean definition cung cấp **metadata** để Spring tạo/quản lý object: class hoặc factory method, scope, lazy, dependency, init/destroy metadata và các thuộc tính khác. Registry ánh xạ **tên** sang definition.

~~~text
BeanDefinitionRegistry
  "notificationClient" -> BeanDefinition #A -> new ChannelClient("FIRST")
  "metricsClient"      -> BeanDefinition #B -> new ChannelClient("METRICS")
  
Bean aliases (nếu đăng ký)
  "preferredClient" -> "notificationClient"
~~~

Trong ví dụ trên, `notificationClient` và `metricsClient` là **hai bean names khác nhau**, dù cùng class `ChannelClient`. `preferredClient` chỉ là **alias**, không có definition thứ ba.

Nếu tiếp tục đăng ký:

~~~text
registerBeanDefinition("notificationClient", BeanDefinition #C)
~~~

thì registry phải xử lý việc `notificationClient` **đã tồn tại**. Tùy chính sách, nó từ chối definition #C hoặc thay ánh xạ cũ bằng definition #C.

### Bảng phân biệt bốn trường hợp

| Tình huống | Có phải xung đột tên definition? | Kết quả chủ yếu |
| --- | --- | --- |
| Hai bean definitions cùng bean name trong cùng registry | Có | Từ chối hoặc thay definition cũ theo chính sách |
| Hai bean khác tên, cùng class Java | Không | Hai bean hợp lệ; có thể gây ambiguity khi inject chỉ bằng kiểu |
| Một bean name với một hoặc nhiều alias | Không tự là hai definitions | Các tên cùng trỏ một bean |
| Hai bean definitions cùng tên nhưng ở hai contexts độc lập | Không nhất thiết | Mỗi context có registry riêng; cần xét quan hệ parent-child nếu có |

Lỗi `NoUniqueBeanDefinitionException` nói về việc container không giải quyết được **một ứng viên từ nhiều bean** khi tra cứu/inject theo type. Ngược lại, `BeanDefinitionOverrideException` xuất hiện ở giai đoạn **đăng ký definition trùng bean name bị cấm**. Hướng sửa khác nhau, đừng đổi qualifier để chữa nhầm một lỗi registry name collision.

## 3. Spring Framework và Spring Boot có mặc định khác nhau

Với `DefaultListableBeanFactory` của **Spring Framework 6.2**, mặc định **cho phép bean definition overriding**; khi đăng ký definition khác cùng tên, definition đến sau có thể thay definition trước.

Spring cung cấp API:

~~~java
context.setAllowBeanDefinitionOverriding(false);
context.setAllowBeanDefinitionOverriding(true);
~~~

Đây là **các lựa chọn thay thế**, không gọi nối tiếp hai dòng trong cùng chương trình để “kết hợp” chính sách. `AnnotationConfigApplicationContext` kế thừa API này từ `GenericApplicationContext`; cũng có thể cấu hình trực tiếp underlying `DefaultListableBeanFactory`.

**Spring Boot mặc định không cho phép ghi đè bean definition** trong cấu hình thông thường. Boot có property:

~~~properties
spring.main.allow-bean-definition-overriding=false
~~~

Đây là **ví dụ property của Spring Boot**, không có tác dụng khi chỉ dùng `new AnnotationConfigApplicationContext()` của Spring Framework thuần và không có Boot làm host. Nếu thật sự muốn thay đổi chính sách trong Boot, có thể cấu hình:

~~~properties
spring.main.allow-bean-definition-overriding=true
~~~

Nhưng **không nên dùng cờ true như cách sửa mọi lỗi trùng tên**: nó có thể giấu lỗi cấu hình, khiến bean dự kiến bị thay bằng implementation không mong muốn.

| Ngữ cảnh | Mặc định điển hình | Cách thể hiện rõ |
| --- | --- | --- |
| Spring Framework `DefaultListableBeanFactory` | Cho phép ghi đè | `setAllowBeanDefinitionOverriding(false/true)` |
| Spring `GenericApplicationContext` / `AnnotationConfigApplicationContext` thuần | Theo BeanFactory, mặc định cho phép | `context.setAllowBeanDefinitionOverriding(false/true)` |
| Spring Boot | Mặc định cấm | Property `spring.main.allow-bean-definition-overriding` hoặc API Boot tương ứng |

**Khuyến nghị cho demo và test:** luôn **đặt chính sách tường minh** ngay khi tạo context, trước lúc đăng ký các definitions có nguy cơ trùng tên. Điều này giúp kết quả không phụ thuộc giả định về framework host và phiên bản.

## 4. Chính sách cấm ghi đè: fail fast ngay lúc registration

Giả sử registry đang có:

~~~java
context.registerBeanDefinition(
        "notificationClient",
        createDefinition("FIRST"));
~~~

Khi chính sách là `false`, việc tiếp tục đăng ký:

~~~java
context.registerBeanDefinition(
        "notificationClient",
        createDefinition("SECOND"));
~~~

sẽ **bị từ chối** vì trùng bean name. Ở trường hợp gọi `registerBeanDefinition` programmatically, `BeanDefinitionOverrideException` được ném ngay từ thao tác đăng ký thứ hai, **trước khi gọi `refresh()`**. Định nghĩa cũ vẫn tồn tại, chưa bị thay bởi definition mới thất bại.

Lợi ích của việc cấm:

- Phát hiện lỗi tên trùng giữa các module/configuration từ sớm.
- Hạn chế nguy cơ một thành phần bị thay thế âm thầm khi refactor, cập nhật thư viện hoặc mở rộng component scanning.
- Làm hợp đồng registration dễ dự đoán: tên đã dùng không thể bị chiếm bởi một definition khác.
- Phù hợp với môi trường cần cấu hình nghiêm ngặt và kiểm thử startup rõ ràng.

Điều này **không** ngăn có nhiều bean cùng class với **tên khác nhau**, và cũng không giải quyết ambiguity của dependency theo type. Một Spring registry có thể cấm overriding nhưng vẫn chứa hai beans `MailSender` khác tên.

## 5. Chính sách cho phép ghi đè: definition đến sau thay definition cũ

Khi đặt `setAllowBeanDefinitionOverriding(true)`:

~~~java
context.registerBeanDefinition(
        "notificationClient",
        createDefinition("FIRST"));

context.registerBeanDefinition(
        "notificationClient",
        createDefinition("SECOND"));
~~~

trong trường hợp đăng ký tuần tự trước `refresh()` ở ví dụ, definition thứ hai thay thế definition thứ nhất **dưới cùng một tên**. Khi context khởi tạo bean `notificationClient`, nó sử dụng definition còn hiệu lực và tạo object có source `SECOND`.

Cần phân biệt rõ:

- Đây là **thay definition**, không phải tạo hai bean singleton cùng tên.
- Definition cũ **không còn** được tra cứu bằng bean name đó. Vì ví dụ đăng ký hai lần **trước refresh**, không có chuyện một singleton cũ đã tồn tại rồi mới bị thay đổi trong chương trình này.
- **Không sửa metadata/ghi đè bean definitions tùy tiện sau khi context đã refresh và instance đã được tạo**. Cache, dependency graph và lifecycle có thể đã tồn tại; hành vi đổi cấu hình runtime không tương đương một bootstrap mới.
- Trong cấu hình có `@Configuration`, `@Bean`, component scan, import và các post-processors, Spring có nhiều bước xử lý nguồn cấu hình. **Không dựa vào thứ tự tình cờ giữa nhiều nguồn** để chọn “ai thắng”. Điều kiện registration và các cơ chế phát hiện trùng tên có thể ảnh hưởng kết quả cụ thể. Demo dùng API đăng ký tuần tự để kết quả xác định.

### Vì sao không nên dùng ghi đè như cơ chế chọn implementation thông thường?

Nếu cần chọn một trong hai implementation theo môi trường, thường nên thiết kế rõ bằng **bean name riêng, qualifier, primary, profile, condition hoặc factory method có chủ đích**, tùy ngữ cảnh; đừng cố tình đăng ký trùng tên và hy vọng source load order nhất quán. Ghi đè có thể hữu ích cho test override hoặc infrastructure có kiểm soát, nhưng phải nêu rõ nơi đăng ký và bằng chứng được kiểm tra.

## 6. Chương trình Java 17+ chạy độc lập

Ví dụ dùng **JDK 17+**, Maven và **Spring Framework 6.2.19** (module `spring-context`). Đây là phiên bản được ghim để tái lập, không phải lời khẳng định bản Spring mới nhất.

Demo tạo **hai context độc lập**:

- Context thứ nhất có policy **false**: registration thứ hai bị từ chối; bean hiện hành vẫn là FIRST.
- Context thứ hai có policy **true**: registration thứ hai thay định nghĩa; bean hiện hành là SECOND.
- Context thứ hai còn đăng ký một alias để chứng minh alias **không phải bean definition mới**.

Không cần Spring Boot, database, network server hoặc Spring MVC.

~~~text
bean-override-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── BeanOverridePolicyDemo.java
~~~

### 6.1. File pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>bean-override-demo</artifactId>
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
                <version>3.14.1</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.1</version>
                <configuration>
                    <mainClass>demo.BeanOverridePolicyDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Maven tải `spring-context` và các module phụ thuộc theo cấu hình POM. Với `javac`/`java` thủ công, cần đưa các JAR đó vào compile/runtime classpath.

### 6.2. File src/main/java/demo/BeanOverridePolicyDemo.java

~~~java
package demo;

import java.util.Objects;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionOverrideException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class BeanOverridePolicyDemo {
    private BeanOverridePolicyDemo() {}

    public static final class ChannelClient {
        private final String source;

        public ChannelClient(String source) {
            this.source = Objects.requireNonNull(source);
        }

        public String source() {
            return source;
        }
    }

    private static BeanDefinition definition(String source) {
        return BeanDefinitionBuilder
                .genericBeanDefinition(ChannelClient.class)
                .addConstructorArgValue(source)
                .getBeanDefinition();
    }

    private static void demonstrateDenied() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            context.setAllowBeanDefinitionOverriding(false);

            context.registerBeanDefinition(
                    "notificationClient", definition("FIRST"));

            System.out.println("denyPolicy=" +
                    context.getDefaultListableBeanFactory()
                            .isAllowBeanDefinitionOverriding());

            try {
                context.registerBeanDefinition(
                        "notificationClient", definition("SECOND"));
                System.out.println("denyResult=UNEXPECTED");
            } catch (BeanDefinitionOverrideException ex) {
                System.out.println("denyResult=REJECTED");
                System.out.println("denyName=" + ex.getBeanName());
            }

            context.refresh();
            ChannelClient retained =
                    context.getBean("notificationClient", ChannelClient.class);
            System.out.println("denyActiveDefinition=" + retained.source());
        }
    }

    private static void demonstrateAllowed() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            context.setAllowBeanDefinitionOverriding(true);

            context.registerBeanDefinition(
                    "notificationClient", definition("FIRST"));
            context.registerBeanDefinition(
                    "notificationClient", definition("SECOND"));
            context.registerAlias("notificationClient", "preferredClient");
            context.refresh();

            ChannelClient selected =
                    context.getBean("notificationClient", ChannelClient.class);
            ChannelClient byAlias =
                    context.getBean("preferredClient", ChannelClient.class);

            System.out.println("allowPolicy=" +
                    context.getDefaultListableBeanFactory()
                            .isAllowBeanDefinitionOverriding());
            System.out.println("allowResult=" + selected.source());
            System.out.println("beanNamesForType=" +
                    context.getBeanNamesForType(ChannelClient.class).length);
            System.out.println("aliasSameInstance=" +
                    (selected == byAlias));
            System.out.println("aliasIsDefinition=" +
                    context.containsBeanDefinition("preferredClient"));
            System.out.println("sameSingleton=" +
                    (selected == context.getBean(
                            "notificationClient", ChannelClient.class)));
        }
    }

    public static void main(String[] args) {
        demonstrateDenied();
        demonstrateAllowed();
    }
}
~~~

**Giải thích vì sao ví dụ có output đáng tin:**

- `ChannelClient` là nested public static class có constructor công khai nhận một String; definition builder lưu constructor argument `FIRST` hoặc `SECOND`.
- `context.setAllowBeanDefinitionOverriding(...)` được gọi **trước registration**, policy không thay đổi giữa chừng.
- Trong nhánh denied, exception được bắt **đúng loại**; sau lỗi, definition đầu tiên vẫn tồn tại. `refresh()` sau đó tạo một instance từ definition FIRST.
- Trong nhánh allowed, hai registration xảy ra tuần tự **trước refresh**, nên definition SECOND còn hiệu lực khi bean được tạo.
- Alias `preferredClient` trỏ tới `notificationClient`; không tạo definition thứ hai, do vậy tra cứu bằng alias trả cùng object singleton.
- `getBeanNamesForType(ChannelClient.class).length` trong context allowed là 1: **một bean name chính** thuộc kiểu đó; alias không xuất hiện như definition độc lập trong danh sách này.
- Hai context được đóng bằng `try-with-resources`. Output không dựa vào log framework, giá trị random, thời gian hoặc class hash codes.

### 6.3. Chạy bằng Maven và stdout

Tại thư mục `bean-override-demo`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

**Chính xác 10 dòng stdout do code Java in khi chạy thành công:**

~~~text
denyPolicy=false
denyResult=REJECTED
denyName=notificationClient
denyActiveDefinition=FIRST
allowPolicy=true
allowResult=SECOND
beanNamesForType=1
aliasSameInstance=true
aliasIsDefinition=false
sameSingleton=true
~~~

Lệnh `java -version`, `javac -version`, `mvn -version` và log Maven/Spring (nếu có) không thuộc **10 dòng do ứng dụng phát ra**. Chuỗi exception nội bộ có thể chứa nhiều metadata/log khác nhau; demo **không in stack trace** mà chỉ in kết quả đã được bắt và name, nên stdout ổn định.

### 6.4. Chạy bằng javac và java trực tiếp

Để Java compiler tìm thấy Spring API, tạo classpath bằng Maven:

~~~bash
mvn -q dependency:build-classpath -Dmdep.outputFile=classpath.txt
~~~

**Linux/macOS (bash/zsh):**

~~~bash
mkdir -p out
javac --release 17 -encoding UTF-8 \
  -cp "$(cat classpath.txt)" \
  -d out src/main/java/demo/BeanOverridePolicyDemo.java
java -cp "out:$(cat classpath.txt)" demo.BeanOverridePolicyDemo
~~~

**Windows PowerShell:**

~~~powershell
$deps = (Get-Content -Raw classpath.txt).Trim()
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -encoding UTF-8 -cp "$deps" -d out src/main/java/demo/BeanOverridePolicyDemo.java
java -cp "out;$deps" demo.BeanOverridePolicyDemo
~~~

Lưu ý dấu phân tách classpath **`:`** trên Unix và **`;`** trên Windows. Hai cách chạy dùng cùng class `demo.BeanOverridePolicyDemo` nên stdout theo cùng một kịch bản.

## 7. Diễn giải từng kiểm chứng

| Dòng output | Ý nghĩa |
| --- | --- |
| `denyPolicy=false` | Context thứ nhất cấm override bằng cấu hình tường minh |
| `denyResult=REJECTED` | Đăng ký definition thứ hai cùng name bị từ chối |
| `denyName=notificationClient` | Exception chỉ đúng bean name bị xung đột |
| `denyActiveDefinition=FIRST` | Sau registration bị từ chối, definition đầu vẫn được sử dụng |
| `allowPolicy=true` | Context thứ hai bật override tường minh |
| `allowResult=SECOND` | Definition mới thay definition cũ trước refresh |
| `beanNamesForType=1` | Registry còn một bean name chính thuộc kiểu ChannelClient |
| `aliasSameInstance=true` | Alias tra cùng bean object, không phải bean thứ hai |
| `aliasIsDefinition=false` | Alias không tạo thêm BeanDefinition |
| `sameSingleton=true` | Trong cùng context, lấy bean singleton nhiều lần trả cùng reference |

**Điều chưa chứng minh bằng output này:** rằng mọi cách đăng ký xung đột qua annotation/scan/XML có cùng thứ tự thắng hoặc loại exception tại cùng thời điểm; đó là các nguồn metadata và bước xử lý bổ sung, cần kiểm tra riêng.

## 8. Xung đột có thể đến từ đâu trong dự án thực tế?

- **Hai phương thức `@Bean` khác lớp cấu hình** dùng cùng bean name mặc định vì cùng tên method, hoặc dùng `@Bean("notificationClient")` trùng tường minh.
- **Component scanning** phát hiện nhiều component cùng tên (theo cơ chế đặt tên/naming strategy) hoặc trùng với bean đã được khai báo bằng JavaConfig.
- **Thư viện và ứng dụng cùng đăng ký một tên**, ví dụ code của dự án cố dùng tên infrastructure đã được thư viện khác sử dụng.
- **XML bean declarations** hoặc programmatic registration trùng key với metadata từ JavaConfig.
- **Thay đổi tên method/class/package hoặc scope quét** khiến các bean bắt đầu va chạm sau refactor/update dependency.
- **Alias đã dùng** làm tên cho một đăng ký khác, hoặc hai alias definitions cạnh tranh; kiểm tra conflict ở registry/alias mechanism liên quan.
- **Context con và context cha:** không nhất thiết là đăng ký cùng registry. Child context có thể shadow bean name ở parent theo lookup hierarchy; không đồng nghĩa hai definitions trong **cùng bean factory** ghi đè nhau. Cần phân biệt registry scope trước khi kết luận là collision.

### Hành vi của @Bean khi cùng tên

Spring xử lý các method `@Bean` thông qua configuration parsing và bean definition registration. Không nên đơn giản hóa thành “method nào nằm dưới sẽ luôn thắng”. Tùy nguồn định nghĩa, thứ tự xử lý, sự trùng giữa method và component, cùng các quy tắc dành cho metadata, một cấu hình có thể bị từ chối, bị bỏ qua trong giai đoạn đăng ký, hoặc được xử lý theo quy tắc riêng.

**Muốn một kiểm chứng có kết quả chắc chắn**, hãy xem bean definitions đã đăng ký, logging của quá trình bootstrap và test context thực tế; đừng dựa vào phán đoán thứ tự method xuất hiện trong source code.

## 9. Xung đột tên khác xung đột theo type

~~~java
@Bean("firstChannel")
ChannelClient firstChannel() {
    return new ChannelClient("FIRST");
}

@Bean("secondChannel")
ChannelClient secondChannel() {
    return new ChannelClient("SECOND");
}
~~~

Đây là **snippet minh họa**, không phải file hoàn chỉnh. Hai bean có **tên khác nhau**, dù cùng class. Không vi phạm `allowBeanDefinitionOverriding=false` vì registry không phải ghi đè một tên đang có.

Tuy nhiên:

~~~java
ChannelClient client = context.getBean(ChannelClient.class);
~~~

nếu không có candidate nào được ưu tiên theo quy tắc phù hợp, tra theo type đơn trị có thể thất bại với `NoUniqueBeanDefinitionException`. Giải pháp là chọn **đúng ứng viên**, không phải bật bean overriding:

- `@Qualifier("firstChannel")` tại injection point cần bean thứ nhất.
- `@Primary` khi một bean thực sự nên là lựa chọn mặc định.
- Tra bằng bean name tại composition root, ví dụ `context.getBean("firstChannel", ChannelClient.class)`.
- Injection list/map khi nghiệp vụ cần tất cả strategies hoặc channels.

Ngược lại, `BeanDefinitionOverrideException` liên quan đến **cùng bean name** bị đăng ký thêm khi policy cấm. Không có lý do dùng `@Qualifier` để cho phép ghi đè một tên đã bị chiếm.

## 10. Chính sách và thời điểm thiết lập

Đối với context do ứng dụng tự tạo, nên đặt policy **trước register/refresh**:

~~~java
try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext()) {
    context.setAllowBeanDefinitionOverriding(false);
    context.register(AppConfig.class);
    context.refresh();
    // Sử dụng bean khi context còn active.
}
~~~

Đây là **snippet minh họa**; `AppConfig` phải được định nghĩa ở chương trình riêng. Cách tạo bằng constructor không tham số rồi cấu hình có lợi khi cần sửa chính sách **trước bước bootstrap**. Nếu dùng ngay `new AnnotationConfigApplicationContext(AppConfig.class)`, constructor thường refresh context trước khi bạn có cơ hội gọi setter policy trên instance đã tạo; không nên dùng cú pháp đó khi bài toán đòi đặt policy trước registration.

Với Spring Boot, option `spring.main.allow-bean-definition-overriding` thuộc về cấu hình khởi động Boot, **không phải một `@Bean` String property tự động áp dụng cho context Spring thuần**.

### Có thể thay đổi một bean đang chạy bằng cách đăng ký lại không?

Không coi override là cơ chế hot swap bean sau startup. Nếu singleton đã được tạo, nó có thể đang được inject vào nhiều consumer, có cached metadata, post-processors, lifecycle và tài nguyên đang chạy. Việc thay đổi definition sau đó **không bảo đảm tự thay mọi reference cũ**, có thể tạo trạng thái không nhất quán. Nếu cần cấu hình lại dịch vụ đang chạy, hãy chọn cơ chế reconfiguration được hỗ trợ, tạo context mới hoặc thiết kế provider phù hợp.

**Quy tắc an toàn:** các bean definitions phải có cấu hình dự kiến hoàn chỉnh **trước refresh**, policy phải rõ, và startup test cần kiểm tra implementation thực tế được cung cấp.

## 11. Quy trình chẩn đoán tên trùng

1. **Đọc bean name trong exception** (ví dụ `notificationClient`), không chỉ đọc “could not start”.
2. **Xác định hai nguồn đăng ký:** `@Bean` methods, component scan, XML, library auto-registration hay API.
3. **Xác định registry/context:** chúng có thật sự vào cùng BeanFactory, hay một bean ở parent và một ở child?
4. **Kiểm tra policy:** context/framework host đang cho override hay cấm? Đừng đoán dựa vào kinh nghiệm từ dự án dùng Spring Boot nếu hiện tại chạy Framework thuần.
5. **Kiểm tra bean definition metadata:** factory method, bean class, resource/source, role, scope và qualifiers (khi có).
6. **Quyết định đúng nghiệp vụ:** hai bean đáng lẽ độc lập khác tên, hay chỉ cần một bean? Có vô tình scan trùng không?
7. **Đổi tên hoặc hợp nhất cấu hình có chủ đích**; chọn qualifier/primary khi vấn đề thật sự là ambiguity theo type.
8. **Chạy startup smoke test và kiểm tra runtime**: bean name, object type, cấu hình/identity, hành vi; không chỉ dựa vào log thiếu exception.
9. **Kiểm tra module/environment khác:** test/prod có thể thêm cấu hình hoặc scan package làm lộ collision khác.

### Các API hữu ích

~~~java
context.containsBeanDefinition("notificationClient");
context.isBeanNameInUse("notificationClient");
context.getBeanDefinition("notificationClient");
context.getAliases("notificationClient");
context.getBean("notificationClient");
~~~

Đây là các snippet **minh họa trong scope có context phù hợp**, không phải full Java file. Chú ý `isBeanNameInUse` tính cả alias/bean name liên quan trong registry; `containsBeanDefinition` hỏi **definition đăng ký trực tiếp**, còn `getBean` lấy **instance** (có thể tạo bean nếu chưa có).

Dùng API tra cứu definition **không đồng nghĩa** cho phép thay đổi definition đang chạy: metadata inspection là việc khác với mutation.

## 12. Lỗi phổ biến và cách khắc phục

| Vấn đề | Sai lầm thường gặp | Cách khắc phục |
| --- | --- | --- |
| `BeanDefinitionOverrideException` | Cùng bean name được đăng ký hai lần trong policy cấm | Tìm hai nguồn, đổi tên hợp lý hoặc loại đăng ký trùng |
| `NoUniqueBeanDefinitionException` | Hai bean khác tên nhưng injection một type mơ hồ | Qualifier/primary/tái thiết kế selection, không bật overriding |
| “Đã bật true nhưng runtime vẫn sai” | Bean mong muốn bị thay bởi definition khác | Kiểm tra nguồn đăng ký, implementation thực tế, startup tests |
| “Alias cũng là bean thứ hai” | Đếm alias thành definition mới | Dùng `isAlias`, `containsBeanDefinition`, so identity |
| “Bean cuối cùng trong source sẽ thắng” | Dựa vào thứ tự văn bản JavaConfig | Kiểm tra order/registering pipeline, đừng lập hợp đồng ngầm |
| Trùng tên do component scan | Hai component đặt tên giống nhau hoặc scan phạm vi sai | Đổi bean names/naming strategy hoặc giới hạn scan |
| Cấu hình thư viện và ứng dụng đụng tên | Tái dùng một bean name generic | Đặt tên có namespace/rõ module, xem extension points chính thức |
| Parent/child context cùng bean name | Nhầm shadowing với override trong một registry | Xem hierarchy và quy tắc lookup ở context thực tế |
| “Đổi policy sau refresh để sửa lỗi” | Thay chính sách quá muộn | Cấu hình trước register/refresh và khởi động lại |
| Ghi đè sau khi singleton đã tạo | Mong những consumer đã inject tự cập nhật reference | Không dùng override làm hot reload; thiết kế lifecycle/reconfiguration |
| Spring Framework và Boot khác mặc định | Áp dụng giả định của framework khác | Kiểm tra policy của runtime thực, đặt tường minh |
| Bean type trùng nhưng name khác | Coi đó là lỗi registry | Chọn bean theo type/qualifier thích hợp |
| Bean name trùng nhưng class khác | Nghĩ khác class thì không xung đột | Registry key theo tên, nên class khác vẫn collision |

**Nguyên tắc vận hành:** “ứng dụng chạy được” không chứng minh đã chọn đúng bean. Khi override được bật, **đặc biệt cần kiểm tra bean thực tế** vì lỗi vô tình thay definition có thể không làm startup thất bại.

## 13. Khi nào override có thể có chủ đích?

Có những trường hợp hệ thống cần đăng ký một implementation thay cho definition mặc định: môi trường test cung cấp fake, framework integration cố ý cho custom registration hoặc một hệ thống cấu hình cho phép thay thế. Nhưng phải xác định:

- Ai sở hữu bean name đó và ai được phép thay?
- Có hợp đồng/documentation chính thức về override hay không?
- Registration order có được định nghĩa rõ trong tình huống này?
- Bean được thay có lifecycle/resource/configuration nào?
- Test nào chứng minh bean cuối cùng đúng loại và đúng hành vi?
- Chính sách ở các môi trường (dev/test/prod) có giống nhau?

Trong Spring Boot tests, có thể có **các cơ chế override bean chuyên biệt của hệ sinh thái test**; chúng không đồng nghĩa việc luôn cần bật property overriding cho toàn ứng dụng. Với code sản phẩm, ưu tiên configuration/extension points chính thức thay vì dùng trùng tên tình cờ.

**Ví dụ lựa chọn ít rủi ro:** thay vì đăng ký hai beans cùng name, cấu hình một method `@Bean` duy nhất chọn implementation từ một property hợp lệ rồi trả interface chung; hoặc đăng ký hai tên rõ và chọn bằng qualifier/profile khi đúng nhu cầu.

## 14. Bài tập và đáp án

**Bài 1.** Hai `@Bean` cùng tên `client` nhưng một trả `MailClient`, một trả `SmsClient`. Đã có xung đột tên chưa?

**Đáp án:** Có thể có nếu cả hai định nghĩa cùng được đưa vào **một registry dưới name client**; class khác không làm mất va chạm. Xử lý thực tế qua `@Bean` parsing có thể có các bước riêng; kiểm tra kết quả đăng ký thay vì suy diễn chắc thứ tự thắng.

**Bài 2.** Hai beans tên `firstClient` và `secondClient` cùng implement interface `Client`. `allowBeanDefinitionOverriding=false` có chặn chúng không?

**Đáp án:** Không vì tên khác nhau. Nếu injection một `Client` không có lựa chọn duy nhất, đó là autowiring ambiguity và cần qualifier/primary/tái thiết kế.

**Bài 3.** Trong nhánh denied, vì sao sau lỗi vẫn nhận `FIRST`?

**Đáp án:** Registration SECOND bị từ chối trước refresh, definition FIRST không bị thay. Context vẫn có thể refresh với definition hợp lệ còn lại.

**Bài 4.** Trong nhánh allowed, tại sao chỉ có một bean name `notificationClient` dù đăng ký hai lần?

**Đáp án:** Định nghĩa mới đã thay định nghĩa cũ **dưới cùng key** trước refresh, không tạo hai beans riêng cùng tên trong registry.

**Bài 5.** Alias `preferredClient` là một bean mới không?

**Đáp án:** Không. Nó là tên phụ tra cùng bean; `aliasSameInstance=true` và `aliasIsDefinition=false` chứng minh điều đó.

**Bài 6.** Vì sao một ứng dụng Spring Boot báo lỗi override còn demo Framework thuần có thể cho phép?

**Đáp án:** Mặc định policy khác nhau. Framework DefaultListableBeanFactory thường cho phép; Boot đặt mặc định cấm. Xác minh policy ở môi trường thật và chỉ bật override nếu có chủ đích.

**Bài 7.** Có nên chuyển policy sang true sau khi context đã chạy để đổi implementation không?

**Đáp án:** Không coi đây là cách hot swap. Metadata và singleton references có thể đã được cache/inject; nên cấu hình đúng từ bootstrap hoặc dùng cơ chế reconfiguration phù hợp.

## 15. Checklist ứng dụng thực tế

- [ ] Mỗi bean có tên rõ ràng, tránh chiếm name generic mà thư viện khác có thể dùng.
- [ ] Biết bean name là registry key, không phải class name, alias hay type.
- [ ] Phân biệt collision cùng name với ambiguity nhiều beans cùng type.
- [ ] Nắm `setAllowBeanDefinitionOverriding(false/true)` và mặc định Framework/Boot.
- [ ] Đặt policy **trước khi đăng ký/refresh** nếu tự sở hữu context.
- [ ] Nếu cấm: biết đọc `BeanDefinitionOverrideException` và tên gây xung đột.
- [ ] Nếu cho phép: kiểm tra bean runtime thực sự là definition mong muốn.
- [ ] Hiểu alias không sinh bean definition thứ hai.
- [ ] Không tin rằng thứ tự source code của nhiều cấu hình bảo đảm ai thắng.
- [ ] Không dùng overriding làm cơ chế thay bean đã khởi tạo.
- [ ] Kiểm tra các nguồn @Bean, scanning, XML/API và framework integration.
- [ ] Chạy Java 17+ Maven/`javac`/`java` và đối chiếu **10 dòng stdout**.
- [ ] Có startup smoke test phát hiện collision bất ngờ khi thêm module.
- [ ] Chỉ cho phép override khi có hợp đồng rõ và có test chứng minh đúng implementation.

**Kết luận:** Xung đột tên trong Spring là xung đột **cùng registry key của BeanDefinition**. Khi cấm overriding, đăng ký trùng bị từ chối; khi cho phép, definition đến sau có thể thay definition trước theo quy tắc đăng ký. Hãy dùng **chính sách tường minh**, phân biệt alias và nhiều bean cùng kiểu, đồng thời ưu tiên **sửa thiết kế registration** trước khi bật override chỉ để ứng dụng khởi động.

### Tài liệu chính thức

- [DefaultListableBeanFactory — Bean Definition Overriding](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/support/DefaultListableBeanFactory.html)
- [GenericApplicationContext — registerBeanDefinition / setAllowBeanDefinitionOverriding](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/GenericApplicationContext.html)
- [BeanDefinitionOverrideException API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/support/BeanDefinitionOverrideException.html)
- [Spring Boot — Common Application Properties](https://docs.spring.io/spring-boot/appendix/application-properties/)


</details>

- [Quay lại đầu trang](#back-to-top)
