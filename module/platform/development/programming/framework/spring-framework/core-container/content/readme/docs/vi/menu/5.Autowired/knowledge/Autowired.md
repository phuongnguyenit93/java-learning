<a id="back-to-top"></a>

# @Autowired

## Menu
- [Cơ chế xử lý @Autowired trong container](#autowired-001)
- [Lớp có một hàm khởi tạo không cần ghi @Autowired](#autowired-002)
- [Quy tắc chọn khi có nhiều hàm khởi tạo](#autowired-003)
- [Tiêm trường, setter và phương thức nhiều tham số](#autowired-004)
- [Ý nghĩa required và thời điểm phụ thuộc được giải quyết](#autowired-005)
- [Phân biệt @Autowired với jakarta.inject.Inject](#autowired-006)
- [Chọn theo tên bằng jakarta.annotation.Resource](#autowired-007)
- [@Named, @Qualifier và phạm vi hỗ trợ của chuẩn Jakarta](#autowired-008)
- [Vì sao đối tượng tự tạo ngoài container không được tự động tiêm](#autowired-009)

## <a id="autowired-001">Cơ chế xử lý @Autowired trong container</a>

<details>
<summary>Xem chi tiết</summary>

# Cơ chế xử lý `@Autowired` trong Spring IoC Container

## 1. `@Autowired` là chỉ dẫn cho container, không phải cú pháp tự động của Java

`@Autowired` là annotation thuộc **Spring Framework** (package `org.springframework.beans.factory.annotation`) được dùng để mô tả những điểm mà Spring nên cung cấp dependency. Nó có thể đặt trên **constructor, instance field, setter hoặc phương thức cấu hình có tham số**. Ở những vị trí được hỗ trợ, Spring IoC Container tìm bean phù hợp và đưa **reference của bean đó** cho đối tượng đang được tạo.

Điểm mấu chốt: **Java compiler/JVM không tự chạy DI chỉ vì nhìn thấy `@Autowired`.** Annotation là metadata; Spring phải đọc và xử lý nó thông qua hạ tầng của container.

~~~java
// Snippet minh họa, không phải file độc lập.
@Autowired
private MessageSource source;
~~~

Trong một **Spring-managed bean**, Spring có thể cấp `MessageSource` thích hợp cho field trên. Nhưng nếu bạn viết `new SomeService()` bên ngoài container, Spring không tự tìm đến object vừa `new` để tiêm field; trường có thể vẫn là `null`. Tương tự, chỉ gắn `@Autowired` không làm class tự đăng ký thành bean.

Bài này trả lời năm câu hỏi:
1. Ai đọc `@Autowired`, vào **giai đoạn nào** của vòng đời bean?
2. Spring chọn **constructor**, **bean candidate** và **điểm tiêm** ra sao?
3. Field injection khác constructor injection và method injection tại thời điểm nào?
4. `required`, `Optional<T>`, `ObjectProvider<T>`, `@Qualifier` tác động thế nào tới dependency resolution?
5. Vì sao một số lỗi xảy ra ngay khi context `refresh()`, còn một số chỉ xảy ra khi lấy bean?

**Phạm vi:** Spring Framework Core Container thuần, **không** cần Spring Boot, HTTP server hay database. Ví dụ chính dùng **Java 17+, Spring Framework 7.0.9, Maven**, không dựa vào random, đồng hồ hoặc log nội bộ để xây expected stdout.

## 2. Những thành phần tham gia xử lý annotation

| Thành phần | Vai trò | Không nên nhầm với |
| --- | --- | --- |
| `BeanDefinition` | Metadata đăng ký cách tạo, scope, factory method và cấu hình cho bean | Bean instance thực |
| `BeanFactory` / `ApplicationContext` | Quản lý definitions, tạo và cấp bean, giải quyết dependency | Java compiler |
| `BeanPostProcessor` | Extension point can thiệp một số giai đoạn xử lý bean do Spring tạo | Một business bean bình thường |
| `AutowiredAnnotationBeanPostProcessor` | Processor chuyên trách `@Autowired` và các annotation liên quan; nhận diện injection points và xử lý field/method injection; tham gia chọn constructor candidate | Lệnh `new` của Java |
| `ConfigurationClassPostProcessor` | Xử lý các nguồn cấu hình như `@Configuration`, `@Bean`, `@ComponentScan` để đăng ký metadata thích hợp | Processor tự tiêm mọi field |
| `DependencyDescriptor` và resolver bên trong bean factory | Mô tả type, qualifier, required/optional, generic và chọn dependency phù hợp | Cơ chế Java reflection đơn thuần |
| `InitializingBean` hoặc init callback | Chạy khi các dependency được cấp và bean đã đến bước initialization | Nơi thích hợp để chọn constructor |

Trong thực tế, `AutowiredAnnotationBeanPostProcessor` có các hợp đồng chuyên biệt như **`SmartInstantiationAwareBeanPostProcessor`** và **`InstantiationAwareBeanPostProcessor`**, cho phép nó tham gia cả **xác định constructor** lẫn **population/injection** của instance.

Với `AnnotationConfigApplicationContext` và Java configuration thông thường, Spring đã **đăng ký sẵn các annotation processors thiết yếu**. Vì vậy ví dụ dùng `@Autowired` không cần tự `new AutowiredAnnotationBeanPostProcessor()`.

Trong XML-based context cấu hình thủ công, những cơ chế như `<context:annotation-config/>` hoặc `<context:component-scan/>` giúp đăng ký processor thích hợp. **Chỉ có XML `<bean>` không bảo đảm mọi annotation injection sẽ tự chạy nếu hạ tầng tương ứng không được bật.**

Cần phân biệt: `@Bean` hoặc `@Component` đưa **định nghĩa bean** vào container, còn `@Autowired` yêu cầu **resolve/cấp cộng tác viên** cho một đối tượng đã thuộc quá trình tạo bean. Có bean definition không đồng nghĩa tất cả các dependency đã được resolve hoặc instance đã tồn tại.

## 3. Toàn bộ pipeline từ metadata đến bean sẵn sàng

Đây là **mô hình khái niệm** của một singleton Spring-managed bean thông dụng; trình tự nội bộ có nhiều nhánh cho factory method, scope, lazy, proxy và các extension points khác.

~~~text
Đăng ký @Configuration / @Bean / @Component / registerBean(...)
                             |
                             v
                BeanDefinitions trong factory
                             |
                        context.refresh()
                             |
                Đăng ký BeanPostProcessors
                             |
                 Cần tạo bean đích hay chưa?
                       /             \
                      /               \
            Lazy/prototype        Eager singleton
              có thể đợi               |
                     \                /
                      v              v
         Xác định bean class, factory method,
           candidate constructors, metadata
                             |
           Resolve required constructor args
                             |
                    Tạo Java instance
                             |
         Xử lý @Autowired trên instance fields
                             |
         Xử lý @Autowired trên config methods
                             |
             Các bước population liên quan
                             |
          Aware / before-init / init callbacks
                             |
       Bean sẵn sàng; có thể qua proxy hậu xử lý
                             |
                 getBean(...) cho client
                             |
          close() -> destroy callbacks phù hợp
~~~

### Trước khi khởi tạo object

Spring dùng metadata và các extension points để xác định **constructor phù hợp** hoặc factory method sẽ tạo object. Nếu constructor có tham số, dependencies của các tham số được resolve **để có đối số cho lời gọi constructor**. Vì thế constructor injection **không phải** thao tác gán field sau construction.

### Sau khi khởi tạo object

Nếu class có `@Autowired` trên field hoặc method, processor tìm các injection points cần thiết và sử dụng dependency resolution của bean factory để cấp các reference. **Field injection xảy ra sau constructor**, và trong luồng xử lý thông thường fields được inject trước các autowired configuration methods.

### Trước khi bàn giao bean

Container hoàn tất các bước property population và initialization tương ứng, có thể chạy **`InitializingBean.afterPropertiesSet()`** hoặc init method. Sau các BeanPostProcessor khác, bean được trả cho client; một số tính năng như AOP có thể tạo proxy nên **object trả về không nhất thiết cùng runtime class với target**.

**Không khẳng định Spring tạo tất cả beans ngay trong `refresh()`:** singleton không lazy thường pre-instantiate khi context refresh, còn bean lazy hoặc prototype có thể tạo sau lúc được yêu cầu. Một lazy bean vẫn có thể bị tạo sớm nếu dependency eager khác bắt buộc cần nó.

## 4. Chọn constructor: trước khi instance xuất hiện

Có thể gắn `@Autowired` trên constructor:

~~~java
// Snippet, giả sử type MessageSource đã khai báo.
final class Consumer {
    private final MessageSource source;

    @Autowired
    Consumer(MessageSource source) {
        this.source = source;
    }
}
~~~

Spring cần tìm một bean phù hợp với `MessageSource` **trước** khi gọi constructor. Do đó field `source` có thể là `final` và object được xây dựng với một dependency rõ ràng.

Quy tắc đáng nhớ của Spring:
- Nếu class bean **chỉ khai báo một constructor**, constructor đó thường được Spring dùng **không cần** `@Autowired`.
- Nếu class có **nhiều constructors**, Spring phải có quy tắc/metadata để chọn. Một constructor được đánh dấu `@Autowired(required = true)` (mặc định) là lựa chọn bắt buộc; không thể đặt nhiều constructor cùng required `true`.
- Với nhiều constructors được đánh dấu non-required (`required=false`), Spring có thể đánh giá candidates theo mức dependency có thể resolve, rồi dùng default/primary constructor khi quy tắc cho phép. Đây **không phải** cam kết rằng mọi overload được thử tùy tiện.
- Constructor riêng tư/không `public` có thể được Spring sử dụng khi khả năng truy cập phản chiếu của môi trường cho phép.
- **Lỗi thiếu dependency bắt buộc** có thể xuất hiện khi bean được khởi tạo, và với eager singleton đó thường là trong `refresh()`.

Nếu class được tạo bằng **`@Bean` factory method**:

~~~java
@Bean
Consumer consumer(MessageSource source) {
    return new Consumer(source);
}
~~~

Spring resolve tham số **factory method** rồi gọi đoạn `new Consumer(source)` do bạn viết. Đây cũng là một dạng DI do container thực hiện, nhưng **đừng mô tả nhầm rằng Spring đang tự chọn constructor của `Consumer` trong factory method**: chính factory method đã chọn constructor trong source Java.

## 5. Inject vào field và method: sau constructor

### Field injection

~~~java
@Autowired
private Audit audit;
~~~

Processor xác định field cần gán rồi resolve `Audit` phù hợp. Nếu bean không có candidate và injection point là required, bean creation có thể thất bại. Field không cần `public`, nhưng phải thuộc đối tượng Spring-managed và environment cho phép reflection thích hợp.

Không thiết kế required fields để chỉ được gán sau construction rồi truy cập ngay trong constructor. `private final Audit audit` thường phù hợp **constructor injection**, không phải field injection.

### Setter hoặc arbitrary configuration method

~~~java
@Autowired
void configure(Audit audit) {
    this.audit = audit;
}
~~~

Tên method **không bắt buộc** theo mẫu `setX`. Spring cũng có thể resolve **nhiều tham số** của một config method nếu các dependency tương ứng tồn tại. Với method injection, các tham số được resolve trước khi Spring gọi method; lỗi thiếu bean ở một tham số có thể làm toàn method injection không hoàn thành.

Ví dụ thứ tự quan sát được trong demo của bài:
1. Constructor in rằng field chưa được gán.
2. Field `audit` được Spring tiêm.
3. Method `configureAudit(Audit)` được gọi, thấy field đã có reference.
4. `afterPropertiesSet()` quan sát trạng thái bean đã được cung cấp các dependency.

Đây là mô hình kiểm chứng **lifecycle injection** của một bean bình thường; không cần hardcode danh sách tất cả các processors hay thứ tự bean definitions nội bộ.

### Những vị trí không nên dùng

`@Autowired` không phải cơ chế DI **vào static field/method** thông thường. Đừng cố dùng `@Autowired static Repo repo` để có dependency toàn JVM. Tương tự, một instance tạo qua `new` nằm ngoài container sẽ không nhận field/method injection tự động.

Trong Java module path, reflection đối với field/method không-public có thể cần thiết lập `opens` phù hợp. Dự án demo dùng **classpath** nên không đưa những giới hạn JPMS vào bước chạy cơ bản.

## 6. Spring tìm bean phù hợp như thế nào?

Dependency resolution dựa trước hết vào **kiểu** và metadata của injection point. Spring có thể xét type (kể cả generic type), các qualifiers, tập bean candidates, primary/fallback, tên injection point và những luật ưu tiên tương ứng.

| Tình huống | Kết quả hoặc cách xử lý |
| --- | --- |
| Một bean có type phù hợp, injection required | Bean được chọn và reference được cấp |
| Không có bean phù hợp, required | Thông thường **bean creation thất bại** |
| Nhiều beans cùng type, không có cách phân giải | Có thể gặp `NoUniqueBeanDefinitionException` |
| Có bean được đánh dấu `@Primary` trong tập phù hợp | Bean primary có thể được ưu tiên khi resolve single-valued dependency |
| Injection point có `@Qualifier("name")` | Thu hẹp candidates theo qualifier/name phù hợp, **không tạo bean mới** |
| Generic types khác nhau | Spring có thể dùng generic type như một dạng qualifier |
| `List<T>`, `T[]`, `Map<String,T>` | Có thể inject tập beans phù hợp; `Map` dùng bean names làm keys |
| Nhiều context cha/con | Resolution có thể liên quan hierarchy, visibility và local overrides theo quy tắc container |

**Không nên viết “Spring luôn tìm theo tên rồi mới theo type”.** Autowiring được thiết kế theo type và những điều kiện chọn candidate; bean name/parameter name có thể tham gia khi loại/qualifier và các ưu tiên cho phép, nhưng không phải một luật đơn giản dùng cho mọi trường hợp.

Ví dụ minh họa (không phải project độc lập):

~~~java
@Autowired
@Qualifier("fastGateway")
private Gateway gateway;
~~~

Spring cần một bean `Gateway` phù hợp với qualifier. `fastGateway` trong annotation là thông tin **chọn dependency**, không phải hướng dẫn tạo một `Gateway` mới. Khi class có nhiều implementation, hãy đặt qualifiers rõ và tránh phụ thuộc vào tên tham số không được giữ lại khi biên dịch.

**Điểm dễ nhầm:** một bean tên giống field không bảo đảm luôn được chọn nếu bean type không tương thích hoặc bị loại bởi qualifier/candidate filters. Một `@Primary` không làm thay đổi Java type hay tạo alias.

## 7. Required, optional và provider được xử lý khác nhau

Theo mặc định, field/method `@Autowired` là **required**. Khi thiếu bean, container thường fail tại thời điểm tạo bean. Tuy nhiên Spring hỗ trợ những cách biểu đạt dependency thực sự **tùy chọn**.

### `@Autowired(required = false)`

~~~java
@Autowired(required = false)
void configureMissing(MissingNotifier missing) {
    // Spring có thể bỏ qua lời gọi method nếu không có candidate.
}
~~~

Với **non-required config method**, khi thiếu một dependency không thể thỏa, Spring có thể **không gọi cả method**. Đối với non-required field, trường có thể giữ giá trị ban đầu. Không đồng nhất hành vi với việc Spring tự truyền `null` cho mọi tham số.

### `Optional<T>`

~~~java
@Autowired
private Optional<Metrics> metrics;
~~~

Spring có thể cung cấp `Optional.empty()` khi không có bean `Metrics` phù hợp, thay vì từ chối bean chứa field. Khi có candidate, `Optional` có thể giữ reference tới bean. Mã business phải thực sự xử lý nhánh empty.

**Không gọi `metrics.get()` vô điều kiện** khi dependency optional. Đừng dùng optional cho chức năng bắt buộc về mặt nghiệp vụ chỉ để làm context “khởi động được”.

### `ObjectProvider<T>`

~~~java
@Autowired
private ObjectProvider<MissingNotifier> notifierProvider;
~~~

Container inject một **provider**, không nhất thiết tạo bean đích ngay khi inject. `notifierProvider.getIfAvailable()` có thể trả `null` nếu không có bean; `getObject()` đòi bean thực và có thể ném exception nếu thiếu. Provider có ích khi cần **lấy dependency theo nhu cầu**, đặc biệt với prototype hoặc lazy behavior.

### `@Nullable`

Spring có thể hiểu **nullability annotation được hỗ trợ** (như JSpecify `org.jspecify.annotations.Nullable` ở các Spring lines hiện đại) trên injection point để biểu thị được phép vắng mặt. Hãy dùng đúng annotation và dependency tương ứng cho version đang chạy; đây **không phải** một cơ chế Java compiler tự thêm bean.

**Đối với arrays/collections/maps:** tài liệu Spring mô tả default required semantics có thể cần ít nhất một candidate cho những injection points bắt buộc. Có các ngoại lệ theo loại điểm inject và phiên bản (chẳng hạn tham số của một constructor duy nhất có thể nhận empty collection), nên không suy luận rằng mọi `List<T>` required đều luôn empty khi thiếu.

## 8. Dự án Maven Java 17 chạy được: quan sát từng giai đoạn

Ví dụ tạo một bean `Consumer` có **constructor `@Autowired`**, **field `@Autowired`**, **method `@Autowired`**, `Optional<Metrics>`, `ObjectProvider<MissingNotifier>`, một optional method bị bỏ qua và callback `InitializingBean.afterPropertiesSet()`.

Để loại bỏ nhầm lẫn về component scan, `Consumer` được **đăng ký tường minh bằng `registerBean(Consumer.class)`**. `AppConfig` khai báo `MessageSource` và `Audit`. Context tự kích hoạt annotation processors thích hợp.

### 8.1. Cấu trúc

~~~text
autowired-pipeline-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── AutowiredPipelineDemo.java
~~~

Yêu cầu **JDK 17+**, **Maven 3.9+** và khả năng tải Maven dependencies ở lần chạy đầu.

### 8.2. `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>autowired-pipeline-demo</artifactId>
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
                    <mainClass>demo.AutowiredPipelineDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` cung cấp các Spring API trong bài và tự kéo Spring artifacts nền tương ứng qua Maven dependency resolution. Không cần Spring Boot, annotation processing plugin của Java compiler hay web starter. Maven Compiler Plugin biên dịch với `--release 17`.

### 8.3. `src/main/java/demo/AutowiredPipelineDemo.java`

~~~java
package demo;

import java.util.Optional;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class AutowiredPipelineDemo {
    private AutowiredPipelineDemo() {
    }

    @FunctionalInterface
    interface MessageSource {
        String message(String name);
    }

    interface Metrics {
    }

    interface MissingNotifier {
        void notifyEvent(String event);
    }

    record Audit(String label) {
    }

    static final class Consumer implements InitializingBean {
        private final MessageSource source;

        @Autowired
        private Audit audit;

        @Autowired
        private Optional<Metrics> metrics;

        @Autowired
        private ObjectProvider<MissingNotifier> notifierProvider;

        private Audit auditFromMethod;
        private boolean optionalMethodCalled;

        @Autowired
        Consumer(MessageSource source) {
            this.source = source;
            System.out.println("constructor.fieldNull=" + (audit == null));
        }

        @Autowired
        void configureAudit(Audit selectedAudit) {
            this.auditFromMethod = selectedAudit;
            System.out.println("method.fieldReady=" + (audit != null));
        }

        @Autowired(required = false)
        void configureMissing(MissingNotifier notifier) {
            optionalMethodCalled = true;
        }

        @Override
        public void afterPropertiesSet() {
            boolean ready = source != null
                    && audit != null
                    && auditFromMethod != null
                    && metrics != null
                    && notifierProvider != null;
            System.out.println("init.ready=" + ready);
            System.out.println("init.metricsAbsent=" + metrics.isEmpty());
        }

        String message(String name) {
            return source.message(name);
        }

        boolean sameAudit() {
            return audit == auditFromMethod;
        }

        boolean optionalMethodSkipped() {
            return !optionalMethodCalled;
        }

        boolean providerMissing() {
            return notifierProvider.getIfAvailable() == null;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        public MessageSource messageSource() {
            return name -> "Hello, " + name + "!";
        }

        @Bean
        public Audit audit() {
            return new Audit("trace");
        }
    }

    public static void main(String[] args) {
        // Context rỗng: đăng ký config và Consumer, sau đó refresh.
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(AppConfig.class);
            context.registerBean(Consumer.class);
            context.refresh();

            Consumer consumer = context.getBean(Consumer.class);
            System.out.println("lookup.message=" + consumer.message("Lan"));
            System.out.println("lookup.sameAudit=" + consumer.sameAudit());
            System.out.println("optional.setterSkipped="
                    + consumer.optionalMethodSkipped());
            System.out.println("optional.providerMissing="
                    + consumer.providerMissing());
            System.out.println("scope.singleton="
                    + (consumer == context.getBean(Consumer.class)));
        }

        // Thiếu dependency bắt buộc: exception khi context.refresh().
        try (var missing = new AnnotationConfigApplicationContext()) {
            missing.registerBean(Consumer.class);
            missing.refresh();
        } catch (BeansException expected) {
            System.out.println("missingRequired.failed=true");
        }
    }
}
~~~

**Giải thích các điểm kiểm chứng:**

1. `register(AppConfig.class)` và `registerBean(Consumer.class)` làm xuất hiện các bean definitions. Constructor `Consumer(MessageSource)` được xử lý như injection point; Spring phải resolve `MessageSource` để tạo object.
2. Constructor quan sát `audit == null` → `true`, vì **field injection chưa xảy ra** ở giai đoạn đang chạy constructor.
3. Khi `configureAudit(Audit)` chạy, field `audit` đã có reference; đó là bằng chứng field injection đến **trước autowired method injection** trong demo này.
4. `afterPropertiesSet()` được gọi khi các injection points đã được xử lý. `init.ready=true` không phải phép đo mọi tác vụ initialization/proxy trong Spring; nó chỉ xác nhận các dependency cần quan sát ở demo đều có sẵn.
5. Không có bean `Metrics`, nên Spring tiêm `Optional.empty()` và `init.metricsAbsent=true`.
6. Không có bean `MissingNotifier`: non-required method `configureMissing` không được gọi; `ObjectProvider` vẫn được inject, và `getIfAvailable()` không tìm thấy bean.
7. `Consumer` mặc định singleton. Lấy hai lần từ cùng context cho cùng reference.
8. Context thứ hai chỉ đăng ký `Consumer`, cố ý thiếu `MessageSource` bắt buộc. Tạo Consumer không thành công; chương trình catch `BeansException` và in một dòng, không phụ thuộc chính xác class/message/stack trace.

**Điểm quan trọng về lambda:** `MessageSource` là interface có một abstract method nên biểu thức `name -> "Hello, " + name + "!"` hợp lệ trong Java 17. `Audit` là Java `record`, cũng là cú pháp được hỗ trợ trong Java 17.

**Không kết luận `@Autowired` xử lý `Consumer` vì class có `@Component`:** class ở đây **không có** `@Component`; nó được `registerBean` trực tiếp. Điều cần thiết là nó thuộc container và có annotation processor thích hợp.

## 9. Build, chạy và expected stdout

Từ thư mục `autowired-pipeline-demo` có `pom.xml`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

Các lệnh phiên bản môi trường không có một stdout chung cố định. Lệnh cuối dùng Maven gọi compiler và chạy main class với classpath Spring tương ứng.

**10 dòng stdout của code demo** (không tính log framework hoặc Maven diagnostics):

~~~text
constructor.fieldNull=true
method.fieldReady=true
init.ready=true
init.metricsAbsent=true
lookup.message=Hello, Lan!
lookup.sameAudit=true
optional.setterSkipped=true
optional.providerMissing=true
scope.singleton=true
missingRequired.failed=true
~~~

**Vì sao thứ tự này hợp lý?**
- Ba dòng đầu cùng dòng `init.metricsAbsent` được in khi context thứ nhất tạo singleton Consumer trong `refresh()`, trước khi `getBean` trả bean cho đoạn code ở `main`.
- Nhóm `lookup.*`, `optional.*`, `scope.*` do code `main` in khi context đã active.
- Dòng cuối xảy ra khi context thứ hai cố tạo Consumer thiếu dependency required, rồi bị catch; lỗi đó có thể được Spring logging ghi thêm diagnostic không nằm trong các dòng `System.out.println` chuẩn.

Bạn có thể kiểm tra biến thể với `@Lazy` trên Consumer: nếu không có bean eager khác cần nó, các dòng constructor/method/init có thể được trì hoãn tới lần `getBean(Consumer.class)` thay vì lúc refresh. **Autowiring vẫn là cùng cơ chế**, chỉ thay đổi thời điểm yêu cầu tạo bean.

## 10. Java 17 thuần: tại sao annotation không tự chạy?

Để tách hành vi Java khỏi Spring, tạo tệp riêng `PureJavaInjection.java` và chạy hoàn toàn không cần Maven/JAR Spring:

~~~java
public class PureJavaInjection {
    interface Greeter {
        String greet(String name);
    }

    static final class Service {
        private Greeter field;

        Service() {
            System.out.println("constructor.fieldNull="
                    + (field == null));
        }

        void setGreeter(Greeter value) {
            field = value;
        }

        String greet(String name) {
            return field.greet(name);
        }
    }

    public static void main(String[] args) {
        Service service = new Service();
        System.out.println("beforeManualInjection="
                + (service.field == null));
        service.setGreeter(name -> "Fake:" + name);
        System.out.println("afterManualInjection="
                + service.greet("Lan"));
    }
}
~~~

Lệnh chạy từ thư mục chứa file:

~~~bash
javac PureJavaInjection.java
java PureJavaInjection
~~~

Stdout:

~~~text
constructor.fieldNull=true
beforeManualInjection=true
afterManualInjection=Fake:Lan
~~~

Đây **không** phải ví dụ `@Autowired` trực tiếp; nó cố ý chỉ minh họa cơ chế object được tạo trước rồi được cấp dependency sau. Spring tự động hóa phần tìm/cấp bean khi bean thuộc container. Java thuần không tự thực thi annotation của một framework nếu không có hạ tầng xử lý.

Mặt khác, **constructor injection** vẫn có thể làm hoàn toàn bằng Java: `new Service(greeter)` với constructor nhận `Greeter` là một cách DI thủ công hợp lệ. Container giúp tổ chức resolution và lifecycle, không thay thế ngôn ngữ Java.

## 11. Những lỗi thường gặp khi `@Autowired` không hoạt động

| Triệu chứng | Tầng gây lỗi | Hướng điều tra |
| --- | --- | --- |
| Field `@Autowired` còn null trong object tự `new` | Object **ngoài container** | Đăng ký bean hoặc chuyển sang constructor injection để test Java thuần |
| Class có `@Autowired` nhưng context không biết class | **Không có bean definition** | Dùng `@Component` + scan đúng package, `@Bean` hoặc `registerBean` |
| XML bean có annotation nhưng không được inject | **Thiếu annotation post-processors** | Bật `<context:annotation-config/>` / component scan hoặc processor tương ứng |
| Không có bean cùng type | **Required dependency resolution** | Đăng ký đúng type/qualifier, xem chain `Caused by` |
| Nhiều beans cùng type | **Ambiguous candidates** | `@Primary`, `@Qualifier` hoặc cấu hình lựa chọn minh bạch |
| Field được dùng ngay trong constructor | **Sai thứ tự lifecycle** | Chuyển required dependency sang constructor |
| `@Autowired(required=false)` method không chạy | Optional injection point **không thỏa** | Giữ default state hợp lệ; không coi đó là lỗi nếu dependency thực sự optional |
| `ObjectProvider.getObject()` ném exception | Chọn API **required lookup** | Cân nhắc `getIfAvailable()` và fallback có chủ đích |
| `Optional<T>` rỗng dù muốn có bean | Bean không đăng ký, sai type/qualifier hoặc config ngoài dự kiến | Xác định đúng context/bean registrations; đừng gọi `get()` mù quáng |
| `@Autowired` lên static field không có hiệu lực | **Static member không hỗ trợ kiểu injection này** | Thiết kế instance service; tránh global mutable state |
| Sai `@Qualifier` | Không có candidate đúng qualifier | Đối chiếu bean name/qualifier metadata và injection point |
| Java source import Spring không compile | **Classpath/build**, không phải autowiring | Kiểm `spring-context`, build tool, dependency versions |
| Bean không được post-process đầy đủ | Bean được tạo quá sớm, custom BPP/FactoryPostProcessor hay manual lifecycle | Xem giai đoạn registration và bean creation |
| Circular constructor dependencies | Đồ thị **không có thứ tự dựng object hợp lệ** | Tách trách nhiệm, đổi hướng phụ thuộc; tránh biến required thành optional để che lỗi |

**Một giới hạn quan trọng:** Các chính `BeanPostProcessor` và `BeanFactoryPostProcessor` có vòng đời **đặc biệt** và được đăng ký/khởi tạo sớm. Tài liệu Spring cảnh báo không trông chờ `@Autowired`/`@Inject`/`@Value`/`@Resource` xử lý đầy đủ **bên trong những processor do bạn tự viết**; nên cấu hình dependencies của chúng rõ ràng bằng factory methods hoặc XML theo hướng dẫn framework.

Ngoài ra, nếu một custom `BeanPostProcessor` được đăng ký quá muộn thì một số beans đã tạo trước đó **không được xử lý lại**. Vì thế việc “đã thấy annotation” không đủ để đảm bảo DI đúng; phải xem hạ tầng processor đã tham gia vào **lifecycle của bean đích** hay chưa.

## 12. Kiểm chứng độc lập, debug và lựa chọn thiết kế

### Khi cần quan sát pipeline

- Đặt breakpoint tại constructor của bean, method `@Autowired`, `afterPropertiesSet()` để thấy các giai đoạn.
- Trong Spring source/debugger, xem `AutowiredAnnotationBeanPostProcessor` và cơ chế `DefaultListableBeanFactory.resolveDependency`. Đây là **cơ chế nội bộ để học**, không phải API nên gọi từ business code.
- Dùng `getBeanDefinitionNames()`, `getBeanNamesForType` để điều tra registrations; **không hardcode số bean definitions** vì các processors và infrastructure beans cũng được đăng ký.
- Đọc exception `UnsatisfiedDependencyException`, `NoSuchBeanDefinitionException`, `NoUniqueBeanDefinitionException` theo **cause chain**. Chuỗi message chính xác không phải một contract ổn định qua mọi patch.
- Test bean wiring bằng context nhỏ khi cần; dùng fake và constructor injection cho các unit tests chỉ kiểm tra logic.

### Khi nào dùng field, setter hay constructor?

- **Required dependency:** ưu tiên **constructor injection** + field `final` và invariant validation; một constructor duy nhất thường không cần `@Autowired`.
- **Truly optional/configurable dependency:** có thể dùng `Optional<T>`, `ObjectProvider<T>` hoặc setter có default hợp lệ, tùy thời điểm cần resolve.
- **Method injection nhiều đối số:** hữu ích khi cần cấu hình object trong một bước, nhưng cần hiểu required semantics và việc gọi sau construction.
- **Không dùng `@Autowired` để giấu đồ thị quá lớn:** constructor có rất nhiều dependencies có thể báo hiệu class đang ôm nhiều trách nhiệm, cần phân chia thiết kế thay vì chuyển toàn bộ sang private fields để trông ngắn hơn.
- **Không dùng `ApplicationContext.getBean` rải khắp nghiệp vụ:** thường nên nhận dependency qua constructor thay vì phụ thuộc service locator.
- **Không cho rằng `@Autowired` tự tạo bean class chưa được đăng ký:** annotation giải quyết wiring cho bean đang tạo, không thay thế registration/composition root.

## 13. Bài tập và checklist tự đánh giá

Làm từng thử nghiệm **trên một bản sao** và khôi phục code gốc trước khi so stdout mục 9.

1. **Chuyển `Consumer` sang một constructor duy nhất không có `@Autowired`:** dự đoán khả năng Spring tự dùng constructor ấy.
2. **Bỏ bean `MessageSource`:** context đầu tiên có thể fail khi constructor của Consumer cần `MessageSource`. Vì sao `Optional<Metrics>` vẫn không giải quyết được?
3. **Bỏ bean `Audit`:** constructor có thể vẫn chạy và in field null, nhưng required field/method injection sẽ fail sau đó. Tách lỗi constructor resolution với property population.
4. **Thêm bean `Metrics`:** dự đoán `init.metricsAbsent=false` và giải thích `Optional.of(bean)`.
5. **Thêm bean `MissingNotifier`:** dự đoán optional method `configureMissing` được gọi, provider lấy được bean và hai dòng `optional.*` đổi giá trị.
6. **Thêm hai implementations `MessageSource`:** dự đoán khả năng ambiguity; giải quyết bằng `@Primary` hoặc `@Qualifier`.
7. **Đổi thứ tự khai báo `@Bean` methods:** có thay đổi bản chất dependency resolution không? Tránh suy luận tuyệt đối từ source ordering.
8. **Đặt `@Lazy` lên `Consumer`:** dự đoán constructor/method/init log xuất hiện lúc `getBean` thay vì refresh khi không có eager consumer.
9. **Gọi `new Consumer(fake)` ngoài Spring:** constructor vẫn chạy, nhưng các field/method `@Autowired` không được xử lý; nếu dùng `afterPropertiesSet` thủ công, tại sao gọi nó không tự inject?
10. **Đổi `@Autowired(required=false)` sang required method:** context đầu tiên có còn khởi động bình thường nếu thiếu `MissingNotifier`?
11. **So constructor vs factory method:** chuyển Consumer sang `@Bean Consumer consumer(MessageSource source)`, giải thích vì sao Spring resolve method parameter nhưng code Java chọn constructor.
12. **Viết unit test:** truyền fake `MessageSource` vào một class chỉ dùng constructor injection để kiểm tra behavior mà không mở context. Viết integration test chỉ khi muốn kiểm tra processor/registration.
13. **Kiểm tra scope:** đổi Consumer thành prototype và lấy hai lần; quan sát mỗi instance được post-process và có dependency riêng theo quy tắc scope.

**Checklist:**
- [ ] Phân biệt annotation **metadata** với DI do container thực thi.
- [ ] Biết vai trò `AutowiredAnnotationBeanPostProcessor` và các hạ tầng bean factory liên quan.
- [ ] Trình bày đúng thứ tự constructor selection → construction → field/method population → initialization.
- [ ] Phân biệt `@Autowired` constructor với Spring injection của tham số `@Bean` factory method.
- [ ] Biết bean cần được **đăng ký và processor cần tồn tại**.
- [ ] Giải thích được type/qualifier/primary và ambiguity.
- [ ] Biết `Optional<T>`, `ObjectProvider<T>`, non-required method có hành vi khác nhau.
- [ ] Chạy được Maven demo Java 17 ra đúng **10 dòng stdout** và Java thuần ra **3 dòng**.
- [ ] Biết vì sao static fields, tự `new` objects và processor quá sớm không phải đường injection thông thường.
- [ ] Không coi `@Autowired` là lời bảo đảm business logic hoặc concurrency đúng.

## 14. Tóm tắt và nguồn tham khảo chính thức

**Tóm tắt cơ chế:**

1. Đăng ký nguồn cấu hình → Spring có **bean definitions**.
2. Khi cần tạo bean, container xem metadata và **chọn constructor/factory method**; resolve những required args tương ứng.
3. Container tạo object; `AutowiredAnnotationBeanPostProcessor` thực hiện **field/method injection** ở các injection points được hỗ trợ.
4. Container xử lý initialization/lifecycle và có thể áp dụng các processors khác/proxies trước khi đưa bean cho client.
5. Scope, lazy, optional/required và bean candidates quyết định **khi nào** và **bean nào** được cấp.

Tài liệu chính thức:
- [Spring Framework – Using @Autowired](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html)
- [Spring Framework – Annotation-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config.html)
- [Spring API – AutowiredAnnotationBeanPostProcessor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/annotation/AutowiredAnnotationBeanPostProcessor.html)
- [Spring Framework – Fine-tuning Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html)
- [Spring Framework – Container Extension Points](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html)
- [Spring Framework – Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java.html)
- [Spring Framework – Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)

**Ghi nhớ:** `@Autowired` **không tự tạo bean và không chạy chỉ vì annotation tồn tại**. Nó là metadata được Spring đọc **khi tạo một bean thuộc container**, từ đó chọn/cấp dependency theo type và các quy tắc candidate, required/optional, rồi hoàn tất vòng đời bean. Nắm pipeline ấy giúp giải thích những lỗi autowiring mà việc chỉ đổi một annotation không thể sửa được.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="autowired-002">Lớp có một hàm khởi tạo không cần ghi @Autowired</a>

<details>
<summary>Xem chi tiết</summary>

# Lớp có một hàm khởi tạo không cần ghi `@Autowired`

## 1. Quy tắc cốt lõi của Spring

Trong Spring Framework hiện đại (từ **Spring Framework 4.3**), khi một class được Spring quản lý dưới dạng **bean** và class đó chỉ có **một constructor**, Spring có thể chọn chính constructor ấy để tạo bean và tự giải quyết các tham số dependency của nó. **Không cần đặt `@Autowired` lên constructor duy nhất.**

Ví dụ:

~~~java
public final class OrderService {
    private final PriceCalculator calculator;

    public OrderService(PriceCalculator calculator) {
        this.calculator = calculator;
    }
}
~~~

Đoạn trên chỉ minh họa: `PriceCalculator` cần được định nghĩa và đăng ký trong context. Spring có thể thấy constructor `OrderService(PriceCalculator)`, tìm bean `PriceCalculator` phù hợp rồi gọi constructor. Nếu mọi tham số đều thỏa mãn, `OrderService` được tạo mà không cần `@Autowired`.

**Điều kiện quan trọng nhất:** `OrderService` phải thực sự được **đăng ký để container tạo/quản lý**. Việc một class có đúng một constructor không tự khiến nó xuất hiện trong Spring ApplicationContext.

~~~text
Class Java có đúng một constructor
            |
            +-- Object do Spring tạo qua bean definition
            |       |
            |       +-- Spring chọn constructor duy nhất
            |       +-- Spring tìm bean cho các tham số
            |       +-- Spring gọi constructor
            |       +-- Object trở thành managed bean
            |
            +-- Object do người dùng gọi new
                    |
                    +-- Java chỉ gọi constructor với các argument
                        mà chính người dùng đã truyền
                    +-- Không tự lookup bean / autowire
~~~

Quy tắc này làm mã constructor injection **gọn hơn**, chứ không thay đổi bản chất của Dependency Injection: service khai báo dependency và nhận implementation từ bên ngoài.

## 2. Phải phân biệt “có một constructor” với “được Spring đăng ký”

Một constructor Java bình thường:

~~~java
public CheckoutService(InventoryPort inventory) {
    this.inventory = java.util.Objects.requireNonNull(inventory);
}
~~~

là đủ biểu diễn **dependency bắt buộc**. Nhưng container chỉ xử lý nó khi class được đưa vào bean graph theo một con đường hợp lệ.

### Ba cách đăng ký phổ biến

| Cách | Spring biết class bằng cách nào? | Ai thực sự gọi constructor? |
| --- | --- | --- |
| `@Component` và component scanning | Scan package rồi phát hiện component | Spring thực hiện bean instantiation |
| `AnnotationConfigApplicationContext.register(MyService.class)` | Code đăng ký bean class tường minh | Spring thực hiện bean instantiation |
| `@Bean MyService service(Dependency d) { return new MyService(d); }` | Factory method cung cấp object | Chính body `@Bean` gọi constructor, tham số factory được Spring inject |

**Lưu ý ở dòng `@Bean`:** nếu code factory method viết `return new MyService(d);`, Spring không tự động “chọn constructor của `MyService`” ở bước đó. Spring giải quyết **tham số của phương thức factory** `service(Dependency d)`; còn chính phương thức Java gọi `new MyService(d)`. Điều này vẫn là DI, nhưng không phải ví dụ trực tiếp chứng minh quy tắc chọn constructor duy nhất của class bean.

**Không bắt buộc có `@Component`:** class có thể giữ hoàn toàn thuần Java nếu được đăng ký tường minh bằng `register(...)`, hoặc instance được tạo bằng `@Bean`.

### Ví dụ component scanning

~~~java
@Component
public class ReportService {
    private final ReportRepository repository;

    public ReportService(ReportRepository repository) {
        this.repository = repository;
    }
}
~~~

Đây là **trích đoạn minh họa**, không chạy độc lập vì còn cần import `Component`, định nghĩa `ReportRepository` và bật component scanning. Khi container phát hiện `ReportService`, constructor duy nhất được dùng cho injection mà không đòi annotation `@Autowired`. Nếu không có bean `ReportRepository`, bean creation sẽ thất bại.

### Ví dụ đăng ký class trực tiếp

~~~java
AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext();

context.register(AppConfig.class, ReportService.class);
context.refresh();
ReportService service = context.getBean(ReportService.class);
context.close();
~~~

Đây cũng là **mã minh họa**: cần khai báo các class và import Spring thích hợp. Trong phần ứng dụng chạy được ở mục 5, ta dùng chính cách đăng ký này để loại bỏ khả năng nhầm với cơ chế `@Bean` tạo service.

## 3. Vì sao constructor injection phù hợp với dependency bắt buộc?

So với field injection hoặc setter injection, constructor mang những lợi ích thiết kế rõ:

- **Dependency hiện ngay ở public API của class:** chỉ cần đọc constructor là thấy service cần collaborator gì.
- **Fail sớm:** thiếu dependency cần thiết sẽ khiến Spring không tạo được bean; khi dùng Java thuần, code buộc phải cung cấp argument.
- **Có thể dùng `final` fields:** các reference cần thiết được gán một lần khi tạo object, không bị gán lại qua setter thông thường.
- **Test Java thuần đơn giản:** tạo fake implementation và truyền vào constructor, không cần khởi động Spring.
- **Không cần service giữ ApplicationContext:** lớp nghiệp vụ không phải tự lookup bean.
- **Tái sử dụng dễ:** business class không cần import Spring nếu không có các annotation/phụ thuộc Spring khác.

Ví dụ:

~~~java
public final class CheckoutService {
    private final InventoryPort inventory;
    private final ReceiptPort receipts;

    public CheckoutService(InventoryPort inventory, ReceiptPort receipts) {
        this.inventory = java.util.Objects.requireNonNull(inventory);
        this.receipts = java.util.Objects.requireNonNull(receipts);
    }
}
~~~

Đây là **đoạn thiết kế minh họa**. Nó có một constructor với **hai tham số**. Quy tắc “một constructor không cần `@Autowired`” vẫn áp dụng: không hề giới hạn constructor chỉ được có **một tham số**. Spring có thể resolve từng tham số theo type và các qualifier phù hợp.

**`final` không có nghĩa tất cả collaborator tự immutable hay thread-safe.** Nó chỉ bảo đảm field reference không bị gán lại sau khi constructor hoàn thành. Bean singleton chứa mutable state vẫn cần thiết kế đồng thời thích hợp.

## 4. Đừng nhầm ba khái niệm “một” khác nhau

| Cụm từ | Ý nghĩa | Đúng hay sai? |
| --- | --- | --- |
| **Một constructor** | Class khai báo đúng một constructor để Spring xem xét | Đây là điều kiện của quy tắc không cần `@Autowired` |
| Một tham số | Constructor nhận đúng một argument | Không bắt buộc; có thể nhận nhiều argument |
| Một bean trong context | Chỉ một instance hoặc một bean definition tương ứng | Không đồng nghĩa; scope/ứng viên dependency là vấn đề khác |

Một class có duy nhất constructor gồm ba argument vẫn thuộc quy tắc:

~~~java
public BillingService(AccountRepository accounts,
                      ExchangeRateClient rates,
                      ReceiptSender receipts) {
    this.accounts = accounts;
    this.rates = rates;
    this.receipts = receipts;
}
~~~

Đoạn minh họa này cần field và interface phù hợp nếu muốn compile. Spring phải có **cách giải quyết mỗi dependency**; việc chỉ có một constructor **không miễn trừ lỗi thiếu bean**.

Lớp không khai báo constructor tường minh có một **constructor mặc định không tham số** do Java cung cấp (khi đủ điều kiện). Nếu class đó được tạo như Spring bean, nó có thể được tạo không cần constructor arguments. Nhưng điều này **không chứng minh injection**: không có dependency nào được inject vào constructor không tham số.

## 5. Ví dụ Maven/Spring Framework 6.2, Java 17 runnable

Ứng dụng console chứng minh trực tiếp:

1. `OrderService` có **một constructor tham số**, **không có `@Autowired`** và **không có `@Component`**.
2. `PricePolicy` có một implementation được đăng ký bằng `@Bean`.
3. `context.register(AppConfig.class, OrderService.class)` làm Spring biết class service.
4. Spring tự cấp bean `PricePolicy` cho constructor `OrderService`.
5. `getBean(OrderService.class)` lấy được service hoạt động, hai lần lookup singleton nhận cùng reference.

### 5.1. Yêu cầu và cấu trúc thư mục

Cần JDK 17+ và Apache Maven. Ví dụ dùng Spring Framework **6.2.0** qua `spring-context`, không dùng Spring Boot, web server hoặc database.

~~~text
one-constructor-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── SingleConstructorDemo.java
~~~

Tạo `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>one-constructor-demo</artifactId>
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
                    <mainClass>demo.SingleConstructorDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 5.2. File Java hoàn chỉnh

Lưu file `src/main/java/demo/SingleConstructorDemo.java`:

~~~java
package demo;

import java.util.Objects;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class SingleConstructorDemo {
    private SingleConstructorDemo() {}

    // Một hợp đồng Java: không phụ thuộc Spring.
    public interface PricePolicy {
        int calculate(int unitPrice, int quantity);
    }

    // Implementation do configuration chọn.
    public static final class StandardPricePolicy implements PricePolicy {
        @Override
        public int calculate(int unitPrice, int quantity) {
            return Math.multiplyExact(unitPrice, quantity);
        }
    }

    // Không có @Component và cũng KHÔNG có @Autowired.
    // Có duy nhất MỘT constructor với một dependency bắt buộc.
    public static final class OrderService {
        private final PricePolicy policy;

        public OrderService(PricePolicy policy) {
            this.policy = Objects.requireNonNull(policy, "policy");
        }

        public String checkout(String sku, int unitPrice, int quantity) {
            Objects.requireNonNull(sku, "sku");
            if (sku.isBlank() || unitPrice < 0 || quantity <= 0) {
                throw new IllegalArgumentException("Invalid order input");
            }
            return sku + "=" + policy.calculate(unitPrice, quantity);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        public PricePolicy pricePolicy() {
            return new StandardPricePolicy();
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            // Đăng ký class service để Spring trực tiếp dùng constructor.
            context.register(AppConfig.class, OrderService.class);
            context.refresh();

            OrderService service = context.getBean(OrderService.class);

            System.out.println("ORDER_BEANS "
                    + context.getBeanNamesForType(OrderService.class).length);
            System.out.println("RESULT "
                    + service.checkout("BOOK", 30, 2));
            System.out.println("SAME_INSTANCE "
                    + (service == context.getBean(OrderService.class)));
        }
    }
}
~~~

### 5.3. Biên dịch và chạy với Maven

Tại thư mục `one-constructor-demo`:

~~~bash
mvn -version
mvn -q compile exec:java
~~~

Các dòng **stdout do chính `SingleConstructorDemo` in ra**, theo thứ tự:

~~~text
ORDER_BEANS 1
RESULT BOOK=60
SAME_INSTANCE true
~~~

Lệnh `mvn -version` sẽ in thông tin phiên bản. Maven/plugin có thể in log hoặc tải dependency khi chạy lần đầu, vì thế **ba dòng trên không bao gồm mọi log terminal**.

### 5.4. Giải thích thứ tự thực thi

1. Lệnh `new AnnotationConfigApplicationContext()` tạo context chưa được refresh.
2. `context.register(AppConfig.class, OrderService.class)` đăng ký **cả cấu hình** và **class service**. `OrderService` không cần `@Component` vì đã được đăng ký trực tiếp.
3. `context.refresh()` xử lý `@Configuration` và `@Bean pricePolicy()`, tạo bean `PricePolicy`; container cũng tạo các singleton non-lazy thích hợp.
4. Khi cần tạo `OrderService`, Spring nhận ra class có **duy nhất constructor `OrderService(PricePolicy)`**, dù không gắn `@Autowired`.
5. Spring giải quyết bean tương thích với `PricePolicy` (chính `StandardPricePolicy` được công bố qua `@Bean`) rồi gọi constructor với instance đó.
6. `getBean(OrderService.class)` trả service đã nhận dependency; nó **không tự gọi constructor mới mỗi lần lookup** với scope singleton mặc định.
7. Trong context này chỉ có một bean `OrderService` nên `getBeanNamesForType(...)` có độ dài 1.
8. `checkout("BOOK",30,2)` gọi `Math.multiplyExact(30,2)` trong policy, cho kết quả 60 và tạo chuỗi `BOOK=60`.
9. Lookup lại bean type `OrderService` trong cùng context trả cùng reference, cho `SAME_INSTANCE true`.
10. `try-with-resources` đóng context. Các callback hủy phù hợp được Spring xử lý cho managed beans.

Điểm nhấn: việc `OrderService` **không có annotation Spring** không ngăn constructor injection; **bean registration** mới là yếu tố để Spring biết tạo đối tượng.

## 6. Java 17 thuần: constructor injection vẫn dùng được khi không có Spring

Để chứng minh constructor injection không phải một cú pháp đặc biệt của Spring, hãy thử chương trình **độc lập** chỉ dùng JDK.

Tạo `src/main/java/demo/PlainConstructorDemo.java`:

~~~java
package demo;

import java.util.Objects;

public final class PlainConstructorDemo {
    private PlainConstructorDemo() {}

    @FunctionalInterface
    public interface GreetingPort {
        String hello(String name);
    }

    public static final class GreetingService {
        private final GreetingPort greeting;

        public GreetingService(GreetingPort greeting) {
            this.greeting = Objects.requireNonNull(greeting);
        }

        public String greet(String name) {
            return greeting.hello(name);
        }
    }

    public static void main(String[] args) {
        GreetingPort fake = name -> "Hello " + name;
        GreetingService service = new GreetingService(fake);
        System.out.println(service.greet("Lan"));
    }
}
~~~

Từ thư mục `one-constructor-demo`, biên dịch và chạy **không dùng Maven/Spring**:

~~~bash
javac --release 17 -encoding UTF-8 -d out src/main/java/demo/PlainConstructorDemo.java
java -cp out demo.PlainConstructorDemo
~~~

**Stdout chính xác:**

~~~text
Hello Lan
~~~

Trong ví dụ Java thuần, **`main` tự cung cấp argument** `fake` cho constructor. JVM không tự tìm implementation của `GreetingPort`. Khi dùng Spring, container thực hiện phần lựa chọn và cung cấp dependency trong một bean graph tương ứng.

### Khi tự gọi `new` thì `@Autowired` có tác dụng không?

Ví dụ minh họa:

~~~java
OrderService service = new OrderService(new StandardPricePolicy());
~~~

Đây là cách tạo trực tiếp và truyền dependency thủ công; nó hợp lệ nếu các class nằm trong phạm vi truy cập. Nhưng object được `new` bên ngoài container **không tự trở thành Spring-managed bean**, cũng không tự nhận bean post-processing, lifecycle hoặc proxy của Spring. Việc không cần `@Autowired` với constructor duy nhất là **quy tắc chọn constructor của Spring khi tạo bean**, không phải phép màu kích hoạt trên mọi `new`.

## 7. Khi nào cần ghi `@Autowired` rõ ràng?

### 7.1. Một constructor: annotation thường dư thừa

~~~java
@Component
final class ShippingService {
    private final ShippingRatePolicy rates;

    public ShippingService(ShippingRatePolicy rates) {
        this.rates = rates;
    }
}
~~~

Mã **minh họa**, cần khai báo `ShippingRatePolicy`, import `Component` và component scan. Khi `ShippingService` là Spring bean và có đúng một constructor, không cần thêm `@Autowired` để chọn constructor đó.

Viết:

~~~java
@Autowired
public ShippingService(ShippingRatePolicy rates) {
    this.rates = rates;
}
~~~

có thể được dùng để **biểu đạt ý định** hoặc giữ tính nhất quán trong dự án, nhưng **không thay đổi yêu cầu có dependency** trong tình huống một constructor duy nhất. Đừng gắn annotation chỉ vì nghĩ Java sẽ không chạy được nếu thiếu nó.

### 7.2. Nhiều constructor: phải làm rõ chiến lược lựa chọn

~~~java
@Component
class MultiConstructorService {
    private final ReportRepository repository;

    public MultiConstructorService() {
        this.repository = null;
    }

    @Autowired
    public MultiConstructorService(ReportRepository repository) {
        this.repository = repository;
    }
}
~~~

Đây là **đoạn minh họa**, không phải chương trình độc lập. Với nhiều constructor, quy tắc “chỉ một constructor thì được dùng ngầm” **không còn áp dụng**. Đánh dấu constructor mong muốn bằng `@Autowired` giúp Spring biết đó là constructor cần dùng; nếu dependency cần thiết thiếu, tạo bean có thể thất bại.

Không nên thêm một constructor không tham số chỉ để “cho Spring dễ tạo”, nhất là nếu nó làm object thiếu field bắt buộc. Một class nghiệp vụ nên cố gắng có **một constructor duy nhất với tất cả required dependencies** khi thiết kế cho phép.

Nếu class có nhiều constructor mà không có annotation, Spring còn có những quy tắc và khả năng suy luận constructor theo bean definition/argument types/version. **Không được suy ra rằng Spring luôn chọn constructor dài nhất, constructor đầu tiên hoặc constructor “có nhiều bean thỏa” một cách vô điều kiện.**

### 7.3. `@Autowired(required = false)` trên constructor không giống optional field

Một số developer thêm `required=false` để biến constructor bắt buộc thành tùy chọn. Đây là cách hiểu dễ sai. Với nhiều constructor, Spring có thể xem các constructor được annotation non-required là candidates và chọn constructor phù hợp theo quy tắc resolution, nhưng **không có nghĩa Spring truyền `null` vào mọi tham số bị thiếu**, hay biến một class bất kỳ thành hợp lệ không dependency.

Nếu collaborator là optional, hãy cân nhắc **`Optional<T>`**, `@Nullable` phù hợp, `ObjectProvider<T>`, hoặc setter injection có default/hành vi an toàn. Đừng làm loãng constructor mandatory chỉ để che lỗi thiếu bean.

## 8. Đúng một constructor mà vẫn có thể lỗi

Một constructor được Spring chọn tự động **không bảo đảm tạo bean thành công**. Xét những trường hợp:

### Thiếu bean dependency

Nếu `OrderService` cần `PricePolicy` mà `AppConfig` không đăng ký implementation tương ứng, Spring không thể resolve parameter. Trong ví dụ singleton eager, lỗi có thể xuất hiện **ngay lúc `refresh()`**, thường qua `UnsatisfiedDependencyException` với nguyên nhân thiếu bean.

~~~java
// Minh họa thử lỗi: không thêm AppConfig vào context.
context.register(OrderService.class);
context.refresh(); // Có thể fail do thiếu PricePolicy.
~~~

Đoạn này chỉ để minh họa tình huống, không nên chèn vào chương trình chạy chính nếu muốn giữ stdout mục 5.

### Có nhiều bean cùng interface

Nếu có hai bean `PricePolicy` mà injection point cần đúng một và không có quy tắc phân giải, có thể gặp `NoUniqueBeanDefinitionException`. Giải pháp thường là `@Primary`, `@Qualifier` hoặc chọn bean name/type rõ ràng ở nơi đăng ký.

Ví dụ:

~~~java
public OrderService(@Qualifier("standardPolicy") PricePolicy policy) {
    this.policy = policy;
}
~~~

Đây là **đoạn minh họa**; cần import `org.springframework.beans.factory.annotation.Qualifier` và khai báo bean name `standardPolicy`. Quy tắc một constructor không cần `@Autowired` **không cấm** dùng `@Qualifier` cho parameter.

### Constructor tự ném exception

Nếu `Objects.requireNonNull(policy)`, logic validate hoặc constructor của dependency ném lỗi, việc tạo bean vẫn thất bại. `@Autowired` không phải exception handler và không tự phục hồi object graph.

### Dependency vòng tròn qua constructor

Một sơ đồ như `A(B)` và `B(A)` là constructor cycle không thể giải quyết bình thường khi cả hai cần nhau để tồn tại. Việc bỏ `@Autowired` không tự giải quyết được chu kỳ; cần thiết kế lại trách nhiệm hoặc dùng cơ chế trì hoãn có chủ đích khi thật sự phù hợp.

### Class chưa đăng ký

Ngay cả khi constructor hợp lệ và bean `PricePolicy` có sẵn, nếu `OrderService` không được khai báo `@Bean`, không được component-scan và không được `register(...)`, `context.getBean(OrderService.class)` vẫn có thể thất bại với `NoSuchBeanDefinitionException`.

**Kết luận của mục này:** annotation không phải chìa khóa sửa mọi lỗi autowiring. Cần kiểm tra **registration**, **constructor selection**, **dependency resolution** và **lifecycle** theo thứ tự.

## 9. `@Bean` cũng có constructor injection, nhưng ở một vị trí khác

So sánh hai kiểu wiring.

### Cách A: Spring khởi tạo class bean trực tiếp

~~~java
context.register(OrderService.class);
context.refresh();
~~~

Khi `OrderService` có duy nhất constructor `OrderService(PricePolicy)` và dependency đã tồn tại, container trực tiếp gọi constructor. Đây là tình huống tiêu biểu cho quy tắc một constructor không cần `@Autowired`.

### Cách B: `@Bean` factory method tự new service

~~~java
@Bean
OrderService orderService(PricePolicy policy) {
    return new OrderService(policy);
}
~~~

Spring giải quyết `PricePolicy` vào **tham số method `orderService`**. Mã Java trong factory **tự** gọi constructor. Với cách này, class `OrderService` có thể được thiết kế không import Spring và không cần component scan.

**Đừng nhầm**: cả A và B đều có thể thực hiện Dependency Injection hiệu quả, nhưng cách Spring quyết định gọi constructor có khác nhau.

Với `@Bean` method, Spring thường không cần `@Autowired` để tự resolve factory method parameters; đó là hành vi của bean factory infrastructure.

## 10. Unit test không cần Spring: lợi ích của một constructor rõ ràng

Với business class đơn giản:

~~~java
public interface PricePolicy {
    int calculate(int unitPrice, int quantity);
}
~~~

và `OrderService(PricePolicy policy)`, test có thể tự truyền fake:

~~~java
PricePolicy fake = (unitPrice, quantity) -> 123;
OrderService service = new OrderService(fake);

String actual = service.checkout("BOOK", 30, 2);

if (!"BOOK=123".equals(actual)) {
    throw new AssertionError("Expected BOOK=123 but got " + actual);
}
~~~

Hai khối là **trích đoạn minh họa trong cùng context với các class mục 5**, không phải file chạy độc lập. Fake không cần database, HTTP client hoặc Spring context. Điều cần assert là **kết quả và tương tác đúng hợp đồng**, không chỉ “không ném exception”.

### Khi muốn kiểm thử Spring wiring

Một integration test có thể khởi động `AnnotationConfigApplicationContext` và kiểm tra lấy được `OrderService`. Việc chạy context kiểm tra:

- Bean class và configuration đã đăng ký đúng.
- `PricePolicy` có ứng viên được lựa chọn duy nhất.
- Constructor duy nhất được resolve thành công.
- Bean đủ điều kiện để dùng.
- Scope và lifecycle theo cấu hình.

Không cần khởi động container trong **mọi unit test** của `OrderService`; cách đó làm test gắn thêm với hạ tầng mà logic nghiệp vụ không cần.

## 11. Bảng đối chiếu nhanh

| Tình huống | Cần `@Autowired` trên constructor không? | Điểm quan trọng |
| --- | --- | --- |
| Spring tạo bean class có đúng một constructor | **Không cần** | Constructor được Spring chọn ngầm |
| Constructor duy nhất có 2–4 tham số | **Không cần** | Từng tham số vẫn cần resolve được |
| Class có `@Component` và được scan, một constructor | **Không cần** | Scan phải được bật và trúng package |
| Class được `context.register(MyClass.class)`, một constructor | **Không cần** | Container biết bean definition |
| Object tự `new MyClass(dep)` ngoài Spring | Không liên quan tới Spring autowiring | Người gọi tự truyền dep |
| `@Bean MyClass myClass(Dep dep)` tự gọi `new` | Không cần trên constructor để factory hoạt động | Factory method được Spring inject |
| Class có nhiều constructor cần chỉ định một constructor | **Thường nên dùng** | Tránh ambiguity và chọn sai constructor |
| Có một constructor nhưng thiếu bean dependency | Viết annotation không chữa được | Cần bean definition/candidate hợp lệ |
| Có một constructor nhưng nhiều bean cùng type | Viết annotation không chữa được | Cần qualifier/primary/quy tắc chọn |
| Cần injection vào setter/field thay vì constructor | Annotation hoặc metadata injection phù hợp | Quy tắc single-constructor chỉ nói về constructor |

## 12. Lỗi phổ biến khi học `@Autowired`

| Hiểu nhầm | Điều đúng |
| --- | --- |
| “Constructor nào cũng phải gắn `@Autowired`.” | Không; Spring 4.3+ tự dùng constructor duy nhất của bean. |
| “Không có `@Autowired` thì Java không biết truyền tham số.” | Java luôn đòi người tạo object truyền argument; Spring chỉ cung cấp tự động khi nó tạo bean. |
| “Có một constructor thì Spring tự scan class.” | Không; cần bean registration/component scan/`@Bean` thích hợp. |
| “Chỉ một constructor nghĩa là constructor không tham số.” | Không; constructor duy nhất có thể có nhiều tham số. |
| “Bỏ `@Autowired` sẽ bỏ yêu cầu bean dependency.” | Không; tham số constructor vẫn phải được giải quyết. |
| “`@Autowired` xử lý được cả thiếu bean và nhiều bean mơ hồ.” | Không; phải xử lý candidate và cấu hình đúng. |
| “Class đã có `@Service` nên chắc chắn được scan.” | Annotation không đủ nếu scanning không bao phủ package/class. |
| “Một constructor thì constructor đó chạy trước khi dependency được tạo.” | Spring thường phải giải quyết dependency để gọi constructor; dependency được cung cấp vào thời điểm invocation. |
| “Không cần `@Autowired` cũng không cần DI.” | Vẫn là DI; chỉ bỏ annotation thừa. |
| “Constructor injection đồng nghĩa tất cả state immutable.” | `final` reference và tính immutable/thread safety là những vấn đề riêng. |
| “`new` ngoài container sẽ được tiêm tự động.” | Không; phải truyền argument thủ công hoặc dùng managed bean. |
| “`@Bean` method argument được inject nhờ annotation trên constructor sản phẩm.” | Factory method arguments được container resolve theo `@Bean` infrastructure. |
| “Nhiều constructor Spring luôn chọn constructor dài nhất.” | Không có quy tắc đơn giản đúng cho mọi cấu hình. |
| “Thêm constructor mặc định là cách sửa dependency lỗi.” | Có thể khiến object tồn tại ở trạng thái thiếu required collaborators. |
| “Đánh dấu `@Autowired(required=false)` biến mọi dependency bắt buộc thành optional.” | Constructor candidate resolution phức tạp hơn; không đồng nghĩa inject null an toàn. |

## 13. Bài tập và đáp án

**Bài 1.** Class `ReportService` có duy nhất constructor nhận `ReportRepository`, được Spring component-scan và có bean repository. Có cần `@Autowired` không?

**Đáp án:** Không. Với Spring 4.3+ Spring có thể dùng constructor duy nhất và tự resolve repository.

**Bài 2.** Class có duy nhất constructor ba tham số. Quy tắc này có áp dụng không?

**Đáp án:** Có. Số **constructor** là 1; số **tham số** có thể là 3. Mỗi tham số vẫn phải có dependency phù hợp.

**Bài 3.** Class có một constructor, nhưng không đăng ký bean và cũng không bật scan. `getBean(Class)` có thể tìm được chỉ nhờ constructor không?

**Đáp án:** Không. Spring cần biết bean definition trước khi tạo/lookup.

**Bài 4.** Service không có `@Autowired`, nhưng một constructor bắt buộc nhận `PricePolicy`, context lại không có bean `PricePolicy`. Thêm annotation có sửa được không?

**Đáp án:** Không. Cần cung cấp bean implementation hoặc sửa cấu hình dependency; thiếu bean không phải do quên annotation trong tình huống một constructor.

**Bài 5.** Vì sao chương trình in `SAME_INSTANCE true`?

**Đáp án:** `OrderService` là bean singleton mặc định trong cùng context; hai lần lookup trả một instance. Điều này **không có nghĩa** chỉ tồn tại một instance toàn JVM.

**Bài 6.** Trong ví dụ `@Bean OrderService service(PricePolicy p)` trả `new OrderService(p)`, ai cung cấp `p` và ai gọi constructor?

**Đáp án:** Spring giải quyết `p` khi gọi factory method; **body `@Bean`** gọi constructor bằng `new`.

**Bài 7.** Nếu thêm một constructor không tham số vào `OrderService`, có thể bỏ qua mọi chú ý tới constructor selection không?

**Đáp án:** Không. Bây giờ có nhiều constructor, nên quy tắc constructor duy nhất không còn áp dụng. Cần chỉ rõ chiến lược, thường đánh dấu constructor cần dùng bằng `@Autowired`.

**Bài 8.** Khi chỉ có một constructor, có được dùng `@Qualifier` trên tham số mà không có `@Autowired` trên constructor không?

**Đáp án:** Có, khi muốn xác định rõ bean candidate cho tham số trong constructor được Spring chọn. Phải khai báo qualifier/bean names khớp cấu hình thực.

**Bài 9.** Tại sao `@Autowired` trên field thường không được khuyến khích bằng constructor injection cho dependency bắt buộc?

**Đáp án:** Field injection làm dependency kém hiển thị, khó tạo unit test Java thuần hơn và không thuận lợi cho `final` fields. Constructor biểu đạt required dependencies từ lúc tạo object.

**Bài 10.** Nếu dùng một constructor với hai dependency nhưng class quá nhiều trách nhiệm, bỏ `@Autowired` có làm kiến trúc tốt hơn không?

**Đáp án:** Không. Bỏ annotation chỉ làm ngắn mã. Chất lượng thiết kế phụ thuộc cách chia trách nhiệm, hợp đồng và vòng đời collaborator.

## 14. Checklist để áp dụng vào dự án

- [ ] Xác định **class thực sự là Spring-managed bean**: component scan, register hoặc `@Bean`.
- [ ] Đếm **số constructor** của bean class, không đếm số tham số.
- [ ] Nếu chỉ có **một constructor**, với Spring Framework 4.3+ thông thường không cần `@Autowired`.
- [ ] Đưa required dependencies vào constructor và kiểm tra argument khi cần.
- [ ] Đảm bảo mỗi parameter có bean candidate phù hợp trong context.
- [ ] Nếu nhiều bean cùng type, dùng qualifier/primary hoặc chính sách chọn rõ.
- [ ] Không thêm constructor không tham số chỉ để che lỗi thiếu dependency.
- [ ] Nếu nhiều constructor, thiết kế strategy selection rõ ràng thay vì đoán.
- [ ] Không cho rằng `@Component` tự được scan khi package không nằm trong cấu hình scan.
- [ ] Với `@Bean` factory method, nhớ Spring inject các factory parameters.
- [ ] Phân biệt object tạo bằng `new` với instance do Spring quản lý.
- [ ] Unit test service bằng fake Java thuần; integration test riêng wiring Spring.
- [ ] Rà scope/lifecycle và tác động của proxy khi mở rộng AOP/transaction.
- [ ] Không coi constructor injection là bảo đảm thread safety hay transactional consistency.
- [ ] Kiểm tra stdout ứng dụng tách biệt log Maven và xác nhận Java 17 compatibility.

### Tài liệu Spring tham khảo

- [Using `@Autowired` — constructor injection](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html)
- [Constructor-based Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)
- [Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java/basic-concepts.html)

**Kết luận:** Nếu Spring đã biết tạo một bean và bean class có **đúng một constructor**, constructor đó thường được Spring sử dụng dù không gắn `@Autowired`. Điều này làm constructor injection ngắn gọn, rõ required dependencies và dễ test. Nhưng nó **không thay thế** bean registration, không tự chọn ứng viên khi thiếu/nhiều bean và không làm Java `new` ngoài container tự động nhận dependency.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="autowired-003">Quy tắc chọn khi có nhiều hàm khởi tạo</a>

<details>
<summary>Xem chi tiết</summary>

# Quy tắc chọn khi có nhiều hàm khởi tạo — @Autowired trong Spring Framework

## 1. Vấn đề cần hiểu

Một lớp Java có thể **overload constructor**: khai báo các hàm khởi tạo cùng tên lớp nhưng khác danh sách tham số. Khi tự gọi `new`, **trình biên dịch Java** chọn overload dựa trên đối số bạn truyền. Khi **Spring tạo bean từ lớp đó**, container phải quyết định **constructor nào được dùng** và có thể phân giải những dependency nào để truyền vào.

Hai cơ chế khác nhau:

- **Java overload resolution:** ví dụ `new ReportService(tracer)` gọi constructor nhận `Tracer`, do biểu thức Java đã chỉ rõ đối số.
- **Spring constructor resolution:** ví dụ `context.getBean(ReportService.class)` (khi bean đã được đăng ký) có thể kích hoạt Spring chọn constructor dựa trên `@Autowired`, những ứng viên phù hợp, constructor mặc định và metadata của bean definition.

**Không có quy tắc “constructor dài nhất luôn thắng”.** Chỉ trong tình huống **nhiều constructor được đánh dấu `@Autowired(required=false)`**, Spring xét các constructor ứng viên và chọn constructor **có nhiều dependency nhất mà container có thể giải quyết**, theo chính sách autowiring. Nếu ứng viên không phù hợp và có default/primary constructor thích hợp, Spring có thể dùng nó. Quy tắc đó không đồng nghĩa lấy bất kỳ constructor dài nhất nào trong class.

Sau bài này, bạn sẽ:

1. Phân biệt constructor overload của Java và cơ chế Spring chọn constructor cho bean.
2. Giải thích trường hợp **chỉ một constructor không cần `@Autowired`**.
3. Hiểu ý nghĩa mặc định `@Autowired(required=true)`: chỉ **một** constructor trong cùng lớp được đánh dấu required.
4. Hiểu nhiều constructor cùng `@Autowired(required=false)`, **greedy satisfiable candidate** và fallback về constructor mặc định.
5. Biết điều gì xảy ra khi lớp có nhiều constructor nhưng **không đánh dấu `@Autowired`**.
6. Phân biệt **chọn constructor** với **chọn bean candidate** cho từng tham số (`@Primary`, `@Qualifier`, ambiguity).
7. Đối chiếu bằng project **Java 17+ / Spring Framework Core Container thuần**, Maven và các lệnh `javac`/`java` có stdout xác định.
8. Nhận biết lỗi và thiết kế constructor injection dễ đọc, dễ test, không lạm dụng nhiều overload.

**Phạm vi:** Chỉ tìm hiểu quy tắc chọn **constructor lúc Spring khởi tạo bean**, không cần Spring Boot, JPA, Lombok, Kotlin hoặc XML. Các trường hợp Java/Spring đặc biệt được ghi chú để tránh áp dụng sai một quy tắc vào mọi dạng bean registration.

## 2. Các tình huống trong một bảng

| Constructor của một class | Cách Spring thường xử lý |
| --- | --- |
| Chỉ có **một constructor** | Dùng constructor đó, kể cả không gắn `@Autowired`; dependency phải resolve được |
| Nhiều constructors, **một** gắn `@Autowired` mặc định `required=true` | Dùng constructor được đánh dấu; thiếu dependency bắt buộc gây lỗi, không chuyển tùy tiện sang constructor khác |
| Nhiều constructors cùng được gắn `@Autowired(required=false)` | Xét các ứng viên; chọn constructor với **nhiều dependency giải quyết được nhất** |
| Tất cả annotated candidates `required=false` đều không giải quyết được | Fallback về default/primary constructor **nếu có** |
| Nhiều constructors **không constructor nào gắn `@Autowired`**, có no-arg mặc định | Mặc định sử dụng constructor không tham số; không tự greedy chọn overload dài nhất |
| Nhiều constructors, không annotation, không constructor mặc định/primary thích hợp | Không nên mong Spring tự suy đoán; cần làm rõ constructor dùng để inject hoặc metadata tạo bean |
| Nhiều constructors với **hai annotation `@Autowired(required=true)`** | Cấu hình không hợp lệ; không được có hai required annotated constructors |
| Có một required annotated constructor **và** một annotated optional constructor | Không hợp lệ theo quy tắc annotation: required constructor phải là constructor annotated duy nhất |

> Trong bảng, *primary constructor* mang nghĩa một constructor chính được Spring nhận diện theo quy tắc/ngôn ngữ/metadata phù hợp; **không** nhầm với `@Primary` dùng để chọn **bean** giữa nhiều ứng viên.

Quy tắc constructor của Spring được mô tả trong JavaDoc `org.springframework.beans.factory.annotation.Autowired` và `AutowiredAnnotationBeanPostProcessor`. Chúng không yêu cầu mọi overload đều public; tuy nhiên trong bài dùng public constructors để mã dễ hiểu và không phụ thuộc vấn đề khả năng truy cập của module.

## 3. Trường hợp đơn giản: một constructor, không cần @Autowired

~~~java
@Component
final class SingleConstructorService {
    private final Tracer tracer;

    public SingleConstructorService(Tracer tracer) {
        this.tracer = java.util.Objects.requireNonNull(tracer);
    }
}
~~~

Đây là **snippet minh họa**: cần khai báo `Tracer` và import `@Component`. Trong Spring 4.3+ (bao gồm Spring Framework 6.2), nếu một bean class **chỉ có một constructor**, Spring có thể sử dụng constructor đó **mà không bắt buộc viết `@Autowired`**.

Lưu ý:

- Spring chỉ tự tạo/đưa dependency vào **bean mà nó quản lý**. Nếu `SingleConstructorService` không được đăng ký bằng component scan, `context.register(...)` hay bean definition phù hợp, annotation ở source không tự sinh instance.
- Không có bean `Tracer` phù hợp thì Spring **không tự truyền null** để tạo service hợp lệ; việc tạo bean thường thất bại.
- Java vẫn cho phép `new SingleConstructorService(tracer)` không có Spring: constructor injection vốn là kỹ thuật Java thuần.
- Nếu chỉ có một constructor và bạn thêm `@Autowired` thì hợp lệ, nhưng thường thừa cú pháp.
- Với một constructor có nhiều tham số, Spring sẽ cố giải quyết những dependency cần thiết. Cách hiểu “một constructor” **không làm mọi tham số tùy chọn**.

### @Autowired không phải dấu hiệu duy nhất của bean registration

Một class có constructor nhưng không phải bean sẽ không tự tham gia dependency injection. Ví dụ này:

~~~java
SingleConstructorService local =
        new SingleConstructorService(new ConsoleTracer());
~~~

là **manual wiring bằng Java**, không phải Spring DI. Nếu `ConsoleTracer` là một object Java bình thường và bạn không đăng ký nó, nó không biến thành managed Spring bean chỉ vì được truyền vào một constructor.

## 4. Nhiều constructor: một constructor @Autowired(required=true)

Mặc định của `@Autowired` là `required=true`. Viết:

~~~java
@Component
final class StrictService {
    private final Tracer tracer;

    public StrictService() {
        this.tracer = null;
    }

    @Autowired
    public StrictService(Tracer tracer) {
        this.tracer = java.util.Objects.requireNonNull(tracer);
    }
}
~~~

Đây là **snippet minh họa về quy tắc chọn**, không phải cấu trúc khuyến khích cho business service vì default constructor để lại trạng thái null. Trong bối cảnh Spring tạo bean:

- Constructor `StrictService(Tracer)` đã được đánh dấu **required**, nên Spring chọn nó làm constructor injection.
- Nếu context không có một `Tracer` thích hợp, việc tạo bean **thất bại**; Spring không tự chuyển sang `StrictService()` chỉ để tránh lỗi.
- Nếu context có đúng một `Tracer` phù hợp, Spring truyền nó vào constructor annotated.
- Việc Java vẫn có no-arg constructor **không biến required dependency thành optional**.

Trong thiết kế thực tế, nếu `Tracer` bắt buộc để service hoạt động, thường **chỉ giữ constructor nhận Tracer**; như vậy class Java thuần cũng không cho phép tạo instance không hợp lệ. Snippet ở đây cố tình có cả hai constructor để quan sát Spring chọn.

**Không được** gắn `@Autowired(required=true)` ở hai constructor trong cùng lớp. Đây là cấu hình mâu thuẫn; Spring không dùng thứ tự source code để “chọn đại” một constructor trong hai cái.

## 5. Nhiều constructor @Autowired(required=false): chọn ứng viên phù hợp

Mô hình:

~~~java
@Component
final class FlexibleService {
    private final String selection;

    public FlexibleService() {
        this.selection = "default";
    }

    @Autowired(required = false)
    public FlexibleService(Tracer tracer) {
        this.selection = "tracer";
    }

    @Autowired(required = false)
    public FlexibleService(Tracer tracer, Formatter formatter) {
        this.selection = "tracer+formatter";
    }

    String selection() {
        return selection;
    }
}
~~~

Đoạn này là **snippet**; cần hai kiểu `Tracer` và `Formatter` được đăng ký phù hợp. Spring xét những constructor annotated `required=false` như **các lựa chọn**, chứ không áp dụng semantics “tham số nullable” cho mỗi constructor.

| Bean collaborators hiện có | Constructor được chọn trong ví dụ | Giá trị `selection` |
| --- | --- | --- |
| Có một `Tracer` và một `Formatter` | `FlexibleService(Tracer, Formatter)` | `tracer+formatter` |
| Có `Tracer`, không có `Formatter` | `FlexibleService(Tracer)` | `tracer` |
| Không có `Tracer`, cũng không có `Formatter` | `FlexibleService()` | `default` |

Đây là trường hợp phổ biến của quy tắc **“constructor thỏa nhiều dependency nhất”**, nhưng có các điều kiện:

- Constructor dài hơn phải **satisfiable**: các tham số cần thiết phải được container giải quyết tương thích.
- Có nhiều constructor `@Autowired(required=false)`; chúng đều là **ứng viên**. Nếu nhiều candidates ngang mức ưu tiên/khả năng giải quyết, đừng thiết kế dựa vào một quy tắc “chọn ngẫu nhiên”; làm rõ bằng một constructor duy nhất hoặc annotation/metadata đúng.
- Một no-arg constructor đóng vai trò **fallback** nếu không ứng viên annotated nào có thể được giải quyết.
- `@Autowired(required=false)` ở constructor **không có nghĩa Spring sẽ truyền null cho bất kỳ tham số không có bean**, và cũng không có nghĩa Spring luôn tạo service theo constructor dài nhất.
- Spring có thể gặp lỗi khác với “không có candidate”, ví dụ một tham số có **nhiều beans không thể chọn duy nhất** hoặc bean dependency khởi tạo bị lỗi. Đừng coi mọi failure là lý do chắc chắn fallback.
- Nếu tình huống “không có Tracer” làm service vô nghĩa, thiết kế fallback `FlexibleService()` sẽ che cấu hình thiếu. Khi collaborator thực sự bắt buộc, tốt hơn chỉ giữ một constructor required.

### Không nhầm required=false ở constructor với optional field/method

Với setter hoặc field:

~~~java
@Autowired(required = false)
public void setMetrics(MetricsClient metrics) {
    this.metrics = metrics;
}
~~~

Spring có thể **bỏ qua injection point** nếu dependency không có (theo quy tắc phù hợp). Với **constructor alternatives**, Spring đang lựa chọn **một trong nhiều constructor** để tạo object; không có chuyện chạy tất cả constructors rồi “bỏ qua” tham số không sẵn có.

Nếu một dependency thật sự optional **ngay trong một constructor duy nhất**, có thể cân nhắc `Optional<T>`, `@Nullable` hoặc `ObjectProvider<T>` khi semantics phù hợp; điều đó là vấn đề **tham số/điểm injection**, không phải mặc định ưu tiên nhiều constructors.

## 6. Không gắn @Autowired khi có nhiều constructors

Xem ví dụ:

~~~java
@Component
final class UnannotatedService {
    private final String selected;

    public UnannotatedService() {
        this.selected = "default";
    }

    public UnannotatedService(Tracer tracer) {
        this.selected = "tracer";
    }
}
~~~

Đây là một class có **hai constructors và không constructor nào được gắn `@Autowired`**. Khi Spring được yêu cầu khởi tạo bean này bằng cơ chế constructor injection thông thường trong demo, nó dùng constructor mặc định **không tham số**, kể cả context đã có bean `Tracer`. Lý do: Spring không tự áp dụng quy tắc “greediest satisfiable” dành cho **nhiều annotated optional constructors** lên mọi constructor không annotation.

Nếu lớp có **nhiều constructors, không có no-arg và không có constructor chính mặc định/metadata phù hợp**, lựa chọn có thể không rõ và dẫn tới lỗi tạo bean. Đừng đoán rằng Spring chọn “cái đầu” hoặc “cái có ít tham số nhất”. Hãy:

1. Chọn **một constructor** có ý nghĩa và gắn `@Autowired` khi có nhiều overload.
2. Hoặc xóa overload không cần thiết để chỉ còn một constructor.
3. Hoặc dùng một `@Bean` factory method có logic và đối số rõ ràng.
4. Nếu có **thực sự** nhiều cấu hình khởi tạo hợp lệ, mới cân nhắc nhiều `@Autowired(required=false)` alternatives.

**Khác với trường hợp một constructor:** chỉ một constructor thì không cần annotation, nhưng khi bạn **thêm overload**, semantics chọn constructor có thể thay đổi. Đây là lý do refactor tưởng vô hại như thêm no-arg constructor cho framework khác có thể khiến Spring không còn inject dependency theo cách mong muốn.

## 7. Chọn constructor khác với chọn bean cho tham số

Spring trải qua hai câu hỏi tách biệt:

1. **Constructor nào được xem là phù hợp để gọi?**
2. **Mỗi tham số constructor đó nhận bean instance nào?**

Giả sử một constructor cần `Tracer`, nhưng context có:

~~~java
@Bean("fileTracer")
Tracer fileTracer() { return new FileTracer(); }

@Bean("consoleTracer")
Tracer consoleTracer() { return new ConsoleTracer(); }
~~~

Đây là **snippet chưa độc lập**, thiếu các kiểu và định nghĩa class. Nó minh họa hai bean cùng type. Spring phải phân giải một `Tracer` đơn trị từ nhiều candidate bằng các quy tắc phù hợp, có thể cần:

- `@Qualifier("consoleTracer")` trên tham số constructor cần chính bean đó.
- `@Primary` trên bean được dự kiến làm lựa chọn mặc định.
- Làm rõ bean name/type và điều kiện đăng ký để tránh tạo thừa candidate.
- Dùng `List<Tracer>`/`Map<String, Tracer>` hoặc `ObjectProvider<Tracer>` nếu hợp đồng thực sự muốn nhiều beans.

**Lưu ý:** `@Primary` chọn **bean candidate**, không phải chọn “constructor chính” theo overload. Ngược lại `@Autowired` trên constructor giúp Spring xác định constructor được phép dùng, nhưng **không giải quyết thay mọi ambiguity giữa các beans của cùng kiểu**.

Ví dụ constructor rõ ràng:

~~~java
@Autowired
public DeliveryService(
        @Qualifier("consoleTracer") Tracer tracer) {
    this.tracer = java.util.Objects.requireNonNull(tracer);
}
~~~

Snippet này cần khai báo `DeliveryService` đầy đủ và đúng import. Trong chương trình demo chính ta cố ý chỉ có **tối đa một bean trên mỗi type** để kết quả chọn constructor không phụ thuộc qualifier, @Primary hay ambiguity.

### Bean lỗi khi đang được tạo cũng là vấn đề khác

Nếu container biết một `Tracer` nhưng factory method tạo Tracer lại ném exception, đó không phải đơn thuần là “không có bean Tracer”. Spring có thể ném `BeanCreationException` và các exception wrapper khác. Không mặc nhiên dùng constructor fallback để giấu lỗi khởi tạo thực sự của dependency.

## 8. Ví dụ Java 17+ hoàn chỉnh: sáu trường hợp chọn constructor

Demo dùng **Spring Framework 6.2.19** từ Maven và **JDK 17+**. Version được ghim để tái lập, không tuyên bố mới nhất. Chương trình mở các Spring contexts **độc lập** để chứng minh ba lựa chọn của class flexible, một constructor duy nhất không annotation, hai constructors không annotation và required constructor.

### 8.1. Cấu trúc

~~~text
autowired-constructor-selection/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── ConstructorSelectionDemo.java
~~~

### 8.2. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>autowired-constructor-selection</artifactId>
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
                    <mainClass>demo.ConstructorSelectionDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Module `spring-context` kéo theo các dependency của Spring Core Container. Maven cần kết nối repository nếu artifact chưa nằm trong cache. Đừng chỉ copy `spring-context.jar` rồi hy vọng có đủ classpath để `javac` và `java` chạy.

### 8.3. File src/main/java/demo/ConstructorSelectionDemo.java

~~~java
package demo;

import java.util.Objects;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

public final class ConstructorSelectionDemo {
    private ConstructorSelectionDemo() {}

    public interface Tracer {}
    public interface Formatter {}

    public static final class ConsoleTracer implements Tracer {
        public ConsoleTracer() {}
    }

    public static final class PlainFormatter implements Formatter {
        public PlainFormatter() {}
    }

    public interface Choice {
        String selected();
    }

    // Nhiều candidates: cả hai overload đều required=false.
    @Component
    public static final class FlexibleClient implements Choice {
        private final String selection;

        public FlexibleClient() {
            this.selection = "default";
        }

        @Autowired(required = false)
        public FlexibleClient(Tracer tracer) {
            Objects.requireNonNull(tracer);
            this.selection = "tracer";
        }

        @Autowired(required = false)
        public FlexibleClient(Tracer tracer, Formatter formatter) {
            Objects.requireNonNull(tracer);
            Objects.requireNonNull(formatter);
            this.selection = "tracer+formatter";
        }

        @Override
        public String selected() {
            return selection;
        }
    }

    // Một constructor duy nhất: không cần @Autowired.
    @Component
    public static final class SingleClient implements Choice {
        private final Tracer tracer;

        public SingleClient(Tracer tracer) {
            this.tracer = Objects.requireNonNull(tracer);
        }

        @Override
        public String selected() {
            return tracer == null ? "UNEXPECTED" : "tracer";
        }
    }

    // Hai constructors không annotation: default được dùng.
    @Component
    public static final class UnannotatedClient implements Choice {
        private final String selection;

        public UnannotatedClient() {
            this.selection = "default";
        }

        public UnannotatedClient(Tracer tracer) {
            Objects.requireNonNull(tracer);
            this.selection = "tracer";
        }

        @Override
        public String selected() {
            return selection;
        }
    }

    // Một constructor annotated mặc định required=true.
    @Component
    public static final class RequiredClient implements Choice {
        private final String selection;

        public RequiredClient() {
            this.selection = "default";
        }

        @Autowired
        public RequiredClient(Tracer tracer) {
            Objects.requireNonNull(tracer);
            this.selection = "required-tracer";
        }

        @Override
        public String selected() {
            return selection;
        }
    }

    private static String choose(
            Class<? extends Choice> beanClass,
            boolean hasTracer, boolean hasFormatter) {

        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {

            if (hasTracer) {
                context.registerBean(Tracer.class, ConsoleTracer::new);
            }
            if (hasFormatter) {
                context.registerBean(Formatter.class, PlainFormatter::new);
            }

            context.register(beanClass);
            context.refresh();
            return context.getBean(beanClass).selected();
        }
    }

    public static void main(String[] args) {
        System.out.println("flexibleBoth=" +
                choose(FlexibleClient.class, true, true));
        System.out.println("flexibleTracerOnly=" +
                choose(FlexibleClient.class, true, false));
        System.out.println("flexibleNone=" +
                choose(FlexibleClient.class, false, false));

        System.out.println("singleNoAnnotation=" +
                choose(SingleClient.class, true, false));

        System.out.println("multipleNoAnnotation=" +
                choose(UnannotatedClient.class, true, false));

        System.out.println("requiredPresent=" +
                choose(RequiredClient.class, true, false));

        try {
            choose(RequiredClient.class, false, false);
            System.out.println("requiredMissing=UNEXPECTED");
        } catch (BeansException expected) {
            System.out.println("requiredMissing=REJECTED");
        }
    }
}
~~~

**Vì sao ví dụ rõ và không cần component scanning:**

- Chỉ có **một public top-level class** `ConstructorSelectionDemo`; các class phụ là `public static` nested classes. Tên file/package trùng đường dẫn.
- `AnnotationConfigApplicationContext()` tạo context trống/chưa refresh, rồi `registerBean(Tracer.class, ...)` và `registerBean(Formatter.class, ...)` có chủ đích trước `refresh()`.
- `context.register(beanClass)` trực tiếp đăng ký đúng class để Spring tạo. Mỗi lần `choose(...)` dùng **một context mới** nên không lẫn bean definitions/candidates giữa các thử nghiệm.
- `ConsoleTracer` và `PlainFormatter` có constructor public không tham số; method references `ConsoleTracer::new` và `PlainFormatter::new` là `Supplier` hợp lệ cho bean registration.
- `FlexibleClient` có hai overload annotated `required=false` và constructor mặc định. Khi hai dependencies có mặt, constructor nhận hai tham số được chọn; chỉ Tracer thì nhận một; thiếu cả hai thì fallback no-arg.
- `SingleClient` có một constructor không annotation và một `Tracer` được đăng ký; Spring vẫn inject.
- `UnannotatedClient` có hai constructors không annotation; với no-arg available, Spring chọn no-arg chứ không greedy chọn Tracer.
- `RequiredClient` có một constructor annotated mặc định required=true. Khi Tracer thiếu, Spring không dùng no-arg làm fallback mà fail; demo bắt `BeansException` để in kết quả kiểm chứng.
- Không sử dụng logic random, system time, reflection output, iteration order hay identity hash trong stdout.
- `try-with-resources` đóng mỗi context do ứng dụng tạo. Ở case deliberately failing refresh, framework có thể ghi cảnh báo/exception detail trên **stderr/logs**; ví dụ chỉ xác định chính xác **các dòng do `System.out.println` của main phát ra**.

### 8.4. Chạy bằng Maven

Trong thư mục `autowired-constructor-selection`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

**Đúng 7 dòng stdout do mã ứng dụng in (không tính thông tin tool/framework):**

~~~text
flexibleBoth=tracer+formatter
flexibleTracerOnly=tracer
flexibleNone=default
singleNoAnnotation=tracer
multipleNoAnnotation=default
requiredPresent=required-tracer
requiredMissing=REJECTED
~~~

Lệnh `java -version`, `mvn -version` và output log của Spring có thể khác theo máy; không phải bảy dòng `System.out.println(...)` được kiểm tra ở trên.

### 8.5. Chạy bằng javac/java

Vì mã import Spring, phải có Spring API/dependencies transitively trong classpath. Đầu tiên dùng Maven tạo file:

~~~bash
mvn -q dependency:build-classpath -Dmdep.outputFile=classpath.txt
~~~

Linux/macOS (bash/zsh):

~~~bash
mkdir -p out
javac --release 17 -encoding UTF-8 \
  -cp "$(cat classpath.txt)" \
  -d out src/main/java/demo/ConstructorSelectionDemo.java
java -cp "out:$(cat classpath.txt)" demo.ConstructorSelectionDemo
~~~

Windows PowerShell:

~~~powershell
$deps = (Get-Content -Raw classpath.txt).Trim()
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -encoding UTF-8 -cp "$deps" -d out src/main/java/demo/ConstructorSelectionDemo.java
java -cp "out;$deps" demo.ConstructorSelectionDemo
~~~

Trên Unix, classpath entries ngăn bằng dấu `:`; Windows dùng dấu `;`. Cả hai lệnh chạy cùng `main` và các trường hợp kiểm thử giống nhau. Nếu `javac` báo `package org.springframework... does not exist`, kiểm tra dependency/classpath trước khi kết luận quy tắc constructor resolution có lỗi.

## 9. Diễn giải từng dòng stdout

| Dòng | Điều được chứng minh |
| --- | --- |
| `flexibleBoth=tracer+formatter` | Hai optional annotated candidates đều có khả năng resolve; Spring dùng overload nhận hai dependencies |
| `flexibleTracerOnly=tracer` | Overload hai tham số thiếu Formatter; Spring chọn overload một tham số có thể giải quyết |
| `flexibleNone=default` | Không overload annotated nào thỏa; Spring dùng constructor mặc định |
| `singleNoAnnotation=tracer` | Chỉ một constructor nên không yêu cầu `@Autowired` |
| `multipleNoAnnotation=default` | Nhiều overload không annotation với no-arg: không greedy chọn overload nhận Tracer |
| `requiredPresent=required-tracer` | `@Autowired` required đã xác định constructor và dependency có mặt |
| `requiredMissing=REJECTED` | Required constructor thiếu dependency; Spring không lặng lẽ dùng no-arg fallback |

**Không suy từ kết quả này rằng Spring bao giờ cũng lấy nhiều tham số hơn**. Thử nghiệm cố ý đăng ký đúng một bean trên mỗi type, tách context và không dùng cấu hình bean factory bất thường. Các trường hợp có constructor overload ngang điểm, parameter names, qualifiers, beans primary, inheritance hoặc nhiều argument không rõ type cần đánh giá dựa trên cấu hình thực.

## 10. Các điểm dễ nhầm ngoài quy tắc chính

### 10.1. @Autowired trên constructor và @Bean factory method

Một đoạn JavaConfig:

~~~java
@Bean
ReportService reportService(Tracer tracer) {
    return new ReportService(tracer);
}
~~~

Đây là **snippet minh họa**, cần `ReportService`/`Tracer` và class config tương ứng. Spring resolve **tham số factory method** `Tracer`; lời gọi `new ReportService(tracer)` trong method là **lời gọi Java trực tiếp** tới overload được viết. Khi BeanDefinition yêu cầu factory method, không thể mặc nhiên dùng quy tắc chọn constructors cho class kết quả giống như khi đăng ký trực tiếp class để Spring instantiate.

Điều này quan trọng khi debug: đôi lúc developer nhìn annotations trên constructor của `ReportService` nhưng thực tế bean được tạo qua một `@Bean` factory method đang tự gọi constructor khác. Hãy xem **đường đăng ký và factory thực tế**, không chỉ nhìn class.

### 10.2. required=false không phải null injection

Với hai constructor `(Tracer)` và `(Tracer, Formatter)` có `@Autowired(required=false)`, khi thiếu Formatter thì Spring thử constructor phù hợp **ít tham số hơn**. Điều đó khác `new FlexibleClient(tracer, null)`. Bạn vẫn nên validate constructor arguments theo invariant Java của chính class.

Nếu không muốn service chạy khi thiếu Formatter, đánh dấu optional chỉ để khỏi báo lỗi là quyết định thiết kế sai; hãy giữ dependency bắt buộc và fail-fast.

### 10.3. @Autowired ở tham số constructor

Mặc dù annotation có `ElementType.PARAMETER`, tài liệu Spring Core lưu ý rằng việc đặt riêng `@Autowired` lên **parameter** không phải cơ chế thông thường để chọn constructor. Cách rõ ràng là đặt `@Autowired` lên **constructor**, và đặt `@Qualifier` hoặc `@Value` lên **parameter** khi cần chỉ định bean/value.

Đây là phân biệt quan trọng khi một class có nhiều overload: đừng viết annotation ở một parameter rồi mong Spring tự hiểu constructor nào là required.

### 10.4. Các tham số dạng Optional, @Nullable và provider

Một single constructor đôi khi có dependency tùy chọn thật sự:

~~~java
public ReportService(
        Tracer tracer,
        java.util.Optional<MetricsClient> metrics) {
    this.tracer = java.util.Objects.requireNonNull(tracer);
    this.metrics = java.util.Objects.requireNonNull(metrics);
}
~~~

**Snippet minh họa**, không phải source độc lập. `Optional<MetricsClient>` cho phép thể hiện absence một cách tường minh khi Spring resolve injection point tương ứng; cũng có thể dùng `@Nullable`/provider theo nhu cầu. Không vì thế mà mọi dependency nên optional — với collaborator bắt buộc, constructor injection rõ và fail-fast dễ kiểm soát hơn.

Khi chọn giữa một constructor có `Optional<T>` và nhiều `@Autowired(required=false)` constructors, ưu tiên thiết kế API đơn giản, nêu rõ trạng thái hợp lệ và kiểm thử được ngay bằng Java thuần.

### 10.5. Không coi mọi lỗi thành “fallback”

Nếu dependency có hai candidate mơ hồ, tự constructor được chọn có thể thất bại khi resolve tham số. Nếu một bean dependency được tạo nhưng constructor/initializer ném exception, đó là lỗi tạo bean thật. Spring **không bảo đảm** “có lỗi ở overload dài thì chắc chắn sử dụng overload ngắn và ứng dụng vẫn đúng”. Phải xác định loại lỗi theo `Caused by`, candidate set và constructor đã xét.

### 10.6. Các phiên bản/công cụ tạo constructor

Lombok có thể sinh thêm constructor mà source Java trước khi compile không thể hiện như bạn tưởng; Java records có constructor canonical riêng. Khi dùng Lombok, records, Kotlin hoặc code generation, hãy kiểm tra **constructors thực sự có trong bytecode** và quy tắc tương ứng. Ví dụ thuần Java 17 trong bài tránh các yếu tố đó để chỉ quan sát Spring `@Autowired`.

## 11. Các lỗi thực tế và hướng xử lý

| Dấu hiệu | Nguyên nhân thường gặp | Cách chẩn đoán/sửa |
| --- | --- | --- |
| Spring dùng no-arg dù đã có overload nhận collaborator | Nhiều constructors không `@Autowired`, default có sẵn | Giữ một constructor hoặc annotate constructor dự kiến |
| `UnsatisfiedDependencyException` khi startup | Constructor required cần bean chưa đăng ký | Kiểm tra type, candidate, registration và qualifier |
| `NoSuchBeanDefinitionException` trong nguyên nhân | Không có bean tương thích | Thêm bean config hoặc chỉnh dependency contract |
| `NoUniqueBeanDefinitionException` | Nhiều bean cùng type, không chọn được candidate | `@Qualifier`/`@Primary` hoặc sửa cấu hình |
| `BeanCreationException` | Factory/constructor/initializer của collaborator ném lỗi | Đọc `Caused by`; sửa logic/giá trị cấu hình |
| Exception về nhiều required constructors | Nhiều constructor gắn `@Autowired` mặc định true | Chỉ một required hoặc tất cả optional candidates theo thiết kế |
| Optional overload “bị bỏ qua” | Constructor đó không thể resolve đầy đủ parameters | Xác minh từng bean type; kiểm tra overload được Spring chọn |
| Không có constructor mặc định và không annotation | Spring không có lựa chọn hợp lệ rõ ràng | Annotate constructor hoặc tách thành một constructor |
| Thêm no-arg gây thay đổi wiring | Một class trước chỉ một constructor, sau thành nhiều overload | Viết test startup, quyết định rõ constructor dùng |
| Service có object nhưng thiếu collaborator | Constructor fallback cho phép trạng thái không hợp lệ | Không cung cấp fallback sai; require necessary dependencies |
| Sử dụng `@Primary` để “chọn constructor” | Nhầm bean candidate với overload choice | `@Primary` chọn bean theo type, constructor cần `@Autowired` phù hợp |
| Unit test gọi `new` chọn overload khác Spring | Caller Java gọi constructor rõ ràng, Spring chọn theo metadata | Kiểm tra cả unit tests và Spring wiring tests |
| `javac` báo thiếu Spring | Classpath/dependency build thiếu | Kiểm tra Maven pom và `-cp` |
| Không thấy lỗi khi bean @Lazy | Instantiation có thể bị trì hoãn | Kiểm thử `getBean`/flow thực và thời điểm tạo |

### Checklist debug theo thứ tự

1. **Bean thực sự được tạo theo đường nào?** Class registration/component scanning hay method `@Bean` trả object đã `new`?
2. **Class có bao nhiêu constructors?** Có constructor mặc định, synthetic/generated hay overload mới được thêm?
3. **Annotation nằm ở đâu?** `@Autowired` trên constructor nào, `required` true hay false?
4. **Container có những beans nào?** Mỗi tham số có candidate phù hợp không? Có hơn một candidate cần qualifier không?
5. **Constructor ứng viên được chọn là gì?** Dựa vào rules và startup logs/exception chain; không chỉ nhìn số tham số.
6. **Object có trạng thái hợp lệ không?** Constructor fallback có bỏ qua dependency thực sự cần thiết?
7. **Có tests chống regression không?** Một thay đổi constructor signature không nên âm thầm đổi wiring của service.
8. **Kết luận và đơn giản hóa:** nếu overload phức tạp chỉ để hỗ trợ DI, cân nhắc một constructor required rõ và dùng factory cho các case khác.

## 12. Kiểm thử các quy tắc với ít giả định

Bài demo kiểm tra qua stdout, nhưng một dự án nghiêm túc nên có:

- **Unit test Java:** gọi trực tiếp từng overload dự kiến, kiểm tra `Objects.requireNonNull` và invariant; không cần Spring.
- **Spring context test:** tạo context chỉ đăng ký các bean cần thiết, lấy service, kiểm tra đường constructor đã dùng (thí dụ một property enum/string chỉ phục vụ test).
- **Negative test:** thiếu collaborator required thì context fail, **không rơi vào default**.
- **Ambiguity test:** thêm hai Tracer candidates có chủ đích để xác minh qualifier/primary, không kết luận từ exception message nguyên văn.
- **Factory method test:** kiểm tra đường `@Bean` có tự gọi constructor định sẵn hay container instantiate trực tiếp.
- **Regression test khi thêm overload:** một test chỉ bảo đảm Java compile không đủ để phát hiện wiring thay đổi.

Không dùng Java `assert` mặc định làm kiểm tra bắt buộc nếu không bật `-ea`. Dùng assertions của test framework hoặc kiểm tra rồi ném exception. Cũng không khóa test vào toàn bộ stack trace vì thông điệp, thứ tự logs và wrapper có thể thay đổi theo phiên bản.

## 13. Bài tập và đáp án

**Bài 1.** Class chỉ có constructor `Service(Tracer tracer)` không `@Autowired`. Spring Framework 6 có sử dụng constructor đó không?

**Đáp án:** Có, nếu class đã đăng ký là Spring bean và một dependency phù hợp giải quyết được. Một constructor duy nhất không cần annotation.

**Bài 2.** Có constructor mặc định và constructor `Service(Tracer)` gắn `@Autowired` mặc định. Context không có Tracer. Spring dùng mặc định không?

**Đáp án:** Không theo quy tắc required constructor. Spring không bỏ qua một required annotated constructor chỉ để chọn no-arg; bean creation thất bại.

**Bài 3.** Có ba constructors: mặc định, `Service(Tracer)` required=false và `Service(Tracer, Formatter)` required=false. Context chỉ có Tracer. Chọn gì?

**Đáp án:** Chọn `Service(Tracer)` vì giải quyết được, còn overload dài hơn thiếu Formatter.

**Bài 4.** Nếu context có cả Tracer và Formatter thì sao?

**Đáp án:** Chọn overload hai tham số trong ví dụ, vì đó là annotated candidate thỏa nhiều dependencies nhất.

**Bài 5.** Nếu context không có cả hai dependencies và có default constructor?

**Đáp án:** Khi không có annotated candidates `required=false` nào có thể giải quyết, fallback về default constructor nếu có.

**Bài 6.** Hai constructors không annotation, một no-arg và một `Service(Tracer)`, context có Tracer. Có luôn dùng overload nhận Tracer không?

**Đáp án:** Không. Theo cơ chế constructor selection thông thường, default được dùng; đừng áp dụng nhầm quy tắc của nhiều `required=false` annotated candidates.

**Bài 7.** Có hai Tracer beans và một constructor cần Tracer. `@Primary` xử lý cái gì?

**Đáp án:** Chọn **bean Tracer** mặc định cho injection point khi phù hợp, không trực tiếp chọn constructor overload. Có thể dùng `@Qualifier` để rõ hơn.

**Bài 8.** `@Autowired(required=false)` trên constructor có nghĩa mọi tham số có thể null không?

**Đáp án:** Không. Với nhiều constructors annotated, nó cho phép constructor là một ứng viên tùy điều kiện, không phải lệnh inject null vào mọi tham số không có bean.

**Bài 9.** Bean được tạo trong method `@Bean service() { return new Service(...); }`. Ai chọn overload?

**Đáp án:** Lời gọi Java `new Service(...)` trong factory method chỉ rõ overload dựa trên đối số; Spring không nhất thiết tự chọn constructor khác của object trả về theo quy tắc class registration.

## 14. Checklist áp dụng

- [ ] Biết một constructor duy nhất có thể hoạt động không `@Autowired`.
- [ ] Với nhiều constructors, quyết định rõ cái nào dùng cho managed bean.
- [ ] Không gắn `@Autowired(required=true)` ở nhiều constructors.
- [ ] Nếu có nhiều `@Autowired(required=false)`, hiểu **greediest satisfiable candidate**.
- [ ] Khi thiếu các candidates tùy chọn, chỉ dùng default fallback nếu trạng thái class vẫn hợp lệ.
- [ ] Không coi `required=false` là cho phép inject null bừa bãi.
- [ ] Không áp dụng quy tắc greedy cho nhiều constructors **không annotation**.
- [ ] Phân biệt constructor choice với bean candidate resolution/`@Qualifier`/`@Primary`.
- [ ] Biết kiểm tra actual registration path: bean class vs `@Bean` factory method.
- [ ] Không biến dependency nghiệp vụ bắt buộc thành tùy chọn chỉ để startup thành công.
- [ ] Có test cho cả case đầy đủ, thiếu một dependency, thiếu mọi dependency và required failure.
- [ ] Kiểm tra constructor được code generation/record/framework khác tạo ra khi cần.
- [ ] Chạy Java 17+ bằng Maven hoặc javac/java với Spring classpath và đối chiếu **7 dòng stdout**.
- [ ] Khi lỗi, đọc bean name, constructor candidates và `Caused by` trước khi sửa.
- [ ] Ưu tiên **một constructor rõ ràng** cho các mandatory dependencies ở phần lớn business services.

**Kết luận:** `@Autowired` không phải chỉ dấu “Spring cứ chọn constructor dài nhất”. Spring **ưu tiên constructor required nếu được chỉ định**, **chọn overload có nhiều dependency có thể thỏa nhất** trong tập các constructor `required=false`, **fallback** về default khi không ứng viên nào phù hợp và điều kiện cho phép, và **dùng constructor duy nhất** kể cả khi thiếu annotation. Nắm rõ registration path và cách resolve bean candidates giúp bạn thiết kế DI **dễ dự đoán và fail-fast đúng nơi**.

### Tài liệu Spring chính thức

- [Spring Framework 6.2 — @Autowired JavaDoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/annotation/Autowired.html)
- [Spring Framework — Using @Autowired](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html)
- [AutowiredAnnotationBeanPostProcessor API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/annotation/AutowiredAnnotationBeanPostProcessor.html)
- [Spring Java Configuration / @Bean](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="autowired-004">Tiêm trường, setter và phương thức nhiều tham số</a>

<details>
<summary>Xem chi tiết</summary>

# Tiêm trường, setter và phương thức nhiều tham số với `@Autowired`

## 1. Mục tiêu và phạm vi

Spring Core Container có thể tự cung cấp dependency cho một object do container quản lý qua nhiều điểm tiêm (*injection points*). Bài này tập trung vào ba kiểu sử dụng `@Autowired` trên **instance field**, **JavaBean setter**, và **phương thức có tên tùy ý, nhận nhiều tham số**. Đây là những cơ chế liên quan nhau nhưng có khác biệt quan trọng về thời điểm tiêm, trạng thái đối tượng, cách xử lý dependency thiếu và khả năng kiểm thử.

Sau khi học, bạn có thể:

1. Viết và đọc đúng `@Autowired` trên trường `private`, trên setter `setX(...)` và trên phương thức `configure(A, B, ...)`.
2. Giải thích vì sao cả **field và method injection** xảy ra **sau khi Java constructor chạy**; Spring thực hiện nó khi tạo **Spring-managed bean**, không phải tự chạy trên một object được `new` tùy ý.
3. Phân biệt phương thức nhiều tham số `required=true` (mặc định) với `@Autowired(required=false)` và tham số `Optional<T>`.
4. Biết cách Spring chọn bean theo type, qualifier, primary và xử lý các tình huống thiếu hoặc có nhiều candidate.
5. Quan sát trực tiếp **một bean được field-inject, một bean setter-inject và một bean có hai tham số method-inject**, có ví dụ riêng cho phương thức nhiều tham số tùy chọn.
6. Dùng được Java 17+, Maven, Spring Framework 7.0.9 với lệnh chạy và expected stdout xác định.

**Phạm vi:** chỉ Spring Framework Core Container thuần; không sử dụng Spring Boot, HTTP server, database, `@SpringBootApplication` hoặc auto-configuration. Quy tắc cụ thể về Java constructor injection chỉ được so sánh khi nó giúp hiểu ba kiểu injection chính.

## 2. Ba cơ chế đặt cạnh nhau

| Kiểu injection | Ví dụ annotation | Tham số | Thời điểm | Hệ quả thiết kế |
| --- | --- | --- | --- | --- |
| **Field** | `@Autowired private Gateway gateway;` | Không có tham số method | **Sau construction**, khi Spring population field | Rất ngắn nhưng dependency bị ẩn khỏi constructor; không tự nhiên với `final` |
| **Setter** | `@Autowired void setGateway(Gateway g)` | Thường một | **Sau construction**, Spring resolve argument và gọi setter | Hợp lý cho property có giá trị mặc định/cho phép cấu hình lại; reference mutable nếu setter công khai |
| **Arbitrary method** | `@Autowired void prepare(Gateway g, Formatter f)` | **Một hoặc nhiều**, tên bất kỳ | **Sau construction**, Spring resolve **các tham số** rồi gọi method | Có thể cấu hình nhiều dependency trong một thao tác có chủ đích |
| Constructor (đối chiếu) | `@Autowired Service(Gateway g)` hoặc một constructor duy nhất không annotation | Một hoặc nhiều | **Trước/suốt construction** | Hợp đồng rõ, hỗ trợ fields `final`, unit test thuận lợi |

Điểm thường gây nhầm: **setter injection không phải một hệ thống Spring đặc biệt tách khỏi method injection**. Setter `setGateway(Gateway)` là **trường hợp riêng** của phương thức cấu hình mà Spring có thể gọi. `@Autowired` không yêu cầu method tên `set...`, và phương thức nhiều tham số có thể tên `initializeClients`, `configure`, `prepare` hoặc tên khác.

Tất cả các dạng trên đều cần một **bean đích thuộc context** và hạ tầng xử lý annotation. Mặc định `AnnotationConfigApplicationContext` đăng ký các annotation processors thiết yếu; trong đó có `AutowiredAnnotationBeanPostProcessor`. Container phải tìm bean candidates cho injection point rồi mới có reference để cấp.

**`@Autowired` không tự tạo một implementation bất kỳ từ interface.** Ví dụ `@Autowired Gateway gateway` cần có bean Gateway được đăng ký và phù hợp; nếu thiếu, Spring thường báo lỗi tạo bean, không tự `new Gateway`.

## 3. Field injection: ngắn, nhưng dependency bị ẩn

Đoạn cú pháp:

~~~java
// Snippet trong một Spring-managed bean:
@Autowired
private Gateway gateway;

public String route() {
    return gateway.id();
}
~~~

Điểm quan trọng:
- Spring có thể inject **private instance field** nhờ cơ chế phản chiếu thích hợp. Field không cần setter hay `public`; annotation nằm ngay trên field.
- Injection xảy ra **sau lúc constructor tạo instance**. Nếu constructor đọc field này trước khi Spring cấp nó, giá trị thường vẫn là `null` (trừ khi class tự khởi tạo field).
- Cần **Spring-managed bean**. `new FieldClient()` ngoài Spring không kích hoạt `@Autowired`. Nếu không có giá trị khởi tạo khác, dùng field lúc đó có thể gây `NullPointerException`.
- **Field `final` nên được gán ở constructor hoặc initializer trong Java**; không sử dụng field injection như đường thông thường để cung cấp required dependency vào một final field.
- `@Autowired` mặc định có `required=true`. Khi không có candidate phù hợp để inject một required field, bean creation có thể thất bại.
- `@Autowired(required=false)` cho field thật sự optional có thể **bỏ qua việc gán**, giữ giá trị field mặc định. Không dùng cách này để che một dependency vốn bắt buộc.

**So với constructor injection:** Một constructor `Service(Gateway gateway)` cho phép Java caller truyền fake và giữ `private final Gateway gateway`. Với field injection, mã test tự `new Service()` không nhận được dependency trừ khi dùng test seam, reflection hoặc context thật. Vì thế constructor injection thường phù hợp hơn cho **required business dependency**, còn field injection được hỗ trợ nhưng cần nhận biết rõ trade-off.

**Thread safety:** Một Spring singleton field-injected không mặc nhiên thread-safe. Việc được inject một reference không làm object cộng tác thành immutable hay đồng bộ hóa trạng thái của nó. Nếu reference không cần thay đổi sau startup, thiết kế bằng constructor + `final` thường làm invariant rõ hơn.

## 4. Setter injection: JavaBean setter là một config method

Một setter injection có dạng:

~~~java
// Snippet đặt trong class có interface Gateway.
private Gateway gateway;
private int calls;

@Autowired
public void setGateway(Gateway gateway) {
    this.gateway = gateway;
    calls++;
}
~~~

Khi Spring tạo bean:
1. Constructor của bean được chạy.
2. Spring nhận diện method có `@Autowired`.
3. Bean factory resolve một dependency `Gateway` phù hợp.
4. Spring gọi `setGateway(resolvedGateway)` **một lần trong quá trình injection thông thường**.
5. Business code có thể dùng field đã được cấu hình khi bean được khởi tạo xong.

Điều kiện không phải chỉ có tên `set...`: `@Autowired` là thứ làm method được xem như injection point. Nếu setter không annotation và cũng không được cấu hình inject bằng cơ chế khác, Spring **không mặc nhiên phải gọi nó** chỉ vì method bắt đầu bằng `set`.

Setter có thể là `public`, `protected` hoặc không-public khi khả năng truy cập của môi trường cho phép. Các ví dụ chạy độc lập ở dưới dùng method `public` cho dễ quan sát. Trong các ứng dụng JPMS dùng Java module path, cần xem yêu cầu `opens`/reflection; bài này chạy trên classpath thông thường.

### Khi nào setter hữu ích?

- Dependency **thật sự optional** có default behavior hợp lệ.
- Cấu hình khởi tạo của một class legacy đã tồn tại mà không thể sửa constructor.
- Một property có thể được điều chỉnh lại bằng API công khai theo hợp đồng rõ ràng.

Nhưng **mở setter cho dependency bắt buộc** có thể cho phép Java caller tạo bean ở trạng thái chưa đầy đủ, và các caller khác có thể thay reference sau đó. Với một singleton được dùng nhiều luồng, thay đổi mutable dependency mà không có đồng bộ đúng sẽ tạo rủi ro.

**Vì sao có thể fail lúc startup?** Nếu setter được annotate `@Autowired` mặc định required, mà không có bean Gateway phù hợp, Spring thường không khởi tạo xong bean. Việc setter chỉ có một tham số không khiến nó tự biến thành optional.

## 5. Phương thức nhiều tham số: mọi dependency được resolve trước lời gọi

Spring hỗ trợ config method có tên bất kỳ:

~~~java
// Snippet: Gateway và Formatter là các interfaces trong demo.
private Gateway gateway;
private Formatter formatter;

@Autowired
public void configure(Gateway gateway, Formatter formatter) {
    this.gateway = gateway;
    this.formatter = formatter;
}
~~~

Với method trên, Spring phải tìm **cả Gateway và Formatter** trước khi gọi `configure(...)`. Nếu injection point required và thiếu một tham số bắt buộc, bean creation có thể thất bại; method không được gọi thành công với một danh sách đối số “nửa vời”.

Có thể dùng một method để đặt **một nhóm phụ thuộc có liên quan**; ví dụ gateway và formatter phối hợp trong một đơn vị cấu hình. Nếu chúng không có liên hệ nghiệp vụ chặt chẽ, gộp 12 tham số vào một method để né constructor dài không giải quyết vấn đề nhiều trách nhiệm của class.

### Tên method và số lượng tham số

- `configure(Gateway, Formatter)` và `setParts(Gateway, Formatter)` đều có thể được Spring xử lý nếu được annotate; đây **không** phải JavaBeans setter đơn tham số theo convention, nhưng là arbitrary config method của Spring.
- Có thể dùng `@Qualifier` trên **tham số cụ thể** khi nhiều beans cùng type, chẳng hạn `configure(@Qualifier("primaryGateway") Gateway g, Formatter f)`.
- `@Autowired` trên **declaration của method** dùng cho method injection. Đặt `@Autowired` riêng lên từng parameter không phải cách thông thường để làm method đó được tự động gọi bởi Core Container.
- Các method không-public vẫn có thể được hỗ trợ theo khả năng truy cập, nhưng tránh thiết kế phụ thuộc quá nặng vào reflection khi cần chạy trên module path.
- Phương thức `static` không phải đích autowiring instance thông thường; dependency nên nằm ở bean instance thay vì global static state.

**Thứ tự:** Javadoc của `AutowiredAnnotationBeanPostProcessor` mô tả field injection được xử lý sau construction và trước các autowired config methods. Không nên dựa vào **thứ tự tuyệt đối giữa nhiều methods độc lập** của cùng một bean khi thiết kế business rule; nếu phải chạy công việc initialization **sau tất cả các injections**, dùng lifecycle callback phù hợp.

## 6. `required=false`: phương thức nhiều tham số bị bỏ qua toàn bộ

Đây là phần khác biệt đặc biệt quan trọng giữa **method injection** và cách nhiều người suy đoán về null:

~~~java
// Snippet: Gateway có bean, nhưng Telemetry có thể vắng mặt.
@Autowired(required = false)
public void attach(Gateway gateway, Telemetry telemetry) {
    // Chỉ chạy nếu resolve được toàn bộ tham số cần thiết.
}
~~~

Nếu context có Gateway nhưng **không có Telemetry**, Spring **không gọi `attach`**. Nó không tự gọi `attach(gateway, null)`. Thuộc tính `required=false` ở đây áp dụng cho **injection point của method**, nghĩa là khi một dependency cần thiết không thỏa, có thể **bỏ qua cả method**. Điều này phù hợp với tài liệu Spring Framework: non-required method không được gọi nếu một trong các dependency thiếu.

| Tình huống | `@Autowired` method required | `@Autowired(required=false)` method |
| --- | --- | --- |
| Có đủ Gateway và Telemetry | Spring resolve và gọi method | Spring resolve và gọi method |
| Có Gateway, thiếu Telemetry | Thường fail bean creation | **Bỏ qua toàn bộ method**, không gán một nửa |
| Không có cả hai | Thường fail bean creation | **Bỏ qua toàn bộ method** |
| Có nhiều Gateway candidates, không chọn được | Có thể fail vì ambiguity | Không nên coi `required=false` là cơ chế chọn tùy tiện hoặc che conflict |

Trong các phương thức nhiều tham số cần vừa **chạy khi có dependency A**, vừa **chấp nhận thiếu B**, hãy cân nhắc biểu diễn riêng tham số B thành `Optional<B>` (hoặc dùng provider/default strategy). Điều đó khác rõ ràng với việc đánh dấu **cả method** non-required.

Ví dụ:

~~~java
@Autowired
public void observe(
        Gateway gateway, Optional<Telemetry> telemetry) {
    // Method vẫn được gọi khi có Gateway, dù telemetry = Optional.empty().
}
~~~

Dù `Optional<T>` có thể biểu diễn vắng mặt, **đừng dùng Optional để biến business dependency thực sự bắt buộc thành không bắt buộc**. Khi thiếu Gateway là lỗi không thể vận hành, nên để context fail-fast thay vì âm thầm chạy ở chế độ sai.

## 7. Phân giải nhiều candidates, qualifiers, collection và nullability

### Một type có nhiều beans

Nếu có `fastGateway` và `safeGateway` cùng implement `Gateway`, một injection point chỉ khai báo `Gateway` có thể mơ hồ:

~~~java
// Snippet áp dụng cho arbitrary config method:
@Autowired
public void configure(
        @Qualifier("fastGateway") Gateway gateway,
        Formatter formatter) {
    // ...
}
~~~

Cần import `org.springframework.beans.factory.annotation.Qualifier`, đăng ký bean tên/qualifier phù hợp. `@Qualifier` giúp **lọc candidate**; nó không tạo Gateway và cũng không đổi Java type. `@Primary` có thể đặt ưu tiên bean khi resolve single-valued dependency trong điều kiện phù hợp.

**Không khẳng định Spring luôn tìm theo tên field trước rồi mới theo type**. Spring resolution chủ yếu theo type, cùng metadata qualifier/primary, generic và các quy tắc khác. Việc trùng tên field/parameter với bean name có thể có vai trò nhưng không nên dùng nó thay hợp đồng lựa chọn rõ khi có nhiều implementations.

### Nhiều beans muốn inject cùng lúc

Spring còn hỗ trợ:
- `List<Gateway>` hoặc `Gateway[]` để nhận các beans phù hợp (quy tắc ordering có thể bị ảnh hưởng bởi `@Order`/`Ordered`).
- `Map<String, Gateway>` để dùng **bean names** làm keys.
- Các điểm inject vào collection bắt buộc có quy tắc riêng theo từng trường hợp; **không** tự suy ra lúc nào thiếu beans sẽ thành empty collection, nhất là khi so sánh với constructor/factory method special cases.

### Optional của từng tham số

`Optional<T>` tại injection point là cách diễn đạt vắng bean. `ObjectProvider<T>` là một API Spring cho **lookup khi cần**, với `getIfAvailable()` khác `getObject()`; cái sau yêu cầu một bean phù hợp và có thể lỗi nếu thiếu. Với `@Autowired(required=false)` trên setter, cả setter bị bỏ qua khi dependency vắng — không cần có một lệnh `setX(null)`.

`@Nullable` được Spring hỗ trợ tại injection points qua những annotation nullability thích hợp theo version, nhưng cần chọn đúng package/semantics. Trong bài, `Optional<T>` thuần Java 17 được dùng để tránh lệ thuộc thêm vào một nullability annotation cụ thể.

## 8. Demo Maven Java 17: field, setter, method nhiều tham số và optional method

Một project Java 17 duy nhất dùng Spring Framework **7.0.9**. Nó chạy ba thí nghiệm có kiểm soát:

1. **Java `new` ngoài container:** field `@Autowired` của `FieldClient` chưa có reference.
2. **Context cơ bản:** có Gateway và Formatter, nhưng **không** có Telemetry. Spring inject field, gọi setter và multi-parameter method. Phương thức non-required `attach(Gateway, Telemetry)` **bị bỏ qua**, trong khi `observe(Gateway, Optional<Telemetry>)` vẫn được gọi với empty.
3. **Context có Telemetry:** cả hai dependency được cấp, nên `attach(...)` được gọi; `Optional<Telemetry>` nhận bean hiện diện.
4. **Thiếu required field:** tạo một bean `MissingClient` có `@Autowired Telemetry` nhưng không khai báo Telemetry, quan sát startup bị từ chối.

Mọi lớp trong demo đều khai báo **cùng một file Java**, làm ví dụ độc lập và dễ chạy. Không cần Spring Boot, web, database hoặc JUnit.

### 8.1. Cấu trúc project

~~~text
injection-forms-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── InjectionFormsDemo.java
~~~

Trước khi thực hành, xác nhận **JDK 17+**, **Maven 3.9+** và repository Maven Central/mirror truy cập được.

### 8.2. `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>injection-forms-demo</artifactId>
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
                    <mainClass>demo.InjectionFormsDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Maven giải quyết `spring-context` và các Spring artifacts nền theo dependency graph; không cần tự tải các JAR transitive. `maven.compiler.release=17` đặt target Java 17, và Exec Maven Plugin chạy main class cùng dependency classpath.

### 8.3. `src/main/java/demo/InjectionFormsDemo.java`

~~~java
package demo;

import java.util.Optional;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class InjectionFormsDemo {
    private InjectionFormsDemo() {
    }

    @FunctionalInterface
    public interface Gateway {
        String id();
    }

    @FunctionalInterface
    public interface Formatter {
        String format(String name);
    }

    @FunctionalInterface
    public interface Telemetry {
        String id();
    }

    public static final class FieldClient {
        @Autowired
        private Gateway gateway;

        public boolean ready() {
            return gateway != null;
        }

        public String gatewayId() {
            return gateway.id();
        }
    }

    public static final class SetterClient {
        private Gateway gateway;
        private int setterCalls;

        @Autowired
        public void setGateway(Gateway gateway) {
            this.gateway = gateway;
            setterCalls++;
        }

        public int calls() {
            return setterCalls;
        }

        public String gatewayId() {
            return gateway.id();
        }
    }

    public static final class MultiClient {
        @Autowired
        private Gateway fieldGateway;
        private Gateway gateway;
        private Formatter formatter;
        private int methodCalls;
        private boolean fieldReadyDuringMethod;

        @Autowired
        public void configure(Gateway gateway, Formatter formatter) {
            this.fieldReadyDuringMethod = fieldGateway != null;
            this.gateway = gateway;
            this.formatter = formatter;
            methodCalls++;
        }

        public int calls() {
            return methodCalls;
        }

        public boolean fieldReadyDuringMethod() {
            return fieldReadyDuringMethod;
        }

        public String result(String name) {
            return gateway.id() + "|" + formatter.format(name);
        }
    }

    public static final class OptionalClient {
        private int optionalMethodCalls;
        private int observationCalls;
        private boolean telemetryPresent;
        private String state = "default";

        @Autowired(required = false)
        public void attach(Gateway gateway, Telemetry telemetry) {
            optionalMethodCalls++;
            state = gateway.id() + ":" + telemetry.id();
        }

        @Autowired
        public void observe(Gateway gateway, Optional<Telemetry> telemetry) {
            observationCalls++;
            telemetryPresent = telemetry.isPresent();
        }

        public int optionalMethodCalls() {
            return optionalMethodCalls;
        }

        public int observationCalls() {
            return observationCalls;
        }

        public boolean telemetryPresent() {
            return telemetryPresent;
        }

        public String state() {
            return state;
        }
    }

    public static final class MissingClient {
        @Autowired
        private Telemetry telemetry;
    }

    @Configuration(proxyBeanMethods = false)
    public static class BaseConfig {
        @Bean
        public Gateway gateway() {
            return () -> "GW";
        }

        @Bean
        public Formatter formatter() {
            return name -> "Hello," + name;
        }

        @Bean
        public FieldClient fieldClient() {
            return new FieldClient();
        }

        @Bean
        public SetterClient setterClient() {
            return new SetterClient();
        }

        @Bean
        public MultiClient multiClient() {
            return new MultiClient();
        }

        @Bean
        public OptionalClient optionalClient() {
            return new OptionalClient();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ExtraConfig {
        @Bean
        public Telemetry telemetry() {
            return () -> "TR";
        }
    }

    public static void main(String[] args) {
        // Lệnh new thuần Java KHÔNG chạy Spring post-processors.
        FieldClient plain = new FieldClient();
        System.out.println("manual.field.ready=" + plain.ready());

        // Có Gateway và Formatter nhưng KHÔNG có Telemetry.
        try (var base =
                new AnnotationConfigApplicationContext(BaseConfig.class)) {
            FieldClient field = base.getBean(FieldClient.class);
            SetterClient setter = base.getBean(SetterClient.class);
            MultiClient multi = base.getBean(MultiClient.class);
            OptionalClient optional = base.getBean(OptionalClient.class);

            System.out.println("base.field.ready=" + field.ready());
            System.out.println("base.field.gateway=" + field.gatewayId());
            System.out.println("base.setter.calls=" + setter.calls());
            System.out.println("base.setter.gateway=" + setter.gatewayId());
            System.out.println("base.multi.calls=" + multi.calls());
            System.out.println("base.multi.fieldBeforeMethod="
                    + multi.fieldReadyDuringMethod());
            System.out.println("base.multi.value=" + multi.result("Lan"));
            System.out.println("base.optional.calls="
                    + optional.optionalMethodCalls());
            System.out.println("base.optional.state=" + optional.state());
            System.out.println("base.optional.observeCalls="
                    + optional.observationCalls());
            System.out.println("base.optional.telemetryPresent="
                    + optional.telemetryPresent());
        }

        // Thêm Telemetry, các injection points đều thỏa mãn.
        try (var with = new AnnotationConfigApplicationContext(
                BaseConfig.class, ExtraConfig.class)) {
            OptionalClient optional = with.getBean(OptionalClient.class);

            System.out.println("with.optional.calls="
                    + optional.optionalMethodCalls());
            System.out.println("with.optional.state=" + optional.state());
            System.out.println("with.optional.observeCalls="
                    + optional.observationCalls());
            System.out.println("with.optional.telemetryPresent="
                    + optional.telemetryPresent());
            System.out.println("with.optional.singleton="
                    + (optional == with.getBean(OptionalClient.class)));
        }

        // Required field thiếu bean: refresh() thất bại.
        try (var missing = new AnnotationConfigApplicationContext()) {
            missing.register(BaseConfig.class);
            missing.registerBean(MissingClient.class);
            missing.refresh();
        } catch (BeansException expected) {
            System.out.println("missingRequired.failed=true");
        }
    }
}
~~~

### 8.4. Giải thích code theo ba injection styles

- **FieldClient:** field `private Gateway gateway` có `@Autowired`, Spring điền bean Gateway sau construction. Với object `new FieldClient()` bên ngoài Spring, `ready() == false`.
- **SetterClient:** method `setGateway(Gateway)` có annotation. Bộ đếm `setterCalls` giúp xác nhận method được Spring gọi **một lần** ở lần tạo bean trong ví dụ. Method cũng có thể được gọi bằng Java code thường nếu caller gọi trực tiếp; annotation không độc quyền cho Spring.
- **MultiClient:** method `configure(Gateway,Formatter)` không mang tên `set...` nhưng vẫn là injection point. Spring resolve cả hai tham số. `fieldGateway` được inject qua field trước config method trong cơ chế này, nên cờ `fieldReadyDuringMethod` là `true`.
- **OptionalClient:** `attach(Gateway,Telemetry)` được đánh dấu `required=false`. Trong context base chỉ có Gateway, Spring **bỏ qua toàn bộ method**, giữ `state="default"` và `optionalMethodCalls=0`. Nhưng `observe(Gateway,Optional<Telemetry>)` vẫn được gọi; tham số optional là empty.
- **ExtraConfig:** thêm Telemetry, lần này `attach` chạy một lần và `observe` nhận `Optional.of(...)`. Hai methods không phụ thuộc vào **thứ tự giữa chúng**, nên kết quả cuối cùng ổn định.
- **MissingClient:** một required field Telemetry, được đăng ký riêng trong context thiếu Telemetry. `refresh()` có thể ném `BeansException`; demo catch và ghi đúng một dòng xác nhận thất bại.

`BaseConfig` dùng `@Bean` factory methods trả các object Java. **Bean do `@Bean` trả về vẫn được Spring post-process**, nên các trường/methods có `@Autowired` được xử lý dù `FieldClient` không hề mang `@Component`.

`@Configuration(proxyBeanMethods=false)` phù hợp vì các factory methods không gọi trực tiếp nhau. Không sửa `BaseConfig` để tự gọi `gateway()` trong những factory methods khác rồi suy luận đó là cùng Spring singleton: với chế độ proxy tắt, lời gọi Java thông thường có thể tạo object khác.

## 9. Chạy CLI và expected stdout

Tại thư mục `injection-forms-demo` chứa `pom.xml`, chạy:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

Ba lệnh kiểm tra môi trường có output phụ thuộc hệ điều hành, JDK và Maven. Lệnh cuối biên dịch với `javac` thông qua Maven Compiler Plugin, sau đó chạy `demo.InjectionFormsDemo` với classpath Spring.

**18 dòng stdout xác định do chính các lệnh `System.out.println` trong `main` tạo ra** (không tính log/diagnostic của Maven hay Spring):

~~~text
manual.field.ready=false
base.field.ready=true
base.field.gateway=GW
base.setter.calls=1
base.setter.gateway=GW
base.multi.calls=1
base.multi.fieldBeforeMethod=true
base.multi.value=GW|Hello,Lan
base.optional.calls=0
base.optional.state=default
base.optional.observeCalls=1
base.optional.telemetryPresent=false
with.optional.calls=1
with.optional.state=GW:TR
with.optional.observeCalls=1
with.optional.telemetryPresent=true
with.optional.singleton=true
missingRequired.failed=true
~~~

**Nhóm quan sát cần nắm:**

| Dòng stdout | Bằng chứng |
| --- | --- |
| `manual.field.ready=false` | Java `new` riêng không kích hoạt autowiring |
| `base.field.ready=true`, `gateway=GW` | Spring cấp bean vào private instance field |
| `base.setter.calls=1` | Spring gọi method setter `@Autowired` khi tạo bean |
| `base.multi.calls=1`, `multi.value=GW|Hello,Lan` | Method tên tùy ý nhận **hai dependencies**, xử lý kết quả |
| `base.multi.fieldBeforeMethod=true` | Field injection đã chạy trước autowired config method của bean này |
| `base.optional.calls=0`, `state=default` | Non-required multi-param method **không chạy** khi thiếu Telemetry |
| `base.optional.observeCalls=1`, `telemetryPresent=false` | Method required vẫn chạy nhờ `Optional.empty()` ở tham số optional |
| `with.optional.calls=1`, `state=GW:TR` | Khi đủ dependencies, non-required method được gọi |
| `with.optional.telemetryPresent=true` | `Optional<Telemetry>` chứa bean hiện diện |
| `with.optional.singleton=true` | OptionalClient là singleton trong context thứ hai |
| `missingRequired.failed=true` | Thiếu required field làm bean creation thất bại, không âm thầm gán null |

**Không chép log exception cố định** cho `missing` context: Spring có thể ghi cảnh báo khi startup fail, với stack trace/message phụ thuộc phiên bản/logging. Expected output chỉ là các dòng do source ứng dụng chủ động in.

## 10. Java thuần `javac`/`java`: tự tạo object và tự cấp tham số

Cả field và method injection đều có điểm tương đồng với Java thông thường: **một object được tạo, rồi caller gán field/gọi setter hoặc config method**. Spring tự động hóa việc tìm bean và gọi đúng injection points, còn Java thuần phải làm việc đó bằng code rõ ràng.

Tạo file riêng `PureInjectionTypes.java`:

~~~java
public class PureInjectionTypes {
    interface Gateway {
        String id();
    }

    interface Formatter {
        String format(String name);
    }

    static final class Client {
        Gateway field;
        Gateway setterGateway;
        Gateway multiGateway;
        Formatter formatter;
        int setterCalls;

        void setGateway(Gateway gateway) {
            setterGateway = gateway;
            setterCalls++;
        }

        void configure(Gateway gateway, Formatter formatter) {
            multiGateway = gateway;
            this.formatter = formatter;
        }

        String multiResult(String name) {
            return multiGateway.id() + "|" + formatter.format(name);
        }
    }

    public static void main(String[] args) {
        Client client = new Client();
        System.out.println("before.manual.field="
                + (client.field != null));

        Gateway gateway = () -> "G";
        Formatter formatter = name -> "Hi," + name;

        client.field = gateway;
        client.setGateway(gateway);
        client.configure(gateway, formatter);

        System.out.println("after.manual.field="
                + (client.field != null));
        System.out.println("after.manual.setter.calls="
                + client.setterCalls);
        System.out.println("after.manual.multi="
                + client.multiResult("Lan"));
    }
}
~~~

Biên dịch bằng **JDK 17+** trong thư mục chứa file, không cần Maven hay Spring:

~~~bash
javac PureInjectionTypes.java
java PureInjectionTypes
~~~

Stdout chính xác:

~~~text
before.manual.field=false
after.manual.field=true
after.manual.setter.calls=1
after.manual.multi=G|Hi,Lan
~~~

Ví dụ chứng minh setter và method nhiều tham số vốn là **lời gọi Java bình thường**. Spring thêm khả năng resolve dependency từ context. Không phải cứ tạo một object Java có setter là Spring tự động inject nó; object phải thuộc cơ chế đăng ký/quản lý bean.

## 11. Các lỗi thường gặp và cách chẩn đoán đúng tầng

| Triệu chứng | Khả năng gây lỗi | Cách xác minh |
| --- | --- | --- |
| Field `@Autowired` vẫn null sau `new` | Object **ngoài Spring container** | Dùng constructor injection trong test hoặc lấy bean từ context |
| Field dùng ngay trong constructor dẫn đến NPE | Injection được thực hiện **sau construction** | Đưa dependency required vào constructor |
| Spring báo không có bean `Gateway` | Không đăng ký bean phù hợp trong context | Kiểm `@Bean`, scan, qualifier, context scope |
| Spring báo nhiều `Gateway` candidates | Type ambiguous | Dùng `@Primary`/`@Qualifier` rõ ràng |
| Setter không được gọi | Không có annotation/cấu hình inject, hoặc non-required method bị skip | Xác minh injection metadata và available candidates |
| Setter required fail lúc startup | Thiếu một dependency bắt buộc | Đọc `UnsatisfiedDependencyException` và `Caused by` |
| Multi-method bị skip dù có tham số A | Dùng `required=false` nhưng còn thiếu tham số B | Tất cả arguments phải được resolve để method được gọi |
| Muốn method chạy dù B vắng | Required `B` đang được biểu diễn sai | Dùng `Optional<B>`/provider hoặc default theo domain |
| Cùng một method chạy hai lần ngoài ý muốn | Test/caller có thể tự gọi thêm method; bean lifecycle/registration cần kiểm tra | Phân biệt lời gọi Java trực tiếp và lời gọi khi Spring inject |
| `@Autowired` static field không hoạt động | Static injection không phải cơ chế được hỗ trợ | Đặt dependency trong bean instance, bỏ global mutable state |
| Một private field trên Java module path bị chặn reflection | Module/package chưa cho phép reflective access | Xem `opens` và module setup; classpath demo không gặp vấn đề đó |
| `@Autowired(required=false)` mà code vẫn NPE | Đã bỏ qua field/method nhưng business không có default hợp lệ | Thiết kế optional thật, kiểm tra null/Optional hoặc dùng no-op implementation |
| Bean bị tạo quá sớm trước processor | Custom factory/post-processor làm lifecycle bất thường | Xem thứ tự registration và BeanPostProcessor |
| `mvn` không resolve `spring-context` | Artifact/repository/JDK/proxy | Kiểm POM GAV, `mvn -version`, Maven Central/mirror |

**Không nhầm thiếu bean với thiếu JAR:** thiếu package lúc compile là vấn đề build classpath; `NoSuchBeanDefinitionException` hay `UnsatisfiedDependencyException` trong Spring là vấn đề graph/configuration. Nếu factory method ném exception thì phải đọc nguyên nhân gốc, không chỉ nhìn nhãn “autowire error”.

### Lưu ý khi có XML hoặc cấu hình hỗn hợp

`@Autowired` hoạt động nhờ các bean post-processors. `AnnotationConfigApplicationContext` đăng ký processors thường dùng; một XML context cấu hình thủ công có thể cần bật annotation configuration, chẳng hạn `<context:annotation-config/>`. XML property injection có thể có thứ tự/ưu tiên riêng khi kết hợp annotation và explicit property values; tránh đặt hai cách cấu hình xung đột trên một property mà không có lý do rõ.

Các class **`BeanPostProcessor` và `BeanFactoryPostProcessor`** do bạn tự tạo cũng có lifecycle đặc biệt. Không giả định `@Autowired` trong chính những processor ấy được xử lý như bean ứng dụng thông thường; tài liệu Spring khuyến nghị cấu hình dependencies cho processors bằng factory method/XML rõ ràng.

## 12. Bài tập dự đoán, sau đó kiểm chứng

Giữ một bản source gốc để đối chiếu; với mỗi thử nghiệm, chỉnh trên bản sao và khôi phục code trước khi làm thử nghiệm tiếp theo.

1. **Bỏ `@Autowired` trên FieldClient:** dự đoán `base.field.ready` và điều gì xảy ra khi gọi `gatewayId()`. Vì sao `@Bean` tạo được FieldClient nhưng không có trường đã được inject?
2. **Thêm một setter thứ hai:** viết một method `setFormatter(Formatter)` có `@Autowired` trong SetterClient, thêm field và bộ đếm; kiểm tra method được gọi lúc bean creation.
3. **Đổi tên `MultiClient.configure` thành `prepareComponents`:** output logic giữ nguyên không? Vì sao tên method không quyết định khả năng autowiring?
4. **Xóa bean `Formatter`:** dự đoán MultiClient required method bị lỗi ở bước tạo bean, không nhận `null`.
5. **Chỉ thêm Telemetry vào context:** dự đoán `optionalMethodCalls=1` và `telemetryPresent=true` trong OptionalClient.
6. **Bỏ Telemetry nhưng vẫn muốn method chạy:** chuyển `attach(Gateway, Telemetry)` thành `attach(Gateway, Optional<Telemetry>)` và bỏ `required=false`; xử lý empty trong method.
7. **Thêm hai bean Gateway:** cố tình gây ambiguity, chọn một bằng `@Primary` hoặc đặt `@Qualifier("mainGateway")` trên **tham số method**.
8. **Cho Getter vào constructor:** refactor FieldClient để nhận Gateway qua constructor và giữ field `final`, viết test bằng Java `new FieldClient(fake)`. So sánh với field injection.
9. **Tạo bean qua component scan:** gắn `@Component` cho MultiClient, quét package thích hợp thay vì `@Bean` method, bảo đảm không có hai bean definitions trùng type.
10. **Non-required method có hai tham số:** đổi `attach` thành method có ba tham số, trong đó một type không có bean; xác nhận cả method được skip.
11. **Quan sát lifecycle:** thêm `InitializingBean.afterPropertiesSet()` vào MultiClient, kiểm tra các references đã có trước init callback.
12. **Dùng provider:** thay một optional parameter bằng `ObjectProvider<Telemetry>`, gọi `getIfAvailable()` để minh họa lookup theo nhu cầu.
13. **Pure Java comparison:** sửa `PureInjectionTypes` để không gọi `configure`, dự đoán `multiResult` lỗi vì thiếu state; Spring required injection giúp phát hiện lỗi wiring sớm hơn khi tạo bean.
14. **Thử hai contexts:** tạo hai context BaseConfig độc lập, so reference `FieldClient`; Spring singleton là phạm vi theo bean definition/container, không phải một instance trên toàn JVM.

### Checklist hiểu bài

- [ ] Field injection và setter/method injection diễn ra **sau constructor** với Spring-managed bean.
- [ ] Setter là một trường hợp đặc biệt của autowired configuration method.
- [ ] Arbitrary method có thể có **tên tùy ý và nhiều tham số**.
- [ ] Multi-argument method required đòi resolve được các required args.
- [ ] Non-required method **bị skip toàn bộ** nếu không thỏa một dependency cần thiết; không nhận một nửa đối số.
- [ ] `Optional<T>` ở tham số diễn đạt vắng dependency theo cách khác với `required=false` trên method.
- [ ] Biết dùng qualifiers khi có nhiều candidates cùng type.
- [ ] Không nhầm `new` ngoài context với bean được Spring quản lý.
- [ ] Dự đoán chính xác 18 stdout lines của Maven demo và 4 dòng của Java thuần.
- [ ] Biết chọn constructor injection cho required dependencies và không lạm dụng mutable setter.

## 13. Tổng kết và tài liệu chính thức

**Tổng kết ngắn gọn:** Spring hỗ trợ tiêm dependency vào **field**, **setter** và **phương thức nhiều tham số** thông qua cùng cơ chế `@Autowired` trên bean thuộc container. Sự khác biệt không chỉ ở cú pháp:
- Field injection gán trường trực tiếp sau construction.
- Setter injection là lời gọi method cấu hình một dependency (thông thường).
- Multi-argument method injection resolve nhiều dependencies trước khi gọi một method tùy tên.
- Non-required method được phép bỏ qua **toàn bộ lời gọi** khi thiếu một dependency bắt buộc, còn `Optional<T>` cho phép method chạy với tham số optional ở trạng thái empty.
- Hãy lựa chọn cơ chế theo hợp đồng của object và khả năng kiểm thử, không chỉ theo số dòng code.

Nguồn tài liệu:
- [Spring Framework – Using @Autowired](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html)
- [Spring Framework – Annotation-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config.html)
- [Spring Framework – Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)
- [Spring Framework – Fine-tuning Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html)
- [Spring API – AutowiredAnnotationBeanPostProcessor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/annotation/AutowiredAnnotationBeanPostProcessor.html)
- [Spring – Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)
- [Java 17 – Optional API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Optional.html)

**Ghi nhớ:** Annotation không thay thế bean registration. Hãy luôn hỏi **đối tượng có do Spring quản lý không**, **injection point yêu cầu những tham số nào**, **thiếu tham số thì fail hay skip method**, và **dependency có thực sự tùy chọn không**. Trả lời bốn câu hỏi này giúp bạn sử dụng field/setter/multi-parameter injection có chủ đích và debug đúng khi cấu hình thay đổi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="autowired-005">Ý nghĩa required và thời điểm phụ thuộc được giải quyết</a>

<details>
<summary>Xem chi tiết</summary>

# Ý nghĩa `required` và thời điểm phụ thuộc được giải quyết trong `@Autowired`

## 1. Câu hỏi trung tâm

Khi Spring Core Container tạo một bean, nó có thể phải tìm các bean khác để tiêm vào constructor, field hoặc phương thức cấu hình. Hai điều cần phân biệt là:

1. **Dependency có bắt buộc không?** Thuộc tính `required` của `@Autowired` ảnh hưởng đến việc Spring có chấp nhận một injection point không có dependency phù hợp hay không.
2. **Spring giải quyết dependency ở thời điểm nào?** Thời điểm này phụ thuộc vào quá trình **tạo bean**, scope, lazy initialization, loại injection point và các cơ chế trì hoãn được khai báo — không đồng nghĩa với mỗi lần business method được gọi.

Quy tắc chung:

~~~java
@Autowired
public void configure(Repository repository) {
    this.repository = repository;
}
~~~

`@Autowired` mặc định tương đương `@Autowired(required = true)`. Spring tìm bean `Repository` thỏa dependency rồi gọi method trong quá trình cấu hình bean. Nếu dependency bắt buộc không thể giải quyết, quá trình tạo bean **thường thất bại**.

Còn:

~~~java
@Autowired(required = false)
public void setAuditSink(AuditSink auditSink) {
    this.auditSink = auditSink;
}
~~~

cho phép Spring **bỏ qua lời gọi setter** nếu không có bean phù hợp, để trạng thái mặc định của đối tượng tiếp tục có hiệu lực. Đây là ví dụ **minh họa**; toàn bộ kiểu Java và cấu hình chạy được nằm ở mục 6.

~~~text
Đăng ký BeanDefinition
        |
ApplicationContext.refresh()
        |
        +-- các singleton non-lazy: thường tạo ở startup
        |        |
        |        +-- chọn constructor / giải quyết constructor args
        |        +-- gọi constructor hoặc factory method
        |        +-- resolve @Autowired fields/methods
        |        +-- chạy init callbacks thích hợp
        |
        +-- lazy singleton: trì hoãn nếu không bị bean eager khác yêu cầu
        |
        +-- prototype: thường tạo/tiêm khi container được yêu cầu cấp instance
        |
getBean(...) -> lấy managed bean, hoặc kích hoạt tạo nếu chưa có
        |
gọi method nghiệp vụ -> dùng dependency đã tiêm
~~~

Bài này tập trung vào Spring Framework 6.x / Java 17+, giúp bạn giải thích chính xác lỗi thiếu bean, không nhầm `required=false` với việc đưa `null` tùy tiện, và không hiểu sai thời điểm injection.

## 2. `required=true`: dependency tại injection point là điều kiện bắt buộc

### 2.1. Giá trị mặc định

Annotation khai báo:

~~~java
@Autowired
private PaymentGateway gateway;
~~~

nghĩa là Spring phải tìm cách giải quyết field `PaymentGateway` khi cấu hình managed bean. Nếu không tìm được ứng viên phù hợp, lỗi thiếu dependency có thể xuất hiện ở thời điểm bean được tạo, ví dụ `UnsatisfiedDependencyException` với nguyên nhân thiếu bean.

Viết:

~~~java
@Autowired(required = true)
private PaymentGateway gateway;
~~~

về mặt yêu cầu bắt buộc tương đương với cách viết không chỉ định thuộc tính. Hai khối chỉ minh họa; `PaymentGateway` và class chứa field cần được định nghĩa thêm để biên dịch riêng.

**Bắt buộc** ở đây không có nghĩa “Spring chắc chắn tìm được một bean” mà là “nếu không đáp ứng điều kiện injection, bean đang tạo không thể hoàn tất bình thường”. Cần cả đăng ký bean và khả năng lựa chọn một ứng viên thích hợp.

### 2.2. Có bean nhưng không phân giải được vẫn lỗi

Spring giải quyết một dependency đơn theo type, sau đó có thể xét `@Qualifier`, `@Primary`, tên tham số và những quy tắc lựa chọn khác khi thích hợp. Các tình huống thường gặp:

| Tình huống | Hành vi thường gặp |
| --- | --- |
| Có đúng một bean phù hợp | Inject bean đó |
| Không có bean phù hợp, injection bắt buộc | Bean creation thất bại |
| Hai bean cùng type, không phân giải duy nhất | Có thể gặp `NoUniqueBeanDefinitionException` |
| Bean cần inject tồn tại nhưng constructor/factory ném lỗi | Bean creation thất bại theo lỗi gốc |
| Có bean, nhưng không tương thích qualifier cần thiết | Không có ứng viên phù hợp với injection point |
| Có vòng phụ thuộc constructor không giải quyết được | Bean graph không được tạo hoàn chỉnh |

`@Autowired(required=true)` không tự khắc phục tình huống có nhiều ứng viên hoặc vòng phụ thuộc. Đây là vấn đề **bean graph và metadata lựa chọn**, không phải vấn đề annotation bị viết thiếu chữ.

### 2.3. Lỗi có thể đến trước `getBean`

Một `ApplicationContext` mặc định thường **pre-instantiate singleton beans không lazy** trong `refresh()`. Nếu bean A có field `@Autowired` bắt buộc nhưng thiếu dependency B, context có thể fail **ngay khi khởi động**, trước khi bạn gọi `context.getBean(A.class)`.

Do đó câu “Spring chỉ tìm dependency khi gọi `getBean`” là **không chính xác** đối với tất cả trường hợp. Nó có thể đúng với một bean lazy chưa bị bean khác yêu cầu, hoặc prototype được yêu cầu sau này, nhưng không phải quy tắc chung.

## 3. `required=false` trên field và method có nghĩa khác gì?

### 3.1. Field optional

~~~java
private AuditSink audit = AuditSink.NO_OP;

@Autowired(required = false)
private AuditSink injectedAudit;
~~~

Đây chỉ là **minh họa**: `AuditSink.NO_OP` chưa định nghĩa. Field `injectedAudit` được phép không được inject khi không có candidate. Nếu không có bean phù hợp, Spring thường để field **nguyên trạng mặc định**; nó không nhất thiết “gọi setter với null”, vì trường hợp này không có setter.

Nếu code dùng `injectedAudit.record(...)` vô điều kiện trong khi field vẫn null, bạn vẫn có thể gặp `NullPointerException`. Đánh dấu optional là một **quyết định thiết kế**, phải có hành vi khi dependency vắng mặt.

Nếu muốn default an toàn, có thể tạo Null Object:

~~~java
private AuditSink audit = id -> { /* Không làm gì */ };

@Autowired(required = false)
public void setAudit(AuditSink audit) {
    this.audit = java.util.Objects.requireNonNull(audit);
}
~~~

Khi Spring không tìm được `AuditSink` thì setter không chạy và field `audit` tiếp tục giữ no-op implementation. Khi có bean, setter thay implementation mặc định. Trong project thực tế, cần tự định nghĩa `AuditSink` là functional interface phù hợp.

### 3.2. Phương thức optional: bỏ qua **toàn bộ** lời gọi

Giả sử:

~~~java
@Autowired(required = false)
public void configureMonitoring(AuditSink audit,
                                MetricSink metrics) {
    this.audit = audit;
    this.metrics = metrics;
    this.monitoringEnabled = true;
}
~~~

Đây là trích đoạn minh họa; file chương trình chính ở mục 6 dùng một setter có một tham số để dễ quan sát. Với một method nhận **hai** tham số như đoạn trên, `required=false` áp dụng cho **cả injection point**:

- Có đủ hai dependency thích hợp: Spring có thể gọi method với cả hai.
- Thiếu một dependency: Spring **bỏ qua cả method**, chứ không tự gọi “nửa method” hoặc truyền `null` vào parameter thiếu.
- Field `monitoringEnabled` tiếp tục giữ default ban đầu nếu method bị bỏ qua.

Đây là một lý do không nên gom các option **độc lập** vào cùng một method `required=false`. Nếu audit và metrics có thể được bật độc lập, dùng hai setter optional riêng hoặc tham số `Optional<T>` phù hợp.

### 3.3. Optional **không** có nghĩa nuốt mọi lỗi

`required=false` không phải bộ xử lý exception:

- Nếu một bean candidate có tồn tại nhưng quá trình khởi tạo bean ấy ném exception, lỗi có thể khiến context không tạo được bean.
- Nếu method optional được gọi và body method ném `IllegalArgumentException`, lỗi đó không bị Spring bỏ qua đơn giản vì annotation là non-required.
- Nếu có nhiều candidates và không thể chọn duy nhất, đó là lỗi lựa chọn ứng viên, không phải tình huống “không cần bean nào cả”.
- Một dependency khác ngoài injection point optional có thể vẫn là bắt buộc.

Vì thế không nên sửa mọi `UnsatisfiedDependencyException` bằng `required=false`. Cần xác định dependency thật sự có thể vắng mặt mà object vẫn hoạt động đúng hay không.

## 4. `required` trên constructor: cần hiểu quy tắc lựa chọn

### 4.1. Chỉ một constructor: không cần `@Autowired`

Từ Spring Framework 4.3 trở đi, một managed bean class chỉ có **một constructor** thường không cần ghi `@Autowired` trên constructor đó:

~~~java
public final class ReportService {
    private final Repository repository;

    public ReportService(Repository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }
}
~~~

Đây là đoạn minh họa; bean `Repository` phải có sẵn. Nếu Spring trực tiếp khởi tạo `ReportService` từ bean definition, nó có thể chọn constructor duy nhất rồi resolve `Repository`. **Không gắn annotation không có nghĩa dependency trở thành tùy chọn.**

### 4.2. Nhiều constructor

Với một class có nhiều constructor, `@Autowired` giúp Spring xác định candidates. Không thể tùy tiện gắn `@Autowired(required=true)` lên nhiều constructor khác nhau và kỳ vọng Spring tự chọn:

- Mặc định, **chỉ một constructor** trong class có thể được đánh dấu `@Autowired(required=true)`.
- Với nhiều constructor được đánh dấu `@Autowired(required=false)`, Spring có thể xét các candidates theo khả năng thỏa argument và chọn constructor phù hợp nhất theo quy tắc.
- Nếu không constructor có annotation nào được thỏa và có constructor không tham số, constructor mặc định có thể làm fallback trong những điều kiện được Spring hỗ trợ.
- Có những quy tắc suy luận constructor đặc biệt cho bean definitions và Java records; đừng coi “Spring luôn chọn constructor dài nhất” là định luật áp dụng mọi class.

**Quan trọng:** `@Autowired(required=false)` đặt trên constructor không có nghĩa Spring tự truyền null vào bất cứ argument thiếu nào. Nó ảnh hưởng **cách đánh giá constructor candidates**, khác với setter method non-required bị bỏ qua khi thiếu dependency.

### 4.3. Dependency bắt buộc nên hiện rõ

Nếu service cần repository để hoạt động, hãy dùng constructor nhận repository và không tạo một constructor no-arg chỉ để tránh lỗi startup. Object không có dependency bắt buộc sẽ tồn tại nhưng có thể ném lỗi khi business method chạy — thường còn khó debug hơn.

Nếu dependency thật sự tùy chọn, cân nhắc `Optional<T>`, `@Nullable` phù hợp, provider hoặc setter optional thay vì làm class hợp lệ trong trạng thái thiếu invariant.

## 5. Tùy chọn theo **tham số** thay vì cả method

### 5.1. `Optional<T>`

Ví dụ:

~~~java
@Autowired
public void configure(Repository repository,
                      java.util.Optional<AuditSink> audit) {
    this.repository = repository;
    this.audit = audit.orElse(null);
}
~~~

Đây là snippet cần field/type tương ứng nếu muốn biên dịch. Khi repository có sẵn mà `AuditSink` không có, Spring có thể truyền `Optional.empty()` cho tham số audit và **vẫn gọi method**, khác với `@Autowired(required=false)` áp dụng lên toàn method nhiều tham số.

| Cấu hình injection | Thiếu audit | Có audit |
| --- | --- | --- |
| `@Autowired(required=false) setAudit(AuditSink)` | Không gọi setter | Gọi setter |
| `@Autowired(required=false) configure(Repository, AuditSink)` | Bỏ qua toàn method | Gọi nếu mọi tham số thỏa |
| `@Autowired configure(Repository, Optional<AuditSink>)` | Gọi với `Optional.empty()` nếu repo có | Gọi với `Optional.of(bean)` |
| Constructor `Service(Repository, Optional<AuditSink>)` | Constructor vẫn có thể được gọi, audit rỗng | Constructor nhận audit hiện có |

`Optional<T>` mô tả điều kiện **theo từng tham số**, trong khi `required` là thuộc tính **của annotation injection point**.

### 5.2. `@Nullable`

Khi annotation nullability được Spring nhận biết ở phiên bản và package phù hợp, một dependency parameter có thể được phép nhận null. **Không phải mọi annotation có chữ Nullable từ bất kỳ thư viện nào đều mặc nhiên tương đương**; dự án cần dùng đúng annotation mà Spring 6.x hỗ trợ. Đối với người mới học, `Optional<T>` thường giúp ý nghĩa rõ hơn so với dựa vào null.

### 5.3. `ObjectProvider<T>`

Nếu collaborator cần được giải quyết **lúc xử lý**, có thể inject Spring `ObjectProvider<T>` rồi gọi `getObject()` hoặc `getIfAvailable()` trong những tình huống thích hợp:

~~~java
public final class ExportService {
    private final org.springframework.beans.factory.ObjectProvider<Exporter>
            exporters;

    public ExportService(
            org.springframework.beans.factory.ObjectProvider<Exporter>
                    exporters) {
        this.exporters = exporters;
    }

    public void export() {
        Exporter exporter = exporters.getIfAvailable();
        if (exporter != null) {
            exporter.run();
        }
    }
}
~~~

Đây là **minh họa** chưa định nghĩa `Exporter`. `ObjectProvider` cho phép trì hoãn **lần lookup cụ thể**, nhưng không miễn trừ mọi lỗi do nhiều ứng viên mơ hồ hoặc factory candidate gặp lỗi. Với scope prototype, lookup từ provider có thể tạo instance mới theo mỗi yêu cầu.

Nếu nghiệp vụ cần hoàn toàn độc lập với Spring, một interface `ExporterFactory` hoặc `Supplier<Exporter>` được injected thường giữ ranh giới dependency sạch hơn.

## 6. Ví dụ Spring Framework 6.2 / Java 17: quan sát required và timing

Chương trình console hoàn chỉnh dùng hai context độc lập:

- **Context thứ nhất** có `TicketStore` nhưng **không có `AuditSink`**. Service có constructor `TicketStore` bắt buộc và setter `@Autowired(required=false)` cho audit; setter bị bỏ qua. Một singleton `@Lazy` chưa được tạo sau refresh; khi gọi `getBean(LazySummary.class)` thì Spring tạo nó.
- **Context thứ hai** có cả `TicketStore` và `AuditSink`. Setter audit được gọi trong quá trình tạo service, và method nghiệp vụ ghi audit trước khi trả kết quả.

Ứng dụng không dùng Spring Boot, HTTP server, database hoặc AOP. Nó minh họa **các thời điểm mà bạn có thể quan sát được**, không hứa mọi singleton không liên quan khởi tạo theo thứ tự trong source code.

### 6.1. Yêu cầu và cấu trúc thư mục

Cần **JDK 17+**, Apache Maven. Maven có thể tải dependencies khi chạy lần đầu.

~~~text
required-timing-demo/
├── pom.xml
└── src/main/java/demo/RequiredTimingDemo.java
~~~

Tạo `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>required-timing-demo</artifactId>
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
                    <mainClass>demo.RequiredTimingDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 6.2. File Java đầy đủ

Tạo `src/main/java/demo/RequiredTimingDemo.java`:

~~~java
package demo;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;

public final class RequiredTimingDemo {
    private RequiredTimingDemo() {}

    public interface TicketStore {
        String find(String id);
    }

    public static final class MemoryTicketStore implements TicketStore {
        @Override
        public String find(String id) {
            return "TICKET(" + id + ")";
        }
    }

    public interface AuditSink {
        void record(String id);
    }

    public static final class ConsoleAuditSink implements AuditSink {
        @Override
        public void record(String id) {
            System.out.println("AUDIT " + id);
        }
    }

    public static final class TicketService {
        private final TicketStore store;  // Bắt buộc
        private AuditSink audit;          // Tùy chọn
        private boolean auditSetterCalled;

        public TicketService(TicketStore store) {
            this.store = Objects.requireNonNull(store, "store");
        }

        @Autowired(required = false)
        public void setAudit(AuditSink audit) {
            this.audit = Objects.requireNonNull(audit, "audit");
            this.auditSetterCalled = true;
        }

        public boolean auditConfigured() {
            return auditSetterCalled;
        }

        public String process(String id) {
            String ticket = store.find(id);
            if (audit != null) {
                audit.record(id);
            }
            return "DONE " + ticket;
        }
    }

    public static final class LazySummary {
        private final TicketStore store;

        public LazySummary(TicketStore store) {
            this.store = Objects.requireNonNull(store);
        }

        public String summarize(String id) {
            return "SUMMARY " + store.find(id);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class CoreConfig {
        @Bean
        public TicketStore ticketStore() {
            return new MemoryTicketStore();
        }

        @Bean
        public TicketService ticketService(TicketStore store) {
            return new TicketService(store);
        }

        @Bean
        @Lazy
        public LazySummary lazySummary(TicketStore store) {
            System.out.println("CREATE lazy-summary");
            return new LazySummary(store);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreConfig.class)
    public static class WithAuditConfig {
        @Bean
        public AuditSink auditSink() {
            return new ConsoleAuditSink();
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(CoreConfig.class)) {
            System.out.println("-- without audit --");
            System.out.println("LAZY_READY "
                    + context.getBeanFactory()
                             .containsSingleton("lazySummary"));

            TicketService service = context.getBean(TicketService.class);
            System.out.println("AUDIT_SET " + service.auditConfigured());
            System.out.println("RESULT " + service.process("A1"));

            LazySummary lazy = context.getBean(LazySummary.class);
            System.out.println(lazy.summarize("A1"));
            System.out.println("LAZY_READY "
                    + context.getBeanFactory()
                             .containsSingleton("lazySummary"));
        }

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             WithAuditConfig.class)) {
            System.out.println("-- with audit --");
            TicketService service = context.getBean(TicketService.class);
            System.out.println("AUDIT_SET " + service.auditConfigured());
            System.out.println("RESULT " + service.process("B2"));
        }
    }
}
~~~

Không có `@Autowired` trên constructor của `TicketService` vì nó chỉ có một constructor, nhưng chương trình còn sử dụng **`@Bean ticketService(TicketStore)`**: chính Spring inject tham số vào factory method; body factory gọi Java constructor. Điều đó vẫn cho dependency bắt buộc, nhưng không nên nhầm với Spring trực tiếp suy luận constructor của service class.

### 6.3. Biên dịch, chạy và stdout

Mở terminal tại thư mục `required-timing-demo`:

~~~bash
mvn -version
mvn -q compile exec:java
~~~

**Các dòng do chương trình in qua `System.out.println` theo đúng thứ tự:**

~~~text
-- without audit --
LAZY_READY false
AUDIT_SET false
RESULT DONE TICKET(A1)
CREATE lazy-summary
SUMMARY TICKET(A1)
LAZY_READY true
-- with audit --
AUDIT_SET true
AUDIT B2
RESULT DONE TICKET(B2)
~~~

`mvn -version` in phiên bản Maven/JDK; Maven và Spring cũng có thể có log khác tùy môi trường. Khối trên chỉ ghi các dòng **do Java code chủ động in**, không khẳng định toàn bộ terminal chỉ gồm từng ấy dòng.

### 6.4. Truy vết thời điểm Spring giải quyết dependency

**Context không có audit:**

1. `AnnotationConfigApplicationContext(CoreConfig.class)` đăng ký config và **refresh** context. Những singleton non-lazy, trong đó có `TicketStore` và `TicketService`, thường đã được tạo trước khi constructor này trả về.
2. Khi tạo `ticketService`, Spring phải resolve tham số `TicketStore` của `@Bean` factory method. Đây là dependency **bắt buộc**; nếu thiếu, startup không thể hoàn tất như demo.
3. Bean `TicketService` được tạo, sau đó Spring xử lý setter `@Autowired(required=false)`. Không có bean `AuditSink`, nên setter **không được gọi**, cờ `auditSetterCalled` giữ `false`.
4. Bean `LazySummary` khai báo `@Lazy` và không bị một bean eager khác yêu cầu; sau refresh, `containsSingleton("lazySummary")` là `false`.
5. `getBean(TicketService.class)` trả service đã cấu hình. `process("A1")` chỉ dùng dependency đã được tiêm và không làm Spring chạy lại setter. Nó in `RESULT DONE TICKET(A1)`.
6. Khi `getBean(LazySummary.class)`, Spring phải tạo lazy bean. Factory method in `CREATE lazy-summary`, rồi Spring trả object. Lệnh `summarize("A1")` trả `SUMMARY TICKET(A1)`.
7. Từ đây singleton `lazySummary` đã tồn tại, nên `containsSingleton("lazySummary")` trả `true`.

**Context có audit:**

1. `WithAuditConfig` import `CoreConfig` và đăng ký thêm bean `AuditSink`. Spring có thể resolve cả repository và audit khi cấu hình `TicketService`.
2. Setter `setAudit(AuditSink)` được gọi trong bean creation; `auditSetterCalled` trở thành `true` **trước khi** `main` gọi `getBean(TicketService.class)`.
3. `process("B2")` gọi `audit.record("B2")`, in `AUDIT B2`, rồi trả `DONE TICKET(B2)` cho `main` in.
4. Mỗi context có bộ singleton riêng. Không suy từ hai lần chạy rằng Spring giữ một global singleton cho toàn JVM.

**Không có stdout về thời điểm tạo ticketStore/ticketService**, vì constructor/factory chúng không in gì. Như vậy output có ý nghĩa ổn định, không dựa vào thứ tự Spring tạo các singleton không liên quan.

## 7. Eager, lazy, prototype: “lúc nào” phải phụ thuộc loại bean

| Kiểu bean | Thời điểm Spring thường tạo và resolve dependency |
| --- | --- |
| Singleton non-lazy | Khi ApplicationContext refresh/pre-instantiation (nếu không có điều kiện khác làm trì hoãn) |
| Singleton `@Lazy` | Lần được yêu cầu đầu tiên, trừ khi dependency eager khác buộc phải tạo sớm |
| Prototype | Mỗi lần container được yêu cầu cấp prototype instance, theo cơ chế scope |
| Request/session scoped | Khi scope web hợp lệ có yêu cầu tương ứng; cần web-aware context |
| Bean được tạo bằng `new` ngoài Spring | Spring không tự xử lý `@Autowired` của instance đó |
| `ObjectProvider<T>` injection | Provider có thể được cấp trước; dependency đích được lookup khi provider được dùng |
| Proxy do `@Lazy` tại injection point | Proxy có thể được inject trước; target được tìm khi proxy cần giải quyết target |

### 7.1. `@Lazy` trên bean definition có thể bị dependency eager đánh thức

Ví dụ:

~~~java
@Bean
@Lazy
ExternalClient externalClient() {
    return new ExternalClient();
}

@Bean
EagerService eagerService(ExternalClient client) {
    return new EagerService(client);
}
~~~

Đây là **minh họa**, `ExternalClient` và `EagerService` cần định nghĩa. Nếu Spring cần tạo `eagerService` trong startup, nó phải resolve tham số client. Kết quả: **`externalClient` có thể được tạo trong startup dù có `@Lazy`**.

Vì vậy câu “mọi bean đánh dấu lazy chỉ được tạo khi lần đầu `getBean` đúng tên bean đó” là sai.

### 7.2. `@Lazy` tại injection point có thể tạo proxy

Trong những tình huống Spring hỗ trợ, đặt `@Lazy` tại tham số/field injection có thể tạo **proxy trì hoãn resolution**:

~~~java
public EagerService(@Lazy ExternalClient client) {
    this.client = client;
}
~~~

Đây là snippet minh họa. Object được inject có thể là proxy, còn việc xác định và tạo target thật có thể dời tới lần dùng đầu. Tuy nhiên:

- Không phải mọi class/method có thể được proxy như nhau (nhất là `final` class/method và lựa chọn proxy).
- Lỗi missing bean có thể dời sang runtime use của proxy.
- Không nhầm “constructor đã nhận một proxy” với “constructor thật sự không nhận dependency nào”.
- Đừng tùy tiện đánh dấu toàn bộ injection points là lazy để che vòng dependency.

### 7.3. Prototype được inject vào singleton

Nếu một bean prototype được inject **trực tiếp** vào singleton qua constructor, việc lấy prototype thường diễn ra **khi singleton được tạo**. Singleton giữ reference ấy; nó **không tự nhận prototype mới mỗi lần method nghiệp vụ chạy**.

Muốn một instance prototype cho mỗi tác vụ, sử dụng `ObjectProvider<T>`, scoped proxy thích hợp hoặc một injected factory với hợp đồng rõ ràng. Prototype cũng có vòng đời dọn dẹp khác singleton: Spring không tự chạy mọi destruction callback khi context đóng cho từng prototype đã cấp.

### 7.4. Khi nào “dependency đã được giải quyết” thực sự xảy ra?

Có thể tách bốn thời điểm:

1. **Đăng ký bean definitions**: Spring biết có bean nào và metadata nào. Chưa đồng nghĩa object đã tồn tại.
2. **Tạo instance**: chọn constructor/factory, resolve những dependency cần ngay để gọi constructor/factory.
3. **Populate/inject properties**: xử lý `@Autowired` field và method, property values cùng các bước lifecycle liên quan.
4. **Business method execution**: người dùng gọi hành vi trên object đã sẵn sàng. Dependency cố định thường chỉ được **sử dụng**, không được tự động lookup lại.

Những thứ như proxy, provider, prototype factory có thể thay đổi hoặc trì hoãn một phần resolution, nhưng nên được coi là **cấu hình riêng**, không làm mất quy tắc mặc định.

## 8. Một ví dụ Java 17 thuần để phân biệt injection với lookup động

Không cần Spring để hiểu rằng một dependency đã được cấp qua constructor sẽ được giữ lại ở field.

Tạo file `PlainRequiredDemo.java` tại thư mục làm việc độc lập:

~~~java
import java.util.Objects;
import java.util.function.Supplier;

public class PlainRequiredDemo {
    interface Formatter {
        String format(String value);
    }

    static class Service {
        private final Formatter formatter;

        Service(Formatter formatter) {
            this.formatter = Objects.requireNonNull(formatter);
        }

        String run(String value) {
            return formatter.format(value);
        }
    }

    static class DynamicService {
        private final Supplier<Formatter> provider;

        DynamicService(Supplier<Formatter> provider) {
            this.provider = Objects.requireNonNull(provider);
        }

        String run(String value) {
            return provider.get().format(value);
        }
    }

    public static void main(String[] args) {
        Formatter fixed = value -> "FIXED:" + value;
        Service service = new Service(fixed);
        System.out.println(service.run("A"));
        System.out.println(service.run("B"));

        int[] calls = {0};
        DynamicService dynamic = new DynamicService(() -> {
            calls[0]++;
            return value -> "DYNAMIC:" + value;
        });
        System.out.println(dynamic.run("C"));
        System.out.println(dynamic.run("D"));
        System.out.println("LOOKUPS " + calls[0]);
    }
}
~~~

Biên dịch và chạy với **JDK 17+** (không cần Spring):

~~~bash
javac --release 17 -encoding UTF-8 PlainRequiredDemo.java
java PlainRequiredDemo
~~~

Stdout chính xác:

~~~text
FIXED:A
FIXED:B
DYNAMIC:C
DYNAMIC:D
LOOKUPS 2
~~~

Trong `Service`, dependency `fixed` đã được truyền vào constructor một lần. Hai lời gọi `run` **không gọi lại nơi cung cấp formatter**. Ngược lại, `DynamicService` gọi `provider.get()` mỗi lần run, vì code của chúng ta **thiết kế rõ** như vậy.

Đây là analog thuần Java giúp hiểu `ObjectProvider` hoặc factory abstraction trong Spring: lookup theo nhu cầu là một lựa chọn API, **không phải mặc định của mọi lần gọi method trên một managed bean**.

## 9. Xử lý thiếu bean đúng cách, không che vấn đề

### 9.1. Dependency thật sự bắt buộc

Giả sử `TicketService` không thể làm việc khi thiếu `TicketStore`. Nên để constructor hoặc `@Bean` factory method yêu cầu bean ấy. Nếu không có, startup thất bại sớm đối với eager singleton là tín hiệu cấu hình sai cần sửa.

Không chuyển sang:

~~~java
@Autowired(required = false)
public void setStore(TicketStore store) {
    this.store = store;
}
~~~

rồi dùng `store.find(...)` vô điều kiện nếu setter không chạy. Điều này chỉ **dời lỗi** tới thời điểm nghiệp vụ, không giải quyết dependency bắt buộc.

### 9.2. Dependency thật sự tùy chọn

Audit, telemetry, tracing hoặc một tối ưu cache phụ trợ có thể không ảnh hưởng chức năng chính. Có thể dùng setter optional, `Optional<T>` hoặc Null Object default, với điều kiện:

- Có test cho trường hợp dependency tồn tại.
- Có test cho trường hợp dependency vắng mặt.
- Hành vi fallback được ghi rõ.
- Không bỏ qua yêu cầu pháp lý/bảo mật thực ra đòi audit bắt buộc.

Ví dụ `TicketService` trong demo minh họa hành vi tiếp tục xử lý khi không có `AuditSink`, không có `NullPointerException`.

### 9.3. Hai beans cùng type không phải “optional”

Nếu đã đăng ký hai `AuditSink` bean với cùng interface và không có quy tắc chọn duy nhất, kết quả không trở thành “không có audit”. Cần lựa chọn có chủ đích: `@Primary`, `@Qualifier`, injection collection hoặc strategy/router theo runtime data.

### 9.4. Lỗi lazy xảy ra muộn cũng vẫn là lỗi cấu hình

Một bean đánh dấu `@Lazy` có thể làm startup hoàn tất nhưng lỗi thiếu dependency chỉ xuất hiện ở `getBean` đầu tiên. Điều đó không có nghĩa `required=true` bị vô hiệu; **điều kiện bắt buộc chỉ được kiểm tra khi bean thật sự được tạo**.

Hãy có integration tests hoặc startup checks thích hợp nếu muốn biết sớm tính đầy đủ của object graph quan trọng.

## 10. Quan hệ với `@Bean` factory method và XML

### 10.1. Factory method parameters được Spring resolve

~~~java
@Bean
TicketService ticketService(TicketStore store) {
    return new TicketService(store);
}
~~~

Đây là đoạn trích từ chương trình chạy. Spring xác định `@Bean` method là một bean factory method và tự giải quyết `TicketStore` parameter. Không cần đặt `@Autowired` lên chính `@Bean` method chỉ để nó nhận các tham số.

Đối với class `TicketService` do method trả về, setter `@Autowired(required=false)` vẫn được bean post-processor xem xét vì nó là Spring-managed bean.

**Cần tách:** resolution của factory parameters xảy ra **trước** khi gọi factory; injection của setters/fields trên sản phẩm xảy ra **sau** khi sản phẩm được tạo và trước khi bean được sử dụng bình thường.

### 10.2. XML bean dependencies

Một cấu hình XML có thể khai báo constructor dependency:

~~~xml
<bean id="ticketStore" class="demo.MemoryTicketStore"/>

<bean id="ticketService" class="demo.TicketService">
    <constructor-arg ref="ticketStore"/>
</bean>
~~~

Đây là **minh họa**; class Java trong chương trình Maven là nested classes bên trong `demo.RequiredTimingDemo` nên không dùng được tên `demo.MemoryTicketStore` của snippet này một cách trực tiếp. Trong project thật, sử dụng fully qualified class names đúng hoặc tách thành top-level classes.

`<constructor-arg ref="ticketStore"/>` là reference bắt buộc theo metadata: nếu tên bean tham chiếu không tồn tại, tạo bean sẽ lỗi. Không có thuộc tính `required=false` trên mọi `<constructor-arg>` có cùng semantics với `@Autowired(required=false)`; không nên cố dịch một-một.

## 11. Các hiểu nhầm thường gặp

| Hiểu nhầm | Điều đúng |
| --- | --- |
| “Không ghi `required` thì dependency optional.” | Mặc định `@Autowired(required=true)`. |
| “`required=false` sẽ gọi setter với `null`.” | Với method non-required thiếu dependency, Spring có thể **không gọi method**. |
| “`required=false` xử lý cả bean ứng viên bị lỗi khi tạo.” | Không: lỗi factory/constructor vẫn có thể phá bean creation. |
| “Mọi field optional đều có giá trị hợp lệ nếu không inject.” | Nó giữ default, có thể là null; object cần fallback an toàn. |
| “Constructor `required=false` giống setter `required=false`.” | Constructor resolution và method injection có quy tắc khác nhau. |
| “Một constructor không ghi `@Autowired` nghĩa là không DI.” | Managed bean có một constructor vẫn có thể được inject tự động. |
| “Spring resolve dependency ở mọi lần gọi business method.” | Thường resolution/injection xảy ra lúc bean được tạo; method chỉ sử dụng dependency đã có. |
| “Dependency chỉ được tìm lúc `getBean`.” | Eager singleton thường được tạo khi context refresh. |
| “`@Lazy` tuyệt đối ngăn bean tạo lúc startup.” | Eager dependency có thể buộc bean lazy khởi tạo sớm. |
| “`@Lazy` trên field giống `@Lazy` trên bean definition.” | Vị trí đặt có thể tạo lazy proxy và thời điểm resolution khác. |
| “Inject prototype vào singleton giúp có prototype mới mỗi request.” | Singleton thường giữ một instance đã được inject. |
| “`@Autowired` trên class tự `new` sẽ được JVM thực thi.” | Không, nó cần Spring-managed lifecycle/processor. |
| “`@Primary` giải quyết thiếu bean.” | Primary giúp ưu tiên khi nhiều bean, không tạo bean còn thiếu. |
| “Mọi bean singleton được tạo theo thứ tự khai báo trong source.” | Không dựa vào thứ tự source; chỉ dựa vào dependency/lifecycle semantics. |
| “Context refresh thành công chứng minh mọi lazy bean hợp lệ.” | Lazy bean có thể chỉ được tạo/kiểm tra khi được yêu cầu. |
| “`ObjectProvider` luôn trả một bean hay null, không thể lỗi.” | Nó còn có quy tắc thiếu/đa ứng viên và lỗi tạo bean. |

## 12. Bài tập kèm đáp án

**Bài 1.** `@Autowired` không ghi `required` là true hay false?

**Đáp án:** Mặc định **true**. Dependency tại injection point cần được đáp ứng nếu không có một hình thức optionality khác được hỗ trợ.

**Bài 2.** Method `@Autowired(required=false) void configure(Audit a, Metrics m)` có audit nhưng thiếu metrics. Nó có được gọi với metrics null không?

**Đáp án:** Không trong semantics của method non-required: Spring có thể bỏ qua **toàn bộ method**. Nếu audit và metrics cần tùy chọn riêng, tách điểm tiêm hoặc dùng `Optional<T>`.

**Bài 3.** Bean singleton eager thiếu `TicketStore` bắt buộc. Lỗi xảy ra khi `getBean(TicketService.class)` lần đầu?

**Đáp án:** Có thể xảy ra **ngay khi `ApplicationContext.refresh()`** tạo singleton non-lazy, trước lời gọi getBean của ứng dụng.

**Bài 4.** Một bean `@Lazy` không bị dependency eager nào yêu cầu. Khi nào dependency của nó được giải quyết?

**Đáp án:** Thường khi bean được yêu cầu tạo lần đầu. Nếu bean ấy được inject vào một eager singleton theo cách cần tạo target, thời điểm có thể sớm hơn.

**Bài 5.** Một singleton được inject prototype trực tiếp. Lần gọi method thứ hai trên singleton tự có prototype thứ hai?

**Đáp án:** Không mặc định. Singleton giữ prototype reference được inject lúc tạo. Muốn instance mới, dùng provider/factory hoặc cách scope phù hợp.

**Bài 6.** Có hai `AuditSink` candidates, method setter `required=false` tự chọn một hay bỏ qua?

**Đáp án:** Không được giả định như vậy. Đây là vấn đề ambiguity resolution; cần qualifier/primary hoặc một thiết kế injection nhiều beans hợp lý.

**Bài 7.** Trong chương trình, vì sao có `LAZY_READY false` rồi `CREATE lazy-summary` và `LAZY_READY true`?

**Đáp án:** `LazySummary` là singleton `@Lazy` không bị yêu cầu lúc refresh. `getBean(LazySummary.class)` kích hoạt factory method tạo instance; sau đó instance tồn tại trong singleton registry.

**Bài 8.** Vì sao `AUDIT B2` xuất hiện trước `RESULT DONE TICKET(B2)`?

**Đáp án:** Method `TicketService.process` gọi `audit.record` trước khi trả chuỗi; `main` in kết quả sau khi lời gọi hoàn tất.

**Bài 9.** Có nên dùng `required=false` cho repository mà service không thể thiếu?

**Đáp án:** Không. Điều đó che dependency bắt buộc, có thể biến lỗi startup dễ nhận biết thành lỗi runtime khó theo dõi.

**Bài 10.** `@Bean TicketService ticketService(TicketStore store)` cần `@Autowired` trên method để Spring tiêm store?

**Đáp án:** Không. Spring nhận biết và resolve factory method parameters từ `@Bean` metadata.

## 13. Checklist triển khai và kiểm tra

- [ ] Biết `@Autowired` mặc định là `required=true`.
- [ ] Phân loại collaborator: bắt buộc hay thực sự optional, không chọn theo mong muốn “đừng fail startup”.
- [ ] Dùng constructor/factory method parameters cho dependencies bắt buộc.
- [ ] Khi dùng `@Autowired(required=false)` trên method, thiết kế rõ hành vi nếu method bị bỏ qua.
- [ ] Không nhầm optional injection với việc Spring luôn truyền `null`.
- [ ] Với method nhiều arguments, nhớ tính nguyên khối của method resolution.
- [ ] Dùng `Optional<T>` hoặc provider khi cần optional theo tham số/lúc sử dụng.
- [ ] Với nhiều bean cùng type, có quy tắc chọn đúng: qualifier, primary hoặc aggregate injection.
- [ ] Đừng áp dụng semantics method `required=false` nguyên xi cho constructor selection.
- [ ] Xác định bean được tạo trong startup (non-lazy singleton) hay sau đó (lazy/prototype).
- [ ] Kiểm tra lazy bean bị dependency eager khác yêu cầu.
- [ ] Không kỳ vọng Spring chạy lại setter mỗi lần business method được gọi.
- [ ] Không kỳ vọng field `@Autowired` tự chạy trên đối tượng tạo tay bằng `new`.
- [ ] Với prototype trực tiếp trong singleton, xác định có cần provider/factory cho instance mới hay không.
- [ ] Kiểm thử ngữ cảnh thiếu bean, có bean và nhiều beans khi cấu hình quan trọng.
- [ ] Đọc nguyên nhân gốc của `BeanCreationException`/`UnsatisfiedDependencyException`.
- [ ] Đóng context khi không còn dùng và không gọi managed resources sau khi đóng.
- [ ] Đảm bảo build Java 17 và phân biệt dòng stdout chương trình với log Maven.

### Tham khảo tài liệu Spring chính thức

- [`@Autowired` — constructor, fields, methods, required semantics](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html)
- [Lazy-initialized beans](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-lazy-init.html)
- [Bean scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)
- [`@Bean` factory methods](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html)

**Kết luận:** `required` trả lời câu hỏi “injection point này có thể vắng dependency không?”, còn **thời điểm resolution** phụ thuộc vào cách và lúc Spring tạo bean. Với singleton eager, lỗi dependency bắt buộc có thể xảy ra ngay khi refresh; với bean lazy/prototype, lỗi có thể xuất hiện muộn hơn; với proxy/provider, lookup đích có thể tiếp tục được trì hoãn. Hiểu hai trục này giúp thiết kế dependency đúng, dự đoán lỗi chính xác và không lạm dụng `required=false` để che cấu hình thiếu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="autowired-006">Phân biệt @Autowired với jakarta.inject.Inject</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt @Autowired với jakarta.inject.Inject — Spring Framework Core Container

## 1. Hai annotation cùng giải quyết dependency injection, nhưng khác nguồn gốc

Trong Spring Core Container, **`@Autowired`** là annotation của chính Spring, còn **`@Inject`** là annotation tiêu chuẩn Dependency Injection của Jakarta, nằm trong package **`jakarta.inject`**. Spring Framework 6.2 có thể nhận biết **cả hai** khi khởi tạo bean, miễn là thư viện Jakarta Inject API hiện diện trên classpath.

Ví dụ hai constructor sau thể hiện cùng ý định cơ bản — **Spring cung cấp bean `MessageSender` cho service**:

~~~java
// Kiểu Spring:
@Autowired
public SpringClient(MessageSender sender) {
    this.sender = sender;
}

// Kiểu tiêu chuẩn Jakarta:
@Inject
public StandardClient(MessageSender sender) {
    this.sender = sender;
}
~~~

Đây là **hai snippet thuộc hai lớp khác nhau**, chưa có class/import đầy đủ; project chạy được nằm ở mục 7. Trong cả hai trường hợp, container phải tìm đúng candidate `MessageSender` và đưa **bean object** vào constructor. Annotation không tự tạo bean nếu lớp chưa được đăng ký với context.

**Mục tiêu bài học:** nắm được điểm giống, điểm khác, giới hạn của chuẩn Jakarta và cách chọn annotation hợp lý khi viết Java 17+ với Spring Framework.

Sau bài, bạn có thể:

1. Phân biệt import `org.springframework.beans.factory.annotation.Autowired` với `jakarta.inject.Inject`.
2. Dùng hai annotation trên **constructor, field hoặc method injection** trong một Spring context.
3. Hiểu `@Autowired(required=false)` **không có bản sao cùng thuộc tính** trên `@Inject`; biết dùng `Optional<T>`, nullable types hoặc provider khi dependency thực sự không bắt buộc.
4. Phân biệt `@Qualifier("...")` của Spring với `@Named("...")` của Jakarta; hiểu `jakarta.inject.Qualifier` là **meta-annotation**.
5. Dùng `jakarta.inject.Provider<T>` cho việc **lấy dependency theo nhu cầu**, đồng thời hiểu nó không tự biến một dependency thiếu thành hợp lệ.
6. Biết `@Named` có thể làm dấu hiệu đăng ký component, nhưng **không tự tạo bean nếu không có đăng ký/scan**.
7. Hiểu vì sao Spring-specific `@Value`, `@Lazy`, scope và các tính năng cấu hình khác vẫn có vai trò dù dùng `@Inject`.
8. Chạy ví dụ độc lập **Java 17+**, Maven, `javac`/`java` và đối chiếu các dòng stdout.

**Phạm vi:** so sánh **`@Autowired` và `jakarta.inject.Inject` trong Spring Framework Core Container**. Không cần Spring Boot, MVC, Servlet, JPA hoặc một CDI container khác.

## 2. Bảng đối chiếu chính xác

| Tiêu chí | Spring `@Autowired` | Jakarta `@Inject` |
| --- | --- | --- |
| Package/import | `org.springframework.beans.factory.annotation.Autowired` | `jakarta.inject.Inject` |
| Thuộc hệ sinh thái | Spring Framework | Jakarta Dependency Injection (JSR-330 lineage) |
| Injection vào constructor | Có | Có |
| Injection vào field | Có | Có |
| Injection vào method/setter | Có | Có |
| Đánh dấu dependency bắt buộc | `required=true` theo mặc định | Không có thuộc tính `required`; injection thông thường là bắt buộc |
| Tùy chọn injection | `required=false` ở injection point phù hợp; cũng hỗ trợ `Optional` | Không có `required=false`; Spring hỗ trợ `Optional<T>`, `@Nullable` ở điểm injection phù hợp |
| Chọn bean theo tên/qualifier | `@Qualifier("mainSender")` | `@Named("mainSender")` |
| Lấy bean theo nhu cầu | Spring `ObjectProvider<T>`/`ObjectFactory<T>` | Jakarta `Provider<T>` với `get()` |
| Đăng ký component theo stereotype | `@Component` | `@Named` |
| Khai báo giá trị cấu hình | Dùng `@Value` / `Environment` riêng | Không có `@Value` tương đương trong Jakarta Inject |
| Trì hoãn khởi tạo bean | Dùng `@Lazy` của Spring khi phù hợp | Không có annotation `@Lazy` tương đương trong Jakarta Inject |
| Phụ thuộc Maven thêm | Có trong Spring modules | Cần `jakarta.inject:jakarta.inject-api` trên compile/runtime classpath |
| Phụ thuộc container tại runtime | Spring nhận diện và thực hiện | Cùng Spring container cũng nhận diện và thực hiện khi Jakarta API có mặt |

**Không nói `@Inject` là “Spring annotation”:** API của nó là chuẩn Jakarta. Nhưng khi chạy trong **Spring**, chính Spring vẫn cung cấp lifecycle, candidate resolution, bean scopes và các extension points. Dùng annotation tiêu chuẩn **không tự động khiến ứng dụng Spring độc lập hoàn toàn với Spring**.

## 3. Spring nhận diện @Inject bằng cơ chế nào?

Trong cấu hình annotation-based của Spring, `AutowiredAnnotationBeanPostProcessor` tham gia phát hiện injection points và thực hiện wiring. Spring hỗ trợ các annotation `@Autowired`/`@Value` của nó, đồng thời hỗ trợ **`jakarta.inject.Inject` nếu API tương ứng hiện diện**.

Điều kiện cần:

1. Class cần inject phải **trở thành Spring bean**, ví dụ được quét từ một package phù hợp hoặc đăng ký trực tiếp bằng `AnnotationConfigApplicationContext.register(...)`.
2. Các bean collaborator phải được khai báo bằng `@Bean`, component scanning hoặc một cách đăng ký bean definition hợp lệ.
3. Container phải giải quyết được injection points theo **type**, **qualifiers**, **scope** và các quy tắc Spring.
4. Với `@Inject`, Maven phải có **Jakarta Inject API**; thiếu thư viện sẽ gây lỗi compile hoặc vấn đề classpath, không phải lỗi bản chất của DI.

~~~mermaid
flowchart TD
    A["Class đã được đăng ký thành Spring bean"] --> B["Spring xử lý injection annotations"]
    B --> C["@Autowired của Spring"]
    B --> D["@Inject của Jakarta"]
    C --> E["Tìm bean candidate phù hợp"]
    D --> E
    E --> F["Constructor / field / setter nhận object"]
    F --> G["Bean sử dụng collaborator"]
~~~

**Phân biệt hai giai đoạn:** `@Named`/`@Component` có thể là stereotype giúp Spring **phát hiện class** trong component scan, trong khi `@Inject`/`@Autowired` đánh dấu **điểm cần wiring**. Chúng phục vụ những vai trò khác nhau.

### @Named khác @Inject

- `@Named("standardClient")` trên class có thể đặt tên component trong Spring.
- `@Named("mainSender")` trên **constructor parameter** có thể làm qualifier để chọn một bean cụ thể.
- `@Inject` trên constructor thể hiện yêu cầu container **cung cấp các tham số**.
- Một class có `@Named` nhưng **nằm ngoài phạm vi scan và không đăng ký trực tiếp** sẽ không tự xuất hiện trong context.

## 4. Constructor injection: hai annotation thường hoạt động tương đương

Trong một context có duy nhất một bean `MessageSender`, hai cách sau đều được Spring xử lý:

~~~java
@Component
final class SpringConsumer {
    private final MessageSender sender;

    @Autowired
    SpringConsumer(MessageSender sender) {
        this.sender = java.util.Objects.requireNonNull(sender);
    }
}
~~~

~~~java
@Named
final class JakartaConsumer {
    private final MessageSender sender;

    @Inject
    JakartaConsumer(MessageSender sender) {
        this.sender = java.util.Objects.requireNonNull(sender);
    }
}
~~~

Cả hai là **snippets minh họa** chưa kèm định nghĩa `MessageSender`. Khi được Spring đăng ký và có collaborator hợp lệ, constructor sẽ nhận bean. Việc giữ collaborator trong `private final` và kiểm tra non-null là thiết kế **của code Java**, không phải đặc quyền của annotation nào.

### Một constructor duy nhất có thể không cần cả hai

Với Spring Framework hiện đại, nếu class đã đăng ký thành bean và **chỉ có một constructor**, Spring có thể dùng constructor đó **ngay cả khi không viết `@Autowired` hoặc `@Inject`**:

~~~java
@Component
final class PlainConsumer {
    private final MessageSender sender;

    PlainConsumer(MessageSender sender) {
        this.sender = java.util.Objects.requireNonNull(sender);
    }
}
~~~

Vì vậy đừng thêm `@Inject` chỉ vì nghĩ **mọi constructor của Spring bean bắt buộc có annotation**. Annotation hữu ích khi muốn làm rõ điểm injection, nhất là lúc class có nhiều constructors. Nếu class được tạo **bằng `new` trong một `@Bean` factory method**, Java quyết định overload cụ thể qua lời gọi `new` ấy; không tự động đồng nghĩa Spring chọn lại constructor của object được trả về.

### Nếu class có nhiều constructors

- `@Autowired` cho phép mô tả constructor required (mặc định) hoặc nhiều ứng viên `required=false` theo quy tắc Spring.
- `@Inject` **không có tham số `required`**. Đừng áp dụng cú pháp `@Inject(required=false)` — Java sẽ báo lỗi compile.
- Với nhiều overload, ưu tiên **một constructor bắt buộc rõ ràng**; đừng trông cậy vào container đoán ý định nghiệp vụ.
- Nên tránh gắn nhiều constructor `@Inject` với kỳ vọng Spring sẽ xử lý như một danh sách required=false. Một class có nhiều constructors cần một lựa chọn minh bạch.
- Không dùng constructor mặc định tạo trạng thái thiếu dependency chỉ để ứng dụng khởi động.

**Quan trọng:** `@Autowired(required=false)` trên **setter method** và trên **constructor** có ngữ nghĩa khác nhau: ở setter, phương thức có thể bị bỏ qua khi dependency không có; với nhiều constructor annotated optional, Spring chọn constructor có thể giải quyết được theo quy tắc. Đừng hiểu `required=false` là truyền null vào mọi tham số còn thiếu.

## 5. Điểm khác biệt lớn: xử lý dependency tùy chọn

### 5.1. Spring: @Autowired(required=false) trên setter

~~~java
@Component
final class OptionalMonitoring {
    private boolean metricsAttached;

    @Autowired(required = false)
    public void setMetrics(MetricsClient metrics) {
        metricsAttached = true;
    }

    boolean metricsAttached() {
        return metricsAttached;
    }
}
~~~

Đây là **snippet**; `MetricsClient` chưa được định nghĩa trong đoạn độc lập này. Nếu Spring không có bean `MetricsClient` nào phù hợp, nó có thể **không gọi setter tùy chọn**; `metricsAttached` vẫn là `false`. Ví dụ runnable ở mục 7 sẽ kiểm chứng điều này.

### 5.2. Jakarta: không có required=false; dùng Optional<T> khi phù hợp

~~~java
@Named
final class OptionalJakartaClient {
    private final java.util.Optional<MetricsClient> metrics;

    @Inject
    OptionalJakartaClient(java.util.Optional<MetricsClient> metrics) {
        this.metrics = java.util.Objects.requireNonNull(metrics);
    }

    boolean missing() {
        return metrics.isEmpty();
    }
}
~~~

**Snippet**, cần khai báo `MetricsClient`. Trong **Spring**, khi không có bean `MetricsClient` phù hợp, injection point kiểu `Optional<MetricsClient>` có thể nhận `Optional.empty()`. Điều này được tài liệu Spring hỗ trợ cho `@Inject`.

| Mong muốn | Cách biểu đạt |
| --- | --- |
| Dependency **bắt buộc** | Constructor injection thường, với `@Autowired` hoặc `@Inject` |
| Setter optional của Spring | `@Autowired(required=false)` trên setter phù hợp |
| Tham số optional trong Spring | `Optional<T>` hoặc nullable injection point với quy tắc phù hợp |
| Truy cập dependency theo nhu cầu | `jakarta.inject.Provider<T>` hoặc Spring `ObjectProvider<T>` |

**Đừng nhầm Provider với Optional.** `Provider<T>.get()` **yêu cầu lấy một instance**; nếu bean không tồn tại, việc gọi `get()` có thể thất bại. Provider giúp trì hoãn/động hóa việc truy cập, **không phải một cam kết “thiếu bean thì luôn trả null”**.

### 5.3. Optional khác “cứ chạy khi thiếu là đúng”

Nếu một service xử lý thanh toán **không thể hoạt động khi thiếu `PaymentGateway`**, đánh dấu dependency thành optional chỉ để tránh startup error là một lỗi thiết kế. Hãy giữ constructor required và để context **fail fast** khi thiếu bean.

Dùng optional cho những thành phần thực sự không thiết yếu, chẳng hạn metrics, tracing phụ, plugin hoặc tính năng có thể tắt. Khi vắng mặt, code phải định nghĩa hành vi có chủ đích; không chỉ bỏ qua `NullPointerException`.

## 6. @Qualifier, @Named, Provider và @Value

### 6.1. Chọn một bean theo tên

Nếu có nhiều beans cùng kiểu `MessageSender`, điểm injection cần chỉ rõ cái mong muốn.

**Spring:**

~~~java
@Autowired
public SpringClient(
        @Qualifier("mainSender") MessageSender sender) {
    this.sender = sender;
}
~~~

**Jakarta:**

~~~java
@Inject
public StandardClient(
        @Named("mainSender") MessageSender sender) {
    this.sender = sender;
}
~~~

Đây là **snippets của hai classes**, chưa phải file độc lập. Trong ngữ cảnh Spring, cả hai đều hướng Spring tới bean có name/qualifier tương ứng. `@Named` còn dùng trên class để đánh dấu tên component.

**Lưu ý:** `jakarta.inject.Qualifier` là **meta-annotation** để xây dựng custom qualifier, không phải annotation chứa tham số tên dạng `@Qualifier("mainSender")` của Spring. Muốn dùng tên String theo Jakarta, hãy dùng `@Named("mainSender")`.

### 6.2. Provider<T> lấy bean khi cần

~~~java
@Named
final class LazyConsumer {
    private final Provider<MessageSender> senderProvider;

    @Inject
    LazyConsumer(Provider<MessageSender> senderProvider) {
        this.senderProvider = senderProvider;
    }

    String run() {
        return senderProvider.get().send("Lan");
    }
}
~~~

**Snippet minh họa:** cần import `jakarta.inject.Provider` và kiểu `MessageSender` có method `send`. Spring hỗ trợ `Provider<T>` khi có Jakarta Inject API. Với singleton thông thường, nhiều lần `get()` vẫn trả cùng instance; với prototype, mỗi lần tra cứu có thể tạo instance mới. Không khẳng định `Provider` tự làm bean có scope prototype hay không bao giờ gọi logic tạo bean.

### 6.3. Không có @Value hay @Lazy tương đương trực tiếp trong Jakarta Inject

Jakarta `@Inject` tập trung vào injection **bean object**. Nó không cung cấp annotation tương ứng với Spring `@Value` để giải quyết trực tiếp property placeholder, hay Spring `@Lazy` để khai báo lazy initialization. Khi dùng Spring, bạn có thể kết hợp annotation Jakarta với các tính năng Spring:

~~~java
@Inject
public ConfigurableClient(
        @Named("mainSender") MessageSender sender,
        @Value("${app.prefix}") String prefix) {
    this.sender = sender;
    this.prefix = prefix;
}
~~~

Đây là **snippet**, cần class/import Spring `@Value` và cấu hình property source phù hợp; không phải file runnable. Nó cho thấy **trộn các annotations là có thể**, nhưng class lúc này **phụ thuộc Spring-specific configuration semantics**, không còn “chỉ dùng Jakarta”.

Đối với simple values, có thể đọc `Environment.getRequiredProperty(...)` tại composition root rồi truyền vào constructor; không nên biến mọi String hay int thành bean để dùng `@Inject`.

### 6.4. Scope và stereotype

Trong Spring, một class `@Named` được đăng ký như Spring bean thường vẫn có scope **singleton mặc định**, giống một class `@Component` theo Spring defaults. Dù chuẩn Jakarta có cách diễn giải scope khi không chỉ định riêng, không nên giả định Spring tự chuyển `@Named` thành prototype.

Nếu cần prototype trong Spring:

~~~java
@Named("temporaryHandler")
@org.springframework.context.annotation.Scope("prototype")
final class TemporaryHandler {
    // Một instance mới theo mỗi yêu cầu phù hợp.
}
~~~

Snippet chỉ minh họa. `jakarta.inject.Singleton` là annotation scope tiêu chuẩn, nhưng trong Spring **không cần gắn nó cho mọi bean để có singleton mặc định**.

Không thay `@ComponentScan`/registration bằng việc chỉ import `@Named`: class vẫn phải được scanner nhìn thấy hoặc đăng ký trực tiếp.

## 7. Ví dụ độc lập Java 17+ — cả hai annotation trong cùng Spring context

### 7.1. Cấu trúc và dependency

Dùng **Spring Framework 6.2.19**, **JDK 17+**, Maven và Jakarta Inject API **2.0.0**. Phiên bản được ghim cho ví dụ, không khẳng định là phiên bản mới nhất của Spring.

~~~text
spring-jakarta-inject-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── InjectionComparisonDemo.java
~~~

File `pom.xml`:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-jakarta-inject-demo</artifactId>
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
        <dependency>
            <groupId>jakarta.inject</groupId>
            <artifactId>jakarta.inject-api</artifactId>
            <version>2.0.0</version>
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
                    <mainClass>demo.InjectionComparisonDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**Cần cả hai dependencies**: `spring-context` đưa Spring container vào chương trình; `jakarta.inject-api` cung cấp `Inject`, `Named` và `Provider` để compile và để Spring nhận diện chúng khi ứng dụng chạy.

### 7.2. Mã nguồn src/main/java/demo/InjectionComparisonDemo.java

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

public final class InjectionComparisonDemo {
    private InjectionComparisonDemo() {}

    public interface MessageSender {
        String send(String name);
    }

    public static final class ConsoleSender implements MessageSender {
        @Override
        public String send(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name required");
            }
            return "Xin chao, " + name.strip();
        }
    }

    public static final class EventLog {
        private final List<String> events = new ArrayList<>();

        public void record(String event) {
            events.add(event);
        }

        public List<String> snapshot() {
            return List.copyOf(events);
        }
    }

    // Spring-specific constructor injection.
    @Component("springClient")
    public static final class SpringClient {
        private final MessageSender sender;
        private final EventLog log;

        @Autowired
        public SpringClient(
                @Qualifier("mainSender") MessageSender sender,
                EventLog log) {
            this.sender = Objects.requireNonNull(sender);
            this.log = Objects.requireNonNull(log);
        }

        public MessageSender sender() {
            return sender;
        }

        public String greet(String name) {
            String message = sender.send(name);
            log.record("spring:" + message);
            return message;
        }
    }

    // Jakarta standard constructor injection.
    @Named("standardClient")
    public static final class StandardClient {
        private final MessageSender sender;
        private final EventLog log;

        @Inject
        public StandardClient(
                @Named("mainSender") MessageSender sender,
                EventLog log) {
            this.sender = Objects.requireNonNull(sender);
            this.log = Objects.requireNonNull(log);
        }

        public MessageSender sender() {
            return sender;
        }

        public String greet(String name) {
            String message = sender.send(name);
            log.record("inject:" + message);
            return message;
        }
    }

    // Jakarta Provider resolves the target on get().
    @Named("providerClient")
    public static final class ProviderClient {
        private final Provider<MessageSender> provider;

        @Inject
        public ProviderClient(Provider<MessageSender> provider) {
            this.provider = Objects.requireNonNull(provider);
        }

        public boolean providesSame(MessageSender sender) {
            return provider.get() == sender;
        }
    }

    // No bean of this interface is registered in the example.
    public interface MissingMetrics {}

    @Component("optionalSpringClient")
    public static final class OptionalSpringClient {
        private boolean metricsAttached;

        public OptionalSpringClient() {}

        @Autowired(required = false)
        public void setMetrics(MissingMetrics metrics) {
            metricsAttached = true;
        }

        public boolean setterSkipped() {
            return !metricsAttached;
        }
    }

    @Named("optionalStandardClient")
    public static final class OptionalStandardClient {
        private final Optional<MissingMetrics> metrics;

        @Inject
        public OptionalStandardClient(Optional<MissingMetrics> metrics) {
            this.metrics = Objects.requireNonNull(metrics);
        }

        public boolean absent() {
            return metrics.isEmpty();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean("mainSender")
        public MessageSender mainSender() {
            return new ConsoleSender();
        }

        @Bean
        public EventLog eventLog() {
            return new EventLog();
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            context.register(AppConfig.class, SpringClient.class,
                    StandardClient.class, ProviderClient.class,
                    OptionalSpringClient.class, OptionalStandardClient.class);
            context.refresh();

            SpringClient spring = context.getBean(SpringClient.class);
            StandardClient standard = context.getBean(StandardClient.class);
            ProviderClient provider = context.getBean(ProviderClient.class);
            OptionalSpringClient optionalSpring =
                    context.getBean(OptionalSpringClient.class);
            OptionalStandardClient optionalStandard =
                    context.getBean(OptionalStandardClient.class);
            MessageSender sender = context.getBean(MessageSender.class);
            EventLog log = context.getBean(EventLog.class);

            System.out.println("springGreeting=" + spring.greet(" Lan "));
            System.out.println("injectGreeting=" + standard.greet(" Minh "));
            System.out.println("sameSender=" +
                    (spring.sender() == sender
                     && standard.sender() == sender));
            System.out.println("providerSame=" +
                    provider.providesSame(sender));
            System.out.println("autowiredSetterSkipped=" +
                    optionalSpring.setterSkipped());
            System.out.println("injectOptionalAbsent=" +
                    optionalStandard.absent());
            System.out.println("events=" + log.snapshot());
            System.out.println("standardSingleton=" +
                    (standard == context.getBean(StandardClient.class)));
        }
    }
}
~~~

**Vì sao ví dụ tự chứa:**

- Có đúng một public top-level class trùng tên tệp; collaborator classes/interfaces được khai báo nested static.
- Bean `mainSender` có kiểu `MessageSender` và được đặt tên rõ; `eventLog` là bean singleton mặc định.
- `SpringClient` inject với Spring `@Autowired`/`@Qualifier("mainSender")`.
- `StandardClient` inject cùng bean bằng Jakarta `@Inject`/`@Named("mainSender")`.
- `ProviderClient` inject `jakarta.inject.Provider<MessageSender>`; vì context chỉ có một sender singleton, `provider.get()` trả chính object đó.
- `OptionalSpringClient` không có bean `MissingMetrics`, vì vậy setter `@Autowired(required=false)` không được gọi, giá trị boolean giữ `false`.
- `OptionalStandardClient` dùng `Optional<MissingMetrics>` qua `@Inject`, nên nhận `Optional.empty()` khi thiếu dependency trong Spring.
- Hai consumers chia sẻ một `EventLog` singleton và ghi theo thứ tự main gọi. Demo không dùng random, clock hay kết quả iteration không ổn định.
- `context.register(...)` **đăng ký trực tiếp** các component classes; không cần component scanning. Chỉ gắn `@Component`/`@Named` ở source mà không đăng ký/scan sẽ không tự làm class thành bean.
- `try-with-resources` đóng context sau khi in xong. Không có bean destroy callback phát ra stdout.

### 7.3. Chạy Maven và đối chiếu stdout

Từ thư mục `spring-jakarta-inject-demo`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

**Đúng tám dòng stdout do chương trình Java in khi chạy thành công:**

~~~text
springGreeting=Xin chao, Lan
injectGreeting=Xin chao, Minh
sameSender=true
providerSame=true
autowiredSetterSkipped=true
injectOptionalAbsent=true
events=[spring:Xin chao, Lan, inject:Xin chao, Minh]
standardSingleton=true
~~~

Những dòng version và logging của Maven/Spring (nếu xuất hiện) không nằm trong tám dòng `System.out.println` ở trên. Các trạng thái `true`/`false` là kết quả trực tiếp của cấu hình demo, **không phải lời khẳng định mọi annotation sẽ chọn cùng instance trong mọi scope/hoàn cảnh**.

### 7.4. Dùng javac/java trực tiếp

Vì mã nguồn import Spring và `jakarta.inject.*`, compiler cần **cả hai nhóm dependency** trên classpath. Dùng Maven tạo file classpath:

~~~bash
mvn -q dependency:build-classpath -Dmdep.outputFile=classpath.txt
~~~

**Linux/macOS (bash/zsh):**

~~~bash
mkdir -p out
javac --release 17 -encoding UTF-8 \
  -cp "$(cat classpath.txt)" \
  -d out src/main/java/demo/InjectionComparisonDemo.java
java -cp "out:$(cat classpath.txt)" demo.InjectionComparisonDemo
~~~

**Windows PowerShell:**

~~~powershell
$deps = (Get-Content -Raw classpath.txt).Trim()
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -encoding UTF-8 -cp "$deps" -d out src/main/java/demo/InjectionComparisonDemo.java
java -cp "out;$deps" demo.InjectionComparisonDemo
~~~

Trên Unix, classpath phân tách bằng `:`, Windows bằng `;`. Cùng class `demo.InjectionComparisonDemo` nên output nghiệp vụ trùng tám dòng ở trên.

Nếu `javac` báo `package jakarta.inject does not exist`, kiểm tra dependency `jakarta.inject-api` trong POM/classpath; nếu thiếu `org.springframework...`, kiểm tra `spring-context` và các transitive JARs.

## 8. Giải thích tám dòng stdout

| Dòng | Kết luận được phép rút ra |
| --- | --- |
| `springGreeting=Xin chao, Lan` | SpringClient nhận sender và xử lý tên đã strip |
| `injectGreeting=Xin chao, Minh` | StandardClient được Spring inject qua @Inject |
| `sameSender=true` | Hai consumers giữ **cùng bean mainSender singleton** trong context này |
| `providerSame=true` | `Provider<MessageSender>.get()` ở context này trả cùng sender singleton |
| `autowiredSetterSkipped=true` | Setter tùy chọn của Spring bị bỏ qua khi thiếu bean MissingMetrics |
| `injectOptionalAbsent=true` | Optional<MissingMetrics> được resolve vắng mặt |
| `events=[spring:..., inject:...]` | Hai services cùng ghi đúng thứ tự vào EventLog |
| `standardSingleton=true` | Bean @Named do Spring quản lý là singleton mặc định ở ví dụ |

Không dùng `sameSender=true` để suy ra **prototype, scoped proxy, nhiều beans cùng type, hoặc multiple contexts** đều trả chung object. Scope và cách bean được resolve vẫn chi phối identity.

## 9. Một số hiểu nhầm thường gặp

### 9.1. “@Inject tự động thay hết mọi tính năng @Autowired”

Không. Spring hiểu `@Inject` như một annotation injection thay thế cơ bản, nhưng Jakarta annotation **không có `required` attribute**. Spring `@Autowired` cùng các facility như `@Value`, `@Lazy` và `ObjectProvider` vẫn cung cấp những tính năng riêng. Khi chuyển annotation, phải kiểm tra optional semantics, provider, qualifier và build dependencies.

### 9.2. “Dùng @Inject thì chạy được trên mọi DI container không sửa gì”

API annotation mang tính tiêu chuẩn và giúp tách bớt phụ thuộc ở **injection code**, nhưng mô hình registration, phạm vi bean, lifecycle, giải pháp external config, qualifier custom và container extensions có thể khác nhau. Cách `Optional<T>` được Spring hỗ trợ không đồng nghĩa mọi CDI/container khác nhất thiết có hành vi y hệt.

Portability là **mức độ**, không phải lời bảo đảm chuyển toàn ứng dụng Spring sang container khác bằng cách đổi một import.

### 9.3. “@Named và @Qualifier của Jakarta giống nhau”

Không. `jakarta.inject.Named` là một qualifier định danh bằng String đồng thời có thể dùng trên component class. `jakarta.inject.Qualifier` dùng như **meta-annotation** để định nghĩa custom qualifiers; không viết `@jakarta.inject.Qualifier("mainSender")`. Spring `@Qualifier("mainSender")` là annotation riêng của Spring và có value.

### 9.4. “@Named làm mọi class trong project thành bean”

Không. Với component scanning, cần chỉ đúng package; với code khởi tạo context thuần, bạn có thể register class trực tiếp. Annotation không tự kích hoạt scanner hoặc tự ghi vào registry.

### 9.5. “Provider<T> luôn cho phép dependency thiếu”

Không. Provider chủ yếu hỗ trợ **lấy dependency tại thời điểm cần**, và cũng thích hợp với prototype/scoped beans. Nếu không có bean hợp lệ, `get()` có thể ném exception. Nếu dependency thực sự tùy chọn, dùng kiểu `Optional<T>` hoặc chính sách rõ ở application layer.

### 9.6. “@Autowired(required=false) bảo đảm field có giá trị null”

Với setter được đánh dấu optional, Spring có thể **bỏ qua toàn bộ method injection** khi không resolve được collaborator. Với field có thể vẫn là null/default nếu không được inject; code phải thiết kế được hành vi thiếu đó. Không nghĩ `required=false` là lệnh luôn truyền null vào một setter.

### 9.7. “@Inject nghĩa là @Singleton”

`@Inject` chỉ đánh dấu injection point. Scope của bean do container/definition quyết định. Trong Spring, bean `@Named` thường mặc định singleton, không phải vì constructor có `@Inject`, cũng không mặc nhiên là prototype.

### 9.8. “Dùng javax.inject.Inject vì project Java cũ từng làm vậy”

Spring Framework 6+ sử dụng các Jakarta APIs trong tài liệu tiêu chuẩn. Trong project Java 17+ mới, ưu tiên `jakarta.inject.Inject`/`Named` và dependency `jakarta.inject-api`. `javax.inject.Inject` thuộc namespace Java EE/JSR-330 cũ; Spring có thể giữ hỗ trợ tương thích ở một số phiên bản/ngữ cảnh, nhưng **không trộn hai namespace** nếu không hiểu chính xác dependency và môi trường migration.

### 9.9. “Có @Inject là không cần kiểm tra null”

Dependency injection không thay thế invariant Java:

~~~java
@Inject
public BillingService(PaymentGateway gateway) {
    this.gateway = java.util.Objects.requireNonNull(gateway);
}
~~~

Snippet này chưa có định nghĩa `BillingService`/`PaymentGateway` hoàn chỉnh. Các guard tiếp tục có ích khi class được tạo **ngoài Spring** bằng `new` hoặc khi caller/adapter cung cấp dữ liệu sai. Không biến dependency bắt buộc thành nullable chỉ để bỏ qua lỗi wiring.

## 10. Lỗi thường gặp và hướng xử lý

| Triệu chứng | Nguyên nhân có thể | Hướng khắc phục |
| --- | --- | --- |
| `package jakarta.inject does not exist` | Chưa khai báo thư viện Jakarta Inject API | Thêm Maven dependency, cập nhật build/classpath |
| `cannot find symbol @Inject` | Import nhầm hoặc dependency không có | Import `jakarta.inject.Inject` |
| `@Inject(required=false)` không compile | Jakarta @Inject không có thuộc tính required | Dùng `Optional<T>`/nullable/provider tùy hợp đồng |
| `UnsatisfiedDependencyException` | Bean constructor required thiếu collaborator | Đăng ký bean, kiểm tra scan/qualifier |
| `NoSuchBeanDefinitionException` | Không có bean theo kiểu/tên mong muốn | Kiểm tra `@Bean`, `@Named`, cấu hình context |
| `NoUniqueBeanDefinitionException` | Nhiều bean candidates cho một tham số đơn trị | Chọn với `@Qualifier`/`@Named` hoặc policy primary |
| Setter `@Autowired(required=false)` không chạy | Bean optional không tồn tại | Đó có thể là đúng semantics; kiểm tra hợp đồng optional |
| `Provider.get()` thất bại | Không có bean thích hợp ở thời điểm lấy | Kiểm tra availability/scope, đừng coi Provider là Optional |
| `@Named` class không được phát hiện | Không có component scan hoặc explicit registration | Cấu hình phạm vi scan hoặc register đúng class |
| Dùng `@Named` để đọc property placeholder | Nhầm bean selector với cấu hình value | Dùng `@Value`/`Environment` phù hợp |
| `@Inject` nhưng thuộc tính vẫn null | Object do `new` ngoài container hoặc bean processing không hoạt động | Dùng managed bean, kiểm tra registration/post-processing |
| Khác biệt scope giữa môi trường | Áp dụng semantics container khác cho Spring | Kiểm tra scope của bean definition trong Spring |
| Đổi import `javax.inject`/`jakarta.inject` bừa bãi | Namespace khác, dependencies không đồng bộ | Chốt Jakarta namespace cho Spring 6+ và kiểm tra migrations |
| Test Spring pass nhưng logic sai | Wiring có mặt chưa chứng minh nghiệp vụ đúng | Viết unit test với fake và integration test |

**Cách debug:** xác định bean source/registration, annotation được import từ package nào, điểm injection cần type gì, có bao nhiêu candidate, có qualifier chính xác không, dependency optional hay required, rồi đọc chuỗi `Caused by` của exception. Nếu lỗi biên dịch, sửa dependencies **trước** khi phân tích runtime Spring.

## 11. Thực hành mở rộng

**Bài 1 — thiếu bean bắt buộc:** tạo class `@Named` có constructor `@Inject RequiredClient(MissingMetrics metrics)` và đăng ký nó vào **context test riêng** nhưng không đăng ký `MissingMetrics`. Dự kiến refresh thất bại; đừng mong Spring truyền null hay tự bỏ constructor.

**Bài 2 — thay qualifier:** đăng ký thêm một sender có tên `backupSender`. Giữ injection `@Qualifier("mainSender")` và `@Named("mainSender")`: cả hai consumers vẫn yêu cầu sender chính. Bỏ qualifier rồi thử giải thích nguyên nhân ambiguity nếu nhiều candidates không có lựa chọn khác.

**Bài 3 — singleton và prototype:** thay một bean hợp lệ thành `@Scope("prototype")`, sau đó dùng `Provider<T>.get()` hai lần; kiểm tra identity của object theo scope thực. Không sửa code demo ban đầu rồi vẫn yêu cầu stdout `providerSame=true`.

**Bài 4 — Optional:** đăng ký `MissingMetrics` bean trong test khác, kiểm tra `Optional<MissingMetrics>.isPresent()` thành true và setter optional được gọi. Nhớ thử cả trường hợp vắng mặt.

**Bài 5 — Java thuần:** tạo `SpringClient` hoặc `StandardClient` bằng constructor với một fake sender và fake log. Cả hai test được như class Java bình thường; không bắt buộc bật Spring chỉ để test logic greet.

### Checklist chốt bài

- [ ] Biết imports và dependency khác nhau của `@Autowired` và `@Inject`.
- [ ] Cả hai đều có thể injection constructor/field/setter trong Spring.
- [ ] Không cho rằng annotation tự đăng ký class thành bean.
- [ ] Dùng một constructor rõ cho collaborator bắt buộc.
- [ ] Biết `@Autowired` có `required`, còn `@Inject` thì không.
- [ ] Chọn Optional/nullable semantics phù hợp cho dependency thật sự tùy chọn.
- [ ] Phân biệt Provider lấy bean on-demand với Optional biểu diễn absence.
- [ ] Dùng `@Qualifier("name")` của Spring hoặc `@Named("name")` của Jakarta đúng vai trò.
- [ ] Biết `jakarta.inject.Qualifier` là meta-annotation.
- [ ] Không coi `@Named` mặc nhiên mang prototype scope trong Spring.
- [ ] Hiểu `@Value`/`@Lazy` là tính năng Spring không có annotation Jakarta thay thế trực tiếp.
- [ ] Có `jakarta.inject-api` trong pom và compile/runtime classpath.
- [ ] Chạy `mvn -q clean compile exec:java` và đối chiếu **tám dòng stdout**.
- [ ] Với `javac`/`java`, dùng đúng Spring + Jakarta JARs.
- [ ] Test cùng behavior ở unit level và wiring ở context level.

**Kết luận:** trong Spring, `@Autowired` và `jakarta.inject.Inject` đều có thể khai báo dependency injection. Hãy chọn `@Inject` nếu muốn dùng **API annotation tiêu chuẩn Jakarta** cho những yêu cầu DI cơ bản, và `@Autowired` nếu cần biểu đạt trực tiếp khả năng Spring-specific như `required=false` tại một injection point. Dù chọn cách nào, **registration, bean selection, scope và lifecycle vẫn do Spring container quyết định** khi chương trình chạy trên Spring Framework.

### Tài liệu chính thức

- [Spring Framework 6.2 — Using JSR-330 Standard Annotations](https://docs.spring.io/spring-framework/reference/6.2/core/beans/standard-annotations.html)
- [Spring Framework 6.2 — Using @Autowired](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html)
- [Spring Framework 6.2 — Annotation-based Container Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config.html)
- [Spring API — AutowiredAnnotationBeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/annotation/AutowiredAnnotationBeanPostProcessor.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="autowired-007">Chọn theo tên bằng jakarta.annotation.Resource</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn bean theo tên bằng `jakarta.annotation.Resource` trong Spring Core Container

## 1. `@Resource` giải quyết vấn đề gì?

Trong Spring IoC Container, ứng dụng có thể đăng ký **nhiều beans cùng Java type**, ví dụ hai implementation của `Channel`: `emailChannel` và `smsChannel`. Khi yêu cầu tiêm một `Channel`, cần có quy tắc xác định **bean nào** sẽ được cấp cho đối tượng.

Annotation **`jakarta.annotation.Resource`** cho phép mô tả một phụ thuộc **chủ yếu theo bean name**, thường áp dụng cho **instance field** hoặc **JavaBean property setter** của một Spring-managed bean. Đây là annotation chuẩn Jakarta Annotations (nguồn gốc JSR-250), không phải `org.springframework.beans.factory.annotation.Resource`.

Cú pháp rõ nhất:

~~~java
// Snippet: các type/bean này được định nghĩa trong dự án hoàn chỉnh ở mục 6.
@Resource(name = "smsChannel")
private Channel selected;
~~~

Khi Spring tạo bean chứa field này, nó tìm bean có **tên `smsChannel`** và đưa reference phù hợp vào field. Spring **không tạo một `Channel` mới** chỉ vì nhìn thấy annotation. Nếu bean `smsChannel` không được đăng ký, injection thường thất bại thay vì tự chọn bean tên khác.

**Nội dung cốt lõi của bài:**
- Cách chỉ định tên tường minh bằng `@Resource(name="...")`;
- Cách lấy tên **mặc định** từ field hoặc tên property của setter;
- Cách xử lý **alias**;
- **Ngoại lệ fallback theo type khi không chỉ định tên tường minh**;
- Khác biệt với `@Autowired`, `@Qualifier` và `@Primary`;
- Nguyên tắc chọn bean đúng type, xử lý thiếu/trùng/sai tên và kiểm chứng bằng Java 17+.

**Phạm vi**: Spring Framework Core Container thuần, Java 17, Maven; không dùng Spring Boot, database, HTTP server hoặc cơ chế auto-configuration.

## 2. Quy tắc chọn: tên tường minh, tên mặc định rồi fallback có điều kiện

Đọc quy trình khái niệm:

~~~text
          Injection point mang @Resource
                        |
                 Có name="..."?
                /                \
              CÓ                KHÔNG
               |                   |
               v                   v
       Lấy tên chỉ định   Lấy tên field/setter property
               |                   |
               v                   v
       Tìm bean theo tên  Có bean mang tên mặc định?
               |              /           \
          Tên có?          CÓ             KHÔNG
          /    \             |               |
        CÓ    KHÔNG           v               v
         |      |       Dùng bean tên ấy  Fallback theo type
         v      v           |             (mặc định được bật)
   Kiểm tra type  LỖI        v               |
         |             Kiểm tra type     Resolve candidate
         v                  |             theo type/primary
      Inject               Inject            |
                                              v
                                            Inject
~~~

**Trường hợp 1 — Có `name` tường minh.** Với `@Resource(name="smsChannel")`, Spring tìm **bean có chính tên được nêu** (hoặc alias hợp lệ). Không có bean ấy thì **không** âm thầm chuyển qua `emailChannel` dù email được `@Primary`. Nếu bean cùng tên có **type không tương thích**, injection cũng không thành công.

**Trường hợp 2 — Không chỉ định `name`.** Với `@Resource private Channel smsChannel;`, tên mặc định là **`smsChannel`**, xuất phát từ tên field. Với `@Resource void setArchiveChannel(Channel c)`, tên mặc định là tên JavaBean property **`archiveChannel`**, không phải tên tham số `c`.

**Trường hợp 3 — Tên mặc định không tồn tại.** Trong cấu hình Spring thông thường, `CommonAnnotationBeanPostProcessor` cho phép fallback từ tên mặc định sang **type matching** (cờ `fallbackToDefaultTypeMatch`, mặc định `true`). Nếu có một candidate nổi bật nhờ `@Primary` hoặc chỉ có một bean phù hợp, fallback có thể resolve thành công. Nếu nhiều candidates mơ hồ, có thể thất bại.

**Kết luận đúng:** `@Resource` là **by-name trước**, nhưng phát biểu “`@Resource` chỉ bao giờ tìm theo tên và không thể theo type” là **không chính xác** khi `name` bị bỏ trống. Ngược lại, không được dựa vào fallback để che một tên bean gõ sai có chủ đích; đặt tên tường minh khi hợp đồng thực sự cần một bean duy nhất.

## 3. `@Resource` và `@Autowired`/`@Qualifier` khác nhau thế nào?

| Tiêu chí | `@Resource` | `@Autowired` + `@Qualifier` |
| --- | --- | --- |
| Annotation | `jakarta.annotation.Resource` | `org.springframework.beans.factory.annotation.Autowired`, `Qualifier` |
| Ý nghĩa chủ đạo | **Tham chiếu một bean theo tên** | **Dependency theo type**, qualifier thu hẹp candidates |
| Tên chỉ định | `@Resource(name="smsChannel")` | `@Autowired @Qualifier("smsChannel")` |
| Nếu `name` tường minh không tìm được | Lỗi name lookup, không tự đổi sang bean khác | Candidate matching theo type+qualifier có thể thất bại |
| Nếu không ghi tên | Tên field/setter property rồi fallback theo type nếu không có bean mang tên ấy | Resolution theo type, metadata và quy tắc ưu tiên; tên field/parameter có thể hỗ trợ chọn candidate |
| Constructor injection | **Không dùng `@Resource` để đánh dấu constructor** | `@Autowired` hỗ trợ constructor; một constructor duy nhất thường không cần annotation |
| Phương thức nhiều tham số | Không dùng `@Resource` làm generic arbitrary multi-argument method injection | `@Autowired` hỗ trợ method nhiều tham số |
| Nhiều beans cùng type | Chọn bằng tên trực tiếp, kiểm tra type cuối cùng | `@Qualifier`, `@Primary`, generic/metadata giúp resolve |
| Tiện ích phù hợp | Integration points, tên bean cố định, chuyển tiếp alias, setter theo property name | Constructor DI, code theo interface, lựa chọn candidate theo vai trò/type |

`@Qualifier("smsChannel")` **không phải tuyệt đối đồng nghĩa** với `@Resource(name="smsChannel")`. Spring mô tả qualifier như **tiêu chí lọc bên trong tập beans khớp type**, không phải một yêu cầu tên chính duy nhất tách khỏi type. Còn `@Resource(name=...)` diễn đạt rõ mục tiêu **định danh theo bean name**.

**Đừng nhầm import:**

~~~java
import jakarta.annotation.Resource;
// Khác hoàn toàn với:
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
~~~

Ba import này chỉ minh họa vị trí API, không phải một chương trình Java độc lập. Một project cần **`jakarta.annotation-api`** trên compile classpath để import `@Resource`. Không dùng import cũ `javax.annotation.Resource` cho mã Spring Framework 7/Jakarta hiện đại.

### Có cần `@Primary` khi dùng `@Resource(name="...")`?

Không: nếu tên chỉ định tồn tại và bean có type phù hợp, Spring chọn theo tên ấy **ngay cả khi bean cùng type khác mang `@Primary`**. `@Primary` có ý nghĩa trong những tình huống cần resolve theo type, chẳng hạn fallback không có tên mặc định. Ví dụ mục 6 cố tình khai báo `emailChannel` là primary nhưng `@Resource(name="smsChannel")` vẫn lấy **SMS**.

## 4. Vị trí annotation được Spring hỗ trợ

### 4.1. Field (bao gồm private instance field)

~~~java
@Resource(name = "smsChannel")
private Channel selectedChannel;
~~~

Spring có thể gán field sau khi Java constructor tạo bean. Không cần setter công khai để dùng field injection. Tuy nhiên, object được tạo bằng `new Client()` ngoài Spring **không** được xử lý annotation. Nếu field chưa được gán, truy cập nó có thể gây `NullPointerException`.

Field injection giấu dependency khỏi constructor và không phù hợp để cung cấp dependency bắt buộc vào field `final`. Với code mới cần invariant và dễ unit test, cân nhắc constructor injection (qua `@Autowired` hoặc một constructor duy nhất) thay vì dùng `@Resource` chỉ để tránh vài dòng constructor.

### 4.2. JavaBean setter và tên property mặc định

~~~java
// Cần bean có tên "archiveChannel".
private Channel selected;

@Resource(name = "archiveChannel")
public void setSelected(Channel selected) {
    this.selected = selected;
}
~~~

Khi có `name` tường minh, bean name **`archiveChannel`** được chọn. Tên setter `setSelected` không bắt buộc trùng bean name.

Ngược lại:

~~~java
// Không chỉ định name. Tên property suy ra là "smsChannel".
private Channel selected;

@Resource
public void setSmsChannel(Channel channel) {
    this.selected = channel;
}
~~~

Spring sử dụng **`smsChannel`** từ property tương ứng với tên method `setSmsChannel` theo quy ước JavaBeans; **không** dùng tên local parameter `channel` để đặt tên mặc định.

**Giới hạn:** `@Resource` dùng theo hợp đồng field/bean property setter — không giống `@Autowired` tùy ý annotate một `configure(A, B)` có nhiều tham số. Nếu cần một constructor hoặc arbitrary multi-parameter config method, ưu tiên `@Autowired`/factory method injection có type rõ ràng.

### 4.3. Alias là một tên hợp lệ

Khi bean được đăng ký với tên chính và alias:

~~~java
@Bean(name = {"archiveChannel", "oldArchiveChannel"})
Channel archiveChannel() {
    return () -> "ARCHIVE";
}
~~~

`@Resource(name="oldArchiveChannel")` vẫn tra cứu được **cùng bean** với `getBean("archiveChannel")`. Alias không tạo bean definition thứ hai, không sao chép instance và không biến singleton thành prototype.

## 5. Hạ tầng Spring xử lý `@Resource` và các lỗi quan trọng

Annotation `@Resource` được Spring nhận diện nhờ **`CommonAnnotationBeanPostProcessor`**, khác với `AutowiredAnnotationBeanPostProcessor` xử lý chủ yếu `@Autowired` và các annotation injection có liên quan.

~~~text
Đăng ký @Bean / class target
             |
       context.refresh()
             |
       Tạo Java instance
             |
 CommonAnnotationBeanPostProcessor
       nhận diện @Resource
             |
  Resolve theo bean name / fallback type
             |
    Gán field hoặc gọi property setter
             |
          Init hooks
             |
    Bean được trả cho client
~~~

`AnnotationConfigApplicationContext` thường đăng ký sẵn hạ tầng annotation này, khi các classes Jakarta tương ứng **có mặt trên classpath**. Với XML context tự cấu hình, `<context:annotation-config/>` hoặc component scan phù hợp có thể giúp kích hoạt các bean post-processors cần thiết.

**Không quên dependency compile/runtime**: `spring-context` là Spring Core Container, còn API khai báo `jakarta.annotation.Resource` được cung cấp bởi **`jakarta.annotation:jakarta.annotation-api`**. Bài thực hành khai báo cả hai trực tiếp để không phụ thuộc tình cờ vào transitive dependencies.

Một số quy tắc quan trọng khác:

- **Required theo tên đã chỉ định:** `@Resource(name="missingChannel")` không có tùy chọn `required=false` giống `@Autowired`; thiếu bean theo tên thường làm bean creation fail.
- **Sai type:** bean mang tên được chỉ định phải **tương thích với type của field/property**. Spring không tiêm một `String` vào field `Channel` chỉ vì cùng bean name.
- **Trùng type:** nhiều Channel beans không phải lỗi nếu `@Resource(name=...)` chỉ rõ một name hợp lệ.
- **Không auto-create:** `@Resource` trên class không được Spring quản lý hoặc bean class chưa đăng ký không tự tạo ra bean definition.
- **JNDI:** `mappedName`/các cấu hình JNDI trong môi trường Jakarta EE là câu chuyện khác. Trong Spring Core Container thông thường, `name` được resolve thành **bean name**; không mặc nhiên nói `@Resource(name=...)` luôn gọi JNDI. Nếu cấu hình `SimpleJndiBeanFactory` hoặc tùy chọn JNDI đặc thù, đường đi có thể khác.
- **Không thay thế Spring `Resource` API:** `jakarta.annotation.Resource` là **annotation DI**; `org.springframework.core.io.Resource` là **interface trừu tượng nguồn dữ liệu** như classpath/file/URL. Tên “Resource” giống nhau nhưng công dụng khác.

Lỗi thường xuất hiện khi eager singleton được tạo trong `refresh()`; nếu lazy/prototype, có thể đến lúc yêu cầu bean mới xuất hiện. Đừng dùng regex trên thông điệp exception thay cho việc đọc root cause và xác minh tên/type thực tế.

## 6. Project Maven Java 17 chạy độc lập: lựa chọn name và fallback

Demo có:
- Ba `Channel` beans: `emailChannel` (**`@Primary`**), `smsChannel`, `archiveChannel` cùng alias `oldArchiveChannel`.
- **ExplicitNameClient** có `@Resource(name="smsChannel")` → chọn SMS dù EMAIL là primary.
- **DefaultFieldClient** có field tên `smsChannel` → chọn SMS theo tên field.
- **ExplicitSetterClient** có `@Resource(name="archiveChannel")` trên `setSelected` → chọn ARCHIVE.
- **DefaultSetterClient** có setter `setArchiveChannel` → chọn ARCHIVE theo tên property.
- **AliasClient** dùng tên alias → cùng instance bean archive.
- **FallbackClient** có field tên không trùng bean nào → fallback theo type, chọn `emailChannel` do `@Primary`.
- Hai tình huống lỗi riêng: tên không tồn tại và bean có tên đúng nhưng sai type.

Dự án chỉ tạo console context và in các kiểm chứng định danh. **Không có mạng, file I/O, Spring Boot hay HTTP.**

### 6.1. Cấu trúc

~~~text
resource-by-name-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── ResourceByNameDemo.java
~~~

Yêu cầu: **JDK 17+**, Maven 3.9+ và repository Maven Central/mirror để tải dependencies lần đầu.

### 6.2. `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>resource-by-name-demo</artifactId>
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
        <dependency>
            <groupId>jakarta.annotation</groupId>
            <artifactId>jakarta.annotation-api</artifactId>
            <version>3.0.0</version>
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
                    <mainClass>demo.ResourceByNameDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`maven.compiler.release=17` yêu cầu biên dịch với target Java 17; Maven Compiler Plugin gọi `javac` với đúng classpath. Dùng import `jakarta.annotation.Resource` với dependency 3.0.0, không dùng `javax.annotation.Resource`. Không cần tự bật annotation processing plugin của Java compiler: injection thực thi **ở runtime bởi Spring**, không phải quá trình sinh code lúc compile.

### 6.3. `src/main/java/demo/ResourceByNameDemo.java`

~~~java
package demo;

import jakarta.annotation.Resource;
import org.springframework.beans.BeansException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

public final class ResourceByNameDemo {
    private ResourceByNameDemo() {
    }

    @FunctionalInterface
    public interface Channel {
        String label();
    }

    public static final class ExplicitNameClient {
        @Resource(name = "smsChannel")
        private Channel selected;

        public String selected() {
            return selected.label();
        }

        public boolean same(Channel expected) {
            return selected == expected;
        }
    }

    public static final class DefaultFieldClient {
        @Resource
        private Channel smsChannel;

        public String selected() {
            return smsChannel.label();
        }
    }

    public static final class ExplicitSetterClient {
        private Channel selected;
        private int calls;

        @Resource(name = "archiveChannel")
        public void setSelected(Channel selected) {
            this.selected = selected;
            calls++;
        }

        public String selected() {
            return selected.label();
        }

        public int calls() {
            return calls;
        }
    }

    public static final class DefaultSetterClient {
        private Channel selected;

        @Resource
        public void setArchiveChannel(Channel value) {
            selected = value;
        }

        public String selected() {
            return selected.label();
        }
    }

    public static final class AliasClient {
        @Resource(name = "oldArchiveChannel")
        private Channel selected;

        public boolean same(Channel expected) {
            return selected == expected;
        }
    }

    public static final class FallbackClient {
        @Resource
        private Channel noSuchDefaultName;

        public String selected() {
            return noSuchDefaultName.label();
        }

        public boolean same(Channel expected) {
            return noSuchDefaultName == expected;
        }
    }

    public static final class MissingNameClient {
        @Resource(name = "missingChannel")
        private Channel selected;
    }

    public static final class WrongTypeClient {
        @Resource(name = "labelBean")
        private Channel selected;
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean("emailChannel")
        @Primary
        public Channel emailChannel() {
            return () -> "EMAIL";
        }

        @Bean("smsChannel")
        public Channel smsChannel() {
            return () -> "SMS";
        }

        @Bean(name = {"archiveChannel", "oldArchiveChannel"})
        public Channel archiveChannel() {
            return () -> "ARCHIVE";
        }

        @Bean("labelBean")
        public String labelBean() {
            return "NOT_A_CHANNEL";
        }
    }

    public static void main(String[] args) {
        // 1) Context hợp lệ: ba beans cùng Channel type.
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(AppConfig.class);
            context.registerBean(ExplicitNameClient.class);
            context.registerBean(DefaultFieldClient.class);
            context.registerBean(ExplicitSetterClient.class);
            context.registerBean(DefaultSetterClient.class);
            context.registerBean(AliasClient.class);
            context.registerBean(FallbackClient.class);
            context.refresh();

            Channel sms = context.getBean("smsChannel", Channel.class);
            Channel archive =
                    context.getBean("archiveChannel", Channel.class);
            Channel email = context.getBean("emailChannel", Channel.class);

            ExplicitNameClient explicit =
                    context.getBean(ExplicitNameClient.class);
            ExplicitSetterClient setter =
                    context.getBean(ExplicitSetterClient.class);
            FallbackClient fallback =
                    context.getBean(FallbackClient.class);

            System.out.println("explicit.name=" + explicit.selected());
            System.out.println("explicit.sameSms=" + explicit.same(sms));
            System.out.println("default.field="
                    + context.getBean(DefaultFieldClient.class).selected());
            System.out.println("setter.explicit=" + setter.selected());
            System.out.println("setter.calls=" + setter.calls());
            System.out.println("setter.default="
                    + context.getBean(DefaultSetterClient.class).selected());
            System.out.println("alias.sameArchive="
                    + context.getBean(AliasClient.class).same(archive));
            System.out.println("alias.hasDefinition="
                    + context.containsBeanDefinition("oldArchiveChannel"));
            System.out.println("fallback.primary=" + fallback.selected());
            System.out.println("fallback.sameEmail=" + fallback.same(email));
            System.out.println("context.beanByType="
                    + context.getBean(Channel.class).label());
        }

        // 2) Có bean Channel, nhưng không có tên explicit cần tìm.
        try (var missing = new AnnotationConfigApplicationContext()) {
            missing.register(AppConfig.class);
            missing.registerBean(MissingNameClient.class);
            missing.refresh();
        } catch (BeansException expected) {
            System.out.println("missingExplicit.failed=true");
        }

        // 3) Có bean tên labelBean, nhưng đó là String, không phải Channel.
        try (var wrong = new AnnotationConfigApplicationContext()) {
            wrong.register(AppConfig.class);
            wrong.registerBean(WrongTypeClient.class);
            wrong.refresh();
        } catch (BeansException expected) {
            System.out.println("wrongType.failed=true");
        }
    }
}
~~~

### 6.4. Tại sao demo này kiểm tra đúng by-name?

1. Spring thấy cả `emailChannel` và `smsChannel` cùng là Channel, nhưng `@Resource(name="smsChannel")` chọn **SMS** rõ ràng. `@Primary` của email không thay đổi tên được yêu cầu.
2. `DefaultFieldClient.smsChannel` không khai `name`, nên tên mặc định từ **field** khớp `smsChannel`. Dù email primary, **bean name match** được sử dụng trước.
3. `setSelected(...)` có annotation tên `archiveChannel`, nên tên Java setter **không quyết định** bean name.
4. `setArchiveChannel(...)` không khai `name`, nên tên JavaBeans property **`archiveChannel`** khớp bean.
5. `oldArchiveChannel` là **alias** cho bean `archiveChannel`. Spring nhận alias khi lookup; phép `==` xác nhận cùng object singleton.
6. `noSuchDefaultName` không có bean cùng tên. Chỉ lúc này lookup mới fallback theo **type**; `emailChannel` được `@Primary`, nên chọn EMAIL.
7. Trong `MissingNameClient`, **explicit name không tồn tại** dù có ba beans cùng type. Spring không bỏ `name` để tùy ý chọn bean khác.
8. Trong `WrongTypeClient`, bean `labelBean` tồn tại nhưng là String; không thể gán vào field Channel. `@Resource` không bắt một Java field nhận một type sai.
9. `@Bean` và `registerBean` tạo nguồn metadata cho context; các lớp Client không bắt buộc có `@Component` nếu đã được **đăng ký tường minh**. Spring xử lý `@Resource` trên những instance do container tạo.

**Đây là console demo chủ đích:** hai context lỗi có thể phát thông báo khởi động thất bại từ logger. Expected stdout chỉ tính các lệnh `System.out.println` trong `main`, không áp đặt exact stack trace của Spring.

## 7. Lệnh chạy và expected stdout

Mở terminal ở thư mục `resource-by-name-demo`, kiểm tra:

~~~bash
java -version
javac -version
mvn -version
~~~

Phiên bản Java/Maven hiển thị tùy môi trường. Từ thư mục có `pom.xml`, chạy:

~~~bash
mvn -q clean compile exec:java
~~~

**13 dòng stdout do chính chương trình Java in**, không tính Maven/logging backend:

~~~text
explicit.name=SMS
explicit.sameSms=true
default.field=SMS
setter.explicit=ARCHIVE
setter.calls=1
setter.default=ARCHIVE
alias.sameArchive=true
alias.hasDefinition=false
fallback.primary=EMAIL
fallback.sameEmail=true
context.beanByType=EMAIL
missingExplicit.failed=true
wrongType.failed=true
~~~

Các giá trị này là deterministic: không phụ thuộc timezone, random, network, `hashCode()` hay tên nội bộ của Spring. Trong phép so `==`, chỉ kiểm tra **reference identity** của những singleton lấy từ **cùng context**; `String.equals` sẽ dùng khi so giá trị nội dung.

Lưu ý `context.getBean(Channel.class)` chọn primary vì có nhiều candidates cùng type. Nếu bỏ `@Primary` và không có qualifier/other discriminator, chính lookup theo type có thể mơ hồ, trong khi `@Resource(name="smsChannel")` vẫn có thể tìm được SMS bằng tên.

## 8. Java 17 thuần: phân biệt tra theo tên với tra theo type

Để thấy ý nghĩa by-name mà không nhầm với annotation runtime, tạo tệp độc lập **`ManualNameSelection.java`**, chạy bằng `javac`/`java` (không cần Spring/Jakarta):

~~~java
import java.util.Map;

public class ManualNameSelection {
    interface Channel {
        String label();
    }

    public static void main(String[] args) {
        Map<String, Channel> beans = Map.of(
                "emailChannel", () -> "EMAIL",
                "smsChannel", () -> "SMS");

        Channel chosen = beans.get("smsChannel");
        System.out.println("selected=" + chosen.label());
        System.out.println("missingName="
                + (beans.get("unknownChannel") == null));
        System.out.println("otherAvailable="
                + beans.containsKey("emailChannel"));
    }
}
~~~

Chạy từ thư mục chứa file:

~~~bash
javac ManualNameSelection.java
java ManualNameSelection
~~~

Stdout:

~~~text
selected=SMS
missingName=true
otherAvailable=true
~~~

Map Java ở đây **không phải Spring BeanFactory**; nó chỉ giúp hình dung rằng tra tên `smsChannel` khác tra tên `emailChannel`, và một tên không tồn tại không có nghĩa không có objects khác. Spring bổ sung type checking, bean definitions, scope, lifecycle, alias và optional fallback với **trường hợp không khai tên tường minh**.

**Không suy từ map Java rằng Spring luôn phải có `@Primary`**. Đối với explicit name, dù có nhiều implementations cùng type, một name match hợp lệ đủ để xác định bean cần tiêm (với điều kiện type tương thích).

## 9. Các trường hợp nâng cao và giới hạn cần nhớ

### `@Resource` không phải một annotation optional tổng quát

Annotation `jakarta.annotation.Resource` **không có** thuộc tính `required=false` như Spring `@Autowired`. Nếu một dependency thật sự optional theo nghiệp vụ, xem xét `Optional<T>`/provider hoặc cách thiết kế default strategy tương thích với Spring; không giả định một `@Resource(name="...")` sai tên sẽ im lặng.

Một Spring bean có nhiều `@Resource` fields vẫn phụ thuộc vào những resources ấy theo từng injection point. Nếu một required resource không resolve được, bean creation có thể fail ngay cả khi các fields khác đã được inject trước đó.

### Fallback có thể được cấu hình

`CommonAnnotationBeanPostProcessor` có setting `setFallbackToDefaultTypeMatch(boolean)`, mặc định `true`. Trong cấu hình custom đặt `false`, `@Resource` **không tên tường minh** cũng phải tìm theo tên mặc định; không còn fallback theo type khi thiếu default name. Đừng xây business behavior dựa trên một custom setting mà chưa kiểm tra.

### Tham chiếu những hạ tầng được Spring tự cung cấp

Tài liệu Spring ghi nhận khi `@Resource` không có `name`, fallback theo type còn có thể resolve một số dependency well-known như `ApplicationContext`, `BeanFactory`, `ResourceLoader` hoặc `ApplicationEventPublisher`. Điều này không có nghĩa bạn đã đăng ký thêm một bean definition dưới mọi tên field tùy ý.

### Trùng tên bean không phải cùng việc với alias

Đăng ký hai bean definitions cùng tên có thể gây lỗi overriding hoặc phụ thuộc vào chính sách override. Ngược lại alias như `oldArchiveChannel` trỏ tới **một definition** duy nhất. Khi debug, `containsBean("oldArchiveChannel")` có thể trả `true` nhưng `containsBeanDefinition("oldArchiveChannel")` là `false`.

### `lookup`, `mappedName`, JNDI

`@Resource` còn có những attributes phục vụ môi trường Jakarta/JNDI tùy cách cấu hình. Chúng **không** phải lý do thay `@Resource(name=...)` thành một JNDI lookup trong Spring application thông thường. Hãy đối chiếu tài liệu Spring và annotation API nếu ứng dụng chạy trên Jakarta EE server.

### Độ ổn định của bean name là hợp đồng cấu hình

Nếu một service được viết `@Resource(name="smsChannel")`, việc rename bean này có thể làm ứng dụng không khởi động. Khi cần đổi tên để refactor, dùng alias có kiểm soát trong thời gian chuyển đổi hoặc cập nhật injection points và tests đồng bộ. Không thêm nhiều aliases tùy tiện để giải quyết mơ hồ do thiết kế.

## 10. Lỗi phổ biến và hướng chẩn đoán

| Triệu chứng | Nguyên nhân khả dĩ | Kiểm tra/cách sửa |
| --- | --- | --- |
| `package jakarta.annotation does not exist` | Thiếu Jakarta Annotations API trên compile classpath | Thêm dependency `jakarta.annotation:jakarta.annotation-api` và sync Maven |
| `@Resource` field vẫn null khi object được `new` | Object ngoài Spring IoC Container | Lấy Spring bean hoặc truyền dependency qua constructor trong unit test |
| Bean tồn tại nhưng `@Resource(name="...")` báo không tìm thấy | Tên explicit không trùng bean name hoặc alias thực | Kiểm `@Bean(name=...)`, tên scan, alias, context hiện hành |
| Có bean name đúng nhưng injection fail type | Type field/setter không tương thích với bean thực | Sửa bean name hoặc khai báo type; không ép cast sai |
| `@Resource` trùng tên một `String`, field cần Channel | Name match nhưng wrong type | Sửa tên field/name hoặc definition tương ứng |
| `@Resource` không tên chọn primary ngoài ý muốn | Default field/property name không có bean → fallback type | Đặt `name` tường minh hoặc sửa tên field cho rõ |
| Không thể chọn bean vì nhiều candidates fallback | Không có primary/selection rule phù hợp | Đặt tên explicit cho `@Resource`, hoặc cấu hình type resolution |
| `@Primary` không thắng `@Resource(name=...)` | Explicit by-name có ưu tiên; đây là hành vi đúng | Đừng dựa vào primary để thay tên đã chỉ định |
| Setter method không được gọi | Không phải Spring-managed bean, processor thiếu hoặc method không phải property setter phù hợp | Kiểm registration, annotation, chữ ký JavaBean setter |
| `@Resource` trên constructor/method nhiều tham số không hoạt động | Vị trí annotation không được hỗ trợ cho DI kiểu đó | Dùng `@Autowired`/constructor/factory-method injection |
| Có `javax.annotation.Resource` trên Spring 7 nhưng không hoạt động | Package cũ, không phải `jakarta.annotation.Resource` được hỗ trợ | Dùng Jakarta import/dependency đúng version |
| Log lỗi startup xuất hiện dù catch exception | Spring logger có thể ghi sự cố khi refresh fail | So từng dòng stdout do application in, xem root cause thực tế |
| XML context không xử lý `@Resource` | Annotation processor chưa được đăng ký | Dùng `<context:annotation-config/>` hoặc cấu hình thích hợp |
| `@Resource` nhầm với file resource | Cùng chữ “Resource” nhưng khác API | So `jakarta.annotation.Resource` và `org.springframework.core.io.Resource` |
| Bean cùng interface nhưng `@Autowired` chọn sai | Type/qualifier candidate resolution khác by-name | Dùng qualifier/primary đúng contract hoặc explicit `@Resource` |

**Kiểm tra có thứ tự:** (1) bean đích có được Spring quản lý không; (2) Jakarta import có đúng không; (3) `CommonAnnotationBeanPostProcessor` có chạy không; (4) name explicit/default là gì; (5) bean name/alias tồn tại không; (6) type tương thích không; (7) có fallback/type ambiguity không. Phân biệt lỗi Maven/classpath với lỗi Spring bean resolution.

Không suy luận rằng trùng Java class name dẫn đến trùng bean name; class `EmailChannel` và tên đăng ký `emailChannel` có thể là hai lớp định danh khác nhau. Khi kiểm thử bằng tên, thay đổi strategy component scan hoặc đổi tên method `@Bean` cũng có thể thay hợp đồng nếu không khai báo tên tường minh.

## 11. Thực hành dự đoán và kiểm chứng

Hãy làm **từng thay đổi độc lập** trong một bản sao dự án rồi khôi phục source gốc trước khi so expected stdout mục 7:

1. **Đổi explicit name:** `@Resource(name="emailChannel")` ở ExplicitNameClient. Dự đoán `explicit.name=EMAIL`, `explicit.sameSms=false`.
2. **Bỏ `@Primary`:** để ba beans Channel cùng type. Phần by-name nào vẫn thành công, và FallbackClient/lookup theo type sẽ thay đổi ra sao?
3. **Đổi field name:** sửa `DefaultFieldClient.smsChannel` thành `selectedChannel` nhưng không thêm `name`. Dự đoán khả năng fallback primary thay vì chọn SMS.
4. **Chỉ định field name:** thêm `@Resource(name="smsChannel")` sau khi rename field. Kiểm chứng name explicit làm hành vi trở lại SMS.
5. **Rename setter:** đổi `setArchiveChannel` thành `setSmsChannel` trong DefaultSetterClient, giữ một tham số và không khai `name`. Dự đoán `setter.default=SMS`.
6. **Explicit setter:** đổi `setSelected` thành tên setter khác nhưng giữ `@Resource(name="archiveChannel")`. Bean được chọn có đổi không?
7. **Alias:** bỏ `oldArchiveChannel` khỏi danh sách `@Bean(name={...})`. Quan sát AliasClient fail bootstrap; không có bean thứ hai nào bị xóa.
8. **Sai type:** đổi kiểu field `WrongTypeClient.selected` thành `String`. Dự đoán tình huống name match `labelBean` trở nên hợp lệ.
9. **Không có bean:** thử `@Resource(name="missingChannel")` rồi thêm đúng bean name này ở configuration; quan sát context không còn báo thiếu name.
10. **Tùy biến fallback:** nghiên cứu `CommonAnnotationBeanPostProcessor.setFallbackToDefaultTypeMatch(false)`, dự đoán ảnh hưởng tới FallbackClient nhưng không ảnh hưởng name explicit.
11. **Kiểm tra identity:** in `context.getBean("oldArchiveChannel")==context.getBean("archiveChannel")`; với singleton mặc định sẽ `true`.
12. **Constructor injection:** viết class có constructor nhận Channel và được tạo bằng `@Bean` method; dùng `@Qualifier` ở factory-method argument thay vì đặt `@Resource` trên constructor.
13. **Unit test Java thuần:** truyền `Channel` fake vào constructor class nghiệp vụ. So sánh khả năng test với field-injected class đòi context/reflection.
14. **Nhiều contexts:** tạo hai contexts từ AppConfig, so instances `smsChannel`; Spring singleton mặc định theo mỗi bean definition/context, không phải global JVM singleton.

### Checklist

- [ ] Import đúng **`jakarta.annotation.Resource`**, không phải `javax.annotation.Resource`.
- [ ] Biết `@Resource(name="x")` tìm **bean name/alias x** và kiểm tra type.
- [ ] Biết tên mặc định đến từ **field name** hoặc **JavaBean setter property name**.
- [ ] Biết fallback theo type **chỉ dành cho trường hợp không khai name tường minh**, khi default name không có và cấu hình cho phép.
- [ ] Hiểu tác động `@Primary` trong fallback, không nhầm với explicit by-name.
- [ ] Dùng `@Resource` đúng vị trí field/property setter, không tự áp dụng như `@Autowired` trên constructor/multi-param method.
- [ ] Phân biệt alias với bean definition mới.
- [ ] Biết khi thiếu name hoặc sai type, bean creation có thể thất bại.
- [ ] Chạy được Maven demo Java 17 ra chính xác 13 dòng stdout, và Java thuần `javac`/`java` ra 3 dòng.
- [ ] Biết vai trò `CommonAnnotationBeanPostProcessor` và dependency `jakarta.annotation-api`.

## 12. Tóm tắt và tài liệu chính thức

`@Resource` thể hiện **một tham chiếu bean theo tên** trong Spring Core Container:

- **Có `name`:** resolve bean theo tên đó/alias; không tùy ý chuyển sang bean cùng type khác khi name không tồn tại.
- **Không có `name`:** dùng tên field hoặc tên JavaBean property của setter. Nếu bean name ấy không có, Spring có thể fallback sang type matching theo chính sách processor mặc định.
- **Kiểu dữ liệu luôn quan trọng:** name match không làm biến mất quy tắc type-safety của Java.
- **`@Primary` và `@Qualifier` không thay thế định nghĩa name của `@Resource`**; chúng chủ yếu thuộc việc chọn dependency theo type/qualifier.
- Bean phải thuộc Spring context và annotation processor phải có mặt. Tự tạo object bằng `new` không thực hiện injection.

Tài liệu:
- [Spring Framework — Injection with @Resource](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/resource.html)
- [Spring Framework — Annotation-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config.html)
- [Spring Framework — Fine-tuning Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html)
- [Spring Framework — Bean Definition Naming and Aliasing](https://docs.spring.io/spring-framework/reference/core/beans/definition.html)
- [Spring API — CommonAnnotationBeanPostProcessor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/CommonAnnotationBeanPostProcessor.html)
- [Jakarta Annotations API — `Resource`](https://jakarta.ee/specifications/annotations/3.0/apidocs/jakarta.annotation/jakarta/annotation/resource)
- [Maven Central — Jakarta Annotations API 3.0.0](https://central.sonatype.com/artifact/jakarta.annotation/jakarta.annotation-api/3.0.0)

**Kết luận thực hành:** Khi `@Resource` chọn một bean gây bất ngờ, đừng vội đổi `@Primary`. Trước hết xác định **name tường minh hay mặc định**, xem bean/alias có tồn tại không, kiểm tra **type**, rồi mới xét **fallback theo type**. Phân biệt thứ tự ấy giúp bạn dùng `@Resource` nhất quán, dễ kiểm thử và tránh lỗi wiring trong ứng dụng Spring.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="autowired-008">@Named, @Qualifier và phạm vi hỗ trợ của chuẩn Jakarta</a>

<details>
<summary>Xem chi tiết</summary>

# `@Named`, `@Qualifier` và phạm vi hỗ trợ của chuẩn Jakarta

## 1. Ba tên annotation cần phân biệt

Spring Framework 6 hỗ trợ một tập annotation **Jakarta Dependency Injection (JSR-330)** thuộc package `jakarta.inject` bên cạnh các annotation riêng của Spring. Sử dụng các annotation chuẩn có thể làm lớp tiêu thụ ít phụ thuộc API Spring hơn, nhưng không có nghĩa mọi khả năng của Core Container đều thuộc Jakarta DI.

- **`jakarta.inject.Named`**: đặt tên cho component hoặc chỉ rõ ứng viên cần inject bằng tên, chẳng hạn `@Named("upperPort")`.
- **`jakarta.inject.Qualifier`**: **meta-annotation** để định nghĩa qualifier tùy chỉnh (`@Fast`, `@Offline`). Nó không nhận một giá trị String theo cú pháp `@Qualifier("upperPort")`.
- **`org.springframework.beans.factory.annotation.Qualifier`**: annotation **riêng của Spring** dùng trực tiếp ở injection point, có thể nhận chuỗi như `@Qualifier("upperPort")`.

Để hiểu cách chọn đối tượng, còn cần biết `jakarta.inject.Inject` là annotation chỉ điểm injection và `jakarta.inject.Provider<T>` cho lookup theo nhu cầu.

~~~text
                    Spring ApplicationContext
                        |
    +-------------------+------------------+
    |                   |                  |
@Named("upperPort") @Named("lowerPort") @Named("textService")
  UpperPort           LowerPort             TextService
    |                   |                        |
    +------ TextPort ----+              @Inject constructor
                                             |
                             @Named("upperPort") TextPort
                             Provider<RequestToken>
                                             |
                                   gọi process(text)
                                             |
                               Provider.get() lấy token
                               UpperPort.format(text)
~~~

Nếu không có quy tắc chọn rõ, hai bean cùng thực hiện `TextPort` có thể khiến một injection point kiểu `TextPort` bị mơ hồ. `@Named("upperPort")` trên tham số giải quyết lựa chọn **tại chính điểm tiêu thụ**.

## 2. `@Named` trên bean và trên injection point

### Trên class: đặt bean name

~~~java
import jakarta.inject.Named;

@Named("upperPort")
public class UpperPort implements TextPort {
    // Phải triển khai đủ method của TextPort.
}
~~~

Đây là **đoạn minh họa**, chưa runnable vì còn thiếu định nghĩa `TextPort` và method. Trong Spring, class có `@Named` có thể được component scanning nhận diện gần giống `@Component`. Nếu không chỉ định tên trong `@Named`, Spring có thể áp dụng cơ chế tự sinh bean name.

**Quan trọng:** chỉ có annotation trên class **không tự làm class xuất hiện trong mọi container**. Spring phải được cấu hình component scan đúng package, hoặc đăng ký class rõ bằng `context.register(...)`, hoặc có bean definition tương ứng.

### Trên tham số: hạn chế ứng viên

~~~java
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Inject
public TextService(@Named("upperPort") TextPort port) {
    this.port = port;
}
~~~

Đây cũng là snippet minh họa. `@Named("upperPort")` ở đây nói rằng tham số cần bean có kiểu tương thích với `TextPort` và phù hợp tên/qualifier `upperPort`. Nó không tạo thêm instance, cũng không sửa bean khác tên `lowerPort`.

Nếu tên ứng viên thực tế là `primaryUpperPort` nhưng injection lại ghi `upperPort`, Spring có thể báo thiếu dependency. Khi sửa bean name, cần cập nhật nơi yêu cầu tên ấy hoặc thiết kế qualifier trừu tượng hơn.

### `@Named` khác Java class name

`UpperPort` là tên class; `upperPort` là bean name trong container. Một class có thể được đăng ký nhiều bean definitions khác nhau; ngược lại một bean definition có tên riêng không bắt buộc giống tên class. `@Named` dựa vào cấu hình tên/qualifier, không chọn implementation “đầu tiên” theo thứ tự khai báo.

## 3. Jakarta `@Qualifier` là meta-annotation

Một custom qualifier có ý nghĩa nghiệp vụ là `@Fast`. Tạo file `Fast.java`:

~~~java
package demo;

import jakarta.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({
    ElementType.TYPE,
    ElementType.FIELD,
    ElementType.PARAMETER,
    ElementType.METHOD
})
public @interface Fast {}
~~~

Đây là **file minh họa định nghĩa annotation**, không phải phần bắt buộc của ứng dụng runnable ở mục 7. `@Qualifier` trong import là `jakarta.inject.Qualifier` và nằm trên **khai báo `Fast`**. Retention runtime giúp container đọc được annotation khi giải quyết dependency.

Có thể dùng:

~~~java
@Fast
@Named("fastPort")
class FastPort implements TextPort {
    // ...
}

@Inject
public SomeService(@Fast TextPort port) {
    this.port = port;
}
~~~

Hai khối **chưa compile độc lập** vì phải khai báo các kiểu, method và đăng ký bean. Khi container hỗ trợ custom qualifier, ứng viên được đánh dấu `@Fast` có thể được chọn cho tham số cùng qualifier.

**Sai:**

~~~java
// Không hợp lệ với jakarta.inject.Qualifier:
// @jakarta.inject.Qualifier("fastPort")
~~~

Annotation Jakarta `Qualifier` không có thuộc tính `value()` kiểu String. Nếu muốn chọn theo tên String, dùng `@Named("fastPort")`. Nếu muốn custom qualifier, tạo annotation riêng có `@Qualifier` làm meta-annotation.

## 4. Spring `@Qualifier` khác Jakarta `@Qualifier`

Ví dụ dùng annotation riêng của Spring:

~~~java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

@Autowired
public SomeService(@Qualifier("upperPort") TextPort port) {
    this.port = port;
}
~~~

Đây là đoạn minh họa. Trong file này, `@Qualifier("upperPort")` là **Spring Qualifier**, không phải Jakarta. Hai annotation có cùng simple name, vì thế nếu cần cả hai trong cùng source Java, phải dùng **tên đầy đủ** cho ít nhất một cái để tránh import collision.

| Ý định | Jakarta DI | Spring |
| --- | --- | --- |
| Chọn ứng viên tên `upperPort` | `@Named("upperPort")` | `@Qualifier("upperPort")` |
| Định nghĩa marker `@Fast` | Meta `@jakarta.inject.Qualifier` | Meta `@org.springframework.beans.factory.annotation.Qualifier` |
| Cấp dependency | `@Inject` | `@Autowired` |
| Bean name / stereotype | `@Named` | `@Component` và các stereotype liên quan |

**Tính đúng của qualifier phụ thuộc cách bean được đăng ký.** Không nên mặc định `@Primary` sẽ bỏ qua một yêu cầu qualifier cụ thể hoặc một `@Named` không khớp tên sẽ được sửa tự động. Lựa chọn nhiều ứng viên cần cấu hình nhất quán và test.

## 5. `@Inject` thay `@Autowired` đến mức nào?

`@Inject` có thể được Spring xử lý trên constructor, field và method injection points:

~~~java
import jakarta.inject.Inject;

@Inject
public void setInventory(InventoryPort inventory) {
    this.inventory = inventory;
}
~~~

Snippet cần `InventoryPort` và field nếu muốn biên dịch. Khi bean do Spring quản lý, container tìm bean phù hợp và áp dụng injection tại thời điểm cấu hình.

| Tính năng | `@Autowired` của Spring | `@Inject` Jakarta |
| --- | --- | --- |
| Constructor injection | Có | Có |
| Field injection | Có | Có |
| Method injection | Có | Có |
| `required=false` trên annotation | Có | **Không có** |
| `Optional<T>` tại injection point trong Spring | Có | Có nhờ Spring tích hợp |
| `new` ngoài container có được autowire tự động? | Không | Không |

Sai cú pháp:

~~~java
// Không được hỗ trợ:
// @Inject(required = false)
~~~

`jakarta.inject.Inject` không định nghĩa thuộc tính `required`. Nếu dependency là tùy chọn trong Spring, một giải pháp là:

~~~java
@Inject
public void setMetrics(java.util.Optional<MetricsPort> metrics) {
    this.metrics = metrics;
}
~~~

Đây chỉ là minh họa. Spring có thể truyền `Optional.empty()` nếu không có ứng viên thích hợp. Khả năng Spring xử lý `Optional<T>` **không đồng nghĩa tất cả DI containers đều phải có semantics giống hệt**.

Khi bean class chỉ có một constructor, Spring Framework hiện đại thường có thể chọn constructor đó ngay cả khi không ghi `@Autowired` hoặc `@Inject`, miễn bean đã được đăng ký và dependency được resolve đúng. Tuy nhiên `@Inject` có thể làm mục đích DI rõ hơn.

## 6. `Provider<T>` và scope của bean đích

`jakarta.inject.Provider<T>` là một API chuẩn để trì hoãn việc **yêu cầu một bean đích** cho đến khi `get()` được gọi.

~~~java
private final Provider<RequestToken> tokens;

@Inject
public TextService(Provider<RequestToken> tokens) {
    this.tokens = tokens;
}

public RequestToken nextToken() {
    return tokens.get();
}
~~~

Ví dụ chỉ minh họa, chưa đủ field/import/class nếu tách riêng. Trong Spring:

- Provider cho **singleton** thường trả cùng instance ở nhiều lần `get()`.
- Provider cho **prototype** có thể trả một instance mới mỗi lần `get()`.
- Provider đích không có bean, nhiều bean mơ hồ hoặc bean creation lỗi có thể gây exception **lúc gọi `get()`**.
- Spring `ObjectProvider<T>` cung cấp thêm các API như `getIfAvailable()`, `stream()`, không có đầy đủ trong Jakarta `Provider<T>`.

**Provider không thay scope.** Nó chỉ thay cách/thời điểm yêu cầu dependency. Vì vậy ví dụ runnable sẽ chủ động gắn Spring `@Scope("prototype")` vào bean `RequestToken`.

### `@Singleton` và default scope

Jakarta `@Singleton` mô tả một scope chuẩn, nhưng **trong Spring** các bean được đăng ký qua `@Named` vẫn theo mặc định **singleton theo bean definition trong mỗi container** nếu không cấu hình khác. Đây không phải global singleton duy nhất cho toàn JVM. Không suy từ quy tắc default của một DI runtime khác rằng Spring `@Named` mặc định prototype.

Spring muốn scope prototype thì dùng `@Scope("prototype")` hoặc bean definition metadata. `jakarta.inject.Scope` là **meta-annotation** dùng tạo custom scope annotation; nó không tự gán một bean vào scope request/prototype.

## 7. Chương trình Java 17 / Spring Framework 6.2 chạy hoàn chỉnh

Ví dụ có hai implementations của một interface `TextPort`, dùng `@Named` để định danh và inject đúng một implementation. `TextService` còn nhận `Provider<RequestToken>` để kiểm tra đối tượng prototype mới cho mỗi lần xử lý. Không dùng Spring Boot, server hoặc database.

### 7.1. Cấu trúc và `pom.xml`

Cần JDK 17+, Maven:

~~~text
jakarta-named-demo/
├── pom.xml
└── src/main/java/demo/JakartaNamedDemo.java
~~~

`pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>jakarta-named-demo</artifactId>
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
            <groupId>jakarta.inject</groupId>
            <artifactId>jakarta.inject-api</artifactId>
            <version>2.0.0</version>
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
                    <mainClass>demo.JakartaNamedDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 7.2. Source `JakartaNamedDemo.java`

~~~java
package demo;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

public final class JakartaNamedDemo {
    private JakartaNamedDemo() {}

    public interface TextPort {
        String format(String input);
    }

    @Named("upperPort")
    public static final class UpperPort implements TextPort {
        @Override
        public String format(String input) {
            return input.toUpperCase(Locale.ROOT);
        }
    }

    @Named("lowerPort")
    public static final class LowerPort implements TextPort {
        @Override
        public String format(String input) {
            return input.toLowerCase(Locale.ROOT);
        }
    }

    public record RequestToken(int number) {}

    @Named("textService")
    public static final class TextService {
        private final TextPort port;
        private final Provider<RequestToken> tokens;

        @Inject
        public TextService(@Named("upperPort") TextPort port,
                           Provider<RequestToken> tokens) {
            this.port = Objects.requireNonNull(port);
            this.tokens = Objects.requireNonNull(tokens);
        }

        public String process(String input) {
            RequestToken token = tokens.get();
            return token.number() + ":" + port.format(input);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        private final AtomicInteger counter = new AtomicInteger();

        @Bean
        @Scope("prototype")
        public RequestToken requestToken() {
            return new RequestToken(counter.incrementAndGet());
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            // Đăng ký tường minh: không cần @ComponentScan.
            context.register(AppConfig.class,
                    UpperPort.class, LowerPort.class, TextService.class);
            context.refresh();

            TextService service = context.getBean(TextService.class);
            System.out.println("PORT_COUNT "
                    + context.getBeanNamesForType(TextPort.class).length);
            System.out.println("FIRST " + service.process("Hello"));
            System.out.println("SECOND " + service.process("Java"));
            System.out.println("LOWER "
                    + context.getBean("lowerPort", TextPort.class)
                             .format("SpRiNg"));
            System.out.println("SAME "
                    + (service == context.getBean(TextService.class)));
        }
    }
}
~~~

Nested classes ở đây là `public static` nên có thể được đăng ký tường minh bằng `register(...)`. `@Named("upperPort")` và `@Named("lowerPort")` giúp Spring đặt tên hai bean; `@Inject` trên constructor yêu cầu ứng viên `upperPort`. `AppConfig` tạo token prototype bằng annotation scope riêng của Spring.

### 7.3. Chạy và stdout

Tại thư mục `jakarta-named-demo`:

~~~bash
mvn -version
mvn -q compile exec:java
~~~

`javac/java` trực tiếp sẽ cần classpath có các Spring và Jakarta dependency jars. Vì vậy dùng Maven để giải quyết dependency và chạy ứng dụng là cách chính xác, đơn giản hơn trong ví dụ này; `maven.compiler.release=17` yêu cầu biên dịch tương thích Java 17.

**Dòng stdout do chương trình in:**

~~~text
PORT_COUNT 2
FIRST 1:HELLO
SECOND 2:JAVA
LOWER spring
SAME true
~~~

Maven và JVM có thể in thông tin/log khác; khối trên chỉ là các dòng do `System.out.println` chủ động tạo.

### 7.4. Vì sao có output đó?

1. `context.register` đăng ký `AppConfig` và ba class, sau đó `refresh` xử lý bean definitions.
2. `getBeanNamesForType(TextPort.class).length` bằng **2** vì hai bean khác nhau cùng interface.
3. `TextService` nhận `upperPort` nhờ qualifier `@Named("upperPort")` ở tham số, không bị mơ hồ với `lowerPort`.
4. `process("Hello")` yêu cầu token qua `Provider.get()`. Bean `RequestToken` là prototype; lần đầu nhận ID 1, UpperPort trả `HELLO`.
5. Lần thứ hai provider yêu cầu token mới ID 2, UpperPort trả `JAVA`.
6. `getBean("lowerPort", TextPort.class)` chọn implementation còn lại, chuyển `SpRiNg` thành `spring`.
7. `TextService` theo scope singleton mặc định trong Spring, nên hai lần lookup trả cùng reference và in `SAME true`.

**Provider không bắt buộc luôn tạo mới**; chính `@Scope("prototype")` làm factory tạo một `RequestToken` mới mỗi lần được yêu cầu.

## 8. Chuẩn Jakarta hỗ trợ tới đâu và thiếu những gì?

| Tính năng Spring | Jakarta DI tương ứng | Ghi chú |
| --- | --- | --- |
| `@Autowired` | `@Inject` | Không có thuộc tính `required` |
| `@Component` | `@Named` | Không có mô hình composed stereotypes đầy đủ như Spring |
| `@Qualifier("beanName")` | `@Named("beanName")` | `jakarta.inject.Qualifier` chỉ là meta-annotation |
| Qualifier annotation tự định nghĩa | `@jakarta.inject.Qualifier` | Cần custom annotation với retention, targets phù hợp |
| `@Scope("singleton")` | `@Singleton` | Spring giữ default singleton khi dùng `@Named` |
| `@Scope("prototype")` | Không có tương đương trực tiếp cho Spring prototype | Thường dùng Spring `@Scope` |
| `@Lazy` | Không có annotation tương đương trực tiếp | Spring `@Lazy` hoặc provider |
| `@Value` | Không có annotation tương đương trực tiếp | Spring property configuration |
| `ObjectFactory<T>` | `Provider<T>` | Jakarta cung cấp `get()`; Spring có mở rộng |
| `@Profile`/`@Conditional` | Không thuộc `jakarta.inject` | Cần Spring configuration |

`@Named` không tự biến service thành HTTP-scoped hay kích hoạt AOP, transaction, retry. Đây là các tính năng không được bảo đảm bởi Jakarta DI API.

### `jakarta.inject` khác `jakarta.annotation`

`@Inject`, `@Named`, `@Qualifier`, `@Singleton`, `Provider<T>` thuộc Jakarta DI/JSR-330. `@PostConstruct`, `@PreDestroy` và `@Resource` thuộc Jakarta Annotations/JSR-250; **không** đi kèm trong `jakarta.inject-api`. Khi cần chúng phải có dependency tương ứng, như `jakarta.annotation-api`.

### Spring 6 và `javax.inject` cũ

Các tutorial Spring đời cũ có thể import `javax.inject.*`. Ví dụ cho Spring Framework 6 nên dùng `jakarta.inject.*` cùng artifact thích hợp. Không thay thế import máy móc khi chưa kiểm tra phiên bản Spring và dependencies hiện có.

### `@Value` hay `@Lazy` không có trong `jakarta.inject`

Ví dụ Spring-specific:

~~~java
// Chỉ minh họa Spring, không phải API Jakarta DI.
@org.springframework.beans.factory.annotation.Value("${app.title:Demo}")
private String title;
~~~

Để giữ domain ít phụ thuộc Spring, có thể đọc configuration ở composition root rồi truyền value qua constructor, hoặc inject một config object đã được validate. `Provider<T>` là công cụ lazy lookup, **không thay** cơ chế placeholder property.

## 9. Sử dụng custom qualifier có ý nghĩa nghiệp vụ

Nếu hệ thống có nhiều adapter theo vai trò và tên bean dễ đổi, custom qualifier giúp thể hiện ý nghĩa `@Offline` hay `@Preferred`. Chẳng hạn:

~~~java
package demo;

import jakarta.inject.Qualifier;
import java.lang.annotation.*;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PARAMETER})
public @interface Offline {}
~~~

Đây là file `Offline.java` minh họa, không phải file chạy của mục 7. Gắn `@Offline` trên bean candidate và ở injection point, đồng thời bảo đảm bean registration và type phù hợp. Spring hỗ trợ qualifier metadata do Jakarta meta-annotation đánh dấu.

Không nên định nghĩa hàng chục marker qualifiers nếu chỉ khác tên một bean đơn giản. Ngược lại, dùng tên chuỗi `@Named("adapter23")` rải khắp domain có thể làm business code gắn cứng với registry; cân nhắc qualifier biểu đạt vai trò hoặc lựa chọn implementation tại configuration.

## 10. Lỗi điển hình và cách chẩn đoán

| Vấn đề | Cách xử lý |
| --- | --- |
| Import `jakarta.inject.Inject` bị lỗi compiler | Thêm đúng `jakarta.inject-api` vào dependency/classpath |
| Bean `@Named` không tồn tại | Xác minh scan đúng package hoặc register class |
| `@Named("upperPort")` không chọn được bean | Kiểm tra tên bean thực tế và type/qualifier |
| Hai bean cùng interface gây ambiguity | Chỉ định qualifier hoặc strategy rõ |
| `@jakarta.inject.Qualifier("upperPort")` không compile | Jakarta `Qualifier` không nhận String; dùng `@Named` |
| Import hai `Qualifier` cùng simple name | Dùng fully-qualified annotation cho một loại |
| `@Inject(required=false)` không compile | Jakarta Inject không có `required` |
| `Provider.get()` trả cùng instance | Scope đích singleton |
| `Provider.get()` ném exception | Candidate vắng mặt, mơ hồ hoặc bean creation lỗi |
| `@Named` bị hiểu là prototype | Spring default singleton; dùng `@Scope` nếu cần |
| `@Value` được tìm trong `jakarta.inject` | Không có; dùng Spring property injection/config |
| `javax.inject` cũ không hoạt động như dự kiến | Kiểm tra migration Jakarta trên Spring 6 |
| Dùng `new` rồi trông đợi annotation tự inject | Phải để Spring quản lý/cấu hình instance |
| Dùng custom qualifier nhưng vẫn lựa sai | Kiểm tra meta annotation, runtime retention, target, candidate registration |
| Nghĩ bean singleton mặc định thread-safe | Scope không cung cấp đồng bộ tự động |

Quy trình chẩn đoán: kiểm tra dependency Jakarta → bean registration → tên/loại candidate → injection qualifier → scope/provider → nguyên nhân gốc trong bean creation exception. Đừng chữa lỗi bằng cách đánh dấu mọi bean `@Primary`.

## 11. Bài tập có đáp án

**1.** `@jakarta.inject.Qualifier("upperPort")` có hợp lệ không?

**Đáp án:** Không. `jakarta.inject.Qualifier` là meta-annotation; dùng `@Named("upperPort")` hoặc custom qualifier như `@Fast`.

**2.** `@Named("upperPort")` trên class và ở tham số có khác nhau không?

**Đáp án:** Trên class định danh component ứng viên, trên tham số giới hạn bean cần nhận theo tên/qualifier.

**3.** Nếu có `upperPort` và `lowerPort` cùng `TextPort`, Spring tự chọn cái khai báo trước?

**Đáp án:** Không được dựa vào thứ tự khai báo; nếu không phân giải được có thể lỗi mơ hồ. Dùng qualifier.

**4.** `@Inject(required=false)` có hợp lệ không?

**Đáp án:** Không. Jakarta `@Inject` không có thuộc tính required. Trong Spring có thể dùng `Optional<T>` hoặc phương án optional DI khác.

**5.** Bean `@Named` mặc định scope gì trong Spring?

**Đáp án:** Singleton theo bean definition/context, không phải global singleton toàn JVM.

**6.** Vì sao hai lần `Provider.get()` trong demo có số 1 và 2?

**Đáp án:** Bean đích `RequestToken` được đặt `@Scope("prototype")`; mỗi lần yêu cầu Spring chạy factory method và tăng counter.

**7.** Nếu đổi `RequestToken` sang singleton, provider có vẫn luôn tạo token mới?

**Đáp án:** Không. Provider thực hiện lookup, còn scope quyết định instance được cấp.

**8.** `jakarta.annotation.PostConstruct` có trong `jakarta.inject-api` không?

**Đáp án:** Không. Đó là API Jakarta Annotations riêng (JSR-250), khác `jakarta.inject`.

**9.** Tại sao custom `@Offline` cần `@Retention(RUNTIME)`?

**Đáp án:** Metadata qualifier cần hiện diện khi container thực hiện resolution. Nếu không giữ annotation ở runtime, cơ chế đọc metadata có thể không nhận biết được.

**10.** `@Named` có thay thế đầy đủ `@Value`, `@Lazy`, `@Scope` không?

**Đáp án:** Không. Nó chỉ hỗ trợ naming/qualifier/stereotype đơn giản, không định nghĩa các chức năng Spring kia.

**11.** Lớp được gắn `@Named` nhưng không scan/register, `getBean(Class)` có thành công không?

**Đáp án:** Không thể trông chờ. Context phải có bean definition đã đăng ký tương ứng.

## 12. Checklist áp dụng

- [ ] Spring Framework 6.x, Java 17+ và `jakarta.inject-api` được khai báo khi dùng Jakarta DI annotations.
- [ ] Phân biệt `jakarta.inject.Named`, `jakarta.inject.Qualifier` và Spring `Qualifier`.
- [ ] Bean có tên/qualifier được đăng ký trong đúng context.
- [ ] Injection point có type và tên/qualifier khớp ứng viên.
- [ ] Không viết `@jakarta.inject.Qualifier("...")` hoặc `@Inject(required=false)`.
- [ ] Khi cần custom qualifier, định nghĩa annotation với meta qualifier, retention runtime và targets phù hợp.
- [ ] Dùng `Provider.get()` khi thật sự cần lookup theo nhu cầu, hiểu scope bean đích.
- [ ] Không hiểu provider đồng nghĩa prototype.
- [ ] Không mặc định mọi Jakarta implementation có chính xác default singleton của Spring.
- [ ] Không coi Jakarta DI có `@Value`, `@Lazy` hoặc Spring profiles/conditions.
- [ ] Phân biệt `jakarta.inject` JSR-330 và `jakarta.annotation` JSR-250.
- [ ] Khi migrate, kiểm tra `javax.inject` vs `jakarta.inject` và dependency thực tế.
- [ ] Test các ứng viên cùng type, trường hợp thiếu bean và resolution theo tên.
- [ ] Không dựa vào thứ tự scan/định nghĩa để chọn dependency.
- [ ] Tách business logic khỏi bean lookup tùy tiện, không biến annotation thành nơi giấu quy tắc nghiệp vụ.

### Nguồn tham khảo Spring

- [Using JSR 330 Standard Annotations — Spring 6.2](https://docs.spring.io/spring-framework/reference/6.2/core/beans/standard-annotations.html)
- [Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html)
- [Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)

**Kết luận:** `@Named` của Jakarta dùng để **định danh ứng viên hoặc chọn theo tên**. `jakarta.inject.Qualifier` chỉ là meta-annotation tạo qualifier tùy chỉnh, không phải cú pháp chuỗi `@Qualifier("...")` của Spring. `@Inject` và `Provider<T>` kết hợp tốt với Spring Core Container nhưng không thay thế toàn bộ `@Autowired(required=false)`, `@Value`, `@Lazy` hoặc `@Scope`. Hiểu đúng API, phạm vi và nơi đăng ký bean giúp dependency injection rõ ràng, ít nhầm import và dễ kiểm thử.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="autowired-009">Vì sao đối tượng tự tạo ngoài container không được tự động tiêm</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao đối tượng tự tạo ngoài container không được tự động tiêm — @Autowired trong Spring Core Container

## 1. Hiện tượng: cùng một class, khác cách tạo, khác kết quả injection

Xét một Java class có field:

~~~java
@Component
public class GreetingClient {
    @Autowired
    private Formatter formatter;
}
~~~

Nhiều người nghĩ rằng chỉ cần gắn `@Component` hoặc `@Autowired`, bất cứ object nào thuộc class này cũng sẽ có giá trị `formatter`. **Đó là hiểu nhầm.** Annotation là **metadata**, không phải một lệnh tự chạy mỗi khi Java cấp phát object bằng `new`.

So sánh:

~~~java
GreetingClient a = new GreetingClient();
GreetingClient b = context.getBean(GreetingClient.class);
~~~

Đây là **snippet giải thích**, chưa phải file Java đầy đủ. Với Spring context đã đăng ký bean và có đúng một bean `Formatter` tương thích:

- Object `a` được tạo trực tiếp bằng Java `new`. Nó **không tự đi qua quá trình autowiring của Spring**, nên field `formatter` giữ giá trị mặc định `null` nếu constructor/code khác không gán.
- Object `b` được trả từ Spring `ApplicationContext`. Nó đã qua **bước xử lý injection** trong vòng đời Spring-managed bean; `formatter` được Spring gán bằng collaborator đã resolve.
- Hai objects cùng Java class nhưng **không cùng ownership**. Việc một object thuộc class đã có bean definition **không biến mọi object khác của class đó thành managed bean**.
- Annotation `@Component` khiến class **có thể được đăng ký thông qua scanning**; riêng object do Java `new` bên ngoài container không tự được Spring “nhận nuôi”.

**Mục tiêu bài:** hiểu *vì sao* Spring không tự inject vào object tạo ngoài container, *hậu quả*, *cách sửa đúng theo kiến trúc* và *giới hạn của các API có thể hỗ trợ đối tượng do framework khác tạo*.

Sau bài bạn có thể:

1. Giải thích vai trò của `ApplicationContext`, `BeanFactory`, `BeanDefinition` và `BeanPostProcessor` trong quá trình tạo và inject bean.
2. Phân biệt object `new`, Spring-managed bean được `getBean(...)`, manual constructor injection và external object được `autowireBean(...)`.
3. Hiểu vì sao `@Autowired` trên field/method của một object bình thường không được JVM tự xử lý.
4. Biết ưu tiên **constructor injection**, giữ collaborators bắt buộc trong `private final` fields và tách trách nhiệm tạo object.
5. Biết trường hợp tích hợp cần dùng `AutowireCapableBeanFactory.autowireBean(existingObject)`, cùng **giới hạn về registry và lifecycle**.
6. Phân tích lỗi `NullPointerException` do object không được inject, lỗi thiếu bean, trùng candidate, tạo object sai chỗ và bất cẩn trong unit tests.
7. Chạy ví dụ **Spring Framework + Java 17+** thuần, không cần Spring Boot, với Maven, `javac`/`java` và stdout chính xác.

**Phạm vi:** bài này tập trung vào việc **object tự tạo ngoài container không tự nhận `@Autowired`**. Các cơ chế AspectJ, JPA, Servlet và factory của framework khác được nhắc ở mức nhận diện, không biến chúng thành điều kiện bắt buộc của demo.

## 2. Cơ chế: annotation tự nó không tiêm dependency

Annotation trong Java được lưu trong metadata của class/method/field (theo retention của annotation), nhưng **JVM không có quy tắc chung “thấy `@Autowired` thì tự tìm bean và gán vào field”**.

Spring phải chủ động thực hiện các bước cần thiết:

1. **Đăng ký class hoặc factory method:** từ `@ComponentScan`, `@Bean`, XML, programmatic bean definition hoặc `context.register(SomeClass.class)` phù hợp.
2. **Nhận diện một bean cần tạo:** Spring dùng definition, tên bean, scope và thông tin constructor/factory.
3. **Khởi tạo object:** có thể bằng constructor được Spring resolve hoặc một factory method cung cấp object.
4. **Giải quyết collaborator:** Spring tìm candidate bằng type, qualifier, primary, name và các quy tắc autowiring.
5. **Áp dụng injection annotations:** các post-processors như `AutowiredAnnotationBeanPostProcessor` xử lý `@Autowired` trên field/method phù hợp, sau khi object được tạo và trước khi dùng nó như bean đã khởi tạo.
6. **Hoàn thành lifecycle:** callbacks, các `BeanPostProcessor` khác và có thể tạo proxy; container công bố bean theo scope và quản lý những bước lifecycle mà nó chịu trách nhiệm.

Một sơ đồ giản lược:

~~~mermaid
flowchart TD
    A["BeanDefinition / đăng ký class"] --> B["Spring tạo hoặc nhận bean instance"]
    B --> C["Bean post-processing"]
    C --> D["Resolve @Autowired fields / methods"]
    D --> E["Initialization, proxies (nếu có)"]
    E --> F["Bean được getBean(...) trả về"]
    N["new SomeClass() bên ngoài Spring"] --> X["Object Java thường"]
    X -.->|"KHÔNG tự qua"| C
~~~

Sơ đồ cố tình đơn giản: constructor injection phải được resolve **khi tạo object**, không chờ đến field/method post-processing; ngoài ra, chu trình khởi tạo có những extension points và xử lý dependency phức tạp hơn. Dù vậy, ranh giới quan trọng vẫn là **có đi qua pipeline Spring hay không**.

Spring có thể đọc annotation khi xử lý **một bean được quản lý**. Một object được `new` trong method nghiệp vụ **không tự đăng ký** vào BeanFactory và không tự gọi bean post-processors.

### Phân biệt ba việc thường bị trộn lẫn

| Công việc | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| Đăng ký **bean definition** | `context.register(GreetingClient.class)` | Spring biết **cách tạo** một bean khi context xử lý registration |
| Lấy managed **bean instance** | `context.getBean(GreetingClient.class)` | Spring cấp bean theo scope, với injection/lifecycle tương ứng |
| Tạo object Java bình thường | `new GreetingClient()` | JVM cấp object; Spring không tự được thông báo hay áp dụng injection |

Không được hiểu `@Component` là “hễ xuất hiện class này ở đâu đều sẽ thành Spring bean”. Component stereotype làm class đủ điều kiện được phát hiện **khi đúng package được scan**, hoặc class có thể được đăng ký theo cách khác. Nó không phải cơ chế giám sát mọi lời gọi `new`.

## 3. Trường hợp dễ gặp nhất: @Autowired field null sau new

~~~java
@Component
final class ReportService {
    @Autowired
    private Formatter formatter;

    String render(String input) {
        return formatter.format(input);
    }
}
~~~

Đây là **snippet**, thiếu định nghĩa `Formatter` và không phải mã độc lập. Lời gọi:

~~~java
ReportService report = new ReportService();
report.render("book"); // Có thể NPE vì formatter chưa được inject.
~~~

Nếu không có code nào khác gán field, Java khởi tạo reference field mặc định là `null`. Annotation `@Autowired` nằm trên field không tự chạy; gọi `render(...)` có thể ném `NullPointerException`.

Với bean do container quản lý:

~~~java
ReportService report = context.getBean(ReportService.class);
String result = report.render("book");
~~~

khi `ReportService` thực sự được đăng ký, có đúng dependency và injection thành công, `formatter` đã được container cấp trước khi sử dụng thông thường. Nhưng **không được suy ra bất kỳ `getBean` nào đều luôn thành công**: thiếu `Formatter`, nhiều candidates mơ hồ hoặc dependency khởi tạo lỗi có thể khiến việc tạo bean thất bại.

**Lỗi không nằm ở việc field private.** Spring có cơ chế xử lý/tiêm vào field được annotate phù hợp. Vấn đề là **người tạo object đã bỏ qua vòng đời Spring**.

### Tại sao thêm @Autowired vào constructor cũng không cứu một lời gọi new tùy ý?

Với:

~~~java
final class ReportService {
    private final Formatter formatter;

    @Autowired
    ReportService(Formatter formatter) {
        this.formatter = java.util.Objects.requireNonNull(formatter);
    }
}
~~~

Nếu code gọi `new ReportService(formatter)`, **caller Java** đã tự cung cấp dependency. Annotation không bị JVM “kích hoạt” theo cơ chế DI; constructor chỉ chạy bình thường. Nếu code gọi `new ReportService()`, Java **không biên dịch được** nếu class không có constructor không tham số. Đây là một ưu điểm: dependency bắt buộc hiện rõ trong API.

Đừng thêm constructor mặc định tạo trạng thái thiếu `formatter` để một nơi nào đó dễ gọi `new`. Hãy sửa nơi tạo object để cấp dependency hoặc lấy managed bean đúng ranh giới.

## 4. Ai tạo object quyết định ai chịu trách nhiệm injection

### Khi Spring tạo/quản lý bean

- `@Component` được phát hiện qua scan/registration hoặc `@Bean` factory đưa object vào Spring.
- Spring giải quyết các dependencies đã khai báo và thực hiện những injection callbacks thích hợp.
- Bean thường có scope singleton mặc định, nên hai lời gọi `getBean("...")` trong cùng context trả cùng instance theo semantics singleton.
- Initialization, AOP proxy, scoped semantics, và destruction callbacks có thể được áp dụng theo cấu hình/lifecycle.
- Consumer không nên tự instantiate thêm một bản song song rồi giả định nó được cấu hình giống bean.

### Khi business code tự new

- Object do Java tạo có thể hoạt động bình thường **nếu mọi dependency đã được truyền trực tiếp** qua constructor/setter.
- Field có `@Autowired` mà không được xử lý sẽ giữ mặc định, thường là `null`.
- Spring **không tự đăng ký** object này trong bean registry, không tự áp dụng `@Value`, `@PostConstruct`, proxy `@Transactional`, `@Async` hoặc `@PreDestroy` như trên một managed bean.
- Việc object được tạo **ở bên trong một method của Spring-managed bean** không làm cho object con tự nhiên được managed. Container biết parent bean, không theo dõi từng lệnh `new` trong JVM.
- Nếu object giữ tài nguyên hoặc mở kết nối, caller thường phải chịu trách nhiệm dọn dẹp nếu object không thuộc lifecycle do Spring quản lý.

**Một ngoại lệ cần phân biệt:** object do factory method `@Bean` gọi `new` để **trả về cho Spring** có thể trở thành managed bean chính vì factory method là **một đường tạo bean được container sử dụng**. Tức là không phải từ khóa `new` bị cấm; quan trọng là **object có được đưa vào pipeline quản lý của container không**.

## 5. Cách khắc phục ưu tiên: constructor injection và tách nơi tạo object

Hãy ưu tiên loại class để dependency bắt buộc được truyền rõ:

~~~java
public final class GreetingService {
    private final Formatter formatter;

    public GreetingService(Formatter formatter) {
        this.formatter = java.util.Objects.requireNonNull(
                formatter, "formatter is required");
    }

    public String greet(String raw) {
        return formatter.format(raw);
    }
}
~~~

**Snippet có thể đưa vào một file phù hợp sau khi khai báo `Formatter`**, không phải chương trình hoàn chỉnh. Hai cách tạo hợp lệ:

~~~java
// Java thuần, không cần Spring:
Formatter fake = raw -> "TEST:" + raw;
GreetingService unit = new GreetingService(fake);

// Spring tại composition root:
Formatter managedFormatter = context.getBean(Formatter.class);
GreetingService manuallyComposed =
        new GreetingService(managedFormatter);
~~~

Trong cả hai trường hợp, object tự tạo **có đầy đủ collaborator** nhờ constructor Java; nó **không tự trở thành Spring bean** chỉ vì collaborator lấy từ context.

Cách thiết kế thường tốt hơn trong Spring application: **đăng ký cả `GreetingService` làm bean** và inject nó vào bean controller/job/service cần dùng. Không gọi `getBean(...)` rải rác khắp các method nghiệp vụ.

### Khi nào được dùng new bình thường?

Dùng `new` tự nhiên với object **không cần container quản lý**: DTO, value object, immutable domain data, collection, class thuần Java có tham số rõ, hoặc helper local không cần injection/AOP/lifecycle. **Không ép mọi object Java trở thành Spring bean.**

Một quy tắc thực tế:

- **Có framework-managed dependencies hoặc lifecycle?** Cho Spring quản lý/định nghĩa rõ factory.
- **Có collaborator nhưng dễ truyền qua constructor?** Manual DI hoàn toàn hợp lệ.
- **Là value object không có service dependency?** `new` bình thường là đúng.
- **Object do thư viện bên ngoài tạo, bạn không kiểm soát constructor?** Cân nhắc adapter hoặc API hỗ trợ autowiring existing instances có chủ đích.

**Constructor injection làm bug dễ thấy hơn:** `Objects.requireNonNull` từ chối reference thiếu ở lúc tạo object; `private final` giữ reference không bị thay. Nhưng field final không làm collaborator mutable tự thread-safe và không tự áp dụng Spring AOP.

## 6. Một ngoại lệ tích hợp: AutowireCapableBeanFactory.autowireBean(...)

Spring có API cấp thấp để **tiêm dependency vào object đã tồn tại**:

~~~java
LegacyClient external = new LegacyClient();
context.getAutowireCapableBeanFactory().autowireBean(external);
~~~

**Đây là snippet**, class/context đầy đủ có ở mục 7. Phương thức `autowireBean(existingObject)` của `AutowireCapableBeanFactory` áp dụng những **after-instantiation callbacks/property post-processing**, trong đó có annotation-driven injection như `@Autowired` lên field/setter của instance.

Đây là API phục vụ các tình huống **tích hợp với hệ thống khác đang sở hữu việc tạo object**, không phải kỹ thuật mặc định cho toàn bộ business code.

### Có gì và chưa có gì?

| Việc | Kết quả khi gọi `autowireBean(external)` |
| --- | --- |
| Instance đã tồn tại được Java tạo từ trước | Giữ nguyên instance do caller đưa vào |
| `@Autowired` field/method phù hợp | Có thể được xử lý/điền nếu dependency resolve được |
| Constructor `@Autowired` của class đã được `new` | **Không chạy lại constructor** và không thay đổi cách object đã được tạo |
| Tự thêm một bean definition vào registry | **Không** |
| `context.getBean(LegacyClient.class)` tự tìm thấy object external | **Không**, trừ khi có registration khác |
| Bảo đảm full init/post-processors/AOP proxy như managed bean | **Không thể kết luận chỉ từ `autowireBean`** |
| Bảo đảm full destruction callback lúc context đóng | **Không**; caller vẫn phải quản lý lifecycle phù hợp |
| Dependency thiếu hoặc mơ hồ | Vẫn có thể ném lỗi injection, không “tha thứ” vì external object |

Đây là chi tiết rất dễ bị diễn giải sai: **được inject không đồng nghĩa được đăng ký làm Spring bean**. Có thể thấy `external.formatter != null` sau `autowireBean` nhưng `getBeanNamesForType(LegacyClient.class)` vẫn rỗng. Trên object ấy, các cơ chế liên quan bean name, singleton registry, proxy và teardown có thể không giống managed bean thông thường.

### Các API liên quan không giống nhau

- `autowireBean(Object)`: xử lý dependency annotations/property post-processing của **instance đang có**, không tự sinh bean registry entry.
- `createBean(Class<T>)`: nhờ bean factory **tạo một instance mới và chạy tạo/khởi tạo qua những post-processors phù hợp**, nhưng không mặc định đồng nghĩa đăng ký nó dưới một tên singleton trong application context.
- `initializeBean(Object, name)`: áp dụng các callback initialization và bean post-processing lên object đã có; **không phải API thay thế đơn giản cho autowireBean trong việc tiêm field**.
- `configureBean(Object, name)`: cấu hình existing object theo **bean definition của name đã có** và thực hiện thêm các callback; yêu cầu một definition tương ứng, nên khác hẳn “cứ truyền object vào là được”.
- `registerSingleton(name, instance)` là một thao tác đăng ký instance có chủ đích trên BeanFactory thích hợp, nhưng **không tự thực hiện đầy đủ callbacks của quá trình tạo bean thông thường**; không dùng bừa để thay `@Bean`.

**Không xâu chuỗi các API này theo cảm giác** để mong đạt cùng semantics với container-managed bean. Phải chọn đúng khả năng tài liệu mô tả và kiểm tra tác động lifecycle/proxy/ownership. Với code nghiệp vụ có thể kiểm soát, ưu tiên khai báo bean/factory rõ ràng hoặc constructor injection.

## 7. Ví dụ Java 17+ tự chứa, Spring Framework thuần

Ví dụ sau chạy một Spring Context và quan sát bốn trường hợp:

1. `new FieldClient()`: field `@Autowired` không được gán tự động.
2. `context.getBean(FieldClient.class)`: cùng class nhưng Spring quản lý và inject thành công.
3. `new ConstructorClient(formatter)`: object không được đăng ký nhưng hoạt động đúng vì được **manual constructor injection**.
4. `new LegacyClient()` rồi gọi `autowireBean(external)`: field được inject có chủ đích, **nhưng object không được đăng ký vào registry**.

**Yêu cầu:** JDK 17+, Maven, Spring Framework **6.2.19** ghim để tái lập. Không cần Spring Boot, Servlet container hoặc DB. Phiên bản ghim không phải tuyên bố đây là bản Spring mới nhất.

~~~text
external-object-autowiring/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── ExternalObjectDemo.java
~~~

### 7.1. File pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>external-object-autowiring</artifactId>
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
                    <mainClass>demo.ExternalObjectDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Spring Framework dependencies còn được Maven giải quyết transitively. Nếu dùng `javac` trực tiếp, classpath phải gồm đủ các thư viện do Maven resolve.

### 7.2. File src/main/java/demo/ExternalObjectDemo.java

~~~java
package demo;

import java.util.Locale;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

public final class ExternalObjectDemo {
    private ExternalObjectDemo() {}

    public interface Formatter {
        String format(String raw);
    }

    public static final class UppercaseFormatter implements Formatter {
        @Override
        public String format(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            return "Xin chao, " + raw.strip().toUpperCase(Locale.ROOT);
        }
    }

    @Component("fieldClient")
    public static final class FieldClient {
        @Autowired
        private Formatter formatter;

        public FieldClient() {}

        public boolean injected() {
            return formatter != null;
        }

        public String reply(String name) {
            if (formatter == null) {
                throw new IllegalStateException("formatter not injected");
            }
            return formatter.format(name);
        }
    }

    public static final class ConstructorClient {
        private final Formatter formatter;

        public ConstructorClient(Formatter formatter) {
            this.formatter = Objects.requireNonNull(
                    formatter, "formatter required");
        }

        public String reply(String name) {
            return formatter.format(name);
        }
    }

    public static final class LegacyClient {
        @Autowired
        private Formatter formatter;

        public LegacyClient() {}

        public boolean injected() {
            return formatter != null;
        }

        public String reply(String name) {
            if (formatter == null) {
                throw new IllegalStateException("formatter not injected");
            }
            return formatter.format(name);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        public Formatter formatter() {
            return new UppercaseFormatter();
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(
                        AppConfig.class, FieldClient.class)) {

            // Case 1: Java new, không chạy autowiring.
            FieldClient plain = new FieldClient();
            System.out.println("newInjected=" + plain.injected());
            try {
                plain.reply("Lan");
                System.out.println("newCall=UNEXPECTED");
            } catch (IllegalStateException ex) {
                System.out.println("newCall=BLOCKED");
            }

            // Case 2: Spring bean có cùng class, field đã được inject.
            FieldClient managed = context.getBean(FieldClient.class);
            System.out.println("managedInjected=" + managed.injected());
            System.out.println("managedReply=" + managed.reply(" Lan "));

            // Case 3: manual constructor injection, không đăng ký.
            Formatter formatter = context.getBean(Formatter.class);
            ConstructorClient manual = new ConstructorClient(formatter);
            System.out.println("manualReply=" + manual.reply(" Minh "));
            System.out.println("manualRegistered=" +
                    (context.getBeanNamesForType(ConstructorClient.class)
                            .length > 0));

            // Case 4: autowireBean trên existing external instance.
            LegacyClient external = new LegacyClient();
            System.out.println("externalBefore=" + external.injected());
            context.getAutowireCapableBeanFactory()
                    .autowireBean(external);
            System.out.println("externalAfter=" + external.injected());
            System.out.println("externalRegistered=" +
                    (context.getBeanNamesForType(LegacyClient.class)
                            .length > 0));
            System.out.println("externalReply=" + external.reply(" An "));

            System.out.println("managedSingleton=" +
                    (managed == context.getBean(FieldClient.class)));
        }
    }
}
~~~

### 7.3. Kết quả mong đợi chính xác

Tại thư mục `external-object-autowiring`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

**Đúng 11 dòng stdout do phương thức `main` in khi chạy thành công:**

~~~text
newInjected=false
newCall=BLOCKED
managedInjected=true
managedReply=Xin chao, LAN
manualReply=Xin chao, MINH
manualRegistered=false
externalBefore=false
externalAfter=true
externalRegistered=false
externalReply=Xin chao, AN
managedSingleton=true
~~~

Các dòng thông tin phiên bản, downloading dependency hay log của Maven/Spring nếu xuất hiện không nằm trong 11 dòng do code ví dụ trực tiếp in.

**Giải thích kỹ thuật của từng dòng:**

| Kết quả | Bằng chứng |
| --- | --- |
| `newInjected=false` | Java `new` không tự kích hoạt Spring annotation injection |
| `newCall=BLOCKED` | Code có guard; lỗi sớm, không truy cập collaborator null |
| `managedInjected=true` | Bean được tạo qua Spring context có field `@Autowired` được xử lý |
| `managedReply=Xin chao, LAN` | Managed bean sử dụng collaborator thật |
| `manualReply=Xin chao, MINH` | Manual constructor injection cho phép object Java bình thường hoạt động |
| `manualRegistered=false` | ConstructorClient không tự được đăng ký chỉ vì được tạo bằng `new` |
| `externalBefore=false` | LegacyClient chưa qua injection |
| `externalAfter=true` | `autowireBean(external)` đã xử lý field `@Autowired` |
| `externalRegistered=false` | Đã inject không đồng nghĩa có bean registry entry |
| `externalReply=Xin chao, AN` | External object đã nhận formatter object từ container |
| `managedSingleton=true` | Spring-managed FieldClient là singleton mặc định trong context này |

Mã ví dụ tránh timestamp, số ngẫu nhiên, identity hash và collection order; stdout dự kiến không phụ thuộc các yếu tố đó. Phần mã và output ở đây được trình bày để người học chạy và tự xác nhận.

### 7.4. Chạy trực tiếp bằng javac/java

Vì chương trình import Spring, Maven có thể tạo classpath cho mọi transitive dependency:

~~~bash
mvn -q dependency:build-classpath -Dmdep.outputFile=classpath.txt
~~~

**Linux/macOS (bash/zsh):**

~~~bash
mkdir -p out
javac --release 17 -encoding UTF-8 \
  -cp "$(cat classpath.txt)" \
  -d out src/main/java/demo/ExternalObjectDemo.java
java -cp "out:$(cat classpath.txt)" demo.ExternalObjectDemo
~~~

**Windows PowerShell:**

~~~powershell
$deps = (Get-Content -Raw classpath.txt).Trim()
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -encoding UTF-8 -cp "$deps" -d out src/main/java/demo/ExternalObjectDemo.java
java -cp "out;$deps" demo.ExternalObjectDemo
~~~

Trên Unix các phần classpath ngăn bằng `:`, trên Windows bằng `;`. Hai cách chạy đều gọi `demo.ExternalObjectDemo`, do đó cho cùng 11 dòng stdout. Nếu `javac` báo không tìm thấy `org.springframework...`, sửa dependency/classpath trước khi chẩn đoán behavior autowiring.

## 8. Một số tình huống thực tế: controller, listener, task, helper

**Một anti-pattern phổ biến trong Spring MVC:**

~~~java
// Sai nếu muốn ReportService này được Spring inject:
ReportService service = new ReportService();
return service.render("book");
~~~

Ngay cả khi `ReportService` có `@Service`, nếu tự `new` nó trong một controller, instance ấy không tự được quản lý. Dùng constructor injection vào controller:

~~~java
@RestController
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }
}
~~~

**Hai đoạn trên là snippets về Spring MVC, không phải phần của project runnable Core Container**; chúng còn cần thư viện Spring Web, khai báo service, import và các method thật. Ý chính: controller do Spring quản lý **nhận service bean**, không tự tạo phiên bản unmanaged của service.

**Một lỗi tương tự trong background tasks:**

~~~java
new Thread(new EmailJob()).start();
~~~

Nếu `EmailJob` chứa field `@Autowired` mà không được container tạo/xử lý, code chạy trong thread cũng không tự được inject. Dùng scheduler/executor được cấu hình phù hợp và tạo task với dependencies qua constructor, hoặc để Spring quản lý phần service cần DI. **Không đồng nghĩa mọi Runnable nên là singleton**: dữ liệu riêng mỗi tác vụ vẫn nên là object riêng, còn dependency managed có thể được truyền vào constructor.

**Trong unit test Java thuần:** nếu viết `new SomeService()` rồi mong field `@Autowired` được gán, test sẽ sai giả định. Dùng constructor injection với fake/mock, hoặc dùng Spring Test context thực sự khi đang kiểm tra Spring wiring; **không khởi động cả context chỉ để unit test một phép tính đơn giản**.

**Trong JPA entities, objects deserialize, objects do thư viện khác tạo:** framework có thể sở hữu việc tạo object. Đừng kỳ vọng `@Autowired` trên entity/DTO tự hoạt động. Tốt hơn là truyền dữ liệu vào một managed service xử lý entity, hoặc dùng integration API có kiểm soát tại ranh giới. Những hệ sinh thái có cơ chế injection riêng chỉ hoạt động khi đã được cấu hình cho cơ chế đó, không phải vì có annotation ở đâu đó.

## 9. Vì sao @Configurable không mặc nhiên sửa được new?

Spring có tích hợp AspectJ cho một số object được tạo **ngoài** IoC container, ví dụ dùng `@Configurable` kết hợp `@EnableSpringConfigured`/`spring-aspects` và **AspectJ weaving** phù hợp. Nhưng:

- Chỉ thêm `@Configurable` vào class **không đủ** để Spring tự inject mọi lời gọi `new`.
- Cần cấu hình hỗ trợ và load-time/compile-time weaving tương ứng; đây là một kỹ thuật nâng cao, có giới hạn runtime/classloader và chi phí vận hành.
- Nó không phải lý do bỏ constructor injection và biến mọi value/domain object thành một dependency lookup point.
- Không dùng AOP proxy thông thường của Spring để giả định JVM intercept được mọi `new` trong bất cứ file Java nào.

**Quy tắc thực hành:** chỉ dùng weaving khi có yêu cầu tích hợp được đánh giá và kiểm thử cụ thể; constructor injection và bean registration rõ ràng thường dễ hiểu, dễ test và ít bất ngờ hơn.

## 10. Hậu quả nếu cố sử dụng object unmanaged như managed bean

### 10.1. Không có injection annotations

`@Autowired` field/method không được xử lý, nên collaborator có thể null. `@Value` property cũng không tự resolve; `@Inject`/`@Resource` có giới hạn tương tự khi không có bộ xử lý của container phù hợp.

### 10.2. Không có bảo đảm bean lifecycle

`@PostConstruct`, `InitializingBean`, `@PreDestroy`, destroy methods và lifecycle management bình thường thuộc quá trình mà framework cần chủ động gọi. Object `new` bên ngoài container không tự chạy các callback Spring chỉ vì class có annotation.

### 10.3. Không có proxy của Spring

Với features dựa vào proxy như `@Transactional`, `@Async`, caching hoặc security interceptors, một object `new` ngoài context thường **không phải proxy managed**. Method call trên instance ấy có thể bỏ qua interceptor cần thiết. Hơn nữa, **self-invocation** trên managed bean cũng có giới hạn proxy riêng; không dùng self-invocation làm cách kiểm chứng bean ownership.

### 10.4. Không tuân theo bean scope

Một Spring singleton được hiểu trong phạm vi bean name/container. `new SomeService()` tạo instance Java khác với singleton được `getBean(...)` trả về. Tên class giống nhau không đồng nghĩa singleton identity.

### 10.5. Không tự có qualifier/primary resolution

Nếu một field muốn bean `Formatter`, annotation trên unmanaged object không khiến Spring chạy candidate resolution. Các quy tắc `@Qualifier`/`@Primary` chỉ có hiệu lực khi đúng một injection mechanism đang thực hiện giải quyết dependency.

### 10.6. Không được container giám sát việc cleanup

Nếu object external mở stream/socket/thread pool, code tạo/nhận nó phải đóng đúng cách. Gọi `autowireBean(external)` chỉ để inject một field **không biến Spring thành owner mặc định của mọi tài nguyên external**.

## 11. Phân biệt lỗi DI với lỗi Java và cấu hình

| Triệu chứng | Nguyên nhân thường gặp | Hướng sửa |
| --- | --- | --- |
| `@Autowired` field vẫn null, object do `new` | Instance không đi qua pipeline Spring | Lấy managed bean hoặc truyền dependency qua constructor |
| `@Component` class vẫn không nằm trong context | Không scan package hoặc không register class | Cấu hình `@ComponentScan` hoặc `context.register(...)` đúng |
| `NoSuchBeanDefinitionException` | Injection được xử lý nhưng không có bean candidate tương ứng | Đăng ký dependency, kiểm tra type/name/qualifier |
| `NoUniqueBeanDefinitionException` | Có nhiều bean cùng type mà không chọn được một | Dùng `@Qualifier`/`@Primary` hoặc sửa cấu hình |
| `UnsatisfiedDependencyException` | Constructor/field/method cần dependency không resolve được | Đọc `Caused by`; sửa bean registration/injection point |
| `NullPointerException` khi gọi service new | Collaborator field mặc định null, không guard | Không new service cần DI; dùng constructor non-null guard |
| Unit test chạy bằng new báo lỗi null | Không khởi động Spring và không cấp dependency | Constructor-inject fake trong unit test; context test cho wiring |
| `autowireBean` xong `getBean(LegacyClient.class)` vẫn fail | Autowire existing instance **không register** nó | Nếu cần managed bean, khai báo definition/factory từ đầu |
| `@Transactional` bị bỏ qua | Đang gọi object ngoài proxy/container | Lấy đúng proxied bean, kiểm tra proxy mode/scope |
| `@PostConstruct` không chạy trên external instance | Chỉ new hoặc chỉ autowireBean, không full lifecycle | Giao ownership rõ; dùng lifecycle API đúng mục đích |
| `@Configurable` không có tác dụng | Chưa cấu hình AspectJ weaving/infrastructure cần thiết | Tránh đoán, thiết lập weaving có chủ đích hoặc đổi kiến trúc |
| `ObjectProvider.getObject()` thiếu bean | Provider lookup thật sự không tìm thấy candidate | Đăng ký bean hoặc xử lý optional đúng hợp đồng |
| `@Autowired(required=false)` để service chạy với null | Che dependency thực sự bắt buộc | Thiết kế constructor required và fail fast |
| `javac` báo thiếu Spring packages | Compile classpath không có Spring JAR | Kiểm tra pom và Maven dependency classpath |
| ApplicationContext đã close | Object external vẫn giữ collaborator dùng tài nguyên đã shutdown | Làm rõ lifecycle của owner/caller, không dùng context đã đóng |

**Mẹo chẩn đoán:** khi một field `@Autowired` bất ngờ null, hỏi ngay **“Ai tạo instance này?”**. Xác định địa chỉ tạo bằng `new`, constructor/factory method, component scan, plugin loader, framework deserializer hay test runner. Sau đó mới kiểm tra qualifier/type/scope. Nếu instance chưa hề được Spring xử lý, lỗi không nằm ở `@Qualifier`.

## 12. Các cách dùng đúng trong kiến trúc ứng dụng

### Cách A — Inject managed service vào managed consumer (ưu tiên)

~~~java
@Component
public class OrderHandler {
    private final OrderService orders;

    public OrderHandler(OrderService orders) {
        this.orders = java.util.Objects.requireNonNull(orders);
    }
}
~~~

**Snippet**, cần `OrderService` được đăng ký làm bean và import `@Component`. Spring sẽ tạo/resolve dependencies khi tạo `OrderHandler`. Nếu `OrderService` thiếu bean, startup có thể fail sớm, giúp tránh silent null.

### Cách B — Tạo value object bằng new và truyền dữ liệu

~~~java
public record OrderRequest(String sku, int count) {
    public OrderRequest {
        if (sku == null || sku.isBlank() || count <= 0) {
            throw new IllegalArgumentException("invalid request");
        }
    }
}
~~~

Snippet record Java 17 hợp lệ trong một file phù hợp. Domain record này không cần `@Autowired`; tạo bằng `new OrderRequest("BOOK", 2)` là tự nhiên và không có DI bị thiếu.

### Cách C — Factory có dependency managed nhưng tạo object mới có constructor hợp lệ

~~~java
@Component
public class ReportFactory {
    private final Formatter formatter;

    public ReportFactory(Formatter formatter) {
        this.formatter = java.util.Objects.requireNonNull(formatter);
    }

    public ReportJob newJob(String title) {
        return new ReportJob(formatter, title);
    }
}
~~~

**Snippet**, cần class `ReportJob` và import phù hợp. ReportFactory là bean nên nhận `Formatter` qua constructor; từng `ReportJob` do factory tạo là Java object riêng nhưng **đã có dependency**, không cần tự field-inject. Những ReportJob đó không tự trở thành managed beans; giữ lifecycle/tài nguyên theo ownership phù hợp.

### Cách D — Autowire object bên ngoài khi tích hợp bắt buộc

Nếu thư viện third-party bắt buộc gọi no-arg constructor và chỉ cung cấp object sau đó, integration boundary có thể gọi `autowireBean(existingObject)`. Hãy document rõ:

- Ai tạo và ai hủy object?
- Có cần `@PostConstruct`/proxy/transaction không?
- Có cần bean name/scope trong registry không?
- Nếu thật sự cần full managed bean, có thể đổi registration/factory hoặc adapter không?

API hỗ trợ object external là **phương án tích hợp**, không phải cách mặc định để chữa các service được `new` lung tung.

## 13. Kiểm thử và bài tập có đáp án

### Unit test không Spring

Tạo một `Formatter` fake và constructor-inject vào một class Java thuần. Không cần context:

~~~java
Formatter fake = raw -> "FAKE:" + raw;
ConstructorClient client = new ConstructorClient(fake);
System.out.println(client.reply("Lan")); // FAKE:Lan
~~~

**Snippet này cần đứng trong phạm vi có quyền truy cập các nested types của demo.** Nó chứng minh constructor injection được kiểm thử thuần Java mà không dùng field reflection hoặc chạy container.

### Integration test Spring

Tạo context với `AppConfig.class` và `FieldClient.class`, lấy `FieldClient` bằng `getBean(...)`; xác nhận collaborator không null và hành vi đúng. Test riêng `new FieldClient()` cho `injected()==false`. Nếu integration buộc `autowireBean`, kiểm tra external object sau wiring nhưng **không** mặc định kỳ vọng `containsBean(...)` hoặc destruction tự động.

Không dùng Java `assert` làm kiểm tra runtime bắt buộc trong ví dụ nếu không bật `-ea`; dùng test framework assertions hoặc `if (...) throw ...`.

**Bài 1.** Class có `@Component` và field `@Autowired`, bạn gọi `new MyService()` trong controller. Vì sao field null?

**Đáp án:** Object mới do Java tạo không đi qua Spring injection post-processors; `@Component` không intercept mọi lệnh `new`. Inject managed service vào controller hoặc cấp dependency qua constructor.

**Bài 2.** `@Bean MyService service() { return new MyService(); }` có phải luôn không được inject vì có `new`?

**Đáp án:** Không. Object được trả về như **bean của factory method do Spring gọi** sẽ qua những bước processing tương ứng. Vấn đề không phải cú pháp `new` tự thân mà là **có được container tiếp nhận/quản lý như bean không**.

**Bài 3.** `autowireBean(external)` làm external trở thành singleton mặc định có bean name không?

**Đáp án:** Không. API có thể xử lý annotation-driven injection trên existing object, nhưng không tự thêm registry entry/singleton definition.

**Bài 4.** Nếu `LegacyClient` có constructor cần `Formatter` và bạn đã gọi `new LegacyClient(null)` thì `autowireBean(...)` có chạy lại constructor để sửa null không?

**Đáp án:** Không. Constructor đã hoàn tất khi Java tạo object. API chỉ xử lý injection đối với existing instance theo hỗ trợ của nó; hãy sửa caller/constructor để bảo vệ invariant.

**Bài 5.** Object được `new` ở trong method của một bean Spring có tự managed không?

**Đáp án:** Không. Scope bean của object cha không khiến mọi object do nó tự tạo trở thành managed. Tuy nhiên, người tạo có thể truyền dependencies hợp lệ qua constructor.

**Bài 6.** Một request DTO không có collaborator Spring. Có nên đăng ký DTO thành bean singleton rồi `getBean` mỗi request?

**Đáp án:** Thường không. DTO/value object mang dữ liệu request được tạo riêng theo nhu cầu; tạo bằng `new` hoàn toàn hợp lý và không có lỗi DI.

**Bài 7.** Vì sao `manualReply=Xin chao, MINH` nhưng `manualRegistered=false` trong demo?

**Đáp án:** ConstructorClient nhận một Formatter hợp lệ qua constructor Java nên hoạt động đúng, nhưng chưa bao giờ được Spring đăng ký như bean.

**Bài 8.** Có thể gắn `@Configurable` vào class để mọi `new` được inject ngay không?

**Đáp án:** Không chỉ nhờ annotation. Tích hợp AspectJ còn cần weaving và infrastructure phù hợp; bài học thông thường nên ưu tiên constructor injection/factory bean rõ ràng.

**Bài 9.** Vì sao test chạy không có Spring có thể fail với field injection mà không fail với constructor injection?

**Đáp án:** Field injection cần một bộ xử lý Spring thực hiện gán; constructor Java chỉ cần caller cung cấp fake/real collaborator hợp lệ nên dễ unit test độc lập.

### Checklist hoàn thành

- [ ] Biết `@Autowired` là metadata do Spring xử lý, không phải JVM tự tiêm sau mọi `new`.
- [ ] Phân biệt đăng ký class/definition với tạo object Java.
- [ ] Nắm BeanPostProcessor/AutowiredAnnotationBeanPostProcessor hoạt động trong pipeline bean.
- [ ] Không suy ra `@Component` biến mọi instance cùng class thành managed.
- [ ] Biết `@Bean` factory có thể gọi `new` mà kết quả vẫn là managed bean khi trả về container.
- [ ] Với service bắt buộc dependency, ưu tiên constructor injection và `Objects.requireNonNull`.
- [ ] Không gọi `ApplicationContext.getBean(...)` khắp method nghiệp vụ chỉ để sửa lỗi ownership.
- [ ] Biết `autowireBean(existing)` có thể inject nhưng không register/full lifecycle.
- [ ] Phân biệt autowireBean/createBean/initializeBean/configureBean/registerSingleton theo khả năng thật.
- [ ] Không kỳ vọng proxy/AOP/lifecycle/scope của managed bean trên unmanaged object.
- [ ] Xác định owner khi object do controller, task framework, mapper, ORM hoặc test tạo ra.
- [ ] Dùng `new` bình thường với value objects/DTOs không cần DI.
- [ ] Chạy Java 17+ và đối chiếu **11 dòng stdout chính xác**.
- [ ] Khi dùng `javac`/`java`, cung cấp đầy đủ Spring compile/runtime classpath.
- [ ] Có unit test Java thuần và wiring smoke test; negative tests ở cấu hình riêng.

**Kết luận:** `@Autowired` không tự tạo dependency cho mọi object Java. Spring tiêm collaborator **khi chính container quản lý hoặc được yêu cầu xử lý một instance theo API rõ ràng**. Object do `new` bên ngoài container mặc định không đi qua quy trình đó, vì vậy trường annotated có thể vẫn null. Hướng giải quyết tốt nhất là **cho Spring sở hữu service cần DI hoặc truyền dependencies qua constructor**; chỉ dùng API autowire existing instance khi thực sự tích hợp đối tượng do hệ thống khác quản lý.

### Tài liệu Spring chính thức

- [Spring Framework — Using @Autowired](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html)
- [Spring Framework — Annotation-based Container Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config.html)
- [AutowireCapableBeanFactory API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/config/AutowireCapableBeanFactory.html)
- [Spring Framework — Using AspectJ with Spring Applications](https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html)


</details>

- [Quay lại đầu trang](#back-to-top)
