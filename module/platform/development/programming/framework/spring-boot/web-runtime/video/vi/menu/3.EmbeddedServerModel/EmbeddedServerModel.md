---
video:
  url: ""
---

# Mô hình embedded server

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

## Web starter và embedded server mặc định

<!-- VIDEO_SECTION -->

### Scene 1 — Web starter và embedded server mặc định

**Time:** `00:00–00:32`

**Visual:**

Giữ runtime diagram của chương nhưng chỉ hiện input, quyết định Boot sở hữu và output server/context của section; làm mờ mọi node khác. Với "Web starter và embedded server mặc định", đặt `spring-boot-starter-web`, `spring-boot-starter-tomcat`, `spring-boot-starter-webflux` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

`spring-boot-starter-web` kéo Tomcat vào qua`spring-boot-starter-tomcat`, vì vậy Tomcat là embedded server mặc định theo quy ước cho ứng dụng Servlet.`spring-boot-starter-webflux` kéo Reactor Netty vào qua`spring-boot-starter-reactor-netty`, nên Reactor Netty là reactive server mặc định. Đây là mặc định từ starter, không phải yêu cầu được mã hóa cứng. Boot web runtime được thiết kế để cách triển khai server có thể thay thế mà ứng dụng xung quanh vẫn giữ mô hình Boot.

**Purpose:**

Giải thích "Web starter và embedded server mặc định" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## Các lựa chọn server được hỗ trợ cho Servlet và Reactive

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:32–00:47`

**Visual:**

Giữ pipeline starter → factory → server trên màn hình. Làm mờ annotation của "Web starter và embedded server mặc định" rồi animate focus sang "Các lựa chọn server được hỗ trợ cho Servlet và Reactive"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Web starter và embedded server mặc định" đã rõ. Dependency kế tiếp là "Các lựa chọn server được hỗ trợ cho Servlet và Reactive"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Các lựa chọn server được hỗ trợ cho Servlet và Reactive" là dependency kế tiếp sau "Web starter và embedded server mặc định", đồng thời giữ ownership và runtime state liên tục.

### Scene 2 — Các lựa chọn server được hỗ trợ cho Servlet và Reactive

**Time:** `00:47–01:20`

**Visual:**

Vẽ dependency/factory pipeline: starter hoặc user bean → ordered customizers → WebServerFactory → embedded server; làm mờ auto-configured factory ngay khi back-off xảy ra. Với "Các lựa chọn server được hỗ trợ cho Servlet và Reactive", đặt `TomcatServletWebServerFactory`, `TomcatReactiveWebServerFactory` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Với stack Servlet, Boot 3.3 hỗ trợ embedded Tomcat, Jetty và Undertow. Với stack reactive, Boot hỗ trợ Reactor Netty cùng các adapter reactive cho Tomcat, Jetty và Undertow. Vì vậy cùng một tên server có thể xuất hiện trong hai mô hình runtime khác nhau. Ví dụ `TomcatServletWebServerFactory` và`TomcatReactiveWebServerFactory` là hai tích hợp khác nhau của Boot. Hãy chọn web stack trước, rồi mới chọn cách triển khai server cụ thể trong stack đó.

**Purpose:**

Nối input trong "Các lựa chọn server được hỗ trợ cho Servlet và Reactive" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.

## Thay embedded server dependency mặc định

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:20–01:35`

**Visual:**

Giữ pipeline starter → factory → server trên màn hình. Làm mờ annotation của "Các lựa chọn server được hỗ trợ cho Servlet và Reactive" rồi animate focus sang "Thay embedded server dependency mặc định"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Khi "Các lựa chọn server được hỗ trợ cho Servlet và Reactive" đã rõ, hãy kiểm boundary kế tiếp: "Thay embedded server dependency mặc định". Giữ kết quả trước để cơ chế mới đi ra trực tiếp từ nó.

**Purpose:**

Chuyển từ "Các lựa chọn server được hỗ trợ cho Servlet và Reactive" sang "Thay embedded server dependency mặc định" mà không reset mô hình, đồng thời làm rõ input, owner hoặc output nào thay đổi.

### Scene 3 — Thay embedded server dependency mặc định

**Time:** `01:35–02:19`

**Visual:**

Vẽ dependency/factory pipeline: starter hoặc user bean → ordered customizers → WebServerFactory → embedded server; làm mờ auto-configured factory ngay khi back-off xảy ra. Với "Thay embedded server dependency mặc định", đặt `spring-boot-starter-tomcat`, `spring-boot-starter-jetty` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Thay server trước hết là một quyết định về dependency. Loại bỏ hoặc thay server mặc định do starter đưa vào và thêm starter của server mong muốn. Ví dụ ứng dụng Servlet có thể thay `spring-boot-starter-tomcat` bằng`spring-boot-starter-jetty`; ứng dụng WebFlux có thể thay Reactor Netty bằng Undertow. Cách này giữ nguyên mô hình auto-configuration của Boot. Classpath giờ cung cấp một cách triển khai server được hỗ trợ khác nên cấu hình factory tương ứng trở thành lựa chọn phù hợp. Không cần viết mã bootstrap server chỉ để thực hiện một lần đổi dependency mà Boot đã hỗ trợ.

**Purpose:**

Biến "Thay embedded server dependency mặc định" thành lựa chọn cụ thể bằng cách cho thấy runtime thay đổi gì và tầng cấu hình hoặc customization nào nên dùng tiếp.

## Vai trò của WebServerFactory

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:19–02:31`

**Visual:**

Giữ pipeline starter → factory → server trên màn hình. Làm mờ annotation của "Thay embedded server dependency mặc định" rồi animate focus sang "Vai trò của WebServerFactory"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ta đã chốt "Thay embedded server dependency mặc định". Giữ kết quả đó trên màn hình; bây giờ chuyển sang "Vai trò của WebServerFactory" và chỉ thay quyết định runtime kế tiếp.

**Purpose:**

Nối "Thay embedded server dependency mặc định" với "Vai trò của WebServerFactory" để quyết định web-runtime kế tiếp đi ra từ kết quả đã chứng minh.

### Scene 4 — Vai trò của WebServerFactory

**Time:** `02:31–03:04`

**Visual:**

Vẽ dependency/factory pipeline: starter hoặc user bean → ordered customizers → WebServerFactory → embedded server; làm mờ auto-configured factory ngay khi back-off xảy ra. Với "Vai trò của WebServerFactory", đặt `WebServerFactory`, `WebServer`, `ServletWebServerFactory` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

`WebServerFactory` là lớp trừu tượng của Boot dùng để tạo`WebServer` lúc runtime. Ứng dụng Servlet làm việc với`ServletWebServerFactory`; ứng dụng Reactive làm việc với`ReactiveWebServerFactory`. Factory cụ thể như`TomcatServletWebServerFactory` hoặc`NettyReactiveWebServerFactory` nối lớp trừu tượng đó với cách triển khai server tương ứng. Factory là điểm tùy biến trước khi server thực sự tồn tại. Property và`WebServerFactoryCustomizer` thay đổi factory; sau đó context ứng dụng web mới yêu cầu factory tạo server.

**Purpose:**

Cho thấy hệ quả runtime cụ thể của "Vai trò của WebServerFactory" và tạo checkpoint quan sát được cho quyết định Boot sở hữu.

## Context ứng dụng web khởi động server như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:04–03:18`

**Visual:**

Giữ pipeline starter → factory → server trên màn hình. Làm mờ annotation của "Vai trò của WebServerFactory" rồi animate focus sang "Context ứng dụng web khởi động server như thế nào?"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Section trước đã cho evidence cho "Vai trò của WebServerFactory". Đi tiếp trên cùng runtime path tới "Context ứng dụng web khởi động server như thế nào?" và quan sát input, owner hoặc output nào thay đổi.

**Purpose:**

Giữ evidence từ "Vai trò của WebServerFactory" và chỉ đưa thêm cơ chế mới cần cho "Context ứng dụng web khởi động server như thế nào?".

### Scene 5 — Context ứng dụng web khởi động server như thế nào?

**Time:** `03:18–03:54`

**Visual:**

Dùng lifecycle timeline: SpringApplication → web context refresh → factory lookup/customization → server create/start; với shutdown thì đảo timeline và tô close phase. Với "Context ứng dụng web khởi động server như thế nào?", đặt `ApplicationContext`, `WebServer`, `server.port=0` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

`ApplicationContext` nhận biết web của Boot điều phối việc tạo server trong quá trình refresh context. Khi factory và các thành phần cần thiết đã sẵn sàng, context lấy`WebServer` từ factory và khởi động nó như một phần của vòng đời do Boot quản lý. Cổng thực tế có thể chỉ biết sau khi server khởi tạo, đặc biệt với`server.port=0`. Boot phát`WebServerInitializedEvent` sau khi server sẵn sàng, còn`WebServerApplicationContext` cho phép truy cập server nếu cần quan sát runtime.

**Purpose:**

Giải thích "Context ứng dụng web khởi động server như thế nào?" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

### Scene 6 — Runtime evidence

**Time:** `03:54–04:18`

**Visual:**

Chạy ứng dụng, gọi GET /spring-boot/web-runtime/server, rồi đặt response cạnh mã WebRuntimeExperimentService.embeddedServer(). Tô sáng applicationContextType, webServerType và actualPort. Với "Context ứng dụng web khởi động server như thế nào?", đặt input của section và runtime result quan sát được tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Đây là bằng chứng runtime từ Step 5. Endpoint /spring-boot/web-runtime/server đọc chính ServletWebServerApplicationContext đang phục vụ request, lấy WebServer đã khởi động và trả concrete context type, server type cùng actual port. Quan sát này nối sơ đồ bootstrap với một server instance thật mà Boot đang quản lý.

**Purpose:**

Dùng endpoint runtime của Step 5 để kiểm chứng "Context ứng dụng web khởi động server như thế nào?" trên context/server đang chạy thay vì xem sơ đồ là bằng chứng.

## Boot chọn server và ranh giới với phần nội bộ của server

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:18–04:35`

**Visual:**

Giữ pipeline starter → factory → server trên màn hình. Làm mờ annotation của "Context ứng dụng web khởi động server như thế nào?" rồi animate focus sang "Boot chọn server và ranh giới với phần nội bộ của server"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Context ứng dụng web khởi động server như thế nào?" đã rõ. Dependency kế tiếp là "Boot chọn server và ranh giới với phần nội bộ của server"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Boot chọn server và ranh giới với phần nội bộ của server" là dependency kế tiếp sau "Context ứng dụng web khởi động server như thế nào?", đồng thời giữ ownership và runtime state liên tục.

### Scene 7 — Boot chọn server và ranh giới với phần nội bộ của server

**Time:** `04:35–05:27`

**Visual:**

Giữ runtime diagram của chương nhưng chỉ hiện input, quyết định Boot sở hữu và output server/context của section; làm mờ mọi node khác. Với "Boot chọn server và ranh giới với phần nội bộ của server", đặt input của section và runtime result quan sát được tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Boot chịu trách nhiệm cho hợp đồng tích hợp: server factory nào đủ điều kiện, cấu hình nào được áp dụng, customizer tham gia ra sao và server nối vào quá trình khởi động/dừng của ứng dụng như thế nào. Boot không định nghĩa lại cách Tomcat connector, Jetty handler, Undertow worker hay Netty event loop hoạt động bên trong. Khi bắt buộc dùng phần nội bộ riêng theo server, hãy đi qua hook tùy biến hẹp nhất của Boot đáp ứng yêu cầu rồi tiếp tục kiến thức sâu ở module sở hữu server/runtime đó. Cách này giúp ứng dụng giữ khả năng chuyển đổi tốt hơn và tránh phụ thuộc chi tiết triển khai ở những nơi không cần thiết.

**Purpose:**

Nối input trong "Boot chọn server và ranh giới với phần nội bộ của server" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.
