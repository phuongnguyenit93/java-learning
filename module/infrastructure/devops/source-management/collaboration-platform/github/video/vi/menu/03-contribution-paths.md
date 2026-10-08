---
video:
  url: ""
---

# Con đường đóng góp và tạo pull request

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

## Đóng góp qua nhánh trong repository chung

<!-- VIDEO_SECTION -->

### Scene 1 — Đóng góp qua nhánh trong repository chung

**Time:** `00:00–01:02`

**Visual:**

Dùng sơ đồ repository orchid/payments với main và nhánh fix-rounding. Chỉ nhánh công việc được tô màu thay đổi, một mũi tên PR hướng về main, không vẽ merge đã xảy ra.

**Script:**

An có quyền Write trên kho chung và muốn sửa lỗi hóa đơn. Thay vì đẩy thẳng vào main, An chuẩn bị thay đổi trên nhánh fix-rounding. Sự tách biệt giúp người khác xem phần chênh lệch bằng pull request trước khi mã đến nhánh chung. Đây là một cách cộng tác thường dùng khi thành viên đã được cấp quyền phù hợp. Chúng ta đang quan sát đường đi của đề xuất trên GitHub, không học lại các lệnh tạo nhánh Git hay giả định mọi nhánh đều được phép merge.

**Purpose:**

Minh họa dòng đóng góp trong repository chung và vai trò review trước acceptance.

## Fork là repository riêng và quan hệ với upstream

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:02–01:13`

**Visual:**

Dịch nhánh fix-rounding của người có quyền Write sang một bên; hiện repository contributor/payments và mũi tên fork từ orchid/payments.

**Script:**

Trong kho chung, An cần quyền ghi. Nếu người đóng góp không có quyền ấy ở repository gốc thì sao?

**Purpose:**

Giải thích vì sao thiếu quyền ghi upstream dẫn đến đường đóng góp qua repository riêng.

### Scene 1 — Fork là repository riêng và quan hệ với upstream

**Time:** `01:13–02:15`

**Visual:**

So sánh hai hộp orchid/payments và contributor/payments. Một mũi tên fork tạo hộp thứ hai, mũi tên pull request trở lại upstream; nhấn mạnh đường biên quyền của hai kho.

**Script:**

Fork là một repository riêng với chủ sở hữu và phạm vi quyền của chính nó, không phải tên khác của nhánh. Người ngoài có thể làm việc trong fork rồi gửi đề xuất về upstream, nếu chính sách repository cho phép. Fork không cấp quyền đẩy vào kho gốc. Trong sơ đồ, nhánh sửa lỗi của An nằm ở fork còn PR hướng đến main của orchid/payments. Hãy nhìn hai namespace: phần nguồn và phần đích phải được kiểm tra tách biệt trước khi nhận xét về khả năng đóng góp.

**Purpose:**

Phân biệt fork với branch và chỉ rõ ranh giới quyền sở hữu giữa upstream và fork.

## Repository và nhánh head/base của pull request

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:15–02:26`

**Visual:**

Giữ hai hộp upstream và fork, đặt nhãn head/base lên từng nhánh và thử chỉ mũi tên sang đích sai.

**Script:**

Hai repository có thể cùng tham gia một PR. Để không gửi sai nơi, ta kiểm tra hướng so sánh.

**Purpose:**

Biến quan hệ fork thành bước kiểm tra hướng PR trước khi xem diff.

### Scene 1 — Repository và nhánh head/base của pull request

**Time:** `02:26–03:26`

**Visual:**

Đặt thẻ head: contributor/payments:fix-rounding và base: orchid/payments:main; đảo thử hai đầu để cho thấy nhãn 'đích sai' và diff có thể khác, rồi trả lại hướng đúng.

**Script:**

Trước khi tạo PR, hãy đọc hai ô head và base. Head là nơi đang chứa thay đổi đề xuất; base là nơi ta muốn đưa thay đổi vào. Một fork có thể dùng tên nhánh khác repository gốc; tên giống nhau cũng không chứng minh hướng đã đúng. Trong PR #57 minh họa, ta giữ head ở fix-rounding và base ở main. Nếu gửi nhầm sang nhánh bảo trì hoặc đảo chiều, reviewer có thể nhìn một tập thay đổi không phải điều nhóm muốn tiếp nhận.

**Purpose:**

Dạy kiểm tra đúng head/base repository và branch trước khi nhờ review.

## Pull request nháp và trạng thái sẵn sàng đánh giá

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:26–03:37`

**Visual:**

Giữ mũi tên head/base đúng, chồng nhãn Draft và danh sách kiểm thử còn thiếu lên PR #57.

**Script:**

Chọn đúng head/base chưa có nghĩa mã sẵn sàng được phê duyệt. GitHub có trạng thái cho công việc còn dở.

**Purpose:**

Tách việc chọn đúng nguồn/đích khỏi tuyên bố đề xuất đã sẵn sàng đánh giá.

### Scene 1 — Pull request nháp và trạng thái sẵn sàng đánh giá

**Time:** `03:37–04:44`

**Visual:**

Dựng timeline Draft PR → Ready for review. Ở Draft bật vùng Comments nhưng để Merge khóa; chỉ khi sang Ready mới hiện biểu tượng tự yêu cầu CODEOWNERS phù hợp.

**Script:**

An có thể mở Draft PR để chia sẻ sớm cách sửa, xin ý kiến về thiết kế hoặc chứng minh lỗi trước khi đủ kiểm thử. Người khác vẫn xem và bình luận, nhưng PR nháp không thể merge. Khi An chuyển sang Ready for review, GitHub mới tự yêu cầu review từ code owner phù hợp nếu repository có CODEOWNERS và điều kiện truy cập được đáp ứng. Việc được mời review không bảo đảm ai đã Approve. Hãy nhìn hai mốc rõ ràng: thời điểm cộng tác sớm và thời điểm tác giả tuyên bố sẵn sàng đánh giá.

**Purpose:**

Phân biệt Draft và Ready, đặc biệt thời điểm CODEOWNERS review request.

## Mở, cập nhật, thảo luận, hợp nhất hoặc đóng pull request

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:44–04:55`

**Visual:**

Đổi nhãn Draft thành Ready, mở sơ đồ trạng thái cập nhật, Merged và Closed without merge.

**Script:**

Sau khi PR sẵn sàng, hành trình còn nhiều kết quả có thể xảy ra. Ta đọc trạng thái chứ không đoán.

**Purpose:**

Liên hệ trạng thái sẵn sàng với các kết quả vòng đời PR mà không mặc định sẽ merge.

### Scene 1 — Mở, cập nhật, thảo luận, hợp nhất hoặc đóng pull request

**Time:** `04:55–05:57`

**Visual:**

Sơ đồ Open → review/update → Merged hoặc Closed without merge. Hiện commit mới thêm vào head làm Files changed cập nhật; hai kết quả cuối được tô màu khác.

**Script:**

Một PR được mở không đồng nghĩa sẽ được nhận. Tác giả có thể thêm thay đổi vào head, reviewer đọc lại, yêu cầu sửa hoặc chấp thuận. Đích cuối có thể là Merged hoặc Closed without merge. Cùng chữ Closed trong một vị trí không đủ để kết luận mã đã vào main; cần xem dấu Merged và bằng chứng ở nhánh đích. Ta cũng không tự giả định Issue liên quan đã đóng hay Release đã công bố. Những mốc đó cần kiểm tra độc lập ở các chương sau.

**Purpose:**

Đóng chương bằng cách đọc trạng thái vòng đời PR chính xác và tránh đánh đồng Closed với Merged.
