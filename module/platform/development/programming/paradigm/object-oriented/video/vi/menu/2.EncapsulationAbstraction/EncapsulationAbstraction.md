---
video:
  url: ""
---

# Danh tính, trạng thái và ranh giới đóng gói đối tượng

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


## Danh tính của đối tượng và sự khác biệt với giá trị

<!-- VIDEO_SECTION -->

### Scene 1 — Danh tính của đối tượng và sự khác biệt với giá trị

**Time:** 00:00–01:14

**Visual:** Hai tài khoản A/B mang mã khác nhau nhưng đều balance=100; bên cạnh hai thẻ giá trị 100 VND giống nhau. Đặt nhãn IDENTITY và VALUE EQUALITY riêng.

**Script:** Hai tài khoản cùng có một trăm không có nghĩa là cùng thực thể. A có lịch sử của A, B có lịch sử của B; trừ ở A thì B không đổi. Còn hai giá trị biểu diễn một trăm đồng có thể được xem là bằng nhau về nội dung. Ta đang hỏi hai câu khác nhau: đây có phải cùng tài khoản không, và giá trị đang bằng nhau không. Đừng vội lấy phép so reference của Java làm toàn bộ câu trả lời.

**Purpose:** Tách danh tính thực thể và bình đẳng giá trị mà không giảng sai cú pháp Java.



## Trạng thái và hành vi có liên quan nhưng không luôn thay đổi

<!-- VIDEO_SECTION -->

### Transition

**Time:** 01:14–01:27

**Visual:** Giữ nguyên mã A rồi chạy hai yêu cầu trên cùng đối tượng: một yêu cầu chỉ hỏi, một yêu cầu xin phép đổi trạng thái.

**Script:** Đã biết đang nói tới tài khoản nào. Nhưng không phải hành vi nào của nó cũng thay đổi số dư.

**Purpose:** Từ identity chuyển sang hành vi và state có thể thay đổi hoặc không.

### Scene 2 — Trạng thái và hành vi có liên quan nhưng không luôn thay đổi

**Time:** 01:27–02:41

**Visual:** Giữ Account A ở 100; cho canWithdraw(30) trả TRUE mà không sửa bảng, sau đó withdraw(30) đổi sang 70. Bên cạnh có FeePolicy bất biến tính phí.

**Script:** Một đối tượng có trạng thái và các yêu cầu nó có thể đáp ứng. Hỏi canWithdraw chỉ đọc số dư để trả lời đúng hay sai. Gọi withdraw mới có thể cập nhật, nếu điều kiện cho phép. Một đối tượng chính sách phí lại có thể không thay đổi trạng thái chút nào mà vẫn biết tính phí. Vì vậy có hành vi không đồng nghĩa với luôn sửa dữ liệu; đối tượng bất biến cũng thuộc OOP.

**Purpose:** Phân biệt thao tác chỉ đọc, thao tác ghi và đối tượng bất biến.



## Đóng gói: Bảo vệ trạng thái nội bộ qua hành vi

<!-- VIDEO_SECTION -->

### Transition

**Time:** 02:41–02:54

**Visual:** Trên tài khoản đang xem, vẽ mũi tên WRITE trực tiếp từ caller màu đỏ rồi thay bằng mũi tên withdraw hướng tới ranh giới.

**Script:** Nếu một hành vi được phép sửa trạng thái, ai ngăn nó tạo ra giá trị không hợp lệ?

**Purpose:** Đưa lý do cần encapsulation từ quyền ghi quá rộng.

### Scene 3 — Đóng gói: Bảo vệ trạng thái nội bộ qua hành vi

**Time:** 02:54–04:08

**Visual:** Một caller bên ngoài gán account.balance=-50 bị gạch đỏ; chuyển sang mũi tên withdraw(50) tới Account với balance30 rồi nhận REJECTED và giữ 30.

**Script:** Giả sử màn hình có quyền tự gán số dư âm năm mươi, tài khoản sẽ không bảo vệ được quy tắc số dư không âm. Đóng gói tạo ranh giới để bên ngoài yêu cầu hành vi có nghĩa, như rút tiền, còn chủ sở hữu quyết định có cho cập nhật hay không. Đánh dấu private trong Java là một công cụ hỗ trợ; mục tiêu thật là bảo vệ quyền quyết định và quy tắc nghiệp vụ.

**Purpose:** Minh họa đóng gói như cơ chế bảo vệ quy tắc, không chỉ giấu biến.



## Điều kiện bất biến của đối tượng khi thực hiện hành vi

<!-- VIDEO_SECTION -->

### Transition

**Time:** 04:08–04:21

**Visual:** Gắn đồng hồ balance≥0 ngay trên ranh giới withdraw và chạy hai thẻ ACCEPT / REJECT cạnh nhau.

**Script:** Ranh giới đã được tạo. Điều gì phải luôn đúng sau một yêu cầu thay đổi trạng thái?

**Purpose:** Nối encapsulation với điều kiện có thể kiểm chứng.

### Scene 4 — Điều kiện bất biến của đối tượng khi thực hiện hành vi

**Time:** 04:21–05:35

**Visual:** Hiện đồng hồ balance≥0; rút 30 từ 100 cho 70 màu xanh; thử rút 120 từ 70 bị từ chối và ô -50 đỏ không bao giờ được ghi.

**Script:** Invariant là điều kiện phải đúng ở những thời điểm tài khoản công bố trạng thái hợp lệ. Ở đây số dư không được âm. Rút ba mươi từ một trăm xuống bảy mươi vẫn giữ quy tắc. Nhưng rút một trăm hai mươi từ bảy mươi thì phải thất bại và giữ bảy mươi, chứ không được ghi âm năm mươi rồi nói sẽ sửa sau. Invariant không phải lệnh cấm trạng thái thay đổi.

**Purpose:** Dùng một cập nhật hợp lệ và một yêu cầu bị từ chối để chứng minh invariant.



## Điều kiện nhất quán và dữ liệu không thể thay đổi

<!-- VIDEO_SECTION -->

### Transition

**Time:** 05:35–05:48

**Visual:** Giữ mệnh đề balance≥0 cố định trong lúc số dư đổi; cạnh đó đặt bản ghi âm bị đóng băng từ khi tạo.

**Script:** Một quy tắc không đổi khác hẳn một giá trị không đổi. Xem hai trường hợp đối lập nhé.

**Purpose:** Ngăn việc nhầm invariant với immutability.

### Scene 5 — Điều kiện nhất quán và dữ liệu không thể thay đổi

**Time:** 05:48–07:02

**Visual:** Một bên tài khoản mutable 100→70 nhưng balance≥0 vẫn xanh; bên kia bản ghi bất biến amount=-5 đóng băng nhưng có dấu INVALID đỏ.

**Script:** Hai khái niệm dễ bị gọi chung là bất biến. Invariant là quy tắc phải giữ đúng, chẳng hạn số dư không âm. Immutability là giá trị không được sửa sau khi tạo. Tài khoản từ một trăm còn bảy mươi vẫn hợp lệ dù có thay đổi. Ngược lại, một bản ghi giao dịch không cho sửa nhưng chứa số tiền âm trái quy tắc vẫn sai ngay từ đầu. Bất biến dữ liệu không thay việc kiểm tra tính hợp lệ.

**Purpose:** Tách rõ business invariant và immutable data bằng phản ví dụ có thể nhìn thấy.



## Ranh giới hành vi công bố và chi tiết bị che giấu

<!-- VIDEO_SECTION -->

### Transition

**Time:** 07:02–07:15

**Visual:** Xóa mũi tên setter vừa thấy, chỉ giữ withdraw và kết quả; cho chi tiết lưu trữ mờ dần khỏi góc nhìn caller.

**Script:** Bên ngoài không nên sửa trực tiếp; vậy nó cần được biết những lời hứa gì để sử dụng tài khoản?

**Purpose:** Đưa quy tắc bảo vệ thành giao diện hành vi có nghĩa.

### Scene 6 — Ranh giới hành vi công bố và chi tiết bị che giấu

**Time:** 07:15–08:29

**Visual:** Che ô balance, cách lưu ledger và thuật toán phí sau Account; chỉ hiển thị withdraw(amount)→SUCCESS/FAILURE, gạch đỏ setBalance từ ngoài.

**Script:** Người gọi cần nhìn thấy gì? Một yêu cầu rút có ý nghĩa và kết quả nó cam kết. Checkout không cần biết tài khoản lưu từng đồng bằng cách nào hay quản lý lịch sử ra sao. Ta có thể đổi cách lưu phía trong mà không sửa tất cả nơi gọi, miễn hợp đồng quan sát được vẫn đúng. Giấu chi tiết không có nghĩa được giấu những tác động phụ quan trọng khỏi người dùng.

**Purpose:** Cho thấy một hành vi công bố ổn định khi cấu trúc bên trong thay đổi.



## Trừu tượng hóa theo điều đối tượng có thể làm

<!-- VIDEO_SECTION -->

### Transition

**Time:** 08:29–08:42

**Visual:** Giữ hai kết quả withdraw ở mặt trước, thay phần triển khai bằng hợp đồng pay(amount) có những khả năng trả lời rõ.

**Script:** Khi đã che cách lưu, chúng ta còn cần diễn đạt lời hứa nào để nhiều cách làm cùng dùng được?

**Purpose:** Nối đóng gói sang hợp đồng trừu tượng.

### Scene 7 — Trừu tượng hóa theo điều đối tượng có thể làm

**Time:** 08:42–09:56

**Visual:** Checkout gửi pay(30) vào một hộp hợp đồng; dưới tấm che thay đường ví điện tử bằng chuyển khoản, nhưng vẫn hiện trạng thái SUCCESS/FAILURE.

**Script:** Trừu tượng hóa là nhìn vào điều đối tượng hứa làm, thay vì mọi bước bên trong. Checkout gửi pay ba mươi rồi nhận xác nhận hoặc lý do thất bại. Ví và chuyển khoản có thể xử lý khác nhau; bên gọi không nên bị buộc biết mọi trường dữ liệu và công thức phí. Nhưng một hàm tên doEverything lại quá mơ hồ. Lời hứa hữu ích phải có yêu cầu, điều kiện và kết quả rõ ràng.

**Purpose:** Biểu diễn abstraction bằng hành vi có thể kiểm chứng, chuẩn bị đa hình.
