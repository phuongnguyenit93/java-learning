---
video:
  url: ""
---

# Lập trình hướng dữ liệu: Mục đích và mô hình cốt lõi

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

## Lập trình hướng dữ liệu: Khái niệm và phạm vi

<!-- VIDEO_SECTION -->
### Scene 1 — Một đơn hàng, ba cách nhìn

**Time:** `00:00–01:22`

**Visual:**

Đặt thẻ đơn A với `id=A`, hai món P và Q trên bảng. Bên trái là hình đối tượng có các nút `addItem/total`; bên phải là map `{'id':'A','lines':[...]}` và một hàm `tinhTong(order)`. Không bấm chạy API ở đây.

**Script:**

Cùng một đơn hàng có thể được nhìn như đối tượng có hành vi hoặc như giá trị dữ liệu được truyền qua các phép xử lý. Với đơn A, chúng ta sẽ chọn cách thứ hai để quan sát cấu trúc, kiểm tra hợp lệ và tạo kết quả mới. DOP không phủ nhận đối tượng; nó chỉ đặt câu hỏi: nếu nhiều phép xử lý cùng cần đọc dữ liệu, cách tổ chức nào giúp nhìn rõ đầu vào và trách nhiệm hơn?

**Purpose:**

Nêu WHAT của DOP với dữ liệu cụ thể trước khi dùng thuật ngữ trừu tượng.

## Chi phí khi dữ liệu gắn chặt với hành vi xử lý

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:22–01:36`

**Visual:**

Di chuyển thẻ đơn A qua ba nhu cầu tính tiền, báo cáo, kiểm tra; làm nổi đường phụ thuộc vào lớp đối tượng.

**Script:**

Cùng dữ liệu xuất hiện ở hai bên, nhưng khi thêm một công việc mới sẽ có chi phí gì?

**Purpose:**

Nối mô hình ban đầu đến động cơ tách dữ liệu và thao tác.

### Scene 2 — Khi hành vi khóa chặt dữ liệu

**Time:** `01:36–02:58`

**Visual:**

Giữ ba ô `tính tiền`, `xuất báo cáo`, `đối chiếu` đang trỏ vào cùng một lớp đối tượng với ba phương thức; thay một ô bằng kiểm tra chính sách và hiển thị mũi tên phải cập nhật lớp.

**Script:**

Nếu chỉ có một việc tính tổng, đặt phương thức trên đối tượng rất hợp lý. Nhưng khi báo cáo, đối chiếu và kiểm tra chính sách cũng cần đọc cùng dữ liệu, nhiều thao tác có thể bị buộc phụ thuộc vào một thiết kế lớp. Tách biểu diễn dữ liệu khỏi phần xử lý giúp mỗi công việc sử dụng lại cấu trúc đã biết. Bù lại, ai được phép thay đổi và kiểm tra dữ liệu sẽ cần hợp đồng rõ ràng. Đây là một đánh đổi, không phải kết luận mọi đối tượng đều sai.

**Purpose:**

Làm rõ WHY, tránh biến DOP thành khẩu hiệu phản đối OOP.

## Điểm xuất phát: Giá trị, tập dữ liệu và lối lập trình đã biết

<!-- VIDEO_SECTION -->
### Transition

**Time:** `02:58–03:12`

**Visual:**

Gom ba nhu cầu về một ô dữ liệu; mở bốn thẻ giá trị, danh sách, map, hàm.

**Script:**

Chi phí phụ thuộc đã hiện ra; người mới cần những viên gạch nào để tiếp tục?

**Purpose:**

Thiết lập prerequisite thay vì nhảy thẳng vào JSON và schema.

### Scene 3 — Bắt đầu bằng giá trị và tập hợp

**Time:** `03:12–04:34`

**Visual:**

Hiện số `30`, danh sách `[P,Q]`, map `{'sku':'P','qty':2,'price':30}` và hàm `tinhTong(lines)`. Kéo hai dòng vào phép tính `2×30 + 1×40` nhưng chưa cộng.

**Script:**

Để theo kịp, ta chỉ cần biết số và chuỗi là giá trị, danh sách chứa nhiều phần tử, map gắn tên trường với giá trị, còn hàm nhận dữ liệu rồi trả kết quả. Đơn A có dòng P số lượng hai giá ba mươi, và dòng Q số lượng một giá bốn mươi. Sau này API dùng JSON để người học tự gửi dữ liệu, nhưng JSON không phải định nghĩa của DOP. Hôm nay ta học ý nghĩa của cấu trúc ấy trước.

**Purpose:**

Cho người mới các khái niệm tối thiểu với ví dụ xuyên suốt.

## Đối tượng, hàm và dữ liệu dưới các góc nhìn thiết kế

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:34–04:48`

**Visual:**

Chuyển từ bản đồ map sang ba ô Data, Function, Coordinator.

**Script:**

Các viên gạch đủ rồi, thử phân định rõ mỗi bên chịu trách nhiệm gì.

**Purpose:**

Chuẩn bị phân biệt biểu diễn, xử lý và điều phối.

### Scene 4 — Dữ liệu không tự thu tiền

**Time:** `04:48–06:10`

**Visual:**

Hiện `order map` ở giữa; mũi tên sang `tinhTong(order)` kết quả 100; ngoài cùng một hộp `checkout` với biểu tượng cổng thanh toán gắn nhãn ngoài phạm vi.

**Script:**

Dữ liệu đơn hàng nói điều gì đã được cung cấp, hàm tính tổng trả lời số tiền theo quy tắc, còn bộ điều phối quyết định khi nào lưu hay thu tiền. Hai dòng của A tạo tổng một trăm vì hai nhân ba mươi bằng sáu mươi, cộng một nhân bốn mươi bằng bốn mươi. Đừng gộp tất cả vào một map có khả năng thu tiền: map chỉ biểu diễn giá trị. Phần xử lý nghiệp vụ thật có thể có giao dịch và lỗi riêng.

**Purpose:**

Nối WHAT với HOW bằng vai trò cụ thể, giữ ranh giới tác động ngoài.

## Bốn nguyên tắc Sharvit và mối quan hệ giữa chúng

<!-- VIDEO_SECTION -->
### Transition

**Time:** `06:10–06:24`

**Visual:**

Thu các ô về bốn thẻ đặt quanh dữ liệu đơn A, thẻ schema để riêng.

**Script:**

Ba trách nhiệm đã tách; làm sao đặt chúng thành một hệ thống nguyên tắc?

**Purpose:**

Giới thiệu bốn nguyên tắc Sharvit bằng đúng phụ thuộc.

### Scene 5 — Bốn nguyên tắc, một luồng đơn

**Time:** `06:24–07:46`

**Visual:**

Hiện lần lượt `tách code/data`, `map/list phổ dụng`, `không sửa bản cũ`, `schema riêng`; mỗi thẻ chỉ vào dòng tương ứng `input → validate → transform → output` và bấm tính 100→90.

**Script:**

Sharvit mô tả bốn ý liên kết với nhau. Tách code khỏi dữ liệu để quy tắc xử lý dùng lại được. Dùng cấu trúc phổ dụng như map và list để giá trị dễ quan sát. Hạn chế sửa bản gốc mà tạo bản mới, chẳng hạn tổng sau giảm từ một trăm thành chín mươi. Và có schema độc lập để ngăn dữ liệu bên ngoài sai hình dạng đi vào phép tính. Chỉ dùng map mà không kiểm tra đầu vào chưa phải giải pháp tốt.

**Purpose:**

Làm bốn nguyên tắc có quan hệ nhân quả, không đơn thuần đọc một danh sách.

## Phân biệt DOP theo Sharvit, DOP trong Java và DOD tối ưu hiệu năng

<!-- VIDEO_SECTION -->
### Transition

**Time:** `07:46–08:00`

**Visual:**

Giữ khung Sharvit ở trái, thêm hai cột Java Amber và DOD hiệu năng.

**Script:**

Bốn nguyên tắc vừa nêu không có nghĩa mọi cộng đồng dùng chữ DOP giống nhau.

**Purpose:**

Ngăn nhầm ba thuật ngữ DOP/DOD có cùng chữ data.

### Scene 6 — Ba tên gọi, ba trọng tâm

**Time:** `08:00–09:22`

**Visual:**

So sánh ba cột: Sharvit map/list + schema; Java Amber record/sealed + mô hình biến thể; DOD cache/SIMD/layout. Dán nhãn cùng dữ liệu A nhưng không vẽ mũi tên tương đương.

**Script:**

Trong tài liệu Java, DOP nhấn mạnh mô hình dữ liệu minh bạch và bất biến, mô tả đúng các trạng thái hợp lệ và tách thao tác khỏi dữ liệu. Cách này thường có kiểu tĩnh rõ, không giống yêu cầu dùng map phổ dụng của Sharvit. Còn Data-Oriented Design trong lập trình hiệu năng tập trung vào bố trí bộ nhớ, locality và cache. Chúng có thể giao nhau nhưng không đồng nghĩa. Module này đi sâu mô hình Sharvit, chỉ so sánh hai góc nhìn kia ở mức khái niệm.

**Purpose:**

Thiết lập đúng curriculum ownership trước khi vào nội dung sâu.

## Lộ trình từ biểu diễn dữ liệu đến quyết định thiết kế

<!-- VIDEO_SECTION -->
### Transition

**Time:** `09:22–09:36`

**Visual:**

Ghép bốn thẻ thành đường từ đơn A đến kết quả, đặt 8 chapter dots.

**Script:**

Đã chọn trọng tâm Sharvit và biết điều gì không thuộc phạm vi; đây là thứ tự học tiếp.

**Purpose:**

Tóm học phần theo prerequisite và bằng chứng.

### Scene 7 — Lộ trình của đơn A

**Time:** `09:36–10:58`

**Visual:**

Vẽ timeline tám nhãn: separation→generic→immutable→schema→flow→Java→tradeoffs, phần mở đầu là chấm đầu tiên; nối dòng A `2×30+1×40=100` đến giảm 10% ra 90, đặt API status 200/422 ở đoạn schema/flow.

**Script:**

Ta sẽ tách hành vi, biểu diễn hai dòng hàng dưới dạng map/list rồi giữ bản gốc khi tạo kết quả giảm giá mới. Trước khi chấp nhận dữ liệu người dùng, schema sẽ kiểm tra số lượng và trường bắt buộc; ở chương luồng xử lý ta sẽ thật sự gửi HTTP để thấy 200 cho dữ liệu hợp lệ và 422 cho dữ liệu sai. Sau đó mới đặt cạnh góc nhìn Java và lựa chọn thiết kế. Mỗi chương trả lời một câu hỏi mới trên cùng đơn hàng A.

**Purpose:**

Tạo roadmap trình bày nhất quán với API thực, không bịa thí nghiệm.
