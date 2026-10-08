---
video:
  url: ""
---

# Đánh đổi packaging và bàn giao sang deployment

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

## Chọn executable JAR, WAR, unpacked archive hay OCI image như thế nào?

<!-- VIDEO_SECTION -->

### Scene 1 — Bắt đầu từ execution contract của môi trường

**Time:** `00:00–00:32`

**Visual:**

Bốn delivery unit nối tới target tương ứng: executable JAR → standalone JVM; WAR → external servlet container; extracted archive → platform dùng file/library tách riêng; OCI image → container platform.

**Script:**

Các packaging format không phải những nấc trưởng thành từ thấp tới cao. Hãy chọn unit phù hợp với cách môi trường đích khởi động và quản lý ứng dụng. Standalone JVM thường dùng executable JAR, external servlet container có thể yêu cầu WAR, một số platform hưởng lợi từ extracted layout, còn container platform tiêu thụ OCI image. Application code có thể giữ nguyên dù trách nhiệm giữa artifact và platform thay đổi.

**Purpose:**

Thiết lập packaging choice như quyết định dựa trên execution contract.

## Môi trường bàn giao nên ảnh hưởng lựa chọn packaging ra sao?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:32–00:41`

**Visual:**

Biến bốn artifact choice thành các câu hỏi: start thế nào, update ra sao, cache ở đâu, observe và secure bằng cách nào?

**Script:**

Format sẽ dễ chọn hơn khi ta ngừng hỏi Boot build được gì và bắt đầu hỏi delivery environment tự nhiên tiêu thụ gì.

**Purpose:**

Chuyển từ artifact taxonomy sang environment-driven decision criteria.

### Scene 2 — Giảm transformation không cần thiết sau build

**Time:** `00:41–01:10`

**Visual:**

So hai flow: build → final delivery unit → deploy; và build → JAR → ad-hoc repackaging → image khác → deploy. Đánh dấu mỗi transformation là nơi identity hoặc config có thể drift.

**Script:**

Hãy nhìn vào cách môi trường start, update, cache, observe và secure application. VM có thể chỉ cần JAR với JDK; Kubernetes thường dùng image; corporate servlet platform có thể bắt buộc WAR. Ưu tiên đường có ít transformation ngoài ý muốn. Nếu mọi deployment đều biến JAR thành image, hãy quyết định rõ conversion đó thuộc central platform hay nên được làm sớm ngay trong Boot build.

**Purpose:**

Kết nối delivery format với platform behavior và giảm các bước repackaging không có owner rõ.

## Ranh giới giữa Boot packaging và vận hành deployment nằm ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:10–01:17`

**Visual:**

Đặt JAR/image đã chọn trên đường handoff và reveal các deployment action ở phía bên kia.

**Script:**

Khi final unit đã được chọn và build, ownership chuyển từ tạo artifact sang vận hành artifact.

**Purpose:**

Thiết lập build-to-deployment ownership boundary.

### Scene 3 — Promote đúng unit đã được verify

**Time:** `01:17–01:43`

**Visual:**

Bên trái: Boot tạo JAR checksum hoặc image digest. Bên phải: repository/registry, secret injection, scheduling, traffic routing, rollout, rollback, scaling. Cùng checksum/digest đi qua các environment.

**Script:**

Boot packaging tạo unit cần bàn giao. Deployment infrastructure quyết định nó chạy ở đâu, khi nào, nhận secret nào, traffic đi tới ra sao và rollout hay scale thế nào. Handoff nên immutable: ghi lại JAR checksum hoặc image digest rồi promote chính unit đó. Rebuild trong lúc deploy sẽ trộn build responsibility với rollout responsibility và làm yếu traceability.

**Purpose:**

Biến immutable artifact identity thành evidence nối build output với deployment operations.

## Packaging JVM thông thường bàn giao sang native-image ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:43–01:50`

**Visual:**

Tách delivery path thành “JVM bytecode + JVM runtime” và “AOT/native analysis → platform-specific executable”.

**Script:**

Có một nhánh packaging thay đổi nhiều hơn lớp vỏ của application: native image thay đổi chính execution model.

**Purpose:**

Chuẩn bị semantic handoff từ JVM packaging sang native-image curriculum.

### Scene 4 — Cùng image wrapper nhưng build semantics khác nhau

**Time:** `01:50–02:14`

**Visual:**

Một JVM OCI image có JVM và Boot application, bên cạnh native OCI image chứa platform-specific executable. Đặt AOT, hints, closed-world analysis và native tests trong owner box riêng.

**Script:**

Default path của module này tạo JVM-oriented artifact: executable archive hoặc image vẫn chứa JVM runtime. Native build đưa AOT processing và platform-specific analysis vào build. Boot plugin hay Buildpacks command có thể trigger workflow, nhưng closed-world constraint, runtime hint, compatibility và native test thuộc module native-image.

**Purpose:**

Tách packaging invocation khỏi semantics riêng của native-image production.

## Toàn bộ luồng build và packaging của Boot kết nối với nhau thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:14–02:20`

**Visual:**

Zoom out thành horizontal pipeline trống trải từ dependencies, development, packaging, image creation tới deployment handoff.

**Script:**

Giờ có thể nối tất cả concept của module thành một build-and-delivery chain duy nhất.

**Purpose:**

Tổng hợp các video trước thành một ordered mental model.

### Scene 5 — Đọc toàn bộ JVM-oriented delivery chain

**Time:** `02:20–02:52`

**Visual:**

Reveal tuần tự: declare dependencies → align bằng Boot BOM → apply Boot plugin → `bootRun / spring-boot:run` → `bootJar / bootWar / repackage` → optional layering/extraction → optional Buildpacks OCI image → deployment handoff.

**Script:**

Flow bắt đầu từ dependency được khai báo và compatibility baseline của Boot. Plugin hỗ trợ development execution nhanh, executable packaging, optional layer hoặc extraction, rồi khi cần thì Buildpacks OCI image. Mỗi stage trả lời một câu hỏi khác và tạo evidence cho stage tiếp theo. Chính việc giữ các boundary này tách biệt giúp team locate failure thay vì gọi mọi lệnh Gradle hay Maven thất bại là cùng một loại build problem.

**Purpose:**

Cung cấp bản đồ duy nhất nối thứ tự các concept trong module với output của chúng.

## Phân loại lỗi giữa plugin, packaging, image và deployment như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:52–03:00`

**Visual:**

Giữ end-to-end pipeline và đặt green checkpoint sau từng boundary đã thành công.

**Script:**

Pipeline đó cũng là troubleshooting tool: checkpoint thành công gần nhất cho biết subsystem nào đã hoàn tất trách nhiệm.

**Purpose:**

Biến synthesized flow thành failure-classification method.

### Scene 6 — Chẩn đoán từ boundary thành công gần nhất

**Time:** `03:00–03:33`

**Visual:**

Highlight theo thứ tự: dependency/plugin application fail; compile thành công nhưng `bootJar/repackage` fail; JAR chạy nhưng `bootBuildImage` fail; image build được nhưng publish/pull fail; artifact launch được nhưng ApplicationContext fail. Cuối cùng so built digest với deployed digest.

**Script:**

Dependency resolution hoặc plugin application fail thì bắt đầu ở build setup. Compile đã qua nhưng executable packaging fail thì kiểm Boot packaging và main-class configuration. JAR chạy được nhưng image task fail thì kiểm Buildpacks, builder, daemon hoặc image config. Image build được nhưng registry operation fail thì chuyển sang registry hoặc deployment infrastructure. Artifact launch được rồi ApplicationContext mới fail thì đã sang application runtime. Luôn xác minh đúng checksum hoặc digest đã qua handoff.

**Purpose:**

Khép lại module bằng troubleshooting method dựa trên evidence xuyên qua build, packaging, image, deployment và runtime boundary.
