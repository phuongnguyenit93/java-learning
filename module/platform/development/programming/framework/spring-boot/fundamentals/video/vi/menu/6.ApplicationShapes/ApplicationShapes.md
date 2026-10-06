---
video:
  url: ""
---

# Các dạng ứng dụng và mô hình embedded server

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

## Spring Boot có thể khởi động những dạng ứng dụng nào?

<!-- VIDEO_SECTION -->

### Scene 1 — Spring Boot không đồng nghĩa với web server

**Time:** `00:00–00:55`

**Visual:**

Mở bằng biểu tượng `SpringApplication` ở giữa, tỏa ra ba nhánh `non-web`, `Servlet web`, `reactive web`. Nhánh non-web không có server icon; hai nhánh web có server integration khác nhau. Đặt dấu gạch lên câu "Spring Boot = web server".

**Script:**

Một hiểu lầm rất phổ biến là cứ Spring Boot thì phải có web server. Thực tế, cùng `SpringApplication` có thể khởi động nhiều dạng ứng dụng khác nhau. Tùy classpath và thiết lập tường minh, Boot có thể tạo non-web context, Servlet web application hoặc reactive web application. Web server chỉ xuất hiện khi dạng runtime là web; nó không phải định nghĩa của Spring Boot.

**Purpose:**

Đặt mental model nền tảng về application shape và loại bỏ giả định mọi ứng dụng Boot đều là web.

## Non-web, Servlet và Reactive khác nhau thế nào ở mức tổng quan?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Ba nhánh từ scene trước phóng to thành ba card với icon workload/context tương ứng.

**Script:**

Ba dạng này cùng dùng Boot bootstrap, nhưng mỗi dạng tồn tại để phục vụ một loại runtime khác nhau. Ta chỉ cần phân biệt ở mức context và vai trò, chưa đi vào chi tiết xử lý request.

**Purpose:**

Chuyển từ danh sách application shape sang đặc điểm runtime cấp cao của từng shape.

### Scene 2 — Ba runtime shape, ba mục đích khác nhau

**Time:** `01:05–02:05`

**Visual:**

Hiện ba cột. Non-web: batch/command/background worker + `ApplicationContext`, không có HTTP listener. Servlet: Spring MVC + Servlet-capable web context. Reactive: Spring WebFlux + reactive web context. Sau đó thêm callout `MVC + WebFlux cùng có → MVC được ưu tiên khi suy luận mặc định`; bên dưới có `explicit WebApplicationType`.

**Script:**

Non-web vẫn có Boot bootstrap, dependency injection và các tích hợp khác, nhưng không cần lắng nghe HTTP request. Batch job, command application hay background worker có thể nằm ở đây. Ứng dụng Servlet dùng Servlet web stack, thường là Spring MVC, với Servlet-capable context. Ứng dụng reactive dùng reactive web stack, thường là WebFlux, với reactive context. Khi Boot suy luận từ classpath và cả MVC lẫn WebFlux cùng hiện diện, MVC được ưu tiên; nếu chủ ý khác, ứng dụng có thể đặt `WebApplicationType` tường minh.

**Purpose:**

Phân biệt application shape bằng context/runtime role và nêu đúng precedence mặc định khi MVC và WebFlux cùng có mặt.

## Embedded server có ý nghĩa gì trong một ứng dụng Boot?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:05–02:15`

**Visual:**

Giữ cột Servlet web và zoom vào server icon đang nằm trong cùng khung `java process`.

**Script:**

Khi dạng ứng dụng là web, câu hỏi tiếp theo là server nằm ở đâu và Boot đang "nhúng" điều gì vào tiến trình ứng dụng.

**Purpose:**

Nối application shape với embedded-server mental model.

### Scene 3 — Server nằm trong cùng tiến trình ứng dụng

**Time:** `02:15–03:10`

**Visual:**

Hiện cây `java process → Boot application → Spring ApplicationContext + embedded Servlet web server`. Bên cạnh là mô hình external deployment cũ hơn với application artifact được đưa vào external server, chỉ dùng để contrast khái niệm. Cuối cảnh hiện terminal `java -jar application.jar`.

**Script:**

Embedded server nghĩa là HTTP server được khởi động và quản lý như một phần của cùng tiến trình ứng dụng, thay vì bắt buộc artifact phải được copy vào một server vận hành tách biệt như cách triển khai duy nhất. Vì vậy, một executable web application có thể được khởi động bằng command hướng ứng dụng như `java -jar`. Boot không tự viết server từ đầu; nó tích hợp các server implementation được hỗ trợ và gắn lifecycle của server với context.

**Purpose:**

Giải thích "embedded" theo ranh giới process/lifecycle, không biến Fundamentals thành nội dung container internals.

## Web starter có thể thay đổi dạng runtime như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:10–03:20`

**Visual:**

Quay lại project non-web, rồi thêm dòng `spring-boot-starter-web` vào dependency list.

**Script:**

Dạng ứng dụng không phải nhãn gắn chết vào project. Chỉ một thay đổi dependency cũng có thể thay đổi các khả năng mà Boot nhìn thấy trên classpath.

**Purpose:**

Kết nối application-shape model với starter/classpath model của chương trước.

### Scene 4 — Từ dependency change tới runtime shape khác

**Time:** `03:20–04:15`

**Visual:**

Animate `add web starter → MVC/server classes appear → SpringApplication detects web capability → web context becomes eligible → embedded-server auto-configuration may apply`. Sau đó chạy animation ngược khi bỏ web stack. Đặt callout debug `unexpected server? inspect dependency tree/classpath first`.

**Script:**

Khi thêm web starter, classpath có thêm các class của web stack và server implementation. `SpringApplication` có thể suy ra một web application type khác, rồi auto-configuration cấu hình hạ tầng server phù hợp khi condition thỏa. Chiều ngược lại cũng rất hữu ích khi chẩn đoán: nếu một ứng dụng đáng lẽ non-web lại khởi động server, hãy kiểm tra dependency tree và classpath trước khi giả định lỗi nằm ở một server property.

**Purpose:**

Cho thấy dependency choice có thể đổi runtime shape và đưa ra một chiến lược debug dựa trên evidence.

## Tổng quan embedded server chuyển sang module nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:15–04:25`

**Visual:**

Các nhãn `server selection`, `server.*`, `TLS`, `forwarded headers`, `graceful shutdown` xuất hiện quanh server icon rồi được gom sang `web-runtime`.

**Script:**

Đến đây ta đã hiểu server xuất hiện khi nào và nằm ở đâu. Những câu hỏi về cách server được chọn, cấu hình và vận hành đã vượt khỏi phạm vi Fundamentals.

**Purpose:**

Đặt boundary giữa embedded-server overview và Boot web-runtime mechanics.

### Scene 5 — Giữ mental model, bàn giao mechanics

**Time:** `04:25–05:15`

**Visual:**

Hiện ba takeaway lớn: `Boot can be non-web or web`, `web runtime lives in same process`, `classpath can influence shape`. Bên phải là checklist `server implementation`, `server.* properties`, `TLS`, `proxy/forwarded headers`, `graceful shutdown → web-runtime`.

**Script:**

Fundamentals cần để lại ba ý có thể tái sử dụng: ứng dụng Boot có thể non-web hoặc web; khi là web, server có thể sống trong cùng tiến trình với ứng dụng; và classpath là một trong các tín hiệu làm dạng ứng dụng thay đổi. Còn server nào được chọn, `server.*` hoạt động ra sao, TLS được nối thế nào, forwarded headers xử lý thế nào sau proxy hay graceful shutdown vận hành ra sao đều thuộc `web-runtime`.

**Purpose:**

Khép video bằng ba invariant của application-shape mental model và handoff rõ sang web-runtime.
