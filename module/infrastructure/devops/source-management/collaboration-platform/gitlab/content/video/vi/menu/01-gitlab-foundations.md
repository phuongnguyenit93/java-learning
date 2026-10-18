---
video:
  url: ""
---

# Nền tảng cộng tác mã nguồn trên GitLab

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

## GitLab: nền tảng lưu trữ Git và phối hợp thay đổi của nhóm

<!-- VIDEO_SECTION -->

### Scene 1 — GitLab: nền tảng lưu trữ Git và phối hợp thay đổi của nhóm

**Time:** `00:00–01:10`

**Visual:**

Mở sơ đồ ba tệp ZIP cùng tên `final`, sau đó thay bằng một project GitLab minh họa. Lần lượt sáng mục Code, Merge requests, Issues và Members; gắn nhãn đây là storyboard, không phải repo thật.

**Script:**

Ba người gửi ba bản mã qua chat, cả ba đều ghi cuối cùng. Chúng ta sẽ chọn bản nào? GitLab không tự quyết định hộ nhóm, nhưng tạo một nơi để gắn lịch sử Git với đề xuất, người đánh giá và quyết định hợp nhất. Hãy nhìn project gateway: Code là nguồn theo nhánh, Merge requests ghi việc xin đưa thay đổi vào nhánh chung, Issues lưu vấn đề. Thử theo dõi lỗi hóa đơn số âm: biết code đã được ghi chưa vẫn khác câu hỏi nhóm đã đồng ý nhận nó chưa.

Từ ví dụ này ta sẽ đi qua project/group và quyền truy cập, nhánh chung hoặc fork, MR và review, quy tắc protected branch, Issue và Release, rồi ghép toàn bộ thành một lần cộng tác. Những thao tác commit chi tiết thuộc Git, còn triển khai hệ thống thuộc CI/CD.

**Purpose:**

Xây câu hỏi nền tảng về nguồn dùng chung và bằng chứng cộng tác trước khi nói tới tên tính năng.

## Rủi ro khi chia sẻ mã nguồn thiếu theo dõi, review và điều kiện hợp nhất

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:10–01:23`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "nơi chung để đọc thay đổi và quyết định". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Đã có nơi lưu nguồn, nhưng lịch sử ghi nhận vẫn không nói rằng hành vi vừa sửa là đúng. Ta xem rủi ro còn lại.

**Purpose:**

Chuyển từ nơi chung để đọc thay đổi và quyết định sang câu hỏi review giảm rủi ro chứ không thay kiểm thử theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Rủi ro khi chia sẻ mã nguồn thiếu theo dõi, review và điều kiện hợp nhất

**Time:** `01:23–02:37`

**Visual:**

Bảng minh họa Issue #42 ghi `-1 phải bị từ chối`; cạnh đó MR !57 đề xuất `clamp` số âm thành 0. Đánh dấu rõ sai lệch yêu cầu, phần chưa có test và người chưa review, không hiển thị check xanh giả.

**Script:**

Nếu An sửa hàm để biến -1 thành 0 mà Issue yêu cầu từ chối giá trị âm, dòng code có thể biên dịch nhưng vẫn sai nghiệp vụ. Không có mô tả và MR, Bình khó biết điều gì đã được thay đổi, ai đọc nó và lý do được nhận. Hãy đặt yêu cầu ở một bên, diff ở bên kia, rồi hỏi kiểm thử nào chứng minh kết quả đúng. Review có thể phát hiện mâu thuẫn này, nhưng tự nó không thay được dữ liệu kiểm chứng. Chúng ta chưa chạy test thật, nên ô kết quả được để trống.

**Purpose:**

Làm hiện ra khoảng cách giữa có bản sửa và có bằng chứng đúng nghiệp vụ; ngăn minh họa giả làm kết quả.

## Lịch sử phiên bản do Git quản lý và quá trình cộng tác trên GitLab

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:37–02:50`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "review giảm rủi ro chứ không thay kiểm thử". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Khi thiếu bằng chứng review, phải hỏi công cụ nào giữ lịch sử và công cụ nào chịu trách nhiệm cho quyết định.

**Purpose:**

Chuyển từ review giảm rủi ro chứ không thay kiểm thử sang câu hỏi Git ghi lịch sử, GitLab quản lý cộng tác theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Lịch sử phiên bản do Git quản lý và quá trình cộng tác trên GitLab

**Time:** `02:50–03:58`

**Visual:**

Chia màn hình: đồ thị commit và branch trên máy ở bên trái; project GitLab, MR và policy ở bên phải. Mũi tên đề xuất đi qua ranh giới, còn thao tác Git không bị tô thành approval.

**Script:**

An vẫn có thể ghi commit trên laptop khi không vào GitLab. Đây là việc của Git: lưu trạng thái và quan hệ lịch sử. GitLab cung cấp project, nhóm người, thảo luận MR và quyền hợp nhất. Vì vậy câu 'em đã merge trên máy' không trả lời được MR !57 đã có review hợp lệ hay chưa. Chúng ta sẽ đọc bằng chứng cộng tác ở GitLab, chứ không học lại cách dùng lệnh reset hay merge. Hai lớp nối với nhau, nhưng không thay thế bằng chứng của nhau.

**Purpose:**

Phân ranh kỹ thuật Git và nền tảng host để tránh nhập nhằng lịch sử với quy trình duyệt.

## Project GitLab và Git repository: phạm vi cộng tác và lịch sử nguồn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:58–04:11`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "Git ghi lịch sử, GitLab quản lý cộng tác". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Nếu GitLab quản lý cả lịch sử lẫn quyết định, project chứa những đối tượng nào ngoài Git repository?

**Purpose:**

Chuyển từ Git ghi lịch sử, GitLab quản lý cộng tác sang câu hỏi project rộng hơn Git repository theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Project GitLab và Git repository: phạm vi cộng tác và lịch sử nguồn

**Time:** `04:11–05:21`

**Visual:**

Vẽ một hộp Project `gateway` chứa repository, Issues, Merge requests, Members và Settings. Đặt tệp DiscountPolicy ở nhánh `fix-rounding` và MR !57 nằm ngoài `main`, từng vùng sáng khác nhau.

**Script:**

Đừng xem GitLab project là một thư mục mã duy nhất. Repository trong project giữ các tệp theo nhánh và commit, còn Issue có thể tồn tại trước khi ai viết dòng code đầu tiên. Một MR cũng có thể xuất hiện dù main chưa mang thay đổi ở nhánh nguồn. Trong cảnh này ta chỉ xem cấu trúc project và phần khác biệt của fix-rounding, không tuyên bố lỗi đã sửa trên production. Khi reviewer đọc MR, họ cần biết mình đang nhìn nội dung của nhánh nào và hồ sơ cộng tác nào.

**Purpose:**

Dựng mô hình project như ranh giới quản lý, phân biệt nhánh nguồn với nguồn đã tích hợp.

## Namespace cá nhân, group và subgroup: các cấp tổ chức nguồn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:21–05:34`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "project rộng hơn Git repository". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Project là một phạm vi cộng tác. Nếu có nhiều project, GitLab sắp xếp owner của chúng như thế nào?

**Purpose:**

Chuyển từ project rộng hơn Git repository sang câu hỏi namespace là ranh giới sở hữu, không phải Git lồng nhau theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Namespace cá nhân, group và subgroup: các cấp tổ chức nguồn

**Time:** `05:34–06:41`

**Visual:**

Lắp đường dẫn `company/payments/gateway`: mỗi phần gắn thẻ group, subgroup, project. Đặt mũi tên quyền kế thừa từ parent xuống, nhưng không vẽ ba repository lồng nhau.

**Script:**

Đường dẫn company, payments, gateway trông như nhiều thư mục, nhưng ba cấp không phải ba Git repository. Company là group, payments là subgroup và gateway là project mang repository của ứng dụng. Cách chia này giúp nhóm quản lý quyền và trách nhiệm trên nhiều project; một số chính sách hoặc quyền có thể kế thừa theo cây tổ chức. Hãy quan sát khu vực Groups, Subgroups and Projects thay vì đọc namespace như cấu trúc package Java. Đến phần membership, ta sẽ kiểm tra nguồn quyền thực sự.

**Purpose:**

Chuyển nhận thức từ đường dẫn hiển thị sang ranh giới tổ chức và kế thừa quyền.

## Trách nhiệm của thành viên, người đóng góp, assignee và reviewer

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:41–06:54`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "namespace là ranh giới sở hữu, không phải Git lồng nhau". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Đường dẫn cho biết project thuộc ai, nhưng bên trong project những người tham gia có trách nhiệm khác nhau.

**Purpose:**

Chuyển từ namespace là ranh giới sở hữu, không phải Git lồng nhau sang câu hỏi role, đóng góp, assignee, reviewer là bốn nghĩa khác nhau theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Trách nhiệm của thành viên, người đóng góp, assignee và reviewer

**Time:** `06:54–08:05`

**Visual:**

Đặt An cạnh Issue #42, Bình cạnh Reviewer MR !57, Chi cạnh Maintainer trên project gateway; mỗi người có thẻ membership riêng không được tự nâng cấp bởi thẻ công việc.

**Script:**

An nhận Issue #42 chưa chắc là người có thể merge vào main. Bình được yêu cầu review MR !57 không vì thế trở thành Maintainer. Chi vận hành nhánh được bảo vệ nhưng không tự biết phép tính hóa đơn là đúng. Chúng ta đặt cạnh mỗi người ba ô: đã làm gì, đang được giao gì và role có hiệu lực ra sao. Quy tắc cấm tự approve cũng có thể ảnh hưởng tác giả trên những gói và cấu hình phù hợp. Đọc đúng vai trò giúp tránh bỏ sót trách nhiệm đánh giá.

**Purpose:**

Tách chức danh hiển thị khỏi quyền và nhiệm vụ thực tế trong một MR.

## Merge request: đề xuất thay đổi để thảo luận và đánh giá trước khi hợp nhất

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:05–08:18`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "role, đóng góp, assignee, reviewer là bốn nghĩa khác nhau". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Sau khi biết người chịu trách nhiệm, nhóm cần một hồ sơ chung để đề xuất một thay đổi cụ thể.

**Purpose:**

Chuyển từ role, đóng góp, assignee, reviewer là bốn nghĩa khác nhau sang câu hỏi MR là đề xuất, không phải mã đã nhận theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Merge request: đề xuất thay đổi để thảo luận và đánh giá trước khi hợp nhất

**Time:** `08:18–09:28`

**Visual:**

Dựng thẻ MR !57 với source `fix-rounding`, target `main`, Description/Changes/Discussions/Approval. Thẻ Merged để trống; chỉ làm sáng yêu cầu review.

**Script:**

Một merge request nối nơi chứa đề xuất với nhánh đích dự kiến tiếp nhận. An mở MR !57 để giải thích lỗi số âm và cho Bình xem diff trước khi mã vào main. MR lưu mô tả, trao đổi, reviewer và trạng thái phê duyệt nếu tính năng áp dụng. Nhưng việc trang MR đã xuất hiện không chứng minh người có thẩm quyền đã đồng ý, càng không chứng minh pipeline đã chạy thành công. Ta dừng ở nhánh nguồn, nhánh đích và bằng chứng cần review trước khi bàn cách merge.

**Purpose:**

Đặt mô hình source/target và trạng thái MR đúng trước các cơ chế phê duyệt nâng cao.

## Quan hệ project, nhánh nguồn/đích, merge request và reviewer

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:28–09:41`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "MR là đề xuất, không phải mã đã nhận". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Ta đã biết project, người tham gia và MR; giờ ghép chúng thành một đường đi có thể truy vết.

**Purpose:**

Chuyển từ MR là đề xuất, không phải mã đã nhận sang câu hỏi các bước có bằng chứng riêng theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Quan hệ project, nhánh nguồn/đích, merge request và reviewer

**Time:** `09:41–10:55`

**Visual:**

Nối các thẻ Issue #42 → source fix-rounding → MR !57 → review/rules → target main → Release v1.4, vẽ mũi tên nét đứt từ merge sang Release và deployment.

**Script:**

Ta có thể lần theo một thay đổi mà không nhầm các sự kiện. Issue #42 nói tại sao cần sửa, nhánh fix-rounding chứa phương án của An, MR !57 mang đề xuất đến reviewer và người được phép merge. Protected branch cùng approval rule nếu có quyết định điều kiện tiếp nhận. Sau đó nhóm mới có thể tạo Release v1.4, nhưng merge không tự biến thành bản phát hành hay triển khai. Mỗi đoạn mũi tên trong sơ đồ cần một dấu vết riêng trên GitLab. Chương sau sẽ bắt đầu từ nơi project và group sở hữu các dấu vết đó.

**Purpose:**

Chốt bức tranh đầu-cuối với điều kiện thật và mở đường sang ownership/visibility.
