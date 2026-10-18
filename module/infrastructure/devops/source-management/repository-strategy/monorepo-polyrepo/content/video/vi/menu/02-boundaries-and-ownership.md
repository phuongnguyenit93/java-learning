---
video:
  url: ""
---

# Phân chia ranh giới và trách nhiệm sở hữu mã nguồn

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

## Code ownership: khái niệm, mục đích và trách nhiệm

<!-- VIDEO_SECTION -->

### Scene 1 — Code ownership: khái niệm, mục đích và trách nhiệm

**Time:** `00:00–01:04`

**Visual:**

Tô vùng `/libs/contracts`; ghi Primary Platform, Backup Checkout, Consumers Web/API. Tách biểu tượng review và biểu tượng quyền đọc.

**Script:**

Code ownership là lời cam kết ai hiểu vùng mã và ai phải được gọi khi thay đổi gặp sự cố. Với thư viện Contracts, nhóm Platform chịu trách nhiệm chính, nhưng Checkout cũng cần người dự phòng và Web là consumer quan trọng. Một owner không nhất thiết là người duy nhất có thể sửa hoặc đọc mã. Nếu tên CODEOWNERS có mà không ai trả lời PR, vùng đó vẫn có lỗ hổng quản trị. Hãy kiểm tra đầu mối, quyền review thực tế và cách escalation khi người chính vắng mặt.

**Purpose:**

Người học phải nhận biết được Owner là người chịu trách nhiệm, không phải chủ sở hữu Git duy nhất từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Trách nhiệm sở hữu theo thư mục hoặc component trong monorepo

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:04–01:28`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Owner là người chịu trách nhiệm, không phải chủ sở hữu Git duy nhất"; mở khung phải cho "Owner theo path giúp duyệt nhiều vùng trong một repo", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Owner là người chịu trách nhiệm, không phải chủ sở hữu Git duy nhất. Nhưng chưa nên kết luận khi chưa kiểm tra Owner theo path giúp duyệt nhiều vùng trong một repo. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Owner theo path giúp duyệt nhiều vùng trong một repo, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Trách nhiệm sở hữu theo thư mục hoặc component trong monorepo

**Time:** `01:28–02:28`

**Visual:**

Mở repo `/apps/web/`, `/libs/auth/`, `/services/api/`; gắn Web/Security/API. Một PR chạm cả hai vùng hiện hai nhãn cần review.

**Script:**

Trong monorepo, nhóm có thể định nghĩa người phụ trách theo folder hoặc component. Khi PR sửa cả `/apps/web` và `/libs/auth`, reviewer Web không tự đủ chuyên môn xét thay đổi bảo mật. Ta biểu diễn hai owner độc lập và cả quyền xem diff chung. Các tính năng như CODEOWNERS có thể gợi ý người review, còn việc bắt buộc phê duyệt phải qua rules cụ thể của host. Đừng giả định tạo file CODEOWNERS đồng nghĩa đã cấu hình quyền hay bảo vệ nhánh.

**Purpose:**

Người học phải nhận biết được Owner theo path giúp duyệt nhiều vùng trong một repo từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Trách nhiệm sở hữu theo từng repository trong polyrepo

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:28–02:51`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Owner theo path giúp duyệt nhiều vùng trong một repo"; mở khung phải cho "Owner theo repo dễ định vị nhưng interface vẫn chung", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Owner theo path giúp duyệt nhiều vùng trong một repo. Nhưng chưa nên kết luận khi chưa kiểm tra Owner theo repo dễ định vị nhưng interface vẫn chung. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Owner theo repo dễ định vị nhưng interface vẫn chung, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Trách nhiệm sở hữu theo từng repository trong polyrepo

**Time:** `02:51–03:54`

**Visual:**

Hai kho `checkout-api.git` và `identity.git`, mỗi thẻ có owner/reviewer; mũi tên API contract đi qua biên, đặt joint responsibility.

**Script:**

Trong polyrepo, ranh giới repo thường làm đầu mối bảo trì rõ hơn. Team Checkout chăm checkout-api và team Identity chăm identity. Nhưng khi Checkout dùng auth contract từ Identity, cả hai phải thống nhất thay đổi giao diện và thời điểm nâng version. Không có lý do để reviewer Checkout bỏ qua contract chỉ vì lịch sử mã nằm ở repo khác. Trên sơ đồ, hãy gắn owner vào từng repo rồi bổ sung người chịu trách nhiệm cho đường nối hợp đồng và người nhận thông báo breaking change.

**Purpose:**

Người học phải nhận biết được Owner theo repo dễ định vị nhưng interface vẫn chung từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Khả năng khám phá, tìm kiếm và tái sử dụng mã theo topology

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:54–04:18`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Owner theo repo dễ định vị nhưng interface vẫn chung"; mở khung phải cho "Tìm được nơi dùng API khác tự động tái sử dụng an toàn", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Owner theo repo dễ định vị nhưng interface vẫn chung. Nhưng chưa nên kết luận khi chưa kiểm tra Tìm được nơi dùng API khác tự động tái sử dụng an toàn. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Tìm được nơi dùng API khác tự động tái sử dụng an toàn, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Khả năng khám phá, tìm kiếm và tái sử dụng mã theo topology

**Time:** `04:18–05:24`

**Visual:**

Quay cảnh search `AuthClient` trong chung root hiện API/Web; ở polyrepo hiện repo catalog/index; so sánh kết quả và test consumer.

**Script:**

Một thư viện dùng chung chỉ hữu ích khi lập trình viên tìm ra nó và hiểu những consumer đã có. Trong monorepo, tìm `AuthClient` có thể cho thấy nơi định nghĩa và ví dụ dùng ở Web lẫn API. Polyrepo cũng làm được nếu có catalog hoặc cross-repo search đáng tin cậy; chỉ có nhiều repo không bắt buộc duplicate code. Hãy đo thời gian tìm người phụ trách và đoạn ví dụ đúng, rồi xét khả năng chạy contract test. Khả năng nhìn thấy mã chưa bảo đảm phiên bản của mỗi consumer tương thích.

**Purpose:**

Người học phải nhận biết được Tìm được nơi dùng API khác tự động tái sử dụng an toàn từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Phân biệt trách nhiệm sở hữu với quyền truy cập mã nguồn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:24–05:48`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Tìm được nơi dùng API khác tự động tái sử dụng an toàn"; mở khung phải cho "CODEOWNERS không ngăn người có Read clone cả repo", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Tìm được nơi dùng API khác tự động tái sử dụng an toàn. Nhưng chưa nên kết luận khi chưa kiểm tra CODEOWNERS không ngăn người có Read clone cả repo. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí CODEOWNERS không ngăn người có Read clone cả repo, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Phân biệt trách nhiệm sở hữu với quyền truy cập mã nguồn

**Time:** `05:48–06:50`

**Visual:**

Vẽ monorepo Web + Fraud; vendor có Read repo nên thấy cả hai folders, dù CODEOWNERS Fraud yêu cầu Security reviewer; kho Fraud riêng thay đổi quyền đọc.

**Script:**

Giả sử nhà thầu được quyền clone monorepo để sửa Web, nhưng trong cùng root còn chứa mã Fraud nhạy cảm. CODEOWNERS có thể yêu cầu Security review khi file Fraud đổi; nó không che nội dung Fraud khỏi người đã được phép đọc cả repo. Đây là khác biệt giữa trách nhiệm review và quyền đọc. Nếu chính sách đòi cô lập source, phải đánh giá boundary của hosting/repository thật thay vì chỉ thêm pattern. Tình huống này có thể buộc tách repo dù làm phối hợp khó hơn.

**Purpose:**

Người học phải nhận biết được CODEOWNERS không ngăn người có Read clone cả repo từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Ranh giới nhóm và repository không nhất thiết trùng nhau

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:50–07:13`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "CODEOWNERS không ngăn người có Read clone cả repo"; mở khung phải cho "Sơ đồ tổ chức không định nghĩa ranh giới phụ thuộc", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra CODEOWNERS không ngăn người có Read clone cả repo. Nhưng chưa nên kết luận khi chưa kiểm tra Sơ đồ tổ chức không định nghĩa ranh giới phụ thuộc. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Sơ đồ tổ chức không định nghĩa ranh giới phụ thuộc, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Ranh giới nhóm và repository không nhất thiết trùng nhau

**Time:** `07:13–08:17`

**Visual:**

Hiện org chart Web/Payments với team đổi tên; đường code dependency Web→Contracts→Payments vẫn giữ, repository boundary chưa đổi.

**Script:**

Team có thể tách, gộp hoặc đổi tên theo quý, nhưng API contract giữa Web và Payments không biến mất vì sơ đồ tổ chức thay đổi. Nếu tạo repo theo từng team, lần tái tổ chức tiếp theo có thể kéo theo di chuyển source vô ích. Ngược lại, một team đôi khi cần nhiều repos do bảo mật hoặc lifecycle khác nhau. Trên màn hình giữ nguyên mũi tên dependency khi hoán đổi thẻ team để thấy topology nên dựa vào tương tác mã và quyền, không chỉ vào phòng ban.

**Purpose:**

Người học phải nhận biết được Sơ đồ tổ chức không định nghĩa ranh giới phụ thuộc từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Số lượng service không quyết định trực tiếp số repository

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:17–08:41`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Sơ đồ tổ chức không định nghĩa ranh giới phụ thuộc"; mở khung phải cho "Đếm service và repo là hai phép đo độc lập", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Sơ đồ tổ chức không định nghĩa ranh giới phụ thuộc. Nhưng chưa nên kết luận khi chưa kiểm tra Đếm service và repo là hai phép đo độc lập. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Đếm service và repo là hai phép đo độc lập, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Số lượng service không quyết định trực tiếp số repository

**Time:** `08:41–09:45`

**Visual:**

Đặt 5 biểu tượng service trong một Git root, đối diện một service tiêu thụ schema ở hai Git root; giữ các mũi tên deployment riêng.

**Script:**

Một hệ thống có năm service có thể đặt tất cả mã trong một monorepo và vẫn phát hành riêng. Một service cũng có thể lấy mã ứng dụng từ một repo và schema công khai từ repo khác. Vì vậy bảng kiểm kê chỉ ghi số service sẽ không cho biết số repository cần có. Hãy bổ sung Git roots, quyền đọc, owner, PR thường phải thay cùng nhau và dependency contracts. Chính quan hệ giữa các thành phần mới quyết định chi phí phối hợp, chứ không phải phép đếm container.

**Purpose:**

Người học phải nhận biết được Đếm service và repo là hai phép đo độc lập từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Đánh giá vùng sở hữu và trách nhiệm chồng lấn trên sơ đồ kho

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:45–10:08`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Đếm service và repo là hai phép đo độc lập"; mở khung phải cho "Ownership map phải phát hiện vùng không người nhận", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Đếm service và repo là hai phép đo độc lập. Nhưng chưa nên kết luận khi chưa kiểm tra Ownership map phải phát hiện vùng không người nhận. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Ownership map phải phát hiện vùng không người nhận, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Đánh giá vùng sở hữu và trách nhiệm chồng lấn trên sơ đồ kho

**Time:** `10:08–11:13`

**Visual:**

Bảng path/repo, primary, backup, consumer, reviewers, read/write và escalation; làm đỏ `/libs/contracts` không backup hoặc không ai review schema.

**Script:**

Hãy lập một bản đồ ownership có thể kiểm tra: mỗi repo hoặc path có người phụ trách chính, người dự phòng, consumer và quy tắc chuyển việc khi vắng mặt. Với `/libs/contracts`, Platform có thể là owner nhưng Web/API là bên chịu ảnh hưởng. Nếu bảng thiếu backup hoặc reviewer cho schema, đó là khoảng trống cần sửa trước khi đổi topology. Theo dõi PR bị ping sai người hoặc chờ lâu để xác nhận vấn đề thật. Một số đường dẫn có hai owner không sai nếu trách nhiệm được thống nhất rõ.

**Purpose:**

Người học phải nhận biết được Ownership map phải phát hiện vùng không người nhận từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.
