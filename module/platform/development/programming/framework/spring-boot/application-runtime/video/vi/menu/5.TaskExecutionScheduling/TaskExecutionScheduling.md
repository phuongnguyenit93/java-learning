---
video:
  url: ""
---

# Auto-configuration cho task execution và scheduling

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

## Vì sao Boot auto-configure hạ tầng task execution?

<!-- VIDEO_SECTION -->

### Scene 1 — Vì sao Boot auto-configure hạ tầng task execution?

**Time:** `00:00–00:52`

**Visual:**

Progressive reveal trên visual của chương: hiện Boot auto-configure executor/scheduler chỉ khi application infrastructure chưa tồn tại.

**Script:**

Trên managed-execution path, các tích hợp framework có thể cần chạy công việc bất đồng bộ, còn công việc theo lịch cần scheduler. Nhiều ứng dụng cần executor ngay cả khi mục tiêu của nó không phải học concurrency. Vai trò của Spring Boot là cung cấp hạ tầng hợp lý khi ứng dụng chưa tự cung cấp, đồng thời cung cấp property cho những nhu cầu tinh chỉnh phổ biến. Trong Spring Boot 3.3, nếu context không có `Executor` bean, Boot auto-configure một `AsyncTaskExecutor`. Với platform thread, cách triển khai là `ThreadPoolTaskExecutor`; khi bật virtual threads, Boot dùng `SimpleAsyncTaskExecutor` chạy virtual thread. Scheduler cũng được auto-configure theo cùng tư tưởng khi ứng dụng cần thực thi task theo lịch.

**Purpose:**

Giải thích vì sao Boot provision task infrastructure như một tích hợp runtime nhưng không sở hữu correctness của concurrency.


## Khi nào Boot cung cấp và sử dụng `AsyncTaskExecutor`?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:52–01:04`

**Visual:**

Giữ caller → executor/scheduler → worker và chuyển highlight sang consumer, tuning namespace, custom bean hoặc ownership edge kế tiếp.

**Script:**

Sau khi biết vì sao Boot cung cấp task infrastructure, cần xem ai đang dùng `AsyncTaskExecutor`; danh sách consumer quyết định phạm vi ảnh hưởng của customization.

**Purpose:**

Làm rõ consumer thật của executor trước khi tuning hoặc replacement thay đổi shared infrastructure.

### Scene 2 — Khi nào Boot cung cấp và sử dụng `AsyncTaskExecutor`?

**Time:** `01:04–01:42`

**Visual:**

Progressive reveal trên visual của chương: mở `/runtime/task-executor`, highlight caller/worker/type/virtual flag và nối executor tới supported consumer.

**Script:**

`applicationTaskExecutor` của Boot là shared runtime infrastructure, không chỉ là bean tiện dụng. Integration được hỗ trợ có thể dùng `AsyncTaskExecutor`, nên thay nó có thể ảnh hưởng nhiều hơn một method `@Async`. `GET /spring-boot/runtime/task-executor` submit một bounded task rồi trả caller thread, worker thread, concrete executor type và `workerVirtual`; `differentThread=true` chứng minh dispatch thật qua executor. Trước khi thay default, kiểm tra type và conventional bean-name contract của mọi integration đang dùng nó.

**Purpose:**

Làm rõ contract dùng chung của `applicationTaskExecutor` và chứng minh dispatch thật bằng endpoint Step 5 trước khi customization.


## Các property `spring.task.execution.*` tinh chỉnh execution bằng platform thread như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:42–01:54`

**Visual:**

Giữ caller → executor/scheduler → worker và chuyển highlight sang consumer, tuning namespace, custom bean hoặc ownership edge kế tiếp.

**Script:**

Khi contract executor dùng chung đã rõ, ta tinh chỉnh implementation bằng platform thread qua capacity properties thay vì thay đổi code không liên quan.

**Purpose:**

Chuyển từ consumer impact sang capacity control thực sự tinh chỉnh platform-thread executor.

### Scene 3 — Các property `spring.task.execution.*` tinh chỉnh execution bằng platform thread như thế nào?

**Time:** `01:54–02:32`

**Visual:**

Progressive reveal trên visual của chương: vẽ `ThreadPoolTaskExecutor` core=8, queue, max-size, keep-alive và map `spring.task.execution.*` vào capacity model.

**Script:**

Trên platform-thread path, `spring.task.execution.*` mô tả pool-capacity model thật. Boot 3.3 bắt đầu với tám core thread; `pool.max-size`, `queue-capacity`, `keep-alive`, shutdown setting và thread-name prefix điều chỉnh `ThreadPoolTaskExecutor`. Bounded queue quyết định lúc nào pool được phép tăng, nên chỉ tăng `max-size` có thể chưa tạo khác biệt nếu queue chưa gây áp lực. Hãy coi đây là workload-capacity control chứ không phải nút “làm nhanh hơn”; đo latency, queueing và downstream limit trước khi tuning.

**Purpose:**

Nối `spring.task.execution.*` với capacity của platform-thread pool mà không biến pool number thành performance knob phổ quát.


## Khi nào Boot cung cấp task scheduler?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:32–02:44`

**Visual:**

Giữ caller → executor/scheduler → worker và chuyển highlight sang consumer, tuning namespace, custom bean hoặc ownership edge kế tiếp.

**Script:**

Task execution và scheduling là hai runtime service liên quan nhưng khác nhau, nên chuyển từ application executor sang scheduler mà Boot cung cấp cho scheduled work.

**Purpose:**

Tách executor work khỏi scheduled work trước khi giới thiệu scheduler như runtime service riêng của Boot.

### Scene 4 — Khi nào Boot cung cấp task scheduler?

**Time:** `02:44–03:39`

**Visual:**

Progressive reveal trên visual của chương: hiện default `ThreadPoolTaskScheduler` một thread với hai job chờ cùng scheduler.

**Script:**

Với scheduled work, trên đường platform thread, Boot dùng `ThreadPoolTaskScheduler`; trong Boot 3.3 pool mặc định có một thread. Boot cung cấp task scheduler khi cần thực thi task theo lịch, ví dụ khi scheduling được bật. Scheduler là hạ tầng runtime chứ không phải nơi định nghĩa ngữ nghĩa scheduling. Spring Framework quyết định cách method `@Scheduled` được phát hiện và gọi; Boot chỉ cung cấp/cấu hình cách triển khai để cơ chế framework sử dụng. Khi scheduled job chạy trễ, hãy phân loại: scheduler có thiếu năng lực hoặc bị chặn, biểu thức scheduling có sai, task có chạy quá lâu, hay chính sách thực thi đồng thời có vấn đề. Chỉ phần cung cấp/cấu hình scheduler là trách nhiệm chính của Boot.

**Purpose:**

Tách provisioning scheduler khỏi scheduling semantics và cho thấy default platform scheduler một thread.


## Các property `spring.task.scheduling.*` tinh chỉnh scheduling như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:39–03:51`

**Visual:**

Giữ caller → executor/scheduler → worker và chuyển highlight sang consumer, tuning namespace, custom bean hoặc ownership edge kế tiếp.

**Script:**

Khi scheduler đã có trên sơ đồ, có thể tinh chỉnh default platform-thread qua scheduling namespace và quan sát hệ quả concurrency riêng của nó.

**Purpose:**

Gắn scheduling properties vào scheduler model để hệ quả concurrency không bị lẫn với executor tuning.

### Scene 5 — Các property `spring.task.scheduling.*` tinh chỉnh scheduling như thế nào?

**Time:** `03:51–04:29`

**Visual:**

Progressive reveal trên visual của chương: tăng scheduler pool và animate job overlap cùng downstream concurrency pressure.

**Script:**

Với platform-thread `ThreadPoolTaskScheduler`, `spring.task.scheduling.*` điều khiển default như `pool.size`, thread-name prefix và shutdown waiting. Boot bắt đầu bảo thủ với một scheduler thread. Pool lớn hơn có thể cho scheduled job độc lập chạy chồng nhau nhưng cũng tăng concurrency lên database, API, lock và shared state; nó không làm task chậm hoặc không thread-safe trở nên đúng. Khi bật virtual thread, pooled scheduler model này không còn active nên pool setting cũ không thể mang nguyên sang.

**Purpose:**

Giải thích `spring.task.scheduling.*` thay đổi gì và vì sao tăng pool tạo thêm concurrency pressure chứ không sửa task chậm.


## Điều gì thay đổi khi ứng dụng tự cung cấp executor hoặc scheduler?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:29–04:41`

**Visual:**

Giữ caller → executor/scheduler → worker và chuyển highlight sang consumer, tuning namespace, custom bean hoặc ownership edge kế tiếp.

**Script:**

Các property chỉ mô tả default của Boot cho đến khi ứng dụng tự cung cấp bean; bước kế tiếp là hiểu back-off và compatibility với consumer.

**Purpose:**

Cho thấy nơi property-driven default dừng và application-owned executor/scheduler bean bắt đầu.

### Scene 6 — Điều gì thay đổi khi ứng dụng tự cung cấp executor hoặc scheduler?

**Time:** `04:41–05:37`

**Visual:**

Progressive reveal trên visual của chương: đưa custom executor/scheduler bean vào và cho default Boot back off nhưng giữ consumer name/type contract.

**Script:**

Khi ứng dụng tự cung cấp bean, vì vậy executor/scheduler tùy biến có thể thay thế một phần bố trí mặc định chứ không chỉ thêm bean mới. Boot auto-configuration chủ động để back off khi ứng dụng chủ động cung cấp hạ tầng riêng. Điều phải kiểm tra là tính tương thích với thành phần sử dụng. Nhiều executor có thể cần quy ước tên bean như `taskExecutor` hoặc `applicationTaskExecutor`; tích hợp web có thể yêu cầu chính kiểu `AsyncTaskExecutor`. Boot cũng cung cấp builder beans để ứng dụng tạo cách triển khai tùy biến dựa trên các quy ước/giá trị mặc định đã được Boot chuẩn bị. Chỉ tùy biến khi workload cần mức cô lập, năng lực, cách đặt tên hoặc chính sách lifecycle khác.

**Purpose:**

Cho thấy custom executor/scheduler làm Boot back off và tạo compatibility contract do ứng dụng sở hữu.


## Boot auto-configuration bàn giao sang ngữ nghĩa concurrency của Spring ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:37–05:48`

**Visual:**

Giữ caller → executor/scheduler → worker và chuyển highlight sang consumer, tuning namespace, custom bean hoặc ownership edge kế tiếp.

**Script:**

Sau provisioning, tuning và back-off, dừng ở đúng boundary: Boot cung cấp hạ tầng, còn Spring Framework và Java sở hữu semantics concurrency.

**Purpose:**

Khép chương bằng cách tách Boot provisioning khỏi Spring scheduling semantics và Java thread correctness.

### Scene 7 — Boot auto-configuration bàn giao sang ngữ nghĩa concurrency của Spring ở đâu?

**Time:** `05:48–06:47`

**Visual:**

Progressive reveal trên visual của chương: triage ownership: Boot provisioning/property, Spring `@Async`/`@Scheduled`, Java thread-safety semantics.

**Script:**

Tại concurrency handoff, Spring Framework sở hữu ngữ nghĩa của `@Async`, `@EnableAsync`, `@Scheduled`, `@EnableScheduling`, task decorators và các abstraction concurrency của framework. Chương này dừng ở ranh giới cung cấp hạ tầng của Boot: Boot tạo executor/scheduler nào, namespace nào cấu hình chúng, thành phần nào sử dụng và khi nào auto-configuration back off. Java concurrency sở hữu thread safety, memory visibility, synchronization, interruption, cơ chế executor nói chung và ngữ nghĩa sâu của virtual threads. Khi chẩn đoán, hãy phân loại trước. "Vì sao Boot tạo executor này?" thuộc chương này. "Vì sao scheduled method chạy chồng nhau?" chủ yếu là Spring scheduling. "Vì sao trạng thái dùng chung có thể thay đổi bị hỏng?" là Java concurrency. Phân ranh giới đúng giúp tránh sửa cấu hình để che lỗi về tính đúng đắn.

**Purpose:**

Dừng Boot ở provisioning boundary, rồi chuyển `@Async`/`@Scheduled` semantics và thread correctness sang Spring/Java concurrency.
