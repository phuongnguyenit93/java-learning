---
video:
  url: ""
---

# Tùy biến server bằng mã

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

## Ưu tiên property, sau đó customizer, cuối cùng factory bean

<!-- VIDEO_SECTION -->

### Scene 1 — Ưu tiên property, sau đó customizer, cuối cùng factory bean

**Time:** `00:00–00:40`

**Visual:**

Vẽ dependency/factory pipeline: starter hoặc user bean → ordered customizers → WebServerFactory → embedded server; làm mờ auto-configured factory ngay khi back-off xảy ra. Với "Ưu tiên property, sau đó customizer, cuối cùng factory bean", đặt `server.*`, `server.tomcat.*`, `server.jetty.*` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Hãy dùng mức customization ít can thiệp nhất vẫn diễn đạt được yêu cầu. Bắt đầu bằng property `server.*` dùng chung, rồi chỉ chuyển sang namespace riêng theo server khi chính yêu cầu phụ thuộc cách triển khai đó. Nếu property vẫn chưa đủ, dùng `WebServerFactoryCustomizer`. Tự cung cấp factory bean là bước can thiệp mạnh nhất vì ứng dụng nhận trách nhiệm tạo factory. Càng đi xuống decision ladder này, mức phụ thuộc server API càng tăng, nên requirement chứ không phải sự quen thuộc với API phải biện minh cho từng bước.

**Purpose:**

Giải thích "Ưu tiên property, sau đó customizer, cuối cùng factory bean" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## WebServerFactoryCustomizer là điểm mở rộng chính bằng mã

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:40–00:55`

**Visual:**

Giữ decision ladder property/customizer/factory trên màn hình. Làm mờ annotation của "Ưu tiên property, sau đó customizer, cuối cùng factory bean" rồi animate focus sang "WebServerFactoryCustomizer là điểm mở rộng chính bằng mã"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Ưu tiên property, sau đó customizer, cuối cùng factory bean" đã rõ. Dependency kế tiếp là "WebServerFactoryCustomizer là điểm mở rộng chính bằng mã"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "WebServerFactoryCustomizer là điểm mở rộng chính bằng mã" là dependency kế tiếp sau "Ưu tiên property, sau đó customizer, cuối cùng factory bean", đồng thời giữ ownership và runtime state liên tục.

### Scene 2 — WebServerFactoryCustomizer là điểm mở rộng chính bằng mã

**Time:** `00:55–01:33`

**Visual:**

Vẽ dependency/factory pipeline: starter hoặc user bean → ordered customizers → WebServerFactory → embedded server; làm mờ auto-configured factory ngay khi back-off xảy ra. Với "WebServerFactoryCustomizer là điểm mở rộng chính bằng mã", đặt `WebServerFactoryCustomizer` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

`WebServerFactoryCustomizer` cho phép mã ứng dụng chỉnh factory Boot đã chọn trước khi server tồn tại. Kiểu generic giới hạn hook vào đúng nhóm factory tương thích, nên customizer cho Tomcat không chạy với Jetty. Khi property chưa diễn đạt được yêu cầu, callback có thể gọi API riêng của factory, còn Boot vẫn chịu trách nhiệm tạo và khởi động web server sau cùng. Hãy để ví dụ Java trên màn hình nhưng phần thuyết minh tập trung vào lifecycle contract thay vì đọc từng dòng phương thức.

**Purpose:**

Nối input trong "WebServerFactoryCustomizer là điểm mở rộng chính bằng mã" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.

## Factory dùng chung và factory riêng theo từng server

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:33–01:48`

**Visual:**

Giữ decision ladder property/customizer/factory trên màn hình. Làm mờ annotation của "WebServerFactoryCustomizer là điểm mở rộng chính bằng mã" rồi animate focus sang "Factory dùng chung và factory riêng theo từng server"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Khi "WebServerFactoryCustomizer là điểm mở rộng chính bằng mã" đã rõ, hãy kiểm boundary kế tiếp: "Factory dùng chung và factory riêng theo từng server". Giữ kết quả trước để cơ chế mới đi ra trực tiếp từ nó.

**Purpose:**

Chuyển từ "WebServerFactoryCustomizer là điểm mở rộng chính bằng mã" sang "Factory dùng chung và factory riêng theo từng server" mà không reset mô hình, đồng thời làm rõ input, owner hoặc output nào thay đổi.

### Scene 3 — Factory dùng chung và factory riêng theo từng server

**Time:** `01:48–02:32`

**Visual:**

Vẽ dependency/factory pipeline: starter hoặc user bean → ordered customizers → WebServerFactory → embedded server; làm mờ auto-configured factory ngay khi back-off xảy ra. Với "Factory dùng chung và factory riêng theo từng server", đặt `ConfigurableWebServerFactory`, `TomcatServletWebServerFactory`, `NettyReactiveWebServerFactory` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Hãy nhắm tới loại factory rộng nhất nhưng vẫn cung cấp khả năng cần dùng. Customizer cho `ConfigurableWebServerFactory` có thể xử lý các mối quan tâm có trên lớp trừu tượng chung. Customizer cho`TomcatServletWebServerFactory` hoặc`NettyReactiveWebServerFactory` là lựa chọn có chủ đích để phụ thuộc vào một cách triển khai server và web stack cụ thể. Phân biệt này quan trọng khi bảo trì. Customizer dùng chung thường vẫn dùng được sau khi đổi server; customizer riêng theo server phải nằm trong danh sách kiểm tra khi chuyển đổi vì API và hành vi của nó gắn với cách triển khai.

**Purpose:**

Biến "Factory dùng chung và factory riêng theo từng server" thành lựa chọn cụ thể bằng cách cho thấy runtime thay đổi gì và tầng cấu hình hoặc customization nào nên dùng tiếp.

## Customizer của Boot và ứng dụng được sắp thứ tự như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:32–02:49`

**Visual:**

Giữ decision ladder property/customizer/factory trên màn hình. Làm mờ annotation của "Factory dùng chung và factory riêng theo từng server" rồi animate focus sang "Customizer của Boot và ứng dụng được sắp thứ tự như thế nào?"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ta đã chốt "Factory dùng chung và factory riêng theo từng server". Giữ kết quả đó trên màn hình; bây giờ chuyển sang "Customizer của Boot và ứng dụng được sắp thứ tự như thế nào?" và chỉ thay quyết định runtime kế tiếp.

**Purpose:**

Nối "Factory dùng chung và factory riêng theo từng server" với "Customizer của Boot và ứng dụng được sắp thứ tự như thế nào?" để quyết định web-runtime kế tiếp đi ra từ kết quả đã chứng minh.

### Scene 4 — Customizer của Boot và ứng dụng được sắp thứ tự như thế nào?

**Time:** `02:49–03:27`

**Visual:**

Vẽ dependency/factory pipeline: starter hoặc user bean → ordered customizers → WebServerFactory → embedded server; làm mờ auto-configured factory ngay khi back-off xảy ra. Với "Customizer của Boot và ứng dụng được sắp thứ tự như thế nào?", đặt `WebServerFactoryCustomizer`, `0`, `Ordered` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Nhiều customizer có thể cùng nhắm tới một factory. Quy tắc ordering của Spring quyết định thứ tự áp dụng, còn `WebServerFactoryCustomizer` do Boot tự động cấu hình dùng order`0`. Customizer của người dùng có thể triển khai`Ordered` hoặc dùng`@Order` khi cần chạy trước/sau một tùy biến khác. Không nên dựa vào thứ tự phát hiện bean tình cờ. Nếu hai customizer cùng thay một trường, hãy thể hiện thứ tự rõ hoặc gom trách nhiệm để trạng thái factory cuối có thể dự đoán được.

**Purpose:**

Cho thấy hệ quả runtime cụ thể của "Customizer của Boot và ứng dụng được sắp thứ tự như thế nào?" và tạo checkpoint quan sát được cho quyết định Boot sở hữu.

## Tự cung cấp WebServerFactory bean

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:27–03:43`

**Visual:**

Giữ decision ladder property/customizer/factory trên màn hình. Làm mờ annotation của "Customizer của Boot và ứng dụng được sắp thứ tự như thế nào?" rồi animate focus sang "Tự cung cấp WebServerFactory bean"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Section trước đã cho evidence cho "Customizer của Boot và ứng dụng được sắp thứ tự như thế nào?". Đi tiếp trên cùng runtime path tới "Tự cung cấp WebServerFactory bean" và quan sát input, owner hoặc output nào thay đổi.

**Purpose:**

Giữ evidence từ "Customizer của Boot và ứng dụng được sắp thứ tự như thế nào?" và chỉ đưa thêm cơ chế mới cần cho "Tự cung cấp WebServerFactory bean".

### Scene 5 — Tự cung cấp WebServerFactory bean

**Time:** `03:43–04:23`

**Visual:**

Vẽ dependency/factory pipeline: starter hoặc user bean → ordered customizers → WebServerFactory → embedded server; làm mờ auto-configured factory ngay khi back-off xảy ra. Với "Tự cung cấp WebServerFactory bean", đặt `ServletWebServerFactory`, `ReactiveWebServerFactory`, `WebServerFactoryCustomizer` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Khai báo riêng bean `ServletWebServerFactory` hoặc`ReactiveWebServerFactory` sẽ làm auto-configuration factory riêng theo cách triển khai của Boot back off. Ứng dụng lúc này tự quyết định cách xây dựng factory. Điều đó không bỏ toàn bộ chuỗi tùy biến của Boot. Các`WebServerFactoryCustomizer` được tự động cấu hình vẫn áp dụng lên factory tùy chỉnh. Vì vậy factory bean nên là lựa chọn cuối khi chính cách xây dựng factory phải thay đổi, và cần kiểm tra sự tương tác giữa trạng thái ban đầu tự cung cấp với customizer của Boot.

**Purpose:**

Giải thích "Tự cung cấp WebServerFactory bean" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## Thêm connector, listener và các mô hình server nâng cao

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:23–04:37`

**Visual:**

Giữ decision ladder property/customizer/factory trên màn hình. Làm mờ annotation của "Tự cung cấp WebServerFactory bean" rồi animate focus sang "Thêm connector, listener và các mô hình server nâng cao"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Tự cung cấp WebServerFactory bean" đã rõ. Dependency kế tiếp là "Thêm connector, listener và các mô hình server nâng cao"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Thêm connector, listener và các mô hình server nâng cao" là dependency kế tiếp sau "Tự cung cấp WebServerFactory bean", đồng thời giữ ownership và runtime state liên tục.

### Scene 6 — Thêm connector, listener và các mô hình server nâng cao

**Time:** `04:37–05:22`

**Visual:**

Dùng decision ladder property → WebServerFactoryCustomizer → server-specific customizer → custom factory bean → connector/listener; chỉ bật sáng nấc đang nói tới. Với "Thêm connector, listener và các mô hình server nâng cao", đặt `server.ssl.*` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Một số mô hình kết nối cần mã phụ thuộc server. Ví dụ phổ biến là cấu hình HTTPS bằng `server.ssl.*` nhưng vẫn muốn thêm một HTTP connector không mã hóa. Spring Boot không biểu diễn đồng thời cặp HTTP + HTTPS connector chỉ bằng các property thông thường, nên connector bổ sung phải được thêm bằng customizer riêng theo server. Nguyên tắc tương tự áp dụng cho listener tùy chỉnh, tài nguyên connector, protocol handler hoặc đối tượng server ngoài mô hình property dùng chung. Hãy giữ những API này tập trung quanh factory thay vì để mã riêng theo container lan vào mã nghiệp vụ.

**Purpose:**

Nối input trong "Thêm connector, listener và các mô hình server nâng cao" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.
