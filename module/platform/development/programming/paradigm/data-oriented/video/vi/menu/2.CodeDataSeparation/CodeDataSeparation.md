---
video:
  url: ""
---

# Tách hành vi xử lý khỏi dữ liệu

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

## Giá trị dữ liệu và hàm xử lý có trách nhiệm riêng

<!-- VIDEO_SECTION -->
### Scene 1 — Giữ giá trị riêng, tính toán riêng

**Time:** `00:00–01:17`

**Visual:**

Đặt map `{id:A,lines:[...]}` ở trái và thẻ `tinhTong(order)` ở phải. Một đường nét đứt nối map tới hàm, không vẽ bất kỳ method nào trên map.

**Script:**

Dữ liệu đơn A chỉ nói các giá trị mà chúng ta có: id, trạng thái và danh sách dòng hàng. Quy tắc cộng số lượng nhân đơn giá không cần được nhúng thành phương thức của từng map. Ta có thể truyền cùng giá trị cho `tinhTong` hoặc `taoBaoCao`. Tách trách nhiệm như vậy làm phép tính dễ dùng lại và kiểm tra bằng đầu vào cố định, nhưng không tự động khiến mọi hàm trở thành thuần nếu nó đọc trạng thái khác.

**Purpose:**

Biểu diễn rõ code/data separation mà không đánh đồng với purity.

## Đối tượng giữ hành vi và dữ liệu có thể tái sử dụng

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:17–01:30`

**Visual:**

Giữ map hiện có; chiếu ngắn icon ổ khóa của đối tượng sang một bên.

**Script:**

Map đã tách khỏi tính tổng; khi một đối tượng có hành vi hữu ích thì phải làm sao?

**Purpose:**

Phân biệt việc tái sử dụng dữ liệu với encapsulation quan trọng.

### Scene 2 — Khi đối tượng vẫn là lựa chọn tốt

**Time:** `01:30–02:47`

**Visual:**

Hai cột: `Order.withdraw()/changeStatus()` bảo vệ điều kiện riêng; `orderData → total/report` cung cấp phép đọc. Dùng dấu tích ở cả hai phía trong các trường hợp phù hợp.

**Script:**

Một đối tượng có thể bảo vệ invariant hoặc điều phối thay đổi đúng thẩm quyền; đó là lý do hợp lệ để cho nó giữ hành vi. Nhưng dữ liệu phục vụ báo cáo hay phân tích có thể cần nhiều phép xử lý độc lập. Ta không nên tháo bỏ mọi lớp chỉ vì muốn map. Câu hỏi ở đây là hành vi có thực sự thuộc trách nhiệm của đối tượng hay đang chỉ làm khó việc quan sát và biến đổi dữ liệu chung?

**Purpose:**

Đặt giới hạn rõ cho nguyên tắc Sharvit thay vì quảng cáo thay thế mọi OOP.

## Quan sát và chia sẻ dữ liệu giữa các phép xử lý

<!-- VIDEO_SECTION -->
### Transition

**Time:** `02:47–03:00`

**Visual:**

Đưa hai nhánh READ cùng trỏ về một snapshot A; nhánh WRITE bị giữ lại ở ranh giới.

**Script:**

Hai cách tổ chức đều hợp lý trong ngữ cảnh riêng; thử cho nhiều người cùng xem một giá trị.

**Purpose:**

Minh hoạ lợi ích quan sát và chia sẻ dữ liệu.

### Scene 3 — Một dữ liệu, nhiều cách đọc

**Time:** `03:00–04:17`

**Visual:**

Bên trái snapshot A `P×2×30, Q×1×40`; bên phải song song `tinhTong=100` và `demDong=2`. Khi cả hai chạy, snapshot không đổi.

**Script:**

Nếu báo cáo cần số dòng, kết quả là hai; nếu tính tiền, tổng là một trăm. Cả hai có thể đọc cùng biểu diễn mà không biết cách nhau thực thi. Cơ hội tái sử dụng tăng khi tên trường và ý nghĩa của chúng rõ ràng. Tuy nhiên, một map dùng chung có thể bị sửa nếu không kiểm soát quyền ghi. Vì vậy ở các chương sau ta sẽ tạo ảnh chụp bất biến và kiểm tra cấu trúc ở ranh giới.

**Purpose:**

Cho thấy hai truy vấn độc lập cùng đọc dữ liệu và lý do cần immutability/schema.

## Đánh đổi về đóng gói và phụ thuộc khi tách code khỏi dữ liệu

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:17–04:30`

**Visual:**

Giữ hai đầu ra 100 và 2; đưa biểu tượng schema và kiểm tra cạnh cổng nhận dữ liệu.

**Script:**

Tái sử dụng được rồi, nhưng sự linh hoạt tạo thêm nghĩa vụ gì?

**Purpose:**

Chuẩn bị đánh đổi về kiểm tra kiểu và đóng gói.

### Scene 4 — Giá của tính mở

**Time:** `04:30–05:47`

**Visual:**

Cho người xem đổi tên `qty` thành `quantity` trong map đầu vào. Hàm cũ đánh dấu `missing qty`; một bảng trường bắt buộc xuất hiện như contract, không tự sửa dữ liệu.

**Script:**

Khi cấu trúc map dễ mở rộng, caller cũng dễ gõ sai `qty` thành `quantity`. Nếu phép tính im lặng coi giá trị thiếu là không, nó có thể trả một tổng tưởng hợp lệ nhưng sai. Đối tượng được đóng gói chặt có thể tránh một số lỗi nhờ kiểu tĩnh và quyền truy cập; map phổ dụng phải bù bằng schema, kiểm thử và thông báo lỗi có vị trí. Vì thế tách code khỏi data chỉ là quyết định thiết kế, không phải giấy phép bỏ kiểm tra.

**Purpose:**

Dẫn đúng sang chương generic data và boundary validation.
