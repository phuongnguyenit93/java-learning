---
video:
  url: ""
---

# Luồng biến đổi dữ liệu từ đầu vào đến đầu ra

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

## Tình huống ứng dụng nhỏ và các ranh giới dữ liệu

<!-- VIDEO_SECTION -->
### Scene 1 — Theo đơn A qua toàn bộ ranh giới

**Time:** `00:00–01:31`

**Visual:**

Nối năm bước RECEIVE→VALIDATE→TOTAL→DERIVE→RETURN, trong đó payment/storage nằm bên ngoài đường demo.

**Script:**

Hãy lần theo chính đơn A trên API chúng ta vừa kiểm thử. HTTP nhận map gồm id A, trạng thái pending và hai dòng: P hai nhân ba mươi, Q một nhân bốn mươi. Một schema riêng kiểm tra hình dạng; khi hợp lệ, hàm tính tổng một trăm rồi tạo kết quả giảm mười phần trăm còn chín mươi. Response 200 chứa cả before và after để so sánh. Đường đi dừng tại phản hồi: không có thanh toán, lưu cơ sở dữ liệu hay cập nhật trạng thái dùng chung. Đây là một thí nghiệm trọn vẹn về trách nhiệm dữ liệu, không phải một checkout thật.

**Purpose:**

Cho thấy flow HTTP thực tế: nhận → kiểm tra → tính → tạo giá trị mới → trả về, với giới hạn side effects.

## Dữ liệu đầu vào, kiểm tra cấu trúc và giá trị bị từ chối

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:31–01:45`

**Visual:**

Giữ pipeline và tạo nhánh đỏ ở VALIDATE, không đi tiếp sang TOTAL.

**Script:**

Luồng thành công phụ thuộc dữ liệu đúng; hãy đặt các lỗi ở chính bước chúng xảy ra.

**Purpose:**

Chứng minh validation xảy ra trước transformation.

### Scene 2 — Nhận đúng input, từ chối đúng lúc

**Time:** `01:45–03:16`

**Visual:**

Gửi payload với dòng thứ hai thiếu price, sau đó qty=-3, chỉ vào response 422 errors lines[1].price và lines[1].qty. So sánh với duplicate qty bị parser chặn 400 trước validator.

**Script:**

Hãy bỏ giá của dòng Q: phép tính không được tự coi giá thiếu là không. Schema trả HTTP 422 với `lines[1].price`. Khi thay bằng số lượng âm ba, đường lỗi là `lines[1].qty`, cũng 422. Nếu ngay trong JSON ta lặp khóa `qty`, parser strict từ chối 400 trước khi có map để kiểm tra. Người học cần thấy rõ lớp lỗi đang nằm ở đâu: cú pháp JSON, hợp đồng shape hay phép tính nghiệp vụ, không gộp tất cả thành một câu báo sai chung.

**Purpose:**

Liên kết nhánh không hợp lệ với mã trạng thái và thứ tự xử lý thực tế.

## Phép xử lý độc lập và biến đổi dữ liệu bất biến

<!-- VIDEO_SECTION -->
### Transition

**Time:** `03:16–03:30`

**Visual:**

Tô xanh lần lượt các dữ liệu được read, giữ list gốc cố định.

**Script:**

Những payload bị từ chối không tới phép cộng; giờ xem payload hợp lệ đi qua hàm riêng.

**Purpose:**

Cho bằng chứng immutable before/after trong response thật.

### Scene 3 — Trước và sau không sửa dòng hàng

**Time:** `03:30–05:01`

**Visual:**

JSON response HTTP200 được mở đồng thời `before.lines` với P qty2 price30 Q qty1 price40 và `after.lines` giữ đúng hai dòng; bên dưới `after.subtotal=100,discountedTotal=90.00`.

**Script:**

Hàm tính tổng chỉ đọc các dòng đã được schema chấp nhận, nhân từng `qty×price` rồi cộng lại. Hàm tạo kết quả mới sao chép dữ liệu theo hình dạng đã biết, thêm subtotal và discountedTotal, không sửa đầu vào. Vì vậy ngay trong response ta so sánh được hai danh sách cùng P và Q. Trường status vẫn pending và số tiền sau giảm là 90.00. Bằng chứng ấy có giới hạn: nó chứng minh đường thực thi của preview, chứ không nói toàn ứng dụng luôn bất biến.

**Purpose:**

Dùng phản hồi trước/sau để kiểm chứng transformation và tránh tuyên bố vượt bằng chứng.

## Điều phối trạng thái mới và tác động ra bên ngoài

<!-- VIDEO_SECTION -->
### Transition

**Time:** `05:01–05:15`

**Visual:**

Dừng pipeline trước hộp payment/database, vẽ mũi tên nét đứt chỉ là tương lai.

**Script:**

Kết quả được tạo mới rồi; có nên xem nó là trạng thái đã được lưu không?

**Purpose:**

Tách value computation khỏi persistence/side effects.

### Scene 4 — Điểm dừng cố ý của API preview

**Time:** `05:15–06:46`

**Visual:**

Giữ response `accepted:true`, nhãn `externalPaymentOrPersistencePerformed:false`; phía dưới vẽ hai yêu cầu X/Y cùng đọc version3 nhưng không cho phép lưu trong API demo.

**Script:**

Một kết quả tính đúng không làm đơn hàng tự chuyển thành paid và không thu tiền của ai cả. Bộ điều phối ở hệ thống thật phải quyết định khi nào dùng kết quả đó, kiểm tra phiên bản và xử lý lỗi ở nơi lưu hoặc thanh toán. Hai request có thể cùng tính trên một phiên bản cũ; điều này không bị loại bỏ nhờ snapshot bất biến. Trong thí nghiệm hiện tại, chúng ta cố ý dừng ở response để quan sát riêng data, schema và operation. Bài này không phải transaction manager.

**Purpose:**

Giữ ranh giới chi phối state và side effects rõ ràng.

## Theo dõi phiên bản dữ liệu, lỗi và kiểm thử

<!-- VIDEO_SECTION -->
### Transition

**Time:** `06:46–07:00`

**Visual:**

Dựng timeline request hợp lệ/invalid, đánh dấu nơi có `errors`, `before`, `after`.

**Script:**

Đã nhìn đúng đường đi, muốn debug khi sai ta cần đo tại bước nào?

**Purpose:**

Tạo thói quen quan sát và kiểm thử bằng kết quả cụ thể.

### Scene 5 — Một lỗi, một bằng chứng thích hợp

**Time:** `07:00–08:31`

**Visual:**

Bảng test: valid=200 subtotal100 discounted90; negative qty=422 path lines[1].qty; duplicate root status=400; discount=0→100; discount=100→0. Thêm version3 write conflict ở khu vực ngoài scope.

**Script:**

Không nên chỉ thấy mã 200 rồi kết luận mọi thứ đúng. Ta kiểm tra subtotal, discountedTotal và trường giữ nguyên trong before/after. Với lỗi, ta phải đối chiếu đường dẫn nested field và HTTP status, chẳng hạn dữ liệu âm là 422 còn trùng khóa JSON là 400. Thử giảm không phần trăm phải giữ 100; giảm 100 phần trăm phải còn 0. Nếu ứng dụng có lưu phiên bản, các kiểm thử xung đột thuộc lớp điều phối khác. Mỗi quan sát chỉ chứng minh một lời hứa đủ cụ thể.

**Purpose:**

Kết thúc bằng bộ test cases phản ánh thật đường chạy hiện hữu.
