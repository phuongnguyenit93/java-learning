---
video:
  url: ""
---

# Bảo vệ nhánh và điều kiện hợp nhất

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

## Branch protection và quy tắc bảo vệ áp dụng cho một nhánh

<!-- VIDEO_SECTION -->

### Scene 1 — Branch protection và quy tắc bảo vệ áp dụng cho một nhánh

**Time:** `00:00–01:03`

**Visual:**

Phác Settings → Branch protection với mẫu main/release/*; đánh dấu các khả năng require PR, approvals, checks và hạn chế push. Vẽ nhiều pattern trùng một nhánh nhưng chỉ một branch protection rule được chọn.

**Script:**

Branch protection đặt điều kiện với nhánh có tên hoặc pattern phù hợp. Người quản trị có thể yêu cầu PR, số lượt review, status checks và giới hạn cập nhật trực tiếp tùy gói và cấu hình. Một điểm dễ nhầm: nếu nhiều branch protection rule có pattern khớp, GitHub chỉ áp dụng một rule cho nhánh theo cơ chế chọn rule, không cộng tất cả chúng. Vì vậy nếu main không hành xử như dự đoán, hãy xem đúng rule đang có hiệu lực và đừng tự suy từ danh sách pattern.

**Purpose:**

Chỉ rõ single matching branch protection rule cùng khả năng bảo vệ nhánh có cấu hình.

## Rulesets chồng lấn và khác biệt với branch protection rule

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:03–01:14`

**Visual:**

Giữ nhánh main dưới một thẻ branch protection được chọn, rồi mở hai thẻ ruleset đang áp dụng và thẻ Evaluate nét đứt.

**Script:**

Branch protection thường chọn một rule; nhưng GitHub còn có rulesets với cách kết hợp khác.

**Purpose:**

Đối chiếu một branch protection rule có hiệu lực với nhiều ruleset cùng tác động trước khi xét điều kiện merge.

### Scene 1 — Rulesets chồng lấn và khác biệt với branch protection rule

**Time:** `01:14–02:24`

**Visual:**

Trên cùng nhánh main vẽ các lớp ruleset 'require check' và 'restrict update'; đối chiếu một thẻ branch protection đơn. Thẻ Evaluate chỉ hiển thị biểu tượng quan sát không chặn.

**Script:**

Ruleset gom quy tắc và có phạm vi cũng như chế độ thực thi riêng. Khác với một branch protection rule, nhiều ruleset có thể cùng áp dụng lên main. Khi chúng ở trạng thái thực thi, nhóm phải xét yêu cầu kết hợp thay vì chỉ đọc một tấm bảng. Nếu một ruleset ở chế độ đánh giá, nó có thể ghi nhận vi phạm dự kiến mà không ngăn thao tác như chế độ thực thi. Quyền tạo và phạm vi ruleset cũng phụ thuộc gói, tài khoản và chính sách. Hãy hỏi ruleset nào đang thực thi thật trước khi kết luận PR bị chặn.

**Purpose:**

Đối chiếu ruleset chồng lấn với branch protection và chế độ đánh giá.

## Yêu cầu pull request, số lượt phê duyệt và phê duyệt của code owner

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:24–02:35`

**Visual:**

Giữ các thẻ ruleset bên trên PR #57; thêm các ô dành cho số lượt Approve hợp lệ và code owner.

**Script:**

Khi quy tắc đã có hiệu lực, điều kiện nào khiến một PR thật sự đủ lượt đánh giá?

**Purpose:**

Chuyển từ lớp chính sách sang những điều kiện đánh giá cụ thể mà pull request phải đáp ứng.

### Scene 1 — Yêu cầu pull request, số lượt phê duyệt và phê duyệt của code owner

**Time:** `02:35–03:41`

**Visual:**

Minh họa PR #57: hai ô approvals yêu cầu theo cấu hình, và một ô code owner; một thẻ Approve không tự điền toàn bộ. Gắn CODEOWNERS từ base main.

**Script:**

Nhánh main có thể yêu cầu mọi thay đổi qua PR, số lượng phê duyệt hợp lệ và code owner cho những tệp phù hợp. Đây là điều kiện phải bật, không phải mặc định cho mọi repository. Một dấu Approve của người không đủ điều kiện hoặc của một người duy nhất có thể chưa đáp ứng quy tắc. CODEOWNERS từ base giúp biết ai phụ trách đường dẫn, còn required code owner review mới biến phê duyệt thành điều kiện. Ở PR #57, ta kiểm tra từng yêu cầu thực tế trước khi gọi nó sẵn sàng.

**Purpose:**

Nối yêu cầu PR/review và code-owner approval với cấu hình thay vì tự suy từ nút Approve.

## Required status checks và điều kiện cho phép merge

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:41–03:52`

**Visual:**

Để nguyên các ô review nhưng mở thêm làn Checks, với ô kết quả required check vẫn trống.

**Script:**

Approval có thể đã đủ, nhưng nhóm còn muốn một nguồn kiểm tra kỹ thuật báo kết quả.

**Purpose:**

Giải thích vì sao đủ phê duyệt vẫn chưa đủ chứng minh điều kiện kiểm tra kỹ thuật.

### Scene 1 — Required status checks và điều kiện cho phép merge

**Time:** `03:52–05:00`

**Visual:**

Khung PR Checks có ba nhãn Pending, Failed, Success dưới cột 'minh họa trạng thái'; Required flag chỉ gắn với một check cấu hình. Không vẽ test output thật.

**Script:**

Status check là kết quả được hệ thống kiểm tra báo về GitHub. Nếu người quản trị đặt nó thành required đối với nhánh đích, PR phải đáp ứng đúng yêu cầu mới có thể merge. Ta phân biệt check chưa báo kết quả, check thất bại và check thành công; không tự tưởng tượng rằng repo hiện có pipeline hoặc đã chạy thử nghiệm. Ngay cả status Success cũng chứng minh loại kiểm tra đã báo xong, không chứng minh phép tính tiền hoàn toàn đúng. Khi thiếu check, hãy xem tên và nguồn báo được yêu cầu, không vô cớ thay đổi mã.

**Purpose:**

Dạy required check và evidence scope, không giả mạo pipeline hay test result.

## Hạn chế push, quyền bỏ qua quy tắc và chẩn đoán merge bị chặn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:00–05:11`

**Visual:**

Khoanh required check còn thiếu bên cạnh actor/branch, mở cây chẩn đoán riêng cho hạn chế push, điều kiện merge và bypass.

**Script:**

Nếu PR vẫn bị chặn, ta không xóa hết rules. Ta tìm đúng lớp đang hạn chế hành động.

**Purpose:**

Biến thông báo bị chặn thành câu hỏi về đúng quyền hay quy tắc đang có hiệu lực.

### Scene 1 — Hạn chế push, quyền bỏ qua quy tắc và chẩn đoán merge bị chặn

**Time:** `05:11–06:18`

**Visual:**

Vẽ cây chẩn đoán: actor → branch/permission → branch protection → rulesets → review/checks → bypass exception; một nhánh chỉ ra 'đọc báo lỗi thật'.

**Script:**

Khi An không thể push hoặc merge, điều quan trọng là biết hành động nào bị từ chối. Hạn chế push trực tiếp không giống điều kiện chấp thuận PR. Một Admin hoặc người có quyền bypass có thể được miễn một số giới hạn theo cấu hình, nhưng ruleset khác vẫn có thể tiếp tục áp dụng. Ta ghi người thao tác, nhánh đích, thông báo và các rules có hiệu lực trước khi đổi bất kỳ thiết lập nào. Mục tiêu là sửa tối thiểu, có dấu vết, không ban quyền Admin hay tắt bảo vệ chỉ vì muốn merge nhanh.

**Purpose:**

Kết thúc bảo vệ nhánh bằng quy trình phân loại lỗi có bằng chứng và ngoại lệ có điều kiện.
