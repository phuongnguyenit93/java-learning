---
video:
  url: ""
---

# Environment, placeholder và @Value

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

## Đọc giá trị đã phân giải qua Environment

<!-- VIDEO_SECTION -->

### Scene 1 — Environment trả về giá trị có hiệu lực

**Time:** `00:00–00:50`

**Visual:** Mở đoạn constructor dùng `environment.getProperty("shop.currency", "USD")`. Bên cạnh hiện `application.properties: USD` và tham số dòng lệnh `--shop.currency=EUR`; kết quả `EUR` được làm nổi bật.

**Script:** “Sau khi Boot đã ghép và sắp thứ tự các nguồn, `Environment` cho mã ứng dụng đọc giá trị có hiệu lực. Nếu tệp nói `USD` nhưng tham số dòng lệnh nói `EUR`, lời gọi `getProperty` thấy `EUR`. `Environment` phù hợp với tra cứu thực sự mang tính động hoặc hạ tầng; nếu ta liên tục tự đọc một nhóm khóa liên quan thì đó thường là dấu hiệu nên chuyển sang `@ConfigurationProperties`.”

**Purpose:** Chứng minh `Environment` tiêu thụ kết quả sau khi áp dụng thứ tự ưu tiên và đặt ranh giới sử dụng trực tiếp.

## Placeholder ${...} được phân giải như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:50–01:00`

**Visual:** Giá trị `EUR` từ Environment được kéo vào một biểu thức `${...}`.

**Script:** “Không phải bên tiêu thụ nào cũng gọi `Environment` trực tiếp; Spring còn cho ta tham chiếu khóa qua placeholder.”

**Purpose:** Nối tra cứu trực tiếp với việc phân giải placeholder trên cùng nguồn dữ liệu đã được phân giải.

### Scene 1 — Placeholder đọc từ Environment, không tạo nguồn mới

**Time:** `01:00–01:45`

**Visual:** Hiện `app.name=orders` và `app.description=${app.name} service`; hiệu ứng thay placeholder thành `orders service`. Sơ đồ cho thấy không có `PropertySource` mới được tạo.

**Script:** “Placeholder `${...}` yêu cầu bộ phân giải lấy giá trị từ `Environment`. Một thuộc tính có thể tham chiếu thuộc tính khác, như `app.description=${app.name} service`. Placeholder không tạo nguồn mới và không thay đổi thứ tự ưu tiên; nó chỉ đọc giá trị đã phân giải khi bên tiêu thụ cần.”

**Purpose:** Tách rõ việc phân giải placeholder khỏi việc khám phá nguồn và thứ tự ưu tiên.

## Giá trị mặc định của placeholder và trường hợp thiếu giá trị

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:45–01:55`

**Visual:** Khóa được tham chiếu biến mất khỏi `Environment`; biểu thức placeholder vẫn còn phần sau dấu `:`.

**Script:** “Nếu khóa không tồn tại, placeholder có thể tự mang một giá trị dự phòng nhỏ.”

**Purpose:** Dẫn từ placeholder thông thường sang ngữ nghĩa của giá trị mặc định.

### Scene 1 — Giá trị dự phòng tiện lợi nhưng có thể che thiếu cấu hình

**Time:** `01:55–02:40`

**Visual:** Hiện `service.region=${REGION_NAME:local}`; khi `REGION_NAME` vắng, `local` xuất hiện. Bên cạnh là đường khác “bắt buộc → binding có kiểu + xác thực”.

**Script:** “Phần sau dấu hai chấm là giá trị dự phòng, ví dụ `${REGION_NAME:local}`. Nó chỉ được dùng khi khóa không phân giải được; nó không trở thành một thuộc tính mới trong `Environment`. Giá trị dự phòng phù hợp với mặc định nhỏ và an toàn. Nếu thiếu giá trị phải làm khởi động thất bại, hãy dùng hợp đồng binding và xác thực thay vì âm thầm chọn giá trị mặc định.”

**Purpose:** Giúp người học chọn giữa giá trị dự phòng tiện dụng và cơ chế thất bại sớm cho cấu hình bắt buộc.

## Tiêm giá trị đơn lẻ bằng @Value

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:40–02:50`

**Visual:** Placeholder được đặt vào tham số constructor có annotation `@Value`.

**Script:** “Khi chỉ một thành phần cần một giá trị đơn lẻ, placeholder thường xuất hiện trực tiếp qua `@Value`.”

**Purpose:** Chuyển từ cú pháp placeholder sang một bên tiêu thụ cụ thể của Spring container.

### Scene 1 — @Value cho cấu hình nhỏ, cục bộ

**Time:** `02:50–03:35`

**Visual:** Mở `Banner(@Value("${app.title:Demo}") String title)`; đổi nguồn thắng của `app.title` và cho thấy giá trị constructor thay đổi theo.

**Script:** “`@Value` nhờ Spring container phân giải placeholder hoặc biểu thức rồi tiêm kết quả vào trường, constructor hay tham số phương thức. Giá trị vẫn đến từ `Environment`, nên nguồn thắng theo thứ tự ưu tiên thay đổi thì giá trị được tiêm cũng đổi. Cơ chế này gọn cho một vài giá trị đơn lẻ cục bộ; một không gian tên lớn sẽ rõ ràng hơn khi binding thành đối tượng có kiểu.”

**Purpose:** Đặt `@Value` đúng trường hợp sử dụng và nhấn mạnh nó không có thứ tự ưu tiên riêng.

## Vì sao tên thuộc tính kebab-case chuẩn quan trọng?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:35–03:45`

**Visual:** Hai placeholder `${demo.item-price}` và `${demo.itemPrice}` xuất hiện cạnh một biến môi trường tương ứng.

**Script:** “Cách viết khóa trong placeholder cũng ảnh hưởng khả năng Spring tìm các dạng tên tương ứng.”

**Purpose:** Nối cách dùng placeholder với quy ước tên thuộc tính chuẩn.

### Scene 1 — Viết khóa chuẩn để tra cứu linh hoạt nhất

**Time:** `03:45–04:25`

**Visual:** Làm nổi bật `demo.item-price` là dạng chuẩn; hiệu ứng ánh xạ sang dạng biến môi trường. Dạng camelCase trong placeholder được gắn cảnh báo.

**Script:** “Boot khuyến nghị khóa chuẩn bằng kebab-case chữ thường, như `demo.item-price`, và cũng nên tham chiếu đúng dạng đó trong placeholder. Dạng chuẩn cho phép tra cứu linh hoạt nhất qua các `PropertySource` được hỗ trợ, kể cả cách viết vật lý khác của biến môi trường. Hãy coi tên chuẩn là hợp đồng công khai của thuộc tính.”

**Purpose:** Củng cố quy ước đặt tên giúp placeholder và công cụ phát triển hoạt động nhất quán.

## @Value và @ConfigurationProperties

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:25–04:35`

**Visual:** Một giá trị đơn lẻ qua `@Value` bên trái và một đối tượng `@ConfigurationProperties` chứa cấu trúc lồng nhau/list/map bên phải.

**Script:** “Khi số lượng cấu hình tăng, câu hỏi không còn là lấy được giá trị hay không mà là nên biểu diễn hợp đồng đó thế nào.”

**Purpose:** Chuyển từ tra cứu cục bộ sang mô hình cấu hình có kiểu.

### Scene 1 — Chọn cách tiêu thụ theo hình dạng cấu hình

**Time:** `04:35–05:25`

**Visual:** Bảng hai cột: giá trị đơn lẻ/SpEL → `@Value`; không gian tên, cấu trúc lồng nhau, list/map, xác thực, metadata → `@ConfigurationProperties`.

**Script:** “Cả `@Value` và `@ConfigurationProperties` đều nhận dữ liệu sau khi thứ tự ưu tiên đã được quyết định. `@Value` hợp với giá trị đơn lẻ cục bộ và có SpEL. `@ConfigurationProperties` hợp với nhóm thiết lập có cấu trúc, relaxed binding, xác thực và metadata. Chọn dựa trên hình dạng và trách nhiệm của cấu hình, không dựa trên ý nghĩ annotation nào ‘mạnh’ hơn.”

**Purpose:** Chuẩn bị mô hình tư duy cho chương binding có kiểu tiếp theo.

## SpEL và ranh giới của @Value

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:25–05:35`

**Visual:** Từ cột `@Value`, phóng lớn nhãn “SpEL”; Config Data được giữ riêng ở phía trước.

**Script:** “Một khả năng riêng của `@Value` là đánh giá SpEL, và chính điểm này cần một ranh giới rõ.”

**Purpose:** Tách việc đánh giá biểu thức khỏi Config Data và quá trình binding dữ liệu.

### Scene 1 — Biểu thức được bên tiêu thụ đánh giá

**Time:** `05:35–06:20`

**Visual:** Một chuỗi giống SpEL nằm trong `application.properties`, sau đó chỉ khi đi qua `@Value` mới được đánh giá. Nhánh `@ConfigurationProperties` giữ dữ liệu thuần.

**Script:** “Một chuỗi trông giống SpEL trong Config Data không tự chạy biểu thức. Việc đánh giá xảy ra khi bên tiêu thụ như `@Value` diễn giải giá trị. `@ConfigurationProperties` chủ ý không hỗ trợ SpEL vì nó tập trung vào binding dữ liệu. Với cấu hình cần dễ quan sát và di chuyển giữa môi trường, dữ liệu thuần cùng binding có kiểu thường rõ ràng hơn.”

**Purpose:** Ngăn nhầm Config Data với bộ máy đánh giá biểu thức và chốt ranh giới của `@Value`.
