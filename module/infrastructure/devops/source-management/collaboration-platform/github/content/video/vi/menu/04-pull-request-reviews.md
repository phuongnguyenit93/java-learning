---
video:
  url: ""
---

# Đánh giá mã nguồn và quyết định pull request

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

## Ngữ cảnh review: mô tả, khác biệt, thảo luận và kết quả kiểm tra

<!-- VIDEO_SECTION -->

### Scene 1 — Ngữ cảnh review: mô tả, khác biệt, thảo luận và kết quả kiểm tra

**Time:** `00:00–01:04`

**Visual:**

Mở bản dựng PR #57: Description có điều kiện tái hiện, Files changed tô phép làm tròn, Checks ghi 'chưa có kết quả minh họa'; dùng kính lúp đi theo ba vùng.

**Script:**

Bình không nên mở diff rồi đọc từng dòng một cách vô định. Trước tiên Bình hỏi vì sao PR #57 tồn tại, hóa đơn nào sai và nhóm mong đợi kết quả nào. Sau đó Bình so phần mô tả với Files changed, các commit và thảo luận. Kết quả kiểm tra nếu được báo về GitHub là một loại bằng chứng khác, không thay thế việc hiểu nghiệp vụ. Trong video ta chưa chạy test thật, nên khung Checks chỉ nêu vị trí cần kiểm tra, không tô xanh để giả vờ thành công.

**Purpose:**

Tổ chức cách đọc ngữ cảnh PR trước diff và phân biệt kết quả checks với kết luận nghiệp vụ.

## Bình luận, phê duyệt và yêu cầu sửa trong pull request

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:04–01:15`

**Visual:**

Đóng băng diff làm tròn cạnh phần mô tả, đổi kính lúp thành ba thẻ Review: Comment, Approve và Request changes.

**Script:**

Đọc diff mới là thu thập ngữ cảnh. Người đánh giá còn phải ghi quyết định theo đúng ý nghĩa.

**Purpose:**

Nối thao tác đọc bằng chứng với quyết định review có ý nghĩa khác nhau đối với merge.

### Scene 1 — Bình luận, phê duyệt và yêu cầu sửa trong pull request

**Time:** `01:15–02:19`

**Visual:**

Cạnh cùng đoạn code đặt ba thẻ Review: Comment, Approve, Request changes. Với Comment giữ cờ 'không phải phê duyệt'; với Request changes chỉ vẽ khóa khi rules yêu cầu.

**Script:**

Bình có thể viết bình luận hỏi về số âm mà chưa đưa ra quyết định chấp thuận. Trên GitHub, Comment, Approve và Request changes là ba lựa chọn khác nhau. Một nhận xét trên dòng mã không tự biến thành yêu cầu sửa đang chặn merge. Khi nhánh đích có quy tắc review bắt buộc, GitHub xét các review hợp lệ và điều kiện đã cấu hình. Để biết PR sẵn sàng chưa, hãy đọc quyết định và quy tắc thực tế, không chỉ đếm bong bóng bình luận hoặc biểu tượng phê duyệt.

**Purpose:**

Dạy ba trạng thái review và ranh giới giữa phản hồi với điều kiện merge bắt buộc.

## Phản hồi góp ý, cập nhật thay đổi và yêu cầu xem xét lại

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:19–02:30`

**Visual:**

Ghim thẻ Request changes của Bình vào nhận xét đầu vào âm; thêm dấu commit cập nhật và lượt xem lại.

**Script:**

Nếu Bình yêu cầu sửa, ta phải nhìn phần thay đổi sau góp ý thay vì dựa vào lời 'đã sửa'.

**Purpose:**

Cho thấy phản hồi phải dẫn tới thay đổi có thể kiểm chứng và review lại.

### Scene 1 — Phản hồi góp ý, cập nhật thay đổi và yêu cầu xem xét lại

**Time:** `02:30–03:37`

**Visual:**

Timeline: Bình hỏi về âm → An bổ sung trường hợp thử → Files changed cập nhật → Bình xem lại. Đặt nhãn 'review trước ≠ bảo chứng cho commit mới'.

**Script:**

An nhận phản hồi, phân loại đó là lỗi nghiệp vụ, thiếu kiểm thử hay góp ý tùy chọn. Sau khi sửa nhánh head, phần khác biệt trong PR được cập nhật. An nên giải thích điều đã đổi và chủ động mời người liên quan xem lại, vì reviewer không nhất thiết biết mọi commit vừa được thêm. Nếu quy tắc bảo vệ đặt chế độ hủy phê duyệt cũ khi có thay đổi mới, trạng thái yêu cầu review sẽ bị ảnh hưởng. Không thể suy ra một lượt Approve hôm qua áp dụng mãi cho phiên bản hôm nay.

**Purpose:**

Gắn chuỗi feedback→rework→re-review với dữ liệu khác biệt và quy tắc stale review.

## Yêu cầu reviewer, nhóm đánh giá và cơ chế CODEOWNERS

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:37–03:48`

**Visual:**

Đưa đường dẫn Files changed vừa sửa vào sơ đồ dò CODEOWNERS, tách hai lối tới nhóm billing và UI.

**Script:**

Ai sẽ được mời xem? Khi thay đổi nhiều thư mục, chọn đúng người có chuyên môn là một bước riêng.

**Purpose:**

Chuyển từ kiểm tra bản sửa sang xác định người phù hợp và có quyền xem xét.

### Scene 1 — Yêu cầu reviewer, nhóm đánh giá và cơ chế CODEOWNERS

**Time:** `03:48–04:51`

**Visual:**

Bản đồ đường dẫn: billing/** → @orchid/billing-reviewers; ui/** → @orchid/ui-reviewers. PR đổi billing/Invoice.java phát sáng người có thể được request; thêm biểu tượng kiểm tra quyền Write phù hợp.

**Script:**

GitHub cho phép yêu cầu cá nhân hoặc team review. Tệp CODEOWNERS mô tả người/nhóm có trách nhiệm theo đường dẫn thay đổi, nên có thể giúp GitHub tự định tuyến lời mời khi PR sẵn sàng. Nhưng ghi tên trong CODEOWNERS không tự cấp quyền truy cập hay quyền review hợp lệ. Chúng ta nhìn đường dẫn billing/Invoice.java, kiểm tra pattern nào khớp và nhóm đó đã có quyền phù hợp chưa. Người được mời vẫn phải thực sự xem nội dung; lời mời chưa nói gì về kết luận của họ.

**Purpose:**

Biến CODEOWNERS thành sơ đồ định tuyến reviewer có điều kiện quyền, không thành ACL.

## CODEOWNERS ở nhánh đích yêu cầu review khi PR sẵn sàng, khác phê duyệt bắt buộc

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:51–05:02`

**Visual:**

Phóng to pattern CODEOWNERS khớp billing rồi chia hai trạng thái 'đã mời review' và 'quy tắc bắt buộc approve' lấy từ base main.

**Script:**

Được tự động nhắc review vẫn khác với bắt buộc có phê duyệt trước khi merge. Ta đặt hai cơ chế cạnh nhau.

**Purpose:**

Tránh nhầm tự động định tuyến người xem xét với điều kiện phê duyệt bắt buộc.

### Scene 1 — CODEOWNERS ở nhánh đích yêu cầu review khi PR sẵn sàng, khác phê duyệt bắt buộc

**Time:** `05:02–06:08`

**Visual:**

Chia đôi ảnh: bên trái CODEOWNERS ở base main và mũi tên request khi Draft→Ready; bên phải Settings với điều kiện require code owner review tùy cấu hình. Gạch rõ 'request ≠ required approval'.

**Script:**

Khi PR sẵn sàng, CODEOWNERS từ nhánh đích có thể giúp gửi yêu cầu đến người phụ trách đường dẫn bị đổi. Đây là cơ chế mời, không phải khóa merge mặc định. Nếu branch protection hoặc ruleset yêu cầu phê duyệt của code owner, GitHub mới xét điều kiện đó trước khi tiếp nhận. Ta cần kiểm tra CODEOWNERS trong base, pattern, quyền reviewer và quy tắc đang có hiệu lực. Không được lấy file CODEOWNERS trên head làm bằng chứng cho quy tắc sở hữu của base hoặc nhầm một yêu cầu review với một approval đã được gửi.

**Purpose:**

Chỉ rõ nguồn CODEOWNERS base branch, Draft→Ready và sự độc lập của review request versus required code owner approval.
