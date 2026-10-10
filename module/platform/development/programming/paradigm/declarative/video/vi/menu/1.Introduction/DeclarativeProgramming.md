---
video:
  url: ""
---

# Lập trình khai báo: Mô tả kết quả và ràng buộc

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

## Lập trình khai báo: Khái niệm và phạm vi

<!-- VIDEO_SECTION -->

### Scene 1 — Lập trình khai báo: Khái niệm và phạm vi

**Time:** `00:00–01:18`

**Visual:**

Chạy từng hàng 10,-2,25 qua bảng đúng/sai của điều kiện dương và dừng tại [10,25], kèm trục WANT và STEPS.

**Script:**

Hãy nghĩ tới việc chọn các giao dịch dương. Ta có thể bảo máy duyệt từng phần tử và thêm mục đạt điều kiện vào danh sách. Hoặc ta nói kết quả cần những khoản lớn hơn không và để bộ đánh giá quyết định các bước. Lập trình khai báo nhấn vào điều phải đúng, không phủ nhận việc máy vẫn cần chạy thuật toán.

**Purpose:**

Giới thiệu kết quả mong muốn trước bước thực hiện.

## Động cơ tách ý định khỏi các bước thực hiện

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:18–01:30`

**Visual:**

Giữ thẻ điều kiện amount lớn hơn 0 ở giữa; ba đoạn mã lọc riêng cho báo cáo, thống kê và màn hình biến thành cùng một mô tả.

**Script:**

Từ ví dụ vừa rồi, vì sao việc nêu điều kiện lại đáng học, thay vì chỉ viết thêm vòng lặp?

**Purpose:**

Nối WHAT với chi phí mã lặp.

### Scene 1 — Động cơ tách ý định khỏi các bước thực hiện

**Time:** `01:30–02:48`

**Visual:**

Hiển thị ba vùng ứng dụng báo cáo, thống kê, xuất dữ liệu đều tô cùng điều kiện; khoanh một lần sửa chính sách.

**Script:**

Khi cùng điều kiện xuất hiện ở nhiều nơi, chúng ta dễ phải sửa ba vòng lặp mỗi lần chính sách đổi. Một đặc tả riêng cho phép kiểm tra xem điều kiện đã đúng chưa, còn engine chịu trách nhiệm thực hiện. Tuy nhiên câu mô tả ngắn không tự hoàn chỉnh: nếu quên giới hạn tài khoản, kết quả vẫn có thể sai và gây lộ dữ liệu.

**Purpose:**

Cho thấy lợi ích diễn đạt ý định và trách nhiệm không biến mất.

## Đối chiếu mô tả mục tiêu với chuỗi lệnh

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:48–03:00`

**Visual:**

Chiếu một bên các bước tạo list, duyệt, if, append; bên kia chỉ hiện điều kiện số tiền dương; cuối hai cột đều là 10 và 25.

**Script:**

Hai cách cho cùng tập kết quả; vậy khác nhau nằm ở nơi chúng ta đặt trách nhiệm điều khiển.

**Purpose:**

Đưa learner sang so sánh hai lối.

### Scene 1 — Đối chiếu mô tả mục tiêu với chuỗi lệnh

**Time:** `03:00–04:18`

**Visual:**

Cho các thẻ init, loop, if, append lần lượt rơi vào cột imperative; cột declarative chỉ có đặc tả và hàng đạt.

**Script:**

Bên mệnh lệnh, người viết chọn thứ tự duyệt và lúc cập nhật danh sách. Bên khai báo, người viết chỉ nêu tính chất của hàng muốn có. Bộ đánh giá có thể vẫn duyệt từng dòng hoặc dùng cách khác. Vì thế không đúng khi nói declarative đồng nghĩa không có vòng lặp; khác biệt là vòng lặp có nằm trong đặc tả hay không.

**Purpose:**

So sánh trách nhiệm thực thi chứ không phán xét cú pháp.

## Mô hình đặc tả, bộ đánh giá và kết quả

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:18–04:30`

**Visual:**

Vẽ ba hộp declaration, evaluator, result; trong evaluator có hai đường scan và index nét đứt, cùng dẫn đến tập kết quả dương.

**Script:**

Ai thực sự chạy phần việc đã không được mô tả chi tiết trong câu điều kiện?

**Purpose:**

Dẫn đến mô hình evaluator.

### Scene 1 — Mô hình đặc tả, bộ đánh giá và kết quả

**Time:** `04:30–05:48`

**Visual:**

Mở rộng hộp evaluator thành hai luồng đọc khác nhau, gắn nhãn đều phải giữ cùng điều kiện account và amount.

**Script:**

Mô hình có ba phần: yêu cầu được viết ra, bộ đánh giá hiểu nó và kết quả thỏa điều kiện. Nếu dữ liệu có chỉ mục, một engine có thể chọn đường đọc khác thay vì quét toàn bộ. Chúng ta chưa đo một database thật nên những đường này chỉ mô tả khả năng. Dù chọn gì, engine vẫn phải tuân thủ ý nghĩa đặc tả và các ràng buộc đi kèm.

**Purpose:**

Tạo mô hình kiểm chứng được mà không giả số liệu hiệu năng.

## Quan hệ với logic, truy vấn và lập trình hàm

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:48–06:00`

**Visual:**

Từ một hộp WHAT, kéo ba nhánh đến mạng quan hệ parent, bảng giao dịch và dãy phép biến đổi hàm; ghi rõ mỗi nhánh có ngữ nghĩa riêng.

**Script:**

Tư duy này xuất hiện trong logic, truy vấn và cả một số phép biến đổi hàm, nhưng có phải chúng đồng nghĩa?

**Purpose:**

Đưa ví dụ khác loại vào một quan hệ có giới hạn.

### Scene 1 — Quan hệ với logic, truy vấn và lập trình hàm

**Time:** `06:00–07:18`

**Visual:**

Lật lần lượt ba bảng facts/rules, query rows, transformations; tô các nhãn logic, bag rows và value outputs riêng.

**Script:**

Logic programming mô tả quan hệ đúng và điều kiện suy ra kết luận. Truy vấn nêu hàng muốn lấy từ dữ liệu. Hàm có thể ghép các phép biến đổi để mô tả kết quả. Ba lối giao nhau ở cách trình bày ý định nhưng không dùng chung mọi quy tắc. Một tệp cấu hình cũng không tự thành declarative chỉ vì viết dưới dạng YAML.

**Purpose:**

Ngăn đồng nhất ngôn ngữ, file và paradigm.

## Kiến thức nền và trình tự học các phong cách khai báo

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:18–07:30`

**Visual:**

Thu hình thành bốn thẻ giá trị, điều kiện, danh sách, vòng lặp; nối timeline năm chương với dấu hỏi duplicate và order.

**Script:**

Trước khi đi sâu, ta cần mang theo những kiến thức tối thiểu nào?

**Purpose:**

Tạo cầu nối beginner tới các phần sau.

### Scene 1 — Kiến thức nền và trình tự học các phong cách khai báo

**Time:** `07:30–08:48`

**Visual:**

Vẽ đường học năm chương và đặt hai bảng câu hỏi về duplicate và order ở mốc Query.

**Script:**

Chỉ cần biết giá trị, điều kiện, danh sách và cách vòng lặp hoạt động là đủ bắt đầu. Sau đây ta học cách viết yêu cầu đầy đủ, phân biệt bộ đánh giá với kế hoạch chạy, rồi xây quan hệ từ fact và rule. Đến truy vấn, ta sẽ xét hàng trùng lặp và thứ tự. Cuối cùng là lúc chọn kiểu khai báo hay các thao tác mệnh lệnh rõ ràng.

**Purpose:**

Đặt trước các câu hỏi trùng lặp và sắp xếp.
