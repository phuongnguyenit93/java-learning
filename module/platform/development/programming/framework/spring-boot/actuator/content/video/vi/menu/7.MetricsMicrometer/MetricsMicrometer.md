---
video:
  url: ""
---

# Metrics endpoint và ranh giới với Micrometer

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

## Boot tích hợp hạ tầng Micrometer MeterRegistry như thế nào?

<!-- VIDEO_SECTION -->

### Scene 1 — Boot tích hợp hạ tầng Micrometer MeterRegistry như thế nào?

**Time:** `00:00–00:32`

**Visual:**

Vẽ `framework/application instrumentation → MeterRegistry → Actuator metrics endpoint`. Từ registry thêm một nhánh khác ra exporter/backend. Gắn ownership: Micrometer meter model, Boot auto-configuration, Actuator inspection.

**Script:**

Metrics bắt đầu từ Micrometer chứ không bắt đầu ở Actuator endpoint. Boot auto-configure `MeterRegistry`, bind nhiều meter của framework/JVM/process và cho application code đăng ký meter qua Spring-managed registry. Actuator sau đó cung cấp management view trên registry đó. Tách ba vai trò này quan trọng vì meter design vẫn thuộc Micrometer/observability dù Boot làm phần tích hợp rất thuận tiện.

**Purpose:**

Đặt MeterRegistry là nguồn metrics và Actuator là lớp inspection, không phải chủ sở hữu meter model.

## Metrics endpoint của Actuator dùng để làm gì?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:32–00:45`

**Visual:**

Giữ `MeterRegistry` ở giữa và đổi hướng mũi tên highlight cho `Metrics endpoint của Actuator dùng để làm gì?`—inspection, measurement identity, tag selection, export hay observability ownership—để cùng một flow tiến hóa.

**Script:**

Khi registry là nguồn dữ liệu, vai trò của Actuator trở nên cụ thể: cho operator kiểm tra meter mà process này đang có ngay lúc đó.

**Purpose:**

Từ ownership của registry chuyển sang diagnostic endpoint cho phép operator inspect registry của một process.

### Scene 1 — Metrics endpoint của Actuator dùng để làm gì?

**Time:** `00:45–01:17`

**Visual:**

Gọi `/actuator/metrics`, chọn một meter rồi gọi `/actuator/metrics/{name}`. Hiện measurements và available tags; cố ý không có time-series database trong scene.

**Script:**

Metrics endpoint là bề mặt chẩn đoán theo request. Collection endpoint liệt kê meter name mà registry đang biết; chọn một name sẽ thấy measurements và chiều tag hiện có. Nó rất hợp để hỏi “meter này có được đăng ký chưa?” hoặc “tag nào đang tồn tại?”. Nó không phải time-series query engine và không thay thế monitoring backend.

**Purpose:**

Cố định metrics endpoint là góc nhìn current-process, không phải kênh lưu metrics dài hạn.

## Tên meter và measurement xuất hiện trong metrics endpoint như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:17–01:28`

**Visual:**

Giữ `MeterRegistry` ở giữa và đổi hướng mũi tên highlight cho `Tên meter và measurement xuất hiện trong metrics endpoint như thế nào?`—inspection, measurement identity, tag selection, export hay observability ownership—để cùng một flow tiến hóa.

**Script:**

Tên meter chưa đủ để hiểu measurement nếu bên dưới vẫn có nhiều tagged identities. Bây giờ cần nhìn chiều tag.

**Purpose:**

Làm rõ một meter name vẫn có thể đại diện nhiều tagged identity trước khi giới thiệu tag filtering.

### Scene 1 — Tên meter và measurement xuất hiện trong metrics endpoint như thế nào?

**Time:** `01:28–02:02`

**Visual:**

Hiện một Micrometer name có nhiều tagged identities. Aggregate `COUNT`, `TOTAL_TIME` ở view chưa lọc, rồi hiện `availableTags` với `outcome=success|failure`.

**Script:**

Selector của metrics endpoint dùng đúng tên meter trong Micrometer model, dù backend sau này có thể đổi naming convention. Một name có thể đại diện nhiều meter identity khác nhau theo tag, nên measurement như `COUNT`, `TOTAL_TIME` hay `VALUE` có thể là aggregate trên nhiều identity khớp. `availableTags` là dấu hiệu cho thấy một tên hiển thị vẫn còn các chiều dữ liệu bên dưới.

**Purpose:**

Giải thích quan hệ giữa name, measurement và tagged identity để aggregate response không bị hiểu nhầm là một meter scalar duy nhất.

## Tag có thể thu hẹp việc xem metrics như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:02–02:13`

**Visual:**

Giữ `MeterRegistry` ở giữa và đổi hướng mũi tên highlight cho `Tag có thể thu hẹp việc xem metrics như thế nào?`—inspection, measurement identity, tag selection, export hay observability ownership—để cùng một flow tiến hóa.

**Script:**

Available tags mới cho biết dimension tồn tại. Experiment `learning.requests` cho thấy query chọn đúng một dimension mà không thay đổi metric.

**Purpose:**

Dùng tagged meter model để dẫn tới filtered query thật trên evidence `learning.requests` của repository.

### Scene 1 — Tag có thể thu hẹp việc xem metrics như thế nào?

**Time:** `02:13–02:46`

**Visual:**

Chạy experiment thật: gọi `POST /api/actuator-learning/metrics/success` hai lần và `.../failure` một lần. Xem `/actuator/metrics/learning.requests`, sau đó thêm `?tag=outcome:success` để thấy measurement được thu hẹp và `availableTags`.

**Script:**

Tag filtering chỉ chọn trong các meter identity đã đăng ký; nó không tạo metric mới. Module này tăng `learning.requests` cùng tag `outcome`. View chưa lọc có thể aggregate mọi outcome, còn `tag=outcome:success` thu hẹp xuống series phù hợp. Có thể dùng nhiều `tag=KEY:VALUE` để lọc thêm. Observation này hữu ích cho diagnosis, nhưng không biến high-cardinality tag thành thiết kế tốt.

**Purpose:**

Chứng minh tag filtering bằng Step 5 runtime evidence và nối response đã lọc về tagged meter model của Micrometer.

## Xem metrics trực tiếp khác export metrics như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:46–02:58`

**Visual:**

Giữ `MeterRegistry` ở giữa và đổi hướng mũi tên highlight cho `Xem metrics trực tiếp khác export metrics như thế nào?`—inspection, measurement identity, tag selection, export hay observability ownership—để cùng một flow tiến hóa.

**Script:**

Tag filtering giúp diagnosis trên một process. Monitoring dài hạn đặt câu hỏi khác: telemetry rời process và đi vào backend như thế nào?

**Purpose:**

Sau diagnosis theo tag, đối chiếu one-process inspection với flow export sang backend.

### Scene 1 — Xem metrics trực tiếp khác export metrics như thế nào?

**Time:** `02:58–03:30`

**Visual:**

Tách pipeline sau `MeterRegistry`: bên trái `/actuator/metrics` cho operator; bên phải Prometheus registry expose `/actuator/prometheus` để scrape, cùng một nhánh push registry ra backend.

**Script:**

Inspect metrics và export metrics là hai flow khác nhau. `/actuator/metrics` trả lời request chẩn đoán của một operator trên một process. Registry/exporter lại publish telemetry theo format và cadence của backend. Prometheus làm khác biệt này rất rõ: khi có registry dependency, Boot expose endpoint Prometheus riêng cho scrape, còn generic metrics endpoint vẫn chỉ là management view của Micrometer meters.

**Purpose:**

Khôi phục đầy đủ hai phía inspection-versus-export để monitoring backend không bị hiểu là cần poll generic metrics endpoint.

## Những vấn đề thiết kế metrics và backend nào nằm ngoài Actuator?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:30–03:43`

**Visual:**

Giữ `MeterRegistry` ở giữa và đổi hướng mũi tên highlight cho `Những vấn đề thiết kế metrics và backend nào nằm ngoài Actuator?`—inspection, measurement identity, tag selection, export hay observability ownership—để cùng một flow tiến hóa.

**Script:**

Khi telemetry đã export, boundary trở nên rõ: Actuator có thể inspect hoặc expose, nhưng meter model và lifecycle của backend tiếp tục ở ngoài module này.

**Purpose:**

Dùng export boundary để đặt meter design và backend architecture ra ngoài ownership của Actuator.

### Scene 1 — Những vấn đề thiết kế metrics và backend nào nằm ngoài Actuator?

**Time:** `03:43–04:14`

**Visual:**

Đặt naming, tag cardinality, instrument choice, histogram/SLO, backend query, dashboard, alert, retention và capacity planning ngoài biên Actuator. Minh họa `userId` tag không giới hạn nổ thành rất nhiều time series.

**Script:**

Actuator có thể giúp nhìn thấy thiết kế metric tệ nhưng không sở hữu cách sửa. Chọn counter/gauge/timer, định nghĩa low-cardinality dimension, histogram/SLO và ý nghĩa business thuộc instrumentation design. Query, alert, dashboard, retention và capacity planning thuộc hệ thống observability. Một tag không giới hạn vẫn gây chi phí lớn dù `/actuator/metrics` có filter được rất đẹp.

**Purpose:**

Khép phần metrics bằng ownership boundary để endpoint tutorial không lấn sang metric-model hoặc backend architecture.
