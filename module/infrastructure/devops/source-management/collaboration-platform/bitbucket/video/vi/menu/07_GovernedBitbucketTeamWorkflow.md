---
video:
  url: ""
---

# Quy trình cộng tác Bitbucket có kiểm soát

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

## Từ quyền truy cập đến thay đổi được hợp nhất

<!-- VIDEO_SECTION -->

### Scene 1 — Từ quyền truy cập đến thay đổi được hợp nhất

**Time:** `00:00–01:35`

**Visual:**

Toàn màn hình timeline `Access → Git change → PR → Review/tasks → Checks/rights → Merge → Accepted main` với một thẻ bằng chứng cho mỗi mốc, gắn nhãn `BILL-142`.

**Script:**

Trong Orchid, Mai cấp quyền phù hợp, An tạo thay đổi thuế trong Git và mở PR vào `invoice-api/main`, Bình kiểm tra hóa đơn số lẻ rồi tạo task cần hoàn thành. An cập nhật mã, task được kiểm chứng và người có quyền mới merge theo các quy tắc đang áp dụng. Chúng ta không xem commit như approval, không xem PR Open như nguồn đã chấp nhận, và không xem merge như bằng chứng bản mới đã lên production. Hãy dừng ở mỗi mốc và nói được đối tượng nào trong Bitbucket chứng minh nó đã xảy ra.

**Purpose:**

Ghép chuỗi bằng chứng end-to-end từ quyền truy cập tới trạng thái accepted source.



## Điều kiện sẵn sàng hợp nhất của pull request

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:35–01:47`

**Visual:**

Giữ timeline `Access→PR→Review→Merge`; phóng bảng readiness gồm target, merge actor, approvals, open tasks và check enforcement.

**Script:**

Chúng ta đã theo dõi thay đổi từ lúc có quyền truy cập đến đề xuất tích hợp. PR cần đáp ứng điều gì trước khi sẵn sàng hợp nhất?

**Purpose:**

Chuyển từ hành trình cộng tác sang danh sách điều kiện phải xác thực trước merge.

### Scene 2 — Điều kiện sẵn sàng hợp nhất của pull request

**Time:** `01:47–03:22`

**Visual:**

PR `BILL-142` với checklist `Correct target`, `Merge permission`, `Approvals`, `Unresolved tasks`, `Changes requested`, `Effective merge checks`; thêm nhãn Standard warning và Premium blocking.

**Script:**

Một PR sẵn sàng merge không chỉ vì An nói đã xong hay Bình nhấn Approve. Mai cần đối chiếu nhánh đích, quyền người thao tác, task còn mở, yêu cầu sửa từ reviewer và trạng thái những check hiện có. Với Free/Standard, check thông thường chưa đạt có thể là lời cảnh báo; với Premium, chế độ ngăn merge khi unresolved checks phải được bật và áp dụng. Chính sách nhóm vẫn phải quy định con người có bỏ qua cảnh báo hay không. Khi quay, mỗi ô checklist cần dẫn đến thông tin nhìn thấy được thay vì giả định giao diện luôn tự chặn.

**Purpose:**

Tạo rubric kiểm tra readiness dựa vào quyền, review và giới hạn thực thi theo plan.



## Truy tìm nguyên nhân pull request bị đình lại

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:34`

**Visual:**

Từ readiness có check chưa đạt, mở bảng `Actor / Target / Exact error / Matching restrictions` cạnh PR BILL-142.

**Script:**

Nếu PR đứng yên, hãy chẩn đoán dựa vào màn hình nào thay vì đoán?

**Purpose:**

Chuyển trạng thái pending thành quy trình tìm nguyên nhân có căn cứ, không đoán Bitbucket bị lỗi.

### Scene 3 — Truy tìm nguyên nhân pull request bị đình lại

**Time:** `03:34–05:09`

**Visual:**

Hiện ví dụ An bấm Merge nhưng thấy từ chối; bảng theo thứ tự `Actor`, `Destination main`, `Error message`, `Effective permission`, `Matched restriction`, `Outstanding check/task`.

**Script:**

Giả sử Bình đã approve nhưng An vẫn không merge được. Thay vì kết luận Bitbucket lỗi, Mai ghi người thao tác và thông báo thật rồi kiểm tra quyền repository/project, branch restrictions cho `main`, các merge checks và tác vụ chưa giải quyết. Nếu check chỉ cảnh báo nhưng An bị từ chối, nguyên nhân có thể là một restriction khác chứ không phải plan. Hãy tách từng loại bằng chứng, không cấp Admin cho tất cả hoặc tắt rules. Một ca chẩn đoán tốt giải thích tại sao thao tác thất bại theo cấu hình cụ thể mà vẫn giữ nguyên nguyên tắc bảo vệ nguồn.

**Purpose:**

Dạy cách phân loại blocker theo actor, rule và state thay vì sửa quyền tùy tiện.



## Dấu vết của một thay đổi được đánh giá và chấp nhận

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:09–05:21`

**Visual:**

Giữ bảng denied/error; mở hồ sơ có Jira key, PR review decision, resolved task, target merged revision; để thiếu bằng chứng là Pending.

**Script:**

Sau khi merge thành công, ta phải lưu những dấu vết nào để khẳng định thay đổi được chấp nhận?

**Purpose:**

Chuyển từ khắc phục merge sang checklist truy vết thay đổi thật sự được tiếp nhận vào nguồn chung.

### Scene 4 — Dấu vết của một thay đổi được đánh giá và chấp nhận

**Time:** `05:21–06:56`

**Visual:**

Bảng `Evidence pack` gồm `Jira BILL-142`, `PR diff/source/target`, `Binh review+tasks`, `Check mode`, `Merged destination revision`; gắn dấu Pending cho mục thiếu.

**Script:**

Một thông báo Done trong Jira nói về trạng thái công việc, không thay thế mốc Git đã merge. PR cho thấy nguồn và đích, comment của Bình ghi lại tranh luận về rounding, task cho biết việc đã xử lý đến đâu, còn check status thể hiện điều kiện có hiệu lực tại thời điểm đó. Cuối cùng phải xem nhánh đích sau merge để xác định nguồn đã thay đổi thật. Nếu chỉ có PR Open và một approval thì chưa đủ kết luận. Bộ bằng chứng này giúp đồng đội tái dựng quyết định mà không dựa vào tin nhắn chat hay ảnh chụp cũ.

**Purpose:**

Tạo bộ bằng chứng acceptance kiểm chứng được và không suy ra kết quả chưa xảy ra.



## Ranh giới với Git, chiến lược nhánh và Bitbucket Pipelines

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:56–07:08`

**Visual:**

Giữ accepted target revision trên bảng; đưa bốn làn Git mechanics, Strategy, Bitbucket Cloud, Pipelines, rồi vạch ranh giới deployment.

**Script:**

Sau khi nhận nguồn, những việc nào thuộc Git, team strategy hay pipeline chứ không thuộc video Bitbucket?

**Purpose:**

Phân định trách nhiệm nền tảng host với cơ chế Git, chính sách nhánh và việc triển khai sau merge.

### Scene 5 — Ranh giới với Git, chiến lược nhánh và Bitbucket Pipelines

**Time:** `07:08–08:43`

**Visual:**

Ba làn `Git mechanics`, `Branching strategy`, `Bitbucket collaboration` và làn cuối `Pipelines/Delivery`; di chuyển ví dụ BILL-142 qua các ranh giới, nhấn `Accepted source ≠ Deployed`.

**Script:**

Bitbucket Cloud giúp nhóm lưu nguồn, quản trị truy cập, đề xuất, review và tiếp nhận thay đổi. Cách Git ghi object, commit hay merge thuộc module Git. Quyết định nhánh sống bao lâu và nên dùng chiến lược nào thuộc branching-strategy; tổ chức một hay nhiều repo thuộc monorepo-polyrepo. Build và deployment qua Bitbucket Pipelines hoặc công cụ khác thuộc CI/CD, không tự xảy ra chỉ vì PR đã merge. Kết thúc chuỗi Orchid, hãy hỏi: mã nào đã ghi nhận, PR nào được review, ai có quyền, check nào thực sự chặn hay cảnh báo, và nhánh đích có thay đổi chưa. Đó là ranh giới của một thay đổi nguồn được quản trị.

**Purpose:**

Tổng kết ranh giới kỹ thuật giữa collaboration, Git, strategy và delivery; tránh gợi ý STEP5 HTTP giả.
