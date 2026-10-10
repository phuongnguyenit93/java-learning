---
video:
  url: ""
---

# Hàm như giá trị và phép biến đổi kết hợp

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

## Hàm như giá trị dùng để biểu diễn hành vi

<!-- VIDEO_SECTION -->
### Scene 1 — Truyền quy tắc giá như một giá trị

**Time:** `00:00–01:23`

**Visual:**

Đặt hai thẻ hàm `giaThuong=(g)=>g` và `giaVip=(g)=>g×0,9`. Chọn khách VIP, kéo thẻ `giaVip` vào ô `quyTac`, chỉ sau đó mới xuất hiện lời gọi `quyTac(100) → 90`.

**Script:**

Chúng ta quen gọi một hàm bằng tên, nhưng còn có thể chọn chính hàm đó rồi truyền nó sang chỗ khác. Khi khách là VIP, hệ thống chọn quy tắc giảm 10 phần trăm; lúc chọn chưa có phép tính nào chạy. Chỉ khi áp dụng quy tắc lên 100, kết quả mới là 90. Đây là ý nghĩa hàm như giá trị. Lưu ý: hàm được truyền vẫn có thể ghi log hay đọc biến chung, nên nó không tự động thuần.

**Purpose:**

Làm hiện ra sự khác biệt giữa chọn hàm và thực thi hàm bằng phép tính rõ ràng.

## Hàm bậc cao và cách tái sử dụng phép xử lý

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:23–01:35`

**Visual:**

Giữ nguyên danh sách `[10,20]`; lần lượt đưa thẻ quy tắc `nhanDoi` rồi `congNam` vào cùng tham số của `bienDoi`, và hiện hai kết quả khác nhau.

**Script:**

Đã truyền được một hàm, hãy xem bên nhận dùng nó để tái sử dụng cùng cơ chế.

**Purpose:**

Nối first-class function tới hàm bậc cao.

### Scene 2 — Một cơ chế duyệt, nhiều công thức

**Time:** `01:35–02:58`

**Visual:**

Hiện bảng `[10,20]`; bên cạnh là `bienDoi(items, nhanDoi)` với `[20,40]`, thay đúng thẻ quy tắc bằng `congNam` tạo `[15,25]`. Tô riêng hàm duyệt và tham số hành vi.

**Script:**

Hàm bậc cao là hàm nhận hoặc trả lại hàm khác. Ở đây `bienDoi` lo việc đi qua danh sách, còn quy tắc được người gọi cung cấp. Chúng ta không cần viết hai vòng lặp giống nhau. Nhưng nếu `nhanDoi` bị thay bằng một hàm đọc tỷ giá đang thay đổi, kết quả sẽ không còn dễ dự đoán. Việc truyền hành vi và tính thuần là hai tính chất khác nhau.

**Purpose:**

Chứng minh tái sử dụng traversal và không đồng nhất higher-order với purity.

## Ghép các hàm từ đầu vào đến đầu ra

<!-- VIDEO_SECTION -->
### Transition

**Time:** `02:58–03:10`

**Visual:**

Đưa người xem trở lại đơn A 100 và hai quy tắc giảm, thuế.

**Script:**

Hàm bậc cao xử lý cả danh sách; giờ quay lại việc nối những phép tính nhỏ.

**Purpose:**

Giới thiệu phép ghép qua đầu ra trung gian.

### Scene 3 — Một mũi tên qua hai hàm

**Time:** `03:10–04:33`

**Visual:**

Hiện `100 → giamGia10% → 90 → thue5% → 94,5`; làm nổi bật đầu ra 90 đồng thời là đầu vào bước hai; cạnh đó thêm chú thích thuế 4,5.

**Script:**

Ghép hàm nghĩa là đầu ra bước trước trở thành đầu vào bước sau. Giá 100 được giảm 10 phần trăm còn 90; tính thuế 5 phần trăm trên 90 là 4,5; tổng là 94,5. Chúng ta kiểm thử riêng từng phép tính rồi mới kiểm tra đường đi đầy đủ. Nhưng thứ tự là quy tắc nghiệp vụ: nếu có làm tròn hoặc quy định khác, giảm rồi tính thuế có thể không tương đương tính thuế trước.

**Purpose:**

Cung cấp bằng chứng cụ thể cho composition và cảnh báo về thứ tự nghiệp vụ.

## Biến đổi, lọc và tổng hợp tập dữ liệu

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:33–04:45`

**Visual:**

Thay đơn hàng đơn lẻ bằng bộ ba số tiền.

**Script:**

Một giá trị đi qua hai hàm. Với nhiều phần tử, ta cần chọn, biến đổi rồi gộp.

**Purpose:**

Cho thấy mỗi thao tác filter/map/reduce tạo dữ liệu gì.

### Scene 4 — Ba tấm phim: chọn, giảm, cộng

**Time:** `04:45–06:08`

**Visual:**

Đưa `[20,40,50]` vào khung; làm mờ 20 khi lọc `>=30`; dán nhãn `[40,50]`, qua giảm10% thành `[36,45]`, đặt bộ cộng `0+36+45` ra `81`.

**Script:**

Hãy theo dõi đúng ba bước. Đầu tiên lọc những khoản ít nhất 30 nên 20 biến mất, còn 40 và 50. Tiếp theo biến đổi từng khoản bằng cách giảm 10 phần trăm, tạo 36 và 45. Cuối cùng gộp từ giá trị khởi đầu 0, ta có tổng 81. Đây là ý tưởng lọc, ánh xạ và tổng hợp, chưa phải bài ghi nhớ tên phương thức Java. Giá trị khởi đầu và thứ tự tác động vẫn có ý nghĩa.

**Purpose:**

Dựng trực quan intermediate states và phép cộng tạo 81, không gắn với JDK Stream.

## Theo dõi toàn bộ phép biến đổi từ đầu vào đến đầu ra

<!-- VIDEO_SECTION -->
### Transition

**Time:** `06:08–06:20`

**Visual:**

Thay ba số bằng ba đơn A, B, C có nhãn trạng thái.

**Script:**

Ba bước trên có thể dùng với dữ liệu thực tế hơn một chút.

**Purpose:**

Chuyển từ phép tính danh sách sang luồng xử lý end-to-end.

### Scene 5 — Từ A, B, C tới tổng 81

**Time:** `06:20–07:43`

**Visual:**

Bảng hiện `A approved 40`, `B pending 20`, `C approved 50`. Lần lượt bỏ B, lấy `[40,50]`, giảm thành `[36,45]`, gộp ra 81; bên cạnh đánh dấu source là snapshot ổn định.

**Script:**

Ở ví dụ này, ta không lọc đơn theo giá trước; ta chỉ chọn đơn đã duyệt. A và C được giữ, B đang chờ nên bị loại. Lấy số tiền của A và C là 40 và 50, giảm từng khoản 10 phần trăm rồi cộng ra 81. Mỗi nấc đều có dữ liệu có thể kiểm tra; nếu kết quả cuối sai, ta biết nhìn bước nào. Cần giữ ảnh chụp đầu vào ổn định và nêu rõ quy tắc làm tròn khi dùng tiền thật.

**Purpose:**

Phân biệt hai bộ lọc khác nhau có cùng số liệu và cho bằng chứng từng bước trên đơn hàng.

## Ghép hàm dễ hiểu và chuỗi xử lý quá phức tạp

<!-- VIDEO_SECTION -->
### Transition

**Time:** `07:43–07:55`

**Visual:**

Đối chiếu một khối chồng nhiều lambda với ba khối đặt tên rõ.

**Script:**

Ghép nhiều bước giúp chia nhỏ lỗi, nhưng thêm quá nhiều bước lại có thể gây rối.

**Purpose:**

Giải thích khi nào không nên tiếp tục ghép hàm.

### Scene 6 — Khi chuỗi xử lý mất ý nghĩa

**Time:** `07:55–09:18`

**Visual:**

Bên trái zoom vào mười ô trống `f1→f2→...→f10`, tô một dấu hỏi tại nơi sai; bên phải ba khối `kiemTraDon → tinhGiamGia → taoBienNhan` kèm giá trị trung gian.

**Script:**

Một đường ống mười hàm không tên có thể khó hiểu hơn một vòng lặp tốt. Khi người xem không biết giá trị ở giữa có ý nghĩa gì, việc gỡ lỗi trở thành đoán mò. Hãy đặt tên theo nghiệp vụ, dừng lại ở các điểm quan sát hữu ích và không giấu lời gọi mạng trong hàm đang quảng cáo là thuần. Nếu một vòng lặp đơn giản nói rõ ý định hơn, dùng vòng lặp là một lựa chọn hợp lý.

**Purpose:**

Kết thúc bằng quy tắc chất lượng composition, mở sang tác động phụ ở chương kế.
