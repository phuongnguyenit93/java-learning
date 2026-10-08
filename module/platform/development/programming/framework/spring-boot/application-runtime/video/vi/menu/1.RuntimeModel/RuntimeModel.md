---
video:
  url: ""
---

# Mô hình runtime của ứng dụng Spring Boot

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

## Spring Boot chịu trách nhiệm phần nào trong application runtime?

<!-- VIDEO_SECTION -->

### Scene 1 — Spring Boot chịu trách nhiệm phần nào trong application runtime?

**Time:** `00:00–00:58`

**Visual:**

Progressive reveal trên visual của chương: tách Boot runtime coordination khỏi ownership Spring container/JVM/application.

**Script:**

Trên runtime map, ở module fundamentals, người học đã biết mô hình bootstrap ở mức tổng quan; tại đây trọng tâm chuyển sang Boot thực hiện việc gì ở thời điểm nào và runtime hook nào chịu trách nhiệm cho việc đó. Application runtime của Spring Boot là lớp điều phối biến một `SpringApplication` đã nhận đủ đầu vào cấu hình thành một tiến trình đang chạy với `ApplicationContext` được quản lý. Boot điều phối quá trình chuẩn bị môi trường và context, phát lifecycle event, gọi startup runner, cập nhật application availability, cung cấp một số hạ tầng runtime qua auto-configuration và đóng context khi JVM shutdown. Bean trong ứng dụng vẫn là Spring bean bình thường và code vẫn chạy trên JVM; Boot bổ sung các quy ước và lớp tích hợp quanh chúng.

**Purpose:**

Tách phần coordination runtime của Boot khỏi ownership của Spring container, JVM và application code trước khi đưa ra quyết định debug.


## Vì sao Spring Boot cần một lớp điều phối runtime?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:58–01:12`

**Visual:**

Giữ runtime timeline trên màn hình và di chuyển highlight tới prerequisite hoặc phase được đưa vào tiếp theo.

**Script:**

Khi đã rõ Boot sở hữu phần coordination nào, lý do tồn tại của layer này trở nên cụ thể: các công việc startup có prerequisite đúng ở những thời điểm khác nhau.

**Purpose:**

Chuyển định nghĩa ownership thành bài toán timing để làm rõ vì sao Boot cần một runtime coordination layer.

### Scene 2 — Vì sao Spring Boot cần một lớp điều phối runtime?

**Time:** `01:12–02:10`

**Visual:**

Progressive reveal trên visual của chương: đặt prerequisite environment/config/classpath trước bean creation và readiness.

**Script:**

Ở phase startup này, cấu hình phải có trước khi bean được tạo, một số thành phần quan sát cần nhìn thấy startup rất sớm, bước khởi tạo có thể cần context đã refresh, còn lưu lượng chỉ nên được nhận khi công việc startup bắt buộc đã hoàn tất. Một ứng dụng thực tế có nhiều loại công việc phải diễn ra ở các thời điểm khác nhau. Nếu xem mọi callback là tương đương, ứng dụng rất dễ gặp lỗi thứ tự và lỗi khó giải thích. `SpringApplication` tạo ra một dòng thời gian chung cho các giai đoạn này. Nhờ đó Boot có thể gắn events, runners, chuyển trạng thái availability, phân tích lỗi, khởi tạo logging, executor được quản lý và dịch vụ phát triển vào những giai đoạn đã biết.

**Purpose:**

Cho thấy lifecycle phase tồn tại vì prerequisite khác nhau, không phải để cung cấp nhiều callback có thể dùng thay nhau.


## Những đầu vào nào định hình runtime trước khi context sẵn sàng?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:10–02:24`

**Visual:**

Giữ runtime timeline trên màn hình và di chuyển highlight tới prerequisite hoặc phase được đưa vào tiếp theo.

**Script:**

Timeline chung chỉ hữu ích nếu biết thứ gì đã định hình runtime trước refresh, vì vậy chuyển từ nhu cầu coordination sang các input tạo nên trạng thái ban đầu.

**Purpose:**

Đi từ nhu cầu coordination sang các input cụ thể quyết định state mà hook ở phase sau có thể nhìn thấy.

### Scene 3 — Những đầu vào nào định hình runtime trước khi context sẵn sàng?

**Time:** `02:24–03:15`

**Visual:**

Progressive reveal trên visual của chương: cho command-line/config/classpath/auto-configuration đi vào pre-refresh timeline.

**Script:**

Khi chẩn đoán, đối số dòng lệnh tham gia vào đầu vào runtime, externalized configuration chuẩn bị giá trị/profile, classpath ảnh hưởng auto-configuration nào có thể khớp và auto-configuration đóng góp bean vào context. Trước khi công việc startup riêng của ứng dụng chạy, runtime đã được định hình bởi các đầu vào do những module Spring Boot trước đó sở hữu. Điểm cần nhớ là các hook không nhìn thấy cùng một thế giới. Event ở giai đoạn môi trường có thể chạy khi `Environment` đã tồn tại nhưng `ApplicationContext` chưa có. Event sau refresh nhìn thấy context hoàn chỉnh hơn. Runner chạy sau refresh nên có thể dùng các bean ứng dụng thông thường.

**Purpose:**

Giúp người học nhận ra early event, hook có context và runner nhìn thấy state khác nhau vì input xuất hiện ở phase khác nhau.


## Các giai đoạn chính từ `SpringApplication.run` đến trạng thái sẵn sàng là gì?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:15–03:26`

**Visual:**

Giữ runtime timeline trên màn hình và di chuyển highlight tới prerequisite hoặc phase được đưa vào tiếp theo.

**Script:**

Khi input đã rõ, ta có thể bỏ mô tả trừu tượng và đặt chính xác các checkpoint từ `run` đến readiness lên timeline.

**Purpose:**

Biến phần input ban đầu thành một lifecycle timeline chính xác trước khi đi vào hook chuyên biệt hơn.

### Scene 4 — Các giai đoạn chính từ `SpringApplication.run` đến trạng thái sẵn sàng là gì?

**Time:** `03:26–04:05`

**Visual:**

Progressive reveal trên visual của chương: reveal đúng chuỗi `run → refresh → started/LIVE → runners → ready/ACCEPTING_TRAFFIC` cạnh evidence `/runtime/lifecycle`.

**Script:**

Đọc startup line từ trái sang phải. `SpringApplication.run` chuẩn bị environment, tạo/khởi tạo context, nạp bean definitions rồi hoàn tất refresh. Sau đó Boot mới publish `ApplicationStartedEvent` và chuyển liveness sang `CORRECT`. `ApplicationRunner`/`CommandLineRunner` chạy tiếp. `ApplicationReadyEvent` chỉ đến sau runner, rồi readiness mới thành `ACCEPTING_TRAFFIC`. Experiment thật `/spring-boot/runtime/lifecycle` ghi chuỗi started → live → runner → ready → accepting nên thứ tự này quan sát được. Hook cần bean phải nằm sau refresh; work bắt readiness chờ phải ở trước ready transition.

**Purpose:**

Thiết lập đầy đủ chuỗi `run` → refresh → started/LIVE → runners → ready/ACCEPTING_TRAFFIC bằng cả timeline lẫn lifecycle experiment.


## Điều gì thay đổi khi ứng dụng bước vào giai đoạn chạy ổn định?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:05–04:16`

**Visual:**

Giữ runtime timeline trên màn hình và di chuyển highlight tới prerequisite hoặc phase được đưa vào tiếp theo.

**Script:**

READY không kết thúc vai trò runtime của Boot; giữ nguyên process rồi mở rộng sang background work, availability, logging, SSL và shutdown.

**Purpose:**

Ngăn việc hiểu READY là điểm kết thúc bằng cách kéo cùng process qua steady-state service và exit path.

### Scene 5 — Điều gì thay đổi khi ứng dụng bước vào giai đoạn chạy ổn định?

**Time:** `04:16–05:15`

**Visual:**

Progressive reveal trên visual của chương: di chuyển highlight sau READY sang executor, logging, availability, SSL, Compose, failure và shutdown.

**Script:**

Trước khi chọn hook, trong môi trường phát triển, Boot cũng có thể quản lý lifecycle của các dịch vụ Docker Compose. Khi ứng dụng đã sẵn sàng, câu hỏi chuyển từ "khởi động thế nào?" sang "Boot tiếp tục điều phối những dịch vụ và trạng thái runtime nào?" Công việc nền có thể dùng hạ tầng task do Boot quản lý, logging tiếp tục chịu cấu hình của lớp tích hợp Boot, các thành phần có thể đọc hoặc phát trạng thái availability và các client được hỗ trợ có thể dùng SSL bundle có tên. Trạng thái chạy ổn định không có nghĩa các mối quan tâm startup biến mất hoàn toàn. Executor có thể bị nghẽn, availability có thể đổi, log có thể phơi bày lỗi runtime và tiến trình vẫn cần shutdown/exit rõ ràng.

**Purpose:**

Mở mental model qua steady state để background infrastructure, availability change, failure và shutdown vẫn nằm trong cùng câu chuyện runtime.


## Application runtime bàn giao trách nhiệm sang các module lân cận ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:15–05:27`

**Visual:**

Giữ runtime timeline trên màn hình và di chuyển highlight tới prerequisite hoặc phase được đưa vào tiếp theo.

**Script:**

Các concern ở steady state chạm nhiều công nghệ, nên bước cuối là tách phần tích hợp Boot khỏi owner sở hữu ngữ nghĩa sâu hơn.

**Purpose:**

Khép chương bằng cách tách vai trò tích hợp của Boot khỏi công nghệ sở hữu semantics sâu hơn.

### Scene 6 — Application runtime bàn giao trách nhiệm sang các module lân cận ở đâu?

**Time:** `05:27–06:17`

**Visual:**

Progressive reveal trên visual của chương: thay timeline bằng ownership map của các module lân cận.

**Script:**

Trong process đang chạy, thứ tự ưu tiên của property source và binding thuộc `externalized-configuration`; tạo bean theo điều kiện và custom auto-configuration thuộc `auto-configuration`. Mô hình runtime chỉ thực sự hữu ích khi người học biết điểm dừng. Lựa chọn/cấu hình web server, server TLS, proxy và graceful shutdown của server thuộc `web-runtime`. Actuator cung cấp health/diagnostic endpoint có thể tiêu thụ trạng thái runtime, nhưng availability lifecycle bên dưới thuộc module này. Java concurrency sở hữu ngữ nghĩa của virtual thread; Spring Framework concurrency sở hữu `@Async`/`@Scheduled`. Observability/logging sở hữu pipeline/backend; security/network sở hữu TLS/PKI; containerization sở hữu cơ chế Docker/Compose.

**Purpose:**

Ngăn duplication giữa module bằng cách chỉ rõ handoff từ Boot runtime sang configuration, web, concurrency, observability, TLS và container owner.
