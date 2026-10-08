---
video:
  url: ""
---

# Tích hợp Docker Compose cho môi trường phát triển

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

## Vì sao Boot tích hợp Docker Compose cho quá trình phát triển?

<!-- VIDEO_SECTION -->

### Scene 1 — Vì sao Boot tích hợp Docker Compose cho quá trình phát triển?

**Time:** `00:00–00:56`

**Visual:**

Progressive reveal trên visual của chương: hiện laptop developer với Boot app + database/broker/cache trong Compose và đóng dấu DEVELOPMENT ONLY.

**Script:**

Trong local development, hỗ trợ Docker Compose của Spring Boot giảm phần ghép nối thủ công giữa "khởi động các dependency" và "khởi động ứng dụng". Lập trình viên thường cần database, broker, cache hoặc dịch vụ bên ngoài chỉ để chạy ứng dụng cục bộ. Dependency `spring-boot-docker-compose` dành cho môi trường phát triển có thể phát hiện Compose file, khởi động dịch vụ khi cần và tạo connection details để Boot auto-configuration sử dụng. Đây là tích hợp dành cho môi trường phát triển, không phải bộ điều phối production. Mục tiêu là làm runtime ứng dụng cục bộ và các dịch vụ phụ thuộc khởi động phối hợp với nhau. Module này vì vậy dạy cơ chế phát hiện, lifecycle, readiness và hành vi service connection của Boot.

**Purpose:**

Định nghĩa Docker Compose support của Boot là development-time runtime integration, không phải production infrastructure orchestrator.


## Boot phát hiện và quản lý dự án Compose như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:56–01:09`

**Visual:**

Giữ topology local app ↔ service connection ↔ Compose rồi di chuyển callout qua lifecycle ownership, connection details, mapped port, readiness, control và boundary.

**Script:**

Development integration bắt đầu từ lifecycle ownership: Boot phải biết Compose project nào tồn tại và ai đã start nó trước khi quyết định có quyền stop hay không.

**Purpose:**

Biến motivation development-time thành lifecycle-ownership rule quyết định Boot có quyền start/stop Compose project hay không.

### Scene 2 — Boot phát hiện và quản lý dự án Compose như thế nào?

**Time:** `01:09–02:11`

**Visual:**

Progressive reveal trên visual của chương: rẽ discovery thành “already running” và “Boot starts it”, chỉ gán stop ownership cho nhánh sau.

**Script:**

Tại project discovery, nếu project chưa chạy và chính sách lifecycle cho phép, Boot gọi Docker Compose để khởi động project. Khi hỗ trợ Docker Compose có trên classpath, Boot tìm `compose.yml` và những tên file Compose phổ biến trong thư mục làm việc. Khi ứng dụng shutdown bình thường, chính sách mặc định dừng các dịch vụ mà Boot đã khởi động. Nếu Boot phát hiện các dịch vụ Compose đã chạy từ trước, nó chỉ tạo các service connection được hỗ trợ; Boot không gọi `docker compose up` thêm và không nhận trách nhiệm dừng project đã được tiến trình/lập trình viên khác khởi động. Quy tắc này tránh việc ứng dụng vô tình tắt hạ tầng không do nó sở hữu. Khi lifecycle gây bất ngờ, hãy kiểm tra Boot có phải bên đã khởi động project không và `spring.docker.compose.lifecycle-management` đang là gì.

**Purpose:**

Làm start/stop ownership tường minh để Boot không dừng Compose project mà nó chỉ phát hiện đã chạy sẵn.


## Khi nào Boot tạo service connection details?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:11–02:21`

**Visual:**

Giữ topology local app ↔ service connection ↔ Compose rồi di chuyển callout qua lifecycle ownership, connection details, mapped port, readiness, control và boundary.

**Script:**

Khi project đã chạy, giá trị tiếp theo của Boot là biến service được nhận diện thành connection details cho auto-configuration.

**Purpose:**

Chuyển từ project lifecycle sang service metadata được Boot đổi thành application connection details.

### Scene 3 — Khi nào Boot tạo service connection details?

**Time:** `02:21–03:21`

**Visual:**

Progressive reveal trên visual của chương: biến image/label metadata được hỗ trợ thành typed service connection, để unsupported container không bind.

**Script:**

Với service được nhận diện, service connection mô tả cách ứng dụng kết nối dịch vụ từ xa, thay vì buộc mỗi auto-configuration tự tìm lại host/port/credential từ nhiều property. Với dịch vụ Compose được nhận diện, Boot tạo các bean connection details mà auto-configuration tương ứng có thể sử dụng. Việc nhận diện thường dựa trên tên container image. Image tùy biến có thể dùng label `org.springframework.Boot.service-connection` được Boot tài liệu hóa để chỉ ra loại dịch vụ; container cũng có thể được bỏ qua bằng label ignore được hỗ trợ. Service connection là hợp đồng tích hợp chứ không thay thế cấu hình Compose tổng quát. Boot chỉ tạo connection details cho công nghệ nó hỗ trợ; container khác vẫn có thể chạy trong cùng Compose project mà không trở thành service connection do Boot quản lý.

**Purpose:**

Cho thấy khi nào Compose service được nhận diện thành service-connection bean và khi nào container chỉ tồn tại trong project.


## Cổng đã ánh xạ và thứ tự ưu tiên của connection details ảnh hưởng ứng dụng ra sao?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:21–03:33`

**Visual:**

Giữ topology local app ↔ service connection ↔ Compose rồi di chuyển callout qua lifecycle ownership, connection details, mapped port, readiness, control và boundary.

**Script:**

Connection details quan trọng vì host port có thể khác container port, đồng thời dữ liệu do Boot sinh có precedence cao hơn static connection property.

**Purpose:**

Giải thích giá trị của connection details bằng mapped host port và configuration precedence.

### Scene 4 — Cổng đã ánh xạ và thứ tự ưu tiên của connection details ảnh hưởng ứng dụng ra sao?

**Time:** `03:33–04:31`

**Visual:**

Progressive reveal trên visual của chương: vẽ container port → mapped host port → service connection → client auto-config và gạch static property xung đột.

**Script:**

Tại mapped-port boundary, docker Compose service connection của Boot dùng mapped host port để JVM cục bộ kết nối đúng địa chỉ thực tế có thể truy cập. Compose thường ánh xạ một container port cố định sang host port khác hoặc được cấp động. Khi service connection tồn tại, connection details của nó có độ ưu tiên cao hơn các configuration property kết nối thông thường cho auto-configuration tương ứng. Điều này có chủ đích vì host port động không thể được cấu hình tĩnh của ứng dụng biết trước. Nếu ứng dụng kết nối tới port bất ngờ, hãy xem service connection và ánh xạ cổng hiện tại trước khi sửa datasource/client properties. Nếu không, bạn có thể đang chỉnh property có độ ưu tiên thấp hơn và không thay đổi hành vi runtime.

**Purpose:**

Giải thích vì sao mapped host port và precedence của service connection có thể thắng static client property ở local development.


## Boot xác định dịch vụ Compose đã sẵn sàng như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:31–04:41`

**Visual:**

Giữ topology local app ↔ service connection ↔ Compose rồi di chuyển callout qua lifecycle ownership, connection details, mapped port, readiness, control và boundary.

**Script:**

Port mở vẫn chưa chứng minh service ready, nên layer kế tiếp là healthcheck/TCP readiness cùng các timeout control.

**Purpose:**

Tách container đã start khỏi service đã ready trước khi giới thiệu healthcheck và TCP readiness.

### Scene 5 — Boot xác định dịch vụ Compose đã sẵn sàng như thế nào?

**Time:** `04:41–05:43`

**Visual:**

Progressive reveal trên visual của chương: animate container started ≠ service ready; healthcheck trước, TCP fallback/timeout sau.

**Script:**

Trước khi dependency dùng được, Boot vì vậy chờ dịch vụ Compose đạt readiness trước khi tích hợp phát triển được xem là sẵn sàng. Tiến trình container đã khởi động chưa chắc dịch vụ bên trong đã sẵn sàng. Tín hiệu nên ưu tiên là Compose `healthcheck`; nếu không có, Boot có thể dùng phương án dự phòng là thử kết nối TCP tới mapped port. Kiểm tra readiness bằng TCP có thể tắt theo container bằng label được Boot hỗ trợ; timeout kết nối/đọc và timeout readiness tổng thể cũng có property riêng. Những điều khiển này phối hợp startup, không định nghĩa health ở mức nghiệp vụ của dịch vụ. Nếu dependency cần điều kiện mạnh hơn "TCP port nhận kết nối", hãy mô tả điều kiện trong Compose healthcheck của dịch vụ thay vì biến Boot thành nơi hiểu nội bộ dịch vụ.

**Purpose:**

Phân biệt container process đã start với service đã ready và chỉ rõ boundary healthcheck/TCP fallback.


## Việc chọn file, profile, cơ chế bỏ qua và chính sách lifecycle thay đổi tích hợp ra sao?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:43–05:53`

**Visual:**

Giữ topology local app ↔ service connection ↔ Compose rồi di chuyển callout qua lifecycle ownership, connection details, mapped port, readiness, control và boundary.

**Script:**

Default discovery/lifecycle không phù hợp mọi local workflow, nên tiếp theo là file selection, profiles, skip control và lifecycle policy.

**Purpose:**

Chỉ đưa runtime controls vào sau khi default discovery/readiness đã rõ để mỗi property có lý do cụ thể.

### Scene 6 — Việc chọn file, profile, cơ chế bỏ qua và chính sách lifecycle thay đổi tích hợp ra sao?

**Time:** `05:53–06:39`

**Visual:**

Progressive reveal trên visual của chương: bao quanh project bằng file/profile/lifecycle/command-timeout/test-skip control.

**Script:**

Khi default không phù hợp, `spring.docker.compose.file` chọn file khác chuẩn, `spring.docker.compose.profiles.active` kích hoạt Compose profiles và `spring.docker.compose.lifecycle-management` quyết định Boot có khởi động/dừng project hay không. Boot cung cấp một số điều khiển khi quy trình Compose mặc định không phù hợp. Trong Boot 3.3, các giá trị lifecycle gồm `none`, `start-only` và `start-and-stop`. Tùy chọn lệnh start/stop cùng timeout tiếp tục điều chỉnh cách Boot gọi Compose CLI. Khi kiểm thử, hỗ trợ Docker Compose bị bỏ qua mặc định trừ khi được bật rõ ràng. Các thiết lập này mô tả quan hệ của Boot với Compose project.

**Purpose:**

Dạy runtime control thay đổi mối quan hệ Boot với Compose project mà không biến chương thành tutorial topology Compose.


## Hỗ trợ Docker Compose lúc phát triển bàn giao sang kiểm thử với Testcontainers ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:39–06:50`

**Visual:**

Giữ topology local app ↔ service connection ↔ Compose rồi di chuyển callout qua lifecycle ownership, connection details, mapped port, readiness, control và boundary.

**Script:**

Development Compose và automated test có thể chạy cùng image nhưng khác lifecycle owner; vì vậy tách local runtime support khỏi Testcontainers testing.

**Purpose:**

Tách dependency do developer runtime sở hữu khỏi dependency do test sở hữu trước khi bàn giao sang Testcontainers.

### Scene 7 — Hỗ trợ Docker Compose lúc phát triển bàn giao sang kiểm thử với Testcontainers ở đâu?

**Time:** `06:50–07:50`

**Visual:**

Progressive reveal trên visual của chương: đặt Development Compose và Testcontainers testing cạnh nhau với lifecycle-owner badge khác nhau.

**Script:**

Tại testing handoff, chương này bao quát Compose project gắn với runtime ứng dụng cục bộ của lập trình viên. Spring Boot có thể tích hợp Docker Compose trong môi trường phát triển và Testcontainers trong kiểm thử, nhưng bên sở hữu lifecycle khác nhau. Boot mặc định tắt hỗ trợ Docker Compose khi chạy test. Có thể bật tường minh, nhưng module `testing` sở hữu Testcontainers service connections và thiết kế integration test tự động có lifecycle lặp lại được. Ngay cả khi cả hai chạy cùng database image, mục tiêu vẫn khác: Compose trong môi trường phát triển tối ưu quy trình cục bộ dài hơn của lập trình viên; Testcontainers testing tối ưu dependency do test kiểm soát cùng tính cô lập/khả năng tái lập. Chọn theo bên sở hữu lifecycle chứ không chỉ theo công nghệ container.

**Purpose:**

Tách lifecycle Compose do developer sở hữu khỏi lifecycle Testcontainers do test sở hữu dù có thể dùng cùng service image.


## Phần nào vẫn thuộc trách nhiệm của Docker và Compose tổng quát?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:50–08:02`

**Visual:**

Giữ topology local app ↔ service connection ↔ Compose rồi di chuyển callout qua lifecycle ownership, connection details, mapped port, readiness, control và boundary.

**Script:**

Sau khi Boot đã xử lý discovery, service connection, readiness và lifecycle, image build, network, volume và production orchestration vẫn là concern Docker/Compose tổng quát.

**Purpose:**

Khép chương tại điểm Boot-specific integration kết thúc và generic Docker/Compose ownership bắt đầu.

### Scene 8 — Phần nào vẫn thuộc trách nhiệm của Docker và Compose tổng quát?

**Time:** `08:02–08:54`

**Visual:**

Progressive reveal trên visual của chương: đưa Dockerfile/image/network/volume/security/production orchestration ra ngoài Boot integration box.

**Script:**

Tại generic Docker boundary, xây dựng image, Dockerfile, layers, registry, network, volume, ngữ nghĩa merge của Compose, giới hạn tài nguyên, bảo mật container và điều phối production là các mối quan tâm container tổng quát. Boot gọi Compose và diễn giải metadata dịch vụ được hỗ trợ, nhưng Docker vẫn là runtime bên ngoài. Ranh giới này cũng tách hỗ trợ Compose lúc phát triển khỏi việc tạo image của Boot. `bootBuildImage`/Cloud Native Buildpacks thuộc `build-tooling-packaging`; việc viết Dockerfile tổng quát thuộc containerization. Nếu Compose CLI tự lỗi, hãy điều tra Docker/Compose trước. Nếu Compose chạy đúng nhưng Boot không tạo connection details hoặc hành vi lifecycle mong muốn, quay lại lớp tích hợp application-runtime này.

**Purpose:**

Kết thúc Boot ownership ở Compose invocation/metadata rồi chuyển Dockerfile, image, network, volume, security và production orchestration sang container owner.
