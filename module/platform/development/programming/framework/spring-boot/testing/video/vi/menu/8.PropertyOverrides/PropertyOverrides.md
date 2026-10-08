---
video:
  url: ""
---

# Properties dành cho test và `@TestConfiguration`

<!--
VIDEO SCRIPT FORMAT
- Each H2 maps 1:1 to the current Knowledge H2 order.
- The first section contains a Scene; later sections contain a conceptual Transition and a Scene.
- Timing is estimated from spoken density and must remain sequential.
-->

## Vì sao cần tùy biến Boot test context cho một kịch bản cụ thể?

<!-- VIDEO_SECTION -->

### Scene 1 — Vì sao cần tùy biến Boot test context cho một kịch bản cụ thể?

**Time:** `00:00–01:03`

**Visual:**

Hiện một production context shape tách ra một test scenario có local override layer, còn các test không liên quan vẫn giữ baseline.

**Script:**

Test đôi khi cần một biến thể có kiểm soát của application: bật feature flag, rút ngắn timeout, thay external endpoint hoặc thêm bean chỉ dành cho test. Boot cung cấp cơ chế tùy biến cục bộ để các thay đổi này nằm trong phạm vi test thay vì rò vào cấu hình application thường. Chọn tùy biến nhỏ nhất đủ diễn đạt kịch bản. Mỗi property hoặc configuration class bổ sung cũng có thể ảnh hưởng khả năng tái sử dụng context, nên tùy biến test là một phần của thiết kế bộ test.

**Purpose:**

Giải thích vì sao một test scenario riêng có thể cần cấu hình cục bộ mà không thay production application hoặc mọi test context khác.

## Properties trên annotation test của Boot ghi đè cấu hình cục bộ như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:03–01:18`

**Visual:**

Giữ configuration graph hiện tại và chỉ thay input/import kế tiếp: overlay test annotation properties on the normal property set, then show the resulting effective Environment and context-cache identity.

**Script:**

Khi test cần behavior riêng cho một scenario, customization nhẹ nhất thường là local property override thay vì tạo application configuration mới.

**Purpose:**

Chuyển từ nhu cầu tùy biến riêng một scenario sang override nhẹ nhất: property chỉ tác động test context đó.

### Scene 1 — Properties trên annotation test của Boot ghi đè cấu hình cục bộ như thế nào?

**Time:** `01:18–02:12`

**Visual:**

Chồng property từ test annotation lên property set thông thường, rồi hiện effective `Environment` và context-cache identity kết quả.

**Script:**

Annotation test của Boot như `@SpringBootTest` có thuộc tính `properties` cho các property riêng của test được khai báo trực tiếp. Những giá trị này được thêm vào test environment của context tương ứng và có thể ghi đè application configuration thường trong kịch bản đó. Cách này thuận tiện cho các giá trị ghi đè cục bộ nhỏ. Mô hình precedence/binding rộng hơn vẫn thuộc externalized configuration và hỗ trợ property source của Spring TestContext.

**Purpose:**

Cho thấy property khai báo trên test annotation override configuration cục bộ ra sao và trở thành một phần của effective context identity.

## `@TestConfiguration` giải quyết vấn đề gì?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:12–02:27`

**Visual:**

Giữ configuration graph hiện tại và chỉ thay input/import kế tiếp: place primary application configuration and `@TestConfiguration` side by side feeding the same context; the test configuration adds beans instead of replacing the primary source.

**Script:**

Property chỉ thay value; một số scenario cần thêm bean hoặc test-only wiring, đó là bài toán `@TestConfiguration` giải quyết.

**Purpose:**

Cho thấy vì sao property override chưa đủ khi scenario cần thêm test-only bean.

### Scene 1 — `@TestConfiguration` giải quyết vấn đề gì?

**Time:** `02:27–03:20`

**Visual:**

Đặt primary application configuration và `@TestConfiguration` cạnh nhau cùng đi vào một context; test configuration chỉ thêm bean chứ không thay primary source.

**Script:**

`@TestConfiguration` đánh dấu configuration chỉ dành cho test. Khác với `@Configuration` lồng nhau có thể trở thành cấu hình chính của test, `@TestConfiguration` lồng nhau được thêm bên cạnh cấu hình chính thường của application. Nó đúng cho bean chỉ dành cho test, hạ tầng adapter thay thế hoặc phần hỗ trợ có thể tái sử dụng nhưng không bao giờ nên được phát hiện như production configuration thường.

**Purpose:**

Định nghĩa `@TestConfiguration` là additive test-only bean configuration chứ không phải replacement cho primary Boot configuration của application.

## Bổ sung cấu hình chỉ dành cho test mà không thay thế cấu hình chính của ứng dụng như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:20–03:35`

**Visual:**

Giữ configuration graph hiện tại và chỉ thay input/import kế tiếp: show nested/imported `@TestConfiguration` joining the primary configuration before bean creation, with test-only beans clearly marked.

**Script:**

`@TestConfiguration` chỉ an toàn khi được thêm mà không thay primary application model, nên bước kế tiếp là cách configuration này tham gia context.

**Purpose:**

Mang ý tưởng TestConfiguration bổ sung sang cách những bean đó tham gia application model chính mà không thay thế nó.

### Scene 1 — Bổ sung cấu hình chỉ dành cho test mà không thay thế cấu hình chính của ứng dụng như thế nào?

**Time:** `03:35–04:26`

**Visual:**

Hiện `@TestConfiguration` nested/imported tham gia cùng primary configuration trước bean creation; đánh dấu rõ các test-only bean.

**Script:**

`@TestConfiguration` lồng nhau được kết hợp với cấu hình Boot chính trong cách tổ chức test. `@TestConfiguration` cấp cao nhất có thể được import tường minh bằng `@Import` khi nhiều test cần dùng chung phần hỗ trợ đó. Điểm quan trọng là mục đích bổ sung: test configuration bổ sung cho mô hình application production thay vì âm thầm trở thành cấu hình chính mới.

**Purpose:**

Cho thấy test-only configuration được import hoặc lồng vào thế nào để thêm bean mà không đổi primary application model Boot đang dùng.

## Khác biệt về property và configuration có thể làm phân mảnh khả năng tái sử dụng context như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:26–04:41`

**Visual:**

Giữ configuration graph hiện tại và chỉ thay input/import kế tiếp: display context-cache cards keyed by properties, profiles, imports, mocks/spies, and dynamic customizers; identical cards merge, differing inputs create new cache entries.

**Script:**

Mỗi property hoặc imported configuration bổ sung đều thay effective context description, dẫn trực tiếp tới câu hỏi cache reuse và fragmentation.

**Purpose:**

Làm lộ cache consequence của property/import cục bộ để chi phí customization nhìn thấy được ở quy mô test suite.

### Scene 1 — Khác biệt về property và configuration có thể làm phân mảnh khả năng tái sử dụng context như thế nào?

**Time:** `04:41–05:42`

**Visual:**

Hiện các context-cache card được key bởi property, profile, import, mock/spy và dynamic customizer; card giống nhau tái sử dụng, input khác tạo cache entry mới.

**Script:**

Spring TestContext chỉ tái sử dụng context khi cấu hình test thực tế trùng khớp. Property khai báo trực tiếp, profile, imported configuration, mock và các bộ tùy biến context khác nhau có thể tạo ra cache key khác nhau. Vì vậy nhiều biến thể configuration riêng lẻ có thể khiến bộ test phải khởi động lại nhiều Boot context gần giống nhau. Hãy ưu tiên test configuration dùng chung và tập property ổn định khi chúng mô tả cùng một ranh giới kịch bản.

**Purpose:**

Làm rõ context-cache fragmentation bằng cách nối khác biệt về property, profile, import và replacement với các effective test-context configuration khác nhau.

## Giá trị ghi đè riêng cho test bàn giao sang externalized configuration và Spring TestContext ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:42–05:56`

**Visual:**

Giữ các ownership lane và chuyển trách nhiệm kế tiếp qua đúng boundary: use ownership swimlanes for Boot testing, Spring TestContext, JUnit/Mockito, web/data frameworks, and Testcontainers; move each responsibility into the lane that actually implements it.

**Script:**

Khi reuse khác dự kiến, chẩn đoán phải đi qua hai owner: Boot cung cấp override hook, còn externalized configuration và Spring TestContext quyết định value resolution cùng cache behavior.

**Purpose:**

Khép cơ chế hiện tại bằng cách bàn giao trách nhiệm kế tiếp cho đúng framework hoặc library sở hữu nó.

### Scene 1 — Giá trị ghi đè riêng cho test bàn giao sang externalized configuration và Spring TestContext ở đâu?

**Time:** `05:56–06:52`

**Visual:**

Dùng ownership swimlane cho Boot testing, Spring TestContext, JUnit/Mockito, web/data framework và Testcontainers; chuyển từng responsibility vào đúng lane thực sự triển khai nó.

**Script:**

Ở phía Boot, phần chịu trách nhiệm là các thuộc tính tiện ích và phần tích hợp `@TestConfiguration` được annotation test của Boot sử dụng. Externalized configuration sở hữu property source thường, precedence, profile, binding và ngữ nghĩa `@ConfigurationProperties`. Spring TestContext chịu trách nhiệm về test property source tổng quát, đăng ký dynamic property, tùy biến context và cache context. Module này giải thích cách Boot dùng các khả năng đó nhưng không định nghĩa lại cơ chế bên dưới.

**Purpose:**

Tách test-local override hook của Boot khỏi externalized-configuration precedence và Spring TestContext caching để ownership rõ khi value hoặc reuse khác dự kiến.