---
video:
  url: ""
---

# Chọn, tách và hợp nhất repository dựa trên bằng chứng

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

## Các tiêu chí lựa chọn repository topology

<!-- VIDEO_SECTION -->

### Scene 1 — Các tiêu chí lựa chọn repository topology

**Time:** `00:00–01:06`

**Visual:**

Bảng chọn topology có hai vùng: Hard constraints đọc Fraud và Preferences chi phí PR, CI, autonomy. Monorepo không đạt constraint thì gạch trước chấm điểm.

**Script:**

Một quyết định repository không nên bắt đầu bằng tên công ty nào dùng monorepo. Hãy hỏi nhu cầu thay đổi xuyên thành phần, interface coupling, owner, quyền đọc, lịch release và năng lực tooling. Với Fraud source chỉ cho Security xem, ranh giới cô lập có thể là điều kiện bắt buộc: phương án chia sẻ rộng cần bị loại trước khi so điểm tiện lợi. Với Web và Checkout chung access và hay thay API cùng nhau, monorepo có thể phù hợp nếu kiểm chứng dependent test đủ nhanh. Mỗi tiêu chí cần bằng chứng thực.

**Purpose:**

Người học phải nhận biết được Lựa chọn dựa ràng buộc cứng rồi mới cân nhắc lợi ích từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Tần suất thay đổi xuyên nhóm trong quyết định topology

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:06–01:30`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Lựa chọn dựa ràng buộc cứng rồi mới cân nhắc lợi ích"; mở khung phải cho "Một yêu cầu chạm mấy repo quan trọng hơn commit count", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Lựa chọn dựa ràng buộc cứng rồi mới cân nhắc lợi ích. Nhưng chưa nên kết luận khi chưa kiểm tra Một yêu cầu chạm mấy repo quan trọng hơn commit count. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Một yêu cầu chạm mấy repo quan trọng hơn commit count, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Tần suất thay đổi xuyên nhóm trong quyết định topology

**Time:** `01:30–02:37`

**Visual:**

Lấy tập ticket TAX-42, SHIP-17, AUTH-8; đếm PR/repo mỗi ticket và khoảng đợi PR cuối, tách formatting commits khỏi feature-related coupling.

**Script:**

Commit count lớn có thể chỉ do format code, không chứng minh teams đang phối hợp tốn kém. Hãy chọn một mẫu yêu cầu thật và xem mỗi yêu cầu thay đổi Contracts, API, Web trong bao nhiêu PR và bao lâu các PR liên quan mới được tích hợp. Nếu hầu hết feature cần ba repos, linked-PR overhead là dấu hiệu đáng xét. Nếu contract chỉ đổi vài lần mỗi năm, khả năng tự chủ của polyrepo có thể vẫn có lợi. Không tự đặt tỷ lệ phần trăm; ghi phương pháp chọn mẫu và thời gian quan sát.

**Purpose:**

Người học phải nhận biết được Một yêu cầu chạm mấy repo quan trọng hơn commit count từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Ma trận so sánh ownership, access, dependency và release

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:37–03:01`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Một yêu cầu chạm mấy repo quan trọng hơn commit count"; mở khung phải cho "Ma trận dùng chứng cứ theo bốn lớp ranh giới", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Một yêu cầu chạm mấy repo quan trọng hơn commit count. Nhưng chưa nên kết luận khi chưa kiểm tra Ma trận dùng chứng cứ theo bốn lớp ranh giới. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Ma trận dùng chứng cứ theo bốn lớp ranh giới, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Ma trận so sánh ownership, access, dependency và release

**Time:** `03:01–04:05`

**Visual:**

Dựng bảng rows Checkout/Web/Fraud, columns owner, read access, contract dependencies, release cadence, checks; tô Fraud read restriction là hard constraint.

**Script:**

Bảng quyết định chỉ hữu ích khi mỗi ô nói điều có thể kiểm tra. Với Checkout và Web, một owner map và lịch PR có thể cho thấy nên chung repo. Nhưng Fraud có thể cần cô lập quyền đọc, dù vẫn phụ thuộc vào một interface dùng chung. Hãy đi lần lượt ownership, access, dependency và nhịp phát hành. Nếu hai phương án đều đáp ứng ràng buộc, mới so mức độ review và công cụ. Topology lai—một monorepo Checkout/Web cùng Fraud repo riêng—có thể hợp lý hơn chọn cực đoan.

**Purpose:**

Người học phải nhận biết được Ma trận dùng chứng cứ theo bốn lớp ranh giới từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Điều kiện và lợi ích khi hợp nhất nhiều repository

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:05–04:29`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Ma trận dùng chứng cứ theo bốn lớp ranh giới"; mở khung phải cho "Gộp repo khi co-change thường xuyên và quyền đọc tương thích", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Ma trận dùng chứng cứ theo bốn lớp ranh giới. Nhưng chưa nên kết luận khi chưa kiểm tra Gộp repo khi co-change thường xuyên và quyền đọc tương thích. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Gộp repo khi co-change thường xuyên và quyền đọc tương thích, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Điều kiện và lợi ích khi hợp nhất nhiều repository

**Time:** `04:29–05:35`

**Visual:**

Hai repo Web/API Contracts với nhiều linked PR cùng ticket; sau gộp sơ đồ một PR thay hai phần, bên cạnh owner và affected tests cần nâng cấp.

**Script:**

Nếu hầu hết thay đổi Web phải chờ Contracts hoặc Checkout API ở kho khác, team tốn công lập và liên kết PR lặp đi lặp lại. Gộp có thể tăng khả năng thấy consumer migration và giảm thời gian chờ giữa các lịch sử riêng. Nhưng trước khi làm, phải kiểm tra các nhóm đều được đọc source, có owner theo path và đủ tooling để chạy affected checks. Đừng gộp chỉ vì quản lý chung một phòng. Thành công cần được đo bằng lead time và chất lượng review, không bằng số repo đã giảm.

**Purpose:**

Người học phải nhận biết được Gộp repo khi co-change thường xuyên và quyền đọc tương thích từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Điều kiện và giới hạn khi tách một repository

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:35–05:59`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Gộp repo khi co-change thường xuyên và quyền đọc tương thích"; mở khung phải cho "Tách repo khi quyền đọc hoặc lifecycle thực sự cần ranh giới", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Gộp repo khi co-change thường xuyên và quyền đọc tương thích. Nhưng chưa nên kết luận khi chưa kiểm tra Tách repo khi quyền đọc hoặc lifecycle thực sự cần ranh giới. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Tách repo khi quyền đọc hoặc lifecycle thực sự cần ranh giới, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Điều kiện và giới hạn khi tách một repository

**Time:** `05:59–07:05`

**Visual:**

Từ một repo Web+Fraud tách thành web.git và fraud.git; nối API contract versioned, giữ lại mũi tên coordination tăng lên.

**Script:**

Khi vendor được xem Web nhưng không được xem Fraud, tách repository có thể tạo một ranh giới quyền đọc rõ hơn. Sự khác nhau thực sự về owner và lifecycle cũng có thể là lý do. Nhưng nếu chỉ vì clone chậm, hãy kiểm tra binary history, indexing và affected tests trước khi di chuyển toàn bộ source. Sau khi tách vẫn phải duy trì versioned contract và phối hợp các thay đổi liên quan. Tách repo là chấp nhận thêm chi phí để giải quyết một ràng buộc thực, không phải thuốc chữa mọi bottleneck.

**Purpose:**

Người học phải nhận biết được Tách repo khi quyền đọc hoặc lifecycle thực sự cần ranh giới từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Chi phí di chuyển lịch sử, quyền và phụ thuộc

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:05–07:29`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Tách repo khi quyền đọc hoặc lifecycle thực sự cần ranh giới"; mở khung phải cho "Migration không chỉ move folder mà mang cả history và quyền", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Tách repo khi quyền đọc hoặc lifecycle thực sự cần ranh giới. Nhưng chưa nên kết luận khi chưa kiểm tra Migration không chỉ move folder mà mang cả history và quyền. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Migration không chỉ move folder mà mang cả history và quyền, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Chi phí di chuyển lịch sử, quyền và phụ thuộc

**Time:** `07:29–08:34`

**Visual:**

Checklist branch/tag/history links, old PR/Issue refs, URLs, permissions, package identities, dependency versions, CI assumptions, backups/rollback; rehearsal ở sandbox riêng.

**Script:**

Nếu đổi monorepo sang polyrepo, nhiều đường dẫn và reference có thể thay đổi: commit history, tags, branch references, liên kết PR cũ, package name, quyền và build assumptions. Việc di chuyển file đơn thuần sẽ không lưu lại đủ ngữ cảnh lịch sử cho người bảo trì. Hãy lập inventory và một bản rehearsal tách biệt, đánh dấu dữ liệu không thể giữ nguyên chính xác. Chuẩn bị rollback và cửa sổ coexistence trước khi ngắt đường truy cập cũ. Lệnh filter Git cụ thể thuộc module Git, không phải bài quyết định topology này.

**Purpose:**

Người học phải nhận biết được Migration không chỉ move folder mà mang cả history và quyền từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Lộ trình chuyển đổi topology và bằng chứng đo kết quả

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:34–08:57`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Migration không chỉ move folder mà mang cả history và quyền"; mở khung phải cho "Pilot có baseline và điều kiện dừng rõ ràng", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Migration không chỉ move folder mà mang cả history và quyền. Nhưng chưa nên kết luận khi chưa kiểm tra Pilot có baseline và điều kiện dừng rõ ràng. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Pilot có baseline và điều kiện dừng rõ ràng, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Lộ trình chuyển đổi topology và bằng chứng đo kết quả

**Time:** `08:57–10:03`

**Visual:**

Vẽ timeline Baseline→Pilot API/Web→Compare→Rollout/Rollback; bảng PR lead time, contract defects, test feedback, owner review với ô measured/unknown.

**Script:**

Một migration không có baseline chỉ biến thành bài đổi tên repository đắt tiền. Với pilot gộp API và Web, hãy ghi PR lead time, số lỗi contract, thời gian test feedback và trải nghiệm người phát triển trước thay đổi. Sau đó giới hạn phạm vi thử, chỉ định owner, kiểm tra khả năng rollback và đo cùng loại bằng chứng sau pilot. Nếu coordination cải thiện mà test feedback chậm đi đáng kể, đội có thể cần đầu tư tooling hoặc dừng mở rộng. Số liệu trong video đều để trống đến khi có log thật.

**Purpose:**

Người học phải nhận biết được Pilot có baseline và điều kiện dừng rõ ràng từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Bằng chứng từ nghiên cứu monorepo quy mô lớn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:03–10:26`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Pilot có baseline và điều kiện dừng rõ ràng"; mở khung phải cho "Google 2016 và Jaspan 2018 là hai nguồn khác phương pháp", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Pilot có baseline và điều kiện dừng rõ ràng. Nhưng chưa nên kết luận khi chưa kiểm tra Google 2016 và Jaspan 2018 là hai nguồn khác phương pháp. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Google 2016 và Jaspan 2018 là hai nguồn khác phương pháp, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Bằng chứng từ nghiên cứu monorepo quy mô lớn

**Time:** `10:26–11:35`

**Visual:**

Hai research cards: Potvin & Levenberg 2016 custom-built source-control/tooling case; Jaspan et al 2018 survey engineers+tool logs multi-method comparison; đường phân biệt claim/scope.

**Script:**

Ở đây rất dễ kể sai câu chuyện 'Google làm monorepo nên Git bình thường cũng chịu hàng tỷ dòng mã'. Potvin và Levenberg năm 2016 mô tả kho chung cực lớn cùng hạ tầng quản lý source tùy biến của Google; đây là case về kiến trúc và đầu tư công cụ. Jaspan và cộng sự năm 2018 là nghiên cứu khác, khảo sát kỹ sư từng trải nghiệm hai loại repo và đối chiếu developer-tool logs. Họ ghi nhận lợi ích khám phá/reuse và chi phí access/tool flexibility. Không được dùng hai kết quả như một lời bảo đảm cho mọi đội.

**Purpose:**

Người học phải nhận biết được Google 2016 và Jaspan 2018 là hai nguồn khác phương pháp từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Hai tình huống hợp lý dẫn đến lựa chọn topology khác nhau

<!-- VIDEO_SECTION -->

### Transition

**Time:** `11:35–11:58`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Google 2016 và Jaspan 2018 là hai nguồn khác phương pháp"; mở khung phải cho "Startup phối hợp API/Web khác doanh nghiệp Fraud restricted", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Google 2016 và Jaspan 2018 là hai nguồn khác phương pháp. Nhưng chưa nên kết luận khi chưa kiểm tra Startup phối hợp API/Web khác doanh nghiệp Fraud restricted. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Startup phối hợp API/Web khác doanh nghiệp Fraud restricted, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Hai tình huống hợp lý dẫn đến lựa chọn topology khác nhau

**Time:** `11:58–13:04`

**Visual:**

Case A startup: API/Web co-change, same readers, fast tests → possible monorepo; Case B regulated firm Fraud restricted, vendors Web → hybrid/polyrepo; mỗi case có tiêu chí đảo lựa chọn.

**Script:**

Hãy đặt hai công ty giả định cạnh nhau. Startup có Web, API và Contracts thường thay cùng ticket, tất cả được đọc mã, nên một monorepo có thể làm review migration thuận tiện. Công ty có Fraud source bị hạn chế, vendor chỉ làm Web, có lý do mạnh để giữ ranh giới repo riêng hoặc topology lai. Không có quy tắc 'startup mono, enterprise poly' bất biến. Nếu startup phát sinh dữ liệu bí mật, quyết định có thể đổi; nếu công ty lớn xây được access/tooling phù hợp, cũng phải xem lại bằng chứng.

**Purpose:**

Người học phải nhận biết được Startup phối hợp API/Web khác doanh nghiệp Fraud restricted từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Ranh giới bàn giao build/CI, Git và kiến trúc service

<!-- VIDEO_SECTION -->

### Transition

**Time:** `13:04–13:28`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Startup phối hợp API/Web khác doanh nghiệp Fraud restricted"; mở khung phải cho "Kết quả chiến lược phải bàn giao yêu cầu kiểm chứng cho owner đúng", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Startup phối hợp API/Web khác doanh nghiệp Fraud restricted. Nhưng chưa nên kết luận khi chưa kiểm tra Kết quả chiến lược phải bàn giao yêu cầu kiểm chứng cho owner đúng. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Kết quả chiến lược phải bàn giao yêu cầu kiểm chứng cho owner đúng, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Ranh giới bàn giao build/CI, Git và kiến trúc service

**Time:** `13:28–14:37`

**Visual:**

Sơ đồ Repository Strategy ở giữa, bốn mũi tên Git history migration, Host permissions/review, Build/CI affected tests, Service architecture contracts/deployment. Đặt outcome metric từng mũi.

**Script:**

Sau quyết định, người phụ trách topology không cần tự viết tất cả Git command, CI job và kiến trúc service. Họ cần nêu rõ source boundary, owner, ràng buộc quyền, nhịp thay đổi và số liệu để kiểm chứng lợi ích. Nhóm Git nhận yêu cầu bảo toàn history, host admin cấu hình quyền/review, CI owner thiết kế affected tests, kiến trúc sư giữ contract và deployment. Nếu API/Web chung repo, nhiệm vụ trọng tâm vẫn là feedback đủ nhanh và consumer compatibility. Đó là cách biến bản quyết định thành trách nhiệm đo được mà không mở rộng sai phạm vi học.

**Purpose:**

Người học phải nhận biết được Kết quả chiến lược phải bàn giao yêu cầu kiểm chứng cho owner đúng từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.
