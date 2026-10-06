---
video:
  url: ""
---

# Mô hình đóng gói ứng dụng Spring Boot

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

## Vì sao Spring Boot cung cấp mô hình đóng gói thực thi được?

<!-- VIDEO_SECTION -->

### Scene 1 — Từ IDE tới một artifact có thể bàn giao

**Time:** `00:00–00:55`

**Visual:**

Mở bằng IDE đang chạy ứng dụng với classpath rời rạc. Sau đó thu các ô `compiled classes` và `runtime dependencies` vào một `application.jar`, rồi chuyển sang terminal ở máy khác chạy `java -jar application.jar`.

**Script:**

Ứng dụng chỉ thật sự hữu ích khi rời khỏi IDE mà vẫn có thể khởi động với đúng code và dependency cần thiết. Mô hình executable packaging của Boot giải bài toán đó bằng cách tạo một application archive có thể tự tìm tới application classes và các dependency đã đóng gói. Giá trị ở mức Fundamentals rất đơn giản: build tạo ra một artifact hướng ứng dụng, rồi JVM có thể khởi động artifact đó bằng `java -jar` thay vì ta phải ghép classpath thủ công cho từng lần chạy.

**Purpose:**

Đặt packaging trong luồng development-to-delivery và giải thích vì sao executable artifact có giá trị với learner mới.

## Artifact ứng dụng Boot thực thi được là gì?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Zoom vào `application.jar`, bên cạnh đặt một `thin ordinary JAR` để chuẩn bị so sánh.

**Script:**

Để hiểu vì sao `java -jar` có thể chạy thuận tiện, ta cần nhìn artifact Boot khác một thin JAR thông thường ở điểm nào.

**Purpose:**

Chuyển từ mục tiêu bàn giao sang cấu tạo khái niệm của executable Boot artifact.

### Scene 2 — Application classes, dependencies và Boot loader cùng một archive

**Time:** `01:05–02:00`

**Visual:**

Hai archive đặt cạnh nhau. Thin JAR chỉ có application classes và mũi tên `external classpath required`. Boot executable JAR có ba lớp: `application classes/resources`, `dependency JARs`, `Boot loader classes/metadata`. Highlight dòng `artifact = application + built dependency set`.

**Script:**

Thin JAR thông thường có thể chỉ chứa class đã compile của ứng dụng và kỳ vọng dependency được lắp riêng trên command line. Executable Boot JAR phổ biến mang application classes, resources, dependency JAR và Boot loader metadata trong cùng archive. Nhờ vậy artifact đại diện cho ứng dụng cùng dependency set mà nó được build với. Chi tiết task `bootJar` hay Maven repackage vẫn thuộc module build-tooling-packaging; ở đây ta chỉ cần mô hình tư duy về kết quả.

**Purpose:**

Phân biệt executable Boot archive với thin JAR bằng thành phần và trách nhiệm runtime classpath.

## Application classes và dependencies được giữ cùng nhau để chạy như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:00–02:10`

**Visual:**

Mở archive Boot theo dạng cây thư mục, chuẩn bị highlight hai vị trí `BOOT-INF/classes` và `BOOT-INF/lib`.

**Script:**

Các dependency nằm trong cùng archive nhưng JVM không tự coi nested JAR như classpath bình thường. Vì thế layout và Boot launcher phải phối hợp với nhau.

**Purpose:**

Nối executable artifact với nested-JAR layout và vai trò của Boot launcher.

### Scene 3 — BOOT-INF và nested classpath

**Time:** `02:10–03:05`

**Visual:**

Hiện cây archive: `BOOT-INF/classes/`, `BOOT-INF/lib/`, Boot launcher classes/metadata. Highlight một application class dưới `BOOT-INF/classes` và vài dependency JAR dưới `BOOT-INF/lib`. Sau đó animate `JarLauncher → construct access to nested layout → load application`.

**Script:**

Trong bố cục executable JAR chuẩn của Spring Boot 3.3, application classes và resources nằm dưới `BOOT-INF/classes`, còn dependency JAR nằm dưới `BOOT-INF/lib`. `JarLauncher` hiểu bố cục này và thiết lập cách truy cập cần thiết để application class cùng nested dependency có thể được nạp. Điều đáng nhớ không phải mọi chi tiết archive, mà là Boot loader làm cầu nối để nested JAR vẫn tham gia runtime classpath của ứng dụng.

**Purpose:**

Cho learner một hình ảnh cụ thể về `BOOT-INF` và giải thích vì sao nested dependency vẫn chạy được.

## Ứng dụng đã đóng gói liên hệ với java -jar ra sao?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:05–03:15`

**Visual:**

Từ cây archive, chuyển sang file `META-INF/MANIFEST.MF` rồi terminal `java -jar application.jar`.

**Script:**

Layout giải thích dependency nằm ở đâu. Bước cuối là hiểu JVM bắt đầu từ class nào và Boot launcher chuyển quyền điều khiển về application `main` như thế nào.

**Purpose:**

Chuyển từ archive layout sang launch flow thực tế của `java -jar`.

### Scene 4 — Main-Class, Start-Class và luồng bootstrap

**Time:** `03:15–04:15`

**Visual:**

Hiện manifest với `Main-Class: org.springframework.boot.loader.launch.JarLauncher` và `Start-Class: com.example.YourApplication`. Animate flow `java -jar → Boot launcher → nested classpath → Start-Class → YourApplication.main(...) → SpringApplication.run(...)`.

**Script:**

Khi chạy `java -jar`, JVM đọc manifest và đi vào `Main-Class`. Với executable Boot JAR, `Main-Class` là Boot launcher, còn application class được chỉ ra qua `Start-Class`. Launcher thiết lập quyền truy cập tới classes và nested dependencies, rồi gọi application class chứa `main`. Từ điểm đó, mô hình tư duy bootstrap vẫn y hệt chương SpringApplication: application `main` gọi `SpringApplication.run(...)`. Packaging thay cách JVM tìm tới ứng dụng, không thay logic bootstrap cốt lõi của ứng dụng.

**Purpose:**

Làm rõ entry point ở cấp archive và giữ đúng liên hệ giữa Boot launcher với application `main`/`SpringApplication`.

## Chi tiết packaging chuyển sang module nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:15–04:25`

**Visual:**

Từ launch flow, mở rộng ra các nhãn `bootJar`, `bootWar`, `layers`, `reproducible archive`, `bootBuildImage`, `Buildpacks`.

**Script:**

Đến đây mô hình tư duy đã đủ để đọc một executable artifact. Còn cách build tool tạo artifact, chia layer hay dựng OCI image là một tầng trách nhiệm khác.

**Purpose:**

Đặt boundary giữa packaging mental model và mechanics của build-tooling-packaging.

### Scene 5 — Giữ ba invariant và handoff build tooling

**Time:** `04:25–05:15`

**Visual:**

Hiện ba invariant: `source + dependencies → runnable artifact`; `Boot launcher understands packaged layout`; `launcher hands off to application main`. Bên phải là checklist `bootRun`, `bootJar/bootWar`, Maven repackage, layering, reproducibility, OCI/Buildpacks → build-tooling-packaging`.

**Script:**

Fundamentals chỉ cần giữ ba quan hệ ổn định: source và dependency được đóng thành artifact chạy được; Boot launcher hiểu layout đó; và sau khi launcher chuyển quyền về application class, luồng `main → SpringApplication` tiếp tục bình thường. Khi câu hỏi chuyển sang plugin Gradle/Maven, `bootJar`, `bootWar`, layered archive, reproducible packaging, `bootBuildImage` hay Cloud Native Buildpacks, hãy sang module `build-tooling-packaging`.

**Purpose:**

Khép video bằng mental model có thể tái sử dụng và handoff toàn bộ mechanics build/deployment sang owner chuyên trách.
