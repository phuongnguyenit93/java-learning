---
video:
  url: ""
---

# Phối hợp thay đổi xuyên thành phần và nhiều repository

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

## Thay đổi xuyên thành phần: khái niệm và tình huống cơ bản

<!-- VIDEO_SECTION -->

### Scene 1 — Thay đổi xuyên thành phần: khái niệm và tình huống cơ bản

**Time:** `00:00–01:02`

**Visual:**

Vẽ TAX-42 thay `totalAmount→amount`; tô `/contracts`, `/api` và `/web`, bên dưới là hai cửa sổ API response trước/sau không phải log thật.

**Script:**

Nếu schema đổi tên một field mà Web còn dùng tên cũ, từng PR vẫn có thể được review đúng trong repo của mình nhưng cả hệ thống thất bại khi ghép. Đây là thay đổi xuyên thành phần: kết quả đúng cần nhiều bên cập nhật hoặc duy trì hợp đồng tương thích. Hãy để Web gọi thử theo hai phiên bản giao diện trên storyboard, không hiển thị kết quả chạy thật. Repository topology quyết định đường phối hợp, không loại bỏ nhu cầu thống nhất giao diện.

**Purpose:**

Người học phải nhận biết được Một ticket đổi đồng thời schema, API và Web từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Nguồn gốc chi phí phối hợp khi thay đổi xuyên nhóm

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:02–01:25`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Một ticket đổi đồng thời schema, API và Web"; mở khung phải cho "Thời gian chờ xuất hiện giữa owner và phiên bản", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Một ticket đổi đồng thời schema, API và Web. Nhưng chưa nên kết luận khi chưa kiểm tra Thời gian chờ xuất hiện giữa owner và phiên bản. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Thời gian chờ xuất hiện giữa owner và phiên bản, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Nguồn gốc chi phí phối hợp khi thay đổi xuyên nhóm

**Time:** `01:25–02:26`

**Visual:**

Dựng timeline Contracts PR→publish 2.1→API PR→Web adoption; tô waiting blocks cạnh mỗi reviewer và mốc phát hành, so với một PR chung cần nhiều owners.

**Script:**

Chi phí phối hợp không chỉ là số dòng sửa. Với ba repos, nhóm cần chờ phiên bản library, PR của API và Web, rồi kiểm tra kết hợp. Một monorepo có thể thấy cả diff cùng một chỗ, nhưng vẫn phải đợi review hoặc build trên những vùng có chủ sở hữu khác nhau. Đừng tự đặt con số lead time để minh họa tốt hơn; hãy chỉ ra thời điểm bắt đầu, chờ và kết thúc mà đội có thể đo từ lịch sử PR thật.

**Purpose:**

Người học phải nhận biết được Thời gian chờ xuất hiện giữa owner và phiên bản từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Lịch sử chung hỗ trợ quan sát và review thay đổi đa thành phần

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:26–02:48`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Thời gian chờ xuất hiện giữa owner và phiên bản"; mở khung phải cho "Một PR đa vùng cho thấy consumer migration", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Thời gian chờ xuất hiện giữa owner và phiên bản. Nhưng chưa nên kết luận khi chưa kiểm tra Một PR đa vùng cho thấy consumer migration. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Một PR đa vùng cho thấy consumer migration, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Lịch sử chung hỗ trợ quan sát và review thay đổi đa thành phần

**Time:** `02:48–03:49`

**Visual:**

Hiện diff chung TAX-42 có `contracts/schema.json`, `api/Dto.java`, `web/client.ts`, ba reviewer lanes và test-coverage checklist chưa đánh dấu pass.

**Script:**

Trong monorepo, một PR có thể sửa contract và các consumer cùng nhau, nên người duyệt nhìn được chuỗi hệ quả ở một điểm lịch sử. Từ diff hãy kiểm tra Web đã đọc field mới chưa, API còn chấp nhận client cũ không và những test nào cần chạy. Đây là lợi ích quan sát và phối hợp cập nhật của shared history. Nhưng một PR chứa cả ba file không tự bảo đảm đã chạy test đúng hay đã có đầy đủ domain owner đồng ý.

**Purpose:**

Người học phải nhận biết được Một PR đa vùng cho thấy consumer migration từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Phối hợp các thay đổi liên quan qua nhiều repository độc lập

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:49–04:11`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Một PR đa vùng cho thấy consumer migration"; mở khung phải cho "Ba PR độc lập cần một liên kết truy vết", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Một PR đa vùng cho thấy consumer migration. Nhưng chưa nên kết luận khi chưa kiểm tra Ba PR độc lập cần một liên kết truy vết. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Ba PR độc lập cần một liên kết truy vết, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Phối hợp các thay đổi liên quan qua nhiều repository độc lập

**Time:** `04:11–05:14`

**Visual:**

Vẽ ticket TAX-42 nối `contracts#42`, `api#81`, `web#17`; mỗi PR có branch, reviewer và merge timestamp riêng, không có biểu tượng commit nguyên tử.

**Script:**

Trong polyrepo, một yêu cầu có thể tạo ba PR với lịch sử và quyền phê duyệt khác nhau. Để tránh thất lạc, hãy giữ ticket chung và bảng từng repo, PR, owner, version được phát hành và consumer đang dùng. Không có giao dịch Git mặc định nào gộp ba commit thuộc ba repositories độc lập thành một commit nguyên tử. Khi một PR chậm hơn, cần giao diện tương thích trong giai đoạn chuyển tiếp. Hãy kiểm tra chuỗi liên kết trước khi tuyên bố yêu cầu hoàn tất.

**Purpose:**

Người học phải nhận biết được Ba PR độc lập cần một liên kết truy vết từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Thứ tự tích hợp và yêu cầu tương thích giữa các thành phần

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:14–05:36`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Ba PR độc lập cần một liên kết truy vết"; mở khung phải cho "Expand–migrate–contract kiểm soát thứ tự tương thích", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Ba PR độc lập cần một liên kết truy vết. Nhưng chưa nên kết luận khi chưa kiểm tra Expand–migrate–contract kiểm soát thứ tự tương thích. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Expand–migrate–contract kiểm soát thứ tự tương thích, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Thứ tự tích hợp và yêu cầu tương thích giữa các thành phần

**Time:** `05:36–06:38`

**Visual:**

Timeline contract chấp nhận `totalAmount`+`amount` → Web chuyển sang field mới → xóa field cũ sau telemetry; vẽ bảng old/new consumer×API.

**Script:**

Một thay đổi phá tương thích có thể khiến Polyrepo phải triển khai cùng lúc, và Monorepo cũng không cứu được client đã phát hành. Ta dùng hướng an toàn: API tạm chấp nhận cả field cũ và mới, nâng các consumer rồi mới xóa đường cũ sau khi có bằng chứng không còn dùng. Đây là chiến lược phối hợp giao diện, không phải lệnh CI trong module này. Kiểm tra ma trận client/server supported versions thay vì xem hai PR merge gần nhau là đã an toàn.

**Purpose:**

Người học phải nhận biết được Expand–migrate–contract kiểm soát thứ tự tương thích từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Phân công review và lưu giữ ngữ cảnh khi thay đổi liên nhóm

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:38–06:59`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Expand–migrate–contract kiểm soát thứ tự tương thích"; mở khung phải cho "Một PR đa vùng không thay được domain owners", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Expand–migrate–contract kiểm soát thứ tự tương thích. Nhưng chưa nên kết luận khi chưa kiểm tra Một PR đa vùng không thay được domain owners. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Một PR đa vùng không thay được domain owners, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Phân công review và lưu giữ ngữ cảnh khi thay đổi liên nhóm

**Time:** `06:59–07:59`

**Visual:**

Dán thẻ owner Contracts, API, Web lên diff; nếu chỉ Web review, vẽ lỗ hổng ở rule validation của API và bản contract.

**Script:**

Review không chỉ đếm approvals. Với TAX-42, reviewer Web hiểu luồng hiển thị, người API hiểu producer và owner Contracts giữ sự tương thích của giao diện. Monorepo có thể gom họ vào một PR, còn polyrepo có thể yêu cầu review ở ba PR. Cả hai đều cần một người nối bối cảnh yêu cầu ban đầu với contract. Nếu thiếu chủ thể chịu trách nhiệm cho đường giao diện, hãy ghi blocker thay vì cho rằng nhiều dấu Approve đồng nghĩa đã đủ.

**Purpose:**

Người học phải nhận biết được Một PR đa vùng không thay được domain owners từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Truy vết yêu cầu và thay đổi xuyên nhiều thành phần

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:59–08:23`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Một PR đa vùng không thay được domain owners"; mở khung phải cho "Một yêu cầu phải truy được version và kết quả test liên quan", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Một PR đa vùng không thay được domain owners. Nhưng chưa nên kết luận khi chưa kiểm tra Một yêu cầu phải truy được version và kết quả test liên quan. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Một yêu cầu phải truy được version và kết quả test liên quan, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Truy vết yêu cầu và thay đổi xuyên nhiều thành phần

**Time:** `08:23–09:27`

**Visual:**

Dựng evidence chain TAX-42 → PR(s) → commit SHA(s) → contract v2.1 → API/Web installed versions → test evidence; để ô production unknown.

**Script:**

Traceability cho phép người đến sau đi từ lỗi hoặc nhu cầu đến tất cả thay đổi có liên quan, kể cả consumer chưa nâng phiên bản. Ở monorepo có thể bắt đầu từ một PR đa vùng; ở polyrepo cần liên kết những PR riêng. Nhưng cả hai đều phải biết version nào đang dùng trong release và test nào đánh giá chúng. Nếu production lỗi, chuỗi này giúp phân biệt source chưa merge, consumer chưa deploy hay contract không tương thích. Đừng dùng trạng thái issue Done làm bằng chứng duy nhất.

**Purpose:**

Người học phải nhận biết được Một yêu cầu phải truy được version và kết quả test liên quan từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Dấu hiệu xung đột nhóm và phụ thuộc chưa giải quyết

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:27–09:51`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Một yêu cầu phải truy được version và kết quả test liên quan"; mở khung phải cho "Dấu hiệu nghẽn không mặc định do topology", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Một yêu cầu phải truy được version và kết quả test liên quan. Nhưng chưa nên kết luận khi chưa kiểm tra Dấu hiệu nghẽn không mặc định do topology. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Dấu hiệu nghẽn không mặc định do topology, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Dấu hiệu xung đột nhóm và phụ thuộc chưa giải quyết

**Time:** `09:51–10:56`

**Visual:**

Bảng điều tra: PR waiting for owner, broken consumer version, rollback, cross-version error. Mỗi hàng có cột nghi vấn contract, ownership, CI, topology.

**Script:**

Nhìn vào ticket TAX-42 bị chậm, ta cần tách triệu chứng khỏi nguyên nhân. PR chờ reviewer có thể do owner quá tải, lỗi giữa Web và API do thay đổi interface phá tương thích, còn test chậm do công cụ. Cả monorepo lẫn polyrepo đều có thể gặp chúng. Hãy lấy lead time và liên kết PR thật, hỏi nhóm tác động, rồi phân loại nghẽn theo quyền, dependency và quy trình. Nếu vấn đề chính là contract thiếu ổn định, đổi topology có thể tạo thêm chi phí mà không giải quyết lỗi.

**Purpose:**

Người học phải nhận biết được Dấu hiệu nghẽn không mặc định do topology từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.

## Tình huống phối hợp thay đổi xuyên ba thành phần

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:56–11:18`

**Visual:**

Giữ ở nửa trái màn hình dấu vết "Dấu hiệu nghẽn không mặc định do topology"; mở khung phải cho "Một schema địa chỉ giao hàng qua ba component", tô mũi tên từ bằng chứng vừa có sang điều cần chứng minh tiếp.

**Script:**

Chúng ta vừa nhận ra Dấu hiệu nghẽn không mặc định do topology. Nhưng chưa nên kết luận khi chưa kiểm tra Một schema địa chỉ giao hàng qua ba component. Hãy đổi góc nhìn và xem bằng chứng cụ thể.

**Purpose:**

Nối vấn đề của phần trước với tiêu chí Một schema địa chỉ giao hàng qua ba component, tránh biến bài học thành các định nghĩa rời rạc.

### Scene 1 — Tình huống phối hợp thay đổi xuyên ba thành phần

**Time:** `11:18–12:23`

**Visual:**

Tạo hai board: monorepo một PR Schema/API/Web cần nhiều owners; polyrepo schema PR→version→API PR→Web PR. Gắn rủi ro rollback ở cả hai.

**Script:**

Tình huống đổi địa chỉ giao hàng chạm Schema, Checkout API và Web. Trong monorepo nhóm có thể thấy toàn bộ migration trong một diff, nhưng PR lớn có thể chờ nhiều owner. Trong polyrepo, Schema phát hành phiên bản tương thích rồi API và Web nâng theo lịch riêng; ngược lại nguy cơ thiếu liên kết PR cao hơn. Ta so bốn bằng chứng: reviewer coverage, version matrix, contract test và rollback plan. Không cần số liệu được dựng giả; quyết định nên căn cứ vào thay đổi lặp lại trên hệ thống thật.

**Purpose:**

Người học phải nhận biết được Một schema địa chỉ giao hàng qua ba component từ dấu vết trên màn hình và tách kết luận chiến lược khỏi giả định về topology.
