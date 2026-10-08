---
video:
  url: ""
---

# Branch policies, điều kiện merge và quyền bypass

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

## Mục đích bảo vệ các nhánh đích quan trọng của PR

<!-- VIDEO_SECTION -->

### Scene 1 — Mục đích bảo vệ các nhánh đích quan trọng của PR

**Time:** `00:00–00:56`

**Visual:**

Mở Branches → main → Branch policies; đánh dấu main là nhánh nền chung và feature là nhánh đề xuất thay đổi.

Màn hình từ sandbox được phép hoặc Microsoft Learn có nhãn; chỉ đọc policy, tuyệt đối không bypass thật hoặc cấu hình pipeline.

**Script:**

Tại sao main cần bảo vệ? Đây thường là nhánh ổn định được nhiều người lấy làm nền; một thay đổi lỗi có thể lan rộng khi tích hợp. Mở Branch policies của main để xem những điều kiện kiểm soát PR trước merge. Đừng biến quy tắc của Azure Repos thành lệnh Git: Git quyết định graph và xung đột, còn nền tảng thực thi review/check theo nhánh đích. Hãy luôn xác nhận nhánh nào đang được bảo vệ.

**Purpose:**

Giải thích mục đích bảo vệ main bằng policy.

## Branch permissions và branch policies: phạm vi khác biệt

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:56–01:08`

**Visual:**

Dịch thẻ Branch policies của main sang một bên, hiện bảng quyền Branch security.

**Script:**

Bảo vệ main đặt ra hai câu hỏi: ai được thao tác, và PR phải đạt điều kiện nào?

**Purpose:**

Tách quyền của người thao tác khỏi điều kiện review/check của nhánh đích.

### Scene 1 — Branch permissions và branch policies: phạm vi khác biệt

**Time:** `01:08–02:00`

**Visual:**

Chia màn hình hai bảng: Branch security có Contribute/bypass và Branch policies có reviewer/validation requirements.

Màn hình từ sandbox được phép hoặc Microsoft Learn có nhãn; chỉ đọc policy, tuyệt đối không bypass thật hoặc cấu hình pipeline.

**Script:**

Ở bảng Security, câu hỏi là ai được Contribute, Force push, Edit policies hoặc bypass. Ở bảng Branch policies, câu hỏi là PR phải đạt minimum reviewers hoặc validation nào trước Complete. Một người có quyền push feature không đồng nghĩa được cập nhật main hay tắt policy. Hai bảng nằm cạnh nhau làm rõ vì sao sửa quyền cho đúng người không thể thay thế kết quả kiểm tra đang fail.

**Purpose:**

Phân biệt quyền thao tác với điều kiện merge.

## Chính sách merge bắt buộc và kiểm tra tùy chọn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:00–02:11`

**Visual:**

Giữ hai bảng Settings, tô nhãn Blocking/Required và Optional cạnh kết quả policy.

**Script:**

Trong danh sách policy, kiểm tra nào chặn merge, kiểm tra nào chỉ tham khảo?

**Purpose:**

Làm nổi sự khác nhau giữa kiểm tra ngăn merge và tín hiệu tham khảo.

### Scene 1 — Chính sách merge bắt buộc và kiểm tra tùy chọn

**Time:** `02:11–03:02`

**Visual:**

Trong Branch policies, tô hai mức Blocking/Required và Optional; đối chiếu thẻ kết quả trong PR Policies.

Màn hình từ sandbox được phép hoặc Microsoft Learn có nhãn; chỉ đọc policy, tuyệt đối không bypass thật hoặc cấu hình pipeline.

**Script:**

Một check Enabled chưa chắc là Blocking. Trên chính sách nhánh đích, tìm nhãn bắt buộc hoặc tùy chọn và đọc PR Policies để xem cái nào đang chặn Complete. Optional status cho tín hiệu hữu ích nhưng không nhất thiết khóa merge. Nếu thẻ trạng thái đỏ, phải kiểm tra chính sách và điều kiện đang áp dụng, không lấy màu thẻ làm định nghĩa chung cho mọi repository.

**Purpose:**

Phân biệt check bắt buộc với thông báo tùy chọn.

## Số lượt phê duyệt tối thiểu và giới hạn tự phê duyệt

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:02–03:14`

**Visual:**

Phóng từ nhãn blocking vào Minimum reviewers với các công tắc tự duyệt và reset votes.

**Script:**

Ngay cả số phiếu yêu cầu cũng phụ thuộc người bỏ phiếu hợp lệ và tùy chọn reset.

**Purpose:**

Nêu điều kiện hợp lệ của số lượt phê duyệt theo cấu hình.

### Scene 1 — Số lượt phê duyệt tối thiểu và giới hạn tự phê duyệt

**Time:** `03:14–04:09`

**Visual:**

Phóng to Minimum reviewers cùng các toggle self-approval, most recent pusher và vote reset; đối chiếu số phiếu được tính.

Màn hình từ sandbox được phép hoặc Microsoft Learn có nhãn; chỉ đọc policy, tuyệt đối không bypass thật hoặc cấu hình pipeline.

**Script:**

Một policy yêu cầu hai người duyệt không có nghĩa hai tên trong danh sách là đủ. Kiểm tra tùy chọn cho phép tác giả tự Approve, giới hạn người push mới nhất và cách reset votes sau source update. Một phiếu Approve vẫn có thể không được tính nếu người bỏ phiếu không hợp lệ theo cấu hình. Hãy đọc kết quả current reviewer count thay vì tự tính từ avatar; đừng thay policy thật trong video.

**Purpose:**

Giải thích số lượt duyệt hợp lệ theo cấu hình.

## Required reviewers và yêu cầu giải quyết comment

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:09–04:20`

**Visual:**

Giữ khung đếm phiếu, mở required reviewer theo đường dẫn và một thread đang Active.

**Script:**

Đủ số lượng chưa chắc đủ nếu reviewer bắt buộc hay thread còn chưa xử lý.

**Purpose:**

Nối số lượt tối thiểu với người bắt buộc và yêu cầu đóng vòng thảo luận.

### Scene 1 — Required reviewers và yêu cầu giải quyết comment

**Time:** `04:20–05:15`

**Visual:**

Hiển thị Required reviewers rule có path filter và chính sách Check for comment resolution với thread Active của PR.

Màn hình từ sandbox được phép hoặc Microsoft Learn có nhãn; chỉ đọc policy, tuyệt đối không bypass thật hoặc cấu hình pipeline.

**Script:**

Một đường dẫn nhạy cảm có thể được gắn required reviewers bằng policy; không thể đơn giản bỏ họ rồi thêm người review tùy chọn cho đủ số. Nếu bật Check for comment resolution dạng blocking, thread Active còn mở cũng cản Complete. Hãy đặt trang policy cạnh PR discussion và xem trạng thái thật: giải quyết thread không phải bằng chứng test pass, và thêm đủ người duyệt không thay thế trách nhiệm đóng vòng phản hồi.

**Purpose:**

Liên hệ required reviewers, threads và blocking policy.

## Build validation, status checks và điểm bàn giao CI/CD

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:15–05:26`

**Visual:**

Giữ thẻ thread chưa xử lý cạnh hai thẻ Failed build validation và Pending status check.

**Script:**

Giải quyết bình luận vẫn chưa thay thế kết quả build hoặc status check bắt buộc.

**Purpose:**

Chứng minh xử lý xong comment không thay thế được kiểm tra kỹ thuật.

### Scene 1 — Build validation, status checks và điểm bàn giao CI/CD

**Time:** `05:26–06:22`

**Visual:**

Trong PR Policies đánh dấu Build validation Failed và Status check Pending; gắn nhãn bàn giao Azure Pipelines.

Màn hình từ sandbox được phép hoặc Microsoft Learn có nhãn; chỉ đọc policy, tuyệt đối không bypass thật hoặc cấu hình pipeline.

**Script:**

Build validation yêu cầu trạng thái build/test đạt theo policy, còn Status checks có thể đến từ dịch vụ bên ngoài. Khi Failed hoặc Pending, phải đọc tên check và thông điệp được báo trên PR. Video chỉ chỉ ra tín hiệu và lý do nó chặn hợp nhất; không dạy thiết lập YAML pipeline hay task CI/CD ở chương Repos. Nếu nguồn lỗi nằm ở pipeline, bàn giao đúng module Azure Pipelines với PR ID và check name.

**Purpose:**

Đọc kết quả checks và bàn giao CI/CD đúng phạm vi.

## Kiểu hợp nhất được phép khi hoàn tất PR

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:22–06:32`

**Visual:**

Thu các thẻ check, mở Basic, Squash, Rebase cạnh Limit merge types.

**Script:**

Khi checks đạt, nhánh đích còn có thể giới hạn cách merge được phép.

**Purpose:**

Chuyển từ đạt điều kiện hoàn tất sang lựa chọn dạng lịch sử Git được phép.

### Scene 1 — Kiểu hợp nhất được phép khi hoàn tất PR

**Time:** `06:32–07:30`

**Visual:**

Mở menu merge strategies gồm basic, squash, rebase variants và phần Limit merge types tại target policy.

Màn hình từ sandbox được phép hoặc Microsoft Learn có nhãn; chỉ đọc policy, tuyệt đối không bypass thật hoặc cấu hình pipeline.

**Script:**

Policy có thể chỉ cho phép một số kiểu tích hợp: basic merge tạo merge commit khi phù hợp, squash gộp sửa đổi thành commit mục tiêu, còn rebase variants phát lại commit và có thể thêm merge commit tùy lựa chọn. Chúng không có cùng lịch sử Git sau hoàn tất. Hãy vẽ ba commit graphs đơn giản trước khi nhìn menu Complete, rồi xác minh danh sách được phép ở nhánh main thay vì khẳng định mọi nút luôn xuất hiện.

**Purpose:**

Kết nối kiểu merge được phép với commit graph.

## Quyền bypass policies khi complete PR và khi push: hai cơ chế riêng

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:30–07:40`

**Visual:**

Đặt kiểu merge được phép cạnh hai quyền bypass riêng: khi Complete PR và khi Push.

**Script:**

Chọn kiểu merge không đồng nghĩa được bypass khi Complete PR hoặc khi push.

**Purpose:**

Ngăn suy diễn một quyền ngoại lệ có thể vượt mọi quy trình cập nhật.

### Scene 1 — Quyền bypass policies khi complete PR và khi push: hai cơ chế riêng

**Time:** `07:40–08:36`

**Visual:**

Khoanh hai quyền bypass độc lập cho Complete PR và khi Pushing, đặt cạnh nút Override cùng sơ đồ direct push.

Màn hình từ sandbox được phép hoặc Microsoft Learn có nhãn; chỉ đọc policy, tuyệt đối không bypass thật hoặc cấu hình pipeline.

**Script:**

Nhìn hai dòng quyền tách biệt: Bypass policies when completing pull requests cho phép người được cấp chọn Override branch policies khi Complete; Bypass policies when pushing áp dụng cho cập nhật trực tiếp vào nhánh theo quyền, khác đường PR. Bypass khi push không phải phiếu reviewer và không làm policy tự đạt. Không giả định cứ là Project Administrator là được bypass mọi nơi. Trình diễn chỉ đọc cấu hình, không thực hiện bypass trong tổ chức thật.

**Purpose:**

Phân biệt hai quyền bypass không thay thế nhau.

## Chẩn đoán policy/check chưa đạt và quyền bypass còn thiếu

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:36–08:48`

**Visual:**

Làm mờ các nút bypass, mở cây chẩn đoán actor, votes, policies và conflicts.

**Script:**

Nếu Complete vẫn bị chặn, trước hết tìm thao tác, người dùng và chính sách hiệu lực.

**Purpose:**

Kết chương bằng chẩn đoán lỗi dựa trên bằng chứng, tránh dùng override.

### Scene 1 — Chẩn đoán policy/check chưa đạt và quyền bypass còn thiếu

**Time:** `08:48–09:41`

**Visual:**

Vẽ cây chẩn đoán từ PR state, target, votes, threads, build/status, conflicts đến quyền của actor.

Màn hình từ sandbox được phép hoặc Microsoft Learn có nhãn; chỉ đọc policy, tuyệt đối không bypass thật hoặc cấu hình pipeline.

**Script:**

Chốt bằng cây chẩn đoán: PR đang Draft hay Active? Nhánh đích có đúng main không? Minimum reviewers và required reviewers đạt chưa? Thread còn mở? Build/status hoặc work-item policy thiếu? Git có conflict? Và người hoàn tất có quyền hiệu lực không? Ghi từng bằng chứng lên bảng trước khi hành động. Một quyền bypass không phải phương án sửa build fail, và một lần Approve thêm không sửa conflict trong mã.

**Purpose:**

Tổng hợp quy trình chẩn đoán lỗi policy và quyền.
