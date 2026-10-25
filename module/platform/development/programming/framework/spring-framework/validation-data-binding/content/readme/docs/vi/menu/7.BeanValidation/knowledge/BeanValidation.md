<a id="back-to-top"></a>

# Tích hợp Jakarta Bean Validation

## Menu
- [Trách nhiệm của Spring và Jakarta Bean Validation](#bean-validation-boundary)
- [SpringValidatorAdapter](#spring-validator-adapter)
- [LocalValidatorFactoryBean](#local-validator-factory-bean)
- [Validation Group và Spring Hints](#validation-groups-hints)
- [Executable Method Validation](#method-validation-model)
- [MethodValidator, MethodValidationAdapter và mô hình kết quả](#method-validation-adapter)
- [MethodValidationPostProcessor và ranh giới AOP](#method-validation-post-processor)

## <a id="bean-validation-boundary">Trách nhiệm của Spring và Jakarta Bean Validation</a>

<details>
<summary>Xem chi tiết</summary>

Jakarta Bean Validation định nghĩa một đặc tả riêng: các constraint annotation như `@NotNull`, hợp đồng `jakarta.validation.Validator`, groups, cascade bằng `@Valid`, executable validation và hành vi của provider. Spring không định nghĩa lại các quy tắc đó. Vai trò của Spring là tích hợp Bean Validation provider với `Validator`, `Errors`, `DataBinder`, vòng đời bean và hạ tầng method validation của Spring.

```java
record RegistrationCommand(
    @jakarta.validation.constraints.NotBlank String username,
    @jakarta.validation.constraints.Min(18) int age) {
}
```

Các annotation trên thuộc Jakarta Bean Validation. Phần Spring bổ sung là khả năng chạy provider qua hạ tầng Spring và chuyển violation sang cách biểu diễn lỗi quen thuộc của Spring khi phù hợp.

Khi phân tích hành vi, cần giữ ranh giới này rõ. `@Min` chấp nhận giá trị nào, cascading được đánh giá ra sao hay custom constraint được provider xử lý thế nào thuộc đặc tả/provider Bean Validation. Cách kết quả đó đi vào `Errors`, `DataBinder` hoặc Spring method validation mới thuộc module này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-validator-adapter">SpringValidatorAdapter</a>

<details>
<summary>Xem chi tiết</summary>

`SpringValidatorAdapter` là cầu nối giữa Jakarta Bean Validation API và mô hình validation của Spring. Nó bao một `jakarta.validation.Validator`, triển khai Spring `SmartValidator`, đồng thời vẫn cung cấp các thao tác của Jakarta `Validator`.

Khi được dùng qua các method `validate(...)` của Spring, adapter yêu cầu Bean Validation provider tạo `ConstraintViolation` rồi chuyển chúng vào Spring `Errors`. Property violation trở thành lỗi gắn với field khi Spring phân giải được field path; violation ở mức object vẫn là object error. Lỗi sau khi chuyển đổi giữ code, arguments, rejected value và thông tin provider cần cho các bước sau.

```java
jakarta.validation.Validator jakartaValidator = validatorFactory.getValidator();
SpringValidatorAdapter springValidator =
    new SpringValidatorAdapter(jakartaValidator);

BeanPropertyBindingResult errors =
    new BeanPropertyBindingResult(command, "command");
springValidator.validate(command, errors);
```

Adapter hữu ích khi hạ tầng ứng dụng đã làm việc với Spring `Validator`/`Errors` nhưng quy tắc được khai báo bằng Jakarta constraint annotation. Nó là lớp tích hợp, không phải một cách triển khai thứ hai của đặc tả constraint.

### Tài liệu tham khảo

- Spring Framework 6.1.14 API — `SpringValidatorAdapter`

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="local-validator-factory-bean">LocalValidatorFactoryBean</a>

<details>
<summary>Xem chi tiết</summary>

`LocalValidatorFactoryBean` là điểm khởi tạo trung tâm của Spring cho Jakarta Bean Validation trong `ApplicationContext`. Nó xây `ValidatorFactory` nền tảng và cung cấp validator mặc định qua ba góc nhìn hữu ích: Spring `Validator`/`SmartValidator`, Jakarta `Validator` và chính Jakarta `ValidatorFactory`.

```java
@Bean
LocalValidatorFactoryBean validator() {
    return new LocalValidatorFactoryBean();
}
```

Vai trò kép này cho phép cùng một cấu hình provider tham gia `DataBinder` validation và cũng được tiêm vào nơi cần Jakarta API gốc. Spring còn cho phép cấu hình provider class, XML mapping, parameter-name discovery, validation properties, message interpolation và `ConstraintValidatorFactory`.

Mặc định Spring dùng `SpringConstraintValidatorFactory`, nhờ đó constraint validator có thể được tạo qua Spring `BeanFactory`. Custom `ConstraintValidator` vì thế có thể nhận dependency từ Spring mà không làm thay đổi quy tắc của Bean Validation.

`setValidationMessageSource(...)` có thể nối provider message interpolation với Spring `MessageSource`. Điểm tích hợp nằm ở đây, còn cấu hình `MessageSource` và hành vi i18n tổng quát vẫn thuộc Core Container.

### Tài liệu tham khảo

- Spring Framework 6.1.14 API — `LocalValidatorFactoryBean`

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="validation-groups-hints">Validation Group và Spring Hints</a>

<details>
<summary>Xem chi tiết</summary>

Bean Validation groups cho phép cùng một kiểu có constraint tham gia nhiều tình huống validation. Spring nối mô hình đó với `SmartValidator` hints: khi validator Spring dựa trên Bean Validation nhận hint dạng `Class<?>`, các class đó có thể được dùng làm validation group.

```java
interface Create {}
interface Update {}

DataBinder binder = new DataBinder(command, "command");
binder.addValidators(localValidatorFactoryBean);
binder.validate(Create.class);
```

Với method validation do Spring điều khiển, `@Validated` có thể khai báo group ở mức type. Trong hạ tầng method validation của Spring 6.1, việc xác định group còn có thể xem xét `@Validated` metadata áp dụng cho method/type theo quy tắc của method validator.

Với validation dựa trên proxy qua `MethodValidationPostProcessor`, cần tách rõ việc **kích hoạt** khỏi việc **chọn group**: target class vẫn cần `@Validated` ở mức type (hoặc annotation tương đương đã được cấu hình) để khớp validation pointcut. `@Validated` ở mức method có thể override validation group cho method đó, nhưng tự nó không kích hoạt proxy-based method validation cho bean.

Group nên mô tả các tình huống validation thực sự khác nhau, không nên thay thế cho input type rõ ràng. Nếu luồng create và update cung cấp dữ liệu rất khác nhau, tách command model thường dễ hiểu hơn một ma trận group lớn.

Cũng cần phân biệt **group** với `@Valid`: `@Valid` yêu cầu cascaded validation cho object liên quan, còn group chọn constraint nào tham gia một lần validation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-validation-model">Executable Method Validation</a>

<details>
<summary>Xem chi tiết</summary>

Object validation hỏi một object có thỏa constraint hay không. Executable method validation hỏi **ranh giới lời gọi method** có thỏa constraint trên tham số và giá trị trả về hay không. Bean Validation định nghĩa quy tắc executable validation; Spring 6.1 bổ sung mô hình kết quả cấp framework giàu thông tin hơn xung quanh các quy tắc đó.

```java
public interface PricingService {
    @jakarta.validation.constraints.Positive
    BigDecimal quote(
        @jakarta.validation.constraints.NotBlank String sku,
        @jakarta.validation.constraints.Positive int quantity);
}
```

Constraint trên tham số được kiểm tra trên các đối số chuẩn bị truyền vào method. Constraint trên giá trị trả về được kiểm tra trên kết quả method tạo ra. Cascaded validation có thể kiểm tra object tham số/giá trị trả về khi có Jakarta `@Valid`.

Điều này khác việc tự gọi `Validator` bên trong thân method. Method validation coi chính ranh giới có thể gọi là đối tượng validation và có thể được hạ tầng áp dụng nhất quán. Nó cũng khác MVC/WebFlux controller argument binding; vòng đời phụ thuộc web vẫn thuộc module web/reactive dù tái sử dụng cùng hợp đồng validation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-validation-adapter">MethodValidator, MethodValidationAdapter và mô hình kết quả</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework 6.1 bổ sung abstraction `MethodValidator` và `MethodValidationAdapter` để cung cấp kết quả method validation theo mô hình Spring thay vì bắt mọi bên gọi làm việc trực tiếp với tập `ConstraintViolation` thô.

`MethodValidationAdapter` dùng Jakarta Bean Validation `Validator` bên dưới. Các thao tác `validateArguments(...)` và `validateReturnValue(...)` tạo `MethodValidationResult`. Kết quả được tổ chức theo tham số method hoặc giá trị trả về với thông tin lỗi dạng Spring `MessageSourceResolvable`; cascaded violation trên object có thể được biểu diễn qua cấu trúc lỗi theo tham số mang các lỗi theo kiểu binding.

```java
MethodValidationAdapter adapter = new MethodValidationAdapter(jakartaValidator);
Class<?>[] groups = adapter.determineValidationGroups(target, method);

MethodValidationResult result = adapter.validateArguments(
    target, method, parameters, arguments, groups);

if (result.hasErrors()) {
    result.getAllValidationResults()
        .forEach(System.out::println);
}
```

Adapter có thể dùng `MessageCodesResolver` và `ParameterNameDiscoverer` để violation có Spring-style code ổn định và định danh tham số có nghĩa. Việc chọn group được xác định riêng với bước validation, giúp mô hình kết quả không phụ thuộc cách validation được kích hoạt.

Mô hình này phù hợp khi hạ tầng cần bằng chứng method validation có cấu trúc. Code ứng dụng thông thường thường để hạ tầng Spring cấp cao hơn áp dụng validation và xử lý lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-validation-post-processor">MethodValidationPostProcessor và ranh giới AOP</a>

<details>
<summary>Xem chi tiết</summary>

`MethodValidationPostProcessor` là cơ chế nối dây cấp container thuận tiện của Spring cho method validation dựa trên proxy. Nó tìm bean đủ điều kiện, mặc định dựa trên `@Validated` ở mức type, rồi cài method-validation advice để chuyển việc kiểm tra sang Jakarta Bean Validation provider.

```java
@Configuration
class ValidationConfig {
    @Bean
    static MethodValidationPostProcessor methodValidationPostProcessor() {
        MethodValidationPostProcessor processor =
            new MethodValidationPostProcessor();
        processor.setAdaptConstraintViolations(true);
        return processor;
    }
}

@Validated
class PricingServiceImpl implements PricingService {
    // constrained methods
}
```

Trong Spring Framework 6.1.14, cách báo lỗi mặc định là Jakarta `ConstraintViolationException`. Khi bật `setAdaptConstraintViolations(true)`, violation được chuyển sang `MethodValidationResult` của Spring và được ném qua `MethodValidationException`.

Ranh giới quan trọng là **cơ chế nối dây khác cơ chế proxy**. Chương này giải thích vì sao dùng post-processor, bean nào đủ điều kiện, cách chọn group và mô hình kết quả/exception của validation. Cách Spring proxy chặn lời gọi, advisor ordering, proxy type hay self-invocation thuộc module Aspect.

### Tài liệu tham khảo

- Spring Framework 6.1.14 API — `MethodValidationPostProcessor`

</details>

- [Quay lại đầu trang](#back-to-top)
