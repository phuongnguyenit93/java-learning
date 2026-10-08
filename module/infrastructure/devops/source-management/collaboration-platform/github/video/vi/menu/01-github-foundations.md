---
video:
  url: ""
---

# Nền tảng cộng tác mã nguồn trên GitHub

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

## GitHub: nền tảng lưu trữ repository và phối hợp thay đổi của nhóm

<!-- VIDEO_SECTION -->

### Scene 1 — GitHub: nền tảng lưu trữ repository và phối hợp thay đổi của nhóm

**Time:** `00:00–01:01`

**Visual:**

Dựng cảnh ba tệp nén trùng tên trên bàn, rồi thay bằng sơ đồ một repository GitHub; lần lượt sáng vùng Code, Pull requests, Settings. Gắn nhãn 'minh họa, không phải trạng thái repo thật'.

**Script:**

Hãy tưởng tượng ba người gửi ba bản mã nguồn mang tên final. Bản nào đã được nhóm đồng ý? GitHub không trả lời thay con người, nhưng gom lịch sử Git và dấu vết quyết định vào cùng nơi. Trên hình, Code cho ta thấy nguồn đã lưu, Pull requests giữ đề xuất và thảo luận, còn Settings giúp người có thẩm quyền đặt quy tắc. Với tình huống sửa lỗi tính tiền, ta cần cả ba lớp: bản sửa, người đánh giá và điều kiện tiếp nhận.

Ta sẽ lần lượt tìm hiểu repository và quyền sở hữu, nhánh chung so với fork, PR/review, quyền thành viên, bảo vệ nhánh, liên kết Issue/Release rồi ghép chúng thành một tình huống xử lý lỗi. Đây là lộ trình cộng tác trên GitHub, không phải bài học về cách Git tạo commit hay cách CI triển khai.

**Purpose:**

Mở đầu bằng vấn đề lựa chọn bản đúng; chỉ ra vai trò của hosting, review và governance mà không đồng nhất GitHub với Git.

## Vấn đề khi chia sẻ mã nguồn thiếu review, truy vết và điều kiện chấp nhận

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:01–01:12`

**Visual:**

Thu nhỏ ba vùng Code / Pull requests / Settings ở góc màn hình; đưa ô còn trống 'đã thử số âm?' của bản sửa làm tròn ra giữa.

**Script:**

Có một nơi lưu mã chưa chắc đã ngăn được lỗi. Ta thử quan sát điều gì xảy ra khi thiếu một bước đánh giá.

**Purpose:**

Chuyển từ nơi lưu thay đổi sang yêu cầu bằng chứng review và kiểm thử trước khi chấp nhận bản sửa.

### Scene 1 — Vấn đề khi chia sẻ mã nguồn thiếu review, truy vết và điều kiện chấp nhận

**Time:** `01:12–02:14`

**Visual:**

Hiển thị bảng tình huống: PR sửa làm tròn; cột 'đã lưu', 'đã thử số âm', 'đã review' và 'được tiếp nhận'. Đánh dấu các ô chưa có bằng chứng, không giả lập kết quả kiểm thử.

**Script:**

Giả sử thay đổi sửa được hóa đơn thông thường nhưng chưa ai kiểm tra đầu vào âm. Việc tệp đã nằm trong kho không nói gì về trường hợp đó. Ta thêm một bản mô tả lỗi, người được yêu cầu review, và một ví dụ có thể tái hiện. Điều cần quan sát không phải huy hiệu xanh giả định; chính là phần còn thiếu trong hồ sơ đề xuất. Review giảm nguy cơ bỏ sót, còn kiểm thử và hiểu nghiệp vụ phải cung cấp bằng chứng riêng.

**Purpose:**

Làm rõ rủi ro thiếu traceability và điều kiện kiểm chứng mà không khẳng định review tự bảo đảm chất lượng.

## Ranh giới trách nhiệm giữa lịch sử Git và cộng tác GitHub

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:14–02:25`

**Visual:**

Biến bảng thiếu bằng chứng thành hai làn: lịch sử commit Git cục bộ bên trái, review và quy tắc GitHub bên phải.

**Script:**

Khi đã có lịch sử và review, ta cần phân định rõ công cụ nào chịu trách nhiệm cho từng phần.

**Purpose:**

Giúp người xem phân biệt bằng chứng lịch sử với bằng chứng đánh giá, tránh xem commit là phê duyệt.

### Scene 1 — Ranh giới trách nhiệm giữa lịch sử Git và cộng tác GitHub

**Time:** `02:25–03:29`

**Visual:**

Chia màn hình: bên trái sơ đồ Git cục bộ có commit/branch; bên phải khung GitHub có PR, reviewer, quyền. Vẽ mũi tên đưa đề xuất sang PR nhưng giữ commit ở phía Git.

**Script:**

Trên máy cá nhân, An vẫn có thể tạo commit và nhánh khi chưa đăng nhập GitHub. Những hành vi đó do Git cung cấp. GitHub nhận repository Git để nhóm cộng tác, yêu cầu reviewer, ghi phản hồi và áp dụng quy tắc trước khi merge. Nếu ai nói 'đã merge ở máy em' thì chưa đủ để kết luận PR trên GitHub đã đáp ứng bảo vệ nhánh. Hai lớp có mối liên hệ, nhưng không cùng một quyết định. Module này theo dõi phần cộng tác, không dạy lại cú pháp lệnh Git.

**Purpose:**

Tạo mô hình hai lớp Git mechanics và GitHub collaboration, tránh nhầm một Git merge với approval.

## Repository GitHub: mã nguồn, lịch sử và thông tin cộng tác

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:29–03:40`

**Visual:**

Đẩy sơ đồ Git cục bộ xuống nền, mở lần lượt bộ chọn nhánh, lịch sử commit và Pull requests trong khung GitHub.

**Script:**

Sau khi tách Git và GitHub, hãy nhìn một repository chứa những phần nào của câu chuyện.

**Purpose:**

Liên hệ ranh giới trách nhiệm vừa học với nơi kiểm tra trạng thái cụ thể trên repository.

### Scene 1 — Repository GitHub: mã nguồn, lịch sử và thông tin cộng tác

**Time:** `03:40–04:43`

**Visual:**

Dùng mock giao diện ba vùng Code, Commits, Pull requests; trên mỗi vùng đóng dấu 'đã ghi nhận', 'lịch sử', 'đang đề xuất'. Gắn nhánh main và nhánh fix-rounding riêng.

**Script:**

Trên tab Code, cây tệp được hiển thị theo nhánh đang chọn. Danh sách commit giúp kiểm tra các trạng thái đã ghi nhận. Nhưng tab Pull requests có thể chứa một thay đổi vẫn chờ đánh giá, chưa thuộc nhánh main. Ta đặt nhánh fix-rounding bên cạnh main và tô phần chênh lệch. Đừng xem ảnh chụp một nhánh là toàn bộ lịch sử hoặc giả định mã trên nhánh mặc định đã chạy ở môi trường sản xuất. Hãy hỏi: đây là trạng thái ghi nhận, hay đề xuất chưa được nhận?

**Purpose:**

Dùng ba góc nhìn phân biệt source tree, lịch sử và thay đổi chưa tích hợp.

## Tài khoản cá nhân, organization và phạm vi quản lý repository

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:43–04:54`

**Visual:**

Giữ main và fix-rounding trên màn Code, sau đó kéo camera lên đường dẫn chủ sở hữu/repository và đặt dấu hỏi về quyền quản trị.

**Script:**

Repository có lịch sử rồi; tiếp theo ai sở hữu nó và ai chịu trách nhiệm quản trị?

**Purpose:**

Từ trạng thái mã chuyển sang câu hỏi ai sở hữu nơi cộng tác trước khi phân tích quyền truy cập.

### Scene 1 — Tài khoản cá nhân, organization và phạm vi quản lý repository

**Time:** `04:54–05:57`

**Visual:**

Vẽ hai namespace: alice/demo và orchid/payments; dưới orchid cho thấy org → teams → repositories, thêm khung enterprise như lớp quản trị có điều kiện, không gán quyền mặc định.

**Script:**

Đường dẫn owner/repository cho biết kho thuộc tài khoản hay organization nào. Một kho thử nghiệm cá nhân có thể do Alice quản lý; kho thanh toán của đội thường cần owner là organization để cấp quyền qua team và duy trì khi một người rời nhóm. Enterprise có thể quản lý nhiều organization tùy gói và cấu hình. Nhưng tên owner không cho biết bạn đang có quyền nào. Để kiểm tra, cần xem thiết lập truy cập của kho và chính sách của tổ chức, chứ không suy đoán chỉ từ URL.

**Purpose:**

Giải thích namespace/sở hữu khác quyền hiệu lực, chuẩn bị cho chương Access.

## Vai trò của chủ sở hữu, cộng tác viên và người đóng góp

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:57–06:08`

**Visual:**

Thu hai namespace alice/demo và orchid/payments thành thẻ owner, collaborator, contributor, reviewer; để trống biểu tượng quyền thao tác.

**Script:**

Một organization có nhiều người, nhưng 'đóng góp' và 'được cấp quyền' vẫn không phải cùng một nhãn.

**Purpose:**

Chuyển từ chủ sở hữu tổ chức sang vai trò từng người mà chưa suy diễn quyền thực tế.

### Scene 1 — Vai trò của chủ sở hữu, cộng tác viên và người đóng góp

**Time:** `06:08–07:12`

**Visual:**

Đặt thẻ Alice/An/ Bình/Mai lên các vùng owner, collaborator, contributor, reviewer; một người có thể nhận hai thẻ. Với contributor không cấp sẵn biểu tượng push.

**Script:**

Giả sử An đã từng gửi sửa lỗi qua fork. An là contributor, nhưng điều đó không tự cấp quyền đẩy vào repository gốc. Bình được yêu cầu review PR #57; lời mời này không biến Bình thành chủ sở hữu. Mai có thể chịu trách nhiệm quản trị kho, nhưng vẫn cần người hiểu nghiệp vụ kiểm tra phép làm tròn. Hãy tách điều một người đã làm, quyền người đó được cấp và trách nhiệm trong PR hiện tại. Nhờ vậy nhóm tránh nhầm lời mời review với phê duyệt hoặc quyền quản trị.

**Purpose:**

Phân biệt danh xưng trách nhiệm với quyền truy cập và hành động kiểm chứng được.

## Pull request: đề xuất thay đổi trước khi tiếp nhận vào nguồn chung

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:12–07:23`

**Visual:**

Giữ thẻ An và Bình ở hai đầu nhánh fix-rounding/main, nối chúng bằng mũi tên PR #57 chưa được phê duyệt.

**Script:**

Ta đã xác định người tham gia; làm thế nào để họ đánh giá cùng một thay đổi cụ thể?

**Purpose:**

Biến danh sách người tham gia thành một đề xuất cụ thể để nhóm cùng đánh giá.

### Scene 1 — Pull request: đề xuất thay đổi trước khi tiếp nhận vào nguồn chung

**Time:** `07:23–08:26`

**Visual:**

Hiện nhánh head fix-rounding ở trái, base main ở phải và thẻ PR #57 nối hai nhánh. Bên trong thẻ mở lần lượt Description, Files changed, Conversation, Reviews.

**Script:**

Pull request là đề xuất đưa thay đổi từ head sang base. Trong ví dụ của ta, PR #57 đưa sửa lỗi từ fix-rounding hướng tới main. Trang PR giúp reviewer đọc vì sao sửa, khác biệt tệp, trao đổi và quyết định review. Một PR có thể đang mở, là nháp, được yêu cầu sửa hoặc được merge; đừng suy từ việc tồn tại PR rằng mã đã đến đích. Kể cả một lượt Approve cũng khác với thao tác Merge và các điều kiện bảo vệ nhánh mà nhóm đã bật.

**Purpose:**

Xây mô hình PR head/base và dấu vết review so với kết quả merge.

## Quan hệ giữa repository, nhánh nguồn/đích, pull request và reviewer

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:26–08:37`

**Visual:**

Giữ PR #57 giữa hình, thêm Issue #42 phía trước và Release bằng mũi tên nét đứt phía sau khả năng merge vào main.

**Script:**

Các đối tượng riêng đã rõ. Cuối chương, ta nối chúng thành một luồng nhưng không nhập nhằng trạng thái.

**Purpose:**

Gắn pull request vào vòng đời truy vết và phân biệt merge với đóng Issue hay phát hành.

### Scene 1 — Quan hệ giữa repository, nhánh nguồn/đích, pull request và reviewer

**Time:** `08:37–09:43`

**Visual:**

Xây sơ đồ Issue #42 → head fix-rounding → PR #57 → reviewer/checks → main; đặt Release sau main bằng mũi tên nét đứt. Bên dưới ghi 'minh họa quan hệ, không phải kết quả đang chạy'.

**Script:**

Nhìn toàn cảnh: Issue #42 diễn đạt lỗi; nhánh công việc giữ phương án sửa; PR #57 xin đưa nó vào main; reviewer kiểm tra và quy tắc quyết định có cho merge hay không. Sau khi mã được tiếp nhận, nhóm có thể công bố Release vào một thời điểm khác. Mũi tên nét đứt cuối sơ đồ là lời nhắc quan trọng: merge không tự tạo Release và cũng không xác nhận đã triển khai. Sang chương sau, ta sẽ xem repository được sở hữu và hiển thị cho những ai trước khi mở đường đóng góp.

**Purpose:**

Tổng hợp danh sách đối tượng thành vòng đời chấp nhận, đồng thời mở cầu nối sang quyền sở hữu/visibility.
