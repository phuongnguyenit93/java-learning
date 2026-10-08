---
video:
  url: ""
---

# Hệ thống logging và runtime logging của Spring Boot

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

## Vì sao Boot khởi tạo logging trước ApplicationContext?

<!-- VIDEO_SECTION -->

### Scene 1 — Vì sao Boot khởi tạo logging trước ApplicationContext?

**Time:** `00:00–00:59`

**Visual:**

Progressive reveal trên visual của chương: hiện log trước `ApplicationContext` và `@PropertySources` tới quá muộn cho bootstrap logging.

**Script:**

Trong bootstrap, vì vậy Spring Boot khởi tạo logging system trước khi `ApplicationContext` được tạo. Logging phải hoạt động ngay khi ứng dụng còn đang khởi động, kể cả khi lỗi xảy ra trước lúc bean bình thường tồn tại. Thời điểm sớm này khiến logging trở thành hạ tầng bootstrap chứ không phải một bean ứng dụng thông thường. Một hệ quả là `@PropertySources` khai báo trong Spring `@Configuration` quá muộn để điều khiển bước khởi tạo logging. Boot có thể dùng các logging property được hỗ trợ từ môi trường chuẩn bị sớm, nhưng cấu hình bean không thể quay ngược thời gian để đổi logging system đã xử lý các thông điệp startup. Khi log sớm và log runtime khác kỳ vọng, hãy kiểm tra cấu hình có sẵn từ giai đoạn nào.

**Purpose:**

Giải thích vì sao logging phải bootstrap trước bean thông thường và vì sao `@PropertySources` đến quá muộn để điều khiển early log.


## `LoggingSystem` đóng vai trò gì?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:59–01:10`

**Visual:**

Giữ pre-context logging timeline và chuyển focus qua bootstrap, abstraction, common properties, native config, local output rồi observability.

**Script:**

Vì log phải tồn tại trước bean thông thường, Boot cần một abstraction bootstrap trên implementation logging cụ thể; vai trò đó thuộc `LoggingSystem`.

**Purpose:**

Dùng pre-context timing của logging để giải thích abstraction Boot cần trước khi normal bean tồn tại.

### Scene 2 — `LoggingSystem` đóng vai trò gì?

**Time:** `01:10–02:11`

**Visual:**

Progressive reveal trên visual của chương: vẽ logging implementation trên classpath → Boot `LoggingSystem` → early configured output, để normal SLF4J ở lane riêng.

**Script:**

Tại logging abstraction, Boot phát hiện cách triển khai từ classpath rồi dùng abstraction này để khởi tạo/cấu hình logging trước khi application context sẵn sàng. Boot dùng `LoggingSystem` như abstraction của Spring Boot trên các cách triển khai logging được hỗ trợ. Code ứng dụng thông thường vẫn log qua SLF4J hoặc logging API; không cần gọi `LoggingSystem` cho hoạt động logging hằng ngày. Abstraction này giải thích vì sao cùng một tập Boot properties có thể điều khiển hành vi chung trong khi file cấu hình native vẫn phụ thuộc framework cụ thể. Boot cũng có lối tùy biến ở cấp bootstrap để chọn hoặc tắt logging system qua system property `org.springframework.Boot.logging.LoggingSystem` được tài liệu hóa. Kiến trúc appender, vận chuyển log từ xa, lưu giữ, indexing và phân tích log thuộc logging/observability; `LoggingSystem` không biến Boot thành logging backend.

**Purpose:**

Đặt `LoggingSystem` đúng vai trò abstraction bootstrap của Boot trong khi application logging bình thường vẫn dùng SLF4J/logging API.


## Các property của Boot có thể cấu hình những khía cạnh logging runtime nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:11–02:24`

**Visual:**

Giữ pre-context logging timeline và chuyển focus qua bootstrap, abstraction, common properties, native config, local output rồi observability.

**Script:**

`LoggingSystem` tạo layer chung cho Boot; câu hỏi tiếp theo là phần hành vi runtime nào đủ phổ biến để cấu hình bằng Boot properties trước khi cần file native.

**Purpose:**

Chuyển từ bootstrap abstraction sang phần hành vi logging chung mà Boot có thể expose nhất quán bằng property.

### Scene 3 — Các property của Boot có thể cấu hình những khía cạnh logging runtime nào?

**Time:** `02:24–03:02`

**Visual:**

Progressive reveal trên visual của chương: hiện YAML root/package level và file output; đặt implementation-specific appender ngoài Boot property boundary.

**Script:**

Dùng Boot properties cho common logging surface. `logging.level.<logger-name>` và `logging.level.root` điều khiển level; `logging.file.name` hoặc `logging.file.path` thêm local file output; pattern/charset property được hỗ trợ điều chỉnh cấu hình mặc định. YAML nhỏ với `root: INFO`, package ứng dụng ở `DEBUG` và `logs/application.log` cho thấy boundary. Nếu cần appender, filter, encoder hoặc routing riêng của implementation, chuyển sang native logging configuration thay vì kéo generic property model quá xa.

**Purpose:**

Xác định boundary của common Boot logging properties trước khi appender/filter/encoder/routing riêng cần native config.


## Vì sao các file cấu hình logging dạng `-spring` quan trọng?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:02–03:15`

**Visual:**

Giữ pre-context logging timeline và chuyển focus qua bootstrap, abstraction, common properties, native config, local output rồi observability.

**Script:**

Khi common properties không đủ, timing load file trở thành vấn đề; biến thể `-spring` cho Boot tham gia thay vì để logging library khởi tạo quá sớm.

**Purpose:**

Làm rõ native logging config chỉ cần sau khi common Boot surface không còn đủ.

### Scene 4 — Vì sao các file cấu hình logging dạng `-spring` quan trọng?

**Time:** `03:15–04:12`

**Visual:**

Progressive reveal trên visual của chương: so `logback.xml` load trực tiếp với `logback-spring.xml` qua Boot và có profile/environment extension.

**Script:**

Khi cần native logging configuration, lý do là thời điểm lifecycle: file chuẩn như `logback.xml` có thể được cách triển khai logging nạp quá sớm khiến Boot không còn kiểm soát đầy đủ bước khởi tạo. Boot dùng được native logging file bình thường nhưng khuyến nghị biến thể `-spring` khi có thể, ví dụ `logback-spring.xml` hoặc `log4j2-spring.xml`. Với `logback-spring.xml`, Boot có thể tham gia cấu hình và dùng các extension hỗ trợ profile/environment ở giai đoạn phù hợp. `logging.config` cũng có thể trỏ tới một vị trí rõ ràng. File native phù hợp khi yêu cầu thật sự phụ thuộc cách triển khai. Cần giữ lựa chọn này rõ ràng vì từ đây quyền sở hữu hành vi nằm nhiều hơn ở logging framework cụ thể, không còn chỉ là Boot property.

**Purpose:**

Cho thấy vì sao `logback-spring.xml`/`log4j2-spring.xml` cho Boot tham gia đúng lifecycle phase trong khi file native chuẩn có thể load quá sớm.


## Logger groups, đầu ra và rotation nằm ở đâu trong lớp tích hợp Boot?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:12–04:23`

**Visual:**

Giữ pre-context logging timeline và chuyển focus qua bootstrap, abstraction, common properties, native config, local output rồi observability.

**Script:**

Khi ranh giới common/native đã rõ, logger group, local output và rotation là các tiện ích Boot còn lại cần đặt đúng layer.

**Purpose:**

Đặt logger group, file output và rotation sau config-file boundary để giữ chúng đúng vai trò tiện ích Boot.

### Scene 5 — Logger groups, đầu ra và rotation nằm ở đâu trong lớp tích hợp Boot?

**Time:** `04:23–05:07`

**Visual:**

Progressive reveal trên visual của chương: hiện logger group điều khiển nhiều category, console/file output và rotation limit.

**Script:**

Với local output, `. Logger group cho phép gom nhiều logger category dưới một tên logic rồi điều khiển bằng `logging.level. Cách này tiện khi một subsystem trải qua nhiều package và lập trình viên cần một công tắc runtime thay vì nhiều entry rời rạc. Boot cũng cung cấp các điều khiển đầu ra thuận tiện. Console logging có sẵn mặc định; đầu ra file bật qua `logging.file.name` hoặc `logging.file.path`. Với tích hợp Logback mặc định, Boot cung cấp property cho file rotation như kích thước file tối đa, lịch sử và tổng dung lượng giới hạn.

**Purpose:**

Đặt logger group, local file output và rotation ở layer tiện ích Boot mà không lẫn với centralized logging operations.


## Tích hợp logging của Boot kết thúc ở đâu và vận hành logging bắt đầu ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:07–05:18`

**Visual:**

Giữ pre-context logging timeline và chuyển focus qua bootstrap, abstraction, common properties, native config, local output rồi observability.

**Script:**

Sau khi log event đã được cấu hình và phát ra đúng, shipping, retention, search, correlation và incident analysis thuộc logging operations/observability.

**Purpose:**

Khép Boot integration tại điểm local log event rời ứng dụng và operational observability bắt đầu.

### Scene 6 — Tích hợp logging của Boot kết thúc ở đâu và vận hành logging bắt đầu ở đâu?

**Time:** `05:18–06:10`

**Visual:**

Progressive reveal trên visual của chương: vẽ local log event rời process tới collector/backend và Actuator logger management ở handoff riêng.

**Script:**

Tại observability boundary, trách nhiệm dừng khi ứng dụng đã phát log event/đầu ra đúng cấu hình. Chương này bao quát logging bootstrap của Boot, `LoggingSystem`, các logging property phổ biến và quan hệ giữa Boot với cấu hình native. Agent thu thập log, backend tập trung, schema có cấu trúc, retention, indexing, dashboard, cảnh báo và trace/log correlation thuộc observability. Actuator logger endpoint cũng là bề mặt quản lý production của module Actuator dù nó có thể đổi log level lúc runtime. Khi có lỗi logging, hãy phân loại trước: "Boot không áp dụng startup property này" thuộc chương hiện tại; "collector không gửi file" hoặc "backend không truy vấn được field" thuộc hạ tầng observability.

**Purpose:**

Bàn giao log shipping, retention, query, correlation, alerting và production logger management sang observability/Actuator.
