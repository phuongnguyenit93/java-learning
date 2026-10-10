---
video:
  url: ""
---

# Truy vấn dữ liệu và các kiểu mô tả khai báo

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

## Truy vấn như đặc tả những dòng kết quả mong muốn

<!-- VIDEO_SECTION -->

### Scene 1 — Truy vấn như đặc tả những dòng kết quả mong muốn

**Time:** `00:00–01:18`

**Visual:**

Chiếu bảng hai transaction A:50 khác ID, chọn cột amount và giữ rõ hai hàng 50 trong SELECT ALL.

**Script:**

Một câu query có thể nói ta muốn những giao dịch dương thuộc tài khoản A. Hai giao dịch khác nhau đều có giá trị năm mươi. Nếu chỉ chiếu cột amount, mặc định SQL SELECT ALL trả hai hàng bằng nhau: năm mươi, năm mươi. Chỉ SELECT DISTINCT mới yêu cầu loại bản sao. Đây là biểu diễn ý nghĩa của SQL, không phải lời khẳng định một terminal database đang chạy trên slide.

**Purpose:**

Cung cấp ví dụ SQL bag semantics không bịa engine.

## Điều kiện lọc, chọn dữ liệu và quan hệ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:18–01:30`

**Visual:**

Tô hai điều kiện account=A và amount>0 trên bốn hàng; B:10 bị loại vì account, A:-5 bị loại vì dấu tiền.

**Script:**

Kết quả trùng nhau có hai hàng, nhưng trước đó ta đã lọc theo những điều kiện nào?

**Purpose:**

Chuyển sang tổ hợp predicate.

### Scene 1 — Điều kiện lọc, chọn dữ liệu và quan hệ

**Time:** `01:30–02:48`

**Visual:**

Dùng bộ lọc account=A và amount>0 trên bốn giao dịch; đánh dấu B:10 và A:-5 bị loại vì nguyên nhân khác nhau.

**Script:**

Ta cần cả hai điều kiện cùng thỏa: thuộc A và số tiền dương. B:10 có số tiền đạt nhưng tài khoản sai; A:-5 có đúng chủ nhưng số tiền không dương. Với dữ liệu thật, NULL và giá trị thiếu có ngữ nghĩa riêng trong SQL, không đơn giản luôn là true hoặc false. Ở đây chúng ta chỉ dùng các số đã biết để hiểu cách khai báo tính chất của hàng cần lấy.

**Purpose:**

Đặt ranh giới SQL NULL và phân quyền.

## Thứ tự kết quả, tính trùng lặp và ràng buộc bổ sung

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:48–03:00`

**Visual:**

Giữ hai nhánh SELECT ALL → [50,50] và DISTINCT → [50] để xét hàng trùng. Bên dưới, mở ví dụ KHÁC gồm hai số phân biệt [25,10]; khi chọn ORDER BY amount ASC, đổi thành [10,25].

**Script:**

Lọc đúng hàng chưa trả lời hai câu hỏi: giữ bản sao và giữ thứ tự ra sao?

**Purpose:**

Nối multiplicity với order requirement.

### Scene 1 — Thứ tự kết quả, tính trùng lặp và ràng buộc bổ sung

**Time:** `03:00–04:18`

**Visual:**

Trước hết so sánh hai hàng 50 với một hàng 50 sau DISTINCT. Sau đó chuyển qua bảng hai số khác nhau [25,10] và [10,25] có ORDER BY amount ASC; ghi rõ vị trí đầu chỉ để minh họa, không phải thứ tự cơ sở dữ liệu được bảo đảm.

**Script:**

SELECT DISTINCT khiến hai hàng có amount năm mươi chỉ còn một, nhưng không quy định sắp xếp. Sang ví dụ riêng có hai số khác nhau, hai mươi lăm và mười: yêu cầu ORDER BY amount tăng dần mới xác định thứ tự mười rồi hai mươi lăm. ORDER BY không tự xóa bản sao, còn khi không có ORDER BY thì thứ tự tình cờ không được bảo đảm. Nếu hai timestamp bằng nhau, thêm khóa phụ như ID để hiển thị ổn định.

**Purpose:**

Chứng minh ALL/DISTINCT và order là hai hợp đồng khác nhau.

## Quy tắc chính sách và cấu hình dạng khai báo

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:18–04:30`

**Visual:**

Đặt câu chính sách chỉ chủ tài khoản xem số dư bên trái; bên phải tệp YAML chứa lệnh open, write, close theo thứ tự.

**Script:**

Ngoài SQL, có thể mô tả một chính sách hoặc cấu hình theo kiểu khai báo không?

**Purpose:**

Dẫn từ truy vấn sang DSL và policy.

### Scene 1 — Quy tắc chính sách và cấu hình dạng khai báo

**Time:** `04:30–05:48`

**Visual:**

Bảng chủ tài khoản được phép xem số dư ở trái; các lệnh open→write→close ở phải và dấu label khác nhau.

**Script:**

Quy tắc chỉ chủ tài khoản được xem số dư là điều kiện mong muốn, không phải danh sách tất cả thao tác kiểm quyền ở mỗi hàm. Ngược lại, một tệp YAML chỉ liệt kê mở tệp, ghi rồi đóng tệp vẫn mô tả chuỗi lệnh. Phần mở rộng file không xác định paradigm; hãy xem người viết mô tả trạng thái cần đạt hay bắt công cụ thực hiện từng bước.

**Purpose:**

Chống hiểu lầm file cấu hình đồng nghĩa khai báo.

## Bộ thực thi lựa chọn cách hiện thực truy vấn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:48–06:00`

**Visual:**

Giữ truy vấn của A ở phía trên; dưới vẽ scan và index với kết quả cùng hai hàng 50, ghi chú chưa đo EXPLAIN.

**Script:**

Cùng câu query, engine có thể có hai kế hoạch. Điều gì phải giống và điều gì có thể khác?

**Purpose:**

Chuyển từ SQL syntax sang evaluator strategy.

### Scene 1 — Bộ thực thi lựa chọn cách hiện thực truy vấn

**Time:** `06:00–07:18`

**Visual:**

Trên cùng truy vấn, hiển thị plan scan/index giả định và nối hai kết quả có cùng số hàng trùng.

**Script:**

Một engine có thể quét toàn bộ bảng hoặc dùng chỉ mục nếu phù hợp. Hai kế hoạch hợp lệ đều phải đáp ứng điều kiện account, amount và số hàng trùng theo hợp đồng truy vấn. Chi phí có thể khác theo phân bố dữ liệu và thống kê, và optimizer không được bảo đảm luôn chọn cách nhanh nhất. Hình này chỉ minh họa cơ chế, không là kết quả đo performance của một SQL engine thật.

**Purpose:**

Giữ evidence kỹ thuật đúng phạm vi paradigm.

## Giới hạn khi coi mọi DSL hoặc điều kiện là khai báo

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:18–07:30`

**Visual:**

Gom ba thẻ truy vấn, chính sách owner-only và file bước lệnh; làm nổi câu hỏi ai quyết định HOW.

**Script:**

Nhìn một DSL có dấu ngoặc hoặc dấu hai chấm chưa đủ để biết nó có khai báo hay không.

**Purpose:**

Đưa về tiêu chí quyết định phong cách.

### Scene 1 — Giới hạn khi coi mọi DSL hoặc điều kiện là khai báo

**Time:** `07:30–08:48`

**Visual:**

Ba thẻ query, policy và ordered YAML command cùng đặt dưới phép thử WHAT hay HOW; thẻ lệnh rơi về HOW.

**Script:**

Một DSL có thể cho phép người viết mô tả điều cần đúng, nhưng cũng có thể chỉ là cách ghi lệnh tuần tự. Một biểu thức if bên trong hàm mệnh lệnh không biến toàn bộ hàm thành declarative. Hãy xét ý nghĩa của mô tả và mức lựa chọn được giao cho evaluator. Nếu tài liệu vẫn yêu cầu làm bước một, hai, ba theo đúng thứ tự, ta đang chỉ định cách thực hiện.

**Purpose:**

Kết thúc chương bằng quyết định semantics thay vì cú pháp.
