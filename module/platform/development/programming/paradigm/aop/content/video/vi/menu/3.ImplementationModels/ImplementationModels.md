---
video:
  url: ""
---

# Cơ chế ghép hành vi và ranh giới các mô hình AOP

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


## Ghép aspect tại thời điểm biên dịch

<!-- VIDEO_SECTION -->

### Scene 1 — Ghép aspect tại thời điểm biên dịch

**Time:** `00:00–01:07`

**Visual:**

Bên trái TransferService.java giữ nguyên; bên phải sơ đồ input target+aspect → compiler/weaver → bytecode đã ghép, đặt nhãn BEFORE RUN.

**Script:**

Ghép tại thời điểm biên dịch nghĩa là bytecode đầu ra đã mang phần hành vi aspect trước khi chương trình khởi động. AspectJ hỗ trợ mô hình đó. Đọc mỗi file source của target có thể chưa thấy được toàn bộ hành vi khi chạy. Điều cần nhớ lúc này là thời điểm ghép và tập join point mà công cụ hỗ trợ, chứ chưa phải tham số lệnh của trình biên dịch.

**Purpose:**

Giải thích compile-time weaving đúng phạm vi và không gán nhầm cho proxy.


## Ghép aspect tại thời điểm nạp lớp

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:07–01:19`

**Visual:**

Giữ sơ đồ target+aspect → bytecode từ cảnh compile; di chuyển cột weaver khỏi BUILD sang CLASS LOAD, tô mốc trước khi lớp được dùng.

**Script:**

Ghép lúc biên dịch là cách sớm nhất, nhưng điều gì thay đổi nếu bytecode chỉ được biến đổi lúc nạp lớp?

**Purpose:**

Làm rõ chỉ thay đổi thời điểm biến đổi bytecode, chưa chuyển sang interception qua proxy.

### Scene 1 — Ghép aspect tại thời điểm nạp lớp

**Time:** `01:19–02:26`

**Visual:**

Timeline tải lớp: bytecode ban đầu → class loader/transformer → bytecode hiệu lực → thực thi, tô đậm ranh giới trước first use.

**Script:**

Load-time weaving đưa việc biến đổi bytecode đến lúc nạp lớp. Tệp đầu vào có thể chưa được ghép sẵn, nhưng lớp phải được xử lý trước khi đi vào thực thi bình thường. Nếu cấu hình loader hoặc agent không áp dụng, hành vi mong muốn sẽ không tự xuất hiện. Đây vẫn là chuyện biến đổi bytecode, khác với một đối tượng proxy chặn lời gọi khi chạy.

**Purpose:**

Phân biệt load-time transformation và cơ chế interception dựa trên thời điểm thực thi.


## Chặn lời gọi qua lớp trung gian khi chạy

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:26–02:38`

**Visual:**

Từ thanh CLASS LOAD chuyển sang hai đối tượng runtime caller và target; chèn timingProxy vào giữa rồi vẽ đường tắt đỏ bỏ qua proxy.

**Script:**

Cách thứ ba không cần nói rằng lớp target đã bị thay đổi; hãy nhìn đường caller đi qua proxy.

**Purpose:**

Đối chiếu biến đổi bytecode khi tải lớp với chặn lời gọi qua đối tượng trung gian lúc chạy.

### Scene 1 — Chặn lời gọi qua lớp trung gian khi chạy

**Time:** `02:38–03:45`

**Visual:**

Hoạt họa caller ngoài → timingProxy → transferService. Vẽ thêm đường tắt caller → target, nhãn "không đi qua proxy".

**Script:**

Trong mô hình proxy, một đối tượng trung gian đứng trên đường lời gọi. Caller đi qua proxy thì chuỗi advice có cơ hội chạy trước khi chuyển tiếp sang target. Nếu caller cầm trực tiếp target hoặc lời gọi bỏ qua proxy, advice của proxy đó sẽ không quan sát được. Vậy bằng chứng quan trọng không phải dòng annotation, mà là tham chiếu nào được gọi và đường lời gọi đi qua đâu.

**Purpose:**

Cho người học tự dự đoán điều kiện cần để method interception xảy ra.


## Phạm vi quan sát và giới hạn của từng mô hình ghép

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:45–03:57`

**Visual:**

Giữ đường gọi xanh qua proxy và đường đỏ đi vòng; mở bảng join point theo hàng method execution, call, constructor, field set và đánh dấu các ô proxy nhìn thấy.

**Script:**

Những mô hình này đều bổ sung hành vi, nhưng phạm vi quan sát của chúng khác nhau.

**Purpose:**

Chuyển từ đường gọi cụ thể sang giới hạn loại sự kiện mà mỗi cơ chế có thể quan sát.

### Scene 1 — Phạm vi quan sát và giới hạn của từng mô hình ghép

**Time:** `03:57–05:04`

**Visual:**

Lập bảng method execution, method call, constructor, field set; đánh dấu khả năng theo từng cơ chế, không mặc định mọi ô đều có ✓.

**Script:**

Trước khi nói aspect có thể quan sát một sự kiện, phải hỏi framework có hỗ trợ loại join point ấy không. Một proxy chỉ xử lý lời gọi phương thức thích hợp sẽ không tự trở thành bộ bắt mọi lần ghi trường. Còn một mô hình weaving đầy đủ có thể biết thêm những điểm khác. Bảng này là cách ngăn ta mắc lỗi đổ cho pointcut khi bản thân cơ chế chưa từng hỗ trợ kiểu thao tác đó.

**Purpose:**

Xác định phạm vi năng lực trước khi đánh giá một quy tắc chọn điểm.


## AspectJ weaving và Spring AOP proxy: Hai phạm vi join point khác nhau

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:04–05:16`

**Visual:**

Từ ô field set chưa được Spring proxy hỗ trợ, tách bảng thành hai cột AspectJ woven và Spring proxy; đẩy marker constructor/field sang cột đúng.

**Script:**

Đặt AspectJ và Spring AOP cạnh nhau sẽ giúp bảng trở nên cụ thể hơn.

**Purpose:**

Dùng một loại sự kiện không được proxy hỗ trợ để giải thích hai implementation không tương đương.

### Scene 1 — AspectJ weaving và Spring AOP proxy: Hai phạm vi join point khác nhau

**Time:** `05:16–06:23`

**Visual:**

Hai hình song song: AspectJ dệt vào bytecode có marker method/call/constructor/field; Spring AOP có proxy quanh method execution trên Spring bean. Dán nhãn @Aspect không tự bật weaver.

**Script:**

AspectJ weaving có thể hỗ trợ các join point phong phú hơn như constructor và truy cập trường, vì nó kết hợp hành vi ở mức bytecode. Spring AOP thường dùng proxy để can thiệp method execution trên Spring bean. Viết annotation theo cú pháp AspectJ trong Spring không có nghĩa đang dệt bytecode bằng AspectJ. Muốn chứng minh một field-set join point thật sự, ta phải có cấu hình weaving tương ứng, không thể chỉ bọc một controller HTTP là xong.

**Purpose:**

Tách hai mô hình theo điểm quan sát, tránh lời hứa sai về annotation.

### Scene 2 — Kiểm chứng trường hợp khó

**Time:** `06:23–07:16`

**Visual:**

Phóng to dòng balance = balance - 100; nhãn AspectJ woven field-set có thể hỗ trợ, Spring proxy method execution không có field-set join point.

**Script:**

Xem phép gán trường này. Nó không tự trở thành một join point thực thi phương thức của Spring AOP chỉ vì có cập nhật balance. Với AspectJ weaving được cấu hình phù hợp, ta có thể chọn loại điểm cập nhật trường. Đây là khác biệt về cơ chế ghép và mô hình join point, không phải trò chọn annotation nào trông mạnh hơn.

**Purpose:**

Dùng đúng một field assignment để kiểm tra giới hạn join point của hai công nghệ.


## Giới hạn interception và lời gọi nội bộ trong mô hình proxy

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:16–07:28`

**Visual:**

Giữ phép gán balance=balance-100 với nhãn field-set của AspectJ; đổi sang đường proxy.execute rồi làm sáng nhánh this.verify bên trong target, vẽ dấu X tại proxy bị bỏ qua.

**Script:**

Đường proxy vừa vẽ cũng lý giải một bất ngờ rất thường gặp: đối tượng tự gọi chính nó.

**Purpose:**

Nối giới hạn join point của weaving/proxy với giới hạn self-invocation qua chính tham chiếu this.

### Scene 1 — Giới hạn interception và lời gọi nội bộ trong mô hình proxy

**Time:** `07:28–08:35`

**Visual:**

Đường xanh: bên ngoài → proxy.verify() → advice ✓. Đường đỏ: bên ngoài → proxy.execute() → target.this.verify() → advice dành cho verify ✕.

**Script:**

Ở đường đầu tiên, caller gọi proxy.verify và có thể đi qua advice. Ở đường thứ hai, caller vào proxy.execute nhưng khi target dùng this.verify thì lời gọi nội bộ đi thẳng trên chính target, không vòng ngược qua proxy. Vì thế advice riêng của verify không được kích hoạt bởi lời gọi nội bộ đó. Proxy dựa trên interface hay class còn có giới hạn khác; weaving AspectJ không gặp đúng kiểu bỏ qua proxy này.

**Purpose:**

Chứng minh self-invocation bằng đường tham chiếu thực tế, không đồng nhất với AspectJ.
