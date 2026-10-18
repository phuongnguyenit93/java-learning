---
video:
  url: ""
---

# Hiểu và tùy chỉnh test auto-configuration

<!--
VIDEO SCRIPT FORMAT
- Each H2 maps 1:1 to the current Knowledge H2 order.
- The first section contains a Scene; later sections contain a conceptual Transition and a Scene.
- Timing is estimated from spoken density and must remain sequential.
-->

## Vì sao Boot cung cấp auto-configuration riêng cho test?

<!-- VIDEO_SECTION -->

### Scene 1 — Vì sao Boot cung cấp auto-configuration riêng cho test?

**Time:** `00:00–01:02`

**Visual:**

Contrast production auto-configuration with test-only facilities such as mock clients, test database replacement, JSON testers, and other support that should exist only in test contexts.

**Script:**

Production auto-configuration được thiết kế để lắp ráp application đang chạy. Test thường cần một tập bean hỗ trợ khác như mock client, embedded test hạ tầng, cơ chế thay test database, JSON tester hoặc các tiện ích không nên trở thành component của quá trình khởi động production. Boot test auto-configuration chỉ cung cấp các cơ chế hỗ trợ này trong test context và cho phép slice annotation import tập đã được chọn lọc. Nhờ đó test hạ tầng vẫn được cấu hình theo kiểu khai báo mà không bị coi là application configuration thường.

**Purpose:**

Giải thích vì sao Boot cần test-specific auto-configuration: focused test vẫn cần đúng hạ tầng nhưng không nên load toàn bộ production auto-configuration graph.

## Các slice annotation chọn tập auto-configuration được import như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:02–01:17`

**Visual:**

Giữ configuration graph hiện tại và chỉ thay input/import kế tiếp: expand a slice annotation into its curated auto-configuration import list and keep that list visually separate from ordinary application component scanning.

**Script:**

Focused context cần hạ tầng bên cạnh filtered application bean, nên hãy xem mỗi slice chọn chính xác auto-configuration set nào để import.

**Purpose:**

Chuyển từ lý do tồn tại của test auto-configuration sang curated import set mà từng slice thực sự nhận.

### Scene 1 — Các slice annotation chọn tập auto-configuration được import như thế nào?

**Time:** `01:17–02:10`

**Visual:**

Mở slice annotation thành curated auto-configuration import list và giữ list đó tách trực quan khỏi application component scanning thông thường.

**Script:**

Mỗi Boot slice gắn với một tập auto-configuration import xác định. Danh sách theo mục tiêu: web slice import hỗ trợ kiểm thử web, data slice import hỗ trợ hướng tới persistence và các slice khác chọn hạ tầng đúng với công nghệ của chúng. Khi cần biết chính xác tập import, hãy xem test-slice appendix của Boot. Đừng giả định một bean phải tồn tại chỉ vì production auto-configuration tương ứng có trong application.

**Purpose:**

Cho thấy slice annotation chọn auto-configuration import như thế nào để missing/unexpected infrastructure được truy về slice definition thay vì đoán.

## Các annotation `@AutoConfigure...` bổ sung hoặc tinh chỉnh cơ chế hỗ trợ test như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:10–02:25`

**Visual:**

Giữ configuration graph hiện tại và chỉ thay input/import kế tiếp: attach an `@AutoConfigure...` block to an existing slice and show it adding or tuning one test facility without widening the main component boundary.

**Script:**

Imported set tạo baseline; từ đó `@AutoConfigure...` annotation mới thêm hoặc tinh chỉnh facility mà không bỏ boundary ban đầu.

**Purpose:**

Cho thấy focused context có thể nhận thêm một test facility mà không đổi primary component boundary.

### Scene 1 — Các annotation `@AutoConfigure...` bổ sung hoặc tinh chỉnh cơ chế hỗ trợ test như thế nào?

**Time:** `02:25–03:20`

**Visual:**

Gắn một khối `@AutoConfigure...` vào slice hiện tại và cho thấy nó bổ sung hoặc tinh chỉnh đúng một test facility mà không mở rộng component boundary chính.

**Script:**

Annotation như `@AutoConfigureMockMvc`, `@AutoConfigureWebTestClient` và `@AutoConfigureTestDatabase` thêm hoặc tinh chỉnh một cơ chế hỗ trợ kiểm thử tập trung quanh test context hiện tại. Chúng hữu ích khi lựa chọn context chính đã đúng nhưng một khả năng hỗ trợ cần được cấu hình rõ. Đây không phải là chọn một slice khác. Annotation chính quyết định ranh giới context; `@AutoConfigure...` tinh chỉnh cơ chế hỗ trợ bên trong ranh giới đó.

**Purpose:**

Làm rõ `@AutoConfigure...` annotation thêm hoặc tinh chỉnh test facility mà không thay đổi primary test boundary.

## Loại một auto-configuration khỏi Boot test như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:20–03:35`

**Visual:**

Giữ configuration graph hiện tại và chỉ thay input/import kế tiếp: show the slice import list with one auto-configuration crossed out by exclusion while the rest of the focused context remains intact.

**Script:**

Có thêm facility thì cũng có chiều ngược lại: exclusion loại auto-configuration hợp lệ chung nhưng không phù hợp scenario này.

**Purpose:**

Đối chiếu việc thêm test facility với việc chủ động loại một auto-configuration không phù hợp khỏi cùng focused context.

### Scene 1 — Loại một auto-configuration khỏi Boot test như thế nào?

**Time:** `03:35–04:35`

**Visual:**

Hiện slice import list với một auto-configuration bị gạch do exclusion, còn phần focused context khác giữ nguyên.

**Script:**

Khi một auto-configuration không đúng với kịch bản test, annotation test và cơ chế điều khiển auto-configuration của Boot cho phép loại trừ rõ ràng. Việc loại trừ nên nhắm tới một configuration đã biết gây ra hạ tầng không mong muốn hoặc xung đột. Trước khi loại trừ, hãy kiểm tra tại sao configuration đó thỏa điều kiện. Dependency bị thiếu, property sai hoặc hiểu sai ranh giới slice thường là nguyên nhân gốc cần sửa thay vì loại vĩnh viễn một auto-configuration hữu ích.

**Purpose:**

Dạy cách exclude auto-configuration như một công cụ chính xác khi behavior của cấu hình đó làm sai scenario cần kiểm thử.

## Khi nào nên dùng `@ImportAutoConfiguration` để bổ sung hạ tầng test?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:35–04:50`

**Visual:**

Giữ configuration graph hiện tại và chỉ thay input/import kế tiếp: show `@ImportAutoConfiguration` adding one selected Boot auto-configuration to an otherwise stable focused context.

**Script:**

Exclusion làm baseline hẹp hơn, còn `@ImportAutoConfiguration` xử lý trường hợp focused boundary đúng nhưng cần thêm một Boot infrastructure piece cụ thể.

**Purpose:**

Chuyển từ exclusion sang selective addition khi slice boundary đúng nhưng cố ý thiếu một Boot infrastructure piece.

### Scene 1 — Khi nào nên dùng `@ImportAutoConfiguration` để bổ sung hạ tầng test?

**Time:** `04:50–05:38`

**Visual:**

Cho thấy `@ImportAutoConfiguration` thêm đúng một Boot auto-configuration được chọn vào focused context vốn đang ổn định.

**Script:**

Chọn `@ImportAutoConfiguration` khi test tập trung cần một auto-configuration cụ thể chưa nằm trong tập mặc định của slice. Boot xử lý auto-configuration import theo cơ chế riêng, bao gồm mô hình condition và order. Không nên dùng `@Import` thường để import class auto-configuration như user configuration. `@Import` vẫn đúng cho class application/test configuration thường.

**Purpose:**

Đặt `@ImportAutoConfiguration` đúng vai trò: thêm một infrastructure piece hẹp khi base slice đã đúng nhưng cố ý thiếu supporting auto-configuration.

## Chẩn đoán bean bị thiếu trong test context tập trung như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:38–05:53`

**Visual:**

Giữ configuration graph hiện tại và chỉ thay input/import kế tiếp: use a diagnostic funnel: component filtered out? → test auto-config not imported? → condition failed? → explicitly excluded? Stop at the first cause that explains the missing bean.

**Script:**

Khi import và exclusion đã rõ, missing bean có thể được chẩn đoán có hệ thống thay vì xem mọi absence là application defect.

**Purpose:**

Biến import/exclusion model tường minh thành thứ tự chẩn đoán missing bean có thể làm theo.

### Scene 1 — Chẩn đoán bean bị thiếu trong test context tập trung như thế nào?

**Time:** `05:53–06:57`

**Visual:**

Dùng diagnostic funnel: component bị filter? → test auto-config chưa import? → condition fail? → bị exclude tường minh? Dừng ở nguyên nhân đầu tiên giải thích được missing bean.

**Script:**

Trước hết xác định ranh giới test đã chọn. Hỏi type bị thiếu có đáng lẽ được quy tắc scanning của slice chọn, được auto-configuration đã import tạo hay cần cung cấp tường minh như một đối tượng cộng tác. Sau đó mới kiểm tra condition và exclusion của auto-configuration nếu bean đáng lẽ được tạo tự động. Thứ tự này tránh “sửa” slice bằng cách import production layer không liên quan. Ví dụ service bị thiếu trong `@WebMvcTest` thường là hành vi được mong đợi và nên được cung cấp như một đối tượng cộng tác tập trung.

**Purpose:**

Đưa ra thứ tự chẩn đoán missing bean: component bị filter, test auto-configuration không được import, condition không match hay explicit exclusion.

## Test auto-configuration bàn giao sang mô hình auto-configuration tổng quát của Boot ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:57–07:11`

**Visual:**

Giữ các ownership lane và chuyển trách nhiệm kế tiếp qua đúng boundary: use ownership swimlanes for Boot testing, Spring TestContext, JUnit/Mockito, web/data frameworks, and Testcontainers; move each responsibility into the lane that actually implements it.

**Script:**

Chuỗi chẩn đoán cuối cùng đi tới condition/import machinery thông thường của Boot, nơi test-specific configuration bàn giao sang general auto-configuration.

**Purpose:**

Khép cơ chế hiện tại bằng cách bàn giao trách nhiệm kế tiếp cho đúng framework hoặc library sở hữu nó.

### Scene 1 — Test auto-configuration bàn giao sang mô hình auto-configuration tổng quát của Boot ở đâu?

**Time:** `07:11–07:59`

**Visual:**

Dùng ownership swimlane cho Boot testing, Spring TestContext, JUnit/Mockito, web/data framework và Testcontainers; chuyển từng responsibility vào đúng lane thực sự triển khai nó.

**Script:**

Module này sở hữu cách Boot test annotations chọn, thêm hoặc exclude auto-configuration trong test context. Quy tắc tổng quát của `@AutoConfiguration`, conditions, ordering, back-off, exclusion và condition diagnostics thuộc Spring Boot auto-configuration module. Khi lỗi phụ thuộc vào lý do một condition thỏa hoặc thứ tự giữa nhiều auto-configuration, hãy tiếp tục ở mô hình auto-configuration tổng quát thay vì lặp lại tại đây.

**Purpose:**

Đánh dấu handoff từ test-specific auto-configuration sang general condition/import model của Boot để phần chẩn đoán sâu đi đúng subsystem.