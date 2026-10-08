---
video:
  url: ""
---

# Server properties và mô hình cấu hình

<!--
VIDEO SCRIPT FORMAT

Section rules:
- Each H2 is one video section/chapter.
- The first section requires at least 1 Scene.
- From the second section onward, each section requires at least 1 Transition + 1 Scene.
- Step 6 may add more Scenes/Transitions when the Knowledge content needs them.

TRANSITION FORMAT

### Transition

**Time:** MM:SS–MM:SS

**Visual:**

Describe the transition, title card, or screen change.

**Script:**

Write the short bridge from the previous section to the current section.

**Purpose:**

Explain why this transition exists.

SCENE FORMAT

### Scene N — <optional scene title>

**Time:** MM:SS–MM:SS

**Visual:**

Describe what the viewer sees: slide, diagram, source code, terminal, API request/response, runtime output, highlight, or callout.

**Script:**

Write the narration/presentation script for this scene.

**Purpose:**

Explain what this scene teaches, demonstrates, or proves.
-->

## ServerProperties là mô hình cấu hình server của Boot

<!-- VIDEO_SECTION -->

### Scene 1 — ServerProperties là mô hình cấu hình server của Boot

**Time:** `00:00–00:47`

**Visual:**

Đặt property sources ở trái, ServerProperties ở giữa và server factory ở phải; tô riêng portable server.* và một namespace server-specific để thấy hai tầng cấu hình. Với "ServerProperties là mô hình cấu hình server của Boot", đặt `ServerProperties`, `server.*` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

`ServerProperties` là mô hình property cấu hình của Boot dành cho embedded server. Nó cung cấp cho server customizer được tự động cấu hình một biểu diễn có cấu trúc của các giá trị dưới`server.*`, gồm cả thiết lập dùng chung và nhóm riêng theo cách triển khai. Đây là ranh giới hữu ích: cấu hình bên ngoài mô tả hành vi server mong muốn,`ServerProperties` biểu diễn mô hình Boot đã bind, còn customizer chuyển mô hình đó thành cấu hình của factory đã chọn. Mã ứng dụng thường không cần inject`ServerProperties` chỉ để đổi một thiết lập vốn đã có thể khai báo ngoài mã.

**Purpose:**

Giải thích "ServerProperties là mô hình cấu hình server của Boot" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

### Scene 2 — Runtime evidence

**Time:** `00:47–01:12`

**Visual:**

Khởi động với --server.port=0, gọi GET /spring-boot/web-runtime/server-properties, rồi đặt configuredPort cạnh actualPort. Tô sáng thêm configuredAddress, forwardHeadersStrategy và shutdown trong response. Với "ServerProperties là mô hình cấu hình server của Boot", đặt input của section và runtime result quan sát được tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Step 5 cho ta một phép so sánh rất rõ. Khi chạy với server.port bằng 0, ServerProperties vẫn biểu diễn giá trị cấu hình là 0, còn WebServer trả actualPort mà hệ điều hành đã chọn sau startup. Endpoint /spring-boot/web-runtime/server-properties vì vậy tách được mô hình cấu hình của Boot khỏi trạng thái server đã hiện thực hóa.

**Purpose:**

Dùng endpoint runtime của Step 5 để kiểm chứng "ServerProperties là mô hình cấu hình server của Boot" trên context/server đang chạy thay vì xem sơ đồ là bằng chứng.

## Các điều khiển dùng chung trong namespace server.*

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:12–01:27`

**Visual:**

Giữ pipeline property sources → ServerProperties → factory trên màn hình. Làm mờ annotation của "ServerProperties là mô hình cấu hình server của Boot" rồi animate focus sang "Các điều khiển dùng chung trong namespace server.*"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "ServerProperties là mô hình cấu hình server của Boot" đã rõ. Dependency kế tiếp là "Các điều khiển dùng chung trong namespace server.*"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Các điều khiển dùng chung trong namespace server.*" là dependency kế tiếp sau "ServerProperties là mô hình cấu hình server của Boot", đồng thời giữ ownership và runtime state liên tục.

### Scene 3 — Các điều khiển dùng chung trong namespace server.*

**Time:** `01:27–02:01`

**Visual:**

Đặt property sources ở trái, ServerProperties ở giữa và server factory ở phải; tô riêng portable server.* và một namespace server-specific để thấy hai tầng cấu hình. Với "Các điều khiển dùng chung trong namespace server.*", đặt `server.*`, `server.port`, `server.address` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Các thiết lập dùng chung nằm trực tiếp dưới `server.*` khi Boot có thể biểu diễn cùng một ý định trên các server được hỗ trợ. Ví dụ gồm `server.port`,`server.address`,`server.compression.*`,`server.http2.enabled`,`server.max-http-request-header-size`,`server.shutdown`,`server.forward-headers-strategy` và`server.ssl.*`. “Dùng chung” không có nghĩa mọi server triển khai tính năng giống hệt nhau. Nó có nghĩa Boot cung cấp một ý định cấu hình chung và adapter của từng server hiện thực ý định đó trong phạm vi server hỗ trợ.

**Purpose:**

Nối input trong "Các điều khiển dùng chung trong namespace server.*" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.

## Cổng, địa chỉ bind và các điều khiển HTTP endpoint

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:01–02:16`

**Visual:**

Giữ pipeline property sources → ServerProperties → factory trên màn hình. Làm mờ annotation của "Các điều khiển dùng chung trong namespace server.*" rồi animate focus sang "Cổng, địa chỉ bind và các điều khiển HTTP endpoint"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Khi "Các điều khiển dùng chung trong namespace server.*" đã rõ, hãy kiểm boundary kế tiếp: "Cổng, địa chỉ bind và các điều khiển HTTP endpoint". Giữ kết quả trước để cơ chế mới đi ra trực tiếp từ nó.

**Purpose:**

Chuyển từ "Các điều khiển dùng chung trong namespace server.*" sang "Cổng, địa chỉ bind và các điều khiển HTTP endpoint" mà không reset mô hình, đồng thời làm rõ input, owner hoặc output nào thay đổi.

### Scene 4 — Cổng, địa chỉ bind và các điều khiển HTTP endpoint

**Time:** `02:16–02:54`

**Visual:**

Đặt property sources ở trái, ServerProperties ở giữa và server factory ở phải; tô riêng portable server.* và một namespace server-specific để thấy hai tầng cấu hình. Với "Cổng, địa chỉ bind và các điều khiển HTTP endpoint", đặt `8080`, `server.port`, `0` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Cổng HTTP chính mặc định là `8080` với ứng dụng web độc lập. Đặt`server.port` thành một giá trị cố định,`0` để hệ điều hành cấp một cổng còn trống hoặc`-1` để vẫn tạo context ứng dụng web nhưng không mở HTTP endpoint.`server.address` điều khiển địa chỉ mạng mà server bind. Đây là điều khiển runtime của server, không phải điều khiển routing. Thay cổng hoặc địa chỉ bind chỉ thay nơi server lắng nghe; nó không thay route mapping của Spring MVC hay WebFlux.

**Purpose:**

Biến "Cổng, địa chỉ bind và các điều khiển HTTP endpoint" thành lựa chọn cụ thể bằng cách cho thấy runtime thay đổi gì và tầng cấu hình hoặc customization nào nên dùng tiếp.

## server.tomcat.*, server.jetty.*, server.undertow.* và server.netty.*

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:54–03:08`

**Visual:**

Giữ pipeline property sources → ServerProperties → factory trên màn hình. Làm mờ annotation của "Cổng, địa chỉ bind và các điều khiển HTTP endpoint" rồi animate focus sang "server.tomcat.*, server.jetty.*, server.undertow.* và server.netty.*"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ta đã chốt "Cổng, địa chỉ bind và các điều khiển HTTP endpoint". Giữ kết quả đó trên màn hình; bây giờ chuyển sang "server.tomcat.*, server.jetty.*, server.undertow.* và server.netty.*" và chỉ thay quyết định runtime kế tiếp.

**Purpose:**

Nối "Cổng, địa chỉ bind và các điều khiển HTTP endpoint" với "server.tomcat.*, server.jetty.*, server.undertow.* và server.netty.*" để quyết định web-runtime kế tiếp đi ra từ kết quả đã chứng minh.

### Scene 5 — server.tomcat.*, server.jetty.*, server.undertow.* và server.netty.*

**Time:** `03:08–03:44`

**Visual:**

Giữ runtime diagram của chương nhưng chỉ hiện input, quyết định Boot sở hữu và output server/context của section; làm mờ mọi node khác. Với "server.tomcat.*, server.jetty.*, server.undertow.* và server.netty.*", đặt `server.tomcat.*`, `server.jetty.*`, `server.undertow.*` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Namespace riêng theo server cung cấp những khả năng khó biểu diễn bằng một mô hình property dùng chung duy nhất. Boot 3.3 có các nhóm như `server.tomcat.*`,`server.jetty.*`,`server.undertow.*` và `server.netty.*`. Ví dụ gồm hàng đợi kết nối và thiết lập remote IP của Tomcat, thiết lập access log của Jetty, worker/options của Undertow hoặc thiết lập kết nối/tài nguyên của Netty. Khi dùng các namespace này, ứng dụng đang chủ động làm cấu hình phụ thuộc vào một họ embedded server cụ thể.

**Purpose:**

Cho thấy hệ quả runtime cụ thể của "server.tomcat.*, server.jetty.*, server.undertow.* và server.netty.*" và tạo checkpoint quan sát được cho quyết định Boot sở hữu.

## Chọn property dùng chung hay property riêng của từng server

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:44–03:59`

**Visual:**

Giữ pipeline property sources → ServerProperties → factory trên màn hình. Làm mờ annotation của "server.tomcat.*, server.jetty.*, server.undertow.* và server.netty.*" rồi animate focus sang "Chọn property dùng chung hay property riêng của từng server"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Section trước đã cho evidence cho "server.tomcat.*, server.jetty.*, server.undertow.* và server.netty.*". Đi tiếp trên cùng runtime path tới "Chọn property dùng chung hay property riêng của từng server" và quan sát input, owner hoặc output nào thay đổi.

**Purpose:**

Giữ evidence từ "server.tomcat.*, server.jetty.*, server.undertow.* và server.netty.*" và chỉ đưa thêm cơ chế mới cần cho "Chọn property dùng chung hay property riêng của từng server".

### Scene 6 — Chọn property dùng chung hay property riêng của từng server

**Time:** `03:59–04:42`

**Visual:**

Giữ runtime diagram của chương nhưng chỉ hiện input, quyết định Boot sở hữu và output server/context của section; làm mờ mọi node khác. Với "Chọn property dùng chung hay property riêng của từng server", đặt `server.*`, `WebServerFactoryCustomizer` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Hãy bắt đầu bằng property `server.*` dùng chung nếu nó mô tả được yêu cầu. Cách này giúp ứng dụng dễ đổi server hơn và làm ý định rõ mà không cần hiểu server API. Chỉ dùng property riêng theo server khi chính yêu cầu phụ thuộc cách triển khai hoặc mô hình chung chưa cung cấp quyền kiểm soát cần thiết. Nếu cả hai mức property đều không đủ, mới chuyển sang `WebServerFactoryCustomizer`. Chuỗi quyết định này giữ tùy biến ở dạng khai báo và có khả năng chuyển đổi tối đa trong phạm vi yêu cầu cho phép.

**Purpose:**

Giải thích "Chọn property dùng chung hay property riêng của từng server" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## Precedence và binding thuộc Externalized Configuration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:42–04:56`

**Visual:**

Giữ pipeline property sources → ServerProperties → factory trên màn hình. Làm mờ annotation của "Chọn property dùng chung hay property riêng của từng server" rồi animate focus sang "Precedence và binding thuộc Externalized Configuration"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Chọn property dùng chung hay property riêng của từng server" đã rõ. Dependency kế tiếp là "Precedence và binding thuộc Externalized Configuration"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Precedence và binding thuộc Externalized Configuration" là dependency kế tiếp sau "Chọn property dùng chung hay property riêng của từng server", đồng thời giữ ownership và runtime state liên tục.

### Scene 7 — Precedence và binding thuộc Externalized Configuration

**Time:** `04:56–05:38`

**Visual:**

Đặt property sources ở trái, ServerProperties ở giữa và server factory ở phải; tô riêng portable server.* và một namespace server-specific để thấy hai tầng cấu hình. Với "Precedence và binding thuộc Externalized Configuration", đặt `SERVER_PORT`, `server.port`, `externalized-configuration` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Module này tiêu thụ kết quả của hệ thống externalized configuration của Boot; nó không định nghĩa lại configuration precedence, kích hoạt profile, relaxed binding, biến môi trường hay thứ tự property source. Các quy tắc đó quyết định giá trị nào thắng trước khi phần tùy biến server sử dụng nó. Ví dụ `SERVER_PORT` có thể bind vào`server.port`, nhưng lý do biến môi trường ghi đè hoặc bị nguồn khác ghi đè thuộc module`externalized-configuration`. Ở đây câu hỏi cần học là giá trị`server.port` đã được phân giải sẽ thay đổi embedded server ra sao.

**Purpose:**

Nối input trong "Precedence và binding thuộc Externalized Configuration" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.
