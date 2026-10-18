---
video:
  url: ""
---

# `bootJar`, `bootWar` và repackaging

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

## `bootJar`, `bootWar` và Maven `repackage` tạo Boot archive như thế nào?

<!-- VIDEO_SECTION -->

### Scene 1 — Biến build input thông thường thành executable Boot archive

**Time:** `00:00–00:38`

**Visual:**

Hiển thị application classes và runtime dependencies đi vào ba lựa chọn: Gradle `bootJar`, Gradle `bootWar`, Maven `package → repackage`. Gắn nhãn BootJar/BootWar là specialized Jar/War task. Ở Maven, cho thấy executable artifact thay main artifact còn bản gốc được đổi đuôi `.original`; một nhánh classifier giữ cả hai artifact để publish.

**Script:**

Gradle và Maven vẫn compile application và resolve runtime input. Bước packaging của Boot nhận những input đó rồi sắp xếp theo executable layout. `BootJar` và `BootWar` là specialized Jar/War task nên archive configuration thông thường vẫn áp dụng cùng tính năng Boot. Maven `repackage` biến archive từ package lifecycle thành executable form; mặc định bản non-executable gốc được đổi tên với `.original`, còn classifier cho phép giữ bản gốc và attach executable riêng. Dù theo đường nào, kết quả là artifact có Loader và dependency layout để launch.

**Purpose:**

Giải thích điểm Boot executable packaging bắt đầu và Gradle specialization/Maven repackaging ảnh hưởng thế nào tới các artifact còn lại.

## Boot executable archive khác library artifact thông thường thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:38–00:47`

**Visual:**

Chia output thành `plain.jar` và `application executable.jar`; một file được đưa lên classpath của application khác, file còn lại chạy bằng `java -jar`.

**Script:**

Khi cùng một project có thể tạo hai file JAR, mục đích sử dụng của chúng quan trọng hơn phần mở rộng giống nhau.

**Purpose:**

Chuyển từ cách tạo archive sang sự khác biệt giữa library artifact và executable artifact.

### Scene 2 — Classpath library khác application delivery unit

**Time:** `00:47–01:26`

**Visual:**

Bên trái là plain JAR chứa reusable classes. Bên phải là Boot executable archive chứa application classes, nested runtime JAR, loader classes và manifest metadata. Highlight convention classifier `plain` của Gradle và cảnh báo `giữ ordinary jar khi native-image tooling cần nó`.

**Script:**

Library JAR thông thường được thiết kế để nằm trên classpath của application khác. Boot executable archive lại là delivery unit của chính ứng dụng: nó mang application, nested runtime dependencies và metadata cần cho `java -jar`. Gradle có thể tạo cả hai output và conventionally gắn classifier `plain` cho ordinary JAR. Plain task có thể tắt nếu project thật sự không có consumer, nhưng Spring Boot cảnh báo không tắt nó khi native-image tooling cần ordinary JAR. Publish configuration vẫn phải làm rõ artifact nào dành cho consumer nào.

**Purpose:**

Làm rõ khác biệt reusable/plain output với executable packaging, kể cả trường hợp plain artifact vẫn cần cho downstream tooling.

## Khi nào nên đóng gói executable JAR hoặc WAR?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:26–01:32`

**Visual:**

Thay artifact comparison bằng hai target: standalone JVM process và external servlet container.

**Script:**

Lựa chọn JAR hay WAR nên đi theo môi trường thực sự sẽ launch ứng dụng.

**Purpose:**

Kết nối archive type với deployment contract thay vì gắn nó với mức độ “enterprise”.

### Scene 3 — JAR và WAR thể hiện hai delivery contract

**Time:** `01:32–02:01`

**Visual:**

Executable JAR → embedded server/JVM process. WAR → external servlet container, highlight `WEB-INF/lib-provided` và cấu hình Gradle `providedRuntime`.

**Script:**

Executable JAR là lựa chọn thông thường khi application tự mang Boot-managed runtime và chạy như một JVM process độc lập. WAR phù hợp khi môi trường yêu cầu external servlet container. Với WAR vừa có thể deploy ngoài container vừa có thể chạy executable, dependency do container cung cấp phải được tách khỏi library thông thường; đó là lý do có `WEB-INF/lib-provided` và Gradle thường dùng `providedRuntime`.

**Purpose:**

Cho thấy JAR-versus-WAR phản ánh quyền sở hữu runtime và dependency của môi trường đích.

## Archive đã đóng gói xác định entry point của ứng dụng như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:01–02:09`

**Visual:**

Mở manifest của archive và highlight hai dòng `Main-Class` cùng `Start-Class`.

**Script:**

Executable archive còn phải trả lời một câu hỏi cuối: JVM sẽ chạy code nào đầu tiên khi nhận `java -jar`?

**Purpose:**

Chuyển từ archive layout sang launcher metadata giúp artifact thực sự executable.

### Scene 4 — Loader entry point và application entry point

**Time:** `02:09–02:36`

**Visual:**

Animate `java -jar` đọc `Main-Class → Spring Boot Loader`, rồi `Start-Class → application main`. Hiện cảnh báo “ambiguous main classes” và cấu hình main class rõ ràng để giải quyết.

**Script:**

Boot tách launcher entry point khỏi main class của application. `Main-Class` trong manifest đưa JVM vào Spring Boot Loader, còn `Start-Class` chỉ class application mà loader sẽ gọi. Auto discovery thuận tiện khi chỉ có một candidate rõ ràng; nếu có nhiều main class, hãy cấu hình entry point qua Boot plugin thay vì sửa manifest thủ công rồi vô tình bypass loader.

**Purpose:**

Giải thích mô hình entry point hai bước và lý do Boot plugin phải sở hữu main-class selection.
