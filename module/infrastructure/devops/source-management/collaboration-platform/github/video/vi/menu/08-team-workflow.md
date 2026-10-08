---
video:
  url: ""
---

# Tổng hợp quy trình cộng tác GitHub

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

## Từ Issue đến đóng góp, review và merge đủ điều kiện

<!-- VIDEO_SECTION -->

### Scene 1 — Từ Issue đến đóng góp, review và merge đủ điều kiện

**Time:** `00:00–01:08`

**Visual:**

Kéo Issue #42 vào bảng việc, nhánh fix-rounding, PR #57 rồi ô main; chỉ tô mũi tên merge khi đã nêu điều kiện đủ. Nhãn trên cùng 'tình huống giả định, không phải ảnh kết quả thật'.

**Script:**

Ta quay lại lỗi làm tròn số tiền âm. Issue #42 mô tả đầu vào -1 và kết quả sai, An chuẩn bị bản sửa ở nhánh công việc hoặc fork phù hợp rồi mở PR #57 nhắm main. Trong PR, An dẫn lại Issue, giải thích cách sửa và đưa ví dụ trước sau. Bình đọc diff, đặt câu hỏi, có thể yêu cầu điều chỉnh. Chỉ khi quyền và những quy tắc áp dụng đều cho phép, thay đổi mới được merge. Hãy dừng ở từng mốc và chỉ ra loại bằng chứng GitHub cần thấy trước khi chuyển sang mốc sau.

**Purpose:**

Tổng hợp quy trình Issue→contribution→PR→review→merge bằng trạng thái có điều kiện.

## Chẩn đoán thiếu quyền hoặc chưa đáp ứng yêu cầu đánh giá

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:08–01:19`

**Visual:**

Giữ chuỗi Issue → branch/fork → PR review, tô nổi biểu tượng visibility và quyền Write trước thẻ reviewer.

**Script:**

Một PR có thể được đề xuất đúng nhưng bị kẹt trước phần kiểm tra mã. Ta tìm xem ai thiếu quyền nào.

**Purpose:**

Nhận diện lớp quyền truy cập là điểm nghẽn đầu tiên trong tình huống xuyên suốt.

### Scene 1 — Chẩn đoán thiếu quyền hoặc chưa đáp ứng yêu cầu đánh giá

**Time:** `01:19–02:21`

**Visual:**

Dựng cây từ trạng thái 'không thấy repo' sang 'không push head' và 'không thể yêu cầu reviewer'; minh họa quyền Read/Write/team và một ngả fork. Không bật quyền trong UI thật.

**Script:**

Nếu Bình không đọc được repository, vấn đề nằm ở visibility hoặc quyền truy cập, không phải ở phép tính trong PR. Nếu An đọc được nhưng không đẩy vào repository chung, An có thể cần quyền phù hợp hoặc một đường đóng góp qua fork. Còn nếu reviewer được ghi trong CODEOWNERS nhưng không đủ quyền, lời mời đó không bảo đảm approval hợp lệ. Hãy ghi người thao tác, bước bị từ chối và rule liên quan. Đừng cấp Admin cho tất cả chỉ để một PR đi tiếp.

**Purpose:**

Dạy phân biệt lỗi hiển thị, quyền đóng góp và quyền review bằng cây chẩn đoán.

## Chẩn đoán ruleset, branch protection và status checks khi merge bị chặn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:21–02:32`

**Visual:**

Giữ phép kiểm tra quyền actor/repository, bổ sung cổng branch protection, rulesets đang có hiệu lực và required status checks trước Merge.

**Script:**

Ngay cả khi đã có reviewer hợp lệ, PR vẫn có thể bị chặn do quy tắc bảo vệ nhánh.

**Purpose:**

Tách việc đã có reviewer phù hợp khỏi việc đáp ứng toàn bộ chính sách merge.

### Scene 1 — Chẩn đoán ruleset, branch protection và status checks khi merge bị chặn

**Time:** `02:32–03:35`

**Visual:**

Bảng chẩn đoán PR #57 với Draft?, review required?, code owner?, required status?, branch protection?, rulesets?; cột 'bằng chứng thực cần xem' thay vì tích xanh sẵn.

**Script:**

Ta giả sử Bình đã gửi Approve nhưng nút Merge vẫn không sẵn sàng. Trước tiên kiểm tra PR còn Draft không, nhánh đích là gì, branch protection rule nào khớp và ruleset nào đang thực thi. Tiếp đó so số phê duyệt hợp lệ, code owner và required status checks. Một branch protection rule có hiệu lực, trong khi nhiều ruleset có thể chồng lên nhau. Đây là quy trình tra nguyên nhân, không phải lời khuyên tắt rule. Nếu kết quả check chưa xuất hiện, phải tìm đúng nguồn được yêu cầu.

**Purpose:**

Tổng hợp các blocker và trình tự kiểm tra policy thay vì sửa cấu hình mù.

## Bằng chứng PR hợp nhất, Issue liên quan và GitHub Release

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:35–03:46`

**Visual:**

Làm mờ bảng điều kiện merge và mở bốn cột bằng chứng: PR merged, Issue state, Release và deployment.

**Script:**

Giả sử nhóm thực sự đã merge; ta còn phải tách chứng cứ mã, công việc và bản phát hành.

**Purpose:**

Chuẩn bị cách báo cáo hậu merge với từng kết quả có thể kiểm chứng riêng.

### Scene 1 — Bằng chứng PR hợp nhất, Issue liên quan và GitHub Release

**Time:** `03:46–04:53`

**Visual:**

Dựng ba cột 'PR Merged', 'Issue Closed?', 'Release Published?'. Chỉ cột đầu có kết quả theo giả định; hai cột kia gắn câu hỏi và điều kiện, thêm ô 'deployed?' chưa biết.

**Script:**

Một nhãn Merged chứng minh thay đổi PR đã được đưa vào nhánh đích theo cách GitHub ghi nhận. Nhưng Issue #42 chỉ có thể tự đóng theo cơ chế liên kết phù hợp; không nên khẳng định Closed khi chưa xem hồ sơ Issue. Release v1.4.0 lại là sự kiện công bố riêng với tag và notes, không tự xuất hiện chỉ vì PR được merge. Và tất cả những thông tin đó vẫn chưa cho ta biết môi trường production đã nhận bản sửa chưa. Hãy phân biệt đúng bốn trạng thái trước khi thông báo 'đã lên sản phẩm'.

**Purpose:**

Chốt bốn loại bằng chứng khác nhau: merged PR, issue, Release và deployment.

## Điểm bàn giao sang thao tác Git, chiến lược nhánh, quản lý phiên bản và CI/CD

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:53–05:04`

**Visual:**

Thu bốn cột bằng chứng vào các vùng Git, GitHub, chiến lược nhánh, CI/CD và versioning.

**Script:**

Từ một Issue đến thông tin phát hành, nhóm đã dùng nhiều lớp công cụ. Ta kết thúc bằng ranh giới học tập rõ ràng.

**Purpose:**

Bàn giao rõ phạm vi học tiếp để không coi dấu vết chấp nhận mã là bằng chứng triển khai.

### Scene 1 — Điểm bàn giao sang thao tác Git, chiến lược nhánh, quản lý phiên bản và CI/CD

**Time:** `05:04–06:12`

**Visual:**

Sơ đồ bốn vùng Git history, GitHub collaboration, Branching Strategy, CI/CD + Versioning. Chiếu từng thẻ Issue #42/PR #57/tag/Release vào vùng phù hợp, để pipeline run riêng.

**Script:**

Bây giờ hãy kể lại câu chuyện như bốn trách nhiệm khác nhau. Git lưu commit, nhánh và lịch sử. GitHub tổ chức repository, đề xuất, review, quyền và điều kiện chấp nhận. Branching Strategy quyết định nhịp tích hợp và tuổi thọ nhánh theo nhu cầu nhóm. CI/CD và chính sách phiên bản mới giải thích cách kiểm tra, build, công bố hay triển khai phần mềm. Nếu PR #57 đã merge, đó là một mốc nguồn được chấp nhận; chưa phải kết thúc toàn bộ vòng đời sản phẩm. Bài học cuối là biết mình đang nhìn bằng chứng thuộc lớp nào.

**Purpose:**

Tạo kết thúc có ích, chỉ đường học tiếp bằng lời nói mà không phát minh navigation hay API.
