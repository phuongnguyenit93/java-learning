---
video:
  url: ""
---

# Review, phản hồi và phê duyệt merge request

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

## Ngữ cảnh review: diffs, thảo luận và bình luận theo dòng

<!-- VIDEO_SECTION -->

### Scene 1 — Ngữ cảnh review: diffs, thảo luận và bình luận theo dòng

**Time:** `00:00–01:13`

**Visual:**

Dựng MR !57 với tab Overview, Changes, Discussions. Đặt Issue #42 nói phải reject -1 bên trái và code clamp -1 thành 0 bên phải, đặt ô checks chưa có kết quả thật.

**Script:**

Bình không nên bắt đầu review bằng đếm dòng thay đổi. Anh ấy cần biết Issue #42 yêu cầu điều gì: số âm phải bị từ chối. Trong Changes, cách clamp số âm thành 0 của An có thể khác với kết quả mong đợi dù code hợp lệ. Chúng ta dừng màn hình tại chỗ sai khác giữa requirement và diff, rồi chuyển sang discussion theo dòng để đặt câu hỏi có thể kiểm chứng. Check xanh giả định không giải quyết được mâu thuẫn nghiệp vụ này; video chưa chạy kiểm thử thật nên sẽ không vẽ kết quả pass.

**Purpose:**

Hướng dẫn thứ tự đọc MR và minh chứng nguy cơ review thiếu ngữ cảnh, không tạo test outcome giả.

## Reviewer, assignee và người đủ điều kiện phê duyệt

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:13–01:26`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "review theo yêu cầu nghiệp vụ trước diff". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Bình tìm được vấn đề trong diff; nhưng ai đang xử lý MR và ai được tính vào điều kiện duyệt?

**Purpose:**

Chuyển từ review theo yêu cầu nghiệp vụ trước diff sang câu hỏi reviewer không mặc nhiên là eligible approver theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Reviewer, assignee và người đủ điều kiện phê duyệt

**Time:** `01:26–02:36`

**Visual:**

Hiện trường Assignee: An, Reviewers: Bình, Approval rules: Chi. Mỗi nhãn nối một chức năng; thêm biểu tượng thẩm tra membership/tier cho phiếu tính theo rule.

**Script:**

Assignee thường điều phối một MR, reviewer là người được đề nghị xem thay đổi, còn eligible approver là người có quyền thỏa rule đang áp dụng. An có thể là assignee, Bình xem diff, Chi được yêu cầu phê duyệt bảo mật trên các gói hỗ trợ. Chỉ đưa Bình vào danh sách Reviewer không sinh ra approval và một Comment cũng không thay phiếu hợp lệ. Trước khi tuyên bố MR đã đủ review, hãy mở vùng Approvals và xem nguồn membership, phạm vi quy tắc và ai đã thực sự gửi quyết định.

**Purpose:**

Tách ba vai trò trong giao diện và khả năng một approval được tính hợp lệ.

## Nhận xét, đề xuất sửa và phê duyệt trong merge request

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:36–02:49`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "reviewer không mặc nhiên là eligible approver". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Khi đã biết ai review, ta phải đọc hành động họ gửi chứ không chỉ xem avatar.

**Purpose:**

Chuyển từ reviewer không mặc nhiên là eligible approver sang câu hỏi comment, suggestion, approve có chức năng riêng theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Nhận xét, đề xuất sửa và phê duyệt trong merge request

**Time:** `02:49–04:02`

**Visual:**

Phóng to một dòng pricing, lần lượt hiện Comment hỏi trường hợp âm, Suggestion chỉnh code và thẻ Approve; không biến Comment thành chặn merge mặc định.

**Script:**

Khi Bình nói 'trường hợp -1 phải trả lỗi', đó là góp ý cần làm rõ. Suggestion có thể đề xuất phần chỉnh cụ thể, còn Approve thể hiện một quyết định chấp thuận. Trong GitLab Free có các khả năng review và feedback, nhưng không nên giả định mọi cơ chế cưỡng chế nâng cao đều có mặt. Ngay cả khi có Approve, protected branch hay các yêu cầu khác vẫn có thể chặn merge. Hãy kiểm tra thread đã được xử lý, mã mới ra sao và trạng thái hợp nhất thực tế thay vì đếm số bình luận.

**Purpose:**

Cho người mới cách đọc các kiểu phản hồi và phân biệt quyết định review với merge eligibility.

## Request changes có thể chặn merge: điều kiện Premium/Ultimate

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:02–04:15`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "comment, suggestion, approve có chức năng riêng". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Một lời bình luận có thể diễn đạt không đồng ý; còn việc GitLab dùng trạng thái đó làm gate phụ thuộc tính năng.

**Purpose:**

Chuyển từ comment, suggestion, approve có chức năng riêng sang câu hỏi Request changes chặn merge là tính năng tier-gated theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Request changes có thể chặn merge: điều kiện Premium/Ultimate

**Time:** `04:15–05:27`

**Visual:**

Hai khung GitLab Free và Premium/Ultimate. Ở khung gói cao hiện Request changes có thể chặn theo tính năng; khung Free chỉ mô tả góp ý không vẽ khóa bắt buộc giả.

**Script:**

Nếu Bình muốn từ chối bản sửa hiện tại, anh ấy cần ghi rõ lỗi, ví dụ đầu vào và kết quả mong đợi. GitLab có trải nghiệm Request changes, nhưng khả năng dùng trạng thái này để chặn merge được giới hạn theo Premium hoặc Ultimate trong tài liệu hiện hành. Ta không được dạy rằng một người dùng Free luôn có cùng nút hay cùng hiệu lực. Trong storyboard, trạng thái nào khả dụng phải được kiểm tra trên instance thật. Khi An sửa xong, Bình cần đọc lại phiên bản mới trước khi thay đổi quyết định.

**Purpose:**

Đưa ranh giới tier vào đúng lúc giải thích Request changes, ngăn suy diễn gate ở Free.

## Approval rules, số lượng phê duyệt và điều kiện theo gói dịch vụ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:27–05:40`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "Request changes chặn merge là tính năng tier-gated". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Nếu Request changes có thể chặn, một loại gate khác là yêu cầu số lượng người thực sự được tính.

**Purpose:**

Chuyển từ Request changes chặn merge là tính năng tier-gated sang câu hỏi approval rules có điều kiện số lượng và eligibility theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Approval rules, số lượng phê duyệt và điều kiện theo gói dịch vụ

**Time:** `05:40–06:54`

**Visual:**

Phác Settings > Merge requests > Approval rules: Required count 0 và 2 ở hai thẻ; với 2 chỉ người thuộc danh sách/group đủ điều kiện mới được tính. Dán nhãn Premium/Ultimate.

**Script:**

Approval rule đặt số lượng và ai có thể được tính. Trên GitLab Premium hoặc Ultimate, người quản trị có thể dùng quy tắc ở project và MR theo các điều kiện cấu hình; giá trị 0 là optional, số lớn hơn 0 tạo yêu cầu. Ta thử tưởng tượng MR !57 có một Approve nhưng cần hai phiếu hợp lệ: đây là ví dụ logic, không phải trạng thái GitLab đã đọc được. Đặc biệt đừng suy chỉ từ quyền xem project mà người đó thuộc nhóm eligible. Cần kiểm tra danh sách và nguồn membership mà Docs quy định cho approver.

**Purpose:**

Chỉ rõ approval gate đếm phiếu theo rule, có giới hạn tier và membership đặc thù.

## Phạm vi Code Owners so với approval rules tổng quát

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:54–07:07`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "approval rules có điều kiện số lượng và eligibility". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Nhóm đã biết số phiếu hợp lệ; nhưng một vùng mã nhạy cảm có thể đòi đúng người phụ trách.

**Purpose:**

Chuyển từ approval rules có điều kiện số lượng và eligibility sang câu hỏi CODEOWNERS theo path khác rule toàn MR theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Phạm vi Code Owners so với approval rules tổng quát

**Time:** `07:07–08:19`

**Visual:**

Đặt file CODEOWNERS có `/pricing/ @company/payments-reviewers` cạnh MR !57 đổi `pricing/Calculator.java`; kế đó rule Code Owner approval ở protected target, chỉ bật khi được hỗ trợ.

**Script:**

CODEOWNERS giúp chỉ ra vùng mã thuộc trách nhiệm của nhóm nào. Khi MR sửa pricing, một reviewer chung có thể đồng ý nhưng chưa chắc thỏa code owner approval nếu nhánh đích đang yêu cầu trên gói phù hợp. Chỉ ghi tên một nhóm trong tệp không cấp cho họ quyền xem hoặc phiếu phê duyệt hợp lệ. Approval rule tổng quát có thể xét những người khác nữa, nên hai cơ chế có phạm vi riêng. Hãy kiểm tra đường dẫn tệp, danh sách owner, protected branch và membership trước khi nói MR đã đủ điều kiện.

**Purpose:**

Diễn đạt ownership theo đường dẫn và rule theo MR như hai lớp phải kiểm chứng độc lập.

## Xử lý góp ý, cập nhật merge request và yêu cầu đánh giá lại

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:19–08:32`

**Visual:**

Thu nhỏ bằng chứng của cảnh trước ở bên trái, đánh dấu điểm chốt "CODEOWNERS theo path khác rule toàn MR". Đưa câu hỏi của cảnh mới lên khung phải và kéo mũi tên nối hai phạm vi.

**Script:**

Khi người đúng quyền góp ý, tác giả cần cập nhật mã và dẫn họ quay lại bằng chứng mới.

**Purpose:**

Chuyển từ CODEOWNERS theo path khác rule toàn MR sang câu hỏi resolved discussion không thay re-review theo đúng mạch học thay vì tách thành danh sách khái niệm.

### Scene 1 — Xử lý góp ý, cập nhật merge request và yêu cầu đánh giá lại

**Time:** `08:32–09:46`

**Visual:**

Timeline Bình nêu số âm → An bổ sung test/diff → Bình xem commit mới → quyết định review; hiển thị thảo luận resolved tách khỏi Approvals. Không vẽ test kết quả xanh.

**Script:**

An trả lời rằng đã sửa thì Bình vẫn phải nhìn phiên bản mới. Diff của MR thay đổi khi thêm commit, và dự án có thể cấu hình reset approval cũ. Đóng một discussion chỉ nói nhóm đã xử lý trao đổi, không chứng minh phép tính tiền đúng. Trong ví dụ ta yêu cầu An nêu chính xác test số âm đã thêm và chỉ Bình cách tìm phần khác biệt. Bình kiểm tra lại mã và bằng chứng rồi mới gửi quyết định. Đó là quy trình có trách nhiệm, không phải dọn màn hình để nút Merge sáng lên.

**Purpose:**

Đóng chương bằng vòng phản hồi–sửa–đánh giá lại có dấu vết riêng.
