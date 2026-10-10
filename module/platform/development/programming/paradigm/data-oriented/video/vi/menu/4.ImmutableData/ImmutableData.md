---
video:
  url: ""
---

# Dữ liệu bất biến và trạng thái ứng dụng

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

## Giá trị bất biến trong mô hình hướng dữ liệu

<!-- VIDEO_SECTION -->
### Scene 1 — Giữ ảnh chụp trước khi biến đổi

**Time:** `00:00–01:25`

**Visual:**

Trên bàn có đơn A `{status:pending,total:100}`; bấm discount nhưng không dùng bút sửa ô A.

**Script:**

Một giá trị bất biến không bị sửa sau khi đã tạo. Với đơn A có tổng một trăm, phép giảm mười phần trăm có thể tạo bản mới chín mươi mà không viết đè đơn gốc. Người xem có thể đặt hai bản cạnh nhau và thấy dữ liệu trước khi tính vẫn còn nguyên. Nhưng nếu map chỉ có tham chiếu ngoài cố định còn danh sách bên trong bị sửa từ nơi khác, đó không phải bất biến sâu. Ta phải kiểm soát cả cấu trúc lồng nhau để kết quả so sánh có ý nghĩa.

**Purpose:**

Dùng snapshot A trước/sau và rủi ro mutable nested list để giải thích giá trị bất biến, không chỉ biến final.

## Cập nhật dữ liệu bằng cách tạo phiên bản mới

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:25–01:38`

**Visual:**

Kéo `discount=10` từ phần input vào một hàm, đầu ra tạo thẻ riêng.

**Script:**

Đã có bản A trước/sau; phép cập nhật cần đi qua những bước nào?

**Purpose:**

Diễn tả cập nhật bằng value transformation thay cho mutation.

### Scene 2 — Tạo đơn mới không chạm đơn cũ

**Time:** `01:38–03:03`

**Visual:**

Vẽ `discount(orderA,10%)` trả `{...,subtotal:100,discountedTotal:90}`. Mở panel `before.lines` và `after.lines`, cùng P×2×30, Q×1×40 không đổi; trạng thái pending vẫn giữ.

**Script:**

Hàm giảm giá đọc cấu trúc đơn A đã hợp lệ, lấy 2 nhân 30 cộng 1 nhân 40 bằng 100, rồi tạo một kết quả mới giảm 10 phần trăm còn 90. Trong API thật, hai snapshot `before` và `after` cùng xuất hiện ở HTTP 200. Trường `status` vẫn là pending: tính số tiền không thể tự ý biến đơn thành đã thanh toán. Vì hai danh sách dòng hàng vẫn giữ nguyên, người xem kiểm tra được phép tính tách khỏi thay đổi trạng thái bên ngoài.

**Purpose:**

Dùng đúng cấu trúc phản hồi API đã kiểm chứng để chứng minh non-mutating transformation.

## Bản dữ liệu trước sau, so sánh và lịch sử thay đổi

<!-- VIDEO_SECTION -->
### Transition

**Time:** `03:03–03:16`

**Visual:**

Chuyển hai card thành timeline version A1/A2 có chú thích quy tắc.

**Script:**

Một bản mới có ích vì bản cũ còn giữ được; thử đặt chúng lên lịch sử.

**Purpose:**

Cho thấy truy vết, so sánh và lịch sử không đồng nghĩa tự lưu trữ.

### Scene 3 — Hai snapshot không phải hai lần thanh toán

**Time:** `03:16–04:41`

**Visual:**

Bảng `A1 before:100,pending`, `A2 after:90,pending`; bên dưới hiển thị `persistence=false`, `payment=false`. Tô khác màu `calculated` và `committed`.

**Script:**

Người kiểm thử có thể đặt A1 và A2 cạnh nhau để trả lời trường nào thay đổi và trường nào giữ nguyên. Nhưng API của chúng ta chỉ trả hai giá trị trong cùng một phản hồi, không lưu bất cứ phiên bản nào vào cơ sở dữ liệu. Nó cũng không gọi cổng thanh toán. Ảnh chụp hỗ trợ giải thích lịch sử *nếu có cơ chế lưu bên ngoài*, chứ tự nó chưa tạo thành lịch sử bền vững. Ta phải phân biệt giá trị được tính với trạng thái đã cam kết.

**Purpose:**

Chặn hiểu lầm API preview thực hiện side effects.

## Điều phối trạng thái và ranh giới xử lý đồng thời

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:41–04:54`

**Visual:**

Đặt hai mũi tên từ cùng phiên bản A3 đến hai bản A4 khác nhau, giữ storage gate trống.

**Script:**

Snapshot an toàn khỏi sửa nhầm, nhưng hai yêu cầu đồng thời có tự đồng ý với nhau không?

**Purpose:**

Dẫn từ immutable data sang coordination và optimistic conflict.

### Scene 4 — Hai bản hợp lệ vẫn có thể xung đột

**Time:** `04:54–06:19`

**Visual:**

Tách flow X đọc `version=3` rồi ra `A4x`, Y cùng đọc `version=3` rồi ra `A4y`. Cổng lưu nhận A4x trước và hiển thị conflict cho A4y; không vẽ snapshot bị sửa.

**Script:**

Mỗi request có thể tạo một giá trị mới đúng từ dữ liệu mình đã đọc, nhưng nếu cả X và Y dựa trên version 3, kho lưu phải quyết định ai được ghi. Bất biến không giải quyết race, transaction hay idempotency. Cơ chế kiểm tra phiên bản là chuyện điều phối trạng thái, không phải thuộc tính tự phát của map bất biến. API preview hiện tại cố ý không có kho lưu để người học chỉ tập trung vào giá trị cũ và giá trị mới. Chương tiếp sẽ kiểm tra dữ liệu ngay khi nó từ bên ngoài đi vào.

**Purpose:**

Đặt ranh giới concurrency trước khi bước sang schema/trust boundary.
