---
video:
  url: ""
---

# Vòng đời ứng dụng và application events

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

## Chuỗi event của SpringApplication diễn tiến như thế nào?

<!-- VIDEO_SECTION -->

### Scene 1 — Chuỗi event của SpringApplication diễn tiến như thế nào?

**Time:** `00:00–00:38`

**Visual:**

Progressive reveal trên visual của chương: dựng Starting → EnvironmentPrepared → ContextInitialized → Prepared → Started → LIVE → Ready → READY và giữ FAILED như nhánh riêng.

**Script:**

Trên event timeline, với Spring Boot 3.3, thứ tự chính là `ApplicationStartingEvent`, `ApplicationEnvironmentPreparedEvent`, `ApplicationContextInitializedEvent`, `ApplicationPreparedEvent`, `ApplicationStartedEvent`, một `AvailabilityChangeEvent` cho liveness, `ApplicationReadyEvent`, rồi `AvailabilityChangeEvent` cho readiness. Lifecycle event của Boot đánh dấu các mốc có tên trên đường startup. Nếu startup gặp exception, `ApplicationFailedEvent` biểu diễn nhánh thất bại. Thứ tự quan trọng vì mỗi mốc có bảo đảm khác nhau. Trước khi context tồn tại, listener không thể dựa vào bean. Sau refresh, context đã sẵn sàng.

**Purpose:**

Biến tên event thành lifecycle guarantee có thứ tự để đặt listener theo state thật sự có sẵn.


## Những event nào xảy ra trước khi ApplicationContext tồn tại?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:38–00:52`

**Visual:**

Giữ event timeline; đẩy phase marker tiến lên hoặc rẽ nhánh FAILED để thấy guarantee vừa thay đổi.

**Script:**

Thứ tự event chỉ hữu ích khi biết mỗi checkpoint nhìn thấy trạng thái nào; vì vậy bắt đầu với nhóm event chạy trước khi bean graph thông thường tồn tại.

**Purpose:**

Dùng event order để lộ boundary đầu tiên: notification nào xảy ra trước khi normal bean tồn tại.

### Scene 2 — Những event nào xảy ra trước khi ApplicationContext tồn tại?

**Time:** `00:52–01:50`

**Visual:**

Progressive reveal trên visual của chương: làm mờ bean graph khi highlight pre-context event và object nào đã tồn tại.

**Script:**

Tại checkpoint này, `ApplicationStartingEvent` xuất hiện gần đầu `run`, `ApplicationEnvironmentPreparedEvent` xuất hiện khi `Environment` đã biết nhưng context chưa được tạo, còn `ApplicationContextInitializedEvent` xuất hiện sau khi các context initializer chạy nhưng trước khi bean definitions được nạp. Các event sớm nhất của SpringApplication được phát khi bean graph thông thường chưa tồn tại. Những giai đoạn này phù hợp với hạ tầng thực sự cần quan sát bootstrap rất sớm. Chúng không phù hợp cho application service thông thường vì dependency injection và bean ứng dụng chưa sẵn sàng. Vì vậy câu hỏi nên là "công việc này cần nhìn thấy trạng thái nào?" thay vì "tôi có thể nghe event nào?" Nếu cần repository/service bình thường, hãy chờ giai đoạn muộn hơn thay vì ép bước khởi tạo nghiệp vụ vào bootstrap hook.

**Purpose:**

Cho thấy pre-context event là bootstrap hook và không thể an toàn phụ thuộc vào application bean thông thường.


## Context refresh, `ApplicationStartedEvent` và `ApplicationReadyEvent` khác nhau thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:01`

**Visual:**

Giữ event timeline; đẩy phase marker tiến lên hoặc rẽ nhánh FAILED để thấy guarantee vừa thay đổi.

**Script:**

Khi ranh giới pre-context đã rõ, ta so nó với refresh, started và ready, nơi runtime cung cấp các guarantee ngày càng mạnh hơn.

**Purpose:**

Đặt pre-context visibility cạnh guarantee mạnh hơn sau refresh, started và ready.

### Scene 3 — Context refresh, `ApplicationStartedEvent` và `ApplicationReadyEvent` khác nhau thế nào?

**Time:** `02:01–02:39`

**Visual:**

Progressive reveal trên visual của chương: zoom refresh → Started → LIVE → runners → Ready → READY cạnh observation `/runtime/lifecycle`.

**Script:**

Refresh, started và ready là ba guarantee khác nhau. Context refresh nghĩa là bean graph đã sẵn sàng. `ApplicationStartedEvent` đến sau refresh nhưng trước `ApplicationRunner`/`CommandLineRunner`; `ApplicationReadyEvent` chỉ đến sau runner. Boot gắn availability theo cùng thứ tự: liveness thành `CORRECT` ở started, readiness thành `ACCEPTING_TRAFFIC` sau ready. `/spring-boot/runtime/lifecycle` capture đúng chuỗi đó, nên runner chậm có thể khiến process đã live nhưng vẫn trì hoãn readiness.

**Purpose:**

Phân biệt refresh, started/LIVE, runner, ready và readiness để đặt startup work đúng checkpoint.


## Điều gì xảy ra trên nhánh startup thất bại?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:39–02:49`

**Visual:**

Giữ event timeline; đẩy phase marker tiến lên hoặc rẽ nhánh FAILED để thấy guarantee vừa thay đổi.

**Script:**

Đường startup thành công đã rõ; mô hình lifecycle còn cần nhánh failure cho trường hợp không bao giờ tới ready.

**Purpose:**

Bổ sung failure branch để lifecycle model giải thích được startup không bao giờ tới ready.

### Scene 4 — Điều gì xảy ra trên nhánh startup thất bại?

**Time:** `02:49–03:29`

**Visual:**

Progressive reveal trên visual của chương: rẽ timeline sang `ApplicationFailedEvent`, root exception và failure-analysis evidence.

**Script:**

Sau context refresh, nếu exception thoát khỏi quá trình startup, Boot phát `ApplicationFailedEvent`. Không phải startup nào cũng đi đến `ApplicationReadyEvent`. Listener bootstrap có thể dùng event này để ghi nhận lỗi và quan sát exception đã kết thúc startup. Event chỉ là một phần của nhánh lỗi. Boot còn cho `FailureAnalyzer` chuyển các lỗi đã biết thành phần mô tả và hướng xử lý tập trung. Khi condition của auto-configuration có liên quan, condition evaluation report là bằng chứng bổ sung chứ không thay thế exception gốc.

**Purpose:**

Cho thấy `ApplicationFailedEvent` và failure analysis giữ bằng chứng khi happy path không bao giờ tới ready.


## Vì sao một số listener phải được đăng ký trước khi bean được tạo?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:29–03:42`

**Visual:**

Giữ event timeline; đẩy phase marker tiến lên hoặc rẽ nhánh FAILED để thấy guarantee vừa thay đổi.

**Script:**

Event rất sớm tạo ra bài toán đăng ký listener: một listener chỉ được tạo như bean bình thường có thể chưa tồn tại lúc event được publish.

**Purpose:**

Làm rõ hệ quả registration của early event: listener không thể nghe event xảy ra trước lúc chính listener bean tồn tại.

### Scene 5 — Vì sao một số listener phải được đăng ký trước khi bean được tạo?

**Time:** `03:42–04:32`

**Visual:**

Progressive reveal trên visual của chương: so early listener đăng ký trên `SpringApplication`/builder với listener `@Bean` chưa tồn tại.

**Script:**

Trên failure branch, vì vậy Boot cho phép đăng ký listener sớm trực tiếp bằng `SpringApplication.addListeners(...)`, `SpringApplicationBuilder.listeners(...)` hoặc cơ chế đăng ký listener tự động được hỗ trợ. Listener chỉ tồn tại dưới dạng `@Bean` bình thường không thể quan sát event xảy ra trước khi `ApplicationContext` tạo bean đó. Đây là ràng buộc của lifecycle, không phải mẹo dependency injection. Chỉ đưa listener ra ngoài bean lifecycle khi event thực sự xảy ra sớm. Với event muộn, listener dạng bean thường dễ quản lý dependency và kiểm thử hơn. Listener càng sớm thì càng nên có trách nhiệm hẹp, vì càng ít application service có thể dùng an toàn.

**Purpose:**

Giải thích vì sao listener rất sớm phải được đăng ký ngoài bean lifecycle và nên giữ responsibility hẹp.


## Khi nào application event là runtime hook phù hợp?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:32–04:43`

**Visual:**

Giữ event timeline; đẩy phase marker tiến lên hoặc rẽ nhánh FAILED để thấy guarantee vừa thay đổi.

**Script:**

Sau timing và registration, câu hỏi còn lại là intent: lúc nào nên quan sát lifecycle bằng event thay vì runner hoặc background task?

**Purpose:**

Chuyển từ timing sang hook selection để event observation không bị lẫn với startup work hoặc background execution.

### Scene 6 — Khi nào application event là runtime hook phù hợp?

**Time:** `04:43–05:34`

**Visual:**

Progressive reveal trên visual của chương: đặt event, runner và executor/scheduler card vào lifecycle phase mà mỗi cơ chế phục vụ.

**Script:**

Khi đặt listener, không nên đặt công việc kéo dài vào event listener đồng bộ chỉ vì event xuất hiện đúng thời điểm mong muốn. Application event phù hợp khi công việc về bản chất là quan sát hoặc phản ứng với một chuyển trạng thái lifecycle: môi trường đã chuẩn bị, context đã khởi động, readiness thay đổi, hoặc nhiều listener độc lập cần phản ứng mà publisher không biết trực tiếp về chúng. Spring application events mặc định được phát trên cùng thread, nên listener nặng có thể kéo dài hoặc chặn đường startup. Công việc phải hoàn tất trước readiness thường phù hợp runner; công việc liên tục nên chạy trên executor/scheduler.

**Purpose:**

Đưa ra tiêu chí chọn event cho lifecycle observation/reaction thay vì dùng event như chỗ tiện để chạy công việc dài.
