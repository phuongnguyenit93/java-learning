---
video:
  url: ""
---

# Lựa chọn chiến lược nhánh và các dấu hiệu vận hành sai

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

## Trunk-Based Development, GitHub Flow và Git Flow: tiêu chí so sánh

<!-- VIDEO_SECTION -->

### Scene 1 — Chọn và chẩn đoán theo bằng chứng

**Time:** `00:00–01:52`

**Visual:**

Ma trận ba cột TBD, GitHub Flow, Git Flow với hàng merge cadence, branch roles, release obligations, hosting PR, cost; mô hình tô theo ngữ cảnh.

Các số liệu case-study được ghi rõ là giả định; commit graph tạo bằng repo demo, không giả kết quả PR hoặc CI trực tiếp.

**Script:**

Đừng chọn chiến lược chỉ vì công ty nổi tiếng dùng nó. TBD ràng buộc nhịp hội tụ thường xuyên về trunk và công việc nhỏ; GitHub Flow nhấn mạnh vòng nhánh–PR–review–merge nhưng không tự giới hạn tuổi nhánh; Git Flow cổ điển tách develop và production với release/hotfix cho phiên bản theo đợt. Trên bảng, mỗi ô là một nguyên tắc cùng chi phí phải trả. Điểm chung là cần Git graph thật, review đáng tin và người có trách nhiệm giữ đường mã chung ổn định.

**Purpose:**

Đưa mô hình vào một ma trận đánh đổi thay vì xếp hạng tuyệt đối.

## Chu kỳ phát hành, nhu cầu phiên bản, quy mô nhóm và kiểm tra tự động

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:52–02:06`

**Visual:**

Giữ ma trận TBD/GitHub Flow/Git Flow, thêm ba team examples web daily, desktop quarterly và library supporting 1.4/2.0 với check maturity.

**Script:**

Ta đã đối chiếu ba chiến lược. Nhịp phát hành, nghĩa vụ hỗ trợ phiên bản và độ tin cậy checks phù hợp mỗi loại ra sao?

**Purpose:**

Biến danh sách mô hình thành câu hỏi lựa chọn theo release cadence, hỗ trợ phiên bản và năng lực kiểm thử.

### Scene 1 — Chọn và chẩn đoán theo bằng chứng

**Time:** `02:06–03:58`

**Visual:**

Ba thẻ đội: web deploy hằng ngày, desktop phát hành hàng quý, thư viện hỗ trợ hai minor versions; gắn bảng review/CI maturity.

Các số liệu case-study được ghi rõ là giả định; commit graph tạo bằng repo demo, không giả kết quả PR hoặc CI trực tiếp.

**Script:**

Với dịch vụ web triển khai nhiều lần trong ngày, nhóm thường ưu tiên nhánh ngắn, main đáng tin và thay đổi nhỏ. Với ứng dụng desktop đóng gói mỗi quý, cửa sổ ổn định release có thể đáng chi phí. Với thư viện duy trì nhiều bản cũ, cần chính sách maintenance và backport tường minh dù dùng chiến lược nào. Còn nhóm chưa có kiểm thử nhanh thì tự gọi mình TBD không làm rủi ro mất đi; phải cải thiện phản hồi trước. Hãy ghi nhu cầu hỗ trợ, nhịp phát hành và người sở hữu checks thành đầu vào quyết định.

**Purpose:**

Chọn chiến lược theo version support, cadence và maturity thực.

## Dấu hiệu nhánh sống lâu và tích hợp dồn vào cuối kỳ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:58–04:12`

**Visual:**

Từ team profiles, mở dashboard năm PR đã tồn tại hai sprint và graph merge-base xa main; giữ maintenance branch ngoài cảnh báo feature.

**Script:**

Chọn mô hình phù hợp vẫn chưa đủ. Hãy đo tuổi nhánh và nhịp merge để tìm dấu hiệu tích hợp muộn.

**Purpose:**

Phân biệt sự cách ly feature do tích hợp chậm với nhánh hỗ trợ bản cũ có lý do tồn tại lâu.

### Scene 1 — Chọn và chẩn đoán theo bằng chứng

**Time:** `04:12–06:04`

**Visual:**

Dashboard giả lập: P50/P90 tuổi PR, độ lớn diff, main merge frequency và mốc 5 PR mở từ sprint trước; graph merge-base xa.

Các số liệu case-study được ghi rõ là giả định; commit graph tạo bằng repo demo, không giả kết quả PR hoặc CI trực tiếp.

**Script:**

Nhóm báo tích hợp liên tục nhưng đồ thị cho thấy năm PR tồn qua hai sprint, mỗi PR chạm cùng thư mục. Hãy kiểm tra tuổi P50/P90, khoảng từ lần cập nhật cuối và kích thước thay đổi, không chỉ số PR đã merge. Nếu hầu hết tích hợp dồn ngày cuối, chiến lược đang tạo feedback trễ. Thử chia phạm vi, review sớm và giảm các phụ thuộc ẩn. Một nhánh maintenance lâu chưa chắc là lỗi; chỉ nên cảnh báo các work branches đáng lẽ phải hội tụ thường xuyên.

**Purpose:**

Đọc tuổi nhánh và cadence để thấy tích hợp muộn.

## Dấu hiệu xung đột lặp lại và lịch sử khó truy vết

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:04–06:18`

**Visual:**

Giữ histogram tuổi PR cùng nhánh feature xa main; làm sáng file config conflict ba lần và commit messages `fix/update/wip` thiếu bối cảnh.

**Script:**

Tuổi nhánh cho thấy độ trễ; xung đột file chung và commit khó hiểu cho thấy thêm chi phí phối hợp.

**Purpose:**

Chuyển từ tín hiệu tuổi nhánh sang chứng cứ conflict lặp và lịch sử khó chẩn đoán.

### Scene 1 — Chọn và chẩn đoán theo bằng chứng

**Time:** `06:18–08:10`

**Visual:**

Hai dòng log: cùng tệp config được giải conflict ba lần, và lịch sử 'fix', 'update', 'wip' thiếu PR link; bảng giả thuyết nguyên nhân.

Các số liệu case-study được ghi rõ là giả định; commit graph tạo bằng repo demo, không giả kết quả PR hoặc CI trực tiếp.

**Script:**

Một team phải giải conflict ở cùng tệp config ba lần trong tuần. Đó có thể là dấu hiệu nhiều nhánh làm trên contract chung quá lâu hoặc thiếu điều phối người sở hữu. Đồng thời, các commit chỉ mang tên 'fix' làm incident review không biết thay đổi nào có ý nghĩa. Hãy mở diff và PR history để tìm nơi phân kỳ, rồi đề xuất tách module, điều chỉnh review, hoặc chuẩn hóa commit message. Không đổ lỗi cho merge commit chỉ vì đồ thị xấu; cần phân biệt dữ liệu tệ với kiểu lịch sử nhóm đã chọn.

**Purpose:**

Chẩn đoán nguyên nhân conflict và thiếu truy vết bằng evidence, không đổ tại Git.

## Dấu hiệu thiếu bản vá trên các dòng mã cần bảo trì

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:10–08:24`

**Visual:**

Từ bảng conflict và lịch sử mờ, mở main đã fix rounding và maintenance/1.9 chưa có; gắn ô phiên bản chịu ảnh hưởng thay vì đếm SHA bằng mắt.

**Script:**

Lịch sử mơ hồ làm khó chẩn đoán. Thiếu bản vá trên nhánh bảo trì đang hỗ trợ cho thấy hệ quả trực tiếp.

**Purpose:**

Chỉ ra chẩn đoán cần xét propagation của bản vá, không suy từ một nhánh source đã hết lỗi.

### Scene 1 — Chọn và chẩn đoán theo bằng chứng

**Time:** `08:24–10:16`

**Visual:**

Ma trận patch `CVE-XYZ` giả định: main fixed, release/2.0 fixed, maintenance/1.9 missing; screenshot graph SHA khác trên cherry-pick.

Các số liệu case-study được ghi rõ là giả định; commit graph tạo bằng repo demo, không giả kết quả PR hoặc CI trực tiếp.

**Script:**

Một incident bảo mật đã được đóng vì commit fix xuất hiện trên main, nhưng ma trận cho thấy maintenance/1.9 còn thiếu bản vá. Đây là chỗ workflow tốt cần danh sách phiên bản được hỗ trợ, commit tương ứng và kết quả test ở từng dòng. Cherry-pick SHA khác không phải dấu hiệu tự động thiếu fix, cần đọc patch hoặc so nội dung. Với TBD, sửa trunk trước rồi backport; với classic GitFlow, hotfix từ production phải trở lại develop. Quy tắc nào cũng cần một owner xác nhận đủ dòng mã.

**Purpose:**

Chỉ ra kiểm chứng backport theo mỗi phiên bản, tránh bug tái phát.

## Chính sách nhánh cho nhóm mẫu và bằng chứng đánh giá hiệu quả

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:16–10:30`

**Visual:**

Từ bảng missing backport, tạo thẻ team policy cho sáu kỹ sư: short PR, review trong ngày, main owner, release1.4 patch; mở ô metric chưa có số.

**Script:**

Lỗ hổng bản vá cần owner. Hãy soạn chính sách thử với mục tiêu review và khôi phục đo được.

**Purpose:**

Đưa lỗi vận hành cụ thể thành chính sách có owner, giới hạn tuổi và tiêu chí đo được.

### Scene 1 — Chọn và chẩn đoán theo bằng chứng

**Time:** `10:30–12:22`

**Visual:**

Một policy card cho nhóm sáu kỹ sư checkout: main, feature tối đa vài ngày có cảnh báo, review trong ngày, test bắt buộc, release tag, branch owner.

Các số liệu case-study được ghi rõ là giả định; commit graph tạo bằng repo demo, không giả kết quả PR hoặc CI trực tiếp.

**Script:**

Hãy cùng đề xuất chính sách thử cho sáu kỹ sư xây API checkout phát hành thường xuyên. Một main dùng chung, nhánh feature có mục tiêu nhỏ và cảnh báo nếu kéo dài vài ngày; review nhận phản hồi trong ngày làm việc; test hồi quy phù hợp là điều kiện trước merge; release dùng tag và chỉ tạo maintenance branch khi phải hỗ trợ phiên bản cũ. Chọn owner theo dõi main và xử lý PR tồn. Sau một tháng, so tuổi nhánh, thời gian review, lỗi sau merge và thời gian phục hồi; nếu không cải thiện, điều chỉnh chính sách chứ không đổi tên mô hình cho đẹp.

**Purpose:**

Đưa ra policy thử có owner, tiêu chí và chu kỳ điều chỉnh.

## Ranh giới cuối: Git mechanics, nền tảng cộng tác và delivery automation

<!-- VIDEO_SECTION -->

### Transition

**Time:** `12:22–12:36`

**Visual:**

Giữ policy và metric branch age/review wait chưa đo; chia bản giao việc thành Git history, hosted PR access, CI validation và release support.

**Script:**
Sau khi đánh giá chính sách thử, hãy giao Git mechanics, bảo vệ trên host và tự động hóa CI/release cho owner.



**Purpose:**

Kết thúc khóa bằng phân công thực hiện từng bằng chứng cho đúng owner module lân cận.

### Scene 1 — Chọn và chẩn đoán theo bằng chứng

**Time:** `12:36–14:28`

**Visual:**

Sơ đồ phân trách nhiệm cuối: Team strategy lựa chọn nhịp; Git lưu graph; hosted PR/security thực thi; CI kiểm tra; release tooling xuất bản.

Các số liệu case-study được ghi rõ là giả định; commit graph tạo bằng repo demo, không giả kết quả PR hoặc CI trực tiếp.

**Script:**

Chúng ta đã đi hết ba mô hình, nhưng không có đường tắt bằng một API demo local. Nếu muốn hiểu `git merge`, `rebase` hay cherry-pick cụ thể, quay về Git mechanics. Nếu cần bắt buộc hai reviewer, chuyển sang Github/GitLab/Bitbucket/Azure Repos để cấu hình quyền và PR. Nếu muốn thực thi kiểm thử trước merge hoặc deploy, học CI/CD và release riêng. Chiến lược nhánh giữ vai trò quyết định điều gì phải xảy ra và lấy bằng chứng nào để biết quy trình có hiệu quả. Đó là trách nhiệm cuối cùng của đội, không phải tên một nhánh.

**Purpose:**

Kết thúc đúng ranh giới chiến lược và các nơi thực thi cơ chế.
