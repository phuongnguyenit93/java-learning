---
video:
  url: ""
---

# Auto-configuration cho Reactive server

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

## Khi nào auto-configuration cho Reactive web server được áp dụng?

<!-- VIDEO_SECTION -->

### Scene 1 — Khi nào auto-configuration cho Reactive web server được áp dụng?

**Time:** `00:00–00:29`

**Visual:**

Giữ runtime diagram của chương nhưng chỉ hiện input, quyết định Boot sở hữu và output server/context của section; làm mờ mọi node khác. Với "Khi nào auto-configuration cho Reactive web server được áp dụng?", đặt `ReactiveWebServerFactoryAutoConfiguration`, `REACTIVE` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

`ReactiveWebServerFactoryAutoConfiguration` là nhánh tương ứng của Reactive runtime. Nó tham gia với ứng dụng web reactive khi các class server/runtime cần thiết có mặt, cấu hình hạ tầng reactive factory và đăng ký cơ chế xử lý customizer trước khi server khởi động. Do đó loại ứng dụng`REACTIVE` là đầu vào của bootstrap/lựa chọn server, không phải một nhãn được gắn sau khi server đã chạy.

**Purpose:**

Giải thích "Khi nào auto-configuration cho Reactive web server được áp dụng?" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## Vì sao Reactor Netty là Reactive server mặc định?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:29–00:44`

**Visual:**

Giữ pipeline Reactive auto-configuration → factory → customizers → server trên màn hình. Làm mờ annotation của "Khi nào auto-configuration cho Reactive web server được áp dụng?" rồi animate focus sang "Vì sao Reactor Netty là Reactive server mặc định?"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Khi nào auto-configuration cho Reactive web server được áp dụng?" đã rõ. Dependency kế tiếp là "Vì sao Reactor Netty là Reactive server mặc định?"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Vì sao Reactor Netty là Reactive server mặc định?" là dependency kế tiếp sau "Khi nào auto-configuration cho Reactive web server được áp dụng?", đồng thời giữ ownership và runtime state liên tục.

### Scene 2 — Vì sao Reactor Netty là Reactive server mặc định?

**Time:** `00:44–01:16`

**Visual:**

Giữ runtime diagram của chương nhưng chỉ hiện input, quyết định Boot sở hữu và output server/context của section; làm mờ mọi node khác. Với "Vì sao Reactor Netty là Reactive server mặc định?", đặt `spring-boot-starter-webflux`, `spring-boot-starter-reactor-netty`, `NettyReactiveWebServerFactory` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Reactor Netty là mặc định vì `spring-boot-starter-webflux` bao gồm`spring-boot-starter-reactor-netty`. Một ứng dụng WebFlux thông thường vì thế có sẵn class cần thiết để Boot tự động cấu hình`NettyReactiveWebServerFactory` mà không cần chọn thêm server. “Mặc định” ở đây là quy ước của dependency. Nó không có nghĩa WebFlux bắt buộc Reactor Netty: Boot 3.3 cũng có thể chạy stack reactive trên các tích hợp được hỗ trợ của Tomcat, Jetty hoặc Undertow.

**Purpose:**

Nối input trong "Vì sao Reactor Netty là Reactive server mặc định?" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.

## Các Reactive factory cho Netty, Tomcat, Jetty và Undertow

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:16–01:31`

**Visual:**

Giữ pipeline Reactive auto-configuration → factory → customizers → server trên màn hình. Làm mờ annotation của "Vì sao Reactor Netty là Reactive server mặc định?" rồi animate focus sang "Các Reactive factory cho Netty, Tomcat, Jetty và Undertow"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Khi "Vì sao Reactor Netty là Reactive server mặc định?" đã rõ, hãy kiểm boundary kế tiếp: "Các Reactive factory cho Netty, Tomcat, Jetty và Undertow". Giữ kết quả trước để cơ chế mới đi ra trực tiếp từ nó.

**Purpose:**

Chuyển từ "Vì sao Reactor Netty là Reactive server mặc định?" sang "Các Reactive factory cho Netty, Tomcat, Jetty và Undertow" mà không reset mô hình, đồng thời làm rõ input, owner hoặc output nào thay đổi.

### Scene 3 — Các Reactive factory cho Netty, Tomcat, Jetty và Undertow

**Time:** `01:31–02:03`

**Visual:**

Giữ runtime diagram của chương nhưng chỉ hiện input, quyết định Boot sở hữu và output server/context của section; làm mờ mọi node khác. Với "Các Reactive factory cho Netty, Tomcat, Jetty và Undertow", đặt `NettyReactiveWebServerFactory`, `TomcatReactiveWebServerFactory`, `JettyReactiveWebServerFactory` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Boot cung cấp `NettyReactiveWebServerFactory`,`TomcatReactiveWebServerFactory`,`JettyReactiveWebServerFactory` và`UndertowReactiveWebServerFactory`. Cách triển khai được chọn phụ thuộc mô hình ứng dụng reactive và các class server tương thích có trên classpath. Vì vậy chỉ thấy “Tomcat” chưa đủ để kết luận ứng dụng đang chạy MVC. Tomcat có thể chạy ứng dụng Servlet hoặc tham gia tích hợp reactive server; loại ứng dụng web và factory tương ứng mới cho biết Boot đang đi theo nhánh nào.

**Purpose:**

Biến "Các Reactive factory cho Netty, Tomcat, Jetty và Undertow" thành lựa chọn cụ thể bằng cách cho thấy runtime thay đổi gì và tầng cấu hình hoặc customization nào nên dùng tiếp.

## Cấu hình server nằm ở đâu trong auto-configuration của Reactive factory?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:03–02:19`

**Visual:**

Giữ pipeline Reactive auto-configuration → factory → customizers → server trên màn hình. Làm mờ annotation của "Các Reactive factory cho Netty, Tomcat, Jetty và Undertow" rồi animate focus sang "Cấu hình server nằm ở đâu trong auto-configuration của Reactive factory?"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ta đã chốt "Các Reactive factory cho Netty, Tomcat, Jetty và Undertow". Giữ kết quả đó trên màn hình; bây giờ chuyển sang "Cấu hình server nằm ở đâu trong auto-configuration của Reactive factory?" và chỉ thay quyết định runtime kế tiếp.

**Purpose:**

Nối "Các Reactive factory cho Netty, Tomcat, Jetty và Undertow" với "Cấu hình server nằm ở đâu trong auto-configuration của Reactive factory?" để quyết định web-runtime kế tiếp đi ra từ kết quả đã chứng minh.

### Scene 4 — Cấu hình server nằm ở đâu trong auto-configuration của Reactive factory?

**Time:** `02:19–02:56`

**Visual:**

Vẽ dependency/factory pipeline: starter hoặc user bean → ordered customizers → WebServerFactory → embedded server; làm mờ auto-configured factory ngay khi back-off xảy ra. Với "Cấu hình server nằm ở đâu trong auto-configuration của Reactive factory?", đặt `ServerProperties`, `ReactiveWebServerFactoryCustomizer`, `server.*` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

`ServerProperties` cũng cấp dữ liệu cho đường reactive server. Boot cung cấp`ReactiveWebServerFactoryCustomizer` cho thiết lập server dùng chung và các customizer riêng theo server bổ sung cho namespace của cách triển khai khi phù hợp. Mô hình vẫn giống phía Servlet: cấu hình được phân giải trước, customizer áp dụng lên factory đã chọn, rồi context ứng dụng web reactive mới tạo server. Nhờ mô hình chung này, các chương sau có thể nói về`server.*` một lần thay vì lặp lại cho từng stack.

**Purpose:**

Cho thấy hệ quả runtime cụ thể của "Cấu hình server nằm ở đâu trong auto-configuration của Reactive factory?" và tạo checkpoint quan sát được cho quyết định Boot sở hữu.

## Back-off khi ứng dụng tự cung cấp Reactive WebServerFactory

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:56–03:13`

**Visual:**

Giữ pipeline Reactive auto-configuration → factory → customizers → server trên màn hình. Làm mờ annotation của "Cấu hình server nằm ở đâu trong auto-configuration của Reactive factory?" rồi animate focus sang "Back-off khi ứng dụng tự cung cấp Reactive WebServerFactory"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Section trước đã cho evidence cho "Cấu hình server nằm ở đâu trong auto-configuration của Reactive factory?". Đi tiếp trên cùng runtime path tới "Back-off khi ứng dụng tự cung cấp Reactive WebServerFactory" và quan sát input, owner hoặc output nào thay đổi.

**Purpose:**

Giữ evidence từ "Cấu hình server nằm ở đâu trong auto-configuration của Reactive factory?" và chỉ đưa thêm cơ chế mới cần cho "Back-off khi ứng dụng tự cung cấp Reactive WebServerFactory".

### Scene 5 — Back-off khi ứng dụng tự cung cấp Reactive WebServerFactory

**Time:** `03:13–03:58`

**Visual:**

Vẽ dependency/factory pipeline: starter hoặc user bean → ordered customizers → WebServerFactory → embedded server; làm mờ auto-configured factory ngay khi back-off xảy ra. Với "Back-off khi ứng dụng tự cung cấp Reactive WebServerFactory", đặt `ReactiveWebServerFactory` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Cấu hình reactive server factory back off khi ứng dụng tự cung cấp `ReactiveWebServerFactory` bean. Khi đó ứng dụng thay lựa chọn factory của Boot, vì vậy chỉ nên làm khi property và customizer có mục tiêu cụ thể không thể biểu diễn yêu cầu. Các factory customizer do Boot tự động cấu hình vẫn áp dụng lên factory tùy chỉnh. Nếu factory tùy chỉnh có vẻ bỏ qua hoặc ghi đè thiết lập ứng dụng, hãy kiểm tra cả trạng thái ban đầu của factory lẫn chuỗi customizer đã sắp thứ tự trước khi kết luận auto-configuration đã bị vô hiệu hoàn toàn.

**Purpose:**

Giải thích "Back-off khi ứng dụng tự cung cấp Reactive WebServerFactory" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## Bootstrap Reactive server và ranh giới với cơ chế xử lý request của Spring WebFlux

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:58–04:15`

**Visual:**

Giữ pipeline Reactive auto-configuration → factory → customizers → server trên màn hình. Làm mờ annotation của "Back-off khi ứng dụng tự cung cấp Reactive WebServerFactory" rồi animate focus sang "Bootstrap Reactive server và ranh giới với cơ chế xử lý request của Spring WebFlux"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Back-off khi ứng dụng tự cung cấp Reactive WebServerFactory" đã rõ. Dependency kế tiếp là "Bootstrap Reactive server và ranh giới với cơ chế xử lý request của Spring WebFlux"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Bootstrap Reactive server và ranh giới với cơ chế xử lý request của Spring WebFlux" là dependency kế tiếp sau "Back-off khi ứng dụng tự cung cấp Reactive WebServerFactory", đồng thời giữ ownership và runtime state liên tục.

### Scene 6 — Bootstrap Reactive server và ranh giới với cơ chế xử lý request của Spring WebFlux

**Time:** `04:15–04:48`

**Visual:**

Vẽ ownership swimlane Boot | Spring MVC/WebFlux | embedded server | network/TLS/proxy; đặt behavior vào đúng lane rồi hiện mũi tên handoff. Với "Bootstrap Reactive server và ranh giới với cơ chế xử lý request của Spring WebFlux", đặt `HttpHandler` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Reactive server auto-configuration chịu trách nhiệm cho bootstrap server và tích hợp vòng đời. Spring WebFlux sở hữu mô hình xử lý HTTP phía trên: `HttpHandler`, routing, annotated controller, codec, filter, cách kết hợp luồng reactive và ngữ nghĩa back-pressure. Giữ ranh giới này rõ khi tinh chỉnh/chẩn đoán. Vấn đề cổng, TLS, nén hoặc tài nguyên server trước hết thuộc Boot/server runtime. Vấn đề route, codec hoặc chuỗi xử lý reactive thuộc WebFlux và Reactor.

**Purpose:**

Nối input trong "Bootstrap Reactive server và ranh giới với cơ chế xử lý request của Spring WebFlux" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.
