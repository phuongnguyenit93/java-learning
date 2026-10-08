---
video:
  url: ""
---

# Vận dụng quy trình Azure Repos từ đầu đến cuối

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

## Sơ đồ organization, project, repository và nhóm chịu trách nhiệm

<!-- VIDEO_SECTION -->

### Scene 1 — Sơ đồ organization, project, repository và nhóm chịu trách nhiệm

**Time:** `00:00–00:56`

**Visual:**

Vẽ tổ chức giả định RetailCo → Checkout → checkout-api; gắn Alice contributor, Bob/Carla reviewer và nhóm Admin giới hạn quyền.

Ghi nhãn RetailCo/Bug 104 là giả định; dùng tài khoản Azure DevOps Services sandbox được phép hoặc tài liệu Microsoft có dẫn nguồn, không thao tác dữ liệu production.

**Script:**

Chúng ta quay về bối cảnh RetailCo/Checkout/checkout-api như một case giả định thống nhất. Project Administrators quản lý settings cấp project, nhóm quản trị repo giới hạn quyền nhạy cảm, Contributors đề xuất thay đổi và Reviewers kiểm tra rủi ro. Trên màn hình, gắn trách nhiệm mỗi người với scope cụ thể; không suy từ chức danh chung rằng ai cũng có bypass. Ghi lại môi trường Azure DevOps Services để không nhầm các khác biệt phiên bản Server.

**Purpose:**

Dựng sơ đồ trách nhiệm và phạm vi người tham gia.

## Lựa chọn nhánh chung hoặc fork theo quyền đóng góp

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:56–01:07`

**Visual:**

Giữ hệ RetailCo → Checkout → checkout-api, tách nhánh làm việc chung và fork.

**Script:**

Đã có sơ đồ người và repo; Alice có Write hay cần đóng góp qua fork?

**Purpose:**

Nối nhân sự cùng phạm vi project với quyền đóng góp thực tế.

### Scene 1 — Lựa chọn nhánh chung hoặc fork theo quyền đóng góp

**Time:** `01:07–02:01`

**Visual:**

Vẽ luồng Alice đi nhánh feature trong checkout-api hoặc fork được phép, hai đường đều về target main.

Ghi nhãn RetailCo/Bug 104 là giả định; dùng tài khoản Azure DevOps Services sandbox được phép hoặc tài liệu Microsoft có dẫn nguồn, không thao tác dữ liệu production.

**Script:**

Alice thuộc Contributors và có quyền tạo/push nhánh feature nên nhóm chọn shared checkout-api repo. Nếu cộng tác viên bên ngoài cần phạm vi khác, fork có thể là lựa chọn theo policy tổ chức; quyền/policies từ repo gốc không tự chép qua fork. Dù chọn đường nào, PR vẫn nhắm đúng upstream main và chịu quy tắc nhánh đích. Ghi vào bảng bằng chứng ai kiểm soát source và ai được quyền complete target.

**Purpose:**

Chọn nhánh/fork theo quyền hiệu lực và policy đích.

## Chuyển đổi Draft PR sang trạng thái sẵn sàng review

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:01–02:11`

**Visual:**

Theo nhánh Alice đã chọn đến Draft PR rồi thao tác Ready for review rõ ràng.

**Script:**

Chọn xong nhánh nguồn, ta tạo Draft và chủ động chuyển sang Ready.

**Purpose:**

Cho thấy đường đóng góp đi tới đề xuất có thể mời người đánh giá.

### Scene 1 — Chuyển đổi Draft PR sang trạng thái sẵn sàng review

**Time:** `02:11–03:08`

**Visual:**

Hiển thị PR timeout Draft từ feature/timeout vào main, Bug 104 và kế hoạch test; chuyển badge sang Ready for review.

Ghi nhãn RetailCo/Bug 104 là giả định; dùng tài khoản Azure DevOps Services sandbox được phép hoặc tài liệu Microsoft có dẫn nguồn, không thao tác dữ liệu production.

**Script:**

Alice phát hiện timeout xử lý sai trong trường hợp biên và mở Draft PR gắn Bug 104 để xin phản hồi sớm. Mô tả nêu dữ liệu đầu vào, thay đổi dự kiến và cách kiểm chứng. Khi đã cập nhật commit và chạy test cần thiết, cô mới đánh dấu Ready for review. Việc đổi trạng thái không tự tạo phiếu Approve hay biến policies thành Passed. Hãy theo dõi PR ID xuyên suốt video để không đổi ví dụ.

**Purpose:**

Minh họa Draft→Ready và bối cảnh Bug 104.

## Theo dõi phiếu đánh giá và điều kiện branch policy

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:08–03:18`

**Visual:**

Giữ PR Ready cạnh Bob Approve, Carla Wait for author và các nhãn policy.

**Script:**

Bob Approve nhưng Carla Wait for author; policy required reviewer sẽ xét thế nào?

**Purpose:**

Nêu xung đột giữa phiếu tích cực hiển thị với yêu cầu reviewer bắt buộc.

### Scene 1 — Theo dõi phiếu đánh giá và điều kiện branch policy

**Time:** `03:18–04:11`

**Visual:**

Đặt Bob Approve cạnh Carla Wait for author, comment Active và các policy yêu cầu hai reviewer plus build validation.

Ghi nhãn RetailCo/Bug 104 là giả định; dùng tài khoản Azure DevOps Services sandbox được phép hoặc tài liệu Microsoft có dẫn nguồn, không thao tác dữ liệu production.

**Script:**

Bob đọc diff và Approve, nhưng required reviewer Carla chọn Wait for author vì chưa có test cho timeout âm. Main yêu cầu hai phiếu hợp lệ, giải quyết comment và build validation. Do đó PR vẫn bị chặn dù có một Approve. Alice bổ sung test, push source update và mời Carla xem lại; policy có thể reset phiếu theo cấu hình, nên video bắt buộc đọc trạng thái reviewer hiện tại.

**Purpose:**

Chứng minh một Approve chưa đủ khi required reviewer Wait.

## Hoàn tất PR và bằng chứng liên kết work item

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:11–04:22`

**Visual:**

Đổi góp ý của Carla thành diff mới, thread resolved, check đạt và tùy chọn Complete.

**Script:**

Sau sửa mã và kiểm tra đạt, bằng chứng nào cho phép Complete đúng quyền?

**Purpose:**

Hiện các bằng chứng cần đổi trước khi đề xuất thực sự được tích hợp.

### Scene 1 — Hoàn tất PR và bằng chứng liên kết work item

**Time:** `04:22–05:19`

**Visual:**

Cho xem bộ bằng chứng sau cập nhật: Carla Approve, thread Resolved, build Passed, Completed và commit target cùng Bug 104.

Ghi nhãn RetailCo/Bug 104 là giả định; dùng tài khoản Azure DevOps Services sandbox được phép hoặc tài liệu Microsoft có dẫn nguồn, không thao tác dữ liệu production.

**Script:**

Sau khi Carla Approve, thread được giải quyết và required build đạt, actor có quyền chọn Complete hoặc auto-complete chờ điều kiện còn thiếu. PR chuyển Completed, target main có dấu vết tích hợp theo kiểu merge đã chọn, còn Bug 104 giữ liên kết giải thích lý do sửa. Work item có thể thay đổi trạng thái nếu cấu hình cho phép, nhưng không suy ra tự động đóng bug. Hãy đọc ba thứ riêng: PR, Git history và work item.

**Purpose:**

Kết nối completion record, merge và work-item trace.

## Tình huống PR bị chặn hoàn tất và cách xác định nguyên nhân

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:19–05:29`

**Visual:**

Giữ PR giả định Completed, phủ các biến thể lỗi Failed, Pending và Denied.

**Script:**

Ví dụ thành công rồi; những lỗi nào có thể chặn luồng tương tự?

**Purpose:**

Đối chiếu luồng thành công với các kiểu chặn cần chẩn đoán riêng.

### Scene 1 — Tình huống PR bị chặn hoàn tất và cách xác định nguyên nhân

**Time:** `05:29–06:26`

**Visual:**

Vẽ năm nhánh lỗi: required reviewer Wait, build Failed, conflict, permission denied, thiếu work item; gắn người xử lý.

Ghi nhãn RetailCo/Bug 104 là giả định; dùng tài khoản Azure DevOps Services sandbox được phép hoặc tài liệu Microsoft có dẫn nguồn, không thao tác dữ liệu production.

**Script:**

Đặt cùng một PR ở các biến thể có blocker khác nhau. Carla còn Wait thì trả review cho Carla và tác giả; Build validation Failed thì chuyển nhóm CI/CD phân tích; conflict thì quay về Git merge; thiếu quyền Complete thì admin kiểm tra effective permission; thiếu linked work item thì bổ sung context thật. Không giải quyết tất cả bằng thêm Approve hoặc cấp quyền bypass. Mỗi đường chẩn đoán phải chỉ ra bằng chứng và người chịu trách nhiệm.

**Purpose:**

Phân công xử lý theo blocker, không bypass tùy tiện.

## Bằng chứng cuối quy trình: quyền, reviewer, check và trạng thái PR

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:26–06:37`

**Visual:**

Xếp các blocker vào bảng bằng chứng actor, quyền, votes, checks và trạng thái PR.

**Script:**

Trước khi báo hoàn tất, gom source, votes, policy và quyền actor vào bảng kiểm.

**Purpose:**

Biến tình huống xuyên suốt thành hồ sơ cộng tác có thể audit.

### Scene 1 — Bằng chứng cuối quy trình: quyền, reviewer, check và trạng thái PR

**Time:** `06:37–07:31`

**Visual:**

Lập bảng kết quả gồm org/project/repo, entitlement/effective rights, PR source-target/status, votes/threads, checks, Completed time.

Ghi nhãn RetailCo/Bug 104 là giả định; dùng tài khoản Azure DevOps Services sandbox được phép hoặc tài liệu Microsoft có dẫn nguồn, không thao tác dữ liệu production.

**Script:**

Tạo bảng kết quả cuối: đúng organization/project/repository, access level và quyền hiệu lực, source/target và PR ID, Draft/Active đã chuyển thế nào, reviewers/votes còn hiệu lực, checks nào đạt, ai có quyền Complete và thời điểm Completed. Mỗi hàng phải có một trang hoặc bản ghi thực để đối chiếu, không chỉ nhãn tự điền. Như vậy nhóm truy vết được quyết định cộng tác mà không nhầm nó với lịch sử commit thuần túy.

**Purpose:**

Tổng hợp quyền, phiếu, policy và bản ghi Complete.

## Bàn giao sang Git mechanics, branching strategy, Boards và Pipelines

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:31–07:41`

**Visual:**

Thu bảng về bốn vùng Git, Branching Strategy, Azure Boards và Pipelines.

**Script:**

Kết thúc bằng ranh giới của Git, chiến lược nhánh, Boards và Pipelines.

**Purpose:**

Chỉ rõ chủ đề học tiếp và tránh nhầm PR đã hoàn tất với triển khai.

### Scene 1 — Bàn giao sang Git mechanics, branching strategy, Boards và Pipelines

**Time:** `07:41–08:37`

**Visual:**

Vẽ sơ đồ năm chủ thể: Git CLI/graph, Branching Strategy, Monorepo/Polyrepo, Azure Boards, Azure Pipelines; Azure Repos PR nằm trung tâm.

Ghi nhãn RetailCo/Bug 104 là giả định; dùng tài khoản Azure DevOps Services sandbox được phép hoặc tài liệu Microsoft có dẫn nguồn, không thao tác dữ liệu production.

**Script:**

Kết lại bằng sơ đồ trách nhiệm. Git quản lý commit graph, merge và fetch/push; Branching Strategy chọn tuổi nhánh và mô hình release; Monorepo/Polyrepo quyết định cách chia repository; Azure Boards sở hữu work-item workflow; Azure Pipelines sở hữu build/job. Azure Repos trung tâm giữ repo, quyền, PR review và policy tiêu thụ kết quả check. Điểm học cuối không phải nhớ mọi nút Azure DevOps mà biết quan sát đúng bằng chứng và bàn giao đúng người.

**Purpose:**

Kết thúc bằng bàn giao trách nhiệm không trùng nội dung module khác.
