---
video:
  url: ""
---

# Các endpoint info và thông tin môi trường

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

## Info endpoint dùng để làm gì?

<!-- VIDEO_SECTION -->

### Scene 1 — Info endpoint dùng để làm gì?

**Time:** `00:00–00:41`

**Visual:**

Hiện `/actuator/info` với các block nhỏ `build`, `git` và một metadata custom. Bên cạnh gạch chéo một debug dump lớn.

**Script:**

Info endpoint phù hợp với metadata nhỏ và tương đối ổn định để operator biết thứ gì đang chạy: build identity, source revision, deployment flavor hoặc thông tin vận hành tương tự. Response được ghép từ các `InfoContributor`. Hãy coi nó như “thẻ căn cước” của process, không phải nơi dump mọi thứ: dữ liệu biến động cao hợp với metrics hơn, còn secret và cấu hình chi tiết không tự nhiên trở nên an toàn chỉ vì operator có lúc muốn xem.

**Purpose:**

Định nghĩa vai trò hẹp cho info và phân biệt metadata với diagnostics hoặc telemetry.

## Các contribution kiểu InfoContributor tạo metadata vận hành như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:41–00:53`

**Visual:**

Giữ bảng management key/value trên màn hình và thay đúng nguồn hoặc lớp bảo vệ cho `Các contribution kiểu InfoContributor tạo metadata vận hành như thế nào?`—contributor, environment, bound config, masking, audience hay externalized-configuration handoff.

**Script:**

Endpoint chỉ là vỏ chứa; câu hỏi tiếp theo là contributor nào thực sự đưa metadata vào đó và default của chúng là gì.

**Purpose:**

Từ container `/info` đi vào contributor và default của Boot quyết định metadata nào xuất hiện.

### Scene 1 — Các contribution kiểu InfoContributor tạo metadata vận hành như thế nào?

**Time:** `00:53–01:25`

**Visual:**

Dựng stack `InfoContributor`. Đánh dấu `build` và `git` enabled khi file prerequisite tồn tại; đánh dấu `env`, `java`, `os`, `process` disabled mặc định trong Boot 3.3. Hiện `management.info.<id>.enabled` và `management.info.defaults.enabled`.

**Script:**

Mỗi `InfoContributor` đóng góp một nhóm metadata có tên. Trong Boot 3.3, `build` và `git` được bật khi có `META-INF/build-info.properties` hoặc `git.properties`, còn `env`, `java`, `os` và `process` mặc định bị tắt. Từng contributor có thể được điều khiển bằng `management.info.<id>.enabled`, độc lập với việc info endpoint có được expose hay không. Custom contributor cũng nên nhỏ, rẻ và rõ nghĩa.

**Purpose:**

Giữ parity đầy đủ về contributor defaults và tách contributor enablement khỏi endpoint exposure.

## Endpoint hướng tới environment có thể cho biết gì về ứng dụng đang chạy?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:25–01:38`

**Visual:**

Giữ bảng management key/value trên màn hình và thay đúng nguồn hoặc lớp bảo vệ cho `Endpoint hướng tới environment có thể cho biết gì về ứng dụng đang chạy?`—contributor, environment, bound config, masking, audience hay externalized-configuration handoff.

**Script:**

Info cho biết ứng dụng này là bản nào. Khi incident hỏi “process đang dùng cấu hình gì?”, bề mặt quản trị chuyển sang env và configprops.

**Purpose:**

Chuyển từ application identity metadata sang runtime inspection về cấu hình bằng env và configprops.

### Scene 1 — Endpoint hướng tới environment có thể cho biết gì về ứng dụng đang chạy?

**Time:** `01:38–02:11`

**Visual:**

Đặt `/actuator/env` cạnh `/actuator/configprops`. Với env, cho property sources đi vào `ConfigurableEnvironment`; với configprops, cho resolved values bind vào object `@ConfigurationProperties`.

**Script:**

Các endpoint hướng environment trả lời cùng bài toán cấu hình nhưng ở hai góc khác nhau. `env` cho thấy `ConfigurableEnvironment` và các property source đang có mặt; `configprops` cho thấy giá trị đã được bind vào các bean `ConfigurationProperties`. Chúng giúp quan sát runtime state nhưng không thay thế các quy tắc đã quyết định precedence, profile, Config Data hay binding.

**Purpose:**

Phân biệt property-source inspection với bound-object inspection và đặt đúng handoff sang externalized configuration.

## Các giá trị có khả năng nhạy cảm được xử lý như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:11–02:24`

**Visual:**

Giữ bảng management key/value trên màn hình và thay đúng nguồn hoặc lớp bảo vệ cho `Các giá trị có khả năng nhạy cảm được xử lý như thế nào?`—contributor, environment, bound config, masking, audience hay externalized-configuration handoff.

**Script:**

Khi cấu hình đã được nhìn thấy, câu hỏi tiếp theo là disclosure: chính dữ liệu chẩn đoán hữu ích lại có thể lộ internals quan trọng.

**Purpose:**

Dùng configuration visibility để đưa masking/show-values vào trước khi bàn rộng hơn về người được phép nhận endpoint.

### Scene 1 — Các giá trị có khả năng nhạy cảm được xử lý như thế nào?

**Time:** `02:24–03:03`

**Visual:**

Hiện response env với giá trị bị thay bằng `******`, sau đó chuyển `show-values` từ `never` sang `when-authorized`. Với HTTP, nối `when-authorized` tới authentication và role đã cấu hình. Với JMX, thêm callout riêng: “mọi user được coi là authorized cho show-values” và một boundary kiểm soát JMX độc lập.

**Script:**

Các endpoint cấu hình có thể che giá trị nhạy cảm, còn `show-values` quyết định khi nào giá trị gốc được trả. Với HTTP, `when-authorized` phụ thuộc authentication và endpoint role đã cấu hình. JMX thì khác: với quyết định `show-values` này, Actuator coi mọi JMX user là authorized, nên exposure và access của JMX phải được bảo vệ độc lập. Tên property, source, object structure và giá trị chưa được che vẫn có thể lộ thông tin hữu ích. Vì thế hãy giới hạn exposure trước và dùng sanitization như defense in depth.

**Purpose:**

Làm rõ masking/show-values nhưng không biến sanitization thành lý do để expose endpoint rộng.

## Vì sao các góc nhìn info và environment là một quyết định về quyền truy cập?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:03–03:13`

**Visual:**

Giữ bảng management key/value trên màn hình và thay đúng nguồn hoặc lớp bảo vệ cho `Vì sao các góc nhìn info và environment là một quyết định về quyền truy cập?`—contributor, environment, bound config, masking, audience hay externalized-configuration handoff.

**Script:**

Masking giảm accidental disclosure nhưng không quyết định ai được gọi endpoint. Đó vẫn là bài toán access design.

**Purpose:**

Nâng từ value masking lên quyết định audience/network/authorization dành cho management data nhạy cảm.

### Scene 1 — Vì sao các góc nhìn info và environment là một quyết định về quyền truy cập?

**Time:** `03:13–03:44`

**Visual:**

So sánh hai audience: deployment automation chỉ đọc `/info` đã curate; operator có quyền mới xem `/env` hoặc `/configprops` qua internal management path. Bao endpoint nhạy cảm bằng network, exposure, authorization và sanitization.

**Script:**

Info và environment có risk profile khác nhau. Một info response được chọn lọc có thể phù hợp với audience rộng hơn, còn env/configprops thường cần network và authorization chặt hơn. Trước khi expose, phải xem cả value lẫn metadata đi kèm. Exposure, network placement, security rule và sanitization nên hỗ trợ lẫn nhau chứ không thay thế nhau.

**Purpose:**

Biến sensitivity thành quyết định audience/access rõ ràng thay vì một rule chung “Actuator chỉ dùng nội bộ”.

## Việc quan sát qua endpoint bàn giao sang externalized configuration ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:44–03:58`

**Visual:**

Giữ bảng management key/value trên màn hình và thay đúng nguồn hoặc lớp bảo vệ cho `Việc quan sát qua endpoint bàn giao sang externalized configuration ở đâu?`—contributor, environment, bound config, masking, audience hay externalized-configuration handoff.

**Script:**

Sau khi quyết định ai được xem cấu hình, câu hỏi cuối là đi đâu khi chính value đang sai. Lúc đó việc điều tra rời Actuator và theo rule của Boot configuration.

**Purpose:**

Bàn giao một runtime value bất ngờ từ Actuator inspection sang các rule externalized-configuration đã tạo ra value đó.

### Scene 1 — Việc quan sát qua endpoint bàn giao sang externalized configuration ở đâu?

**Time:** `03:58–04:37`

**Visual:**

Vẽ flow troubleshooting: Actuator cho thấy property source/value hoặc bound object → externalized configuration lần theo Config Data, profile, env var, CLI, precedence và binding. Dùng một ví dụ wrong value đi qua boundary.

**Script:**

Actuator trả lời “process đang chạy báo được trạng thái cấu hình nào?”. Externalized configuration trả lời “Boot đã resolve trạng thái đó như thế nào?”. Nếu `env` cho thấy property nằm ở source bất ngờ hoặc `configprops` cho value đã bind sai mong đợi, đừng tiếp tục đổi Actuator setting; hãy lần theo property precedence và binding. Runtime inspection và configuration resolution là hai công cụ bổ sung nhau, không phải hai curriculum trùng lặp.

**Purpose:**

Cho incident về cấu hình một handoff rõ từ runtime evidence sang subsystem sở hữu value resolution.
