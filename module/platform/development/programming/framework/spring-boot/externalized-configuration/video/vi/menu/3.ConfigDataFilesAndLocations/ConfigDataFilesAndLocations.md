---
video:
  url: ""
---

# Tệp Config Data và vị trí tìm kiếm

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

## Config Data đóng góp gì vào cấu hình Spring Boot?

<!-- VIDEO_SECTION -->

### Scene 1 — Config Data nằm trong cùng Environment

**Time:** `00:00–00:50`

**Visual:** Sơ đồ `application.properties`, `application.yaml`, các tệp theo profile và tài nguyên được import cùng đổ vào `Config Data`, sau đó thành các `PropertySource` trong `Environment`.

**Script:** “Config Data là cơ chế Boot dùng để nạp tài liệu cấu hình trước khi application context hoàn chỉnh. Nó bao gồm các tệp ứng dụng quen thuộc, biến thể theo profile, vị trí tường minh và tài nguyên import. Điểm cần nhớ là Config Data không tạo một hệ cấu hình riêng; nó đóng góp `PropertySource` vào cùng `Environment` và vẫn cạnh tranh với biến môi trường, system property hay tham số dòng lệnh.”

**Purpose:** Đặt Config Data đúng vị trí trong luồng cấu hình tổng thể.

## Các vị trí tìm kiếm mặc định trong và ngoài ứng dụng đã đóng gói

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:50–01:00`

**Visual:** Từ khối Config Data, bung ra cây thư mục trên classpath và hệ thống tệp.

**Script:** “Muốn biết tệp nào tham gia Config Data, trước hết phải biết Boot mặc định đi tìm ở đâu.”

**Purpose:** Chuyển từ vai trò Config Data sang cơ chế khám phá vị trí.

### Scene 1 — Vị trí mặc định trên classpath và bên ngoài

**Time:** `01:00–01:50`

**Visual:** Cây thư mục hiện `classpath:/`, `classpath:/config/`, thư mục hiện tại, `./config/`, và `./config/*/`; làm nổi bật lần lượt vị trí đóng gói rồi vị trí bên ngoài.

**Script:** “Boot tìm `application.properties` hoặc YAML ở gốc classpath và `classpath:/config/`. Bên ngoài gói ứng dụng, nó còn xét thư mục hiện tại, `config/` và các thư mục con trực tiếp trong `config/`. Các vị trí bên ngoài được xét sau phần đóng gói trong thứ tự Config Data, nên phù hợp để gắn cấu hình ghi đè theo môi trường.”

**Purpose:** Cho người học một bản đồ vị trí mặc định cụ thể để chẩn đoán tệp có được phát hiện hay không.

## Cấu hình đóng gói sẵn và cấu hình bên ngoài

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:00`

**Visual:** Tách màn hình thành “trong jar” và “ngoài jar”, cùng khóa `orders.timeout`.

**Script:** “Các vị trí mặc định được sắp như vậy để hỗ trợ một mô hình triển khai rất phổ biến: cấu hình nền trong jar và phần ghi đè bên ngoài.”

**Purpose:** Nối các vị trí tìm kiếm với chiến lược cấu hình triển khai.

### Scene 1 — Cấu hình nền và phần ghi đè bên ngoài

**Time:** `02:00–02:45`

**Visual:** Bên trái hiện `orders.timeout=2s` trong jar; bên phải hiện `orders.timeout=5s` ở tệp ngoài; kết quả giữa màn hình là `5s`, sau đó thêm nhãn “vẫn có thể bị biến môi trường hoặc dòng lệnh ghi đè”.

**Script:** “Nếu jar chứa `orders.timeout=2s` và tệp ngoài chứa `5s`, thì trong phạm vi Config Data, giá trị bên ngoài thắng. Nhưng đây chưa phải quy tắc cuối của toàn bộ `Environment`: biến môi trường hay tham số dòng lệnh có thứ tự ưu tiên cao hơn vẫn có thể ghi đè tiếp.”

**Purpose:** Phân biệt thứ tự ưu tiên bên trong Config Data với thứ tự ưu tiên toàn cục của `Environment`.

## application.properties và application.yaml

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:45–02:55`

**Visual:** Hai tệp `application.properties` và `application.yaml` đặt cạnh nhau, cùng biểu diễn một khóa.

**Script:** “Cùng một vị trí vẫn có thể chứa hai định dạng cấu hình khác nhau, nhưng không nên dựa vào điều đó để tạo thêm một lớp thứ tự ưu tiên khó nhớ.”

**Purpose:** Dẫn sang lựa chọn định dạng tệp mà vẫn giữ mô hình tư duy về thứ tự ưu tiên.

### Scene 1 — Hai định dạng, cùng mô hình

**Time:** `02:55–03:40`

**Visual:** Chuyển một cấu trúc YAML nhỏ thành các khóa dạng properties. Hiện chú thích “Boot 3.3: .properties ưu tiên hơn YAML khi cùng vị trí”.

**Script:** “Boot hỗ trợ cả Java properties và YAML. YAML dễ đọc với cấu trúc phân cấp; properties làm khóa hiển thị tường minh. Cả hai cuối cùng đều đóng góp giá trị vào `Environment`. Nếu cả hai cùng tồn tại tại một vị trí, Boot 3.3 cho `.properties` thứ tự ưu tiên cao hơn YAML, nên cách vận hành dễ hiểu nhất vẫn là chọn một định dạng chính thay vì dựa vào khác biệt này.”

**Purpose:** Giải thích định dạng tệp mà không khiến người học nhầm định dạng với một mô hình cấu hình riêng.

## Đổi tên cơ sở bằng spring.config.name

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:40–03:50`

**Visual:** Đổi nhãn tệp `application.properties` thành `myservice.properties`.

**Script:** “Tên `application` cũng chỉ là mặc định; Boot cho phép đổi tên cơ sở mà nó sẽ tìm.”

**Purpose:** Chuyển từ định dạng tệp sang đầu vào điều khiển tên tệp.

### Scene 1 — spring.config.name thay đổi tệp được tìm

**Time:** `03:50–04:35`

**Visual:** Terminal chạy `java -jar app.jar --spring.config.name=myservice`; cây thư mục làm nổi bật `myservice.properties` và `myservice-prod.yaml`.

**Script:** “`spring.config.name=myservice` làm Boot tìm `myservice.properties`, YAML và các biến thể theo profile thay cho tên cơ sở `application`. Vì thiết lập này quyết định Boot sẽ tìm tệp nào, nó phải được cung cấp trước giai đoạn Config Data; đặt nó trong chính tệp mà nó đang cố đổi tên là quá muộn.”

**Purpose:** Cho thấy `spring.config.name` là đầu vào phải có sớm để điều khiển quá trình khám phá, không phải thuộc tính ứng dụng thông thường.

## Thay thế vị trí tìm kiếm bằng spring.config.location

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:35–04:45`

**Visual:** Danh sách vị trí mặc định bị gạch đi và thay bằng hai vị trí do người dùng nhập.

**Script:** “Ngoài đổi tên tệp, ta còn có thể thay cả tập vị trí mà Boot sẽ dùng.”

**Purpose:** Nối việc tìm theo tên tệp với việc tìm theo vị trí.

### Scene 1 — spring.config.location thay thế vị trí mặc định

**Time:** `04:45–05:35`

**Visual:** Terminal hiện `--spring.config.location=optional:classpath:/defaults/,optional:file:./runtime-config/`. Bên cạnh là nhãn lớn “THAY THẾ mặc định”; làm nổi bật dấu `/` cuối thư mục.

**Script:** “`spring.config.location` thay thế các vị trí mặc định bằng danh sách bạn cung cấp. Thư mục nên kết thúc bằng dấu gạch chéo để Boot ghép tên cơ sở. Đây là công cụ mạnh, nhưng cũng dễ làm `application.properties` ‘biến mất’ nếu bạn chỉ định một vị trí mới và quên rằng mặc định đã bị loại khỏi tập tìm kiếm.”

**Purpose:** Làm nổi bật ngữ nghĩa thay thế của `spring.config.location` và lỗi sử dụng phổ biến.

## Bổ sung vị trí tìm kiếm bằng spring.config.additional-location

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:35–05:45`

**Visual:** Khôi phục các vị trí mặc định rồi thêm thư mục `customer-config/` ở cuối.

**Script:** “Nếu mục tiêu chỉ là thêm một lớp ghi đè, Boot có một thiết lập khác phù hợp hơn.”

**Purpose:** Đối chiếu thay thế với bổ sung để giúp người học chọn đúng cơ chế.

### Scene 1 — additional-location giữ nguyên vị trí mặc định

**Time:** `05:45–06:30`

**Visual:** Terminal hiện `--spring.config.additional-location=optional:file:./customer-config/`; sơ đồ giữ nguyên các vị trí mặc định và nối thư mục mới phía sau.

**Script:** “`spring.config.additional-location` giữ các vị trí mặc định rồi bổ sung vị trí mới phía sau, nên tệp ở vị trí bổ sung có thể ghi đè cấu hình nền. Nếu môi trường triển khai chỉ muốn phủ vài giá trị lên cấu hình chuẩn, đây thường là lựa chọn dễ suy luận hơn. Hãy nhớ: `location` là thay thế, `additional-location` là bổ sung.”

**Purpose:** Khóa khác biệt vận hành giữa hai thiết lập vị trí quan trọng.

## Tệp, thư mục, mẫu wildcard và nhóm vị trí

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:30–06:40`

**Visual:** Một chuỗi vị trí được tách thành bốn dạng: tệp, thư mục, mẫu wildcard và nhóm vị trí.

**Script:** “Khi vị trí phức tạp hơn một thư mục đơn lẻ, cách Boot diễn giải từng dạng cũng ảnh hưởng hành vi cấu hình.”

**Purpose:** Mở rộng từ vị trí đơn giản sang các hình thức vị trí nâng cao.

### Scene 1 — Tệp và thư mục mang ý nghĩa khác nhau

**Time:** `06:40–07:05`

**Visual:** Đặt `file:./config/app.properties` cạnh `file:./config/`. Làm nổi bật rằng vị trí tệp chỉ định một tài nguyên cụ thể, còn vị trí thư mục cho phép Boot ghép tên cơ sở đã cấu hình vào thư mục đó.

**Script:** “Vị trí tệp chỉ định trực tiếp một tài nguyên. Vị trí thư mục lại yêu cầu Boot tìm các tên cơ sở như `application` bên trong thư mục đó, nên dấu gạch chéo cuối đường dẫn có ý nghĩa. Trước khi dùng wildcard hay nhóm vị trí, hãy xác định rõ Boot cần nạp một tệp cụ thể hay cần tìm cấu hình trong một thư mục.”

**Purpose:** Tách quyết định cơ bản giữa vị trí tệp và vị trí thư mục khỏi các quy tắc wildcard và nhóm vị trí.

### Scene 2 — Mẫu wildcard mở rộng tìm kiếm, nhóm vị trí định hình thứ tự ưu tiên

**Time:** `07:05–07:35`

**Visual:** Mở rộng ví dụ thư mục thành `file:./config/*/` và hiện hai thư mục con được phát hiện. Sau đó đưa hai vị trí có dấu `;` vào cùng một khung “cùng mức ưu tiên”, rồi dùng dấu `,` để chuyển sang nhóm kế tiếp.

**Script:** “Mẫu wildcard như `file:./config/*/` giúp phát hiện các thư mục con bên ngoài mà không phải liệt kê từng thư mục. Nhóm vị trí giải quyết một vấn đề khác: các vị trí trong cùng nhóm được xét ở cùng mức ưu tiên, còn nhóm sau tham gia ở mức kế tiếp. Mẫu wildcard thay đổi những gì được phát hiện; nhóm vị trí thay đổi cách các vị trí đã phát hiện tham gia thứ tự xử lý.”

**Purpose:** Giữ tách biệt hai khái niệm: mẫu wildcard mở rộng phạm vi tìm kiếm, còn nhóm vị trí điều khiển quan hệ thứ tự ưu tiên.

## Vì sao đầu vào xác định vị trí cấu hình phải được cung cấp sớm?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:35–07:45`

**Visual:** Quay lại trục thời gian khởi động và đặt `spring.config.name/location/additional-location` trước bước “nạp Config Data”.

**Script:** “Tất cả thiết lập vừa học đều có chung một đặc điểm: chúng quyết định chính cách Boot tìm Config Data.”

**Purpose:** Tổng hợp ba đầu vào phải có sớm thành một quy tắc vòng đời duy nhất.

### Scene 1 — Tránh cấu hình vòng tròn

**Time:** `07:45–08:35`

**Visual:** Terminal hiện `SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:./ops/`; cạnh đó là ví dụ sai: đặt `spring.config.location` trong `application.properties` rồi vẽ vòng tròn đỏ.

**Script:** “`spring.config.name`, `spring.config.location` và `spring.config.additional-location` phải có trước khi Boot tải các tệp mà chúng điều khiển. Hãy cung cấp chúng từ biến môi trường, JVM system property hoặc tham số dòng lệnh. Một tệp `application.properties` không thể đáng tin cậy để tự đổi vị trí đã dùng để tìm chính nó. Nếu vị trí tùy chỉnh bị bỏ qua, hãy kiểm tra thời điểm xuất hiện trước rồi mới kiểm tra thứ tự ưu tiên.”

**Purpose:** Chứng minh lý do các đầu vào điều khiển khám phá phải đến từ nguồn có sẵn sớm và cung cấp hướng chẩn đoán đầu tiên.
