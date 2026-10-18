---
video:
  url: ""
---

# Tự xây dựng Custom Auto-configuration

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

## @AutoConfiguration và quy ước dành cho tác giả

<!-- VIDEO_SECTION -->

### Scene 1 — Một auto-configuration chỉ nên sở hữu một phần tích hợp rõ ràng

**Time:** `00:00–00:50`

**Visual:**

Mở đoạn mã ngắn gồm AutoConfiguration, ConditionalOnClass(AcmeClient.class), EnableConfigurationProperties(AcmeProperties.class) trên AcmeClientAutoConfiguration. Dùng chú thích chỉ ba trách nhiệm: candidate, điều kiện áp dụng, quy ước cấu hình; thêm callout `proxyBeanMethods=false` cạnh @AutoConfiguration.

**Script:**

Khi tự viết auto-configuration, hãy bắt đầu thật hẹp. Class này không phải nơi gom mọi bean tiện tay của thư viện. Nó mô tả khi nào một phần tích hợp hợp lệ và đóng góp những definition mà phần tích hợp đó sở hữu. @AutoConfiguration đặt class vào mô hình của Boot và dùng `proxyBeanMethods=false`; ConditionalOnClass bảo vệ dependency cần thiết, còn EnableConfigurationProperties nối vào quy ước cấu hình. Nhưng chỉ có annotation vẫn chưa đủ để công bố candidate.

**Purpose:**

Đặt chuẩn cho tác giả: auto-configuration tập trung, có condition rõ và phạm vi sở hữu hẹp.

## Đăng ký, ranh giới Package và Import tường minh

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:50–01:00`

**Visual:**

Từ annotation AutoConfiguration chuyển sang cây JAR có file AutoConfiguration.imports và package com.acme.boot.autoconfigure.

**Script:**

Class đã được viết đúng vẫn vô hình nếu dependency không công bố nó cho Boot. Và phần hỗ trợ cũng không nên được tìm bằng một component scan rộng.

**Purpose:**

Chuyển từ nội dung class sang đăng ký và ranh giới package.

### Scene 2 — Công bố candidate bằng imports, đưa cấu hình hỗ trợ vào bằng Import

**Time:** `01:00–01:50`

**Visual:**

Hiển thị AutoConfiguration.imports chứa AcmeClientAutoConfiguration. Bên cạnh là AutoConfiguration với Import(AcmeMetricsConfiguration.class); đặt dấu gạch trên ComponentScan rộng.

**Script:**

Candidate cần được liệt kê trong AutoConfiguration.imports. Package của nó thuộc về thư viện, không phụ thuộc base package của ứng dụng sử dụng. Nếu cần cấu hình hỗ trợ, import tường minh giúp người đọc biết chính xác nhánh nào thuộc phần tích hợp. Component scan rộng trong auto-configuration làm ranh giới mờ đi và dễ kéo nhầm class không liên quan vào context.

**Purpose:**

Cho thấy đăng ký và import tường minh tạo nên ranh giới thư viện ổn định, dễ kiểm tra.

## Tích hợp @ConfigurationProperties

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:00`

**Visual:**

Từ AcmeClientAutoConfiguration nối sang AcmeProperties với hai trường endpoint và timeout.

**Script:**

Một phần tích hợp dùng lại được thường cần đầu vào cấu hình. Nhưng ở đây ta chỉ cần phần quy ước mà auto-configuration sử dụng.

**Purpose:**

Nối việc xây dựng auto-configuration với configuration properties mà vẫn giữ đúng phạm vi sở hữu của module.

### Scene 3 — Properties là quy ước đầu vào, không phải lý do dạy lại binding

**Time:** `02:00–02:50`

**Visual:**

Hiển thị ConfigurationProperties(acme.client) trên AcmeProperties, sau đó vẽ auto-configuration nhận object này để tạo AcmeClient. Thêm chú thích: Externalized Configuration sở hữu việc nạp property, thứ tự ưu tiên, binding và validation.

**Script:**

ConfigurationProperties là cách tự nhiên để gom các giá trị cấu hình thành một quy ước có cấu trúc. Auto-configuration có thể bật type đó và dùng nó khi tạo bean, hoặc dùng một property cụ thể làm condition. Điều quan trọng ở module này là auto-configuration sử dụng quy ước đó ra sao. Cách các nguồn property được nạp, ưu tiên, bind và validate vẫn thuộc Externalized Configuration.

**Purpose:**

Giữ phần tích hợp configuration properties đúng phạm vi và tránh trùng nội dung học với module lân cận.

## Thiết kế Dependency tùy chọn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:50–03:00`

**Visual:**

Tách phần tích hợp thành phần lõi, thư viện Acme client tùy chọn và phần tích hợp metrics tùy chọn.

**Script:**

Khi starter có nhiều tính năng, không nên ép mọi dependency tùy chọn lên tất cả người dùng.

**Purpose:**

Chuyển từ quy ước cấu hình sang thiết kế dependency và cô lập khi nạp class.

### Scene 4 — Dependency tùy chọn phải tùy chọn cả khi build lẫn khi nạp class

**Time:** `03:00–03:50`

**Visual:**

Sơ đồ hai gói: acme-spring-boot có các dependency tùy chọn; acme-spring-boot-starter chọn tập phổ biến. Sau đó hiển thị cấu hình lồng có class-level ConditionalOnClass và một kiểm thử FilteredClassLoader xác nhận nhánh tùy chọn biến mất.

**Script:**

Đánh dấu dependency là tùy chọn mới chỉ giải quyết một nửa bài toán. Auto-configuration còn phải tránh liên kết sớm tới type có thể không tồn tại. Hãy cô lập nhánh tùy chọn sau class-level condition và kiểm chứng bằng FilteredClassLoader. Khi loại thư viện ra khỏi classpath mô phỏng, context phải vẫn khởi động, còn đúng nhánh tùy chọn biến mất. Starter sau đó mới là nơi đưa ra bộ dependency thuận tiện cho phần lớn người dùng.

**Purpose:**

Kết nối thiết kế dependency với condition và bằng chứng khi nạp class thay vì chỉ nhìn metadata build.

## Auto-configuration Processor và Condition Metadata

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:50–04:00`

**Visual:**

Từ annotation trong mã nguồn đi qua annotation processor tới spring-autoconfigure-metadata.properties.

**Script:**

Một auto-configuration lớn còn có thể được Boot lọc sớm. Nhưng tối ưu này không được phép thay đổi kết quả logic.

**Purpose:**

Chuyển từ hành vi lúc chạy sang lớp metadata tối ưu khởi động.

### Scene 5 — Metadata tối ưu lựa chọn nhưng không đổi ngữ nghĩa

**Time:** `04:00–04:50`

**Visual:**

Luồng annotation trong mã nguồn → annotation processor → spring-autoconfigure-metadata.properties → lọc sớm → đánh giá condition bình thường. Tô nhãn tối ưu trên metadata và ngữ nghĩa quyết định trên condition.

**Script:**

Auto-configuration annotation processor có thể tạo spring-autoconfigure-metadata.properties để Boot loại sớm một số candidate chắc chắn không khớp. Hãy xem đây là tối ưu cho bước lựa chọn, không phải một ngôn ngữ condition thứ hai. Nếu mã chỉ đúng khi metadata được sinh ra thì thiết kế đã có vấn đề; condition trong mã nguồn vẫn phải mô tả đúng quy tắc áp dụng.

**Purpose:**

Phân biệt metadata tối ưu với ngữ nghĩa thực sự của condition.

## Danh tính class ổn định cho Ordering và Exclusion

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:50–05:00`

**Visual:**

Từ metadata chuyển sang các mũi tên before, after, exclude và excludeName cùng trỏ vào AcmeClientAutoConfiguration.

**Script:**

Dù ứng dụng hiếm khi khởi tạo auto-configuration trực tiếp, nhiều cấu hình khác vẫn tham chiếu chính danh tính class của nó.

**Purpose:**

Khép phần dành cho tác giả bằng yêu cầu giữ danh tính class ổn định cho ordering và exclusion.

### Scene 6 — Tên class auto-configuration là một phần của quy ước công khai

**Time:** `05:00–05:50`

**Visual:**

Hiển thị package/tên class hiện tại, rồi mô phỏng đổi tên làm đứt before/after và excludeName. Đặt nhãn Boot 3.3: không có cơ chế AutoConfiguration.replacements tổng quát.

**Script:**

Class auto-configuration có thể trở thành mục tiêu của ordering hoặc exclusion, nên package và tên class là một phần của quy ước phần tích hợp. Với mốc Spring Boot 3.3 của module này, không có cơ chế AutoConfiguration.replacements tổng quát để tự ánh xạ mọi tên cũ sang tên mới. Vì vậy đổi tên hoặc di chuyển cần kế hoạch tương thích. Sau khi danh tính class ổn định, bước tiếp theo là chứng minh toàn bộ hành vi bằng kiểm thử context tập trung.

**Purpose:**

Nêu rõ ranh giới phiên bản và lý do cần giữ danh tính class ổn định cho ứng dụng sử dụng phần tích hợp.
