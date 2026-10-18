---
video:
  url: ""
---

# Mã nguồn cục bộ và nguồn dùng chung

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

## Tệp đang chỉnh sửa và lịch sử có phiên bản

<!-- VIDEO_SECTION -->

### Scene 1 — Tệp đang chỉnh sửa và lịch sử có phiên bản

**Time:** `00:00–01:15`

**Visual:**

Tách ba lớp trên màn hình: editor của An đang có `Invoice.java` 10%, vùng `Đã ghi nhận` vẫn 8%, nhóm chung đang 8%; bật sáng từng lớp.

**Script:**

An đã gõ thuế mười phần trăm trong editor, nhưng bản đang sửa chưa chắc đã có trong lịch sử. Có thể file chỉ mới lưu trên đĩa, chưa ghi thành revision. Ở màn hình này, ba nơi có thể giữ ba trạng thái: tệp đang làm, lịch sử cục bộ đã ghi và nguồn chung nhóm đang dùng. Nếu An đóng editor rồi nói ‘tôi đã lưu’, Bình chưa có đủ thông tin để biết bản sửa nào được ghi nhận. Chúng ta cần quan sát rõ từng ranh giới trước khi gọi một thay đổi là đã hoàn thành.

**Purpose:**

Khởi tạo mô hình ba trạng thái khác biệt: working files, recorded history, shared source.



## Kho cục bộ và kho từ xa: Vai trò và ranh giới

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:15–01:27`

**Visual:**

Giữ ba tầng editor/recorded/shared cho Invoice.java, tách hai hộp `An local repo` và `Team remote`; mũi tên trao đổi vẫn tắt cho tới khi sync.

**Script:**

Nếu bản sửa trên máy chưa đồng nghĩa bản nhóm đang dùng, nơi nào giữ từng trạng thái?

**Purpose:**

Chuyển từ file đang sửa sang nơi mỗi repository giữ lịch sử mà không giả đồng bộ tự động.

### Scene 2 — Kho cục bộ và kho từ xa: Vai trò và ranh giới

**Time:** `01:27–02:42`

**Visual:**

Hai hộp repository `Local An` và `Remote team`; dưới An là working files, trong mỗi hộp là lịch sử commit. Hiện mũi tên trao đổi chỉ khi An chủ động đồng bộ.

**Script:**

Một local repository lưu lịch sử mà An đã ghi nhận ở máy mình. Remote repository là kho khác được dùng để trao đổi lịch sử, thường đặt trên máy chủ. Hai kho không có đường dây thần kỳ đồng bộ từng phím gõ; việc truyền dữ liệu diễn ra qua các thao tác và quyền phù hợp. Nếu An tạo commit local, server có thể chưa biết. Nếu nhóm trên server đã nhận commit mới của Bình, máy An cũng có thể chưa có. Tránh gọi remote là nguồn duy nhất tuyệt đối; nhóm phải định nghĩa đâu là nhánh hoặc mốc tích hợp mà họ chấp nhận.

**Purpose:**

Giải thích ranh giới local/remote đúng với Git khái niệm, không gán đồng bộ tự động hay review mặc định.



## Đồng bộ và lịch sử khác nhau giữa các bản sao

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:42–02:54`

**Visual:**

Giữ hộp local và remote; đặt shared ancestor B ở giữa, local tăng tới C còn remote tới D; hai đầu C/D không có mũi tên merge tự động.

**Script:**

Hai repository đều hợp lệ nhưng không cùng mốc: ta đọc sự khác nhau thế nào?

**Purpose:**

Dựng bằng chứng divergence từ hai lịch sử hợp lệ thay vì coi remote khác local là lỗi mất dữ liệu.

### Scene 3 — Đồng bộ và lịch sử khác nhau giữa các bản sao

**Time:** `02:54–04:09`

**Visual:**

Đồ thị commit chung A–B tách thành An→C và Remote→D; che mũi tên merge trước, chỉ hiện dấu `diverged` và hai đỉnh riêng.

**Script:**

Giả sử An có commit C sửa thuế và remote có commit D bổ sung làm tròn. C và D đều có cùng tổ tiên B, nhưng chưa nằm trên cùng một tuyến cha. Đó là lịch sử phân kỳ, không phải mặc định có dữ liệu hỏng. Chia sẻ C không tự động xóa D, và lấy D về không đồng nghĩa C đã được hợp nhất. Người làm cần xem mỗi phía thêm gì, chọn cách tích hợp và kiểm tra nội dung kết quả. Cơ chế fetch, merge hay rebase sẽ thuộc module Git; ở đây ta chỉ cần hiểu vì sao hai bản sao có thể chưa cùng lịch sử.

**Purpose:**

Trực quan hóa divergence dưới dạng DAG và ngăn suy luận 'đã fetch = đã merge'.



## Lịch sử nguồn chung được chấp nhận: Khái niệm và tiêu chí

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:09–04:21`

**Visual:**

Giữ hai nhánh C và D xuất phát từ B, thêm điểm tích hợp M và vùng `Review/check criteria`; chỉ đánh dấu `Accepted` sau khi điều kiện có bằng chứng.

**Script:**

Sau khi kết hợp C và D, làm sao biết bản nào thực sự được nhóm coi là chuẩn?

**Purpose:**

Chuyển từ kết hợp lịch sử sang quyết định nguồn chung đã được tiếp nhận theo quy tắc nhóm.

### Scene 4 — Lịch sử nguồn chung được chấp nhận: Khái niệm và tiêu chí

**Time:** `04:21–05:36`

**Visual:**

Từ đồ thị A–B→C,D thêm nút M chứa cả thuế 10% và làm tròn; đặt nhãn `Proposed` lên C/D và `Accepted integration` tại M, kèm dấu hỏi review.

**Script:**

Một bản đã đẩy tới server có thể chỉ là đề xuất. Nhóm thường xác định nguồn chấp nhận, chẳng hạn một nhánh tích hợp cùng các điều kiện kiểm tra và đánh giá. Nếu mốc M chứa cả sửa thuế của An và làm tròn của Bình, nó chỉ trở thành nguồn chung được chấp nhận khi đáp ứng quy trình nhóm đặt ra; sự tồn tại của M không tự chứng minh rằng đã có reviewer. Ta phải đối chiếu mốc lịch sử với trạng thái tiếp nhận riêng. Đây là lý do không thể dùng một lệnh Git duy nhất để chứng minh thay đổi đã được duyệt hoặc triển khai.

**Purpose:**

Xác định nguồn chuẩn qua thỏa thuận/quy tắc tiếp nhận, không qua tên remote hay một commit bất kỳ.
