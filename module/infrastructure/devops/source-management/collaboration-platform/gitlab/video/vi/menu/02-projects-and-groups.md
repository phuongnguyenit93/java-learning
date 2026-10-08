---
video:
  url: ""
---

# Tổ chức project và group trên GitLab

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

## Sở hữu project: namespace cá nhân so với group

<!-- VIDEO_SECTION -->

### Scene 1 — Sở hữu project: namespace cá nhân so với group

**Time:** `00:00–01:11`

**Visual:**

So sánh namespace `alice/demo` và `company/payments/gateway`. Khi chuyển owner, đánh dấu URL, membership và integrations bằng biểu tượng kiểm tra chứ không giả lập một thao tác chuyển thật.

**Script:**

Hãy nhìn hai đường dẫn cùng chứa mã nguồn, nhưng khác người chịu trách nhiệm. Alice có thể quản lý demo cá nhân; gateway thanh toán nên thuộc group để quyền duy trì không biến mất khi một thành viên rời nhóm. Nếu muốn di chuyển project giữa namespace, ta phải cân nhắc liên kết, quyền và những tích hợp phụ thuộc đường dẫn. Không thể dựa vào tên owner để kết luận mọi người đang được phép làm gì. Trước khi mời reviewer, hãy kiểm tra đúng nơi project tồn tại và ai quản trị nó.

**Purpose:**

Giúp chọn chủ sở hữu bền vững và chỉ ra tác động thực tế của namespace đối với cộng tác.

## Tổ chức group, subgroup và các project thành viên

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:11–01:24`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "owner xác định nơi chịu trách nhiệm". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Owner ở cấp group cho phép nhiều project chung chính sách; tiếp theo ta cần nhìn cấu trúc cha-con.

**Purpose:**

Chuyển từ owner xác định nơi chịu trách nhiệm sang câu hỏi group là tầng quản trị, không phải thư mục source theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Tổ chức group, subgroup và các project thành viên

**Time:** `01:24–02:34`

**Visual:**

Dựng cây `company → payments → gateway,billing`, tô hình cây organization khác hình Git tree; mũi tên kế thừa từ company xuống payments được gắn 'tùy cấu hình'.

**Script:**

Group có thể gom nhiều project và subgroup giúp chia theo team hoặc sản phẩm. Ví dụ payments chứa gateway và billing, nhưng gateway không phải tệp nằm trong repository billing. Khi người quản trị thêm thành viên ở group cha, họ có thể tạo nguồn quyền được kế thừa; một lời mời ở project đơn lẻ lại có phạm vi khác. Trên GitLab hãy mở Members và quan sát nguồn quyền thay vì nhìn mỗi đường dẫn. Ranh giới group phục vụ quản trị, còn quyết định triển khai dịch vụ thuộc chỗ khác.

**Purpose:**

Phân biệt cây namespace với cây Git, giải thích tại sao người dùng có thể thừa hưởng quyền.

## Repository và thiết lập quản trị ở cấp project

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:34–02:47`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "group là tầng quản trị, không phải thư mục source". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Group xác định chủ thể quản trị, còn từng project có những thiết lập riêng ảnh hưởng trải nghiệm MR.

**Purpose:**

Chuyển từ group là tầng quản trị, không phải thư mục source sang câu hỏi default branch là ngữ cảnh duyệt, không phải production theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Repository và thiết lập quản trị ở cấp project

**Time:** `02:47–03:56`

**Visual:**

Phác Settings của project gateway: visibility, default branch, MR, Members. Thử chuyển nhãn `master` sang `main` trên bản vẽ, tô mục tiêu MR nhưng để ô deployed trống.

**Script:**

Một project cho người quản trị đặt nhánh mặc định, chế độ hiển thị và cách cộng tác. Khi đổi default branch, cách GitLab mở Code và gợi ý target có thể thay đổi. Nhưng không có gì trong thao tác đó chứng minh production đang chạy nhánh mới. Trên bảng minh họa, ta rà MR đang mở, protected branch và integrations trước khi coi việc đổi tên là an toàn. Người xem source cũng chưa chắc có quyền sửa Settings. Bài học là luôn kiểm tra ngữ cảnh project và quyền của actor.

**Purpose:**

Giúp đọc project settings đúng vai trò và tách default-branch khỏi deployment.

## Public, private, internal: GitLab.com không tạo internal mới nhưng dự án cũ vẫn giữ chế độ này

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:56–04:09`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "default branch là ngữ cảnh duyệt, không phải production". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Cấu hình visibility tưởng giống mọi dịch vụ host, nhưng 'Internal' trên GitLab có lịch sử và phạm vi rất cụ thể.

**Purpose:**

Chuyển từ default branch là ngữ cảnh duyệt, không phải production sang câu hỏi Internal của GitLab không giống Internal của GitHub theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Public, private, internal: GitLab.com không tạo internal mới nhưng dự án cũ vẫn giữ chế độ này

**Time:** `04:09–05:21`

**Visual:**

Bảng ba cột Public/Private/Internal với hàng GitLab.com mới, GitLab.com legacy, Self-Managed/Dedicated. Để ô 'Internal mới trên GitLab.com' đánh dấu unavailable, không đồng nhất với GitHub enterprise.

**Script:**

Ở GitLab, Public cho phép người ngoài đọc nguồn được công khai; Private yêu cầu quyền thích hợp. Internal trên GitLab Self-Managed hoặc Dedicated thường liên quan người dùng đã xác thực trong instance, trừ external users. Đối với GitLab.com, không thể tạo project Internal mới; một số project đã có trạng thái đó trước đây vẫn giữ chế độ legacy. Đây không phải Internal toàn enterprise theo khái niệm GitHub Enterprise Cloud. Với gateway chứa mã độc quyền, hãy kiểm tra offering và audience thực tế trước khi chọn visibility, tuyệt đối không dựa vào một nhãn quen mắt.

**Purpose:**

Nêu chính xác giới hạn GitLab.com và khác biệt audience của Internal theo offering.

## Ranh giới group/project đối với việc chia sẻ và cộng tác

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:21–05:34`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "Internal của GitLab không giống Internal của GitHub". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Chọn visibility và owner rồi vẫn chưa biết quyền cụ thể của từng người; ta cần xét nơi lời mời được cấp.

**Purpose:**

Chuyển từ Internal của GitLab không giống Internal của GitHub sang câu hỏi quyền group cha không đồng nghĩa lời mời project theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Ranh giới group/project đối với việc chia sẻ và cộng tác

**Time:** `05:34–06:46`

**Visual:**

Hai nhánh sơ đồ: người được mời trực tiếp vào gateway và người thừa hưởng quyền qua payments group; tô hai project gateway/billing với đường biên quyền khác nhau.

**Script:**

Một người được mời vào gateway không tự động quản lý tất cả project thuộc payments. Ngược lại, thành viên từ group cha có thể nhận quyền đối với nhiều project con, tùy chính sách. Đây là lý do không nên mời nhà thầu vào cả group nếu họ chỉ cần xem đúng gateway. Trên màn hình minh họa, ta yêu cầu người quản trị mở Members để phân biệt quyền trực tiếp, quyền kế thừa và quyền qua group được chia sẻ. Chỉ sau đó mới kết luận người đó có thể mở MR hay xem repository khác.

**Purpose:**

Tổng kết ranh giới group/project và dẫn sang quyền hiệu lực của người tham gia.
