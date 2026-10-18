---
video:
  url: ""
---

# Mô hình endpoint của Actuator

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

## Actuator endpoint là gì?

<!-- VIDEO_SECTION -->

### Scene 1 — Actuator endpoint là gì?

**Time:** `00:00–00:32`

**Visual:**

Hiện một thẻ endpoint `health` gồm hai lớp: endpoint ID và operations. Ánh xạ cùng thẻ đó sang `/actuator/health` ở HTTP và một MBean ở JMX; endpoint ID giữ nguyên khi transport thay đổi.

**Script:**

Một Actuator endpoint là một capability quản trị có endpoint ID và một hay nhiều operation. ID mới là định danh ổn định; URL HTTP hoặc biểu diễn JMX chỉ là cách transport trình bày capability đó. Vì vậy nên hiểu `health` trước hết là một endpoint model, chứ không phải chuỗi path `/actuator/health` bị hard-code vào bản chất của endpoint.

**Purpose:**

Đặt endpoint identity lên trên transport để các phần về path, exposure và custom endpoint sau này đều dựa trên cùng một mental model.

## Endpoint ID và operation tổ chức mô hình như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:32–00:44`

**Visual:**

Giữ thẻ endpoint cố định; ở `Endpoint ID và operation tổ chức mô hình như thế nào?` chỉ reveal thêm đúng lớp mới cần thiết—operation, catalog/prerequisite, transport adapter hoặc configuration ownership—để cùng một model tiến hóa liên tục.

**Script:**

Khi identity đã ổn định, lớp tiếp theo là behavior: những operation nào thuộc ID đó và transport sẽ biểu diễn chúng ra sao?

**Purpose:**

Từ endpoint identity ổn định chuyển sang các operation tạo behavior quản trị cho identity đó.

### Scene 1 — Endpoint ID và operation tổ chức mô hình như thế nào?

**Time:** `00:44–01:15`

**Visual:**

Mở class nhỏ `@Endpoint(id = "learning")`, highlight `@ReadOperation`, `@WriteOperation`, `@DeleteOperation`. Bên cạnh, ánh xạ read/write/delete sang HTTP method và JMX operation nhưng giữ nguyên endpoint class.

**Script:**

Endpoint ID nhóm một capability quản trị, còn operation annotation mô tả capability đó làm được gì. `@ReadOperation`, `@WriteOperation` và `@DeleteOperation` biểu diễn intent quản trị mà không biến class thành MVC controller. Hạ tầng HTTP hoặc JMX mới chuyển những operation đó sang request method hay MBean operation tương ứng. Chuỗi cần nhớ là ID → operations → exposure adapter.

**Purpose:**

Cho thấy management intent độc lập transport và lý do endpoint code không nên bắt đầu từ controller annotation.

## Nên tư duy thế nào về tập endpoint có sẵn?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:15–01:27`

**Visual:**

Giữ thẻ endpoint cố định; ở `Nên tư duy thế nào về tập endpoint có sẵn?` chỉ reveal thêm đúng lớp mới cần thiết—operation, catalog/prerequisite, transport adapter hoặc configuration ownership—để cùng một model tiến hóa liên tục.

**Script:**

Khi ID và operations đã rõ, catalog chỉ còn là nhiều capability cùng tuân theo một mô hình và mỗi capability có prerequisite riêng.

**Purpose:**

Dùng mô hình ID/operation để phân loại catalog theo mục đích và prerequisite thay vì học thuộc tên endpoint.

### Scene 1 — Nên tư duy thế nào về tập endpoint có sẵn?

**Time:** `01:27–02:00`

**Visual:**

Nhóm endpoint tích hợp sẵn theo câu hỏi vận hành: health, metadata/config, metrics, logging, framework structure, startup/HTTP exchange và binary diagnostics. Gắn badge prerequisite cho `startup`, `httpexchanges`, `prometheus`, `logfile`, `heapdump`.

**Script:**

Không nên học thuộc endpoint catalog như một danh sách phẳng. Hãy phân loại theo câu hỏi vận hành rồi kiểm tra prerequisite và exposure. `startup` cần dữ liệu startup đã buffer, `httpexchanges` cần `HttpExchangeRepository`, `prometheus` cần registry tương ứng, còn một số diagnostics chỉ tồn tại ở web. Cách nhìn này biến catalog thành công cụ troubleshooting thay vì bài học thuộc tên.

**Purpose:**

Dạy cách phân loại endpoint và làm prerequisite lộ rõ để “endpoint không thấy” không bị chẩn đoán nhầm thành lỗi routing.

## Vì sao Actuator endpoint mặc định độc lập với công nghệ expose?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:00–02:13`

**Visual:**

Giữ thẻ endpoint cố định; ở `Vì sao Actuator endpoint mặc định độc lập với công nghệ expose?` chỉ reveal thêm đúng lớp mới cần thiết—operation, catalog/prerequisite, transport adapter hoặc configuration ownership—để cùng một model tiến hóa liên tục.

**Script:**

Catalog cho thấy nhiều capability dùng chung một model. Sự tái sử dụng đó chỉ có được vì abstraction cốt lõi không thuộc MVC, WebFlux, Jersey hay JMX.

**Purpose:**

Giải thích vì sao một catalog dùng được cho nhiều transport bằng cách đưa technology independence thành thuộc tính kế tiếp.

### Scene 1 — Vì sao Actuator endpoint mặc định độc lập với công nghệ expose?

**Time:** `02:13–02:48`

**Visual:**

Đặt một `@Endpoint` ở giữa rồi tách ra bốn adapter MVC, WebFlux, Jersey và JMX. Gạch chéo việc import trực tiếp các công nghệ này vào endpoint class.

**Script:**

Mô hình `@Endpoint` tổng quát cố ý độc lập công nghệ. Boot phát hiện endpoint và operation trước; sau đó web hoặc JMX infrastructure mới expose chúng khi công nghệ đó khả dụng và endpoint được include. Với library hoặc management component tái sử dụng, điều này rất có giá trị: đổi web stack không buộc viết lại capability, và cùng endpoint có thể xuất hiện qua nhiều transport.

**Purpose:**

Giải thích lợi ích của technology-agnostic endpoint và chỉ đưa concern transport vào khi requirement thật sự cần.

## Thiết lập endpoint nào thuộc Actuator và phần nào thuộc externalized configuration?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:48–03:02`

**Visual:**

Giữ thẻ endpoint cố định; ở `Thiết lập endpoint nào thuộc Actuator và phần nào thuộc externalized configuration?` chỉ reveal thêm đúng lớp mới cần thiết—operation, catalog/prerequisite, transport adapter hoặc configuration ownership—để cùng một model tiến hóa liên tục.

**Script:**

Technology independence tạo ra một target cấu hình ổn định. Bước cuối là phân biệt setting của Actuator với machinery quyết định giá trị setting đó đến từ đâu.

**Purpose:**

Khép endpoint abstraction bằng cách tách semantics của Actuator setting khỏi cơ chế property resolution cung cấp giá trị.

### Scene 1 — Thiết lập endpoint nào thuộc Actuator và phần nào thuộc externalized configuration?

**Time:** `03:02–03:34`

**Visual:**

Chia màn hình: trái là ý nghĩa của `management.endpoint.*` / `management.endpoints.*`; phải là YAML, env var, CLI và property-source precedence cấp giá trị. Vẽ mũi tên bàn giao sang externalized-configuration.

**Script:**

Actuator sở hữu ý nghĩa của management property: bật endpoint, chọn exposure, đổi web base path hay điều khiển mức chi tiết health. Externalized configuration sở hữu nguồn và precedence của các giá trị đó. Nếu `management.endpoints.web.exposure.include` sai, Actuator giải thích hậu quả; nếu env var bất ngờ ghi đè YAML, phải quay sang cơ chế phân giải property của Boot.

**Purpose:**

Tách semantics của cấu hình endpoint khỏi cơ chế resolve property để người học biết chính xác module nào cần điều tra.
