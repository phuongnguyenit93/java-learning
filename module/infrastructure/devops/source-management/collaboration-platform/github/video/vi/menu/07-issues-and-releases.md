---
video:
  url: ""
---

# Theo dõi Issues và thông tin Releases trên GitHub

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

## GitHub Issues: đơn vị theo dõi công việc và thảo luận

<!-- VIDEO_SECTION -->

### Scene 1 — GitHub Issues: đơn vị theo dõi công việc và thảo luận

**Time:** `00:00–01:06`

**Visual:**

Mở thẻ mô phỏng Issue #42 gồm tiêu đề 'Lỗi làm tròn số tiền âm', dữ liệu -1 và Expected/Observed. Đặt thẻ commit riêng ngoài Issue.

**Script:**

Issue ghi lại vấn đề cần giải quyết, không phải đoạn mã đã sửa. Trong câu chuyện của ta, Issue #42 mô tả trường hợp số tiền âm cho kết quả làm tròn sai và cách tái hiện. Người đọc có thể hỏi thêm trong thảo luận và xác định yêu cầu chấp nhận. Nhưng tạo Issue không làm mã trong main thay đổi. Một nhánh sửa lỗi và pull request sẽ xuất hiện sau, với bằng chứng riêng. Hãy đặt câu hỏi đầu tiên: người đến sau có đủ dữ liệu để hiểu và thử lại vấn đề hay không?

**Purpose:**

Giải thích Issue là hồ sơ công việc, không đánh đồng với commit hay kết quả sửa lỗi.

## Assignees, labels và milestones để phân loại và theo dõi Issues

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:06–01:17`

**Visual:**

Giữ mô tả lỗi của Issue #42 ở trái; lần lượt xuất hiện ba ô Assignee, Label và Milestone chưa điền.

**Script:**

Có một Issue rõ ràng rồi, nhóm cần tìm và theo dõi nó trong nhiều công việc khác.

**Purpose:**

Chuyển từ ghi nhận lỗi sang tổ chức công việc nhưng chưa suy ra lỗi đã được sửa.

### Scene 1 — Assignees, labels và milestones để phân loại và theo dõi Issues

**Time:** `01:17–02:22`

**Visual:**

Hiển thị ba thẻ Assignee: An, Label: bug, Milestone: candidate v1.4.0. Mỗi thẻ nối tới công dụng 'trách nhiệm', 'phân loại', 'nhóm mục tiêu' nhưng không nối tới check 'đã sửa'.

**Script:**

Assignee giúp xác định người đang theo dõi công việc, label giúp lọc như bug hoặc priority, còn milestone gom các Issue theo mục tiêu. Chúng hỗ trợ điều phối nhưng không tự bảo đảm vấn đề đã được sửa. Trong ví dụ, gắn An và mốc dự kiến v1.4.0 giúp nhóm lên kế hoạch; không có gì trong các nhãn đó chứng minh PR đã merge hay bản phát hành đã ra. Khi kiểm tra tiến độ, hãy đi từ hồ sơ Issue sang đề xuất và kết quả tiếp nhận thay vì chỉ nhìn màu nhãn.

**Purpose:**

Dạy ba loại metadata công việc và giới hạn làm bằng chứng hoàn thành.

## Liên kết Issue với pull request và kết quả thay đổi

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:22–02:33`

**Visual:**

Giữ Issue #42 cùng metadata lập kế hoạch, hiện PR #57 chưa merge với liên kết có điều kiện.

**Script:**

Issue #42 và PR #57 là hai hồ sơ. Ta tạo quan hệ để người theo dõi đi đúng giữa nhu cầu và bản sửa.

**Purpose:**

Cho thấy liên kết tăng khả năng truy vết mà chưa chứng minh Issue được đóng hay thay đổi đã tiếp nhận.

### Scene 1 — Liên kết Issue với pull request và kết quả thay đổi

**Time:** `02:33–03:41`

**Visual:**

Vẽ đường nối Issue #42 ↔ PR #57; hiện từ khóa 'Fixes #42' ở mô tả PR, thêm chú thích hành vi đóng tự động phụ thuộc default branch/đích phù hợp.

**Script:**

Liên kết giúp người đọc đi từ lỗi đã ghi tới bản sửa đang đề xuất. GitHub hỗ trợ liên kết thủ công và từ khóa như Fixes trong mô tả PR. Nhưng việc tự đóng Issue theo từ khóa phụ thuộc điều kiện liên quan tới default branch; PR nhắm một nhánh khác không nên được coi là chắc chắn sẽ đóng Issue. Trong storyboard, ta vẽ link trước, rồi để trạng thái Closed trống cho đến khi sự kiện thực tế cho phép. Liên kết không thay thế review, và một Issue đã đóng cũng không chứng minh bản sửa được phát hành.

**Purpose:**

Làm rõ keyword closing của Issue có điều kiện, không tạo hiệu ứng đóng giả.

## Git tag và GitHub Release: hai đối tượng khác nhau

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:41–03:52`

**Visual:**

Theo mũi tên merge giả định của PR #57 tới một commit, đặt Git tag ở đó rồi tạo thẻ Release trống bên cạnh.

**Script:**

Khi thay đổi đã vào nguồn chung, người dùng còn cần biết nhóm công bố phiên bản nào.

**Purpose:**

Phân tách mã đã được tiếp nhận với thao tác gắn nhãn và công bố phiên bản.

### Scene 1 — Git tag và GitHub Release: hai đối tượng khác nhau

**Time:** `03:52–04:57`

**Visual:**

Đặt Git tag v1.4.0 trỏ tới một commit; bên cạnh là thẻ GitHub Release v1.4.0 có title, notes, draft/prerelease và assets. Không nối tag sang deployment tự động.

**Script:**

Git tag là tên tham chiếu trong lịch sử Git, thường đánh dấu một mốc phiên bản. GitHub Release là hồ sơ công bố gắn với tag, có tên, ghi chú và có thể kèm assets. Có tag chưa có nghĩa đã có Release. Cũng như vậy, một Release có mặt trên GitHub không tự xác nhận ứng dụng đã triển khai đến mọi môi trường. Với bản sửa lỗi làm tròn, đội có thể lập tag rồi viết ghi chú cho v1.4.0 sau khi quyết định phát hành; đó là các sự kiện riêng cần kiểm tra.

**Purpose:**

Phân biệt tham chiếu Git với hồ sơ Release và với deployment.

## Ghi chú phát hành, tài nguyên đính kèm và phạm vi quản lý phiên bản

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:57–05:08`

**Visual:**

Giữ tag và Release cạnh nhau, mở rộng thẻ Release thành notes, assets tải lên và source archives GitHub tự tạo.

**Script:**

Release là hồ sơ công bố; làm sao người dùng đọc được thay đổi quan trọng thay vì chỉ thấy số phiên bản?

**Purpose:**

Từ phân biệt hai đối tượng chuyển sang xem thông tin nào giúp người dùng đánh giá bản phát hành.

### Scene 1 — Ghi chú phát hành, tài nguyên đính kèm và phạm vi quản lý phiên bản

**Time:** `05:08–06:16`

**Visual:**

Phác Release notes cho v1.4.0: Fixed negative rounding, Compatibility, Docs; cạnh đó assets và source archives được ngăn bằng đường dọc. Đánh dấu 'ví dụ nội dung cần kiểm chứng'.

**Script:**

Ghi chú phát hành nên nói điều người dùng cần biết: lỗi nào được sửa, tác động tương thích và nơi tìm hướng dẫn. Assets có thể là tệp bản phân phối do người quản lý đính kèm; chúng khác bản nén source GitHub tạo từ tag. Việc tự sinh release notes có thể tiết kiệm thời gian nhưng người duyệt vẫn phải kiểm tra sự thật kỹ thuật và nghiệp vụ. Trong video, đây chỉ là bố cục minh họa cho bản sửa tính tiền, không phải Release đã được đăng. Quy tắc đánh số và quy trình build thuộc những chủ đề khác.

**Purpose:**

Kết thúc bằng thông tin phát hành có trách nhiệm và giới hạn của release metadata.
