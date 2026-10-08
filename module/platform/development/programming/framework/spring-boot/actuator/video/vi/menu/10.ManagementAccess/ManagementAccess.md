---
video:
  url: ""
---

# Quyền truy cập bề mặt quản trị và ranh giới bảo mật

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

## Vì sao tập endpoint được công khai từ xa nên được giữ tối thiểu?

<!-- VIDEO_SECTION -->

### Scene 1 — Vì sao tập endpoint được công khai từ xa nên được giữ tối thiểu?

**Time:** `00:00–00:37`

**Visual:**

Bắt đầu với exposure allow-list chỉ có `health`. Thêm lần lượt `metrics`, `loggers`, `env`, `heapdump`; mỗi lần đều reveal consumer và risk trước khi quyết định giữ endpoint đó hay không.

**Script:**

Management capability an toàn nhất là capability không bị expose từ xa nếu chưa có lý do vận hành rõ. Boot 3.3 bắt đầu với posture tối thiểu: chỉ health được expose mặc định qua web và JMX. Mỗi endpoint thêm vào nên gắn với consumer, automation hoặc incident workflow cụ thể. Cũng cần rà lại wildcard sau upgrade vì dependency hoặc endpoint mới có thể vô tình làm thêm diagnostic trở nên reachable.

**Purpose:**

Biến least exposure thành policy có thể review lại theo thời gian, không phải một cấu hình làm một lần rồi quên.

## Có thể tách web base path quản trị khỏi bề mặt ứng dụng như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:37–00:49`

**Visual:**

Giữ network path từ operator tới endpoint trên màn hình; với `Có thể tách web base path quản trị khỏi bề mặt ứng dụng như thế nào?` chỉ di chuyển đúng gate—URL routing, listener/address, exposure, authorization hoặc Spring Security ownership—để access luôn được nhìn như nhiều lớp độc lập.

**Script:**

Minimal exposure quyết định capability nào được publish. Bước tiếp theo là route của chúng nằm ở đâu—nhưng đổi route không phải access control.

**Purpose:**

Từ endpoint set tối thiểu chuyển sang URL placement nhưng vẫn nhấn mạnh tổ chức path không phải security control.

### Scene 1 — Có thể tách web base path quản trị khỏi bề mặt ứng dụng như thế nào?

**Time:** `00:49–01:15`

**Visual:**

Cho `/actuator/health` đi qua `management.endpoints.web.base-path`, đổi thành `/manage/health`, rồi minh họa individual endpoint path mapping. Thêm nhãn “đổi path ≠ security boundary”.

**Script:**

`management.endpoints.web.base-path` di chuyển common HTTP prefix, nên `/actuator` có thể thành `/manage`; path từng endpoint cũng có thể remap. Điều đó hữu ích cho routing và convention nhưng không bảo vệ endpoint. Endpoint ID vẫn là `health`, và caller tới được path mới vẫn cần network/authorization phù hợp.

**Purpose:**

Tách routing organization khỏi security và giữ endpoint identity ổn định khi URL thay đổi.

## Cổng và địa chỉ quản trị riêng thay đổi vị trí mạng như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:15–01:28`

**Visual:**

Giữ network path từ operator tới endpoint trên màn hình; với `Cổng và địa chỉ quản trị riêng thay đổi vị trí mạng như thế nào?` chỉ di chuyển đúng gate—URL routing, listener/address, exposure, authorization hoặc Spring Security ownership—để access luôn được nhìn như nhiều lớp độc lập.

**Script:**

Đổi path chỉ tổ chức lại một listener. Separate port thay đổi network placement thực sự, và chỉ lúc đó management server mới có thể bind address riêng.

**Purpose:**

Nâng từ path routing lên separate listener/address vì thay đổi này mới thực sự đổi network placement.

### Scene 1 — Cổng và địa chỉ quản trị riêng thay đổi vị trí mạng như thế nào?

**Time:** `01:28–02:06`

**Visual:**

Vẽ hai listener: application `:8080`, management `:9090`. Hiện `management.server.port=9090`, `management.server.address=127.0.0.1`, `management.server.base-path=/ops`, rồi ghép `management.endpoints.web.base-path=/actuator` bên dưới. Gắn note: management address khác chỉ được hỗ trợ khi port cũng khác.

**Script:**

`management.server.port` có thể tạo management web server riêng để hạ tầng route hoặc firewall operational traffic độc lập với business traffic. Khi port này khác main server, `management.server.address` có thể bind vào interface cụ thể như loopback hay mạng vận hành nội bộ. `management.server.base-path` là base của management server, còn Actuator web base path được resolve tương đối với nó. Boot chỉ hỗ trợ chọn management address khác khi management port đã tách khỏi main port.

**Purpose:**

Khôi phục đầy đủ cả port lẫn address semantics và cho thấy hai base path ghép với nhau như thế nào.

## Vì sao exposure endpoint và phân quyền là hai quyết định khác nhau?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:06–02:17`

**Visual:**

Giữ network path từ operator tới endpoint trên màn hình; với `Vì sao exposure endpoint và phân quyền là hai quyết định khác nhau?` chỉ di chuyển đúng gate—URL routing, listener/address, exposure, authorization hoặc Spring Security ownership—để access luôn được nhìn như nhiều lớp độc lập.

**Script:**

Port/address mới quyết định reachability; reachability vẫn chưa phải permission. Bây giờ phải tách publish endpoint khỏi quyền caller được dùng endpoint.

**Purpose:**

Dùng network placement để tách reachability khỏi exposure và caller authorization thành các gate độc lập.

### Scene 1 — Vì sao exposure endpoint và phân quyền là hai quyết định khác nhau?

**Time:** `02:17–02:58`

**Visual:**

Hiện ba gate độc lập theo thứ tự: endpoint exposure → network reachability → Spring Security authorization. Cho bốn case: enabled nhưng not exposed; exposed nhưng bị firewall; reachable nhưng 403; exposed và authorized.

**Script:**

Exposure và authorization trả lời hai câu hỏi khác nhau. Exposure quyết định management transport có publish endpoint hay không. Network placement quyết định client nào chạm tới transport. Authorization quyết định caller đã tới được đó có được invoke operation hay không. Vì thế endpoint có thể enabled mà không có route, route có thể bị network chặn, hoặc route reachable nhưng bị Spring Security từ chối. Giấu path không phải authorization; có authentication cũng không có nghĩa nên expose mọi thứ.

**Purpose:**

Khôi phục đầy đủ phân biệt exposure-versus-authorization và đặt network policy thành lớp phòng thủ thứ ba độc lập.

## Boot cung cấp hành vi bảo mật nào khi có Spring Security?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:58–03:11`

**Visual:**

Giữ network path từ operator tới endpoint trên màn hình; với `Boot cung cấp hành vi bảo mật nào khi có Spring Security?` chỉ di chuyển đúng gate—URL routing, listener/address, exposure, authorization hoặc Spring Security ownership—để access luôn được nhìn như nhiều lớp độc lập.

**Script:**

Sau khi tách exposure khỏi authorization, default integration của Boot cho biết ai đang cung cấp authorization rule—và khi nào application phải tự chịu trách nhiệm.

**Purpose:**

Áp dụng các gate đó vào Spring Security auto-configuration của Boot và điểm backoff khi application tự cung cấp filter chain.

### Scene 1 — Boot cung cấp hành vi bảo mật nào khi có Spring Security?

**Time:** `03:11–03:51`

**Visual:**

Hiện đường mặc định: Spring Security có mặt + chưa có custom `SecurityFilterChain` → Boot cung cấp management security. Highlight health là endpoint phù hợp cho basic check. Sau đó thêm custom `SecurityFilterChain`, cho Boot back off và reveal `EndpointRequest`.

**Script:**

Khi Spring Security có trên classpath và ứng dụng chưa tự khai báo `SecurityFilterChain`, Boot cung cấp default security behavior cho management surface; health vẫn phù hợp với phép kiểm tra vận hành cơ bản trong khi các endpoint Actuator khác được bảo vệ. Khi ứng dụng định nghĩa filter chain riêng, Boot back off và team phải sở hữu policy. `EndpointRequest` giúp match management endpoint, nhưng matcher order, identity, role, CSRF, session hay OAuth vẫn là nội dung của Spring Security.

**Purpose:**

Cho thấy chính xác điểm auto-configuration back off để custom security không vô tình làm mất policy quản trị mặc định mà team tưởng vẫn còn.

## Tích hợp bảo mật của Actuator bàn giao sang Spring Security ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:51–04:04`

**Visual:**

Giữ network path từ operator tới endpoint trên màn hình; với `Tích hợp bảo mật của Actuator bàn giao sang Spring Security ở đâu?` chỉ di chuyển đúng gate—URL routing, listener/address, exposure, authorization hoặc Spring Security ownership—để access luôn được nhìn như nhiều lớp độc lập.

**Script:**

Default security của Boot chỉ là lớp tích hợp. Boundary cuối cùng là ownership: Actuator nhận diện management surface, Spring Security quyết định ai được đi qua.

**Purpose:**

Khép access model bằng cách bàn giao authentication/authorization mechanics sang Spring Security sau khi Actuator routing đã được xác nhận.

### Scene 1 — Tích hợp bảo mật của Actuator bàn giao sang Spring Security ở đâu?

**Time:** `04:04–04:37`

**Visual:**

Vẽ handoff tại filter chain: Actuator cung cấp endpoint identity/exposure và request matcher; Spring Security cung cấp authentication/authorization. Route case 401/403 sang active `SecurityFilterChain`, không đổi exposure.

**Script:**

Actuator sở hữu capability quản trị, transport và điểm tích hợp để security nhận diện management request. Spring Security sở hữu cách caller được authenticate và cách authorization được enforce. Nếu endpoint đã enabled, exposed, reachable nhưng trả 401/403, investigation đã đi qua boundary đó. Hãy theo active security filter chain thay vì mở rộng Actuator exposure cho đến khi request “tự nhiên chạy”.

**Purpose:**

Đưa lỗi access về đúng owner và ngăn việc sửa security bằng cách làm endpoint public hơn.
