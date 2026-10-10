---
video:
  url: ""
---

# Lập trình hướng đối tượng: Mô hình đối tượng và trách nhiệm

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


## Lập trình hướng đối tượng: Khái niệm, bản chất và phạm vi

<!-- VIDEO_SECTION -->

### Scene 1 — Lập trình hướng đối tượng: Khái niệm, bản chất và phạm vi

**Time:** 00:00–01:14

**Visual:** Vẽ hai thẻ tài khoản A và B đều có số dư 100; đóng dấu mã tài khoản khác nhau. Chạy rút 30 chỉ trên A và giữ B ở 100.

**Script:** Nhìn vào hai tài khoản này nhé. Cả hai đều có một trăm, nhưng không phải cùng một tài khoản. Rút ba mươi ở A không thể tự làm B mất tiền. Đó là ý nghĩa của danh tính đối tượng: yêu cầu được gửi đến đúng thực thể nào. Đối tượng có thể có trạng thái và hành vi riêng, kể cả không cho sửa. OOP nói về trách nhiệm và cộng tác, không phải cứ tạo nhiều class là xong.

**Purpose:** Mở đầu bằng danh tính và cùng giá trị mà không đánh đồng OOP với sự thay đổi dữ liệu.



## Động cơ tổ chức dữ liệu và hành vi theo trách nhiệm

<!-- VIDEO_SECTION -->

### Transition

**Time:** 01:14–01:27

**Visual:** Giữ hai tài khoản có mã riêng, đồng thời nhân ba mũi tên caller đều tự tính điều kiện rút tiền.

**Script:** Mỗi tài khoản có danh tính riêng; vậy khi nhiều nơi cùng cần kiểm tra, ai phải giữ một quy tắc nhất quán?

**Purpose:** Từ danh tính chuyển sang nhu cầu sở hữu hành vi dùng chung.

### Scene 2 — Động cơ tổ chức dữ liệu và hành vi theo trách nhiệm

**Time:** 01:27–02:41

**Visual:** Hiện ba ô Screen, BatchJob, Checkout đều tự kiểm tra số dư. Đổi quy tắc thấu chi, hiện ba cờ phải sửa rồi kéo quyết định về ô ACCOUNT.

**Script:** Thử tưởng tượng quy tắc rút tiền đổi vào tháng sau. Nếu màn hình, tác vụ nền và bộ thanh toán đều tự kiểm tra, người sửa rất dễ quên một chỗ. Lỗi nằm ở việc chưa xác định ai chịu trách nhiệm cho quy tắc. Khi tài khoản sở hữu việc duyệt rút tiền, các bên chỉ cần gửi yêu cầu và xử lý kết quả theo một cam kết thống nhất.

**Purpose:** Đặt vấn đề trách nhiệm và thay đổi quy tắc bị phân tán một cách cụ thể.



## Cách tổ chức thủ tục và dữ liệu tách rời

<!-- VIDEO_SECTION -->

### Transition

**Time:** 02:41–02:54

**Visual:** Gom ba ô kiểm tra số dư thành một hàm withdraw(account,amount), rồi đặt cạnh một object Account có hành vi withdraw.

**Script:** Gom quy tắc vào một nơi đã tốt. Cách tổ chức đó khác gì với việc giao chính tài khoản sở hữu quyết định?

**Purpose:** Không tạo hiểu lầm rằng thủ tục là cách làm sai.

### Scene 3 — Cách tổ chức thủ tục và dữ liệu tách rời

**Time:** 02:54–04:08

**Visual:** Chia màn hình: bên trái AccountData + withdraw(account,amount), bên phải Account.withdraw(amount); cả hai chạy 100→70 và hiện SUCCESS.

**Script:** Lập trình thủ tục vẫn có thể giải quyết bài toán rất tốt. Một hàm nhận dữ liệu tài khoản và số tiền có thể kiểm tra chính xác. Khác biệt cần cân nhắc là khi hệ thống lớn lên, ai được quyền quyết định số dư? Mỗi caller đều điều khiển cấu trúc dữ liệu hay tài khoản là ranh giới chịu trách nhiệm? Đừng chọn OOP chỉ vì nghe có vẻ hiện đại hơn.

**Purpose:** So sánh OOP và thủ tục công bằng bằng cùng một kết quả số học.



## Đối tượng, danh tính, trạng thái, hành vi và cộng tác

<!-- VIDEO_SECTION -->

### Transition

**Time:** 04:08–04:21

**Visual:** Đưa cùng dữ liệu và quy tắc rút từ sơ đồ thủ tục vào khung Account, giữ nguyên kết quả 100→70.

**Script:** Cả hai cách đều có thể tính đúng. Ranh giới đối tượng giúp diễn đạt điều gì về quyền quyết định?

**Purpose:** Giới thiệu mô hình đối tượng qua trách nhiệm, không qua cú pháp class.

### Scene 4 — Đối tượng, danh tính, trạng thái, hành vi và cộng tác

**Time:** 04:21–05:35

**Visual:** Làm động ô Account A gồm mã, balance và withdraw; Checkout gửi withdraw(30), nhận SUCCESS và thấy 100→70 mà không dùng setter.

**Script:** Hãy lần theo lời gọi này. Checkout gửi yêu cầu rút ba mươi tới tài khoản A. Chính A kiểm tra điều kiện, quyết định có chấp nhận không rồi thay đổi số dư hợp lệ. Bên gọi chỉ nhận kết quả, không cần sửa trường bên trong. Danh tính cho biết ta đang xử lý tài khoản nào; hợp đồng cho biết hành vi được phép và kết quả mà caller có thể tin.

**Purpose:** Ghép danh tính, trạng thái, hành vi và cộng tác thành một ví dụ có vết gọi.



## Quan hệ giữa tư duy OOP và cơ chế ngôn ngữ Java

<!-- VIDEO_SECTION -->

### Transition

**Time:** 05:35–05:48

**Visual:** Giữ sơ đồ Checkout gọi Account, cho các từ khóa Java rơi xuống dưới vạch phân cách và giữ phần lời hứa ở trên.

**Script:** Mô hình trách nhiệm vừa vẽ vẫn có nghĩa ngay cả khi dùng một ngôn ngữ khác.

**Purpose:** Đặt giới hạn học phù hợp cho module OOP.

### Scene 5 — Quan hệ giữa tư duy OOP và cơ chế ngôn ngữ Java

**Time:** 05:48–07:02

**Visual:** Vẽ vạch ranh giới: phía trên IDENTITY, CONTRACT, INVARIANT; phía dưới Java CLASS, INTERFACE, PRIVATE, DISPATCH, ghi chú bài Java Core.

**Script:** Đến đây ta chưa cần cú pháp private hay interface của Java mà vẫn hiểu ai giữ quy tắc rút tiền. Ta đã mô tả danh tính, trách nhiệm, hợp đồng và điều gì phải đúng khi thao tác lỗi. Những từ khóa của Java giúp hiện thực mô hình đó, nhưng không phải toàn bộ định nghĩa OOP. Học tư duy sở hữu trước, rồi quay lại ngôn ngữ để biết nó hỗ trợ bằng cơ chế nào.

**Purpose:** Tách định nghĩa paradigm khỏi cú pháp và luật dispatch Java.



## Kiến thức khởi đầu và lộ trình từ đối tượng đến thiết kế

<!-- VIDEO_SECTION -->

### Transition

**Time:** 07:02–07:15

**Visual:** Gập phần cú pháp Java lại, giữ hai thẻ tài khoản rồi mở dần tuyến từ danh tính đến quyết định thiết kế.

**Script:** Trước khi chọn cơ chế ngôn ngữ, ta nên hiểu những câu hỏi thiết kế nào theo thứ tự?

**Purpose:** Nối chương mở đầu sang nội dung invariant và cộng tác.

### Scene 6 — Kiến thức khởi đầu và lộ trình từ đối tượng đến thiết kế

**Time:** 07:15–08:29

**Visual:** Hiện tuyến năm trạm IDENTITY → INVARIANT → RESPONSIBILITY → PAYMENT VARIANTS → TRADE-OFF, mỗi trạm mở thêm một phần sơ đồ tài khoản.

**Script:** Chúng ta sẽ đi từng bước. Trước hết phân biệt đối tượng và giá trị, rồi xem tài khoản bảo vệ trạng thái hợp lệ ra sao. Tiếp theo chọn ai chịu trách nhiệm và các đối tượng gọi nhau như thế nào. Sau đó cho ví điện tử và chuyển khoản cùng đáp ứng một hợp đồng thanh toán. Cuối cùng, ta tự kiểm tra xem cây kế thừa có thật sự cần thiết và quy trình thất bại thì điều gì xảy ra.

**Purpose:** Đưa lộ trình có nền tảng an toàn và hứa hẹn ví dụ nhất quán qua chương.
