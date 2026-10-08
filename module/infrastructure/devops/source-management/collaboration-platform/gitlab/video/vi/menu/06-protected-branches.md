---
video:
  url: ""
---

# Protected branches và điều kiện cho phép merge

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

## Protected branch: phạm vi bảo vệ nhánh quan trọng

<!-- VIDEO_SECTION -->

### Scene 1 — Protected branch: phạm vi bảo vệ nhánh quan trọng

**Time:** `00:00–01:14`

**Visual:**

Làm sáng nhánh main trong cây gateway rồi phủ lớp Protected. Hai mũi tên 'direct push' và 'MR merge' tách riêng; không tạo thông báo permission denied giả.

**Script:**

Protected branch đặt rào chắn cho nhánh quan trọng như main hoặc release. Nó không khiến code tự đúng, mà kiểm soát ai được đẩy thay đổi trực tiếp và ai được đưa MR vào nhánh đó. Ví dụ An là Developer không đẩy được main có thể là chủ đích của nhóm, để đường tiếp nhận đi qua review. Khi quan sát GitLab, hãy xác nhận pattern khớp và Settings > Repository > Branch rules ở phiên bản hiện hành; một số giao diện trước đó gọi Protected branches. Không suy luận hành động bị từ chối khi chưa thấy quyền hiệu lực.

**Purpose:**

Giới thiệu bảo vệ nhánh là cơ chế quản trị acceptance, không phải bộ sửa lỗi.

## Quyền Allowed to merge so với Allowed to push and merge

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:14–01:27`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "bảo vệ nhánh giới hạn ai và cách tiếp nhận". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Protected branch có thể cho một người merge MR nhưng không cho họ push trực tiếp; ta tách hai quyền.

**Purpose:**

Chuyển từ bảo vệ nhánh giới hạn ai và cách tiếp nhận sang câu hỏi Allowed to merge khác Allowed to push and merge theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Quyền Allowed to merge so với Allowed to push and merge

**Time:** `01:27–02:42`

**Visual:**

Dựng hai công tắc đặt tên chính xác. Ví dụ main: Allowed to merge = Maintainers, Allowed to push and merge = No one; vẽ MR merge path riêng với direct push bị hạn chế.

**Script:**

Đây là chỗ không nên nhìn vào một công tắc rồi đoán toàn bộ quyền. Allowed to merge kiểm soát ai có thể thực hiện merge MR vào nhánh bảo vệ. Allowed to push and merge liên quan đường cập nhật trực tiếp và quyền merge theo cấu hình của nó. Với ví dụ Maintainer được phép merge nhưng No one được push trực tiếp, nhóm vẫn có đường chấp nhận MR sau khi đủ yêu cầu. Ta chỉ đang minh họa cấu hình, không thay đổi project thật. Hãy đọc cả hai giá trị và diễn giải kết quả trước khi cấp thêm quyền.

**Purpose:**

Làm lộ sự khác nhau giữa merge MR và direct push, tránh hướng dẫn mở quyền quá rộng.

## Branch rules cấp project và bảo vệ group cấp cao: GitLab.com, Self-Managed, Dedicated (Premium/Ultimate, 17.6+)

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:42–02:55`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "Allowed to merge khác Allowed to push and merge". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Một project có thể có rule riêng; tổ chức lớn còn muốn chuẩn hóa nhưng khả năng group-level phụ thuộc môi trường.

**Purpose:**

Nối hai quyền thao tác ở project với quyền Owner của top-level group và hỗ trợ Premium/Ultimate trên cả ba môi trường GitLab.

### Scene 1 — Branch rules cấp project và bảo vệ group cấp cao: GitLab.com, Self-Managed, Dedicated (Premium/Ultimate, 17.6+)

**Time:** `02:55–04:06`

**Visual:**

So sánh giao diện `Project → Settings → Repository → Branch rules` với `Top-level Group → Settings → Repository → Protected branches`. Ghi rõ `Premium/Ultimate · GitLab.com | Self-Managed | Dedicated · GA 17.6`, `Owner của top-level group`, `No subgroup rule`. Vẽ mũi tên kế thừa tới gateway/billing, khóa sửa group rule từ project; đặt REST API `Self-Managed only` trong khung riêng.

**Script:**

Tính năng bảo vệ nhánh ở top-level group dùng được trên GitLab.com, Self-Managed và Dedicated, với Premium hoặc Ultimate; đã GA từ 17.6. Chỉ Owner của top-level group tạo rule, không cấu hình trực tiếp ở subgroup. Rule truyền tới các project; Maintainer project không sửa được chính rule kế thừa từ project settings. Riêng tài liệu REST API cấp group ghi Self-Managed only: đừng lấy giới hạn API đó để phủ định tính năng giao diện trên hai offering còn lại. Chi kiểm tra quyền Owner, tier và giao diện thật trước khi minh họa; đây là storyboard, không phải kết quả live.

**Purpose:**

Dạy đủ ba offering và giới hạn top-level Owner, tách rõ UI feature khỏi phạm vi REST API Self-Managed và tránh giả tạo kết quả thực thi.

## Khi nhiều rules khớp nhánh: quyền rộng nhất và ngoại lệ Code Owner chặt nhất

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:06–04:19`

**Visual:**

Giữ ba offering cùng hai nhãn `Group rule inherited` và `Project rule separate`; đặt chúng cạnh branch `main` rồi tô sáng các pattern có thể cùng khớp.

**Script:**

Có nhiều rule cùng khớp một nhánh, nhưng GitLab không nhất thiết chọn rule có tên chính xác nhất.

**Purpose:**

Chuyển từ rule nhóm kế thừa sang bài toán khi cùng một nhánh khớp nhiều rule nhóm và project, thay vì mặc định group rule luôn thắng.

### Scene 1 — Khi nhiều rules khớp nhánh: quyền rộng nhất và ngoại lệ Code Owner chặt nhất

**Time:** `04:19–05:33`

**Visual:**

Hai thẻ pattern `main` push No one và `m*` Developer allowed; phủ bảng 'most permissive access'. Bên cạnh thẻ Code Owner required với đường 'most restrictive'.

**Script:**

Khi main khớp nhiều protection rule của GitLab, không thể suy rằng tên chính xác luôn thắng wildcard. Với quyền push, merge và force push, cơ chế thường xét thiết lập cho phép rộng nhất trong các rule khớp; nhưng yêu cầu Code Owner approval đi theo điều kiện chặt nhất. Vì vậy một wildcard quá thoáng có thể mở quyền bạn tưởng đã khóa bằng main. Ta so hai pattern trên bản vẽ, rồi hỏi ai thật sự được push và vùng mã nào vẫn cần owner. Đây cũng là lý do không bê tư duy chọn một rule của GitHub sang GitLab.

**Purpose:**

Dạy phép kết hợp bất đối xứng quan trọng cho bảo mật nhánh, với ví dụ thật về wildcard.

## Điều kiện bắt buộc Code Owner phê duyệt trên protected target branch

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:33–05:46`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "access rộng nhất nhưng code owner nghiêm nhất". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Ta vừa thấy rule chồng lấn có thể giữ yêu cầu owner nghiêm hơn; khi nào điều đó tác động tới MR?

**Purpose:**

Chuyển từ access rộng nhất nhưng code owner nghiêm nhất sang câu hỏi CODEOWNERS không tự tạo gate bắt buộc theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Điều kiện bắt buộc Code Owner phê duyệt trên protected target branch

**Time:** `05:46–06:57`

**Visual:**

MR !57 thay `pricing/Calculator.java`; mở CODEOWNERS mapping `/pricing/` và cài đặt Code Owner approval trên protected main. Đặt ô eligibility cạnh reviewer Chi.

**Script:**

Khi protected branch main yêu cầu Code Owner approval trên gói hỗ trợ, sửa đổi trong pricing có thể cần phiếu từ owner hợp lệ của vùng đó. Nhưng chỉ có file CODEOWNERS không tự làm MR bị khóa trên mọi tier. Người xuất hiện trong file vẫn cần membership và quyền cho phép approval được tính. Bình có thể Approve logic chung trong khi quy tắc vẫn chờ Chi thuộc payments ownership group. Trên UI, kiểm tra pattern khớp, cấu hình của nhánh đích và trạng thái approval thực tế trước khi kết luận thiếu đúng người.

**Purpose:**

Làm rõ khi nào Code Owner là điều kiện bắt buộc, gắn path ownership với tier và membership.

## Approval/check trạng thái có thể chặn merge; pipeline chỉ là điểm tích hợp

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:57–07:10`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "CODEOWNERS không tự tạo gate bắt buộc". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Approval của con người có thể chưa đủ; merge widget còn đọc các loại bằng chứng kỹ thuật và discussion.

**Purpose:**

Chuyển từ CODEOWNERS không tự tạo gate bắt buộc sang câu hỏi pipeline status là bằng chứng, không phải bài CI theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Approval/check trạng thái có thể chặn merge; pipeline chỉ là điểm tích hợp

**Time:** `07:10–08:25`

**Visual:**

Trong merge widget đặt các ô Approvals, Discussions, Pipeline checks. Hiện ba trạng thái mô hình Pending/Failed/Success, nhưng không giả vờ có job chạy hay kết quả thực.

**Script:**

GitLab có thể xét phê duyệt, discussion chưa giải quyết và pipeline status theo cấu hình khi cho phép merge. Ở module cộng tác, ta chỉ đọc trạng thái hiện trên MR chứ không dạy viết `.gitlab-ci.yml` hoặc dựng graph job. Một MR có reviewer đồng ý vẫn có thể bị chặn nếu kiểm tra bắt buộc báo thất bại; ngược lại pipeline xanh cũng không chứng minh quy tắc nghiệp vụ đã đúng. Hãy hỏi check đang áp dụng vào commit nào và nguồn thực sự báo gì. Trong storyboard, các màu chỉ là trường hợp có thể xảy ra, không phải log từ runner.

**Purpose:**

Giữ đúng ranh giới CI/CD và cách hiểu status check là một phần bằng chứng merge.

## Các nguyên nhân MR bị chặn: quyền push/merge, approval và checks

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:25–08:38`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "pipeline status là bằng chứng, không phải bài CI". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Nếu tất cả các lớp đều có thể chặn, cần một thứ tự kiểm tra minh bạch khi MR vẫn chưa merge được.

**Purpose:**

Chuyển từ pipeline status là bằng chứng, không phải bài CI sang câu hỏi đọc chính xác blocker trước khi đổi rule theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Các nguyên nhân MR bị chặn: quyền push/merge, approval và checks

**Time:** `08:38–09:48`

**Visual:**

Dựng cây chẩn đoán Ready? → target & actor → membership/merge permission → approvals/Code Owners → discussions/checks → tất cả pattern protected branch. Bên phải để trống ô kết quả thật.

**Script:**

Nếu nút Merge không sáng, ta không tắt protection để đi đường tắt. Trước tiên xác nhận MR !57 đang nhắm đúng main và đã Ready. Sau đó kiểm tra người thao tác có được merge không, số phiếu hợp lệ, Code Owner, discussion chưa giải quyết và check trên commit đang xét. Cuối cùng đọc tất cả pattern branch protection khớp, kể cả wildcard. Các gate Request changes hoặc approval rules còn phụ thuộc plan và offering. Hãy ghi nguyên nhân GitLab thực sự báo trước khi nhờ người có quyền chỉnh cấu hình.

**Purpose:**

Kết thúc chương bằng quy trình điều tra có thể kiểm chứng và tránh sửa policy đoán mò.
