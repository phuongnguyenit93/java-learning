---
video:
  url: ""
---

# Lập trình hàm: Mục đích và mô hình tư duy

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

## Lập trình hàm: Khái niệm và phạm vi

<!-- VIDEO_SECTION -->

### Scene 1 — Một phép tính có thể kể theo hai cách

**Time:** `00:00–01:17`

**Visual:**

Mở với đơn hàng A có giá 100. Phía trái hiện danh sách lệnh sửa biến `tong`; phía phải lần lượt xuất hiện ba ô `100 → 90 → 94,5`, không có nút Run giả.

**Script:**

Nếu tôi hỏi số tiền phải thu của đơn A, ta có thể kể từng lần thay đổi biến. Nhưng cũng có cách khác: xem mỗi bước như một phép biến đổi nhận giá trị rồi trả giá trị. Cả hai đều tính được; hôm nay ta tìm hiểu vì sao cách thứ hai khiến những quy tắc như giảm giá hay thuế dễ tách ra và kiểm tra hơn. Nó không có nghĩa mọi chương trình phải bỏ lệnh hay đối tượng.

**Purpose:**

Đặt trọng tâm ở giá trị đi qua hàm, không đánh đồng functional với một cú pháp hoặc lệnh HTTP.

## Khó khăn khi suy luận về trạng thái dùng chung có thể thay đổi

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:17–01:29`

**Visual:**

Giữ hai cột của cảnh mở đầu; tô sáng giá trị ở cột biến đổi rồi chuyển tiêu điểm sang biến khuyến mãi dùng chung phía sau lời gọi tại cột còn lại.

**Script:**

Hai cách vừa rồi đều cho ra tổng tiền. Nhưng nếu cùng một phép tính mà lần sau ra con số khác thì điều gì đã thay đổi?

**Purpose:**

Nêu vấn đề đầu vào ẩn để dẫn sang động cơ của tính thuần.

### Scene 2 — Biến chung che giấu điều kiện tính

**Time:** `01:29–02:46`

**Visual:**

Giữ lại `gia = 100`; thêm khung `khuyenMaiToanCuc` đổi từ 10% sang 20% giữa hai lần gọi `tinhGia(100)`. Khoanh hai kết quả `90` và `80` dù lời gọi hiển thị giống nhau.

**Script:**

Hai dòng cùng ghi `tinhGia(100)` nhưng trả 90 rồi 80. Không phải vì toán học thay đổi; chương trình đã đọc một mức giảm giá ở nơi khác. Muốn giải thích lỗi, tôi phải biết thời điểm và ai đã sửa khuyến mãi toàn cục. Nếu đưa tỷ lệ giảm thành đối số, một lần tính có đủ dữ kiện để lặp lại. Chúng ta không cấm trạng thái chung, chỉ muốn nhận diện khi nào nó can thiệp.

**Purpose:**

Cho thấy phụ thuộc ẩn phá khả năng suy luận và lý do truyền đầu vào tường minh.

## Lối mệnh lệnh và động cơ của cách lập trình hàm

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:46–02:58`

**Visual:**

Thu nhỏ biến khuyến mãi toàn cục; phóng to hai phép cộng `total=0; total+=40; total+=50` và `sum([40,50])`, rồi làm nổi chữ LOCAL cạnh biến cộng dồn.

**Script:**

Đã biết vì sao trạng thái chung khó theo dõi, giờ đừng vội kết luận lối mệnh lệnh luôn sai.

**Purpose:**

Phân biệt động cơ FP với phán xét tuyệt đối các paradigm.

### Scene 3 — Không phải cuộc chiến giữa hai phong cách

**Time:** `02:58–04:15`

**Visual:**

Chiếu bảng đối chiếu: `total=0; total+=40; total+=50` → 90 ở trái; `sum([40,50])` →90 ở phải. Tô khác màu biến cục bộ với danh sách dùng chung có thể bị sửa.

**Script:**

Một bộ cộng dồn cục bộ, ngắn gọn, có thể là lời giải tốt. Rủi ro tăng lên khi cùng dữ liệu bị sửa từ nhiều chỗ và người đọc không thấy điều đó. Lập trình hàm khuyến khích lấy một ảnh chụp đầu vào ổn định và trả kết quả mới. Điều cần học không phải tránh mọi vòng lặp mà là biết ai sở hữu trạng thái, lúc nào có tác động phụ, và lựa chọn lối viết dễ kiểm tra hơn.

**Purpose:**

Ngăn suy diễn FP phủ nhận imperative; chỉ ra vấn đề thật nằm ở quyền sửa và phụ thuộc.

## Điểm xuất phát: Hàm, giá trị và các lối tổ chức chương trình

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:15–04:27`

**Visual:**

Thu gọn màn hình chỉ còn dữ liệu, hàm, danh sách và điều kiện.

**Script:**

Ta đã có ví dụ. Trước khi đặt tên thêm khái niệm, cần biết những kiến thức tối thiểu nào?

**Purpose:**

Bảo đảm beginner không phải biết lambda Java hay Stream API.

### Scene 4 — Bốn viên gạch khởi đầu

**Time:** `04:27–05:44`

**Visual:**

Hiện từng thẻ `giá trị: 100`, `hàm: giảmGiá(giá,tỷLệ)`, `danh sách: [20,40,50]`, `điều kiện: giá >= 30`; kèm sơ đồ đầu vào, kết quả, không hiện syntax Java.

**Script:**

Ta bắt đầu từ những thứ quen thuộc: số tiền là một giá trị; hàm nhận dữ kiện và tạo kết quả; danh sách chứa nhiều giá trị; điều kiện quyết định phần tử nào phù hợp. Tư duy khai báo thường nói điều ta muốn, còn ở đây ta sẽ học vì sao một chuỗi biến đổi có thể giải thích được. Chưa cần biết interface hàm, lambda hay thư viện Stream của Java.

**Purpose:**

Giới thiệu và kiểm chứng prerequisite tối thiểu bằng dữ liệu cụ thể thay vì jargon.

## Mô hình cốt lõi: Đầu vào tường minh, biến đổi và ghép hàm

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:44–05:56`

**Visual:**

Mở rộng một đường duy nhất từ đơn hàng A tới số tiền cuối.

**Script:**

Các viên gạch đã rõ; giờ nối chúng thành một phép tính có thể lần theo.

**Purpose:**

Tổng hợp model đầu vào minh bạch và ghép phép biến đổi.

### Scene 5 — Giá 100 đi qua hai hàm

**Time:** `05:56–07:13`

**Visual:**

Reveal từng bước `giamGia(100,10%) → 90`, rồi `tinhTongCoThue(90,5%) → 94,5`. Dừng hình ở 90, so sánh thuế `4,5` và tổng `94,5`; mũi tên thanh toán đặt ngoài khung tính.

**Script:**

Giả sử giá gốc 100, giảm 10 phần trăm còn 90. Thuế 5 phần trăm được tính trên 90, tức 4,5; tổng cần trả là 94,5. Mỗi hàm có thể được kiểm tra bằng một bộ đầu vào cố định. Nếu thay quy tắc thuế, ta biết phải xem lại bước nào. Nhưng thu tiền từ khách là một hành động bên ngoài; không được coi việc tính đúng 94,5 là bằng chứng cổng thanh toán đã thu thành công.

**Purpose:**

Tạo bằng chứng số học của composition đồng thời giữ ranh giới phép tính/side effect.

## Tình huống áp dụng và giới hạn của lối lập trình hàm

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:13–07:25`

**Visual:**

Đưa thẻ `tính giá` và `thu tiền` tách ra hai cột.

**Script:**

Đã có hai hàm thuần; câu hỏi mới là chỗ nào áp dụng tốt, chỗ nào không?

**Purpose:**

Làm rõ giới hạn công nghệ của tư duy hàm.

### Scene 6 — Không biến thao tác thật thành con số trên slide

**Time:** `07:25–08:42`

**Visual:**

Vẽ khối `báo cáo/giá/chuẩn hoá` nối với `biến đổi giá trị`; khối `cổng thanh toán/cơ sở dữ liệu` nối với `I/O và lỗi`; đặt biểu tượng đồng hồ cho dữ liệu thay đổi theo thời gian.

**Script:**

Nếu ta chuẩn hoá địa chỉ, xếp loại điểm hay tính giảm giá từ một ảnh chụp dữ liệu, phép biến đổi nhỏ rất tiện để kiểm tra. Còn khi ứng dụng đọc mạng, thu tiền hoặc ghi kho, kết quả phụ thuộc cả hệ thống bên ngoài. Ta vẫn có thể đặt một lõi tính toán thuần ở giữa, nhưng phải điều phối tác động phụ ở ranh giới. Một vòng lặp dễ hiểu đôi khi tốt hơn một chuỗi hàm rối.

**Purpose:**

Nêu nơi phù hợp và nơi dừng của phương pháp, tránh hứa hẹn tính thuần cho I/O.

## Lộ trình học: Hàm thuần, giá trị, ghép hàm và tác động phụ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:42–08:54`

**Visual:**

Biến mũi tên `100→90→94,5` thành sáu mốc: purity, value, function, composition, effects, tradeoffs.

**Script:**

Đến đây ta biết câu hỏi lớn. Phần tiếp theo sẽ cung cấp phép thử cụ thể cho từng lời hứa.

**Purpose:**

Dẫn người học theo thứ tự tiên quyết, không nhồi thuật ngữ mới.

### Scene 7 — Bản đồ sáu chương

**Time:** `08:54–10:11`

**Visual:**

Chiếu timeline sáu chương; ở purity highlight lời gọi cùng input, immutability highlight hai ảnh chụp, composition highlight các giá trị 90/94,5 và effects viền ngoài cho cổng thanh toán.

**Script:**

Ta sẽ bắt đầu bằng câu hỏi: cùng đầu vào có thật sự cho cùng kết quả mà không làm gì bên ngoài không? Sau đó giữ các giá trị cũ nguyên vẹn khi tạo phiên bản mới. Tiếp theo là truyền hàm như giá trị, ghép chúng để tính từ 100 đến 94,5, rồi kiểm soát việc thu tiền thật. Chương cuối giúp biết lúc nào dùng tư duy hàm, lúc nào chọn cách tổ chức khác. Mỗi mốc dựa trên mốc trước.

**Purpose:**

Kết nối đầy đủ hành trình và mở sang Pure Functions.
