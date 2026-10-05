---
video:
  url: ""
---

# Binding an toàn và quyết định End-to-End

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

## Binding Security là bài toán kiểm soát bề mặt Input

<!-- VIDEO_SECTION -->

### Scene 1 — Quyết định input được phép ghi gì trước khi validation

**Time:** `00:00–01:05`

**Visual:**

Hiển thị external input `role=ADMIN` tiến về domain object. Chèn một cổng lớn “binding surface” trước conversion và validation. Sau cổng mới đặt checkpoint validation riêng.

**Script:**

Binding security bắt đầu bằng câu hỏi khác validation: những tên và giá trị nào từ bên ngoài được phép tác động vào object state? Một field như `role` có thể là domain state hoàn toàn hợp lệ nhưng vẫn không nên được mass-assign từ input. Nếu channel này không được phép thay đổi field đó, quyết định an toàn phải xảy ra ở binding surface trước khi validation chạy. Validation kiểm tra state có hợp lệ; binding policy quyết định external input được quyền thử ghi state nào.

**Purpose:**

Đặt Safe Binding là bài toán input surface và tách security boundary khỏi domain correctness.

## Input Model có phạm vi rõ ràng

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:05–01:15`

**Visual:**

Thay rich domain entity bằng một `ProfileUpdateForm` nhỏ chỉ chứa `displayName` và `timezone`.

**Script:**

Cách thu hẹp writable surface mạnh nhất thường là không đưa sensitive member vào input type ngay từ đầu.

**Purpose:**

Chuyển từ surface control khái niệm sang cách giới hạn bề mặt ngay ở thiết kế type.

### Scene 1 — Làm accepted shape nhìn thấy được trong Java

**Time:** `01:15–02:15`

**Visual:**

So sánh domain entity có `role`, `accountStatus`, internal identifiers và profile fields với `ProfileUpdateForm` chỉ expose display name và timezone. Animate generic property binding thử áp lên hai shape.

**Script:**

Command hoặc DTO riêng cho input làm bề mặt binding nhỏ lại trước cả runtime configuration. Nếu `role` và `accountStatus` không tồn tại trên input model, property binding thông thường không thể vô tình ghi chúng. Validation của input cũng dễ review hơn vì command mô tả đúng một use case, còn bước map sang domain object trở thành hành động rõ ràng của application. Ta đổi thêm một chút type và mapping code để lấy một contract dễ nhìn và dễ kiểm tra ở trust boundary.

**Purpose:**

Cho thấy constrained input model tạo guarantee cấu trúc mạnh chống accidental mass assignment.

## Constructor, Declarative và Allowed-Field Controls

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:15–02:25`

**Visual:**

Đặt ba control cạnh input model: constructor shape, switch declarative binding và allowed-field list.

**Script:**

Thiết kế type là lớp đầu tiên. Spring 6.1 còn cung cấp các binder control bổ sung khi runtime binding policy vẫn cần thiết.

**Purpose:**

Nối constrained input model sang constructor, declarative binding và allow-list.

### Scene 1 — Quan sát allow-list chặn một writable field

**Time:** `02:25–03:35`

**Visual:**

Chạy evidence thật `/validation-binding/safe-binding/declarative`. Bắt đầu với `displayName=Before`, `role=USER`; bật declarative binding; chỉ allow `displayName`; gửi `displayName=Ada` và `role=ADMIN`; reveal `displayName=Ada`, `role=USER`, `suppressedFields=[role]`.

**Script:**

Constructor binding chỉ hỏi những value cần cho construction path. Declarative binding chuyển property binding sang tư thế opt-in, còn allowed fields mô tả phần writable còn lại. Demo thật cho thấy cả display name và role đều được gửi, nhưng chỉ display name được phép. Role vẫn là USER và `BindingResult` ghi `role` vào suppressed fields. Allow-list cũng dễ bảo trì hơn deny-list vì một property writable mới không tự động trở thành externally bindable.

**Purpose:**

Dùng evidence runtime để chứng minh constructor/declarative/allowed-field controls giới hạn write surface như thế nào.

## Binding Security khác Validation Rule

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:35–03:45`

**Visual:**

Giữ `role=ADMIN` trên màn hình rồi đặt hai câu hỏi nối tiếp: “Channel này có được bind role không?” và “State sau đó có hợp lệ không?”.

**Script:**

Field role bị suppress cũng cho thấy vì sao validator không thể thay thế binding security.

**Purpose:**

Nối safe-binding evidence sang separation rõ ràng giữa write authorization và state validation.

### Scene 1 — Đặt câu hỏi đúng ở đúng layer

**Time:** `03:45–04:45`

**Visual:**

Animate flow: input name -> binding policy -> conversion -> typed state -> validation. Dừng `role` ngay cổng đầu. Sau đó cho một rule khác như “end date bắt buộc khi status=CLOSED” đi qua binding rồi fail ở validation.

**Script:**

Nếu use case tuyệt đối không được đổi role, đừng expose `role` rồi hy vọng validator phát hiện giá trị “không được phép”. Hãy chặn field đó ở binding surface. Ngược lại, rule như “end date bắt buộc khi status là CLOSED” đánh giá ý nghĩa của state sau cùng nên thuộc validation. Ngay cả `requiredFields` cũng là binding rule về sự hiện diện của incoming property value, không phải domain invariant.

**Purpose:**

Cho người học tiêu chí ổn định để phân loại security surface decision và semantic validation rule.

## Chọn Conversion, Formatting, Validator, Bean Validation và DataBinder

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:45–04:55`

**Visual:**

Biến pipeline thành decision board có năm thẻ mechanism.

**Script:**

Input processing sẽ dễ reasoning hơn khi mỗi vấn đề được giao cho đúng abstraction vốn sở hữu loại quyết định đó.

**Purpose:**

Chuyển từ security/validation boundary sang checklist chọn mechanism toàn module.

### Scene 1 — Ghép problem với đúng owner

**Time:** `04:55–06:00`

**Visual:**

Reveal bảng: typed transformation reusable -> `ConversionService/Converter`; text phụ thuộc locale/presentation -> `Formatter`; programmatic object rule -> Spring `Validator`; annotation/provider constraint -> Jakarta Bean Validation integration; apply input + field policy + binding failure -> `DataBinder`.

**Script:**

Chọn cơ chế theo responsibility. Conversion xử lý type transformation reusable. Formatting xử lý text khi locale hoặc field context quan trọng. Spring `Validator` phù hợp với programmatic object rule; Bean Validation integration phù hợp với constraint annotation và group. `DataBinder` áp input, thực thi field policy, phối hợp conversion và giữ binding failure. Nếu “37” phải thành integer thì đó là conversion; nếu tuổi âm không được chấp nhận thì đó là validation.

**Purpose:**

Tổng hợp module thành một decision guide thực dụng mà không làm mờ boundary giữa các abstraction.

## Luồng Binding và Validation End-to-End

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:00–06:10`

**Visual:**

Nối năm thẻ mechanism thành một pipeline từ trái sang phải.

**Script:**

Các mechanism giờ đã đứng đúng chỗ. Ta có thể theo một input từ ngoài vào đến structured result mà không bỏ qua evidence ở giữa.

**Purpose:**

Gộp các quyết định riêng lẻ thành một end-to-end processing model thống nhất.

### Scene 1 — Giữ evidence xuyên suốt mọi stage

**Time:** `06:10–07:20`

**Visual:**

Animate: raw external values -> accepted input surface -> constructor/property path -> conversion/formatting -> typed state -> `BindingResult` -> Spring Validator/Bean Validation -> `ObjectError/FieldError`. Thêm nhánh “skip validation when binding errors exist” với nhãn “application/infrastructure decision”.

**Script:**

Một flow an toàn bắt đầu bằng việc chọn accepted input surface. Sau đó chọn constructor hoặc property binding, resolve và convert value, rồi để BindingResult giữ lại binding failure. Validation chạy trên typed state và thêm lỗi của riêng nó. Có chạy validation tiếp khi binding đã có lỗi hay không là policy của application hoặc infrastructure; điều quan trọng là hai loại evidence vẫn phân biệt được. Đừng bỏ binding error rồi coi một object mới được populate một phần như input processing đã thành công.

**Purpose:**

Tích hợp Security, Conversion, Binding và Validation vào một pipeline có thể quan sát và giải thích từng failure stage.

## Chuyển giao sang Core Container, MVC và WebFlux

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:20–07:30`

**Visual:**

Từ structured result cuối pipeline, vẽ ba mũi tên sang Core Container, MVC, WebFlux và một mũi tên nhỏ từ method validation sang Aspect.

**Script:**

Reusable pipeline kết thúc ở typed state và structured errors. Phần tiếp theo thuộc owner framework lân cận.

**Purpose:**

Khép module bằng ownership handoff thay vì lặp lại lifecycle của transport và container.

### Scene 1 — Đi theo ownership chain downstream

**Time:** `07:30–08:35`

**Visual:**

Highlight Core Container resolve message code qua `MessageSource`; MVC dùng `WebDataBinder`, `@InitBinder` và controller argument binding; WebFlux sở hữu reactive controller lifecycle; Aspect sở hữu proxy/interceptor mechanics quanh method validation.

**Script:**

Module này sở hữu các contract reusable cho tới structured binding và validation error. Core Container tiếp quản localization rộng hơn qua `MessageSource`. MVC và WebFlux quyết định controller lifecycle lấy request input ra sao, tạo binder thế nào, gọi validation khi nào và trình bày failure cho client như thế nào. Method-validation infrastructure nằm trong curriculum này, còn interception mechanics thuộc Aspect. Đi theo ownership chain giúp các core contract vẫn có thể dùng trong service, batch, test hay custom infrastructure mà không cần mang theo web lifecycle.

**Purpose:**

Tạo learning handoff chính xác từ validation/data-binding reusable sang Core Container, MVC, WebFlux và AOP.
