---
video:
  url: ""
---

# Quy ước nhóm và kiểm soát tích hợp

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

## Quy ước vai trò, đặt tên và đối tượng sở hữu nhánh

<!-- VIDEO_SECTION -->

### Scene 1 — Kiểm chứng một chính sách nhóm

**Time:** `00:00–01:49`

**Visual:**

Trang policy nhóm có các cột Branch role, Naming example, Owner, Merge target; so `main`, `feature/*`, `release/*` và `hotfix/*`.

Số liệu dashboard/PR minh họa có nhãn; nếu không có quyền hosting, không giả claim dữ liệu reviewer trực tiếp.

**Script:**

Một chiến lược chỉ tồn tại trên slide sẽ nhanh chóng bị hiểu khác nhau. Hãy đặt bảng quy ước ngắn: branch nào là điểm tích hợp, prefix nào giúp người khác biết mục đích, ai chịu trách nhiệm review, nhánh nào được phép nhận fix. Tên feature/tax không tự bảo vệ code; nó chỉ giúp định vị công việc và owner. Nếu dùng trunk-only, không cần thêm develop chỉ để cho bảng nhiều dòng. Chúng ta giữ ít quy tắc nhưng mỗi quy tắc có lý do rõ.

**Purpose:**

Tạo bảng role/name/owner rõ mà không áp GitFlow cho mọi nhóm.

## Quy tắc thời gian sống, nhánh tồn đọng và đóng nhánh

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:49–02:03`

**Visual:**

Giữ bảng tên main/feature/release/hotfix với owner; thêm nhánh feature đã mở 24 ngày và người chịu trách nhiệm cho từng lần review/đóng.

**Script:**

Đã có vai trò nhánh và owner. Giờ đo tuổi nhánh, trạng thái quá hạn và tiêu chí dọn theo đúng mục đích.

**Purpose:**

Chuyển từ quy ước tên nhánh sang kỳ vọng tuổi nhánh và điều kiện dọn work branch đúng lúc.

### Scene 1 — Kiểm chứng một chính sách nhóm

**Time:** `02:03–03:52`

**Visual:**

Dashboard minh họa: tuổi PR 1 ngày, 6 ngày, 24 ngày; owner và ngày dự kiến đóng, cùng commit graph nhiều merge-base.

Số liệu dashboard/PR minh họa có nhãn; nếu không có quyền hosting, không giả claim dữ liệu reviewer trực tiếp.

**Script:**

Trong danh sách PR có một nhánh mới mở hôm qua và một nhánh đã 24 ngày không cập nhật. Chính sách nhóm nên xác định ai kiểm tra nhánh quá hạn, có cần chia nhỏ lại không, và khi nào đóng branch sau merge. Đừng xóa nhánh đang chứa công việc chưa hợp nhất chỉ để bảng sạch. Nhánh release phục vụ phiên bản còn được hỗ trợ có thời hạn khác với feature. Hãy kết hợp tuổi, hoạt động, kích thước diff và mục đích thay vì một con số cứng cho mọi trường hợp.

**Purpose:**

Thiết lập trách nhiệm xử lý stale branch thay vì xóa tùy tiện.

## Mục đích review, người chịu trách nhiệm và thời điểm phản hồi

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:52–04:06`

**Visual:**

Từ dashboard PR age 1/6/24 ngày, phóng PR rounding có Bình được request review, thêm thẻ review wait và lần sửa sau góp ý.

**Script:**

Nhánh tồn tại lâu có thể do chờ review. Theo một quy trình góp ý, sửa đổi và duyệt lại có mốc thời gian.

**Purpose:**

Cho thấy nhánh bị già đi có thể do phản hồi chậm, không chỉ do tác giả commit nhiều.

### Scene 1 — Kiểm chứng một chính sách nhóm

**Time:** `04:06–05:55`

**Visual:**

Pull request minh họa có author, reviewer, mã sửa và test note; timeline request changes → update → re-review.

Số liệu dashboard/PR minh họa có nhãn; nếu không có quyền hosting, không giả claim dữ liệu reviewer trực tiếp.

**Script:**

Một quy ước review tốt trả lời ba việc: thay đổi muốn giải quyết gì, ai có chuyên môn kiểm tra và phản hồi nên tới khi nào. Trong PR sửa timeout, reviewer nêu test case thiếu; tác giả cập nhật code rồi mời xem lại. Nhóm theo dõi thời gian chờ review để không biến branch ngắn thành nhánh tồn đọng. Phiếu Approve trong một nền tảng chỉ có ý nghĩa theo permission và branch policy tương ứng; bước cấu hình những nút đó thuộc module nền tảng, không phải quyết định chiến lược thuần túy.

**Purpose:**

Kết nối nhiệm vụ review với thời gian phản hồi và thay đổi được kiểm chứng.

## Chính sách kiểm tra trước merge và mức độ bắt buộc

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:55–06:09`

**Visual:**

Giữ timeline Bình comment→An sửa→Bình re-review; mở checklist passing tests, domain review, contract compatibility và merge eligibility.

**Script:**

Đã rõ ai review. Điều kiện kiểm thử, tương thích và phê duyệt nào phải đạt trước khi merge?

**Purpose:**

Nối giá trị review nghiệp vụ với bằng chứng kỹ thuật cần hoàn thành trước khi tích hợp.

### Scene 1 — Kiểm chứng một chính sách nhóm

**Time:** `06:09–07:58`

**Visual:**

Team requirement checklist: tests pass, compatible change, review complete, release impact known; PR check screen ở cột minh họa.

Số liệu dashboard/PR minh họa có nhãn; nếu không có quyền hosting, không giả claim dữ liệu reviewer trực tiếp.

**Script:**

Hãy viết yêu cầu ở cấp nhóm trước khi vào giao diện: thay đổi phải có test phù hợp, reviewer hiểu rủi ro, không làm hỏng contract và đáp ứng quy tắc merge đã thống nhất. Những tiêu chí nào cần chặn, tiêu chí nào chỉ cảnh báo? Một biểu tượng build xanh không chứng minh mọi hành vi đúng; nó chỉ là một phần bằng chứng. Module CI sẽ triển khai kiểm tra còn module hosting cấu hình required checks. Ở đây, chúng ta quyết định điều kiện chấp nhận và người chịu trách nhiệm khi không đạt.

**Purpose:**

Phân biệt yêu cầu kiểm soát chất lượng và implementation CI.

## Trách nhiệm duy trì nhánh chính và xử lý tích hợp thất bại

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:58–08:12`

**Visual:**

Từ checklist quality gates, chọn một commit làm main test đỏ và mở lịch phát hiện→người nhận→revert hoặc fix→green confirmed.

**Script:**

Các gate vẫn có thể bỏ sót regression. Hãy theo quá trình khôi phục mainline và owner sửa hoặc revert.

**Purpose:**

Minh họa chất lượng mainline còn gồm phản ứng sau merge khi kiểm thử phát hiện hồi quy.

### Scene 1 — Kiểm chứng một chính sách nhóm

**Time:** `08:12–10:01`

**Visual:**

Màn hình commit main trước/sau một commit gây test đỏ, timeline owner điều tra, fix-forward hoặc revert có kiểm chứng.

Số liệu dashboard/PR minh họa có nhãn; nếu không có quyền hosting, không giả claim dữ liệu reviewer trực tiếp.

**Script:**

Giả sử một PR đã merge nhưng kiểm tra sau tích hợp phát hiện regression. Nhóm phải chỉ định owner theo dõi main, khoanh commit nghi ngờ bằng log và diff, chọn sửa tiến hoặc revert có cân nhắc, rồi xác minh test trở lại xanh. Không nên mặc định force-push hoặc reset lịch sử chung để xóa dấu vết. Một chính sách TBD mạnh không chỉ yêu cầu tích hợp thường xuyên; nó còn bảo vệ người khác khỏi việc phải làm việc hàng giờ trên main bị hỏng.

**Purpose:**

Chỉ rõ owner và bằng chứng phục hồi nhánh chính khi lỗi.

## Ý định quản trị của nhóm so với quyền, bảo vệ nhánh và CI theo nền tảng

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:01–10:15`

**Visual:**

Giữ timeline main đỏ→green và người trực trách, tách ba bảng team intent, Git graph và host/CI enforcement với cấu hình ví dụ không giả kết quả live.

**Script:**

Khi đã rõ trách nhiệm khôi phục, hãy tách chính sách nhóm khỏi phân quyền host và phần triển khai CI.

**Purpose:**

Kết thúc bằng ranh giới trách nhiệm khi chính sách được triển khai trên Git, host và CI.

### Scene 1 — Kiểm chứng một chính sách nhóm

**Time:** `10:15–12:04`

**Visual:**

Sơ đồ ba tầng: Team intent → Git commands/history → Host rulesets/permissions + CI status; dấu không có local Servlet API.

Số liệu dashboard/PR minh họa có nhãn; nếu không có quyền hosting, không giả claim dữ liệu reviewer trực tiếp.

**Script:**

Ta kết thúc quy ước bằng ba lớp. Nhóm xác định tuổi nhánh, đường tích hợp, reviewer và điều kiện phải đạt. Git thực thi các thao tác branch và ghi commit graph. GitHub, GitLab, Bitbucket hoặc Azure Repos cấp quyền, policy và PR; CI chạy kiểm thử rồi trả trạng thái. Nếu quy định 'hai người review' chưa được cấu hình trên hosting, tài liệu nhóm không tự chặn merge; ngược lại nút enabled cũng không bảo đảm người review hiểu logic. Bàn giao cấu hình cho đúng module và kiểm chứng sự phù hợp bằng dữ liệu thực.

**Purpose:**

Phân công đúng governance, Git, hosting và CI.
