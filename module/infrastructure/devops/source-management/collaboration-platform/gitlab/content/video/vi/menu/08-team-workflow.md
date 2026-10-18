---
video:
  url: ""
---

# Tổng hợp luồng cộng tác nhóm với GitLab

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

## Từ Issue tới đóng góp, review, phê duyệt và merge

<!-- VIDEO_SECTION -->

### Scene 1 — Từ Issue tới đóng góp, review, phê duyệt và merge

**Time:** `00:00–01:14`

**Visual:**

Dựng timeline Issue #42 → branch fix-rounding hoặc fork → MR !57 vào main → Bình review → Chi xét merge → main. Mỗi mốc chỉ tô sáng khi mô tả evidence cần đọc, không giả trạng thái real.

**Script:**

Ta nối toàn bộ câu chuyện lỗi số tiền âm. Issue #42 yêu cầu từ chối đầu vào -1. An tạo thay đổi trong nhánh làm việc hoặc fork, mở MR !57 đúng project và main đích, rồi ghi cách xác nhận hành vi trước sau. Bình xem diff và đặt câu hỏi; An bổ sung bằng chứng và đề nghị review lại. Chỉ khi những người có quyền phù hợp đáp ứng rule và các check áp dụng, Chi mới có thể merge. Mũi tên là chuỗi điều kiện, không khẳng định chúng ta đã thực thi một MR trên GitLab thật.

**Purpose:**

Tổng hợp đường cộng tác end-to-end bằng từng loại chứng cứ mà learner phải nhận biết.

## Trở ngại do vai trò thành viên, quyền truy cập và reviewer

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:14–01:27`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "Issue đến MR merge có những điểm kiểm chứng riêng". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Trong quy trình hoàn chỉnh, một MR có thể dừng sớm do người tham gia không đủ quyền hoặc không đúng reviewer.

**Purpose:**

Chuyển từ Issue đến MR merge có những điểm kiểm chứng riêng sang câu hỏi thiếu quyền xem và thiếu eligible approval là hai lỗi khác nhau theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Trở ngại do vai trò thành viên, quyền truy cập và reviewer

**Time:** `01:27–02:36`

**Visual:**

Cây hai nhánh: Contractor cannot view private gateway → membership/visibility; Bình reads project nhưng approval không được tính → tier/rule/eligibility. Không hiện tài khoản hoặc token thật.

**Script:**

Nếu contractor không nhìn thấy gateway private, phải kiểm tra visibility và nguồn membership trước khi nói tới sửa code. Bình có thể đọc MR và để lại nhận xét nhưng phiếu approve của Bình chưa chắc thỏa rule có chọn nhóm khác. Hai tình huống có bề ngoài là 'không tiến được' nhưng giải pháp khác nhau: quyền đọc, đường fork, người phê duyệt hợp lệ hoặc quy tắc review đúng phạm vi. Ta ghi actor, project và hành động đang thử, không nâng tất cả lên Maintainer để tránh triệu chứng.

**Purpose:**

Rèn thói quen chẩn đoán tầng quyền và eligibility thay vì quy lỗi chung cho GitLab.

## Trở ngại do protected branch, approval rules và trạng thái kiểm tra

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:36–02:49`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "thiếu quyền xem và thiếu eligible approval là hai lỗi khác nhau". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Khi actor đủ quyền nhưng merge chưa được, chúng ta cần đọc những điều kiện gắn với target chứ không sửa mã tùy tiện.

**Purpose:**

Chuyển từ thiếu quyền xem và thiếu eligible approval là hai lỗi khác nhau sang câu hỏi một Approve không vượt qua mọi protection gate theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Trở ngại do protected branch, approval rules và trạng thái kiểm tra

**Time:** `02:49–04:02`

**Visual:**

Mở bản vẽ merge widget MR !57 gồm Ready, Allowed to merge, Approval rules, Code Owner, Discussions, Pipeline. Cạnh đó hai pattern `main` và `m*` có ghi quy tắc precedence.

**Script:**

Giả sử reviewer đã Approve nhưng merge widget vẫn thông báo chưa sẵn sàng. Hãy kiểm tra MR đã Ready, target main đúng chưa, người thao tác có quyền merge vào protected branch hay không. Tiếp theo đọc phiếu approval hợp lệ, Code Owner, Request changes nếu gói hỗ trợ, discussion và trạng thái pipeline nếu được yêu cầu. Đừng bỏ qua wildcard protection: GitLab có cơ chế access rộng nhất và yêu cầu Code Owner nghiêm nhất trong các rule khớp. Đây là danh sách cần đọc trên instance thật, không phải kết quả của một pipeline đã được video chạy.

**Purpose:**

Tổng hợp đầy đủ các loại blocker với giới hạn tier và rule precedence đặc thù GitLab.

## Liên hệ thay đổi đã merge với thông tin Release

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:02–04:15`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "một Approve không vượt qua mọi protection gate". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Sau khi tìm được đường merge hợp lệ, còn phải kiểm tra bằng chứng công việc và bản phát hành riêng biệt.

**Purpose:**

Chuyển từ một Approve không vượt qua mọi protection gate sang câu hỏi Merged, Issue Closed và Release Published không đồng nhất theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Liên hệ thay đổi đã merge với thông tin Release

**Time:** `04:15–05:27`

**Visual:**

Ba thẻ PR !57 Merged, Issue #42 Closed?, Release v1.4.0 Published? và thêm Deployed? ở cột riêng. Các dấu hỏi giữ nguyên cho tới khi có record thật.

**Script:**

Nếu GitLab ghi MR !57 Merged, ta có bằng chứng thay đổi được tích hợp vào nhánh đích theo trạng thái của MR. Không vì thế tự suy Issue #42 đã Closed: cần xem đường liên kết và timeline, nhất là điều kiện default branch. Release v1.4.0 là công bố theo tag và notes, cũng có thể diễn ra sau đó. Ngay cả khi cả ba mốc có thật, vẫn chưa đủ nói hệ thống production đã dùng phiên bản mới. Hãy báo trạng thái chính xác từng mốc và chỉ đưa link khi hồ sơ tương ứng tồn tại.

**Purpose:**

Kết luận trung thực về kết quả thay đổi, đóng việc, công bố và deployment như những sự kiện khác nhau.

## Bàn giao sang Git mechanics, branching strategy, GitLab CI/CD và bảo mật chuyên sâu

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:27–05:40`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "Merged, Issue Closed và Release Published không đồng nhất". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Toàn bộ quy trình đã có nghĩa; phần cuối đặt đúng ranh giới giữa cộng tác, lịch sử mã và phân phối sản phẩm.

**Purpose:**

Chuyển từ Merged, Issue Closed và Release Published không đồng nhất sang câu hỏi GitLab cộng tác không thay thế Git, chiến lược nhánh hay CI theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Bàn giao sang Git mechanics, branching strategy, GitLab CI/CD và bảo mật chuyên sâu

**Time:** `05:40–06:54`

**Visual:**

Bốn vùng: Git history, GitLab projects/MRs/policy, Branching Strategy, GitLab CI/CD & specialized security. Kéo Issue #42/MR !57/tag/Release vào vùng thích hợp và để pipeline job ngoài module.

**Script:**

Ở cảnh cuối, chúng ta phải biết ai sở hữu phần kiến thức nào. Git xử lý commit, nhánh và lịch sử. GitLab tổ chức project, group, merge request, quyền và điều kiện tiếp nhận. Branching Strategy giải thích đội nên tích hợp thường xuyên hay có release branch dài hạn. GitLab CI/CD mới phụ trách tự động chạy build, test và triển khai; bảo mật chuyên sâu có nội dung riêng. Nếu MR merge nhưng production chưa đổi, đừng sửa role hoặc tắt protection: hãy xem bằng chứng build/deploy thuộc workflow khác. Biết đúng ranh giới giúp đặt câu hỏi đúng nơi.

**Purpose:**

Đóng lại mental model về platform ownership mà không mở rộng sang cú pháp Git/CI hoặc API giả.
