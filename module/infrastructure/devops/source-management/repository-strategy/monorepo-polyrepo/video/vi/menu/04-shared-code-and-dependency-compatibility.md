---
video:
  url: ""
---

# Chia sẻ mã nguồn, phụ thuộc và tương thích

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

## Shared library và consumer: khái niệm và quan hệ sử dụng

<!-- VIDEO_SECTION -->

### Scene 1 — Shared library và consumer: khái niệm và quan hệ sử dụng

**Time:** `00:00–01:03`

**Visual:**

Dựng `auth-client` → Web và Checkout API; nâng version thư viện, tô hai consumer có thể lỗi ở hai bên repo boundary.

**Script:**

Shared library cung cấp một phần giao diện hoặc logic dùng lại, còn consumer là những module dựa vào nó. Nếu AuthClient đổi một phương thức đang gọi, cả Web và API có thể bị ảnh hưởng dù chúng thuộc một hay nhiều repositories. Trước khi sửa, người bảo trì cần tìm mọi consumer, owner, version và bộ test hợp đồng liên quan. Monorepo có thể làm quan hệ dễ nhìn hơn; polyrepo cần catalog tốt. Không có topology nào tự động bảo đảm mọi consumer đã nâng cấp an toàn.

**Purpose:**

Người học phải nhận biết được AuthClient là một phụ thuộc có nhiều consumer từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Ranh giới phụ thuộc so với ranh giới repository

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:03–01:24`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "AuthClient là một phụ thuộc có nhiều consumer"; mở khung phải cho "Hai module chung repo vẫn bị coupling", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra AuthClient là một phụ thuộc có nhiều consumer. Nhưng chưa nên kết luận khi chưa kiểm tra Hai module chung repo vẫn bị coupling. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Hai module chung repo vẫn bị coupling, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Ranh giới phụ thuộc so với ranh giới repository

**Time:** `01:24–02:28`

**Visual:**

Trên cùng Git root, vẽ mũi tên dependency `web→auth-client` và cảnh báo dependency cycle; bên cạnh hai repos có interface phụ thuộc chặt.

**Script:**

Ranh giới dependency xuất hiện nơi mã A gọi giao diện của B; ranh giới repository xuất hiện nơi hai mã có lịch sử quản lý riêng. Chúng không trùng nhau. Đặt Web và AuthClient vào chung repo không khiến coupling biến mất, còn tách kho cũng không cho Web thôi phụ thuộc vào contract. Nếu build graph có vòng phụ thuộc, di chuyển thư mục chưa giải quyết được. Hãy vẽ đồ thị sử dụng API trước, rồi mới cân nhắc liệu quyền hay lịch thay đổi có cần một Git root khác.

**Purpose:**

Người học phải nhận biết được Hai module chung repo vẫn bị coupling từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Cập nhật thư viện dùng chung và consumer trong monorepo

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:28–02:49`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Hai module chung repo vẫn bị coupling"; mở khung phải cho "Thư viện và consumer đổi chung một PR", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Hai module chung repo vẫn bị coupling. Nhưng chưa nên kết luận khi chưa kiểm tra Thư viện và consumer đổi chung một PR. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Thư viện và consumer đổi chung một PR, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Cập nhật thư viện dùng chung và consumer trong monorepo

**Time:** `02:49–03:52`

**Visual:**

Một PR chạm `/libs/auth-client`, `/apps/web`, `/services/api`; đặt review ownership và danh sách affected contract tests cạnh ba diff.

**Script:**

Trong monorepo, người bảo trì có thể sửa AuthClient và cùng lúc cập nhật Web, API trên một PR. Reviewer dễ nhìn xem chữ ký mới được sử dụng đúng chưa, nên khả năng sửa nhiều consumer cùng lúc là lợi thế thực tế. Nhưng nếu có một consumer chưa được phát hiện hoặc test chưa chạy, PR đồng bộ vẫn có thể hỏng khi phát hành. Bảng kiểm cần liệt kê những consumer phải kiểm chứng và người duyệt mỗi vùng, không chỉ thấy nhiều folder trong diff rồi yên tâm.

**Purpose:**

Người học phải nhận biết được Thư viện và consumer đổi chung một PR từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Quản lý phiên bản và lộ trình consumer tiếp nhận trong polyrepo

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:52–04:13`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Thư viện và consumer đổi chung một PR"; mở khung phải cho "Publisher ra 2.1 không làm Web tự nâng", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Thư viện và consumer đổi chung một PR. Nhưng chưa nên kết luận khi chưa kiểm tra Publisher ra 2.1 không làm Web tự nâng. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Publisher ra 2.1 không làm Web tự nâng, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Quản lý phiên bản và lộ trình consumer tiếp nhận trong polyrepo

**Time:** `04:13–05:14`

**Visual:**

Biểu đồ version `AuthClient 2.1 published`, `API uses 2.1`, `Web uses 1.9`; gắn timeline adoption riêng và bảng support range.

**Script:**

Ở polyrepo, bên cung cấp có thể phát hành AuthClient 2.1 trước khi tất cả consumer sẵn sàng nâng. API dùng 2.1 trong khi Web còn 1.9; đây là tình huống bình thường nếu hợp đồng còn tương thích. Nhưng nếu bản 2.1 bỏ hành vi Web cần, phải quản lý giai đoạn hỗ trợ hoặc kế hoạch migration. Hãy đọc dependency version thực tế trong từng consumer và release record; ngày đóng PR library không đủ để chứng minh ứng dụng đã đưa version mới vào production.

**Purpose:**

Người học phải nhận biết được Publisher ra 2.1 không làm Web tự nâng từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Interface contract, tính tương thích và thay đổi phá vỡ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:14–05:37`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Publisher ra 2.1 không làm Web tự nâng"; mở khung phải cho "Contract bao gồm hành vi và lỗi, không chỉ method signature", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Publisher ra 2.1 không làm Web tự nâng. Nhưng chưa nên kết luận khi chưa kiểm tra Contract bao gồm hành vi và lỗi, không chỉ method signature. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Contract bao gồm hành vi và lỗi, không chỉ method signature, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Interface contract, tính tương thích và thay đổi phá vỡ

**Time:** `05:37–06:41`

**Visual:**

Dựng request JSON trước/sau `totalAmount`, `amount`, expected 400 khi số âm; lập bảng client cũ/mới × server cũ/mới.

**Script:**

Interface contract không chỉ có tên hàm hay kiểu JSON. Consumer còn dựa vào field bắt buộc, mã lỗi, ý nghĩa dữ liệu và những cam kết version. Nếu API bỏ totalAmount ngay khi Web cũ vẫn gửi field đó, thay đổi phá tương thích có thể xuất hiện dù cả hai repo đều merge thành công. Hãy kiểm tra tổ hợp client cũ/mới với server cũ/mới và mong đợi về lỗi âm tiền. Một repository chung có thể phát hiện sớm, nhưng không làm client đã triển khai cập nhật tức thì.

**Purpose:**

Người học phải nhận biết được Contract bao gồm hành vi và lỗi, không chỉ method signature từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Đánh đổi giữa nhất quán phụ thuộc và quyền tự chủ công nghệ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:41–07:05`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Contract bao gồm hành vi và lỗi, không chỉ method signature"; mở khung phải cho "Nhất quán thư viện và tự chủ công cụ có thể đánh đổi", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Contract bao gồm hành vi và lỗi, không chỉ method signature. Nhưng chưa nên kết luận khi chưa kiểm tra Nhất quán thư viện và tự chủ công cụ có thể đánh đổi. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Nhất quán thư viện và tự chủ công cụ có thể đánh đổi, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Đánh đổi giữa nhất quán phụ thuộc và quyền tự chủ công nghệ

**Time:** `07:05–08:08`

**Visual:**

Vẽ bảng central version catalog và từng repo riêng dùng Java/Python/Node; đánh dấu nơi cần coordination và nơi có thể đổi toolchain độc lập.

**Script:**

Monorepo thường giúp một team phát hiện thư viện dùng lại và kiểm soát dependency updates cùng nhau. Polyrepo thường làm việc lựa chọn toolchain hoặc ngày nâng version theo từng nhóm dễ khoanh vùng hơn. Nhưng cả hai hướng đều có thể vận hành nhiều ngôn ngữ hay áp chính sách version thống nhất. Vấn đề là đội có đủ công cụ và năng lực duy trì dependency graph hay không. Đừng chọn polyrepo chỉ để được đổi Java version nếu monorepo hiện đã cho phép build boundaries độc lập.

**Purpose:**

Người học phải nhận biết được Nhất quán thư viện và tự chủ công cụ có thể đánh đổi từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Cùng repository không bắt buộc cùng phiên bản phụ thuộc

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:08–08:32`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Nhất quán thư viện và tự chủ công cụ có thể đánh đổi"; mở khung phải cho "Cùng Git root vẫn có dependency 1.9 và 2.1", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Nhất quán thư viện và tự chủ công cụ có thể đánh đổi. Nhưng chưa nên kết luận khi chưa kiểm tra Cùng Git root vẫn có dependency 1.9 và 2.1. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Cùng Git root vẫn có dependency 1.9 và 2.1, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Cùng repository không bắt buộc cùng phiên bản phụ thuộc

**Time:** `08:32–09:36`

**Visual:**

Trong cây monorepo, module Web khai báo AuthClient 1.9, API khai báo 2.1; hiển thị cảnh báo cần contract test nhưng không tự động sửa version.

**Script:**

Hai Gradle module ở một monorepo không bắt buộc dùng cùng version AuthClient nếu chính sách build cho phép. Để Web ở 1.9 và API ở 2.1 có thể là kế hoạch migration, hoặc có thể tạo rủi ro khi dùng API chung. Điều cần xem là khai báo dependency của từng build unit và tập hợp version được hỗ trợ. Ta không suy từ chung root rằng chúng chia cùng classpath hay phải phát hành chung. Đó là quyết định dependency và release policy, không phải thuộc tính cố định của repository topology.

**Purpose:**

Người học phải nhận biết được Cùng Git root vẫn có dependency 1.9 và 2.1 từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Bằng chứng khi nâng cấp shared library và các consumer

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:36–09:58`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Cùng Git root vẫn có dependency 1.9 và 2.1"; mở khung phải cho "Đọc bằng chứng migration từ provider tới consumers", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Cùng Git root vẫn có dependency 1.9 và 2.1. Nhưng chưa nên kết luận khi chưa kiểm tra Đọc bằng chứng migration từ provider tới consumers. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Đọc bằng chứng migration từ provider tới consumers, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Bằng chứng khi nâng cấp shared library và các consumer

**Time:** `09:58–11:06`

**Visual:**

Lập bảng migration AuthClient 1.9→2.1: provider API change, Web adoption PR, API adoption PR, contract tests, actual version rollout; để các ô trống nếu chưa được kiểm chứng.

**Script:**

Một bản kế hoạch migration tốt không kết thúc ở PR của thư viện. Ta cần nhìn cả thay đổi interface, danh sách consumer, version đang khai báo, kết quả tương thích và thời điểm các nhóm triển khai. Trong monorepo có thể xem chúng cùng một nhánh; trong polyrepo phải dùng PR/version links. Nếu Web chưa nâng hoặc contract test không có, bảng đó phải thể hiện chưa đủ bằng chứng. Đừng diễn kết quả pass hoặc ngày release để minh họa cho đẹp; dùng trạng thái mẫu và nói rõ cần kiểm tra dữ liệu thật trước khi ký nghiệm thu.

**Purpose:**

Người học phải nhận biết được Đọc bằng chứng migration từ provider tới consumers từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.
