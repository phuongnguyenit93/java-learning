---
video:
  url: ""
---

# Đề xuất thay đổi qua pull request

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

## Pull Request như một đề xuất thay đổi có thể đánh giá

<!-- VIDEO_SECTION -->

### Scene 1 — Pull Request như một đề xuất thay đổi có thể đánh giá

**Time:** `00:00–01:30`

**Visual:**

Mở PR demo `BILL-142 Update invoice tax`; giữ nhánh `fix/BILL-142-tax` và `main` thành hai dải màu, đặt nhãn `Open proposal`.

**Script:**

Trong Git, An có thể tạo commit đổi mức thuế mà không hề có ai review. Trên Bitbucket Cloud, pull request biến thay đổi đó thành đề xuất có chỗ thảo luận: nguồn nào, đích nào, nội dung nào, ai được mời đánh giá và trạng thái hiện tại ra sao. PR không tự tạo ra một loại commit đặc biệt, cũng không tự tiếp nhận thay đổi. Trên màn hình thử nghiệm, nhìn thấy PR `Open` là bằng chứng đang đề xuất chứ không phải thay đổi đã nằm trên `main`.

**Purpose:**

Làm rõ PR là đối tượng reviewable proposal và phân biệt nó với commit và merge.



## Repository, nhánh nguồn/đích và phần khác biệt

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:30–01:42`

**Visual:**

Giữ thẻ `BILL-142 Open` và source/destination; đảo thử đích sang nhánh release để lộ diff bất thường, rồi trở về `main`.

**Script:**

PR là đề xuất để cả nhóm đánh giá. Nhưng nó thật sự so sánh repository, nhánh nguồn và nhánh đích nào?

**Purpose:**

Chứng minh nhắm sai source/target có thể làm review đúng mã nhưng sai lịch tích hợp.

### Scene 2 — Repository, nhánh nguồn/đích và phần khác biệt

**Time:** `01:42–03:12`

**Visual:**

Hai hộp Source/Destination với thông tin repository và branch, tô đỏ khi source và target bị đảo; phóng to tab `Files changed` và diff thuế 8%→10%.

**Script:**

Trước khi để Bình đọc diff, An phải kiểm tra cả source repository/branch và destination repository/branch. Một PR có thể đi từ nhánh trong cùng repo hoặc từ fork, tùy quyền đóng góp. Trong Orchid, nguồn là `invoice-api/fix/BILL-142-tax`, đích là `invoice-api/main`. Nếu nhắm nhầm nhánh release cũ, một bản vá đúng về nghiệp vụ có thể được đưa vào sai nơi. Diff giúp Bình xem dòng thuế thay đổi, nhưng chỉ trong bối cảnh hai nhánh đã chọn. Trước khi quay, dùng dữ liệu demo và che đường dẫn private.

**Purpose:**

Dạy bằng chứng xác định hai đầu PR và phạm vi diff trước review.



## Mô tả đề xuất, bối cảnh và người đánh giá

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:12–03:24`

**Visual:**

Giữ diff thuế 8%→10%, mở bảng `Before / After / Invoice 100.05 / Verification` và thẻ reviewer Bình.

**Script:**

Chọn đúng nguồn và đích rồi; reviewer còn thiếu bối cảnh nào để đánh giá thay đổi?

**Purpose:**

Đưa bối cảnh nghiệp vụ và chứng cứ kiểm thử vào trước lời mời đánh giá, thay vì chỉ xem dòng mã.

### Scene 3 — Mô tả đề xuất, bối cảnh và người đánh giá

**Time:** `03:24–04:54`

**Visual:**

Hiện mô tả PR gồm `Before/After`, `Example invoice 100.05`, `Test evidence`, `Related Jira BILL-142`; bên cạnh là reviewer Bình.

**Script:**

Một tiêu đề kiểu 'fix bug' không trả lời lý do đổi thuế, dữ liệu bị ảnh hưởng hay cách tự kiểm chứng. An nên giải thích hành vi trước và sau, ví dụ hóa đơn số lẻ, phạm vi sửa, cùng kết quả kiểm tra có thể xem lại. Nếu Jira đã kết nối, mã `BILL-142` giúp đối chiếu yêu cầu. Bình là người hiểu phép làm tròn nên được mời review, không phải vì anh ấy có mọi quyền quản trị. Default reviewer có thể tự được điền theo cấu hình nhưng một người xuất hiện trong danh sách không có nghĩa đã phê duyệt.

**Purpose:**

Chuyển PR từ một trang diff thành đề xuất có mục đích, chứng cứ và reviewer phù hợp.



## Thay đổi đã chia sẻ so với thay đổi được nhóm chấp nhận

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:54–05:06`

**Visual:**

Giữ mô tả PR và reviewer, đặt `Open` bên cạnh main chưa đổi; mở task `Test negative invoice` còn pending.

**Script:**

PR đã chia sẻ đủ thông tin. Liệu nhóm đã chấp nhận nó hay chưa?

**Purpose:**

Phân biệt PR đã chia sẻ đầy đủ với mã đã được chấp nhận qua merge hợp lệ.

### Scene 4 — Thay đổi đã chia sẻ so với thay đổi được nhóm chấp nhận

**Time:** `05:06–06:36`

**Visual:**

Hai màn hình cạnh nhau: PR đang `Open`, task `Test negative invoice` chưa hoàn tất; nhánh đích `main` vẫn giữ trạng thái cũ. Thêm timeline push → proposed → accepted.

**Script:**

Khi An push nhánh và mở PR, mã có thể đã tới máy chủ nhưng vẫn chưa vào nhánh mà nhóm công nhận. Bình có thể yêu cầu kiểm thử số âm, tạo task hoặc đề nghị thay đổi. Một merge check cũng có thể báo chưa đạt. Chỉ sau khi nhóm xử lý quyền và điều kiện đang áp dụng, rồi có merge thực sự vào destination, ta mới được nói nguồn chung đã nhận thay đổi. Đặc biệt, cảnh báo trên Free/Standard không mặc nhiên ngăn thao tác merge; đừng coi nút vẫn bấm được là xác nhận chất lượng.

**Purpose:**

Phân biệt bằng chứng shared, reviewed, merged; mở cầu nối sang workflow review.
