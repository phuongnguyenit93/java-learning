---
video:
  url: ""
---

# Thực hành một vòng thay đổi Git hoàn chỉnh

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

## Tình huống theo dõi tệp từ lần sửa tới commit

<!-- VIDEO_SECTION -->

### Scene 1 — Một tình huống, một chuỗi bằng chứng

**Time:** `00:00–01:52`

**Visual:**

Sử dụng các clone demo độc lập của ví dụ sửa rounding; terminal, diff, commit graph và refs. Chạy/đọc `git status -sb; git diff; git add README.md; git diff --cached; git commit -m "Fix rounding"`. Dừng tại đầu ra quan trọng và so sánh với ảnh chụp bước trước, không thao tác trên source thật.

**Script:**

Hãy mở một tình huống duy nhất: lỗi làm tròn giá trong README hoặc mã ví dụ của một kho Git độc lập. Ban đầu status ghi thay đổi chưa staging; xem diff để hiểu đúng dòng, add riêng tệp cần thiết, đọc diff --cached, rồi commit với tên mô tả sửa lỗi. Đóng băng bốn thời điểm trước/sau để người xem chỉ được vùng nào đã thay đổi và vì sao một commit thành công không thay thế kiểm thử nghiệp vụ.

**Purpose:**

Tổng hợp mô hình ba vùng bằng một thay đổi xuyên suốt.

## Tình huống theo dõi commit qua nhánh và remote-tracking reference

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:52–02:04`

**Visual:**

Giữ frame kết quả lệnh [git status -sb] với nhãn 'Tình huống theo dõi tệp từ lần sửa tới commit'; khoanh chứng cứ đã xác nhận: Tổng hợp mô hình ba vùng bằng một thay đổi xuyên suốt.. Mở cửa sổ terminal/đồ thị kế tại [git init --bare ../remote-demo.git] dưới nhãn 'Tình huống theo dõi commit qua nhánh và remote-tracking reference'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Kết nối commit/branch với remote mà không giả lập PR API.

**Script:**

Đã có commit, nhưng công việc nhóm sẽ cần nhánh riêng và nơi trao đổi lịch sử.

**Purpose:**

Nối phép kiểm chứng 'Tình huống theo dõi tệp từ lần sửa tới commit' sang 'Tình huống theo dõi commit qua nhánh và remote-tracking reference', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Kết nối commit/branch với remote mà không giả lập PR API.

### Scene 1 — Một tình huống, một chuỗi bằng chứng

**Time:** `02:04–03:56`

**Visual:**

Từ repo demo Chương 8 đã có commit baseline trên `main`, tạo bare remote NẰM TRONG thư mục cha thực hành bằng `git init --bare ../remote-demo.git`, khai báo `git remote add origin ../remote-demo.git` chỉ nếu chưa có origin. TRƯỚC KHI tạo feature, chạy rõ `git push -u origin main; git fetch origin`, xác nhận `git ls-remote origin main` và `origin/main` local có commit thật. Sau đó mới `git switch -c feature/rounding`, commit bản sửa thực và `git push -u origin feature/rounding; git ls-remote origin`. So hai remote branches; chỉ push feature không tự tạo main ở server.

**Script:**

Sau commit nền, kết nối bare remote và publish `main` trước để có `origin/main` thật. Chỉ sau đó tạo nhánh feature, ghi bản sửa riêng và publish nhánh đó. So các refs main, feature ở local và server sau push. Bây giờ đồng nghiệp mới có thể cập nhật main độc lập để ta quan sát lịch sử phân kỳ. Nhắc rõ push không tự mở pull request và quyền review không thuộc câu lệnh Git.

**Purpose:**

Kết nối commit/branch với remote mà không giả lập PR API.

## So sánh cơ chế tích hợp bằng merge và rebase

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:56–04:08`

**Visual:**

Giữ frame kết quả lệnh [git init --bare ../remote-demo.git] với nhãn 'Tình huống theo dõi commit qua nhánh và remote-tracking reference'; khoanh chứng cứ đã xác nhận: Kết nối commit/branch với remote mà không giả lập PR API.. Mở cửa sổ terminal/đồ thị kế tại [git fetch origin] dưới nhãn 'So sánh cơ chế tích hợp bằng merge và rebase'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Làm rõ trade-off lịch sử merge/rebase trong tình huống chung.

**Script:**

Remote có thể thêm commit mới; lúc này phải chọn cơ chế tích hợp dựa vào lịch sử có thật.

**Purpose:**

Nối phép kiểm chứng 'Tình huống theo dõi commit qua nhánh và remote-tracking reference' sang 'So sánh cơ chế tích hợp bằng merge và rebase', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Làm rõ trade-off lịch sử merge/rebase trong tình huống chung.

### Scene 1 — Một tình huống, một chuỗi bằng chứng

**Time:** `04:08–06:00`

**Visual:**

Trước cảnh tích hợp, clone bare remote đã seed thành bản `colleague-copy` riêng, checkout `main`, commit một sửa đổi ở dòng KHÁC với rounding bằng danh tính demo và push main lên bare remote. Giữ tác giả ở `feature/rounding` trong repo thực hành đầu tiên. Tại bản tác giả chạy `git fetch origin; git log --graph --oneline --decorate --all; git merge origin/main`. Đóng băng đồ thị trước merge để chứng minh feature và origin/main có commit mới riêng; diễn rebase bằng sơ đồ so sánh chứ không rewrite nhánh shared.

**Script:**

Một clone đồng nghiệp thật sự vừa đẩy commit lên remote main khi tác giả ở feature. Ta fetch và xem hai tip khác nhau rồi mới tích hợp; merge có thể giữ dấu vết phân nhánh, còn rebase phát lại commit chưa publish khi chính sách cho phép. Demo này chọn merge và kiểm tra kết quả, không rebase lịch sử đã được chia sẻ. Quyết định trunk hay Git Flow là chính sách nhóm chứ không phải do một lệnh.

**Purpose:**

Làm rõ trade-off lịch sử merge/rebase trong tình huống chung.

## Chẩn đoán lịch sử phân kỳ và xung đột

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:00–06:12`

**Visual:**

Giữ frame kết quả lệnh [git fetch origin] với nhãn 'So sánh cơ chế tích hợp bằng merge và rebase'; khoanh chứng cứ đã xác nhận: Làm rõ trade-off lịch sử merge/rebase trong tình huống chung.. Mở cửa sổ terminal/đồ thị kế tại [git merge origin/main] dưới nhãn 'Chẩn đoán lịch sử phân kỳ và xung đột'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Chứng minh conflict được xử lý bằng nghiệp vụ và trạng thái index.

**Script:**

Hai lịch sử phân kỳ là chuyện bình thường; điểm khó thật sự là conflict đòi quyết định nội dung.

**Purpose:**

Nối phép kiểm chứng 'So sánh cơ chế tích hợp bằng merge và rebase' sang 'Chẩn đoán lịch sử phân kỳ và xung đột', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Chứng minh conflict được xử lý bằng nghiệp vụ và trạng thái index.

### Scene 1 — Một tình huống, một chuỗi bằng chứng

**Time:** `06:12–08:04`

**Visual:**

Cảnh trước đã merge thành công KHÔNG conflict, nên không chạy lại trên chính nhánh ấy rồi tuyên bố lỗi. Chuẩn bị cặp clone demo MỚI từ baseline trước merge: ở nhánh `main` clone A sửa một dòng rounding và push lên bare remote; ở `feature/rounding` clone B commit sửa KHÁC trên CHÍNH dòng ấy. Tại clone B fetch rồi chạy `git merge origin/main; git status; git ls-files -u; git diff --check`. Dừng ở unmerged index entries thật, dùng yêu cầu nghiệp vụ để xử lý, stage và xác minh; không chạm source công ty.

**Script:**

Không dùng lại nhánh đã merge ở cảnh trước. Hai clone demo mới ghi hai commit xung đột trên cùng dòng rounding. Sau fetch, merge dừng và status cùng ls-files -u hiện các index stages chưa hợp nhất. Kiểm tra yêu cầu nghiệp vụ, sửa có chủ đích, stage và test rồi mới hoàn tất merge. Không commit conflict marker hoặc dùng lệnh hủy dữ liệu của video trên repository công ty.

**Purpose:**

Chứng minh conflict được xử lý bằng nghiệp vụ và trạng thái index.

## Lựa chọn hoàn tác theo trạng thái chia sẻ của commit

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:04–08:16`

**Visual:**

Giữ frame kết quả lệnh [git merge origin/main] với nhãn 'Chẩn đoán lịch sử phân kỳ và xung đột'; khoanh chứng cứ đã xác nhận: Chứng minh conflict được xử lý bằng nghiệp vụ và trạng thái index.. Mở cửa sổ terminal/đồ thị kế tại [git status --short] dưới nhãn 'Lựa chọn hoàn tác theo trạng thái chia sẻ của commit'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Dùng phân loại trạng thái để chọn revert thay vì rewrite bừa.

**Script:**

Một fix đã được tích hợp có thể vẫn sai; chọn hoàn tác phụ thuộc commit có chia sẻ hay chưa.

**Purpose:**

Nối phép kiểm chứng 'Chẩn đoán lịch sử phân kỳ và xung đột' sang 'Lựa chọn hoàn tác theo trạng thái chia sẻ của commit', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Dùng phân loại trạng thái để chọn revert thay vì rewrite bừa.

### Scene 1 — Một tình huống, một chuỗi bằng chứng

**Time:** `08:16–10:08`

**Visual:**

Sử dụng các clone demo độc lập của ví dụ sửa rounding; terminal, diff, commit graph và refs. Chạy/đọc `git status --short; git log --oneline --all; git revert <selected-commit-id>`. Dừng tại đầu ra quan trọng và so sánh với ảnh chụp bước trước, không thao tác trên source thật.

**Script:**

Trong tình huống một commit sai chỉ ở local, người viết có thể sửa bằng commit tiếp hoặc rewrite có chủ đích trước khi chia sẻ. Nhưng nếu bản sửa sai đã có trên nhánh chung, minh họa revert trên clone thử tạo commit đảo thay đổi, giữ lại dấu vết lịch sử. Chọn ID từ log thật của demo chứ không gõ placeholder. Hỏi người xem: tệp chưa commit, commit local hay shared? Câu trả lời quyết định công cụ.

**Purpose:**

Dùng phân loại trạng thái để chọn revert thay vì rewrite bừa.

## Đối chiếu status, diff, lịch sử và tham chiếu sau một vòng thay đổi

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:08–10:20`

**Visual:**

Giữ frame kết quả lệnh [git status --short] với nhãn 'Lựa chọn hoàn tác theo trạng thái chia sẻ của commit'; khoanh chứng cứ đã xác nhận: Dùng phân loại trạng thái để chọn revert thay vì rewrite bừa.. Mở cửa sổ terminal/đồ thị kế tại [git status -sb] dưới nhãn 'Đối chiếu status, diff, lịch sử và tham chiếu sau một vòng thay đổi'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Kết thúc với checklist trạng thái, diff, graph và refs nhất quán.

**Script:**

Sau khi sửa và hoàn tác, cần có cách tự tin xác nhận toàn bộ đường đi thay đổi.

**Purpose:**

Nối phép kiểm chứng 'Lựa chọn hoàn tác theo trạng thái chia sẻ của commit' sang 'Đối chiếu status, diff, lịch sử và tham chiếu sau một vòng thay đổi', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Kết thúc với checklist trạng thái, diff, graph và refs nhất quán.

### Scene 1 — Một tình huống, một chuỗi bằng chứng

**Time:** `10:20–12:12`

**Visual:**

Sử dụng các clone demo độc lập của ví dụ sửa rounding; terminal, diff, commit graph và refs. Chạy/đọc `git status -sb; git diff; git diff --cached; git log --graph --oneline --all; git show-ref`. Dừng tại đầu ra quan trọng và so sánh với ảnh chụp bước trước, không thao tác trên source thật.

**Script:**

Tổng kết bằng bảng năm bằng chứng: trạng thái working tree, staged diff, bản commit đã ghi, đồ thị parent và vị trí refs local/remote. Hãy yêu cầu người học dự đoán khi nào output status sạch nhưng origin/main vẫn cũ; khi nào log đã có commit nhưng tệp vẫn đang sửa. Đó là cách tự chẩn đoán Git: không học thuộc chuỗi lệnh mà đối chiếu kết quả của nhiều góc nhìn độc lập.

**Purpose:**

Kết thúc với checklist trạng thái, diff, graph và refs nhất quán.

## Ranh giới giữa cơ chế Git, review nền tảng và chiến lược nhánh

<!-- VIDEO_SECTION -->

### Transition

**Time:** `12:12–12:24`

**Visual:**

Giữ frame kết quả lệnh [git status -sb] với nhãn 'Đối chiếu status, diff, lịch sử và tham chiếu sau một vòng thay đổi'; khoanh chứng cứ đã xác nhận: Kết thúc với checklist trạng thái, diff, graph và refs nhất quán.. Mở cửa sổ terminal/đồ thị kế tại [git log --oneline --graph --all] dưới nhãn 'Ranh giới giữa cơ chế Git, review nền tảng và chiến lược nhánh'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Bàn giao đúng trách nhiệm Git mechanics sang review và branching policy.

**Script:**

Có đủ bằng chứng cơ chế Git; giờ chỉ ra ranh giới nơi công cụ nhường chỗ cho quy trình đội nhóm.

**Purpose:**

Nối phép kiểm chứng 'Đối chiếu status, diff, lịch sử và tham chiếu sau một vòng thay đổi' sang 'Ranh giới giữa cơ chế Git, review nền tảng và chiến lược nhánh', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Bàn giao đúng trách nhiệm Git mechanics sang review và branching policy.

### Scene 1 — Một tình huống, một chuỗi bằng chứng

**Time:** `12:24–14:16`

**Visual:**

Sử dụng các clone demo độc lập của ví dụ sửa rounding; terminal, diff, commit graph và refs. Chạy/đọc `git log --oneline --graph --all; git branch -vv`. Dừng tại đầu ra quan trọng và so sánh với ảnh chụp bước trước, không thao tác trên source thật.

**Script:**

Trên sơ đồ cuối, Git cho ta commit, nhánh, diff và trao đổi lịch sử giữa repository; nhưng nó không tự biết ai đã review hay quy tắc merge của tổ chức. Chỉ sang giao diện GitHub/GitLab/Azure Repos để thấy PR/MR là một tầng quản trị khác, còn việc chọn mô hình nhánh thuộc module Branching Strategy. Hãy giữ một bài học rõ ràng: thành thạo cơ chế Git giúp đọc bằng chứng; chấp thuận thay đổi và chính sách phát hành cần quy trình ngoài Git.

**Purpose:**

Bàn giao đúng trách nhiệm Git mechanics sang review và branching policy.
