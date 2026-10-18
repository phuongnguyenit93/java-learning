---
video:
  url: ""
---

# Bảo vệ nhánh và kiểm tra trước hợp nhất

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

## Mục đích bảo vệ các nhánh quan trọng

<!-- VIDEO_SECTION -->

### Scene 1 — Mục đích bảo vệ các nhánh quan trọng

**Time:** `00:00–01:35`

**Visual:**

Màn hình nhánh `invoice-api/main`; chia hai mũi tên `Direct push` và `Merge PR` rồi đặt `Branch restrictions` ở quyền thao tác, `Merge checks` ở điều kiện đề xuất.

**Script:**

Orchid dùng `main` làm nhánh nguồn chung nên không muốn An vô tình ghi đè trực tiếp hoặc hợp nhất một PR chưa kiểm tra. Bitbucket Cloud có hai công cụ bổ sung: branch restrictions giới hạn những người được làm thao tác nhất định; merge checks đánh giá trạng thái của đề xuất trước khi hợp nhất. Đây không phải hai cách gọi cùng một nút. Câu hỏi đầu tiên là người này có được cập nhật nhánh không; câu hỏi thứ hai là PR đã đạt tiêu chí nhóm đặt ra chưa. Mọi ví dụ sau phải giữ hai dòng kiểm tra riêng.

**Purpose:**

Mở mô hình who-can-act vs what-conditions-passed cho nhánh quan trọng.



## Quyền ghi lên nhánh so với quyền hợp nhất

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:35–01:47`

**Visual:**

Trên main tách mũi tên direct push của An và PR merge của Mai, đặt hai bảng write access và merge access cạnh nhau.

**Script:**

Mục tiêu là bảo vệ `main`. Quyền đẩy mã trực tiếp và quyền hợp nhất qua PR khác nhau ở đâu?

**Purpose:**

Cho thấy quyền viết vào nhánh khác với quyền chấp nhận PR vào nhánh đó.

### Scene 2 — Quyền ghi lên nhánh so với quyền hợp nhất

**Time:** `01:47–03:22`

**Visual:**

Một bảng có An quyền repository Write nhưng hai cột `Push directly to main` và `Merge PR into main` khác trạng thái; cạnh đó là setting branch restrictions demo.

**Script:**

Một thành viên có Write ở repository vẫn có thể bị hạn chế ghi trực tiếp vào `main` hoặc quyền merge PR, tùy quy tắc nhánh. Ở Orchid, An cần đẩy lên nhánh công việc nhưng không được tự thay đổi nguồn chung. Mai có thể cấu hình người được cập nhật trực tiếp khác với người được phép merge đề xuất đã qua review. Vì vậy lỗi 'không được cập nhật nhánh' không thể giải thích chỉ bằng nhóm repo. Hãy đối chiếu thao tác thực tế với branch restriction có hiệu lực, không mở mọi quyền để thử.

**Purpose:**

Chứng minh quyền push và merge có thể tách biệt, tránh suy diễn repo Write là vô điều kiện.



## Quy tắc cấp project/repository và mẫu tên nhánh

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:34`

**Visual:**

Giữ hai quyền push/merge; mở Project Billing bao quanh invoice-api và billing-docs với pattern `main` và `release/*`.

**Script:**

Hai loại quyền khác nhau rồi, nhưng cùng một quy tắc có thể áp dụng đến bao nhiêu nhánh?

**Purpose:**

Nêu phạm vi áp dụng project-level rule và cách pattern có thể chạm nhiều nhánh/repository.

### Scene 3 — Quy tắc cấp project/repository và mẫu tên nhánh

**Time:** `03:34–05:09`

**Visual:**

Cây project Billing có `invoice-api` và `billing-docs`; highlight quy tắc cấp project cho `main`, thêm mẫu `release/*` và hai tên nhánh có/không khớp.

**Script:**

Một branch restriction có thể đặt ở repo, hoặc ở project để các repo liên quan có chung cách quản trị. Với mẫu tên như `release/*`, quy tắc nhắm tới những nhánh khớp mẫu, không phải mọi nhánh có từ 'release' bất kỳ. Tại Orchid, quy tắc project cho `main` có thể ảnh hưởng đồng thời `invoice-api` và `billing-docs`, khiến một nhóm tưởng chỉ cấu hình API nhưng tài liệu cũng bị giới hạn. Khi quay, chỉ đánh dấu mẫu và phạm vi đã kiểm tra, không tuyên bố quy tắc luôn ưu tiên hay ghi đè nhau theo thứ tự chưa được chứng minh.

**Purpose:**

Thể hiện tính lan truyền project/repository và sự cần thiết kiểm tra branch patterns.



## Merge Checks: Các điều kiện kiểm tra trước khi hợp nhất

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:09–05:21`

**Visual:**

Thu nhỏ bảng permission; mở checklist PR `Approvals, Task, Changes requested, Build status`, để kết quả chưa đủ khi task chưa giải quyết.

**Script:**

Giới hạn người thao tác xong, chúng ta xét chất lượng đề xuất trước merge.

**Purpose:**

Chuyển từ ai có quyền merge sang bằng chứng chất lượng PR cần được kiểm tra trước merge.

### Scene 4 — Merge Checks: Các điều kiện kiểm tra trước khi hợp nhất

**Time:** `05:21–06:56`

**Visual:**

Trên PR `BILL-142`, bảng checks: `Binh approves`, `No unresolved tasks`, `No Changes requested`, `Build status`; đánh dấu task hóa đơn âm còn mở.

**Script:**

Merge check nhìn trạng thái PR: đủ approvals chưa, có task chưa giải quyết không, reviewer còn yêu cầu thay đổi không, hoặc build status theo tích hợp đã báo gì. Orchid muốn Bình xem thuế và task hóa đơn số âm được đóng bằng bằng chứng. Nếu task còn mở, một check có thể báo chưa đạt. Nhưng check không tự đọc được đúng/sai của công thức nghiệp vụ; nó chỉ đánh giá điều kiện đã cấu hình. Hãy chụp trạng thái check thực ở PR và tách nó khỏi review comment hay quyền repository.

**Purpose:**

Định nghĩa merge check như điều kiện quan sát được, không phải bộ phận tự chứng minh correctness.



## Cảnh báo merge check và chặn hợp nhất trên Premium

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:56–07:08`

**Visual:**

Giữ task `Test negative invoice` mở; đặt hai cột Standard advisory và Premium enforced, chú thích `Prevent merge` chỉ khi bật.

**Script:**

Một check chưa đạt có luôn làm nút Merge bị khóa không?

**Purpose:**

Nêu đúng ranh giới cảnh báo và cấm merge trong Bitbucket Cloud, tránh gán tính năng Premium cho Standard.

### Scene 5 — Cảnh báo merge check và chặn hợp nhất trên Premium

**Time:** `07:08–08:43`

**Visual:**

So sánh hai trạng thái UI có cùng check unresolved: bên trái `Free/Standard: warning` nút merge có thể khả dụng; bên phải `Premium + Prevent a merge ... enabled: blocked`. Gắn điều kiện authorization độc lập.

**Script:**

Đây là khác biệt quan trọng của Bitbucket Cloud. Với Free hoặc Standard, các merge checks thông thường có thể cảnh báo rằng task hay approval chưa đạt nhưng người đã có quyền merge vẫn thực hiện được nếu không có giới hạn khác. Premium cung cấp lựa chọn `Prevent a merge with unresolved merge checks`, khi bật và áp dụng đúng, hệ thống chặn merge còn thiếu check. Mua Premium không tự bật chế độ này; cảnh báo cũng không nhất thiết là lệnh cấm. Khi quay demo, ghi rõ gói và toggle cấu hình, không đưa ra kết luận từ màu biểu tượng đơn lẻ.

**Purpose:**

Phân biệt warning với enforced blocking theo tier và cấu hình, không hứa sai về Premium.



## Tình huống: cùng một merge check chưa đạt trên gói thường và Premium

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:43–08:55`

**Visual:**

Giữ cùng checklist và cùng actor; mô phỏng nhấn Merge dưới Standard cảnh báo so với Premium bật enforcement nhưng không tạo kết quả server thật.

**Script:**

Hãy giữ nguyên PR và task để so kết quả trên hai cấu hình thật khác nhau.

**Purpose:**

Chứng minh so sánh phải giữ nguyên PR, quyền và check rồi mới kết luận khác biệt do cấu hình.

### Scene 6 — Tình huống: cùng một merge check chưa đạt trên gói thường và Premium

**Time:** `08:55–10:30`

**Visual:**

Kẻ bảng hai hàng cho cùng `BILL-142` open task: `Standard, otherwise authorized → advisory warning / may merge` và `Premium + enforce → blocked`; ô kiểm quyền tách riêng.

**Script:**

Tình huống này dùng một PR, một task chưa hoàn tất và cùng check 'task phải được giải quyết'. Trên Standard, nếu người thao tác có quyền và không vướng restriction khác, Bitbucket có thể hiện cảnh báo nhưng vẫn cho merge. Nhóm khi đó cần quy ước con người không bỏ qua cảnh báo quan trọng. Trên Premium, khi Mai bật chế độ chặn unresolved checks và check áp dụng, nút merge bị hệ thống chặn cho tới khi task được xử lý. Nếu demo không có tài khoản ở cả hai gói, dùng ảnh đối chiếu từ tài liệu được ghi rõ, không giả mạo kết quả live.

**Purpose:**

Đưa ra mô hình đối chiếu cùng điều kiện, kết quả phụ thuộc tier và toggle, phân biệt minh họa với quan sát trực tiếp.



## Quy tắc chồng lấn và cách tìm nguyên nhân bị chặn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:30–10:42`

**Visual:**

Từ hai kết quả giả định mở cây chẩn đoán `Actor → branch pattern → project/repo grants → check enforcement → task`, phóng to đúng thông báo.

**Script:**

Nếu kết quả chặn không khớp kỳ vọng, nguyên nhân có thể ở nhiều quy tắc chồng nhau.

**Purpose:**

Hướng dẫn phân loại denied merge theo quyền/rule/check trước khi đề xuất sửa cấu hình.

### Scene 7 — Quy tắc chồng lấn và cách tìm nguyên nhân bị chặn

**Time:** `10:42–12:17`

**Visual:**

PR báo không merge được, phủ các vòng tròn `Workspace/Project/Repo rights`, `Branch restriction`, `Merge check`, `Review task`; bắt đầu ở Actor+target+exact message.

**Script:**

Khi An không merge được, đừng chỉ nhìn dấu approval rồi nói Bitbucket lỗi. Có thể An thiếu quyền repo hoặc project, một branch restriction nhắm `main`, một check Premium còn thiếu, hay task chưa được giải quyết. Mai nên ghi người thao tác, nhánh đích, thời điểm và thông báo nguyên văn, sau đó kiểm tra các quy tắc khớp trên project và repository. Không tắt hết restrictions hoặc cấp Admin để 'chữa' nhanh; cách ấy xóa đi điều kiện kiểm soát đang cần chứng minh. Cuối cùng hãy tách thông báo cảnh báo khỏi từ chối thực sự.

**Purpose:**

Dạy trình tự chẩn đoán branch policy an toàn theo evidence, chuẩn bị cho workflow tổng hợp.
