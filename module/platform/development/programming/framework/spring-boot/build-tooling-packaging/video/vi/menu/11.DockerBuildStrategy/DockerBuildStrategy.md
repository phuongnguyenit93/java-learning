---
video:
  url: ""
---

# Dockerfile, Buildpacks, layering và cache strategy

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

## Khi nào nên chọn Dockerfile hoặc Cloud Native Buildpacks?

<!-- VIDEO_SECTION -->

### Scene 1 — Chọn theo mức kiểm soát và ownership cần thiết

**Time:** `00:00–00:28`

**Visual:**

Bảng so sánh. Buildpacks: standardized builder, ít recipe code per-app, lifecycle-managed layers. Dockerfile: base image và command rõ ràng, custom OS packages/layout, nhiều policy do repository sở hữu hơn.

**Script:**

Buildpacks và Dockerfile đều có thể tạo OCI image hợp lệ; lựa chọn nằm ở mức kiểm soát và ai sẽ bảo trì nó. Buildpacks hợp khi tổ chức muốn một application-to-image path chuẩn hóa và giảm recipe code ở từng service. Dockerfile thường rõ hơn khi image cần base-image policy, filesystem step, OS package hoặc process composition mà application team phải kiểm soát trực tiếp.

**Purpose:**

Định vị Dockerfile-versus-Buildpacks như trade-off về ownership thay vì xếp hạng tuyệt đối.

## Archive layering liên hệ thế nào với image layering?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:28–00:37`

**Visual:**

Đặt file Boot `layers.idx` dưới bảng so sánh và nối nó với stack OCI image layers.

**Script:**

Cả hai strategy đều nói về layer, nhưng Boot archive layer và OCI image layer đang mô tả hai đối tượng khác nhau.

**Purpose:**

Chuẩn bị phân biệt packaging metadata với image filesystem history.

### Scene 2 — Archive group có thể hướng dẫn cách dựng image

**Time:** `00:37–01:04`

**Visual:**

Bên trái: archive files grouped theo `layers.idx`. Bên phải: OCI filesystem layers. Hiển thị một Dockerfile extraction path dùng archive groups và một Buildpacks path tự tạo layer qua lifecycle.

**Script:**

Boot archive layering phân loại file bên trong application artifact. OCI image layer ghi lại filesystem change của image. Dockerfile có thể extract các logical group của Boot thành image layer riêng, trong khi Buildpacks có thể tạo layer trực tiếp theo lifecycle của nó. Hai concept liên hệ vì đều ảnh hưởng cache reuse, nhưng `layers.idx` không phải bản thân OCI layer history.

**Purpose:**

Ngăn từ “layer” làm người học trộn hai packaging model khác nhau.

## Mức kiểm soát, tính chuẩn hóa và trách nhiệm bảo trì đánh đổi với nhau thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:04–01:12`

**Visual:**

Slider từ “central platform convention” tới “per-service explicit control”, đặt Buildpacks và Dockerfile ở các vị trí khác nhau.

**Script:**

Technical choice này đồng thời quyết định ai phải xử lý khi base image, JDK hoặc security policy thay đổi.

**Purpose:**

Mở rộng so sánh từ cú pháp sang organizational maintenance responsibility.

### Scene 3 — Xác định rõ ai sở hữu việc upgrade

**Time:** `01:12–01:42`

**Visual:**

Hiển thị vulnerability alert. Nhánh Buildpacks cập nhật một centrally managed builder/run image rồi rebuild nhiều app. Nhánh Dockerfile cho từng repository cập nhật pinned base image. Thêm note custom builder/buildpack có thể gom requirement dùng chung.

**Script:**

Với Buildpacks, platform team có thể nâng managed builder hoặc run image rồi áp policy mới cho nhiều application. Với Dockerfile, mỗi repository nhìn thấy và pin chính xác base cùng command của mình nhưng cũng thường nhận nhiều maintenance work hơn. Ngay cả yêu cầu thêm OS package cũng chưa chắc buộc từng service có Dockerfile; custom builder hoặc buildpack dùng chung có thể là owner hợp lý hơn.

**Purpose:**

Đưa upgrade và security maintenance ownership vào quyết định image strategy.

## Quyết định cache nào thuộc tích hợp Boot và quyết định nào thuộc công cụ container?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:42–01:51`

**Visual:**

Ba tầng cache: Boot archive grouping, Buildpacks build/launch caches, Docker/BuildKit hoặc registry-backed image cache.

**Script:**

Cache performance đi qua nhiều tool, nên tune nhầm layer rất dễ tạo nhiều config mà không tác động đúng vấn đề.

**Purpose:**

Thiết lập cache troubleshooting map theo ownership boundary.

### Scene 4 — Chẩn đoán cache miss tại đúng owner

**Time:** `01:51–02:29`

**Visual:**

Decision list: wrong archive layer membership → Boot packaging; wrong Buildpacks build/launch cache hoặc temporary workspace → Boot image integration; Dockerfile/BuildKit cache miss hoặc registry cache policy → container tooling. Hiện default named volume gắn với target image identity và việc image name đổi liên tục có thể làm cache reuse phân mảnh. Kết thúc bằng log reused/rebuilt layers.

**Script:**

Boot sở hữu archive layer configuration cùng cache/workspace option mà image task expose. Buildpacks path có build cache, launch cache và temporary build workspace riêng; mặc định chúng thường dùng Docker volume có identity suy ra từ target image/build configuration, nên image name đổi quá thường xuyên có thể làm reuse bị phân mảnh. Dockerfile/BuildKit cache, registry-backed cache policy, daemon pruning và CI cache transport thuộc container tooling. Hãy đo layer nào được reuse và input nào làm invalidate thay vì chỉ tối đa hóa số lượng layer.

**Purpose:**

Cung cấp cách troubleshoot cache gồm build/launch cache và workspace của Boot trước khi bàn giao generic image-cache behavior cho container tooling.
