---
video:
  url: ""
---

# Chạy ứng dụng bằng công cụ build

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

## Một lần chạy ứng dụng lúc phát triển qua công cụ build của Boot là gì?

<!-- VIDEO_SECTION -->

### Scene 1 — Chạy từ build model trước khi đóng gói

**Time:** `00:00–00:32`

**Visual:**

Hiển thị source compilation tạo `classes + runtime classpath`, sau đó đi thẳng tới JVM đang chạy. Giữ nhánh `bootJar → app.jar` riêng ở phía dưới như một bước khác.

**Script:**

Development run của Boot khởi động ứng dụng trực tiếp từ compiled output và runtime classpath của build. Cách này phù hợp với vòng lặp sửa code rồi chạy lại vì không cần tạo executable archive cuối cùng ở mỗi lần start. Nhưng evidence của nó có giới hạn: run thành công cho biết application chạy được từ build model, chưa chứng minh manifest, nested library layout hay loader của artifact cuối cùng là đúng.

**Purpose:**

Định nghĩa chính xác development-run success chứng minh điều gì và không chứng minh điều gì.

## Gradle `bootRun` khởi chạy ứng dụng như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:32–00:39`

**Visual:**

Zoom vào nhánh Gradle và đặt task `bootRun` giữa main source set và JVM process.

**Script:**

Gradle hiện thực development path này bằng một task vẫn nằm trong execution model bình thường của Gradle.

**Purpose:**

Chuyển từ development run khái quát sang hành vi cụ thể của BootRun.

### Scene 2 — BootRun là một Java execution task có convention của Boot

**Time:** `00:39–01:15`

**Visual:**

Hiển thị `./gradlew bootRun` cùng các input: main source-set output, runtime classpath, discovered main class, JVM args, application args, environment. Tách rõ JVM arguments và application arguments thành hai lane, đồng thời thêm toggle `optimizedLaunch=true → false khi chẩn đoán`.

**Script:**

Trong Spring Boot 3.3, `BootRun` là subclass của `JavaExec` với các convention của Boot. Nó dùng main runtime classpath, có thể tự tìm main class và vẫn expose các control quen thuộc như JVM argument, system property, environment variable và application argument. Boot còn bật optimized development launch theo mặc định; có thể tắt `optimizedLaunch` khi cần so sánh chẩn đoán với cách JVM launch thông thường. Các input channel vẫn phải tách bạch: application argument không tự biến thành JVM system property.

**Purpose:**

Cho thấy BootRun kết hợp JavaExec controls với runtime classpath, main-class discovery và optimized development launch của Boot.

## Maven `spring-boot:run` khởi chạy ứng dụng như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:15–01:21`

**Visual:**

Chuyển từ Gradle task graph sang Maven command line nhưng giữ nguyên sơ đồ “compiled output + dependencies → JVM”.

**Script:**

Maven đạt cùng mục tiêu phát triển thông qua plugin goal thay vì Gradle task.

**Purpose:**

Giữ concept launch chung nhưng chuyển sang cơ chế của Maven.

### Scene 3 — Development launch theo Maven

**Time:** `01:21–01:45`

**Visual:**

Hiển thị `./mvnw spring-boot:run` và callout cho application arguments, JVM arguments, environment variables, system properties và profiles. Bao quanh bằng Maven lifecycle.

**Script:**

`spring-boot:run` dựng application classpath của project và launch main class qua Maven plugin. Maven vẫn resolve dependency và sở hữu lifecycle; plugin bổ sung Boot-aware launch. Nếu lỗi chỉ xuất hiện sau khi đóng gói, run goal này không phải evidence đúng vì executable archive boundary chưa được exercise.

**Purpose:**

Giải thích Maven run goal và giữ cùng evidence boundary với nhánh Gradle.

## Khi nào nên chạy qua công cụ build thay vì dùng `java -jar`?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:45–01:52`

**Visual:**

Đặt `bootRun / spring-boot:run` và `java -jar app.jar` cạnh nhau, gắn nhãn “development classpath” và “packaged artifact”.

**Script:**

Cả hai cách đều có thể start cùng một ứng dụng, nhưng chúng kiểm tra hai boundary khác nhau.

**Purpose:**

Chuẩn bị so sánh evidence giữa development execution và packaged execution.

### Scene 4 — Chọn run path theo câu hỏi cần kiểm chứng

**Time:** `01:52–02:23`

**Visual:**

Decision card: “đang edit/debug?” → build-tool run; “đang kiểm manifest, loader, BOOT-INF, packaged dependencies?” → build archive rồi `java -jar`. Thêm dependency `developmentOnly` chỉ xuất hiện ở development side.

**Script:**

Hãy dùng build-tool run khi cần feedback nhanh trong lúc phát triển. Dùng `java -jar` khi cần evidence về chính artifact sẽ được bàn giao. Sự khác biệt còn nằm ở dependency set: dependency phục vụ development có thể xuất hiện trong `bootRun` nhưng bị loại khỏi production archive. Vì vậy release check cần launch đúng JAR hoặc image cuối cùng, không dùng IDE run làm bằng chứng thay thế cho packaging.

**Purpose:**

Dạy cách chọn launch method dựa trên boundary thực sự cần verify.

## Run task cấu hình phần nào và phần nào vẫn thuộc `SpringApplication`?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:23–02:32`

**Visual:**

Dừng sơ đồ launch process ngay tại application main method và vẽ ranh giới trước `SpringApplication.run`.

**Script:**

Run task kiểm soát cách process được tạo ra. Sau khi ứng dụng bước vào Spring Boot runtime, owner lại thay đổi.

**Purpose:**

Tách launch configuration khỏi runtime lifecycle semantics.

### Scene 5 — Process launch kết thúc nơi application runtime bắt đầu

**Time:** `02:32–02:58`

**Visual:**

Bên trái: classpath, main class, JVM args, app args, environment. Bên phải sau `SpringApplication.run`: context creation, events, runners, availability, failure analysis, shutdown.

**Script:**

Build plugin quyết định JVM nhận classpath nào, main class nào và các launch input được truyền ra sao. Sau khi main method gọi `SpringApplication.run`, context creation, application event, runner, availability, failure analysis và shutdown đã thuộc runtime. Nếu cùng một lỗi vẫn xảy ra khi chạy `java -jar`, tiếp tục chỉnh `bootRun` thường là đang điều tra sai owner.

**Purpose:**

Khép lại bằng ranh giới troubleshooting giữa build-time launch configuration và SpringApplication runtime behavior.
