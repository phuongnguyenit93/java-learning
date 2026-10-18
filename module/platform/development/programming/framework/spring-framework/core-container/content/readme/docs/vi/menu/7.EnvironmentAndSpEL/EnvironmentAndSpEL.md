<a id="back-to-top"></a>

# Environment, PropertySource, Profile và SpEL

## Menu
- [Environment abstraction](#environment-abstraction)
- [PropertySource và thứ tự ưu tiên](#property-sources-and-precedence)
- [Property placeholders](#property-placeholders)
- [Profiles và @Profile](#profiles)
- [SpEL dùng để làm gì?](#spel-purpose)
- [Mô hình đánh giá SpEL](#spel-evaluation-model)
- [SpEL trong bean definitions và @Value](#spel-in-bean-definitions)
- [Ranh giới với Spring Boot externalized configuration](#boot-config-boundary)

## <a id="environment-abstraction">Environment abstraction</a>

<details>
<summary>Xem chi tiết</summary>

`Environment` cho container một mô hình chung cho hai mối quan tâm có liên hệ: **bean definition nào đủ điều kiện được đăng ký** và **những property có tên nào đang khả dụng cho code cấu hình**.

Hai mặt này có vai trò khác nhau:

```text
profiles
→ quyết định definition nào tham gia vào context

properties
→ phân giải giá trị theo tên từ tập PropertySource có thứ tự
```

Bean có thể phụ thuộc vào API hẹp `Environment` mà không cần biết giá trị đến từ đâu:

```java
@Bean
ClientSettings clientSettings(Environment environment) {
    String endpoint = environment.getRequiredProperty("client.endpoint");
    int timeout = environment.getProperty("client.timeout-ms", Integer.class, 2000);
    return new ClientSettings(endpoint, timeout);
}
```

Nhờ vậy code sử dụng không bị gắn với JVM system properties, OS environment variables, một property file được thêm riêng hay một `PropertySource` khác.

`ConfigurableEnvironment` cung cấp thao tác thay đổi như đặt active profiles hoặc thêm property source. Đây là việc của quá trình thiết lập context và thông thường phải hoàn tất trước `refresh()`, vì việc đánh giá profile và xử lý bean definition diễn ra trong lúc container được dựng. Service ứng dụng thường chỉ nên đọc qua `Environment`, không nên sửa cấu hình chung sau khi khởi động.

Ranh giới cần giữ rõ: `Environment` là abstraction nền tảng của Spring Framework. Bản thân nó không định nghĩa quy tắc Config Data của Spring Boot.

### Tài liệu tham khảo

- Spring Framework Reference — Environment Abstraction

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="property-sources-and-precedence">PropertySource và thứ tự ưu tiên</a>

<details>
<summary>Xem chi tiết</summary>

`PropertySource` biểu diễn một nguồn dữ liệu key-value có tên. `Environment` giữ nhiều nguồn theo thứ tự rõ ràng rồi tìm theo độ ưu tiên. Với một key, nguồn có độ ưu tiên cao hơn và trả được giá trị sẽ thắng.

Ví dụ, `StandardEnvironment` thông thường có JVM system properties và OS environment variables. Ta có thể đặt một `PropertySource` của ứng dụng lên trước:

```java
ConfigurableEnvironment environment = context.getEnvironment();
MutablePropertySources sources = environment.getPropertySources();

Map<String, Object> overrides = Map.of("client.timeout-ms", 500);
sources.addFirst(new MapPropertySource("localOverrides", overrides));
```

Sau đó `client.timeout-ms` trong `localOverrides` có độ ưu tiên cao hơn cùng key ở các source phía sau.

`@PropertySource` là cách tiện lợi ở mức Spring Framework để thêm một property source lấy từ resource:

```java
@Configuration
@PropertySource("classpath:client.properties")
class ClientConfiguration {
}
```

Không nên hình dung "properties" như một map lớn đã được hợp nhất mà không biết giá trị nào thắng. Kết quả phụ thuộc vào thứ tự các source. Nếu thứ tự ghi đè là một phần quan trọng của thiết kế ứng dụng, hãy cấu hình nó một cách chủ động. Phụ thuộc vào thứ tự phát hiện tình cờ giữa nhiều configuration class được scan độc lập sẽ làm key trùng rất khó chẩn đoán.

Cũng cần phân biệt property không tồn tại với property bắt buộc. `Environment.getProperty(...)` có thể trả `null`; `getRequiredProperty(...)` biểu đạt rõ rằng quá trình khởi động phải thất bại khi key thiếu.

Chương này dạy cơ chế sắp thứ tự của Spring Framework. Các quy tắc nạp và ưu tiên mở rộng do Spring Boot cung cấp thuộc phần Boot configuration.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="property-placeholders">Property placeholders</a>

<details>
<summary>Xem chi tiết</summary>

Property placeholder lấy một giá trị theo key. Dạng quen thuộc:

```text
${client.timeout-ms}
${client.timeout-ms:2000}
```

Dạng thứ hai có giá trị mặc định. Về bản chất placeholder là **lookup**, không phải một biểu thức tùy ý.

`@Value` có thể dùng placeholder tại injection point:

```java
@Component
final class RemoteClient {
    private final int timeoutMs;

    RemoteClient(@Value("${client.timeout-ms:2000}") int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
```

Việc tìm placeholder và việc chuyển đổi kiểu là hai bước riêng biệt. Các kiểu scalar đơn giản như `int` hoạt động với hạ tầng chuyển đổi kiểu chuẩn. Với kiểu ứng dụng phong phú hơn, context cần có conversion support phù hợp; không nên mặc định rằng một Spring Framework context thuần đã đăng ký mọi formatter mà các dự án cấp cao hơn có thể cung cấp.

Trong Spring Framework context thuần có một lựa chọn quan trọng về độ nghiêm ngặt. Spring có thể cung cấp embedded value resolver mặc định theo kiểu lenient: placeholder không phân giải được có thể còn nguyên dạng text. Nếu thiếu giá trị phải làm context initialization thất bại, hãy đăng ký `PropertySourcesPlaceholderConfigurer`. Với Java configuration, nên khai báo `@Bean` method này là `static` để tránh xung đột lifecycle do các bean `BeanFactoryPostProcessor` phải được tạo rất sớm:

```java
@Bean
static PropertySourcesPlaceholderConfigurer placeholders() {
    return new PropertySourcesPlaceholderConfigurer();
}
```

Configurer này phân giải `${...}` dựa trên `Environment` và các `PropertySources` trong lúc bean metadata được chuẩn bị.

Hãy giữ rõ khác biệt cú pháp: `${...}` yêu cầu một property value; `#{...}` yêu cầu SpEL đánh giá một biểu thức. Nếu chỉ cần lookup thì placeholder đơn giản hơn và nên được ưu tiên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="profiles">Profiles và @Profile</a>

<details>
<summary>Xem chi tiết</summary>

Profile là **điều kiện đăng ký bean**. Nó trả lời "definition này có tham gia context của môi trường hiện tại không?", không phải "mỗi request nghiệp vụ nên chạy nhánh nào?".

```java
@Configuration
@Profile("development")
class DevelopmentConfiguration {
    @Bean
    PaymentGateway paymentGateway() {
        return new StubPaymentGateway();
    }
}

@Configuration
@Profile("production")
class ProductionConfiguration {
    @Bean
    PaymentGateway paymentGateway() {
        return new RemotePaymentGateway();
    }
}
```

Khi khởi động context, các active profile quyết định definition nào đủ điều kiện. Nếu `@Profile` nằm trên configuration class thì các bean definition và import gắn với class đó bị bỏ qua khi điều kiện không khớp.

Profile expression hỗ trợ cách kết hợp đơn giản:

```text
production & eu
development | test
!cloud
```

Khi trộn `&` và `|` trong cùng expression phải dùng dấu ngoặc để nhóm rõ. Nhiều profile có thể được kích hoạt đồng thời; profile không mặc định là các lựa chọn loại trừ lẫn nhau.

Nếu không profile nào được kích hoạt rõ ràng, Spring dùng tập default profile, với tên quy ước là `default`. Active/default profiles có thể cấu hình qua `Environment` và nên được thiết lập trước khi context refresh.

Profile phù hợp để chọn đồ thị object theo môi trường ở mức tương đối thô. Nếu quy tắc nghiệp vụ thay đổi theo customer, tenant, request hoặc dữ liệu runtime, hãy mô hình hóa quy tắc đó trong mã ứng dụng thay vì giấu nó trong profile-based registration.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spel-purpose">SpEL dùng để làm gì?</a>

<details>
<summary>Xem chi tiết</summary>

Property placeholder lấy được một giá trị, nhưng đôi khi bean metadata cần **tính** giá trị từ một đồ thị object. Spring Expression Language (SpEL) tồn tại cho vai trò đánh giá biểu thức đó.

SpEL có thể đọc property, gọi method, dùng toán tử, làm việc với collection, tham chiếu type, variable hoặc bean và trả về kết quả có kiểu. Ví dụ:

```java
ExpressionParser parser = new SpelExpressionParser();
Expression expression =
        parser.parseExpression("name.toUpperCase()");

String value = expression.getValue(customer, String.class);
```

Cơ chế này hữu ích khi cấu hình vốn mang tính khai báo và một expression nhỏ diễn tả quan hệ rõ hơn việc tạo thêm adapter class.

Đổi lại, expression càng lớn thì logic càng bị giấu trong string, mất hỗ trợ refactoring thông thường của Java và nhiều lỗi chỉ xuất hiện lúc runtime. Nếu expression đã trở thành logic nghiệp vụ hoặc cần được suy luận, kiểm thử độc lập, hãy chuyển hành vi sang Java rồi tham chiếu bean/property kết quả.

SpEL nằm trong module này vì core container có thể đánh giá nó trong bean metadata và `@Value`. Những Spring module khác có thể dùng SpEL cho domain riêng, nhưng ý nghĩa của expression trong domain đó thuộc module tương ứng.

Không nên đánh giá SpEL do người dùng cuối hoặc nguồn không tin cậy cung cấp như thể đây chỉ là template language. Full SpEL có thể chạm tới method, constructor, type và object được cung cấp qua evaluation context.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spel-evaluation-model">Mô hình đánh giá SpEL</a>

<details>
<summary>Xem chi tiết</summary>

Khi dùng SpEL theo chương trình, có ba vai trò chính:

```text
ExpressionParser
→ parse text của expression

Expression
→ biểu diễn expression đã parse và có thể tái sử dụng

EvaluationContext
→ quyết định cách phân giải property, method, type, variable,
  bean reference và conversion khi đánh giá
```

Root object cung cấp đồ thị object mặc định:

```java
ExpressionParser parser = new SpelExpressionParser();
Expression expression = parser.parseExpression("address.city");

String city = expression.getValue(customer, String.class);
```

Với nhu cầu đánh giá phong phú hơn, `StandardEvaluationContext` cho phép cấu hình đầy đủ:

```java
StandardEvaluationContext context = new StandardEvaluationContext(customer);
context.setVariable("discount", new BigDecimal("0.10"));

BigDecimal amount = parser
        .parseExpression("orderTotal * (1 - #discount)")
        .getValue(context, BigDecimal.class);
```

Variable dùng cú pháp `#name`. Bean reference như `@pricingPolicy` cần `BeanResolver` trong evaluation context. Type conversion cũng thuộc quá trình đánh giá, nên target type được yêu cầu có thể ảnh hưởng cách kết quả được chuyển đổi.

`SimpleEvaluationContext` chủ động cung cấp một tập feature nhỏ hơn và phù hợp khi không cần toàn bộ ngôn ngữ. Dù vậy nó vẫn phải được cấu hình cẩn thận; giảm feature không tự biến một expression tùy ý từ nguồn không tin cậy thành an toàn.

Cần tách parsing với evaluation trong mô hình tư duy. Sai cú pháp làm parse thất bại; expression đúng cú pháp vẫn có thể fail khi đánh giá vì property, method, type, variable hoặc conversion không tồn tại trong context hiện tại.

### Tài liệu tham khảo

- Spring Framework Reference — Spring Expression Language: Evaluation

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spel-in-bean-definitions">SpEL trong bean definitions và @Value</a>

<details>
<summary>Xem chi tiết</summary>

Trong Spring bean metadata, SpEL dùng `#{...}`. Application context cung cấp hạ tầng đánh giá gắn với container, vì vậy expression có thể truy cập dữ liệu chuẩn của context và bean.

```java
@Component
final class CatalogClient {
    private final String region;
    private final int batchSize;

    CatalogClient(
            @Value("#{systemProperties['user.region'] ?: 'global'}") String region,
            @Value("${catalog.batch-size:100}") int batchSize) {
        this.region = region;
        this.batchSize = batchSize;
    }
}
```

Hai giá trị trên dùng hai cơ chế khác nhau:

```text
${catalog.batch-size:100}
→ resolve property placeholder

#{systemProperties['user.region'] ?: 'global'}
→ evaluate SpEL expression
```

Bean-expression environment còn cung cấp sẵn các context object hữu ích như `environment`, `systemProperties` và `systemEnvironment`. Bean reference có thể dùng `@beanName` khi bean resolver của container tham gia.

`@Value` dùng được trên field, method và constructor/method parameter. Với cấu hình bắt buộc, constructor parameter thường tốt hơn vì giúp object bất biến và làm dependency về cấu hình hiển thị rõ.

Có một lưu ý lifecycle quan trọng: `@Value` được xử lý bởi `BeanPostProcessor`. Code bản thân đang đóng vai trò `BeanPostProcessor` hoặc `BeanFactoryPostProcessor` không nên trông chờ vào `@Value` injection thông thường để bootstrap chính hạ tầng đó.

Chỉ dùng SpEL khi giá trị thực sự cần đánh giá biểu thức. Với một giá trị bên ngoài trực tiếp, `${...}` dễ đọc, kiểm tra và ghi đè hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boot-config-boundary">Ranh giới với Spring Boot externalized configuration</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework cung cấp các nền tảng được học trong chương này:

```text
Environment
PropertySource / MutablePropertySources
profiles và @Profile
@PropertySource
placeholder resolution infrastructure
SpEL
```

Spring Boot xây một hệ thống cấu hình ứng dụng trên các cơ chế nền đó. Boot chịu trách nhiệm về cách tìm và nạp Config Data location, cách `application.properties` hoặc YAML tham gia, thứ tự ưu tiên riêng của Boot và structured binding như `@ConfigurationProperties`.

Quan hệ có thể hình dung như sau:

```text
Boot configuration loading/binding
        ↓ nạp vào / cấu hình
Spring Environment + PropertySources
        ↓ được dùng bởi
Framework container và bean ứng dụng
```

Khi chẩn đoán ở mức Spring Framework, hãy hỏi key đang nằm trong `PropertySource` nào và độ ưu tiên của source đó ra sao. Khi câu hỏi là vì sao Boot nạp một file, tài liệu riêng theo profile, import hoặc object cấu hình có cấu trúc cụ thể, đó là bài toán của Spring Boot externalized configuration.

Vì vậy module này dùng các `PropertySource` đơn giản để dạy cơ chế và dừng trước Config Data hay ngữ nghĩa của `@ConfigurationProperties`.

</details>

- [Quay lại đầu trang](#back-to-top)
