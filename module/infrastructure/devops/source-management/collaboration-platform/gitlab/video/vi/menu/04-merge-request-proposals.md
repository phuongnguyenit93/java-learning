---
video:
  url: ""
---

# Đề xuất thay đổi bằng merge request

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

## Đề xuất từ nhánh trong cùng project

<!-- VIDEO_SECTION -->

### Scene 1 — Đề xuất từ nhánh trong cùng project

**Time:** `00:00–01:12`

**Visual:**

Dựng project `company/payments/gateway` với main và nhánh fix-rounding. Chỉ tô diff trên nhánh làm việc; mũi tên MR !57 hướng về main chưa nhập mã.

**Script:**

An có quyền phù hợp trên project chung và muốn sửa lỗi Issue #42. Thay vì thay main trực tiếp, An đề xuất từ nhánh fix-rounding để Bình có thể đọc diff và đặt câu hỏi trước khi nhận mã. Việc tạo và đẩy nhánh vẫn phụ thuộc role và các protected branch rule đang áp dụng, chứ Developer không có nghĩa được push mọi nơi. Hãy chỉ vào đầu mũi tên MR: đó là nơi nhóm muốn tích hợp, không phải kết quả đã xảy ra. Chúng ta đang học quy trình GitLab, không viết lại lệnh Git.

**Purpose:**

Cho thấy giá trị của branch contribution có review mà không lấn vào cách thao tác Git CLI.

## Fork là project riêng và cách đóng góp về upstream

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:12–01:25`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "nhánh làm việc trong project chung". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Không phải contributor nào cũng có quyền Write tại upstream; fork mở một đường gửi đề xuất khác.

**Purpose:**

Chuyển từ nhánh làm việc trong project chung sang câu hỏi fork là project riêng có quyền riêng theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Fork là project riêng và cách đóng góp về upstream

**Time:** `01:25–02:33`

**Visual:**

Phác gateway gốc ở `company/payments`, fork ở `contractor/gateway`, mỗi hộp có namespace và Members riêng. Mũi tên MR đi ngược về upstream main, không vẽ push trực tiếp vào upstream.

**Script:**

Nếu contractor không có quyền đẩy lên project gốc, fork có thể là đường đóng góp khi visibility và chính sách cho phép. Fork là một project mới, không phải thêm nhánh trong project gateway gốc. Người quản lý fork không vì vậy có quyền merge vào main của upstream. Trong tình huống minh họa, An làm việc trên branch của contractor/gateway và mở MR đến company/payments/gateway. Người review cần xác nhận đúng source project và target project, nhất là khi GitLab giới hạn vị trí fork hoặc chia sẻ theo group.

**Purpose:**

Giải thích ranh giới quyền giữa fork và upstream trước khi đi vào target branch.

## Project/nhánh nguồn và project/nhánh đích của merge request

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:33–02:46`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "fork là project riêng có quyền riêng". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Fork vừa tạo ra hai project; vì vậy MR cần xác nhận cả hai đầu trước khi review.

**Purpose:**

Chuyển từ fork là project riêng có quyền riêng sang câu hỏi source và target cần chọn chính xác cả project lẫn branch theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Project/nhánh nguồn và project/nhánh đích của merge request

**Time:** `02:46–03:55`

**Visual:**

Trên form tạo MR, khoanh hai nhóm: Source `contractor/gateway:fix-rounding` và Target `company/payments/gateway:main`. Đổi target sang release/v1 để cảnh báo sai hướng và diff không mong muốn.

**Script:**

Đọc MR không chỉ nhìn tên nhánh. Hãy kiểm tra cặp project-branch của nguồn và đích. Trên màn hình, thay đổi xuất phát từ fork contractor/gateway, nhưng nhóm muốn nhận vào main của gateway gốc. Nếu chọn nhầm release/v1, danh sách tệp thay đổi có thể lớn hơn dự kiến hoặc bản sửa đến sai dòng phát hành. Hai nhánh đều tên main cũng không chứng minh thuộc cùng project. Trước khi mời reviewer, xác nhận target, số commit và diff; nếu bất thường, sửa hướng so sánh chứ không vội thêm commit.

**Purpose:**

Làm rõ source/target hai chiều với bằng chứng diff giúp phát hiện MR nhắm sai đích.

## Mô tả thay đổi, assignee và trạng thái Draft/Ready

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:55–04:08`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "source và target cần chọn chính xác cả project lẫn branch". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Đúng hướng nguồn-đích mới là phần kỹ thuật của đề xuất; người đọc còn cần hiểu vấn đề và trạng thái chuẩn bị.

**Purpose:**

Chuyển từ source và target cần chọn chính xác cả project lẫn branch sang câu hỏi Draft để xin phản hồi sớm, Ready không phải approval theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Mô tả thay đổi, assignee và trạng thái Draft/Ready

**Time:** `04:08–05:21`

**Visual:**

Dựng MR !57 với Description: input -1, expected rejection, observed rounding; Assignee An, Reviewer Bình. Timeline Draft → Ready; trạng thái Approvals vẫn chưa có.

**Script:**

Một MR có ích phải nói thay đổi giải quyết vấn đề nào, cách thử và rủi ro. An mở Draft MR !57 vì chưa có test cho số âm, nhưng có thể mời trao đổi sớm. Assignee là người theo đuổi công việc; reviewer chịu trách nhiệm đánh giá, hai vai trò không nhất thiết cùng một người. Khi An bổ sung minh chứng và chuyển sang Ready, đó mới là tín hiệu sẵn sàng review, không tự phát sinh approval. Hãy nhìn merge widget thực tế nếu muốn biết còn điều kiện nào thiếu, thay vì suy từ nhãn Ready.

**Purpose:**

Dạy người học xem thông tin MR cần thiết và ranh giới trạng thái Draft/Ready.

## Mở, cập nhật, sẵn sàng đánh giá, hợp nhất hoặc đóng merge request

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:21–05:34`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "Draft để xin phản hồi sớm, Ready không phải approval". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Ngay cả sau Ready, MR có thể được sửa tiếp hoặc đóng mà không nhập mã vào target.

**Purpose:**

Chuyển từ Draft để xin phản hồi sớm, Ready không phải approval sang câu hỏi Open, Merged, Closed without merge khác nhau theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Mở, cập nhật, sẵn sàng đánh giá, hợp nhất hoặc đóng merge request

**Time:** `05:34–06:47`

**Visual:**

Timeline Open → push commit mới → review/discussion → Merged hoặc Closed. Dùng hai đường nhánh cuối khác màu, đặt nhãn approval có thể reset theo setting.

**Script:**

Từ lúc mở MR đến lúc kết thúc, An có thể thêm commit vào source, làm Changes và số phiên bản được review thay đổi. Bình cần xem lại phần diff cập nhật; các approval cũ có thể bị reset nếu project cấu hình điều đó. Một MR ở trạng thái Open không chắc đủ quyền merge; và Closed without merge không chứng minh code đã vào nhánh đích. Trên storyboard chúng ta chọn hai kết quả có thể, chứ không gắn thành công giả cho MR !57. Muốn kết luận Merged, phải đọc trạng thái GitLab và bằng chứng tại target.

**Purpose:**

Đóng chương bằng vòng đời MR có những kết quả khác nhau và kiểm tra version review.
