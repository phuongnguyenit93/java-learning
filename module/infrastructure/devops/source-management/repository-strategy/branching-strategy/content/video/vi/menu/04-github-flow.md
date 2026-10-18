---
video:
  url: ""
---

# GitHub Flow như một chiến lược cộng tác gọn nhẹ

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

## GitHub Flow: nhánh thay đổi và vòng phản hồi qua pull request

<!-- VIDEO_SECTION -->

### Scene 1 — Xem vòng phản hồi PR

**Time:** `00:00–01:45`

**Visual:**

Sơ đồ default `main` → branch `feature/checkout-tax` → PR → review → merge → delete branch, giữ một issue làm bối cảnh.

Ảnh PR lấy từ dự án demo hoặc tài liệu GitHub có dẫn nguồn; số liệu tuổi nhánh chỉ là ví dụ giả định có nhãn.

**Script:**

GitHub Flow tập trung vào một nhánh mặc định đáng tin và một nhánh cho thay đổi đang đề xuất. Người viết đẩy commit, tạo pull request để thảo luận rồi hợp nhất khi điều kiện được đáp ứng. Hãy nhìn vòng đời, đặc biệt mũi tên quay về default branch chứ không phải develop lâu dài. Đây là một quy trình hợp tác dễ hiểu, có thể dùng ở GitHub và các nền tảng tương đương, nhưng bản thân sơ đồ chưa nói nhánh sẽ sống đúng hai ngày hay hai tháng.

**Purpose:**

Giới thiệu PR-driven workflow nhưng không gắn thời hạn nhánh giả.

## Chu trình nhánh công việc, pull request, review và hợp nhất

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:45–02:00`

**Visual:**

Giữ PR branch→main đang mở; phóng tab Files changed với tax diff và thẻ Bình đang được request review, đánh dấu base main chưa merge.

**Script:**

Đi theo một thay đổi từ nhánh mặc định, qua nhánh công việc, pull request, review rồi merge.

**Purpose:**

Nối mô hình PR vòng đời với mục tiêu và dấu vết thực tế của đề xuất vào default branch.

### Scene 1 — Xem vòng phản hồi PR

**Time:** `02:00–03:45`

**Visual:**

Terminal demo `git switch -c feature/tax`, `git log --graph --oneline --all`, bên cạnh là màn PR Create/Files/Reviews theo GitHub Docs.

Ảnh PR lấy từ dự án demo hoặc tài liệu GitHub có dẫn nguồn; số liệu tuổi nhánh chỉ là ví dụ giả định có nhãn.

**Script:**

Trong repository thử nghiệm, tách feature/tax từ main, tạo một commit nhỏ và quan sát đồ thị. Trên ảnh PR có nguồn và đích rõ ràng, tác giả mô tả lý do, người review đọc diff, rồi nhóm quyết định merge. Sau khi đã merge và xác minh nhánh đích, branch công việc có thể xóa để tránh nhầm với việc đang chạy. Git tạo commit và ref; PR là bản ghi đánh giá ở hosting. Không gọi tên một API localhost để giả phản hồi reviewer.

**Purpose:**

Trình diễn source/target và ranh giới Git mechanics với PR.

## Giá trị trao đổi sớm, đề xuất nháp và phản hồi review

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:45–04:00`

**Visual:**

Giữ tax diff và comment của Bình, đổi nhãn PR từ Draft sang Ready sau bản test bổ sung; kéo thẻ thời điểm review sát commit mới.

**Script:**

Pull request giúp trao đổi. Xem draft PR mang lại phản hồi sớm khi phần thay đổi còn dễ điều chỉnh.

**Purpose:**

Cho thấy phản hồi sớm và cập nhật bản sửa không tự tạo approval cho PR hiện tại.

### Scene 1 — Xem vòng phản hồi PR

**Time:** `04:00–05:45`

**Visual:**

Hai trạng thái PR Draft/Ready của một cùng diff; comment review trên dòng code và một commit sửa phản hồi, có timestamp riêng.

Ảnh PR lấy từ dự án demo hoặc tài liệu GitHub có dẫn nguồn; số liệu tuổi nhánh chỉ là ví dụ giả định có nhãn.

**Script:**

Mở Draft PR khi còn thảo luận cách xử lý số âm, đánh dấu câu hỏi lên dòng code và cho reviewer bình luận sớm. Khi tác giả cập nhật commit và test, họ chuyển sang Ready để xin đánh giá cuối. Bảng thời gian cho thấy phản hồi có trước khi nhánh phình to. Tuy nhiên, không nên xem nhãn Draft hay Approved như giấy phép merge; branch protection và checks do từng hosting cấu hình sẽ quyết định hành động cuối có khả dụng hay không.

**Purpose:**

Chỉ ra lợi ích review sớm nhưng không suy từ Draft ra merge-readiness.

## GitHub Flow không tự bảo đảm nhánh sống ngắn như Trunk-Based Development

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:45–06:00`

**Visual:**

Từ Draft/Ready mở hai PR có cùng quy trình: A hợp nhất sau một ngày, B chờ ba tuần; so khoảng cách merge-base tới main.

**Script:**

Draft giúp cộng tác, nhưng nhánh có thể tồn tại bao lâu? So một PR một ngày với PR ba tuần.

**Purpose:**

Phân biệt quy trình GitHub Flow có review với kỷ luật hội tụ rất nhanh của TBD.

### Scene 1 — Xem vòng phản hồi PR

**Time:** `06:00–07:45`

**Visual:**

Hai PR đều đúng bước GitHub Flow: PR A merge sau một ngày, PR B mở ba tuần, hiển thị tuổi nhánh và main divergence.

Ảnh PR lấy từ dự án demo hoặc tài liệu GitHub có dẫn nguồn; số liệu tuổi nhánh chỉ là ví dụ giả định có nhãn.

**Script:**

Đây là phép so sánh quan trọng: cả PR A và B đều có nhánh, review và merge vào main. Nhưng A tồn tại một ngày, còn B tồn tại ba tuần và xa merge-base. GitHub Flow mô tả hình thức cộng tác; nó không tự ép nhánh rất ngắn hay tích hợp liên tục như kỷ luật TBD. Một nhóm có thể áp cả hai nguyên tắc nếu tự đặt giới hạn tuổi nhánh và chia công việc, nhưng không được coi hai tên là từ đồng nghĩa.

**Purpose:**

Phân biệt GitHub Flow với nhịp tích hợp TBD dựa trên bằng chứng.

## GitHub Flow ở cấp chiến lược so với cấu hình PR và ruleset của nền tảng

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:45–08:00`

**Visual:**

Giữ PR A/B đang được so tuổi nhánh; chuyển highlight sang hai cột team turnaround policy và host rulesets/checks với người chịu trách nhiệm riêng.

**Script:**

Tuổi nhánh là kỳ vọng của đội. Hãy xem host thực thi được yêu cầu duyệt, checks và quyền truy cập nào.

**Purpose:**

Phân biệt cam kết vận hành về tuổi PR với quyền và điều kiện merge có thể cấu hình trên host.

### Scene 1 — Xem vòng phản hồi PR

**Time:** `08:00–09:45`

**Visual:**

Bảng hai cột: Team policy 'review before merge within two days' và GitHub settings Rulesets/required checks/permissions; mỗi loại có chủ.

Ảnh PR lấy từ dự án demo hoặc tài liệu GitHub có dẫn nguồn; số liệu tuổi nhánh chỉ là ví dụ giả định có nhãn.

**Script:**

Ở cột trái là lựa chọn nhóm: khi nào tạo nhánh, ai cần review, thời gian kỳ vọng và ai chăm sóc main. Cột phải là các công cụ Github giúp thực thi một phần: pull request, review requirement, ruleset hoặc branch protection. Không phải mọi nguyên tắc về nhịp đều có toggle sẵn trong UI; cần đo tuổi PR và thực hiện chính sách bằng vận hành. Học cấu hình GitHub cụ thể thuộc module Collaboration Platform, còn ở đây ta quyết định mục tiêu và bằng chứng chấp hành.

**Purpose:**

Phân biệt mục tiêu tổ chức và công cụ thực thi của hosting.
