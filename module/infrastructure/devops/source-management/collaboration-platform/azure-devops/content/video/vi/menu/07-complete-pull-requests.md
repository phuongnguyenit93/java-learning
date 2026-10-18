---
video:
  url: ""
---

# Hoàn tất PR và liên kết dấu vết công việc

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

## Điều kiện hoàn tất PR và xung đột chưa được giải quyết

<!-- VIDEO_SECTION -->

### Scene 1 — Điều kiện hoàn tất PR và xung đột chưa được giải quyết

**Time:** `00:00–00:56`

**Visual:**

Trên cùng PR, đánh dấu target/main, conflict status, policy summary, effective actor rights và nút Complete.

Quan sát sandbox được phép hoặc tài liệu Microsoft có nhãn; không nhấn Complete, bypass hoặc bỏ PR production cho mục đích quay phim.

**Script:**

Một PR muốn Complete cần nhiều điều kiện cùng đúng: nhánh nguồn/đích hợp lệ, không còn conflict chưa xử lý, required votes và blocking checks đạt, và actor có quyền trên target. Ta đi theo checklist trên cùng màn hình trước khi nhấn Complete. Một người Approve không sửa được conflict, còn policy xanh không cấp thêm quyền cho người bấm. Nếu chưa đủ, trạng thái phải được ghi là chưa sẵn sàng, không phải 'gần như đã merge'.

**Purpose:**

Xác định các điều kiện cần trước Complete.

## Complete PR so với Set auto-complete

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:56–01:07`

**Visual:**

Giữ checklist conflict và policy, mở cạnh nhau Complete và Set auto-complete.

**Script:**

Ta so Complete một PR đủ điều kiện ngay với Set auto-complete để chờ về sau.

**Purpose:**

Phân biệt merge được phép thực hiện ngay với hoàn tất có điều kiện sau.

### Scene 1 — Complete PR so với Set auto-complete

**Time:** `01:07–02:04`

**Visual:**

So sánh các tùy chọn Complete ngay với Set auto-complete; hiển thị trạng thái check Pending rồi Passed có dẫn nguồn.

Quan sát sandbox được phép hoặc tài liệu Microsoft có nhãn; không nhấn Complete, bypass hoặc bỏ PR production cho mục đích quay phim.

**Script:**

Complete cố hoàn tất ngay khi điều kiện hiện tại đạt. Set auto-complete ghi ý định sẽ hoàn tất về sau khi các policy yêu cầu được thỏa. Nó không tự sửa code, không biến build Failed thành Passed và không cấp quyền còn thiếu. Trên ảnh sandbox, chỉ đọc trạng thái Pending của check và tùy chọn auto-complete; nếu chưa có check thật, dùng minh họa ghi nguồn. Việc hoàn tất tự động vẫn phải tuân thủ điều kiện target.

**Purpose:**

Tách Complete ngay với Auto-complete có điều kiện.

## Abandon, Reactivate và Complete: vòng đời của PR

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:04–02:14`

**Visual:**

Đưa thẻ auto-complete đang chờ vào timeline Active → Abandoned → Reactivated → Completed.

**Script:**

Auto-complete là chờ điều kiện; Abandon hay Reactivate thuộc vòng đời khác.

**Purpose:**

Tách trạng thái chờ, bỏ PR và đã tích hợp thành các kết quả khác nhau.

### Scene 1 — Abandon, Reactivate và Complete: vòng đời của PR

**Time:** `02:14–03:10`

**Visual:**

Vẽ timeline Active → Abandoned → Reactivated Active → Completed, giữ riêng source branch/commit graph.

Quan sát sandbox được phép hoặc tài liệu Microsoft có nhãn; không nhấn Complete, bypass hoặc bỏ PR production cho mục đích quay phim.

**Script:**

Active nghĩa là đề xuất còn đang mở, Abandoned đóng đề xuất mà không merge, Reactivate cho phép mở lại vòng đánh giá, Completed mới ghi nhận tích hợp thành công. Trên timeline, đánh dấu vị trí của commit trong source branch: Abandon không tự xóa lịch sử Git và cũng không phải một dạng revert. Khi khôi phục PR, phải xem lại diff, phiếu và policies hiện tại vì mọi thứ có thể đã đổi theo thời gian.

**Purpose:**

Phân biệt vòng đời PR với việc xóa hoặc hoàn tác commit.

## Tùy chọn hoàn tất PR và dấu vết hợp nhất trong Azure Repos

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:10–03:20`

**Visual:**

Giữ Completed trên timeline, mở graph Git nhánh đích của merge và squash.

**Script:**

Trạng thái Completed có bằng chứng gì trong lịch sử Git của nhánh đích?

**Purpose:**

Gắn trạng thái hoàn tất với lịch sử commit thực tế của kiểu merge.

### Scene 1 — Tùy chọn hoàn tất PR và dấu vết hợp nhất trong Azure Repos

**Time:** `03:20–04:16`

**Visual:**

Đặt hộp thoại completion với merge strategy, xóa source branch, timeline Completed và target Git graph cạnh nhau.

Quan sát sandbox được phép hoặc tài liệu Microsoft có nhãn; không nhấn Complete, bypass hoặc bỏ PR production cho mục đích quay phim.

**Script:**

Khi Complete, Azure Repos có các lựa chọn basic merge, squash hoặc rebase variants nếu policy cho phép. Bật tắt tùy chọn xóa nhánh nguồn là lựa chọn quản trị sau tích hợp, không phải dấu hiệu kết quả code đã đúng. Hãy so đồ thị target sau basic merge với squash để nhận ra lịch sử khác nhau; đồng thời giữ số PR/commit để truy vết. Không gọi một loại merge là duy nhất an toàn cho mọi nhóm.

**Purpose:**

Đọc dấu vết commit và tùy chọn sau Complete.

## Liên kết work item với PR: mục đích và ngữ cảnh công việc

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:16–04:28`

**Visual:**

Ghim thay đổi đã merge vào thẻ Azure Boards Bug 104 tách riêng.

**Script:**

Lịch sử merge nói cách tích hợp; liên kết work item diễn đạt vì sao cần sửa.

**Purpose:**

Thêm bối cảnh nghiệp vụ mà chưa khẳng định lỗi tự được đóng.

### Scene 1 — Liên kết work item với PR: mục đích và ngữ cảnh công việc

**Time:** `04:28–05:22`

**Visual:**

Trong tab Work Items của PR, khoanh Bug 104 và sơ đồ work-item tracking khác commit Git.

Quan sát sandbox được phép hoặc tài liệu Microsoft có nhãn; không nhấn Complete, bypass hoặc bỏ PR production cho mục đích quay phim.

**Script:**

Gắn PR timeout vào Bug 104 giúp reviewer tìm lại bối cảnh lỗi trong Azure Boards. Work item là đối tượng theo dõi công việc, còn commit và PR là dấu vết mã nguồn và đánh giá. Chỉ có liên kết không chứng minh bug đã sửa; người review vẫn cần đọc diff và bằng chứng test. Chúng ta không thực hành tạo workflow, trạng thái hay board mới vì đó là phạm vi Azure Boards.

**Purpose:**

Giải thích mục đích link work item, không coi link là test.

## Chính sách bắt buộc liên kết work item khi complete PR

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:22–05:33`

**Visual:**

Giữ liên kết Bug 104, bật khung yêu cầu linked work item của Branch policies.

**Script:**

Liên kết work item cũng có thể là điều kiện bắt buộc của branch policy.

**Purpose:**

Phân biệt liên kết truy vết tùy chọn với yêu cầu bắt buộc do policy cấu hình.

### Scene 1 — Chính sách bắt buộc liên kết work item khi complete PR

**Time:** `05:33–06:27`

**Visual:**

Chỉ chính sách Check for linked work items của nhánh main và PR ví dụ có hoặc chưa gắn item.

Quan sát sandbox được phép hoặc tài liệu Microsoft có nhãn; không nhấn Complete, bypass hoặc bỏ PR production cho mục đích quay phim.

**Script:**

Tại Branch policies, có thể bật Check for linked work items và cấu hình nó là blocking. Nếu PR thiếu liên kết cần thiết, dù reviewer đã Approve và build pass, Complete thông thường vẫn chưa đạt yêu cầu. Đừng tạo work item rỗng chỉ để làm xanh check; liên kết phải là công việc có liên quan thật. Chúng ta đọc trạng thái policy và tìm đúng chủ thể chịu trách nhiệm bổ sung context.

**Purpose:**

Chỉ ra policy linked work items có thể chặn PR.

## Dấu vết PR đã hoàn tất và trạng thái work item liên quan

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:27–06:38`

**Visual:**

Giữ trạng thái Completed, đặt dấu hỏi trên trạng thái của work item đã liên kết.

**Script:**

PR hoàn tất có tự bảo đảm work item liên quan chuyển trạng thái đóng không?

**Purpose:**

Chặn suy luận hoàn tất PR tự làm thay đổi trạng thái công việc.

### Scene 1 — Dấu vết PR đã hoàn tất và trạng thái work item liên quan

**Time:** `06:38–07:34`

**Visual:**

Hiển thị PR Completed có timestamp/reviewer/merge choice bên cạnh trạng thái work item độc lập.

Quan sát sandbox được phép hoặc tài liệu Microsoft có nhãn; không nhấn Complete, bypass hoặc bỏ PR production cho mục đích quay phim.

**Script:**

Sau hoàn tất, PR hiển thị Completed, ai đã review, thời điểm và kiểu merge. Target Git history phản ánh kết quả tích hợp. Work item liên quan có thể thay đổi trạng thái nếu tùy chọn completion và quy trình được cấu hình phù hợp, nhưng không mặc định mọi PR Completed đều tự đóng bug. Trên bản ghi, đọc riêng trạng thái PR và trạng thái work item để tránh kết luận sai về tiến độ nghiệp vụ.

**Purpose:**

Tách trạng thái PR và work item sau merge.

## Tình huống kiểm tra quyền complete PR trên nhánh đích

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:34–07:44`

**Visual:**

Đóng băng PR đủ vote/check, đổi actor sang Charlie và hiện quyền Complete hiệu lực.

**Script:**

Dù mọi policy xanh, người nhấn Complete vẫn cần có quyền hiệu lực.

**Purpose:**

Đặt câu hỏi quyền thực tế trên nhánh đích dù policy có vẻ đều đạt.

### Scene 1 — Tình huống kiểm tra quyền complete PR trên nhánh đích

**Time:** `07:44–08:39`

**Visual:**

Đặt quyền hiệu lực của Charlie trên target main cạnh phiếu Bob Approved và policy Passed, trong khi Complete bị khóa.

Quan sát sandbox được phép hoặc tài liệu Microsoft có nhãn; không nhấn Complete, bypass hoặc bỏ PR production cho mục đích quay phim.

**Script:**

Giả sử Alice tạo PR, Bob Approve và checks pass, nhưng Charlie vẫn không bấm Complete được. Đừng gọi đó là lỗi reviewer: kiểm tra quyền hiệu lực của Charlie đối với target main cùng các quyền cần thiết để hoàn tất. Quyền 'Contribute to pull requests' không tự là quyền Contribute hay quyền bypass nhánh. Video dùng bảng giả định có ghi nhãn để giải thích tình huống; không tự cấp Admin cho Charlie để nút sáng.

**Purpose:**

Chứng minh quyền complete gắn với người thao tác và nhánh đích.

## Chẩn đoán trở ngại do quyền, review vote, policy, check và xung đột

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:39–08:49`

**Visual:**

Giữ lỗi của Charlie, mở bảng kiểm conflicts, votes, checks, link và quyền.

**Script:**

Khi không Complete được, kiểm tra conflict, phiếu, checks, link và quyền actor.

**Purpose:**

Tách lỗi hoàn tất thành từng bằng chứng và đơn vị chịu trách nhiệm.

### Scene 1 — Chẩn đoán trở ngại do quyền, review vote, policy, check và xung đột

**Time:** `08:49–09:47`

**Visual:**

Vẽ checklist theo nhánh: Draft/Abandoned, target, conflicts, votes, threads, checks, work item, quyền actor.

Quan sát sandbox được phép hoặc tài liệu Microsoft có nhãn; không nhấn Complete, bypass hoặc bỏ PR production cho mục đích quay phim.

**Script:**

Gói toàn bộ thành cây quyết định. Nếu PR Draft/Abandoned thì xử lý trạng thái; nếu conflict thì quay về Git; thiếu required vote thì tìm reviewer; build Failed thì chuyển nhóm CI/CD; thiếu work item thì bổ sung context thật; thiếu quyền thì quản trị xác minh quyền hiệu lực tại target. Mỗi nhánh dẫn tới người và công việc khác nhau. Bypass không là lựa chọn mặc định, và không một nhãn xanh đơn lẻ chứng minh tất cả bước đều xong.

**Purpose:**

Tổng hợp từng lý do không Complete được và đường xử lý.
