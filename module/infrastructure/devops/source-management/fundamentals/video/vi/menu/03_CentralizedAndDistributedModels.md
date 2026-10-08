---
video:
  url: ""
---

# Mô hình tập trung và phân tán

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

## Kiểm soát phiên bản tập trung: Mô hình máy chủ và máy khách

<!-- VIDEO_SECTION -->

### Scene 1 — Kiểm soát phiên bản tập trung: Mô hình máy chủ và máy khách

**Time:** `00:00–01:15`

**Visual:**

Vẽ mô hình ba máy trạm An/Bình/Lan nối vào `Server lịch sử chính`; mũi tên `checkout` và `check in` hướng về máy chủ; tô xám khi mất kết nối.

**Script:**

Trong mô hình kiểm soát phiên bản tập trung, máy chủ giữ kho lịch sử chính và các máy khách làm việc dựa trên nó. Một hệ thống tiêu biểu là Subversion. Người lập trình có thể sửa tệp đã lấy về, nhưng những thao tác ghi nhận lịch sử mới thường cần liên hệ máy chủ. Vì vậy khi đường truyền gián đoạn, tệp trong working copy vẫn có thể đọc và chỉnh sửa, còn việc tra cứu hoặc ghi lịch sử theo cơ chế máy chủ có thể bị hạn chế. Nhìn hình này, đừng hiểu máy khách không thể làm việc offline chút nào; điều bị phụ thuộc là những chức năng cần kho trung tâm.

**Purpose:**

Mô tả chính xác nơi đặt lịch sử authoritative trong CVCS và phân biệt sửa tệp với ghi lịch sử.



## Kiểm soát phiên bản phân tán: Bản sao chứa lịch sử trên máy cá nhân

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:15–01:27`

**Visual:**

Giữ máy chủ trung tâm chứa history A–B; nhân bản A–B vào repository của An/Bình, tô riêng commit C tại máy An và xoá mũi tên mạng.

**Script:**

Nếu lịch sử không chỉ ở một máy chủ thì bản sao trên máy cá nhân thay đổi ra sao?

**Purpose:**

Biểu diễn khác biệt nơi dữ liệu lịch sử tồn tại giữa CVCS và DVCS chứ không chỉ thay tên công cụ.

### Scene 2 — Kiểm soát phiên bản phân tán: Bản sao chứa lịch sử trên máy cá nhân

**Time:** `01:27–02:42`

**Visual:**

Thay server-only timeline bằng hai hộp repository của An và Bình, mỗi hộp có cùng commit A–B và nhánh cục bộ riêng. Mũi tên trao đổi A/B chỉ xuất hiện khi kết nối.

**Script:**

Mô hình phân tán như Git đặt dữ liệu lịch sử trong mỗi bản clone thông thường. An vẫn có thể xem commit cũ, tạo commit mới và so sánh phiên bản ngay khi ngắt mạng, bởi các đối tượng lịch sử cần thiết đã ở local. Khi có kết nối, các kho mới trao đổi thay đổi với nhau hoặc qua server chung. Phân tán không có nghĩa mọi clone luôn đồng bộ hoặc được tin cậy như nhau. Nhóm vẫn chọn một nguồn tích hợp được chấp nhận và quyền ghi nguồn ấy. Bare repository hay clone nông có giới hạn riêng; ở đây ta đang nói bản clone thông thường có lịch sử đã tải về.

**Purpose:**

Cho thấy lịch sử trong local clone làm được gì offline, không đồng nhất DVCS với thiếu quản trị.



## So sánh khả năng làm việc cục bộ và phối hợp

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:42–02:54`

**Visual:**

Thu nhỏ hai clone đang có lịch sử A–B/C; dựng bảng CVCS/DVCS với các ô offline commit, sync, history availability và quyền cập nhật nhánh chung.

**Script:**

Có thể commit offline là lợi thế, nhưng đội nhóm phải trả giá gì cho nhiều bản lịch sử?

**Purpose:**

Biến lợi thế local commit thành đánh đổi phối hợp, đồng bộ và quyền thay vì coi DVCS luôn tốt hơn.

### Scene 3 — So sánh khả năng làm việc cục bộ và phối hợp

**Time:** `02:54–04:09`

**Visual:**

Bảng so sánh hai cột: `CVCS: máy chủ là điểm phối hợp`, `DVCS: local history + trao đổi`; tô sáng hàng `offline`, `quyền`, `đồng bộ`, `khả năng kiểm tra`.

**Script:**

Tập trung có thể đơn giản hóa việc biết đâu là kho chính và quản lý phiên bản tại server, nhưng phụ thuộc khả năng truy cập dịch vụ khi cần ghi lịch sử. Phân tán làm việc local linh hoạt, song mỗi bản sao có thể tiến theo hướng khác nhau và cần đồng bộ khi chia sẻ. Đừng chọn chỉ bằng nhãn hệ thống: hãy hỏi nhóm thường mất mạng không, có cần nhánh cục bộ không, ai quản lý quyền cập nhật nguồn chung, và cách phát hiện phân kỳ là gì. Cả hai mô hình đều có thể xây quy trình review và chính sách tiếp nhận riêng.

**Purpose:**

Dạy đánh đổi thực tế và tiêu chí quyết định, tránh tuyên bố một mô hình luôn tốt hơn.



## Khả năng truy cập lịch sử và quản trị bản sao

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:09–04:21`

**Visual:**

Giữ bảng so sánh offline và sync; đưa ba mũi tên clone An, clone Bình, remote vào đồ thị, đánh dấu `History available` và `Accepted` là hai nhãn khác nhau.

**Script:**

Nhiều bản sao chứa lịch sử đem lại quyền tự chủ, nhưng lịch sử nào được nhóm tin nhận?

**Purpose:**

Làm rõ nhiều bản sao có lịch sử vẫn không thay thế lựa chọn và bảo vệ nguồn tích hợp của nhóm.

### Scene 4 — Khả năng truy cập lịch sử và quản trị bản sao

**Time:** `04:21–05:36`

**Visual:**

Hiện ba hộp lịch sử An/Bình/Server; trên cùng là hai nhãn riêng `Lịch sử còn có thể đọc` và `Lịch sử được nhóm chấp nhận`; mũi tên không tự động.

**Script:**

Một clone Git có thể chứa lịch sử và hoạt động khi máy chủ không kết nối, nhưng clone ấy không vì thế là nguồn được nhóm phê duyệt. Nhóm thường chỉ định repository hoặc nhánh tích hợp có quy tắc quyền và review. Với hệ tập trung, máy chủ giữ lịch sử trung tâm nhưng vẫn cần backup độc lập và quản trị truy cập. Với hệ phân tán, nhiều bản sao giúp tăng khả năng còn dữ liệu lịch sử, nhưng không bảo đảm mọi nội dung trong các bản sao đều an toàn hoặc đã được duyệt. Hãy tách khả năng có lịch sử khỏi quyền tuyên bố một phiên bản là chuẩn.

**Purpose:**

Tách availability khỏi governance và accepted source; không nhầm replication với authorization.



## Tình huống máy chủ gián đoạn: Khả năng sử dụng lịch sử cục bộ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:36–05:48`

**Visual:**

Cho remote chuyển sang `unavailable`; giữ clone An với history A–B/C và một working copy CVCS bên cạnh, khoanh thao tác đọc lịch sử so với check-in server.

**Script:**

Hãy thử một phép kiểm chứng đơn giản khi mạng bị cắt.

**Purpose:**

Dùng outage case kiểm chứng chính xác thao tác DVCS cục bộ còn làm được và ghi revision CVCS nào phụ thuộc server.

### Scene 5 — Tình huống máy chủ gián đoạn: Khả năng sử dụng lịch sử cục bộ

**Time:** `05:48–07:03`

**Visual:**

Chia đôi màn hình: bản clone Git đã tải về chạy minh họa `git log`/`git commit` khi offline; working copy CVCS có file đọc được nhưng biểu tượng server check-in không khả dụng; chú thích điều kiện `đã clone đủ lịch sử`.

**Script:**

Giả sử cả nhóm mất kết nối với server trong một giờ. Trong một bản clone Git thông thường đã tải đủ đối tượng, An có thể đọc lịch sử và commit local rồi đồng bộ sau. Ở máy khách CVCS phụ thuộc server để ghi revision mới, An vẫn sửa được tệp đã lấy về nhưng không ghi revision trung tâm trong lúc server không có mặt. Đây là khác biệt quan sát được về nơi xử lý lịch sử, chứ không phải bằng chứng rằng DVCS tự giải quyết xung đột hay bỏ qua quyền tiếp nhận. Khi mạng trở lại, cả hai vẫn phải đối chiếu, kiểm tra và đưa thay đổi đến nguồn nhóm theo quy trình.

**Purpose:**

Kết thúc bằng kịch bản outage có giả định rõ và kết quả quan sát kiểm chứng được.
