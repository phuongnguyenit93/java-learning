---
video:
  url: ""
---

# Kích hoạt và khám phá Candidate

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

## @SpringBootApplication và @EnableAutoConfiguration

<!-- VIDEO_SECTION -->

### Scene 1 — Một annotation, nhiều trách nhiệm

**Time:** `00:00–00:50`


**Visual:**

Hiển thị SpringBootApplication rồi tách thành ba nhánh: cấu hình ứng dụng, component scanning và EnableAutoConfiguration. Chỉ tô sáng nhánh auto-configuration.

**Script:**

Trong ứng dụng Boot thông thường, bạn không cần thêm EnableAutoConfiguration riêng vì SpringBootApplication đã bao gồm nó. Nhưng đừng gộp mọi thứ thành một ý. Component scanning tìm component phía ứng dụng; cơ chế lựa chọn auto-configuration tìm cấu hình mà dependency công bố. Hai cơ chế cùng được bật từ SpringBootApplication nhưng trả lời hai câu hỏi khác nhau.

**Purpose:**

Tách việc kích hoạt auto-configuration khỏi component scanning ngay từ đầu.

## Khám phá Candidate không đồng nghĩa với tạo Bean

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:50–01:00`


**Visual:**

Từ nhánh EnableAutoConfiguration chuyển sang một hộp chứa nhiều candidate, trong đó chỉ vài candidate được đánh dấu xanh.

**Script:**

Bật auto-configuration chỉ mở quy trình lựa chọn. Nó chưa nói candidate nào cuối cùng sẽ đóng góp bean.

**Purpose:**

Nối việc kích hoạt với hai giai đoạn riêng: khám phá candidate và đánh giá condition.

### Scene 2 — Tìm thấy trước, quyết định sau

**Time:** `01:00–01:50`


**Visual:**

Hai cột Candidate đã được khám phá và Cấu hình được áp dụng. Đưa AcmeClientAutoConfiguration vào cột đầu, cho một property condition không khớp và giữ cột sau trống. Hiện danh sách kiểm tra: đã được khám phá, condition khớp, bị loại trừ và back-off.

**Script:**

Bước khám phá chỉ trả lời cấu hình nào đang có mặt để Boot xem xét. Sau đó condition mới trả lời cấu hình nào phù hợp với ứng dụng này. Một candidate có thể được tìm thấy bình thường nhưng vẫn không tạo bean vì thiếu class, property đang tắt, thiếu bean cộng tác, bị back-off hoặc bị loại trừ. Khi chẩn đoán, hãy hỏi theo từng tầng thay vì thấy thiếu bean rồi kết luận ngay rằng bước khám phá bị lỗi.

**Purpose:**

Ngăn suy luận sai rằng không có bean đồng nghĩa candidate chưa được khám phá.

## AutoConfiguration.imports và ImportCandidates

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:00`


**Visual:**

Phóng to câu hỏi candidate đã được khám phá chưa rồi chuyển sang cây thư mục của một dependency JAR.

**Script:**

Vậy Boot biết các candidate bên trong dependency bằng cách nào? Ta cần nhìn vào chỉ mục mà thư viện công bố.

**Purpose:**

Chuyển từ khái niệm khám phá sang cơ chế đăng ký candidate trong Boot 3.3.

### Scene 3 — Chỉ mục candidate trong JAR

**Time:** `02:00–02:50`


**Visual:**

Mở META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports với hai class name. Vẽ ImportCandidates đọc file và đưa các tên vào tập candidate, chưa qua condition.

**Script:**

Trong Boot 3.3, thư viện công bố auto-configuration qua file AutoConfiguration.imports dưới META-INF/spring. Mỗi dòng hợp lệ là tên đầy đủ của một auto-configuration class. Hạ tầng ImportCandidates đọc danh sách khi auto-configuration được bật. Hãy xem file này như một chỉ mục khám phá: nó nói hãy xem xét các class này, chứ không nói hãy tạo bean từ tất cả các class này.

**Purpose:**

Cho thấy bằng chứng cấu trúc cụ thể của bước khám phá và giữ ranh giới với bước condition khớp.

## Package Auto-configuration, Component Scanning và Explicit Import

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:50–03:00`


**Visual:**

Từ file imports kéo AcmeClientAutoConfiguration sang package com.acme.boot.autoconfigure; làm mờ vùng component scan của ứng dụng.

**Script:**

Khi đã có một chỉ mục khám phá riêng, auto-configuration không cần nằm dưới package của ứng dụng để được component scan tìm thấy.

**Purpose:**

Nối chỉ mục khám phá với nguyên tắc cô lập package và import tường minh.

### Scene 4 — Ranh giới package phải rõ

**Time:** `03:00–03:50`


**Visual:**

Hiển thị AutoConfiguration cùng Import(AcmeClientConfiguration.class). Bên cạnh là dấu cảnh báo trên một ComponentScan rộng. Sơ đồ cho thấy cấu hình hỗ trợ được import tường minh.

**Script:**

Auto-configuration của thư viện nên sống trong package do thư viện sở hữu và được nạp qua AutoConfiguration.imports. Nếu cần cấu hình hỗ trợ, ưu tiên Import tường minh thay vì quét một vùng package rộng. Cách này cho người đọc thấy chính xác phần nào thuộc phần tích hợp và tránh vô tình kéo component không liên quan vào context.

**Purpose:**

Thể hiện cách đăng ký và cô lập package giúp phần tích hợp dễ kiểm soát và dễ kiểm tra.

## Base Package của ứng dụng và ranh giới với cơ chế khám phá Auto-configuration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:50–04:00`


**Visual:**

Vẽ hai cây package độc lập: com.example.myapp và com.acme.boot.autoconfigure.

**Script:**

Điểm cuối cùng là tách hai địa chỉ: base package của ứng dụng và package của auto-configuration bên ngoài.

**Purpose:**

Chốt ranh giới giữa việc quét phía ứng dụng và cơ chế khám phá auto-configuration bên ngoài.

### Scene 5 — Hai đường tìm kiếm độc lập

**Time:** `04:00–04:50`


**Visual:**

Bên trái, class SpringBootApplication mở vùng component scan phía ứng dụng. Bên phải, dependency JAR trỏ từ AutoConfiguration.imports đến AcmeClientAutoConfiguration. Không có quan hệ package cha-con giữa hai phía.

**Script:**

Package chứa class SpringBootApplication thường là mốc cho nhiều quy ước tìm kiếm phía ứng dụng. Nhưng auto-configuration của dependency đi theo đường khác: JAR công bố candidate trong AutoConfiguration.imports. Vì thế com.acme.boot không cần nằm dưới com.example.myapp. Khi đã tách hai đường này, câu hỏi tiếp theo mới là candidate nào thực sự khớp — đó là vai trò của condition.

**Purpose:**

Khóa lại mô hình tư duy về khám phá candidate và tạo cầu nối tự nhiên sang mô hình condition.
