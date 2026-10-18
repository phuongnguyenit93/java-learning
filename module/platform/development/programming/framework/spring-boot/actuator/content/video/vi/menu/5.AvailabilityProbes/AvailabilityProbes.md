---
video:
  url: ""
---

# Liveness, readiness và health probe

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

## Actuator sử dụng application availability của Boot như thế nào?

<!-- VIDEO_SECTION -->

### Scene 1 — Actuator sử dụng application availability của Boot như thế nào?

**Time:** `00:00–00:37`

**Visual:**

Vẽ `ApplicationAvailability` nằm ngoài biên Actuator. Cho `LivenessState` đi vào `LivenessStateHealthIndicator`, `ReadinessState` đi vào `ReadinessStateHealthIndicator`, sau đó cả hai nhập vào health tree.

**Script:**

Liveness và readiness trước hết là trạng thái runtime, sau đó mới trở thành management data. Application runtime sở hữu `ApplicationAvailability` và quyết định thời điểm state thay đổi. Actuator chỉ đọc trạng thái hiện tại qua các health indicator chuyên biệt rồi đưa vào health model. Vì thế một probe request chỉ quan sát lifecycle state; nó không làm runner hoàn tất, không tự khiến ứng dụng ready và cũng không điều khiển shutdown.

**Purpose:**

Cố định chiều bàn giao một chiều từ lifecycle state sang health representation của Actuator.

## Liveness và readiness trở thành health group như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:37–00:49`

**Visual:**

Giữ sơ đồ liveness/readiness liên tục; ở `Liveness và readiness trở thành health group như thế nào?` chỉ chuyển focus giữa state source, health group, HTTP path, dependency policy hoặc lifecycle owner để mạch không bị reset.

**Script:**

Khi availability đã đi vào health tree, health group giúp mỗi state có một operational contract riêng thay vì trộn chung vào health tổng thể.

**Purpose:**

Biến lifecycle state thành health group tập trung mà không chuyển quyền sở hữu state sang Actuator.

### Scene 1 — Liveness và readiness trở thành health group như thế nào?

**Time:** `00:49–01:23`

**Visual:**

Hiện hai health group `liveness` và `readiness` với availability indicator tương ứng. Gắn badge Kubernetes “auto-enabled”, đặt `management.endpoint.health.probes.enabled` cho môi trường cần bật rõ, rồi thêm knob theo group cho contributor include, detail visibility và status behavior.

**Script:**

Actuator biểu diễn hai availability signal thành health group `liveness` và `readiness`, dùng đúng indicator của state tương ứng. Trong Kubernetes, Boot tự bật các probe group này; ở môi trường khác có thể bật rõ bằng `management.endpoint.health.probes.enabled`. Vì chúng là health group bình thường, ta vẫn cấu hình được contributor include, mức detail và status behavior theo từng group. Giữ hai contract khác nhau: liveness hỏi có nên restart process, readiness hỏi có nên route traffic vào instance.

**Purpose:**

Khôi phục cách probe group được bật và cấu hình, đồng thời giữ liveness/readiness là hai operational contract khác nhau.

## Các endpoint probe liveness và readiness cung cấp góc nhìn vận hành nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:23–01:34`

**Visual:**

Giữ sơ đồ liveness/readiness liên tục; ở `Các endpoint probe liveness và readiness cung cấp góc nhìn vận hành nào?` chỉ chuyển focus giữa state source, health group, HTTP path, dependency policy hoặc lifecycle owner để mạch không bị reset.

**Script:**

Health group mới định nghĩa ý nghĩa; probe path biến ý nghĩa đó thành endpoint mà deployment platform có thể gọi thật.

**Purpose:**

Từ semantics của group chuyển sang HTTP path cụ thể mà deployment platform sẽ gọi.

### Scene 1 — Các endpoint probe liveness và readiness cung cấp góc nhìn vận hành nào?

**Time:** `01:34–02:07`

**Visual:**

Gọi `/actuator/health/liveness` và `/actuator/health/readiness`. Sau đó bật additional probe paths và hiện `/livez`, `/readyz` trên main port bên cạnh separate management port.

**Script:**

Với management base path mặc định, hai probe group xuất hiện tại `/actuator/health/liveness` và `/actuator/health/readiness` khi group đã bật và health được expose. Nếu management chạy trên cổng riêng, Boot còn có thể thêm path liveness/readiness lên main server port. Điều đó hữu ích khi probe cần kiểm tra chính listener phục vụ application traffic, không chỉ một management server vẫn còn khỏe.

**Purpose:**

Cho người học thấy URL probe thật và lý do main-port probes có thể tránh false positive khi management server tách riêng.

## Vì sao phải thận trọng khi đưa phụ thuộc bên ngoài vào probe?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:07–02:19`

**Visual:**

Giữ sơ đồ liveness/readiness liên tục; ở `Vì sao phải thận trọng khi đưa phụ thuộc bên ngoài vào probe?` chỉ chuyển focus giữa state source, health group, HTTP path, dependency policy hoặc lifecycle owner để mạch không bị reset.

**Script:**

Khi platform đã gọi được probe, lựa chọn nguy hiểm nhất là đưa bằng chứng nào vào từng group—đặc biệt là dependency dùng chung.

**Purpose:**

Dùng consumer thực của probe để giải thích vì sao external dependency phải được chọn theo recovery behavior.

### Scene 1 — Vì sao phải thận trọng khi đưa phụ thuộc bên ngoài vào probe?

**Time:** `02:19–02:59`

**Visual:**

Dùng sơ đồ ba instance cùng phụ thuộc một database. Đặt database check ra ngoài liveness và cho thấy có thể cân nhắc trong readiness. Làm database fail, so sánh “restart mọi pod” với “tạm ngừng route traffic”.

**Script:**

External dependency phải được chọn cẩn thận. Liveness thường không nên phụ thuộc database, remote API hay cache vì restart process không sửa được shared outage; nếu mọi instance cùng báo liveness fail, orchestrator có thể tạo restart storm. Readiness có thể chứa một số dependency khi service thật sự không nhận traffic được nếu thiếu chúng, nhưng đó là quyết định riêng của ứng dụng. Probe group không phải nơi để nhét toàn bộ health check chỉ vì chúng tồn tại.

**Purpose:**

Giải thích hậu quả theo failure domain để người học thiết kế probe cho recovery behavior thay vì completeness.

## Actuator bàn giao quyền sở hữu availability state về application-runtime ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:59–03:12`

**Visual:**

Giữ sơ đồ liveness/readiness liên tục; ở `Actuator bàn giao quyền sở hữu availability state về application-runtime ở đâu?` chỉ chuyển focus giữa state source, health group, HTTP path, dependency policy hoặc lifecycle owner để mạch không bị reset.

**Script:**

Probe composition cho biết ta đang quan sát gì; section cuối chốt ai được quyền thay đổi state đó. Chủ sở hữu vẫn là lifecycle, không phải Actuator.

**Purpose:**

Khép probe model bằng cách trả state-transition ownership về application-runtime trong startup và shutdown.

### Scene 1 — Actuator bàn giao quyền sở hữu availability state về application-runtime ở đâu?

**Time:** `03:12–03:45`

**Visual:**

Animate startup: liveness → `CORRECT`, runner vẫn chạy, rồi readiness → `ACCEPTING_TRAFFIC`. Animate shutdown theo chiều ngược lại với readiness rời trạng thái nhận traffic trước khi process kết thúc; Actuator chỉ quan sát.

**Script:**

Ranh giới ownership nhìn rõ nhất trong lifecycle. Khi startup, liveness có thể lên `CORRECT` trước readiness `ACCEPTING_TRAFFIC` vì startup runner vẫn chưa xong. Khi shutdown, application runtime đổi readiness như một phần của lifecycle và Actuator chỉ phản ánh signal mới. Nếu probe bất ngờ trả `REFUSING_TRAFFIC`, đổi health group không chữa nguyên nhân; phải lần theo state transition trong miền application-runtime.

**Purpose:**

Khôi phục đầy đủ nuance startup/shutdown và chỉ rõ lúc nào troubleshooting phải rời Actuator để sang application runtime.
