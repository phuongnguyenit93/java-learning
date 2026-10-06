---
video:
  url: ""
---

# Tổng hợp Spring Boot Fundamentals

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

## Một ứng dụng Boot đơn giản kết nối end-to-end như thế nào?

<!-- VIDEO_SECTION -->

### Scene 1 — Ghép toàn bộ Fundamentals thành một chuỗi

**Time:** `00:00–01:00`

**Visual:**

Animate một flow lớn: `starter/dependency choices → classpath → primary @SpringBootApplication class → Java main → SpringApplication.run → prepare environment/context → ApplicationContext refresh → application shape runs → orderly shutdown`. Đặt DevTools thành vòng ngoài quanh development loop và packaging thành mũi tên từ build sang executable artifact.

**Script:**

Sau các chương riêng lẻ, một ứng dụng Boot cơ bản có thể được giải thích end-to-end. Dependency và starter tạo nên classpath sẵn có. Primary `@SpringBootApplication` class cung cấp cấu hình và các entry point cho scan cùng auto-configuration. JVM gọi `main`, `main` giao bootstrap cho `SpringApplication`, context được chuẩn bị và refresh, rồi dạng ứng dụng bắt đầu vai trò runtime của nó. Khi dừng có trật tự, context được đóng. DevTools chỉ rút ngắn vòng phản hồi khi phát triển; packaging chỉ thay cách ứng dụng và dependency được bàn giao và khởi động.

**Purpose:**

Tổng hợp các chương trước thành một mental model liên tục từ dependency tới runtime và shutdown.

## Classpath, đầu vào cấu hình và quy ước của Boot liên hệ với nhau ra sao?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:00–01:10`

**Visual:**

Từ flow end-to-end, zoom vào giai đoạn `SpringApplication / ApplicationContext` và đặt ba input ở cạnh trái.

**Script:**

Trong luồng lớn này, phần dễ nhầm nhất là những yếu tố nào là đầu vào và phần nào là cơ chế của Boot phản ứng với chúng.

**Purpose:**

Chuyển từ end-to-end flow sang quan hệ chính xác giữa classpath, configuration, application beans và Boot conventions.

### Scene 2 — Ba tín hiệu, một quá trình xây context

**Time:** `01:10–02:10`

**Visual:**

Hiện ba hộp `classpath`, `configuration inputs`, `application bean/bean definitions` đi vào vùng `SpringApplication bootstrap / ApplicationContext preparation + refresh`. Bên trong vùng đó hiện `Boot conventions / auto-configuration processed as configuration`, rồi đi ra `resulting ApplicationContext + integrations`.

**Script:**

Classpath cho biết khả năng nào đang sẵn có. Đầu vào cấu hình cung cấp giá trị và lựa chọn cho lần chạy. Bean hoặc bean definition của ứng dụng cho biết những gì ứng dụng đã chủ động cung cấp. Quy ước Boot và auto-configuration không phải một đầu vào thứ tư; chúng là cơ chế cấu hình phản ứng với các tín hiệu đó trong lúc `SpringApplication` chuẩn bị và refresh context. Thêm web starter đổi classpath, đặt property đổi đầu vào cấu hình, còn tự định nghĩa bean có thể làm auto-configuration đóng góp khác đi hoặc back off.

**Purpose:**

Khóa lại causal model trung tâm của Boot và ngăn learner biến auto-configuration thành một bước độc lập chạy trước bootstrap.

## Những hiểu lầm nào về Spring Boot cần tránh?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:10–02:20`

**Visual:**

Biến sơ đồ thành sáu thẻ misconception, mỗi thẻ hiện dấu X rồi lật sang correction.

**Script:**

Một mô hình tư duy tốt cũng phải giúp phát hiện câu nói sai. Ta sẽ dùng sáu hiểu lầm phổ biến như bài kiểm tra nhanh cho toàn bộ Fundamentals.

**Purpose:**

Chuyển từ synthesis model sang kiểm tra các điểm learner dễ trộn lẫn.

### Scene 3 — Sáu misconception và correction ngắn gọn

**Time:** `02:20–03:35`

**Visual:**

Lần lượt hiện sáu cặp: `Boot replaces Spring → Boot builds on Spring`; `starter = auto-config → dependency vs configuration`; `every Boot app is web → non-web/Servlet/reactive`; `convention cannot be overridden → supported override points`; `DevTools is production runtime feature → development feedback`; `executable packaging replaces logical main flow → archive Main-Class is Boot launcher, Start-Class points to app, then app main → SpringApplication`.

**Script:**

Thứ nhất, Boot không thay Spring Framework; nó xây trên Spring container. Thứ hai, starter không phải auto-configuration: một bên định hình dependency, một bên đóng góp configuration theo condition. Thứ ba, Boot application không nhất thiết là web. Thứ tư, convention không có nghĩa không thể override. Thứ năm, DevTools là công cụ phản hồi cho quá trình phát triển, không phải khả năng runtime cho môi trường vận hành thực tế. Và thứ sáu, executable packaging chỉ đổi entry point ở cấp archive: Boot launcher là `Main-Class`, application class được chỉ qua `Start-Class`, rồi application `main` vẫn gọi `SpringApplication` như mô hình tư duy đã học.

**Purpose:**

Dùng misconception như bài kiểm tra tích hợp để xác nhận learner phân biệt đúng framework, dependency, runtime shape, convention, DevTools và packaging.

## Module nào chịu trách nhiệm cho từng mảng Spring Boot chuyên sâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:35–03:45`

**Visual:**

Sáu misconception thu nhỏ thành bản đồ Spring Boot modules với `fundamentals` ở trung tâm.

**Script:**

Khi các khái niệm đã không còn lẫn nhau, ta có thể dùng loại câu hỏi đang gặp để chọn đúng module chuyên sâu tiếp theo.

**Purpose:**

Chuyển từ correction nội bộ sang bản đồ ownership của toàn area Spring Boot.

### Scene 4 — Bản đồ owner theo loại câu hỏi

**Time:** `03:45–04:50`

**Visual:**

Hiện bảng mapping: `Config Data/precedence/profiles/binding → externalized-configuration`; `conditions/back-off/exclusions/custom auto-config/starter → auto-configuration`; `detailed lifecycle/runners/availability/logging/runtime integrations → application-runtime`; `web detection/server/TLS/proxy/graceful shutdown → web-runtime`; `plugins/executable internals/layers/images/Buildpacks → build-tooling-packaging`; `health/metrics/loggers/endpoints → actuator`; `Boot test/slices/Testcontainers service connections → testing`; `AOT/GraalVM native → native-image`. Dưới cùng đặt `Spring Framework owns bean/DI, MVC/WebFlux request processing, TestContext`.

**Script:**

Câu hỏi về property source, profile hay binding đi sang externalized-configuration. Câu hỏi về condition, back-off và custom auto-configuration đi sang auto-configuration. Lifecycle chi tiết, runner, availability và logging thuộc application-runtime. Nhận diện web application và cơ chế server thuộc web-runtime. Build plugin, archive internals, layers và việc tạo image thuộc build-tooling-packaging. Vận hành runtime qua endpoint thuộc Actuator; kiểm thử Boot thuộc testing; AOT và native image thuộc native-image. Các cơ chế container, xử lý request của MVC/WebFlux hay TestContext nền tảng vẫn do Spring Framework sở hữu.

**Purpose:**

Biến curriculum boundary thành một decision map để learner chọn đúng nguồn học tiếp theo.

## Nên học gì sau Fundamentals?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:50–05:00`

**Visual:**

Bản đồ owner chuyển thành một learning path tuyến tính bắt đầu từ `fundamentals`.

**Script:**

Bản đồ trách nhiệm cho biết học ở đâu; lộ trình học cho biết nên đi theo thứ tự nào để mỗi cơ chế mới có đủ nền tảng từ cơ chế trước.

**Purpose:**

Chuyển từ bản đồ module ngang sang thứ tự học đề xuất sau Fundamentals.

### Scene 5 — Handoff sang decision inputs của Boot

**Time:** `05:00–05:55`

**Visual:**

Animate path `fundamentals → externalized-configuration → auto-configuration → application-runtime → web-runtime`. Bên cạnh, đặt `build-tooling-packaging`, `actuator`, `testing` như các nhánh hỗ trợ có thể học sau foundation; `native-image` ở cuối với nhãn `AOT/closed-world after normal JVM model`.

**Script:**

Bước tiếp theo hợp lý là externalized-configuration để làm rõ configuration source, precedence, profile và binding. Sau đó học auto-configuration để hiểu condition, back-off và cách Boot phản ứng với classpath, configuration cùng context. Từ đó đi vào application-runtime rồi web-runtime khi cần. Build/packaging, Actuator và testing mở rộng việc bàn giao, vận hành và kiểm chứng. Native image nên để sau khi mô hình JVM Boot bình thường đã vững. Nếu bạn giải thích được vì sao `main` gọi `SpringApplication`, `@SpringBootApplication` đóng góp gì, starter thay classpath ra sao, vì sao ứng dụng Boot có thể web hoặc non-web và câu hỏi sâu hơn thuộc module nào, Fundamentals đã hoàn thành vai trò của nó.

**Purpose:**

Kết thúc module bằng learning path cụ thể và một readiness test có thể tự kiểm tra trước khi chuyển sang Spring Boot chuyên sâu.
