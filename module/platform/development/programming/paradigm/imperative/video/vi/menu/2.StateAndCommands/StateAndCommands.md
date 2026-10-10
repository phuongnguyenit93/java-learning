---
video:
  url: ""
---

# Trạng thái, lệnh và thứ tự thực thi

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

## Trạng thái chương trình và giá trị tại một thời điểm

<!-- VIDEO_SECTION -->

### Scene 1 — Trạng thái chương trình và giá trị tại một thời điểm

**Time:** 00:00–01:20

**Visual:** Dựng bảng trạng thái t0: balance=100, transactions=0. Chạy READ và giữ bảng, sau đó WITHDRAW 30 để tạo hàng t1: 70.

**Script:** Trạng thái là những giá trị đang được lưu ở một thời điểm, không phải toàn bộ lịch sử hoạt động. Đọc số dư 100 tạo một đầu ra nhưng không thay các ô lưu. Rút hợp lệ 30 mới chuyển số dư sang 70. Khi bị từ chối, chương trình vẫn có thể in lời giải thích dù số dư giữ nguyên. Vì thế trên màn hình tôi đặt cột trạng thái riêng và cột đầu ra riêng; chúng có thể đổi độc lập.

**Purpose:** Tạo công cụ quan sát hai loại kết quả thay vì coi mọi thông báo là mutation.

## Lệnh thực hiện thao tác và biểu thức tính giá trị

<!-- VIDEO_SECTION -->

### Transition

**Time:** 01:20–01:33

**Visual:** Đóng băng hàng `balance=70` như LỊCH SỬ ĐÃ XONG; mở thẻ VÍ DỤ MỚI ghi rõ `balance=100, fee=5`, để biểu thức ở ô nháp riêng và giữ WRITE tắt.

**Script:** Ta vừa thấy con số 70. Làm sao phân biệt nó là kết quả tính hay giá trị đã lưu?

**Purpose:** Nối khái niệm trạng thái hiện tại với phép tính chưa phát sinh ghi dữ liệu.

### Scene 2 — Lệnh thực hiện thao tác và biểu thức tính giá trị

**Time:** 01:33–02:48

**Visual:** Trong thẻ VÍ DỤ MỚI đặt lại `balance=100, fee=5`; ô nháp tính `100−5=95` nhưng ô lưu vẫn 100; sau đó mới chiếu phép gán làm ô lưu đổi sang 95.

**Script:** Hãy nhìn biểu thức balance trừ fee, với 100 và 5 nó cho ra 95. Nhưng có kết quả 95 chưa có nghĩa số dư đã thành 95. Muốn lưu lại, ta cần lệnh ghi như phép gán. Cả phép kiểm tra balance có đủ tiền không cũng chỉ tạo ra đúng hoặc sai. Khi debug, đừng mặc định cứ gặp một lệnh là biến bị sửa; hãy tìm chính xác bước nào ghi trạng thái.

**Purpose:** Phân biệt phép tính, điều kiện và lệnh gán bằng cùng bộ dữ liệu.

## Phép gán và chuyển đổi trạng thái

<!-- VIDEO_SECTION -->

### Transition

**Time:** 02:48–03:01

**Visual:** Kéo kết quả nháp `95` bên cạnh `balance=100`; lần lượt đánh sáng ba từ READ → CALCULATE → WRITE trên cùng mũi tên.

**Script:** Chúng ta hãy xem bên trong một phép gán, bên nào được tính trước khi biến đổi số dư.

**Purpose:** Từ biểu thức trả kết quả đi vào cơ chế đọc–tính–ghi của phép gán.

### Scene 3 — Phép gán và chuyển đổi trạng thái

**Time:** 03:01–04:16

**Visual:** Phóng to balance=balance-30; đánh sáng vế phải đọc 100, phép trừ sinh 70, rồi vế trái lưu 70. Lặp để ra 40.

**Script:** Dấu bằng trong câu lệnh này không phải một đẳng thức toán học. Máy đọc giá trị hiện tại, tính số dư mới rồi gán nó vào vị trí balance. Từ 100 thành 70, sau một lần rút 30 hợp lệ nữa thành 40. Mỗi lần dùng lại câu lệnh phải xét trạng thái lúc đó. Nếu điều kiện rút không cho phép, chúng ta phải bỏ qua bước gán chứ không cứ trừ rồi xử lý sau.

**Purpose:** Cho thấy rõ thứ tự đọc–tính–ghi và hệ quả của phép gán lặp.

## Thứ tự lệnh và quan hệ phụ thuộc giữa các bước

<!-- VIDEO_SECTION -->

### Transition

**Time:** 04:16–04:29

**Visual:** Giữ phép gán ở `balance=40`; đặt hai thẻ `giảm toàn đơn` và `giảm theo món` dưới một giá đơn vị 20.

**Script:** Phép gán đã rõ; bây giờ đổi thứ tự hai lệnh sẽ cho hệ quả gì?

**Purpose:** Chuyển từ thứ tự cập nhật sang thứ tự nghiệp vụ quyết định ý nghĩa kết quả.

### Scene 4 — Thứ tự lệnh và quan hệ phụ thuộc giữa các bước

**Time:** 04:29–05:44

**Visual:** Hiển thị giá 20, số lượng 3 và giảm 5. So sánh tổng (20×3)-5=55 với (20-5)×3=45 trên cùng màn hình.

**Script:** Cùng ba con số, kết quả vẫn khác nếu thay thứ tự. Giảm năm đồng cho cả đơn cho 55; giảm năm đồng trên mỗi sản phẩm cho 45. Chương trình không tự suy ra quy tắc kinh doanh. Chính người viết phải đặt bước tính đúng vị trí và xác định dữ liệu bước sau đọc từ bước trước. Với hai lệnh độc lập, đổi chỗ có thể không sao; với lệnh dùng chung dữ liệu, ta phải kiểm tra phụ thuộc.

**Purpose:** Chứng minh sequencing bằng số học và quy tắc nghiệp vụ thực tế.

## Theo dõi trạng thái trước và sau mỗi thao tác

<!-- VIDEO_SECTION -->

### Transition

**Time:** 05:44–05:57

**Visual:** Giữ hai tổng 55 và 45, làm mờ công thức; mở khung `before | condition | after` để chuẩn bị một bảng vết cho rút tiền.

**Script:** Khi lệnh phụ thuộc trạng thái vừa tạo ra, một bảng vết giúp kiểm tra kết quả từng nhánh.

**Purpose:** Dùng ví dụ thứ tự cho kết quả khác nhau để đặt nhu cầu ghi bằng chứng từng bước.

### Scene 5 — Theo dõi trạng thái trước và sau mỗi thao tác

**Time:** 05:57–07:12

**Visual:** Hiện bảng request, condition, before, after, output. Hàng rút 30 là 100→70 thành công, hàng rút 90 là 70→70 từ chối.

**Script:** Bắt đầu với số dư 100, tôi rút 30. Điều kiện hợp lệ nên số dư còn 70. Tiếp theo là yêu cầu rút 90: nó phải được so với số dư hiện tại 70, không phải số dư cũ 100. Nhánh từ chối vẫn báo “không đủ tiền” nhưng không trừ đồng nào, cho nên 70 trở thành 70. Hãy luôn ghi đầu ra và trạng thái ở các cột khác nhau để thấy cả hành động không xảy ra.

**Purpose:** Đưa bằng chứng trước/sau và nhánh không cập nhật vào cùng vết quan sát.

## Hệ quả của dữ liệu bị thay đổi ngoài dự kiến

<!-- VIDEO_SECTION -->

### Transition

**Time:** 07:12–07:25

**Visual:** Khoanh hàng rút 90 với số dư `70→70`, đặt biểu tượng `displayBalance()` và dấu hỏi WRITE ngay phía trên ô số dư.

**Script:** Bảng vết có thể phát hiện tình huống tên hàm bảo chỉ xem nhưng lại sửa dữ liệu không?

**Purpose:** Dẫn từ bảng vết hợp lệ đến nguy cơ thao tác chỉ xem lại âm thầm sửa trạng thái.

### Scene 6 — Hệ quả của dữ liệu bị thay đổi ngoài dự kiến

**Time:** 07:25–08:40

**Visual:** Cho printBalance chỉ đọc và in 70. Bật bản lỗi tự trừ 5 khi in, lần xem kế tiếp chỉ còn 65; khoanh đỏ nơi ghi ẩn.

**Script:** Một thao tác mang tên hiển thị số dư thường chỉ nên đọc. Nếu nó âm thầm trừ phí năm đồng mỗi lần in, việc quan sát lại thay đổi thứ ta quan sát. Đó là cập nhật không dự kiến. Trên vết chạy, ta phải đánh dấu mọi thao tác được phép ghi, kể cả bên trong thủ tục khác. Lúc có nhiều người viết vào cùng dữ liệu, vấn đề đồng bộ phức tạp hơn sẽ được học ở module concurrency.

**Purpose:** Chỉ ra tác động phụ ẩn gây sai lệch vết thực thi.
