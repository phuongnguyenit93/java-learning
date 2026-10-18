---
video:
  url: ""
---

# Nền tảng cộng tác mã nguồn trên Bitbucket Cloud

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

## Bitbucket Cloud: Khái niệm và vai trò trong cộng tác mã nguồn

<!-- VIDEO_SECTION -->

### Scene 1 — Bitbucket Cloud: Khái niệm và vai trò trong cộng tác mã nguồn

**Time:** `00:00–01:30`

**Visual:**

Dựng **sơ đồ minh họa, không phải giao diện repository thật**, theo thứ bậc workspace `orchid-team` → project `Billing` → repository `invoice-api` (không trình bày chuỗi này như URL). Đặt bên cạnh đồ thị commit Git và hai thẻ `Đã push`/`Đã chấp nhận`; không gắn trạng thái xanh hay kết quả duyệt giả.

**Script:**

An đổi thuế trong `invoice-api`; commit Git ghi được thay đổi, nhưng đồng đội vẫn cần nơi kiểm tra trước khi nhận. Bitbucket Cloud là dịch vụ Atlassian lưu trữ repository Git và các hoạt động cộng tác: phân quyền, pull request, thảo luận và quy tắc merge. Trên màn hình, PR còn mở dù mã đã được chia sẻ. Vì thế đừng kết luận ứng dụng đã đổi chỉ từ một push: chúng ta phải kiểm tra nhánh đích và dấu vết đánh giá.

**Purpose:**

Đặt vai trò Bitbucket Cloud như nơi cộng tác có quản trị, tách khỏi cơ chế Git và việc triển khai.



## Nhu cầu lưu trữ và cộng tác mã nguồn trong nhóm

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:30–01:42`

**Visual:**

Đặt hai ZIP của An/Bình chồng nhau bên trái; kéo ảnh lên thẻ PR diff của `invoice-api`, để riêng Git history và reviewer activity.

**Script:**

Chúng ta đã biết Bitbucket Cloud hỗ trợ cộng tác. Nếu nhóm chỉ chuyền nhau các bản sao thư mục và không có dấu vết đánh giá chung thì điều gì dễ sai?

**Purpose:**

Giải thích vì sao cộng tác có lịch sử và review dễ đối chiếu hơn gửi bản ZIP thủ công.

### Scene 2 — Nhu cầu lưu trữ và cộng tác mã nguồn trong nhóm

**Time:** `01:42–03:12`

**Visual:**

Đối chiếu `invoice-final.zip` và `invoice-final-v2.zip` của hai người; chuyển dần thành một repository và một trang pull request có diff được gắn người review.

**Script:**

Nếu An gửi bản nén trong chat còn Bình gửi bản nén khác, người nhận phải đoán tệp nào bao gồm cả thuế mới và sửa làm tròn. Một kho nguồn chung cho phép truy xuất lịch sử; PR cho phép trình bày khác biệt, yêu cầu đánh giá và lưu phản hồi ngay cạnh mã. Giá trị không phải dung lượng lưu trữ lớn hơn, mà là nhóm có thể lần ra nguồn nào đã được đề xuất và vì sao phiên bản cuối được chọn.

**Purpose:**

Giải thích vì sao nhóm cần một điểm cộng tác được quản lý thay vì trao đổi thư mục thủ công.



## Git và Bitbucket Cloud: Phân định trách nhiệm

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:12–03:24`

**Visual:**

Giữ commit graph `Git` phía dưới và PR `Bitbucket` phía trên; làm mờ tầng host để chỉ commit local còn được ghi dù chưa mở trang PR.

**Script:**

Có Bitbucket để cộng tác rồi, hãy đối chiếu rõ phần việc của nó với Git.

**Purpose:**

Chỉ rõ commit là dữ liệu Git, còn review và settings chỉ xuất hiện trong lớp Bitbucket.

### Scene 3 — Git và Bitbucket Cloud: Phân định trách nhiệm

**Time:** `03:24–04:54`

**Visual:**

Hai tầng cùng một hình: dưới là `git commit`, `git fetch`; trên là Bitbucket `Pull requests`, `Reviewers`, `Repository settings`. Chiếu nhãn `Cơ chế` và `Quản trị`.

**Script:**

Git lưu revision, nhánh và dữ liệu lịch sử, kể cả khi An làm việc local. Bitbucket Cloud đặt các trang và chính sách cộng tác quanh repository Git ấy. Một commit có thể tồn tại mà chưa có PR. Ngược lại, một phiếu review là dữ liệu của nền tảng, không phải đối tượng commit Git. Khi phân tích sự cố, hãy hỏi: đang cần chứng minh nội dung lịch sử hay quyết định tiếp nhận? Chọn đúng tầng thì mới tìm được bằng chứng đúng.

**Purpose:**

Ngăn nhầm Git commit với phê duyệt trên hệ thống hosting.



## Cấu trúc workspace, project và repository trên Bitbucket Cloud

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:54–05:06`

**Visual:**

Từ hai tầng Git/Bitbucket kéo breadcrumb `orchid-team → Billing → invoice-api`, mở thêm `billing-docs` với Git graph riêng.

**Script:**

Hai tầng đã rõ. Vậy trong Bitbucket, mã được đặt vào những phạm vi nào?

**Purpose:**

Chuyển từ việc host repository sang phân biệt workspace, project và từng repo không chung lịch sử.

### Scene 4 — Cấu trúc workspace, project và repository trên Bitbucket Cloud

**Time:** `05:06–06:36`

**Visual:**

Mở breadcrumb minh họa `orchid-team → Billing → invoice-api`; kế đó mở repository thứ hai `billing-docs` trong cùng project, giữ hai commit graph riêng.

**Script:**

Workspace là không gian tổ chức cộng tác; project nhóm những repository có liên quan; repository lưu nội dung và lịch sử Git riêng. Orchid có workspace `orchid-team`, project `Billing` và hai repo `invoice-api` cùng `billing-docs`. Chung project không biến chúng thành một lịch sử Git. Khi người học xem giao diện Bitbucket Cloud, nên đi lần lượt từ workspace tới project và repo, ghi lại URL/đường dẫn để không cấu hình nhầm quyền ở nơi khác.

**Purpose:**

Xác lập quan hệ workspace–project–repository và ranh giới lịch sử của từng kho.



## Thành viên, quản trị viên và kho mã dùng chung

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:36–06:48`

**Visual:**

Giữ hai repos thuộc Billing; gắn An/Bình/Mai vào bảng `Contribute / Review / Admin` và bỏ trống mọi quyền chưa được kiểm chứng.

**Script:**

Có cấu trúc rồi, câu hỏi kế tiếp là ai được thực hiện từng thao tác trong đó.

**Purpose:**

Biến sơ đồ nơi đặt mã thành câu hỏi về quyền và trách nhiệm thực tế của từng thành viên.

### Scene 5 — Thành viên, quản trị viên và kho mã dùng chung

**Time:** `06:48–08:18`

**Visual:**

Hiện ba avatar An, Bình, Mai cạnh repository với cột `Contribute`, `Review`, `Administration`; không tô cùng một quyền cho cả ba.

**Script:**

An là người sửa thuế, Bình đánh giá phép làm tròn, Mai quản trị quyền và quy tắc của khu vực mình chịu trách nhiệm. Một người có thể giữ nhiều vai trò, nhưng được giao review không tự động cấp quyền Admin, và quản trị viên cũng không tự trở thành người hiểu nhất mọi nghiệp vụ. Trên giao diện thật phải kiểm tra quyền hiệu lực theo phạm vi workspace, project, repository, thay vì suy luận từ chức danh. Ghi riêng ai cần đọc, góp mã và cấu hình chính sách.

**Purpose:**

Làm rõ sự phân công người đóng góp, người đánh giá và quản trị viên.



## Pull Request: Khái niệm, tác giả, reviewer và nhánh đích

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:18–08:30`

**Visual:**

Từ bảng actor mở PR `BILL-142`, chỉ rõ `fix/tax` source, `main` destination và ô `Reviewers: Binh` chưa Approve.

**Script:**

Nhóm đã có tác giả và reviewer; họ gặp nhau ở đối tượng cộng tác nào?

**Purpose:**

Đưa trách nhiệm của người vào đối tượng PR cụ thể và phân biệt lời mời review với approval.

### Scene 6 — Pull Request: Khái niệm, tác giả, reviewer và nhánh đích

**Time:** `08:30–10:00`

**Visual:**

Hiện PR thử nghiệm `BILL-142 Update invoice tax`, đánh dấu source `fix/tax` và destination `main`; phóng to tiêu đề, diff, danh sách reviewer.

**Script:**

Một pull request là lời đề nghị đưa thay đổi từ source sang destination, không phải một commit mới có tên PR. An mở PR để Bình xem logic thuế và các hóa đơn số lẻ. Tại đây ta thấy mục đích, phần khác biệt và các phản hồi. Mời Bình làm reviewer không có nghĩa Bình đã phê duyệt. Và ngay cả khi PR có approval, merge còn tùy quyền và điều kiện đang áp dụng. Người xem hãy chỉ chính xác hai đầu source/destination trước khi đọc phần khác biệt.

**Purpose:**

Giới thiệu PR qua bằng chứng có thể kiểm tra và ngăn đồng nhất lời mời reviewer với approval.



## Hành trình thay đổi mã nguồn trong Bitbucket Cloud

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:00–10:12`

**Visual:**

Giữ PR BILL-142 mở, mở timeline `Edit → Share → Review/task → Merge main` và để trống kết quả nếu chưa có merge.

**Script:**

Sau trang PR, hãy theo toàn bộ đường đi cho đến khi nguồn chung thật sự thay đổi.

**Purpose:**

Tổng hợp PR thành quy trình có các trạng thái riêng, nhấn mạnh proposal không đồng nghĩa accepted source.

### Scene 7 — Hành trình thay đổi mã nguồn trong Bitbucket Cloud

**Time:** `10:12–11:42`

**Visual:**

Dựng timeline quyền repo → nhánh công việc → PR → bình luận của Bình → task → điều kiện merge → `main`; mỗi mốc có thẻ bằng chứng riêng.

**Script:**

Hành trình của An bắt đầu bằng quyền truy cập `invoice-api`, sau đó là một bản sửa Git, PR hướng vào `main`, phản hồi của Bình, task kiểm thử làm tròn và các check áp dụng. Chỉ khi người có quyền merge đưa thay đổi vào nhánh đích thì nguồn chung mới cập nhật. Đèn cảnh báo check trên Free hoặc Standard không nhất thiết chặn merge; cơ chế chặn Premium phải được bật và cấu hình đúng. Tạm dừng ở đây: bài tiếp theo sẽ phân biệt quyền ở từng tầng trước khi chúng ta thử review.

**Purpose:**

Tổng hợp quy trình nguồn được chấp nhận và tạo câu hỏi kế tiếp về quyền hiệu lực.
