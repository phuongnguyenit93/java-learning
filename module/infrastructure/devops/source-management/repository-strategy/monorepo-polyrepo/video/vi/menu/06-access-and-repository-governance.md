---
video:
  url: ""
---

# Phân quyền truy cập và quản trị repository

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

## Ranh giới quyền đọc và ghi tại mức repository

<!-- VIDEO_SECTION -->

### Scene 1 — Ranh giới quyền đọc và ghi tại mức repository

**Time:** `00:00–01:05`

**Visual:**

Vẽ source Web và Fraud trong một monorepo có vendor Read; chuyển Fraud sang repo riêng và hiển thị hai access lists khác nhau.

**Script:**

Khi ai đó có quyền đọc repository chung, họ thường có thể clone các thư mục mã bên trong, kể cả vùng không thuộc team của họ. Ví dụ nhà thầu cần Web nhưng không được xem Fraud: folder `/fraud` trong cùng repo broadly readable không tự tạo cách ly. Polyrepo có thể giúp cấp quyền đọc riêng cho Fraud, nhưng hệ thống host, token CI và việc chia sẻ artifacts cũng phải duy trì ranh giới đó. Hãy yêu cầu bằng chứng access trên kho thực, không xem diagram là một permission test thành công.

**Purpose:**

Người học phải nhận biết được Quyền clone ở cấp repo là ranh giới thực tế từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Code owner và chính sách review theo khu vực mã nguồn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:05–01:28`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Quyền clone ở cấp repo là ranh giới thực tế"; mở khung phải cho "Path CODEOWNERS là điều phối reviewer, không phải ACL đọc", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Quyền clone ở cấp repo là ranh giới thực tế. Nhưng chưa nên kết luận khi chưa kiểm tra Path CODEOWNERS là điều phối reviewer, không phải ACL đọc. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Path CODEOWNERS là điều phối reviewer, không phải ACL đọc, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Code owner và chính sách review theo khu vực mã nguồn

**Time:** `01:28–02:27`

**Visual:**

PR đụng `/security/keys` và `/web/`; hai CODEOWNERS patterns gọi Security/Web; bên cạnh role có thể đọc cả repo.

**Script:**

Trong monorepo, CODEOWNERS có thể chỉ định reviewer cho từng path để PR chạm `/security/keys` phải được người Security nhìn thấy. Tính bắt buộc của approval còn phụ thuộc branch rules, plan và quyền reviewer trong nền tảng host. Người có quyền clone kho chung vẫn có thể đọc file ở vùng đó, dù không được approve. Vì thế sơ đồ có hai đường rõ ràng: một đường routing review, một đường truy cập nguồn. Không đánh đồng chúng khi thiết kế bảo mật.

**Purpose:**

Người học phải nhận biết được Path CODEOWNERS là điều phối reviewer, không phải ACL đọc từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Review theo đường dẫn không thay thế cô lập quyền truy cập đọc

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:27–02:50`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Path CODEOWNERS là điều phối reviewer, không phải ACL đọc"; mở khung phải cho "Approval bảo vệ merge, không bảo vệ bí mật source", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Path CODEOWNERS là điều phối reviewer, không phải ACL đọc. Nhưng chưa nên kết luận khi chưa kiểm tra Approval bảo vệ merge, không bảo vệ bí mật source. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Approval bảo vệ merge, không bảo vệ bí mật source, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Review theo đường dẫn không thay thế cô lập quyền truy cập đọc

**Time:** `02:50–03:52`

**Visual:**

Chia hai hành động: vendor clones whole repo; vendor submits Fraud PR needing Security approval. Hành động đọc vẫn có thể thành công dù review bị chặn.

**Script:**

Hãy theo dõi cùng nhà thầu qua hai câu hỏi. Khi clone monorepo, họ có thể thấy thuật toán Fraud nếu quyền đọc đã được cấp; CODEOWNERS không ẩn file. Khi đề xuất sửa Fraud, rule có thể yêu cầu Security approve trước merge. Đó là kiểm soát thay đổi, không phải bảo mật nội dung. Nếu yêu cầu pháp lý cấm xem source Fraud, không được bù bằng hai reviewer. Phải có access boundary thật ở repository hay hệ thống có cơ chế cô lập được chứng minh.

**Purpose:**

Người học phải nhận biết được Approval bảo vệ merge, không bảo vệ bí mật source từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Ràng buộc bảo mật và pháp lý khi quyết định tách repository

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:52–04:15`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Approval bảo vệ merge, không bảo vệ bí mật source"; mở khung phải cho "Hard constraint về pháp lý ưu tiên hơn tiện lợi", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Approval bảo vệ merge, không bảo vệ bí mật source. Nhưng chưa nên kết luận khi chưa kiểm tra Hard constraint về pháp lý ưu tiên hơn tiện lợi. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Hard constraint về pháp lý ưu tiên hơn tiện lợi, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Ràng buộc bảo mật và pháp lý khi quyết định tách repository

**Time:** `04:15–05:23`

**Visual:**

Bảng Compliance: partner-licensed source, regulated Fraud, export control; đánh dấu read boundary bắt buộc, CI token và artifacts cần rà lại.

**Script:**

Một repository có thể thuận tiện cho thay đổi xuyên team, nhưng nếu partner agreement chỉ cho một nhóm xem source Fraud, tiện lợi không phải lý do hợp lệ để chia sẻ rộng. Polyrepo có thể cấp quyền riêng, với điều kiện quyền token CI, cache và artifact không rò dữ liệu sang dự án khác. Trước khi quyết định, hãy hỏi bộ phận security/legal phạm vi cấm đọc, lưu hồ sơ kiểm chứng và chọn topology đáp ứng bắt buộc trước. Không đặt hard constraint vào bảng chấm điểm như một sở thích có thể bị điểm thấp lấn át.

**Purpose:**

Người học phải nhận biết được Hard constraint về pháp lý ưu tiên hơn tiện lợi từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Nhất quán quản trị và quy tắc review qua nhiều repository

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:23–05:45`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Hard constraint về pháp lý ưu tiên hơn tiện lợi"; mở khung phải cho "Mười hai repositories cần baseline review tối thiểu", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Hard constraint về pháp lý ưu tiên hơn tiện lợi. Nhưng chưa nên kết luận khi chưa kiểm tra Mười hai repositories cần baseline review tối thiểu. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Mười hai repositories cần baseline review tối thiểu, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Nhất quán quản trị và quy tắc review qua nhiều repository

**Time:** `05:45–06:50`

**Visual:**

Lưới 12 repo, 11 có protected branch/required review và một thiếu; không gán lỗi thật, đánh dấu ô cần audit.

**Script:**

Polyrepo cho phép mỗi team chọn chính sách phù hợp, nhưng cũng làm số điểm cần quản trị tăng. Hãy tưởng tượng 12 kho đều cần review bảo mật tối thiểu, trong đó một kho bị bỏ sót khi cập nhật rule. Một thay đổi nhạy cảm qua kho yếu có thể không đi qua chuẩn chung. Nhóm cần catalog repo, owner, quyền, bộ rule cần thiết và thời gian kiểm tra định kỳ. Mục tiêu không phải mọi repo giống hệt nhau, mà các ngoại lệ phải có lý do và người chịu trách nhiệm.

**Purpose:**

Người học phải nhận biết được Mười hai repositories cần baseline review tối thiểu từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Rủi ro thiếu code ownership trong monorepo

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:50–07:12`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Mười hai repositories cần baseline review tối thiểu"; mở khung phải cho "Source chung không có owner thành source không ai nhận", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Mười hai repositories cần baseline review tối thiểu. Nhưng chưa nên kết luận khi chưa kiểm tra Source chung không có owner thành source không ai nhận. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Source chung không có owner thành source không ai nhận, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Rủi ro thiếu code ownership trong monorepo

**Time:** `07:12–08:18`

**Visual:**

Cây monorepo `/libs/contracts` đỏ vì thiếu owner, Web/API đổi qua lại; bên phải primary/backup owner được gán sau audit.

**Script:**

Một repository lớn cho mọi người nhìn thấy mã không tự tạo trách nhiệm. Nếu `/libs/contracts` không có owner rõ, Web có thể sửa vì cần gấp, API sửa lại vì yêu cầu khác, cuối cùng không ai kiểm tra compatibility toàn bộ. Ta đánh dấu lỗ hổng trong ownership map và tìm người có chuyên môn để phụ trách schema lẫn consumer coordination. CODEOWNERS hỗ trợ gọi reviewer khi cấu hình đúng, nhưng vẫn cần người thực sự chấp nhận trách nhiệm. Bằng chứng là PR được phân công đúng và incident không bị đùn đẩy.

**Purpose:**

Người học phải nhận biết được Source chung không có owner thành source không ai nhận từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Policy drift và sự không nhất quán giữa các polyrepo

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:18–08:41`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Source chung không có owner thành source không ai nhận"; mở khung phải cho "Policy drift cần so sánh baseline và ngoại lệ", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Source chung không có owner thành source không ai nhận. Nhưng chưa nên kết luận khi chưa kiểm tra Policy drift cần so sánh baseline và ngoại lệ. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Policy drift cần so sánh baseline và ngoại lệ, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Policy drift và sự không nhất quán giữa các polyrepo

**Time:** `08:41–09:45`

**Visual:**

Bảng Repo A checks required, B lacks backup, C stale access, D justified exception; gắn ngày audit và người chịu trách nhiệm.

**Script:**

Khi các repository phát triển độc lập, branch rule, reviewer và quyền có thể lệch dần mà không ai chủ ý. Nhưng không phải mọi khác biệt đều sai; một kho chỉ chứa tài liệu có thể có rủi ro khác kho xử lý Fraud. Hãy so từng repo với baseline đã phê duyệt, ghi ngoại lệ và ngày xem lại. Nếu C còn quyền của người đã rời nhóm, đó là drift cần khắc phục. Nếu D có ngoại lệ hợp lệ, đừng ép giống A chỉ vì muốn biểu đồ đồng đều.

**Purpose:**

Người học phải nhận biết được Policy drift cần so sánh baseline và ngoại lệ từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Ma trận đánh giá topology với yêu cầu kiểm soát truy cập

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:45–10:08`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Policy drift cần so sánh baseline và ngoại lệ"; mở khung phải cho "Bảng quyết định Access không chỉ là điểm cộng trừ", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Policy drift cần so sánh baseline và ngoại lệ. Nhưng chưa nên kết luận khi chưa kiểm tra Bảng quyết định Access không chỉ là điểm cộng trừ. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Bảng quyết định Access không chỉ là điểm cộng trừ, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Ma trận đánh giá topology với yêu cầu kiểm soát truy cập

**Time:** `10:08–11:14`

**Visual:**

Ma trận monorepo/polyrepo gồm read audience, write/merge, review owner, source sensitivity, coordination. Dòng Fraud restricted đánh dấu hard constraint đỏ.

**Script:**

Để chọn topology dưới ràng buộc quản trị, hãy lập bảng ai được đọc, ai có thể sửa và merge, người review nào bắt buộc, dữ liệu nguồn nhạy cảm ra sao và chi phí giữ nhiều policies. Nếu Fraud phải cô lập khỏi vendor nhưng Web được chia sẻ, một monorepo broadly readable không đạt điều kiện, dù giảm được nhiều PR. Tách Fraud có thể hợp lý và phải có kế hoạch contract giữa hai phần. Sau khi loại những phương án vi phạm hard constraint, ta mới cân nhắc tốc độ cộng tác và tooling.

**Purpose:**

Người học phải nhận biết được Bảng quyết định Access không chỉ là điểm cộng trừ từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.
