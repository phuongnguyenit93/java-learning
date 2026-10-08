---
video:
  url: ""
---

# Gradle plugin và Maven plugin của Spring Boot

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

## Gradle plugin và Maven plugin của Spring Boot bổ sung điều gì?

<!-- VIDEO_SECTION -->

### Scene 1 — Hai adapter hướng tới cùng kết quả của Boot

**Time:** `00:00–00:41`

**Visual:**

Chia màn hình thành hai nhánh Gradle và Maven. Ở Gradle, mở rộng phản ứng với Java plugin thành `bootJar`, `bootRun`, `bootBuildImage`, `assemble → bootJar`, `plain.jar` và các configuration runtime cho development/production. Ở Maven, hiện các goal cùng `repackage` execution được parent cấu hình sẵn. Hai nhánh vẫn hội tụ vào run, executable archive và OCI image.

**Script:**

Spring Boot tích hợp vào hai build model khác nhau. Khi Java plugin có mặt, Boot phản ứng với model Gradle sẵn có: đăng ký các task như `bootJar`, `bootRun`, `bootBuildImage`, nối `assemble` tới executable archive, đặt classifier `plain` cho JAR thông thường và tạo các configuration runtime dùng cho development/production. Maven dùng goal thay vì Gradle task; nếu project kế thừa `spring-boot-starter-parent`, parent còn có thể cấu hình sẵn `repackage` execution. Cơ chế khác nhau nhưng cả hai đều thích nghi build system nền cho các outcome của Boot chứ không thay thế nó.

**Purpose:**

Cho thấy cụ thể mỗi plugin bổ sung gì nhưng vẫn giữ Gradle và Maven là build engine nền.

## Những task chạy, đóng gói và tạo image nào thuộc plugin của Boot?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:41–00:50`

**Visual:**

Giữ hai nhánh build tool, thay ba output khái quát bằng các tên task và goal cụ thể.

**Script:**

Khi đã nắm kết quả chung, ta có thể gắn vào đó các tên lệnh thật mà developer sẽ gặp trong build và log.

**Purpose:**

Chuyển từ vai trò khái niệm sang các Boot operation cụ thể.

### Scene 2 — Ghép command với output

**Time:** `00:50–01:16`

**Visual:**

Hiển thị bảng: Gradle `bootRun`, `bootJar / bootWar`, `bootBuildImage`; Maven `spring-boot:run`, `repackage`, `build-image`. Mỗi dòng nối tới process, archive hoặc image tương ứng.

**Script:**

Hãy nhớ các operation này theo output. `bootRun` và Maven run goal tạo development process. `bootJar`, `bootWar` và `repackage` tạo executable Boot archive. `bootBuildImage` và `build-image` đi vào Buildpacks image path. Tất cả vẫn tiêu thụ input từ build tool, nên class bị thiếu hay dependency resolve thất bại vẫn phải được xử lý ở build model bên dưới trước.

**Purpose:**

Cung cấp bản đồ task-to-output gọn và nhấn mạnh Boot operation phụ thuộc vào input của build tool.

## Phần nào vẫn là trách nhiệm của Gradle hoặc Maven?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:16–01:27`

**Visual:**

Zoom out khỏi bảng Boot task để lộ repository, source compilation, dependency resolution, incremental work và project structure bao quanh.

**Script:**

Các Boot operation chỉ là một phần của build model lớn hơn. Xác định đúng owner của lỗi sẽ giúp tránh việc cứ mặc định nghi ngờ Boot plugin.

**Purpose:**

Chuyển sự chú ý từ Boot command sang các trách nhiệm build-system mà chúng phụ thuộc vào.

### Scene 3 — Chẩn đoán đúng ranh giới build system

**Time:** `01:27–01:59`

**Visual:**

Hai cột: “Build tool” gồm repository credentials, compiler configuration, dependency resolution, task/lifecycle ordering; “Boot integration” gồm `BOOT-INF`, main-class discovery, executable layout và image task configuration.

**Script:**

Gradle và Maven vẫn compile source, resolve dependency, mô hình hóa project và điều phối task hoặc lifecycle. Một câu hỏi chẩn đoán hữu ích là: lỗi này có còn tồn tại trong một Java project không dùng Boot không? Nếu repository access hoặc compilation đã hỏng, hãy bắt đầu ở build tool. Nếu lỗi xoay quanh `bootJar`, `BOOT-INF`, `repackage` hay `bootBuildImage`, lúc đó Boot integration mới là nơi hợp lý để kiểm tra.

**Purpose:**

Dạy một phép kiểm tra ownership thực tế để tách generic build failure khỏi Boot-specific failure.

## Nên tư duy thế nào về hai đường tích hợp Gradle và Maven?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:59–02:09`

**Visual:**

Thay hai cột ownership bằng hai road map: “Gradle task model” và “Maven lifecycle/goal model”, cùng kết thúc tại executable JAR.

**Script:**

Bẫy cuối cùng là nghĩ rằng cùng tạo ra một output thì hai build tool phải có cấu hình tương đương từng dòng. Điều đó không đúng.

**Purpose:**

Chuẩn bị cách so sánh dựa trên intent chung nhưng vẫn tôn trọng cơ chế riêng của từng build tool.

### Scene 4 — So sánh intent, không ép cú pháp giống nhau

**Time:** `02:09–02:33`

**Visual:**

Highlight `bootJar` ở nhánh Gradle và `package + repackage` ở nhánh Maven. Thêm nhãn “same Boot archive model, different build integration”.

**Script:**

Gradle biểu diễn executable packaging bằng task chuyên biệt như `bootJar`. Maven thường để lifecycle package tạo archive rồi áp dụng `repackage`. Đừng cố ghép hai cách này thành một bảng cú pháp một-một giả tạo. Hãy học Boot intent trước, sau đó dùng integration style chính thức của build tool mà project đã chọn.

**Purpose:**

Khép lại bằng cách so sánh giữ được điểm chung của Boot nhưng không làm mất khác biệt giữa Gradle và Maven.
