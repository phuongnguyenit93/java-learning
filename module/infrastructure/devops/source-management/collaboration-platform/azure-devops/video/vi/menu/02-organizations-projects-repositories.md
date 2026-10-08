---
video:
  url: ""
---

# Cấu trúc tổ chức, project và Git repository

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

## Cấu trúc organization, project và thành viên trong Azure DevOps

<!-- VIDEO_SECTION -->

### Scene 1 — Cấu trúc organization, project và thành viên trong Azure DevOps

**Time:** `00:00–00:55`

**Visual:**

Mở organization selector RetailCo, chọn project Checkout rồi Warehouse; quan sát danh sách thành viên sandbox có phân quyền.

Chỉ dùng sandbox được ủy quyền hoặc tài liệu Microsoft có nhãn nguồn; không thay đổi cấu hình tổ chức đang vận hành.

**Script:**

Ta đi từ organization RetailCo đến hai project Checkout và Warehouse. Các account có thể thuộc tổ chức nhưng quyền với từng project không giống nhau; muốn xem repo phải có entitlement và security permission tương ứng. Khi quay lại organization, lưu ý đây là ranh giới tài khoản và cấu hình lớn hơn một repository. Chỉ quan sát tên project và membership được cấp quyền, không mở trang quản trị người dùng thật nếu không được phép.

**Purpose:**

Định vị quan hệ tổ chức, dự án và thành viên.

## Ranh giới quản trị project so với Git repository

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:06`

**Visual:**

Phóng từ bộ chọn RetailCo xuống Checkout Project Settings, giữ danh sách repository bên dưới.

**Script:**

Organization chứa nhiều project, nhưng quản trị project có giống quản trị một repository không?

**Purpose:**

Cho thấy quản trị project có phạm vi rộng hơn một Git repository.

### Scene 1 — Ranh giới quản trị project so với Git repository

**Time:** `01:06–01:58`

**Visual:**

Đặt Project settings cạnh trang Security của repository checkout-api; tô khác màu quyền project và quyền repo.

Chỉ dùng sandbox được ủy quyền hoặc tài liệu Microsoft có nhãn nguồn; không thay đổi cấu hình tổ chức đang vận hành.

**Script:**

Đặt trang Project settings cạnh trang checkout-api repository. Project tổ chức thành viên và dịch vụ; repository giữ lịch sử Git, thiết lập và quyền riêng, còn branch có thể có quyền cụ thể hơn. Khi Project Administrator quản lý project, không suy ra mọi quyền thao tác Git đều tự động giống nhau cho mọi danh tính. Hãy kiểm tra góc tên repo trước khi chẩn đoán nút bị vô hiệu.

**Purpose:**

Phân biệt quản trị project và quyền cụ thể repo/branch.

## Một project với nhiều Git repository và trường hợp sử dụng

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:58–02:09`

**Visual:**

Mở checkout-api và checkout-web thành hai graph Git độc lập dưới project Checkout.

**Script:**

Một project có thể chứa nhiều repository. Lịch sử Git của chúng có chung nhau không?

**Purpose:**

Tách quyền quản trị chung của project khỏi lịch sử riêng của từng repository.

### Scene 1 — Một project với nhiều Git repository và trường hợp sử dụng

**Time:** `02:09–03:02`

**Visual:**

Trong project Checkout chuyển dropdown giữa checkout-api và checkout-web, đọc hai danh sách branch khác nhau.

Chỉ dùng sandbox được ủy quyền hoặc tài liệu Microsoft có nhãn nguồn; không thay đổi cấu hình tổ chức đang vận hành.

**Script:**

Trong cùng Checkout project, chọn lần lượt checkout-api và checkout-web. Branch list, lịch sử và cấu hình của hai repository khác nhau dù chia sẻ một không gian project. Điều này hữu ích khi các codebase có vòng đời riêng, nhưng liên kết sửa đổi xuyên repo phải ghi rõ nguồn và rủi ro đồng bộ. Chúng ta chưa bàn sâu monorepo/polyrepo; ở đây chỉ xác định đúng danh tính repo trước khi PR.

**Purpose:**

Nhận biết mỗi Git repo có refs và lịch sử độc lập.

## Vai trò quản trị khi tạo, chia sẻ và quản lý repository

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:02–03:13`

**Visual:**

Giữ hai thẻ repository và hiện hai quyền Create repository và Manage permissions.

**Script:**

Giờ ta tách quyền tạo repository với quyền quản lý quyền truy cập của nó.

**Purpose:**

Buộc phân biệt người được tạo repository với người được quản lý quyền truy cập.

### Scene 1 — Vai trò quản trị khi tạo, chia sẻ và quản lý repository

**Time:** `03:13–04:08`

**Visual:**

So sánh quyền Create repository ở Project settings với Manage permissions và Edit policies của một Git repository.

Chỉ dùng sandbox được ủy quyền hoặc tài liệu Microsoft có nhãn nguồn; không thay đổi cấu hình tổ chức đang vận hành.

**Script:**

Một kỹ sư được thêm Contributors thường có thể làm việc trên nhánh phù hợp nhưng không mặc nhiên được tạo repository mới hay chỉnh policy. Hãy đọc hai quyền Create repository ở phạm vi project và Manage permissions hoặc Edit policies ở phạm vi repo/branch. Project Administrators thường có quyền quản trị nhưng vẫn cần kiểm tra quyền hiệu lực ở đúng đối tượng. Đừng thử thay đổi quyền thật chỉ để dựng một cảnh quay.

**Purpose:**

Phân biệt quyền tạo repo, quản trị policy và đóng góp code.

## Phạm vi hiển thị project và quyền truy cập repository

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:08–04:19`

**Visual:**

Dịch ma trận quyền sang Project visibility và quyền Read ở repository cụ thể, đặt dấu hỏi giữa hai vùng.

**Script:**

Quyền quản trị và visibility vẫn chưa quyết định người xem được đọc mã gì.

**Purpose:**

Tách đối tượng thấy project khỏi quyền đọc mã hiệu lực trong một repo.

### Scene 1 — Phạm vi hiển thị project và quyền truy cập repository

**Time:** `04:19–05:14`

**Visual:**

Chụp nhãn visibility của project bên trái và effective Read của repo private bên phải; dùng bảng hai cột.

Chỉ dùng sandbox được ủy quyền hoặc tài liệu Microsoft có nhãn nguồn; không thay đổi cấu hình tổ chức đang vận hành.

**Script:**

Nếu một project đang private, người ngoài phải có quyền truy cập phù hợp mới xem được mã nguồn. Nhưng dù một user nằm trong project, quyền Read/Contribute ở repository cụ thể vẫn quyết định thao tác. Hãy vẽ bảng hai cột: project visibility trả lời ai có thể vào phạm vi dự án; repo security trả lời làm được gì với mã nguồn. Không giả định một nhãn public tương đương tất cả repo đều cho push.

**Purpose:**

Phân biệt nhãn hiển thị project và quyền thao tác mã nguồn.

## Azure DevOps Services: ngừng tạo public project từ 2026, chuyển private năm 2027

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:14–05:25`

**Visual:**

Giữ nhãn visibility, mở thông báo Microsoft về public projects giai đoạn 2026–2027.

**Script:**

Phạm vi public project của Azure DevOps Services thay đổi trong năm 2026 và 2027.

**Purpose:**

Gắn quyết định hiển thị với lộ trình Azure DevOps Services theo thời điểm.

### Scene 1 — Azure DevOps Services: ngừng tạo public project từ 2026, chuyển private năm 2027

**Time:** `05:25–06:22`

**Visual:**

Mở bài Public projects retirement trên Microsoft Learn, tô năm 2026 và 2027, kèm nhãn chỉ áp dụng Azure DevOps Services.

Chỉ dùng sandbox được ủy quyền hoặc tài liệu Microsoft có nhãn nguồn; không thay đổi cấu hình tổ chức đang vận hành.

**Script:**

Mở thông báo Public projects retirement của Microsoft, chụp rõ mốc năm 2026 và kế hoạch tự chuyển các public projects còn lại sang private trong 2027. Đây là thay đổi của Azure DevOps Services, không phải luật Git và không suy diễn ngay sang Azure DevOps Server. Cần đọc cả lưu ý về tổ chức trước đây đã bật Allow public projects: có ngoại lệ legacy trong tài liệu nên không nói tuyệt đối mọi tài khoản đều có cùng nút.

**Purpose:**

Ghi rõ chính sách 2026–2027 và ngoại lệ tổ chức legacy.

## Ảnh hưởng của việc bật hoặc tắt dịch vụ Azure Repos

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:22–06:34`

**Visual:**

Thu thông báo retirement vào góc, mở bảng Services và sidebar có/không có Azure Repos.

**Script:**

Project có thể truy cập mà không thấy tab Repos. Liệu chính dịch vụ đang bị tắt?

**Purpose:**

Chứng minh mất tab Repos có thể do dịch vụ tắt, chưa đủ kết luận bị từ chối quyền.

### Scene 1 — Ảnh hưởng của việc bật hoặc tắt dịch vụ Azure Repos

**Time:** `06:34–07:31`

**Visual:**

Hiển thị Project Settings → Overview → Services, khoanh trạng thái Repos enabled và ảnh sidebar thiếu Repos khi dịch vụ tắt.

Chỉ dùng sandbox được ủy quyền hoặc tài liệu Microsoft có nhãn nguồn; không thay đổi cấu hình tổ chức đang vận hành.

**Script:**

Không nhìn thấy Azure Repos chưa chắc là mất quyền Read trên repo. Có thể dịch vụ Repos đã bị tắt ở project, khiến mục này không hiện trong thanh điều hướng. Mở phần Services để quan sát trạng thái bật/tắt bằng screenshot được phép; chúng ta không tắt service thật vì có thể ảnh hưởng đồng đội. Sau đó mới kiểm tra quyền user nếu Repos đang hoạt động. Tách lỗi tính năng bị ẩn khỏi lỗi quyền cụ thể.

**Purpose:**

Tách lỗi ẩn dịch vụ và lỗi phân quyền repository.

## Bằng chứng về cấu trúc project, repository và ranh giới quản trị

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:31–07:42`

**Visual:**

Gom RetailCo → Checkout → checkout-api thành breadcrumb và bảng thu thập bằng chứng.

**Script:**

Hãy gom hierarchy, trạng thái dịch vụ và breadcrumb repo trước khi chẩn đoán quyền.

**Purpose:**

Chuẩn bị thông tin phạm vi chính xác cho bước chẩn đoán phân quyền tiếp theo.

### Scene 1 — Bằng chứng về cấu trúc project, repository và ranh giới quản trị

**Time:** `07:42–08:37`

**Visual:**

Đi dọc breadcrumb RetailCo → Checkout → checkout-api rồi chuyển sang Warehouse, đóng băng chỗ breadcrumb thay đổi.

Chỉ dùng sandbox được ủy quyền hoặc tài liệu Microsoft có nhãn nguồn; không thay đổi cấu hình tổ chức đang vận hành.

**Script:**

Chốt bằng việc ghi lại organization, project, repository và nhánh main đích đang được chọn. Chuyển sang Warehouse để thấy breadcrumb khác, dù người đăng nhập không đổi. Mỗi lần screenshot hoặc trình bày policy phải ghi rõ scope; nếu không, khán giả có thể hiểu nhầm hai màn hình thuộc cùng repo. Đây là checklist nguồn bằng chứng trước khi chúng ta đi sâu vào access level, security group và quyền kế thừa ở chương sau.

**Purpose:**

Tạo chứng cứ định vị chính xác organization/project/repo/branch.
