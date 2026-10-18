---
video:
  url: ""
---

# Đánh giá pull request và xử lý phản hồi

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

## Mục đích đánh giá mã và trách nhiệm các bên

<!-- VIDEO_SECTION -->

### Scene 1 — Mục đích đánh giá mã và trách nhiệm các bên

**Time:** `00:00–01:30`

**Visual:**

Trên PR `BILL-142`, đặt An (author), Bình (reviewer), Mai (repo policy admin); hiện ba câu hỏi `Mục đích đúng?`, `Logic an toàn?`, `Ai được merge?`.

**Script:**

Review là cuộc kiểm tra có trao đổi: An giải thích quy tắc thuế mới, Bình kiểm tra tác động tới làm tròn, Mai quản trị điều kiện nhóm đã thống nhất. Có quyền quản trị không đồng nghĩa là người giỏi nhất mọi nghiệp vụ. Bình có thể phát hiện một hóa đơn số lẻ chưa được kiểm thử dù diff rất ngắn. An cần phản hồi và bổ sung bằng chứng thay vì chỉ xin thêm dấu Approve. Kết quả cần xem là câu hỏi nào đã được giải quyết, ai thực sự đánh giá và liệu nhánh đích có thể được cập nhật theo chính sách hay chưa.

**Purpose:**

Giải thích trách nhiệm author, reviewer, maintainer và giá trị nghiệp vụ của review.



## Đọc phần khác biệt và bình luận theo tệp hoặc dòng

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:30–01:42`

**Visual:**

Từ dòng làm tròn `Invoice.java` và comment `100.05`, tách một thẻ câu hỏi, một task kiểm thử unresolved và quyết định Changes requested.

**Script:**

Đã rõ vì sao cần người viết và người đánh giá. Bình sẽ xem dòng mã thay đổi và góp ý đúng chỗ bằng cách nào?

**Purpose:**

Phân loại ba loại phản hồi phát sinh từ cùng một dòng code theo mức độ hành động được theo dõi.

### Scene 2 — Đọc phần khác biệt và bình luận theo tệp hoặc dòng

**Time:** `01:42–03:12`

**Visual:**

Zoom tab `Files changed`, highlight dòng làm tròn trên `Invoice.java`, mở một comment có số đầu vào `100.05` rồi theo dõi reply ở activity.

**Script:**

Trên Bitbucket Cloud, phần Files changed giúp Bình xem chính xác những dòng An đề nghị thay. Bình chọn vùng xử lý làm tròn và hỏi: với hóa đơn một trăm phẩy không năm, làm tròn ở bước nào? Câu hỏi gắn với dòng mã, dữ liệu và kết quả mong đợi; nó hữu ích hơn tin nhắn 'đoạn này sai'. An trả lời ngay trong cuộc thảo luận để người đến sau hiểu quyết định. Một comment là bằng chứng đã trao đổi, nhưng không tự động tạo thành task hay approval.

**Purpose:**

Dạy cách gắn feedback vào diff thật và kiểm tra activity mà không giả mạo trạng thái khác.



## Góp ý, yêu cầu chỉnh sửa và tác vụ đánh giá: Vai trò riêng

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:12–03:24`

**Visual:**

Giữ comment/task/review status trên PR; đặt Approve của Bình cạnh `Unresolved task` và `Merge check warning`, giữ nút merge chưa kết luận.

**Script:**

Khi Bình đưa feedback, đâu là câu hỏi thảo luận và đâu là việc phải hoàn tất?

**Purpose:**

Làm rõ một approval không chứng minh task xong hay các điều kiện merge có hiệu lực đã đạt.

### Scene 3 — Góp ý, yêu cầu chỉnh sửa và tác vụ đánh giá: Vai trò riêng

**Time:** `03:24–04:54`

**Visual:**

Ba thẻ trong PR: `Comment: đổi tên biến`, `Task: thêm case 100.05`, `Changes requested`; chỉ task có trạng thái unresolved/resolved.

**Script:**

Bitbucket phân biệt bình luận để trao đổi, task để giao việc có trạng thái hoàn tất, và quyết định Changes requested của reviewer. Bình có thể đề xuất đổi tên biến bằng comment, đồng thời tạo task riêng yêu cầu kiểm thử số lẻ. An sửa mã rồi cập nhật trạng thái task khi bằng chứng đã đủ; Bình vẫn nên kiểm tra lại kết quả. Đừng cho rằng mọi comment tự biến thành task, hoặc task được đánh dấu resolved nghĩa là PR đã được phê duyệt. Ba dấu hiệu giải quyết những nhu cầu khác nhau trong cùng vòng phản hồi.

**Purpose:**

Phân biệt comment, actionable task và review status theo kết quả quan sát.



## Trạng thái phê duyệt so với điều kiện hợp nhất

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:54–05:06`

**Visual:**

Từ thẻ approval chuyển sang hai diff trước/sau An bổ sung test và sửa thêm discount; tô phần mới chưa được Bình đánh giá.

**Script:**

Ngay cả khi reviewer đã approve, điều gì còn có thể khiến PR chưa đủ điều kiện merge?

**Purpose:**

Yêu cầu re-review dựa trên diff mới thay vì dựa vào approval dành cho phiên bản cũ.

### Scene 4 — Trạng thái phê duyệt so với điều kiện hợp nhất

**Time:** `05:06–06:36`

**Visual:**

Dàn trang PR summary: `Binh approved`, `Task open`, `Merge checks unresolved`, `Branch permission`; tô màu từng điều kiện, ghi riêng cảnh báo và chặn.

**Script:**

Một dấu approval nói Bình đã chấp nhận phiên bản anh ấy xem, không khẳng định mọi điều kiện khác đã hoàn tất. Task có thể còn mở, Changes requested có thể cần giải quyết, user thao tác có thể không có quyền merge, hoặc merge check chưa đạt. Đặc biệt, trên Free và Standard những merge checks thường có thể chỉ cảnh báo; trong Premium phải cấu hình khả năng chặn check chưa xử lý thì chúng mới thực thi ngăn merge. Luôn đọc thông báo và quyền ở nhánh đích, đừng quyết định chỉ theo một biểu tượng xanh.

**Purpose:**

Giải thích vì sao approval là một tín hiệu, không phải điều kiện duy nhất; tách advisory khỏi enforced.



## Cập nhật thay đổi và yêu cầu đánh giá lại

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:36–06:48`

**Visual:**

Giữ phần discount diff mới cùng thẻ re-review; kéo target `main` vào khung branch restrictions và người có quyền merge.

**Script:**

An vừa sửa theo feedback; liệu Bình có thể dựa vào lần review cũ?

**Purpose:**

Chuyển từ chất lượng phiên bản hiện tại sang giới hạn hành động trên nhánh tích hợp quan trọng.

### Scene 5 — Cập nhật thay đổi và yêu cầu đánh giá lại

**Time:** `06:48–08:18`

**Visual:**

So hai revisions của cùng PR: `Before: tax 10%` và `After: added rounding test + discount edit`; tô vùng ngoài phạm vi và dấu `Need another review`.

**Script:**

Sau khi Bình yêu cầu ví dụ hóa đơn số lẻ, An đẩy một revision mới thêm test. Nhưng An vô tình sửa thêm logic giảm giá, khiến phạm vi khác với bản Bình vừa duyệt. Reviewer cần xem lại diff mới, xác nhận task đã giải quyết thật và kiểm tra những thay đổi ngoài mong đợi. Một số chính sách Premium có thể buộc xử lý approvals khi source branch thay đổi, tùy cấu hình; đừng mặc định mọi PR tự thu hồi hoặc giữ nguyên phê duyệt theo một quy tắc chung. Kết luận là review phải dựa trên nội dung hiện tại.

**Purpose:**

Minh họa nhu cầu re-review và tránh gán chính sách reset approval mặc định.
