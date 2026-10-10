---
video:
  url: ""
---

# Tổ chức chương trình theo thủ tục

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

## Thủ tục và mục đích gom nhóm thao tác

<!-- VIDEO_SECTION -->

### Scene 1 — Thủ tục và mục đích gom nhóm thao tác

**Time:** 00:00–01:20

**Visual:** Biến bốn thẻ kiểm tra, trừ, ghi log và báo kết quả thành khung withdraw(amount), rồi cho hai caller gọi chung một khung.

**Script:** Thay vì sao chép các bước rút tiền vào nhiều nơi, ta đặt chúng trong một thủ tục mang tên withdraw. Mỗi lần gọi vẫn thực hiện các lệnh tuần tự; điều mới là một ranh giới có tên và có một nhiệm vụ rõ. Một thủ tục không nên tồn tại chỉ vì vài dòng quá dài: nó phải giúp người đọc nhận ra công việc nào được thực hiện và phần nào có thể được kiểm thử độc lập.

**Purpose:** Nối phép điều khiển luồng với tổ chức mã có trách nhiệm.

## Đầu vào, đầu ra và hợp đồng của thủ tục

<!-- VIDEO_SECTION -->

### Transition

**Time:** 01:20–01:33

**Visual:** Nén chuỗi CHECK/DEBIT/REPORT thành hộp `withdraw(amount)`; trên viền bật `amount>0`, `đủ tiền`, `giữ nguyên nếu lỗi`.

**Script:** Có tên thủ tục chưa đủ; người gọi phải biết dữ liệu hợp lệ và kết quả nó cam kết.

**Purpose:** Từ thủ tục có tên sang lời hứa đầu vào và hậu điều kiện với người gọi.

### Scene 2 — Đầu vào, đầu ra và hợp đồng của thủ tục

**Time:** 01:33–02:48

**Visual:** Hiện thẻ hợp đồng withdraw(amount): amount>0, đủ số dư, success giảm đúng amount, failure giữ nguyên. Chạy thử 30 rồi 90.

**Script:** Giả sử người gọi không được xem thân thủ tục. Họ vẫn cần biết số tiền phải dương và tài khoản phải đủ tiền. Rút 30 từ 100 phải còn 70. Yêu cầu 90 ngay sau đó phải trả lý do thất bại mà không trừ thêm. Đó là tiền điều kiện, kết quả và tác động được phép của một thủ tục. Nếu còn ghi log hay cập nhật dịch vụ ngoài, hợp đồng cũng phải mô tả điều có thể quan sát ấy.

**Purpose:** Làm rõ hợp đồng thủ tục qua ca thành công và bị từ chối.

## Phạm vi dữ liệu và trạng thái cục bộ

<!-- VIDEO_SECTION -->

### Transition

**Time:** 02:48–03:01

**Visual:** Giữ hai vết 100→70 và 70→70 ở dải LỊCH SỬ đã làm mờ; mở một LẦN GỌI MỚI đặt lại `account.balance=100`, tính thử `newBalance=70` trong LOCAL nhưng chưa bật WRITE.

**Script:** Bên trong thủ tục, ta có thể tính thử số mới trước khi thật sự sửa tài khoản.

**Purpose:** Nối hợp đồng rút tiền với khác biệt giữa dữ liệu thử tính và trạng thái dùng chung.

### Scene 3 — Phạm vi dữ liệu và trạng thái cục bộ

**Time:** 03:01–04:16

**Visual:** Tiếp tục LẦN GỌI MỚI từ `account.balance=100`: đặt `newBalance=70` trong thủ tục, giữ ô shared ở 100 cho đến khi hiện mũi tên WRITE hợp lệ mới chuyển sang 70.

**Script:** Tôi tính số dư dự kiến 70 vào biến newBalance cục bộ. Việc này chưa sửa tài khoản, vì ô account.balance vẫn là 100. Chỉ bước ghi được phép mới thay đổi trạng thái tài khoản. Mỗi lần gọi thủ tục có phần tính trung gian của riêng nó, dù cùng dùng tên biến. Phân biệt dữ liệu tạm và dữ liệu dùng chung giúp ta biết bước nào đang thử tính, bước nào gây tác động thật.

**Purpose:** Dùng hai ô lưu trữ để nhận biết local state và shared state.

## Tác động phụ khi gọi thủ tục

<!-- VIDEO_SECTION -->

### Transition

**Time:** 04:16–04:29

**Visual:** Khoanh một mũi tên WRITE trên viền thủ tục, rồi xuất hiện ba đèn RETURN/PRINT/WRITE chưa kích hoạt.

**Script:** Ngoài giá trị trả về, thủ tục còn có thể làm điều gì khiến phần khác quan sát được?

**Purpose:** Đặt câu hỏi về tác động quan sát được khác với giá trị thủ tục trả về.

### Scene 4 — Tác động phụ khi gọi thủ tục

**Time:** 04:29–05:44

**Visual:** Dựng ba biểu tượng RETURN, PRINT, WRITE. Cho displayBalance chỉ đọc rồi bật bản lỗi vừa hiển thị vừa trừ phí 5.

**Script:** Một thủ tục có thể trả số 70, nhưng đồng thời in thông báo, ghi file hoặc sửa trạng thái chung. Những hành động có thể quan sát ấy là tác động phụ. Hãy thử một phiên bản lỗi: displayBalance mỗi lần chạy lại trừ phí. Khi gọi để xem tài khoản, ta vô tình thay dữ liệu. Điều quan trọng không phải cấm tất cả tác động, mà là làm chúng rõ ràng trong tên, hợp đồng và vết chạy.

**Purpose:** Cho thấy khác biệt giữa giá trị trả về và các tác động bên ngoài.

## Phân rã lời giải thành các thủ tục cộng tác

<!-- VIDEO_SECTION -->

### Transition

**Time:** 05:44–05:57

**Visual:** Tách ba biểu tượng RETURN/PRINT/WRITE thành năm nhãn validate/check/debit/credit/record, làm sáng khoảng trống giữa debit và credit.

**Script:** Một nghiệp vụ lớn cần nhiều thủ tục nhỏ; tuy nhiên sự chia nhỏ có tự đảm bảo an toàn toàn bộ quy trình không?

**Purpose:** Từ tác động của một lời gọi chuyển sang điểm thất bại khi phối hợp nhiều lời gọi.

### Scene 5 — Phân rã lời giải thành các thủ tục cộng tác

**Time:** 05:57–07:12

**Visual:** Vẽ validate→check→debit A→credit B→record. Làm bước credit thất bại và tô đỏ trạng thái sau debit A.

**Script:** Chuyển tiền có thể chia thành kiểm tra, trừ nguồn, cộng đích và lưu kết quả. Nhờ những tên này, ta biết nhiệm vụ nào sai khi vết chạy dừng. Nhưng nếu đã trừ tiền ở A mà chưa cộng được ở B, chỉ việc chia mã thành thủ tục không tạo ra giao dịch nguyên tử. Ta còn cần quy tắc phục hồi cho thất bại giữa chừng. Video này dừng ở cách nhìn trình tự và điểm lỗi, không nhận thay nội dung về transaction thực tế.

**Purpose:** Chứng minh ranh giới tổ chức tốt không thay thế tính nguyên tử.

## Giới hạn của thủ tục phụ thuộc trạng thái bên ngoài

<!-- VIDEO_SECTION -->

### Transition

**Time:** 07:12–07:25

**Visual:** Dừng hình ngay sau debit A nhưng trước credit B; đặt ô `global balance` cạnh lần gọi kế tiếp với mũi tên đọc dữ liệu không đi qua tham số.

**Script:** Các thủ tục nhìn gọn, song nếu chúng đọc biến toàn cục ngầm thì hợp đồng vẫn khó dự đoán.

**Purpose:** Nối lỗi giữa các thủ tục với phụ thuộc trạng thái ẩn mà chữ ký không kể ra.

### Scene 6 — Giới hạn của thủ tục phụ thuộc trạng thái bên ngoài

**Time:** 07:25–08:40

**Visual:** Đặt hai lần withdraw(30) cạnh global balance=100 rồi 70. Tô mũi tên đọc global và hiện kết quả khác nhau sau mỗi lời gọi.

**Script:** Hai lần gọi withdraw với cùng tham số 30 không hẳn cho cùng hậu quả. Lần thứ nhất có thể đọc số dư 100, lần sau đọc 70 vì thủ tục giữ trạng thái bên ngoài. Nếu phụ thuộc ấy không xuất hiện trong hợp đồng, người đọc sẽ khó giải thích khác biệt. Ta có thể truyền giá trị cần dùng rõ ràng, giới hạn nơi được phép cập nhật, và ghi nhận tác động thật. Mục tiêu là kiểm soát mutation, không giả vờ mọi thủ tục đều thuần.

**Purpose:** Bóc tách coupling do trạng thái ẩn khi cùng lời gọi cho hành vi khác.
