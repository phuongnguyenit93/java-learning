---
video:
  url: ""
---

# Phân quyền và quản trị kho mã

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

## Quyền ở workspace, project và repository

<!-- VIDEO_SECTION -->

### Scene 1 — Quyền ở workspace, project và repository

**Time:** `00:00–01:30`

**Visual:**

Trên workspace `orchid-team` zoom vào project Billing rồi repo `invoice-api`; đặt ba khung `Workspace access / Project permissions / Repository permissions`.

**Script:**

Một người thuộc workspace chưa chắc có quyền chỉnh mọi repository. Bitbucket Cloud có các phạm vi quyền vì Orchid muốn một nhóm xem mã chung, nhóm khác sửa `invoice-api`, còn Mai kiểm soát project Billing. Khi cần biết vì sao An không thể cập nhật, hãy ghi đúng tài nguyên và thao tác: xem repo, push nhánh, hay quản trị cấu hình. Mỗi thao tác có điều kiện riêng. Đừng suy từ việc thấy tên workspace sang quyền ghi; hãy kiểm tra quyền thực tế ở cả project và repository.

**Purpose:**

Buộc người học xác định chính xác phạm vi quyền trước khi giải thích truy cập.



## Vai trò của người dùng, nhóm và quản trị viên

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:30–01:42`

**Visual:**

Giữ ba khung Workspace/Project/Repository; đưa nhóm `billing-developers` và Mai vào khung đúng, không tự cấp Admin ở tất cả cấp.

**Script:**

Quyền áp dụng ở workspace, project và repository. Vậy ai nhận những quyền đó, nhóm người dùng khác người quản trị ra sao?

**Purpose:**

Chuyển phạm vi quyền thành cách kiểm tra actor và group đang chịu trách nhiệm.

### Scene 2 — Vai trò của người dùng, nhóm và quản trị viên

**Time:** `01:42–03:12`

**Visual:**

Trong quyền của project Billing hiển thị một nhóm `billing-developers` và An/Bình; dùng biểu đồ thành viên đi vào/ra nhóm, đặt Mai ở vai trò admin cấp phù hợp.

**Script:**

Khi chỉ có một ngoại lệ, cấp trực tiếp cho người dùng có thể dễ hiểu. Nhưng cấp riêng cho từng thành viên sẽ khó bảo trì khi cả đội thay đổi. Nhóm `billing-developers` giúp Orchid cấp cùng phạm vi quyền cho nhiều người và rà soát khi thành viên rời nhóm. Vai trò administrator lại phụ thuộc nơi quản trị: người quản lý một project không tự động là admin tổ chức Atlassian. Khi quay video, dùng tài khoản demo, ẩn email thật và kiểm tra nhãn quyền hiện hành thay vì suy diễn.

**Purpose:**

Chứng minh lý do dùng groups và sự khác nhau của quyền quản trị theo scope.



## Hai môi trường quản trị workspace: Bitbucket và Atlassian Administration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:12–03:24`

**Visual:**

Giữ vai trò Mai và nhóm developers, chia màn hình thành giao diện workspace kiểu cũ và Atlassian Administration mới; đánh dấu nơi cấu hình membership khác.

**Script:**

Đã phân vai quản trị rồi, hãy tìm đúng giao diện quản lý cho từng loại workspace.

**Purpose:**

Giải thích vì sao cùng một thao tác quản trị có thể xuất hiện ở UI khác trên workspace khác loại.

### Scene 3 — Hai môi trường quản trị workspace: Bitbucket và Atlassian Administration

**Time:** `03:24–04:54`

**Visual:**

Chia đôi giao diện minh họa `Older Bitbucket-managed workspace` và `Atlassian-administered workspace`; dẫn mũi tên quyền ứng dụng đến Atlassian Administration, không khẳng định mọi workspace giống nhau.

**Script:**

Bitbucket Cloud có sự khác nhau giữa workspace cũ được quản trị trực tiếp trong Bitbucket và workspace mới do Atlassian Administration quản lý. Vì vậy người học có thể không thấy nút cấp quyền ở vị trí một hướng dẫn cũ mô tả. Trong môi trường Atlassian-administered, quyền truy cập ứng dụng và thành viên có thể được cấp qua quản trị tổ chức; những quyền project và repository còn phải được đánh giá theo phạm vi tương ứng. Ghi rõ môi trường thử nghiệm trước khi chụp thao tác. Đừng cấp quyền rộng chỉ để giao diện trông giống một ảnh mẫu.

**Purpose:**

Hướng dẫn quay đúng hai môi trường quản trị, tránh lẫn quyền ứng dụng với repo rights.



## Tác động của quyền project lên repository thành viên

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:54–05:06`

**Visual:**

Từ hai UI quản trị chuyển thẻ Write cấp Billing xuống `invoice-api` và `billing-docs`, theo dõi cả repository đã tồn tại và repo tạo sau.

**Script:**

Khi cấp quyền ở project, điều gì có thể lan xuống các repository con?

**Purpose:**

Minh họa hệ quả của quyền project áp xuống nhiều repo trước khi đưa ra quyết định cấp quyền.

### Scene 4 — Tác động của quyền project lên repository thành viên

**Time:** `05:06–06:36`

**Visual:**

Project Billing có hai repo `invoice-api` và `billing-docs`; kéo thẻ Write cho `billing-developers` vào project, tô sáng cả hai repo; thêm bảng Read→Write→Create→Admin cho project.

**Script:**

Trong Bitbucket Cloud, cấp quyền project có thể áp dụng với các repository hiện có và sẽ tạo sau trong project. Nếu nhóm phát triển có Write ở Billing, quyền ấy có thể vượt quá nhu cầu khi họ chỉ sửa API mà không nên cập nhật tài liệu vận hành. Tài liệu Atlassian mô tả các cấp Read, Write, Create và Admin ở phạm vi project; đừng chép nguyên bảng ấy cho mọi loại quyền repository. Khi demo, xem đồng thời cả hai repo để chứng minh ảnh hưởng rộng của việc cấp quyền tại project.

**Purpose:**

Dùng tình huống kế thừa để giải thích lợi ích và nguy cơ cấp quyền quá rộng.



## Quyền xem, sửa, quản trị và phạm vi hiển thị

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:36–06:48`

**Visual:**

Giữ vùng hai repo được kế thừa Write; đặt `public/private` và thao tác `Clone / Push / Settings` thành các cột riêng.

**Script:**

Từ kế thừa quyền, cần tách khả năng nhìn thấy repository với khả năng sửa nó.

**Purpose:**

Ngăn suy rằng có thể xem repository thì mặc nhiên có quyền ghi hoặc quản trị.

### Scene 5 — Quyền xem, sửa, quản trị và phạm vi hiển thị

**Time:** `06:48–08:18`

**Visual:**

Đặt repository demo `public` và `private` cạnh hai hành động `Clone`/`Push`; dùng biểu tượng mắt, bút và bánh răng, không hiển thị token đăng nhập.

**Script:**

Read cho phép xem nội dung ở phạm vi được cấp; Write cho phép đóng góp; Admin liên quan cấu hình trong phạm vi đó. Project có thêm Create để tạo repository. Nhưng public không có nghĩa ai cũng được push; và private không có nghĩa tất cả thành viên workspace đều tự xem được. Muốn xác minh, thử đọc bằng tài khoản demo phù hợp rồi kiểm tra quyền ghi riêng, không dùng token có đặc quyền cao làm kết quả mẫu. Mục tiêu là phân biệt visibility và operation permission bằng hai phép quan sát khác nhau.

**Purpose:**

Làm rõ quyền Read/Write/Admin và visibility thay vì suy ra quyền từ trạng thái public.



## Thiếu quyền và cấp quyền quá rộng: Dấu hiệu và tác động

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:18–08:30`

**Visual:**

Đổi từ bảng Read/Write/Admin sang checklist `Actor, Operation, Repo, Branch, Error`; minh họa An không thể push `main` dù đọc được source.

**Script:**

Khi một thao tác thất bại hoặc quá dễ thành công, nhóm nên điều tra thế nào mà không leo quyền bừa bãi?

**Purpose:**

Chuyển mô hình quyền thành quy trình chẩn đoán thiếu/được cấp thừa quyền theo hành động cụ thể.

### Scene 6 — Thiếu quyền và cấp quyền quá rộng: Dấu hiệu và tác động

**Time:** `08:30–10:00`

**Visual:**

Hiện bảng điều tra `Actor / Operation / Scope / Message / Effective permission`; điền tình huống An không push được `main`, đối chiếu trường hợp tài khoản chỉ đọc lại có Admin.

**Script:**

Nếu An mở được PR mà không merge được, lỗi có thể nằm ở branch restriction chứ không phải quyền vào workspace. Nếu một người chỉ cần đọc nhưng lại sửa được settings, đó là dấu hiệu cấp quyền thừa. Mai ghi lại tài khoản nào, thao tác nào, repo nào, nhánh nào, thông báo cụ thể và quy tắc tương ứng. Sau đó kiểm tra quyền project, repo và nhánh, ưu tiên thu hẹp đúng phạm vi. Đừng vội cấp Admin hoặc tắt mọi chính sách để 'chữa' một thông báo từ chối; làm vậy xóa bằng chứng và tăng rủi ro.

**Purpose:**

Đặt quy trình chẩn đoán quyền an toàn, chuẩn bị cho PR/policy sau này.
