---
video:
  url: ""
---

# Biểu diễn dữ liệu bằng cấu trúc phổ dụng

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

## Cấu trúc dữ liệu phổ dụng: Map, danh sách và giá trị

<!-- VIDEO_SECTION -->
### Scene 1 — Đơn A như map và list

**Time:** `00:00–01:26`

**Visual:**

Một hộp đơn A tách thành key/value `id=A`, `status=pending`, `lines=[...]`. Dùng nét màu khác cho string, số, map con và list.

**Script:**

Cấu trúc phổ dụng gồm map, danh sách và các giá trị quen thuộc. Với đơn A, map ngoài chứa id, trạng thái và một danh sách dòng hàng. Map bên trong mỗi dòng chứa sku, số lượng và đơn giá. Người xem có thể đọc hình dạng dữ liệu mà không cần biết lớp Java nào định nghĩa nó. Sự minh bạch này giúp nhiều phép xử lý đọc cùng dữ liệu, nhưng còn thiếu câu trả lời liệu caller gửi đúng tên trường và đúng kiểu hay chưa.

**Purpose:**

Dạy generic representation bằng hình dạng thật, trước khi động đến schema.

## Biểu diễn bài toán đơn giản bằng dữ liệu lồng nhau

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:26–01:39`

**Visual:**

Kéo hai map con P/Q vào list và giữ tên trường nhìn được.

**Script:**

Khung dữ liệu đã có; thêm hai dòng cụ thể để kiểm tra điều nó biểu diễn.

**Purpose:**

Gắn map/list với bài toán đơn hàng có số học.

### Scene 2 — Hai dòng hàng tạo tổng 100

**Time:** `01:39–03:05`

**Visual:**

Hiện JSON ví dụ `id:A,status:pending,lines:[{sku:P,qty:2,price:30},{sku:Q,qty:1,price:40}]`. Tô từng cặp `2×30=60`, `1×40=40`, cuối cùng `60+40=100`.

**Script:**

Đây là đơn A mà ta dùng xuyên suốt: mặt hàng P có số lượng hai và giá ba mươi, mặt hàng Q có số lượng một và giá bốn mươi. Bởi vậy tổng là một trăm. Tên trường trong cả bài là `qty` và `price`, không phải `quantity` hay `unitPrice`. Một mẩu JSON giống cấu trúc map/list để chúng ta dễ gửi qua HTTP về sau; nhưng vẫn phải hiểu mỗi giá trị đang thể hiện điều gì.

**Purpose:**

Khóa đúng canonical field names và phép tính cơ sở cho các ví dụ tiếp theo.

## Phép xử lý dùng lại trên cấu trúc dữ liệu dễ quan sát

<!-- VIDEO_SECTION -->
### Transition

**Time:** `03:05–03:18`

**Visual:**

Từ JSON A kéo hai mũi tên sang Total và Summary.

**Script:**

Map/lists đã biểu diễn được đơn; nay nhiều phép xử lý sẽ sử dụng dữ liệu ấy.

**Purpose:**

Chứng minh chia sẻ representation không cần phương thức trên map.

### Scene 3 — Một cấu trúc, nhiều phép đọc

**Time:** `03:18–04:44`

**Visual:**

Dùng chung card JSON ở trái; chạy minh họa `total(lines) = 100`, `summarize(order) = 'A: 2 lines'`. Giữ con trỏ không chạm vào dữ liệu gốc.

**Script:**

Hàm tính tổng duyệt từng dòng rồi cộng `qty×price`. Hàm tóm tắt chỉ đọc id và số dòng. Hai chức năng không bắt map chứa các method đặc biệt. Đó là lợi ích tái sử dụng cấu trúc dữ liệu phổ dụng. Tuy nhiên mỗi hàm phải nêu rõ trường mà nó cần; nếu không có `price`, không thể tự gán số không để tạo kết quả tưởng đúng. Bước tiếp theo sẽ cố tình gửi dữ liệu hỏng để thấy giới hạn ấy.

**Purpose:**

Hiện tác dụng practical của generic data và mở đường lỗi.

## Khóa bị thiếu, cấu trúc không hợp lệ và giá trị mơ hồ

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:44–04:57`

**Visual:**

Cho JSON A thêm một dòng sai `qty:-3` và một khóa lạ, không tính lại tổng.

**Script:**

Hai phép đọc đều đúng khi map hợp lệ; thử một payload do người lạ gửi.

**Purpose:**

Nêu lỗi missing/wrong-type và nguy cơ silent corruption.

### Scene 4 — Đừng hợp thức hóa dữ liệu sai

**Time:** `04:57–06:23`

**Visual:**

Ba thử nghiệm tách ô: `qty: '2'` kiểu chuỗi, thiếu `price`, và `quantitty:2`; đặt dấu cảnh báo tại `lines[1].qty`/`lines[1].price`, không hiển thị subtotal mới.

**Script:**

Một map rất dễ chứa hình dạng khác điều người xử lý dự kiến. Chuỗi chữ `'2'` không tự là một số nguyên, thiếu `price` không nên thành giá không, và `quantitty` là một tên trường khác hẳn `qty`. Nếu mã tính tiền tiếp tục thay vì từ chối, kết quả sai có thể đi xa trước khi ai đó phát hiện. Lỗi phải chỉ đúng đường dẫn của dòng và trường, ví dụ `lines[1].qty`, để người gửi sửa dữ liệu ngay tại nguồn.

**Purpose:**

Cho hình ảnh failure mode theo field path, không đánh đồng JSON parse với shape validation.

## Tính linh hoạt, ràng buộc kiểu tĩnh và nhu cầu mô tả cấu trúc

<!-- VIDEO_SECTION -->
### Transition

**Time:** `06:23–06:36`

**Visual:**

Giữ sai lệch `quantitty` bên trái, đưa schema card vào cổng nhận.

**Script:**

Dữ liệu phổ dụng dễ quan sát nhưng chưa tự bảo đảm tính đúng; cần một bản mô tả độc lập.

**Purpose:**

Dẫn tới schema và đối chiếu với kiểm tra kiểu tĩnh.

### Scene 5 — Sơ đồ schema bên cạnh dữ liệu

**Time:** `06:36–08:02`

**Visual:**

Đặt dữ liệu A cạnh quy tắc `lines:1..20`, `qty:1..10000 integer`, `price:0..1e6 integer`, `status:pending`. Mũi tên INPUT đi qua GATE rồi mới sang total.

**Script:**

Cấu trúc map/list linh hoạt, nhưng sai kiểu và tên khóa thường chỉ lộ ra khi chạy. Ta có thể dùng mô hình có kiểu tĩnh cho những ranh giới quan trọng; Sharvit lại nhấn mạnh schema độc lập như một cách nói rõ hình dạng map phổ dụng. Khi nhận payload ngoài, quy tắc kiểm tra cần đọc số dòng, kiểu dữ liệu, giá trị trong khoảng và tên trường được phép. Những điều kiện ấy chính là hợp đồng mà chương Schema sẽ mở rộng.

**Purpose:**

Tạo cầu nối rõ từ generic representation tới data trust boundary.
