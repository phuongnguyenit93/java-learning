---
video:
  url: ""
---

# Đường đóng góp qua nhánh chung và fork

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

## Hai đường đóng góp: nhánh trong repository chung và fork

<!-- VIDEO_SECTION -->

### Scene 1 — Hai đường đóng góp: nhánh trong repository chung và fork

**Time:** `00:00–00:58`

**Visual:**

Dựng hai luồng song song: Alice push nhánh feature trong repo chung; cộng tác viên khác push fork rồi tạo PR về main.

Khi quay UI chỉ dùng sandbox được phép hoặc ảnh Microsoft được dẫn nguồn; tên RetailCo/Bug 104 chỉ là ví dụ giả định.

**Script:**

Đặt cạnh nhau hai luồng đóng góp. Alice có quyền tạo nhánh feature trong checkout-api nên dùng shared repository; một cộng tác viên cần tách phạm vi có thể dùng fork riêng nếu policy và permissions cho phép. Cả hai phải gửi thay đổi qua PR để người có trách nhiệm xem xét trước khi cập nhật nhánh main. Không đánh giá cách nào luôn an toàn hơn: điểm quyết định là danh tính, phạm vi push, yêu cầu review và quản trị quyền.

**Purpose:**

Chọn nhánh chung hay fork dựa trên phạm vi quyền.

## Fork là repository riêng và quan hệ với nguồn upstream

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:58–01:11`

**Visual:**

Dịch nhánh chung của Alice sang bên, tạo fork checkout-api với đường biên chủ sở hữu riêng.

**Script:**

Người không có Write ở repo chung có thể dùng fork. Fork khác ở quyền và nơi lưu ra sao?

**Purpose:**

Giải thích đường đóng góp thay thế thay đổi phạm vi sở hữu và quyền ghi.

### Scene 1 — Fork là repository riêng và quan hệ với nguồn upstream

**Time:** `01:11–02:02`

**Visual:**

Vẽ checkout-api upstream → fork checkout-api-alice → branch feature/timeout; đánh dấu remote names origin/upstream chỉ là cấu hình local.

Khi quay UI chỉ dùng sandbox được phép hoặc ảnh Microsoft được dẫn nguồn; tên RetailCo/Bug 104 chỉ là ví dụ giả định.

**Script:**

Đánh dấu fork là repository riêng có Git history và nhánh do người đóng góp quản lý theo quyền của họ. Khi Alice push lên fork, upstream/main không tự thay đổi. Muốn đề xuất tích hợp, cô tạo PR từ fork/feature/timeout vào repo gốc main. Các tên Git remote local origin và upstream chỉ là quy ước cấu hình ở máy, đừng nhầm với nhãn owner trong Azure DevOps UI.

**Purpose:**

Phân biệt dữ liệu fork với thay đổi upstream.

## Quyền của fork và chính sách PR tại repository đích

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:02–02:12`

**Visual:**

Giữ origin và upstream ở hai hộp, gắn branch policies của main lên upstream.

**Script:**

Fork có quyền riêng, nhưng PR về upstream vẫn chịu chính sách của main.

**Purpose:**

Làm rõ quyền ở fork không loại bỏ chính sách của nhánh đích.

### Scene 1 — Quyền của fork và chính sách PR tại repository đích

**Time:** `02:12–03:04`

**Visual:**

Hiển thị hai tab Security/Policies với breadcrumb repo gốc và fork khác nhau; tô policy nhánh đích upstream/main.

Khi quay UI chỉ dùng sandbox được phép hoặc ảnh Microsoft được dẫn nguồn; tên RetailCo/Bug 104 chỉ là ví dụ giả định.

**Script:**

Từ hai bảng settings, hãy chỉ ra fork không tự sao chép permissions, policies hoặc build pipelines của repo gốc. Một người có quyền quản trị fork chưa chắc được push vào upstream main. Khi PR hướng vào upstream/main, chính sách của **nhánh đích upstream** mới là điểm kiểm tra cho merge. Chúng ta không mở mục tạo pipeline; chỉ đọc branch policy và ghi rõ repository nào sở hữu điều kiện.

**Purpose:**

Chỉ ra chính sách PR thuộc nhánh đích upstream.

## Repository/nhánh nguồn và repository/nhánh đích của PR

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:04–03:14`

**Visual:**

Gắn nhãn source/target repository và branch lên mũi tên PR từ fork về upstream.

**Script:**

Ta kiểm tra cả source và target để tránh gửi đề xuất nhầm đích.

**Purpose:**

Loại bỏ nhầm hướng đề xuất trước khi đọc nội dung thay đổi.

### Scene 1 — Repository/nhánh nguồn và repository/nhánh đích của PR

**Time:** `03:14–04:06`

**Visual:**

Trên PR header làm nổi bật bốn ô source repository, source branch, target repository, target branch.

Khi quay UI chỉ dùng sandbox được phép hoặc ảnh Microsoft được dẫn nguồn; tên RetailCo/Bug 104 chỉ là ví dụ giả định.

**Script:**

Đừng chỉ chụp tên nhánh feature/timeout và main: cùng một nhánh main có thể tồn tại ở nhiều repository. PR cần xác định đủ source repository, source branch, target repository và target branch. Từ đây mới đọc được reviewer và policy có hiệu lực. Hãy yêu cầu người học tự chỉ ra mũi tên đi từ fork hay shared branch và đặt câu hỏi khác biệt commit nào đang được đề xuất.

**Purpose:**

Xác minh đầy đủ hai repository và hai nhánh của PR.

## Đề xuất PR có mục đích, mô tả và bối cảnh thay đổi

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:06–04:17`

**Visual:**

Mở phần PR description cạnh mũi tên đúng đích, lần lượt hiện Summary, Risks và Verification.

**Script:**

Chọn đúng nhánh chưa đủ; reviewer cần hiểu mục đích và bằng chứng kiểm tra.

**Purpose:**

Gắn hướng PR đúng với bằng chứng reviewer cần để đánh giá.

### Scene 1 — Đề xuất PR có mục đích, mô tả và bối cảnh thay đổi

**Time:** `04:17–05:15`

**Visual:**

Điền biểu mẫu PR minh họa với title, Summary, Risk, Verification và work item Bug 104, tô các trường mang bằng chứng.

Khi quay UI chỉ dùng sandbox được phép hoặc ảnh Microsoft được dẫn nguồn; tên RetailCo/Bug 104 chỉ là ví dụ giả định.

**Script:**

Giả sử sửa timeout theo Bug 104. PR title phải nói được hành vi thay đổi; description cho biết lỗi trước đó, phạm vi sửa, dữ liệu kiểm thử và ảnh hưởng tương thích. Người review đọc phần Files để kiểm tra xem diff có thực sự khớp lời mô tả. Thêm work item là dấu vết bối cảnh, không tự chứng minh vấn đề đã được giải quyết. Tạo PR cũng không tạo commit mới; source branch vẫn thuộc trách nhiệm tác giả.

**Purpose:**

Tạo đề xuất thay đổi có mục tiêu và bằng chứng.

## Draft PR và trạng thái sẵn sàng review

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:15–05:27`

**Visual:**

Giữ bản sửa đề xuất, đặt hai thẻ Draft và Ready cạnh nhau.

**Script:**

PR mô tả rõ vẫn có thể là Draft. Khi nào tác giả muốn chuyển sang Ready?

**Purpose:**

Phân biệt thời điểm xin góp ý với sẵn sàng đánh giá, chưa mặc định được approve.

### Scene 1 — Draft PR và trạng thái sẵn sàng review

**Time:** `05:27–06:23`

**Visual:**

Đặt hai ảnh trạng thái Draft và Ready for review/Active cạnh nhau; chỉ thay đổi badge, không tạo phiếu giả.

Khi quay UI chỉ dùng sandbox được phép hoặc ảnh Microsoft được dẫn nguồn; tên RetailCo/Bug 104 chỉ là ví dụ giả định.

**Script:**

Một Draft PR báo rằng bản sửa còn đang được hoàn thiện hoặc muốn xin ý kiến sớm. Khi tác giả đánh dấu sẵn sàng review, đó là lời mời đánh giá chính thức, không phải tín hiệu chính sách đã đạt hoặc được phép merge. Nhìn status ở header và bản diff hiện tại; tuyệt đối không gán cho Draft khả năng tự bỏ qua policy. Người review nên hiểu rõ trạng thái trước khi yêu cầu Approve.

**Purpose:**

Tách trạng thái Draft/Ready khỏi điều kiện merge.

## Bằng chứng tác giả, quyền và nhánh đích của đường đóng góp

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:23–06:34`

**Visual:**

Gom author, quyền source, policy target và trạng thái Draft/Active thành một bảng bằng chứng.

**Script:**

Cuối cùng ghi tác giả, quyền source, quy tắc target và trạng thái sẵn sàng.

**Purpose:**

Tổng hợp các kiểm tra quyền và trạng thái quyết định đường đóng góp.

### Scene 1 — Bằng chứng tác giả, quyền và nhánh đích của đường đóng góp

**Time:** `06:34–07:29`

**Visual:**

Lập bảng PR gồm author, source repo/branch, target repo/branch, Draft/Active, source rights và target policies.

Khi quay UI chỉ dùng sandbox được phép hoặc ảnh Microsoft được dẫn nguồn; tên RetailCo/Bug 104 chỉ là ví dụ giả định.

**Script:**

Chốt chương bằng ba câu hỏi tách biệt: người này có thể tạo PR không, có quyền push thêm commit lên source không, và có quyền complete vào target không? Mỗi câu dùng quyền và trạng thái khác nhau. Ghi đúng actor, hai repository, hai nhánh, trạng thái Draft/Active cùng target policies trên bảng bằng chứng. Không dựa vào một nút Create PR xuất hiện để khẳng định người đó đã được phép cập nhật nhánh đích.

**Purpose:**

Lập bằng chứng phân biệt tạo PR, push source và complete target.
