---
video:
  url: ""
---

# Packaging có khả năng tái lập và archive metadata

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

## Vì sao khả năng tái lập khi đóng gói quan trọng?

<!-- VIDEO_SECTION -->

### Scene 1 — Để khác biệt artifact mang ý nghĩa thật

**Time:** `00:00–00:27`

**Visual:**

Hai build cùng source tạo hash khác nhau vì timestamp, sau đó hai build với metadata ổn định tạo cùng hash. Highlight “signal” và “incidental noise”.

**Script:**

Hash artifact chỉ hữu ích khi variation không liên quan đã được giảm tối đa. Nếu hai build tương đương khác nhau chỉ vì entry order hoặc timestamp lấy theo wall clock, rất khó biết application thực sự thay đổi hay chưa. Reproducible packaging loại bỏ loại noise đó để cache, comparison, provenance và incident analysis có tín hiệu đáng tin hơn.

**Purpose:**

Giải thích reproducibility như operational clarity thay vì một nhãn chất lượng trừu tượng.

## Thứ tự entry và timestamp trong archive ảnh hưởng khả năng tái lập thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:27–00:34`

**Visual:**

Mở ZIP/JAR viewer và highlight cả file entry lẫn metadata như order và timestamp.

**Script:**

Cùng một tập file logic chưa đủ để tạo cùng archive, vì JAR còn mang metadata của từng entry.

**Purpose:**

Nối hash stability với chi tiết archive-writing cụ thể.

### Scene 2 — Kiểm soát cả content lẫn metadata

**Time:** `00:34–01:01`

**Visual:**

Hiển thị reproducible ordering/timestamp controls của Gradle đi vào `BootJar / BootWar`. Bên cạnh là Maven `outputTimestamp` đi vào `repackage`. So sánh entry list và timestamp từ hai archive.

**Script:**

Entry order và timestamp có thể làm byte của archive khác nhau dù logical content giống nhau. Boot archive task trên Gradle kế thừa các reproducible archive control của Gradle; Maven repackage có cấu hình timestamp như `outputTimestamp`. Khi hai output khác nhau, hãy so entry list, timestamp, generated resources, manifest value và dependency trước khi kết luận nguyên nhân nằm ở compression.

**Purpose:**

Cho thấy metadata control và evidence cụ thể để chẩn đoán archive không reproducible.

## Nên hiểu khả năng tái lập thế nào trong quy trình đóng gói của Boot?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:01–01:08`

**Visual:**

Zoom out khỏi archive metadata sang toàn bộ input graph: generated resources, toolchain, dependencies, Git/build info, locale/time zone, code generation.

**Script:**

Archive setting chỉ loại bỏ một nguồn variation; nó không thể biến input không deterministic thành deterministic.

**Purpose:**

Mở rộng verification model từ archive-writing sang toàn bộ build input graph.

### Scene 3 — Xác minh đúng mức reproducibility mà project tuyên bố

**Time:** `01:08–01:38`

**Visual:**

Flow “same declared inputs + controlled toolchain → build twice → compare paths/versions → compare metadata → compare hashes”. Gắn hai nhãn logical equivalence và byte identity.

**Script:**

Không nên tuyên bố byte-for-byte reproducibility chỉ vì đã dùng `bootJar` hoặc `repackage`. Generated resource, build-info timestamp, dependency artifact, environment-sensitive code generation và nhiều input khác vẫn có thể đổi. Hãy định nghĩa contract: đầu tiên so logical contents và versions, sau đó metadata, rồi mới so hash nếu policy thật sự yêu cầu byte identity. Boot tạo điều kiện cho reproducible packaging, nhưng project phải kiểm soát input xung quanh.

**Purpose:**

Dạy verification theo từng tầng và tránh claim byte-identical khi chưa đủ evidence.

## Trách nhiệm đóng gói của Boot kết thúc ở đâu và công cụ chuỗi cung ứng phần mềm bắt đầu ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:38–01:45`

**Visual:**

Đưa stable artifact qua đường handoff sang các khối signing, SBOM/provenance, policy, vulnerability scanning và promotion.

**Script:**

Khi đã có artifact ổn định, downstream system mới có thể gắn trust và policy vào đúng output đó.

**Purpose:**

Kết nối reproducible packaging với supply-chain consumer nhưng vẫn giữ boundary ownership.

### Scene 4 — Stable artifact trước, supply-chain policy sau

**Time:** `01:45–02:10`

**Visual:**

Giữ “Boot packaging” quanh archive structure và metadata. Đặt signing, attestation, SBOM/provenance, vulnerability policy và registry promotion trong khối “supply-chain tooling” nối bằng artifact hash.

**Script:**

Spring Boot packaging sở hữu application archive structure và các metadata control mà nó expose. Signing infrastructure, attestation, SBOM hoặc provenance workflow, vulnerability policy và promotion system cấp tổ chức thuộc software supply chain. Những hệ thống này hưởng lợi từ artifact ổn định, nhưng chúng tiêu thụ output của build chứ không trở thành feature của Boot packaging.

**Purpose:**

Khép lại bằng ranh giới giữa việc tạo Boot artifact có thể dự đoán và việc quản trị artifact trong software supply chain.
