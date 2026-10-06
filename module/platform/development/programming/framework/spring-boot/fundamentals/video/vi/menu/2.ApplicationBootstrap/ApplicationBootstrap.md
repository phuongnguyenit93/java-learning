---
video:
  url: ""
---

# Khởi động ứng dụng với SpringApplication

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

## SpringApplication là gì và vì sao là điểm vào của Boot?

<!-- VIDEO_SECTION -->

### Scene 1 — main vẫn là điểm vào của JVM

**Time:** `00:00–00:55`

**Visual:**

Mở `DemoApplication.java`, highlight `public static void main(String[] args)` rồi tiếp tục highlight `SpringApplication.run(DemoApplication.class, args)`. Bên cạnh là sơ đồ `JVM → main → SpringApplication → Spring container`.

**Script:**

Ứng dụng Boot vẫn bắt đầu từ `main` như một chương trình Java bình thường. Điểm khác là `main` giao phần bootstrap cho `SpringApplication`. Lời gọi `SpringApplication.run(...)` nhận primary source cùng command-line arguments rồi thiết lập môi trường và Spring container. Vì vậy `SpringApplication` là cầu nối giữa lúc JVM bắt đầu tiến trình và lúc ứng dụng Spring sẵn sàng chạy; logic nghiệp vụ không nên bị nhét vào thao tác bootstrap này.

**Purpose:**

Xác lập đúng vai trò của `SpringApplication` và giữ ranh giới giữa Java entry point, bootstrap và business logic.

## SpringApplication.run biến main thành ApplicationContext như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Từ dòng `SpringApplication.run(...)`, kéo mũi tên sang một khối `ConfigurableApplicationContext` đang trống.

**Script:**

Biết ai nhận quyền điều khiển mới là bước đầu. Tiếp theo ta cần thấy lời gọi nhỏ này thực sự tạo ra thứ gì.

**Purpose:**

Chuyển từ vai trò của SpringApplication sang kết quả bootstrap cụ thể là Spring `ApplicationContext`.

### Scene 2 — Primary source, args và context đang chạy

**Time:** `01:05–02:00`

**Visual:**

Hiện code `ConfigurableApplicationContext context = SpringApplication.run(...)`. Sau đó animate `DemoApplication.class` vào ô `primary source`, `args` vào `application arguments/environment`, rồi lần lượt `create context → load configuration → refresh → running context`.

**Script:**

Class truyền vào `run` là primary source để Spring và Boot bắt đầu khám phá cấu hình. `args` vừa là application arguments, vừa có thể tham gia vào environment. Ở mức tổng quan, Boot tạo loại `ApplicationContext` phù hợp, nạp cấu hình, refresh context để tạo các singleton bean, rồi trả về context đang chạy. Việc `run` trả về một `ConfigurableApplicationContext` cũng nhắc ta rằng đây vẫn là Spring container thật, không phải một runtime riêng của Boot.

**Purpose:**

Giải thích quan hệ giữa primary source, arguments và `ApplicationContext` mà không đi sâu vào event lifecycle.

## Quá trình khởi động diễn ra ở mức tổng quan ra sao?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:00–02:10`

**Visual:**

Biến animation trước thành một timeline bootstrap đầy đủ, nhưng chỉ hiện các phase lớn.

**Script:**

Từ kết quả là một context đang chạy, ta có thể quay lại và đọc bootstrap như một chuỗi trách nhiệm cấp cao thay vì học thuộc hàng loạt event nội bộ.

**Purpose:**

Nối kết quả `run` với mental model tuần tự của quá trình bootstrap.

### Scene 3 — Chuỗi trách nhiệm của bootstrap

**Time:** `02:10–03:00`

**Visual:**

Hiện timeline `primary source + args → prepare environment/settings → choose/create ApplicationContext → load sources → refresh context/create beans → running application`. Ở cạnh trái, `classpath` và `configuration inputs` được nối vào quá trình bằng các mũi tên phụ.

**Script:**

Bootstrap có thể được đọc theo sáu bước lớn: nhận source và args, chuẩn bị environment, xác định dạng ứng dụng rồi chọn hoặc tạo context phù hợp, nạp configuration sources, refresh context và cuối cùng hoàn tất startup. Hai yếu tố đã gặp là classpath và đầu vào cấu hình ảnh hưởng kết quả này. Auto-configuration cũng phản ứng với chúng trong quá trình xây context, nhưng cách condition được đánh giá chi tiết thuộc module auto-configuration.

**Purpose:**

Cho learner một timeline đủ để định vị startup mà vẫn giữ chi tiết condition/event ở module chuyên trách.

## Ứng dụng đi qua các giai đoạn khởi động, chạy và dừng có trật tự thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:00–03:10`

**Visual:**

Timeline bootstrap dừng ở `running application`; sau đó kéo dài thêm sang `running` và `shutdown`.

**Script:**

Startup hoàn tất không có nghĩa tiến trình kết thúc. Từ đây, dạng ứng dụng và các non-daemon thread quyết định nó tiếp tục sống như thế nào.

**Purpose:**

Mở rộng mental model từ startup sang vòng đời start/run/stop cấp cao.

### Scene 4 — Non-web, web và shutdown hook

**Time:** `03:10–04:05`

**Visual:**

Chia màn hình hai nhánh. Nhánh A: `non-web command → work completes → process may exit`. Nhánh B: `web app → embedded server threads → keep serving`. Hai nhánh hợp lại ở `orderly JVM shutdown → Boot shutdown hook → close ApplicationContext → destruction callbacks`.

**Script:**

Với ứng dụng non-web kiểu command, công việc có thể hoàn thành rồi tiến trình tự đủ điều kiện kết thúc. Với web application, server và các runtime thread thường giữ tiến trình sống để phục vụ request. Khi JVM shutdown theo luồng bình thường, Boot đã đăng ký shutdown hook để đóng `ApplicationContext`, từ đó Spring có cơ hội chạy các destruction callback được quản lý. Đây là mô hình tư duy về vòng đời, chưa phải danh sách event chi tiết.

**Purpose:**

Phân biệt cách ứng dụng tiếp tục chạy theo shape và giải thích vai trò đóng context khi shutdown có trật tự.

## Mô hình vòng đời trong Fundamentals dừng ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:05–04:15`

**Visual:**

Các nhãn `events`, `runners`, `availability`, `task execution`, `logging` xuất hiện bên ngoài timeline hiện tại với mũi tên sang module `application-runtime`.

**Script:**

Timeline hiện tại đủ để định vị một lỗi ở mức thô. Khi câu hỏi chuyển sang event cụ thể hay runtime service, Fundamentals cần bàn giao thay vì kéo sâu hơn.

**Purpose:**

Xác định rõ boundary giữa bootstrap mental model và runtime lifecycle chuyên sâu.

### Scene 5 — Handoff sang application-runtime

**Time:** `04:15–05:00`

**Visual:**

Hiện ba vùng lỗi: `before SpringApplication`, `during context bootstrap/refresh`, `after running`. Bên phải là danh sách `ApplicationRunner`, `CommandLineRunner`, lifecycle events, liveness/readiness, task execution, virtual threads, logging` với nhãn `application-runtime`.

**Script:**

Trong Fundamentals, hãy giữ khả năng trả lời ba câu hỏi: JVM đã tới `main` chưa, Boot đã bắt đầu và refresh context thành công chưa, hay lỗi xảy ra sau khi ứng dụng đã chạy? Nếu cần biết event nào được publish, runner chạy lúc nào, availability state thay đổi ra sao hoặc Boot tích hợp task execution và logging thế nào, đó là phần của `application-runtime`. Ranh giới này giữ chương bootstrap gọn nhưng vẫn đủ hữu ích khi chẩn đoán ban đầu.

**Purpose:**

Kết thúc bằng kỹ năng định vị lifecycle ở mức coarse-grained và handoff chính xác sang module runtime.
