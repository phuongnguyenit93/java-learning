---
video:
  url: ""
---

# Bật/tắt, tính khả dụng và exposure của endpoint

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

## Bật hoặc tắt một endpoint thay đổi điều gì?

<!-- VIDEO_SECTION -->

### Scene 1 — Bật hoặc tắt một endpoint thay đổi điều gì?

**Time:** `00:00–00:33`

**Visual:**

Dùng một endpoint card có hai trạng thái. Khi enabled, bean/capability tồn tại; khi disabled, xóa card khỏi application context. Hiện `management.endpoint.<id>.enabled`, `management.endpoints.enabled-by-default` và đánh dấu `shutdown` mặc định disabled.

**Script:**

Enablement trả lời capability quản trị có tồn tại trong ứng dụng hay không. Phần lớn endpoint tích hợp sẵn được bật mặc định, còn chức năng tác động mạnh như shutdown bị tắt mặc định. Disable endpoint mạnh hơn việc giấu URL: Boot loại endpoint đó khỏi application context, nên code và auto-configuration cũng không thể trông chờ bean tương ứng còn tồn tại.

**Purpose:**

Định nghĩa enablement là quyết định về sự tồn tại của capability, không phải tên khác của HTTP visibility.

## Exposure khác việc bật endpoint như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:33–00:44`

**Visual:**

Giữ endpoint capability card ở giữa và chỉ animate gate tương ứng với `Exposure khác việc bật endpoint như thế nào?`—enablement, exposure, availability, HTTP/JMX adapter, base path hoặc risk control—để mỗi section thêm một điều kiện vào cùng mô hình.

**Script:**

Capability enabled vẫn chưa tạo remote surface. Bước tiếp theo mới là quyết định transport nào, nếu có, được phép publish nó.

**Purpose:**

Cho thấy capability tồn tại vẫn cần quyết định exposure riêng trước khi remote client có thể truy cập.

### Scene 1 — Exposure khác việc bật endpoint như thế nào?

**Time:** `00:44–01:16`

**Visual:**

Giữ endpoint enabled, thêm hai cổng Web và JMX điều khiển bằng include/exclude. Chỉ `health` đi qua mặc định; đặt exclude đứng trước include để minh họa precedence.

**Script:**

Exposure trả lời câu hỏi khác: endpoint đã bật có thể được truy cập qua management technology nào? Web và JMX có include/exclude riêng, trong đó exclude thắng include. Ở Boot 3.3 chỉ `health` được expose mặc định qua các kênh này. Vì thế một endpoint hoàn toàn có thể đang enabled nhưng cố ý không có route HTTP hay representation JMX.

**Purpose:**

Tách capability tồn tại khỏi khả năng truy cập từ xa và ghi nhớ posture expose tối thiểu mặc định.

## Khi nào endpoint thực sự khả dụng qua một công nghệ quản trị?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:16–01:28`

**Visual:**

Giữ endpoint capability card ở giữa và chỉ animate gate tương ứng với `Khi nào endpoint thực sự khả dụng qua một công nghệ quản trị?`—enablement, exposure, availability, HTTP/JMX adapter, base path hoặc risk control—để mỗi section thêm một điều kiện vào cùng mô hình.

**Script:**

Enablement và exposure là hai gate riêng; ghép chúng lại mới giải thích được lúc nào capability thực sự có mặt trên một transport.

**Purpose:**

Ghép enablement và exposure thành các điều kiện availability giải thích vì sao endpoint vẫn có thể không xuất hiện.

### Scene 1 — Khi nào endpoint thực sự khả dụng qua một công nghệ quản trị?

**Time:** `01:28–02:03`

**Visual:**

Dựng AND-gate: enabled + selected for exposure + transport infrastructure available → endpoint available. Tắt từng input và cho route/MBean biến mất.

**Script:**

Khả dụng qua một management technology là kết quả của nhiều điều kiện, không phải một property duy nhất. Endpoint phải enabled, được chọn cho exposure đó và có infrastructure tương ứng. Auto-configuration của endpoint dùng availability model này, nên endpoint disabled hoặc không expose có thể không tạo management component mà ta mong đợi. Khi endpoint “biến mất”, kiểm tra các gate này trước khi đổ lỗi cho routing.

**Purpose:**

Biến endpoint availability thành chuỗi chẩn đoán cụ thể để không coi mọi 404 đều là lỗi path.

## Endpoint được expose qua HTTP như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:03–02:12`

**Visual:**

Giữ endpoint capability card ở giữa và chỉ animate gate tương ứng với `Endpoint được expose qua HTTP như thế nào?`—enablement, exposure, availability, HTTP/JMX adapter, base path hoặc risk control—để mỗi section thêm một điều kiện vào cùng mô hình.

**Script:**

Availability model còn trừu tượng. HTTP là adapter cụ thể đầu tiên biến operation thành route web.

**Purpose:**

Áp dụng availability gate trừu tượng vào HTTP để thấy chính xác web routing đi vào model ở đâu.

### Scene 1 — Endpoint được expose qua HTTP như thế nào?

**Time:** `02:12–02:41`

**Visual:**

Cho một endpoint đi qua WebEndpoint infrastructure rồi tách ra MVC, WebFlux hoặc Jersey. Thêm rule “MVC được dùng khi MVC + Jersey cùng có mặt” và property include chọn endpoint.

**Script:**

Với web exposure, Actuator adapt operation sang web stack đang hoạt động. Spring MVC, WebFlux hoặc Jersey có thể cung cấp bề mặt HTTP; khi Jersey và MVC cùng có thì MVC được dùng. Endpoint vẫn đến từ cùng core model—web stack chỉ là adapter biến selector và operation thành route và HTTP method.

**Purpose:**

Chỉ rõ nơi HTTP routing xuất hiện mà không biến endpoint abstraction thành controller.

## Endpoint được expose qua JMX như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:41–02:49`

**Visual:**

Giữ endpoint capability card ở giữa và chỉ animate gate tương ứng với `Endpoint được expose qua JMX như thế nào?`—enablement, exposure, availability, HTTP/JMX adapter, base path hoặc risk control—để mỗi section thêm một điều kiện vào cùng mô hình.

**Script:**

Sau HTTP, JMX làm điểm technology-agnostic trở nên rõ nhất: representation đổi nhưng identity không đổi.

**Purpose:**

Đối chiếu HTTP với JMX để chứng minh representation có thể đổi trong khi endpoint identity giữ nguyên.

### Scene 1 — Endpoint được expose qua JMX như thế nào?

**Time:** `02:49–03:15`

**Visual:**

Bật `spring.jmx.enabled` từ false sang true, sau đó hiện cùng endpoint ID dưới JMX domain `org.springframework.boot` với MBean operation. Giữ route web bên cạnh để so sánh.

**Script:**

JMX là cách biểu diễn khác của cùng management capability. Spring JMX mặc định bị tắt và có thể bật bằng `spring.jmx.enabled=true`; khi đó endpoint được expose đi qua JMX infrastructure thay vì HTTP routing. Transport thay đổi, nhưng endpoint ID và management intent vẫn giữ nguyên.

**Purpose:**

Đối chiếu JMX với HTTP để người học thấy hai transport trên một endpoint model chứ không phải hai hệ thống endpoint khác nhau.

## Base path web mặc định /actuator nằm ở đâu trong mô hình endpoint?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:15–03:27`

**Visual:**

Giữ endpoint capability card ở giữa và chỉ animate gate tương ứng với `Base path web mặc định /actuator nằm ở đâu trong mô hình endpoint?`—enablement, exposure, availability, HTTP/JMX adapter, base path hoặc risk control—để mỗi section thêm một điều kiện vào cùng mô hình.

**Script:**

HTTP và JMX dùng chung identity, nhưng chỉ HTTP cần ghép URL. Vì vậy `/actuator` phải được đặt đúng vị trí trong mô hình routing.

**Purpose:**

Đặt URL composition sau transport selection để `/actuator` được hiểu là routing chứ không phải identity.

### Scene 1 — Base path web mặc định /actuator nằm ở đâu trong mô hình endpoint?

**Time:** `03:27–03:53`

**Visual:**

Vẽ endpoint ID `health` đi qua `management.endpoints.web.base-path=/actuator` thành `/actuator/health`. Đổi base path sang `/manage` nhưng ID vẫn giữ nguyên; chỉ thêm `management.server.base-path` khi có separate management server.

**Script:**

Tiền tố `/actuator` thuộc web routing, không thuộc endpoint identity. Với mặc định, endpoint ID `health` thường nằm ở `/actuator/health`; đổi `management.endpoints.web.base-path` chỉ di chuyển route, không đổi tên endpoint. Khi management chạy trên server riêng, endpoint base path còn được resolve tương đối với management-server base path.

**Purpose:**

Ngăn URL convention bị nhầm với endpoint identity và chuẩn bị cho phần network placement phía sau.

## Vì sao việc expose endpoint từ xa phải là một quyết định có chủ đích?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:53–04:06`

**Visual:**

Giữ endpoint capability card ở giữa và chỉ animate gate tương ứng với `Vì sao việc expose endpoint từ xa phải là một quyết định có chủ đích?`—enablement, exposure, availability, HTTP/JMX adapter, base path hoặc risk control—để mỗi section thêm một điều kiện vào cùng mô hình.

**Script:**

Khi transport và routing đã rõ, câu hỏi cuối không còn là “expose được gì?” mà là “capability nào thực sự đáng để remote caller chạm tới?”

**Purpose:**

Từ mechanics của exposure chuyển sang quyết định risk: capability nào thật sự đáng để reachable từ xa.

### Scene 1 — Vì sao việc expose endpoint từ xa phải là một quyết định có chủ đích?

**Time:** `04:06–04:42`

**Visual:**

Lập ma trận rủi ro: `health` ít disclosure; `metrics` vừa; `env/configprops` nhạy cảm; `loggers` có write; `heapdump` rất nhạy cảm và tốn tài nguyên. Bao quanh bằng exposure, network và authorization.

**Script:**

Exposure phải có chủ đích vì management endpoint có thể lộ internals hoặc thay đổi runtime. Configuration view lộ cấu trúc, metrics lộ tín hiệu hoạt động, logger write thay behavior, heap dump có thể chứa production data. Wildcard include còn có thể làm endpoint mới sau upgrade vô tình được publish. Hãy expose tập nhỏ nhất phục vụ workflow vận hành thật, rồi tiếp tục bảo vệ bằng network và authorization.

**Purpose:**

Khép chương bằng lý do vận hành đằng sau default exposure tối thiểu, thay vì chỉ ghi nhớ property.
