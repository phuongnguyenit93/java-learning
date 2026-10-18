---
video:
  url: ""
---

# Forwarded headers và triển khai sau reverse proxy

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

## Vì sao triển khai sau proxy làm thay đổi request metadata?

<!-- VIDEO_SECTION -->

### Scene 1 — Vì sao triển khai sau proxy làm thay đổi request metadata?

**Time:** `00:00–00:48`

**Visual:**

Vẽ client → reverse proxy → Boot app; so host/scheme/remote address trước và sau proxy, rồi đặt NONE/NATIVE/FRAMEWORK tại node thực sự xử lý header. Với "Vì sao triển khai sau proxy làm thay đổi request metadata?", đặt `10.0.0.5:8080`, `https://example.org`, `443` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Khi ứng dụng chạy sau reverse proxy, kết nối mà embedded server nhìn thấy có thể khác request công khai bên ngoài. Ứng dụng có thể nhận lưu lượng tại `10.0.0.5:8080` qua HTTP trong khi máy khách thật sự truy cập`https://example.org` trên cổng`443`. Nếu bỏ qua khác biệt này, redirect, liên kết được sinh ra, kiểm tra scheme và thông tin địa chỉ máy khách có thể sai. Forwarded headers mang một phần thông tin của request ban đầu qua chặng proxy. Trách nhiệm của Boot là chọn cách ứng dụng/server tiêu thụ metadata đó; định tuyến proxy và việc header được tạo như thế nào thuộc hạ tầng.

**Purpose:**

Giải thích "Vì sao triển khai sau proxy làm thay đổi request metadata?" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## Các chiến lược NONE, NATIVE và FRAMEWORK

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:48–01:03`

**Visual:**

Giữ đường client → trusted proxy → request metadata của Boot trên màn hình. Làm mờ annotation của "Vì sao triển khai sau proxy làm thay đổi request metadata?" rồi animate focus sang "Các chiến lược NONE, NATIVE và FRAMEWORK"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Vì sao triển khai sau proxy làm thay đổi request metadata?" đã rõ. Dependency kế tiếp là "Các chiến lược NONE, NATIVE và FRAMEWORK"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Các chiến lược NONE, NATIVE và FRAMEWORK" là dependency kế tiếp sau "Vì sao triển khai sau proxy làm thay đổi request metadata?", đồng thời giữ ownership và runtime state liên tục.

### Scene 2 — Các chiến lược NONE, NATIVE và FRAMEWORK

**Time:** `01:03–01:38`

**Visual:**

Giữ runtime diagram của chương nhưng chỉ hiện input, quyết định Boot sở hữu và output server/context của section; làm mờ mọi node khác. Với "Các chiến lược NONE, NATIVE và FRAMEWORK", đặt `server.forward-headers-strategy`, `NONE`, `NATIVE` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

`server.forward-headers-strategy` chọn chế độ xử lý của Boot.`NONE` không bật xử lý forwarded header.`NATIVE` giao việc xử lý cho cơ chế native của embedded server.`FRAMEWORK` dùng hỗ trợ của Spring Framework trong stack ứng dụng. Chọn chế độ theo hợp đồng triển khai. Nếu proxy cung cấp các header thông dụng và server xử lý đúng yêu cầu thì`NATIVE` giữ logic gần server. Nếu ứng dụng cần mô hình biến đổi của Spring Framework thì chọn`FRAMEWORK`.

**Purpose:**

Nối input trong "Các chiến lược NONE, NATIVE và FRAMEWORK" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.

### Scene 3 — Runtime evidence

**Time:** `01:38–02:13`

**Visual:**

Mở WebRuntimeExperimentController.requestMetadata() và WebRuntimeExperimentService.requestMetadata(). Ở cạnh mã nguồn, đặt ba nhãn NONE, NATIVE, FRAMEWORK dưới property server.forward-headers-strategy; khoanh các field scheme, secure, serverName, serverPort, remoteAddress và requestUrl mà endpoint trả về. Với "Các chiến lược NONE, NATIVE và FRAMEWORK", đặt input của section và runtime result quan sát được tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Step 5 đã chuẩn bị một điểm quan sát phù hợp cho ba chiến lược này. Endpoint /spring-boot/web-runtime/request-metadata không tự quyết định forwarded headers; nó chỉ đọc metadata mà request hiện có sau khi strategy đã được áp dụng. Vì vậy ta có thể giữ cùng một endpoint và thay đổi strategy để quan sát lớp nào đang chịu trách nhiệm. Ở phần Spring Framework phía sau, ta sẽ chạy một request proxy-style cụ thể để xem sự thay đổi đó.

**Purpose:**

Dùng endpoint runtime của Step 5 để kiểm chứng "Các chiến lược NONE, NATIVE và FRAMEWORK" trên context/server đang chạy thay vì xem sơ đồ là bằng chứng.

## Khi cơ chế forwarded headers native của server đã đủ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:13–02:28`

**Visual:**

Giữ đường client → trusted proxy → request metadata của Boot trên màn hình. Làm mờ annotation của "Các chiến lược NONE, NATIVE và FRAMEWORK" rồi animate focus sang "Khi cơ chế forwarded headers native của server đã đủ"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Khi "Các chiến lược NONE, NATIVE và FRAMEWORK" đã rõ, hãy kiểm boundary kế tiếp: "Khi cơ chế forwarded headers native của server đã đủ". Giữ kết quả trước để cơ chế mới đi ra trực tiếp từ nó.

**Purpose:**

Chuyển từ "Các chiến lược NONE, NATIVE và FRAMEWORK" sang "Khi cơ chế forwarded headers native của server đã đủ" mà không reset mô hình, đồng thời làm rõ input, owner hoặc output nào thay đổi.

### Scene 4 — Khi cơ chế forwarded headers native của server đã đủ

**Time:** `02:28–03:04`

**Visual:**

Đặt property sources ở trái, ServerProperties ở giữa và server factory ở phải; tô riêng portable server.* và một namespace server-specific để thấy hai tầng cấu hình. Với "Khi cơ chế forwarded headers native của server đã đủ", đặt `NATIVE`, `X-Forwarded-For`, `X-Forwarded-Proto` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Spring Boot 3.3 hướng dẫn rằng `NATIVE` thường đủ khi proxy cung cấp`X-Forwarded-For` và`X-Forwarded-Proto` theo cách thông dụng và hỗ trợ native của server phù hợp môi trường triển khai. Hành vi native phụ thuộc server. Tên header, quy tắc proxy tin cậy, việc viết lại địa chỉ remote và các chi tiết khác có thể khác nhau, vì vậy cần xem tài liệu của server khi chiến lược Boot chung đã đúng nhưng kết quả native vẫn cần tinh chỉnh.

**Purpose:**

Biến "Khi cơ chế forwarded headers native của server đã đủ" thành lựa chọn cụ thể bằng cách cho thấy runtime thay đổi gì và tầng cấu hình hoặc customization nào nên dùng tiếp.

## Khi nào cần cơ chế forwarded headers của Spring Framework?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:04–03:20`

**Visual:**

Giữ đường client → trusted proxy → request metadata của Boot trên màn hình. Làm mờ annotation của "Khi cơ chế forwarded headers native của server đã đủ" rồi animate focus sang "Khi nào cần cơ chế forwarded headers của Spring Framework?"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ta đã chốt "Khi cơ chế forwarded headers native của server đã đủ". Giữ kết quả đó trên màn hình; bây giờ chuyển sang "Khi nào cần cơ chế forwarded headers của Spring Framework?" và chỉ thay quyết định runtime kế tiếp.

**Purpose:**

Nối "Khi cơ chế forwarded headers native của server đã đủ" với "Khi nào cần cơ chế forwarded headers của Spring Framework?" để quyết định web-runtime kế tiếp đi ra từ kết quả đã chứng minh.

### Scene 5 — Khi nào cần cơ chế forwarded headers của Spring Framework?

**Time:** `03:20–03:54`

**Visual:**

Vẽ client → reverse proxy → Boot app; so host/scheme/remote address trước và sau proxy, rồi đặt NONE/NATIVE/FRAMEWORK tại node thực sự xử lý header. Với "Khi nào cần cơ chế forwarded headers của Spring Framework?", đặt `FRAMEWORK`, `ForwardedHeaderFilter`, `ForwardedHeaderTransformer` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Khi cơ chế xử lý native của server chưa đủ, `FRAMEWORK` kích hoạt khả năng xử lý forwarded header của Spring Framework:`ForwardedHeaderFilter` cho ứng dụng Servlet và`ForwardedHeaderTransformer` cho ứng dụng Reactive. Boot chịu trách nhiệm cho quyết định chọn chiến lược này. Hành vi lọc/biến đổi chi tiết thuộc Spring Framework. Việc đổi chiến lược có thể làm metadata request mà ứng dụng nhìn thấy thay đổi dù kết nối socket thực tế tới embedded server không đổi.

**Purpose:**

Cho thấy hệ quả runtime cụ thể của "Khi nào cần cơ chế forwarded headers của Spring Framework?" và tạo checkpoint quan sát được cho quyết định Boot sở hữu.

### Scene 6 — Runtime evidence

**Time:** `03:54–04:23`

**Visual:**

Khởi động với --server.forward-headers-strategy=framework. Gọi GET /spring-boot/web-runtime/request-metadata với X-Forwarded-Proto=https, X-Forwarded-Host=example.test, X-Forwarded-Port=443 và X-Forwarded-For=203.0.113.10; đặt request và response cạnh nhau. Với "Khi nào cần cơ chế forwarded headers của Spring Framework?", đặt input của section và runtime result quan sát được tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Bây giờ dùng thí nghiệm Step 5 để thấy tác động thật. Với strategy FRAMEWORK, endpoint /spring-boot/web-runtime/request-metadata đọc request sau khi Spring Framework xử lý forwarded headers. Scheme, secure flag, host, port, remote address và URL mà application nhìn thấy phản ánh metadata được forward, dù socket local vẫn kết thúc ở embedded server. Đây cũng là lý do chỉ bật forwarding khi đường proxy được tin cậy.

**Purpose:**

Dùng endpoint runtime của Step 5 để kiểm chứng "Khi nào cần cơ chế forwarded headers của Spring Framework?" trên context/server đang chạy thay vì xem sơ đồ là bằng chứng.

## Mặc định của Boot trên các nền tảng đám mây được hỗ trợ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:23–04:41`

**Visual:**

Giữ đường client → trusted proxy → request metadata của Boot trên màn hình. Làm mờ annotation của "Khi nào cần cơ chế forwarded headers của Spring Framework?" rồi animate focus sang "Mặc định của Boot trên các nền tảng đám mây được hỗ trợ"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Section trước đã cho evidence cho "Khi nào cần cơ chế forwarded headers của Spring Framework?". Đi tiếp trên cùng runtime path tới "Mặc định của Boot trên các nền tảng đám mây được hỗ trợ" và quan sát input, owner hoặc output nào thay đổi.

**Purpose:**

Giữ evidence từ "Khi nào cần cơ chế forwarded headers của Spring Framework?" và chỉ đưa thêm cơ chế mới cần cho "Mặc định của Boot trên các nền tảng đám mây được hỗ trợ".

### Scene 7 — Mặc định của Boot trên các nền tảng đám mây được hỗ trợ

**Time:** `04:41–05:15`

**Visual:**

Đặt property sources ở trái, ServerProperties ở giữa và server factory ở phải; tô riêng portable server.* và một namespace server-specific để thấy hai tầng cấu hình. Với "Mặc định của Boot trên các nền tảng đám mây được hỗ trợ", đặt `server.forward-headers-strategy`, `NATIVE`, `NONE` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Trong Boot 3.3, `server.forward-headers-strategy` mặc định là`NATIVE` khi ứng dụng chạy trên nền tảng đám mây được hỗ trợ. Ở các môi trường còn lại, mặc định là`NONE`. Không nên sao chép giả định của môi trường đám mây sang môi trường triển khai khác. Cấu hình an toàn phụ thuộc việc toàn bộ lưu lượng trực tiếp có thực sự đi qua proxy tin cậy, và proxy đó có làm sạch/cung cấp đúng forwarded header hay không.

**Purpose:**

Giải thích "Mặc định của Boot trên các nền tảng đám mây được hỗ trợ" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## TLS termination, redirect và nhận biết scheme bên ngoài

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:15–05:32`

**Visual:**

Giữ đường client → trusted proxy → request metadata của Boot trên màn hình. Làm mờ annotation của "Mặc định của Boot trên các nền tảng đám mây được hỗ trợ" rồi animate focus sang "TLS termination, redirect và nhận biết scheme bên ngoài"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Mặc định của Boot trên các nền tảng đám mây được hỗ trợ" đã rõ. Dependency kế tiếp là "TLS termination, redirect và nhận biết scheme bên ngoài"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "TLS termination, redirect và nhận biết scheme bên ngoài" là dependency kế tiếp sau "Mặc định của Boot trên các nền tảng đám mây được hỗ trợ", đồng thời giữ ownership và runtime state liên tục.

### Scene 8 — TLS termination, redirect và nhận biết scheme bên ngoài

**Time:** `05:32–06:14`

**Visual:**

Dùng TLS handshake diagram với certificate material ở trái, server.ssl hoặc named SslBundle ở giữa và HTTPS connector ở phải; với SNI cho hostname chọn certificate. Với "TLS termination, redirect và nhận biết scheme bên ngoài", đặt `https`, `server.tomcat.redirect-context-root=false`, `X-Forwarded-Proto` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

TLS thường kết thúc tại proxy, để lại một chặng HTTP từ proxy tới ứng dụng. Kết nối cục bộ của server khi đó có vẻ không được mã hóa dù request phía máy khách là HTTPS. Cơ chế xử lý giao thức chuyển tiếp đúng giúp metadata của request mà ứng dụng nhìn thấy vẫn giữ scheme `https` bên ngoài. Điều này ảnh hưởng redirect và URL tuyệt đối được sinh ra. Với Tomcat, tài liệu Boot nêu rõ`server.tomcat.redirect-context-root=false` khi SSL kết thúc ở proxy để`X-Forwarded-Proto` được xem xét trước khi context-root redirect được tạo.

**Purpose:**

Nối input trong "TLS termination, redirect và nhận biết scheme bên ngoài" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.

## Thiết lập proxy và remote IP riêng theo server

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:14–06:29`

**Visual:**

Giữ đường client → trusted proxy → request metadata của Boot trên màn hình. Làm mờ annotation của "TLS termination, redirect và nhận biết scheme bên ngoài" rồi animate focus sang "Thiết lập proxy và remote IP riêng theo server"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Khi "TLS termination, redirect và nhận biết scheme bên ngoài" đã rõ, hãy kiểm boundary kế tiếp: "Thiết lập proxy và remote IP riêng theo server". Giữ kết quả trước để cơ chế mới đi ra trực tiếp từ nó.

**Purpose:**

Chuyển từ "TLS termination, redirect và nhận biết scheme bên ngoài" sang "Thiết lập proxy và remote IP riêng theo server" mà không reset mô hình, đồng thời làm rõ input, owner hoặc output nào thay đổi.

### Scene 9 — Thiết lập proxy và remote IP riêng theo server

**Time:** `06:29–07:00`

**Visual:**

Vẽ client → reverse proxy → Boot app; so host/scheme/remote address trước và sau proxy, rồi đặt NONE/NATIVE/FRAMEWORK tại node thực sự xử lý header. Với "Thiết lập proxy và remote IP riêng theo server", đặt `server.tomcat.remoteip.*`, `server.*` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Khi chiến lược chung chưa đủ, namespace riêng theo server cung cấp quyền kiểm soát sâu hơn. Ví dụ Tomcat cho phép đổi tên forwarded header và mẫu proxy nội bộ tin cậy dưới `server.tomcat.remoteip.*`. Chỉ nên dùng các thiết lập này khi hợp đồng triển khai cần chúng. Chúng làm cấu hình phụ thuộc vào một server cụ thể và không phải hành vi di động giữa các server của `server.*`.

**Purpose:**

Biến "Thiết lập proxy và remote IP riêng theo server" thành lựa chọn cụ thể bằng cách cho thấy runtime thay đổi gì và tầng cấu hình hoặc customization nào nên dùng tiếp.

## Forwarded headers và ranh giới tin cậy với proxy

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:00–07:15`

**Visual:**

Giữ đường client → trusted proxy → request metadata của Boot trên màn hình. Làm mờ annotation của "Thiết lập proxy và remote IP riêng theo server" rồi animate focus sang "Forwarded headers và ranh giới tin cậy với proxy"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ta đã chốt "Thiết lập proxy và remote IP riêng theo server". Giữ kết quả đó trên màn hình; bây giờ chuyển sang "Forwarded headers và ranh giới tin cậy với proxy" và chỉ thay quyết định runtime kế tiếp.

**Purpose:**

Nối "Thiết lập proxy và remote IP riêng theo server" với "Forwarded headers và ranh giới tin cậy với proxy" để quyết định web-runtime kế tiếp đi ra từ kết quả đã chứng minh.

### Scene 10 — Forwarded headers và ranh giới tin cậy với proxy

**Time:** `07:15–08:03`

**Visual:**

Vẽ client → reverse proxy → Boot app; so host/scheme/remote address trước và sau proxy, rồi đặt NONE/NATIVE/FRAMEWORK tại node thực sự xử lý header. Với "Forwarded headers và ranh giới tin cậy với proxy", đặt input của section và runtime result quan sát được tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Forwarded headers chỉ đáng tin khi proxy tin cậy kiểm soát chúng và máy khách không đáng tin không thể đi vòng qua proxy để gửi trực tiếp vào ứng dụng. Nếu không, máy khách có thể tự gửi metadata chuyển tiếp và làm ứng dụng tin sai scheme, host hoặc địa chỉ remote. Vì vậy tài liệu Boot khuyến nghị chỉ bật hỗ trợ forwarded header khi lưu lượng đến từ HTTP proxy hoặc mạng đáng tin cậy. Mô hình tin cậy proxy đầy đủ thuộc hạ tầng/bảo mật, nhưng cấu hình Boot phải giữ được ranh giới này thay vì xem mọi forwarding header đầu vào là nguồn có thẩm quyền.

**Purpose:**

Cho thấy hệ quả runtime cụ thể của "Forwarded headers và ranh giới tin cậy với proxy" và tạo checkpoint quan sát được cho quyết định Boot sở hữu.
