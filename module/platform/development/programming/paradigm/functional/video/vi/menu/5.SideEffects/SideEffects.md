---
video:
  url: ""
---

# Tác động phụ và ranh giới chương trình

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

## Tác động phụ và thay đổi bên ngoài có thể quan sát

<!-- VIDEO_SECTION -->
### Scene 1 — Giá đúng nhưng hệ thống đã thay đổi?

**Time:** `00:00–01:23`

**Visual:**

Hiện `tinhTong(100)` trả 94,5 và bảng cạnh ghi `stock: 8→7`; thêm biểu tượng log và đồng hồ, phân biệt return value với thay đổi bên ngoài.

**Script:**

Một hàm có thể tính đúng số tiền mà vẫn tạo tác động phụ, chẳng hạn giảm tồn kho hoặc ghi nhật ký khi không ai yêu cầu. Một phép đọc đồng hồ cũng phụ thuộc vào dữ liệu bên ngoài đang đổi. Ngược lại, trả về một giá trị biểu diễn lỗi không nhất thiết làm hệ thống thay đổi. Trong lập trình hàm, ta muốn chỉ rõ phần nào chỉ tính toán và phần nào tương tác với thế giới. Đây là ranh giới quan sát, không phải lệnh cấm I/O.

**Purpose:**

Đặt side effect bằng dấu vết quan sát được và phân biệt lỗi như giá trị với hiệu ứng thật.

## Phép tính thuần và thao tác có tác động phụ

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:23–01:35`

**Visual:**

Dời logo mạng/kho khỏi đường tính 100→90→94,5.

**Script:**

Ta đã nhận ra hiệu ứng. Giờ thử di chuyển chúng ra ngoài phép tính giá.

**Purpose:**

Chứng minh pure core/effectful shell bằng sơ đồ hoạt động.

### Scene 2 — Lõi thuần ở giữa hai cổng I/O

**Time:** `01:35–02:58`

**Visual:**

Vẽ ba ô `đọc đơn, chính sách [I/O]` → `giảm/thuế [thuần]` → `thu tiền và lưu [I/O]`; chạy ánh sáng từ trái sang phải, dừng ở giá 94,5.

**Script:**

Bộ điều phối đọc đơn và chính sách hiện hành, rồi truyền một ảnh chụp dữ liệu ổn định vào hàm tính. Hàm này có thể trả 94,5 mà không cần mạng. Sau đó bộ điều phối mới thử thu tiền và lưu kết quả. Chúng ta kiểm thử phần tính bằng đầu vào cố định, còn bộ kết nối cần thử lỗi thật hoặc giả lập. Sơ đồ không bảo đảm việc thu và lưu cùng nguyên tử; nó chỉ làm ranh giới hiện rõ.

**Purpose:**

Minh họa phân tách trách nhiệm và giới hạn không tự bảo đảm transaction.

## Thứ tự tác động phụ và điều phối trạng thái

<!-- VIDEO_SECTION -->
### Transition

**Time:** `02:58–03:10`

**Visual:**

So sánh hai timeline thanh toán, một đúng và một ghi paid quá sớm.

**Script:**

Các ô đã tách; nhưng nếu gọi chúng sai thứ tự, lỗi gì xuất hiện?

**Purpose:**

Làm hiện tác động của thứ tự và retry.

### Scene 3 — Không ghi đã thanh toán trước xác nhận

**Time:** `03:10–04:33`

**Visual:**

Timeline A: `calculate→gateway approved→save paid`; timeline B gạch đỏ `save paid→gateway declined`; dưới thêm retry lặp `charge` hai lần và nhãn `duplicate`.

**Script:**

Giả sử ta đánh dấu đơn đã thanh toán trước khi cổng thanh toán xác nhận. Nếu cổng từ chối, dữ liệu nội bộ đã báo sai. Hoặc một request bị retry và gọi thu tiền hai lần: phép tính tổng vẫn đúng nhưng khách có thể bị thu trùng. Hàm thuần chỉ trả lời số tiền dự định và điều kiện đề xuất. Thành phần điều phối phải xử lý phản hồi, thứ tự và chính sách chống trùng, không thể trông đợi purity tạo ra exactly-once.

**Purpose:**

Cho thấy thất bại khi đảo thứ tự và khi retry, không hứa bảo đảm phân tán giả.

## Xử lý lỗi và kiểm thử tại ranh giới tác động phụ

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:33–04:45`

**Visual:**

Chia bảng kiểm thử deterministic calculations và adapters lỗi mạng.

**Script:**

Một giao dịch có thể lỗi tại nhiều ranh giới; các bài thử phải chứng minh những điều khác nhau.

**Purpose:**

Tạo chiến lược kiểm chứng đúng phạm vi.

### Scene 4 — Hai bộ kiểm thử không thay thế nhau

**Time:** `04:45–06:08`

**Visual:**

Bên trái hiện phép tính giỏ rỗng, giảm giá 10%, làm tròn; bên phải ma trận `timeout | declined | duplicate reply | save fail`, mỗi ô gắn tình huống và kết quả xử lý.

**Script:**

Kiểm thử hàm tính giá bằng số liệu cố định: đơn rỗng, tỷ lệ giảm hợp lệ, số tiền âm hay quy tắc làm tròn. Những phép thử ấy không cần mạng. Bộ kết nối thì khác: thử timeout, thanh toán bị từ chối, phản hồi trùng hoặc ghi thất bại. Một mock giúp ta kiểm tra lời gọi dự định, nhưng không chứng minh mạng ngoài đời luôn ổn định. Trả lỗi đủ rõ để bộ điều phối biết từ chối, thử lại hay xử lý bù trừ.

**Purpose:**

Phân biệt evidence của unit test và integration test, tránh overclaim mock.

## Logic theo lối hàm cùng I/O và dịch vụ bên ngoài

<!-- VIDEO_SECTION -->
### Transition

**Time:** `06:08–06:20`

**Visual:**

Từ đơn A dẫn qua I/O, khối tính, kiểm tra phiên bản và lưu.

**Script:**

Ta vừa tách phép tính và thao tác bên ngoài để kiểm thử. Khi ghép chúng vào một ứng dụng web, phần nào thực sự còn thuần?

**Purpose:**

Tổng hợp ranh giới thuần và lỗi cập nhật cạnh tranh.

### Scene 5 — Một lõi hàm bên trong ứng dụng web

**Time:** `06:20–07:43`

**Visual:**

Sơ đồ `HTTP → đọc/validate → tính 94,5 [thuần] → kiểm phiên bản/lưu [I/O] → HTTP`; đồng thời vẽ request X và Y cùng đọc version3 dẫn tới conflict ở bước ghi.

**Script:**

Ứng dụng nhận HTTP không làm nó mất đi cơ hội dùng hàm thuần. Ta có thể đọc đơn, tính giá từ dữ liệu đã biết, rồi xác nhận trạng thái khi lưu. Hai yêu cầu vẫn có thể đọc cùng phiên bản và tạo hai đề xuất hợp lệ, vì vậy nơi ghi phải kiểm tra xung đột. Và cổng thanh toán vẫn có thể từ chối dù phép tính cho đúng 94,5. Lõi thuần giúp cô lập phần dự đoán được; vỏ tương tác chịu trách nhiệm bằng chứng vận hành.

**Purpose:**

Chốt lại cách đưa FP vào hệ thống có I/O, không giả lập một HTTP learning endpoint.
