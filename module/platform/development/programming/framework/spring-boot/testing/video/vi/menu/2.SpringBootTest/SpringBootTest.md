---
video:
  url: ""
---

# Full application context với `@SpringBootTest`

<!--
VIDEO SCRIPT FORMAT
- Each H2 maps 1:1 to the current Knowledge H2 order.
- The first section contains a Scene; later sections contain a conceptual Transition and a Scene.
- Timing is estimated from spoken density and must remain sequential.
-->

## Khi nào full application context của Boot là ranh giới kiểm thử phù hợp?

<!-- VIDEO_SECTION -->

### Scene 1 — Khi nào full application context của Boot là ranh giới kiểm thử phù hợp?

**Time:** `00:00–01:07`

**Visual:**

Đặt unit, slice và full-context scope trên cùng trục fidelity; tô sáng điểm đầu tiên behavior đi qua nhiều layer do Boot quản lý.

**Script:**

Chọn full Boot context khi hành vi cần kiểm tra phụ thuộc nhiều tầng ứng dụng hoặc phụ thuộc cấu hình Boot gần với production phối hợp với nhau. Ví dụ gồm configuration binding cùng service wiring, tích hợp liên tầng, security/messaging hạ tầng hoặc điều kiện khởi động mà slice tập trung chủ động loại bỏ. Full-context test phải là lựa chọn độ sát thực tế có chủ đích. Nó tốn thời gian khởi động hơn và kéo theo nhiều hạ tầng hơn test tập trung, nên chỉ dùng để chứng minh hành vi mà context nhỏ hơn không thể thiết lập đáng tin cậy.

**Purpose:**

Xác định đúng tình huống cần full-context fidelity để `@SpringBootTest` được dùng cho hành vi liên tầng thật sự thay vì trở thành lựa chọn mặc định.

## `@SpringBootTest` nạp những gì vào test context?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:07–01:22`

**Visual:**

Giữ kết quả quan sát được từ cảnh trước và đưa nó vào cơ chế kế tiếp: reveal the full-context graph: primary configuration, component scanning, auto-configuration, external properties, then a separate switch for whether a real server starts.

**Script:**

Chọn full context chỉ có ý nghĩa khi biết lựa chọn đó thực sự nạp gì, nên tiếp theo hãy nhìn application model mà `@SpringBootTest` dựng lên.

**Purpose:**

Biến quyết định dùng full context thành danh sách cụ thể những gì context nạp trước khi đi sang configuration discovery.

### Scene 1 — `@SpringBootTest` nạp những gì vào test context?

**Time:** `01:22–02:13`

**Visual:**

Lần lượt hiện full-context graph: primary configuration, component scanning, auto-configuration, external property; sau đó tách riêng switch quyết định có khởi động server thật hay không.

**Script:**

Mặc định, `@SpringBootTest` tìm cấu hình Boot chính của application rồi dùng `SpringApplication` để tạo context. Vì vậy application configuration, component scanning, auto-configuration, externalized properties và các cơ chế khởi động khác của Boot đều có thể tham gia. Annotation này không nhất thiết khởi động network server. Mặc định, `WebEnvironment.MOCK` dùng mock web environment khi web hạ tầng có mặt. Việc khởi động server được điều khiển riêng bằng `webEnvironment`.

**Purpose:**

Cho thấy `@SpringBootTest` thực sự dựng những gì và không ngụ ý điều gì, đặc biệt việc load full context tách biệt với khởi động real server.

## Kiểm thử full context dùng lại cơ chế phát hiện cấu hình chính của Boot như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:13–02:28`

**Visual:**

Giữ configuration graph hiện tại và chỉ thay input/import kế tiếp: reuse the package-tree search and overlay the full-context test to show it starting from the same primary Boot configuration as the application.

**Script:**

Vì context này bắt đầu từ primary configuration của application, bước chẩn đoán kế tiếp là Boot tìm cấu hình đó cho test bằng cách nào.

**Purpose:**

Nối full context đã nạp với primary-configuration search để sai root configuration trở thành bootstrap problem có thể chẩn đoán.

### Scene 1 — Kiểm thử full context dùng lại cơ chế phát hiện cấu hình chính của Boot như thế nào?

**Time:** `02:28–03:31`

**Visual:**

Reuse the package-tree search and overlay the full-context test to show it starting from the same primary Boot configuration as the application.

**Script:**

Full-context test thường tái sử dụng chính `@SpringBootApplication` / `@SpringBootConfiguration` mà Boot tìm cho application. Điều này giảm việc lặp lại wiring giữa production và test, đồng thời làm các condition của auto-configuration chạy trên mô hình cấu hình thực tế hơn. Nếu test thật sự cần một cấu hình cấp cao nhất khác, có thể truyền class tường minh. Chọn lựa chọn này cẩn thận vì thay cấu hình chính cũng thay ý nghĩa của test và có thể làm nó ít đại diện cho luồng khởi động production.

**Purpose:**

Nối full-context test với primary-configuration discovery của Boot để chẩn đoán package placement hoặc explicit class khi startup chọn sai application model.

## Full context đem lại độ sát thực tế và chi phí khởi động nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:31–03:46`

**Visual:**

Giữ configuration graph hiện tại và chỉ thay input/import kế tiếp: plot fidelity against startup/failure surface; show context-cache reuse for identical context shapes and cache splits when properties, profiles, mocks, or imports differ.

**Script:**

Dùng lại production configuration tăng fidelity nhưng cũng tăng startup và cache cost, nên cần cân bằng trước khi mở rộng test suite.

**Purpose:**

Làm lộ chi phí của production-like wiring và context identity trước khi quyết định slice nhỏ hơn có phải boundary trung thực hơn hay không.

### Scene 1 — Full context đem lại độ sát thực tế và chi phí khởi động nào?

**Time:** `03:46–04:51`

**Visual:**

Plot fidelity against startup/failure surface; show context-cache reuse for identical context shapes and cache splits when properties, profiles, mocks, or imports differ.

**Script:**

Full context có độ sát thực tế cao vì nhiều production bean và auto-configuration cùng xuất hiện. Đổi lại là thời gian khởi động, khả năng kéo thêm external dependency và phạm vi lỗi có thể xảy ra lớn hơn. Spring context cache có thể phân bổ chi phí này khi nhiều test dùng cùng cấu hình thực tế. Mỗi biến thể không cần thiết về property, profile, mock hoặc imported configuration đều có thể tạo cached context khác. Vì vậy full-context test cũng cần cấu hình ổn định và có thể tái sử dụng.

**Purpose:**

Làm rõ trade-off giữa fidelity và startup cost, gồm cả context-cache reuse và các biến thể cấu hình làm cache bị phân mảnh.

## Khi nào nên dùng slice tập trung thay cho `@SpringBootTest`?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:51–05:06`

**Visual:**

Giữ context boundary hiện tại, rồi co/giãn nó để làm lộ quyết định scope kế tiếp: compare a focused slice with the full application graph; highlight the collaboration required by the assertion and choose the smaller graph only while that collaboration remains inside it.

**Script:**

Khi chi phí đã rõ, focused slice trở thành lựa chọn hợp lý nếu hành vi không cần toàn bộ application graph.

**Purpose:**

Dùng trade-off fidelity/chi phí để xác định điểm focused slice nên thay full context.

### Scene 1 — Khi nào nên dùng slice tập trung thay cho `@SpringBootTest`?

**Time:** `05:06–06:06`

**Visual:**

Đối chiếu focused slice với full application graph; tô sáng collaboration mà assertion cần và chỉ chọn graph nhỏ hơn khi collaboration đó vẫn nằm trọn bên trong.

**Script:**

Chọn slice khi mục tiêu kiểm thử thuộc một phần tập trung của application và hạ tầng không liên quan chỉ làm tăng chi phí hoặc nhiễu. `@WebMvcTest`, `@WebFluxTest`, `@DataJpaTest` và `@JdbcTest` chủ động giới hạn context và import test auto-configuration theo mục đích cụ thể. Slice không “kém đúng” nếu nó khớp ranh giới. Nó chỉ không đủ khi hành vi phụ thuộc đối tượng cộng tác bị loại bỏ, wiring liên tầng hoặc ngữ nghĩa khởi động đầy đủ của Boot.

**Purpose:**

Dạy lúc focused slice mới là boundary chính xác hơn vì hạ tầng không liên quan chỉ làm tăng chi phí và failure surface.

## `WebEnvironment` thay đổi mô hình full context như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:06–06:21`

**Visual:**

Giữ kết quả quan sát được từ cảnh trước và đưa nó vào cơ chế kế tiếp: chỉ hiện đoạn code/cấu hình nhỏ nhất cần cho “`WebEnvironment` thay đổi mô hình full context như thế nào?”, rồi nối input đó với test-context hoặc runtime boundary kết quả.

**Script:**

Full context và real HTTP server là hai quyết định độc lập, vì vậy phần cuối là xem `WebEnvironment` thay đổi cùng Boot context đó ra sao.

**Purpose:**

Tách rõ phạm vi context khỏi việc khởi động server trước khi chuyển sang `WebEnvironment`, tránh để “full context” bị hiểu nhầm thành “luôn có HTTP server thật”.

### Scene 1 — `WebEnvironment` thay đổi mô hình full context như thế nào?

**Time:** `06:21–07:13`

**Visual:**

Chỉ hiện đoạn code/cấu hình nhỏ nhất cần cho “`WebEnvironment` thay đổi mô hình full context như thế nào?”, rồi nối input đó với test-context hoặc runtime boundary kết quả.

**Script:**

`WebEnvironment` quyết định full Boot context dùng mock web environment, khởi động embedded server thật hay vô hiệu web hạ tầng. `MOCK` là mặc định. `RANDOM_PORT` và `DEFINED_PORT` tạo môi trường server thật, còn `NONE` tạo non-web context qua `SpringApplication`. Chương tiếp theo tập trung vào hệ quả vận hành của các chế độ này: lựa chọn client, port thực tế và ranh giới transaction khi request đi qua HTTP server thật.

**Purpose:**

Tách lựa chọn full context khỏi lựa chọn server bằng `WebEnvironment`, để mock hay real-server fidelity được chọn có chủ đích.
