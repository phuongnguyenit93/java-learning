---
video:
  url: ""
---

# Các mặc định web của Boot và ranh giới sở hữu với Spring Framework

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

## Các mặc định web của Boot cần nhận biết

<!-- VIDEO_SECTION -->

### Scene 1 — Các mặc định web của Boot cần nhận biết

**Time:** `00:00–00:41`

**Visual:**

Vẽ ownership swimlane Boot | Spring MVC/WebFlux | embedded server | network/TLS/proxy; đặt behavior vào đúng lane rồi hiện mũi tên handoff. Với "Các mặc định web của Boot cần nhận biết", đặt `WebApplicationType`, `spring-boot-starter-web`, `spring-boot-starter-webflux` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Một số quy ước giải thích vì sao ứng dụng web Boot chạy được ngay từ đầu: classpath quyết định `WebApplicationType` nếu không ghi đè;`spring-boot-starter-web` chọn Servlet/MVC với Tomcat mặc định;`spring-boot-starter-webflux` chọn mô hình reactive với Reactor Netty khi MVC không có; cổng HTTP chính mặc định là`8080`. Một số mặc định ở môi trường production khác cũng cần nhớ: nén response mặc định tắt,`server.shutdown` là`immediate` trong Boot 3.3, còn xử lý forwarded header thường là`NONE` ngoại trừ nền tảng đám mây được hỗ trợ nơi Boot mặc định`NATIVE`.

**Purpose:**

Giải thích "Các mặc định web của Boot cần nhận biết" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## Quy ước và kiểm soát tường minh trong web runtime

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:41–00:56`

**Visual:**

Giữ swimlane ownership Boot/framework/server/infrastructure trên màn hình. Làm mờ annotation của "Các mặc định web của Boot cần nhận biết" rồi animate focus sang "Quy ước và kiểm soát tường minh trong web runtime"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Các mặc định web của Boot cần nhận biết" đã rõ. Dependency kế tiếp là "Quy ước và kiểm soát tường minh trong web runtime"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Quy ước và kiểm soát tường minh trong web runtime" là dependency kế tiếp sau "Các mặc định web của Boot cần nhận biết", đồng thời giữ ownership và runtime state liên tục.

### Scene 2 — Quy ước và kiểm soát tường minh trong web runtime

**Time:** `00:56–01:46`

**Visual:**

Giữ runtime diagram của chương nhưng chỉ hiện input, quyết định Boot sở hữu và output server/context của section; làm mờ mọi node khác. Với "Quy ước và kiểm soát tường minh trong web runtime", đặt `server.*` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Boot web runtime hoạt động tốt nhất khi quy ước xử lý trường hợp phổ biến còn cấu hình tường minh thể hiện quyết định triển khai. Dependency chọn server được hỗ trợ, property `server.*` diễn đạt hành vi chung, namespace riêng theo server xử lý chi tiết theo cách triển khai, còn customizer lấp khoảng trống của mô hình property. Chuyển sang kiểm soát tường minh khi có lý do thật: classpath trộn MVC/WebFlux, server không mặc định, địa chỉ bind cố định, dữ liệu TLS, proxy forwarding, connector bổ sung hoặc graceful shutdown. Giữ quyết định ở lớp trừu tượng cao nhất có thể giúp nâng cấp và chuyển server dễ suy luận hơn.

**Purpose:**

Nối input trong "Quy ước và kiểm soát tường minh trong web runtime" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.

## Bàn giao trách nhiệm sang Spring MVC và Spring WebFlux

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:46–02:02`

**Visual:**

Giữ swimlane ownership Boot/framework/server/infrastructure trên màn hình. Làm mờ annotation của "Quy ước và kiểm soát tường minh trong web runtime" rồi animate focus sang "Bàn giao trách nhiệm sang Spring MVC và Spring WebFlux"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Khi "Quy ước và kiểm soát tường minh trong web runtime" đã rõ, hãy kiểm boundary kế tiếp: "Bàn giao trách nhiệm sang Spring MVC và Spring WebFlux". Giữ kết quả trước để cơ chế mới đi ra trực tiếp từ nó.

**Purpose:**

Chuyển từ "Quy ước và kiểm soát tường minh trong web runtime" sang "Bàn giao trách nhiệm sang Spring MVC và Spring WebFlux" mà không reset mô hình, đồng thời làm rõ input, owner hoặc output nào thay đổi.

### Scene 3 — Bàn giao trách nhiệm sang Spring MVC và Spring WebFlux

**Time:** `02:02–02:44`

**Visual:**

Vẽ ownership swimlane Boot | Spring MVC/WebFlux | embedded server | network/TLS/proxy; đặt behavior vào đúng lane rồi hiện mũi tên handoff. Với "Bàn giao trách nhiệm sang Spring MVC và Spring WebFlux", đặt `DispatcherServlet` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Sau khi Boot đã chọn web runtime, khởi động server và nối nó với context ứng dụng, ngữ nghĩa xử lý request thuộc Spring Framework. MVC sở hữu `DispatcherServlet`, controller mapping, converter, interceptor và mô hình Servlet web framework. WebFlux sở hữu route/handler reactive, codec, filter và mô hình xử lý reactive. Boot thêm auto-configuration và các mặc định hợp lý quanh các framework đó, nhưng module này dừng ở ranh giới tích hợp runtime/server. Một server đang lắng nghe bình thường vẫn có thể đi cùng một handler mapping sai; hai lỗi thuộc hai lớp khác nhau.

**Purpose:**

Biến "Bàn giao trách nhiệm sang Spring MVC và Spring WebFlux" thành lựa chọn cụ thể bằng cách cho thấy runtime thay đổi gì và tầng cấu hình hoặc customization nào nên dùng tiếp.

## Bàn giao trách nhiệm sang phần nội bộ của Servlet và Reactive server

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:44–03:01`

**Visual:**

Giữ swimlane ownership Boot/framework/server/infrastructure trên màn hình. Làm mờ annotation của "Bàn giao trách nhiệm sang Spring MVC và Spring WebFlux" rồi animate focus sang "Bàn giao trách nhiệm sang phần nội bộ của Servlet và Reactive server"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ta đã chốt "Bàn giao trách nhiệm sang Spring MVC và Spring WebFlux". Giữ kết quả đó trên màn hình; bây giờ chuyển sang "Bàn giao trách nhiệm sang phần nội bộ của Servlet và Reactive server" và chỉ thay quyết định runtime kế tiếp.

**Purpose:**

Nối "Bàn giao trách nhiệm sang Spring MVC và Spring WebFlux" với "Bàn giao trách nhiệm sang phần nội bộ của Servlet và Reactive server" để quyết định web-runtime kế tiếp đi ra từ kết quả đã chứng minh.

### Scene 4 — Bàn giao trách nhiệm sang phần nội bộ của Servlet và Reactive server

**Time:** `03:01–03:45`

**Visual:**

Vẽ ownership swimlane Boot | Spring MVC/WebFlux | embedded server | network/TLS/proxy; đặt behavior vào đúng lane rồi hiện mũi tên handoff. Với "Bàn giao trách nhiệm sang phần nội bộ của Servlet và Reactive server", đặt input của section và runtime result quan sát được tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Tomcat, Jetty, Undertow và Reactor Netty có connector, handler, worker, event loop, queue, cách triển khai giao thức và mô hình tinh chỉnh riêng. Boot cung cấp property, factory và hook customizer để chạm vào các hệ thống đó nhưng không làm phần nội bộ của chúng trở thành di động giữa các server. Hãy học đủ API riêng theo server để cấu hình yêu cầu qua Boot, rồi bàn giao phần suy luận sâu về container/runtime cho phần chịu trách nhiệm tương ứng. Cách này giữ nội dung học Boot tập trung vào lựa chọn, cấu hình, vòng đời và tích hợp.

**Purpose:**

Cho thấy hệ quả runtime cụ thể của "Bàn giao trách nhiệm sang phần nội bộ của Servlet và Reactive server" và tạo checkpoint quan sát được cho quyết định Boot sở hữu.

## Bàn giao trách nhiệm sang hạ tầng HTTP, TLS và reverse proxy

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:45–04:03`

**Visual:**

Giữ swimlane ownership Boot/framework/server/infrastructure trên màn hình. Làm mờ annotation của "Bàn giao trách nhiệm sang phần nội bộ của Servlet và Reactive server" rồi animate focus sang "Bàn giao trách nhiệm sang hạ tầng HTTP, TLS và reverse proxy"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Section trước đã cho evidence cho "Bàn giao trách nhiệm sang phần nội bộ của Servlet và Reactive server". Đi tiếp trên cùng runtime path tới "Bàn giao trách nhiệm sang hạ tầng HTTP, TLS và reverse proxy" và quan sát input, owner hoặc output nào thay đổi.

**Purpose:**

Giữ evidence từ "Bàn giao trách nhiệm sang phần nội bộ của Servlet và Reactive server" và chỉ đưa thêm cơ chế mới cần cho "Bàn giao trách nhiệm sang hạ tầng HTTP, TLS và reverse proxy".

### Scene 5 — Bàn giao trách nhiệm sang hạ tầng HTTP, TLS và reverse proxy

**Time:** `04:03–04:44`

**Visual:**

Dùng TLS handshake diagram với certificate material ở trái, server.ssl hoặc named SslBundle ở giữa và HTTPS connector ở phải; với SNI cho hostname chọn certificate. Với "Bàn giao trách nhiệm sang hạ tầng HTTP, TLS và reverse proxy", đặt input của section và runtime result quan sát được tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Boot có thể bật nén/HTTP/2, gắn dữ liệu chứng chỉ, tiêu thụ SSL bundle, xử lý forwarded headers và cung cấp các thiết lập proxy riêng theo server. Đây là các điểm tích hợp với những miền kiến thức lớn hơn. Cơ chế giao thức HTTP, lý thuyết TLS/PKI, vận hành chứng chỉ, định tuyến reverse proxy, hành vi load balancer, độ tin cậy mạng và tinh chỉnh socket hệ điều hành vẫn thuộc hạ tầng/bảo mật. Người học Boot cần biết property/hook nào nối sang chúng và từ đâu trách nhiệm chuyên sâu bắt đầu.

**Purpose:**

Giải thích "Bàn giao trách nhiệm sang hạ tầng HTTP, TLS và reverse proxy" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## Mô hình quyết định đầu-cuối của Boot web runtime

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:44–05:00`

**Visual:**

Giữ swimlane ownership Boot/framework/server/infrastructure trên màn hình. Làm mờ annotation của "Bàn giao trách nhiệm sang hạ tầng HTTP, TLS và reverse proxy" rồi animate focus sang "Mô hình quyết định đầu-cuối của Boot web runtime"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Bàn giao trách nhiệm sang hạ tầng HTTP, TLS và reverse proxy" đã rõ. Dependency kế tiếp là "Mô hình quyết định đầu-cuối của Boot web runtime"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Mô hình quyết định đầu-cuối của Boot web runtime" là dependency kế tiếp sau "Bàn giao trách nhiệm sang hạ tầng HTTP, TLS và reverse proxy", đồng thời giữ ownership và runtime state liên tục.

### Scene 6 — Mô hình quyết định đầu-cuối của Boot web runtime

**Time:** `05:00–05:51`

**Visual:**

Vẽ ownership swimlane Boot | Spring MVC/WebFlux | embedded server | network/TLS/proxy; đặt behavior vào đúng lane rồi hiện mũi tên handoff. Với "Mô hình quyết định đầu-cuối của Boot web runtime", đặt `WebApplicationType` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Hãy dùng mô hình này như một chuỗi câu hỏi, không phải checklist để học thuộc. Trước hết chọn `WebApplicationType`, sau đó xác nhận embedded server được hỗ trợ và factory auto-configuration Servlet hoặc Reactive tương ứng. Tiếp theo hỏi `server.*`, property riêng theo server hay `WebServerFactoryCustomizer` hẹp nhất có diễn đạt được yêu cầu không. Sau đó xét các yếu tố triển khai như HTTP/2, TLS hoặc SNI, forwarded headers và cách server shutdown. Cuối cùng là bước tám: vấn đề còn lại thực sự thuộc MVC hoặc WebFlux, phần nội bộ server hay hạ tầng mạng và bảo mật? Câu hỏi cuối này chính là điểm handoff giúp việc chẩn đoán Boot runtime dừng đúng layer.

**Purpose:**

Nối input trong "Mô hình quyết định đầu-cuối của Boot web runtime" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.
