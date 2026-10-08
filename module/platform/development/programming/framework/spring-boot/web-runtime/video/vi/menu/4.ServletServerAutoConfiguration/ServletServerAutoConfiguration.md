---
video:
  url: ""
---

# Auto-configuration cho Servlet server

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

## Khi nào auto-configuration cho Servlet web server được áp dụng?

<!-- VIDEO_SECTION -->

### Scene 1 — Khi nào auto-configuration cho Servlet web server được áp dụng?

**Time:** `00:00–00:39`

**Visual:**

Giữ runtime diagram của chương nhưng chỉ hiện input, quyết định Boot sở hữu và output server/context của section; làm mờ mọi node khác. Với "Khi nào auto-configuration cho Servlet web server được áp dụng?", đặt `ServletWebServerFactoryAutoConfiguration` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

`ServletWebServerFactoryAutoConfiguration` tham gia khi Boot đang chạy một ứng dụng web Servlet và các class cần thiết của server có mặt. Trách nhiệm của nó là lắp ráp hạ tầng embedded Servlet server của Boot, bao gồm các property cấu hình và cơ chế xử lý customizer cho factory được chọn. Điều kiện quan trọng là mô hình runtime đã được xác định từ trước. Chỉ có một server JAR trên classpath chưa đủ để làm Servlet server factory xuất hiện trong ứng dụng cố ý chạy không phải web hoặc reactive.

**Purpose:**

Giải thích "Khi nào auto-configuration cho Servlet web server được áp dụng?" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## Các Servlet factory cho Tomcat, Jetty và Undertow

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:39–00:54`

**Visual:**

Giữ pipeline Servlet auto-configuration → factory → customizers → server trên màn hình. Làm mờ annotation của "Khi nào auto-configuration cho Servlet web server được áp dụng?" rồi animate focus sang "Các Servlet factory cho Tomcat, Jetty và Undertow"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Khi nào auto-configuration cho Servlet web server được áp dụng?" đã rõ. Dependency kế tiếp là "Các Servlet factory cho Tomcat, Jetty và Undertow"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Các Servlet factory cho Tomcat, Jetty và Undertow" là dependency kế tiếp sau "Khi nào auto-configuration cho Servlet web server được áp dụng?", đồng thời giữ ownership và runtime state liên tục.

### Scene 2 — Các Servlet factory cho Tomcat, Jetty và Undertow

**Time:** `00:54–01:26`

**Visual:**

Giữ runtime diagram của chương nhưng chỉ hiện input, quyết định Boot sở hữu và output server/context của section; làm mờ mọi node khác. Với "Các Servlet factory cho Tomcat, Jetty và Undertow", đặt `TomcatServletWebServerFactory`, `JettyServletWebServerFactory`, `UndertowServletWebServerFactory` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Boot cung cấp các Servlet factory cụ thể cho embedded container được hỗ trợ: `TomcatServletWebServerFactory`,`JettyServletWebServerFactory` và`UndertowServletWebServerFactory`. Điều kiện classpath quyết định cấu hình dành cho cách triển khai nào đủ điều kiện. Ứng dụng thường tiếp cận các factory này gián tiếp qua starter. Vì vậy thay dependency của server là lựa chọn đầu tiên: nó thay cách triển khai đủ điều kiện mà không thay cơ chế vòng đời/cấu hình của Boot.

**Purpose:**

Nối input trong "Các Servlet factory cho Tomcat, Jetty và Undertow" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.

## Cấu hình server nằm ở đâu trong auto-configuration của Servlet factory?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:26–01:42`

**Visual:**

Giữ pipeline Servlet auto-configuration → factory → customizers → server trên màn hình. Làm mờ annotation của "Các Servlet factory cho Tomcat, Jetty và Undertow" rồi animate focus sang "Cấu hình server nằm ở đâu trong auto-configuration của Servlet factory?"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Khi "Các Servlet factory cho Tomcat, Jetty và Undertow" đã rõ, hãy kiểm boundary kế tiếp: "Cấu hình server nằm ở đâu trong auto-configuration của Servlet factory?". Giữ kết quả trước để cơ chế mới đi ra trực tiếp từ nó.

**Purpose:**

Chuyển từ "Các Servlet factory cho Tomcat, Jetty và Undertow" sang "Cấu hình server nằm ở đâu trong auto-configuration của Servlet factory?" mà không reset mô hình, đồng thời làm rõ input, owner hoặc output nào thay đổi.

### Scene 3 — Cấu hình server nằm ở đâu trong auto-configuration của Servlet factory?

**Time:** `01:42–02:15`

**Visual:**

Vẽ dependency/factory pipeline: starter hoặc user bean → ordered customizers → WebServerFactory → embedded server; làm mờ auto-configured factory ngay khi back-off xảy ra. Với "Cấu hình server nằm ở đâu trong auto-configuration của Servlet factory?", đặt `server.*`, `ServerProperties`, `externalized-configuration` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Auto-configuration không chỉ tạo factory. Boot bind cấu hình `server.*` vào `ServerProperties` rồi cung cấp các customizer áp dụng thiết lập dùng chung và riêng theo server lên factory trước khi server được tạo. Có thể hình dung luồng:`cấu hình bên ngoài → ServerProperties → customizer của Boot → ServletWebServerFactory → WebServer`. Precedence và relaxed binding thuộc module`externalized-configuration`; ở đây trọng tâm là cấu hình server đã được phân giải sẽ tác động lên web runtime như thế nào.

**Purpose:**

Biến "Cấu hình server nằm ở đâu trong auto-configuration của Servlet factory?" thành lựa chọn cụ thể bằng cách cho thấy runtime thay đổi gì và tầng cấu hình hoặc customization nào nên dùng tiếp.

## Back-off khi ứng dụng tự cung cấp Servlet WebServerFactory

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:15–02:31`

**Visual:**

Giữ pipeline Servlet auto-configuration → factory → customizers → server trên màn hình. Làm mờ annotation của "Cấu hình server nằm ở đâu trong auto-configuration của Servlet factory?" rồi animate focus sang "Back-off khi ứng dụng tự cung cấp Servlet WebServerFactory"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ta đã chốt "Cấu hình server nằm ở đâu trong auto-configuration của Servlet factory?". Giữ kết quả đó trên màn hình; bây giờ chuyển sang "Back-off khi ứng dụng tự cung cấp Servlet WebServerFactory" và chỉ thay quyết định runtime kế tiếp.

**Purpose:**

Nối "Cấu hình server nằm ở đâu trong auto-configuration của Servlet factory?" với "Back-off khi ứng dụng tự cung cấp Servlet WebServerFactory" để quyết định web-runtime kế tiếp đi ra từ kết quả đã chứng minh.

### Scene 4 — Back-off khi ứng dụng tự cung cấp Servlet WebServerFactory

**Time:** `02:31–03:13`

**Visual:**

Vẽ dependency/factory pipeline: starter hoặc user bean → ordered customizers → WebServerFactory → embedded server; làm mờ auto-configured factory ngay khi back-off xảy ra. Với "Back-off khi ứng dụng tự cung cấp Servlet WebServerFactory", đặt `ServletWebServerFactory`, `WebServerFactoryCustomizer` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Các cấu hình embedded Servlet factory được thiết kế để back off khi ứng dụng tự khai báo `ServletWebServerFactory` bean. Đây là hook thay thế mạnh vì ứng dụng đã tự nhận trách nhiệm chọn và tạo factory. Hãy dùng cơ chế này có chủ đích. Tự cung cấp factory bean can thiệp sâu hơn property hoặc customizer và làm giảm khả năng chuyển đổi giữa các server. Các`WebServerFactoryCustomizer` do Boot tự động cấu hình vẫn được áp dụng lên factory tùy chỉnh, nên thay factory không có nghĩa toàn bộ tùy biến của Boot biến mất.

**Purpose:**

Cho thấy hệ quả runtime cụ thể của "Back-off khi ứng dụng tự cung cấp Servlet WebServerFactory" và tạo checkpoint quan sát được cho quyết định Boot sở hữu.

## Customizer của Boot và ứng dụng quanh Servlet factory

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:13–03:29`

**Visual:**

Giữ pipeline Servlet auto-configuration → factory → customizers → server trên màn hình. Làm mờ annotation của "Back-off khi ứng dụng tự cung cấp Servlet WebServerFactory" rồi animate focus sang "Customizer của Boot và ứng dụng quanh Servlet factory"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Section trước đã cho evidence cho "Back-off khi ứng dụng tự cung cấp Servlet WebServerFactory". Đi tiếp trên cùng runtime path tới "Customizer của Boot và ứng dụng quanh Servlet factory" và quan sát input, owner hoặc output nào thay đổi.

**Purpose:**

Giữ evidence từ "Back-off khi ứng dụng tự cung cấp Servlet WebServerFactory" và chỉ đưa thêm cơ chế mới cần cho "Customizer của Boot và ứng dụng quanh Servlet factory".

### Scene 5 — Customizer của Boot và ứng dụng quanh Servlet factory

**Time:** `03:29–04:08`

**Visual:**

Vẽ dependency/factory pipeline: starter hoặc user bean → ordered customizers → WebServerFactory → embedded server; làm mờ auto-configured factory ngay khi back-off xảy ra. Với "Customizer của Boot và ứng dụng quanh Servlet factory", đặt `ServletWebServerFactory`, `WebServerFactoryCustomizer`, `0` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Customizer của Boot và ứng dụng được thu thập rồi áp dụng lên `ServletWebServerFactory` trước khi factory tạo server. Các`WebServerFactoryCustomizer` do Boot tự động cấu hình dùng order`0`; customizer của người dùng không khai báo thứ tự cụ thể thường chạy sau nhóm customizer của Boot đó. Thứ tự quan trọng khi hai customizer thay cùng một thiết lập. Hãy ưu tiên configuration property nếu đã có; chỉ dùng thứ tự tường minh khi mã thực sự cần tinh chỉnh hoặc ghi đè trạng thái factory có chủ đích.

**Purpose:**

Giải thích "Customizer của Boot và ứng dụng quanh Servlet factory" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## Bootstrap Servlet server và ranh giới với cơ chế xử lý request của Spring MVC

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:08–04:25`

**Visual:**

Giữ pipeline Servlet auto-configuration → factory → customizers → server trên màn hình. Làm mờ annotation của "Customizer của Boot và ứng dụng quanh Servlet factory" rồi animate focus sang "Bootstrap Servlet server và ranh giới với cơ chế xử lý request của Spring MVC"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Customizer của Boot và ứng dụng quanh Servlet factory" đã rõ. Dependency kế tiếp là "Bootstrap Servlet server và ranh giới với cơ chế xử lý request của Spring MVC"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Bootstrap Servlet server và ranh giới với cơ chế xử lý request của Spring MVC" là dependency kế tiếp sau "Customizer của Boot và ứng dụng quanh Servlet factory", đồng thời giữ ownership và runtime state liên tục.

### Scene 6 — Bootstrap Servlet server và ranh giới với cơ chế xử lý request của Spring MVC

**Time:** `04:25–05:08`

**Visual:**

Vẽ ownership swimlane Boot | Spring MVC/WebFlux | embedded server | network/TLS/proxy; đặt behavior vào đúng lane rồi hiện mũi tên handoff. Với "Bootstrap Servlet server và ranh giới với cơ chế xử lý request của Spring MVC", đặt `DispatcherServlet` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Servlet server auto-configuration đưa server hỗ trợ Servlet vào trạng thái chạy và nối nó với vòng đời của Boot. Cơ chế xử lý request của Spring MVC bắt đầu ở ranh giới khác: `DispatcherServlet`, handler mapping, controller, argument resolution, message conversion, interceptor và MVC error handling thuộc Spring Framework web. Ranh giới này hữu ích khi chẩn đoán. Nếu ứng dụng không bind được cổng, hãy kiểm tra loại ứng dụng, server dependency, factory auto-configuration và thiết lập server. Nếu server đã nhận kết nối nhưng controller mapping sai, hãy chuyển điều tra sang lớp xử lý request của MVC.

**Purpose:**

Nối input trong "Bootstrap Servlet server và ranh giới với cơ chế xử lý request của Spring MVC" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.
