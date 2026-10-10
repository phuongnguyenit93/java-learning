---
video:
  url: ""
---

# Lựa chọn thiết kế hướng dữ liệu và giới hạn

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

## Lựa chọn cách tiếp cận dữ liệu hay trách nhiệm đối tượng

<!-- VIDEO_SECTION -->
### Scene 1 — Chọn dữ liệu trước hay hành vi trước?

**Time:** `00:00–01:31`

**Visual:**

Giữ đơn A; một nhánh biểu diễn map+operations, nhánh kia là Order object bảo vệ invariant.

**Script:**

Không có đáp án rằng map và hàm riêng luôn tốt hơn đối tượng. Khi nhiều phép báo cáo, kiểm tra và tính toán cần đọc chung một hình dạng dữ liệu, biểu diễn map/list và schema riêng có thể làm trách nhiệm minh bạch. Nhưng nếu một đối tượng phải bảo vệ invariant, quyền thay đổi trạng thái hoặc vòng đời riêng, việc giữ hành vi bên trong đối tượng lại có lý. Trên đơn A, ta có thể dùng dữ liệu phổ dụng ở cổng nhận và vẫn để hệ thống thanh toán sở hữu quy trình trả tiền. Hãy lựa chọn theo trách nhiệm cần bảo vệ, không theo số lớp hay số dòng code.

**Purpose:**

Đưa ra tiêu chí lựa chọn DOP hay OOP qua cùng bài toán đơn hàng thay vì tuyệt đối hóa một paradigm.

## Chi phí schema, khả năng quan sát và đánh đổi đóng gói

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:31–01:45`

**Visual:**

Đưa lỗi `qty:'2'` vào map và lớp schema bảo vệ, song song icon đóng gói.

**Script:**

Đã chọn map cho ranh giới ngoài, những chi phí nào đi kèm?

**Purpose:**

Cân nhắc cost of flexibility cùng dữ liệu.

### Scene 2 — Map dễ nhìn cũng dễ sai

**Time:** `01:45–03:16`

**Visual:**

So sánh `qty:'2'` và `qty:2`: payload đầu 422, payload sau 200. Bên cạnh liệt kê schema/diagnostics/test như khoản chi phí phải trả.

**Script:**

Cấu trúc phổ dụng giúp nhìn và chuyển dữ liệu dễ, nhưng không tự ngăn người gửi thiếu giá, sai khóa hay gửi số lượng dạng chuỗi. API của chúng ta trả 422 cho shape sai nhờ schema độc lập; nếu bỏ schema, phép tính có thể sai hoặc vỡ trong runtime. Đóng gói có thể bảo vệ quyền thay đổi trạng thái, còn tính mở tạo nhu cầu kiểm tra nhiều hơn. Chi phí này phải tính vào thiết kế và bảo trì, không chỉ tính số dòng code.

**Purpose:**

Định lượng đánh đổi map bằng trường hợp accepted/rejected chạy thật.

## Điểm chung và khác biệt với lập trình hàm

<!-- VIDEO_SECTION -->
### Transition

**Time:** `03:16–03:30`

**Visual:**

Đặt hàm `total` thuần bên cạnh schema/map và hiệu ứng I/O.

**Script:**

Bản mới bất biến khá giống cách tư duy hàm; vậy hai paradigm có phải một không?

**Purpose:**

Phân biệt DOP responsibility với FP purity.

### Scene 3 — Giao điểm nhưng không đồng nhất

**Time:** `03:30–05:01`

**Visual:**

Vẽ vòng giao nhau có `immutable values / transformations`. Phía DOP thêm `generic representation / separate schema`; phía FP thêm `purity / referential transparency`; đặt adapter HTTP bên ngoài cả hai.

**Script:**

Lập trình hàm nhấn mạnh hàm thuần, minh bạch tham chiếu, ghép phép tính và giới hạn tác động phụ. Sharvit DOP dùng dữ liệu bất biến và thao tác độc lập, nhưng trọng tâm khác là biểu diễn dữ liệu phổ dụng và schema tách rời. Một hàm DOP có thể không thuần nếu nó đọc thông tin bên ngoài. Ngược lại, một hàm thuần không bắt buộc dùng map. Hai cách tổ chức hỗ trợ nhau trong lõi tính tiền, nhưng không đồng nghĩa ở mọi ứng dụng.

**Purpose:**

Chống nhầm DOP với FP và API HTTP với hàm thuần.

## Mô hình lập trình và Data-Oriented Design theo bố trí bộ nhớ

<!-- VIDEO_SECTION -->
### Transition

**Time:** `05:01–05:15`

**Visual:**

Chuyển từ map/schema sang hình CPU cache/memory layout.

**Script:**

Hai mô hình lập trình cùng quan tâm dữ liệu; còn một thuật ngữ có vẻ giống nhưng trọng tâm khác hẳn.

**Purpose:**

Phân biệt programming model khỏi optimization oriented DOD.

### Scene 4 — DOP khác tối ưu bố trí bộ nhớ

**Time:** `05:15–06:46`

**Visual:**

So sánh `Map/List + schema` với mảng struct-of-arrays, cache line và SIMD ở cột DOD, gắn nhãn chuyên đề hiệu năng, không chạy benchmark giả.

**Script:**

Data-Oriented Design trong tối ưu hiệu năng hỏi dữ liệu nằm trên bộ nhớ ra sao để CPU đọc nhanh, cache có hiệu quả và có thể xử lý theo lô. Đây không phải bốn nguyên tắc Sharvit. Dùng map linh hoạt có thể còn kém locality hơn cấu trúc chuyên biệt; không được hứa DOP theo Sharvit sẽ tự tăng tốc. Muốn kết luận tối ưu phải có đo đạc và kiến thức phần cứng. Module hiện tại chỉ đặt ranh giới, không dạy SIMD hay layout chi tiết.

**Purpose:**

Định vị DOD theo phần cứng và không bịa benchmark.

## Quyết định đầu cuối và hướng học tiếp ở module chuyên sâu

<!-- VIDEO_SECTION -->
### Transition

**Time:** `06:46–07:00`

**Visual:**

Thu toàn bộ sơ đồ thành một đường nhận/validate/calculate/derive và bảng quyết định.

**Script:**

Đã biết chọn mô hình và giới hạn; quay lại đơn A để tổng hợp bằng chứng thật.

**Purpose:**

Khép module bằng chứng cứ và hướng học đúng owner.

### Scene 5 — Bốn nguyên tắc và một quyết định thực tế

**Time:** `07:00–08:31`

**Visual:**

Timeline đơn A: JSON map P2×30,Q1×40 → Schema 200/422 → subtotal100 → discount10%=90 → before/after pending. Góc ngoài gắn Java Amber typed, FP purity, Web/DB, DOD perf vào các nhánh chuyên môn.

**Script:**

Nếu người dùng cần gửi đơn A, ta có thể dùng một map/list dễ quan sát. Schema độc lập chặn dữ liệu sai trước phép tính. Hàm riêng tính một trăm và tạo bản giảm giá còn chín mươi, giữ snapshot ban đầu. HTTP 200 và 422 là bằng chứng chạy thật của ranh giới ấy; JSON trùng khóa bị chặn 400. Nhưng lưu đơn, thu tiền hay giải quyết cạnh tranh phiên bản vẫn ở lớp khác. Nếu muốn học cú pháp record, giao dịch hay tối ưu cache, hãy sang module chuyên sâu. DOP giúp tổ chức trách nhiệm, không thay mọi kỹ thuật.

**Purpose:**

Synthesis từ dữ liệu, phản hồi thật và ranh giới ownership.
