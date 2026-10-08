---
video:
  url: ""
---

# Tổ chức và chia sẻ repository trên GitHub

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

## Repository cá nhân và repository thuộc organization

<!-- VIDEO_SECTION -->

### Scene 1 — Repository cá nhân và repository thuộc organization

**Time:** `00:00–01:05`

**Visual:**

Đặt hai thẻ alice/demo và orchid/payments cạnh nhau; dưới kho tổ chức lần lượt hiện Team Billing, Maintainer và bảng phân quyền. Mũi tên chuyển owner có cảnh báo rà soát integration.

**Script:**

Một repository cá nhân rất phù hợp để thử nghiệm độc lập. Nhưng khi cả nhóm cần duy trì ứng dụng thanh toán, hãy hỏi ai sẽ quản lý quyền lúc một người chuyển bộ phận. Organization cho phép nhóm gán quyền theo team và thống nhất trách nhiệm. Việc chuyển repository sang owner khác cũng cần rà soát URL, quyền và các tích hợp liên quan; không nên coi đó chỉ là đổi nhãn. Với kho payments, ta ưu tiên một chủ sở hữu tổ chức rõ ràng thay vì phụ thuộc tài khoản cá nhân.

**Purpose:**

Dùng bài toán duy trì trách nhiệm để so sánh ownership cá nhân và tổ chức, tránh suy luận quyền từ tên kho.

## Public, private và internal: repository internal của GitHub Enterprise Cloud hiển thị cho thành viên toàn enterprise

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:05–01:16`

**Visual:**

Giữ hai tên chủ sở hữu alice/demo và orchid/payments; phủ lên ba vùng độc giả và làm nổi thành viên organization khác trong cùng enterprise ở ô Internal.

**Script:**

Sở hữu bởi tổ chức vẫn chưa cho biết ai nhìn thấy mã. Ta chuyển từ owner sang visibility.

**Purpose:**

Chuyển từ ai sở hữu kho sang ai có thể đọc kho; nhấn mạnh Internal rộng hơn phạm vi team quản lý.

### Scene 1 — Public, private và internal: repository internal của GitHub Enterprise Cloud hiển thị cho thành viên toàn enterprise

**Time:** `01:16–02:17`

**Visual:**

Hiện ba vòng đồng tâm Public (Internet), Private (người được cấp quyền), Internal (enterprise members, chỉ khi GitHub Enterprise Cloud hỗ trợ). Vẽ thành viên organization khác trong cùng enterprise đọc Internal, người ngoài bị loại.

**Script:**

Ba nhãn này không đơn thuần là ba mức 'kín'. Public cho người trên Internet xem; Private giới hạn theo quyền được cấp. Riêng Internal trên GitHub Enterprise Cloud có thể cho thành viên toàn enterprise xem, kể cả người thuộc organization khác với chủ kho. Đừng dịch Internal thành 'chỉ nhóm tôi'. Tính năng còn phụ thuộc mô hình enterprise và chính sách hiện hành. Nếu nội dung có dữ liệu nhạy cảm, ta phải kiểm tra đối tượng đọc thực tế trước khi chọn chế độ hiển thị.

**Purpose:**

Chứng minh điểm khác biệt enterprise-wide của Internal và không hứa tính năng trên mọi gói.

## Thông tin repository, thiết lập chia sẻ và nhánh mặc định

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:17–02:28`

**Visual:**

Thu ba vùng Public/Private/Internal về bảng Settings; nối default branch tới nhánh đang hiển thị trong Code và base gợi ý khi mở PR.

**Script:**

Khi đã biết ai có thể đọc, ta xem thông tin repository và trạng thái mặc định mà họ nhìn thấy.

**Purpose:**

Liên hệ phạm vi xem với thiết lập điều hướng và nhánh đích mặc định của cộng tác.

### Scene 1 — Thông tin repository, thiết lập chia sẻ và nhánh mặc định

**Time:** `02:28–03:32`

**Visual:**

Phác Settings → General với owner, mô tả, visibility, default branch; khi đổi từ main sang release thử highlight màn Code và PR base đề xuất, giữ ô deployment tách rời.

**Script:**

Nhánh mặc định quyết định nhiều ngữ cảnh khi mở trang Code và khi GitHub đề xuất nhánh đích cho PR mới. Nhưng nó không phải cam kết ứng dụng ngoài production cũng lấy đúng nhánh đó. Trên bảng, đổi default branch làm thay đổi cách người dùng duyệt nguồn và có thể ảnh hưởng quy trình liên quan. Trước khi thay đổi, nhóm cần kiểm tra mô tả kho, liên kết tài liệu, luồng PR và automation sử dụng tên nhánh. Một thiết lập tưởng nhỏ có thể thay đổi trải nghiệm cộng tác.

**Purpose:**

Nối default branch với UI, PR target và integrations, không gán nhầm với deployed version.

## Phạm vi tìm thấy, xem và đóng góp theo quyền và chế độ hiển thị

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:32–03:43`

**Visual:**

Giữ nhãn nhánh mặc định main phía trên ma trận Find / Read / Push; chừa trống quyền ghi để xét riêng.

**Script:**

Cấu hình hiển thị chưa quyết định người xem được đóng góp theo cách nào. Ta tách ba hành động.

**Purpose:**

Đưa người xem từ cấu hình hiển thị sang câu hỏi chưa giải quyết về quyền đóng góp.

### Scene 1 — Phạm vi tìm thấy, xem và đóng góp theo quyền và chế độ hiển thị

**Time:** `03:43–04:45`

**Visual:**

Tạo bảng ba cột Find/Read/Push; thêm Public outsider, Private authorized reader và Enterprise Internal member. Với từng người để dấu hỏi ở ô đề xuất PR cho đến khi xét quyền/thiết lập.

**Script:**

Một người thấy repository public thường đọc được mã, nhưng không vì vậy có quyền đẩy trực tiếp vào main. Họ có thể đóng góp qua fork và pull request nếu điều kiện cho phép. Người có quyền đọc kho Private cũng không mặc nhiên quản lý Settings. Với Internal, phạm vi đọc đi theo thành viên enterprise, không đồng nghĩa quyền Write. Khi không thể mở trang hoặc gửi PR, hãy chẩn đoán riêng khả năng tìm thấy, đọc và quyền hành động thay vì cấp quyền Admin cho nhanh.

**Purpose:**

Kết luận quyền xem, khám phá và sửa nguồn là các câu hỏi tách biệt, dẫn vào shared-branch/fork.
