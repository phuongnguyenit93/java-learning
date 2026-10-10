---
video:
  url: ""
---

# Lập trình mệnh lệnh: Cách diễn đạt phép tính bằng thao tác

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

## Lập trình mệnh lệnh: Khái niệm và phạm vi

<!-- VIDEO_SECTION -->

### Scene 1 — Lập trình mệnh lệnh: Khái niệm và phạm vi

**Time:** 00:00–01:20

**Visual:** Vẽ hai ô: COMMAND và balance=100. Chạy thử lệnh chỉ đọc rồi lệnh trừ 30; chỉ tô ô số dư khi thao tác ghi xảy ra.

**Script:** Ta bắt đầu bằng một câu hỏi đơn giản: chương trình vừa làm gì? Khi xem balance bằng 100, dữ liệu chưa thay đổi. Khi gán balance bằng balance trừ 30, nó trở thành 70. Lập trình mệnh lệnh kể lại phép tính bằng những hành động theo thứ tự. Một hành động có thể đọc, tính, xuất thông báo hoặc sửa trạng thái; không phải bước nào cũng ghi dữ liệu.

**Purpose:** Phân biệt việc thực hiện lệnh với việc sửa trạng thái trong ví dụ đầu tiên.

## Vai trò của việc kiểm soát từng bước thực thi

<!-- VIDEO_SECTION -->

### Transition

**Time:** 01:20–01:33

**Visual:** Khóa bảng trạng thái ở số dư 100, kéo thẻ READ sang bên để thấy số dư giữ nguyên; chỉ đưa CHECK đứng trước DEBIT và đặt dấu hỏi lên mũi tên nối hai thẻ, chưa chạy tình huống rút tiền.

**Script:** Thao tác nào làm trạng thái thay đổi đã rõ; vì sao việc đặt chúng đúng thứ tự quan trọng?

**Purpose:** Chuyển từ phân biệt lệnh đọc/ghi sang câu hỏi vì sao thứ tự CHECK trước DEBIT bảo vệ trạng thái, để Scene kế tiếp tự chứng minh bằng số dư âm.

### Scene 2 — Vai trò của việc kiểm soát từng bước thực thi

**Time:** 01:33–02:48

**Visual:** Chiếu hai thẻ CHECK và DEBIT. Với balance=20, rút 30, thử đảo vị trí để hiện -10 trước khi sửa thành từ chối.

**Script:** Hãy thử trừ tiền trước rồi mới kiểm tra. Từ số dư 20, rút 30 tạo ra âm 10. Nếu kiểm tra trước, chương trình từ chối và giữ nguyên số dư. Công thức trừ không sai; chính thứ tự đã làm quy trình sai. Khi bài toán có hành động ảnh hưởng lẫn nhau, việc điều khiển bước nào được thực hiện trước trở thành một phần của yêu cầu nghiệp vụ.

**Purpose:** Dùng phản ví dụ số dư âm để chứng minh giá trị của sequencing.

## Bài toán cần thứ tự thao tác tường minh

<!-- VIDEO_SECTION -->

### Transition

**Time:** 02:48–03:01

**Visual:** Giữ dấu `-10` đỏ từ thử nghiệm rút trước kiểm tra; kéo nó khỏi luồng và dựng cổng VALID ở đầu vào của tài khoản 100, chưa chạy phép rút 30.

**Script:** Chúng ta cần một tình huống đủ nhỏ để kiểm tra từng bước chứ không chỉ nhìn kết quả.

**Purpose:** Dùng sai lệch số dư âm để đặt câu hỏi về trình tự kiểm tra một yêu cầu mới.

### Scene 3 — Bài toán cần thứ tự thao tác tường minh

**Time:** 03:01–04:16

**Visual:** Trên bảng trắng ghi balance 100 và request 30. Xếp bốn thẻ: kiểm tra số tiền, kiểm tra số dư, trừ tiền, báo kết quả.

**Script:** Nếu chỉ thấy kết quả 70, chưa thể biết chương trình làm đúng chưa. Ta còn phải kiểm tra tiền yêu cầu là số dương, số dư có đủ không, rồi mới trừ tiền và thông báo. Tôi sẽ dùng chính ví dụ tài khoản 100 này trong cả loạt bài: trước tiên rút 30, sau đó thử rút 90. Mỗi bước đều có đầu vào, điều kiện và kết quả quan sát được.

**Purpose:** Thiết lập ví dụ xuyên suốt giúp người xem hiểu tại sao cần vết thực thi.

## Mô hình lệnh, trạng thái và kết quả

<!-- VIDEO_SECTION -->

### Transition

**Time:** 04:16–04:29

**Visual:** Từ bốn thẻ kiểm tra, trừ và báo cáo, kéo riêng biểu thức `100−30=70` ra ô nháp trong khi ô nhớ vẫn hiện `balance=100`.

**Script:** Đã có chuỗi hành động, nhưng cần tách số được tính ra khỏi số đã được lưu.

**Purpose:** Đưa người xem từ chuỗi thao tác đến ranh giới giữa giá trị tính ra và giá trị được lưu.

### Scene 4 — Mô hình lệnh, trạng thái và kết quả

**Time:** 04:29–05:44

**Visual:** Chia đôi màn hình: phép tính 100-30 ra 70 ở ô giấy nháp; phép gán balance=balance-30 biến ô số dư từ 100 thành 70.

**Script:** Hai dòng có thể cùng tạo con số 70 nhưng tác động khác nhau. Phép trừ chỉ cho ta một giá trị. Phép gán mới đặt giá trị đó vào vị trí đang lưu số dư. Khi lần theo chương trình, hãy ghi lệnh đang chạy, trạng thái trước và trạng thái sau. Với bước chỉ đọc hoặc in, hai trạng thái có thể giống nhau. Đó là cách phát hiện nơi cập nhật thật mà không suy đoán từ tên lệnh.

**Purpose:** Làm rõ mô hình lệnh–giá trị–trạng thái bằng phép gán cụ thể.

## Quan hệ với lập trình khai báo, hướng đối tượng và hàm

<!-- VIDEO_SECTION -->

### Transition

**Time:** 05:44–05:57

**Visual:** Giữ hai ô `giá trị tính=70` và `balance=70`; từ ô thao tác rẽ ba mũi tên đến QUY TẮC, CÂU LỆNH và ACCOUNT mà chưa phát lại phép rút.

**Script:** Vậy lối mệnh lệnh có đối lập với cách mô tả quy tắc hay với các đối tượng không?

**Purpose:** Dẫn từ phép gán cụ thể đến ba góc nhìn bổ sung trong cùng một nghiệp vụ.

### Scene 5 — Quan hệ với lập trình khai báo, hướng đối tượng và hàm

**Time:** 05:57–07:12

**Visual:** Ba cột cùng bài toán: thao tác CHECK/DEBIT, quy tắc đủ tiền, và đối tượng ACCOUNT giữ số dư. Nối các cột bằng mũi tên.

**Script:** Ta có thể mô tả cùng một vấn đề dưới nhiều góc nhìn. Kiểm tra rồi gán là các bước mệnh lệnh. Quy tắc chỉ rút khi đủ tiền tập trung vào điều phải đúng, gần với khai báo. Đối tượng tài khoản có thể sở hữu trách nhiệm bảo vệ trạng thái. Những lối nhìn này không loại trừ nhau trong một ứng dụng. Ở đây ta chỉ học cách suy luận theo thứ tự; cú pháp Java và thiết kế đối tượng sẽ có bài riêng.

**Purpose:** Giữ ranh giới paradigm và tránh nhầm một chương trình chỉ thuộc một phong cách.

## Điểm xuất phát và hành trình học: Từ thao tác đến tổ chức thủ tục

<!-- VIDEO_SECTION -->

### Transition

**Time:** 07:12–07:25

**Visual:** Thu ba góc nhìn CHECK/QUY TẮC/ACCOUNT thành một thẻ đơn A; dưới thẻ bật tuyến STATE → BRANCH → PROCEDURE → CORRECTNESS.

**Script:** Nếu mới gặp những thuật ngữ này, nên học thứ tự nào để không bị ngợp?

**Purpose:** Chuyển so sánh giữa các paradigm thành lộ trình học về thao tác và kiểm chứng.

### Scene 6 — Điểm xuất phát và hành trình học: Từ thao tác đến tổ chức thủ tục

**Time:** 07:25–08:40

**Visual:** Hiện năm thẻ nối tiếp: MODEL → STATE → BRANCH/LOOP → PROCEDURE → CORRECTNESS. Trong mỗi thẻ hiện một mảnh ví dụ số dư.

**Script:** Ta đã có bức tranh ban đầu. Video sau sẽ chỉ cách nhìn trạng thái trước và sau lệnh. Từ đó mới đến điều kiện chọn nhánh và vòng lặp, rồi gom các bước thành thủ tục có trách nhiệm rõ. Cuối cùng ta dùng tiền điều kiện và hậu điều kiện để xem kết quả đúng chưa. Bạn chỉ cần biết biến, phép tính và điều kiện cơ bản, chưa cần học sâu bộ nhớ hay đa luồng.

**Purpose:** Tạo bản đồ học để các chapter có tuyến tính và giữ đúng prerequisite.
