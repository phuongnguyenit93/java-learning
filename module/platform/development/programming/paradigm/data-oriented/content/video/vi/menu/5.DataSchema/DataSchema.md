---
video:
  url: ""
---

# Hình dạng dữ liệu, schema và ranh giới kiểm tra

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

## Hình dạng dữ liệu và phần mô tả schema độc lập

<!-- VIDEO_SECTION -->
### Scene 1 — Dữ liệu và bản quy tắc tách nhau

**Time:** `00:00–01:30`

**Visual:**

Bên trái đặt JSON đơn A; bên phải bảng quy tắc riêng: id chuỗi không trắng, status=pending, lines 1..20, qty nguyên 1..10000, price nguyên 0..1e6.

**Script:**

Map đơn A là một giá trị cụ thể, còn schema diễn tả thế nào là giá trị hợp lệ. Hai thứ tồn tại độc lập: cùng một bảng quy tắc có thể kiểm tra nhiều đơn hàng khác nhau. Schema của bài học ràng buộc tên trường, số dòng, kiểu và khoảng số lượng, giá. Nếu số lượng được viết dưới dạng chuỗi, map vẫn có thể chứa nó nhưng không đáp ứng hợp đồng. Đây là nguyên tắc thứ tư Sharvit, tách bản mô tả khỏi dữ liệu được biểu diễn.

**Purpose:**

Giới thiệu schema độc lập bằng hai biểu diễn có thể quay được.

## Dữ liệu linh hoạt và kiểm tra schema theo nhu cầu

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:30–01:43`

**Visual:**

Khoanh cổng dữ liệu bên ngoài màu đỏ; giữ biến tạm bên trong vùng đã xác thực màu xanh.

**Script:**

Schema đã ở riêng; liệu có phải mọi biến nội bộ đều phải kiểm tra lại từ đầu?

**Purpose:**

Phân biệt nơi cần validation với thao tác nội bộ.

### Scene 2 — Kiểm tra đúng nơi dữ liệu chưa đáng tin

**Time:** `01:43–03:13`

**Visual:**

Sơ đồ input JSON → OrderShapeSchema → calculator; gửi qty dạng chuỗi vào cổng để thấy nhánh từ chối. Không dùng dữ liệu của API giả.

**Script:**

Dữ liệu do người dùng gửi là chưa đáng tin cậy. Người dùng có thể bỏ trường bắt buộc, thay số bằng chuỗi hoặc gửi một danh sách sai hình dạng. Vì vậy ta đặt kiểm tra trước tính tiền, ngay ranh giới đầu vào. Với giá trị nội bộ đã được kiểm tra và có hợp đồng rõ, không nhất thiết lặp toàn bộ kiểm tra ở từng phép cộng. Cách tách này giảm nguy cơ một phép xử lý âm thầm đoán giá trị thiếu là số không.

**Purpose:**

Làm rõ ranh giới tin cậy và lý do schema cần trước phép tính.

## Kiểm tra tại ranh giới đầu vào, đầu ra và độ tin cậy

<!-- VIDEO_SECTION -->
### Transition

**Time:** `03:13–03:26`

**Visual:**

Chia màn hình request/response thành hai đường hợp lệ và không hợp lệ.

**Script:**

Cổng đã có; bây giờ gửi cùng một đơn theo hai hình dạng để so sánh.

**Purpose:**

Chứng minh API thực sự thực hiện kiểm tra schema.

### Scene 3 — HTTP 200 với A, HTTP 422 với Q sai

**Time:** `03:26–04:56`

**Visual:**

Gửi POST thật /paradigm/data-oriented/orders/preview?discountPercent=10. Payload A P(2,30),Q(1,40) trả 200 và subtotal100, discounted90; đổi duy nhất Q.qty=-3 trả 422 với errors path lines[1].qty.

**Script:**

Ở request đầu tiên, hai dòng P và Q tạo tổng một trăm. Schema chấp nhận và phần tính giảm mười phần trăm cho kết quả chín mươi. Ở request thứ hai, ta đổi số lượng dòng Q thành âm ba. JSON vẫn đúng cú pháp, nhưng dữ liệu không đạt quy tắc qty dương. Phản hồi thật là HTTP 422, accepted=false và đường dẫn lines[1].qty. Không có tổng tiền sai được tạo ra. Đó là khác biệt mà một ví dụ giấy chỉ nói chứ chưa tự chứng minh.

**Purpose:**

Cung cấp quan sát 200/422 với chính endpoint đã chạy và field path thật.

## Dữ liệu không hợp lệ, thông tin lỗi và lựa chọn xử lý

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:56–05:09`

**Visual:**

Giữ nhánh 422; thêm nhánh 400 trước schema và một cột giải thích actual.

**Script:**

HTTP 422 xuất hiện sau parser; còn JSON hỏng thì phải được chặn sớm hơn.

**Purpose:**

Dạy đúng phân loại lỗi và giới hạn phản hồi.

### Scene 4 — 400 là cú pháp, 422 là shape

**Time:** `05:09–06:39`

**Visual:**

Bảng đối chiếu: JSON trùng trường status ở root hoặc qty ở line con → HTTP400; dữ liệu đúng JSON nhưng status=paid →422 với actual string value: paid; id dài chỉ trả chiều dài.

**Script:**

Nếu JSON sai cú pháp hoặc cùng object lặp lại khóa status hay qty, strict parser của ứng dụng từ chối HTTP 400 trước khi schema xử lý. Nhưng status=paid là JSON hợp cú pháp, chỉ sai hợp đồng dành cho preview, nên schema trả 422. Lỗi phải cho biết trường sai và loại giá trị đủ để sửa. Với chuỗi do người lạ gửi quá dài, không được đưa lại toàn bộ nội dung vào thông báo; cần giữ độ dài chẩn đoán trong giới hạn.

**Purpose:**

Chứng minh phân biệt parse failure và validation failure, kể cả dữ liệu không đáng tin cậy.

## Thay đổi schema và chi phí của biểu diễn linh hoạt

<!-- VIDEO_SECTION -->
### Transition

**Time:** `06:39–06:52`

**Visual:**

Bên cạnh schema hiện tại, phác schema v2 giả định thêm currency; không đụng API thực.

**Script:**

Các quy tắc hiện tại chặt chẽ; khi hợp đồng thay đổi phải cân nhắc tương thích.

**Purpose:**

Nêu schema evolution là thiết kế hợp đồng, không thêm chức năng ngoài scope.

### Scene 5 — Thay schema là quyết định về client

**Time:** `06:52–08:22`

**Visual:**

Cho sơ đồ client v1 gửi A, schema v1 chấp nhận; mũi tên tới schema v2 giả định field currency, ba thẻ optional/reject/migrate và nhãn không triển khai.

**Script:**

Một hệ thống có thể phải thêm đơn vị tiền tệ hay một loại dòng hàng mới. Nếu schema thay đổi, client cũ không biết trường mới; ta cần quyết định giá trị mặc định có hợp lệ không, chấp nhận trường tùy chọn hay bắt buộc nâng phiên bản. Đó là công việc tương thích dữ liệu, không phải chỉ mở map và thêm khóa. API của bài học vẫn kiểm tra tập trường đã công bố, không có schema v2. Ví dụ tương lai chỉ cho thấy vì sao schema độc lập tạo chỗ để bàn luận thay đổi.

**Purpose:**

Dẫn vào chi phí quản trị schema mà không bịa tính năng đã triển khai.
