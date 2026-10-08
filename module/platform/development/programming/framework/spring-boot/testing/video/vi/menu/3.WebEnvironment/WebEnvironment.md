---
video:
  url: ""
---

# `WebEnvironment` và ranh giới kiểm thử với server thật

<!--
VIDEO SCRIPT FORMAT
- Each H2 maps 1:1 to the current Knowledge H2 order.
- The first section contains a Scene; later sections contain a conceptual Transition and a Scene.
- Timing is estimated from spoken density and must remain sequential.
-->

## `WebEnvironment` điều khiển điều gì?

<!-- VIDEO_SECTION -->

### Scene 1 — `WebEnvironment` điều khiển điều gì?

**Time:** `00:00–00:46`

**Visual:**

Hiện ma trận bốn cột `WebEnvironment`: `MOCK`, `RANDOM_PORT`, `DEFINED_PORT`, `NONE`; mỗi cột có context type, trạng thái server và hành vi port.

**Script:**

`SpringBootTest.WebEnvironment` điều khiển loại web context mà full Boot test sử dụng và embedded server có được khởi động hay không. Bốn giá trị là `MOCK`, `RANDOM_PORT`, `DEFINED_PORT` và `NONE`. Đây là quyết định test bootstrap, không phải web-framework API. Nó quyết định Boot thiết lập environment nào để MVC hoặc WebFlux hạ tầng được kiểm thử bên trong.

**Purpose:**

Định nghĩa `WebEnvironment` như control quyết định full Boot test chỉ dùng mock web context hay thực sự đi qua ranh giới server.

## `WebEnvironment.MOCK` cung cấp gì khi không khởi động server?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:46–01:01`

**Visual:**

Nối dài lifecycle/timeline hiện tại sang phase kế tiếp thay vì dựng lại từ đầu: zoom vào `MOCK`: có web `ApplicationContext` nhưng không bind port. Tách nhánh `MockMvc` cho MVC và mock `WebTestClient` cho WebFlux; ghi rõ mock `WebTestClient` chỉ thuộc WebFlux trong Boot 3.3.

**Script:**

`WebEnvironment` đặt tên cho boundary; bắt đầu với `MOCK` để thấy web-capable context cung cấp gì khi chưa mở socket.

**Purpose:**

Nối từ bốn WebEnvironment mode sang đường không có server, đồng thời ghép MVC với MockMvc và WebFlux với mock WebTestClient.

### Scene 1 — `WebEnvironment.MOCK` cung cấp gì khi không khởi động server?

**Time:** `01:01–01:51`

**Visual:**

Zoom vào `MOCK`: có web `ApplicationContext` nhưng không bind port. Tách nhánh `MockMvc` cho MVC và mock `WebTestClient` cho WebFlux; ghi rõ mock `WebTestClient` chỉ thuộc WebFlux trong Boot 3.3.

**Script:**

`MOCK` là mặc định. Khi có web stack được hỗ trợ, Boot nạp web `ApplicationContext` với mock web environment nhưng không khởi động embedded server. Nếu classpath không có web environment, Boot chuyển về context non-web thường. Với MVC, dùng `MockMvc` để kiểm thử request theo mô hình mock. Với WebFlux, Boot có thể auto-configure `WebTestClient` trên reactive application dạng mock. Trong Boot 3.3, mock `WebTestClient` thuộc đường WebFlux chứ không phải lựa chọn tương đương `MockMvc` cho MVC.

**Purpose:**

Làm rõ `MOCK`: có web infrastructure nhưng không bind network port, nên request test vẫn chạy hoàn toàn trong process.

## `RANDOM_PORT` và `DEFINED_PORT` khởi động embedded server thật như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:51–02:06`

**Visual:**

Nối dài lifecycle/timeline hiện tại sang phase kế tiếp thay vì dựng lại từ đầu: chuyển sang hai lane server thật: `RANDOM_PORT` bind port trống và expose `@LocalServerPort`; `DEFINED_PORT` bind port đã cấu hình hoặc port mặc định.

**Script:**

Khi no-server case đã rõ, đối chiếu với `RANDOM_PORT` và `DEFINED_PORT`, nơi request thật sự đi qua embedded server.

**Purpose:**

Đối chiếu đường không có server với embedded server thật để hệ quả về client và transaction có một boundary cụ thể.

### Scene 1 — `RANDOM_PORT` và `DEFINED_PORT` khởi động embedded server thật như thế nào?

**Time:** `02:06–03:00`

**Visual:**

Chuyển sang hai lane server thật: `RANDOM_PORT` bind port trống và expose `@LocalServerPort`; `DEFINED_PORT` bind port đã cấu hình hoặc port mặc định.

**Script:**

Cả `RANDOM_PORT` và `DEFINED_PORT` đều nạp `WebServerApplicationContext` và khởi động embedded web server. `RANDOM_PORT` yêu cầu server bind một port còn trống; `DEFINED_PORT` dùng application port đã cấu hình hoặc mặc định thường. `RANDOM_PORT` thường an toàn hơn cho bộ test tự động vì các lần chạy song song không tranh cùng một port cố định. Nếu client tùy chỉnh cần biết port thực tế, có thể inject bằng `@LocalServerPort`.

**Purpose:**

Cho thấy `RANDOM_PORT` và `DEFINED_PORT` tạo real embedded-server boundary, kéo theo thay đổi về client, thread và runtime observation.

## Khi nào `WebEnvironment.NONE` phù hợp?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:00–03:15`

**Visual:**

Nối dài lifecycle/timeline hiện tại sang phase kế tiếp thay vì dựng lại từ đầu: loại node web server và mock-web nhưng giữ `SpringApplication` → `ApplicationContext` thường, thể hiện Boot configuration không có Servlet/Reactive web infrastructure.

**Script:**

Real-server mode không phải lúc nào cũng cần, nên `NONE` hoàn tất mô hình bằng full Boot context nhưng chủ động tắt web infrastructure.

**Purpose:**

Hoàn tất mô hình WebEnvironment bằng case non-web có chủ đích trước khi chuyển sang client cần server thật.

### Scene 1 — Khi nào `WebEnvironment.NONE` phù hợp?

**Time:** `03:15–04:08`

**Visual:**

Loại node web server và mock-web nhưng giữ `SpringApplication` → `ApplicationContext` thường, thể hiện Boot configuration không có Servlet/Reactive web infrastructure.

**Script:**

`NONE` vẫn boot application qua `SpringApplication` nhưng không cấu hình web environment. Nó đúng khi test cần full Boot configuration/auto-configuration nhưng chủ động loại các mối quan tâm của Servlet hoặc Reactive web runtime. Ví dụ command-line application, scheduler, component xử lý batch hoặc configuration integration test có thể cần độ sát thực tế của quá trình khởi động Boot mà không cần mock web hạ tầng hay server.

**Purpose:**

Giải thích `NONE` như lựa chọn full-context nhưng cố ý không có web infrastructure khi Boot configuration vẫn cần được kiểm thử.

## Các test client do Boot hỗ trợ nằm ở đâu trong kiểm thử chạy server thật?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:08–04:23`

**Visual:**

Nối dài lifecycle/timeline hiện tại sang phase kế tiếp thay vì dựng lại từ đầu: draw client → live-server arrows: `WebTestClient` when WebFlux is available and `TestRestTemplate` as the REST-style alternative; keep `WebEnvironment` as the control that created the server.

**Script:**

Sau khi chọn running-server mode, concern thực tế kế tiếp là Boot-provided client nào phù hợp để điều khiển boundary đó.

**Purpose:**

Chuyển từ việc server được tạo sang client đi qua server boundary, đồng thời giữ environment choice tách khỏi client convenience.

### Scene 1 — Các test client do Boot hỗ trợ nằm ở đâu trong kiểm thử chạy server thật?

**Time:** `04:23–05:27`

**Visual:**

Vẽ mũi tên client → live server: `WebTestClient` khi WebFlux có mặt và `TestRestTemplate` là lựa chọn REST-style; giữ `WebEnvironment` làm control đã tạo server.

**Script:**

Với test chạy server thật, Boot có thể cung cấp `WebTestClient` tự phân giải URL tương đối theo server đang chạy. Nếu WebFlux không có hoặc không muốn thêm chỉ để làm client, Boot cũng cung cấp `TestRestTemplate` cho lời gọi kiểu REST. Các client này là tiện ích quanh web environment đã chọn. Chúng không quyết định server có tồn tại hay không; `WebEnvironment` mới quyết định điều đó. API gửi request và kiểm tra kết quả của client thuộc phần hỗ trợ kiểm thử web tương ứng của Spring.

**Purpose:**

Ghép Boot-provided test client với running-server test để client phản ánh đúng boundary mà test đang chứng minh.

## Vì sao kiểm thử chạy server thật thay đổi kỳ vọng về rollback transaction?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:27–05:42`

**Visual:**

Giữ kết quả quan sát được từ cảnh trước và đưa nó vào cơ chế kế tiếp: chỉ hiện đoạn code/cấu hình nhỏ nhất cần cho “Vì sao kiểm thử chạy server thật thay đổi kỳ vọng về rollback transaction?”, rồi nối input đó với test-context hoặc runtime boundary kết quả.

**Script:**

Client đi qua server cũng đi qua thread khác, vì vậy transaction rollback expectation phải thay đổi.

**Purpose:**

Dùng chính việc client và server chạy trên các thread khác nhau để dẫn sang transaction boundary; đây là nguyên nhân rollback do test quản lý không bao trùm công việc phía server.

### Scene 1 — Vì sao kiểm thử chạy server thật thay đổi kỳ vọng về rollback transaction?

**Time:** `05:42–06:39`

**Visual:**

Chỉ hiện đoạn code/cấu hình nhỏ nhất cần cho “Vì sao kiểm thử chạy server thật thay đổi kỳ vọng về rollback transaction?”, rồi nối input đó với test-context hoặc runtime boundary kết quả.

**Script:**

Với `RANDOM_PORT` hoặc `DEFINED_PORT`, test client và server xử lý request trên các thread khác nhau. Vì vậy test-managed `@Transactional` transaction không tự động bao quanh transaction được application code mở ở server side. Transaction của test method vẫn có thể rollback phần việc của chính nó, nhưng thay đổi do server commit có thể còn lại. Ngữ nghĩa chi tiết của test-managed transaction thuộc Spring TestContext; bài học riêng của Boot là server thật đã vượt qua ranh giới transaction đó.

**Purpose:**

Làm rõ hệ quả transaction của real-server test: rollback ở test thread không tự rollback công việc chạy trong server thread.

## Phạm vi kiểm thử bàn giao sang web runtime và Spring web testing ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:39–06:53`

**Visual:**

Giữ các ownership lane và chuyển trách nhiệm kế tiếp qua đúng boundary: use ownership swimlanes for Boot testing, Spring TestContext, JUnit/Mockito, web/data frameworks, and Testcontainers; move each responsibility into the lane that actually implements it.

**Script:**

Khác biệt transaction này làm lộ ownership boundary cuối cùng: Boot cấu hình environment, còn web runtime và Spring web testing sở hữu mechanics sâu hơn.

**Purpose:**

Khép cơ chế hiện tại bằng cách bàn giao trách nhiệm kế tiếp cho đúng framework hoặc library sở hữu nó.

### Scene 1 — Phạm vi kiểm thử bàn giao sang web runtime và Spring web testing ở đâu?

**Time:** `06:53–07:54`

**Visual:**

Dùng ownership swimlane cho Boot testing, Spring TestContext, JUnit/Mockito, web/data framework và Testcontainers; chuyển từng responsibility vào đúng lane thực sự triển khai nó.

**Script:**

Ở phía Boot, trách nhiệm chính là lựa chọn `WebEnvironment`, các client cho server đang chạy do Boot cung cấp và cách các tiện ích đó tích hợp với Boot test context. Việc chọn production server, port, TLS, forwarded headers và các hành vi runtime khác của server thuộc module Spring Boot web-runtime. `MockMvc`, API test của Spring MVC, cơ chế gửi request/kiểm tra kết quả của `WebTestClient` và hành vi web-test tổng quát thuộc Spring Framework testing. Module này sử dụng các công cụ đó nhưng không định nghĩa lại chúng.

**Purpose:**

Đánh dấu ownership handoff từ phần Boot dựng web environment sang web runtime và Spring web-testing mechanics để debug đúng tầng.
