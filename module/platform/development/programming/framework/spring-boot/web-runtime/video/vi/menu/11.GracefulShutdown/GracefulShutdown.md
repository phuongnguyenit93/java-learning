---
video:
  url: ""
---

# Graceful shutdown

<!--
VIDEO SCRIPT FORMAT

Section rules:
- Each H2 is one video section/chapter.
- The first section requires at least 1 Scene.
- From the second section onward, each section requires at least 1 Transition + 1 Scene.
- Step 6 may add more Scenes/Transitions when the Knowledge content needs them.

TRANSITION FORMAT

### Transition

**Time:** MM:SS–MM:SS

**Visual:**

Describe the transition, title card, or screen change.

**Script:**

Write the short bridge from the previous section to the current section.

**Purpose:**

Explain why this transition exists.

SCENE FORMAT

### Scene N — <optional scene title>

**Time:** MM:SS–MM:SS

**Visual:**

Describe what the viewer sees: slide, diagram, source code, terminal, API request/response, runtime output, highlight, or callout.

**Script:**

Write the narration/presentation script for this scene.

**Purpose:**

Explain what this scene teaches, demonstrates, or proves.
-->

## Hai chế độ shutdown của server: immediate và graceful

<!-- VIDEO_SECTION -->

### Scene 1 — Hai chế độ shutdown của server: immediate và graceful

**Time:** `00:00–00:40`

**Visual:**

Dùng shutdown timeline: context close → ngừng nhận request mới → request in-flight hoàn tất → phase timeout → server stop; đặt readiness ở lane riêng. Với "Hai chế độ shutdown của server: immediate và graceful", đặt `immediate`, `graceful` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Boot 3.3 hỗ trợ hai chế độ shutdown cho web server là `immediate` và `graceful`, trong đó `immediate` là mặc định. Đặt `server.shutdown` thành `graceful` khi một lần kết thúc có kế hoạch cần ngừng nhận việc mới nhưng vẫn cho request đang xử lý một khoảng thời gian hữu hạn để hoàn tất. Cơ chế này được hỗ trợ trên bốn họ embedded server của Boot 3.3 và cho cả ứng dụng Servlet lẫn Reactive. Nó thay hành vi shutdown của server chứ không tạo một quy trình shutdown tách khỏi application context.

**Purpose:**

Giải thích "Hai chế độ shutdown của server: immediate và graceful" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## Graceful shutdown nằm ở đâu trong lúc ApplicationContext đóng?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:40–00:55`

**Visual:**

Giữ timeline context-close/graceful-shutdown trên màn hình. Làm mờ annotation của "Hai chế độ shutdown của server: immediate và graceful" rồi animate focus sang "Graceful shutdown nằm ở đâu trong lúc ApplicationContext đóng?"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Hai chế độ shutdown của server: immediate và graceful" đã rõ. Dependency kế tiếp là "Graceful shutdown nằm ở đâu trong lúc ApplicationContext đóng?"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Graceful shutdown nằm ở đâu trong lúc ApplicationContext đóng?" là dependency kế tiếp sau "Hai chế độ shutdown của server: immediate và graceful", đồng thời giữ ownership và runtime state liên tục.

### Scene 2 — Graceful shutdown nằm ở đâu trong lúc ApplicationContext đóng?

**Time:** `00:55–01:32`

**Visual:**

Dùng lifecycle timeline: SpringApplication → web context refresh → factory lookup/customization → server create/start; với shutdown thì đảo timeline và tô close phase. Với "Graceful shutdown nằm ở đâu trong lúc ApplicationContext đóng?", đặt `ApplicationContext`, `SmartLifecycle` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Graceful server shutdown diễn ra như một phần của việc đóng `ApplicationContext`. Boot thực hiện nó ở giai đoạn sớm nhất khi dừng các`SmartLifecycle` bean, nhờ đó điểm vào web bắt đầu từ chối công việc mới trong khi phần còn lại của ứng dụng tiếp tục shutdown có điều phối. Đây là lý do graceful shutdown thuộc web-runtime nhưng có điểm bàn giao sang application-runtime. Server tham gia cùng quá trình đóng context chứ không chạy một tiến trình shutdown độc lập bên ngoài Spring.

**Purpose:**

Nối input trong "Graceful shutdown nằm ở đâu trong lúc ApplicationContext đóng?" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.

## Cấu hình timeout cho giai đoạn shutdown

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:32–01:47`

**Visual:**

Giữ timeline context-close/graceful-shutdown trên màn hình. Làm mờ annotation của "Graceful shutdown nằm ở đâu trong lúc ApplicationContext đóng?" rồi animate focus sang "Cấu hình timeout cho giai đoạn shutdown"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Khi "Graceful shutdown nằm ở đâu trong lúc ApplicationContext đóng?" đã rõ, hãy kiểm boundary kế tiếp: "Cấu hình timeout cho giai đoạn shutdown". Giữ kết quả trước để cơ chế mới đi ra trực tiếp từ nó.

**Purpose:**

Chuyển từ "Graceful shutdown nằm ở đâu trong lúc ApplicationContext đóng?" sang "Cấu hình timeout cho giai đoạn shutdown" mà không reset mô hình, đồng thời làm rõ input, owner hoặc output nào thay đổi.

### Scene 3 — Cấu hình timeout cho giai đoạn shutdown

**Time:** `01:47–02:19`

**Visual:**

Dùng shutdown timeline: context close → ngừng nhận request mới → request in-flight hoàn tất → phase timeout → server stop; đặt readiness ở lane riêng. Với "Cấu hình timeout cho giai đoạn shutdown", đặt `spring.lifecycle.timeout-per-shutdown-phase`, `SmartLifecycle` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

`spring.lifecycle.timeout-per-shutdown-phase` đặt ngân sách thời gian cho một phase shutdown. Khi graceful web-server shutdown được bật, ngân sách phase đó chính là khoảng thời gian request đang xử lý có thể hoàn tất trước khi shutdown tiếp tục. Vì vậy giá trị như hai mươi giây phải được hiểu là ngân sách lifecycle của ứng dụng, không phải HTTP timeout riêng lẻ; các `SmartLifecycle` participant khác trong cùng phase cũng có thể chịu ảnh hưởng.

**Purpose:**

Biến "Cấu hình timeout cho giai đoạn shutdown" thành lựa chọn cụ thể bằng cách cho thấy runtime thay đổi gì và tầng cấu hình hoặc customization nào nên dùng tiếp.

## Các server được hỗ trợ ngừng nhận request mới như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:19–02:35`

**Visual:**

Giữ timeline context-close/graceful-shutdown trên màn hình. Làm mờ annotation của "Cấu hình timeout cho giai đoạn shutdown" rồi animate focus sang "Các server được hỗ trợ ngừng nhận request mới như thế nào?"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ta đã chốt "Cấu hình timeout cho giai đoạn shutdown". Giữ kết quả đó trên màn hình; bây giờ chuyển sang "Các server được hỗ trợ ngừng nhận request mới như thế nào?" và chỉ thay quyết định runtime kế tiếp.

**Purpose:**

Nối "Cấu hình timeout cho giai đoạn shutdown" với "Các server được hỗ trợ ngừng nhận request mới như thế nào?" để quyết định web-runtime kế tiếp đi ra từ kết quả đã chứng minh.

### Scene 4 — Các server được hỗ trợ ngừng nhận request mới như thế nào?

**Time:** `02:35–03:21`

**Visual:**

Đặt property sources ở trái, ServerProperties ở giữa và server factory ở phải; tô riêng portable server.* và một namespace server-specific để thấy hai tầng cấu hình. Với "Các server được hỗ trợ ngừng nhận request mới như thế nào?", đặt `503 Service Unavailable` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Hợp đồng chung của Boot là “cho công việc đang xử lý hoàn tất trong khoảng thời gian gia hạn và không nhận công việc mới”, nhưng cơ chế khác nhau theo server. Trong Boot 3.3, Jetty, Reactor Netty và Tomcat ngừng nhận request mới ở tầng mạng khi graceful shutdown. Undertow khác ở chỗ vẫn có thể chấp nhận kết nối nhưng trả HTTP `503 Service Unavailable` ngay cho request mới. Kết nối duy trì cũng có thể làm máy khách quan sát hành vi khác nhau, vì vậy không nên lấy hành vi trên đường truyền của một server làm định nghĩa chung cho graceful shutdown.

**Purpose:**

Cho thấy hệ quả runtime cụ thể của "Các server được hỗ trợ ngừng nhận request mới như thế nào?" và tạo checkpoint quan sát được cho quyết định Boot sở hữu.

## Khoảng thời gian gia hạn cho request đang xử lý

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:21–03:38`

**Visual:**

Giữ timeline context-close/graceful-shutdown trên màn hình. Làm mờ annotation của "Các server được hỗ trợ ngừng nhận request mới như thế nào?" rồi animate focus sang "Khoảng thời gian gia hạn cho request đang xử lý"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Section trước đã cho evidence cho "Các server được hỗ trợ ngừng nhận request mới như thế nào?". Đi tiếp trên cùng runtime path tới "Khoảng thời gian gia hạn cho request đang xử lý" và quan sát input, owner hoặc output nào thay đổi.

**Purpose:**

Giữ evidence từ "Các server được hỗ trợ ngừng nhận request mới như thế nào?" và chỉ đưa thêm cơ chế mới cần cho "Khoảng thời gian gia hạn cho request đang xử lý".

### Scene 5 — Khoảng thời gian gia hạn cho request đang xử lý

**Time:** `03:38–04:18`

**Visual:**

Dùng shutdown timeline: context close → ngừng nhận request mới → request in-flight hoàn tất → phase timeout → server stop; đặt readiness ở lane riêng. Với "Khoảng thời gian gia hạn cho request đang xử lý", đặt input của section và runtime result quan sát được tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Request đã bắt đầu xử lý được một khoảng thời gian để hoàn tất trong giai đoạn shutdown đã cấu hình. Graceful shutdown vì vậy giúp giảm lỗi không cần thiết khi kết thúc có kế hoạch, triển khai cuốn chiếu hoặc thay thế instance. Đây vẫn là shutdown có giới hạn, không phải chờ vô hạn. Công việc của ứng dụng phải phù hợp ngân sách thời gian của vòng đời, và orchestrator/load balancer bên ngoài cũng phải cho tiến trình đủ thời gian để sử dụng khoảng thời gian gia hạn đó.

**Purpose:**

Giải thích "Khoảng thời gian gia hạn cho request đang xử lý" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## Bàn giao sang application-runtime và trạng thái sẵn sàng

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:18–04:33`

**Visual:**

Giữ timeline context-close/graceful-shutdown trên màn hình. Làm mờ annotation của "Khoảng thời gian gia hạn cho request đang xử lý" rồi animate focus sang "Bàn giao sang application-runtime và trạng thái sẵn sàng"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Khoảng thời gian gia hạn cho request đang xử lý" đã rõ. Dependency kế tiếp là "Bàn giao sang application-runtime và trạng thái sẵn sàng"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Bàn giao sang application-runtime và trạng thái sẵn sàng" là dependency kế tiếp sau "Khoảng thời gian gia hạn cho request đang xử lý", đồng thời giữ ownership và runtime state liên tục.

### Scene 6 — Bàn giao sang application-runtime và trạng thái sẵn sàng

**Time:** `04:33–05:18`

**Visual:**

Giữ runtime diagram của chương nhưng chỉ hiện input, quyết định Boot sở hữu và output server/context của section; làm mờ mọi node khác. Với "Bàn giao sang application-runtime và trạng thái sẵn sàng", đặt `SIGTERM` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Trách nhiệm web-runtime kết thúc khi hành vi dừng có kiểm soát của server và thời điểm trong vòng đời đã rõ. Trạng thái sẵn sàng, tín hiệu tiến trình, các bean vòng đời khác, công việc nền và chính sách kết thúc của orchestrator thuộc application-runtime cùng phần hạ tầng chịu trách nhiệm. Một ranh giới thực tế là tín hiệu kết thúc. Tài liệu Boot lưu ý rằng nút stop trong IDE có thể dẫn tới shutdown ngay nếu IDE không gửi `SIGTERM` phù hợp. Graceful shutdown chỉ tham gia khi tiến trình thực sự đi vào đường đóng context thông thường.

**Purpose:**

Nối input trong "Bàn giao sang application-runtime và trạng thái sẵn sàng" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.
