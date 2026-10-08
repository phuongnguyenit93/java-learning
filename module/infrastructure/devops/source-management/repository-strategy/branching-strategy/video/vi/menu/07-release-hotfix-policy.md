---
video:
  url: ""
---

# Chính sách nhánh phát hành và hotfix

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

## Phát hành từ nhánh chính so với nhánh ổn định tách riêng

<!-- VIDEO_SECTION -->

### Scene 1 — Truy vết một bản vá qua các dòng mã

**Time:** `00:00–01:51`

**Visual:**

Hai đường phát hành: tag `v2.0` trên main so với nhánh `release/2.0` cắt từ commit đã duyệt; mục tiêu ổn định tô khác màu.

Các SHA và version thuộc repository minh họa; dùng `git log --graph --oneline --all` để kiểm chứng, không giả kết quả deployment.

**Script:**

Một đội deploy liên tục có thể phát hành từ commit đã kiểm tra trên main, gắn tag và sửa lỗi bằng roll-forward. Đội cần giữ bản 2.0 ổn định vài tuần trong lúc main tiếp tục thay đổi có thể cắt release/2.0 từ mốc đáng tin cậy. Hai lựa chọn phụ thuộc lịch phát hành và nghĩa vụ hỗ trợ phiên bản, không phải tên branch 'chuẩn'. Vẽ rõ mốc tag, commit hiện tại và điểm cắt branch để thấy release không bắt buộc luôn xuất phát từ HEAD mới nhất.

**Purpose:**

Phân biệt tag từ main và release branch có thời hạn.

## Vòng đời nhánh phát hành: tạo, duy trì và kết thúc

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:51–02:05`

**Visual:**

Giữ hai lối phát hành tag v2.0 từ main và release/2.0 tách từ commit ổn định; mở timeline nhánh release chỉ nhận hai bản sửa rồi được nghỉ.

**Script:**

Ta đã so tag từ main với nhánh ổn định release. Theo một release branch từ lúc tạo đến lúc nghỉ bảo trì.

**Purpose:**

Nối lựa chọn phát hành từ main với trách nhiệm giới hạn vòng đời nhánh ổn định.

### Scene 1 — Truy vết một bản vá qua các dòng mã

**Time:** `02:05–03:56`

**Visual:**

Đồ thị main liên tục nhận feature, `release/2.0` tách sát ngày phát hành và chỉ nhận hai bản vá; sau tag cuối nhánh nghỉ.

Các SHA và version thuộc repository minh họa; dùng `git log --graph --oneline --all` để kiểm chứng, không giả kết quả deployment.

**Script:**

Trên timeline, release/2.0 chỉ được tạo khi phạm vi phiên bản đã rõ; trong thời gian ổn định, nó không trở thành nhánh phát triển tính năng mới. Ghi chủ thể chịu trách nhiệm review bản vá và điều kiện kết thúc nhánh sau khi không còn phiên bản chạy cần hỗ trợ. Với Trunk-Based Development, nhánh này có thể được tạo sát ngày release hoặc thậm chí từ tag cũ khi phát hiện lỗi. Giữ nhánh release vô hạn làm tăng chi phí đồng bộ.

**Purpose:**

Cho thấy release branch có điểm cắt, phạm vi và điều kiện nghỉ.

## Đích sửa lỗi khẩn và việc lan truyền bản vá giữa các dòng phát triển

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:56–04:10`

**Visual:**

Từ release/2.0 chỉ nhận fix, chia hai mô hình TBD fix trunk→cherry-pick release và Git Flow hotfix production→develop.

**Script:**

Đường release ổn định đã có. Giờ so điểm bắt đầu hotfix giữa cách làm trunk-first và Git Flow cổ điển.

**Purpose:**

Làm rõ hướng truyền bản vá phụ thuộc chiến lược, không có một quy tắc hotfix chung.

### Scene 1 — Truy vết một bản vá qua các dòng mã

**Time:** `04:10–06:01`

**Visual:**

So hai đồ thị có mũi tên: TBD fix F trên main rồi cherry-pick sang release/2.0; Git Flow cổ điển hotfix từ production tag rồi merge về production và develop.

Các SHA và version thuộc repository minh họa; dùng `git log --graph --oneline --all` để kiểm chứng, không giả kết quả deployment.

**Script:**

Đây là nơi không được gộp hai chiến lược. Với trunk-first, lỗi được tái hiện và sửa kèm test ở trunk, rồi chọn commit phù hợp cherry-pick sang nhánh release cũ và kiểm tra lại. Với Git Flow cổ điển, hotfix thường tách từ production line hoặc tag, sau đó phải tích hợp bản sửa về production và develop. Cả hai đều cần lưu dấu bản nào đã nhận fix, nhưng mũi tên ưu tiên khác nhau. Nếu giảng 'mọi hotfix bắt đầu trên release' sẽ sai với TBD.

**Purpose:**

Phân biệt trunk-first và classic GitFlow hotfix bằng commit arrows.

## Backport và forward-port: chuyển bản vá giữa các dòng mã cần thiết

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:01–06:15`

**Visual:**

Giữ hai hướng hotfix, lập ma trận main/release2.0/maintenance1.9 với commit vá và bộ test cần kiểm, để ô chưa có fix trống.

**Script:**

Hai hướng hotfix đều cần bằng chứng. Hãy theo phiên bản được backport/forward-port và kiểm chứng từng bản.

**Purpose:**

Chuyển hướng mũi tên lịch sử thành chứng cứ từng phiên bản đã thực sự nhận và kiểm bản vá.

### Scene 1 — Truy vết một bản vá qua các dòng mã

**Time:** `06:15–08:06`

**Visual:**

Ma trận dòng `main`, `release/2.0`, `maintenance/1.9`: fix SHA, target SHA mới sau cherry-pick, test result và người phê duyệt.

Các SHA và version thuộc repository minh họa; dùng `git log --graph --oneline --all` để kiểm chứng, không giả kết quả deployment.

**Script:**

Giả sử lỗ hổng ảnh hưởng cả 1.9 và 2.0. Một commit F đã có trên main không chứng minh hai dòng cũ an toàn. Ghi vào bảng target nào cần bản vá, ai backport, SHA kết quả và kết quả test tương thích từng phiên bản. Cherry-pick có thể sinh SHA khác hoặc conflict do API thay đổi; không tự động áp toàn bộ dependency cùng lúc. Forward-port cũng phải có owner rõ nếu cách làm cổ điển sửa trên maintenance trước. Bằng chứng cuối là cả dòng mã lẫn gói phát hành phù hợp.

**Purpose:**

Dùng ma trận chứng minh bản vá đến từng dòng mã.

## Chi phí duy trì nhánh ổn định dài hạn và hỗ trợ nhiều phiên bản

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:06–08:20`

**Visual:**

Từ ma trận ba dòng, mở commit graph có bốn release branches và checklist backport/review tăng, bên cạnh lịch end-of-life từng phiên bản.

**Script:**

Ma trận bản vá trải qua nhiều phiên bản. Việc duy trì các đường bảo trì đó tốn những nguồn lực gì?

**Purpose:**

Giải thích chi phí giữ nhiều branch là cam kết phiên bản và validation chứ không phải tạo ref đắt.

### Scene 1 — Truy vết một bản vá qua các dòng mã

**Time:** `08:20–10:11`

**Visual:**

Graph có bốn release branches cùng tồn tại và bảng số lần cherry-pick/conflict tăng qua quý, bên cạnh phương án cắt giảm support window.

Các SHA và version thuộc repository minh họa; dùng `git log --graph --oneline --all` để kiểm chứng, không giả kết quả deployment.

**Script:**

Hãy tưởng tượng đội giữ 1.7, 1.8, 1.9 và 2.0 lâu dài. Mỗi sửa lỗi bảo mật có thể cần bốn lần đánh giá, bốn bộ test và bốn quyết định phát hành. Số commit khác biệt và conflict tăng, chứ không phải Git xử lý branch chậm đi đơn thuần. Đây là lúc chiến lược cần trao đổi với sản phẩm về thời hạn hỗ trợ, đội bảo trì và cách hết vòng đời phiên bản. Đừng kết luận phải bỏ nhánh release, mà hãy tính chi phí trước khi hứa hỗ trợ vô thời hạn.

**Purpose:**

Đánh giá chi phí bảo trì theo số dòng mã thực sự cần hỗ trợ.
