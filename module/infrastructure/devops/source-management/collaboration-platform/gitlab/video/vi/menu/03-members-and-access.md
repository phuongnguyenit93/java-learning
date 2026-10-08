---
video:
  url: ""
---

# Thành viên, vai trò và quyền truy cập

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

## Các vai trò Guest, Planner, Reporter, Developer, Maintainer và Owner

<!-- VIDEO_SECTION -->

### Scene 1 — Các vai trò Guest, Planner, Reporter, Developer, Maintainer và Owner

**Time:** `00:00–01:15`

**Visual:**

Hiện bảng Guest, Planner, Reporter, Developer, Maintainer, Owner trên các hàng; bên cạnh là các tác vụ đọc code, phân loại Issue, sửa code, quản trị project. Chèn chú thích availability và project/group context.

**Script:**

Đừng đọc sáu vai trò như một chiếc thang cho phép mọi người ở bậc cao hơn làm mọi việc vô điều kiện. Guest thường có phạm vi cộng tác hạn chế; Planner hỗ trợ lập kế hoạch theo tính năng được cấp; Reporter đọc repository; Developer đóng góp mã theo branch rule; Maintainer quản lý project sâu hơn, còn Owner liên quan quyền sở hữu, đặc biệt trong group. Trong ví dụ gateway, Bình chỉ cần đọc và review, An cần sửa nhánh, Chi kiểm soát thiết lập. Hãy chọn quyền dựa trên thao tác và offering thực tế, không cấp Owner chỉ cho tiện.

**Purpose:**

Giới thiệu hệ vai trò theo nhu cầu làm việc và cảnh báo giới hạn gói/quyền project.

## Quyền từ thành viên trực tiếp, kế thừa và group được chia sẻ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:15–01:28`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "chọn role theo công việc, không theo chức danh chung". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Role nhìn trên project chưa cho biết ai đã cấp nó. Hãy đi ngược các đường membership có thể cùng tồn tại.

**Purpose:**

Chuyển từ chọn role theo công việc, không theo chức danh chung sang câu hỏi một người có thể có nhiều nguồn membership theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Quyền từ thành viên trực tiếp, kế thừa và group được chia sẻ

**Time:** `01:28–02:42`

**Visual:**

Dựng ba mũi tên tới gateway: Direct invite, Inherited from company/payments, Shared group. Xóa thử mũi tên direct nhưng giữ hai mũi tên còn lại và đánh dấu quyền chưa chắc mất.

**Script:**

Bình được mời trực tiếp vào gateway và cũng thuộc payments ở group cha. Nếu Chi xóa lời mời trực tiếp, Bình có thể vẫn đọc được project do quyền kế thừa. Quyền còn có thể đến từ một group được chia sẻ với project. Ba nguồn này do các chủ thể quản trị khác nhau kiểm soát, nên xóa sai một dòng không nhất thiết thu hồi quyền hiệu lực. Trong cảnh này chúng ta không thay membership thật; ta mở mô hình trang Members và hỏi cột Source cho biết điều gì, rồi mới quyết định phải thu hồi ở cấp nào.

**Purpose:**

Dạy tìm nguồn grant thực tế và tránh kết luận sai khi thu hồi direct membership.

## Quyền hiệu lực của project và phạm vi quản trị group

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:42–02:55`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "một người có thể có nhiều nguồn membership". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Sau khi biết quyền đến từ những đường nào, câu hỏi thật là GitLab sẽ cho phép hành động nào trong ngữ cảnh này.

**Purpose:**

Chuyển từ một người có thể có nhiều nguồn membership sang câu hỏi role hiệu lực phải xét cả target branch theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Quyền hiệu lực của project và phạm vi quản trị group

**Time:** `02:55–04:08`

**Visual:**

Hiện bảng An=Developer qua group cha; action `push fix-rounding` và `push main`. Kéo thẻ protected `main` xuống ô thứ hai, đánh dấu kiểm tra rule thay vì gán kết quả luôn.

**Script:**

Giả sử An là Developer nhờ membership của group cha. Điều đó có thể cho phép đóng góp vào nhánh làm việc, nhưng không chứng minh An được push trực tiếp vào main được bảo vệ. Ta cần ghép vai trò hiệu lực với trạng thái nhánh đích và policy áp dụng. Tương tự, một thành viên có tên ở group chưa chắc sửa được Settings của project. Khi hành động bị từ chối, hãy ghi đúng project, người thao tác và nhánh. Từ đó ta phân biệt lỗi cấp quyền với quy tắc bảo vệ chứ không nâng role bừa bãi.

**Purpose:**

Gắn membership với điều kiện nhánh để tạo cách chẩn đoán permission theo hành động.

## Quyền xem mã nguồn, tạo đề xuất, đánh giá và hợp nhất

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:08–04:21`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "role hiệu lực phải xét cả target branch". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Có quyền với project không có nghĩa được thực hiện tất cả bước trong vòng đời merge request.

**Purpose:**

Chuyển từ role hiệu lực phải xét cả target branch sang câu hỏi đọc, gửi MR, approve và merge là quyền khác nhau theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Quyền xem mã nguồn, tạo đề xuất, đánh giá và hợp nhất

**Time:** `04:21–05:33`

**Visual:**

Ma trận các hành động View source, Create MR, Approve, Merge main; đặt contractor vào Fork path, Bình vào Review path, Chi vào protected-target merge path. Đánh dấu approval eligibility cần kiểm tra theo tier.

**Script:**

Một người đọc được project public có thể đề xuất sửa qua fork mà không cần quyền đẩy vào upstream, tùy chính sách. Người được mời review chưa chắc có phiếu phê duyệt hợp lệ để thỏa required rule. Chi là Maintainer có thể được phép merge theo nhánh nhưng vẫn phải đáp ứng những approval gate đang bật. Hãy theo bốn ô trên màn hình: xem mã, tạo đề xuất, được tính approval và thực hiện merge. Mỗi ô cần bằng chứng riêng từ membership, rules và trạng thái MR, không suy ra toàn bộ từ một tên role.

**Purpose:**

Phân tách hành động collaboration thành cổng quyền độc lập và chuẩn bị cho tier-gated approval.

## Thiếu quyền và quyền vượt nhu cầu khi truy cập project

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:33–05:46`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "đọc, gửi MR, approve và merge là quyền khác nhau". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Một thao tác thất bại có thể do nhiều lớp; ta cần một thứ tự điều tra an toàn thay vì tăng quyền.

**Purpose:**

Chuyển từ đọc, gửi MR, approve và merge là quyền khác nhau sang câu hỏi chẩn đoán trước khi tăng quyền theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Thiếu quyền và quyền vượt nhu cầu khi truy cập project

**Time:** `05:46–06:59`

**Visual:**

Vẽ cây chẩn đoán `cannot open project` → visibility/membership, `cannot push` → source branch rule, `cannot merge` → target role/approvals/checks. Đặt nút Admin ở bên đỏ 'không tự chọn'.

**Script:**

Một contractor chỉ cần theo dõi Issues nhưng được cấp Maintainer cho dễ sẽ có khả năng tác động rộng hơn nhu cầu. Khi không mở được project, kiểm tra visibility và nguồn membership. Khi không push được, hỏi nhánh nguồn có được bảo vệ không. Khi nút Merge bị khóa, đọc trạng thái reviewer, target và checks trước khi kết luận thiếu quyền. Cấp nhiều quyền hơn thường che giấu nguyên nhân thực, có thể phá kiểm soát nhóm. Hãy giữ lại thông báo lỗi và thử đường đóng góp qua fork hoặc reviewer đủ điều kiện khi thích hợp.

**Purpose:**

Kết thúc phần access bằng quy trình kiểm chứng cụ thể và nguyên tắc ít quyền nhất.
