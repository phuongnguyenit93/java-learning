---
video:
  url: ""
---

# Đọc quá trình khởi động Spring Boot như bằng chứng

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

## Vì sao nên xem đầu ra khởi động là bằng chứng?

<!-- VIDEO_SECTION -->

### Scene 1 — Đọc startup thay vì gọi mọi thứ là "tự động"

**Time:** `00:00–00:55`

**Visual:**

Chia màn hình. Bên trái chỉ có dòng `main()` và chữ "Boot tự làm hết?" với dấu hỏi. Bên phải mở terminal startup log, highlight lần lượt application name, active profile, runtime component và dòng `Started ... in ... seconds`. Cuối cảnh hiện flow `change → start → observe → compare`.

**Script:**

Một ứng dụng chạy thành công không phải bằng chứng rằng Boot làm mọi thứ bằng phép thuật. Đầu ra khởi động cho ta dấu vết về ứng dụng nào đang chạy, profile nào được kích hoạt, thành phần runtime nào được khởi tạo và context có đi tới trạng thái đang chạy hay không. Vì vậy, mỗi khi thay dependency, cấu hình hoặc code, hãy khởi động lại rồi so sánh bằng chứng quan sát được với điều mình mong đợi. Đây là thói quen chẩn đoán cơ bản trước cả khi học các công cụ sâu hơn.

**Purpose:**

Biến startup log thành nguồn bằng chứng đầu tiên cho mental model Spring Boot thay vì xem hành vi mặc định là không thể quan sát.

## Có thể học được gì từ đầu ra lúc khởi động?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Terminal log được chia thành các vùng màu với nhãn `identity`, `profiles`, `runtime`, `timing`, `failure`.

**Script:**

Đã biết phải quan sát startup, câu hỏi tiếp theo là nên tìm loại bằng chứng nào thay vì học thuộc từng câu log cụ thể.

**Purpose:**

Chuyển từ thói quen quan sát sang cách phân loại thông tin trong startup output.

### Scene 2 — Năm nhóm tín hiệu trong startup log

**Time:** `01:05–02:00`

**Visual:**

Hiện một đoạn log minh họa và lần lượt highlight: Boot banner/version, `Starting ...`, profile messages, server/context messages, `Started ... in ... seconds`. Sau đó so sánh hai terminal nhỏ: web app có server/port log, non-web app không có server message.

**Script:**

Không cần coi câu chữ log là API contract. Hãy đọc theo nhóm tín hiệu. Banner cho biết phiên bản Boot đang chạy. Dòng `Starting` nhận diện ứng dụng và ngữ cảnh tiến trình. Thông báo về profile cho biết profile nào đang được kích hoạt. Thông báo về context hoặc server gợi ý dạng runtime. Dòng `Started` cho biết startup hoàn tất và thường cho thời gian. Với web application ta thường thấy server implementation và port; với non-web application, các thông báo server đó không nên xuất hiện.

**Purpose:**

Dạy learner đọc category of evidence ổn định hơn việc ghi nhớ wording log cụ thể.

## Các mặc định của Boot vẫn quan sát và ghi đè được như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:00–02:10`

**Visual:**

Từ một giá trị mặc định xuất hiện trong log, vẽ mũi tên sang `documented property/programmatic control` rồi quay lại log sau khi thay đổi.

**Script:**

Khi đã nhìn thấy một mặc định, điều quan trọng là hiểu mặc định đó không phải quyết định đóng kín. Ta có thể tìm đúng điểm điều khiển rồi quan sát lại kết quả.

**Purpose:**

Nối startup observation với tinh thần convention over configuration và khả năng override.

### Scene 3 — Mặc định là quyết định có control point

**Time:** `02:10–03:00`

**Visual:**

Hiện hai trạng thái: `no explicit choice → documented default` và `supported explicit choice → adjusted behavior`. Minh họa bằng các callout `application name`, `banner mode`, `server shape`, `configuration value`; không đi vào precedence chi tiết.

**Script:**

Boot giúp trường hợp phổ biến chạy với ít thiết lập, nhưng một mặc định vẫn là một quyết định có thể quan sát và thay đổi. Tên ứng dụng, chế độ banner, hành vi liên quan server hay nhiều tích hợp đều có điểm điều khiển bằng cấu hình hoặc mã. Khi gặp một mặc định trong log, hãy tìm tài liệu và module phụ trách nó thay vì kết luận rằng hành vi đã bị viết cố định trong mã. Thứ tự ưu tiên property cụ thể sẽ được học trong `externalized-configuration`.

**Purpose:**

Làm rõ default là observable và overridable nhưng giữ precedence mechanics ngoài Fundamentals.

## Những nhóm lỗi khởi động nào quan trọng với người mới?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:00–03:10`

**Visual:**

Terminal đang chạy thành công chuyển thành ba failure cards đặt trên timeline từ trước `main` tới context refresh.

**Script:**

Startup log hữu ích nhất khi ứng dụng không đi tới dòng `Started`. Khi đó, thay vì nhìn stack trace như một khối, hãy định vị lớp bị lỗi trước.

**Purpose:**

Chuyển từ quan sát success path sang triage startup failure ở mức người mới.

### Scene 4 — Ba lớp failure để định vị ban đầu

**Time:** `03:10–04:15`

**Visual:**

Hiện timeline ba vùng. `Java/process`: main class không launch, missing class, invalid process arguments. `Spring container`: bean creation, DI, configuration parsing, refresh failure. `Boot integration/config`: selected integration không cấu hình được, thiếu required configuration, environment choice conflict. Highlight dòng root cause ở cuối stack trace minh họa.

**Script:**

Nhóm thứ nhất là Java hoặc tiến trình: JVM chưa khởi động được main class, thiếu class bắt buộc hoặc argument sai trước khi Boot thật sự bắt đầu. Nhóm thứ hai là Spring container: context refresh thất bại vì tạo bean, dependency injection hay cấu hình Spring. Nhóm thứ ba là tích hợp hoặc cấu hình Boot: Boot đã vào quá trình startup nhưng một tích hợp được chọn không thể cấu hình với thiết lập hiện tại. Các nhóm có thể chồng lên nhau, nên đây là cách phân loại ban đầu, không phải một hệ phân loại exception chính thức. Luôn ưu tiên nguyên nhân gốc và thông báo lỗi có ý nghĩa sớm nhất.

**Purpose:**

Cung cấp mental model chẩn đoán coarse-grained mà không biến Fundamentals thành chương exception chi tiết.

## Khi nào nên chuyển việc chẩn đoán sang Auto-Configuration hoặc Actuator?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:15–04:25`

**Visual:**

Từ failure card, tạo hai nhánh: `startup/config decision` và `running application operational state`.

**Script:**

Sau khi định vị được vấn đề, loại câu hỏi quyết định công cụ tiếp theo: ta đang hỏi về quyết định cấu hình lúc startup hay trạng thái của ứng dụng đã chạy?

**Purpose:**

Chuẩn bị handoff giữa auto-configuration diagnostics và Actuator.

### Scene 5 — Chọn đúng owner cho câu hỏi chẩn đoán

**Time:** `04:25–05:15`

**Visual:**

Nhánh trái: `startup did not produce expected configuration → Condition Evaluation Report / auto-configuration`. Nhánh phải: `application is running → health/metrics/loggers/operational endpoints → Actuator`. Kết bằng biển `startup log ≠ production monitoring system`.

**Script:**

Nếu câu hỏi là vì sao một auto-configuration match, không match hoặc back off, hãy chuyển sang module `auto-configuration` và Condition Evaluation Report. Nếu startup đã thành công và câu hỏi là trạng thái của service đang vận hành, đó là phần của Actuator với health, metrics, loggers và operational endpoints. Startup log là bằng chứng rất tốt cho giai đoạn khởi động, nhưng không phải một hệ thống giám sát vận hành hoàn chỉnh.

**Purpose:**

Kết thúc bằng boundary chẩn đoán rõ ràng và ngăn learner dùng startup logs thay cho observability/Actuator runtime surface.
