---
video:
  url: ""
---

# DataBinder và luồng điều phối Binding

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

## DataBinder điều phối những gì?

<!-- VIDEO_SECTION -->

### Scene 1 — Bộ điều phối, không phải owner của mọi rule

**Time:** `00:00–01:05`

**Visual:**

Animate một hộp input values đi qua field policy, conversion/formatting/property access, target state, `BindingResult`, rồi tới validator tùy chọn. Giữ `DataBinder` như khung bao quanh flow, không biến nó thành từng component bên trong.

**Script:**

`DataBinder` là bộ điều phối reusable kết nối các cơ chế đã học. Nó nhận mô hình input, áp dụng binding policy, giao việc chuyển đổi và property access cho đúng abstraction, cập nhật hoặc tạo typed state, rồi gom failure vào `BindingResult`. Validator có thể tiếp tục thêm domain error vào cùng result đó. DataBinder điều phối cả flow, nhưng converter, property accessor và validator vẫn sở hữu rule riêng của chúng.

**Purpose:**

Tạo mental model thống nhất cho DataBinder mà vẫn giữ rõ boundary trách nhiệm của các thành phần bên trong.

## Luồng Binding từ dữ liệu đầu vào tới BindingResult

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:05–01:15`

**Visual:**

Phóng to nhánh property binding, đặt `bind(PropertyValues)` ở đầu vào và `getBindingResult()` ở đầu ra.

**Script:**

Khi đã thấy toàn bộ bộ điều phối, ta có thể theo dõi một lần bind thật từ input tới evidence cuối cùng.

**Purpose:**

Chuyển từ sơ đồ trách nhiệm của DataBinder sang workflow bind và đọc BindingResult.

### Scene 1 — Type mismatch trở thành structured evidence

**Time:** `01:15–02:25`

**Visual:**

Chạy scenario binding failure trong experiment thật `/validation-binding/validation/binding-vs-validation`. Hiển thị `age="not-a-number"` đi vào property `int` có giá trị ban đầu `41`, rồi dừng trên response: `targetAgeBeforeBinding=41`, `targetAgeAfterBinding=41`, `targetAgeUnchanged=true`, `hasErrors=true`, type-mismatch code, rejected value `not-a-number`, `bindingFailure=true`.

**Script:**

Với property binding, `bind(PropertyValues)` là entry point chính. Trong experiment này, text không thể chuyển thành integer. Spring không giả vờ assignment thành công mà ghi một `FieldError` vào binding result. Giá trị age của target không đổi, rejected value vẫn được giữ và `bindingFailure` bằng true. Đó mới là evidence cần đọc. Nhìn một target bị cập nhật một phần không đủ để kết luận toàn bộ input đã được chấp nhận.

**Purpose:**

Dùng runtime evidence thật để chứng minh flow từ bind tới BindingResult và ý nghĩa của conversion failure trực tiếp.

## Property và Setter Binding

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:25–02:35`

**Visual:**

Thay input lỗi bằng một command object mutable; các setter writable lần lượt sáng lên như những binding target tiềm năng.

**Script:**

Experiment vừa rồi dùng property binding mặc định, tức là áp input lên một object đã tồn tại.

**Purpose:**

Nối binding flow tổng quát sang semantics và input surface của property/setter binding.

### Scene 1 — Object đã có sẵn và bề mặt writable

**Time:** `02:35–03:35`

**Visual:**

Hiển thị `AccountForm` trước khi bind rồi áp `name=Ada` và `age=37` qua JavaBean property. Highlight `setAllowedFields("name", "age")`, sau đó thoáng hiển thị `initDirectFieldAccess()` như một mode khác có chủ đích.

**Script:**

Property binding bắt đầu với target đã có và áp value qua writable property, mặc định theo JavaBean setter semantics. Nested path và conversion tham gia khi cần. Vì object đã tồn tại, một số field có thể được gán thành công trước khi field khác fail; do đó `BindingResult` mới phản ánh toàn bộ operation. Direct field access cũng tồn tại, nhưng nó thay đổi khái niệm “writable” và có thể bỏ qua setter behavior nên phải là lựa chọn có chủ đích.

**Purpose:**

Giải thích property binding mutate state hiện có và vì sao writable surface cùng non-atomic assignment cần được xem xét.

## Constructor Binding

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:35–03:45`

**Visual:**

Fade target mutable ra khỏi màn hình và thay bằng các constructor parameter đang chờ value trước khi object được tạo.

**Script:**

Property binding bắt đầu từ một object. Constructor binding đảo thứ tự đó: input phải được resolve trước khi target tồn tại.

**Purpose:**

Đối chiếu mutation của target hiện có với việc tạo object từ constructor arguments.

### Scene 1 — Input surface được thể hiện qua constructor

**Time:** `03:45–04:50`

**Visual:**

Hiển thị `new DataBinder(null, "account")`, `setTargetType(...)` và `construct(ValueResolver)`. Animate resolver chỉ được hỏi những constructor parameter cần thiết, sau đó reveal target từ `BindingResult.getTarget()`.

**Script:**

Trong Spring Framework 6.1, `DataBinder` hỗ trợ constructor binding qua `construct(ValueResolver)`. Binder biết target type, hỏi resolver các value mà constructor cần, thực hiện conversion rồi đưa object đã tạo vào binding result. Cách này phù hợp với immutable hoặc purpose-built input model vì accepted shape nằm ngay trong construction contract. Những setting như unknown field hay allowed field là policy của property binding; chúng không định nghĩa lại constructor parameter.

**Purpose:**

Minh họa lifecycle constructor binding và phân biệt input surface của nó với field policy của property binding.

## Declarative Binding trong Spring Framework 6.1

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:50–05:00`

**Visual:**

Đặt constructor binding và property binding cạnh nhau, sau đó thêm switch `declarativeBinding=true` trên nhánh property binding.

**Script:**

Spring 6.1 thêm một setting thay đổi default posture của property binding, trong khi constructor binding vẫn hoạt động.

**Purpose:**

Giới thiệu declarative binding như input-surface policy rõ ràng, không phải validation feature.

### Scene 1 — Opt in cho property được phép ghi

**Time:** `05:00–06:10`

**Visual:**

Chạy setup thật: `setDeclarativeBinding(true)`, `setAllowedFields("displayName")`, input `displayName=Ada` và `role=ADMIN`. Hiển thị `displayName` được áp dụng, `role` vẫn là `USER`, `suppressedFields=[role]`.

**Script:**

Declarative binding quy định property binding chỉ xảy ra khi allowed fields đã được cấu hình. Trong demo thật, cả display name và role đều được gửi vào nhưng chỉ `displayName` nằm trong allow-list. Vì vậy display name đổi thành Ada, role vẫn là USER và `role` xuất hiện trong suppressed fields. Đây là quyết định về bề mặt được phép ghi trước khi validation chạy. Một giá trị hoàn toàn hợp lệ trong domain vẫn có thể cố ý không được bind từ kênh input này.

**Purpose:**

Dùng runtime evidence để chứng minh contract declarative binding của Spring 6.1 và suppressed-field behavior.

## Required, Unknown, Invalid, Allowed và Disallowed Fields

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:10–06:20`

**Visual:**

Mở rộng màn hình declarative binding thành bảng policy gồm năm dòng: required, unknown, invalid, allowed, disallowed.

**Script:**

Allowed fields kiểm soát writable surface, nhưng DataBinder còn nhiều policy khác và mỗi policy trả lời một câu hỏi riêng.

**Purpose:**

Chuyển từ một safe-binding control sang vocabulary đầy đủ của property-binding policy.

### Scene 1 — Không gom mọi policy thành một “strict mode”

**Time:** `06:20–07:30`

**Visual:**

Đi lần lượt qua bảng: thiếu configured required field -> `required`; unknown field -> bỏ qua mặc định; nested field không truy cập được -> không bỏ qua mặc định; allowed patterns -> bề mặt được phép; disallowed patterns -> bề mặt bị chặn. Đánh dấu cả năm là property-binding policy.

**Script:**

Required field nghĩa là một tên phải có mặt trong lần bind này. Unknown field không có property tương ứng và mặc định được bỏ qua. Invalid field trỏ tới target state hiện không truy cập được và mặc định không bị bỏ qua. Allowed fields tạo positive surface; disallowed fields loại các pattern khỏi surface đó. Các policy có liên quan nhưng không thay thế nhau, và chúng áp dụng cho property binding. Với input không đáng tin cậy, allow-list dễ review hơn vì property mới thêm vào sẽ không tự động trở thành bindable.

**Purpose:**

Gán ý nghĩa riêng cho từng DataBinder field policy và liên hệ allow-list với input-surface control dễ bảo trì.

## Conversion, Custom Editor, Validator và Error Processing

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:30–07:40`

**Visual:**

Biến bảng policy thành các extension socket xung quanh hộp DataBinder.

**Script:**

Sau khi xác định bề mặt binding, ứng dụng vẫn cần những extension point đúng chỗ cho representation, validation và error processing.

**Purpose:**

Nối binding policy sang các extension point chính của DataBinder.

### Scene 1 — Mở rộng đúng trách nhiệm

**Time:** `07:40–08:50`

**Visual:**

Gắn bốn nhãn: `setConversionService`, `registerCustomEditor`, validators, và `BindingErrorProcessor`/MessageCodesResolver. Animate conversion đi vào ConversionService, legacy text editing sang PropertyEditor, domain rule sang validator, missing/property-access failure sang error processor.

**Script:**

`DataBinder` cho phép tùy biến nhưng mỗi hook có công việc riêng. Dùng `ConversionService` cho typed conversion và formatting reusable. Dùng custom `PropertyEditor` chủ yếu để tích hợp legacy code. Validator xử lý domain rule sau binding. `BindingErrorProcessor` quyết định missing required value hay property-access exception trở thành binding error như thế nào; `MessageCodesResolver` mở rộng error code cho bước lookup sau đó. Giữ các vai trò tách biệt giúp failure luôn có thể giải thích được.

**Purpose:**

Khép chapter bằng bản đồ từ nhu cầu tùy biến tới extension point thực sự sở hữu nhu cầu đó.
