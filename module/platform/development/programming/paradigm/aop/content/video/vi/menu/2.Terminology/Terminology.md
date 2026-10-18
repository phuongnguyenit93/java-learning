---
video:
  url: ""
---

# Mô hình cốt lõi của AOP: Khía cạnh và điểm thực thi

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


## Aspect: Đơn vị mô-đun hóa mối quan tâm xuyên suốt

<!-- VIDEO_SECTION -->

### Scene 1 — Aspect: Đơn vị mô-đun hóa mối quan tâm xuyên suốt

**Time:** `00:00–01:07`

**Visual:**

Một thẻ ServiceTiming chia làm hai ngăn "quy tắc chọn" và "hành vi đo"; đặt phía trên các thẻ transfer, invoice, inventory.

**Script:**

Ta có thể hình dung aspect là nơi đóng gói một mối quan tâm xuyên suốt. Ví dụ ServiceTiming chịu trách nhiệm về chính sách đo thời gian và cách xác định thao tác cần đo. Nó không chịu trách nhiệm trừ tiền, lưu đơn hay kiểm tra kho. Nếu aspect bắt đầu tự quyết định các nhánh nghiệp vụ, ta đã kéo nhầm trách nhiệm sang nơi không thuộc về nó.

**Purpose:**

Định nghĩa aspect bằng vai trò mô-đun hóa, không đồng nhất với annotation hoặc phương thức.


## Join point: Điểm có ý nghĩa trong mô hình thực thi

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:07–01:19`

**Visual:**

Trên thẻ ServiceTiming vừa có policy và timer, dựng timeline gọi transferFunds; đặt marker phương thức, khởi tạo và truy cập trường trên đúng các điểm tương ứng.

**Script:**

Đã đặt tên nơi giữ chính sách chung, ta cần biết những điểm thực thi nào có thể được cơ chế AOP quan sát.

**Purpose:**

Cho thấy aspect đang được mô tả cần những điểm thực thi đủ điều kiện.

### Scene 1 — Join point: Điểm có ý nghĩa trong mô hình thực thi

**Time:** `01:19–02:26`

**Visual:**

Trên timeline hiện ba dấu: thực thi phương thức, khởi tạo đối tượng, cập nhật trường. Vùng Spring proxy tô riêng ở method execution; AspectJ có vùng rộng hơn.

**Script:**

Join point là một điểm có ý nghĩa trong mô hình thực thi mà công nghệ AOP có thể nhận diện. Với Spring AOP, chúng ta thường nói đến lần thực thi phương thức đi qua proxy. Trong mô hình AspectJ có thể có thêm loại điểm khác. Vì vậy đừng mặc định cứ gán trường hay tạo object là mọi framework đều nhìn thấy. Trước hết phải hỏi công nghệ triển khai hỗ trợ loại join point nào.

**Purpose:**

Chỉ rõ join point là tập điểm đủ điều kiện, không phải quy tắc lựa chọn.


## Pointcut: Quy tắc lựa chọn join point

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:26–02:38`

**Visual:**

Giữ các marker join point của timeline; kéo khung lọc qua transferFunds và refund, để formatMoney ở ngoài cùng nhãn NO MATCH.

**Script:**

Có nhiều điểm có thể quan sát không có nghĩa chính sách của chúng ta phải chọn tất cả.

**Purpose:**

Biến tập điểm có thể quan sát thành bài toán chọn một tập con bằng pointcut.

### Scene 1 — Pointcut: Quy tắc lựa chọn join point

**Time:** `02:38–03:45`

**Visual:**

Bảng ba hàm transferFunds(), refund(), formatMoney(); hai dòng đầu phủ màu MATCH, dòng cuối NO MATCH. Tô khung biểu thức chọn chứ không tô thân hàm.

**Script:**

Pointcut là quy tắc chọn trong số các join point mà cơ chế hỗ trợ. Ví dụ chính sách đo thời gian chọn transferFunds và refund, còn formatMoney thì không. Điểm rất quan trọng là phải thử cả trường hợp khớp và không khớp. Một mẫu tên quá rộng có thể vô tình làm log thêm hàng trăm thao tác không liên quan.

**Purpose:**

Dạy pointcut như một phép chọn có thể kiểm thử, không phải đoạn code thực hiện timing.


## Advice: Hành vi bổ sung tại điểm được chọn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:45–03:57`

**Visual:**

Giữ bảng MATCH/NO MATCH; đặt biểu tượng timer chỉ bên cạnh hai hàng xanh rồi vẽ mũi tên trước và sau một lời gọi.

**Script:**

Quy tắc vừa cho chúng ta biết ở đâu. Còn làm gì tại điểm đó là chuyện khác.

**Purpose:**

Phân tách bước chọn điểm khỏi hành vi advice thực hiện tại điểm được chọn.

### Scene 1 — Advice: Hành vi bổ sung tại điểm được chọn

**Time:** `03:57–05:04`

**Visual:**

Gắn ô START TIMER trước và LOG ELAPSED sau transferFunds ở các dòng MATCH; phần code bên trong target giữ nguyên.

**Script:**

Advice là hành vi bổ sung ở điểm đã được chọn. Trong ví dụ này, advice bắt đầu đo, cho nghiệp vụ chạy rồi ghi thời lượng. Nếu chính sách chỉ yêu cầu đo thời gian, nó không nên tự đổi kết quả chuyển tiền hoặc nuốt ngoại lệ. Nhớ tách hai câu hỏi: pointcut chọn điểm nào, còn advice thực sự thực hiện hành vi gì.

**Purpose:**

Giữ rõ selection versus action và yêu cầu bảo toàn kết quả/lỗi nếu không có chính sách khác.


## Target: Thành phần hoặc hành vi gốc

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:04–05:16`

**Visual:**

Tách bảng đã gắn timer thành hai lớp: hộp TransferService giữ code chuyển tiền ở giữa, timer nằm phía ngoài; thử đường test target riêng.

**Script:**

Và tất nhiên, trong sơ đồ vẫn còn phần thực hiện công việc chính. Hãy gọi tên nó.

**Purpose:**

Xác nhận phần bị gắn advice vẫn sở hữu bất biến nghiệp vụ và có thể kiểm thử độc lập.

### Scene 1 — Target: Thành phần hoặc hành vi gốc

**Time:** `05:16–06:23`

**Visual:**

Tách hai lớp màu: ở giữa là mã transferFunds và điều kiện nghiệp vụ; vòng ngoài là timer. Trên màn hình để một dòng test target độc lập và một dòng test advised call.

**Script:**

Target là thành phần hoặc hành vi gốc được bổ sung advice. TransferService vẫn xử lý logic chuyển tiền và những điều kiện hợp lệ. Chúng ta có thể kiểm thử riêng logic ấy, rồi kiểm thử đường gọi thật đã được aspect can thiệp để xem chính sách có chạy đúng hay không. Hai phép kiểm tra này trả lời hai câu hỏi khác nhau, nên không thể lấy test target đơn lẻ làm bằng chứng AOP hoạt động.

**Purpose:**

Định vị target trong sơ đồ và tách đúng kiểm thử nghiệp vụ khỏi kiểm thử hành vi hiệu lực.


## Weaving: Ghép aspect với target

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:23–06:35`

**Visual:**

Kéo hai lớp TransferService và ServiceTiming từ cảnh target vào một khung effective execution; bên dưới xuất hiện ba thời điểm ghép compile, load và proxy.

**Script:**

Vậy cơ chế nào làm hai lớp target và aspect thực sự gặp nhau?

**Purpose:**

Chuyển từ hai trách nhiệm riêng sang câu hỏi cơ chế nào thực sự nối chúng trong đường chạy.

### Scene 1 — Weaving: Ghép aspect với target

**Time:** `06:35–07:42`

**Visual:**

Đặt hai lớp phim trong suốt target và ServiceTiming; kéo chúng lại thành một timeline hiệu lực. Phía dưới chỉ xuất hiện ba nhãn compile, load, runtime proxy.

**Script:**

Weaving là việc kết hợp aspect với target để tạo ra hành vi thực thi có bổ sung. Đây là khái niệm chung; không có một lệnh weaving cố định cho mọi framework. Có cách ghép vào bytecode trước khi chạy, có cách dùng lớp trung gian để chặn lời gọi lúc chạy. Vì thế khi debug, ta phải biết hành vi được nối vào chương trình theo con đường nào.

**Purpose:**

Giải thích ý nghĩa ghép hành vi độc lập kỹ thuật, không dạy giả cú pháp compiler.


## Các vị trí thực thi của before, after và around advice

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:42–07:54`

**Visual:**

Từ ba nhãn compile/load/proxy, làm nổi timeline của một lời gọi thành công và một lời gọi ném lỗi; cắm thẻ BEFORE, AFTER RETURNING, AFTER THROWING ở đúng vị trí.

**Script:**

Sau khi hai lớp được ghép, vị trí chạy của advice sẽ quyết định ta quan sát được gì.

**Purpose:**

Dùng hai đường thoát để giải thích vị trí advice, không gộp mọi loại after thành một.

### Scene 1 — Các vị trí thực thi của before, after và around advice

**Time:** `07:54–09:01`

**Visual:**

Bốn nhánh trên timeline: before trước target; after-returning chỉ khi success; after-throwing khi exception; after/finally cả hai. Bao vòng lớn around.

**Script:**

Hãy xem hai lần gọi cùng hàm: một lần thành công và một lần ném lỗi. Before chạy trước công việc đích. After-returning chỉ gặp kết quả trả về bình thường; after-throwing gắn với đường lỗi; after kiểu finally vẫn có việc trên cả hai đường. Around ở ngoài cùng có thể quyết định có tiếp tục vào target hay không. Các loại advice này khác nhau về vị trí và hành vi, không chỉ khác tên annotation.

**Purpose:**

Làm rõ điều kiện success/failure của advice bằng hai đường chạy trên màn hình.


## Luồng thực thi hiệu lực, kết quả và ngoại lệ khi áp dụng advice

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:01–09:13`

**Visual:**

Giữ timeline success/failure, khoanh vòng AROUND và nhân ba nhánh proceed 0, proceed 1, proceed 2 với bộ đếm target 0/1/2.

**Script:**

Chỗ nguy hiểm nhất nằm ở việc around advice có thể làm target chạy bao nhiêu lần.

**Purpose:**

Đưa người xem từ vị trí advice sang hậu quả định lượng nếu around thay đổi số lần thực thi.

### Scene 1 — Luồng thực thi hiệu lực, kết quả và ngoại lệ khi áp dụng advice

**Time:** `09:13–10:20`

**Visual:**

Dựng bảng ba hàng có bộ đếm chuyển tiền: proceed 0 → 0 giao dịch; proceed 1 → 1; proceed 2 → có thể 2. Thêm lane exception minh họa cleanup rồi ném tiếp lỗi.

**Script:**

Nhìn bộ đếm nhé. Nếu around advice không gọi tiếp mà tự trả kết quả, target có thể chưa chạy lần nào. Gọi tiếp một lần thì nghiệp vụ chạy một lần. Trong mô hình cho phép gọi tiếp hai lần, target cũng có thể chuyển tiền hai lần. Đó là hai tác động thật, chứ không phải hai dòng log! Khi chỉ đo thời gian, ta thường phải cho chạy đúng một lần và giữ nguyên cả kết quả lẫn lỗi. Muốn retry hay bỏ qua phải là một quyết định nghiệp vụ có kiểm soát.

**Purpose:**

Chứng minh rủi ro kinh doanh khi advice thay đổi số lần thực thi và đường lỗi.

### Scene 2 — Kiểm chứng trường hợp khó

**Time:** `10:20–11:13`

**Visual:**

Giữ bảng bộ đếm, sau đó kích hoạt nhánh transfer lỗi; tô đoạn finally vẫn ghi elapsed và mũi tên ngoại lệ được ném tiếp, không biến thành thành công.

**Script:**

Còn một bẫy nữa. Nếu timer bắt lỗi rồi trả về kết quả như thể chuyển tiền thành công, nó đã thay đổi hợp đồng nghiệp vụ. Chính sách đo tốt phải ghi nhận thời gian cả khi target ném lỗi và để lỗi tiếp tục truyền ra. Bảng proceed trả lời target đã chạy mấy lần; sơ đồ lỗi trả lời advice có lén làm thay đổi điều caller nhìn thấy hay không.

**Purpose:**

Tách rõ số lần target chạy khỏi nghĩa vụ bảo toàn đường lỗi của advice đo thời gian.


## Ví dụ nối concern, aspect, join point, pointcut và advice

<!-- VIDEO_SECTION -->

### Transition

**Time:** `11:13–11:25`

**Visual:**

Thu bảng proceed và lane exception về bên trái; bên phải bật sơ đồ concern → aspect → pointcut MATCH → advice → target, thêm lane renderPage NO MATCH.

**Script:**

Giờ nối các thuật ngữ vừa học thành một lần thực thi có thể quan sát từ đầu đến cuối.

**Purpose:**

Tổng hợp từ vựng vừa học vào một phép thử có ca trúng, trượt và thất bại.

### Scene 1 — Ví dụ nối concern, aspect, join point, pointcut và advice

**Time:** `11:25–12:32`

**Visual:**

Hai lane: transferFunds(100) MATCH → mở đồng hồ → target → ghi thời gian; renderPage() NO MATCH → chỉ target. Replay một transfer ném lỗi, timer vẫn ghi.

**Script:**

Trong ví dụ này, ServiceTiming là aspect, lần thực thi transferFunds là join point, điều kiện chọn thao tác thanh toán là pointcut, phần đo thời gian là advice, còn TransferService là target. renderPage không khớp nên không bị đo. Chúng ta còn thử đường lỗi để kiểm tra bản ghi thời gian được tạo mà ngoại lệ nghiệp vụ vẫn truyền ra. Một ví dụ có cả khớp, không khớp, thành công và thất bại mới thật sự kiểm tra được chính sách.

**Purpose:**

Tổng hợp concern, aspect, join point, pointcut, advice, target trong trace có thể bác bỏ được.
