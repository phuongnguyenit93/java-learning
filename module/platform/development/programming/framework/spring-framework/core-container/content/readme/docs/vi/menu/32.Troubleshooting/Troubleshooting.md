<a id="back-to-top"></a>

# Chẩn đoán lỗi

## Menu
- [Đọc chuỗi nguyên nhân trong BeanCreationException](#troubleshooting-001)
- [Thiếu định nghĩa với NoSuchBeanDefinitionException](#troubleshooting-002)
- [Nhiều ứng viên với NoUniqueBeanDefinitionException](#troubleshooting-003)
- [Phụ thuộc không thỏa mãn với UnsatisfiedDependencyException](#troubleshooting-004)
- [Xung đột tên và lỗi BeanDefinitionOverrideException](#troubleshooting-005)
- [Sai phạm vi quét, profile hoặc điều kiện đăng ký](#troubleshooting-006)
- [Sai kiểu trả về, kiểu proxy và chuyển đổi cấu hình](#troubleshooting-007)
- [Callback khởi tạo thất bại hoặc bean chưa được post-process đầy đủ](#troubleshooting-008)
- [Phân biệt lỗi lúc refresh với lỗi khi truy cập bean lazy](#troubleshooting-009)
- [Quan sát log container và rút gọn cấu hình để tái hiện](#troubleshooting-010)

## <a id="troubleshooting-001">Đọc chuỗi nguyên nhân trong BeanCreationException</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc chuỗi nguyên nhân trong BeanCreationException của Spring Framework

## 1. Vấn đề: thông báo bên ngoài thường chưa phải gốc lỗi

Khi Spring Core Container không thể tạo một bean, thông báo đầu tiên có thể chỉ cho biết bean đang được yêu cầu, trong khi lỗi thực xảy ra ở một dependency sâu hơn. Ví dụ:

~~~text
UnsatisfiedDependencyException: Error creating bean with name 'checkout'
  Unsatisfied dependency expressed through method 'checkout' parameter 0
Caused by: UnsatisfiedDependencyException:
  Error creating bean with name 'shippingPort'
  Unsatisfied dependency expressed through method 'shippingPort' parameter 0
Caused by: NoSuchBeanDefinitionException:
  No qualifying bean of type 'demo.ApiKey' available
~~~

Đây là **log minh họa**, không phải output cố định của Spring 6.2.9. Nếu chỉ đọc dòng đầu, ta dễ sửa sai `checkout`. Nếu chỉ đọc dòng cuối, ta thấy thiếu `ApiKey` nhưng chưa biết nó được yêu cầu từ nơi nào. Cách đọc đúng là **ngoài → trong → quay lại đường dependency**:

1. Operation nào thất bại (startup, refresh hay getBean)?
2. Bean đầu tiên nào Spring đang tạo?
3. Những bean trung gian nào dẫn tới lỗi?
4. Injection point nào cần dependency không đáp ứng được?
5. Root cause trong main cause chain là exception class nào?
6. Có related/suppressed causes nào giải thích các thử nghiệm khác không?
7. Cấu hình nào cần sửa để tất cả dependencies đúng hợp đồng?

Bài học thuộc nhóm **Chẩn đoán lỗi** của module **Spring Framework - core-container**, với chủ đề **Đọc chuỗi nguyên nhân trong BeanCreationException**. Nội dung hướng dẫn chẩn đoán thực tế, cách đọc metadata exception, phân loại các lỗi thường gặp, giới hạn của root-cause analysis và ví dụ chạy được bằng **Java 17 + Maven + Spring Framework 6.2.9**.

## 2. Các loại exception thường xuất hiện trong chuỗi

~~~text
java.lang.RuntimeException
  └── NestedRuntimeException
       └── BeansException
            ├── FatalBeanException
            │    └── BeanCreationException
            │         ├── UnsatisfiedDependencyException
            │         ├── BeanCurrentlyInCreationException
            │         └── ScopeNotActiveException
            └── NoSuchBeanDefinitionException
                 └── NoUniqueBeanDefinitionException

BeanInstantiationException hoặc IllegalArgumentException
  có thể xuất hiện làm cause bên trong BeanCreationException.
~~~

Đây là **sơ đồ rút gọn**, không đầy đủ toàn bộ Spring exception hierarchy. Một điểm quan trọng: `NoSuchBeanDefinitionException` là một `BeansException` nhưng **không phải subclass của BeanCreationException**; nó thường nằm **bên trong cause chain** của `UnsatisfiedDependencyException`.

### 2.1. BeanCreationException

`org.springframework.beans.factory.BeanCreationException` biểu diễn lỗi khi BeanFactory cố tạo bean từ definition. Nó có thể lưu bean name, nguồn definition, main cause và những related causes.

~~~java
String beanName = error.getBeanName();
String resource = error.getResourceDescription();
Throwable[] related = error.getRelatedCauses();
~~~

Đây là các snippets với biến `error` có type `BeanCreationException`. Cả bean name, resource description và related causes đều **có thể null**.

### 2.2. UnsatisfiedDependencyException

`UnsatisfiedDependencyException extends BeanCreationException`, cho biết một dependency không được thỏa mãn: thiếu bean, quá nhiều candidates, hoặc một bean cần thiết không tạo được.

~~~java
if (error instanceof UnsatisfiedDependencyException failure) {
    InjectionPoint injection = failure.getInjectionPoint();
}
~~~

Method `getInjectionPoint()` có thể trả `null` nếu Spring không có metadata vị trí field/parameter. Đừng tự giả định nó có mặt trong mọi instance hay mọi BeanCreationException.

### 2.3. NoSuchBeanDefinitionException

Spring không tìm được bean name/type đủ điều kiện. Javadoc cung cấp các methods:

~~~java
String name = problem.getBeanName();
Class<?> type = problem.getBeanType();
ResolvableType generic = problem.getResolvableType();
~~~

Ở đây `problem` là `NoSuchBeanDefinitionException`. Những trường này có thể null tùy lookup theo name hoặc type.

### 2.4. NoUniqueBeanDefinitionException

Spring tìm được **nhiều candidates**, nhưng injection point cần đúng một. `NoUniqueBeanDefinitionException extends NoSuchBeanDefinitionException`, do đó phép kiểm tra chung `contains(NoSuchBeanDefinitionException.class)` cũng có thể true khi **không hề thiếu bean**.

~~~java
if (error.contains(NoUniqueBeanDefinitionException.class)) {
    // Quá nhiều ứng viên
} else if (error.contains(NoSuchBeanDefinitionException.class)) {
    // Không tìm được ứng viên
}
~~~

Thứ tự hai nhánh là quan trọng. `NoUniqueBeanDefinitionException` cũng có `getNumberOfBeansFound()` và `getBeanNamesFound()`; tên danh sách có thể null nếu constructor không cung cấp.

### 2.5. BeanCurrentlyInCreationException

Container thấy bean đang được tạo bị yêu cầu thêm một lần theo cách không giải được. Trường hợp điển hình: constructor cycle `A(B)` và `B(A)`. Tuy nhiên một vài tình huống proxy/initialization cũng phức tạp, vì vậy phải xem full dependency chain thay vì luôn kết luận là một vòng hai bean.

### 2.6. BeanInstantiationException và exceptions từ code ứng dụng

Một `@Bean` factory method có thể ném `IllegalArgumentException`, `IllegalStateException`, exception từ driver/database, network hoặc một constructor. Spring có thể bọc qua `BeanInstantiationException` và `BeanCreationException`. **Root cause có thể là exception Java thường**, không phải Spring-specific type.

### 2.7. Exceptions không nằm trong BeanCreationException

Lỗi đọc XML hoặc đăng ký BeanDefinition có thể ném `BeanDefinitionStoreException` **trước bean creation**. Một Spring TestContext failure có thể có outer `IllegalStateException: Failed to load ApplicationContext` bọc BeanCreationException. Không giả định mọi startup failure đều có BeanCreationException làm outermost throwable.

## 3. Chuỗi Throwable.getCause(): outer, middle, root

~~~text
T0: UnsatisfiedDependencyException("checkout")
  │ getCause()
  ▼
T1: UnsatisfiedDependencyException("shippingPort")
  │ getCause()
  ▼
T2: NoSuchBeanDefinitionException(ApiKey.class)
  │ getCause()
  ▼
null
~~~

- **Outer (T0)**: operation đang cố tạo checkout bị thất bại.
- **Middle (T1)**: shippingPort là dependency trung gian không thể tạo.
- **Innermost/root (T2)**: không có BeanDefinition/candidate phù hợp cho ApiKey.

Một Throwable chỉ có một `getCause()` trực tiếp. Nó có thể có **các nhánh khác** như suppressed exceptions hoặc Spring related causes, nhưng không được tự nối chúng vào main chain rồi gọi nhánh cuối là root.

### Java stack trace biểu diễn điều này thế nào?

Thông thường logger in exception ngoài cùng trước, tiếp theo các đoạn `Caused by:` ở dưới. Dòng `... N common frames omitted` có nghĩa một số stack frames trùng được rút gọn, **không có nghĩa root cause bị bỏ đi**.

### Root cause không nhất thiết là cách sửa

Nếu root là `ConnectException: Connection refused`, root kỹ thuật ở socket, nhưng lỗi cấu hình có thể là database host sai. Nếu root là `NoSuchBeanDefinitionException`, sự thật sâu hơn có thể là profile không active hoặc qualifier mismatch. Vì vậy cần đọc root **và** metadata các tầng bên ngoài.

## 4. Spring APIs: getRootCause, getMostSpecificCause và contains

`BeanCreationException` kế thừa `NestedRuntimeException`. Những methods sau hữu ích:

| API | Ý nghĩa | Có thể null? |
| --- | --- | --- |
| `Throwable.getCause()` | Nguyên nhân trực tiếp | Có |
| `NestedRuntimeException.getRootCause()` | Cause trong cùng nếu có | **Có** |
| `NestedRuntimeException.getMostSpecificCause()` | Root nếu có, không thì bản thân exception | **Không** |
| `NestedRuntimeException.contains(Class<?>)` | Kiểm tra type trên main cause chain | Trả boolean |
| `BeanCreationException.getBeanName()` | Bean name liên quan đến wrapper này | Có |
| `BeanCreationException.getResourceDescription()` | Resource/source của definition nếu có | Có |
| `BeanCreationException.getRelatedCauses()` | Lỗi liên quan ngoài direct cause chain | Có |
| `UnsatisfiedDependencyException.getInjectionPoint()` | Field/parameter gây dependency failure | Có |
| `Throwable.getSuppressed()` | Java suppressed exceptions | Array có thể rỗng |

Ví dụ:

~~~java
BeanCreationException ex = ...;

Throwable root = ex.getRootCause();
Throwable specific = ex.getMostSpecificCause();

String rootName = root == null
        ? "(no nested cause)"
        : root.getClass().getSimpleName();

boolean hasMissing =
        ex.contains(NoSuchBeanDefinitionException.class);
~~~

Đây là **API snippet**, không phải file Java chạy độc lập. `getMostSpecificCause()` hữu ích cho automated diagnostics không muốn nhận null, nhưng **đừng chỉ log nó**: bạn sẽ mất bean names và injection points trong các wrappers phía ngoài.

### contains(Class<?>) không duyệt mọi nhánh

`contains(...)` kiểm tra exception hiện tại và nested cause chain. Nếu `relatedCauses[]` hoặc `getSuppressed()` chứa lỗi quan trọng khác, bạn cần đọc riêng chúng. Không tự khẳng định `contains` đã kiểm tra toàn bộ exception graph.

### Tránh kiểm tra theo string

Mã kiểu:

~~~java
boolean missing = ex.getMessage().contains("No qualifying bean");
~~~

dễ vỡ khi messages thay đổi theo Spring version, generic type, annotation metadata hoặc locale. Ưu tiên `ex.contains(NoSuchBeanDefinitionException.class)` và những structured fields đã có.

## 5. Metadata bean name và resource description

Một outer exception có thể nhắc `apiController`, nested exception nhắc `orderService` và root exception nhắc `dataSource`. Bạn nên ghi lại tên bean ở **từng BeanCreationException wrapper**, không chỉ `outer.getBeanName()`.

~~~java
for (Throwable t = error; t != null; t = t.getCause()) {
    if (t instanceof BeanCreationException b) {
        System.out.println("bean=" + b.getBeanName());
        System.out.println("resource=" + b.getResourceDescription());
    }
}
~~~

Đây là snippet để hiểu API; với custom malformed Throwable graph, nên dùng visited identity set như chương trình đầy đủ bên dưới.

### Bean name không phải Java class name

Một bean có thể được đăng ký với `@Bean("paymentPort")` dù factory method nằm trong `BillingConfiguration` và concrete target type là `StripeClient`. Exception bean name **thường là registry identifier**, không nhất thiết bằng simple class name.

### Resource description

Resource có thể là classpath XML, imported resource, annotated configuration description hoặc null. Trong Java `@Configuration`, không kỳ vọng luôn có filesystem path hữu ích. `getResourceDescription() == null` không chứng minh BeanDefinition bị thiếu.

### InjectionPoint có thể cho biết tham số số mấy

Một log dạng:

~~~text
Unsatisfied dependency expressed through method 'checkout'
parameter 0
~~~

cho thấy BeanFactory resolve tham số đầu tiên của method liên quan. Nếu nhiều constructor overloads, hãy xác định đúng declaring class/method và generic/qualifier annotations.

## 6. Mở rộng: related causes và suppressed exceptions

### 6.1. getRelatedCauses

`BeanCreationException.addRelatedCause(Throwable)` có thể lưu các errors phát sinh trước đó khi Spring thử resolution một bean, nhưng **không là direct cause chính**.

~~~java
Throwable[] related = ex.getRelatedCauses();

if (related != null) {
    for (Throwable item : related) {
        System.out.println(item.getClass().getSimpleName());
    }
}
~~~

Không assume `related` luôn non-null hoặc chỉ chứa đúng một lỗi. Nếu có nhiều constructor candidates từng thất bại, các related causes có thể cung cấp thêm context hữu ích.

### 6.2. Throwable.getSuppressed

Java suppressed exceptions có thể xuất hiện khi `try-with-resources` cleanup thất bại trong lúc một exception chính đang propagate.

~~~java
for (Throwable suppressed : ex.getSuppressed()) {
    System.out.println(suppressed.getClass().getSimpleName());
}
~~~

`getSuppressed()` và `getRelatedCauses()` là **hai cơ chế khác nhau**. Suppressed errors không tự thay thế root cause của main chain.

### 6.3. Mô hình đúng

~~~text
BeanCreationException
  ├── cause -------------> next cause -> innermost cause
  ├── relatedCauses[] ---> lỗi liên quan ở nhánh khác
  └── suppressed[] ------> lỗi cleanup hay lỗi phụ
~~~

Khi xây logging/telemetry, giữ nhãn edge `cause`/`related`/`suppressed`; giới hạn số lượng và depth nếu exception graph lớn. Đừng nối các branches thành một chuỗi làm sai quan hệ nhân quả.

## 7. Chẩn đoán thiếu dependency

~~~text
checkout -> shippingPort -> ApiKey
                            không có bean hợp lệ
~~~

Khi root là `NoSuchBeanDefinitionException(ApiKey)`, hãy kiểm tra:

- `@Component`/@Bean definition có được đăng ký không?
- Package scanner có bao phủ class này không?
- `@Profile` active đúng trước refresh không?
- `@Conditional` và property sources có loại bean không?
- Bean có đúng interface/generic type không?
- `@Qualifier` có yêu cầu một tên/nhãn không tồn tại không?
- `autowireCandidate=false` hoặc `defaultCandidate=false` có tác động không?
- Lookup có đang diễn ra từ parent hay child ApplicationContext không?
- `FactoryBean.getObjectType()` có cung cấp product type đúng không?

Một `NoSuchBeanDefinitionException` không nhất thiết do “quên viết @Component”. Nhiều lần bean tồn tại nhưng **không đủ điều kiện** cho injection point.

### Kiểm tra theo hướng ít side effects

~~~java
boolean named = context.containsBean("apiKey");
String[] names = context.getBeanNamesForType(ApiKey.class);
~~~

Đây là snippet với context và type của bạn. Đừng vội gọi `getBeansOfType(...)` trên production context chỉ để tìm names: method đó có thể instantiate lazy beans hoặc trigger errors ở các collaborators khác.

### Fix phải sửa đúng graph

Nếu ApiKey là required dependency, đăng ký giá trị đúng từ configuration an toàn; nếu optional theo business semantics, dùng `ObjectProvider<ApiKey>` hoặc `Optional<ApiKey>` đúng chỗ. Không biến dependency bắt buộc thành optional chỉ để che startup failure.

## 8. Chẩn đoán nhiều ứng viên

~~~text
checkout(ShipmentPort port)
  ├── bluePort : ShipmentPort
  └── redPort  : ShipmentPort
~~~

Khi có nhiều eligible candidates mà Spring phải chọn một, root có thể là `NoUniqueBeanDefinitionException`.

Các quy tắc cần rà soát:

- `@Qualifier` thu hẹp candidate set theo annotation/name.
- `@Primary` chọn một bean ưu tiên nếu phù hợp.
- `@Fallback` trong Spring 6.2 hạ ưu tiên một số candidates cho single injection.
- `@Bean(autowireCandidate=false)` làm bean không eligible dù type query vẫn thấy.
- `@Bean(defaultCandidate=false)` liên quan plain type injection vs qualified injection.
- Generic metadata qua `ResolvableType` lọc `Repository<Order>` khác `Repository<Customer>`.
- Java constructor/method parameter name fallback cần `-parameters` trong Spring 6.1+.

**NoUnique extends NoSuch**: khi phân tích hãy test NoUnique trước, nếu không bạn có thể báo nhầm “không có bean” khi thực tế là “hai hoặc nhiều bean”.

Không sửa ambiguity bằng cách chọn `names[0]` hay dựa vào registration order. Hãy chọn semantic qualifier hoặc điều kiện presence đúng nghiệp vụ.

## 9. Chẩn đoán circular dependencies

Vòng constructor:

~~~text
alpha(Beta beta)
      |
      v
beta(Alpha alpha)
      |
      v
alpha vẫn đang được tạo
~~~

Khi BeanFactory cần Alpha trước khi Alpha đã instantiate, `BeanCurrentlyInCreationException` có thể nằm trong nested chain. `allowCircularReferences=true` trong Spring Core cho phép thử giải một số **singleton setter/field** cycles qua early references, không giải vòng constructor/factory method arguments thuần.

Spring Boot từ 2.6 mặc định `spring.main.allow-circular-references=false`, khác với BeanFactory default trong Spring Framework thuần. Tuy nhiên ngay cả bật flag, constructor cycle vẫn không tạo ra object A chưa tồn tại.

### Đừng gán mọi lỗi CurrentlyInCreation cho vòng A↔B

Một số proxy/BeanPostProcessor paths hoặc asynchronous initialization dependencies cũng có thể dẫn tới trạng thái “currently in creation”. Hãy đọc full bean path, injection phase, scope và các wrappers; đừng đoán chỉ từ một exception class.

### Thiết kế sửa bền vững

Tách responsibility chung thành bean thứ ba, đưa orchestration lên lớp cao hơn, hoặc dùng late lookup khi dependency bản chất chỉ cần ở runtime. Không đổi hết constructor injection sang field injection chỉ để context startup “xanh”; half-initialized early objects có thể gây bugs khó dò.

## 10. Lỗi factory method và constructor: đọc lỗi từ application code

~~~java
@Bean("configClient")
public ConfigClient configClient() {
    throw new IllegalArgumentException("invalid-endpoint");
}
~~~

Đây là snippet, type `ConfigClient` do ứng dụng định nghĩa. Spring có thể bọc exception trong `BeanInstantiationException` và `BeanCreationException`. Inner-most `IllegalArgumentException` nói factory method reject endpoint. Cách sửa đúng có thể nằm ở external configuration, default property hoặc validation, không phải bean scanner.

### Ném lại phải giữ cause

Anti-pattern:

~~~java
catch (RuntimeException ex) {
    throw new IllegalStateException("client setup failed");
}
~~~

Nó làm mất stack trace gốc. Cách tốt hơn:

~~~java
catch (RuntimeException ex) {
    throw new IllegalStateException("client setup failed", ex);
}
~~~

Đây là snippets phải đặt trong try/catch context hợp lệ. Không nuốt exception và trả dummy production client nếu endpoint/config thực sự là bắt buộc.

### Root cause cũng có thể là lỗi hạ tầng

`ConnectException`/`SQLException`/`ClassNotFoundException`/`ExceptionInInitializerError` có thể xuất hiện trong cause chain. Cần phân biệt:
- Lỗi cấu hình endpoint/credentials.
- Service/database chưa sẵn sàng.
- Runtime classpath/version mismatch.
- Constructor/static initialization bug.
- Bean lifecycle order hoặc wrong scope.

Root kỹ thuật không tự chỉ ra một fix duy nhất; nó chỉ thu hẹp nơi cần điều tra.

## 11. Ví dụ Java 17 + Spring Framework 6.2.9: bốn tình huống lỗi

Demo dùng **bốn ApplicationContexts độc lập**: missing dependency, ambiguous candidates, circular factory methods và factory method tự ném exception. Tất cả bean methods gây lỗi được gắn `@Lazy`, rồi chương trình gọi `getBean(...)` chủ động sau `refresh()`. Điều này giúp bắt lỗi đúng case; `@Lazy` **không sửa nguyên nhân**, chỉ trì hoãn creation.

Chương trình tránh phụ thuộc vào chuỗi error message hoặc chính xác số lượng wrappers, thay vào đó in **root exception type** và những facts có thể kiểm chứng. Với flag `--trace`, nó còn in cause chain/bean names.

### 11.1. Cấu trúc

~~~text
bean-error-chain-demo/
├── pom.xml
└── src/main/java/demo/
    └── BeanErrorChainDemo.java
~~~

### 11.2. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>bean-error-chain-demo</artifactId>
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

### 11.3. src/main/java/demo/BeanErrorChainDemo.java

~~~java
package demo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

public class BeanErrorChainDemo {

    public record ApiKey(String value) {}

    public interface ShipmentPort {
        String ship(String id);
    }

    public record Checkout(ShipmentPort port) {}

    public record Alpha(Beta beta) {}

    public record Beta(Alpha alpha) {}

    public record ConfigClient(String endpoint) {}

    private static List<Throwable> chain(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(
                new IdentityHashMap<Throwable, Boolean>());

        List<Throwable> result = new ArrayList<>();
        Throwable current = error;

        while (current != null && seen.add(current)) {
            result.add(current);
            current = current.getCause();
        }
        return result;
    }

    private static boolean containsBean(
            Throwable error, String wantedName) {

        for (Throwable cause : chain(error)) {
            if (cause instanceof BeanCreationException beanFailure
                    && wantedName.equals(beanFailure.getBeanName())) {
                return true;
            }
        }
        return false;
    }

    private static BeanCreationException failure(
            Class<?> config, String name) {

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            context.register(config);
            context.refresh();

            try {
                context.getBean(name);
            } catch (BeanCreationException expected) {
                return expected;
            }

            throw new AssertionError(
                    "Expected bean creation failure: " + name);
        }
    }

    private static void printTrace(
            String label, BeanCreationException error) {

        System.out.println("=== " + label + " ===");

        List<Throwable> causes = chain(error);

        for (int i = 0; i < causes.size(); i++) {
            Throwable cause = causes.get(i);
            String bean = cause instanceof BeanCreationException bc
                    ? bc.getBeanName() : null;

            System.out.println(i + " "
                    + cause.getClass().getSimpleName()
                    + (bean == null ? "" : " bean=" + bean));
        }

        Throwable[] related = error.getRelatedCauses();
        if (related != null) {
            for (Throwable item : related) {
                System.out.println("related "
                        + item.getClass().getSimpleName());
            }
        }

        for (Throwable item : error.getSuppressed()) {
            System.out.println("suppressed "
                    + item.getClass().getSimpleName());
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class MissingConfig {

        @Bean("shippingPort")
        @Lazy
        public ShipmentPort shippingPort(ApiKey key) {
            return id -> "shipped:" + id + ":" + key.value();
        }

        @Bean("checkout")
        @Lazy
        public Checkout checkout(ShipmentPort port) {
            return new Checkout(port);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AmbiguousConfig {

        @Bean("bluePort")
        @Lazy
        public ShipmentPort bluePort() {
            return id -> "blue:" + id;
        }

        @Bean("redPort")
        @Lazy
        public ShipmentPort redPort() {
            return id -> "red:" + id;
        }

        @Bean("checkout")
        @Lazy
        public Checkout checkout(ShipmentPort port) {
            return new Checkout(port);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class CycleConfig {

        @Bean("alpha")
        @Lazy
        public Alpha alpha(Beta beta) {
            return new Alpha(beta);
        }

        @Bean("beta")
        @Lazy
        public Beta beta(Alpha alpha) {
            return new Beta(alpha);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class FactoryConfig {

        @Bean("configClient")
        @Lazy
        public ConfigClient configClient() {
            throw new IllegalArgumentException("invalid-endpoint");
        }
    }

    public static void main(String[] args) {
        boolean trace = args.length > 0
                && "--trace".equals(args[0]);

        BeanCreationException missing =
                failure(MissingConfig.class, "checkout");

        System.out.println("missing.root="
                + missing.getMostSpecificCause()
                        .getClass().getSimpleName());

        System.out.println("missing.hasCheckout="
                + containsBean(missing, "checkout"));

        System.out.println("missing.hasShippingPort="
                + containsBean(missing, "shippingPort"));

        System.out.println("missing.hasNoSuch="
                + missing.contains(NoSuchBeanDefinitionException.class));

        if (trace) {
            printTrace("missing", missing);
        }

        BeanCreationException ambiguous =
                failure(AmbiguousConfig.class, "checkout");

        System.out.println("ambiguous.root="
                + ambiguous.getMostSpecificCause()
                        .getClass().getSimpleName());

        System.out.println("ambiguous.hasNoUnique="
                + ambiguous.contains(NoUniqueBeanDefinitionException.class));

        if (trace) {
            printTrace("ambiguous", ambiguous);
        }

        BeanCreationException cycle =
                failure(CycleConfig.class, "alpha");

        System.out.println("cycle.hasCurrentlyInCreation="
                + cycle.contains(BeanCurrentlyInCreationException.class));

        if (trace) {
            printTrace("cycle", cycle);
        }

        BeanCreationException factory =
                failure(FactoryConfig.class, "configClient");

        System.out.println("factory.root="
                + factory.getMostSpecificCause()
                        .getClass().getSimpleName());

        System.out.println("factory.message="
                + factory.getMostSpecificCause().getMessage());

        if (trace) {
            printTrace("factory", factory);
        }
    }
}
~~~

### 11.4. Lệnh chạy

Tại thư mục `bean-error-chain-demo` với JDK 17+:

~~~bash
java -version
mvn -version
mvn -q compile exec:java -Dexec.mainClass=demo.BeanErrorChainDemo
~~~

**Stdout do chương trình in** (không tính Maven hoặc Spring diagnostic logs):

~~~text
missing.root=NoSuchBeanDefinitionException
missing.hasCheckout=true
missing.hasShippingPort=true
missing.hasNoSuch=true
ambiguous.root=NoUniqueBeanDefinitionException
ambiguous.hasNoUnique=true
cycle.hasCurrentlyInCreation=true
factory.root=IllegalArgumentException
factory.message=invalid-endpoint
~~~

### 11.5. Ý nghĩa từng kết quả

| Output | Điều chứng minh |
| --- | --- |
| `missing.root` | NoSuchBeanDefinitionException nằm sâu nhất, ApiKey vắng mặt |
| `missing.hasCheckout` | Outer bean checkout xuất hiện trong error path |
| `missing.hasShippingPort` | Lỗi lan qua shippingPort trước khi tới ApiKey |
| `missing.hasNoSuch` | `contains(...)` tìm thấy NoSuch trong main chain |
| `ambiguous.root` | Có nhiều ShipmentPort candidates, không phải thiếu bean |
| `ambiguous.hasNoUnique` | Dùng subclass NoUnique để phân loại ambiguity |
| `cycle.hasCurrentlyInCreation` | alpha/beta factory argument cycle không được giải |
| `factory.root` | @Bean factory method ném exception từ application code |
| `factory.message` | Giá trị message tự xác định của root exception |

### 11.6. In chain chi tiết

Để quan sát những wrappers thật và bean names:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.BeanErrorChainDemo -Dexec.args=--trace
~~~

Trong trace mode, `printTrace` duyệt mỗi `Throwable.getCause()` và in class cùng `getBeanName()` nếu có. Trace mode **không có stdout được cố định theo số tầng**, vì Spring có thể thêm những wrappers theo implementation/creation path.

Ví dụ pattern cho missing case:

~~~text
=== missing ===
0 UnsatisfiedDependencyException bean=checkout
1 UnsatisfiedDependencyException bean=shippingPort
2 NoSuchBeanDefinitionException
~~~

Đây chỉ là **minh họa**. Khi chạy thực, luôn dùng chính cause chain hiện có. Tránh viết regression test khăng khăng phải có đúng ba tầng nếu contract bạn cần chỉ là root NoSuch và hai bean names.

### 11.7. Vì sao contexts được cô lập?

Nếu một context đăng ký cả bốn configurations, lỗi eager singleton đầu tiên có thể khiến refresh fail trước khi những tình huống khác được kiểm tra. Test cô lập giúp xác định đúng graph gây lỗi và đảm bảo resource lifecycle/cleanup độc lập.

### 11.8. Vì sao dùng @Lazy?

`@Lazy` cho phép refresh trước, rồi test chủ động `getBean(...)` để bắt exception. Không được hiểu rằng `@Lazy` làm dependency trở thành optional hoặc giải constructor cycle. Một lazy bean vẫn thất bại khi thực sự được tạo.

### 11.9. Lưu ý độ ổn định

Spring 6.2 có thể thay đổi exception wrapping path theo minor patch, BeanPostProcessors hoặc dependency metadata. Demo so sánh root type/causal presence thay vì full message; riêng `invalid-endpoint` do mình tạo nên test message có chủ đích.

## 12. Đọc injection point bằng code

~~~java
for (Throwable cause : chain(error)) {
    if (cause instanceof UnsatisfiedDependencyException u) {
        InjectionPoint point = u.getInjectionPoint();

        if (point != null) {
            System.out.println("injection=" + point);
        }
    }
}
~~~

Đây là snippet cần import `UnsatisfiedDependencyException` và `InjectionPoint`, cộng với helper `chain` ở demo. Nếu `point` mô tả factory method parameter 0, cần tra signature của method đó. Nếu field injection, xem type/qualifier/annotation trên field.

### Nên đọc những metadata nào?

- Declaring bean name và method/constructor/field.
- Thứ tự tham số (0-based index).
- Java type và generic signature.
- Qualifiers và `@Primary`/`@Fallback` conflicts.
- Parameter name metadata `-parameters` khi name fallback tham gia.
- Source của bean definition và active profiles.

Nếu `getInjectionPoint() == null`, đừng cố ép lấy field hoặc method; dựa trên stack frames và BeanDefinition registry information bổ sung.

## 13. Những loại root cause khác cần nhận biết

| Root/cause | Khả năng chính | Hướng xử lý |
| --- | --- | --- |
| `NoSuchBeanDefinitionException` | Thiếu eligible bean | Scan, @Configuration, @Profile, qualifier |
| `NoUniqueBeanDefinitionException` | Nhiều eligible beans | @Qualifier, @Primary, @Fallback, conditions |
| `BeanCurrentlyInCreationException` | Circular/early creation constraint | Dò dependency graph, constructor vs setter |
| `BeanNotOfRequiredTypeException` | Bean name đúng, type/proxy không hợp | Interface proxy vs target class, declared return type |
| `BeanInstantiationException` | Constructor/factory code lỗi | Kiểm tra inner exception/line trong method |
| `IllegalArgumentException` | Config value validation lỗi | Config source, required/default, validation |
| `IllegalStateException` | Trạng thái không hợp lệ | Initialization order/invariant |
| `ConnectException` | Service từ chối kết nối | Endpoint, port, readiness/network |
| `SQLException` | DB driver/URL/query/credentials | DataSource configuration và database |
| `ClassNotFoundException` | Runtime dependency thiếu | Package/classpath, transitive dependencies |
| `NoClassDefFoundError` | Class không load/link được hoặc static init fail | Build, versions, module path |
| `ScopeNotActiveException` | Web/custom scope chưa active | Web-aware context, scoped proxy |
| `ExceptionInInitializerError` | Static initialization failure | Static initializer và nested cause |

Với `FactoryBean`, lỗi tạo factory product có thể chỉ xuất hiện khi bean product được yêu cầu. Với AOP proxied beans, raw target exception và proxy/infrastructure creation exception có thể được bọc trong nhiều tầng. Cần tìm **causal path thực tế**, không suy theo class name một cách cứng nhắc.

## 14. Quy trình điều tra thực tế

### Bước 1 — Xác định thời điểm

Lỗi xuất hiện trong `ApplicationContext.refresh()`, lần đầu `getBean(...)`, request, scheduled job hay Spring TestContext startup? Một context refresh thành công **không** chứng minh mọi lazy/prototype/scoped bean sẽ thành công khi yêu cầu sau đó.

### Bước 2 — Lấy exception đầy đủ

Log Throwable object, không chỉ `getMessage()`:

~~~java
logger.error("Application context failed", error);
~~~

Đây là snippet với logger thích hợp. Khi logging production, hạn chế lộ credentials, tokens, sensitive query params/URLs. Stack traces có thể chứa dữ liệu người dùng, nên cần log access control và redaction.

### Bước 3 — Đi ngoài vào trong

Ghi danh sách `BeanCreationException.getBeanName()` theo main cause order để xây dependency path. Đừng bỏ qua intermediate bean vì root cause không biết ai yêu cầu nó.

### Bước 4 — Xem injection point

Nếu `UnsatisfiedDependencyException`, đọc `getInjectionPoint()`. Phải xác định type và annotation metadata của **đúng parameter/field**, không chỉ cả class.

### Bước 5 — Phân loại

Kiểm tra NoUnique trước NoSuch; xem cycle/instantiation/application exceptions. Đọc `getMostSpecificCause()` nhưng đừng đánh mất outer context. Xem `getRelatedCauses()` và `getSuppressed()` khi có.

### Bước 6 — Xác minh registry/conditions

`containsBeanDefinition` khác `containsBean`. `getBeanNamesForType` chỉ liệt kê local trong `ListableBeanFactory`; nếu có parent, dùng `BeanFactoryUtils...IncludingAncestors` theo same-name shadowing. Profiles/conditions, qualifiers và type metadata có thể giải thích “có BeanDefinition mà autowiring không thấy”.

### Bước 7 — Tạo minimum reproduction

Dựng một `AnnotationConfigApplicationContext` nhỏ với các beans liên quan, set profiles trước refresh, gọi đúng lazy bean nếu lỗi chỉ xuất hiện muộn. Một context quá rộng có thể nổ vì lỗi không liên quan.

### Bước 8 — Sửa và test thành công

Nếu thiếu ApiKey, đăng ký ApiKey có giá trị hợp lệ. Nếu NoUnique, chọn đúng candidate bằng qualifier/primary/condition. Nếu cycle, refactor dependency graph. Nếu factory method throw, sửa validation/config source. Test sau sửa phải **khởi tạo được bean và chứng minh behavior đúng**, không chỉ không còn dòng lỗi.

## 15. Regression tests với JUnit

Ví dụ trong mục 11 chạy bằng main nên không cần JUnit. Ở dự án dùng JUnit Jupiter, có thể test exception chain như sau:

~~~java
BeanCreationException ex = assertThrows(
        BeanCreationException.class,
        () -> context.getBean("checkout"));

assertTrue(ex.contains(
        NoSuchBeanDefinitionException.class));
~~~

Đây là test snippet, cần JUnit `Assertions` imports, context đăng ký `MissingConfig` của demo và test dependencies/configuration. Không copy snippet này vào `BeanErrorChainDemo.java`.

### 15.1. Missing bean test

Assert `ex.contains(NoSuchBeanDefinitionException.class)`, và nếu cần assert bean path thì duyệt `getBeanName()` qua main chain. Đừng assert toàn bộ outer message, vì resource paths và method parameter formatting có thể thay đổi.

### 15.2. Ambiguous injection test

~~~java
BeanCreationException ex = assertThrows(
        BeanCreationException.class,
        () -> context.getBean("checkout"));

assertTrue(ex.contains(
        NoUniqueBeanDefinitionException.class));
~~~

Áp dụng cho context chỉ có `AmbiguousConfig`. Tránh test NoSuch chung chung vì NoUnique kế thừa NoSuch.

### 15.3. Constructor cycle test

~~~java
BeanCreationException ex = assertThrows(
        BeanCreationException.class,
        () -> context.getBean("alpha"));

assertTrue(ex.contains(
        BeanCurrentlyInCreationException.class));
~~~

Áp dụng cho `CycleConfig`. Đừng xem bật allowCircularReferences là một fix cho constructor-only graph.

### 15.4. Factory method test

~~~java
BeanCreationException ex = assertThrows(
        BeanCreationException.class,
        () -> context.getBean("configClient"));

assertInstanceOf(
        IllegalArgumentException.class,
        ex.getMostSpecificCause());
~~~

Áp dụng cho `FactoryConfig`. Nếu validation contract thay đổi, cập nhật test theo expected failure semantics.

### 15.5. Test sự phục hồi sau sửa

Tạo **context mới** có ApiKey đã đăng ký, gọi bean Checkout và verify `port.ship("ORD-7")` trả expected result. Đối với ambiguity, assert đúng bean identity chứ không chỉ startup pass. Không giữ lại context đã thất bại trong khi tạo singleton để tiếp tục test bằng cách thêm definitions bừa bãi.

### 15.6. Spring TestContext và Boot diagnostics

Với `@SpringJUnitConfig` hoặc Spring Boot test, lỗi load context có thể được bọc bên ngoài bởi test framework. Vẫn duyệt `getCause()` và kiểm tra từng BeanCreationException bên trong. Spring Boot FailureAnalyzers/condition evaluation report có thể bổ sung gợi ý nhưng không thay thế causal reasoning của Spring Core.

## 16. Những anti-pattern làm mất nguyên nhân

- **Chỉ in `ex.getMessage()`**: mất stack frames và cause chain.
- **Ném exception mới không giữ cause**: mất root stack trace.
- **Bắt `Exception` rồi trả dummy bean**: biến startup failure thành lỗi production âm thầm.
- **So khớp toàn bộ Spring error message trong test**: quá phụ thuộc version/packaging.
- **Tự chọn bean đầu tiên để chữa NoUnique**: kết quả phụ thuộc registration order.
- **Thêm optional dependency để che NoSuch**: làm hỏng invariant nghiệp vụ.
- **Bật allowCircularReferences cho mọi lỗi**: không liên quan missing/ambiguity/factory failures.
- **Không kiểm tra @Profile/@Conditional**: root NoSuch có thể do conditional registration.
- **Dùng getBeansOfType hàng loạt khi chỉ muốn names**: có thể khởi tạo thêm beans đang lỗi.
- **Nhầm related causes với direct cause chain**: báo sai nguyên nhân ưu tiên.
- **Log secrets trong exception message**: rò dữ liệu nhạy cảm.
- **Đánh đồng startup success với lazy bean health**: lỗi có thể chỉ nổ tại first access.

## 17. Checklist chẩn đoán

- [ ] Xác định context, bean name và operation gây lỗi.
- [ ] Giữ nguyên Throwable và toàn bộ stack trace trong vùng log phù hợp.
- [ ] Duyệt getCause từ ngoài vào trong.
- [ ] Lấy bean names từ từng BeanCreationException.
- [ ] Xem resource description nhưng xử lý null.
- [ ] Với UnsatisfiedDependencyException, đọc InjectionPoint khi có.
- [ ] Phân biệt NoUnique trước NoSuch.
- [ ] Dùng getRootCause() có null-check.
- [ ] Dùng getMostSpecificCause() nếu muốn root-or-self non-null.
- [ ] Kiểm tra contains() chỉ là main cause chain.
- [ ] Đọc related causes và suppressed nếu có.
- [ ] Với missing, kiểm tra bean definitions, scans, profiles, qualifiers và scope.
- [ ] Với NoUnique, kiểm tra primary/fallback/qualified candidates.
- [ ] Với cycle, kiểm tra constructor/factory vs setter/field.
- [ ] Với factory failure, mở đúng code nơi exception được ném.
- [ ] Không lạm dụng các global bean factory flags.
- [ ] Không bỏ qua runtime lazy/prototype bean creation paths.
- [ ] Test failures bằng isolated contexts, không trộn nhiều lỗi.
- [ ] Sau sửa phải có positive integration test.
- [ ] Tránh cố định exact Spring exception message.
- [ ] Không log sensitive configuration values công khai.

## 18. Câu hỏi tự kiểm tra

**1. Dòng "Error creating bean checkout" có nghĩa code checkout sai?**  
Không chắc. Có thể checkout chỉ là consumer của một dependency sâu hơn đang lỗi.

**2. Vì sao cần đọc từ ngoài vào trong rồi đối chiếu ngược?**  
Outer cung cấp đường đi và injection point; root cung cấp failure type cụ thể.

**3. `getRootCause()` có thể null không?**  
Có nếu không có nested cause.

**4. `getMostSpecificCause()` khác `getRootCause()` ở đâu?**  
Nó trả root nếu có; nếu không, trả chính exception thay vì null.

**5. Vì sao kiểm tra NoUnique trước NoSuch?**  
NoUniqueBeanDefinitionException kế thừa NoSuchBeanDefinitionException.

**6. `getBeanName()` có luôn non-null?**  
Không. Mỗi wrapper có thể chứa hoặc không chứa tên bean.

**7. `getInjectionPoint()` thuộc exception nào?**  
UnsatisfiedDependencyException; có thể null nếu không có metadata cần thiết.

**8. `getRelatedCauses()` có phải main chain?**  
Không. Nó là nhánh lỗi liên quan tách biệt.

**9. `getSuppressed()` có tương đương related causes của Spring không?**  
Không. Đó là cơ chế Java Throwable khác.

**10. NoSuch có phải luôn do thiếu @Component annotation?**  
Không; có thể do profile, conditions, qualifiers, scan, scope hoặc type metadata.

**11. NoUnique nên sửa thế nào?**  
Chọn candidate rõ bằng qualifier/primary/fallback hoặc sửa registration/conditions.

**12. Constructor cycle có giải bằng allowCircularReferences=true không?**  
Không chỉ bằng flag, vì chưa có instance để expose sớm.

**13. Vì sao một lỗi factory method có root IllegalArgumentException?**  
Method application code ném exception, Spring bọc qua creation pipeline.

**14. Vì sao context refresh thành công mà getBean sau đó vẫn lỗi?**  
Lazy/prototype/scoped/FactoryBean products được tạo muộn.

**15. Làm sao test bền vững?**  
Assert exception classes, causal presence, bean/injection metadata và behavior sau fix; không hardcode full message.

**16. Nguyên tắc đọc lỗi ngắn nhất?**  
Operation → outer bean → dependency chain → injection point → root cause → related branches → verify configuration → regression test.

## 19. Tài liệu chính thức

- [Spring Framework 6.2.9 Javadoc — BeanCreationException](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/BeanCreationException.html): bean name, resource, related causes và contains.
- [Spring Framework 6.2.9 Javadoc — NestedRuntimeException](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/core/NestedRuntimeException.html): getRootCause, getMostSpecificCause, contains.
- [Spring Framework 6.2.9 Javadoc — UnsatisfiedDependencyException](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/UnsatisfiedDependencyException.html): injection point.
- [Spring Framework 6.2.9 Javadoc — NoSuchBeanDefinitionException](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/NoSuchBeanDefinitionException.html).
- [Spring Framework 6.2.9 Javadoc — NoUniqueBeanDefinitionException](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/NoUniqueBeanDefinitionException.html).
- [Spring Framework 6.2.9 Javadoc — BeanCurrentlyInCreationException](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/BeanCurrentlyInCreationException.html).
- [Spring Framework 6.2.9 Javadoc — BeanInstantiationException](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/BeanInstantiationException.html).
- [Spring Framework 6.2 Reference — Dependency Injection / Circular Dependencies](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html).
- [Spring Framework 6.2 Reference — @Autowired](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html).
- [Spring Framework 6.2 Reference — Qualifiers](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-qualifiers.html).
- [Spring Framework 6.2 Reference — Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="troubleshooting-002">Thiếu định nghĩa với NoSuchBeanDefinitionException</a>

<details>
<summary>Xem chi tiết</summary>

# Thiếu định nghĩa với NoSuchBeanDefinitionException trong Spring Framework

## 1. Vấn đề và ý nghĩa của lỗi

**`NoSuchBeanDefinitionException`** là một exception trong **Spring Framework Core Container**, thuộc package `org.springframework.beans.factory`. Nó thường xuất hiện khi Spring được yêu cầu tìm hoặc tạo một bean nhưng **không có ứng viên phù hợp theo tên/kiểu và ngữ cảnh tra cứu**.

Hai tình huống dễ gặp:

~~~java
context.getBean("paymentGateway");
context.getBean(PaymentGateway.class);
~~~

Nếu context không có bean với tên hoặc type tương ứng, lời gọi có thể ném `NoSuchBeanDefinitionException`. Các snippets này cần biến `context` có kiểu `ApplicationContext` và interface `PaymentGateway`.

Một đường khác là Spring tự resolve required dependency khi tạo bean:

~~~java
public OrderService(PaymentGateway gateway) {
    this.gateway = gateway;
}
~~~

Nếu không có bean `PaymentGateway` hợp lệ, Spring có thể báo `UnsatisfiedDependencyException`/`BeanCreationException` **với `NoSuchBeanDefinitionException` nằm trong chuỗi nguyên nhân**.

> **Cốt lõi:** Lỗi không nhất thiết nói “class không tồn tại trong Java classpath”. Nó nói **BeanFactory không tìm được bean phù hợp với truy vấn cần thiết**. Một Java class có thể được biên dịch và hiện diện trên classpath nhưng chưa từng được đăng ký làm Spring bean.

Bài này thuộc mục *Chẩn đoán lỗi* của **Spring Framework — core-container**, với trọng tâm **“Thiếu định nghĩa với NoSuchBeanDefinitionException”**. Nội dung giải thích cơ chế tra cứu, cách đọc exception chain, nguyên nhân phổ biến, cách sửa theo từng tình huống và ví dụ chạy được bằng **Java 17 + Spring Context 6.1.13 + JUnit 5**.

## 2. Bean Java object và BeanDefinition là hai thứ khác nhau

Ba trạng thái cần phân biệt:

1. **Class tồn tại trên classpath:** Java compiler/classloader biết đến `PaymentGatewayImpl`.
2. **BeanDefinition được đăng ký:** Spring có metadata để tạo/mô tả bean `paymentGateway`.
3. **Bean instance có thể được truy cập:** BeanFactory thực sự giải quyết được dependency và tạo/lấy instance theo scope.

Nếu chỉ khai báo:

~~~java
public final class PaymentGatewayImpl
        implements PaymentGateway {
    // ...
}
~~~

thì Spring **không tự động** biết đây là bean. Bạn còn cần một cơ chế đăng ký, ví dụ:

- `@Component`/`@Service` kết hợp **component scanning đúng phạm vi**;
- `@Bean` method trong một configuration được nạp;
- `@Import` configuration/component thích hợp;
- `BeanDefinitionRegistry.registerBeanDefinition(...)`;
- `GenericApplicationContext.registerBean(...)`;
- XML bean definition khi ứng dụng sử dụng XML.

Những cơ chế ấy phải được chạy trên **đúng ApplicationContext** có nhiệm vụ resolve dependency.

### Không đồng nhất “class exists” với “bean exists”

~~~text
Java class exists
       |
       +-- Có @Component? -> cần scan/import phù hợp
       |
       +-- Có @Bean method? -> cần config class được xử lý
       |
       +-- Programmatic registration? -> cần gọi register trước lookup
       |
       v
BeanDefinition/instance được BeanFactory nhận biết
       |
       v
getBean(name/type) có thể resolve
~~~

Ngay cả khi definition tồn tại, scope, type, conditions hoặc bean creation failure có thể gây lỗi khác với `NoSuchBeanDefinitionException`.

## 3. Tra cứu theo tên và tra cứu theo kiểu khác nhau

### 3.1. Lookup theo tên

~~~java
Object bean = context.getBean("gateway");
~~~

Spring tìm bean **tên `gateway`**. Nếu không tồn tại tên ấy trong phạm vi lookup, có thể ném:

~~~text
NoSuchBeanDefinitionException:
No bean named 'gateway' available
~~~

Thông báo trên là **mẫu minh họa**, không dùng nó làm assertion so sánh nguyên văn vì wording có thể thay đổi giữa phiên bản.

Tên bean do `@Bean` method thường mặc định là tên method; với `@Component`, mặc định dựa trên tên class theo BeanNameGenerator. Bạn có thể đặt tên explicit:

~~~java
@Bean("gateway")
PaymentGateway sender() {
    return new HttpPaymentGateway();
}
~~~

Đây là snippet minh họa, cần interface/class cụ thể.

Nếu bean được đặt tên `gateway` thì `getBean("sender")` **không tự** tìm sang bean đó chỉ vì method Java từng có tên sender.

### 3.2. Lookup theo type

~~~java
PaymentGateway gateway =
    context.getBean(PaymentGateway.class);
~~~

Spring tìm một bean phù hợp type `PaymentGateway`. Nếu không có candidate, thường ném `NoSuchBeanDefinitionException` mang thông tin required bean type.

Nếu có **nhiều candidates** phù hợp và không thể chọn một bean duy nhất, có thể ném `NoUniqueBeanDefinitionException`.

### 3.3. Lookup vừa tên vừa type

~~~java
PaymentGateway gateway =
    context.getBean("gateway", PaymentGateway.class);
~~~

Nếu tên không tồn tại: `NoSuchBeanDefinitionException`.

Nếu tên tồn tại nhưng instance **không thuộc required type**, có thể là `BeanNotOfRequiredTypeException`. Đây là lỗi **khác** lỗi missing bean.

## 4. Phân biệt ba exception dễ nhầm

| Exception | Tình huống | Cách sửa gợi ý |
| --- | --- | --- |
| `NoSuchBeanDefinitionException` | Không có bean name/type phù hợp | Kiểm tra registration, scanning, profiles, qualifiers, context |
| `NoUniqueBeanDefinitionException` | Có nhiều beans cùng type nhưng cần một | `@Qualifier`, `@Primary` hoặc explicit name |
| `BeanNotOfRequiredTypeException` | Có bean tên đó nhưng type không đúng | Sửa name/return type/factory product type |

**Quan trọng:** Trong Spring Framework 6.1, `NoUniqueBeanDefinitionException` **kế thừa `NoSuchBeanDefinitionException`**. Vì vậy:

~~~java
catch (NoSuchBeanDefinitionException e) {
    // Có thể bắt cả NoUniqueBeanDefinitionException!
}
~~~

Nếu muốn xử lý riêng “không có ứng viên” và “có quá nhiều ứng viên”, hãy bắt subtype `NoUniqueBeanDefinitionException` trước hoặc kiểm tra `instanceof`.

Không diễn giải mọi instance của base exception là “số ứng viên bằng 0” khi chưa xét subtype.

## 5. Đọc metadata của NoSuchBeanDefinitionException

Javadoc cung cấp các getters:

~~~java
e.getBeanName();
e.getBeanType();
e.getResolvableType();
e.getNumberOfBeansFound();
~~~

Đây là snippet với `e` kiểu `NoSuchBeanDefinitionException`.

- `getBeanName()`: tên đã yêu cầu nếu lỗi xuất phát từ lookup bằng name, có thể `null`.
- `getBeanType()`: required class nếu lookup theo type, có thể `null`.
- `getResolvableType()`: thông tin type tổng quát/generics khi phù hợp.
- `getNumberOfBeansFound()`: với `NoSuchBeanDefinitionException` thông thường là 0; `NoUniqueBeanDefinitionException` có thể cho biết số ứng viên.

### Ví dụ chẩn đoán

~~~java
try {
    context.getBean(PaymentGateway.class);
} catch (NoUniqueBeanDefinitionException multiple) {
    System.out.println("Multiple candidates: " +
        multiple.getNumberOfBeansFound());
} catch (NoSuchBeanDefinitionException missing) {
    System.out.println("Missing type: " +
        missing.getBeanType());
}
~~~

Đây là snippet cần Spring imports và context đã hoạt động. Thứ tự catch là thiết yếu do quan hệ kế thừa.

### Đừng chỉ dựa vào message string

Lời nhắn exception có thể kèm tên injection point, qualifiers, bean names hoặc gợi ý auto-configuration tùy Spring/Spring Boot. Khi tự động chẩn đoán, ưu tiên **exception type, getters và cause chain** hơn so sánh toàn bộ câu tiếng Anh trong log.

## 6. Đọc chuỗi lỗi khi refresh() thất bại

Khi Spring tạo eager singleton:

~~~text
ApplicationContext.refresh()
    |
    v
Create OrderService
    |
    v
Resolve constructor parameter: PaymentGateway
    |
    v
No suitable bean found
    |
    v
NoSuchBeanDefinitionException
    |
    v
UnsatisfiedDependencyException
    |
    v
BeanCreationException / context startup failure
~~~

Lỗi ngoài cùng có thể là `UnsatisfiedDependencyException` (một subtype của `BeanCreationException`) kèm chuỗi nguyên nhân `NoSuchBeanDefinitionException`.

### Chẩn đoán có hệ thống

1. Xác định **bean đang được tạo** (ví dụ `orderService`).
2. Xác định **injection point** (constructor parameter, field, setter, `@Bean` method parameter).
3. Tìm **required type/name/generic qualifier**.
4. Kiểm tra có definition ở context đang sử dụng không.
5. Kiểm tra profile/condition có loại ứng viên không.
6. Kiểm tra component scanning/import có đăng ký bean không.
7. Đối chiếu multiple candidate hoặc wrong type; đừng nhầm ba exception.
8. Sửa **nguyên nhân registration hoặc dependency contract**, không chỉ catch exception rồi bỏ qua required functionality.

## 7. Nguyên nhân 1 — chưa hề đăng ký bean

Ví dụ:

~~~java
public final class StripeGateway
        implements PaymentGateway {
    // ...
}
~~~

Một class Java thuần không tự xuất hiện trong Spring registry.

### Cách sửa bằng @Bean

~~~java
@Configuration
class PaymentConfig {

    @Bean
    PaymentGateway paymentGateway() {
        return new StripeGateway();
    }
}
~~~

Đây là snippets minh họa, cần concrete constructor/interface và imports `Configuration`, `Bean`. Bảo đảm `PaymentConfig` được nạp vào ApplicationContext.

### Cách sửa bằng stereotype + scan

~~~java
@Service
class StripeGateway implements PaymentGateway {
    // ...
}
~~~

Và có configuration thích hợp:

~~~java
@Configuration
@ComponentScan("com.example.payment")
class AppConfig {
}
~~~

`@Service` đặt trên class **chưa đủ** nếu nó nằm ngoài component scan hoặc context không xử lý annotation scanning.

## 8. Nguyên nhân 2 — @ComponentScan bỏ sót package

Ví dụ ứng dụng chỉ scan:

~~~java
@ComponentScan("com.example.orders")
~~~

nhưng bean implementation nằm trong:

~~~text
com.example.payments.StripeGateway
~~~

Nếu không có `@Import` hoặc explicit bean definition tương ứng, bean ở payment package không được scan.

### Điều cần kiểm tra

- Package thực sự của class.
- Class có `@Component`, `@Service` hoặc meta-annotation thích hợp không.
- `@ComponentScan(basePackages=...)` hoặc `basePackageClasses` có bao phủ package không.
- Có filters loại class khỏi scanning không.
- Test có chỉ register một config slice không.
- Spring Boot main class đặt ở package có phạm vi scan phù hợp không.

### Khuyến nghị

Dùng package layout rõ ràng hoặc `basePackageClasses` với marker class khi phù hợp, tránh string package paths dễ lỗi khi refactor.

Đừng giải quyết thiếu bean bằng component-scanning **toàn classpath**; việc đó có thể kéo vào beans không liên quan, tăng thời gian startup và gây candidate ambiguity.

## 9. Nguyên nhân 3 — config chứa @Bean không được import

Một class:

~~~java
@Configuration
class MessagingConfig {
    @Bean
    MessageSender messageSender() {
        return new ConsoleSender();
    }
}
~~~

không có nghĩa mọi ApplicationContext đều nhận bean. Nếu bạn tạo:

~~~java
AnnotationConfigApplicationContext context =
    new AnnotationConfigApplicationContext(OtherConfig.class);
~~~

mà `OtherConfig` không import MessagingConfig và không có scan, `messageSender` không tồn tại trong context đó.

### Sửa bằng register/import

~~~java
context.register(OtherConfig.class, MessagingConfig.class);
context.refresh();
~~~

hoặc:

~~~java
@Configuration
@Import(MessagingConfig.class)
class OtherConfig {
}
~~~

Các snippets này yêu cầu classes thích hợp và đúng giai đoạn: với manual context, register **trước refresh**.

Đừng nhầm “có class config trên classpath” với “config đã được Spring xử lý”.

## 10. Nguyên nhân 4 — @Profile không match

Spring `@Profile` có thể đặt trên `@Configuration` hoặc `@Bean` method.

~~~java
@Configuration
@Profile("dev")
class DevGatewayConfig {

    @Bean
    PaymentGateway paymentGateway() {
        return new DevGateway();
    }
}
~~~

Nếu active profile là `prod`, config dev không match, nên bean được mô tả trong đó sẽ không được đăng ký.

### Cách kiểm tra

~~~java
String[] profiles =
    context.getEnvironment().getActiveProfiles();
~~~

Đừng suy rằng profile `dev` đang bật chỉ vì developer chạy local. Có thể test/CI hoặc external configuration đặt profiles khác.

### Sửa trong manual context

~~~java
AnnotationConfigApplicationContext context =
    new AnnotationConfigApplicationContext();

context.getEnvironment().setActiveProfiles("dev");
context.register(DevGatewayConfig.class);
context.refresh();
~~~

**Đặt active profile trước refresh.** Thay profile sau khi context đã hoạt động không tự làm BeanFactory nạp lại các bean definitions bị loại lúc bootstrap.

### Default profile

Khi không có active profile, Spring sử dụng default profile semantics. `@Profile("default")` không đồng nghĩa với “fallback luôn luôn có” khi một profile khác đang active.

## 11. Nguyên nhân 5 — @Conditional hoặc cấu hình Boot không match

Spring `@Conditional` có thể loại một configuration/bean definition nếu condition trả false. Spring Boot còn nhiều annotations như:

- `@ConditionalOnProperty`.
- `@ConditionalOnClass`.
- `@ConditionalOnBean`.
- `@ConditionalOnMissingBean`.

Những annotations `ConditionalOn...` thuộc **Spring Boot**, không phải cùng API cốt lõi của Spring Framework thuần.

Nếu class/bean bị loại bởi condition, dù source có `@Bean`, BeanFactory vẫn có thể không có definition mong đợi.

### Debug

- Với Framework Core: kiểm tra `@Conditional` class, condition implementation và Environment.
- Với Spring Boot: dùng condition evaluation report/debug logging/Actuator conditions khi phù hợp.
- Kiểm tra property value thực tế và active profiles.
- Đảm bảo classpath dependencies của conditional configuration có mặt.

### Tránh đặt condition sai cấp

`@Conditional` trên configuration class có thể loại cả những `@Bean` methods bên trong. Điều kiện trên từng method chỉ ảnh hưởng bean method tương ứng. Cần xác định chính xác scope của điều kiện.

## 12. Nguyên nhân 6 — tên bean hoặc @Qualifier không khớp

Nếu bạn khai báo:

~~~java
@Bean("stripeGateway")
PaymentGateway gateway() {
    return new StripeGateway();
}
~~~

thì tên bean là `stripeGateway`, không nhất thiết là `gateway`.

~~~java
context.getBean("gateway");
~~~

có thể ném `NoSuchBeanDefinitionException` dù context có một PaymentGateway bean tên `stripeGateway`.

### @Qualifier không sinh bean mới

~~~java
public CheckoutService(
        @Qualifier("stripeGateway") PaymentGateway gateway) {
    // ...
}
~~~

Qualifier giúp Spring **chọn candidate**; nó không đăng ký implementation. Nếu không có candidate phù hợp qualifier, injection có thể thất bại.

### Bean aliases

Spring hỗ trợ aliases. Kiểm tra tên canonical và aliases nếu ứng dụng có alias registrations. Không đoán tên bean chỉ từ class filename hoặc tên biến của field.

## 13. Nguyên nhân 7 — @Bean return type và type prediction

Giả sử:

~~~java
@Bean
PaymentGateway gateway() {
    return new StripeGateway();
}
~~~

Nếu injection yêu cầu cụ thể `StripeGateway` thay vì `PaymentGateway`, type prediction có thể bị giới hạn bởi return type khai báo, đặc biệt khi bean chưa instantiate.

Spring Reference khuyến nghị khai báo return type **đủ cụ thể cho các injection points dự kiến**, hoặc nhất quán phụ thuộc trên interface contract.

### Khi nào nên sửa?

Nếu consumer thật sự cần các method chỉ có trên StripeGateway, cân nhắc interface phù hợp hoặc khai báo return type cụ thể. Nhưng đừng ép mọi consumer phụ thuộc concrete implementation chỉ để làm test qua; dependency inversion có giá trị riêng.

### @Bean trả null

Nếu factory method trả null hoặc factory product type không như kỳ vọng, triệu chứng có thể khác missing definition thuần; kiểm tra return type, factory behavior và nested exceptions. Đừng quy mọi startup failure về NoSuchBeanDefinitionException.

## 14. Nguyên nhân 8 — bean ở context khác

Spring cho phép ApplicationContext/BeanFactory hierarchy:

~~~text
Parent context
  sharedGateway

Child context
  checkoutService
~~~

Child có thể lookup ancestor bean khi không có local bean cùng tên phù hợp theo hierarchical rules. Nhưng parent **không tự nhìn xuống child**.

Nếu `sharedGateway` chỉ đăng ký tại child nhưng một bean trong parent cần nó, lookup từ parent có thể thất bại.

### Cần kiểm tra

- Bean consumer được tạo ở context nào?
- Bean provider được đăng ký ở context nào?
- Context con đã `setParent(...)` **trước refresh** chưa?
- Có local bean cùng tên che ancestor bean không?
- Test chỉ tạo một context tối thiểu và bỏ sót parent không?

### Chú ý containsBean vs containsBeanDefinition

~~~java
child.containsBean("sharedGateway");
child.containsLocalBean("sharedGateway");
child.containsBeanDefinition("sharedGateway");
~~~

Với ancestor-only bean, `containsBean` có thể true, nhưng `containsLocalBean` và `containsBeanDefinition` ở child là false.

Đừng kết luận “không có bean anywhere” chỉ từ `containsBeanDefinition` ở một local registry.

## 15. Nguyên nhân 9 — scope và thời điểm lookup

Một BeanDefinition có thể tồn tại nhưng instance chưa truy cập được vì scope không active.

Ví dụ bean dùng `request` scope nhưng bạn tra cứu trong background thread không có HTTP request context. Lỗi tiêu biểu thường là `ScopeNotActiveException`, **không phải NoSuchBeanDefinitionException**.

Ngoài ra:

- Lazy singleton có thể không được tạo tại refresh.
- Prototype được tạo khi lookup.
- `FactoryBean` có thể expose product type khác factory type.
- Một bean creation exception do constructor/init method thất bại là lỗi khác missing definition.

**Định nghĩa tồn tại** nhưng **không tạo được object** phải được chẩn đoán riêng.

## 16. Nguyên nhân 10 — custom BeanFactory thiếu annotation infrastructure

Một bare `DefaultListableBeanFactory` không tự cấu hình tất cả processors để xử lý `@Autowired`, `@ComponentScan`, `@PostConstruct` hoặc full `@Configuration` semantics giống `AnnotationConfigApplicationContext`.

Nếu bạn tạo low-level BeanFactory và chỉ register Java class, annotation-based injection có thể không hoạt động như mong đợi.

### Cách sửa

Nếu bạn cần Java-config annotation processing, dùng `AnnotationConfigApplicationContext` hoặc đăng ký infrastructure processors cần thiết. Với custom IoC test, đọc rõ BeanFactory implementation đang dùng.

Đừng xem lỗi thuộc một annotation bị bỏ qua như “Spring không nhận ra class” mà không kiểm tra hạ tầng configuration.

## 17. Điều tra bằng containsBean và BeanDefinitionRegistry

Một số checks:

~~~java
boolean visible = context.containsBean("gateway");
boolean local = context.containsLocalBean("gateway");
boolean definedLocally =
    context.containsBeanDefinition("gateway");
~~~

- `containsBean`: bean name visible theo context lookup, có thể thấy ancestor.
- `containsLocalBean`: bean thuộc local factory.
- `containsBeanDefinition`: registry local có definition theo name; không phải mọi manually registered singleton đều có BeanDefinition.

### Kiểm tra names theo type

~~~java
String[] names =
    context.getBeanNamesForType(PaymentGateway.class);
~~~

Đây là local ListableBeanFactory enumeration (đối với `AnnotationConfigApplicationContext`); ancestor beans không tự được gộp trong method này.

Khi cần ancestor-inclusive lookup, Spring có `BeanFactoryUtils.beansOfTypeIncludingAncestors`, nhưng có quy tắc name shadowing; không đồng nhất với việc liệt kê mọi physical instance từ mọi factory.

### In ra names an toàn

Bạn có thể in bean names/type để debug, nhưng không log property values chứa passwords/secrets. Trong production, logs cần kiểm soát cả bean names nếu tên mã hóa thông tin nhạy cảm.

## 18. Khi thiếu dependency là tùy chọn thực sự

Một required dependency **nên làm startup fail** nếu thiếu. Tuy nhiên có những tính năng hoàn toàn tùy chọn, ví dụ metrics sink không bắt buộc.

Có thể dùng `ObjectProvider<T>`:

~~~java
public final class MetricsReporter {
    private final ObjectProvider<MetricsSink> sink;

    public MetricsReporter(ObjectProvider<MetricsSink> sink) {
        this.sink = sink;
    }

    public void report(String event) {
        MetricsSink available = sink.getIfAvailable();
        if (available != null) {
            available.record(event);
        }
    }
}
~~~

Snippet cần `ObjectProvider`/`MetricsSink`. `getIfAvailable()` trả `null` nếu không có bean phù hợp, nhưng **có thể ném `NoUniqueBeanDefinitionException`** nếu có nhiều bean mà không chọn được. Nó không tự là “swallow all DI problems”.

### Các lựa chọn khác

- `Optional<T>` trong constructor injection: diễn tả dependency có thể vắng mặt.
- `@Autowired(required = false)` trên field/method: cho phép bỏ qua injection point phù hợp; với method nhiều parameters, injection có thể bị bỏ qua cả method.
- `@Nullable` tại injection point khi phù hợp.
- `ObjectProvider.getIfUnique()` khi muốn không chọn nếu không có **unique** candidate; hiểu đúng semantics trước khi sử dụng.
- List/Map collections có cơ chế resolution riêng, không mặc định mọi khai báo tập hợp là optional trong mọi injection style.

### Không biến core requirement thành optional để chữa cấu hình sai

Nếu PaymentGateway là required để xử lý thanh toán, biến nó thành Optional rồi im lặng tiếp tục có thể làm ứng dụng chạy sai business contract. Sửa registration hoặc profile thay vì giấu lỗi.

## 19. Vì sao catch NoSuchBeanDefinitionException ở runtime có thể nguy hiểm?

Anti-pattern:

~~~java
try {
    gateway.charge();
} catch (NoSuchBeanDefinitionException ignored) {
    // Bỏ qua thiếu thanh toán.
}
~~~

Trường hợp trên là ví dụ xấu: exception từ IoC không nhất thiết xuất hiện ở `charge()`, và việc bỏ qua thanh toán có thể là lỗi nghiệp vụ nghiêm trọng.

### Khi nào catch hợp lý?

- Khi dùng `getBean(name/type)` cho một optional extension point và có fallback rõ.
- Trong công cụ chẩn đoán bean registration.
- Trong negative test xác nhận bean đúng là không được đăng ký.

Với required dependency, ưu tiên fail-fast, log cause chain đủ thông tin và sửa configuration.

### Ưu tiên getBeanProvider khi cần optional lookup

`ObjectProvider` biểu đạt optional/on-demand lookup tốt hơn việc dùng try/catch `getBean` ở rất nhiều tình huống. Nhưng luôn xét ambiguity và bean creation errors riêng.

## 20. Ví dụ Maven hoàn chỉnh — Java 17 + Spring Context 6.1.13

Project demo các tình huống:

1. Empty context không có bean, tra theo tên và type ném exception.
2. Optional provider lookup trả null.
3. Context có consumer nhưng thiếu dependency: `refresh()` thất bại.
4. Context hợp lệ đăng ký provider và consumer, chạy được.
5. Bean nằm trong `@Profile("lab")` bị loại khi không active.
6. Kích hoạt profile trước refresh giúp bean được tạo.
7. Hai beans cùng type tạo `NoUniqueBeanDefinitionException` thay vì missing-candidate condition.

### 20.1. Cấu trúc thư mục

~~~text
missing-bean-diagnostics/
├── pom.xml
└── src/
    ├── main/java/com/example/missing/MissingBeanDemo.java
    └── test/java/com/example/missing/MissingBeanDemoTest.java
~~~

### 20.2. File pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>missing-bean-diagnostics</artifactId>
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
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.1</version>
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
                <version>3.2.5</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <mainClass>com.example.missing.MissingBeanDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 20.3. File src/main/java/com/example/missing/MissingBeanDemo.java

~~~java
package com.example.missing;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

public class MissingBeanDemo {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext empty =
                 new AnnotationConfigApplicationContext()) {
            empty.refresh();

            System.out.println("empty.contains-gateway=" +
                empty.containsBean("gateway"));

            try {
                empty.getBean("gateway");
            } catch (NoSuchBeanDefinitionException e) {
                System.out.println("empty.by-name=" +
                    e.getBeanName());
            }

            try {
                empty.getBean(Sender.class);
            } catch (NoSuchBeanDefinitionException e) {
                System.out.println("empty.by-type=" +
                    e.getBeanType().getSimpleName());
            }

            System.out.println("empty.optional=" +
                (empty.getBeanProvider(Sender.class)
                    .getIfAvailable() == null));
        }

        try (AnnotationConfigApplicationContext broken =
                 new AnnotationConfigApplicationContext()) {
            broken.register(ConsumerConfig.class);
            try {
                broken.refresh();
            } catch (BeanCreationException e) {
                System.out.println("broken.refresh-missing-sender=" +
                    containsMissingSender(e));
            }
        }

        try (AnnotationConfigApplicationContext healthy =
                 new AnnotationConfigApplicationContext(
                     GatewayConfig.class, ConsumerConfig.class)) {

            System.out.println("healthy.checkout=" +
                healthy.getBean(Checkout.class).process("ORD-1"));
        }

        try (AnnotationConfigApplicationContext disabled =
                 new AnnotationConfigApplicationContext(
                     LabGatewayConfig.class)) {

            System.out.println("lab.disabled=" +
                disabled.containsBean("gateway"));
        }

        try (AnnotationConfigApplicationContext enabled =
                 new AnnotationConfigApplicationContext()) {

            enabled.getEnvironment().setActiveProfiles("lab");
            enabled.register(LabGatewayConfig.class, ConsumerConfig.class);
            enabled.refresh();

            System.out.println("lab.enabled=" +
                enabled.getBean(Checkout.class).process("ORD-2"));
        }

        try (AnnotationConfigApplicationContext multiple =
                 new AnnotationConfigApplicationContext(
                     TwoGatewayConfig.class)) {

            try {
                multiple.getBean(Sender.class);
            } catch (NoUniqueBeanDefinitionException e) {
                System.out.println("ambiguous.count=" +
                    e.getNumberOfBeansFound());
            }
        }
    }

    public static boolean containsMissingSender(Throwable error) {
        for (Throwable cause = error;
             cause != null;
             cause = cause.getCause()) {
            if (cause instanceof NoSuchBeanDefinitionException missing
                    && !(missing instanceof NoUniqueBeanDefinitionException)
                    && missing.getBeanType() == Sender.class) {
                return true;
            }
        }
        return false;
    }

    public interface Sender {
        String send(String orderId);
    }

    public record PrefixSender(String prefix) implements Sender {
        @Override
        public String send(String orderId) {
            return prefix + ":" + orderId;
        }
    }

    public static final class Checkout {
        private final Sender sender;

        public Checkout(Sender sender) {
            this.sender = sender;
        }

        public String process(String id) {
            return sender.send(id);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ConsumerConfig {

        @Bean
        public Checkout checkout(Sender sender) {
            return new Checkout(sender);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class GatewayConfig {

        @Bean("gateway")
        public Sender gateway() {
            return new PrefixSender("healthy");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("lab")
    public static class LabGatewayConfig {

        @Bean("gateway")
        public Sender gateway() {
            return new PrefixSender("lab");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class TwoGatewayConfig {

        @Bean
        public Sender firstSender() {
            return new PrefixSender("first");
        }

        @Bean
        public Sender secondSender() {
            return new PrefixSender("second");
        }
    }
}
~~~

### 20.4. Chạy chương trình

Yêu cầu JDK 17+ và Maven 3.9.x. Từ thư mục chứa pom.xml:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do chính `System.out.println` in ra theo chương trình:

~~~text
empty.contains-gateway=false
empty.by-name=gateway
empty.by-type=Sender
empty.optional=true
broken.refresh-missing-sender=true
healthy.checkout=healthy:ORD-1
lab.disabled=false
lab.enabled=lab:ORD-2
ambiguous.count=2
~~~

Spring có thể in warning/error log khi `broken.refresh()` cố ý thất bại. Maven/logging cũng có thể có dòng bổ sung. Những dòng trong khối trên chỉ là **stdout được code demo in có chủ đích**, không phải cam kết toàn bộ console chỉ chứa chín dòng.

### 20.5. Phân tích các dòng

**empty.contains-gateway=false:** Đã `refresh()` một context không register GatewayConfig, nên không có bean name gateway.

**empty.by-name=gateway:** `getBean("gateway")` tạo `NoSuchBeanDefinitionException` chứa requested bean name.

**empty.by-type=Sender:** `getBean(Sender.class)` không có candidate; exception chứa required type Sender.

**empty.optional=true:** `ObjectProvider<Sender>.getIfAvailable()` trả null khi không có Sender, nên biểu thức so sánh bằng null là true.

**broken.refresh-missing-sender=true:** ConsumerConfig khai báo eager Checkout(Sender); Spring không tìm được Sender, báo bean creation failure với nested missing Sender cause.

**healthy.checkout=healthy:ORD-1:** GatewayConfig tạo Sender, ConsumerConfig dùng nó và Checkout xử lý thành công.

**lab.disabled=false:** Chỉ register LabGatewayConfig nhưng không bật profile lab; cấu hình bị loại nên không có gateway bean.

**lab.enabled=lab:ORD-2:** Bật profile lab trước register/refresh, Sender được tạo và Checkout dùng nó.

**ambiguous.count=2:** Hai Sender beans cùng type trong TwoGatewayConfig; lookup theo type không có unique candidate, `NoUniqueBeanDefinitionException` báo hai beans.

### 20.6. Tại sao negative startup test vẫn hữu ích?

Một ứng dụng có thể compile đầy đủ dù thiếu dependency ở Spring runtime. Cấu hình ConsumerConfig chỉ cần type Sender, nhưng thiếu Sender bean không được Java compiler phát hiện. `refresh()` chủ động tạo non-lazy Checkout singleton nên báo lỗi sớm.

Khi app sử dụng lazy/prototype/special scope, hãy kích hoạt `getBean` tương ứng để test đúng thời điểm resolution.

## 21. JUnit 5 — Kiểm tra mọi đường lỗi chính

### 21.1. File src/test/java/com/example/missing/MissingBeanDemoTest.java

~~~java
package com.example.missing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class MissingBeanDemoTest {

    @Test
    void missingNameReportsRequestedBeanName() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext()) {
            context.refresh();

            NoSuchBeanDefinitionException exception =
                assertThrows(
                    NoSuchBeanDefinitionException.class,
                    () -> context.getBean("gateway"));

            assertEquals("gateway", exception.getBeanName());
            assertFalse(context.containsBean("gateway"));
        }
    }

    @Test
    void missingTypeReportsRequiredClass() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext()) {
            context.refresh();

            NoSuchBeanDefinitionException exception =
                assertThrows(
                    NoSuchBeanDefinitionException.class,
                    () -> context.getBean(
                        MissingBeanDemo.Sender.class));

            assertEquals(
                MissingBeanDemo.Sender.class,
                exception.getBeanType());

            assertNull(
                context.getBeanProvider(
                    MissingBeanDemo.Sender.class)
                    .getIfAvailable());
        }
    }

    @Test
    void requiredDependencyFailsAtRefresh() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext()) {

            context.register(
                MissingBeanDemo.ConsumerConfig.class);

            BeanCreationException exception =
                assertThrows(
                    BeanCreationException.class,
                    context::refresh);

            assertTrue(
                MissingBeanDemo.containsMissingSender(exception));
        }
    }

    @Test
    void healthyConfigurationWiresSender() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(
                     MissingBeanDemo.GatewayConfig.class,
                     MissingBeanDemo.ConsumerConfig.class)) {

            MissingBeanDemo.Checkout checkout =
                context.getBean(MissingBeanDemo.Checkout.class);

            assertEquals("healthy:ORD-1",
                checkout.process("ORD-1"));
            assertTrue(context.containsBean("gateway"));
        }
    }

    @Test
    void inactiveProfileDoesNotRegisterGateway() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("not-lab");
            context.register(
                MissingBeanDemo.LabGatewayConfig.class);
            context.refresh();

            assertFalse(context.containsBean("gateway"));
            assertFalse(
                context.containsBeanDefinition("gateway"));
        }
    }

    @Test
    void activeProfileRegistersGateway() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext()) {

            context.getEnvironment().setActiveProfiles("lab");
            context.register(
                MissingBeanDemo.LabGatewayConfig.class,
                MissingBeanDemo.ConsumerConfig.class);
            context.refresh();

            assertEquals("lab:ORD-2",
                context.getBean(
                    MissingBeanDemo.Checkout.class)
                    .process("ORD-2"));
        }
    }

    @Test
    void multipleBeansAreAmbiguousNotMissing() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(
                     MissingBeanDemo.TwoGatewayConfig.class)) {

            NoUniqueBeanDefinitionException exception =
                assertThrows(
                    NoUniqueBeanDefinitionException.class,
                    () -> context.getBean(
                        MissingBeanDemo.Sender.class));

            assertEquals(2, exception.getNumberOfBeansFound());
        }
    }
}
~~~

### 21.2. Lệnh chạy tests

~~~bash
mvn -q test
~~~

Dự kiến **7 JUnit Jupiter tests, 0 failures, 0 errors**. Maven `-q` thường không in mọi test name; Surefire viết reports vào `target/surefire-reports`.

Negative test cố ý khiến context refresh fail; Spring có thể log một warning trước khi `assertThrows` xác nhận expected exception. Đây là kết quả test đúng, không phải build fail.

## 22. Phân biệt kiểm thử âm tính và bỏ qua lỗi

Một negative test có thể hợp lệ:

~~~java
assertThrows(
    NoSuchBeanDefinitionException.class,
    () -> context.getBean(Sender.class));
~~~

Nhưng logic production cố bắt exception thiếu required dependency rồi bỏ qua sẽ làm sai hệ thống.

**Hướng ưu tiên:** phát hiện missing bean tại startup, giữ dependency bắt buộc, và sửa configuration trong test/prod. Optional chỉ dành cho tính năng thực sự optional.

Test cần xác nhận cả happy path khi bean có mặt. Một chương trình chỉ in missing exception nhưng không có healthy configuration dễ khiến người học không thấy cách sửa.

## 23. @Autowired(required=false) có thay thế Optional không?

Với field/method injection, `@Autowired(required=false)` cho phép Spring bỏ qua injection khi không có candidate phù hợp.

Ví dụ:

~~~java
@Autowired(required = false)
public void setMetricsSink(MetricsSink sink) {
    this.metricsSink = sink;
}
~~~

Đây là snippet với class `MetricsSink` và field `metricsSink` được khai báo thích hợp. Nếu dependency không satisfiable, một optional annotated method có thể **không được gọi**, không chỉ được truyền null.

Constructor injection có quy tắc resolution riêng; nếu muốn biểu diễn optional constructor dependency rõ, `Optional<MetricsSink>` hoặc `ObjectProvider<MetricsSink>` thường dễ hiểu hơn.

Không thay toàn bộ required collaborator bằng `@Autowired(required=false)` để tránh startup failure.

## 24. @Resource, @Inject và tên bean

Ngoài `@Autowired`, Java/Spring applications có thể sử dụng:

- `jakarta.annotation.Resource`, thường nhấn mạnh name-based resolution trong nhiều trường hợp.
- `jakarta.inject.Inject`, nếu thư viện injection tương ứng có trên classpath.
- `@Qualifier` để thu hẹp candidates.
- `@Value` cho configuration values.

Với `@Resource(name="gateway")`, kiểm tra exact bean name và annotation processing infrastructure. Không suy rằng required bean đã được đăng ký chỉ vì Java annotation xuất hiện trong source.

### Annotation infrastructure trong test

`AnnotationConfigApplicationContext` chuẩn có cơ chế xử lý Spring annotation config. Một low-level BeanFactory tự tạo có thể thiếu processors cần thiết. Để chẩn đoán, kiểm tra **container type và cách khởi tạo**, không chỉ annotation declaration.

## 25. Scope, lazy và thời điểm xuất hiện exception

Một lỗi missing bean không nhất thiết lộ ở cùng thời điểm:

| Tình huống | Thời điểm thường gặp |
| --- | --- |
| Eager singleton cần missing dependency | `refresh()` |
| Lazy singleton cần dependency | `getBean()` hoặc khi bị eager bean khác cần |
| Prototype cần dependency | Mỗi lần tạo prototype |
| Optional ObjectProvider | Khi `getIfAvailable/getObject` được gọi |
| @Profile condition không match | Không có definition sau bootstrap |
| @Conditional condition false | Không có definition tương ứng |
| Named lookup bean không tồn tại | `getBean(name)` ngay |
| Web scope inactive | Có thể `ScopeNotActiveException` |

Không kết luận “config chắc chắn hợp lệ” chỉ vì `refresh()` thành công nếu bean quan trọng là lazy hoặc prototype chưa được tạo.

## 26. Spring Boot có thể báo "Consider defining a bean..."

Spring Boot có thể bổ sung failure analysis, chẳng hạn:

~~~text
Consider defining a bean of type '...'
in your configuration.
~~~

Đây là gợi ý phổ biến nhưng **không phải lời giải tự động**.

Nếu bean bị loại do condition/profile, việc định nghĩa thêm một bean có thể che sai config hoặc làm nhiều candidates xuất hiện.

### Checklist Boot riêng

- `@SpringBootApplication` nằm ở package gốc đúng phạm vi scan?
- Auto-configuration phù hợp có được bật?
- `@ConditionalOnClass` có library yêu cầu không?
- `@ConditionalOnProperty` có property đúng không?
- Profile thực tế ở environment là gì?
- Auto-configuration đã bị exclude không?
- Có `@MockBean`/test replacement (tùy Boot version) làm test graph khác production không?

Bài này vẫn thuộc Spring Core Container; Spring Boot bổ sung layer scanning/auto-config/failure analysis, không làm thay ý nghĩa cơ bản của missing bean.

## 27. Parent/child ApplicationContext khi debug missing bean

Nếu root context chứa `sharedSender` và một child context chứa consumer, child có thể thấy root bean qua hierarchical lookup.

Nhưng nếu root consumer cần bean chỉ ở child, root không tự tìm xuống child.

Cũng cần chú ý local bean name masking. Nếu child đăng ký bean cùng name nhưng khác type, named lookup từ child có thể gặp `BeanNotOfRequiredTypeException` thay vì fallback lên parent đúng type.

### Thực hành

- Log/inspect context ID và parent factory nếu cần.
- So sánh `containsBean` với `containsLocalBean`.
- Nếu cần lấy bean ở parent để chẩn đoán, lookup trực tiếp parent.
- Không assume bean definition có mặt trên toàn hierarchy chỉ vì một child tìm thấy nó.

## 28. Kiểm tra FactoryBean và product type

`FactoryBean<T>` là một abstraction nâng cao trong Spring:

- `getBean("builder")` thường lấy **object product** do FactoryBean tạo.
- `getBean("&builder")` yêu cầu chính **FactoryBean instance**.
- `FactoryBean.getObjectType()` ảnh hưởng việc BeanFactory dự đoán product type.

Nếu FactoryBean trả type không đúng hoặc không khai báo type metadata đủ, by-type lookup có thể có kết quả bất ngờ. Đây không nhất thiết là bean definition hoàn toàn vắng mặt; cần phân biệt **factory metadata**, **product object** và **dereference prefix &**.

Khi đang học lỗi `NoSuchBeanDefinitionException`, kiểm tra bean regular trước, rồi mới đi sâu FactoryBean nếu ứng dụng thật có dùng.

## 29. Thực hiện debug có hệ thống

Một quy trình từ nhanh tới sâu:

1. Xác định exact exception class; bắt `NoUniqueBeanDefinitionException` riêng.
2. Tìm required bean name/type từ exception getters và cause chain.
3. Kiểm tra consumer đang được tạo ở context nào.
4. Kiểm tra bean có `@Component`/`@Service` hoặc `@Bean` không.
5. Kiểm tra config class đã register/import chưa.
6. Kiểm tra phạm vi component scanning và package.
7. Kiểm tra active/default profiles **trước refresh**.
8. Kiểm tra conditions, Boot auto-config khi có.
9. Kiểm tra `@Qualifier`/`@Resource` bean names.
10. Kiểm tra các implementations có đúng assignable type không.
11. Kiểm tra BeanDefinition/bean names qua `containsBeanDefinition`, `getBeanNamesForType`.
12. Kiểm tra parent/child hierarchy.
13. Kiểm tra lazy/prototype/scope timing.
14. Test registration tối thiểu để cô lập lỗi.
15. Sửa đúng layer và viết regression tests cho healthy và negative scenarios.

Không xóa qualifier hoặc thêm `@Primary` theo cảm tính; mỗi thao tác đó có thể tạo lỗi chọn nhầm implementation trong môi trường khác.

## 30. Logging và an toàn khi chẩn đoán

Những log hữu ích:

- Requested bean name/type.
- Injection point type/parameter name.
- Context ID.
- Active profiles.
- Tên configuration class.
- Candidate bean names và declared types.
- Exception class/cause chain.
- Condition diagnostics khi có.

Hạn chế log:

- Full bean property values nếu có secrets.
- Credentials trong Environment.
- API tokens/connection URLs có passwords.
- Personal data được giữ trong test objects.

Với test local, bật log Spring ở mức DEBUG có thể giúp thấy bean registration và injection, nhưng production log level cần kiểm soát hiệu năng và quyền truy cập.

## 31. Những anti-pattern dễ gặp

**1.** Thêm `@Component` nhưng không bật component scanning.

**2.** Đặt `@Bean` trong configuration class chưa được import.

**3.** Chọn profile sau `refresh()`.

**4.** Thêm một bean thứ hai để chữa thiếu, rồi tạo `NoUniqueBeanDefinitionException`.

**5.** Catch base `NoSuchBeanDefinitionException` và xử lý cả subtype NoUnique như một lỗi “0 candidates”.

**6.** Bật `@Autowired(required=false)` cho dependency bắt buộc để application startup qua.

**7.** Dùng `ApplicationContext.getBean` ở nhiều business methods như service locator, khiến lỗi đến muộn.

**8.** Dựa vào `containsBeanDefinition` local để khẳng định ancestor không có bean.

**9.** Đổi `@Bean` return type quá trừu tượng trong khi injection point yêu cầu concrete class không thể dự đoán trước instantiate.

**10.** Đặt name/qualifier sai nhưng cố thay bằng `@Primary` thay vì sửa tên.

**11.** Tin Spring Boot auto-config sẽ tự tạo mọi interface implementation.

**12.** Chỉ test happy path với mocked collaborator và không chạy context wiring regression test.

## 32. Câu hỏi ôn tập

**Câu 1:** Java class đã tồn tại nhưng `getBean(Type)` báo NoSuch. Vì sao? **Vì class chưa chắc được đăng ký làm Spring bean** hoặc bị loại bởi profile/condition.

**Câu 2:** `getBean("gateway")` khác `getBean(Sender.class)`? **Một cái tra theo tên, một cái theo type**.

**Câu 3:** Thiếu Sender khi tạo CheckoutService có thể xuất hiện ở đâu? **Trong cause chain của BeanCreationException/UnsatisfiedDependencyException khi refresh**.

**Câu 4:** NoUnique có thuộc NoSuch hierarchy không? **Có, NoUniqueBeanDefinitionException extends NoSuchBeanDefinitionException** trong Spring 6.1, nhưng nghĩa là nhiều candidates.

**Câu 5:** `@Profile("lab")` mà chỉ có active `prod` thì sao? **Configuration/bean bị loại**, nếu không có lựa chọn khác sẽ thiếu bean.

**Câu 6:** `@Component` có tự được scan trong mọi context không? **Không**; cần scanning/import.

**Câu 7:** `@Qualifier` có tạo bean mới không? **Không**, chỉ thu hẹp candidate selection.

**Câu 8:** `getIfAvailable()` có bao giờ ném khi hai beans cùng type? **Có thể ném NoUnique** nếu không chọn được unique candidate.

**Câu 9:** Bean ở child có tự được parent thấy không? **Không** theo lookup mặc định.

**Câu 10:** Missing definition và ScopeNotActive có giống nhau không? **Không**; một bên thiếu candidate/definition, bên kia scope của bean không active.

**Câu 11:** Bật `required=false` cho PaymentGateway bắt buộc có phải sửa đúng không? **Không**, phải bảo đảm registration đúng.

**Câu 12:** Nên assert nguyên văn exception message không? **Không nên**; ưu tiên type/getters/cause chain.

## 33. Checklist code review

- [ ] Đã xác định chính xác bean name/type đang thiếu?
- [ ] Exception là NoSuch thường hay subtype NoUnique?
- [ ] Đã kiểm tra wrapped causes trong BeanCreationException?
- [ ] Class tồn tại có đồng nghĩa được Spring register không?
- [ ] @ComponentScan đúng package và filters?
- [ ] Configuration chứa @Bean đã được import/register?
- [ ] Active/default profiles đúng trước refresh?
- [ ] @Conditional/Boot conditions có loại bean không?
- [ ] Bean names, aliases và qualifiers khớp?
- [ ] Constructor/factory parameter cần interface hay concrete type?
- [ ] Context hierarchy đúng hướng parent/child?
- [ ] containsBean, containsLocalBean, containsBeanDefinition được dùng đúng?
- [ ] Lazy/prototype/FactoryBean có làm lỗi xảy ra muộn?
- [ ] Có phân biệt sai type và thiếu name?
- [ ] Required dependency không bị làm optional cho qua lỗi?
- [ ] Optional dependency dùng provider/Optional đúng semantics?
- [ ] Có integration wiring test dùng context thật?
- [ ] Có negative test thiếu bean và positive test đã đăng ký bean?
- [ ] Có test profile-mismatch?
- [ ] Có test ambiguity nếu nhiều implementations?
- [ ] Không log secrets hoặc toàn bộ properties?
- [ ] Có tránh scanning quá rộng để “chữa” thiếu bean?

## 34. Tổng kết

`NoSuchBeanDefinitionException` báo Spring **không thể tìm thấy một bean phù hợp với truy vấn** theo name/type/injection context. Điều đầu tiên phải kiểm tra là **BeanDefinition registration, component scanning, configuration import, profile và conditions**, không chỉ source class có tồn tại hay không.

Đừng nhầm với `NoUniqueBeanDefinitionException` (có nhiều candidates), `BeanNotOfRequiredTypeException` (sai type của named bean), `ScopeNotActiveException` (scope inactive) hoặc bean creation failure khác.

Với required dependency, **fail-fast và sửa graph/configuration** là lựa chọn tốt. Với dependency thực sự optional, `ObjectProvider` hoặc `Optional` thể hiện contract rõ nhưng vẫn cần kiểm soát ambiguity và runtime failures.

Một regression test hiệu quả phải bao gồm cả **thiếu bean gây lỗi** và **bean được đăng ký đúng thì ứng dụng hoạt động**, tốt nhất bằng `AnnotationConfigApplicationContext` tối thiểu.

## 35. Tài liệu chính thức

- [Spring Framework 6.1 — NoSuchBeanDefinitionException Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/beans/factory/NoSuchBeanDefinitionException.html): lookup semantics, bean name/type getters và subclasses.
- [Spring Framework 6.1 — NoUniqueBeanDefinitionException Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/beans/factory/NoUniqueBeanDefinitionException.html): multiple candidate exception.
- [Spring Framework 6.1 — BeanFactory Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/beans/factory/BeanFactory.html): `getBean` overloads, by-name/by-type lookup.
- [Spring Framework 6.1 — ObjectProvider Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/beans/factory/ObjectProvider.html): optional lookup và uniqueness.
- [Spring Framework Reference — Using @Autowired](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html): required vs optional dependencies.
- [Spring Framework Reference — @Bean Annotation](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html): naming, type declaration, registration.
- [Spring Framework Reference — Component Scanning](https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html): scan scope, stereotype annotations.
- [Spring Framework Reference — Environment Profiles](https://docs.spring.io/spring-framework/reference/core/beans/environment.html): active profiles, missing bean khi profile không match.
- [Spring Framework Reference — Composing Java Configurations](https://docs.spring.io/spring-framework/reference/core/beans/java/composing-configuration-classes.html): @Import, @Conditional và configuration composition.
- [Spring Framework Reference — Bean Overview](https://docs.spring.io/spring-framework/reference/core/beans/definition.html): BeanDefinition registration và IoC.
- [JUnit 5 User Guide](https://docs.junit.org/5.11.1/user-guide/): Jupiter @Test và `assertThrows`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="troubleshooting-003">Nhiều ứng viên với NoUniqueBeanDefinitionException</a>

<details>
<summary>Xem chi tiết</summary>

# Nhiều ứng viên với NoUniqueBeanDefinitionException trong Spring Framework Core Container

## 1. Vấn đề: một điểm tiêm yêu cầu một bean nhưng container tìm thấy nhiều ứng viên

Spring IoC container cho phép nhiều bean cùng implement một interface. Đây là đặc điểm hữu ích để thay thế hạ tầng, lựa chọn chiến lược hoặc triển khai hai adapters khác nhau. Tuy nhiên, một injection point kiểu đơn như `PaymentGateway gateway` thường cần **chính xác một bean**. Nếu hai hay nhiều beans đều phù hợp mà không có tiêu chí xác định duy nhất, Spring không được phép tùy tiện chọn một bean theo thứ tự khai báo.

Ví dụ:

```java
public interface PaymentGateway {
    String code();
}

public final class CardGateway implements PaymentGateway {
    @Override public String code() { return "card"; }
}

public final class BankGateway implements PaymentGateway {
    @Override public String code() { return "bank"; }
}
```

Đây là **những Java type fragments**, cần tách public types ra files riêng nếu đưa vào source package thật.

Nếu trong một context có hai bean `cardGateway` và `bankGateway`:

```java
public final class CheckoutService {
    private final PaymentGateway gateway;

    public CheckoutService(PaymentGateway gateway) {
        this.gateway = gateway;
    }
}
```

Spring sẽ thử resolve constructor argument `PaymentGateway gateway`. Nếu không có `@Qualifier`, một `@Primary` duy nhất, fallback rules hoặc tiêu chí selection thích hợp khác, yêu cầu này **không duy nhất**:

```text
CheckoutService(PaymentGateway gateway)
                         |
                         v
                 type PaymentGateway
                    /          \
                   v            v
              cardGateway   bankGateway
                   \          /
                    \        /
                     ambiguous!
                       |
                       v
          NoUniqueBeanDefinitionException
```

**Kết luận chính:** `NoUniqueBeanDefinitionException` không có nghĩa không tồn tại bean nào; nó nghĩa là Spring nhận được **nhiều matches khi chỉ cần một**. Đó là lỗi *ambiguity*, khác lỗi thiếu bean.

Chương này hướng dẫn phân tích chính xác nguồn ambiguity, đọc exception và exception wrappers, dùng `@Qualifier`/`@Primary`/`@Fallback`, phân biệt injection by type/by name, và kiểm chứng bằng dự án Maven Java 17+ với Spring Framework 6.2, có JUnit tests.

## 2. NoUniqueBeanDefinitionException là ngoại lệ gì?

Tên đầy đủ là `org.springframework.beans.factory.NoUniqueBeanDefinitionException`. Theo Spring Framework Javadoc, nó được ném khi `BeanFactory` được yêu cầu một bean instance nhưng phát hiện nhiều candidates trong trường hợp cần một bean duy nhất.

Điểm dễ bất ngờ: lớp này **extends `NoSuchBeanDefinitionException`**:

```text
BeansException
  |
  +-- FatalBeanException
       |
       +-- NoSuchBeanDefinitionException
              |
              +-- NoUniqueBeanDefinitionException
```

Đây là sơ đồ quan hệ kế thừa; `NoUniqueBeanDefinitionException` có các API chuyên biệt:

```java
int count = exception.getNumberOfBeansFound();
Collection<String> names = exception.getBeanNamesFound();
```

Đây là fragment, biến `exception` phải là `NoUniqueBeanDefinitionException` thực tế. `getBeanNamesFound()` có thể trả null trong trường hợp exception được tạo theo constructor chỉ cung cấp số lượng, nên diagnostic code cần xử lý null thay vì luôn giả định names có sẵn.

Một thông báo thường tương tự:

```text
No qualifying bean of type 'com.example.PaymentGateway' available:
expected single matching bean but found 2: cardGateway,bankGateway
```

Đây chỉ là **ví dụ về cách đọc message**, không phải exact message bắt buộc của mọi Spring version. Tên đầy đủ class, dấu câu, những qualifier/primary conditions, nguyên nhân đóng gói bên ngoài có thể khác nhau.

### Lớp ngoại lệ kế thừa có ý nghĩa gì?

Vì `NoUniqueBeanDefinitionException extends NoSuchBeanDefinitionException`, code catch chung `NoSuchBeanDefinitionException` có thể bắt luôn ambiguity. Do đó trong diagnostic handlers, **catch subtype cụ thể trước**:

```java
try {
    PaymentGateway gateway = context.getBean(PaymentGateway.class);
} catch (NoUniqueBeanDefinitionException ambiguous) {
    // Có nhiều ứng viên, cần phân biệt.
} catch (NoSuchBeanDefinitionException missing) {
    // Không có ứng viên đáp ứng.
}
```

Đây là fragment, cần imports và context hợp lệ. Đảo hai catch blocks có thể gây lỗi compile Java vì catch superclass trước làm subtype catch unreachable.

**Không bao giờ tự động bắt tất cả NoSuchBeanDefinitionException rồi thêm một bean mới**: nếu nguyên nhân là nhiều beans, thêm bean thứ ba còn làm bài toán khó hơn.

## 3. NoUnique khác NoSuch và UnsatisfiedDependency như thế nào?

| Tình huống | Exception/triệu chứng | Cách hiểu |
| --- | --- | --- |
| Không có bean nào theo type/name | `NoSuchBeanDefinitionException` | Thiếu implementation/registration hoặc bị filter |
| Có hai beans phù hợp, cần một | `NoUniqueBeanDefinitionException` | Ambiguity |
| Constructor không được cấp dependency | `UnsatisfiedDependencyException` ở tầng trên | Wrapper cho một lỗi resolve dependency |
| Tạo candidate thất bại do constructor/init | `BeanCreationException` | Bean đã chọn có thể không tạo được |
| Cấu hình trùng bean name nhưng override bị chặn | `BeanDefinitionOverrideException` | Trùng **name**, khác nhiều beans cùng type |
| Hai Spring contexts có cùng bean names | Có thể thành lookup/selection khác theo hierarchy | Kiểm owner và local/ancestor rules |
| Nhiều bean types nhưng injection yêu cầu list | Thường không lỗi NoUnique | Collection injection có chủ đích lấy nhiều |
| `ObjectProvider.getIfUnique()` gặp ambiguity | Có thể trả null | API gọi khác `getObject()` |

Trong quá trình khởi tạo một `CheckoutService`, Spring có thể ném `UnsatisfiedDependencyException` và lồng bên trong là `NoUniqueBeanDefinitionException`. Ngược lại, lệnh `context.getBean(PaymentGateway.class)` trực tiếp thường phơi bày ambiguity exception ngay từ lookup.

### Vì sao một startup log dài không có nghĩa nhiều lỗi độc lập?

Một chain có thể là:

```text
UnsatisfiedDependencyException: Error creating bean checkoutService
  Caused by: NoUniqueBeanDefinitionException:
    expected single matching bean but found 2:
      cardGateway, bankGateway
```

Tầng trên cho biết *điểm tiêm nào không được thỏa*. Tầng dưới cho biết *vì sao*: hai beans cùng type. Chữa bằng cách thêm `@Component` khác hoặc cài thêm starter có thể làm số ứng viên tăng.

Đọc cả **bean đang tạo**, **constructor parameter index/name**, **required type**, **candidate bean names** và any explicit qualifiers.

## 4. Container chọn bean đơn dựa trên tiêu chí nào?

Không nên học thuộc một danh sách ưu tiên cứng áp cho mọi lookup API. Spring có nhiều đường: `getBean(Class)`, autowiring single-valued constructor/field, `ObjectProvider`, named lookup, generic type matching. Tuy nhiên những yếu tố thường tham gia chọn ứng viên là:

1. **Type match:** BeanDefinition/FactoryBean product có type tương thích với injection point.
2. **Autowire candidacy:** `autowireCandidate`, qualifiers, generics, scopes và rules của dependency descriptor.
3. **Explicit qualifier:** injection point khai `@Qualifier("cardGateway")` hoặc custom qualifier.
4. **@Primary:** một candidate được ưu tiên khi resolve single-valued injection.
5. **@Fallback** trong Spring 6.2: candidates mang fallback được xem là thứ yếu khi có non-fallback candidate phù hợp.
6. **Name matching:** tên field/constructor parameter có thể dùng làm yếu tố chọn khi không bị những điều kiện khác override.
7. **Priority/default-candidate rules:** metadata như `@Priority`/default-candidate được xét theo ngữ cảnh.
8. **Kiểu yêu cầu:** yêu cầu `List<PaymentGateway>`/`Map<String,PaymentGateway>` mang nhiều kết quả, không phải một.

Các bước này là **khung tư duy**, không thay thế implementation flow cụ thể của `DefaultListableBeanFactory` ở bản bạn dùng.

### Chỉ có một bean phù hợp thì đơn giản

```java
@Bean PaymentGateway paymentGateway() {
    return new CardGateway();
}
```

Đây là `@Bean` method fragment trong config. Nếu chỉ có candidate này và không có qualifier/scope condition khác, Spring cấp nó cho `PaymentGateway`.

Khi tạo thêm:

```java
@Bean PaymentGateway bankGateway() {
    return new BankGateway();
}
```

cùng type mà không thêm selection rule, trước đây “tự động inject” giờ có thể fail. Đây là một regression rất phổ biến khi modular hóa ứng dụng, bật profile thứ hai hoặc thêm bean để test.

## 5. Cách xử lý bằng @Qualifier: diễn tả sự lựa chọn ngay tại điểm tiêm

Giải pháp rõ nhất khi **consumer thực sự cần một implementation cụ thể**:

```java
public final class CheckoutService {
    private final PaymentGateway gateway;

    public CheckoutService(
            @Qualifier("cardGateway") PaymentGateway gateway) {
        this.gateway = gateway;
    }
}
```

Đây là fragment, cần import `org.springframework.beans.factory.annotation.Qualifier` và type `PaymentGateway`. Spring tìm candidates tương thích type, sau đó dùng qualifier để thu hẹp lựa chọn. Nếu đúng một candidate tương ứng, dependency được resolve.

### @Qualifier không đơn thuần là getBean theo tên

Theo Spring Reference, `@Autowired` về bản chất là **by-type**, sau đó qualifier **lọc tập các matches theo type**. Một string qualifier có thể match bean name như fallback convention, nhưng nó không phải lệnh “lấy bất cứ bean nào có ID này bất kể type”.

Ví dụ:

```java
public CheckoutService(
        @Qualifier("cardGateway") PaymentGateway gateway) {
    // ...
}
```

Nếu bean `cardGateway` không implements `PaymentGateway`, qualifier không cưỡng ép type không tương thích thành một candidate hợp lệ.

### Khai qualifier ở định nghĩa bean

```java
@Bean("cardGateway")
@Qualifier("online")
PaymentGateway cardGateway() {
    return new CardGateway();
}
```

Đây là config fragment. Một consumer có `@Qualifier("online")` có thể match bean này. Nhưng **qualifier không bắt buộc duy nhất**: nhiều beans có thể cùng một qualifier và lúc yêu cầu **một** bean, ambiguity vẫn tồn tại.

```java
@Qualifier("online")
PaymentGateway onlineGateway
```

Fragment trên chỉ minh họa một injection point. Nếu hai implementations đều mang qualifier `online`, Spring vẫn có thể ném NoUnique.

### Bean name vs semantic qualifier

Qualifier mang ý nghĩa semantic như `"fast"`, `"regional"`, `"primaryMarket"` thường bền hơn coupling tới tên bean machine-generated. Nhưng nếu integration có đúng một adapter với identity cụ thể và tên ổn định, explicit bean name là hợp lý.

Nếu đổi `@Bean` method name mà qualifier trỏ vào bean name, test integration phải bắt lỗi; không hy vọng compiler Java phát hiện mọi string-based wiring changes.

## 6. @Primary: khai implementation mặc định ở phía provider

Khi ứng dụng có nhiều implementations, nhưng **đa số injection points** nên nhận một implementation chung, gắn `@Primary` trên đúng một candidate:

```java
@Configuration(proxyBeanMethods = false)
class GatewayConfig {

    @Bean
    PaymentGateway cardGateway() {
        return new CardGateway();
    }

    @Bean
    @Primary
    PaymentGateway bankGateway() {
        return new BankGateway();
    }
}
```

Đây là config fragment, cần imports `@Configuration`, `@Bean`, `@Primary`. Khi Spring resolve `PaymentGateway` by type cho single-valued autowiring, bean `bankGateway` được ưu tiên.

### Các giới hạn của @Primary

- `@Primary` **không xóa** `cardGateway` khỏi container.
- `List<PaymentGateway>`, `Map<String,PaymentGateway>` vẫn có thể chứa cả hai.
- `@Primary` không làm Java object trở thành global singleton mọi contexts.
- Hai beans cùng `@Primary` và đều match có thể lại gây `NoUniqueBeanDefinitionException`, bởi có **hai primary**.
- Explicit qualifier cho một candidate thường thu hẹp tập trước khi primary selection trong dependency resolution.
- Bean được tạo qua `@Bean` method có thể đặt `@Primary` tại method; với component scanning có thể đặt tại class stereotype-managed.

Khi có nhiều gateways mà chỉ Checkout cần card, hãy dùng qualifier tại Checkout. Khi ngân hàng là implementation mặc định cho toàn module, dùng `@Primary` thể hiện provider-level choice. Đừng lạm dụng primary để che policy khác nhau giữa các consumers.

## 7. @Fallback (Spring 6.2): đánh dấu ứng viên thứ yếu

Spring Framework **6.2** bổ sung `@Fallback` cho một số trường hợp cần “lựa chọn tự nhiên” mà không khai primary trực tiếp.

```java
@Bean
@Fallback
PaymentGateway sandboxGateway() {
    return new CardGateway();
}

@Bean
PaymentGateway productionGateway() {
    return new BankGateway();
}
```

Đây là config fragment với import `org.springframework.context.annotation.Fallback`. Khi cả hai match một single-valued dependency mà **chỉ một non-fallback** còn lại, `productionGateway` có thể được chọn. Đây là ví dụ khác với `@Primary` vì implementation được chọn không cần gắn primary.

### Cách diễn giải @Fallback chính xác

Nếu candidates là:
- Một normal + một fallback → normal có ưu thế.
- Một normal + ba fallback → normal có thể là ứng viên duy nhất sau khi loại fallback khỏi lựa chọn ưu tiên.
- Hai normal + một fallback → **vẫn ambiguous** nếu hai normal không được phân biệt.
- Hai fallback, không có normal → vẫn có thể ambiguous.
- @Qualifier chọn một fallback cụ thể → vẫn có thể lấy fallback nếu điểm tiêm cho phép và candidate phù hợp.

`@Fallback` không phải “luôn bỏ bean khỏi tất cả autowiring”. Nó chỉ diễn tả **ưu tiên tương đối** trong quy trình resolve một dependency. `List/Map` của các implementations vẫn có thể thu thập các candidates theo các rules phù hợp.

**Không dùng @Fallback nếu project Spring Framework 6.1 hoặc cũ hơn**: annotation đó là tính năng Spring 6.2. Với Java/Maven demo ở mục 12, Spring 6.2.18 hỗ trợ.

## 8. Khi nào tên parameter/field quyết định kết quả?

Spring có thể dùng tên injection point (field name hoặc constructor parameter name) để match bean name/alias khi chưa có lựa chọn mạnh hơn. Ví dụ:

```java
public final class CheckoutService {
    private final PaymentGateway cardGateway;

    public CheckoutService(PaymentGateway cardGateway) {
        this.cardGateway = cardGateway;
    }
}
```

Nếu hai beans `cardGateway` và `bankGateway` tồn tại, và compiler lưu parameter name, Spring có thể dùng tên `cardGateway` làm lựa chọn trong một số đường dependency resolution. Nhưng hãy hiểu **điều kiện**:

- Với constructor parameters, từ Spring 6.1 cần Java `-parameters` compiler flag để giữ tên tham số khi runtime reflection dùng nó.
- Spring 6.2 có fast shortcut resolution khi name match và không có type/qualifier/primary/fallback conditions override.
- Nếu sửa tên tham số từ `cardGateway` thành `gateway`, kết quả có thể biến từ pass thành NoUnique.
- Nếu một primary khác xuất hiện, primary rules có thể thay đổi lựa chọn.
- Nếu build plugin không giữ parameter names, dựa vào names là một assumption không ổn định.

Vì vậy với API quan trọng, **viết `@Qualifier` rõ ràng tốt hơn phụ thuộc tên biến**. Bạn có thể vẫn dùng bean name-based matching cho convention-heavy framework nhưng phải có integration tests và `-parameters` rõ trong compiler.

### Maven compiler

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <version>3.13.0</version>
  <configuration>
    <release>17</release>
    <parameters>true</parameters>
  </configuration>
</plugin>
```

Đây là POM fragment. `parameters=true` giúp compiler ghi metadata parameter names; nó **không** tự thêm `@Qualifier` và không chữa tất cả ambiguities.

## 9. @Resource và lookup theo tên: trường hợp dùng hợp lý

`@Resource(name="cardGateway")` biểu đạt một mục đích khác: tham chiếu đến **resource/bean được định danh bằng tên** theo JSR-250 style.

```java
@Resource(name = "cardGateway")
private PaymentGateway gateway;
```

Đây là field fragment (Spring 6.x sử dụng `jakarta.annotation.Resource`, cần Jakarta annotations API). `@Resource` thường dùng trên fields/setter methods một tham số, không phải tùy ý đặt vào constructor parameter như `@Qualifier`.

**Phân biệt nhanh:**
- Constructor injection với nhiều implementation: thường dùng `@Qualifier` trên parameter.
- Field/setter injection cần một named resource thực sự: `@Resource(name="...")` có thể phù hợp.
- Gọi code thuần: `context.getBean("cardGateway", PaymentGateway.class)` nếu caller chủ động chọn tên.
- Java đơn giản: inject interface qua constructor và để Spring biết rõ selection bằng configuration.

Đừng chuyển toàn bộ dependencies sang field injection chỉ để tận dụng `@Resource`; điều đó có thể làm required dependencies không còn rõ trên constructor và khó unit test hơn.

## 10. Lấy nhiều bean: List, Set, Map và mảng không phải ambiguity

Khi một service thật sự cần **tất cả gateways**, yêu cầu một collection chứ không yêu cầu một bean:

```java
public final class GatewayRouter {
    private final Map<String, PaymentGateway> gateways;

    public GatewayRouter(Map<String, PaymentGateway> gateways) {
        this.gateways = Map.copyOf(gateways);
    }

    public PaymentGateway byName(String name) {
        PaymentGateway found = gateways.get(name);
        if (found == null) {
            throw new IllegalArgumentException("Unknown gateway");
        }
        return found;
    }
}
```

Đây là complete class fragment, cần imports `Map` và `PaymentGateway`. Spring có thể inject `Map<String,PaymentGateway>` chứa candidates với keys là bean names. Yêu cầu type element vẫn quan trọng.

Các hình thức:
- `List<PaymentGateway>`: thu thập candidates theo type, thường có ordering theo `@Order`/`Ordered`.
- `Set<PaymentGateway>`: tập phù hợp.
- `PaymentGateway[]`: mảng.
- `Map<String,PaymentGateway>`: key là bean name, value là bean.

**Không nhầm `@Order` với `@Primary`.** `@Order` chủ yếu ảnh hưởng thứ tự collection elements hoặc ordered use cases; nó **không bảo đảm** chọn “phần tử đầu tiên” làm single-valued bean. Nếu cần một bean mặc định, khai `@Primary`/qualifier hợp lệ.

### Khi nào collection là anti-pattern?

Nếu nghiệp vụ chỉ cần một gateway để xử lý tất cả payments và bạn chuyển sang `List<PaymentGateway>` rồi lấy `list.get(0)`, bạn đã **che ambiguity bằng selection phụ thuộc thứ tự**. Thứ tự có thể thay đổi khi thêm bean hoặc refactor. Hãy dùng qualifier/primary hoặc một StrategyResolver có policy explicit.

### Semantic route tốt hơn các string magic

Nếu payment method được chọn từ request, hãy thiết kế:
- Một fixed set gateways được Spring inject dưới dạng list/map.
- Mỗi gateway công bố `supports(PaymentMethod)` hoặc một `code()`.
- Router tạo immutable map theo codes, reject duplicates.
- Tại runtime chọn theo method code, validate permissions/feature flags.
- Không tạo BeanDefinition mới mỗi request.

Đây là multi-bean design có chủ ý, khác một dependency cần một bean duy nhất.

## 11. Generic types cũng có thể lọc ứng viên

Spring hỗ trợ generic types làm **implicit autowiring qualifiers** khi metadata generic được giữ đầy đủ.

```java
public interface Store<T> {
    void save(T entity);
}

public record Customer(String id) { }
public record Invoice(String id) { }

public final class CustomerStore implements Store<Customer> {
    public void save(Customer value) { }
}

public final class InvoiceStore implements Store<Invoice> {
    public void save(Invoice value) { }
}
```

Đây là Java type fragments; public declarations cần files riêng. Nếu Spring register cả `Store<Customer>` và `Store<Invoice>` với concrete/generic return types, injection:

```java
public BillingService(Store<Invoice> invoices) {
    this.invoices = invoices;
}
```

có thể được resolve duy nhất mà không cần qualifier string, dù cả hai beans cùng raw interface `Store`.

### Cẩn trọng khi mất generic metadata

Nếu `@Bean` methods trả raw `Store` hoặc `Object`, metadata có thể không đủ để Spring phân biệt T. Khi dùng FactoryBean hoặc programmatic definitions, hãy khai `ResolvableType`/return type chính xác.

Đừng dùng unsafe casts để dập NoUnique, vì lỗi type mismatch sẽ xuất hiện ở runtime. Khi cần hai implementations cùng `Store<Invoice>`, **generic vẫn chưa đủ**; cần qualifier/primary/route policy.

## 12. Ví dụ Maven Java 17+ hoàn chỉnh: tái hiện lỗi và khắc phục

Dự án minh họa:
1. **Ambiguous:** hai gateways cùng interface → `getBean(Gateway.class)` ném `NoUniqueBeanDefinitionException`.
2. **Named lookup:** `getBean("cardGateway", Gateway.class)` lấy đúng card.
3. **Provider uniqueness:** `getIfUnique()` trả null trong trường hợp có hai candidates không ưu tiên.
4. **Qualifier:** consumer có `@Qualifier("cardGateway")` nhận card.
5. **Primary:** consumer không qualifier nhận bank nhờ `@Primary`.
6. **Fallback:** consumer không qualifier nhận bank khi card được đánh `@Fallback`.
7. **Collection:** consumer inject `Map<String,Gateway>` thấy cả hai, minh chứng primary không loại bean khác.

Dùng **Java 17+, Maven 3.9+, Spring Framework 6.2.18** và JUnit Jupiter 5.11.4. Không cần Spring Boot, DB, network hay external services khi chạy code (Maven có thể cần tải dependencies lần đầu).

### 12.1. Cấu trúc thư mục

```text
no-unique-beans-demo/
├── pom.xml
└── src/
    ├── main/java/edu/example/ambiguity/App.java
    └── test/java/edu/example/ambiguity/AppTest.java
```

### 12.2. pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>edu.example</groupId>
  <artifactId>no-unique-beans-demo</artifactId>
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
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.11.4</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.13.0</version>
        <configuration>
          <release>17</release>
          <parameters>true</parameters>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.5.2</version>
      </plugin>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.5.0</version>
        <configuration>
          <mainClass>edu.example.ambiguity.App</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

### 12.3. src/main/java/edu/example/ambiguity/App.java

```java
package edu.example.ambiguity;

import java.util.Arrays;
import java.util.Map;

import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Primary;

public final class App {

    public interface Gateway {
        String code();
    }

    public static final class CardGateway implements Gateway {
        @Override
        public String code() {
            return "card";
        }
    }

    public static final class BankGateway implements Gateway {
        @Override
        public String code() {
            return "bank";
        }
    }

    public static final class QualifiedCheckout {
        private final Gateway gateway;

        public QualifiedCheckout(
                @Qualifier("cardGateway") Gateway gateway) {
            this.gateway = gateway;
        }

        public String selected() {
            return gateway.code();
        }
    }

    public static final class DefaultCheckout {
        private final Gateway gateway;

        public DefaultCheckout(Gateway gateway) {
            this.gateway = gateway;
        }

        public String selected() {
            return gateway.code();
        }
    }

    public static final class GatewayRegistry {
        private final Map<String, Gateway> all;

        public GatewayRegistry(Map<String, Gateway> all) {
            this.all = Map.copyOf(all);
        }

        public Map<String, Gateway> all() {
            return all;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class StandardConfig {

        @Bean("cardGateway")
        public Gateway cardGateway() {
            return new CardGateway();
        }

        @Bean("bankGateway")
        public Gateway bankGateway() {
            return new BankGateway();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class PrimaryConfig {

        @Bean("cardGateway")
        public Gateway cardGateway() {
            return new CardGateway();
        }

        @Bean("bankGateway")
        @Primary
        public Gateway bankGateway() {
            return new BankGateway();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class FallbackConfig {

        @Bean("cardGateway")
        @Fallback
        public Gateway cardGateway() {
            return new CardGateway();
        }

        @Bean("bankGateway")
        public Gateway bankGateway() {
            return new BankGateway();
        }
    }

    public static AnnotationConfigApplicationContext context(
            Class<?>... componentClasses) {
        AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext();
        ctx.register(componentClasses);
        ctx.refresh();
        return ctx;
    }

    public static void main(String[] args) {
        System.out.println("== ambiguous ==");
        try (AnnotationConfigApplicationContext ctx =
                context(StandardConfig.class)) {

            try {
                ctx.getBean(Gateway.class);
                throw new AssertionError(
                        "Expected ambiguity for Gateway");
            } catch (NoUniqueBeanDefinitionException expected) {
                System.out.println(
                        "ambiguous count=" +
                        expected.getNumberOfBeansFound());
            }

            String[] names = ctx.getBeanNamesForType(Gateway.class);
            Arrays.sort(names);
            System.out.println(
                    "candidates=" + String.join(",", names));

            System.out.println("by-name=" +
                    ctx.getBean("cardGateway", Gateway.class).code());

            System.out.println("provider-not-unique=" +
                    (ctx.getBeanProvider(Gateway.class)
                            .getIfUnique() == null));
        }

        System.out.println("== qualifier ==");
        try (AnnotationConfigApplicationContext ctx =
                context(StandardConfig.class, QualifiedCheckout.class)) {
            System.out.println("qualified=" +
                    ctx.getBean(QualifiedCheckout.class).selected());
        }

        System.out.println("== primary ==");
        try (AnnotationConfigApplicationContext ctx =
                context(PrimaryConfig.class, DefaultCheckout.class,
                        GatewayRegistry.class)) {
            System.out.println("primary=" +
                    ctx.getBean(DefaultCheckout.class).selected());
            System.out.println("all-candidates=" +
                    ctx.getBean(GatewayRegistry.class).all().size());
        }

        System.out.println("== fallback ==");
        try (AnnotationConfigApplicationContext ctx =
                context(FallbackConfig.class, DefaultCheckout.class)) {
            System.out.println("non-fallback=" +
                    ctx.getBean(DefaultCheckout.class).selected());
        }

        System.out.println("RESULT PASS");
    }
}
```

### 12.4. Chạy chương trình

```bash
mvn -q compile exec:java
```

**Các dòng do `App.main` in (không bao gồm Maven/Spring logging):**

```text
== ambiguous ==
ambiguous count=2
candidates=bankGateway,cardGateway
by-name=card
provider-not-unique=true
== qualifier ==
qualified=card
== primary ==
primary=bank
all-candidates=2
== fallback ==
non-fallback=bank
RESULT PASS
```

Đây là kết quả dự kiến trong một context không có bean Gateway khác ngoài hai beans được khai. Lệnh Java không kết nối network, DB hoặc file hệ thống khi thực thi business logic. Không assert exact log framework vì logging backend, Maven version và JVM flags có thể ảnh hưởng console.

### 12.5. Từng tình huống chứng minh điều gì?

- `StandardConfig` register hai bean definitions cùng declared return type `Gateway`. `getBean(Gateway.class)` không có tên/qualifier → ambiguity và count 2.
- `getBeanNamesForType(Gateway.class)` trả hai tên; `Arrays.sort` cố định thứ tự output nhằm không dựa internal bean registry ordering.
- `getBean("cardGateway",Gateway.class)` là lookup theo tên và type, nên có kết quả duy nhất.
- `getBeanProvider(Gateway.class).getIfUnique()` không ném trong trường hợp không có unique candidate mà trả null, khác `getObject()`.
- `QualifiedCheckout` dùng constructor parameter `@Qualifier("cardGateway")`; explicit selection không phụ thuộc tên tham số.
- `PrimaryConfig` đánh dấu bank là primary, nên `DefaultCheckout` by type nhận bank.
- `GatewayRegistry` inject map gồm cả hai gateways; primary chỉ là **ưu tiên với single-valued dependency**, không loại card khỏi map.
- `FallbackConfig` đánh dấu card là fallback, còn bank normal; injection single-valued nhận bank nhờ Spring 6.2 fallback rule.
- Các contexts được tạo riêng và đóng bằng try-with-resources, tránh ảnh hưởng BeanDefinitions của kịch bản này tới kịch bản khác.

### 12.6. Vì sao dùng nhiều config classes?

Nếu chỉ có một context chứa StandardConfig, PrimaryConfig và FallbackConfig cùng lúc, các `@Bean` definitions trùng tên và thêm candidate làm kết quả khó hiểu. Demo cố tình tách scenarios để:
- Khả năng tái hiện exception đáng tin.
- Không phụ thuộc overriding behavior.
- Không có component scanning phát hiện thêm beans ngoài ý muốn.
- Test từng selection rule riêng.
- Có thể thêm trường hợp “hai @Primary” ở một context khác mà không phá demo chính.

## 13. JUnit 5: kiểm exception và các cách resolution

Tạo file `src/test/java/edu/example/ambiguity/AppTest.java`:

```java
package edu.example.ambiguity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

final class AppTest {

    @Test
    void detectsTwoBeansForOneType() {
        try (AnnotationConfigApplicationContext ctx =
                App.context(App.StandardConfig.class)) {

            NoUniqueBeanDefinitionException ex =
                    assertThrows(NoUniqueBeanDefinitionException.class,
                            () -> ctx.getBean(App.Gateway.class));

            assertEquals(2, ex.getNumberOfBeansFound());
            assertEquals(Set.of("bankGateway", "cardGateway"),
                    Set.of(ctx.getBeanNamesForType(
                            App.Gateway.class)));
        }
    }

    @Test
    void lookupByNameAvoidsTypeAmbiguity() {
        try (AnnotationConfigApplicationContext ctx =
                App.context(App.StandardConfig.class)) {

            assertEquals("card",
                    ctx.getBean("cardGateway",
                            App.Gateway.class).code());
            assertNull(ctx.getBeanProvider(
                    App.Gateway.class).getIfUnique());
        }
    }

    @Test
    void qualifierSelectsSpecificCandidate() {
        try (AnnotationConfigApplicationContext ctx =
                App.context(App.StandardConfig.class,
                        App.QualifiedCheckout.class)) {

            assertEquals("card",
                    ctx.getBean(App.QualifiedCheckout.class).selected());
        }
    }

    @Test
    void primarySelectsOneWithoutRemovingOtherCandidates() {
        try (AnnotationConfigApplicationContext ctx =
                App.context(App.PrimaryConfig.class,
                        App.DefaultCheckout.class,
                        App.GatewayRegistry.class)) {

            assertEquals("bank",
                    ctx.getBean(App.DefaultCheckout.class).selected());

            Map<String, App.Gateway> all =
                    ctx.getBean(App.GatewayRegistry.class).all();

            assertEquals(Set.of("bankGateway", "cardGateway"),
                    all.keySet());
            assertEquals("card", all.get("cardGateway").code());
            assertEquals("bank", all.get("bankGateway").code());
        }
    }

    @Test
    void fallbackIsNotChosenWhenNormalCandidateExists() {
        try (AnnotationConfigApplicationContext ctx =
                App.context(App.FallbackConfig.class,
                        App.DefaultCheckout.class)) {

            assertEquals("bank",
                    ctx.getBean(App.DefaultCheckout.class).selected());
        }
    }
}
```

**Lệnh kiểm thử:**

```bash
mvn -q test
```

Kỳ vọng Surefire/JUnit ghi **5 tests, 0 failures, 0 errors, 0 skipped** nếu POM và source đúng. Vì `-q` giảm log Maven, không cam kết một block stdout cụ thể. Test cases mở từng context riêng, không cần `@SpringBootTest` hoặc `@SpringJUnitConfig` vì mục đích là container integration ở kích thước nhỏ.

### Cảnh báo: Set.of(array) trong Java

Trong code trên, `Set.of(ctx.getBeanNamesForType(App.Gateway.class))` nhận varargs array nên tạo Set của các phần tử trong array với Java generic inference phù hợp, nhưng có một caveat: nếu names chứa duplicates (không kỳ vọng từ registry) `Set.of` sẽ ném IllegalArgumentException. Nếu cần test cung cấp diagnostics rõ hơn, dùng `new HashSet<>(Arrays.asList(...))`. Với hai names đã rõ và unique trong demo, `Set.of(...)` là phù hợp.

### Tại sao không chỉ assert context không throw?

`StandardConfig` có hai beans cùng type nhưng bản thân context **có thể refresh thành công** nếu không có bean nào yêu cầu một `Gateway` đơn. Exception chỉ xảy ra khi lookup by type hoặc tạo consumer bắt buộc phải chọn một. Vì vậy test phải **thực sự gọi `getBean(Gateway.class)`** hoặc register `DefaultCheckout` để kích hoạt dependency resolution.

Nếu chỉ viết `new AnnotationConfigApplicationContext(StandardConfig.class)` rồi assert context.isActive(), bạn có thể bỏ sót hoàn toàn ambiguity.

## 14. Chẩn đoán ambiguity từ startup stacktrace

Một trình tự hiệu quả:

1. Tìm outer `UnsatisfiedDependencyException` hoặc `BeanCreationException`.
2. Xác định bean đang tạo: ví dụ `checkoutService`.
3. Xác định điểm tiêm: constructor argument 0, setter parameter hoặc field `PaymentGateway`.
4. Tìm `NoUniqueBeanDefinitionException` trong `cause chain`.
5. Ghi candidate names: `cardGateway`, `bankGateway`.
6. Tìm nguồn của từng bean definition: `@Component`, `@Bean`, `@Import`, module auto-config, tests, profiles, FactoryBean product.
7. Quyết định nghiệp vụ: thực sự cần **một default**, **một named implementation**, hay **tập tất cả implementations**?
8. Chọn correction tương ứng: primary, qualifier, fallback, map/list hoặc loại registration sai.
9. Thêm integration test cho expected selection và assertion các candidate names/types.
10. Chạy với profiles/conditional configurations giống deployment thật.

### Một helper chẩn đoán an toàn

```java
static void printAmbiguity(NoUniqueBeanDefinitionException error) {
    System.out.println("requiredType=" + error.getBeanType());
    System.out.println("count=" + error.getNumberOfBeansFound());
    if (error.getBeanNamesFound() != null) {
        System.out.println("names=" + error.getBeanNamesFound());
    }
}
```

Đây là method fragment. Không dump toàn bộ Spring environment hoặc bean definitions nếu property values có secrets. Bean names và types thường ít nhạy cảm hơn nhưng vẫn nên tuân theo logging policy của ứng dụng.

### Nếu outer cause chain không chứa subtype rõ?

Một số errors được wrap theo container-specific path; có thể inspect `getRelatedCauses()` của `BeanCreationException` và logs với debug settings phù hợp. Không mặc định bất cứ `UnsatisfiedDependencyException` nào là NoUnique. Missing bean hoặc circular constructor dependency cũng có wrapper tương tự.

## 15. Kiểm BeanDefinitions đúng phạm vi mà không phụ thuộc thứ tự

Những API hữu ích:

```java
String[] names = context.getBeanNamesForType(PaymentGateway.class);
PaymentGateway card =
        context.getBean("cardGateway", PaymentGateway.class);

boolean hasCard =
        context.containsBeanDefinition("cardGateway");
```

Đây là fragments, cần context và type.

- `getBeanNamesForType(PaymentGateway.class)` hữu ích để liệt kê beans theo type.
- `getBeansOfType(PaymentGateway.class)` tạo/tra instances theo matching rules, có thể khởi tạo beans sớm hoặc gặp bean creation errors; không gọi tùy tiện trong BeanFactoryPostProcessor khi bootstrap chưa xong.
- `containsBeanDefinition(...)` hỏi **local definition**; không đồng nghĩa mọi bean có thể lookup theo tên/parent.
- `containsBean(...)` có semantics lookup rộng hơn, có thể liên quan ancestor context.
- `getBean(name, type)` để xác nhận một named candidate thực sự có type tương thích.

### Không sửa production runtime để “in ra hết beans” mà không cân nhắc side effects

Một diagnostic endpoint liệt kê names/types bằng `getBeansOfType` có thể kích hoạt initialization của lazy beans hoặc FactoryBean products tùy cách gọi. Trong production, cân nhắc Actuator beans endpoint với quyền truy cập bảo mật, hoặc chỉ đọc BeanDefinition metadata khi đủ.

### Dùng Spring Boot diagnostics nếu ứng dụng là Boot

Nếu dự án là Spring Boot, startup failure analysis có thể liệt kê candidates và gợi ý `@Primary` hoặc qualifier. Boot Actuator `/actuator/beans` có thể hỗ trợ xem topology nhưng phải bảo vệ endpoint theo security policy. Những công cụ này **bổ sung** chứ không thay việc hiểu dependency graph. Trong demo chỉ dùng Spring Framework core context, không cần Boot.

## 16. Những nguyên nhân phát sinh nhiều candidates ngoài dự kiến

### Trùng component scan và @Bean

Một implementation đã có `@Component`, sau đó người phát triển thêm một `@Bean` mới trả cùng type. Hai bean names khác nhau → cùng interface có hai candidates, dù developer nghĩ “đó vẫn là một class”.

```java
@Component
public class HttpGateway implements PaymentGateway { /* ... */ }

@Configuration
class GatewayConfig {
    @Bean
    PaymentGateway selectedGateway() {
        return new HttpGateway();
    }
}
```

Đây là fragments, không phải code cần thêm vào demo. Với annotation scanning có `httpGateway` và `@Bean` có `selectedGateway`, Spring có thể có **hai bean instances**. Đăng ký một lần, hoặc dùng một definition tham chiếu/alias khi thật sự muốn cùng identity.

### Thêm test bean nhưng không thay bean cũ

Một test định nghĩa fake bean dưới tên `fakeGateway` trong khi production bean vẫn có mặt; context có hai implementations. Đừng hy vọng `@TestConfiguration`/test registration luôn “thay” bean production — một số trường hợp là **thêm candidate**. Nếu muốn override đúng bean, dùng test bean override mechanism hợp lệ ở Spring 6.2 (ví dụ `@MockitoBean`/`@TestBean` qua Spring TestContext, theo version và constraints) hoặc cấu hình profile/condition rõ.

### Profile hoặc conditional thay đổi

Các beans có `@Profile("dev")` và `@Profile("prod")` có thể cùng xuất hiện nếu cả hai profiles được bật. `@Conditional` cũng có thể tạo candidate mới khi property kích hoạt. Hãy kiểm effective profiles/conditions thay vì đoán bean đã bị loại.

### Auto-configuration/module integration

Một starter mới có thể register implementation mặc định cùng type với implementation custom của bạn. Một số auto-configurations dùng `@ConditionalOnMissingBean` để giảm collisions, nhưng không phải mọi thư viện đều làm vậy hoặc làm theo cùng type/name. Hãy đọc auto-config conditions/report đúng deployment.

### FactoryBean và product types

Một `FactoryBean<PaymentGateway>` có thể xuất hiện như một candidate type Gateway cho lookup product. `getBean("factoryName")` thường trả product, còn `getBean("&factoryName")` trả factory. Nếu count không khớp số classes bạn tìm thấy bằng @Component, hãy kiểm bean factories/products và type prediction.

### Parent-child ApplicationContexts

Bean nằm trong parent factory có thể được lookup từ child theo contract; local bean names có thể shadow ancestor names. Different lookup APIs xử lý hierarchy khác nhau. Khi chẩn đoán, phải hỏi **candidate thực đến từ context nào**, không chỉ xem definitions của một registry local.

## 17. @Primary, @Qualifier, @Fallback và @Order: chọn đúng công cụ

| Yêu cầu nghiệp vụ | Công cụ phù hợp | Không nên làm |
| --- | --- | --- |
| Một implementation mặc định cho phần lớn consumers | `@Primary` | Đánh primary cho cả hai |
| Consumer A luôn dùng bean chuyên biệt | Constructor `@Qualifier` | Dựa thứ tự scan |
| Một implementation chỉ là fallback | `@Fallback` (Spring 6.2+) | Bật fallback cho tất cả |
| Runtime phải chọn theo request code | `Map<String,Gateway>`/router | Lấy bean đầu tiên |
| Cần tất cả gateways theo thứ tự | `List<Gateway>` + `@Order` | Tin @Order chọn single bean |
| Cần một bean theo tên từ API | `getBean(name, type)` | Lấy by type rồi cast |
| Cần group semantic | Custom `@Qualifier` | Chỉ dùng bean name random |
| Cần phân biệt `Store<Customer>` và `Store<Invoice>` | Generic qualifier | Raw `Store` casting |
| Sửa một test context có 2 beans | Override đúng bean hoặc config hẹp | Thêm fake rồi bật global overriding mù |
| Không có candidate nào | Đăng ký/scan đúng bean | Thêm @Primary vào bean không tồn tại |

### @Order không giải ambiguity về single bean

```java
@Order(1)
@Component
class CardGateway implements PaymentGateway { /* ... */ }

@Order(2)
@Component
class BankGateway implements PaymentGateway { /* ... */ }
```

Đây là fragments. Một `List<PaymentGateway>` có thể được Spring ordered theo metadata. Nhưng một constructor `CheckoutService(PaymentGateway gateway)` không có nghĩa “lấy @Order(1)”. Dùng primary/qualifier thay vì dựa @Order.

### @Priority có vai trò khác

`jakarta.annotation.Priority` là metadata khác `@Order` và có thể được Spring xét trong một số single-candidate resolution paths. Tuy nhiên không nên dùng nó để thay toàn bộ explicit qualifier khi một consumer cần đúng adapter cụ thể. Khi ưu tiên global có thật, hãy kiểm trong integration test với version Spring đang dùng.

### @Fallback không giống Optional

`@Fallback` không có nghĩa candidate vắng mặt và cũng không có nghĩa fallback chỉ xuất hiện khi thiếu bean. Bean vẫn trong container. `Optional<Gateway>` và `ObjectProvider<Gateway>` mô tả optional/lazy lookup, còn `@Fallback` mô tả priority của candidate.

## 18. ObjectProvider và trường hợp nhiều ứng viên

`ObjectProvider<T>` cho phép tách thời điểm lookup khỏi constructor:

```java
public final class ReportService {
    private final ObjectProvider<PaymentGateway> gateways;

    public ReportService(ObjectProvider<PaymentGateway> gateways) {
        this.gateways = gateways;
    }

    public PaymentGateway selected() {
        return gateways.getObject();
    }
}
```

Đây là class fragment cần import `ObjectProvider` và `PaymentGateway`.

Các APIs:
- `getObject()` cần resolve bean phù hợp; ambiguity có thể ném `NoUniqueBeanDefinitionException`.
- `getIfAvailable()` phù hợp khi bean có thể vắng mặt; nó không tự biến nhiều candidates thành một lựa chọn rõ.
- `getIfUnique()` có thể trả null nếu không có unique candidate.
- `stream()`/`orderedStream()` phù hợp khi muốn duyệt tất cả candidates.

**Đừng “sửa lỗi” bằng cách thay type T thành `ObjectProvider<T>` rồi gọi getObject ngay trong constructor**; ambiguity chỉ bị dời thời điểm. Nó cũng có thể chuyển startup failure thành production request failure nếu lookup diễn ra muộn.

Trong demo, `getIfUnique()==null` với hai candidates không primary/fallback giúp chứng minh sự khác nhau với `getObject()`, nhưng không phải khuyến nghị production rằng null là default gateway.

## 19. BeanDefinitionRegistry và cấu hình lập trình

Nếu chương trình tạo definitions bằng mã:

```java
RootBeanDefinition card = new RootBeanDefinition(CardGateway.class);
RootBeanDefinition bank = new RootBeanDefinition(BankGateway.class);

registry.registerBeanDefinition("cardGateway", card);
registry.registerBeanDefinition("bankGateway", bank);
```

Đây là fragment cần `BeanDefinitionRegistry`, `RootBeanDefinition` và classes. Registry có hai definitions type PaymentGateway, nên single by-type lookup vẫn ambiguous nếu không cấu hình thêm.

Đặt primary ở metadata:

```java
bank.setPrimary(true);
```

hoặc đánh dấu bean không là candidate cho autowiring:

```java
card.setAutowireCandidate(false);
```

Đây là hai methods fragments, không phải cùng một ý nghĩa:
- `setPrimary(true)`: bean vẫn là candidate và được ưu tiên trong single-valued injection.
- `setAutowireCandidate(false)`: hạn chế tham gia autowiring, nhưng explicit name-based getBean vẫn có thể lookup bean theo name; nó không delete definition.

Đừng tắt autowire candidacy chỉ để giấu một duplicate bean do configuration sai nếu application còn cần collection injection ở nơi khác.

### DefaultListableBeanFactory không phải AnnotationConfigApplicationContext

Một `DefaultListableBeanFactory` thuần có thể quản lý BeanDefinitions và resolve by type, nhưng không tự quét `@Component` hoặc xử lý mọi annotation nếu bạn chưa đăng ký infrastructure. `AnnotationConfigApplicationContext` trong demo cung cấp annotation configuration processing, cho phép `@Primary`, `@Qualifier` và `@Fallback` phát huy đúng trong registered config.

Khi debug library dùng raw BeanFactory, xác nhận setup BeanFactoryPostProcessors/BeanPostProcessors thay vì đổ lỗi ngay cho annotations.

## 20. Test checklist và những assertions quan trọng

Một bộ kiểm thử nhỏ nên chứng minh **cả lỗi ban đầu và fix**:

1. **Red test:** có hai candidates, `getBean(Gateway.class)` ném `NoUniqueBeanDefinitionException`.
2. **Số candidates:** count đúng 2, names tương ứng config.
3. **By-name lookup:** `getBean("cardGateway", Gateway.class)` lấy đúng card.
4. **Qualifier:** Checkout chỉ lấy card dù bank vẫn tồn tại.
5. **Primary:** Checkout mặc định lấy bank.
6. **Fallback:** Spring 6.2 chọn unique non-fallback bank.
7. **Collection:** Map vẫn chứa bank và card sau khi primary được áp.
8. **Negative:** khi hai beans cùng primary, ambiguity vẫn có thể xảy ra; thêm dedicated test nếu policy đòi phòng vệ.
9. **Profile regression:** các combinations thực tế không phát sinh duplicate ngoài dự kiến.
10. **Test override:** fake bean cho tests không vô tình cùng tồn tại với real bean.
11. **FactoryBean:** nếu dùng factory-based adapters, kiểm product type.
12. **Generic narrowing:** nếu dùng `Store<T>`, kiểm return type metadata.
13. **Parameter names:** test build Maven với `-parameters` hoặc dùng explicit qualifier để loại dependency vào compiler flag.
14. **Context hierarchy:** nếu ứng dụng có parent/child, kiểm lookup đúng context owner.

### Không nên test bằng cách assert full exception message

Thông báo của `NoUniqueBeanDefinitionException` có thể thay đổi formatting qua các Spring patch versions. Trong JUnit, hãy assert type, `getNumberOfBeansFound()`, candidate names và behavior. Nếu project cần audit log, log names/type theo policy; không dùng string comparison gồm cả package/class path từ console.

### Mockito không giải cấu hình container nếu chỉ mock SUT

Unit test `new CheckoutService(fakeGateway)` là tuyệt vời để test checkout business logic nhưng **không** test Spring chọn bean đúng. Một context integration test như `AppTest` phải tồn tại để bắt NoUnique do registrar/profile/scan. Không cần full Spring Boot startup nếu chỉ kiểm một `@Configuration` nhỏ.

## 21. Lỗi thường gặp và cách khắc phục

| Hiện tượng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Expected single matching bean but found 2 | Hai implementations, single injection | @Qualifier hoặc @Primary |
| Đã thêm @Qualifier mà vẫn NoUnique | Nhiều beans match cùng semantic qualifier | Qualifier cụ thể hơn hoặc sửa definitions |
| Đã thêm @Primary mà vẫn lỗi | Hai primary beans hoặc qualifier lọc khác | Kiểm toàn bộ candidates và metadata |
| @Fallback không hoạt động | Spring version trước 6.2 hoặc còn nhiều normal beans | Upgrade hợp lệ/giảm normal candidates |
| Constructor tên đúng bean nhưng vẫn lỗi | Thiếu `-parameters` hoặc primary/qualifier khác chi phối | @Qualifier explicit, compiler metadata |
| @Order(1) nhưng vẫn NoUnique | @Order chủ yếu sắp list | @Primary/qualifier |
| Đổi single bean sang list rồi lấy phần tử đầu | Selection mơ hồ, phụ thuộc ordering | Router theo business key |
| Bean name trùng nhưng không NoUnique | Bean overriding xảy ra | Kiểm override policy, không nhầm same name với same type |
| Không thấy bean trong scan nhưng type count 2 | Bean tạo bởi @Bean/FactoryBean/auto-config | Kiểm factory and config contributors |
| Test có fake và real candidate | Test thêm bean thay vì override | Test bean override/config isolate |
| Chỉ lỗi ở production | Profiles/conditionals differ | In effective profiles/candidates an toàn |
| Sau thêm starter bắt đầu ambiguous | Auto-config register implementation khác | Exclude/configure/condition proper |
| `ObjectProvider.getObject()` vẫn throw | Deferred ambiguity, không giải | Select qualified/unique provider |
| `getIfUnique()` trả null | Có nhiều matches hoặc không có unique | Check candidates, don't blindly dereference |
| Context refresh PASS, runtime lookup FAIL | Không ai inject by type khi startup | Test actual lookup |
| Inject Map thiếu bean | AutowireCandidate/qualifier/generic mismatch | Kiểm type/filter metadata |
| Generic Store injection ambiguous | Raw return types hoặc cùng T có 2 beans | Preserve generic metadata + qualifier |
| @Resource không inject constructor | Annotation không dành cho constructor argument | Constructor @Qualifier |
| BeanFactory thuần không áp annotation như context | Thiếu annotation processors/config | Dùng ApplicationContext hoặc configure processors |
| Lỗi trong FactoryBean product | Product getObjectType/type prediction | Khai type chính xác |
| Parent bean không hiện tại local count | Hierarchical lookup khác local definitions | Kiểm cả parent/child owner |

### Quy trình 60 giây khi gặp lỗi

- Đọc **tên bean đang được tạo và type injection point**.
- Truy `NoUniqueBeanDefinitionException` trong exception cause chain.
- Lấy **tập candidate bean names**, không đoán implementation từ class bạn vừa sửa.
- Chọn requirement: **một explicit**, **một default**, hay **nhiều**.
- Áp **@Qualifier**, **@Primary**, **@Fallback** hoặc **Map/List** tương ứng.
- Chạy integration test nhỏ có thể tái hiện trước khi build full application.

## 22. Câu hỏi tự đánh giá và đáp án

1. `NoUniqueBeanDefinitionException` xảy ra khi nào?
2. Nó kế thừa class nào trực tiếp?
3. Nó khác `NoSuchBeanDefinitionException` về bản chất thế nào?
4. Vì sao một `UnsatisfiedDependencyException` có thể chứa NoUnique ở cause?
5. `getNumberOfBeansFound()` trả gì?
6. `getBeanNamesFound()` có luôn non-null không?
7. Vì sao `getBean(PaymentGateway.class)` ambiguous khi có card và bank?
8. Tại sao `getBean("cardGateway",PaymentGateway.class)` có thể thành công?
9. `@Qualifier("cardGateway")` có được xét sau type compatibility không?
10. Một qualifier có bắt buộc chỉ nằm trên một bean không?
11. `@Primary` có làm tất cả bean khác biến mất không?
12. Hai primary candidates có tạo ambiguity không?
13. `@Fallback` lần đầu có trong nhánh Spring nào?
14. Một normal và hai fallback beans có thể chọn normal không?
15. Hai normal, một fallback có tự duy nhất không?
16. `@Order` có chọn single bean không?
17. Field/parameter name có thể giúp Spring match bean name không?
18. Constructor parameter name cần compiler flag nào?
19. `@Resource` có dùng trực tiếp trên constructor parameter như Qualifier không?
20. `List<PaymentGateway>` có lấy nhiều implementations không?
21. `Map<String,PaymentGateway>` keys là gì?
22. Khi nào inject List rồi lấy get(0) là anti-pattern?
23. Generic Store<Customer> khác Store<Invoice> có giúp resolve không?
24. Nếu factory method trả raw Store thay vì typed Store<T>, có rủi ro gì?
25. `ObjectProvider.getIfUnique()` làm gì khi không có unique candidate?
26. `getIfAvailable()` có bảo đảm tự chọn một trong hai không?
27. Vì sao `setAutowireCandidate(false)` khác `setPrimary(true)`?
28. Một @Component cùng @Bean cùng type có thể sinh NoUnique không?
29. Thêm test fake có thể làm context startup lỗi vì sao?
30. FactoryBean product có tham gia type lookup không?
31. Vì sao full exception message là assertion dễ gãy?
32. Vì sao pure unit test không phát hiện Spring selection?
33. Trong demo, ambiguous count là bao nhiêu?
34. Trong demo, qualifier chọn implementation nào?
35. Trong demo, primary và non-fallback chọn implementation nào?
36. Trong demo, all-candidates còn bao nhiêu?
37. Khi thật sự cần chọn theo request, nên dùng cấu trúc nào?
38. Khi làm migration từ Spring 6.1 sang 6.2, @Fallback có điểm gì đáng chú ý?
39. @Primary vs @Qualifier giải quyết ở provider hay consumer?
40. Test nào cần duy trì để tránh duplicate candidate regression?

**Đáp án ngắn:** (1) Một lookup/injection cần một nhưng có nhiều matching candidates. (2) `NoSuchBeanDefinitionException`. (3) Không có vs có nhiều. (4) Bean creation không resolve được constructor argument. (5) Số ứng viên. (6) Không nhất thiết. (7) Cùng type và chưa có selection rule. (8) Lookup explicit by name. (9) Có. (10) Không. (11) Không. (12) Có thể. (13) Spring Framework 6.2. (14) Có. (15) Không. (16) Không để chọn một mặc định. (17) Có, theo conditions. (18) `-parameters`. (19) Không. (20) Có. (21) Bean names. (22) Khi dùng thứ tự ngẫu nhiên làm business selection. (23) Có nếu metadata giữ đầy đủ. (24) Mất generic narrowing. (25) Trả null trong trường hợp ambiguity/no unique phù hợp. (26) Không. (27) Candidate exclusion vs priority. (28) Có. (29) Cùng type thêm candidate. (30) Có theo product type matching. (31) Formatting/version-dependent. (32) Không chạy DI container. (33) 2. (34) Card. (35) Bank. (36) 2. (37) Registry/router với explicit key và inject collection. (38) Có annotation fallback mới, cần đủ version. (39) Primary provider default, qualifier consumer explicit choice. (40) Context integration tests với type count, qualifier/default/fallback và profiles.

## 23. Checklist triển khai production

- [ ] Phân biệt NoUnique với missing bean và bean creation errors.
- [ ] Đọc bean names/type từ exception; không sửa mù một class.
- [ ] Phân biệt duplicate bean names với multiple bean names same type.
- [ ] Biết selection rules tùy injection path và BeanFactory version.
- [ ] Nếu consumer phải dùng implementation cụ thể, thêm constructor @Qualifier.
- [ ] Nếu module có một default, đánh một bean @Primary.
- [ ] Nếu dùng Spring 6.2 và có secondary implementations, cân nhắc @Fallback.
- [ ] Không đánh @Primary lên mọi candidate.
- [ ] Không đánh cùng @Qualifier semantic lên nhiều beans rồi trông chờ uniqueness.
- [ ] Không kỳ vọng @Order chọn single-valued dependency.
- [ ] Nếu muốn tất cả implementations, inject List/Set/Map đúng generic.
- [ ] Không phụ thuộc list.get(0) cho business routing trừ khi ordering là contract thật.
- [ ] Không ẩn duplicate bean bằng setAutowireCandidate(false) thiếu lý do.
- [ ] Kiểm method @Bean return types/generic metadata chính xác.
- [ ] Không dùng getBean(Class) nếu nghiệp vụ cần named selection.
- [ ] Không dùng getIfUnique()==null như default gateway silently.
- [ ] Nếu dựa parameter-name matching, compiler bật -parameters và có integration test.
- [ ] Ưu tiên explicit qualifier hơn magic parameter names ở critical code.
- [ ] Hiểu @Resource theo name khác constructor @Qualifier.
- [ ] Không nhầm annotation của bare factory với ApplicationContext infrastructure.
- [ ] Kiểm profiles/conditions/starter auto-config khi ứng viên tăng bất ngờ.
- [ ] Kiểm duplicate test beans và override policies.
- [ ] Nếu có parent-child contexts, kiểm candidate ownership và shadowing.
- [ ] Không gọi getBeansOfType bừa trong early BeanFactoryPostProcessor.
- [ ] Tránh dump bean metadata/secret trong logs.
- [ ] Có red test tái hiện NoUnique trước khi chốt fix.
- [ ] Có context integration tests cho qualifier, primary, fallback và multi-bean collection.
- [ ] Test behavior không so full framework exception message.
- [ ] Document business intent của việc chọn một implementation trong code/config.

## 24. Tài liệu Spring chính thức

- Spring Framework 6.2 Reference — @Primary và @Fallback: https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-primary.html
- Spring Framework 6.2 Reference — Autowired Qualifiers: https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-qualifiers.html
- Spring Framework 6.2 Reference — Generics as Autowiring Qualifiers: https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/generics-as-qualifiers.html
- Spring Framework 6.2 Reference — Using @Autowired: https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html
- Spring Framework 6.2 Reference — Injection with @Resource: https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/resource.html
- Spring Framework 6.2 Javadoc — NoUniqueBeanDefinitionException: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/NoUniqueBeanDefinitionException.html
- Spring Framework 6.2 Javadoc — NoSuchBeanDefinitionException: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/NoSuchBeanDefinitionException.html
- Spring Framework 6.2 Javadoc — @Fallback: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Fallback.html
- Spring Framework 6.2 Javadoc — @Primary: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Primary.html
- Spring Framework 6.2 Javadoc — @Qualifier: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/annotation/Qualifier.html
- Spring Framework 6.2 Javadoc — ObjectProvider: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/ObjectProvider.html
- Spring Framework 6.2 Javadoc — DefaultListableBeanFactory: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/support/DefaultListableBeanFactory.html
- JUnit 5 — User Guide: https://junit.org/junit5/docs/current/user-guide/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="troubleshooting-004">Phụ thuộc không thỏa mãn với UnsatisfiedDependencyException</a>

<details>
<summary>Xem chi tiết</summary>

# Phụ thuộc không thỏa mãn với UnsatisfiedDependencyException

## 1. Bài toán và mục tiêu

Khi Spring Framework khởi tạo một bean, constructor, factory method, field hoặc setter của bean đó có thể yêu cầu một dependency. Nếu container không cung cấp được dependency hợp lệ, quá trình tạo bean thất bại. Một ngoại lệ đặc trưng là **`org.springframework.beans.factory.UnsatisfiedDependencyException`**.

Ví dụ lỗi thường thấy trong log:

```text
Error creating bean with name 'checkoutService':
Unsatisfied dependency expressed through constructor parameter 0:
No qualifying bean of type '...PaymentPort' available
```

Đây là **log minh họa**, không phải nguyên văn bắt buộc của mọi Spring version.

Điểm quan trọng: **“phụ thuộc không thỏa mãn” không chỉ đồng nghĩa với “chưa khai báo @Bean”**. Nó còn có thể xuất phát từ:
- Bean cần thiết không được đăng ký hoặc chưa nằm trong ApplicationContext đang tra cứu.
- Bean đã có nhưng bị `@Profile` / `@Conditional` loại khỏi cấu hình.
- Có hai hay nhiều candidates cùng type mà không có rule chọn unique bean.
- Injection point yêu cầu concrete type, còn exposed object là JDK proxy chỉ implements interface.
- `@Qualifier`, bean name, qualifier metadata hoặc autowire candidate rules không khớp.
- Generic type `Repository<Order>` khác `Repository<Customer>`.
- Bean được tìm thấy nhưng bản thân nó không thể tạo vì dependency ở tầng sâu hơn.
- Constructor/factory method arguments không thể resolve hoặc thiếu các simple properties theo dependency checking.
- Config class/package scanning, parent–child context scope hoặc register definitions sai giai đoạn.

Bài hướng dẫn đọc nguyên nhân gốc, sửa đúng cấu hình và **viết test tái hiện** thay vì chỉ đổi annotation theo phỏng đoán. Ví dụ dùng Java 17, Spring Framework 6.2.18, Gradle và JUnit 5.

## 2. UnsatisfiedDependencyException nằm ở đâu trong hệ ngoại lệ?

```text
java.lang.RuntimeException
         |
         v
org.springframework.core.NestedRuntimeException
         |
         v
org.springframework.beans.BeansException
         |
         v
org.springframework.beans.factory.BeanCreationException
         |
         v
org.springframework.beans.factory.UnsatisfiedDependencyException
```

Đây là sơ đồ quan hệ kế thừa khái niệm trong Spring Framework 6.2.

`UnsatisfiedDependencyException` là một biến thể của **`BeanCreationException`**. Bởi vậy một `catch (BeanCreationException ex)` có thể bắt được nó, nhưng nếu chỉ in tên lớp ngoại lệ ngoài cùng, ta vẫn chưa có toàn bộ lời giải.

Các API chẩn đoán hữu ích:

| API | Giá trị |
| --- | --- |
| `getBeanName()` | Tên bean mà Spring đang cố tạo ở node lỗi đó, có thể null tùy nguồn tạo exception |
| `getResourceDescription()` | Nguồn bean definition, nếu framework có metadata |
| `getInjectionPoint()` | `InjectionPoint` liên quan tới field hoặc method/constructor parameter, **có thể null** |
| `getCause()` | Lỗi dẫn đến failure trong quá trình resolution, ví dụ `NoSuchBeanDefinitionException` |
| `getMostSpecificCause()` | Nguyên nhân cụ thể nhất trong chuỗi nested exceptions theo Spring |
| `getRootCause()` | Root cause được framework suy ra, có thể null khi không có cause |
| `getRelatedCauses()` | Các lỗi liên quan được `BeanCreationException` bổ sung, nếu có |

Spring 6.2.18 Javadoc chính thức xác nhận `getInjectionPoint()` trả injection point **nếu biết được**; không dereference nó mà không kiểm tra null.

## 3. Ba tầng thông tin cần đọc trong stack trace

Khi thấy failure, xác định:

1. **Bean nào đang tạo thất bại?** Ví dụ `checkoutService`. Đây có thể là consumer, không phải bean bị thiếu.
2. **Vị trí nào yêu cầu dependency?** Constructor parameter 0, field `paymentPort` hay `@Bean` factory method parameter.
3. **Vì sao không resolve được?** Một `NoSuchBeanDefinitionException`, `NoUniqueBeanDefinitionException`, type mismatch hoặc nested `BeanCreationException` của một dependency khác.

Đừng chỉ đọc dòng đầu `APPLICATION FAILED TO START` hoặc `Error creating bean`. Trong chuỗi nhân quả:

```text
UnsatisfiedDependencyException: 'orderService'
  -> UnsatisfiedDependencyException: 'paymentProcessor'
      -> NoSuchBeanDefinitionException: PaymentRepository
```

đây là ví dụ khái niệm; bean `orderService` là một **nạn nhân ở phía trên dependency graph**, còn nguyên nhân thực sự là thiếu `PaymentRepository` trong quá trình tạo `paymentProcessor`.

Sửa bean đầu tiên được nhắc tới trong log có thể vô ích. Theo dõi chuỗi `Caused by:` và injection points, từ ngoài vào trong.

## 4. Phân biệt những exception thường gặp

| Exception | Câu hỏi nó trả lời |
| --- | --- |
| `NoSuchBeanDefinitionException` | Không tìm được bean/candidate thích hợp theo name/type |
| `NoUniqueBeanDefinitionException` | Có nhiều candidates cho một dependency cần single bean và không chọn được duy nhất |
| `UnsatisfiedDependencyException` | Bean đang được tạo có dependency không thể đáp ứng; thường **bọc** hai lỗi trên |
| `BeanNotOfRequiredTypeException` | Bean dưới name tồn tại nhưng actual object không assignable cho required type |
| `BeanCurrentlyInCreationException` | Bean đang được tạo và có cycle/early reference conflict |
| `BeanCreationException` | Ngoại lệ tổng quát trong quá trình tạo bean |
| `BeanInstantiationException` | Tạo instance bằng constructor/factory method thất bại |
| `IllegalArgumentException` hoặc `TypeMismatchException` | Có thể liên quan chuyển đổi giá trị/metadata sai, không nhất thiết dẫn tới UnsatisfiedDependencyException |

Một `context.getBean(PaymentPort.class)` khi hoàn toàn thiếu bean có thể ném trực tiếp `NoSuchBeanDefinitionException`. Nhưng nếu lỗi xảy ra **trong lúc Spring tạo một bean khác cần PaymentPort**, outer exception thường là `UnsatisfiedDependencyException`.

Tương tự, truy vấn trực tiếp một type có hai beans có thể ném `NoUniqueBeanDefinitionException`, còn một `@Bean` method nhận type đó có thể ném `UnsatisfiedDependencyException` với nested `NoUniqueBeanDefinitionException`.

## 5. InjectionPoint là điểm tựa khi debug

`UnsatisfiedDependencyException.getInjectionPoint()` giúp chỉ tới:
- Constructor parameter.
- Factory method parameter.
- Method/setter parameter.
- Field được inject.

Ví dụ log khái niệm:

```text
Error creating bean with name 'billingService':
Unsatisfied dependency expressed through constructor parameter 1
```

Điều đó có nghĩa Spring đang resolve **tham số thứ hai** (index 0-based) cho constructor/factory method có liên quan.

Nếu có thể truy cập instance exception trong code:

```java
UnsatisfiedDependencyException error = ...;

InjectionPoint point = error.getInjectionPoint();

if (point != null) {
    System.out.println(point);
}
```

Đây là **mảnh minh họa**, cần import `InjectionPoint` và phải lấy `error` từ một failure thật. Không phải mọi exception đều có `InjectionPoint`; một số đường dependency checking cũ hoặc factory registration lỗi có thể không cung cấp thông tin này.

Đối chiếu với signature thực tế của constructor, đừng suy tên tham số từ index nếu đã thay code nhưng log/bytecode vẫn cũ.

## 6. Thiếu bean do không đăng ký

Ví dụ:

```java
public interface PaymentPort {
    String charge();
}

public final class CheckoutService {
    private final PaymentPort port;

    public CheckoutService(PaymentPort port) {
        this.port = port;
    }
}
```

Đây là mảnh Java độc lập về type nhưng chưa có Spring registration.

Nếu chỉ đăng ký `CheckoutService` thành bean mà không đăng ký implementation của `PaymentPort`:
- Spring thấy constructor cần một `PaymentPort`.
- Container tìm bean candidate assignable theo type.
- Không có candidate.
- Bean `checkoutService` không tạo được; thường có `UnsatisfiedDependencyException`.
- Nested cause thường là `NoSuchBeanDefinitionException`.

**Cách sửa đúng** là **đăng ký provider thực sự** hoặc sửa dependency contract nếu cố tình optional:

```java
@Bean
PaymentPort paymentPort() {
    return () -> "charged";
}
```

Mảnh này thuộc một configuration class và trả lambda implementation vì `PaymentPort` là functional interface.

Không nên chữa bằng cách đặt một instance `null` trong `@Bean` rồi hy vọng Spring có dependency hợp lệ. Spring có thể biểu diễn `NullBean` hoặc có semantics khác khi factory method trả null; đó không phải cách thực hiện một business dependency bắt buộc.

## 7. Component scanning và package boundaries

Một implementation:

```java
@Service
public class StripePaymentPort implements PaymentPort {
    @Override
    public String charge() {
        return "paid";
    }
}
```

có `@Service` nhưng Spring không nhất thiết tìm thấy nó nếu:
- `@ComponentScan` không bao phủ package chứa class.
- Ứng dụng chỉ `register(Config.class)` với config không có scan/import.
- Implementation không có trên runtime classpath/module.
- Có exclude filter hoặc conditional registration.
- Bean đã nằm trong một **context khác không phải parent** của consumer.

Cấu hình minh họa:

```java
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.example.payments")
class PaymentConfiguration {
}
```

Đây là snippet cần đúng package và imports.

Đối với library/application modules, thay vì mở rộng scan mù quáng toàn classpath, có thể sử dụng `@Import(SomeConfig.class)` hoặc explicit `@Bean` registration.

**Không kiểm tra mỗi việc file source tồn tại:** Spring chạy trên runtime classpath và container configuration, không đọc mọi class mà IDE tìm thấy.

## 8. @Profile và @Conditional loại BeanDefinition

Một bean được khai báo:

```java
@Bean
@Profile("prod")
PaymentPort productionPaymentPort() {
    return new ProductionPaymentPort();
}
```

Method này chỉ đăng ký khi profile phù hợp. Nếu test sử dụng active profile `test` mà không có provider khác, constructor consumer vẫn yêu cầu `PaymentPort` và bị unsatisfied.

Tương tự, `@Conditional` có thể khiến BeanDefinition không đăng ký.

Spring Framework core cung cấp `@Conditional` và `@Profile`; **`@ConditionalOnProperty` là annotation thuộc Spring Boot**, không tự có trong `spring-context` dependency thuần.

Cách debug:
1. In `environment.getActiveProfiles()` trước refresh.
2. Xem declaration/config imports và điều kiện.
3. Kiểm tra thật sự có bean definition sau config processing không.
4. Bảo đảm tests dùng profile phù hợp, ví dụ `@ActiveProfiles("test")` hoặc tự set profile trước `refresh()`.
5. Nếu feature optional, biểu đạt dependency optional thay vì hardcode assumption rằng bean luôn tồn tại.

Đổi active profile **sau khi context đã refresh** không tự rerun `@Profile` và tạo bean mới.

## 9. Có nhiều beans cùng type cũng là một lỗi dependency

Ví dụ:

```java
@Bean("fastPort")
PaymentPort fastPort() {
    return () -> "fast";
}

@Bean("safePort")
PaymentPort safePort() {
    return () -> "safe";
}

@Bean
CheckoutService checkoutService(PaymentPort port) {
    return new CheckoutService(port);
}
```

Đây là các factory methods minh họa trong một `@Configuration`. Nếu cả hai providers đều là candidates và không có selection hint phù hợp, Spring không thể chọn một `PaymentPort` duy nhất.

Ngoại lệ thường thấy:
- Outer `UnsatisfiedDependencyException` cho `checkoutService`.
- Inner `NoUniqueBeanDefinitionException`, liệt kê candidates.

**Không dùng “No qualifying bean” như từ đồng nghĩa với “zero candidates”**; một thông báo có thể liên quan qualifiers/generics khiến tập hợp candidate sau lọc rỗng dù beans cùng raw type hiện diện. Kiểm tra type/qualifier filtering thực tế.

## 10. Sửa ambiguity bằng @Qualifier

Khi consumer cần rõ một implementation:

```java
@Bean
CheckoutService checkoutService(
        @Qualifier("fastPort") PaymentPort port) {
    return new CheckoutService(port);
}
```

Spring dùng qualifier để thu hẹp candidates. String trong `@Qualifier` phải khớp name/qualifier metadata tương ứng.

Với field/constructor injection:

```java
public CheckoutService(
        @Qualifier("fastPort") PaymentPort port) {
    this.port = port;
}
```

Cách đặt qualifier tại **injection point** làm ý đồ selection rõ ràng. Nếu đổi bean name nhưng quên qualifier, dependency lại không được thỏa mãn.

**Không coi qualifier như sửa được proxy type mismatch:** @Qualifier chọn bean; nó không làm JDK proxy tương thích với concrete implementation class.

## 11. Sửa ambiguity bằng @Primary

Nếu có một provider mặc định dùng rộng rãi:

```java
@Bean("fastPort")
@Primary
PaymentPort fastPort() {
    return () -> "fast";
}

@Bean("safePort")
PaymentPort safePort() {
    return () -> "safe";
}
```

Trong single-valued autowiring, `@Primary` được ưu tiên khi candidates phù hợp. Nếu consumer có `@Qualifier("safePort")` thì qualifier có thể chỉ rõ bean thứ hai thay vì default primary.

`@Primary` không biến nhiều implementations thành một implementation; các API collection injection như `List<PaymentPort>`/`Map<String,PaymentPort>` vẫn có thể nhận nhiều beans.

Đừng đánh dấu bừa nhiều beans cùng kiểu là `@Primary`. Nếu nhiều candidates đều primary và không có rule tie-break hợp lệ, ambiguity vẫn có thể xảy ra.

## 12. Name matching và -parameters

Spring có thể xét tên dependency/parameter như một tín hiệu khi chọn candidate, nhưng không nên dựa vào Java compiler retaining parameter names một cách ngẫu nhiên.

Ví dụ:

```java
CheckoutService checkoutService(PaymentPort fastPort)
```

tên parameter `fastPort` có thể giúp disambiguation trong những điều kiện nhất định. Tuy nhiên:
- Java bytecode có thể không chứa tên parameter nếu không compile với `-parameters`.
- Name matching là một phần của resolution algorithm có precedence cùng qualifiers/primary/fallback.
- Refactor parameter name có thể làm selection đổi ngoài ý muốn.
- Java records, constructors/factory methods và một số compiler có metadata khác nhau.

Gradle có thể cấu hình:

```groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += ['-parameters']
}
```

Đây là snippet build config hữu ích khi muốn Spring/JVM reflection thấy parameter names.

**Với behavior bắt buộc**, `@Qualifier` rõ ràng thường tốt hơn việc mong tên parameter “vừa khớp” bean name.

## 13. Generic types là thành phần của candidate filtering

Giả sử:

```java
interface Repository<T> {
    T load();
}

record Invoice(String id) {}
record Customer(String id) {}
```

Một constructor cần:

```java
public BillingService(Repository<Invoice> invoices) {
    this.invoices = invoices;
}
```

Nếu chỉ có `Repository<Customer>` bean, không được kết luận Spring chắc chắn thỏa mãn `Repository<Invoice>` vì cùng raw class `Repository`.

Spring tận dụng `ResolvableType` và generic metadata để lọc candidates. Khi factory method trả raw type hoặc `Object` quá chung, việc dự đoán generics không đầy đủ có thể gây lỗi hoặc ambiguity.

Cách sửa:
- Khai báo generic return type thật của `@Bean`.
- Khai báo implementation type cụ thể.
- Dùng qualifier nếu có nhiều repositories cùng generic type.
- Tránh raw types làm mất contract.

Không ép cast một repository sai generic chỉ để container start thành công; sai lầm sẽ chuyển từ startup thành runtime type errors.

## 14. Parent–child ApplicationContexts và lookup

Một dependency có thể “đang tồn tại” ở ứng dụng nhưng consumer vẫn không tìm được vì context placement:

```text
Root ApplicationContext
   ├── sharedService
   └── repository

Child ApplicationContext A
   └── controllerA

Child ApplicationContext B
   └── controllerB
```

Child có thể lookup bean từ parent theo delegation rules. Nhưng:
- Parent không tự nhìn ngược xuống beans của child.
- Sibling child contexts A và B không tự nhìn vào local beans của nhau.
- Local bean name có thể shadow parent bean.
- Type name enumeration APIs trên `ListableBeanFactory` có local-only semantics nếu không dùng các helpers including ancestors.

Vì vậy thêm `@ComponentScan` trong child B chưa chắc sửa dependency nằm ở parent hoặc child A. Chọn **bean definition ownership** và hierarchy đúng.

Đừng chẩn đoán “scan package” mà quên xác định đang inject bean trong context nào.

## 15. JDK proxy và concrete type injection

Giả sử target:

```java
public class PaymentAdapter implements PaymentPort {
    // business methods
}
```

Spring có thể tạo **JDK dynamic proxy** implements `PaymentPort` nhưng **không extends `PaymentAdapter`**.

Nếu consumer nhận:

```java
public CheckoutService(PaymentAdapter adapter) {
    // ...
}
```

và bean exposed ra ngoài là JDK proxy, actual type không assignable về `PaymentAdapter`. Tùy resolution/initialization path, có thể thấy `BeanNotOfRequiredTypeException`, `NoSuchBeanDefinitionException` hoặc một outer `UnsatisfiedDependencyException`.

Giải pháp ưu tiên là inject **`PaymentPort` interface**, phù hợp với cả JDK proxy và class-based CGLIB proxy.

Bật `proxyTargetClass=true` có thể làm CGLIB subclass phù hợp concrete type nếu target class proxyable, nhưng không phải cách sửa mọi dependency issue. `final` class/method và JPMS constraints giới hạn class proxies.

**Tên BeanDefinition class gốc không chứng minh object cuối cùng expose cùng class sau post-processing.**

## 16. @Autowired required và optional dependencies

Field injection bắt buộc:

```java
@Autowired
private PaymentPort paymentPort;
```

Spring yêu cầu có candidate phù hợp. Có thể chuyển sang optional nếu thật sự optional:

```java
@Autowired(required = false)
private PaymentPort paymentPort;
```

Khi không có candidate, Spring có thể không inject field này, nó giữ default Java value (thường null). Điều đó chuyển trách nhiệm null-check sang application code; **không dùng optional để giấu dependency thực sự cần thiết**.

Các kiểu biểu đạt optional khác:

```java
public PaymentService(Optional<PaymentPort> paymentPort) {
    // ...
}
```

```java
public PaymentService(ObjectProvider<PaymentPort> provider) {
    // ...
}
```

Với `Optional<T>`, application phải xử lý empty. Với `ObjectProvider<T>`, caller có thể gọi `getIfAvailable()` thay vì `getObject()`, nhưng lỗi khi **tạo** bean hoặc ambiguity vẫn có thể lan ra.

Lưu ý với **constructor injection**: `@Autowired(required=false)` không mang cùng semantics đơn giản như field/setter; Spring dùng constructor candidate selection. Với constructor duy nhất, dependency arguments thường vẫn required trừ các dạng optional được khai báo. Không khuyên “thêm @Autowired(required=false) vào constructor duy nhất” như một cách sửa thiếu bean chắc chắn đúng.

## 17. Constructor selection và @Autowired

Spring 6.2 hỗ trợ constructor injection mà không cần `@Autowired` nếu target class chỉ có **một constructor**. Có nhiều constructors:
- Một constructor duy nhất đánh dấu `@Autowired(required=true)` là preferred required constructor.
- Nhiều constructors `@Autowired(required=false)` được xem là candidates với greedy resolution rules.
- Default/primary constructor có thể là fallback trong một số trường hợp.
- Constructor argument types phải resolve theo BeanFactory candidates.

Ví dụ minh họa:

```java
public class BillingService {
    public BillingService() {
    }

    @Autowired
    public BillingService(PaymentPort port) {
        // ...
    }
}
```

Nếu `PaymentPort` không tồn tại, Spring không thể tự bỏ qua required constructor để chọn zero-arg constructor theo mong muốn của developer; phải hiểu selection rules và đánh dấu đúng.

**Thiếu dependency của một required constructor** không được sửa bằng cách thêm constructor không tham số chỉ để startup “qua được”; nếu object ở trạng thái chưa đầy đủ, lỗi sẽ nổ ở business method và khó debug hơn.

## 18. @Bean factory method parameters

Phương thức `@Bean` cũng có injection points:

```java
@Bean
CheckoutService checkoutService(PaymentPort port) {
    return new CheckoutService(port);
}
```

Khi Spring tạo `checkoutService`, nó resolve `PaymentPort` cho tham số. Nếu không có hoặc không unique, method body **chưa được gọi**, nên constructor `CheckoutService` cũng chưa chạy.

Vì vậy việc đặt debugger breakpoint trong body rồi thấy “không chạy” không có nghĩa Spring bỏ qua factory method; failure có thể xảy ra **trước invocation**, ngay tại argument resolution.

Giải pháp tương tự constructor:
- Register candidate hợp lệ.
- `@Qualifier`/`@Primary`.
- Chọn profile/config đúng.
- Dùng `ObjectProvider` hoặc `Optional` khi dependency thực sự optional.

## 19. Field và setter injection có thời điểm khác constructor

Constructor injection resolution xảy ra **trước khi target instance hoàn chỉnh**. Field/setter injection qua `AutowiredAnnotationBeanPostProcessor` xảy ra **sau instantiation, trước initialization callbacks**.

Ví dụ:

```java
@Component
public class ReportService {
    @Autowired
    private ExportPort exportPort;
}
```

Nếu không có `ExportPort`, Spring có thể đã gọi constructor mặc định của `ReportService` rồi mới thất bại khi populating fields. Constructor counter tăng không chứng minh bean đã hoàn tất lifecycle hoặc sẵn sàng hoạt động.

Nếu một `BeanPostProcessor` tùy biến gọi `getBean` quá sớm, lỗi wiring có thể do infrastructure phase chứ không phải controller bình thường. Chẩn đoán phải xác định **phase** của exception.

Ưu tiên constructor injection cho những dependencies bắt buộc vì invariant rõ ràng, test đơn giản và tránh partially initialized objects.

## 20. @Value, placeholder và simple constructor values

Không phải mọi lỗi tạo bean đến từ một Java service interface.

Ví dụ:

```java
public BillingClient(
        @Value("${billing.timeout}") int timeout) {
    // ...
}
```

Mảnh này dùng placeholder property theo Spring. Nếu `billing.timeout` thiếu, syntax configuration/property resolver hoặc conversion có thể fail; ngoại lệ ngoài cùng **không nhất thiết** là `UnsatisfiedDependencyException` trong mọi pathway.

Nếu value là `"abc"` nhưng target là `int`, conversion failure có thể xuất hiện trong cause chain. Đừng nhìn thấy chữ “parameter 0” rồi luôn đi tìm một bean type chưa đăng ký.

Hướng debug:
1. Xác nhận property key đúng.
2. Kiểm tra property source precedence/profiles.
3. Xác nhận string value hợp lệ cho type target.
4. Có default value hợp lý nếu optional, ví dụ placeholder syntax `${billing.timeout:30}`.
5. Phân biệt `@Value` resolution với `@Autowired` bean candidate selection.

Trong các code snippets Java template đang trình bày, placeholder được viết bằng cú pháp `${...}` và sẽ hiển thị thành dấu đô la thông thường sau khi xuất Markdown.

## 21. Circular dependencies: một nguyên nhân khác

Cycle:

```text
A requires B by constructor
B requires A by constructor
```

Không thể hoàn thành cả hai constructors nếu phải có đối tượng kia hoàn chỉnh trước. Thường thấy `BeanCurrentlyInCreationException` hoặc `UnsatisfiedDependencyException` ở tầng ngoài.

Một số setter/field injection cycles trong Spring core có thể được giải qua early singleton exposure khi được cho phép, nhưng đó không phải giải pháp thiết kế tốt. Spring Boot còn có defaults riêng về circular references.

**Đừng sửa vòng bằng cách thêm @Lazy mọi nơi** mà không phân tích runtime control flow. Có thể refactor:
- Tách một contract/shared state thành bean thứ ba.
- Dùng domain event khi quan hệ chỉ là notification.
- Dùng `ObjectProvider` khi dependency chỉ cần ở runtime tại một operation.
- Tránh hai business services phụ thuộc ngược nhau bằng constructor.

`@Primary` hay `@Qualifier` không giải quyết cycle về mặt topology.

## 22. Thiếu dependency do test slice / test config

Một Spring test có thể load **ít beans hơn production**:

```java
@SpringJUnitConfig(ReportService.class)
class ReportServiceTest {
    // ...
}
```

Nếu `ReportService` cần `ExportPort` nhưng test chỉ import một class, context thiếu collaborator nên báo `UnsatisfiedDependencyException`. Trong Boot test slices, phần tự động cấu hình bị giới hạn còn rõ hơn.

Cách sửa:
- `@SpringJUnitConfig(TestConfig.class)` chứa fake/in-memory `ExportPort`.
- `@Import` additional test configuration.
- `@TestBean` / `@MockitoBean` khi phù hợp Spring Test 6.2.
- Unit test trực tiếp new service + fake nếu không cần IoC integration.

**Không bật scan cả ứng dụng chỉ để chữa một unit test** nếu làm vậy kéo database/network infrastructure không liên quan.

## 23. Một checklist điều tra production startup

Khi ứng dụng không startup:

1. Copy **exception stack trace đầy đủ**, không cắt chỉ “Error creating bean”.
2. Đọc bean name ở outermost failure.
3. Tìm `UnsatisfiedDependencyException` có injection point chi tiết nhất.
4. Đi theo `Caused by` đến `NoSuch...` / `NoUnique...` / bean creation root cause.
5. Kiểm tra type và generic type của injection point.
6. Kiểm tra `@Qualifier` và name metadata.
7. Kiểm tra bean definitions từ `@ComponentScan` / `@Import` / `@Bean` / XML.
8. Kiểm tra active profiles và conditions tại runtime.
9. Xem BeanFactory nào sở hữu provider/caller trong parent-child contexts.
10. Kiểm tra annotations có được xử lý bởi AnnotationConfigApplicationContext hay custom factory không.
11. Nếu có proxies, kiểm tra interface vs concrete exposed type.
12. Xác nhận Java constructor selection.
13. Kiểm tra factory method return type/bean instance supplier.
14. Kiểm tra dependency bị thiếu thực sự hay bị tạo lỗi trước khi cung cấp.
15. Kiểm tra version/autoconfiguration/classpath của module chứa implementation.
16. Viết test failure nhỏ nhất và test fix tương ứng.

Một cách tốt để tìm đúng tầng lỗi là bắt `BeansException` lúc dựng context trong test và kiểm tra **exception cause chain**, không grep nguyên văn thông báo có thể đổi theo locale/version.

## 24. Vì sao không nên dùng catch(Exception) rồi cho app chạy tiếp?

Một pattern sai:

```java
try {
    context.refresh();
} catch (Exception ignored) {
    // Bỏ lỗi startup và vẫn nhận requests.
}
```

Đây là anti-pattern. Sau failed `refresh()`, ApplicationContext có thể đã cleanup một phần, một số singleton đã được tạo rồi destroy, và trạng thái container không bảo đảm dùng được như context active hoàn chỉnh.

Cách đúng:
- Trong application startup, **fail fast** và surface root cause.
- Trong integration tests, **expect failure** và assert exception type/cause.
- Trong optional feature wiring, biểu đạt optionality bằng bean definitions/`ObjectProvider` thay vì nuốt lỗi tạo required bean.
- Không cố gọi `getBean` từ context refresh thất bại như một runtime fallback.

Nếu ứng dụng cần graceful degradation, định nghĩa fallback provider rõ ràng, dùng `@Conditional`/profile/config hoặc một adapter chịu trách nhiệm fallback ngay từ bean graph.

## 25. Chẩn đoán không eager instantiate toàn bộ beans

Một công cụ diagnostics có thể muốn liệt kê candidates trước khi tạo instances. Với `DefaultListableBeanFactory` hoặc `ListableBeanFactory`:

```java
String[] names =
        factory.getBeanNamesForType(
                PaymentPort.class, true, false);
```

Cờ `allowEagerInit=false` hạn chế một số eager type checks, đặc biệt đối với `FactoryBean`. Nó không có nghĩa mọi class/type prediction hoàn toàn free side effects. Một số FactoryBean product types có thể chưa được biết nếu không eager initialize.

Đừng dùng `getBeansOfType(PaymentPort.class)` chỉ để in tên, vì API này trả **instances** và có thể tạo lazy/prototype bean candidates. Việc quét toàn container để “xem bean có không” có thể kích hoạt chính lỗi startup hoặc side effect mà bạn đang điều tra.

Có thể dùng `getBeanDefinitionNames()` để inspect definitions local trước, rồi `getBeanNamesForType(...,false)`, sau đó mới lấy **một bean có chọn lọc**.

## 26. Ví dụ Java 17 hoàn chỉnh: tái hiện và sửa lỗi

Ví dụ sau độc lập, chỉ dùng `spring-context:6.2.18`. Một chương trình tạo nhiều **contexts tách biệt**, chủ động gây lỗi và kiểm tra **class của exceptions trong cause chain**.

Các ca:

- **Missing:** một `@Bean` factory method nhận `AbsentPort` nhưng không có provider.
- **Ambiguous:** `Checkout` cần `PaymentPort`, có hai beans cùng type và không có chọn lựa.
- **Profile missing:** chỉ có production bean dưới `@Profile("prod")`, context profile test không có candidate.
- **@Qualifier repaired:** chọn đúng bean `fastPort`.
- **@Primary repaired:** chọn default bean `fastPort`.
- **Profile repaired:** mở context với profile `prod`.
- **Optional provider:** không có `AbsentPort` vẫn khởi tạo bean sử dụng `ObjectProvider<AbsentPort>` và `getIfAvailable()` trả null.
- **Direct lookup:** `getBean(AbsentPort.class)` ném `NoSuchBeanDefinitionException` trực tiếp; `getBean(PaymentPort.class)` khi hai providers ném `NoUniqueBeanDefinitionException` trực tiếp.

### 26.1 Cấu trúc thư mục

```text
spring-dependency-diagnosis/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/demo/DependencyDiagnosisDemo.java
    └── test/java/demo/DependencyDiagnosisTest.java
```

### 26.2 settings.gradle

```groovy
rootProject.name = 'spring-dependency-diagnosis'
```

### 26.3 build.gradle

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.18'

    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.DependencyDiagnosisDemo'
}

test {
    useJUnitPlatform()
}
```

Không cần Spring Boot, cơ sở dữ liệu hoặc HTTP server. Mỗi `AnnotationConfigApplicationContext` đều được đóng sau khi dùng.

### 26.4 src/main/java/demo/DependencyDiagnosisDemo.java

```java
package demo;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

public class DependencyDiagnosisDemo {

    public interface PaymentPort {
        String code();
    }

    public interface AbsentPort {
        String unavailable();
    }

    public record Checkout(PaymentPort port) {
        public String chosen() {
            return port.code();
        }
    }

    public record MissingClient(AbsentPort port) {
    }

    public record OptionalClient(
            ObjectProvider<AbsentPort> provider) {
        public boolean absent() {
            return provider.getIfAvailable() == null;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class MissingConfig {
        @Bean
        public MissingClient missingClient(AbsentPort port) {
            return new MissingClient(port);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class SourcesOnlyConfig {
        @Bean("fastPort")
        public PaymentPort fastPort() {
            return () -> "fast";
        }

        @Bean("safePort")
        public PaymentPort safePort() {
            return () -> "safe";
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AmbiguousConfig {
        @Bean("fastPort")
        public PaymentPort fastPort() {
            return () -> "fast";
        }

        @Bean("safePort")
        public PaymentPort safePort() {
            return () -> "safe";
        }

        @Bean
        public Checkout checkout(PaymentPort port) {
            return new Checkout(port);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class QualifierConfig {
        @Bean("fastPort")
        public PaymentPort fastPort() {
            return () -> "fast";
        }

        @Bean("safePort")
        public PaymentPort safePort() {
            return () -> "safe";
        }

        @Bean
        public Checkout checkout(
                @Qualifier("fastPort") PaymentPort port) {
            return new Checkout(port);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class PrimaryConfig {
        @Bean("fastPort")
        @Primary
        public PaymentPort fastPort() {
            return () -> "fast";
        }

        @Bean("safePort")
        public PaymentPort safePort() {
            return () -> "safe";
        }

        @Bean
        public Checkout checkout(PaymentPort port) {
            return new Checkout(port);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ProfileConfig {
        @Bean
        @Profile("prod")
        public PaymentPort prodPort() {
            return () -> "prod";
        }

        @Bean
        public Checkout checkout(PaymentPort port) {
            return new Checkout(port);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class OptionalConfig {
        @Bean
        public OptionalClient optionalClient(
                ObjectProvider<AbsentPort> provider) {
            return new OptionalClient(provider);
        }
    }

    public static BeansException expectRefreshFailure(
            Class<?> configClass, String profile) {

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            context.getEnvironment().setActiveProfiles(profile);
            context.register(configClass);

            try {
                context.refresh();
            } catch (BeansException failure) {
                return failure;
            }

            throw new AssertionError(
                    "Expected context refresh failure");
        }
    }

    public static boolean chainContains(
            Throwable failure,
            Class<? extends Throwable> expectedType) {

        for (Throwable current = failure;
             current != null;
             current = current.getCause()) {

            if (expectedType.isInstance(current)) {
                return true;
            }
        }

        return false;
    }

    public static UnsatisfiedDependencyException findUnsatisfied(
            Throwable failure) {

        for (Throwable current = failure;
             current != null;
             current = current.getCause()) {

            if (current instanceof UnsatisfiedDependencyException ude) {
                return ude;
            }
        }

        return null;
    }

    public static String checkoutSource(
            Class<?> configClass, String profile) {

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            context.getEnvironment().setActiveProfiles(profile);
            context.register(configClass);
            context.refresh();

            return context.getBean(Checkout.class).chosen();
        }
    }

    public static void main(String[] args) {

        BeansException missing =
                expectRefreshFailure(MissingConfig.class, "test");

        System.out.println("missing.unsatisfied="
                + chainContains(
                        missing, UnsatisfiedDependencyException.class));
        System.out.println("missing.noSuch="
                + chainContains(
                        missing, NoSuchBeanDefinitionException.class));

        UnsatisfiedDependencyException missingUde =
                findUnsatisfied(missing);

        System.out.println("missing.injectionPointKnown="
                + (missingUde != null &&
                   missingUde.getInjectionPoint() != null));

        BeansException ambiguous =
                expectRefreshFailure(AmbiguousConfig.class, "test");

        System.out.println("ambiguous.unsatisfied="
                + chainContains(
                        ambiguous, UnsatisfiedDependencyException.class));
        System.out.println("ambiguous.noUnique="
                + chainContains(
                        ambiguous, NoUniqueBeanDefinitionException.class));

        BeansException wrongProfile =
                expectRefreshFailure(ProfileConfig.class, "test");

        System.out.println("profile.test.unsatisfied="
                + chainContains(
                        wrongProfile, UnsatisfiedDependencyException.class));

        System.out.println("profile.prod.source="
                + checkoutSource(ProfileConfig.class, "prod"));

        System.out.println("qualifier.source="
                + checkoutSource(QualifierConfig.class, "test"));

        System.out.println("primary.source="
                + checkoutSource(PrimaryConfig.class, "test"));

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             OptionalConfig.class)) {

            OptionalClient optional =
                    context.getBean(OptionalClient.class);

            System.out.println("optional.absent="
                    + optional.absent());
        }

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            context.refresh();

            try {
                context.getBean(AbsentPort.class);
                throw new AssertionError("Expected missing bean");
            } catch (NoSuchBeanDefinitionException expected) {
                System.out.println("direct.missing=NoSuch");
            }
        }

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             SourcesOnlyConfig.class)) {

            try {
                context.getBean(PaymentPort.class);
                throw new AssertionError("Expected multiple beans");
            } catch (NoUniqueBeanDefinitionException expected) {
                System.out.println("direct.ambiguous=NoUnique");
            }
        }
    }
}
```

**Vì sao dùng nhiều context?** Mỗi case độc lập và khi một context intentionally fail refresh, ta không cố phục hồi context đó để chạy scenario khác. Điều này tránh việc bean definitions hoặc singleton cache từ một case “rò” sang case kế tiếp.

### 26.5 Chạy ví dụ

Yêu cầu JDK 17+ và Gradle 8+:

```bash
gradle run --quiet
```

### 26.6 System.out dự kiến

```text
missing.unsatisfied=true
missing.noSuch=true
missing.injectionPointKnown=true
ambiguous.unsatisfied=true
ambiguous.noUnique=true
profile.test.unsatisfied=true
profile.prod.source=prod
qualifier.source=fast
primary.source=fast
optional.absent=true
direct.missing=NoSuch
direct.ambiguous=NoUnique
```

Đây là output của `System.out` do chương trình tự in. Console thực có thể thêm logs từ Gradle/Spring. Không hardcode toàn bộ error message từ Spring, chỉ khẳng định cause chain types và API selection đã thiết kế.

`getInjectionPoint()` sẽ có thông tin ở case thiếu factory method argument do Spring đang resolve `AbsentPort` cho `MissingConfig.missingClient`. Với lỗi khác (ví dụ legacy setter/property checking), method này có thể null; vì vậy README không giả định luôn non-null.

### 26.7 Ca thiếu bean

`MissingConfig.missingClient(AbsentPort port)` là factory method bắt buộc nhận `AbsentPort`. Không có bean type đó, nên:
- Outer failure liên quan `UnsatisfiedDependencyException`.
- Nguyên nhân trực tiếp là không có qualifying candidate, thường `NoSuchBeanDefinitionException`.
- Injection point là factory method argument.
- Method body không thể chạy vì chưa resolve xong argument.

### 26.8 Ca nhiều bean

`AmbiguousConfig` có hai bean names `fastPort` và `safePort` cùng type `PaymentPort`. `checkout(PaymentPort port)` không chỉ rõ qualifier và không có default primary; selection trở nên ambiguous.

`NoUniqueBeanDefinitionException` chỉ ra đây **không phải zero candidates**. Cách sửa được minh họa bởi:
- `QualifierConfig`: `@Qualifier("fastPort")`.
- `PrimaryConfig`: `@Primary` trên `fastPort`.

### 26.9 Ca profile

`ProfileConfig.prodPort()` chỉ có definition dưới profile `prod`. Khi active profile `test`, `checkout(PaymentPort port)` thiếu provider. Khi set profile `prod` trước `refresh()`, bean tồn tại và method `chosen()` trả `prod`.

### 26.10 Ca optional provider

`OptionalConfig.optionalClient(ObjectProvider<AbsentPort> provider)` không yêu cầu tạo một `AbsentPort` ngay lúc inject handle. `OptionalClient.absent()` gọi `getIfAvailable()`, trả null khi không có bean.

Điều này đúng vì nghiệp vụ đã quyết định feature optional. Đừng dùng `ObjectProvider` để che lỗi cho payment processor cốt lõi mà app không thể chạy nếu thiếu.

### 26.11 Direct lookup vs injected dependency

`context.getBean(AbsentPort.class)` trên context không có provider ném direct `NoSuchBeanDefinitionException`. Tương tự, `context.getBean(PaymentPort.class)` trên context có hai matches ném direct `NoUniqueBeanDefinitionException`.

Khi cùng vấn đề xảy ra **đang tạo Checkout/MissingClient**, Spring wrap nó trong context của injection point thành `UnsatisfiedDependencyException`. Bởi vậy đọc **outer và inner exceptions** đều cần thiết.

## 27. JUnit 5 kiểm chứng các ca lỗi

Các tests kiểm tra **cause-chain classes**, không so khớp nguyên văn message tiếng Anh. Điều này giảm độ nhạy với thay đổi Spring patch versions.

### 27.1 src/test/java/demo/DependencyDiagnosisTest.java

```java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class DependencyDiagnosisTest {

    @Test
    void missingFactoryMethodArgumentIsUnsatisfied() {
        BeansException failure =
                DependencyDiagnosisDemo.expectRefreshFailure(
                        DependencyDiagnosisDemo.MissingConfig.class,
                        "test");

        assertTrue(DependencyDiagnosisDemo.chainContains(
                failure, UnsatisfiedDependencyException.class));

        assertTrue(DependencyDiagnosisDemo.chainContains(
                failure, NoSuchBeanDefinitionException.class));

        var ude =
                DependencyDiagnosisDemo.findUnsatisfied(failure);

        assertNotNull(ude);
        assertNotNull(ude.getInjectionPoint());
    }

    @Test
    void twoCandidatesWithoutSelectionFail() {
        BeansException failure =
                DependencyDiagnosisDemo.expectRefreshFailure(
                        DependencyDiagnosisDemo.AmbiguousConfig.class,
                        "test");

        assertTrue(DependencyDiagnosisDemo.chainContains(
                failure, UnsatisfiedDependencyException.class));

        assertTrue(DependencyDiagnosisDemo.chainContains(
                failure, NoUniqueBeanDefinitionException.class));
    }

    @Test
    void profileCanRemoveRequiredCandidate() {
        BeansException failure =
                DependencyDiagnosisDemo.expectRefreshFailure(
                        DependencyDiagnosisDemo.ProfileConfig.class,
                        "test");

        assertTrue(DependencyDiagnosisDemo.chainContains(
                failure, UnsatisfiedDependencyException.class));

        assertEquals("prod",
                DependencyDiagnosisDemo.checkoutSource(
                        DependencyDiagnosisDemo.ProfileConfig.class,
                        "prod"));
    }

    @Test
    void qualifierResolvesAmbiguity() {
        assertEquals("fast",
                DependencyDiagnosisDemo.checkoutSource(
                        DependencyDiagnosisDemo.QualifierConfig.class,
                        "test"));
    }

    @Test
    void primaryResolvesDefaultCandidate() {
        assertEquals("fast",
                DependencyDiagnosisDemo.checkoutSource(
                        DependencyDiagnosisDemo.PrimaryConfig.class,
                        "test"));
    }

    @Test
    void directLookupThrowsMoreSpecificExceptions() {
        try (var empty =
                     new AnnotationConfigApplicationContext()) {

            empty.refresh();

            assertThrows(
                    NoSuchBeanDefinitionException.class,
                    () -> empty.getBean(
                            DependencyDiagnosisDemo.AbsentPort.class));
        }

        try (var multi =
                     new AnnotationConfigApplicationContext(
                             DependencyDiagnosisDemo.SourcesOnlyConfig.class)) {

            assertThrows(
                    NoUniqueBeanDefinitionException.class,
                    () -> multi.getBean(
                            DependencyDiagnosisDemo.PaymentPort.class));
        }
    }

    @Test
    void absentOptionalDependencyDoesNotFailRefresh() {
        try (var context =
                     new AnnotationConfigApplicationContext(
                             DependencyDiagnosisDemo.OptionalConfig.class)) {

            var client = context.getBean(
                    DependencyDiagnosisDemo.OptionalClient.class);

            assertTrue(client.absent());
        }
    }
}
```

### 27.2 Lệnh chạy JUnit

```bash
gradle test
```

Kết quả dự kiến: **7 JUnit tests pass** nếu chạy với project và versions đã khai báo. JUnit/Gradle không bắt buộc in riêng mỗi test method ra console; kiểm tra HTML report:

```text
build/reports/tests/test/index.html
```

Các tests deliberately sử dụng **independent contexts**, không phụ thuộc thứ tự test methods và không mutate shared static business state.

## 28. Tại sao không assert equals trên entire message?

Một số developers viết:

```java
assertEquals(
        "Unsatisfied dependency expressed through constructor ...",
        failure.getMessage());
```

Đây là anti-pattern khi mục đích là kiểm thử resolution behavior. Text message có thể khác:
- Spring patch version.
- Bean name/function metadata.
- JVM reflection parameter names.
- Package/class names.
- Nested exception formatting.
- Thứ tự candidates.

Kiểm tra **exception classes trong cause chain** và public getters ổn định hơn. Khi muốn assert message có thông tin thiết yếu, dùng `assertTrue(message.contains("..."))` cho một cụm nhỏ đã có contract, không snapshot nguyên cả stack trace.

## 29. Sửa theo nguyên nhân, không theo triệu chứng

| Biểu hiện | Kiểm tra trước | Sửa phù hợp |
| --- | --- | --- |
| `NoSuchBeanDefinitionException` nested | Bean definition có được đăng ký không? | @Bean/@ComponentScan/@Import đúng |
| No bean dưới profile test | Active profiles/conditions | Bổ sung test provider/đổi profile trước refresh |
| `NoUniqueBeanDefinitionException` nested | Danh sách candidates/qualifiers | @Qualifier/@Primary/đổi contract |
| Named bean có nhưng type không match | Proxy vs concrete type | Inject interface hoặc chọn class proxy có lý do |
| Generic injection thất bại | `ResolvableType` và @Bean declared type | Correct generic metadata |
| Constructor dependency thiếu | Required ctor signature | Register collaborator; optional only if intended |
| @Autowired field không được inject | Annotation processor/scan/context | AnnotationConfigApplicationContext hoặc processors hợp lệ |
| @Bean method chưa chạy | Factory method argument resolution | Sửa dependency của chính method đó |
| Nested bean failure | Cause chain tới bean lá | Sửa bean leaf thay vì outer consumer |
| BeanFactoryPostProcessor tự cần @Autowired | Infrastructure phase xảy ra sớm | Dùng explicit @Bean wiring; đừng phụ thuộc regular annotation processing |
| Child không thấy sibling bean | Context hierarchy | Di chuyển definition đúng owner |
| Property string không đổi được sang target type | `@Value`/converter | Sửa property/ConversionService |
| Explicit circular constructor graph | A → B → A | Refactor graph/hoãn hợp lý |
| Lazy/provider làm fail muộn | Resolution bị trì hoãn, không hết lỗi | Test call path và xác định fallback |

**Mục tiêu:** sửa graph và contract để bean thật sự đúng, không chỉ làm startup không ném ngoại lệ.

## 30. Công cụ hỗ trợ debug

### 30.1 Bật logging phù hợp

Log categories hữu ích khi ứng dụng dùng logging backend tương thích:
- `org.springframework.beans.factory.support` cho bean factory registration/creation internals.
- `org.springframework.beans.factory.annotation` cho autowired annotation processing.
- `org.springframework.context.annotation` cho configuration class processing.

Chọn `DEBUG` hoặc `TRACE` tạm thời trong môi trường điều tra. Không bật TRACE toàn bộ Spring vĩnh viễn ở production nếu log volume/PII gây rủi ro.

### 30.2 Xem profiles

```java
System.out.println(
        Arrays.toString(
                context.getEnvironment().getActiveProfiles()));
```

Đoạn này cần `java.util.Arrays`, phù hợp khi context đang có. Nếu in sau failed refresh mà context không active, có thể vẫn đọc Environment metadata nhưng không coi context là healthy.

### 30.3 Xem names trước khi instantiate

```java
String[] names =
        context.getBeanNamesForType(
                PaymentPort.class, true, false);
```

Dùng với `ListableBeanFactory`/`ApplicationContext`, method trả names, không phải objects. Một số FactoryBean products có thể chưa hiện diện trong lookup non-eager nếu product type không dự đoán được; không suy kết quả rỗng là kết luận cuối cho mọi dynamic bean.

### 30.4 Kiểm tra dependency từ test scope

```java
assertTrue(context.containsBean("fastPort"));
assertEquals(
        PaymentPort.class,
        context.getType("fastPort"));
```

Đây là mảnh minh họa; nếu object là proxy/FactoryBean hoặc metadata không đủ, `getType` có thể khác target class hoặc null. Trong demo cụ thể, factory method return interface và lambda product nên declared type `PaymentPort` có thể được biết.

Không gọi vòng lặp `getBean` trên tất cả definitions chỉ để liệt kê class; điều đó có thể instantiate beans đang lỗi hoặc side-effecting.

## 31. Khi nào nên dùng @MockBean / @MockitoBean?

Đây là điểm dễ nhầm giữa Spring Framework và Boot.

**Spring Framework 6.2:** `spring-test` cung cấp `@MockitoBean` và `@MockitoSpyBean` test bean overrides, tích hợp vào TestContext khi có Mockito dependency. `@TestBean` cũng có sẵn để override bằng factory method.

**Spring Boot:** `@MockBean` từng là annotation đặc thù Boot, không phải annotation có sẵn chỉ vì thêm `spring-context`.

Khi test một consumer cần `PaymentPort`:
- Unit test thuần Java có thể tạo fake trực tiếp, không cần Spring.
- Context integration test có thể dùng `@Bean PaymentPort fakePort()` trong TestConfig.
- Nếu cần mock behavior, dùng `@MockitoBean` trong Spring 6.2 integration tests và thêm Mockito dependencies phù hợp.

Không dùng một annotation chưa có trên classpath khiến code không compile; mục tiêu bài là chẩn đoán wiring lỗi chứ không cần dependency Boot.

## 32. Lộ trình xử lý lỗi theo decision tree

```text
Spring reports UnsatisfiedDependencyException
                    |
                    v
Identify failing bean + InjectionPoint
                    |
                    v
Inspect nested cause chain
            +-------+--------+--------------------+
            |                |                    |
            v                v                    v
     NoSuchBean...      NoUniqueBean...      Other BeanCreation...
            |                |                    |
            v                v                    v
   Registered / scan?  Candidates count?       Follow cause to
   Profile / @Import?  @Primary/Qualifier?      failed leaf bean
   Wrong context?      Generic/name rules?      Type/conversion/cycle?
            |                |                    |
            +----------------+--------------------+
                             |
                             v
            Make smallest correct config change
                             |
                             v
                   Rerun isolated test
                             |
                             v
                  Verify behavior + contract
```

Nếu cause không thuộc hai loại candidate errors, không cố gán nhãn thiếu provider. Ví dụ failure có thể là constructor của provider ném `SQLException` hoặc circular reference khiến bean không tạo được.

## 33. Checklist code review tránh lỗi phụ thuộc

1. Constructor dependencies bắt buộc được đăng ký trong mọi deployment profile quan trọng.
2. Có annotation `@Component`/`@Service` thôi chưa đủ; scan/import phải đúng.
3. Các `@Bean` return types cụ thể và phù hợp injection points.
4. Một injection point cần single bean có selection strategy không?
5. `@Qualifier` names nhất quán với bean naming.
6. `@Primary` chỉ dùng cho default rõ ràng, không đánh dấu tràn lan.
7. Profiles/conditions của test và production được kiểm tra.
8. Có tránh proxy concrete injection khi contract là interface không?
9. Generic types không dùng raw types tùy tiện.
10. Không biến required dependency thành nullable để giấu cấu hình thiếu.
11. Optional provider có fallback behavior được kiểm thử.
12. BeanFactoryPostProcessors không gọi autowired application beans quá sớm.
13. Không có constructor circular dependencies.
14. Cấu hình parent–child contexts rõ nơi sở hữu beans.
15. Tests dùng limited context có TestConfig/fakes đầy đủ.
16. Logs giữ nguyên cause chain và injection point context.
17. Các context fail refresh được đóng, không sử dụng như context khỏe.
18. Integration tests kiểm tra cả failure và repair.
19. Không assert nguyên văn thông báo ngoại lệ nếu không cần.
20. Compiler flags/Java parameter names không vô tình đổi bean selection.

## 34. Câu hỏi luyện tập

**Câu 1:** Có bean `Checkout` cần `PaymentPort`, nhưng `PaymentPort` được tạo trong `@Bean` của configuration class chưa `@Import`. Lỗi gì?

**Trả lời:** Thường `UnsatisfiedDependencyException` tại Checkout injection point, với nested missing candidate. Sửa bằng import/register bean config vào đúng context.

**Câu 2:** Hai beans `FastPort` và `SafePort` đều implement `PaymentPort`. Có phải thêm `@Autowired(required=false)` sẽ tự chọn một bean không?

**Trả lời:** Không. `required=false` không phải rule lựa chọn một trong nhiều candidates; dùng `@Qualifier` hoặc `@Primary` theo contract.

**Câu 3:** `@Profile("prod")` provider có trong source code nhưng test chỉ bật `test`. Vì sao không inject?

**Trả lời:** Definition không được register dưới active profile hiện tại. Cần test config/provider riêng hoặc bật đúng profile trước context initialization.

**Câu 4:** `getBean(PaymentPort.class)` ném `NoUniqueBeanDefinitionException`. Sao app log lại có `UnsatisfiedDependencyException`?

**Trả lời:** Vì khi lỗi phát sinh trong quá trình tạo bean consumer, Spring thêm context injection point bằng wrapper `UnsatisfiedDependencyException`. Truy vấn độc lập ở ngoài không cần wrapper đó.

**Câu 5:** Một `ObjectProvider<PaymentPort>` có làm missing payment port tự tồn tại không?

**Trả lời:** Không. Handle có thể được inject, nhưng khi `getObject()` mà không có matching bean, lỗi vẫn xuất hiện. `getIfAvailable()` chỉ phù hợp nếu dependency thật sự optional.

**Câu 6:** Tại sao có bean class triển khai `PaymentPort` nhưng injection theo `PaymentAdapter` vẫn fail?

**Trả lời:** Có thể object exposed là JDK proxy chỉ implements `PaymentPort`. Cần injection theo interface hoặc class proxy đúng mục đích; scan/class annotations không thay đổi Java assignability.

## 35. Tài liệu chính thức và nguồn đối chiếu

1. [Spring Framework 6.2.18 Javadoc — UnsatisfiedDependencyException](https://www.springframework.org/spring/docs/6.2.18/javadoc-api/org/springframework/beans/factory/UnsatisfiedDependencyException.html): extends `BeanCreationException`, `getInjectionPoint()`, constructors và inherited bean name APIs.
2. [Spring Framework 6.2 Reference — Autowiring Collaborators](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-autowire.html): candidate selection, autowire ambiguity và failure modes.
3. [Spring Framework 6.2 Reference — Using @Autowired](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html): required fields/methods, constructors, Optional và multi-element injection.
4. [Spring Framework 6.2 Reference — Qualifiers](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-qualifiers.html): `@Qualifier` selection.
5. [Spring Framework 6.2 Reference — Fine-tuning Annotation-based Autowiring with @Primary](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-primary.html): candidate priority.
6. [Spring Framework 6.2 Reference — Bean Definition Profiles](https://docs.spring.io/spring-framework/reference/6.2/core/beans/environment.html): `@Profile` and Environment.
7. [Spring Framework 6.2 Reference — Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html): constructor arguments, metadata và selection.
8. [Spring Framework 6.2.18 Javadoc — NoSuchBeanDefinitionException](https://www.springframework.org/spring/docs/6.2.18/javadoc-api/org/springframework/beans/factory/NoSuchBeanDefinitionException.html): missing candidates.
9. [Spring Framework 6.2.18 Javadoc — NoUniqueBeanDefinitionException](https://www.springframework.org/spring/docs/6.2.18/javadoc-api/org/springframework/beans/factory/NoUniqueBeanDefinitionException.html): ambiguous candidates.
10. [Spring Framework 6.2.18 Javadoc — BeanCreationException](https://www.springframework.org/spring/docs/6.2.18/javadoc-api/org/springframework/beans/factory/BeanCreationException.html): nested causes and bean metadata.
11. [Spring Framework 6.2.18 Javadoc — ObjectProvider](https://www.springframework.org/spring/docs/6.2.18/javadoc-api/org/springframework/beans/factory/ObjectProvider.html): lazy/optional resolution.
12. [Spring Framework 6.2 Reference — Spring Test Bean Overriding](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/bean-overriding.html): `@TestBean` và `@MockitoBean` cho integration tests.
13. [JUnit Jupiter 5.11.4 User Guide](https://docs.junit.org/5.11.4/user-guide/): `@Test`, exception assertions và integration test lifecycle.

**Tóm tắt:** `UnsatisfiedDependencyException` là **lỗi tạo bean do một dependency ở injection point không được resolve thành công**. Đừng chỉ tìm một class thiếu `@Component`: hãy đọc **bean name**, **injection point** và **chuỗi nested causes** để phân biệt *không có candidate*, *quá nhiều candidates*, *profile/condition*, *generic/qualifier mismatch*, *proxy type mismatch* và *bean lá tạo lỗi*. Sửa đúng configuration, dùng `@Qualifier`/`@Primary` khi có ambiguity, `ObjectProvider`/`Optional` khi dependency thực sự optional, sau đó xác minh bằng test context độc lập. 


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="troubleshooting-005">Xung đột tên và lỗi BeanDefinitionOverrideException</a>

<details>
<summary>Xem chi tiết</summary>

# Xung đột tên và lỗi BeanDefinitionOverrideException trong Spring Core Container

## 1. Vấn đề: đăng ký hai bean definitions dưới cùng một tên

Spring IoC Container dùng **bean name** như khóa của một bean definition trong registry. Nếu hai nguồn cấu hình cùng muốn đăng ký tên `paymentClient`, container phải quyết định chấp nhận một definition thay thế definition cũ hay từ chối việc đăng ký thứ hai. Khi chính sách **không cho phép override**, trường hợp này thường sinh:

```text
org.springframework.beans.factory.support.BeanDefinitionOverrideException
```

Tình huống minh họa:

```java
@Configuration(proxyBeanMethods = false)
class BillingConfig {
    @Bean("paymentClient")
    PaymentClient billingClient() {
        return new BillingPaymentClient();
    }
}

@Configuration(proxyBeanMethods = false)
class CheckoutConfig {
    @Bean("paymentClient")
    PaymentClient checkoutClient() {
        return new CheckoutPaymentClient();
    }
}
```

Đây là **snippet tích hợp**, cần `PaymentClient` và hai implementations, không phải ứng dụng độc lập. Nếu cả hai configurations hoạt động trong cùng một registry và policy cấm override, hai `@Bean` methods cạnh tranh cho cùng **bean name**. Spring không thể giữ hai definitions với một key chỉ vì Java method names/return types khác nhau.

**Không nhầm ba hiện tượng:**

- **Trùng tên**: hai definitions cùng name `paymentClient` → có khả năng `BeanDefinitionOverrideException`.
- **Trùng type**: có `billingPaymentClient` và `checkoutPaymentClient`, cùng implement `PaymentClient` nhưng name khác → definitions hợp lệ; inject đơn lẻ có thể bị `NoUniqueBeanDefinitionException`.
- **Alias**: `billingClient` và `primaryClient` cùng chỉ tới **một** definition → có thể hợp lệ, không phải hai definitions cùng key.

Bài này hướng dẫn chẩn đoán **đúng stage đăng ký BeanDefinition**, khác với lỗi xảy ra khi resolve một dependency hoặc khởi tạo singleton.

## 2. BeanDefinitionOverrideException là gì?

Tên đầy đủ:

```java
org.springframework.beans.factory.support.BeanDefinitionOverrideException
```

Trong Spring Framework 6.2:

- Class kế thừa `BeanDefinitionStoreException`, có từ **Spring Framework 5.1**.
- Biểu thị một nỗ lực override không hợp lệ; tình huống điển hình là `DefaultListableBeanFactory.registerBeanDefinition(name, newDefinition)` khi name đã có definition và `isAllowBeanDefinitionOverriding()` là false.
- Exception cung cấp metadata của **name bị tranh chấp**, **definition mới** và **definition cũ**.

Các APIs cần đọc:

```java
String getBeanName();
BeanDefinition getBeanDefinition();
BeanDefinition getExistingDefinition();
```

`getBeanDefinition()` là **new definition đang cố đăng ký**; `getExistingDefinition()` là **old definition đã chiếm tên đó**. Khi ghi báo cáo debug, lấy các thuộc tính như bean class name, resource/source, factory bean/method, scope, role, qualifiers nếu metadata có. Không phân tích lỗi chỉ từ exception message vì văn bản thay đổi giữa releases.

### 2.1. Lỗi phát sinh lúc nào?

```text
Đọc XML / @Configuration / @ComponentScan / registrar
       |
       v
BeanDefinitionRegistry.registerBeanDefinition(beanName, beanDefinition)
       |
       +-- Name chưa có: chấp nhận
       |
       +-- Name đã có:
             |
             +-- Cho phép override: thay registration và có thể ghi log
             |
             +-- Cấm override: BeanDefinitionOverrideException
```

Đây là **giai đoạn cấu hình/registration**, thường trong startup/refresh, trước khi các singleton application cần thiết được khởi tạo. Nó không yêu cầu có HTTP request hay gọi business method.

**Không phải mọi va chạm tên đều đi qua đúng đường này.** Component scanning tự kiểm tra candidate definitions và có thể báo `ConflictingBeanDefinitionException`; xử lý đặc biệt giữa scanned `@Component` và `@Bean` cũng có các ngoại lệ.

## 3. Mặc định Spring Framework Core khác Spring Boot

| Môi trường | Chính sách mặc định | Kết luận |
|---|---|---|
| Spring Framework 6.2 `DefaultListableBeanFactory` | `allowBeanDefinitionOverriding=true` | Có thể thay thế definition cũ |
| Spring Framework 6.2 `GenericApplicationContext` | `true` | Delegate tới BeanFactory |
| Spring Framework 6.2 `AnnotationConfigApplicationContext` thuần | `true` | Nhưng component-scan/conflict checks có thể chặn riêng |
| Spring Boot 3.4 thông thường | `spring.main.allow-bean-definition-overriding=false` | Bean name override bị chặn |
| Boot có explicit setting true | `true` | Cho phép hơn, nhưng không loại mọi loại conflict |
| Custom host | Phải kiểm tra | Host có thể override factory policy |

Theo tài liệu Spring Framework 6.2, overriding khiến cấu hình khó đọc và được **khuyến cáo tránh trong production**. Bản 6.2 log các lần override ở mức INFO theo mặc định, trừ một số ngoại lệ Java configuration. Tài liệu còn cảnh báo khả năng deprecated overriding trong tương lai.

### 3.1. Spring Core API

```java
DefaultListableBeanFactory factory =
        new DefaultListableBeanFactory();

factory.setAllowBeanDefinitionOverriding(false);
```

Snippet cần import `org.springframework.beans.factory.support.DefaultListableBeanFactory`. Dùng khi tự bootstrapping BeanFactory và muốn detect duplicate definitions sớm.

Với `AnnotationConfigApplicationContext`:

```java
var context = new AnnotationConfigApplicationContext();

context.setAllowBeanDefinitionOverriding(false);
context.register(AppConfig.class);
context.refresh();
```

Đây là snippet tích hợp, cần class `AppConfig` và đóng context sau khi dùng. **Đặt flag trước `refresh()`**, không gọi constructor `new AnnotationConfigApplicationContext(AppConfig.class)` đã tự refresh rồi mới đổi flag.

### 3.2. Spring Boot application.properties

```properties
spring.main.allow-bean-definition-overriding=true
```

hoặc trong application.yml:

```yaml
spring:
  main:
    allow-bean-definition-overriding: true
```

Đây là **Boot bootstrap property**, không phải flag mà plain BeanFactory tự đọc từ `application.properties`. Đặt trong nguồn được Spring Boot đọc đủ sớm. Khi biết lỗi, ưu tiên **đổi tên/loại definition trùng**, không khuyến nghị cứ bật true trong production.

### 3.3. Không nhầm với allowCircularReferences

Hai flags độc lập:

```properties
spring.main.allow-bean-definition-overriding=false
spring.main.allow-circular-references=false
```

Cờ đầu kiểm soát **đăng ký BeanDefinition trùng tên**. Cờ sau kiểm soát **circular dependencies trong bean creation**. Đổi `allow-circular-references` không giải quyết `BeanDefinitionOverrideException`.

### 3.4. Sửa flag sau khi lỗi xảy ra không giải quyết startup đã fail

Đăng ký definition diễn ra **trước khi có các bean instances của application**. Vì vậy một `ApplicationRunner`, `CommandLineRunner` hoặc `@PostConstruct` không thể sửa flag kịp khi context đã thất bại trong registration phase. Đưa cấu hình vào đúng bootstrap path, hoặc tốt hơn sửa chính definitions bị trùng.

## 4. Nguyên nhân 1: nhiều @Bean methods dùng cùng default name

`@Bean` mặc định đặt tên bean theo **tên method** khi không khai báo name/value.

```java
@Configuration(proxyBeanMethods = false)
class SearchConfig {
    @Bean
    Client apiClient() {
        return new SearchClient();
    }
}

@Configuration(proxyBeanMethods = false)
class AnalyticsConfig {
    @Bean
    Client apiClient() {
        return new AnalyticsClient();
    }
}
```

Đây là snippet minh họa: cần interface `Client` và implementations. Dù hai factory methods nằm ở hai classes khác nhau, cùng tên `apiClient` được đăng ký vào **một** BeanFactory có thể gây override.

**Sửa:**

```java
@Bean("searchApiClient")
Client searchClient() { return new SearchClient(); }

@Bean("analyticsApiClient")
Client analyticsClient() { return new AnalyticsClient(); }
```

Hai methods thuộc configuration classes tương ứng; code là snippet, chưa độc lập. Sau khi names khác nhau, nếu một constructor yêu cầu `Client` mà cả hai cùng type, có thể cần `@Qualifier("searchApiClient")` hoặc một primary candidate.

**Nguyên tắc:** tên method Java và bean name mặc định thường trùng, nhưng có thể khác khi dùng `@Bean("explicitName")`.

## 5. Nguyên nhân 2: @ComponentScan sinh trùng tên từ two packages

Ví dụ:

```text
com.acme.billing.UserService
com.acme.shipping.UserService
```

Nếu cả hai annotated `@Service`, default `AnnotationBeanNameGenerator` dùng short class name theo JavaBeans decapitalize, cho cả hai tên `userService`. Scanner có thể ném:

```text
org.springframework.context.annotation.ConflictingBeanDefinitionException
```

Lỗi này **không phải luôn là `BeanDefinitionOverrideException`**; scanner có kiểm tra riêng `checkCandidate` đối với các discovered classes không tương thích.

### 5.1. Đổi explicit component names

Trong package billing:

```java
@Service("billingUserService")
public class UserService {
}
```

Trong package shipping:

```java
@Service("shippingUserService")
public class UserService {
}
```

Đây là hai **files ở packages khác nhau**, với import `org.springframework.stereotype.Service`. Tên mới thể hiện domain, tránh va chạm ngẫu nhiên.

### 5.2. FullyQualifiedAnnotationBeanNameGenerator

Nếu codebase có nhiều classes khác packages nhưng cùng simple name, cân nhắc:

```java
@ComponentScan(
    basePackages = "com.acme",
    nameGenerator =
        FullyQualifiedAnnotationBeanNameGenerator.class)
```

Snippet đặt trên configuration class, cần imports `ComponentScan`, `FullyQualifiedAnnotationBeanNameGenerator`. Khi không có explicit annotation name, generated bean name có thể là fully qualified class name. **Không sửa được trường hợp cả hai `@Service("shared")` đã khai báo explicit cùng tên.**

### 5.3. Phạm vi scan cần có chủ đích

Một `@ComponentScan("com")` quá rộng có thể kéo vào sample, test, legacy package hoặc hai modules khác nhau. Không nên dựa vào override để chọn “component được scan sau”. Giới hạn `basePackages`, filters hoặc tách module boundaries, nhưng đừng chỉ loại bỏ một component mà business logic thực sự cần.

## 6. Nguyên nhân 3: scanned component và @Bean có cùng tên

Ví dụ:

```java
@Component("gateway")
class Gateway {
}

@Configuration(proxyBeanMethods = false)
class GatewayConfig {
    @Bean("gateway")
    Gateway gateway() {
        return new Gateway();
    }
}
```

Đây là snippet tích hợp. Cần nhớ **một ngoại lệ quan trọng**: theo Spring Framework 6.2 Reference, một `@Bean` method có thể thay thế **scanned bean** cùng name khi declared return type đúng bean class đã scan; Spring có đường xử lý riêng cho tình huống này, kể cả khi general overriding bị disable.

Vì vậy không kết luận máy móc: “hễ scanned @Component và @Bean trùng name thì luôn có BeanDefinitionOverrideException”.

Tuy nhiên về kiến trúc, vẫn phải hỏi: **Ai sở hữu việc tạo bean?** Nếu chọn constructor/bean factory custom, có thể bỏ `@Component` trên class và chỉ dùng `@Bean`; nếu chọn component scan, không đăng ký `@Bean` lần nữa. Cách này dễ hiểu hơn dù Spring cho phép đường thay thế đặc biệt.

## 7. Nguyên nhân 4: @Configuration, @Import và XML trùng definition

Một application có thể nạp definitions qua nhiều đường:

```text
@ComponentScan(...)
@Import(SomeConfiguration.class)
@ImportResource("classpath:/beans.xml")
ImportBeanDefinitionRegistrar
BeanDefinitionRegistryPostProcessor
Library auto-configuration
Test configuration
```

Khi có bean name `sharedClock` từ XML và cả Java Config, registry vẫn chỉ có một key `sharedClock`.

```xml
<bean id="sharedClock" class="com.acme.SystemClock"/>
```

và:

```java
@Bean("sharedClock")
Clock configuredClock() {
    return Clock.systemUTC();
}
```

Đây là snippets tích hợp; `SystemClock` phải tồn tại trong ứng dụng nếu dùng XML. Ngoài lớp user code, một imported library starter hoặc module `@Configuration` có thể âm thầm đăng ký cùng name.

**Lưu ý:** import một configuration class qua hai đường không nhất thiết sinh hai definitions, vì Spring có duplicate detection cho nhiều trường hợp. Debug theo actual registry state và source metadata, không đoán từ số imports.

### 7.1. Overloaded @Bean methods và enforceUniqueMethods

Trong Spring 6.2, `@Configuration(enforceUniqueMethods=true)` mặc định yêu cầu `@Bean` methods không overload cùng tên theo cách dễ gây mơ hồ, kể từ thế hệ Spring 6.0. Đừng dùng overloaded `@Bean` methods để biểu diễn hai variants thay cho explicit bean names.

```java
@Bean
Service service() { /* ... */ }

@Bean
Service service(Dependency d) { /* ... */ }
```

Đây là anti-example, các bodies chưa độc lập. Overloading factory methods có semantics riêng và không đồng nghĩa tạo hai distinct named beans an toàn. Dùng method names hoặc `@Bean("...")` tường minh.

## 8. Nguyên nhân 5: điều kiện @Profile và @Conditional cùng match

`@Profile("dev")` và `@Profile("test")` không loại trừ nhau, vì Spring có thể kích hoạt **nhiều active profiles**.

```java
@Bean("gateway")
@Profile("dev")
Gateway developmentGateway() {
    return new MockGateway();
}

@Bean("gateway")
@Profile("test")
Gateway testGateway() {
    return new TestGateway();
}
```

Đây là snippets tích hợp trong configuration class và cần types tương ứng. Khi cả dev và test active, hai methods có thể cùng đăng ký name `gateway`.

Một số cách sửa:

- Chọn profiles/conditions thực sự mutually exclusive ở layer cấu hình.
- Dùng hai names (`devGateway` và `testGateway`) rồi chọn candidate theo contract.
- Dùng một `@Bean` method, chọn implementation dựa vào một configuration value đã được validate.
- Đừng đặt một `@Conditional` phụ thuộc trạng thái runtime không tái lập như external service health check.

Trong Spring Boot, `@ConditionalOnMissingBean` là công cụ để library auto-configuration nhường quyền cho user bean khi appropriate; **nó không thuộc core spring-context**. Conditional evaluation theo registered definitions và ordering vẫn quan trọng.

## 9. Alias collision cũng cần điều tra

Một definition có thể có nhiều aliases:

```java
registry.registerAlias("paymentClient", "primaryPaymentClient");
```

Đây là snippet registry, cần `registry` là alias-capable registry. Cả hai tên có thể resolve về cùng canonical bean definition. Alias registration khác với đăng ký hai BeanDefinitions.

Tuy nhiên, alias collisions/cycles và việc alias chiếm một bean name đã tồn tại cũng có rules. Javadoc `DefaultListableBeanFactory.setAllowBeanDefinitionOverriding` cho biết policy này **cũng liên quan alias overriding**. Không suy rằng chỉ có `registerBeanDefinition` mới bị ảnh hưởng.

Nếu log liên quan aliases, inspect canonical name và alias mappings, không mù quáng đổi một `@Bean` method chưa xác định. Tránh alias chains dài khiến team khó hiểu ownership.

## 10. Parent-child context: trùng tên nhưng khác registry

Có hai factories riêng:

```text
Parent BeanFactory
  paymentClient -> ParentPaymentClient

Child BeanFactory (parent=Parent BeanFactory)
  paymentClient -> ChildPaymentClient
```

Lookup `child.getBean("paymentClient")` thường thấy **local child definition** trước; lookup `parent.getBean("paymentClient")` thấy parent definition. Đây là **name shadowing trong hierarchy**, không phải hai registrations cùng name trong một BeanDefinitionRegistry.

Vì vậy, `child.setAllowBeanDefinitionOverriding(false)` **không nhất thiết ngăn** đăng ký `paymentClient` trong child nếu parent có cùng tên. Flag kiểm soát override **local registry**, không mặc định buộc unique bean names qua mọi ancestor context.

Cần nhớ:

- Child có thể thấy parent beans qua delegate lookup.
- Parent thường không nhìn thấy child beans.
- Parent/child có lifecycle ownership riêng.
- Child local bean che parent trên name lookup, nhưng parent singleton vẫn tồn tại trong parent.
- By-type resolution và visibility/hierarchy có nuances; test thực tế khi thiết kế module boundaries.

## 11. Demo Java 17 + Spring Framework 6.2: tái hiện và sửa lỗi thật

Chương trình Maven độc lập sau minh họa **bốn kết quả xác định**:

1. Default của `DefaultListableBeanFactory` là cho phép overriding.
2. Khi đặt `false`, đăng ký `Beta` với cùng name `taskService` bị chặn bằng `BeanDefinitionOverrideException`; đọc được **cả new và existing definition**.
3. Khi đặt `true`, definition `Beta` thay thế definition `Alpha` và lookup trả instance Beta.
4. Hai factories cha/con có local bean cùng tên nhưng không gây same-registry override.

Chương trình **không dùng Boot**, không cần annotation scanning hoặc servlet, vì mục tiêu là cô lập đúng registry behavior.

### 11.1. Cấu trúc project

```text
spring-override-demo/
├── pom.xml
└── src/main/java/example/BeanOverrideDemo.java
```

Yêu cầu Java 17+ và Maven 3.9+.

### 11.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-override-demo</artifactId>
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

### 11.3. src/main/java/example/BeanOverrideDemo.java

```java
package example;

import org.springframework.beans.factory.support.BeanDefinitionOverrideException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

public final class BeanOverrideDemo {

    public static void main(String[] args) {
        DefaultListableBeanFactory defaults = new DefaultListableBeanFactory();
        System.out.println(
                "CORE_DEFAULT=" + defaults.isAllowBeanDefinitionOverriding());

        blockedOverride();
        allowedOverride();
        hierarchyShadowing();
    }

    private static void blockedOverride() {
        var factory = new DefaultListableBeanFactory();
        factory.setAllowBeanDefinitionOverriding(false);

        factory.registerBeanDefinition(
                "taskService", new RootBeanDefinition(Alpha.class));

        try {
            factory.registerBeanDefinition(
                    "taskService", new RootBeanDefinition(Beta.class));
            throw new AssertionError("Expected override to be blocked");
        } catch (BeanDefinitionOverrideException ex) {
            boolean namesCorrect = "taskService".equals(ex.getBeanName());
            boolean oldCorrect =
                    Alpha.class.getName().equals(
                            ex.getExistingDefinition().getBeanClassName());
            boolean newCorrect =
                    Beta.class.getName().equals(
                            ex.getBeanDefinition().getBeanClassName());

            System.out.println("BLOCKED_NAME=" + namesCorrect);
            System.out.println("OLD_DEFINITION=" + oldCorrect);
            System.out.println("NEW_DEFINITION=" + newCorrect);
        }

        // Registration cũ vẫn còn sau lần override bị từ chối.
        boolean preserved = Alpha.class.getName().equals(
                factory.getBeanDefinition("taskService").getBeanClassName());
        System.out.println("OLD_PRESERVED=" + preserved);
    }

    private static void allowedOverride() {
        var factory = new DefaultListableBeanFactory();
        factory.setAllowBeanDefinitionOverriding(true);

        factory.registerBeanDefinition(
                "taskService", new RootBeanDefinition(Alpha.class));
        factory.registerBeanDefinition(
                "taskService", new RootBeanDefinition(Beta.class));

        boolean betaSelected =
                factory.getBean("taskService").getClass() == Beta.class;

        System.out.println("ALLOWED_BETA=" + betaSelected);
    }

    private static void hierarchyShadowing() {
        var parent = new DefaultListableBeanFactory();
        parent.setAllowBeanDefinitionOverriding(false);
        parent.registerBeanDefinition(
                "taskService", new RootBeanDefinition(Alpha.class));

        var child = new DefaultListableBeanFactory(parent);
        child.setAllowBeanDefinitionOverriding(false);
        child.registerBeanDefinition(
                "taskService", new RootBeanDefinition(Beta.class));

        System.out.println(
                "PARENT=" + parent.getBean("taskService").getClass().getSimpleName());
        System.out.println(
                "CHILD=" + child.getBean("taskService").getClass().getSimpleName());
    }

    public static final class Alpha {
        public Alpha() {
        }
    }

    public static final class Beta {
        public Beta() {
        }
    }
}
```

### 11.4. Chạy

Tại thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.BeanOverrideDemo
```

Các dòng stdout **do code tự in**, không bao gồm logs của Spring/Maven:

```text
CORE_DEFAULT=true
BLOCKED_NAME=true
OLD_DEFINITION=true
NEW_DEFINITION=true
OLD_PRESERVED=true
ALLOWED_BETA=true
PARENT=Alpha
CHILD=Beta
```

### 11.5. Vì sao kết quả như vậy?

**CORE_DEFAULT=true:** Spring Framework 6.2 `DefaultListableBeanFactory` có override policy mặc định true. Một `SpringApplication` trong Boot có thể đặt nó false; demo dùng factory thuần nên nhìn đúng Core default.

**BLOCKED_NAME=true:** Factory riêng được đặt false; việc đăng ký lần hai với cùng name sinh `BeanDefinitionOverrideException`. `ex.getBeanName()` bằng `"taskService"`.

**OLD_DEFINITION/NEW_DEFINITION=true:** Getter cung cấp metadata để phân biệt Alpha đã được đăng ký với Beta là request mới. Code so sánh **bean class name** chứ không parse exception message.

**OLD_PRESERVED=true:** Override bị từ chối nên registry vẫn giữ Alpha. Không sinh một instance Beta vì `registerBeanDefinition` chỉ ghi metadata, chưa instantiate.

**ALLOWED_BETA=true:** Factory khác được đặt true. RootBeanDefinition(Beta.class) thay definition Alpha; `getBean("taskService")` lần đầu tạo Beta. Đừng dựa vào “bean cuối thắng” trong business production code.

**PARENT=Alpha và CHILD=Beta:** Dù cả hai factories cấm local override, child được quyền có local definition tên `taskService` vì đó là registry riêng. Name lookup trên child ưu tiên local Beta, parent giữ Alpha.

### 11.6. Tại sao ví dụ chỉ dùng BeanDefinitionRegistry?

Nếu dùng `@Configuration` và component scanning, Spring có những rules bổ sung cho scanned candidates, `@Bean` method handling và alias registration. Demo đi thẳng vào `registerBeanDefinition` để chứng minh **chính xác semantics của exception**. Sau khi hiểu registry, hãy mở rộng integration tests với configuration sources thật của ứng dụng.

### 11.7. Cảnh báo khi override một singleton đã được tạo

Demo gọi `getBean` **sau khi** chọn final definition. Nếu bạn thay definition **sau khi singleton instance đã được tạo**, Spring có cache invalidation/reset logic, nhưng các callers đang giữ **reference cũ** có thể không tự cập nhật. Đừng biến definition overriding thành runtime hot-swap mechanism. Container configuration nên hoàn tất trước regular bean creation/refresh.

## 12. Debug trong một ApplicationContext thực tế

Khi thấy lỗi, đừng chỉ chụp 3 dòng cuối của stack trace. Hãy thu thập:

1. **Tên bean**: `getBeanName()`.
2. **Existing definition**: bean class, factory method, resource description/source.
3. **Attempted definition**: những metadata tương ứng.
4. **Đường đăng ký**: component scan, `@Bean`, XML, `@Import`, registrar, library auto-config.
5. **Container owner**: factory nào trong context hierarchy?
6. **Effective policy**: override flag true/false, được Boot/host đặt khi nào?
7. **Profiles và conditions**: vì sao cả hai definitions được chọn?
8. **Classpath**: có bị load trùng starter/module, tests/configuration ngoài ý muốn?
9. **Runtime version**: Spring Core/Boot patch đúng với docs và log?
10. **Sửa root cause**: bỏ duplication hoặc đổi name/selector, không che lỗi.

### 12.1. In metadata từ exception

```java
try {
    context.refresh();
} catch (BeanDefinitionOverrideException ex) {
    System.err.println("bean=" + ex.getBeanName());
    System.err.println("new=" + ex.getBeanDefinition());
    System.err.println("existing=" + ex.getExistingDefinition());
    throw ex;
}
```

Đây là snippet tích hợp, cần `context` được tạo trước, import exception class, và cleanup context ở finally. Nó cho thông tin tốt hơn so với grep ngẫu nhiên theo `@Bean` method name. Không catch rồi nuốt exception để một application context lỗi vẫn tiếp tục chạy.

### 12.2. Đọc BeanDefinition trước refresh nếu có thể

Khi có quyền đăng ký programmatically:

```java
if (registry.containsBeanDefinition("paymentClient")) {
    BeanDefinition previous =
            registry.getBeanDefinition("paymentClient");
    // Báo rõ source và mục tiêu thay vì ghi đè ngầm.
}
```

Đây là snippet integration, cần `BeanDefinitionRegistry` và `BeanDefinition` imports. Nếu code chạy trong `BeanDefinitionRegistryPostProcessor`, bạn có thể inspect metadata cùng registration phase. Nhưng một check rồi register vẫn không thay **registry's atomic policy** trong concurrent mutation scenarios; để exception thật quyết định, và tránh registration có race.

### 12.3. Diagnostics từ resource/source

`BeanDefinition.getResourceDescription()` có thể trỏ tới classpath resource, file XML hoặc configuration method. `getSource()` có thể chứa metadata của parse source, tùy generator/reader. Không mặc định mọi definition có resource description khác null.

Trong error report, ghi cả new/existing source nếu có; nếu null, dùng bean class/factory method metadata và registration trace từ application setup.

### 12.4. BeanDefinition không phải instance

Một `RootBeanDefinition(Beta.class)` tồn tại không có nghĩa `new Beta()` đã chạy. Đăng ký/overriding xảy ra ở metadata phase; `getBean` hoặc eager singleton initialization mới tạo instance theo scope. Chẩn đoán memory leaks do application instance phải xem giai đoạn khác, không chỉ counting definitions.

## 13. Thực hiện sửa lỗi: chọn đúng loại giải pháp

### 13.1. Hai beans đều cần được giữ

Nếu có PaymentGateway của hai nhà cung cấp, dùng **hai names độc lập**:

```java
@Bean("stripeGateway")
Gateway stripeGateway() {
    return new StripeGateway();
}

@Bean("paypalGateway")
Gateway paypalGateway() {
    return new PaypalGateway();
}
```

Khi inject:

```java
public CheckoutService(
        @Qualifier("stripeGateway") Gateway gateway) {
    this.gateway = gateway;
}
```

Đây là snippets tích hợp, cần interface/types/imports. Sau đổi tên, nhiều candidates cùng `Gateway` có thể coexist. `@Qualifier` mới là công cụ **chọn candidate**, không phải sửa registration duplicate trực tiếp.

### 13.2. Chỉ một bean nên tồn tại

Nếu dev/prod muốn implementation thay thế nhau, viết conditions **mutually exclusive** hoặc một factory method quyết định implementation với explicit config:

```java
@Bean("gateway")
Gateway gateway(Environment env) {
    String mode = env.getRequiredProperty("app.gateway.mode");
    return switch (mode) {
        case "stub" -> new StubGateway();
        case "live" -> new LiveGateway();
        default -> throw new IllegalArgumentException(
                "Unsupported gateway mode: " + mode);
    };
}
```

Đây là Java 17 snippet tích hợp, cần imports và implementations. Một BeanDefinition name duy nhất được đăng ký, nên không còn hai definitions tranh chấp. Nếu muốn ứng dụng fail sớm khi config sai, validate property và lưu trace rõ. **Không nhầm lựa chọn object trong factory method với BeanDefinition override.**

### 13.3. Application muốn thay auto-configured default của library

Trong Spring Boot, tự cấu hình library có thể khai báo `@ConditionalOnMissingBean` trên default bean. Cách này cho application tự đăng ký bean của mình và Boot auto-config **back off** khi điều kiện đúng.

```java
@Bean
@ConditionalOnMissingBean(Gateway.class)
Gateway defaultGateway() {
    return new DefaultGateway();
}
```

Đây là **Boot-only snippet**, không thuộc `spring-context` thuần. Conditional result tùy vào definitions đã processed và ordering; không phải lý do để override mọi BeanDefinitions đang tồn tại. Với library integration, xem official extension hooks và Boot auto-configuration ordering.

### 13.4. Test cần thay một bean bằng mock

Spring Framework 6.2 TestContext có cơ chế test bean overriding chính thức:

- `@TestBean`: cung cấp replacement bean bằng static factory method trong test.
- `@MockitoBean`/`@MockitoSpyBean`: mock hoặc spy integration qua Mockito.

Điều này được thiết kế để giảm rủi ro so với việc bật BeanFactory's general overriding true cho cả project. Nếu đang dùng Spring Boot test ở một version trước, namespace/annotations có thể khác; đối chiếu docs tương ứng thay vì copy nguyên import.

### 13.5. Khi nào có thể bật flag true?

Một số migration legacy hoặc plugin ecosystems cố ý muốn last-registration-wins. Khi buộc phải dùng:

1. Ghi rationale và owner.
2. Giới hạn nơi cấu hình (test hoặc migration).
3. Ghi tên bean, old/new definition và thứ tự được đảm bảo.
4. Assert bean class/config cuối cùng đúng ý định.
5. Kiểm tra AOP, @Transactional, security, datasource, properties/caching.
6. Thêm regression test để phát hiện accidental new overrides.
7. Lập kế hoạch loại bỏ workaround nếu muốn nâng framework lớn.
8. Không áp true như default mới của tất cả microservices.

Trong production mới, **fail-fast** thường an toàn hơn.

## 14. Thứ tự registration và rủi ro khi cho phép override

Khi flag true, một new definition thay previous definition theo registration path. Nhưng **registration order** có thể đổi do:

- Component scanning order, module/classpath layout.
- `@Import` và configuration class parsing.
- `BeanDefinitionRegistryPostProcessor` ordering.
- Spring Boot auto-configuration ordering và conditions.
- Gradle/Maven dependency changes.
- Môi trường/profiles chọn các configuration sets khác nhau.
- Test-specific configurations và context caching.

Đừng lấy kết quả “dev machine thấy Beta thắng” làm contract rằng prod cũng luôn Beta. Đặc biệt, nếu hai definitions cùng tên nhưng **type khác nhau**, việc override âm thầm có thể làm injections/qualifiers không còn hợp lệ hoặc phá AOP proxy assumptions.

### 14.1. Type safety sau override

Nếu một component yêu cầu `PaymentClient` cụ thể nhưng name `paymentClient` bị override bởi class không implement type đó, việc tạo phụ thuộc có thể ném `BeanNotOfRequiredTypeException` hoặc lỗi dependency matching sau registration. Đây là **lỗi tiếp theo**, không chứng minh override là safe.

### 14.2. Bean scopes/lifecycle metadata cũng có thể bị thay

Override BeanDefinition không chỉ thay class; scope, lazy init, factory method, init/destroy method, @Primary/qualifier metadata và autowire candidacy cũng có thể đổi. Một singleton service có thể bị thay bằng prototype hoặc ngược lại, dẫn đến behavior khó phát hiện. So sánh **full metadata**, không chỉ class name.

## 15. Liên hệ với @Primary, @Qualifier và @Fallback

Những annotation này giải bài toán **candidate resolution**, không giải duplicate registration:

| Công cụ | Tác dụng chính | Sửa cùng bean name? |
|---|---|---|
| `@Primary` | Ưu tiên một bean khi nhiều candidates cùng type | **Không** |
| `@Qualifier` | Thu hẹp candidates theo qualifier/name | **Không** |
| `@Fallback` (Spring 6.2+) | Đánh dấu một candidate là phương án phụ | **Không** |
| `@Bean("uniqueName")` | Đặt bean name tường minh | **Có thể sửa** duplicate name |
| `@Component("uniqueName")` | Đặt scanned bean name tường minh | **Có thể sửa** duplicate name |
| `@ConditionalOnMissingBean` (Boot) | Chỉ đăng ký default khi thiếu candidate thích hợp | Có thể tránh đăng ký xung đột trong auto-config |
| `allowBeanDefinitionOverriding=true` | Cho phép replacement registration | Che conflict, **không tạo hai beans** |

Khi đã đổi names, nếu có hai `PaymentClient` bean definitions, injection một `PaymentClient` có thể cần `@Qualifier` hoặc `@Primary`. Đó là bước **sau** khi registration conflict đã được xử lý.

## 16. Test để bắt duplicate registration sớm

### 16.1. Programmatic registry test

```java
var factory = new DefaultListableBeanFactory();
factory.setAllowBeanDefinitionOverriding(false);
factory.registerBeanDefinition(
        "gateway", new RootBeanDefinition(DefaultGateway.class));

assertThrows(
        BeanDefinitionOverrideException.class,
        () -> factory.registerBeanDefinition(
                "gateway", new RootBeanDefinition(CustomGateway.class)));
```

Đây là **JUnit 5-style snippet** cần imports/test dependency và classes Gateway. Test này tách đúng registry behavior khỏi component scanning.

### 16.2. Context refresh test

```java
var context = new AnnotationConfigApplicationContext();
context.setAllowBeanDefinitionOverriding(false);
context.register(FirstConfig.class, SecondConfig.class);

assertThrows(BeanDefinitionOverrideException.class, context::refresh);
context.close();
```

Đây là **JUnit-style snippet** với giả định hai @Configuration classes đăng ký `@Bean` cùng name theo đường gây đúng exception. Nếu `refresh()` wrap exception hoặc configuration parser xử lý special case, assert root cause hoặc kiểm tra actual reported subtype thay vì giả định mọi configuration source đi cùng error path. Tách context cho mỗi test.

### 16.3. Application Boot test

Đặt `spring.main.allow-bean-definition-overriding=false` trong test bootstrap cùng profiles/conditions với production. Assert startup fail khi duplicate definitions được đưa vào và startup pass khi fixed.

**Đừng chỉ test app loads** nếu Boot test context có profiles khác production; collision có thể chỉ xuất hiện khi đồng thời active hai configuration modules.

### 16.4. Test source của scanned components

Nếu project có hai `@Service` classes với same short name, dùng component scanning integration test để xác nhận collision và test name generator/explicit name fix. **Không bắt `BeanDefinitionOverrideException` cứng** nếu scanner báo `ConflictingBeanDefinitionException`; phân biệt rõ class trong test output.

### 16.5. Test hierarchy

Khởi tạo parent/child `DefaultListableBeanFactory` như demo và assert local child lookup trả Beta trong khi parent vẫn Alpha. Đây là test cho **shadowing**, không phải permitted local override.

## 17. Checklist chẩn đoán từng bước

Khi nhận stack trace về duplicate bean name:

1. Đọc **bean name** bị tranh chấp, không chỉ loại exception.
2. Đọc **existing definition** và **new definition**.
3. Xác định registry/context nơi lỗi xảy ra.
4. Kiểm tra effective `allowBeanDefinitionOverriding`.
5. Ghi version Spring Framework/Boot và BOM.
6. Tìm declarations `@Bean("name")`, methods tên `name`, `@Component("name")`, `@Service("name")`.
7. Tìm generated name từ short class names ở hai packages.
8. Kiểm tra `@Import`, XML imports và custom registrars.
9. Kiểm tra Boot starters/auto-configurations bị include trùng hoặc đang không back off.
10. Kiểm tra `@Profile`/`@Conditional` có đồng thời match không.
11. Kiểm tra bean source/resource descriptions.
12. Xem đây là component-scan conflict hay registry override exception.
13. Nếu hai beans đều cần: **đặt hai names duy nhất**, sau đó chọn candidate bằng qualifier/primary.
14. Nếu chỉ một bean cần: loại source registration trùng hoặc viết mutually exclusive condition.
15. Nếu test cần mock: dùng Spring TestContext bean override tooling thay vì production flag.
16. Chạy tests với override disabled để bắt conflict mới.
17. Khi tạm bật override, xác nhận final bean type, scope, lifecycle và AOP behavior.
18. Không kết luận fix thành công chỉ vì app startup pass; kiểm tra đúng implementation được inject.

## 18. Các sai lầm phổ biến

| Sai lầm | Vì sao sai | Cách sửa |
|---|---|---|
| Bật `@Primary` trên một definition trùng name | Primary không điều khiển registry key | Đổi bean names |
| Dùng `@Qualifier` nhưng context vẫn fail | Qualifier chỉ dùng khi resolve candidates | Sửa registration conflict trước |
| Bật `allow-circular-references` | Sai giai đoạn lỗi | Kiểm tra đúng override flag |
| Bật override globally ở prod | Có thể vô tình thay datasource/security beans | Fail-fast/đổi tên |
| Cho rằng Spring Core và Boot default giống nhau | Core 6.2 true, Boot 3.4 false | Xác minh effective policy |
| Cho rằng Core true bỏ qua mọi scan conflicts | Scanner có `ConflictingBeanDefinitionException` riêng | Explicit names/generator |
| Đặt `@Profile("dev")` và `@Profile("test")` cùng name | Hai profiles có thể active cùng lúc | Mutual exclusion |
| Scan toàn bộ dependency packages | Kéo component/config không chủ đích | Giới hạn scan scope |
| Lấy output của last-registry-wins làm contract | Registration order có thể đổi | Cấu hình deterministic |
| Coi alias là definition thứ hai | Alias chỉ thêm tên tới canonical bean | Kiểm tra registry alias mapping |
| Coi child bean trùng parent là local override | Hai registries riêng | Phân biệt shadowing |
| Chỉ log exception message rồi đoán class | Metadata new/existing có sẵn | Dùng exception getters |
| Sửa flag trong ApplicationRunner | Registration đã diễn ra | Sửa trước refresh/bootstrap |
| Dùng @Bean method overloaded cùng tên làm alternatives | Factory method resolution semantics khó kiểm soát | Đặt names/methods riêng |
| Override một already-instantiated bean như hot-swap | Existing references có thể giữ old object | Thiết kế lifecycle rõ |
| Để test configuration lọt production | Tên mock có thể ghi đè | Test scope/import isolation |
| Đọc docs mới nhất cho runtime cũ | Override rules khác patch/version | Pin Spring docs release |

## 19. Best practices cho thiết kế bean naming

- Dùng bean names rõ domain khi có nhiều implementations của cùng loại service.
- Giữ chỉ một cơ chế registration làm **source of truth** cho một bean quan trọng (component scan hoặc @Bean explicit).
- Ưu tiên constructor injection và explicit qualifiers khi có nhiều candidates cùng interface.
- Sử dụng `FullyQualifiedAnnotationBeanNameGenerator` khi nhiều packages có classes trùng simple names, nhưng nhớ explicit names vẫn có thể conflict.
- Giới hạn component scan theo module boundaries.
- Đặt `@Profile`/`@Conditional` như một selection policy được test, không coi chúng mặc định loại trừ nhau.
- Dùng `@ConditionalOnMissingBean` theo đúng Boot auto-configuration semantics nếu là library providing defaults.
- Giữ `allowBeanDefinitionOverriding=false` trong ứng dụng muốn fail-fast; ngay cả khi Spring Core default true, có thể đặt false tường minh ở context bootstrap.
- Nếu dự án legacy cần override, lập bảng những override **cố ý** và test chính xác final bean metadata.
- Tách integration tests cho registry collision, scan collision, type ambiguity và hierarchy shadowing.
- Không thay bean names đã public cho các modules khác mà không sửa `@Qualifier` và named lookups.
- Với AOT, bean graph có thể được chọn tại build time, nên phải kiểm tra collisions với profiles/conditions **của AOT build**.
- Đọc nguồn official của đúng Spring patch khi xử lý ngoại lệ Java config/scanned bean.

## 20. Câu hỏi tự kiểm tra

**1. BeanDefinitionOverrideException thể hiện điều gì?**  
Một bean definition mới cố chiếm tên đã có nhưng registry không cho phép kiểu override đó.

**2. Exception bắt đầu có từ Spring Framework nào?**  
Spring Framework 5.1.

**3. `getBeanDefinition()` trả new hay old definition?**  
New definition đang cố đăng ký.

**4. `getExistingDefinition()` trả gì?**  
Definition hiện đã được đăng ký dưới tên đó.

**5. Spring Framework 6.2 DefaultListableBeanFactory default là gì?**  
Cho phép overriding (`true`).

**6. Spring Boot 3.4 default là gì?**  
`spring.main.allow-bean-definition-overriding=false`.

**7. Boot property có tự áp vào plain BeanFactory không?**  
Không; plain BeanFactory cần được cấu hình bằng API tương ứng.

**8. Hai bean khác name nhưng cùng type có override conflict không?**  
Không vì names khác, nhưng injection một type có thể ambiguous.

**9. @Primary sửa được same-name definitions không?**  
Không. Nó chọn candidate trong by-type resolution.

**10. @Qualifier sửa được same-name definitions không?**  
Không. Nó thu hẹp injection candidate selection.

**11. Component scan có thể báo lỗi gì khác BeanDefinitionOverrideException?**  
`ConflictingBeanDefinitionException` khi discovered names trùng với definitions không tương thích.

**12. Tại sao hai UserService khác packages có thể xung đột?**  
Default naming dùng simple class names sau decapitalize, tạo cùng `userService`.

**13. FullyQualifiedAnnotationBeanNameGenerator có sửa explicit @Service("shared") không?**  
Không; nó chỉ thay default name generation khi chưa explicit.

**14. Một scanned @Component và @Bean cùng name có luôn ném exception không?**  
Không; Spring có special-case khi @Bean declared return type trùng scanned bean class.

**15. Alias khác overriding như thế nào?**  
Alias là tên phụ cho cùng definition; override thay definition dưới một key.

**16. Child và parent contexts có cùng name là override local không?**  
Không, vì hai BeanFactories riêng, child có thể shadow parent.

**17. Khi nào nên bật allowBeanDefinitionOverriding=true?**  
Chỉ khi có override chủ đích, được kiểm soát và kiểm thử đầy đủ; không phải fix mặc định.

**18. Vì sao @Profile("dev") và @Profile("test") chưa chắc loại trừ?**  
Spring có thể có cả dev và test active.

**19. BeanDefinitionOverrideException thuộc phase nào?**  
Registration/configuration metadata, trước normal bean instantiation.

**20. Làm sao lấy đúng old/new definitions để debug?**  
Dùng `getExistingDefinition()` và `getBeanDefinition()`.

**21. Có dùng @ConditionalOnMissingBean trong Spring Core chỉ với spring-context không?**  
Không, đó là annotation Spring Boot.

**22. Nếu hai bean alternatives đều cần tồn tại, làm gì?**  
Đặt unique names, rồi dùng qualifier/primary/provider để chọn dependency.

**23. Đổi policy sau khi context.refresh() lỗi có sửa app đang fail không?**  
Không; cần sửa nguồn/cấu hình rồi dựng context hợp lệ.

**24. Override definition đã instantiate có phải hot-swap đúng nghĩa không?**  
Không, callers có thể giữ references cũ; lifecycle/metadata thay đổi có rủi ro.

**25. Cách sửa lâu dài nhất?**  
Thiết kế naming và registration không chồng lấn, có tests phát hiện duplicate sớm.

## 21. Tài liệu tham khảo chính thức

- [Spring Framework 6.2 Reference — Bean Overview, Overriding Beans](https://docs.spring.io/spring-framework/reference/6.2/core/beans/definition.html): naming, overriding, Java config special cases, warning.
- [Spring Framework 6.2 API — BeanDefinitionOverrideException](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/support/BeanDefinitionOverrideException.html): class, getters, exception semantics.
- [Spring Framework 6.2 API — DefaultListableBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/support/DefaultListableBeanFactory.html): `setAllowBeanDefinitionOverriding` và default true.
- [Spring Framework 6.2 API — GenericApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/GenericApplicationContext.html): policy trước refresh.
- [Spring Framework 6.2 Reference — Classpath Scanning](https://docs.spring.io/spring-framework/reference/6.2/core/beans/classpath-scanning.html): default naming và fully qualified generator.
- [Spring Framework 6.2 API — FullyQualifiedAnnotationBeanNameGenerator](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/FullyQualifiedAnnotationBeanNameGenerator.html): cách tránh short-name conflicts.
- [Spring Framework 6.2 Reference — @Bean and @Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/basic-concepts.html): bean name, registration.
- [Spring Framework 6.2 Reference — Bean Overriding in Tests](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/bean-overriding.html): @TestBean, @MockitoBean, @MockitoSpyBean.
- [Spring Framework 6.2 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.2-Release-Notes): override INFO logging và recommendations.
- [Spring Boot 3.4 Common Application Properties](https://docs.spring.io/spring-boot/3.4/appendix/application-properties/): default false của `spring.main.allow-bean-definition-overriding`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="troubleshooting-006">Sai phạm vi quét, profile hoặc điều kiện đăng ký</a>

<details>
<summary>Xem chi tiết</summary>

# Sai phạm vi quét, profile hoặc điều kiện đăng ký trong Spring Framework

## 1. Tổng quan: bean không được đăng ký không giống bean tạo thất bại

Trong **Spring Framework Core Container**, Spring chỉ có thể inject hoặc trả về những bean mà `BeanFactory` nhìn thấy theo **bean definition, singleton registration và context hierarchy**. Một class Java hợp lệ chưa chắc là một Spring bean.

Ba nguyên nhân đặc biệt thường gặp khi báo lỗi “không tìm thấy bean”:

1. **Sai phạm vi quét (component scanning)**: `@Component`/`@Service` tồn tại nhưng package không nằm trong phạm vi `@ComponentScan`, hoặc class bị filter loại.
2. **Profile không khớp**: bean/configuration được đánh dấu `@Profile("dev")`, nhưng active profile thực tế là `prod` hay một profile khác.
3. **Điều kiện đăng ký không khớp**: `@Conditional` hoặc điều kiện của Spring Boot đánh giá false, do đó definition không được thêm vào registry.

Kết quả có thể giống nhau: một consumer khai báo required dependency nhưng Spring không tìm được ứng viên, thường dẫn đến `NoSuchBeanDefinitionException` trong cause chain của `UnsatisfiedDependencyException` hoặc `BeanCreationException` khi tạo bean.

> Cần phân biệt **không có BeanDefinition phù hợp** với **đã có definition nhưng không tạo được instance**, chẳng hạn constructor ném exception, scope chưa active hay cấu hình proxy lỗi. Cách sửa cho hai loại tình huống này khác nhau.

Bài thuộc mục **Chẩn đoán lỗi** của module **Spring Framework — core-container**, đi đúng chủ đề **“Sai phạm vi quét, profile hoặc điều kiện đăng ký”**. Bài có kiến thức nguyên lý, quy trình chẩn đoán, bảng đối chiếu tình huống và một project chạy được với **Java 17 + Spring Context 6.1.13 + JUnit Jupiter 5**.

## 2. Spring hình thành registry và bean instances như thế nào?

Một lộ trình đơn giản:

~~~text
Classpath
   |
   v
Cấu hình được nạp: @Configuration / @Import / @ComponentScan
   |
   +--> Component scanning tìm candidates theo packages và filters
   |
   +--> @Profile chọn cấu hình/bean đúng Environment
   |
   +--> @Conditional đánh giá điều kiện đăng ký
   |
   v
BeanDefinitions đủ điều kiện được ghi vào registry
   |
   v
BeanFactory resolve constructor args, @Autowired, @Bean parameters
   |
   v
Tạo bean instances và chạy lifecycle
   |
   v
Các consumers sử dụng beans
~~~

`@ComponentScan`, `@Profile` và `@Conditional` chủ yếu quyết định **cái gì được đăng ký và xử lý khi context khởi động**. BeanFactory chỉ tạo object từ definitions hoặc instance registrations phù hợp.

Một `@Component` class bị bỏ sót khỏi scan sẽ không có definition để tạo. Một `@Bean` method bên trong `@Profile` class bị loại cũng không được thực thi.

### Đừng nhầm ba câu hỏi

| Câu hỏi | API kiểm tra |
| --- | --- |
| Có bean local definition theo tên không? | `containsBeanDefinition("name")` |
| Bean theo tên có visible để lookup không? | `containsBean("name")` |
| Singleton instance đã nằm trong cache chưa? | `getBeanFactory().containsSingleton("name")` |

Một lazy singleton có definition nhưng chưa có singleton instance vẫn là bình thường. Ngược lại, `containsBeanDefinition` chỉ nhìn local definitions, không nhất thiết bao gồm ancestor beans.

## 3. Triệu chứng lỗi thường gặp

### 3.1. NoSuchBeanDefinitionException

~~~java
context.getBean(Notifier.class);
~~~

Nếu không có candidate theo type, thường ném `NoSuchBeanDefinitionException`.

### 3.2. UnsatisfiedDependencyException khi refresh

~~~java
@Bean
WorkService workService(Notifier notifier) {
    return new WorkService(notifier);
}
~~~

Nếu không có `Notifier`, Spring có thể fail khi `refresh()` tạo eager `WorkService` singleton, với exception ngoài cùng thuộc `BeanCreationException` hierarchy và nested cause mô tả missing Notifier.

### 3.3. NoUniqueBeanDefinitionException

Nếu component scan quá rộng làm xuất hiện **hai Notifier implementations** mà consumer cần một bean duy nhất, lỗi có thể là `NoUniqueBeanDefinitionException`. Đây không phải “thiếu bean”, mà là “quá nhiều candidates”.

### 3.4. BeanNotOfRequiredTypeException

Nếu tên `notifier` có bean nhưng runtime type không assignable với required type, named lookup có thể báo `BeanNotOfRequiredTypeException`. Đừng xử lý nó như case scan không tìm thấy component.

### 3.5. ScopeNotActiveException hoặc BeanCreationException khác

Một bean đã được đăng ký nhưng thuộc web scope chưa active có thể báo `ScopeNotActiveException`. Constructor/factory/init method ném exception sẽ gây bean creation failure dù definition tồn tại.

**Luôn bắt đầu bằng exception class và root cause**, không chỉ đọc câu “Consider defining a bean…” trong một số logs của Spring Boot.

## 4. Kiểm tra trước khi sửa code

Khi gặp lỗi missing bean, lập bảng:

| Mục | Cần xác nhận |
| --- | --- |
| Consumer | Bean nào đang yêu cầu dependency? |
| Injection point | Constructor, field, setter hay `@Bean` parameter? |
| Required type | Interface/concrete/generic type? |
| Required name/qualifier | Có `@Qualifier`/`@Resource` không? |
| Provider | Class nào cung cấp implementation? |
| Registration path | Scan, `@Bean`, import hay programmatic? |
| Context | Consumer và provider cùng context/hierarchy? |
| Profiles | Active/default profiles thực tế là gì? |
| Conditions | `@Conditional` đánh giá dựa vào state nào? |
| Lifecycle | Lỗi ở refresh hay lúc getBean? |

Một quy trình đúng phải xác định **điểm bị loại khỏi registration**, không chỉ cố tìm một annotation để thêm.

## 5. @ComponentScan chọn package nào?

`@ComponentScan` trên configuration class cho Spring biết các package được scan. Ví dụ:

~~~java
@Configuration
@ComponentScan(basePackages = "com.example.orders")
class OrderConfig {}
~~~

Spring scan `com.example.orders` và các subpackages có thể được tìm theo classpath resource scanner. Nếu `NotifierComponent` nằm ở `com.example.notifications`, nó có thể nằm ngoài phạm vi scan dù đã có `@Component`.

**Nếu `@ComponentScan` không khai báo basePackages hoặc basePackageClasses**, mặc định dùng **package của class đang khai báo `@ComponentScan`**.

### Thứ cần kiểm tra

- Package thực tế của provider class trong source/file system.
- `basePackages`, `value` hoặc `basePackageClasses`.
- Class có `@Component`/`@Service`/`@Repository`/`@Controller` hay custom stereotype meta-annotated with `@Component`?
- `includeFilters` và `excludeFilters` có thay đổi candidates không?
- `useDefaultFilters=false` có tắt automatic stereotype detection không?
- `resourcePattern` và custom name generator có ảnh hưởng không?
- Có `@Profile`/`@Conditional` trên scanned class làm nó bị loại không?

Đừng kết luận scanning hoạt động chỉ vì `@Configuration` được register. Configuration class tồn tại không tự động scan toàn bộ project.

## 6. Dùng basePackageClasses để giảm lỗi refactor

Thay vì hard-code string:

~~~java
@ComponentScan(basePackages = "com.example.notifications")
~~~

có thể dùng:

~~~java
@ComponentScan(
    basePackageClasses = NotifierComponent.class)
~~~

Spring lấy **package chứa NotifierComponent** làm base package. Đây là lựa chọn type-safe hơn vì compiler/IDE có thể hỗ trợ refactor class marker.

### Lưu ý

`basePackageClasses` xác định **package**, không chỉ đăng ký duy nhất class được chỉ định. Nếu package có nhiều components khác, chúng cũng có thể được scan.

Khi muốn một marker độc lập, tạo interface/class không có business semantics, rồi dùng marker để chỉ package. Nếu chỉ muốn đăng ký **đúng một class**, `context.register(NotifierComponent.class)`/`@Import(NotifierComponent.class)` có thể thích hợp hơn component scan toàn package.

## 7. @Service có tự làm class thành bean không?

~~~java
@Service
public final class EmailNotifier implements Notifier {
    // ...
}
~~~

`@Service` là một stereotype meta-annotated with `@Component`. Nhưng **nó chỉ được scanner xử lý nếu scan đi qua package chứa class** hoặc được đưa vào context bằng cơ chế đăng ký khác.

Một số ứng dụng có:

~~~java
@SpringBootApplication
public class Application {}
~~~

Spring Boot `@SpringBootApplication` bao gồm component scanning với mặc định dựa trên package của application class. Nếu main class được đặt quá sâu trong package tree, những packages ngang hàng dễ bị bỏ sót.

Đây là phần Boot-specific; trong Spring Framework thuần, explicit `@ComponentScan` hoặc `AnnotationConfigApplicationContext.scan(...)`/`register(...)` quyết định scanning.

### Không nhầm annotation với registration

Java classloader có thể nạp EmailNotifier mà Spring chưa hề có BeanDefinition của nó. Lời gọi `new EmailNotifier()` cũng không đưa instance ấy vào Spring container.

## 8. Filters có thể loại một @Component hợp lệ

`@ComponentScan` có:

- `includeFilters`: bổ sung candidate types theo annotation, assignable type, regex hoặc custom TypeFilter.
- `excludeFilters`: loại candidate types.
- `useDefaultFilters`: bật/tắt detection của stereotypes mặc định.

Ví dụ exclude implementation:

~~~java
@Configuration
@ComponentScan(
    basePackageClasses = NotifierComponent.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = NotifierComponent.class))
class ConfigWithoutNotifier {}
~~~

Dù package đúng và class có `@Component`, explicit exclude filter trên vẫn loại class khỏi scan.

### useDefaultFilters=false

~~~java
@ComponentScan(
    basePackageClasses = NotifierComponent.class,
    useDefaultFilters = false)
~~~

Nếu không có includeFilters phù hợp, default component stereotype detection bị tắt, có thể khiến `NotifierComponent` không được đăng ký.

### Thực hành tốt

Đặt filters có chủ đích, giới hạn scan packages, review custom filter behavior. Đừng sửa lỗi thiếu bean bằng cách xóa mọi excludeFilters rồi quét hết classpath: bạn có thể vô tình đăng ký components không mong muốn.

## 9. Vùng quét quá rộng cũng có lỗi

Scan toàn package gốc quá lớn có thể kéo vào:

- Hai beans cùng implementation interface, gây `NoUniqueBeanDefinitionException`.
- Beans dành cho dev/test trong production.
- Expensive startup clients/schedulers không thuộc context hiện tại.
- Configurations có `@Bean` names trùng nhau.
- Các components tích hợp với external resources.

Một missing-bean diagnostic có thể dẫn tới **overcorrect**: mở rộng scan quá mức rồi tạo ambiguity hoặc startup side effects.

### Chiến lược tốt hơn

- Phân nhóm package theo bounded context.
- Chọn explicit basePackages/basePackageClasses hợp lý.
- Dùng `@Import` cho configuration đặc thù.
- Đặt `@Profile` trên implementation phù hợp môi trường.
- Viết integration tests cho mỗi môi trường/profile quan trọng.

## 10. @Configuration và @Import khác component scan

Một `@Bean` method chỉ được Spring xử lý nếu **configuration class chứa nó được nạp** qua register, scan hoặc `@Import`.

Ví dụ:

~~~java
@Configuration
class ClientConfig {
    @Bean
    RemoteClient remoteClient() {
        return new RemoteClient();
    }
}
~~~

Nếu bạn chỉ tạo context từ `OtherConfig.class` mà không scan/import `ClientConfig`, remoteClient sẽ không được đăng ký.

Sửa theo phạm vi:

~~~java
context.register(OtherConfig.class, ClientConfig.class);
context.refresh();
~~~

hoặc:

~~~java
@Configuration
@Import(ClientConfig.class)
class OtherConfig {}
~~~

Những snippets này cần classes/imports phù hợp. `register(...)` trên `AnnotationConfigApplicationContext` rỗng nên thực hiện **trước refresh**.

### @Import không phải quét tự động mọi class

`@Import` dùng để đưa configuration/components/registrar tùy trường hợp vào graph; nó không tương đương scanning toàn ứng dụng.

## 11. @Profile quyết định eligibility của bean

`@Profile` là conditional registration dựa vào active profiles của Spring `Environment`. Nó có thể đặt trên:

- Class được annotate bằng `@Component` hoặc meta-stereotypes.
- Class `@Configuration`.
- `@Bean` method.

Ví dụ:

~~~java
@Configuration
@Profile("dev")
class DevConfig {
    @Bean
    FeaturePolicy featurePolicy() {
        return new FeaturePolicy("dev");
    }
}
~~~

Nếu context active profile `prod`, cấu hình dev này không match; method `featurePolicy` không được đăng ký.

**Không đánh đồng "có class DevConfig" với "có bean featurePolicy".**

## 12. Class-level và method-level @Profile

Class-level:

~~~java
@Configuration
@Profile("staging")
class StagingConfig {
    @Bean
    FeaturePolicy policy() {
        return new FeaturePolicy("staging");
    }
}
~~~

Toàn `@Configuration` (cùng những `@Bean`/`@Import` gắn với nó theo semantics framework) có thể bị bỏ qua khi staging không active.

Method-level:

~~~java
@Configuration
class PolicyConfig {
    @Bean
    @Profile("staging")
    FeaturePolicy stagingPolicy() {
        return new FeaturePolicy("staging");
    }
}
~~~

Chỉ bean method tương ứng bị điều kiện profile kiểm soát; các bean methods khác trong config vẫn có thể được xử lý.

### Khi nào chọn dạng nào?

- Một bộ cấu hình chỉ dành riêng một môi trường: class-level.
- Một hoặc vài beans thay thế theo môi trường, nằm chung config: method-level.
- Nhiều implementations có cùng contract: bảo đảm đúng qualifier/primary/name khi nhiều profiles cùng active.

## 13. @Profile có thể dùng biểu thức logic

Javadoc Spring 6.1 hỗ trợ profile expressions, ví dụ:

~~~java
@Profile("dev & cloud")
~~~

hoặc:

~~~java
@Profile("!prod")
~~~

Có thể kết hợp theo cú pháp do `Profiles.of(...)` hỗ trợ, gồm `&`, `|` và dấu ngoặc.

### Chú ý với mảng profiles

~~~java
@Profile({"dev", "qa"})
~~~

Có semantics **OR** giữa các điều kiện trong mảng: chỉ cần một profile match là configuration đủ điều kiện.

Không suy rằng mảng trên là “dev AND qa”. Muốn AND, dùng expression `@Profile("dev & qa")`.

### Toán tử phủ định

`@Profile("!prod")` match khi profile prod **không active**, kể cả khi active profile là dev hoặc không có explicit active profiles phù hợp. Đừng xem !prod là tương đương @Profile("dev").

## 14. Default profile và active profile

Spring Environment có:

~~~java
environment.getActiveProfiles();
environment.getDefaultProfiles();
~~~

Một default profile `"default"` được xét khi không có profile nào được active tường minh (có thể đổi qua `setDefaultProfiles` hoặc property tương ứng).

Ví dụ:

~~~java
@Profile("default")
class FallbackConfig {}
~~~

**Không** luôn là fallback khi `dev`/`prod` đã active. Nếu prod active, default không tự match.

### Kiểm tra giá trị thực tế

Đọc cả `getActiveProfiles()` và `getDefaultProfiles()`. Environment có thể nhận active profile từ:

- `spring.profiles.active` property.
- System properties/JVM flags.
- Environment variables qua `SystemEnvironmentPropertySource`.
- Programmatic `setActiveProfiles(...)`.
- Spring Boot configuration processing (nếu có).

Không dựa vào “local máy mình thường chạy dev” làm bằng chứng profile nào thực sự được bật.

## 15. Đặt profile trước refresh là bắt buộc

Lộ trình đúng:

~~~java
AnnotationConfigApplicationContext ctx =
    new AnnotationConfigApplicationContext();

ctx.getEnvironment().setActiveProfiles("dev");
ctx.register(DevConfig.class, ProdConfig.class);
ctx.refresh();
~~~

Ngược lại, đặt profile **sau refresh** không làm Spring đăng ký lại những beans đã bị loại.

### Tại sao?

Spring đánh giá profile trong quá trình xử lý bean definitions, trước khi ứng dụng chạy bình thường. Việc thay Environment property/profile runtime **không phải lệnh refresh lại configuration class registry**.

`AnnotationConfigApplicationContext`/`GenericApplicationContext` cũng không cho gọi `refresh()` lần hai để “reload” definitions.

### Hướng thay đổi cấu hình runtime

Nếu cần đổi behavior runtime, tách policy vào managed service/router với config state phù hợp, hoặc dựng context mới/version mới khi cần thay cả một tập bean definitions. Không dùng `setActiveProfiles` sau refresh như công tắc hot-swap bean graph.

## 16. Trường hợp nhiều profiles cùng active

~~~java
ctx.getEnvironment().setActiveProfiles("dev", "prod");
~~~

Nếu DevConfig và ProdConfig cùng có một bean `FeaturePolicy`, bạn có thể tạo **hai candidates cùng type** hoặc trùng bean name tùy cách đặt tên/overriding policy.

### Hai lỗi khác nhau

- Hai beans tên khác nhau cùng type, injection theo type cần một → có thể `NoUniqueBeanDefinitionException`.
- Hai beans cùng name → chịu policy bean definition overriding; có thể bị từ chối hoặc bean này ghi đè bean kia tùy cấu hình.

**Giải pháp:** chính sách profile loại trừ nhau, naming rõ, qualifier/primary hợp lý, hoặc fail-fast khi cấu hình môi trường không hợp lệ.

Đừng mặc định rằng dev và prod sẽ không bao giờ cùng active nếu external config có thể bật cả hai.

## 17. Cảnh báo về overloaded @Bean với @Profile

Javadoc `@Profile` của Spring cảnh báo: nếu có **overloaded `@Bean` methods cùng Java method name**, các profile conditions phải được khai báo nhất quán; Spring không dùng profile để chọn riêng overload theo argument signature như bạn có thể kỳ vọng.

### Thiết kế nên chọn

Thay vì dựa vào overloaded `@Bean` methods:

~~~java
@Bean("featurePolicy")
@Profile("dev")
FeaturePolicy devFeaturePolicy() {
    return new FeaturePolicy("dev");
}

@Bean("featurePolicy")
@Profile("prod")
FeaturePolicy prodFeaturePolicy() {
    return new FeaturePolicy("prod");
}
~~~

Hai methods có **Java method names khác nhau** nhưng cùng bean name. Điều này phù hợp khi profiles loại trừ nhau; nếu cùng match, vẫn phải tính bean-name conflict policy.

Đừng dùng overloading cùng method name để triển khai profile-dependent factory-method resolution.

## 18. @Conditional là gì?

`@Conditional` của Spring Framework cho phép đăng ký component/configuration/bean method **chỉ nếu tất cả điều kiện yêu cầu match**.

Ví dụ một condition:

~~~java
public final class OnAuditFlag implements Condition {
    @Override
    public boolean matches(
            ConditionContext context,
            AnnotatedTypeMetadata metadata) {
        return "true".equalsIgnoreCase(
            context.getEnvironment()
                .getProperty("feature.audit.enabled", "false"));
    }
}
~~~

Đây là snippet cần imports `Condition`, `ConditionContext` và `AnnotatedTypeMetadata`.

Áp dụng:

~~~java
@Bean
@Conditional(OnAuditFlag.class)
AuditSink auditSink() {
    return new AuditSink();
}
~~~

Nếu property `feature.audit.enabled` có giá trị string true khi condition được đánh giá, bean đủ điều kiện. Nếu false hoặc không có property, bean definition bị bỏ qua.

### Condition không phải kiểm tra bean instance lúc runtime

`Condition.matches(...)` chạy quanh thời điểm **bean definition registration**, không phải được gọi mỗi lần consumer gọi `getBean`. Một property thay đổi sau refresh không tự kích hoạt lại condition hay tạo/hủy bean.

## 19. @Conditional trên class và trên method

Class-level:

~~~java
@Configuration
@Conditional(OnAuditFlag.class)
class AuditConfig {
    @Bean
    AuditSink auditSink() {
        return new AuditSink();
    }
}
~~~

Khi condition không match, cả config bị loại theo semantics `@Conditional`, bao gồm những bean methods và scanning/import directives đi kèm phạm vi config.

Method-level:

~~~java
@Configuration
class AuditConfig {
    @Bean
    @Conditional(OnAuditFlag.class)
    AuditSink auditSink() {
        return new AuditSink();
    }

    @Bean
    HealthProbe healthProbe() {
        return new HealthProbe();
    }
}
~~~

Ở đây chỉ `auditSink` chịu condition; `healthProbe` vẫn có thể được tạo nếu configuration class được xử lý.

### Nhiều Conditions

~~~java
@Conditional({OnAuditFlag.class, OnOtherCondition.class})
~~~

Spring yêu cầu **tất cả conditions match** để annotation element đủ điều kiện.

### @Conditional inheritance

Javadoc lưu ý `@Conditional` không được thừa kế tự động từ superclass/overridden method theo cách người dùng có thể tưởng; nó không khai báo `@Inherited`. Khi dùng custom composed annotations, cần hiểu rõ metadata inheritance/merging semantics.

## 20. Condition nên phụ thuộc metadata/environment, không tạo bean sớm

Một `Condition` phù hợp thường dựa vào:

- Active profiles.
- Environment property.
- Class/resource availability.
- Annotation metadata.
- Metadata registry phù hợp với thời điểm xử lý.

**Không nên gọi `getBean()` để khởi tạo application beans từ Condition.matches**. Condition chạy khi container đang dựng registry; bean instances thường chưa sẵn sàng.

Nếu condition cần phân biệt configuration parsing và bean registration phases, Spring có `ConfigurationCondition` cùng `ConfigurationPhase` để biểu đạt đúng timing.

Cũng tránh phụ thuộc vào thứ tự đăng ký các bean không được bảo đảm giữa các configurations. Đặc biệt với condition based on “bean đã được đăng ký?”, cần cân nhắc timing và cơ chế framework thay vì giả định toàn graph đã hoàn chỉnh.

## 21. Spring Boot @ConditionalOn... khác Spring Framework @Conditional

Những annotations phổ biến của **Spring Boot**:

- `@ConditionalOnProperty`.
- `@ConditionalOnClass`.
- `@ConditionalOnBean`.
- `@ConditionalOnMissingBean`.
- `@ConditionalOnResource`.
- `@ConditionalOnWebApplication`.

Chúng được xây trên Spring condition infrastructure nhưng **không thuộc `spring-context` thuần**. Nếu bạn chỉ khai báo dependency `spring-context`, không thể import `org.springframework.boot.autoconfigure.condition.ConditionalOnProperty` mà không bổ sung Spring Boot dependencies.

### Với Spring Boot, kiểm tra thêm

- Auto-configuration có được import/bật không?
- Một auto-config class bị `exclude` không?
- Property cần thiết có mặt, đúng tên và value semantics không?
- Library classpath có đáp ứng `@ConditionalOnClass`?
- Bean trước đó có làm `@ConditionalOnMissingBean` không còn match?
- Profiles có khiến conditions nhìn thấy Environment khác không?

### Đọc condition evaluation report

Spring Boot có condition evaluation report (ví dụ debug logs hoặc Actuator conditions endpoint nếu được cấu hình), rất hữu ích để phân biệt **negative matches** và **positive matches**. Không giả định report Boot tồn tại trong bare Spring Framework context.

## 22. PropertySources và mức ưu tiên làm Condition false

Một điều kiện property có thể đọc:

~~~java
context.getEnvironment()
    .getProperty("feature.audit.enabled");
~~~

Giá trị đến từ `PropertySources` với thứ tự ưu tiên.

Nếu system property nói true nhưng test/launcher thêm `MapPropertySource` lên đầu với false, condition có thể thấy false.

### Ví dụ test cô lập

~~~java
MapPropertySource overrides = new MapPropertySource(
    "scenario",
    Map.of("feature.audit.enabled", "true"));

context.getEnvironment()
    .getPropertySources()
    .addFirst(overrides);
~~~

Cần imports `MapPropertySource` và `java.util.Map`; làm **trước refresh**.

### Không chỉ in getProperty rồi kết luận

Nếu condition parse boolean bằng `Boolean.parseBoolean`, các giá trị như `"yes"` hoặc `"1"` có thể không được xem là true. Nếu implementation so sánh `"true"` không phân biệt hoa/thường, nó có semantics riêng. Khi debug, đọc **condition code và exact property value**.

## 23. @Profile và @Conditional có thể cùng tồn tại

Một bean có thể bị ràng buộc cả bởi profile lẫn feature flag:

~~~java
@Bean
@Profile("dev")
@Conditional(OnAuditFlag.class)
AuditSink developerAuditSink() {
    return new AuditSink();
}
~~~

Muốn bean xuất hiện:

- Configuration class phải được load.
- Profile dev phải match.
- Condition flag phải match.
- Không có scan/import filter loại config/bean.
- Bean name/definition không bị overriding policy chặn.

### Bảng quyết định

| Config được nạp | Profile dev | Flag true | Kết quả |
| --- | --- | --- | --- |
| Không | Có | Có | Không có bean |
| Có | Không | Có | Không có bean |
| Có | Có | Không | Không có bean |
| Có | Có | Có | Bean đủ điều kiện đăng ký |

Cần phân biệt **đủ điều kiện đăng ký** với **khởi tạo bean instance thành công**, vì constructor và dependencies vẫn có thể gây lỗi.

## 24. Cách chẩn đoán từng lớp khi không có bean

Một luồng thực tế:

~~~text
Consumer cần Notifier
     |
     v
Lớp NotifierComponent tồn tại trong classpath?
     | không -> dependency/build/classpath
     | có
     v
Có cơ chế register/scan/import cho nó?
     | không -> sai phạm vi scan
     | có
     v
Có include/exclude filter loại nó?
     | có -> điều chỉnh filter
     | không
     v
@Profile có match Environment?
     | không -> sửa active profile/annotation
     | có
     v
@Conditional có match?
     | không -> kiểm tra properties, classes, registry state
     | có
     v
BeanDefinition có trong đúng BeanFactory/context?
     | không -> tên/registration/context hierarchy
     | có
     v
Bean có đúng type/name và tạo được?
     | không -> constructor/scope/FactoryBean/proxy/wrong type
     | có
     v
Consumer DI hoạt động
~~~

Đừng sửa một trong các bước cuối cùng bằng cách quét nhiều packages hơn nếu nguyên nhân thực tế là condition false.

## 25. API quan sát registry đúng cách

Trong `AnnotationConfigApplicationContext`:

~~~java
boolean defined =
    context.containsBeanDefinition("scannedNotifier");

boolean visible =
    context.containsBean("scannedNotifier");

String[] notifierNames =
    context.getBeanNamesForType(Notifier.class);

String[] profiles =
    context.getEnvironment().getActiveProfiles();
~~~

Đây là snippets cần context và Notifier type được định nghĩa.

- `containsBeanDefinition`: local registry đã có definition với tên đó?
- `containsBean`: lookup name có thể nhìn thấy bean theo semantics context (có thể gồm ancestor)?
- `getBeanNamesForType`: names phù hợp type trong local `ListableBeanFactory`.
- `getActiveProfiles`: explicit active profiles, không phải luôn cả default profiles.

### Context hierarchy

Nếu bean provider đăng ký trong child context, parent **không tự lookup xuống child**. Child có thể lookup bean từ parent khi không có local masking. Kiểm tra `context.getParent()`, `containsLocalBean` và cùng BeanFactory trước khi kết luận scanning sai.

### Đừng dùng beanDefinitionCount quá đơn giản

Một context có internal configuration processors, bean definitions infrastructure và proxies. Đừng đoán expected bean count chỉ bằng số `@Bean` methods bạn viết.

## 26. Ví dụ runnable: ba loại sai sót trong cùng project

Ví dụ gồm:

1. `@ComponentScan` sai package dẫn đến thiếu Notifier; consumer fail khi refresh.
2. `@ComponentScan(basePackageClasses=...)` đúng package, tìm được `@Component` Notifier, DI thành công.
3. Hai configs có `@Profile("dev")` / `@Profile("prod")`; chỉ một policy được đăng ký mỗi context.
4. Profile `qa` không match nên không có policy.
5. `@Conditional` dựa trên property `feature.audit.enabled`, false thì không có bean, true thì có.
6. Đổi property sau refresh **không làm bean definition biến mất**.
7. JUnit 5 có positive và negative tests đối chiếu từng cơ chế.

Project không dùng Spring Boot, tránh nhầm conditional annotations của Boot với Spring Framework Core.

## 27. Cấu trúc Maven

~~~text
registration-diagnostics/
├── pom.xml
└── src/
    ├── main/
    │   └── java/
    │       └── com/example/registration/
    │           ├── Notifier.java
    │           ├── RegistrationDemo.java
    │           └── scanned/
    │               └── ScannedNotifier.java
    └── test/
        └── java/
            └── com/example/registration/
                └── RegistrationDemoTest.java
~~~

Tách scanned component vào package con riêng để test package boundary chính xác. Class `RegistrationDemo` là chương trình chạy, đồng thời chứa các `@Configuration` classes nhỏ như `CoreConfig`, `WrongScanConfig`, `CorrectScanConfig`, `DevPolicyConfig`, `ProdPolicyConfig`, `FeatureConfig`.

## 28. File pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>registration-diagnostics</artifactId>
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
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.1</version>
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
                <version>3.2.5</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <mainClass>com.example.registration.RegistrationDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Maven Compiler Plugin đặt source/target compatibility qua `maven.compiler.release=17`. Surefire 3.2.5 hỗ trợ chạy JUnit Jupiter tests với dependency trên.

## 29. File Notifier.java

Đường dẫn `src/main/java/com/example/registration/Notifier.java`:

~~~java
package com.example.registration;

public interface Notifier {
    String send(String message);
}
~~~

Đây là contract được consumer inject. Chưa có implementation không gây lỗi Java compilation; missing bean chỉ xuất hiện khi Spring runtime cần một Notifier instance.

## 30. File ScannedNotifier.java

Đường dẫn `src/main/java/com/example/registration/scanned/ScannedNotifier.java`:

~~~java
package com.example.registration.scanned;

import com.example.registration.Notifier;
import org.springframework.stereotype.Component;

@Component
public final class ScannedNotifier implements Notifier {

    @Override
    public String send(String message) {
        return "scanned:" + message;
    }
}
~~~

Class được đặt riêng trong package `com.example.registration.scanned`.

- Có `@Component`.
- Có public no-args constructor ngầm định.
- Implement `Notifier`.
- Khi scan package này đúng, default bean name là `scannedNotifier` theo `AnnotationBeanNameGenerator`.
- Khi scan một package khác không bao phủ nó, annotation này **không tự đăng ký bean**.

## 31. File RegistrationDemo.java — chương trình hoàn chỉnh

Đường dẫn `src/main/java/com/example/registration/RegistrationDemo.java`:

~~~java
package com.example.registration;

import java.util.Map;

import com.example.registration.scanned.ScannedNotifier;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class RegistrationDemo {

    public static void main(String[] args) {

        // A. Sai scan package: Notifier không được tìm thấy.
        try (AnnotationConfigApplicationContext wrong =
                 new AnnotationConfigApplicationContext()) {

            wrong.register(CoreConfig.class, WrongScanConfig.class);

            boolean failed = false;
            try {
                wrong.refresh();
            } catch (BeanCreationException expected) {
                failed = true;
            }

            System.out.println("scan.wrong.bean=" +
                wrong.containsBean("scannedNotifier"));
            System.out.println("scan.wrong.refresh-failed=" + failed);
        }

        // B. Scan package của ScannedNotifier: DI thành công.
        try (AnnotationConfigApplicationContext correct =
                 new AnnotationConfigApplicationContext(
                     CoreConfig.class,
                     CorrectScanConfig.class)) {

            System.out.println("scan.correct.bean=" +
                correct.containsBean("scannedNotifier"));
            System.out.println("scan.correct.result=" +
                correct.getBean(WorkService.class).process("JOB-1"));
        }

        // C. Cùng policy contract nhưng profiles khác nhau.
        try (AnnotationConfigApplicationContext dev =
                 openPolicy("dev");
             AnnotationConfigApplicationContext prod =
                 openPolicy("prod");
             AnnotationConfigApplicationContext qa =
                 openPolicy("qa")) {

            System.out.println("profile.dev.value=" +
                dev.getBean(FeaturePolicy.class).name());
            System.out.println("profile.prod.value=" +
                prod.getBean(FeaturePolicy.class).name());
            System.out.println("profile.qa.bean=" +
                qa.containsBean("policy"));
        }

        // D. Custom @Conditional dựa vào property.
        try (AnnotationConfigApplicationContext off =
                 openFeature(false);
             AnnotationConfigApplicationContext on =
                 openFeature(true)) {

            System.out.println("condition.off.bean=" +
                off.containsBean("auditSink"));
            System.out.println("condition.on.bean=" +
                on.containsBean("auditSink"));

            // Đổi property sau refresh không tái chạy @Conditional.
            on.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource(
                    "later-change",
                    Map.of("feature.audit.enabled", "false")));

            System.out.println("condition.after-change.bean=" +
                on.containsBean("auditSink"));
        }
    }

    public static AnnotationConfigApplicationContext openPolicy(
            String profile) {

        AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext();

        context.getEnvironment().setActiveProfiles(profile);
        context.register(DevPolicyConfig.class, ProdPolicyConfig.class);
        context.refresh();
        return context;
    }

    public static AnnotationConfigApplicationContext openFeature(
            boolean enabled) {

        AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext();

        context.getEnvironment().getPropertySources().addFirst(
            new MapPropertySource(
                "test-feature-flag",
                Map.of(
                    "feature.audit.enabled",
                    Boolean.toString(enabled))));

        context.register(FeatureConfig.class);
        context.refresh();
        return context;
    }

    public static final class WorkService {
        private final Notifier notifier;

        public WorkService(Notifier notifier) {
            this.notifier = notifier;
        }

        public String process(String id) {
            return notifier.send(id);
        }
    }

    public record FeaturePolicy(String name) {}
    public record AuditSink(String id) {}

    @Configuration(proxyBeanMethods = false)
    public static class CoreConfig {

        @Bean
        public WorkService workService(Notifier notifier) {
            return new WorkService(notifier);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
        basePackages = "com.example.registration.not-scanned")
    public static class WrongScanConfig {
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = ScannedNotifier.class)
    public static class CorrectScanConfig {
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("dev")
    public static class DevPolicyConfig {

        @Bean("policy")
        public FeaturePolicy devPolicy() {
            return new FeaturePolicy("dev");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("prod")
    public static class ProdPolicyConfig {

        @Bean("policy")
        public FeaturePolicy prodPolicy() {
            return new FeaturePolicy("prod");
        }
    }

    public static final class WhenAuditEnabled implements Condition {

        @Override
        public boolean matches(
                ConditionContext context,
                AnnotatedTypeMetadata metadata) {

            return "true".equalsIgnoreCase(
                context.getEnvironment().getProperty(
                    "feature.audit.enabled", "false"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class FeatureConfig {

        @Bean
        @Conditional(WhenAuditEnabled.class)
        public AuditSink auditSink() {
            return new AuditSink("AUDIT");
        }
    }
}
~~~

### Vì sao code không đọc source configuration ngoài dự kiến?

`CoreConfig` chỉ khai báo WorkService cần Notifier. `WrongScanConfig` scan một package không có provider; `CorrectScanConfig` scan đúng package chứa ScannedNotifier.

Policy configurations dùng `@Profile` ở class level và được đăng ký cả hai trong mỗi context, nhưng chỉ config match active profile mới cung cấp `policy`.

FeatureConfig dùng một `Condition` thuần Spring, đọc một key từ Environment. `MapPropertySource` được thêm **trước refresh**, làm kết quả ổn định trước external system environment.

## 32. Chạy chương trình và kết quả đầu ra

Yêu cầu **JDK 17+** và **Maven 3.9.x**. Từ thư mục `registration-diagnostics` chứa pom.xml:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do các `System.out.println` trong chương trình tạo:

~~~text
scan.wrong.bean=false
scan.wrong.refresh-failed=true
scan.correct.bean=true
scan.correct.result=scanned:JOB-1
profile.dev.value=dev
profile.prod.value=prod
profile.qa.bean=false
condition.off.bean=false
condition.on.bean=true
condition.after-change.bean=true
~~~

Spring có thể log cảnh báo vì `wrong.refresh()` cố ý thất bại; Maven và logging backend cũng có thể có dòng bổ sung. Mười dòng trên mô tả **stdout do chương trình chủ động in**, không khẳng định toàn bộ console chỉ có mười dòng.

### Phân tích output

1. **scan.wrong.bean=false:** wrong scan không tìm thấy class trong package `com.example.registration.scanned`.
2. **scan.wrong.refresh-failed=true:** WorkService bắt buộc inject Notifier, nhưng thiếu ứng viên nên refresh fail.
3. **scan.correct.bean=true:** đúng basePackageClasses dẫn scanner tới package chứa component.
4. **scan.correct.result=scanned:JOB-1:** Spring inject ScannedNotifier vào WorkService và consumer gọi thành công.
5. **profile.dev.value=dev:** `@Profile("dev")` match.
6. **profile.prod.value=prod:** `@Profile("prod")` match.
7. **profile.qa.bean=false:** cả dev lẫn prod không match qa, nên không có bean policy.
8. **condition.off.bean=false:** custom condition false, auditSink không được đăng ký.
9. **condition.on.bean=true:** condition true, auditSink được đăng ký.
10. **condition.after-change.bean=true:** đổi property sau refresh không tự xóa bean đã đăng ký.

Đây là **registration behavior**, không có web server, external APIs, database hoặc dynamic registration sau refresh.

## 33. File RegistrationDemoTest.java — JUnit 5

Đường dẫn `src/test/java/com/example/registration/RegistrationDemoTest.java`:

~~~java
package com.example.registration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationDemoTest {

    @Test
    void wrongScanFailsToProvideRequiredNotifier() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext()) {

            context.register(
                RegistrationDemo.CoreConfig.class,
                RegistrationDemo.WrongScanConfig.class);

            assertThrows(BeanCreationException.class, context::refresh);
            assertFalse(context.containsBean("scannedNotifier"));
        }
    }

    @Test
    void correctScanCreatesNotifierAndWiresConsumer() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(
                     RegistrationDemo.CoreConfig.class,
                     RegistrationDemo.CorrectScanConfig.class)) {

            assertTrue(context.containsBean("scannedNotifier"));
            assertEquals("scanned:JOB-1",
                context.getBean(RegistrationDemo.WorkService.class)
                    .process("JOB-1"));
        }
    }

    @Test
    void devProfileActivatesOnlyDevPolicy() {
        try (AnnotationConfigApplicationContext context =
                 RegistrationDemo.openPolicy("dev")) {

            assertEquals("dev",
                context.getBean(RegistrationDemo.FeaturePolicy.class)
                    .name());
            assertTrue(context.containsBean("policy"));
        }
    }

    @Test
    void prodProfileActivatesOnlyProdPolicy() {
        try (AnnotationConfigApplicationContext context =
                 RegistrationDemo.openPolicy("prod")) {

            assertEquals("prod",
                context.getBean(RegistrationDemo.FeaturePolicy.class)
                    .name());
            assertTrue(context.containsBean("policy"));
        }
    }

    @Test
    void otherProfileDoesNotRegisterEitherPolicy() {
        try (AnnotationConfigApplicationContext context =
                 RegistrationDemo.openPolicy("qa")) {

            assertFalse(context.containsBean("policy"));
            assertTrue(
                context.getBeansOfType(
                    RegistrationDemo.FeaturePolicy.class).isEmpty());
        }
    }

    @Test
    void conditionFalseDoesNotRegisterBean() {
        try (AnnotationConfigApplicationContext context =
                 RegistrationDemo.openFeature(false)) {

            assertFalse(context.containsBeanDefinition("auditSink"));
            assertFalse(context.containsBean("auditSink"));
        }
    }

    @Test
    void conditionTrueRegistersBean() {
        try (AnnotationConfigApplicationContext context =
                 RegistrationDemo.openFeature(true)) {

            assertTrue(context.containsBeanDefinition("auditSink"));
            assertEquals("AUDIT",
                context.getBean(RegistrationDemo.AuditSink.class).id());
        }
    }

    @Test
    void changingPropertyAfterRefreshDoesNotReevaluateCondition() {
        try (AnnotationConfigApplicationContext context =
                 RegistrationDemo.openFeature(true)) {

            context.getEnvironment()
                .getPropertySources()
                .addFirst(new org.springframework.core.env.MapPropertySource(
                    "runtime-override",
                    java.util.Map.of(
                        "feature.audit.enabled", "false")));

            assertEquals("false",
                context.getEnvironment().getProperty(
                    "feature.audit.enabled"));

            assertTrue(context.containsBean("auditSink"));
        }
    }
}
~~~

### Chạy unit/integration tests

~~~bash
mvn -q test
~~~

Có **8 JUnit Jupiter tests**. Nếu code và dependencies như trên, kết quả dự kiến **8 tests, 0 failures, 0 errors**. Báo cáo Surefire nằm trong `target/surefire-reports`; một vài Spring startup warning logs có thể xuất hiện do negative test `wrongScanFailsToProvideRequiredNotifier`, nhưng `assertThrows` xác nhận đó là lỗi mong đợi.

### Vì sao nên có cả negative tests?

Chỉ nhìn happy-path correct scan không chứng minh false scenario thật sự xảy ra. Bộ tests này cố ý loại provider khỏi scan và tắt các registration conditions để xác nhận:

- Lỗi không được âm thầm thay bằng bean giả.
- Context hỏng fail-fast.
- Bean không tồn tại khi profile/condition không match.
- Context đúng nạp đủ dependencies.

## 34. Biện pháp sửa theo từng tình huống

| Tình huống | Cần sửa | Không nên làm |
| --- | --- | --- |
| Component nằm ngoài scan | Điều chỉnh base package/marker | Scan tất cả packages bừa bãi |
| Bean bị exclude filter | Sửa/filter chính xác | Xóa mọi filters không xem xét |
| useDefaultFilters=false | Thêm include filter hoặc bật default filters | Nghĩ @Component vẫn luôn được scan |
| @Bean config không được nạp | `register` hoặc `@Import` | Chỉ thêm @Bean mà không load config |
| @Profile không match | Kiểm tra active/default profiles | Đổi profile sau refresh và kỳ vọng hot reload |
| @Conditional false | Kiểm tra condition và Environment/metadata | Gọi getBean liên tục chờ condition đổi |
| Boot @ConditionalOnProperty false | Kiểm tra property và evaluation report | Giả định bare spring-context có Boot API |
| Hai implementations cùng type | Qualifier, primary hoặc điều kiện loại trừ | Xử lý như missing bean |
| Provider ở child, consumer ở parent | Sửa hierarchy/placement | Kỳ vọng parent tìm xuống child |
| Có definition nhưng factory ném | Sửa constructor/factory/lifecycle | Mở rộng scan để “chữa” |

Bảng này giúp tách registration diagnosis khỏi dependency resolution và bean creation diagnosis.

## 35. Câu hỏi tự kiểm tra

**Câu 1:** Có `@Service` nhưng Spring không inject được. Vì sao?

**Đáp án:** Annotation không tự đăng ký bean nếu component scan hoặc import/explicit registration không bao phủ class.

**Câu 2:** `@ComponentScan` không có basePackages sẽ scan đâu?

**Đáp án:** Package chứa class khai báo `@ComponentScan` và classpath resources dưới package đó.

**Câu 3:** `basePackageClasses=NotifierComponent.class` có chỉ quét một class không?

**Đáp án:** Không. Nó xác định **package** của class để scan.

**Câu 4:** `useDefaultFilters=false` làm gì?

**Đáp án:** Tắt detection mặc định của `@Component` stereotypes, trừ những includeFilters phù hợp được cung cấp.

**Câu 5:** `@Profile({"dev","qa"})` nghĩa là cả hai phải active?

**Đáp án:** Không. Các entries trong mảng có semantics OR, một profile match là đủ.

**Câu 6:** `@Profile("dev & cloud")` biểu thị gì?

**Đáp án:** Profile expression yêu cầu cả dev và cloud match.

**Câu 7:** `@Profile("default")` có luôn match ở production không?

**Đáp án:** Không, default profile chỉ áp khi không có active profiles tường minh.

**Câu 8:** Vì sao đổi active profile sau refresh không tạo bean?

**Đáp án:** Profile được dùng khi xử lý/đăng ký bean definitions ở startup, không phải dynamic recomputation mỗi getBean.

**Câu 9:** `@Conditional` có chạy lại khi property thay đổi không?

**Đáp án:** Không tự động. Nó kiểm soát registration, không phải watcher runtime cho properties.

**Câu 10:** `@Conditional({A.class,B.class})` yêu cầu gì?

**Đáp án:** Tất cả điều kiện A và B phải match.

**Câu 11:** `@ConditionalOnProperty` thuộc Spring Framework Core hay Boot?

**Đáp án:** Nó thuộc **Spring Boot**; Framework Core cung cấp `@Conditional` và `Condition`.

**Câu 12:** Missing bean và constructor error giống nhau không?

**Đáp án:** Không. Missing definition/candidate là một nguyên nhân; constructor ném exception dù definition tồn tại là nguyên nhân khác.

**Câu 13:** Nếu component scan quá rộng xuất hiện hai candidates cùng type, lỗi gì?

**Đáp án:** Có thể `NoUniqueBeanDefinitionException` nếu Spring cần một ứng viên mà không có quy tắc lựa chọn duy nhất.

**Câu 14:** Condition có nên gọi getBean để quyết định không?

**Đáp án:** Không nên; nó chạy sớm khi đang xây metadata, bean instances chưa chắc đã sẵn sàng.

**Câu 15:** Tại sao context test phải dùng try-with-resources?

**Đáp án:** Để gọi close, cleanup singleton lifecycle và giảm rò rỉ qua các scenarios.

## 36. Checklist chẩn đoán

- [ ] Đã đọc exception root cause thay vì chỉ thông báo bề mặt?
- [ ] Đã xác định consumer bean và injection point?
- [ ] Required name/type có đúng không?
- [ ] Implementation class tồn tại trong runtime classpath?
- [ ] Implementation có stereotype annotation thích hợp?
- [ ] Bean được đăng ký bằng scan, @Bean, import hay thủ công?
- [ ] @ComponentScan basePackages/basePackageClasses đúng?
- [ ] Nếu không cấu hình base, đã kiểm tra package của config class?
- [ ] Có excludeFilters/includeFilters/useDefaultFilters ảnh hưởng?
- [ ] Package scanning có quá rộng gây duplicate candidates?
- [ ] @Configuration có được register/import?
- [ ] Active/default profiles có đúng lúc refresh?
- [ ] Class-level và method-level @Profile được hiểu đúng?
- [ ] Có mảng profiles và biểu thức OR/AND cần phân biệt?
- [ ] Có overloaded @Bean methods và profile conditions không nhất quán?
- [ ] @Conditional có thực sự match trong Environment hiện tại?
- [ ] Properties/PropertySources có giá trị và precedence đúng?
- [ ] Custom Condition có tránh gọi application beans sớm?
- [ ] Có conditions thuộc Spring Boot nhưng thiếu Boot infrastructure?
- [ ] Có điều kiện false do classpath/auto-config exclusion?
- [ ] Có BeanDefinition trong đúng context, không chỉ ancestor/child khác?
- [ ] Bean sai scope/type/constructor chứ không phải missing definition?
- [ ] Có kiểm thử context nhỏ cho từng condition?
- [ ] Có positive và negative JUnit tests để khóa registration behavior?
- [ ] Đã tránh sửa bằng cách mở scan vô hạn hoặc bật profiles vô tội vạ?
- [ ] Có giữ context test được close, không rò resources?

## 37. Thực hành mở rộng

### Bài tập A — sửa sai scan

Thay WrongScanConfig thành:

~~~java
@ComponentScan(basePackageClasses = ScannedNotifier.class)
~~~

Dự đoán `wrong.refresh` không còn fail, vì Scanner có provider Notifier. Sau đó chạy Maven tests và cập nhật assertion của negative test khi đổi cấu hình.

### Bài tập B — giả lập exclude filter

Trên CorrectScanConfig, thêm:

~~~java
excludeFilters = @ComponentScan.Filter(
    type = FilterType.ASSIGNABLE_TYPE,
    classes = ScannedNotifier.class)
~~~

Quan sát provider bị loại và `CoreConfig` fail khi tạo WorkService. Cần import `FilterType` nếu dùng.

### Bài tập C — profile expression

Đổi `@Profile("dev")` thành `@Profile("dev & cloud")`, rồi thử `setActiveProfiles("dev")` và `setActiveProfiles("dev","cloud")`. Chú ý trong demo, ProdPolicyConfig sẽ không match nếu chỉ bật dev; với thiếu policy, lookup theo type phải fail.

### Bài tập D — Condition class-level

Chuyển `@Conditional(WhenAuditEnabled.class)` từ method auditSink lên FeatureConfig class, tạo thêm một `@Bean` healthProbe khác, quan sát cả config bị loại khi property false.

### Bài tập E — Boot-specific property condition

Trong một project **Spring Boot riêng**, thay custom Condition bằng `@ConditionalOnProperty(prefix="feature.audit",name="enabled",havingValue="true")`. Kiểm tra Boot condition evaluation report để hiểu khác biệt với Framework Core.

### Bài tập F — negative profile and condition tests

Tạo contexts active dev, prod, qa và flag values true/false. Với mỗi scenario, assert `containsBeanDefinition`, `getBeansOfType` và behavior của consumer. Không cần component-scan toàn application.

## 38. Tổng kết

**Sai phạm vi quét** nghĩa là class không lọt vào candidate detection hoặc bị scan filter loại. **Profile không khớp** nghĩa là một configuration/component/bean method bị loại khỏi registration theo active/default profiles. **Condition không khớp** nghĩa là framework condition trả false trong giai đoạn xử lý metadata.

Ba nguyên nhân có thể cùng dẫn tới thiếu bean, nhưng **biện pháp sửa khác nhau**. Luôn lần từ exception → required dependency → registration path → scan package/filter → profile → conditional metadata → context hierarchy → bean creation.

`@Profile` và `@Conditional` là **registration-time mechanisms**, không phải cơ chế refresh bean graph tự động khi môi trường thay đổi. Với Spring Boot, các `@ConditionalOn...` annotations bổ sung tiện ích và diagnostics, nhưng cần phân biệt chúng với `@Conditional` của Framework Core.

Tốt nhất là tạo **một context nhỏ có thể tái hiện** và thêm JUnit tests cho cả trường hợp xuất hiện và không xuất hiện bean. Khi sửa đúng nguyên nhân, cấu hình trở nên dễ đọc, fail-fast và ít phụ thuộc vào máy chạy hơn.

## 39. Tài liệu chính thức

- [Spring Framework 6.1 — ComponentScan Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/annotation/ComponentScan.html): basePackages, basePackageClasses, useDefaultFilters, include/exclude filters.
- [Spring Framework Reference — Classpath Scanning and Managed Components](https://docs.spring.io/spring-framework/reference/6.1/core/beans/classpath-scanning.html): stereotype detection, scanning, filters.
- [Spring Framework 6.1 — Profile Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/annotation/Profile.html): class/method profile conditions và overloaded bean factory methods.
- [Spring Framework Reference — Environment Abstraction](https://docs.spring.io/spring-framework/reference/6.1/core/beans/environment.html): active/default profiles, property sources và setActiveProfiles trước refresh.
- [Spring Framework 6.1 — Conditional Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/annotation/Conditional.html): registration eligibility và composed annotations.
- [Spring Framework 6.1 — Condition Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/annotation/Condition.html): matches(ConditionContext, AnnotatedTypeMetadata).
- [Spring Framework 6.1 — ConfigurationCondition Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/annotation/ConfigurationCondition.html): configuration parsing vs registration phase.
- [Spring Framework 6.1 — AnnotationConfigApplicationContext Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/annotation/AnnotationConfigApplicationContext.html): register/scan/refresh lifecycle.
- [Spring Framework 6.1 — NoSuchBeanDefinitionException Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/beans/factory/NoSuchBeanDefinitionException.html): missing candidate error.
- [Spring Boot Reference — Creating Your Own Auto-configuration](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html): Boot-specific `@ConditionalOn...` configuration conditions.
- [JUnit 5 User Guide](https://docs.junit.org/5.11.1/user-guide/): Jupiter tests và assertions.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="troubleshooting-007">Sai kiểu trả về, kiểu proxy và chuyển đổi cấu hình</a>

<details>
<summary>Xem chi tiết</summary>

# Sai kiểu trả về, kiểu proxy và chuyển đổi cấu hình trong Spring Core Container

## 1. Bài toán: ba kiểu lỗi thường bị nhầm làm một

Khi Spring không tạo được bean hoặc một dependency không được tiêm đúng, thông báo ban đầu có thể đều bắt đầu bằng `BeanCreationException`, `UnsatisfiedDependencyException` hoặc `NoSuchBeanDefinitionException`. Tuy nhiên, ba tình huống rất khác nhau cần tách biệt:

1. **Sai kiểu bean thực tế hoặc kiểu được yêu cầu**: lấy một bean theo tên nhưng ép sang một Java type không tương thích. Có thể gặp `BeanNotOfRequiredTypeException`.
2. **Sai kỳ vọng về proxy**: bean target là một concrete class, nhưng Spring AOP trả về **JDK dynamic proxy** chỉ implements interface; consumer cố tiêm/lookup concrete class nên thất bại. Khi class-based proxy được sử dụng, còn có giới hạn class/method `final` và self-invocation.
3. **Sai chuyển đổi giá trị cấu hình**: một property như `app.maxRetries=seven` phải được tiêm vào `int`, hoặc giá trị `app.endpoint=example.test:8443` phải convert sang kiểu domain tự định nghĩa mà thiếu `Converter`. Những lỗi này thường xuất hiện **trong bean creation** và có exception wrappers giống lỗi dependency khác.

Mục tiêu là **chẩn đoán đúng lớp vấn đề trước khi sửa**. Việc thêm `@Primary`, đổi proxy strategy, tạo một bean tên `conversionService` hay thêm `@Lazy` chỉ hữu ích nếu bạn biết cơ chế nào đang sai. Một sửa đổi thiếu mục đích có thể đổi biểu hiện lỗi mà không chữa root cause.

### Sau bài này bạn cần làm được

- Phân biệt **bean name**, **declared type**, **runtime type**, **target class**, **proxy type** và **injection point required type**.
- Đọc exception tree để nhận diện `BeanNotOfRequiredTypeException`, `NoSuchBeanDefinitionException`, `NoUniqueBeanDefinitionException`, `BeanCreationException` và `ConversionFailedException`.
- Hiểu Spring AOP dùng **JDK proxy** với interface và **class-based proxy (CGLIB)** khi được chọn; không xem class target là luôn assignable từ proxy.
- Kiểm tra các BeanFactory APIs như `getBean(name, type)`, `getBean(type)`, `getType(name)` và `isTypeMatch(name, type)` theo cách không gây khởi tạo không cần thiết.
- Biết kiểm tra AOP bằng `AopUtils.isAopProxy`, `isJdkDynamicProxy` và `isCglibProxy`; phân biệt với `Proxy.isProxyClass` của JDK.
- Phân biệt `@Bean` method declared return type, actual bean type, và type của `FactoryBean` product.
- Dùng `@Value("${...}")` cho int/boolean/String cùng một **ConversionService** phù hợp khi cần domain type.
- Hiểu type conversion của **BeanFactory** không tự đồng nghĩa conversion của **Environment**.
- Viết test cho cả happy path và failure path, tránh assert exact exception message thay đổi theo Spring version.
- Chạy chương trình **Java 17 + Maven + Spring Framework 6.2.19**, tạo các contexts cô lập, phát hiện ba lỗi và in kết quả dự kiến.

## 2. Mô hình sáu lớp để debug một bean

~~~text
BeanDefinition registration
   |
   +-- beanName: "pricing"
   +-- declared return type: PricingPort (interface)
   +-- bean supplier / @Bean method: tạo implementation/proxy
   |
   v
Spring BeanFactory
   |
   +-- choose bean candidate using type/name/qualifier/primary
   +-- resolve dependencies
   +-- instantiate target
   +-- post-process / proxy nếu có
   |
   v
Object được expose cho consumer
   |
   +-- runtime type: jdk.proxy...$ProxyN (nếu JDK proxy)
   +-- implements: PricingPort
   +-- not assignable to: PricingService concrete class
   |
   v
Consumer injection or lookup
   |
   +-- required type: PricingPort       => hợp lệ
   +-- required type: PricingService    => có thể không hợp lệ
~~~

Không nên lấy tên class từ stack trace rồi suy ra mọi bước đều dùng cùng class. Trong AOP, một bean có thể có:

- **Target class**: class chứa nghiệp vụ, ví dụ `PricingService`.
- **Runtime/exposed class**: proxy class mà Spring đưa ra ngoài, ví dụ JDK `Proxy` hoặc CGLIB subclass.
- **Declared bean type**: chữ ký `@Bean PricingPort pricing()`; dùng trong giai đoạn phân tích type/candidates.
- **Required injection type**: tham số constructor hoặc kiểu truyền vào `getBean(name, requiredType)`.

**Một target implements interface không chứng minh object proxy cũng là instance của concrete target class.** Với JDK proxy, phép ép sang concrete class sẽ không hợp lệ.

## 3. BeanNotOfRequiredTypeException: đã tìm thấy bean nhưng type sai

`BeanNotOfRequiredTypeException` là một exception của Spring BeanFactory có thông tin về **bean name**, **required type** và **actual type**.

~~~java
PricingPort port = context.getBean("pricing", PricingPort.class);
// Hợp lệ nếu bean pricing implements PricingPort.

PricingService concrete =
        context.getBean("pricing", PricingService.class);
// Có thể ném BeanNotOfRequiredTypeException khi pricing là JDK proxy.
~~~

Ở lời gọi thứ hai, bean **được tìm thấy theo tên**, nhưng exposed object không assignable sang `PricingService`.

Các getters hữu ích:

~~~java
catch (BeanNotOfRequiredTypeException ex) {
    String name = ex.getBeanName();
    Class<?> requested = ex.getRequiredType();
    Class<?> actual = ex.getActualType();
}
~~~

Thực tế chẩn đoán: log `name`, `requested.getName()`, `actual.getName()` và bean definition/feature liên quan. Tránh chỉ grep một cụm string từ message; dùng fields có cấu trúc.

### Đừng nhầm với NoSuchBeanDefinitionException

`NoSuchBeanDefinitionException` thường nghĩa là **không tồn tại bean candidate phù hợp** theo tên/type resolution; nó không hứa rằng một bean tồn tại dưới tên ấy với type không hợp lệ.

~~~java
context.getBean("unregisteredPricing");
// Bean name không có -> NoSuchBeanDefinitionException.
~~~

Với `getBean(PricingService.class)` trong một container chỉ expose interface-typed proxy `PricingPort`, **type lookup** có thể không nhận ra candidate concrete và ném `NoSuchBeanDefinitionException` thay vì `BeanNotOfRequiredTypeException`. Trong khi `getBean("pricing", PricingService.class)` rõ tên hơn và thường báo wrong required type.

**Luật chẩn đoán:** Cùng một nguyên nhân thiết kế “consumer yêu cầu concrete class, proxy không assignable” có thể hiện thành các exception khác nhau tùy lookup path.

## 4. NoUniqueBeanDefinitionException: đúng type nhưng nhiều candidates

Giả sử:

~~~java
interface PricingPort {
    String quote(int cents);
}

class OnlinePricing implements PricingPort { /* ... */ }
class OfflinePricing implements PricingPort { /* ... */ }
~~~

Nếu cả hai được register và một constructor chỉ nhận `PricingPort` không qualifier, Spring có thể không chọn được candidate duy nhất:

~~~text
NoUniqueBeanDefinitionException:
expected single matching bean but found 2
~~~

Không nên sửa bằng cách đổi implementation thành một concrete class chỉ để “tránh ambiguity”; nó làm tăng coupling và có thể vấp JDK proxy mismatch khi AOP được bật.

Các cách giải phù hợp:

- `@Qualifier("onlinePricing")` để chọn bean rõ ràng.
- `@Primary` nếu một implementation thật sự là default.
- `Map<String, PricingPort>` hoặc `List<PricingPort>` khi consumer cần cả tập.
- Thiết kế strategy registry nếu chọn implementation theo runtime input.
- Đặt bean name ổn định; đừng trông chờ registration order quyết định single-valued injection.

### Bean name vs @Qualifier

`@Qualifier` là metadata lọc candidates; bean name có thể tham gia matching nhưng không nên hiểu qualifier lúc nào cũng tương đương “tên bean duy nhất”. Với tên interface hoặc nhiều implementations, kiểm tra rõ `@Bean` names, bean aliases, primary/fallback/default candidate metadata theo phiên bản Spring.

## 5. JDK dynamic proxy: object công khai không là subclass của target

Spring AOP 6.2 có hai proxy strategies phổ biến:

- **JDK dynamic proxy** cho interface-based proxying.
- **CGLIB class-based proxy** khi cần proxy concrete class hoặc cấu hình `proxyTargetClass=true`.

Một JDK proxy implements một hoặc nhiều interfaces, nhưng **không extends concrete target class**:

~~~text
                PricingPort (interface)
                      ▲
                      │ implements
                 JDK $ProxyNN
                      │
                      └── delegates through interceptor to
                                   PricingService
                                   implements PricingPort

   $ProxyNN instanceof PricingPort       => true
   $ProxyNN instanceof PricingService    => false
~~~

Kể cả PricingService implements PricingPort, object `$ProxyNN` vẫn không phải instance của PricingService. Java type system không có cơ chế “proxy của T nghĩa là T concrete assignable”.

### ProxyFactory ví dụ rút gọn

~~~java
ProxyFactory factory = new ProxyFactory(new PricingService());
factory.setInterfaces(PricingPort.class);
factory.setProxyTargetClass(false);

PricingPort exposed = (PricingPort) factory.getProxy();
~~~

Cần Spring AOP trên classpath, interface/class definitions phù hợp. Trong mẫu này, chúng ta chỉ định **interface proxy strategy**. Với target implements PricingPort, exposed proxy có thể sử dụng được qua interface. Demo đầy đủ ở mục 14 kiểm tra `Proxy.isProxyClass` và `instanceof` trên object thực.

### Vì sao nó làm @Autowired concrete class fail?

~~~java
@Component
class CheckoutService {
    @Autowired PricingService pricing;  // Rủi ro nếu bean bị JDK proxy.
}
~~~

Injection point yêu cầu `PricingService`. Nếu BeanFactory chỉ expose một JDK proxy implements `PricingPort`, object không assignable sang class đó. Lỗi ở type resolution/assignment, **không phải PricingService mất một constructor**.

Thay bằng:

~~~java
@Component
class CheckoutService {
    private final PricingPort pricing;

    CheckoutService(PricingPort pricing) {
        this.pricing = pricing;
    }
}
~~~

Đây là hai snippets, cần Spring annotation infrastructure và class declarations. **Inject interface** là lựa chọn phù hợp khi consumer chỉ cần operations có trong PricingPort.

## 6. Khi nào cần class-based (CGLIB) proxy?

Một class proxy là một subclass được sinh ra, nên thông thường assignable sang class target:

~~~text
PricingService
      ▲ extends
PricingService$$SpringCGLIB$$...
      │
      └── proxy object được expose
~~~

Để yêu cầu class-based proxy trong một cấu hình phù hợp:

~~~java
@EnableAspectJAutoProxy(proxyTargetClass = true)
@Configuration
class AopConfig { }
~~~

Hoặc API programmatic:

~~~java
ProxyFactory factory = new ProxyFactory(new PricingService());
factory.setProxyTargetClass(true);
Object proxy = factory.getProxy();
~~~

**Nhưng “ép CGLIB cho tất cả” không luôn là cách sửa tốt**:

- Class `final` không thể được subclass.
- Method `final` không thể được proxy subclass override để advice.
- Method `private` không bị subclass proxy intercept theo semantics thông thường.
- Visibility/package boundaries và Java Module System có thể tạo hạn chế.
- Self-invocation qua `this.someMethod()` thường bỏ qua proxy advice trong Spring AOP proxy model.
- Đổi chiến lược proxy toàn ứng dụng có thể làm behavior khác ở nhiều beans, không chỉ bean bị lỗi.

**Ưu tiên inject interface** khi hợp đồng service được thiết kế theo interface. Chỉ chọn class-based proxy khi thực sự cần concrete methods và đã kiểm tra restrictions.

### Không nhầm JDK proxy với Spring bean JDK proxy

`Proxy.isProxyClass(bean.getClass())` nhận biết Java JDK proxy class. Nhưng một Spring AOP proxy có thể là CGLIB subclass; JDK `Proxy` không phát hiện class proxy. Có thể dùng:

~~~java
AopUtils.isAopProxy(bean);
AopUtils.isJdkDynamicProxy(bean);
AopUtils.isCglibProxy(bean);
~~~

Các methods ở `org.springframework.aop.support.AopUtils` nhận diện Spring AOP proxy types. Cẩn thận khi code dùng JDK Proxy trực tiếp không phải do Spring AOP tạo; `AopUtils.isAopProxy` có criteria khác `Proxy.isProxyClass`.

## 7. @Bean method return type: hợp đồng khai báo quyết định nhiều điều

Ví dụ:

~~~java
@Bean
PricingPort pricing() {
    return new PricingService();
}
~~~

Method signature nói **bean type tối thiểu là PricingPort**, còn actual object có thể là PricingService hoặc proxy. Khi nhiều frameworks phân tích BeanDefinitions **trước instantiation**, declared return type ảnh hưởng type matching, conditional wiring, metadata inference và lifecycle decisions.

### Khi nào trả interface?

Nếu mọi consumer cần interface `PricingPort`, khai báo factory method trả interface là tự nhiên. Nó phù hợp proxying nhưng **không phải mọi type lookup concrete đều hoạt động** trước/sau initialization.

### Khi nào trả concrete?

Nếu bean không proxy bằng JDK và một infrastructure cần introspect methods/annotations chỉ có ở implementation, return concrete class trong `@Bean` có thể giúp early type inference. Tuy nhiên nếu sau đó AOP dùng JDK proxy, declared concrete type vẫn **không biến exposed proxy thành concrete**.

Không chỉnh declared return type chỉ để làm IDE code completion đẹp mà bỏ qua cách bean được decorate/proxy trong runtime.

### Không khai báo sai bằng raw casts

Một lỗi Java rõ ràng:

~~~java
@Bean
PricingService pricing() {
    return (PricingService) someJdkProxy;
}
~~~

Nếu `someJdkProxy` không assignable, `ClassCastException` xảy ra trong factory method, thường bị bọc bởi `BeanCreationException`. Thêm unchecked cast không giải vấn đề; làm sai type contract.

## 8. FactoryBean: factory type, product type và dấu &

Spring `FactoryBean<T>` tạo ra một **product bean** thông qua `getObject()`. Tên bean trỏ tới product mặc định; prefix `&` dùng truy cập bản thân factory.

~~~text
BeanDefinition "connectionFactory"
       │
       ▼
FactoryBean<Connection>
       ├── getObjectType()  -> Connection.class
       ├── getObject()      -> product Connection
       └── isSingleton()    -> product caching policy

getBean("connectionFactory")  -> Connection product
getBean("&connectionFactory") -> FactoryBean instance
~~~

Vì vậy “sai kiểu trả về” có thể xuất hiện khi caller cần `FactoryBean` nhưng lại lookup product name, hoặc ngược lại.

### getObjectType rất quan trọng

`FactoryBean.getObjectType()` nên trả kiểu sản phẩm chính xác nếu có thể **mà không đòi tạo product**. Spring dùng thông tin này để type matching, autowiring và scanning. Nếu trả `null` hoặc quá tổng quát khi đã biết type, candidate resolution có thể thiếu thông tin.

`FactoryBean` là một abstraction khác với `@Bean` factory method hoặc `Supplier` trong programmatic BeanDefinition. Không xem ba cơ chế là một chỉ vì đều tạo object.

### Trường hợp nào có BeanNotOfRequiredTypeException?

Nếu `getBean("connectionFactory", SpecialFactory.class)` được gọi nhưng tên thường trả product, có thể báo wrong required type. Để lấy factory instance, dùng `getBean("&connectionFactory")` với đúng type. Không tự thêm `&` vào mọi bean name nếu bean đó không thực sự là FactoryBean.

## 9. @Value: placeholder, converted type và exception wrapping

Spring thường nhận cấu hình từ `Environment`, chẳng hạn:

~~~properties
app.maxRetries=5
app.enabled=true
app.endpoint=example.test:8443
~~~

Code:

~~~java
@Bean
Settings settings(
        @Value("${app.maxRetries}") int maxRetries,
        @Value("${app.enabled}") boolean enabled,
        @Value("${app.endpoint}") Endpoint endpoint) {
    return new Settings(maxRetries, enabled, endpoint);
}
~~~

Đây là snippet; cần record `Settings`, `Endpoint`, converter và config đầy đủ.

Spring xử lý:

~~~text
Property sources -> resolved placeholder string
    "5"
      |
      v
ConversionService / TypeConverter
      |
      v
int 5

Property sources -> "example.test:8443"
      |
      v
Converter<String, Endpoint>
      |
      v
new Endpoint("example.test", 8443)
~~~

### Khi nào lỗi xảy ra?

- Missing required property: unresolved placeholder hoặc dependency resolution failure, tùy placeholder configuration.
- `app.maxRetries=oops`: không thể convert sang int, thường wrapped khi Spring tạo bean.
- `app.endpoint=bad` mà không có converter: không có conversion path hoặc custom parser throw; thường failure trong bean creation.
- Có hai beans cùng type mà không chọn được: dependency candidate ambiguity, **không phải** lỗi conversion.
- Proxy không assignable sang concrete class: type mismatch, **không phải** lỗi số/property parsing.

Đừng khẳng định chỉ một exception class duy nhất luôn ở top level. **Đọc cause chain**, đặc biệt khi `@Value` nằm trên method/constructor parameters của bean factory.

## 10. Spring ConversionService và PropertyEditor

Spring có ConversionService SPI (`Converter<S,T>`, `ConverterFactory`, `GenericConverter`, `ConditionalGenericConverter`) và legacy `PropertyEditor`-based conversion. Với các kiểu cơ bản, conversion tự động hỗ trợ nhiều trường hợp như String→Integer/Boolean/Enum.

Cho domain type không có built-in converter, tự đăng ký:

~~~java
@Bean("conversionService")
static ConversionService conversionService() {
    DefaultConversionService service = new DefaultConversionService();
    service.addConverter(new EndpointConverter());
    return service;
}
~~~

Ở đây `DefaultConversionService` có built-in converters; `EndpointConverter` thêm đúng `String→Endpoint`. Bean tên `conversionService` được BeanFactory sử dụng theo convention khi container khởi tạo.

### Vì sao nên dùng DefaultConversionService?

Nếu tạo một `GenericConversionService` rỗng và thay thế BeanFactory converter, có thể **mất cả String→Integer và String→Boolean** cần cho những values vốn đã hoạt động. `DefaultConversionService` là điểm xuất phát ít rủi ro hơn trong Core Container.

### Converter không chọn PropertySource

Converter chỉ nhận source value **sau khi placeholder/property resolver đã chọn một value**. Nếu `@TestPropertySource` override `app.endpoint` thành chuỗi không hợp lệ, converter không nên tự tìm giá trị “đúng” ở một nguồn ưu tiên thấp hơn, trừ khi ứng dụng định nghĩa chính sách fallback rất cụ thể.

### @Value và Environment.getProperty khác nhau ở conversion wiring

`BeanFactory` có conversion service dùng trong property/constructor injection. `ConfigurableEnvironment` có conversion service của property resolver cho typed lookups:

~~~java
Endpoint endpoint = environment.getProperty(
        "app.endpoint", Endpoint.class);
~~~

Một bean `conversionService` trong BeanFactory **không tự bảo đảm** Environment resolver dùng cùng instance. Nếu bạn cần typed Environment lookup, đăng ký converter vào `ConfigurableEnvironment.getConversionService()` một cách rõ ràng. Trong demo runnable dưới đây, custom converter dùng cho **@Value BeanFactory path**; typed Environment path được thảo luận nhưng không khẳng định tự chạy.

## 11. Viết converter có hợp đồng đầu vào rõ ràng

Giả sử `Endpoint` biểu diễn hostname và port:

~~~java
public record Endpoint(String host, int port) {
    public Endpoint {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host required");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid port");
        }
    }
}
~~~

Converter:

~~~java
public final class EndpointConverter
        implements Converter<String, Endpoint> {
    @Override
    public Endpoint convert(String text) {
        String[] parts = text.split(":", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Expected host:port");
        }
        try {
            return new Endpoint(
                    parts[0].trim(),
                    Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Invalid port number", ex);
        }
    }
}
~~~

### Các tình huống validation

| Chuỗi nguồn | Kết quả |
| --- | --- |
| `example.test:8443` | Hợp lệ, Endpoint("example.test",8443) |
| ` example.test : 8443 ` | Hợp lệ sau trim |
| `:8443` | Host rỗng, reject |
| `example.test:0` | Port ngoài range, reject |
| `example.test:65536` | Port ngoài range, reject |
| `example.test:abc` | Port không phải integer, reject |
| `example.test` | Thiếu colon, reject |
| `a:b:c` | Quá nhiều colon, reject |

Ví dụ grammar này **không hỗ trợ IPv6 literal** có dấu `:` trong host. Nếu cấu hình thực tế cần IPv6, URL scheme, IDN hoặc credentials, dùng parser/typed configuration phù hợp. Một demo đơn giản không nên nhận những đầu vào nó không thể xác thực đầy đủ.

### Không phải validation chỉ là conversion

Converter xác nhận grammar và range; các rule khác có thể cần validate: port không reserved, hostname theo allowlist, connection timeout hợp lý, TLS required. Bean Validation/@ConfigurationProperties binding là tầng khác, đặc biệt với Spring Boot.

## 12. PropertySourcesPlaceholderConfigurer: khi nào cần?

`@Value("${...}")` placeholder thường được xử lý trong Spring annotation-based contexts. `PropertySourcesPlaceholderConfigurer` là một `BeanFactoryPostProcessor` giúp xử lý property placeholders trong bean definitions/metadata, đặc biệt khi muốn strict checking với unresolved placeholders, XML/legacy và customized property source handling.

~~~java
@Bean
static PropertySourcesPlaceholderConfigurer placeholders() {
    return new PropertySourcesPlaceholderConfigurer();
}
~~~

Dùng `static @Bean` giúp infrastructure bean được đăng ký ở giai đoạn phù hợp mà không instantiate cấu hình chứa nó sớm không cần thiết.

### Không nghĩ PSPC là converter

PSPC xử lý placeholder values. Chuyển `String→Endpoint` cần ConversionService/Converter; thêm PSPC **không** tạo converter cho record tùy chỉnh.

### Không nghĩ converter là placeholder source

Đăng ký `Converter<String, Endpoint>` không làm `${app.endpoint}` tự tìm thấy key. Bạn vẫn cần `@PropertySource`, `@TestPropertySource` hoặc property source khác cung cấp giá trị.

### Khi nào Environment.getProperty cho giá trị khác @Value?

Nếu bạn đổi property source sau khi singleton đã được tạo, một phép Environment lookup sau đó có thể thấy giá trị mới, trong khi `@Value` parameter của singleton đã resolve từ thời điểm bean creation. Đây là **khác timing**, không hẳn khác conversion. Đừng sửa live property sources rồi kỳ vọng singleton tự được inject lại.

## 13. Chiến lược kiểm chứng: tách các lỗi ra contexts khác nhau

Một integration test có cấu hình sai vừa về proxy type vừa về numeric property sẽ thường fail ngay ở lỗi đầu tiên; lỗi thứ hai bị che. Để dạy cơ chế chính xác, hãy tách:

~~~text
Context A — GoodConfig:
  pricing = JDK interface-based Spring AOP proxy
  settings = typed, converted from valid properties
  checkout = constructor-injected PricingPort + Settings

  Assert:
    pricing instanceof PricingPort = true
    pricing instanceof PricingService = false
    Proxy.isProxyClass(pricing.getClass()) = true
    typed settings đúng

  Deliberate wrong lookups:
    getBean("pricing", PricingService.class)
       -> BeanNotOfRequiredTypeException
    getBean("missingBean")
       -> NoSuchBeanDefinitionException

Context B — InvalidConfig:
  app.maxRetries=not-a-number
  @Bean settings(@Value("${app.maxRetries}") int maxRetries)
       -> context.refresh() fails with BeansException

Separate conversion check:
  conversionService.convert("bad-endpoint", Endpoint.class)
       -> ConversionFailedException
~~~

Bằng cách này, main program có thể **bắt đúng failure paths mong đợi** rồi tiếp tục kiểm chứng happy path. Không cần cố giữ một ApplicationContext bị refresh fail để xử lý nghiệp vụ.

### Dùng test đúng ở mức độ nào?

- Unit test pure `EndpointConverter`: không cần Spring context.
- BeanFactory integration test `@Value`: cần Spring context với conversionService.
- Proxy test: cần Spring AOP `ProxyFactory` hoặc aspect/proxy infrastructure tương ứng.
- Spring MVC/WebFlux test: conversion/binding infrastructure riêng, chỉ dựng thêm nếu bài toán thuộc web layer.
- Boot auto-config test: xem Boot test slices/`ApplicationConversionService` chứ không suy ra từ plain Spring.

## 14. Ví dụ hoàn chỉnh Java 17 / Maven / Spring Framework 6.2.19

Dự án dùng:

- Java 17.
- Maven 3.9+.
- `spring-context:6.2.19` và `spring-aop:6.2.19`.
- Hai Spring contexts độc lập (một valid, một invalid).
- `@PropertySource` nạp two distinct files.
- `ProxyFactory` tạo JDK interface proxy với target thật.
- `@Bean("conversionService")` cung cấp custom converter.
- `@Value` inject typed int, boolean, Endpoint.
- Assertions trong `main` ném `IllegalStateException` nếu hành vi khác kỳ vọng.

### Cấu trúc thư mục

~~~text
spring-diagnose-types/
├── pom.xml
└── src/main/
    ├── java/example/DiagnoseTypesDemo.java
    └── resources/
        ├── demo-good.properties
        └── demo-bad.properties
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-diagnose-types</artifactId>
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
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
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

### src/main/resources/demo-good.properties

~~~properties
app.maxRetries=5
app.enabled=true
app.endpoint=example.test:8443
~~~

### src/main/resources/demo-bad.properties

~~~properties
app.maxRetries=not-a-number
~~~

### src/main/java/example/DiagnoseTypesDemo.java

~~~java
package example;

import java.lang.reflect.Proxy;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.DefaultConversionService;

public final class DiagnoseTypesDemo {

    public static void main(String[] args) {

        try (var ctx = new AnnotationConfigApplicationContext(
                GoodConfig.class)) {

            PricingPort pricing =
                    ctx.getBean("pricing", PricingPort.class);
            Checkout checkout = ctx.getBean(Checkout.class);
            Settings settings = ctx.getBean(Settings.class);

            boolean jdkProxy =
                    Proxy.isProxyClass(pricing.getClass());
            boolean interfaceType =
                    pricing instanceof PricingPort;
            boolean concreteType =
                    pricing instanceof PricingService;

            require(jdkProxy, "expected JDK proxy");
            require(interfaceType, "proxy must implement PricingPort");
            require(!concreteType,
                    "JDK proxy must not extend PricingService");
            require("QUOTE:120".equals(checkout.quote(120)),
                    "interface injection works");
            require(settings.maxRetries() == 5,
                    "String to int conversion");
            require(settings.enabled(),
                    "String to boolean conversion");
            require(new Endpoint("example.test", 8443)
                            .equals(settings.endpoint()),
                    "String to Endpoint conversion");

            boolean concreteRejected;
            try {
                ctx.getBean("pricing", PricingService.class);
                concreteRejected = false;
            } catch (BeanNotOfRequiredTypeException ex) {
                concreteRejected =
                        "pricing".equals(ex.getBeanName()) &&
                        ex.getRequiredType() == PricingService.class;
            }
            require(concreteRejected, "wrong required type rejection");

            boolean missingRejected;
            try {
                ctx.getBean("missingBean");
                missingRejected = false;
            } catch (NoSuchBeanDefinitionException ex) {
                missingRejected = true;
            }
            require(missingRejected, "missing bean name rejection");

            ConversionService converter =
                    ctx.getBean("conversionService",
                            ConversionService.class);
            boolean invalidEndpointRejected;
            try {
                converter.convert("invalid-endpoint",
                        Endpoint.class);
                invalidEndpointRejected = false;
            } catch (ConversionFailedException ex) {
                invalidEndpointRejected = true;
            }
            require(invalidEndpointRejected,
                    "invalid custom conversion rejection");

            System.out.println("JDK proxy: " + jdkProxy);
            System.out.println("Interface type: " + interfaceType);
            System.out.println("Concrete type: " + concreteType);
            System.out.println("Quote via interface: "
                    + checkout.quote(120));
            System.out.println("Retries: "
                    + settings.maxRetries());
            System.out.println("Enabled: " + settings.enabled());
            System.out.println("Endpoint: "
                    + settings.endpoint().host() + ":"
                    + settings.endpoint().port());
            System.out.println("Concrete lookup rejected: "
                    + concreteRejected);
            System.out.println("Missing name rejected: "
                    + missingRejected);
            System.out.println("Invalid Endpoint rejected: "
                    + invalidEndpointRejected);
        }

        boolean invalidNumericRejected = false;
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(BadConfig.class);
            try {
                ctx.refresh();
            } catch (BeansException ex) {
                invalidNumericRejected = true;
            }
        }

        require(invalidNumericRejected,
                "malformed int property must fail");
        System.out.println("Invalid number rejected: "
                + invalidNumericRejected);
        System.out.println("Verification: PASS");
    }

    private static void require(boolean test, String detail) {
        if (!test) {
            throw new IllegalStateException(
                    "Verification failed: " + detail);
        }
    }

    public interface PricingPort {
        String quote(int cents);
    }

    public static final class PricingService
            implements PricingPort {
        @Override
        public String quote(int cents) {
            return "QUOTE:" + cents;
        }
    }

    public record Endpoint(String host, int port) {
        public Endpoint {
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException(
                        "Host required");
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException(
                        "Port out of range");
            }
        }
    }

    public static final class EndpointConverter
            implements Converter<String, Endpoint> {

        @Override
        public Endpoint convert(String text) {
            String[] pieces = text.split(":", -1);
            if (pieces.length != 2) {
                throw new IllegalArgumentException(
                        "Expected host:port");
            }
            try {
                return new Endpoint(
                        pieces[0].trim(),
                        Integer.parseInt(pieces[1].trim()));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid port number", ex);
            }
        }
    }

    public record Settings(
            int maxRetries,
            boolean enabled,
            Endpoint endpoint) { }

    public static final class Checkout {
        private final PricingPort pricing;
        private final Settings settings;

        public Checkout(PricingPort pricing, Settings settings) {
            this.pricing = pricing;
            this.settings = settings;
        }

        public String quote(int cents) {
            if (settings.maxRetries() < 1) {
                throw new IllegalStateException(
                        "Retries must be positive");
            }
            return pricing.quote(cents);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @PropertySource("classpath:demo-good.properties")
    public static class GoodConfig {

        @Bean("conversionService")
        static ConversionService conversionService() {
            DefaultConversionService service =
                    new DefaultConversionService();
            service.addConverter(new EndpointConverter());
            return service;
        }

        @Bean("pricing")
        PricingPort pricing() {
            ProxyFactory factory =
                    new ProxyFactory(new PricingService());
            factory.setInterfaces(PricingPort.class);
            factory.setProxyTargetClass(false);
            return (PricingPort) factory.getProxy();
        }

        @Bean
        Settings settings(
                @Value("${app.maxRetries}") int maxRetries,
                @Value("${app.enabled}") boolean enabled,
                @Value("${app.endpoint}") Endpoint endpoint) {
            return new Settings(
                    maxRetries, enabled, endpoint);
        }

        @Bean
        Checkout checkout(
                PricingPort pricing, Settings settings) {
            return new Checkout(pricing, settings);
        }
    }

    public record BadSettings(int maxRetries) { }

    @Configuration(proxyBeanMethods = false)
    @PropertySource("classpath:demo-bad.properties")
    public static class BadConfig {

        @Bean
        BadSettings badSettings(
                @Value("${app.maxRetries}") int maxRetries) {
            return new BadSettings(maxRetries);
        }
    }
}
~~~

### Cách chạy

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.DiagnoseTypesDemo
~~~

Kết quả **từ các lệnh `System.out.println` trong source**, không tính Spring/Maven logging hay thông báo về một context được cố ý refresh thất bại:

~~~text
JDK proxy: true
Interface type: true
Concrete type: false
Quote via interface: QUOTE:120
Retries: 5
Enabled: true
Endpoint: example.test:8443
Concrete lookup rejected: true
Missing name rejected: true
Invalid Endpoint rejected: true
Invalid number rejected: true
Verification: PASS
~~~

**Không coi đây là log build đã được thực thi trong môi trường hiện tại.** Nó là stdout kỳ vọng nếu files/dependencies đúng như bài và không bị custom infrastructure sửa đổi.

## 15. Đối chiếu từng assertion trong chương trình

### ProxyFactory thật sự đưa ra JDK proxy

`factory.setInterfaces(PricingPort.class)` xác định user-supplied proxy interfaces; `setProxyTargetClass(false)` cho phép interface proxy. Object exposed là một JDK proxy được tạo từ target PricingService. Vì vậy:

~~~java
Proxy.isProxyClass(pricing.getClass()) // true
pricing instanceof PricingPort        // true
pricing instanceof PricingService     // false
~~~

Không so tên class proxy với một chuỗi hard-coded như `jdk.proxy1.$Proxy0` vì tên/loader/module có thể khác theo JDK và runtime.

### getBean(name, concreteType) cố ý sai

~~~java
ctx.getBean("pricing", PricingService.class);
~~~

Tên `pricing` tồn tại, nhưng actual exposed bean là JDK proxy không extends PricingService. `BeanNotOfRequiredTypeException` có cấu trúc chẩn đoán `beanName/requiredType/actualType`. Demo chỉ xác nhận những fields ổn định, không so whole error message hoặc toàn bộ runtime proxy class name.

### Missing bean name là loại lỗi khác

~~~java
ctx.getBean("missingBean");
~~~

Không có bean tên này, nên `NoSuchBeanDefinitionException` là loại lỗi đúng. Đừng biến nó thành “proxy có vấn đề” khi name đơn giản chưa được đăng ký.

### Numeric property trong context lỗi

BadConfig nạp `demo-bad.properties` với `app.maxRetries=not-a-number`, rồi tiêm nó vào `int`. Spring phải chuyển từ String sang primitive int khi tạo BadSettings; phép chuyển không hợp lệ, nên refresh thất bại bằng một `BeansException` thích hợp. Demo không phụ thuộc message/cause depth vì số tầng wrapping có thể thay đổi theo code path và phiên bản.

### Custom Endpoint converter negative path

`DefaultConversionService.convert("invalid-endpoint",Endpoint.class)` tìm converter, converter ném IllegalArgumentException vì không có `:`. `ConversionService` bọc lỗi tạo bởi converter vào `ConversionFailedException`. Đây là phép thử converter trực tiếp, **không làm hỏng context hợp lệ**.

### Vì sao không test Environment typed getProperty?

Demo đăng ký converter bean cho **BeanFactory** `@Value` path, không thêm converter vào Environment. Nếu muốn typed Environment API, hãy đăng ký thêm vào `ConfigurableEnvironment.getConversionService()` trước lookup. Không lặp lại sai lầm “cùng tên conversionService nghĩa là cả hai layers tự dùng chung”.

## 16. Chẩn đoán bằng BeanFactory APIs mà không nạp mọi bean

Một số inspection APIs:

~~~java
boolean exists = context.containsBean("pricing");
Class<?> visible = context.getType("pricing");
boolean byInterface =
        context.isTypeMatch("pricing", PricingPort.class);
boolean byConcrete =
        context.isTypeMatch("pricing", PricingService.class);
~~~

Các phương thức giúp định hướng, nhưng **không phải mọi thông tin type đều hoàn hảo trước bean instantiation**:

- `@Bean` declared return type có thể là interface, trong khi runtime object là concrete/proxy.
- `FactoryBean.getObjectType()` có thể cần metadata hoặc thậm chí eager factory initialization tùy API/flag.
- Một số resolvable type/phần graph do condition/FactoryBean/proxy definitions động cung cấp.
- `getType(name)` có overload với `allowFactoryBeanInit` để kiểm soát một số kiểu eager factory initialization.
- BeanFactory introspection chính nó có thể khiến infrastructure beans được resolve trong những cases đặc biệt; không gọi getBean hàng loạt trong production chỉ để debug.

### Cách nhìn chính xác sau khi bean đã được tạo

~~~java
Object bean = context.getBean("pricing");
System.out.println(bean.getClass().getName());
System.out.println(AopUtils.isJdkDynamicProxy(bean));
~~~

Chỉ thực hiện trong test/debug có kiểm soát. Đừng in toàn bộ object bằng `toString()` nếu method này có side effects hoặc lộ dữ liệu. Nếu mục tiêu chỉ xác định candidate names, `getBeanNamesForType` có thể phù hợp hơn instantiate mọi bean.

### getBeanNamesForType

~~~java
String[] names = context.getBeanNamesForType(
        PricingPort.class);
~~~

Nó dùng metadata/type matching. Nếu muốn nhìn implementation concrete target của AOP proxy, cần thêm `AopUtils.getTargetClass(bean)` hoặc `AopProxyUtils.ultimateTargetClass(bean)` theo use case. **Target class có thể là PricingService trong khi exposed object không phải instance của PricingService.**

## 17. Chẩn đoán @Autowired concrete với proxy sau khi BPP chạy

Nếu service được `@Transactional`, `@Async` hoặc custom `BeanPostProcessor` wrap, injection có thể fail theo nhiều mốc:

~~~text
BeanDefinition for billingService
   |
   +-- target class BillingService implements BillingPort
   |
   v
AOP BeanPostProcessor
   |
   +-- default interface-based strategy (case-specific)
   |
   v
JDK proxy implements BillingPort, SpringProxy, ...
   |
   v
Consumer constructor wants BillingService concrete
   |
   +-- not an assignable candidate / wrong actual type
   |
   v
NoSuchBeanDefinitionException, BeanNotOfRequiredTypeException
or UnsatisfiedDependencyException wrapping an underlying cause
(depending on resolution path)
~~~

**Không được khẳng định rằng mọi `@Transactional` bean đều là JDK proxy**, vì proxy strategy là cấu hình ở application/AOP subsystem. Spring Boot và core Spring setups có thể khác về default proxy-target-class; kiểm tra actual runtime.

### Giải pháp được ưu tiên

1. Inject interface hợp đồng.
2. Đặt qualifier/primary nếu có nhiều beans của interface.
3. Nếu cần concrete-only API, xem có thể đưa operation lên interface/port không.
4. Nếu bắt buộc class proxy, chọn `proxyTargetClass=true` ở scope phù hợp, kiểm tra final/module constraints.
5. Không ép kiểu trực tiếp một JDK proxy sang concrete class.
6. Không bỏ AOP để “hết lỗi type” nếu application cần transactional/security advice; đó có thể là regression nguy hiểm.

## 18. Self-invocation và phương thức final: trường hợp proxy vẫn đúng type nhưng sai behavior

Ngay cả khi BeanFactory inject được proxy, AOP advice có thể không chạy như mong đợi:

~~~java
public void outer() {
    inner();  // this.inner(), lời gọi nội bộ, có thể bypass proxy advice.
}

@Transactional
public void inner() {
    // ...
}
~~~

Với Spring proxy-based AOP, `outer()` gọi `inner()` qua `this` không đi từ caller qua proxy nên transactional advice trên inner thường không được intercept. Đây **không phải BeanNotOfRequiredTypeException**, mà là lỗi hiểu cơ chế interception.

### Với class proxy

- `final` class không thể subclass.
- `final` method không thể override để advice.
- `private` method không thể được proxy subclass intercept theo cách gọi bình thường.
- Một method gọi từ constructor/lifecycle có thể diễn ra trước khi proxy/advice sẵn sàng.
- Trong modular Java applications, reflection/subclass generation restrictions cần xem xét.

Không đặt `@Transactional` trên private/final method rồi nhận định rằng “Spring đã tạo CGLIB proxy thì chắc chắn transactional”.

## 19. BeanPostProcessor và type mismatch sau wrapping

`BeanPostProcessor.postProcessAfterInitialization(...)` có thể trả **một object khác** với bean đã instantiate. Spring AOP là ví dụ thường gặp. Nếu custom BPP trả một wrapper chỉ implements một interface, runtime type có thể khác target type mà @Bean metadata ban đầu gợi ý.

Một BPP sai:

~~~java
@Override
public Object postProcessAfterInitialization(
        Object bean, String beanName) {
    if ("specialService".equals(beanName)) {
        return someWrapper;
    }
    return bean;
}
~~~

Đây là snippet; `someWrapper` phải tuân hợp đồng type và behavior. Nếu consumer muốn concrete type mà wrapper không assignable, một lookup/injection mismatch có thể xảy ra.

### Vì sao dùng BeanPostProcessor có kiểm soát?

- BPP thuộc **một BeanFactory**; parent context BPP không tự post-process child beans.
- BPP có thể ảnh hưởng type identity, order và lifecycle.
- Một số BPP được đăng ký bằng code, một số autodetected.
- BPP được tạo/đăng ký sớm trong refresh; dependency quá nhiều trong BPP constructor có thể kéo một số beans vào creation trước khi processors sẵn sàng.
- Nếu bọc beans bằng proxy, ưu tiên Spring AOP APIs để phối hợp đúng early references và bean metadata thay vì tạo wrapper tùy tiện.

## 20. Chuyển đổi sai vì nguồn giá trị chứ không phải converter

Không phải mọi `NumberFormatException` đều do thiếu converter. Hãy kiểm tra **chuỗi effective** đầu vào:

~~~text
application.properties:
  app.maxRetries=5

test properties:
  app.maxRetries=not-a-number

Environment:
  getProperty("app.maxRetries") -> "not-a-number"

@Bean settings(@Value("${app.maxRetries}") int retries):
  BeanFactory nhận "not-a-number", chuyển sang int -> FAIL
~~~

Thêm converter đặc biệt để chấp nhận “not-a-number” có thể là sửa sai. Nguồn ưu tiên cao hơn đang override giá trị hợp lệ; phải sửa key/value/precedence.

### Test property source precedence

Trong Spring TestContext 6.2:

- `@DynamicPropertySource` có precedence cao hơn `@TestPropertySource`.
- Inline `@TestPropertySource(properties=...)` có precedence cao hơn file `@TestPropertySource(locations=...)`.
- Test property sources thường có priority hơn app property sources.
- `@PropertySource` được thêm trong quá trình configuration processing, có priority phụ thuộc vị trí và các sources đã tồn tại.

Không giả định tùy tiện rằng một `MapPropertySource.addLast(...)` được gọi trước refresh luôn có precedence thấp hơn một `@PropertySource` được thêm sau; **thứ tự thực phải được kiểm tra sau khi các sources đã được đăng ký**. Điều quan trọng là lookup effective value và source order.

### Debug an toàn

~~~java
ConfigurableEnvironment env =
        (ConfigurableEnvironment) context.getEnvironment();

for (PropertySource<?> source : env.getPropertySources()) {
    System.out.println(source.getName());
}
~~~

In tên source giúp biết precedence; tránh log credential values. Nếu muốn trace key, chỉ log non-secret key và nguồn đầu tiên chứa nó.

## 21. Phân biệt conversion với validation

Một chuỗi `"99999"` có thể parse hợp lệ thành `int` nhưng không hợp lệ làm TCP port. Ngược lại, `"not-a-number"` không thể qua cả conversion.

~~~text
Raw config
   -> property source resolution
   -> placeholder parsing
   -> type conversion
   -> domain validation
   -> bean construction
   -> initialization
~~~

Mỗi tầng có loại lỗi và remediation khác:

| Tầng | Ví dụ lỗi | Sửa ở đâu? |
| --- | --- | --- |
| Source selection | Key bị override bởi test source | PropertySource precedence |
| Placeholder | Thiếu `app.endpoint` | Cấu hình/env/strict property policy |
| Type conversion | `"seven" → int` | Config value / converter |
| Domain validation | Port 99999 | Domain constraint |
| Bean construction | Constructor ném lỗi I/O | Bean factory logic/dependency |
| Proxy wrapping | Exposed JDK proxy không concrete assignable | Injection contract/proxy strategy |
| Runtime call | Transaction self-invocation bypass | Call graph/proxy design |

**Không catch mọi BeansException rồi trả một default object**, vì nó có thể che mất các lỗi deployment, mất security advice hoặc dùng endpoint không hợp lệ.

## 22. Cách đọc cause chain và tránh kết luận vội

Một exception ở startup:

~~~text
Error creating bean with name 'checkout'
  Unsatisfied dependency expressed through method 'checkout' parameter 0
    ...
Root cause:
  NoSuchBeanDefinitionException / BeanCurrentlyInCreationException
  / ConversionFailedException / TypeMismatchException ...
~~~

Cấu trúc exception tùy code path và version. Thay vì lọc “Error creating bean” chung, hãy:

1. Ghi bean name đang fail.
2. Xác định dependency parameter/property path.
3. Ghi required type và candidate names.
4. Kiểm tra exposed runtime type/proxy nếu bean đã có.
5. Nếu liên quan `@Value`, ghi sanitized raw/effective property và target type.
6. Duyệt `Throwable.getCause()`; với `BeanCreationException` có thể còn related causes.
7. Phân loại nguyên nhân trước khi sửa.
8. Kiểm thử lại bằng context tối thiểu sau khi sửa, không chỉ xóa annotation để app start được.

### Hàm kiểm tra nguyên nhân theo type

~~~java
static boolean hasCause(Throwable failure,
                        Class<? extends Throwable> kind) {
    for (Throwable cause = failure;
         cause != null; cause = cause.getCause()) {
        if (kind.isInstance(cause)) {
            return true;
        }
    }
    return false;
}
~~~

Đây là utility snippet không truy cập related causes. Khi cần diagnostic tổng quát, traverse cả `BeanCreationException.getRelatedCauses()` và tránh lặp vô hạn nếu một custom Throwable graph có cycles.

### Đừng so error message nguyên văn

Tên bean, parameter index, proxy implementation class name, module name, locale và Spring patch version có thể thay đổi exception message. Test nên assert **exception type**, **beanName/requiredType**, hoặc outcome business, thay vì so hàng trăm ký tự message.

## 23. Ma trận lỗi cần kiểm thử trong CI

| Test case | Setup | Expected assertion |
| --- | --- | --- |
| JDK proxy + interface lookup | Bean expose PricingPort | `getBean("pricing", PricingPort.class)` hoạt động |
| JDK proxy + concrete lookup | Exposed object không phải PricingService | `BeanNotOfRequiredTypeException` |
| Missing name | Không register "missingBean" | `NoSuchBeanDefinitionException` |
| Multiple ports | Hai PricingPort không qualifier | `NoUniqueBeanDefinitionException`/wrapped |
| @Primary | Một port primary | Single injection lấy primary |
| @Qualifier | Chọn bean name cụ thể | Đúng implementation |
| Valid int property | `app.maxRetries=5` | `int 5` |
| Invalid int property | `not-a-number` | Bean creation fail |
| Valid boolean | `app.enabled=true` | `boolean true` |
| Custom Endpoint converter | `example.test:8443` | Endpoint record đúng |
| No converter | Có Endpoint injection nhưng chưa đăng ký conversion | Conversion/type-mismatch failure |
| Malformed endpoint | `invalid-endpoint` | `ConversionFailedException` khi convert trực tiếp |
| Port out of range | `example.test:70000` | Domain validator reject |
| Missing required property | Không có app.endpoint | Error/fail strict theo config |
| Source override | Test property cao hơn app value | Effective source đúng |
| FactoryBean product vs factory | Name vs `&name` | Trả đúng object type |
| Class proxy | `proxyTargetClass=true` | Instance assignable nếu proxy hợp lệ |
| Final class | Yêu cầu subclass proxy final | Proxying exception/limitation |
| Self-invocation | Internal call bypasses proxy | Advice không chạy như qua proxy |
| Context parent/child | BPP ở parent, beans ở child | Không giả định BPP tự áp dụng child |
| ApplicationContext close | Managed singleton resources | Destroy callbacks được xử lý |

Không cần viết toàn bộ cases trong một file main dài; dự án thật nên dùng JUnit tests riêng, mỗi test chỉ kiểm chứng một cơ chế, và bắt failure path **có chủ đích**.

## 24. Runbook sửa lỗi theo thứ tự hiệu quả

### Trường hợp A: “Bean tên đúng nhưng required type sai”

1. Xác nhận bean name tồn tại (`containsBean`).
2. Đọc exception `getRequiredType/getActualType`.
3. Nếu actual là `$Proxy...`, xác minh interface set của JDK proxy.
4. Đổi injection point sang interface nếu phù hợp.
5. Nếu cần concrete target thực, xem class-based proxy hoặc refactor abstraction.
6. Không thêm cast vô lý hoặc tắt transaction/security advice để tránh exception.

### Trường hợp B: “@Autowired concrete service báo không có bean”

1. Xem bean definition declared type và có AOP proxy không.
2. So `getBeanNamesForType(Concrete.class)` với `getBeanNamesForType(Interface.class)`.
3. Kiểm tra bean đã bị proxy và exposed type là interface.
4. Kiểm tra `@Qualifier`/`@Primary` nếu interface có nhiều candidates.
5. Inject interface, hoặc đánh giá class-proxy strategy theo constraints.

### Trường hợp C: “@Value int fail”

1. Ghi property key/required type.
2. Xem effective raw string, không chỉ một file.
3. Kiểm tra `@TestPropertySource`, `@DynamicPropertySource` và app/default sources.
4. Sửa malformed value hoặc parser policy có hợp đồng rõ.
5. Không thêm converter biến mọi string thành 0 rồi cho test pass.

### Trường hợp D: “@Value custom record fail”

1. Xác nhận property tồn tại và grammar đúng.
2. Xem BeanFactory conversionService bean có đúng tên `conversionService` không.
3. Xem `Converter<String, DomainType>` có được đăng ký vào đó không.
4. Kiểm tra converter có giữ default String→int/boolean conversions không.
5. Test happy/malformed/boundary cases.
6. Nếu typed Environment lookup dùng riêng, đăng ký converter cho Environment resolver nữa.

### Trường hợp E: “FactoryBean lookup ra kiểu lạ”

1. Kiểm tra bean implements `FactoryBean<T>` không.
2. Phân biệt `getBean("name")` (product) với `getBean("&name")` (factory).
3. Kiểm tra `getObjectType()` phản ánh product type đủ chính xác.
4. Không thêm prefix `&` lên một bean không phải FactoryBean.

## 25. Khi nào không nên thay proxy strategy toàn cục?

Một số ứng dụng có auto-proxy creators liên quan `@Transactional`, `@Async`, `@AspectJ` và security. Đổi một flag chung `proxyTargetClass=true` có thể làm tất cả proxy chuyển sang class-based, ảnh hưởng:

- Generated class behavior và runtime type.
- Final classes/methods.
- Proxy dispatch qua methods ngoài interface.
- Java module/classloader constraints.
- AOT/native-image proxy metadata.
- Timing của bean post-processors/early singleton references.
- Identity assumptions trong tests hoặc serializable interfaces.

Nếu chỉ một consumer đang inject implementation class thay vì interface, thường **sửa consumer injection** nhỏ và ít rủi ro hơn đổi proxy mode cho toàn context. Hãy test advice thực sự còn chạy sau fix, đặc biệt transactions/security.

### Không bóc target khỏi proxy trong business code

Code như:

~~~java
PricingService target =
        (PricingService) ((Advised) proxy)
                .getTargetSource().getTarget();
~~~

có thể lấy target ở test/debug đặc biệt nhưng **không phải giải pháp dependency injection cho production**: có thể bypass advice, đụng scope/lifecycle và rò rỉ implementation. Với scoped proxy, target source có thể thay đổi theo invocation. Business code nên dùng giao diện hợp đồng và gọi qua proxy.

## 26. Kiến trúc tốt để ngăn lỗi tái phát

Thiết kế ports/adapters:

~~~text
CheckoutService
    |
    | depends on stable interface
    v
PricingPort
    ▲
    |
PricingService (implementation)
    |
    +-- Spring may wrap with JDK proxy
    |
    +-- future implementations: RemotePricing, CachedPricing, ...
~~~

Những lợi ích:

- Consumer ít phụ thuộc AOP proxy type.
- Test bằng fake PricingPort dễ hơn.
- Có thể thay implementation qua `@Profile`, `@Conditional`, `@Qualifier`.
- Class internal methods/final status ít tác động trực tiếp tới injection contract.
- Phân tách cấu hình khỏi business object.

Với typed config, dùng một object cấu hình bất biến:

~~~java
public record PricingSettings(
        int maxRetries, boolean enabled, Endpoint endpoint) {
}
~~~

Một nơi chịu trách nhiệm đọc properties, conversion, validation; services chỉ nhận `PricingSettings` đã hợp lệ. Không nên mỗi service tự đọc raw property rồi tự parse, vì conversion bugs sẽ lặp lại và fail-late.

## 27. Câu hỏi tự kiểm tra

**Câu 1.** Tại sao `PricingService implements PricingPort` nhưng `getBean("pricing", PricingService.class)` lại có thể fail?

**Trả lời:** Vì exposed bean có thể là JDK proxy implements PricingPort, không extends PricingService.

**Câu 2.** BeanNotOfRequiredTypeException khác NoSuchBeanDefinitionException thế nào?

**Trả lời:** BeanNotOfRequiredType thường khi bean theo tên có nhưng type không assignable; NoSuchBeanDefinition khi không có candidate matching theo lookup path.

**Câu 3.** Có nên mặc định buộc CGLIB để mọi concrete injection hoạt động không?

**Trả lời:** Không. Ưu tiên interface, và class proxy có final/module constraints.

**Câu 4.** CGLIB có thể advice final method không?

**Trả lời:** Không theo subclass proxying, vì không override được final method.

**Câu 5.** @Bean method return interface có nghĩa exposed object luôn là JDK proxy không?

**Trả lời:** Không. Declared type là metadata; có thể trả implementation trực tiếp. Proxy chỉ có nếu code/infrastructure tạo proxy.

**Câu 6.** FactoryBean getBean("x") trả gì?

**Trả lời:** Mặc định trả product của FactoryBean, còn getBean("&x") để lấy factory.

**Câu 7.** `@Value("${app.maxRetries}") int` với value `"oops"` bị lỗi do proxy không?

**Trả lời:** Không, đó là type conversion của property.

**Câu 8.** String→int thường cần custom converter không?

**Trả lời:** Không, Spring có built-in conversion hỗ trợ trong setup thông thường.

**Câu 9.** String→Endpoint record có cần converter nếu Spring chưa biết grammar host:port không?

**Trả lời:** Có, hoặc một binding/constructor/factory policy được thiết kế khác rõ ràng.

**Câu 10.** Vì sao `DefaultConversionService` thường tốt hơn một GenericConversionService rỗng?

**Trả lời:** Nó giữ các converters tiêu chuẩn và cho phép thêm domain converter.

**Câu 11.** Đăng ký bean conversionService có làm Environment typed lookup dùng nó tự động không?

**Trả lời:** Không thể mặc nhiên giả định. BeanFactory và Environment có conversion services riêng.

**Câu 12.** `@Primary` có chữa JDK proxy không assignable concrete class không?

**Trả lời:** Không. Nó chọn candidate, không đổi Java runtime type.

**Câu 13.** Vì sao test nên xem nested causes chứ không so error message?

**Trả lời:** Lỗi bean creation có thể bị wrap, còn message/proxy generated name thay đổi giữa versions.

**Câu 14.** Nếu source config chứa valid integer nhưng @Value nhận invalid string, cần kiểm tra gì?

**Trả lời:** Property source precedence, effective value và test/dynamic overrides.

**Câu 15.** Tại sao chương trình mẫu tách GoodConfig và BadConfig?

**Trả lời:** Để lỗi property sai ở BadConfig không ngăn kiểm chứng proxy và conversion đúng trong GoodConfig.

**Câu 16.** Một Spring AOP proxy có thể là JDK Proxy và CGLIB cùng lúc không?

**Trả lời:** Một object proxy cụ thể dùng một strategy, dù có thể bị bọc nhiều lớp proxies. Kiểm tra exposed runtime object và từng layer.

## 28. Checklist kiểm tra pull request

1. Bean name, declared type, exposed type, target class và required injection type đã được phân biệt?
2. Có kiểm tra exact bean name trong exception?
3. `@Bean` return type có trung thực với API consumer không?
4. Có tránh inject concrete class khi service contract có interface phù hợp?
5. Có `@Primary`/`@Qualifier` đúng khi nhiều candidates?
6. Có nhận diện BeanNotOfRequiredType vs NoSuchBeanDefinition vs NoUniqueBeanDefinition?
7. Nếu AOP, có kiểm tra JDK/CGLIB proxy strategy hiện tại?
8. Có avoid final class/method advice assumptions?
9. Có kiểm tra AOP self-invocation behavior?
10. Có tránh unwrapping proxy trong business code?
11. Nếu custom BPP bọc bean, có bảo toàn exposed type contract?
12. Nếu FactoryBean, đã phân biệt factory/product và `&` prefix?
13. Property keys có tồn tại và effective values đúng?
14. Có đọc precedence sau sources registration chứ không chỉ đoán?
15. Placeholder strict policy có phù hợp config bắt buộc?
16. Có kiểm tra built-in int/boolean/enum conversion?
17. Domain converter có grammar và error policy rõ?
18. BeanFactory conversionService có đăng ký đúng tên/timing?
19. Environment typed conversion có được cấu hình riêng nếu dùng?
20. Có giữ built-in converters khi bổ sung custom converter?
21. Có test malformed strings, empty fields và boundary values?
22. Có avoid logging sensitive properties?
23. Có integration tests cho both success/failure contexts?
24. Có assert exception types/fields thay vì message nguyên văn?
25. Có kiểm tra Spring Framework vs Spring Boot default proxy/config behavior?
26. Có bảo đảm không vô tình bỏ transactional/security advice?
27. Có test AOP method invocation qua proxy thật?
28. Có đóng context/resources sau test?
29. Có đọc root cause trong full exception chain?
30. Có thể diễn giải lỗi và giải pháp mà không “thử annotation cho đến khi hết lỗi” không?

## 29. Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — AOP Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Spring Framework 6.2 — Using @Value](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/value-annotations.html)
- [Spring Framework 6.2 — Spring Type Conversion](https://docs.spring.io/spring-framework/reference/6.2/core/validation/convert.html)
- [Spring Framework 6.2 — The BeanFactory API](https://docs.spring.io/spring-framework/reference/6.2/core/beans/beanfactory.html)
- [Spring Framework 6.2 — FactoryBean](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-extension/factorybean.html)
- [Spring Framework 6.2 Javadoc — BeanNotOfRequiredTypeException](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/BeanNotOfRequiredTypeException.html)
- [Spring Framework 6.2 Javadoc — NoSuchBeanDefinitionException](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/NoSuchBeanDefinitionException.html)
- [Spring Framework 6.2 Javadoc — NoUniqueBeanDefinitionException](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/NoUniqueBeanDefinitionException.html)
- [Spring Framework 6.2 Javadoc — BeanCreationException](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/BeanCreationException.html)
- [Spring Framework 6.2 Javadoc — ConversionFailedException](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/convert/ConversionFailedException.html)
- [Spring Framework 6.2 Javadoc — ProxyFactory](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/framework/ProxyFactory.html)
- [Spring Framework 6.2 Javadoc — AopUtils](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/support/AopUtils.html)
- [Spring Framework 6.2 Javadoc — DefaultConversionService](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/convert/support/DefaultConversionService.html)
- [Spring Framework 6.2 Javadoc — PropertySourcesPlaceholderConfigurer](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/PropertySourcesPlaceholderConfigurer.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="troubleshooting-008">Callback khởi tạo thất bại hoặc bean chưa được post-process đầy đủ</a>

<details>
<summary>Xem chi tiết</summary>

# Callback khởi tạo thất bại hoặc bean chưa được post-process đầy đủ trong Spring Framework

## 1. Hai triệu chứng nhìn giống nhau nhưng khác bản chất

Khi Spring Core Container tạo một bean, công việc không kết thúc ở lệnh `new`. Sau khi instantiate object và đưa dependencies vào, Spring còn chạy các lifecycle callbacks, `BeanPostProcessor` (BPP), có thể kiểm tra cấu hình, tạo proxy và cuối cùng công bố object cho các consumer.

Có hai tình huống chẩn đoán quan trọng:

**A. Callback khởi tạo thất bại.** `@PostConstruct`, `InitializingBean.afterPropertiesSet()`, phương thức `initMethod` hoặc chính một BPP ném exception. Container có thể báo `BeanCreationException` hoặc exception được bọc liên quan; bean **không hoàn tất khởi tạo**.

**B. Bean đã được tạo nhưng chưa đi qua đầy đủ BPP.** Bean được instantiate **quá sớm**, trong giai đoạn ApplicationContext còn đang tìm và đăng ký BPP. Bean ấy có thể hoạt động như Java object bình thường, nhưng **không được tất cả các BPP xử lý**, ví dụ không được auto-proxy bởi một processor chỉ đăng ký sau đó. Trường hợp này thường là **cảnh báo**, không nhất thiết là exception.

~~~text
                          BeanDefinition
                                |
                       instantiate bean
                                |
                       populate properties
                                |
                 BeanPostProcessor(s) BEFORE
                  /                     \
        @PostConstruct                 failure?
                  |                       |
           InitializingBean               v
                  |                BeanCreationException
          custom init method             (bean chưa ready)
                  |
          BeanPostProcessor(s) AFTER
                  |
                  v
         exposed singleton / proxy

TRƯỜNG HỢP KHÁC: bean được tạo trong giai đoạn đăng ký BPP
                  |
                  v
   Một số BPP chưa tồn tại -> bean không được xử lý bởi chúng
                  |
                  v
      warning "not eligible ... all BeanPostProcessors"
~~~

Sơ đồ là mô hình khái quát. `@PostConstruct` thực tế được gọi bởi một `BeanPostProcessor` ở nhánh before-initialization; **không có ranh giới độc lập cố định giữa tất cả before-processors và @PostConstruct**. Phải xét **thứ tự các BPP thực tế**, không giả định custom BPP.before luôn chạy trước `@PostConstruct`.

Bài thuộc nhóm **Chẩn đoán lỗi** của module **Spring Framework - core-container**, với chủ đề **Callback khởi tạo thất bại hoặc bean chưa được post-process đầy đủ**.

### Bạn sẽ học được

1. Phân biệt instance creation, property population, initialization, BPP và exposed proxy.
2. Biết các lifecycle callbacks `@PostConstruct`, `InitializingBean` và `@Bean(initMethod=...)` được gọi lúc nào và lỗi được bọc ra sao.
3. Hiểu `postProcessBeforeInitialization` và `postProcessAfterInitialization` có thể sửa/wrap bean hoặc ném exception.
4. Phân biệt “bean không tạo xong” với “bean đã được tạo nhưng không eligible cho **đầy đủ** BPP”.
5. Giải thích tại sao một BPP phụ thuộc vào business bean làm bean ấy instantiate quá sớm và có thể mất auto-proxying.
6. Dùng `static @Bean` và return type `BeanPostProcessor`/concrete type đúng để đăng ký processor từ đầu.
7. Đọc `BeanCreationException` cause chain và warning “not eligible for getting processed by all BeanPostProcessors”.
8. Biết cách tái cấu trúc BPP để tránh phụ thuộc trực tiếp, xử lý premature initialization, và kiểm tra proxy/interceptor behavior.
9. Chạy ví dụ **Java 17 + Maven + Spring Framework 6.2.9**, có success path, bốn failure paths và early/late BPP experiment.
10. Viết các tests phát hiện regression mà không dựa vào nguyên văn logging messages.

## 2. Từ BeanDefinition đến bean được công bố

Một `BeanDefinition` là metadata: class/factory method, scope, constructor arguments, lazy flag, init/destroy callbacks và những chỉ dẫn khác. Nó không phải object instance.

Khi tạo bean, `AbstractAutowireCapableBeanFactory` thường xử lý theo trình tự khái niệm:

| Pha | Hoạt động | Ví dụ lỗi |
| --- | --- | --- |
| Instantiate | Constructor / factory method tạo Java object | Constructor throw, factory method fail |
| Populate | Inject setter/field, xử lý dependencies | `UnsatisfiedDependencyException` |
| Before initialization | Các BPP có thể tác động; annotation processor gọi `@PostConstruct` | @PostConstruct throw, BPP.before throw |
| Initialization callbacks | `InitializingBean.afterPropertiesSet`, custom init method | Validation/config exception |
| After initialization | BPP có thể wrap object thành proxy | Proxy creation failure, BPP.after throw |
| Publish | Singleton exposed object được dùng trong lookup/injection | BPP chưa áp đủ do premature creation |

### “Đã có object” không có nghĩa “đã có bean dùng được”

Sau constructor, Java object có thể tồn tại nhưng chưa nhận hết dependencies, init callbacks hoặc proxy. Nếu `@PostConstruct` ném lỗi, việc constructor đã chạy không làm bean trở thành singleton hoàn chỉnh.

Nếu một bean được tạo sớm trong lúc đăng ký post-processors, ngược lại nó có thể được BeanFactory công bố thành một singleton bình thường nhưng thiếu behavior mà ứng dụng kỳ vọng từ processor đăng ký muộn hơn. Vì vậy **khởi động thành công không đủ để chứng minh @Transactional/@Async/@Cacheable advice hoặc custom BPP đã áp vào đúng bean**.

### BeanFactory và ApplicationContext

`ApplicationContext` tự phát hiện những beans có type `BeanPostProcessor` và đăng ký chúng khi khởi động. Một plain `ConfigurableBeanFactory` cho phép đăng ký BPP programmatically qua `addBeanPostProcessor`. Trong cả hai mô hình, BPP chỉ tự động ảnh hưởng các beans được tạo **sau khi processor có mặt trong chain**, không back-process tất cả instances đã được tạo từ trước.

## 3. @PostConstruct: callback do processor thực hiện

Trong Spring Framework 6.x, dùng:

~~~java
import jakarta.annotation.PostConstruct;

public class SettingsCache {

    private boolean ready;

    @PostConstruct
    public void initialize() {
        this.ready = true;
    }

    public boolean isReady() {
        return ready;
    }
}
~~~

Đây là snippet của class Java, chưa tự đăng ký bean. `CommonAnnotationBeanPostProcessor`/lifecycle annotation processing infrastructure nhận diện `jakarta.annotation.PostConstruct` khi được đăng ký vào ApplicationContext.

### Jakarta, không phải javax.annotation của Java 8

Trên Java 17 và Spring 6, chú ý `jakarta.annotation.PostConstruct` thay vì các imports cũ `javax.annotation.PostConstruct`. Để có annotation API khi tự xây demo Spring Core, khai báo dependency rõ:

~~~xml
<dependency>
    <groupId>jakarta.annotation</groupId>
    <artifactId>jakarta.annotation-api</artifactId>
    <version>2.1.1</version>
</dependency>
~~~

Snippet này thuộc Maven `dependencies`, không phải nội dung đứng độc lập của `pom.xml`.

### Callback chạy lúc nào?

`@PostConstruct` được gọi sau dependency population nhưng trước khi bean được coi là initialized xong. Nó nằm trong `postProcessBeforeInitialization` processing. Một BPP khác với ordered priority khác có thể đã chạy trước hoặc sẽ chạy sau annotation processor. Vì vậy không nên dùng logs của một custom BPP để tuyên bố “mọi BPP.before đều chạy trước @PostConstruct”.

### Khi @PostConstruct ném exception

~~~java
@PostConstruct
public void initialize() {
    throw new IllegalStateException("cache-index-invalid");
}
~~~

Bean creation thất bại; Spring thường wrap lỗi trong `BeanCreationException`. `postProcessAfterInitialization` của các BPP sẽ **không phải** một cleanup hook được bảo đảm sau failure. Không cố lập trình dựa trên giả định after-phase luôn chạy dù before/init đã throw.

### Tránh khởi chạy quá nhiều trong @PostConstruct

Lời gọi mạng blocking, vòng gọi bean khác, scheduling background work hoặc lock acquisition dài trong `@PostConstruct` có thể tạo deadlock/hang vì initialization diễn ra khi container quản lý singleton creation. Theo hướng dẫn của Spring, callback nên tập trung **validate cấu hình và chuẩn bị state nội bộ**. Công việc sau khi toàn bộ singleton initialization xong có thể cân nhắc:

- `SmartInitializingSingleton.afterSingletonsInstantiated()`;
- `ContextRefreshedEvent` (phải hiểu việc refresh có thể xảy ra nhiều lần ở context hỗ trợ);
- `SmartLifecycle` nếu cần start/stop lifecycle, phases hoặc auto-startup.

Những alternatives này phục vụ giai đoạn khác; không chữa lỗi cấu hình invalid, và phải đảm bảo thread-safety/ordering phù hợp.

## 4. InitializingBean.afterPropertiesSet()

~~~java
import org.springframework.beans.factory.InitializingBean;

public final class ClientHolder implements InitializingBean {

    private String url;

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public void afterPropertiesSet() {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("client-url-required");
        }
    }
}
~~~

`InitializingBean` là Spring-specific contract với `void afterPropertiesSet() throws Exception`. Container gọi sau property population, thông thường sau các `@PostConstruct` callbacks. Nếu method throw, Spring abort bean initialization và có thể wrap cause.

### Dùng lúc nào?

Khi xây infrastructure component chặt với Spring lifecycle hoặc cần validate property-injection invariants. Nếu không muốn domain classes phụ thuộc Spring interfaces, cân nhắc `@PostConstruct` hoặc custom init methods. Cả ba đều có lỗi nếu constructor/property state không hợp lệ; không có cách nào tự biến config sai thành hợp lệ.

### Thứ tự khi cùng bean có nhiều callbacks

Thứ tự theo Spring lifecycle cho các loại callback thường là:

1. `@PostConstruct`;
2. `InitializingBean.afterPropertiesSet()`;
3. Custom `init-method`.

Đây là thứ tự của **các loại init callbacks**, không áp đặt thứ tự tuyệt đối của mọi unrelated BeanPostProcessors. Nếu callback trước throw, callbacks phía sau **không được chạy như thể initialization thành công**.

## 5. Custom init method trên @Bean

Bạn có thể giữ class thuần Java và khai báo callback trong definition:

~~~java
public final class CacheEngine {

    public void warmup() {
        // Validate / chuẩn bị state.
    }
}
~~~

~~~java
@Configuration(proxyBeanMethods = false)
class CacheConfiguration {

    @Bean(initMethod = "warmup")
    public CacheEngine cacheEngine() {
        return new CacheEngine();
    }
}
~~~

Đây là hai snippets, cần imports `Configuration`/`Bean`. Spring gọi `warmup()` trong initialization phase. Nếu method throw, bean creation fail.

### Method requirements

Một custom init method có thể được chỉ ra bằng `@Bean(initMethod="...")` hoặc XML/BeanDefinition metadata. Chọn method không cần arguments cho use case thông thường và dùng access signature hợp lệ. Nếu tên method sai, reflection/invocation metadata không hợp lệ cũng có thể dẫn đến BeanCreationException, không chỉ method body throw.

### Điều gì xảy ra khi kết hợp ba loại init?

~~~text
@jakarta.annotation.PostConstruct
             |
             v
InitializingBean.afterPropertiesSet()
             |
             v
@Bean(initMethod = "warmup")
~~~

Nếu bean có cả ba, mỗi callback dùng để chuẩn bị phần riêng hoặc validate theo một contract được document rõ. Tránh lặp cùng initialization action ba lần. Mỗi callback có thể fail; một failure sớm ngăn các callbacks tiếp theo chạy bình thường.

## 6. BeanPostProcessor.before và after

`org.springframework.beans.factory.config.BeanPostProcessor` có hai hooks chính:

~~~java
Object postProcessBeforeInitialization(
        Object bean, String beanName);

Object postProcessAfterInitialization(
        Object bean, String beanName);
~~~

Một BPP thường có thể kiểm tra bean marker interface, bổ sung state hoặc thay return object bằng wrapper/proxy. Ví dụ:

~~~java
public final class AuditMarkerProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(
            Object bean, String name) {

        if (bean instanceof Auditable auditable) {
            auditable.markReady();
        }
        return bean;
    }
}
~~~

Đây là snippet cần type/interface `Auditable`, import `BeanPostProcessor` và implementation tương ứng.

### Exceptions trong BPP

Nếu `postProcessBeforeInitialization` hoặc `postProcessAfterInitialization` ném exception, bean creation có thể bị hủy. Outer wrapper thường là `BeanCreationException` nếu đang tạo qua Spring BeanFactory, nhưng exact wrapper/cause layering phụ thuộc implementation.

### Return null là một failure?

Theo BeanPostProcessor contract, một BPP có thể trả `null`; BeanFactory xử lý `null` như **tín hiệu dừng việc gọi các BPP tiếp theo của pha ấy** và giữ result phù hợp theo implementation, không phải tự động ném exception. Vì vậy, “BPP không chạy” không nhất thiết có stack trace; đôi khi một BPP trả null làm những processors phía sau bị bỏ qua.

**Quy tắc thực hành:** một BPP không chủ đích thay đổi bean nên trả lại `bean`, không dùng `null` làm shortcut cho “không xử lý”.

### Khác BeanFactoryPostProcessor

- `BeanFactoryPostProcessor` thay/chỉnh **BeanDefinition metadata** trước regular bean instantiation.
- `BeanPostProcessor` xử lý **instances** trước/sau initialization.
- `BeanDefinitionRegistryPostProcessor` có thể thêm/chỉnh definitions sớm hơn trong configuration lifecycle.

Nếu lỗi liên quan placeholder/property values, có thể cần nhìn BeanFactoryPostProcessor. Nếu bean bị thiếu proxy hoặc annotation-based post-processing, hãy nhìn BPP registration timing.

## 7. Tại sao xuất hiện warning "not eligible for all BeanPostProcessors"?

Spring Reference nêu dạng cảnh báo:

~~~text
Bean 'someBean' of type [...] is not eligible for getting
processed by all BeanPostProcessors (for example: not eligible
for auto-proxying)
~~~

Đây là đoạn trích thông điệp **minh họa**; logger names và wording thay đổi theo phiên bản.

### Cơ chế chính

Để dùng custom BPP, ApplicationContext phải **instantiate chính BPP** trong giai đoạn đăng ký special infrastructure, trước khi regular singleton beans được pre-instantiate.

~~~text
Context.refresh()
     |
     v
Detect BeanPostProcessor definitions
     |
     v
Instantiate / register BeanPostProcessors
     |
     +-- Một BPP constructor cần BusinessService
               |
               v
          Create BusinessService TOO EARLY
               |
               v
    AOP auto-proxy creator hoặc BPP khác chưa đăng ký
               |
               v
        BusinessService thiếu full post-processing
     |
     v
Register all BPPs
     |
     v
Instantiate regular beans, áp chain hoàn chỉnh
~~~

Bean tạo sớm có thể trở thành đối tượng bình thường và không bị chạy lại toàn bộ BPP chain. Đó là lý do cảnh báo nói **not eligible**, không phải “bean chắc chắn chưa được tạo” hoặc “mọi BPP đều bị bỏ qua”.

### AOP auto-proxy là một BPP

Spring AOP proxy creation thường dùng BeanPostProcessor/auto-proxy creator. Nếu một business service được instantiate trước auto-proxy creator, service có thể **không được expose qua proxy đúng kỳ vọng**. Hậu quả là:

- `@Transactional` có thể không intercept;
- `@Async` có thể chạy synchronous;
- `@Cacheable` hoặc security advice có thể bị bỏ qua;
- custom after-initialization wrapping không áp vào bean sớm.

Từng annotation cần infrastructure tương ứng; **cảnh báo không tự chứng minh một annotation cụ thể đã bị bỏ qua**. Cần kiểm tra actual bean/proxy và behavior bằng integration test.

### Không phải mọi warning có nghĩa root cause chính

Một warning eligibility không nhất thiết là nguyên nhân của một `BeanCreationException` xảy ra sau đó. Cần xác định thứ tự, bean name, BPP count và actual lifecycle. Đừng vội tắt logging để che cảnh báo.

## 8. Nguyên nhân bean được instantiate quá sớm

### 8.1. BPP constructor inject business bean

~~~java
public final class MonitoringProcessor implements BeanPostProcessor {

    private final BillingService billing;

    public MonitoringProcessor(BillingService billing) {
        this.billing = billing;
    }
}
~~~

Đây là snippet có domain `BillingService`. Để tạo BPP, BeanFactory phải tạo BillingService **ngay trong giai đoạn đăng ký processors**. Nếu BillingService cần transaction proxy, nó có thể mất auto-proxying.

### 8.2. @Bean method BPP không static

~~~java
@Configuration
public class MonitoringConfiguration {

    @Bean
    public MonitoringProcessor monitoringProcessor() {
        return new MonitoringProcessor(...);
    }
}
~~~

Đây là snippet minh họa với dependencies/call parameters do ứng dụng định nghĩa. Vì method không static, Spring có thể phải tạo enclosing `@Configuration` instance sớm; những dependencies của configuration instance có nguy cơ bị kéo theo.

### 8.3. @Bean return type quá rộng

~~~java
@Bean
public Object customProcessor() {
    return new MonitoringProcessor(...);
}
~~~

Spring khó tự phát hiện BPP từ factory method signature nếu declared return là `Object` trước khi gọi factory. Reference khuyến nghị khai báo **implementation type** hoặc ít nhất `BeanPostProcessor` để framework nhận biết từ metadata:

~~~java
@Bean
public MonitoringProcessor customProcessor() {
    return new MonitoringProcessor(...);
}
~~~

Cách tốt hơn, khi không cần instance dependencies:

~~~java
@Bean
public static MonitoringProcessor customProcessor() {
    return new MonitoringProcessor();
}
~~~

Đây là snippets, `MonitoringProcessor` cần constructor tương ứng. `static` tránh instantiate configuration class chỉ để gọi factory method.

### 8.4. Eager lookup trong BeanFactoryPostProcessor hoặc BPP

Một infrastructure callback gọi `beanFactory.getBean("service")` có thể tạo business bean trước khi BPP chain hoàn tất. Nếu chỉ cần BeanDefinition metadata, hãy dùng registry/type metadata APIs thay vì instantiate service.

### 8.5. @Resource/@Autowired trên BPP kích hoạt type matching

Spring Reference cảnh báo BPP tự autowire collaborators có thể kéo thêm beans vào early phase. Với `@Resource` nếu name không match field/setter như mong đợi, type fallback có thể dẫn đến thêm lookups. Giảm dependencies trên BPP; tránh inject những service cần AOP.

### 8.6. Early registration bằng tay

Nếu dùng plain BeanFactory:

~~~java
factory.getBean("reportService"); // tạo sớm

factory.addBeanPostProcessor(new MarkerProcessor());
~~~

Khi `reportService` đã được instantiate trước registration, processor mới **không tự retroactively chạy lại** trên nó. Ví dụ chạy đầy đủ ở mục 12 chứng minh điều này mà không phụ thuộc framework warning log.

## 9. BeanPostProcessor ordering: phần nào được bảo đảm?

Với BPPs **tự được ApplicationContext phát hiện**, Spring sắp xếp theo `PriorityOrdered`, `Ordered` và các rules tương ứng. Nhưng đừng giả định chỉ dùng `@Order` trên @Bean method là đủ điều khiển mọi trường hợp; chính BPP instances/order contracts mới quan trọng.

### Programmatic registration

~~~java
beanFactory.addBeanPostProcessor(new FirstProcessor());
beanFactory.addBeanPostProcessor(new SecondProcessor());
~~~

Đây là snippet; `FirstProcessor`/`SecondProcessor` implements BeanPostProcessor. Spring Reference lưu ý những processors được đăng ký **programmatically** chạy theo **registration order** và được xử lý trước các BPP auto-detected; `Ordered` của processors đăng ký bằng tay không tự override trình tự ấy.

### Lỗi do thứ tự processors

Một custom BPP.before giả định `@PostConstruct` đã chạy hoặc một BPP.after giả định proxy đã tồn tại có thể thất bại khi chain order thay đổi. Test dựa vào **behavior cuối và contract của processor**; nếu order thật sự thiết yếu, khai báo ordering tường minh và test ở context thật.

### Không ép @PostConstruct đứng sau mọi BPP.before

`CommonAnnotationBeanPostProcessor` là một processor thuộc before-initialization chain. Nếu một custom BPP.before chạy sau nó, custom BPP.before có thể thấy `@PostConstruct` đã thiết lập state. Nếu chạy trước, điều ngược lại. Điều chắc chắn với bean thành công là init callback đã hoàn tất trước **after-initialization phase** cho instance đó.

## 10. Đọc BeanCreationException khi callback thất bại

Ví dụ:

~~~text
BeanCreationException: Error creating bean with name 'cache'
  Initialization of bean failed
Caused by: IllegalStateException: cache-index-invalid
~~~

Đây là trace **minh họa**.

Các bước:

1. Ghi nhận `BeanCreationException.getBeanName()`: instance nào đang khởi tạo?
2. Xem `getResourceDescription()` nếu có: BeanDefinition từ đâu?
3. Duyệt `Throwable.getCause()`: callback nào đã throw? Class/method nào?
4. Dùng `getRootCause()` hoặc `getMostSpecificCause()` và **không mất outer chain**.
5. Kiểm tra `@PostConstruct`, `afterPropertiesSet`, custom init method, BPP hooks.
6. Xác nhận dependencies được inject đầy đủ trước callback theo lifecycle thích hợp.
7. Nếu warning “not eligible”, kiểm tra BPP registration phase **riêng**, đừng giả định nó là cause của exception.
8. Dựng isolated context cho callback hỏng và viết positive test sau sửa.

### Inner cause có thể không phải BeansException

~~~java
BeanCreationException failure = ...;
Throwable root = failure.getMostSpecificCause();
System.out.println(root.getClass().getSimpleName());
~~~

Một root `IllegalStateException`, `NullPointerException`, `IOException` hoặc custom exception có thể bắt nguồn từ code ứng dụng trong init callback. Spring chỉ đang báo nó không thể hoàn thành bean creation.

### @PostConstruct và InitializingBean liên quan nhau thế nào?

Nếu `@PostConstruct` throw, `InitializingBean` và custom init **không** hoàn tất như trên success path. Nếu `afterPropertiesSet` throw, custom init không chạy tiếp thành công. Cũng vậy, nếu custom init throw, after-initialization post-processors/proxy exposure có thể không hoàn tất.

### Fail fast không phải lúc nào ở startup

Eager singleton thường fail trong `context.refresh()`. Nhưng `@Lazy` singleton chỉ có thể fail ở `getBean(...)` đầu tiên; prototype/scoped beans có thể tạo sau. Việc startup thành công không chứng minh tất cả init callbacks của mọi bean đã chạy.

## 11. Diagnostic checklist cho warning thiếu BPP

| Dấu hiệu | Nguyên nhân có thể | Bước kiểm chứng |
| --- | --- | --- |
| BeanCreationException trong @PostConstruct | Config/state validation hoặc code init lỗi | Root cause + stack frame callback |
| BeanCreationException trong afterPropertiesSet | Spring-specific init logic fail | Kiểm tra injected properties/invariant |
| BeanCreationException từ initMethod | Method name/signature/body lỗi | BeanDefinition init method metadata |
| Exception trong BPP.before | Processor validate/modify object không đúng | Xem BPP class, condition, order |
| Exception trong BPP.after | Proxy/wrapper creation hoặc code processor lỗi | Kiểm tra target interface/final class/pointcut |
| Bean "not eligible for ... all BPPs" | Bean instantiate trong BPP registration phase | Tìm BPP constructor dependencies và eager lookups |
| Bean không có @Transactional proxy | AOP infrastructure missing hoặc early creation | Kiểm tra proxy type + actual advice execution |
| @Async method chạy đồng bộ | Không qua proxy, hoặc async BPP chưa áp | Gọi qua exposed bean và test executor |
| @Cacheable không cache | Self-invocation hoặc bean unproxied | Test calls qua Spring-managed proxy |
| @PostConstruct không chạy | Jakarta import/API, processor registration hoặc bean unmanaged | Kiểm tra jakarta.annotation + Spring context |
| @Bean return Object tạo BPP | Processor type bị che khi early detection | Khai báo concrete BPP return type |
| @Configuration chứa nonstatic BPP @Bean và heavy dependencies | Config bean bị tạo quá sớm | Static @Bean không dependencies |
| Bean có BPP.before nhưng không có BPP.after | Callback init failure, processor chain stop | Xem main exception + null returns |
| Custom BPP trả null | Các processors sau có thể bị short-circuit | Trả bean nếu không muốn thay đổi |
| Bean đã tạo trước khi add BPP | Post-processors không retroactive | Tạo bean sau registration |
| Unit test new Bean bình thường, integration khác | Unit test bỏ Spring BPP lifecycle | Dùng Spring context integration test |
| Bean bị wrap vào proxy sai type | JDK proxy vs concrete dependency | Dùng interface/kiểm tra AOP settings |
| Prototype bean lỗi khi getBean lần hai | Init xảy ra mỗi request | Test từng retrieval, cleanup riêng |
| Circular reference + proxy mismatch | Early raw reference khác exposed proxy | Phá vòng, xem getEarlyBeanReference |

## 12. Ví dụ runnable Java 17 + Spring 6.2.9

Ví dụ duy nhất này minh họa **hai nhóm lỗi** bằng cách hoàn toàn độc lập:

1. Một `AnnotationConfigApplicationContext` có `static @Bean GuardProcessor` để BPP được đăng ký sớm. `GoodBean` chạy đủ `@PostConstruct`, `InitializingBean`, custom init method và BPP.before/BPP.after.
2. Bốn bean `@Lazy` cố tình fail ở `@PostConstruct`, `afterPropertiesSet`, custom init và `BPP.after`. Mỗi lỗi được lấy qua **context riêng** để không nhiễm trạng thái.
3. Một plain `DefaultListableBeanFactory` tạo `early` trước khi thêm `MarkerProcessor`, tạo `late` sau đó. Không có automatic BPP-registration phase, nhưng thí nghiệm chứng minh **processor không được áp ngược lên instance cũ**. Nó không cố giả mạo warning tự động của ApplicationContext.

### 12.1. Thư mục

~~~text
spring-init-diagnostics/
├── pom.xml
└── src/main/java/demo/
    └── InitializationDiagnosticsDemo.java
~~~

### 12.2. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-init-diagnostics</artifactId>
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
        <dependency>
            <groupId>jakarta.annotation</groupId>
            <artifactId>jakarta.annotation-api</artifactId>
            <version>2.1.1</version>
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

### 12.3. src/main/java/demo/InitializationDiagnosticsDemo.java

~~~java
package demo;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

public class InitializationDiagnosticsDemo {

    // Success path: ba init callbacks và custom BeanPostProcessor.
    public static final class GoodBean implements InitializingBean {
        boolean postConstruct;
        boolean afterPropertiesSet;
        boolean customInit;
        boolean beforeProcessed;
        boolean afterProcessed;

        @PostConstruct
        public void configure() {
            postConstruct = true;
        }

        @Override
        public void afterPropertiesSet() {
            afterPropertiesSet = true;
        }

        public void finishInitialization() {
            customInit = true;
        }
    }

    public static final class PostConstructFailure {
        @PostConstruct
        public void initialize() {
            throw new IllegalStateException("postconstruct-error");
        }
    }

    public static final class InitializingFailure
            implements InitializingBean {

        @Override
        public void afterPropertiesSet() {
            throw new IllegalArgumentException(
                    "after-properties-set-error");
        }
    }

    public static final class CustomInitFailure {
        public void initialize() {
            throw new IllegalStateException("custom-init-error");
        }
    }

    public static final class AfterProcessorFailure {
    }

    public static final class GuardProcessor
            implements BeanPostProcessor {

        @Override
        public Object postProcessBeforeInitialization(
                Object bean, String name) {

            if (bean instanceof GoodBean good) {
                good.beforeProcessed = true;
            }
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(
                Object bean, String name) {

            if ("afterProcessorFailure".equals(name)) {
                throw new IllegalStateException(
                        "after-processor-error");
            }

            if (bean instanceof GoodBean good) {
                good.afterProcessed = true;
            }
            return bean;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {

        // Static và concrete return type để Spring phát hiện BPP sớm.
        @Bean
        public static GuardProcessor guardProcessor() {
            return new GuardProcessor();
        }

        @Bean(initMethod = "finishInitialization")
        @Lazy
        public GoodBean goodBean() {
            return new GoodBean();
        }

        @Bean("postConstructFailure")
        @Lazy
        public PostConstructFailure postConstructFailure() {
            return new PostConstructFailure();
        }

        @Bean("initializingFailure")
        @Lazy
        public InitializingFailure initializingFailure() {
            return new InitializingFailure();
        }

        @Bean(name = "customInitFailure",
              initMethod = "initialize")
        @Lazy
        public CustomInitFailure customInitFailure() {
            return new CustomInitFailure();
        }

        @Bean("afterProcessorFailure")
        @Lazy
        public AfterProcessorFailure afterProcessorFailure() {
            return new AfterProcessorFailure();
        }
    }

    private static BeanCreationException failure(
            String beanName) {

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            try {
                context.getBean(beanName);
            } catch (BeanCreationException expected) {
                return expected;
            }

            throw new AssertionError(
                    "Expected failure: " + beanName);
        }
    }

    // Chỉ kiểm tra type/bean name, không assert full exception text.
    private static void printFailure(
            String label, String beanName) {

        BeanCreationException error = failure(beanName);

        System.out.println(label + ".root="
                + error.getMostSpecificCause()
                       .getClass().getSimpleName());

        System.out.println(label + ".beanSeen="
                + beanName.equals(error.getBeanName()));
    }

    public static class MarkerBean {
        boolean marked;
    }

    public static class MarkerProcessor
            implements BeanPostProcessor {

        @Override
        public Object postProcessAfterInitialization(
                Object bean, String name) {

            if (bean instanceof MarkerBean marker) {
                marker.marked = true;
            }
            return bean;
        }
    }

    private static void earlyVsLateProcessor() {
        DefaultListableBeanFactory factory =
                new DefaultListableBeanFactory();

        factory.registerBeanDefinition(
                "early", new RootBeanDefinition(MarkerBean.class));

        // Bean này đã được tạo khi chưa có MarkerProcessor.
        MarkerBean early = factory.getBean(
                "early", MarkerBean.class);

        factory.addBeanPostProcessor(new MarkerProcessor());

        factory.registerBeanDefinition(
                "late", new RootBeanDefinition(MarkerBean.class));

        MarkerBean late = factory.getBean(
                "late", MarkerBean.class);

        System.out.println("early.marked=" + early.marked);
        System.out.println("late.marked=" + late.marked);
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            GoodBean good = context.getBean(GoodBean.class);

            System.out.println("good.postConstruct="
                    + good.postConstruct);

            System.out.println("good.afterPropertiesSet="
                    + good.afterPropertiesSet);

            System.out.println("good.customInit="
                    + good.customInit);

            System.out.println("good.beforeProcessed="
                    + good.beforeProcessed);

            System.out.println("good.afterProcessed="
                    + good.afterProcessed);
        }

        printFailure(
                "postConstruct", "postConstructFailure");

        printFailure(
                "initializingBean", "initializingFailure");

        printFailure(
                "customInit", "customInitFailure");

        printFailure(
                "postProcessor", "afterProcessorFailure");

        earlyVsLateProcessor();
    }
}
~~~

### 12.4. Chạy

Từ thư mục `spring-init-diagnostics`:

~~~bash
java -version
mvn -version
mvn -q compile exec:java -Dexec.mainClass=demo.InitializationDiagnosticsDemo
~~~

**Stdout do chương trình in**, không tính các Maven/Spring logger messages:

~~~text
good.postConstruct=true
good.afterPropertiesSet=true
good.customInit=true
good.beforeProcessed=true
good.afterProcessed=true
postConstruct.root=IllegalStateException
postConstruct.beanSeen=true
initializingBean.root=IllegalArgumentException
initializingBean.beanSeen=true
customInit.root=IllegalStateException
customInit.beanSeen=true
postProcessor.root=IllegalStateException
postProcessor.beanSeen=true
early.marked=false
late.marked=true
~~~

### 12.5. Diễn giải kết quả

| Output | Vì sao? |
| --- | --- |
| `good.postConstruct=true` | Annotation processor gọi `@PostConstruct` |
| `good.afterPropertiesSet=true` | Container gọi `InitializingBean` sau property population |
| `good.customInit=true` | `@Bean(initMethod="finishInitialization")` được xử lý |
| `good.beforeProcessed=true` | Custom GuardProcessor tham gia before initialization |
| `good.afterProcessed=true` | Custom GuardProcessor tham gia after initialization |
| `postConstruct.root=IllegalStateException` | Method @PostConstruct cố tình throw |
| `postConstruct.beanSeen=true` | Outer BeanCreationException nhắc đúng bean name |
| `initializingBean.root=IllegalArgumentException` | afterPropertiesSet() fail |
| `initializingBean.beanSeen=true` | Factory wrapper giữ bean name |
| `customInit.root=IllegalStateException` | Custom init method fail |
| `customInit.beanSeen=true` | Exception nhắc bean customInitFailure |
| `postProcessor.root=IllegalStateException` | GuardProcessor.after cố tình ném |
| `postProcessor.beanSeen=true` | Spring không công bố bean thất bại |
| `early.marked=false` | MarkerBean được tạo trước khi processor đăng ký |
| `late.marked=true` | MarkerBean được tạo sau registration nên nhận BPP |

### 12.6. Vì sao code không in thứ tự từng callback?

Trong success path, chúng ta khẳng định tất cả flags đều true **sau khi getBean hoàn tất**, điều này ổn định. Nếu in từng callback khi chạy, thứ tự giữa custom BPP.before và annotation processor `@PostConstruct` có thể thay đổi theo thứ tự registration. Bài không dựa trên thứ tự này.

### 12.7. Tại sao những bean lỗi đều @Lazy?

Nếu bean fail là eager singleton, context sẽ fail ngay khi `refresh()`/constructor nhận config được gọi, trước khi main có cơ hội phân loại từng tình huống. `@Lazy` cho phép context khởi tạo trước rồi `getBean(name)` cố ý gây lỗi trong từng case. Nó **không sửa** callback; đây chỉ là kỹ thuật test isolation.

### 12.8. Vì sao marker experiment dùng DefaultListableBeanFactory?

Nó mô phỏng rõ quy tắc không retroactive BPP bằng API level thấp, không phụ thuộc vào logs hoặc BPP autodetection. Trong ApplicationContext thật, cảnh báo “not eligible” thường xuất hiện **khi BPP infrastructure đang được khởi tạo**. Experiment chỉ chứng minh một hệ quả căn bản: nếu bean được tạo trước processor, processor mới không tự quay lại chạy với bean cũ.

### 12.9. Các thí nghiệm hữu ích

**A — Bỏ static khỏi guardProcessor():** Processor có thể khiến configuration class instantiate sớm, ảnh hưởng warning/ordering. Không assume mọi configuration sẽ báo lỗi: rủi ro phụ thuộc object graph của app.

**B — Cho guardProcessor() trả Object:** Spring có thể không autodetect nó đúng thời điểm như BeanPostProcessor, nên success flags có thể khác. Tốt hơn giữ concrete processor return type.

**C — Chuyển @PostConstruct thành method private:** Tùy lifecycle annotation contract/reflective constraints, có thể hoạt động hoặc fail trong một số module/runtime. Với native image và JPMS, tránh phụ thuộc reflection access không được hỗ trợ và test runtime thực.

**D — Xóa annotation dependency:** Khi compile mà code import jakarta.annotation.PostConstruct, Java compiler có thể báo package không tồn tại nếu classpath không có API.

**E — Bỏ throw khỏi CustomInitFailure.initialize():** `getBean("customInitFailure")` sẽ thành công nếu không có lỗi khác; viết positive test thay vì chỉ xóa assertion.

**F — Đổi GuardProcessor.after trả null:** Một số processors phía sau có thể bị skip theo contract; không nên dùng null để biểu thị no-op.

**G — Gọi getBean("afterProcessorFailure") nhiều lần:** Bean fail không hoàn tất singleton creation. Đừng dựa vào failed instance được cache như successful singleton.

**H — Đưa MarkerProcessor vào factory trước getBean("early"):** Cả early và late được xử lý, hai flags true. Đây là cách sửa đúng trong plain BeanFactory.

**I — Thêm @Transactional vào GoodBean:** Annotation tự nó không đủ; cần transaction manager và proxy infrastructure thực. Bài không giả vờ demo có transaction advice chỉ vì annotation xuất hiện.

**J — Dùng `@Bean(initMethod=...)` và `@PostConstruct` cùng side effect:** Nếu side effect không idempotent, có thể bị thực hiện nhiều lần khi nhiều callback gọi cùng business logic. Tách và validate rõ trách nhiệm từng phase.

## 13. Cách tái tạo "not eligible for auto-proxying" trong ApplicationContext

Một anti-pattern:

~~~java
@Configuration(proxyBeanMethods = false)
class EarlyConfig {

    @Bean
    public BillingService billingService() {
        return new BillingService();
    }

    @Bean
    public static AuditBeanPostProcessor auditProcessor(
            BillingService billingService) {

        return new AuditBeanPostProcessor(billingService);
    }
}
~~~

Đây là **snippet**, `BillingService` và `AuditBeanPostProcessor` là types do ứng dụng định nghĩa. Mặc dù method static, BPP **có parameter BillingService**, nên Spring phải instantiate BillingService khi tạo processor. Tùy những auto-proxy processors đã được registered ở thời điểm ấy, BillingService có thể trở thành bean không eligible cho full chain.

### Tách dependency nếu BPP không thật sự cần service

Nếu AuditBeanPostProcessor chỉ cần metadata hoặc configuration string, truyền một immutable lightweight setting, hoặc lấy thông tin từ BeanDefinition/Environment khi phù hợp thay vì inject một business service có AOP. Nếu service cần dùng sau khi context khởi động, thiết kế late dependency rõ ràng như `ObjectProvider<BillingService>` có thể hữu ích — nhưng phải tránh gọi `getObject()` ngay trong BPP constructor/registration phase.

### Static @Bean là cần thiết nhưng chưa đủ

~~~java
@Bean
public static AuditBeanPostProcessor auditProcessor() {
    return new AuditBeanPostProcessor();
}
~~~

Đây là snippet minh họa với constructor phù hợp. Static factory tránh eager configuration-instance creation, còn **không inject business dependencies** tránh kéo những regular beans vào infrastructure startup. Nếu BPP constructor vẫn gọi `applicationContext.getBean(...)` để lấy business service, rủi ro vẫn còn.

### Tại sao warning không đồng nghĩa luôn có lỗi?

Nó là cảnh báo về **eligibility đối với mọi BPP**, không chứng minh bean đã thiếu một advisor cụ thể. Một số beans là infrastructure không cần AOP; warning với chúng có thể ít ảnh hưởng, nhưng phải xác minh. Với business bean cần transaction/security, nên xem warning là chỉ dấu cần kiểm tra nghiêm túc.

## 14. Cách kiểm tra exposed object thực sự được proxy

Khi một Spring AOP proxy được tạo, consumer có thể nhận proxy, không phải raw target. Nếu BeanPostProcessor auto-proxy chưa có mặt khi bean được tạo, consumer có thể nhận raw object.

Có thể dùng:

~~~java
boolean proxied = AopUtils.isAopProxy(bean);
boolean jdk = AopUtils.isJdkDynamicProxy(bean);
boolean cglib = AopUtils.isCglibProxy(bean);
Class<?> type = AopUtils.getTargetClass(bean);
~~~

Đây là snippet; import `org.springframework.aop.support.AopUtils` và có `bean`. Kết quả là quan sát proxy identity/type, **không** tự chứng minh @Transactional advice đã chạy trên method cụ thể.

### Phân biệt JDK/CGLIB

- JDK proxy expose interfaces; `bean instanceof ConcreteTarget` có thể false.
- CGLIB class proxy là subclass, nhưng final classes/final methods không được advise theo cơ chế override.
- Với proxy-aware tests, dependency injection nên theo business interface khi phù hợp.
- Self-invocation qua `this.method()` vẫn bypass Spring AOP proxy dù bean đã được proxy đúng.

### Test behavior thay vì chỉ test isAopProxy

Với `@Transactional`, dùng transaction manager và assert transaction active/rollback trên external invocation. Với `@Async`, verify executor/thread behavior; với caching, assert cache hits/misses. Một bean có proxy nhưng interceptor không match method vẫn không có behavior bạn mong.

### Không kích hoạt BPP để debug bằng cách gọi getBean bất kỳ

Nếu đang trong `BeanFactoryPostProcessor` hoặc `BeanPostProcessor` registration phase, việc gọi `getBean(...)` cho domain service **có thể tạo lỗi bạn đang chẩn đoán**. Thu thập metadata nhẹ, dùng logging có giới hạn hoặc inspect BeanDefinitions trước.

## 15. Phân biệt failure với circular dependency và early reference

Một bean A đang được tạo, A cần B, B quay lại A; trong singleton setter/field cycle, Spring có thể expose early reference của A nếu circular references được phép. Với AOP, early reference có thể cần proxy từ `SmartInstantiationAwareBeanPostProcessor.getEarlyBeanReference(...)`.

~~~text
create raw A
   |
register early ObjectFactory for A
   |
create B, B needs A
   |
getEarlyBeanReference(A)  -> raw A hoặc proxy tùy infrastructure
   |
B receives reference
   |
finish A initialization/wrapping
~~~

Nếu B giữ **raw A** nhưng BeanFactory cuối công bố `Proxy(A)`, có thể xảy ra inconsistency và advice bypass; Spring có cơ chế fail-fast trong một số tình huống. Lỗi này khác với `@PostConstruct` throw và khác warning BPP eligibility ở startup, dù chúng có thể xuất hiện gần nhau trong logs.

Các cờ như `allowCircularReferences` hay `allowRawInjectionDespiteWrapping` **không** là giải pháp mặc định cho callback failures hoặc missing proxy. Nếu có vòng, ưu tiên refactor graph, không cố tạo BeanFactory fallback nguy hiểm.

## 16. Kiểm thử và cách cô lập lỗi

### 16.1. Unit test callback thuần Java

Một unit test gọi `new GoodBean()` rồi gọi method của nó có thể test internal business logic, nhưng **không kiểm chứng Spring đã gọi @PostConstruct hoặc BPP**. Để kiểm thử container lifecycle, dựng `ApplicationContext` thật.

### 16.2. Integration test success path

Ví dụ JUnit Jupiter:

~~~java
try (AnnotationConfigApplicationContext context =
             new AnnotationConfigApplicationContext(
                     InitializationDiagnosticsDemo.Config.class)) {

    InitializationDiagnosticsDemo.GoodBean good =
            context.getBean(InitializationDiagnosticsDemo.GoodBean.class);

    assertTrue(good.postConstruct);
    assertTrue(good.afterPropertiesSet);
    assertTrue(good.customInit);
    assertTrue(good.beforeProcessed);
    assertTrue(good.afterProcessed);
}
~~~

Đây là test snippet, cần import JUnit assertions và test class ở cùng package `demo` để truy cập package-private fields. Nó yêu cầu Spring dependencies và JUnit Jupiter; không thuộc main demo source.

### 16.3. Kiểm thử failure path

~~~java
BeanCreationException ex = assertThrows(
        BeanCreationException.class,
        () -> context.getBean("postConstructFailure"));

assertInstanceOf(
        IllegalStateException.class,
        ex.getMostSpecificCause());
~~~

Đây là snippet trong context `Config` được khởi tạo riêng và chưa dùng `getBean` cho failed bean trước đó. Với Spring TestContext, test context có thể được cache; đừng mutate một shared context giữa nhiều failure tests.

### 16.4. Kiểm thử processor registration timing

Trong plain BeanFactory, test `early.marked == false` và `late.marked == true`. Sau khi sửa startup order để processor có trước beans, assert cả hai marked true. Đây là test **thời điểm áp BPP**, không test Spring Boot auto-proxy infrastructure đầy đủ.

### 16.5. Kiểm thử actual AOP behavior

Nếu warning nói not eligible for auto-proxying và business bean yêu cầu transaction/security, test phải kiểm tra injected service là Spring exposed proxy và **external method call** có advice tương ứng. Không dùng `context.getBean(...) != null` làm test duy nhất.

### 16.6. Negative tests không phụ thuộc exact log message

Spring có thể thay warning wording/logger/category theo phiên bản. Dùng bean identity/flag/advice behavior assertions, exception type/root cause và BeanDefinition metadata; log-capture test chỉ cần thiết khi hệ thống có yêu cầu observability cụ thể.

## 17. Troubleshooting theo bước

**Bước 1 — Bean có tạo xong không?** Nếu `getBean(...)` ném `BeanCreationException`, xem `getMostSpecificCause()` và outer bean name. Nếu lấy bean thành công nhưng mất advice/marker, kiểm tra BPP eligibility.

**Bước 2 — Xác định điểm lỗi.** Constructor/factory, property injection, `@PostConstruct`, `afterPropertiesSet`, custom init hay postProcessBefore/After? Stack frames và callback method name là gợi ý.

**Bước 3 — Kiểm tra annotation/API.** Với Spring 6 Java 17, `jakarta.annotation.PostConstruct` và dependency phù hợp. `CommonAnnotationBeanPostProcessor` phải có trong context.

**Bước 4 — Kiểm tra BPP registration.** Trong `ApplicationContext`, BPP có BeanDefinition được nhận diện bằng type không? `@Bean` return type có là concrete BPP/BeanPostProcessor? Static và không có dependencies nặng?

**Bước 5 — Tìm bean tạo sớm.** BPP constructor, factory method args, @Autowired/@Resource fields hoặc bean factory getBean trong post-processor có kéo business bean vào early phase không?

**Bước 6 — Kiểm tra proxy.** Nếu kỳ vọng transaction/async/cache, xem actual exposed object, không chỉ target class. Kiểm tra AOP infrastructure, proxy strategy và external method calls.

**Bước 7 — Cô lập test.** Dựng minimal ApplicationContext cho một failure; dùng @Lazy khi muốn kiểm soát thời điểm getBean fail; không gộp tất cả beans lỗi vào một context eager.

**Bước 8 — Sửa đúng nguyên nhân.** Validate config, chia responsibilities, loại dependency BPP sớm, return đúng type, chuyển static factory, hoặc đăng ký processor trước bean creation.

**Bước 9 — Kiểm tra regression.** Có tests cho callback success, callback failure root cause, BPP before/after participation và actual AOP advice cần thiết.

## 18. Checklist áp dụng

- [ ] Phân biệt bean instance raw với singleton đã initialized/proxied.
- [ ] Biết @PostConstruct do BPP infrastructure thực hiện.
- [ ] Dùng jakarta.annotation trên Spring 6 / Java 17.
- [ ] Không giả định custom BPP.before luôn trước @PostConstruct.
- [ ] Biết @PostConstruct → InitializingBean → custom init là thứ tự callback loại tương ứng.
- [ ] Không gọi business work nặng trong init callbacks khi không cần.
- [ ] Khi callback throw, đọc BeanCreationException và root cause đầy đủ.
- [ ] Không coi BPP.after là cleanup callback luôn chạy sau failure.
- [ ] Không dùng BPP return null làm no-op.
- [ ] Không nhầm BeanFactoryPostProcessor và BeanPostProcessor.
- [ ] Nếu warning not eligible, tìm phase BPP registration.
- [ ] Không inject domain service vào BPP nếu có thể tránh.
- [ ] Khi dùng @Bean tạo BPP, ưu tiên static method không dependencies.
- [ ] Khai báo @Bean return type là concrete BPP hoặc BeanPostProcessor.
- [ ] Không eager getBean cho business service trong infrastructure startup.
- [ ] Hiểu programmatic BPP registration order.
- [ ] Không giả định processor mới retroactively xử lý singleton cũ.
- [ ] Nếu thiếu AOP proxy, test actual advice chứ không chỉ class name.
- [ ] Phân biệt early circular reference với BPP eligibility warning.
- [ ] Không bật allowCircularReferences/allowRawInjectionDespiteWrapping như fix tổng quát.
- [ ] Dùng isolated context cho negative tests.
- [ ] Test trường hợp lazy bean fail tại lần getBean đầu tiên.
- [ ] Đảm bảo không leak secrets trong diagnostics.
- [ ] Sau sửa có positive integration test.

## 19. Câu hỏi tự kiểm tra

**1. Bean có constructor chạy xong là initialized chưa?**  
Chưa. Nó có thể cần dependency injection, @PostConstruct, InitializingBean, custom init và BPP.after.

**2. @PostConstruct được gọi bởi ai?**  
Lifecycle annotation processing qua BeanPostProcessor, thường CommonAnnotationBeanPostProcessor.

**3. Vì sao Spring 6 dùng jakarta.annotation.PostConstruct?**  
Các Jakarta annotations đã chuyển namespace khỏi javax.annotation cũ; Java 17 không tự cung cấp javax.annotation của Java 8.

**4. Ba init callback loại chính chạy theo thứ tự nào?**  
@PostConstruct, InitializingBean.afterPropertiesSet, custom init method.

**5. Một custom BPP.before có chắc chạy trước @PostConstruct không?**  
Không, vì @PostConstruct được xử lý bởi một BPP trước-init khác; ordering quyết định.

**6. Nếu @PostConstruct throw, bean có được công bố thành singleton hoàn chỉnh không?**  
Không. Bean creation thất bại.

**7. Nếu BPP.after throw, có thể bị BeanCreationException không?**  
Có, vì failure xảy ra trong bean initialization pipeline.

**8. BPP returning null có luôn ném exception không?**  
Không; nó có thể làm những BPP tiếp theo trong phase bị bỏ qua.

**9. Warning not eligible for all BeanPostProcessors có đồng nghĩa bean không tồn tại?**  
Không. Bean có thể đã tạo quá sớm và vẫn tồn tại nhưng không nhận full processing.

**10. Tại sao BPP inject business service nguy hiểm?**  
Nó khiến business service instantiate trong early registration phase, trước khi mọi BPP/auto-proxy creator sẵn sàng.

**11. Tại sao BPP @Bean nên static và return type concrete?**  
Để được phát hiện từ metadata và tránh tạo enclosing configuration instance/dependencies quá sớm.

**12. Bean tạo trước addBeanPostProcessor có tự được xử lý sau không?**  
Không. Processor không retroactively áp vào singleton instance đã tạo.

**13. Vì sao @Transactional không chạy dù bean được inject?**  
Có thể vì không đi qua proxy, thiếu infrastructure, self-invocation hoặc bean đã tạo trước auto-proxy BPP.

**14. isAopProxy=true có chứng minh @Transactional trên method X chạy không?**  
Không. Cần kiểm tra advisor match và actual behavior.

**15. Khi init method fail, root cause có thể là IllegalArgumentException không?**  
Có, nếu validation logic trong method throw exception đó.

**16. Spring Boot và Spring Framework khác nhau thế nào về BPP?**  
Boot sử dụng Spring Core lifecycle/BPP nhưng có thêm auto-configuration và infrastructure; cần test trong actual Boot context nếu app dùng Boot.

**17. Có nên dùng @Lazy để chữa BPP exception không?**  
Không. Nó chỉ trì hoãn lúc lỗi xảy ra, trừ khi business design thật sự muốn late initialization.

**18. Circular setter dependency có thể gây proxy mismatch không?**  
Có, early raw reference và eventual proxy có thể khác; cần phá vòng hoặc bảo đảm proxy-aware infrastructure.

**19. Vì sao tests cần tách failure contexts?**  
Để một bean hỏng không chặn việc kiểm thử những bean hỏng khác và tránh state contamination.

**20. Công thức chẩn đoán ngắn gọn?**  
**Bean creation failure** → xác định callback/root cause. **Bean tồn tại nhưng thiếu advice** → tìm thời điểm đăng ký BPP, premature creation và actual proxy/injection behavior.

## 20. Tài liệu chính thức

- [Spring Framework 6.2 Reference — Container Extension Points](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-extension.html): BeanPostProcessor, registration, early initialization eligibility, static @Bean.
- [Spring Framework 6.2 Reference — Customizing the Nature of a Bean](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html): lifecycle callbacks và locking.
- [Spring Framework 6.2 Reference — Using @PostConstruct and @PreDestroy](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/postconstruct-and-predestroy-annotations.html).
- [Spring Framework 6.2 Reference — Using the @Bean Annotation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html): initMethod và lifecycle.
- [Spring Framework 6.2 Javadoc — BeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/config/BeanPostProcessor.html).
- [Spring Framework 6.2 Javadoc — Bean](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/annotation/Bean.html): static BPP-returning factory guidance.
- [Spring Framework 6.2 Javadoc — InitializingBean](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/InitializingBean.html).
- [Spring Framework 6.2 Javadoc — CommonAnnotationBeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/annotation/CommonAnnotationBeanPostProcessor.html).
- [Spring Framework 6.2 Javadoc — AbstractAutowireCapableBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/support/AbstractAutowireCapableBeanFactory.html).
- [Spring Framework 6.2 Javadoc — BeanCreationException](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/BeanCreationException.html).
- [Spring Framework 6.2 Reference — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html).
- [Spring Framework 6.2 Reference — Lazy-initialized Beans](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-lazy-init.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="troubleshooting-009">Phân biệt lỗi lúc refresh với lỗi khi truy cập bean lazy</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt lỗi lúc refresh với lỗi khi truy cập bean lazy trong Spring Core Container

## 1. Đặt vấn đề

Một Spring bean có thể được khai báo hợp lệ nhưng **không tạo được instance**. Cùng một lỗi constructor, factory method hoặc callback khởi tạo, Spring có thể phát hiện **khi `ApplicationContext.refresh()`** hoặc **khi mã ứng dụng truy cập bean lần đầu**. Nguyên nhân khác biệt là chính sách eager/lazy và đường phụ thuộc giữa các bean.

- **Eager singleton:** tạo trong refresh; lỗi thường làm context không khởi động.
- **Lazy singleton độc lập:** definition có nhưng instance chưa tạo; refresh có thể thành công, `getBean()` lần đầu mới fail.
- **Lazy bean là required dependency của eager singleton:** vẫn bị buộc tạo trong refresh.
- **`@Lazy` tại injection point:** consumer được inject lazy-resolution proxy; target có thể chỉ được tạo khi proxy nhận lời gọi.
- **`ObjectProvider<T>`:** bean consumer nhận provider, lỗi target dời tới `getObject()` hoặc thao tác lookup thích hợp.

```text
EAGER
refresh -> create A -> failure -> startup FAIL

LAZY DEFINITION
refresh -> register A -> startup PASS
request -> getBean(A) -> create A -> failure -> request FAIL

EAGER DEPENDS ON LAZY
refresh -> create B(A) -> getBean(A) -> failure -> startup FAIL

LAZY INJECTION PROXY
refresh -> create B(proxy to A) -> PASS
later B.method() -> proxy resolves A -> failure -> call FAIL
```

Điều cần chẩn đoán là **thời điểm tạo instance**, không phải đánh giá “lỗi lazy nhẹ hơn”. Trì hoãn phát hiện cấu hình hỏng tới request đầu tiên đôi khi gây tác hại hơn fail-fast deployment.

## 2. Refresh và eager pre-instantiation

`AnnotationConfigApplicationContext.refresh()` hoàn thiện cấu hình bean factory, chạy các `BeanFactoryPostProcessor` và `BeanPostProcessor`, thiết lập infrastructure, rồi tạo regular non-lazy singleton beans. Chuỗi khái quát:

```text
refresh()
  -> prepareRefresh()
  -> obtainFreshBeanFactory()
  -> invokeBeanFactoryPostProcessors()
  -> registerBeanPostProcessors()
  -> initApplicationEventMulticaster()
  -> finishBeanFactoryInitialization()
       -> preInstantiateSingletons()
            -> constructors / @Bean methods
            -> injection / property population
            -> @PostConstruct / initialization
            -> proxying / final singleton exposure
  -> finishRefresh()
```

Sơ đồ không phải exact stacktrace của mọi subclass `ApplicationContext`. Điểm then chốt là regular eager singletons được tạo trước khi refresh thành công; việc khởi tạo thất bại sẽ ngăn startup hoàn tất bình thường. Spring Reference khuyến khích eager mặc định vì lỗi cấu hình được phát hiện sớm.

**`context.isActive()==true` không chứng minh mọi bean đều usable**: lazy singleton, prototype, request/session bean hoặc lazy proxy target có thể chưa được instantiate.

## 3. Eager bean: lỗi xuất hiện trong refresh

Một factory method trả bean không lazy:

```java
@Configuration(proxyBeanMethods = false)
class EagerConfig {
    @Bean
    FaultyClient faultyClient() {
        return new FaultyClient();
    }
}
```

Đây là fragment; `FaultyClient` cần được định nghĩa. Nếu constructor `FaultyClient()` throw `IllegalStateException`, Spring có thể bọc bằng `BeanCreationException`, và lời gọi `ctx.refresh()` fail. Nếu một eager consumer cần target trước, `UnsatisfiedDependencyException` cũng có thể xuất hiện ở outer layer.

Đọc chuỗi causes từ ngoài vào trong: outer bean name/injection point chỉ **nơi fail**, deepest meaningful cause chỉ **vì sao**.

## 4. Bean @Lazy: metadata tồn tại, instance chưa chắc đã tồn tại

```java
@Configuration(proxyBeanMethods = false)
class LazyConfig {
    @Bean
    @Lazy
    FaultyClient faultyClient() {
        return new FaultyClient();
    }
}
```

Fragment trên đánh dấu bean definition lazy. Khi không có eager dependency yêu cầu nó:
1. Context parse `@Bean` và đăng ký `BeanDefinition`.
2. `refresh()` có thể trả thành công mà chưa gọi factory method.
3. `containsBeanDefinition("faultyClient")` trả true, nhưng **không đảm bảo đã có instance**.
4. `ctx.getBean(FaultyClient.class)` kích hoạt việc tạo và có thể ném `BeanCreationException`.
5. Nếu tạo thành công, lần lookup sau thường trả **cùng singleton**, không tạo mới như prototype.

**Không nhầm `@Lazy` với `@Scope("prototype")`.** Lazy thay thời điểm tạo singleton, còn scope quyết định lifecycle/identity.

### Metadata không giống health check

`getBeanDefinition("faultyClient").isLazyInit()` cho biết flag lazy trong bean definition; nó không chứng minh bean vẫn chưa được tạo, vì một dependency hoặc một lời gọi `getBean` khác có thể đã tạo target.

## 5. Tại sao bean lazy vẫn khiến refresh thất bại?

```java
@Bean
@Lazy
FaultyClient faultyClient() {
    return new FaultyClient();
}

@Bean
ReportService reportService(FaultyClient client) {
    return new ReportService(client);
}
```

Hai `@Bean` method fragments này cho thấy **reportService eager** cần **FaultyClient instance thật** làm factory-method argument. Container phải tạo FaultyClient để tạo ReportService. Lỗi vẫn phát sinh trong `refresh()` mặc dù target mang `@Lazy`.

Các đường khác buộc lazy bean được tạo sớm:
- Constructor/field/setter dependency không lazy của bean eager.
- `@DependsOn("faultyClient")` trên bean eager.
- Một infrastructure processor hoặc startup hook chủ động gọi `getBean`.
- `@PostConstruct` của eager collaborator truy cập lazy dependency.
- Type/product resolution đặc biệt hoặc custom factory logic gây eager initialization.

Đừng kết luận Spring “bỏ qua `@Lazy`”. Đây là yêu cầu dependency có thật.

## 6. @Lazy tại injection point khác @Lazy tại bean definition

```java
@Bean
Consumer consumer(@Lazy Port port) {
    return new Consumer(port);
}
```

Đây là factory method fragment cần interface `Port`, class `Consumer` và imports. Annotation **trên parameter** yêu cầu lazy-resolution proxy cho dependency. Spring có thể tạo consumer mà chưa tạo target.

```text
refresh -> Consumer(proxy Port) -> PASS
later consumer.use() -> proxy.fetch()
       -> lookup actual Port bean
       -> target constructor or init FAIL
```

Một proxy vẫn có giới hạn:
- Target có thể không tồn tại, lỗi chỉ xuất hiện khi invocation.
- JDK proxy làm việc với interfaces; class proxy chịu hạn chế final class/method.
- Nếu consumer gọi `port.fetch()` ngay trong constructor/`@PostConstruct`, lỗi có thể quay về startup phase.
- Annotation không xác thực rằng remote dependency đang sống hoặc user request hợp lệ.
- `@Lazy` injection point không đồng nghĩa `@Lazy` bean definition, và ngược lại.

Spring Javadoc khuyến nghị `ObjectProvider` cho deferred lookup phức tạp hoặc optional dependencies để có semantics rõ ràng.

## 7. ObjectProvider: lỗi tại thời điểm getObject

```java
public final class ReportService {
    private final ObjectProvider<FaultyClient> provider;

    public ReportService(ObjectProvider<FaultyClient> provider) {
        this.provider = provider;
    }

    public String run() {
        return provider.getObject().ping();
    }
}
```

Class fragment cần imports/types. Inject provider **không bắt buộc** tạo target. Lúc `getObject()`, container mới resolve target:
- Không có bean -> `NoSuchBeanDefinitionException`.
- Nhiều beans không phân biệt -> `NoUniqueBeanDefinitionException`.
- Target constructor/init fail -> `BeanCreationException`.
- Target tạo đúng -> trả bean instance.

`getIfAvailable()`, `getIfUnique()` và `stream()` có hợp đồng khác, không phải cách tự động chữa một configuration hỏng. **Gọi `provider.getObject()` trong constructor consumer chỉ biến deferred lookup thành eager lookup.**

## 8. Lifecycle callbacks cũng có thể fail sau startup

Một lazy singleton, khi được yêu cầu, vẫn phải hoàn tất lifecycle của nó:

```text
first getBean
  -> constructor/factory method
  -> inject properties
  -> BeanPostProcessor beforeInitialization
       -> @PostConstruct
  -> InitializingBean.afterPropertiesSet()
  -> custom init-method
  -> BeanPostProcessor afterInitialization
  -> publish initialized singleton
```

Nếu `@PostConstruct` ném, lỗi xảy ra **khi bean lazy được tạo**, không nhất thiết ở refresh. Ngược lại, cùng callback trên eager singleton có thể làm refresh fail.

Lỗi không chỉ giới hạn constructor; cần đọc đầy đủ `BeanCreationException`, `BeanInstantiationException`, `UnsatisfiedDependencyException` và original cause. `BeanCreationException` còn có thể giữ `relatedCauses` ngoài primary `getCause()` chain.

Không biến init callback thành remote network workflow dài và thiếu timeout. Các validation về local configuration thích hợp để fail fast; công việc remote hoặc data warm-up có lifecycle/coordinator/health policy riêng.

## 9. Project Java 17+ hoàn chỉnh: sáu kịch bản

Dự án dùng **Java 17+, Maven 3.9+, Spring Framework 6.2.18, JUnit Jupiter 5.11.4**. Không cần Spring Boot, database hay HTTP server. Mỗi scenario tạo **một context riêng** để không bị eager bean lỗi của case này phá case khác.

```text
lazy-bean-errors/
├── pom.xml
└── src/
    ├── main/java/edu/example/lazy/App.java
    └── test/java/edu/example/lazy/AppTest.java
```

### 9.1. pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>edu.example</groupId>
  <artifactId>lazy-bean-errors</artifactId>
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
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.11.4</version>
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
        <version>3.5.2</version>
      </plugin>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.5.0</version>
        <configuration>
          <mainClass>edu.example.lazy.App</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

### 9.2. src/main/java/edu/example/lazy/App.java

```java
package edu.example.lazy;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

public final class App {

    public static final class Faulty {
        public Faulty() {
            throw new IllegalStateException("intentional failure");
        }

        public String ping() {
            return "unreachable";
        }
    }

    public static final class Dependent {
        public Dependent(Faulty faulty) {
        }
    }

    public interface Port {
        String fetch();
    }

    public static final class BadPort implements Port {
        public BadPort() {
            throw new IllegalStateException("intentional failure");
        }

        @Override
        public String fetch() {
            return "unreachable";
        }
    }

    public static final class ProxyConsumer {
        private final Port port;

        public ProxyConsumer(Port port) {
            this.port = port;
        }

        public String use() {
            return port.fetch();
        }
    }

    public static final class ProviderConsumer {
        private final ObjectProvider<Faulty> provider;

        public ProviderConsumer(ObjectProvider<Faulty> provider) {
            this.provider = provider;
        }

        public String use() {
            return provider.getObject().ping();
        }
    }

    public static final class Probe {
        int constructions;
        public int constructions() {
            return constructions;
        }
    }

    public static final class Healthy {
        public Healthy(Probe probe) {
            probe.constructions++;
        }

        public String ping() {
            return "OK";
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class EagerConfig {
        @Bean
        public Faulty faulty() {
            return new Faulty();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class LazyConfig {
        @Bean
        @Lazy
        public Faulty faulty() {
            return new Faulty();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ForcedConfig {
        @Bean
        @Lazy
        public Faulty faulty() {
            return new Faulty();
        }

        @Bean
        public Dependent dependent(Faulty faulty) {
            return new Dependent(faulty);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ProxyConfig {
        @Bean
        @Lazy
        public Port port() {
            return new BadPort();
        }

        @Bean
        public ProxyConsumer proxyConsumer(@Lazy Port port) {
            return new ProxyConsumer(port);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ProviderConfig {
        @Bean
        @Lazy
        public Faulty faulty() {
            return new Faulty();
        }

        @Bean
        public ProviderConsumer providerConsumer(
                ObjectProvider<Faulty> provider) {
            return new ProviderConsumer(provider);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class HealthyConfig {
        @Bean
        public Probe probe() {
            return new Probe();
        }

        @Bean
        @Lazy
        public Healthy healthy(Probe probe) {
            return new Healthy(probe);
        }
    }

    public static AnnotationConfigApplicationContext context(
            Class<?> configuration) {
        AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext();
        ctx.register(configuration);
        return ctx;
    }

    public static String rootName(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }

    private static void eagerCase() {
        System.out.println("== eager ==");
        try (var ctx = context(EagerConfig.class)) {
            ctx.refresh();
            System.out.println("refresh=PASS");
        } catch (BeansException error) {
            System.out.println("refresh=FAIL");
            System.out.println("root=" + rootName(error));
        }
    }

    private static void lazyCase() {
        System.out.println("== lazy ==");
        try (var ctx = context(LazyConfig.class)) {
            ctx.refresh();
            System.out.println("refresh=PASS");
            System.out.println("definition=" +
                    ctx.containsBeanDefinition("faulty"));
            try {
                ctx.getBean(Faulty.class);
                System.out.println("lookup=PASS");
            } catch (BeansException error) {
                System.out.println("lookup=FAIL");
                System.out.println("root=" + rootName(error));
            }
        }
    }

    private static void forcedCase() {
        System.out.println("== forced ==");
        try (var ctx = context(ForcedConfig.class)) {
            ctx.refresh();
            System.out.println("refresh=PASS");
        } catch (BeansException error) {
            System.out.println("refresh=FAIL");
            System.out.println("root=" + rootName(error));
        }
    }

    private static void proxyCase() {
        System.out.println("== proxy ==");
        try (var ctx = context(ProxyConfig.class)) {
            ctx.refresh();
            System.out.println("refresh=PASS");
            try {
                ctx.getBean(ProxyConsumer.class).use();
                System.out.println("call=PASS");
            } catch (BeansException error) {
                System.out.println("call=FAIL");
                System.out.println("root=" + rootName(error));
            }
        }
    }

    private static void providerCase() {
        System.out.println("== provider ==");
        try (var ctx = context(ProviderConfig.class)) {
            ctx.refresh();
            System.out.println("refresh=PASS");
            try {
                ctx.getBean(ProviderConsumer.class).use();
                System.out.println("call=PASS");
            } catch (BeansException error) {
                System.out.println("call=FAIL");
                System.out.println("root=" + rootName(error));
            }
        }
    }

    private static void healthyCase() {
        System.out.println("== healthy lazy ==");
        try (var ctx = context(HealthyConfig.class)) {
            ctx.refresh();
            Probe probe = ctx.getBean(Probe.class);
            System.out.println("before=" + probe.constructions());
            Healthy first = ctx.getBean(Healthy.class);
            System.out.println("after=" + probe.constructions());
            Healthy second = ctx.getBean(Healthy.class);
            System.out.println("same=" + (first == second));
            System.out.println("after-second=" + probe.constructions());
        }
    }

    public static void main(String[] args) {
        eagerCase();
        lazyCase();
        forcedCase();
        proxyCase();
        providerCase();
        healthyCase();
        System.out.println("RESULT PASS");
    }
}
```

### 9.3. Lệnh chạy và stdout

```bash
mvn -q compile exec:java
```

**Các dòng do App tự in, không tính Maven/Spring logging:**

```text
== eager ==
refresh=FAIL
root=IllegalStateException
== lazy ==
refresh=PASS
definition=true
lookup=FAIL
root=IllegalStateException
== forced ==
refresh=FAIL
root=IllegalStateException
== proxy ==
refresh=PASS
call=FAIL
root=IllegalStateException
== provider ==
refresh=PASS
call=FAIL
root=IllegalStateException
== healthy lazy ==
before=0
after=1
same=true
after-second=1
RESULT PASS
```

Khi `refresh()` không thành công, Spring có thể ghi WARN/stacktrace riêng. Các assertions nên kiểm timing và exception type, **không so nguyên cả log console**.

### 9.4. Diễn giải từng trường hợp

**Eager:** `Faulty` không lazy; constructor ném trong `refresh`.

**Lazy:** bean definition đã đăng ký; refresh pass. `getBean(Faulty.class)` lần đầu kích hoạt constructor rồi fail.

**Forced:** eager `Dependent` nhận actual `Faulty`, nên buộc tạo lazy target trong refresh.

**Proxy:** eager `ProxyConsumer` nhận `@Lazy Port` proxy; refresh pass. `use()` gọi proxy và target `BadPort` fail.

**Provider:** eager consumer nhận `ObjectProvider<Faulty>`; refresh pass, `getObject()` bên trong `use()` fail.

**Healthy lazy:** `Probe` bắt đầu counter 0; first lookup tạo đúng một `Healthy`, second lookup dùng cùng singleton, counter không đổi.

## 10. JUnit Jupiter test theo failure phase

File `src/test/java/edu/example/lazy/AppTest.java`:

```java
package edu.example.lazy;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;

final class AppTest {

    @Test
    void eagerFailsAtRefresh() {
        try (var ctx = App.context(App.EagerConfig.class)) {
            BeansException error =
                    assertThrows(BeansException.class, ctx::refresh);
            assertEquals("IllegalStateException", App.rootName(error));
        }
    }

    @Test
    void lazyFailsAtFirstLookupNotRefresh() {
        try (var ctx = App.context(App.LazyConfig.class)) {
            assertDoesNotThrow(ctx::refresh);
            assertTrue(ctx.isActive());
            assertTrue(ctx.containsBeanDefinition("faulty"));
            BeansException error = assertThrows(BeansException.class,
                    () -> ctx.getBean(App.Faulty.class));
            assertEquals("IllegalStateException", App.rootName(error));
        }
    }

    @Test
    void eagerDependencyForcesLazyAtRefresh() {
        try (var ctx = App.context(App.ForcedConfig.class)) {
            BeansException error =
                    assertThrows(BeansException.class, ctx::refresh);
            assertEquals("IllegalStateException", App.rootName(error));
        }
    }

    @Test
    void proxyDefersUntilInvocation() {
        try (var ctx = App.context(App.ProxyConfig.class)) {
            assertDoesNotThrow(ctx::refresh);
            App.ProxyConsumer consumer = ctx.getBean(
                    App.ProxyConsumer.class);
            BeansException error =
                    assertThrows(BeansException.class, consumer::use);
            assertEquals("IllegalStateException", App.rootName(error));
        }
    }

    @Test
    void providerDefersUntilGetObject() {
        try (var ctx = App.context(App.ProviderConfig.class)) {
            assertDoesNotThrow(ctx::refresh);
            App.ProviderConsumer consumer = ctx.getBean(
                    App.ProviderConsumer.class);
            BeansException error =
                    assertThrows(BeansException.class, consumer::use);
            assertEquals("IllegalStateException", App.rootName(error));
        }
    }

    @Test
    void healthyLazySingletonCreatedOnce() {
        try (var ctx = App.context(App.HealthyConfig.class)) {
            assertDoesNotThrow(ctx::refresh);
            App.Probe probe = ctx.getBean(App.Probe.class);
            assertEquals(0, probe.constructions());
            App.Healthy a = ctx.getBean(App.Healthy.class);
            assertEquals(1, probe.constructions());
            App.Healthy b = ctx.getBean(App.Healthy.class);
            assertSame(a, b);
            assertEquals(1, probe.constructions());
        }
    }
}
```

```bash
mvn -q test
```

Kỳ vọng **6 tests, 0 failures, 0 errors, 0 skipped** trong Surefire report. Lệnh `-q` không nhất thiết in summary; xem `target/surefire-reports`.

Không dùng một context chứa tất cả configurations vì bean names có thể trùng và eager failure sẽ phá những kịch bản cần kiểm deferred lookup. Mỗi context được đóng độc lập bằng try-with-resources.

## 11. Chẩn đoán bằng exception chain thay vì message

`BeanCreationException` và `UnsatisfiedDependencyException` có thể bao quanh original `IllegalStateException`. Một failure khác có thể là `NoSuchBeanDefinitionException`, `NoUniqueBeanDefinitionException`, `BeanCurrentlyInCreationException` hoặc lỗi scope.

| Ngoại lệ | Gợi ý nguyên nhân | Có thể ở refresh hoặc lookup? |
| --- | --- | --- |
| `BeanCreationException` | Tạo hoặc init bean thất bại | Có |
| `BeanInstantiationException` | Constructor/factory method fail | Có |
| `UnsatisfiedDependencyException` | Dependency resolution fail | Có |
| `NoSuchBeanDefinitionException` | Không có candidate | Có, tùy thời điểm yêu cầu |
| `NoUniqueBeanDefinitionException` | Có nhiều candidates | Có |
| `BeanCurrentlyInCreationException` | Bean đang tạo bị yêu cầu lại | Có |
| `ScopeNotActiveException` | Request/session scope không active | Khi scope bị yêu cầu |
| `IllegalStateException` sâu trong cause | Logic validation/constructor tự throw | Có |

### Quy trình phân tích

1. Xác định stacktrace do `refresh()` hay do request/scheduled method/`getBean()`.
2. Ghi bean name và injection point trong outer exception.
3. Xem original cause và related causes nếu có.
4. Kiểm `BeanDefinition.isLazyInit()` và các eager consumers.
5. Kiểm `@Lazy` injection-point proxy/`ObjectProvider`.
6. Kiểm constructor, factory method, property injection, `@PostConstruct`.
7. Đối chiếu profile, condition, secret/env và resource thực tế.
8. Tái hiện bằng context tối giản rồi assert timing trong JUnit.

**Không dùng class name của exception để tự suy đó là refresh failure**: cùng exception thường xảy ra ở cả hai thời điểm.

## 12. Prototype, request scope và FactoryBean không giống lazy singleton

`@Scope("prototype")` vốn demand-created. `getBean` có thể tạo object mới mỗi lần, không được cache như singleton lazy. Nếu một eager singleton inject prototype trực tiếp, prototype có thể được tạo khi eager bean khởi tạo; muốn mỗi lần có prototype mới, dùng `ObjectProvider`/lookup thích hợp.

Request/session scoped bean cần active scope; nếu truy cập ngoài request, lỗi thường là scope context chứ không phải lazy constructor. Scoped proxies có thể hoãn lookup đến method invocation nhưng yêu cầu scope vẫn tồn tại.

`FactoryBean<T>` có product resolution và type prediction riêng; đôi khi introspection có thể instantiate factory hoặc product theo API/options. Đừng cho rằng `getBeansOfType` hoặc tất cả type lookup luôn vô hại đối với lazy state. Trong test muốn xác định instance creation, nên có một `Probe` đếm constructor invocation như demo.

## 13. Spring Boot global lazy và khác biệt với plain Spring Core

Trong Spring Boot, cấu hình:

```properties
spring.main.lazy-initialization=true
```

yêu cầu nhiều beans được tạo theo nhu cầu, giúp giảm startup time nhưng cũng dời lỗi cấu hình đến first use. Spring Boot documentation cảnh báo điều này và **không bật global lazy mặc định**. Có thể dùng:

```properties
spring.main.lazy-initialization=false
```

để giữ chính sách global thường dùng. `@Lazy(false)` có thể override lazy default tại những definitions thích hợp. Infrastructure hoặc eager dependencies vẫn có thể khiến các beans được tạo tại startup.

**Dự án mục 9 chỉ dùng Spring Framework, không dùng Boot:** chỉ thêm property Boot vào classpath không tự đổi behavior của `AnnotationConfigApplicationContext`. Thay vào đó, demo đánh `@Lazy` ở từng `@Bean` method để đo behavior một cách minh bạch.

## 14. @DependsOn và startup hooks có thể kích hoạt lazy bean

`@DependsOn("faulty")` trên eager bean buộc named dependency được initialized trước, kể cả nếu bean đó được đánh lazy. Tương tự, một `@PostConstruct` của eager bean gọi proxy/provider sẽ khiến target lookup xảy ra khi container đang startup.

```java
@Bean
@DependsOn("faulty")
public ReportService reportService() {
    return new ReportService();
}
```

Đây là method fragment, cần constructor no-arg của ReportService và annotation imports. `@DependsOn` **không phải** công cụ defer bean; nó yêu cầu tạo trước.

Ngoài constructor injection, lazy target còn có thể được kích hoạt bởi:
- `SmartInitializingSingleton` callback hoặc startup event listener.
- Spring Boot runner.
- Scheduler job lập tức chạy.
- Health indicator probing target.
- Một custom BeanPostProcessor gọi getBean quá sớm.
- Explicit lookup trong utility/service locator.

Khi lỗi xảy ra trước HTTP traffic, chưa chắc bean không lazy; có thể một startup hook đã gọi nó.

## 15. First-use latency, concurrency và readiness

Lazy bean nặng có thể khởi tạo chậm ở request đầu:
- First request chịu constructor, property binding, resource loading, proxy creation.
- Nhiều requests đồng thời có thể chờ singleton initialization.
- Nếu init lỗi, nhiều requests có thể fail hoặc timeout.
- Monitoring có thể báo startup thành công nhưng một feature chưa dùng bị hỏng.
- Readiness chỉ hữu ích nếu kiểm các dependencies **critical** theo policy của deployment.

Không dùng `@Lazy` để che secret thiếu, broken JDBC credentials hoặc vòng DI. Đối với critical dependencies, ưu tiên fail-fast hoặc warm-up có kiểm soát trước readiness. Đối với optional features, lazy kèm fallback, error classification, metrics và retry/circuit-breaker policy có thể hợp lý.

**Không warm-up tất cả beans một cách mù quáng.** Prototype, request scope và các beans có side effects cần policy khác. Nếu cần test concurrency, dùng `CountDownLatch`/`AtomicInteger` và bounded timeout; đừng dùng một static boolean không thread-safe hay `Thread.sleep` để đoán trạng thái.

## 16. Lazy có chữa vòng phụ thuộc không?

Xét vòng A(B), B(A):
- Đánh `@Lazy` trên bean definition A có thể làm refresh pass **nếu không có eager path nào cần A**.
- Khi `getBean(A)` thực sự chạy, constructor cycle vẫn còn và có thể ném `BeanCurrentlyInCreationException`.
- Đặt `@Lazy` trên một injection point có thể đưa lazy proxy vào graph và hoãn một edge, nhưng target vẫn phải resolve được lúc sử dụng.
- `ObjectProvider` cũng chỉ hoãn lookup, không sửa trách nhiệm nghiệp vụ giữa hai service.

Giải pháp bền vững là sửa dependency direction, đưa trách nhiệm chung sang bean C hoặc orchestrator, và test actual functionality chứ không chỉ context startup.

## 17. Ma trận kiểm chứng và checklist

| Kịch bản | Refresh | Lookup/call | Điều cần assert |
| --- | --- | --- | --- |
| Eager constructor lỗi | FAIL | Không tới | Error phase và root cause |
| Lazy không ai dùng trong startup | PASS | FAIL tại getBean | Definition tồn tại |
| Eager phụ thuộc lazy | FAIL | Không tới | Forced creation trong refresh |
| @Lazy injection proxy | PASS | FAIL tại method call | Consumer đã được tạo |
| ObjectProvider deferred | PASS | FAIL tại getObject | Provider được inject |
| Healthy lazy singleton | PASS | PASS | Counter 0 → 1, same instance |
| Prototype fail | Có thể PASS | FAIL khi được yêu cầu | Scope khác singleton |
| Lazy target chỉ lỗi ở @PostConstruct | PASS nếu chưa tạo | FAIL khi tạo | Init callback timing |
| Lazy feature trong Boot global mode | Có thể PASS | Tùy first use | Readiness/feature flow |

**Checklist production:**
- [ ] Xác định rõ refresh-time và first-use failures.
- [ ] Hiểu regular eager singletons được tạo trong refresh.
- [ ] Biết bean definition tồn tại không có nghĩa object đã được tạo.
- [ ] Không nhầm lazy singleton với prototype.
- [ ] Phân biệt `@Lazy` trên definition và injection point.
- [ ] Kiểm eager dependencies/`@DependsOn` làm forced creation.
- [ ] Không gọi provider/proxy trong constructor nếu mục tiêu là defer.
- [ ] Xác minh original causes và bean names thay vì so nguyên stacktrace.
- [ ] Test riêng constructor, factory method và initialization failures.
- [ ] Test Spring Boot global lazy theo config thật nếu ứng dụng dùng Boot.
- [ ] Test first-use paths của critical lazy features.
- [ ] Không dùng global lazy làm cách sửa circular dependencies.
- [ ] Đo first-request latency và concurrency khi startup time là mục tiêu.
- [ ] Có clear readiness policy cho critical dependencies.
- [ ] Khi cần fail-fast, validate config/resources trước khi nhận traffic.
- [ ] Khi cần optional lazy feature, có fallback và monitoring phù hợp.

## 18. Câu hỏi ôn tập và đáp án

1. Tại sao eager singleton constructor lỗi làm refresh fail? — Vì container tạo nó trong pre-instantiation.
2. Lazy BeanDefinition được đăng ký lúc nào? — Khi configuration được parse/registry được chuẩn bị.
3. Vì sao `containsBeanDefinition` true vẫn có thể getBean fail? — Metadata không chứng minh instantiation.
4. Lazy singleton có được cache sau khi tạo thành công không? — Có.
5. Lazy singleton và prototype khác gì? — Singleton dùng lại instance; prototype thường tạo mới theo lookup.
6. Tại sao eager consumer buộc lazy dependency tạo ở refresh? — Nó cần actual constructor/factory argument.
7. @Lazy trên injection point làm gì? — Có thể inject lazy-resolution proxy.
8. Lazy proxy target thường được resolve lúc nào? — Khi method invocation cần target.
9. `ObjectProvider` có tạo target lúc được inject không? — Không bắt buộc.
10. `getObject()` có thể ném NoUnique không? — Có nếu candidates ambiguous.
11. `@DependsOn` hoãn hay ép initialization? — Ép dependency khởi tạo trước.
12. `@PostConstruct` trên lazy bean chạy khi nào? — Khi bean thực sự được tạo.
13. Spring Boot có bật global lazy mặc định không? — Không.
14. Property Boot global lazy là gì? — `spring.main.lazy-initialization`.
15. Property Boot tự áp vào plain AnnotationConfigApplicationContext không? — Không.
16. Context active có bảo đảm mọi bean hoạt động không? — Không.
17. Lỗi xuất hiện ở HTTP request đầu luôn là lazy constructor error không? — Không, phải đọc root cause.
18. BeanCurrentlyInCreationException có thể bị dời tới first lookup không? — Có nếu graph lazy chưa tạo trong refresh.
19. Có nên warm-up mọi prototype/request bean trong startup không? — Không.
20. Test nào phân biệt eager và lazy failure? — Assert refresh fail với eager, refresh pass rồi getBean fail với lazy.
21. Trong demo, healthy counter trước lookup bằng bao nhiêu? — 0.
22. Sau first lookup healthy counter bằng bao nhiêu? — 1.
23. Second lookup healthy có tạo instance mới không? — Không.
24. ProxyConfig fail vào lúc nào? — Khi method invocation thực sự resolve target.
25. ProviderConfig fail vào lúc nào? — Khi `getObject()` trong consumer method.
26. Lợi ích fail-fast với critical config là gì? — Phát hiện lỗi trước khi nhận traffic.
27. Khi nào lazy đáng dùng? — Dependency tốn chi phí/optional, có first-use monitoring và fallback.
28. Vì sao không assert nguyên Spring stacktrace? — Wrapping/format thay đổi giữa versions.

## 19. Tài liệu chính thức

- Spring Framework 6.2 Reference — Lazy-initialized Beans: https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-lazy-init.html
- Spring Framework 6.2 Reference — Using @Bean: https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html
- Spring Framework 6.2 Reference — Bean Lifecycle: https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html
- Spring Framework 6.2 Reference — Using depends-on: https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-dependson.html
- Spring Framework 6.2 Javadoc — @Lazy: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Lazy.html
- Spring Framework 6.2 Javadoc — ObjectProvider: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/ObjectProvider.html
- Spring Framework 6.2 Javadoc — BeanCreationException: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/BeanCreationException.html
- Spring Boot Reference — SpringApplication Lazy Initialization: https://docs.spring.io/spring-boot/reference/features/spring-application.html
- JUnit 5 User Guide: https://junit.org/junit5/docs/current/user-guide/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="troubleshooting-010">Quan sát log container và rút gọn cấu hình để tái hiện</a>

<details>
<summary>Xem chi tiết</summary>

# Quan sát log container và rút gọn cấu hình để tái hiện

## 1. Mục tiêu

Khi Spring Core Container không khởi động được, điều khó không phải chỉ là biết nó ném `BeanCreationException`. Điều khó là tìm đúng **quyết định đăng ký bean, phụ thuộc và điều kiện môi trường nào** đã dẫn đến lỗi. Một ứng dụng thật có hàng trăm definitions, nhiều `@Configuration` classes, component scanning, profiles, custom `BeanFactoryPostProcessor` và các integration dependencies; ghi toàn bộ log ở `TRACE` có thể khiến vấn đề càng khó thấy.

Cách làm có hệ thống gồm hai phần bổ sung nhau:

1. **Quan sát có chủ đích** các log container và chuỗi `Caused by` để xác định thất bại xảy ra trong bước nào của vòng đời.
2. **Rút gọn cấu hình tái hiện** (minimal reproducible example, MRE) sao cho vẫn giữ đúng lỗi; chứng minh bằng hai test: cấu hình **lỗi nhỏ nhất** thất bại và cấu hình **đã sửa một nguyên nhân** khởi động thành công.

Bài học này dùng Spring Framework 6.2.18 + Java 17 + Gradle + JUnit Jupiter. Chương trình chính tạo từng `AnnotationConfigApplicationContext` độc lập để thử các trường hợp: cấu hình đúng, thiếu dependency, nhiều candidates, sửa bằng `@Qualifier`, và provider bị loại bởi `@Profile`.

Bạn sẽ phân biệt:

- Log **đăng ký BeanDefinition** với log **khởi tạo instance**.
- `DEBUG`/`TRACE` logger categories có chọn lọc với việc bật tất cả logging.
- Cause ngoài cùng với nguyên nhân sâu trong `BeansException`.
- Spring Framework logging thông qua `spring-jcl` với Spring Boot `--debug` và Conditions Evaluation Report.
- Khi nào dùng `@Import`, `@Bean`, `@ComponentScan` và khi nào một **test configuration tường minh** là lựa chọn tốt hơn.
- Cách chứng minh một rút gọn vẫn giữ **cùng failure mechanism**, không chỉ cùng dòng chữ “Application failed”.
- Giới hạn: profile, environment, order, classpath, proxies, parent contexts, async/stateful external infrastructure.

## 2. Container làm gì khi refresh?

`ApplicationContext` đọc cấu hình, đăng ký bean definitions, giải quyết phụ thuộc và tạo singleton instances trong quá trình `refresh()`. Theo Spring Reference, `DefaultListableBeanFactory` là một delegate chính của những context triển khai phổ biến.

Một sơ đồ rút gọn:

```text
Create AnnotationConfigApplicationContext
          |
          v
register(Configuration.class)
          |
          v
refresh()
   |
   +-- Parse configuration / @Import / component scan
   +-- Evaluate @Profile / @Conditional
   +-- Register and modify BeanDefinitions
   +-- Register BeanPostProcessors
   +-- Instantiate non-lazy singletons
   |      |
   |      +-- Resolve constructor/factory method arguments
   |      +-- Populate properties / @Autowired
   |      +-- Apply BeanPostProcessors
   |      +-- Initialize beans
   |
   +-- Finish refresh and publish context event
          |
          v
Working ApplicationContext
```

Điểm lỗi quan trọng:

| Giai đoạn | Triệu chứng thường gặp | Hướng quan sát |
| --- | --- | --- |
| Tìm configuration | Không scan/import bean class | Kiểm tra source registration, scan base packages, `@Import` |
| Đánh giá điều kiện | Bean bị loại bởi profile/condition | In active profiles, nguồn property, cấu hình condition |
| Đăng ký definitions | Duplicate name / invalid metadata | Logger `org.springframework.context.annotation`, registry metadata |
| Type resolution | Thiếu hoặc thừa candidates | `NoSuchBeanDefinitionException` / `NoUniqueBeanDefinitionException` |
| Tạo instance | Constructor/factory method ném lỗi | Đi theo nested causes, không chỉ outer bean name |
| Inject field/method | `UnsatisfiedDependencyException` | Xem `InjectionPoint` và collaborator cần thiết |
| Bean post-processing | Proxy/validation/configuration error | Kiểm tra BPP và kiểu object được expose |
| Initialization | `@PostConstruct` / `afterPropertiesSet` fail | Xem callback đầu tiên ném exception |
| Refresh hoàn tất | Events/lifecycle/listener error | Khoanh vùng listener và sự kiện |

Đây là bảng định hướng điều tra, **không** khẳng định một exception class chỉ có thể phát sinh ở một giai đoạn.

## 3. “Log container” chính xác là log nào?

Cần phân biệt ba loại thông tin:

- **Framework logs:** Messages từ các components như bean factory, configuration class parser, annotation processors, context lifecycle. Chúng đi qua cơ chế logging của Spring.
- **Exception stack trace:** Chuỗi lỗi Java gồm exception type, bean names, resource descriptions, injection points và `Caused by`. Đôi khi hữu ích hơn một trăm dòng DEBUG.
- **Diagnostic snapshots có cấu trúc:** BeanDefinition names, active profiles, candidate names và object type lấy bằng API có kiểm soát, được ghi trước hoặc sau một giai đoạn thích hợp.

Không phải lúc nào `DEBUG` cũng in mọi bean đã đăng ký. Framework logger categories và wording thay đổi theo phiên bản, nên phải xem log thật trên ứng dụng hiện tại. Một công cụ chẩn đoán tốt ghi cả **bằng chứng cấu hình** và **exception chain**, không dựa vào một câu log cố định.

## 4. Spring JCL và logging backend

Spring Framework sử dụng `spring-jcl` để bridge logging infrastructure. Theo Spring Reference, `spring-jcl` tự phát hiện logging APIs hiện có (như SLF4J/Log4j) và có thể fallback sang Java Util Logging tùy classpath.

Điều đó dẫn tới ba nhận xét:

1. Spring Framework thuần không bắt buộc có Logback để khởi động.
2. Muốn điều chỉnh log chính xác, hãy chọn một logging backend phù hợp và cấu hình nó: Logback, Log4j 2 hoặc JUL.
3. Không nên đưa nhiều bridge/provider mâu thuẫn vào classpath, vì khi đó bạn có thể thấy warnings, duplicate logging hoặc cấu hình không được đọc như mong đợi.

Trong ví dụ runnable, chúng ta chọn `logback-classic` làm SLF4J backend. File `src/main/resources/logback.xml` điều chỉnh logger levels. **Đây là lựa chọn của demo, không phải yêu cầu bắt buộc của mọi Spring app.**

## 5. Logger categories thường hữu ích

| Logger category | Gợi ý xem gì |
| --- | --- |
| `org.springframework.beans.factory.support` | BeanFactory definitions, singleton creation/resolution |
| `org.springframework.beans.factory.annotation` | Annotation-based dependency injection processing |
| `org.springframework.context.annotation` | Java configuration parsing, imports, scanning |
| `org.springframework.context.support` | Context initialization, lifecycle events |
| `org.springframework.core.env` | Property/environment resolution diagnostics trong một số code paths |
| `org.springframework.test.context.cache` | Context caching trong TestContext-based tests |

Một ứng dụng dùng Spring 6.2 không có nghĩa tất cả logger categories trên phát ra cùng một tập thông điệp mỗi lần chạy. Hãy bật đúng nơi có chứng cứ liên quan.

Quy trình hợp lý:
1. Giữ root ở `WARN` hoặc `ERROR`.
2. Nâng `DEBUG` cho **một** package cần điều tra.
3. Chỉ nâng `TRACE` trong lúc hẹp, khi DEBUG chưa đủ.
4. Lưu output, xác định tên bean/giai đoạn.
5. Trả mức log về cấu hình bình thường sau khi tìm ra lỗi.

Một trace toàn container có thể làm lộ đường dẫn hệ thống, tên cấu hình hoặc metadata nhạy cảm. Khi chia sẻ log, lọc secrets/tokens/passwords và thông tin người dùng.

## 6. Ví dụ Logback có chọn lọc

```xml
<configuration>
    <appender name="CONSOLE"
              class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>LOG %-5level %logger{48} - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="org.springframework.beans.factory.support"
            level="DEBUG"/>
    <logger name="org.springframework.context.annotation"
            level="DEBUG"/>

    <root level="WARN">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

Đây là một `logback.xml` hoàn chỉnh cho demo (nếu có Logback runtime dependency). Các log từ hai packages chỉ định có thể xuất hiện với prefix `LOG`. Vì Java application `System.out` không dùng mẫu của Logback, những dòng do chương trình in bắt đầu bằng `case=` sẽ dễ lọc.

Không dùng unit test assertions so khớp toàn bộ DEBUG output, vì:
- Spring patch versions có thể đổi câu chữ.
- Thứ tự log có thể khác khi thay imports/scan/profiles.
- Các frameworks khác có thể phát log cùng thời điểm.
- Output capture của Gradle/IDE có thể thêm prefix hoặc thay encoding.

Thay vào đó, test exception **types**, bean presence và runtime behavior.

## 7. DEBUG khác TRACE và khác stack trace

Mức độ chi tiết thông thường:

| Level | Cách dùng |
| --- | --- |
| ERROR | Lỗi khiến thao tác không tiếp tục |
| WARN | Điều kiện bất thường nhưng có thể chạy |
| INFO | Startup milestones / trạng thái được lựa chọn |
| DEBUG | Diễn biến đăng ký/tạo beans và cấu hình ở mức tương đối chi tiết |
| TRACE | Đường đi nội bộ rất chi tiết ở logger hỗ trợ |

**Không đánh đồng TRACE level với Java stack trace.** Một exception stack trace gồm các stack frames và `Caused by`; nó có thể được log ở ERROR, WARN hoặc DEBUG tùy application logger. Mức TRACE chỉ nói mức cho phép của logging backend.

Một exception từ `context.refresh()` được catch ở `main` mà không tự log sẽ không nhất thiết có full trace dù DEBUG đang bật.

## 8. Điều kiện cần để log xuất hiện

Một file `logback.xml` tốt không đủ nếu:

- Logback không có trên runtime classpath.
- SLF4J binding/provider bị xung đột.
- Ứng dụng đang dùng backend khác, đọc cấu hình khác.
- File không nằm trên classpath, đặt sai thư mục.
- Spring code path hiện tại không phát log ở logger/level mà bạn đã bật.
- Gradle/IDE redirect output và bạn quan sát nhầm process.
- Các package names hoặc initialization paths khác phiên bản docs.

Với Gradle Java project, để file ở:

```text
src/main/resources/logback.xml
```

Rồi chạy JVM process thật để kiểm tra output. Nếu cần chứng minh logging implementation, thử phát một application-owned SLF4J log với logger đơn giản, thay vì suy từ việc có dependency trong Gradle file.

## 9. Một bước quan trọng: lưu lỗi nguyên vẹn trước khi rút gọn

Đầu tiên giữ lại:

- Full stack trace, kể cả các `Caused by` và suppressed/related causes khi có.
- Spring Framework/Boot version chính xác.
- JDK/Gradle/Maven versions.
- Profiles và các property source quyết định bean graph.
- Command line, JVM args, environment variables cần thiết (đã loại secrets).
- Runtime classpath/dependency versions, đặc biệt các starter/transitive dependencies.
- Configuration entry point và package boundaries.
- Exception name, bean name, injection point và direct cause.
- Hành vi mong đợi vs hành vi thực tế.

Một bản ghi tốt có dạng:

```text
EXPECTED:
  AnnotationConfigApplicationContext refresh succeeds.

ACTUAL:
  Bean 'invoiceService' fails during factory method argument resolution.
  Outer exception: UnsatisfiedDependencyException.
  Nested cause: NoSuchBeanDefinitionException (InvoiceGateway).

ENV:
  Java 17; Spring Framework 6.2.18; profile=test.
  Config: InvoiceConfig.class.
  Runtime classpath: spring-context and dependencies.
```

Đây là **mẫu minh họa**, không phải nội dung được lấy từ ứng dụng thực tế. Cách ghi này làm rõ một lỗi cấu hình, tránh chỉ gửi screenshot không có nguyên nhân.

## 10. Đọc nguyên nhân: từ ngoài vào trong

Một lỗi Spring điển hình:

```text
BeanCreationException: Error creating bean 'apiHandler'
  Caused by: UnsatisfiedDependencyException: 'invoiceService'
    Caused by: NoSuchBeanDefinitionException: InvoiceGateway
```

Trong ví dụ này:
- `apiHandler` là nơi lỗi lan tới.
- `invoiceService` là nơi một dependency không được thỏa mãn.
- `InvoiceGateway` là type mà BeanFactory không tìm thấy candidate thích hợp.

Đừng chỉ thêm annotation vào `apiHandler`. Hãy kiểm tra trực tiếp vì sao `InvoiceGateway` không được đăng ký/không ở cùng context/không khớp profile/generic qualifiers.

Sửa “bean đầu tiên trong log” có thể khiến lỗi tiếp tục xuất hiện ở nơi khác vì nó không giải quyết nguyên nhân gốc.

## 11. Chẩn đoán bằng public exception APIs

Khi một context fail và bạn đang viết repro program, có thể đi theo chuỗi `Throwable.getCause()`:

```java
public static boolean hasCause(
        Throwable failure, Class<? extends Throwable> expected) {

    for (Throwable current = failure;
         current != null; current = current.getCause()) {

        if (expected.isInstance(current)) {
            return true;
        }
    }
    return false;
}
```

Đây là phương thức Java minh họa (được dùng trong chương trình đầy đủ ở dưới).

Đừng dùng `error.getMessage().contains("Unsatisfied")` làm test quan trọng: văn bản exception không phải API ổn định cho exact-string snapshots.

Với `UnsatisfiedDependencyException`, `getInjectionPoint()` có thể giúp xác định field/method parameter đang thiếu, nhưng method này **có thể trả null**. Cần kiểm tra null trước khi in. Với `BeanCreationException`, xem `getBeanName()` và `getMostSpecificCause()` khi thích hợp.

## 12. Khi nào lỗi là thiếu bean?

Một `@Bean` method:

```java
@Bean
InvoiceService invoiceService(InvoiceGateway gateway) {
    return new InvoiceService(gateway);
}
```

Sẽ thất bại nếu context không cung cấp bean tương thích với `InvoiceGateway`. Nhưng “tại sao thiếu” có nhiều nguyên nhân:

1. Chưa viết/register provider bean.
2. Provider class có `@Component` nhưng không nằm trong `@ComponentScan`.
3. `@Import` thiếu configuration class.
4. Provider bị `@Profile` hoặc `@Conditional` loại.
5. Injection point yêu cầu generic type khác provider.
6. Bean thuộc sibling/child ApplicationContext không visible.
7. Proxy object expose interface mà consumer yêu cầu concrete type.
8. Custom qualifier/autowire candidate settings lọc hết candidates.

Một bản tái hiện tốt **giữ đúng nguyên nhân**. Nếu production thiếu bean do profile, MRE chỉ đơn giản xóa provider definition có thể tạo **cùng NoSuchBeanDefinitionException** nhưng không chứng minh profile là nguyên nhân.

## 13. Khi nào lỗi là quá nhiều bean?

Nếu có hai providers:

```java
@Bean("northGateway")
InvoiceGateway northGateway() {
    return () -> "north";
}

@Bean("southGateway")
InvoiceGateway southGateway() {
    return () -> "south";
}
```

Và một `@Bean` method chỉ nhận `InvoiceGateway`, container có thể không chọn được duy nhất và ném `NoUniqueBeanDefinitionException` dưới một `UnsatisfiedDependencyException`.

Cần kiểm tra:
- `@Primary` hay `@Fallback` có tác động không.
- Có `@Qualifier` nào tại injection point không.
- Parameter name có match bean name không.
- Type generics và priority/default candidate semantics.
- Cả hai candidates có thật trong **cùng** context hierarchy không.

Một bước sửa nhỏ, chứng minh được là thêm `@Qualifier("northGateway")` đúng injection point thay vì vô cớ xóa tất cả provider thứ hai nếu nghiệp vụ cần cả hai.

## 14. Vì sao không gọi getBeansOfType để “in thử”?

`getBeansOfType(T)` trả **instances** chứ không chỉ tên. Nếu chạy trong bootstrap, nó có thể tạo lazy singleton/prototype hoặc FactoryBean products, gây side effects hoặc tạo lỗi mới, làm mất khả năng tái hiện đúng lỗi ban đầu.

Với `ListableBeanFactory`, ưu tiên:

```java
String[] names = factory.getBeanNamesForType(
        InvoiceGateway.class, true, false);
```

Trong đó:
- `includeNonSingletons=true` cho phép tính cả prototype candidates.
- `allowEagerInit=false` hạn chế eager initialization phục vụ type discovery.
- Method trả `String[]`, không instantiate tất cả matching beans chỉ để đưa vào một `Map`.

Tuy nhiên, kết quả non-eager có thể **bỏ qua FactoryBean products chưa có type metadata**. Nó là công cụ quan sát, không phải bằng chứng tuyệt đối rằng một bean dynamic không tồn tại.

## 15. Quan sát metadata theo thứ tự ít side effect

Khi cần diagnostics trong `BeanFactoryPostProcessor` hoặc một tool khởi tạo trước full refresh:

1. `getBeanDefinitionNames()` — liệt kê local definitions.
2. `containsBeanDefinition(name)` — kiểm tra một definition local.
3. `getBeanDefinition(name)` — đọc class/scope/lazy/qualifier metadata.
4. `getBeanNamesForType(type,true,false)` — liệt kê type candidates non-eager khi metadata cho phép.
5. `getType(name,false)` — dự đoán type, có thể null nếu chưa biết.
6. Chỉ gọi `getBean(name)` **sau khi đã chọn một bean để test thật**.

Một output hữu ích in sorted names và scopes. Đừng dựa vào thứ tự iteration không được bảo đảm của maps hoặc nội bộ bean registry.

Giới hạn: definitions tại **thời điểm bạn đọc** có thể chưa đầy đủ nếu configuration processors còn đang chạy. Ví dụ đọc trước `ConfigurationClassPostProcessor` sẽ không thấy các `@Bean` methods chưa được parse thành definitions. Chọn giai đoạn quan sát phù hợp.

## 16. Cách rút gọn cấu hình đúng: giữ hiện tượng, cắt nhiễu

Mục tiêu của MRE là giảm số biến nhưng giữ đúng điều kiện gây lỗi:

```text
Production application
    |
    v
Record failure + complete cause chain
    |
    v
Fix versions and runtime environment
    |
    v
Identify failing bean + dependency edge
    |
    v
Remove unrelated controllers/features/imports
    |
    v
Replace external DB/HTTP/queue with safe fake
    |
    v
Keep only config/profiles/properties that influence failure
    |
    v
Re-run: does SAME failure mechanism persist?
    |
    +-- NO: restore last removed relevant part
    |
    +-- YES: remove another unrelated part
    |
    v
Minimal failing configuration + minimal repaired configuration
```

Không chỉ rút gọn code bằng cách bỏ random files. Nếu MRE chuyển từ **multiple candidates** thành **no candidates** thì nguyên nhân đã thay đổi; quá trình thu nhỏ đó không còn giữ failure mechanism.

Một MRE tốt có cả **negative assertion** (đúng exception) và **positive assertion** (cùng context sau thay đổi nhỏ nhất khởi động).

## 17. “Một thay đổi mỗi lượt” thay vì đổi nhiều thứ

Một vòng giảm cấu hình:

1. Lưu baseline lỗi `F0`.
2. Loại hoặc disable đúng **một nhóm** cấu hình độc lập.
3. Chạy lại test/command.
4. Nếu cùng loại lỗi gốc vẫn xảy ra, giữ giảm đó.
5. Nếu lỗi biến mất hoặc chuyển cơ chế, restore phần đó.
6. Tiếp tục tới khi những thành phần còn lại đều có lý do tồn tại.

Ví dụ:

| Lượt | Thay đổi | Kết quả | Quyết định |
| --- | --- | --- | --- |
| 0 | Full application | Missing `InvoiceGateway` | Baseline |
| 1 | Bỏ mail sender config | Vẫn missing gateway | Giữ giảm |
| 2 | Bỏ security filter config | Vẫn missing gateway | Giữ giảm |
| 3 | Bỏ `@Profile("prod")` khỏi gateway | Context chạy được | Restore annotation: profile là nguyên nhân |
| 4 | Chỉ giữ gateway + consumer + profile test | Cùng missing gateway | MRE hoàn tất |
| 5 | Đổi profile sang prod | Context chạy được | Positive control |

Đây là ví dụ **quy trình**, không phải một phép biến đổi thực hiện trên dự án cụ thể nào.

Với config graph lớn có thể dùng binary search/delta debugging trên nhóm imports. Dù giảm theo nhóm, mỗi lần thử vẫn cần xác nhận **cùng root cause** và rollback một nhóm nếu kết quả đổi.

## 18. Những gì được phép thay trong MRE

Có thể thay thế an toàn những phần không liên quan:
- HTTP client bằng fake trả giá trị cố định.
- DataSource bằng in-memory fake/repository khi lỗi không liên quan DB configuration.
- Queue/clock/filesystem bằng stub có contract tương đương.
- Background jobs, schedulers, event listeners không liên quan bằng implementation rỗng.
- Component scanning rộng bằng **explicit `@Import`** hoặc `@Bean` definitions cần thiết.
- Spring Boot application context lớn bằng `AnnotationConfigApplicationContext` **nếu cơ chế lỗi vẫn còn**.

Không nên thay:
- Bean type/generic qualifiers đang nằm ở injection point lỗi.
- `@Profile` hoặc `@Conditional` nghi là nguyên nhân chính.
- Constructor/factory method signature đã tạo failure.
- Bean selection ambiguity bằng cách xóa một candidate mà chưa ghi nhận.
- Proxy strategy nếu lỗi là type mismatch do proxy.
- Parent-child context hierarchy nếu visibility là điều đang điều tra.

**Giảm cấu hình không được bóp méo chẩn đoán.**

## 19. Test profile và Environment phải được đặt trước refresh

Ví dụ:

```java
AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext();

context.getEnvironment().setActiveProfiles("test");
context.register(FeatureConfig.class);
context.refresh();
```

Đây là mảnh Java minh họa, phải đóng context sau khi dùng.

`@Profile("prod")` thường loại provider khỏi bean graph khi profile active là `test`. Việc gọi `setActiveProfiles("prod")` **sau** `refresh()` không tự chạy lại configuration class parsing hay đăng ký definitions đã bị loại.

Để tái hiện lỗi profile phải giữ:
- Active profiles lúc bootstrap.
- Property sources liên quan.
- Relevant `@Profile`/`@Conditional` declarations.
- Đúng context registration sequence.

Nếu bạn chỉ chạy `new AnnotationConfigApplicationContext(FeatureConfig.class)`, context tự refresh ngay. Khi muốn cấu hình Environment trước refresh, dùng constructor rỗng + `register` + `refresh` tường minh như ví dụ trên.

## 20. Khi nào thu nhỏ bằng context thuần là không đủ?

Không dùng Spring Framework thuần để thay thế MRE nếu lỗi cần Boot-only behavior:

- `@ConditionalOnProperty`, `@ConditionalOnClass`, `@ConditionalOnMissingBean` là các annotations thuộc Boot auto-configuration.
- Boot condition evaluation, configuration properties binding và starter-based classpath decisions có thể là chính nguyên nhân.
- Web server auto-configuration hoặc test slices cần Boot context behavior.
- Native/AOT Boot integration hoặc actuator endpoint cụ thể không thể suy chỉ từ `spring-context`.

Khi lỗi phụ thuộc Boot auto-configuration, hãy giữ một **minimal Boot application** và dùng công cụ như `ApplicationContextRunner`/`ConditionEvaluationReport` cho bài test thích hợp.

Ngược lại, khi lỗi chỉ là constructor injection thiếu bean do `@Bean` registration, một `AnnotationConfigApplicationContext` nhỏ thường là repro nhanh, rõ và ít nhiễu hơn.

## 21. Spring Boot --debug không phải công tắc Spring Framework thuần

Theo Spring Boot Reference, chạy Boot app với:

```bash
java -jar my-app.jar --debug
```

có thể bật DEBUG logs cho một số core loggers và in **Conditions Evaluation Report** để giải thích auto-configuration decisions. Một ứng dụng thuần `AnnotationConfigApplicationContext` không tự có behavior này chỉ vì bạn thêm chuỗi `--debug` vào args.

Với Boot có Actuator, endpoint `/actuator/conditions` cũng có thể hiển thị report nếu được cấu hình/expose đúng cách. Điều này thuộc Boot infrastructure, không phải một REST endpoint mặc định của `spring-context`.

Khi chia sẻ report/log, tránh lộ credentials hoặc kết cấu nội bộ nhạy cảm; đừng expose actuator endpoints công khai chỉ phục vụ debug.

## 22. Cấu trúc dự án minh họa có thể chạy

Bài dùng Gradle Java 17, Spring Framework 6.2.18 và JUnit Jupiter 5.11.4. Logback là logging backend để quan sát framework logs.

```text
spring-container-repro/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/
    │   ├── java/demo/ContainerRepro.java
    │   └── resources/logback.xml
    └── test/
        └── java/demo/ContainerReproTest.java
```

Các ca được chạy bằng một process Java và mỗi ca có context độc lập. Điều này tránh ảnh hưởng của singleton cache, failed refresh hay state từ ca trước.

Chương trình in **sáu dòng `case=...` dự đoán được**. Các dòng `LOG ...` là output hạ tầng bổ sung, không dùng làm exact snapshot. Unit tests kiểm tra result codes và behavior.

## 23. settings.gradle

```groovy
rootProject.name = 'spring-container-repro'
```

## 24. build.gradle

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.18'
    implementation 'ch.qos.logback:logback-classic:1.5.18'

    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.ContainerRepro'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Đây là một project Gradle độc lập; dùng JDK 17+ và Gradle 8+ để chạy. Spring Framework dependencies được Maven Central resolve. `spring-context` kéo `spring-beans`, `spring-core`, `spring-jcl` và những dependencies lõi phù hợp.

Logback backend được khai báo trực tiếp; ví dụ không dùng Spring Boot starter hoặc annotation Boot-only.

## 25. src/main/resources/logback.xml

```xml
<configuration>
    <appender name="CONSOLE"
              class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>LOG %-5level %logger{45} - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="org.springframework.beans.factory.support"
            level="DEBUG"/>
    <logger name="org.springframework.context.annotation"
            level="DEBUG"/>

    <root level="WARN">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

Bạn có thể tạm hạ root xuống `ERROR` hoặc bỏ một logger nếu quá nhiều dòng. Để quan sát lifecycle sớm hơn, bật thêm `org.springframework.context.support` ở DEBUG, nhưng luôn tăng dần có chủ đích.

File logback.xml được Gradle đưa vào runtime classpath khi ở `src/main/resources`. `Gradle --quiet` chỉ giảm log Gradle, **không ngăn chính JVM Spring/Logback phát DEBUG**.

## 26. src/main/java/demo/ContainerRepro.java

```java
package demo;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

public final class ContainerRepro {

    private ContainerRepro() {
    }

    public interface InvoiceGateway {
        String code();
    }

    public record InvoiceService(InvoiceGateway gateway) {
        public String issue() {
            return "INV:" + gateway.code();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class WorkingConfig {

        @Bean
        InvoiceGateway gateway() {
            return () -> "ok";
        }

        @Bean
        InvoiceService invoiceService(InvoiceGateway gateway) {
            return new InvoiceService(gateway);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class MissingConfig {

        @Bean
        InvoiceService invoiceService(InvoiceGateway gateway) {
            return new InvoiceService(gateway);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AmbiguousConfig {

        @Bean("northGateway")
        InvoiceGateway northGateway() {
            return () -> "north";
        }

        @Bean("southGateway")
        InvoiceGateway southGateway() {
            return () -> "south";
        }

        @Bean
        InvoiceService invoiceService(InvoiceGateway gateway) {
            return new InvoiceService(gateway);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class RepairedConfig {

        @Bean("northGateway")
        InvoiceGateway northGateway() {
            return () -> "north";
        }

        @Bean("southGateway")
        InvoiceGateway southGateway() {
            return () -> "south";
        }

        @Bean
        InvoiceService invoiceService(
                @Qualifier("northGateway") InvoiceGateway gateway) {
            return new InvoiceService(gateway);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ProfileConfig {

        @Bean
        @Profile("prod")
        InvoiceGateway gateway() {
            return () -> "prod";
        }

        @Bean
        InvoiceService invoiceService(InvoiceGateway gateway) {
            return new InvoiceService(gateway);
        }
    }

    public record Observation(
            String status,
            String detail,
            boolean unsatisfied) {
    }

    private static boolean hasCause(
            Throwable error, Class<? extends Throwable> type) {

        for (Throwable item = error;
             item != null;
             item = item.getCause()) {

            if (type.isInstance(item)) {
                return true;
            }
        }
        return false;
    }

    public static Observation inspect(
            Class<?> configType, String profile) {

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            context.getEnvironment().setActiveProfiles(profile);
            context.register(configType);

            try {
                context.refresh();

                InvoiceService service =
                        context.getBean(InvoiceService.class);

                return new Observation(
                        "STARTED", service.issue(), false);

            } catch (BeansException failure) {
                String reason;

                if (hasCause(
                        failure, NoUniqueBeanDefinitionException.class)) {
                    // NoUnique is a subclass of NoSuchBeanDefinition.
                    // Its check must come first.
                    reason = "MULTIPLE_CANDIDATES";

                } else if (hasCause(
                        failure, NoSuchBeanDefinitionException.class)) {
                    reason = "NO_CANDIDATE";

                } else {
                    reason = "OTHER_BEAN_ERROR";
                }

                boolean injectionFailed = hasCause(
                        failure, UnsatisfiedDependencyException.class);

                return new Observation(
                        "FAILED", reason, injectionFailed);
            }
        }
    }

    private static void show(
            String label, Class<?> configType, String profile) {

        Observation result = inspect(configType, profile);

        System.out.println("case=" + label
                + " status=" + result.status()
                + " detail=" + result.detail()
                + " unsatisfied=" + result.unsatisfied());
    }

    public static void main(String[] args) {

        show("ok", WorkingConfig.class, "test");
        show("missing", MissingConfig.class, "test");
        show("ambiguous", AmbiguousConfig.class, "test");
        show("fixed", RepairedConfig.class, "test");
        show("profile-test", ProfileConfig.class, "test");
        show("profile-prod", ProfileConfig.class, "prod");
    }
}
```

### Vì sao đoạn này dễ kiểm tra?

- Không dùng component scanning: registration chỉ gồm **một `@Configuration`** trong mỗi ca.
- Không dùng Spring Boot auto-configuration.
- Không dùng database/network/threads/randomness.
- `@Bean` return types tường minh và `InvoiceGateway` là functional interface hợp lệ.
- `MissingConfig` không có `InvoiceGateway`; lỗi được phát hiện ở factory method parameter.
- `AmbiguousConfig` có đúng hai candidates và không có primary/qualifier.
- `RepairedConfig` khác ở `@Qualifier("northGateway")`.
- `ProfileConfig` được thử dưới hai profiles trước `refresh()`.
- `NoUniqueBeanDefinitionException` là subclass của `NoSuchBeanDefinitionException`; code phân loại **NoUnique trước** để tránh nhầm “multiple candidates” thành “no candidate”.
- Kết quả trả về có status/detail cố định, nên kiểm thử không phụ thuộc wording nội bộ của Spring.

Chương trình cố ý bắt `BeansException` **trong thử nghiệm** để phân loại failure. Trong ứng dụng production, không nên nuốt lỗi startup rồi tiếp tục phục vụ requests trên context chưa refresh thành công.

## 27. Chạy demo và đọc kết quả

```bash
gradle run --quiet
```

Những dòng **do chương trình `System.out.println` in** dự kiến là:

```text
case=ok status=STARTED detail=INV:ok unsatisfied=false
case=missing status=FAILED detail=NO_CANDIDATE unsatisfied=true
case=ambiguous status=FAILED detail=MULTIPLE_CANDIDATES unsatisfied=true
case=fixed status=STARTED detail=INV:north unsatisfied=false
case=profile-test status=FAILED detail=NO_CANDIDATE unsatisfied=true
case=profile-prod status=STARTED detail=INV:prod unsatisfied=false
```

Do có `logback.xml` bật DEBUG cho bean factory và configuration package, console thực tế **có thể chứa thêm** nhiều dòng `LOG DEBUG ...`. Sáu dòng `case=` ở trên là phần ổn định cần đối chiếu, **không phải toàn bộ stdout được bảo đảm giống hệt** khi bật logging.

Nếu muốn dễ xem kết quả trên Unix-like shell:

```bash
gradle run --quiet | grep '^case='
```

Lưu ý Gradle/logging có thể dùng stdout/stderr khác nhau; lệnh lọc chỉ dùng khi log và chương trình xuất đúng stream phù hợp. Trên PowerShell có thể lọc bằng:

```powershell
gradle run --quiet | Select-String '^case='
```

Đừng khẳng định `grep`/`Select-String` là một tính năng của Spring. Đây chỉ là bước lọc console cho con người.

## 28. Diễn giải sáu ca

### Ca 1 — WorkingConfig

Spring đăng ký bean `gateway` trả `InvoiceGateway`. `invoiceService` nhận đúng một candidate và trở thành singleton bình thường. `issue()` trả `INV:ok`.

### Ca 2 — MissingConfig

`invoiceService(InvoiceGateway gateway)` yêu cầu dependency nhưng không có provider trong context. Root cause điển hình là `NoSuchBeanDefinitionException`; outer wrapper thường có `UnsatisfiedDependencyException`.

Một MRE thiếu bean cần giữ injection point này thay vì xóa `invoiceService`, vì nếu xóa cả consumer, lỗi cũng biến mất mà không cung cấp thông tin sửa.

### Ca 3 — AmbiguousConfig

Container có hai providers `northGateway` và `southGateway`. Parameter tên `gateway` không tự phân biệt hai names đó, và không có primary/qualifier, nên failure là `NoUniqueBeanDefinitionException` nested trong `UnsatisfiedDependencyException`.

Không sửa MRE bằng cách xóa một provider nếu production thật sự cần hai providers. Phải tìm lựa chọn bean đúng theo nghiệp vụ.

### Ca 4 — RepairedConfig

Thêm `@Qualifier("northGateway")` trên `@Bean` factory method parameter. Container giữ nguyên hai providers nhưng consumer chọn một provider xác định.

Đây là **positive control** tốt: đổi một quyết định resolution, không viết lại toàn bộ module.

### Ca 5 — ProfileConfig với profile test

`@Profile("prod")` loại `gateway` definition trong context profile `test`. `invoiceService` vẫn được đăng ký và thiếu dependency. Nếu MRE xóa `@Profile`, lỗi sẽ biến mất; điều đó giúp xác định condition đã đóng vai trò nhân quả.

### Ca 6 — ProfileConfig với profile prod

Cùng Java config class, khác profile được set **trước refresh**. Bean `gateway` được đăng ký và `issue()` trả `INV:prod`.

Hai lần chạy riêng không phải là “đổi profile của cùng context”; mỗi ca khởi tạo một BeanFactory độc lập.

## 29. Bộ JUnit 5 kiểm thử MRE

Các tests xác minh:

- Happy path context khởi động.
- Missing dependency được phân loại đúng.
- Ambiguous candidates khác missing candidate.
- `@Qualifier` sửa được ambiguity.
- Profile test loại provider.
- Profile prod có provider.
- Trường `unsatisfied` phản ánh injection point failure, thay vì chỉ status chung.

File `src/test/java/demo/ContainerReproTest.java`:

```java
package demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerReproTest {

    @Test
    void workingConfigStarts() {
        var result = ContainerRepro.inspect(
                ContainerRepro.WorkingConfig.class, "test");

        assertEquals("STARTED", result.status());
        assertEquals("INV:ok", result.detail());
        assertFalse(result.unsatisfied());
    }

    @Test
    void missingDependencyFailsWithNoCandidate() {
        var result = ContainerRepro.inspect(
                ContainerRepro.MissingConfig.class, "test");

        assertEquals("FAILED", result.status());
        assertEquals("NO_CANDIDATE", result.detail());
        assertTrue(result.unsatisfied());
    }

    @Test
    void multipleCandidatesAreNotMissingCandidates() {
        var result = ContainerRepro.inspect(
                ContainerRepro.AmbiguousConfig.class, "test");

        assertEquals("FAILED", result.status());
        assertEquals("MULTIPLE_CANDIDATES", result.detail());
        assertTrue(result.unsatisfied());
    }

    @Test
    void qualifierFixesSelection() {
        var result = ContainerRepro.inspect(
                ContainerRepro.RepairedConfig.class, "test");

        assertEquals("STARTED", result.status());
        assertEquals("INV:north", result.detail());
    }

    @Test
    void profileTestExcludesProvider() {
        var result = ContainerRepro.inspect(
                ContainerRepro.ProfileConfig.class, "test");

        assertEquals("FAILED", result.status());
        assertEquals("NO_CANDIDATE", result.detail());
    }

    @Test
    void profileProdRegistersProvider() {
        var result = ContainerRepro.inspect(
                ContainerRepro.ProfileConfig.class, "prod");

        assertEquals("STARTED", result.status());
        assertEquals("INV:prod", result.detail());
    }

    @Test
    void failedCasesReportUnsatisfiedInjection() {
        var missing = ContainerRepro.inspect(
                ContainerRepro.MissingConfig.class, "test");

        var ambiguous = ContainerRepro.inspect(
                ContainerRepro.AmbiguousConfig.class, "test");

        assertTrue(missing.unsatisfied());
        assertTrue(ambiguous.unsatisfied());
    }
}
```

Chạy:

```bash
gradle test
```

Kỳ vọng **7 JUnit Jupiter tests pass** trong project như mô tả. Gradle có thể ghi kết quả chi tiết vào `build/reports/tests/test/index.html`, không nhất thiết in từng test name ra console.

Đây là expected behavior dựa trên Spring 6.2.18 contracts và logic của source; khi dùng trong dự án thật cần chạy build trên toolchain/JDK thực tế. Các tests không cần test order vì mỗi call tới `inspect` tạo context mới.

## 30. Chọn thất bại nhỏ nhất làm test, không chỉ làm ảnh minh họa

Một MRE đủ tốt để duy trì regression test thường có:

- Một test `shouldFailWithX` để chứng minh root cause được hiểu đúng trước khi sửa.
- Một test `shouldStartAfterFix` xác nhận behavior đúng.
- Không phụ thuộc vào một thứ tự chạy nhất định.
- Không yêu cầu database/queue/network nếu lỗi là wiring.
- Không chứa nhiều mô-đun mà việc bỏ một nửa vẫn giữ nguyên lỗi.
- Không có arbitrary sleeps, random ports hoặc time-dependent assertions.
- Không buộc exact English message text của Spring.
- Có config version cố định và môi trường chạy rõ ràng.
- Có một assertion về **kết quả nghiệp vụ sau khi sửa**, không chỉ `context.refresh` không ném lỗi.

Trong ví dụ, `RepairedConfig` vẫn giữ hai `InvoiceGateway` beans và chứng minh giá trị đầu ra là `INV:north`. Điều này mạnh hơn chỉ kiểm tra không có exception.

## 31. Không tạo dependency graph mới khi giảm cấu hình

Một anti-pattern:

```text
Production:
  Controller -> Service -> Repository -> DataSource
  Error: Repository requires an absent Converter bean.

Bad reduction:
  Controller -> Service
  Error: Service no longer has Repository bean.
```

Hai lỗi có thể cùng in `UnsatisfiedDependencyException` nhưng **nguyên nhân khác nhau**. MRE thứ hai không chứng minh missing Converter.

Giữ dependency edge đang lỗi và thay thế các edges khác bằng fake providers. Ví dụ nếu mục tiêu là xác nhận `Converter` thiếu, cần vẫn giữ một bean consumer yêu cầu đúng `Converter` type/generics/qualifier, thay vì xóa các tầng đó rồi tái tạo một lỗi thiếu bean tùy ý.

## 32. Khi nghi ngờ @ComponentScan

Với component scanning rộng, cách cô lập là:
1. Xác định bean consumer thật.
2. Xác định provider class và package.
3. Kiểm tra scan base packages/excludes.
4. Chuyển MRE sang đăng ký classes tường minh (`@Import` hoặc `@Bean`).
5. So sánh hai lần: explicit registration chạy được, original scanning không chạy.
6. Kết luận scan boundary mới có đủ bằng chứng.

Ví dụ:

```java
@Configuration(proxyBeanMethods = false)
@Import({InvoiceGatewayConfig.class, InvoiceServiceConfig.class})
class ReproConfig {
}
```

Đây là mảnh cấu hình minh họa; các classes `InvoiceGatewayConfig` và `InvoiceServiceConfig` phải tồn tại. Nó không phải phần của runnable project ở trên.

Đừng dùng `@ComponentScan("com")` rộng để chữa lỗi chỉ vì nó làm application start: có thể kéo nhầm beans từ thư viện, tạo duplicate candidates hoặc thêm infrastructure side effects.

## 33. Khi nghi ngờ BeanPostProcessor / proxy

Nếu production failure phụ thuộc AOP/transaction/scoped proxy, cần giữ:

- Advisor/auto-proxy creator mà làm exposed bean type thay đổi.
- Target class/interface declaration và injection point type.
- Annotation như `@Transactional`, `@Async`, `@Cacheable` liên quan.
- Proxy mode JDK vs CGLIB.
- Early reference behavior nếu có circular dependency.

Một MRE bỏ toàn bộ proxy infrastructure và vẫn có thể khởi động **không bác bỏ** lỗi proxy ở production — nó chỉ cho biết loại bỏ proxying làm hiện tượng biến mất.

Ghi ra khi có context khỏe:

```java
Object bean = context.getBean("invoiceGateway");

System.out.println(bean.getClass().getName());
System.out.println(
        AopUtils.isJdkDynamicProxy(bean));
System.out.println(
        AopUtils.isCglibProxy(bean));
```

Đây là snippet minh họa yêu cầu import `AopUtils` từ `spring-aop` và context có bean named `invoiceGateway`. Trong runnable demo, provider có tên `gateway`/`northGateway`, nên **không** copy snippet vào demo mà chưa đổi bean name.

`AopUtils.getTargetClass(bean)` là thông tin target metadata; nó không chứng minh một JDK proxy assignable về concrete target class.

## 34. BeanFactoryPostProcessor và thời điểm đọc metadata

`BeanFactoryPostProcessor` có thể dùng để inspect/modify BeanDefinitions trước normal singleton instantiation:

```java
@Bean
static BeanFactoryPostProcessor inspectDefinitions() {
    return beanFactory -> {
        String[] names =
                beanFactory.getBeanDefinitionNames();
        Arrays.sort(names);
        System.out.println(Arrays.toString(names));
    };
}
```

Đây là method minh họa trong một `@Configuration`, cần imports `BeanFactoryPostProcessor` và `Arrays`. Đặt `@Bean` method dạng `static` giúp tránh instantiate configuration class sớm chỉ để tạo BeanFactoryPostProcessor.

Cần chú ý:
- BeanFactoryPostProcessor **không** phải nơi thích hợp để gọi `getBeansOfType` trên toàn app chỉ để in mọi object.
- Đừng tạo application beans ở giai đoạn bean post-processors chưa đăng ký đầy đủ.
- Nếu app dùng AOP, tạo bean quá sớm có thể làm bean không được proxy như mong đợi.
- Diagnostics nên đọc BeanDefinitions và type predictions khi có thể.

Đây là công cụ hữu ích để so sánh bản config đầy đủ với bản rút gọn **ở cùng lifecycle phase**.

## 35. Những lỗi quan sát logging thường gặp

| Triệu chứng | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| Bật DEBUG nhưng không thấy log nào | Sai logging backend/config file | Kiểm tra classpath/backend và file location |
| Logback config không có tác dụng | SLF4J provider khác hoặc resources sai | Kiểm tra runtime dependency tree |
| Log quá nhiều không tìm được bean lỗi | TRACE root toàn framework | Bật categories hẹp, tìm bean name và cause |
| Không thấy stack trace vì đã catch exception | App catch rồi không log | In stack trace vào test diagnostics khi cần |
| Spring Boot --debug không có effect trong core app | Boot feature không tồn tại | Dùng logging backend config thuần |
| Rút gọn xong lỗi biến mất | Đã xóa thành phần gây lỗi | Restore thành phần và giảm lại có kiểm soát |
| Rút gọn từ ambiguous thành missing | Đã thay cơ chế thất bại | Giữ provider candidates/selection metadata |
| Context đóng rồi getBean tiếp | Failed context không còn active | Tạo context mới cho lần thử |
| Different profile không tạo bean | Profile evaluate khi bootstrap | Set profiles trước refresh |
| getBeansOfType gây lỗi mới | Instantiates object khi chỉ cần metadata | Dùng getBeanNamesForType/type metadata |
| Runtime khác IDE | Classpath/env/profiles khác | Chốt versions, JDK args, environment |
| Log thứ tự thay đổi giữa runs | Framework internals và processor ordering | Test contract, không snapshot log text |
| @Configuration class không được import | Thiếu root config registration | Explicit register/import/scanning đúng |
| Thêm package scan quá rộng tạo ambiguity | Nhặt cả provider không mong muốn | Giới hạn scan, dùng qualifiers |
| Lỗi nằm trong property binding | Nhầm bean type selection với value resolution | Kiểm tra property source/converter/cause |
| JVM forks làm logs phân tán | Nhiều test processes | Nhận diện PID/process/build task tương ứng |

## 36. Checklist trước khi kết luận đã tìm ra root cause

1. Đã lưu full stack trace ban đầu.
2. Đã xác định failing bean name và injection point.
3. Đã đọc nested `Caused by` đủ sâu.
4. Biết Spring/Java/Build tool versions chính xác.
5. Biết active profiles và conditional properties.
6. Đã phân biệt Spring Framework logging và Boot Condition Evaluation Report.
7. Logging backend đang đúng (không có conflicting bindings).
8. Logger packages được bật có chọn lọc.
9. Không có secrets trong logs được chia sẻ.
10. Không dùng `getBeansOfType` chỉ để inspect names.
11. Biết BeanDefinition xuất hiện trước hay sau processors liên quan.
12. MRE giữ nguyên **failure mechanism**.
13. MRE chỉ giữ những config beans/phụ thuộc liên quan.
14. Những external integrations không liên quan đã được stub hoặc isolate.
15. MRE có command chạy độc lập, không cần IDE magic.
16. Negative test tái hiện đúng exception type/root cause.
17. Positive control thay đổi tối thiểu và khởi động được.
18. Khi có ambiguous candidates, đã giữ đầy đủ candidate set.
19. Khi lỗi profile, đã giữ condition/profiles y nguyên.
20. Khi lỗi proxy, đã giữ proxy-producing infrastructure.
21. Không suy một exception message thuộc một phiên bản là contract lâu dài.
22. Không dùng một context failed refresh để chạy tiếp.
23. Tests không phụ thuộc thứ tự hoặc state ngoại vi chưa cleanup.
24. Đã áp dụng sửa chữa cho cấu hình thật và chạy lại integration tests.
25. Mức logging production đã được trả về an toàn.

## 37. Mẫu báo cáo lỗi ngắn gọn cho đồng đội

Một issue/ticket hữu ích có thể theo bố cục:

```text
Title:
  Spring IoC: invoiceService fails when active profile=test

Expected:
  Context refresh succeeds with exactly one InvoiceGateway.

Actual:
  UnsatisfiedDependencyException during invoiceService creation.
  Root cause: NoSuchBeanDefinitionException for InvoiceGateway.

Environment:
  Java 17 / Spring Framework 6.2.18 / Gradle.
  Active profile: test.
  Relevant config: ProfileConfig.

Repro:
  gradle test
  Run test profileTestExcludesProvider.

Cause:
  Gateway bean is annotated @Profile("prod");
  test profile excludes its BeanDefinition.

Fix:
  Register a test-specific gateway or use the correct profile
  before refresh.

Validation:
  The minimal failing test still detects missing candidate.
  The repaired configuration starts and InvoiceService.issue()
  returns the expected invoice code.
```

Đây là **report template** minh họa, không phải log thực của một hệ thống đã được kiểm tra. Lưu ý root-cause và fix rõ ràng, không chứa secrets hoặc unrelated stack traces.

## 38. Nguồn tham khảo chính thức

1. [Spring Framework 6.2 Reference — Container Overview](https://docs.spring.io/spring-framework/reference/6.2/core/beans/basics.html): `ApplicationContext` và configuration metadata.
2. [Spring Framework 6.2 Reference — BeanFactory API](https://docs.spring.io/spring-framework/reference/6.2/core/beans/beanfactory.html): bean factory là nền tảng của IoC, `DefaultListableBeanFactory`.
3. [Spring Framework 6.2 Reference — Logging](https://docs.spring.io/spring-framework/reference/6.2/core/spring-jcl.html): `spring-jcl` và logging backends.
4. [Spring Framework 6.2 Reference — Annotation-based Container Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config.html): annotation processing, `@Autowired` và post-processors.
5. [Spring Framework 6.2 Reference — Using @Bean](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html): registration qua factory methods.
6. [Spring Framework 6.2 Reference — Autowiring Collaborators](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-autowire.html): candidate resolution và ambiguity.
7. [Spring Framework 6.2 Reference — Profiles and Environment](https://docs.spring.io/spring-framework/reference/6.2/core/beans/environment.html): profiles và conditional registration.
8. [Spring Framework 6.2.18 Javadoc — AnnotationConfigApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/context/annotation/AnnotationConfigApplicationContext.html): `register`, `refresh`, lifecycle.
9. [Spring Framework 6.2.18 Javadoc — DefaultListableBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/beans/factory/support/DefaultListableBeanFactory.html): definition/type inspection, eager resolution flags.
10. [Spring Framework 6.2.18 Javadoc — UnsatisfiedDependencyException](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/beans/factory/UnsatisfiedDependencyException.html): injection point và nested causes.
11. [Spring Boot Reference — Auto-configuration](https://docs.spring.io/spring-boot/reference/using/auto-configuration.html): Boot-specific `--debug` và Conditions Evaluation Report.
12. [Spring Boot Reference — Troubleshoot Auto-configuration](https://docs.spring.io/spring-boot/how-to/application.html): điều tra condition reports và Actuator conditions endpoint.
13. [JUnit Jupiter User Guide 5.11.4](https://docs.junit.org/5.11.4/user-guide/): `@Test` assertions và test lifecycle.

**Ghi nhớ:** bật `DEBUG` đúng logger để quan sát, nhưng **nguyên nhân gốc nằm trong configuration graph và chuỗi exceptions**. Khi thu nhỏ để tái hiện, giữ nguyên injection point, candidate set, profile/condition và proxy/runtime features có liên quan; giảm những phần không liên quan dần dần. Dùng MRE có cả **failure test và repaired test**, đo contract thay vì snapshot toàn bộ Spring logs. Với Spring Framework thuần, cấu hình logging backend trực tiếp; chỉ dùng Boot `--debug`/Conditions Evaluation Report nếu đang thật sự chạy Spring Boot.


</details>

- [Quay lại đầu trang](#back-to-top)
