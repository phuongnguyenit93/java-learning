---
video:
  url: ""
---

# Health endpoint và các HealthContributor

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

## Health endpoint biểu diễn điều gì?

<!-- VIDEO_SECTION -->

### Scene 1 — Health endpoint biểu diễn điều gì?

**Time:** `00:00–00:36`

**Visual:**

Hiện `/actuator/health` trước với chỉ `status`, sau đó mở rộng cùng response thành component tree. Bên cạnh đặt consumer: load balancer, deployment platform, operator; thêm cảnh báo health không bảo đảm mọi transaction nghiệp vụ đều thành công.

**Script:**

Health endpoint tóm tắt bằng chứng vận hành của ứng dụng và các thành phần liên quan. Platform và operator dùng nó để biết service có đang usable hay không, nhưng `UP` không phải lời bảo đảm mọi giao dịch nghiệp vụ đều thành công. Boot tập hợp kết quả từ các health contributor đã đăng ký, rồi tùy cấu hình/authorization mà chỉ trả aggregate status hoặc mở thêm component và detail.

**Purpose:**

Định nghĩa đúng phạm vi của health và cho thấy cùng một model phục vụ probe lẫn chẩn đoán sâu hơn.

## HealthContributor và HealthIndicator liên hệ với nhau như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:36–00:47`

**Visual:**

Giữ health tree trên màn hình và chỉ biến đổi lớp liên quan đến `HealthContributor và HealthIndicator liên hệ với nhau như thế nào?`—leaf contributor, composite branch, aggregate status, response visibility hoặc live custom-indicator experiment.

**Script:**

Endpoint chỉ là aggregate view. Muốn hiểu aggregate đến từ đâu, ta cần nhìn các contributor tạo từng mảnh bằng chứng.

**Purpose:**

Từ aggregate health response đi xuống contributor contract tạo ra từng mảnh health evidence.

### Scene 1 — HealthContributor và HealthIndicator liên hệ với nhau như thế nào?

**Time:** `00:47–01:21`

**Visual:**

Mở `LearningDependencyHealthIndicator` cạnh cây health. Highlight `HealthContributor` ở cấp tree và `HealthIndicator.health()` ở leaf trả `Health.up()`/`Health.down()` cùng detail có giới hạn.

**Script:**

`HealthContributor` là contract chung để Actuator dựng health tree. `HealthIndicator` là leaf contributor, tính ra một `Health` gồm status và detail tùy chọn. Indicator tốt chỉ kiểm tra một điều kiện vận hành có giới hạn và báo bằng chứng; nó không nên tự repair component, restart process hay nhét toàn bộ monitoring policy vào một method. Reactive variant vẫn đi vào cùng mental model contributor tree.

**Purpose:**

Phân biệt contract của cả cây với phép check ở leaf và cố định nguyên tắc indicator phải bounded, side-effect free.

## Composite HealthContributor tạo cây health như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:21–01:32`

**Visual:**

Giữ health tree trên màn hình và chỉ biến đổi lớp liên quan đến `Composite HealthContributor tạo cây health như thế nào?`—leaf contributor, composite branch, aggregate status, response visibility hoặc live custom-indicator experiment.

**Script:**

Indicator trả lời câu hỏi cục bộ; composite là cách nhiều check trở thành một subsystem mà vẫn giữ khả năng drill down.

**Purpose:**

Cho thấy các leaf check độc lập trở thành hierarchy có ý nghĩa mà incident vẫn drill down được.

### Scene 1 — Composite HealthContributor tạo cây health như thế nào?

**Time:** `01:32–02:07`

**Visual:**

Vẽ composite node `payments` có các child `database`, `queue`, `remoteApi`. Collapse thành parent status rồi expand nested path khi component visibility cho phép.

**Script:**

`CompositeHealthContributor` nhóm nhiều contributor con dưới một node có ý nghĩa vận hành, nên health có thể là cây thay vì list phẳng. Điều này hữu ích khi câu hỏi incident là “phần nào của payments đang kéo health xuống?”. Không nên dựng cây sâu chỉ để phản chiếu package. Khi component được phép hiển thị, caller có thể drill down từ aggregate tới đúng nhánh lỗi.

**Purpose:**

Cho thấy composite giúp định vị nguyên nhân nhưng chỉ nên dùng khi hierarchy thật sự có ý nghĩa vận hành.

## Trạng thái health tổng thể được tổng hợp như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:07–02:20`

**Visual:**

Giữ health tree trên màn hình và chỉ biến đổi lớp liên quan đến `Trạng thái health tổng thể được tổng hợp như thế nào?`—leaf contributor, composite branch, aggregate status, response visibility hoặc live custom-indicator experiment.

**Script:**

Có health tree rồi vẫn cần một kết quả hướng ra ngoài. Aggregation quyết định status nào thắng, còn HTTP mapping quyết định caller thấy mã gì.

**Purpose:**

Thu health tree thành aggregate status và HTTP signal mà infrastructure thực sự tiêu thụ.

### Scene 1 — Trạng thái health tổng thể được tổng hợp như thế nào?

**Time:** `02:20–02:59`

**Visual:**

Cho leaf status đi vào `StatusAggregator`, rồi qua `HttpCodeStatusMapper`. Hiện `DOWN`/`OUT_OF_SERVICE` → 503, `UP`/`UNKNOWN` → 200. Thêm cảnh báo: cấu hình custom `management.endpoint.health.status.http-mapping.*` làm mất default 503 nếu không khai báo lại.

**Script:**

Actuator dùng `StatusAggregator` để kết hợp trạng thái, với thứ tự mặc định để trạng thái nghiêm trọng như `DOWN` và `OUT_OF_SERVICE` thắng `UP`. Khi expose qua HTTP, `HttpCodeStatusMapper` đổi aggregate status thành response code: mặc định `DOWN` và `OUT_OF_SERVICE` là 503, còn `UP` và `UNKNOWN` là 200. Một chi tiết dễ bỏ sót: chỉ cần khai báo custom health status mapping, các default 503 này không còn tự áp dụng; muốn giữ phải khai báo lại.

**Purpose:**

Nối status contributor tới tín hiệu HTTP mà platform thật sự tiêu thụ và giữ lại rule custom-mapping dễ gây lỗi.

## Nên công khai component và thông tin chi tiết của health như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:59–03:10`

**Visual:**

Giữ health tree trên màn hình và chỉ biến đổi lớp liên quan đến `Nên công khai component và thông tin chi tiết của health như thế nào?`—leaf contributor, composite branch, aggregate status, response visibility hoặc live custom-indicator experiment.

**Script:**

Sau khi biết aggregate result, câu hỏi tiếp theo là mỗi audience thực sự cần nhìn thấy bao nhiêu bằng chứng.

**Purpose:**

Tách outward status khỏi lượng component/detail evidence mà từng audience được phép nhìn thấy.

### Scene 1 — Nên công khai component và thông tin chi tiết của health như thế nào?

**Time:** `03:10–03:45`

**Visual:**

Hiện ba response từ cùng health tree: chỉ status, có component, và có full details. Label `show-components`, `show-details`, `when-authorized`, `management.endpoint.health.roles`; mask các detail giống secret.

**Script:**

Health status và mức chi tiết là hai quyết định riêng. `show-components` và `show-details` điều khiển lượng contributor data được trả, với default bảo thủ. `when-authorized` có thể dùng health roles đã cấu hình, nhưng authentication và role assignment vẫn thuộc security configuration của ứng dụng. Probe thường chỉ cần status; operator có thể cần component/detail. Credential, token hay exception dump lớn vẫn không nên đặt vào health detail.

**Purpose:**

Dạy least-detail exposure và tách rõ visibility rule của Actuator khỏi identity/role do Spring Security cung cấp.

## Khi nào nên bổ sung HealthIndicator tùy chỉnh?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:45–03:59`

**Visual:**

Giữ health tree trên màn hình và chỉ biến đổi lớp liên quan đến `Khi nào nên bổ sung HealthIndicator tùy chỉnh?`—leaf contributor, composite branch, aggregate status, response visibility hoặc live custom-indicator experiment.

**Script:**

Contributor model phát huy giá trị nhất khi Boot không thể tự biết một điều kiện domain-specific. Experiment thật cho thấy bằng chứng đó gia nhập health pipeline ra sao.

**Purpose:**

Đưa contributor model vào custom check thật để thấy application-specific evidence gia nhập standard health model như thế nào.

### Scene 1 — Khi nào nên bổ sung HealthIndicator tùy chỉnh?

**Time:** `03:59–04:43`

**Visual:**

Chạy experiment thật: `POST /api/actuator-learning/health/false`, highlight `LearningDependencyHealthIndicator.health()` trả `DOWN`, rồi gọi `/actuator/health/learningDependency` và aggregate `/actuator/health`; cuối cùng restore bằng `POST .../health/true`.

**Script:**

Custom indicator phù hợp khi ứng dụng có điều kiện vận hành thật mà Boot không tự suy ra. Ở đây learning API đổi một dependency state có kiểm soát; indicator đọc chính state đó và trả `DOWN`, còn component endpoint lẫn aggregate health đều phản ánh ngay. Điều cần quan sát không chỉ là custom code chạy, mà là bằng chứng riêng của ứng dụng đã đi vào health model chuẩn của Actuator. Check như vậy phải nhanh, bounded, không side effect và có timeout nếu đụng dependency từ xa.

**Purpose:**

Chứng minh custom-health model bằng Step 5 evidence của repository và nối observation runtime về nguyên tắc thiết kế indicator.
