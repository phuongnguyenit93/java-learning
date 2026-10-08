---
video:
  url: ""
---

# Khả năng HTTP, nén và thiết lập kết nối

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

## Nén HTTP response

<!-- VIDEO_SECTION -->

### Scene 1 — Nén HTTP response

**Time:** `00:00–00:34`

**Visual:**

Hiện một HTTP exchange; đổi đúng layer của feature: response body cho compression, connection/protocol cho HTTP/2, request boundary cho header limit, terminal log cho access logging. Với "Nén HTTP response", đặt `server.compression.*`, `server.compression.enabled=true`, `2KB` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Boot cung cấp nén response qua `server.compression.*`. Tính năng này mặc định tắt;`server.compression.enabled=true` bật nén trên embedded server được hỗ trợ. Boot 3.3 hỗ trợ nén response với Jetty, Tomcat, Reactor Netty và Undertow. Nén còn có điều kiện áp dụng. Kích thước response tối thiểu mặc định là`2KB`, và chỉ các MIME type được cấu hình mới được nén. Dùng`server.compression.min-response-size` và`server.compression.mime-types` để điều chỉnh thay vì giả định mọi response đều được nén.

**Purpose:**

Giải thích "Nén HTTP response" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## Bật HTTP/2 và khả năng hỗ trợ theo server

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:34–00:47`

**Visual:**

Giữ sơ đồ HTTP exchange và server capability trên màn hình. Làm mờ annotation của "Nén HTTP response" rồi animate focus sang "Bật HTTP/2 và khả năng hỗ trợ theo server"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Nén HTTP response" đã rõ. Dependency kế tiếp là "Bật HTTP/2 và khả năng hỗ trợ theo server"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Bật HTTP/2 và khả năng hỗ trợ theo server" là dependency kế tiếp sau "Nén HTTP response", đồng thời giữ ownership và runtime state liên tục.

### Scene 2 — Bật HTTP/2 và khả năng hỗ trợ theo server

**Time:** `00:47–01:30`

**Visual:**

Đặt property sources ở trái, ServerProperties ở giữa và server factory ở phải; tô riêng portable server.* và một namespace server-specific để thấy hai tầng cấu hình. Với "Bật HTTP/2 và khả năng hỗ trợ theo server", đặt `server.http2.enabled=true`, `h2`, `h2c` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

`server.http2.enabled=true` là công tắc dùng chung của Boot cho HTTP/2. Khi SSL bật, server dùng HTTP/2 qua TLS (`h2`); khi SSL không bật, server được hỗ trợ dùng HTTP/2 không mã hóa (`h2c`). Đây là khả năng của server, không phải tính năng request mapping của MVC hay WebFlux. Chi tiết hỗ trợ vẫn khác theo server. Tomcat 10.1 dùng trong Boot 3.3 hỗ trợ`h2` và`h2c` sẵn, còn Jetty cần thêm HTTP/2 server dependency. Nếu công tắc dùng chung không cho hành vi mong đợi, hãy kiểm tra điều kiện tiên quyết của chính server đang dùng.

**Purpose:**

Nối input trong "Bật HTTP/2 và khả năng hỗ trợ theo server" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.

## Giới hạn kích thước request header

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:30–01:44`

**Visual:**

Giữ sơ đồ HTTP exchange và server capability trên màn hình. Làm mờ annotation của "Bật HTTP/2 và khả năng hỗ trợ theo server" rồi animate focus sang "Giới hạn kích thước request header"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Khi "Bật HTTP/2 và khả năng hỗ trợ theo server" đã rõ, hãy kiểm boundary kế tiếp: "Giới hạn kích thước request header". Giữ kết quả trước để cơ chế mới đi ra trực tiếp từ nó.

**Purpose:**

Chuyển từ "Bật HTTP/2 và khả năng hỗ trợ theo server" sang "Giới hạn kích thước request header" mà không reset mô hình, đồng thời làm rõ input, owner hoặc output nào thay đổi.

### Scene 3 — Giới hạn kích thước request header

**Time:** `01:44–02:26`

**Visual:**

Hiện một HTTP exchange; đổi đúng layer của feature: response body cho compression, connection/protocol cho HTTP/2, request boundary cho header limit, terminal log cho access logging. Với "Giới hạn kích thước request header", đặt `server.max-http-request-header-size` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

`server.max-http-request-header-size` biểu diễn giới hạn chung cho HTTP request header. Nó hữu ích để tránh metadata của request quá lớn và để đồng bộ giới hạn của ứng dụng với reverse proxy/load balancer ở phía trước. Không nên suy ra một property dùng chung sẽ bao phủ mọi giới hạn liên quan header trên mọi server. Giới hạn response header hoặc điều khiển parser/connector sâu hơn có thể cần property/API riêng theo server. Hãy bắt đầu từ giới hạn request dùng chung của Boot và chỉ xuống sâu hơn khi môi trường triển khai thực sự cần.

**Purpose:**

Biến "Giới hạn kích thước request header" thành lựa chọn cụ thể bằng cách cho thấy runtime thay đổi gì và tầng cấu hình hoặc customization nào nên dùng tiếp.

## Các thiết lập HTTP ở tầng kết nối và server

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:26–02:41`

**Visual:**

Giữ sơ đồ HTTP exchange và server capability trên màn hình. Làm mờ annotation của "Giới hạn kích thước request header" rồi animate focus sang "Các thiết lập HTTP ở tầng kết nối và server"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ta đã chốt "Giới hạn kích thước request header". Giữ kết quả đó trên màn hình; bây giờ chuyển sang "Các thiết lập HTTP ở tầng kết nối và server" và chỉ thay quyết định runtime kế tiếp.

**Purpose:**

Nối "Giới hạn kích thước request header" với "Các thiết lập HTTP ở tầng kết nối và server" để quyết định web-runtime kế tiếp đi ra từ kết quả đã chứng minh.

### Scene 4 — Các thiết lập HTTP ở tầng kết nối và server

**Time:** `02:41–03:28`

**Visual:**

Hiện một HTTP exchange; đổi đúng layer của feature: response body cho compression, connection/protocol cho HTTP/2, request boundary cho header limit, terminal log cho access logging. Với "Các thiết lập HTTP ở tầng kết nối và server", đặt input của section và runtime result quan sát được tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Hàng đợi kết nối, hành vi khi nhàn rỗi, tài nguyên worker/thread, tùy chọn giao thức mức thấp và các điều khiển tương tự thường phụ thuộc server vì từng cách triển khai có mô hình runtime khác nhau. Boot cung cấp nhiều điều khiển trong namespace tương ứng và để trường hợp hiếm cho factory customizer. Chỉ tinh chỉnh sau khi đã xác định server cụ thể và triệu chứng vận hành cụ thể. Một property dành cho Tomcat không có ý nghĩa dùng chung trên Reactor Netty, và thiết lập event loop của Netty cũng không nên được trình bày như quy tắc chung của Spring Boot.

**Purpose:**

Cho thấy hệ quả runtime cụ thể của "Các thiết lập HTTP ở tầng kết nối và server" và tạo checkpoint quan sát được cho quyết định Boot sở hữu.

## Access log của embedded server

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:28–03:43`

**Visual:**

Giữ sơ đồ HTTP exchange và server capability trên màn hình. Làm mờ annotation của "Các thiết lập HTTP ở tầng kết nối và server" rồi animate focus sang "Access log của embedded server"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Section trước đã cho evidence cho "Các thiết lập HTTP ở tầng kết nối và server". Đi tiếp trên cùng runtime path tới "Access log của embedded server" và quan sát input, owner hoặc output nào thay đổi.

**Purpose:**

Giữ evidence từ "Các thiết lập HTTP ở tầng kết nối và server" và chỉ đưa thêm cơ chế mới cần cho "Access log của embedded server".

### Scene 5 — Access log của embedded server

**Time:** `03:43–04:22`

**Visual:**

Hiện một HTTP exchange; đổi đúng layer của feature: response body cho compression, connection/protocol cho HTTP/2, request boundary cho header limit, terminal log cho access logging. Với "Access log của embedded server", đặt `server.tomcat.accesslog.*`, `server.undertow.accesslog.*`, `server.jetty.accesslog.*` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Access log của embedded server ghi lại quan sát ở tầng server như địa chỉ remote, request line, status, thời gian xử lý và số byte. Nó khác logging của ứng dụng được ghi trong controller, filter hoặc mã nghiệp vụ. Access log được cấu hình theo server. Hướng dẫn Spring Boot 3.3 đưa ví dụ cho Tomcat dưới `server.tomcat.accesslog.*`, Undertow dưới `server.undertow.accesslog.*` và Jetty dưới `server.jetty.accesslog.*`. Token định dạng, vị trí file, cơ chế xoay log và trường cụ thể là mối quan tâm của cách triển khai server đã chọn.

**Purpose:**

Giải thích "Access log của embedded server" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## Điều khiển HTTP dùng chung và khả năng riêng theo server

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:22–04:36`

**Visual:**

Giữ sơ đồ HTTP exchange và server capability trên màn hình. Làm mờ annotation của "Access log của embedded server" rồi animate focus sang "Điều khiển HTTP dùng chung và khả năng riêng theo server"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Access log của embedded server" đã rõ. Dependency kế tiếp là "Điều khiển HTTP dùng chung và khả năng riêng theo server"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Điều khiển HTTP dùng chung và khả năng riêng theo server" là dependency kế tiếp sau "Access log của embedded server", đồng thời giữ ownership và runtime state liên tục.

### Scene 6 — Điều khiển HTTP dùng chung và khả năng riêng theo server

**Time:** `04:36–05:29`

**Visual:**

Đặt property sources ở trái, ServerProperties ở giữa và server factory ở phải; tô riêng portable server.* và một namespace server-specific để thấy hai tầng cấu hình. Với "Điều khiển HTTP dùng chung và khả năng riêng theo server", đặt input của section và runtime result quan sát được tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Một property dùng chung của Boot có nghĩa “một ý định cấu hình cho nhiều tích hợp được hỗ trợ”. Nó không đảm bảo các server có cùng cách triển khai, cùng mặc định bên dưới Boot hoặc cùng hành vi ở trường hợp biên. Nén, HTTP/2, giới hạn request header và graceful shutdown đều thể hiện mẫu này. Hãy dùng điều khiển dùng chung để diễn đạt ý định của ứng dụng, rồi dùng thiết lập riêng theo server cho yêu cầu phụ thuộc cách triển khai. Nếu quyết định ở môi trường production cần đi sâu vào giao thức HTTP, hành vi proxy, tinh chỉnh kernel/mạng hoặc phần nội bộ container thì nên bàn giao sang phần học hạ tầng tương ứng.

**Purpose:**

Nối input trong "Điều khiển HTTP dùng chung và khả năng riêng theo server" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.
