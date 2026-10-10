---
video:
  url: ""
---

# Trách nhiệm, hợp đồng và sự cộng tác giữa đối tượng

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


## Trách nhiệm của đối tượng trong một bài toán

<!-- VIDEO_SECTION -->

### Scene 1 — Trách nhiệm của đối tượng trong một bài toán

**Time:** 00:00–01:14

**Visual:** Dựng ba hộp ACCOUNT, CHECKOUT, NOTIFIER. Nối Account với bảo vệ số dư, Checkout với điều phối, Notifier với gửi thông tin.

**Script:** Ai phải chịu trách nhiệm khi hệ thống chạy sai? Tài khoản quản lý quy tắc rút tiền hợp lệ. Bộ thông báo có nhiệm vụ chuyển kết quả đến người dùng. Checkout điều phối yêu cầu. Nếu ngày mai đổi câu chữ email, ta không muốn buộc chỉnh phần kiểm tra số dư. Trách nhiệm không có nghĩa mỗi đối tượng chỉ được có một phương thức; các hành vi liên quan có thể cùng thuộc một vai trò.

**Purpose:** Giới thiệu ranh giới trách nhiệm qua lý do thay đổi khác nhau.



## Xác định nơi sở hữu hành vi và quyết định

<!-- VIDEO_SECTION -->

### Transition

**Time:** 01:14–01:27

**Visual:** Phóng to Checkout đang nhìn thẳng vào ô balance của Account; dịch điều kiện kiểm tra về trong Account.

**Script:** Đã biết các vai trò rồi, nhưng quyết định cụ thể có được rút tiền phải nằm ở đâu?

**Purpose:** Biến tên trách nhiệm thành vị trí sở hữu quyết định.

### Scene 2 — Xác định nơi sở hữu hành vi và quyết định

**Time:** 01:27–02:41

**Visual:** Đặt phép kiểm tra đủ tiền nhầm trong Checkout; kéo quyết định về Account và chỉ giữ request/result trong Checkout. Giữ coordinator chuyển giữa hai tài khoản.

**Script:** Giả sử Checkout tự đọc số dư rồi quyết định có được rút hay không. Một nơi gọi khác lại phải sao chép cùng logic. Tài khoản mới là bên có thông tin và quyền bảo vệ số dư, nên quyết định nên nằm gần nó. Nhưng chuyển tiền giữa hai tài khoản lại là việc phối hợp rộng hơn, không nên ép một tài khoản tự làm toàn bộ. Chọn nơi sở hữu theo kiến thức và quyền quyết định.

**Purpose:** Phân biệt trách nhiệm bảo vệ số dư và trách nhiệm điều phối nhiều đối tượng.



## Tính gắn kết giữa các hành vi cùng trách nhiệm

<!-- VIDEO_SECTION -->

### Transition

**Time:** 02:41–02:54

**Visual:** Sau khi chuyển quyền quyết định về Account, nhóm các hành vi theo mục đích và kéo email không liên quan ra khỏi hộp.

**Script:** Đặt một quy tắc đúng chỗ vẫn chưa đủ nếu đối tượng đó ôm nhiều việc không liên quan.

**Purpose:** Nối quyền sở hữu hành vi sang tính gắn kết.

### Scene 3 — Tính gắn kết giữa các hành vi cùng trách nhiệm

**Time:** 02:54–04:08

**Visual:** Tô deposit, withdraw, availableBalance màu xanh trong Account; kéo sendPromotionalEmail sang Notifier và hiện hai lý do thay đổi độc lập.

**Script:** Deposit, withdraw và xem số dư đều phục vụ trách nhiệm quản lý tài khoản. Nhưng gửi thư khuyến mãi không có cùng lý do thay đổi. Nếu một EverythingManager ôm cả hai, mỗi lần sửa email cũng đụng vào nơi tính tiền. Cohesion nói về mức các hành vi phục vụ cùng mục đích, không phải cứ ít phương thức mới tốt.

**Purpose:** Nêu tính gắn kết bằng nhóm hành vi và lý do sửa đổi.



## Hợp đồng hành vi giữa đối tượng cộng tác

<!-- VIDEO_SECTION -->

### Transition

**Time:** 04:08–04:21

**Visual:** Giữ Account và Checkout, đặt thẻ input/outcome lên đúng mũi tên request giữa hai hộp.

**Script:** Có nơi sở hữu trách nhiệm rồi. Nhưng bên gọi thực sự được phép kỳ vọng điều gì?

**Purpose:** Chuẩn bị lời hứa cụ thể cho cộng tác.

### Scene 4 — Hợp đồng hành vi giữa đối tượng cộng tác

**Time:** 04:21–05:35

**Visual:** Đặt bảng hợp đồng cạnh withdraw: amount>0; đủ tiền thì trừ đúng; không đủ thì FAILURE kèm lý do và balance không đổi. Replay 70/90.

**Script:** Khi hai đối tượng cộng tác, chữ ký withdraw(amount) chưa đủ để người gọi tin. Ta phải nói số tiền hợp lệ phải dương, khi thành công trừ chính xác và khi bị từ chối số dư không đổi. Nếu số dư hiện tại là bảy mươi, yêu cầu chín mươi không được làm số dư âm rồi trả kết quả như thành công. Hợp đồng mô tả điều bên ngoài quan sát được, không chỉ kiểu dữ liệu.

**Purpose:** Biến hợp đồng hành vi thành ca thành công và thất bại có thể kiểm thử.



## Luồng phối hợp nhiều đối tượng cho một yêu cầu

<!-- VIDEO_SECTION -->

### Transition

**Time:** 05:35–05:48

**Visual:** Đưa bảng hợp đồng withdraw vào một sequence hai nhánh, chỉ nối đến Notifier sau khi có kết quả.

**Script:** Một lời hứa chỉ hữu ích khi phía gọi xử lý đúng cả nhánh thành công lẫn từ chối.

**Purpose:** Dẫn từ hợp đồng tới luồng cộng tác có điều kiện.

### Scene 5 — Luồng phối hợp nhiều đối tượng cho một yêu cầu

**Time:** 05:48–07:02

**Visual:** Sequence: Checkout→Account.withdraw30→SUCCESS→Notifier.receipt; nhánh withdraw90 tại balance70 trả FAILURE và gạch bỏ SUCCESS receipt.

**Script:** Hãy nhìn thứ tự lời gọi. Checkout xin tài khoản rút tiền. Nếu tài khoản báo thành công, bộ thông báo mới gửi biên nhận. Còn nếu từ chối vì không đủ tiền, không được gửi email như thể thanh toán đã hoàn tất. Mỗi đối tượng chịu trách nhiệm của mình: Account duyệt, Notifier gửi, Checkout điều phối nhánh. Vết gọi giúp ta chỉ ra điểm lỗi mà không cần phá ranh giới dữ liệu.

**Purpose:** Trực quan hóa phối hợp và nhánh lỗi không được tạo biên nhận thành công.



## Hợp thành đối tượng từ các phần có trách nhiệm riêng

<!-- VIDEO_SECTION -->

### Transition

**Time:** 07:02–07:15

**Visual:** Biến ba hộp trong sequence vừa rồi thành các bộ phận có thể thay thế; tô riêng FeePolicy để thử đổi.

**Script:** Nhiều đối tượng đang phối hợp, ta có thể tổ chức chúng thành phần lớn hơn bằng cách nào?

**Purpose:** Từ message flow sang composition theo vai trò.

### Scene 6 — Hợp thành đối tượng từ các phần có trách nhiệm riêng

**Time:** 07:15–08:29

**Visual:** Checkout dùng ba cộng tác viên Account, FeePolicy, Notifier; thay FlatFeePolicy bằng PercentFeePolicy mà không giả chúng là subtype Account.

**Script:** Composition là ghép những đối tượng có trách nhiệm riêng thành hành vi lớn hơn. Checkout nhờ Account quản lý số dư, FeePolicy quyết định phí và Notifier gửi kết quả. Ta có thể đổi chính sách phí nếu nó vẫn đáp ứng hợp đồng cần thiết. Không có lý do gì để coi chính sách phí là một loại tài khoản. Nhưng nếu mọi phép cộng lại phải đi qua năm lớp trung gian thì thiết kế đang quá nặng.

**Purpose:** Giải thích quan hệ có cộng tác viên và tránh nhầm với quan hệ kế thừa.



## Mức độ phụ thuộc và tác động của thay đổi thiết kế

<!-- VIDEO_SECTION -->

### Transition

**Time:** 08:29–08:42

**Visual:** Giữ nguyên các cộng tác viên, bật hai lớp mũi tên: đọc thẳng cấu trúc nội bộ và chỉ gọi hợp đồng.

**Script:** Composition vẫn tạo phụ thuộc; vậy mỗi cộng tác viên cần biết nhiều hay ít về nhau?

**Purpose:** Kết thúc chương trách nhiệm bằng tiêu chí coupling.

### Scene 7 — Mức độ phụ thuộc và tác động của thay đổi thiết kế

**Time:** 08:42–09:56

**Visual:** Sơ đồ đỏ: Checkout đọc Account.balance và định dạng ledger; sơ đồ xanh: Checkout chỉ gọi withdraw. Đổi từ balance field sang ledger ở cả hai.

**Script:** Khi Checkout biết cả tên trường, cách lưu lịch sử và định dạng dữ liệu tài khoản, chỉ một thay đổi nhỏ bên trong cũng khiến nó phải sửa theo. Nếu bên gọi chỉ phụ thuộc hợp đồng withdraw thì tài khoản có thể thay đổi cách lưu mà vẫn giữ hành vi. Coupling không có nghĩa mọi đối tượng phải độc lập tuyệt đối. Chúng vẫn cần phụ thuộc vào những lời hứa đủ rõ, chỉ tránh biết chi tiết không cần thiết.

**Purpose:** Chỉ ra thay đổi lan truyền khi phụ thuộc vào cấu trúc thay vì hợp đồng.
