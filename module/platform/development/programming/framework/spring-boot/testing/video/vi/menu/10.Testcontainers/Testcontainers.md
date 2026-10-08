---
video:
  url: ""
---

# Service connection của Testcontainers và `ConnectionDetails`

<!--
VIDEO SCRIPT FORMAT
- Each H2 maps 1:1 to the current Knowledge H2 order.
- The first section contains a Scene; later sections contain a conceptual Transition and a Scene.
- Timing is estimated from spoken density and must remain sequential.
-->

## Vì sao Boot cung cấp service connection cho Testcontainers?

<!-- VIDEO_SECTION -->

### Scene 1 — Vì sao Boot cung cấp service connection cho Testcontainers?

**Time:** `00:00–01:10`

**Visual:**

Vẽ pipeline container → Boot `ConnectionDetails` → auto-configuration thông thường → application client, thay cho việc copy thủ công dynamic endpoint property.

**Script:**

Testcontainers có thể khởi động một service thật, nhưng application vẫn cần thông tin kết nối như host, port, thông tin xác thực hoặc URL. Boot service connection nối khoảng trống đó bằng cách tạo `ConnectionDetails` có kiểu đúng từ container được hỗ trợ rồi cung cấp cho application auto-configuration. Kết quả là integration test gọn hơn: field container được quản lý qua tích hợp JUnit của Testcontainers vẫn dùng vòng đời do Testcontainers quản lý, còn container được khai báo dưới dạng Spring `@Bean` đi theo vòng đời Spring application context. Trong cả hai trường hợp, Boot cấu hình client hạ tầng thường từ service connection thay vì mỗi test tự sao chép các giá trị động của container vào properties.

**Purpose:**

Giải thích giá trị của Boot service connection: chuyển running Testcontainers dependency thành connection information ứng dụng dùng được mà không hard-code endpoint property.

## Test dependency `spring-boot-testcontainers` kích hoạt khả năng gì?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:10–01:25`

**Visual:**

Giữ pipeline dependency/client đang chạy và nối tiếp qua bước tích hợp kế tiếp: show `spring-boot-testcontainers` as the adapter layer between Testcontainers and Boot auto-configuration; keep Testcontainers/JUnit lifecycle outside that adapter.

**Script:**

Service connection chỉ hoạt động khi Boot integration module tương ứng có mặt, nên bước tiếp theo là dependency bật các adapter đó.

**Purpose:**

Chuyển từ service-connection concept sang Boot adapter module cung cấp các ConnectionDetails factory.

### Scene 1 — Test dependency `spring-boot-testcontainers` kích hoạt khả năng gì?

**Time:** `01:25–02:17`

**Visual:**

Cho thấy `spring-boot-testcontainers` là adapter layer giữa Testcontainers và Boot auto-configuration; giữ lifecycle Testcontainers/JUnit nằm ngoài adapter đó.

**Script:**

Phần tích hợp Testcontainers của Boot nằm trong module `spring-boot-testcontainers`. Thêm module này ở test scope sẽ cung cấp `@ServiceConnection` cùng các connection-details factory có thể nhận diện loại Testcontainers được hỗ trợ hoặc container image. Dependency này không thay thế thư viện Testcontainers hay phần tích hợp JUnit của nó. Nó bổ sung adapter riêng của Boot để biến thông tin container thành connection details mà Boot auto-configuration có thể sử dụng.

**Purpose:**

Xác định vai trò của `spring-boot-testcontainers` để người học biết integration module nào bật service-connection support quanh Testcontainers.

## Vì sao `ConnectionDetails` từ service connection ưu tiên hơn connection properties?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:17–02:32`

**Visual:**

Giữ kết quả quan sát được từ cảnh trước và đưa nó vào cơ chế kế tiếp: chỉ hiện đoạn code/cấu hình nhỏ nhất cần cho “Vì sao `ConnectionDetails` từ service connection ưu tiên hơn connection properties?”, rồi nối input đó với test-context hoặc runtime boundary kết quả.

**Script:**

Khi integration module đã load, kết quả runtime quan trọng là typed `ConnectionDetails` có thể được auto-configuration ưu tiên hơn connection property thông thường.

**Purpose:**

Chuyển từ việc có adapter module sang kết quả runtime mà adapter tạo ra: typed `ConnectionDetails` trở thành nguồn kết nối có quyền ưu tiên cho auto-configuration.

### Scene 1 — Vì sao `ConnectionDetails` từ service connection ưu tiên hơn connection properties?

**Time:** `02:32–03:31`

**Visual:**

Chỉ hiện đoạn code/cấu hình nhỏ nhất cần cho “Vì sao `ConnectionDetails` từ service connection ưu tiên hơn connection properties?”, rồi nối input đó với test-context hoặc runtime boundary kết quả.

**Script:**

Khi Boot auto-configuration nhận một `ConnectionDetails` bean đúng, connection details đó có độ ưu tiên cao hơn các configuration property liên quan tới kết nối. Nhờ vậy test container trở thành endpoint có thẩm quyền cho test mà không phải viết lại tập property application thường. Ưu tiên này có chủ ý vì service connection đại diện một dependency đang chạy thật với địa chỉ thường thay đổi. Các application property khác không liên quan vẫn theo mô hình externalized configuration thường.

**Purpose:**

Cho thấy vì sao typed `ConnectionDetails` được ưu tiên hơn connection property cạnh tranh, làm nguồn connectivity rõ ràng và ít lỗi hơn.

## `@ServiceConnection` hoạt động trên field container do Testcontainers quản lý như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:31–03:46`

**Visual:**

Giữ pipeline dependency/client đang chạy và nối tiếp qua bước tích hợp kế tiếp: show a static `@Container` + `@ServiceConnection` field: Testcontainers starts the container, Boot inspects type/image, creates `ConnectionDetails`, then configures the client.

**Script:**

Typed connection details vẫn cần một nguồn; `@ServiceConnection` trên managed container field là đường phổ biến từ running container tới object Boot này.

**Purpose:**

Truy ngược authoritative ConnectionDetails về field container do Testcontainers quản lý thường tạo ra chúng.

### Scene 1 — `@ServiceConnection` hoạt động trên field container do Testcontainers quản lý như thế nào?

**Time:** `03:46–04:39`

**Visual:**

Hiện field static `@Container` + `@ServiceConnection`: Testcontainers start container, Boot đọc type/image, tạo `ConnectionDetails`, rồi cấu hình client.

**Script:**

Mẫu thường gặp là field do Testcontainers quản lý trong một test class đã bật JUnit extension của Testcontainers bằng `@Testcontainers`. Field dùng `@Container` của Testcontainers cùng `@ServiceConnection` của Boot; Boot kiểm tra loại container hoặc image để tạo `ConnectionDetails` bean tương ứng. Việc khởi động và dừng container vẫn thuộc Testcontainers. Boot chỉ sử dụng thông tin kết nối của container đang chạy để cấu hình application hạ tầng.

**Purpose:**

Theo dõi `@ServiceConnection` trên container field do Testcontainers quản lý, từ container metadata qua connection-details factory tới Boot auto-configuration.

## Container bean trong `@TestConfiguration` tham gia service connection như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:39–04:54`

**Visual:**

Giữ pipeline dependency/client đang chạy và nối tiếp qua bước tích hợp kế tiếp: đặt container `@Bean @ServiceConnection` trong `@TestConfiguration`; cho thấy Spring start container trước dependent bean và stop container sau khi các dependent bean bị destroy.

**Script:**

Field-managed container không phải mô hình duy nhất; container bean trong `@TestConfiguration` cho thấy Spring lifecycle và service-connection adaptation phối hợp ra sao.

**Purpose:**

Đối chiếu Testcontainers-managed field với Spring-managed container bean để lifecycle ownership rõ trước khi đi sang generic container type.

### Scene 1 — Container bean trong `@TestConfiguration` tham gia service connection như thế nào?

**Time:** `04:54–05:53`

**Visual:**

Đặt container `@Bean @ServiceConnection` trong `@TestConfiguration`; cho thấy Spring start container trước dependent bean và stop container sau khi các dependent bean bị destroy.

**Script:**

Container cũng có thể được khai báo bằng phương thức `@Bean` trong `@TestConfiguration` và gắn `@ServiceConnection`. Cách này giúp cấu hình container có thể tái sử dụng và đưa bean vào test application context do Spring quản lý. Với phương thức bean, Boot dùng kiểu trả về đã khai báo để chọn connection-details factory mà không cần gọi sớm phương thức chỉ để xem Docker image. Vì vậy kiểu trả về container cụ thể cung cấp nhiều thông tin hơn `GenericContainer`.

**Purpose:**

Cho thấy container bean trong `@TestConfiguration` tham gia cả Spring lifecycle lẫn service-connection adaptation như thế nào.

## Khi nào `GenericContainer` cần tên service connection được chỉ định rõ?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:53–06:08`

**Visual:**

Giữ pipeline dependency/client đang chạy và nối tiếp qua bước tích hợp kế tiếp: compare a typed container return type with `GenericContainer<?>`; on the generic branch add `@ServiceConnection(name="...")` as the hint Boot needs without invoking the bean for its image.

**Script:**

Generic container có thể không tiết lộ service type, vì vậy một số case cần explicit connection name trước khi Boot chọn được adapter.

**Purpose:**

Làm lộ service-type information bị thiếu khiến explicit service-connection name trở nên cần thiết.

### Scene 1 — Khi nào `GenericContainer` cần tên service connection được chỉ định rõ?

**Time:** `06:08–06:57`

**Visual:**

Đối chiếu typed container return type với `GenericContainer<?>`; ở nhánh generic, thêm `@ServiceConnection(name="...")` làm hint để Boot chọn adapter mà không cần gọi bean chỉ để đọc image.

**Script:**

`GenericContainer` không xác định service bằng Java type. Với container bean, Boot có thể cần chỉ định rõ `@ServiceConnection(name = "...")` để chọn đúng connection-details factory mà không tạo container chỉ để phát hiện image. Chọn tên mà Boot service-connection factory nhận diện cho công nghệ đó. Đây là gợi ý tích hợp dành cho Boot, không phải tên container hay khái niệm vòng đời của Testcontainers.

**Purpose:**

Giải thích vì sao `GenericContainer` đôi lúc cần service-connection name tường minh để Boot chọn đúng connection-details adapter.

## Khi nào `@DynamicPropertySource` là phương án thay thế phù hợp hơn?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:57–07:12`

**Visual:**

Giữ pipeline dependency/client đang chạy và nối tiếp qua bước tích hợp kế tiếp: show the manual fallback: running container → static `@DynamicPropertySource` → `DynamicPropertyRegistry` → Spring Environment → application client configuration.

**Script:**

Khi không có service-connection factory phù hợp, chuyển sang `@DynamicPropertySource` và publish đúng property mà application cần.

**Purpose:**

Chuyển từ typed service connection sang fallback dynamic property thủ công nhưng vẫn giữ đúng real-service boundary.

### Scene 1 — Khi nào `@DynamicPropertySource` là phương án thay thế phù hợp hơn?

**Time:** `07:12–08:05`

**Visual:**

Hiện fallback thủ công: running container → static `@DynamicPropertySource` → `DynamicPropertyRegistry` → Spring `Environment` → application client configuration.

**Script:**

Chọn `@DynamicPropertySource` khi Boot chưa có service-connection factory đúng, application dùng contract property tùy chỉnh hoặc test cần công bố giá trị mà một loại `ConnectionDetails` được hỗ trợ không biểu diễn được. Cách này thủ công hơn: test đọc giá trị từ container rồi tự đăng ký dynamic properties. Cơ chế chung thuộc Spring TestContext; Boot service connection nên được ưu tiên khi tích hợp có kiểu đã biểu diễn đúng mục đích.

**Purpose:**

Đặt `@DynamicPropertySource` đúng vai trò fallback khi không có service-connection abstraction phù hợp hoặc test cần publish property tùy biến.

## Tích hợp của Boot kết thúc ở đâu và trách nhiệm Testcontainers tổng quát bắt đầu ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:05–08:20`

**Visual:**

Giữ kết quả quan sát được từ cảnh trước và đưa nó vào cơ chế kế tiếp: chỉ hiện đoạn code/cấu hình nhỏ nhất cần cho “Tích hợp của Boot kết thúc ở đâu và trách nhiệm Testcontainers tổng quát bắt đầu ở đâu?”, rồi nối input đó với test-context hoặc runtime boundary kết quả.

**Script:**

Fallback này cũng làm ownership boundary rõ: Boot thích nghi connectivity vào application context, còn Testcontainers vẫn sở hữu container và Docker mechanics.

**Purpose:**

Dùng fallback thủ công để làm rõ ownership boundary cuối cùng: Boot đưa thông tin kết nối vào application context, còn container lifecycle và Docker mechanics vẫn thuộc Testcontainers.

### Scene 1 — Tích hợp của Boot kết thúc ở đâu và trách nhiệm Testcontainers tổng quát bắt đầu ở đâu?

**Time:** `08:20–09:30`

**Visual:**

Chỉ hiện đoạn code/cấu hình nhỏ nhất cần cho “Tích hợp của Boot kết thúc ở đâu và trách nhiệm Testcontainers tổng quát bắt đầu ở đâu?”, rồi nối input đó với test-context hoặc runtime boundary kết quả.

**Script:**

Ở phía Boot, trách nhiệm chính là `@ServiceConnection`, các adapter `ConnectionDetails` và cách những thông tin đó đi vào Boot auto-configuration. Với container được quản lý qua annotation hoặc extension JUnit của Testcontainers, Testcontainers chịu trách nhiệm khởi động và dừng container. Container được khai báo dưới dạng Spring `@Bean` thì đi theo vòng đời Spring application context: Spring tạo và khởi động container cùng context rồi dừng nó khi context đóng. Docker image, network, wait strategy, cơ chế tái sử dụng container và kết nối Docker tổng quát vẫn thuộc Testcontainers. Nếu container do Testcontainers quản lý không khởi động hoặc network/wait strategy hoạt động sai, hãy kiểm tra Testcontainers. Với container bean do Spring quản lý, hãy kiểm tra thêm quá trình tạo bean và vòng đời context. Nếu container đang chạy nhưng Boot không cấu hình được client của ứng dụng từ nó, hãy kiểm tra phần tích hợp service connection.

**Purpose:**

Tách Boot service-connection adaptation khỏi trách nhiệm generic của Testcontainers như image behavior, network, wait strategy và container lifecycle.
