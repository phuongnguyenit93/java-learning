---
video:
  url: ""
---

# Loại trừ và chẩn đoán

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

## Khi nào nên loại trừ Auto-configuration?

<!-- VIDEO_SECTION -->

### Scene 1 — Back-off và loại trừ là hai mức can thiệp khác nhau

**Time:** `00:00–00:50`

**Visual:**

Hai nút lựa chọn: Thay một mặc định dẫn tới back-off; Tắt toàn bộ auto-configuration dẫn tới loại trừ. Dùng cùng phần tích hợp Acme để so sánh.

**Script:**

Không phải mọi nhu cầu tùy biến đều cần loại trừ. Nếu ứng dụng vẫn muốn phần tích hợp nhưng chỉ thay một bean, back-off hoặc property thường là công cụ hẹp hơn. Loại trừ phù hợp khi ứng dụng không muốn một auto-configuration cụ thể tham gia hoàn toàn. Chọn đúng mức can thiệp giúp cấu hình dễ đọc và tránh tắt nhiều hơn mức cần thiết.

**Purpose:**

Phân biệt khi nào nên thay một lựa chọn và khi nào nên loại bỏ cả candidate.

## Loại trừ theo Class, Name và Property

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:50–01:00`

**Visual:**

Từ nút loại trừ mở thành ba nhánh class, name và spring.autoconfigure.exclude.

**Script:**

Khi đã quyết định loại trừ, Boot cho vài cách biểu đạt tùy vào nơi ta muốn đặt quyết định.

**Purpose:**

Chuyển từ quyết định thiết kế sang các cơ chế loại trừ cụ thể.

### Scene 2 — Ba con đường loại trừ

**Time:** `01:00–01:50`

**Visual:**

Hiển thị EnableAutoConfiguration exclude, excludeName và property spring.autoconfigure.exclude. Ghi chú dạng class cần type có mặt lúc biên dịch; dạng name tránh tham chiếu trực tiếp; property đưa quyết định ra cấu hình ngoài.

**Script:**

Có ba dạng thường gặp. Loại trừ theo class rõ và an toàn về type khi mã nguồn nhìn thấy auto-configuration class. Loại trừ theo name tránh một tham chiếu trực tiếp lúc biên dịch. Property spring.autoconfigure.exclude cho phép đưa quyết định ra externalized configuration. Điểm chung là candidate bị loại khỏi quá trình tham gia; điều này khác với candidate vẫn tồn tại nhưng một condition tự đánh giá false.

**Purpose:**

Cho người học thấy cách loại trừ tương ứng với mức phụ thuộc và vị trí cấu hình.

## Condition Evaluation Report

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:00`

**Visual:**

Từ ba cơ chế loại trừ chuyển sang một Condition Evaluation Report với các condition khớp và không khớp được tô màu.

**Script:**

Dù dùng cơ chế nào, lúc hành vi khác mong đợi ta cần bằng chứng vì sao candidate có hoặc không tham gia.

**Purpose:**

Nối loại trừ với công cụ chẩn đoán trung tâm của Boot.

### Scene 3 — Condition Evaluation Report biến suy đoán thành điều kiện cụ thể

**Time:** `02:00–02:50`

**Visual:**

Mở report cho AcmeClientAutoConfiguration. Làm nổi bật lần lượt class condition, property condition, missing-bean condition và trạng thái loại trừ. Bên cạnh là kết quả cuối: bean có hoặc không.

**Script:**

Condition Evaluation Report ghi lại lý do condition khớp hoặc không khớp. Thay vì hỏi chung chung vì sao Boot không tạo bean, ta có thể lần từng điều kiện: candidate có được xét không, class có mặt không, property có đúng không, missing-bean condition có thấy bean người dùng không, và candidate có bị loại trừ không. Report là bằng chứng của quá trình lựa chọn, không phải một bản thay thế cho việc hiểu condition.

**Purpose:**

Biến chẩn đoán auto-configuration thành quy trình dựa trên bằng chứng thay vì đoán.

## Thông tin chẩn đoán từ Boot Debug

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:50–03:00`

**Visual:**

Thu nhỏ report dài thành log terminal rồi bật cờ debug; chỉ làm nổi bật tên AcmeClientAutoConfiguration.

**Script:**

Report có thể rất dài. Mục tiêu không phải đọc tất cả, mà là tìm đúng candidate và condition liên quan.

**Purpose:**

Chuyển từ nội dung report sang cách sử dụng thực tế khi chẩn đoán lúc khởi động.

### Scene 4 — Dùng debug để tìm đúng phần report

**Time:** `03:00–03:50`

**Visual:**

Terminal hiển thị Condition Evaluation Report khi debug bật. Dùng tìm kiếm/làm nổi bật tên auto-configuration, sau đó mở dòng condition không khớp cụ thể.

**Script:**

Khi bật debug, Boot có thể ghi Condition Evaluation Report ra log. Đừng cuộn toàn bộ như đọc một bức tường chữ. Hãy bắt đầu từ auto-configuration mà bạn mong đợi, rồi đi ra condition giải thích trạng thái của nó. Cách đọc có mục tiêu giúp phân biệt nhanh lỗi classpath, property, bean người dùng, loại trừ hay giả định ordering.

**Purpose:**

Dạy cách khai thác report hiệu quả khi điều tra quá trình khởi động.

## Chẩn đoán Candidate khớp ngoài dự kiến và Bean bị thiếu

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:50–04:00`

**Visual:**

Từ một dòng condition không khớp vẽ luồng 1 đến 7 kết thúc ở ApplicationContextRunner.

**Script:**

Sau khi biết report nằm ở đâu, ta cần một thứ tự điều tra để không nhảy ngẫu nhiên giữa mã nguồn, property và bean.

**Purpose:**

Tổng hợp việc chẩn đoán thành quy trình có thể lặp lại.

### Scene 5 — Quy trình từ dấu hiệu lỗi đến ca tái hiện nhỏ nhất

**Time:** `04:00–04:50`

**Visual:**

Danh sách tuần tự: dependency/candidate, loại trừ, condition ở mức cấu hình, bean condition, property/loại context, ordering, kiểm thử context tập trung. Cuối cùng hiển thị ApplicationContextRunner tái hiện đúng trạng thái lỗi.

**Script:**

Khi bean thiếu hoặc xuất hiện ngoài dự kiến, hãy đi từ ngoài vào. Xác nhận dependency và candidate, kiểm tra loại trừ, rồi condition ở mức cấu hình, bean condition, property và loại context, sau đó mới xem ordering. Với custom auto-configuration, ApplicationContextRunner thường là ca tái hiện nhỏ nhất. Nếu test và ứng dụng thật cho kết quả khác, hãy so sánh classpath, property, cấu hình người dùng và loại context thay vì thêm log ngẫu nhiên.

**Purpose:**

Cung cấp quy trình chẩn đoán nhất quán và kết thúc bằng bằng chứng thực thi nhỏ, kiểm soát được.
