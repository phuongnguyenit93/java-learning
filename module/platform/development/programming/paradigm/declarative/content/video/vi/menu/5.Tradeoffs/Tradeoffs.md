---
video:
  url: ""
---

# Suy luận, đánh đổi và liên hệ với các paradigm

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

## Ưu điểm của việc làm rõ ý định hơn chi tiết thao tác

<!-- VIDEO_SECTION -->

### Scene 1 — Ưu điểm của việc làm rõ ý định hơn chi tiết thao tác

**Time:** `00:00–01:18`

**Visual:**

Khối loop dài thu nhỏ cạnh đặc tả account=A AND amount>0, đánh dấu đủ hai điều kiện.

**Script:**

Khi yêu cầu được viết thành điều kiện account A và amount dương, người review có thể kiểm tra ngay hai ràng buộc nghiệp vụ. Trong vòng lặp dài, chúng đôi khi bị chìm trong chi tiết thêm vào danh sách. Một engine cũng có thể đổi chiến lược khi dữ liệu tăng. Nhưng câu truy vấn ngắn mà quên điều kiện account không tốt hơn code mệnh lệnh đúng.

**Purpose:**

Nêu ưu thế clarity nhưng có điều kiện.

## Chi phí khi phụ thuộc bộ đánh giá và công cụ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:18–01:30`

**Visual:**

Giữ query ngắn phía trên, mở hai hộp dữ liệu 100 hàng và một triệu hàng bên dưới; đồng hồ không ghi số bịa.

**Script:**

Nếu ta không chỉ định cách chạy, liệu mọi plan đều có chi phí như nhau?

**Purpose:**

Dẫn sang performance và inspect plan.

### Scene 1 — Chi phí khi phụ thuộc bộ đánh giá và công cụ

**Time:** `01:30–02:48`

**Visual:**

Chiếu hai kích thước bảng 100 và một triệu, giữ thước đo trống và yêu cầu chạy EXPLAIN thật mới có số.

**Script:**

Một truy vấn đọc một triệu hàng có thể đắt hơn nhiều so với một trăm hàng. Engine còn phải đối phó thống kê, chỉ mục, bộ nhớ và điều kiện dữ liệu. Khi hệ thống chậm, người vận hành cần dùng công cụ thật để xem execution plan và đo chi phí. Declarative làm rõ mục tiêu, chứ không biến chi phí phần cứng thành không. Chúng ta không học SQL tuning chi tiết trong phần này.

**Purpose:**

Đòi evidence khi bàn performance, không hứa hẹn.

## Đặc tả không đầy đủ và kết quả ngoài dự kiến

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:48–03:00`

**Visual:**

Bảng ba lỗi: thiếu ORDER BY, thiếu account=A, hai hàng amount 50; mỗi lỗi có thẻ điều kiện bị bỏ quên.

**Script:**

Engine có thể tuân thủ câu truy vấn mà màn hình vẫn sai. Ta tìm thiếu sót ở đâu?

**Purpose:**

Dẫn sang completeness và unexpected outcomes.

### Scene 1 — Đặc tả không đầy đủ và kết quả ngoài dự kiến

**Time:** `03:00–04:18`

**Visual:**

Đưa ba lỗi thiếu order, thiếu ownership và duplicate amount vào ba ô kiểm tra đặc tả riêng.

**Script:**

Nếu danh sách hiển thị đảo thứ tự, hỏi đã khai báo order chưa. Nếu tài khoản B xuất hiện, xem điều kiện chủ tài khoản có bị bỏ không. Nếu hai hàng năm mươi xuất hiện, phải biết chúng là hai giao dịch hợp lệ hay người dùng chỉ muốn một amount duy nhất. Test nên đưa vào bản sao, cùng timestamp và hàng ngoài phạm vi. Sửa đặc tả trước khi tối ưu engine.

**Purpose:**

Chẩn đoán lỗi do WHAT sai thay vì đổ cho HOW.

## Giới hạn điều khiển thứ tự và tác động phụ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:18–04:30`

**Visual:**

Vẽ hai flow: truy vấn chỉ đọc và chuỗi update rồi notify; đổi thứ tự notify và update để lộ thông báo gửi sớm.

**Script:**

Khi mô tả khai báo dẫn tới ghi hoặc gửi, thứ tự thực hiện còn là chi tiết phụ không?

**Purpose:**

Dẫn đến hạn chế đối với hiệu ứng.

### Scene 1 — Giới hạn điều khiển thứ tự và tác động phụ

**Time:** `04:30–05:48`

**Visual:**

Hai bước update và notify đảo vị trí, đường gửi thông báo sáng đỏ nếu save chưa thành công.

**Script:**

Một truy vấn read-only khác với quá trình thực sự cập nhật sổ và gửi thông báo. Nếu thông báo được phát trước lúc ghi thành công, người dùng thấy trạng thái sai; retry có thể gửi lặp. Không có định nghĩa chung nào nói một biểu thức khai báo luôn thuần hoặc có thể hoán đổi mọi effect. Hợp đồng engine, transaction và chính sách idempotency mới quyết định điều nào an toàn.

**Purpose:**

Bảo vệ temporal correctness và idempotency.

## Đối chiếu lời giải khai báo với điều khiển mệnh lệnh

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:48–06:00`

**Visual:**

Bên trái cộng dồn 10,-2,25 theo lệnh, bên phải khai báo sum của khoản dương; cả hai hiện 35; thêm flow validate, charge, save phía dưới.

**Script:**

Có thể cùng giải một vấn đề bằng hai lối. Khi nào nên giữ các bước mệnh lệnh tường minh?

**Purpose:**

Kết luận tradeoff bằng use case.

### Scene 1 — Đối chiếu lời giải khai báo với điều khiển mệnh lệnh

**Time:** `06:00–07:18`

**Visual:**

Với [10,-2,25], bên trái chạy accumulator, bên phải sum sau filter, dừng chung tại 35 và tách checkout effects.

**Script:**

Với danh sách này, lọc dương rồi cộng tạo ba mươi lăm, bất kể dùng vòng lặp hay biểu thức khai báo. Khi trọng tâm là chọn dữ liệu, mô tả kết quả có thể đơn giản hơn. Khi cần xác nhận, cập nhật rồi gửi thông báo theo thứ tự, các lệnh tường minh giúp thấy luồng hiệu ứng. Một ứng dụng có thể dùng SQL để chọn dữ liệu và imperative coordinator cho phần còn lại.

**Purpose:**

Cho tiêu chí phối hợp thay vì bắt chọn một nhãn.

## Mối liên hệ với functional và các module công nghệ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:18–07:30`

**Visual:**

Thu về sơ đồ intent, evaluator, result; ba mũi tên dẫn sang SQL engine thật, Prolog thật và functional transformations; không hiện nút HTTP giả.

**Script:**

Sau chương này, điều quan trọng nhất người học nên tự hỏi khi gặp một mô tả mới là gì?

**Purpose:**

Đưa học viên tới các module sở hữu thí nghiệm thật.

### Scene 1 — Mối liên hệ với functional và các module công nghệ

**Time:** `07:30–08:48`

**Visual:**

Mở bảng checklist kết quả, trùng lặp, thứ tự, fact, effect và ba đường dẫn sang SQL/Prolog/functional.

**Script:**

Hãy hỏi điều kiện đã đủ chưa, trùng lặp có được phép không, thứ tự có được quy định không, fact nào chứng minh kết luận, và ai kiểm soát tác động bên ngoài. Muốn kiểm tra SQL SELECT ALL, DISTINCT và ORDER BY, hãy dùng hệ quản trị thật trong module database. Muốn xem Prolog tìm lời giải, dùng trình thông dịch tương ứng. Phần này dạy cách phân biệt WHAT với HOW, không tự dựng endpoint trả lại đáp án được viết sẵn.

**Purpose:**

Kết thúc bằng checklist và ranh giới ownership công nghệ.
