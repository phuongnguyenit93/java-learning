---
video:
  url: ""
---

# Quyền truy cập repository và thành viên tổ chức

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

## Organization, team và outside collaborator trên GitHub

<!-- VIDEO_SECTION -->

### Scene 1 — Organization, team và outside collaborator trên GitHub

**Time:** `00:00–01:07`

**Visual:**

Vẽ organization Orchid chứa team Billing, Repo payments; ngoài đường biên đặt một outside collaborator có mũi tên chỉ tới đúng repo được cấp, không tô quyền các repo khác.

**Script:**

Organization gom nhiều người và repository dưới phạm vi quản trị chung. Thay vì mời từng người vào mọi kho, người quản lý có thể dùng team để cấp quyền nhất quán. Nhưng outside collaborator chỉ có quyền đối với một số repository được phép truy cập, không vì thế trở thành thành viên đầy đủ của organization. Trong ví dụ Orchid, Mai muốn Bình review mã tính tiền, nên kiểm tra quan hệ team, quyền đến kho payments và chính sách khác nếu có. Một tên nằm trong danh sách team không tự chứng minh quyền hiệu lực trên mọi kho.

**Purpose:**

Trình bày các lớp org/team/outside collaborator và quyền theo repository.

## Các vai trò Read, Triage, Write, Maintain và Admin

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:07–01:18`

**Visual:**

Giữ sơ đồ organization, team và outside collaborator; hiện ma trận năm vai trò Read / Triage / Write / Maintain / Admin ngay bên cạnh.

**Script:**

Ta biết quyền đến từ đâu. Nhưng Read, Triage, Write, Maintain và Admin cho phép các công việc khác nhau.

**Purpose:**

Từ nguồn cấp quyền chuyển sang khả năng thao tác của từng vai trò repository.

### Scene 1 — Các vai trò Read, Triage, Write, Maintain và Admin

**Time:** `01:18–02:27`

**Visual:**

Dựng ma trận vai trò ở hàng, hành động đọc nguồn/quản Issues/đẩy mã/vận hành repo/đổi quyền ở cột; tô vùng minh họa và ghi 'kiểm tra policy và entitlement'.

**Script:**

Hãy cấp quyền theo nhiệm vụ thay vì theo mức độ thân quen. Người cần đọc mã có thể chỉ cần Read. Triage phù hợp với một số công việc tổ chức Issue hay PR mà không đẩy code. Write dành cho người đóng góp mã. Maintain hỗ trợ quản lý vận hành rộng hơn nhưng không thay Admin cho các thao tác nhạy cảm. Admin bao gồm quyền quản trị sâu, nên càng cần cân nhắc. Vai trò cụ thể còn tùy loại repository và chính sách tổ chức. Bảng này là mô hình lựa chọn, không phải lệnh cấp quyền hay kết quả thực tế.

**Purpose:**

Làm rõ ý định ít quyền nhất của năm vai trò GitHub mà không hứa mọi người có quyền tuyệt đối.

## Quyền đóng góp, yêu cầu review và quản trị repository

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:27–02:38`

**Visual:**

Thu ma trận vai trò thành các hành động cụ thể: đọc PR, push head, mời reviewer, đổi Settings và merge vào nhánh được bảo vệ.

**Script:**

Một người có quyền truy cập vẫn có thể không thực hiện được hành động PR hoặc Settings mong muốn.

**Purpose:**

Chỉ ra quyền thực tế cần được đối chiếu với từng hành động và quy tắc nhánh đích.

### Scene 1 — Quyền đóng góp, yêu cầu review và quản trị repository

**Time:** `02:38–03:43`

**Visual:**

Bảng hành động: đọc PR, yêu cầu reviewer, push head, thay đổi Settings, merge protected base. Kéo hai nhãn 'quyền người thao tác' và 'quy tắc nhánh đích' vào hàng merge.

**Script:**

Ta tách ba câu hỏi: ai được nhìn, ai được đóng góp và ai được thay đổi cấu hình. Bình nhận lời mời review không vì vậy thành owner. An có thể đẩy lên nhánh mình nhưng chưa chắc được merge vào main vì branch protection hoặc ruleset. Mai có trách nhiệm vận hành repository cũng cần kiểm tra giới hạn theo role. Khi thao tác bị từ chối, hãy ghi lại người thực hiện, đích và thông báo rồi đối chiếu quyền và rule. Không tăng quyền trước khi biết chính xác lớp nào đang chặn.

**Purpose:**

Dạy chẩn đoán dựa trên hành động cụ thể thay vì suy từ tên role.

## Thiếu quyền, quyền dư thừa và trách nhiệm chưa rõ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:43–03:54`

**Visual:**

Khoanh vùng thao tác push bị từ chối, đặt nhánh 'cấp Admin' cạnh nhánh kiểm tra quyền và quy tắc đích.

**Script:**

Nếu cấp quyền rộng để sửa lỗi quyền, nhóm có thể tạo ra rủi ro còn lớn hơn.

**Purpose:**

Chuyển từ hiểu role sang chẩn đoán lỗi quyền dựa trên bằng chứng thay vì tăng quyền rộng.

### Scene 1 — Thiếu quyền, quyền dư thừa và trách nhiệm chưa rõ

**Time:** `03:54–05:04`

**Visual:**

Hai đường lựa chọn: cấp Admin cho An (vùng đỏ rủi ro) hoặc kiểm tra quyền push nhánh và review rule (vùng xanh điều tra). Không hiển thị kết quả cấp quyền thật.

**Script:**

An báo không đẩy được sửa lỗi. Phản ứng dễ nhất là cấp Admin để 'chắc chắn chạy', nhưng như vậy An có thể được phép động đến nhiều thiết lập không phục vụ công việc. Cách hợp lý là kiểm tra quyền hiện có, nhánh đang gửi, quy tắc bảo vệ và phạm vi repo. Có thể vấn đề không nằm ở thiếu Write mà ở điều kiện tiếp nhận. Với Orchid, người quản lý phải chỉ định ai chịu trách nhiệm phê duyệt, ai được đổi Settings và khi nào thu hồi quyền tạm thời. Ít quyền nhất là quá trình rà soát, không chỉ bảng role.

**Purpose:**

Chứng minh cấp Admin không phải chiến lược khắc phục lỗi; hướng đến kiểm tra bằng chứng và trách nhiệm.
