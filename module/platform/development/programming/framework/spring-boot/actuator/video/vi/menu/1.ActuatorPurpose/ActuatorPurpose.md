---
video:
  url: ""
---

# Vì sao Spring Boot Actuator tồn tại

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

## Actuator đóng vai trò gì trong một ứng dụng Boot đang chạy?

<!-- VIDEO_SECTION -->

### Scene 1 — Actuator đóng vai trò gì trong một ứng dụng Boot đang chạy?

**Time:** `00:00–00:41`

**Visual:**

Đặt ứng dụng Boot đang chạy ở giữa, bên cạnh là console dành cho vận hành. Lần lượt thêm các thẻ `health`, `metrics`, `loggers` và một custom endpoint; cuối cùng highlight dependency `spring-boot-starter-actuator`.

**Script:**

Điểm đầu tiên cần tách rõ: Actuator không phải một business API mới. Nó bổ sung lớp quản trị production quanh ứng dụng Boot đang chạy để operator và automation có thể xem health, metrics, trạng thái logging, dữ liệu chẩn đoán hoặc gọi một số thao tác quản trị có giới hạn. Khi thêm `spring-boot-starter-actuator`, Boot mang vào hạ tầng và auto-configuration cần thiết; còn request, job hay message nghiệp vụ vẫn đi qua các entry point bình thường của ứng dụng.

**Purpose:**

Đặt Actuator đúng vai trò ngay từ đầu để mọi endpoint phía sau được hiểu là bề mặt vận hành, không phải chức năng sản phẩm.

## Actuator giải quyết bài toán vận hành production nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:41–00:55`

**Visual:**

Giữ sơ đồ management hiện tại trên màn hình; chuyển highlight từ `Actuator đóng vai trò gì trong một ứng dụng Boot đang chạy?` sang `Actuator giải quyết bài toán vận hành production nào?` bằng đúng boundary hoặc mũi tên liên quan, để người xem thấy vì sao khái niệm sau nối tiếp khái niệm trước.

**Script:**

Khi đã tách Actuator khỏi business traffic, lý do tồn tại của nó trở nên thực tế: “process còn sống” chỉ là một phần rất nhỏ thông tin operator cần.

**Purpose:**

Biến định nghĩa vai trò thành bài toán vận hành thực tế để thấy vì sao cần một management surface riêng.

### Scene 1 — Actuator giải quyết bài toán vận hành production nào?

**Time:** `00:55–01:37`

**Visual:**

Chia màn hình làm hai: bên trái chỉ có một TCP port màu xanh; bên phải có health contributor, tên meter, logger level và góc nhìn cấu hình. Ba controller tự phát `/debug`, `/status`, `/admin` mờ dần phía sau bề mặt Actuator chuẩn hóa.

**Script:**

Một process có thể vẫn nhận kết nối nhưng gần như không thể vận hành tốt. Khi có sự cố, ta cần biết dependency nào hỏng, meter nào đã được ghi, logger nào đang có hiệu lực hay cấu hình nào thực sự đang chạy. Nếu mỗi team tự dựng `/debug` hoặc `/status`, hợp đồng phản hồi và chính sách bảo vệ sẽ rất khó thống nhất. Actuator gom những câu hỏi vận hành quen thuộc đó vào một mô hình Boot có thể dự đoán.

**Purpose:**

Tạo động cơ học từ tình huống incident thực tế và cho thấy vì sao bề mặt quản trị chuẩn hóa tốt hơn controller debug tự phát.

## Bề mặt quản trị dành cho production là gì?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:37–01:51`

**Visual:**

Giữ sơ đồ management hiện tại trên màn hình; chuyển highlight từ `Actuator giải quyết bài toán vận hành production nào?` sang `Bề mặt quản trị dành cho production là gì?` bằng đúng boundary hoặc mũi tên liên quan, để người xem thấy vì sao khái niệm sau nối tiếp khái niệm trước.

**Script:**

Bài toán vận hành dẫn tới một phân biệt quan trọng hơn: capability tồn tại, được expose ở đâu và ai được dùng nó là ba câu hỏi khác nhau.

**Purpose:**

Từ nhu cầu vận hành chuyển sang mô hình capability/exposure/access để giải quyết nhu cầu đó mà vẫn an toàn.

### Scene 1 — Bề mặt quản trị dành cho production là gì?

**Time:** `01:51–02:27`

**Visual:**

Dựng sơ đồ ba lớp: endpoint capability → Web/JMX exposure → authorization/network policy. Đặt ví dụ đọc như `health`, `metrics` cạnh thao tác có tác động như đổi logger level.

**Script:**

Hãy nghĩ management surface là tập khả năng vận hành chứ không chỉ là vài URL. Có capability chỉ đọc, cũng có operation thay đổi trạng thái runtime. Ba quyết định phải tách riêng: endpoint phải tồn tại, phải được expose qua HTTP/JMX thì client từ xa mới thấy, và caller vẫn cần quyền phù hợp. Vì thế “endpoint tồn tại” hoàn toàn không có nghĩa “ai cũng được gọi”.

**Purpose:**

Cố định mô hình capability/exposure/access để người học không trộn lẫn cấu hình endpoint với chính sách bảo mật.

## Actuator liên hệ với trạng thái runtime thế nào mà không sở hữu vòng đời ứng dụng?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:27–02:40`

**Visual:**

Giữ sơ đồ management hiện tại trên màn hình; chuyển highlight từ `Bề mặt quản trị dành cho production là gì?` sang `Actuator liên hệ với trạng thái runtime thế nào mà không sở hữu vòng đời ứng dụng?` bằng đúng boundary hoặc mũi tên liên quan, để người xem thấy vì sao khái niệm sau nối tiếp khái niệm trước.

**Script:**

Sau capability/exposure/access, cần đi sâu thêm một lớp: nhiều giá trị endpoint thực chất chỉ là góc nhìn của trạng thái được tạo ở nơi khác.

**Purpose:**

Dùng management-surface model để làm lộ ra việc nhiều giá trị endpoint chỉ là projection của state do nơi khác sở hữu.

### Scene 1 — Actuator liên hệ với trạng thái runtime thế nào mà không sở hữu vòng đời ứng dụng?

**Time:** `02:40–03:15`

**Visual:**

Cho các mũi tên từ `ApplicationAvailability`, `LoggingSystem`, `MeterRegistry`, `ApplicationStartup` đi vào các thẻ Actuator endpoint. Giữ các nguồn trạng thái nằm ngoài biên Actuator.

**Script:**

Actuator thường chỉ trình bày trạng thái do phân hệ khác sở hữu. `ApplicationAvailability` quyết định liveness/readiness; logging system giữ cấu hình logger; Micrometer giữ meter; `ApplicationStartup` có thể thu startup step. Actuator lấy một phần trạng thái đó và chiếu ra management endpoint. Vì vậy nếu readiness đang `REFUSING_TRAFFIC`, sửa Actuator không tự chữa được nguyên nhân; cần quay về nơi đã làm runtime chuyển sang trạng thái đó.

**Purpose:**

Dạy mô hình “management projection” để từ triệu chứng ở endpoint có thể lần ngược về đúng subsystem sở hữu trạng thái.

## Những phần nào không thuộc trách nhiệm của Actuator?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:15–03:28`

**Visual:**

Giữ sơ đồ management hiện tại trên màn hình; chuyển highlight từ `Actuator liên hệ với trạng thái runtime thế nào mà không sở hữu vòng đời ứng dụng?` sang `Những phần nào không thuộc trách nhiệm của Actuator?` bằng đúng boundary hoặc mũi tên liên quan, để người xem thấy vì sao khái niệm sau nối tiếp khái niệm trước.

**Script:**

Nếu Actuator là lớp chiếu quản trị, câu hỏi tự nhiên tiếp theo là: những việc nào dù endpoint có liên quan nhưng vẫn không thuộc Actuator?

**Purpose:**

Từ projection model dẫn tự nhiên tới ownership boundary trước khi đi sâu vào từng feature.

### Scene 1 — Những phần nào không thuộc trách nhiệm của Actuator?

**Time:** `03:28–04:04`

**Visual:**

Vẽ bản đồ ranh giới: Actuator ở giữa, các mũi tên bàn giao sang Spring Security, Micrometer/observability, application-runtime, hạ tầng logging tập trung và JVM analysis; cạnh mỗi miền ghi một nhiệm vụ đại diện.

**Script:**

Actuator đứng giữa nhiều miền nhưng không nuốt luôn trách nhiệm của chúng. Nó expose health/metrics nhưng không thiết kế dashboard và alert; tích hợp Spring Security nhưng không định nghĩa authentication model; trả thread/heap dump nhưng không phân tích JVM pathology; phản ánh lifecycle availability nhưng không điều khiển vòng đời. Giữ ranh giới này rõ giúp module không biến thành khóa học thứ hai về security, observability hay JVM tuning.

**Purpose:**

Ngăn ownership drift bằng cách chỉ rõ Actuator kết thúc ở đâu và subsystem nào tiếp quản phần phân tích sâu hơn.

## Lộ trình học Actuator kết nối với nhau như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:04–04:17`

**Visual:**

Giữ sơ đồ management hiện tại trên màn hình; chuyển highlight từ `Những phần nào không thuộc trách nhiệm của Actuator?` sang `Lộ trình học Actuator kết nối với nhau như thế nào?` bằng đúng boundary hoặc mũi tên liên quan, để người xem thấy vì sao khái niệm sau nối tiếp khái niệm trước.

**Script:**

Khi ranh giới ownership đã rõ, ta có thể biến nó thành lộ trình học đi từ abstraction của endpoint tới cách xử lý sự cố production.

**Purpose:**

Chuyển các boundary vừa xác định thành chuỗi câu hỏi mà phần còn lại của module sẽ lần lượt trả lời.

### Scene 1 — Lộ trình học Actuator kết nối với nhau như thế nào?

**Time:** `04:17–04:54`

**Visual:**

Trải các menu còn lại thành một luồng xử lý sự cố: endpoint model → enablement/exposure → health/probes → info/environment → metrics → diagnostics → custom endpoint → access/security → synthesis.

**Script:**

Phần còn lại của module đi theo đúng thứ tự suy luận production. Ta học endpoint model trước, rồi tách enablement khỏi exposure; sau đó áp mô hình đó cho health, probe, info, metrics và diagnostics. Tiếp theo mới mở rộng bằng custom endpoint và bảo vệ management surface bằng network/security. Chương tổng hợp cuối cùng ghép tất cả thành một workflow điều tra sự cố thay vì danh sách endpoint rời rạc.

**Purpose:**

Kết thúc chương mở đầu bằng một lộ trình có quan hệ nguyên nhân, để mỗi menu sau giải quyết một câu hỏi vận hành đã được đặt ra.
