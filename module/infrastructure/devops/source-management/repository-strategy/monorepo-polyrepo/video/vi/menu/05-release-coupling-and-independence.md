---
video:
  url: ""
---

# Phụ thuộc khi thay đổi và mức độ độc lập phát hành

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

## Coupling khi sửa mã so với coupling khi phát hành

<!-- VIDEO_SECTION -->

### Scene 1 — Coupling khi sửa mã so với coupling khi phát hành

**Time:** `00:00–01:00`

**Visual:**

Dựng hai trục: cùng PR thay API/Web và hai đồng hồ release Web/API; ghi điều kiện interface backward compatible.

**Script:**

Nếu một yêu cầu làm đổi cả API và Web, đó là change coupling: hai vùng source cần được xét cùng nhau. Nhưng nếu API tạm chấp nhận field cũ lẫn mới, Web có thể triển khai sau mà không cần cùng thời điểm. Đó là release independence dựa vào contract. Ngược lại, dù mỗi service ở repo riêng, một breaking change có thể khiến chúng phải nâng đồng thời. Dùng hai trục thay đổi và phát hành để tránh kết luận từ số Git root.

**Purpose:**

Người học phải nhận biết được Cùng phải sửa source chưa chắc phải deploy chung từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Lợi ích và điều kiện của phát hành độc lập

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:00–01:23`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Cùng phải sửa source chưa chắc phải deploy chung"; mở khung phải cho "Bản vá một service không nhất thiết chờ toàn hệ thống", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Cùng phải sửa source chưa chắc phải deploy chung. Nhưng chưa nên kết luận khi chưa kiểm tra Bản vá một service không nhất thiết chờ toàn hệ thống. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Bản vá một service không nhất thiết chờ toàn hệ thống, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Lợi ích và điều kiện của phát hành độc lập

**Time:** `01:23–02:26`

**Visual:**

Đặt API hotfix nhỏ trên timeline thứ hai; Web release tuần sau, client cũ vẫn hoạt động theo ma trận tương thích.

**Script:**

Một lỗi ở API đôi khi cần vá khẩn mà Web chưa có bản mới. Nếu interface đủ ổn định, nhóm có thể triển khai API riêng và kiểm tra rằng client cũ vẫn được hỗ trợ. Tính độc lập như vậy giúp giảm thời gian chờ và giới hạn phạm vi rollout. Nhưng cũng phải quản lý trách nhiệm owner, contract tests và rollback của từng phần. Đừng xem monorepo là rào cản mặc định của phát hành riêng; hãy nhìn artifact, version chạy thực tế và ranh giới triển khai.

**Purpose:**

Người học phải nhận biết được Bản vá một service không nhất thiết chờ toàn hệ thống từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Khả năng phát hành độc lập từng thành phần trong monorepo

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:26–02:49`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Bản vá một service không nhất thiết chờ toàn hệ thống"; mở khung phải cho "Một repo, hai artifacts, hai lịch phát hành", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Bản vá một service không nhất thiết chờ toàn hệ thống. Nhưng chưa nên kết luận khi chưa kiểm tra Một repo, hai artifacts, hai lịch phát hành. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Một repo, hai artifacts, hai lịch phát hành, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Khả năng phát hành độc lập từng thành phần trong monorepo

**Time:** `02:49–03:56`

**Visual:**

Cùng `.git` root chứa Web và API; vẽ hai artifact IDs, Web v3.1 API v5.2, deployment timeline độc lập và contract status riêng.

**Script:**

Từ cùng một commit graph, Web và API có thể được build thành hai artifacts và chạy qua hai quy trình phát hành độc lập. Ví dụ API v5.2 đi trước trong khi Web vẫn v3.1, miễn hợp đồng cho phép. Một team monorepo cần xác định thay đổi nào tác động component nào và phiên bản nào tương thích, chứ không nhất thiết tạo một release toàn bộ mã nguồn. Nếu nhóm gặp khó vì mọi test phải chạy cùng nhau, đó có thể là vấn đề công cụ chọn phạm vi, không phải giới hạn của monorepo.

**Purpose:**

Người học phải nhận biết được Một repo, hai artifacts, hai lịch phát hành từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Phụ thuộc phát hành vẫn tồn tại trong polyrepo

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:56–04:18`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Một repo, hai artifacts, hai lịch phát hành"; mở khung phải cho "Hai repo vẫn bị buộc deploy cùng khi phá contract", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Một repo, hai artifacts, hai lịch phát hành. Nhưng chưa nên kết luận khi chưa kiểm tra Hai repo vẫn bị buộc deploy cùng khi phá contract. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Hai repo vẫn bị buộc deploy cùng khi phá contract, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Phụ thuộc phát hành vẫn tồn tại trong polyrepo

**Time:** `04:18–05:19`

**Visual:**

Sơ đồ `api.git`, `web.git` với API bỏ `totalAmount`; Web cũ không hiểu schema mới. Nối hai clocks bằng khóa dependency màu đỏ.

**Script:**

Tách API và Web vào hai kho không làm hợp đồng biến mất. Nếu API loại bỏ ngay trường mà Web cũ dùng, hai service có thể buộc phối hợp triển khai hoặc chấp nhận lỗi trong cửa sổ chuyển đổi. Đây là release coupling do thiết kế interface, không phải Git. Đặt PR merged của API riêng với deployed Web version để thấy mốc nào có rủi ro. Giải pháp có thể là hỗ trợ field cũ tạm thời, không nhất thiết gom hai kho lại.

**Purpose:**

Người học phải nhận biết được Hai repo vẫn bị buộc deploy cùng khi phá contract từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Rủi ro lịch phát hành khi thay đổi interface hoặc thư viện

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:19–05:41`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Hai repo vẫn bị buộc deploy cùng khi phá contract"; mở khung phải cho "Nâng AuthClient cần ma trận version thực chạy", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Hai repo vẫn bị buộc deploy cùng khi phá contract. Nhưng chưa nên kết luận khi chưa kiểm tra Nâng AuthClient cần ma trận version thực chạy. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Nâng AuthClient cần ma trận version thực chạy, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Rủi ro lịch phát hành khi thay đổi interface hoặc thư viện

**Time:** `05:41–06:44`

**Visual:**

Hiển thị Provider/AuthClient v2.1 với Web 1.9 và API 2.1, bên phải bảng backward compatibility, rollback scenario, unknown deployments.

**Script:**

Khi thư viện chung lên 2.1, một số consumer có thể đang dùng 1.9. Rủi ro phát hành đến từ giao diện cũ bị loại bỏ hoặc các bản API và Web không hiểu nhau, chứ không chỉ đến từ việc có nhiều repositories. Hãy kiểm tra các release thật và version dependency để biết môi trường nào cần hỗ trợ song song. Nếu một consumer chưa nâng, đừng xóa contract cũ sớm. Sơ đồ nêu trạng thái giả định, không phải báo cáo telemetry hay một deployment đã được chạy.

**Purpose:**

Người học phải nhận biết được Nâng AuthClient cần ma trận version thực chạy từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Phạm vi kiểm chứng và tiêu chí sẵn sàng phát hành

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:44–07:05`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Nâng AuthClient cần ma trận version thực chạy"; mở khung phải cho "Library-only check xanh chưa đủ cho consumer migration", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Nâng AuthClient cần ma trận version thực chạy. Nhưng chưa nên kết luận khi chưa kiểm tra Library-only check xanh chưa đủ cho consumer migration. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Library-only check xanh chưa đủ cho consumer migration, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Phạm vi kiểm chứng và tiêu chí sẵn sàng phát hành

**Time:** `07:05–08:08`

**Visual:**

Checklist AuthClient PR: library tests, API/Web contract tests, owner review, release risk; chỉ tô verified khi có report thật.

**Script:**

Validation boundary là những component và contract phải được kiểm tra sau một thay đổi. Nếu AuthClient đổi interface, chỉ thấy thư viện build thành công không chứng minh Web/API còn dùng được. Monorepo có thể xác định affected graph và chạy bài kiểm tương ứng; polyrepo cần kết quả kiểm thử chéo version qua các kho. Trước khi quyết định phát hành, nhóm nên ghi bản nào đã test, ai duyệt và rủi ro chưa biết. Đây là tiêu chí readiness, không phải hướng dẫn cấu hình Gradle hay pipeline.

**Purpose:**

Người học phải nhận biết được Library-only check xanh chưa đủ cho consumer migration từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Đánh đổi giữa nhịp phát hành khác nhau và chi phí phối hợp

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:08–08:30`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Library-only check xanh chưa đủ cho consumer migration"; mở khung phải cho "Nhịp mobile, API, library khác nhau nên cần stable contract", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Library-only check xanh chưa đủ cho consumer migration. Nhưng chưa nên kết luận khi chưa kiểm tra Nhịp mobile, API, library khác nhau nên cần stable contract. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Nhịp mobile, API, library khác nhau nên cần stable contract, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Đánh đổi giữa nhịp phát hành khác nhau và chi phí phối hợp

**Time:** `08:30–09:35`

**Visual:**

Ba lane Mobile monthly, API daily, Library quarterly; đánh dấu giai đoạn hỗ trợ song song và thời gian thông báo deprecation.

**Script:**

Một app mobile có thể cập nhật theo tháng, API cập nhật mỗi ngày và thư viện chung theo chu kỳ khác. Nếu yêu cầu tất cả phát hành cùng lúc chỉ vì source chung repo, nhóm tạo nút thắt không cần thiết. Nếu tách kho nhưng API thay đổi phá tương thích ngay, client mobile vẫn gặp lỗi. Hãy đưa lịch release thực tế và hợp đồng hỗ trợ lên cùng timeline. Thông báo deprecation, giai đoạn tương thích và người chịu trách nhiệm consumer quan trọng hơn một quy ước tổ chức thư mục.

**Purpose:**

Người học phải nhận biết được Nhịp mobile, API, library khác nhau nên cần stable contract từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Tình huống đối chiếu phát hành phối hợp và độc lập

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:35–09:59`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Nhịp mobile, API, library khác nhau nên cần stable contract"; mở khung phải cho "Hai phiên bản API: mở rộng an toàn hay bỏ field đột ngột", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Nhịp mobile, API, library khác nhau nên cần stable contract. Nhưng chưa nên kết luận khi chưa kiểm tra Hai phiên bản API: mở rộng an toàn hay bỏ field đột ngột. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Hai phiên bản API: mở rộng an toàn hay bỏ field đột ngột, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Tình huống đối chiếu phát hành phối hợp và độc lập

**Time:** `09:59–11:03`

**Visual:**

Case A API thêm `amount` nhưng giữ `totalAmount`, Web nâng sau; Case B API bỏ ngay field cũ khiến Web lỗi dù repo riêng.

**Script:**

Ta xem cùng một yêu cầu sửa tax calculation. Trong case A, API thêm field mới nhưng vẫn đọc và trả field cũ trong thời gian chuyển tiếp; Web có thể nâng tuần sau, nên monorepo vẫn phát hành độc lập. Case B xóa totalAmount tức thì trong khi Web cũ còn dùng; ngay cả polyrepo cũng phải deploy phối hợp để tránh lỗi. Hãy nhìn ma trận client/server và release timestamp để chọn phương án an toàn. Không cần gán tốc độ deploy hay tỷ lệ lỗi giả cho hai trường hợp.

**Purpose:**

Người học phải nhận biết được Hai phiên bản API: mở rộng an toàn hay bỏ field đột ngột từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.
