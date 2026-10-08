---
video:
  url: ""
---

# Quy mô và đánh đổi công cụ, build, CI ở cấp chiến lược

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

## Quy mô repository: lịch sử, kích thước, nhân sự và tần suất thay đổi

<!-- VIDEO_SECTION -->

### Scene 1 — Quy mô repository: lịch sử, kích thước, nhân sự và tần suất thay đổi

**Time:** `00:00–01:03`

**Visual:**

Bảng gồm history objects, large binaries, refs, contributors, change volume, dependency density, clone/PR feedback; ô số liệu để N/A khi chưa đo.

**Script:**

Một repo 2 GB có thể còn vận hành nhanh nếu history và công cụ phù hợp, trong khi mười repo nhỏ tạo rất nhiều PR liên kho. Vì vậy, hãy đánh giá cả lịch sử Git, binary lớn, số contributors, tần suất thay đổi và dependency graph. Đặt những câu hỏi này trên bảng hiện trạng trước khi nói monorepo sẽ quá lớn. Không có một ngưỡng GB phổ quát buộc tách kho. Hãy ghi nguồn số đo, môi trường và thời gian quan sát thật thay vì tạo dashboard giả.

**Purpose:**

Người học phải nhận biết được Kích thước repo không chỉ là số gigabytes từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Tác động của quy mô lịch sử và thời gian phản hồi công cụ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:03–01:25`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Kích thước repo không chỉ là số gigabytes"; mở khung phải cho "Đo clone/fetch theo phân phối, không bằng cảm giác", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Kích thước repo không chỉ là số gigabytes. Nhưng chưa nên kết luận khi chưa kiểm tra Đo clone/fetch theo phân phối, không bằng cảm giác. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Đo clone/fetch theo phân phối, không bằng cảm giác, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Tác động của quy mô lịch sử và thời gian phản hồi công cụ

**Time:** `01:25–02:32`

**Visual:**

Vẽ p50/p95 clone, fetch, search, PR feedback trên hai môi trường sample; ghi 'placeholder requires measured logs', large binary risk riêng.

**Script:**

Lịch sử source lớn hoặc chứa các binary thường xuyên thay đổi có thể làm clone và thao tác Git chậm đi. Nhưng nhiều repos cũng khiến một tính năng cần tải và tìm kiếm ở vài kho. Thay vì 'repo lớn luôn chậm', ta yêu cầu dữ liệu p50 và p95 theo môi trường, đối tượng người dùng và thời gian quan sát. Tài liệu Azure Repos nêu rủi ro tệp lớn và cấu trúc cây, không cho một ngưỡng tách kho cố định. Khi bottleneck là lịch sử binary, tối ưu tooling có thể đi trước migration.

**Purpose:**

Người học phải nhận biết được Đo clone/fetch theo phân phối, không bằng cảm giác từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Giới hạn phạm vi công việc chịu ảnh hưởng trong monorepo lớn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:32–02:55`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Đo clone/fetch theo phân phối, không bằng cảm giác"; mở khung phải cho "Thay đổi schema phải mở rộng affected tests tới consumers", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Đo clone/fetch theo phân phối, không bằng cảm giác. Nhưng chưa nên kết luận khi chưa kiểm tra Thay đổi schema phải mở rộng affected tests tới consumers. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Thay đổi schema phải mở rộng affected tests tới consumers, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Giới hạn phạm vi công việc chịu ảnh hưởng trong monorepo lớn

**Time:** `02:55–04:02`

**Visual:**

Dependency graph `Contracts→API,Web`; một PR sửa README Web chỉ ảnh hưởng Web, PR sửa Contracts đánh dấu API/Web. Chưa chạy jobs nên không tạo pass badge.

**Script:**

Trong monorepo lớn, chạy toàn bộ backend tests cho sửa tài liệu Web sẽ lãng phí. Nhưng nếu chỉnh schema Contracts, chỉ chạy Web tests có thể bỏ sót API bị ảnh hưởng. Cần bản đồ path tới component và dependent để chọn phạm vi kiểm chứng bảo thủ. Đó là yêu cầu chiến lược gửi cho tooling team, không phải công thức pipeline mà chúng ta triển khai ở đây. Hãy xem dependency graph, mức tin cậy của nó và bug lọt qua affected selection để xác định có thể giảm thời gian phản hồi an toàn hay không.

**Purpose:**

Người học phải nhận biết được Thay đổi schema phải mở rộng affected tests tới consumers từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Build, test và CI: vai trò cơ bản và yêu cầu chiến lược của repository topology

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:02–04:25`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Thay đổi schema phải mở rộng affected tests tới consumers"; mở khung phải cho "Build tạo artifact, test kiểm contract, CI tự động hóa", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Thay đổi schema phải mở rộng affected tests tới consumers. Nhưng chưa nên kết luận khi chưa kiểm tra Build tạo artifact, test kiểm contract, CI tự động hóa. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Build tạo artifact, test kiểm contract, CI tự động hóa, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Build, test và CI: vai trò cơ bản và yêu cầu chiến lược của repository topology

**Time:** `04:25–05:31`

**Visual:**

Ba lanes Build→artifact, Test→behavior/contract, CI→automation results; overlay một thay đổi Contracts cần kiểm API/Web. Không đưa command hoặc output runner giả.

**Script:**

Build, test và CI giải quyết ba việc khác nhau. Build tạo artifact, test kiểm tra hành vi hoặc hợp đồng, CI tự động gọi những bước ấy khi thay đổi được đề xuất. Repository topology ảnh hưởng nơi dữ liệu nguồn xuất hiện và phạm vi cần kiểm chứng, nhưng không bắt mọi team dùng một công cụ. Với Contracts đổi schema, ta yêu cầu thông tin kết quả API/Web liên quan trước khi chấp nhận migration. Đây là bản đặc tả outcome cho nhóm CI, không phải ví dụ chạy Gradle hay tự dựng pipeline YAML.

**Purpose:**

Người học phải nhận biết được Build tạo artifact, test kiểm contract, CI tự động hóa từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Chi phí lặp lại cấu hình và công cụ trên nhiều repository

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:31–05:54`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Build tạo artifact, test kiểm contract, CI tự động hóa"; mở khung phải cho "Hai mươi repos có thể lệch template bảo mật", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Build tạo artifact, test kiểm contract, CI tự động hóa. Nhưng chưa nên kết luận khi chưa kiểm tra Hai mươi repos có thể lệch template bảo mật. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Hai mươi repos có thể lệch template bảo mật, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Chi phí lặp lại cấu hình và công cụ trên nhiều repository

**Time:** `05:54–07:00`

**Visual:**

20 repo tiles, 16 đã nâng review baseline, 4 chưa; phần thời gian rollout chỉ để blank, chú thích ví dụ chứ không phải số thực.

**Script:**

Nhiều repositories có thể nhân bản cấu hình review, branch protection, checks và owners. Khi policy thay đổi, người vận hành phải xác nhận đã triển khai đủ trên từng repo và biết lý do ngoại lệ. Trên mô hình 20 kho, bốn ô còn thiếu chỉ để minh họa khả năng policy drift; không phải số liệu từ một tổ chức. Hãy đo thời gian rollout và tỷ lệ coverage thực tế, cùng những khác biệt toolchain có lợi. Một catalog và template quản trị chung có thể giảm nợ mà không bắt buộc hợp nhất source.

**Purpose:**

Người học phải nhận biết được Hai mươi repos có thể lệch template bảo mật từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Đầu tư công cụ, hạ tầng và quản trị cho monorepo quy mô lớn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:00–07:22`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Hai mươi repos có thể lệch template bảo mật"; mở khung phải cho "Monorepo lớn đòi indexing, cache và affected validation", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Hai mươi repos có thể lệch template bảo mật. Nhưng chưa nên kết luận khi chưa kiểm tra Monorepo lớn đòi indexing, cache và affected validation. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Monorepo lớn đòi indexing, cache và affected validation, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Đầu tư công cụ, hạ tầng và quản trị cho monorepo quy mô lớn

**Time:** `07:22–08:28`

**Visual:**

Vẽ monorepo 300 components với Search index, dependency graph, cache, path owners, incremental validation quanh lõi; mỗi thành phần có owner đầu tư.

**Script:**

Đặt 300 components trong cùng Git root không tự khiến người phát triển làm việc hiệu quả. Họ có thể cần code search, indexing, caches, dependency graph, affected validation và quy tắc owner đáng tin cậy. Nếu mỗi PR chờ hàng giờ dù chỉ đổi vài file, đầu tư công cụ có thể là trở ngại thật. Case Google năm 2016 cũng mô tả hạ tầng chuyên dụng, không phải Git tiêu chuẩn tự mở rộng vô hạn. Trước khi gom thêm repo, hãy hỏi ai chịu trách nhiệm và chi phí vận hành các năng lực này.

**Purpose:**

Người học phải nhận biết được Monorepo lớn đòi indexing, cache và affected validation từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Tác động của repository topology tới quyền tự chủ công cụ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:28–08:50`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Monorepo lớn đòi indexing, cache và affected validation"; mở khung phải cho "Đa ngôn ngữ không tự ép một build system", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Monorepo lớn đòi indexing, cache và affected validation. Nhưng chưa nên kết luận khi chưa kiểm tra Đa ngôn ngữ không tự ép một build system. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Đa ngôn ngữ không tự ép một build system, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Tác động của repository topology tới quyền tự chủ công cụ

**Time:** `08:50–09:56`

**Visual:**

Trong cùng repo hiển thị Python Data, Java API, Node Web với build tools khác nhau; so với ba repos và centralized contract catalog.

**Script:**

Một nhóm Python Data có thể dùng toolchain khác Java API ngay khi cùng ở monorepo nếu boundary và automation được thiết kế phù hợp. Polyrepo thường dễ để nhóm đổi compiler hoặc nhịp nâng cấp riêng, nhưng cũng phải phối hợp khi chia shared contract. Hãy kiểm tra chi phí tool startup và môi trường thật thay vì đoán tất cả code chung root phải dùng cùng một lệnh build. Nghiên cứu năm 2018 ghi nhận sự linh hoạt toolchain của multi-repo như một lợi ích trong bối cảnh khảo sát, không phải quy luật tuyệt đối.

**Purpose:**

Người học phải nhận biết được Đa ngôn ngữ không tự ép một build system từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Dấu hiệu repository topology gây trở ngại phát triển

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:56–10:18`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Đa ngôn ngữ không tự ép một build system"; mở khung phải cho "Đừng quy mọi sự cố về repo quá lớn", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Đa ngôn ngữ không tự ép một build system. Nhưng chưa nên kết luận khi chưa kiểm tra Đừng quy mọi sự cố về repo quá lớn. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Đừng quy mọi sự cố về repo quá lớn, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Dấu hiệu repository topology gây trở ngại phát triển

**Time:** `10:18–11:21`

**Visual:**

Bảng hiện tượng: clone slow, reviewer wait, linked PR chain, compliance read failure, incompatible consumers. Đặt mỗi triệu chứng cạnh bước xác minh khác nhau.

**Script:**

Nếu dev phàn nàn 'repo quá lớn', hãy hỏi họ đang chờ clone, search, build feedback hay reviewer. Nếu API/Web phải mở ba PR liên tục, vấn đề có thể là coordination. Nếu vendor thấy source nhạy cảm, đó là hard access boundary. Mọi triệu chứng đều có thể xuất hiện ở cả hai topology và có nguyên nhân ngoài repo placement. Phân loại incidents bằng metric, owner và dependency trước khi chuyển đổi. Không nên tách lịch sử Git chỉ để giải quyết một bottleneck vốn nằm ở CI hay contract.

**Purpose:**

Người học phải nhận biết được Đừng quy mọi sự cố về repo quá lớn từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Bằng chứng định lượng và định tính khi đánh giá trade-off quy mô

<!-- VIDEO_SECTION -->

### Transition

**Time:** `11:21–11:44`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Đừng quy mọi sự cố về repo quá lớn"; mở khung phải cho "Dữ liệu p50/p95 phải đi cùng phỏng vấn người dùng", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Đừng quy mọi sự cố về repo quá lớn. Nhưng chưa nên kết luận khi chưa kiểm tra Dữ liệu p50/p95 phải đi cùng phỏng vấn người dùng. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Dữ liệu p50/p95 phải đi cùng phỏng vấn người dùng, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Bằng chứng định lượng và định tính khi đánh giá trade-off quy mô

**Time:** `11:44–12:51`

**Visual:**

Dashboard có khung p50/p95 clone, PR wait, affected test time, regressions; cạnh đó interviews về discoverability và tool autonomy. Không điền số liệu giả.

**Script:**

Không có một metric duy nhất đủ chọn topology. Ta cần dữ liệu định lượng từ lịch sử PR và công cụ, cùng phản hồi định tính về tìm owner, khám phá API và quyền chọn toolchain. Nghiên cứu Jaspan năm 2018 so sánh trải nghiệm kỹ sư từng làm với cả monorepo và multi-repo, kết hợp survey với developer-tool logs. Đây là cách gợi ý triangulation, không phải kết luận mọi team nên dùng monorepo. Khi đo trước/sau, hãy ghi rõ quy mô team, sản phẩm và thay đổi công cụ để không ngộ nhận quan hệ nhân quả.

**Purpose:**

Người học phải nhận biết được Dữ liệu p50/p95 phải đi cùng phỏng vấn người dùng từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.
