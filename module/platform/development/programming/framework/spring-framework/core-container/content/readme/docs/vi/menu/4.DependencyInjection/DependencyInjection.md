<a id="back-to-top"></a>

# Tiêm phụ thuộc

## Menu
- [Phụ thuộc bắt buộc, phụ thuộc tùy chọn và bất biến đối tượng](#dependency-injection-001)
- [Tiêm qua hàm khởi tạo và bảo đảm trạng thái hợp lệ](#dependency-injection-002)
- [Tiêm qua setter cho cấu hình có thể tùy chọn](#dependency-injection-003)
- [Tiêm trực tiếp vào trường và ảnh hưởng tới kiểm thử](#dependency-injection-004)
- [Tiêm qua phương thức cấu hình có nhiều tham số](#dependency-injection-005)
- [Phân biệt tham chiếu bean với giá trị đơn giản](#dependency-injection-006)
- [Tiêm dựa trên hợp đồng thay vì tự tìm kiếm đối tượng](#dependency-injection-007)
- [Nhận diện quá nhiều phụ thuộc và trách nhiệm cần tách](#dependency-injection-008)

## <a id="dependency-injection-001">Phụ thuộc bắt buộc, phụ thuộc tùy chọn và bất biến đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Phụ thuộc bắt buộc, phụ thuộc tùy chọn và bất biến đối tượng trong Spring

## 1. Phân loại dependency theo hợp đồng của đối tượng

Một class Java có thể cần một hoặc nhiều đối tượng cộng tác (*dependencies*) để thực hiện nhiệm vụ. Trong Spring Core Container, **Dependency Injection (DI)** là quá trình container cung cấp những dependency đã được định nghĩa cho bean, thay vì để bean tự tìm hoặc tự tạo tất cả cộng tác viên.

Câu hỏi quan trọng khi thiết kế một service không phải “đặt annotation nào?”, mà là **dependency đó có bắt buộc để đối tượng hợp lệ không?**

| Loại phụ thuộc | Ý nghĩa | Khi thiếu dependency | Cách thiết kế nên ưu tiên |
| --- | --- | --- | --- |
| **Bắt buộc (required)** | Không thể thực hiện hợp đồng chính của object nếu thiếu | Nên thất bại sớm và rõ nguyên nhân | **Constructor injection**, tham số không được `null`, trường `final` |
| **Tùy chọn (optional)** | Thiếu vẫn có một hành vi hợp lệ đã định nghĩa trước | Dùng mặc định, bỏ qua tính năng hoặc cơ chế thay thế | `Optional<T>`, `ObjectProvider<T>`, default strategy; setter nếu cần tái cấu hình |
| **Cấu hình giá trị (configuration)** | Tham số như timeout, base URL, giới hạn nghiệp vụ | Cần chính sách validation và default rõ ràng | Constructor/configuration factory method, value object bất biến |

Ví dụ một `BillingService` **phải có** `PaymentGateway` để ghi nhận thanh toán, nhưng **có thể không có** `AuditSink` trong một demo/offline mode. Khi thiếu `PaymentGateway`, tạo service sẽ thất bại; khi thiếu `AuditSink`, service vẫn thanh toán được nhưng không phát bản ghi audit.

**Tùy chọn không đồng nghĩa “không quan trọng về bảo mật”.** Nếu audit là yêu cầu bắt buộc vì pháp lý/kiểm toán thì phải thiết kế nó là **required**; không được silently skip một chức năng thiết yếu chỉ vì API `Optional` cho phép.

Mục tiêu học: xác định đúng hợp đồng, phân biệt **container thiếu bean** với **giá trị null do code tự truyền**, làm chủ các lựa chọn optional của Spring và hiểu phạm vi thực sự của `final`/immutability.

## 2. Constructor injection và bất biến trạng thái cấu hình

Với dependency bắt buộc, đối tượng nên từ chối được tạo nếu giá trị cần thiết không tồn tại:

~~~java
// Snippet Java thuần: các type PaymentGateway/AuditSink định nghĩa ở demo đầy đủ.
public final class BillingService {
    private final PaymentGateway gateway;
    private final Optional<AuditSink> audit;

    public BillingService(
            PaymentGateway gateway, Optional<AuditSink> audit) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.audit = Objects.requireNonNull(audit, "audit");
    }
}
~~~

Đây là **mẫu minh họa**, không phải source biên dịch độc lập: cần import `java.util.Objects`, `java.util.Optional` và khai báo các interfaces. Chương trình hoàn chỉnh có ở mục 5.

**Constructor injection** có ba lợi ích trực tiếp:

1. **Thiếu dependency bắt buộc bị phát hiện sớm.** Java caller cố truyền `null` sẽ nhận exception tại constructor thay vì một `NullPointerException` ở giữa xử lý nghiệp vụ.
2. **Object ra khỏi constructor trong trạng thái hợp lệ.** Không cần tạm tồn tại một `BillingService` chưa có gateway rồi mong setter được gọi trước khi dùng.
3. **Dependency references có thể là `final`.** Sau khi constructor chạy xong, class không thể gán lại trường `gateway` hoặc `audit` trong Java thông thường.

Spring cũng khuyến nghị constructor injection cho các thành phần bắt buộc vì nó cho phép thiết kế bất biến và tránh đối tượng được trả về trong trạng thái chưa hoàn tất. Với một Spring-managed component chỉ có **một constructor**, Spring thường có thể chọn constructor đó mà không cần viết `@Autowired`; với `@Bean` factory method, ta có thể inject qua **tham số factory method**. Quy tắc nhiều constructors phức tạp hơn, không suy ra rằng Spring sẽ đoán đúng mọi constructor overload.

### `final` không đồng nghĩa deep immutability

~~~java
private final List<String> events = new ArrayList<>();
~~~

Reference `events` không thể được gán sang list khác sau khởi tạo, nhưng **nội dung list vẫn có thể thay đổi**. Tương tự, `private final PaymentGateway gateway` chỉ giữ cố định *reference*; nếu implementation `PaymentGateway` có trạng thái mutable, cả object graph không tự trở nên bất biến hoặc thread-safe.

**Bất biến đối tượng có nhiều mức:**
- **Stable dependency wiring:** reference tới dependency không bị đổi sau construction (`final`).
- **Immutable state:** các giá trị mà object chứa không thay đổi sau construction; khi dùng mutable inputs nên tạo defensive copies.
- **Deep immutability:** cả object con reachable từ object cũng phải có hợp đồng phù hợp.
- **Thread safety:** cần xem xét cả concurrency và state thực tế; Spring singleton **không tự bảo đảm thread safety**.

Một service có thể có final dependencies mà vẫn thao tác với database, ghi log hoặc gọi mutable client. **Immutability của cấu hình và side effects của phương thức là hai khái niệm khác nhau.**

## 3. Spring hiểu “required” ở điểm inject như thế nào?

Trong Spring, dependency thông thường được coi là **bắt buộc** khi resolve một tham số constructor hoặc factory method theo type. Nếu container không có bean phù hợp và không có một quy tắc đặc biệt cho điểm inject, quá trình tạo bean thất bại.

~~~java
// Snippet configuration, có đầy đủ type/import trong demo mục 5.
@Bean
BillingService billingService(
        PaymentGateway gateway, Optional<AuditSink> audit) {
    return new BillingService(gateway, audit);
}
~~~

`PaymentGateway gateway` là **required**. Spring phải tìm bean tương thích để gọi factory method. `Optional<AuditSink>` là điểm inject **optional** theo khả năng resolve của Spring: nếu không có bean `AuditSink` thích hợp, nó có thể truyền `Optional.empty()`; khi có đúng một candidate phù hợp, nó truyền `Optional.of(bean)`.

Nếu có **hai beans cùng type** mà không có qualifier/primary hoặc cơ chế phân giải rõ ràng, Spring có thể báo ambiguity. `Optional<T>` không có nghĩa “lấy tùy một bean đầu tiên” khi có nhiều candidates.

### Khi Spring resolve thiếu bean bắt buộc

~~~text
@Bean billingService(PaymentGateway gateway, Optional<AuditSink> audit)
                         |
                         v
           Tìm PaymentGateway trong context
                     /                 \
             Có 1 phù hợp           Không có
                  |                     |
                  v                     v
         Inject reference       Thất bại tạo bean
                  |
                  v
         Resolve optional AuditSink
                  |
            Có / không có
                  |
                  v
       Tạo BillingService hợp lệ
~~~

Khi một singleton không lazy được pre-instantiate ở `ApplicationContext.refresh()`, thiếu required bean có thể khiến **cả context khởi động thất bại** trước khi ứng dụng gọi `getBean`. Với lazy/prototype, lỗi có thể xuất hiện muộn hơn vào lúc tạo bean. Không đồng nhất lỗi tạo bean với lỗi Maven chưa tải đủ JAR.

**Khác biệt giữa hai tầng validation:**

- **Container validation:** Có bean nào có type/qualifier đáp ứng injection point?
- **Object invariant validation:** Tham số có hợp lệ về null, domain, phạm vi giá trị hay quan hệ business không?

Spring có thể đã resolve đúng bean theo type nhưng bean ấy vẫn có một cấu hình nghiệp vụ không hợp lệ. Dùng validation tại constructor/factory để giữ invariant, thay vì kỳ vọng DI tự kiểm tra mọi quy tắc domain.

## 4. Các cách diễn đạt phụ thuộc tùy chọn

Spring hỗ trợ nhiều cách, với ý nghĩa và lifecycle không hoàn toàn giống nhau.

| Cách diễn đạt | Khi không có bean | Khi nên cân nhắc |
| --- | --- | --- |
| **`Optional<T>`** tại injection point | Thường nhận `Optional.empty()` | Biết ngay tại construction xem tính năng có sẵn không; dependency optional thật sự |
| **`ObjectProvider<T>`** | Nhận provider; có thể kiểm tra `getIfAvailable()` sau | Muốn **lookup theo nhu cầu**, trì hoãn việc lấy bean hoặc phối hợp scope |
| **`@Nullable T`** trên tham số | Cho phép nhận `null` theo quy tắc injection | Tích hợp API vốn dùng null; phải code null-safe và chọn đúng annotation |
| **`@Autowired(required = false)`** trên setter/field | Setter không được gọi hoặc field không được set nếu thiếu candidate | Optional mutable/configuration legacy; cần default hợp lệ |
| **No-op/default implementation** | Luôn inject một implementation đáp ứng hợp đồng | Khi tính năng luôn cần một strategy nhưng có implementation mặc định không thực hiện side effect |

**Khuyến nghị cho code mới:** `Optional<T>` rõ nghĩa cho một dependency có thể vắng mặt ngay lúc tạo object; `ObjectProvider<T>` hữu ích nếu dependency cần lấy động tại thời điểm gọi method. Không dùng optional cho thứ mà service không thể hoạt động nếu thiếu.

### `Optional<T>` ngay trong constructor

~~~java
public BillingService(
        PaymentGateway gateway, Optional<AuditSink> audit) {
    this.gateway = Objects.requireNonNull(gateway);
    this.audit = Objects.requireNonNull(audit);
}
~~~

`audit` **không được là null**; vắng audit được biểu diễn bằng `Optional.empty()`. Điều này tránh kết hợp hai cơ chế “vắng”: vừa `null`, vừa empty. Ở caller Java thuần, bạn phải tự tạo `Optional.empty()` hoặc `Optional.of(sink)`; Spring chỉ giúp resolve `Optional` khi thực sự tạo/tiêm bean.

### `ObjectProvider<T>`: lấy khi cần

~~~java
// Snippet trong một bean Spring, giả sử provider đã được inject.
private final ObjectProvider<AuditSink> sinkProvider;

public void maybeRecord(String message) {
    AuditSink sink = sinkProvider.getIfAvailable();
    if (sink != null) {
        sink.record(message);
    }
}
~~~

Đoạn trên cần import `org.springframework.beans.factory.ObjectProvider` và các type phù hợp. **Provider không tự biến một bean bị thiếu thành một implementation**, chỉ cho phép hỏi và xử lý sự vắng mặt. `getObject()` thông thường **yêu cầu có bean**, không giống `getIfAvailable()`; với nhiều candidates, cần xử lý ambiguity/qualifiers.

Provider có thể hữu ích khi một singleton cần tạo/lấy prototype theo từng lượt. Inject prototype trực tiếp vào constructor singleton **không** tự làm mới dependency ở mỗi lần gọi method.

### `@Autowired(required=false)` và `@Nullable`

~~~java
// Snippet minh họa (không phải một file Java độc lập):
private AuditSink audit = message -> { }; // Mặc định no-op.

@Autowired(required = false)
public void setAudit(AuditSink sink) {
    this.audit = sink;
}
~~~

Nếu Spring không có dependency phù hợp, non-required setter có thể **không được gọi**, nên field giữ giá trị no-op ban đầu. Cách này hợp lý khi object **cho phép thay strategy sau construction**; nhưng state giờ mutable, cần cân nhắc concurrency và thời điểm object được dùng.

`@Nullable` được Spring hỗ trợ trên điểm inject (chẳng hạn `org.jspecify.annotations.Nullable` trong hệ Spring hiện đại) để biểu diễn việc vắng dependency bằng `null`. API này cần đúng dependency/import và phải được xử lý null-safe. **Không nhầm `@Nullable` với `@Autowired(required=false)` trên mọi vị trí**; chúng có semantics khác nhau, đặc biệt ở method nhiều tham số.

## 5. Dự án Spring Framework 7 + Java 17: ba tình huống required/optional

Ví dụ dùng **một business service** và ba cách cấu hình container:

1. Có `PaymentGateway`, **không có `AuditSink`** → service tạo được, `Optional.empty()`.
2. Có `PaymentGateway` và **có một `AuditSink`** → service tạo được, audit nhận thông điệp.
3. **Không có `PaymentGateway`** → container từ chối tạo `BillingService` bắt buộc, thay vì cung cấp null âm thầm.

Ba lần khởi tạo context độc lập, không dùng Spring Boot hay web server.

### 5.1. Cấu trúc Maven

~~~text
required-optional-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── RequiredOptionalDemo.java
~~~

Yêu cầu: **JDK 17+**, Maven **3.9+**, tải thư viện từ repository ở lần chạy đầu.

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>required-optional-demo</artifactId>
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
                    <mainClass>demo.RequiredOptionalDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` có các API container và kéo các Spring artifacts nền cần thiết theo Maven transitive dependencies; không phải khai báo Spring Boot starter. Spring Framework 7.0 có baseline Java 17; dùng BOM/quản lý phiên bản phù hợp khi bổ sung các Spring modules khác vào ứng dụng thật.

### 5.2. Mã `src/main/java/demo/RequiredOptionalDemo.java`

~~~java
package demo;

import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.BeansException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class RequiredOptionalDemo {

    private RequiredOptionalDemo() {
    }

    interface PaymentGateway {
        String charge(String invoice, long amount);
    }

    interface AuditSink {
        void record(String message);
    }

    static final class FixedGateway implements PaymentGateway {
        @Override
        public String charge(String invoice, long amount) {
            if (amount < 0) {
                throw new IllegalArgumentException("amount must be >= 0");
            }
            return "OK:" + invoice + ":" + amount;
        }
    }

    static final class ConsoleAuditSink implements AuditSink {
        @Override
        public void record(String message) {
            System.out.println("AUDIT:" + message);
        }
    }

    // Business service: không phụ thuộc API Spring.
    public static final class BillingService {
        private final PaymentGateway gateway;
        private final Optional<AuditSink> audit;

        public BillingService(
                PaymentGateway gateway, Optional<AuditSink> audit) {
            this.gateway = Objects.requireNonNull(gateway, "gateway");
            this.audit = Objects.requireNonNull(audit, "audit");
        }

        public boolean hasAudit() {
            return audit.isPresent();
        }

        public String bill(String invoice, long amount) {
            String receipt = gateway.charge(invoice, amount);
            audit.ifPresent(sink -> sink.record(receipt));
            return receipt;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class BaseConfig {
        @Bean
        public PaymentGateway paymentGateway() {
            return new FixedGateway();
        }

        @Bean
        public BillingService billingService(
                PaymentGateway gateway, Optional<AuditSink> audit) {
            return new BillingService(gateway, audit);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AuditConfig {
        @Bean
        public AuditSink auditSink() {
            return new ConsoleAuditSink();
        }
    }

    // Cố tình KHÔNG khai báo PaymentGateway.
    @Configuration(proxyBeanMethods = false)
    public static class MissingGatewayConfig {
        @Bean
        public BillingService billingService(
                PaymentGateway gateway, Optional<AuditSink> audit) {
            return new BillingService(gateway, audit);
        }
    }

    public static void main(String[] args) {
        // Có required PaymentGateway, thiếu optional AuditSink.
        try (var context =
                new AnnotationConfigApplicationContext(BaseConfig.class)) {
            BillingService service = context.getBean(BillingService.class);
            System.out.println("without.audit=" + service.hasAudit());
            System.out.println("without.result="
                    + service.bill("INV-1", 120));
            System.out.println("without.singleton="
                    + (service == context.getBean(BillingService.class)));
        }

        // Đăng ký thêm một bean AuditSink.
        try (var context = new AnnotationConfigApplicationContext(
                BaseConfig.class, AuditConfig.class)) {
            BillingService service = context.getBean(BillingService.class);
            System.out.println("with.audit=" + service.hasAudit());
            // Lệnh dưới gọi AuditSink.record(), in dòng AUDIT trước.
            String receipt = service.bill("INV-2", 200);
            System.out.println("with.result=" + receipt);
        }

        // Không có bean required: lỗi khi startup context.
        try (var context = new AnnotationConfigApplicationContext(
                MissingGatewayConfig.class)) {
            System.out.println("unreachable");
        } catch (BeansException expected) {
            System.out.println("missingRequired.rejected=true");
        }
    }
}
~~~

**Vì sao có thể biên dịch và chạy độc lập:** Tất cả classes/interfaces của demo nằm trong cùng source, chỉ cần dependency `spring-context`. `BaseConfig` khai báo gateway và service; `AuditConfig` độc lập chỉ khai báo audit. Spring resolve tham số `PaymentGateway` bắt buộc và tham số `Optional<AuditSink>` tùy chọn cho factory method.

`BillingService` giữ hai field `final`, không có setters, và tự từ chối tham số `null` qua `Objects.requireNonNull`. Đây là **thiết kế không đổi reference**; không suy luận rằng mọi implementation `PaymentGateway`/`AuditSink` sẽ bất biến hoặc thread-safe.

`MissingGatewayConfig` chỉ có `BillingService` cần gateway. Ở cấu hình mặc định (singleton không lazy), context không thể hoàn thành refresh; exception thuộc họ `BeansException`. Chương trình chỉ in một dòng boolean xác nhận thất bại, **không dựa vào toàn bộ class/message/stack trace** vốn phụ thuộc version và logger.

**Chú ý `@Bean` factory-method injection:** Với `proxyBeanMethods=false`, factory methods trong ví dụ **không tự gọi nhau**. Spring cấp dependency cho `billingService(...)` qua tham số; không gọi `paymentGateway()` bằng tay ở bên trong để giả định đó là singleton.

## 6. Chạy Maven và đối chiếu stdout

Tại thư mục `required-optional-demo` có `pom.xml`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

Các lệnh kiểm tra Java/Maven in thông tin phụ thuộc môi trường. Lệnh cuối dùng compiler qua Maven để biên dịch target Java 17 rồi chạy class `demo.RequiredOptionalDemo`.

**7 dòng stdout xác định của chương trình** (không tính log/exception diagnostic do Maven hoặc Spring backend sinh ngoài các lệnh `System.out.println`):

~~~text
without.audit=false
without.result=OK:INV-1:120
without.singleton=true
with.audit=true
AUDIT:OK:INV-2:200
with.result=OK:INV-2:200
missingRequired.rejected=true
~~~

| Dòng output | Bằng chứng | Giới hạn suy luận |
| --- | --- | --- |
| `without.audit=false` | `Optional.empty()` được dùng khi không có bean AuditSink | Không có nghĩa API `AuditSink` bị lỗi |
| `without.result=...` | Required gateway tồn tại, nghiệp vụ vẫn chạy | Không chứng minh mọi cấu hình optional đều an toàn |
| `without.singleton=true` | Bean BillingService mặc định singleton trong một context | Không phải một instance toàn JVM |
| `with.audit=true` | Có đúng một AuditSink phù hợp ở context thứ hai | Không có nghĩa audit bắt buộc trong context thứ nhất |
| `AUDIT:...` | Optional dependency được dùng khi hiện diện | Một side effect có thể xảy ra mà kết quả bill vẫn trả đúng |
| `with.result=...` | Business receipt không đổi khi thêm audit | Không tự bảo đảm audit delivery thật sự bền vững |
| `missingRequired.rejected=true` | Tạo singleton thiếu required dependency bị từ chối | Không có nghĩa thiếu mọi optional dependency cũng fail |

Khi một context startup thất bại, Spring có thể log warning/exception trước khi exception được chương trình catch. Expected output ở đây là **các dòng từ chính source**, không phải toàn bộ nội dung terminal.

Nếu ứng dụng dùng một `PaymentGateway` thật có I/O, không cố định expected output theo các lệnh gọi network; demo dùng `FixedGateway` có kết quả xác định và không có side effects bên ngoài.

## 7. Java thuần Java 17: required/optional không cần Spring

Tính **bắt buộc/tùy chọn** là vấn đề hợp đồng của object, không chỉ là tính năng Spring. Để tự kiểm chứng, tạo file **`RequiredOptionalPureJava.java`** tách biệt:

~~~java
import java.util.Objects;
import java.util.Optional;

public class RequiredOptionalPureJava {
    interface Store {
        String id();
    }

    static final class Reader {
        private final Store store;
        private final Optional<String> label;

        Reader(Store store, Optional<String> label) {
            this.store = Objects.requireNonNull(store, "store");
            this.label = Objects.requireNonNull(label, "label");
        }

        String describe() {
            return store.id() + ":" + label.orElse("default");
        }
    }

    public static void main(String[] args) {
        Store store = () -> "store";
        System.out.println("noLabel="
                + new Reader(store, Optional.empty()).describe());
        System.out.println("withLabel="
                + new Reader(store, Optional.of("vip")).describe());

        try {
            new Reader(null, Optional.empty());
        } catch (NullPointerException expected) {
            System.out.println("requiredRejected=true");
        }
    }
}
~~~

Chạy từ thư mục chứa file, **không cần Maven hay JAR Spring**:

~~~bash
javac RequiredOptionalPureJava.java
java RequiredOptionalPureJava
~~~

Expected stdout:

~~~text
noLabel=store:default
withLabel=store:vip
requiredRejected=true
~~~

Hai ví dụ dùng cùng nguyên tắc: **required không null**, **optional biểu diễn vắng mặt rõ ràng**, và **reference được giữ final**. Spring chỉ tổ chức việc resolve/dependency wiring; Java thuần vẫn phải bảo vệ invariants khi caller chủ động dùng `new`.

Một lưu ý thiết kế: `Optional` thường phù hợp với return value và một số dependency injection points; không nên cố đưa `Optional` vào mọi field hoặc mọi DTO để thay thế việc định nghĩa contract và serialisation rõ ràng. Ở ví dụ, field `Optional<AuditSink>` có chủ đích để làm nổi bật optional dependency.

## 8. Setter injection khi cấu hình không thiết yếu có giá trị mặc định

Trong ví dụ `BillingService`, `PaymentGateway` là dependency bắt buộc còn `AuditSink` được biểu diễn là `Optional<AuditSink>` ngay trong constructor. Với một **thiết lập có mặc định hợp lệ** và cho phép điều chỉnh, setter là một lựa chọn khác:

~~~java
// Mẫu Java thuần để minh họa setter, chưa có main().
public final class ReportService {
    private boolean verbose; // Mặc định false, object vẫn hợp lệ.

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void report() {
        if (verbose) {
            System.out.println("report:verbose");
        }
    }
}
~~~

Không gọi `setVerbose(...)` vẫn để `ReportService` có hành vi xác định. Với setter `@Autowired(required = false)` nhận **bean tùy chọn**, Spring có thể bỏ qua lời gọi setter nếu không tìm được dependency phù hợp; vì vậy trạng thái mặc định phải an toàn. Đừng biến một dependency thật sự bắt buộc như `PaymentGateway` thành setter tùy chọn chỉ để context khởi động được.

**Giới hạn:** setter giữ state có thể thay đổi, nên nếu sửa cấu hình khi một singleton đang phục vụ nhiều luồng phải có chính sách đồng bộ và tính nhất quán rõ ràng. Quy tắc chọn nhiều constructor và cách chẩn đoán constructor quá nhiều dependency là hai chủ đề riêng; chúng không làm thay đổi định nghĩa required/optional của bài này.

## 9. Immutability thực tế: dữ liệu, defensive copy và concurrency

Một class có `final` fields vẫn có thể bị thay đổi gián tiếp nếu field trỏ đến collection do caller quản lý. Ví dụ:

~~~java
// Snippet Java 17: giữ bản sao bất biến của các tags.
public final class BillingPolicy {
    private final List<String> allowedTags;

    public BillingPolicy(List<String> tags) {
        this.allowedTags = List.copyOf(
                Objects.requireNonNull(tags, "tags"));
    }

    public List<String> allowedTags() {
        return allowedTags;
    }
}
~~~

`List.copyOf` tạo hoặc tái sử dụng một **unmodifiable list** với hợp đồng phù hợp và từ chối phần tử `null`; caller không thể sửa các phần tử của list qua reference được trả về. Tuy nhiên nếu phần tử là mutable object, **bản thân object con** vẫn có thể thay đổi. Đoạn trên cần import `java.util.List`, `java.util.Objects` và đặt trong file có class public cùng tên.

Các nguyên tắc nâng mức bất biến:

1. Khai báo field `private final` cho state nên giữ cố định; constructor thực thi validation.
2. Tránh công khai setters thay state thiết yếu sau construction.
3. Dùng value objects immutable (ví dụ `record` Java 17 khi phù hợp), defensive copies cho collections/arrays và không rò reference mutable.
4. Xác định rõ ownership của resource và side effects. Một `PaymentGateway` final vẫn có thể thực hiện giao dịch bên ngoài.
5. Khi Spring tạo singleton, **đảm bảo code của bean thread-safe theo nhu cầu**: Spring scope không tự bảo vệ mutable caches hoặc counters.
6. Tránh circular constructor dependencies. Nếu A cần B để constructor hoàn tất và B đồng thời cần A, container có thể báo lỗi khi tạo graph; xem lại thiết kế thay vì chuyển mọi dependency sang setter để che vòng lặp.

**Không biến mọi dependency thành Optional để né lỗi startup.** Nếu một ứng dụng bắt buộc phải thanh toán, thiếu PaymentGateway nên là failure rõ ràng — khởi động “thành công” với một payment gateway null/no-op có thể nguy hiểm hơn fail-fast.

## 10. Lỗi thường gặp và cách tự chẩn đoán

| Triệu chứng/nhận định | Nguyên nhân khả dĩ | Cách khắc phục |
| --- | --- | --- |
| `NoSuchBeanDefinitionException` cho PaymentGateway | Không có bean phù hợp trong context | Đăng ký một `@Bean`/component scan đúng, xem cấu hình active |
| `UnsatisfiedDependencyException` lúc refresh | Bean cần dependency bắt buộc chưa resolve | Đọc exception `Caused by`; xác minh type, bean name, qualifier |
| `NoUniqueBeanDefinitionException` | Nhiều candidates cùng type mà không có cách chọn | Dùng `@Qualifier`, `@Primary` hoặc phân tách interface |
| `Optional<AuditSink>` luôn empty | Bean AuditSink không được đăng ký trong context hiện tại | Kiểm tra config class/scan, không ép code `get()` |
| Gọi `audit.get()` khi Optional.empty | `NoSuchElementException` trong business code | Dùng `ifPresent`, `orElse`, `orElseGet` theo policy |
| `@Autowired(required=false)` setter không chạy | Không có candidate hợp lệ cho injection point | Đảm bảo default object state hợp lệ trước setter |
| `ObjectProvider.getObject()` bị lỗi | API này yêu cầu bean, không cho fallback tự động | Chọn `getIfAvailable()` hoặc xử lý exception |
| `Objects.requireNonNull` vẫn ném NPE | Code tự gọi `new Service(null,...)` hoặc factory tự truyền null | Sửa caller/factory; container injection không thay validation |
| Có `final` fields nhưng state vẫn thay đổi | Dependency hoặc collection được tham chiếu vẫn mutable | Defensive copy, immutable implementations, đồng bộ state thích hợp |
| Singleton bị race khi nhiều request | Có mutable state không an toàn được chia sẻ | Thiết kế stateless/immutable hoặc cơ chế concurrency đúng |
| Context tạo được nhưng nghiệp vụ sai | DI chỉ giải quyết wiring, không chứng minh domain rule đúng | Unit/integration tests, input validation và contract checks |
| Java import Spring không compile | Thiếu `spring-context` trên compile classpath | Dùng Maven dependency đúng version và reload project |

### Phân biệt lỗi hạ tầng với lỗi hợp đồng

- Nếu Maven không tìm thấy artifact, đó là vấn đề **dependency resolution/repository**.
- Nếu code Java không import được `org.springframework.context.annotation`, đó là vấn đề **compile classpath**.
- Nếu context không tìm được bean, đó là vấn đề **DI configuration**.
- Nếu constructor tự ném `IllegalArgumentException`/`NullPointerException`, đó là vấn đề **invariant/argument validation** hoặc factory đang truyền giá trị không hợp lệ.
- Nếu `bill()` xử lý sai `amount`, đó là vấn đề **business logic**, không được Spring tự phát hiện.

Đọc **nguyên nhân gốc trong exception chain**, thay vì chép nguyên message cố định của Spring theo một patch version; các type và thông báo lồng nhau có thể thay đổi theo cách context được dựng.

## 11. Thử nghiệm có kiểm chứng

Thực hiện từng thay đổi độc lập trên một bản sao, khôi phục code gốc để đối chiếu stdout ở mục 6:

1. **Không có optional:** chạy BaseConfig, giải thích vì sao `without.audit=false` nhưng vẫn có `without.result`.
2. **Có optional:** thêm AuditConfig, chỉ ra thời điểm `ConsoleAuditSink.record()` chạy và lý do dòng `AUDIT` xuất hiện trước `with.result`.
3. **Không có required:** chạy MissingGatewayConfig riêng và xác định loại lỗi startup. Vì sao thiếu gateway phải fail thay vì `Optional.empty()`?
4. **Gây ambiguity:** khai báo hai beans `PaymentGateway` khác nhau, rồi thêm `@Qualifier`/`@Primary` để giải quyết. Không dùng Optional để che lỗi.
5. **Constructor guard:** tự tạo `new BillingService(null, Optional.empty())` bên ngoài Spring, xác định exception là do Java constructor validation, không phải container.
6. **Đổi optional sang required:** thay tham số `Optional<AuditSink>` bằng `AuditSink`; dự đoán trường hợp BaseConfig không còn khởi động bình thường.
7. **Dùng Provider:** inject `ObjectProvider<AuditSink>`, so sánh `getIfAvailable()` với `getObject()` khi thiếu bean.
8. **Thêm no-op fallback:** tạo `NoOpAuditSink` và inject như một bean mặc định có điều kiện/hợp đồng rõ; so sánh với việc bỏ hẳn audit.
9. **Setter optional:** chuyển audit thành setter với `@Autowired(required=false)`, cho default no-op, so sánh vì sao wiring giờ mutable hơn.
10. **Thử concurrency:** giải thích vì sao `final PaymentGateway` không bảo đảm implementation của gateway thread-safe khi gọi đồng thời.
11. **Defensive copy:** thử sửa list được trả về từ `BillingPolicy.allowedTags()`, dự đoán exception và phân biệt shallow/deep immutability.
12. **Unit test thuần Java:** truyền fake gateway và `Optional.empty()` để kiểm thử `BillingService` mà không khởi tạo context; phân biệt test business với test wiring.

### Checklist đạt bài

- [ ] Phân biệt required, optional và default dependency theo **hợp đồng nghiệp vụ**.
- [ ] Dùng constructor injection và `Objects.requireNonNull` cho required dependency.
- [ ] Dự đoán đúng hành vi `Optional<AuditSink>` không/có bean.
- [ ] Không nhầm `ObjectProvider.getObject()` với `getIfAvailable()`.
- [ ] Giải thích đúng `@Autowired(required=false)` trên setter và điểm khác với constructor.
- [ ] Phân biệt `final` reference, immutable state, deep immutability và thread safety.
- [ ] Biết vì sao thiếu PaymentGateway làm context fail-fast.
- [ ] Chạy Maven demo Java17 ra đúng 7 dòng stdout chính và Java thuần ra đúng 3 dòng.
- [ ] Biết kiểm tra dependency ambiguity và không dùng optional để che lỗi cấu hình.

## 12. Tóm tắt và tài liệu chính thức

**Điểm cần ghi nhớ:**

- **Required dependency:** object không thể hợp lệ khi thiếu; hãy bảo vệ bằng constructor injection, non-null contract và validation. Spring mặc định đòi resolve nó.
- **Optional dependency:** vắng vẫn có hành vi hợp lệ; diễn đạt bằng `Optional<T>`, `ObjectProvider<T>` hoặc một default strategy phù hợp. Đừng gắn optional cho nghiệp vụ vốn bắt buộc.
- **Bất biến:** `final` cố định reference nhưng **không tự cố định trạng thái của dependency**; sử dụng defensive copy và value objects khi cần.
- **Thiết kế DI tốt:** nghiệp vụ nhận dependency rõ ràng và có thể kiểm thử bằng Java thuần; Spring là công cụ wiring/lifecycle, không phải nguồn gốc của quy tắc bất biến.

Tài liệu tra cứu:
- [Spring Framework – Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)
- [Spring Framework – Using @Autowired](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html)
- [Spring Framework – Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java.html)
- [Spring Framework – Using @Bean](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html)
- [Spring Framework – Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)
- [Spring Framework – Dependency Resolution with Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html)
- [Java 17 – `java.util.Optional`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Optional.html)
- [Java 17 – `Objects.requireNonNull`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html)

**Kết luận:** Trong Spring Core Container, “phụ thuộc bắt buộc” và “phụ thuộc tùy chọn” là **quyết định thiết kế đối tượng**, còn `Optional`, provider và autowiring là những công cụ để thực hiện quyết định ấy. Một service với constructor rõ ràng, required dependencies được kiểm tra và references `final` sẽ dễ lý giải, kiểm thử và vận hành hơn mà không ngộ nhận rằng Spring tự làm mọi object bất biến.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dependency-injection-002">Tiêm qua hàm khởi tạo và bảo đảm trạng thái hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Tiêm qua hàm khởi tạo và bảo đảm trạng thái hợp lệ — Spring Framework Core Container

## 1. Vấn đề cần giải quyết

Giả sử `CheckoutService` muốn tính tiền một đơn hàng và ghi lại giao dịch. Nó phụ thuộc vào `PricingPolicy` để tính tổng tiền và `AuditTrail` để ghi lịch sử. Cả hai là **dependency bắt buộc**: nếu một dependency chưa được cung cấp, service **không nên được tạo thành công rồi đợi tới lúc gọi method mới báo `NullPointerException`**.

**Constructor injection** (tiêm phụ thuộc qua hàm khởi tạo) là thiết kế trong đó những dependency bắt buộc xuất hiện trong **tham số constructor**; caller hoặc Spring container phải cung cấp chúng tại thời điểm tạo object. Constructor có thể kiểm tra chúng, bảo đảm object không thoát ra ngoài trong trạng thái thiếu thành phần thiết yếu.

~~~mermaid
flowchart LR
    Container["Spring IoC Container"] -->|"tạo và cung cấp"| P["pricingPolicy : PricingPolicy"]
    Container -->|"tạo và cung cấp"| A["auditTrail : AuditTrail"]
    P -->|"constructor parameter"| S["checkoutService : CheckoutService"]
    A -->|"constructor parameter"| S
    S -->|"đã có các collaborator"| Use["checkout(...)"]
~~~

Trong hình, mũi tên từ dependency tới service là **luồng cung cấp object vào constructor**. Nếu vẽ *dependency graph* theo quy ước **consumer → dependency**, bạn sẽ viết `CheckoutService → PricingPolicy` và `CheckoutService → AuditTrail`. Không nên nhầm hai quy ước hướng mũi tên.

Sau bài học bạn có thể:

1. Viết constructor nhận dependency bắt buộc thay vì `new` hoặc gọi `ApplicationContext.getBean(...)` từ code nghiệp vụ.
2. Hiểu cách Spring tự chọn **constructor duy nhất** của một bean (không bắt buộc `@Autowired` với một constructor).
3. Bảo đảm tham chiếu không null và giá trị cấu hình hợp lệ **ngay tại ranh giới khởi tạo**, kể cả khi object được tạo thủ công ngoài Spring.
4. Sử dụng `private final` hợp lý, hiểu giới hạn của nó đối với tính bất biến và thread safety.
5. Kiểm thử được wiring, constructor guards, logic nghiệp vụ và ca thiếu bean.
6. Chạy ví dụ Java 17+ dùng **Spring Framework Core Container thuần**, Maven, `javac`/`java` và stdout chính xác.
7. Phân biệt constructor injection với setter/field injection, dependency tùy chọn, scope/lazy và vòng phụ thuộc.

**Giới hạn:** bài học tập trung vào **constructor injection và tính hợp lệ của trạng thái sau khởi tạo**, không đi sâu Spring Boot, MVC, JPA, transaction hoặc các framework validation bên ngoài.

## 2. Tiêm phụ thuộc là gì nếu bỏ Spring ra khỏi bài toán?

Trong Java thuần, một lớp có thể tự tạo collaborator:

~~~java
public final class CheckoutService {
    private final PricingPolicy pricing = new PricingPolicy(150);
    // Service tự quyết định giá, khó thay bằng fake khi test.
}
~~~

**Snippet minh họa**, không phải file Java hoàn chỉnh; `PricingPolicy` là lớp nghiệp vụ được định nghĩa trong project ở mục 5. Ở đây service chịu cả trách nhiệm tính tiền **và chọn/cấu hình một dependency**.

Thiết kế với constructor injection:

~~~java
public final class CheckoutService {
    private final PricingPolicy pricing;
    private final AuditTrail audit;

    public CheckoutService(PricingPolicy pricing, AuditTrail audit) {
        this.pricing = Objects.requireNonNull(pricing, "pricing is required");
        this.audit = Objects.requireNonNull(audit, "audit is required");
    }
}
~~~

**Snippet minh họa**, cần import `Objects` cùng định nghĩa hai collaborator. Tại đây:

- Class tuyên bố minh bạch cần `PricingPolicy` và `AuditTrail` để tồn tại hợp lệ.
- Caller **phải chọn và cung cấp** dependency ở lúc gọi constructor.
- Không có constructor mặc định cho phép tạo một `CheckoutService` “rỗng” rồi quên inject.
- `Objects.requireNonNull` ném `NullPointerException` **trong constructor** nếu tham số null, thay vì tạo object hỏng.
- `private final` bảo đảm field được gán một lần trên những đường constructor hợp lệ; code bên ngoài không thể tùy ý thay field.

Java thuần đã có DI:

~~~java
PricingPolicy pricing = new PricingPolicy(150);
AuditTrail audit = new AuditTrail();
CheckoutService service = new CheckoutService(pricing, audit);
~~~

Đây là snippet minh họa *manual wiring*. Spring không phát minh ra constructor của Java; nó thực hiện **lắp ghép object graph theo bean definitions** và cung cấp các tham số constructor cho những bean nó tạo.

### Ba tầng khác nhau

| Tầng | Trách nhiệm | Ví dụ |
| --- | --- | --- |
| Hợp đồng của class | Định nghĩa thứ gì bắt buộc, kiểm tra invariant | `CheckoutService(PricingPolicy, AuditTrail)` |
| Composition/wiring | Chọn instance đúng để đưa vào constructor | Spring resolve hai beans theo type |
| Sử dụng nghiệp vụ | Gọi method trên object đã hợp lệ | `checkout("BOOK", 2)` |

**Invariant** là điều kiện phải đúng với object ở trạng thái hợp lệ. Ở ví dụ này: hai dependency không null. Trong `PricingPolicy`, giá đơn vị phải dương. Trong method `checkout(...)`, input phải hợp lệ trước khi ghi audit. Hai loại kiểm tra *constructor invariant* và *business input validation* không thay thế nhau.

## 3. Constructor giúp tránh trạng thái khởi tạo dở dang

So sánh với field/setter injection cho dependency bắt buộc:

~~~java
class UnsafeCheckoutService {
    private PricingPolicy pricing;
    private AuditTrail audit;

    void setPricing(PricingPolicy pricing) {
        this.pricing = pricing;
    }

    void setAudit(AuditTrail audit) {
        this.audit = audit;
    }
}
~~~

**Mã minh họa**, không phải file runnable. Với API như thế, caller có thể tạo một instance rồi chỉ gọi một setter hoặc không gọi setter nào. Trước khi được cấu hình xong, object đã tồn tại nhưng **chưa đáp ứng invariant**.

Constructor injection không cho phép tạo instance qua **constructor công khai đó** nếu thiếu những tham số bắt buộc. Khi Spring container tạo một bean, nó phải tìm dependency cho constructor, tạo hoặc lấy dependency phù hợp và truyền vào **trước khi trả bean đã khởi tạo** cho consumer theo vòng đời bình thường.

| Đặc điểm | Constructor injection | Setter injection | Field injection |
| --- | --- | --- | --- |
| Dependency bắt buộc hiển thị rõ trong API | Có, ở constructor | Thường không | Không trong chữ ký constructor |
| Có thể tạo service Java thuần để unit test | Dễ | Cần gọi setters | Cần framework/reflection hoặc API phụ |
| Có thể dùng `final` cho reference | Tự nhiên | Thường không | Không hợp lý với field injection thông thường |
| Bắt buộc trạng thái hợp lệ từ cuối constructor | Có thể bằng validation | Cần cơ chế bổ sung | Phụ thuộc lifecycle/injection của container |
| Hợp lý cho giá trị thật sự tùy chọn sau tạo | Có thể, nhưng thường không phải mục tiêu chính | Phù hợp hơn trong một số thiết kế | Ít minh bạch hơn |

**Không tuyệt đối hóa:** setter injection có chỗ đứng cho dependency *thật sự tùy chọn* hoặc đối tượng cần reconfiguration. Một class có thể cần nhiều kiểu injection vì API của thư viện bên thứ ba. Nhưng với collaborator bắt buộc để thực thi nghiệp vụ, constructor injection thường là lựa chọn mặc định dễ kiểm thử và bảo trì.

### Lỗi thường gặp: tưởng final làm object tự động immutable

Trường:

~~~java
private final AuditTrail audit;
~~~

chỉ bảo đảm **reference `audit` không bị gán lại** sau khởi tạo. Nếu `AuditTrail` có danh sách mutable, những method của nó vẫn có thể thay đổi nội dung. Vì vậy:

- `final reference` không đồng nghĩa *deep immutability*.
- Bean singleton được nhiều thread gọi vẫn phải bảo đảm **thread safety** cho state mutable.
- Không để lộ một danh sách mutable qua getter rồi nghĩ rằng service đã bất biến.
- Validation ở constructor không bảo đảm mọi method sau đó luôn thành công; nó chỉ bảo đảm **object ban đầu hợp lệ theo invariant đã kiểm tra**.

Ví dụ dùng `List.copyOf(...)` khi công bố snapshot của audit, tránh trao một reference danh sách nội bộ cho caller sửa.

## 4. Spring chọn constructor bằng cách nào?

Một bean được Spring quản lý có **chỉ một constructor** có thể viết:

~~~java
@Component
public class OrderService {
    private final PricingPolicy pricing;

    public OrderService(PricingPolicy pricing) {
        this.pricing = Objects.requireNonNull(pricing);
    }
}
~~~

**Đây là snippet, không phải mã đầy đủ của project**. Nếu Spring đã đăng ký `OrderService` làm bean và biết bean `PricingPolicy` thích hợp, Spring 4.3+ có thể tự dùng constructor duy nhất mà **không yêu cầu viết `@Autowired`** trên constructor.

Với nhiều constructors hoặc candidate phức tạp, phải xem quy tắc chọn constructor:

~~~java
@Component
public class OtherService {
    private final PricingPolicy pricing;

    public OtherService() {
        throw new IllegalStateException("unsupported configuration");
    }

    @Autowired
    public OtherService(PricingPolicy pricing) {
        this.pricing = Objects.requireNonNull(pricing);
    }
}
~~~

Snippet chỉ minh họa annotation và cách chọn constructor, không phải source runnable độc lập; trong cấu hình như thế, đánh dấu constructor dự kiến bằng `@Autowired` để lựa chọn minh bạch. Không thêm constructor không tham số chỉ để làm Spring vui lòng rồi để service ở trạng thái thiếu dependency.

**Một số quy tắc đáng nhớ:**

- Một class có **một constructor**: constructor đó thường được Spring chọn, kể cả không gắn `@Autowired`.
- Nhiều constructors: nên làm rõ constructor dùng cho dependency bắt buộc; đừng trông chờ Spring suy đoán ý định nghiệp vụ.
- Một constructor gắn `@Autowired(required=true)` phải là lựa chọn bắt buộc; không gắn nhãn required cho nhiều constructors khác nhau.
- Nếu nhiều constructors được gắn `@Autowired(required=false)`, Spring chọn theo khả năng giải quyết dependency theo quy tắc của nó; cách viết này có thể khó đọc hơn một constructor rõ ràng.
- Spring chọn **bean candidate** dựa vào type, qualifier, primary và các quy tắc phù hợp. Nếu thiếu hoặc không xác định được một ứng viên bắt buộc, quá trình tạo bean thất bại.

**Đăng ký class khác với có source Java:** một class có `@Component` chưa tự xuất hiện trong context nếu không được component scanning hoặc đăng ký bằng cách khác. Trong ví dụ runnable, ta gọi trực tiếp `context.register(AppConfig.class, CheckoutService.class)` để không lệ thuộc quét toàn bộ package.

## 5. Ví dụ hoàn chỉnh Java 17+ và Spring Core Container

Chương trình sau chứng minh bốn điều:

1. Spring có thể tạo `CheckoutService` **qua constructor duy nhất không có `@Autowired`**.
2. Container cung cấp đúng **hai bean bắt buộc** đã đăng ký.
3. Sau khởi tạo, service có thể chạy nghiệp vụ, và giữ **chính reference** mà context đang quản lý.
4. Nếu tạo object bằng Java thường với null dependency hoặc giá không hợp lệ, constructor **fail fast** ngay.

Dùng **JDK 17+**, Maven và **Spring Framework 6.2.19** qua module `spring-context`. Phiên bản ghim cho tính tái lập, không khẳng định mới nhất. Không cần Spring Boot hoặc HTTP server.

Cấu trúc thư mục:

~~~text
constructor-di-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── ConstructorInjectionDemo.java
~~~

### 5.1. File pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>constructor-di-demo</artifactId>
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
                    <mainClass>demo.ConstructorInjectionDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Maven sẽ tải `spring-context` và các dependency transitively liên quan. Muốn chạy `javac` thủ công phải đưa Spring JAR và dependency đầy đủ vào classpath; đừng chỉ đặt một file `spring-context.jar` rồi kỳ vọng compiler/runtime có tất cả modules.

### 5.2. File src/main/java/demo/ConstructorInjectionDemo.java

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

public final class ConstructorInjectionDemo {
    private ConstructorInjectionDemo() {}

    public static final class PricingPolicy {
        private final int unitPrice;

        public PricingPolicy(int unitPrice) {
            if (unitPrice <= 0) {
                throw new IllegalArgumentException("unitPrice must be positive");
            }
            this.unitPrice = unitPrice;
        }

        public int total(int quantity) {
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            return Math.multiplyExact(unitPrice, quantity);
        }
    }

    public static final class AuditTrail {
        private final List<String> events = new ArrayList<>();

        public void record(String event) {
            events.add(event);
        }

        public List<String> snapshot() {
            return List.copyOf(events);
        }
    }

    @Component("checkoutService")
    public static final class CheckoutService {
        private final PricingPolicy pricing;
        private final AuditTrail audit;

        // Constructor duy nhất: không bắt buộc @Autowired.
        public CheckoutService(PricingPolicy pricing, AuditTrail audit) {
            this.pricing = Objects.requireNonNull(
                    pricing, "pricing is required");
            this.audit = Objects.requireNonNull(
                    audit, "audit is required");
        }

        public PricingPolicy pricing() {
            return pricing;
        }

        public AuditTrail audit() {
            return audit;
        }

        public String checkout(String sku, int quantity) {
            if (!"BOOK".equals(sku)) {
                throw new IllegalArgumentException("unknown sku");
            }
            String receipt = sku + " x " + quantity
                    + " = " + pricing.total(quantity);
            audit.record(receipt);
            return receipt;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        public PricingPolicy pricingPolicy() {
            return new PricingPolicy(150);
        }

        @Bean
        public AuditTrail auditTrail() {
            return new AuditTrail();
        }
    }

    private static void runWithSpring() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            // Register trực tiếp, không cần component scan.
            context.register(AppConfig.class, CheckoutService.class);
            context.refresh();

            CheckoutService service =
                    context.getBean(CheckoutService.class);
            PricingPolicy pricing = context.getBean(PricingPolicy.class);
            AuditTrail audit = context.getBean(AuditTrail.class);

            System.out.println("registered=" +
                    context.containsBean("checkoutService"));
            System.out.println("pricingInjected=" +
                    (service.pricing() == pricing));
            System.out.println("auditInjected=" +
                    (service.audit() == audit));
            System.out.println("serviceSingleton=" +
                    (service == context.getBean(CheckoutService.class)));
            System.out.println("receipt=" + service.checkout("BOOK", 2));
            System.out.println("audit=" + audit.snapshot());
        }
    }

    private static void checkConstructorGuards() {
        try {
            new CheckoutService(null, new AuditTrail());
            System.out.println("nullGuard=UNEXPECTED");
        } catch (NullPointerException failure) {
            System.out.println("nullGuard=" + failure.getMessage());
        }

        try {
            new PricingPolicy(0);
            System.out.println("invalidPrice=UNEXPECTED");
        } catch (IllegalArgumentException failure) {
            System.out.println("invalidPrice=" + failure.getMessage());
        }
    }

    public static void main(String[] args) {
        runWithSpring();
        checkConstructorGuards();
    }
}
~~~

**Điểm Java/Spring đáng chú ý:**

- `CheckoutService` được đánh dấu `@Component("checkoutService")` và được đăng ký **trực tiếp** qua `context.register(..., CheckoutService.class)`. Không cần `@ComponentScan` hoặc `@Autowired`.
- `CheckoutService` có **một constructor công khai** nhận `PricingPolicy` và `AuditTrail`. Spring tìm hai bean đang đăng ký và cung cấp chúng khi tạo service.
- Hai dependency được cung cấp bằng hai factory methods `@Bean` thuộc `AppConfig`. Chúng chỉ phụ trách khởi tạo collaborators; service không tự tra bean factory.
- `Objects.requireNonNull(...)` bảo vệ constructor ngay cả khi một caller tạo service bằng `new` ngoài Spring và truyền null.
- `PricingPolicy(150)` hợp lệ; `PricingPolicy(0)` ném exception tại lúc khởi tạo, không tạo ra một chính sách giá sai.
- `Math.multiplyExact` giúp báo lỗi nếu phép nhân số nguyên bị overflow thay vì âm thầm trả số sai; `quantity <= 0` bị chặn.
- `AuditTrail.snapshot()` trả một bản copy chỉ đọc từ những event đã ghi. Ví dụ dùng list đơn giản vì gọi trên một thread, **không khẳng định bean mutable này đã thread-safe**.
- Context được đóng khi rời `try-with-resources`. Bài không dựa vào thứ tự tạo các bean không có cạnh phụ thuộc hoặc vào tên class do Spring sinh ra.

### 5.3. Chạy bằng Maven

Từ thư mục `constructor-di-demo`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

**Đúng 8 dòng stdout do chương trình Java tự in trong happy path + kiểm tra constructor:**

~~~text
registered=true
pricingInjected=true
auditInjected=true
serviceSingleton=true
receipt=BOOK x 2 = 300
audit=[BOOK x 2 = 300]
nullGuard=pricing is required
invalidPrice=unitPrice must be positive
~~~

Lệnh in phiên bản JDK/Maven và log do Maven/Spring (nếu có) không nằm trong **tám dòng stdout của chương trình**. Các dòng `nullGuard=...` và `invalidPrice=...` không do Spring tự ném: chúng minh họa **constructor validation của code Java** ngay cả khi không có container.

### 5.4. Tự biên dịch bằng javac và chạy java

Vì code import Spring, `javac` cần compile classpath và `java` cần runtime classpath chứa Spring dependencies. Từ thư mục dự án:

~~~bash
mvn -q dependency:build-classpath -Dmdep.outputFile=classpath.txt
~~~

Linux/macOS (bash hoặc zsh):

~~~bash
mkdir -p out
javac --release 17 -encoding UTF-8 \
  -cp "$(cat classpath.txt)" \
  -d out src/main/java/demo/ConstructorInjectionDemo.java
java -cp "out:$(cat classpath.txt)" demo.ConstructorInjectionDemo
~~~

Windows PowerShell:

~~~powershell
$deps = (Get-Content -Raw classpath.txt).Trim()
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -encoding UTF-8 -cp "$deps" -d out src/main/java/demo/ConstructorInjectionDemo.java
java -cp "out;$deps" demo.ConstructorInjectionDemo
~~~

Dấu ngăn classpath là `:` trên Unix, `;` trên Windows. Hai cách chạy gọi cùng `main` với cùng tám dòng stdout ứng dụng. Nếu compiler báo thiếu package `org.springframework...`, hãy kiểm tra Maven/classpath; lúc đó chương trình chưa tới bước DI của Spring.

## 6. Dựa vào đâu để khẳng định constructor injection đã đúng?

| Output | Bằng chứng | Không chứng minh điều gì? |
| --- | --- | --- |
| `registered=true` | Bean được Spring biết theo tên `checkoutService` | Không chứng minh mọi đường nghiệp vụ đúng |
| `pricingInjected=true` | Service giữ đúng bean `PricingPolicy` context trả về | Không đảm bảo mọi giá tính đều hợp lệ |
| `auditInjected=true` | Service giữ đúng bean `AuditTrail` trong context | Không đảm bảo AuditTrail thread-safe |
| `serviceSingleton=true` | Service có một instance trên mỗi bean definition của context này | Không có nghĩa singleton toàn JVM |
| `receipt=BOOK x 2 = 300` | Price 150 nhân quantity 2, hợp đồng checkout hoạt động | Không phải kiểm thử toàn bộ sản phẩm/SKU |
| `audit=[BOOK x 2 = 300]` | Audit đã được ghi sau lời gọi checkout thành công | Không bảo đảm lưu trữ bền vững |
| `nullGuard=pricing is required` | Constructor không chấp nhận collaborator null | Không xác nhận hành vi Spring khi thiếu bean |
| `invalidPrice=unitPrice must be positive` | Constructor PricingPolicy reject cấu hình giá 0 | Không có bean invalid object nào được tạo từ ca test này |

Ở cấu hình minh họa, `AppConfig` có đúng một bean `PricingPolicy` và một bean `AuditTrail`. Vì vậy injection theo kiểu là rõ ràng. Trong project thực, nếu có nhiều beans cùng type thì cần xác định candidate bằng qualifier/primary hoặc sửa cấu hình, thay vì đoán rằng Spring sẽ chọn theo thứ tự khai báo.

**Đọc hai kiểm chứng khác nhau:** object đã tạo **không null** nhờ constructor guards là một yêu cầu; object là **đúng bean mong muốn** lại là yêu cầu khác. Identity checks trong ví dụ cho thấy wiring của hai collaborator singleton cùng context, chứ không phải quy tắc cho mọi scope.

## 7. Fail fast khi dependency thiếu và khi cấu hình không hợp lệ

### 7.1. Thiếu bean bắt buộc

Nếu `CheckoutService` được đăng ký nhưng context không có bean `AuditTrail` tương thích, Spring **không thể hoàn tất constructor** của service. Với singleton không lazy (mặc định), vấn đề thường bị phát hiện trong `context.refresh()`; chuỗi lỗi thường xuất hiện `UnsatisfiedDependencyException` hoặc nguyên nhân `NoSuchBeanDefinitionException`.

Ví dụ negative test **minh họa**, không phải file Java độc lập:

~~~java
try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext()) {
    context.register(CheckoutService.class); // Thiếu PricingPolicy và AuditTrail
    context.refresh(); // Dự kiến thất bại khi tạo bean bắt buộc.
}
~~~

Nếu có `@Lazy` hoặc scope khác, lỗi có thể bị trì hoãn cho tới lúc Spring thật sự tạo bean. **Đừng kết luận context không báo lỗi tức là mọi dependency ở mọi scope đều đã kiểm tra thành công.**

**Không dùng `@Autowired(required=false)` hoặc `Optional<T>` chỉ để che thiếu dependency bắt buộc.** Một service không có chính sách giá không thể xử lý đúng; khởi động với giá trị null/default giả tạo là thiết kế không hợp lệ.

### 7.2. Dependency tồn tại nhưng có trạng thái sai

Giả sử factory khai báo:

~~~java
@Bean
PricingPolicy pricingPolicy() {
    return new PricingPolicy(0);
}
~~~

Snippet minh họa **thay cho** factory trong `AppConfig` của demo, không dùng song song hai method cùng tên. Context có bean definition, nhưng khi factory chạy, `new PricingPolicy(0)` ném `IllegalArgumentException`. Spring có thể bọc nó trong `BeanCreationException` của bean đang tạo.

**Hai kiểu fail-fast khác nhau:**

- **Missing dependency:** container không resolve đủ bean để gọi constructor service.
- **Invalid dependency:** có bean/factory được chọn, nhưng chính constructor/factory từ chối giá trị không hợp lệ.

Cả hai đều tốt hơn việc đưa một object nửa hợp lệ ra phục vụ request. Tuy vậy, fail fast còn phụ thuộc *khi nào* bean được tạo (eager/lazy/prototype).

### 7.3. Hai dependency cùng kiểu nhưng không chọn được

Nếu có `primaryPricingPolicy` và `secondaryPricingPolicy` đều kiểu `PricingPolicy`, tiêm đơn trị có thể bị mơ hồ:

~~~java
public CheckoutService(
        @Qualifier("primaryPricingPolicy") PricingPolicy pricing,
        AuditTrail audit) {
    this.pricing = Objects.requireNonNull(pricing);
    this.audit = Objects.requireNonNull(audit);
}
~~~

Snippet **chỉ minh họa**; cần import `org.springframework.beans.factory.annotation.Qualifier`, có bean tương ứng và đặt đoạn trong class phù hợp. Spring hỗ trợ đặt qualifier tại tham số constructor; nếu số lượng ứng viên nhiều, nêu rõ selection policy thay vì dựa vào tên parameter vô tình.

`@Primary` phù hợp khi muốn một bean mặc định; collection/provider phù hợp khi nghiệp vụ cần nhiều instance, không phải khi chỉ một chính sách được phép.

## 8. Vòng phụ thuộc constructor: vì sao không thể tạo object nào trước?

Ví dụ:

~~~java
final class Alpha {
    Alpha(Beta beta) {}
}
final class Beta {
    Beta(Alpha alpha) {}
}
~~~

Snippet là hai class phụ thuộc nhau, chưa có Spring registration. Nếu Spring được yêu cầu tạo cả hai như managed beans qua constructors bắt buộc, graph có chu trình:

~~~mermaid
flowchart LR
    A["Alpha"] --> B["Beta"]
    B --> A
~~~

Để tạo Alpha cần Beta, để tạo Beta cần Alpha, nhưng chưa có cái nào tạo xong. Spring thông thường báo lỗi liên quan `BeanCurrentlyInCreationException` hoặc exception wrapper khác.

**Đừng sửa bằng cách đổi mọi constructor thành field injection cho Spring “lách vòng”**, vì kết quả có thể là object khởi tạo dở dang và các quan hệ sở hữu khó kiểm soát. Nên tái phân chia trách nhiệm, tách dịch vụ chung, dùng sự kiện hoặc một abstraction làm cho graph không còn chu trình bắt buộc khi khởi tạo.

Một số cơ chế setter-based cycle có thể được Spring giải quyết trong những điều kiện nhất định; điều đó **không phải bảo đảm an toàn cho mọi vòng** và cũng không làm constructor cycle trở nên hợp lệ.

## 9. Giá trị cấu hình cũng phải được kiểm tra tại ranh giới

Constructor injection không chỉ áp dụng với những collaborator là interface. Một bean cấu hình như `PricingPolicy` còn cần **dữ liệu hợp lệ**.

Ví dụ:

~~~java
public PricingPolicy(int unitPrice) {
    if (unitPrice <= 0) {
        throw new IllegalArgumentException("unitPrice must be positive");
    }
    this.unitPrice = unitPrice;
}
~~~

Dù `unitPrice` đến từ literal, `Environment.getRequiredProperty(...)`, `@Value` hay cơ chế cấu hình khác, constructor vẫn phải bảo vệ invariant khi có thể. Container có thể resolve/convert một value nhưng **không tự hiểu mọi quy tắc nghiệp vụ**, ví dụ giá phải dương hoặc số lần thử tối đa.

Các bước thiết kế hợp lý:

1. **Khai báo invariant:** giá đơn vị dương, mã tiền tệ hợp lệ, dependency không null.
2. **Resolve dữ liệu:** lấy property/bean đúng nguồn, đúng tên, đúng kiểu.
3. **Validate tại constructor/factory:** không lưu trạng thái không hợp lệ.
4. **Test negative paths:** thiếu bean, null được truyền thủ công, sai value, ambiguity.
5. **Cho phép lỗi bootstrap có ý nghĩa:** không catch xong dùng fallback sai nghiệp vụ để che cấu hình.

Nếu dependency cần một thay đổi cấu hình hợp lệ trong suốt vòng đời, chọn thiết kế immutable value object hoặc chính sách cập nhật có kiểm soát. Constructor validation chỉ có tác dụng **tại thời điểm khởi tạo**; nó không tự ngăn một collaborator mutable bị thay đổi sai về sau.

## 10. Constructor injection với interface và unit testing

Một ưu điểm mạnh của constructor DI là test không cần Spring. Ví dụ hợp đồng:

~~~java
interface PriceLookup {
    int unitPrice(String sku);
}

final class OrderCalculator {
    private final PriceLookup lookup;

    OrderCalculator(PriceLookup lookup) {
        this.lookup = Objects.requireNonNull(lookup);
    }

    int total(String sku, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity");
        }
        return Math.multiplyExact(lookup.unitPrice(sku), quantity);
    }
}
~~~

Các class trên là snippet Java có thể đặt trong một file/test thích hợp; **không phải chương trình độc lập với `main`**. Trong unit test, inject một fake lambda:

~~~java
PriceLookup fake = sku -> 25;
OrderCalculator calculator = new OrderCalculator(fake);
System.out.println(calculator.total("ANY", 3)); // 75
~~~

Có thể kiểm tra cả null guard, input, overflow và behavior không cần tải Spring framework. Đó là khác biệt lớn so với service tự gọi container bằng **service locator pattern**:

~~~java
// Phản ví dụ minh họa:
PricingPolicy pricing = applicationContext.getBean(PricingPolicy.class);
~~~

Nếu mọi method nghiệp vụ đều tự lấy bean theo cách đó, danh sách dependency bị ẩn khỏi constructor, test cần mock container hoặc setup phức tạp, và runtime error có thể xuất hiện muộn.

**Không bắt buộc mọi collaborator phải là interface.** Khi chỉ có một implementation ổn định hoặc một value object, constructor nhận concrete class vẫn hợp lệ. Interface hữu ích khi có nhiều strategy, cần tách domain/infrastructure hoặc fake trong unit test.

## 11. Khi constructor có nhiều dependency

Constructor injection làm những dependency bắt buộc lộ rõ trong API. Nếu chữ ký chứa 12–15 collaborator, hãy kiểm tra **những trách nhiệm và lý do thay đổi** của class, thay vì coi một số lượng tham số cụ thể là lỗi. Một orchestrator có nhiều bước liên quan vẫn có thể hợp lý.

Đừng rút ngắn chữ ký bằng cách giấu collaborator trong `ApplicationContext`, field injection hoặc một `Dependencies` bag khổng lồ: độ phức tạp thực tế vẫn tồn tại. Lập nhóm method theo phần nghiệp vụ chúng sử dụng, rồi cân nhắc tách các trách nhiệm ít liên quan; chỉ gom thành value object khi những giá trị có cùng ý nghĩa và invariant. Việc tách phải được kiểm chứng để không làm đổi thứ tự xử lý và side effects.

## 12. Scope, lazy và trạng thái hợp lệ

| Trường hợp | Điều cần nhớ |
| --- | --- |
| Singleton không lazy (mặc định) | Thường được khởi tạo trong `ApplicationContext.refresh()`; dependency missing thường fail sớm |
| Singleton `@Lazy` | Có thể được tạo khi yêu cầu lần đầu; lỗi constructor có thể xuất hiện muộn |
| Prototype | Mỗi yêu cầu tạo bean thường chạy constructor mới và kiểm tra invariant riêng |
| Prototype inject trực tiếp vào singleton | Dependency thường được giải quyết một lần khi singleton được khởi tạo, không tự tạo prototype mới sau mỗi method call |
| Scoped proxy/provider | Có thể ảnh hưởng thời điểm/identity object được đưa vào; không đồng nghĩa constructor guard tự kiểm tra target cuối ở mọi request |
| Object được `new` thủ công | Spring không can thiệp, nhưng chính constructor guards Java vẫn hoạt động |
| Context đã đóng | Không tạo/lấy thêm managed dependencies từ context đã shutdown |

**Không đồng nhất “Spring đã inject non-null reference” với “mọi thao tác remote/database chắc chắn thành công”.** Một collaborator có thể không null nhưng kết nối ngoại vi lỗi sau đó. Constructor kiểm tra những invariant **khả thi và ổn định** tại thời điểm tạo; lỗi nghiệp vụ/ngoại vi vẫn cần được xử lý ở đúng lớp trách nhiệm.

Một bean singleton có state mutable phục vụ nhiều luồng phải được thiết kế thread-safe; `final` trên field chỉ bảo đảm reference không bị gán lại, không làm object phía sau tự thread-safe.

## 13. Những lỗi thường gặp và cách sửa

| Triệu chứng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| `UnsatisfiedDependencyException` lúc startup | Constructor cần một bean không có candidate phù hợp | Kiểm tra `@Bean`, component registration/scan, type và qualifier |
| `NoSuchBeanDefinitionException` | Spring không có bean theo tên/kiểu bắt buộc | Đăng ký collaborator, kiểm tra cấu hình được nạp |
| `NoUniqueBeanDefinitionException` | Có nhiều bean ứng viên cho một tham số đơn trị | Dùng `@Qualifier`, `@Primary` hoặc tách lựa chọn nghiệp vụ |
| `BeanCurrentlyInCreationException` | Dependency cycle ở constructors/factories | Vẽ graph, tách trách nhiệm; không che chu trình bằng field injection |
| Bean service được `new` ngoài Spring rồi dependency null | Caller bypass container và không cung cấp hợp đồng | Constructor `requireNonNull`, manual wiring đúng hoặc lấy bean từ context |
| Bean factory tạo object với giá 0/âm | Cấu hình sai giá trị cần thiết | Validate ở `PricingPolicy` constructor; fail fast |
| `@Autowired` ở nhiều constructors required | Spring không rõ constructor bắt buộc | Giữ một constructor rõ, hoặc khai báo theo quy tắc lựa chọn được hỗ trợ |
| Quá nhiều constructor arguments | Service có thể chứa quá nhiều trách nhiệm | Refactor ranh giới lớp, không chỉ ẩn arguments bằng setters |
| Muốn `final` nên inject field trực tiếp | Field injection không phù hợp với mô hình final constructor assignment | Ưu tiên constructor injection |
| Nhiều threads sửa cùng singleton AuditTrail | Mutable list không tự thread-safe | Synchronization hoặc collection phù hợp/thiết kế stateless |
| Unit test phải khởi động cả Spring để tạo một service | Service phụ thuộc container/service locator | Truyền collaborator qua constructor; test bằng fake |
| Context khởi động thành công nhưng service vẫn lỗi ở request khác | Chỉ test một input; remote calls/logic có thể fail | Tách wiring test và nghiệp vụ/edge cases |
| Lỗi chỉ xuất hiện lần đầu `getBean` | Bean lazy/prototype trì hoãn instantiation | Kiểm tra thời điểm tạo, không chỉ nhìn trạng thái context |
| Thêm constructor mặc định tạo service thiếu dependency | Cho phép object tồn tại trong trạng thái sai | Bỏ constructor vô nghĩa, bắt buộc dependencies qua constructor |

**Quy trình debug:** xác định bean đang tạo, xem danh sách tham số constructor, kiểm tra các candidates cùng type, theo chuỗi `Caused by` đến lỗi gốc. Nếu đó là `IllegalArgumentException` từ constructor, xem cấu hình giá trị; nếu là missing/ambiguous bean, sửa registration/qualifier. Đừng catch `BeansException` ở bootstrap rồi cho ứng dụng hoạt động với null dependencies.

## 14. Kiểm thử ở hai cấp độ

**Unit test (không Spring):** tạo `PricingPolicy`, `AuditTrail` và `CheckoutService` bằng Java thuần; kiểm tra:

- `new CheckoutService(null, audit)` bị từ chối ngay.
- `new CheckoutService(pricing, null)` bị từ chối ngay.
- `new PricingPolicy(0)` bị từ chối.
- `checkout("BOOK", 2)` có receipt đúng và audit thêm đúng một event.
- `checkout("BOOK", 0)` bị từ chối; audit không được ghi.
- `checkout("UNKNOWN", 1)` bị từ chối theo hợp đồng SKU.

**Spring wiring test:** tạo context với `AppConfig` và `CheckoutService.class`, kiểm tra:

~~~java
try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext()) {
    context.register(AppConfig.class, CheckoutService.class);
    context.refresh();

    CheckoutService service = context.getBean(CheckoutService.class);
    PricingPolicy pricing = context.getBean(PricingPolicy.class);
    if (service.pricing() != pricing) {
        throw new IllegalStateException("unexpected pricing bean");
    }
}
~~~

Đây là **snippet trích từ ngữ cảnh class demo**, không phải một file độc lập. Nó chứng minh *wiring*, không tự chứng minh mọi ca nghiệp vụ.

**Negative Spring integration test:** dùng cấu hình cố ý thiếu `AuditTrail` hoặc có hai bean `PricingPolicy` cùng type không chọn rõ. Kiểm tra loại lỗi/nguyên nhân, không khóa test theo toàn bộ nguyên văn stack trace vì thông điệp có thể thay đổi theo phiên bản Spring/JDK.

**Không dùng Java `assert` mặc định làm kiểm tra bắt buộc**, vì assertions có thể bị tắt nếu JVM chạy không có `-ea`. Dùng assertion của test framework hoặc biểu thức kiểm tra rồi ném exception.

## 15. Bài tập tự luyện có đáp án

**Bài 1 — Khởi tạo hợp lệ.** Service có constructor `S(A a, B b)`, không constructor khác. Nếu A tồn tại nhưng B không được đăng ký, Spring có thể trả một `S` với B=null được không?

**Đáp án:** Với dependency bắt buộc theo cơ chế constructor resolution bình thường, Spring phải giải quyết B; nếu thiếu, việc tạo bean thất bại, không nên âm thầm tạo service có B=null.

**Bài 2 — @Autowired.** Một bean Spring chỉ có một constructor công khai nhận hai collaborators. Có cần `@Autowired` không?

**Đáp án:** Không bắt buộc từ Spring Framework 4.3; container có thể chọn constructor duy nhất. Cần bảo đảm bean được đăng ký và dependencies resolve được.

**Bài 3 — Object bên ngoài Spring.** Ai đó gọi `new CheckoutService(null, new AuditTrail())`. Spring có chặn trước không?

**Đáp án:** Spring không tham gia lời gọi `new` này. Nhưng chính `Objects.requireNonNull(pricing)` trong constructor ném exception; invariant của class không phụ thuộc vào container.

**Bài 4 — final.** `private final AuditTrail audit` có làm danh sách nội bộ AuditTrail bất biến và thread-safe không?

**Đáp án:** Không. Nó chỉ giữ reference không đổi; state của `AuditTrail` vẫn có thể mutable. Cần thiết kế bảo vệ state riêng.

**Bài 5 — Config sai.** `PricingPolicy(0)` bị từ chối khi nào?

**Đáp án:** Ngay trong constructor PricingPolicy. Nếu Spring tạo bean từ factory trả `new PricingPolicy(0)`, lỗi thường được bọc trong bean creation exception.

**Bài 6 — Vòng phụ thuộc.** A constructor cần B, B constructor cần A. Chỉ cần đổi thứ tự định nghĩa bean để khởi động được không?

**Đáp án:** Không. Đây là vòng bắt buộc A→B→A, không có thứ tự tạo object đầy đủ nào giải quyết được; cần sửa thiết kế.

**Bài 7 — Nhiều candidates.** Context có hai PricingPolicy. Bản thân constructor một tham số `PricingPolicy pricing` có đủ để Spring biết chọn cái nào không?

**Đáp án:** Không nhất thiết; Spring còn xem qualifier, primary và các quy tắc phân giải khác. Khi thực sự mơ hồ, phải chỉ rõ lựa chọn hoặc cấu hình lại.

**Bài 8 — Trạng thái hợp lệ.** Constructor đã kiểm tra tất cả references khác null. Vậy mọi lời gọi checkout đều thành công?

**Đáp án:** Không. Method còn validate SKU, số lượng và có thể gặp overflow/ngoại lệ nghiệp vụ. Constructor valid state và method input validation là hai tầng khác nhau.

## 16. Checklist áp dụng

- [ ] Liệt kê dependency **bắt buộc** và đưa chúng vào constructor công khai.
- [ ] Tránh service tự `new` hoặc tự `getBean(...)` dependency ở chỗ không thuộc composition root.
- [ ] Giữ reference bắt buộc trong `private final` fields khi phù hợp.
- [ ] Dùng `Objects.requireNonNull`/validation rõ ràng cho invariant, không rely chỉ vào Spring.
- [ ] Không tạo constructor mặc định khiến object thiếu dependencies.
- [ ] Với một constructor, hiểu vì sao không bắt buộc `@Autowired`.
- [ ] Nếu nhiều constructors, nêu rõ constructor được chọn và semantics cần thiết.
- [ ] Kiểm tra class có được đăng ký thành bean không; annotation trong source không tự tạo bean.
- [ ] Phân giải candidate theo type/qualifier/primary thay vì dựa vào may rủi.
- [ ] Tránh dependency cycles và constructor với quá nhiều trách nhiệm.
- [ ] Phân biệt reference `final` với object mutable và thread safety.
- [ ] Phân biệt lỗi missing bean với constructor reject giá trị cấu hình không hợp lệ.
- [ ] Kiểm tra lazy/prototype nếu lỗi không xuất hiện ngay startup.
- [ ] Chạy Java 17+ và đối chiếu **đúng tám dòng stdout**.
- [ ] Với `javac`/`java`, sử dụng đầy đủ classpath Spring.
- [ ] Test riêng constructor invariants, business behavior và Spring wiring.

**Ghi nhớ:** Constructor injection không chỉ là cách “đưa bean A vào bean B”; đó còn là một **hợp đồng khởi tạo**. Khi dependency bắt buộc được khai báo trong constructor và kiểm tra hợp lệ ở cùng ranh giới, object không thoát ra dưới trạng thái thiếu collaborator thiết yếu. Spring Core Container lo **giải quyết và cung cấp các dependency đã đăng ký**, còn **thiết kế invariant và validation** vẫn do code Java bảo đảm.

### Tài liệu chính thức

- [Spring Framework 6.2 — Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html)
- [Spring Framework — Using @Autowired](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html)
- [Spring Framework — Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java.html)
- [AnnotationConfigApplicationContext API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/AnnotationConfigApplicationContext.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dependency-injection-003">Tiêm qua setter cho cấu hình có thể tùy chọn</a>

<details>
<summary>Xem chi tiết</summary>

# Tiêm qua setter cho cấu hình có thể tùy chọn trong Spring Core Container

## 1. Mục tiêu: dependency bắt buộc khác cấu hình tùy chọn

**Setter injection** là cách Spring cung cấp một giá trị hoặc cộng tác viên cho bean thông qua **phương thức setter** sau khi đối tượng đã được tạo. Kỹ thuật này đặc biệt hữu ích khi một dependency **không phải điều kiện bắt buộc** để đối tượng thực hiện nhiệm vụ chính, hoặc khi một thông số có **giá trị mặc định hợp lý** nhưng có thể thay đổi qua cấu hình.

Ví dụ: `ReportService` luôn cần `ReportRepository` để lấy dữ liệu báo cáo, nhưng có thể:

- Có hoặc không có `AuditSink` để ghi dấu vết xử lý.
- Dùng tiền tố hiển thị mặc định `REPORT`, hoặc thay bằng giá trị cấu hình như `CUSTOM`.

Thiết kế thích hợp là:

- **Constructor injection** cho `ReportRepository`: không có repository thì service không thể làm việc.
- **Setter injection** cho `AuditSink`: nếu không có audit, chức năng chính vẫn chạy.
- **Setter nhận property** cho tiền tố báo cáo: có giá trị mặc định hợp lệ; cấu hình chỉ ghi đè khi cần.

~~~text
ApplicationContext
     |
     +-- tạo ReportRepository bean
     |
     +-- gọi new ReportService(repository)  [bắt buộc]
     |         |
     |         +-- @Value setter: prefix = REPORT hoặc CUSTOM
     |         |
     |         +-- @Autowired(required=false) setter:
     |                    AuditSink bean có -> gọi setter
     |                    AuditSink bean thiếu -> không gọi setter
     |
     +-- trả ReportService bean đã cấu hình
                      |
                      +-- render(id) -> repository.find(id)
                                      -> audit nếu có
                                      -> chuỗi kết quả
~~~

**Điểm cốt lõi:** “tùy chọn” không có nghĩa là Spring sẽ luôn truyền `null`. Với `@Autowired(required = false)` đặt trên một method, nếu dependency không khả dụng, Spring có thể **không gọi method đó**. Vì vậy trạng thái mặc định đã thiết lập trong object sẽ được giữ nguyên.

## 2. Constructor injection và setter injection khác nhau ở đâu?

| Tiêu chí | Constructor injection | Setter injection |
| --- | --- | --- |
| Thời điểm cung cấp dependency | Khi tạo object | Sau khi object được tạo, trong quá trình cấu hình bean |
| Phù hợp nhất | Dependency bắt buộc, invariant để object hợp lệ | Dependency/tùy chọn cấu hình có default rõ |
| Có thể dùng trường `final`? | Thường có, vì gán trong constructor | Không thể gán lại `final` qua setter bình thường |
| Nếu thiếu dependency | Thường thất bại ở bước tạo bean | Tùy cấu hình: lỗi, bỏ qua hoặc dùng default |
| Mức độ linh hoạt sau khi tạo | Ít đổi reference hơn | Có thể đổi cấu hình nhưng phải kiểm soát lifecycle/thread safety |
| Unit test Java thuần | Truyền dependency vào constructor | Tạo object rồi gọi setter rõ ràng |
| Nguy cơ | Constructor quá nhiều tham số có thể báo hiệu class ôm nhiều trách nhiệm | Bean có thể ở trạng thái chưa cấu hình hoàn chỉnh nếu dùng sai |

Ví dụ Java thuần:

~~~java
public final class ReportService {
    private final ReportRepository repository;  // bắt buộc
    private String prefix = "REPORT";            // giá trị mặc định

    public ReportService(ReportRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    public void setPrefix(String prefix) {        // tùy chọn
        this.prefix = prefix;
    }
}
~~~

Đây là **đoạn minh họa**, thiếu định nghĩa `ReportRepository` và phần xử lý nên chưa phải file chạy độc lập. Khối trên cho thấy Java đã hỗ trợ setter injection theo nghĩa “người bên ngoài gọi setter để cấu hình”; **Spring không phát minh setter**, mà giúp gọi nó bằng metadata và dependency resolution.

**Quy tắc thiết kế:** đừng chuyển mọi tham số constructor sang setter chỉ để giảm số tham số. Một class mà không có các dependency quan trọng vẫn bị coi là “khởi tạo thành công” có thể ném `NullPointerException` rất muộn khi xử lý nghiệp vụ. Đối với các dependency bắt buộc, constructor injection thường minh bạch và an toàn hơn.

## 3. Setter là một điểm cấu hình, không bắt buộc có Spring

Một setter truyền thống có chữ ký:

~~~java
public void setAuditSink(AuditSink sink) {
    this.auditSink = sink;
}
~~~

Nếu mã khởi động tự làm:

~~~java
ReportService service = new ReportService(repository);
service.setAuditSink(new ConsoleAuditSink());
~~~

thì đó là **setter injection thủ công**. Các kiểu trong hai khối này được giả định đã định nghĩa; chương trình đầy đủ nằm ở mục 6.

Cấu hình Spring có thể chọn gọi setter theo ba cách phổ biến:

1. **Explicit setter call trong `@Bean` factory method**: khi tạo bean, cấu hình Java tự gọi `set...`.
2. **Annotation `@Autowired` trên setter**: Spring cố gắng tìm bean phù hợp và gọi setter qua `AutowiredAnnotationBeanPostProcessor`.
3. **Property injection từ XML**: `<property name="auditSink" ref="auditSink"/>` khiến Spring áp dụng JavaBeans property setter.

Đối với giá trị scalar từ environment, một method nhận `@Value("${app.report.prefix:REPORT}")` có thể nhận chuỗi property đã được Spring resolve rồi lưu vào trường. Cú pháp `${key:default}` có phần sau dấu `:` làm giá trị mặc định nếu không có key thích hợp.

Không nên coi mọi cơ chế này là giống hệt nhau. `@Bean` gọi setter trực tiếp do **mã cấu hình** quyết định; `@Autowired` có semantics về bean candidate và `required`; `@Value` phụ thuộc property resolution, còn XML `<property>` là metadata của bean definition.

## 4. `@Autowired(required = false)`: setter có thể được bỏ qua

### 4.1. Cú pháp và semantics

~~~java
private AuditSink auditSink;  // mặc định null: không có audit

@Autowired(required = false)
public void setAuditSink(AuditSink auditSink) {
    this.auditSink = auditSink;
}
~~~

Đây là trích đoạn từ chương trình chạy mục 6. Trong một `ApplicationContext` có annotation processing:

- Nếu có một bean `AuditSink` phù hợp, Spring gọi setter và đặt reference.
- Nếu **không có** bean phù hợp, **không gọi setter**, trường giữ giá trị mặc định đã khai báo.
- Với một method có **nhiều tham số**, `required = false` áp dụng cho cả method: chỉ cần một dependency không khả dụng, method có thể bị bỏ qua hoàn toàn.
- Nếu có **nhiều bean `AuditSink` cùng phù hợp nhưng không chọn được một**, `required = false` không phải cơ chế để giải quyết mơ hồ: cần `@Qualifier`, `@Primary` hoặc cấu hình hợp lý.
- Nếu có bean nhưng quá trình tạo dependency thất bại, đừng coi `required = false` là cơ chế nuốt mọi exception của constructor/factory hay lỗi cấu hình.
- Nếu setter được gọi mà **body setter tự ném exception**, bean creation vẫn có thể thất bại.

Nói cách khác, thuộc tính `required = false` có nghĩa **điểm injection không bắt buộc phải được đáp ứng**, chứ không phải “bất kể có lỗi gì cũng bỏ qua”.

### 4.2. Thiết kế hành vi khi không có dependency

Nếu audit thật sự là tùy chọn, mã dùng phải xử lý trường hợp vắng mặt rõ ràng:

~~~java
if (auditSink != null) {
    auditSink.record(reportId);
}
~~~

Một cách khác là **Null Object pattern**: khởi tạo reference bằng một implementation không làm gì, rồi cho setter ghi đè khi có bean thật:

~~~java
private AuditSink auditSink = reportId -> { };

@Autowired(required = false)
public void setAuditSink(AuditSink auditSink) {
    this.auditSink = java.util.Objects.requireNonNull(auditSink);
}
~~~

Nếu không có bean, setter bị bỏ qua và audit implementation mặc định im lặng. Cách này tránh `null` check ở từng lời gọi, nhưng phải đặt tên/semantics rõ ràng để không vô tình che lỗi thiếu audit khi audit là yêu cầu bắt buộc.

Trong bài chạy ở mục 6, ta dùng `null` + kiểm tra rõ ràng để dễ quan sát `AUDIT_ENABLED false/true`.

### 4.3. `Optional<T>` và `ObjectProvider<T>` là những lựa chọn khác

Khi dependency chỉ để thể hiện “có hoặc không”, bạn cũng có thể dùng:

~~~java
public ReportService(ReportRepository repository,
                     java.util.Optional<AuditSink> optionalAudit) {
    this.repository = repository;
    this.auditSink = optionalAudit.orElse(null);
}
~~~

Đây là đoạn **minh họa constructor** cần các field/interface tương ứng. Spring hỗ trợ xử lý injection của `Optional<T>`; cách này cho phép dependency tùy chọn được biểu lộ ngay ở constructor, không bắt buộc phải dùng setter.

Nếu collaborator phải được lấy **theo nhu cầu ở thời điểm chạy**, có thể cân nhắc `ObjectProvider<AuditSink>`, chẳng hạn `getIfAvailable()`. Tuy nhiên:

- Đừng dùng provider chỉ để thay cho một tham chiếu optional đơn giản.
- Đọc contract của `getIfAvailable()`, bao gồm tình huống nhiều candidate gây mơ hồ.
- Đừng trộn tùy chọn cấu hình với lựa chọn implementation thay đổi theo từng request.

Với configuration property scalar, `@Value` có default hoặc bean `@ConfigurationProperties` của Spring Boot (nếu dùng Boot) thường phù hợp hơn một `Optional<Bean>` chỉ để chứa chuỗi.

## 5. `@Value` trên setter: default và cấu hình bên ngoài

### 5.1. Giá trị mặc định bằng placeholder

~~~java
private String prefix = "REPORT";

@Value("${app.report.prefix:REPORT}")
public void setPrefix(String prefix) {
    this.prefix = prefix;
}
~~~

Trong trường hợp property `app.report.prefix` không tồn tại, Spring dùng default `REPORT`. Nếu property có giá trị `CUSTOM`, setter nhận `CUSTOM`.

Phân biệt:

- **Java default field:** `private String prefix = "REPORT";` có sẵn khi constructor kết thúc.
- **Property placeholder default:** `${app.report.prefix:REPORT}` là quy tắc resolve của Spring khi key chưa được cung cấp.
- **Setter body validation:** nếu cần, method phải kiểm tra prefix không null/blank để tránh nhận cấu hình vô nghĩa.
- **@Value không đồng nghĩa @Autowired(required=false):** `@Value` cung cấp giá trị đã resolve; optional bean injection có semantics riêng.
- **Một property rỗng hoặc chứa toàn dấu cách** có thể vẫn được resolve thành chuỗi rỗng/trắng, không nhất thiết kích hoạt giá trị mặc định. Vì vậy nên validate nếu dữ liệu cấu hình cần không rỗng.

Trong demo, Spring cung cấp `MapPropertySource` cho context thứ hai, tạo một key cấu hình rõ ràng mà không cần file `application.properties` hay phụ thuộc Spring Boot.

### 5.2. Có thể dùng factory method thay `@Value`

Một hướng khác là truyền `Environment` vào `@Bean` method rồi **gọi setter có chủ đích**:

~~~java
@Bean
ReportService reportService(ReportRepository repository,
                            Environment env) {
    ReportService service = new ReportService(repository);
    service.setPrefix(env.getProperty("app.report.prefix", "REPORT"));
    return service;
}
~~~

Đây là **đoạn minh họa alternative**, không phải một factory method bổ sung trong chương trình chính. Nếu dùng trong project thật, cần import `org.springframework.core.env.Environment` và các class liên quan.

Cách này rõ đường cấu hình trong một nơi, dễ kiểm soát giá trị mặc định và validation. Nó cũng cho phép giữ class nghiệp vụ **không import Spring annotation**. Đổi lại, configuration cần biết setter và cung cấp giá trị.

Khi một class có nhiều property, hãy tránh hàng chục setter rời rạc nếu có thể gom thành một **immutable configuration object** được validate và inject qua constructor.

## 6. Chương trình Java 17 / Spring Framework 6.2 chạy đầy đủ

Ví dụ cố ý tạo hai context độc lập:

- **Context mặc định:** có `ReportRepository` và `ReportService` nhưng **không có `AuditSink`** và không cung cấp property `app.report.prefix`. Service vẫn chạy, setter audit không được gọi; prefix mặc định là `REPORT`.
- **Context tùy chỉnh:** đăng ký thêm `AuditSink` và thiết lập property `app.report.prefix=CUSTOM` **trước `refresh()`**. Setter audit được Spring gọi; setter prefix nhận `CUSTOM`.

Không có HTTP, database hoặc network I/O. Audit chỉ in dòng console. Ví dụ dùng `spring-context`, JDK 17 và Maven; không cần Spring Boot.

### 6.1. Cấu trúc và Maven

~~~text
optional-setter-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── OptionalSetterDemo.java
~~~

Nội dung `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>optional-setter-demo</artifactId>
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
                    <mainClass>demo.OptionalSetterDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 6.2. File Java hoàn chỉnh

Lưu `src/main/java/demo/OptionalSetterDemo.java`:

~~~java
package demo;

import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;

public final class OptionalSetterDemo {
    private OptionalSetterDemo() {}

    public interface ReportRepository {
        String find(String id);
    }

    public static final class DemoRepository implements ReportRepository {
        @Override
        public String find(String id) {
            return "ITEM(" + id + ")";
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

    public static final class ReportService {
        private final ReportRepository repository;
        private AuditSink auditSink;       // tùy chọn, mặc định vắng mặt
        private String prefix = "REPORT";  // mặc định luôn hợp lệ

        public ReportService(ReportRepository repository) {
            this.repository = Objects.requireNonNull(
                    repository, "repository");
        }

        @Autowired(required = false)
        public void setAuditSink(AuditSink auditSink) {
            this.auditSink = Objects.requireNonNull(
                    auditSink, "auditSink");
        }

        @Value("${app.report.prefix:REPORT}")
        public void setPrefix(String prefix) {
            if (prefix == null || prefix.isBlank()) {
                throw new IllegalArgumentException(
                        "report prefix must not be blank");
            }
            this.prefix = prefix;
        }

        public String render(String id) {
            String value = repository.find(id);
            if (auditSink != null) {
                auditSink.record(id);
            }
            return prefix + ":" + value;
        }

        public boolean hasAuditSink() {
            return auditSink != null;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class BaseConfig {
        @Bean
        public ReportRepository reportRepository() {
            return new DemoRepository();
        }

        @Bean
        public ReportService reportService(ReportRepository repository) {
            return new ReportService(repository);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(BaseConfig.class)
    public static class WithAuditConfig {
        @Bean
        public AuditSink auditSink() {
            return new ConsoleAuditSink();
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(BaseConfig.class)) {
            ReportService service = context.getBean(ReportService.class);
            System.out.println("-- default --");
            System.out.println("TEXT " + service.render("A1"));
            System.out.println("AUDIT_ENABLED " + service.hasAuditSink());
        }

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("demo",
                            Map.of("app.report.prefix", "CUSTOM")));

            context.register(WithAuditConfig.class);
            context.refresh();

            ReportService service = context.getBean(ReportService.class);
            System.out.println("-- customized --");
            System.out.println("TEXT " + service.render("B2"));
            System.out.println("AUDIT_ENABLED " + service.hasAuditSink());
        }
    }
}
~~~

Dấu `@Autowired(required = false)` nằm **trên method setter**, không phải constructor. `@Value` được xử lý trên một setter khác. Cả hai bean `ReportService` đều được tạo bởi `@Bean` factory method, nên Spring vẫn áp dụng bean post-processors để xử lý các annotation của bean được quản lý.

### 6.3. Lệnh và stdout

Từ thư mục `optional-setter-demo`:

~~~bash
mvn -version
mvn -q compile exec:java
~~~

Nếu là lần đầu dùng Maven, các dependency có thể cần tải từ repository. **Những dòng do chương trình in ra stdout theo đúng thứ tự:**

~~~text
-- default --
TEXT REPORT:ITEM(A1)
AUDIT_ENABLED false
-- customized --
AUDIT B2
TEXT CUSTOM:ITEM(B2)
AUDIT_ENABLED true
~~~

`mvn -version` cùng Maven/plugin có thể in phiên bản và log riêng. Khối stdout mô tả **dòng được in từ logic `OptionalSetterDemo`**, không khẳng định terminal luôn chỉ có bảy dòng này.

### 6.4. Theo dõi cơ chế thật

1. `AnnotationConfigApplicationContext(BaseConfig.class)` tạo và refresh context đầu. Spring tạo `DemoRepository` và `ReportService`.
2. Constructor `ReportService(ReportRepository)` nhận repository bắt buộc, không tự tìm bean.
3. Không có bean `AuditSink` trong context đầu, nên injection method `@Autowired(required=false)` **bị bỏ qua**, trường `auditSink` vẫn `null`.
4. Không có key `app.report.prefix`, nên `@Value("${app.report.prefix:REPORT}")` dùng default `REPORT`; setter prefix được gọi.
5. `render("A1")` lấy `ITEM(A1)` từ repository, không gọi audit và trả `REPORT:ITEM(A1)`.
6. Context thứ hai thêm `MapPropertySource` với giá trị `CUSTOM` **trước** khi register/refresh. `@Import(BaseConfig.class)` đưa định nghĩa repository và service vào cùng context với bean `AuditSink`.
7. Spring có một bean `AuditSink` phù hợp, nên gọi `setAuditSink(ConsoleAuditSink)` khi cấu hình `ReportService`. Setter prefix nhận `CUSTOM`.
8. `render("B2")` gọi `auditSink.record("B2")`, in `AUDIT B2` **trước** khi `main` in `TEXT CUSTOM:ITEM(B2)`. Cuối cùng `hasAuditSink()` trả `true`.
9. Hai context độc lập: singleton `ReportService` của context này không được chia sẻ mặc định với context kia. `try-with-resources` đóng từng context.

**Điều đáng học:** cả hai lần, logic `render` không cần đổi. Thành phần tùy chọn và prefix được quyết định ở cách Spring lắp ráp/cấu hình bean.

## 7. XML property injection: một cách cấu hình setter khác

Một cấu hình XML có thể đưa giá trị vào setter JavaBeans:

~~~xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
                           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="repository" class="demo.DemoRepository"/>

    <bean id="reportService" class="demo.ReportService">
        <constructor-arg ref="repository"/>
        <property name="prefix" value="REPORT"/>
    </bean>
</beans>
~~~

Đây là **XML minh họa**, không phải tệp XML chạy cùng ví dụ Java có nested classes ở mục 6. Khi dùng thực tế, thay `demo.DemoRepository` và `demo.ReportService` bằng tên class đầy đủ tồn tại trong project, hoặc tách class ra file riêng như thiết kế thông thường.

`<property name="prefix">` ánh xạ tới JavaBeans method `setPrefix(String)`. Nếu thêm:

~~~xml
<property name="auditSink" ref="auditSink"/>
~~~

thì cần đăng ký bean `auditSink` tương ứng để Spring truyền qua setter. Nếu property XML tham chiếu một bean không tồn tại, đó **không tự động là optional injection**: context có thể fail khi tạo bean. Vì thế cú pháp explicit `<property ref>` và `@Autowired(required=false)` khác nhau.

Với legacy XML, cũng có `autowire="byName"` hoặc `autowire="byType"` để tự nối một số property phù hợp. Các mode này có quy tắc riêng và có thể bị mơ hồ, nên không nên chọn chỉ để tránh viết cấu hình rõ ràng khi dependency quan trọng.

## 8. Vì sao không nên dùng setter cho mọi dependency?

### 8.1. Trạng thái chưa hoàn chỉnh

Nếu object nghiệp vụ yêu cầu repository nhưng chỉ khai báo:

~~~java
class BadReportService {
    private ReportRepository repository;

    public void setRepository(ReportRepository repository) {
        this.repository = repository;
    }

    public String render(String id) {
        return repository.find(id);
    }
}
~~~

Đây là đoạn minh họa với `ReportRepository` đã giả định có sẵn. `new BadReportService().render("A1")` sẽ ném `NullPointerException` vì repository chưa được set. Nếu dùng constructor có `Objects.requireNonNull`, thiếu dependency được phát hiện **ngay khi tạo service**, không bị dời sang lúc chạy nghiệp vụ.

Một setter “tùy chọn” phải thật sự có hành vi khi không được gọi; nếu không, đó thực chất là dependency bắt buộc đặt sai nơi.

### 8.2. Thay đổi runtime và concurrency

Bean singleton được nhiều caller dùng chung. Dù có setter public, **không nên tùy ý gọi setter sau khi bean đã phục vụ request** nếu nó thay đổi state không đồng bộ. Ví dụ một thread đang `render` mà thread khác `setPrefix` có thể quan sát cấu hình khác nhau; setter không tự tạo synchronization hay transaction.

Trong ứng dụng thường gặp, setter injection là **bước cấu hình ban đầu của container**, không phải lời mời thay cấu hình runtime không kiểm soát. Nếu cần hot reload, hãy dùng cơ chế quản lý state an toàn, rõ ràng hoặc immutable snapshot.

### 8.3. Không khắc phục vòng phụ thuộc bằng mọi giá

Setter injection trong một số tình huống có thể khiến container giải quyết một số chu kỳ dependency mà constructor injection không thể, nhưng **vòng phụ thuộc thường là dấu hiệu thiết kế cần xem xét**. Không nên cố thay constructor bằng setter chỉ để giấu một graph A↔B; cần tách trách nhiệm, áp dụng mediator/event hoặc thiết kế lại nơi sở hữu hành vi nếu phù hợp.

Spring cũng có nhiều quy tắc riêng về proxy, singleton đang tạo, circular references và Boot defaults. Không suy từ một ví dụ cũ rằng Spring hiện tại luôn giải quyết mọi vòng setter injection.

## 9. Hướng xử lý các tình huống tùy chọn khó hơn

| Tình huống | Lựa chọn phù hợp | Lưu ý |
| --- | --- | --- |
| Dependency không bắt buộc, có thì sử dụng | `@Autowired(required=false)` setter | Method bị bỏ qua khi không thỏa dependency |
| Muốn thể hiện option rõ trong constructor | `Optional<T>` | Không cần biến mọi dependency thành setter |
| Cần lấy dependency muộn hoặc theo nhu cầu | `ObjectProvider<T>` | Xác định semantics nếu thiếu/nhiều bean |
| Tham số cấu hình có default | `@Value("${key:default}")` hoặc `Environment.getProperty(key, default)` | Validate giá trị rỗng/không hợp lệ |
| Cấu hình nhiều trường liên quan | Bean config có validation, constructor injection | Giảm mutable setters rời rạc |
| Có nhiều bean cùng interface | `@Qualifier` hoặc `@Primary` | `required=false` không giải quyết mơ hồ |
| Cần một hành vi mặc định khi không có collaborator | Null Object hoặc điều kiện rõ | Không âm thầm che thiếu dependency bắt buộc |
| Chọn implementation tùy từng request | Strategy/router theo business data | Không dùng bean injection để thay quyết định runtime |
| Đổi cấu hình lúc đang phục vụ nhiều thread | Thiết kế snapshot/thread-safety rõ | Setter thông thường không giải quyết concurrency |

### Multi-argument optional method

~~~java
@Autowired(required = false)
public void configure(AuditSink audit, MetricsSink metrics) {
    this.audit = audit;
    this.metrics = metrics;
}
~~~

Đây là **minh họa**, `MetricsSink` và các field chưa được định nghĩa. Nếu thiếu một trong hai dependency, method non-required có thể **không được gọi hoàn toàn**. Nếu hai thứ có tính tùy chọn **độc lập**, nên chia thành hai injection point hoặc dùng `Optional<T>`/provider phù hợp, thay vì đặt vào cùng một setter rồi vô tình buộc chúng cùng xuất hiện.

### Tương tác với `@Bean` và annotation processing

`@Autowired`/`@Value` được áp dụng bởi `BeanPostProcessor`. `AnnotationConfigApplicationContext` đăng ký các annotation processors cần thiết trong cấu hình thông thường, bao gồm xử lý bean được tạo từ `@Bean` methods.

Tuy nhiên, nếu bạn **tự `new ReportService(repo)` ngoài Spring context**, những annotation setter **không tự chạy**. Cần tự gọi setter, hoặc đưa đối tượng vào quy trình quản lý/cấu hình bean một cách rõ ràng. `@Autowired` không phải một cú pháp được JVM tự thực thi khi gọi constructor.

Trong những bean thuộc loại `BeanFactoryPostProcessor` hoặc `BeanPostProcessor` của chính bạn, không nên mặc định có thể áp dụng injection annotation như trên các bean nghiệp vụ thông thường: các đối tượng tham gia xử lý post-processing được dựng ở giai đoạn đặc biệt và có những hạn chế cần cấu hình rõ.

## 10. Lỗi thường gặp và cách chẩn đoán

| Triệu chứng | Nguyên nhân / hướng xử lý |
| --- | --- |
| Setter `@Autowired` không chạy | Thiếu bean phù hợp; `required=false` khiến Spring bỏ qua method |
| Setter chạy nhưng code vẫn dùng null | Kiểm tra field thực sự được gán, null guard và lifecycle của bean |
| Thiếu bean nhưng context vẫn khởi động | Đây có thể là hành vi đúng nếu dependency thật sự optional |
| Dependency bắt buộc không có mà service vẫn được tạo | Thiết kế sai ranh giới; chuyển dependency sang constructor |
| Có 2 bean cùng kiểu optional và context lỗi | Phân giải mơ hồ bằng qualifier/primary; optional không đồng nghĩa chọn bừa |
| `@Value` không lấy default như mong đợi | Key có thể tồn tại nhưng rỗng; kiểm tra property sources và validation |
| Property vừa được thêm nhưng đã refresh context | Thêm/cấu hình nguồn property trước bước bean creation nếu muốn ảnh hưởng setter |
| Setter `@Value` không chạy trên object tự tạo bằng `new` | Annotation được Spring xử lý với bean managed, không phải bởi Java trực tiếp |
| XML có `<property ref>` nhưng thiếu bean tham chiếu | Explicit ref bắt buộc bean name hợp lệ; không tương đương setter optional |
| `@Autowired(required=false)` trên method nhiều tham số bỏ qua tất cả | Một tham số không giải quyết được có thể khiến method không được gọi |
| Bean singleton thay cấu hình giữa runtime bị race | Cần thiết kế concurrency/hot-reload riêng |
| Gắn setter injection để chữa mọi vòng phụ thuộc | Đánh giá lại kiến trúc dependency graph |
| Kỳ vọng Spring tạo HTTP/transaction từ setter annotation | DI không tự bổ sung web server hoặc transaction manager |

**Quy trình debug:** xác nhận class thực sự là Spring-managed bean → annotation processor được đăng ký → đúng setter signature → bean candidate/property key có tồn tại → có nhiều candidate không → kiểm tra default khi injection bị bỏ qua → xác minh lỗi gốc trong stack trace.

## 11. Bài tập tự luyện có đáp án

**Bài 1.** `ReportRepository` là bắt buộc, `AuditSink` chỉ để ghi trace. Nên inject chúng bằng cách nào?

**Đáp án:** Repository qua constructor injection; audit có thể qua setter `@Autowired(required=false)` với hành vi an toàn khi vắng mặt. Nếu audit thực ra bắt buộc về tuân thủ, không nên đánh dấu optional.

**Bài 2.** Vì sao context không có `AuditSink` vẫn render được?

**Đáp án:** Spring bỏ qua setter `@Autowired(required=false)`, `auditSink` giữ giá trị `null`; `render` kiểm tra trước khi gọi audit. Constructor vẫn nhận repository đầy đủ.

**Bài 3.** Context thứ hai in `AUDIT B2` trước `TEXT CUSTOM:ITEM(B2)` vì sao?

**Đáp án:** `render` gọi `auditSink.record` trong khi tính kết quả, sau đó trả chuỗi cho `main` in. Các dòng không phải do Spring tự in log.

**Bài 4.** Nếu có hai bean `AuditSink` cùng type, `required=false` có tự chọn một không?

**Đáp án:** Không. Cần quy tắc chọn rõ bằng `@Primary`/`@Qualifier` hoặc thiết kế injection nhiều bean có chủ đích.

**Bài 5.** `@Value("${app.report.prefix:REPORT}")` có tương đương “đọc property hoặc dùng REPORT nếu giá trị là khoảng trắng” không?

**Đáp án:** Không. Default chủ yếu dùng khi property không tìm thấy; một giá trị đã cung cấp nhưng blank có thể cần tự từ chối hoặc chuẩn hóa. Setter ở demo ném lỗi nếu blank.

**Bài 6.** Nếu khai báo method optional có hai tham số và chỉ có một bean tương ứng, method sẽ nhận null cho tham số còn lại?

**Đáp án:** Không nên giả định như vậy. Với method `@Autowired(required=false)`, nếu một parameter không có dependency, method có thể bị bỏ qua toàn bộ. Có thể dùng `Optional<T>` ở từng tham số để thể hiện độc lập.

**Bài 7.** Unit test `ReportService` không cần context được không?

**Đáp án:** Có. Dựng một fake `ReportRepository`, gọi constructor; nếu cần thì gọi `setAuditSink` và `setPrefix` thủ công, sau đó assert `render` và các lời gọi fake. Annotation không bắt buộc để Java method setter chạy.

**Bài 8.** Vì sao không nên thay `ReportRepository` bằng một setter optional để “linh hoạt hơn”?

**Đáp án:** Vì service không thể render khi thiếu repository. Constructor biến dependency bắt buộc thành điều kiện tạo object hợp lệ, phát hiện lỗi sớm hơn.

## 12. Checklist áp dụng thực tế

- [ ] Mỗi dependency được phân loại rõ: **bắt buộc** hay **tùy chọn**.
- [ ] Dependency bắt buộc được inject qua constructor, không để service vào trạng thái thiếu điều kiện hoạt động.
- [ ] Setter optional có default hoặc nhánh xử lý vắng mặt rõ ràng.
- [ ] `@Autowired(required=false)` được đặt đúng trên setter và hiểu rằng method có thể bị bỏ qua.
- [ ] Với method nhiều tham số, kiểm tra semantics `required=false` áp dụng cho toàn method.
- [ ] Không dùng `required=false` để giải quyết lỗi nhiều bean cùng type hoặc lỗi tạo bean.
- [ ] `@Value` placeholder có default đúng cú pháp và giá trị được validate khi cần.
- [ ] Đặt property sources trước `refresh()` hoặc trước khi bean cần được khởi tạo.
- [ ] Hiểu difference giữa setter được gọi trong `@Bean`, annotation autowiring và XML property.
- [ ] Không trộn một setter cấu hình nhiều nguồn (XML, annotation, manual) nếu thứ tự ưu tiên không rõ.
- [ ] Unit test service với/không có optional collaborator; integration test cả hai profile/configurations.
- [ ] Khi service singleton chạy đa luồng, tránh chỉnh mutable settings tùy tiện giữa runtime.
- [ ] Đừng biến mọi dependency thành optional chỉ để context không fail.
- [ ] Xác nhận output của chương trình và phân biệt với log Maven.
- [ ] Đóng `ApplicationContext` đúng vòng đời và không trông chờ injection trên object ngoài container.

### Đọc thêm trong tài liệu Spring

- [Setter-based Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)
- [Using @Autowired và required=false](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html)
- [Using @Value](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/value-annotations.html)
- [Annotation-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config.html)

**Kết luận:** Setter injection có giá trị khi cấu hình hoặc collaborator **thực sự tùy chọn** và object vẫn hoạt động đúng khi setter không được gọi. `@Autowired(required=false)` biểu đạt khả năng bỏ qua injection, `@Value` giúp áp dụng property có default, trong khi constructor injection giữ các dependency bắt buộc rõ ràng. Sự tách bạch đó khiến bean hợp lệ ngay từ lúc tạo nhưng vẫn linh hoạt ở những điểm cấu hình hợp lý.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dependency-injection-004">Tiêm trực tiếp vào trường và ảnh hưởng tới kiểm thử</a>

<details>
<summary>Xem chi tiết</summary>

# Tiêm trực tiếp vào trường và ảnh hưởng tới kiểm thử trong Spring

## 1. Khái niệm field injection và vấn đề cốt lõi

**Field injection** là hình thức Dependency Injection (DI) trong đó Spring IoC Container gán bean cộng tác vào **trường của một object đã được tạo**. Cách viết quen thuộc:

~~~java
// Mã minh họa; interface GreetingRepository có trong ví dụ đầy đủ ở mục 5.
class FieldWiredGreeter {
    @Autowired
    private GreetingRepository repository;

    String greet(String name) {
        return repository.greet(name);
    }
}
~~~

`@Autowired` trên field là một cơ chế hợp lệ của Spring. Nhưng `new FieldWiredGreeter()` bằng Java thuần **không kích hoạt autowiring**, khiến `repository` chưa được gán. Lời gọi `greet()` có thể gây `NullPointerException` dù code **biên dịch thành công**. Đó là hệ quả của **dependency bị ẩn khỏi chữ ký constructor**.

Hãy tách ba câu hỏi:
1. **Dependency cần thiết gì?** Ví dụ `GreetingRepository` là bắt buộc để service tạo lời chào.
2. **Ai cấp dependency?** Caller Java thuần truyền vào constructor hay Spring container resolve bean?
3. **Có thể kiểm thử logic mà không dựng container không?** Nếu dependency hiển thị trong constructor, truyền fake rất dễ. Nếu nó chỉ nằm ở private field có `@Autowired`, người viết test thường phải dựng Spring hoặc can thiệp vào field.

| Đặc điểm | Field injection | Constructor injection |
| --- | --- | --- |
| Vị trí khai báo dependency | `@Autowired` trên field | Tham số constructor |
| `new Service()` khi thiếu dependency | Có thể tạo object thiếu wiring | Không compile nếu chỉ có constructor bắt buộc tham số |
| Reference `final` | Không phù hợp với cách gán sau construction | Tự nhiên, dễ tạo immutable wiring |
| Unit test Java thuần | Cần giải pháp để cấp private field | `new Service(fake)`, không cần Spring |
| Dấu hiệu class có nhiều dependencies | Nằm rải ở fields/annotations | Nhìn thấy trong constructor |
| Autowiring khi Spring quản lý bean | Có | Có |
| Khả năng lỗi vì thiếu bean trong Spring | Có | Có |

**Không kết luận field injection luôn sai.** Nó được Spring hỗ trợ, và có hệ thống legacy hoặc integration adapter vẫn dùng. Mục đích là nhận diện **ảnh hưởng cụ thể lên testability, trạng thái khởi tạo và chất lượng API** để chọn cơ chế DI phù hợp.

## 2. Spring thực hiện injection khi nào?

Khi container tạo một **Spring-managed bean**, các annotation post-processors như `AutowiredAnnotationBeanPostProcessor` xử lý `@Autowired` và tìm bean tương thích để gán vào field. Việc này xảy ra **sau constructor** (trước khi bean được khởi tạo xong để sử dụng thông thường).

~~~text
Nguồn cấu hình @Bean hoặc @Component + scan
                    |
                    v
          Đăng ký BeanDefinition
                    |
                    v
           Spring tạo object Java
                    |
                    v
       Xử lý @Autowired private field
                    |
           Resolve dependency
              /           \
       Có candidate      Thiếu required
            |                 |
            v                 v
     Gán reference       Bean creation fail
            |
            v
       Initialization callbacks
            |
            v
      Bean sẵn sàng để sử dụng
~~~

Trường `private` vẫn có thể được Spring inject qua reflection/access mechanism phù hợp. Điều đó không có nghĩa Java tự bỏ qua encapsulation cho mọi caller. Trong môi trường **Java module path (JPMS)**, quyền truy cập reflection/module openness có thể cần cấu hình thích hợp; ví dụ này chạy trên **classpath truyền thống**.

**Quan trọng:** chỉ đặt annotation trên class/field **không tự tạo bean**. Đối tượng phải được container quản lý bằng một cấu hình hợp lệ: component scan, `@Bean`, XML hoặc API đăng ký tương ứng. `AnnotationConfigApplicationContext` tự thiết lập annotation processors phù hợp khi được khởi tạo bằng Java configuration.

Với field-injected bean:

~~~java
@Autowired
private GreetingRepository repository;
~~~

Đừng truy cập `repository` trong constructor nếu bạn phụ thuộc vào việc Spring inject nó: ở giai đoạn đó field vẫn có giá trị mặc định hoặc giá trị khởi tạo trực tiếp từ Java. Trước khi chuyển một bean cho client, container thông thường đã hoàn tất injection hoặc đã báo lỗi wiring.

**`@Autowired` mặc định required.** Thiếu bean phù hợp thường khiến Spring fail khi tạo bean. Với singleton không lazy trong `ApplicationContext`, lỗi có thể xuất hiện ngay ở `refresh()`/startup; với lazy/prototype, có thể xuất hiện ở lần yêu cầu bean. Điều này khác với việc Java tự `new` object rồi nhận field null.

## 3. Vì sao unit test cho field injection thường khó hơn?

### Dependency ẩn trong object được tạo

Một unit test viết:

~~~java
// Đoạn minh họa:
FieldWiredGreeter service = new FieldWiredGreeter();
service.greet("An");
~~~

đã tạo được object nhưng chưa cấp repository. Người viết test chỉ phát hiện dependency bằng cách đọc các fields/annotations hoặc khi runtime lỗi. Trong khi đó:

~~~java
// Đoạn minh họa của một class có constructor dependency:
ConstructorWiredGreeter service =
        new ConstructorWiredGreeter(name -> "Fake:" + name);
System.out.println(service.greet("An"));
~~~

có thể hoạt động hoàn toàn bằng Java thuần; lambda fake thực thi một interface có một abstract method.

### Những chiến lược test field-injected bean

| Chiến lược | Test được gì | Hạn chế |
| --- | --- | --- |
| Dựng một `ApplicationContext` nhỏ | Container có inject đúng, bean có hoạt động không | Test phải chuẩn bị Spring config và chạy lifecycle |
| Dùng Spring Test `ReflectionTestUtils.setField` hoặc reflection thuần | Business behavior sau khi ép dependency | Test phụ thuộc tên field/chi tiết triển khai; cần `spring-test` nếu dùng tiện ích |
| Thêm setter/package-level seam cho test | Cho phép cấp dependency thủ công | Có thể tạo API test-only, làm object mutable và thiếu invariant |
| Refactor sang constructor injection | Unit test business bằng fake, không cần Spring | Cần cập nhật constructor và composition root |
| Mockito `@InjectMocks` (khi dùng Mockito) | Tạo subject và thử inject mocks | Hành vi công cụ mock không thay thế test Spring wiring; dependency/runner riêng |

Trong code mới có quyền thay đổi, **constructor injection + unit test thuần Java** thường ít tốn công bảo trì hơn việc phản chiếu private fields. Với mã không sửa được, test context nhỏ là lựa chọn minh bạch nếu mục tiêu thật sự là xác nhận Spring autowiring.

**Unit test behavior khác integration test wiring:** logic `greet("An")` có thể đúng nhưng cấu hình Spring thiếu bean; hoặc cấu hình Spring pass nhưng business logic trả giá trị sai. Vì thế nên có đúng loại kiểm chứng cho từng mục tiêu.

## 4. Constructor injection, `final` và thiết kế bất biến

Cách khai báo được khuyến nghị cho dependency bắt buộc:

~~~java
// Snippet; cần import java.util.Objects và interface GreetingRepository.
final class ConstructorWiredGreeter {
    private final GreetingRepository repository;

    ConstructorWiredGreeter(GreetingRepository repository) {
        this.repository =
                Objects.requireNonNull(repository, "repository");
    }

    String greet(String name) {
        return repository.greet(name);
    }
}
~~~

**Ưu điểm:**
- API constructor làm rõ rằng object cần `GreetingRepository` ngay từ đầu.
- Java caller có thể truyền fake/in-memory repository, không cần Spring.
- `Objects.requireNonNull` fail-fast cả khi gọi từ code Java thuần.
- Trường `final` giúp đảm bảo reference không bị gán lại sau constructor.
- Khi constructor có rất nhiều dependencies, code review dễ nhận diện class đang gánh quá nhiều trách nhiệm.

Lưu ý: `final` **không biến bản thân repository thành immutable hoặc thread-safe**. Repository vẫn có thể giữ state mutable hoặc thực hiện I/O; Spring singleton cũng không tự đồng bộ các lời gọi đa luồng.

Spring Framework hỗ trợ autowiring cho một **constructor duy nhất** của component mà thông thường **không cần đặt `@Autowired`**. Với `@Bean` configuration, bạn cũng có thể để Spring inject qua tham số factory method. Nhiều constructors cần tuân thủ quy tắc chọn constructor; không nên trông đợi Spring tùy ý đoán đúng overload.

**Không cần tránh mọi lệnh `new` trong Spring.** Một `@Bean` factory method có thể trả về `new ConstructorWiredGreeter(repository)`; điều Spring giúp thực hiện là **resolve `repository` và quản lý object trả về** như bean trong container.

## 5. Project Maven Java 17: một chương trình đối chiếu hai kiểu injection

Ví dụ dùng **Spring Framework thuần**, không cần Spring Boot, HTTP server hoặc database. Chương trình quan sát:
1. `new FieldWiredGreeter()` ở Java thuần **không có repository**.
2. Constructor service nhận fake Java thuần và **hoạt động được ngay**.
3. Một Spring context đăng ký cả hai service: **cả field và constructor injection đều hoạt động**.
4. Constructor-injected service giữ đúng bean dependency được lấy qua context.

Yêu cầu JDK **17+**, Maven **3.9+**, Maven Central/mirror để tải dependency khi cần. Version bài học: **Spring Framework 7.0.9**, **JUnit Jupiter 5.12.2**.

### 5.1. Cấu trúc

~~~text
field-injection-demo/
├── pom.xml
└── src/
    ├── main/
    │   └── java/demo/FieldInjectionDemo.java
    └── test/
        └── java/demo/FieldInjectionDemoTest.java
~~~

### 5.2. `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>field-injection-demo</artifactId>
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
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.12.2</version>
            <scope>test</scope>
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
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.3</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>demo.FieldInjectionDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` cung cấp `AnnotationConfigApplicationContext`, `@Configuration`, `@Bean` và hỗ trợ xử lý `@Autowired`; Maven tự resolve những Spring artifacts nền cần thiết. `junit-jupiter` là dependency thuộc **test scope**; Surefire chạy các test qua JUnit Platform. POM không cần `spring-test` vì test context ví dụ được mở trực tiếp bằng `AnnotationConfigApplicationContext`.

### 5.3. `src/main/java/demo/FieldInjectionDemo.java`

~~~java
package demo;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class FieldInjectionDemo {
    private FieldInjectionDemo() {
    }

    @FunctionalInterface
    interface GreetingRepository {
        String greet(String name);
    }

    static final class FieldWiredGreeter {
        @Autowired
        private GreetingRepository repository;

        boolean hasRepository() {
            return repository != null;
        }

        String greet(String name) {
            return repository.greet(name);
        }
    }

    static final class ConstructorWiredGreeter {
        private final GreetingRepository repository;

        ConstructorWiredGreeter(GreetingRepository repository) {
            this.repository =
                    Objects.requireNonNull(repository, "repository");
        }

        GreetingRepository dependency() {
            return repository;
        }

        String greet(String name) {
            return repository.greet(name);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        public GreetingRepository greetingRepository() {
            return name -> "Hello, " + name + "!";
        }

        @Bean
        public FieldWiredGreeter fieldWiredGreeter() {
            return new FieldWiredGreeter();
        }

        @Bean
        public ConstructorWiredGreeter constructorWiredGreeter(
                GreetingRepository repository) {
            return new ConstructorWiredGreeter(repository);
        }
    }

    public static void main(String[] args) {
        // new thuần Java không thực thi @Autowired.
        var plainField = new FieldWiredGreeter();
        System.out.println("plain.field.ready="
                + plainField.hasRepository());

        try {
            plainField.greet("Lan");
        } catch (NullPointerException expected) {
            System.out.println("plain.field.callFailed=true");
        }

        // Constructor nhận fake: không cần context.
        var plainConstructor = new ConstructorWiredGreeter(
                name -> "Fake:" + name);
        System.out.println("plain.constructor.greet="
                + plainConstructor.greet("Lan"));

        // Đối chứng có Spring container.
        try (var context = new AnnotationConfigApplicationContext(
                AppConfig.class)) {
            var field = context.getBean(FieldWiredGreeter.class);
            var constructor = context.getBean(
                    ConstructorWiredGreeter.class);
            var repository = context.getBean(GreetingRepository.class);

            System.out.println("spring.field.ready="
                    + field.hasRepository());
            System.out.println("spring.field.greet="
                    + field.greet("Lan"));
            System.out.println("spring.constructor.greet="
                    + constructor.greet("Lan"));
            System.out.println("spring.constructor.sameDependency="
                    + (constructor.dependency() == repository));
            System.out.println("spring.constructor.singleton="
                    + (constructor == context.getBean(
                            ConstructorWiredGreeter.class)));
        }
    }
}
~~~

**Giải thích:**

- `plainField` là object được tạo ngoài Spring nên field annotation **không được xử lý**. Lỗi null trong demo được catch có chủ đích; **không** dùng catch `NullPointerException` làm cách xử lý thiếu dependency trong sản phẩm.
- `plainConstructor` nhận fake qua lambda một phương thức, kiểm chứng API testable mà không cần container.
- `AppConfig` cung cấp bean `GreetingRepository` và hai services. Dù `FieldWiredGreeter` được trả về từ một factory method `@Bean` (không có `@Component`), Spring **vẫn post-process bean ấy và tiêm `@Autowired` field**.
- `constructorWiredGreeter(GreetingRepository repository)` nhận dependency qua **Spring-resolved `@Bean` method parameter**. `ConstructorWiredGreeter` chỉ phụ thuộc Java API `Objects`, không import Spring.
- `@Configuration(proxyBeanMethods=false)` phù hợp vì các factory methods không tự gọi nhau để lấy bean. Scope mặc định singleton giúp hai lần lấy `ConstructorWiredGreeter` từ **cùng context** trả cùng object.

### 5.4. Biên dịch/chạy và expected stdout

Đứng tại thư mục `field-injection-demo` chứa `pom.xml`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

Maven Compiler Plugin biên dịch bằng `javac` với `--release 17`; Exec Plugin đưa Spring dependencies vào runtime classpath. Ba lệnh kiểm tra phiên bản đầu có output phụ thuộc máy.

**8 dòng stdout do code `main` in**, không tính log Maven/Spring tùy môi trường:

~~~text
plain.field.ready=false
plain.field.callFailed=true
plain.constructor.greet=Fake:Lan
spring.field.ready=true
spring.field.greet=Hello, Lan!
spring.constructor.greet=Hello, Lan!
spring.constructor.sameDependency=true
spring.constructor.singleton=true
~~~

Kết quả chứng minh **không phải field injection luôn thất bại**: ở Spring-managed bean, nó hoạt động. Sự khác biệt là khi test dùng `new`, field-injected service không có API constructor để cung cấp dependency bắt buộc, còn constructor service có thể nhận fake rõ ràng.

## 6. JUnit 5: tách unit test không Spring và test bean wiring

Tệp hoàn chỉnh **`src/test/java/demo/FieldInjectionDemoTest.java`**:

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

final class FieldInjectionDemoTest {

    @Test
    void constructorBehaviorWithoutSpring() {
        FieldInjectionDemo.GreetingRepository fake =
                name -> "Test:" + name;
        var service = new FieldInjectionDemo.ConstructorWiredGreeter(fake);

        assertEquals("Test:Lan", service.greet("Lan"));
        assertSame(fake, service.dependency());
        assertThrows(NullPointerException.class,
                () -> new FieldInjectionDemo.ConstructorWiredGreeter(null));
    }

    @Test
    void plainFieldObjectIsUnwired() {
        var service = new FieldInjectionDemo.FieldWiredGreeter();

        assertFalse(service.hasRepository());
        assertThrows(NullPointerException.class,
                () -> service.greet("Lan"));
    }

    @Test
    void springManagesBothInjectionStyles() {
        try (var context = new AnnotationConfigApplicationContext(
                FieldInjectionDemo.AppConfig.class)) {
            var field = context.getBean(
                    FieldInjectionDemo.FieldWiredGreeter.class);
            var constructor = context.getBean(
                    FieldInjectionDemo.ConstructorWiredGreeter.class);
            var repository = context.getBean(
                    FieldInjectionDemo.GreetingRepository.class);

            assertTrue(field.hasRepository());
            assertEquals("Hello, Lan!", field.greet("Lan"));
            assertEquals("Hello, Lan!", constructor.greet("Lan"));
            assertSame(repository, constructor.dependency());
        }
    }
}
~~~

Chạy:

~~~bash
mvn -q test
~~~

**Kết quả kiểm thử mong đợi:** 3 test methods thành công, **0 failures, 0 errors, 0 skipped**. Báo cáo Surefire có thể đọc tại `target/surefire-reports/demo.FieldInjectionDemoTest.txt`, hoặc bỏ `-q` khi chạy Maven để xem summary. **Không có một stdout cố định cho toàn bộ Maven/Surefire**, nên không giả vờ rằng mỗi lần chạy đều in đúng cùng một đoạn terminal.

**Ba mục đích khác nhau:**
1. `constructorBehaviorWithoutSpring`: kiểm tra business logic và null invariant, **không** dùng context.
2. `plainFieldObjectIsUnwired`: negative test cho `new` field-injected object, không phải khẳng định Spring DI sai.
3. `springManagesBothInjectionStyles`: integration test nhỏ xác nhận bean registration/post-processing và wiring thật.

Một unit test cho service không cần Spring có thể dùng fake/spy/mock để kiểm tra logic; integration test có context cần khi đang kiểm tra configuration. Với ứng dụng lớn, tách loại test giúp rút ngắn vòng phản hồi và tránh phụ thuộc vào nhiều beans không liên quan.

## 7. Java 17 thuần với `javac`/`java`: injection không phải đặc quyền Spring

Tạo file riêng **`PlainJavaGreeter.java`** ngoài project Maven (hoặc tại một thư mục ví dụ độc lập):

~~~java
import java.util.Objects;

public class PlainJavaGreeter {
    interface Greeter {
        String greet(String name);
    }

    static final class Service {
        private final Greeter greeter;

        Service(Greeter greeter) {
            this.greeter = Objects.requireNonNull(greeter);
        }

        String greet(String name) {
            return greeter.greet(name);
        }
    }

    public static void main(String[] args) {
        Service service = new Service(name -> "Fake:" + name);
        System.out.println("pure.fake=" + service.greet("Lan"));

        try {
            new Service(null);
        } catch (NullPointerException expected) {
            System.out.println("pure.rejectsNull=true");
        }
    }
}
~~~

Biên dịch/chạy bằng JDK 17+, **không Maven hoặc Spring**:

~~~bash
javac PlainJavaGreeter.java
java PlainJavaGreeter
~~~

Stdout:

~~~text
pure.fake=Fake:Lan
pure.rejectsNull=true
~~~

Bài học: thiết kế DI qua constructor là kỹ thuật Java cơ bản. Spring bổ sung **automatic bean resolution và lifecycle**, không thay thế trách nhiệm tạo một class có hợp đồng rõ ràng.

## 8. Field injection với optional dependency, qualifiers và giới hạn

### `@Autowired(required=false)`

~~~java
// Snippet (đặt trong class có type Repository phù hợp):
@Autowired(required = false)
private GreetingRepository repository;
~~~

Nếu không tìm được bean phù hợp, Spring có thể **không gán field**, giữ giá trị khởi tạo mặc định (`null` nếu chưa gán). Điều đó **chỉ đúng** nếu dependency thực sự tùy chọn theo hợp đồng class và tất cả chỗ sử dụng đều xử lý trường hợp vắng. Với một repository bắt buộc, chuyển thành optional chỉ để tránh lỗi startup là rủi ro.

Nếu nhiều beans `GreetingRepository` cùng type và không có cách chọn rõ ràng, Spring vẫn có thể báo `NoUniqueBeanDefinitionException`. `required=false` **không phải công tắc “chọn bừa một bean”**.

### `@Qualifier`, `@Primary`

Có thể giải quyết nhiều candidates ở field injection:

~~~java
@Autowired
@Qualifier("primaryGreetingRepository")
private GreetingRepository repository;
~~~

Cần import `org.springframework.beans.factory.annotation.Qualifier` và bean thực có qualifier/name thích hợp. Với constructor injection, bạn có thể đặt `@Qualifier` lên **tham số constructor** tương ứng; chuyển cơ chế injection không tự xử lý ambiguity.

### `final` và thread safety

Không cố biến field injection thành `private final Repository repository` rồi kỳ vọng Spring sẽ bypass hoàn toàn invariant construction. Trong code thông thường, `final` phải được khởi tạo đúng lúc xây object; constructor injection là con đường rõ ràng.

Tuy nhiên, giữ reference `final` chỉ giúp **stable wiring**, không bảo đảm repository object bất biến hoặc xử lý concurrency an toàn. Một singleton có mutable fields vẫn có thể xảy ra race khi dùng từ nhiều threads. Cần thiết kế stateless hoặc kiểm soát synchronization phù hợp.

### Circular dependency

Field/setter injection đôi khi khiến một số chu trình phụ thuộc được xử lý thông qua early references, nhưng không phải mọi trường hợp. Những object chưa hoàn tất initialization có thể khó hiểu; **không dùng field injection làm giải pháp mặc định cho vòng phụ thuộc**. Hãy xem lại hướng phụ thuộc, trách nhiệm của class và cách tách services.

## 9. Khi nào nên giữ field injection, khi nào nên refactor?

**Ưu tiên constructor injection** khi:
- Dependency **bắt buộc** để object hoạt động đúng.
- Cần unit test service bằng fake/stub/mock mà không khởi động Spring.
- Muốn fields `final` và construction invariant rõ.
- Business class phải dùng được trong console/batch hoặc Java code độc lập.
- Muốn review dependency graph qua constructor và phát hiện class có quá nhiều phụ thuộc.

**Có thể tạm giữ field injection** khi:
- Bảo trì mã legacy có số lượng class lớn; thay constructor có thể đòi chỉnh sửa nhiều entry points.
- Mã thuộc framework adapter do Spring luôn quản lý, cần xác minh chủ yếu qua context và không có nhu cầu new trực tiếp.
- Đang chuyển dần sang constructor injection theo từng bước có test bảo vệ; không nên refactor một cách máy móc nếu ảnh hưởng hành vi chưa kiểm chứng.

Khi không sửa được class legacy, test có thể dùng context nhỏ hoặc reflection helper. Nhưng phải ghi rõ rằng **test phụ thuộc implementation detail** như tên field và yêu cầu quyền reflection. Spring Test `ReflectionTestUtils` cần artifact `spring-test` trong test scope; **POM ví dụ này không có artifact đó** vì không sử dụng reflection để chạy test.

Một phép so sánh thực tế: field injection rút ngắn code constructor vài dòng, nhưng có thể tăng số thao tác setup và số assumptions ở mỗi unit test. Đánh giá theo **tổng độ phức tạp** thay vì chỉ độ dài class.

## 10. Những lỗi phổ biến và cách xử lý

| Triệu chứng | Nguyên nhân khả dĩ | Cách kiểm tra/khắc phục |
| --- | --- | --- |
| `new FieldWiredGreeter()` rồi gọi `greet()` bị NPE | Java không chạy Spring post-processors | Dùng constructor injection khi test Java thuần hoặc test thông qua context |
| `@Autowired` field trên object tạo bằng `new` vẫn null | Object không được container quản lý | Đăng ký bean đúng hoặc thiết kế class không phụ thuộc container |
| Spring startup lỗi thiếu repo | Field required không có candidate | Xem bean registration, component scan và type/qualifier |
| Nhiều repositories cùng type | Candidate mơ hồ | `@Qualifier`, `@Primary` hoặc cấu hình rõ |
| Field được dùng trong constructor | Injection diễn ra sau construction | Truyền required dependency qua constructor |
| Test không thể gán private field | Dependency không có seam để truyền vào | Refactor constructor; legacy mới cân nhắc context/reflection |
| `final` field vẫn cho phép thay đổi nội dung object con | `final` khóa reference, không khóa object graph | Immutable value objects/defensive copies/concurrency design |
| `@Autowired(required=false)` vẫn gây NPE | Field null nhưng business code không kiểm tra | Xác định dependency thật sự optional hay required |
| Test quá chậm vì phải mở Spring cho từng method | Dùng context cả cho test behavior đơn giản | Tách unit tests dùng fake và integration tests wiring |
| Field/circular dependency dẫn tới object nửa khởi tạo | Graph phụ thuộc vòng hoặc lifecycle không rõ | Phá vòng, dùng design với hướng phụ thuộc đơn giản |
| Lỗi Maven `package org.springframework...` | Thiếu Spring trên compile classpath | Đúng `spring-context`, sync Maven, kiểm JDK |
| Kết quả stdout khác bản gốc | Source/config đã chỉnh hoặc log nền xen vào | Chạy source gốc, phân biệt stdout ứng dụng với diagnostic |

**Phân tầng lỗi:** thiếu JAR là lỗi classpath/build; thiếu bean required là lỗi container wiring; tạo đối tượng Java bằng `new` rồi dùng field null là lỗi construction contract; business trả kết quả sai là lỗi nghiệp vụ. Không dùng một loại giải pháp cho tất cả.

## 11. Bài tập thực hành và checklist

Làm từng thử nghiệm trên **bản sao**, khôi phục source giữa các bài để so với stdout gốc:

1. **Refactor:** đổi `FieldWiredGreeter` sang constructor nhận `GreetingRepository`, sửa factory `fieldWiredGreeter(repo)`, rồi viết unit test thuần Java không cần context.
2. **Giữ field, dùng Spring:** thêm test assert reference của repository trong field bean sau khi context khởi tạo (qua một method quan sát, không yêu cầu reflection).
3. **Bỏ bean repo:** xóa `@Bean greetingRepository()` trong bản sao; quan sát startup failure và cause chain.
4. **Thêm candidate thứ hai:** đăng ký thêm bean `GreetingRepository`; dự đoán ambiguity ở field và factory-method injection.
5. **Dùng qualifier:** chỉ định bean được chọn, xác nhận cả hai services nhận implementation mong đợi.
6. **Thử `@Autowired(required=false)`:** giả định không có repo; giải thích vì sao service phải có fallback hợp lệ trước khi đổi thành optional.
7. **Test fake khác:** sửa lambda fake trong JUnit thành `name -> "TEST2:" + name`, cập nhật assertion mà không sửa business class.
8. **Kiểm `final`:** thử gán lại repository sau constructor trong constructor service; quan sát lỗi compiler.
9. **Gọi dependency từ constructor field service:** mô tả vì sao dùng field injected quá sớm.
10. **Bỏ context:** dùng `PlainJavaGreeter` để kiểm tra thuật toán `greet` và null validation chỉ với `javac`/`java`.
11. **Nhiều contexts:** so sánh object identity qua hai contexts, giải thích singleton có phạm vi theo container.
12. **Thiết kế test suite:** lập nhóm pure unit test cho logic và integration tests cho bean wiring, xác định test nào nên chạy thường xuyên hơn.

**Checklist:**
- [ ] Field injection dùng `@Autowired` trên field của **Spring-managed bean**.
- [ ] Spring xử lý field sau construction; `new` Java không tự kích hoạt wiring.
- [ ] Constructor injection cho phép tạo object hợp lệ với fake trong unit test.
- [ ] `final` là ràng buộc reference, không đồng nghĩa deep immutability hoặc thread safety.
- [ ] 8 dòng stdout Maven và 2 dòng stdout Java thuần dự đoán đúng.
- [ ] 3 bài test JUnit phân biệt được behavior, negative scenario và Spring wiring.
- [ ] `@Autowired(required=false)` chỉ phù hợp dependency tùy chọn thực sự.
- [ ] Xử lý thiếu/mơ hồ bean với cấu hình đúng, không che lỗi bằng annotation.
- [ ] Biết khi nào dùng test context, khi nào dùng fake Java thuần và vì sao reflection test dễ giòn.

## 12. Tóm tắt và nguồn tham khảo

Field injection **hoạt động trong Spring** nhưng làm required dependency ít rõ trong API khởi tạo: unit test trực tiếp với `new` dễ tạo ra object chưa được tiêm. Constructor injection hiển thị hợp đồng, hỗ trợ `private final`, fail-fast với null guard và cho phép kiểm thử hành vi bằng fake thuần Java. Với Spring app, hai cơ chế vẫn cần bean configuration chính xác.

Các nguồn chính thức để tra cứu:
- [Spring Framework — Using @Autowired](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html)
- [Spring Framework — Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)
- [Spring Framework — Annotation-based Configuration](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config.html)
- [Spring Framework — Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)
- [Spring Framework — Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html)
- [JUnit 5.12.2 Guide](https://docs.junit.org/5.12.2/user-guide/)
- [Maven Surefire Plugin](https://maven.apache.org/surefire/maven-surefire-plugin/)

**Ghi nhớ:** Tối ưu testability không phải bỏ DI hay bỏ Spring, mà là **đặt dependency vào hợp đồng rõ ràng của đối tượng**, dùng Spring để lắp ráp graph và chỉ dựng container trong những test thật sự cần xác nhận Spring wiring.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dependency-injection-005">Tiêm qua phương thức cấu hình có nhiều tham số</a>

<details>
<summary>Xem chi tiết</summary>

# Tiêm qua phương thức cấu hình có nhiều tham số trong Spring Core Container

## 1. Mục tiêu của bài

Một bean thường cần **nhiều cộng tác viên** cùng lúc: ví dụ cần repository để đọc dữ liệu, formatter để định dạng, audit sink để ghi nhật ký và metrics sink để ghi số liệu. Spring Core Container có thể tự tìm bean thích hợp và **truyền từng tham số** vào một phương thức cấu hình (*configuration method*) được đánh dấu `@Autowired`.

Điều đáng chú ý là phương thức đó:

- **Không bắt buộc có tên `set...`** như JavaBeans setter. Có thể tên `configure`, `prepare` hay `attachInfrastructure`.
- Có thể nhận **nhiều tham số với nhiều kiểu khác nhau** trong một lời gọi.
- Được Spring gọi trong **quá trình cấu hình bean**, không phải mỗi khi ứng dụng gọi một nghiệp vụ.
- Có mặc định `required=true`; nếu dùng `required=false` trên phương thức, Spring có thể **bỏ qua cả phương thức** khi một dependency cần thiết không tồn tại.
- Khác với `@Bean`: phương thức `@Bean` là **factory method tạo/công bố một bean**, còn phương thức `@Autowired` thông thường **cấu hình một bean đã được tạo**.

Bài này đi từ cơ chế, trường hợp nhiều tham số, cách chọn bean, sự khác nhau giữa dependency bắt buộc và tùy chọn, rồi tới ví dụ Spring Framework 6.2 + Java 17 độc lập có lệnh Maven và stdout cụ thể.

~~~text
ApplicationContext
    |
    | (1) gọi factory method có nhiều tham số
    |     @Bean OrderEngine orderEngine(
    |           Repository repository,
    |           Formatter formatter,
    |           @Value(...) String label)
    v
 OrderEngine instance (đã có repository, formatter, label)
    |
    | (2) BeanPostProcessor xử lý @Autowired
    |     configureExtras(AuditSink audit, MetricSink metrics)
    |            |
    |            +-- cả hai có sẵn -> gọi method một lần
    |            +-- thiếu một, required=false -> không gọi method
    |
    v
 bean sẵn sàng dùng -> execute(orderId)
~~~

Vì vậy cần phân biệt **tạo object**, **tiêm tham số để cấu hình**, và **thực thi method nghiệp vụ**. Đây là ba giai đoạn khác nhau.

## 2. `@Autowired` trên phương thức bất kỳ có nhiều tham số

### 2.1. Cú pháp cơ bản

~~~java
public final class ReportService {
    private Formatter formatter;
    private AuditSink auditSink;

    @Autowired
    public void configure(Formatter formatter, AuditSink auditSink) {
        this.formatter = formatter;
        this.auditSink = auditSink;
    }
}
~~~

Đây là **mã minh họa**, chưa runnable độc lập vì các kiểu `Formatter`, `AuditSink` và import `@Autowired` chưa được cung cấp trong khối này. Ý nghĩa quan trọng:

1. Spring tạo một `ReportService` theo bean definition.
2. `AutowiredAnnotationBeanPostProcessor` nhận diện method `configure`.
3. Spring giải quyết **mỗi parameter** theo metadata/kiểu và những qualifier phù hợp.
4. Nếu thỏa mãn các dependency cần thiết, container gọi `configure(formatterBean, auditBean)` để gán trạng thái.
5. Bean đi qua những bước initialization/lifecycle còn lại; sau đó mới nên đưa vào luồng sử dụng thông thường.

Vì method chỉ cấu hình object, thường sử dụng `void` return type. Nó có thể khai báo visibility phù hợp, không bắt buộc public, nhưng nên tránh những phương thức static cho mục đích này và tránh lạm dụng reflection/access khó kiểm soát.

**Đừng nhầm đây với mỗi lần gọi ứng dụng:** Spring **không** tự gọi `configure` trước từng lần `service.generate()`. Với singleton bean thông thường, cấu hình injection hoàn tất một lần trong quá trình tạo bean. Với prototype, mỗi object được tạo có quy trình cấu hình riêng.

### 2.2. Setter injection là một trường hợp của method injection

Setter quen thuộc:

~~~java
@Autowired
public void setFormatter(Formatter formatter) {
    this.formatter = formatter;
}
~~~

đơn giản là **method injection có một tham số**, tuân theo quy ước tên JavaBeans. Method injection có thể nhận nhiều tham số:

~~~java
@Autowired
public void configure(Formatter formatter,
                      AuditSink auditSink,
                      MetricsSink metricsSink) {
    this.formatter = formatter;
    this.auditSink = auditSink;
    this.metricsSink = metricsSink;
}
~~~

Tên `configure` không vi phạm quy tắc `@Autowired`; annotation cho Spring biết đó là injection point. Mã ở hai khối này chỉ để so sánh cấu trúc; các interface/field cần được định nghĩa khi tạo project thực.

Một phương thức có nhiều tham số phù hợp khi các dependency **cùng cấu thành một bước cấu hình có nghĩa**, ví dụ một nhóm bộ phận cần được gắn đồng thời. Không nên gom mọi cộng tác viên không liên quan vào một method chỉ để giảm số lượng setter hoặc che những vấn đề về trách nhiệm của lớp.

## 3. Required semantics: phương thức nhiều tham số là một injection point

### 3.1. `@Autowired` mặc định yêu cầu dependency

~~~java
@Autowired
public void configure(Repository repository,
                      Formatter formatter) {
    this.repository = repository;
    this.formatter = formatter;
}
~~~

Mặc định là `required=true`. Khi Spring không tìm được một tham số cần thiết, bean creation thường thất bại với lỗi dependency; nó không gọi method với một đối số ngẫu nhiên hay lặng lẽ bỏ qua injection bắt buộc.

Một số lỗi cần phân biệt:

- **Không có ứng viên** phù hợp: có thể phát sinh `UnsatisfiedDependencyException` với nguyên nhân liên quan tới `NoSuchBeanDefinitionException`.
- **Có nhiều bean cùng kiểu nhưng không phân giải được**: thường do `NoUniqueBeanDefinitionException` trong chuỗi nguyên nhân.
- **Bean ứng viên tồn tại nhưng constructor/factory của chính bean đó lỗi**: vẫn có thể làm việc tạo bean thất bại.
- **Method body ném exception khi được gọi**: không phải lỗi “thiếu dependency”; Spring có thể bọc lỗi như bean creation failure.

Nên đọc nguyên nhân gốc trong stack trace, không sửa cấu hình chỉ dựa vào tên exception ngoài cùng.

### 3.2. `@Autowired(required = false)`: bỏ qua toàn bộ method

~~~java
@Autowired(required = false)
public void configureExtras(AuditSink audit,
                            MetricSink metrics) {
    this.audit = audit;
    this.metrics = metrics;
    this.extrasConfigured = true;
}
~~~

Đây là **đoạn minh họa**, nằm trong chương trình đầy đủ ở mục 6. Quy tắc đặc biệt quan trọng:

- Nếu có **cả hai** bean, Spring có thể gọi method và truyền đủ hai argument.
- Nếu **thiếu `MetricSink`** nhưng có `AuditSink`, Spring **không gọi `configureExtras`** và không “gọi một nửa phương thức” với `null`.
- Nếu không có cả hai, method cũng bị bỏ qua.
- Những field ban đầu vẫn giữ giá trị mặc định của object, ví dụ `extrasConfigured=false`.
- `required=false` **không** giải quyết mơ hồ do hai bean ứng viên `MetricSink` và cũng không nuốt mọi lỗi khởi tạo bean.

Hãy xem nhóm hai dependency này như **một điểm cấu hình tùy chọn có tính nguyên khối** (*all-or-nothing*). Nếu muốn audit hoạt động dù metrics thiếu, **đừng** đặt cả hai vào một method non-required rồi kỳ vọng audit tự có; thay đổi cách thiết kế injection.

| Ngữ cảnh | `AuditSink` | `MetricSink` | `configureExtras` với `required=false` |
| --- | --- | --- | --- |
| Đầy đủ | Có | Có | Được gọi, gắn cả hai |
| Thiếu metrics | Có | Không | Bị bỏ qua hoàn toàn |
| Thiếu audit | Không | Có | Bị bỏ qua hoàn toàn |
| Thiếu cả hai | Không | Không | Bị bỏ qua hoàn toàn |

Bảng giả định các kiểu mỗi bên có một bean phù hợp, không có lỗi khác; nó không hứa về cơ chế mơ hồ khi nhiều candidates.

### 3.3. Khi nhiều tùy chọn độc lập, nên tách method

Ví dụ:

~~~java
@Autowired(required = false)
public void setAuditSink(AuditSink sink) {
    this.auditSink = sink;
}

@Autowired(required = false)
public void setMetricSink(MetricSink sink) {
    this.metricSink = sink;
}
~~~

Hai injection points độc lập: chỉ có audit thì setter audit vẫn được gọi; thiếu metric chỉ khiến setter metric bị bỏ qua. Cách này phù hợp nếu hai khả năng thực sự không phụ thuộc nhau.

Còn nếu hai dependency phải được cài chung để bảo đảm invariant, một method `configureExtras(audit, metrics)` là thiết kế có chủ đích, nhưng phải có **fallback hành vi** khi method bị bỏ qua.

## 4. Tùy chọn trên từng tham số: `Optional<T>`, `@Nullable` và provider

### 4.1. Một method vẫn được gọi khi tham số riêng lẻ optional

~~~java
@Autowired
public void configure(AuditSink audit,
                      java.util.Optional<MetricSink> metrics) {
    this.audit = audit;
    this.metricsEnabled = metrics.isPresent();
}
~~~

Spring có hỗ trợ `Optional<MetricSink>` như một injection point không bắt buộc. Khi có `AuditSink` nhưng thiếu `MetricSink`, method **vẫn có thể được gọi**, với `Optional.empty()` ở tham số metrics. Ngược lại, nếu **chính `AuditSink`** thiếu, method mặc định vẫn không thỏa requirement vì tham số đó bắt buộc.

Đây là khác biệt quan trọng với toàn method `@Autowired(required=false)`:

| Tình trạng | Method `required=false` có 2 tham số thường | Method `@Autowired` với `AuditSink, Optional<MetricSink>` |
| --- | --- | --- |
| Có audit và metrics | Gọi | Gọi, `Optional` chứa metrics |
| Có audit, thiếu metrics | Bỏ qua toàn method | Gọi, `Optional.empty()` |
| Thiếu audit, có metrics | Bỏ qua toàn method | Không thỏa dependency bắt buộc của audit |

Mã minh họa sẽ được triển khai trên một bean `OptionalObserver` trong chương trình chạy mục 6, để bạn so sánh kết quả cùng lúc.

### 4.2. `@Nullable` và `ObjectProvider<T>`

Trong một số phiên bản Spring, có thể khai báo parameter nullable theo annotation được Spring hỗ trợ để biểu đạt tính tùy chọn. Khi dùng Spring Framework 6.x, cần chọn đúng annotation nullability phù hợp module/phiên bản thực tế, không tùy tiện gắn một annotation bất kỳ rồi mong Spring hiểu.

`ObjectProvider<MetricSink>` là lựa chọn khác nếu **muốn tra dependency lúc cần**, thay vì chốt reference khi cấu hình:

~~~java
@Autowired
public void configure(ObjectProvider<MetricSink> metricsProvider) {
    this.metricsProvider = metricsProvider;
}
~~~

Đây là đoạn minh họa, cần import `org.springframework.beans.factory.ObjectProvider` và field tương ứng. Code có thể gọi `metricsProvider.getIfAvailable()` ở thời điểm xử lý để yêu cầu bean nếu có. Với nhiều candidates, phải xem xét quy tắc resolution; provider không tự tạo ra một ứng viên “đúng ý” khi cấu hình mơ hồ.

Dùng `Optional<T>` khi chỉ muốn một quyết định có/không tại lúc cấu hình. Dùng provider khi cần cấp bean theo nhu cầu, prototype hoặc một cơ chế lookup động có chủ đích; tránh tạo abstraction phức tạp nếu chỉ cần một tham số ổn định.

## 5. Phân biệt `@Autowired` config method và `@Bean` factory method

Hai loại method cùng nhận nhiều tham số nhưng **khác vai trò**.

### 5.1. `@Autowired` instance method

~~~java
public final class OrderEngine {
    @Autowired(required = false)
    public void configureExtras(AuditSink audit, MetricSink metrics) {
        // Lưu reference để dùng sau.
    }
}
~~~

Method thuộc **bean đã được tạo**, do Spring gọi để cấu hình object. Nó **không đăng ký một bean mới** chỉ vì được đánh dấu `@Autowired`; giá trị trả về (nếu khai báo) không phải bean product giống `@Bean`.

### 5.2. `@Bean` factory method nhận nhiều tham số

~~~java
@Configuration(proxyBeanMethods = false)
public class AppConfig {
    @Bean
    public OrderEngine orderEngine(Repository repository,
                                   Formatter formatter,
                                   @Value("${app.label:BASE}") String label) {
        return new OrderEngine(repository, formatter, label);
    }
}
~~~

Đây là trích đoạn từ chương trình hoàn chỉnh mục 6. Spring:

1. Xác định một bean definition qua `@Bean` method `orderEngine`.
2. Giải quyết `Repository` và `Formatter` bằng type và các metadata chọn ứng viên.
3. Resolve placeholder `${app.label:BASE}` thành chuỗi cấu hình có default `BASE`.
4. Gọi factory method với **ba argument đã xác định**; instance trả về trở thành Spring-managed bean `orderEngine`.
5. Sau đó, nếu bean class có `@Autowired` methods, các phương thức cấu hình được xử lý trong lifecycle thích hợp.

**Không cần thêm `@Autowired` lên `@Bean` method** để Spring truyền tham số. `@Bean` đã khai báo điểm tạo bean và cơ chế giải quyết factory arguments.

### Bảng phân biệt

| Tính chất | `@Autowired` config method | `@Bean` factory method |
| --- | --- | --- |
| Nằm ở đâu? | Một bean do container quản lý | Lớp `@Configuration` hoặc component cấu hình phù hợp |
| Mục đích | Thiết lập dependency lên object đã tạo | Tạo hoặc trả object để công bố thành bean |
| Có thể nhận nhiều tham số? | Có | Có |
| Tên method phải `setX`? | Không | Không |
| Return value tạo bean? | Không | **Có**, product của `@Bean` |
| `required=false` áp dụng thế nào? | Cả method có thể bị bỏ qua nếu thiếu một dependency | Thông thường factory parameters là requirements; tùy chọn thể hiện qua `Optional`/provider/default phù hợp |
| Khi nào chạy? | Trong giai đoạn cấu hình bean | Khi container cần tạo bean theo scope/lazy |
| Bản thân gọi method nghiệp vụ mỗi request? | Không | Không |

Đừng gắn `@Autowired(required=false)` lên một `@Bean` factory method chỉ để giải quyết việc thiếu bean: cơ chế resolution của factory method không nên bị hiểu như một arbitrary configuration method. Thay vào đó dùng `Optional<T>` hoặc cấu hình conditional/factory phù hợp.

## 6. Ứng dụng Spring Framework 6.2 / Java 17 chạy được

Ví dụ có **hai `ApplicationContext` độc lập**. Cả hai chứa repository, formatter và audit bean:

- **Context 1:** không có metrics bean. Phương thức `OrderEngine.configureExtras(AuditSink, MetricSink)` được gắn `@Autowired(required=false)`, vì thiếu metrics nên **không chạy**. Ngược lại, `OptionalObserver.configure(AuditSink, Optional<MetricSink>)` vẫn chạy và nhận `Optional.empty()`.
- **Context 2:** đăng ký thêm metrics bean và đưa property `app.label=CUSTOM` vào environment trước khi refresh. Cả hai phương thức đều được gọi; use case in audit và metric trước khi in kết quả.

### 6.1. Cấu trúc project

Yêu cầu **JDK 17+**, Maven và kết nối tới Maven repository ở lần tải dependencies đầu tiên. Không dùng Spring Boot, database, web server hoặc HTTP.

~~~text
multi-arg-injection/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── MultiArgumentDemo.java
~~~

Tạo file `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>multi-arg-injection</artifactId>
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
                    <mainClass>demo.MultiArgumentDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 6.2. File Java hoàn chỉnh

Tạo `src/main/java/demo/MultiArgumentDemo.java`:

~~~java
package demo;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;

public final class MultiArgumentDemo {
    private MultiArgumentDemo() {}

    public interface Repository {
        String find(String id);
    }

    public static final class DemoRepository implements Repository {
        @Override
        public String find(String id) {
            return "ROW(" + id + ")";
        }
    }

    public interface Formatter {
        String format(String text);
    }

    public static final class BracketFormatter implements Formatter {
        @Override
        public String format(String text) {
            return "[" + text + "]";
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

    public interface MetricSink {
        void record(String id);
    }

    public static final class ConsoleMetricSink implements MetricSink {
        @Override
        public void record(String id) {
            System.out.println("METRIC " + id);
        }
    }

    public static final class OrderEngine {
        private final Repository repository;
        private final Formatter formatter;
        private final String label;
        private AuditSink audit;
        private MetricSink metrics;
        private boolean extrasConfigured;

        public OrderEngine(Repository repository,
                           Formatter formatter,
                           String label) {
            this.repository = Objects.requireNonNull(repository);
            this.formatter = Objects.requireNonNull(formatter);
            this.label = Objects.requireNonNull(label);
        }

        // Method có tên bất kỳ và 2 tham số.
        // Nếu thiếu dù chỉ một dependency, method bị bỏ qua.
        @Autowired(required = false)
        public void configureExtras(AuditSink audit, MetricSink metrics) {
            this.audit = Objects.requireNonNull(audit);
            this.metrics = Objects.requireNonNull(metrics);
            this.extrasConfigured = true;
        }

        public boolean extrasConfigured() {
            return extrasConfigured;
        }

        public String execute(String id) {
            String row = repository.find(id);
            if (extrasConfigured) {
                audit.record(id);
                metrics.record(id);
            }
            return label + ":" + formatter.format(row);
        }
    }

    public static final class OptionalObserver {
        private boolean methodCalled;
        private boolean metricsPresent;

        @Autowired
        public void configure(AuditSink audit,
                              Optional<MetricSink> metrics) {
            Objects.requireNonNull(audit);
            this.methodCalled = true;
            this.metricsPresent = metrics.isPresent();
        }

        public boolean methodCalled() {
            return methodCalled;
        }

        public boolean metricsPresent() {
            return metricsPresent;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class CoreConfig {
        @Bean
        public Repository repository() {
            return new DemoRepository();
        }

        @Bean
        public Formatter formatter() {
            return new BracketFormatter();
        }

        @Bean
        public AuditSink auditSink() {
            return new ConsoleAuditSink();
        }

        // Factory method nhiều tham số tự được Spring resolve.
        @Bean
        public OrderEngine orderEngine(Repository repository,
                                       Formatter formatter,
                                       @Value("${app.label:BASE}") String label) {
            return new OrderEngine(repository, formatter, label);
        }

        @Bean
        public OptionalObserver optionalObserver() {
            return new OptionalObserver();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreConfig.class)
    public static class WithMetricsConfig {
        @Bean
        public MetricSink metricSink() {
            return new ConsoleMetricSink();
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(CoreConfig.class)) {
            OrderEngine engine = context.getBean(OrderEngine.class);
            OptionalObserver observer =
                    context.getBean(OptionalObserver.class);

            System.out.println("-- no metrics --");
            System.out.println("ENGINE_METHOD " + engine.extrasConfigured());
            System.out.println("OBSERVER_METHOD " + observer.methodCalled());
            System.out.println("OBSERVER_METRICS " + observer.metricsPresent());
            System.out.println("RESULT " + engine.execute("A1"));
        }

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            // Property phải được cung cấp trước refresh.
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("demo",
                            Map.of("app.label", "CUSTOM")));

            context.register(WithMetricsConfig.class);
            context.refresh();

            OrderEngine engine = context.getBean(OrderEngine.class);
            OptionalObserver observer =
                    context.getBean(OptionalObserver.class);

            System.out.println("-- with metrics --");
            System.out.println("ENGINE_METHOD " + engine.extrasConfigured());
            System.out.println("OBSERVER_METHOD " + observer.methodCalled());
            System.out.println("OBSERVER_METRICS " + observer.metricsPresent());
            System.out.println("RESULT " + engine.execute("B2"));
        }
    }
}
~~~

### 6.3. Biên dịch và chạy

Từ thư mục `multi-arg-injection` có `pom.xml`:

~~~bash
mvn -version
mvn -q compile exec:java
~~~

Maven có thể in tiến trình tải dependencies hoặc log tùy cấu hình. **Các dòng stdout do logic `MultiArgumentDemo` chủ động in, theo đúng thứ tự:**

~~~text
-- no metrics --
ENGINE_METHOD false
OBSERVER_METHOD true
OBSERVER_METRICS false
RESULT BASE:[ROW(A1)]
-- with metrics --
ENGINE_METHOD true
OBSERVER_METHOD true
OBSERVER_METRICS true
AUDIT B2
METRIC B2
RESULT CUSTOM:[ROW(B2)]
~~~

`mvn -version` còn in thông tin Maven/JDK. Khối trên mô tả **dòng do code ví dụ in**, không hứa rằng toàn bộ output terminal chỉ có các dòng đó.

### 6.4. Phân tích từng bước kết quả

**Context 1 — không có metrics:**

1. Spring đọc `CoreConfig`, tạo ba bean dependency `Repository`, `Formatter`, `AuditSink` và các bean service theo `@Bean`.
2. Method `orderEngine(Repository, Formatter, String)` nhận repository, formatter và label từ placeholder. Không có `app.label` nên `@Value("${app.label:BASE}")` trả `BASE`.
3. Spring tạo `OrderEngine` rồi xử lý `@Autowired(required=false)` trên `configureExtras(AuditSink, MetricSink)`.
4. Vì **không có bean `MetricSink`**, toàn method bị bỏ qua, `extrasConfigured` giữ mặc định `false`. Bean audit có tồn tại nhưng **không được gắn một mình** vào engine.
5. Spring tạo `OptionalObserver` rồi gọi `configure(AuditSink, Optional<MetricSink>)`; `AuditSink` có, `MetricSink` không, nên truyền `Optional.empty()` và đặt `methodCalled=true`, `metricsPresent=false`.
6. Lệnh `execute("A1")` lấy `ROW(A1)`, áp dụng formatter thành `[ROW(A1)]`, trả `BASE:[ROW(A1)]` mà không in audit hay metric.

**Context 2 — có metrics:**

1. `MapPropertySource` cung cấp `app.label=CUSTOM` trước `refresh()`, nên factory method nhận `CUSTOM`.
2. `WithMetricsConfig` import `CoreConfig` và bổ sung bean `MetricSink`.
3. Lần này cả `AuditSink` và `MetricSink` đều tồn tại, nên Spring gọi `configureExtras(...)`; `extrasConfigured=true`.
4. `OptionalObserver.configure(...)` được gọi, và `Optional<MetricSink>` chứa giá trị, nên `metricsPresent=true`.
5. Lệnh `execute("B2")` gọi audit và metric **trước khi trả chuỗi kết quả**. Hai dòng `AUDIT B2`, `METRIC B2` xuất hiện trước dòng `RESULT CUSTOM:[ROW(B2)]` do `main` in.

Hai context không chia sẻ các singleton instance mặc định; chúng chạy tuần tự và được đóng riêng. Không có database transaction, HTTP server hay Spring AOP trong ví dụ.

**Lưu ý:** Chương trình không in dòng nào trong constructor hoặc các `@Bean` factory methods, vì thế stdout không phụ thuộc vào thứ tự tạo singleton không liên quan.

## 7. Dùng `@Qualifier` và `@Primary` với nhiều tham số

Khi hai bean cùng thực hiện một interface, chỉ type có thể chưa đủ để chọn:

~~~java
@Bean("fastFormatter")
Formatter fastFormatter() { return new FastFormatter(); }

@Bean("safeFormatter")
Formatter safeFormatter() { return new SafeFormatter(); }
~~~

Đây là ví dụ **minh họa, chưa runnable**, thiếu hai class. Nếu method nhận một `Formatter` nhưng có nhiều candidates không xác định được lựa chọn, có thể thất bại vì mơ hồ.

Cách chọn rõ ngay trên **từng tham số**:

~~~java
@Autowired
public void configure(@Qualifier("safeFormatter") Formatter formatter,
                      AuditSink audit) {
    this.formatter = formatter;
    this.audit = audit;
}
~~~

Đoạn này cần import `org.springframework.beans.factory.annotation.Qualifier` và các field/interface thích hợp.

- `@Qualifier("safeFormatter")` giúp giới hạn ứng viên của tham số formatter.
- `AuditSink` vẫn được resolve độc lập theo type/qualifier của nó.
- `@Primary` đặt trên một bean candidate có thể cung cấp ưu tiên mặc định cho single-valued injection.
- Khi có nhiều bean primary cùng phù hợp hoặc qualifier không khớp, phải xem nguyên nhân cụ thể; không nên dựa vào thứ tự khai báo bean.

**Với nhiều tham số, mỗi tham số có metadata lựa chọn riêng.** Một annotation qualifier trên tham số formatter không quyết định cách giải quyết tham số audit.

Một chi tiết cần biết: `@Resource` theo JSR-250 chủ yếu dùng cho field hoặc setter JavaBeans với **một tham số**; nó không phải công cụ thay thế trực tiếp cho một arbitrary method `@Autowired` nhiều tham số. Spring hỗ trợ tham số qualifiers trong config method phù hợp hơn cho tình huống này.

## 8. Method injection với collection và nhiều implementation

Spring có thể inject các nhóm bean theo kiểu:

~~~java
@Autowired
public void configureAll(List<Formatter> formatters,
                         Map<String, AuditSink> audits) {
    this.formatters = formatters;
    this.audits = audits;
}
~~~

Đây là **đoạn minh họa**, cần `java.util.List`, `java.util.Map` và các field/class thực tế. Khi đủ điều kiện, `List<Formatter>` nhận các formatter beans phù hợp; `Map<String, AuditSink>` dùng bean names làm keys và bean instances làm values.

Những điểm cần lưu ý:

- List, array, Set và Map có semantics riêng; không dựa vào thứ tự Map nếu chưa có quy tắc được bảo đảm.
- `@Order`/`Ordered` có thể ảnh hưởng tới thứ tự injection của collection phù hợp, nhưng **không phải metadata chọn primary cho một tham số duy nhất**.
- Với `@Autowired(required=true)` trên một method thông thường, collection rỗng có thể không thỏa requirement nếu không có bean phù hợp; có ngoại lệ ở một số đường resolution của constructor/factory method. Vì vậy nếu collection có thể rỗng, hãy thiết kế/kiểm tra theo API và phiên bản Spring thực tế.
- Một multi-argument config method có collection và scalar required vẫn phải giải quyết các tham số thích hợp theo cùng injection point.

**Không tự thêm một `@Bean List<Formatter>` chỉ để dùng nhiều formatter** khi mục tiêu thực tế là “inject tất cả beans thuộc interface”; Spring hỗ trợ collection injection trực tiếp trong tình huống tương ứng.

## 9. Thời điểm Spring gọi config method và vòng đời

`@Autowired` annotation được xử lý bởi bean post-processing của Spring:

~~~text
instantiate bean (constructor hoặc factory)
    |
    +--> populate properties / @Autowired method injection
    |
    +--> aware callbacks, @PostConstruct / init callbacks phù hợp
    |
    +--> bean sẵn sàng cho getBean / injection tiếp theo
~~~

Sơ đồ tổng quát, không phải toàn bộ thứ tự extension points trong mọi cấu hình.

Một số hệ quả:

1. **Method injection là bước cấu hình**: method nên thiết lập reference/khởi tạo cấu hình cần thiết, không phải chạy ca sử dụng của khách hàng.
2. **Thời điểm có thể sớm**: non-lazy singleton thường được tạo lúc `ApplicationContext.refresh()`, nên config method cũng có thể chạy từ startup.
3. **Scope quan trọng**: prototype bean mới có quy trình injection riêng ở mỗi lần được yêu cầu; singleton thông thường được cấu hình một lần trong container.
4. **Không tự chạy trên object tạo tay**: `new OrderEngine(...)` ngoài Spring container không kích hoạt `@Autowired` method. Unit test có thể tự gọi method cần cấu hình.
5. **Đừng tạo dependency cycle một cách vô ý**: nếu config method của A đòi B, còn B lại đòi A, bạn có thể gặp vòng phụ thuộc và lỗi khởi tạo; đừng dùng setter chỉ để che lỗi thiết kế.
6. **Không hot-reload tự động**: việc thay bean definition/PropertySource sau khi bean đã được cấu hình không có nghĩa Spring tự gọi lại mọi config method để cập nhật bean.

Phương thức nhiều tham số lý tưởng nên có tác dụng ngắn, xác định và không chứa I/O khó kiểm soát. Nếu là bean singleton dùng nhiều thread, tránh thay các field cấu hình từ thread khác sau khi bean đã chạy mà không thiết kế concurrency đúng.

## 10. Vì sao constructor injection vẫn được khuyến nghị cho dependency bắt buộc?

Nếu repository/formatter là điều kiện cần để xử lý request, giữ chúng trong constructor:

~~~java
public OrderEngine(Repository repository,
                   Formatter formatter,
                   String label) {
    this.repository = Objects.requireNonNull(repository);
    this.formatter = Objects.requireNonNull(formatter);
    this.label = Objects.requireNonNull(label);
}
~~~

Các field constructor có thể `final`; object được tạo là có đủ điều kiện tối thiểu để chạy. Những phần mở rộng audit/metrics được nhóm ở `configureExtras` vì demo muốn minh họa chúng là một **cấu hình bổ sung** có thể có hoặc không.

**Không phải quy tắc cứng rằng constructor tốt còn mọi setter sai**. Điều quan trọng là:

- Required dependency nên hiện rõ trong constructor.
- Optional dependency có thể đi qua setter/config method hoặc `Optional<T>`.
- Những dependency cần thiết lập nguyên khối có thể nhóm trong một method nhiều tham số, với semantics rõ.
- Một class có quá nhiều dependencies có thể đang ôm nhiều nhiệm vụ; cân nhắc tách responsibilities, không chỉ “chuyển hết sang config method”.

Phương thức được đánh dấu `@Autowired` không buộc domain phải khởi tạo Spring context trong test: đó vẫn là method Java bình thường, có thể gọi trực tiếp nếu cần.

## 11. Những hiểu nhầm và lỗi thường gặp

| Hiểu nhầm / lỗi | Cách hiểu đúng |
| --- | --- |
| “`@Autowired` chỉ dùng được cho constructor và setter.” | Có thể áp dụng trên arbitrary instance method nhận nhiều tham số. |
| “Method tên `prepare` sẽ bị bỏ qua vì không bắt đầu bằng `set`.” | Tên method không phải điều kiện của `@Autowired`. |
| “Method nhiều tham số được Spring gọi riêng một lần cho từng tham số.” | Spring giải quyết argument rồi gọi method như một lời gọi Java. |
| “`required=false` khiến dependency thiếu được truyền `null` và method vẫn chạy.” | Nếu thiếu một dependency, non-required method có thể bị bỏ qua **toàn bộ**. |
| “`Optional<MetricSink>` khiến toàn method mặc định tùy chọn.” | Chỉ parameter tương ứng optional; dependency khác vẫn có thể required. |
| “`required=false` xử lý luôn lỗi nhiều bean mơ hồ.” | Cần qualifier/primary hoặc lựa chọn rõ. |
| “`@Bean` method nhiều tham số cần `@Autowired` để được inject.” | Spring tự resolve factory method arguments. |
| “`@Autowired` config method tạo thêm một bean.” | Nó cấu hình bean đang tồn tại; `@Bean` method mới khai báo bean product. |
| “`@Value` tự lấy từ `application.properties` như Spring Boot.” | Spring Framework thuần cần environment/property sources phù hợp; ví dụ dùng MapPropertySource rõ ràng. |
| “`@Autowired` chạy trên bất cứ object nào tạo bằng `new`.” | Annotation xử lý bởi Spring container, không phải JVM tự gọi. |
| “Có bean thiếu nhưng vẫn cho method chạy một nửa.” | Một method invocation là nguyên khối; Spring không tự gọi với danh sách tham số thiếu. |
| “Một config method có 20 tham số là thiết kế tốt vì ít method.” | Có thể là dấu hiệu class làm quá nhiều việc, cần tách responsibility. |
| “Qualifier chọn formatter cũng chọn luôn audit.” | Từng tham số có metadata resolution riêng. |
| “Bean singleton tự động thread-safe.” | Injection/scope không tự tạo đồng bộ cho mutable state. |
| “Đặt `@Autowired` trên business method thì Spring sẽ gọi trước từng request.” | Không, nó là config injection point, không phải request interceptor. |

### Các lỗi chẩn đoán phổ biến

- `NoSuchBeanDefinitionException`: dependency type được yêu cầu mà context không có.
- `NoUniqueBeanDefinitionException`: nhiều candidates cho một tham số và không thể chọn.
- `UnsatisfiedDependencyException`: injection point không thỏa.
- `BeanCreationException`: lỗi tạo/configure/init bean, thường bọc nguyên nhân bên trong.
- `BeanCurrentlyInCreationException`: vòng phụ thuộc chưa được giải quyết.
- `IllegalArgumentException` từ config method: ví dụ setter kiểm tra tham số hoặc business invariant không hợp lệ.

Đọc **bean name và tên injection point** trong stack trace trước khi thay cấu hình. Không đánh dấu toàn bộ method `required=false` chỉ để che một dependency thực sự bắt buộc.

## 12. Bài tập kèm đáp án

**Bài 1.** Method `@Autowired(required=false) void configure(AuditSink a, MetricSink m)` có audit bean nhưng không có metrics bean. Spring sẽ gọi method với `m=null` hay không gọi?

**Đáp án:** Không gọi method trong trường hợp thiếu dependency; những field ban đầu giữ trạng thái mặc định. Đó là semantics non-required method, không phải partial injection.

**Bài 2.** Method `@Autowired void configure(AuditSink a, Optional<MetricSink> m)` với audit có, metrics thiếu sẽ xảy ra điều gì?

**Đáp án:** Method được gọi với audit bean và `Optional.empty()` cho metrics, nếu không có lỗi khác. Đây là optionality **trên một parameter**.

**Bài 3.** Một method trong `@Configuration` là `@Bean Foo foo(Bar bar, Baz baz)`. Có phải gắn `@Autowired` lên nó mới nhận đủ hai dependency?

**Đáp án:** Không. Container nhận biết factory method là điểm tạo bean qua `@Bean` và tự giải quyết các tham số thích hợp.

**Bài 4.** Vì sao context đầu tiên của demo in `ENGINE_METHOD false` nhưng `OBSERVER_METHOD true`?

**Đáp án:** Engine config method required=false thiếu `MetricSink` nên bị bỏ qua; observer method có tham số `Optional<MetricSink>` nên vẫn được gọi với `Optional.empty()`.

**Bài 5.** Vì sao `AUDIT B2` và `METRIC B2` xuất hiện trước dòng `RESULT CUSTOM:[ROW(B2)]`?

**Đáp án:** `OrderEngine.execute` gọi hai collaborator trước khi trả chuỗi. `main` chỉ in kết quả sau khi method trả về.

**Bài 6.** Nếu bean `Formatter` có hai implementation và cả hai đều được đăng ký, giải quyết thế nào?

**Đáp án:** Cung cấp một quy tắc lựa chọn có chủ đích, ví dụ qualifier ngay trên tham số hoặc bean primary phù hợp. Không dựa vào thứ tự method trong source file.

**Bài 7.** Nếu một config method lấy `List<AuditSink>`, nó có giống một tham số `AuditSink` không?

**Đáp án:** Không. Collection injection có thể nhận nhiều beans phù hợp; còn tham số đơn cần chọn đúng một candidate.

**Bài 8.** Nếu `new OrderEngine(repo, fmt, label)` bên ngoài Spring, `@Autowired(required=false) configureExtras(...)` có tự chạy không?

**Đáp án:** Không. Chỉ annotation trong class chưa đủ. Nếu cần cấu hình thủ công, gọi method hoặc dùng factory thích hợp.

**Bài 9.** Để hai tham số audit và metric tùy chọn **độc lập** với nhau, nên thiết kế sao?

**Đáp án:** Chia thành hai injection methods non-required riêng, hoặc biểu đạt tính tùy chọn độc lập bằng `Optional<T>`/provider tùy trường hợp.

## 13. Checklist cho dự án

- [ ] Phân biệt `@Autowired` configuration method với `@Bean` factory method.
- [ ] Đảm bảo bean cần được config đã đăng ký trong Spring context.
- [ ] Method được đánh dấu `@Autowired` là instance method có signature rõ ràng; tên không cần `set...`.
- [ ] Kiểm tra tất cả parameter types có ứng viên tương ứng.
- [ ] Khi có nhiều ứng viên, chọn bằng `@Qualifier`/`@Primary` đúng tham số.
- [ ] Dùng `required=true` (mặc định) cho configuration thực sự bắt buộc.
- [ ] Dùng `required=false` khi **cả nhóm dependencies** có thể vắng mặt mà object vẫn hợp lệ.
- [ ] Không kỳ vọng method non-required nhận `null` cho argument thiếu.
- [ ] Nếu từng tham số tùy chọn độc lập, dùng `Optional<T>` hoặc nhiều injection points.
- [ ] Cho dependency bắt buộc vào constructor khi hợp lý để tránh object ở trạng thái thiếu điều kiện.
- [ ] Đối với `@Bean` có nhiều parameter, để container tự resolve thay vì gọi thủ công từng `getBean`.
- [ ] Nếu có property `@Value`, validate default và cấu hình property source trước khi refresh.
- [ ] Không giấu use case business trong lifecycle method hoặc in ra output khó dự đoán tại startup.
- [ ] Test context có và không có dependency tùy chọn; kiểm tra stdout và trạng thái thực của bean.
- [ ] Không hot-swap các field của singleton không có chiến lược thread-safety.
- [ ] Đóng context sau khi ứng dụng hoàn tất.

### Tài liệu Spring chính thức

- [Using @Autowired](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html)
- [Using the @Bean Annotation](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html)
- [Fine-tuning Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html)

**Kết luận:** Trong Spring Core Container, một **phương thức cấu hình `@Autowired` nhiều tham số** cho phép thiết lập nhiều collaborator trên một bean bằng một lời gọi method. Với `required=false`, method thường **bị bỏ qua toàn bộ** nếu thiếu dependency; với `Optional<T>`, tính tùy chọn được biểu đạt ở một tham số cụ thể. Ngược lại, **`@Bean` factory method nhiều tham số** tạo bean và tự được Spring cung cấp arguments. Hiểu rõ ba cơ chế này giúp bạn định nghĩa đúng dependency bắt buộc, tùy chọn và nhóm cấu hình nguyên khối, thay vì nhầm việc tạo bean với tiêm cấu hình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dependency-injection-006">Phân biệt tham chiếu bean với giá trị đơn giản</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt tham chiếu bean với giá trị đơn giản — Spring Framework Core Container

## 1. Mục tiêu: xác định chính xác thứ được inject

Trong Spring IoC container, một constructor hoặc factory method có thể nhận nhiều tham số: một số tham số là **bean collaborator** do Spring đăng ký và quản lý; một số khác chỉ là **giá trị cấu hình đơn giản** như chuỗi, số, boolean hoặc thời gian chờ. Chúng đều được truyền vào một phương thức Java, nhưng **ý nghĩa và cơ chế phân giải khác nhau**.

Ví dụ `GreetingService` cần:

- `NameFormatter`: một **đối tượng bean** dùng chung để chuẩn hóa tên.
- `prefix`: chuỗi `"Xin chao"` từ cấu hình.
- `maxChars`: số nguyên `8` từ cấu hình, dùng để giới hạn độ dài tên.

`NameFormatter` là một **dependency tới bean**. `prefix` và `maxChars` là **giá trị**, không tự động trở thành Spring bean chỉ vì chúng xuất hiện trong constructor.

Sau bài này bạn có thể:

1. Phân biệt bean reference, bean name, literal value, external configuration value và bean instance.
2. Nhận biết constructor/factory-method argument nào nên do Spring resolve như bean, argument nào phải được cung cấp từ cấu hình hoặc dữ liệu Java.
3. Sử dụng `@Bean` factory method và `Environment.getRequiredProperty(...)` để truyền đúng collaborator cùng các giá trị `String`/`Integer`.
4. Giải thích `@Value`/property placeholders khác `@Qualifier`/bean lookup.
5. Phân biệt `BeanDefinitionBuilder.addPropertyValue(...)` với `addPropertyReference(...)` và cách `RuntimeBeanReference` được resolve thành object.
6. Xác minh metadata và wiring trên một ví dụ Spring Framework thuần, chạy được với Java 17+.
7. Xử lý lỗi thiếu bean, nhiều bean cùng kiểu, thiếu property, sai kiểu, sai setter, scope và những hiểu nhầm thường gặp.

**Phạm vi bài:** *phân biệt bean reference với simple/configuration value trong cơ chế dependency injection*. Spring Boot, Spring MVC, JPA và các framework ngoài Core Container không cần thiết.

## 2. Khái niệm theo đúng tầng abstraction

| Khái niệm | Ví dụ | Container hiểu thế nào |
| --- | --- | --- |
| Java type | `NameFormatter`, `String`, `int` | Kiểu của tham số/field, chưa tự nói đó là bean hay value |
| Bean name | `"nameFormatter"` | Khóa định danh bean trong registry |
| Bean reference | Bean object được cung cấp cho `NameFormatter formatter` | Một dependency tới đối tượng container quản lý |
| Literal value | `"manual"`, `8` | Dữ liệu trực tiếp được đưa vào constructor/setter |
| External configuration value | Property `greeting.prefix=Xin chao` | Được đọc từ `Environment` hoặc cơ chế value resolution |
| `RuntimeBeanReference` | Metadata trỏ tới bean tên `nameFormatter` | Chưa phải object; Spring resolve khi tạo bean |
| Bean instance | Object `UppercaseFormatter` thực tế | Object đã được tạo, có thể được chia sẻ theo scope |

**Chỉ có tên chưa phải một tham chiếu.** Hai đoạn sau không tương đương:

~~~java
.addPropertyValue("formatter", "nameFormatter")
.addPropertyReference("formatter", "nameFormatter")
~~~

Đây là các **snippet cấu hình**, không phải Java statements độc lập. Dòng đầu truyền literal `String` chứa chữ `nameFormatter` vào property; dòng sau yêu cầu Spring **tìm bean tên `nameFormatter` và inject object** vào property `formatter`. Nếu setter nhận `NameFormatter`, thông thường phải dùng dòng thứ hai.

### Một sơ đồ có hai đường vào khác nhau

~~~mermaid
flowchart LR
    E1["Environment: greeting.prefix"] -->|"String value"| S["greetingService: GreetingService"]
    E2["Environment: greeting.maxChars"] -->|"int value"| S
    B["nameFormatter: NameFormatter"] -->|"bean object"| S
    L["literal: manual"] -->|"String value"| M["manualGreeting: ManualGreeting"]
    B -->|"bean reference"| M
~~~

Sơ đồ dùng chiều mũi tên thể hiện **giá trị/object được cung cấp cho consumer**. Nếu biểu diễn dependency graph theo chiều **consumer → dependency**, cạnh bean tương ứng là `greetingService → nameFormatter` và `manualGreeting → nameFormatter`. Giá trị String/int không nhất thiết là node bean riêng.

## 3. Java constructor nhận bean và value vẫn là một constructor bình thường

Thiết kế Java không phụ thuộc Spring:

~~~java
public final class GreetingService {
    private final NameFormatter formatter; // collaborator
    private final String prefix;            // configuration value
    private final int maxChars;              // configuration value

    public GreetingService(
            NameFormatter formatter, String prefix, int maxChars) {
        this.formatter =
                java.util.Objects.requireNonNull(formatter);
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix required");
        }
        if (maxChars <= 0) {
            throw new IllegalArgumentException("maxChars must be positive");
        }
        this.prefix = prefix;
        this.maxChars = maxChars;
    }
}
~~~

**Snippet minh họa**, thiếu `NameFormatter` và một public class hợp lệ chứa code. Nó cho thấy:

- `formatter` là một **collaborator object**. Spring cần biết có bean nào thỏa kiểu `NameFormatter`.
- `prefix` là **String data**, phải đến từ cấu hình hoặc caller, không mặc nhiên là một bean theo tên.
- `maxChars` là **số nguyên**, cần được đọc/chuyển kiểu và validate.
- Constructor bảo vệ invariant: không null collaborator, prefix có nội dung, maxChars dương. Spring không thay thế validation domain do Java class tự định nghĩa.
- Những field `final` giữ các reference/value không bị gán lại; **không đồng nghĩa deep immutability** hoặc thread safety của collaborator mutable.

Java thuần có thể tạo object nếu tự cấp đủ tham số:

~~~java
NameFormatter formatter = new UppercaseFormatter();
GreetingService service =
        new GreetingService(formatter, "Hello", 12);
~~~

Đây là **manual dependency injection**, chưa dùng Spring. Container chỉ tự động hóa việc chọn/tạo bean và cung cấp các giá trị theo cấu hình mà bạn định nghĩa.

### Đừng gắn nhãn bean cho mọi tham số

Spring không nên được yêu cầu “autowire theo type” một `String` hay một `int` chỉ vì service constructor có chúng. Trong autowiring mặc định, các simple properties như primitive, `String` và `Class` không được tự động autowire như collaborator thông thường theo cùng cách. Để value có ý nghĩa rõ, hãy dùng `Environment`, `@Value`, giá trị literal tại composition root hoặc một configuration object được khai báo rõ ràng.

Có thể cố tình đăng ký một bean kiểu `String`, nhưng đó là quyết định cấu hình riêng, **không biến mọi String trong ứng dụng thành bean**. Tránh tạo hàng chục `@Bean String` vô danh gây nhầm lẫn giữa dữ liệu và service.

## 4. Cách JavaConfig truyền hai loại đầu vào đúng ranh giới

Một lớp cấu hình ví dụ:

~~~java
@Configuration(proxyBeanMethods = false)
class AppConfig {
    @Bean
    NameFormatter nameFormatter() {
        return new UppercaseFormatter();
    }

    @Bean
    GreetingService greetingService(
            NameFormatter formatter, Environment env) {
        String prefix =
                env.getRequiredProperty("greeting.prefix");
        int maxChars =
                env.getRequiredProperty("greeting.maxChars", Integer.class);
        return new GreetingService(formatter, prefix, maxChars);
    }
}
~~~

**Đây là snippet minh họa**, ví dụ đầy đủ nằm ở mục 6. Cách đọc:

1. `@Bean nameFormatter()` tạo/đăng ký một managed bean `NameFormatter`.
2. `@Bean greetingService(NameFormatter formatter, Environment env)` có một collaborator theo type và một tham số `Environment` do Spring cung cấp.
3. Spring resolve bean `NameFormatter`, truyền đúng object vào method tạo service.
4. Factory method đọc hai configuration keys; `prefix` là String, còn `maxChars` được conversion thành `Integer` rồi unbox sang `int`.
5. Factory method tạo service với đầy đủ **bean reference + simple values**. Nếu required key thiếu/không chuyển được, việc tạo bean thất bại thay vì tự động điền null hoặc số không không phù hợp.
6. `@Configuration(proxyBeanMethods=false)` hợp lệ vì factory methods **không trực tiếp gọi lẫn nhau**; Spring thực hiện wiring qua tham số method.

**Không suy ra “`Environment` là simple value”.** `Environment` là abstraction Spring cung cấp vào factory method; những property đọc *từ nó* mới là simple values trong ví dụ. Nó không giống việc một `String` được ngẫu nhiên tìm như bean theo type.

### @Value là một cách khác để cung cấp simple value

Bạn có thể dùng:

~~~java
@Bean
GreetingService greetingService(
        NameFormatter formatter,
        @Value("${greeting.prefix}") String prefix,
        @Value("${greeting.maxChars}") int maxChars) {
    return new GreetingService(formatter, prefix, maxChars);
}
~~~

**Snippet thay thế**, cần import `org.springframework.beans.factory.annotation.Value`, các property sources và cơ chế placeholder resolution phù hợp. Ở đây `@Value("${greeting.prefix}")` có ý định resolve một **giá trị** từ config; `NameFormatter formatter` vẫn là một **bean dependency**. `@Value` còn có thể dùng biểu thức Spring Expression Language theo cấu hình, nhưng không cần trong bài học này.

So sánh:

| Cú pháp | Ý nghĩa |
| --- | --- |
| `@Value("${greeting.prefix}") String prefix` | Resolve cấu hình thành String cho tham số |
| `@Value("${greeting.maxChars}") int maxChars` | Resolve rồi chuyển value sang int phù hợp |
| `@Qualifier("nameFormatter") NameFormatter formatter` | Chọn **bean candidate**, không đọc một property String |
| `environment.getRequiredProperty("greeting.prefix")` | Đọc property rõ ràng, fail nếu key bắt buộc thiếu |
| `@Bean NameFormatter nameFormatter()` | Đăng ký cách tạo **bean object** thay vì một value String |

Cảnh báo: biểu thức placeholder `${key}` cần property source và cấu hình resolution tương thích. Không mặc định việc thiếu key luôn được xử lý giống hệt `Environment.getRequiredProperty(...)` trong mọi kiểu context. Ví dụ runnable sẽ dùng `Environment` để quy tắc **bắt buộc có key** minh bạch.

## 5. BeanDefinition: value và reference nằm ở metadata như thế nào?

Spring có thể lưu cấu hình object không chỉ qua `@Bean` factory methods mà còn dưới dạng **BeanDefinition metadata**:

~~~java
BeanDefinition definition = BeanDefinitionBuilder
        .genericBeanDefinition(ManualGreeting.class)
        .addPropertyValue("label", "manual")
        .addPropertyReference("formatter", "nameFormatter")
        .getBeanDefinition();

context.registerBeanDefinition("manualGreeting", definition);
~~~

Đây là **snippet**; cần các class và context phù hợp ở mục 6. Hai thao tác làm hai việc khác nhau:

- `addPropertyValue("label", "manual")` lưu literal value. Khi bean `manualGreeting` được tạo, Spring tìm property `label` và gọi setter `setLabel(String)` với String `"manual"`.
- `addPropertyReference("formatter", "nameFormatter")` lưu một **RuntimeBeanReference** (hoặc metadata resolution tương ứng) tới bean name. Khi tạo bean, Spring resolve object `nameFormatter`, sau đó gọi `setFormatter(NameFormatter)`.

Setter phải phù hợp về **tên, khả năng ghi và kiểu đích**. Nếu `ManualGreeting` không có `setFormatter(NameFormatter)` hoặc bean name không tồn tại, Spring có thể không tạo được bean hợp lệ.

### Metadata giữ tên, runtime giữ object

Có thể kiểm tra:

~~~java
BeanDefinition stored = context.getBeanFactory()
        .getBeanDefinition("manualGreeting");
PropertyValue labelProperty =
        stored.getPropertyValues().getPropertyValue("label");
PropertyValue formatterProperty =
        stored.getPropertyValues().getPropertyValue("formatter");
RuntimeBeanReference ref =
        (RuntimeBeanReference) formatterProperty.getValue();

System.out.println(labelProperty.getValue()); // manual
System.out.println(ref.getBeanName());          // nameFormatter
~~~

**Snippet trích từ ví dụ đầy đủ**, cần imports cho `PropertyValue` và `RuntimeBeanReference`. Ở đây:

- `ref.getBeanName()` trả String **tên bean được tham chiếu**.
- Sau khi Spring khởi tạo `ManualGreeting`, `manual.formatter()` là **object của NameFormatter**.
- Việc chỉ nhìn bean definition không chứng minh setter đã chạy; dùng identity/behavior test để xác nhận instance thực tế.
- `RuntimeBeanReference` không phải service và không phải proxy để gọi method nghiệp vụ; nó là metadata cho container.

### Nếu dùng value nhầm chỗ thì sao?

~~~java
.addPropertyValue("formatter", "nameFormatter")
~~~

Đây là **sai ngữ nghĩa trong ví dụ**: người viết đang đưa String literal vào setter cần `NameFormatter`. Spring không mặc nhiên coi mọi String trông giống một bean name là lệnh truy xuất managed bean. Nếu bạn muốn **object**, hãy biểu đạt reference tường minh; nếu muốn **tên** để hiển thị/log, property nên có kiểu `String` và dùng value.

Đừng dựa vào một custom converter bí ẩn để giải quyết lỗi thiết kế này.

## 6. Dự án Spring Framework + Java 17+ chạy được

Dùng **Spring Framework 6.2.19**, JDK 17+ và Maven. Phiên bản cố định để ví dụ có thể tái lập; không hàm ý đó là bản Spring mới nhất. Không cần Spring Boot, HTTP server hay database.

Project:

~~~text
bean-reference-vs-value/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── BeanReferenceVsValueDemo.java
~~~

### 6.1. File pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>bean-reference-vs-value</artifactId>
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
                    <mainClass>demo.BeanReferenceVsValueDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Maven kéo theo các Spring dependencies cần thiết; `spring-context` không phải file JAR duy nhất có mặt trên classpath runtime. Nếu dùng `javac` trực tiếp, cần các Spring API JAR và transitive dependencies được Maven resolve.

### 6.2. File src/main/java/demo/BeanReferenceVsValueDemo.java

~~~java
package demo;

import java.util.Locale;
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

public final class BeanReferenceVsValueDemo {
    private BeanReferenceVsValueDemo() {}

    public interface NameFormatter {
        String normalize(String raw);
    }

    public static final class UppercaseFormatter implements NameFormatter {
        @Override
        public String normalize(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            return raw.strip().toUpperCase(Locale.ROOT);
        }
    }

    public static final class GreetingService {
        private final NameFormatter formatter;
        private final String prefix;
        private final int maxChars;

        public GreetingService(
                NameFormatter formatter, String prefix, int maxChars) {
            this.formatter = Objects.requireNonNull(formatter);
            if (prefix == null || prefix.isBlank()) {
                throw new IllegalArgumentException("prefix required");
            }
            if (maxChars <= 0) {
                throw new IllegalArgumentException("maxChars must be positive");
            }
            this.prefix = prefix;
            this.maxChars = maxChars;
        }

        public NameFormatter formatter() {
            return formatter;
        }

        public String greet(String name) {
            String normalized = formatter.normalize(name);
            if (normalized.length() > maxChars) {
                throw new IllegalArgumentException("name too long");
            }
            return prefix + ", " + normalized + "!";
        }
    }

    public static final class ManualGreeting {
        private String label;
        private NameFormatter formatter;

        public ManualGreeting() {}

        public void setLabel(String label) {
            this.label = Objects.requireNonNull(label);
        }

        public void setFormatter(NameFormatter formatter) {
            this.formatter = Objects.requireNonNull(formatter);
        }

        public NameFormatter formatter() {
            return formatter;
        }

        public String greet(String rawName) {
            return label + ":" + formatter.normalize(rawName);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        public NameFormatter nameFormatter() {
            return new UppercaseFormatter();
        }

        @Bean
        public GreetingService greetingService(
                NameFormatter formatter, Environment env) {
            String prefix =
                    env.getRequiredProperty("greeting.prefix");
            int maxChars =
                    env.getRequiredProperty("greeting.maxChars", Integer.class);
            return new GreetingService(formatter, prefix, maxChars);
        }
    }

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext();

        try (context) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("lessonProperties",
                            Map.<String, Object>of(
                                    "greeting.prefix", "Xin chao",
                                    "greeting.maxChars", "8")));

            context.register(AppConfig.class);

            BeanDefinition manualDefinition = BeanDefinitionBuilder
                    .genericBeanDefinition(ManualGreeting.class)
                    .addPropertyValue("label", "manual")
                    .addPropertyReference("formatter", "nameFormatter")
                    .getBeanDefinition();

            context.registerBeanDefinition(
                    "manualGreeting", manualDefinition);
            context.refresh();

            NameFormatter formatter = context.getBean(NameFormatter.class);
            GreetingService service =
                    context.getBean(GreetingService.class);
            ManualGreeting manual = context.getBean(ManualGreeting.class);

            BeanDefinition actual = context.getBeanFactory()
                    .getBeanDefinition("manualGreeting");
            PropertyValue label = actual.getPropertyValues()
                    .getPropertyValue("label");
            PropertyValue propertyReference = actual.getPropertyValues()
                    .getPropertyValue("formatter");
            RuntimeBeanReference reference =
                    (RuntimeBeanReference) propertyReference.getValue();

            System.out.println("propertyPrefix=" +
                    context.getEnvironment().getProperty("greeting.prefix"));
            System.out.println("propertyMaxChars=" +
                    context.getEnvironment().getRequiredProperty(
                            "greeting.maxChars", Integer.class));
            System.out.println("literalMetadata=" + label.getValue());
            System.out.println("referenceMetadata=" +
                    reference.getBeanName());
            System.out.println("sameFormatter=" +
                    (service.formatter() == formatter
                     && manual.formatter() == formatter));
            System.out.println("serviceGreeting=" +
                    service.greet(" Lan "));
            System.out.println("manualGreeting=" +
                    manual.greet(" Lan "));
            System.out.println("manualSingleton=" +
                    (manual == context.getBean(ManualGreeting.class)));
        }
    }
}
~~~

**Phân tích các điểm then chốt:**

- `AppConfig` đăng ký bean `nameFormatter` và `greetingService`; factory method service nhận **NameFormatter object** cùng `Environment`.
- `MapPropertySource` dùng `Map.<String, Object>of(...)` (Java 17 hỗ trợ) để tạo hai property không nhạy cảm trong bộ nhớ. `addFirst(...)` ưu tiên dữ liệu demo trước những nguồn có key trùng, giúp kết quả tái lập được.
- Cấu hình factory đọc String `greeting.prefix` và chuyển `"8"` sang `Integer` để truyền vào constructor `int maxChars`. **Value không biến thành bean.**
- `ManualGreeting` có no-argument constructor cùng hai setter public để Spring có thể áp dụng property injection theo metadata.
- Bean `manualGreeting` được đăng ký **trước `refresh()`**, chứa một literal value và một bean reference có tên rõ ràng.
- Method `getPropertyValue("formatter")` trả metadata; cast sang `RuntimeBeanReference` được kiểm soát vì chính builder đặt `addPropertyReference(...)`.
- Ba nơi dùng formatter (context lookup, service field, manual field) trỏ cùng managed singleton trong context này.
- `GreetingService.greet(" Lan ")` chuẩn hóa thành `LAN` và in `Xin chao, LAN!`; `ManualGreeting.greet(...)` in `manual:LAN`.
- Khi rời `try (context)`, context được đóng. Demo không đăng ký callback hủy in stdout nên không có dòng shutdown tự phát.

### 6.3. Lệnh chạy Maven

Ở thư mục `bean-reference-vs-value`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

**Tám dòng stdout chính xác do phương thức main của ví dụ in khi chạy thành công:**

~~~text
propertyPrefix=Xin chao
propertyMaxChars=8
literalMetadata=manual
referenceMetadata=nameFormatter
sameFormatter=true
serviceGreeting=Xin chao, LAN!
manualGreeting=manual:LAN
manualSingleton=true
~~~

Các dòng thông tin JDK/Maven và log framework/build (nếu có) **không thuộc tám dòng do code demo tự in**. Không có random, phụ thuộc đồng hồ hoặc thứ tự iteration không ổn định trong những giá trị output trên.

### 6.4. Tự chạy bằng javac và java trên hai hệ điều hành

Vì source import Spring, trước tiên dùng Maven xuất classpath gồm các dependency:

~~~bash
mvn -q dependency:build-classpath -Dmdep.outputFile=classpath.txt
~~~

**Linux/macOS (bash/zsh):**

~~~bash
mkdir -p out
javac --release 17 -encoding UTF-8 \
  -cp "$(cat classpath.txt)" \
  -d out src/main/java/demo/BeanReferenceVsValueDemo.java
java -cp "out:$(cat classpath.txt)" demo.BeanReferenceVsValueDemo
~~~

**Windows PowerShell:**

~~~powershell
$deps = (Get-Content -Raw classpath.txt).Trim()
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -encoding UTF-8 -cp "$deps" -d out src/main/java/demo/BeanReferenceVsValueDemo.java
java -cp "out;$deps" demo.BeanReferenceVsValueDemo
~~~

Classpath entry separator là `:` trên Unix và `;` trên Windows. Hai cách chạy cùng class `demo.BeanReferenceVsValueDemo` và cho cùng tám dòng stdout ở mục 6.3.

## 7. Đọc kết quả: metadata khác object thực tế

| Dòng output | Điều được kiểm chứng |
| --- | --- |
| `propertyPrefix=Xin chao` | `Environment` cung cấp String từ property source |
| `propertyMaxChars=8` | Value cấu hình String đã được conversion sang kiểu số yêu cầu |
| `literalMetadata=manual` | `BeanDefinition` của ManualGreeting giữ literal value |
| `referenceMetadata=nameFormatter` | `RuntimeBeanReference` giữ **tên** bean đích |
| `sameFormatter=true` | Service và ManualGreeting thực tế giữ cùng bean formatter singleton |
| `serviceGreeting=Xin chao, LAN!` | Service kết hợp collaborator object với prefix/maxChars value |
| `manualGreeting=manual:LAN` | Setter nhận cả literal value và resolved bean object |
| `manualSingleton=true` | Cùng bean definition singleton trả lại cùng instance khi lookup |

**Không đồng nhất hai kiểm tra:** `referenceMetadata=nameFormatter` chỉ cho biết **metadata trỏ tới bean nào**; `sameFormatter=true` mới xác minh **object reference thực tế** sau wiring. Cả hai cần thiết khi debug dependency injection phức tạp.

**Đừng suy ra** reference luôn cùng identity ở mọi scope. Đây là singleton trong một context cụ thể. Prototype, scoped proxy hay các factory/proxy đặc biệt có thể thay đổi giả định về identity và thời điểm tạo.

## 8. @Qualifier chọn bean, @Value chọn dữ liệu

Có hai loại “chọn” khác nhau:

~~~java
@Qualifier("fastFormatter") NameFormatter formatter
~~~

Đó là một **injection point yêu cầu bean candidate** gắn với qualifier/tên `fastFormatter`. Nó không đọc property `fastFormatter` từ `Environment`.

~~~java
@Value("${greeting.prefix}") String prefix
~~~

Đó là một **injection point muốn giá trị cấu hình** ứng với key `greeting.prefix`, không tìm một Spring bean `String` tên prefix theo cùng quy tắc.

Nếu muốn nhiều beans cùng implement `NameFormatter`:

~~~java
@Bean("upperFormatter")
NameFormatter upperFormatter() {
    return new UppercaseFormatter();
}

@Bean("otherFormatter")
NameFormatter otherFormatter() {
    return new OtherFormatter();
}
~~~

**Đây là snippet minh họa, không chạy độc lập vì `OtherFormatter` chưa được định nghĩa.** Hai bean cùng type không sai; nhưng injection đơn trị `NameFormatter formatter` có thể bị mơ hồ nếu Spring không có cách chọn rõ. Sử dụng `@Qualifier` ở injection point, `@Primary` khi có một mặc định hợp lệ hoặc thiết kế lại để chỉ cần một candidate.

**Không sử dụng `@Qualifier` để sửa lỗi thiếu giá trị `greeting.prefix`.** Đó là lỗi property configuration, phải xem nguồn value. Và không dùng `@Value("${someBeanName}")` để thay cho dependency bean kiểu `NameFormatter` nếu mục đích là lấy managed object.

## 9. Các loại simple value và conversion thường gặp

Không phải chỉ có `String`:

| Giá trị cấu hình | Kiểu Java mong muốn | Kiểm tra |
| --- | --- | --- |
| `"Xin chao"` | `String` | Có rỗng/blank không? |
| `"8"` | `Integer`/`int` | Có chuyển kiểu và lớn hơn 0 không? |
| `"true"` | `Boolean`/`boolean` | Cú pháp hợp lệ và chính sách default |
| `"3000"` | `long` | Có ý nghĩa đơn vị ms rõ ràng không? |
| `"MAIL"` | Enum `Channel` nếu đã định nghĩa | Có đúng hằng enum không? |
| `"https://example.org"` | `URI` nếu conversion hỗ trợ | Có scheme/host hợp lệ không? |

Spring có các cơ chế property resolution và type conversion, nhưng **conversion thành công không đồng nghĩa giá trị đúng nghiệp vụ**. Ví dụ `maxChars=-5` vẫn có thể chuyển thành `int`; constructor phải từ chối vì vi phạm invariant.

Giá trị của `Environment` có thể đến từ:

- JVM system properties hoặc environment variables.
- `MapPropertySource`, file `.properties` được đăng ký bằng cơ chế phù hợp, hoặc các nguồn property do framework chủ quản thêm.
- Spring Boot có thêm cơ chế external configuration riêng, nhưng bài này chạy thuần Spring Framework; **không tự giả định file `application.properties` đã được nạp như Boot**.

**Thứ tự property sources quan trọng:** nguồn có độ ưu tiên cao hơn quyết định value nếu key trùng. Demo dùng `addFirst` để giữ kết quả ổn định, không phải quy định chung rằng mọi ứng dụng phải ưu tiên một map trong bộ nhớ.

### Missing value và optional value

Dùng `getRequiredProperty(key)` khi cấu hình bắt buộc phải có. Nếu thiếu key, container có thể thất bại khi tạo bean phụ thuộc — tốt hơn dùng chuỗi rỗng hoặc số 0 một cách âm thầm.

Khi một giá trị thực sự tùy chọn, có thể dùng default qua `Environment.getProperty(key, defaultValue)` hoặc property placeholder có fallback phù hợp. Default phải có ý nghĩa nghiệp vụ và được ghi rõ; **không dùng default để che cấu hình sai**.

Nếu secret là value (password/token), không hardcode hay in ra stdout/log. Secret không vì thế trở thành một Spring service bean; cơ chế lưu trữ, truyền và bảo vệ nó là vấn đề khác cần thiết kế riêng.

## 10. Scope, lifecycle và nơi nhận reference

| Cơ chế | Ảnh hưởng |
| --- | --- |
| Singleton mặc định | Các consumer cùng trỏ tới một bean definition thường dùng chung một object |
| Prototype | Mỗi lần Spring tạo/đáp ứng yêu cầu prototype có thể nhận instance mới |
| Prototype inject vào singleton | Thường được resolve một lần lúc singleton tạo, không tự đổi instance sau mỗi method call |
| `@Lazy` | Có thể trì hoãn instantiation; không xóa dependency logic |
| `ObjectProvider<NameFormatter>` | Cho phép truy xuất bean theo nhu cầu, phù hợp khi cần động |
| Alias bean | Một tên thay thế cho cùng bean, không tạo object độc lập chỉ vì alias |
| `new UppercaseFormatter()` ngoài container | Object Java bình thường, không tự nhận lifecycle như managed bean |

**Các giá trị cấu hình đơn giản thường được sao chép/đọc tại thời điểm khởi tạo.** Thay property source sau khi service singleton đã được tạo không tự động biến field `final String prefix` của service thành giá trị mới. Nếu muốn cập nhật runtime phải có cơ chế thiết kế rõ (reconfigure, tạo bean/context mới hoặc hợp đồng thay đổi phù hợp).

**Reference không giống value**, nhưng cả hai đều có thể dẫn đến vấn đề nếu được chia sẻ sai: singleton collaborator chứa state mutable phải thread-safe khi nhiều consumer gọi đồng thời; simple value cũng cần validation kiểu và giới hạn.

## 11. Lỗi thường gặp và cách chẩn đoán

| Triệu chứng | Nguyên nhân thường gặp | Hướng sửa |
| --- | --- | --- |
| `NoSuchBeanDefinitionException` | Không có bean `NameFormatter` tương thích hoặc tên reference sai | Đăng ký collaborator/kiểm tra bean name |
| `UnsatisfiedDependencyException` | Constructor/factory cần bean mà container không giải quyết được | Kiểm tra type, qualifier, scan và dependencies |
| `NoUniqueBeanDefinitionException` | Có nhiều bean cùng type nhưng không chọn được một | Dùng `@Qualifier`/`@Primary` hoặc thiết kế lại |
| `getRequiredProperty(...)` ném exception | Thiếu property key bắt buộc | Kiểm tra Environment, key và property sources |
| Property String không chuyển được sang int | Dữ liệu lỗi hoặc conversion không tương thích | Kiểm tra type/property value và validation |
| Bean được tạo nhưng constructor reject maxChars | Value có kiểu hợp lệ nhưng vi phạm invariant | Sửa giá trị cấu hình, không bỏ guard |
| `NotWritablePropertyException` | Setter JavaBean không tồn tại hoặc sai tên | Khai báo đúng `setFormatter(...)`/`setLabel(...)` |
| `addPropertyValue("formatter", "nameFormatter")` | Nhầm bean name String với object reference | Dùng `addPropertyReference(...)` |
| `@Value` nhận chuỗi placeholder chưa mong đợi | Nguồn property/resolver chưa được cấu hình phù hợp | Xem resolution của context, có key và syntax đúng |
| Context refresh PASS nhưng behavior sai | Bean/value được nối không đúng ý định hoặc logic lỗi | Kiểm tra identity và output, không chỉ startup |
| Bean singleton giữ cấu hình cũ | Thay property source sau khi singleton đã tạo | Có chiến lược refresh/reconfigure rõ |
| `javac` báo thiếu Spring API | Classpath không chứa Spring JAR và dependencies | Kiểm tra Maven pom, `-cp` hoặc file classpath |
| Dùng bean name làm password/config secret | Sai phân loại và bảo mật | Dùng nguồn value phù hợp, bảo vệ secrets |
| Reference tới một bean có lifecycle đã kết thúc | Sử dụng object sau khi context shutdown | Tôn trọng ownership/lifecycle context |

**Quy trình debug ngắn:** xác định tham số gây lỗi; hỏi đó là **giá trị** hay **collaborator bean**; kiểm tra key/property source hoặc type/name/qualifier tương ứng; đọc chain `Caused by`; xác minh metadata, sau đó kiểm tra object đã được nối trong runtime.

## 12. Hai tầng kiểm thử bổ trợ nhau

**Unit test Java thuần** xác minh logic không cần Spring:

~~~java
NameFormatter fake = name -> "TEST";
GreetingService service = new GreetingService(fake, "Hello", 10);
System.out.println(service.greet("anything")); // Hello, TEST!
~~~

Đây là **snippet**, có thể đặt trong method/class truy cập các kiểu của ví dụ; không phải một file runnable độc lập. Nó kiểm tra hành vi service và khả năng thay collaborator.

**Spring context smoke test** kiểm tra wiring thực:

~~~java
try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext()) {
    context.getEnvironment().getPropertySources().addFirst(
            new MapPropertySource("test", Map.<String, Object>of(
                    "greeting.prefix", "Test",
                    "greeting.maxChars", "10")));
    context.register(AppConfig.class);
    context.refresh();

    GreetingService service = context.getBean(GreetingService.class);
    NameFormatter formatter = context.getBean(NameFormatter.class);
    if (service.formatter() != formatter) {
        throw new IllegalStateException("wrong bean reference");
    }
}
~~~

Đây là **snippet trong ngữ cảnh có imports và các types đầy đủ**, không phải file thứ hai cần chạy. Kiểm tra dùng equality của reference với bean mong muốn. Với proxy/scope khác, cần điều chỉnh theo hợp đồng thực, không áp dụng identity test máy móc.

Các negative tests đáng thực hiện trong cấu hình riêng: thiếu property `greeting.prefix`, `greeting.maxChars=0`, thiếu bean `NameFormatter`, có hai formatter candidates không qualifier, hoặc dùng nhầm `addPropertyValue` thay reference. Khi một test cần lỗi, kiểm tra **loại lỗi/nguyên nhân** chứ không hardcode nguyên văn stack trace vì wrapper/message có thể thay đổi theo phiên bản.

Không dùng Java `assert` mặc định như điều kiện bắt buộc vì nó có thể bị tắt nếu JVM không có `-ea`; dùng assertion của test framework hoặc if/exception để fail rõ.

## 13. Bài tập có đáp án

**Bài 1.** Trong constructor `GreetingService(NameFormatter formatter, String prefix, int maxChars)`, tham số nào là bean collaborator?

**Đáp án:** `formatter`. Hai tham số còn lại là simple configuration values; chúng cần được cung cấp rõ ràng từ caller hoặc cơ chế cấu hình, không tự được resolve như một bean cùng tên.

**Bài 2.** Vì sao `@Qualifier("nameFormatter")` khác `@Value("${greeting.prefix}")`?

**Đáp án:** Qualifier ràng buộc việc **chọn bean candidate**; Value yêu cầu **resolve dữ liệu cấu hình** rồi truyền vào tham số. Không thay thế nhau.

**Bài 3.** `RuntimeBeanReference("nameFormatter")` có phải chính object `UppercaseFormatter` đang chạy không?

**Đáp án:** Không. Nó là metadata có tên đích. Container phải resolve khi wiring; object thật xuất hiện trong field của consumer.

**Bài 4.** Thêm một bean `String` tên `greeting.prefix` có tự làm factory method `Environment.getRequiredProperty("greeting.prefix")` đọc bean đó không?

**Đáp án:** Không. Property resolution đọc các **PropertySource**; bean registry là một cơ chế riêng. Muốn đọc value qua Environment, phải đăng ký property source đúng.

**Bài 5.** `addPropertyValue("formatter","nameFormatter")` có gửi object bean vào setter `setFormatter(NameFormatter)` không?

**Đáp án:** Không theo ngữ nghĩa định nghĩa trong bài. Đó là String literal; để inject bean object cần `addPropertyReference` hoặc cơ chế bean resolution phù hợp.

**Bài 6.** Property `greeting.maxChars` có giá trị `"-1"`. Chuyển thành Integer thành công thì cấu hình hợp lệ chưa?

**Đáp án:** Chưa. Constructor `GreetingService` cần maxChars dương và sẽ từ chối `-1`. Conversion và domain validation là hai bước khác nhau.

**Bài 7.** Vì sao `sameFormatter=true` trong demo?

**Đáp án:** Service và ManualGreeting cùng nhận bean singleton `nameFormatter` trong một context, và identity checks đối chiếu chính object đó. Không suy ra kết quả này cho mọi prototype hoặc scoped proxy.

**Bài 8.** Nếu môi trường đổi `greeting.prefix` sau khi singleton service đã tạo, field `prefix` có đổi ngay không?

**Đáp án:** Không tự động; field được truyền vào constructor lúc khởi tạo. Muốn thay runtime cần cơ chế cập nhật được thiết kế rõ ràng.

## 14. Checklist áp dụng

- [ ] Xác định mỗi constructor/factory argument là bean reference hay simple value.
- [ ] Không đánh đồng bean name String với managed bean object.
- [ ] Dùng factory method parameters cho collaborator và Environment/@Value cho data values.
- [ ] Không tự động autowire primitive/String như collaborator nếu chưa có chủ ý.
- [ ] Dùng `BeanDefinitionBuilder.addPropertyValue` cho literal.
- [ ] Dùng `addPropertyReference` cho tên bean cần resolve ra object.
- [ ] Phân biệt `RuntimeBeanReference` (metadata) với field đã inject (runtime).
- [ ] Cần setter/constructor phù hợp về tên, kiểu và khả năng ghi.
- [ ] Đăng ký bean definitions và property sources trước refresh trong demo.
- [ ] Dùng required value policy và validation hợp đồng cho dữ liệu cấu hình.
- [ ] Giải quyết nhiều bean candidates bằng qualifier/primary hợp lý.
- [ ] Kiểm tra scope/lifecycle nếu identity hoặc thời điểm tạo khác giả định.
- [ ] Không log secrets, không biến mọi value thành bean riêng.
- [ ] Chạy Java 17+ bằng Maven hoặc javac/java và đối chiếu đúng tám dòng stdout.
- [ ] Test riêng hành vi Java và wiring/configuration của Spring.

**Kết luận:** Một **bean reference** là quan hệ giữa các managed objects được Spring resolve theo tên/kiểu. Một **simple value** là dữ liệu được truyền vào bean, có thể là literal hoặc được resolve từ configuration. Mặc dù cả hai gặp nhau ở constructor/setter, chúng khác nhau ở **nguồn, metadata và cách container giải quyết**. Hiểu ranh giới này giúp DI rõ ràng, tránh nhầm chuỗi tên bean với collaborator và kiểm thử cấu hình chính xác.

### Tài liệu Spring chính thức

- [Dependency Injection and Collaborators](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html)
- [Using @Bean](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html)
- [Using @Value](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/value-annotations.html)
- [Environment and PropertySource](https://docs.spring.io/spring-framework/reference/6.2/core/beans/environment.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dependency-injection-007">Tiêm dựa trên hợp đồng thay vì tự tìm kiếm đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Tiêm dựa trên hợp đồng thay vì tự tìm kiếm đối tượng trong Spring Core Container

## 1. Mục tiêu: service khai báo thứ mình cần, không tự đi tìm

Một class nghiệp vụ thường không thể hoạt động đơn lẻ: nó cần nơi lưu trữ, nơi gửi thông báo, bộ kiểm tra, đồng hồ hoặc một API ở hệ thống khác. Có hai hướng thiết kế trái ngược:

- **Tự tìm kiếm hoặc tự tạo collaborator:** service gọi `new SqlRepository()`, `ApplicationContext.getBean(...)` hoặc một registry toàn cục mỗi khi cần phụ thuộc.
- **Nhận collaborator theo hợp đồng (Dependency Injection / DI):** service khai báo rõ dependency qua constructor/factory method/setter và để bên ngoài cung cấp implementation phù hợp.

Ví dụ use case tiếp nhận đơn hàng cần đúng hai **năng lực**: giữ chỗ hàng trong kho và phát thông báo. Lớp xử lý đơn chỉ biết `InventoryPort` và `ReceiptPort`, không cần biết adapter lưu trên Map, JDBC hay một service từ xa.

~~~text
                  composition root
              Java main / Spring AppConfig
                   |              |
       chọn InMemoryInventory   ConsoleReceipt
                   |              |
                   v              v
           InventoryPort      ReceiptPort  <--- hợp đồng
                   \              /
                    \ constructor/
                     v          v
                      CheckoutService
                             |
                      place(Order)
                         /      \
                        v        v
                 reserve stock  publish receipt
~~~

**Đảo ngược quyền kiểm soát (IoC)** ở đây có nghĩa service không tự quyết định cách tạo và tìm đối tượng cộng tác. Đơn vị chịu trách nhiệm khởi động/lắp ráp quyết định concrete implementation, còn service tập trung vào quy tắc nghiệp vụ.

Sau bài này, bạn sẽ có thể:

1. Nhận diện Service Locator và việc tự `new` dependency làm ẩn hoặc khóa chặt mối quan hệ.
2. Thiết kế interface dựa trên **nhu cầu của bên sử dụng**, không theo API công nghệ cụ thể.
3. Viết constructor injection bằng Java 17, kiểm thử với fake mà không cần Spring.
4. Cấu hình Spring `@Bean` để lắp ráp cùng business class không import Spring.
5. Phân biệt lúc nào việc tra bean ở điểm khởi động hoặc dùng provider động vẫn hợp lý.
6. Giải thích các lỗi missing/multiple bean, scope và proxy mà không đánh đồng với bản chất của DI.

## 2. Vấn đề với tự tạo đối tượng cộng tác

### 2.1. Ghép chặt bằng `new` bên trong service

~~~java
// Minh họa: tên các class bên dưới cần định nghĩa ở project thực tế.
final class BadCheckoutService {
    private final JdbcInventory inventory = new JdbcInventory();
    private final SmtpReceipt receipt = new SmtpReceipt();

    String place(Order order) {
        inventory.reserve(order.sku(), order.quantity());
        receipt.publish(order.id());
        return "OK";
    }
}
~~~

Đoạn trên **không độc lập để biên dịch**, vì chưa định nghĩa `JdbcInventory`, `SmtpReceipt` và `Order`. Nó giúp thấy các vấn đề:

- Service chọn thẳng JDBC/SMTP, nên test đơn vị có thể vô tình chạm database/email thật.
- Thay adapter cần chỉnh code nghiệp vụ, hoặc thêm điều kiện môi trường vào service.
- Cấu hình connection pool, retry, credentials và lifecycle bị kéo vào một lớp không nên sở hữu chúng.
- Nếu service tự tạo nhiều instance không cần thiết, tài nguyên có thể bị lãng phí hoặc bị rò rỉ.
- API service che giấu các dependency quan trọng, khó nhìn object graph từ constructor.

**Không phải mọi `new` đều sai.** Tạo một value object như `new Order(...)` hay list cục bộ phục vụ tính toán không nhất thiết phải ủy quyền cho IoC container. Vấn đề là **service tự lựa chọn và xây dựng các collaborator có vai trò hệ thống** mà đáng ra phải nhận từ ranh giới lắp ráp.

### 2.2. Service Locator: tự tra bean trong business method

Một cách tưởng như bớt ghép chặt là giữ `ApplicationContext` rồi lookup:

~~~java
// Minh họa; giả sử đã khai báo CheckoutService và các interface.
final class LocatingCheckoutService {
    private final ApplicationContext context;

    LocatingCheckoutService(ApplicationContext context) {
        this.context = context;
    }

    String place(Order order) {
        InventoryPort inventory = context.getBean(InventoryPort.class);
        ReceiptPort receipt = context.getBean(ReceiptPort.class);
        // Logic dùng inventory và receipt...
        return "DONE";
    }
}
~~~

Đây cũng chỉ là **trích đoạn minh họa**, không phải file runnable. Nó loại bỏ việc tự `new` concrete adapters nhưng tạo vấn đề khác:

1. Constructor chỉ lộ dependency `ApplicationContext`, không cho thấy rõ service **cần** `InventoryPort` và `ReceiptPort`.
2. Service import Spring API; unit test Java thuần phải giả lập container hoặc cung cấp `ApplicationContext`.
3. Một lỗi thiếu bean có thể chỉ xuất hiện **khi nhánh nghiệp vụ chạy**, thay vì khi container dựng service.
4. Cách đặt tên/type/qualifier của bean len vào logic nghiệp vụ, dễ phát sinh fallback tùy ý hoặc lookup nhiều lần.
5. Nếu mỗi nơi tự `getBean`, việc hiểu quan hệ phụ thuộc và scope của object graph trở nên khó hơn.

Đó là cách dùng **Service Locator** trong lớp nghiệp vụ, trái với nguyên tắc “dependency được cung cấp cho đối tượng”. Nó không tự động làm ứng dụng sai chức năng, nhưng thường giảm khả năng test và khả năng thay implementation.

Đừng nhầm vấn đề này với việc **`main` hoặc adapter ở biên gọi `getBean(CheckoutService.class)` một lần** để bắt đầu use case: đó là lookup ở **composition root/entry point**, không phải nghiệp vụ tự đi tìm những dependency ẩn bên trong.

## 3. Hợp đồng tốt phản ánh nhu cầu của service

### 3.1. Hai hợp đồng nhỏ

Service tiếp nhận đơn cần các thao tác:

~~~java
public interface InventoryPort {
    boolean reserve(String sku, int quantity);
}

public interface ReceiptPort {
    void publish(String orderId);
}
~~~

Đây là minh họa API; chương trình Java 17 đầy đủ sẽ khai báo hai interface trong `ContractDemo` ở mục 5.

Contract `InventoryPort.reserve` được định nghĩa là:

- Trả `true` nếu đủ hàng và giữ chỗ thành công.
- Trả `false` nếu không đủ hàng; trong demo không thay đổi số lượng.
- Với đầu vào không hợp lệ, bên gọi/model phải kiểm tra; adapter thật cũng nên bảo vệ invariant.
- Với backend thật, còn cần mô tả chính sách concurrent reservation, idempotency và lỗi mạng/database.

Contract `ReceiptPort.publish` thông báo sau khi giữ chỗ thành công. Nó **không** bắt service biết email hay message broker cụ thể. Trong production, cần xác định thêm độ tin cậy, retry và semantics khi gửi thất bại; demo sẽ nói rõ giới hạn này.

**Hợp đồng không phải luôn là interface Java.** Có thể inject một class cụ thể khi đó là dependency hợp lý, ổn định và không cần thay bằng nhiều adapter. Tuy nhiên interface nhỏ rất có ích tại ranh giới giữa nghiệp vụ và hạ tầng, đặc biệt để thay fake trong unit test.

### 3.2. Tránh interface chỉ “đổi tên công nghệ”

Ví dụ contract chưa tốt:

~~~java
interface InventoryPort {
    java.sql.ResultSet executeSql(String sql);
}
~~~

Đây là trích đoạn minh họa. Dù mang tên `InventoryPort`, nó vẫn bắt service biết SQL và `ResultSet`; đây không phải ranh giới hướng nghiệp vụ tốt.

Hãy hỏi: “Bên sử dụng thực sự muốn làm gì?” Nếu là “giữ N đơn vị hàng hóa cho SKU”, `reserve(sku, n)` thể hiện ý nghĩa tốt hơn là `executeSql(...)`.

Một interface có hàng chục method không liên quan cũng gây khó khăn cho fake và testing. Nên tách theo nhu cầu của consumer (*interface segregation*) khi lợi ích rõ.

### 3.3. Constructor cho dependency bắt buộc

~~~java
public CheckoutService(InventoryPort inventory, ReceiptPort receipt) {
    this.inventory = java.util.Objects.requireNonNull(inventory);
    this.receipt = java.util.Objects.requireNonNull(receipt);
}
~~~

Đoạn này được triển khai trong file chạy ở mục 5. Hai field được khai báo `final` vì service không cần đổi dependency sau khi xây dựng. Nếu một dependency là bắt buộc, constructor giúp fail sớm khi không được cung cấp; dùng setter tùy chọn cho nó có thể để object ở trạng thái không hợp lệ.

**DI không đòi có Spring.** Constructor injection chỉ là cách một object nhận dependency từ nơi tạo nó. Spring Core Container tự động hóa cấu hình và quản lý bean graph, không phát minh ra nguyên tắc “nhận qua constructor”.

## 4. Trước và sau: ai kiểm soát lựa chọn triển khai?

| Câu hỏi | Tự tìm/tạo collaborator trong service | Tiêm theo hợp đồng |
| --- | --- | --- |
| Service biết class nào? | Concrete adapter hoặc Spring context API | Interface/abstraction nó thực sự cần |
| Ai chọn adapter? | Business service | `main`, factory, Spring configuration |
| Dependency có hiện rõ? | Dễ bị ẩn trong method hoặc singleton registry | Thường thể hiện trực tiếp ở constructor |
| Unit test phải làm gì? | Dựng I/O thật, mock container hoặc viết đường vòng | Truyền fake implementation thuần Java |
| Đổi database/email? | Có thể phải sửa service | Thay adapter và wiring nếu contract giữ nguyên |
| Khi thiếu dependency? | Có thể lỗi muộn ở một nhánh method | Constructor hoặc khởi tạo bean có thể fail sớm |
| Có tự động transaction/HTTP? | Không | Cũng không; cần hạ tầng riêng |
| Ai quản lý lifecycle collaborator? | Dễ bị phân tán | Composition root/container có chỗ quản lý rõ |

Không nên đưa ra tuyên bố “không sửa một dòng mã nào suốt đời”: nếu yêu cầu nghiệp vụ hoặc semantics contract thay đổi thì service vẫn phải thay đổi. Lợi ích là **giảm tác động của việc thay cách triển khai một năng lực**, không phải phủ nhận những thay đổi nghiệp vụ thật.

## 5. Ví dụ Java 17 hoàn chỉnh: contract-first và fake test

Đầu tiên chạy hoàn toàn bằng Java 17 để chứng minh DI **không phụ thuộc Spring**. File Java ở mục này cũng được dùng lại trong ví dụ Maven/Spring mục 6, vì thế không tạo bản sao business service.

### 5.1. Cấu trúc

~~~text
contract-di-demo/
├── pom.xml                         # thêm cho Spring ở mục 6
└── src/
    └── main/
        └── java/
            └── demo/
                ├── ContractDemo.java
                └── SpringWiringDemo.java  # thêm ở mục 6
~~~

Tạo `src/main/java/demo/ContractDemo.java`:

~~~java
package demo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;

public final class ContractDemo {
    private ContractDemo() {}

    public record Order(String id, String sku, int quantity) {
        public Order {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id is required");
            }
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("sku is required");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
        }
    }

    public interface InventoryPort {
        boolean reserve(String sku, int quantity);
    }

    public interface ReceiptPort {
        void publish(String orderId);
    }

    // Adapter demo trong RAM, không thay thế database thực.
    public static final class InMemoryInventory implements InventoryPort {
        private final Map<String, Integer> available = new HashMap<>();

        public InMemoryInventory(Map<String, Integer> initial) {
            Objects.requireNonNull(initial, "initial");
            available.putAll(initial);
        }

        @Override
        public boolean reserve(String sku, int quantity) {
            Integer current = available.get(sku);
            if (current == null || current < quantity) {
                return false;
            }
            available.put(sku, current - quantity);
            return true;
        }
    }

    // Adapter demo: phát sự kiện bằng dòng console.
    public static final class ConsoleReceipt implements ReceiptPort {
        @Override
        public void publish(String orderId) {
            System.out.println("EVENT " + orderId);
        }
    }

    public static final class CheckoutService {
        private final InventoryPort inventory;
        private final ReceiptPort receipt;

        public CheckoutService(InventoryPort inventory, ReceiptPort receipt) {
            this.inventory = Objects.requireNonNull(inventory, "inventory");
            this.receipt = Objects.requireNonNull(receipt, "receipt");
        }

        public String place(Order order) {
            Objects.requireNonNull(order, "order");
            if (!inventory.reserve(order.sku(), order.quantity())) {
                return "REJECTED " + order.id();
            }
            receipt.publish(order.id());
            return "ACCEPTED " + order.id();
        }
    }

    public static void main(String[] args) {
        // Composition root thuần Java: lựa chọn concrete adapter ở ngoài service.
        InventoryPort stock = new InMemoryInventory(Map.of("BOOK", 3));
        ReceiptPort receipts = new ConsoleReceipt();
        CheckoutService service = new CheckoutService(stock, receipts);

        System.out.println(service.place(new Order("O-1", "BOOK", 2)));
        System.out.println(service.place(new Order("O-2", "BOOK", 2)));

        // Fake dùng ngay trong test, không cần Spring, DB hoặc email.
        List<String> events = new ArrayList<>();
        InventoryPort alwaysAvailable = (sku, quantity) -> true;
        ReceiptPort fakeReceipt = events::add;
        CheckoutService tested =
                new CheckoutService(alwaysAvailable, fakeReceipt);

        String testResult = tested.place(new Order("T-1", "PEN", 1));
        System.out.println("TEST_ACCEPTED " + testResult);
        System.out.println("TEST_EVENTS " + events);

        if (!"ACCEPTED T-1".equals(testResult)
                || !events.equals(List.of("T-1"))) {
            throw new AssertionError("Unexpected collaborator calls");
        }
        System.out.println("TEST PASS");
    }
}
~~~

### 5.2. Lệnh `javac`/`java`

Mở terminal tại thư mục `contract-di-demo`, cần JDK 17+:

~~~bash
javac --release 17 -encoding UTF-8 -d out src/main/java/demo/ContractDemo.java
java -cp out demo.ContractDemo
~~~

**Stdout chính xác của chương trình Java thuần:**

~~~text
EVENT O-1
ACCEPTED O-1
REJECTED O-2
TEST_ACCEPTED ACCEPTED T-1
TEST_EVENTS [T-1]
TEST PASS
~~~

Giải thích:

1. `Map.of("BOOK", 3)` cung cấp 3 đơn vị hàng. Đơn `O-1` muốn 2 đơn vị: `reserve` trừ còn 1, rồi `ConsoleReceipt.publish` in `EVENT O-1`.
2. `place` trả `ACCEPTED O-1`; `main` in giá trị trả về sau khi lời gọi hoàn tất.
3. Đơn `O-2` muốn thêm 2 nhưng chỉ còn 1: `reserve` trả `false`; service trả `REJECTED O-2` **không phát sự kiện**.
4. Phép thử dùng `InventoryPort` lambda luôn trả `true` và `ReceiptPort` lambda ghi mã đơn vào `events`.
5. Service `tested` không thay đổi mã so với service thực, nhưng đã được cấp **collaborators khác**. `TEST_EVENTS [T-1]` kiểm tra thông báo đi qua fake, không có dòng `EVENT T-1`.
6. `List.of("T-1")` là danh sách kỳ vọng, `AssertionError` chỉ ném nếu hành vi sai. Trường hợp đúng in `TEST PASS`.

**Điểm then chốt:** `CheckoutService` chỉ gọi `inventory.reserve` và `receipt.publish`. Nó không tạo `InMemoryInventory`, không gọi `new ConsoleReceipt` và không hề import Spring. Việc chọn implementation diễn ra ở `main`.

### 5.3. Đánh giá độ an toàn của ví dụ

- `InMemoryInventory` dùng `HashMap` mutable và **không thread-safe**; khi có nhiều concurrent requests, tồn kho có thể bị sai nếu không có cơ chế đồng bộ/database thích hợp.
- Demo không xử lý trùng order ID, expiration của reservation hay retry: đó là các quy tắc quan trọng trong hệ thống thật, không nằm trong hợp đồng tối giản của bài này.
- Nếu `receipt.publish` ném exception **sau khi reserve**, stock đã giảm. Java thuần không tự rollback; Spring DI cũng không tự thêm database transaction.
- Một `boolean reserve` quá đơn giản cho mọi hệ thống thật, nhưng phù hợp để tập trung vào ranh giới DI; trong production có thể cần reservation ID và trạng thái rõ hơn.
- Validate đầu vào ở `Order` giúp giữ invariant, nhưng adapter vẫn cần bảo vệ khi bị gọi từ code khác; đừng lấy demo làm bảo đảm chống gọi quantity âm trực tiếp vào adapter.

## 6. Cùng business class, Spring chỉ thay bước lắp ráp

Sau khi chương trình Java thuần chạy, giữ nguyên `ContractDemo.java`. Bây giờ chỉ thêm Spring configuration và main để **container quyết định object graph**, không sửa implementation của `CheckoutService`.

### 6.1. Maven configuration

Spring Framework 6.2.0 tương thích Java 17. Bài chạy bằng `spring-context`, **không dùng Spring Boot**, web server, AOP hoặc database. Tạo `pom.xml` tại thư mục `contract-di-demo`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>contract-di-demo</artifactId>
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
                    <mainClass>demo.SpringWiringDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 6.2. `@Configuration` và `@Bean` với tham số interface

Tạo `src/main/java/demo/SpringWiringDemo.java`:

~~~java
package demo;

import java.util.Map;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class SpringWiringDemo {
    private SpringWiringDemo() {}

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        public ContractDemo.InventoryPort inventory() {
            return new ContractDemo.InMemoryInventory(
                    Map.of("BOOK", 3));
        }

        @Bean
        public ContractDemo.ReceiptPort receipt() {
            return new ContractDemo.ConsoleReceipt();
        }

        @Bean
        public ContractDemo.CheckoutService checkoutService(
                ContractDemo.InventoryPort inventory,
                ContractDemo.ReceiptPort receipt) {
            return new ContractDemo.CheckoutService(inventory, receipt);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            // Lookup hợp lý ở biên khởi động để gọi một entry-point service.
            ContractDemo.CheckoutService service =
                    context.getBean(ContractDemo.CheckoutService.class);

            System.out.println("SERVICE_BEANS "
                    + context.getBeanNamesForType(
                            ContractDemo.CheckoutService.class).length);

            System.out.println(service.place(
                    new ContractDemo.Order("S-1", "BOOK", 1)));
            System.out.println(service.place(
                    new ContractDemo.Order("S-2", "BOOK", 3)));

            System.out.println("SAME_BEAN "
                    + (service == context.getBean(
                            ContractDemo.CheckoutService.class)));
        }
    }
}
~~~

Từ thư mục `contract-di-demo` có `pom.xml`, chạy:

~~~bash
mvn -version
mvn -q compile exec:java
~~~

**Stdout do `SpringWiringDemo` tự in đúng thứ tự:**

~~~text
SERVICE_BEANS 1
EVENT S-1
ACCEPTED S-1
REJECTED S-2
SAME_BEAN true
~~~

`mvn -version` và Maven/plugin có thể in thêm phiên bản, quá trình tải dependency hoặc log riêng. Khối trên liệt kê **stdout do mã ứng dụng chủ động tạo**, không hứa terminal chỉ có các dòng đó.

### 6.3. Theo dõi cơ chế container

1. `AnnotationConfigApplicationContext(AppConfig.class)` đọc metadata `@Configuration` và `@Bean`, tạo các singleton không lazy khi refresh.
2. Bean `inventory` công bố một `InventoryPort`, thực tế là `InMemoryInventory` với 3 cuốn BOOK ban đầu.
3. Bean `receipt` công bố `ReceiptPort`, thực tế là `ConsoleReceipt`.
4. `checkoutService(InventoryPort inventory, ReceiptPort receipt)` khai báo hai dependency bằng **parameter type**, không cần tự `getBean` trong factory body.
5. Spring giải quyết những bean tương ứng và gọi factory method tạo `CheckoutService`. Trong service, các field vẫn là hai interface Java.
6. `main` gọi `getBean(CheckoutService.class)` để lấy **một entry point**. Đây là nơi thích hợp để dùng context. Sau đó xử lý nghiệp vụ qua `service.place`; service **không giữ `ApplicationContext`**.
7. Đơn `S-1` giữ chỗ 1, còn 2; in `EVENT S-1` rồi `ACCEPTED S-1`. `S-2` yêu cầu 3 nên bị từ chối và không gửi event.
8. Bean `CheckoutService` mặc định singleton trong context, nên lần lookup tiếp theo trả cùng reference và in `SAME_BEAN true`.
9. `try-with-resources` đóng context; Spring thực thi những callback dọn dẹp phù hợp cho bean do mình quản lý.

**Không bắt buộc có `@Autowired` trên constructor `CheckoutService`** trong ví dụ này vì bean được tạo qua `@Bean` factory method nhận dependencies. Cũng không cần gắn `@Service` trên class business hoặc bật component scan; `@Bean` đã đăng ký instance cho container.

### 6.4. Nếu muốn dùng `@Component` và constructor injection tự động?

Một cách khác (không dùng đồng thời với `@Bean checkoutService` nếu không muốn trùng bean) là đánh dấu class là component:

~~~java
@Component
public final class CheckoutService {
    private final InventoryPort inventory;
    private final ReceiptPort receipt;

    public CheckoutService(InventoryPort inventory, ReceiptPort receipt) {
        this.inventory = inventory;
        this.receipt = receipt;
    }
}
~~~

Đây là **đoạn minh họa thiết kế thay thế**, không phải mã sửa vào `ContractDemo.java` ở mục 5. Với component scanning bật cho đúng package và có bean hai interface, Spring có thể chọn **constructor duy nhất** mà không cần `@Autowired` (trên Spring Framework hiện đại). Khi đó business class có annotation Spring, nhưng vẫn không cần tự `getBean`.

Nếu mục tiêu là giữ tầng nghiệp vụ hoàn toàn không biết Spring, cấu hình `@Bean` như ví dụ chính phù hợp hơn; cả hai đều là cách DI, không có cơ chế nào bắt buộc mọi class triển khai contract phải là `@Component`.

## 7. Sai lầm phổ biến: nhìn thấy interface nhưng vẫn bị khóa vào adapter

Ví dụ:

~~~java
final class NotActuallyDecoupled {
    private final InventoryPort inventory =
            new InMemoryInventory(java.util.Map.of("BOOK", 3));
}
~~~

Đây là **mã minh họa không độc lập**: dù field có kiểu `InventoryPort`, class này vẫn quyết định `new InMemoryInventory`. Như vậy đổi adapter vẫn phải sửa class đang tiêu thụ.

Muốn tiêm dựa trên hợp đồng, **constructor nhận `InventoryPort`** chứ không tự tạo nó. Đừng nhầm “biến khai báo interface” với “quyền lựa chọn implementation ở bên ngoài”.

Tương tự, một class có tham số `ApplicationContext context` rồi gọi `getBean(InventoryPort.class)` trong mỗi method vẫn biết nhiều chi tiết container hơn mức cần thiết, dù không nhắc class adapter.

## 8. Thiếu hoặc có nhiều bean cùng contract

### 8.1. Không có ứng viên

Nếu Spring cần tạo `CheckoutService` qua `@Bean checkoutService(InventoryPort,ReceiptPort)` nhưng context không đăng ký `ReceiptPort`, quá trình khởi tạo bean có thể thất bại với lỗi dependency như `UnsatisfiedDependencyException`. Với singleton non-lazy, lỗi có thể xuất hiện **ngay khi refresh context**, trước khi `main` gọi `getBean(CheckoutService.class)`.

Điều này thường tốt hơn lỗi xảy ra muộn ở một nhánh xử lý mà service tự tra `ReceiptPort` khi cần. Nhưng cũng cần nhớ Spring không thể phát hiện hết mọi lỗi nghiệp vụ hoặc tài nguyên chỉ nhờ DI.

### 8.2. Có hai triển khai cùng interface

Giả sử có `ConsoleReceipt` và `EmailReceipt`, đều là bean `ReceiptPort`. Spring có thể không biết bean nào phải được inject nếu không có quy tắc chọn duy nhất.

Một cách chọn rõ trong Java configuration:

~~~java
@Bean
CheckoutService checkoutService(
        InventoryPort inventory,
        @Qualifier("consoleReceipt") ReceiptPort receipt) {
    return new CheckoutService(inventory, receipt);
}
~~~

Đây là **minh họa**, giả định đã định nghĩa các kiểu và bean name tương ứng. Cần import `org.springframework.beans.factory.annotation.Qualifier`.

- `@Qualifier` thu hẹp bean candidates ở injection point cần chỉ định.
- `@Primary` có thể đánh dấu một bean làm ứng viên ưu tiên mặc định.
- Nếu muốn dùng **tất cả** các implementation (ví dụ nhiều notification channels), có thể inject `List<ReceiptPort>` hoặc `Map<String,ReceiptPort>` và xây chiến lược phù hợp, thay vì tùy tiện chọn bean đầu tiên.
- Nếu phải chọn theo dữ liệu runtime từng đơn (email hay SMS), đó là yêu cầu **strategy/router nghiệp vụ**. `@Primary` không quyết định lựa chọn theo dữ liệu của mỗi request.

**Một interface chưa chắc đồng nghĩa chỉ có một bean.** Luôn nghĩ tới tên/qualifier hoặc một chiến lược rõ khi module phát triển.

## 9. Scope, lifecycle và vì sao không nên lookup mỗi lần xử lý

Spring bean mặc định scope `singleton`, thường là một instance cho bean definition trong mỗi container. Gọi `getBean(InventoryPort.class)` nhiều lần trên singleton không tạo mới adapter mỗi lần; nhưng việc lookup rải rác vẫn tạo coupling và che giấu dependency.

Với `prototype`, lời gọi `getBean` có thể tạo object mới mỗi lần được yêu cầu. Đây là một trường hợp khiến nhiều người nghĩ phải truyền nguyên `ApplicationContext` vào service để “tự lấy prototype”.

Thường có lựa chọn tốt hơn: truyền **factory/provider có contract rõ** hoặc `ObjectProvider<T>` ở tầng hạ tầng phù hợp, tùy mục đích.

Ví dụ minh họa:

~~~java
public interface TaskSessionFactory {
    TaskSession newSession();
}

public final class TaskExecutor {
    private final TaskSessionFactory sessions;

    public TaskExecutor(TaskSessionFactory sessions) {
        this.sessions = sessions;
    }

    public void execute() {
        TaskSession session = sessions.newSession();
        // Sử dụng session theo lifecycle đã định nghĩa.
    }
}
~~~

Đây là **mã minh họa**, thiếu định nghĩa `TaskSession`. Ý tưởng: nghiệp vụ cần “tạo session mới” thì khai báo **hợp đồng factory**. Adapter của factory có thể sử dụng Spring `ObjectProvider<TaskSession>` hoặc một cơ chế cấp phát khác, nhưng nghiệp vụ không cần biết `getBean("taskSession")`.

Các ngoại lệ hợp lý để lookup trực tiếp gồm:

- `main` hoặc lớp khởi động lấy một entry-point service.
- Mã framework/infrastructure như factory, plugin registry, extension processor cần truy cập container có chủ đích.
- Tình huống cần dynamic resolution thật sự nhưng đã giới hạn rõ vào một adapter thay vì rải khắp domain.

Đừng biến nguyên tắc “tránh Service Locator trong nghiệp vụ” thành khẩu hiệu “cấm tuyệt đối `getBean`”; vấn đề là **vị trí và mức coupling**.

## 10. Test độc lập Spring và test wiring qua container

### 10.1. Unit test nghiệp vụ

`ContractDemo.main` đã dùng fake cho hai interface. Một unit test thực tế (JUnit hoặc framework khác) có thể tách thành:

- **Happy path:** inventory trả true, receipt được gọi đúng một lần với ID chính xác, result accepted.
- **Không đủ hàng:** inventory trả false, receipt không được gọi, result rejected.
- **Input invalid:** `new Order("X", "BOOK", 0)` ném `IllegalArgumentException`.
- **Dependency thiếu:** `new CheckoutService(null, receipt)` ném `NullPointerException` ở constructor.
- **Lỗi collaborator:** inventory ném exception thì receipt không chạy; receipt ném exception sau reserve thì cần chính sách khôi phục rõ ràng.
- **Lựa chọn implementation:** thay fake bằng adapter thực không đòi sửa cách service gọi contract.

Những test này **không cần khởi động Spring**. Đó là lợi ích lớn của việc giữ service nhận interface qua constructor và không import `ApplicationContext`.

### 10.2. Integration test cấu hình

Để xác minh container thực sự nối bean đúng, tạo context từ `AppConfig`, lấy `CheckoutService` và thử một use case. Với nhiều profiles/implementations, kiểm tra các trường hợp thiếu dependency hoặc mơ hồ có lỗi sớm/đúng kỳ vọng.

Không nên viết mọi unit test bằng cách khởi động toàn bộ Spring context nếu code cần test thuần túy là Java business logic; việc đó làm test chậm, khó cô lập lỗi và phụ thuộc cấu hình ngoài hơn mức cần thiết.

### 10.3. Không nhầm DI với transaction hay tính nhất quán nghiệp vụ

Constructor injection chỉ xác định **ai sử dụng dependency nào**. Nếu use case phải ghi database và phát message bảo đảm không mất sự kiện, cần transaction/outbox/idempotency theo kiến trúc phù hợp. `@Bean` hay `@Autowired` **không tự** bảo đảm hai thao tác reserve/publish atomic.

## 11. Thay implementation theo môi trường

Hợp đồng `InventoryPort` cho phép thay adapter qua Spring configuration. Ví dụ:

~~~java
// Đây là ví dụ cấu hình minh họa, không phải file runnable độc lập.
@Bean
InventoryPort inventory(DataSource dataSource) {
    return new JdbcInventory(dataSource);
}
~~~

Với mã này cần định nghĩa `JdbcInventory`, cấu hình `DataSource` và JDBC driver thật; nó **không** thể chạy chỉ bằng dependency `spring-context` của ví dụ chính.

Những cách lựa chọn adapter hay gặp:

1. **Khai báo `@Bean` theo môi trường**: dự án local dùng RAM, production dùng JDBC hoặc API client.
2. **`@Profile` / `@Conditional`**: đăng ký bean tùy profile/điều kiện; phải kiểm tra dependency candidates và môi trường thực tế.
3. **`@Primary` / `@Qualifier`**: chọn mặc định hoặc một ứng viên cụ thể khi nhiều implementation cùng type.
4. **Cấu hình factory riêng**: nếu adapter cần credentials/client pool, đặt construction và lifecycle ở configuration/infrastructure.
5. **Adapter qua interface nghiệp vụ**: tránh đưa `DataSource` hoặc `ApplicationContext` thẳng vào service chỉ để tự quyết định adapter bên trong.

Thay implementation không đảm bảo hành vi tương đương nếu semantics đổi. Một adapter mới phải giữ những điều hợp đồng đã hứa: `reserve` true/false khi nào, có side effect nào, lỗi được báo thế nào.

## 12. Nguyên tắc thiết kế chi tiết

### 12.1. Dependency Inversion Principle (DIP) và DI

- **DIP** là nguyên tắc thiết kế: module cấp cao không nên phụ thuộc chặt vào module hạ tầng cấp thấp; cả hai hướng tới abstraction thích hợp.
- **DI** là kỹ thuật cung cấp dependency cho object từ bên ngoài, có thể thực hiện với Java thuần hoặc container như Spring.
- **IoC container** là một công cụ tổ chức object creation/configuration/lifecycle; không đồng nghĩa DIP hoặc DI.
- **Interface** là một phương tiện mô tả contract trong Java, không tự động tạo DI.

Có thể dùng constructor injection vào **class concrete** mà vẫn có DI; và cũng có thể khai báo interface nhưng tự tạo implementation bên trong service, khiến coupling còn nguyên. Chất lượng thiết kế phụ thuộc **ranh giới trách nhiệm**, không chỉ số lượng interface.

### 12.2. Dependency nên “chỉ thứ bạn cần”

Nếu một service chỉ cần đọc trạng thái hàng tồn, đừng buộc nó phụ thuộc một interface quản trị kho với hàng trăm method. Tạo contract tối thiểu phù hợp consumer, chẳng hạn `InventoryQuery`, khi điều đó có lợi.

Không có lý do bắt mọi value object như `Order`, `Money` hay `Address` trở thành singleton Spring beans. Chúng thường chứa dữ liệu từng use case, được tạo theo đời sống nghiệp vụ của chúng.

### 12.3. Composition root là nơi được phép biết concrete implementation

Composition root có quyền biết cả `InMemoryInventory` lẫn `CheckoutService`, vì nhiệm vụ của nó là **lắp object graph**. Nếu dùng Spring, `@Configuration`/`@Bean` ở lớp hạ tầng là một cách biểu diễn composition root đó.

Không nên áp dụng nguyên tắc “domain không biết concrete adapter” thành “configuration cũng không được biết adapter”, vì khi đó chẳng còn ai quyết định implementation được dùng.

## 13. Sai lầm thường gặp và cách nhận diện

| Sai lầm / triệu chứng | Cách hiểu và hướng xử lý |
| --- | --- |
| Dùng `new JdbcRepository()` bên trong mọi service | Service chọn hạ tầng; hãy tiêm collaborator từ cấu hình |
| Đổi field thành interface nhưng vẫn tự `new Concrete()` | Interface alone không làm thay đổi nơi lựa chọn implementation |
| Tiêm `ApplicationContext` vào toàn bộ domain | Dễ tạo Service Locator; khai báo dependency thực bằng constructor |
| Tưởng `getBean` tự xấu ở mọi vị trí | Lookup ở entry point/factory hạ tầng có thể hợp lý |
| Interface thiết kế theo SQL/client API | Contract nên biểu diễn năng lực consumer cần |
| Constructor có hàng chục collaborator không liên quan | Cần xem lại trách nhiệm service, tách use case/aggregator |
| Bean thiếu nhưng lỗi chỉ xảy ra khi xử lý đơn cụ thể | Kiểm tra hidden lookup/lazy dependencies, cho required dependency ra constructor |
| `NoSuchBeanDefinitionException` | Xem bean registration, type, profile và context đúng |
| `NoUniqueBeanDefinitionException` | Xem các ứng viên, `@Primary`/`@Qualifier` hoặc chiến lược |
| Bean được tạo tay rồi mong có AOP/transaction | Object ngoài Spring container không tự được proxy/intercept |
| `@Bean` trả một interface rồi lookup concrete class luôn đúng | Bean metadata/proxy/type exposure có thể khiến lookup concrete không phù hợp; ưu tiên contract đã đăng ký |
| Singleton bean được hiểu là thread-safe | Scope không bảo đảm concurrency; xem mutable state |
| Fake chỉ assert không ném exception | Phải assert result, số lần gọi và đúng argument |
| Adapter thật lỗi rồi tưởng DI tự rollback | Transaction/consistency là cơ chế khác |
| Service tự lấy prototype bằng `getBean` mỗi lần | Cân nhắc factory/provider injected có phạm vi rõ |
| Tất cả class trong dự án đều cần `@Component` | Bean registration chỉ cần cho những object container phải quản lý |

## 14. Bài tập kèm đáp án

**Bài 1.** `CheckoutService` chỉ cần gọi `inventory.reserve`; tại sao nó không nên nhận trực tiếp `DataSource` rồi tự chạy SQL?

**Đáp án:** Khi đó service phải biết chi tiết persistence. `InventoryPort` mô tả đúng năng lực cần, còn adapter có thể dùng JDBC bên ngoài nghiệp vụ.

**Bài 2.** Chuyển field từ `JdbcInventory` sang `InventoryPort` nhưng vẫn `new JdbcInventory()` trong service đã là DI chưa?

**Đáp án:** Chưa đúng mục tiêu DI ở ranh giới này. Service vẫn tự chọn và tạo collaborator; nên nhận `InventoryPort` từ constructor hoặc injection point khác.

**Bài 3.** `main` gọi `context.getBean(CheckoutService.class)` có phá nguyên tắc tránh Service Locator không?

**Đáp án:** Không nhất thiết. `main` là entry point/composition boundary; một lookup có chủ đích để bắt đầu use case khác với business service liên tục tự tìm các dependency ẩn.

**Bài 4.** Tại sao `ReceiptPort fakeReceipt = events::add` trong mã Java 17 là hợp lệ?

**Đáp án:** `ReceiptPort` có một abstract method `void publish(String)`. Method reference tới `List.add(String)` trả boolean nhưng Java có thể sử dụng method invocation đó trong ngữ cảnh hàm trả void, bỏ qua giá trị trả về. Vì thế mã dùng được như một implementation của functional interface.

**Bài 5.** Nếu có hai bean cùng kiểu `ReceiptPort`, Spring chắc chắn chọn bean khai báo trước chứ?

**Đáp án:** Không. Cần cơ chế lựa chọn rõ, ví dụ qualifier/primary hoặc thiết kế injection nhiều bean có chủ đích.

**Bài 6.** Nếu service có `InventoryPort` và `ReceiptPort` qua constructor, unit test có cần Spring context không?

**Đáp án:** Không. Truyền fake implementations bằng Java thuần và assert output/calls.

**Bài 7.** Nếu thêm database adapter làm thay đổi chính sách trả true/false của `reserve`, có phải service chắc chắn vẫn đúng?

**Đáp án:** Không. Thay implementation chỉ an toàn khi contract/semantics thực sự được giữ; kiểm thử adapter là cần thiết.

**Bài 8.** Nếu cần một object `TaskSession` mới cho mỗi lần xử lý, giải pháp duy nhất là tiêm `ApplicationContext` vào domain đúng không?

**Đáp án:** Không. Có thể tiêm `TaskSessionFactory` interface hoặc một provider được bọc trong adapter hạ tầng. Domain chỉ cần chức năng “tạo session mới”.

**Bài 9.** Vì sao Spring `@Bean` factory method không phải nơi vi phạm quy tắc “service không tự `new` collaborator”?

**Đáp án:** Configuration/composition root có trách nhiệm tạo và chọn implementation; `new` ở factory method là hợp lý. Điều tránh là **business service** tự ôm nhiệm vụ hạ tầng này.

## 15. Checklist áp dụng

- [ ] Liệt kê những collaborator service thật sự cần để thực hiện một use case.
- [ ] Đặt contract theo nghiệp vụ consumer, không theo API SQL/SMTP/HTTP chi tiết.
- [ ] Cho required dependencies xuất hiện trực tiếp ở constructor.
- [ ] Kiểm tra không null/invariant khi tạo service nếu cần.
- [ ] Không tự `new` adapter hạ tầng trong service.
- [ ] Không dùng `ApplicationContext.getBean` rải trong các business methods.
- [ ] Cho composition root/`@Configuration` quyền quyết định concrete adapters.
- [ ] Bảo đảm mỗi contract có đúng bean phù hợp hoặc quy tắc chọn rõ.
- [ ] Dùng fake để unit test service không cần Spring context.
- [ ] Kiểm thử wiring của Spring riêng với kiểm thử nghiệp vụ.
- [ ] Nếu cần tạo instance theo yêu cầu, ưu tiên factory/provider contract thay việc phát tán container API vào domain.
- [ ] Xác định scope/lifecycle của collaborator và tài nguyên có state.
- [ ] Không mặc định DI cung cấp transaction, locking, retry hoặc rollback.
- [ ] Test semantics và lỗi của mỗi implementation, không chỉ kiểm tra interface compile được.
- [ ] Không tạo abstraction thừa cho mọi class chỉ để “đúng pattern”.

### Tài liệu Spring để đối chiếu

- [Dependency Injection và collaborators](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)
- [ApplicationContextAware và giới hạn coupling](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html)
- [Java-based bean configuration](https://docs.spring.io/spring-framework/reference/core/beans/java/basic-concepts.html)

**Kết luận:** Với DI dựa trên hợp đồng, `CheckoutService` nhận `InventoryPort` và `ReceiptPort` từ bên ngoài, không tự tạo/tìm chúng. Java constructor injection giúp dependency hiện rõ và unit test dễ; Spring Core Container chỉ thay composition root thủ công bằng cấu hình bean và lifecycle quản lý. Service Locator đôi khi hữu ích ở ranh giới hạ tầng, nhưng tránh để các lớp nghiệp vụ biết container khi chỉ cần những năng lực được mô tả bằng interface.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dependency-injection-008">Nhận diện quá nhiều phụ thuộc và trách nhiệm cần tách</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện quá nhiều phụ thuộc và trách nhiệm cần tách — Spring Framework Core Container

## 1. Mục tiêu: constructor dài là dấu hiệu cần phân tích

Constructor injection giúp dependency của một class trở nên **hiện rõ trong chữ ký**. Chính vì vậy, khi một service phải nhận 6–10 collaborators, bạn sẽ nhìn thấy ngay độ phức tạp mà field injection hoặc service locator trước đó có thể đã che đi.

**Nhiều tham số không tự động là lỗi.** Không có quy tắc của Java hay Spring rằng “constructor từ 5 tham số trở lên là sai”. Cần xem **các dependency đại diện những trách nhiệm nào**, những thay đổi nghiệp vụ nào khiến class bị sửa, mức độ liên kết của các method và độ khó kiểm thử.

Ví dụ `OrderService` cần xử lý báo giá, kiểm tra kho, thu tiền, sắp xếp giao hàng, gửi biên nhận và audit. Một constructor nhận 7 dependency có thể là triệu chứng class đã ôm nhiều lý do thay đổi. Refactor tốt là **tách các trách nhiệm có ranh giới rõ**, không phải chỉ giấu bảy tham số trong một object khác.

Sau bài này, bạn có thể:

1. Nhận diện dấu hiệu của **quá nhiều phụ thuộc**, **low cohesion**, **high coupling** và class có nhiều lý do thay đổi.
2. Phân biệt **số dependency** với **chất lượng thiết kế**; đánh giá cả thời điểm cùng thay đổi, nhóm method và nhiệm vụ thật của service.
3. Lập bản đồ một class đang sử dụng collaborator nào cho từng nghiệp vụ, tìm cụm trách nhiệm có thể tách.
4. Refactor `OrderService` thành `QuoteService`, `PurchaseService`, `FulfillmentService` và `OrderWorkflow` có cấu trúc dependency minh bạch.
5. Wiring các lớp sau tách bằng `@Configuration`/`@Bean` và **constructor injection** trong Spring Framework thuần.
6. Kiểm chứng bằng Java 17+, Maven, `javac`/`java` và stdout xác định.
7. Tránh anti-pattern: `ApplicationContext.getBean(...)` trong nghiệp vụ, “God facade”, generic dependency bag, truyền một container khổng lồ và trừu tượng hóa quá mức.
8. Thiết kế unit test và smoke test để bảo đảm refactor **giữ nguyên hành vi** ở các điều kiện quan trọng.

**Phạm vi:** Đây là kỹ thuật **đánh giá và tách trách nhiệm từ dấu hiệu quá nhiều constructor dependencies** trong chương *Tiêm phụ thuộc*. Không cần Spring Boot, database, message broker, kiến trúc microservices hay DI framework khác.

## 2. Ví dụ trước refactor: một class giải quyết quá nhiều việc

Hãy tưởng tượng một service thực hiện toàn bộ quy trình:

~~~java
// Phác thảo trước refactor: KHÔNG phải file Java đầy đủ.
final class OrderService {
    private final PriceCatalog catalog;
    private final DiscountPolicy discount;
    private final StockGateway stock;
    private final PaymentGateway payment;
    private final ShippingGateway shipping;
    private final ReceiptSender receipts;
    private final AuditSink audit;

    OrderService(
            PriceCatalog catalog,
            DiscountPolicy discount,
            StockGateway stock,
            PaymentGateway payment,
            ShippingGateway shipping,
            ReceiptSender receipts,
            AuditSink audit) {
        this.catalog = java.util.Objects.requireNonNull(catalog);
        this.discount = java.util.Objects.requireNonNull(discount);
        this.stock = java.util.Objects.requireNonNull(stock);
        this.payment = java.util.Objects.requireNonNull(payment);
        this.shipping = java.util.Objects.requireNonNull(shipping);
        this.receipts = java.util.Objects.requireNonNull(receipts);
        this.audit = java.util.Objects.requireNonNull(audit);
    }

    // quote(...), buy(...), ship(...), notify(...), audit(...) ...
}
~~~

Đoạn trên chỉ minh họa **dependency list**; dấu `...` ở comment không phải mã nghiệp vụ runnable. Một service như vậy có thể có nhiều method, mỗi method chỉ sử dụng một phần trong bảy collaborators.

| Nhóm nghiệp vụ | Collaborators tương ứng | Lý do dễ phải thay đổi |
| --- | --- | --- |
| Báo giá | `PriceCatalog`, `DiscountPolicy` | Quy tắc giá và khuyến mãi |
| Đặt mua/thu tiền | `StockGateway`, `PaymentGateway` | Tồn kho và tích hợp thanh toán |
| Hoàn tất đơn | `ShippingGateway`, `ReceiptSender`, `AuditSink` | Vận chuyển, thông báo, nhật ký kiểm toán |

Nếu class `OrderService` bị sửa khi thay cách tính khuyến mãi, khi đổi nhà cung cấp thanh toán **và** khi thêm kênh gửi biên nhận, đó là nhiều **lý do thay đổi** nằm trong một lớp. Người bảo trì phải hiểu rộng hơn phần họ đang sửa, trong khi tests cho báo giá có thể phải tạo cả bảy fake không liên quan.

**Tuy nhiên:** không phải cứ có bảy constructor parameters là chắc chắn vi phạm Single Responsibility Principle (SRP). Một adapter orchestration thật sự có nhiệm vụ điều phối bảy collaborator và không chứa các logic riêng đôi khi vẫn hợp lý, nhất là ở ranh giới application service. Hãy quan sát các method và thay đổi thực tế trước khi kết luận.

## 3. Những dấu hiệu quan trọng hơn một con số

### 3.1. Method clusters dùng những nhóm dependency khác nhau

Lập ma trận:

| Method trong lớp cũ | Catalog | Discount | Stock | Payment | Shipping | Receipt | Audit |
| --- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `quote()` | X | X | | | | | |
| `charge()` | | | X | X | | | |
| `fulfill()` | | | | | X | X | X |
| `placeOrder()` | X | X | X | X | X | X | X |

Ma trận thể hiện ba cụm khá độc lập và một method **điều phối** gọi các bước. Nếu `placeOrder()` còn tự implement các công thức giá, trạng thái thanh toán và formatting biên nhận, nó trở thành “god method”. Nếu method ấy chỉ gọi các bước đã tách rõ, nó có vai trò orchestration dễ hiểu.

### 3.2. Cohesion và coupling

- **Cohesion (độ gắn kết):** các method và dữ liệu của một class có cùng phục vụ một trách nhiệm rõ ràng không? Một `QuoteService` xử lý các phép tính báo giá thường cohesive hơn class vừa báo giá vừa gửi email.
- **Coupling (mức độ phụ thuộc):** lớp cần biết bao nhiêu API hoặc chi tiết dễ đổi? Class phụ thuộc vào quá nhiều client cụ thể có thể bị kéo theo thay đổi ở nhiều hệ thống.
- **Change coupling:** nhiều feature/bugfix khác nhau thường sửa cùng class vì những trách nhiệm không liên quan. Nếu mỗi lần đổi kênh thông báo đều có nguy cơ ảnh hưởng tính giá, ranh giới đang kém rõ.
- **Test friction:** unit test một quy tắc báo giá vẫn phải dựng payment/stock/shipping/email mocks mà không dùng tới; đây là tín hiệu liên kết không cần thiết.
- **Độ khó nêu trách nhiệm:** nếu câu giải thích class phải nối “và… và… và…”, cần thử viết lại hợp đồng ngắn gọn hơn.

### 3.3. Dấu hiệu cần kiểm chứng, không phải quy tắc tuyệt đối

Một constructor với hai dependencies vẫn có thể trộn hai trách nhiệm hoàn toàn khác nhau. Một class infrastructure như composite engine, adapter hoặc orchestrator đôi khi cần năm collaborators nhưng có **một nhiệm vụ nhất quán**. Kết luận dựa vào **lý do thay đổi và mối quan hệ trong nghiệp vụ**, không chỉ số dòng code, số fields hay số tham số.

Không tách class chỉ để vượt qua một rule linter kiểu `max-params=4`. Nếu việc tách khiến bạn phải nhảy qua tám lớp pass-through mà không cải thiện hiểu biết, có thể đó là **overengineering**.

## 4. Lập bản đồ trách nhiệm trước khi động vào code

Quy trình hữu ích:

1. **Đọc public API:** các operation nào thật sự thuộc class?
2. **Liệt kê dependency và vai trò:** mỗi dependency được dùng ở method nào, cho một kết quả gì?
3. **Nhóm những method thay đổi vì cùng lý do:** pricing/discount, purchase/stock/payment, fulfillment/shipping/receipt/audit.
4. **Xác định điểm giao:** lớp điều phối chỉ nên giữ việc kết nối các bước, không copy toàn bộ thuật toán của chúng.
5. **Tách một cụm trước:** dùng kỹ thuật Extract Class/Extract Service; đưa dữ liệu đầu vào/đầu ra qua kiểu có ý nghĩa như record.
6. **Viết unit test của cụm vừa tách**, rồi thêm Spring wiring test để kiểm tra bean candidate.
7. **Lặp lại theo bằng chứng** cho tới khi mỗi lớp có trách nhiệm rõ; dừng nếu tách thêm chỉ tạo forwarding methods.
8. **So hành vi trước–sau**, đặc biệt các lỗi payment/stock, ordering của side effects và boundary conditions.

### Mục tiêu thiết kế sau refactor

~~~mermaid
flowchart TD
    W["OrderWorkflow"] --> P["PurchaseService"]
    W --> F["FulfillmentService"]
    P --> Q["QuoteService"]
    P --> S["StockGateway"]
    P --> Pay["PaymentGateway"]
    Q --> Cat["PriceCatalog"]
    Q --> D["DiscountPolicy"]
    F --> Ship["ShippingGateway"]
    F --> R["ReceiptSender"]
    F --> A["AuditSink"]
~~~

**Đọc sơ đồ theo chiều consumer → dependency.** `OrderWorkflow` điều phối hai bước lớn. `QuoteService` chỉ tính giá; `PurchaseService` phối hợp báo giá/kho/thanh toán; `FulfillmentService` sắp xếp giao hàng và ghi nhận việc hoàn tất.

Các nhóm mới không nhất thiết ít tổng bean hơn class cũ. Mục tiêu là **giảm phạm vi hiểu và phạm vi thay đổi của mỗi thành phần**. Dependency graph vẫn biểu diễn đúng nghiệp vụ, thay vì “giấu” một graph phức tạp sau một interface mơ hồ.

## 5. Những thay đổi cụ thể trong thiết kế

| Trước | Sau | Lợi ích |
| --- | --- | --- |
| `OrderService` tự xử lý bảng giá và giảm giá | `QuoteService` phụ thuộc `PriceCatalog` và `DiscountPolicy` | Test báo giá độc lập khỏi gateway bên ngoài |
| `OrderService` tự giữ logic stock/payment | `PurchaseService` phụ thuộc `QuoteService`, `StockGateway`, `PaymentGateway` | Tập trung hợp đồng đặt mua và thu tiền |
| `OrderService` tự gọi shipping/receipt/audit | `FulfillmentService` phụ thuộc ba collaborator có liên quan đến hoàn tất | Thay đổi kênh gửi/hậu cần không ảnh hưởng công thức tính giá |
| Method chính biết đủ bảy collaborators | `OrderWorkflow` nhận `PurchaseService` và `FulfillmentService` | Orchestration dễ đọc, ít chi tiết |
| Dữ liệu chuyền bằng nhiều tham số rời rạc | `OrderRequest`, `Quote` và `Purchase` là các records mang thông tin có ý nghĩa | Hợp đồng rõ, giảm nhầm thứ tự tham số |

**Cẩn thận với phương thức thanh toán và side effects:** tách class không tự sinh transaction phân tán. Ví dụ demo đặt chỗ trong kho trước khi thu tiền, rồi mới xếp giao hàng. Nếu thu tiền lỗi sau khi giữ kho, hệ thống thực cần compensation/idempotency/transaction policy. Việc refactor **không được lén thay thứ tự side effects** nếu hợp đồng trước đây phụ thuộc vào thứ tự.

## 6. Ví dụ Spring Framework + Java 17+ có thể chạy

Project minh họa dùng **Spring Framework 6.2.19** (cố định cho ví dụ, không hàm ý mới nhất), JDK 17+ và Maven. Không cần Spring Boot, database, HTTP server hay một payment provider thật. Các gateway được thay bằng implementation **deterministic trong bộ nhớ** để quan sát ranh giới trách nhiệm.

~~~text
order-responsibilities-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── OrderResponsibilitiesDemo.java
~~~

### 6.1. File pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>order-responsibilities-demo</artifactId>
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
                    <mainClass>demo.OrderResponsibilitiesDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Maven tải thêm dependencies transitively từ `spring-context`. Muốn dùng `javac` trực tiếp phải cung cấp các Spring JAR liên quan cho compiler/launcher.

### 6.2. File src/main/java/demo/OrderResponsibilitiesDemo.java

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class OrderResponsibilitiesDemo {
    private OrderResponsibilitiesDemo() {}

    public record OrderRequest(String sku, int quantity) {
        public OrderRequest {
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("sku required");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
        }
    }

    public record Quote(OrderRequest request, int totalCents) {}
    public record Purchase(OrderRequest request, int totalCents,
                           String paymentId) {}

    public interface PriceCatalog {
        int unitPrice(String sku);
    }

    public interface DiscountPolicy {
        int discountCents(int subtotal);
    }

    public interface StockGateway {
        void reserve(String sku, int quantity);
    }

    public interface PaymentGateway {
        String charge(int totalCents);
    }

    public interface ShippingGateway {
        String schedule(String sku, int quantity);
    }

    public interface ReceiptSender {
        void send(String receipt);
    }

    public interface AuditSink {
        void record(String event);
    }

    public static final class RecordingReceiptSender
            implements ReceiptSender {
        private final List<String> sent = new ArrayList<>();

        @Override
        public void send(String receipt) {
            sent.add(receipt);
        }

        public List<String> snapshot() {
            return List.copyOf(sent);
        }
    }

    public static final class RecordingAuditSink implements AuditSink {
        private final List<String> events = new ArrayList<>();

        @Override
        public void record(String event) {
            events.add(event);
        }

        public List<String> snapshot() {
            return List.copyOf(events);
        }
    }

    public static final class QuoteService {
        private final PriceCatalog catalog;
        private final DiscountPolicy discount;

        public QuoteService(PriceCatalog catalog, DiscountPolicy discount) {
            this.catalog = Objects.requireNonNull(catalog);
            this.discount = Objects.requireNonNull(discount);
        }

        public Quote quote(OrderRequest request) {
            Objects.requireNonNull(request);
            int unitPrice = catalog.unitPrice(request.sku());
            if (unitPrice <= 0) {
                throw new IllegalArgumentException("invalid unit price");
            }
            int subtotal = Math.multiplyExact(unitPrice, request.quantity());
            int reduction = discount.discountCents(subtotal);
            if (reduction < 0 || reduction > subtotal) {
                throw new IllegalArgumentException("invalid discount");
            }
            return new Quote(request, Math.subtractExact(subtotal, reduction));
        }
    }

    public static final class PurchaseService {
        private final QuoteService quotes;
        private final StockGateway stock;
        private final PaymentGateway payments;

        public PurchaseService(
                QuoteService quotes, StockGateway stock,
                PaymentGateway payments) {
            this.quotes = Objects.requireNonNull(quotes);
            this.stock = Objects.requireNonNull(stock);
            this.payments = Objects.requireNonNull(payments);
        }

        public Purchase buy(OrderRequest request) {
            Quote quote = quotes.quote(request);
            stock.reserve(request.sku(), request.quantity());
            String paymentId = payments.charge(quote.totalCents());
            return new Purchase(request, quote.totalCents(), paymentId);
        }
    }

    public static final class FulfillmentService {
        private final ShippingGateway shipping;
        private final ReceiptSender receipts;
        private final AuditSink audit;

        public FulfillmentService(
                ShippingGateway shipping, ReceiptSender receipts,
                AuditSink audit) {
            this.shipping = Objects.requireNonNull(shipping);
            this.receipts = Objects.requireNonNull(receipts);
            this.audit = Objects.requireNonNull(audit);
        }

        public String fulfill(Purchase purchase) {
            String shipmentId = shipping.schedule(
                    purchase.request().sku(), purchase.request().quantity());
            String result = "payment=" + purchase.paymentId()
                    + ",shipment=" + shipmentId
                    + ",total=" + purchase.totalCents();
            receipts.send(result);
            audit.record("fulfilled:" + purchase.request().sku());
            return result;
        }
    }

    public static final class OrderWorkflow {
        private final PurchaseService purchases;
        private final FulfillmentService fulfillment;

        public OrderWorkflow(
                PurchaseService purchases,
                FulfillmentService fulfillment) {
            this.purchases = Objects.requireNonNull(purchases);
            this.fulfillment = Objects.requireNonNull(fulfillment);
        }

        public String run(OrderRequest request) {
            Purchase purchase = purchases.buy(request);
            return fulfillment.fulfill(purchase);
        }

        public PurchaseService purchases() {
            return purchases;
        }

        public FulfillmentService fulfillment() {
            return fulfillment;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        public PriceCatalog priceCatalog() {
            return sku -> {
                if (!"BOOK".equals(sku)) {
                    throw new IllegalArgumentException("unknown sku");
                }
                return 150;
            };
        }

        @Bean
        public DiscountPolicy discountPolicy() {
            return subtotal -> subtotal >= 300 ? 20 : 0;
        }

        @Bean
        public StockGateway stockGateway() {
            return (sku, quantity) -> {
                if (quantity > 5) {
                    throw new IllegalArgumentException("not enough stock");
                }
            };
        }

        @Bean
        public PaymentGateway paymentGateway() {
            return amount -> "PAY-" + amount;
        }

        @Bean
        public ShippingGateway shippingGateway() {
            return (sku, quantity) -> "SHIP-" + sku + "-" + quantity;
        }

        @Bean
        public RecordingReceiptSender receiptSender() {
            return new RecordingReceiptSender();
        }

        @Bean
        public RecordingAuditSink auditSink() {
            return new RecordingAuditSink();
        }

        @Bean
        public QuoteService quoteService(
                PriceCatalog catalog, DiscountPolicy discount) {
            return new QuoteService(catalog, discount);
        }

        @Bean
        public PurchaseService purchaseService(
                QuoteService quotes, StockGateway stock,
                PaymentGateway payments) {
            return new PurchaseService(quotes, stock, payments);
        }

        @Bean
        public FulfillmentService fulfillmentService(
                ShippingGateway shipping, ReceiptSender receipts,
                AuditSink audit) {
            return new FulfillmentService(shipping, receipts, audit);
        }

        @Bean
        public OrderWorkflow orderWorkflow(
                PurchaseService purchases, FulfillmentService fulfillment) {
            return new OrderWorkflow(purchases, fulfillment);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderWorkflow workflow = context.getBean(OrderWorkflow.class);
            QuoteService quotes = context.getBean(QuoteService.class);
            RecordingReceiptSender sent =
                    context.getBean(RecordingReceiptSender.class);
            RecordingAuditSink audit =
                    context.getBean(RecordingAuditSink.class);

            OrderRequest request = new OrderRequest("BOOK", 2);
            System.out.println("workflowWired=" +
                    (workflow.purchases() == context.getBean(PurchaseService.class)
                     && workflow.fulfillment()
                     == context.getBean(FulfillmentService.class)));
            System.out.println("quoteTotal=" +
                    quotes.quote(request).totalCents());
            System.out.println("result=" + workflow.run(request));
            System.out.println("sent=" + sent.snapshot());
            System.out.println("audit=" + audit.snapshot());
            System.out.println("workflowSingleton=" +
                    (workflow == context.getBean(OrderWorkflow.class)));
        }
    }
}
~~~

**Đảm bảo bản demo có thể hiểu độc lập:**

- Một top-level public class `OrderResponsibilitiesDemo` khớp file; interfaces, records, services và config nằm trong cùng class dưới dạng nested types.
- Hai records `OrderRequest` và `Quote`/`Purchase` dùng Java 17. `OrderRequest` kiểm tra SKU và quantity tại constructor, `QuoteService` dùng `Math.multiplyExact`/`Math.subtractExact` để không âm thầm overflow.
- `QuoteService` nhận **hai collaborators** (giá và chiết khấu), không cần biết stock/payment/shipping.
- `PurchaseService` nhận **ba collaborators** (quote, stock, payment), chỉ thực hiện giai đoạn đặt mua.
- `FulfillmentService` nhận **ba collaborators** (shipping, receipts, audit), không tính tiền.
- `OrderWorkflow` nhận **hai service đã có nghĩa** và nối hai bước. Không chứa bảy gateway chi tiết, không phụ thuộc Spring API trong code nghiệp vụ.
- `@Bean` factory methods trong `AppConfig` dùng **method parameters để Spring resolve**. Không gọi trực tiếp các method `@Bean` khác ở chế độ `proxyBeanMethods=false`.
- Các gateway fake và hai recording adapters được quản lý bởi container; fake payment tạo mã `PAY-280`, fake shipping tạo `SHIP-BOOK-2`.
- Bộ đếm/recording adapters dùng `ArrayList` vì demo gọi đơn luồng. Nếu bean singleton có nhiều requests đồng thời, cần thiết kế concurrency và lifecycle thích hợp; `final` references không tự làm list thread-safe.
- Chương trình có đúng một lần thực hiện workflow; câu lệnh `quotes.quote(...)` trước đó chỉ tính dữ liệu và **không** reserve/charge/ship. Không có random, clock hoặc mã hash trong stdout.

### 6.3. Chạy bằng Maven và stdout cố định

Mở terminal ở thư mục `order-responsibilities-demo`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

**Sáu dòng stdout chính xác do mã `main` in khi chạy thành công:**

~~~text
workflowWired=true
quoteTotal=280
result=payment=PAY-280,shipment=SHIP-BOOK-2,total=280
sent=[payment=PAY-280,shipment=SHIP-BOOK-2,total=280]
audit=[fulfilled:BOOK]
workflowSingleton=true
~~~

Giải thích `quoteTotal=280`: giá `150` × số lượng `2` = `300`; `DiscountPolicy` giảm `20` khi subtotal đạt ít nhất `300`, còn `280`. `PurchaseService` chuyển `280` cho payment fake, sau đó `FulfillmentService` ghi biên nhận và audit.

Các lệnh version và log Maven/Spring (nếu có) không thuộc **sáu dòng do chương trình in**. Các gateway ở đây là fake, nên mã `PAY-280` không chứng minh có giao dịch ngân hàng thật.

### 6.4. Chạy bằng javac/java trực tiếp

Spring API phải nằm trên compile/runtime classpath. Tạo file chứa đầy đủ dependencies:

~~~bash
mvn -q dependency:build-classpath -Dmdep.outputFile=classpath.txt
~~~

**Linux/macOS (bash/zsh):**

~~~bash
mkdir -p out
javac --release 17 -encoding UTF-8 \
  -cp "$(cat classpath.txt)" \
  -d out src/main/java/demo/OrderResponsibilitiesDemo.java
java -cp "out:$(cat classpath.txt)" demo.OrderResponsibilitiesDemo
~~~

**Windows PowerShell:**

~~~powershell
$deps = (Get-Content -Raw classpath.txt).Trim()
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -encoding UTF-8 -cp "$deps" -d out src/main/java/demo/OrderResponsibilitiesDemo.java
java -cp "out;$deps" demo.OrderResponsibilitiesDemo
~~~

Classpath separator trên Unix là `:` và trên Windows là `;`. Hai cách chạy thực hiện cùng phương thức `main`, cho cùng sáu dòng output. Nếu `javac` báo không tìm thấy `org.springframework...`, vấn đề đầu tiên là classpath/dependency chứ chưa phải lỗi constructor injection.

## 7. Tại sao bản tách trách nhiệm tốt hơn?

### 7.1. Giảm các lý do thay đổi cho từng lớp

Giả sử thêm một loại chiết khấu: thường sửa `DiscountPolicy` hoặc logic quote; không cần chỉnh sửa code shipping hoặc sender. Đổi email template: thay implementation `ReceiptSender` mà không cần đụng `QuoteService`. Nếu thay phương thức thanh toán, thay implementation `PaymentGateway` và kiểm thử `PurchaseService`.

Quan hệ này hữu ích hơn việc đo constructor length:

| Nhu cầu thay đổi | Trọng tâm bị ảnh hưởng |
| --- | --- |
| Đổi bảng giá/chiết khấu | `QuoteService` và collaborator của nó |
| Thay quy tắc giữ hàng/thu tiền | `PurchaseService` và gateways |
| Thay vận chuyển/thông báo | `FulfillmentService` và adapters |
| Thay thứ tự bước lớn trong use case | `OrderWorkflow` |

Điều này không có nghĩa **không bao giờ** sửa nhiều lớp cùng lúc. Một feature xuyên suốt có thể cần cập nhật cả workflow và hợp đồng dữ liệu, nhưng ranh giới rõ sẽ làm thay đổi dễ đánh giá hơn.

### 7.2. Giảm chi phí test

Trước refactor, test một phép tính giá có thể phải tạo/mock bảy dependencies để khởi tạo `OrderService`. Sau refactor, unit test `QuoteService` chỉ cần `PriceCatalog` và `DiscountPolicy`.

~~~java
// Snippet minh họa trong phạm vi truy cập các nested types của demo:
PriceCatalog prices = sku -> 100;
DiscountPolicy discount = subtotal -> 10;
QuoteService quotes = new QuoteService(prices, discount);

Quote q = quotes.quote(new OrderRequest("BOOK", 2));
System.out.println(q.totalCents()); // 190
~~~

Đây **không phải file Java độc lập**, cần đặt vào method/test phù hợp. Kết quả `190` = `100 × 2 − 10`. Test đó không phải tạo stock, payment, shipping, receipts hoặc audit. Vì vậy test nhanh và sát trách nhiệm được kiểm tra.

### 7.3. Wiring tổng thể vẫn cần được kiểm tra

Có thêm lớp không tự làm cấu hình Spring đúng. Trong ví dụ, `OrderWorkflow` phụ thuộc một `PurchaseService` và một `FulfillmentService`; Spring phải có bean phù hợp cho cả hai. Nếu thiếu `PaymentGateway`, startup thường thất bại khi tạo singleton `PurchaseService`/`OrderWorkflow`. Nếu có hai `PaymentGateway` candidates không thể chọn duy nhất, Spring cũng có thể báo lỗi ambiguity.

Kết hợp hai loại test:

- **Unit tests** cho Quote/Purchase/Fulfillment riêng; các fake phù hợp với mỗi trách nhiệm.
- **Spring wiring/smoke test** tạo context và kiểm tra `OrderWorkflow` nhận đúng beans, sau đó kiểm tra một luồng hợp lệ.

Không dùng Java `assert` không bật `-ea` để thay những điều kiện kiểm tra bắt buộc; dùng test assertions được thực thi hoặc `if (...) throw ...`.

## 8. Những cách “giảm tham số” nhưng không giải quyết vấn đề

### 8.1. Bọc tất cả dependency vào một túi

~~~java
// Anti-pattern minh họa:
record AllDependencies(
        PriceCatalog catalog, DiscountPolicy discount,
        StockGateway stock, PaymentGateway payment,
        ShippingGateway shipping, ReceiptSender receipts,
        AuditSink audit) {}

final class OrderService {
    OrderService(AllDependencies everything) {
        // Vẫn nắm và làm việc với đủ 7 collaborators.
    }
}
~~~

Số tham số bề mặt của constructor giảm từ 7 xuống 1 nhưng class vẫn có quyền truy cập đủ các trách nhiệm cũ. Nó không tăng cohesion, có thể chỉ **giấu coupling**. Một parameter object hợp lý phải **nhóm dữ liệu/hành vi cùng ý nghĩa domain**, không chỉ gom mọi service cho tiện.

### 8.2. Tiêm ApplicationContext rồi tự getBean

~~~java
// Anti-pattern minh họa:
final class OrderService {
    private final ApplicationContext context;

    OrderService(ApplicationContext context) {
        this.context = context;
    }

    void process() {
        PaymentGateway gateway = context.getBean(PaymentGateway.class);
        // Phụ thuộc thật bị giấu trong method.
    }
}
~~~

Đây là snippet chưa có imports và full implementation, **không phải code runnable**. Nó biến class thành **service locator consumer**: chữ ký constructor có một tham số, nhưng dependencies thật bị giấu đi, dễ lỗi muộn và khó unit test. Đôi lúc dùng context/provider là phù hợp ở infrastructure/composition root, nhưng không phải cách thông thường để làm gọn một business service.

### 8.3. Chuyển constructor arguments sang @Autowired fields

~~~java
// Anti-pattern minh họa cho collaborator bắt buộc:
@Autowired private PriceCatalog catalog;
@Autowired private PaymentGateway payment;
@Autowired private ShippingGateway shipping;
~~~

Các field declarations trên phải nằm trong một class hợp lệ và có imports; không phải file độc lập. Chuyển từ constructor sang field injection chỉ **che giấu** phụ thuộc khỏi API khởi tạo. Nếu class đang ôm nhiều trách nhiệm, việc chuyển sang field injection không tách được trách nhiệm nào.

### 8.4. Thêm facade chỉ chuyển tiếp mọi thứ

Một lớp `OrderFacade` có bảy public methods và chỉ gọi một lớp `OrderHelper` chứa toàn bộ thuật toán không cải thiện thực chất. Cần tách **quyền quyết định của từng phần** (tính giá, thanh toán, fulfillment) về đúng nơi. Một orchestration layer có giá trị khi nó thể hiện **luồng use case** và chủ yếu gọi các bước đã có trách nhiệm rõ.

### 8.5. Tách microservices quá sớm

SRP và constructor DI là **thiết kế lớp/module**, không bắt buộc tách một hệ thống thành bảy dịch vụ mạng độc lập. Việc chuyển payment/stock/shipping sang microservices có thêm lỗi mạng, eventual consistency và vận hành, không tự chữa low cohesion trong code ứng dụng.

## 9. Khi nào nên giữ một constructor tương đối dài?

Đừng tối ưu hình thức. Có thể giữ nhiều tham số khi:

- Các collaborator thực sự cùng phục vụ **một mục tiêu cohesive**, ví dụ một adapter tổng hợp dữ liệu từ nhiều backend.
- Class chỉ làm **orchestration rõ ràng**, không đồng thời sở hữu các chi tiết thay đổi độc lập; mỗi collaborator là một bước được thiết kế có chủ đích.
- Tách tiếp sẽ tạo quá nhiều pass-through wrappers mà không có hợp đồng mới hay khả năng test tốt hơn.
- Dependencies là những thành phần bắt buộc của một thuật toán, và một lớp khác chứa chúng không làm ranh giới rõ thêm.
- Nhóm dữ liệu được biểu diễn bằng value object domain hợp lệ và có invariant chung, chứ không đơn thuần là bag of services.

Câu hỏi quyết định không phải “bao nhiêu tham số” mà là: **Có thể mô tả trách nhiệm class trong một câu có nghĩa? Các method và dependencies có đi cùng nhau khi nghiệp vụ đổi không?**

Nếu constructor dài nhưng class cohesive, cải thiện trước bằng **đặt tên rõ ràng, phân nhóm ở mức module, composition root và tests**, thay vì chia tùy tiện.

## 10. Lỗi Spring DI liên quan đến refactor

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| `UnsatisfiedDependencyException` | Một service mới cần collaborator chưa đăng ký | Kiểm tra `@Bean`/scan và constructor parameters |
| `NoSuchBeanDefinitionException` | Thiếu bean cho một interface ở graph mới | Khai báo adapter hoặc cấu hình test fake rõ ràng |
| `NoUniqueBeanDefinitionException` | Hai beans cùng type không có selection rule | Dùng `@Qualifier`/`@Primary` theo vai trò, không đoán |
| `BeanCurrentlyInCreationException` | Tách lớp nhưng sinh vòng constructor dependency | Vẽ lại graph, đưa trách nhiệm chung ra lớp khác; tránh cycle |
| Context refresh thành công, output sai | Logic bị di chuyển sai hoặc thứ tự side effects thay đổi | Regression tests cho các đường nghiệp vụ và audit |
| Constructor ít tham số nhưng class vẫn phức tạp | Đã chuyển sang `ApplicationContext`, dependency bag hoặc field injection | Xem lại trách nhiệm và hidden dependencies |
| Hàng loạt tiny services chỉ chuyển tiếp | Tách chỉ để giảm param count | Gộp lại nơi hợp đồng thực sự cohesive |
| Test quote phải mock payment/shipping | Quote logic chưa được tách khỏi workflow | Tách nghiệp vụ báo giá có inputs/outputs rõ |
| Singleton ghi state dùng chung không an toàn | Fake/list hoặc service mutable được gọi đa luồng | Thiết kế thread-safe/stateless, giới hạn scope |
| Purchase đã giữ hàng nhưng thu tiền lỗi | Thiếu chiến lược rollback/compensation | Thiết kế nghiệp vụ thật, không nhầm với DI refactor |
| Constructor mặc định tạo object thiếu dependency | Né wiring errors bằng trạng thái không hợp lệ | Constructor bắt buộc và validation/fail fast |
| `javac` không thấy package Spring | Thiếu Maven/JAR classpath | Sửa build/classpath trước khi chẩn đoán bean graph |

**Quan trọng:** Tách class có thể **làm lộ** vòng phụ thuộc từng bị che bởi service locator hoặc mutable fields. Đây là cơ hội sửa thiết kế dependency graph, không phải lý do quay lại cách ẩn dependencies.

## 11. Kế hoạch refactor có kiểm chứng

Một cách an toàn khi làm trên hệ thống đang chạy:

1. **Chụp lại hành vi hiện tại:** tests cho giá, discount, stock failure, payment failure, fulfillment và audit.
2. **Xác định invariant:** không thu tiền cho số lượng không hợp lệ; không gửi receipt trước khi có thanh toán hợp lệ; ghi audit ở đúng giai đoạn.
3. **Tách báo giá đầu tiên:** `QuoteService` trả `Quote` immutable, không biết payment/shipping. Bảo đảm quote cũ/mới giống nhau.
4. **Tách bước mua:** `PurchaseService` nhận `QuoteService` cùng các gateways của purchase; tạo `Purchase` hợp lệ.
5. **Tách bước hậu mua:** `FulfillmentService` nhận dữ liệu Purchase thay vì tự gọi lại pricing.
6. **Giữ orchestration mỏng:** `OrderWorkflow` chỉ thể hiện order của các bước và xử lý use-case level.
7. **Cập nhật Spring configuration:** đăng ký các beans và dependencies bằng factory method parameters/constructors, không gọi trực tiếp `@Bean` methods nếu `proxyBeanMethods=false`.
8. **Chạy unit tests, integration tests và failure-path tests**; đánh giá side effects/exception behavior có giữ đúng không.
9. **Đo mức độ dễ thay đổi/test**, không chỉ đếm tham số. Nếu xuất hiện pass-through layer không có lý do, cân nhắc gộp.
10. **Tài liệu hóa ranh giới và ownership** của side effects, nhất là transaction, retry, idempotency và audit.

### Những regression cases đáng có

| Trường hợp | Kỳ vọng chính |
| --- | --- |
| BOOK × 2, subtotal 300 | Discount 20; total 280 |
| BOOK × 1, subtotal 150 | Không discount; total 150 |
| SKU không hỗ trợ | Quote fail; không gọi payment/fulfillment |
| Quantity bằng 0 hoặc âm | `OrderRequest` từ chối trước side effects |
| Quantity vượt tồn kho giả lập (trên 5) | Stock báo lỗi; không charge |
| Payment fake ném lỗi | Không gửi receipt/không fulfill; cần đánh giá reserve compensation trong hệ thống thật |
| Shipping lỗi sau payment | Không thể chỉ retry mù quáng; cần policy/idempotency/compensation |
| Unit test QuoteService | Không cần khởi tạo Payment/Stock/Shipping |
| Wiring của OrderWorkflow | Inject đúng hai services, context startup thành công |

Chú ý: bảng này là **kế hoạch kiểm thử**, không phải lời khẳng định ví dụ console đã chạy tất cả cases. Chỉ happy path có sáu dòng stdout ở mục 6.3.

## 12. Bài tập có đáp án

**Bài 1.** Constructor có bảy tham số. Có chắc class sai SRP không?

**Đáp án:** Không. Đây là tín hiệu cần xét method clusters, change reasons, cohesion, coupling và test friction. Một orchestrator rõ ràng có thể có nhiều collaborator mà vẫn có một trách nhiệm nhất quán.

**Bài 2.** Vì sao truyền một `AllDependencies` vào constructor chưa phải refactor đủ tốt?

**Đáp án:** Nó giảm số tham số hiển thị nhưng giữ nguyên khả năng truy cập bảy collaborators và nhiều trách nhiệm. Không làm cho các thay đổi price/payment/shipping ít va chạm hơn.

**Bài 3.** Nếu QuoteService cần mock PaymentGateway trong unit test, điều gì có thể chưa đúng?

**Đáp án:** Logic báo giá còn phụ thuộc không cần thiết vào payment hoặc test đang dùng quá nhiều infrastructure. Tách tính giá khỏi bước thu tiền để quote unit test chỉ cần price catalog/discount.

**Bài 4.** Khi nào nên giữ `OrderWorkflow` thay vì gọi tất cả từ `main`?

**Đáp án:** Khi có một use case thực sự cần bảo đảm thứ tự Purchase→Fulfillment và cho nhiều entry points (CLI, web, jobs) dùng chung contract điều phối. Nếu ứng dụng rất nhỏ và không có orchestration rule, lớp này có thể chưa cần thiết.

**Bài 5.** Tách class có tự giải quyết transaction/payment failure không?

**Đáp án:** Không. Cần thiết kế side-effect ordering, idempotency, compensation và exception handling riêng. Refactor phải giữ hoặc cải thiện đúng hợp đồng nghiệp vụ, không chỉ cấu trúc class.

**Bài 6.** Có nên thay bảy constructor parameters bằng `ApplicationContext` để hết cảnh báo linter?

**Đáp án:** Không nên ở business service. Service locator giấu phụ thuộc thật, lỗi xuất hiện muộn và unit test phức tạp. Ưu tiên constructor injection với trách nhiệm rõ.

**Bài 7.** Vì sao `QuoteService` chỉ có hai collaborator có ích cho kiểm thử?

**Đáp án:** Nó chỉ yêu cầu catalogue và discount policy, nên fake đơn giản là đủ; test pricing không phụ thuộc shipping/notification/payment và dễ xác định khi thất bại.

**Bài 8.** Nếu thêm một wrapper chuyển tiếp vô nghĩa chỉ để constructor mỗi lớp còn tối đa 3 tham số, thiết kế có chắc tốt hơn không?

**Đáp án:** Không. Đó có thể là indirection không giá trị. Tiêu chí là cohesion, ranh giới trách nhiệm, khả năng thay đổi/test và tính dễ hiểu của dependency graph.

## 13. Checklist rà soát

- [ ] Có thể nêu trách nhiệm của mỗi service bằng một câu ngắn gọn.
- [ ] Constructor thể hiện dependency thật, không giấu trong service locator hoặc injected fields.
- [ ] Không dùng một ngưỡng tham số tùy ý làm kết luận duy nhất.
- [ ] Có ma trận method–dependency và biết những cụm trách nhiệm nào thay đổi cùng nhau.
- [ ] `QuoteService` không biết payment/shipping; `FulfillmentService` không tính giá.
- [ ] `OrderWorkflow` chỉ điều phối những bước có ý nghĩa, không ôm bảy clients.
- [ ] Value objects/records có contract và invariant rõ; không chỉ là “bag” chứa services.
- [ ] Cấu hình Spring inject collaborators bằng constructors hoặc `@Bean` method parameters.
- [ ] Chạy Java 17+ bằng Maven/`javac`/`java` và so **sáu dòng stdout** chính xác.
- [ ] Kiểm tra missing/ambiguous beans, cycles và business failure paths.
- [ ] Unit test các service nhỏ không cần Spring; smoke test context cho wiring.
- [ ] Không thêm tầng pass-through không cải thiện khả năng thay đổi/test.
- [ ] Side effects có ownership rõ: giữ kho, thu tiền, shipping, receipt, audit.
- [ ] Không kết luận refactor hoàn thành chỉ từ việc ít constructor parameters hơn.
- [ ] Tránh thay đổi thứ tự/số lần thực hiện nghiệp vụ một cách tình cờ.

**Kết luận:** Constructor injection không gây ra “quá nhiều dependencies” mà **làm chúng trở nên dễ nhìn**. Nếu một class có nhiều lý do thay đổi, method clusters ít liên quan và unit tests phải dựng các collaborator không dùng đến, hãy tách theo **trách nhiệm nghiệp vụ cohesive**. Đừng chỉ giấu dependencies trong một bag, context hoặc wrapper; hãy cải thiện **dependency graph, tính rõ ràng của hợp đồng và khả năng kiểm thử**.

### Tài liệu chính thức và khái niệm liên quan

- [Spring Framework — Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)
- [Spring Framework — Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java.html)
- [Spring Framework — Using @Bean](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html)


</details>

- [Quay lại đầu trang](#back-to-top)
