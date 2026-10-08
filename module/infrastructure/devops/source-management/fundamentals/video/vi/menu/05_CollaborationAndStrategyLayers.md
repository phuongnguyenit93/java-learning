---
video:
  url: ""
---

# Công cụ, cộng tác và chiến lược nhóm

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

## Phân biệt hệ thống kiểm soát phiên bản và nền tảng lưu trữ cộng tác

<!-- VIDEO_SECTION -->

### Scene 1 — Phân biệt hệ thống kiểm soát phiên bản và nền tảng lưu trữ cộng tác

**Time:** `00:00–01:15`

**Visual:**

Hai tầng tách biệt: bên dưới là đồ thị Git A–B–C và nút `fetch/push`; bên trên là bảng `PR / reviewer / policy` của nơi lưu trữ. Đường nét đứt nối commit với PR.

**Script:**

Git giải quyết việc ghi và trao đổi lịch sử commit. Một nền tảng như GitHub, GitLab, Bitbucket hay Azure DevOps bổ sung tài khoản, repository hosting, giao diện đề xuất sửa đổi và chính sách quyền. Hãy nhìn hai tầng: khi An tạo commit C, đó là bằng chứng kỹ thuật trong Git. Khi An mở pull request và Bình để lại ý kiến, đó là bản ghi cộng tác ở hệ thống hosting. Không nên kỳ vọng lệnh `git log` cho biết ai đã phê duyệt, cũng không nên nghĩ nền tảng hosting thay thế cách Git lưu object. Mỗi tầng trả lời một loại câu hỏi.

**Purpose:**

Tách rõ Git mechanics và lớp hosting, chuẩn bị đọc bản ghi review thật.



## Mục đích đề xuất và đánh giá thay đổi trước khi tiếp nhận

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:15–01:27`

**Visual:**

Giữ dưới cùng đồ thị commit A–B–C; mở lớp trên với PR `Tax 8%→10%`, vùng diff, Bình hỏi về rounding và ô review vẫn chưa xong.

**Script:**

Nếu lịch sử commit không tự nói ai đã xem, nhóm đề xuất và đánh giá như thế nào?

**Purpose:**

Nối cơ chế Git ghi commit với hồ sơ đề xuất và đánh giá cộng tác do host quản lý.

### Scene 2 — Mục đích đề xuất và đánh giá thay đổi trước khi tiếp nhận

**Time:** `01:27–02:42`

**Visual:**

Bảng PR ví dụ `Tax 8%→10%`: cột `Mục đích`, `Diff`, `Câu hỏi reviewer`, `Trạng thái`; xuất hiện lời Bình hỏi về rounding, sau đó An cập nhật đề xuất.

**Script:**

Một đề xuất thay đổi giúp tác giả giải thích vì sao phải sửa, người khác xem diff và đặt câu hỏi trước khi nhận thay đổi vào nguồn chung. Trong ví dụ, Bình nhận thấy mức thuế đã đổi nhưng bước làm tròn chưa được xem xét. Bình hỏi rõ tình huống hóa đơn lẻ; An bổ sung kiểm thử hoặc sửa tiếp rồi mời xem lại. Điều quan trọng không phải có một nút Approve để bấm, mà là người đánh giá đủ ngữ cảnh và có bằng chứng phản hồi. Commit vẫn tồn tại ngay cả khi đề xuất còn chờ duyệt.

**Purpose:**

Minh họa giá trị review qua phát hiện vấn đề cụ thể, không đồng nhất proposed và accepted.



## Quyền truy cập và điều kiện tiếp nhận mã nguồn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:42–02:54`

**Visual:**

Giữ màn PR và nhận xét của Bình; đưa bảng Read/Contribute/Approve/Merge vào cạnh target `main`, khoá cột Merge đến khi policy được xét.

**Script:**

Ngay cả khi nội dung tốt, ai thực sự có quyền xem, ghi và tiếp nhận thay đổi?

**Purpose:**

Chứng minh nhận xét kỹ thuật đúng không tự cấp quyền merge hay thỏa điều kiện bảo vệ nhánh.

### Scene 3 — Quyền truy cập và điều kiện tiếp nhận mã nguồn

**Time:** `02:54–04:09`

**Visual:**

Vẽ ma trận quyền `Read / Contribute / Approve / Merge`; đặt An/Bình/maintainer vào các hàng; tô sáng trường hợp có quyền push branch nhưng chưa quyền hợp nhất vào main.

**Script:**

Đội nhóm thường cần phân biệt quyền đọc repository, quyền cập nhật một nhánh, quyền tham gia đánh giá và quyền hoàn tất tích hợp. Một người có thể được phép đề xuất nhưng không được trực tiếp đổi nhánh được bảo vệ. Tùy dịch vụ và cấu hình, số phiếu, checks hoặc quyền bắt buộc khác có thể chặn việc tiếp nhận. Chúng ta không tự đặt ra một quy tắc chung cho mọi nền tảng; ở đây cần nhận ra câu hỏi phải kiểm tra: ai được làm gì, đối với nguồn nào, và điều kiện nào phải hoàn tất. Dữ liệu quyền nên xem trên hosting thật.

**Purpose:**

Dạy phân biệt khả năng đề xuất, phê duyệt và merge dưới quyền hiệu lực riêng.



## Chính sách nhánh của nhóm so với cơ chế nhánh Git

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:09–04:21`

**Visual:**

Từ thẻ Merge rights và target main, mở đồng hồ hai nhánh `short-lived topic` và `release branch`; giữ lệnh Git merge làm cơ chế chung không đổi.

**Script:**

Quyền và PR nói ai được tích hợp; vậy nhóm quyết định làm việc trên nhánh bao lâu?

**Purpose:**

Tách chính sách tuổi nhánh/nhịp tích hợp khỏi lệnh Git thực hiện thao tác merge.

### Scene 4 — Chính sách nhánh của nhóm so với cơ chế nhánh Git

**Time:** `04:21–05:36`

**Visual:**

Giữ graph A–B→topic; thêm đồng hồ và ba thẻ `Trunk-based`, `GitHub Flow`, `Git Flow`, sau đó che đi các lệnh Git để nổi bật câu hỏi về nhịp tích hợp.

**Script:**

Tạo nhánh, merge hay rebase là các cơ chế Git. Nhưng nhóm còn phải chọn lúc nào tạo nhánh, nhánh nên sống bao lâu, có cần nhánh release riêng không và ai được cập nhật nhánh chung. Đó là chiến lược nhánh chứ không phải cú pháp lệnh. Cùng một lệnh `git merge` có thể xuất hiện trong những quy trình tổ chức rất khác nhau. Video nền tảng chỉ đặt mối liên hệ; tiêu chí chọn trunk-based, GitHub Flow hay Git Flow sẽ được giải thích ở module branching-strategy, dựa trên nhịp tích hợp và nhu cầu phát hành của nhóm.

**Purpose:**

Ngăn nhầm Git branch operations với team branching policy.



## Quyết định tổ chức một hay nhiều kho mã

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:36–05:48`

**Visual:**

Thu nhỏ đồng hồ branch lifetime; vẽ một Git root chứa invoice-api/checkout-web so với hai Git roots, thêm đường dependency contract nối hai component.

**Script:**

Ngoài chiến lược nhánh, cả hình dạng kho mã cũng là một quyết định của nhóm.

**Purpose:**

Chuyển từ policy trong một repository sang quyết định số repository theo ownership và cross-component coordination.

### Scene 5 — Quyết định tổ chức một hay nhiều kho mã

**Time:** `05:48–07:03`

**Visual:**

Sơ đồ bên trái một repository chứa `invoice-api` và `checkout-web`; bên phải hai repository riêng. Đánh dấu một thay đổi contract yêu cầu một PR hoặc hai PR.

**Script:**

Một nhóm có thể tổ chức nhiều thành phần trong cùng repository hoặc tách thành nhiều repository. Với sửa đổi contract thanh toán, một kho chung có thể biểu diễn thay đổi phối hợp trong một lịch sử, còn hai kho riêng có thể cần hai đề xuất cùng kiểm tra tương thích. Tuy nhiên không thể chỉ nhìn số kho để kết luận tốc độ build hay chất lượng release. Quyết định này phụ thuộc ranh giới sở hữu, bảo mật, công cụ và phối hợp thay đổi. Module monorepo-polyrepo sẽ đi sâu vào đánh đổi ấy. Còn bài học hiện tại là quản lý nguồn gồm nhiều tầng quyết định, không chỉ chọn phần mềm Git.

**Purpose:**

Liên kết repository topology với bài toán thay đổi xuyên thành phần mà không mở rộng curriculum.
