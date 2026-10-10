---
video:
  url: ""
---

# Đặc tả ý định và chiến lược thực thi

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

## Đặc tả kết quả và các thuộc tính phải thỏa mãn

<!-- VIDEO_SECTION -->

### Scene 1 — Đặc tả kết quả và các thuộc tính phải thỏa mãn

**Time:** `00:00–01:18`

**Visual:**

Đưa số -2 qua cổng loại và giữ 10,25; thêm thẻ top-three chưa có tie-breaker để thấy đặc tả chưa đủ.

**Script:**

Đặc tả nói mỗi phần tử được chấp nhận phải thỏa một tính chất. Ở đây điều kiện số tiền dương chọn mười và hai mươi lăm, còn âm hai bị loại. Nếu đổi yêu cầu thành ba khoản lớn nhất, chúng ta phải bổ sung cách so sánh, cách xử lý đồng hạng và giới hạn. Ngắn gọn không tự bảo đảm yêu cầu đã đầy đủ.

**Purpose:**

Phân biệt thuộc tính chấp nhận và chi tiết bổ sung.

## Điều kiện, vị từ và ràng buộc trong một mô tả

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:18–01:30`

**Visual:**

Từ amount dương, đặt thêm bảng account A và B; giữ hai hàng dương nhưng chỉ tô hàng thuộc tài khoản A.

**Script:**

Ta vừa lọc theo số tiền. Nếu dữ liệu đến từ nhiều tài khoản thì còn cần gì?

**Purpose:**

Chuyển từ một vị từ sang kết hợp ràng buộc.

### Scene 1 — Điều kiện, vị từ và ràng buộc trong một mô tả

**Time:** `01:30–02:48`

**Visual:**

Tô màu hai cột amount và account trong bảng bốn hàng; hai bộ lọc đánh dấu lỗi thiếu quyền truy cập.

**Script:**

Một predicate là điều kiện đúng hoặc sai với một hàng. Yêu cầu có thể là số tiền lớn hơn không và account phải bằng A. Nếu chỉ kiểm tra số tiền, một giao dịch của B vẫn lọt vào kết quả. Đó không phải lỗi của thuật toán quét hay chỉ mục; lỗi nằm trong ý định chưa nêu giới hạn quyền truy cập.

**Purpose:**

Dùng tình huống bảo mật làm bằng chứng về completeness.

## Mức độ đầy đủ và tính mơ hồ của đặc tả

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:48–03:00`

**Visual:**

Làm mờ chữ gần đây, bật ba đồng hồ created, posted, modified; thêm hai giao dịch trùng thời điểm và nút tie-breaker.

**Script:**

Với câu lấy giao dịch gần đây, liệu hai người đọc đã hiểu cùng một yêu cầu?

**Purpose:**

Dẫn đến ambiguity và tie breaking.

### Scene 1 — Mức độ đầy đủ và tính mơ hồ của đặc tả

**Time:** `03:00–04:18`

**Visual:**

Chiếu ba nhãn thời gian created, posted, modified trên cùng hai giao dịch và biểu tượng khóa phụ khi thời gian bằng nhau.

**Script:**

Gần đây có thể tính theo lúc tạo, lúc ghi sổ hoặc lúc chỉnh sửa. Một câu không nêu cột thời gian và số lượng bản ghi vẫn có nhiều cách hiểu. Nếu cần danh sách đúng ba mục, chúng ta còn phải chỉ thứ tự và cách xử lý các giao dịch cùng timestamp. Nhiều cách hiểu đều có thể hợp lệ, nhưng muốn kết quả duy nhất thì phải nói rõ tiêu chí chọn.

**Purpose:**

Chỉ ra lúc cần ràng buộc bổ sung.

## Bộ đánh giá: Vai trò biến mô tả thành phép tính

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:18–04:30`

**Visual:**

Giữ đặc tả khoản dương ở đầu, bên dưới vẽ hai đường quét bảng và tra chỉ mục, cả hai về cùng các hàng được phép.

**Script:**

Khi mô tả đã đủ rõ, công cụ làm gì để tạo kết quả?

**Purpose:**

Giới thiệu engine như người thi hành chứ không suy đoán ý định.

### Scene 1 — Bộ đánh giá: Vai trò biến mô tả thành phép tính

**Time:** `04:30–05:48`

**Visual:**

Bảng 10000 hàng ở giữa và hai đường scan/index dẫn tới cùng hai hàng hợp lệ; bảng cảnh báo không có phép đo thật.

**Script:**

Một evaluator phải lấy dữ liệu thật, kiểm tra các vị từ rồi xây đầu ra. Với truy vấn, nó có thể quét từng hàng hoặc sử dụng chỉ mục khi môi trường hỗ trợ. Chúng ta mới diễn tả hai khả năng, chưa chạy optimizer. Cả hai chiến lược đều phải tôn trọng account và amount. Nếu thiếu account trong truy vấn, engine không tự đoán mục đích bảo mật để sửa.

**Purpose:**

Cụ thể hóa lựa chọn HOW và giới hạn của nó.

## Kết quả theo ngữ nghĩa và kế hoạch thực thi cụ thể

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:48–06:00`

**Visual:**

Đặt hai hình kết quả cùng các hàng, nhưng thứ tự hiển thị khác; gắn nhãn membership và ordering trên hai tầng riêng.

**Script:**

Engine có thể chọn đường đi khác, vậy thế nào là cùng một kết quả đúng?

**Purpose:**

Dẫn sang ngữ nghĩa khác kế hoạch.

### Scene 1 — Kết quả theo ngữ nghĩa và kế hoạch thực thi cụ thể

**Time:** `06:00–07:18`

**Visual:**

Hiện hai bảng kết quả có cùng các hàng nhưng đảo vị trí; thêm ORDER BY và id tie-breaker để cố định hiển thị.

**Script:**

Ngữ nghĩa của truy vấn cho biết những hàng và số bản sao nào được chấp nhận. Kế hoạch chỉ giải thích engine lấy được chúng bằng cách nào. Nếu truy vấn không yêu cầu thứ tự, việc một lần chạy hiện hàng A trước không phải hợp đồng cho lần sau. Khi nghiệp vụ cần danh sách ổn định, ta phải nêu ORDER BY và khóa phụ nếu dữ liệu có thể bằng nhau.

**Purpose:**

Không lấy thứ tự tình cờ làm guarantee.

## Sự khác nhau giữa chiến lược, thứ tự quan sát và hiệu năng

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:18–07:30`

**Visual:**

Trên màn hình đặt mô hình 100 hàng và một triệu hàng, hai đường plan giả định không ghi thời gian; thêm thẻ update/log có cảnh báo.

**Script:**

Khi che đi các bước chạy, chúng ta đã đánh đổi điều gì?

**Purpose:**

Khép chương bằng performance và effects.

### Scene 1 — Sự khác nhau giữa chiến lược, thứ tự quan sát và hiệu năng

**Time:** `07:30–08:48`

**Visual:**

So bảng dữ liệu nhỏ/lớn với nhãn tài nguyên, sau đó hoán đổi hai thẻ write và notify làm bật cảnh báo effect.

**Script:**

Một mô tả đẹp không xóa chi phí CPU, bộ nhớ hoặc I/O. Khi dữ liệu lớn lên, engine có thể chọn kế hoạch khác và ta cần đo bằng công cụ thật. Ngoài ra, thay thứ tự hai phép tính độc lập khác hẳn đổi thứ tự việc ghi và thông báo. Tác động phụ khiến quyền thay đổi chiến lược bị giới hạn bởi hợp đồng của hệ thống.

**Purpose:**

Ngăn ngộ nhận tối ưu miễn phí và tự do đổi thứ tự.
