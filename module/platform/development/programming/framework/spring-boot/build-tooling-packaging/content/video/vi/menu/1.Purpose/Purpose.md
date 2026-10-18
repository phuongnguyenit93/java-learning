---
video:
  url: ""
---

# Vì sao Spring Boot cần công cụ build riêng

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

## Build tooling của Spring Boot là gì và vì sao tồn tại?

<!-- VIDEO_SECTION -->

### Scene 1 — Từ Java build đến quy trình bàn giao theo chuẩn Boot

**Time:** `00:00–00:35`

**Visual:**

Hiển thị sơ đồ ba bước: `Gradle / Maven` → `Spring Boot plugin` → `run / executable archive / OCI image`. Giữ compile và dependency resolution nằm trong khối build tool, chỉ highlight các thao tác do Boot bổ sung.

**Script:**

Ứng dụng Spring Boot vẫn dùng Gradle hoặc Maven làm hệ thống build. Boot bổ sung một lớp tích hợp phía trên: lớp này giúp build tool biết cách chạy ứng dụng theo convention của Boot, đóng gói executable archive và khi cần thì tạo OCI image. Vì vậy có thể nhớ rất rõ ranh giới này: Gradle hoặc Maven vẫn là build engine, còn Boot plugin bổ sung hành vi phục vụ riêng cho việc bàn giao một ứng dụng Boot.

**Purpose:**

Thiết lập mental model giữa build tool và Boot plugin trước khi đi vào từng task và artifact cụ thể.

## Boot giải quyết bài toán build và packaging nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:35–00:47`

**Visual:**

Thu nhỏ sơ đồ trước thành một Java build thông thường, sau đó hiện lần lượt các câu hỏi chưa được giải quyết: entry point, runtime dependencies, executable layout và image creation.

**Script:**

Nếu Gradle và Maven vốn đã build được Java thì vì sao Boot còn cần một lớp tích hợp riêng? Câu trả lời nằm ở những gì ứng dụng phải bàn giao sau khi compile xong.

**Purpose:**

Chuyển từ vai trò của plugin sang các vấn đề packaging cụ thể mà Boot chuẩn hóa.

### Scene 2 — Chuẩn hóa đường đi từ source đến delivery artifact

**Time:** `00:47–01:17`

**Visual:**

Reveal tuần tự chuỗi `dependency alignment → development run → executable JAR/WAR → layered archive → OCI image`. Bên cạnh là một nhánh mờ “mỗi team tự quy ước” với archive layout không thống nhất.

**Script:**

Nếu không có Boot-specific tooling, mỗi team có thể tự quyết định cách chọn version tương thích, tìm main class, gom runtime dependency và biến output thành một đơn vị có thể chạy được. Spring Boot cung cấp một đường tích hợp được hỗ trợ cho những concern đó. Boot không thay Gradle, Maven, Docker hay CI/CD; nó thu hẹp phần công việc lặp lại quanh một ứng dụng Boot.

**Purpose:**

Cho thấy các quyết định build và packaging lặp lại mà Boot chuẩn hóa mà không mở rộng sang curriculum của công cụ khác.

## Trách nhiệm build-time kết thúc ở đâu và runtime bắt đầu ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:17–01:29`

**Visual:**

Vẽ ranh giới giữa artifact nằm trên disk và JVM process đang chạy. Đưa card `bootJar` sang phía build-time và card `SpringApplication.run(...)` sang phía runtime.

**Script:**

Khi artifact đã được tạo ra thì một trách nhiệm khác bắt đầu. Tách build-time khỏi runtime giúp chúng ta không nhầm lỗi packaging với lỗi khởi động ứng dụng.

**Purpose:**

Giới thiệu ranh giới artifact-to-process dùng xuyên suốt phần troubleshooting.

### Scene 3 — Tạo artifact khác với khởi động ứng dụng

**Time:** `01:29–01:56`

**Visual:**

Hiển thị `bootJar / repackage → app.jar`, rồi animate `java -jar app.jar → JVM → Spring Boot Loader → application main → SpringApplication`. Highlight điểm bàn giao sau khi process bắt đầu.

**Script:**

Build-time tooling resolve input, compile code, ghi archive metadata và tạo delivery artifact. Runtime bắt đầu khi artifact đó được launch và JVM thực thi nó. Một lệnh như `bootRun` đi qua cả hai phía trong cùng một thao tác phát triển, nhưng sau khi main method đi vào `SpringApplication`, các event, context creation, runner và shutdown đã là concern của runtime.

**Purpose:**

Tạo checkpoint rõ ràng để xác định vấn đề thuộc packaging hay application runtime.

## Build tooling của Boot bàn giao cho hạ tầng deployment ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:56–02:08`

**Visual:**

Đưa JAR và image đã hoàn tất tới một đường “handoff”, phía bên kia hiện artifact repository, image registry, CI/CD và runtime platform.

**Script:**

Build-time còn có một ranh giới bên ngoài nữa. Tạo được một delivery unit hợp lệ không có nghĩa Boot plugin sở hữu mọi việc xảy ra với unit đó ở production.

**Purpose:**

Kết nối việc hoàn tất packaging với chủ sở hữu tiếp theo trong delivery chain.

### Scene 4 — Handoff bằng artifact có danh tính rõ ràng

**Time:** `02:08–02:32`

**Visual:**

Hiển thị checksum của JAR và digest của image đi từ “Boot build tooling” sang “artifact repository / registry → promotion → runtime platform”. Giữ rollout, secrets, scaling và traffic routing ngoài khối Boot.

**Script:**

Spring Boot build tooling kết thúc khi nó đã tạo, và khi được cấu hình thì publish, artifact hoặc image cần bàn giao. Repository policy, image promotion, rollout strategy, secrets, scheduling và scaling thuộc deployment infrastructure. Handoff tốt nên mang theo checksum hoặc image digest cụ thể để downstream promote đúng unit mà build đã kiểm chứng.

**Purpose:**

Khép lại video bằng ranh giới trách nhiệm rõ giữa Boot packaging và deployment operations.
