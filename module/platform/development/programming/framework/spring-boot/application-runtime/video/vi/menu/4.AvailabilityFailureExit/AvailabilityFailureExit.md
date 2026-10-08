---
video:
  url: ""
---

# Trạng thái availability, phân tích lỗi và xử lý khi thoát

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

## Liveness và readiness có ý nghĩa gì trong Boot runtime?

<!-- VIDEO_SECTION -->

### Scene 1 — Liveness và readiness có ý nghĩa gì trong Boot runtime?

**Time:** `00:00–00:54`

**Visual:**

Progressive reveal trên visual của chương: hiện gauge LIVENESS và READINESS riêng cùng dependency outage tạm thời không tự động làm liveness broken.

**Script:**

Về mặt vận hành, liveness cho biết trạng thái nội bộ của ứng dụng còn đủ đúng để tiếp tục hoặc tự phục hồi hay không. Liveness và readiness chủ ý trả lời hai câu hỏi vận hành khác nhau. Readiness cho biết ứng dụng có nên nhận lưu lượng ở thời điểm hiện tại hay không. Khác biệt này ảnh hưởng trực tiếp cách dependency bên ngoài được dùng. Database tạm thời không sẵn sàng có thể khiến một số request thất bại, nhưng nếu coi đó là lỗi liveness thì nền tảng có thể khởi động lại hàng loạt instance và làm sự cố nặng hơn. Readiness có thể thận trọng hơn vì từ chối lưu lượng không đồng nghĩa kết thúc tiến trình.

**Purpose:**

Tách liveness khỏi readiness để lỗi dependency bên ngoài không tự động trở thành tín hiệu restart process.


## Trạng thái availability thay đổi ở những thời điểm nào trong quá trình khởi động?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:54–01:06`

**Visual:**

Giữ state machine LIVE/READY và di chuyển marker sang nhánh startup, shutdown, failure hoặc exit được nói tới tiếp theo.

**Script:**

Khác biệt liveness/readiness chỉ thật sự có ý nghĩa vận hành khi đặt các state transition mặc định lên cùng timeline với started, runner và ready.

**Purpose:**

Làm khác biệt liveness/readiness quan sát được bằng cách gắn hai state vào checkpoint startup cụ thể.

### Scene 2 — Trạng thái availability thay đổi ở những thời điểm nào trong quá trình khởi động?

**Time:** `01:06–01:44`

**Visual:**

Progressive reveal trên visual của chương: animate refresh → Started → CORRECT → runners → Ready → ACCEPTING_TRAFFIC cạnh lifecycle evidence.

**Script:**

Availability mặc định của Boot đi theo startup. Sau context refresh và `ApplicationStartedEvent`, Boot publish liveness thành `LivenessState.CORRECT`; runner vẫn chạy sau đó. Chỉ khi runner xong Boot mới publish `ApplicationReadyEvent` và readiness thành `ReadinessState.ACCEPTING_TRAFFIC`. Chuỗi là refresh → started → LIVE → runners → ready → READY. Khoảng này giải thích vì sao process đã live nhưng vẫn có thể chưa nhận traffic; lifecycle experiment cho phép xác minh trực tiếp.

**Purpose:**

Gắn availability transition mặc định của Boot vào started, runner và ready để LIVE không bị coi là đồng nghĩa READY.


## Readiness thay đổi thế nào khi quá trình shutdown bắt đầu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:44–01:54`

**Visual:**

Giữ state machine LIVE/READY và di chuyển marker sang nhánh startup, shutdown, failure hoặc exit được nói tới tiếp theo.

**Script:**

Availability không chỉ có đường vào steady state; khi shutdown bắt đầu, readiness cũng phải rời trạng thái nhận traffic.

**Purpose:**

Mở availability từ startup sang shutdown để readiness được hiểu là runtime signal thay đổi theo thời gian.

### Scene 3 — Readiness thay đổi thế nào khi quá trình shutdown bắt đầu?

**Time:** `01:54–02:50`

**Visual:**

Progressive reveal trên visual của chương: chuyển readiness sang REFUSING_TRAFFIC khi shutdown và giữ HTTP draining ở lane web-runtime riêng.

**Script:**

Khi startup tiến lên, khi shutdown bắt đầu, readiness cần chuyển khỏi trạng thái nhận lưu lượng để hạ tầng ngừng gửi công việc mới trong khi lifecycle shutdown tiếp tục. Availability còn quan trọng khi ứng dụng rời trạng thái chạy ổn định. Ở lớp Boot tổng quát, hãy nghĩ theo chuyển trạng thái và việc đóng context. Việc embedded HTTP server ngừng nhận request mới, chờ request đang xử lý bao lâu và server nào hỗ trợ hành vi nào thuộc `web-runtime` cùng cấu hình graceful shutdown. Tách hai lớp này giúp tránh nhầm tuyên bố ứng dụng không còn sẵn sàng nhận lưu lượng với cơ chế protocol/server cụ thể để hoàn tất request đang xử lý. Chúng phối hợp nhưng không phải cùng một cơ chế.

**Purpose:**

Tách “không nhận traffic mới” khỏi request draining phụ thuộc protocol khi ứng dụng rời steady state.


## `ApplicationAvailability` và `AvailabilityChangeEvent` biểu diễn trạng thái runtime như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:50–03:02`

**Visual:**

Giữ state machine LIVE/READY và di chuyển marker sang nhánh startup, shutdown, failure hoặc exit được nói tới tiếp theo.

**Script:**

State transition không chỉ để vẽ sơ đồ; Boot có read side và event side để code đọc hoặc chủ động publish trạng thái có bằng chứng.

**Purpose:**

Chuyển từ ý nghĩa state sang API dùng để đọc và chủ động publish transition đó.

### Scene 4 — `ApplicationAvailability` và `AvailabilityChangeEvent` biểu diễn trạng thái runtime như thế nào?

**Time:** `03:02–03:40`

**Visual:**

Progressive reveal trên visual của chương: tách read-side `ApplicationAvailability` khỏi observe/publish-side `AvailabilityChangeEvent`.

**Script:**

Dùng `ApplicationAvailability` để đọc liveness/readiness hiện tại. `AvailabilityChangeEvent` là transition side: code có thể observe hoặc publish state có chủ đích khi đủ domain evidence, ví dụ `ReadinessState.REFUSING_TRAFFIC` trước một controlled transition. Đừng biến mọi dependency hiccup thành `LivenessState.BROKEN`; liveness nên tập trung vào internal unrecoverable state. API truyền đạt state, còn application policy quyết định lúc nào change hợp lý.

**Purpose:**

Cho thấy read/publish side của availability và trách nhiệm chỉ publish state khi có domain evidence phù hợp.


## Boot biến lỗi khởi động thành thông tin chẩn đoán có thể hành động như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:40–03:52`

**Visual:**

Giữ state machine LIVE/READY và di chuyển marker sang nhánh startup, shutdown, failure hoặc exit được nói tới tiếp theo.

**Script:**

Availability mô tả runtime state, còn startup bị abort cần một cơ chế khác: failure analysis giữ root exception và bổ sung mô tả hành động.

**Purpose:**

Tách runtime availability khỏi startup diagnostics khi ứng dụng fail trước khi thiết lập state dùng được.

### Scene 5 — Boot biến lỗi khởi động thành thông tin chẩn đoán có thể hành động như thế nào?

**Time:** `03:52–04:48`

**Visual:**

Progressive reveal trên visual của chương: freeze root exception → FailureAnalyzer description/action → optional condition report.

**Script:**

Trên failure path, khối `APPLICATION FAILED TO START` vì thế là lớp chẩn đoán đặt trên exception gốc, không phải thứ thay thế stack trace/nguyên nhân gốc. Nếu startup thất bại, Boot giữ nguyên đường exception rồi cho các `FailureAnalyzer` đã đăng ký cơ hội biến lỗi quen thuộc thành phần mô tả ngắn gọn cùng hướng xử lý cụ thể. Nếu analyzer không giải thích được lỗi, hoặc quyết định auto-configuration có liên quan, có thể bật `debug` hoặc logging cho `ConditionEvaluationReportLoggingListener` để xem conditions report. Chi tiết condition matching vẫn thuộc module auto-configuration. Thứ tự điều tra nên là: xác định exception gốc, đọc phân tích lỗi nếu có, rồi kiểm tra bằng chứng configuration/condition liên quan. Tránh đổi property ngẫu nhiên cho đến khi startup chạy.

**Purpose:**

Dạy thứ tự điều tra failure: giữ root exception, đọc failure analysis rồi mới xem condition/config evidence thay vì đổi property ngẫu nhiên.


## Shutdown hook của SpringApplication chịu trách nhiệm phần nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:48–04:59`

**Visual:**

Giữ state machine LIVE/READY và di chuyển marker sang nhánh startup, shutdown, failure hoặc exit được nói tới tiếp theo.

**Script:**

Startup fail không giống normal exit. Từ chẩn đoán failure, chuyển sang shutdown hook tổng quát dùng để đóng context đang chạy bình thường.

**Purpose:**

Phân biệt startup failure bất thường với lifecycle shutdown bình thường của context đang khỏe.

### Scene 6 — Shutdown hook của SpringApplication chịu trách nhiệm phần nào?

**Time:** `04:59–05:46`

**Visual:**

Progressive reveal trên visual của chương: vẽ JVM normal exit → shutdown hook → context close → managed callback, đặt unmanaged resource ngoài chain.

**Script:**

Với process shutdown, việc đóng context cho phép destruction callback và thành phần lifecycle do Spring quản lý tham gia shutdown thay vì tài nguyên bị bỏ lại đột ngột. `SpringApplication` mặc định đăng ký JVM shutdown hook để `ApplicationContext` được đóng khi JVM thoát theo đường bình thường. Hook này là hành vi application-runtime tổng quát. Nó không quy định cách mọi protocol hoặc tài nguyên bên ngoài hoàn tất công việc. Từng công nghệ có lớp tích hợp lifecycle riêng; graceful shutdown của embedded web server thuộc `web-runtime`. Trong thực tế nên giữ tài nguyên sống lâu trong lifecycle được quản lý khi có thể.

**Purpose:**

Giải thích shutdown hook đảm bảo điều gì cho managed context lifecycle và điều gì nó không thể sửa với resource unmanaged.


## `ExitCodeGenerator` và `SpringApplication.exit` truyền trạng thái của tiến trình như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:46–05:58`

**Visual:**

Giữ state machine LIVE/READY và di chuyển marker sang nhánh startup, shutdown, failure hoặc exit được nói tới tiếp theo.

**Script:**

Đóng context xử lý lifecycle resource nhưng command-style application còn có thể cần process result mà caller đọc được; đây là chỗ exit code tham gia.

**Purpose:**

Thêm process-result semantics sau context cleanup để command application trả outcome cho caller.

### Scene 7 — `ExitCodeGenerator` và `SpringApplication.exit` truyền trạng thái của tiến trình như thế nào?

**Time:** `05:58–06:53`

**Visual:**

Progressive reveal trên visual của chương: hiện domain outcome → exit-code generator/mapper → `SpringApplication.exit`, rồi box `System.exit` riêng.

**Script:**

Với command-style exit, Boot hỗ trợ `ExitCodeGenerator` bean và `ExitCodeExceptionMapper` để chuyển kết quả ứng dụng thành exit code. Command-style application đôi khi cần trả kết quả tiến trình cho hệ điều hành hoặc script điều phối. `SpringApplication.exit(context)` thu thập generator và trả về code kết quả. Việc gọi `SpringApplication.exit` không tự kết thúc JVM; ứng dụng dạng lệnh thường truyền code đó cho `System.exit(...)` khi muốn tiến trình dừng thật sự. Cơ chế này hữu ích cho batch/CLI khi "hoàn tất với lỗi nghiệp vụ" cần biểu diễn để máy đọc được. Hãy giữ ánh xạ nhỏ, ổn định và có tài liệu vì exit code là hợp đồng với bên gọi tiến trình, không phải thay thế xử lý exception hay log.

**Purpose:**

Cho thấy Boot tổng hợp exit code nhưng việc terminate JVM vẫn thuộc command application.


## Shutdown runtime tổng quát và graceful shutdown của web server tách nhau ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:53–07:06`

**Visual:**

Giữ state machine LIVE/READY và di chuyển marker sang nhánh startup, shutdown, failure hoặc exit được nói tới tiếp theo.

**Script:**

Exit code hoàn tất câu chuyện process chung. Việc HTTP server drain request là layer protocol khác, nên boundary cuối là generic shutdown so với web-server graceful shutdown.

**Purpose:**

Kết thúc process story chung đúng tại điểm protocol-specific HTTP draining bắt đầu.

### Scene 8 — Shutdown runtime tổng quát và graceful shutdown của web server tách nhau ở đâu?

**Time:** `07:06–08:02`

**Visual:**

Progressive reveal trên visual của chương: xếp generic process/context shutdown trên protocol-specific web-server draining và route failure sang đúng layer.

**Script:**

Tại boundary với web-runtime, mô hình này áp dụng cho web, command-line và các dạng ứng dụng Boot khác. Shutdown runtime tổng quát sở hữu câu chuyện tiến trình/context: readiness ngừng nhận công việc, JVM shutdown hook đóng `ApplicationContext`, lifecycle được quản lý chạy và tiến trình có thể trả exit code. Graceful shutdown của web server thêm một lớp riêng cho protocol: server ngừng nhận request mới và cho request đang xử lý cơ hội hoàn tất theo chính sách. Lựa chọn server, timeout và hành vi cụ thể thuộc `web-runtime`. Khi chẩn đoán shutdown, hãy tách lớp. Context không đóng là vấn đề application-runtime; HTTP request không hoàn tất như mong muốn là web-runtime; thread không được quản lý giữ JVM sống lại là vấn đề Java/application lifecycle.

**Purpose:**

Vẽ boundary giữa generic context/process shutdown và graceful drain HTTP request của embedded web server.
