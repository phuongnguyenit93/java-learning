---
video:
  url: ""
---

# Nền tảng chiến lược nhánh và tích hợp

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

## Chiến lược nhánh: khái niệm, mục đích và trách nhiệm tích hợp của nhóm

<!-- VIDEO_SECTION -->

### Scene 1 — Tình huống và bằng chứng

**Time:** `00:00–01:43`

**Visual:**

Màn hình chia đôi Git graph có hai nhánh phát triển song song; bên phải là lịch nhóm với ngày review và phát hành được đánh dấu.

Mọi commit graph sử dụng repo thử nghiệm; bảng quy trình là tình huống minh họa có nhãn, không được giả nhận là một PR live.

**Script:**

Giả sử hai kỹ sư đều thêm chức năng vào cùng repository. Git cho phép mỗi người tạo branch rất nhanh, nhưng không quyết định lúc nào họ đưa thay đổi vào nhánh chung, ai xem xét hay lịch phát hành có được chờ không. Đó chính là lý do có branching strategy: một thỏa thuận nhóm về nơi tích hợp, kích thước công việc và điều kiện chấp nhận. Hãy nhìn hai mũi tên trên đồ thị: nếu không có điểm gặp nhau, bản chạy chung sẽ không có những thay đổi mà mọi người tưởng đã xong.

Chúng ta sẽ lần lượt nhìn tuổi nhánh và nhịp tích hợp, so ba mô hình Trunk-Based, GitHub Flow, Git Flow, rồi quyết định hình dạng lịch sử, hướng phát hành/hotfix, quy tắc review và cách đo hiệu quả. Mỗi bước đều quay lại tình huống làm tròn và hoàn tiền của nhóm thanh toán.

**Purpose:**

Định nghĩa chiến lược như quy tắc phối hợp dựa trên đồ thị và nhịp nhóm.

## Cơ chế nhánh Git so với chính sách cộng tác do nhóm lựa chọn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:43–01:57`

**Visual:**

Giữ hai nhánh đang phân kỳ từ đồ thị chiến lược; mở terminal `git branch -vv` bên trái, bảng reviewer/merge policy bên phải, đánh dấu sự khác nhau giữa ref và quy tắc.

**Script:**

Hai luồng sửa mã đang song song. Git cho thấy lịch sử nào, còn điều kiện tích hợp nào đội phải tự quy định?

**Purpose:**

Nối định nghĩa chiến lược với khả năng Git và quyền chấp nhận vốn thuộc các tầng riêng.

### Scene 1 — Tình huống và bằng chứng

**Time:** `01:57–03:40`

**Visual:**

Cửa sổ terminal đọc `git branch -vv` và `git log --graph --oneline --all`; bên cạnh là tài liệu nhóm yêu cầu PR và review trước merge.

Mọi commit graph sử dụng repo thử nghiệm; bảng quy trình là tình huống minh họa có nhãn, không được giả nhận là một PR live.

**Script:**

Trên terminal, Git biết nhánh nào trỏ commit nào; tạo nhánh hay merge là cơ chế. Nhưng hãy nhìn tài liệu đội: phải review trước khi cập nhật main, hạn chế nhánh tồn đọng, và ai chịu trách nhiệm khi kiểm tra thất bại. Git không tự sinh những nguyên tắc đó. Nếu ta gọi tên branch là trunk mà vẫn tích hợp mỗi quý, hành vi vận hành chưa trở thành Trunk-Based Development. Phân biệt câu hỏi 'Git làm được gì?' với 'nhóm cho phép khi nào?' nhé.

**Purpose:**

Phân biệt khả năng Git và quy ước nhóm bằng hai loại bằng chứng.

## Vấn đề làm việc song song, tích hợp muộn và xung đột

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:40–03:54`

**Visual:**

Giữ bảng Git vs Policy, đưa hai developer An/Bình vào hai đầu nhánh feature, kéo lịch từ một ngày tới 12 ngày và tô vùng config hai người cùng sửa.

**Script:**

Khi đã tách lệnh Git và quy tắc review, hãy quan sát hai thay đổi ngày càng xa nhau trước lúc tích hợp.

**Purpose:**

Biến sự khác nhau giữa công cụ và quy định thành rủi ro tích hợp trễ có thể nhìn trên graph.

### Scene 1 — Tình huống và bằng chứng

**Time:** `03:54–05:37`

**Visual:**

Hai commit graphs: feature/auth và feature/billing rẽ từ main, sau 12 commit đều sửa một tệp config; đánh dấu merge-base và conflict.

Mọi commit graph sử dụng repo thử nghiệm; bảng quy trình là tình huống minh họa có nhãn, không được giả nhận là một PR live.

**Script:**

Ở bản đồ thứ nhất, hai nhánh sống một ngày và chỉ vài dòng khác nhau. Ở bản thứ hai, mỗi nhánh kéo dài hàng tuần và cùng sửa config: merge-base đã xa, nên xung đột tiềm ẩn khó điều tra hơn. Hãy so `git log --graph` và diff giữa hai đầu nhánh trên repository giả lập. Chiến lược không xóa mọi conflict; nó giảm thời gian bất đồng bị che giấu và giúp reviewer phản hồi khi bản sửa vẫn còn nhỏ.

**Purpose:**

Cho thấy tuổi nhánh và phân kỳ kéo dài làm tăng công phối hợp.

## Nhánh chính, nhánh công việc và nhánh phát hành: vai trò và phạm vi

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:37–05:51`

**Visual:**

Từ hai nhánh feature xa main, tô nhánh đích main; mở thẻ `feature/checkout` và `release/1.4` với owner và thời hạn tồn tại khác nhau.

**Script:**

Đồ thị xung đột cho thấy cần phân vai nhánh: dòng nào nhận thay đổi, dòng nào phục vụ phát hành?

**Purpose:**

Làm rõ không phải nhánh nào tồn tại lâu đều có cùng trách nhiệm hoặc rủi ro như feature bị bỏ quên.

### Scene 1 — Tình huống và bằng chứng

**Time:** `05:51–07:34`

**Visual:**

Commit graph có nhãn `main`, `feature/checkout`, `release/1.4`, mỗi nhánh gắn chủ thể chịu trách nhiệm và thời điểm đóng.

Mọi commit graph sử dụng repo thử nghiệm; bảng quy trình là tình huống minh họa có nhãn, không được giả nhận là một PR live.

**Script:**

Ba cái tên trên hình không phải ba nhánh bắt buộc mọi nhóm phải tạo. Mainline là điểm tích hợp được chia sẻ. Work branch, nếu dùng, chứa thay đổi chưa đưa vào main và nên có vòng đời rõ. Release branch chỉ có ý nghĩa khi phải giữ một dòng ổn định để chuẩn bị hoặc vá bản đã phát hành. Không nhầm release branch với môi trường deployment và cũng không mặc định có develop: đó là lựa chọn của từng chiến lược.

**Purpose:**

Nhận diện vai trò nhánh không áp đặt một sơ đồ chung.

## Mã đã tích hợp và mã sẵn sàng phát hành: hai trạng thái khác nhau

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:34–07:48`

**Visual:**

Giữ graph main/release, đóng dấu `Integrated` tại commit X; chuyển sang bảng release checklist và tag v1.4 còn chưa xác minh deployed.

**Script:**

Sau khi phân vai nhánh, hãy so commit đã vào main với phiên bản thực sự được kiểm chứng để phát hành.

**Purpose:**

Tách thay đổi đã vào nguồn chung khỏi tiêu chuẩn được phát hành và kết quả triển khai thật.

### Scene 1 — Tình huống và bằng chứng

**Time:** `07:48–09:31`

**Visual:**

Một bảng hai cột: commit X đã vào main nhưng kiểm thử chưa xác nhận release; tag v1.4 đánh dấu phiên bản được duyệt.

Mọi commit graph sử dụng repo thử nghiệm; bảng quy trình là tình huống minh họa có nhãn, không được giả nhận là một PR live.

**Script:**

Một PR đã merge chứng tỏ thay đổi vào lịch sử chung, nhưng chưa chứng minh đã được phát hành. Trên bảng, trạng thái tích hợp và phát hành được đánh dấu riêng: commit X ở main, trong khi bản v1.4 chỉ chứa mốc đã qua kiểm tra và được phê duyệt. Nhóm delivery liên tục cần main đáng tin cậy để có thể phát hành nhanh, nhưng vẫn có cổng kiểm chứng hoặc feature flag. Đừng dùng nút Merge làm bằng chứng rằng production đã nhận tính năng.

**Purpose:**

Không đánh đồng tích hợp với phát hành hoặc bật tính năng.

## Những quyết định do nhóm sở hữu và ranh giới với Git/nền tảng/CI

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:31–09:45`

**Visual:**

Giữ thẻ Integrated/Releasable/Deployed, gắn lần lượt Git graph, hosted PR, CI report và owner recovery vào từng cột bên dưới.

**Script:**

Ta đã tách tích hợp khỏi sẵn sàng phát hành. Bây giờ phân rõ trách nhiệm Git, PR review, CI và nhóm.

**Purpose:**

Kết thúc phần nền tảng bằng trách nhiệm từng công cụ thay vì coi merge là quyết định toàn bộ vòng đời.

### Scene 1 — Tình huống và bằng chứng

**Time:** `09:45–11:28`

**Visual:**

Ba vùng cùng màn hình: Git CLI/graph, PR review của hosting, nhãn check CI; bảng quyết định với người chịu trách nhiệm.

Mọi commit graph sử dụng repo thử nghiệm; bảng quy trình là tình huống minh họa có nhãn, không được giả nhận là một PR live.

**Script:**

Để kết thúc, hãy chỉ chủ thể của từng quyết định. Git quản lý commit, refs và cách hợp nhất. GitHub, GitLab hay Bitbucket ghi reviewer và có cơ chế cấu hình bảo vệ. CI cung cấp kết quả kiểm tra; nhóm phải quyết định chất lượng tối thiểu, nhịp tích hợp và ai chữa main khi đỏ. Những vai trò này liên hệ chặt, nhưng không thuộc một API local giả lập. Sang chương sau, chúng ta đo tác động thật của nhánh sống dài hay ngắn.

**Purpose:**

Tổng hợp ranh giới chính sách và công cụ trước khi học cadence.
