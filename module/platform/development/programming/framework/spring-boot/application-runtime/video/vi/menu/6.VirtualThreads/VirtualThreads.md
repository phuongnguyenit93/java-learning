---
video:
  url: ""
---

# Virtual threads trong Spring Boot

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

## `spring.threads.virtual.enabled` thay đổi điều gì?

<!-- VIDEO_SECTION -->

### Scene 1 — `spring.threads.virtual.enabled` thay đổi điều gì?

**Time:** `00:00–00:38`

**Visual:**

Progressive reveal trên visual của chương: toggle `spring.threads.virtual.enabled` và đổi platform-thread implementation sang simple implementation dùng virtual thread.

**Script:**

Coi `spring.threads.virtual.enabled=true` là integration switch của Boot. Với Java 21, Boot giữ abstraction executor/scheduler nhưng đổi infrastructure được hỗ trợ phía sau: task execution dùng `SimpleAsyncTaskExecutor` bật virtual thread thay cho `ThreadPoolTaskExecutor`, còn scheduling dùng `SimpleAsyncTaskScheduler` bật virtual thread thay cho pooled scheduler. Property này không thay executor do application code tự tạo; nó đồng bộ hạ tầng do Boot quản lý theo một lựa chọn cấp ứng dụng.

**Purpose:**

Đặt property của Boot đúng vai trò switch tích hợp cho infrastructure được hỗ trợ, không biến nó thành tuyên bố về toàn bộ Java virtual thread.


## Chiến lược executor và scheduler của Boot thay đổi như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:38–00:50`

**Visual:**

Tách execution diagram thành lane platform thread và virtual thread rồi chuyển highlight sang strategy hoặc hệ quả JVM lifetime kế tiếp.

**Script:**

Application switch có ý nghĩa vì nó thay implementation phía sau executor/scheduler do Boot quản lý, chứ không thay ngữ nghĩa Java trên toàn JVM.

**Purpose:**

Biến một Boot switch thành concrete executor/scheduler implementation change mà nó thật sự điều khiển.

### Scene 2 — Chiến lược executor và scheduler của Boot thay đổi như thế nào?

**Time:** `00:50–01:45`

**Visual:**

Progressive reveal trên visual của chương: giữ cùng executor/scheduler abstraction nhưng thay implementation phía dưới.

**Script:**

Phía sau executor abstraction, ứng dụng vẫn dùng abstraction executor/scheduler, nhưng cách triển khai không còn mô hình một pool worker cố định/có giới hạn giống đường platform thread. Bật virtual thread làm thay đổi chiến lược triển khai phía sau hạ tầng task của Boot. `SimpleAsyncTaskExecutor` có thể tạo virtual thread cho task được gửi vào, còn `SimpleAsyncTaskScheduler` sử dụng virtual-thread execution cho công việc theo lịch. Các builder tương ứng do Boot cung cấp cũng được cấu hình theo lựa chọn virtual thread. Vì vậy nên đánh giá việc chuyển đổi ở ranh giới abstraction thay vì tìm từng chỗ tạo virtual thread. Nếu ứng dụng tự tạo executor ngoài Boot, `spring.threads.virtual.enabled` không tự động quản lý hạ tầng do ứng dụng sở hữu.

**Purpose:**

Cho thấy concrete executor/scheduler strategy thay đổi phía sau cùng abstraction để migration được hiểu ở Boot boundary.


## Vì sao các property kích thước pool không còn mô tả cùng một mô hình?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:45–01:57`

**Visual:**

Tách execution diagram thành lane platform thread và virtual thread rồi chuyển highlight sang strategy hoặc hệ quả JVM lifetime kế tiếp.

**Script:**

Khi implementation strategy đổi, phép tính pool cũ không còn mô tả cùng runtime model, nên các giả định về pool size phải được xem lại.

**Purpose:**

Dùng implementation change đó để loại bỏ giả định tuning pool cũ trước khi bàn tới capacity của virtual thread.

### Scene 3 — Vì sao các property kích thước pool không còn mô tả cùng một mô hình?

**Time:** `01:57–02:48`

**Visual:**

Progressive reveal trên visual của chương: làm mờ pool-sizing control ở virtual-thread lane vì fixed-worker math không còn ánh xạ trực tiếp.

**Script:**

Khi worker model thay đổi, mô hình đó không ánh xạ trực tiếp sang `SimpleAsyncTaskExecutor`/`SimpleAsyncTaskScheduler` dùng virtual thread. Các property về kích thước pool mô tả cách quản lý một tập platform worker threads. Spring Boot 3.3 ghi rõ các scheduler property liên quan pooling bị bỏ qua khi virtual threads được bật. Do đó quy tắc tinh chỉnh cũ như "tăng `spring.task.scheduling.pool.size`" có thể không còn mô tả runtime đang chạy. Không nên mang nguyên cách tính của pool platform thread sang đường virtual thread rồi giả định nó vẫn điều khiển mức song song giống trước. Virtual threads vẫn tiêu thụ CPU, bộ nhớ, connection, rate limit và năng lực của hệ thống phía sau.

**Purpose:**

Ngăn việc mang nguyên tuning pool của platform thread sang mô hình execution dựa trên virtual thread.


## Vì sao daemon virtual thread có thể cần `spring.main.keep-alive`?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:48–03:00`

**Visual:**

Tách execution diagram thành lane platform thread và virtual thread rồi chuyển highlight sang strategy hoặc hệ quả JVM lifetime kế tiếp.

**Script:**

Hạ tầng dựa trên virtual thread còn thay giả định về process liveness vì virtual thread là daemon thread; đây là nơi `spring.main.keep-alive` xuất hiện.

**Purpose:**

Nối daemon-thread semantics với process lifetime, chính là lý do cụ thể để keep-alive property tồn tại.

### Scene 4 — Vì sao daemon virtual thread có thể cần `spring.main.keep-alive`?

**Time:** `03:00–03:38`

**Visual:**

Progressive reveal trên visual của chương: hiện chỉ còn daemon virtual thread, rủi ro JVM exit rồi `spring.main.keep-alive=true` như process-lifetime anchor.

**Script:**

Virtual thread là daemon thread, nên JVM có thể thoát khi không còn non-daemon thread. Điều này quan trọng với non-web hoặc scheduling application nếu công việc tiếp tục nằm hoàn toàn trên virtual thread. `spring.main.keep-alive=true` yêu cầu Boot giữ process sống trong tình huống đó. Hãy bật vì lifetime requirement này, không phải như performance setting: nó không sửa task correctness, scheduling policy hay downstream capacity.

**Purpose:**

Nối daemon virtual thread với JVM process lifetime và giải thích lúc nào cần `spring.main.keep-alive=true`.


## Phần nào vẫn là ngữ nghĩa của Java virtual thread thay vì trách nhiệm của Boot?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:38–03:50`

**Visual:**

Tách execution diagram thành lane platform thread và virtual thread rồi chuyển highlight sang strategy hoặc hệ quả JVM lifetime kế tiếp.

**Script:**

Boot tích hợp công tắc, nhưng scheduling, pinning, interruption và workload suitability vẫn là ngữ nghĩa Java virtual thread, nên chương kết thúc ở boundary đó.

**Purpose:**

Dừng ở Boot integration và bàn giao JVM-level virtual-thread behavior sang Java concurrency.

### Scene 5 — Phần nào vẫn là ngữ nghĩa của Java virtual thread thay vì trách nhiệm của Boot?

**Time:** `03:50–04:47`

**Visual:**

Progressive reveal trên visual của chương: vẽ Boot switch/integration một bên và Java scheduling/carrier/pinning/blocking/interruption semantics bên kia.

**Script:**

Tại Java concurrency boundary, java sở hữu ngữ nghĩa bên dưới: cách virtual thread được schedule, blocking/pinning, carrier thread, daemon status, interruption, hành vi thread-local và quyết định workload nào phù hợp. Boot sở hữu integration switch và những tích hợp được hỗ trợ phản ứng với công tắc đó. Ranh giới này quan trọng vì `spring.threads.virtual.enabled=true` không phải lời hứa rằng mọi workload nhanh hơn. Workload CPU-bound vẫn tranh CPU; thư viện hoặc code có thể tạo điều kiện làm khả năng mở rộng khác kỳ vọng. Dùng tài liệu Boot để biết thành phần do Boot quản lý thay đổi ra sao. Dùng phần kiến thức Java concurrency và hướng dẫn JDK để hiểu vì sao virtual thread hành xử như vậy và cách chẩn đoán concurrency ở cấp JVM.

**Purpose:**

Giữ ownership của Boot ở switch/integration và chuyển pinning, carrier, blocking, interruption, workload suitability sang Java concurrency.
