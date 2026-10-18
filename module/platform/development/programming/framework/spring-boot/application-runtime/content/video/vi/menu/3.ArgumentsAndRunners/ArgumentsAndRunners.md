---
video:
  url: ""
---

# Application arguments và runners

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

## `ApplicationArguments` diễn giải đầu vào dòng lệnh như thế nào?

<!-- VIDEO_SECTION -->

### Scene 1 — `ApplicationArguments` diễn giải đầu vào dòng lệnh như thế nào?

**Time:** `00:00–00:38`

**Visual:**

Progressive reveal trên visual của chương: hiện terminal `--mode=demo sample` cạnh response `/runtime/arguments` thật và highlight option/non-option.

**Script:**

Dùng `--mode=demo sample` làm invocation cụ thể. Boot giữ original source arguments, còn `ApplicationArguments` tách option khỏi positional data: `mode` là option và `sample` là non-option. `/spring-boot/runtime/arguments` trả chính các view của process đã start nên không cần tự parse `String[]`. Đây là abstraction đúng cho startup flag của invocation; property-source precedence vẫn thuộc externalized-configuration.

**Purpose:**

Chứng minh parsed argument model của Boot bằng option/non-option thật thay vì tự parse `String[]`.


## `ApplicationRunner` và `CommandLineRunner` chạy ở thời điểm nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:38–00:50`

**Visual:**

Giữ command-line pane cạnh startup timeline; chuyển focus từ parsed input sang runner phase hoặc card ordering.

**Script:**

Input đã parse chỉ có ý nghĩa khi code sử dụng nó, nên đặt runner vào khoảng mà bean đã sẵn sàng nhưng readiness vẫn còn chờ.

**Purpose:**

Nối parsed invocation data với lifecycle phase nơi startup work cần bean có thể tiêu thụ nó.

### Scene 2 — `ApplicationRunner` và `CommandLineRunner` chạy ở thời điểm nào?

**Time:** `00:50–01:39`

**Visual:**

Progressive reveal trên visual của chương: zoom Started → runner phase → Ready với normal bean đã có nhưng readiness gate còn đóng.

**Script:**

Bên trong runner phase, chúng chạy trước `ApplicationReadyEvent` và trước khi Boot chuyển readiness thành `ACCEPTING_TRAFFIC`. Boot chạy `ApplicationRunner` và `CommandLineRunner` sau khi `ApplicationContext` refresh và `ApplicationStartedEvent` đã được phát. Thời điểm này rất phù hợp cho công việc startup cần bean bình thường và bắt buộc phải hoàn thành trước khi ứng dụng được xem là sẵn sàng: kiểm tra invariant riêng của ứng dụng, làm ấm một cache nhỏ bắt buộc, hoặc thực hiện workload dạng lệnh của ứng dụng non-web. Thời điểm này cũng tạo ra trách nhiệm. Runner chặn vài phút thì readiness cũng chậm vài phút. Runner ném exception thì startup không hoàn tất bình thường.

**Purpose:**

Đặt runner chính xác sau started/context refresh nhưng trước ready để thấy rõ ảnh hưởng tới readiness.


## `ApplicationRunner` và `CommandLineRunner` khác nhau thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:39–01:48`

**Visual:**

Giữ command-line pane cạnh startup timeline; chuyển focus từ parsed input sang runner phase hoặc card ordering.

**Script:**

Hai runner interface ở cùng một phase; khác biệt hữu ích nằm ở dạng argument mà mỗi interface nhận.

**Purpose:**

Tách hai runner API có cùng timing nhưng khác contract argument.

### Scene 3 — `ApplicationRunner` và `CommandLineRunner` khác nhau thế nào?

**Time:** `01:48–02:35`

**Visual:**

Progressive reveal trên visual của chương: so `ApplicationRunner(ApplicationArguments)` với `CommandLineRunner(String...)` trong khi timing lifecycle giữ nguyên.

**Script:**

Khi normal bean đã sẵn sàng, `CommandLineRunner.run(String... args)` nhận chuỗi command-line thô, còn `ApplicationRunner.run(ApplicationArguments args)` nhận biểu diễn đã được Boot phân tích với API cho option/non-option. Hai interface runner nằm ở cùng giai đoạn lifecycle; khác biệt chính là dạng đối số. Chọn `ApplicationRunner` khi code quan tâm command-line options như dữ liệu có cấu trúc. Chọn `CommandLineRunner` khi chính chuỗi đối số thô là hợp đồng cần dùng. Không interface nào bất đồng bộ hơn hay chạy "muộn hơn" interface kia. Vì thời điểm và thứ tự giống nhau, lựa chọn nên dựa trên ngữ nghĩa của đầu vào.

**Purpose:**

Phân biệt `ApplicationRunner` và `CommandLineRunner` theo contract argument trong khi giữ timing lifecycle giống nhau.


## Điều khiển thứ tự khi có nhiều runner như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:35–02:47`

**Visual:**

Giữ command-line pane cạnh startup timeline; chuyển focus từ parsed input sang runner phase hoặc card ordering.

**Script:**

Khi một runner đã rõ, nhiều startup task tạo ra nhu cầu ordering, vì vậy bước tiếp theo là cách Boot làm thứ tự đó tường minh.

**Purpose:**

Chỉ đưa ordering vào sau khi contract của một runner đã rõ để sequence không bị hiểu thành lifecycle phase khác.

### Scene 4 — Điều khiển thứ tự khi có nhiều runner như thế nào?

**Time:** `02:47–03:49`

**Visual:**

Progressive reveal trên visual của chương: xếp runner card theo order và animate order nhỏ chạy trước trong cùng phase.

**Script:**

Khi có nhiều runner, runner có thể implement `Ordered` hoặc dùng `@Order`; giá trị order nhỏ hơn có độ ưu tiên cao hơn và được gọi sớm hơn tương đối. Với nhiều runner, hợp đồng sắp thứ tự của Spring được áp dụng. Việc sắp thứ tự hữu ích khi có dependency startup thực sự, ví dụ phải nạp dữ liệu tham chiếu trước khi kiểm tra một index phụ thuộc vào dữ liệu đó. Nhưng không nên biến danh sách runner cùng hàng loạt số order thành một công cụ điều phối quy trình ẩn. Nếu runner B không thể chạy đúng khi thiếu runner A, hãy làm dependency đó rõ trong thiết kế và kiểm thử. Nếu dependency graph ngày càng lớn, nên chuyển phần điều phối thành application service chuyên trách thay vì tiếp tục thêm callback và độ ưu tiên dạng số.

**Purpose:**

Cho thấy `Ordered`/`@Order` làm startup sequence của nhiều runner tường minh nhưng không biến nó thành business transaction ordering.


## Loại công việc startup nào phù hợp với runner?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:49–03:59`

**Visual:**

Giữ command-line pane cạnh startup timeline; chuyển focus từ parsed input sang runner phase hoặc card ordering.

**Script:**

Ordering trả lời task nào chạy trước, nhưng chưa trả lời task có nên nằm trong runner phase hay không.

**Purpose:**

Chuyển từ “runner nào chạy trước?” sang câu hỏi quan trọng hơn: công việc có thật sự thuộc startup hay không.

### Scene 5 — Loại công việc startup nào phù hợp với runner?

**Time:** `03:59–04:58`

**Visual:**

Progressive reveal trên visual của chương: hiện checklist startup work hữu hạn kết thúc trước khi readiness gate mở.

**Script:**

Với startup work bắt buộc, ví dụ: kiểm tra điều kiện tiên quyết runtime không thể xác minh sớm hơn, thực hiện một migration startup nhỏ do ứng dụng sở hữu, hoặc chạy task ở chế độ lệnh rồi kết thúc tiến trình. Runner phù hợp với công việc có ba đặc điểm: cần context đã refresh, thực sự thuộc startup và readiness phải chờ nó hoàn tất. Hãy giữ công việc có giới hạn và quan sát được. Log rõ lúc bắt đầu/lỗi, để exception nghiêm trọng lan truyền và đảm bảo việc chạy lặp lại an toàn nếu môi trường triển khai có thể khởi động lại tiến trình. Nếu việc kiểm tra có thể làm ngay khi bind configuration, hãy dùng configuration validation. Nếu invariant thuộc bước tạo bean, hãy để bean lifecycle xử lý.

**Purpose:**

Xác định loại initialization hữu hạn, cần bean và bắt buộc hoàn tất trước readiness phù hợp với runner.


## Công việc nào không nên chặn giai đoạn chạy runner?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:58–05:10`

**Visual:**

Giữ command-line pane cạnh startup timeline; chuyển focus từ parsed input sang runner phase hoặc card ordering.

**Script:**

Runner chỉ phù hợp với startup work hữu hạn; kết thúc bằng việc tách công việc dài hạn sang executor hoặc scheduler được quản lý.

**Purpose:**

Vẽ boundary giữa initialization hữu hạn và work phải rời readiness-critical path.

### Scene 6 — Công việc nào không nên chặn giai đoạn chạy runner?

**Time:** `05:10–06:07`

**Visual:**

Progressive reveal trên visual của chương: chuyển loop/retry dài khỏi runner phase sang managed executor/scheduler.

**Script:**

Trước khi readiness mở, những công việc đó làm giai đoạn runner không kết thúc, kéo theo `ApplicationReadyEvent` và readiness không tới. Runner không phù hợp với vòng lặp polling vô hạn, message consumer sống suốt tiến trình hoặc workload nền nặng kéo dài. Tạo thread không được quản lý bên trong runner còn bỏ qua hạ tầng thực thi do Boot quản lý và làm shutdown khó dự đoán. Nếu công việc phải tiếp tục sau startup, hãy đưa nó sang executor được quản lý, scheduler hoặc thành phần runtime chuyên biệt. Nếu lưu lượng không cần chờ công việc, đừng chặn readiness khi không có yêu cầu rõ ràng. Một dấu hiệu thiết kế xấu khác là dùng runner để "sửa" cấu hình thiếu hoặc nuốt lỗi khởi tạo nghiêm trọng.

**Purpose:**

Giữ work dài hạn, block hoặc retry vô hạn ra khỏi runner phase để startup vẫn hữu hạn và quan sát được.
