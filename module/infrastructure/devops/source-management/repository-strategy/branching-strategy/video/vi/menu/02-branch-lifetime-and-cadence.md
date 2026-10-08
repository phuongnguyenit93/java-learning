---
video:
  url: ""
---

# Vòng đời nhánh và nhịp tích hợp

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

## Nhánh ngắn hạn so với nhánh dài hạn: mục đích và chi phí phối hợp

<!-- VIDEO_SECTION -->

### Scene 1 — Đọc nhịp làm việc thực

**Time:** `00:00–01:48`

**Visual:**

So đồ thị A: feature/discount ba commit trong một ngày, B: feature/payment hai tuần và nhiều commit lệch main; ghi số ngày bên cạnh merge-base.

Nhánh và PR là dữ liệu demo/biểu đồ có nhãn, không tuyên bố là tình trạng GitHub hiện tại của người dùng.

**Script:**

Hãy so hai nhánh cùng sửa phần checkout. Nhánh A chỉ sống một ngày, reviewer còn nhớ mục tiêu và merge-base gần main. Nhánh B sống hai tuần, nhiều người đã sửa file liên quan; trước khi merge phải đọc lại giả định cũ. Nhánh dài không tự sai, ví dụ nhánh bảo trì phiên bản có mục tiêu riêng, nhưng nhánh phát triển cô lập càng lâu thì chi phí kiểm tra tương thích càng tăng. Đo tuổi và mức chênh để thảo luận thay vì phán từ tên nhánh.

**Purpose:**

Phân biệt nhánh feature dài với nhánh bảo trì có chủ ý.

## Quan hệ giữa nhịp tích hợp, kích thước thay đổi và phản hồi sớm

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:48–02:02`

**Visual:**

Giữ hai graph branch một ngày và hai tuần, biến chúng thành lịch từ thứ Hai tới thứ Sáu với ba PR nhỏ và một PR gộp lớn; hiển thị thời gian chờ review.

**Script:**

So một nhánh một ngày với nhánh hai tuần. Thay đổi nhỏ và tích hợp sớm giúp nhận phản hồi khác thế nào?

**Purpose:**

Liên hệ tuổi nhánh với kích thước thay đổi và nhịp phản hồi thay vì chỉ đếm commits.

### Scene 1 — Đọc nhịp làm việc thực

**Time:** `02:02–03:50`

**Visual:**

Timeline thứ Hai tới thứ Sáu: 1 PR 900 dòng vào cuối tuần so với 5 PR nhỏ mỗi ngày; ghi vòng review và đồ thị main.

Nhánh và PR là dữ liệu demo/biểu đồ có nhãn, không tuyên bố là tình trạng GitHub hiện tại của người dùng.

**Script:**

Một thay đổi 900 dòng để tới thứ Sáu mới review chứa nhiều quyết định khó tách. Cùng công việc đó được chia thành các lát dọc có thể kiểm tra thì mỗi ngày main nhận phản hồi sớm; vấn đề giao diện hoặc contract xuất hiện khi còn ít code phải sửa. Đếm kích thước diff, thời gian chờ review và khoảng cách giữa các lần merge. Tích hợp thường xuyên chỉ có ích khi từng thay đổi đủ an toàn, không phải cứ đẩy một mớ code lỗi nhanh hơn.

**Purpose:**

Liên hệ change size, feedback latency và an toàn tích hợp.

## Bằng chứng nhánh phân kỳ, xung đột và tích hợp dồn cuối kỳ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:50–04:04`

**Visual:**

Giữ lịch PR lớn/ngắn, phóng đồ thị main và feature tại merge-base C; mở `git log --graph` cùng hai diff cạnh tranh ở file config.

**Script:**

Tích hợp thường xuyên có ích, nhưng hãy xem merge-base và file sửa chung để hiểu chi phí xung đột.

**Purpose:**

Biến giả định nhánh lâu nguy hiểm thành cách quan sát divergence và phân biệt conflict với lỗi ngữ nghĩa.

### Scene 1 — Đọc nhịp làm việc thực

**Time:** `04:04–05:52`

**Visual:**

Terminal demo `git log --graph --oneline --all`, `git merge-base main feature/payment`, diff tệp config hai đầu nhánh và cảnh báo conflict.

Nhánh và PR là dữ liệu demo/biểu đồ có nhãn, không tuyên bố là tình trạng GitHub hiện tại của người dùng.

**Script:**

Hãy phóng to graph khi main và feature/payment phân kỳ tại commit C. Hai người cùng thay một đoạn config theo giả định khác nhau; đến ngày merge, Git báo conflict. Cột bên cạnh là số lần cả hai phía chạm tệp và số ngày kể từ C. Không thể suy xác suất conflict chính xác chỉ từ tuổi nhánh, nhưng graph và diff cho thấy vùng cần review. Khi nhóm dồn mọi nhánh vào cuối sprint, thời gian chờ và tải reviewer cũng dồn theo.

**Purpose:**

Sử dụng merge-base và diff làm chứng cứ phân kỳ, không đoán.

## Chia nhỏ công việc và review mà không trì hoãn tích hợp

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:52–06:06`

**Visual:**

Từ diff config cạnh tranh, chia một thay đổi lớn thành ba PR: contract tương thích, consumer migration, dọn API cũ; bật thẻ review cho mỗi lát.

**Script:**

Xung đột đã hiện rõ. Nếu chia một yêu cầu lớn thành các phần nhỏ an toàn, review sẽ thay đổi ra sao?

**Purpose:**

Chứng minh cách làm giảm vùng xung đột mà vẫn giữ tính an toàn cho mỗi phần được nhập vào main.

### Scene 1 — Đọc nhịp làm việc thực

**Time:** `06:06–07:54`

**Visual:**

Ba PR minh họa cho một user story: đổi contract tương thích, thêm xử lý, sau đó dọn code; mỗi PR có diff và review timestamp.

Nhánh và PR là dữ liệu demo/biểu đồ có nhãn, không tuyên bố là tình trạng GitHub hiện tại của người dùng.

**Script:**

Không cần ép mỗi user story thành đúng một nhánh hoặc một PR khổng lồ. Trong ví dụ thay API checkout, PR đầu tạo contract tương thích, PR thứ hai đổi consumer, PR cuối dọn cách cũ sau khi đã kiểm chứng. Ba lần review nhỏ có thể vẫn liên kết cùng work item. Hãy cho thấy từng mốc đều có bài test và có thể tích hợp riêng. Nếu một bước chưa độc lập an toàn, phải tìm cách chia tiếp chứ không cố ghép vào main để chạy cho kịp lịch.

**Purpose:**

Minh họa chia nhỏ theo giá trị, không theo một nhánh mỗi story.

## Feature flags: kiểm soát tính năng chưa hoàn thiện và tích hợp từng phần

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:54–08:08`

**Visual:**

Giữ ba PR nhỏ trên main; dùng công tắc OFF/ON để cho thấy mã hoàn tiền được tích hợp nhưng UI vẫn ẩn ở trạng thái OFF, bổ sung test cả hai trạng thái.

**Script:**

Các phần nhỏ có thể chứa hành vi chưa sẵn sàng. Dùng feature flag tắt để tách tích hợp khỏi mở tính năng.

**Purpose:**

Phân biệt thay đổi đã tích hợp với thời điểm cho người dùng tiếp xúc chức năng chưa hoàn chỉnh.

### Scene 1 — Đọc nhịp làm việc thực

**Time:** `08:08–09:56`

**Visual:**

Sơ đồ `if (featureEnabled)` ở cấp ý tưởng với hai ô flag OFF/ON và bảng contract tests; main có commit nhưng UI chưa hiển thị nút mới.

Nhánh và PR là dữ liệu demo/biểu đồ có nhãn, không tuyên bố là tình trạng GitHub hiện tại của người dùng.

**Script:**

Chức năng giảm giá cần phát triển nhiều ngày. Thay vì giữ nhánh feature một tháng, nhóm có thể tích hợp dần những thay đổi tương thích phía sau feature flag đang tắt. Trên màn hình cùng một commit ở main, trạng thái OFF giữ hành vi cũ, trạng thái ON ở môi trường kiểm soát cho phép thử mới. Flag không thay kiểm thử, phân quyền hay rollout; chính sách còn phải có người sở hữu và ngày dọn flag. Chương này chỉ giải thích chiến lược, không dạy thư viện toggle cụ thể.

**Purpose:**

Giải thích flag giúp tránh nhánh lâu mà không bảo đảm code đúng.
