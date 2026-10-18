---
video:
  url: ""
---

# Nhận diện loại ứng dụng web

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

## NONE, SERVLET và REACTIVE

<!-- VIDEO_SECTION -->

### Scene 1 — NONE, SERVLET và REACTIVE

**Time:** `00:00–00:30`

**Visual:**

Dùng bảng NONE / SERVLET / REACTIVE; đưa classpath signal qua decision flow rồi nối type được chọn tới ApplicationContext và trạng thái server startup. Với "NONE, SERVLET và REACTIVE", đặt `WebApplicationType`, `NONE`, `SERVLET` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

`WebApplicationType` có ba giá trị:`NONE`,`SERVLET` và`REACTIVE`.`NONE` nghĩa là Boot tạo context ứng dụng không chạy web và không khởi động embedded web server.`SERVLET` chọn Servlet web runtime.`REACTIVE` chọn Reactive web runtime. Hãy xem đây là một quyết định bootstrap rất sớm. Nó ảnh hưởng tới loại`ApplicationContext` mà Spring Boot tạo và các web-server auto-configuration nào đủ điều kiện tham gia ở các bước sau.

**Purpose:**

Giải thích "NONE, SERVLET và REACTIVE" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## Boot suy ra WebApplicationType từ classpath như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:30–00:43`

**Visual:**

Giữ bảng quyết định NONE/SERVLET/REACTIVE trên màn hình. Làm mờ annotation của "NONE, SERVLET và REACTIVE" rồi animate focus sang "Boot suy ra WebApplicationType từ classpath như thế nào?"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "NONE, SERVLET và REACTIVE" đã rõ. Dependency kế tiếp là "Boot suy ra WebApplicationType từ classpath như thế nào?"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Boot suy ra WebApplicationType từ classpath như thế nào?" là dependency kế tiếp sau "NONE, SERVLET và REACTIVE", đồng thời giữ ownership và runtime state liên tục.

### Scene 2 — Boot suy ra WebApplicationType từ classpath như thế nào?

**Time:** `00:43–01:34`

**Visual:**

Dùng bảng NONE / SERVLET / REACTIVE; đưa classpath signal qua decision flow rồi nối type được chọn tới ApplicationContext và trạng thái server startup. Với "Boot suy ra WebApplicationType từ classpath như thế nào?", đặt `SpringApplication`, `REACTIVE`, `SERVLET` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Nếu không cấu hình rõ loại ứng dụng, `SpringApplication` suy ra giá trị từ classpath. Ở mức mô hình tư duy, classpath chỉ có reactive web stack dẫn tới`REACTIVE`, classpath có Servlet web stack dẫn tới`SERVLET`, còn khi thiếu các dấu hiệu cần thiết của môi trường web thì kết quả là`NONE`. Vì vậy chỉ cần thêm hoặc bỏ starter cũng có thể làm hành vi runtime thay đổi dù`main` không đổi. Đồ thị dependency là một đầu vào cho quyết định của Boot; loại ứng dụng sau đó được các conditional auto-configuration và quá trình tạo context sử dụng. Chi tiết class nào được Boot dùng làm tín hiệu là chi tiết triển khai.

**Purpose:**

Nối input trong "Boot suy ra WebApplicationType từ classpath như thế nào?" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.

## Khi tín hiệu Servlet và Reactive cùng xuất hiện

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:34–01:49`

**Visual:**

Giữ bảng quyết định NONE/SERVLET/REACTIVE trên màn hình. Làm mờ annotation của "Boot suy ra WebApplicationType từ classpath như thế nào?" rồi animate focus sang "Khi tín hiệu Servlet và Reactive cùng xuất hiện"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Khi "Boot suy ra WebApplicationType từ classpath như thế nào?" đã rõ, hãy kiểm boundary kế tiếp: "Khi tín hiệu Servlet và Reactive cùng xuất hiện". Giữ kết quả trước để cơ chế mới đi ra trực tiếp từ nó.

**Purpose:**

Chuyển từ "Boot suy ra WebApplicationType từ classpath như thế nào?" sang "Khi tín hiệu Servlet và Reactive cùng xuất hiện" mà không reset mô hình, đồng thời làm rõ input, owner hoặc output nào thay đổi.

### Scene 3 — Khi tín hiệu Servlet và Reactive cùng xuất hiện

**Time:** `01:49–02:27`

**Visual:**

Dùng bảng NONE / SERVLET / REACTIVE; đưa classpath signal qua decision flow rồi nối type được chọn tới ApplicationContext và trạng thái server startup. Với "Khi tín hiệu Servlet và Reactive cùng xuất hiện", đặt `WebClient`, `REACTIVE` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Khi Spring MVC và Spring WebFlux cùng có trên classpath, Spring Boot mặc định chọn mô hình Servlet/MVC. Quyết định này có chủ ý vì nhiều ứng dụng MVC thêm WebFlux chỉ để dùng `WebClient` chứ không muốn chuyển toàn bộ runtime sang WebFlux. Do đó, thấy thư viện reactive trong dependency tree chưa đủ để kết luận ứng dụng đang chạy kiểu`REACTIVE`. Nếu cả hai web stack cùng tồn tại nhưng ứng dụng thực sự cần chạy WebFlux, hãy cấu hình lựa chọn đó rõ ràng.

**Purpose:**

Biến "Khi tín hiệu Servlet và Reactive cùng xuất hiện" thành lựa chọn cụ thể bằng cách cho thấy runtime thay đổi gì và tầng cấu hình hoặc customization nào nên dùng tiếp.

## Ghi đè bằng spring.main.web-application-type

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:27–02:41`

**Visual:**

Giữ bảng quyết định NONE/SERVLET/REACTIVE trên màn hình. Làm mờ annotation của "Khi tín hiệu Servlet và Reactive cùng xuất hiện" rồi animate focus sang "Ghi đè bằng spring.main.web-application-type"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ta đã chốt "Khi tín hiệu Servlet và Reactive cùng xuất hiện". Giữ kết quả đó trên màn hình; bây giờ chuyển sang "Ghi đè bằng spring.main.web-application-type" và chỉ thay quyết định runtime kế tiếp.

**Purpose:**

Nối "Khi tín hiệu Servlet và Reactive cùng xuất hiện" với "Ghi đè bằng spring.main.web-application-type" để quyết định web-runtime kế tiếp đi ra từ kết quả đã chứng minh.

### Scene 4 — Ghi đè bằng spring.main.web-application-type

**Time:** `02:41–03:12`

**Visual:**

Giữ runtime diagram của chương nhưng chỉ hiện input, quyết định Boot sở hữu và output server/context của section; làm mờ mọi node khác. Với "Ghi đè bằng spring.main.web-application-type", đặt `spring.main.web-application-type`, `servlet`, `reactive` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Property `spring.main.web-application-type` cho phép ép quyết định bootstrap. Các giá trị thường dùng là`servlet`,`reactive` và`none`. spring.main.web-application-type=reactive Hãy dùng ghi đè khi classpath cố ý chứa cả hai stack hoặc khi ứng dụng có dependency web nhưng một chế độ thực thi cụ thể không nên mở server.`none` hữu ích cho chế độ dòng lệnh/batch muốn tái sử dụng dependency của ứng dụng nhưng không cung cấp HTTP endpoint.

**Purpose:**

Cho thấy hệ quả runtime cụ thể của "Ghi đè bằng spring.main.web-application-type" và tạo checkpoint quan sát được cho quyết định Boot sở hữu.

## Loại ứng dụng thay đổi context và việc khởi động server ra sao?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:12–03:27`

**Visual:**

Giữ bảng quyết định NONE/SERVLET/REACTIVE trên màn hình. Làm mờ annotation của "Ghi đè bằng spring.main.web-application-type" rồi animate focus sang "Loại ứng dụng thay đổi context và việc khởi động server ra sao?"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Section trước đã cho evidence cho "Ghi đè bằng spring.main.web-application-type". Đi tiếp trên cùng runtime path tới "Loại ứng dụng thay đổi context và việc khởi động server ra sao?" và quan sát input, owner hoặc output nào thay đổi.

**Purpose:**

Giữ evidence từ "Ghi đè bằng spring.main.web-application-type" và chỉ đưa thêm cơ chế mới cần cho "Loại ứng dụng thay đổi context và việc khởi động server ra sao?".

### Scene 5 — Loại ứng dụng thay đổi context và việc khởi động server ra sao?

**Time:** `03:27–04:11`

**Visual:**

Dùng bảng NONE / SERVLET / REACTIVE; đưa classpath signal qua decision flow rồi nối type được chọn tới ApplicationContext và trạng thái server startup. Với "Loại ứng dụng thay đổi context và việc khởi động server ra sao?", đặt `SERVLET`, `ServletWebServerFactory`, `REACTIVE` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Loại ứng dụng được chọn sẽ thay đổi cách context được tạo trước khi server xuất hiện. `SERVLET` dẫn Boot tới context ứng dụng web Servlet và`ServletWebServerFactory`;`REACTIVE` dẫn tới context ứng dụng web reactive và`ReactiveWebServerFactory`;`NONE` dùng context không phải web và không đi theo quá trình khởi động embedded web server. Điều này cũng cho thấy thay server dependency khác với thay`WebApplicationType`. Đổi Tomcat sang Jetty chỉ đổi cách triển khai bên trong cùng mô hình Servlet runtime. Đổi từ`SERVLET` sang`REACTIVE` thay đổi cả mô hình web runtime và nhánh auto-configuration đủ điều kiện chạy.

**Purpose:**

Giải thích "Loại ứng dụng thay đổi context và việc khởi động server ra sao?" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.
