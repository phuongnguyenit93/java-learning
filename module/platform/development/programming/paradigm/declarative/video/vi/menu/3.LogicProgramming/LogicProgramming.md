---
video:
  url: ""
---

# Mô hình dữ kiện logic, quan hệ và suy luận

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

## Lập trình logic như cách mô tả quan hệ

<!-- VIDEO_SECTION -->

### Scene 1 — Lập trình logic như cách mô tả quan hệ

**Time:** `00:00–01:18`

**Visual:**

Mở mạng quan hệ An→Binh→Chi; đánh dấu hai fact cha mẹ được chấp nhận trong cơ sở tri thức.

**Script:**

Logic programming mô tả quan hệ được chấp nhận là đúng và quy tắc cho phép suy ra quan hệ mới. Nếu An là cha mẹ Bình, Bình là cha mẹ Chi, chúng ta có các quan hệ làm dữ liệu đầu vào. Ta chưa chỉ định engine tìm ai trước. Điều cần học là một kết luận có thể được biện minh bởi những quan hệ nào.

**Purpose:**

Đặt nền cho fact, rule, goal.

## Dữ kiện logic (fact) và các quan hệ đã biết

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:18–01:30`

**Visual:**

Đối chiếu một fact parent(An,Binh) cạnh biểu tượng quan hệ với sự kiện user-click lúc 10:20 cạnh đồng hồ.

**Script:**

Fact vừa nhắc đến có phải một event xảy ra theo thời gian không?

**Purpose:**

Tách logical fact và temporal event.

### Scene 1 — Dữ kiện logic (fact) và các quan hệ đã biết

**Time:** `01:30–02:48`

**Visual:**

Bên trái giữ fact parent ổn định, bên phải biểu tượng click có đồng hồ tiến; nối thẻ FACT và EVENT tới đúng cột.

**Script:**

Trong logic, một fact là phát biểu về quan hệ được coi là đúng trong cơ sở tri thức. Nó khác sự kiện người dùng vừa bấm nút, vốn gắn thời điểm diễn ra. Thêm fact parent(Binh,Chi) làm mạng quan hệ phong phú hơn; từ đó ta có thể hỏi về ông bà. Đừng nhầm bài suy luận quan hệ với các luồng sự kiện theo thời gian của reactive programming.

**Purpose:**

Bảo vệ ranh giới giữa logic và reactive.

## Quy tắc suy ra và các điều kiện logic

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:48–03:00`

**Visual:**

Sáng hai cạnh parent An đến Bình và Bình đến Chi, vẽ thêm cạnh grandparent An đến Chi chỉ khi cả hai tiền đề cùng hiện.

**Script:**

Có hai fact rồi, làm sao kết luận được quan hệ chưa ghi sẵn?

**Purpose:**

Dẫn sang luật có tiền đề kép.

### Scene 1 — Quy tắc suy ra và các điều kiện logic

**Time:** `03:00–04:18`

**Visual:**

Từ hai cạnh parent sáng màu, vẽ nét đứt grandparent An→Chi sau khi nhân chứng y=Binh được chọn.

**Script:**

Quy tắc ông bà nói x là ông bà z nếu tồn tại y mà x là cha mẹ y và y là cha mẹ z. Với mạng này, y chính là Bình, nên An có quan hệ ông bà với Chi. Nếu một trong hai cạnh không được chứng minh, quy tắc đó chưa tạo kết luận. Đây là quan hệ logic, không phải một đoạn for được giấu dưới tên rule.

**Purpose:**

Chứng minh inference bằng nhân chứng trung gian.

## Mục tiêu truy vấn và kết luận phù hợp

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:18–04:30`

**Visual:**

Bên trái mạng ba người, bên phải khung câu hỏi grandparent(Who,Chi); khi hai cạnh đủ sáng, nhãn Who=An xuất hiện.

**Script:**

Quy tắc đã cho phép suy ra quan hệ. Người học có thể đặt câu hỏi thế nào?

**Purpose:**

Nối quy tắc vào goal cần chứng minh.

### Scene 1 — Mục tiêu truy vấn và kết luận phù hợp

**Time:** `04:30–05:48`

**Visual:**

Chiếu goal grandparent(Who,Chi) cạnh mạng; chạy đường sáng qua Bình rồi kết thúc bằng Who=An.

**Script:**

Một goal là mệnh đề ta muốn kiểm tra. Nếu hỏi An có là ông bà của Chi không, hệ thống có thể trả lời có căn cứ. Nếu hỏi ai là ông bà của Chi, một bộ suy diễn hỗ trợ biến có thể đưa ra An từ những fact đã có. Khi cơ sở tri thức thêm quan hệ khác, đáp án có thể gồm nhiều người. Chọn một câu trả lời duy nhất cần ràng buộc khác, không phải mặc định của logic.

**Purpose:**

Cho người xem thấy suy luận trả yes/no hoặc gán biến.

## Đặc tả quan hệ so với thuật toán tìm kiếm

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:48–06:00`

**Visual:**

Giữ luật grandparent cố định; ở dưới vẽ hai con đường tìm từ An hoặc Chi, một đường có nhánh đệ quy cảnh báo không kết thúc.

**Script:**

Biết kết luận nào hợp lệ chưa có nghĩa engine dùng một cách tìm duy nhất.

**Purpose:**

Dẫn sang ranh giới thuật toán tìm kiếm.

### Scene 1 — Đặc tả quan hệ so với thuật toán tìm kiếm

**Time:** `06:00–07:18`

**Visual:**

Dùng cùng graph vẽ hai thứ tự tìm kiếm khác nhau và một nhánh đệ quy dấu hỏi về kết thúc.

**Script:**

Quy tắc nêu những quan hệ cần đúng để kết luận, còn bộ giải có thể chọn chiến lược truy tìm khác nhau. Một hệ có thể đi ngược từ câu hỏi tới các tiền đề, hệ khác có thể suy ra dần từ fact. Chi phí, thứ tự lời giải và việc dừng có thể khác, đặc biệt với quan hệ đệ quy. Chưa tìm ra kết luận không luôn có nghĩa đã chứng minh kết luận ấy sai.

**Purpose:**

Phân biệt logic semantics với search performance.

## Giới hạn giữa ý tưởng logic và cú pháp/trình giải Prolog

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:18–07:30`

**Visual:**

Đưa khung fact–rule–goal cạnh thẻ Prolog; các thuật ngữ unification, clause order, interpreter nằm ngoài viền module.

**Script:**

Chúng ta vừa dùng ký hiệu giống Prolog. Nó đã là một chương trình chạy thật chưa?

**Purpose:**

Đặt ranh giới với ngôn ngữ logic chuyên dụng.

### Scene 1 — Giới hạn giữa ý tưởng logic và cú pháp/trình giải Prolog

**Time:** `07:30–08:48`

**Visual:**

Đặt bảng conceptual fact/rule/goal cạnh bảng Prolog syntax, mũi tên từ khái niệm sang chủ sở hữu ngôn ngữ.

**Script:**

Prolog dùng dữ kiện, quy tắc và mục tiêu truy vấn, nhưng còn có luật cú pháp, hợp nhất biến và thứ tự lựa chọn mệnh đề. Các chi tiết ấy quyết định cách trình thông dịch tìm lời giải, kể cả những trường hợp không kết thúc. Video này dùng ký hiệu quan hệ để minh họa một ý tưởng, không giả vờ đã chạy code Prolog. Muốn quan sát behavior của engine thật, cần dùng học phần Prolog riêng.

**Purpose:**

Không biến diagram thành bằng chứng runtime giả.
