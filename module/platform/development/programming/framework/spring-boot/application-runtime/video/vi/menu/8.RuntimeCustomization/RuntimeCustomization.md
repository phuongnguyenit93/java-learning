---
video:
  url: ""
---

# Ranh giới của tùy biến runtime

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

## Khi nào việc tùy biến runtime thực sự cần thiết?

<!-- VIDEO_SECTION -->

### Scene 1 — Khi nào việc tùy biến runtime thực sự cần thiết?

**Time:** `00:00–01:03`

**Visual:**

Progressive reveal trên visual của chương: bắt đầu từ runtime requirement cụ thể và đi property → supported Boot API → neighboring owner.

**Script:**

Trước khi thêm bootstrap code, hãy bắt đầu từ hành vi cụ thể như chế độ startup, đăng ký listener, hình dạng ứng dụng hoặc chính sách khởi tạo, thay vì từ nhu cầu chung chung "muốn tự kiểm soát Boot". Runtime customization chỉ nên xuất hiện khi hành vi ứng dụng cần khác giá trị mặc định được Boot hỗ trợ và không có bề mặt cấu hình đơn giản hơn để biểu diễn yêu cầu đó. Mỗi tùy biến bằng code đưa một phần hợp đồng runtime vào code ứng dụng. Điều đó đôi khi cần thiết nhưng khó phát hiện hơn property và có thể tương tác với auto-configuration hoặc quy ước framework. Trước khi tùy biến, hãy xác định bên sở hữu và giai đoạn lifecycle. Nhiều vấn đề tưởng là `SpringApplication` thực ra thuộc externalized configuration, auto-configuration, container, executor hoặc cấu hình web server.

**Purpose:**

Yêu cầu một runtime behavior cụ thể chưa được default/property hỗ trợ trước khi thêm programmatic customization.


## Vì sao nên ưu tiên configuration property được hỗ trợ trước khi tùy biến bằng code?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:03–01:16`

**Visual:**

Giữ customization decision tree và di chuyển một tầng từ property sang `SpringApplication`, builder, lifecycle hook hoặc neighboring owner.

**Script:**

Trước khi viết bootstrap code, kiểm tra Boot đã có property hỗ trợ hành vi đó chưa; property giữ runtime contract dễ phát hiện và đúng convention hơn.

**Purpose:**

Đặt checkpoint property-first trước khi programmatic customization trở thành phản xạ mặc định cho mọi runtime requirement.

### Scene 2 — Vì sao nên ưu tiên configuration property được hỗ trợ trước khi tùy biến bằng code?

**Time:** `01:16–02:09`

**Visual:**

Progressive reveal trên visual của chương: đặt property làm nhánh đầu, chỉ mở code khi không có supported property diễn đạt yêu cầu.

**Script:**

Tại checkpoint property-first, property hiện diện trong configuration metadata, tham gia mô hình externalized configuration và thường giữ đúng hành vi back-off/lifecycle mà Boot thiết kế. Nếu Boot đã có configuration property diễn tả đúng hành vi mong muốn, đó thường là lựa chọn đầu tiên. Tùy biến bằng code phù hợp khi bề mặt property không đủ hoặc ứng dụng phải lắp ráp `SpringApplication` trước khi context bình thường tồn tại. Không nên tự parse/bind property trong `main()` chỉ để cấu hình trông "tường minh" hơn. Thứ tự ưu tiên, profiles và cơ chế `@ConfigurationProperties` thuộc module `externalized-configuration`. Ở đây trọng tâm là chọn bề mặt runtime được hỗ trợ trước khi hạ xuống hook thấp hơn.

**Purpose:**

Ưu tiên Boot property dễ discover khi nó đã diễn đạt được yêu cầu và giữ lifecycle/back-off chuẩn.


## Có thể tùy biến điều gì qua `SpringApplication`?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:09–02:19`

**Visual:**

Giữ customization decision tree và di chuyển một tầng từ property sang `SpringApplication`, builder, lifecycle hook hoặc neighboring owner.

**Script:**

Nếu property không biểu diễn được yêu cầu bootstrap-time, tầng hỗ trợ kế tiếp là tạo `SpringApplication` tường minh với customization hẹp.

**Purpose:**

Chỉ nâng từ property lên `SpringApplication` khi bootstrap behavior không thể diễn đạt bằng supported property surface.

### Scene 3 — Có thể tùy biến điều gì qua `SpringApplication`?

**Time:** `02:19–02:57`

**Visual:**

Progressive reveal trên visual của chương: hiện snippet `SpringApplication` nhỏ với một setting/listener/initializer và không có business config.

**Script:**

Nếu supported property không diễn đạt được bootstrap-time requirement, hãy tạo `SpringApplication` tường minh thay vì chỉ dùng static shortcut. Giữ code hẹp: đặt banner mode, thêm early listener/initializer, chọn application type, cấu hình lazy initialization rồi gọi `run(args)`. Nếu `main()` bắt đầu đăng ký business service hoặc dựng lại container configuration, customization đã vượt sang Spring configuration/auto-configuration và nên chuyển về owner đó.

**Purpose:**

Cho thấy các bootstrap setting hẹp hợp lý khi tạo `SpringApplication` trực tiếp mà không biến `main()` thành nơi cấu hình toàn ứng dụng.


## Khi nào `SpringApplicationBuilder` hữu ích?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:57–03:07`

**Visual:**

Giữ customization decision tree và di chuyển một tầng từ property sang `SpringApplication`, builder, lifecycle hook hoặc neighboring owner.

**Script:**

Khi customization chuyển thành composition, đặc biệt parent/child context, builder mới mang lại giá trị ngoài shortcut `run` thông thường.

**Purpose:**

Chỉ giới thiệu builder khi composition hoặc context hierarchy tạo requirement thật ngoài direct `SpringApplication` customization.

### Scene 4 — Khi nào `SpringApplicationBuilder` hữu ích?

**Time:** `03:07–04:04`

**Visual:**

Progressive reveal trên visual của chương: vẽ parent/child context từ `SpringApplicationBuilder`, shared Environment và constraint web component.

**Script:**

Với context composition, trường hợp sử dụng nổi bật là tạo phân cấp `ApplicationContext` theo quan hệ parent/child; ngoài ra builder cũng giúp ghép các tùy chọn bootstrap theo phong cách fluent. `SpringApplicationBuilder` cung cấp fluent API để cấu hình và chạy `SpringApplication`. Hệ phân cấp này kéo theo ràng buộc: parent/child dùng chung `Environment`, và thành phần web phải nằm ở child context theo giới hạn mà Boot mô tả. Vì vậy hệ phân cấp là một lựa chọn kiến trúc chứ không phải cách viết đẹp hơn cho một context thông thường. Chỉ dùng builder khi hệ phân cấp hoặc cách lắp ráp fluent giải quyết yêu cầu thật sự. Với ứng dụng một context thông thường, static `run` hoặc một `SpringApplication` tùy biến nhỏ thường dễ hiểu hơn.

**Purpose:**

Chỉ dùng `SpringApplicationBuilder` cho fluent composition hoặc parent/child hierarchy thật sự, cùng các constraint của hierarchy.


## Chọn extension point runtime theo thời điểm lifecycle như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:04–04:14`

**Visual:**

Giữ customization decision tree và di chuyển một tầng từ property sang `SpringApplication`, builder, lifecycle hook hoặc neighboring owner.

**Script:**

Lúc này có nhiều extension point, nên chọn theo lifecycle prerequisite và intent thay vì chọn hook chạy sớm nhất.

**Purpose:**

Biến danh sách hook ngày càng dài thành decision theo lifecycle timing thay vì thêm một menu API.

### Scene 5 — Chọn extension point runtime theo thời điểm lifecycle như thế nào?

**Time:** `04:14–04:52`

**Visual:**

Progressive reveal trên visual của chương: hiện lifecycle selection table: event, early listener, runner, managed executor/scheduler, property/Application/builder.

**Script:**

Chọn hook muộn nhất nhưng vẫn đáp ứng yêu cầu. Quan sát lifecycle transition bằng application listener; nếu event xảy ra trước bean thì đăng ký listener sớm. Startup work bắt buộc và cần normal bean thuộc `ApplicationRunner`/`CommandLineRunner`. Work chạy lâu dài thuộc managed executor/scheduler. Supported bootstrap setting nên qua property trước, rồi tới `SpringApplication`/builder nếu thật sự cần code. Sớm hơn không tốt hơn: phase càng sớm càng ít guarantee và dependency càng khó biểu đạt.

**Purpose:**

Chọn latest safe extension point dựa trên prerequisite và intent thay vì cố chạy code càng sớm càng tốt.


## Tùy biến nào thực ra thuộc configuration, auto-configuration, container hoặc web runtime thay vì module này?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:52–05:04`

**Visual:**

Giữ customization decision tree và di chuyển một tầng từ property sang `SpringApplication`, builder, lifecycle hook hoặc neighboring owner.

**Script:**

Decision tree chỉ hoàn chỉnh khi biết lúc nào rời module này; kết thúc bằng việc bàn giao configuration, auto-configuration, container và web concern cho owner chính.

**Purpose:**

Khép customization tại ownership boundary để concern configuration, web, container hoặc build không tích tụ vào đây.

### Scene 6 — Tùy biến nào thực ra thuộc configuration, auto-configuration, container hoặc web runtime thay vì module này?

**Time:** `05:04–05:43`

**Visual:**

Progressive reveal trên visual của chương: route values/profiles, conditional bean, container internals, web runtime, diagnostics và build behavior sang owner chính.

**Script:**

Tại ownership boundary, giá trị cấu hình/profile thuộc `externalized-configuration`. Một tùy biến có thể thực hiện về mặt kỹ thuật ở nhiều lớp, nhưng quyền sở hữu quyết định nơi nào dễ hiểu và bảo trì. Tạo bean theo điều kiện, back-off và các giá trị mặc định dùng lại thuộc `auto-configuration`. Lifecycle bean và phần nội bộ context tổng quát thuộc Spring Framework. Web-server factory, server property, connector, TLS consumption, forwarded header và graceful shutdown thuộc `web-runtime`. Chẩn đoán production thuộc Actuator.

**Purpose:**

Giữ application-runtime gọn bằng cách chuyển configuration, conditional bean, container internals, web runtime, diagnostics và build behavior sang owner chính.
