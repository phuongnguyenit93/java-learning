---
video:
  url: ""
---

# Stash, hoàn tác và phục hồi an toàn

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

## Phân loại thay đổi chưa commit và lịch sử đã chia sẻ trước khi hoàn tác

<!-- VIDEO_SECTION -->

### Scene 1 — Đưa ra quyết định không phá dữ liệu

**Time:** `00:00–01:48`

**Visual:**

Dùng repository thử nghiệm có bản sao riêng và sơ đồ working tree/index/HEAD; kiểm tra có chủ đích `git status --short; git log --oneline -4; git branch -vv`. Khoanh status trước/sau và hiển thị cảnh báo thao tác phá huỷ; placeholder commit ID phải được lấy từ log thật.

**Script:**

Khi cần hoàn tác, việc đầu tiên là phân loại: tệp có sửa chưa commit, phần nào đã staging, commit nào chỉ ở máy mình và commit nào đã push. Cùng một chữ 'undo' nhưng mỗi trạng thái có rủi ro khác nhau. Hãy chụp status và log trong repository thực hành, ghi lại ID hiện tại trước bất kỳ lệnh sửa lịch sử nào. Không có một nút Git nào vừa an toàn vừa phù hợp cho mọi trường hợp.

**Purpose:**

Giữ một bản ghi trạng thái trước khi chọn công cụ hoàn tác.

## Stash: tạm cất và khôi phục công việc chưa commit

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:48–02:02`

**Visual:**

Giữ frame kết quả lệnh [git status --short] với nhãn 'Phân loại thay đổi chưa commit và lịch sử đã chia sẻ trước khi hoàn tác'; khoanh chứng cứ đã xác nhận: Giữ một bản ghi trạng thái trước khi chọn công cụ hoàn tác.. Mở cửa sổ terminal/đồ thị kế tại [git stash push -m "pause pricing fix"] dưới nhãn 'Stash: tạm cất và khôi phục công việc chưa commit'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Phân biệt stash push/apply/pop và bằng chứng khôi phục.

**Script:**

Ta biết phải giữ công việc chưa commit; stash là nơi cất tạm, không phải commit chính thức.

**Purpose:**

Nối phép kiểm chứng 'Phân loại thay đổi chưa commit và lịch sử đã chia sẻ trước khi hoàn tác' sang 'Stash: tạm cất và khôi phục công việc chưa commit', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Phân biệt stash push/apply/pop và bằng chứng khôi phục.

### Scene 1 — Đưa ra quyết định không phá dữ liệu

**Time:** `02:02–03:50`

**Visual:**

Dùng repository thử nghiệm có bản sao riêng và sơ đồ working tree/index/HEAD; kiểm tra có chủ đích `git stash push -m "pause pricing fix"; git stash list; git stash apply`. Khoanh status trước/sau và hiển thị cảnh báo thao tác phá huỷ; placeholder commit ID phải được lấy từ log thật.

**Script:**

Giả sử đang sửa pricing thì phải chuyển sang lỗi khẩn cấp. Sau khi kiểm tra status, stash cất thay đổi tracked chưa commit và trả working tree gần về trạng thái trước đó để có thể đổi việc. Dùng stash list xác minh mục vừa tạo, rồi apply để thử đưa lại công việc; apply giữ stash trong danh sách, còn pop sẽ thử áp dụng rồi gỡ mục khi thành công. Nếu có conflict, phải giải quyết chứ không cho rằng stash luôn khôi phục nguyên trạng.

**Purpose:**

Phân biệt stash push/apply/pop và bằng chứng khôi phục.

## Phạm vi stash mặc định và tùy chọn cho tệp untracked hoặc ignored

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:50–04:04`

**Visual:**

Giữ frame kết quả lệnh [git stash push -m "pause pricing fix"] với nhãn 'Stash: tạm cất và khôi phục công việc chưa commit'; khoanh chứng cứ đã xác nhận: Phân biệt stash push/apply/pop và bằng chứng khôi phục.. Mở cửa sổ terminal/đồ thị kế tại [git stash push -u -m "include new notes"] dưới nhãn 'Phạm vi stash mặc định và tùy chọn cho tệp untracked hoặc ignored'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Chỉ ra ranh giới mặc định của stash qua tệp untracked.

**Script:**

Stash có vẻ đã cất mọi thứ, nhưng tệp mới và tệp ignored không đi theo mặc định.

**Purpose:**

Nối phép kiểm chứng 'Stash: tạm cất và khôi phục công việc chưa commit' sang 'Phạm vi stash mặc định và tùy chọn cho tệp untracked hoặc ignored', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Chỉ ra ranh giới mặc định của stash qua tệp untracked.

### Scene 1 — Đưa ra quyết định không phá dữ liệu

**Time:** `04:04–05:52`

**Visual:**

Dùng repository thử nghiệm có bản sao riêng và sơ đồ working tree/index/HEAD; kiểm tra có chủ đích `git stash push -u -m "include new notes"; git stash list; git status -sb`. Khoanh status trước/sau và hiển thị cảnh báo thao tác phá huỷ; placeholder commit ID phải được lấy từ log thật.

**Script:**

Tạo cả file tracked đã sửa và file notes mới chưa được Git theo dõi. Stash mặc định cất thay đổi tracked nhưng không tự chứa mọi file untracked, còn -u cho phép đưa untracked vào; ignored files cần lựa chọn khác như -a và phải cực kỳ cẩn thận. Hãy xem status trước và sau rồi đọc stash show nếu cần. Dữ liệu cần bảo vệ đừng chỉ dựa vào một stash không được sao lưu.

**Purpose:**

Chỉ ra ranh giới mặc định của stash qua tệp untracked.

## git restore và ảnh hưởng lên working tree, index

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:52–06:06`

**Visual:**

Giữ frame kết quả lệnh [git stash push -u -m "include new notes"] với nhãn 'Phạm vi stash mặc định và tùy chọn cho tệp untracked hoặc ignored'; khoanh chứng cứ đã xác nhận: Chỉ ra ranh giới mặc định của stash qua tệp untracked.. Mở cửa sổ terminal/đồ thị kế tại [git restore -- README.md] dưới nhãn 'git restore và ảnh hưởng lên working tree, index'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Phân biệt restore working tree với restore --staged index.

**Script:**

Khi đã đưa file khỏi stash, nhiều lúc chỉ muốn bỏ staged chứ không muốn mất phần đang sửa.

**Purpose:**

Nối phép kiểm chứng 'Phạm vi stash mặc định và tùy chọn cho tệp untracked hoặc ignored' sang 'git restore và ảnh hưởng lên working tree, index', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Phân biệt restore working tree với restore --staged index.

### Scene 1 — Đưa ra quyết định không phá dữ liệu

**Time:** `06:06–07:54`

**Visual:**

Dùng repository thử nghiệm có bản sao riêng và sơ đồ working tree/index/HEAD; kiểm tra có chủ đích `git restore -- README.md; git restore --staged README.md; git diff --cached`. Khoanh status trước/sau và hiển thị cảnh báo thao tác phá huỷ; placeholder commit ID phải được lấy từ log thật.

**Script:**

Một lệnh restore có thể tác động tới working tree hoặc index tùy cờ. Trong bản sao thử, hiển thị diff trước rồi mới restore một file đã chủ động chọn; restore --staged bỏ staged khỏi index mà thường vẫn giữ nội dung trong working tree. Ngược lại restore path từ index có thể loại bỏ phần sửa chưa staging. Đây là thao tác có khả năng mất nội dung, vì vậy phải kiểm tra diff và không thử bừa trên source thật.

**Purpose:**

Phân biệt restore working tree với restore --staged index.

## Các chế độ git reset và rủi ro mất dữ liệu

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:54–08:08`

**Visual:**

Giữ frame kết quả lệnh [git restore -- README.md] với nhãn 'git restore và ảnh hưởng lên working tree, index'; khoanh chứng cứ đã xác nhận: Phân biệt restore working tree với restore --staged index.. Mở cửa sổ terminal/đồ thị kế tại [git reset --soft HEAD~1] dưới nhãn 'Các chế độ git reset và rủi ro mất dữ liệu'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: So sánh soft/mixed và cảnh báo hard rõ bằng ma trận ba vùng.

**Script:**

Restore xử lý nội dung/tệp; reset còn có thể di chuyển HEAD nên tác động rộng hơn.

**Purpose:**

Nối phép kiểm chứng 'git restore và ảnh hưởng lên working tree, index' sang 'Các chế độ git reset và rủi ro mất dữ liệu', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: So sánh soft/mixed và cảnh báo hard rõ bằng ma trận ba vùng.

### Scene 1 — Đưa ra quyết định không phá dữ liệu

**Time:** `08:08–09:56`

**Visual:**

Hai clone demo KHÁC NHAU đều có ít nhất hai commit và worktree sạch: ở bản A chạy riêng `git reset --soft HEAD~1; git status -sb; git diff --cached`, ở bản B bắt đầu từ CÙNG commit gốc và chạy `git reset --mixed HEAD~1; git status -sb; git diff --cached`. So index và working tree theo bảng; không thực thi soft rồi mixed nối tiếp trên một nhánh hoặc reset branch đã chia sẻ.

**Script:**

Trên hai bản clone thử tách biệt, minh họa soft di chuyển HEAD nhưng giữ index và working tree, còn mixed mặc định di chuyển HEAD và reset index nhưng để lại nội dung làm việc. Đừng chạy liên tiếp các lệnh trên cùng bản rồi tự cho đó là phép so sánh hợp lệ. reset --hard còn ghi đè cả working tree đối với tệp được theo dõi nên không được đưa vào demo thực thi mặc định; chỉ mô tả nguy cơ bằng sơ đồ và bản sao có thể xóa.

**Purpose:**

So sánh soft/mixed và cảnh báo hard rõ bằng ma trận ba vùng.

## git revert để hoàn tác lịch sử đã chia sẻ mà không viết lại commit cũ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:56–10:10`

**Visual:**

Giữ frame kết quả lệnh [git reset --soft HEAD~1] với nhãn 'Các chế độ git reset và rủi ro mất dữ liệu'; khoanh chứng cứ đã xác nhận: So sánh soft/mixed và cảnh báo hard rõ bằng ma trận ba vùng.. Mở cửa sổ terminal/đồ thị kế tại [git revert <published-commit-id>] dưới nhãn 'git revert để hoàn tác lịch sử đã chia sẻ mà không viết lại commit cũ'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Chứng minh revert không viết lại commit đã publish.

**Script:**

Reset có thể thay vị trí nhánh; lịch sử đã chia sẻ thường cần một dấu vết hoàn tác mới.

**Purpose:**

Nối phép kiểm chứng 'Các chế độ git reset và rủi ro mất dữ liệu' sang 'git revert để hoàn tác lịch sử đã chia sẻ mà không viết lại commit cũ', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Chứng minh revert không viết lại commit đã publish.

### Scene 1 — Đưa ra quyết định không phá dữ liệu

**Time:** `10:10–11:58`

**Visual:**

Dùng repository thử nghiệm có bản sao riêng và sơ đồ working tree/index/HEAD; kiểm tra có chủ đích `git revert <published-commit-id>; git log --oneline -4`. Khoanh status trước/sau và hiển thị cảnh báo thao tác phá huỷ; placeholder commit ID phải được lấy từ log thật.

**Script:**

Nếu lỗi đã nằm trong commit mà nhóm khác có thể đã fetch, cách an toàn thông thường là tạo commit mới đảo ngược thay đổi bằng revert. Chọn commit từ log của kho demo, chạy revert sau khi bảo đảm trạng thái sạch và kiểm tra kết quả. Lịch sử cũ vẫn tồn tại; đồ thị có thêm commit thể hiện quyết định hoàn tác, nhưng revert cũng có thể conflict và không khôi phục tự động mọi tác động bên ngoài như dữ liệu production.

**Purpose:**

Chứng minh revert không viết lại commit đã publish.

## Reflog cục bộ, thời hạn lưu và giới hạn khôi phục

<!-- VIDEO_SECTION -->

### Transition

**Time:** `11:58–12:12`

**Visual:**

Giữ frame kết quả lệnh [git revert <published-commit-id>] với nhãn 'git revert để hoàn tác lịch sử đã chia sẻ mà không viết lại commit cũ'; khoanh chứng cứ đã xác nhận: Chứng minh revert không viết lại commit đã publish.. Mở cửa sổ terminal/đồ thị kế tại [git reflog -8] dưới nhãn 'Reflog cục bộ, thời hạn lưu và giới hạn khôi phục'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Giới thiệu reflog với giới hạn cục bộ và thời hạn lưu.

**Script:**

Revert để lại commit công khai, còn khi lỡ đổi ref local ta cần tìm dấu vết vị trí cũ.

**Purpose:**

Nối phép kiểm chứng 'git revert để hoàn tác lịch sử đã chia sẻ mà không viết lại commit cũ' sang 'Reflog cục bộ, thời hạn lưu và giới hạn khôi phục', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Giới thiệu reflog với giới hạn cục bộ và thời hạn lưu.

### Scene 1 — Đưa ra quyết định không phá dữ liệu

**Time:** `12:12–14:00`

**Visual:**

Dùng repository thử nghiệm có bản sao riêng và sơ đồ working tree/index/HEAD; kiểm tra có chủ đích `git reflog -8; git log --oneline --all`. Khoanh status trước/sau và hiển thị cảnh báo thao tác phá huỷ; placeholder commit ID phải được lấy từ log thật.

**Script:**

Ta vừa nhìn thấy reset hay rebase có thể đổi vị trí ref. Reflog trên chính repository này ghi lại nhiều bước di chuyển HEAD/nhánh giúp tìm lại commit ID đã rời khỏi tầm nhìn của log thường. Hãy chỉ một ID cũ từ reflog nhưng không vội reset trở lại; trước tiên xác minh commit bằng show hoặc log. Reflog là dữ liệu cục bộ, có thể hết hạn hoặc bị dọn nên không thay bản sao lưu và không chứng minh server còn dữ liệu.

**Purpose:**

Giới thiệu reflog với giới hạn cục bộ và thời hạn lưu.

## Tiêu chí chọn cách khôi phục theo trạng thái Git

<!-- VIDEO_SECTION -->

### Transition

**Time:** `14:00–14:14`

**Visual:**

Giữ frame kết quả lệnh [git reflog -8] với nhãn 'Reflog cục bộ, thời hạn lưu và giới hạn khôi phục'; khoanh chứng cứ đã xác nhận: Giới thiệu reflog với giới hạn cục bộ và thời hạn lưu.. Mở cửa sổ terminal/đồ thị kế tại [git status --short] dưới nhãn 'Tiêu chí chọn cách khôi phục theo trạng thái Git'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Tổng hợp bảng quyết định phục hồi an toàn có chứng cứ.

**Script:**

Reflog giúp tìm lại mốc, nhưng điều quan trọng cuối cùng là chọn công cụ theo trạng thái cụ thể.

**Purpose:**

Nối phép kiểm chứng 'Reflog cục bộ, thời hạn lưu và giới hạn khôi phục' sang 'Tiêu chí chọn cách khôi phục theo trạng thái Git', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Tổng hợp bảng quyết định phục hồi an toàn có chứng cứ.

### Scene 1 — Đưa ra quyết định không phá dữ liệu

**Time:** `14:14–16:02`

**Visual:**

Dùng repository thử nghiệm có bản sao riêng và sơ đồ working tree/index/HEAD; kiểm tra có chủ đích `git status --short; git diff; git diff --cached; git reflog -5`. Khoanh status trước/sau và hiển thị cảnh báo thao tác phá huỷ; placeholder commit ID phải được lấy từ log thật.

**Script:**

Kết lại bằng bảng quyết định: chưa commit thì kiểm tra diff và cân nhắc stash/restore; staged nhầm thì restore --staged; commit local cần sửa thì cân nhắc amend/reset theo tác động; commit đã chia sẻ thường chọn revert. Đặt dòng warning khi xuất hiện hard reset, clean hay force push và yêu cầu sao chép repository trước khi thử. Một lựa chọn khôi phục tốt phải có bằng chứng trạng thái trước/sau, không chỉ lệnh chạy không báo lỗi.

**Purpose:**

Tổng hợp bảng quyết định phục hồi an toàn có chứng cứ.
