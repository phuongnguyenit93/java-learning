---
video:
  url: ""
---

# Actuator trong kiến trúc observability production

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

## Luồng vận hành Actuator từ đầu đến cuối kết nối với nhau như thế nào?

<!-- VIDEO_SECTION -->

### Scene 1 — Luồng vận hành Actuator từ đầu đến cuối kết nối với nhau như thế nào?

**Time:** `00:00–00:37`

**Visual:**

Dựng flow end-to-end: runtime state/meter/logging/config/custom operation → Actuator endpoint model → enablement/exposure → network/security → operator hoặc external system. Cho một incident đi ngược từ request lỗi về state owner.

**Script:**

Luồng production đầy đủ bắt đầu từ operational state bên trong ứng dụng. Actuator chiếu một phần state đó thành endpoint; enablement quyết định capability có tồn tại; exposure chọn HTTP/JMX; network và security quyết định ai được gọi. Khi có lỗi, nên debug theo đúng lớp: state có tồn tại chưa, endpoint có available/exposed không, route có reachable/authorized không, rồi mới diễn giải response hoặc bàn giao sang subsystem sở hữu nguyên nhân.

**Purpose:**

Ghép toàn module thành một layered incident model có thể dùng cả khi cấu hình lẫn troubleshooting.

## Khi nào nên dùng Actuator endpoint và khi nào nên dùng công cụ telemetry bên ngoài?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:37–00:50`

**Visual:**

Giữ end-to-end production flow trên màn hình và zoom vào đúng layer của `Khi nào nên dùng Actuator endpoint và khi nào nên dùng công cụ telemetry bên ngoài?`—consumer type, signal type, infrastructure handoff, failure branch hoặc ownership map cuối.

**Script:**

End-to-end flow có hai kiểu consumer. Câu hỏi tiếp theo là ta cần bằng chứng tức thời từ một process hay lịch sử trên cả hệ thống.

**Purpose:**

Chia end-to-end flow theo nhu cầu consumer: quản trị tức thời một process hay telemetry qua thời gian và nhiều replica.

### Scene 1 — Khi nào nên dùng Actuator endpoint và khi nào nên dùng công cụ telemetry bên ngoài?

**Time:** `00:50–01:27`

**Visual:**

Chia màn hình “một process, ngay lúc này” và “nhiều instance, theo thời gian”. Đưa health contributor, meter/tag inspection, logger change, thread dump, custom maintenance sang phía đầu; dashboard, alert, retained metrics/logs, trace correlation, capacity analysis sang phía sau.

**Script:**

Dùng Actuator trực tiếp khi nhiệm vụ cục bộ và tức thời: xem một health tree, inspect meter/tag, đổi logger, lấy dump hoặc gọi bounded management action. Dùng telemetry infrastructure khi câu hỏi trải qua thời gian hay nhiều replica: lưu dữ liệu, aggregate instance, correlate trace, search log, alert theo trend hoặc capacity planning. Hai cách bổ sung nhau: dashboard tìm instance bất thường, rồi Actuator giúp đào sâu chính instance đó.

**Purpose:**

Cho operator một decision rule thực tế giữa direct management và external telemetry thay vì coi chúng là hai giải pháp cạnh tranh.

## Health, metrics và chẩn đoán logging liên hệ với nhau thế nào mà không trở thành một hệ thống duy nhất?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:27–01:39`

**Visual:**

Giữ end-to-end production flow trên màn hình và zoom vào đúng layer của `Health, metrics và chẩn đoán logging liên hệ với nhau thế nào mà không trở thành một hệ thống duy nhất?`—consumer type, signal type, infrastructure handoff, failure branch hoặc ownership map cuối.

**Script:**

Direct hay historical chỉ là một trục. Ngay trong cùng incident, health, metrics, logs và dumps vẫn cung cấp những loại evidence khác nhau.

**Purpose:**

Dùng khác biệt consumer để so sánh loại evidence riêng của health, metrics, logs và JVM snapshot.

### Scene 1 — Health, metrics và chẩn đoán logging liên hệ với nhau thế nào mà không trở thành một hệ thống duy nhất?

**Time:** `01:39–02:16`

**Visual:**

Hiện cùng một incident theo ba lane song song: health chuyển DOWN, error-rate metric tăng, log có causal exception; thêm thread dump làm deeper point-in-time evidence. Mỗi signal giữ lane riêng.

**Script:**

Health, metrics và logging mô tả các mặt khác nhau của cùng sự cố. Health nén điều kiện thành operational status; metrics định lượng behavior và trend; log ghi event rời rạc cùng context; thread/heap dump cho point-in-time JVM evidence sâu hơn. Chúng nên corroborate nhau chứ không bị ép thành một model. Readiness fail, error rate tăng và causal exception là ba clue bổ sung nhau với cost và audience khác nhau.

**Purpose:**

Khôi phục đầy đủ quan hệ health/metrics/logging và lý do nhiều signal nên liên hệ nhưng không thay thế nhau.

## Actuator bàn giao sang hạ tầng observability bên ngoài ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:16–02:29`

**Visual:**

Giữ end-to-end production flow trên màn hình và zoom vào đúng layer của `Actuator bàn giao sang hạ tầng observability bên ngoài ở đâu?`—consumer type, signal type, infrastructure handoff, failure branch hoặc ownership map cuối.

**Script:**

Khi signal type đã tách rõ, đích đến tiếp theo cũng rõ: Actuator expose local evidence, còn observability infrastructure vận chuyển và phân tích trên toàn môi trường.

**Purpose:**

Theo các local signal ra khỏi application tới infrastructure bên ngoài có nhiệm vụ lưu giữ và correlate chúng.

### Scene 1 — Actuator bàn giao sang hạ tầng observability bên ngoài ở đâu?

**Time:** `02:29–03:06`

**Visual:**

Vẽ application boundary phát ra Micrometer registry/observation bridge, management endpoint và diagnostics. Bên ngoài đặt collector/exporter, durable storage, dashboard, alerting, trace backend, log indexing, incident workflow.

**Script:**

Actuator và Boot tích hợp operational state cục bộ của ứng dụng với Micrometer và các management surface chọn lọc. Hạ tầng observability bên ngoài sở hữu transport ở quy mô lớn, retention bền vững, query nhiều instance, dashboard, alerting, trace backend, log indexing và workflow vận hành. Boot có thể auto-configure registry/bridge khi dependency có mặt; điều đó không biến Actuator thành chủ sở hữu kiến trúc Prometheus, OpenTelemetry hay centralized logging.

**Purpose:**

Làm handoff từ application-local integration sang system-wide observability infrastructure thật rõ.

## Phân loại lỗi quản trị Actuator như thế nào trước khi chọn cách sửa?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:06–03:16`

**Visual:**

Giữ end-to-end production flow trên màn hình và zoom vào đúng layer của `Phân loại lỗi quản trị Actuator như thế nào trước khi chọn cách sửa?`—consumer type, signal type, infrastructure handoff, failure branch hoặc ownership map cuối.

**Script:**

Observability handoff chỉ hữu ích khi nhận ra lớp nào đang fail. Decision tree theo symptom giúp tránh configuration thrashing.

**Purpose:**

Biến layered architecture thành failure-classification tree dựa trên symptom trước khi đề xuất bất kỳ fix nào.

### Scene 1 — Phân loại lỗi quản trị Actuator như thế nào trước khi chọn cách sửa?

**Time:** `03:16–03:54`

**Visual:**

Tạo decision tree troubleshooting: 404/missing route → enablement/exposure/base path/prerequisite; connection failure → listener/network; 401/403 → security; missing/masked detail → visibility/sanitization; valid unhealthy → contributor owner; missing metric → instrumentation/registry; dump tải được → JVM analysis.

**Script:**

Hãy phân loại failure trước khi đổi cấu hình. URL thiếu thường dẫn tới enablement, exposure, base path hoặc prerequisite. Không kết nối được dẫn tới listener/network. 401/403 dẫn tới Spring Security. Detail thiếu hoặc bị che dẫn tới visibility/sanitization. Response hợp lệ nhưng unhealthy nghĩa là management layer đã làm đúng—hãy điều tra contributor. Metric thiếu có thể do instrumentation chưa tạo meter, còn dump tải thành công thì analysis đã chuyển sang JVM tooling.

**Purpose:**

Khôi phục toàn bộ failure-classification model để troubleshooting chọn đúng owner thay vì thay Actuator property liên tục.

## Module hoặc miền nào sở hữu lớp chi tiết tiếp theo?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:54–04:07`

**Visual:**

Giữ end-to-end production flow trên màn hình và zoom vào đúng layer của `Module hoặc miền nào sở hữu lớp chi tiết tiếp theo?`—consumer type, signal type, infrastructure handoff, failure branch hoặc ownership map cuối.

**Script:**

Decision tree cho biết lớp nào fail. Scene cuối gọi đúng tên owner của từng lớp để investigation tiếp tục mà không kéo Actuator vượt quá responsibility.

**Purpose:**

Map từng failure đã phân loại sang neighboring owner để investigation tiếp tục mà không kéo Actuator vượt scope.

### Scene 1 — Module hoặc miền nào sở hữu lớp chi tiết tiếp theo?

**Time:** `04:07–04:40`

**Visual:**

Kết thúc bằng ownership map: application-runtime cho lifecycle/availability/logging bootstrap; externalized-configuration cho property resolution; Spring Security cho auth; Micrometer/observability cho meter/telemetry design; JVM analysis cho dump; web-runtime cho server behavior; Actuator cho management projection/integration.

**Script:**

Actuator sở hữu management endpoint model của Boot, tích hợp enablement/exposure, health representation, probe group, info/environment view, metrics inspection, logger control, diagnostic delivery, custom endpoint và management-access integration. Lifecycle timing thuộc application-runtime; property resolution thuộc externalized-configuration; authentication/authorization mechanics thuộc Spring Security; metric/telemetry architecture thuộc Micrometer/observability; dump interpretation thuộc JVM analysis; server behavior thuộc web-runtime. Biết handoff cũng là một phần của việc hiểu Actuator.

**Purpose:**

Để lại một ownership map bền vững cho biết lớp diagnosis tiếp theo nằm ở module hoặc domain nào.
