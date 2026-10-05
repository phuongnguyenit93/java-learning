---
video:
  url: ""
---

# Truy cập thuộc tính và trạng thái Binding

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

## Property Path và truy cập object lồng nhau

<!-- VIDEO_SECTION -->

### Scene 1 — Một đường dẫn đi qua state có thể ghi

**Time:** `00:00–01:00`

**Visual:**

Vẽ object tree của `CustomerForm`, rồi animate property path `address.city` đi qua từng node. Sau đó đổi node `address` thành `null` và hiện cảnh báo rằng kết quả còn phụ thuộc cấu hình accessor/binder.

**Script:**

Binding cần một cách ổn định để nói chính xác giá trị sẽ đi vào đâu. `name` là path đơn giản; `address.city` đi qua một object lồng nhau. Nhưng property path chỉ mô tả vị trí đích. Nó không tự đảm bảo property tồn tại, writable, object trung gian luôn có sẵn, hay input chuyển được sang kiểu đích. Chính vì vậy cùng một path có thể xuất hiện ở thao tác gán và sau đó xuất hiện lại trong `FieldError`.

**Purpose:**

Xây mental model property path và làm rõ rằng path hợp lệ về hình thức chưa đồng nghĩa truy cập hay binding chắc chắn thành công.

## BeanWrapper và abstraction truy cập thuộc tính

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:00–01:10`

**Visual:**

Giữ `address.city` được highlight rồi bọc object tree bằng khung `BeanWrapper`, kèm icon read, write, descriptor và conversion.

**Script:**

Khi đã biết tên vị trí, Spring vẫn cần một abstraction hiểu JavaBean để đọc, ghi và đi qua nested path. Đó là vai trò của `BeanWrapper`.

**Purpose:**

Nối từ ký hiệu property path sang cơ chế property access xử lý path đó.

### Scene 1 — Property access thấp hơn binding orchestration

**Time:** `01:10–02:15`

**Visual:**

Hiển thị `PropertyAccessorFactory.forBeanPropertyAccess(form)`, sau đó `setPropertyValue("address.city", "Da Nang")`, `getPropertyValue` và `isWritableProperty`. Bên cạnh, vẽ `DataBinder` lớn hơn bao quanh lớp `BeanWrapper` và thêm field policy, result, validation.

**Script:**

`BeanWrapper` có thể xem descriptor, kiểm tra property có readable hoặc writable hay không, đọc ghi giá trị, đi qua nested path và tham gia type conversion hay `PropertyEditor`. Nhưng đó vẫn chưa phải toàn bộ workflow binding. `DataBinder` thường dùng hạ tầng property access này rồi thêm policy, structured result, required field, allowed field và validator. Có thể nhớ ngắn gọn: `BeanWrapper` thao tác property; `DataBinder` điều phối một lần xử lý input.

**Purpose:**

Tách rõ thao tác JavaBean cấp thấp khỏi binding workflow có policy ở cấp cao hơn.

## BindingResult và Errors

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:15–02:25`

**Visual:**

Biến thao tác property thành một thẻ kết quả, rồi chia thành lớp chung `Errors` và lớp giàu thông tin hơn `BindingResult`.

**Script:**

Property access có thể thành công hoặc thất bại, nhưng caller cần bằng chứng có cấu trúc chứ không chỉ một chuỗi exception rời rạc.

**Purpose:**

Chuyển từ property manipulation sang model kết quả chung cho Binding và Validation.

### Scene 1 — Giữ lỗi dưới dạng dữ liệu có cấu trúc

**Time:** `02:25–03:30`

**Visual:**

Hiển thị sơ đồ kế thừa `Errors -> BindingResult`. Animate `rejectValue("address.city", "city.required")` thành một field-error card, sau đó mở các thông tin riêng của BindingResult như target, raw field value, suppressed fields và message-code resolution.

**Script:**

`Errors` là contract chung để ghi và truy vấn lỗi. `BindingResult` mở rộng contract đó bằng thông tin đặc thù của binding: target object, raw value, suppressed field, property editor và cơ chế phân giải message code. Validator chỉ cần phụ thuộc vào `Errors`; hạ tầng cần nhìn toàn bộ kết quả bind có thể dùng `BindingResult`. Điểm quan trọng là Spring giữ lỗi dưới dạng structured data để tầng sau còn có thể phân tích, localize hoặc render theo transport.

**Purpose:**

Giải thích vì sao Spring tách error collector chung khỏi result giàu thông tin của binding operation.

## ObjectError và FieldError

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:30–03:40`

**Visual:**

Tách một thẻ error tổng quát thành hai nhánh: “toàn object” và “một field/path cụ thể”.

**Script:**

Structured error hữu ích hơn khi ta biết lỗi thuộc về cả object hay thuộc về một field xác định.

**Purpose:**

Nối error container sang hai dạng lỗi chính sẽ xuất hiện xuyên suốt các chapter sau.

### Scene 1 — Đặt lỗi đúng phạm vi

**Time:** `03:40–04:40`

**Visual:**

Hiển thị `errors.reject("dateRange.invalid")` gắn với cả form và `errors.rejectValue("quantity", "quantity.positive")` gắn với một field. Mở panel `FieldError` có rejected value và cờ `bindingFailure`.

**Script:**

`ObjectError` phù hợp khi rule nói về state kết hợp của cả object, ví dụ start date phải đứng trước end date. `FieldError` phù hợp khi một property path cụ thể sở hữu lỗi. Cả hai đều có thể mang message code, arguments và default message vì chúng là `MessageSourceResolvable`. Riêng field error còn có thể giữ rejected value và cho biết lỗi có phát sinh trong lúc binding hay không.

**Purpose:**

Giúp người học chọn và đọc đúng ObjectError/FieldError mà không biến localized message thành identity duy nhất của rule.

## Lỗi trực tiếp và các trường hợp Binding phụ thuộc chính sách

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:40–04:50`

**Visual:**

Đặt bốn thẻ input lên bảng: sai kiểu, field không tồn tại, nested path không truy cập được, và field bị thiếu sau khi cấu hình required.

**Script:**

Đã có model để biểu diễn lỗi, nhưng không phải input đáng ngờ nào cũng được xử lý giống nhau. Có lỗi trực tiếp và có trường hợp phụ thuộc policy của binder.

**Purpose:**

Nối structured error sang các policy distinction cần biết trước chapter DataBinder.

### Scene 1 — Bốn trường hợp, bốn ý nghĩa

**Time:** `04:50–06:05`

**Visual:**

Đi qua bảng so sánh: type mismatch là direct binding failure; unknown field được bỏ qua mặc định vì `ignoreUnknownFields=true`; invalid nested field không bị bỏ qua mặc định vì `ignoreInvalidFields=false`; required field chỉ tồn tại sau `setRequiredFields(...)`. Cuối bảng ghi chú các switch này thuộc property binding.

**Script:**

Type mismatch nghĩa là property có thật nhưng input không chuyển được sang kiểu cần thiết, nên đây là lỗi binding trực tiếp. Unknown field không có property tương ứng và mặc định được bỏ qua. Invalid field trỏ tới state có ý nghĩa nhưng hiện không truy cập được, ví dụ object trung gian đang null, và mặc định không bị bỏ qua. Còn “required” chỉ xuất hiện khi binder đã cấu hình field đó bắt buộc trong incoming values. Đây là policy của property binding, không phải rule của constructor binding và cũng chưa phải định nghĩa bề mặt ghi an toàn.

**Purpose:**

Cho người học taxonomy rõ ràng về binding failure và policy trước khi đi sâu vào DataBinder và Safe Binding.
