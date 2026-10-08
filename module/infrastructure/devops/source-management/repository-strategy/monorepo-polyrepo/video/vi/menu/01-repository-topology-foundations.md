---
video:
  url: ""
---

# Nền tảng về monorepo, polyrepo và ranh giới mã nguồn

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

## Repository topology: khái niệm và phạm vi tổ chức kho mã

<!-- VIDEO_SECTION -->

### Scene 1 — Repository topology: khái niệm và phạm vi tổ chức kho mã

**Time:** `00:00–00:59`

**Visual:**

Vẽ ba thành phần Web, Checkout API, Contracts; kéo chúng vào một khung `.git` và ba khung `.git` riêng. Giữ sơ đồ service/deployment bất biến.

**Script:**

Hãy nhìn ba thành phần phục vụ cùng chức năng thanh toán. Ở bản vẽ thứ nhất chúng dùng chung một Git root; bản vẽ thứ hai mỗi thành phần có lịch sử riêng. Chúng ta chưa đổi số service hay môi trường chạy. Vì vậy, đừng đếm thư mục hoặc container để kết luận topology. Ta sẽ lần lượt xét chủ sở hữu, thay đổi liên thành phần, phụ thuộc, phát hành, quyền đọc, chi phí CI rồi mới chọn tách hay gộp repository.

**Purpose:**

Người học phải nhận biết được Hai kiến trúc source khác số Git root, không khác số service từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Tác động của ranh giới repository tới cộng tác và thay đổi

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:59–01:23`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Hai kiến trúc source khác số Git root, không khác số service"; mở khung phải cho "Một thay đổi hợp đồng làm lộ chi phí phối hợp", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Hai kiến trúc source khác số Git root, không khác số service. Nhưng chưa nên kết luận khi chưa kiểm tra Một thay đổi hợp đồng làm lộ chi phí phối hợp. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Một thay đổi hợp đồng làm lộ chi phí phối hợp, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Tác động của ranh giới repository tới cộng tác và thay đổi

**Time:** `01:23–02:22`

**Visual:**

Dùng ticket TAX-42 đổi field `totalAmount` ở API, Web và Contracts; đánh dấu nơi PR/approval và quyền đọc khác nhau trong hai mô hình.

**Script:**

Một yêu cầu đổi tên trường totalAmount trông nhỏ, nhưng chạm cả thư viện hợp đồng, API và Web. Nếu chung repository, reviewer có thể thấy cả ba diff cùng lúc nhưng vẫn phải tìm đúng owner. Nếu tách ba kho, từng PR và version có thể đi theo lịch riêng, khiến nhóm phải liên kết và theo dõi sự tương thích. Ranh giới repository làm thay đổi nơi ta ghi và duyệt công việc, không xóa quan hệ giữa các thành phần.

**Purpose:**

Người học phải nhận biết được Một thay đổi hợp đồng làm lộ chi phí phối hợp từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Monorepo: nhiều project hoặc thành phần trong một repository

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:22–02:46`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Một thay đổi hợp đồng làm lộ chi phí phối hợp"; mở khung phải cho "Một root Git chứa nhiều component với ranh giới build riêng", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Một thay đổi hợp đồng làm lộ chi phí phối hợp. Nhưng chưa nên kết luận khi chưa kiểm tra Một root Git chứa nhiều component với ranh giới build riêng. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Một root Git chứa nhiều component với ranh giới build riêng, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Monorepo: nhiều project hoặc thành phần trong một repository

**Time:** `02:46–03:48`

**Visual:**

Hiển thị cây `store/.git`, `/apps/web`, `/services/api`, `/libs/contracts`, mỗi nhánh mang một biểu tượng build unit khác nhau.

**Script:**

Trong monorepo, các thư mục Web, API và Contracts cùng thuộc một repository nên có thể tham gia một diff hoặc commit được review chung. Nhưng điều đó không yêu cầu cả ba cùng ngôn ngữ, cùng build command, hay phát hành một lượt. Hãy phóng lớn đường viền `.git` duy nhất và tách nó khỏi đường viền của từng build unit. Lợi ích có thể là khả năng tìm API và nơi sử dụng của nó; chi phí là phạm vi quyền và công cụ quản lý diện rộng.

**Purpose:**

Người học phải nhận biết được Một root Git chứa nhiều component với ranh giới build riêng từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Polyrepo: phân chia project hoặc thành phần vào nhiều repository

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:48–04:11`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Một root Git chứa nhiều component với ranh giới build riêng"; mở khung phải cho "Nhiều root Git giữ version và quyền riêng", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Một root Git chứa nhiều component với ranh giới build riêng. Nhưng chưa nên kết luận khi chưa kiểm tra Nhiều root Git giữ version và quyền riêng. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Nhiều root Git giữ version và quyền riêng, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Polyrepo: phân chia project hoặc thành phần vào nhiều repository

**Time:** `04:11–05:15`

**Visual:**

Thay cây chung bằng `store-web.git`, `store-api.git`, `store-contracts.git`; vẽ tag `contracts 2.1` và consumer còn dùng `1.9`.

**Script:**

Trong polyrepo, Web, API và Contracts có repository, lịch sử và quyền quản trị riêng. Một bản sửa của thư viện có thể được công bố là version 2.1 trong khi Web chưa nâng khỏi 1.9. Nhóm phải theo dõi hợp đồng hỗ trợ cả hai bản trong giai đoạn chuyển đổi. Sự độc lập này giúp giới hạn quyền đọc hoặc công cụ của từng kho, nhưng không bảo đảm các service tự vận hành độc lập. Hãy nhìn đúng ba đường lịch sử và các version mà consumer thực sự dùng.

**Purpose:**

Người học phải nhận biết được Nhiều root Git giữ version và quyền riêng từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Repository, project, module và package: các ranh giới khác nhau

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:15–05:36`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Nhiều root Git giữ version và quyền riêng"; mở khung phải cho "Repository, product project, build module và Java package", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Nhiều root Git giữ version và quyền riêng. Nhưng chưa nên kết luận khi chưa kiểm tra Repository, product project, build module và Java package. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Repository, product project, build module và Java package, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Repository, project, module và package: các ranh giới khác nhau

**Time:** `05:36–06:35`

**Visual:**

Chia màn hình thành bốn thẻ repo root, project Payments, Gradle module `:billing`, package `com.shop.billing`; nối quan hệ không phải một-một.

**Script:**

Ở codebase Payments, project chỉ mục tiêu sản phẩm; Gradle module là đơn vị build; package Java gom lớp theo namespace; repository bao quanh lịch sử source. Một repository có thể chứa nhiều Gradle module, hoặc một sản phẩm sử dụng hai repository khác nhau. Nếu người học dùng từ module cho cả bốn lớp, quyết định topology sẽ thiếu dữ liệu. Hãy chỉ vào thẻ `.git`, sau đó tách khỏi build.gradle và khai báo package để thấy rõ ranh giới đang bàn.

**Purpose:**

Người học phải nhận biết được Repository, product project, build module và Java package từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Ranh giới repository so với service và đơn vị triển khai

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:35–06:56`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Repository, product project, build module và Java package"; mở khung phải cho "Một repo không đồng nghĩa một deploy", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Repository, product project, build module và Java package. Nhưng chưa nên kết luận khi chưa kiểm tra Một repo không đồng nghĩa một deploy. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Một repo không đồng nghĩa một deploy, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Ranh giới repository so với service và đơn vị triển khai

**Time:** `06:56–07:57`

**Visual:**

Cùng cây repo chứa API/Web, nhưng dưới vẽ hai deployment độc lập; thêm trường hợp nhiều repos đóng gói chung một sản phẩm.

**Script:**

Hãy xem hai service API và Web sống trong một repo, nhưng mỗi service có artifact và lịch đưa lên môi trường riêng. Như vậy monorepo không tự đồng bộ deploy. Ở chiều ngược lại, một sản phẩm chạy cùng lúc có thể lấy mã và schema từ nhiều repo; đó vẫn là polyrepo về nguồn. Muốn biết hệ thống có độc lập ở runtime không, phải kiểm tra contract, dữ liệu, release và cách triển khai. Đếm Git root không cho chúng ta câu trả lời đó.

**Purpose:**

Người học phải nhận biết được Một repo không đồng nghĩa một deploy từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Quan hệ giữa sở hữu, phối hợp thay đổi và tính độc lập

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:57–08:19`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Một repo không đồng nghĩa một deploy"; mở khung phải cho "Ba câu hỏi chiến lược: owner, phối hợp, độc lập", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Một repo không đồng nghĩa một deploy. Nhưng chưa nên kết luận khi chưa kiểm tra Ba câu hỏi chiến lược: owner, phối hợp, độc lập. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Ba câu hỏi chiến lược: owner, phối hợp, độc lập, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Quan hệ giữa sở hữu, phối hợp thay đổi và tính độc lập

**Time:** `08:19–09:23`

**Visual:**

Bảng ba hàng: owner `/contracts`, ai review TAX-42, API/Web có thể deploy khác lịch? Mỗi hàng gắn một nguồn bằng chứng riêng.

**Script:**

Khi nhóm muốn thay đổi topology, hãy đặt ba câu hỏi trước khi vẽ lại thư mục: ai chịu trách nhiệm cho Contracts, ai cần tham gia mỗi lần API đổi, và khi nào Web có thể phát hành mà không chờ API. Một team có thể sở hữu cả hai repos nhưng vẫn mắc nợ phối hợp; chung repo cũng không bảo đảm deploy độc lập. Ta sẽ ghi owner map, linked PR và version compatibility cạnh từng câu hỏi. Chỉ quyết định sau khi nhận diện ràng buộc quan trọng nhất.

**Purpose:**

Người học phải nhận biết được Ba câu hỏi chiến lược: owner, phối hợp, độc lập từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Monorepo không đồng nghĩa với ứng dụng nguyên khối

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:23–09:45`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Ba câu hỏi chiến lược: owner, phối hợp, độc lập"; mở khung phải cho "Ma trận hai chiều topology và runtime architecture", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Ba câu hỏi chiến lược: owner, phối hợp, độc lập. Nhưng chưa nên kết luận khi chưa kiểm tra Ma trận hai chiều topology và runtime architecture. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Ma trận hai chiều topology và runtime architecture, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Monorepo không đồng nghĩa với ứng dụng nguyên khối

**Time:** `09:45–10:45`

**Visual:**

Vẽ ma trận 2×2: monorepo/polyrepo trên trục ngang, monolith/microservices trên trục dọc. Đặt ví dụ API+Web vào bốn ô có thể.

**Script:**

Một ứng dụng nguyên khối mô tả cách đóng gói và các phần phụ thuộc khi chạy. Monorepo chỉ nói cách ghi lịch sử source. Ta có thể có nhiều service chạy riêng trong một repo, hoặc một sản phẩm nguyên khối được ghép từ mã thuộc nhiều repo. Vì vậy, câu 'đang microservices nên cần polyrepo' không có cơ sở chỉ từ runtime. Dùng ma trận hai trục này để bắt người ra quyết định nói rõ họ đang thay đổi nguồn, build hay deployment.

**Purpose:**

Người học phải nhận biết được Ma trận hai chiều topology và runtime architecture từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Ví dụ nhận diện monorepo và polyrepo theo cấu trúc mã nguồn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:45–11:07`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Ma trận hai chiều topology và runtime architecture"; mở khung phải cho "Nhận diện bằng Git root, không phải số folder", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Ma trận hai chiều topology và runtime architecture. Nhưng chưa nên kết luận khi chưa kiểm tra Nhận diện bằng Git root, không phải số folder. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Nhận diện bằng Git root, không phải số folder, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Ví dụ nhận diện monorepo và polyrepo theo cấu trúc mã nguồn

**Time:** `11:07–12:07`

**Visual:**

Đặt sơ đồ A `store.git/{web,api,shared}`, sơ đồ B `web.git/api.git/shared.git`, thêm C Git submodule có hai root và dependency registry.

**Script:**

Ở sơ đồ A, có ba thư mục và ba module build, nhưng chỉ một Git root nên đó là monorepo. B có ba repositories độc lập nên là polyrepo, dù các file nằm cạnh nhau trong một workspace IDE. C dùng submodule hoặc package registry để liên kết nhiều nguồn; sự liên kết không làm lịch sử thành một repo. Để xác nhận topology thật, hãy tìm root và commit history riêng, sau đó mới hỏi ai xem, sửa và review được từng phần.

**Purpose:**

Người học phải nhận biết được Nhận diện bằng Git root, không phải số folder từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.
