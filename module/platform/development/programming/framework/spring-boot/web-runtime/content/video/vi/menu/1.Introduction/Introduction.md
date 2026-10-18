---
video:
  url: ""
---

# Spring Boot web runtime

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

## Spring Boot sở hữu phần nào của web runtime?

<!-- VIDEO_SECTION -->

### Scene 1 — Spring Boot sở hữu phần nào của web runtime?

**Time:** `00:00–00:44`

**Visual:**

Giữ runtime diagram của chương nhưng chỉ hiện input, quyết định Boot sở hữu và output server/context của section; làm mờ mọi node khác. Với "Spring Boot sở hữu phần nào của web runtime?", đặt `ApplicationContext` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Web runtime của Spring Boot là tầng tích hợp biến một ứng dụng Boot thông thường thành một tiến trình có thể sở hữu và khởi động HTTP server. Boot xác định ứng dụng có phải ứng dụng web hay không, tạo `ApplicationContext` phù hợp với mô hình web, tự động cấu hình embedded server factory, áp dụng cấu hình và customizer, rồi quản lý server đó trong suốt quá trình khởi động và shutdown. Phạm vi này hẹp hơn khái niệm “mọi thứ liên quan đến web”. Boot nối bootstrap, cấu hình, auto-configuration và một cách triển khai server được hỗ trợ.

**Purpose:**

Giải thích "Spring Boot sở hữu phần nào của web runtime?" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## Vì sao Boot cần một tầng web runtime?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:44–00:59`

**Visual:**

Giữ sơ đồ lifecycle/ownership của Boot trên màn hình. Làm mờ annotation của "Spring Boot sở hữu phần nào của web runtime?" rồi animate focus sang "Vì sao Boot cần một tầng web runtime?"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Spring Boot sở hữu phần nào của web runtime?" đã rõ. Dependency kế tiếp là "Vì sao Boot cần một tầng web runtime?"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Vì sao Boot cần một tầng web runtime?" là dependency kế tiếp sau "Spring Boot sở hữu phần nào của web runtime?", đồng thời giữ ownership và runtime state liên tục.

### Scene 2 — Vì sao Boot cần một tầng web runtime?

**Time:** `00:59–01:55`

**Visual:**

Giữ runtime diagram của chương nhưng chỉ hiện input, quyết định Boot sở hữu và output server/context của section; làm mờ mọi node khác. Với "Vì sao Boot cần một tầng web runtime?", đặt input của section và runtime result quan sát được tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Nếu không có tầng web runtime của Boot, ứng dụng phải tự ghép nhiều quyết định hạ tầng: dùng cách triển khai server nào, tạo server ra sao, cấu hình đi vào server bằng cách nào, server khởi động ở thời điểm nào so với Spring context và dừng cùng ứng dụng như thế nào. Boot biến phần lớn các quyết định đó thành quy ước có thể thay đổi bằng dependency và cấu hình thay vì phải viết mã bootstrap riêng. Điểm quan trọng là web framework và web server là hai trách nhiệm khác nhau. Spring MVC hoặc WebFlux mô tả cách request được framework xử lý; Boot làm cho ứng dụng có thể chạy độc lập bằng cách gắn framework đó với vòng đời của embedded server.

**Purpose:**

Nối input trong "Vì sao Boot cần một tầng web runtime?" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.

## Từ SpringApplication tới embedded server đang chạy

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:55–02:09`

**Visual:**

Giữ sơ đồ lifecycle/ownership của Boot trên màn hình. Làm mờ annotation của "Vì sao Boot cần một tầng web runtime?" rồi animate focus sang "Từ SpringApplication tới embedded server đang chạy"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Khi "Vì sao Boot cần một tầng web runtime?" đã rõ, hãy kiểm boundary kế tiếp: "Từ SpringApplication tới embedded server đang chạy". Giữ kết quả trước để cơ chế mới đi ra trực tiếp từ nó.

**Purpose:**

Chuyển từ "Vì sao Boot cần một tầng web runtime?" sang "Từ SpringApplication tới embedded server đang chạy" mà không reset mô hình, đồng thời làm rõ input, owner hoặc output nào thay đổi.

### Scene 3 — Từ SpringApplication tới embedded server đang chạy

**Time:** `02:09–02:45`

**Visual:**

Dùng lifecycle timeline: SpringApplication → web context refresh → factory lookup/customization → server create/start; với shutdown thì đảo timeline và tô close phase. Với "Từ SpringApplication tới embedded server đang chạy", đặt `main` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Bắt đầu từ `SpringApplication.run`. Boot trước hết suy ra, hoặc dùng giá trị đã cấu hình, của `WebApplicationType`. Lựa chọn đó quyết định Boot tạo Servlet hay Reactive web application context. Trong lúc refresh context, web-server auto-configuration cung cấp `WebServerFactory`; `ServerProperties` cùng các `WebServerFactoryCustomizer` điều chỉnh factory; rồi context yêu cầu factory tạo và khởi động `WebServer`. Điểm cần nhớ là ownership: embedded server nằm trong lifecycle do Boot quản lý, không phải một tiến trình bootstrap server tách rời bên cạnh Spring.

**Purpose:**

Biến "Từ SpringApplication tới embedded server đang chạy" thành lựa chọn cụ thể bằng cách cho thấy runtime thay đổi gì và tầng cấu hình hoặc customization nào nên dùng tiếp.

## Servlet và Reactive như hai mô hình runtime

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:45–02:59`

**Visual:**

Giữ sơ đồ lifecycle/ownership của Boot trên màn hình. Làm mờ annotation của "Từ SpringApplication tới embedded server đang chạy" rồi animate focus sang "Servlet và Reactive như hai mô hình runtime"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ta đã chốt "Từ SpringApplication tới embedded server đang chạy". Giữ kết quả đó trên màn hình; bây giờ chuyển sang "Servlet và Reactive như hai mô hình runtime" và chỉ thay quyết định runtime kế tiếp.

**Purpose:**

Nối "Từ SpringApplication tới embedded server đang chạy" với "Servlet và Reactive như hai mô hình runtime" để quyết định web-runtime kế tiếp đi ra từ kết quả đã chứng minh.

### Scene 4 — Servlet và Reactive như hai mô hình runtime

**Time:** `02:59–03:43`

**Visual:**

Giữ runtime diagram của chương nhưng chỉ hiện input, quyết định Boot sở hữu và output server/context của section; làm mờ mọi node khác. Với "Servlet và Reactive như hai mô hình runtime", đặt `ServletWebServerApplicationContext`, `ReactiveWebServerApplicationContext` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Boot hỗ trợ hai mô hình web runtime chính. Ứng dụng kiểu Servlet dùng server có khả năng Servlet và `ServletWebServerApplicationContext`. Ứng dụng kiểu Reactive dùng reactive web server và`ReactiveWebServerApplicationContext`. Lựa chọn này ảnh hưởng tới server factory, auto-configuration và cách Boot tích hợp server. Nó không có nghĩa module này sở hữu ngữ nghĩa của Servlet API hay cơ chế xử lý request theo reactive. Web runtime trả lời câu hỏi “Boot phải khởi động loại ứng dụng web nào?”; Spring MVC và Spring WebFlux trả lời “framework xử lý request như thế nào sau khi runtime đã sẵn sàng?”.

**Purpose:**

Cho thấy hệ quả runtime cụ thể của "Servlet và Reactive như hai mô hình runtime" và tạo checkpoint quan sát được cho quyết định Boot sở hữu.

## Ranh giới của Boot web runtime nằm ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:43–03:58`

**Visual:**

Giữ sơ đồ lifecycle/ownership của Boot trên màn hình. Làm mờ annotation của "Servlet và Reactive như hai mô hình runtime" rồi animate focus sang "Ranh giới của Boot web runtime nằm ở đâu?"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Section trước đã cho evidence cho "Servlet và Reactive như hai mô hình runtime". Đi tiếp trên cùng runtime path tới "Ranh giới của Boot web runtime nằm ở đâu?" và quan sát input, owner hoặc output nào thay đổi.

**Purpose:**

Giữ evidence từ "Servlet và Reactive như hai mô hình runtime" và chỉ đưa thêm cơ chế mới cần cho "Ranh giới của Boot web runtime nằm ở đâu?".

### Scene 5 — Ranh giới của Boot web runtime nằm ở đâu?

**Time:** `03:58–04:40`

**Visual:**

Vẽ ownership swimlane Boot | Spring MVC/WebFlux | embedded server | network/TLS/proxy; đặt behavior vào đúng lane rồi hiện mũi tên handoff. Với "Ranh giới của Boot web runtime nằm ở đâu?", đặt `server.*` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Module này sở hữu các quyết định đặc trưng của Boot quanh loại ứng dụng web, lựa chọn embedded server, server auto-configuration, các property `server.*`, tùy biến bằng mã, khả năng HTTP ở tầng server, cách web server dùng TLS/SSL bundle, forwarded headers và graceful shutdown. Khi câu hỏi chuyển sang handler mapping, controller, chuỗi filter của framework, codec, reactive operator, cơ chế luồng nội bộ của Servlet container, lý thuyết HTTP, lý thuyết chuỗi chứng chỉ hoặc cơ chế hoạt động chi tiết của reverse proxy thì phải bàn giao sang phần học sở hữu nội dung đó.

**Purpose:**

Giải thích "Ranh giới của Boot web runtime nằm ở đâu?" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## Các chương web runtime kết nối với nhau như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:40–04:56`

**Visual:**

Giữ sơ đồ lifecycle/ownership của Boot trên màn hình. Làm mờ annotation của "Ranh giới của Boot web runtime nằm ở đâu?" rồi animate focus sang "Các chương web runtime kết nối với nhau như thế nào?"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Ranh giới của Boot web runtime nằm ở đâu?" đã rõ. Dependency kế tiếp là "Các chương web runtime kết nối với nhau như thế nào?"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Các chương web runtime kết nối với nhau như thế nào?" là dependency kế tiếp sau "Ranh giới của Boot web runtime nằm ở đâu?", đồng thời giữ ownership và runtime state liên tục.

### Scene 6 — Các chương web runtime kết nối với nhau như thế nào?

**Time:** `04:56–05:44`

**Visual:**

Giữ runtime diagram của chương nhưng chỉ hiện input, quyết định Boot sở hữu và output server/context của section; làm mờ mọi node khác. Với "Các chương web runtime kết nối với nhau như thế nào?", đặt input của section và runtime result quan sát được tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Thứ tự các chương đi theo chuỗi quyết định thường gặp trong thực tế. Trước hết hiểu loại ứng dụng và server được chọn từ classpath. Sau đó tìm hiểu Boot tạo server factory tương ứng như thế nào, property/customizer thay đổi factory ra sao và những khả năng HTTP nào được Boot cung cấp theo mô hình dùng chung. Khi mô hình server cục bộ đã rõ, mới thêm các yếu tố triển khai: TLS và SSL bundle, forwarded headers sau proxy và graceful shutdown. Chương cuối gom các quyết định này thành một mô hình đầu-cuối và chỉ rõ điểm nào cần tiếp tục sang Spring Framework hoặc hạ tầng.

**Purpose:**

Nối input trong "Các chương web runtime kết nối với nhau như thế nào?" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.
