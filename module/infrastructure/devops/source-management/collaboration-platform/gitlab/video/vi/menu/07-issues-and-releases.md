---
video:
  url: ""
---

# Theo dõi Issues và công bố Releases

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

## GitLab Issues: mô tả, phân công và theo dõi công việc nhóm

<!-- VIDEO_SECTION -->

### Scene 1 — GitLab Issues: mô tả, phân công và theo dõi công việc nhóm

**Time:** `00:00–01:11`

**Visual:**

Mở thẻ Issue #42 minh họa tiêu đề 'Âm tiền phải bị từ chối', input -1, expected error và observed behavior. Đặt commit và deployment ở hai khung ngoài Issue.

**Script:**

Một Issue là nơi nhóm ghi vấn đề trước khi chạm mã nguồn. Trong ví dụ, khách hàng nhập số tiền âm và hệ thống trả kết quả không phù hợp, nên An viết rõ dữ liệu tái hiện và hành vi mong đợi. Người khác có thể thảo luận xem đây là bug hay yêu cầu mới. Nhưng Issue Open hay Closed không phải là commit, cũng không cho biết đã deploy. Khi nhìn hồ sơ #42, hãy hỏi người đọc có đủ thông tin để hiểu lỗi và đối chiếu MR !57 sau này hay chưa.

**Purpose:**

Bắt đầu traceability từ yêu cầu có thể tái hiện thay vì giả định công việc đã được thực hiện.

## Assignee, labels và milestones để tổ chức Issues

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:11–01:24`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "Issue mô tả nhu cầu, chưa phải bản sửa". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Khi Issue rõ ràng, làm sao người quản lý phân công và gom kế hoạch mà không nhầm với hoàn thành?

**Purpose:**

Chuyển từ Issue mô tả nhu cầu, chưa phải bản sửa sang câu hỏi metadata lập kế hoạch không chứng minh đã sửa theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Assignee, labels và milestones để tổ chức Issues

**Time:** `01:24–02:38`

**Visual:**

Gắn vào Issue #42 ba thẻ Assignee An, Label bug, Milestone v1.4.0. Dùng bảng riêng cho trạng thái Merge và Release với dấu hỏi, không vẽ tự động hoàn thành.

**Script:**

Assignee chỉ ai đang theo dõi việc xử lý, label giúp nhóm lọc như bug hay priority, milestone gom công việc hướng về một mốc. Không thẻ nào tự nói PR đã vào main. Giả sử chúng ta gắn Issue #42 cho An và milestone v1.4.0, nhóm sẽ dễ tìm nó trong kế hoạch, nhưng vẫn phải xem MR và kết quả merge. Một label mang tên ready chỉ là quy ước quản lý chứ không cấp quyền merge. Nếu người chịu trách nhiệm thay đổi, hãy cập nhật assignee cùng bối cảnh thay vì coi thẻ cũ là bằng chứng đã xong.

**Purpose:**

Phân biệt ba loại metadata công việc với các sự kiện kỹ thuật về chấp nhận và phát hành.

## Liên kết Issue với merge request và điều kiện tự đóng trên nhánh mặc định

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:38–02:51`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "metadata lập kế hoạch không chứng minh đã sửa". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Một Issue có thể chỉ sang MR, nhưng đường link không đồng nghĩa GitLab đã hoàn tất công việc.

**Purpose:**

Chuyển từ metadata lập kế hoạch không chứng minh đã sửa sang câu hỏi MR liên kết Issue khác sự kiện auto-close theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Liên kết Issue với merge request và điều kiện tự đóng trên nhánh mặc định

**Time:** `02:51–04:03`

**Visual:**

Nối Issue #42 với MR !57, phóng lớn cụm `Closes #42` trong description. Đặt đường source→default main và source→release branch cạnh nhau, nhánh release không tự gắn kết quả Closed.

**Script:**

Liên kết Issue với MR giúp người khác biết bản sửa nào đang xử lý nhu cầu nào. GitLab có thể nhận từ khóa như Closes trong mô tả MR, nhưng tự đóng Issue thường phụ thuộc việc thay đổi được merge vào default branch theo quy tắc áp dụng. Nếu MR nhắm nhánh release khác, ta không nên hứa kết quả tương tự. Trong storyboard, đường liên kết xuất hiện ngay, còn trạng thái đóng để trống cho đến khi kiểm tra timeline Issue thật. MR bị đóng mà không merge càng không phải bằng chứng lỗi đã sửa.

**Purpose:**

Làm rõ traceability so với default-branch auto-closure có điều kiện.

## Git tag và GitLab Release: hai đối tượng khác nhau

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:03–04:16`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "MR liên kết Issue khác sự kiện auto-close". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Issue và MR nói vì sao và mã nào được nhận; giờ cần phân biệt dấu mốc phiên bản với thông báo phát hành.

**Purpose:**

Chuyển từ MR liên kết Issue khác sự kiện auto-close sang câu hỏi Git tag khác GitLab Release và deployment theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Git tag và GitLab Release: hai đối tượng khác nhau

**Time:** `04:16–05:30`

**Visual:**

Đặt thẻ `v1.4.0` tag trỏ tới một commit, bên phải là trang GitLab Release với title, notes và assets. Để production thành cột thứ ba độc lập.

**Script:**

Sau khi sửa đổi được nhận, nhóm có thể đặt Git tag để chỉ một điểm trong lịch sử. GitLab Release là hồ sơ công bố gắn với tag đó, có tiêu đề, ghi chú, ngày và liên kết tài nguyên. Tạo tag không bắt buộc có Release ngay, còn có Release cũng không tự chứng minh production đang chạy phiên bản ấy. Trong ví dụ chúng ta lập kế hoạch v1.4.0 cho lỗi âm tiền; trước khi báo đã phát hành, người quản lý phải nhìn tag, commit tương ứng và trang Release thật. Việc chạy lệnh tag thuộc phần Git riêng.

**Purpose:**

Tách ba bằng chứng lịch sử, công bố và triển khai, không biến Release thành kết quả CI giả.

## Ghi chú, tài nguyên Release và ranh giới với quản lý phiên bản/triển khai

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:30–05:43`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "Git tag khác GitLab Release và deployment". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Release tồn tại để truyền đạt kết quả tới người dùng; nội dung công bố phải được đối chiếu với thay đổi thật.

**Purpose:**

Chuyển từ Git tag khác GitLab Release và deployment sang câu hỏi ghi chú và assets công bố thông tin có kiểm chứng theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Ghi chú, tài nguyên Release và ranh giới với quản lý phiên bản/triển khai

**Time:** `05:43–07:00`

**Visual:**

Dựng Release v1.4.0 mẫu với mục Fixed negative amounts, Compatibility, Documentation và danh sách assets; bên cạnh bảng test/build/deploy đặt ngoài phạm vi GitLab Release.

**Script:**

Người dùng không chỉ cần số v1.4.0 mà cần biết lỗi nào được sửa và điều gì thay đổi tương thích. Release notes nên liên hệ đúng Issue #42, MR !57 và commit đã được nhận, sau khi xác minh những hồ sơ đó thật. Assets có thể là tệp hoặc liên kết đến bản phân phối; chúng không tự thay thế quy trình build, ký hoặc triển khai. Trong video, bản ghi phát hành chỉ là một mẫu kế hoạch, không phải file đã tải lên. Nếu chưa có Release thật, hãy nói rõ mới có thay đổi nguồn hoặc tag chứ không báo sản phẩm đã phát hành.

**Purpose:**

Kết thúc bằng quy tắc truyền thông phát hành có căn cứ và ranh giới CI/CD.
