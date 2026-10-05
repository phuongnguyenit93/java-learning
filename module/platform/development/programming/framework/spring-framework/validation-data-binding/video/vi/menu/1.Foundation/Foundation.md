---
video:
  url: ""
---

# Validation, Binding, Chuyển đổi kiểu và Định dạng

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

## Vì sao các cơ chế này tồn tại?

<!-- VIDEO_SECTION -->

### Scene 1 — Một input, bốn việc khác nhau

**Time:** `00:00–01:05`

**Visual:**

Cho thẻ input `age = "37"` đi qua bốn chặng: Binding chọn property đích, Conversion tạo `int`, assignment cập nhật state đã có kiểu, rồi Validation kiểm tra rule. Bên cạnh chặng chuyển đổi, thêm một nhánh Formatter có `Locale`.

**Script:**

Dữ liệu đi vào ứng dụng hiếm khi có sẵn đúng hình dạng mà object Java cần. Spring tách công việc thành nhiều cơ chế để mỗi cơ chế trả lời một câu hỏi rõ ràng. Binding quyết định giá trị có tên này sẽ đi vào đâu. Conversion đổi kiểu Java. Formatting xử lý cách text được đọc hoặc hiển thị khi có ngữ cảnh trình bày như locale. Validation kiểm tra state sau cùng có chấp nhận được hay không. Khi tách đúng như vậy, một converter hay validator có thể tái sử dụng ở nhiều nơi và ta biết lỗi phát sinh ở giai đoạn nào.

**Purpose:**

Xây mental model pipeline và gán đúng trách nhiệm cho Binding, Conversion, Formatting và Validation trước khi đi sâu vào API.

## Validation khác Data Binding như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:05–01:15`

**Visual:**

Giữ pipeline trên màn hình rồi phóng to hai checkpoint: “Input có tạo được state đúng kiểu không?” và “State đó có hợp lệ không?”.

**Script:**

Từ pipeline này, ta có một ranh giới rất dễ kiểm chứng: có input hỏng trước khi business rule chạy, nhưng cũng có input bind hoàn toàn thành công rồi mới thất bại ở validation.

**Purpose:**

Nối mental model pipeline sang ranh giới giữa binding failure và validation failure.

### Scene 1 — Cùng một field, hai loại lỗi

**Time:** `01:15–02:25`

**Visual:**

Đặt hai lần chạy cạnh nhau. Lần A bind `age = "not-a-number"` vào `int`, highlight `FieldError` với `bindingFailure=true`. Lần B bind `age = "15"`, hiển thị 0 binding error, sau đó chạy `AdultRegistrationValidator` và highlight `age.tooYoung` với `bindingFailure=false`.

**Script:**

Hai input này đều dẫn tới lỗi nhưng nguyên nhân khác hẳn nhau. “not-a-number” không thể thành số nguyên, nên lỗi xuất hiện ngay ở binding và conversion. Không cần business validator để phát hiện chuyện đó. Còn “15” chuyển kiểu và gán vào target bình thường. Chỉ khi validation chạy, rule `age.tooYoung` mới xuất hiện. Vì vậy hãy chẩn đoán lỗi ở đúng stage: binding trả lời input có tạo được state có kiểu hay không; validation trả lời state đó có được chấp nhận hay không.

**Purpose:**

Dùng evidence thật của module để người học nhìn thấy ranh giới Binding/Validation thay vì chỉ ghi nhớ định nghĩa.

## Conversion khác Formatting như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:25–02:35`

**Visual:**

Thu nhỏ checkpoint Validation và đưa hai thẻ lên giữa màn hình: `String -> OrderId` và `LocalDate <-> text theo locale`.

**Script:**

Khi đã biết một giá trị cần đổi representation, ta còn phải chọn đúng loại chuyển đổi: đây là ý nghĩa kiểu ổn định hay là cách text được trình bày cho người dùng?

**Purpose:**

Chuyển từ các stage lỗi sang sự khác nhau giữa type conversion và formatting có ngữ cảnh trình bày.

### Scene 1 — Ý nghĩa kiểu và cách hiển thị text

**Time:** `02:35–03:40`

**Visual:**

Bên trái hiển thị `ConversionService` đổi `"42"` thành `Integer.class`. Bên phải hiển thị cùng `1234.5` thành `1,234.5` với US và `1.234,5` với Germany, kèm badge `Locale` đi vào `Formatter`.

**Script:**

Conversion tổng quát trả lời câu hỏi về kiểu: một giá trị Java biến thành kiểu Java khác như thế nào. Formatter trả lời câu hỏi trình bày: cùng một giá trị sẽ được parse từ text hoặc print thành text ra sao cho field và locale hiện tại. Một identifier có syntax ổn định như `String -> OrderId` hợp với converter. Số hoặc ngày có cách viết thay đổi theo locale hợp với formatter. Nhờ vậy policy trình bày không bị nhét vào một converter vốn không có tham số locale.

**Purpose:**

Cho người học tiêu chí chọn Conversion hay Formatting dựa trên việc ngữ cảnh trình bày có ảnh hưởng tới transformation hay không.

## Ranh giới module và các module lân cận

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:40–03:50`

**Visual:**

Zoom out thành sơ đồ module: validation-data-binding ở giữa, xung quanh là Core Container, MVC, WebFlux và Aspect.

**Script:**

Các contract vừa học có thể tái sử dụng rất rộng, nhưng chúng không sở hữu toàn bộ lifecycle sử dụng chúng. Ta cần đánh dấu rõ điểm bàn giao cho module lân cận.

**Purpose:**

Đặt các contract reusable vào đúng boundary của curriculum trước khi đi vào chi tiết triển khai.

### Scene 1 — Biết core reusable dừng ở đâu

**Time:** `03:50–05:00`

**Visual:**

Highlight module này sở hữu `BeanWrapper`, `ConversionService`, formatter SPI, `Errors`, `Validator`, `DataBinder` và tích hợp Bean Validation. Sau đó highlight các mũi tên: `MessageSource` sang Core Container, `WebDataBinder/@InitBinder` sang MVC/WebFlux, proxy mechanics của method validation sang Aspect.

**Script:**

Module này tập trung vào các contract reusable của property access, conversion, formatting, validation, binding và phần Spring tích hợp Jakarta Bean Validation. Nó dừng trước lifecycle controller cụ thể. Core Container sở hữu `MessageSource` và hạ tầng i18n rộng hơn. MVC và WebFlux sở hữu cách request tạo và dùng web binder. Method validation được nối dây ở đây, còn cơ chế proxy chặn lời gọi thuộc phần Aspect. Giữ boundary này giúp ta nhớ rằng `DataBinder` bản thân không phải API chỉ dành cho web.

**Purpose:**

Ngăn việc gán nhầm hành vi của web, container hoặc proxy cho các contract validation/data-binding cấp thấp.
