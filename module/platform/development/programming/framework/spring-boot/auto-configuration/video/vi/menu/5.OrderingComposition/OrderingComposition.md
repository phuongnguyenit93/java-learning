---
video:
  url: ""
---

# Thứ tự và cách phối hợp Auto-configuration

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

## Thứ tự khai báo Configuration và thứ tự tạo Bean

<!-- VIDEO_SECTION -->

### Scene 1 — Thứ tự xử lý configuration không phải thứ tự tạo bean

**Time:** `00:00–00:50`

**Visual:**

Hai dòng thời gian song song. Dòng trên: AutoConfiguration A được xử lý rồi tới B. Dòng dưới: Bean Y được tạo trước X vì X phụ thuộc Y. Không nối hai dòng bằng cùng một mũi tên.

**Script:**

Ordering của auto-configuration phối hợp thứ tự xử lý bean definition từ cấu hình. Nó không điều khiển thứ tự khởi tạo bean. Nếu B cần nhìn thấy definition mà A có cơ hội đóng góp, ordering có ý nghĩa. Nhưng nếu bean X thật sự phụ thuộc bean Y, hãy biểu diễn dependency qua container thay vì mong một annotation ordering sẽ ép thứ tự tạo bean.

**Purpose:**

Ngăn nhầm lẫn giữa xử lý cấu hình và vòng đời bean.

## Quan hệ Before và After

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:50–01:00`

**Visual:**

Từ hai dòng thời gian giữ lại dòng xử lý cấu hình rồi đặt AcmeCoreAutoConfiguration trước AcmeMetricsAutoConfiguration.

**Script:**

Khi thứ tự xử lý thật sự quan trọng, cách rõ nhất là diễn đạt quan hệ giữa chính hai auto-configuration.

**Purpose:**

Chuyển từ ranh giới khái niệm sang cơ chế ordering có chủ đích.

### Scene 2 — Before và after diễn đạt quan hệ trực tiếp

**Time:** `01:00–01:50`

**Visual:**

Hiển thị AutoConfiguration(before=...) và AutoConfigureAfter ở hai ví dụ nhỏ. Bên cạnh có phiên bản dùng tên class khi không muốn tạo dependency cứng.

**Script:**

Boot cho phép diễn đạt quan hệ before và after trực tiếp giữa các auto-configuration. Điều đó ghi lại ý định rõ hơn một con số: metrics chỉ được xử lý sau khi core đã có cơ hội đăng ký definition. Khi không muốn tạo dependency cứng lên class phía bên kia, quan hệ theo tên class vẫn giữ được ý nghĩa ordering mà không ép classpath.

**Purpose:**

Cho thấy before/after là công cụ mô tả quan hệ xử lý definition, không phải trang trí thứ tự.

## Sắp xếp các Auto-configuration độc lập

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:00`

**Visual:**

Biến mũi tên trực tiếp thành một thước số với vài auto-configuration độc lập.

**Script:**

Không phải lúc nào cũng có một cặp phụ thuộc cụ thể. Đôi khi chỉ cần một vị trí tương đối trong chuỗi.

**Purpose:**

Phân biệt quan hệ trực tiếp với ordering tổng quát.

### Scene 3 — Giá trị thứ tự chỉ dành cho vị trí tổng quát

**Time:** `02:00–02:50`

**Visual:**

Hiển thị AutoConfigureOrder với ba mức số; phía dưới gạch bỏ suy luận rằng cùng số đó sẽ sắp filter, listener hoặc thứ tự tạo bean.

**Script:**

AutoConfigureOrder phù hợp khi một auto-configuration cần vị trí tổng quát nhưng không có quan hệ before hoặc after rõ với một class cụ thể. Giá trị này thuộc riêng quá trình xử lý auto-configuration. Đừng suy ra nó sẽ sắp thứ tự callback lúc chạy, filter, listener hay vòng đời bean. Khi có dependency cụ thể, before hoặc after thường dễ đọc và ít mơ hồ hơn.

**Purpose:**

Giúp người học chọn đúng cơ chế ordering theo loại quan hệ.

## Phối hợp các Configuration có Condition

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:50–03:00`

**Visual:**

Từ danh sách auto-configuration chuyển sang một cây AcmeClientAutoConfiguration có nhánh core, metrics và servlet.

**Script:**

Ordering giải quyết ai được xử lý trước. Nhưng phần tích hợp lớn còn cần cấu trúc để condition nằm đúng gần tính năng mà nó bảo vệ.

**Purpose:**

Nối ordering với cách phối hợp các nhánh có điều kiện.

### Scene 4 — Tách configuration theo ranh giới áp dụng

**Time:** `03:00–03:50`

**Visual:**

Cây cấu hình: core luôn được xét, metrics có ConditionalOnClass, servlet có ConditionalOnWebApplication. Các nhánh được import tường minh thay vì component scan.

**Script:**

Phần tích hợp lớn dễ hiểu hơn khi tách theo ranh giới áp dụng. Core client có thể là nhánh chính; metrics chỉ tồn tại khi thư viện metrics có mặt; servlet chỉ tồn tại trong web context phù hợp. Condition nằm gần tính năng giúp lỗi ở một nhánh không kéo sập cả phần tích hợp và làm cấu trúc dễ kiểm thử hơn. Việc phối hợp vẫn nên tường minh bằng import hoặc class cấu hình tập trung.

**Purpose:**

Cho thấy cách phối hợp tốt giúp condition hẹp, phạm vi sở hữu rõ và giảm phụ thuộc lẫn nhau giữa các tính năng tùy chọn.

## Cô lập công nghệ tùy chọn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:50–04:00`

**Visual:**

Phóng to nhánh metrics rồi bỏ thư viện metrics khỏi classpath.

**Script:**

Nhánh tùy chọn chỉ thật sự tùy chọn nếu thiếu dependency không làm cấu hình chính lỗi khi nạp class.

**Purpose:**

Chuyển từ logic phối hợp sang ranh giới class loading có thể kiểm chứng.

### Scene 5 — Cô lập công nghệ tùy chọn ở cả dependency lẫn class loading

**Time:** `04:00–04:50`

**Visual:**

Cấu hình lồng cho phần tùy chọn được bảo vệ bằng class-level ConditionalOnClass. Chạy ApplicationContextRunner với FilteredClassLoader: bean lõi vẫn có, bean metrics biến mất, không có NoClassDefFoundError.

**Script:**

Đánh dấu dependency là tùy chọn chưa đủ. Nếu cấu hình chính liên kết quá sớm tới type của thư viện đó, JVM vẫn có thể lỗi trước khi condition giúp. Hãy cô lập type tùy chọn trong cấu hình riêng có class-level ConditionalOnClass. FilteredClassLoader là bằng chứng rất rõ: loại thư viện khỏi classpath mô phỏng, context vẫn chạy và chỉ nhánh tùy chọn biến mất.

**Purpose:**

Chứng minh sự cô lập phải tồn tại ở cả ranh giới dependency và ranh giới class loading.
