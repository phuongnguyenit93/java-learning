---
video:
  url: ""
---

# Thành viên, nhóm bảo mật và quyền hiệu lực

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

## Access level và security permission: hai loại kiểm soát truy cập

<!-- VIDEO_SECTION -->

### Scene 1 — Access level và security permission: hai loại kiểm soát truy cập

**Time:** `00:00–00:59`

**Visual:**

Đặt màn hình access level Basic/Stakeholder của user bên trái và bảng Security Read/Contribute cùng danh tính bên phải.

Dùng tài khoản sandbox được ủy quyền, che danh tính thật; ảnh tài liệu chính thức phải ghi nhãn minh họa, không đổi quyền production.

**Script:**

Hãy nhìn hai cột trên cùng một danh tính: access level Basic hoặc Stakeholder là phạm vi tính năng được cấp, còn permission Read/Contribute là quyền trên repository. Một người có Allow trên repo chưa chắc mở được tính năng nếu entitlement của họ không phù hợp. Với private Git repo, đối chiếu điều kiện Basic trong tài liệu Microsoft. Không thử đổi gói truy cập thực chỉ để chứng minh; ta đọc giá trị đang có và dự đoán kết quả trước khi mở repository.

**Purpose:**

Tách cấp truy cập tính năng khỏi quyền tài nguyên.

## Nhóm Readers, Contributors và Project Administrators cùng quyền mặc định

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:59–01:11`

**Visual:**

Từ bộ chọn Basic/Stakeholder, mở bảng Readers, Contributors và Project Administrators.

**Script:**

Basic hay Stakeholder quyết định tính năng; nhóm bảo mật nào quyết định thao tác thực tế?

**Purpose:**

Chuyển từ quyền dùng tính năng sang nhóm bảo mật mặc định, tránh nhập hai lớp.

### Scene 1 — Nhóm Readers, Contributors và Project Administrators cùng quyền mặc định

**Time:** `01:11–02:08`

**Visual:**

Trên trang Security mở lần lượt ba nhóm Readers, Contributors, Project Administrators, chỉ quyền mẫu thay vì suy từ tên.

Dùng tài khoản sandbox được ủy quyền, che danh tính thật; ảnh tài liệu chính thức phải ghi nhãn minh họa, không đổi quyền production.

**Script:**

Chúng ta so ba nhóm thường thấy: Readers thường dùng để đọc, Contributors có đường đóng góp khi quyền cho phép, còn Project Administrators quản lý phạm vi cấu hình dự án. Đây là điểm xuất phát, không phải cam kết mọi quyền áp dụng giống nhau ở mỗi repo. Chọn một danh tính test ở từng nhóm và đọc quyền hiệu lực thay vì chỉ nhìn tên nhóm. Nhất là quyền bypass không được mặc định suy ra từ nhãn Admin.

**Purpose:**

Không đánh đồng tên nhóm với quyền hiệu lực.

## Phân quyền trực tiếp và quyền qua security group

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:08–02:19`

**Visual:**

Chọn Alice trên bảng nhóm, hiện quyền trực tiếp cạnh các quyền nhận từ hai nhóm.

**Script:**

Một nhóm chưa phải nguồn quyền duy nhất; Alice còn có quyền đặt trực tiếp.

**Purpose:**

Chỉ ra những nguồn khác nhau tạo ra quyền cấu hình của một người.

### Scene 1 — Phân quyền trực tiếp và quyền qua security group

**Time:** `02:19–03:16`

**Visual:**

Chọn user Alice trong ví dụ, mở membership hai nhóm Contributors và Restricted; đối chiếu Allow trực tiếp và Deny qua nhóm.

Dùng tài khoản sandbox được ủy quyền, che danh tính thật; ảnh tài liệu chính thức phải ghi nhãn minh họa, không đổi quyền production.

**Script:**

Chọn Alice trong ví dụ và mở danh sách nhóm mà cô ấy thuộc về. Một quyền có thể đến từ thiết lập trực tiếp hoặc từ nhiều nhóm; nếu cùng scope có Allow và Deny xung đột, Deny thông thường chiếm ưu tiên. Hãy ghi rõ nguồn của mỗi quyền bên cạnh giá trị, không chỉ chụp một dấu kiểm. Khi tìm lỗi 'Alice không push được', phải kiểm tra toàn bộ membership chứ không sửa ngay cấu hình repository.

**Purpose:**

Truy tìm nguồn quyền direct và group.

## Phạm vi quyền organization, project, repository, branch và kế thừa

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:16–03:26`

**Visual:**

Xếp quyền Alice thành các lớp organization, project, repository và nhánh main.

**Script:**

Ta xếp các quyền đó theo organization, project, repository và từng nhánh.

**Purpose:**

Nhấn mạnh phạm vi của quyền quan trọng tương đương nguồn cấp quyền.

### Scene 1 — Phạm vi quyền organization, project, repository, branch và kế thừa

**Time:** `03:26–04:24`

**Visual:**

Vẽ bốn tầng quyền organization/project/repository/main branch; chuyển giữa Repo Security và Main Branch security.

Dùng tài khoản sandbox được ủy quyền, che danh tính thật; ảnh tài liệu chính thức phải ghi nhãn minh họa, không đổi quyền production.

**Script:**

Trên sơ đồ bốn tầng, quyền từ project/repository có thể được kế thừa xuống branch, nhưng nhánh main có thể có cài đặt riêng. Chuyển giữa Security của repo và Branch security của main, giữ nguyên danh tính Alice ở cả hai để so sánh. Một Allow ở cấp cha chưa kết luận được quyền ở cấp con; chúng ta ghi rõ đối tượng đang xét trước khi giải thích kết quả. Đây là cơ chế quyền, chưa phải branch policy xét PR.

**Purpose:**

Giải thích phạm vi quyền kế thừa tới branch.

## Trạng thái Allow, Deny, Not set và quyền hiệu lực

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:24–04:35`

**Visual:**

Phóng to dòng Git Security với Allow, Deny và Not set trên sơ đồ nhánh.

**Script:**

Bảng Security có Allow, Deny và Not set. Ba nhãn ấy thực sự nghĩa là gì?

**Purpose:**

Giới thiệu ba trạng thái cấu hình trước khi xét quyền thao tác thực tế.

### Scene 1 — Trạng thái Allow, Deny, Not set và quyền hiệu lực

**Time:** `04:35–05:35`

**Visual:**

Dựng bảng ba trạng thái Allow, Deny, Not set và đối chiếu với mục Effective permissions của cùng một user.

Dùng tài khoản sandbox được ủy quyền, che danh tính thật; ảnh tài liệu chính thức phải ghi nhãn minh họa, không đổi quyền production.

**Script:**

Tạm dừng bảng quyền: Allow cấp tại nơi đặt, Deny chặn theo quy tắc đánh giá, Not set không phải 'được phép' và cũng không phải 'bị cấm'. Hãy đặt trường hợp Alice có Not set trực tiếp nhưng nhận Allow qua Contributors để thấy giá trị trống không đủ kết luận. Sau đó thêm ví dụ cùng scope nhận Deny từ nhóm Restricted và kiểm tra hiệu lực. Không biến ví dụ thành khẩu quyết Deny luôn thắng mọi cấp trong mọi quan hệ kế thừa.

**Purpose:**

Phân biệt Not set với quyền cho phép và Deny.

## Mối liên hệ giữa quyền explicit, inherited và effective

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:35–05:46`

**Visual:**

Đổi ba nhãn thành bảng explicit, inherited và effective permissions của Alice.

**Script:**

Trạng thái cấu hình chưa hẳn là quyền hiệu lực; kết quả còn do kế thừa.

**Purpose:**

Phân biệt cấu hình đang nhìn thấy với kết quả quyền hệ thống thực sự đánh giá.

### Scene 1 — Mối liên hệ giữa quyền explicit, inherited và effective

**Time:** `05:46–06:44`

**Visual:**

Trên một quyền, dán ba nhãn explicit, inherited, effective cạnh hàng Security thực để đọc nguồn cấp quyền.

Dùng tài khoản sandbox được ủy quyền, che danh tính thật; ảnh tài liệu chính thức phải ghi nhãn minh họa, không đổi quyền production.

**Script:**

Đặt ba nhãn lên một quyền: explicit là giá trị đặt ngay tại scope đang xem, inherited là cấu hình truyền từ cha hoặc nhóm, effective là kết quả sau khi tổng hợp. Trên ảnh demo, Not set trực tiếp vẫn có thể dẫn đến quyền hiệu lực Allow vì nguồn khác. Ta không tự đoán thuật toán từ màu ô. Khi có chênh, mở công cụ xem quyền hiệu lực được sản phẩm hỗ trợ hoặc yêu cầu quản trị viên xác minh.

**Purpose:**

Tách quyền explicit, inherited và effective.

## Các quyền đọc, đóng góp và quản trị repository Git

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:44–06:56`

**Visual:**

Giữ cột quyền hiệu lực, mở các thao tác Read, Contribute và quản trị repository.

**Script:**

Khi biết quyền hiệu lực, Alice có thể Read, Contribute hay quản trị Git ở mức nào?

**Purpose:**

Chuyển khái niệm quyền trừu tượng thành hành động Git cụ thể.

### Scene 1 — Các quyền đọc, đóng góp và quản trị repository Git

**Time:** `06:56–07:52`

**Visual:**

Đánh dấu lần lượt Read, Contribute, Create branches, Force push, Edit policies và Manage permissions trong bảng Git security.

Dùng tài khoản sandbox được ủy quyền, che danh tính thật; ảnh tài liệu chính thức phải ghi nhãn minh họa, không đổi quyền production.

**Script:**

Mở bảng quyền Git repository và phân nhóm: Read cho xem hoặc clone; Contribute liên quan push lên nhánh được phép; tạo branch hoặc tag có thể yêu cầu quyền riêng; Edit policies và Manage permissions là công việc quản trị. Force push có thể viết lại lịch sử hoặc xóa tham chiếu nên phải hạn chế. Một người đọc code không đồng nghĩa được ghi, và một người push nhánh feature không đồng nghĩa được sửa luật main.

**Purpose:**

Gắn Read/Contribute/Force push/policy với hành vi cụ thể.

## Tương tác giữa Deny, quyền kế thừa và cấu hình theo phạm vi

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:52–08:03`

**Visual:**

Đặt Deny ở repository và Allow trên branch tại đúng cấp trong bảng thao tác.

**Script:**

Quyền ở repo và quyền riêng của nhánh có thể khác; cần so đúng phạm vi.

**Purpose:**

Làm rõ sự tương tác giữa kế thừa và độ cụ thể của phạm vi.

### Scene 1 — Tương tác giữa Deny, quyền kế thừa và cấu hình theo phạm vi

**Time:** `08:03–09:00`

**Visual:**

Đặt hai sơ đồ nhỏ cạnh nhau: xung đột Allow/Deny cùng scope và quyền kế thừa từ repo sang nhánh con.

Dùng tài khoản sandbox được ủy quyền, che danh tính thật; ảnh tài liệu chính thức phải ghi nhãn minh họa, không đổi quyền production.

**Script:**

Hãy so hai tình huống chứ không gộp chúng. Trường hợp thứ nhất, hai nhóm cấp Allow và Deny ngay cùng một scope: Deny thường ưu tiên. Trường hợp thứ hai, một giá trị inherited ở cha có thể tương tác khác với cấu hình explicit ở branch con. Vì vậy câu 'Deny luôn thắng' thiếu bối cảnh, còn 'Allow của user luôn thắng group' cũng sai. Bản demo chỉ dừng ở việc ghi nguồn và xem kết quả hiệu lực thật.

**Purpose:**

Phân biệt xung đột cùng scope và override theo cấp.

## Quyền Contribute to pull requests so với quyền Contribute vào nhánh

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:00–09:11`

**Visual:**

Đổi sáng từ Branch Contribute sang Contribute to pull requests cạnh bảng reviewer votes.

**Script:**

Quyền thay đổi branch và quyền góp ý PR là hai thao tác cần xét riêng.

**Purpose:**

Ngăn nhầm tham gia đánh giá PR với quyền ghi Git branch.

### Scene 1 — Quyền Contribute to pull requests so với quyền Contribute vào nhánh

**Time:** `09:11–10:04`

**Visual:**

Khoanh hai hàng quyền tách biệt Contribute to pull requests và Contribute, bên cạnh là phần vote/comment trên PR.

Dùng tài khoản sandbox được ủy quyền, che danh tính thật; ảnh tài liệu chính thức phải ghi nhãn minh họa, không đổi quyền production.

**Script:**

Một reviewer có thể đọc, bình luận và bỏ phiếu PR khi quyền 'Contribute to pull requests' và entitlement cho phép, nhưng không có quyền Contribute để push lên nhánh source. Đây là hai thao tác khác nhau. Trong demo, chỉ rõ nút comment/vote trên PR và nút push không thuộc cùng trang quyền. Đừng nhầm việc tạo hoặc review PR với quyền cập nhật main; cần kiểm tra quyền tại từng hành động.

**Purpose:**

Tách quyền PR review và quyền push vào nhánh.

## Chẩn đoán quyền hiệu lực khi không thể xem hoặc sửa repository

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:04–10:15`

**Visual:**

Đặt thao tác bị từ chối cạnh cây kiểm tra access level, nhóm và effective rights.

**Script:**

Nếu thao tác thất bại, hãy kiểm tra access level, nhóm, quyền hiệu lực và scope.

**Purpose:**

Biến mô hình phân quyền đã học thành một quy trình điều tra có thể tái hiện.

### Scene 1 — Chẩn đoán quyền hiệu lực khi không thể xem hoặc sửa repository

**Time:** `10:15–11:12`

**Visual:**

Vẽ lưu đồ chẩn đoán: Repos enabled, access level, membership, repo Read, branch Contribute và effective permissions.

Dùng tài khoản sandbox được ủy quyền, che danh tính thật; ảnh tài liệu chính thức phải ghi nhãn minh họa, không đổi quyền production.

**Script:**

Giả sử Alice báo không thấy Repos, không clone được hoặc bị từ chối push. Ba triệu chứng đòi ba tuyến kiểm tra khác nhau: Repos có bật, Services hay Server, cấp truy cập phù hợp cho private repo, membership project, repo Read và branch Contribute. Sau đó mở effective permissions của đúng user/branch, kiểm tra group Deny hoặc quyền riêng. Chỉ đổi cấu hình khi đã chỉ ra nguyên nhân, không cấp toàn bộ Project Administrators để 'chữa' một lỗi quyền.

**Purpose:**

Dạy kiểm tra quyền hiệu lực theo triệu chứng, không cấp quyền bừa.
