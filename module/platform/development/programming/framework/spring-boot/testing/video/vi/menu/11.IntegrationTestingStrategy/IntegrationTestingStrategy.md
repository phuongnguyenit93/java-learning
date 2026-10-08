---
video:
  url: ""
---

# Xây chiến lược kiểm thử nhất quán cho Spring Boot

<!--
VIDEO SCRIPT FORMAT
- Each H2 maps 1:1 to the current Knowledge H2 order.
- The first section contains a Scene; later sections contain a conceptual Transition and a Scene.
- Timing is estimated from spoken density and must remain sequential.
-->

## Unit test, Boot slice, full context, server thật và dịch vụ thật kết hợp với nhau như thế nào?

<!-- VIDEO_SECTION -->

### Scene 1 — Unit test, Boot slice, full context, server thật và dịch vụ thật kết hợp với nhau như thế nào?

**Time:** `00:00–01:06`

**Visual:**

Dùng decision tree bắt đầu từ hành vi assertion cần chứng minh, rồi phân nhánh unit → slice → full context → server thật → dịch vụ thật; đặt chi phí và độ sát thực tế trên cùng một trục.

**Script:**

Spring Boot testing cung cấp một dải mức độ sát thực tế của context thay vì một annotation dùng cho mọi trường hợp. Unit test thuần chạy object không cần Spring. Boot slice nạp Spring context tập trung. `@SpringBootTest` nạp full application context. Chế độ chạy server thật thêm embedded HTTP server, còn service connection thêm dependency bên ngoài thật. Hãy chọn mức đúng dựa vào hành vi cần chứng minh. Độ sát thực tế cao hữu ích cho ranh giới tích hợp nhưng đồng thời tăng thời gian khởi động, yêu cầu hạ tầng và số thành phần có thể gặp lỗi.

**Purpose:**

Trình bày toàn bộ testing spectrum từ unit test tới slice, full context, real server và real service như các mức fidelity tăng dần kèm startup/infrastructure cost tăng dần.

## Vì sao nên ưu tiên context nhỏ nhất vẫn đi qua đúng ranh giới cần kiểm thử?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:06–01:21`

**Visual:**

Giữ context boundary hiện tại, rồi co/giãn nó để làm lộ quyết định scope kế tiếp: draw the assertion boundary first, then shrink the context until the next shrink would remove a collaboration the assertion must prove.

**Script:**

Testing spectrum chỉ hữu ích khi dẫn tới scope decision, nên tiếp theo áp dụng smallest-context rule để không trả chi phí cho fidelity assertion không cần.

**Purpose:**

Biến fidelity spectrum thành quy tắc smallest-honest-context vẫn giữ collaboration cần kiểm thử.

### Scene 1 — Vì sao nên ưu tiên context nhỏ nhất vẫn đi qua đúng ranh giới cần kiểm thử?

**Time:** `01:21–02:24`

**Visual:**

Vẽ assertion boundary trước, rồi thu nhỏ context cho tới khi bước thu nhỏ tiếp theo sẽ làm mất một collaboration mà assertion phải chứng minh.

**Script:**

Context nhỏ nhất nhưng đủ dùng cho phản hồi nhanh hơn và lỗi rõ hơn, trong khi vẫn đi qua ranh giới tích hợp quan trọng. Controller test không cần database nếu contract chỉ phụ thuộc web mapping cùng đối tượng cộng tác service được kiểm soát; repository test không cần toàn bộ web layer. Đừng thu nhỏ context bằng cách mock mất chính tương tác mà test phải chứng minh. “Nhỏ nhất” nghĩa là tối thiểu sau khi giữ đúng ranh giới cần kiểm thử, không phải tối thiểu số lượng bean bằng mọi giá.

**Purpose:**

Dạy quy tắc smallest-honest-context: chọn boundary hẹp nhất nhưng vẫn đi qua mọi collaboration assertion phải chứng minh.

## Khi nào độ sát thực tế của server thật đáng với chi phí bổ sung?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:24–02:39`

**Visual:**

Giữ context boundary hiện tại, rồi co/giãn nó để làm lộ quyết định scope kế tiếp: compare mock web processing with a live HTTP socket/connector path; highlight redirect, connector, production server configuration, and end-to-end serialization as real-server reasons.

**Script:**

Context hẹp thường tốt hơn, nhưng một số rủi ro nằm đúng ở HTTP server boundary; đó là lúc real-server fidelity xứng đáng với chi phí tăng thêm.

**Purpose:**

Xác định rủi ro đầu tiên thực sự nằm ở HTTP-server boundary và vì thế xứng đáng mở rộng khỏi in-process web test.

### Scene 1 — Khi nào độ sát thực tế của server thật đáng với chi phí bổ sung?

**Time:** `02:39–03:37`

**Visual:**

Đối chiếu mock web processing với đường HTTP socket/connector thật; tô sáng redirect, connector, production server configuration và end-to-end serialization là lý do cần real server.

**Script:**

Chọn server thật khi hành vi phụ thuộc chính ranh giới server: tương tác HTTP ở cấp socket, server filter/connector, production server configuration, hành vi redirect, serialization qua luồng client/server thật hoặc web wiring end-to-end. Nếu mock web environment chứng minh được cùng contract thì nó thường rẻ và dễ chẩn đoán hơn. Test chạy server thật nên tồn tại vì ranh giới server thực sự quan trọng, không chỉ vì trông có vẻ “integration” hơn.

**Purpose:**

Đưa ra tiêu chí chỉ trả chi phí real server khi socket, connector, redirect, serialization hoặc end-to-end web wiring thật sự là phần rủi ro cần kiểm tra.

## Khi nào test nên dùng dịch vụ thật thông qua service connection?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:37–03:52`

**Visual:**

Giữ context boundary hiện tại, rồi co/giãn nó để làm lộ quyết định scope kế tiếp: compare fake/in-memory dependency with a real service container; highlight dialect, protocol, or engine-specific behavior as the fidelity gained.

**Script:**

Server fidelity bao phủ web boundary, còn một số integration risk nằm ngoài process và cần real external service.

**Purpose:**

Chuyển từ server fidelity sang external-service fidelity khi rủi ro nằm ở product hoặc protocol thật.

### Scene 1 — Khi nào test nên dùng dịch vụ thật thông qua service connection?

**Time:** `03:52–04:59`

**Visual:**

Đối chiếu fake/in-memory dependency với service thật trong container; tô sáng dialect, protocol hoặc engine-specific behavior là phần fidelity thu được.

**Script:**

Chọn dịch vụ thật khi hành vi riêng của sản phẩm là một phần của rủi ro: database dialect, broker protocol, search-engine mapping, ngữ nghĩa caching hoặc chi tiết tích hợp mà in-memory fake không tái hiện đáng tin cậy. Boot service connection giảm công sức cấu hình nhưng không làm hạ tầng thật trở nên miễn phí. Khởi động container, khả năng sẵn có của image, mức sử dụng tài nguyên và khởi tạo service đều làm tăng chi phí của bộ test, nên chỉ dành chúng cho test thực sự hưởng lợi từ độ sát thực tế đó.

**Purpose:**

Đưa ra tiêu chí dùng real external service khi protocol, driver, engine hoặc integration behavior không thể được mô phỏng đáng tin cậy bằng mock hay in-memory substitute.

## Giữ cấu hình test context có khả năng tái sử dụng như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:59–05:14`

**Visual:**

Giữ kết quả quan sát được từ cảnh trước và đưa nó vào cơ chế kế tiếp: show several tests pointing to one cached context shape, then demonstrate how unnecessary property/profile/import/mock differences split reuse into multiple startups.

**Script:**

Real service tăng setup cost, vì vậy reusable test-context configuration trở thành điều kiện để suite vẫn thực dụng.

**Purpose:**

Mang chi phí của hạ tầng thật sang kỷ luật tái sử dụng context để suite vẫn giữ được fidelity với chi phí hợp lý.

### Scene 1 — Giữ cấu hình test context có khả năng tái sử dụng như thế nào?

**Time:** `05:14–06:21`

**Visual:**

Hiện nhiều test cùng trỏ tới một cached context shape, rồi cho thấy khác biệt property/profile/import/mock không cần thiết làm reuse bị tách thành nhiều lần startup.

**Script:**

Spring context cache có thể biến một lần khởi động Boot context tốn kém thành chi phí một lần khi nhiều test dùng cùng cấu hình thực tế. Khả năng tái sử dụng giảm khi property, profile, imported configuration, định nghĩa mock/spy, dynamic context customizer hoặc đầu vào bootstrap khác nhau không cần thiết. Hãy nhóm test quanh cấu trúc context ổn định. Ưu tiên test configuration có thể tái sử dụng thay vì nhiều biến thể riêng lẻ gần giống nhau, và đưa test chỉ cần hành vi ở cấp object ra khỏi Spring hoàn toàn.

**Purpose:**

Cho thấy property, import, profile và replacement ổn định giúp Spring context cache tái sử dụng hiệu quả trong integration-test suite.

## Phân loại lỗi giữa bootstrap, slice, tùy biến context và service connection như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:21–06:36`

**Visual:**

Giữ kết quả quan sát được từ cảnh trước và đưa nó vào cơ chế kế tiếp: vẽ flow debug từ trái sang phải: bootstrap → slice selection/test auto-configuration → local properties/import/mock → service connection → container/service. Đánh dấu dừng ở boundary đầu tiên bị sai.

**Script:**

Ngay cả suite thiết kế tốt vẫn có failure; hãy phân loại boundary sai sớm nhất trước khi debug hạ tầng thấp hơn.

**Purpose:**

Biến scope selection thành đường debug có thứ tự và dừng ở boundary sai sớm nhất.

### Scene 1 — Phân loại lỗi giữa bootstrap, slice, tùy biến context và service connection như thế nào?

**Time:** `06:36–07:42`

**Visual:**

Vẽ flow debug từ trái sang phải: bootstrap → slice selection/test auto-configuration → local properties/import/mock → service connection → container/service. Đánh dấu dừng ở boundary đầu tiên bị sai.

**Script:**

Hãy phân loại lỗi theo ranh giới sớm nhất bị sai. Nếu context không tìm được cấu hình chính hoặc gặp lỗi trước khi bean sẵn sàng, kiểm tra Boot bootstrap. Nếu context tập trung thiếu bean, kiểm tra lựa chọn slice và test auto-configuration. Nếu sai giá trị hoặc đối tượng cộng tác, kiểm tra test properties, import, mock hoặc spy. Nếu application đã cấu hình đúng nhưng không kết nối được dependency thật, kiểm tra service connection rồi mới xuống service/container bên ngoài. Thứ tự này tránh chẩn đoán ở mức thấp trước khi test context được xác nhận đúng.

**Purpose:**

Cung cấp đường phân loại failure theo boundary từ bootstrap, slice selection, customization, service connection tới external dependency.

## Mối quan tâm kiểm thử nào thuộc Boot và phần nào thuộc module lân cận?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:42–07:56`

**Visual:**

Giữ các ownership lane và chuyển trách nhiệm kế tiếp qua đúng boundary: use ownership swimlanes for Boot testing, Spring TestContext, JUnit/Mockito, web/data frameworks, and Testcontainers; move each responsibility into the lane that actually implements it.

**Script:**

Khi failure đã được phân loại theo boundary, bước cuối là gán mỗi boundary cho framework hoặc library thực sự sở hữu nó.

**Purpose:**

Khép cơ chế hiện tại bằng cách bàn giao trách nhiệm kế tiếp cho đúng framework hoặc library sở hữu nó.

### Scene 1 — Mối quan tâm kiểm thử nào thuộc Boot và phần nào thuộc module lân cận?

**Time:** `07:56–09:06`

**Visual:**

Dùng ownership swimlane cho Boot testing, Spring TestContext, JUnit/Mockito, web/data framework và Testcontainers; chuyển từng responsibility vào đúng lane thực sự triển khai nó.

**Script:**

Giữ Boot testing tập trung vào cách Boot application được lắp ráp cho test: `@SpringBootTest`, web environment, slice, test auto-configuration, giá trị ghi đè cục bộ cho cấu hình Boot, tích hợp thay bean và service connection. Spring TestContext chịu trách nhiệm về vòng đời/cache context và test-managed transaction. Spring MVC/WebFlux testing sở hữu cơ chế kiểm thử request. JUnit vẫn chịu trách nhiệm về việc thực thi test. Mockito vẫn chịu trách nhiệm về hành vi mock. Testcontainers vẫn chịu trách nhiệm về phần tích hợp container/Docker tổng quát. Các module production web runtime, persistence, messaging và configuration sở hữu hành vi của application đang được kiểm thử.

**Purpose:**

Khép module bằng ownership map để biết vấn đề thuộc Boot testing, Spring TestContext, web/data framework, Mockito hay Testcontainers.