---
video:
  url: ""
---

# Quan hệ kiểu, kế thừa và đa hình trong mô hình đối tượng

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


## Đa hình: Khái niệm và động cơ thay đổi hành vi

<!-- VIDEO_SECTION -->

### Scene 1 — Đa hình: Khái niệm và động cơ thay đổi hành vi

**Time:** 00:00–01:14

**Visual:** Checkout gọi pay(30), phía dưới có WalletPayment và BankPayment. Gạch nhánh if-wallet/else-bank ở caller.

**Script:** Một ứng dụng thanh toán có thể hỗ trợ ví điện tử và chuyển khoản. Nếu Checkout liên tục phải hỏi loại thanh toán rồi tự chạy nhánh tương ứng, mỗi phương thức mới đều buộc sửa caller. Đa hình cho phép gửi cùng một yêu cầu pay ba mươi, còn từng đối tượng thực hiện theo cách riêng. Nhưng hai bên phải cùng giữ hợp đồng kết quả, không được nói thành công khi thực tế thanh toán thất bại.

**Purpose:** Đưa ra động cơ đa hình từ caller ổn định và cách làm nội bộ khác nhau.



## Hợp đồng chung cho nhiều kiểu thực hiện

<!-- VIDEO_SECTION -->

### Transition

**Time:** 01:14–01:27

**Visual:** Giữ pay(30) ở giữa, kéo các dòng success, decline và invalid lên đầu cả hai nhánh triển khai.

**Script:** Có nhiều phương thức rồi. Phần nào phải giống nhau để Checkout dùng được chúng như nhau?

**Purpose:** Nối đa hình với hợp đồng quan sát được.

### Scene 2 — Hợp đồng chung cho nhiều kiểu thực hiện

**Time:** 01:27–02:41

**Visual:** Bảng ba dòng: tiền dương và xử lý xong→mã xác nhận; tiền dương bị từ chối→FAILURE/lý do; tiền không hợp lệ→INVALID. Gắn dưới cả ví và ngân hàng.

**Script:** Trước khi so sánh các kiểu triển khai, hãy viết lời hứa chung. Số tiền dương được tiếp nhận để xử lý, chứ không phải luôn thanh toán thành công. Nếu xử lý xong, trả mã xác nhận. Nếu nhà cung cấp từ chối, trả thất bại có lý do. Số tiền âm hoặc bằng không đi theo đường báo đầu vào sai. Hai phương thức có thể xử lý bên trong khác nhau, nhưng bên gọi cần đúng những kết quả đã công bố này.

**Purpose:** Không nhầm đầu vào đủ điều kiện xử lý với cam kết luôn thành công.



## Quan hệ kiểu và lời hứa của kiểu con

<!-- VIDEO_SECTION -->

### Transition

**Time:** 02:41–02:54

**Visual:** Giữ thẻ hợp đồng số tiền dương, chiếu hai đường đi của cùng ví: báo từ chối hợp lệ và ngoại lệ mới không có trong lời hứa.

**Script:** Cùng tên pay chưa đủ; kiểu con có được tự yêu cầu điều kiện đầu vào chặt hơn không?

**Purpose:** Đưa hợp đồng chung sang nghĩa vụ của subtype.

### Scene 3 — Quan hệ kiểu và lời hứa của kiểu con

**Time:** 02:54–04:08

**Visual:** Cùng hợp đồng PaymentMethod tiếp nhận tiền dương; Wallet trả DECLINED đúng hợp đồng thì xanh, ném lỗi UnsupportedAmount không khai báo cho pay30 thì đỏ.

**Script:** Kiểu con có nghĩa là dùng được ở nơi yêu cầu hợp đồng của kiểu rộng hơn. Nếu hợp đồng chung nhận mọi số tiền dương để xử lý, kiểu ví không thể âm thầm tự đặt thêm điều kiện tối thiểu năm mươi rồi ném ngoại lệ ngoài hợp đồng khi nhận ba mươi. Nhưng nếu nhà cung cấp thực sự từ chối và nó báo thất bại đúng quy ước thì vẫn hợp lệ. Lỗi nghiệp vụ được báo đúng khác với việc phá lời hứa nhận đầu vào.

**Purpose:** Dạy khả năng thay thế và điều kiện đầu vào bị siết trái hợp đồng.



## Kế thừa như một cách tổ chức và tái sử dụng

<!-- VIDEO_SECTION -->

### Transition

**Time:** 04:08–04:21

**Visual:** Tách mũi tên subtype vừa vẽ thành một mũi tên tái sử dụng mã và một mũi tên kiểm thử cam kết.

**Script:** Quan hệ cấu trúc có thể chia sẻ mã, nhưng liệu hành vi bên ngoài đã được bảo đảm chưa?

**Purpose:** Nối subtype với kế thừa và giới hạn của kế thừa.

### Scene 4 — Kế thừa như một cách tổ chức và tái sử dụng

**Time:** 04:21–05:35

**Visual:** Vẽ kiểu cha Payment có code tính phí màu xanh, con Wallet tái sử dụng; cạnh đó bảng kiểm hợp đồng màu khác, hai mũi tên tách biệt.

**Script:** Kế thừa giúp một số ngôn ngữ tổ chức hoặc tái sử dụng mã giữa các kiểu. Nhưng có chung đoạn tính phí không chứng minh kiểu con thay thế đúng kiểu cha. Nếu nó đổi quy tắc chấp nhận tiền, bên gọi có thể gặp lỗi dù tất cả phương thức vẫn biên dịch được. Hãy hỏi riêng hai điều: mã dùng chung có hợp lý không, và hợp đồng của kiểu rộng hơn có được giữ không?

**Purpose:** Phân biệt reuse và substitutability thay vì gộp chung vào một quan hệ.



## Khả năng thay thế và các kỳ vọng hành vi

<!-- VIDEO_SECTION -->

### Transition

**Time:** 05:35–05:48

**Visual:** Bỏ cây thừa kế qua một bên, đưa cùng bốn ca thử tới từng đối tượng mà không sửa tiêu chí đạt.

**Script:** Dùng lại mã của cha không có nghĩa mọi người gọi đều nhận kết quả hợp lệ.

**Purpose:** Từ reuse chuyển sang bằng chứng behavioral substitution.

### Scene 5 — Khả năng thay thế và các kỳ vọng hành vi

**Time:** 05:48–07:02

**Visual:** Cho Wallet và Bank cùng chạy bốn thẻ hợp đồng: thành công, từ chối, tiền âm, lỗi provider. Gạch trường hợp provider FAIL nhưng trả SUCCESS.

**Script:** Muốn biết hai cách thanh toán có thay thế nhau được không, hãy chạy cùng các tình huống mà caller được phép kỳ vọng. Khi thành công phải có xác nhận. Khi nhà cung cấp từ chối phải báo lỗi rõ. Khi đầu vào không hợp lệ phải theo đúng kết quả đã nêu. Một biến thể trả SUCCESS giả sau khi provider thất bại đã phá hợp đồng, dù nó có cùng chữ ký phương thức với kiểu cha.

**Purpose:** Đưa substitutability từ khẩu hiệu sang test các kết quả quan sát.



## Lựa chọn hợp thành đối tượng hay quan hệ kế thừa

<!-- VIDEO_SECTION -->

### Transition

**Time:** 07:02–07:15

**Visual:** Tách phần tính phí khỏi lớp Payment, gắn nó bằng mũi tên has-a và cho thay thẻ FeePolicy trực tiếp.

**Script:** Không phải mọi điểm biến đổi đều cần một kiểu con của cả phương thức thanh toán.

**Purpose:** Giúp người học quyết định composition versus inheritance.

### Scene 6 — Lựa chọn hợp thành đối tượng hay quan hệ kế thừa

**Time:** 07:15–08:29

**Visual:** PaymentService HAS FeePolicy có mũi tên; vẽ FeePolicy IS-A PaymentMethod bị gạch. Hoán đổi thẻ chính sách mà không thay cây lớp.

**Script:** Một dịch vụ thanh toán có chính sách phí, nhưng chính sách phí không phải là một loại phương thức thanh toán. Đây là quan hệ có cộng tác viên, phù hợp với composition. Kế thừa nên dành cho nơi kiểu con thực sự đáp ứng hợp đồng kiểu cha, không chỉ vì hai bên có vài dòng giống nhau. Dùng hợp thành cũng cần chọn lọc, đừng xếp nhiều lớp trung gian chỉ để cộng một con số.

**Purpose:** Chọn has-a và is-a theo nghĩa hợp đồng, không theo nhu cầu giảm trùng mã.



## Mở rộng biến thể hành vi qua đa hình

<!-- VIDEO_SECTION -->

### Transition

**Time:** 08:29–08:42

**Visual:** Giữ nguyên caller và bộ test hợp đồng, kéo biến thể FastTransfer vào hàng bên dưới rồi bật lại từng ca thử.

**Script:** Nếu có thêm cách thanh toán mới, ta có thể mở rộng mà không sửa từng nơi gọi không?

**Purpose:** Kết thúc chương bằng mở rộng có giới hạn bởi hợp đồng.

### Scene 7 — Mở rộng biến thể hành vi qua đa hình

**Time:** 08:42–09:56

**Visual:** Thêm FastTransferPayment bên cạnh Wallet và Bank; caller pay(amount) giữ nguyên, chạy lại cùng bảng kiểm bốn trường hợp.

**Script:** Ngày mai có thêm chuyển khoản nhanh. Nếu thiết kế hợp lý, Checkout vẫn gọi pay như cũ và xử lý cùng dạng xác nhận hoặc thất bại. Cách thanh toán mới có thể triển khai khác bên trong, nhưng phải vượt bộ test hợp đồng đã có. Đây mới là giá trị của đa hình ở một điểm thực sự cần biến đổi. Còn nếu chỉ cộng hai số thì dựng nguyên một hệ phân cấp sẽ khiến bài toán khó đọc hơn.

**Purpose:** Đưa biến thể mới vào mà không làm yếu lời hứa của caller.
