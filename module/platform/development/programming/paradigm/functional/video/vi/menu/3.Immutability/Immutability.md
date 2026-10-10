---
video:
  url: ""
---

# Giá trị bất biến và trạng thái thay đổi

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

## Giá trị bất biến và tham chiếu có thể thay đổi

<!-- VIDEO_SECTION -->
### Scene 1 — Giữ lại đơn cũ

**Time:** `00:00–01:18`

**Visual:**

Trên bàn có thẻ đơn `[A,B]`. Bấm thêm C, thẻ cũ đứng yên, thẻ mới `[A,B,C]` xuất hiện bên cạnh; zoom vào danh sách lồng nhau.

**Script:**

Dữ liệu bất biến là dữ liệu không bị sửa sau khi tạo. Khi thêm món C, ta tạo đơn mới chứ không viết đè danh sách `[A,B]` của người đang giữ bản cũ. Nhưng nếu lớp vỏ bất biến còn trỏ vào danh sách bên trong có thể bị sửa, lời hứa vẫn chưa đủ. Ta phải nói rõ phần nào bất biến sâu và phần nào chỉ có tham chiếu cố định. Đây là khác biệt giữa tạo giá trị mới và âm thầm sửa một vật đã được chia sẻ.

**Purpose:**

Minh họa giá trị cũ được giữ và cảnh báo về nested mutation.

## Giá trị, danh tính và trạng thái theo thời gian

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:18–01:31`

**Visual:**

Nối hai ảnh chụp bằng đường timeline `order A` với điểm pending và paid.

**Script:**

Hai bản đơn có thể cùng đại diện một đơn hàng. Vậy ai giữ phiên bản hiện hành?

**Purpose:**

Tách identity/state khỏi value bất biến.

### Scene 2 — Một danh tính, nhiều giá trị theo thời gian

**Time:** `01:31–02:49`

**Visual:**

Cho thấy `Order A` ở thời điểm 1 `{status:pending}` và thời điểm 2 `{status:paid}`; ở trên là nhãn identity A cố định, mũi tên `currentVersion` chuyển từ bản 1 sang 2.

**Script:**

Chúng ta vừa tạo hai giá trị khác nhau, nhưng không nhất thiết tạo hai đơn hàng nghiệp vụ. `Order A` có danh tính xuyên thời gian; trạng thái hiện tại là phiên bản đang được chọn. Một ảnh chụp chờ thanh toán vẫn có thể giữ nguyên để kiểm tra lịch sử, trong khi bản đã thanh toán là phiên bản mới. Phần điều phối quyết định bản nào được lưu là hiện hành. Dữ liệu bất biến không có nghĩa hệ thống không bao giờ thay đổi trạng thái.

**Purpose:**

Phân biệt giá trị/identity/state và nơi quyết định phiên bản hiện hành.

## Chuyển trạng thái bằng cách tạo giá trị mới

<!-- VIDEO_SECTION -->
### Transition

**Time:** `02:49–03:02`

**Visual:**

Từ `pending`, tạo hai nhánh theo kết quả payment đã xác nhận.

**Script:**

Đã có hai ảnh chụp; giờ xem một chuyển trạng thái có thể là phép tính như thế nào.

**Purpose:**

Giới thiệu chuyển đổi dữ liệu thuần nhưng không giả lập thanh toán.

### Scene 3 — Tính trạng thái tiếp theo, đừng giả vờ thu tiền

**Time:** `03:02–04:20`

**Visual:**

Vẽ `transition(pending, PaymentApproved) → paid` và `transition(pending, PaymentDeclined) → rejection`; đóng khung `gateway call` bên ngoài đường tính.

**Script:**

Ta có thể viết một phép chuyển trạng thái: nhận bản chờ và **thông tin sự kiện đã biết**, rồi đề xuất bản đã thanh toán hoặc kết quả từ chối. Phép tính không cần sửa bản chờ. Nhưng tuyệt đối không tự tạo sự kiện `PaymentApproved` bằng suy đoán: chỉ cổng thanh toán mới cho biết tiền thực sự đã được chấp nhận hay chưa. Ghi phiên bản mới vẫn là việc bên ngoài cần phối hợp an toàn.

**Purpose:**

Ràng buộc event đã có bằng chứng và giữ ranh giới quyết định thuần/hiệu ứng.

## Chia sẻ cấu trúc dữ liệu và chi phí cấp phát

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:20–04:33`

**Visual:**

Phóng vào cây đơn hàng gồm 3 nhánh, đổi một lá, giữ hai nhánh gốc khác màu.

**Script:**

Tạo bản mới giúp giữ lịch sử, nhưng sao chép toàn bộ một cây lớn liệu có đắt?

**Purpose:**

Giải thích structural sharing ở mức hình ảnh.

### Scene 4 — Giữ nhánh cũ, chỉ tạo đường đi mới

**Time:** `04:33–05:51`

**Visual:**

Đặt hai sơ đồ cây gần nhau: Root1 và Root2 trỏ chung nhánh A,B bất biến; nhánh C2 và các nút trên đường đi được tô sáng, không tuyên bố mọi collection dùng cách này.

**Script:**

Nếu sao chép cả cây dữ liệu mỗi lần sửa một món, bộ nhớ có thể tăng đáng kể. Một cấu trúc bất biến lưu phiên bản có thể tạo gốc mới và dùng lại các nhánh không đổi, bởi các nhánh đó an toàn để chia sẻ. Nhưng đây là một kỹ thuật của cấu trúc cụ thể, không phải tính năng mặc nhiên của mọi collection bất biến. Khi cần tối ưu, phải đo chi phí cấp phát và truy cập.

**Purpose:**

Minh hoạ điều kiện chia sẻ cấu trúc và tradeoff thực tế.

## Bất biến, xử lý đồng thời và các giới hạn thực tế

<!-- VIDEO_SECTION -->
### Transition

**Time:** `05:51–06:04`

**Visual:**

Cho hai người đọc version3, mỗi người tạo một version4 khác nhau.

**Script:**

Ngay cả khi các ảnh chụp không bị sửa, hai người vẫn có thể tranh chấp phiên bản đang lưu.

**Purpose:**

Bác bỏ kết luận sai bất biến tự giải quyết cạnh tranh.

### Scene 5 — Hai phiên bản 4 không thể cùng thắng

**Time:** `06:04–07:22`

**Visual:**

Hiện `request X: v3→v4X`, `request Y: v3→v4Y` đồng thời, rồi yêu cầu bộ lưu `check version 3` trước khi chọn một kết quả; đường còn lại hiện `conflict`.

**Script:**

Hai yêu cầu đều đọc đơn phiên bản 3 và mỗi yêu cầu tạo ra một giá trị phiên bản 4. Cả hai giá trị đều bất biến, nhưng điều đó không thể quyết định bản nào được lưu cuối cùng. Bộ điều phối cần cơ chế kiểm tra phiên bản, khóa hoặc chính sách nhất quán. Tương tự, giá trị bất biến không ngăn một yêu cầu thu tiền bị gửi hai lần. Bất biến giúp bảo toàn ảnh chụp, không hứa tự xử lý concurrency và giao dịch.

**Purpose:**

Phân biệt an toàn ảnh chụp với điều phối cập nhật bên ngoài.
