---
video:
  url: ""
---

# Tạo Actuator endpoint tùy chỉnh

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

## Khi nào ứng dụng cần endpoint quản trị tùy chỉnh?

<!-- VIDEO_SECTION -->

### Scene 1 — Khi nào ứng dụng cần endpoint quản trị tùy chỉnh?

**Time:** `00:00–00:37`

**Visual:**

Hiện decision fork: built-in endpoint/health/metric/info contribution ở một nhánh, custom `@Endpoint` ở nhánh kia. Đặt bounded maintenance state và operator-only metadata phía custom; customer CRUD phía business API.

**Script:**

Custom Actuator endpoint dành cho một nhu cầu quản trị vận hành mà catalog có sẵn chưa biểu diễn. Internal state có giới hạn, maintenance action được kiểm soát hoặc deployment metadata có thể phù hợp. Customer workflow bình thường không trở thành management operation chỉ vì Actuator cũng trả JSON. Trước khi tạo endpoint mới, hãy kiểm tra health, metric, info hoặc built-in endpoint có thể mô tả nhu cầu đó chưa.

**Purpose:**

Giữ custom endpoint trong phạm vi operational purpose rõ ràng và tránh biến management surface thành nơi chứa business feature.

## @ReadOperation, @WriteOperation và @DeleteOperation định nghĩa operation như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:37–00:50`

**Visual:**

Giữ implementation `learning` ở trung tâm và chỉ đổi lớp contract bao quanh cho `@ReadOperation, @WriteOperation và @DeleteOperation định nghĩa operation như thế nào?`—operation inputs, Actuator mapping thật, transport specialization, extension hay business/API boundary.

**Script:**

Khi custom capability đã có lý do tồn tại, public contract của nó đến từ endpoint ID, operation type và input rõ ràng—không phải controller convention.

**Purpose:**

Từ quyết định cần custom management capability chuyển sang operation contract độc lập transport của capability đó.

### Scene 1 — @ReadOperation, @WriteOperation và @DeleteOperation định nghĩa operation như thế nào?

**Time:** `00:50–01:23`

**Visual:**

Mở `LearningOperationsEndpoint`, highlight `@Endpoint(id="learning")`, `@ReadOperation`, `@WriteOperation`. Thêm callout: root JSON property `enabled` map vào parameter, `@Nullable`, selector path segment và yêu cầu `-parameters` giữ tên method parameter.

**Script:**

`@Endpoint` khai báo management identity; `@ReadOperation`, `@WriteOperation`, `@DeleteOperation` khai báo intent. Method parameter trở thành input quản trị: web write map root JSON property vào từng parameter, `@Selector` có thể đưa input vào path, input optional dùng `@Nullable`. Tên parameter phải được giữ bằng `-parameters` để Actuator bind đúng. Trước khi gọi method, Boot còn thực hiện conversion qua application conversion infrastructure.

**Purpose:**

Narrate toàn bộ callout về operation input để Java signature được hiểu như management contract độc lập transport.

## Vì sao nên ưu tiên @Endpoint độc lập công nghệ khi có thể?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:23–01:36`

**Visual:**

Giữ implementation `learning` ở trung tâm và chỉ đổi lớp contract bao quanh cho `Vì sao nên ưu tiên @Endpoint độc lập công nghệ khi có thể?`—operation inputs, Actuator mapping thật, transport specialization, extension hay business/API boundary.

**Script:**

Operation annotation đã định nghĩa intent. Live endpoint cho thấy lợi ích: một implementation quản trị đứng độc lập với controller chỉ dùng cho mục đích demo.

**Purpose:**

Đưa operation contract vào endpoint `learning` thật để technology independence được chứng minh bằng runtime evidence.

### Scene 1 — Vì sao nên ưu tiên @Endpoint độc lập công nghệ khi có thể?

**Time:** `01:36–02:08`

**Visual:**

Chạy `GET /api/actuator-learning/custom-endpoint` để xem shared learning state, rồi gọi management surface thật `GET /actuator/learning`. Kích hoạt bounded write qua learning bridge và quan sát cùng state đổi trên management read; label MVC bridge là “demo only”. Thêm side card: generic `@Endpoint` → `Resource` → `application/octet-stream` + range support của MVC/WebFlux.

**Script:**

Nên ưu tiên generic `@Endpoint` khi capability không phụ thuộc HTTP hay JMX. `LearningOperationsEndpoint` chứng minh separation đó: Swagger bridge và mapping thật `/actuator/learning` cùng dùng một management implementation. Chỉ vì output là binary chưa đủ lý do chuyển sang `@WebEndpoint`: generic endpoint có thể trả `Resource`, Actuator phục vụ nó dưới `application/octet-stream`, và MVC/WebFlux hỗ trợ range request. Chỉ dùng endpoint riêng theo transport khi chính contract thật sự phụ thuộc transport đó.

**Purpose:**

Dùng Step 5 evidence để chứng minh technology-agnostic endpoint và phân biệt rõ demo bridge với management surface thật.

## Khi nào endpoint riêng cho Web hoặc JMX là phù hợp?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:08–02:21`

**Visual:**

Giữ implementation `learning` ở trung tâm và chỉ đổi lớp contract bao quanh cho `Khi nào endpoint riêng cho Web hoặc JMX là phù hợp?`—operation inputs, Actuator mapping thật, transport specialization, extension hay business/API boundary.

**Script:**

Generic endpoint đúng khi transport chỉ là chi tiết triển khai. Section tiếp theo nói về trường hợp ngược lại: transport thật sự là một phần requirement.

**Purpose:**

Đối chiếu generic endpoint với trường hợp HTTP hoặc JMX semantics thật sự là một phần requirement.

### Scene 1 — Khi nào endpoint riêng cho Web hoặc JMX là phù hợp?

**Time:** `02:21–02:53`

**Visual:**

Tách `@WebEndpoint` và `@JmxEndpoint` thành hai nhánh. Nhánh Web có status/content-type đặc thù; nhánh JMX có representation chỉ hợp MBean. Giữ generic `@Endpoint` làm lựa chọn mặc định.

**Script:**

Technology-specific endpoint phù hợp khi capability thật sự chỉ có nghĩa trên một transport. `@WebEndpoint` dành cho management feature phụ thuộc HTTP contract; `@JmxEndpoint` có thể dựa vào JMX representation. Ta đổi portability để lấy contract chuyên biệt. Nếu cần đầy đủ behavior của MVC/WebFlux request-response, dùng normal controller có thể trung thực hơn việc nhét framework-specific concern vào generic endpoint.

**Purpose:**

Nêu decision rule cho endpoint theo transport và tránh chọn chỉ vì developer quen viết HTTP controller.

## Khi nào nên mở rộng một endpoint có sẵn thay vì tạo endpoint mới?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:53–03:02`

**Visual:**

Giữ implementation `learning` ở trung tâm và chỉ đổi lớp contract bao quanh cho `Khi nào nên mở rộng một endpoint có sẵn thay vì tạo endpoint mới?`—operation inputs, Actuator mapping thật, transport specialization, extension hay business/API boundary.

**Script:**

Transport-specific behavior chưa chắc cần management identity mới. Nếu concept đã tồn tại, extension giúp giữ nguyên identity.

**Purpose:**

Cho thấy behavior theo transport có thể extend management identity hiện có thay vì ép tạo endpoint trùng ý nghĩa.

### Scene 1 — Khi nào nên mở rộng một endpoint có sẵn thay vì tạo endpoint mới?

**Time:** `03:02–03:34`

**Visual:**

Hiện built-in endpoint được bổ sung Web extension bằng `@EndpointWebExtension` và JMX extension bằng `@EndpointJmxExtension`. Endpoint identity giữ nguyên; một responsibility hoàn toàn mới được đánh dấu “create new endpoint”.

**Script:**

Khi operational concept đã thuộc built-in endpoint nhưng một transport cần thêm operation hoặc representation, nên extend endpoint đó thay vì tạo ID trùng ý nghĩa. Web/JMX extension giữ nguyên management identity và chỉ thêm behavior theo transport. Extension cũng nên nhỏ và được test với Boot version đang dùng vì coupling sâu vào implementation của built-in endpoint sẽ tăng upgrade cost.

**Purpose:**

Phân biệt endpoint extension với việc tạo endpoint mới, đồng thời làm rõ chi phí coupling/upgrades.

## Actuator endpoint tùy chỉnh tách khỏi API nghiệp vụ như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:34–03:45`

**Visual:**

Giữ implementation `learning` ở trung tâm và chỉ đổi lớp contract bao quanh cho `Actuator endpoint tùy chỉnh tách khỏi API nghiệp vụ như thế nào?`—operation inputs, Actuator mapping thật, transport specialization, extension hay business/API boundary.

**Script:**

Sau extensions, kiểm tra thiết kế cuối là ownership: cùng dùng HTTP không làm business contract và management contract trở thành một.

**Purpose:**

Khép endpoint design bằng cách tách operational contract khỏi business/product API dù cả hai đều có thể dùng HTTP.

### Scene 1 — Actuator endpoint tùy chỉnh tách khỏi API nghiệp vụ như thế nào?

**Time:** `03:45–04:26`

**Visual:**

Chia màn hình `/api/orders` và `/actuator/learning`. Label caller, contract purpose, versioning/security expectation và accidental-exposure impact. Đặt maintenance state phía management, order creation phía business.

**Script:**

Business API và management endpoint đều có thể dùng HTTP nhưng phục vụ hai contract khác nhau. Business API mô hình hóa hành vi sản phẩm cho client bình thường. Custom Actuator endpoint mô hình hóa operation, diagnosis hoặc maintenance cho audience vận hành. Đưa customer CRUD vào `@Endpoint` làm discovery, authorization, versioning mơ hồ; ngược lại, giấu maintenance action đặc quyền trong public controller cũng che mất risk. Hãy hỏi ai gọi, gọi để làm gì và nếu expose nhầm thì hậu quả gì.

**Purpose:**

Khôi phục cả hai phía của ranh giới business-versus-management để placement dựa trên contract ownership, không dựa trên cùng dùng HTTP.
