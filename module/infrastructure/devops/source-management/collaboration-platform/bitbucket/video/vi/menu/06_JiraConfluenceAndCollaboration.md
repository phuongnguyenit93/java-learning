---
video:
  url: ""
---

# Liên kết công việc và cộng tác Atlassian

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

## Jira Work Item: Khái niệm và bối cảnh của thay đổi mã nguồn

<!-- VIDEO_SECTION -->

### Scene 1 — Jira Work Item: Khái niệm và bối cảnh của thay đổi mã nguồn

**Time:** `00:00–01:35`

**Visual:**

Mở Jira work item demo `BILL-142 Tax rule update` cạnh PR Bitbucket `invoice-api`; đánh dấu riêng `Why` và `How`, không hiển thị dữ liệu Jira thật.

**Script:**

Một PR cho Bình biết những dòng code An muốn đưa vào nguồn chung, nhưng không đủ để chứng minh vì sao doanh nghiệp cần đổi thuế. Jira work item `BILL-142` mô tả yêu cầu, người chịu trách nhiệm và tình trạng xử lý nghiệp vụ. Hai đối tượng khác nhau: Jira item không phải commit, còn PR không thay thế quy trình theo dõi yêu cầu. Khi review, Bình đọc Jira để hiểu mục tiêu, rồi quay về diff và ví dụ hóa đơn để đánh giá implementation. Từ 'Jira Done' không tự suy ra mã đã merge hay ứng dụng đã triển khai.

**Purpose:**

Dạy ranh giới giữa nguồn yêu cầu kinh doanh và thay đổi source.



## Liên kết nhánh, thay đổi và pull request với Jira

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:35–01:47`

**Visual:**

Giữ Jira BILL-142 nói lý do đổi thuế; vẽ đường link được tạo có điều kiện từ tên branch/PR và yêu cầu integration phải bật.

**Script:**

Jira mô tả lý do thay đổi quy tắc tính thuế. Làm sao nối yêu cầu đó với nhánh, commit và pull request cụ thể?

**Purpose:**

Làm rõ key xuất hiện trong văn bản không tự tạo liên kết Jira nếu kết nối/chính sách không đáp ứng.

### Scene 2 — Liên kết nhánh, thay đổi và pull request với Jira

**Time:** `01:47–03:22`

**Visual:**

PR có tiêu đề `BILL-142 Update invoice tax`, branch `BILL-142-tax`; đồ thị liên kết Jira↔Bitbucket chỉ sáng sau khi cấu hình integration được bật.

**Script:**

Với kết nối Jira–Bitbucket Cloud được người quản trị thiết lập phù hợp, dùng work-item key trong tên branch, thông điệp commit hoặc tiêu đề PR giúp các hệ thống nhận biết công việc liên quan. Bình có thể lần từ Jira `BILL-142` sang PR thuế rồi quay lại mô tả yêu cầu. Nhưng việc gõ một chuỗi `BILL-142` không bảo đảm liên kết đã được tạo nếu integration hoặc quyền chưa đáp ứng. Khi quay demo, xác minh cả hai chiều điều hướng và trạng thái kết nối thay vì dùng một đường gạch giả làm kết quả thực.

**Purpose:**

Minh họa liên kết có điều kiện integration; không tuyên bố tự động hóa thiếu cấu hình.



## Nhắc tên, thông báo và tác vụ để phối hợp phản hồi

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:34`

**Visual:**

Giữ link Jira–PR; phóng vào @mention Bình, notification và task kiểm thử 100.05 trong vùng Activity.

**Script:**

Liên kết tạo bối cảnh; những cuộc trao đổi nhỏ trong PR được tổ chức ra sao?

**Purpose:**

Phân biệt nhận thông báo, thảo luận và việc cần hoàn thành trong cùng một luồng cộng tác.

### Scene 3 — Nhắc tên, thông báo và tác vụ để phối hợp phản hồi

**Time:** `03:34–05:09`

**Visual:**

Hiện dòng comment hỏi làm tròn, @mention Bình, một notification demo, và task giao An; giữ ba đối tượng có icon/trạng thái khác nhau.

**Script:**

Không phải góp ý nào cũng cần họp. Trong PR, comment giữ câu hỏi bên cạnh mã, @mention mời người liên quan xem, notification thông báo có hoạt động và task ghi rõ việc phải hoàn tất. Orchid nhờ Bình xác nhận trường hợp hóa đơn âm, rồi giao An task bổ sung kiểm thử. Tag một người không đồng nghĩa họ đã phê duyệt, còn nhận thông báo không chứng minh người đó đã đọc. Khi quay, cho thấy ai được nhắc, nhiệm vụ nào đang mở và bằng chứng nào giải quyết được nó.

**Purpose:**

Tách mention, notification, task và approval thành những bằng chứng khác nhau.



## Issues và Wiki tích hợp đã ngừng hoạt động từ 20/08/2026

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:09–05:21`

**Visual:**

Từ task PR chuyển sang timeline Atlassian 2026; gạch native Cloud Issues/Wiki tại 20/08 và dẫn nhãn Jira/Confluence thay thế.

**Script:**

Trước đây Bitbucket từng có công cụ Issue/Wiki riêng, vậy hướng dẫn năm 2026 còn dùng được không?

**Purpose:**

Ngăn học viên làm theo thao tác Issues/Wiki đã bị loại khỏi Bitbucket Cloud UI/API.

### Scene 4 — Issues và Wiki tích hợp đã ngừng hoạt động từ 20/08/2026

**Time:** `05:21–06:56`

**Visual:**

Dựng timeline Atlassian chính thức với mốc **20 Aug 2026**, gạch bỏ `Bitbucket Cloud native Issues` và `native Wiki` khỏi UI/API; thay bằng Jira và tài liệu ngoài repo, kèm nhãn `Cloud only`.

**Script:**

Nếu bạn tìm tutorial cũ có nút Issues hoặc Wiki trong từng repository Bitbucket Cloud, hãy kiểm tra ngày xuất bản. Atlassian đã gỡ tính năng Issues và Wiki tích hợp khỏi Bitbucket Cloud UI và API vào ngày hai mươi tháng tám năm hai nghìn không trăm hai mươi sáu. Vì thế không hướng dẫn tạo issue native mới hoặc upload trang Wiki native như thao tác còn hỗ trợ. Đây là thay đổi của Cloud; đừng áp một câu chung cho mọi sản phẩm Bitbucket Server/Data Center. Orchid cần dùng Jira hoặc hệ thống issue khác cho công việc, và chọn nơi giữ tài liệu thay thế.

**Purpose:**

Nêu rõ tính năng đã bị retire theo sản phẩm và ngày; phòng tránh demo UI/API lỗi thời.



## Confluence và nơi lưu tài liệu cộng tác thay thế

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:56–07:08`

**Visual:**

Giữ timeline ngừng Issues/Wiki; mở trang Confluence `Invoice rounding policy` cạnh README trong repo và PR link, nhưng không vẽ commit tự sinh.

**Script:**

Issue tracking cần nơi khác; còn các quyết định và quy tắc dài hạn thì nên lưu ở đâu?

**Purpose:**

Phân biệt tài liệu lâu dài ngoài repo với README được version theo Git và sự kiện thay đổi source.

### Scene 5 — Confluence và nơi lưu tài liệu cộng tác thay thế

**Time:** `07:08–08:43`

**Visual:**

Confluence trang demo `Invoice rounding policy` cạnh README được version trong repo; mũi tên PR BILL-142 dẫn tới cả hai, không gắn hai hệ thống thành một commit.

**Script:**

Confluence phù hợp cho quy tắc nghiệp vụ, quyết định kiến trúc và tri thức dùng chung; README hoặc tài liệu nằm trong Git repository thích hợp với thông tin sát source và cần được version cùng code. Orchard đặt quy tắc làm tròn vào tài liệu nhóm, còn PR `BILL-142` mô tả việc triển khai. Sửa Confluence không tự tạo commit Git, và merge PR không tự xác nhận tài liệu đã cập nhật đúng. Nhóm cần nêu ai chịu trách nhiệm mỗi nơi, cách liên kết và kiểm tra độ nhất quán trước khi đóng công việc.

**Purpose:**

Dạy lựa chọn documentation theo lifecycle và ranh giới liên kết, không suy ra đồng bộ tự động.
