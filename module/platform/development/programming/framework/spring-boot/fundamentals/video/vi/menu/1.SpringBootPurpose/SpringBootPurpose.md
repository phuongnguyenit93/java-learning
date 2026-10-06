---
video:
  url: ""
---

# Mục đích và mô hình tư duy về Spring Boot

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

## Spring Boot là gì và vì sao cần nó?

<!-- VIDEO_SECTION -->

### Scene 1 — Từ Spring code đến ứng dụng chạy được

**Time:** `00:00–00:55`

**Visual:**

Mở bằng sơ đồ hai cột. Bên trái là một ứng dụng Spring với các ô "dependency versions", "bootstrap", "infrastructure beans", "packaging" đang phải nối thủ công. Bên phải gom các ô đó quanh một khối `Spring Boot`, rồi chạy mũi tên `application code + dependencies → SpringApplication → ApplicationContext → running application`.

**Script:**

Spring Boot tồn tại để giảm phần thiết lập lặp lại khi đưa một ứng dụng Spring từ mã nguồn tới trạng thái chạy được. Spring Framework vẫn là nơi cung cấp container, dependency injection và các cơ chế nền tảng. Boot đặt thêm quy ước, giá trị mặc định, cách điều phối dependency, bootstrap và tích hợp quanh những cơ chế đó. Vì vậy, cách nghĩ đúng ngay từ đầu là: Boot giúp lắp ghép và khởi động một ứng dụng Spring thuận tiện hơn; nó không tạo ra một framework hay container hoàn toàn khác.

**Purpose:**

Đặt mental model gốc cho toàn bộ module: Spring Boot giảm công việc lặp lại quanh Spring Framework và dẫn ứng dụng tới một runtime có thể giải thích được.

## Spring Framework và Spring Boot chịu trách nhiệm khác nhau thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Giữ sơ đồ trước, tách khối `ApplicationContext` sang vùng "Spring Framework" và các khối `SpringApplication`, starter, auto-configuration, DevTools sang vùng "Spring Boot".

**Script:**

Muốn hiểu Boot mà không xem nó như phép thuật, bước tiếp theo là phân biệt rõ phần nào vốn thuộc Spring Framework và phần nào Boot bổ sung quanh đó.

**Purpose:**

Chuyển từ định nghĩa Spring Boot sang ranh giới trách nhiệm giữa Boot và Spring Framework.

### Scene 2 — Hai lớp trách nhiệm

**Time:** `01:05–01:55`

**Visual:**

Hiện bảng so sánh. Cột Spring Framework: `ApplicationContext`, bean, DI, `@Configuration`, Spring MVC/WebFlux. Cột Spring Boot: `SpringApplication`, `@SpringBootApplication`, starter, auto-configuration, DevTools, executable packaging. Highlight `ApplicationContext` ở cột Framework rồi vẽ Boot bao quanh quá trình tạo/cấu hình context.

**Script:**

Một cách kiểm tra rất hữu ích là hỏi: khái niệm này có còn tồn tại trong ứng dụng Spring không dùng Boot hay không? Bean, `ApplicationContext`, `@Configuration` hay Spring MVC vẫn là cơ chế của Spring Framework. `SpringApplication`, starter, Boot auto-configuration, DevTools và cách đóng gói thực thi được là phần Boot mang vào. Một ứng dụng Boot thông thường vẫn chạy trên Spring `ApplicationContext`; Boot giúp xây dựng và cấu hình context đó thuận tiện hơn.

**Purpose:**

Ngăn hiểu lầm Boot thay thế Spring Framework và tạo nền cho các handoff sang module Spring Framework hoặc Boot chuyên sâu.

## Vì sao convention over configuration giúp giảm công việc thiết lập?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:55–02:05`

**Visual:**

Thu nhỏ bảng trách nhiệm thành một khối "Boot conventions" rồi xuất hiện ba nhánh: `common case`, `special case`, `unusual architecture`.

**Script:**

Khi đã biết Boot chịu trách nhiệm ở lớp tích hợp, câu hỏi tự nhiên là: Boot giảm thiết lập bằng cách nào mà vẫn không lấy mất quyền kiểm soát của ứng dụng?

**Purpose:**

Nối ranh giới trách nhiệm với cơ chế convention over configuration.

### Scene 3 — Mặc định trước, ghi đè khi cần

**Time:** `02:05–02:55`

**Visual:**

Hiện flow `trường hợp phổ biến → dùng mặc định`, `trường hợp đặc biệt → ghi đè`, `kiến trúc khác thường → tùy biến/thay thế`. Sau đó minh họa classpath có web libraries làm xuất hiện web application context và embedded-server integration; cạnh đó có icon property/programmatic hook để điều chỉnh.

**Script:**

Convention over configuration nghĩa là Boot bắt đầu bằng những lựa chọn hợp lý cho trường hợp phổ biến, rồi cho phép ứng dụng chỉ khai báo phần khác biệt. Ví dụ, khi classpath có web stack phù hợp, Boot có thể chuẩn bị web application context và tích hợp server mà ta không phải tự nối từng bean hạ tầng. Nếu yêu cầu thay đổi, Boot thường có property hoặc hook bằng mã để điều chỉnh. Mặc định vì thế là điểm xuất phát có thể quan sát và ghi đè, không phải hành vi bị khóa kín.

**Purpose:**

Biến khẩu hiệu convention over configuration thành một mô hình quyết định cụ thể và có thể kiểm soát.

## Đầu vào cấu hình thay đổi ứng dụng Boot như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:55–03:05`

**Visual:**

Giữ nguyên một biểu tượng JAR ở giữa, đưa hai môi trường `dev` và `prod` vào hai bên với các file/property khác nhau.

**Script:**

Mặc định chỉ là một phần của câu chuyện. Cùng một artifact còn có thể nhận đầu vào cấu hình khác nhau ở từng môi trường mà không cần sửa Java code.

**Purpose:**

Chuyển từ convention sang externalized configuration ở mức định hướng, đúng boundary của Fundamentals.

### Scene 4 — Cùng code, đầu vào khác, lựa chọn runtime có thể khác

**Time:** `03:05–03:55`

**Visual:**

Hiện `application.jar` cố định. Bên trái gắn `application.yml`, environment variable và command-line argument cho môi trường A; bên phải gắn bộ giá trị khác cho môi trường B. Hai nhánh đi tới các callout `application name`, `port`, `profile`, `feature setting` khác nhau.

**Script:**

Ở mức Fundamentals, chỉ cần giữ một ý: đầu vào cấu hình là một trong các yếu tố định hình ứng dụng mà Boot tạo ra. Giá trị có thể đến từ `application.properties`, YAML, biến môi trường, command-line argument và các nguồn được hỗ trợ khác. Cùng code và dependencies nhưng đầu vào khác có thể dẫn tới lựa chọn runtime khác. Còn Config Data, thứ tự ưu tiên, profile, binding và `@ConfigurationProperties` sẽ thuộc module externalized-configuration.

**Purpose:**

Cho thấy vai trò của configuration inputs mà không kéo chi tiết precedence/binding ra khỏi module chuyên trách.

## Các khả năng chính của Spring Boot phối hợp với nhau ra sao?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:55–04:05`

**Visual:**

Gom các mảnh `classpath`, `configuration inputs`, `application beans` vào một canvas chung; chuẩn bị animate chúng đi vào `SpringApplication`.

**Script:**

Đến đây ta đã gặp nhiều mảnh riêng. Bây giờ cần đặt chúng đúng vị trí để tránh tưởng rằng starter, auto-configuration và `SpringApplication` chạy như một chuỗi tuyến tính đơn giản.

**Purpose:**

Chuẩn bị mental model phối hợp giữa các khả năng Boot trước khi tổng hợp boundary.

### Scene 5 — Các tín hiệu đi vào quá trình bootstrap

**Time:** `04:05–05:00`

**Visual:**

Animate sơ đồ: `starter/managed dependencies → classpath`; sau đó `classpath + configuration inputs + application bean definitions → SpringApplication bootstrap + ApplicationContext preparation/refresh`; trong vùng refresh hiển thị `auto-configuration processed as configuration`; cuối cùng là `resulting ApplicationContext → runtime/web integrations`. Đặt packaging/Actuator/testing/native ở một vòng ngoài riêng.

**Script:**

Starter chủ yếu giúp hình thành classpath. Đầu vào cấu hình cung cấp giá trị và lựa chọn tường minh. Bean hay bean definition của ứng dụng cũng là tín hiệu quan trọng. `SpringApplication` dùng những thông tin này trong quá trình chuẩn bị và refresh `ApplicationContext`; auto-configuration được xử lý như cấu hình Spring trong chính lifecycle đó và phản ứng với classpath, configuration cùng context đang được xây dựng. Packaging, Actuator, testing và hỗ trợ native nằm ngoài chuỗi bootstrap cốt lõi này và phục vụ các giai đoạn khác.

**Purpose:**

Thiết lập quan hệ đúng giữa classpath, cấu hình, bean, SpringApplication và auto-configuration mà không đảo thứ tự hay đánh đồng cơ chế.

## Fundamentals chịu trách nhiệm đến đâu và học gì tiếp theo?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:00–05:10`

**Visual:**

Zoom out từ sơ đồ bootstrap thành bản đồ tám module kế tiếp quanh `fundamentals`.

**Script:**

Mô hình tư duy đã đủ rộng để định hướng, nhưng Fundamentals cố ý dừng trước các cơ chế chuyên sâu. Phần cuối là biết câu hỏi nào phải chuyển sang module phụ trách nào.

**Purpose:**

Biến boundary của curriculum thành kỹ năng định tuyến câu hỏi kỹ thuật.

### Scene 6 — Bản đồ handoff sau Fundamentals

**Time:** `05:10–06:05`

**Visual:**

Hiện bảng hai cột: Config Data/profile/binding → `externalized-configuration`; condition/back-off → `auto-configuration`; lifecycle/runners/logging → `application-runtime`; server/TLS/proxy/shutdown → `web-runtime`; plugin/bootJar/layers/Buildpacks → `build-tooling-packaging`; production endpoints → `actuator`; Boot test/slices → `testing`; AOT/native → `native-image`. Kết bằng highlight `Fundamentals = vocabulary + end-to-end map`.

**Script:**

Sau Fundamentals, mục tiêu không phải biết sâu mọi tính năng Boot, mà biết bức tranh và chọn đúng nơi để đào sâu. Cấu hình ngoài đi sang externalized-configuration; condition và back-off sang auto-configuration; lifecycle chi tiết sang application-runtime; server sang web-runtime; build và packaging sang build-tooling-packaging; vận hành sang Actuator; kiểm thử sang testing; AOT và native image sang native-image. Nếu bạn phân biệt được những ranh giới này, bạn đã có bản đồ để học Spring Boot mà không trộn lẫn trách nhiệm.

**Purpose:**

Khép video bằng bản đồ ownership cụ thể và handoff trực tiếp sang các module Spring Boot tiếp theo.
