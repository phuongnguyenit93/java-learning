---
video:
  url: ""
---

# Lazy initialization và tối ưu startup

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

## Lazy initialization thay đổi điều gì trong startup?

<!-- VIDEO_SECTION -->

### Scene 1 — Lazy initialization thay đổi điều gì trong startup?

**Time:** `00:00–00:47`

**Visual:**

Progressive reveal trên visual của chương: chuyển bean-creation block từ startup sang first use thay vì xóa công việc.

**Script:**

Trên startup critical path, lazy initialization đổi thời điểm đó: bean đủ điều kiện chỉ được tạo khi lần đầu cần dùng. Startup Boot bình thường tạo nhiều singleton bean trong quá trình context startup. Boot cung cấp cơ chế này qua `spring.main.lazy-initialization=true` và API trên `SpringApplication`/builder. Điều đó có thể giảm lượng công việc nằm trên đường quan trọng của startup, nhất là khi một phần bean graph chưa cần ngay. Nhưng công việc không biến mất; nó được dời sang lần sử dụng đầu tiên. Mô hình tư duy đúng là deferred initialization, không phải "hiệu năng startup miễn phí".

**Purpose:**

Định nghĩa lazy initialization là trì hoãn tạo bean, tức chuyển chi phí chứ không loại bỏ chi phí.


## Lazy initialization có thể trì hoãn những lỗi và chi phí nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:47–01:03`

**Visual:**

Giữ startup critical-path bar và chuyển cost/failure marker từ startup sang first use, measurement hoặc downstream observability.

**Script:**

Dời việc tạo bean khỏi startup path là đổi startup latency lấy công việc xảy ra muộn hơn, nên tiếp theo phải xem lỗi và chi phí nào chỉ bị trì hoãn chứ không biến mất.

**Purpose:**

Lộ phần cost bị lazy initialization dời đi để section sau đánh giá deferred failure thay vì chỉ nhìn startup number nhỏ hơn.

### Scene 2 — Lazy initialization có thể trì hoãn những lỗi và chi phí nào?

**Time:** `01:03–01:53`

**Visual:**

Progressive reveal trên visual của chương: cho misconfigured lazy bean vượt startup rồi fail ở first use; hiện memory/cost dồn về sau.

**Script:**

Khi initialization bị trì hoãn, bean cấu hình sai vốn thất bại ngay khi startup có thể chỉ lỗi khi request hoặc task nền đầu tiên cần bean đó. Đánh đổi lớn nhất của lazy initialization là thời điểm phát hiện lỗi bị trì hoãn. Điều này thay đổi cả thời điểm và tác động vận hành của lỗi. Lazy initialization cũng không có nghĩa JVM chỉ cần bộ nhớ cho bean được tạo lúc startup. Khi ứng dụng chạy đủ lâu, toàn bộ graph vẫn có thể được tạo đầy đủ, nên hoạch định năng lực phải nhìn trạng thái chạy ổn định. Spring Boot vì thế không bật lazy initialization mặc định.

**Purpose:**

Làm rõ failure discovery bị trì hoãn và steady-state memory vẫn có thể đạt full graph để tránh coi startup nhanh là optimization miễn phí.


## Vì sao phải đo startup trước khi tối ưu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:53–02:05`

**Visual:**

Giữ startup critical-path bar và chuyển cost/failure marker từ startup sang first use, measurement hoặc downstream observability.

**Script:**

Đánh đổi đó khiến tuning mù trở nên nguy hiểm. Trước khi bật optimization khác, cần đo phase nào của startup thật sự tốn thời gian.

**Purpose:**

Biến trade-off lazy-init thành yêu cầu đo lường trước khi chọn thêm optimization knob.

### Scene 3 — Vì sao phải đo startup trước khi tối ưu?

**Time:** `02:05–03:07`

**Visual:**

Progressive reveal trên visual của chương: so optimization đoán mò với startup baseline có đo và khoanh phase đắt.

**Script:**

Trước khi tuning, nguyên nhân có thể thuộc nhiều bên sở hữu: khởi tạo bean, lời gọi mạng trong code startup, cấu hình ứng dụng, thiết lập logging, runner hoặc vấn đề web server. Tối ưu startup nên bắt đầu bằng giai đoạn và phép đo, không phải property. "Startup mất 12 giây" mới chỉ là quan sát; câu hỏi hữu ích là công việc nào chiếm thời gian và nó có thật sự cần nằm trên đường quan trọng hay không. Lazy initialization không thể sửa mọi loại nguyên nhân. Hãy tạo mốc chuẩn có thể lặp lại, so sánh các lần chạy tương đương rồi thay đổi một cơ chế có chi phí giải thích được. Mục tiêu là thời gian startup chấp nhận được đồng thời giữ khả năng phát hiện lỗi sớm và độ trễ lần sử dụng đầu tiên có thể dự đoán.

**Purpose:**

Yêu cầu baseline lặp lại được và phase evidence trước khi chọn bất kỳ startup optimization nào.


## `ApplicationStartup` cung cấp bằng chứng về startup như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:07–03:18`

**Visual:**

Giữ startup critical-path bar và chuyển cost/failure marker từ startup sang first use, measurement hoặc downstream observability.

**Script:**

Measurement hữu ích cần bằng chứng có cấu trúc, vì vậy `ApplicationStartup` và các startup step được ghi là bước tiếp theo của baseline.

**Purpose:**

Đi từ “đo trước” sang startup-step evidence cụ thể mà `ApplicationStartup` có thể ghi.

### Scene 4 — `ApplicationStartup` cung cấp bằng chứng về startup như thế nào?

**Time:** `03:18–03:56`

**Visual:**

Progressive reveal trên visual của chương: hiện `StartupStep` name/tag/duration và so cùng step trước/sau một thay đổi.

**Script:**

Wall-clock number chỉ nói startup chậm; `ApplicationStartup` giúp thấy thời gian nằm ở đâu. Cấu hình recorder như `BufferingApplicationStartup`, chạy ứng dụng rồi xem `StartupStep` name, tag và timing đã ghi. Mục tiêu không phải trace mọi thứ mãi mãi. So cùng startup path trước/sau một thay đổi, xác định phase đắt rồi mới quyết định owner là bean creation, framework startup, runner hay integration khác.

**Purpose:**

Cho thấy `ApplicationStartup` ghi structured startup step để tìm phase đắt thay vì chỉ nhìn một con số wall-clock.


## Startup tracking bàn giao sang Actuator và observability ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:56–04:08`

**Visual:**

Giữ startup critical-path bar và chuyển cost/failure marker từ startup sang first use, measurement hoặc downstream observability.

**Script:**

Khi startup step đã được capture, việc expose hoặc vận chuyển dữ liệu đó chuyển sang Actuator/observability thay vì tiếp tục mở rộng application-runtime.

**Purpose:**

Tách việc thu thập startup evidence khỏi việc expose và vận chuyển evidence đó trong production.

### Scene 5 — Startup tracking bàn giao sang Actuator và observability ở đâu?

**Time:** `04:08–05:00`

**Visual:**

Progressive reveal trên visual của chương: chuyển buffered startup evidence sang Actuator/observability exposure và giữ native-image ở lane runtime khác.

**Script:**

Tại observability handoff, việc cung cấp dữ liệu startup đã buffer qua production endpoint thuộc Actuator; pipeline metrics/tracing rộng hơn thuộc observability. Chương này sở hữu việc dùng startup tracking như bằng chứng runtime và mô hình quyết định quanh lazy initialization. Ranh giới giúp luồng học rõ: trước tiên hiểu phép đo startup nói gì về chi phí lifecycle; sau đó mới học công cụ production cung cấp hoặc vận chuyển dữ liệu đó. Tương tự, đặc tính startup của AOT/native image thuộc module `native-image`. Không nên dùng quy tắc tinh chỉnh của JVM thông thường như lời giải mặc định cho dạng runtime khác. Hãy xác định runtime và nguồn đo trước khi so sánh con số.

**Purpose:**

Bàn giao việc expose startup data sang Actuator/observability và tách JVM tuning thông thường khỏi native-image runtime.
