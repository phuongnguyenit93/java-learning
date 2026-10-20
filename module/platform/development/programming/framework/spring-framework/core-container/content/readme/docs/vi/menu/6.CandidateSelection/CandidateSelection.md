<a id="back-to-top"></a>

# Chọn ứng viên

## Menu
- [Tra cứu ứng viên theo kiểu có thể gán](#candidate-selection-001)
- [Giải quyết một phụ thuộc có nhiều bean cùng kiểu](#candidate-selection-002)
- [Ưu tiên một ứng viên bằng @Primary](#candidate-selection-003)
- [Lọc tập ứng viên bằng @Qualifier](#candidate-selection-004)
- [Tạo annotation qualifier theo ý nghĩa nghiệp vụ](#candidate-selection-005)
- [Dùng tham số kiểu tổng quát làm điều kiện chọn bean](#candidate-selection-006)
- [Tên tham số, tên bean và vai trò của tùy chọn biên dịch -parameters](#candidate-selection-007)
- [@Fallback và điều kiện phiên bản từ Spring Framework 6.2](#candidate-selection-008)
- [Giới hạn ứng viên bằng autowire-candidate](#candidate-selection-009)
- [Phân biệt ưu tiên lựa chọn với thứ tự khởi tạo](#candidate-selection-010)

## <a id="candidate-selection-001">Tra cứu ứng viên theo kiểu có thể gán</a>

<details>
<summary>Xem chi tiết</summary>

# Tra cứu ứng viên theo kiểu có thể gán trong Spring Core Container

## 1. Từ kiểu được yêu cầu đến tập bean ứng viên

Spring Dependency Injection thường bắt đầu từ câu hỏi: **“Có những bean nào có kiểu có thể gán (*assignable*) vào kiểu mà injection point yêu cầu?”** Ví dụ một service có constructor nhận `Channel`, nhưng container có hai class `EmailChannel` và `SmsChannel` cùng implement `Channel`. Cả hai có thể **khớp về kiểu** mà vẫn chưa thể chọn một bean duy nhất.

Hãy phân biệt ba việc:

1. **Type matching (khớp kiểu):** bean được khai báo hoặc được suy ra là có type phù hợp với interface/superclass được yêu cầu không?
2. **Autowire candidate filtering (lọc ứng viên):** bean khớp kiểu ấy có được phép tham gia quá trình tự động tiêm dependency tại điểm inject không? Có bị loại bởi `autowireCandidate=false`, qualifier, điều kiện khác không?
3. **Single-candidate selection (chọn một bean):** nếu còn nhiều ứng viên hợp lệ, Spring dùng `@Primary`, `@Qualifier`, metadata và các quy tắc ưu tiên khác để chọn hoặc báo ambiguity.

**Một bean khớp type chưa chắc là một autowire candidate hợp lệ; một danh sách nhiều type matches cũng không có nghĩa injection luôn thất bại.** Với collection injection, Spring còn có thể đưa **nhiều beans** thích hợp vào cùng `List<T>` hoặc `Map<String,T>`.

| Câu hỏi | API/cơ chế | Trả lời |
| --- | --- | --- |
| Class Java có thể gán vào một interface hoặc superclass? | `isAssignableFrom`, `instanceof` | Quan hệ kiểu trong **Java** |
| Container biết tên những beans nào khớp type? | `getBeanNamesForType(Type.class)` | Danh sách **tên beans phù hợp type**, chưa nhất thiết lọc đúng injection point |
| Cần instances của các beans khớp type? | `getBeansOfType(Type.class)` | Map **bean name → bean object**, có thể khởi tạo beans |
| Một bean cụ thể có khớp type? | `isTypeMatch("beanName", Type.class)` | Kiểm tra type của bean được chỉ định |
| Cần đúng một bean theo type? | `getBean(Type.class)` | Trả bean hoặc lỗi nếu thiếu/mơ hồ, với quy tắc ưu tiên tương ứng |
| Một constructor cần type `T`? | `@Autowired`/factory-method parameter | Resolution của một **injection point**, còn phải lọc candidates |
| Cần tất cả beans được phép autowire kiểu `T`? | `@Autowired List<T>`, `Map<String,T>` | Tập các dependency candidates phù hợp, có thể được sắp thứ tự |

Mục tiêu bài: đọc đúng quan hệ **assignable type**, kiểm chứng tác động của inheritance/interface/generic, nhìn rõ khác biệt giữa **tra cứu theo type** và **lọc ứng viên autowiring**, rồi xác định khi nào phải dùng `@Primary`/`@Qualifier`.

Bài sử dụng **Java 17+**, **Spring Framework 7.0.9**, Maven và CLI. Đây là Spring Core Container thuần, không yêu cầu Spring Boot hoặc môi trường web.

## 2. “Có thể gán” trong Java nghĩa là gì?

Trong Java, `Child extends Parent` và `Implementation implements Contract` tạo các quan hệ kiểu. Nếu phương thức cần `Parent`, một object `Child` hợp lệ có thể được truyền vào; nếu cần `Contract`, object của class implement `Contract` cũng hợp lệ.

~~~java
// Snippet minh họa, không phải file biên dịch độc lập.
interface Channel {
    String id();
}
abstract class BaseChannel implements Channel { }
class EmailChannel extends BaseChannel {
    public String id() { return "EMAIL"; }
}
class SmsChannel extends BaseChannel {
    public String id() { return "SMS"; }
}
~~~

Những biểu thức sau có ý nghĩa:

~~~java
Channel a = new EmailChannel();        // Hợp lệ: EmailChannel implements Channel gián tiếp.
BaseChannel b = new SmsChannel();      // Hợp lệ: SmsChannel extends BaseChannel.
boolean yes = Channel.class.isAssignableFrom(SmsChannel.class);
boolean no = SmsChannel.class.isAssignableFrom(Channel.class);
~~~

**Đọc đúng hướng của `Class.isAssignableFrom`:**

`Target.class.isAssignableFrom(Source.class)` hỏi: một value thuộc **Source** có thể dùng tại vị trí mong đợi **Target** theo quan hệ kiểu thông thường không? Vì vậy:
- `Channel.class.isAssignableFrom(EmailChannel.class)` → `true`;
- `BaseChannel.class.isAssignableFrom(SmsChannel.class)` → `true`;
- `EmailChannel.class.isAssignableFrom(Channel.class)` → `false`.

Đây là ví dụ về **reference type compatibility**, không phải chuyển đổi giá trị tùy ý. Nếu một bean thực là `SmsChannel`, Spring không thể tiêm nó vào field kiểu `EmailChannel` chỉ vì cả hai cùng có interface `Channel`. Chúng là hai class sibling, không có quan hệ assignability từ SMS sang Email.

**Ghi nhớ chiều matching**: injection point đòi `Channel` thì `SmsChannel` có thể phù hợp; injection point đòi `SmsChannel` thì một bean chỉ biết là `Channel` **không được mặc nhiên coi là** `SmsChannel`.

Spring còn sử dụng **type metadata, factory methods, generics và proxy information**. Do đó việc Spring biết/tiên đoán type trước khi tạo object có thể phức tạp hơn một lần gọi `isAssignableFrom` với runtime class của object.

## 3. Spring lấy thông tin type từ đâu?

Nguồn thông tin type không chỉ có một:

- **Component-scanned class:** từ bean class được phát hiện (`@Component`, `@Service`, …), class hierarchy, interfaces và metadata đăng ký.
- **`@Bean` factory method:** Spring có thể dựa vào **declared return type** của factory method trước khi instance thực được tạo.
- **XML bean definitions:** từ `class`, factory-method/factory-bean metadata và những thông tin container suy ra.
- **Bean instance đã được tạo:** Spring có thể dùng type đã biết của instance khi thích hợp; tuy nhiên proxy có thể thay đổi runtime class/kiểu mà caller nhìn thấy.
- **`FactoryBean<T>`:** tên bean thường đại diện cho **sản phẩm** `T`, trong khi bản thân factory được tham chiếu bằng dạng `&beanName`. Type prediction còn liên quan `getObjectType()`.
- **Generic metadata:** `ResolvableType` và chữ ký generic giúp phân biệt `Store<String>` với `Store<Integer>`.

Ví dụ cần chú ý khi cấu hình `@Bean`:

~~~java
// Snippet minh họa, không phải project độc lập:
@Bean
Channel smsChannel() {
    return new SmsChannel();
}
~~~

Dù method **trả về instance** của `SmsChannel`, *declared return type* của nó là `Channel`. Trước khi bean được tạo, Spring **không nhất thiết biết** nó có type cụ thể `SmsChannel` để resolve một injection point yêu cầu subclass. Vì vậy tài liệu Spring khuyến nghị chữ ký `@Bean` **đủ cụ thể** khi bạn định inject theo implementation type:

~~~java
@Bean
SmsChannel smsChannel() {
    return new SmsChannel();
}
~~~

Khi injection chỉ cần `Channel`, cả hai kiểu khai báo có thể đáp ứng; nhưng nếu injection yêu cầu `SmsChannel`, kiểu trả về cụ thể làm metadata ổn định và rõ ràng hơn.

**Không dùng kết quả runtime `getClass()` làm lý do bỏ qua chữ ký public API**. Với generic, proxies, `FactoryBean` và lazy beans, dựa vào class runtime duy nhất rất dễ dự đoán sai những type matches mà Spring có thể biết trong từng giai đoạn.

## 4. Hai nhóm API: khám phá type và lấy dependency

### 4.1. `getBeanNamesForType`: khám phá tên

~~~java
String[] names = context.getBeanNamesForType(Channel.class);
~~~

API thuộc `ListableBeanFactory` (được `ApplicationContext` cung cấp) trả về tên của các beans có type tương thích với `Channel`, **bao gồm subclass/implementation**.

- Không cần giả định chỉ tìm bean có `getClass() == Channel.class`: interface thường không thể instantiate trực tiếp.
- Trả về **tên**, không phải `Channel` objects.
- Kết quả có thể gồm bean mà **autowire-candidate filtering** sẽ loại khi xử lý injection point.
- Đây không phải danh sách aliases: alias không tự nhân số bean definitions/instances.
- Thứ tự trả về có thể liên quan thứ tự đăng ký; muốn assert chính xác trong bài, hãy **sort** trước khi in.
- Container nội bộ có nhiều infrastructure beans; chỉ liệt kê **type cụ thể của chủ đề**, không in toàn bộ `getBeanDefinitionNames()` rồi kết luận số lượng cố định.

### 4.2. `getBeansOfType`: lấy cả instances

~~~java
Map<String, Channel> beans = context.getBeansOfType(Channel.class);
~~~

API trả map tên→instance, có thể gây khởi tạo bean và callbacks liên quan. Nó **khác** việc Spring inject `Map<String,Channel>` tại một injection point:

- `getBeansOfType(Channel.class)` là **liệt kê tất cả beans khớp type** theo hành vi của API.
- `@Autowired Map<String,Channel>` còn chịu **autowire candidate rules**: bean `autowireCandidate=false` có thể bị bỏ khỏi injected map, dù vẫn xuất hiện trong các API discovery/get-by-name.

Đây là bẫy phổ biến khi debug: “`getBeanNamesForType` tìm thấy ba beans, sao `List<Channel>` được tiêm chỉ có hai?” — vì **type matching và injectability là hai bước khác nhau**.

### 4.3. `isTypeMatch`, `getBean(Class)` và `getBean(name, Class)`

- `context.isTypeMatch("smsChannel", BaseChannel.class)` kiểm tra named bean có phù hợp với superclass này không.
- `context.getBean(Channel.class)` cần chọn **một bean** có thể gán cho type ấy; nhiều ứng viên không có quy tắc ưu tiên phù hợp có thể ném `NoUniqueBeanDefinitionException`.
- `context.getBean("smsChannel", Channel.class)` lấy **bean theo tên**, đồng thời kiểm tra type tương thích; không chọn một bean khác ngẫu nhiên.

`@Primary`, `@Qualifier`, bean name và những chính sách khác tham gia resolution theo ngữ cảnh cụ thể. Đừng suy luận rằng một bean được trả về do “chính xác type trùng tên method” mà không xét metadata.

## 5. Type matching không đồng nghĩa autowire candidate selection

**`autowireCandidate=false`** cho phép giữ một bean trong context để tra cứu chủ động nhưng **loại nó khỏi type-based autowiring** thông thường.

~~~java
// Snippet nằm trong @Configuration phù hợp:
@Bean(name = "localChannel", autowireCandidate = false)
LocalChannel localChannel() {
    return new LocalChannel();
}
~~~

Hệ quả:
1. Bean `localChannel` vẫn có tên/định nghĩa, Spring vẫn có thể tạo object theo cơ chế bean.
2. `getBean("localChannel")` vẫn có thể truy cập nó theo tên.
3. `getBeanNamesForType(Channel.class)` vẫn có thể **phát hiện** type match của nó.
4. Nhưng Spring **không dùng nó làm candidate autowiring** cho `@Autowired Channel`, `@Autowired List<Channel>` hay constructor parameter cần `Channel`.

Với Spring 7, `@Bean` còn có metadata `defaultCandidate`, `@Primary`, `@Fallback` và các cách fine-tune độ ưu tiên khi chọn bean. Không nên gom tất cả thành một boolean duy nhất “match hoặc không match”. `autowireCandidate=false` là **loại bean khỏi autowiring candidates**, còn `@Primary` nói rằng một candidate được ưu tiên trong một số tình huống lựa chọn **một** dependency.

| Trạng thái bean | Type match? | Có thể xuất hiện trong discovery? | Candidate autowiring thông thường? |
| --- | --- | --- | --- |
| `EmailChannel` bình thường | Có, với `Channel` và `BaseChannel` | Có | Có |
| `SmsChannel` bình thường | Có | Có | Có |
| `LocalChannel` với `autowireCandidate=false` | Có | Có | **Không** |
| `EmailChannel` đánh dấu `@Primary` | Có | Có | Có, thường được ưu tiên khi cần một bean |
| Class hoàn toàn không implements `Channel` | Không | Không theo type `Channel` | Không theo type `Channel` |

**Lưu ý:** `getBeanNamesForType` là API **khám phá đối tượng phù hợp type**, không phải API “lấy duy nhất tất cả candidate cuối cùng ở mọi injection point”. `@Qualifier`, generic type và thông tin injection point có thể lọc tập kết quả theo những cách mà một call `getBeanNamesForType(Channel.class)` không diễn đạt đủ.

## 6. Khi nhiều ứng viên cùng có thể gán, Spring chọn thế nào?

Kết quả `getBeanNamesForType(Channel.class)` có thể chứa nhiều bean hợp kiểu, nhưng một điểm tiêm `Channel` chỉ nhận **một** bean. Khi đó Spring còn kiểm tra quyền tham gia autowiring, các qualifier, quy tắc ưu tiên và metadata của injection point. Nếu còn nhiều ứng viên ngang nhau, phép chọn đơn trị có thể thất bại với `NoUniqueBeanDefinitionException`.

| Nhu cầu | Hướng giải quyết | Ranh giới |
| --- | --- | --- |
| Một implementation mặc định | `@Primary` | Không xóa ứng viên khác và không sắp thứ tự khởi tạo |
| Một implementation theo vai trò | `@Qualifier` tại injection point | Chỉ lọc những bean vẫn hợp kiểu và đủ điều kiện |
| Một bean dự phòng | `@Fallback` (Spring 6.2+) | Hạ ưu tiên, không tạo failover khi chạy |
| Tất cả implementations | `List<Channel>` / `Map<String, Channel>` | Chọn tập hợp, không yêu cầu một ứng viên duy nhất |

Bài kế tiếp phân tích từng nhánh resolution với các context độc lập. Ở bài này, điểm cần giữ là **type discovery là điều kiện đầu vào, chưa phải kết quả dependency injection**.

## 7. Generic types cũng tham gia xác định candidates

Khi điểm tiêm yêu cầu `Store<Channel>`, Spring có thể dùng chữ ký generic đã biết của bean definition hoặc factory method để lọc ngoài kiểm tra raw type. `Store<Channel>` và `Store<String>` đều có raw type `Store`, nhưng không mặc nhiên thay thế nhau. Chữ ký `@Bean Store<Channel> channelStore()` thể hiện hợp đồng rõ hơn `@Bean Store rawStore()`.

Đây là metadata ở cấp khai báo; Spring không “phục hồi” mọi tham số kiểu từ object sau type erasure. Nếu factory method khai raw type, generic metadata có thể thiếu và cần điều tra dựa trên khai báo thực tế. Generic filtering cũng **không** giải quyết mơ hồ khi có hai bean cùng đầy đủ type. Bài chuyên sâu về generic qualifier sẽ thử trường hợp này với single bean, collection và qualifier bổ sung.

## 8. Demo đầy đủ Java 17 + Maven + Spring 7.0.9

Demo cho thấy:
- Hai class `EmailChannel` và `SmsChannel` **extends `BaseChannel` và implements `Channel`**, còn SMS thêm interface `Auditable`.
- `LocalChannel` vẫn match type `Channel` nhưng khai `autowireCandidate=false`; nó **có trong type discovery nhưng vắng khỏi injected list/map**.
- `EmailChannel` là `@Primary`; single dependency `Channel` chọn EMAIL.
- Injection theo `Auditable` nhận SMS; generic `Vault<String>` khác `Vault<Integer>`.
- Một context khác không có primary sẽ dẫn tới ambiguity khi tra đúng một `Channel` theo type.

Toàn bộ code nằm trong **một file Java**, không cần database, web server hoặc Spring Boot. Các thao tác in danh sách luôn được `Arrays.sort`/`sorted()` để có **stdout ổn định**, không phụ thuộc vào thứ tự bean registrations.

### 8.1. Cấu trúc project

~~~text
assignable-candidates-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── AssignableCandidatesDemo.java
~~~

Yêu cầu **JDK 17+**, **Maven 3.9+**, kết nối Maven Central/mirror nếu thư viện chưa nằm trong local cache.

### 8.2. `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>assignable-candidates-demo</artifactId>
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
                    <mainClass>demo.AssignableCandidatesDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` đem theo các Spring modules cơ bản qua Maven transitive dependency resolution. `maven.compiler.release=17` dùng Java 17 bytecode/API. Ví dụ chỉ có Java configuration.

### 8.3. `src/main/java/demo/AssignableCandidatesDemo.java`

~~~java
package demo;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ResolvableType;

public final class AssignableCandidatesDemo {
    private AssignableCandidatesDemo() {
    }

    interface Channel {
        String id();
    }

    interface Auditable {
        String auditTag();
    }

    abstract static class BaseChannel implements Channel {
    }

    static final class EmailChannel extends BaseChannel {
        @Override public String id() {
            return "EMAIL";
        }
    }

    static final class SmsChannel extends BaseChannel implements Auditable {
        @Override public String id() {
            return "SMS";
        }

        @Override public String auditTag() {
            return "SMS-AUDIT";
        }
    }

    static final class LocalChannel extends BaseChannel {
        @Override public String id() {
            return "LOCAL";
        }
    }

    @FunctionalInterface
    interface Vault<T> {
        String kind();
    }

    static final class Consumer {
        private final Channel primary;
        private final Auditable audit;
        private final List<Channel> channels;
        private final Map<String, Channel> channelMap;
        private final Vault<String> stringVault;
        private final List<Vault<Integer>> integerVaults;

        Consumer(Channel primary, Auditable audit,
                List<Channel> channels, Map<String, Channel> channelMap,
                Vault<String> stringVault, List<Vault<Integer>> integerVaults) {
            this.primary = primary;
            this.audit = audit;
            this.channels = channels;
            this.channelMap = channelMap;
            this.stringVault = stringVault;
            this.integerVaults = integerVaults;
        }

        String primaryName() {
            return primary.id();
        }

        String auditTag() {
            return audit.auditTag();
        }

        String channelList() {
            return channels.stream().map(Channel::id)
                    .sorted().collect(Collectors.joining(","));
        }

        String mapKeys() {
            return channelMap.keySet().stream()
                    .sorted().collect(Collectors.joining(","));
        }

        String textKind() {
            return stringVault.kind();
        }

        String integerKinds() {
            return integerVaults.stream().map(Vault::kind)
                    .sorted().collect(Collectors.joining(","));
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean("emailChannel")
        @Primary
        public EmailChannel email() {
            return new EmailChannel();
        }

        @Bean("smsChannel")
        public SmsChannel sms() {
            return new SmsChannel();
        }

        @Bean(name = "localChannel", autowireCandidate = false)
        public LocalChannel local() {
            return new LocalChannel();
        }

        @Bean
        public Vault<String> stringVault() {
            return () -> "STRING";
        }

        @Bean
        public Vault<Integer> integerVault() {
            return () -> "INTEGER";
        }

        @Bean
        public Consumer consumer(
                Channel selected,
                @Qualifier("smsChannel") Auditable audit,
                List<Channel> channels,
                Map<String, Channel> channelsByName,
                Vault<String> stringVault,
                List<Vault<Integer>> integerVaults) {
            return new Consumer(selected, audit, channels,
                    channelsByName, stringVault, integerVaults);
        }
    }

    // Cố ý không dùng @Primary để minh họa ambiguity.
    @Configuration(proxyBeanMethods = false)
    public static class AmbiguousConfig {
        @Bean
        public EmailChannel first() {
            return new EmailChannel();
        }

        @Bean
        public SmsChannel second() {
            return new SmsChannel();
        }
    }

    private static String names(String[] values) {
        Arrays.sort(values);
        return String.join(",", values);
    }

    private static String keys(Map<String, ?> map) {
        return map.keySet().stream()
                .sorted().collect(Collectors.joining(","));
    }

    public static void main(String[] args) {
        System.out.println("java.channelFromEmail="
                + Channel.class.isAssignableFrom(EmailChannel.class));
        System.out.println("java.emailFromChannel="
                + EmailChannel.class.isAssignableFrom(Channel.class));
        System.out.println("java.baseFromSms="
                + BaseChannel.class.isAssignableFrom(SmsChannel.class));
        System.out.println("java.auditFromSms="
                + Auditable.class.isAssignableFrom(SmsChannel.class));

        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            System.out.println("type.channel="
                    + names(context.getBeanNamesForType(Channel.class)));
            System.out.println("type.base="
                    + names(context.getBeanNamesForType(BaseChannel.class)));
            System.out.println("type.email="
                    + names(context.getBeanNamesForType(EmailChannel.class)));
            System.out.println("type.audit="
                    + names(context.getBeanNamesForType(Auditable.class)));
            System.out.println("type.unknown="
                    + context.getBeanNamesForType(Runnable.class).length);
            System.out.println("type.smsIsBase="
                    + context.isTypeMatch("smsChannel", BaseChannel.class));
            System.out.println("type.localRegistered="
                    + context.containsBeanDefinition("localChannel"));
            System.out.println("type.localByName="
                    + context.getBean("localChannel", Channel.class).id());
            System.out.println("type.allObjects="
                    + keys(context.getBeansOfType(Channel.class)));

            Consumer consumer = context.getBean(Consumer.class);
            System.out.println("wire.primary=" + consumer.primaryName());
            System.out.println("wire.audit=" + consumer.auditTag());
            System.out.println("wire.list=" + consumer.channelList());
            System.out.println("wire.map=" + consumer.mapKeys());
            System.out.println("wire.stringVault=" + consumer.textKind());
            System.out.println("wire.integerVaults=" + consumer.integerKinds());

            ResolvableType textType =
                    ResolvableType.forClassWithGenerics(
                            Vault.class, String.class);
            ResolvableType intType =
                    ResolvableType.forClassWithGenerics(
                            Vault.class, Integer.class);
            System.out.println("type.vault.text="
                    + names(context.getBeanNamesForType(textType)));
            System.out.println("type.vault.integer="
                    + names(context.getBeanNamesForType(intType)));
        }

        // Với hai candidates cùng type nhưng không có primary.
        try (var context = new AnnotationConfigApplicationContext(
                AmbiguousConfig.class)) {
            context.getBean(Channel.class);
            System.out.println("unexpected");
        } catch (NoUniqueBeanDefinitionException expected) {
            System.out.println("ambiguousLookup.failed=true");
        }
    }
}
~~~

### 8.4. Vì sao ví dụ là một phép so sánh hợp lệ?

- `emailChannel`, `smsChannel` và `localChannel` đều có Java class extends `BaseChannel`. Vì `BaseChannel implements Channel`, **cả ba types** đều assignable sang `Channel`.
- Chỉ `SmsChannel` implements `Auditable`, nên `getBeanNamesForType(Auditable.class)` có đúng một bean tên `smsChannel`.
- `EmailChannel` và `SmsChannel` đủ điều kiện autowiring; `LocalChannel` bị loại nhờ `@Bean(autowireCandidate=false)`.
- `getBeanNamesForType(Channel.class)` có thể phát hiện **cả ba**, vì API là **type discovery**, không phải autowiring candidate resolution cho một injection point.
- Inject một `Channel` vào method `consumer(...)` chọn **EMAIL** vì `@Primary` chỉ định một bean ưu tiên trong những candidates còn hợp lệ.
- Inject `List<Channel>` và `Map<String,Channel>` chỉ nhận **EMAIL/SMS**, không có `LOCAL`; đó là chứng cứ autowiring filtering không giống type discovery.
- Type `Auditable` + `@Qualifier("smsChannel")` chọn **SMS** bằng cả hợp đồng interface và qualifier.
- `Vault<String>`/ `Vault<Integer>` có chung raw interface nhưng tách theo **generic argument** nhờ metadata từ `@Bean` return signatures.
- Trong `AmbiguousConfig`, hai `Channel` cùng match type, không primary; `getBean(Channel.class)` không được tùy ý chọn một nên thất bại.

**Chú ý runtime:** `@Bean("emailChannel")` khai báo return type cụ thể là `EmailChannel`; `@Bean("smsChannel")` trả `SmsChannel`. Điều này giúp Spring nhận biết type tương ứng trước khi object được tạo. Demo không buộc Spring đoán implementation từ một factory method chỉ khai return type `Channel`.

## 9. Lệnh chạy và expected stdout

Trong thư mục `assignable-candidates-demo` chứa `pom.xml`, kiểm tra:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

Ba lệnh kiểm tra phiên bản có kết quả tùy máy. Lệnh Maven cuối chạy `javac` qua Compiler Plugin và sử dụng dependency classpath để chạy `demo.AssignableCandidatesDemo`.

**22 dòng stdout do chính chương trình in** (không tính Maven/backend logging):

~~~text
java.channelFromEmail=true
java.emailFromChannel=false
java.baseFromSms=true
java.auditFromSms=true
type.channel=emailChannel,localChannel,smsChannel
type.base=emailChannel,localChannel,smsChannel
type.email=emailChannel
type.audit=smsChannel
type.unknown=0
type.smsIsBase=true
type.localRegistered=true
type.localByName=LOCAL
type.allObjects=emailChannel,localChannel,smsChannel
wire.primary=EMAIL
wire.audit=SMS-AUDIT
wire.list=EMAIL,SMS
wire.map=emailChannel,smsChannel
wire.stringVault=STRING
wire.integerVaults=INTEGER
type.vault.text=stringVault
type.vault.integer=integerVault
ambiguousLookup.failed=true
~~~

**Quan sát then chốt:**
1. Bốn dòng `java.*` kiểm chứng Java `Class.isAssignableFrom`: interface/superclass nhận subclass/implementation, nhưng hướng ngược không hợp lệ.
2. `type.channel` và `type.base` đều có ba beans, còn `type.audit` chỉ có SMS — Spring xét **quan hệ type**, không chỉ so tên class bằng nhau.
3. `type.localRegistered=true`, `type.localByName=LOCAL` chứng minh bean loại khỏi autowiring **vẫn tồn tại và có thể lookup**.
4. `type.allObjects` liệt kê cả LOCAL, còn `wire.list`/`wire.map` **không có LOCAL**. Đây là sự phân biệt quan trọng nhất của ví dụ.
5. `wire.primary=EMAIL`: Spring chọn một trong những candidates nhờ `@Primary`, nhưng `wire.list` vẫn chứa SMS; primary không có nghĩa loại những beans khác khỏi injected collection.
6. `type.vault.*` tách đúng hai generic specializations; `Vault<String>` không bị coi như `Vault<Integer>`.
7. `ambiguousLookup.failed=true` là thất bại **có chủ đích** của lookup không có primary; chương trình catch `NoUniqueBeanDefinitionException`.

Lưu ý: nếu cố tình thay cấu hình tạo bean khác hoặc thay generic return signatures, **stdout chuẩn sẽ không còn đúng**. Nếu logger ghi thêm thông báo startup failure, chỉ so 22 dòng ứng dụng đã nêu; không cố định mọi stack trace ở terminal.

## 10. Java 17 thuần với `javac`/`java`

Tạo một file độc lập **`JavaAssignableTypes.java`**, không cần Maven hay Spring, để quan sát nền Java của khái niệm *assignable*.

~~~java
public class JavaAssignableTypes {
    interface Sender {
        String send();
    }

    abstract static class BaseSender implements Sender {
    }

    static final class SmsSender extends BaseSender {
        @Override
        public String send() {
            return "SMS";
        }
    }

    static final class EmailSender extends BaseSender {
        @Override
        public String send() {
            return "EMAIL";
        }
    }

    public static void main(String[] args) {
        Sender sms = new SmsSender();
        BaseSender email = new EmailSender();

        System.out.println("senderFromSms="
                + Sender.class.isAssignableFrom(SmsSender.class));
        System.out.println("baseFromEmail="
                + BaseSender.class.isAssignableFrom(EmailSender.class));
        System.out.println("smsFromSender="
                + SmsSender.class.isAssignableFrom(Sender.class));
        System.out.println("values=" + sms.send() + "," + email.send());
    }
}
~~~

Chạy trong thư mục chứa file:

~~~bash
javac JavaAssignableTypes.java
java JavaAssignableTypes
~~~

Stdout chính xác:

~~~text
senderFromSms=true
baseFromEmail=true
smsFromSender=false
values=SMS,EMAIL
~~~

Đây là **quan hệ kiểu Java thuần**. Spring tận dụng metadata của Java class/interface và bổ sung BeanDefinition, qualifier, scope, proxy và các quy tắc autowire candidate. Hãy hiểu quan hệ Java trước khi suy luận cách container chọn một bean từ nhiều registrations.

## 11. Các trường hợp đặc biệt và giới hạn của việc tra cứu type

### 11.1. Scope và lazy beans

`getBeanNamesForType(Class)` có thể tìm tên dựa trên metadata type của singleton, prototype, và các beans khác theo khả năng suy ra. Overload:

~~~java
context.getBeanNamesForType(Channel.class, true, false);
~~~

có hai cờ:
- `includeNonSingletons=true`: cho phép bao gồm prototype hoặc scoped beans;
- `allowEagerInit=false`: yêu cầu tránh một số kiểu eager initialization để xác định type, như khởi tạo `FactoryBean` hoặc factory tham chiếu cần xét type.

**Kết quả của hai overload có thể khác nhau**, nhất là khi type chưa thể biết mà không khởi tạo thêm metadata-producing objects. Đừng dùng một type-query với `allowEagerInit=true` trong code tưởng như chỉ “kiểm tra” rồi mặc nhiên suy luận không có tác dụng phụ hoặc startup cost.

### 11.2. `FactoryBean` và dấu `&`

Giả sử `widgetFactory` là một `FactoryBean<Widget>`:
- `getBean("widgetFactory")` có thể trả **Widget product**;
- `getBean("&widgetFactory")` trả **FactoryBean instance**;
- `getBeanNamesForType(Widget.class)` nhằm tìm type của **product**, nếu container có đủ metadata;
- Type prediction có thể dựa vào `getObjectType()` và chính sách `allowEagerInit`.

Vì vậy, nếu bạn debug mà thấy tên bean match một interface nhưng class của factory không implement interface ấy, hãy kiểm tra có `FactoryBean` hay không. Không nhầm `@Bean` factory method với việc class implements `FactoryBean<T>`: đây là hai cơ chế khác.

### 11.3. AOP proxy và type prediction

AOP có thể bọc đối tượng target trong một proxy. Với **JDK dynamic proxy**, proxy có thể chỉ lộ các interfaces, không phải concrete target class; với class-based proxy, quan hệ kiểu có thể khác. Khi một injection point đòi implementation class nhưng proxy chỉ phơi interface, type matching/injection có thể gặp vấn đề.

**Thực hành:** hãy inject qua một **interface hợp đồng** khi thích hợp, khai báo return type factory method rõ ràng và kiểm tra proxy mode. Không dựa vào `bean.getClass() == Implementation.class` để kết luận bean không phù hợp, vì đó có thể là proxy hợp lệ.

### 11.4. Parent/child application contexts

Một `ListableBeanFactory` lookup có thể chỉ tra **bean definitions cục bộ**; tài liệu API lưu ý những `getBeanNamesForType` methods không phải lúc nào cũng hợp nhất parent factory. Nếu thực sự cần names từ ancestor contexts, Spring có `BeanFactoryUtils.beanNamesForTypeIncludingAncestors(...)`.

Đừng nhầm **parent ApplicationContext** với `parent="templateBean"` trong bean definition inheritance. Một cái là hệ phân cấp containers, cái kia là kế thừa metadata của definitions.

### 11.5. Alias, visibility và no candidate

Bean alias không tạo additional bean definition. Một bean mang alias `backupSender` vẫn đại diện cho một bean, không làm danh sách type matches tự nhân đôi các instances.

`autowireCandidate=false` ảnh hưởng autowiring, không biến bean thành “không tồn tại”. Ngược lại, một class chỉ có trên classpath nhưng chưa được Spring đăng ký bằng `@Bean`, component scan hoặc API khác **không tự xuất hiện trong các type queries**.

**Đừng hiểu “chỉ tìm được một bean theo type” là chắc chắn bean hợp lệ ở mọi injection point**: qualifier có thể yêu cầu tên cụ thể khác, generic type có thể không khớp, hoặc candidate có thể bị loại bởi metadata. Những điều kiện ấy phải được xem trong ngữ cảnh của điểm tiêm.

## 12. Debug type matching và những lỗi thường gặp

| Hiện tượng hoặc nhận định sai | Nguyên nhân/cách điều tra đúng |
| --- | --- |
| `getBeanNamesForType(Channel.class)` trả nhiều tên | Nhiều subclasses/implementations cùng assignable; chưa phải lỗi |
| `getBean(Channel.class)` báo `NoUniqueBeanDefinitionException` | Lookup cần **một** instance nhưng nhiều candidates không có chính sách chọn |
| `@Autowired Channel` thiếu bean dù class implementation có trong project | Class chưa đăng ký thành bean, scope/profile/config không active hoặc bị lọc |
| Type discovery thấy bean nhưng `List<Channel>` không có nó | Có thể `autowireCandidate=false` hoặc qualifier/generic filtering |
| `@Primary` giúp inject một bean nhưng list vẫn có nhiều | Primary chỉ ưu tiên **single-valued resolution**, không xóa candidates khỏi collection |
| Injection theo implementation class thất bại | `@Bean` return type quá rộng, bean proxy chỉ lộ interface hoặc metadata không đủ |
| `getBeanNamesForType(SmsChannel.class)` không thấy bean do factory tạo | Kiểm tra declared return type, `FactoryBean`/type prediction, lazy init |
| Generic injection `Vault<String>` chọn sai/không tìm thấy | Kiểm chữ ký return type, generic metadata, `ResolvableType` |
| Bean excluded khỏi autowiring vẫn truy cập được bằng tên | `autowireCandidate=false` chỉ loại candidate chứ không xóa bean |
| `isAssignableFrom` trả ngược dự đoán | Nhầm hướng Target/Source: `Target.isAssignableFrom(Source)` |
| Lấy theo type có cảnh báo type không duy nhất | Thử `getBeanNamesForType` để quan sát candidate names rồi dùng primary/qualifier |
| Có bean nhưng class bị proxy | Kiểm interface/class proxy, đừng so runtime class bằng equality |
| `getBeansOfType` làm ứng dụng khởi tạo sớm | API trả **instances**, có thể tạo beans/lifecycle callbacks |
| Bean tên giống field nhưng type không khớp | Name không làm Java reference trở nên assignable; sửa hợp đồng type |
| Không thấy bean từ parent context | Tìm hiểu context hierarchy và `BeanFactoryUtils` |
| Khó đọc log lỗi autowiring | Xem `UnsatisfiedDependencyException`, `NoSuchBeanDefinitionException`, `NoUniqueBeanDefinitionException` và cause chain |

**Quy trình debug đề xuất:**
1. Xác nhận đúng context/bean registrations và `@Bean` declared return types.
2. Kiểm thử `getBeanNamesForType(RequiredType.class)`; sort kết quả nếu cần snapshot.
3. Nếu đã có type matches nhưng injection thiếu, kiểm metadata **`autowireCandidate`**, qualifier, generic type, primary/fallback và visibility.
4. Nếu có nhiều candidates, xác định injection đang cần một bean hay collection/map.
5. Nếu xảy ra ambiguity, dùng `@Primary` hoặc qualifier **có chủ đích**, không chỉ đổi tên field để dựa vào fallback.
6. Với factory/proxy/generics, sử dụng `ResolvableType`, `isTypeMatch`, API bean factory và debugger khi cần.
7. Test riêng hành vi Java class, container wiring và integration boundaries; đừng trông đợi type matching tự chứng minh behavior nghiệp vụ.

## 13. Bài tập tự kiểm chứng và checklist

Làm từng thử nghiệm trên bản sao và khôi phục code trước khi so expected stdout:

1. **Thêm subclass:** tạo `PushChannel extends BaseChannel`, đăng ký `@Bean PushChannel`. Dự đoán type lists và injected list/map thay đổi thế nào.
2. **Đổi @Primary:** chuyển primary từ Email sang SMS; dự đoán `wire.primary=SMS` nhưng `wire.list` vẫn chứa cả hai.
3. **Bỏ @Primary:** ở `AppConfig`, hai candidates hợp lệ `Channel` sẽ trở nên mơ hồ tại factory method `consumer(Channel selected,...)`; context có thể fail sớm.
4. **Đổi autowireCandidate:** bỏ `autowireCandidate=false` của Local. Dự đoán discovery giữ nguyên ba beans nhưng injected list/map giờ có thêm LOCAL.
5. **Lấy Local bằng tên:** thực hiện `context.getBean("localChannel", Channel.class)` và giải thích vì sao nó vẫn hoạt động khi Local excluded from autowiring.
6. **Đổi type injection:** thay `Channel` bằng `SmsChannel` trong một method parameter, giải thích vì sao chỉ SMS thỏa.
7. **Thử interface riêng:** inject `Auditable`, quan sát chỉ `SmsChannel` implement interface ấy.
8. **Đổi kiểu trả về `@Bean`:** sửa `SmsChannel sms()` thành `Channel sms()`, so kết quả type prediction trước khi khởi tạo.
9. **Thêm qualifier:** sử dụng `@Qualifier("smsChannel")` trên `Channel` parameter của consumer thay vì dựa vào primary; xác nhận cách lọc.
10. **Generic mismatch:** đổi `Vault<String>` thành `Vault<Integer>` cho parameter `stringVault`, nghiên cứu vì sao generic metadata dẫn tới thay candidate hoặc fail vì không tương thích mong đợi.
11. **Thêm Vault<Long>:** đăng ký thêm một bean `Vault<Long>`; kiểm `getBeanNamesForType(ResolvableType.forClassWithGenerics(...))` theo từng type argument.
12. **Thử prototype:** đặt `@Scope("prototype")` trên một channel và so `getBeanNamesForType` với `getBean` lặp lại; type membership không hàm ý identity singleton.
13. **No unique bean:** chạy `AmbiguousConfig`, giải thích vì sao `getBean(Channel.class)` không chọn “first” dù thứ tự đăng ký rõ ràng.
14. **Tách self-reference:** thử autowire chính bean đang tạo và xem vì sao self-injection là fallback, không phải lựa chọn primary thông thường.
15. **Java standalone:** sửa `JavaAssignableTypes` cho `EmailSender` implement một interface mới, dùng `Class.isAssignableFrom` kiểm tra loại nào có thể gán.
16. **FactoryBean:** tạo thử một `FactoryBean<Channel>`, phân biệt lookup bằng bean name và `&beanName`; đọc `getObjectType()` trước khi nhận định type-query.

### Checklist

- [ ] Hiểu `Target.class.isAssignableFrom(Source.class)` theo **đúng chiều**.
- [ ] Biết bean implementation/subclass có thể match interface/superclass injection point.
- [ ] Không nhầm `getBeanNamesForType` với tập candidates đã qua autowiring filters.
- [ ] Biết `autowireCandidate=false` vẫn cho phép lookup theo tên hoặc type discovery.
- [ ] Biết `@Primary` giải quyết single-valued injection, không xóa bean khỏi list/map.
- [ ] Biết `@Qualifier`, generic type và `@Fallback` có thể ảnh hưởng lựa chọn.
- [ ] Đọc đúng `getBeanNamesForType`, `getBeansOfType`, `isTypeMatch`, `getBean(Class)`.
- [ ] Phân biệt raw generic type với `ResolvableType` và declared `@Bean` return type.
- [ ] Dự đoán chính xác 22 dòng stdout Spring Maven và 4 dòng Java thuần.
- [ ] Biết các giới hạn FactoryBean, lazy/scope, proxy và parent contexts.

## 14. Kết luận và tài liệu chính thức

**Kết luận:** *Assignable type* là **bước xác định tập beans có kiểu phù hợp**, nhưng lựa chọn ứng viên trong Spring còn phụ thuộc **autowire eligibility, qualifiers, generics, primary/fallback, scope và bối cảnh injection point**. Một bean có type khớp **không mặc nhiên** được inject; ngược lại, nhiều type matches có thể được Spring resolve thành đúng một bean hoặc một collection có chủ đích.

Nguồn tra cứu:
- [Spring Framework — Using @Autowired](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html)
- [Spring Framework — Autowiring Collaborators](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-autowire.html)
- [Spring Framework — Fine-tuning Annotation-based Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html)
- [Spring Framework — Primary and Fallback](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-primary.html)
- [Spring Framework — Generics as Autowiring Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/generics-as-qualifiers.html)
- [Spring API — `ListableBeanFactory`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/ListableBeanFactory.html)
- [Spring API — `ResolvableType`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/ResolvableType.html)
- [Java 17 — `Class.isAssignableFrom`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#isAssignableFrom(java.lang.Class))

**Ghi nhớ để áp dụng:** Khi debug một dependency chưa được inject, hãy đi theo thứ tự **Java type compatibility → Spring type discovery → autowire filtering → chọn single candidate hoặc nhiều candidates → instance lifecycle**. Cách suy luận này chính xác hơn câu nói “Spring thấy cùng interface là tự tiêm vào”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="candidate-selection-002">Giải quyết một phụ thuộc có nhiều bean cùng kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Giải quyết một phụ thuộc có nhiều bean cùng kiểu — Spring Framework Core Container

## 1. Vấn đề: Spring thấy nhiều bean hợp kiểu, nhưng dependency chỉ nhận một

Giả sử ứng dụng có interface `MessageSender` và hai Spring beans:

~~~java
@Bean("emailSender")
MessageSender emailSender() {
    return new EmailSender();
}

@Bean("smsSender")
MessageSender smsSender() {
    return new SmsSender();
}
~~~

**Snippet minh họa:** chưa có interface, implementation và cấu hình đầy đủ; ví dụ Java 17 chạy được nằm ở mục 7.

Một service cần **chính một** sender:

~~~java
public AlertService(MessageSender sender) {
    this.sender = java.util.Objects.requireNonNull(sender);
}
~~~

Spring tìm những bean tương thích kiểu `MessageSender`, nhưng hiện có **hai** ứng viên: `emailSender` và `smsSender`. Nếu không có thêm chỉ dẫn đủ để chọn một, container **không được đoán tùy tiện**. Đối với một dependency đơn trị, Spring thường ném `NoUniqueBeanDefinitionException` (đôi khi được bọc trong `UnsatisfiedDependencyException`/`BeanCreationException` khi xảy ra trong quá trình tạo service).

**Câu hỏi cần giải:** “Trong nhiều bean cùng kiểu đã đăng ký, bean nào được đưa vào điểm injection này?”

Sau bài, bạn có thể:

1. Phân biệt **bean name**, **Java type/interface**, **bean candidate**, **injection point** và bean instance.
2. Nhận diện khác nhau giữa **thiếu ứng viên** và **nhiều ứng viên**.
3. Chọn một implementation mặc định qua `@Primary`; hiểu tình huống **hai primary** vẫn mơ hồ.
4. Dùng `@Qualifier` để yêu cầu một implementation phù hợp cho **một injection point**; hiểu qualifier là *bộ lọc theo kiểu*, không phải tham chiếu String bất chấp type.
5. Sử dụng `@Fallback` của **Spring Framework 6.2** để hạ ưu tiên một bean; hiểu khi nào fallback vẫn được chọn.
6. Phân biệt **injection một bean** với **List/Map/array injection nhiều beans**, bao gồm vai trò của `@Order`.
7. Hiểu name matching, `-parameters`, `@Resource`, generic types và cấu hình `autowireCandidate`/`defaultCandidate` khi phù hợp.
8. Chạy demo độc lập Java 17+ với Spring Framework 6.2.19, Maven và lệnh `javac`/`java`, đối chiếu stdout xác định.
9. Chẩn đoán và sửa ambiguity mà không che lỗi bằng các lựa chọn ngẫu nhiên, `@Autowired(required=false)` hoặc service locator.

**Phạm vi:** cơ chế *chọn bean ứng viên* trong Spring Core Container. Không cần Spring Boot, web, database, email/SMS thực, framework test hay một bộ DI bên ngoài.

## 2. Phân biệt ba kết quả khi resolve một dependency

Cho một điểm injection yêu cầu `MessageSender`:

| Số ứng viên phù hợp sau các quy tắc chọn | Kết quả điển hình |
| --- | --- |
| Không có bean phù hợp | Thiếu dependency; có thể phát sinh `NoSuchBeanDefinitionException` hoặc wrapper |
| Có một bean phù hợp | Spring inject bean object đó |
| Có nhiều bean phù hợp, không có lựa chọn duy nhất | Ambiguity; thường `NoUniqueBeanDefinitionException` |
| Nhiều bean cùng type, nhưng **một @Primary** hợp lệ | Spring chọn primary cho injection đơn trị |
| Nhiều bean cùng type, nhưng **@Qualifier** thu hẹp đúng một | Spring chọn ứng viên khớp qualifier |
| Nhiều bean, có **một non-fallback** và các `@Fallback` | Bean non-fallback được ưu tiên theo quy tắc Spring 6.2 |
| Điểm injection là `List<MessageSender>` | Spring có thể cung cấp **nhiều** bean, không buộc phải chọn một |

**Đặc biệt:** nhiều bean cùng kiểu **không phải lỗi registry**. Hai bean names khác nhau vẫn hợp lệ. Lỗi chỉ xuất hiện khi **một consumer muốn đúng một instance** nhưng container không chọn được duy nhất (hoặc khi lookup bằng type không đủ rõ).

Hai bean definitions cùng **bean name** lại là chuyện khác — chính sách đăng ký và ghi đè definition, không phải trọng tâm giải quyết nhiều candidate theo type.

## 3. Spring chọn ứng viên theo những lớp thông tin nào?

Một cách hiểu thực dụng về quá trình tìm một collaborator:

~~~mermaid
flowchart TD
    I["Injection point: MessageSender"] --> T["Lọc bean phù hợp kiểu"]
    T --> N{"Số candidate?"}
    N -->|"0"| X["Missing dependency"]
    N -->|"1"| OK["Chọn bean duy nhất"]
    N -->|"Nhiều"| Q["Xét qualifier, primary, fallback và quy tắc liên quan"]
    Q --> U{"Có duy nhất ứng viên tốt nhất?"}
    U -->|"Có"| OK
    U -->|"Không"| E["Ambiguous dependency"]
~~~

Đây là **mô hình tư duy**, không mô tả từng bước nội bộ/độ ưu tiên tuyệt đối của mọi nhánh thuật toán Spring. Thực tế Spring dùng `DependencyDescriptor`, bean definitions, type metadata và các quy tắc resolution khác nhau, bao gồm `@Qualifier`, `@Primary`, `@Fallback`, matching tên parameter/field, `@Priority` và trạng thái candidate.

Để áp dụng chính xác, hãy hỏi theo thứ tự:

1. **Type tại injection point là gì?** Interface `MessageSender` hay implementation cụ thể `EmailSender`? Generic arguments có ý nghĩa không?
2. **Những bean nào đã đăng ký?** Bean names, factory methods, scopes và return types có đủ rõ?
3. **Bean nào đủ điều kiện autowiring?** Có `autowireCandidate=false` hoặc metadata hạn chế không?
4. **Injection point có qualifier không?** Có chỉ rõ ý định “EMAIL”, “SMS”, “fast”, “main” hay một tên bean cụ thể không?
5. **Trong những candidate còn lại, có primary/fallback policy không?** Có một lựa chọn độc nhất không?
6. **Có cơ chế phù hợp theo tên injection point không?** Tên parameter có được giữ lúc compile không?
7. **Consumer cần một hay nhiều?** Nếu cần tất cả, injection collection thường phù hợp hơn việc chọn tùy ý.

**Không dùng thứ tự khai báo method trong source làm hợp đồng chọn một bean.** Đăng ký sau, tên đứng đầu bảng chữ cái hoặc component scan đi trước không phải cơ chế nghiệp vụ thay thế cho qualifier/primary rõ ràng.

## 4. @Primary: chọn một bean mặc định khi có nhiều bean cùng kiểu

`@Primary` đánh dấu ứng viên được ưu tiên cho **dependency đơn trị** nếu bean đó vẫn thỏa type, qualifier và các điều kiện autowire. Ví dụ có `emailSender` và `smsSender` cùng type `MessageSender`; đánh dấu EMAIL là primary giúp một tham số `MessageSender sender` nhận EMAIL mà không ép mọi consumer phải ghi qualifier.

Một `@Qualifier("smsSender")` hợp lệ tại injection point vẫn giới hạn lựa chọn sang SMS. Hai primary cùng đáp ứng một dependency không tạo ra mặc định duy nhất. Primary cũng không loại SMS khỏi `List<MessageSender>` hoặc `Map<String, MessageSender>`. Mục 7 kiểm tra các trường hợp này; bài riêng về `@Primary` giải thích sâu về bean definition và các edge case.

## 5. @Qualifier: thu hẹp candidate tại đúng injection point

Với hai bean `MessageSender`, một consumer cần SMS có thể khai `@Qualifier("smsSender") MessageSender sender`. Qualifier là **bộ lọc trong tập ứng viên hợp kiểu**; giá trị trùng bean name có thể được Spring dùng để match, nhưng đừng hiểu nó là thao tác lookup theo tên bất chấp type. Một qualifier có thể mô tả vai trò như `transactional` hoặc `regional` và có thể gắn cho nhiều beans.

Vì vậy `@Qualifier("fast") List<MessageSender>` có thể nhận nhiều beans cùng nhãn, còn `@Qualifier("fast") MessageSender` vẫn mơ hồ khi còn hai beans ngang nhau. Trong demo mục 7, qualifier chỉ định một SMS/EMAIL cụ thể; bài sau về `@Qualifier` và custom qualifier đi sâu vào collection, annotation metadata và ngữ nghĩa nghiệp vụ.

## 6. @Fallback: hạ ưu tiên một bean trong Spring Framework 6.2

Từ **Spring Framework 6.2**, `org.springframework.context.annotation.Fallback` đánh dấu một bean là phương án ưu tiên thấp khi chọn dependency **đơn trị**. Nếu có đúng một bean thường và những bean còn lại là fallback, Spring có thể chọn bean thường. Nếu context chỉ có một bean fallback hợp lệ, bean đó vẫn được inject.

Fallback **không** xóa bean khỏi container, không ngăn nó xuất hiện trong collection, không trì hoãn khởi tạo và không thực hiện failover khi lời gọi nghiệp vụ lỗi. Nếu có nhiều bean thường hoặc nhiều bean fallback cùng đáp ứng một injection point, vẫn cần rule khác để phân định. Context `FallbackConfig` và `OnlyFallbackConfig` ở mục 7 chứng minh hai nhánh; bài `@Fallback` riêng trình bày rõ giới hạn phiên bản và các trường hợp khó.

## 7. Project Java 17+ runnable: chứng minh năm cách xử lý

Dùng **JDK 17+**, Maven và **Spring Framework 6.2.19**. Version được ghim để ví dụ tái lập, không tuyên bố mới nhất. Chạy thuần Spring Core Container, không Spring Boot/MVC, không email/SMS thật.

Demo dùng nhiều contexts độc lập để mỗi tình huống chỉ mang đúng các bean definitions cần kiểm tra:

1. **Plain:** hai Sender cùng type, không có lựa chọn đơn trị; lookup theo type bị từ chối.
2. **Qualified + collection:** chỉ định `@Qualifier("emailSender")` lấy EMAIL, còn `List/Map` nhận cả EMAIL và SMS. `@Order` sắp thứ tự List.
3. **Primary:** EMAIL là `@Primary`, nên lookup không qualifier lấy EMAIL; nhưng qualifier SMS vẫn chọn SMS.
4. **Fallback:** SMS là `@Fallback`, EMAIL là bean thông thường, nên email được ưu tiên.
5. **Only fallback:** chỉ còn một SMS fallback, nó vẫn là bean duy nhất hợp lệ.
6. **Two primaries:** cả email và SMS đều `@Primary`, nên không có một lựa chọn primary duy nhất.

### 7.1. Cấu trúc thư mục

~~~text
candidate-resolution-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── CandidateResolutionDemo.java
~~~

### 7.2. File pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>candidate-resolution-demo</artifactId>
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
                    <mainClass>demo.CandidateResolutionDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Spring dependencies transitively cần thiết do Maven resolve. Với `javac`/`java` trực tiếp, cần đủ JAR của Spring trên classpath.

### 7.3. File src/main/java/demo/CandidateResolutionDemo.java

~~~java
package demo;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;

public final class CandidateResolutionDemo {
    private CandidateResolutionDemo() {}

    public interface Sender {
        String id();
    }

    public record NamedSender(String id) implements Sender {
        public NamedSender {
            Objects.requireNonNull(id);
        }
    }

    public static final class QualifiedClient {
        private final Sender sender;

        public QualifiedClient(Sender sender) {
            this.sender = Objects.requireNonNull(sender);
        }

        public String selected() {
            return sender.id();
        }
    }

    public static final class CollectionClient {
        private final List<Sender> all;
        private final Map<String, Sender> byName;

        public CollectionClient(
                List<Sender> all, Map<String, Sender> byName) {
            this.all = List.copyOf(all);
            this.byName = Map.copyOf(byName);
        }

        public String orderedIds() {
            return all.stream().map(Sender::id).toList().toString();
        }

        public String sortedBeanNames() {
            return byName.keySet().stream().sorted()
                    .toList().toString();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class PairConfig {
        @Bean("emailSender")
        @Order(2)
        public Sender emailSender() {
            return new NamedSender("EMAIL");
        }

        @Bean("smsSender")
        @Order(1)
        public Sender smsSender() {
            return new NamedSender("SMS");
        }

        @Bean
        public QualifiedClient qualifiedClient(
                @Qualifier("emailSender") Sender sender) {
            return new QualifiedClient(sender);
        }

        @Bean
        public CollectionClient collectionClient(
                List<Sender> senders, Map<String, Sender> sendersByName) {
            return new CollectionClient(senders, sendersByName);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class PrimaryConfig {
        @Bean("emailSender")
        @Primary
        public Sender emailSender() {
            return new NamedSender("EMAIL");
        }

        @Bean("smsSender")
        public Sender smsSender() {
            return new NamedSender("SMS");
        }

        @Bean
        public QualifiedClient qualifiedClient(
                @Qualifier("smsSender") Sender sender) {
            return new QualifiedClient(sender);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class FallbackConfig {
        @Bean("emailSender")
        public Sender emailSender() {
            return new NamedSender("EMAIL");
        }

        @Bean("smsSender")
        @Fallback
        public Sender smsSender() {
            return new NamedSender("SMS");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class OnlyFallbackConfig {
        @Bean("smsSender")
        @Fallback
        public Sender smsSender() {
            return new NamedSender("SMS");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class TwoPrimaryConfig {
        @Bean("emailSender")
        @Primary
        public Sender emailSender() {
            return new NamedSender("EMAIL");
        }

        @Bean("smsSender")
        @Primary
        public Sender smsSender() {
            return new NamedSender("SMS");
        }
    }

    private static void demonstratePair() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(PairConfig.class)) {
            try {
                context.getBean(Sender.class);
                System.out.println("plain=UNEXPECTED");
            } catch (NoUniqueBeanDefinitionException expected) {
                System.out.println("plain=AMBIGUOUS");
            }

            QualifiedClient qualified =
                    context.getBean(QualifiedClient.class);
            CollectionClient all =
                    context.getBean(CollectionClient.class);

            System.out.println("qualified=" + qualified.selected());
            System.out.println("ordered=" + all.orderedIds());
            System.out.println("beanNames=" + all.sortedBeanNames());
        }
    }

    private static void demonstratePrimary() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(PrimaryConfig.class)) {
            Sender selected = context.getBean(Sender.class);
            QualifiedClient sms = context.getBean(QualifiedClient.class);

            System.out.println("primary=" + selected.id());
            System.out.println("qualifierOverPrimary=" + sms.selected());
        }
    }

    private static void demonstrateFallback() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(FallbackConfig.class)) {
            System.out.println("fallbackPreferred=" +
                    context.getBean(Sender.class).id());
        }

        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(
                        OnlyFallbackConfig.class)) {
            System.out.println("onlyFallback=" +
                    context.getBean(Sender.class).id());
        }
    }

    private static void demonstrateTwoPrimaries() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(TwoPrimaryConfig.class)) {
            try {
                context.getBean(Sender.class);
                System.out.println("twoPrimaries=UNEXPECTED");
            } catch (NoUniqueBeanDefinitionException expected) {
                System.out.println("twoPrimaries=AMBIGUOUS");
            }
        }
    }

    public static void main(String[] args) {
        demonstratePair();
        demonstratePrimary();
        demonstrateFallback();
        demonstrateTwoPrimaries();
    }
}
~~~

**Phân tích khả năng chạy và phạm vi của demo:**

- Chỉ có một public top-level class `CandidateResolutionDemo` khớp đường dẫn/package; records, configs và consumers là nested types.
- `Sender` là interface, `NamedSender` là record Java 17 implement interface và giữ ID cố định; không dùng network thực.
- Mỗi `@Configuration` class tạo **context độc lập**. Điều này cô lập các chính sách thay vì đưa năm nhóm cùng tên vào một registry rồi bị ghi đè.
- Các `@Bean` methods có return type `Sender`, đủ để Spring nhìn thấy hai ứng viên thuộc cùng type; chúng được đặt tên khác nhau và không trùng trong một config.
- `PairConfig` đánh dấu `smsSender` `@Order(1)`, `emailSender` `@Order(2)` để List injection có thứ tự `[SMS, EMAIL]`. Đây là sắp xếp collection, **không** chọn một Sender duy nhất.
- `Map<String, Sender>` nhận các bean theo tên; khi in, ta **sort tên** để kết quả không phụ thuộc thứ tự Map iteration.
- `QualifiedClient` chỉ yêu cầu một Sender cụ thể qua `@Qualifier` đặt trên `@Bean` factory-method parameter; các `@Bean` methods không gọi trực tiếp nhau.
- `PrimaryConfig` có một primary EMAIL; explicit qualifier SMS ở consumer khác vẫn yêu cầu SMS.
- `FallbackConfig` có một bean thường EMAIL và một fallback SMS; `OnlyFallbackConfig` chỉ có SMS.
- `TwoPrimaryConfig` có hai primary cùng interface; type-only lookup thiếu unique primary.
- `AnnotationConfigApplicationContext` đóng theo `try-with-resources`. Những exception được dự kiến xảy ra ở **lookup kiểu đơn trị** và được bắt có chủ đích; không in stack trace hay nhét exception message tùy phiên bản vào stdout.
- Không sử dụng đồng hồ, random, dữ liệu người dùng hay mạng; các text đầu ra đều xác định từ mã demo.

### 7.4. Chạy Maven và đối chiếu stdout

Tại thư mục `candidate-resolution-demo`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

**Đúng chín dòng stdout do chương trình Java in khi chạy thành công:**

~~~text
plain=AMBIGUOUS
qualified=EMAIL
ordered=[SMS, EMAIL]
beanNames=[emailSender, smsSender]
primary=EMAIL
qualifierOverPrimary=SMS
fallbackPreferred=EMAIL
onlyFallback=SMS
twoPrimaries=AMBIGUOUS
~~~

Những dòng phiên bản JDK/Maven, log của Maven/Spring (nếu có) không thuộc chín dòng do `main` in trong chương trình. Không lock test vào nội dung đầy đủ của exception vì exception detail có thể thay đổi giữa các Spring versions.

### 7.5. Chạy trực tiếp javac/java trên Linux/macOS và Windows

Trước tiên tạo danh sách JAR dependencies bằng Maven:

~~~bash
mvn -q dependency:build-classpath -Dmdep.outputFile=classpath.txt
~~~

**Linux/macOS — bash/zsh:**

~~~bash
mkdir -p out
javac --release 17 -encoding UTF-8 \
  -cp "$(cat classpath.txt)" \
  -d out src/main/java/demo/CandidateResolutionDemo.java
java -cp "out:$(cat classpath.txt)" demo.CandidateResolutionDemo
~~~

**Windows — PowerShell:**

~~~powershell
$deps = (Get-Content -Raw classpath.txt).Trim()
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -encoding UTF-8 -cp "$deps" -d out src/main/java/demo/CandidateResolutionDemo.java
java -cp "out;$deps" demo.CandidateResolutionDemo
~~~

Classpath separator là `:` trên Unix và `;` trên Windows. Cả hai cách chạy cùng phương thức `main`, cho cùng output ứng dụng. Nếu `javac` báo không tìm thấy `org.springframework.*`, cần kiểm tra dependency/classpath trước khi sửa bean resolution.

## 8. Vì sao List/Map injection không gặp lỗi “nhiều bean”?

Có sự khác biệt về **contract** của injection point:

~~~java
public NotificationBatch(
        List<MessageSender> allSenders,
        Map<String, MessageSender> byName) {
    // allSenders: những bean phù hợp kiểu MessageSender
    // byName: key là bean name; value là bean object
}
~~~

**Snippet**, không phải full Java file. Một constructor nhận `MessageSender` đơn trị cần chọn một, nhưng List/Map có nhiệm vụ tập hợp các beans cùng kiểu. Vì vậy tồn tại ba strategies cùng type không tự là lỗi cho collection injection.

Spring hỗ trợ array, collection và `Map<String,T>` injection. Với map, **key type nên là String** để đại diện bean names, và value type phải giữ thông tin phù hợp. Với `List<T>`, thứ tự có thể bị ảnh hưởng bởi `@Order`/`Ordered` và sau đó bởi registration order khi không có policy sắp xếp cụ thể.

**Không suy rằng @Order là “chọn bean có rank nhỏ nhất”:**

- `@Order(1)` và `@Order(2)` giúp sắp phần tử trong một collection được inject.
- Nó **không tương đương** `@Primary` khi consumer chỉ muốn một `MessageSender`.
- Nó cũng **không nhất thiết điều khiển thứ tự khởi tạo singleton**. Trình tự startup phụ thuộc dependency graph, `@DependsOn` và các cơ chế bootstrap.
- Nếu nghiệp vụ cần “thử mọi sender theo thứ tự”, `List<MessageSender>` với order rõ có ích; nếu cần “chỉ một sender”, dùng qualifier/primary/fallback theo hợp đồng.

Nếu collection chỉ nên chứa một nhóm, có thể dùng qualifier để **lọc cả collection**. Ví dụ hai beans đánh dấu `@Qualifier("transactional")` có thể đi vào `@Qualifier("transactional") List<MessageSender>`; qualifier không buộc chỉ ra một bean duy nhất.

### Map là cấu trúc hữu ích cho strategy lookup, nhưng cần kiểm soát key

Ví dụ một registry nghiệp vụ:

~~~java
private final Map<String, MessageSender> senders;

public StrategySelector(Map<String, MessageSender> senders) {
    this.senders = Map.copyOf(senders);
}

public MessageSender forChannel(String beanName) {
    MessageSender sender = senders.get(beanName);
    if (sender == null) {
        throw new IllegalArgumentException("Unknown channel: " + beanName);
    }
    return sender;
}
~~~

**Đoạn này là method snippets thuộc class cần import/khai báo interface phù hợp**, không phải project runnable thứ hai. Nó cho phép lựa chọn *ở runtime* theo một input mà application đã validate. Nhưng tránh để bean name (chi tiết wiring của Spring) trở thành hợp đồng API công khai một cách tình cờ; nên ánh xạ domain channel sang bean strategy hoặc dùng enum/typed key khi thích hợp.

## 9. Parameter name matching: vì sao đôi khi không có @Qualifier mà vẫn chọn được?

Trong một số trường hợp, Spring có thể dùng **tên field/parameter** để chọn bean có tên hoặc alias phù hợp sau khi xem xét những quy tắc lựa chọn khác. Ví dụ `MessageSender emailSender` có thể match bean tên `emailSender`, nhưng tên tham số Java chỉ được phản ánh đáng tin cậy nếu biên dịch với `-parameters` (đặc biệt khi dùng Spring Framework 6.1+).

Name matching là tiện ích có điều kiện, không phải lời bảo đảm rằng Spring luôn inject theo tên trước type hoặc qualifier. Với hợp đồng nghiệp vụ cần một implementation ổn định, qualifier tường minh dễ kiểm chứng hơn. Bài riêng về `-parameters` giải thích bytecode metadata, Maven và cách tự thử với/không với cờ này.

## 10. Generic type và metadata cũng ảnh hưởng ứng viên

Khi có `Store<String>` và `Store<Long>`, một injection point `Store<String>` có thể dùng metadata generic để lọc ứng viên trước khi xét `@Primary`, qualifier hoặc tên. Đây là phân biệt bằng hợp đồng kiểu, khác việc gắn nhãn nghiệp vụ. Nếu có hai bean cùng `Store<String>`, ambiguity vẫn có thể xảy ra; nếu method khai raw `Store`, Spring có thể thiếu thông tin để phân biệt. Phần chuyên sâu về generic types trình bày giới hạn erasure và collection injection.

## 11. Những cách xử lý khác và điểm giới hạn

| Cơ chế | Khi phù hợp | Giới hạn |
| --- | --- | --- |
| `@Primary` | Có một implementation mặc định cho phần lớn consumers | Không giải quyết hai primaries ngang nhau |
| `@Qualifier` | Mỗi consumer cần implementation theo vai trò rõ | Qualifier cần ứng viên phù hợp type; có thể còn nhiều |
| `@Fallback` | Một implementation là fallback trong Spring 6.2+ | Không thay thế policy bắt buộc/không loại bean |
| `List<T>` / `Map<String,T>` | Cần tất cả strategies hoặc lookup động | Consumer phải tự quyết định thứ tự/chọn lúc dùng |
| Tên parameter/field | Tên có ý nghĩa và metadata đáng tin | Dễ thay đổi khi refactor; cần `-parameters` với constructor |
| `@Resource(name=...)` | Cần name-oriented field/setter resolution | Semantics khác type-driven constructor qualifier |
| Generic type | Interface specialization thể hiện ý định hợp đồng | Cần metadata generics đủ cụ thể |
| `ObjectProvider<T>` | Lazy/optional/stream access có chủ đích | `getObject()` đơn trị vẫn có thể gặp ambiguity |
| `@Bean(defaultCandidate=false)` | Bean chỉ nên xuất hiện khi injection có qualifier | Feature 6.2, không phải `@Fallback` |
| `@Bean(autowireCandidate=false)` | Exclude khỏi type-based injection | Không có nghĩa unregister bean hay cấm explicit lookup |
| `@Profile` / điều kiện đăng ký | Mỗi môi trường chỉ đăng ký implementation phù hợp | Cần test cấu hình profile/condition ở môi trường thật |

### Profile và điều kiện có thể giải quyết vấn đề từ gốc

Nếu ứng dụng **không bao giờ cần cả hai implementations trong cùng một môi trường**, một cách khác là làm cho đúng **một definition được đăng ký** theo profile/condition, thay vì luôn đăng ký cả hai rồi chọn qua primary.

Ví dụ conceptual:

~~~java
@Bean
@Profile("dev")
MessageSender devSender() { return new ConsoleSender(); }

@Bean
@Profile("prod")
MessageSender prodSender() { return new RemoteSender(); }
~~~

**Snippet minh họa**, thiếu interfaces/implementations và config. Phải chắc profile phù hợp đang active, nếu không có thể thiếu candidate; nếu nhiều profiles active, có thể lại có nhiều candidate. Đây là vấn đề **tập bean được đăng ký**, khác việc chọn giữa những candidate đã có.

Không dùng `@Conditional` hoặc `@Profile` để che những implementation nghiệp vụ thật sự cần tồn tại đồng thời.

## 12. Chẩn đoán NoUniqueBeanDefinitionException đúng chỗ

Một stack trace có thể có các lớp exception:

~~~text
UnsatisfiedDependencyException
  caused by: NoUniqueBeanDefinitionException:
  expected single matching bean but found 2:
  emailSender, smsSender
~~~

**Đây là ví dụ cấu trúc lỗi, không phải stack trace nguyên văn**. Class/wording của exception wrapper tùy đường tạo bean và phiên bản Spring. Hãy đọc tới bean name, type, candidate count và injection point cụ thể.

Một quy trình đáng tin:

1. **Tìm injection point:** constructor parameter, field, `@Bean` factory method hoặc `context.getBean(Type.class)`?
2. **Xác minh type:** `MessageSender` hay subtype khác, có generic arguments, proxies hoặc factory declared return type ảnh hưởng không?
3. **Liệt kê candidate beans:** dùng `context.getBeanNamesForType(MessageSender.class)` khi context đã chạy; xem definitions của các tên liên quan.
4. **Kiểm tra qualifier và primary:** annotation có ở injection point/bean definition thật không? Có hai primary cùng type?
5. **Kiểm tra fallback, candidate flags, profile:** có bean bị loại khỏi autowiring hoặc bean chỉ dùng theo qualifier không?
6. **Kiểm tra tên parameter/field:** có vô tình khớp với bean name không? Có compile `-parameters` nếu dựa vào điều đó?
7. **Đọc lỗi gốc:** nếu missing bean là do registration không chạy hoặc dependency lỗi, đừng đánh đồng với ambiguity.
8. **Chọn giải pháp tương ứng với nghiệp vụ:** một default → primary; consumer-specific → qualifier; all strategies → List/Map; environment-specific → profile; fallback → @Fallback; thực sự cần đúng một definition → sửa registration.
9. **Viết smoke test:** test context khởi động, kiểm tra identity/type/behavior của bean được inject, không chỉ kiểm tra “không ném exception”.

### API kiểm tra những bean có mặt

~~~java
String[] names = context.getBeanNamesForType(MessageSender.class);
for (String name : names) {
    System.out.println(name);
}
~~~

**Snippet**: cần nằm trong scope có `ApplicationContext context` và `MessageSender` type. Thứ tự array có thể theo registration, nên nếu đưa vào assertion yêu cầu deterministic list hãy sort names trước. Sự có mặt của một bean trong registry **không tự chứng minh nó là candidate hợp lệ cho mọi injection point**; qualifier flags và scopes còn ảnh hưởng.

## 13. Những lỗi thiết kế thường gặp và cách sửa

| Triệu chứng | Hiểu nhầm thường gặp | Hướng sửa |
| --- | --- | --- |
| `NoUniqueBeanDefinitionException` | “Hai bean cùng interface là bất hợp pháp” | Hai bean được phép tồn tại; hãy xác định contract consumer cần một hay nhiều |
| Đánh dấu cả hai beans `@Primary` | “Càng nhiều primary càng chắc chắn chọn được” | Chọn một default rõ hoặc dùng qualifier |
| Thêm `@Order(1)` vào một bean | Nghĩ Spring chọn bean có order nhỏ nhất cho single injection | Dùng primary/qualifier; order cho collection |
| Bỏ qualifier khi refactor | Spring bắt đầu chọn theo tên parameter hoặc báo ambiguity | Khôi phục yêu cầu chọn có chủ đích |
| `@Qualifier` chứa bean name không đúng type | Mong String bean name bất chấp type | Fix declared types/bean definitions |
| `@Fallback` mà vẫn nhiều normal beans | Tưởng fallback giải quyết hết ambiguity | Dùng qualifier/primary hoặc giảm candidate set |
| `@Autowired(required=false)` để “fix” hai candidates | Optional không mang nghĩa chọn tùy ý một bean | Làm rõ candidate selection, đừng che lỗi |
| Đổi `@Bean` method return type quá chung | Metadata không đủ để match subtype/generic | Khai báo return type đủ cụ thể |
| Chỉ sửa ở unit test bằng fake bean | Production config vẫn ambiguous | Có integration test với config thật |
| Chạy được khi bật một profile, lỗi khi bật hai | Hai implementations cùng đăng ký | Kiểm tra profile combinations và candidate policy |
| Dùng `getBean(Class)` trong business service | Runtime lookup dễ thất bại và giấu dependency | Constructor injection vào service |
| Dựa vào tên constructor parameter tình cờ | Refactor đổi tên phá binding | Explicit qualifier khi cần ổn định |
| Đánh dấu `autowireCandidate=false` bừa bãi | Candidate biến mất khỏi type-driven injection | Chỉ exclude đúng bean không nên autowire |
| Dùng Map rồi chọn `values().iterator().next()` | Phụ thuộc thứ tự không phải business policy | Chọn theo domain key/chiến lược rõ |
| Singleton mutable strategy có race | Tưởng bean được chọn đúng là thread-safe | Bảo vệ state và thiết kế scope/concurrency |
| `javac` không thấy Spring API | Thiếu Spring JAR/classpath | Kiểm tra POM và `-cp` trước khi debug DI |

**Nguyên tắc:** lỗi ambiguity thường mang thông tin hữu ích về **một hợp đồng chưa rõ**. Không sửa bằng cách đánh dấu một bean bất kỳ `@Primary` chỉ để ứng dụng chạy, nếu nghiệp vụ không có lý do dùng nó mặc định.

## 14. Kiểm thử chiến lược chọn ứng viên

### Unit test không cần Spring

Một service có constructor nhận interface vẫn có thể test bằng fake:

~~~java
Sender fake = () -> "TEST";
QualifiedClient client = new QualifiedClient(fake);
System.out.println(client.selected()); // TEST
~~~

**Snippet** trong phạm vi truy cập các nested types của demo. Nó kiểm tra logic Java thuần và việc constructor nhận một collaborator, **không** kiểm tra chính sách Spring autowiring.

### Wiring integration test

Trong context với hai Senders, nên kiểm tra:

- Không qualifier/primary/fallback mà yêu cầu `Sender.class` đơn trị → expected ambiguity.
- `@Qualifier("emailSender")` ở một injection point → nhận đúng bean email.
- Một `@Primary` → bare type resolution chọn đúng default.
- Qualifier `smsSender` vẫn chọn SMS dù email primary.
- Một normal + một `@Fallback` → normal được ưu tiên.
- Chỉ một fallback candidate → fallback vẫn được chọn.
- Hai primary → ambiguity (trừ khi có điều kiện phân biệt khác phù hợp).
- `List<Sender>` được sort bởi `@Order`; `Map<String,Sender>` có tên chính xác.
- Bean không được đăng ký trong một profile → thiếu candidate, không phải multiple-candidate error.

**Đừng khóa test theo toàn bộ message của exception**, bởi wrapper và câu chữ có thể thay đổi. Với test framework, dùng assertions cho exception type/cause, bean identity, qualifier semantics và output/behavior.

### Lưu ý phiên bản

Bài dùng **Spring Framework 6.2.19** vì `@Fallback` và `@Bean(defaultCandidate=...)` là các khả năng liên quan đến 6.2. Nếu dự án dùng Spring Framework 6.1 hoặc cũ hơn, **không copy code có `@Fallback` mà chưa xác nhận phiên bản**. Với bản cũ, `@Primary`/`@Qualifier` và cách cấu hình candidate có thể là lựa chọn sẵn có; nâng framework phải theo chính sách tương thích của dự án.

## 15. Bài tập có đáp án

**Bài 1.** Context có `emailSender` và `smsSender` cùng implement `Sender`, không primary/qualifier. `context.getBean(Sender.class)` có chọn bean đăng ký trước không?

**Đáp án:** Không theo hợp đồng autowiring đơn trị. Khi có nhiều candidate không phân biệt, `getBean(Sender.class)` thường ném `NoUniqueBeanDefinitionException`.

**Bài 2.** Có một email `@Primary` và một SMS thường. Factory method nhận `Sender sender` không qualifier lấy gì?

**Đáp án:** EMAIL, vì có đúng một primary hợp lệ.

**Bài 3.** Consumer dùng `@Qualifier("smsSender") Sender sender` nhưng EMAIL primary. Có nhận EMAIL không?

**Đáp án:** Không trong ví dụ. Qualifier thu hẹp candidates theo yêu cầu SMS; Spring inject SMS.

**Bài 4.** Có một bean thường EMAIL và một bean `@Fallback` SMS. Điểm injection bare `Sender` nhận bean nào?

**Đáp án:** EMAIL, ứng viên non-fallback duy nhất.

**Bài 5.** Context chỉ có SMS đánh dấu `@Fallback`. Dependency `Sender` bắt buộc có thiếu không?

**Đáp án:** Không vì SMS vẫn là một candidate hợp lệ; fallback là ưu tiên thấp, không phải loại bỏ bean.

**Bài 6.** Đánh dấu `@Order(1)` cho SMS và `@Order(2)` cho EMAIL sẽ làm `getBean(Sender.class)` tự chọn SMS không?

**Đáp án:** Không. @Order có tác dụng sắp collection injection, không thay @Primary cho lookup đơn trị thông thường.

**Bài 7.** `List<Sender>` nhận gì khi có hai beans khác tên cùng type?

**Đáp án:** Thường nhận cả hai bean tương thích, sắp theo policy ordering phù hợp. Không cần chọn một.

**Bài 8.** Hai beans đều có `@Primary` mà không có lựa chọn khác. Có được một default duy nhất không?

**Đáp án:** Không. Hai primary khiến lựa chọn đơn trị mơ hồ.

**Bài 9.** @Qualifier có thể chọn bean thuộc kiểu không tương thích injection point không?

**Đáp án:** Không. Qualifier thu hẹp **trong tập bean tương thích type**, không ép String name thành object khác kiểu.

**Bài 10.** Vì sao chỉ đổi tên tham số có thể ảnh hưởng wiring nếu không qualifier?

**Đáp án:** Spring có thể dùng tên injection point làm gợi ý khi phù hợp; với Java 6.1+ việc giữ parameter name cần compile `-parameters`. Explicit qualifier phù hợp hơn khi logic lựa chọn phải bền vững qua refactor.

**Bài 11.** Nếu chỉ cần email sender tại tất cả môi trường, có nhất thiết phải tạo thêm SMS bean rồi dùng @Primary không?

**Đáp án:** Không. Nếu SMS không cần tồn tại, đừng đăng ký nó. Profile/conditional hoặc một configuration rõ ràng có thể giúp đảm bảo một bean candidate ngay từ đầu.

**Bài 12.** Khi nào nên inject `Map<String, Sender>` thay vì `Sender`?

**Đáp án:** Khi nghiệp vụ thật sự cần nhiều strategies rồi tự chọn theo một key/điều kiện được validate, không phải khi muốn né việc làm rõ một default.

## 16. Checklist trước khi đưa vào dự án

- [ ] Xác định dependency cần **một** hay **nhiều** beans.
- [ ] Kiểm tra bean names, interface/types, generics và qualifier metadata.
- [ ] Đọc đúng `NoUniqueBeanDefinitionException` thay vì chữa nhầm lỗi thiếu bean.
- [ ] Nếu có một default hợp lệ, đánh dấu **đúng một `@Primary`**.
- [ ] Nếu consumer có ý định riêng, dùng `@Qualifier` ở injection point.
- [ ] Nếu có bean dự phòng, hiểu semantics `@Fallback` của Spring 6.2.
- [ ] Không coi `@Fallback` là remove/exclude bean.
- [ ] Không dùng `@Order` để thay primary trong injection đơn trị.
- [ ] Với `List/Map`, xác nhận các thành phần và ordering theo hợp đồng.
- [ ] Tránh selection phụ thuộc accidental parameter names/registration order.
- [ ] Chỉ dùng `@Bean(defaultCandidate=false)`/`autowireCandidate=false` khi hiểu hậu quả.
- [ ] Khi dùng `@Bean`, khai báo return type đủ cụ thể cho các injection points.
- [ ] Tách unit tests logic khỏi Spring context wiring tests.
- [ ] Test nhiều profile combinations, primary/qualifier/fallback interactions.
- [ ] Chạy Java 17+ bằng Maven và `javac`/`java`, đối chiếu **9 dòng stdout**.
- [ ] Không hardcode chọn strategy “đầu tiên trong Map” mà không có business rule.
- [ ] Khi đổi version Spring, xác nhận support cho `@Fallback` và flags của `@Bean`.

**Ghi nhớ:** Bean cùng kiểu là điều tự nhiên khi triển khai strategy/interface-based design. Spring chỉ cần một cơ chế lựa chọn **rõ ràng và duy nhất** khi injection point yêu cầu một object. `@Primary` nói **“đây là mặc định”**; `@Qualifier` nói **“ở đây cần loại này”**; `@Fallback` nói **“đây là phương án ưu tiên thấp”**; `List/Map` nói **“ở đây cần nhiều triển khai”**. Chọn cơ chế phản ánh đúng nghiệp vụ sẽ làm dependency graph dễ hiểu, dễ kiểm thử và tránh sự cố wiring khi dự án mở rộng.

### Tài liệu Spring chính thức

- [Spring 6.2 — Autowiring Collaborators](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-autowire.html)
- [Spring 6.2 — Fine-tuning @Primary and @Fallback](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-primary.html)
- [Spring 6.2 — Fine-tuning Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-qualifiers.html)
- [Spring 6.2 — Using @Autowired](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html)
- [Spring 6.2 — Using Generics as Autowiring Qualifiers](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/generics-as-qualifiers.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="candidate-selection-003">Ưu tiên một ứng viên bằng @Primary</a>

<details>
<summary>Xem chi tiết</summary>

# Ưu tiên một ứng viên bằng `@Primary` trong Spring Core Container

## 1. Vấn đề: nhiều beans cùng type nhưng chỉ cần một dependency

Spring IoC Container cho phép có **nhiều bean definitions cùng tương thích một Java type**. Ví dụ `FastTransport`, `SafeTransport` và `BackupTransport` cùng implement interface `Transport`. Một service có constructor chỉ nhận `Transport` cần **một reference duy nhất**, trong khi cả ba đều có thể gán vào tham số ấy.

Nếu không có quy tắc phân giải phù hợp, việc inject một dependency duy nhất theo type có thể thất bại vì **không xác định được bean nào được chọn** (`NoUniqueBeanDefinitionException`, đôi khi được bọc trong `UnsatisfiedDependencyException`).

`@Primary`, thuộc package `org.springframework.context.annotation`, đánh dấu **một bean được ưu tiên** giữa những autowire candidates cùng đáp ứng một injection point. Khi sau khi lọc có **đúng một primary bean hợp lệ**, Spring dùng bean đó cho single-valued dependency.

~~~java
// Snippet minh họa trong một @Configuration class:
@Bean("fastTransport")
@Primary
Transport fastTransport() {
    return new FastTransport();
}

@Bean("safeTransport")
Transport safeTransport() {
    return new SafeTransport();
}
~~~

Trong tình huống thông thường, injection point nhận `Transport` sẽ lấy bean `fastTransport`, miễn cả hai beans được đăng ký và không có điều kiện lọc khác khiến primary không còn phù hợp.

**Những điều `@Primary` không làm:** không tạo bean, không đổi Java type, không đổi bean name, không thay scope, không đảm bảo thread safety, không xóa các bean khác, không làm cho mọi điểm inject đều nhận primary và cũng không thay thế một qualifier chỉ định rõ bean khác.

Mục tiêu học là hiểu **khi nào Spring cần primary, primary tham gia quá trình chọn ứng viên ra sao, giới hạn của nó và các trường hợp đối chứng**. Bài sử dụng Java 17+, Spring Framework 7.0.9 và Maven, có code hoàn chỉnh, lệnh chạy và expected stdout.

## 2. Nơi khai báo `@Primary` và phạm vi tác dụng

### Trên phương thức `@Bean`

~~~java
@Configuration(proxyBeanMethods = false)
class AppConfig {
    @Bean
    @Primary
    Transport fastTransport() {
        return new FastTransport();
    }
}
~~~

Khi dùng **Java configuration** với `@Bean`, hãy đặt `@Primary` trên **factory method đăng ký bean** muốn ưu tiên. Metadata primary thuộc **bean definition**, không phải một trạng thái mà JVM tự gắn vào mọi instance của cùng class.

### Trên component được quét

~~~java
@Component
@Primary
class FastTransport implements Transport {
    @Override
    public String id() {
        return "FAST";
    }
}
~~~

Class phải nằm trong phạm vi **component scan** hoặc được đăng ký theo một cơ chế Spring hỗ trợ. Chỉ có class `@Component @Primary` trên classpath không có nghĩa mọi context đều chứa bean ấy.

### Cấu hình XML tương đương

~~~xml
<bean id="fastTransport" class="demo.FastTransport" primary="true"/>
<bean id="safeTransport" class="demo.SafeTransport"/>
~~~

XML này là **snippet minh họa**, không phải file được dùng trong project Maven ở mục 7; trong project đó, classes được khai báo bên trong một source Java khác nên tên `demo.FastTransport` chỉ là ví dụ cho class public độc lập tương ứng. Thuộc tính XML `primary="true"` diễn đạt ý tưởng ưu tiên của cùng Spring bean-definition metadata.

**Lưu ý quan trọng về nơi đặt annotation:** Nếu một Java class mang `@Primary` nhưng bean ấy được đăng ký bằng một `@Bean` method cụ thể, đừng giả định class-level annotation luôn là metadata được Spring dùng cho definition từ method. Khai `@Primary` tại **`@Bean` method** để hợp đồng rõ ràng.

**Scope ưu tiên:** primary áp dụng với những bean definitions có thể được xét làm **candidate của một dependency đang được resolve**, không phải “bean ưu tiên toàn cục” có thể ghi đè mọi yêu cầu. Một interface khác hoặc qualifier khác có thể tạo tập candidate khác.

## 3. Quy trình Spring chọn một candidate

Khi Spring cần cấp `Transport` cho constructor/method/field, quy trình khái niệm gồm:

~~~text
Injection point yêu cầu Transport
                |
                v
   Tìm bean có type tương thích
                |
                v
  Lọc autowire candidates và metadata
     (qualifier, generic, điều kiện khác)
                |
                v
    Một candidate hay nhiều candidates?
          /                 \
      Một hợp lệ          Nhiều hợp lệ
          |                    |
          v                    v
       Chọn nó       Có đúng một @Primary?
                            /        \
                          Có         Không
                           |           |
                           v           v
                      Chọn primary   Xét luật
                                    ưu tiên khác
                                    hoặc báo mơ hồ
~~~

Đây là **sơ đồ học cơ chế**, không phải thứ tự đầy đủ từng nhánh của `DefaultListableBeanFactory`. Spring còn cân nhắc `@Fallback`, default-candidate, name matching, self references, priority và các quy tắc đặc biệt theo injection point.

Kết luận thực dụng:
- **Một candidate duy nhất:** dependency được cấp mà **không cần** `@Primary`.
- **Nhiều candidates, một primary:** primary có thể phân giải yêu cầu một bean.
- **Nhiều candidates, không primary:** có thể còn cách phân giải khác; nếu không, lỗi không duy nhất.
- **Nhiều candidates, từ hai primary trở lên:** primary không còn chọn được **một** bean; vẫn có thể báo mơ hồ.
- **Primary không khớp type/qualifier:** nó không được đưa vào tập ứng viên cuối cùng; không thể tự thắng.
- **Không có candidate:** `@Primary` trên một type khác không giải quyết được vấn đề thiếu bean.

Một primary bean thường được **pre-instantiate** khi là eager singleton trong application context, nhưng `@Primary` **không tự quyết định lúc nào bean được tạo**. Scope/lazy/factory rules vẫn có hiệu lực như bình thường.

## 4. Khác biệt quan trọng: injection một bean và injection nhiều beans

`@Primary` chủ yếu ảnh hưởng tới **single-valued dependency**, ví dụ:

~~~java
@Autowired
private Transport defaultTransport;
~~~

Hoặc constructor:

~~~java
// Snippet Java 17: constructor injection.
final class Dispatcher {
    private final Transport transport;

    Dispatcher(Transport transport) {
        this.transport = transport;
    }
}
~~~

Ngược lại, Spring có thể inject **tất cả các ứng viên phù hợp** vào:

~~~java
@Autowired
private List<Transport> transports;

@Autowired
private Map<String, Transport> transportByName;
~~~

Với ba beans được phép autowire:
- Một `Transport` không chỉ định cụ thể thường nhận **primary**.
- Một `List<Transport>` có thể chứa **cả ba** (kể cả primary và fallback phù hợp).
- Một `Map<String,Transport>` có thể chứa cả ba; **keys là bean names**, không phải Java class names.
- `@Primary` **không** loại `safeTransport` hay `backupTransport` khỏi collection/map.
- `@Order` hoặc `Ordered` (khi có) tác động tới **thứ tự danh sách**, không biến một bean thành primary.

Vì vậy không được dùng số lượng `List<Transport>` để kết luận primary “không hoạt động”. Primary là tín hiệu khi container cần **chọn đúng một reference** từ nhiều candidates; collections có hợp đồng khác.

Trong ví dụ hoàn chỉnh, kết quả danh sách được **sort** trước khi in để không phụ thuộc vào thứ tự bean registration hoặc order metadata. Đây là cách kiểm chứng nội dung thay vì áp đặt thứ tự không được đảm bảo cho mọi cấu hình.

## 5. `@Qualifier` có thể chọn một bean khác với primary

Giả sử:
- `fastTransport` được đánh dấu `@Primary`;
- `safeTransport` cũng có type `Transport`, nhưng **không** primary;
- một điểm inject có `@Qualifier("safeTransport")`.

~~~java
// Snippet trong @Bean factory method:
@Bean
Dispatcher safeDispatcher(
        @Qualifier("safeTransport") Transport transport) {
    return new Dispatcher(transport);
}
~~~

Ở đây, qualifier giới hạn **tập ứng viên** theo metadata/name phù hợp với type được yêu cầu. Chỉ `safeTransport` có thể thỏa điều kiện, nên Spring chọn nó **dù `fastTransport` là primary**.

**Không nói “primary luôn đè qualifier”.** Nếu primary bean không thỏa qualifier, nó không phải ứng viên hợp lệ của injection point đó. Quy tắc type/qualifier phải được xét trước khi kết luận ai được chọn.

So sánh:

| Injection point | Các candidates phù hợp | Kết quả thông thường |
| --- | --- | --- |
| `Transport t` không qualifier | FAST, SAFE, BACKUP | FAST khi FAST là primary |
| `@Qualifier("safeTransport") Transport t` | SAFE | SAFE |
| `@Qualifier("missingTransport") Transport t` | Không có bean phù hợp | Lỗi required dependency |
| `SmsTransport t` | Chỉ beans kiểu SmsTransport | Không chọn FAST nếu FAST không assignable |
| `List<Transport> list` | Các Transport hợp lệ | Nhiều beans, không chỉ FAST |

`@Qualifier` về bản chất là **bộ lọc trong tập type matches**, không phải câu lệnh tùy tiện lấy một bean khác type. Với yêu cầu **đúng bean name** ở field/setter, `jakarta.annotation.Resource(name="...")` là cơ chế by-name khác, nhưng không cần nó để giải thích `@Primary`.

**Đừng dựa vào tên tham số thay cho qualifier minh bạch** khi code có nhiều implementations. Spring có thể xét tên field/parameter cùng nhiều tiêu chí khác; việc đổi tên field hoặc thay compiler metadata có thể đổi behavior không mong muốn.

## 6. `@Fallback` là một chiến lược khác, không phải `@Primary` đảo ngược

Spring Framework 6.2+ có `@Fallback`:

~~~java
@Bean
Transport fastTransport() {
    return new FastTransport();
}

@Bean
@Fallback
Transport backupTransport() {
    return new BackupTransport();
}
~~~

Khi có **một bean thường** (không fallback) và những beans còn lại đều là fallback, bean thường ấy có thể được chọn như lựa chọn mặc định cho dependency đơn. Trong ví dụ chỉ có FAST thường và BACKUP fallback, yêu cầu một `Transport` chọn **FAST dù không có `@Primary`**.

So sánh ngắn:

| Khái niệm | Ý nghĩa |
| --- | --- |
| `@Primary` | Ưu tiên bean được đánh dấu khi có nhiều qualified candidates cho **một dependency** |
| `@Fallback` | Đánh dấu bean là dự phòng, có thể ưu tiên những candidate thường còn lại |
| `@Qualifier` | Lọc candidates theo điều kiện của injection point |
| `@Bean(autowireCandidate=false)` | Loại bean khỏi cơ chế autowiring thông thường |
| `@Order` | Sắp thứ tự khi thu thập nhiều beans theo một số quy tắc |
| `@Lazy` | Liên quan thời điểm khởi tạo; không phải thứ tự chọn candidate |
| `@Scope` | Quy định phạm vi instance; không tự chọn bean thay thế |

Một bean fallback **vẫn là bean thật** và có thể được lookup bằng tên. `@Fallback` không nhất thiết khiến bean không có mặt trong `List<Transport>`. Theo cơ chế Spring, các type-matching beans vẫn có thể tham gia collection/map injection tùy autowire eligibility.

Đặt đồng thời primary, fallback, qualifiers và default-candidate flags trên nhiều beans có thể khó đọc: cần kiểm tra tập **candidate thực tế ở từng injection point**, chứ không suy diễn từ một annotation đơn lẻ. Trong dự án, cố ý dùng cấu hình đơn giản để chứng minh từng khác biệt.

## 7. Demo Spring Framework 7.0.9 + Maven + Java 17

Demo tạo năm tình huống độc lập:

1. **PrimaryConfig:** ba beans cùng type, FAST là `@Primary`, BACKUP là `@Fallback`. Một dependency đơn chọn FAST, `@Qualifier("safeTransport")` chọn SAFE, collection/map chứa cả ba.
2. **NoPrimaryConfig:** FAST và SAFE đều bình thường; lookup đơn theo type không có tiêu chí phân giải → lỗi **không duy nhất**.
3. **TwoPrimaryConfig:** FAST và SAFE **đều `@Primary`** → không thể chọn một primary duy nhất.
4. **FallbackOnlyConfig:** FAST bình thường và SAFE `@Fallback`, không có primary → FAST vẫn được chọn.
5. **OnlyOneConfig:** duy nhất FAST → Spring chọn FAST dù bean không cần `@Primary`.

Mỗi tình huống dùng một `AnnotationConfigApplicationContext` **riêng**, tránh accidental bean registrations từ cấu hình trước. Tình huống thất bại được catch có chủ đích; không khóa expected stdout vào thông điệp exception/stack trace của Spring.

### 7.1. Cấu trúc project

~~~text
primary-selection-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── PrimarySelectionDemo.java
~~~

Yêu cầu **JDK 17+**, **Maven 3.9+**, và quyền tải Spring dependencies từ Maven Central/mirror ở lần chạy đầu. Đây là **Spring Framework thuần**, không cần Spring Boot, servlet hay cơ sở dữ liệu.

### 7.2. `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>primary-selection-demo</artifactId>
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
                    <mainClass>demo.PrimarySelectionDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Maven compiler plugin chạy `javac` theo target Java 17; Exec Maven Plugin đưa Spring dependencies lên runtime classpath. `spring-context` kéo theo các Spring modules cần thiết qua Maven dependency resolution; không tự thêm từng JAR vào CLI.

### 7.3. Tệp `src/main/java/demo/PrimarySelectionDemo.java`

~~~java
package demo;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Primary;

public final class PrimarySelectionDemo {
    private PrimarySelectionDemo() {
    }

    @FunctionalInterface
    interface Transport {
        String id();
    }

    static final class FastTransport implements Transport {
        @Override
        public String id() {
            return "FAST";
        }
    }

    static final class SafeTransport implements Transport {
        @Override
        public String id() {
            return "SAFE";
        }
    }

    static final class BackupTransport implements Transport {
        @Override
        public String id() {
            return "BACKUP";
        }
    }

    static final class Dashboard {
        private final Transport selected;
        private final Transport qualified;
        private final List<Transport> all;
        private final Map<String, Transport> byName;

        Dashboard(Transport selected, Transport qualified,
                List<Transport> all, Map<String, Transport> byName) {
            this.selected = selected;
            this.qualified = qualified;
            this.all = all;
            this.byName = byName;
        }

        String selectedId() {
            return selected.id();
        }

        String qualifiedId() {
            return qualified.id();
        }

        boolean sameAs(Transport expected) {
            return selected == expected;
        }

        boolean qualifiedSameAs(Transport expected) {
            return qualified == expected;
        }

        String allIds() {
            return all.stream().map(Transport::id)
                    .sorted().collect(Collectors.joining(","));
        }

        String beanNames() {
            return byName.keySet().stream()
                    .sorted().collect(Collectors.joining(","));
        }

        boolean hasBackup() {
            return byName.containsKey("backupTransport");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class PrimaryConfig {
        @Bean("fastTransport")
        @Primary
        public Transport fastTransport() {
            return new FastTransport();
        }

        @Bean("safeTransport")
        public Transport safeTransport() {
            return new SafeTransport();
        }

        @Bean("backupTransport")
        @Fallback
        public Transport backupTransport() {
            return new BackupTransport();
        }

        @Bean
        public Dashboard dashboard(Transport transport,
                @Qualifier("safeTransport") Transport safeTransport,
                List<Transport> transports,
                Map<String, Transport> transportByName) {
            return new Dashboard(transport, safeTransport,
                    transports, transportByName);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class NoPrimaryConfig {
        @Bean
        public Transport fastTransport() {
            return new FastTransport();
        }

        @Bean
        public Transport safeTransport() {
            return new SafeTransport();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class TwoPrimaryConfig {
        @Bean
        @Primary
        public Transport fastTransport() {
            return new FastTransport();
        }

        @Bean
        @Primary
        public Transport safeTransport() {
            return new SafeTransport();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class FallbackOnlyConfig {
        @Bean
        public Transport fastTransport() {
            return new FastTransport();
        }

        @Bean
        @Fallback
        public Transport safeTransport() {
            return new SafeTransport();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class OnlyOneConfig {
        @Bean
        public Transport fastTransport() {
            return new FastTransport();
        }
    }

    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(PrimaryConfig.class)) {
            Dashboard dashboard = context.getBean(Dashboard.class);
            Transport fast = context.getBean("fastTransport", Transport.class);
            Transport safe = context.getBean("safeTransport", Transport.class);

            System.out.println("main.selected=" + dashboard.selectedId());
            System.out.println("main.qualified=" + dashboard.qualifiedId());
            System.out.println("main.samePrimary="
                    + dashboard.sameAs(fast));
            System.out.println("main.sameQualified="
                    + dashboard.qualifiedSameAs(safe));
            System.out.println("main.allIds=" + dashboard.allIds());
            System.out.println("main.mapNames=" + dashboard.beanNames());
            System.out.println("main.mapHasFallback="
                    + dashboard.hasBackup());
            System.out.println("main.byType="
                    + context.getBean(Transport.class).id());
            System.out.println("main.fast.primary="
                    + context.getBeanFactory()
                            .getBeanDefinition("fastTransport").isPrimary());
            System.out.println("main.safe.primary="
                    + context.getBeanFactory()
                            .getBeanDefinition("safeTransport").isPrimary());
            System.out.println("main.backup.fallback="
                    + context.getBeanFactory()
                            .getBeanDefinition("backupTransport").isFallback());
            System.out.println("main.backup.byName="
                    + context.getBean("backupTransport", Transport.class).id());
        }

        try (var context =
                new AnnotationConfigApplicationContext(NoPrimaryConfig.class)) {
            context.getBean(Transport.class);
            System.out.println("noPrimary.unexpected");
        } catch (NoUniqueBeanDefinitionException expected) {
            System.out.println("noPrimary.ambiguous=true");
        }

        try (var context =
                new AnnotationConfigApplicationContext(TwoPrimaryConfig.class)) {
            context.getBean(Transport.class);
            System.out.println("twoPrimary.unexpected");
        } catch (NoUniqueBeanDefinitionException expected) {
            System.out.println("twoPrimary.ambiguous=true");
        }

        try (var context =
                new AnnotationConfigApplicationContext(FallbackOnlyConfig.class)) {
            System.out.println("fallbackOnly.selected="
                    + context.getBean(Transport.class).id());
            System.out.println("fallbackOnly.backup="
                    + context.getBean("safeTransport", Transport.class).id());
        }

        try (var context =
                new AnnotationConfigApplicationContext(OnlyOneConfig.class)) {
            System.out.println("onlyOne.selected="
                    + context.getBean(Transport.class).id());
            System.out.println("onlyOne.isPrimary="
                    + context.getBeanFactory()
                            .getBeanDefinition("fastTransport").isPrimary());
        }
    }
}
~~~

### 7.4. Cách đọc code

- **PrimaryConfig:** FAST, SAFE và BACKUP đều thuộc `Transport`. FAST được đánh dấu `@Primary`, BACKUP có `@Fallback`.
- **Tham số `Transport transport` của `dashboard(...)`:** không có qualifier, Spring chọn FAST vì nó là primary hợp lệ; `selectedId()="FAST"`.
- **Tham số `@Qualifier("safeTransport") Transport safeTransport`:** chỉ SAFE thỏa qualifier nên được cấp vào `Dashboard.qualified`, không lấy FAST.
- **`List<Transport>` / `Map<String,Transport>`:** cả ba beans vẫn được đưa vào collection/map trong ví dụ; `@Primary` và `@Fallback` không xóa chúng khỏi type matches. Output sort để ổn định.
- **BeanDefinition metadata:** `isPrimary()` chỉ true cho FAST; `isFallback()` true cho BACKUP. Đây là metadata của definitions, không phải static flags trên Java class.
- **NoPrimaryConfig:** chỉ có FAST/SAFE, không qualifier và không primary; `getBean(Transport.class)` không thể chọn duy nhất một candidate.
- **TwoPrimaryConfig:** hai beans cùng primary, cũng thiếu quy tắc chọn một duy nhất. Annotation không có nghĩa “chọn bean đăng ký đầu tiên”.
- **FallbackOnlyConfig:** FAST bình thường, SAFE fallback; với đúng một candidate thường, Spring chọn FAST. Nhưng SAFE vẫn có thể được lấy theo tên.
- **OnlyOneConfig:** chỉ có FAST; `getBean(Transport.class)` hoạt động dù `isPrimary=false`. Không nên gắn `@Primary` cho mọi bean nếu chỉ có một implementation.

**Khả năng biên dịch:** file dùng Java 17, `@Configuration`, `@Bean`, `@Primary`, `@Fallback`, `@Qualifier` của Spring 7.0.9; không cần thêm JUnit/Mockito hoặc XML. `@FunctionalInterface` trên `Transport` hợp lệ vì chỉ khai một abstract method `id()` ngoài các methods từ `Object`.

## 8. Chạy CLI và đối chiếu stdout

Trong thư mục `primary-selection-demo` chứa `pom.xml`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

Ba lệnh đầu kiểm tra version JDK/Maven và có output tùy máy. Lệnh cuối biên dịch bằng Maven Compiler Plugin (`--release 17`) rồi chạy `demo.PrimarySelectionDemo`.

**18 dòng stdout do chính chương trình in** (không tính log của Maven, Spring và các báo cáo/stack trace phụ thuộc môi trường):

~~~text
main.selected=FAST
main.qualified=SAFE
main.samePrimary=true
main.sameQualified=true
main.allIds=BACKUP,FAST,SAFE
main.mapNames=backupTransport,fastTransport,safeTransport
main.mapHasFallback=true
main.byType=FAST
main.fast.primary=true
main.safe.primary=false
main.backup.fallback=true
main.backup.byName=BACKUP
noPrimary.ambiguous=true
twoPrimary.ambiguous=true
fallbackOnly.selected=FAST
fallbackOnly.backup=SAFE
onlyOne.selected=FAST
onlyOne.isPrimary=false
~~~

Các kết quả trên không dựa vào ngẫu nhiên, thời gian hoặc network. `Map.keySet()` và `List` được sort trước khi in. Hai context cố tình mơ hồ có thể sinh diagnostic ngoài stdout ứng dụng; chương trình catch đúng loại `NoUniqueBeanDefinitionException` và in boolean ổn định.

**Cách diễn giải trọng tâm:**

| Output | Điều chứng minh |
| --- | --- |
| `main.selected=FAST` | FAST được chọn cho một dependency `Transport` không qualifier |
| `main.qualified=SAFE` | Qualifier chọn SAFE dù FAST là primary |
| `main.samePrimary=true` | Dependency và bean lookup theo tên FAST cùng reference singleton trong context |
| `main.allIds=BACKUP,FAST,SAFE` | Primary/fallback không loại beans khỏi injected list |
| `main.mapNames=...` | Tất cả eligible beans vẫn xuất hiện trong injected map với bean names |
| `main.fast.primary=true` | Metadata trên bean definition FAST đã nhận annotation `@Primary` |
| `noPrimary.ambiguous=true` | Hai candidates ngang hàng không tự được phân giải |
| `twoPrimary.ambiguous=true` | Đánh dấu hai primaries không tạo tiêu chí chọn một |
| `fallbackOnly.selected=FAST` | `@Fallback` có thể ưu tiên candidate thường mà không dùng primary |
| `onlyOne.isPrimary=false` | Một candidate duy nhất không cần đánh dấu primary |

Nếu bạn cố tình sửa cấu hình, output sẽ thay đổi; hãy so với source gốc trước khi đánh giá đúng/sai. Không cố định exception message vì Spring phiên bản/logging khác có thể diễn đạt lỗi khác nhau.

## 9. Ví dụ Java 17 thuần: tư duy chọn một mặc định

`@Primary` là quy tắc của Spring, nhưng có thể hiểu logic bằng Java thuần: một registry có nhiều implementations và một **tên lựa chọn mặc định**. Tạo file `ManualPrimaryChoice.java` riêng biệt (không phụ thuộc Maven/Spring):

~~~java
import java.util.Map;

public class ManualPrimaryChoice {
    interface Transport {
        String id();
    }

    public static void main(String[] args) {
        Map<String, Transport> registry = Map.of(
                "fast", () -> "FAST",
                "safe", () -> "SAFE");

        String defaultName = "fast";
        Transport selected = registry.get(defaultName);
        Transport explicitlyChosen = registry.get("safe");

        System.out.println("default=" + selected.id());
        System.out.println("explicit=" + explicitlyChosen.id());
        System.out.println("available=" + registry.size());
        System.out.println("same=" + (selected == explicitlyChosen));
    }
}
~~~

Chạy với JDK 17+ tại thư mục chứa file:

~~~bash
javac ManualPrimaryChoice.java
java ManualPrimaryChoice
~~~

Stdout:

~~~text
default=FAST
explicit=SAFE
available=2
same=false
~~~

Đây **không** phải cách Spring nội bộ lưu primary: `Map` và `defaultName` được chúng ta tạo thủ công, không có BeanFactory, type filtering hay lifecycle. Nó chỉ minh họa ý tưởng **“có nhiều lựa chọn hợp lệ, nhưng chỉ định một lựa chọn mặc định cho yêu cầu không đặc tả thêm”**.

Spring bổ sung các yếu tố khác: bean metadata, `@Qualifier`, fallback, generic type, scope, conditional registrations và exception khi không thể phân giải. Đừng tự thay Spring BeanFactory bằng một map toàn cục trong business code nếu không có lý do kiến trúc đặc biệt.

## 10. Các tình huống cần đặc biệt cẩn trọng

### 10.1. Primary chỉ có tác dụng trong tập candidates hợp lệ

Nếu bean FAST bị đặt `autowireCandidate=false` hoặc không đáp ứng qualifier/generic của injection point, annotation primary không buộc Spring inject nó. Một bean bị exclude khỏi autowiring vẫn có thể được tra theo tên trong những trường hợp thích hợp, nhưng **không còn là candidate để chọn cho injection point đó**.

Nếu bean FAST không có type tương thích `Transport`, đừng mong `@Primary` làm JVM ép kiểu một object sai interface.

### 10.2. Primary và generic type

Giả sử có các bean `Store<String>` và `Store<Integer>`, Spring có thể dùng **generic type arguments như implicit qualifier**. Một `Store<Integer>` được đánh dấu primary không nhất thiết là candidate cho injection point yêu cầu `Store<String>`. Hãy khai báo factory method return type cụ thể để Spring có metadata generic tin cậy.

### 10.3. Primary và parent/child contexts

Một hệ thống có nhiều `ApplicationContext` (cha/con). Bean visibility và local overrides có thể ảnh hưởng candidate set; primary metadata ở context cha không tự biến mọi lookup trong context con thành một quy tắc toàn JVM.

Một số type-discovery APIs như `getBeanNamesForType` còn có phạm vi local khác với các tiện ích ancestor-aware. Khi debug, hãy chỉ rõ **context nào đang resolve dependency**.

### 10.4. Primary và AOP proxy

Proxy có thể thay runtime class nhìn thấy qua `getClass()` hoặc chỉ phơi interface, tùy JDK dynamic proxy/class-based proxy. `@Primary` không sửa type assignability hoặc tạo interface thiếu. Nếu injection point yêu cầu concrete class mà proxy không tương thích, điều đó phải được giải quyết ở thiết kế type/proxy, không phải bằng cách tăng độ ưu tiên.

### 10.5. Primary trên class và đăng ký bằng XML

Javadoc của `@Primary` có giới hạn liên quan nguồn metadata. Với XML `<bean>`, nên khai rõ **`primary="true"`** trong bean definition thay vì giả định class annotation được đọc giống component scan. Với Java `@Bean`, đặt `@Primary` tại `@Bean` method.

### 10.6. Scope singleton không có nghĩa bean primary có một instance cho cả JVM

Bean singleton mặc định thuộc **bean definition trong một Spring container**. Hai contexts độc lập có thể tạo hai instances khác nhau của cùng một `@Bean @Primary`. `@Primary` quyết định ưu tiên dependency candidate, không thay đổi scope hoặc lifecycle.

### 10.7. Primary không thay thế fail-fast khi thiếu required bean

Nếu không có implementation thỏa type, `@Primary` trên một definition không được đăng ký không giúp ích. Với một required dependency, container nên fail sớm và chỉ ra thiếu bean thay vì lặng lẽ tạo `null`. Nếu optional thật sự, biểu đạt bằng `Optional<T>`, provider hoặc default strategy theo hợp đồng; đừng dùng annotation primary để che lỗi.

## 11. Lỗi thường gặp và cách chẩn đoán

| Triệu chứng | Cách hiểu đúng | Cách xử lý |
| --- | --- | --- |
| `NoUniqueBeanDefinitionException` khi inject type `T` | Có nhiều candidates mà không xác định được một | Kiểm type matches; dùng một primary hoặc qualifier theo ý định |
| Hai `@Primary` cùng type | Không có primary duy nhất | Chọn một bean mặc định, hoặc thêm qualifier tại từng điểm inject |
| `@Qualifier("safeTransport")` không nhận FAST primary | Đây là hành vi đúng: FAST không thỏa qualifier SAFE | Kiểm hợp đồng điểm inject, không đổi primary để chữa |
| `@Primary` nhưng `getBean("safeTransport")` vẫn lấy SAFE | Lookup **theo tên explicit**, không phải lựa chọn theo type | Phân biệt getBean(name) với getBean(type) |
| `List<Transport>` chứa cả FAST và SAFE | Primary chỉ giúp chọn **một** dependency | Dùng collection đúng mục đích; lọc/sắp nếu cần |
| Bean primary không có mặt trong collection injection | Có thể bị loại bởi type/qualifier/autowireCandidate | Kiểm bean registration và candidate filtering |
| Bỏ `@Primary` nhưng injection vẫn chạy | Có thể chỉ còn một candidate, hoặc có qualifier/name/fallback khác | Quan sát injection point thực tế và tập candidates |
| Class có `@Primary` nhưng bean không được ưu tiên | Metadata đăng ký không đến từ component scan hoặc annotation ở sai nơi | Với `@Bean`, đặt `@Primary` trên method; XML dùng primary attr |
| Bean cùng type ở 2 contexts cho behavior khác | Candidate set/hierarchy khác nhau | Xem context cụ thể, profile/registration/parent hierarchy |
| Primary bean có type implementation khác | Chỉ type tương thích mới là candidate | Đổi interface hợp đồng hoặc type đăng ký phù hợp |
| App chạy nhưng chọn implementation sai | `@Primary` bị dùng quá rộng cho nhiều use cases | Dùng `@Qualifier` cho điểm inject cụ thể, test composition root |
| Circular dependencies không biến mất | Primary chọn candidate, không sửa đồ thị chu trình | Refactor quan hệ phụ thuộc |
| Bean được đánh `@Primary` nhưng init lỗi | Primary không bỏ qua constructor/lifecycle failure | Đọc root cause của bean creation |
| Không thấy `@Primary` trên type discovery | Type-discovery APIs thường không thể hiện trực tiếp ưu tiên | Xem bean definition `isPrimary()` hoặc phép lookup thực |
| Ngộ nhận `@Order` là `@Primary` | Ordering khác candidate preference | Dùng đúng annotation theo nhu cầu |

**Checklist debug:** xác định injection point đang cần **một bean hay collection**; kiểm bean registrations và type compatibility; xem qualifier/generic/primary/fallback và `autowireCandidate`; xác minh primary metadata ở **bean definition**; rồi quan sát kết quả `getBean(Type.class)` hoặc wiring integration test trong **đúng context**.

## 12. Bài tập dự đoán trước khi chạy

Làm từng thay đổi trên một bản sao code, khôi phục source gốc giữa các bài để so expected stdout.

1. **Đổi primary:** chuyển `@Primary` từ FAST sang SAFE trong PrimaryConfig. Dự đoán `main.selected=SAFE`, còn `main.qualified=SAFE` vẫn giữ.
2. **Bỏ primary:** xóa `@Primary` ở PrimaryConfig nhưng giữ FAST/SAFE thường. Dự đoán `dashboard(Transport transport,...)` có thể gây ambiguity khi context refresh.
3. **Thêm primary thứ hai:** đánh `@Primary` cho SAFE khi FAST vẫn primary. Giải thích tại sao hai primary không tạo ra “thứ tự ưu tiên” và context có thể fail vì Dashboard cần một Transport.
4. **Đổi qualifier:** sửa `@Qualifier("safeTransport")` thành `@Qualifier("fastTransport")`; dự đoán `main.qualified=FAST` và `main.sameQualified` phải so với đúng reference.
5. **Bỏ qualifier:** bỏ `@Qualifier` ở tham số safeTransport. Dự đoán cả hai `Transport` parameters sẽ chọn FAST primary nếu không có ràng buộc khác.
6. **Thêm fallback:** đánh `@Fallback` cho SAFE (không primary). Dự đoán các beans nào vẫn có mặt trong list/map.
7. **Thêm implementation:** tạo `ExperimentalTransport`, đăng ký `@Bean` mới. Nếu giữ đúng một primary FAST, single injection vẫn có thể chọn FAST, collection sẽ mở rộng.
8. **Thay collection:** dùng `List<Transport>` và in `size()`. Vì sao số lượng không trở thành 1 chỉ vì có primary?
9. **Đổi scope:** đặt `@Scope("prototype")` trên FAST, lấy hai lần theo tên để quan sát identity. Phân biệt candidate priority và number of instances.
10. **Đổi declared return type:** khai báo một `@Bean` có interface type vs concrete class type; xem ảnh hưởng của metadata trong lookup theo implementation.
11. **Dùng `@Qualifier` với SAFE không primary:** xác nhận qualifier vẫn chọn đúng SAFE; vì sao primary không phải “bộ lọc mạnh nhất”?
12. **Dùng XML:** tạo định nghĩa độc lập với `primary="true"` và so cơ chế với annotation trên method `@Bean`.
13. **Không có bean:** loại tất cả Transport beans khỏi một config và gọi `getBean(Transport.class)`; phân biệt `NoSuchBeanDefinitionException` với `NoUniqueBeanDefinitionException`.
14. **Chạy Java thuần:** thêm lựa chọn `backup` vào map của `ManualPrimaryChoice`; thấy việc chỉ định default không ngăn caller yêu cầu một key khác.
15. **Tách tests:** viết unit test Dashboard với các transport fake truyền trực tiếp và integration test dùng `AnnotationConfigApplicationContext` để kiểm chứng `@Primary`.

### Checklist hoàn thành

- [ ] Hiểu `@Primary` là **preference cho single-valued dependency**, không phải tạo bean hoặc loại beans khác.
- [ ] Khai báo được `@Primary` đúng nơi trên `@Bean` hoặc component class.
- [ ] Biết một candidate duy nhất không cần primary.
- [ ] Biết hai primary có thể tạo ambiguity.
- [ ] Phân biệt `@Primary`, `@Qualifier`, `@Fallback`, `@Order` và `autowireCandidate=false`.
- [ ] Không nhầm `getBean(name)` với `getBean(Type.class)`.
- [ ] Giải thích vì sao list/map injection vẫn nhận nhiều beans.
- [ ] Nhận diện đúng scope/context, qualifier và generic type khi primary không được chọn.
- [ ] Chạy Maven Java 17 ra đúng 18 dòng stdout ứng dụng và Java thuần ra đúng 4 dòng.
- [ ] Biết điều tra root cause khi bean selection bị thiếu hoặc mơ hồ.

## 13. Tóm tắt và tài liệu tra cứu

**Ghi nhớ:** Khi nhiều bean definitions cùng có thể đáp ứng một dependency, `@Primary` đánh dấu bean nên được **ưu tiên** nếu phải chọn **một** và bean ấy **còn là ứng viên hợp lệ** sau khi xét type, qualifiers và các ràng buộc liên quan. Nó không xóa bean khác, không đổi lifecycle/scope và không ép override một yêu cầu tên tường minh.

Tài liệu chính thức:
- [Spring Framework — Primary and Fallback](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-primary.html)
- [Spring Framework — Fine-tuning Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html)
- [Spring Framework — Using @Autowired](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html)
- [Spring Framework — Autowiring Collaborators](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-autowire.html)
- [Spring Framework — Java Configuration and @Bean](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html)
- [Spring API — Primary](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/Primary.html)
- [Spring API — Fallback](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/Fallback.html)
- [Spring API — BeanDefinition](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/config/BeanDefinition.html)

**Kết luận thực hành:** Hãy xem `@Primary` là **một policy mặc định khi Spring phải chọn một ứng viên**, không phải cách che mọi lỗi DI. Nếu một use case cần implementation cụ thể, dùng qualifier rõ ràng; nếu cần tất cả implementations, dùng collection; nếu muốn một lựa chọn dự phòng, cân nhắc fallback. Kiểm chứng từng policy bằng một context nhỏ và output quan sát được.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="candidate-selection-004">Lọc tập ứng viên bằng @Qualifier</a>

<details>
<summary>Xem chi tiết</summary>

# Lọc tập ứng viên bằng `@Qualifier` trong Spring Core Container

## 1. Bản chất: chọn từ tập bean đã khớp kiểu

Khi Spring cần cung cấp dependency cho một constructor, field, method hoặc `@Bean` factory method, container trước hết xét **những bean phù hợp kiểu** của injection point. Nếu có nhiều ứng viên cùng interface, `@Qualifier` của Spring cho phép **thu hẹp tập ứng viên** theo một đặc điểm, nhãn hoặc tên phù hợp.

~~~text
                  Bean definitions
        fastA: Channel + qualifier "fast"
        fastB: Channel + qualifier "fast"
        slowC: Channel + qualifier "slow"
        formatter: Formatter + qualifier "fast"
                           |
                yêu cầu type Channel
                           |
                {fastA, fastB, slowC}
                           |
                @Qualifier("fast")
                           |
                    {fastA, fastB}
                      /        \
                List<Channel>  Channel
                   2 phần tử    2 ứng viên:
                                có thể mơ hồ
~~~

Điều quan trọng nhất của bài: **`@Qualifier` là bộ lọc trong số các bean tương thích kiểu, không mặc nhiên là khóa tham chiếu duy nhất tới một bean ID**. Hai bean cùng qualifier `"fast"` vẫn đều phù hợp khi inject một `List<Channel>`. Ngược lại, một injection point yêu cầu **một `Channel` duy nhất** sẽ còn mơ hồ nếu sau khi lọc vẫn có hai ứng viên và không có quy tắc chọn bổ sung. Đây là semantics được Spring nêu trực tiếp trong tài liệu tham khảo. 

Vì vậy, với `@Autowired` hoặc injection vào tham số `@Bean`, hãy hình dung ba bước:

1. Xác định **required type** tại injection point, có tính đến generic type và assignability.
2. Áp dụng các giới hạn/metadata như `@Qualifier` để **lọc những bean được phép làm ứng viên**.
3. Nếu yêu cầu một object, container phải chọn được **một** ứng viên cuối cùng; nếu yêu cầu collection, container có thể cung cấp **tất cả** ứng viên còn lại theo quy tắc tương ứng.

**Mục tiêu học:** dùng qualifier trực tiếp trên tham số, đánh dấu ứng viên bằng annotation, lọc `List`/`Set`, phân biệt với `@Primary` và lookup theo tên, xây custom qualifier, kiểm tra lỗi thiếu/mơ hồ, và chạy ví dụ Maven Java 17.

## 2. Cú pháp tối thiểu của Spring `@Qualifier`

Annotation ở đây là:

~~~java
import org.springframework.beans.factory.annotation.Qualifier;
~~~

Đừng nhầm với `jakarta.inject.Qualifier`: cái thứ hai là **meta-annotation** để định nghĩa qualifier tùy chỉnh, không nhận String trực tiếp theo cùng cú pháp.

### Trên bean definition: mô tả ứng viên

Với Java configuration:

~~~java
@Bean
@Qualifier("fast")
DeliveryPort fastGateway() {
    return new FastGateway();
}
~~~

Đây là **đoạn minh họa**, cần định nghĩa `DeliveryPort` và `FastGateway` nếu biên dịch độc lập. Nó công bố một bean tên mặc định `fastGateway`, type `DeliveryPort`, với metadata qualifier `"fast"`.

Cùng annotation cũng có thể được đặt trên một Spring component class:

~~~java
@Component
@Qualifier("fast")
final class FastGateway implements DeliveryPort {
    // Cần triển khai interface trong project thực.
}
~~~

Đây là cấu hình **thay thế minh họa**, không phải class thêm vào chương trình chính. Nó chỉ được đăng ký khi Spring scan đúng package hoặc bean được đăng ký theo cơ chế phù hợp.

### Trên injection point: chỉ nhận nhóm đã lọc

~~~java
@Autowired
public Sender(@Qualifier("fast") DeliveryPort gateway) {
    this.gateway = gateway;
}
~~~

Đây là snippet minh họa constructor injection. Spring tìm bean phù hợp type `DeliveryPort` **và** qualifier `"fast"`, rồi cần lựa chọn cuối cùng cho tham số đơn `gateway`. Nếu còn nhiều bean `"fast"` cùng type, chỉ ghi qualifier này chưa bảo đảm kết quả duy nhất.

Nếu class managed bean chỉ có một constructor, Spring Framework 4.3+ thường không cần `@Autowired` trên constructor ấy. Tuy nhiên **`@Qualifier` trên tham số vẫn có ý nghĩa**:

~~~java
public Sender(@Qualifier("fast") DeliveryPort gateway) {
    this.gateway = gateway;
}
~~~

Một lần nữa, mã trên chỉ minh họa khi `Sender` và collaborator được đăng ký đúng.

### Áp dụng trên từng tham số

~~~java
@Autowired
public void configure(
        @Qualifier("fast") DeliveryPort foreground,
        @Qualifier("slow") DeliveryPort background) {
    this.foreground = foreground;
    this.background = background;
}
~~~

Mỗi tham số được giải quyết riêng. Annotation trên `foreground` không tác động tới `background`. Phương thức có thể tên `configure`, không cần `set...`. Điều kiện là bean thực sự được Spring quản lý, các type/qualifiers phù hợp và không mơ hồ. Đoạn này không độc lập vì chưa định nghĩa các field/interface.

## 3. Qualifier là bộ lọc, **không buộc phải duy nhất**

Giả sử ba bean cùng kiểu `DeliveryPort`:

| Bean name | Type | Qualifier khai báo |
| --- | --- | --- |
| `fastA` | `DeliveryPort` | `fast` |
| `fastB` | `DeliveryPort` | `fast` |
| `slowC` | `DeliveryPort` | `slow` |

Các yêu cầu:

| Injection point | Tập ứng viên sau lọc | Kết quả thông thường |
| --- | --- | --- |
| `DeliveryPort` không qualifier | `fastA, fastB, slowC` | Mơ hồ nếu không có rule chọn |
| `@Qualifier("fast") DeliveryPort` | `fastA, fastB` | Vẫn có thể mơ hồ |
| `@Qualifier("slow") DeliveryPort` | `slowC` | Chọn `slowC` |
| `@Qualifier("fast") List<DeliveryPort>` | `fastA, fastB` | Inject cả hai |
| `@Qualifier("slow") Set<DeliveryPort>` | `slowC` | Inject tập gồm một bean |
| `@Qualifier("missing") DeliveryPort` | Không có bean phù hợp | Required injection thường lỗi |

Bảng trên giả định bean không có các metadata chọn bổ sung như `@Primary`, tên parameter mang ý nghĩa đặc biệt, hay điều kiện khác; nó minh họa chính quy tắc **thu hẹp theo type và qualifier**.

### Vì sao List vẫn nhận hai bean?

Bởi vì `@Qualifier("fast")` không có điều khoản “chỉ một bean duy nhất được phép mang nhãn fast”. Nó có nghĩa “chỉ xét những bean có đặc trưng fast”. Khi injection target là `List<DeliveryPort>` hoặc `Set<DeliveryPort>`, Spring có thể cung cấp tất cả bean phù hợp với cả type lẫn qualifier. Đây là trường hợp rất tự nhiên khi muốn nhiều handler, rule engine, notifier hay adapter cùng nhóm.

### Một bean ID không phải định nghĩa của qualifier

Spring cho phép **bean name làm giá trị fallback qualifier** trong tập bean đã đúng type. Nhờ đó, trong nhiều trường hợp có thể viết:

~~~java
@Autowired
public Sender(@Qualifier("fastA") DeliveryPort gateway) {
    this.gateway = gateway;
}
~~~

dù bean `fastA` chưa được đánh dấu riêng `@Qualifier("fastA")`, vì Spring có thể xét tên bean `fastA`. Nhưng đây vẫn là **type-driven autowiring**; một bean sai type không trở thành `DeliveryPort` chỉ vì tên khớp.

**Khuyến nghị:** nhãn qualifier mô tả thuộc tính ổn định như `"fast"`, `"eu"` hoặc `"persistent"` thường bền hơn chuỗi bean name `"fastGatewayV2"`. Còn nếu mục tiêu thật sự là tham chiếu **duy nhất theo tên**, `@Resource(name="...")` từ Jakarta Annotations/JSR-250 có semantics lookup theo tên khác với `@Autowired` + qualifier; cần classpath/annotation support phù hợp.

## 4. Ví dụ Spring Framework 6.2 / Java 17 runnable

Chương trình console có **hai bean cùng qualifier `fast`** và một bean qualifier `slow`. Spring inject toàn bộ nhóm fast vào một `List<DeliveryPort>` nhưng chỉ một slow vào dependency đơn.

### 4.1. Cấu trúc project

Cần JDK 17+ và Maven:

~~~text
qualifier-filter-demo/
├── pom.xml
└── src/main/java/demo/QualifierFilterDemo.java
~~~

Tạo `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>qualifier-filter-demo</artifactId>
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
                    <mainClass>demo.QualifierFilterDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 4.2. Mã nguồn đầy đủ

Tạo `src/main/java/demo/QualifierFilterDemo.java`:

~~~java
package demo;

import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

public final class QualifierFilterDemo {
    private QualifierFilterDemo() {}

    public interface DeliveryPort {
        String code();
        String send(String text);
    }

    public record DemoDelivery(String code) implements DeliveryPort {
        @Override
        public String send(String text) {
            return code + ":" + text;
        }
    }

    public static final class Router {
        private final List<DeliveryPort> fast;
        private final DeliveryPort slow;

        public Router(List<DeliveryPort> fast, DeliveryPort slow) {
            this.fast = List.copyOf(Objects.requireNonNull(fast));
            this.slow = Objects.requireNonNull(slow);
        }

        public String fastNames() {
            return fast.stream()
                    .map(DeliveryPort::code)
                    .sorted()
                    .toList()
                    .toString();
        }

        public int fastCount() {
            return fast.size();
        }

        public String slowName() {
            return slow.code();
        }

        public String deliverSlow(String text) {
            return slow.send(text);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        @Qualifier("fast")
        public DeliveryPort fastA() {
            return new DemoDelivery("fast-a");
        }

        @Bean
        @Qualifier("fast")
        public DeliveryPort fastB() {
            return new DemoDelivery("fast-b");
        }

        @Bean
        @Qualifier("slow")
        public DeliveryPort slowC() {
            return new DemoDelivery("slow-c");
        }

        @Bean
        public Router router(
                @Qualifier("fast") List<DeliveryPort> fast,
                @Qualifier("slow") DeliveryPort slow) {
            return new Router(fast, slow);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            Router router = context.getBean(Router.class);

            System.out.println("TYPE_COUNT "
                    + context.getBeanNamesForType(DeliveryPort.class).length);
            System.out.println("FAST_NAMES " + router.fastNames());
            System.out.println("FAST_COUNT " + router.fastCount());
            System.out.println("SLOW_NAME " + router.slowName());
            System.out.println("SLOW_RESULT "
                    + router.deliverSlow("PING"));
            System.out.println("SAME_ROUTER "
                    + (router == context.getBean(Router.class)));
        }
    }
}
~~~

Điểm quan trọng: `@Qualifier("fast")` trên **hai `@Bean` methods** tạo metadata cho hai beans cùng nhóm. `@Qualifier("fast")` ở tham số `List<DeliveryPort>` của factory method `router` lọc ra đúng hai bean. `Router` là Java class không import Spring, còn quyết định wiring nằm ở configuration.

### 4.3. Lệnh chạy

Từ thư mục `qualifier-filter-demo`:

~~~bash
mvn -version
mvn -q compile exec:java
~~~

Trong ví dụ này, `maven.compiler.release=17` dùng trình biên dịch Java tương thích release 17. Nếu dùng `javac/java` trực tiếp thì cần classpath đầy đủ gồm Spring và dependencies; Maven giúp xử lý classpath và chạy main.

**Stdout từ `System.out.println` theo đúng thứ tự:**

~~~text
TYPE_COUNT 3
FAST_NAMES [fast-a, fast-b]
FAST_COUNT 2
SLOW_NAME slow-c
SLOW_RESULT slow-c:PING
SAME_ROUTER true
~~~

`mvn -version` cũng in phiên bản Maven/JDK; Maven có thể in log và tải dependency. Khối trên **chỉ mô tả output ứng dụng chủ động in**, không khẳng định terminal không có log khác.

### 4.4. Giải thích kết quả

1. Khi `AnnotationConfigApplicationContext(AppConfig.class)` refresh, Spring đăng ký ba bean `DeliveryPort` là `fastA`, `fastB`, `slowC`, cùng bean `Router`.
2. `getBeanNamesForType(DeliveryPort.class)` có độ dài 3. Đây là đếm **theo type**, chưa áp qualifier.
3. Factory method `router(...)` nhận `List<DeliveryPort> fast` với qualifier `"fast"`. Spring cung cấp cả `fastA` và `fastB`; vì vậy `FAST_COUNT 2`.
4. `Router.fastNames()` sắp xếp các code trước khi in, để stdout không phụ thuộc thứ tự container duyệt bean cùng nhóm: `[fast-a, fast-b]`.
5. Tham số đơn `@Qualifier("slow") DeliveryPort slow` sau lọc chỉ còn `slowC`, nên `SLOW_NAME slow-c`.
6. `deliverSlow("PING")` dùng reference đã nhận để trả `slow-c:PING`, không yêu cầu container chọn lại candidate ở mỗi lần gọi.
7. `Router` là singleton theo mặc định trong một context; lookup hai lần trả cùng object, nên `SAME_ROUTER true`.

Đầu ra không có println trong factory methods/constructor, tránh phụ thuộc thứ tự tạo singleton không liên quan. `List.copyOf` bảo vệ field list khỏi bị sửa từ bên ngoài, nhưng không tự làm mỗi `DeliveryPort` immutable/thread-safe.

## 5. Ví dụ Java 17 thuần: filtering và injection là hai khâu khác nhau

Để nhìn rõ ý tưởng lọc, có thể thử một ví dụ **không dùng Spring**. Đây **không phải** Spring qualifier resolution; Java code chủ động lọc theo tag để mô phỏng cơ chế chọn.

Tạo `CandidateFilter.java`:

~~~java
import java.util.List;

public final class CandidateFilter {
    public record Candidate(String beanName, String type, String tag) {}

    public static void main(String[] args) {
        List<Candidate> beans = List.of(
            new Candidate("fastA", "Channel", "fast"),
            new Candidate("fastB", "Channel", "fast"),
            new Candidate("slowC", "Channel", "slow"),
            new Candidate("formatA", "Formatter", "fast")
        );

        List<String> matches = beans.stream()
            .filter(b -> b.type().equals("Channel"))
            .filter(b -> b.tag().equals("fast"))
            .map(Candidate::beanName)
            .sorted()
            .toList();

        System.out.println("MATCHES " + matches);
        System.out.println("COUNT " + matches.size());
        System.out.println("UNIQUE " + (matches.size() == 1));
    }
}
~~~

Tại thư mục chứa file:

~~~bash
javac --release 17 -encoding UTF-8 CandidateFilter.java
java CandidateFilter
~~~

**Stdout chính xác:**

~~~text
MATCHES [fastA, fastB]
COUNT 2
UNIQUE false
~~~

Hai bước filter mô phỏng rằng `formatA` cùng nhãn fast nhưng **khác type** không được chọn, còn hai Channel cùng nhãn fast vẫn tồn tại sau lọc. Trong Spring thực tế, các type còn có inheritance, generic metadata, qualifiers và nhiều rule candidate resolution khác; code này chỉ dùng để củng cố ý tưởng **type first, qualifier narrowing**.

## 6. `@Primary` và `@Qualifier` phối hợp thế nào?

`@Primary` là một quy tắc ưu tiên khi Spring cần chọn **một bean** trong nhiều ứng viên. `@Qualifier` giới hạn đối tượng được xét. Không nên hiểu `@Primary` là cơ chế bỏ qua mọi qualifier.

Ví dụ giả định:

- `fastA` và `fastB` mang qualifier `fast`.
- `slowC` mang qualifier `slow` và được đánh dấu `@Primary`.

Nếu injection point yêu cầu `@Qualifier("fast") DeliveryPort`, Spring **không chọn slowC chỉ vì slowC là primary**, bởi slowC không thỏa qualifier yêu cầu.

Còn nếu đánh dấu `fastA` làm primary duy nhất trong nhóm `fast`, Spring có thể chọn `fastA` cho dependency đơn có qualifier fast (khi không có điều kiện khác). Nhưng với `@Qualifier("fast") List<DeliveryPort>`, **cả fastA và fastB vẫn phù hợp để xuất hiện trong collection**; primary không tự biến bộ lọc thành chọn một phần tử.

~~~java
// Biến thể minh họa, không thêm đồng thời vào file runnable:
// @Bean
// @Primary
// @Qualifier("fast")
// DeliveryPort fastA() { return new DemoDelivery("fast-a"); }
~~~

Cần import `org.springframework.context.annotation.Primary` nếu áp dụng thật. Đừng dùng `@Primary` để giải quyết logic chọn adapter **theo dữ liệu runtime** của mỗi giao dịch; đó là thiết kế strategy/router nghiệp vụ, không phải bean candidate resolution.

### Khi cả nhóm không có ứng viên

`@Qualifier("missing") DeliveryPort` không tạo bean mới. Với injection point required thông thường, Spring báo lỗi thiếu bean phù hợp. Đối với `List<DeliveryPort>` required trên field/method, thiếu phần tử phù hợp cũng có thể dẫn tới lỗi; ở một số đường injection qua constructor/factory method, Spring có quy tắc đặc biệt cho multi-element arguments. Vì vậy nếu nhóm có thể rỗng, hãy thiết kế/test theo phiên bản và signature thực tế thay vì tự tin Spring luôn trả list rỗng.

## 7. Lọc `List`, `Set`, `Map`, array

Spring hỗ trợ injection theo type cho typed collections và arrays. Qualifier có thể giới hạn những bean lọt vào nhóm.

~~~java
@Autowired
public void configure(
        @Qualifier("fast") List<DeliveryPort> fastPorts,
        @Qualifier("slow") DeliveryPort slowPort) {
    this.fastPorts = fastPorts;
    this.slowPort = slowPort;
}
~~~

Đây là ví dụ minh họa chưa độc lập; các field/interface cần được định nghĩa. Các hình thức tương tự có thể là:

- `Set<DeliveryPort>`: nhóm beans phù hợp, không phụ thuộc vào thứ tự ngẫu nhiên khi sử dụng.
- `DeliveryPort[]`: array các ứng viên phù hợp.
- `Map<String, DeliveryPort>`: key thông thường là bean name, value là bean instance phù hợp; qualifier cũng có thể thu hẹp candidates theo những điều kiện resolution tương ứng.
- `List<DeliveryPort>`: có thể giữ order theo `@Order`/`Ordered` cho injection theo nhóm, nếu có quy tắc order được khai báo. Không dùng thứ tự ngẫu nhiên của component scan làm logic.

Một qualifier **có thể khớp nhiều beans**, nên phù hợp tự nhiên với chain handlers, batch processors, multiple notifications hoặc các chiến lược cùng một vai trò.

### Phân biệt group filtering và strategy runtime

Inject danh sách “mọi kênh fast” là **cấu hình thành phần** tại lúc Spring lắp bean graph. Nếu use case cần chọn “fast hay slow” dựa trên yêu cầu người dùng trong mỗi lần gọi, logic đó thuộc service/router runtime. `@Qualifier` không đánh giá nội dung request để tự chuyển bean.

## 8. Custom qualifier mang ý nghĩa nghiệp vụ

Một chuỗi `@Qualifier("fast")` dễ dùng, nhưng string rải nhiều nơi có thể khó đổi tên. Khi một nhãn là quy ước quan trọng, có thể tạo annotation riêng.

Ví dụ file `Fast.java`:

~~~java
package demo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.beans.factory.annotation.Qualifier;

@Target({ElementType.TYPE, ElementType.METHOD,
         ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface Fast {}
~~~

Đây là **file annotation minh họa**, không được thêm vào project runnable ở mục 4. Có thể đặt `@Fast` trên candidate bean/class và trên injection point. Spring sẽ xem annotation đó như qualifier metadata, không phải một method được tự gọi.

Cũng có thể định nghĩa một annotation có thuộc tính, ví dụ `@Region("EMEA")`:

~~~java
@Target({ElementType.TYPE, ElementType.METHOD,
         ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface Region {
    String value();
}
~~~

Đây là minh họa cần imports giống `Fast.java`. Khi đặt `@Region("EMEA")` và `@Region("APAC")` lên các beans phù hợp, injection point mang `@Region("EMEA")` sẽ lọc theo giá trị cụ thể.

**Lưu ý:** không nhầm Spring meta `@Qualifier` với `jakarta.inject.Qualifier`; cả hai đều có thể liên quan qualifier tùy chỉnh trong một số cấu hình, nhưng package và cơ chế framework cần được nhận biết. Bài này tập trung **Spring `@Qualifier`**.

### Dùng XML bean definitions

Với XML, có thể gắn metadata qualifier trực tiếp:

~~~xml
<bean id="fastGateway" class="demo.FastGateway">
    <qualifier value="fast"/>
</bean>
~~~

Đây là **đoạn XML minh họa**, không chạy độc lập; cần class và XML root/schema hợp lệ. Spring xét qualifier metadata khi giải quyết dependency qua annotation. Không nhầm XML `<qualifier>` với Java method `setQualifier` trên bean; nó là metadata của bean definition.

## 9. Injection theo tên và sự khác biệt với `@Resource`

Spring có fallback matching theo bean name hoặc alias trong quá trình type-driven resolution khi không có chỉ dẫn mạnh hơn như qualifier/primary/fallback. Trên Spring 6.1+, matching dựa **tên tham số Java** đòi thông tin tên tham số được giữ lại khi compile, thường dùng tùy chọn `-parameters`; Spring 6.2 còn tối ưu shortcut cho name matching trong một số trường hợp.

Vì vậy code:

~~~java
public Sender(DeliveryPort fastA) {
    this.gateway = fastA;
}
~~~

có thể chọn bean tên `fastA` khi có nhiều `DeliveryPort` phù hợp và không có metadata ưu tiên khác, **nếu** thông tin tên tham số được giữ lại và các rule resolution thỏa. Không nên dựa vào việc này khi yêu cầu nghiệp vụ thực tế cần một vai trò cụ thể: `@Qualifier("fast")` hoặc qualifier custom có ý nghĩa rõ và ít phụ thuộc tên biến Java hơn.

`@Resource(name="fastA")` đến từ Jakarta Annotations (JSR-250, package `jakarta.annotation`), là cách lookup thiên về tên bean và có quy tắc riêng. Nó phù hợp hơn khi mục tiêu rõ là lấy một object theo name, nhưng `@Resource` không có cùng phạm vi áp dụng như `@Autowired` trên constructor/method nhiều tham số; cần phân biệt.

## 10. Các sai lầm thường gặp

| Hiểu nhầm / lỗi | Giải thích / cách khắc phục |
| --- | --- |
| “`@Qualifier("fast")` luôn chọn đúng một bean.” | Nó lọc tập bean theo type; có thể còn hai bean fast. |
| “Qualifier là một ID tuyệt đối, bất kể type.” | Type-driven selection vẫn áp dụng; qualifier không làm bean sai type hợp lệ. |
| “Một qualifier chỉ được dùng trên một bean.” | Nhiều beans có thể cùng qualifier, đặc biệt để inject collection. |
| “Gắn `@Qualifier` lên parameter là đủ dù bean chưa đăng ký.” | Bean phải tồn tại và mang type/metadata phù hợp. |
| “`@Primary` luôn thắng mọi qualifier.” | Primary không cho phép chọn bean không thỏa qualifier. |
| “`@Primary` trên một bean fast làm List fast chỉ còn bean đó.” | Collection có thể nhận tất cả candidates thỏa qualifier. |
| “Gắn `@Qualifier` lên method business làm method tự chạy.” | Annotation có ý nghĩa trên bean definition/injection point phù hợp, không gọi business logic. |
| “`@Qualifier` và `jakarta.inject.Qualifier` là một.” | Spring annotation nhận String; Jakarta annotation thường là meta-annotation. |
| “Tên parameter trùng bean name luôn đủ để autowire.” | Có điều kiện metadata và cần `-parameters` trên Spring 6.1+ cho name match. |
| “Không bean fast thì list injection luôn nhận [] mà không lỗi.” | Còn phụ thuộc đường injection/required semantics; phải test đúng signature. |
| “Inject mọi bean là thực thi tất cả bean mỗi request.” | Injection là wiring; gọi business methods cần code chủ động. |
| “Qualifier giải quyết transaction/HTTP route/đa luồng.” | Nó chỉ ảnh hưởng candidate selection, không tự thêm hạ tầng khác. |
| “Bean name fallback biến `@Autowired` thành `@Resource`.” | Semantics khác; `@Autowired` vẫn lọc type trước. |
| “Thứ tự quét package xác định implementation được chọn.” | Không dựa vào thứ tự scan; dùng rule rõ. |

### Chẩn đoán khi ứng dụng không tạo được context

- `NoSuchBeanDefinitionException` hoặc `UnsatisfiedDependencyException`: có thể thiếu type/qualifier match. Kiểm tra bean definitions, component scanning và `@Bean` names.
- `NoUniqueBeanDefinitionException`: sau filtering vẫn nhiều candidates cho **single-valued injection point**; cần qualifier cụ thể hơn, một primary phù hợp hoặc thay kiến trúc injection.
- `BeanCreationException`: bean candidate phù hợp nhưng constructor/factory/initialization thất bại; `@Qualifier` không tự nuốt lỗi.
- Sai import `Qualifier`: kiểm tra package `org.springframework.beans.factory.annotation.Qualifier`, không copy cú pháp String sang `jakarta.inject.Qualifier`.
- Lỗi thiếu nguyên nhóm sau khi cấu hình `@Qualifier("fast") List<Port>`: kiểm tra bean thực sự mang metadata fast, tên/aliases và cơ chế injection đúng.

**Cách debug hiệu quả:** trước tiên liệt kê candidates cùng interface; sau đó xem từng metadata qualifier và injection point; cuối cùng xác định target cần một bean hay một nhóm. Đừng xử lý lỗi mơ hồ bằng cách đánh dấu mọi implementation là primary.

## 11. Bài tập có đáp án

**1.** Spring có hai `DeliveryPort` mang qualifier fast. Inject `@Qualifier("fast") DeliveryPort` có chắc chắn chọn một không?

**Đáp án:** Không. Nếu sau lọc còn hai ứng viên và không có thêm quy tắc chọn, single-valued injection có thể mơ hồ.

**2.** Với cùng cấu hình, inject `@Qualifier("fast") List<DeliveryPort>` nhận gì?

**Đáp án:** Cả hai bean fast, khi chúng phù hợp type và metadata.

**3.** Một bean `Formatter` mang qualifier fast có vào `List<DeliveryPort>` với qualifier fast không?

**Đáp án:** Không, nếu Formatter không phải một DeliveryPort. Qualifier lọc **sau type matching**, không thay type.

**4.** Nếu `slowC` là `@Primary` nhưng injection yêu cầu qualifier fast, slowC có được chọn không?

**Đáp án:** Không chỉ vì primary; nó không thỏa qualifier fast.

**5.** Nếu gắn `@Primary` lên fastA nhưng giữ hai bean fast, `List<DeliveryPort>` qualifier fast còn fastB không?

**Đáp án:** Có, primary dùng để ưu tiên một candidate khi injection đơn, không tự loại bean khác khỏi group.

**6.** `@Qualifier("fastA")` có thể match bean tên fastA chưa gắn qualifier cùng tên không?

**Đáp án:** Trong Spring có bean name fallback trong tập beans type-compatible; nhưng đó vẫn là narrowing theo type, không phải lookup bean ID tuyệt đối.

**7.** Tại sao ví dụ runnable sắp xếp tên trước khi in?

**Đáp án:** Vì không muốn output phụ thuộc thứ tự Spring trả ứng viên; sort bảo đảm `[fast-a, fast-b]`.

**8.** Trong ví dụ, vì sao `FAST_COUNT 2` nhưng `TYPE_COUNT 3`?

**Đáp án:** TYPE_COUNT đếm cả ba bean đúng type; FAST_COUNT đếm các bean còn lại sau qualifier fast.

**9.** Vì sao không dùng `@Resource` thay thế hoàn toàn `@Qualifier`?

**Đáp án:** `@Resource` có semantics và injection targets khác, thường nhấn mạnh tên, không phải công cụ thay thế trực tiếp qualifier lọc groups trong constructor hay multi-arg method.

**10.** Một qualifier string thay đổi nhiều lần, làm sao giảm rủi ro typo?

**Đáp án:** Có thể định nghĩa custom meta-annotated qualifier như `@Fast` hoặc `@Region`, và dùng nhất quán trên candidate/injection point.

## 12. Checklist áp dụng

- [ ] Hiểu `@Qualifier` là điều kiện **lọc trong tập bean đúng type**.
- [ ] Biết rõ injection point cần **một bean** hay **collection**.
- [ ] Bean candidates đã được đăng ký bằng `@Bean`, component scan hoặc registry.
- [ ] Đã gắn qualifier metadata cho bean ứng viên phù hợp.
- [ ] `@Qualifier` được dùng đúng package Spring khi viết dạng String.
- [ ] Mỗi injection parameter có type/qualifier rõ ràng nếu nhiều beans.
- [ ] Không tự cho rằng một qualifier được dùng tối đa một lần.
- [ ] Không dựa vào thứ tự khai báo để chọn một ứng viên.
- [ ] Hiểu sự khác nhau giữa `@Primary` và qualifier filtering.
- [ ] Chỉ sử dụng bean name fallback khi đã hiểu điều kiện matching.
- [ ] Không đánh đồng Spring `@Qualifier` với Jakarta `@Qualifier`.
- [ ] Khi tạo custom qualifier, sử dụng runtime retention và target phù hợp.
- [ ] Test một ứng viên, nhiều ứng viên cùng qualifier và trường hợp thiếu.
- [ ] Nếu inject danh sách/map, kiểm soát order hoặc sort nếu output cần xác định.
- [ ] Giữ lựa chọn runtime theo business data trong service/router, không giấu ở annotation.
- [ ] Không dùng `@Qualifier` như giải pháp cho lỗi transaction, lifecycle hay concurrency.

### Nguồn tham khảo chính thức

- [Spring — Fine-tuning Annotation-based Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html)
- [Spring — Using `@Autowired`](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html)
- [Spring — Using Generics as Autowiring Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/generics-as-qualifiers.html)

**Kết luận:** `@Qualifier` của Spring thu hẹp **những bean cùng khớp kiểu** dựa trên nhãn hoặc metadata cần thiết. Nếu cần một bean thì lọc xong vẫn phải còn ứng viên duy nhất (hoặc có rule chọn thêm); nếu cần `List`/`Set`, nhiều bean mang cùng qualifier có thể được nhận đồng thời. Đây là điểm khác biệt giữa **lọc tập ứng viên** và **lookup chính xác một bean name**, và là cơ sở để thiết kế DI dễ mở rộng mà không lựa chọn implementation theo thứ tự ngẫu nhiên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="candidate-selection-005">Tạo annotation qualifier theo ý nghĩa nghiệp vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo annotation qualifier theo ý nghĩa nghiệp vụ — Spring Framework Core Container

## 1. Bài toán: chọn đúng bean theo ý nghĩa, không theo tên tình cờ

Một hệ thống gửi thông báo có nhiều bean cùng thực thi interface `Sender`:

- Kênh **EMAIL** cho thị trường **VN**.
- Kênh **EMAIL** cho thị trường **GLOBAL**.
- Kênh **SMS** cho thị trường **VN**.

Nếu một service chỉ có constructor:

~~~java
public NotificationService(Sender sender) {
    this.sender = sender;
}
~~~

Spring thấy ba bean cùng tương thích kiểu `Sender`. Nếu không có dấu hiệu chọn duy nhất, dependency một đối tượng này có thể gặp `NoUniqueBeanDefinitionException`. Đánh dấu tùy tiện một bean `@Primary` sẽ tạo mặc định toàn cục, nhưng không biểu đạt rằng **service ở thị trường VN cần EMAIL**, còn service khác cần **SMS**.

Một phương án có nghĩa nghiệp vụ là **tự tạo qualifier annotations**:

~~~java
public NotificationService(
        @ChannelTag(Channel.EMAIL) @MarketTag("VN") Sender sender) {
    this.sender = sender;
}
~~~

Đây là **snippet minh họa**, chưa có định nghĩa annotation/type; project runnable đầy đủ ở mục 7.

Hai chú giải ở injection point diễn đạt: “Trong các bean thuộc kiểu `Sender`, chọn bean có **kênh EMAIL** và **thị trường VN**”. Ý định vẫn rõ khi người phát triển đổi tên method `@Bean` hoặc implementation class.

**Mục tiêu học tập:**

1. Hiểu qualifier là **metadata lọc candidate theo kiểu**, không tự tạo bean và không nhất thiết trỏ tới một bean name duy nhất.
2. Tạo annotation nghiệp vụ với `@Qualifier` làm **meta-annotation**, `@Retention(RUNTIME)` và `@Target` đúng vị trí.
3. Gắn custom qualifier trên **bean declaration** (`@Bean` method hoặc component class) và trên **injection point** (constructor/field/method parameter).
4. Dùng **nhiều qualifier đồng thời** để diễn đạt các điều kiện kênh/thị trường; hoặc dùng **một annotation nhiều attributes**.
5. Hiểu quy tắc “bean phải khớp các attribute đã yêu cầu”, không có logic wildcard/OR tự phát.
6. Biết áp dụng qualifier lên `List<Sender>` để lọc **nhiều bean cùng kênh**, và dùng `@Order` để sắp collection.
7. Phân biệt `@Primary`, `@Fallback`, `@Named`, `@Resource`, generic qualifiers và lựa chọn qua bean name.
8. Chạy ví dụ **Java 17+ / Spring Framework 6.2.19** với Maven và `javac`/`java`, đối chiếu stdout ổn định.
9. Chẩn đoán thiếu candidate, nhiều candidate cùng qualifier, thiếu `RUNTIME`, đặt annotation sai vị trí hoặc quên đăng ký bean.

**Phạm vi:** custom annotation qualifiers phục vụ **chọn bean theo ngữ nghĩa nghiệp vụ** trong Spring Core Container. Không cần Spring Boot, MVC, persistence, mạng hay tích hợp gửi email/SMS thật.

## 2. Nền tảng: từ @Qualifier("beanName") tới một hợp đồng miền nghiệp vụ

Spring có sẵn:

~~~java
public BillingClient(@Qualifier("vnEmailSender") Sender sender) {
    this.sender = sender;
}
~~~

Với constructor injection, `@Qualifier("vnEmailSender")` có thể thu hẹp về bean tương ứng. Tuy nhiên tên `vnEmailSender` phản ánh cách **đăng ký** nhiều hơn một mô hình nghiệp vụ có thể phát triển.

Một nhu cầu khác:

- Với một consumer, cần sender **EMAIL**, không quan tâm thị trường cụ thể.
- Với consumer khác, cần sender phục vụ **VN**, bất kể EMAIL hay SMS.
- Với một consumer đơn trị, cần chính xác **EMAIL + VN**.

Dùng hai annotation có nghĩa:

~~~java
@ChannelTag(Channel.EMAIL)
@MarketTag("VN")
Sender sender
~~~

thay cho các chuỗi tên bean như `"vnEmailSender"`, `"sender1"`. Chúng diễn đạt **những đặc tính có thể giữ nguyên khi bean name thay đổi**.

| Cách biểu đạt | Ý nghĩa | Điểm đáng chú ý |
| --- | --- | --- |
| `Sender sender` | Yêu cầu một bean kiểu Sender | Có thể mơ hồ nếu nhiều candidates |
| `@Qualifier("vnEmailSender") Sender sender` | Thu hẹp theo qualifier/bean name | Dễ gắn với tên wiring cụ thể |
| `@ChannelTag(EMAIL) Sender sender` | Chỉ nhận Sender thuộc kênh EMAIL | Có thể vẫn nhiều thị trường |
| `@MarketTag("VN") Sender sender` | Chỉ nhận Sender thuộc thị trường VN | Có thể vẫn nhiều kênh |
| `@ChannelTag(EMAIL) @MarketTag("VN") Sender sender` | Sender thỏa **cả hai** điều kiện | Đơn trị nếu chỉ một candidate còn lại |
| `@ChannelTag(EMAIL) List<Sender> senders` | Toàn bộ Sender được đánh dấu EMAIL | Không yêu cầu qualifier duy nhất |

**Từ khóa:** qualifier **thu hẹp** tập ứng viên đã tương thích Java type. Annotation không thể khiến `PaymentGateway` được inject vào tham số `Sender` nếu class không tương thích kiểu. Nó cũng không biến tên bean thành object khi code ngoài Spring chỉ gọi `new`.

## 3. Một annotation tùy biến cần những thành phần nào?

Một qualifier tự viết cho kênh thông báo:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Qualifier;

enum Channel {
    EMAIL, SMS
}

@Target({
        ElementType.FIELD,
        ElementType.PARAMETER,
        ElementType.METHOD,
        ElementType.TYPE,
        ElementType.ANNOTATION_TYPE
})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface ChannelTag {
    Channel value();
}
~~~

**Đây là mẫu annotation minh họa**, không phải toàn bộ file Java: một file Java không thể vừa có public annotation này vừa có public demo class khác. Ở mục 7, annotation được khai báo **nested trong một public class**, do đó không có lỗi nhiều public top-level types.

### Ba thành phần thiết yếu

**`@Qualifier`:** đây là meta-annotation Spring nhận biết, đặt **trên khai báo annotation của bạn**. Nó nói rằng `@ChannelTag` phải được xem như một qualifier annotation khi Spring chọn ứng viên. Không cần tự viết `BeanPostProcessor` chỉ để thực hiện custom qualifier cơ bản.

**`@Retention(RetentionPolicy.RUNTIME)`:** bảo đảm annotation tồn tại ở runtime để Spring có thể đọc qua metadata/reflection. Nếu chỉ để mặc định `CLASS` hoặc đặt `SOURCE`, việc chọn candidate theo annotation ấy có thể không hoạt động đúng khi container chạy.

**`@Target(...)`:** chọn những vị trí được phép gắn. Trong demo:
- `PARAMETER` cho tham số constructor/factory method.
- `METHOD` để đánh dấu `@Bean` factory method.
- `TYPE` để gắn trên class/component khi dùng component scanning.
- `FIELD` cho field injection được Spring quản lý.
- `ANNOTATION_TYPE` nếu muốn xây một annotation tổng hợp thêm một tầng.

Không bắt buộc dùng tất cả targets; hãy chỉ khai báo đúng **vị trí bạn thật sự hỗ trợ**. Ví dụ chỉ khai báo `FIELD, PARAMETER` mà sau đó đặt qualifier lên `@Bean` method sẽ **không biên dịch được** vì `METHOD` không được phép.

**Member `value()`:** cho phép viết `@ChannelTag(Channel.EMAIL)`, truyền **giá trị enum có type rõ**. Nhờ enum, viết sai `Channel.EMIAL` sẽ bị Java compiler bắt ngay, khác với string literal `"EMIAL"` có thể lọt tới runtime.

### Một qualifier nữa cho thị trường

~~~java
@Target({
        ElementType.FIELD, ElementType.PARAMETER,
        ElementType.METHOD, ElementType.TYPE,
        ElementType.ANNOTATION_TYPE
})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface MarketTag {
    String value();
}
~~~

**Snippet minh họa** tương tự. Dùng String như `"VN"`/`"GLOBAL"` có thể đủ ở bài nhỏ; hệ thống lớn nên cân nhắc enum `Market.VN` hoặc value object để tránh typo và bảo vệ hợp đồng.

**Lưu ý về Java:** `ChannelTag` và `MarketTag` ở đây là **annotation types**, khác `ChannelTag.class` là kiểu Class của annotation và khác object bean. Spring **không tạo instance bean** chỉ vì nhìn thấy annotation.

## 4. Dùng qualifier ở cả hai đầu: bean definition và injection point

Đặt metadata trên những beans:

~~~java
@Configuration(proxyBeanMethods = false)
class SenderConfig {
    @Bean("vnEmailSender")
    @ChannelTag(Channel.EMAIL)
    @MarketTag("VN")
    Sender vnEmailSender() {
        return new InMemorySender("VN_EMAIL");
    }

    @Bean("globalEmailSender")
    @ChannelTag(Channel.EMAIL)
    @MarketTag("GLOBAL")
    Sender globalEmailSender() {
        return new InMemorySender("GLOBAL_EMAIL");
    }

    @Bean("vnSmsSender")
    @ChannelTag(Channel.SMS)
    @MarketTag("VN")
    Sender vnSmsSender() {
        return new InMemorySender("VN_SMS");
    }
}
~~~

**Snippet cấu hình**, cần các type/imports và implementation; ví dụ hoàn chỉnh ở mục 7.

Nhìn các bean như những hàng dữ liệu định danh:

| Bean name | Java type | Channel qualifier | Market qualifier |
| --- | --- | --- | --- |
| `vnEmailSender` | `Sender` | EMAIL | VN |
| `globalEmailSender` | `Sender` | EMAIL | GLOBAL |
| `vnSmsSender` | `Sender` | SMS | VN |

Khi consumer yêu cầu:

~~~java
public VnEmailClient(
        @ChannelTag(Channel.EMAIL)
        @MarketTag("VN")
        Sender sender) {
    this.sender = sender;
}
~~~

Spring lọc những beans hợp kiểu `Sender`, sau đó kiểm tra metadata qualifiers cần khớp; chỉ còn `vnEmailSender`. **Không có lý do dựa vào thứ tự method khai báo**.

### Hai annotations đồng thời là điều kiện kết hợp

`@ChannelTag(Channel.EMAIL) @MarketTag("VN")` yêu cầu ứng viên phù hợp **cả hai chiều**. Không phải Spring tự chọn EMAIL *hoặc* VN. Trong bảng:

- `vnEmailSender` khớp **cả hai** → hợp lệ.
- `globalEmailSender` khớp EMAIL, nhưng không khớp VN → loại.
- `vnSmsSender` khớp VN, nhưng không khớp EMAIL → loại.

Nếu bạn chỉ yêu cầu `@ChannelTag(Channel.EMAIL) Sender sender` ở điểm **đơn trị**, có **hai beans EMAIL**, nên vẫn có thể ambiguity. Qualifier không có nghĩa bảo đảm duy nhất; nó chỉ là điều kiện lọc.

**Nguyên tắc tốt:** annotation mô tả **đặc tính nghiệp vụ** của bean. Không nên đặt tên `@ExactBeanWithNameVnEmailSender` rồi coi đó là kiến trúc độc lập tên bean.

## 5. Đặt qualifier trên @Component hay trên @Bean?

Spring hỗ trợ metadata qualifier trên bean component đã được scan:

~~~java
@Component
@ChannelTag(Channel.EMAIL)
@MarketTag("VN")
public final class VietnamEmailSender implements Sender {
    // Implement Sender...
}
~~~

Với bean do `@Bean` method tạo, hãy đặt qualifier ở **factory method/bean definition** như mục 4:

~~~java
@Bean("vnEmailSender")
@ChannelTag(Channel.EMAIL)
@MarketTag("VN")
Sender sender() {
    return new InMemorySender("VN_EMAIL");
}
~~~

**Hai đoạn là snippets thay thế**, không chạy cùng demo. Phân biệt:

- Class-level qualifier có ích khi một class component tương ứng một bộ đặc tính nhất định.
- Method-level qualifier cho phép cùng một implementation class sinh **nhiều beans khác nhau**, mỗi definition có đặc tính kênh/thị trường riêng.
- Không chỉ đánh dấu class `InMemorySender` với `@ChannelTag(EMAIL)` rồi kỳ vọng hai bean SMS/VN và EMAIL/VN tạo từ class ấy tự có metadata khác nhau. Khi biến thể được cấu hình từ factory method, hãy gắn đúng annotation **trên từng method**.
- `@Component` và `@Bean` là các đường đăng ký; `@ChannelTag` một mình **không phải stereotype tạo bean**.
- Class có `@Component` nằm ngoài phạm vi component scan cũng chưa tự xuất hiện; ví dụ runnable gọi `context.register(...)` tường minh.

### Có cần @Autowired trên constructor?

Nếu component class chỉ có **một constructor**, Spring 4.3+ có thể tự chọn constructor đó mà không cần `@Autowired`:

~~~java
@Component
public final class VnEmailClient {
    private final Sender sender;

    public VnEmailClient(
            @ChannelTag(Channel.EMAIL)
            @MarketTag("VN")
            Sender sender) {
        this.sender = java.util.Objects.requireNonNull(sender);
    }
}
~~~

Snippet này cần các types và imports. Spring vẫn đọc **parameter annotations** của constructor duy nhất khi resolve collaborator. `@Autowired` không bắt buộc trong case đó; custom qualifiers không thay thế chức năng đăng ký bean.

Nếu có **nhiều constructors**, hãy làm rõ constructor được dùng bằng cơ chế `@Autowired`/definition phù hợp. Đừng cho rằng đặt qualifier trên một tham số tự buộc Spring chọn overload có tham số đó.

## 6. Bài học về dùng collection và @Order

Một service cần gửi thông điệp qua **tất cả kênh EMAIL**, không cần giới hạn một thị trường:

~~~java
public EmailBatch(
        @ChannelTag(Channel.EMAIL) List<Sender> senders) {
    this.senders = List.copyOf(senders);
}
~~~

Spring có thể inject **hai beans** phù hợp `@ChannelTag(Channel.EMAIL)`: VN_EMAIL và GLOBAL_EMAIL. Qualifier ở đây làm **bộ lọc collection**, không phải yêu cầu chọn một bean duy nhất.

Một service khác chỉ muốn tất cả senders của thị trường VN:

~~~java
public VnBatch(
        @MarketTag("VN") List<Sender> senders) {
    this.senders = List.copyOf(senders);
}
~~~

Nó nhận VN_EMAIL và VN_SMS.

**`@Order` có thể sắp các bean trong List/array injection**. Nếu annotations trên `@Bean` methods:

~~~java
@Bean
@Order(1)
@ChannelTag(Channel.EMAIL)
Sender firstEmail() { /* implementation */ }

@Bean
@Order(2)
@ChannelTag(Channel.EMAIL)
Sender secondEmail() { /* implementation */ }
~~~

đây là snippets **chỉ minh họa về metadata**; các methods cần trả Sender thực và phải tồn tại trong configuration class. Khi Spring inject `List<Sender>`, ordering có thể dựa trên `@Order`. **Không dùng `@Order(1)` để thay `@Primary` khi cần một bean đơn trị**.

**Cẩn thận:** `@Order` không tự bảo đảm singleton khởi tạo theo cùng thứ tự, và cũng không thay đổi điều kiện qualifier. Nó là thông tin sắp xếp kết quả collection. Khi nghiệp vụ cần thứ tự cứng, hãy gắn metadata/test rõ ràng; khi chỉ cần tập hợp không thứ tự, tránh tạo phụ thuộc không cần thiết vào thứ tự registration.

## 7. Ví dụ hoàn chỉnh Java 17+ / Spring Framework 6.2.19

**Ý tưởng:** giữ một interface `Sender`, ba beans cùng type nhưng được gắn nhãn nghiệp vụ. Ba consumers có hai custom qualifiers trong constructor, hai batch consumers lọc collection theo một chiều. Tất cả được Spring quản lý; không có network thực.

Chạy với **JDK 17+**, Maven và Spring Framework **6.2.19** (ghim cho khả năng tái lập, không khẳng định là bản Spring mới nhất). Không cần Spring Boot hoặc một dịch vụ gửi thật.

### 7.1. Cấu trúc project

~~~text
custom-business-qualifier/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── BusinessQualifierDemo.java
~~~

### 7.2. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>custom-business-qualifier</artifactId>
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
                    <mainClass>demo.BusinessQualifierDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Spring dependencies được Maven resolve. Nếu chạy trực tiếp `javac`/`java`, compile/runtime classpath phải có `spring-context` và các transitive JAR cần thiết.

### 7.3. src/main/java/demo/BusinessQualifierDemo.java

~~~java
package demo;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

public final class BusinessQualifierDemo {
    private BusinessQualifierDemo() {}

    public enum Channel {
        EMAIL, SMS
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({
        ElementType.FIELD, ElementType.PARAMETER,
        ElementType.METHOD, ElementType.TYPE,
        ElementType.ANNOTATION_TYPE
    })
    @Qualifier
    public @interface ChannelTag {
        Channel value();
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({
        ElementType.FIELD, ElementType.PARAMETER,
        ElementType.METHOD, ElementType.TYPE,
        ElementType.ANNOTATION_TYPE
    })
    @Qualifier
    public @interface MarketTag {
        String value();
    }

    public interface Sender {
        String send(String recipient);
    }

    public record InMemorySender(String label) implements Sender {
        public InMemorySender {
            Objects.requireNonNull(label);
        }

        @Override
        public String send(String recipient) {
            if (recipient == null || recipient.isBlank()) {
                throw new IllegalArgumentException("recipient required");
            }
            return label + ":" + recipient.strip();
        }
    }

    @Component
    public static final class VnEmailClient {
        private final Sender sender;

        public VnEmailClient(
                @ChannelTag(Channel.EMAIL)
                @MarketTag("VN")
                Sender sender) {
            this.sender = Objects.requireNonNull(sender);
        }

        public Sender sender() {
            return sender;
        }

        public String notify(String recipient) {
            return sender.send(recipient);
        }
    }

    @Component
    public static final class GlobalEmailClient {
        private final Sender sender;

        public GlobalEmailClient(
                @ChannelTag(Channel.EMAIL)
                @MarketTag("GLOBAL")
                Sender sender) {
            this.sender = Objects.requireNonNull(sender);
        }

        public String notify(String recipient) {
            return sender.send(recipient);
        }
    }

    @Component
    public static final class VnSmsClient {
        private final Sender sender;

        public VnSmsClient(
                @ChannelTag(Channel.SMS)
                @MarketTag("VN")
                Sender sender) {
            this.sender = Objects.requireNonNull(sender);
        }

        public String notify(String recipient) {
            return sender.send(recipient);
        }
    }

    @Component
    public static final class EmailBatch {
        private final List<Sender> senders;

        public EmailBatch(@ChannelTag(Channel.EMAIL)
                          List<Sender> senders) {
            this.senders = List.copyOf(senders);
        }

        public int size() {
            return senders.size();
        }

        public String sendAll(String recipient) {
            return senders.stream()
                    .map(sender -> sender.send(recipient))
                    .collect(Collectors.toList()).toString();
        }
    }

    @Component
    public static final class VnBatch {
        private final List<Sender> senders;

        public VnBatch(@MarketTag("VN") List<Sender> senders) {
            this.senders = List.copyOf(senders);
        }

        public String sendAll(String recipient) {
            return senders.stream()
                    .map(sender -> sender.send(recipient))
                    .toList().toString();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class SenderConfig {
        @Bean("vnEmailSender")
        @ChannelTag(Channel.EMAIL)
        @MarketTag("VN")
        @Order(1)
        public Sender vnEmailSender() {
            return new InMemorySender("VN_EMAIL");
        }

        @Bean("globalEmailSender")
        @ChannelTag(Channel.EMAIL)
        @MarketTag("GLOBAL")
        @Order(2)
        public Sender globalEmailSender() {
            return new InMemorySender("GLOBAL_EMAIL");
        }

        @Bean("vnSmsSender")
        @ChannelTag(Channel.SMS)
        @MarketTag("VN")
        @Order(3)
        public Sender vnSmsSender() {
            return new InMemorySender("VN_SMS");
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            // Explicit registration; no package scanning assumptions.
            context.register(SenderConfig.class,
                    VnEmailClient.class, GlobalEmailClient.class,
                    VnSmsClient.class, EmailBatch.class, VnBatch.class);
            context.refresh();

            VnEmailClient vnEmail =
                    context.getBean(VnEmailClient.class);
            GlobalEmailClient globalEmail =
                    context.getBean(GlobalEmailClient.class);
            VnSmsClient vnSms =
                    context.getBean(VnSmsClient.class);
            EmailBatch emailBatch = context.getBean(EmailBatch.class);
            VnBatch vnBatch = context.getBean(VnBatch.class);

            System.out.println("vnEmail=" +
                    vnEmail.notify(" Lan "));
            System.out.println("globalEmail=" +
                    globalEmail.notify(" Minh "));
            System.out.println("vnSms=" + vnSms.notify(" An "));
            System.out.println("emailBatch=" +
                    emailBatch.sendAll(" Mai "));
            System.out.println("vnBatch=" +
                    vnBatch.sendAll(" Hoa "));
            System.out.println("sameVnEmail=" +
                    (vnEmail.sender()
                     == context.getBean("vnEmailSender", Sender.class)));
            System.out.println("senderNames=" +
                    java.util.Arrays.stream(
                            context.getBeanNamesForType(Sender.class))
                            .sorted().toList());

            try {
                context.getBean(Sender.class);
                System.out.println("rawTypeLookup=UNEXPECTED");
            } catch (NoUniqueBeanDefinitionException expected) {
                System.out.println("rawTypeLookup=AMBIGUOUS");
            }

            System.out.println("emailBatchCount=" +
                    emailBatch.size());
        }
    }
}
~~~

**Vì sao ví dụ rõ và chạy độc lập:**

- Có đúng một public top-level class `BusinessQualifierDemo`; annotations, enum, records, config và consumer classes là nested types. Tên tệp/package khớp CLI.
- `@ChannelTag` và `@MarketTag` được khai báo `public @interface` nested, có `@Qualifier`, `@Retention(RUNTIME)`, `@Target` và `@Documented`.
- `SenderConfig` đăng ký ba beans có cùng declared return type `Sender` nhưng qualifier metadata khác nhau, qua ba `@Bean` methods riêng.
- Ba client components chỉ có một constructor, nên Spring nhận diện injection constructor không cần `@Autowired`, nhưng các parameter qualifiers vẫn được áp dụng.
- `EmailBatch`/`VnBatch` cũng được đăng ký trực tiếp. `@ChannelTag(EMAIL) List<Sender>` lọc hai bean EMAIL; `@MarketTag("VN") List<Sender>` lọc hai bean VN.
- `@Order(1)`/`@Order(2)`/`@Order(3)` trên từng `@Bean` method làm ordering rõ trong collections. Chỉ có ba labels nên stdout không phụ thuộc order của một HashMap.
- `java.util.Arrays.stream(...).sorted().toList()` sắp tên beans trước khi in, không phụ thuộc thứ tự registration không quan trọng.
- `InMemorySender` là record Java 17, thay sender thật bằng logic đơn giản; không có network, timer, random hoặc I/O ngoài stdout.
- Vì ba beans `Sender` tồn tại song song, `getBean(Sender.class)` không được cung cấp một qualifier/primary; demo bắt đúng `NoUniqueBeanDefinitionException`.
- Beans mặc định singleton trong context này; `sameVnEmail=true` chỉ chứng minh identity của bean được inject trong **một context**, không hứa hẹn về prototype/scoped proxies hoặc contexts khác.

### 7.4. Lệnh chạy bằng Maven

Trong thư mục `custom-business-qualifier`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

**Đúng chín dòng stdout do `main` in khi chương trình thành công:**

~~~text
vnEmail=VN_EMAIL:Lan
globalEmail=GLOBAL_EMAIL:Minh
vnSms=VN_SMS:An
emailBatch=[VN_EMAIL:Mai, GLOBAL_EMAIL:Mai]
vnBatch=[VN_EMAIL:Hoa, VN_SMS:Hoa]
sameVnEmail=true
senderNames=[globalEmailSender, vnEmailSender, vnSmsSender]
rawTypeLookup=AMBIGUOUS
emailBatchCount=2
~~~

Những dòng phiên bản Java/Maven, downloading artifacts hoặc log Spring/Maven (nếu xuất hiện) không thuộc chín dòng do `System.out.println` trong code trên phát ra.

### 7.5. Chạy trực tiếp bằng javac/java

Tạo classpath từ các dependencies do Maven resolve:

~~~bash
mvn -q dependency:build-classpath -Dmdep.outputFile=classpath.txt
~~~

Linux/macOS (bash/zsh):

~~~bash
mkdir -p out
javac --release 17 -encoding UTF-8 \
  -cp "$(cat classpath.txt)" \
  -d out src/main/java/demo/BusinessQualifierDemo.java
java -cp "out:$(cat classpath.txt)" demo.BusinessQualifierDemo
~~~

Windows PowerShell:

~~~powershell
$deps = (Get-Content -Raw classpath.txt).Trim()
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -encoding UTF-8 -cp "$deps" -d out src/main/java/demo/BusinessQualifierDemo.java
java -cp "out;$deps" demo.BusinessQualifierDemo
~~~

Trên Unix, classpath separator là `:`; trên Windows là `;`. Hai cách chạy cùng `main`, nên chín dòng do code in là như nhau. Nếu `javac` báo không thấy `org.springframework.*`, cần kiểm tra build/classpath trước khi kết luận custom annotation hoặc bean resolution có lỗi.

## 8. Giải thích kết quả: qualifier thực sự chọn theo nghiệp vụ

| Dòng stdout | Ý nghĩa |
| --- | --- |
| `vnEmail=VN_EMAIL:Lan` | Hai conditions EMAIL + VN chọn đúng sender |
| `globalEmail=GLOBAL_EMAIL:Minh` | EMAIL + GLOBAL chọn implementation khác |
| `vnSms=VN_SMS:An` | SMS + VN chọn bean VN_SMS |
| `emailBatch=[VN_EMAIL:Mai, GLOBAL_EMAIL:Mai]` | Collection lọc theo EMAIL, không giới hạn market |
| `vnBatch=[VN_EMAIL:Hoa, VN_SMS:Hoa]` | Collection lọc theo VN, không giới hạn channel |
| `sameVnEmail=true` | Bean qua custom qualifiers chính là bean Spring tên vnEmailSender |
| `senderNames=[...]` | Cả ba Sender beans đã được đăng ký song song |
| `rawTypeLookup=AMBIGUOUS` | Lookup bằng type trần cần một nhưng không có chọn lựa duy nhất |
| `emailBatchCount=2` | Bộ lọc EMAIL trả đúng hai candidates |

Tại client constructor, `@ChannelTag(Channel.EMAIL)` và `@MarketTag("VN")` là **injection-point metadata**. Trong `SenderConfig`, cùng các annotations ấy là **candidate metadata**. Spring đối chiếu chúng khi resolve một `Sender` dependency.

**Điểm chính:** không có client nào gọi `ApplicationContext.getBean("vnEmailSender")` bên trong business method. Spring resolve dependency ở **composition/wiring phase**; client chỉ gọi interface `Sender`.

## 9. Có thể gộp nhiều điều kiện thành một annotation nhiều attributes

Thay vì hai annotations:

~~~java
@ChannelTag(Channel.EMAIL)
@MarketTag("VN")
Sender sender
~~~

bạn có thể định nghĩa một domain qualifier có hai members:

~~~java
@Target({
    ElementType.FIELD, ElementType.PARAMETER,
    ElementType.METHOD, ElementType.TYPE
})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface RouteTag {
    Channel channel();
    String market();
}
~~~

Rồi áp dụng:

~~~java
@Bean
@RouteTag(channel = Channel.EMAIL, market = "VN")
Sender vnEmailSender() {
    return new InMemorySender("VN_EMAIL");
}

public VnClient(
        @RouteTag(channel = Channel.EMAIL, market = "VN")
        Sender sender) {
    this.sender = sender;
}
~~~

**Các đoạn này là snippets thay thế, không phải file thứ hai được chạy.** Trong thực tế, cần import/định nghĩa `Channel`, `Sender`, `InMemorySender`, class `VnClient` và `@Configuration` tương ứng. Trong một custom qualifier có nhiều attributes, bean candidate phải **khớp các attributes được yêu cầu**. Giá trị mặc định của attribute (nếu có) là một giá trị metadata thực, **không tự động là wildcard**.

### Chọn một annotation nhiều attributes hay nhiều annotations độc lập?

| Thiết kế | Phù hợp khi | Điểm cần tránh |
| --- | --- | --- |
| `@ChannelTag(EMAIL) @MarketTag("VN")` | Channel/market là các chiều độc lập, có thể cần lọc từng chiều hoặc kết hợp | Viết annotation thiếu một điều kiện khi consumer cần đủ hai |
| `@RouteTag(channel=EMAIL, market="VN")` | Cặp channel + market tạo một khái niệm nghiệp vụ có tính nguyên khối | Nghĩ bỏ attribute/đặt default sẽ tự có wildcard |
| `@Qualifier("vnEmailSender")` | Hệ thống nhỏ hoặc consumer cần đúng bean với tên ổn định | Khóa vào tên bean khó đổi |
| `@Primary` | Có một default hợp lệ trên phần lớn consumers | Không diễn đạt đúng lựa chọn riêng cho từng thị trường |
| Inject `List<Sender>` có filter qualifier | Consumer cần nhiều strategies cùng đặc tính | Đừng lấy phần tử đầu như cách ngẫu nhiên chọn một |

Nếu business dùng một taxonomy kênh hoặc thị trường chuẩn, ưu tiên **enum** cho giá trị hữu hạn. Nếu metadata có nhiều chiều có thể thay đổi độc lập, các qualifiers nhỏ giúp composition linh hoạt hơn.

## 10. Hợp đồng annotation tốt: đặt tên, kiểu, phạm vi

### 10.1. Đặt tên theo ngữ nghĩa

Tên `@ChannelTag` và `@MarketTag` nói lên đặc tính. Một annotation như `@EmailSenderBean42` hoặc `@BeanImplA` buộc business code biết cách đăng ký implementation. Qualifier nên mô tả **vai trò** thay vì internal bean identity.

Ví dụ các ý nghĩa có thể phù hợp theo domain:

- `@StorageTier(Storage.COLD)` / `@StorageTier(Storage.HOT)`.
- `@Region(Region.APAC)` / `@Region(Region.EMEA)`.
- `@DeliveryChannel(Channel.EMAIL)` / `@DeliveryChannel(Channel.SMS)`.
- `@ReadReplica` / `@PrimaryStore`, nếu thiết kế database routing thực sự cần phân biệt.

Những ví dụ trên chỉ là **tên minh họa**, không phải annotation types có sẵn trong Spring.

### 10.2. Làm rõ cardinality

Một qualifier **không nhất thiết duy nhất** trong toàn bộ context. Có thể có hai beans EMAIL vì hai markets. Với injection một Sender, hãy chắc rằng kết hợp qualifiers/candidate selection thu về đúng một. Với collection injection, việc có nhiều matches là đúng chủ đích.

Nếu một loại business contract yêu cầu **duy nhất một implementation**, cần test startup để phát hiện có hai bean cùng type/qualifiers sau khi thêm module.

### 10.3. Chọn dữ liệu có type

Sử dụng `Channel.EMAIL` thay vì string `"EMAIL"` giúp tránh typo ở compile time. Với `String market`, nên thống nhất hằng hoặc enum nếu danh mục hữu hạn; cẩn thận với casing/locale. Spring không tự hiểu `"VN"` tương đương `"vn"` trong một qualifier string.

### 10.4. Không tạo nghiệp vụ giả trong annotation

Annotations chứa metadata tĩnh; chúng không phải nơi tính quyết định phức tạp dựa trên user, request hoặc thời gian. Nếu khách hàng có thể chuyển kênh theo một input runtime, injection một Sender cố định ở startup có thể không đủ. Hãy inject **Map/list/provider** các strategies phù hợp rồi chọn bằng **domain routing policy** được test. Đừng dùng một annotation mà mong nó thay đổi cho từng HTTP request.

### 10.5. Scope và lifecycle không do qualifier quyết định

Custom qualifier chỉ tham gia **chọn ứng viên**. Nó không biến bean thành singleton/prototype, không làm object thread-safe, không tạo transaction hoặc đổi thời điểm khởi tạo. Chọn đúng bean còn phải kiểm tra lifecycle, scope và tính an toàn khi cùng instance được dùng nhiều luồng.

## 11. Spring xem custom qualifier metadata ở những vị trí nào?

**Component class:**

~~~java
@Component
@ChannelTag(Channel.EMAIL)
public class EmailSender implements Sender {
    // ...
}
~~~

**@Bean factory method:**

~~~java
@Bean
@ChannelTag(Channel.EMAIL)
Sender sender() {
    return new InMemorySender("EMAIL");
}
~~~

**Constructor parameter:**

~~~java
public Client(@ChannelTag(Channel.EMAIL) Sender sender) {
    this.sender = sender;
}
~~~

**Field injection:**

~~~java
@Autowired
@ChannelTag(Channel.EMAIL)
private Sender sender;
~~~

**Setter/method parameter:**

~~~java
@Autowired
public void setSender(
        @ChannelTag(Channel.EMAIL) Sender sender) {
    this.sender = sender;
}
~~~

**Snippets minh họa**, mỗi đoạn cần class/import và definitions tương ứng; không ghép chúng vào project chính vì có thể tạo thêm beans cùng qualifier ngoài ý định. Với collaborator bắt buộc, constructor injection nhìn rõ dependency hơn field/setter injection và dễ unit test bằng Java thuần.

**Không cần đặt `@Autowired` trên mỗi parameter**. Ở Spring, khi method/constructor được chọn làm injection point, metadata như custom qualifier được đọc từ parameter. Với một constructor duy nhất trên component, Spring có thể tự dùng constructor không cần `@Autowired`.

Nếu một class do Java `new` ở ngoài container, việc bạn gắn field `@ChannelTag` và `@Autowired` **không làm JVM tự inject**; đó vẫn là object unmanaged, phải được Spring xử lý hoặc có manual DI.

## 12. @Primary, @Fallback và qualifier tương tác ra sao?

| Cơ chế | Vai trò |
| --- | --- |
| `@Qualifier("...")` | Thu hẹp candidate theo metadata/bean name phù hợp |
| Custom `@ChannelTag`/`@MarketTag` | Thu hẹp theo nghĩa nghiệp vụ có type |
| `@Primary` | Chỉ một preferred candidate mặc định khi phù hợp |
| `@Fallback` | Hạ ưu tiên candidate trong Spring Framework 6.2+ |
| `@Order` | Sắp collection injection; không giải quyết ngầm một bean đơn trị |
| `@Bean(defaultCandidate=false)` | Chỉ dùng khi injection có chỉ dẫn thêm như qualifier phù hợp |
| `@Bean(autowireCandidate=false)` | Loại bean khỏi type-driven autowiring; không phải chỉ là ưu tiên thấp |

Tư duy quan trọng là **chọn trong tập type-compatible candidates có metadata phù hợp**, không nghĩ annotation nào cũng làm bean được đăng ký, tồn tại hoặc bị loại hoàn toàn.

**Ví dụ:** nếu `globalEmailSender` được đánh dấu `@Primary`, injection point đòi **EMAIL+VN** không được âm thầm nhận GLOBAL. Bean GLOBAL không thỏa `@MarketTag("VN")`. Ngược lại, injection chỉ yêu cầu EMAIL có thể nhận primary GLOBAL nếu quy tắc resolution áp dụng phù hợp.

**Không viết nhiều `@Primary` trên các beans cùng type** rồi hy vọng Spring giải quyết ambiguity. Hãy chọn một default có lý do nghiệp vụ hoặc dùng qualifier rõ ở consumer.

## 13. Hai lỗi chính sau khi tự tạo qualifier: không có bean hoặc còn nhiều beans

### Không có candidate phù hợp

Ví dụ consumer yêu cầu `@ChannelTag(Channel.SMS) @MarketTag("GLOBAL") Sender`, nhưng chỉ đăng ký các bean VN_SMS, VN_EMAIL, GLOBAL_EMAIL.

Spring thấy không có bean nào thỏa **cả SMS lẫn GLOBAL**, nên dependency bắt buộc fail. Có thể thấy `UnsatisfiedDependencyException` với nguyên nhân `NoSuchBeanDefinitionException` hoặc thông điệp “No qualifying bean” tùy đường tạo/phiên bản.

**Cách sửa:** đăng ký đúng bean SMS_GLOBAL nếu nghiệp vụ có nó, hoặc sửa ý định consumer nếu bạn yêu cầu sai. **Không** tự đổi thành `@Autowired(required=false)` chỉ để startup thành công khi dependency thực sự bắt buộc.

### Nhiều candidates cùng qualifier

Nếu bạn vô tình có hai bean cùng kiểu `Sender`, cùng `@ChannelTag(EMAIL)` và `@MarketTag("VN")`, injection đơn trị vẫn **có thể ambiguity**. Tên bean khác nhau không giúp qualifier trở nên duy nhất.

**Cách sửa:** xác định có thật cần hai implementations cho cùng route không. Nếu có, consumer cần một quy tắc chọn cụ thể hơn hoặc injection collection/provider. Nếu không, sửa registration để chỉ một bean cho hợp đồng đó. `@Primary` có thể là lựa chọn nếu thật sự có một default, nhưng cần test để tránh giấu lỗi cấu hình.

### Không có annotation vì quên @Retention(RUNTIME)

Một custom annotation không có runtime retention có thể xuất hiện trong source nhưng không được Spring thấy khi resolve metadata. Kiểm tra định nghĩa annotation, không chỉ kiểm tra injection point.

### @Target đặt quá hẹp

Khai báo `@Target(FIELD)` rồi đặt lên method `@Bean` sẽ lỗi compile. Khi annotation dùng cả trên bean definition và parameter, cần `METHOD` và `PARAMETER` (ngoài các targets thích hợp khác).

### Quên @Qualifier trên định nghĩa annotation

Một annotation tự định nghĩa nhưng không có meta-annotation Spring `@Qualifier` (hoặc chưa đăng ký bằng cách Spring hỗ trợ) thường chỉ là metadata Java tùy ý, **không tự trở thành autowiring qualifier**. Nếu muốn qualifier dựa trên Spring, đặt `@Qualifier` đúng chỗ.

Spring còn có `CustomAutowireConfigurer` cho những loại annotation bạn không thể sửa để gắn Spring `@Qualifier`; đó là công cụ infrastructure riêng, **không cần trong demo**.

### Chỉ gắn annotation trên injection point

Nếu bean candidate không có qualifier metadata tương ứng, Spring không thể dựa trên annotation nghiệp vụ đó để xác nhận route. Kiểm tra **cả nơi khai báo bean lẫn nơi inject**, nhất là khi bean sinh từ `@Bean` factory method.

### Sai bean registration và component scan

Class có `@Component` nhưng không được scan/register thì không thành bean. Class có `@ChannelTag` nhưng không phải stereotype hoặc không được khai báo qua `@Bean` cũng không tự thành bean. Đọc thực tế những beans đã có trong context.

### Lỗi type/generic metadata

Qualifier không biến một `Sender<Email>` thành `Sender<Sms>` nếu generic specialization không tương thích. Bean factory method khai báo type quá chung cũng có thể gây khó cho matching; giữ declared return type diễn đạt đầy đủ contract.

## 14. Chẩn đoán bằng phương pháp có hệ thống

| Dấu hiệu | Kiểm tra | Hướng sửa |
| --- | --- | --- |
| `NoUniqueBeanDefinitionException` | Nhiều Sender beans thỏa cùng qualifiers | Bổ sung điều kiện có nghĩa hoặc đổi consumer sang List/Map |
| `NoSuchBeanDefinitionException` | Không có candidate thỏa type + qualifier | Kiểm tra metadata cả bean và injection point |
| `UnsatisfiedDependencyException` | Constructor/factory parameter không resolve được | Đọc `Caused by`, bean names và qualifiers |
| Annotation bị bỏ qua | Thiếu `@Qualifier` meta hoặc `RUNTIME` | Sửa khai báo custom annotation |
| Annotation không thể gắn @Bean | Thiếu `METHOD` trong `@Target` | Bổ sung target đúng chỗ |
| Class có @Component nhưng không có bean | Không scan/register | Cấu hình component scan hoặc register tường minh |
| Chỉ một consumer được đúng bean, consumer khác sai | Khác qualifier, tên/giá trị string typo | So attribute values/casing tại từng injection point |
| List có hai beans nhưng chỉ muốn một | Injection point chọn List thay vì một Sender | Đặt lại cardinality đúng nghiệp vụ |
| Thứ tự List sai | Thiếu/nhầm `@Order` | Gắn order trên đúng @Bean method/component |
| String qualifier không tìm được match | Giá trị không đúng, khác chữ hoa thường | Dùng enum/hằng, thêm wiring test |
| Không có bean khi đổi profile | Bean definition bị loại theo profile/condition | Kiểm tra active profiles và bean registration |
| Dùng @Primary nhưng route sai | Tưởng primary được vượt qua qualifier | Làm rõ type/qualifier set và test identity |
| Test unit new class nhưng field @Autowired null | Object ngoài Spring | Dùng constructor injection hoặc context test cho wiring |
| `javac` thiếu Spring packages | Build/classpath thiếu dependency | Kiểm tra Maven POM và javac -cp |

**Quy trình debug đáng tin:**

1. Xác định **injection point** đang cần kiểu nào, có bao nhiêu qualifier và các attribute values là gì.
2. Liệt kê **bean definitions thực sự đăng ký**; kiểm tra tên, declared return type, scope, qualifier metadata.
3. Kiểm tra tương thích **type/generic** trước, rồi đối chiếu **từng qualifier**.
4. Kiểm tra `@Primary`/`@Fallback`/candidate flags và những điều kiện profile liên quan.
5. Nếu còn nhiều bean, hỏi có cần single candidate hay collection. Nếu không có bean, tìm nguyên nhân registration hoặc metadata mismatch.
6. Dùng Spring context test với ít bean nhất tái lập sự cố; không dựa vào thứ tự source code.
7. Xác minh **bean object identity và hành vi** thay vì chỉ nhìn context đã refresh không lỗi.

### Inspect nhanh trong context

~~~java
String[] names = context.getBeanNamesForType(Sender.class);
java.util.Arrays.sort(names);
System.out.println(java.util.Arrays.toString(names));
~~~

**Snippet** cho biết những bean names cùng type; cần chạy trong scope có context và interface `Sender`. **Sự tồn tại trong danh sách không có nghĩa bean thỏa mọi custom qualifier**. Muốn xác nhận điều kiện đúng, kiểm tra injection thực tế hoặc metadata ở definition phù hợp.

## 15. Kiểm thử để giữ hợp đồng nghiệp vụ

**Unit test Java thuần:** constructor của `VnEmailClient` nhận một `Sender` object. Nếu dùng fake:

~~~java
Sender fake = recipient -> "TEST:" + recipient;
VnEmailClient client = new VnEmailClient(fake);
System.out.println(client.notify("Lan")); // TEST:Lan
~~~

Đây là **snippet** khi có quyền truy cập nested types của demo. Annotation trên constructor parameter không có tác dụng tự chặn caller `new`; unit test Java truyền đúng dependency. Nó kiểm tra hành vi consumer, không xác minh Spring qualifier selection.

**Spring wiring test:** dùng config giống demo, kiểm tra:

- `VnEmailClient.sender() == context.getBean("vnEmailSender", Sender.class)`.
- `GlobalEmailClient` trả GLOBAL_EMAIL, `VnSmsClient` trả VN_SMS.
- `EmailBatch` chỉ chứa hai senders EMAIL và `VnBatch` chỉ chứa hai senders VN, đúng @Order.
- Lookup type trần `context.getBean(Sender.class)` thất bại vì ambiguity.
- Thêm một candidate sai market không làm đổi client đã có qualifier đủ chặt.
- Thiếu một candidate bắt buộc làm context fail trong **negative test riêng**.
- Đăng ký hai beans cùng route khiến single injection báo ambiguity nếu không có lựa chọn khác.

**Không dùng Java `assert` mặc định làm bảo đảm bắt buộc** nếu JVM không bật `-ea`; trong integration test, dùng test framework assertions hoặc if/throw. Không khóa test vào toàn bộ nguyên văn stack trace vì Spring có thể bọc exception/đổi wording giữa các versions.

### Khi nào custom qualifier là quá mức?

Một dự án có đúng **một** `Sender` implementation không cần ba annotation/enum khác nhau. Nếu chỉ có hai beans và một consumer, `@Qualifier("mainSender")` có thể đủ. Custom qualifiers hữu ích khi có **một khái niệm nghiệp vụ ổn định** được nhiều consumer dùng và cách đặt tên bean không biểu đạt hết ý định.

Tính "có giá trị" của abstraction nên được đánh giá qua khả năng hiểu code, tái sử dụng và giảm lỗi wiring, **không phải số annotations được viết**.

## 16. Bài tập tự kiểm tra

**Bài 1.** Ba beans: VN_EMAIL, GLOBAL_EMAIL, VN_SMS. Injection point `@ChannelTag(EMAIL) @MarketTag("VN") Sender` nhận gì?

**Đáp án:** Bean VN_EMAIL. Nó thỏa cả type Sender, channel EMAIL và market VN.

**Bài 2.** Chỉ có `@ChannelTag(EMAIL) Sender` ở một constructor cần đúng một object. Có chắc duy nhất không?

**Đáp án:** Không. Hai beans VN_EMAIL và GLOBAL_EMAIL cùng khớp channel, nên có thể gặp ambiguity.

**Bài 3.** `@ChannelTag(EMAIL) List<Sender>` nhận bao nhiêu phần tử?

**Đáp án:** Hai Senders EMAIL trong demo; qualifier là bộ lọc collection, không phải lệnh chọn một.

**Bài 4.** Vì sao cần `@Retention(RUNTIME)`?

**Đáp án:** Để metadata qualifier còn tồn tại khi Spring thực hiện bean candidate resolution tại runtime.

**Bài 5.** Đặt `@ChannelTag` chỉ trên injection parameter, không đặt trên các bean definitions. Có đủ không?

**Đáp án:** Không theo cấu hình demo; để Spring match theo channel, bean candidates phải có metadata qualifier tương ứng. Cần chú ý cả phía producer và consumer.

**Bài 6.** Dùng `@ChannelTag(EMAIL) @MarketTag("VN")` có nghĩa EMAIL hoặc VN không?

**Đáp án:** Không. Các qualifiers này được dùng kết hợp để ràng buộc candidate phải khớp điều kiện cần thiết, không tạo biểu thức OR tự động.

**Bài 7.** `@Order(1)` trên SMS có khiến single injection luôn nhận SMS không?

**Đáp án:** Không. @Order dùng sắp nhiều beans trong collection; single-valued injection cần quy tắc chọn riêng như qualifier/primary.

**Bài 8.** Có thể dùng cùng `InMemorySender` class cho nhiều bean routes khác nhau không?

**Đáp án:** Có. Đặt qualifier metadata trên từng `@Bean` factory method để phân biệt từng bean definition.

**Bài 9.** Nếu market name đổi từ VN thành VIETNAM ở producer nhưng injection point vẫn ghi VN, chuyện gì xảy ra?

**Đáp án:** Metadata không còn khớp, injection bắt buộc có thể fail. Dùng enum/constants và wiring test để giữ hợp đồng.

**Bài 10.** Khi một service cần chọn kênh theo request runtime, qualifier constructor cố định có giải quyết được không?

**Đáp án:** Không đủ. Hãy thiết kế router/strategy selection ở runtime, có thể inject nhiều Senders rồi ánh xạ theo domain key hợp lệ.

**Bài 11.** Custom qualifier có tự tạo một bean mới không?

**Đáp án:** Không. Nó chỉ là metadata giúp lọc những bean definitions đã được đăng ký qua cơ chế Spring phù hợp.

**Bài 12.** Bao giờ nên dùng một qualifier có nhiều attributes thay vì hai qualifiers độc lập?

**Đáp án:** Khi channel + market là một khái niệm đơn nhất luôn đi cùng nhau. Nếu từng chiều cần lọc collection độc lập, dùng hai qualifiers nhỏ thường linh hoạt hơn.

## 17. Checklist áp dụng

- [ ] Tên annotation nói rõ vai trò nghiệp vụ, không chỉ encode một bean name.
- [ ] Có `@Qualifier` trên **annotation declaration**.
- [ ] Có `@Retention(RetentionPolicy.RUNTIME)`.
- [ ] `@Target` cho phép `PARAMETER`, `METHOD`, `TYPE` hoặc `FIELD` đúng nhu cầu.
- [ ] Member `value()`/named attributes có type rõ, ưu tiên enum khi phù hợp.
- [ ] Gắn qualifier lên từng `@Bean` method hoặc component class cần phân biệt.
- [ ] Gắn đúng qualifier vào injection point, không chỉ producer hoặc chỉ consumer.
- [ ] Nếu nhiều qualifiers, xác định đây là **AND**, không phải OR.
- [ ] Với single-valued injection, xác minh chỉ một candidate hợp lệ.
- [ ] Với collection, xác minh đủ thành viên và thứ tự mong muốn.
- [ ] Không dùng `@Order` thay `@Qualifier`/`@Primary` cho single injection.
- [ ] Không dựa vào tên bean/parameter tình cờ nếu business semantics cần bền vững.
- [ ] Không đánh dấu `@Primary` bừa bãi để che hai bean trùng route.
- [ ] Không nhầm qualifier với stereotype, scope hoặc lifecycle.
- [ ] Có unit tests cho logic và integration tests cho đúng wiring.
- [ ] Chạy Java 17+ Maven/`javac`/`java` và đối chiếu **9 dòng stdout**.
- [ ] Với ứng dụng lớn, document taxonomy kênh/thị trường và sở hữu annotation.
- [ ] Khi version Spring đổi, kiểm tra chính sách candidate selection và tests trước khi deploy.

**Kết luận:** custom qualifier annotations là cách đưa **ngôn ngữ nghiệp vụ** vào ranh giới dependency injection. Thay vì chỉ yêu cầu một bean tên `vnEmailSender`, consumer có thể yêu cầu **Sender kênh EMAIL tại thị trường VN**. Spring lọc những bean tương thích type theo metadata qualifier, chọn một candidate hoặc trả collection các matches theo hợp đồng. Thiết kế annotation tốt cần đúng Java retention/target, producer/injection metadata nhất quán và test wiring để tránh lỗi khi ứng dụng thêm implementation mới.

### Tài liệu Spring chính thức

- [Spring Framework 6.2 — Fine-tuning Annotation-based Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-qualifiers.html)
- [Spring Framework 6.2 — Providing Qualifier Metadata with Annotations](https://docs.spring.io/spring-framework/reference/6.2/core/beans/classpath-scanning.html)
- [Spring Framework 6.2 — Using @Autowired](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html)
- [Spring API — Qualifier](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/annotation/Qualifier.html)
- [Spring Framework 6.2 — CustomAutowireConfigurer](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/custom-autowire-configurer.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="candidate-selection-006">Dùng tham số kiểu tổng quát làm điều kiện chọn bean</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng tham số kiểu tổng quát làm điều kiện chọn bean trong Spring

## 1. Vấn đề: cùng interface nhưng khác kiểu dữ liệu

Một interface generic có thể biểu diễn nhiều năng lực khác nhau:

~~~java
interface Store<T> {
    T read();
}
~~~

Một implementation `Store<String>` cung cấp chuỗi, trong khi `Store<Integer>` cung cấp số nguyên. Về **raw type**, cả hai đều là `Store`, nhưng chúng không mang cùng **kiểu tham số hóa**.

Nếu Spring đăng ký các bean có metadata kiểu:

~~~java
@Bean
Store<String> messageStore() {
    return new StringStore();
}

@Bean
Store<Integer> counterStore() {
    return new IntegerStore();
}
~~~

thì một injection point khai báo `Store<String>` có thể chọn `messageStore` mà không cần ghi `@Qualifier("messageStore")`; tương tự `Store<Integer>` có thể chọn `counterStore`. Spring gọi cơ chế này là **generics as autowiring qualifiers**: **tham số kiểu tổng quát là một dạng qualifier ngầm**. <https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/generics-as-qualifiers.html>

Hai method `@Bean` trên là **ví dụ minh họa**, còn thiếu các class; mã chạy độc lập có đầy đủ định nghĩa ở mục 5.

**Điểm mấu chốt:** Spring không chỉ nhìn tên interface `Store`, mà còn có thể xét kiểu `T` trong các metadata type được bảo tồn tại bean definition và injection point. Điều này khác với việc Java tạo một class runtime riêng cho từng `Store<T>`: Java vẫn sử dụng **type erasure**, nhưng chữ ký khai báo, generic interfaces và những nguồn metadata thích hợp vẫn có thể lưu thông tin generic để container phân giải.

~~~text
Bean declarations                 Injection point
--------------------------------------------------------------
Store<String> messageStore    --> Store<String>       chọn messageStore
Store<Integer> counterStore   --> Store<Integer>      chọn counterStore
Store<Double> priceStore      --> Store<Double>       chọn priceStore

Store<Integer> counterStore   --+
Store<Integer> auditStore     --+--> List<Store<Integer>> nhận cả hai
Store<String> messageStore    --+--> không vào nhóm Integer
~~~

**Lợi ích:** khi kiểu generic thực sự biểu thị vai trò, chúng giúp tránh phải đặt qualifier bằng String chỉ để phân biệt các nhóm khác nhau.

## 2. Generic qualifier được áp dụng ở bước nào?

Một mô hình tư duy hữu ích:

1. Spring xác định kiểu dependency từ constructor/field/method/factory method; ví dụ `Store<String>`.
2. Container tìm các bean có metadata **assignable và tương thích generic** với kiểu yêu cầu.
3. Những bộ lọc bổ sung như `@Qualifier`, tên bean, `@Primary` và quy tắc giải quyết khác được xét khi phù hợp.
4. Nếu injection yêu cầu **một bean**, container cần chọn được ứng viên cuối cùng; nếu yêu cầu **một collection**, Spring có thể tập hợp nhiều ứng viên tương thích.

Tài liệu Spring xác nhận việc dùng generic type như qualifier không chỉ áp dụng cho field mà còn cả **typed collections** như `List`, `Map` và arrays. Có thể dùng ở constructor arguments và `@Bean` factory method parameters phù hợp. <https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/generics-as-qualifiers.html>

### Bảng ví dụ

| Bean được khai báo | Kiểu metadata | Injection point | Có phù hợp về generic? |
| --- | --- | --- | --- |
| `words` | `Store<String>` | `Store<String>` | Có |
| `counter` | `Store<Integer>` | `Store<String>` | Không |
| `counter` | `Store<Integer>` | `Store<Integer>` | Có |
| `price` | `Store<Double>` | `List<Store<Integer>>` | Không |
| `auditCounter` | `Store<Integer>` | `List<Store<Integer>>` | Có |

Bảng giả định Spring có **đầy đủ metadata generic** của từng bean; với raw types, factory metadata thiếu kiểu hoặc proxy đặc biệt, phải kiểm tra behavior thực tế.

### Generic không phải một annotation `@Qualifier`

Đoạn này:

~~~java
@Autowired
private Store<String> messages;
~~~

có thể inject bean phù hợp chỉ nhờ kiểu `Store<String>`, ngay cả khi **không có `@Qualifier`** trên field. Spring hiểu yêu cầu thông qua generic signature.

Dùng `@Qualifier` vẫn hữu ích nếu còn **hai bean cùng kiểu đầy đủ `Store<String>`**. Generic chỉ tách được nhóm theo kiểu, không phân biệt ý nghĩa giữa hai đối tượng cùng nhóm.

## 3. Java generics, type erasure và lý do Spring vẫn làm được

### 3.1. `Store<String>` và `Store<Integer>` có cùng raw class

Java generics chủ yếu được kiểm tra ở compile time; nhiều tham số kiểu bị **xóa** ở biểu diễn bytecode của các tham chiếu. Điều đó có nghĩa Java không có hai class runtime độc lập tên `Store<String>` và `Store<Integer>`.

Ví dụ (chưa runnable độc lập vì thiếu `Store`):

~~~java
Store<String> text = ...;
Store<Integer> number = ...;

// Khi nhìn raw interface:
// text instanceof Store
// number instanceof Store
~~~

`instanceof Store<String>` không phải phép kiểm tra kiểu tham số hóa cụ thể thông thường được Java cho phép. Không thể giải quyết mọi generic bằng cách hỏi `someObject.getClass()` rồi đoán T từ một instance ở runtime.

### 3.2. Generic signatures có thể được giữ trong metadata

Tuy có erasure, Java reflection vẫn có thể đọc **chữ ký generic được khai báo** trên field, superclass, generic interface, method parameter/return type. Spring sử dụng cơ chế kiểu nâng cao như `ResolvableType` để làm việc với metadata tương ứng.

Ví dụ một `@Bean` method khai báo return type `Store<Integer>` giúp container biết ý định type của bean hơn nhiều so với một method chỉ khai báo raw type `Store`.

**Không hiểu nhầm:** Spring không suy ra rằng một raw `Store` là `Store<Integer>` chỉ vì instance chứa giá trị `123`. Nội dung dữ liệu runtime không phải hợp đồng type an toàn cho autowiring.

### 3.3. Java 17 thuần kiểm tra metadata erasure

Tạo file độc lập `GenericSignatureDemo.java`:

~~~java
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public final class GenericSignatureDemo {
    interface Store<T> {}

    Store<String> words;
    Store<Integer> numbers;

    public static void main(String[] args) throws Exception {
        Type a = GenericSignatureDemo.class
                .getDeclaredField("words").getGenericType();
        Type b = GenericSignatureDemo.class
                .getDeclaredField("numbers").getGenericType();

        ParameterizedType text = (ParameterizedType) a;
        ParameterizedType integer = (ParameterizedType) b;

        System.out.println("TEXT_ARG "
                + text.getActualTypeArguments()[0].getTypeName());
        System.out.println("NUMBER_ARG "
                + integer.getActualTypeArguments()[0].getTypeName());
        System.out.println("SAME_RAW "
                + text.getRawType().equals(integer.getRawType()));
    }
}
~~~

Biên dịch và chạy với **JDK 17+**, không dùng Spring:

~~~bash
javac --release 17 -encoding UTF-8 GenericSignatureDemo.java
java GenericSignatureDemo
~~~

Stdout:

~~~text
TEXT_ARG java.lang.String
NUMBER_ARG java.lang.Integer
SAME_RAW true
~~~

Hai field mang generic arguments khác nhau và `getGenericType()` phản ánh điều đó. Nhưng `getRawType()` giống nhau. Spring dựa trên metadata type phù hợp, không tự tạo một JVM class mới mỗi tham số type.

## 4. Injection một bean và injection nhiều bean theo generic

### 4.1. Một tham số đầy đủ

~~~java
public MessageService(Store<String> store) {
    this.store = store;
}
~~~

Đây là snippet minh họa cần khai báo service và `Store`. Nếu chỉ có **một `Store<String>`** được đăng ký, Spring có thể resolve tham số từ generic type.

### 4.2. Nếu có hai `Store<Integer>`?

Giả sử:

~~~java
@Bean
Store<Integer> primaryCounter() { ... }

@Bean
Store<Integer> secondaryCounter() { ... }
~~~

Khi yêu cầu một `Store<Integer>` duy nhất, generic filter vẫn để lại **hai ứng viên**. Nếu không có rule chọn bổ sung, có thể gặp `NoUniqueBeanDefinitionException`.

Điều này chứng minh **generic qualifier phân biệt loại tham số kiểu, không xác định bean ID duy nhất**. Có thể dùng thêm `@Qualifier("primaryCounter")` hoặc qualifier mang ý nghĩa như `@Qualifier("reporting")` trên injection point và candidate phù hợp.

### 4.3. Collection chỉ lấy các bean generic tương thích

~~~java
public Aggregator(List<Store<Integer>> integerStores) {
    this.integerStores = integerStores;
}
~~~

Đoạn minh họa này biểu diễn một nhóm. Với nhiều bean `Store<Integer>`, Spring có thể inject **tất cả** bean tương thích với generic `Integer`, nhưng không đưa `Store<String>` vào nhóm.

Spring cũng hỗ trợ kiểu injection `Map<String, Store<Integer>>`; key trong map thông thường là **bean names** và values là các bean `Store<Integer>` tương ứng. `List<Store<String>>` sẽ là tập khác.

### 4.4. Cách nhìn đúng với `@Primary` và `@Qualifier`

| Injection | Sau generic filter | Còn cần gì? |
| --- | --- | --- |
| `Store<String>` với một bean String | 1 bean | Không cần qualifier tên |
| `Store<Integer>` với hai bean Integer | 2 bean | Cần một rule chọn bean cho single dependency |
| `@Qualifier("reporting") Store<Integer>` | Những bean Integer có nhãn reporting | Một ứng viên cuối hoặc rule bổ sung |
| `List<Store<Integer>>` | Toàn bộ bean Integer phù hợp | Có thể inject cả nhóm |
| `Map<String, Store<Integer>>` | Toàn bộ bean Integer phù hợp | Keys là bean names |
| `Store<?>` | Kiểu wildcard rộng hơn | Có thể match nhiều candidate; không tự chọn duy nhất |

`@Primary` có thể ưu tiên một bean khi injection target cần **một object**, nhưng không có nghĩa `List<Store<Integer>>` tự co lại thành một phần tử. Nếu candidate được đánh dấu `@Primary` lại có generic type không phù hợp, primary không làm nó thỏa type yêu cầu.

## 5. Ví dụ đầy đủ: Spring Framework 6.2 và Java 17

Ví dụ dùng **một interface generic `Store<T>`**, một bean `Store<String>` và **hai bean `Store<Integer>`**. Một bean Integer được đánh dấu qualifier `reporting`; bean còn lại không. `Dashboard` nhận:

- `Store<String>`: một candidate nhờ generic type.
- `@Qualifier("reporting") Store<Integer>`: một candidate trong số hai bean cùng generic.
- `List<Store<Integer>>`: hai ứng viên Integer.
- `Map<String, Store<Integer>>`: hai entries với keys là bean names.
- `List<Store<String>>`: chỉ một ứng viên String.

### 5.1. Thư mục và Maven

Yêu cầu JDK 17+ và Maven; không cần Spring Boot, web server, database hoặc Java preview features.

~~~text
generic-qualifier-demo/
├── pom.xml
└── src/main/java/demo/GenericQualifierDemo.java
~~~

Tệp `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>generic-qualifier-demo</artifactId>
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
                    <mainClass>demo.GenericQualifierDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 5.2. File Java đầy đủ

Tạo `src/main/java/demo/GenericQualifierDemo.java`:

~~~java
package demo;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class GenericQualifierDemo {
    private GenericQualifierDemo() {}

    public interface Store<T> {
        String label();
        T read();
    }

    public record FixedStore<T>(String label, T value) implements Store<T> {
        @Override
        public T read() {
            return value;
        }
    }

    public static final class Dashboard {
        private final Store<String> message;
        private final Store<Integer> selectedCounter;
        private final List<Store<Integer>> counters;
        private final Map<String, Store<Integer>> countersByBeanName;
        private final List<Store<String>> messages;

        public Dashboard(Store<String> message,
                         Store<Integer> selectedCounter,
                         List<Store<Integer>> counters,
                         Map<String, Store<Integer>> countersByBeanName,
                         List<Store<String>> messages) {
            this.message = Objects.requireNonNull(message);
            this.selectedCounter = Objects.requireNonNull(selectedCounter);
            this.counters = List.copyOf(counters);
            this.countersByBeanName = Map.copyOf(countersByBeanName);
            this.messages = List.copyOf(messages);
        }

        public String messageValue() {
            return message.read();
        }

        public String selectedValue() {
            return selectedCounter.label() + "="
                    + selectedCounter.read();
        }

        public String counterValues() {
            return counters.stream()
                    .map(s -> s.label() + "=" + s.read())
                    .sorted()
                    .toList()
                    .toString();
        }

        public String counterNames() {
            return countersByBeanName.keySet().stream()
                    .sorted()
                    .toList()
                    .toString();
        }

        public int counterCount() {
            return counters.size();
        }

        public int messageCount() {
            return messages.size();
        }

        public int reportValue() {
            return countersByBeanName.get("reportCounter").read();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        public Store<String> welcomeStore() {
            return new FixedStore<>("welcome", "hello");
        }

        @Bean
        @Qualifier("reporting")
        public Store<Integer> reportCounter() {
            return new FixedStore<>("report", 17);
        }

        @Bean
        @Qualifier("archival")
        public Store<Integer> archiveCounter() {
            return new FixedStore<>("archive", 42);
        }

        // Spring resolves generic arguments of each factory parameter.
        @Bean
        public Dashboard dashboard(
                Store<String> stringStore,
                @Qualifier("reporting") Store<Integer> selected,
                List<Store<Integer>> integerStores,
                Map<String, Store<Integer>> integersByName,
                List<Store<String>> stringStores) {
            return new Dashboard(stringStore, selected, integerStores,
                    integersByName, stringStores);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            Dashboard dash = context.getBean(Dashboard.class);

            System.out.println("MESSAGE " + dash.messageValue());
            System.out.println("SELECTED " + dash.selectedValue());
            System.out.println("INT_COUNT " + dash.counterCount());
            System.out.println("INT_VALUES " + dash.counterValues());
            System.out.println("INT_NAMES " + dash.counterNames());
            System.out.println("STRING_COUNT " + dash.messageCount());
            System.out.println("REPORT_VALUE " + dash.reportValue());
            System.out.println("SAME_DASHBOARD "
                    + (dash == context.getBean(Dashboard.class)));
        }
    }
}
~~~

**Điều làm ví dụ đáng chú ý:** hai bean `reportCounter` và `archiveCounter` đều có return type khai báo rõ `Store<Integer>`, nên generic qualifier **không chọn được duy nhất** nếu chỉ nhìn type. `@Qualifier("reporting")` đặt trên một factory method và tham số `selected` giúp chọn một bean báo cáo; còn `List` và `Map` **vẫn nhận cả hai `Store<Integer>`**. Bean `welcomeStore` mang type `Store<String>` nên chỉ vào nhóm String.

### 5.3. Chạy qua Maven

Từ thư mục `generic-qualifier-demo`:

~~~bash
mvn -version
mvn -q compile exec:java
~~~

Nếu chạy trực tiếp bằng `javac/java`, cần classpath chứa cả Spring và dependencies tương ứng; Maven quản lý classpath cho ví dụ. `maven.compiler.release=17` yêu cầu biên dịch tương thích Java 17.

**Stdout do chính chương trình in theo thứ tự:**

~~~text
MESSAGE hello
SELECTED report=17
INT_COUNT 2
INT_VALUES [archive=42, report=17]
INT_NAMES [archiveCounter, reportCounter]
STRING_COUNT 1
REPORT_VALUE 17
SAME_DASHBOARD true
~~~

Maven có thể in thêm thông tin tải dependencies và log riêng. Khối trên mô tả **System.out.println của chương trình**, không khẳng định toàn bộ terminal chỉ có tám dòng.

### 5.4. Giải thích stdout

1. `Store<String> stringStore` tại `dashboard(...)` được match tới `welcomeStore`. `reportCounter` và `archiveCounter` không phù hợp với tham số type String nên `MESSAGE hello`.
2. `@Qualifier("reporting") Store<Integer> selected` chọn bean `reportCounter`, nên `SELECTED report=17`.
3. `List<Store<Integer>>` nhận hai beans integer, không nhận `Store<String>`. Kết quả `INT_COUNT 2`.
4. `counterValues()` biến từng store thành chuỗi `label=value` rồi `sorted()`, cho `[archive=42, report=17]` ổn định, không phụ thuộc thứ tự đăng ký bean.
5. `Map<String, Store<Integer>>` mang hai keys bean name `archiveCounter` và `reportCounter`. `sorted()` trên keys cho thứ tự như dòng `INT_NAMES`.
6. `List<Store<String>>` có một bean nên `STRING_COUNT 1`.
7. Truy vấn map theo `reportCounter` cho `REPORT_VALUE 17`.
8. `Dashboard` là singleton mặc định trong cùng context, nên lookup lặp lại trả cùng reference `SAME_DASHBOARD true`.

**Không có stdout trong constructor/factory methods** của các bean, giúp output ổn định và không dựa vào thứ tự Spring tạo singleton.

## 6. Những giới hạn và lỗi dễ gặp

### 6.1. Khai báo raw generic làm mất thông tin chọn bean

So sánh:

~~~java
@Bean
Store<Integer> integerStore() {
    return new FixedStore<>("int", 5);
}
~~~

với:

~~~java
@Bean
@SuppressWarnings("rawtypes")
Store rawStore() {
    return new FixedStore<Integer>("int", 5);
}
~~~

Đây là **hai snippet minh họa riêng**, giả định đã có `Store` và `FixedStore`. Method thứ hai **khai báo raw return type** nên bean factory method metadata không thể hiện `<Integer>`. Tùy class, target type, bean factory inference và proxy, Spring có thể không tìm được generic argument chính xác hoặc có đường matching rộng hơn mong muốn.

Không kết luận mọi bean raw chắc chắn bị loại/luôn được chọn: behavior cần kiểm chứng theo bean definition thực tế. Nhưng best practice là **giữ generic signature tường minh** trên `@Bean` method và injection point.

### 6.2. Type erasure không bị “vô hiệu hóa”

Spring dùng metadata của chữ ký kiểu đã khai báo; nó không cần thử từng value bên trong store để đoán `T`. Nếu một factory tạo `Store<Integer>` nhưng khai báo return type `Object`/raw `Store`, container không nhất thiết suy ra chính xác từ contents.

Đừng viết business code cho rằng `getClass()` của một instance `FixedStore<Integer>` luôn cho biết T là Integer. Class generic `FixedStore<T>` có thể dùng chung runtime class cho nhiều instantiations.

### 6.3. Wildcard, raw, type variables và nested generics

Ví dụ:

~~~java
Store<?> anyStore;
Store<? extends Number> numericStore;
List<Store<Integer>> integerStores;
Store<List<String>> nestedStringLists;
~~~

Đây là các field minh họa, không phải injection points của demo. `Store<?>` thường rộng hơn `Store<String>`, có thể để lại nhiều ứng viên. `Store<? extends Number>` và nested generic type có quan hệ assignability/variance cần metadata phù hợp; **không đoán rằng Spring luôn chọn duy nhất**. Với type variables `Store<T>` ở class generic, việc resolve T còn phụ thuộc Spring biết được binding của class bean đang xét hay không.

Một cấu hình nên tránh wildcard/raw injection nếu tính chính xác của bean selection phụ thuộc một `T` cụ thể.

### 6.4. Java generics bất biến ở compile time

`Store<Integer>` **không phải subtype** của `Store<Number>` chỉ vì `Integer extends Number`. Đây là invariance của Java generics, không phải sự cố của Spring. Vì thế không dùng `Store<Number>` để ngầm yêu cầu `Store<Integer>` nếu không có typing phù hợp.

### 6.5. Generic qualifier không giải quyết mọi ambiguity

Nếu có hai bean `Store<Integer>`, injection `Store<Integer>` đơn vẫn có thể báo `NoUniqueBeanDefinitionException`. Có thể kết hợp:

- `@Qualifier("reporting")` để chọn vai trò.
- `@Primary` nếu một bean thực sự là ưu tiên mặc định trong tập phù hợp.
- Bean name hoặc custom qualifier khi dùng tên/metadata có chủ đích.
- `List<Store<Integer>>` khi nghiệp vụ cần **cả nhóm**, không cần duy nhất một bean.
- Factory/strategy theo dữ liệu runtime nếu lựa chọn thay đổi theo từng request.

Không tự thêm `@Primary` cho mọi implementation hoặc chuyển tất cả sang `ObjectProvider` để lẩn tránh lựa chọn rõ.

### 6.6. Annotation và component registration vẫn là điều kiện

Một class triển khai `Store<String>` không tự xuất hiện trong Spring context vì Java generic interface của nó. Phải đăng ký bean qua `@Bean`, component scan hoặc cách phù hợp. Và nếu type metadata bị ẩn bởi proxy/factory, cần đánh giá đường runtime thật, không suy luận chỉ từ interface Java trong IDE.

## 7. Generics vs `@Qualifier` vs bean name: chọn cách nào?

| Cách | Thể hiện | Phù hợp khi |
| --- | --- | --- |
| Generic type | `Store<String>` vs `Store<Integer>` | Khác **loại dữ liệu/hợp đồng kiểu** |
| Spring `@Qualifier("reporting")` | Chỉ các candidates cùng nhãn | Nhiều implementation **cùng generic type** nhưng khác vai trò |
| `@Primary` | Ưu tiên khi cần một bean | Có lựa chọn mặc định trong số candidates hợp lệ |
| Bean name | `reportCounter` | Cần tham chiếu tên cụ thể, hiểu bean-name matching semantics |
| Collection typed | `List<Store<Integer>>` | Cần nhiều beans cùng kiểu generic |
| Provider/factory | `ObjectProvider<Store<Integer>>` | Cần lookup trì hoãn hoặc theo nhu cầu |
| Strategy ở nghiệp vụ | `Map<Key, Handler>` hoặc router | Chọn theo request/runtime policy, không phải lúc wiring |

**Không đồng nhất trách nhiệm:** generic parameter mô tả **kiểu** của một dependency; qualifier annotation mô tả thêm **đặc tính** của bean. Hai cơ chế có thể phối hợp, không loại trừ nhau.

### Tránh phụ thuộc tên Java parameter một cách vô ý

Trong Spring hiện đại, khi có nhiều bean cùng kiểu, tên field/parameter có thể đóng vai trò matching cuối trong một số điều kiện. Nhưng không nên dựa vào tên source code như `reportCounter` để thay thế một qualifier có ý nghĩa, đặc biệt khi project chưa bật `-parameters` hoặc cần ổn định qua refactor.

Nếu yêu cầu thật sự là “store phục vụ reporting”, hãy ghi `@Qualifier("reporting")`. Nếu chỉ yêu cầu “store số nguyên”, hãy giữ generic `Store<Integer>` và bảo đảm số lượng ứng viên đúng.

## 8. Kiểm thử và gỡ lỗi

### Kiểm thử generic type resolution

Một integration test cho container nên kiểm tra:

1. `Store<String>` chọn đúng string bean.
2. `List<Store<Integer>>` chỉ có những integer beans.
3. `Map<String, Store<Integer>>` chứa đúng bean names.
4. `@Qualifier("reporting") Store<Integer>` chọn đúng báo cáo.
5. Trường hợp cố tình inject một `Store<Integer>` không qualifier với hai candidates báo lỗi hoặc đòi rule chọn, thay vì ứng dụng vô tình chọn một bean.

Chương trình demo đã cung cấp output kiểm tra bốn điều đầu. Một số test negative nên chạy trong một test context riêng, tránh làm ứng dụng chính fail startup.

### Gỡ lỗi theo thứ tự

- **Bước 1 — xác nhận bean được đăng ký:** kiểm tra `@Bean`, component scan, profiles, conditions.
- **Bước 2 — so type đầy đủ:** `Store<String>` khác `Store<Integer>`, không chỉ nhìn raw `Store`.
- **Bước 3 — xem declared signatures:** bean factory method có khai báo parameterized return type không? Class generic có triển khai interface với parameter cụ thể không?
- **Bước 4 — kiểm tra qualifiers/primary:** sau generic filtering còn bao nhiêu candidates?
- **Bước 5 — kiểm tra injection target:** là single bean hay `List`/`Map`? Loại target thay đổi cách tổng hợp kết quả.
- **Bước 6 — đọc nguyên nhân gốc:** `NoSuchBeanDefinitionException` khi thiếu candidate, `NoUniqueBeanDefinitionException` khi dư candidate phù hợp, `BeanCreationException` khi factory/init của candidate bị lỗi.

**Đừng sửa bằng cách cast raw bean về `Store<String>`** chỉ để Spring không báo lỗi: cast không tạo được type safety và có thể gây `ClassCastException` khi dữ liệu được sử dụng.

## 9. Bài tập có đáp án

**Bài 1.** Có bean `Store<String>` và `Store<Integer>`. Injection `Store<String>` có nhất thiết cần `@Qualifier("stringStore")` không?

**Đáp án:** Không, nếu generic metadata đầy đủ và chỉ có một candidate phù hợp `Store<String>`. Spring có thể dùng generic argument làm implicit qualifier.

**Bài 2.** Hai bean cùng type `Store<Integer>`. Generic qualifier có chọn được một bean duy nhất không?

**Đáp án:** Không. Generic filter vẫn để lại cả hai; cần một rule chọn bổ sung nếu injection target chỉ cần một object.

**Bài 3.** Injection `List<Store<Integer>>` có nhận `Store<String>` không?

**Đáp án:** Không theo generic matching bình thường khi metadata type được bảo tồn. Nó chỉ lấy những store phù hợp `Integer`.

**Bài 4.** Vì sao `Map<String, Store<Integer>>` có keys là bean names?

**Đáp án:** Spring hỗ trợ autowiring typed maps theo bean names làm keys (String) và matching beans làm values.

**Bài 5.** Vì sao `Store<Integer>` không là subtype của `Store<Number>`?

**Đáp án:** Generic type parameters bất biến trong Java. Quan hệ subtype giữa Integer và Number không tự chuyển qua `Store<T>`.

**Bài 6.** Nếu `@Bean Store rawStore()` trả object chứa số Integer, Spring có chắc biết bean đó là `Store<Integer>` không?

**Đáp án:** Không chắc. Raw return type không bảo tồn T trên factory method signature; metadata target có thể thiếu hoặc phụ thuộc cách Spring biết actual bean class.

**Bài 7.** `Store<?>` có phân biệt rõ String và Integer như `Store<String>` không?

**Đáp án:** Không. Wildcard rộng hơn, nên có thể có nhiều bean compatible và cần quy tắc lựa chọn rõ.

**Bài 8.** Khi có một `Store<String>` và hai `Store<Integer>`, nên dùng gì nếu muốn tất cả integer stores?

**Đáp án:** `List<Store<Integer>>` hoặc typed `Map<String, Store<Integer>>`. Không cần chọn một bean bằng `@Primary` chỉ để có cả nhóm.

**Bài 9.** Nếu muốn trong hai Integer beans chỉ lấy reporting store cho một injection point?

**Đáp án:** Có thể kết hợp `Store<Integer>` với `@Qualifier("reporting")` ở injection point và bean candidate.

**Bài 10.** Vì sao `new FixedStore<>("x", 17).getClass()` không tự cho biết đây là `FixedStore<Integer>`?

**Đáp án:** Type erasure làm nhiều instantiations dùng chung runtime class. Metadata ở deklarations là nguồn chính xác hơn để Spring suy luận generic.

**Bài 11.** Có thể biến raw `Store` thành bean có type an toàn chỉ bằng annotation `@Primary` không?

**Đáp án:** Không. `@Primary` là quy tắc ưu tiên lựa chọn, không khôi phục generic argument bị thiếu.

## 10. Checklist thiết kế

- [ ] Dùng generic interface khi `T` thật sự diễn đạt hợp đồng type có giá trị.
- [ ] Khai báo return type `@Bean` tường minh như `Store<String>` thay vì raw `Store`.
- [ ] Giữ type parameters rõ ở constructor, field, method và collection injection points.
- [ ] Đăng ký bean đầy đủ, không nghĩ generic class tự được Spring scan.
- [ ] Kiểm tra số candidate **sau khi** lọc generic.
- [ ] Nếu cần một bean và còn nhiều candidates cùng generic, thêm qualifier/primary có chủ đích.
- [ ] Nếu cần tất cả beans `Store<Integer>`, dùng `List`/`Map` được tham số hóa.
- [ ] Không dựa vào type erasure để phân biệt actual T qua `getClass()`.
- [ ] Tránh raw/wildcard signatures nếu cần chính xác trong bean selection.
- [ ] Kiểm tra nested generics/type variables và metadata proxy khi cấu hình phức tạp.
- [ ] Đừng lạm dụng bean name matching theo tên source Java parameter.
- [ ] Để lựa chọn theo nội dung request ở strategy/business router, không ở generic qualifier.
- [ ] Tách unit test hợp đồng generic khỏi integration test Spring bean wiring.
- [ ] Test thiếu bean, hai bean cùng generic và nhóm collection injection.
- [ ] Phân biệt stdout chương trình với log Maven và compile Java 17 đúng release.

### Tài liệu Spring chính thức

- [Using Generics as Autowiring Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/generics-as-qualifiers.html)
- [Fine-tuning Annotation-based Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html)
- [Using `@Autowired`](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html)

**Kết luận:** tham số generic của Java như `Store<String>` và `Store<Integer>` có thể là **implicit qualifiers** khi Spring giải quyết dependency. Tính năng này dùng được cho injection đơn, `List` và `Map` được tham số hóa; tuy nhiên generic không bảo đảm chọn duy nhất nếu có nhiều bean cùng full type, và nó phụ thuộc metadata type rõ ràng chứ không “đọc T từ object” bất chấp type erasure. Kết hợp generic type, `@Qualifier` và typed collections đúng chỗ sẽ làm object graph có kiểu an toàn, dễ mở rộng và dễ kiểm thử.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="candidate-selection-007">Tên tham số, tên bean và vai trò của tùy chọn biên dịch -parameters</a>

<details>
<summary>Xem chi tiết</summary>

# Tên tham số, tên bean và vai trò của tùy chọn biên dịch `-parameters` trong Spring

## 1. Tại sao tên tham số có thể quyết định bean được tiêm?

Trong Spring Core Container, **một injection point thường được xác định đầu tiên theo Java type**, chẳng hạn constructor cần một `Gateway`. Nếu có hai beans `emailGateway` và `smsGateway` cùng implement `Gateway`, việc chỉ nhìn type sẽ không đủ để chọn một đối tượng.

Spring có những quy tắc chọn ứng viên: **qualifier**, **primary/fallback**, generic type, tên field/tên tham số và các metadata khác. Trong trường hợp có nhiều ứng viên cùng type, **không có chỉ dẫn chọn khác đang ưu tiên**, Spring có thể so **tên của injection point** với **bean name hoặc alias**, rồi dùng ứng viên trùng tên.

Ví dụ:

~~~java
// Snippet bên trong một bean được Spring quản lý:
OrderService(Gateway smsGateway) {
    this.gateway = smsGateway;
}
~~~

Giả sử tồn tại hai beans:
- `emailGateway`, kiểu `Gateway`;
- `smsGateway`, cũng kiểu `Gateway`.

Nếu Spring có thể nhìn thấy **tên tham số Java thật là `smsGateway`**, nó có thể dùng tên ấy như dấu hiệu phân giải và tiêm bean `smsGateway`. Nhưng JVM **không bảo đảm giữ được tên tham số gốc cho reflection** sau mọi kiểu biên dịch. Đây chính là lý do cần hiểu tùy chọn **`javac -parameters`**.

### Ba khái niệm không được trộn lẫn

| Khái niệm | Ví dụ | Nơi phát sinh | Vai trò |
| --- | --- | --- | --- |
| **Tên bean** | `smsGateway` | Spring registration qua `@Bean`, `@Component`, XML, aliases | Định danh bean trong IoC Container |
| **Tên tham số Java** | `smsGateway` trong `OrderService(Gateway smsGateway)` | Chữ ký source Java, tùy compilation metadata | Spring có thể dùng khi chọn bean cùng type |
| **Tên field Java** | `private Gateway smsGateway;` | Khai báo field và thông tin field reflection | Spring có thể xem tên field cho field injection |

**`-parameters` liên quan tới metadata tên tham số của constructor/method**, chứ **không đổi tên bean**, không thêm `@Qualifier`, và **không biến tên field thành tên tham số**. Tên field có đường reflection riêng nên không cần `-parameters` để đọc chính tên field.

Mục tiêu bài học: xác định bean names đến từ đâu, Spring dùng tên của injection point khi nào, công dụng và giới hạn của `javac -parameters`, cách bật đúng trong Maven/IDE, phương pháp debug và kiểm chứng qua **Java 17 + Spring Framework 7.0.9**.

## 2. Bean name lấy từ đâu?

Spring quản lý các bean definitions bằng **bean name** trong một container. Tên này không nhất thiết là tên Java class hay tên biến mà bạn đặt trong business code.

### 2.1. `@Bean` mặc định lấy tên method

~~~java
@Configuration(proxyBeanMethods = false)
class GatewayConfig {
    @Bean
    Gateway emailGateway() {
        return new EmailGateway();
    }
}
~~~

Nếu không chỉ định tên tường minh, method `emailGateway()` thường đăng ký bean tên **`emailGateway`**. Class instance bên trong có thể là `EmailGateway` nhưng điều đó **không tự đổi bean name** thành `EmailGateway`.

Có thể đặt tên khác với method:

~~~java
@Bean("smsGateway")
Gateway createSmsChannel() {
    return new SmsGateway();
}
~~~

Method Java là `createSmsChannel`, nhưng bean name chính là **`smsGateway`** vì đã khai báo tường minh. Nếu injection point cần tên `smsGateway`, phải so với **bean name thực**, không được suy ra từ tên method hay concrete class.

### 2.2. Alias không tạo object mới

~~~java
@Bean(name = {"smsGateway", "oldSmsGateway"})
Gateway createSmsChannel() {
    return new SmsGateway();
}
~~~

Tên đầu thường là tên chính; tên còn lại là **alias** trỏ tới cùng bean. `getBean("oldSmsGateway")` và `getBean("smsGateway")` trong cùng context thường trả cùng reference với singleton mặc định. Một điểm inject có tên **`oldSmsGateway`** cũng có thể khớp thông qua alias.

### 2.3. `@Component` và naming strategy

~~~java
@Component("gatewayForTests")
class FakeGateway implements Gateway {
    // ...
}
~~~

Tại component scan phù hợp, bean name được chỉ rõ là `gatewayForTests`. Nếu không khai tên, Spring dùng tên được tạo bởi cơ chế naming strategy, thường dựa trên short class name theo quy ước. Quy tắc tên cho những class có chữ viết hoa bất thường, custom generator hoặc nested classes có thể khác; **hãy kiểm bean name thực**, đừng dựa vào đoán tên theo filename.

### 2.4. XML và chương trình đăng ký thủ công

~~~xml
<bean id="emailGateway" class="demo.EmailGateway"/>
<alias name="emailGateway" alias="primaryEmailGateway"/>
~~~

XML dùng `id/name` và aliases. API như `registerBean` cũng cho phép đặt tên khi đăng ký. **Bean names là metadata của container**, không phải thuộc tính được JVM gắn vào từng Java class.

**Lưu ý scope:** hai definitions có cùng bean class nhưng tên khác là **hai registrations**, có thể có instance/lifecycle/scope khác nhau. Còn alias không tạo registration thứ hai.

## 3. JVM lưu tên tham số Java như thế nào?

Java source:

~~~java
public ReportService(Gateway smsGateway, int retries) {
    // ...
}
~~~

Trong source, bạn nhìn thấy `smsGateway` và `retries`. Tuy nhiên, khi đọc method/constructor qua **Java Reflection**, tên tham số chỉ được bảo toàn dưới dạng metadata đáng tin cậy khi compiler được yêu cầu ghi nó.

Với **JDK 8+**, tùy chọn:

~~~bash
javac -parameters ReportService.java
~~~

yêu cầu Java compiler ghi **`MethodParameters` attribute** chứa parameter names vào `.class` file. Reflection có thể truy cập bằng `java.lang.reflect.Parameter.getName()` và kiểm tra `Parameter.isNamePresent()`.

Nếu **không** có metadata này, reflection thường báo các tên tổng hợp như `arg0`, `arg1`, kèm `isNamePresent() == false`. Các tên ấy **không phải tên tham số khai báo ban đầu** để Spring dựa vào như fallback bean-name matching.

| Khía cạnh | Biên dịch với `-parameters` | Không có `-parameters` |
| --- | --- | --- |
| Java bytecode chạy ứng dụng | Hoạt động | Hoạt động |
| Method/constructor parameters có metadata tên source | Có, cho code được compile với flag | Thường không |
| Reflection `getName()` | Ví dụ `smsGateway` | Thường là `arg0` |
| Reflection `isNamePresent()` | `true` | `false` |
| Spring suy luận tên tham số cho matching theo name | Có cơ sở metadata | Không nên dựa vào cơ chế này |
| `@Qualifier` explicit | Vẫn hoạt động khi bean/qualifier hợp lệ | Vẫn hoạt động, không phụ thuộc tên source |
| `@Primary` | Vẫn có tác dụng khi phù hợp | Vẫn có tác dụng |
| Java field names qua reflection | Vẫn có | Vẫn có |

**`-parameters` khác `-g`:** `-g` sinh thông tin debug như line/local-variable data; nó **không thay thế** `MethodParameters` cho parameter-name discovery trong Spring Framework 6.1+. Cũng không nhầm `-parameters` với JVM runtime option: đây là **tùy chọn lúc biên dịch**, không phải đối số của lệnh `java`.

## 4. Từ Spring 6.1: parameter-name discovery cần metadata đúng

Tài liệu Spring Framework và ghi chú nâng cấp xác nhận: **từ Spring Framework 6.1**, hỗ trợ suy luận tên tham số qua cách đọc **Local Variable Table** trong bytecode đã bị loại bỏ. Vì thế nếu ứng dụng dựa vào constructor/factory-method parameter names để chọn bean, hãy compile Java với **`-parameters`**.

Trước đó, một số trường hợp từng hoạt động nhờ debug information dù không bật flag. Sau nâng cấp lên 6.1/6.2/7.x, ứng dụng có thể gặp lỗi autowiring kiểu “multiple beans of type...” vì Spring không còn biết `smsGateway` là tên thật của tham số.

Từ **Spring 6.2**, trong trường hợp tên tham số trùng bean name và không có các quy tắc type/qualifier/primary/fallback khác ghi đè lựa chọn, Spring có thể dùng **fast shortcut resolution** cho name match. Đây là **tối ưu hóa**, không phải một thay đổi biến Spring thành hệ thống luôn chọn theo tên trước type.

Cần phát biểu chính xác:

- `@Autowired` vẫn là **type-driven dependency injection**.
- Tên tham số/field là **một dấu hiệu phân giải dự phòng** trong những tình huống đủ điều kiện.
- Primary, qualifiers, fallback và generic metadata có thể khiến một bean trùng tên **không** được chọn.
- **Tên phải tương thích với type**; bean name không thể làm một object sai Java type trở nên assignable.
- Một dependency cần **một bean** có thể lỗi khi không còn cách chọn giữa nhiều candidates; việc có `-parameters` không bảo đảm mọi trường hợp sẽ resolve được.
- Để yêu cầu bean một cách **rõ ràng và bền vững trước refactor**, dùng `@Qualifier` hoặc `@Primary` theo đúng ý nghĩa hợp đồng.

## 5. Quy trình chọn ứng viên và thứ tự ưu tiên cần hiểu

Khi Spring cần giải quyết:

~~~java
@Autowired
void configure(Gateway smsGateway) {
    // ...
}
~~~

Quy trình **khái niệm**:

~~~text
Injection point: Gateway smsGateway
                 |
                 v
      Tìm beans compatible với Gateway
                 |
                 v
   Lọc autowire candidates + qualifiers
                 |
                 v
   Có single candidate đã rõ không?
                 |
       Có điều kiện ưu tiên khác?
       /                    \
   Có primary/qualifier      Không có quy tắc
   hay fallback hợp lệ       ưu tiên override
       |                         |
       v                         v
    Chọn theo                 Có tên field hoặc
   metadata đó               tên parameter thật?
                                  |
                            Có bean trùng tên?
                             /           \
                           Có           Không
                            |               |
                            v               v
                     Xét candidate      Các bước resolution
                      trùng tên         còn lại hoặc lỗi
~~~

Sơ đồ chỉ dùng để tư duy; thuật toán nội bộ còn có các nhánh: dependency name shortcut, priority, default-candidate, tự tham chiếu (self reference), generic types, alias, `ObjectProvider`, collections và nhiều cơ chế đặc biệt.

**Những ví dụ phân biệt cụ thể:**

| Tình huống | Bean được chọn/thất bại |
| --- | --- |
| Hai `Gateway` là emailGateway và smsGateway, param `Gateway smsGateway`, có `-parameters`, không primary/qualifier | Có thể chọn **smsGateway** nhờ tên trùng |
| Hai beans cùng type, param `Gateway arbitraryName` và không có rule khác | Có thể báo ambiguity |
| Hai beans cùng type, emailGateway `@Primary`, param `Gateway smsGateway` | Thông thường chọn **emailGateway** (primary ưu tiên) |
| Param `@Qualifier("smsGateway") Gateway emailGateway` | Chọn **smsGateway** vì qualifier hợp lệ, dù tên param khác |
| `Gateway oldSmsGateway`, bean smsGateway có alias oldSmsGateway | Có thể chọn **smsGateway** qua alias |
| Param yêu cầu `SmsGateway`, bean trùng tên nhưng chỉ có type `EmailGateway` | Không tự vượt qua kiểm tra type |
| Chỉ có một candidate Gateway | Không cần dựa vào tên tham số |
| `List<Gateway>` thay vì `Gateway` | Có thể inject nhiều candidates, không dùng name matching để chọn đúng một |

**Cẩn trọng với names của tham số `@Bean`:** Code bên dưới dùng method factory `nameSelection(Gateway smsGateway,...)`, nên tên method được đăng ký là `nameSelection`, **không phải** tên tham số `smsGateway`. Spring resolve các tham số lúc gọi factory method rồi tạo object được trả về.

## 6. Cấu hình Maven để giữ tên tham số

Một dự án Maven sử dụng Java 17 và Spring Framework 7.0.9 có thể bật:

~~~xml
<properties>
    <maven.compiler.release>17</maven.compiler.release>
    <maven.compiler.parameters>true</maven.compiler.parameters>
</properties>
~~~

Thuộc tính **`maven.compiler.parameters=true`** được Maven Compiler Plugin hỗ trợ; nó yêu cầu `javac` sinh reflection metadata tương ứng `-parameters`. Không cần thêm annotation processor riêng để Spring autowiring hoạt động: đây là compiler metadata, còn IoC xử lý injection ở runtime.

Cũng có thể cấu hình trực tiếp trong plugin:

~~~xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.13.0</version>
    <configuration>
        <release>17</release>
        <parameters>true</parameters>
    </configuration>
</plugin>
~~~

**Chọn một nguồn cấu hình nhất quán** trong project thật. Ví dụ runnable ở mục 7 dùng **properties**, không lặp cấu hình parameters trong plugin. Khi dùng Gradle, IDE build hoặc `javac` thủ công, phải thiết lập flag ở **compiler thực sự tạo `.class`**, không chỉ thêm thuộc tính Maven trong một file không được build tool đó sử dụng.

### Xác minh đã bật flag thật sự

- Chạy `mvn -q clean compile` để xóa các class artifacts cũ rồi compile.
- Dùng `java.lang.reflect.Executable.getParameters()` và `Parameter.isNamePresent()` để xác nhận.
- Có thể dùng `javap -v -p` inspect class và tìm **`MethodParameters`**, khác với `LocalVariableTable`.
- Khi đổi IDE compiler settings, hãy **clean/rebuild**; đừng dựa vào class file từ lần build cũ.

**Cần kiểm tra cả compile và testCompile** nếu tests cũng có factory methods/constructor parameter naming; Maven Compiler Plugin hỗ trợ property cho các compilation goals. Nếu một module được build bởi hệ khác, property Maven của module bên cạnh không tự áp dụng.

## 7. Dự án runnable Spring 7.0.9 + Java 17

Demo có ba context độc lập và một bước Reflection:

1. **NameConfig không có `@Primary`:** có emailGateway và smsGateway (alias `oldSmsGateway`); một factory method nhận tham số **`Gateway smsGateway`** không gắn qualifier → Spring chọn **SMS** nhờ parameter name. Một tham số khác có `@Qualifier("emailGateway")` → chọn EMAIL bất kể tên `preferred`.
2. **AliasSelection** nhận `Gateway oldSmsGateway` → fallback name match alias → SMS. **FieldSelection** có field `@Autowired Gateway smsGateway` → chọn SMS theo **field name**, không cần `-parameters` để biết tên field.
3. **PrimaryConfig:** emailGateway được `@Primary`; tham số tên `smsGateway` vẫn nhận **EMAIL**. Tham số tên `emailGateway` có `@Qualifier("smsGateway")` lại nhận **SMS**.
4. **AmbiguousConfig:** hai beans cùng type, injection parameter tên `unknownGateway` không khớp bean name/alias, không có primary/qualifier → container không thể resolve một bean duy nhất; lỗi được catch.
5. **Java Reflection:** chương trình in tên tham số thực và `isNamePresent()` để xác nhận `-parameters` trên lớp vừa compile.

Không dùng Spring Boot, web server, database hoặc network call.

### 7.1. Tổ chức files

~~~text
parameter-names-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── ParameterNamesDemo.java
~~~

Yêu cầu **JDK 17+**, Maven **3.9+**, có thể tải `spring-context` khi lần đầu build.

### 7.2. `pom.xml` đầy đủ

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>parameter-names-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <maven.compiler.parameters>true</maven.compiler.parameters>
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
                    <mainClass>demo.ParameterNamesDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Đây là cấu hình **bắt buộc đối với đúng expected output của demo**: tên tham số trong method `@Bean` phải được giữ. `maven.compiler.parameters` được dùng để Maven Compiler Plugin chuyển tương đương `-parameters` xuống `javac`.

### 7.3. `src/main/java/demo/ParameterNamesDemo.java` đầy đủ

~~~java
package demo;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

public final class ParameterNamesDemo {
    private ParameterNamesDemo() {
    }

    @FunctionalInterface
    interface Gateway {
        String id();
    }

    static final class NameSelection {
        private final Gateway byParameterName;
        private final Gateway byQualifier;

        NameSelection(Gateway byParameterName, Gateway byQualifier) {
            this.byParameterName = byParameterName;
            this.byQualifier = byQualifier;
        }

        String paramId() {
            return byParameterName.id();
        }

        String qualifierId() {
            return byQualifier.id();
        }
    }

    static final class AliasSelection {
        private final Gateway gateway;

        AliasSelection(Gateway gateway) {
            this.gateway = gateway;
        }

        String id() {
            return gateway.id();
        }
    }

    static final class FieldSelection {
        @Autowired
        private Gateway smsGateway;

        String id() {
            return smsGateway.id();
        }
    }

    static final class PrimarySelection {
        private final Gateway byName;
        private final Gateway byQualifier;

        PrimarySelection(Gateway byName, Gateway byQualifier) {
            this.byName = byName;
            this.byQualifier = byQualifier;
        }

        String namedArgumentId() {
            return byName.id();
        }

        String qualifiedId() {
            return byQualifier.id();
        }
    }

    static final class AmbiguousSelection {
        AmbiguousSelection(Gateway gateway) {
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class NameConfig {
        @Bean("emailGateway")
        public Gateway emailGateway() {
            return () -> "EMAIL";
        }

        @Bean(name = {"smsGateway", "oldSmsGateway"})
        public Gateway smsGatewayFactory() {
            return () -> "SMS";
        }

        @Bean
        public NameSelection nameSelection(
                Gateway smsGateway,
                @Qualifier("emailGateway") Gateway preferred) {
            return new NameSelection(smsGateway, preferred);
        }

        @Bean
        public AliasSelection aliasSelection(Gateway oldSmsGateway) {
            return new AliasSelection(oldSmsGateway);
        }

        @Bean
        public FieldSelection fieldSelection() {
            return new FieldSelection();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class PrimaryConfig {
        @Bean("emailGateway")
        @Primary
        public Gateway emailGateway() {
            return () -> "EMAIL";
        }

        @Bean("smsGateway")
        public Gateway smsGateway() {
            return () -> "SMS";
        }

        @Bean
        public PrimarySelection primarySelection(
                Gateway smsGateway,
                @Qualifier("smsGateway") Gateway emailGateway) {
            return new PrimarySelection(smsGateway, emailGateway);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AmbiguousConfig {
        @Bean("emailGateway")
        public Gateway emailGateway() {
            return () -> "EMAIL";
        }

        @Bean("smsGateway")
        public Gateway smsGateway() {
            return () -> "SMS";
        }

        @Bean
        public AmbiguousSelection ambiguousSelection(Gateway unknownGateway) {
            return new AmbiguousSelection(unknownGateway);
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Method factory = NameConfig.class.getDeclaredMethod(
                "nameSelection", Gateway.class, Gateway.class);
        Parameter parameter = factory.getParameters()[0];
        System.out.println("reflection.name=" + parameter.getName());
        System.out.println("reflection.present=" + parameter.isNamePresent());

        try (var context =
                new AnnotationConfigApplicationContext(NameConfig.class)) {
            var named = context.getBean(NameSelection.class);
            var alias = context.getBean(AliasSelection.class);
            var field = context.getBean(FieldSelection.class);

            System.out.println("name.selected=" + named.paramId());
            System.out.println("name.qualified=" + named.qualifierId());
            System.out.println("alias.selected=" + alias.id());
            System.out.println("field.selected=" + field.id());
            String[] beanNames = context.getBeanNamesForType(Gateway.class);
            Arrays.sort(beanNames);
            System.out.println("names=" + String.join(",", beanNames));
            System.out.println("alias.same="
                    + (context.getBean("oldSmsGateway")
                       == context.getBean("smsGateway")));
        }

        try (var context =
                new AnnotationConfigApplicationContext(PrimaryConfig.class)) {
            var primary = context.getBean(PrimarySelection.class);
            System.out.println("primary.namedArgument="
                    + primary.namedArgumentId());
            System.out.println("primary.qualified=" + primary.qualifiedId());
        }

        try (var context =
                new AnnotationConfigApplicationContext(AmbiguousConfig.class)) {
            System.out.println("ambiguous.unexpected");
        } catch (BeansException expected) {
            System.out.println("ambiguous.failed=true");
        }
    }
}
~~~

**Giải thích:**
- `getDeclaredMethod(...)` truy vấn factory method hai tham số có type `Gateway`. Tên thật tham số đầu phải là **`smsGateway`** vì POM bật `-parameters`; Reflection `isNamePresent()` phải `true`.
- `NameConfig` đăng ký hai Gateway beans. `nameSelection(Gateway smsGateway, ...)` thiếu qualifier ở tham số đầu, nên **name matching** có thể phân giải type ambiguity thành SMS.
- Tham số `preferred` có **`@Qualifier("emailGateway")`** nên chọn EMAIL; tên param `preferred` không ảnh hưởng.
- `oldSmsGateway` là alias của `smsGateway`. Parameter `Gateway oldSmsGateway` có tên khớp alias nên chọn SMS.
- Field `@Autowired Gateway smsGateway` cũng chọn SMS vì field name; **tên field vẫn có ngay cả khi compiler không bật `-parameters`**.
- Trong `PrimaryConfig`, tham số tên `smsGateway` chọn **EMAIL** nhờ `@Primary` hợp lệ, chứng minh primary có thể thắng name fallback. Tham số `@Qualifier("smsGateway") Gateway emailGateway` lại chọn SMS.
- `AmbiguousConfig` cố ý không có primary/qualifier và param `unknownGateway` không match bean name; context tạo eager singleton bị từ chối, demo bắt `BeansException` và in một dòng.
- Mỗi context độc lập, nên các beans cùng tên trong configs khác **không đè lên nhau**.

**Hạn chế có chủ ý:** chương trình không sửa Java bytecode hay chạy compile thiếu `-parameters` tại runtime, bởi compiler options chỉ thay đổi được lúc build. Phép đối chứng bật/tắt thực hiện **ở ví dụ Java Reflection độc lập tại mục 9**.

## 8. Chạy Maven và expected stdout

Đứng trong thư mục `parameter-names-demo` chứa POM:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

Các lệnh `java -version`/`javac -version`/`mvn -version` có output theo môi trường; không có stdout cố định. Maven sẽ compile Java 17 với `-parameters`, rồi chạy class `demo.ParameterNamesDemo` và các Spring contexts.

**11 dòng stdout do application chủ động in** (không tính log/exception diagnostics của Maven hoặc Spring):

~~~text
reflection.name=smsGateway
reflection.present=true
name.selected=SMS
name.qualified=EMAIL
alias.selected=SMS
field.selected=SMS
names=emailGateway,smsGateway
alias.same=true
primary.namedArgument=EMAIL
primary.qualified=SMS
ambiguous.failed=true
~~~

**Những điểm cần xác nhận khi chạy:**

| Output | Ý nghĩa |
| --- | --- |
| `reflection.name=smsGateway` | Java compiler đã giữ tên tham số thật |
| `reflection.present=true` | Reflection đọc được metadata `MethodParameters` |
| `name.selected=SMS` | Name fallback chọn bean trùng parameter name khi không có primary/qualifier |
| `name.qualified=EMAIL` | Qualifier explicit không phụ thuộc tên tham số |
| `alias.selected=SMS` | Name fallback có thể match alias |
| `field.selected=SMS` | Tên field cũng dùng được cho fallback matching |
| `names=emailGateway,smsGateway` | Alias không trở thành bean definition thứ ba |
| `primary.namedArgument=EMAIL` | Primary có thể được ưu tiên hơn tên tham số |
| `primary.qualified=SMS` | Qualifier lọc candidates, chọn SMS dù param tên khác |
| `ambiguous.failed=true` | Không có rule để chọn một bean nên Spring báo failure |

**Chú ý log ngoài stdout:** Khi `AmbiguousConfig` thất bại tại `refresh()`, Spring có thể in warning/stack trace qua logger. Expected stdout ở đây chỉ tính những dòng từ `System.out.println` của chương trình. Không kiểm tra exact text của exception cause/stack trace vì chúng thay đổi theo phiên bản/launch context.

## 9. Java 17 thuần: đối chiếu có và không có `-parameters`

Tạo file riêng **`ParameterReflectionDemo.java`** tại một thư mục ví dụ không phụ thuộc Maven/Spring:

~~~java
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class ParameterReflectionDemo {
    static void configure(String emailGateway, int retries) {
    }

    public static void main(String[] args) throws Exception {
        Method method = ParameterReflectionDemo.class.getDeclaredMethod(
                "configure", String.class, int.class);
        for (Parameter parameter : method.getParameters()) {
            System.out.println("name=" + parameter.getName()
                    + ",present=" + parameter.isNamePresent());
        }
    }
}
~~~

### Biên dịch **có** lưu tên tham số

~~~bash
javac -parameters ParameterReflectionDemo.java
java ParameterReflectionDemo
~~~

Stdout chính xác với JDK 17:

~~~text
name=emailGateway,present=true
name=retries,present=true
~~~

### Biên dịch **không** lưu tên tham số

Biên dịch lại **cùng file**, ghi đè `ParameterReflectionDemo.class` bằng lệnh dưới (không giữ class file cũ từ bước trước):

~~~bash
javac ParameterReflectionDemo.java
java ParameterReflectionDemo
~~~

Stdout chính xác theo Java Reflection khi không có `MethodParameters` tên nguồn:

~~~text
name=arg0,present=false
name=arg1,present=false
~~~

**Dấu hiệu kỹ thuật:** `isNamePresent=false` chứng minh Reflection đang thấy tên tổng hợp, không phải `emailGateway`/`retries`. Hai lần chạy tạo cùng **hành vi method**, chỉ khác metadata phục vụ reflection/frameworks. Tên biến địa phương bên trong method không được tự bảo toàn chỉ vì bật `-parameters`.

Có thể quan sát class bằng:

~~~bash
javap -v -p ParameterReflectionDemo.class
~~~

và tìm phần **`MethodParameters`**. Với lần compile có flag, attribute mang tên `emailGateway` và `retries`; với lần không flag, metadata tên này không có. Nếu bạn vừa chuyển công cụ biên dịch, hãy clean/rebuild để tránh đọc nhầm class artifacts.

### Windows PowerShell, cmd và Linux

Các lệnh `javac` và `java` ở trên dùng được trong PowerShell, Command Prompt và shell Linux/macOS nếu `JAVA_HOME`/`PATH` đúng. Không dùng `export JAVA_TOOL_OPTIONS` hay lệnh đặt biến môi trường hệ điều hành để bật `-parameters`: flag thuộc **`javac`**, phải truyền ở compile step.

## 10. Lỗi thường gặp và cách debug

| Triệu chứng | Nguyên nhân có thể | Cách xử lý |
| --- | --- | --- |
| Java Reflection báo `arg0`/`isNamePresent=false` | Class biên dịch thiếu `-parameters` | Bật flag trong tool biên dịch thực tế, clean compile |
| Spring 6.1+ trước chạy ổn, nâng version thì `NoUniqueBeanDefinitionException` | Từng dựa vào LocalVariableTable/debug symbols cho parameter names | Compile `-parameters`, hoặc dùng qualifier rõ ràng |
| Bật property Maven nhưng chạy trong IDE vẫn không chọn bean theo param name | IDE compiler không sử dụng config Maven, output class cũ | Xem build delegation/compiler args, clean rebuild và kiểm Reflection |
| Có `@Bean smsGateway()` nhưng Spring lại thấy tên khác | `@Bean(name=...)` override default method name hoặc naming strategy | Kiểm bean names thực qua `getBeanNamesForType` |
| Parameter tên khớp nhưng bean sai type | Name không thay được Java assignability | Sửa contract type, return type hoặc bean registrations |
| Bean `@Primary` được chọn dù tham số mang tên bean khác | Primary là selection rule hợp lệ | Dùng qualifier nếu use case đòi bean cụ thể |
| `@Qualifier("smsGateway")` chọn SMS dù param tên khác | Qualifier đang lọc candidates như thiết kế | Không nhầm name fallback với explicit qualifier |
| Không có bean trùng tên param, nhưng chỉ có một candidate | Dependency vẫn có thể resolve theo type | `-parameters` không bắt buộc cho mọi injection |
| Constructor có nhiều parameters cùng type | Từng param cần resolution độc lập; có thể phải dùng qualifier | Đặt tên có nghĩa, giữ metadata và thêm qualifier khi cần |
| Alias xuất hiện trong `getBeanNamesForType`? | Type discovery liệt kê bean names, alias không phải bean mới | So `getAliases(name)` và `containsBeanDefinition` |
| Đặt `-parameters` vào command `java` | Sai giai đoạn | Đặt vào `javac` hoặc Maven Compiler Plugin |
| Bật `-g` mà Spring vẫn không biết param name | Debug info không còn là fallback trong Spring 6.1+ | Dùng `-parameters` |
| Đổi tên tham số khiến wiring đổi | Đang phụ thuộc vào name fallback ngầm | Dùng `@Qualifier` hoặc policy `@Primary` rõ hơn |
| Field name có sẵn nhưng constructor name mất | Field reflection khác parameter-name metadata | Xem `Parameter.isNamePresent()`, không dựa vào field test |
| `NoUniqueBeanDefinitionException` khi có nhiều candidates | Không đủ selector theo injection point | Kiểm type, qualifier, primary/fallback, name/alias |
| App startup fail dù đã compile đúng flag | Có thể còn lỗi DI thật hoặc business config | Đọc root cause, kiểm registrations/context/bean types |

### Checklist chẩn đoán theo thứ tự

1. Xác nhận **đúng `ApplicationContext`** và bean registrations; in tên bằng `getBeanNamesForType(RequiredType.class)`.
2. Kiểm **type compatibility**, xem declared `@Bean` return types, generic qualifications và proxy nếu liên quan.
3. Kiểm point có **`@Qualifier`**, `@Primary`, `@Fallback`, `autowireCandidate` hay rule khác không.
4. Nếu đang dựa vào tên, xác định **field name hoặc method/constructor parameter name** chính xác và bean name/alias tương ứng.
5. Dùng **Reflection `isNamePresent()`** kiểm tra tên method/constructor parameters.
6. Đối chiếu `maven.compiler.parameters`, IDE compiler flags, toolchains và rebuild outputs.
7. Không tự sửa một bean thành primary chỉ để che sai cấu hình khi thực sự muốn chọn một implementation cụ thể.

## 11. Khi nào nên dựa vào tên tham số, khi nào nên dùng qualifier?

**Tên tham số có thể là fallback thuận tiện** khi nhiều beans cùng type và tên injection point có ý nghĩa tự nhiên. Nó giúp giảm annotation lặp và Spring 6.2+ có thể tối ưu resolution trong những trường hợp đơn giản. Nhưng nó vẫn là **hợp đồng phụ thuộc vào chữ ký source + compile metadata + quy tắc candidate selection**.

Cân nhắc dùng `@Qualifier` khi:
- Use case yêu cầu **một implementation cụ thể** dù bean khác được primary.
- Việc đổi tên param khi refactor không nên âm thầm đổi wiring.
- Nhiều beans cùng type thể hiện **vai trò** khác nhau, như `primaryGateway`, `auditGateway`.
- Việc gọi bean qua alias/name là policy đáng được mô tả rõ trong code review và integration tests.

Dùng `@Primary` khi thật sự có một **mặc định chung** hợp lý cho injection đơn, không phải khi mọi use case cần được gắn qualifier dài dòng.

Dùng `@Resource(name="...")` ở field/setter nếu yêu cầu là **tra cứu by-name** theo semantic Jakarta injection. Đây là cơ chế khác với `@Autowired` (type-driven) và không nên coi hai cách như các alias hoàn toàn tương đương.

**Ưu tiên constructor injection** cho dependency bắt buộc, để object hợp lệ ngay từ lúc tạo; dù dùng constructor, bạn vẫn có thể đặt `@Qualifier` lên tham số constructor nếu cần. Điều này không phụ thuộc vào việc class có setter hoặc field annotation.

## 12. Bài tập kiểm chứng từng quy tắc

Thực hiện mỗi thí nghiệm **trên một bản sao code**, khôi phục project gốc giữa các lần để expected stdout mục 8 còn nguyên.

1. **Bỏ `maven.compiler.parameters=true`** và clean rebuild. Dự đoán `reflection.present=false` và context `NameConfig` có thể fail vì thiếu parameter-name matching.
2. **Bật lại `-parameters`**; xác nhận Reflection trả đúng `smsGateway`, `name.selected=SMS`.
3. **Đổi tên tham số** `Gateway smsGateway` thành `Gateway gateway` mà không đổi beans. Dự đoán ambiguity ở context không có primary/qualifier.
4. **Giữ tên param sai nhưng thêm `@Qualifier("smsGateway")`**; dự đoán vẫn chọn SMS.
5. **Thêm `@Primary` cho emailGateway trong NameConfig**; dự đoán param tên smsGateway nhận EMAIL nếu không có qualifier.
6. **Đổi `@Qualifier("emailGateway")` thành `@Qualifier("smsGateway")`**; output qualified đổi sang SMS dù tên param là preferred.
7. **Đổi bean name tường minh** thành `@Bean("phoneGateway")`; xem field/parameter names cần cập nhật ra sao, hoặc phải thêm alias để tương thích.
8. **Xóa alias `oldSmsGateway`**; dự đoán injection theo tham số oldSmsGateway không còn có đường name matching, có thể fail khi có nhiều beans.
9. **Tên field không phụ thuộc `-parameters`:** sau khi bỏ flag, tạo một context chỉ đăng ký FieldSelection với Gateway beans; field smsGateway vẫn có thể resolve theo tên field.
10. **Không có ambiguity:** chỉ đăng ký một Gateway bean, dùng param `Gateway arbitraryName` và xem type injection không cần parameter-name fallback.
11. **Hai qualifiers:** tạo các bean có nhiều qualifier labels, thử injection point với selector minh bạch, không dựa vào raw parameter name.
12. **Reflection:** thử `javac -g` nhưng không `-parameters`; kiểm `isNamePresent()` và `MethodParameters`.
13. **Maven vs IDE:** biên dịch cùng source bằng hai công cụ, so output `Parameter.isNamePresent()` để phát hiện build path khác.
14. **Spring 6.2 fast path:** giải thích khi nào tối ưu name match áp dụng và vì sao `@Primary`/`@Qualifier` có thể làm đường tắt không áp dụng.
15. **Test wiring:** viết integration test với hai Gateway beans, assert đối tượng nhận bean đúng; viết unit test service bằng constructor fake để tách nghiệp vụ khỏi Spring.
16. **Generic types:** thử các beans `Store<String>` và `Store<Integer>` với param names giống bean names khác nhưng type generic không khớp; xác nhận tên không phá type filtering.

### Checklist hiểu bài

- [ ] Phân biệt **bean name**, **method/constructor parameter name**, **field name** và **alias**.
- [ ] Biết `@Bean` mặc định đặt tên theo method, hoặc dùng `@Bean(name=...)` để đặt tường minh.
- [ ] Hiểu `javac -parameters` sinh `MethodParameters` cho Java Reflection.
- [ ] Biết `Parameter.getName()`/`isNamePresent()` xác minh compiler metadata.
- [ ] Biết Spring 6.1+ không còn LocalVariableTable parameter-name fallback.
- [ ] Biết Spring 6.2+ có name-matching fast shortcut trong những trường hợp phù hợp.
- [ ] Không suy luận name fallback thắng `@Primary` hoặc `@Qualifier`.
- [ ] Biết field name không phụ thuộc `-parameters` giống tên tham số.
- [ ] Cấu hình Maven `maven.compiler.parameters=true`, clean compile, check IDE compiler.
- [ ] Chạy được **11 dòng stdout Spring demo** và hai kết quả Java Reflection có/không flag.
- [ ] Dùng qualifier/primary theo policy rõ khi dependency quan trọng.

## 13. Kết luận và tài liệu chính thức

**Tóm tắt:** Tên tham số có thể giúp Spring chọn bean trùng tên khi nhiều beans cùng type, nhưng đây là **một phần của dependency resolution**, không phải nguyên tắc “luôn tiêm theo tên”. Để Spring đọc đúng tên tham số của constructor/`@Bean` method từ Java class files, hãy bật `javac -parameters` (hoặc `maven.compiler.parameters=true`). Từ Spring 6.1, dựa vào debug symbols `-g` để cứu thiếu parameter names không còn phù hợp; từ 6.2, name match có thể có đường tắt xử lý trong trường hợp không bị các rule khác ghi đè.

Nguồn chính thức:
- [Spring Framework — Fine-tuning Annotation-based Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html)
- [Spring Framework — Using @Autowired](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html)
- [Spring Framework — Primary and Fallback](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-primary.html)
- [Spring Framework — Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java.html)
- [Spring Framework — Bean Definition Naming](https://docs.spring.io/spring-framework/reference/core/beans/definition.html)
- [Spring Framework — 6.x Upgrade Notes / Parameter Name Retention](https://github.com/spring-projects/spring-framework/wiki/Upgrading-to-Spring-Framework-6.x)
- [Maven Compiler Plugin — compile mojo (`parameters` option)](https://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html)
- [Java 17 — java.lang.reflect.Parameter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Parameter.html)
- [Oracle javac `-parameters` option](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html)

**Ghi nhớ để debug:** Hãy kiểm tra **type → qualifiers/primary/fallback → parameter/field name → bean name/alias → compiler metadata**. Nếu bean name trùng parameter mà Spring vẫn không chọn, hãy hỏi trước tiên: *đã biên dịch với `-parameters` và có rule chọn ứng viên khác ưu tiên hơn chưa?*

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="candidate-selection-008">@Fallback và điều kiện phiên bản từ Spring Framework 6.2</a>

<details>
<summary>Xem chi tiết</summary>

# @Fallback và điều kiện phiên bản từ Spring Framework 6.2

## 1. @Fallback giải quyết vấn đề gì?

Một thư viện hoặc module có thể cung cấp **implementation mặc định** để ứng dụng chạy ngay. Khi ứng dụng đăng ký implementation riêng, ta muốn Spring **ưu tiên implementation riêng mà không buộc người dùng thêm @Primary**.

Từ **Spring Framework 6.2**, annotation `org.springframework.context.annotation.Fallback` phục vụ đúng tình huống này: đánh dấu một bean là **ứng viên dự phòng** khi Spring tự động chọn bean cho **dependency đơn** (*single-valued autowiring*).

~~~java
@Bean
@Fallback
MessageCodec defaultCodec() {
    return new DefaultCodec();
}
~~~

Khối này chỉ là **minh họa**; cần định nghĩa các class/interface, import và cấu hình Spring. Phần chạy hoàn chỉnh nằm ở mục 5.

Nếu context chỉ có bean `defaultCodec` phù hợp, Spring vẫn inject bean đó bình thường. Nếu ứng dụng đăng ký thêm một bean `MessageCodec` **thông thường (không fallback)** và đó là ứng viên thông thường duy nhất, Spring chọn bean thông thường.

~~~text
Injection point: MessageCodec codec
                  |
       ứng viên đúng type MessageCodec
                  |
        +---------+---------+
        |                   |
    defaultCodec         customCodec
    @Fallback            thông thường
        |                   |
        +---------+---------+
                  |
      có đúng 1 ứng viên thông thường?
                  |
            Có -> customCodec
            Không -> cần xét thêm các quy tắc chọn
                    / thiếu / mơ hồ
~~~

**Ba điểm không được hiểu sai:**

- `@Fallback` **không xóa hoặc vô hiệu hóa** bean. Bean vẫn có thể lấy theo tên, inject theo qualifier thích hợp và xuất hiện trong `List<MessageCodec>`, `Map<String, MessageCodec>` hoặc stream từ provider.
- `@Fallback` **không đồng nghĩa @Lazy, @Conditional hay @Profile**. Nó không đợi tới lúc implementation khác thất bại rồi thay thế bean đang được service sử dụng.
- `@Fallback` **không có trước Spring Framework 6.2**. Source import annotation này không compile với Spring 6.1.x hoặc các phiên bản trước vì API tương ứng chưa tồn tại.

Trong mô hình này, "fallback" mô tả **ưu tiên ứng viên khi autowire một object**, không phải kỹ thuật chịu lỗi, circuit breaker hay retry của ứng dụng.

## 2. Điều kiện áp dụng và quy tắc chọn bean

`@Fallback` có dạng marker annotation (không cần tham số):

~~~java
import org.springframework.context.annotation.Fallback;
~~~

Theo API Spring 6.2, nó có thể được đặt trên **class** (`TYPE`) hoặc **method** (`METHOD`), với retention runtime. Hai vị trí thường dùng:

~~~java
// Minh họa bean factory method:
@Bean
@Fallback
MessageCodec fallbackCodec() {
    return new DefaultCodec();
}
~~~

~~~java
// Minh họa component class:
@Component
@Fallback
class DefaultCodec implements MessageCodec {
    // Triển khai các method MessageCodec
}
~~~

Hai khối trên chỉ minh họa; không là file chạy độc lập. Với component, phải bật component scanning đúng package hoặc đăng ký class vào context. Với `@Bean`, class `@Configuration` chứa factory method phải được Spring đăng ký.

### 2.1. Bảng quyết định cho dependency đơn

Giả sử `MessageCodec` là interface, bean candidates đã đúng type và không có qualifier/điều kiện đặc biệt khác.

| Bean phù hợp | Kết quả mong đợi khi inject `MessageCodec` đơn |
| --- | --- |
| Một bean fallback, không có bean khác | Chọn bean fallback |
| Một bean thông thường + một bean fallback | Chọn bean thông thường |
| Một bean thông thường + nhiều bean fallback | Chọn bean thông thường |
| Hai bean thông thường + một bean fallback | Còn mơ hồ nếu không có quy tắc chọn thêm |
| Hai bean fallback, không bean thông thường | Có thể mơ hồ vì chỉ đánh dấu fallback chưa xác định duy nhất |
| Một bean `@Primary` phù hợp + những ứng viên không primary | Bean primary thường được ưu tiên cho injection đơn |
| Không có bean phù hợp | Required dependency không giải quyết được |

Quy tắc do tài liệu Spring nêu rõ: **nếu trong nhiều ứng viên chỉ có một bean không đánh dấu fallback, bean đó được ưu tiên**, gần như có hiệu lực primary ở tình huống này.

**Không suy rộng** rằng "cứ có @Fallback là Spring luôn chọn được một bean". Khi còn hai beans thông thường, `@Fallback` trên bean thứ ba không thể tự phân biệt hai bean chính. Khi có hai fallbacks và không có bean thường, cũng vẫn cần lựa chọn rõ hoặc thay cấu hình.

### 2.2. Một bean fallback duy nhất vẫn dùng được

Đây là điều kiện giúp một thư viện đóng gói implementation mặc định:

- Thư viện cung cấp `@Fallback MessageCodec defaultCodec()`.
- Ứng dụng không override: service nhận `defaultCodec`, chạy ngay.
- Ứng dụng bổ sung `@Bean MessageCodec customCodec()`: service nhận `customCodec` mà không phải thêm `@Primary` lên bean mới.

Cách này thuận tiện cho **library starter, module mở rộng hoặc tích hợp có implementation mặc định**. Nhưng annotation không tự quyết định nên có bao nhiêu bean được đăng ký; cơ chế đăng ký vẫn qua `@Bean`, component scan, import, bean definition hay configuration tương ứng.

### 2.3. Bean fallback vẫn hiện diện trong container

`@Fallback` không xóa bean cũ và cũng không ngăn Spring tạo singleton non-lazy. Nếu `defaultCodec` có singleton scope, nó vẫn có thể được tạo khi context refresh theo vòng đời thông thường.

Khi gọi:

~~~java
MessageCodec selected = context.getBean(MessageCodec.class);
MessageCodec backup = context.getBean(
        "defaultCodec", MessageCodec.class);
~~~

trong context có cả một bean thường duy nhất và một fallback, type-based lookup có thể chọn bean thường, còn **lookup tường minh tên `defaultCodec` vẫn lấy fallback**. Đây là hai dạng yêu cầu bean khác nhau, đừng dùng chúng như cùng một phép đo.

## 3. So sánh @Fallback, @Primary và @Qualifier

| Annotation | Vai trò chính | Khi nào nên dùng? |
| --- | --- | --- |
| `@Fallback` | Hạ mức ưu tiên của bean dự phòng trong chọn dependency đơn | Module cung cấp default implementation, muốn application-specific bean tự được ưu tiên |
| `@Primary` | Nâng mức ưu tiên một bean khi có nhiều candidates đơn | Có một lựa chọn mặc định rõ giữa các implementations |
| `@Qualifier("name")` | **Lọc** các ứng viên theo tiêu chí/metadata | Injection point cần implementation mang vai trò cụ thể |
| Không annotation đặc biệt | Phân giải theo type và những metadata khác | Chỉ có một candidate đúng type hoặc Spring có rule rõ |

### 3.1. Khác nhau về hướng ưu tiên

Với `@Primary`:

~~~java
@Bean
@Primary
MessageCodec preferredCodec() {
    return new PreferredCodec();
}
~~~

Bean `preferredCodec` được đánh dấu là lựa chọn ưu tiên khi có nhiều ứng viên. Với `@Fallback`:

~~~java
@Bean
@Fallback
MessageCodec defaultCodec() {
    return new DefaultCodec();
}
~~~

Bean `defaultCodec` tuy vẫn đủ điều kiện cung cấp `MessageCodec` nhưng **nhường chỗ cho bean không fallback** nếu chỉ còn đúng một bean thường trong các candidates.

Hai đoạn cần class tương ứng và import Spring mới compile độc lập. Chúng giải thích hai hướng thiết kế: **chỉ định bean nào là ưu tiên** so với **chỉ định bean nào là dự phòng**.

### 3.2. Qualifier vẫn quan trọng

Nếu cả `customA` và `customB` đều là beans thông thường cùng type `MessageCodec`, `@Fallback` trên bean thứ ba không phân biệt được hai ứng viên ấy. Có thể đặt:

~~~java
public Renderer(
        @Qualifier("customA") MessageCodec codec) {
    this.codec = codec;
}
~~~

Đây là snippet minh họa. `@Qualifier` của Spring đặt trên tham số để yêu cầu ứng viên phù hợp tên/metadata. Spring giải quyết qualifier trong **tập đã phù hợp type**, nên đừng nghĩ qualifier có thể biến một bean khác kiểu thành `MessageCodec`.

Bạn cũng có thể chủ động yêu cầu bean dự phòng qua qualifier rõ (ví dụ `@Qualifier("defaultCodec")` nếu metadata/tên bean thực tế tương ứng). Khi đó annotation fallback không có ý nghĩa "cấm sử dụng" bean; nó chỉ ảnh hưởng ưu tiên khi chọn theo kiểu.

### 3.3. @Primary không làm collection nhỏ lại

`@Primary` và `@Fallback` đều chủ yếu ảnh hưởng **chọn một object** khi có nhiều candidates. Nếu inject `List<MessageCodec>`, Spring vẫn có thể cung cấp cả beans thường, primary và fallback đúng type. Để giới hạn collection có chủ đích, hãy dùng qualifier hoặc một strategy/filter trong code thích hợp.

## 4. Collection, Map và ObjectProvider vẫn bao gồm fallback

Ví dụ:

~~~java
@Autowired
public void configure(List<MessageCodec> codecs,
                      Map<String, MessageCodec> codecsByName) {
    this.codecs = codecs;
    this.codecsByName = codecsByName;
}
~~~

Đây là code minh họa chưa định nghĩa field/interface. Spring có thể inject mọi bean đúng type vào `List`, và `Map<String, MessageCodec>` có **bean name** làm key và bean instance làm value.

`@Fallback` không loại thành viên khỏi tập này. Tài liệu API cũng chỉ rõ các array, collection, map và stream từ `ObjectProvider` bao gồm tất cả ứng viên type-matching.

Nếu chỉ cần **một implementation khi service hoạt động**, inject `MessageCodec` đơn. Nếu muốn **khám phá tất cả providers/strategies**, inject collection hoặc provider stream có chủ đích; khi đó hãy tự quyết định có bỏ qua fallback trong logic hay không, thay vì giả định container đã loại nó.

**Chú ý thứ tự:** collection injection không nên dựa vào thứ tự bean source hoặc scan package. Nếu thứ tự quan trọng, dùng Spring `@Order`/`Ordered` thích hợp hoặc sắp xếp dữ liệu trước khi in/xử lý.

### 4.1. ObjectProvider không có nghĩa chỉ trả bean thường

Nếu code gọi:

~~~java
ObjectProvider<MessageCodec> provider =
        context.getBeanProvider(MessageCodec.class);
var names = provider.stream().map(MessageCodec::name).toList();
~~~

đó là **ví dụ minh họa** cần khai báo interface, context và imports đầy đủ. Stream có thể liệt kê cả beans fallback. Trái lại, `getObject()` hoặc các biến thể single-object resolution của provider tuân theo quy tắc chọn candidate cụ thể và có thể bị mơ hồ nếu không có một ứng viên cuối.

Không nhầm việc có bean fallback với cơ chế `getIfAvailable()` trả null khi **thực sự không có bean**; fallback vẫn là bean đã đăng ký.

## 5. Ví dụ chạy đầy đủ: Spring Framework 6.2 và Java 17

Chương trình này tạo hai `ApplicationContext` độc lập:

- **Context thứ nhất:** chỉ đăng ký một `MessageCodec` fallback. Bean tiêu thụ được tiêm default.
- **Context thứ hai:** đăng ký thêm một `MessageCodec` thông thường. Bean tiêu thụ tự chọn implementation thường, nhưng collection vẫn có cả fallback; fallback vẫn lấy trực tiếp theo tên được.

### 5.1. Cấu trúc và Maven

Yêu cầu JDK 17+, Apache Maven. Dùng **Spring Framework 6.2.0** và `spring-context`; không dùng Spring Boot, web server hay database.

~~~text
spring-fallback-demo/
├── pom.xml
└── src/main/java/demo/FallbackDemo.java
~~~

Tạo tệp `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>spring-fallback-demo</artifactId>
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
                    <mainClass>demo.FallbackDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 5.2. Java source hoàn chỉnh

Tạo `src/main/java/demo/FallbackDemo.java`:

~~~java
package demo;

import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Import;

public final class FallbackDemo {
    private FallbackDemo() {}

    public interface MessageCodec {
        String name();
        String render(String message);
    }

    public record PrefixCodec(String name, String prefix)
            implements MessageCodec {
        @Override
        public String render(String message) {
            return prefix + message;
        }
    }

    public static final class Processor {
        private final MessageCodec selected;
        private final List<MessageCodec> all;

        public Processor(MessageCodec selected, List<MessageCodec> all) {
            this.selected = Objects.requireNonNull(selected);
            this.all = List.copyOf(all);
        }

        public String selectedName() {
            return selected.name();
        }

        public String allNames() {
            return all.stream()
                    .map(MessageCodec::name)
                    .sorted()
                    .toList()
                    .toString();
        }

        public String process(String message) {
            return selected.render(message);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class DefaultsConfig {
        @Bean
        @Fallback
        public MessageCodec defaultCodec() {
            return new PrefixCodec("fallback", "DEFAULT:");
        }

        @Bean
        public Processor processor(MessageCodec codec,
                                   List<MessageCodec> allCodecs) {
            return new Processor(codec, allCodecs);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(DefaultsConfig.class)
    public static class ExtensionConfig {
        @Bean
        public MessageCodec customCodec() {
            return new PrefixCodec("custom", "CUSTOM:");
        }
    }

    public static void main(String[] args) {
        inspect("-- fallback only --", DefaultsConfig.class);
        inspect("-- with regular bean --", ExtensionConfig.class);
    }

    private static void inspect(String heading, Class<?> configuration) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(configuration)) {
            Processor processor = context.getBean(Processor.class);
            System.out.println(heading);
            System.out.println("SELECTED " + processor.selectedName());
            System.out.println("ALL " + processor.allNames());
            System.out.println("RESULT " + processor.process("HELLO"));
            System.out.println("DEFAULT_DIRECT "
                    + context.getBean("defaultCodec", MessageCodec.class)
                             .render("HELLO"));
        }
    }
}
~~~

Trong `DefaultsConfig`, bean `defaultCodec` là fallback. Vì nó là bean `MessageCodec` duy nhất trong context đầu, Spring vẫn chọn nó cho tham số `MessageCodec codec`.

Trong `ExtensionConfig`, `@Import(DefaultsConfig.class)` làm cả `defaultCodec` lẫn `customCodec` xuất hiện. Vì có đúng một ứng viên **không đánh dấu fallback** là `customCodec`, container chọn nó cho tham số đơn `codec`. Tuy nhiên, `List<MessageCodec> allCodecs` chứa cả hai bean.

**Những quyết định đảm bảo stdout ổn định:**

- `PrefixCodec` không in gì trong constructor hoặc factory method.
- `Processor.allNames()` sắp xếp tên bằng `sorted()` thay vì phụ thuộc thứ tự bean được liệt kê.
- Hai contexts tách biệt, mỗi context đóng qua try-with-resources.
- Lookup `defaultCodec` có tên tường minh và loại bean đúng.

### 5.3. Lệnh chạy và stdout dự kiến

Tại thư mục `spring-fallback-demo`:

~~~bash
mvn -version
mvn -q compile exec:java
~~~

Vì chương trình phụ thuộc Spring jars, lệnh `javac/java` trực tiếp đòi classpath đầy đủ; Maven quản lý dependency và main class cho bản chạy trên. `maven.compiler.release=17` bảo đảm compile theo Java 17.

**Stdout do code chủ động in theo đúng thứ tự:**

~~~text
-- fallback only --
SELECTED fallback
ALL [fallback]
RESULT DEFAULT:HELLO
DEFAULT_DIRECT DEFAULT:HELLO
-- with regular bean --
SELECTED custom
ALL [custom, fallback]
RESULT CUSTOM:HELLO
DEFAULT_DIRECT DEFAULT:HELLO
~~~

Lệnh `mvn -version` in phiên bản; Maven có thể in thông tin tải dependencies hoặc plugin logs. Khối trên chỉ biểu diễn các dòng `System.out.println` trong `FallbackDemo`.

### 5.4. Phân tích kết quả

1. `AnnotationConfigApplicationContext(DefaultsConfig.class)` refresh context đầu và đăng ký `defaultCodec` cùng `processor`.
2. `@Fallback` không làm `defaultCodec` biến mất. `processor(MessageCodec codec, ...)` nhận fallback vì không còn candidate nào khác cùng type.
3. `Processor` in tên `fallback`; list có một bean nên `ALL [fallback]`.
4. `process("HELLO")` trả `DEFAULT:HELLO`. Lookup theo tên `defaultCodec` cũng trả implementation mặc định.
5. Context thứ hai import cấu hình default và bổ sung `customCodec` như **bean thường**, không gắn `@Primary`.
6. Khi Spring tạo `processor`, type `MessageCodec` có hai candidates nhưng một candidate fallback. Spring ưu tiên bean `customCodec` cho injection đơn nên `SELECTED custom`.
7. `List<MessageCodec>` vẫn nhận cả hai candidates, nên `ALL [custom, fallback]` sau khi sort.
8. `process("HELLO")` dùng custom codec trả `CUSTOM:HELLO`; lookup trực tiếp `defaultCodec` vẫn trả `DEFAULT:HELLO`.
9. `@Fallback` chỉ quyết định lúc autowiring; nó **không thay dependency của một Processor đã được tạo** khi bạn thêm bean definition hay thay environment về sau.

Ví dụ không dùng `@ComponentScan`; tất cả beans được khai báo bằng `@Configuration`/`@Bean`. Không có Spring Boot auto-configuration hoặc điều kiện đặc biệt nào tác động tới việc chọn bean.

## 6. Java 17 thuần: mô phỏng quy tắc fallback để hiểu rõ

Java 17 không có `@Fallback` của Spring và không tự tạo container. Để minh họa tiêu chí chọn, hãy viết một chương trình riêng thực hiện **phiên bản rút gọn** của quy tắc:

- Nếu có đúng **một bean thường**, chọn nó.
- Nếu **không có bean thường** nhưng chỉ có một candidate, chọn candidate ấy.
- Nếu còn nhiều ứng viên không phân giải được, báo mơ hồ.

Đây là **mô hình giảng dạy**, không thay thế thuật toán Spring thực tế (còn có qualifier, primary, scope, type matching và các quy tắc resolution khác).

Tạo file `FallbackSelection.java`:

~~~java
import java.util.List;

public final class FallbackSelection {
    public record Candidate(String name, boolean fallback) {}

    static Candidate select(List<Candidate> candidates) {
        List<Candidate> regular = candidates.stream()
                .filter(candidate -> !candidate.fallback())
                .toList();
        List<Candidate> considered =
                regular.isEmpty() ? candidates : regular;

        if (considered.size() != 1) {
            throw new IllegalStateException(
                    "ambiguous: " + considered.size());
        }
        return considered.get(0);
    }

    public static void main(String[] args) {
        Candidate base = new Candidate("default", true);
        Candidate custom = new Candidate("custom", false);
        Candidate another = new Candidate("another", false);

        System.out.println("BASE "
                + select(List.of(base)).name());
        System.out.println("CUSTOM "
                + select(List.of(base, custom)).name());

        try {
            select(List.of(base, custom, another));
        } catch (IllegalStateException ex) {
            System.out.println("MULTIPLE " + ex.getMessage());
        }
    }
}
~~~

Tại thư mục chứa file này, chạy JDK 17+:

~~~bash
javac --release 17 -encoding UTF-8 FallbackSelection.java
java FallbackSelection
~~~

**Stdout chính xác:**

~~~text
BASE default
CUSTOM custom
MULTIPLE ambiguous: 2
~~~

Dòng cuối thể hiện trường hợp có **hai bean thường** còn tồn tại sau khi bỏ qua bean fallback để chọn một dependency đơn. Không có quy tắc `@Primary` hoặc qualifier nào được mô phỏng nên code báo mơ hồ. Đây chính là lý do `@Fallback` **không thay được** toàn bộ bean candidate resolution.

## 7. Điều kiện phiên bản và khả năng tương thích

### 7.1. Version tối thiểu

- `@Fallback` được giới thiệu **từ Spring Framework 6.2.0**.
- Package và import chính xác: `org.springframework.context.annotation.Fallback`.
- Với `spring-context:6.2.0`, demo dùng API có thật và Java 17, không cần Java preview syntax.
- **Spring Framework 6.1.x trở xuống không có `@Fallback`**; thêm import trên những phiên bản ấy gây lỗi biên dịch kiểu `cannot find symbol`/`Fallback cannot be resolved to a type`.
- Không nhầm `@Fallback` với `@Primary`, vốn đã có ở các phiên bản Spring trước đó.

### 7.2. Spring Boot và quản lý phiên bản dependency

Nếu project dùng Spring Boot, phiên bản Spring Framework thường do **BOM/dependency management** của Boot quyết định. Đừng mặc định rằng phiên bản được viết trong `pom.xml` của một module phụ sẽ luôn là phiên bản framework thực tế khi chạy một ứng dụng khác.

Khi nghi ngờ version mismatch:

~~~bash
mvn -version
mvn dependency:tree -Dincludes=org.springframework:spring-context
~~~

Nếu project dùng Gradle, kiểm tra dependency resolution bằng công cụ tương ứng như `./gradlew dependencies --configuration runtimeClasspath`, với tên configuration tùy project. Lệnh Maven trên không yêu cầu chạy ứng dụng; nó giúp nhìn `spring-context` được resolve thành version nào.

**Nên đồng bộ các module Spring Framework** qua BOM hoặc dependency management tương thích. Ép riêng một JAR Spring lên 6.2 trong một ứng dụng vẫn có các JAR lõi Spring 6.1 có thể tạo classloading/compatibility problems. Muốn dùng `@Fallback`, bảo đảm toàn bộ Spring dependency graph phù hợp, không chỉ biên dịch được một source file.

### 7.3. Nếu chưa thể nâng lên 6.2

Trong Spring cũ, một cách tương đối đơn giản là:

- Đánh dấu implementation muốn ưu tiên bằng `@Primary` nếu bạn kiểm soát cấu hình ứng dụng.
- Hoặc dùng `@Qualifier` để chọn đúng implementation tại injection point.
- Hoặc thiết kế conditional bean registration bằng cơ chế/framework thích hợp, nếu thực sự muốn **không đăng ký** default bean khi có custom bean.

Đây là các **phương án thay thế theo yêu cầu cụ thể**, không có nghĩa một annotation `@Primary` trên default bean sẽ cho cùng hành vi với `@Fallback`. Nếu đánh dấu **default** là primary, nó sẽ được ưu tiên thay vì nhường chỗ cho custom bean — ngược hẳn mục tiêu.

### 7.4. Java 17 và import

Spring Framework 6.2 yêu cầu nền Java phù hợp với thế hệ Spring 6; dùng **Java 17+** là lựa chọn nền tảng cho các ví dụ ở đây. Mã của mục 5 có record, stream `toList()` và try-with-resources — đều được Java 17 hỗ trợ.

Annotation có thể dùng trên class hoặc method:

~~~java
@Fallback
class DefaultCodec implements MessageCodec {
    // ...
}

@Bean
@Fallback
MessageCodec defaultCodec() {
    return new DefaultCodec();
}
~~~

Đây là hai kiểu khai báo **minh họa thay thế nhau**. Không copy cả hai vào cùng bean graph rồi kỳ vọng chỉ có một fallback bean: mỗi bean definition sẽ được Spring xét riêng.

## 8. Những điều @Fallback KHÔNG làm

| Hiểu nhầm | Sự thật |
| --- | --- |
| "Bean fallback chỉ được tạo nếu không có bean thường." | Không; annotation không phải điều kiện đăng ký/khởi tạo bean. |
| "Fallback tự xóa default bean khi custom bean xuất hiện." | Không; default bean vẫn nằm trong context. |
| "Fallback có nghĩa là failover khi bean chính ném exception." | Không; đây là metadata chọn candidate, không phải cơ chế chịu lỗi runtime. |
| "Có @Fallback rồi, hai bean thường sẽ tự hết mơ hồ." | Không; hai beans thường vẫn cần rule chọn. |
| "Fallback luôn bị loại khỏi `List<T>` hoặc `Map<String,T>`." | Không; collection/map/provid​er streams vẫn có các beans phù hợp. |
| "Đánh dấu @Fallback lên injection parameter." | Không đúng target; annotation dành cho bean class hoặc factory method. |
| "Fallback giống @Primary." | Ngược hướng ưu tiên: primary nâng ưu tiên, fallback nhường chỗ cho bean thường. |
| "Fallback giống @Qualifier." | Qualifier lọc theo tiêu chí; fallback đánh dấu mức ưu tiên trong giải quyết dependency đơn. |
| "Fallback dùng được trên Spring 6.1." | Không; cần Spring 6.2+. |
| "Fallback luôn làm bean @Lazy." | Không; lazy initialization là cấu hình riêng. |
| "Bean fallback là candidate sai kiểu cũng được inject." | Không; cần match type/metadata của dependency. |
| "Annotation tự động chọn bean theo nội dung từng request." | Không; autowiring không phải business runtime routing. |

### Khi có nhiều beans fallback

Nếu chỉ có hai beans fallback cùng type, injection đơn vẫn có thể không chọn được duy nhất. Đừng tưởng "cả hai là backup" có nghĩa Spring sẽ tự dùng bản được khai báo trước. Nếu muốn một lựa chọn mặc định cố định trong nhóm, cần một rule chọn thêm hoặc đơn giản hóa bean graph; nếu muốn cả hai, inject collection.

### @Fallback và @Qualifier cùng tồn tại

Qualifier thường giúp chọn **một tập ứng viên hợp lệ cho injection point**. Fallback vẫn mang ý nghĩa mức ưu tiên khi Spring cần quyết định ứng viên đơn. Vì vậy lựa chọn theo tên/qualifier có thể chủ động đưa một bean fallback vào vị trí cần dùng; nhưng đừng dựa vào một thứ tự ưu tiên tưởng tượng áp dụng cho mọi cấu hình. Khi có nhiều qualifier/primary/fallback, kiểm thử bean graph bằng integration test là cách tốt nhất.

## 9. Kiểm thử hành vi selection

Bài demo xác minh hai trường hợp quan trọng mà không cần network/database:

- Chỉ fallback: processor nhận `defaultCodec`.
- Fallback + một regular bean: processor nhận `customCodec`, nhưng `List<MessageCodec>` vẫn chứa cả hai.

Trong dự án thực, nên thêm integration tests:

1. Context chỉ có bean fallback → inject thành công.
2. Context có fallback và một bean thường → chọn bean thường.
3. Context có fallback và **hai bean thường** → xem kết quả theo qualifier/primary/name matching thực tế; nếu thiếu rule phải phát hiện ambiguity.
4. Inject `List<T>` hoặc typed `Map<String,T>` → xác minh bean fallback vẫn có mặt.
5. Lookup bean fallback theo tên → bean vẫn được phép truy cập.
6. Kết quả dependency tree thể hiện Spring Framework phiên bản 6.2+ đồng bộ.

Không nên viết unit test chỉ assert source có `@Fallback` mà không khởi tạo context: annotation đứng trên bean là metadata, cần một Spring container thật để xác nhận injection resolution.

### Dấu hiệu lỗi và cách debug

- `cannot find symbol: Fallback`: version Spring cũ hoặc import sai package.
- `NoSuchBeanDefinitionException`: không có candidate matching type/qualifier, hoặc configuration chưa được register.
- `NoUniqueBeanDefinitionException`: vẫn có nhiều candidates cuối cùng, thường là nhiều bean thường cùng type.
- `BeanCreationException`: candidate hoặc factory method lỗi khi khởi tạo; `@Fallback` không tự xử lý exception này.
- App đã nâng Spring nhưng vẫn chọn bean khác dự kiến: kiểm tra qualifier, `@Primary`, bean name, `@Fallback` trên đúng bean definition, và thứ tự/nguồn cấu hình.
- Collection không có fallback như mong đợi: kiểm tra fallback bean có thực sự được đăng ký và có type tương thích hay không, không tự quy lỗi cho annotation.

## 10. Bài tập kèm đáp án

**1.** Chỉ có một `@Fallback MessageCodec`, injection đơn `MessageCodec` có dùng được không?

**Đáp án:** Có. Khi nó là candidate duy nhất, Spring có thể inject bean fallback.

**2.** Thêm một bean `MessageCodec` thông thường, không có `@Primary`; Spring chọn ai?

**Đáp án:** Với đúng một bean thường phù hợp, Spring chọn bean thường, không chọn fallback.

**3.** Nếu có hai beans thường và một fallback, injection đơn chắc chắn chọn bean thường khai báo trước?

**Đáp án:** Không. Khi còn hai candidates thường, cần một quy tắc rõ để chọn cuối cùng; nếu không có có thể bị lỗi mơ hồ.

**4.** `List<MessageCodec>` có hai phần tử khi context có một fallback và một regular bean không?

**Đáp án:** Có, nếu cả hai cùng type và là ứng viên autowire phù hợp. Annotation fallback không loại bean khỏi collection.

**5.** `@Fallback` có làm `getBean("defaultCodec")` thất bại không?

**Đáp án:** Không. Bean vẫn được đăng ký; explicit lookup theo tên có thể lấy nó.

**6.** Có thể dùng `@Fallback` thay circuit breaker nếu implementation chính ngắt mạng?

**Đáp án:** Không. Fallback annotation chỉ xử lý candidate selection lúc wiring, không retry/failover tự động giữa các lần gọi nghiệp vụ.

**7.** Tại sao Spring 6.1 báo không tìm thấy class `Fallback`?

**Đáp án:** Vì annotation được thêm từ Spring Framework 6.2. Muốn dùng cần nâng framework phù hợp hoặc chọn cách cấu hình tương thích version cũ.

**8.** `@Primary` có giống `@Fallback` không?

**Đáp án:** Không. Primary đánh dấu bean được ưu tiên; fallback đánh dấu bean sẽ nhường chỗ khi có lựa chọn thông thường phù hợp.

**9.** Một bean `@Fallback` có thể xuất hiện trong `ObjectProvider<T>.stream()` không?

**Đáp án:** Có, nếu type và các điều kiện autowire phù hợp; fallback không tự loại nó khỏi provider stream.

**10.** Nếu framework starter cung cấp default implementation, nên dùng `@Primary` lên default hay `@Fallback`?

**Đáp án:** Trong Spring 6.2+, `@Fallback` thường thích hợp khi muốn bean do ứng dụng đăng ký **được ưu tiên tự nhiên** mà không cần người dùng ghi `@Primary`. Nếu starter muốn chỉ tạo bean khi hoàn toàn chưa có bean loại đó, cần xét **conditional registration** riêng, không đánh đồng với fallback metadata.

**11.** Vì sao demo sắp xếp tên trong `allNames()`?

**Đáp án:** Để stdout không phụ thuộc thứ tự Spring đưa bean vào collection; chuỗi `[custom, fallback]` ổn định.

## 11. Checklist áp dụng trong dự án

- [ ] Đã xác định yêu cầu Java 17+ và Spring Framework **6.2+** nếu dùng `@Fallback`.
- [ ] Import đúng `org.springframework.context.annotation.Fallback`.
- [ ] Annotation đặt trên bean class hoặc `@Bean` factory method, không trên injection parameter.
- [ ] Có bean default đăng ký hợp lệ; không tưởng annotation tự tạo bean.
- [ ] Dependency đơn chỉ còn một bean thường nếu muốn nó thắng fallback một cách tự nhiên.
- [ ] Không nghĩ `@Fallback` loại bean khỏi collection/Map/provider stream.
- [ ] Nếu có nhiều regular beans, thiết kế rule chọn rõ bằng qualifier/primary khi phù hợp.
- [ ] Không áp `@Primary` cho default nếu mong custom bean luôn được ưu tiên.
- [ ] Không coi fallback bean là điều kiện `@ConditionalOnMissingBean` hay failover runtime.
- [ ] Kiểm tra Spring dependency tree/bom để tránh sai phiên bản.
- [ ] Unit test logic xử lý riêng với integration test candidate resolution.
- [ ] Test cả context có và không có bean override.
- [ ] Không dựa vào thứ tự quét package làm rule chọn bean.
- [ ] Với bean singleton, cân nhắc lifecycle/thread safety riêng; annotation fallback không ảnh hưởng các yêu cầu đó.
- [ ] Khi giữ backward compatibility với Spring trước 6.2, không import API chưa có.
- [ ] Kiểm tra stdout ứng dụng tách khỏi log Maven.

### Tài liệu chính thức

- [Fine-tuning Annotation-based Autowiring with @Primary or @Fallback](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-primary.html)
- [@Fallback API — Spring Framework 6.2](https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/context/annotation/Fallback.html)
- [Spring Framework 6.2 Release Notes — Support for fallback beans](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.2-Release-Notes)
- [Fine-tuning Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html)

**Kết luận:** `@Fallback` là một công cụ **ưu tiên bean candidate** có từ **Spring Framework 6.2**. Nó cho phép module đăng ký implementation mặc định nhưng tự nhường cho một implementation thông thường được ứng dụng bổ sung. Bean fallback vẫn tồn tại trong context và trong injection theo collection. Hiểu giới hạn này, điều kiện version và khác biệt với `@Primary`/`@Qualifier` giúp bạn cung cấp default behavior một cách chủ động mà không biến annotation thành cơ chế thay thế bean hay xử lý lỗi runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="candidate-selection-009">Giới hạn ứng viên bằng autowire-candidate</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn ứng viên bằng `autowire-candidate` trong Spring Core Container

## 1. Bean tồn tại không có nghĩa bean được phép autowire

Một Spring `ApplicationContext` có thể đăng ký nhiều beans cùng Java type. Ví dụ `activeTransport` phục vụ nghiệp vụ chính, còn `internalTransport` là implementation dùng để bảo trì hoặc kiểm thử một đường xử lý đặc biệt. Cả hai implement `Transport`, nhưng service thông thường **không nên tự nhận** `internalTransport` chỉ vì nó có type tương thích.

Spring Core Container tách những khái niệm sau:

1. **Bean registration:** bean có tên, định nghĩa và thuộc container hay không?
2. **Type matching:** bean có kiểu có thể gán vào kiểu mà injection point yêu cầu hay không?
3. **Autowire candidate eligibility:** bean matching type có **được tham gia autowiring** không?
4. **Candidate selection:** khi có nhiều candidates được phép, `@Primary`, `@Qualifier`, `@Fallback` và các quy tắc khác có chọn được bean phù hợp không?
5. **Explicit reference:** caller/cấu hình chủ động tra một bean theo **tên tường minh** có còn được sử dụng bean ấy không?

Cơ chế **`autowire-candidate=false`** (XML) hoặc **`@Bean(autowireCandidate=false)`** (Java configuration) đặt giới hạn vào **mục 3**. Nó không xóa bean và **không cấm mọi lookup bằng tên**.

Ví dụ Spring Java configuration:

~~~java
// Snippet: Transport là interface có trong project hoàn chỉnh ở mục 7.
@Bean(name = "internalTransport", autowireCandidate = false)
Transport internalTransport() {
    return () -> "INTERNAL";
}
~~~

Bean `internalTransport` vẫn được đăng ký; `context.getBean("internalTransport", Transport.class)` vẫn có thể trả về instance. Tuy nhiên, Spring sẽ loại bean này khỏi việc chọn theo type tại những injection points như:

~~~java
@Autowired
private Transport transport;

// Hoặc factory method nhận dependency do Spring resolve:
@Bean
Report report(Transport selected) {
    return new Report(selected);
}
~~~

Hai đoạn trên chỉ minh họa, **không phải các file Java độc lập**. Chương trình có cấu trúc đầy đủ ở mục 7.

**Thông điệp chính:** Không thể dùng kết quả `getBeanNamesForType(Transport.class)` như một bản sao chính xác của danh sách beans mà `@Autowired Transport` sẽ cân nhắc. Một bean match type vẫn có thể bị đánh dấu **không phải autowire candidate**.

## 2. Cấu hình `autowire-candidate` ở đâu?

### 2.1. Trên bean definition XML

Trong XML, attribute đúng tên là **`autowire-candidate`**:

~~~xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
         http://www.springframework.org/schema/beans
         https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="activeTransport"
          class="demo.ActiveTransport"/>

    <bean id="internalTransport"
          class="demo.InternalTransport"
          autowire-candidate="false"/>
</beans>
~~~

Đây là ví dụ cấu hình XML **minh họa** với hai class độc lập giả định `demo.ActiveTransport`/`demo.InternalTransport`; dự án chạy ở mục 7 **không đọc XML** mà dùng `@Bean` và Java 17.

Giá trị `false` có nghĩa **định nghĩa `internalTransport` không được đưa vào autowire candidate set cho type-based injection**. Nếu XML cấu hình explicit dependency:

~~~xml
<bean id="specialReport" class="demo.SpecialReport">
    <constructor-arg ref="internalTransport"/>
</bean>
~~~

thì explicit `ref` **vẫn có thể hoạt động**; không được diễn giải flag thành “bean không tồn tại”. File XML trên chỉ minh họa điều đó; class `demo.SpecialReport` cũng là một class minh họa, không thuộc source Maven chạy độc lập phía dưới.

### 2.2. Trên method `@Bean`

Spring cung cấp thuộc tính `autowireCandidate` ngay trong `org.springframework.context.annotation.Bean`:

~~~java
@Configuration(proxyBeanMethods = false)
class Config {
    @Bean("activeTransport")
    Transport activeTransport() {
        return () -> "ACTIVE";
    }

    @Bean(name = "internalTransport", autowireCandidate = false)
    Transport internalTransport() {
        return () -> "INTERNAL";
    }
}
~~~

Cấu hình này tạo ra hai definitions `activeTransport` và `internalTransport` cùng type `Transport`. Chỉ `activeTransport` là candidate cho plain `@Autowired Transport`. **Flag nằm trên bean definition**, không nằm trong interface hay instance `Transport`.

### 2.3. Điều chỉnh bằng BeanDefinition API

Nếu thật sự cần điều khiển metadata bằng Java lúc đăng ký definitions (thường thuộc infrastructure code):

~~~java
// Snippet: chạy trước khi bean factory bắt đầu resolve dependencies.
BeanDefinition definition =
        registry.getBeanDefinition("internalTransport");
definition.setAutowireCandidate(false);
~~~

Snippet này cần kiểu `BeanDefinition`, `BeanDefinitionRegistry` và registration phase thích hợp; **không phải** lời khuyên để business service sửa bean definition khi ứng dụng đã chạy. Với code mới, thường khai `@Bean(autowireCandidate=false)` tường minh sẽ dễ kiểm tra hơn.

Spring bean definition cũng có `isAutowireCandidate()` để truy vấn metadata, rất hữu ích khi debug nguyên nhân “bean có trong context nhưng không được inject”.

### 2.4. Giá trị mặc định

Với phần lớn ordinary bean definitions, **autowire candidate mặc định là `true`**. Việc có một bean trùng type thường khiến nó có thể tham gia autowiring, nhưng còn tùy qualifiers, generic signatures, scope và các bộ lọc khác.

Đừng đánh `false` tùy tiện để né lỗi `NoUniqueBeanDefinitionException` mà không biết use case nào có thể cần bean đó. Đây là **chính sách đóng quyền tham gia DI**, có hệ quả ở nhiều injection points.

## 3. Có thể tra tên hoặc type, nhưng danh sách injection đã bị lọc

So sánh bốn trường hợp:

| Thao tác | `activeTransport` bình thường | `internalTransport` `autowireCandidate=false` |
| --- | --- | --- |
| Đăng ký bean definition | Có | Có |
| `context.containsBean("...")` | Có | Có |
| `getBean("name", Transport.class)` | Có thể lấy | **Vẫn có thể lấy bằng tên** |
| `getBeanNamesForType(Transport.class)` | Thường xuất hiện | **Vẫn xuất hiện** nếu type tương thích |
| `getBeansOfType(Transport.class)` | Có thể xuất hiện trong map | **Vẫn có thể xuất hiện** vì đây là type discovery/lookup API |
| Plain `@Autowired Transport` | Được cân nhắc | **Bị loại** |
| `@Autowired List<Transport>` | Được xét vào collection | **Bị loại khỏi candidates tiêm** |
| `@Autowired Map<String,Transport>` | Được xét vào map | **Bị loại khỏi candidates tiêm** |
| `@Qualifier("internalTransport") Transport` | Không chọn bean không khớp qualifier | **Không làm excluded bean trở lại** type-based autowiring |

### Discovery khác injection

~~~java
String[] names =
        context.getBeanNamesForType(Transport.class);
Map<String, Transport> all =
        context.getBeansOfType(Transport.class);
~~~

Hai API trên là **khám phá/tra cứu beans theo type** trong `ListableBeanFactory`. Chúng không trực tiếp trả về “tập ứng viên đã lọc cho một điểm autowire cụ thể”. Một bean có thể vừa match type vừa bị gắn `autowireCandidate=false`.

Ngược lại, Spring resolve dependency cho field/constructor/factory-method parameter thông qua các quy tắc autowiring. **Kết quả có thể ít hơn type discovery**, đặc biệt khi có exclusions, qualifiers hoặc generic constraints.

### Name-based explicit reference còn hoạt động

~~~java
Transport internal =
        context.getBean("internalTransport", Transport.class);
~~~

Đây là **lệnh chủ động yêu cầu bean name**; flag autowire candidate không xóa `internalTransport`. Theo tài liệu Spring, explicit references bằng tên vẫn được resolve. XML `<ref bean="internalTransport"/>` và các cơ chế by-name explicit có nguyên tắc tương tự khi được sử dụng đúng.

**Cần phân biệt:** `@Qualifier("internalTransport")` trên `@Autowired Transport` **vẫn là type-based autowiring với filter qualifier**. Qualifier **không** chuyển injection ấy thành một explicit `getBean(name)` bỏ qua exclusion. Đó là lý do không được dùng qualifier như “cách phá khóa” `autowireCandidate=false`.

## 4. `autowireCandidate=false` không ngăn bean tự được cấu hình

Một điểm thường bị hiểu ngược:

> “Bean bị loại khỏi autowire candidates sẽ không còn được Spring inject dependencies vào chính nó.”

Sai. Flag mô tả **bean có được người khác autowire vào không**, không phải bean ấy có được Spring khởi tạo/cấp dependencies của chính nó không.

Ví dụ:

~~~java
// Snippet minh họa (đưa vào class config có @Bean phù hợp).
@Bean(name = "internalTransport", autowireCandidate = false)
Transport internalTransport(Settings settings) {
    return new InternalTransport(settings);
}
~~~

Trong đó `Settings` là bean hợp lệ và Spring có thể resolve tham số `Settings settings` để tạo `internalTransport`. Sau khi được tạo, `internalTransport` vẫn bị loại **khi một consumer khác yêu cầu Transport theo type**.

**Hai hướng phụ thuộc khác nhau:**

~~~text
          Settings (ordinary candidate)
                    |
                    | Inject Settings để TẠO bean
                    v
    internalTransport (autowireCandidate=false)
                    |
                    X Không được autowire THEO TYPE
                    |
                    v
               RegularConsumer

   Explicit getBean("internalTransport") vẫn có thể hoạt động
~~~

Khi viết hệ thống có plugin/internal delegates hoặc adapter hỗ trợ, đây là sự phân biệt quan trọng: ta muốn một bean hoạt động đầy đủ bên trong context, nhưng **không chen vào candidate set mặc định** của các services khác.

Bean đó vẫn chịu scope, constructor, field/method injection, initialization và destruction callbacks theo những quy tắc Spring áp dụng cho bean tương ứng. Flag không biến một Spring bean thành object ngoài container.

## 5. `autowireCandidate=false` khác `defaultCandidate=false`

Từ Spring Framework **6.2+**, `@Bean` có thêm hai biến thể quan trọng:

| Metadata | Không có qualifier, inject chỉ theo type | Có `@Qualifier` phù hợp | Explicit name lookup |
| --- | --- | --- | --- |
| `autowireCandidate=true, defaultCandidate=true` (mặc định) | Có thể là candidate | Có thể là candidate | Có |
| **`autowireCandidate=false`** | **Không được autowire** | **Vẫn không được autowire theo type** | **Có** |
| **`defaultCandidate=false`** nhưng autowireCandidate=true | Không phải lựa chọn mặc định chỉ theo type | **Có thể được chọn với qualifier** | Có |

### Restriction “cứng”: `autowireCandidate=false`

~~~java
@Bean(name = "internalTransport", autowireCandidate = false)
Transport internalTransport() {
    return () -> "INTERNAL";
}
~~~

Không dùng `@Autowired @Qualifier("internalTransport")` để đòi bean này như một type-based candidate thông thường. Nếu nghiệp vụ cần tham chiếu chính xác bean tên ấy, hãy diễn đạt đó là **explicit reference by name** tại composition root/bean wiring khi thích hợp.

### Restriction “có điều kiện”: `defaultCandidate=false`

~~~java
@Bean(name = "specialTransport", defaultCandidate = false)
Transport specialTransport() {
    return () -> "SPECIAL";
}
~~~

Bean này vẫn có `isAutowireCandidate=true`, nhưng được dự kiến chỉ tham gia vào những injection points có **chỉ báo bổ sung** như `@Qualifier("specialTransport")`.

Ví dụ:

~~~java
@Bean
NamedConsumer selectedClient(
        @Qualifier("specialTransport") Transport selected) {
    return new NamedConsumer(selected);
}
~~~

Đoạn này là snippet: class `NamedConsumer` phải được định nghĩa và import qualifier đúng. Dự án đầy đủ ở mục 7 minh họa cùng cơ chế với tên `restrictedTransport`.

**Khi cần “không bao giờ tự chọn khi inject theo type”, dùng `autowireCandidate=false`. Khi cần “không mặc định nhưng vẫn cho phép yêu cầu có qualifier”, cân nhắc `defaultCandidate=false`.** Không lẫn chúng với `@Primary`/`@Fallback`: hai annotation sau ảnh hưởng mức ưu tiên giữa các candidates đủ điều kiện, còn metadata candidates ảnh hưởng việc một bean có đủ điều kiện ngay từ đầu hay không.

## 6. `@Primary`, `@Qualifier`, `@Fallback` và tên field có cứu được bean bị exclude không?

### `@Primary` không ghi đè exclusion

Nếu bean `internalTransport` vừa gắn `autowireCandidate=false` vừa gắn `@Primary`:

~~~java
@Bean(name = "internalTransport", autowireCandidate = false)
@Primary
Transport internalTransport() {
    return () -> "INTERNAL";
}
~~~

Khi một service yêu cầu plain `Transport`, Spring vẫn **không sử dụng internalTransport làm autowire candidate**. `@Primary` chỉ có thể ưu tiên **ứng viên còn hợp lệ**; không biến một definition excluded thành candidate.

Trong demo, chúng ta **cố tình** đánh dấu `@Primary` trên bean bị loại để kiểm chứng quy tắc này. Sau đó metadata `isPrimary()` và `isAutowireCandidate()` đều được in; bạn sẽ thấy **primary=true nhưng autowireCandidate=false**, và service vẫn nhận bean bình thường.

### `@Qualifier` là filter, không phải explicit ref

~~~java
@Autowired
@Qualifier("internalTransport")
private Transport transport;
~~~

Khi bean `internalTransport` có `autowireCandidate=false`, đây vẫn là một injection point dựa trên type nên **không được phép lấy bean ấy chỉ nhờ qualifier**. Đừng nhầm `@Qualifier("beanName")` với `getBean("beanName")`; hai cách có ý nghĩa khác nhau.

### `@Fallback` không loại bean khỏi autowire candidates

Với Spring 6.2+, `@Fallback` đánh dấu bean là phương án dự phòng; nó **vẫn có thể là một autowire candidate** và tham gia collections, tùy qualifiers/eligibility. `@Primary` ưu tiên một candidate, `@Fallback` hạ độ ưu tiên so với non-fallback candidates, còn **`autowireCandidate=false` loại hẳn khỏi type-based autowiring**.

### Tên field/parameter không phá exclusion

Một field tên `internalTransport` với `@Autowired` không thể mặc định “khôi phục” autowiring cho bean excluded chỉ vì field trùng bean name. Tên injection point giúp chọn giữa candidates, **không nên coi là đường vòng qua điều kiện candidate eligibility**.

**Ngoại lệ cần phân biệt:** XML autowiring mode **`byName`** hoặc một explicit bean reference by name có semantics riêng; tài liệu Spring nêu rõ `autowire-candidate` không chặn explicit references theo tên. Không suy luận rằng mọi kỹ thuật nhìn có chữ “name” đều là cùng một loại injection.

## 7. Project Maven Java 17: nhìn rõ discovery, filtering và explicit lookup

Mục tiêu là **chạy ba context độc lập**:

1. **MainConfig:** có một ordinary bean `activeTransport` và một bean `internalTransport` mang `autowireCandidate=false` **và `@Primary`**. `getBeanNamesForType(Transport.class)` thấy cả hai; `@Bean Report report(Transport selected, List<Transport> ..., Map<String,Transport> ...)` chỉ được inject **ACTIVE**. `getBean("internalTransport")` vẫn trả **INTERNAL**.
2. **ExcludedOnlyConfig:** chỉ có một bean cùng type nhưng **không phải candidate**, trong khi một `@Bean` cần Transport required → context **khởi động thất bại**, chứng minh “khớp type nhưng bị exclude” vẫn không đủ.
3. **RestrictedConfig:** có `regularTransport` thường và `restrictedTransport` với `defaultCandidate=false`. Plain `Transport` được cấp **REGULAR**; tham số mang `@Qualifier("restrictedTransport")` được cấp **RESTRICTED**.

Code không cần Spring Boot, không dùng HTTP server hay database; tất cả kết quả ứng dụng do source chủ động in.

### 7.1. Cấu trúc thư mục

~~~text
candidate-restriction-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── CandidateRestrictionDemo.java
~~~

Yêu cầu **JDK 17+**, Maven **3.9+**, có thể tải Maven artifacts từ repository khi build lần đầu.

### 7.2. `pom.xml` hoàn chỉnh

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>candidate-restriction-demo</artifactId>
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
                    <mainClass>demo.CandidateRestrictionDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` kéo các Spring Core Container modules phụ thuộc qua Maven; plugin compiler dùng Java 17. Không cần Jakarta annotation API vì demo dùng Spring `@Bean`, `@Primary`, `@Qualifier`; không dùng `@Resource` trong chương trình chính.

### 7.3. `src/main/java/demo/CandidateRestrictionDemo.java`

~~~java
package demo;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

public final class CandidateRestrictionDemo {

    private CandidateRestrictionDemo() {
    }

    @FunctionalInterface
    interface Transport {
        String id();
    }

    record FixedTransport(String id) implements Transport {
    }

    static final class Report {
        private final Transport selected;
        private final List<Transport> available;
        private final Map<String, Transport> byName;

        Report(Transport selected, List<Transport> available,
                Map<String, Transport> byName) {
            this.selected = selected;
            this.available = available;
            this.byName = byName;
        }

        String chosen() {
            return selected.id();
        }

        String injectedIds() {
            return available.stream()
                    .map(Transport::id)
                    .sorted()
                    .collect(Collectors.joining(","));
        }

        String injectedNames() {
            return byName.keySet().stream()
                    .sorted()
                    .collect(Collectors.joining(","));
        }

        boolean containsInternal() {
            return byName.containsKey("internalTransport");
        }
    }

    static final class SingleConsumer {
        private final Transport selected;

        SingleConsumer(Transport selected) {
            this.selected = selected;
        }

        String chosen() {
            return selected.id();
        }
    }

    static final class RestrictedReport {
        private final Transport plain;
        private final Transport qualified;

        RestrictedReport(Transport plain, Transport qualified) {
            this.plain = plain;
            this.qualified = qualified;
        }

        String plain() {
            return plain.id();
        }

        String qualified() {
            return qualified.id();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class MainConfig {
        @Bean("activeTransport")
        public Transport activeTransport() {
            return new FixedTransport("ACTIVE");
        }

        @Bean(name = "internalTransport", autowireCandidate = false)
        @Primary
        public Transport internalTransport() {
            return new FixedTransport("INTERNAL");
        }

        @Bean
        public Report report(
                Transport selected,
                List<Transport> transports,
                Map<String, Transport> transportsByName) {
            return new Report(selected, transports, transportsByName);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ExcludedOnlyConfig {
        @Bean(name = "excludedOnly", autowireCandidate = false)
        public Transport excludedOnly() {
            return new FixedTransport("EXCLUDED");
        }

        @Bean
        public SingleConsumer needsTransport(Transport candidate) {
            return new SingleConsumer(candidate);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class RestrictedConfig {
        @Bean("regularTransport")
        public Transport regularTransport() {
            return new FixedTransport("REGULAR");
        }

        @Bean(name = "restrictedTransport", defaultCandidate = false)
        public Transport restrictedTransport() {
            return new FixedTransport("RESTRICTED");
        }

        @Bean
        public RestrictedReport restrictedReport(
                Transport plain,
                @Qualifier("restrictedTransport") Transport explicit) {
            return new RestrictedReport(plain, explicit);
        }
    }

    private static String sortedNames(String[] input) {
        Arrays.sort(input);
        return String.join(",", input);
    }

    private static String sortedKeys(Map<String, ?> input) {
        return input.keySet().stream()
                .sorted()
                .collect(Collectors.joining(","));
    }

    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(MainConfig.class)) {
            var report = context.getBean(Report.class);
            var factory = context.getBeanFactory();
            var internal =
                    context.getBean("internalTransport", Transport.class);

            System.out.println("main.typeNames=" + sortedNames(
                    context.getBeanNamesForType(Transport.class)));
            System.out.println("main.allTypeObjects=" + sortedKeys(
                    context.getBeansOfType(Transport.class)));
            System.out.println("main.internalRegistered="
                    + context.containsBeanDefinition("internalTransport"));
            System.out.println("main.internalCandidate="
                    + factory.getBeanDefinition(
                            "internalTransport").isAutowireCandidate());
            System.out.println("main.internalPrimary="
                    + factory.getBeanDefinition(
                            "internalTransport").isPrimary());
            System.out.println("main.activeCandidate="
                    + factory.getBeanDefinition(
                            "activeTransport").isAutowireCandidate());
            System.out.println("main.selected=" + report.chosen());
            System.out.println("main.injectedList=" + report.injectedIds());
            System.out.println("main.injectedMap=" + report.injectedNames());
            System.out.println("main.mapContainsInternal="
                    + report.containsInternal());
            System.out.println("main.explicitByName=" + internal.id());
            System.out.println("main.explicitSame="
                    + (internal == context.getBean(
                            "internalTransport", Transport.class)));
        }

        // Chỉ có excluded bean cùng type: không đủ điều kiện autowiring.
        try (var context = new AnnotationConfigApplicationContext(
                ExcludedOnlyConfig.class)) {
            System.out.println("excludedOnly.unexpected");
        } catch (BeansException expected) {
            System.out.println("excludedOnly.failed=true");
        }

        try (var context = new AnnotationConfigApplicationContext(
                RestrictedConfig.class)) {
            var report = context.getBean(RestrictedReport.class);
            var factory = context.getBeanFactory();

            System.out.println("restricted.typeNames=" + sortedNames(
                    context.getBeanNamesForType(Transport.class)));
            System.out.println("restricted.plain=" + report.plain());
            System.out.println("restricted.qualified=" + report.qualified());
            System.out.println("restricted.canAutowire="
                    + factory.getBeanDefinition(
                            "restrictedTransport").isAutowireCandidate());
            System.out.println("restricted.defaultCandidate="
                    + factory.getBeanDefinition(
                            "restrictedTransport").isDefaultCandidate());
            System.out.println("restricted.byName="
                    + context.getBean(
                            "restrictedTransport", Transport.class).id());
        }
    }
}
~~~

### 7.4. Đọc chính xác ba thí nghiệm

**MainConfig — internal không tham gia autowiring:**

- Spring tạo definitions cho hai beans type `Transport` và bean `Report`.
- `internalTransport` vẫn có metadata `isPrimary=true`, nhưng `isAutowireCandidate=false`. Do đó `@Primary` trên bean ấy **không đè** điều kiện excluded.
- Tham số `Transport selected` tại factory method `report` nhận `activeTransport` (ứng viên hợp lệ duy nhất).
- `List<Transport>` và `Map<String,Transport>` được inject vào `Report` chỉ chứa ACTIVE, **không** chứa INTERNAL.
- `getBeanNamesForType(Transport.class)` và `getBeansOfType(Transport.class)` vẫn quan sát thấy hai bean type matches.
- `context.getBean("internalTransport", Transport.class)` vẫn hoạt động. Hai lần lookup theo tên trên một context trả cùng instance vì scope mặc định là singleton.

**ExcludedOnlyConfig — type match nhưng không có candidate:**

- Context có đúng một `Transport` type match, nhưng bean ấy `autowireCandidate=false`.
- `needsTransport(Transport candidate)` là required injection point; không có eligible candidate nên Spring không thể tạo bean `SingleConsumer` và context `refresh()` thất bại.
- `BeansException` được catch có chủ đích. **Không** dựa vào exact exception subclass/stack trace để chốt expected stdout.

**RestrictedConfig — defaultCandidate khác autowireCandidate:**

- `regularTransport` mặc định autowire candidate và default candidate.
- `restrictedTransport` được gắn `defaultCandidate=false` nhưng `isAutowireCandidate=true`.
- Tham số `Transport plain` chọn REGULAR khi không qualifier, còn `@Qualifier("restrictedTransport") Transport explicit` nhận RESTRICTED.
- `getBean("restrictedTransport")` cũng hoạt động; không phải vì setting là `true` hay `false`, mà vì đây là explicit lookup by name.

**Vì sao không có race/order dependency trong output:** ba contexts được tạo rồi đóng **lần lượt**. Mỗi method factory không cần gọi một `@Bean` khác trực tiếp; `proxyBeanMethods=false` là phù hợp. Phần liệt kê tên được sort; giá trị trả từ `FixedTransport` là cố định.

## 8. CLI và expected stdout

Đứng trong thư mục `candidate-restriction-demo` chứa `pom.xml`, chạy:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

Ba lệnh phiên bản có output phụ thuộc môi trường. Lệnh cuối compile target Java 17 qua Maven Compiler Plugin rồi chạy main class với Spring dependencies.

**19 dòng stdout do chính chương trình Java in** (không tính Maven/Spring logging, warning hoặc exception diagnostics):

~~~text
main.typeNames=activeTransport,internalTransport
main.allTypeObjects=activeTransport,internalTransport
main.internalRegistered=true
main.internalCandidate=false
main.internalPrimary=true
main.activeCandidate=true
main.selected=ACTIVE
main.injectedList=ACTIVE
main.injectedMap=activeTransport
main.mapContainsInternal=false
main.explicitByName=INTERNAL
main.explicitSame=true
excludedOnly.failed=true
restricted.typeNames=regularTransport,restrictedTransport
restricted.plain=REGULAR
restricted.qualified=RESTRICTED
restricted.canAutowire=true
restricted.defaultCandidate=false
restricted.byName=RESTRICTED
~~~

### Nhóm output trọng tâm

| Output | Điều được kiểm chứng |
| --- | --- |
| `main.typeNames=activeTransport,internalTransport` | Type discovery nhận ra bean bị excluded |
| `main.internalCandidate=false` | Metadata definition chủ động loại internal khỏi autowiring |
| `main.internalPrimary=true` | Primary vẫn có trong metadata nhưng **không cứu** bean excluded |
| `main.injectedList=ACTIVE` | Autowired collection chỉ gồm eligible beans |
| `main.injectedMap=activeTransport` | Autowired map không có internal |
| `main.explicitByName=INTERNAL` | Name lookup vẫn lấy được excluded bean |
| `excludedOnly.failed=true` | Chỉ có excluded bean không đủ để thỏa required injection |
| `restricted.plain=REGULAR` | Bean defaultCandidate=false không chen vào plain type injection |
| `restricted.qualified=RESTRICTED` | Qualifier cho phép restricted default candidate được inject |
| `restricted.canAutowire=true` | `defaultCandidate=false` không đồng nghĩa `autowireCandidate=false` |

Chạy một bản sửa đổi khác với defaultCandidate/qualifier/primary/beans có thể làm các dòng trên đổi; dùng source gốc để đối chiếu. Spring có thể log context startup failure ở case excluded-only, nhưng expected output chỉ tính `System.out.println` của code.

## 9. Java 17 thuần: phân biệt registry và eligible candidates

Để hiểu cơ chế mà không cần Spring, tạo file độc lập **`ManualCandidateFilter.java`**:

~~~java
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ManualCandidateFilter {
    interface Transport {
        String id();
    }

    public static void main(String[] args) {
        Map<String, Transport> registered = Map.of(
                "core", () -> "CORE",
                "internal", () -> "INTERNAL");
        Set<String> excluded = Set.of("internal");

        String names = registered.keySet().stream()
                .sorted().collect(Collectors.joining(","));
        String eligible = registered.keySet().stream()
                .filter(name -> !excluded.contains(name))
                .sorted().collect(Collectors.joining(","));

        System.out.println("registered=" + names);
        System.out.println("eligible=" + eligible);
        System.out.println("byType=" + registered.get("core").id());
        System.out.println("explicit=" + registered.get("internal").id());
    }
}
~~~

Lệnh Java thuần, chạy từ thư mục chứa file:

~~~bash
javac ManualCandidateFilter.java
java ManualCandidateFilter
~~~

Stdout chính xác:

~~~text
registered=core,internal
eligible=core
byType=CORE
explicit=INTERNAL
~~~

**Đây là mô hình tư duy bằng Java collections, không phải một bản sao thuật toán Spring.** `Map` không có BeanDefinition, qualifier, scope hoặc lifecycle. Điểm duy nhất cần nhìn là: object có trong registry (`registered`) **khác** object được phép chọn tự động (`eligible`), nhưng explicit lookup theo tên vẫn có thể lấy một object đã excluded.

## 10. XML pattern `default-autowire-candidates` và precedence

Ngoài cấu hình per bean, XML cho phép giới hạn **theo mẫu bean name** bằng `default-autowire-candidates` ở **top-level `<beans>`**:

~~~xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
         http://www.springframework.org/schema/beans
         https://www.springframework.org/schema/beans/spring-beans.xsd"
       default-autowire-candidates="*Repository,*Gateway">

    <bean id="orderRepository" class="demo.OrderRepository"/>
    <bean id="internalHelper" class="demo.InternalHelper"/>

    <bean id="explicitHelper"
          class="demo.InternalHelper"
          autowire-candidate="true"/>

    <bean id="blockedGateway"
          class="demo.InternalHelper"
          autowire-candidate="false"/>
</beans>
~~~

Đây là đoạn XML **giải thích chính sách**, giả định các demo classes `demo.OrderRepository`/`demo.InternalHelper` có tồn tại trong một project XML riêng.

Các mẫu `*Repository,*Gateway` dùng để chỉ những bean names phù hợp **mặc định** có thể làm autowire candidates. Nếu `internalHelper` không khớp mẫu, nó mặc định bị loại. Nhưng **khai tường minh** `autowire-candidate="true"` hoặc `"false"` trên một bean definition **được ưu tiên** so với pattern.

**Tại sao dùng tên để quy định candidate eligibility?** Đôi khi module có nhiều internal adapters không muốn expose cho autowiring toàn cục, và naming conventions đã tương đối chặt chẽ. Tuy nhiên, đổi bean name có thể làm nó bị lọc khác, nên cách này cần conventions/tests rõ ràng.

**Không nhầm** XML `default-autowire-candidates` (pattern, áp dụng theo bean names) với thuộc tính Java `@Bean(defaultCandidate=false)` (restricted default selection theo type/qualifier). Hai cơ chế này khác nhau cả vị trí khai báo lẫn semantics.

## 11. Các use case nên và không nên dùng exclusion

### Tình huống phù hợp

- **Infrastructure helper:** implementation cùng interface với các beans nghiệp vụ nhưng chỉ dùng nội bộ bởi một adapter riêng.
- **Diagnostic/probe client:** một client giả lập dùng để kiểm thử đường xử lý khác, không nên là lựa chọn tự động cho service sản phẩm.
- **Legacy compatibility bean:** bean vẫn phải tồn tại để giữ explicit references trong cấu hình cũ, nhưng không tham gia type-based autowiring mới.
- **Tách public API và internal delegate:** có bean được cấp theo đường explicit wiring ở composition root, nhưng các component khác inject interface theo type cần một implementation mặc định rõ ràng.

### Tình huống không nên dùng để che vấn đề khác

- Có nhiều implementations cùng type, nhưng mọi implementation đều hợp lệ cho một số use case: nên dùng **qualifier** hoặc policy `@Primary` thay vì exclude bừa một bean mà module khác cần.
- Một dependency **thật sự optional**: hãy diễn đạt bằng `Optional<T>`, `ObjectProvider<T>`, setter optional hoặc no-op strategy hợp lệ; exclusion không làm dependency required thành optional.
- Circular dependencies: thay đổi candidate filtering không phá vòng phụ thuộc giữa các objects.
- Bean bị thiếu do chưa đăng ký/cấu hình sai: đặt autowireCandidate=false khiến tình hình khó hiểu hơn.
- Dùng flag để tránh thiết kế interface phân biệt trách nhiệm: nếu một interface bị implement bởi 20 delegates không liên quan, hãy xem lại abstraction/module boundary.

Cũng không nên gắn `@Primary` cho excluded bean với hy vọng nó được chọn lại. **Primary là ưu tiên giữa candidates; exclusion là cắt một bean ra khỏi tập ấy.**

## 12. Lỗi thường gặp và cách debug theo đúng thứ tự

| Hiện tượng | Nguyên nhân khả dĩ | Hướng xử lý |
| --- | --- | --- |
| Bean có trong `getBeanNamesForType` nhưng không được inject | `autowireCandidate=false` hoặc qualifier/generic filtering | Kiểm `BeanDefinition.isAutowireCandidate()` và injection point |
| `@Autowired Transport` báo thiếu dù có một bean cùng type | Bean ấy bị loại khỏi autowiring | Bỏ exclusion nếu đúng contract hoặc explicit wiring |
| Thêm `@Qualifier("internalTransport")` vẫn lỗi | Qualifier không biến excluded bean thành type candidate | Dùng đúng loại reference by name hoặc đổi metadata có chủ đích |
| `@Primary` trên bean bị loại không có hiệu lực | Primary chỉ chọn trong candidates hợp lệ | Kiểm `isPrimary` **và** `isAutowireCandidate` |
| `List<Transport>` thiếu một bean được đăng ký | Collection injection cũng lọc candidate eligibility | Phân biệt injected list với `getBeansOfType` |
| `Map<String,Transport>` khác type discovery map | Điểm autowire có rules riêng | Kiểm `getBeanNamesForType` rồi candidate filtering |
| `getBean("internalTransport")` vẫn thành công | Exclusion không xóa definition và không chặn name lookup | Đây là hành vi đúng |
| Bean bị excluded nhưng vẫn được tiêm `Settings` vào chính nó | Flag giới hạn bean như dependency của người khác | Đây là hành vi đúng; xem hai hướng phụ thuộc |
| `defaultCandidate=false` vẫn inject được qua qualifier | Chỉ loại khỏi default/plain type selection | Đây là hành vi đúng; khác autowireCandidate=false |
| Plain `Transport` chọn implementation lạ | Bean metadata, primary/fallback/defaultCandidate hoặc tên injection point | Xác định actual candidates từng context |
| `NoUniqueBeanDefinitionException` dù vừa exclude một bean | Vẫn còn nhiều eligible candidates | Dùng qualifier/primary hoặc tách interface hợp lý |
| XML pattern loại bean ngoài ý muốn | Bean name không khớp `default-autowire-candidates` | Kiểm patterns và explicit override |
| `@Bean` method không compile khi thêm attribute | Spring version/API khác giả định | Dùng Spring line phù hợp, kiểm actual `Bean` Javadoc |
| `NoSuchBeanDefinitionException` nhưng type discovery có beans | Candidate eligibility, qualifier hoặc scope/hierarchy | Đọc cause chain, không kết luận class không tồn tại |
| Java compile báo thiếu Spring annotation | Dependency/classpath Maven bị lỗi | Kiểm `spring-context` và JDK |
| Bean không được tạo dù vẫn registered | `@Lazy`, prototype, scope hoặc lifecycle khác | Đừng đồng nhất registration với eager instance creation |

### Quy trình chẩn đoán tối thiểu

1. Xác nhận **đúng context**, đúng bean registrations và interface/implementation type compatibility.
2. Dùng `getBeanNamesForType(T.class)` xem **những tên** được phát hiện.
3. Với bean nghi vấn, xem `BeanDefinition.isAutowireCandidate()`, `isDefaultCandidate()`, `isPrimary()`, `isFallback()` khi API line tương ứng hỗ trợ.
4. Kiểm **injection point**: `@Autowired T`, `List<T>`, `Map<String,T>`, qualifier, generic signatures hay explicit name reference?
5. Kiểm các điều kiện khác: Java module/classloader, proxy, scope, primary/fallback, XML patterns và parent contexts nếu có.
6. Đọc **root cause** của `UnsatisfiedDependencyException`, `NoSuchBeanDefinitionException`, `NoUniqueBeanDefinitionException`, thay vì cố match từng câu exception message.
7. Có test integration riêng cho composition root; giữ unit tests nghiệp vụ độc lập Spring bằng fake objects qua constructor khi phù hợp.

## 13. Bài tập tự kiểm chứng và checklist

Thử từng thay đổi **trên bản sao** của project, khôi phục source gốc trước khi so stdout mục 8:

1. **Bỏ exclusion:** sửa `@Bean(name="internalTransport",autowireCandidate=false)` thành bean bình thường. Dự đoán `main.injectedList`, `main.injectedMap` có thêm INTERNAL; vì INTERNAL còn `@Primary`, `main.selected` có thể đổi thành INTERNAL.
2. **Chỉ bỏ primary:** giữ `autowireCandidate=false`, bỏ `@Primary`. Dự đoán Report vẫn chọn ACTIVE, vì internal vốn không tham gia autowiring.
3. **Cố dùng qualifier để lấy excluded bean:** thêm `@Qualifier("internalTransport") Transport t` vào một bean khác. Dự đoán failure khi type-based autowiring không tìm eligible candidate ấy.
4. **Lookup bằng tên:** so `getBean("internalTransport", Transport.class)` hai lần; với singleton mặc định, identity phải trùng.
5. **Type-discovery snapshot:** so `getBeanNamesForType(Transport.class)` với `Report.injectedNames()` và giải thích khác biệt.
6. **Không có eligible bean:** chạy riêng ExcludedOnlyConfig, xác định lỗi tại injection point `needsTransport(Transport candidate)`, không phải do Java không biết Transport type.
7. **Giữ defaultCandidate=false:** thử `@Qualifier("restrictedTransport")` và bỏ qualifier trên bản sao; giải thích điểm khác với hard exclusion.
8. **Đổi restricted thành excluded:** thay `defaultCandidate=false` bằng `autowireCandidate=false` trong RestrictedConfig. Dự đoán qualifier lúc này không đủ để chọn bean.
9. **Biến excluded bean thành prototype:** thêm `@Scope("prototype")`, gọi `getBean("internalTransport")` hai lần; so việc lookup theo tên vẫn hoạt động với instance identity.
10. **Thêm second eligible bean:** đăng ký thêm `backupTransport` mà không primary. Dự đoán một required `Transport selected` có thể trở nên mơ hồ, dù excluded internal vẫn bị loại.
11. **Cấu hình XML:** thử `autowire-candidate="false"` và explicit XML `ref`, quan sát bean vẫn dùng được khi tham chiếu tên.
12. **XML pattern:** dùng `default-autowire-candidates="*Transport"`, quan sát bean names không khớp bị lọc trừ khi explicit override.
13. **In metadata trước khi inject:** viết một công cụ kiểm definition flags và type names trước khi gọi consumer beans.
14. **Dependency của excluded bean:** khai báo constructor method `internalTransport(Settings settings)`, chứng minh bean excluded **vẫn nhận Settings** từ container.
15. **Biên dịch Java thuần:** chạy `ManualCandidateFilter`, thêm một registry entry và điều chỉnh excluded set, rồi xem registered/eligible lists khác nhau.

### Checklist đạt bài

- [ ] Hiểu **registered bean**, **type match**, **autowire candidate** và **chosen bean** là các bước khác nhau.
- [ ] Biết XML `autowire-candidate="false"` và Java `@Bean(autowireCandidate=false)`.
- [ ] Biết bean excluded vẫn có thể tra bằng `getBean(name)`/explicit ref.
- [ ] Biết type-discovery APIs có thể hiển thị bean excluded.
- [ ] Biết injection đơn lẻ **và** injected List/Map đều xem candidate eligibility.
- [ ] Biết `@Primary` hoặc `@Qualifier` không tự phá hard exclusion.
- [ ] Phân biệt **`defaultCandidate=false`** với **`autowireCandidate=false`**.
- [ ] Hiểu excluded bean vẫn có thể **nhận dependencies vào chính nó**.
- [ ] Biết tác động của XML `default-autowire-candidates` patterns và override.
- [ ] Chạy được Spring Maven Java 17 demo ra đúng 19 dòng stdout và Java thuần ra 4 dòng.
- [ ] Debug theo bean definition metadata + injection point thay vì suy đoán từ type names.

## 14. Tổng kết và nguồn chính thức

`autowire-candidate=false` đặt một **ranh giới tham gia dependency injection**: bean vẫn tồn tại trong Spring container nhưng **không trở thành type-based autowire candidate** cho những nơi cần cùng interface/superclass. Các explicit references bằng tên vẫn có thể hoạt động.

`defaultCandidate=false` là mức hạn chế **nhẹ hơn** từ Spring 6.2+: tránh plain type injection nhưng vẫn cho phép qualified injection khi metadata phù hợp. `@Primary`/`@Fallback` chỉ giải quyết **ưu tiên trong tập candidates hợp lệ**, không thay đổi cờ exclusion.

Nguồn tài liệu:
- [Spring Framework — Autowiring Collaborators / Excluding a Bean from Autowiring](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-autowire.html)
- [Spring Framework — Fine-tuning Annotation-based Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html)
- [Spring Framework — Primary and Fallback](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-primary.html)
- [Spring API — `@Bean`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/Bean.html)
- [Spring API — `BeanDefinition`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/config/BeanDefinition.html)
- [Spring Framework — Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java.html)
- [Spring Framework — Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)

**Ghi nhớ:** Nếu Spring “nhìn thấy” một bean theo type nhưng không tự tiêm, hãy kiểm tra **`isAutowireCandidate()` trước khi thêm `@Primary` hoặc `@Qualifier`**. Chọn đúng cấp độ cấu hình giữa hard exclusion, restricted-default candidate và ưu tiên primary giúp DI rõ ràng, dễ bảo trì và không tạo dependency ambiguity ngoài dự kiến.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="candidate-selection-010">Phân biệt ưu tiên lựa chọn với thứ tự khởi tạo</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt ưu tiên lựa chọn với thứ tự khởi tạo — Spring Framework Core Container

## 1. Ba câu hỏi khác nhau thường bị trộn lẫn

Trong Spring IoC Container, khi có nhiều bean thực thi cùng một interface, developer có thể cần **chọn một bean**, **sắp xếp nhiều beans**, hoặc **bảo đảm một bean khởi tạo trước bean khác**. Ba yêu cầu này xảy ra ở những bước khác nhau và không được giải quyết bằng một annotation duy nhất.

Giả sử ứng dụng có hai bean cùng kiểu `Sender`: `emailSender` và `smsSender`. Hãy phân biệt:

1. **Ưu tiên chọn ứng viên:** Nếu constructor cần **một** `Sender`, bean nào được inject? Đây là vấn đề candidate resolution. Công cụ thường dùng là `@Primary`, `@Qualifier`, `@Fallback` hoặc các quy tắc chọn bean khác.
2. **Thứ tự của các ứng viên được tập hợp:** Nếu constructor cần `List<Sender>`, phần tử nào đứng trước trong danh sách? Đây là collection ordering. Công cụ thường dùng là `@Order`, `Ordered` hoặc khi thích hợp `jakarta.annotation.Priority`.
3. **Thứ tự khởi tạo/lifecycle:** Bean nào được Spring **tạo và hoàn tất khởi tạo** trước một bean khác? Đây là vấn đề dependency graph, `@DependsOn`, constructor dependencies, `@Lazy`, scope và lịch bootstrap. `@Primary` hoặc `@Order` không phải câu lệnh điều khiển thứ tự khởi tạo singleton.

Một câu ghi nhớ quan trọng:

> **Được ưu tiên chọn không đồng nghĩa được tạo trước. Đứng đầu collection cũng không đồng nghĩa được tạo đầu tiên.**

Bài học đặt ba ý niệm này cạnh nhau trong **Spring Framework Core Container**, không dùng Spring Boot để tránh thêm lifecycle/scheduler không liên quan.

### Mục tiêu học tập

Sau bài, bạn có thể:

- Giải thích injection một bean khác injection `List<Bean>` và khác trình tự tạo bean.
- Biết `@Primary` chọn một ứng viên mặc định, `@Qualifier` chọn theo ý định consumer và `@Fallback` hạ độ ưu tiên trong Spring 6.2.
- Biết `@Order` tác động tới **thứ tự các phần tử trong collection** và một số điểm ưu tiên khác của framework, nhưng **không điều khiển singleton startup order**.
- Dùng `@DependsOn` khi thực sự cần thứ tự initialization do side effect, và phân biệt nó với **truyền dependency qua constructor**.
- Hiểu singleton eager initialization, `@Lazy`, prototype, circular dependency và giới hạn của việc dựa vào thứ tự khai báo.
- Chạy ví dụ Java 17+ với Spring Framework 6.2.19: một bean **`@Primary` và `@Order(1)` được khởi tạo sau** bean còn lại do `@DependsOn`.
- Viết smoke tests xác nhận **đúng bean**, **đúng collection order** và **đúng quan hệ khởi tạo**, tránh suy luận từ log ngẫu nhiên.
- Phân biệt initialization order, destruction order và thứ tự gọi method nghiệp vụ.

**Phạm vi:** lựa chọn ứng viên so với thứ tự khởi tạo của beans trong IoC container. Bài không yêu cầu Spring Boot, MVC, transaction manager, thread scheduler hay dependency ngoài module Spring Context.

## 2. Bảng phân biệt trước khi viết annotation

| Câu hỏi | Điểm cần quan sát | Công cụ Spring phù hợp | Không nên suy ra |
| --- | --- | --- | --- |
| Chọn **một** Sender | Constructor parameter `Sender` | `@Primary`, `@Qualifier`, `@Fallback`, candidate rules | Chọn trước tức là tạo trước |
| Chọn Sender theo một nghiệp vụ cụ thể | `@Qualifier("emailSender") Sender` | Qualifier metadata và type resolution | Qualifier quyết định init order |
| Lấy và sắp **nhiều** Sender | `List<Sender>` / array | `@Order`, `Ordered`, ordering comparator | `@Order(1)` tự thành primary |
| Buộc bean B chỉ tạo sau bean A | Bean definitions và dependency graph | Constructor/factory parameter, `@DependsOn("beanA")` | `@Primary` hoặc `@Order` giải quyết quan hệ này |
| Trì hoãn tạo một singleton | Bean definition | `@Lazy` | Lazy đồng nghĩa phụ thuộc luôn optional |
| Tạo lại object theo mỗi lần yêu cầu | Bean scope | `@Scope("prototype")` | Prototype được eagerly pre-instantiate như singleton |
| Điều khiển thứ tự **shutdown** với quan hệ depends-on | Lifecycle singleton | `@DependsOn` + destruction callbacks | Mọi bean đều có cùng thứ tự hủy |
| Điều khiển thứ tự gọi nghiệp vụ | Logic application | Orchestrator/explicit method calls | Spring tự chạy method theo `@Order` của bean |

Nếu một team dùng chữ “priority” mà không nói **priority cho hành động gì**, hãy hỏi lại: “Ưu tiên lựa chọn dependency đơn trị, ưu tiên trong danh sách hay ưu tiên khởi động?” Nhiều bug xuất phát từ việc gọi cả ba là “thứ tự bean”.

## 3. Chọn một ứng viên với @Primary, @Qualifier, @Fallback

Giả sử context có hai bean cùng interface `Sender`. Một điểm tiêm `Sender` đơn trị cần chọn một trong các ứng viên hợp lệ: `@Primary` có thể xác định lựa chọn mặc định; `@Qualifier("emailSender")` yêu cầu vai trò cụ thể; `@Fallback` từ Spring 6.2 hạ ưu tiên một bean dự phòng. Các cơ chế này can thiệp vào **lựa chọn dependency**, không ra lệnh cho Spring gọi factory method nào trước.

Nếu một ứng viên bị loại bằng `autowireCandidate=false`, các annotation ưu tiên không tự đưa nó trở lại tập được autowire. Nếu consumer yêu cầu `List<Sender>`, những bean đủ điều kiện có thể cùng xuất hiện dù bean nào là primary/fallback. Bài này dùng SMS được chọn làm default để đối chiếu với thứ tự collection và khởi tạo khác hẳn trong demo mục 8.

## 4. @Order: thứ tự trả về cho collection injection

Nếu consumer cần **tất cả** senders, nó có thể inject collection:

~~~java
public NotificationPipeline(List<Sender> senders) {
    this.senders = List.copyOf(senders);
}
~~~

Spring sắp collection theo metadata phù hợp như `@Order`, `Ordered`, hoặc `@Priority` khi annotation chuẩn có mặt và được hỗ trợ đúng vị trí.

~~~java
@Bean("emailSender")
@Order(20)
Sender emailSender() {
    return new NamedSender("EMAIL");
}

@Bean("smsSender")
@Order(1)
Sender smsSender() {
    return new NamedSender("SMS");
}
~~~

Nếu inject `List<Sender>` từ hai beans này, kết quả dự kiến là:

~~~text
[SMS, EMAIL]
~~~

**Giá trị nhỏ hơn có ưu tiên đứng trước trong collection** (ví dụ 1 đứng trước 20). Điều đó là **sorting policy**, không phải quy tắc gọi hai factory methods theo thứ tự 1 rồi 20.

### Không dùng @Order để thay thế @Primary

Một consumer cần đơn trị:

~~~java
public NotificationJob(Sender sender) {
    this.sender = sender;
}
~~~

Giả sử hai bean cùng type có `@Order(1)` và `@Order(20)`, **không nên xem @Order là cách giải quyết đáng tin cậy để chọn một sender duy nhất**. Hãy khai báo `@Primary` cho default thực sự hoặc `@Qualifier` ở injection point.

JavaDoc Spring ghi rõ order values có thể ảnh hưởng tới mức ưu tiên ở **một số injection points**, nhưng **chúng không ảnh hưởng singleton startup order**. Cách áp dụng an toàn là xem `@Order` chủ yếu như **collection ordering**, không viết logic nghiệp vụ giả định nó sẽ thay thế mọi rule candidate resolution.

### @Order trên @Configuration class có ý nghĩa khác

Có thể gắn `@Order` cho `@Configuration` class, điều này ảnh hưởng **thứ tự đánh giá** các configuration classes trong tập đang xử lý. Nó **không tự áp dụng mức order đó cho các `@Bean` methods** bên trong.

Nếu muốn List<Sender> có thứ tự, gắn order trên từng target component hoặc `@Bean` method phù hợp. Đừng dựa vào thứ tự các configuration files hoặc classpath scanning để làm thứ tự collection có ý nghĩa nghiệp vụ.

### @Order không mặc định quyết định Map iteration

Spring có thể inject `Map<String, Sender>`, trong đó key là bean name. Nếu muốn hiển thị danh sách tên bean theo thứ tự xác định, hãy sort keys hoặc dùng một chính sách rõ. Đừng suy rằng `@Order(1)` luôn quyết định mọi kiểu Map traversal hoặc container iteration.

## 5. Thứ tự khởi tạo phụ thuộc bean graph, không phải ưu tiên chọn

Khi Spring tạo một bean, trước tiên những **dependency cần thiết của nó** phải được resolve và khởi tạo phù hợp. Một constructor:

~~~java
public EmailJob(Sender sender) {
    this.sender = Objects.requireNonNull(sender);
}
~~~

thể hiện quan hệ **EmailJob phụ thuộc Sender**. Trong cơ chế bean initialization bình thường không có vòng phụ thuộc, Spring cần có Sender phù hợp rồi mới có thể hoàn thành EmailJob.

Sơ đồ dependency theo chiều consumer → dependency:

~~~mermaid
flowchart LR
    Job["emailJob"] --> Sender["emailSender"]
    Sms["smsSender"] -.->|"phụ thuộc initialization"| Email["emailSender"]
    Note["Chú ý: quan hệ phụ thuộc"] --> Meaning["không phải @Primary/@Order"]
~~~

Mũi tên từ `smsSender` tới `emailSender` trong ví dụ phản ánh trường hợp ta **khai báo dependency khởi tạo**, chẳng hạn `@DependsOn("emailSender")`. Nó không phát sinh từ việc smsSender là primary hay có order number nhỏ.

### Khi không có quan hệ phụ thuộc

Hai singleton độc lập có thể được Spring tạo trong giai đoạn context startup, nhưng **không nên coi thứ tự source code của `@Bean` methods hay tên class là hợp đồng**. Một số cấu hình bình thường tạo non-lazy singletons theo thứ tự đăng ký definitions, nhưng điều đó không thay cho quan hệ phụ thuộc tường minh và có thể bị ảnh hưởng bởi graph, lazy beans, conditions, post-processors và những lựa chọn bootstrap khác.

**Nguyên tắc:** nếu đúng chức năng hệ thống cần bean A khởi tạo trước bean B, hãy biểu đạt lý do đó qua dependency thật hoặc `@DependsOn`, không dùng một đặc tính quan sát được trong lần chạy hiện tại.

## 6. @DependsOn: yêu cầu bean khác được khởi tạo trước

Khi bean B cần một **side effect khởi tạo** của bean A, nhưng không trực tiếp cần reference A trong constructor/property, bạn có thể khai báo:

~~~java
@Bean("emailSender")
Sender emailSender() {
    return new NamedSender("EMAIL");
}

@Bean("smsSender")
@DependsOn("emailSender")
Sender smsSender() {
    return new NamedSender("SMS");
}
~~~

Đây là **snippet** thuộc configuration class. Khi Spring tạo `smsSender`, nó bảo đảm bean `emailSender` được khởi tạo **trước đó**, dù `smsSender` không cần giữ field reference tới `emailSender`.

**Điều @DependsOn làm:**

- Ghi metadata initialization dependency theo **bean name**.
- Buộc Spring tạo/cấu hình bean được chỉ định trước bean hiện tại, trong điều kiện lifecycle liên quan.
- Với singleton destruction, `depends-on` còn tạo quan hệ hủy ngược: bean phụ thuộc bị hủy trước bean mà nó phụ thuộc.
- Hữu ích cho yêu cầu side-effect initialization không được thể hiện qua constructor/property injection; ví dụ một registration prerequisite được cung cấp bởi infrastructure bean.

**Điều @DependsOn không làm:**

- Không tự inject object A vào field của B.
- Không biến A thành primary hay cho A đứng đầu `List<T>`.
- Không thay `@Qualifier` khi cần chọn giữa nhiều beans cùng type.
- Không bảo đảm B không bao giờ bị gọi trước A trong mọi luồng nghiệp vụ; nó chỉ ràng buộc **bean lifecycle**.
- Không sửa được vòng phụ thuộc. Nếu A depends on B và B depends on A thì graph không thể được khởi tạo bình thường.
- Không có nghĩa A đã hoàn tất **mọi tác vụ asynchronous** hay external system side effects; cần contract readiness riêng nếu có công việc nền.

### Ưu tiên một dependency thực sự khi có thể

Nếu B thực sự **dùng API của A**, nên dùng constructor injection:

~~~java
public StartupCoordinator(ReadyState readyState) {
    this.readyState = Objects.requireNonNull(readyState);
}
~~~

Đây là API Java rõ ràng, cho phép test thuần Java. Dùng `@DependsOn` khi cần **khởi tạo trước nhưng không có dependency reference thích hợp**, không thay constructor injection bằng một string-based ordering khó bảo trì.

**Cẩn thận khi rename:** `@DependsOn("emailSender")` chứa tên bean dưới dạng String. Đổi bean name mà không cập nhật annotation có thể khiến context startup lỗi. Nên kiểm tra wiring/registration ở integration tests.

## 7. @Lazy, singleton và prototype: khi nào việc tạo thực sự xảy ra?

Theo mặc định, nhiều `ApplicationContext` implementations **eagerly pre-instantiate singleton beans** khi refresh context. Nhưng có các ngoại lệ quan trọng.

| Bean/cấu hình | Thời điểm tạo thường thấy | Điều không thể kết luận |
| --- | --- | --- |
| Singleton không lazy | Trong khởi động/`refresh()` của context | Sẽ tạo theo @Primary/@Order |
| `@Lazy` singleton không bị eager bean khác cần ngay | Khi được yêu cầu lần đầu | Không bao giờ tạo hoặc không thể lỗi |
| `@Lazy` singleton bị non-lazy singleton khác yêu cầu trực tiếp | Có thể vẫn tạo trong startup để đáp ứng dependency | @Lazy luôn trì hoãn tuyệt đối |
| Prototype | Khi Spring xử lý yêu cầu tạo bean tương ứng | Được eagerly instantiate như mọi singleton |
| Prototype injected trực tiếp vào singleton | Thường được resolve một lần lúc singleton tạo | Mỗi lần gọi method trên singleton sẽ có prototype mới |
| `ObjectProvider<T>` | Bean có thể được tra cứu on-demand bằng provider | Tự bảo đảm thứ tự nghiệp vụ |
| `@DependsOn` | Buộc prerequisite khởi tạo trước bean dependent | Chọn bean theo primary/order |
| Background initialization của Spring 6.2 (khi bật) | Có thể song song/lệch thứ tự so với synchronous startup | Nên đoán thứ tự từ log constructor |

Khi code không đăng ký bean lazy làm dependency bắt buộc của eager singleton, bạn có thể kiểm tra nó **chưa** được tạo sau `refresh()` và **đã** được tạo sau `getBean(...)`.

### @Lazy đặt ở đâu?

`@Lazy` trên bean definition thông báo chính sách lazy initialization; nó khác với `@Lazy` ở injection point, có thể yêu cầu lazy-resolution proxy tùy cấu hình. Nếu eager consumer cần một bean lazy **trực tiếp** khi xây dựng constructor, bean đích có thể vẫn được tạo sớm.

Không dùng `@Lazy` để che một vòng phụ thuộc hoặc một dependency thiếu bắt buộc. Nó có thể **chỉ dời thời điểm lỗi**, khiến runtime failure đến muộn và khó phân tích hơn.

### Scope không phải priority

Một bean `@Primary` có thể singleton, prototype hoặc scope khác. `@Primary` quyết định candidate khi cần single injection; **scope quyết định cách cấp instance**. Chọn đúng implementation chưa bảo đảm cùng object identity qua mỗi lời gọi nếu scope không phải singleton.

## 8. Demo Java 17+ hoàn chỉnh: chọn SMS trước nhưng tạo EMAIL trước

Chương trình cho thấy **cùng lúc**:

- `smsSender` có **`@Primary`** → dependency đơn trị trần nhận SMS.
- `smsSender` có **`@Order(1)`**, `emailSender` có `@Order(20)` → `List<Sender>` theo thứ tự **[SMS, EMAIL]**.
- `smsSender` có **`@DependsOn("emailSender")`** → dù SMS đứng đầu collection và là primary, bean EMAIL phải khởi tạo trước SMS.
- `@Qualifier("emailSender")` ở một factory method → consumer chuyên biệt vẫn nhận EMAIL.
- Bean `lazyReporter` gắn `@Lazy` và không được eager bean nào yêu cầu → sau startup chưa là instantiated singleton; sau lookup thì được khởi tạo.

Đây là **ba chiều độc lập**: *bean selection*, *list ordering*, và *creation timing*.

Yêu cầu: **JDK 17+**, Maven và Spring Framework **6.2.19**. Phiên bản được ghim để ví dụ tái lập; không có nghĩa đó là bản mới nhất. Không cần Spring Boot, HTTP server hay database.

### 8.1. Cấu trúc

~~~text
bean-order-vs-initialization/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── BeanOrderVsInitDemo.java
~~~

### 8.2. File pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>bean-order-vs-initialization</artifactId>
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
                    <mainClass>demo.BeanOrderVsInitDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Maven giải quyết các module/transitive JARs của Spring Context. Với `javac`/`java` trực tiếp, cần thêm cả compile/runtime dependencies chứ không chỉ đặt một JAR `spring-context` lên classpath.

### 8.3. File src/main/java/demo/BeanOrderVsInitDemo.java

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;

public final class BeanOrderVsInitDemo {
    private BeanOrderVsInitDemo() {}

    public interface Sender {
        String id();
    }

    public record NamedSender(String id) implements Sender {
        public NamedSender {
            Objects.requireNonNull(id);
        }
    }

    public static final class Events {
        private final List<String> events = new ArrayList<>();

        public void record(String name) {
            events.add(name);
        }

        public List<String> snapshot() {
            return List.copyOf(events);
        }

        public boolean before(String earlier, String later) {
            int a = events.indexOf(earlier);
            int b = events.indexOf(later);
            return a >= 0 && b > a;
        }
    }

    public static final class Dashboard {
        private final Sender selected;
        private final Sender specificEmail;
        private final List<Sender> ordered;

        public Dashboard(Sender selected, Sender specificEmail,
                         List<Sender> ordered) {
            this.selected = Objects.requireNonNull(selected);
            this.specificEmail = Objects.requireNonNull(specificEmail);
            this.ordered = List.copyOf(ordered);
        }

        public Sender selectedBean() {
            return selected;
        }

        public String selected() {
            return selected.id();
        }

        public String qualified() {
            return specificEmail.id();
        }

        public String collectionOrder() {
            return ordered.stream().map(Sender::id)
                    .toList().toString();
        }
    }

    public record LazyReporter(String status) {}

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        public Events events() {
            return new Events();
        }

        // Khai báo SMS trước trong mã để tránh dựa vào source order.
        // @DependsOn mới là ràng buộc yêu cầu EMAIL được tạo trước.
        @Bean("smsSender")
        @Primary
        @Order(1)
        @DependsOn("emailSender")
        public Sender smsSender(Events events) {
            events.record("sms");
            return new NamedSender("SMS");
        }

        @Bean("emailSender")
        @Order(20)
        public Sender emailSender(Events events) {
            events.record("email");
            return new NamedSender("EMAIL");
        }

        @Bean
        public Dashboard dashboard(
                Sender selected,
                @Qualifier("emailSender") Sender specificEmail,
                List<Sender> senders) {
            return new Dashboard(selected, specificEmail, senders);
        }

        @Bean("lazyReporter")
        @Lazy
        public LazyReporter lazyReporter(Events events) {
            events.record("lazy");
            return new LazyReporter("READY");
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            Dashboard dashboard = context.getBean(Dashboard.class);
            Events events = context.getBean(Events.class);
            Sender primary = context.getBean(Sender.class);

            System.out.println("selected=" + dashboard.selected());
            System.out.println("qualified=" + dashboard.qualified());
            System.out.println("collection=" +
                    dashboard.collectionOrder());
            System.out.println("created=" + events.snapshot());
            System.out.println("selectedIsPrimary=" +
                    (dashboard.selectedBean() == primary));
            System.out.println("emailBeforeSms=" +
                    events.before("email", "sms"));
            System.out.println("orderVsCreationDifferent=" +
                    !dashboard.collectionOrder().equals(
                            events.snapshot().stream()
                                    .map(String::toUpperCase)
                                    .toList().toString()));

            System.out.println("lazyBefore=" +
                    context.getBeanFactory()
                            .containsSingleton("lazyReporter"));
            System.out.println("lazyValue=" +
                    context.getBean(LazyReporter.class).status());
            System.out.println("lazyAfter=" +
                    context.getBeanFactory()
                            .containsSingleton("lazyReporter"));
            System.out.println("createdAfterLazy=" + events.snapshot());
        }
    }
}
~~~

### 8.4. Phân tích từng đặc điểm của code

**Bean selection:** `dashboard(Sender selected, ...)` có một tham số single-valued `Sender` không qualifier, trong context có EMAIL và SMS. `smsSender` có `@Primary` nên selected là SMS. `context.getBean(Sender.class)` cũng nhận SMS. Identity check cho thấy cùng singleton trong context này.

**Qualifier-specific dependency:** `@Qualifier("emailSender") Sender specificEmail` được chọn là EMAIL, dù SMS là primary. Qualifier chọn bean theo ngữ nghĩa injection point, không buộc EMAIL khởi tạo trước SMS; ràng buộc creation được khai báo ở chỗ khác.

**Collection ordering:** `@Order(1)` ở SMS, `@Order(20)` ở EMAIL; `List<Sender>` được sắp [SMS, EMAIL]. Không sử dụng source order hoặc random để sắp danh sách.

**Creation ordering:** `@DependsOn("emailSender")` trên SMS yêu cầu EMAIL initialization xong trước khi SMS factory được chạy. Hai factory methods ghi một event vào `Events`, nên trace là [email, sms]. Cả hai phụ thuộc cùng singleton `Events` nhưng Events không ghi sự kiện của chính nó.

**Cố ý ngược nhau:** SMS là **primary và first in List**, nhưng EMAIL được tạo **trước**. Do đó demo phản ví dụ trực tiếp cho ý tưởng “ưu tiên được chọn là khởi tạo sớm”.

**Lazy:** `LazyReporter` có `@Lazy` trên factory method và không có bean eager khác yêu cầu nó. `containsSingleton("lazyReporter")` ban đầu false, sau `getBean(LazyReporter.class)` thì true. Event thứ ba `lazy` chứng tỏ thời điểm tạo khác hai Sender eager.

**Ổn định:** demo dùng các labels String cố định, không dùng thời gian, random hoặc output từ các thread. List/Event trace cùng nằm trong một context, được ghi đồng bộ và chỉ để minh họa; `ArrayList` không được quảng cáo là thread-safe trong ứng dụng đa luồng.

**Cấu trúc Java:** chỉ có một public top-level class `BeanOrderVsInitDemo`, các interface/records/config là nested types; package `demo` và file path tương ứng. Java 17 hỗ trợ record, `List.copyOf` và `Stream.toList()`.

**`@Configuration(proxyBeanMethods=false)`:** các factory methods **không gọi trực tiếp lẫn nhau**. Spring resolve dependency qua tham số `@Bean`/`@DependsOn`, nên không phụ thuộc interception của lời gọi method JavaConfig.

**Lifecycle:** context đóng khi ra khỏi `try-with-resources`. Demo không đăng ký destroy callbacks in stdout; các dòng dự kiến chỉ đến từ `System.out.println` ở `main`. Với một singleton `@DependsOn` có destroy callback, Spring cũng có quan hệ hủy theo chiều ngược, nhưng demo này không dùng log hủy để tránh nhầm với thứ tự khởi tạo.

### 8.5. Chạy bằng Maven

Từ thư mục `bean-order-vs-initialization`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

**Đúng 11 dòng stdout do chương trình in khi chạy thành công:**

~~~text
selected=SMS
qualified=EMAIL
collection=[SMS, EMAIL]
created=[email, sms]
selectedIsPrimary=true
emailBeforeSms=true
orderVsCreationDifferent=true
lazyBefore=false
lazyValue=READY
lazyAfter=true
createdAfterLazy=[email, sms, lazy]
~~~

Các dòng `java -version`, `mvn -version`, Maven downloading/logs hoặc framework logging (nếu có) không tính vào **11 dòng `System.out.println` do `main` in**.

**Đọc kết quả mà không suy quá mức:**

| Dòng | Chứng minh | Không chứng minh |
| --- | --- | --- |
| `selected=SMS` | @Primary chọn SMS cho bare type dependency | SMS được tạo sớm nhất |
| `qualified=EMAIL` | Qualifier chọn EMAIL ngay cả khi SMS primary | Qualifier điều khiển startup |
| `collection=[SMS, EMAIL]` | @Order sắp List<Sender> | Factory SMS chạy trước factory EMAIL |
| `created=[email, sms]` | Thứ tự tạo 2 Sender objects quan sát được | Mọi bean khác cũng theo thứ tự này |
| `selectedIsPrimary=true` | Dashboard giữ đúng bean singleton được chọn | Singleton toàn JVM hay mọi scope |
| `emailBeforeSms=true` | Ràng buộc @DependsOn hoạt động | Một business method EMAIL luôn chạy trước SMS |
| `orderVsCreationDifferent=true` | Collection order và creation order khác nhau | Có một lịch bootstrap chung cho mọi ứng dụng |
| `lazyBefore=false` | lazyReporter chưa instantiated sau startup | Lazy luôn trì hoãn khi có eager consumer cần nó |
| `lazyValue=READY` | Lookup tạo được LazyReporter | Lazy bean không có lifecycle |
| `lazyAfter=true` | Singleton này đã được tạo sau lookup | Mọi prototype có chứa trong singleton registry |
| `createdAfterLazy=[email, sms, lazy]` | Trace có event khởi tạo lazy sau | Mọi background job đều đã xong |

### 8.6. Chạy bằng javac/java trên hai hệ điều hành

Nếu chạy trực tiếp, phải tạo compile/runtime classpath có **đầy đủ** các transitive dependencies từ Maven:

~~~bash
mvn -q dependency:build-classpath -Dmdep.outputFile=classpath.txt
~~~

Linux/macOS (bash/zsh):

~~~bash
mkdir -p out
javac --release 17 -encoding UTF-8 \
  -cp "$(cat classpath.txt)" \
  -d out src/main/java/demo/BeanOrderVsInitDemo.java
java -cp "out:$(cat classpath.txt)" demo.BeanOrderVsInitDemo
~~~

Windows PowerShell:

~~~powershell
$deps = (Get-Content -Raw classpath.txt).Trim()
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -encoding UTF-8 -cp "$deps" -d out src/main/java/demo/BeanOrderVsInitDemo.java
java -cp "out;$deps" demo.BeanOrderVsInitDemo
~~~

Trên Unix, classpath separator là `:`, Windows là `;`. Hai cách chạy đều gọi cùng `main`. Nếu `javac` không thấy package `org.springframework...`, kiểm tra Maven POM/classpath trước khi chẩn đoán candidate resolution hoặc lifecycle.

## 9. Tại sao không nên dựa vào thứ tự @Bean trong source?

Một vài lần chạy có thể cho thấy bean khai báo trước trong `@Configuration` được tạo trước. Đó là một **quan sát cụ thể**, không phải contract đủ vững để điều phối nghiệp vụ.

Ví dụ:

~~~java
@Bean
public Warmup warmup() { ... }

@Bean
public Worker worker() { ... }
~~~

Đây là snippet minh họa, thiếu classes/implementation. Nếu `Worker` phụ thuộc vào side effect của `Warmup` nhưng không khai báo quan hệ, việc chỉ đặt method `warmup()` lên phía trên là **mong manh**. Hãy khai báo:

~~~java
@Bean
@DependsOn("warmup")
public Worker worker() { ... }
~~~

Hoặc tốt hơn, nếu `Worker` dùng chính object `Warmup`, đưa nó vào constructor/factory parameter để Spring thấy dependency thật.

Thứ tự đăng ký có thể bị ảnh hưởng bởi các configuration classes, conditional beans, component scanning, bean factory post-processors hoặc những extension points khác. Vì vậy **không ràng buộc nghiệp vụ với thứ tự reflection trả về methods**.

### @Order trên @Configuration khác order trên @Bean

`@Order` ở configuration-class level tác động tới thứ tự đánh giá các configuration classes, không tự “truyền” order xuống các bean methods. Để làm List injection có thứ tự, đặt order trên **bean definition target** hoặc implementation type. Để bắt initialization A trước B, khai báo **dependency B → A**.

## 10. Initialization và execution là hai thứ tự khác nhau

Ngay cả khi `@DependsOn("cacheLoader")` bảo đảm CacheLoader bean được **khởi tạo** trước Job bean, Spring không tự chạy `Job.execute()` sau `cacheLoader.load()` trừ khi có logic/lifecycle contract thực sự gọi chúng.

Phân biệt:

- **Construction order:** lúc constructor/factory method tạo object.
- **Dependency resolution order:** lúc Spring tạo/lấy những collaborator cần cho object.
- **Initialization callbacks:** `@PostConstruct`, `InitializingBean.afterPropertiesSet`, custom init methods, sau khi injection và trước bean sẵn sàng theo lifecycle thông thường.
- **Runtime method execution order:** controller, scheduler, event handler hoặc application orchestrator gọi method theo logic riêng.
- **Destruction order:** lúc context close/shutdown, có thể chịu ràng buộc đảo chiều của depends-on với singleton.

**Ví dụ nghiệp vụ:** nếu phải kiểm tra tồn kho trước khi charge, hãy viết một use case gọi stock rồi charge với xử lý lỗi/transaction phù hợp; đừng gắn `@Order(1)` cho `StockGateway` rồi mong runtime tự gọi nó trước `PaymentGateway`.

Tương tự, `@Primary` chỉ nói khi chọn một gateway thì lấy gateway nào; nó không nói **khi nào charge được phép thực hiện**. Chọn bean và lập lịch thực thi là hai trách nhiệm khác nhau.

## 11. Thứ tự hủy: @DependsOn có hiệu lực ngược với singletons

Với hai singleton beans:

~~~java
@Bean("connectionManager")
ConnectionManager manager() { ... }

@Bean("consumer")
@DependsOn("connectionManager")
Consumer consumer() { ... }
~~~

**Snippet minh họa** thiếu types. Khi khởi tạo, manager phải hoàn thành trước consumer. Khi container hủy singleton beans theo dependency relationship, Spring đảm bảo **consumer được hủy trước connectionManager**, để consumer có thể cleanup khi prerequisite còn tồn tại.

Điều này hữu ích với resource ownership, nhưng không thay cho proper `close()`/destroy callback khi bean thực sự có tài nguyên cần giải phóng.

Không suy rằng @Order hoặc @Primary áp dụng cho destruction order. Cũng không khẳng định prototype instances có full automatic destroy callbacks chỉ vì được chọn và tạo qua Spring; Spring không quản lý destruction của prototypes giống singletons mặc định.

## 12. Những nhầm lẫn cần sửa ngay

| Hiểu nhầm | Vì sao sai | Cách tiếp cận đúng |
| --- | --- | --- |
| “`@Primary` nghĩa là bean được khởi tạo đầu tiên” | Primary dùng chọn candidate đơn trị | Dùng dependency graph/`@DependsOn` nếu cần thứ tự khởi tạo |
| “`@Order(1)` nghĩa là factory method gọi trước” | Order sắp collection/priority ở các injection points liên quan | Đừng dùng @Order điều khiển singleton startup |
| “`@DependsOn` nghĩa là phụ thuộc sẽ được inject vào field” | Depends-on chỉ nói điều kiện khởi tạo | Inject object qua constructor/factory argument |
| “Bean đầu tiên trong List luôn là primary” | List ordering khác single-bean selection | Kiểm tra @Order và @Primary riêng |
| “Bean được chọn theo @Qualifier sẽ tạo trước bean khác” | Qualifier chỉ chọn ứng viên | Khởi tạo theo graph/scope/lazy |
| “@Fallback hoãn tạo bean cho tới lúc cần” | Fallback là chính sách ưu tiên lựa chọn | Dùng @Lazy khi muốn lazy singleton |
| “Dán @Lazy là không thể khởi tạo lúc startup” | Eager consumer có thể yêu cầu lazy bean | Xác định trực tiếp dependency và proxy/injection semantics |
| “Prototype giống eagerly created singleton” | Prototype được tạo khi cần | Xác định scope và request điểm lấy bean |
| “Đổi thứ tự các @Bean methods để fix bug” | Source order không phải lifecycle contract | Biểu đạt dependency tường minh |
| “@DependsOn tự chờ external service thật sự healthy” | Bean init không đồng nghĩa remote readiness | Có health/readiness contract riêng |
| “@Order trên @Configuration sắp mọi bean trong class” | Config processing order khác bean-level collection order | Đánh dấu @Order trên bean target |
| “Ứng dụng khởi động rồi thì mọi bean đã được tạo” | Có lazy/prototype/background modes | Test time-of-creation theo scope |
| “@Order quyết định thứ tự gọi các strategies” | Nó sắp List khi inject, method calls vẫn do code quyết định | Viết orchestrator loop theo collection khi cần |
| “@DependsOn giải quyết constructor cycle” | Graph tuần hoàn không có thứ tự init hợp lệ | Refactor cycle, bỏ hidden mutual dependencies |

### Vấn đề thiếu/thừa bean là vấn đề khác

Nếu injection một `Sender` thất bại vì có hai beans và không có selection policy, thêm `@DependsOn` **không giải quyết ambiguity**. Nếu `@DependsOn("emailSender")` báo không tìm thấy bean, thêm `@Primary` cũng không tạo được bean bị thiếu. Mỗi annotation sửa một loại quan hệ khác nhau; chẩn đoán đúng vấn đề trước khi chỉnh.

## 13. Debug có hệ thống khi thứ tự không như kỳ vọng

**Bước 1 — Làm rõ thứ tự cần kiểm tra.** Bạn đang nhìn order của `List<T>`, order của những factory events, lifecycle callbacks, destruction, hay business logs?

**Bước 2 — Chọn đúng bằng chứng.** Với candidate selection, kiểm tra bean identity của một injection point. Với List, kiểm tra thứ tự `Sender::id` trong List. Với initialization, thu events từ constructors/initializers vào một collector test đơn luồng hoặc kiểm tra dependency graph tường minh.

**Bước 3 — Xem bean definitions.** Tên bean nào được đăng ký, declared types nào, primary/fallback/qualifier/order metadata ra sao? Có `@Lazy`, scope hoặc `@DependsOn` không?

**Bước 4 — Kiểm tra graph trước timestamps.** Nếu B có constructor A, hoặc `@DependsOn("A")`, Spring phải xử lý A trước B theo contract thông thường. Không có edge thì một log tình cờ chưa tạo bảo đảm cho lần chạy sau.

**Bước 5 — Phân biệt eager và lazy.** Một singleton non-lazy thường tạo lúc refresh; một bean lazy có thể chưa tạo tới khi getBean. Nếu bean lazy là dependency của eager bean, kiểm tra liệu nó có bị buộc tạo sớm không.

**Bước 6 — Xem exception chain.** `NoUniqueBeanDefinitionException` chỉ việc chọn candidate; `NoSuchBeanDefinitionException` có thể chỉ thiếu prerequisite/bean; `BeanCurrentlyInCreationException` liên quan vòng dependency; `BeanCreationException` có thể chứa lỗi initializer.

**Bước 7 — Sửa đúng chiều quan hệ.** Chọn một bean → `@Primary`/`@Qualifier`; sắp nhiều beans → `@Order`; cần prerequisite init → dependency/`@DependsOn`; muốn trì hoãn → `@Lazy`; muốn thứ tự nghiệp vụ → code orchestration.

**Bước 8 — Viết regression test.** Đừng chỉ nhìn console log khi mọi thứ “có vẻ đúng”; kiểm tra một invariant cụ thể trong automated test.

### Ma trận chọn công cụ

| Nhu cầu | Ưu tiên dùng | Kiểm thử |
| --- | --- | --- |
| Luôn inject SMS mặc định | `@Primary` trên smsSender | `getBean(Sender.class)` hoặc service.selected.id == SMS |
| Chỉ client EMAIL dùng emailSender | `@Qualifier("emailSender")` | Consumer giữ đúng instance theo tên |
| Nhiều senders có thứ tự | `List<Sender>` và `@Order` trên beans | IDs == [SMS, EMAIL] |
| Email initialization phải xong trước SMS | `@DependsOn("emailSender")` hoặc dependency thật | Event email xuất hiện trước sms |
| Trì hoãn reporter cho tới lần dùng | `@Lazy` trên bean không bị eager consumer yêu cầu | Chưa singleton trước lookup, có sau |
| Consumer cần A trước khi thực hiện logic B | Explicit orchestration/method calls | Assert call order và failure paths |
| Shutdown consumer trước prerequisite | Singleton depends-on relationship | Destroy callback event order trong test riêng |

## 14. Test những khẳng định độc lập

### Unit test Java thuần cho logic chọn strategy

Có thể tạo fake/implementation với constructor injection thường:

~~~java
Sender fake = () -> "FAKE";
Dashboard dashboard = new Dashboard(
        fake, fake, List.of(fake));
System.out.println(dashboard.selected()); // FAKE
~~~

**Snippet** cần nằm trong phạm vi có thể truy cập các nested types của demo; không phải file Java độc lập. Unit test này chỉ chứng minh Java logic của Dashboard, không kiểm tra Spring chọn bean.

### Spring wiring test

Tạo context với `AppConfig.class`, rồi kiểm tra:

- `getBean(Sender.class)` nhận đúng SMS có `@Primary`.
- Bean EMAIL vẫn lấy được theo name và được inject vào `specificEmail`.
- `Dashboard.collectionOrder()` trả `[SMS, EMAIL]` theo `@Order`.
- Events sau refresh chứa `email` trước `sms` vì `@DependsOn`.
- LazyReporter chưa được tạo khi context mới refresh và chưa có eager consumer nào yêu cầu.
- Sau `getBean(LazyReporter.class)`, Events thêm `lazy`, bean được tạo.

Có thể dùng test framework assertions; không dựa vào Java `assert` khi không bật `-ea`. Trong test nghiêm túc, hãy tạo collector trong một context mới để không lẫn events các lần chạy.

### Negative tests hữu ích

**Test 1 — bỏ @Primary:** với hai Sender beans và single bare `Sender` injection, có thể phát sinh ambiguity (trừ khi có lựa chọn khác như qualifier/name matching); thiết kế test để không có name-based shortcut làm sai kết luận.

**Test 2 — bỏ @DependsOn:** vẫn có thể quan sát EMAIL trước SMS trong một lần chạy. Nhưng **không còn ràng buộc bắt buộc**; test không được kết luận từ một trace tình cờ rằng Spring cam kết ordering.

**Test 3 — thay @Order:** đổi `@Order(1)` thành giá trị lớn hơn EMAIL sẽ đổi list ordering, **không thay contract @DependsOn**.

**Test 4 — lazy bị eager consumer yêu cầu:** thêm singleton non-lazy cần LazyReporter trong constructor; có thể khiến LazyReporter tạo trong context refresh mặc dù @Lazy trên bean definition.

**Test 5 — constructor cycle:** bean A bắt buộc B, B bắt buộc A; Spring không giải quyết vòng constructor dependency đơn giản. Cần sửa graph.

**Test 6 — context shutdown:** thêm destroy callbacks ở cả hai singleton có quan hệ @DependsOn để test thứ tự hủy ngược. Đừng lẫn output đó vào expected stdout của demo chính.

## 15. Bài tập có đáp án

**Bài 1.** SMS có `@Primary`, EMAIL không có. Bare `Sender` injection nhận gì?

**Đáp án:** SMS nếu không có ràng buộc khác loại nó khỏi candidate set; primary quyết định lựa chọn đơn trị, không quyết định lúc tạo.

**Bài 2.** SMS `@Order(1)`, EMAIL `@Order(20)`. `List<Sender>` nhận thứ tự nào trong demo?

**Đáp án:** [SMS, EMAIL]; order nhỏ đứng trước khi Spring sắp collection injection.

**Bài 3.** SMS có `@Order(1)` và `@Primary`. Có bảo đảm SMS được tạo trước EMAIL không?

**Đáp án:** Không. Demo còn có @DependsOn(emailSender), nên SMS bắt buộc tạo sau EMAIL.

**Bài 4.** `@DependsOn("emailSender")` trên SMS có làm SMS không còn primary không?

**Đáp án:** Không. @DependsOn chi phối initialization dependency; @Primary vẫn là selection metadata.

**Bài 5.** EMAIL có `@Order(20)`. Sao trong trace vẫn được tạo trước SMS?

**Đáp án:** Vì @DependsOn ràng buộc EMAIL phải được khởi tạo trước SMS; @Order không quy định thứ tự factory invocations.

**Bài 6.** Consumer có `@Qualifier("emailSender")` dù SMS primary. Consumer lấy gì?

**Đáp án:** EMAIL trong demo; qualifier thu hẹp ứng viên mà injection point muốn.

**Bài 7.** Bean lazy không được tham chiếu bởi eager beans. Nó được tạo lúc nào?

**Đáp án:** Thường khi được truy cập lần đầu, không phải trong initial pre-instantiation của context.

**Bài 8.** Nếu singleton non-lazy trực tiếp cần một lazy bean trong constructor thì sao?

**Đáp án:** Spring có thể phải tạo lazy bean trong startup để đáp ứng eager consumer; lazy definition không phải cam kết “không bao giờ khởi tạo trước getBean trực tiếp”.

**Bài 9.** @DependsOn có tự gọi method send() của Sender theo thứ tự không?

**Đáp án:** Không. Nó ràng buộc bean initialization; method execution do code ứng dụng quyết định.

**Bài 10.** Khi context đóng, thứ tự hủy singletons có depends-on thế nào?

**Đáp án:** Dependent bean bị hủy trước prerequisite bean; ngược chiều ràng buộc initialization.

**Bài 11.** Nếu hai singleton không có quan hệ phụ thuộc, có nên đưa thứ tự init quan sát được vào hợp đồng domain không?

**Đáp án:** Không. Hãy thiết kế dependency/ready-state tường minh nếu thứ tự thật sự quan trọng, tránh phụ thuộc registration order tình cờ.

**Bài 12.** Với task cần các bước chuẩn bị → xử lý → thông báo, dùng @Order trên ba service để Spring tự chạy đủ thứ tự có ổn không?

**Đáp án:** Không. Dùng một orchestrator/service method kiểm soát trình tự nghiệp vụ, lỗi và retry; @Order chỉ có thể hỗ trợ sắp collection nếu chính code thực thi tuần tự trên collection.

## 16. Checklist áp dụng

- [ ] Phân biệt rõ **lựa chọn một bean**, **thứ tự List**, **thứ tự khởi tạo** và **thứ tự chạy nghiệp vụ**.
- [ ] Dùng `@Primary` cho một default hợp lý, không phải startup priority.
- [ ] Dùng `@Qualifier` cho lựa chọn riêng của consumer.
- [ ] Dùng `@Fallback` như chính sách lựa chọn ưu tiên thấp, không phải lazy initialization.
- [ ] Dùng `@Order` cho List/array ordering, không dùng nó làm lifecycle contract.
- [ ] Đọc đúng vị trí @Order trên @Bean methods hoặc target components; không nhầm @Configuration order.
- [ ] Dùng dependency graph/constructor injection cho những collaborator thực sự được dùng.
- [ ] Dùng `@DependsOn` khi cần prerequisite initialization không có direct reference.
- [ ] Không dựa vào thứ tự khai báo @Bean trong source để buộc một side effect khởi tạo.
- [ ] Không nhầm bean initialized với service bên ngoài đã ready hay nghiệp vụ đã chạy.
- [ ] Với @Lazy, kiểm tra có eager consumer buộc bean khởi tạo sớm không.
- [ ] Với prototype, hiểu bean không được eagerly pre-instantiate như singleton.
- [ ] Biết quan hệ hủy singleton ngược chiều depends-on.
- [ ] Không dùng @DependsOn để che circular dependencies.
- [ ] Viết unit tests cho Java logic và integration tests cho chọn bean, List ordering và creation events.
- [ ] Chạy Java 17+ với Maven hoặc javac/java; so **11 dòng stdout ứng dụng**.
- [ ] Khi đọc logs, hỏi “đây là init event, log nghiệp vụ hay destroy callback?”
- [ ] Khi lỗi, sửa đúng annotation/cơ chế phục vụ đúng mục tiêu.

**Kết luận:** `@Primary`, `@Qualifier` và `@Fallback` giúp Spring **chọn đúng bean cho dependency**. `@Order` giúp sắp **nhiều bean** trong những collection/injection points tương ứng. `@DependsOn`, constructor dependencies, `@Lazy` và scope chi phối **khi nào bean được tạo**. Ba lớp quyết định này độc lập về ý nghĩa; nắm ranh giới của chúng giúp bạn tránh wiring lệch, startup assumptions mong manh và sai trình tự nghiệp vụ.

### Tài liệu Spring chính thức

- [Spring 6.2 — Using @Autowired](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html)
- [Spring 6.2 — Fine-tuning @Primary / @Fallback](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-primary.html)
- [Spring 6.2 — Using depends-on](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-dependson.html)
- [Spring 6.2 — Lazy-initialized Beans](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-lazy-init.html)
- [Spring 6.2 — Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html)


</details>

- [Quay lại đầu trang](#back-to-top)
