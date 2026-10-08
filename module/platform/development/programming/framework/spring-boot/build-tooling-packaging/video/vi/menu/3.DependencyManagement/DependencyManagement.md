---
video:
  url: ""
---

# Dependency management và Spring Boot BOM

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

## `spring-boot-dependencies` là gì?

<!-- VIDEO_SECTION -->

### Scene 1 — Compatibility baseline chứ không phải gói dependency tự động

**Time:** `00:00–00:25`

**Visual:**

Hiển thị một BOM card tên `spring-boot-dependencies` với nhiều hàng version cho Spring và third-party library. Chỉ những dependency thật sự được application khai báo mới có đường nối tới managed version tương ứng.

**Script:**

`spring-boot-dependencies` là BOM do Spring Boot curate. Nó cung cấp baseline version đã được kiểm thử cho nhiều library để application không phải ghi từng version riêng lẻ. BOM không tự đưa tất cả library được quản lý vào classpath; nó trả lời câu hỏi version nào nên được chọn khi dependency đó thực sự xuất hiện trong build.

**Purpose:**

Phân biệt dependency version management với việc dependency có được đưa vào application hay không.

## Maven parent và BOM import khác nhau thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:25–00:33`

**Visual:**

Tách BOM card thành hai Maven project: một project inherit `spring-boot-starter-parent`, project còn lại giữ parent riêng và chỉ import BOM.

**Script:**

Maven có hai đường phổ biến để dùng baseline này, nhưng lượng convention nhận được từ mỗi đường khác nhau.

**Purpose:**

Chuyển từ BOM concept sang quyết định parent-versus-import trong Maven.

### Scene 2 — Parent convention so với BOM-only management

**Time:** `00:33–00:57`

**Visual:**

So sánh hai checklist. Parent: dependency management, plugin configuration, useful defaults. BOM import: managed dependency versions nhưng giữ parent khác.

**Script:**

Khi dùng `spring-boot-starter-parent`, Maven project nhận dependency management cùng nhiều build default và plugin configuration hữu ích. Khi chỉ import `spring-boot-dependencies` trong `dependencyManagement`, project chủ yếu nhận baseline version và vẫn có thể giữ parent riêng. Hai cách này không phải hai cách viết tương đương; một cách nhận nhiều convention hơn, cách kia hẹp hơn.

**Purpose:**

Làm rõ project nhận được gì và không tự động nhận gì ở mỗi đường tích hợp Maven.

## Gradle sử dụng các version dependency do Boot quản lý như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:57–01:05`

**Visual:**

Đưa Maven comparison ra khỏi màn hình, thay bằng hai nhánh Gradle: `io.spring.dependency-management` và native `platform(...)` / `enforcedPlatform(...)`.

**Script:**

Gradle dùng cùng Boot BOM nhưng tiêu thụ nó theo dependency model của Gradle thay vì cơ chế parent của Maven.

**Purpose:**

Nối Boot BOM chung với các lựa chọn tích hợp riêng của Gradle.

### Scene 3 — Hai đường Gradle tới cùng một baseline

**Time:** `01:05–01:46`

**Visual:**

Cho Boot plugin tự import BOM khi `io.spring.dependency-management` được áp dụng. Bên cạnh là native `platform(...)` / `enforcedPlatform(...)` tiêu thụ cùng BOM. Highlight property-based customization ở plugin path, Gradle-native constraint ở platform path và khác biệt recommendation của `platform` với requirement của `enforcedPlatform`.

**Script:**

Với Gradle, khi `io.spring.dependency-management` được áp dụng cùng Boot plugin, Boot tự import BOM tương ứng và đường này hỗ trợ tùy chỉnh managed version bằng property. Đường còn lại là native BOM support của Gradle với `platform` hoặc `enforcedPlatform`; nó bám sát model Gradle hơn và thường nhanh hơn. `platform` đưa ra version recommendation có thể bị constraint khác ảnh hưởng, còn `enforcedPlatform` biến version BOM thành requirement cho configuration sử dụng nó. Dù chọn đường nào, Boot cung cấp compatibility baseline, Gradle resolve graph và application vẫn khai báo dependency thực sự cần.

**Purpose:**

Dạy concept ổn định về dependency management mà không biến bài này thành một khóa đầy đủ về Gradle resolution.

## Khi override một managed version, bạn nhận thêm trách nhiệm gì?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:46–01:56`

**Visual:**

Highlight một managed version rồi animate project override sang version khác, đồng thời hiện warning cạnh compatibility baseline.

**Script:**

Managed không có nghĩa là project vĩnh viễn không được thay đổi version. Nhưng ngay khi override, một phần trách nhiệm tương thích chuyển về cho project.

**Purpose:**

Biến override version từ chi tiết cú pháp thành thay đổi ownership rõ ràng.

### Scene 4 — Override phải đi cùng evidence

**Time:** `01:56–02:37`

**Visual:**

Chia card override thành hai cơ chế: dependency-management plugin → BOM version property; native Gradle BOM → Gradle constraint/resolution rule. Thêm checklist về lý do, related libraries, compatibility, tests và điều kiện cuối `gỡ override khi Boot baseline đã bắt kịp`.

**Script:**

Override có thể hợp lý vì security fix hoặc feature bắt buộc, nhưng cả verification lẫn cơ chế override lúc này thuộc project. Với dependency-management plugin, version property của BOM là điểm tùy chỉnh tự nhiên. Với native BOM support của Gradle, các property kiểu Maven đó không điều khiển platform; phải dùng constraint hoặc resolution mechanism của Gradle. Hãy kiểm tra related libraries, assumption tích hợp của Boot và focused tests, đồng thời ghi rõ vì sao deviation tồn tại và điều kiện để gỡ nó khi một Boot baseline tương thích đã bắt kịp.

**Purpose:**

Biến managed-version override thành quyết định tương thích có đúng cơ chế, evidence kiểm chứng và điều kiện gỡ bỏ rõ ràng.
