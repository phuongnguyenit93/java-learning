---
video:
  url: ""
---

# Tích hợp Jakarta Bean Validation

<!--
VIDEO SCRIPT FORMAT

Section rules:
- Each H2 is one video section/chapter.
- The first section requires at least 1 Scene.
- From the second section onward, each section requires at least 1 Transition + 1 Scene.
- Step 6 may add more Scenes/Transitions when the Knowledge content needs them.

TRANSITION FORMAT

### Transition

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe the transition, title card, or screen change.

**Script:**

Write the short bridge from the previous section to the current section.

**Purpose:**

Explain why this transition exists.

SCENE FORMAT

### Scene N — <optional scene title>

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe what the viewer sees: slide, diagram, source code, terminal, API request/response, runtime output, highlight, or callout.

**Script:**

Write the narration/presentation script for this scene.

**Purpose:**

Explain what this scene teaches, demonstrates, or proves.
-->

## Trách nhiệm của Spring và Jakarta Bean Validation

<!-- VIDEO_SECTION -->

### Scene 1 — Semantics thuộc specification, integration thuộc Spring

**Time:** `00:00–01:05`

**Visual:**

Đặt Jakarta Bean Validation bên trái với `@NotBlank`, `@Min`, groups, `@Valid` và executable validation. Bên phải là Spring với `Validator`, `Errors`, `DataBinder`, bean lifecycle và method-validation infrastructure. Vẽ các mũi tên integration giữa hai phía.

**Script:**

Jakarta Bean Validation định nghĩa constraint model: annotation, Jakarta validator API, groups, cascading, executable validation và behavior của provider. Spring không định nghĩa lại những semantics đó. Spring tích hợp provider với `Validator`, `Errors`, DataBinder, ApplicationContext và hạ tầng method validation của mình. Khi reasoning về behavior, hãy giữ ownership rõ: constraint có hợp lệ theo specification hay không thuộc Jakarta và provider; violation đi vào Spring error model như thế nào thuộc integration layer ở đây.

**Purpose:**

Đặt ranh giới giữa semantics của Bean Validation specification và phần tích hợp do Spring cung cấp.

## SpringValidatorAdapter

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:05–01:15`

**Visual:**

Phóng to mũi tên integration và đặt nhãn `SpringValidatorAdapter`.

**Script:**

Bridge đầu tiên cho phép hạ tầng đã dùng Spring `Validator` và `Errors` tiêu thụ trực tiếp kết quả từ Jakarta constraints.

**Purpose:**

Nối ownership boundary sang adapter chuyển provider violation thành Spring validation model.

### Scene 1 — Adapt ConstraintViolation thành Errors

**Time:** `01:15–02:15`

**Visual:**

Hiển thị Jakarta `Validator` được bọc bởi `SpringValidatorAdapter`. Cho constrained command đi vào `validate(command, errors)`, rồi animate `ConstraintViolation` thành Spring field error và object error.

**Script:**

`SpringValidatorAdapter` bọc Jakarta `Validator`, implement Spring `SmartValidator` và đồng thời expose các operation của Jakarta Validator. Khi dùng qua Spring validation methods, adapter gọi provider để lấy constraint violation rồi chuyển chúng vào `Errors`. Violation gắn với property có thể trở thành field error; violation ở mức object vẫn là object error. Đây là integration bridge, không phải một implementation thứ hai của Bean Validation.

**Purpose:**

Cho thấy evidence từ Jakarta constraints được chuyển sang structured error model của Spring như thế nào.

## LocalValidatorFactoryBean

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:15–02:25`

**Visual:**

Zoom out từ một adapter instance thành ApplicationContext có một `LocalValidatorFactoryBean`.

**Script:**

Adapter giải quyết một bridge. Ứng dụng còn cần một nơi bootstrap và cấu hình provider sao cho dùng tự nhiên trong Spring container.

**Purpose:**

Chuyển từ adapter đơn lẻ sang Bean Validation bootstrap thân thiện với ApplicationContext.

### Scene 1 — Một bootstrap, nhiều interface sử dụng

**Time:** `02:25–03:30`

**Visual:**

Hiển thị `@Bean LocalValidatorFactoryBean` tỏa ra ba view: Spring `Validator/SmartValidator`, Jakarta `Validator` và `ValidatorFactory`. Thêm callout cho `SpringConstraintValidatorFactory` và `setValidationMessageSource`.

**Script:**

`LocalValidatorFactoryBean` là bootstrap trung tâm của Spring cho Jakarta Bean Validation. Cùng một instance đã cấu hình có thể được nhìn như Spring Validator, Jakarta Validator hoặc ValidatorFactory. Mặc định Spring có thể tạo constraint validator qua `BeanFactory`, nhờ vậy custom `ConstraintValidator` có thể nhận dependency injection mà không thay đổi validation semantics. Nó cũng có thể nối message interpolation của provider với Spring `MessageSource`.

**Purpose:**

Giải thích vì sao LocalValidatorFactoryBean là điểm tích hợp chính giữa provider bootstrap và Spring container services.

## Validation Group và Spring Hints

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:30–03:40`

**Visual:**

Tách cùng một constrained command thành hai scenario Create và Update.

**Script:**

Cùng một type có thể cần rule set khác nhau theo scenario. Bean Validation gọi đó là group, còn Spring có thể truyền group qua hint.

**Purpose:**

Nối Bean Validation groups với SmartValidator hints đã học ở chapter trước.

### Scene 1 — Chọn scenario mà không đổi object type

**Time:** `03:40–04:40`

**Visual:**

Hiển thị `binder.validate(Create.class)` đi qua Bean Validation-backed `SmartValidator`. So sánh `Create.class` và `Update.class`, rồi đặt `@Valid` ở nhánh riêng có nhãn “cascade”.

**Script:**

Khi Spring gọi một `SmartValidator` dựa trên Bean Validation, class-valued hint có thể được dùng làm validation group. Caller vì vậy chọn được rule set theo create hay update mà không phải đổi target type. Group nên thể hiện scenario thật; nếu hai workflow có input shape rất khác nhau thì tách command model có thể đơn giản hơn. Và `@Valid` không phải group: nó yêu cầu cascaded validation cho object liên quan.

**Purpose:**

Làm rõ quan hệ giữa Spring hints, Bean Validation groups và cascaded validation.

## Executable Method Validation

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:40–04:50`

**Visual:**

Chuyển các constraint annotation từ field của object sang parameter và return value của một service method.

**Script:**

Cho tới đây target của validation chủ yếu là object. Bean Validation còn có thể xem chính method invocation boundary là target cần kiểm tra.

**Purpose:**

Chuyển mental model từ object validation sang validation parameter và return value.

### Scene 1 — Validate ngay tại call boundary

**Time:** `04:50–05:50`

**Visual:**

Hiển thị `PricingService.quote(@NotBlank sku, @Positive quantity)` và `@Positive` trên return value. Animate parameter được kiểm tra trước invocation, return value sau invocation, và `@Valid` cascade vào object parameter.

**Script:**

Executable validation kiểm tra constraint trên method parameter và return value. Parameter constraint áp vào argument sắp được truyền; return-value constraint áp vào kết quả method vừa tạo. `@Valid` cho phép cascade vào object parameter hoặc return value. Đây không phải việc đặt một validator call bên trong method body. Call boundary tự nó là validation target, nên infrastructure có thể áp policy nhất quán.

**Purpose:**

Định nghĩa executable method validation và phân biệt nó với object validation hoặc validation thủ công trong method.

## MethodValidator, MethodValidationAdapter và mô hình kết quả

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:50–06:00`

**Visual:**

Đưa raw method constraint violations qua `MethodValidationAdapter` rồi chuyển thành sơ đồ structured result.

**Script:**

Raw violation cho biết constraint nào fail, nhưng Spring 6.1 bổ sung model tổ chức lỗi theo parameter và return value của method.

**Purpose:**

Nối executable-validation semantics của Jakarta sang structured result model của Spring.

### Scene 1 — Adapt lỗi invocation thành Spring result

**Time:** `06:00–07:05`

**Visual:**

Hiển thị `determineValidationGroups`, `validateArguments` và `validateReturnValue` tạo `MethodValidationResult`. Mở một parameter result thành message-resolvable errors và cascaded object-error structure.

**Script:**

Spring Framework 6.1 giới thiệu `MethodValidator` và `MethodValidationAdapter`. Adapter vẫn dùng Jakarta Validator bên dưới nhưng trả về `MethodValidationResult` theo model của Spring. Lỗi được tổ chức quanh parameter hoặc return value tương ứng và có thể cung cấp `MessageSourceResolvable`; violation từ object cascade cũng có thể xuất hiện dưới dạng cấu trúc gần với binding error. Cách này hữu ích khi infrastructure cần structured evidence thay vì chỉ một set `ConstraintViolation`.

**Purpose:**

Cho thấy Spring 6.1 bổ sung cấu trúc kết quả, parameter identity và message metadata quanh executable validation.

## MethodValidationPostProcessor và ranh giới AOP

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:05–07:15`

**Visual:**

Bọc validated service bằng outline Spring proxy và đặt `MethodValidationPostProcessor` trong container configuration.

**Script:**

Result model cần được áp đúng call boundary. Spring cung cấp một post-processor để cài method-validation advice vào bean phù hợp.

**Purpose:**

Nối method-validation result model sang component container cài runtime interception.

### Scene 1 — Wiring ở đây, proxy mechanics thuộc Aspect

**Time:** `07:15–08:25`

**Visual:**

Hiển thị `MethodValidationPostProcessor` phát hiện bean có type-level `@Validated` rồi cài advice. So sánh default `ConstraintViolationException` với `setAdaptConstraintViolations(true)` tạo `MethodValidationException` gắn với Spring result model. Fade phần proxy internals sang boundary “Aspect”.

**Script:**

`MethodValidationPostProcessor` tìm bean đủ điều kiện, mặc định qua `@Validated` ở type level, rồi cài advice dùng Bean Validation provider. Trong Spring Framework 6.1.14, failure mặc định là Jakarta `ConstraintViolationException`. Khi bật `setAdaptConstraintViolations(true)`, violation được adapt sang result model của Spring và được ném qua `MethodValidationException`. Chapter này sở hữu validation wiring và failure model; advisor ordering, proxy type hay self-invocation thuộc module Aspect.

**Purpose:**

Giải thích behavior của MethodValidationPostProcessor, hai exception mode và boundary rõ ràng với AOP proxy mechanics.
