---
video:
  url: ""
---

# Đánh đổi thiết kế hướng đối tượng và phạm vi áp dụng

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


## Thiết kế ví dụ từ yêu cầu đến nhóm đối tượng cộng tác

<!-- VIDEO_SECTION -->

### Scene 1 — Thiết kế ví dụ từ yêu cầu đến nhóm đối tượng cộng tác

**Time:** 00:00–01:14

**Visual:** Bảng TransferCoordinator giữa Account A=100 và B=20; hiện VALIDATE→DEBIT30 A=70→CREDIT30 B=50→CONFIRM. Nhánh CREDIT FAIL đứng ở A70/B20.

**Script:** Bây giờ ghép các ý vào một ví dụ hoàn chỉnh. Ta chuyển ba mươi từ tài khoản A đang có một trăm sang B đang có hai mươi. Tài khoản quản lý quy tắc số dư, bộ điều phối gọi trừ A rồi cộng B, bộ thông báo chỉ báo kết quả. Thành công thì A còn bảy mươi, B có năm mươi. Nếu trừ A xong nhưng cộng B lỗi, trạng thái đang dở dang. OOP giúp chia trách nhiệm, không tự rollback hay tạo tính nguyên tử.

**Purpose:** Chứng minh luồng 100/20→70/50 và nhánh thất bại không có phép màu giao dịch.



## Lợi ích và chi phí của ranh giới đóng gói

<!-- VIDEO_SECTION -->

### Transition

**Time:** 01:14–01:27

**Visual:** Giữ vết A100/B20→A70/B50 trên màn hình, chỉ thay các ô lưu trữ bên trong Account thành ledger.

**Script:** Luồng chuyển tiền có cần sửa không khi tài khoản đổi cách lưu số dư mà vẫn giữ lời hứa?

**Purpose:** Từ cộng tác có kết quả chuyển sang lợi ích đóng gói.

### Scene 2 — Lợi ích và chi phí của ranh giới đóng gói

**Time:** 01:27–02:41

**Visual:** Giữ sơ đồ TransferCoordinator→Account, thay cách lưu balance field thành ledger bên trong Account; quan sát cùng hợp đồng withdraw/deposit và số dư cuối.

**Script:** Một khi tài khoản chịu trách nhiệm cho số dư, ta có thể đổi cách lưu nội bộ mà không bắt bộ điều phối biết tên trường hay định dạng sổ cái. Bên ngoài vẫn gọi rút, nạp và nhận kết quả đúng hợp đồng. Đó là lợi ích của đóng gói. Đổi lại, nếu có quá nhiều lớp quá mỏng, ta lại khó tìm nơi lỗi xảy ra. Ranh giới nên được giữ khi bảo vệ quy tắc hoặc cô lập một thay đổi có thật.

**Purpose:** Chứng minh lợi ích thay đổi nội bộ và chi phí thêm lớp.



## Lạm dụng đối tượng và phân tán trách nhiệm

<!-- VIDEO_SECTION -->

### Transition

**Time:** 02:41–02:54

**Visual:** Giữ ranh giới Account hữu ích, nhưng thử chèn nhiều lớp rỗng vào vết chuyển tiền; lần lượt xóa những lớp không thêm ý nghĩa.

**Script:** Khi nào ranh giới giúp dễ sửa, và khi nào nó chỉ làm tăng số nơi phải lần theo?

**Purpose:** Dẫn từ lợi ích sang giới hạn overengineering.

### Scene 3 — Lạm dụng đối tượng và phân tán trách nhiệm

**Time:** 02:54–04:08

**Visual:** Đưa EverythingManager ôm số dư, phí, email, báo cáo; bên cạnh chuỗi năm wrapper chỉ để cộng hai số; gỡ lớp dư trả về Account/Coordinator/Notifier.

**Script:** OOP cũng có thể bị lạm dụng. Một EverythingManager vừa giữ tiền vừa gửi mail khiến mỗi lý do thay đổi cùng đổ vào một chỗ. Chiều ngược lại, quá nhiều đối tượng chỉ chuyển tiếp một phép cộng nhỏ cũng làm chương trình nặng nề. Hãy hỏi từng ranh giới để làm gì: bảo vệ invariant, tách trách nhiệm hay thay một hành vi thật? Nếu không có câu trả lời rõ, một hàm đơn giản có thể tốt hơn.

**Purpose:** Đánh giá nguy cơ god object và số lớp trung gian vô nghĩa.



## Quan hệ kế thừa mong manh và vi phạm hợp đồng

<!-- VIDEO_SECTION -->

### Transition

**Time:** 04:08–04:21

**Visual:** Đổi sơ đồ các wrapper thừa thành cây kế thừa, rồi tô đỏ một child đổi hành vi sau khi cha sửa chính sách phí.

**Script:** Giảm lặp bằng inheritance có thể làm những thay đổi ở cha lan tới con thế nào?

**Purpose:** Đưa đánh đổi cây kế thừa vào ví dụ thay đổi có thể quan sát.

### Scene 4 — Quan hệ kế thừa mong manh và vi phạm hợp đồng

**Time:** 04:21–05:35

**Visual:** Kiểu cha Payment thêm phí 5, kiểu con vốn nghĩ phí=0 bị đổi tổng ngoài ý muốn. Cảnh kế chuyển sang provider FAIL mà con trả SUCCESS giả.

**Script:** Một cây kế thừa có thể mong manh khi con phụ thuộc chi tiết mà cha thay đổi. Cha thêm phí mới, kiểu con tưởng mức phí luôn bằng không nên kết quả sai. Tệ hơn là kiểu con ném lỗi không đúng hợp đồng hoặc báo thanh toán thành công khi thực tế thất bại. Dùng chung code không giải quyết được những rủi ro này. Ta cần kiểm tra lại cam kết từ góc nhìn caller và cân nhắc composition khi quan hệ kiểu không thật.

**Purpose:** Tách lỗi phụ thuộc triển khai và lỗi phá hợp đồng hành vi.



## Rủi ro chia sẻ trạng thái thay đổi giữa các đối tượng

<!-- VIDEO_SECTION -->

### Transition

**Time:** 05:35–05:48

**Visual:** Rời khỏi cây kế thừa, nối hai hộp Report và Checkout vào một ô trạng thái chung đang đổi giá trị.

**Script:** Ngay cả các đối tượng không có quan hệ kế thừa vẫn có thể ảnh hưởng nhau qua dữ liệu chung.

**Purpose:** Khép mạch về coupling bằng trạng thái chia sẻ.

### Scene 5 — Rủi ro chia sẻ trạng thái thay đổi giữa các đối tượng

**Time:** 05:48–07:02

**Visual:** Report và Checkout cùng trỏ tới một ô balance mutable; Checkout ghi 70 trong khi Report đang dùng snapshot100. Đặt biển single flow ≠ thread safety.

**Script:** Hai đối tượng có thể cùng tham chiếu tới dữ liệu cho phép sửa. Nếu một bên âm thầm cập nhật, bên kia đang dùng ảnh chụp cũ sẽ khó giải thích kết quả. Chúng ta cần giới hạn ai được ghi và khi nào phải đọc lại hoặc dùng bản sao phù hợp. Nhưng đây chưa phải cơ chế khóa luồng. Dùng OOP không tự bảo đảm an toàn khi nhiều luồng chạy hoặc nhiều dịch vụ cần phối hợp giao dịch.

**Purpose:** Nêu rủi ro state chung và đặt đúng giới hạn concurrency/distributed coordination.



## Trường hợp thủ tục hoặc phép biến đổi dữ liệu đơn giản hơn

<!-- VIDEO_SECTION -->

### Transition

**Time:** 07:02–07:15

**Visual:** Gập sơ đồ chia sẻ trạng thái thành danh sách khoản thu cố định rồi đối chiếu hàm cộng trực tiếp với hệ phân cấp nhiều lớp.

**Script:** Không phải bài toán nào cũng cần bảo vệ trạng thái như tài khoản; vậy khi nào chỉ cần một phép tính?

**Purpose:** Dùng phản ví dụ để chỉ trường hợp OOP không đáng chi phí.

### Scene 6 — Trường hợp thủ tục hoặc phép biến đổi dữ liệu đơn giản hơn

**Time:** 07:15–08:29

**Visual:** So sánh totalReceipts([10,20,5])→35 bằng một hàm với cây sáu đối tượng phải gọi nhau để làm cùng phép cộng; rồi trở về Account có invariant thật.

**Script:** Nếu yêu cầu chỉ là tính tổng ba khoản thu không thay đổi, một hàm trả ba mươi lăm có thể dễ hiểu nhất. Ta không cần sáu đối tượng để bọc một phép cộng. Mô hình đối tượng phát huy khi có danh tính, quy tắc trạng thái và trách nhiệm cộng tác đáng bảo vệ. Còn lệnh tuần tự, phép tính thuần hay mô tả khai báo vẫn có thể nằm bên trong một ứng dụng hướng đối tượng. Không cần tuyệt đối hóa một phong cách.

**Purpose:** Chọn mô hình dựa trên bài toán, không biến OOP thành quota.



## Ranh giới với Java Core, DDD, AOP và các paradigm khác

<!-- VIDEO_SECTION -->

### Transition

**Time:** 08:29–08:42

**Visual:** Giữ hàm tính khoản thu đơn giản cạnh vết chuyển tiền nhiều đối tượng, rồi hiện ba câu hỏi và bốn tuyến học tiếp.

**Script:** Bài toán khác nhau cần mức mô hình hóa khác nhau, không có thiết kế OOP chung cho mọi trường hợp.

**Purpose:** Bàn giao chính xác sang các cơ chế ngôn ngữ và kiến trúc chuyên biệt.

### Scene 7 — Ranh giới với Java Core, DDD, AOP và các paradigm khác

**Time:** 08:42–09:56

**Visual:** Bảng ba câu hỏi: ai sở hữu quy tắc? bên gọi kỳ vọng gì? nếu cộng tác viên lỗi thì sao? Bên dưới gắn Java Core, DDD, AOP và Transaction làm các tuyến học tiếp.

**Script:** Sau cả loạt bài, hãy giữ ba câu hỏi. Ai chịu trách nhiệm bảo vệ trạng thái? Người gọi có thể tin vào hợp đồng kết quả nào? Và nếu một cộng tác viên thất bại giữa chừng thì chuyện gì xảy ra? OOP giúp ta phân chia vai trò và nhìn rõ lời hứa. Cú pháp Java học sâu ở Java Core; ranh giới miền ở DDD; concern xuyên suốt ở AOP; giao dịch nguyên tử thuộc module Transaction. Đừng lấy số lượng class làm thước đo thiết kế tốt.

**Purpose:** Tổng kết qua bộ câu hỏi ứng dụng và ranh giới với module khác.
