---
video:
  url: ""
---

# Ghi nhận thay đổi và kiểm tra lịch sử commit

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

## Kiểm tra trạng thái thay đổi bằng git status

<!-- VIDEO_SECTION -->

### Scene 1 — Thực hành có kiểm chứng: Kiểm tra trạng thái thay đổi bằng git status

**Time:** `00:00–01:34`

**Visual:**

Trên repository chỉ dùng cho demo, mở terminal sát cửa sổ hiển thị README và gõ lần lượt `git status -sb; git status`. So sánh trước/sau và làm nổi bật dòng chứng minh trạng thái.

**Script:**

Khi mở terminal, câu hỏi đầu tiên không phải commit thế nào mà là chúng ta đang có gì. status phân riêng tệp chưa được theo dõi, thay đổi đã đưa vào index và thay đổi chưa staging. Hãy cố ý tạo một tệp mới rồi sửa tệp cũ để thấy thông báo thay đổi. Đọc tên và trạng thái từng tệp trước khi chạm đến add, nhất là khi repository có nhiều công việc dở dang.

**Purpose:**

Tập thói quen kiểm tra bằng chứng trước khi add.

## Quy tắc .gitignore và ranh giới theo dõi tệp

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:34–01:48`

**Visual:**

Giữ frame kết quả lệnh [git status -sb] với nhãn 'Kiểm tra trạng thái thay đổi bằng git status'; khoanh chứng cứ đã xác nhận: Tập thói quen kiểm tra bằng chứng trước khi add.. Mở cửa sổ terminal/đồ thị kế tại [printf 'dist/\n.env\n' > .gitignore] dưới nhãn 'Quy tắc .gitignore và ranh giới theo dõi tệp'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Làm rõ .gitignore không thay quyền kiểm soát nội dung đã track.

**Script:**

Status cho thấy cả file không nên đưa vào commit; hãy đặt ranh giới ignore trước khi chọn nội dung.

**Purpose:**

Nối phép kiểm chứng 'Kiểm tra trạng thái thay đổi bằng git status' sang 'Quy tắc .gitignore và ranh giới theo dõi tệp', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Làm rõ .gitignore không thay quyền kiểm soát nội dung đã track.

### Scene 1 — Thực hành có kiểm chứng: Quy tắc .gitignore và ranh giới theo dõi tệp

**Time:** `01:48–03:22`

**Visual:**

Trên repository chỉ dùng cho demo, mở terminal sát cửa sổ hiển thị README và gõ lần lượt `printf 'dist/\n.env\n' > .gitignore; git status --short`. So sánh trước/sau và làm nổi bật dòng chứng minh trạng thái.

**Script:**

Cùng một thư mục có file nguồn và file phát sinh. Đưa dist/ và .env vào .gitignore rồi kiểm tra tệp chưa được theo dõi biến khỏi danh sách thường thấy. Nhưng hãy nhấn mạnh: ignore không tự làm biến mất tệp đã được track; nếu một secret từng commit, việc thêm ignore không xoá nó khỏi lịch sử. Chỉ commit .gitignore sau khi đã xem nội dung và phạm vi.

**Purpose:**

Làm rõ .gitignore không thay quyền kiểm soát nội dung đã track.

## Khác biệt giữa nội dung working tree và index

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:36`

**Visual:**

Giữ frame kết quả lệnh [printf 'dist/\n.env\n' > .gitignore] với nhãn 'Quy tắc .gitignore và ranh giới theo dõi tệp'; khoanh chứng cứ đã xác nhận: Làm rõ .gitignore không thay quyền kiểm soát nội dung đã track.. Mở cửa sổ terminal/đồ thị kế tại [git diff -- README.md] dưới nhãn 'Khác biệt giữa nội dung working tree và index'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Chứng minh ranh giới staged và unstaged bằng chính một tệp.

**Script:**

Một file đã track có thể có hai bản diff; cần biết xem bản nào sẽ được ghi.

**Purpose:**

Nối phép kiểm chứng 'Quy tắc .gitignore và ranh giới theo dõi tệp' sang 'Khác biệt giữa nội dung working tree và index', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Chứng minh ranh giới staged và unstaged bằng chính một tệp.

### Scene 1 — Thực hành có kiểm chứng: Khác biệt giữa nội dung working tree và index

**Time:** `03:36–05:10`

**Visual:**

Trên repository chỉ dùng cho demo, mở terminal sát cửa sổ hiển thị README và gõ lần lượt `git diff -- README.md; git diff --cached -- README.md`. So sánh trước/sau và làm nổi bật dòng chứng minh trạng thái.

**Script:**

Hãy add một sửa đổi, rồi đổi README thêm lần nữa. Ở khung trên, diff không có cached cho thấy phần chênh mới ở working tree; ở khung dưới, diff --cached cho thấy đúng nội dung index chuẩn bị commit. Đừng vội nói hai diff là cùng một thay đổi: đó là hai cặp vùng khác nhau. Tắt màn hình trước commit và yêu cầu người học dự đoán dòng nào được ghi vào snapshot.

**Purpose:**

Chứng minh ranh giới staged và unstaged bằng chính một tệp.

## Chọn lọc nội dung cần ghi nhận bằng staging

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:10–05:24`

**Visual:**

Giữ frame kết quả lệnh [git diff -- README.md] với nhãn 'Khác biệt giữa nội dung working tree và index'; khoanh chứng cứ đã xác nhận: Chứng minh ranh giới staged và unstaged bằng chính một tệp.. Mở cửa sổ terminal/đồ thị kế tại [git add -p README.md] dưới nhãn 'Chọn lọc nội dung cần ghi nhận bằng staging'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Biểu diễn một commit có phạm vi rõ bằng partial staging.

**Script:**

Hai bản diff cho thấy ta cần chọn có chủ đích, không chỉ chạy add tất cả.

**Purpose:**

Nối phép kiểm chứng 'Khác biệt giữa nội dung working tree và index' sang 'Chọn lọc nội dung cần ghi nhận bằng staging', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Biểu diễn một commit có phạm vi rõ bằng partial staging.

### Scene 1 — Thực hành có kiểm chứng: Chọn lọc nội dung cần ghi nhận bằng staging

**Time:** `05:24–06:58`

**Visual:**

Trên repository chỉ dùng cho demo, mở terminal sát cửa sổ hiển thị README và gõ lần lượt `git add -p README.md; git diff --cached`. So sánh trước/sau và làm nổi bật dòng chứng minh trạng thái.

**Script:**

Bây giờ một tệp chứa cả sửa lỗi và đổi định dạng chưa liên quan. Thay vì add tất cả, dùng add -p để xem từng hunk, chọn riêng phần sửa lỗi rồi đọc lại diff --cached. Điều chúng ta muốn ghi nhận là một ý định nghiệp vụ có thể giải thích, không nhất thiết toàn bộ thay đổi trong thư mục. Nếu hunk quá lớn, tách công việc hoặc kiểm tra lại lựa chọn; không cần dùng lệnh nguy hiểm để hoàn tác sự lựa chọn.

**Purpose:**

Biểu diễn một commit có phạm vi rõ bằng partial staging.

## Commit: bản chụp có chủ đích và thông điệp thay đổi

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:58–07:12`

**Visual:**

Giữ frame kết quả lệnh [git add -p README.md] với nhãn 'Chọn lọc nội dung cần ghi nhận bằng staging'; khoanh chứng cứ đã xác nhận: Biểu diễn một commit có phạm vi rõ bằng partial staging.. Mở cửa sổ terminal/đồ thị kế tại [git commit -m "Validate negative totals"] dưới nhãn 'Commit: bản chụp có chủ đích và thông điệp thay đổi'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Liên hệ staged snapshot với commit có thông điệp hành vi.

**Script:**

Sau khi chọn đúng phần sửa, cần đặt tên một mốc lịch sử đủ ý nghĩa để đọc lại.

**Purpose:**

Nối phép kiểm chứng 'Chọn lọc nội dung cần ghi nhận bằng staging' sang 'Commit: bản chụp có chủ đích và thông điệp thay đổi', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Liên hệ staged snapshot với commit có thông điệp hành vi.

### Scene 1 — Thực hành có kiểm chứng: Commit: bản chụp có chủ đích và thông điệp thay đổi

**Time:** `07:12–08:46`

**Visual:**

Trên repository chỉ dùng cho demo, mở terminal sát cửa sổ hiển thị README và gõ lần lượt `git commit -m "Validate negative totals"; git show --stat HEAD`. So sánh trước/sau và làm nổi bật dòng chứng minh trạng thái.

**Script:**

Khi diff --cached đã đúng, tạo commit với thông điệp mô tả hành vi đã sửa, không ghi những câu như 'fix things'. Mở git show --stat và đọc tệp cùng commit mới; điều Git ghi lại chính là snapshot của index lúc commit, còn phần chỉnh tiếp trong working tree có thể vẫn chưa được ghi. Đừng lấy việc thông báo commit thành công làm bằng chứng chương trình đã qua kiểm thử.

**Purpose:**

Liên hệ staged snapshot với commit có thông điệp hành vi.

## Lịch sử git log, commit ID và quan hệ cha–con

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:46–09:00`

**Visual:**

Giữ frame kết quả lệnh [git commit -m "Validate negative totals"] với nhãn 'Commit: bản chụp có chủ đích và thông điệp thay đổi'; khoanh chứng cứ đã xác nhận: Liên hệ staged snapshot với commit có thông điệp hành vi.. Mở cửa sổ terminal/đồ thị kế tại [git log --oneline --graph --decorate -5] dưới nhãn 'Lịch sử git log, commit ID và quan hệ cha–con'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Đọc parent/commit ID làm nền cho đồ thị lịch sử.

**Script:**

Một commit đơn lẻ đã có ý nghĩa; nhưng lịch sử nối chúng lại như thế nào?

**Purpose:**

Nối phép kiểm chứng 'Commit: bản chụp có chủ đích và thông điệp thay đổi' sang 'Lịch sử git log, commit ID và quan hệ cha–con', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Đọc parent/commit ID làm nền cho đồ thị lịch sử.

### Scene 1 — Thực hành có kiểm chứng: Lịch sử git log, commit ID và quan hệ cha–con

**Time:** `09:00–10:34`

**Visual:**

Trên repository chỉ dùng cho demo, mở terminal sát cửa sổ hiển thị README và gõ lần lượt `git log --oneline --graph --decorate -5; git show --pretty=raw HEAD`. So sánh trước/sau và làm nổi bật dòng chứng minh trạng thái.

**Script:**

Màn hình hiển thị ba commit và những đường nối cha–con. log oneline giúp định vị mốc, còn show --pretty=raw làm rõ ID và parent của commit đang chọn. ID là danh tính của đối tượng nội dung và metadata, không phải số thứ tự đếm một, hai, ba. Hãy chỉ đường HEAD về commit hiện tại rồi lần về parent; chính quan hệ này sẽ giúp đọc branch và merge ở các chương sau.

**Purpose:**

Đọc parent/commit ID làm nền cho đồ thị lịch sử.

## So sánh các mốc commit và phiên bản tệp

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:34–10:48`

**Visual:**

Giữ frame kết quả lệnh [git log --oneline --graph --decorate -5] với nhãn 'Lịch sử git log, commit ID và quan hệ cha–con'; khoanh chứng cứ đã xác nhận: Đọc parent/commit ID làm nền cho đồ thị lịch sử.. Mở cửa sổ terminal/đồ thị kế tại [git diff HEAD~1 HEAD -- README.md] dưới nhãn 'So sánh các mốc commit và phiên bản tệp'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Tách so sánh snapshot khỏi trạng thái tệp đang sửa.

**Script:**

Lịch sử có ID và parent, vậy ta dùng chúng để hỏi một tệp đã đổi ra sao.

**Purpose:**

Nối phép kiểm chứng 'Lịch sử git log, commit ID và quan hệ cha–con' sang 'So sánh các mốc commit và phiên bản tệp', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Tách so sánh snapshot khỏi trạng thái tệp đang sửa.

### Scene 1 — Thực hành có kiểm chứng: So sánh các mốc commit và phiên bản tệp

**Time:** `10:48–12:22`

**Visual:**

Trên repository chỉ dùng cho demo, mở terminal sát cửa sổ hiển thị README và gõ lần lượt `git diff HEAD~1 HEAD -- README.md; git show HEAD~1:README.md`. So sánh trước/sau và làm nổi bật dòng chứng minh trạng thái.

**Script:**

Muốn biết sửa đổi thực sự, đừng đoán từ thông điệp commit. So sánh HEAD~1 với HEAD trên đúng tệp, rồi mở bản tệp thuộc commit cũ. Mũi tên trên sơ đồ chỉ hai mốc snapshot, không phải nội dung working tree hiện thời. Nếu commit root không có HEAD~1, hãy chọn hai commit hợp lệ sau khi kiểm tra log; chúng ta luôn xác minh điểm so sánh trước khi chạy lệnh.

**Purpose:**

Tách so sánh snapshot khỏi trạng thái tệp đang sửa.

## Bằng chứng phân biệt nội dung đã commit và thay đổi chưa ghi nhận

<!-- VIDEO_SECTION -->

### Transition

**Time:** `12:22–12:36`

**Visual:**

Giữ frame kết quả lệnh [git diff HEAD~1 HEAD -- README.md] với nhãn 'So sánh các mốc commit và phiên bản tệp'; khoanh chứng cứ đã xác nhận: Tách so sánh snapshot khỏi trạng thái tệp đang sửa.. Mở cửa sổ terminal/đồ thị kế tại [git status -sb] dưới nhãn 'Bằng chứng phân biệt nội dung đã commit và thay đổi chưa ghi nhận'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Tạo checklist bằng chứng một commit hoàn chỉnh nhưng không đánh đồng sạch với đúng.

**Script:**

So sánh hai mốc đã lưu chưa đủ; cần xác minh phần chưa lưu còn lại.

**Purpose:**

Nối phép kiểm chứng 'So sánh các mốc commit và phiên bản tệp' sang 'Bằng chứng phân biệt nội dung đã commit và thay đổi chưa ghi nhận', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Tạo checklist bằng chứng một commit hoàn chỉnh nhưng không đánh đồng sạch với đúng.

### Scene 1 — Thực hành có kiểm chứng: Bằng chứng phân biệt nội dung đã commit và thay đổi chưa ghi nhận

**Time:** `12:36–14:10`

**Visual:**

Trên repository chỉ dùng cho demo, mở terminal sát cửa sổ hiển thị README và gõ lần lượt `git status -sb; git diff; git diff --cached; git show --stat HEAD`. So sánh trước/sau và làm nổi bật dòng chứng minh trạng thái.

**Script:**

Hãy dừng lại trước khi khép chương: mở bốn cửa sổ nhỏ cùng một repository. status ghi các thay đổi hiện thời; hai diff phân biệt staged và chưa staged; show đọc nội dung commit gần nhất. Một commit đã tạo không bảo đảm tất cả thay đổi đã vào lịch sử. Nếu vẫn còn dấu M sau commit, ta phải quyết định đó là công việc tiếp hay phần vô tình bỏ sót, thay vì chạy add . cho thật sạch.

**Purpose:**

Tạo checklist bằng chứng một commit hoàn chỉnh nhưng không đánh đồng sạch với đúng.
