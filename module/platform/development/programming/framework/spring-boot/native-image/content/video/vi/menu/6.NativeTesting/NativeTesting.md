---
video:
  url: ""
---

# Kiểm thử AOT và hành vi native có chủ đích

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

## Những rủi ro riêng của native nào cần kiểm thử riêng?

<!-- VIDEO_SECTION -->

### Scene 1 — Những rủi ro riêng của native nào cần kiểm thử riêng?

**Time:** `00:00–00:33`

**Visual:**

Risk map highlight reflective binding, resources, serialization, dynamic proxies, custom hints, third-party native support và structural configuration; business rules bình thường nằm ở JVM lane.

**Script:**

Native testing nên tập trung vào hành vi có khả năng khác do AOT hoặc closed-world constraint: reflective binding, resource, Java serialization, dynamic proxy, custom Runtime Hints, hỗ trợ native của dependency và configuration làm đổi prepared bean model. Business rule bình thường không chính xác hơn chỉ vì mọi unit test được compile native. Mục tiêu là representative startup và integration coverage tại những boundary mà JVM không thể chứng minh thay native runtime.

**Purpose:**

Định nghĩa native-specific risk surface để test native đắt tiền vẫn tập trung.

## Vì sao phần lớn phản hồi kiểm thử nên giữ trên JVM?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:33–00:43`

**Visual:**

Xếp test thành pyramid ba tầng: JVM unit/integration, AOT-on-JVM validation và selected native tests; chi phí tăng dần lên trên.

**Script:**

Vì rủi ro native khá chuyên biệt, phần lớn feedback nên ở JVM nhanh hơn, còn native checks chỉ bổ sung khi tạo ra bằng chứng mới.

**Purpose:**

Nối risk targeting với test strategy cân bằng chi phí.

### Scene 1 — Vì sao phần lớn phản hồi kiểm thử nên giữ trên JVM?

**Time:** `00:43–01:19`

**Visual:**

JVM feedback rộng và nhanh, tầng AOT kiểm tra generated application structure, tầng native nhỏ chứng minh closed-world execution thật.

**Script:**

JVM tests compile và chạy rẻ hơn nhiều, có debugging tooling trưởng thành và đã kiểm tra phần lớn behavior. Native compilation phải làm reachability analysis và native code generation, nên chạy toàn bộ test suite ở native sẽ làm feedback rất đắt. Giữ unit và Boot integration tests phổ biến trên JVM, dùng AOT-focused checks cho prepared model, rồi chỉ dùng native test hoặc smoke test cho những path mà execution native thực sự có thể lộ lỗi JVM không thấy.

**Purpose:**

Giải thích vì sao native testing bổ sung chứ không thay thế JVM test suite.

## Khi nào chạy code đã qua AOT trên JVM hữu ích?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:19–01:27`

**Visual:**

Zoom vào tầng giữa với JAR đã qua AOT chạy trên JVM bằng -Dspring.aot.enabled=true trước khi GraalVM compiler xuất hiện.

**Script:**

Tầng giữa hữu ích vì tách lỗi Spring AOT preparation khỏi GraalVM, tạo checkpoint rẻ hơn trước full native build.

**Purpose:**

Cho thấy AOT-on-JVM giúp phân biệt framework preparation failure với native failure sau đó.

### Scene 1 — Khi nào chạy code đã qua AOT trên JVM hữu ích?

**Time:** `01:27–02:04`

**Visual:**

Hiện `java -Dspring.aot.enabled=true -jar myapplication.jar`; bên cạnh là prerequisite Maven native profile hoặc `process-aot`, và với Gradle là Boot `org.springframework.boot.aot`. Có thể vẽ GraalVM Native Build Tools như native-workflow route tự động bật/cấu hình AOT support này.

**Script:**

Application đã AOT-process có thể chạy trên JVM với `spring.aot.enabled=true` khi JAR đã chứa generated AOT assets. Cách này không mô phỏng closed-world compiler của GraalVM, nhưng chứng minh generated initialization của Spring có mô tả application đúng hay không. Maven dùng Boot parent có thể tạo assets qua native profile; Maven không dùng parent cấu hình `process-aot` tương đương. Với Gradle, prerequisite trực tiếp cho AOT-on-JVM là plugin `org.springframework.boot.aot`; GraalVM Native Build Tools tự động bật/cấu hình AOT support đó khi đi theo native workflow. Nếu AOT-mode JVM đã lỗi, hãy sửa prepared model trước khi trả chi phí native compile.

**Purpose:**

Cung cấp checkpoint chẩn đoán rẻ hơn giữa JVM startup bình thường và native compilation.

## Maven `process-test-aot` và Gradle `processTestAot` chuẩn bị context kiểm thử như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:04–02:13`

**Visual:**

Chuyển từ application AOT sang test-context scanner gom JUnit/Spring tests theo ApplicationContext và sinh initializer assets cho từng context đủ điều kiện.

**Script:**

Test có những context riêng, nên native test preparation có AOT step riêng chứ không chỉ tái sử dụng generated model của main application.

**Purpose:**

Khôi phục chính xác process-test-aot và processTestAot chuẩn bị những gì.

### Scene 1 — Maven `process-test-aot` và Gradle `processTestAot` chuẩn bị context kiểm thử như thế nào?

**Time:** `02:13–02:51`

**Visual:**

Maven process-test-aot và Gradle processTestAot đi vào Spring TestContext discovery, distinct ApplicationContexts, generated ApplicationContextInitializer code cùng test hints/resources, rồi thành native-test input bundle.

**Script:**

Spring TestContext Framework tham gia AOT processing cho test. Maven process-test-aot và Gradle processTestAot phân tích các Spring test context đủ điều kiện, phát hiện những ApplicationContext khác nhau mà test cần, rồi sinh initialization assets để các context đó được dựng lại mà không cần runtime discovery mở rộng. Bước này tách khỏi application AOT vì test có thể thêm configuration, infrastructure và property riêng. Do đó test context có runtime dynamism không hỗ trợ vẫn có thể fail native preparation dù main context đã sẵn sàng.

**Purpose:**

Giải thích TestContext discovery và generated AOT initializer assets thay vì chỉ kể tên task.

## Profile Maven `nativeTest` và Task Gradle `nativeTest` chạy kiểm thử native như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:51–03:00`

**Visual:**

Gom test-context initializers, application/test classes, hints/resources và JUnit engine thành native test executable rồi chạy nó.

**Script:**

Sau khi test context được chuẩn bị, nativeTest biến bundle này thành executable thật để selected tests chịu cùng closed-world constraint như production code.

**Purpose:**

Nối test AOT preparation với native test execution thật trên Maven và Gradle.

### Scene 1 — Profile Maven `nativeTest` và Task Gradle `nativeTest` chạy kiểm thử native như thế nào?

**Time:** `03:00–03:38`

**Visual:**

mvn -PnativeTest test và gradle nativeTest đi vào GraalVM Native Build Tools, tạo native test executable chứa AOT-prepared test support rồi chạy JUnit; ghi chú Maven không dùng Boot parent phải cấu hình explicit.

**Script:**

Với native test setup được hỗ trợ, build AOT-process test context đủ điều kiện, compile application cùng generated test assets thành native test executable có test-engine support cần thiết, rồi chạy selected tests ở native form. Maven dùng Boot parent nhận nativeTest profile; Gradle có GraalVM Native Build Tools nhận nativeTest task. Maven không dùng parent phải cấu hình tương đương cho test AOT và native plugin. Khi native test fail, hãy chạy cùng test trên JVM trước để tách AOT/reachability effect khỏi logic bug thông thường.

**Purpose:**

Mô tả nativeTest thực thi gì và giữ parity giữa Maven và Gradle entry point.

## Chi phí kiểm thử native nên ảnh hưởng chiến lược kiểm thử trên máy lập trình viên và CI như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:38–03:47`

**Visual:**

Đặt native test executable lên CI timeline với trigger riêng cho hint changes, pull request, scheduled build và release candidate.

**Script:**

Vì executable này tốn công build, native coverage nên được schedule theo risk và signal thay vì chạy vô điều kiện sau mọi edit.

**Purpose:**

Chuyển native test cost thành policy local/CI thực tế.

### Scene 1 — Chi phí kiểm thử native nên ảnh hưởng chiến lược kiểm thử trên máy lập trình viên và CI như thế nào?

**Time:** `03:47–04:23`

**Visual:**

Fast JVM tests mỗi edit, focused native test sau native-sensitive changes, representative native suite tại CI gate phù hợp và clean-build reproducibility check.

**Script:**

Native tests tốn nhiều thời gian và tài nguyên hơn, nên hãy đặt chúng ở nơi signal xứng đáng chi phí. Developer có thể chạy focused native check khi sửa hint hoặc integration nhạy cảm với native. CI chạy representative suite trên pull request, lịch định kỳ hoặc release candidate tùy risk. Hãy đo build duration và failure frequency, giảm duplicate coverage trước khi bỏ native verification. Cache có thể tăng tốc, nhưng clean environment vẫn phải dựng lại executable từ declared inputs.

**Purpose:**

Tạo cadence native test theo risk mà vẫn bảo toàn reproducibility.

## Kiểm thử riêng cho native bàn giao sang module Boot Testing tổng quát ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:23–04:35`

**Visual:**

Đóng pyramid bằng module boundary: testing sở hữu test design tổng quát; native-image chỉ sở hữu câu hỏi 'còn chạy sau AOT + native compilation không?'.

**Script:**

Native layer xây trên nền Boot tests sẵn có, nên cuối cùng phải xác định phần testing nào ở lại module testing và phần nào thực sự riêng cho native.

**Purpose:**

Không lặp lại Spring Boot testing fundamentals trong native-image.

### Scene 1 — Kiểm thử riêng cho native bàn giao sang module Boot Testing tổng quát ở đâu?

**Time:** `04:35–05:04`

**Visual:**

@SpringBootTest, slices, web environments, Testcontainers và test auto-configuration nằm phía testing; một mũi tên sang native-image mang nhãn 'survives AOT + native compilation?'.

**Script:**

Module testing sở hữu SpringBootTest, slices, web environment, test auto-configuration, property overrides, dependency replacement, Testcontainers service connections và integration-test strategy tổng quát. Native-image tiêu thụ các test đó rồi thêm một câu hỏi: startup hoặc integration behavior quan trọng có còn hoạt động sau AOT processing và native compilation không? Chỉ chiều bổ sung này thuộc module hiện tại; curriculum testing rộng hơn không cần dạy lại.

**Purpose:**

Định nghĩa native testing như execution constraint bổ sung trên general Boot test design.
