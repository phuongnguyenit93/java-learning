---
video:
  url: ""
---

# Azure Repos và mô hình cộng tác trên Git

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

## Azure DevOps và Azure Repos: khái niệm, phạm vi dịch vụ

<!-- VIDEO_SECTION -->

### Scene 1 — Azure DevOps và Azure Repos: khái niệm, phạm vi dịch vụ

**Time:** `00:00–01:03`

**Visual:**

Mở trang tổ chức Azure DevOps Services, chỉ lần lượt các ô Repos, Boards và Pipelines; phóng to đúng khu vực Repos đang học.

Dùng giao diện Azure DevOps Services sandbox được cho phép; nếu không có dữ liệu sống, ảnh minh họa phải có nhãn nguồn Microsoft Learn, không bịa phản hồi trực tiếp.

**Script:**

Mở trang tổ chức của Azure DevOps Services trên tài khoản demo được phép truy cập. Chúng ta thấy nhiều dịch vụ, nhưng trong hành trình này chỉ đi vào Azure Repos. Azure DevOps là nền tảng cộng tác; Azure Repos lưu Git repositories, phân quyền và vận hành pull request. Git tự tạo commit ở máy cá nhân, còn Azure Repos đặt lịch sử ấy trong không gian cộng tác. Azure DevOps Server triển khai tại chỗ có vòng đời và phiên bản riêng; không gộp mọi chính sách Services vào Server.

Ta sẽ đi từ organization/project/repository sang quyền truy cập, nhánh hoặc fork, review, chính sách và hoàn tất PR; cuối cùng ghép các bước trong ví dụ giả định RetailCo/Checkout/checkout-api. Boards và Pipelines là hai mảng liên quan nhưng có bài học riêng.

**Purpose:**

Phân biệt ba lớp Azure DevOps Services, Azure Repos và Git.

## Vai trò của nền tảng cộng tác bổ sung cho Git

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:03–01:15`

**Visual:**

Giữ ô Repos sáng, mở lịch sử Git cạnh một thẻ đánh giá pull request.

**Script:**

Git lưu thay đổi; vậy cả nhóm sẽ đọc quyết định review và trạng thái phối hợp ở đâu?

**Purpose:**

Nêu các quyết định cộng tác còn thiếu sau khi đã có nơi lưu lịch sử mã.

### Scene 1 — Vai trò của nền tảng cộng tác bổ sung cho Git

**Time:** `01:15–02:15`

**Visual:**

Chia màn hình: bên trái là git log trong terminal, bên phải là danh sách PR và các badge reviewer/trạng thái trên Azure Repos.

Dùng giao diện Azure DevOps Services sandbox được cho phép; nếu không có dữ liệu sống, ảnh minh họa phải có nhãn nguồn Microsoft Learn, không bịa phản hồi trực tiếp.

**Script:**

Đặt log Git bên trái, danh sách Pull Requests của Azure Repos bên phải. Commit ID chứng minh tệp đã thay đổi qua các snapshot, nhưng không trả lời ai được phép merge vào main hay thay đổi nào đã được hai người xem xét. Bên phải, PR ghi tác giả, reviewer, comment và trạng thái policy. Chúng ta dùng một repository demo được cấp quyền, không bịa kết quả PR: nếu chưa có PR thật, hiển thị ảnh tài liệu Microsoft và gắn nhãn minh họa.

**Purpose:**

Chứng minh vì sao bằng chứng review bổ sung lịch sử Git.

## Ranh giới giữa lịch sử Git và quản trị cộng tác Azure Repos

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:15–02:27`

**Visual:**

Đưa graph Git sang trái, hiện Repository Security và PR Policies của Azure Repos bên phải.

**Script:**

Đã thấy lịch sử; ta tách công việc của Git khỏi phần quản trị của Azure Repos.

**Purpose:**

Xác định nơi lịch sử Git không đủ chứng minh việc đánh giá và chấp nhận có kiểm soát.

### Scene 1 — Ranh giới giữa lịch sử Git và quản trị cộng tác Azure Repos

**Time:** `02:27–03:24`

**Visual:**

Trên sơ đồ bên trái đánh dấu git fetch/push và commit graph; bên phải mở các tab Security của repo và Branch policies của PR.

Dùng giao diện Azure DevOps Services sandbox được cho phép; nếu không có dữ liệu sống, ảnh minh họa phải có nhãn nguồn Microsoft Learn, không bịa phản hồi trực tiếp.

**Script:**

Nhìn terminal: fetch, push, merge hay rebase thuộc cơ chế Git và thay đổi commit/ref. Chuyển sang giao diện: ai có quyền Read hoặc Contribute, ai bỏ phiếu, yêu cầu kiểm tra nào chặn Complete là quyết định ở Azure Repos. Không dùng một HTTP endpoint local để giả làm luật Microsoft. Video này sẽ đọc các màn hình quản trị hợp lệ và chính sách được tài liệu chính thức xác nhận, còn thao tác Git sâu nằm ở module Git.

**Purpose:**

Tách cơ chế Git khỏi kiểm soát cộng tác.

## Organization, project, repository và contributor: thuật ngữ nền

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:24–03:36`

**Visual:**

Thu sơ đồ Git/Azure Repos thành breadcrumb organization → project → repository → contributor.

**Script:**

Những trách nhiệm ấy nằm ở đâu? Ta gọi tên organization, project, repository và người tham gia.

**Purpose:**

Gọi đúng tên các phạm vi Azure DevOps sẽ chi phối quyết định cộng tác.

### Scene 1 — Organization, project, repository và contributor: thuật ngữ nền

**Time:** `03:36–04:35`

**Visual:**

Vẽ sơ đồ phân cấp Azure DevOps Services → organization RetailCo → project Checkout → repository checkout-api → contributor Alice.

Dùng giao diện Azure DevOps Services sandbox được cho phép; nếu không có dữ liệu sống, ảnh minh họa phải có nhãn nguồn Microsoft Learn, không bịa phản hồi trực tiếp.

**Script:**

Dựng bản đồ RetailCo → Checkout → checkout-api, đây là ví dụ được đặt tên, không phải tenant người dùng đang kết nối. Organization gom project và người dùng; trong project có một hay nhiều repository; contributor làm việc trong phạm vi quyền. Mỗi cấp có cấu hình và chủ thể quản lý riêng. Khi mở giao diện thực, đọc breadcrumb và tên repo đang chọn trước khi diễn giải một quyền: không thể dùng quyền ở repo A để suy ra quyền ở repo B.

**Purpose:**

Gắn đúng người và đối tượng vào cây tổ chức.

## Pull request: khái niệm, mục đích và nhánh nguồn/đích

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:35–04:47`

**Visual:**

Theo Alice từ thẻ contributor vào màn tạo PR; ghim source feature/timeout và target main.

**Script:**

Đã xác định người tham gia, giờ xem Alice đề xuất đổi main qua một PR ra sao.

**Purpose:**

Nối vai trò người tham gia với đề xuất có hướng rõ ràng, chưa phải kết quả merge.

### Scene 1 — Pull request: khái niệm, mục đích và nhánh nguồn/đích

**Time:** `04:47–05:42`

**Visual:**

Hiển thị biểu mẫu tạo PR từ feature/timeout đến main, tô nhãn source/target và vùng Title/Description.

Dùng giao diện Azure DevOps Services sandbox được cho phép; nếu không có dữ liệu sống, ảnh minh họa phải có nhãn nguồn Microsoft Learn, không bịa phản hồi trực tiếp.

**Script:**

Mở trang tạo PR trên repository demo: nguồn là feature/timeout, đích là main. PR không phải một đối tượng commit mới trong Git; nó là yêu cầu ở Azure Repos để thảo luận khác biệt hai nhánh trước khi tích hợp. Hãy đọc cả repository nguồn và đích, không chỉ nhìn mỗi tên main. Trong trường hợp fork, hai repository có thể khác nhau. PR có thể ở Draft và chưa được coi là sẵn sàng hoàn tất.

**Purpose:**

Phân biệt đề xuất PR với thao tác merge.

## Reviewer, branch policy và mô hình điều kiện hợp nhất PR

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:42–05:54`

**Visual:**

Giữ mũi tên source/target, lần lượt thêm reviewer votes, branch policies và trạng thái Complete.

**Script:**

PR mới là lời đề nghị. Người review và quy tắc nhánh đích quyết định khả năng tiếp nhận.

**Purpose:**

Giới thiệu ba lớp review, chính sách và quyền hoàn tất cùng tác động lên PR.

### Scene 1 — Reviewer, branch policy và mô hình điều kiện hợp nhất PR

**Time:** `05:54–06:51`

**Visual:**

Đặt ba vùng cạnh nhau: phiếu reviewer, kết quả chính sách nhánh main và nút Complete, mỗi vùng có nhãn rõ ràng.

Dùng giao diện Azure DevOps Services sandbox được cho phép; nếu không có dữ liệu sống, ảnh minh họa phải có nhãn nguồn Microsoft Learn, không bịa phản hồi trực tiếp.

**Script:**

Dừng hình ở ba vùng: danh sách reviewer, bảng Policies của nhánh main và nút Complete. Một người Approve là phiếu đánh giá; minimum reviewers hoặc check bắt buộc được tính theo cấu hình nhánh đích; Complete là một hành động khác do người có quyền thực hiện. Hãy cố ý đọc nhãn policy chặn và nhãn tùy chọn thay vì nói cứ đủ hai avatar là merge được. Một reviewer có thể không được tính vì yêu cầu chính sách riêng.

**Purpose:**

Liên hệ reviewer, policy đích và quyền complete.

## Ranh giới Azure Repos với Azure Boards và Azure Pipelines

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:51–07:03`

**Visual:**

Tách liên kết work item và build check của PR sang hai vùng Boards và Pipelines.

**Script:**

PR còn liên quan hồ sơ công việc và kiểm tra; mỗi bằng chứng thuộc dịch vụ nào?

**Purpose:**

Chỉ rõ bằng chứng nào thuộc dịch vụ liên quan ngoài Azure Repos.

### Scene 1 — Ranh giới Azure Repos với Azure Boards và Azure Pipelines

**Time:** `07:03–08:00`

**Visual:**

Minh họa một PR liên kết Bug 104; bên cạnh có tab Azure Boards và Azure Pipelines với đường nét đứt biểu thị ranh giới sở hữu.

Dùng giao diện Azure DevOps Services sandbox được cho phép; nếu không có dữ liệu sống, ảnh minh họa phải có nhãn nguồn Microsoft Learn, không bịa phản hồi trực tiếp.

**Script:**

Một PR có thể gắn Bug 104 để người xem biết tại sao sửa timeout. Work item thuộc Azure Boards, không phải một commit; kết quả build validation có thể được hiển thị trong Policies nhưng việc cấu hình job và pipeline thuộc Azure Pipelines. Ta chỉ đọc status được tiêu thụ bởi Azure Repos, không học viết YAML CI ở đây. Nếu cần điều tra build fail, ghi lại check nào fail rồi bàn giao cho đúng nhóm phụ trách.

**Purpose:**

Chỉ ra ranh giới Boards/Pipelines trong quy trình Repos.

## Dấu vết và trạng thái cộng tác của một thay đổi trên Azure Repos

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:00–08:12`

**Visual:**

Thu ba vùng dịch vụ về bảng kiểm PR gồm tác giả, nhánh, phiếu, policy và trạng thái.

**Script:**

Cuối chương, thu tác giả, nhánh, phiếu đánh giá, kiểm tra và trạng thái vào hồ sơ.

**Purpose:**

Chuyển sơ đồ khái niệm thành thói quen kiểm tra bằng chứng hợp tác cụ thể.

### Scene 1 — Dấu vết và trạng thái cộng tác của một thay đổi trên Azure Repos

**Time:** `08:12–09:10`

**Visual:**

Chụp toàn cảnh PR và tô lần lượt author, source/target, reviewer vote, active thread, policy result và nhãn Active/Completed.

Dùng giao diện Azure DevOps Services sandbox được cho phép; nếu không có dữ liệu sống, ảnh minh họa phải có nhãn nguồn Microsoft Learn, không bịa phản hồi trực tiếp.

**Script:**

Kết chương bằng một bảng kiểm bên cạnh PR: tổ chức và repo nào, tác giả nào, nguồn/đích ở đâu, phiếu reviewer nào còn hiệu lực, thread nào còn mở, policy nào đang chặn, và PR hiện Active hay Completed. Không suy luận status hiện thời chỉ từ một ảnh cũ; refresh sau khi author push commit mới. Đây là chuỗi chứng cứ mà các chương sau sẽ đọc sâu hơn, trước khi nói một thay đổi thực sự đã được tiếp nhận.

**Purpose:**

Lập checklist chứng cứ PR tránh suy luận theo nhãn đơn lẻ.
