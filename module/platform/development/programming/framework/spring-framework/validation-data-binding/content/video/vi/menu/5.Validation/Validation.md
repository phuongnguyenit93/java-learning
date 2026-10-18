---
video:
  url: ""
---

# Validation và báo lỗi trong Spring

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

## Hợp đồng Validator và supports(...)

<!-- VIDEO_SECTION -->

### Scene 1 — Rule object và error collector

**Time:** `00:00–01:05`

**Visual:**

Hiển thị `AccountValidator` nhỏ với `supports(Account.class)` ở bên trái và `validate(account, errors)` ở bên phải. Animate rule username/age không hợp lệ thành các lệnh `errors.rejectValue(...)` thay vì exception.

**Script:**

Spring `Validator` giúp rule validation không bị phụ thuộc vào HTTP, UI hay persistence. Contract này chỉ có hai trách nhiệm chính. `supports` cho biết validator có phù hợp với target type hay không. `validate` kiểm tra object và ghi lỗi bình thường vào `Errors`. Hãy xem nó như “rule object cộng với error collector”: type compatibility ở `supports`, còn business rule nằm trong `validate`.

**Purpose:**

Đặt nền tảng cho Spring Validator và tách kiểm tra type compatibility khỏi validation rule thực sự.

## Errors là nơi thu thập lỗi Validation

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:05–01:15`

**Visual:**

Chuyển focus từ validator sang tham số `Errors`, rồi mở rộng thành `BindingResult` khi có ngữ cảnh binding.

**Script:**

Validator quyết định điều gì không hợp lệ; bước tiếp theo là giữ các lỗi đó dưới dạng dữ liệu đủ giàu để tầng sau còn sử dụng được.

**Purpose:**

Nối rule evaluation sang structured collector dùng chung cho Validation và Binding.

### Scene 1 — Một result giữ được hai giai đoạn lỗi

**Time:** `01:15–02:20`

**Visual:**

Chạy evidence thật của module: bind `age="15"`, dừng ở `errorsAfterBinding=0`, sau đó chạy validator và reveal `age.tooYoung`, `errorsAfterValidation=1`, `bindingFailure=false`.

**Script:**

`Errors` là collector chung cho validator, còn `BindingResult` bổ sung thông tin đặc thù của binding trên cùng error model đó. Trong demo thật của module, “15” chuyển thành integer thành công nên sau binding chưa có lỗi. Chỉ khi `binder.validate()` chạy thì `age.tooYoung` mới xuất hiện, và `bindingFailure=false`. Nhờ structured result, ta biết lỗi đến từ validation chứ không phải conversion hay property assignment.

**Purpose:**

Dùng evidence runtime thật để cho thấy BindingResult mở rộng Errors mà vẫn giữ được nguồn gốc của failure.

## Validation cho object lồng nhau

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:20–02:30`

**Visual:**

Mở một object phẳng thành `Order` có `shippingAddress` lồng bên trong.

**Script:**

Object thật thường có cấu trúc lồng nhau. Khi child object sở hữu rule riêng, error path cũng cần đi theo object graph đó.

**Purpose:**

Chuyển từ top-level validation sang nested validation nhưng vẫn giữ field identity.

### Scene 1 — Delegate mà không làm mất path

**Time:** `02:30–03:35`

**Visual:**

Hiển thị `pushNestedPath("shippingAddress")`, gọi `AddressValidator`, rồi `popNestedPath()` trong `finally`. Animate `rejectValue("street", ...)` thành `shippingAddress.street`.

**Script:**

Nested validation dùng path cursor có thể thay đổi trong `Errors`. Parent validator push `shippingAddress`, giao phần rule địa chỉ cho `AddressValidator`, rồi phải khôi phục path trong `finally`. Trong khoảng đó, lỗi của `street` sẽ được ghi thành `shippingAddress.street`. Rule nào thật sự thuộc child thì delegate; invariant cần nhiều object nên ở validator có đủ context để đánh giá.

**Purpose:**

Minh họa cách compose nested validator an toàn và lý do phải luôn khôi phục nested path.

## ValidationUtils là hạ tầng hỗ trợ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:35–03:45`

**Visual:**

Thu các đoạn check null/blank lặp lại thành hai helper call nhỏ có nhãn `ValidationUtils`.

**Script:**

Một số rule rất nhỏ lặp lại nhiều lần. Spring có helper để giảm ceremony, nhưng helper đó không phải validation model mới.

**Purpose:**

Đặt ValidationUtils đúng vai trò infrastructure hỗ trợ.

### Scene 1 — Helper cho thao tác phổ biến

**Time:** `03:45–04:40`

**Visual:**

Hiển thị `rejectIfEmptyOrWhitespace`, `rejectIfEmpty` và `invokeValidator`. Highlight bước `supports` được kiểm tra trước khi delegate.

**Script:**

`ValidationUtils` phù hợp với các thao tác nhỏ và quen thuộc như reject field rỗng, chỉ chứa whitespace, hoặc gọi validator khác với compatibility check chuẩn. Nó cũng có thể truyền hint sang `SmartValidator`. Nhưng khi rule cần nhiều field, branching hay ngôn ngữ domain rõ ràng, Java code thông thường trong validator sẽ dễ đọc và test hơn.

**Purpose:**

Cho thấy khi nào ValidationUtils giúp giảm boilerplate và khi nào nên quay về rule code rõ ràng.

## SmartValidator và Validation Hints

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:40–04:50`

**Visual:**

Thêm input thứ ba cho lời gọi validation mang nhãn “hints”, với thẻ `RegistrationChecks.class`.

**Script:**

Cùng một target type đôi khi có nhiều scenario validation. Spring truyền ngữ cảnh bổ sung đó dưới dạng hint.

**Purpose:**

Nối Validator cơ bản sang contextual validation.

### Scene 1 — Thêm ngữ cảnh mà không đổi target type

**Time:** `04:50–05:50`

**Visual:**

Hiển thị `binder.validate(RegistrationChecks.class)` đi vào `SmartValidator`. Sau đó show Bean Validation adapter diễn giải class hint thành validation group, kèm note validator khác có thể có semantics khác hoặc bỏ qua hint.

**Script:**

`SmartValidator` mở rộng contract bằng `validate(Object, Errors, Object... hints)`. Spring không gán một ý nghĩa duy nhất cho mọi hint; concrete validator quyết định. Với adapter dựa trên Jakarta Bean Validation, class hint thường được dùng như validation group. Như vậy caller có thể yêu cầu rule set theo scenario mà không đổi target type. Hãy chỉ phụ thuộc vào semantics của hint khi validator cụ thể đã định nghĩa rõ.

**Purpose:**

Giải thích validation hint, cách Bean Validation thường dùng nó và giới hạn của contract.

## Phân giải Message Code

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:50–06:00`

**Visual:**

Từ code ổn định `tooYoung`, bung ra danh sách candidate từ cụ thể tới tổng quát.

**Script:**

Error đã có cấu trúc, nhưng validator vẫn chưa nên hard-code câu chữ cuối cùng cho người dùng. Ta cần một lớp phân giải code trước khi localize.

**Purpose:**

Nối structured error sang hệ thống message-code candidate.

### Scene 1 — Ưu tiên cụ thể rồi fallback dần

**Time:** `06:00–07:00`

**Visual:**

Reveal lần lượt `tooYoung.account.age`, `tooYoung.age`, `tooYoung.int`, `tooYoung`. Sau đó cho `typeMismatch` đi qua cùng resolver.

**Script:**

`MessageCodesResolver` mở rộng một code ổn định thành chuỗi candidate có thứ tự. Field error có thể thử code gắn với object và field trước, rồi fallback về field, type và code tổng quát. Nhờ vậy validator độc lập với locale, còn application vẫn có thể custom message ở mức chi tiết phù hợp. Binding error như `typeMismatch` cũng tham gia cùng strategy này.

**Purpose:**

Cho thấy cách stable error code trở thành lookup hierarchy phục vụ localization mà không làm thay đổi validation logic.

## Ranh giới MessageSource và i18n

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:00–07:10`

**Visual:**

Đưa danh sách message-code candidate qua một đường boundary sang hộp `MessageSource` thuộc Core Container.

**Script:**

Resolver mới chỉ chuẩn bị code. Biến code đó thành text theo locale là trách nhiệm của owner tiếp theo.

**Purpose:**

Đánh dấu điểm bàn giao từ structured error model của module này sang localization của Core Container.

### Scene 1 — Module này dừng ở resolvable error

**Time:** `07:10–08:10`

**Visual:**

Hiển thị pipeline `Validator/DataBinder -> ObjectError/FieldError -> codes + arguments -> MessageSource -> localized text`. Thêm nhánh `LocalValidatorFactoryBean.setValidationMessageSource(...)` nối sang cùng MessageSource.

**Script:**

Module này kết thúc ở structured error, message-code candidate và arguments. `MessageSource` chịu trách nhiệm đổi chúng thành text theo locale, và lifecycle rộng hơn đó thuộc Core Container. `LocalValidatorFactoryBean` có thể nối message interpolation của Bean Validation sang Spring `MessageSource`, nhưng đó là integration bridge chứ không làm ownership i18n chuyển sang validation. MVC và WebFlux tiếp tục quyết định lỗi được render ra transport như thế nào.

**Purpose:**

Chốt ranh giới i18n đồng thời chỉ ra điểm tích hợp hợp lệ với Bean Validation.
