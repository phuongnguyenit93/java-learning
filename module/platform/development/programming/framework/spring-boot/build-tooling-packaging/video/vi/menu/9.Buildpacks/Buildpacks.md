---
video:
  url: ""
---

# Cloud Native Buildpacks

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

## Builder, buildpack và run image là gì?

<!-- VIDEO_SECTION -->

### Scene 1 — Tách ba vai trò trong Buildpacks

**Time:** `00:00–00:28`

**Visual:**

Hiển thị ba khối: builder image = build environment + lifecycle + buildpacks; buildpack = detection + contributed layers; run image = runtime base. Đưa Boot application vào builder và cho OCI image đi ra.

**Script:**

Cloud Native Buildpacks tạo OCI image mà application team không cần tự viết toàn bộ Dockerfile. Builder cung cấp build environment, lifecycle và tập buildpack có sẵn. Mỗi buildpack phát hiện nhu cầu của application rồi đóng góp build hoặc runtime layer. Run image là base runtime của image cuối cùng. Spring Boot tích hợp với mô hình này chứ không thay thế Buildpacks specification.

**Purpose:**

Cung cấp bộ từ vựng tối thiểu để hiểu Boot Buildpacks integration.

## Người dùng Boot cần hiểu phần nào của vòng đời Buildpack?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:28–00:36`

**Visual:**

Biến builder block thành lifecycle timeline chạy từ trái sang phải.

**Script:**

Developer không cần implement lifecycle, nhưng cần nhớ đủ thứ tự để biết image build thất bại ở giai đoạn nào.

**Purpose:**

Chuyển từ vai trò Buildpacks sang ordered diagnostic model của lifecycle.

### Scene 2 — Theo dõi analyze, detect, restore, build và export

**Time:** `00:36–01:06`

**Visual:**

Animate `analyze → detect → restore → build → export`. Dưới mỗi phase là một evidence cue: prior image state, applicable buildpacks, restored cache, contributed layers, final OCI image/registry.

**Script:**

Ở mức đủ dùng cho Boot developer, lifecycle đọc state của image trước, detect buildpack phù hợp, restore layer có thể reuse, build application và runtime content rồi export image. Boot task điều phối flow này và log của task là evidence đầu tiên. Detect thất bại thường dẫn về application input hoặc builder; export thất bại xảy ra muộn hơn và có thể liên quan image hoặc registry.

**Purpose:**

Cung cấp đủ thứ tự lifecycle để phân loại failure mà không đi sâu vào Buildpacks internals.

## Buildpack layer và cache hỗ trợ các lần build lặp lại như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:06–01:15`

**Visual:**

Chạy lại lần build thứ hai khi chỉ application classes thay đổi và highlight những layer cũ còn reuse được.

**Script:**

Lifecycle có thứ tự rõ cũng giúp ta thấy vì sao lần build sau không nhất thiết phải làm lại mọi thứ.

**Purpose:**

Kết nối lifecycle phase với giá trị thực tế của layer và cache.

### Scene 3 — Reuse phần runtime và dependency ổn định

**Time:** `01:15–01:47`

**Visual:**

Hiển thị build cache, launch cache và các layer cho JDK/runtime, dependencies, application content. Thay đổi một source file, đánh dấu stable layers reused và application layer rebuilt.

**Script:**

Buildpacks tách runtime component và application content thành layer có thể reuse, đồng thời giữ cache state qua nhiều lần build. Một source edit không nhất thiết làm JDK và toàn bộ dependency bị build lại. Tuy nhiên cache hit còn phụ thuộc builder, dependency, environment và việc cache có bị clean hay không. Hãy đọc log để biết layer nào thực sự được reuse thay vì chỉ thấy “cache enabled” rồi giả định.

**Purpose:**

Giải thích cache reuse như behavior quan sát được và chỉ ra các nguyên nhân invalidate phổ biến.

## Spring Boot cho phép tùy chỉnh những đầu vào Buildpack nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:47–01:54`

**Visual:**

Đặt configuration panel trước builder với các control: image name, builder, run image, environment, buildpacks, bindings, caches, network, publish.

**Script:**

Khi default không phù hợp, Boot expose những input Buildpacks mà application team thường cần kiểm soát nhất.

**Purpose:**

Chuyển từ hiểu lifecycle sang cấu hình lifecycle bằng supported Boot input.

### Scene 4 — Ưu tiên high-level input được hỗ trợ

**Time:** `01:54–02:26`

**Visual:**

Highlight image name, builder/run image, buildpack environment variables, additional buildpacks, bindings, cache configuration, builder network và registry credentials. So sánh stable builder reference với floating tag có warning.

**Script:**

Boot Gradle và Maven integration cho phép cấu hình target image, builder, run image, buildpack environment, extra buildpack, binding, cache, network-related setting và publication credential. Nên dùng input cấp cao được support để diễn đạt requirement thay vì thay cả builder chỉ vì một option nhỏ. Với pipeline cần repeatable, cũng phải quản lý builder version có chủ đích vì floating tag có thể đổi JDK hoặc buildpack behavior mà source không hề thay đổi.

**Purpose:**

Cho thấy customization surface được hỗ trợ và liên hệ nó với image build có thể tái lập.

## Tích hợp Buildpack kết thúc ở đâu và ngữ nghĩa của native image bắt đầu ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:26–02:35`

**Visual:**

Tách output Buildpacks thành “JVM image” và “native executable image”, rồi đặt boundary trước AOT, closed-world analysis, hints và native tests.

**Script:**

Buildpacks có thể tham gia nhiều runtime strategy, nên command tạo image không quyết định owner của mọi semantic question phía sau.

**Purpose:**

Chuẩn bị ownership handoff từ image invocation sang native-image semantics.

### Scene 5 — Có thể chung invocation nhưng semantic owner vẫn tách biệt

**Time:** `02:35–03:15`

**Visual:**

Hiển thị `bootBuildImage / build-image` ở packaging side. Thêm callout Gradle: apply `org.graalvm.buildtools.native` khiến Boot chọn native-oriented default builder và đặt `BP_NATIVE_IMAGE=true`. Sau đó mới đi sang khối “native-image” chứa AOT, hints, closed-world constraints và compatibility.

**Script:**

Spring Boot có thể dùng Buildpacks để tạo JVM image thông thường hoặc image chứa native executable. Với Gradle, apply GraalVM Native Image plugin không chỉ là convention tài liệu: Boot phản ứng bằng cách chọn native-oriented builder mặc định và truyền `BP_NATIVE_IMAGE=true` cho builder. Automatic bridge đó thuộc build integration. Khi câu hỏi chuyển sang vì sao cần AOT, reflection hint có thiếu không, closed-world analysis hoạt động thế nào hay native test ra sao thì owner đã là curriculum native-image. Image command chỉ là entry point, không sở hữu native semantics.

**Purpose:**

Giữ automatic native Buildpacks bridge trong build-tooling nhưng bàn giao AOT, hints, closed-world behavior và native testing cho owner native-image.
