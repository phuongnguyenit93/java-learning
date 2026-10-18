---
video:
  url: ""
---

# Lựa chọn và kết hợp lập trình hàm

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

## Những tình huống phù hợp với phép biến đổi theo lối hàm

<!-- VIDEO_SECTION -->
### Scene 1 — Bắt đầu từ bài toán nào thì lối hàm dễ tỏa sáng?

**Time:** `00:00–01:23`

**Visual:**

Hiện bốn thẻ `tính giá`, `chuẩn hóa`, `kiểm tra điều kiện`, `quản lý kết nối thiết bị`. Ba thẻ đầu chuyển sang cột biến đổi giá trị. Thẻ kết nối đi vào cột điều phối. Giữ đơn A giá 100 làm ví dụ đối chiếu với kết nối thiết bị thay đổi trạng thái theo thời gian.

**Script:**

Nếu dữ liệu đầu vào đã xác định và quy tắc chỉ biến nó thành kết quả, ta có thể dễ dàng kiểm tra từng bước. Tính giá, chuẩn hóa hay lập báo cáo đều là ví dụ tốt. Nhưng một phiên làm việc với thiết bị hoặc hệ thống bên ngoài đòi hỏi quản lý kết nối và trạng thái thật. Không cần ép toàn bộ việc đó thành hàm thuần. Ta có thể dùng tư duy hàm cho phần tính toán bên trong một luồng mang tác động phụ.

**Purpose:**

Phân loại tình huống sử dụng theo đầu vào có thể kiểm chứng và trách nhiệm điều phối, không theo sở thích cú pháp.

## Đánh đổi về khả năng đọc, cấp phát và gỡ lỗi

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:23–01:35`

**Visual:**

Phân chia giữa lợi ích giữ snapshot và chi phí cấp phát/lambda khó đọc.

**Script:**

Đã chọn việc phù hợp; vẫn phải tính chi phí tạo giá trị mới và chuỗi xử lý.

**Purpose:**

Nêu đánh đổi đo được, không hứa immutable miễn phí.

### Scene 2 — Cái giá của bản sao và đường ống dài

**Time:** `01:35–02:58`

**Visual:**

Trái: hai snapshot `[A,B]` và `[A,B,C]`; thêm counter các đối tượng mới; giữa có cây chia sẻ nhánh; phải hiện pipeline `f1→...→f10` khó truy vết.

**Script:**

Bản bất biến giúp so sánh lịch sử và tránh sửa ngầm, nhưng có thể tạo thêm đối tượng và áp lực thu gom rác. Một số cấu trúc lưu phiên bản chia sẻ nhánh để giảm chi phí, còn cách triển khai cụ thể phải được đo. Một chuỗi mười hàm ẩn danh cũng không dễ đọc chỉ vì không có phép gán. Hãy đặt tên điểm trung gian và chỉ tối ưu sau khi biết phần nào tốn tài nguyên. Đôi khi biến đếm cục bộ là hợp lý.

**Purpose:**

Cân bằng correctness và chi phí thực tế của allocations/readability.

## Lựa chọn giữa lối hàm, mệnh lệnh và hướng đối tượng

<!-- VIDEO_SECTION -->
### Transition

**Time:** `02:58–03:10`

**Visual:**

Đặt ba thẻ lập trình mệnh lệnh, đối tượng, hàm quanh cùng đơn A.

**Script:**

Chi phí đã rõ; giờ đặt ba cách tổ chức mã bên cạnh nhau, tránh xem chúng là đối thủ.

**Purpose:**

Giải thích ranh giới trách nhiệm giữa các style.

### Scene 3 — Ba phong cách cho một đơn hàng

**Time:** `03:10–04:33`

**Visual:**

Sơ đồ `Order` giữ invariant; `tinhGia(order,rate)` tính 94,5; `CheckoutCoordinator` gọi gateway/lưu; đánh dấu đối tượng/lõi hàm/trình tự lệnh khác màu.

**Script:**

Đối tượng có thể chịu trách nhiệm bảo vệ dữ liệu đơn, một hàm thuần có thể tính giảm giá và bộ điều phối có thể lần lượt gọi cổng thanh toán. Đây không phải ba chương trình không liên quan, mà là ba cách phân định công việc trong cùng hệ thống. Ta chọn phong cách dựa trên điều cần bảo vệ: danh tính và hợp đồng, biến đổi có thể dự đoán, hay thứ tự tương tác. Cứ ép mọi dòng theo một phong cách có thể làm thiết kế khó hiểu.

**Purpose:**

Cho thấy kết hợp paradigm đúng scope với cùng tình huống đang học.

## Suy luận đầu cuối cho một bài toán xử lý nhỏ

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:33–04:45`

**Visual:**

Dồn tất cả thẻ về một flow đơn A 100→90→94,5 rồi charge/save.

**Script:**

Nếu ghép những phần vừa học vào một đơn hàng thật, ta chứng minh được điều gì trước khi bắt đầu gọi cổng thanh toán?

**Purpose:**

Tổng hợp bằng số học và ranh giới giao dịch.

### Scene 4 — Một đơn A, ba lời hứa khác nhau

**Time:** `04:45–06:08`

**Visual:**

Màn hình chia ba đoạn: input A(100), tính `100→90→94,5`, cổng thanh toán trả `approved/declined`, sau đó lưu. Bật nhánh declined để hiển thị không ghi paid.

**Script:**

Trước tiên lấy ảnh chụp đơn A có giá 100 cùng chính sách giảm 10 phần trăm, thuế 5 phần trăm tính trên giá sau giảm. Hàm giảm giá trả 90, thuế là 4,5 và số tiền dự định thu là 94,5. Ta có thể chứng minh từng phép tính từ đầu vào. Nhưng ngay khi gọi thanh toán, tính thuần kết thúc: kết quả còn phụ thuộc cổng ngoài, kiểm tra phiên bản và xử lý retry. Cần phân biệt đúng tổng tiền với thực sự thu được tiền.

**Purpose:**

Đưa toàn bộ evidence số học và failure branch vào một flow ghi nhớ được.

## Ranh giới với API hàm Java và luồng reactive

<!-- VIDEO_SECTION -->
### Transition

**Time:** `06:08–06:20`

**Visual:**

Dùng sơ đồ bản đồ module với FP ở trung tâm, Java, DOP và Reactive ở ba nhánh riêng.

**Script:**

Ta đã hoàn thiện mô hình; giờ xác định bước học tiếp thuộc module nào.

**Purpose:**

Kết lại phạm vi và chuyển giao chính xác.

### Scene 5 — Đừng nhầm hàm với một thư viện cụ thể

**Time:** `06:20–07:43`

**Visual:**

Chiếu thẻ `tư duy hàm` gồm purity, value, composition; bên cạnh `Java lambda/Stream`, `DOP data model`, `Reactive events/time/backpressure`; nối nét đứt các phần giao nhau.

**Script:**

Chúng ta vừa học cách suy luận từ đầu vào qua các phép biến đổi và đặt I/O ở ranh giới. Cú pháp lambda, functional interface và Stream API thuộc bài Java. Lập trình hướng dữ liệu tập trung vào biểu diễn dữ liệu và tách xử lý; reactive nghiên cứu tín hiệu theo thời gian và subscription. Chúng có thể mượn các phép biến đổi theo lối hàm, nhưng không đồng nghĩa. Câu hỏi nên mang đi là: đầu vào và tác động của bước này có đủ rõ để kiểm tra không?

**Purpose:**

Đóng video bằng decision lens và ranh giới module, không hứa kiến thức ngoài curriculum.
