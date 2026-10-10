---
video:
  url: ""
---

# Lựa chọn AOP: Lợi ích, rủi ro và tình huống áp dụng

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


## Giảm lặp và tập trung chính sách xuyên suốt

<!-- VIDEO_SECTION -->

### Scene 1 — Giảm lặp và tập trung chính sách xuyên suốt

**Time:** `00:00–01:07`

**Visual:**

Thay ba khối timer lặp lại bằng một thẻ ServiceTiming; ba quy trình nghiệp vụ vẫn giữ nguyên; hiện dòng "một chính sách, nhiều điểm".

**Script:**

Khi một chính sách đo thống nhất áp dụng cho nhiều dịch vụ, thay đổi cách ghi thời lượng ở một nơi có thể giúp cả hệ thống dùng chung. Đó là lợi ích rất thực. Nhưng nó chỉ bền khi tiêu chí chọn điểm rõ ràng và cả đội biết nơi tìm hành vi bổ sung. Gom hết vào một chỗ mà không ai hiểu thì không tự biến chương trình thành dễ bảo trì.

**Purpose:**

Đánh giá lợi ích giảm lặp với điều kiện coverage có thể kiểm tra.


## Hành vi ngầm, độ phụ thuộc và chi phí truy vết

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:07–01:19`

**Visual:**

Từ ba đồng hồ trùng nhau đã được gộp thành ServiceTiming, mở đồng thời source transferFunds không có timer và runtime trace vẫn ghi thời gian.

**Script:**

Tập trung chính sách giúp giảm lặp, nhưng người đọc target không còn thấy toàn bộ hành vi; hãy nhìn cái giá này.

**Purpose:**

Cho người xem thấy lợi ích giảm lặp đi kèm chi phí tìm hành vi ngầm ngoài source target.

### Scene 1 — Hành vi ngầm, độ phụ thuộc và chi phí truy vết

**Time:** `01:19–02:26`

**Visual:**

Đặt source transferFunds cạnh runtime trace có timer, audit; vẽ nét đứt từ tên hàm sang pointcut khớp ngầm.

**Script:**

Người đọc hàm chuyển tiền có thể không thấy đồng hồ hay việc ghi audit, nhưng hai thứ ấy vẫn chạy thật. Khi debug, chỉ nhìn method body là chưa đủ: còn phải tìm aspect nào khớp và caller có đi qua proxy hay weaving không. Ngay cả đổi tên phương thức cũng có thể làm chính sách chọn điểm bị lệch. Ít dòng code hơn không đồng nghĩa đường thực thi dễ hiểu hơn.

**Purpose:**

Thể hiện chi phí coupling ẩn và quy trình điều tra một lỗi AOP.


## Mối quan tâm xuyên suốt phù hợp để tách thành aspect

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:26–02:38`

**Visual:**

Giữ runtime trace có timer vô hình; trích hai thẻ timing, correlation ID và một thẻ approve transfer, đưa vào bảng tiêu chí phụ thuộc nghiệp vụ.

**Script:**

Nếu có cái giá như vậy, chúng ta phải chọn thật kỹ concern nào xứng đáng dùng aspect.

**Purpose:**

Chuyển từ chi phí hành vi ngầm sang cách chọn concern xứng đáng với sự gián tiếp này.

### Scene 1 — Mối quan tâm xuyên suốt phù hợp để tách thành aspect

**Time:** `02:38–03:45`

**Visual:**

Ba thẻ timing, correlation ID, kiểm tra hạn mức tài khoản; chuyển hai thẻ đầu sang "shared policy", thẻ cuối sang "business contract".

**Script:**

Đo thời gian hoặc gắn mã tương quan thường có mục tiêu tương tự trên nhiều thao tác độc lập. Chúng phù hợp khi có một quy tắc chọn điểm gọn và test được. Nhưng điều kiện hạn mức chuyển tiền phụ thuộc nghiệp vụ của giao dịch, không nên bị che một cách tùy tiện. Với chính sách bảo mật, việc chọn thiếu một điểm còn nguy hiểm, nên coverage phải được kiểm tra cẩn thận chứ không chỉ tin vào tên hàm.

**Purpose:**

Xây tiêu chí chọn concern bằng kiểm chứng thực tế và rủi ro bảo mật.


## Luồng nghiệp vụ cần giữ tường minh

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:45–03:57`

**Visual:**

Từ thẻ approve transfer nằm ngoài nhóm aspect, triển khai tuyến bước check → reserve → ledger → confirm; đặt timer chỉ ở vòng ngoài.

**Script:**

Câu chuyện chuyển tiền cho ta ví dụ đối lập: những bước nào nhất thiết phải giữ tường minh?

**Purpose:**

Giữ những quyết định di chuyển tiền ở luồng nghiệp vụ tường minh.

### Scene 1 — Luồng nghiệp vụ cần giữ tường minh

**Time:** `03:57–05:04`

**Visual:**

Tuyến nghiệp vụ kiểm tra → giữ hạn mức → ghi sổ → xác nhận. Bên cạnh là phương án xấu: các bước lẫn trong ba advice khó nhìn thứ tự.

**Script:**

Một giao dịch chuyển tiền có thứ tự và cách xử lý thất bại rất quan trọng. Nếu phân tán việc trừ tiền, cập nhật sổ và bù trừ qua nhiều advice, người đọc khó thấy toàn bộ tác động thực sự. Chúng ta vẫn có thể đặt đồng hồ ở vòng ngoài, nhưng thuật toán chuyển tiền và những quyết định nghiệp vụ phải có chủ sở hữu rõ ràng trong code chính.

**Purpose:**

Giữ an toàn luồng nghiệp vụ trước mong muốn loại bỏ mọi mã trùng.


## Ranh giới paradigm với framework và công nghệ AOP

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:04–05:16`

**Visual:**

Giữ pipeline nghiệp vụ bên trái và vòng timing bên phải; đặt nhãn hai vùng paradigm: reason about flow và framework: configure proxy/weaver.

**Script:**

Bài này giúp ta ra quyết định ở mức paradigm, còn cấu hình cơ chế chạy thật cần học ở đúng module.

**Purpose:**

Phân biệt đường suy luận AOP với kỹ thuật triển khai Spring/AspectJ mà video không dạy.

### Scene 1 — Ranh giới paradigm với framework và công nghệ AOP

**Time:** `05:16–06:23`

**Visual:**

Biển chỉ dẫn: "Ở đây: mô hình và trace"; "Học tiếp: Spring bean/proxy, AspectJ weaver, giao dịch". Không đặt URL API giả.

**Script:**

Chúng ta đã hiểu aspect, pointcut và các đường chạy có thể xảy ra. Nhưng chưa cấu hình compiler AspectJ hoặc Spring proxy trong bài paradigm này. Annotation Spring không tự bảo đảm advice đang chạy; trace AOP cũng không tự mang lại giao dịch nguyên tử. Khi học sâu công nghệ, hãy mang theo ba câu hỏi: framework nhìn thấy những điểm nào, quy tắc chọn có khớp không và caller đi qua ranh giới gì?

**Purpose:**

Bảo vệ ranh giới kỹ thuật và hướng học tiếp không tạo thêm prerequisite giả.


## Pointcut quá rộng, advice chồng lấn và thứ tự khó dự đoán

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:23–06:35`

**Visual:**

Giữ vùng framework và biểu thức save*; cho nó tô nhầm saveDraft nhưng bỏ sót persistOrder, thêm hai vòng advice lồng nhau.

**Script:**

Trước khi chốt thiết kế, thử làm sai pointcut một cách có chủ đích để thấy hậu quả.

**Purpose:**

Biến lời cảnh báo về cấu hình thành phép kiểm chứng mẫu chọn quá rộng/quá hẹp.

### Scene 1 — Pointcut quá rộng, advice chồng lấn và thứ tự khó dự đoán

**Time:** `06:35–07:42`

**Visual:**

Bảng saveOrder, saveDraft, persistOrder, formatMoney; mẫu save* tô cả saveDraft nhưng bỏ persistOrder. Chồng hai vòng quanh lên một transfer.

**Script:**

Giả sử pointcut chọn mọi tên bắt đầu bằng save. Nó có thể audit nhầm saveDraft nhưng lại bỏ sót persistOrder. Nếu hai around advice cùng khớp, thứ tự lồng nhau còn quyết định lớp nào thấy ngoại lệ ở thời điểm nào. Đừng chỉ kiểm tra biểu thức có hợp lệ không. Hãy thử cả khớp, không khớp, ném lỗi và thứ tự advice trong chính mô hình chạy thật.

**Purpose:**

Chỉ ra lỗi quá rộng, quá hẹp và advice lồng nhau bằng những ca kiểm chứng.


## Từ yêu cầu logging hoặc auditing đến quyết định dùng aspect

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:42–07:54`

**Visual:**

Từ bảng hai sai lệch saveDraft/persistOrder, thay bằng ma trận yêu cầu MATCH/NO MATCH và success/exception; hiển thị log chỉ có operationId và elapsed.

**Script:**

Giờ ta kết hợp yêu cầu ban đầu thành một chính sách đo thời gian có thể đánh giá từ đầu đến cuối.

**Purpose:**

Đưa người xem từ ca pointcut sai sang thiết kế chính sách có thể test và che dữ liệu nhạy cảm.

### Scene 1 — Từ yêu cầu logging hoặc auditing đến quyết định dùng aspect

**Time:** `07:54–09:01`

**Visual:**

Storyboard ba thao tác dịch vụ, danh sách điểm chọn, trace thành công rồi trace exception màu đỏ. Chỉ hiển thị operation ID và elapsed, che số tài khoản.

**Script:**

Giả sử ba dịch vụ cần đo thời gian, kể cả khi thất bại, nhưng tuyệt đối không ghi số tài khoản. Ta tách chính sách đo, xác định những operation được chọn và dùng advice có nhánh ghi thời gian cho cả trường hợp ném lỗi. Sau đó thử một lời gọi thành công, một lời gọi lỗi và một thao tác không được chọn. Nếu thiếu bản ghi khi lỗi hoặc có dữ liệu nhạy cảm, thiết kế aspect vẫn chưa đạt yêu cầu.

**Purpose:**

Đưa mô hình AOP thành checklist có thể kiểm chứng, bao gồm redaction và xử lý exception.

### Scene 2 — Kiểm chứng trường hợp khó

**Time:** `09:01–09:54`

**Visual:**

Đặt bảng chấp nhận: matched success có timing; matched exception vẫn timing và lỗi gốc; unmatched không timing; log không có số tài khoản.

**Script:**

Bảng này biến yêu cầu thành phép kiểm tra có đáp án. Không cần dựng một HTTP endpoint giả để xem trace của lời gọi. Ta cần biết thao tác được chọn có nhận đúng chính sách không, thao tác ngoài phạm vi có còn nguyên không, và lỗi có giữ đúng ý nghĩa không. Chỉ xem ca thành công là chưa đủ để chứng minh thiết kế đạt yêu cầu.

**Purpose:**

Chuyển storyboard thành tiêu chí pass/fail kiểm chứng phạm vi, lỗi và thông tin nhạy cảm.


## So sánh quyết định cuối cùng với helper, wrapper và decorator

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:54–10:06`

**Visual:**

Giữ bảng acceptance của timing với success, failure và unmatched; đặt cạnh bốn lựa chọn helper, wrapper, decorator và aspect, đánh dấu chi phí mỗi cách.

**Script:**

Cuối cùng, hãy hỏi liệu chính sách này có đáng phức tạp hơn một wrapper hay không.

**Purpose:**

Dùng bằng chứng của chính sách vừa thử làm cơ sở quyết định dùng aspect hay giải pháp tường minh.

### Scene 1 — So sánh quyết định cuối cùng với helper, wrapper và decorator

**Time:** `10:06–11:13`

**Visual:**

Bảng bốn cột helper, wrapper, decorator, aspect; đánh dấu số điểm gọi, mức tường minh, khả năng kiểm thử, độ ổn định của rule; kết thúc ở hình transferFunds.

**Script:**

Nếu chỉ hai đường xử lý cần đo, một wrapper dễ đọc có thể là lựa chọn tốt nhất. Nếu hàng trăm thao tác cùng chịu một chính sách độc lập, aspect có thể tiết kiệm rất nhiều công việc lặp lại. Đừng chọn chỉ vì số dòng code ít đi. Hãy so phạm vi áp dụng, kết quả và ngoại lệ, khả năng debug, và mức độ quan trọng của luồng nghiệp vụ. Nhớ điều cốt lõi: tách chính sách xuyên suốt, nhưng đừng giấu những hành động thực sự làm tiền di chuyển.

**Purpose:**

Kết luận bằng tiêu chí lựa chọn dựa trên bằng chứng thay vì khẩu hiệu AOP luôn tốt hơn.
