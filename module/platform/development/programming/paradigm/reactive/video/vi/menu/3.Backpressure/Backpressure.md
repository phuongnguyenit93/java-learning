---
video:
  url: ""
---

# Chênh lệch tốc độ producer–consumer và backpressure

<!--
VIDEO SCRIPT FORMAT

Section rules:
- Each H2 is one video section/chapter.
- The first section requires at least 1 Scene.
- From the second section onward, each section requires at least 1 Transition + 1 Scene.
- Step 6 may add more Scenes/Transitions when the Knowledge content needs them.

TRANSITION FORMAT

### Transition

**Time:** MM:SS–MM:SS

**Visual:**
Describe what changes visually between the previous concept and the next.
**Script:**
Write a short spoken bridge into the following section.
**Purpose:**
Explain why this transition is pedagogically needed.

SCENE FORMAT

### Scene N — optional presentation title

**Time:** MM:SS–MM:SS
**Visual:**
Describe the exact diagram, timeline, source, or observation visible.
**Script:**
Write natural narration for the filmed demonstration.
**Purpose:**
State the specific concept or evidence this scene demonstrates.
-->

## Producer phát nhanh và consumer xử lý chậm: Nguyên nhân tồn đọng

<!-- VIDEO_SECTION -->

### Scene 1 — Producer phát nhanh và consumer xử lý chậm: Nguyên nhân tồn đọng

**Time:** `00:00–00:58`

**Visual:**

Đặt hai bộ đếm trên hàng chờ: CẢM BIẾN +100/giây và MÀN HÌNH -40/giây. Cho tích lũy +60/giây, dừng ở giây 1 còn 60, giây 10 còn khoảng 600 (bắt đầu rỗng).

**Script:**

Màn hình xử lý chậm hơn cảm biến: mỗi giây một trăm số đo tới nhưng chỉ bốn mươi được dùng. Nếu liên tục nhận mà không giới hạn hay loại bớt, mỗi giây tồn thêm sáu mươi, sau mười giây là khoảng sáu trăm phần tử. Tên reactive không làm phép trừ này biến mất. Ta phải biết hàng đợi nằm ở đâu và nguồn có thể giảm tốc hay không.

**Purpose:**

Chứng minh chênh lệch tốc độ và lượng tồn bằng số cụ thể có giả thiết rõ.

## Bộ đệm, giới hạn tài nguyên và nguy cơ mất dữ liệu

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:58–01:12`

**Visual:**

Giữ bộ đếm +60 phần tử mỗi giây và vẽ hàng chờ đúng 120 ô.

**Script:**

Queue tăng sáu mươi mỗi giây. Nếu bộ đệm chỉ có một trăm hai mươi chỗ thì bao lâu đầy?

**Purpose:**

Nối phép tính chênh lệch tốc độ với sức chứa và dữ liệu cũ.

### Scene 2 — Bộ đệm, giới hạn tài nguyên và nguy cơ mất dữ liệu

**Time:** `01:12–02:10`

**Visual:**

Vẽ hàng chờ 120 ô; giây một đầy 60, sau khoảng hai giây đầy 120. Bên cạnh đối chiếu queue có giới hạn, vô hạn và tuổi số đo lâu nhất.

**Script:**

Nếu hàng chờ chỉ chứa được một trăm hai mươi mục, phần dư sáu mươi mỗi giây khiến nó đầy sau khoảng hai giây trong mô hình đơn giản. Hàng đợi giới hạn cần chính sách khi phần tử mới tới. Hàng đợi vô hạn không xóa giới hạn tài nguyên, chỉ chuyển vấn đề sang bộ nhớ và dữ liệu quá cũ. Bảng nhiệt độ hiển thị số đo năm phút trước có thể sai mục đích dù không mất mẫu.

**Purpose:**

Thể hiện thời gian đầy hàng chờ, chi phí bộ nhớ và độ trễ dữ liệu.

## Nhu cầu nhận (demand) và điều phối số lượng tín hiệu

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:10–02:24`

**Visual:**

Giữ hàng đợi đang đầy, thay đồng hồ đầu vào bằng bộ đếm quyền nhận hiện 3; chỉ cho phần tử đi khi có một lượt cho phép.

**Script:**

Tăng hàng đợi vẫn không chữa được chênh lệch kéo dài. Liệu người nhận có thể nói rõ mình sẵn sàng nhận bao nhiêu?

**Purpose:**

Chuyển từ giữ phần tử dư sang hạn mức demand theo từng subscription.

### Scene 3 — Nhu cầu nhận (demand) và điều phối số lượng tín hiệu

**Time:** `02:24–03:22`

**Visual:**

Cho bộ đếm demand đổi: request(3)→3; onNext(A)→2, onNext(B)→1, request(2)→3, onNext(C)→2; không tự thêm phần tử nào.

**Script:**

Demand là số lượt được cho phép, không phải bộ hẹn giờ. Người nhận xin ba phần tử; nhận A và B thì còn một. Xin thêm hai, số lượt chưa dùng lại thành ba. Nhận C còn hai. Publisher có thể phát ít hơn nếu chưa có dữ liệu, nhưng không được vượt hạn mức đã cấp. Và bộ đếm này cũng không chứng minh mọi hàng đợi trung gian đều có giới hạn.

**Purpose:**

Diễn giải phép cộng và tiêu hao demand chính xác, phân biệt một subscription với tổng bộ nhớ hệ thống.

## Đánh đổi giữa buffering, dropping và giảm tốc nguồn phát

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:36`

**Visual:**

Chia hàng đợi tràn thành ba hướng: đệm có tuổi dữ liệu, thay số đo cũ, giảm tốc nguồn có hỗ trợ; gắn biểu tượng mất dữ liệu và trễ.

**Script:**

Demand giúp nếu phía trước chịu phối hợp. Với cảm biến vẫn đo liên tục, ta phải chọn chính sách cho số đo dư.

**Purpose:**

Nối hạn mức giao thức với đánh đổi mất dữ liệu và độ trễ thật.

### Scene 4 — Đánh đổi giữa buffering, dropping và giảm tốc nguồn phát

**Time:** `03:36–04:34`

**Visual:**

Ba bảng tại nguồn100/giây, màn hình40/giây: BUFFER (queue dài lên), DROP OLD (giữ mẫu 33°C mới), THROTTLE (adapter giới hạn số gửi). Bên cạnh gạch chéo lựa chọn DROP cho giao dịch tiền.

**Script:**

Không có chiến lược quá tải tốt nhất cho mọi bài toán. Đệm giữ mẫu một thời gian nhưng tăng bộ nhớ và độ trễ. Bỏ số đo cũ có thể hợp với giao diện chỉ cần nhiệt độ mới nhất, nhưng không thể âm thầm bỏ thanh toán hay cảnh báo an toàn bắt buộc. Giảm tốc chỉ có ích nếu có thành phần upstream chịu điều chỉnh. Hãy xác định dữ liệu nào được mất và được trễ trước khi chọn operator.

**Purpose:**

Đối chiếu mất dữ liệu, độ trễ và khả năng giảm tốc bằng hai miền có yêu cầu khác nhau.

## Demand-based backpressure và giới hạn áp dụng theo API

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:34–04:48`

**Visual:**

Gỡ chi tiết cảm biến, giữ một kết nối có request(3), khoanh khung CONTRACT để tách nó khỏi các chính sách đệm/bỏ.

**Script:**

Những cách xử lý tải vừa rồi là quyết định thiết kế. Giờ hãy xem hợp đồng demand thực sự hứa điều gì và bỏ lại phần nào cho ta.

**Purpose:**

Chuyển từ chọn chiến lược vận hành sang phạm vi bắt buộc của RS.

### Scene 5 — Demand-based backpressure và giới hạn áp dụng theo API

**Time:** `04:48–05:46`

**Visual:**

Một Subscriber gửi request(3) tới Subscription, ba ô onNext được cho phép; bên cạnh nguồn hot vẫn đo 100/giây và adapter có hàng đợi riêng.

**Script:**

Backpressure theo demand giới hạn số phần tử publisher đúng chuẩn gửi trên một subscription. Nó không nhất thiết bắt cảm biến vật lý dừng đo. Adapter nguồn hot có thể vẫn nhận một trăm mẫu mỗi giây, phải tự quản bộ đệm hoặc chính sách bỏ bớt trước ranh giới Publisher. Vì vậy tuân thủ giao thức và an toàn tài nguyên toàn hệ thống có liên quan nhưng không đồng nghĩa.

**Purpose:**

Khoanh phạm vi demand đúng một ranh giới, không ngầm hứa giảm tốc nguồn vật lý.
