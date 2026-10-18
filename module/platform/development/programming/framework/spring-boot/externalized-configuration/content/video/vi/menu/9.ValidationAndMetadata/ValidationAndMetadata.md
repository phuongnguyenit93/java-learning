---
video:
  url: ""
---

# Xác thực cấu hình và metadata

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

## Vì sao nên xác thực cấu hình ngay khi khởi động?

<!-- VIDEO_SECTION -->

### Scene 1 — Đúng kiểu chưa chắc đã đúng hợp đồng

**Time:** `00:00–00:50`

**Visual:** Sơ đồ `property → binding/chuyển đổi kiểu → đối tượng có kiểu → xác thực`. Một giá trị `8080` được chuyển thành `int`, rồi ví dụ `pool-size=-1` dừng lại ở bước kiểm tra ràng buộc.

**Script:** “Chuyển đổi kiểu chỉ chứng minh rằng một giá trị có thể trở thành kiểu Java; nó chưa chứng minh giá trị đó hợp lệ với miền cấu hình. Xác thực chạy sau binding để kiểm tra những điều kiện như host bắt buộc, kích thước pool phải dương hay timeout phải nằm trong khoảng cho phép. Mục tiêu là phát hiện lỗi ngay khi ứng dụng khởi động, thay vì để cấu hình sai biến thành lỗi lúc chạy khó hiểu.”

**Purpose:** Đặt validation đúng vị trí trong luồng xử lý và giải thích lợi ích của việc phát hiện lỗi sớm.

## Xác thực @ConfigurationProperties bằng @Validated

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:50–01:00`

**Visual:** Đối tượng có kiểu từ sơ đồ được gắn `@Validated` cùng các ràng buộc Jakarta Validation.

**Script:** “Với `@ConfigurationProperties`, ta có thể mô tả hợp đồng của miền cấu hình ngay trên kiểu và các thuộc tính của nó.”

**Purpose:** Chuyển từ mục tiêu của validation sang cách bật validation cho cấu hình có kiểu.

### Scene 2 — @Validated phân biệt chuyển đổi kiểu với ràng buộc miền

**Time:** `01:00–01:50`

**Visual:** Mở record `ClientProperties(@NotBlank String baseUrl, @Positive int maxConnections)`; giá trị `client.max-connections=-1` được bind thành `int`, sau đó bị validation từ chối.

**Script:** “Đặt `@Validated` lên kiểu `@ConfigurationProperties` và ràng buộc Jakarta Validation lên các thành phần cần kiểm tra. Với `max-connections=-1`, binding vẫn tạo được một `int`, nhưng `@Positive` từ chối giá trị đó và quá trình khởi động báo lỗi. Nhờ vậy ta biết dữ liệu đã đúng kiểu nhưng vi phạm hợp đồng miền, chứ không phải lỗi bộ phân tích hay bộ chuyển đổi.”

**Purpose:** Chứng minh validation hoạt động sau binding bằng một lỗi có thể phân loại rõ ràng.

## Xác thực cấu hình lồng nhau bằng @Valid

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:00`

**Visual:** `MailProperties` mở ra đối tượng lồng `Security`; constraint bên trong chưa được kích hoạt.

**Script:** “Nếu đối tượng cấu hình có cấu trúc lồng nhau, validation cũng cần biết phải đi sâu qua liên kết nào.”

**Purpose:** Nối binding đối tượng lồng nhau với cơ chế validation theo tầng.

### Scene 3 — @Valid cho phép kiểm tra ràng buộc bên trong đối tượng lồng

**Time:** `02:00–02:50`

**Visual:** Mở `MailProperties` có trường `@Valid Security security`; bên trong `Security.protocol` có `@NotBlank`. Bật rồi bỏ `@Valid` để minh họa constraint bên trong được kiểm tra hay bị bỏ qua.

**Script:** “Ràng buộc nằm trong đối tượng con không tự động bảo đảm validator sẽ đi sâu vào đó. Đánh dấu liên kết bằng `@Valid` để xác thực tiếp tục xuống đối tượng lồng. Nếu một giá trị bên trong rõ ràng sai mà vẫn không bị bắt, hãy kiểm tra đường xác thực trước khi kết luận rằng quá trình phân giải property có vấn đề.”

**Purpose:** Làm rõ vai trò của `@Valid` trong cấu hình lồng nhau và cách chẩn đoán khi ràng buộc bên trong không được kiểm tra.

## Lỗi binding, chuyển đổi kiểu và xác thực khác nhau thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:50–03:00`

**Visual:** Ba loại lỗi xuất hiện tại ba vị trí khác nhau trên luồng khởi động.

**Script:** “Nhiều lỗi đều kết thúc bằng việc ứng dụng không khởi động được, nhưng vị trí xảy ra lỗi cho ta hướng xử lý hoàn toàn khác nhau.”

**Purpose:** Chuyển từ từng cơ chế validation sang cách phân loại lỗi theo toàn bộ luồng cấu hình.

### Scene 4 — Xác định giai đoạn lỗi trước khi sửa

**Time:** `03:00–03:55`

**Visual:** Bảng ba hàng: thiếu import bắt buộc → nạp cấu hình; `timeout=banana` → chuyển đổi kiểu; `max-connections=-1` + `@Positive` → validation. Làm nổi bật thông tin lỗi tương ứng trong log.

**Script:** “Nếu import bắt buộc không tồn tại, lỗi nằm ở bước nạp cấu hình. Nếu `timeout=banana` không thể chuyển thành `Duration`, lỗi nằm ở binding hoặc chuyển đổi kiểu. Nếu `-1` đã thành `int` nhưng vi phạm `@Positive`, lỗi nằm ở xác thực. Xác định đúng giai đoạn trước khi sửa giúp tránh những hướng xử lý sai, như nới ràng buộc để chữa lỗi chuyển đổi kiểu hoặc điều tra thứ tự ưu tiên khi tệp còn chưa được nạp.”

**Purpose:** Cung cấp cách phân loại lỗi khởi động theo từng giai đoạn để chẩn đoán có hệ thống.

## Metadata cấu hình tồn tại để làm gì?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:55–04:05`

**Visual:** Luồng xử lý lúc chạy mờ đi; trình soạn thảo IDE và tệp `spring-configuration-metadata.json` xuất hiện.

**Script:** “Validation bảo vệ hợp đồng lúc chạy; metadata giúp người viết cấu hình hiểu hợp đồng đó ngay từ lúc chỉnh sửa, trước khi ứng dụng được chạy.”

**Purpose:** Tách tính đúng đắn lúc chạy khỏi phần mô tả phục vụ IDE và công cụ phát triển.

### Scene 5 — Metadata mô tả hợp đồng, không điều khiển hành vi lúc chạy

**Time:** `04:05–04:55`

**Visual:** Mở `META-INF/spring-configuration-metadata.json`; làm nổi bật tên, kiểu, mô tả, giá trị mặc định, deprecation và hint. Hộp gợi ý của IDE lấy thông tin từ file; bên dưới ghi “không tạo property / không đổi thứ tự ưu tiên / không thực hiện binding”.

**Script:** “Configuration metadata mô tả cho công cụ những khóa được hỗ trợ, kiểu dữ liệu, mô tả, giá trị mặc định, thông tin deprecation và gợi ý. Nó không tạo property, không thay đổi thứ tự ưu tiên và không thực hiện binding. Hành vi lúc chạy vẫn do `Environment` và binder quyết định; metadata chỉ giúp IDE và công cụ hỗ trợ người dùng viết cấu hình đúng hơn.”

**Purpose:** Ngăn việc nhầm metadata với nguồn dữ liệu hoặc cơ chế xử lý lúc chạy.

## Tạo metadata bằng configuration processor

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:55–05:05`

**Visual:** Mã nguồn `@ConfigurationProperties` đi qua bước biên dịch rồi sinh metadata.

**Script:** “Với kiểu cấu hình do ứng dụng sở hữu, Spring Boot có processor để sinh phần lớn metadata trực tiếp từ mã nguồn.”

**Purpose:** Dẫn từ vai trò của metadata sang cơ chế tạo metadata trong quá trình biên dịch.

### Scene 6 — Processor tạo metadata khi biên dịch

**Time:** `05:05–05:55`

**Visual:** Mở Gradle `annotationProcessor "org.springframework.boot:spring-boot-configuration-processor"`; minh họa biên dịch → `META-INF/spring-configuration-metadata.json`. Hiện ghi chú “có metadata không đồng nghĩa lúc chạy đã có giá trị”.

**Script:** “`spring-boot-configuration-processor` thường được khai báo trong cấu hình Gradle `annotationProcessor`. Khi biên dịch, nó đọc các kiểu `@ConfigurationProperties` cùng thông tin từ mã nguồn rồi sinh metadata dưới `META-INF`. File được tạo thành công chỉ chứng minh mô tả phục vụ công cụ đã tồn tại; nó không chứng minh property đã được cung cấp hay validation lúc chạy sẽ thành công.”

**Purpose:** Đặt configuration processor vào đúng giai đoạn biên dịch và tránh suy luận hành vi lúc chạy từ tệp metadata.

## Metadata, hỗ trợ từ IDE và ranh giới công cụ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:55–06:05`

**Visual:** Ba cột hiện ra: phân giải lúc chạy, binding/validation, metadata/IDE.

**Script:** “Khi IDE và ứng dụng lúc chạy cho tín hiệu khác nhau, hãy tách ba lớp này trước khi chẩn đoán.”

**Purpose:** Tổng hợp chương thành ranh giới rõ giữa hành vi lúc chạy, binding và công cụ hỗ trợ phát triển.

### Scene 7 — Gợi ý của IDE không quyết định hành vi lúc chạy

**Time:** `06:05–06:55`

**Visual:** Sơ đồ `mã nguồn @ConfigurationProperties → processor → metadata → gợi ý IDE`; song song là đường riêng `PropertySource → Environment → binding`. Làm nổi bật trường hợp “IDE không gợi ý nhưng lúc chạy vẫn bind được”.

**Script:** “Gợi ý của IDE chỉ hỗ trợ quá trình phát triển, không quyết định hành vi lúc chạy. Một property vẫn có thể bind được khi metadata thiếu; ngược lại, metadata có một mục cũng không bảo đảm mã đang chạy thực sự sử dụng khóa đó. Nếu IDE gợi ý sai, hãy kiểm tra processor và metadata. Nếu ứng dụng nhận sai giá trị, hãy quay lại `PropertySource`, thứ tự ưu tiên và binding.”

**Purpose:** Chốt đúng phạm vi của metadata và chỉ ra hướng chẩn đoán riêng cho từng lớp.
