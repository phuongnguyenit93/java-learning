---
video:
  url: ""
---

# Thiết kế Starter và tổng hợp

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

## Starter bổ sung gì cho Auto-configuration?

<!-- VIDEO_SECTION -->

### Scene 1 — Starter đóng gói dependency, auto-configuration đóng gói hành vi

**Time:** `00:00–00:50`

**Visual:**

Sơ đồ starter dependency → các dependency điển hình → AutoConfiguration.imports → condition → thiết lập mặc định. Tách màu starter ở phần dependency và auto-configuration ở phần hành vi có điều kiện.

**Script:**

Starter và auto-configuration thường đi cùng nhau nên rất dễ bị xem là một thứ. Thực ra vai trò khác nhau. Starter làm việc chọn dependency trở nên thuận tiện; auto-configuration chứa hành vi có điều kiện. Khi ứng dụng thêm một starter, nó nhận bộ thư viện điển hình, từ đó candidate auto-configuration trở nên có sẵn và Boot mới bắt đầu quá trình lựa chọn.

**Purpose:**

Đặt ranh giới cốt lõi giữa sự thuận tiện khi chọn dependency và hành vi cấu hình có điều kiện.

## Tách Autoconfigure và Starter hay dùng một Starter kết hợp?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:50–01:00`

**Visual:**

Từ một starter duy nhất tách thành acme-spring-boot và acme-spring-boot-starter; sau đó cho phép ghép lại ở nhánh tích hợp đơn giản.

**Script:**

Biết hai vai trò khác nhau không có nghĩa lúc nào cũng phải tạo hai gói riêng.

**Purpose:**

Chuyển từ khái niệm vai trò sang đánh đổi về cấu trúc package/module.

### Scene 2 — Tách module khi cần tính tùy chọn, gộp khi phần tích hợp đơn giản

**Time:** `01:00–01:50`

**Visual:**

Bảng so sánh: tách khi có dependency tùy chọn, nhiều lựa chọn starter hoặc người dùng nâng cao; gộp khi phần tích hợp đơn giản và ít nhánh tùy chọn.

**Script:**

Mô hình tách autoconfigure và starter rất hữu ích khi dependency tùy chọn hoặc nhiều cách dùng phổ biến bắt đầu xuất hiện. Người dùng nâng cao có thể lấy gói autoconfigure mà không nhận toàn bộ lựa chọn dependency của starter. Nhưng với phần tích hợp nhỏ, ít tính năng tùy chọn, Boot không bắt buộc phải chia thành hai module. Hãy chọn cấu trúc theo nhu cầu mở rộng, không theo nghi thức đặt tên.

**Purpose:**

Giúp người học chọn tách/gộp theo dependency và nhu cầu mở rộng thay vì quy tắc cứng.

## Quy tắc đặt tên Starter và quyền sở hữu Package

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:00`

**Visual:**

Từ hai gói chuyển sang nhãn tên của bên thứ ba và vùng package của thư viện.

**Script:**

Dù tách hay gộp, người dùng vẫn cần nhìn tên gói và package để biết ai sở hữu phần tích hợp.

**Purpose:**

Nối cấu trúc starter với danh tính công khai của thư viện.

### Scene 3 — Tên và package phải thể hiện đúng quyền sở hữu

**Time:** `02:00–02:50`

**Visual:**

Hiển thị acme-spring-boot-starter là mẫu của bên thứ ba; đặt dấu tránh trên tên bắt đầu như module chính thức của Spring Boot. Bên dưới, package com.acme.boot.autoconfigure nằm tách khỏi package ứng dụng.

**Script:**

Starter bên thứ ba nên dùng namespace của chính thư viện thay vì tạo cảm giác đây là module chính thức do Spring Boot sở hữu. Package cũng vậy: auto-configuration nằm dưới package của thư viện, không nằm dưới package ứng dụng sử dụng thư viện. Danh tính ổn định còn giúp before, after và exclusion không bị vỡ khi phần tích hợp phát triển.

**Purpose:**

Kết nối quy tắc đặt tên với phạm vi sở hữu và độ ổn định của phần tích hợp.

## Namespace của Configuration Key và Metadata

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:50–03:00`

**Visual:**

Từ namespace của gói chuyển xuống các key acme.client.endpoint, acme.client.timeout và acme.client.enabled.

**Script:**

Quyền sở hữu không chỉ nằm ở package. Các key cấu hình công khai cũng là một phần của bề mặt phần tích hợp.

**Purpose:**

Chuyển từ cách đặt tên gói/class sang cách đặt tên quy ước cấu hình.

### Scene 4 — Configuration key cũng cần namespace riêng và metadata hữu ích

**Time:** `03:00–03:50`

**Visual:**

Danh sách key acme.client.* cạnh configuration metadata hiển thị mô tả và type trong IDE. Đặt cảnh báo trên việc chiếm namespace spring, server hoặc management cho key của bên thứ ba.

**Script:**

Configuration key của starter nên dùng namespace do thư viện sở hữu, ví dụ acme.client. Tránh đặt key bên thứ ba vào các namespace Boot quản lý như spring, server hay management. Metadata cấu hình nên giúp IDE hiển thị type và mô tả đúng với quy ước công khai. Cơ chế binding chi tiết vẫn thuộc Externalized Configuration; ở đây điều quan trọng là namespace rõ và ít xung đột.

**Purpose:**

Cho thấy namespace cấu hình là một phần của bề mặt thiết kế starter mà không lấn sang ngữ nghĩa binding.

## Lựa chọn Dependency có chủ đích và tính năng tùy chọn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:50–04:00`

**Visual:**

Đặt nhiều thư viện tùy chọn quanh một hộp starter; chỉ một tập phổ biến được kéo vào mặc định.

**Script:**

Khi đã có namespace và cấu trúc rõ, câu hỏi cuối về dependency là: người dùng nên nhận những gì chỉ bằng một lần thêm starter?

**Purpose:**

Nối quy ước công khai của starter với lựa chọn dependency mặc định.

### Scene 5 — Starter là lựa chọn Dependency có chủ đích

**Time:** `04:00–04:50`

**Visual:**

Sơ đồ auto-configuration ở giữa dùng condition; starter A đưa phần lõi + metrics phổ biến, starter B đưa phần lõi + tracing, người dùng nâng cao chỉ dùng autoconfigure và tự chọn dependency.

**Script:**

Starter thể hiện một lựa chọn dependency có chủ đích: bộ thư viện mà phần lớn người dùng nên có để bắt đầu. Đừng vì auto-configuration có condition cho một công nghệ mà ép công nghệ đó vào mọi starter. Khi có nhiều tính năng tùy chọn, có thể có nhiều starter cho các tổ hợp phổ biến hoặc để người dùng dùng autoconfigure trực tiếp. Condition quyết định hành vi; starter quyết định tập dependency thuận tiện.

**Purpose:**

Phân biệt rõ lựa chọn dependency với hành vi có điều kiện và tránh ép công nghệ tùy chọn lên mọi người dùng.

## Từ Starter Dependency đến ứng dụng đã được cấu hình

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:50–05:00`

**Visual:**

Các mảnh đã học được ghép thành một luồng dài từ lúc thêm starter đến kiểm thử context tập trung.

**Script:**

Bây giờ ta có thể nối mọi chương thành một luồng duy nhất và kiểm tra xem mỗi lớp chịu trách nhiệm ở đâu.

**Purpose:**

Tổng hợp khám phá candidate, ordering, condition, back-off, chẩn đoán và kiểm thử vào một mô hình tư duy hoàn chỉnh.

### Scene 6 — Theo dấu toàn bộ đường đi từ Dependency đến bằng chứng

**Time:** `05:00–05:50`

**Visual:**

Luồng: thêm starter → dependency → AutoConfiguration.imports → khám phá candidate → ordering → condition → bean definition → back-off → Condition Evaluation Report → bằng chứng từ ApplicationContextRunner. Mỗi bước hiện một biểu tượng hoặc nguồn tương ứng.

**Script:**

Ứng dụng thêm starter và nhận các dependency điển hình. JAR công bố AutoConfiguration.imports, Boot tìm candidate và phối hợp ordering. Condition nhìn classpath, property, bean và context để chọn cấu hình phù hợp. Cấu hình khớp đóng góp bean definition; lựa chọn của người dùng làm bean mặc định back off. Khi cần giải thích quyết định, đọc Condition Evaluation Report. Khi cần chứng minh quy tắc, dựng kiểm thử context tập trung. Đây là đường đi đầy đủ mà không cần một HTTP API giả để minh họa.

**Purpose:**

Tạo phần tổng hợp từ đầu đến cuối và nhấn mạnh loại bằng chứng phù hợp của module.

## Ranh giới và hướng học tiếp theo

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:50–06:00`

**Visual:**

Luồng hoàn chỉnh thu nhỏ lại, sau đó tỏa ra năm nhánh: Core Container, Externalized Configuration, Testing, Build Tooling, Native Image.

**Script:**

Một mô hình tư duy tốt cũng phải biết nơi nào không còn thuộc auto-configuration nữa.

**Purpose:**

Khép module bằng ranh giới rõ và chỉ hướng học tiếp theo.

### Scene 7 — Biết điểm dừng để học tiếp đúng module

**Time:** `06:00–06:50`

**Visual:**

Bản đồ bàn giao: cơ chế bean/container → Spring Framework core container; nạp property/binding → Externalized Configuration; kiểm thử Boot tổng quát → Testing; BOM/plugin/packaging → Build Tooling; AOT/native → Native Image. Ở giữa giữ câu tổng kết ngắn của module.

**Script:**

Module này dừng ở hành vi auto-configuration của Boot. Nếu câu hỏi chuyển sang Spring tạo và quản lý bean thế nào, hãy sang core container. Nếu hỏi property được nạp và bind thế nào, sang Externalized Configuration. Chiến lược kiểm thử tổng quát, build tooling và native image cũng có module riêng. Điều cần mang theo là chuỗi: starter làm khả năng tích hợp xuất hiện, cơ chế khám phá tìm candidate, condition chọn, back-off giữ quyền kiểm soát, còn chẩn đoán và kiểm thử context tập trung biến quyết định thành bằng chứng.

**Purpose:**

Giữ ranh giới sở hữu rõ và để người học rời module với mô hình tư duy có thể tái sử dụng.
