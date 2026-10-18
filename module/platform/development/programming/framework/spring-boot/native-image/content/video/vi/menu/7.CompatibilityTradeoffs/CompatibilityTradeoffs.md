---
video:
  url: ""
---

# Đánh đổi, khả năng tương thích và metadata của Native Image

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

## Nên đánh giá startup nhanh hơn và mức sử dụng bộ nhớ thấp hơn như thế nào?

<!-- VIDEO_SECTION -->

### Scene 1 — Nên đánh giá startup nhanh hơn và mức sử dụng bộ nhớ thấp hơn như thế nào?

**Time:** `00:00–00:36`

**Visual:**

Benchmark board apples-to-apples gồm cold start, steady-state throughput, tail latency, memory under load và CPU cho JVM/native dưới cùng configuration.

**Script:**

Hãy đo lợi ích native theo đúng bài toán vận hành. Cold start nhanh có giá trị với scale-to-zero, bursty worker và command application; memory thấp có thể tăng density hoặc giảm reservation. Nhưng start nhanh không đồng nghĩa mọi workload được phục vụ nhanh hơn. So sánh throughput, tail latency, memory dưới tải và CPU bằng configuration tương đương, đồng thời nhớ JVM chạy lâu có thể hưởng JIT optimization. Benchmark headline từ ứng dụng khác không đủ làm bằng chứng.

**Purpose:**

Biến claim hiệu năng native thành quyết định đo lường theo workload cụ thể.

## Những chi phí build-time và tài nguyên nào tăng lên?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:36–00:45`

**Visual:**

Trượt benchmark board sang trái và thêm delivery-cost timeline với compile time, CPU/RAM và build job theo platform.

**Script:**

Mọi runtime gain đều phải cân với tài nguyên build và feedback time cần để tạo native artifact lặp lại trong delivery pipeline.

**Purpose:**

Cân đối operational benefit với delivery cost.

### Scene 1 — Những chi phí build-time và tài nguyên nào tăng lên?

**Time:** `00:45–01:21`

**Visual:**

Flow JVM compile/test feedback trước, focused AOT/hint checks cho native-sensitive changes, reproducible native builds cho từng target ở release.

**Script:**

Native compilation thường chậm và dùng CPU/RAM nhiều hơn nhiều so với tạo JVM JAR. CI cần GraalVM toolchain phù hợp hoặc builder cung cấp nó, còn output theo platform có thể cần nhiều job riêng. Điều đó thay đổi feedback loop: giữ ordinary change trên JVM compile/test nhanh, thêm AOT/hint test tập trung cho native-sensitive work, và dùng full native build ở nơi signal xứng đáng. Hãy budget chi phí này trước khi native trở thành default delivery form.

**Purpose:**

Cho thấy native build cost thay đổi workflow developer và CI chứ không phải phiền toái một lần.

## Mức độ sẵn sàng cho native của dependency bên thứ ba ảnh hưởng ứng dụng như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:21–01:31`

**Visual:**

Thêm dependency graph và tô đỏ một library dùng unsupported reflection hoặc bytecode generation.

**Script:**

Dù application code đã thân thiện với AOT, một dependency quan trọng vẫn có thể làm quyết định native thất bại nếu thiếu support phù hợp.

**Purpose:**

Mở rộng compatibility analysis từ application code sang toàn dependency graph.

### Scene 1 — Mức độ sẵn sàng cho native của dependency bên thứ ba ảnh hưởng ứng dụng như thế nào?

**Time:** `01:31–02:05`

**Visual:**

Dependency readiness card gồm version, native support status, shipped reachability metadata, dynamic behaviors và kích thước application-owned workaround.

**Script:**

Native readiness có tính truyền dẫn. Ứng dụng có thể dùng pattern AOT-friendly nhưng dependency vẫn dựa vào deep reflection, runtime scanning, bytecode generation, native library hoặc resource discovery đặc thù chưa được mô tả. Hãy kiểm tra đúng library version có hỗ trợ GraalVM, có ship metadata hoặc được cover bởi reachability-metadata ecosystem không. Nếu application phải giữ workaround hint riêng rất lớn, hãy tính đó là maintenance cost và ưu tiên upstream fix khi có thể.

**Purpose:**

Đưa dependency version và native support thành readiness evidence hạng nhất.

## Vì sao chất lượng reachability metadata quan trọng?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:05–02:14`

**Visual:**

Zoom vào metadata của dependency và so sánh missing entry, precise entry và reflection registration quá rộng.

**Script:**

Chất lượng support không chỉ là có hay không; độ chính xác và ownership của reachability metadata ảnh hưởng cả correctness lẫn maintainability.

**Purpose:**

Giải thích vì sao metadata phải narrow, version-aware và được test.

### Scene 1 — Vì sao chất lượng reachability metadata quan trọng?

**Time:** `02:14–02:47`

**Visual:**

Metadata quality checklist: owner gần dynamic behavior, operation cụ thể, version-aware condition, focused test; catch-all metadata quá rộng được đánh dấu smell.

**Script:**

Reachability metadata là input của executable build. Thiếu metadata có thể làm mất behavior cần thiết; metadata quá rộng lại giữ type hoặc resource không cần và che khuất dynamic contract thật. Metadata tốt nên được sở hữu gần hành vi, đủ cụ thể để giải thích access, aware với thay đổi version và có test exercise dynamic path. Hãy review application-owned metadata như code và xóa workaround khi dependency upgrade đã cung cấp support chính thức.

**Purpose:**

Dạy metadata quality như vấn đề correctness và maintenance, không chỉ mẹo để build pass.

## Phân biệt lỗi hint, metadata, tích hợp build và dependency như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:47–02:57`

**Visual:**

Biến metadata checklist thành diagnostic ladder, highlight boundary cuối cùng còn hoạt động.

**Script:**

Khi có lỗi, metadata chỉ là một khả năng; hãy phân loại theo boundary cuối cùng còn pass trước khi đổi hint hay compiler flag.

**Purpose:**

Thay visual hints-table sai ngữ cảnh bằng failure classifier theo từng layer.

### Scene 1 — Phân biệt lỗi hint, metadata, tích hợp build và dependency như thế nào?

**Time:** `02:57–03:33`

**Visual:**

Branches: JVM fail → app/config; JVM pass nhưng AOT fail → prepared model; AOT pass nhưng native compile fail → toolchain/unsupported construct/metadata; native start nhưng dynamic path fail → hints/resources/proxy/dependency; một library feature fail → native support của library.

**Script:**

Hãy phân loại theo boundary cuối cùng còn đúng. Nếu JVM application cũng fail, sửa application hoặc configuration trước. JVM chạy nhưng AOT fail thì xem prepared Spring model hay unsupported registration. AOT pass mà native compile fail thì xem native build configuration, unsupported construct, metadata và toolchain. Executable start được nhưng một dynamic path fail thì kiểm tra hints, resources, proxies, reflection và dependency metadata. Giữ logs cùng generated assets làm evidence và thay từng biến một để không che root cause.

**Purpose:**

Cung cấp failure classifier đầy đủ, không còn truncation hay fragment rác.

## Khi nào triển khai JVM thông thường là lựa chọn tốt hơn?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:33–03:43`

**Visual:**

Đặt failure ladder cạnh benchmark board và thêm decision arrow quay về JVM khi native cost lớn hơn measured value.

**Script:**

Build native sạch không phải mục tiêu cuối; quyết định cuối cùng vẫn là runtime nào phù hợp nhất với workload, dependency và cách team vận hành.

**Purpose:**

Biến JVM deployment thành lựa chọn first-class dựa trên evidence, không phải failure state.

### Scene 1 — Khi nào triển khai JVM thông thường là lựa chọn tốt hơn?

**Time:** `03:43–04:18`

**Visual:**

Decision matrix ưu tiên JVM cho service chạy lâu/JIT-sensitive, dependency native support yếu, JVM-only diagnostics hoặc native build latency quá lớn; ưu tiên native khi measured startup/memory gain trả được chi phí.

**Script:**

Ưu tiên JVM khi lợi ích native nhỏ hơn build và compatibility cost: service chạy lâu ít scale, workload hưởng lợi mạnh từ JIT, dependency stack hỗ trợ native yếu, team phụ thuộc JVM-only agent hoặc diagnostics, hay pipeline bị native compile latency chi phối. JVM không phải phương án hiện đại hóa thất bại; nó là Boot runtime first-class với dynamic compatibility rộng nhất. Architecture tốt có thể giữ cả hai option và chọn dựa trên constraint đã đo.

**Purpose:**

Chốt chapter bằng quyết định native-versus-JVM đối xứng và dựa trên bằng chứng.
