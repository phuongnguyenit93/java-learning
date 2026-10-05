<a id="back-to-top"></a>

# Đăng ký và cấu hình Bean

## Menu
- [Đăng ký tường minh và component scanning](#explicit-vs-scanning)
- [Stereotype annotations và các component được quản lý](#stereotype-components)
- [Phạm vi và filter của component scanning](#component-scan-boundaries)
- [@Configuration và @Bean](#configuration-and-bean)
- [Tên bean, alias và quy tắc sinh tên](#bean-names-and-aliases)
- [Ngữ nghĩa full và lite của Java configuration](#full-vs-lite-configuration)
- [Ghép cấu hình bằng @Import](#import-composition)
- [Đăng ký có điều kiện bằng @Conditional](#conditional-registration)
- [Đăng ký bean bằng XML và API lập trình](#xml-and-programmatic-registration)

## <a id="explicit-vs-scanning">Đăng ký tường minh và component scanning</a>

<details>
<summary>Xem chi tiết</summary>

Trước khi Spring có thể tạo bean, container phải có definition của bean đó. Vì vậy quyết định đầu tiên là: các definition sẽ đi vào container bằng cách nào?

Với **đăng ký tường minh**, cấu hình chủ động chỉ ra component nào tồn tại, chẳng hạn bằng `@Bean` hoặc API đăng ký. Cách ghép nối dễ lần theo vì cấu hình cho thấy object nào thuộc đồ thị và class từ thư viện bên ngoài được tạo ra sao.

Với **component scanning**, Spring quét những package đã chọn để tìm các class ứng viên, thông thường là class mang `@Component` hoặc stereotype được meta-annotate bằng `@Component`. Cách này giảm phần khai báo lặp lại cho component do ứng dụng sở hữu, nhưng đồng thời biến ranh giới package và quy ước annotation thành một phần của cấu hình.

Không có một lựa chọn đúng cho mọi tình huống. Một quy tắc thực tế:

- scanning phù hợp với các component ứng dụng cùng một vùng package mà bạn sở hữu và có thể annotate;
- `@Bean` tường minh phù hợp khi cách khởi tạo cần nhìn thấy rõ, khi tích hợp class từ thư viện bên ngoài, hoặc khi muốn đồ thị object dễ đọc từ cấu hình;
- có thể kết hợp hai cách nếu mỗi cách giải quyết một nhóm object rõ ràng.

Điểm quan trọng là cả hai đều hội tụ về bean definitions. Scanning không tạo ra một loại container khác; nó chỉ là cơ chế khám phá để đóng góp definition vào cùng `BeanFactory`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stereotype-components">Stereotype annotations và các component được quản lý</a>

<details>
<summary>Xem chi tiết</summary>

`@Component` đánh dấu một class là ứng viên cho component scanning. Các stereotype chuyên biệt như `@Service`, `@Repository` và `@Controller` đều được meta-annotate bằng `@Component`, nên chúng tham gia cùng cơ chế khám phá nhưng đồng thời diễn đạt vai trò cụ thể hơn.

Vai trò đó không chỉ để trang trí. Nó giúp người đọc hiểu trách nhiệm của class và đôi khi còn tham gia trực tiếp vào tích hợp của framework. Ví dụ, `@Repository` biểu đạt trách nhiệm persistence và có thể tham gia cơ chế persistence exception translation khi hạ tầng tương ứng được đăng ký.

```java
@Service
final class BillingService {
    private final InvoiceRepository invoices;

    BillingService(InvoiceRepository invoices) {
        this.invoices = invoices;
    }
}
```

Khi scan tìm thấy `BillingService`, Spring đăng ký một bean definition; class không được tạo instance chỉ vì annotation tồn tại. Bean creation vẫn tuân theo dependency resolution, scope, post-processing và lifecycle bình thường.

Bạn cũng có thể tạo stereotype riêng bằng cách meta-annotate annotation của mình với `@Component` hoặc stereotype khác. Với Spring Framework 6.1, nếu composed annotation cần expose/override attribute như component name, nên khai báo quan hệ đó rõ bằng `@AliasFor` thay vì dựa vào convention ngầm theo tên attribute.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="component-scan-boundaries">Phạm vi và filter của component scanning</a>

<details>
<summary>Xem chi tiết</summary>

Component scanning chỉ dễ kiểm soát khi phạm vi scan được chọn có chủ đích. Quét quá hẹp làm mất component; quét quá rộng có thể kéo vào context những class hạ tầng hoặc tính năng không nên cùng tồn tại.

`@ComponentScan` có thể chỉ định package trực tiếp hoặc dùng marker class qua `basePackageClasses` để tránh string package dễ sai. Nếu không chỉ định package, Spring bắt đầu scan từ package của class khai báo `@ComponentScan`.

```java
@Configuration
@ComponentScan(basePackageClasses = BillingModule.class)
class BillingConfig { }
```

Mặc định, Spring nhận các class có `@Component` hoặc meta-annotation dựa trên nó. Include/exclude filter có thể tinh chỉnh tập ứng viên theo annotation, assignable type, AspectJ pattern, regex hoặc `TypeFilter` tùy biến.

Filter nên là chính sách cấu hình có giới hạn, không phải cách bù cho thiết kế package thiếu rõ ràng. Một mạng filter phức tạp khiến đồ thị khó dự đoán và kiểm thử. Ranh giới package ổn định cùng một vài filter nhỏ thường dễ bảo trì hơn.

Scanning cũng không bỏ qua các quy tắc resolution thông thường. Hai component được scan cùng triển khai một interface vẫn có thể gây mơ hồ; bean-name collision vẫn có thể làm registration thất bại hoặc yêu cầu naming strategy có chủ đích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-and-bean">@Configuration và @Bean</a>

<details>
<summary>Xem chi tiết</summary>

Java configuration cho phép dùng method Java thông thường để mô tả bean creation. Class có `@Configuration` là một nguồn bean definitions; mỗi method `@Bean` mô tả cách lấy một bean.

```java
@Configuration
class PaymentConfig {
    @Bean
    PaymentGateway paymentGateway(HttpClient client) {
        return new StripeGateway(client);
    }

    @Bean
    HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }
}
```

Parameter của `@Bean` method chính là dependency point. Spring phân giải `HttpClient` từ container trước khi gọi `paymentGateway`, vì vậy configuration method không cần gọi trực tiếp factory method khác chỉ để lấy dependency.

`@Bean` đặc biệt hữu ích với class bạn không thể annotate, với factory cần logic khởi tạo rõ ràng, hoặc khi muốn lựa chọn dependency thể hiện ngay trong cấu hình. Object được trả về trở thành bean do Spring quản lý và tiếp tục chịu scope, lifecycle, post-processing cùng các quy tắc dependency như bean khác.

Nên giữ factory method tập trung vào việc tạo object. Logic nghiệp vụ nặng, side effect ra bên ngoài hoặc quyết định ở runtime bị giấu trong `@Bean` method sẽ khiến quá trình khởi động context khó hiểu và biến lỗi cấu hình thành lỗi vận hành khó đoán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-names-and-aliases">Tên bean, alias và quy tắc sinh tên</a>

<details>
<summary>Xem chi tiết</summary>

Mỗi bean definition có một tên. Bean name hữu ích cho chẩn đoán, lookup tường minh, qualifier, alias và những tình huống type không đủ để nhận diện duy nhất một bean.

Với component được scanning phát hiện, tên mặc định thường được suy ra từ short class name, ví dụ `BillingService` thành `billingService`. Stereotype có thể khai báo tên rõ ràng khi cần một tên có ý nghĩa ổn định.

Với `@Bean`, tên mặc định là tên method:

```java
@Bean
DataSource reportingDataSource() { ... }
```

Bean mặc định tên `reportingDataSource`. Thuộc tính `name`/`value` của `@Bean` có thể chỉ định primary name và thêm aliases. Alias là tên khác cùng phân giải tới một bean definition chuẩn; nó không tạo bean definition thứ hai. Instance cụ thể vẫn phụ thuộc vào scope, vì vậy nhiều lần lookup một prototype qua tên chính hoặc alias vẫn có thể nhận các instance khác nhau.

Nếu bean name là một phần của hợp đồng tích hợp, đừng phụ thuộc vào tên mặc định dễ thay đổi khi class/method được đổi tên. Ngược lại, cũng không cần gán tên tường minh cho mọi bean: type-based injection thường rõ ràng hơn, còn qualifier phù hợp khi nhiều bean cùng type mang vai trò khác nhau.

Chiến lược đặt tên cũng ảnh hưởng collision khi scanning. Hai component sinh ra cùng tên có thể làm quá trình đăng ký thất bại; đây thường là tín hiệu cần xem lại danh tính component hoặc ranh giới package thay vì chỉ che lỗi bằng một tên ngẫu nhiên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="full-vs-lite-configuration">Ngữ nghĩa full và lite của Java configuration</a>

<details>
<summary>Xem chi tiết</summary>

Spring phân biệt **full configuration** với cách xử lý `@Bean` theo **lite mode**. Khác biệt trở nên rõ nhất khi một `@Bean` method gọi trực tiếp một `@Bean` method khác.

Với `@Configuration` thông thường (`proxyBeanMethods = true`, mặc định), Spring có thể enhance configuration class để đưa lời gọi giữa các instance `@Bean` method có thể được intercept qua container. Vì vậy lời gọi singleton `repository()` trong ví dụ dưới đây vẫn trả về managed singleton thay vì cứ tạo object mới.

```java
@Configuration
class AppConfig {
    @Bean
    Repository repository() { return new JdbcRepository(); }

    @Bean
    Service service() { return new Service(repository()); }
}
```

Ở **lite mode**, chẳng hạn `@Configuration(proxyBeanMethods = false)` hoặc `@Bean` nằm trên class không được xử lý như full configuration, lời gọi method trực tiếp chỉ là lời gọi Java bình thường. `repository()` trong ví dụ có thể chạy lại và tạo object mới ngoài managed singleton lookup path.

Interception ở full mode vẫn chịu giới hạn của Java: configuration class và các instance `@Bean` method liên quan phải cho phép subclass/override để Spring enhance chúng. Method `final` hoặc `private` không tham gia được đường interception này, còn lời gọi tới `static @Bean` method thì không bao giờ được intercept.

Vì vậy parameter injection thường là cách cấu hình rõ nhất:

```java
@Bean
Service service(Repository repository) {
    return new Service(repository);
}
```

Cách này hoạt động tự nhiên ở cả full và lite mode, đồng thời làm dependency edge rõ ràng. Chọn full configuration khi thực sự cần inter-bean interception; chọn lite khi các factory method độc lập và không cần proxy configuration class.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="import-composition">Ghép cấu hình bằng @Import</a>

<details>
<summary>Xem chi tiết</summary>

Cấu hình lớn nên được ghép từ các phần có trách nhiệm rõ ràng thay vì dồn vào một class. `@Import` cho phép một configuration class đưa nguồn cấu hình khác vào cùng application context.

```java
@Configuration
@Import({PersistenceConfig.class, BillingConfig.class})
class ApplicationConfig { }
```

Với cấu hình ứng dụng thông thường, import các configuration class là dạng dễ đọc nhất: quan hệ giữa các module cấu hình được thể hiện trực tiếp và Spring xử lý các class được import như nguồn cấu hình.

`@Import` cũng là một cơ chế mở rộng quan trọng của framework. Nó có thể làm việc với `ImportSelector`, `DeferredImportSelector` và `ImportBeanDefinitionRegistrar`, cho phép code hạ tầng chọn hoặc đăng ký definition dựa trên annotation metadata. Đây là công cụ mạnh, nhưng mã ứng dụng thường không cần dùng khi một configuration class hoặc `@Bean` bình thường đã đủ.

Việc ghép cấu hình vẫn phải giữ trách nhiệm dễ hiểu. Nếu tính năng A import tính năng B, quan hệ đó nên phản ánh dependency cấu hình thực sự. Một mạng `@Import` chằng chịt có thể che coupling giữa các module chẳng khác gì một component scan quá rộng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="conditional-registration">Đăng ký có điều kiện bằng @Conditional</a>

<details>
<summary>Xem chi tiết</summary>

Đôi khi bean hoặc cả configuration chỉ nên tồn tại khi một điều kiện cụ thể thỏa mãn. Spring Framework cung cấp `@Conditional` như cơ chế tổng quát cho bài toán này.

Một lớp điều kiện triển khai `Condition` và quyết định component cấu hình có được đăng ký hay không:

```java
final class ProductionCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return context.getEnvironment().acceptsProfiles(Profiles.of("prod"));
    }
}
```

`@Conditional` có thể đặt trên `@Bean` method và trên các component type, bao gồm `@Configuration` class; nó cũng có thể được dùng làm meta-annotation. Condition được đánh giá trong lúc xử lý metadata cấu hình, trước khi bean đích được tạo. Qua `ConditionContext`, code có thể xem `Environment`, resource loader, class loader, registry context và annotation metadata phù hợp.

Condition thuộc giai đoạn cấu hình. Quy ước của `Condition` yêu cầu quyết định dựa trên trạng thái cấu hình mà không tương tác với bean instance. Không được lấy hoặc gọi bean ứng dụng từ `matches`, vì lúc này container vẫn đang quyết định việc đăng ký.

Hãy dùng `@Conditional` khi việc đăng ký có điều kiện thực sự thuộc trách nhiệm của cấu hình/framework. Phần này chỉ nói về cơ chế của Spring Framework; các cơ chế conditional registration cấp cao hơn của Spring Boot nằm ngoài module này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="xml-and-programmatic-registration">Đăng ký bean bằng XML và API lập trình</a>

<details>
<summary>Xem chi tiết</summary>

Annotation Java không phải nguồn metadata duy nhất của Spring. XML và API lập trình vẫn đưa definition vào cùng mô hình container và có thể phù hợp ở một số ranh giới tích hợp hoặc trong code hạ tầng.

XML có thể mô tả bean class, constructor arguments, properties, scope, alias và nhiều metadata khác mà không sửa source class. Context như `ClassPathXmlApplicationContext` đọc các definition đó rồi xây bean factory theo cùng quy tắc lifecycle như Java configuration.

Đăng ký bằng API cho code quyền kiểm soát trực tiếp hơn. `GenericApplicationContext` có API đăng ký bean; `AnnotationConfigApplicationContext` có thể đăng ký configuration class trước refresh. Ở tầng thấp hơn, code hạ tầng có thể đưa `BeanDefinition` trực tiếp vào `BeanDefinitionRegistry`.

Các cơ chế này là những cách khác nhau để cung cấp metadata, không phải các DI engine khác nhau:

```text
XML
Java configuration
component scanning
programmatic registration
        ↓
BeanDefinition registry
        ↓
cùng quy tắc lifecycle và dependency resolution
```

Nên chọn nguồn metadata ít gây bất ngờ nhất cho trách nhiệm cấu hình của hệ thống. Java configuration thường thuận tiện cho mã ứng dụng; XML vẫn hữu ích ở hệ thống đã có wiring khai báo; đăng ký bằng API phù hợp khi definition phải được tạo động. Có thể phối hợp nhiều kiểu, miễn đồ thị object cuối cùng vẫn dễ truy vết.

</details>

- [Quay lại đầu trang](#back-to-top)
