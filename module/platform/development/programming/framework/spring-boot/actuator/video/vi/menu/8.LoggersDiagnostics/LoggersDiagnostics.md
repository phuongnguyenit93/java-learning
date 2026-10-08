---
video:
  url: ""
---

# Loggers và các endpoint chẩn đoán runtime

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

## Loggers endpoint cung cấp thông tin gì?

<!-- VIDEO_SECTION -->

### Scene 1 — Loggers endpoint cung cấp thông tin gì?

**Time:** `00:00–00:29`

**Visual:**

Mở `/actuator/loggers` với bảng nhỏ logger/group và hai cột `configuredLevel`, `effectiveLevel`. Đặt `LoggingSystem` phía sau endpoint như nguồn trạng thái runtime.

**Script:**

Loggers endpoint expose cấu hình runtime mà `LoggingSystem` của Boot đang biết. Nó có thể liệt kê logger/group, xem một entry và cho biết configured/effective level. Actuator chỉ thêm management operation trên logging system đã chạy; nó không sở hữu quá trình logging bootstrap hay file cấu hình logging bình thường của ứng dụng.

**Purpose:**

Định nghĩa loggers endpoint là lớp inspect/control runtime và giữ ranh giới với logging initialization của Boot.

## Logger level được cấu hình và mức có hiệu lực khác nhau thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:29–00:41`

**Visual:**

Giữ diagnostics board trên màn hình và chuyển focus cho `Logger level được cấu hình và mức có hiệu lực khác nhau thế nào?` từ logging state sang runtime capture, analysis handoff hoặc exposure risk; không quay lại generic endpoint diagram.

**Script:**

Liệt kê logger chưa đủ khi inheritance tham gia. Bước tiếp theo là tách level được đặt trực tiếp khỏi level được thừa hưởng.

**Purpose:**

Từ danh sách logger đi vào inheritance để configured/effective level được diễn giải đúng.

### Scene 1 — Logger level được cấu hình và mức có hiệu lực khác nhau thế nào?

**Time:** `00:41–01:15`

**Visual:**

Dùng cây logger: root=`INFO`, `com.example` không có configured level nên effective=`INFO`. Sau đó set `DEBUG` trực tiếp trên package và cho hai field thay đổi.

**Script:**

Configured và effective level trả lời hai câu khác nhau. `configuredLevel` cho biết chính logger đó có setting explicit hay không; `effectiveLevel` cho biết mức thực sự chi phối sau inheritance. Một package có thể configured level rỗng nhưng vẫn effective `INFO` vì root đang `INFO`. Khi điều tra runtime logging, luôn xem cả hai trước khi kết luận level được cấu hình ở đâu.

**Purpose:**

Làm inheritance nhìn thấy được để diagnosis theo đúng nguồn tạo ra effective level.

## Có thể thay đổi logger level trong lúc chạy như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:15–01:27`

**Visual:**

Giữ diagnostics board trên màn hình và chuyển focus cho `Có thể thay đổi logger level trong lúc chạy như thế nào?` từ logging state sang runtime capture, analysis handoff hoặc exposure risk; không quay lại generic endpoint diagram.

**Script:**

Khi đã hiểu configured và inherited state, write operation cho thấy endpoint mạnh hơn inspector—và vì vậy phải dùng tạm thời, có kiểm soát.

**Purpose:**

Dùng level model đó để dẫn tới runtime write có giới hạn và hành vi restore trong incident.

### Scene 1 — Có thể thay đổi logger level trong lúc chạy như thế nào?

**Time:** `01:27–02:04`

**Visual:**

Gửi write request đổi một package sang `DEBUG`, cho log event mới xuất hiện, rồi clear configured level để nó inherit trở lại. Thêm biểu tượng restart khôi phục normal config.

**Script:**

Web loggers endpoint có thể đổi level của logger hoặc group khi process vẫn chạy. Điều này rất hữu ích cho incident ngắn vì không cần restart, nhưng đây là operational state chứ không phải chỉnh cấu hình bền vững. Điều tra xong nên restore/clear level; restart thường xây lại logging từ nguồn cấu hình bình thường. Và nên giữ scope hẹp vì DEBUG/TRACE rộng có thể tăng I/O lẫn nguy cơ lộ dữ liệu.

**Purpose:**

Cho thấy runtime logger control phải reversible, có scope hẹp và không được nhầm với persistent configuration.

## Thread dump endpoint cung cấp dữ liệu gì?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:04–02:17`

**Visual:**

Giữ diagnostics board trên màn hình và chuyển focus cho `Thread dump endpoint cung cấp dữ liệu gì?` từ logging state sang runtime capture, analysis handoff hoặc exposure risk; không quay lại generic endpoint diagram.

**Script:**

Logger control giúp điều tra event đã phát ra; khi vấn đề nằm ở execution state, management surface có thể thu một loại bằng chứng khác: thread snapshot.

**Purpose:**

Nâng từ logging configuration sang JVM thread snapshot khi vấn đề nằm ở execution state chứ không phải log visibility.

### Scene 1 — Thread dump endpoint cung cấp dữ liệu gì?

**Time:** `02:17–02:48`

**Visual:**

Gọi `/actuator/threaddump`, freeze một JSON excerpt nhỏ có thread id/name, state, lock và stack frame. Bên cạnh cho snapshot đi vào JVM-analysis tool.

**Script:**

`threaddump` endpoint chụp snapshot thông tin thread của JVM và có thể trả dạng cấu trúc gồm identity, state, lock và stack frame. Nó trả lời “hãy lấy bằng chứng thread từ process này”, chứ không kết luận wait state nào bất thường, lock nào nóng hay có deadlock không. Phần diễn giải đó thuộc Java concurrency/JVM diagnostics.

**Purpose:**

Tách việc thu thập thread evidence khỏi việc phân tích để không trình bày endpoint như một deadlock analyzer.

## Heap dump endpoint cung cấp dữ liệu gì?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:48–03:02`

**Visual:**

Giữ diagnostics board trên màn hình và chuyển focus cho `Heap dump endpoint cung cấp dữ liệu gì?` từ logging state sang runtime capture, analysis handoff hoặc exposure risk; không quay lại generic endpoint diagram.

**Script:**

Thread dump là execution evidence có cấu trúc; heap dump nặng hơn nhiều và chứa memory state, nên risk lẫn quy trình xử lý cũng phải được nâng lên.

**Purpose:**

Từ thread evidence nhẹ hơn chuyển sang heap evidence nặng hơn đồng thời nâng mức cost và confidentiality.

### Scene 1 — Heap dump endpoint cung cấp dữ liệu gì?

**Time:** `03:02–03:38`

**Visual:**

Download `/actuator/heapdump` thành binary file. Gắn HotSpot → HPROF, OpenJ9 → PHD; đưa file vào heap analyzer và thêm warning về size, runtime cost, secret/user data giữ trong memory.

**Script:**

Heapdump web endpoint trả một heap snapshot dạng binary: HPROF trên HotSpot và PHD trên OpenJ9. Dump có thể rất lớn, tạo ra chi phí runtime và chứa string, cached object, credential hay request data còn giữ trong memory. Vì vậy cả thao tác tạo dump lẫn file tải xuống đều là hoạt động incident đặc quyền. Phân tích retention, leak hay GC bắt đầu sau khi Actuator đã giao artifact.

**Purpose:**

Giải thích format, cost, sensitivity và handoff sang memory-analysis tooling.

## Việc Actuator cung cấp dữ liệu kết thúc ở đâu và phân tích JVM/logging bắt đầu ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:38–03:49`

**Visual:**

Giữ diagnostics board trên màn hình và chuyển focus cho `Việc Actuator cung cấp dữ liệu kết thúc ở đâu và phân tích JVM/logging bắt đầu ở đâu?` từ logging state sang runtime capture, analysis handoff hoặc exposure risk; không quay lại generic endpoint diagram.

**Script:**

Sau nhiều loại diagnostic, pattern chung đã rõ: Actuator thu hoặc điều khiển evidence, còn việc giải nghĩa thuộc subsystem khác.

**Purpose:**

Khái quát logger/thread/heap thành boundary giữa việc giao evidence và specialist interpretation.

### Scene 1 — Việc Actuator cung cấp dữ liệu kết thúc ở đâu và phân tích JVM/logging bắt đầu ở đâu?

**Time:** `03:49–04:22`

**Visual:**

Tạo bảng handoff: loggers → logging analysis; threaddump → concurrency/JVM; heapdump → memory/GC. Cột trái “Actuator delivers/controls evidence”, cột phải “specialist tooling interprets cause”.

**Script:**

Ba endpoint này cho thấy cùng một pattern ownership. Actuator có thể báo effective logger level, lấy thread snapshot hoặc tạo heap dump. Nó không quyết định log đó đã giải thích incident chưa, thread state có bệnh lý không hay object nào giữ memory lớn nhất. Management layer lấy bằng chứng đáng tin ra khỏi process; domain/tool chuyên môn mới diễn giải nguyên nhân.

**Purpose:**

Thống nhất các diagnostics quanh ranh giới acquisition-versus-analysis.

## Vì sao các endpoint chẩn đoán nhạy cảm tạo rủi ro khi được công khai?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:22–04:36`

**Visual:**

Giữ diagnostics board trên màn hình và chuyển focus cho `Vì sao các endpoint chẩn đoán nhạy cảm tạo rủi ro khi được công khai?` từ logging state sang runtime capture, analysis handoff hoặc exposure risk; không quay lại generic endpoint diagram.

**Script:**

Vì Actuator thu được bằng chứng mạnh, câu hỏi cuối không còn là capability mà là risk: mỗi diagnostic sẽ lộ hoặc tiêu tốn gì nếu caller không phù hợp chạm tới?

**Purpose:**

Dùng chính sức mạnh của diagnostics để giải thích vì sao exposure phải tối thiểu và incident access cần được kiểm soát.

### Scene 1 — Vì sao các endpoint chẩn đoán nhạy cảm tạo rủi ro khi được công khai?

**Time:** `04:36–05:11`

**Visual:**

Dựng risk board: logger write → log volume/data; thread dump → code path/synchronization; heap dump → secret/user data + resource cost; env/config/mappings → internal structure. Bao quanh bằng minimal exposure, network, authorization và incident-only access.

**Script:**

Diagnostic endpoint tập trung cả thông tin đặc quyền lẫn chi phí vận hành. Logger control có thể tăng volume hoặc lộ event nhạy cảm; thread dump lộ execution path và synchronization; heap dump có thể chứa production data và gây tải; endpoint cấu trúc còn tiết lộ internals. Có authentication không có nghĩa nên expose rộng. Cần minimal exposure, network placement, authorization mạnh và workflow incident có kiểm soát.

**Purpose:**

Khôi phục đầy đủ risk của logger, thread, heap và structural diagnostics để quyết định exposure tính cả confidentiality lẫn resource impact.
