---
video:
  url: ""
---

# Tính đúng đắn, đánh đổi và liên hệ các lối lập trình

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

## Điều kiện đầu vào, bước chuyển và kết quả mong đợi

<!-- VIDEO_SECTION -->

### Scene 1 — Điều kiện đầu vào, bước chuyển và kết quả mong đợi

**Time:** 00:00–01:20

**Visual:** Hiện bảng PRE–ACTION–POST. Rút 30 từ 100: điều kiện đúng, hậu trạng thái 70. Rút 90 từ 70: điều kiện sai, hậu trạng thái vẫn 70.

**Script:** Để kiểm tra rút tiền, đừng chỉ chạy một trường hợp đẹp. Với 100 và yêu cầu 30, đầu vào hợp lệ nên số dư sau phải là 70 và không âm. Khi chỉ còn 70 mà yêu cầu 90, nhánh từ chối phải giữ số dư 70. Tiền điều kiện cho biết lúc nào được hành động; hậu điều kiện nói điều gì phải đúng sau đó. Hai trường hợp này là bằng chứng về tính đúng của đường thực thi chứ không chỉ của phép trừ.

**Purpose:** Chuyển ví dụ cũ thành tiêu chí thử đúng/sai quan sát được.

## Rủi ro của cập nhật trạng thái và thứ tự thao tác

<!-- VIDEO_SECTION -->

### Transition

**Time:** 01:20–01:33

**Visual:** Giữ PRE/POST của lần rút bị từ chối, tô điều kiện `balance không đổi=70` và đặt READ/WRITE lên một timeline.

**Script:** Hợp đồng rõ rồi; vậy vì sao vẫn dễ sai khi nhiều lệnh cùng đọc và ghi trạng thái?

**Purpose:** Dẫn từ kiểm chứng hậu điều kiện đến rủi ro đọc và ghi sai phiên bản hoặc thứ tự.

### Scene 2 — Rủi ro của cập nhật trạng thái và thứ tự thao tác

**Time:** 01:33–02:48

**Visual:** Xếp thẻ READ, DISCOUNT, WRITE. Cho DISCOUNT chạy hai lần; rồi đánh dấu phí đọc phiên bản balance cũ sau khi đã cập nhật.

**Script:** Có những lỗi không nằm trong công thức. Giảm giá hai lần, tính phí từ số dư cũ, hoặc ghi dữ liệu ở nhánh không nên chạy đều có thể tạo kết quả sai. Khi nhiều bước đụng cùng biến, ta phải biết ai có quyền ghi và mỗi bước đang đọc trạng thái ở thời điểm nào. Hãy thử đánh dấu lần cập nhật gần nhất trước từng phép tính. Các vấn đề khóa và đa luồng có bài riêng; ở đây ta tập trung lịch sử một luồng.

**Purpose:** Thấy được rủi ro từ thứ tự và dữ liệu stale thay vì chỉ số học.

## Truy vết lỗi qua lịch sử thay đổi trạng thái

<!-- VIDEO_SECTION -->

### Transition

**Time:** 02:48–03:01

**Visual:** Đưa số dư âm 20 từ nhánh sai vào hàng `before | branch | after`, rồi khoanh ô last WRITE đang trống.

**Script:** Nếu đầu ra đã sai, một bảng lịch sử giúp lần ngược tới lệnh gây sai như thế nào?

**Purpose:** Từ một trạng thái sai đưa người học đến chiến lược truy lệnh ghi gây lỗi.

### Scene 3 — Truy vết lỗi qua lịch sử thay đổi trạng thái

**Time:** 03:01–04:16

**Visual:** Trong bảng hai lần rút, đổi dòng rút 90 thành balance 70→-20. Tô ô âm 20, lùi mũi tên đến nhánh kiểm tra không bảo vệ phép gán.

**Script:** Đây là lần chạy cố ý sai. Rút 30 còn 70, nhưng rút tiếp 90 làm số dư âm 20. Ta có thể lần ngược từ hậu điều kiện số dư không âm tới thao tác vừa ghi -20. Tại sao lệnh trừ vẫn chạy khi không đủ tiền? Bảng ghi số dư trước, số tiền, điều kiện, nhánh và số dư sau sẽ chỉ ra lệnh cập nhật đã nằm ngoài nhánh bảo vệ. Debug bằng lịch sử cụ thể đáng tin hơn sửa ngẫu nhiên.

**Purpose:** Dạy chiến lược tìm lỗi từ trạng thái sai tới câu lệnh ghi gần nhất.

## Lợi ích và chi phí của điều khiển thực thi tường minh

<!-- VIDEO_SECTION -->

### Transition

**Time:** 04:16–04:29

**Visual:** Rút gọn bảng vết dài thành hai thẻ `điều khiển từng bước` và `mô tả kết quả`, không chọn thẻ thắng.

**Script:** Chúng ta đã hiểu lợi ích của từng bước; nhưng có bài toán nào mô tả ý định sẽ đơn giản hơn?

**Purpose:** Đặt câu hỏi liệu mức chi tiết cần cho debug cũng luôn cần trong mọi lời giải.

### Scene 4 — Lợi ích và chi phí của điều khiển thực thi tường minh

**Time:** 04:29–05:44

**Visual:** Đặt hai slide cạnh nhau: một flow có nhiều vòng lặp và nhánh, một tấm thẻ ghi yêu cầu tổng các khoản dương. Zoom vào ô kiểm tra và chỗ dễ nhầm.

**Script:** Điều khiển lệnh từng bước rất hợp với việc rút tiền hoặc điều khiển thiết bị, nơi thứ tự tác động mang ý nghĩa. Nhưng khi chỉ cần tìm các mục thỏa một điều kiện, hàng loạt chỉ số và biến cộng dồn có thể che mục tiêu thật. Không nên chọn một phong cách vì tên nghe hiện đại. Hãy tự hỏi cách nào cho người đọc thấy điều cần kiểm chứng rõ hơn và ít chỗ sai hơn.

**Purpose:** Đưa tiêu chí lựa chọn paradigm dựa trên loại bài toán.

## Đối chiếu lời giải mệnh lệnh và mô tả khai báo

<!-- VIDEO_SECTION -->

### Transition

**Time:** 05:44–05:57

**Visual:** Giữ vòng lặp với chỉ số bên trái, câu `tổng khoản dương` bên phải; ghim cùng dữ liệu `[10,-2,25]` giữa hai thẻ.

**Script:** Với đúng một danh sách số, ta có thể so hai lối diễn đạt mà không đổi đáp số.

**Purpose:** Chuẩn bị so sánh công bằng hai cách diễn đạt dựa trên cùng đầu vào.

### Scene 5 — Đối chiếu lời giải mệnh lệnh và mô tả khai báo

**Time:** 05:57–07:12

**Visual:** Chạy con trỏ qua [10,-2,25]. Ở màn hình trái, cộng chỉ số 10 rồi 25 để được 35; màn hình phải hiện yêu cầu sum positive values.

**Script:** Nếu viết mệnh lệnh, tôi lặp qua ba khoản 10, âm 2, 25; kiểm tra từng khoản dương rồi cập nhật tổng. Kết quả là 35. Còn cách mô tả khai báo nói “tổng các khoản dương” và để bộ đánh giá quyết định các bước thực hiện. Hai cách có thể cho cùng con số, nhưng khác mức độ người viết phải quản lý luồng. Cách khai báo không có nghĩa máy không làm việc hoặc mọi thứ đều không có tác động phụ.

**Purpose:** Đặt imperative và declarative cạnh nhau với kết quả số học thống nhất.

## Kết hợp thủ tục với các paradigm khác và phạm vi Java

<!-- VIDEO_SECTION -->

### Transition

**Time:** 07:12–07:25

**Visual:** Cố định tổng 35 ở giữa hai mô hình; nối ô PROCEDURE tới OBJECT và FUNCTION, giữ câu hỏi `lệnh nào chạy tiếp?`.

**Script:** Sự khác biệt là góc nhìn; vậy thủ tục mệnh lệnh vẫn nằm ở đâu trong Java và OOP?

**Purpose:** Từ so sánh imperative/declarative chuyển sang khả năng phối hợp procedure với các paradigm.

### Scene 6 — Kết hợp thủ tục với các paradigm khác và phạm vi Java

**Time:** 07:25–08:40

**Visual:** Ghép thẻ PROCEDURE với OBJECT METHOD, PURE TRANSFORM và Java source; cuối cùng hiện lại ba câu hỏi command, before/after, condition.

**Script:** Lập trình thủ tục là một cách gom các thao tác mệnh lệnh để gọi lại, không phải một đối thủ hoàn toàn khác. Một phương thức của đối tượng Java có thể thực hiện các bước tuần tự. Một phép biến đổi kiểu hàm cũng có thể nằm trong một thủ tục trước khi ứng dụng cập nhật trạng thái. Sau loạt video này, hãy giữ ba câu hỏi: lệnh nào được thực hiện, trạng thái đổi như thế nào, và điều kiện nào cho phép cập nhật? Cú pháp Java cùng thiết kế OOP, functional sẽ có bài học chuyên sâu riêng.

**Purpose:** Kết thúc bằng bộ câu hỏi có thể áp dụng vào code của người học và handoff đúng owner.
