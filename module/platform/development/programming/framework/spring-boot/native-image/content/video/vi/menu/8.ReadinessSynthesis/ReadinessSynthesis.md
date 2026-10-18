---
video:
  url: ""
---

# Mức độ sẵn sàng cho Native Image từ đầu đến cuối

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

## Toàn bộ hành trình Native Image của Boot kết nối với nhau như thế nào?

<!-- VIDEO_SECTION -->

### Scene 1 — Toàn bộ hành trình Native Image của Boot kết nối với nhau như thế nào?

**Time:** `00:00–00:36`

**Visual:**

Vẽ toàn bộ evidence chain: normal Boot model → native goal + build environment → Spring AOT → Runtime Hints/metadata → GraalVM compile → focused native tests → executable hoặc OCI delivery.

**Script:**

Native readiness là một chuỗi bằng chứng, không phải túi compiler flags. Bắt đầu từ Boot application bình thường và lý do rõ ràng để cân nhắc native. Tái lập build-time environment, để Spring AOT chuẩn bị model, giữ dynamic needs bằng hints và reachability metadata, compile closed-world program với GraalVM, chạy native-sensitive paths rồi mới bàn giao binary hoặc OCI image. Nếu một mắt xích chưa được chứng minh, quay về boundary sớm nhất thay vì bù bằng flag ở cuối pipeline.

**Purpose:**

Tổng hợp toàn module thành causal flow dùng được cả khi delivery lẫn diagnosis.

## Cần kiểm tra gì trước khi quyết định triển khai native?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:36–00:46`

**Visual:**

Biến flow thành production-readiness checklist; mỗi mắt xích trở thành một measurable gate.

**Script:**

Một build pass một lần là evidence yếu hơn checklist tái lập được cho thấy runtime goal, toolchain, dependency và operation đều phù hợp native delivery.

**Purpose:**

Chuyển conceptual chain thành go/no-go evidence cụ thể.

### Scene 1 — Cần kiểm tra gì trước khi quyết định triển khai native?

**Time:** `00:46–01:22`

**Visual:**

Checklist gate: measured startup/memory goal, reproducible JDK/GraalVM/Boot, structural profiles/properties, dynamic metadata, dependency readiness, native-sensitive tests, CI capacity, OS/architecture, diagnostics và JVM rollback.

**Script:**

Trước khi chọn native cho production, cần xác nhận nhiều hơn compile pass. Runtime goal phải đo được; JDK, GraalVM và Boot toolchain phải tái lập; profile/property ảnh hưởng structure phải được hiểu; reflection, resource và proxy cần metadata hỗ trợ; critical dependency phải native-ready; native-sensitive path phải có test; CI đủ CPU, memory và thời gian; target OS/architecture rõ ràng; diagnostics vẫn chấp nhận được; và khi risk yêu cầu, JVM fallback hoặc rollback path cũng phải được hiểu.

**Purpose:**

Khôi phục checklist production readiness đầy đủ và sạch truncation.

## Xác định lỗi nằm ở AOT, closed-world, hints, build hay dependency như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:22–01:31`

**Visual:**

Sắp checklist thành numbered failure ladder và chỉ highlight bước đầu tiên fail.

**Script:**

Các gate hữu ích nhất khi chẩn đoán nếu ta kiểm tra theo thứ tự và dừng ở boundary sớm nhất bị phá vỡ.

**Purpose:**

Khôi phục đầy đủ seven-step failure locator thay vì chỉ còn hai mục đầu.

### Scene 1 — Xác định lỗi nằm ở AOT, closed-world, hints, build hay dependency như thế nào?

**Time:** `01:31–02:07`

**Visual:**

Bảy bước: 1 JVM build/run, 2 JVM tests, 3 AOT processing, 4 inspect generated AOT/hint assets, 5 native compilation, 6 native startup, 7 failing native-specific code path; gắn owner gợi ý cho bước 3, 5 và 7.

**Script:**

Khoanh vùng native failure bằng cách chỉ đi tiếp khi boundary trước đã pass. Một: regular JVM build và run. Hai: ordinary JVM tests. Ba: AOT processing. Bốn: inspect generated bean code cùng hint hoặc resource assets mong đợi. Năm: native compilation. Sáu: native startup. Bảy: exercise native-specific code path bị lỗi. Fail ở AOT hướng về prepared Spring model; fail ở compile hướng về toolchain, unsupported construct hoặc reachability input; fail ở dynamic runtime path hướng về hints, dependency hoặc environment difference.

**Purpose:**

Cung cấp failure-localization algorithm đầy đủ để đưa debug về đúng layer.

## Chọn native executable, native container image hay triển khai JVM như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:07–02:16`

**Visual:**

Từ failure ladder tách ra ba delivery card: bare native executable, native OCI image và JVM JAR/JVM OCI image.

**Script:**

Khi application đã chứng minh readiness, hãy tách quyết định runtime khỏi quyết định packaging để không nhầm containerization với native execution.

**Purpose:**

Làm rõ hai lựa chọn độc lập: native hay JVM, và direct artifact hay OCI.

### Scene 1 — Chọn native executable, native container image hay triển khai JVM như thế nào?

**Time:** `02:16–02:49`

**Visual:**

Ma trận 2x2: runtime form native/JVM trên một trục, direct artifact/OCI trên trục kia; native executable và native OCI cùng dùng native runtime semantics nhưng delivery contract khác nhau.

**Script:**

Hãy chọn runtime form và packaging form riêng. Bare native executable là direct OS-process delivery. Native OCI image mang cùng native runtime trong container contract. JVM JAR hoặc JVM OCI image giữ runtime JVM thông thường với dynamic compatibility rộng hơn và build feedback nhanh hơn. Native executable hay native image chủ yếu là packaging theo deployment platform; native hay JVM phải dựa trên runtime economics, build cost, compatibility, diagnostics và operational requirement đã đo.

**Purpose:**

Ngăn packaging terminology che khuất trade-off runtime quan trọng hơn.

## Các đầu ra được tạo, kết quả kiểm thử và bằng chứng về khả năng tương thích nào nên dẫn dắt quyết định?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:49–03:00`

**Visual:**

Cho ba delivery card đi vào evidence loop gồm generated AOT code, hint metadata, build logs, focused tests, dependency support và production-like measurements.

**Script:**

Delivery choice phải có thể thay đổi khi evidence thay đổi; readiness native cần được duy trì chứ không đạt vĩnh viễn chỉ vì một lần compile thành công.

**Purpose:**

Biến build success một lần thành practice duy trì bằng chứng theo thời gian.

### Scene 1 — Các đầu ra được tạo, kết quả kiểm thử và bằng chứng về khả năng tương thích nào nên dẫn dắt quyết định?

**Time:** `03:00–03:36`

**Visual:**

Feedback loop: generated AOT source/hints → native build logs → focused native tests → dependency support docs → startup/memory/load measurements → decision; thêm action tạo test cho hint mới và xóa workaround cũ sau upgrade.

**Script:**

Hãy dùng generated AOT source và hint metadata, native build logs, focused native tests, dependency support documentation cùng số đo startup, memory và runtime gần production như một feedback loop. Nếu application hint sửa được lỗi, thêm test chứng minh registration đó. Nếu dependency upgrade cung cấp support chính thức, xóa workaround đã stale. Nếu measurement cho thấy native không còn tạo giá trị đáng kể, giữ hoặc quay lại JVM thay vì duy trì complexity chỉ vì đã đầu tư trước đó.

**Purpose:**

Định nghĩa native readiness là evidence được duy trì cùng code, dependency và workload measurement.

## Module lân cận nào chịu trách nhiệm lớp chi tiết tiếp theo?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:36–03:46`

**Visual:**

Kết thúc bằng responsibility map nối evidence loop tới auto-configuration, application-runtime, build-tooling-packaging, testing, Java/Spring Framework và GraalVM.

**Script:**

End-to-end view cũng cho biết lúc nào nên dừng: khi vấn đề trở thành cơ chế sâu của module lân cận, hãy bàn giao cho đúng owner.

**Purpose:**

Khép module bằng ownership boundary rõ ràng, không còn fragment navigation bị leak.

### Scene 1 — Module lân cận nào chịu trách nhiệm lớp chi tiết tiếp theo?

**Time:** `03:46–04:18`

**Visual:**

Conditional beans → auto-configuration; lifecycle → application-runtime; Maven/Gradle/Buildpacks/OCI → build-tooling-packaging; general tests → testing; reflection/proxy/class-loader semantics → Java/Spring Framework; compiler internals → GraalVM.

**Script:**

Dùng boundary map khi native problem mở rộng. Conditional bean và auto-configuration semantics thuộc auto-configuration; lifecycle và events bình thường thuộc application-runtime; Maven, Gradle, Buildpacks và OCI mechanics tổng quát thuộc build-tooling-packaging; Boot test design chung thuộc testing; reflection, proxy và class-loader semantics thuộc Java hoặc Spring Framework owner; còn low-level native compiler behavior thuộc GraalVM. Native-image giữ integration story từ đầu đến cuối rồi bàn giao cơ chế sâu cho đúng nơi.

**Purpose:**

Kết thúc bằng handoff map ổn định để tránh duplication giữa các module lân cận.
