---
video:
  url: ""
---

# Thảo luận và đánh giá pull request

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

## Mô tả PR và phần khác biệt tệp mã nguồn

<!-- VIDEO_SECTION -->

### Scene 1 — Mô tả PR và phần khác biệt tệp mã nguồn

**Time:** `00:00–00:55`

**Visual:**

Chia đôi tab Description và Files changed của PR timeout; đánh dấu đoạn sửa đúng phạm vi và tệp không liên quan.

Quan sát PR test được phép hoặc ảnh hướng dẫn Microsoft có ghi nguồn; không gán phiếu giả cho tài khoản thật.

**Script:**

Ta đọc PR như một đề xuất có thể kiểm tra: phần Description hứa sửa timeout, còn tab Files cho biết thực tế tệp nào đổi. Nếu xuất hiện chỉnh sửa ngoài phạm vi, reviewer phải hỏi vì sao; không bỏ phiếu chỉ dựa vào câu 'đã fix'. Khi tác giả push thêm commit hoặc nhánh đích đổi, diff có thể thay đổi. Vì thế mỗi nhận xét cần gắn với phiên bản mới nhất đang hiển thị.

**Purpose:**

Đối chiếu mô tả với thay đổi thật trong Files.

## Reviewer được chỉ định và bối cảnh cần đánh giá

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Giữ diff lỗi timeout, tô sáng thẻ reviewer Bob và Carla được policy yêu cầu.

**Script:**

Mô tả đã nêu mục đích; vậy ai sẽ kiểm tra diff sửa timeout?

**Purpose:**

Từ nội dung thay đổi chuyển sang ai phải tham gia đánh giá thay đổi đó.

### Scene 1 — Reviewer được chỉ định và bối cảnh cần đánh giá

**Time:** `01:05–01:59`

**Visual:**

Trên panel Reviewers, tô Bob được mời tùy chọn và Carla là reviewer bắt buộc với indicator riêng.

Quan sát PR test được phép hoặc ảnh hướng dẫn Microsoft có ghi nguồn; không gán phiếu giả cho tài khoản thật.

**Script:**

Trong panel Reviewers, hãy tách Bob được tác giả mời với Carla được nhánh đích yêu cầu theo policy. Số avatar không thay thế quyền và phiếu hợp lệ. Người review cần đủ quyền, hiểu phạm vi mã nguồn và biết mình đang xem nhánh đích nào. Nếu không có dữ liệu reviewer thật, dùng ảnh hướng dẫn chính thức và ghi nhãn minh họa, không đặt tên ai là người duyệt một PR thực.

**Purpose:**

Phân biệt reviewer tự chọn và reviewer bắt buộc.

## Bình luận theo dòng và luồng thảo luận trong PR

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:59–02:10`

**Visual:**

Ghim lời mời Bob cạnh dòng timeout, mở một discussion thread chưa giải quyết.

**Script:**

Được giao review là khởi đầu trao đổi; reviewer bình luận chính xác dòng nào?

**Purpose:**

Biến lời mời reviewer thành câu hỏi có địa chỉ và lịch sử rõ trong mã.

### Scene 1 — Bình luận theo dòng và luồng thảo luận trong PR

**Time:** `02:10–03:06`

**Visual:**

Chọn một dòng diff timeout, hiển thị inline comment và trạng thái thread Active/Resolved sát đoạn code.

Quan sát PR test được phép hoặc ảnh hướng dẫn Microsoft có ghi nguồn; không gán phiếu giả cho tài khoản thật.

**Script:**

Một nhận xét tốt nói rõ điều kiện lỗi, ví dụ timeout âm hoặc giá trị rỗng, rồi gắn với đúng dòng diff. Tác giả trả lời và sửa source branch; reviewer mở lại diff để xác nhận thay đổi. Đóng dấu Resolved chỉ nói thread đã được xử lý về mặt hội thoại, không bảo đảm logic đã đúng. Nếu policy yêu cầu resolve comments, thread đang Active có thể cản Complete dù các phiếu đều đồng ý.

**Purpose:**

Liên kết bình luận theo dòng với bằng chứng xử lý.

## Phiếu Approve so với Approve with suggestions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:06–03:16`

**Visual:**

Giữ thread trên màn hình rồi mở lựa chọn Approve và Approve with suggestions.

**Script:**

Một thread chưa phải phiếu review. Hai lựa chọn Approve khác nhau ra sao?

**Purpose:**

Phân tách một cuộc thảo luận với phiếu review được gửi chính thức.

### Scene 1 — Phiếu Approve so với Approve with suggestions

**Time:** `03:16–04:07`

**Visual:**

Mở menu vote trên PR, khoanh Approve và Approve with suggestions, giữ nút Complete ở vùng riêng.

Quan sát PR test được phép hoặc ảnh hướng dẫn Microsoft có ghi nguồn; không gán phiếu giả cho tài khoản thật.

**Script:**

Mở menu vote của Azure Repos và chỉ hai lựa chọn Approve, Approve with suggestions. Cả hai là hình thức đồng ý, nhưng lựa chọn thứ hai giữ lời khuyên nên cải thiện. Chúng không tự merge PR; việc phiếu có được tính còn chịu minimum-reviewer policy. Hãy ghi dấu thời điểm phiếu và số commit đang được review, không thay thế thao tác Complete bằng ảnh một nút xanh.

**Purpose:**

Phân biệt phiếu đồng ý và việc complete PR.

## Wait for author và Reject: ý nghĩa phản hồi đánh giá

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:07–04:17`

**Visual:**

Đổi các thẻ Approve thành Wait for author và Reject cạnh biểu tượng required reviewer của Carla.

**Script:**

Approve hay Approve with suggestions khác gì Wait for author và Reject?

**Purpose:**

Đối chiếu phản hồi tích cực với trạng thái đòi hỏi xử lý hoặc có thể chặn policy.

### Scene 1 — Wait for author và Reject: ý nghĩa phản hồi đánh giá

**Time:** `04:17–05:13`

**Visual:**

Đặt Wait for author và Reject bên cạnh badge required reviewer và trạng thái policy ở nhánh đích.

Quan sát PR test được phép hoặc ảnh hướng dẫn Microsoft có ghi nguồn; không gán phiếu giả cho tài khoản thật.

**Script:**

Carla chọn Wait for author vì trường hợp timeout chưa xử lý; đó là yêu cầu xem lại sau khi tác giả sửa. Reject thể hiện không chấp nhận thay đổi ở trạng thái hiện tại. Khi required reviewer đưa hai phiếu này, PR có thể bị chặn theo policy, còn ảnh hưởng của phiếu reviewer tùy chọn phải đọc thiết lập cụ thể. Ta không tự dựng bảng luật mới thay Microsoft; phải xem kết quả Policies thật.

**Purpose:**

Hiểu Wait/Reject theo required reviewer và policy.

## Sự khác biệt giữa review vote và hoàn tất pull request

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:13–05:23`

**Visual:**

Giữ bảng vote cạnh build policy thất bại và nút Complete chưa cho thao tác.

**Script:**

Được phê duyệt vẫn có thể chưa đủ policy hay quyền Complete.

**Purpose:**

Cho thấy đã nhận phiếu review khác với đủ điều kiện và quyền hoàn tất PR.

### Scene 1 — Sự khác biệt giữa review vote và hoàn tất pull request

**Time:** `05:23–06:17`

**Visual:**

Hiển thị phiếu Approved đã đủ ở một bên nhưng Build validation Failed và Complete chưa sẵn sàng ở bên còn lại.

Quan sát PR test được phép hoặc ảnh hướng dẫn Microsoft có ghi nguồn; không gán phiếu giả cho tài khoản thật.

**Script:**

Cho một PR đã đủ số phiếu Approve nhưng policy Build validation vẫn báo Failed. Review vote thuộc đánh giá người đọc; completion đòi chính sách và quyền trên target branch đều được thỏa. Tấm ảnh Approved một mình không thể là bằng chứng đã merge. Chúng ta ghi hai trạng thái cùng lúc, rồi hỏi nguyên nhân nào phải sửa trước; không mở bài giảng viết Azure Pipelines để khắc phục lỗi build ở đây.

**Purpose:**

Chứng minh Approved không bảo đảm merge khi check fail.

## Cập nhật của tác giả, xử lý phản hồi và review lại

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:17–06:28`

**Visual:**

Chuyển từ phiếu cũ sang commit mới, làm mới diff và chỉ báo reset vote.

**Script:**

Nếu tác giả sửa mã, phiếu cũ có còn áp dụng cho diff mới không?

**Purpose:**

Giải thích vì sao mã cập nhật có thể cần đánh giá lại theo cấu hình.

### Scene 1 — Cập nhật của tác giả, xử lý phản hồi và review lại

**Time:** `06:28–07:24`

**Visual:**

Minh họa tác giả push commit thứ hai, tab Files thay đổi và tùy chọn reset votes của minimum-reviewer policy.

Quan sát PR test được phép hoặc ảnh hướng dẫn Microsoft có ghi nguồn; không gán phiếu giả cho tài khoản thật.

**Script:**

Tác giả push thêm commit lên source branch của PR đang Active; phần Files thay đổi và checks có thể chạy lại. Phiếu reviewer có thể được giữ, reset hoặc yêu cầu xem lại tùy tùy chọn minimum-reviewer policy. Vì vậy đừng dạy câu 'mọi push đều xoá phiếu' hay 'phiếu trước luôn còn hiệu lực'. Trước khi Complete, mở lại danh sách reviewer và policy state hiện tại rồi đề nghị người đánh giá xác nhận thay đổi mới.

**Purpose:**

Minh họa việc reset phiếu phụ thuộc cấu hình, không suy đoán.

## Bằng chứng reviewer, thảo luận và trạng thái phiếu đánh giá

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:24–07:35`

**Visual:**

Thu PR cập nhật thành checklist reviewer, votes, threads, commits và kết quả policy hiện hành.

**Script:**

Trước khi xem branch policies, hãy chốt phiếu, thread và commit đang có hiệu lực.

**Purpose:**

Tạo hồ sơ chứng minh lượt đánh giá hiện tại áp dụng cho phiên bản nào.

### Scene 1 — Bằng chứng reviewer, thảo luận và trạng thái phiếu đánh giá

**Time:** `07:35–08:34`

**Visual:**

Tạo checklist hình ảnh reviewer names, required labels, votes, active threads, source update và policy results.

Quan sát PR test được phép hoặc ảnh hướng dẫn Microsoft có ghi nguồn; không gán phiếu giả cho tài khoản thật.

**Script:**

Hãy cùng đọc lại PR từ đầu bằng các bằng chứng: reviewer nào được gắn Required, phiếu hiện tại là gì, thread nào còn mở, source vừa được cập nhật chưa và Policies đang đỏ ở đâu. Một nhãn Approved màu xanh chỉ cho ta một mảnh của câu chuyện. Ghi lại trạng thái cùng thời điểm quan sát; sau khi tác giả sửa thêm commit, ảnh cũ không còn đủ độ tin cậy. Chương sau sẽ tập trung vào từng điều kiện branch policy.

**Purpose:**

Thiết lập kiểm chứng phiếu reviewer, thread và source update.
