---
video:
  url: ""
---

# Hàm thuần và khả năng thay thế biểu thức bằng giá trị

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

## Hàm thuần: Khái niệm và hành vi quan sát được

<!-- VIDEO_SECTION -->
### Scene 1 — Cùng dữ kiện, cùng phép tính

**Time:** `00:00–01:18`

**Visual:**

Đặt hai thẻ `giamGia(100,0.1)` trên dòng thời gian; hiện cùng `90`. Bên dưới hiển thị một đơn A không bị sửa, không có ghi log.

**Script:**

Ta có thể gọi phép giảm giá bao nhiêu lần cũng được: nếu giá và tỷ lệ không đổi, kết quả là 90. Còn một điều quan trọng nữa: phép tính không âm thầm sửa đơn hàng, gửi email hay ghi gì ra bên ngoài. Đó là hai dấu hiệu cần kiểm tra khi gọi một hàm là thuần. Việc tạo giá trị kết quả mới không phá tính thuần nếu người gọi không quan sát được tác động khác.

**Purpose:**

Biến tính thuần thành hai phép kiểm tra quan sát được, không chỉ là nhãn của hàm.

## Kết quả dự đoán được và chi phí của phụ thuộc ẩn

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:18–01:31`

**Visual:**

Ẩn đầu vào tỷ giá/khuyến mãi sau hậu cảnh để người xem thấy lỗ hổng.

**Script:**

Ta đã biết hai tiêu chí; giờ thử loại bỏ một dữ kiện khỏi chữ ký hàm.

**Purpose:**

Chứng minh chi phí của phụ thuộc bị che giấu.

### Scene 2 — Hàm không nói hết đầu vào

**Time:** `01:31–02:49`

**Visual:**

Chiếu `doiTien(100)` cạnh ô `tyGiaNgoai=23`; đổi ô lên 24, trả kết quả khác. Sau đó sửa chữ ký thành `doiTien(100,tyGia)` và cố định tham số 23.

**Script:**

Khi cùng lời gọi trả khác nhau, đừng vội kết luận máy tính chạy sai. Có thể tỷ giá đã được đọc từ nơi khác, hoặc đồng hồ đã thay đổi. Bài kiểm thử hôm qua không thể lặp lại nếu ta không tái tạo được bối cảnh. Truyền tỷ giá vào hàm giúp ta chụp đúng mọi dữ kiện mà kết quả phụ thuộc. Hàm thuần không hứa chạy nhanh hơn; nó giúp trả lời vì sao kết quả xuất hiện.

**Purpose:**

Đưa phụ thuộc ẩn thành sự khác biệt cụ thể giữa hai lần tính.

## Đầu vào tường minh, kết quả và trạng thái bên ngoài

<!-- VIDEO_SECTION -->
### Transition

**Time:** `02:49–03:02`

**Visual:**

Di chuyển sang đơn A có danh sách sản phẩm để soi thao tác viết ngầm.

**Script:**

Tỷ giá đã được đưa vào tham số; nhưng truyền tham chiếu cũng có thể che giấu việc sửa dữ liệu.

**Purpose:**

Phân biệt đầu vào có tên với không có tác động phụ.

### Scene 3 — Truyền dữ liệu không đồng nghĩa được phép sửa

**Time:** `03:02–04:20`

**Visual:**

Hiện `tinhTong(donHang,quyTac)`, đầu vào `donHang.items=[A,B]`; thử thao tác gạch đỏ `items.add(C)` bên trong hàm, rồi thay bằng `return ketQuaMoi`.

**Script:**

Một tham số ghi rõ trong chữ ký vẫn chưa đủ để gọi hàm là thuần. Nếu hàm nhận đơn hàng rồi âm thầm thêm món C vào danh sách mà người gọi đang giữ, trạng thái bên ngoài đã thay đổi. Hãy lấy dữ liệu từ mạng hoặc cấu hình ở ranh giới, đưa một ảnh chụp ổn định vào phần tính, rồi chỉ trả về kết quả. Người kiểm thử khi ấy không cần biết kho hàng hay máy chủ đã làm gì.

**Purpose:**

Tách tính tường minh của dữ kiện khỏi quyền chỉnh sửa đối tượng được truyền.

## Tính thay thế biểu thức bằng giá trị

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:20–04:33`

**Visual:**

Biến hai khối `gapDoi(4)` thành hai số 8 để làm rõ sự thay thế.

**Script:**

Bây giờ ta có một phép tính ổn định; thử thay lời gọi bằng chính kết quả của nó.

**Purpose:**

Chuyển từ định nghĩa purity sang phép suy luận tham chiếu.

### Scene 4 — Thay lời gọi bằng giá trị

**Time:** `04:33–05:51`

**Visual:**

Reveal `gapDoi(4)+gapDoi(4)` → `8+8` → `16`. Cạnh đó đặt `docDongHo()` rồi giữ biểu tượng thời gian đang chạy để đánh dấu không thể thay như vậy.

**Script:**

Nếu `gapDoi(4)` luôn là 8 và không có tác động bên ngoài, ta có quyền thay hai lần gọi bằng số 8. Tổng vẫn là 16; hành vi quan sát được không đổi. Với một hàm đọc đồng hồ, hai lần gọi có thể trả thời điểm khác nhau, nên thay cả hai bằng một giá trị là sai. Đây là tính minh bạch tham chiếu. Nó hỗ trợ suy luận về biểu thức, nhưng không xoá các quy tắc làm tròn số thực.

**Purpose:**

Làm phép thay thế trực tiếp có chứng cứ và một phản ví dụ thời gian.

## Vi phạm tính thuần và phép tính dễ kiểm thử

<!-- VIDEO_SECTION -->
### Transition

**Time:** `05:51–06:04`

**Visual:**

Tách màn hình thành phép tính quy đổi và cổng tỷ giá đang kết nối mạng.

**Script:**

Ta có thể suy luận thuần; vậy một ứng dụng có cần loại bỏ hoàn toàn I/O không?

**Purpose:**

Handoff về boundaries mà không biến phần điều phối thành hàm thuần.

### Scene 5 — Một phép tính, hai loại kiểm thử

**Time:** `06:04–07:22`

**Visual:**

Chia flow thành `đọc tỷ giá [I/O]` | `đổi(100,23) [thuần]` | `lưu kết quả [I/O]`. Bật trạng thái `timeout` ở ô mạng, giữ phép nhân 100×23 không đổi.

**Script:**

Một hàm đọc tỷ giá trực tuyến hoặc ghi tồn kho không thuần chỉ vì nó trả số tiền. Nhưng ta vẫn có thể tách nó ra: phần kết nối lấy tỷ giá ở ngoài; phép tính nhận tỷ giá đã biết và thực hiện chuyển đổi; phần lưu nằm ở phía sau. Kiểm thử phép nhân dùng dữ liệu cố định. Kiểm thử kết nối phải giả lập timeout, từ chối và lỗi mạng. Lời hứa không phải toàn bộ chương trình sẽ thuần, mà là phần nào có thể chứng minh độc lập.

**Purpose:**

Chỉ ra cách kiểm thử từng loại tác động và ranh giới được học ở chương sau.
