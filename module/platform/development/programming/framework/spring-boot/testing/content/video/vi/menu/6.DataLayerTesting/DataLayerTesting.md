---
video:
  url: ""
---

# Kiểm thử data tập trung với `@DataJpaTest` và `@JdbcTest`

<!--
VIDEO SCRIPT FORMAT
- Each H2 maps 1:1 to the current Knowledge H2 order.
- The first section contains a Scene; later sections contain a conceptual Transition and a Scene.
- Timing is estimated from spoken density and must remain sequential.
-->

## Boot data slice cô lập phần nào của ứng dụng?

<!-- VIDEO_SECTION -->

### Scene 1 — Boot data slice cô lập phần nào của ứng dụng?

**Time:** `00:00–00:46`

**Visual:**

Hiện persistence-only context: repository/data-access component cùng data infrastructure ở trong; web, messaging và service không liên quan ở ngoài.

**Script:**

Data slice tập trung test context vào persistence hạ tầng và các component cần để kiểm tra hành vi data access. Nó chủ động không nạp web, messaging hoặc service-layer hạ tầng không liên quan. Điều cần đạt là context đủ lớn để chạy Boot persistence integration thực tế nhưng vẫn nhỏ và dễ suy luận hơn full application context.

**Purpose:**

Giải thích cách Boot data slice cô lập persistence behavior khỏi web/service layer nhưng vẫn cung cấp hạ tầng đủ cho focused database test.

## `@DataJpaTest` cấu hình gì cho kiểm thử tập trung vào JPA?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:46–01:01`

**Visual:**

Giữ context boundary hiện tại, rồi co/giãn nó để làm lộ quyết định scope kế tiếp: draw `@DataJpaTest` around entities, repositories, JPA/Hibernate infrastructure, transaction support, and test database configuration.

**Script:**

Data slice chỉ hữu ích khi biết chính xác nó chứa gì, nên bắt đầu với `@DataJpaTest` và JPA infrastructure mà Boot đặt trong boundary đó.

**Purpose:**

Chuyển từ data-slice boundary tổng quát sang JPA infrastructure cần cho repository và mapping test.

### Scene 1 — `@DataJpaTest` cấu hình gì cho kiểm thử tập trung vào JPA?

**Time:** `01:01–01:56`

**Visual:**

Vẽ boundary `@DataJpaTest` bao quanh entity, repository, JPA/Hibernate infrastructure, transaction support và test database configuration.

**Script:**

`@DataJpaTest` tập trung vào JPA component như entity và repository, đồng thời import test auto-configuration cần cho kiểm thử persistence hướng tới JPA. Nó đúng để kiểm tra mapping, hành vi repository, query và persistence integration mà không nạp toàn bộ application. Mặc định Boot cũng tham gia test database configuration, có thể thay database thường bằng embedded test database khi có sẵn. Hành vi đó có thể được tùy chỉnh khi test cần cách bố trí database khác.

**Purpose:**

Cho thấy `@DataJpaTest` thêm gì quanh JPA repository, entity, transaction và test-database support để boundary được hiểu chính xác.

## Mặc định transactional của `@DataJpaTest` ảnh hưởng test như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:56–02:11`

**Visual:**

Nối dài lifecycle/timeline hiện tại sang phase kế tiếp thay vì dựng lại từ đầu: hiện một test method nằm trong test-managed transaction: ghi dữ liệu, chạy assertion rồi rollback để trả isolation cho test kế tiếp.

**Script:**

Vì `@DataJpaTest` transactional mặc định, hệ quả cần hiểu tiếp là rollback thay đổi điều test quan sát được như thế nào.

**Purpose:**

Mang JPA slice setup sang rollback model quyết định isolation của persistence test.

### Scene 1 — Mặc định transactional của `@DataJpaTest` ảnh hưởng test như thế nào?

**Time:** `02:11–03:06`

**Visual:**

Hiện một test method nằm trong test-managed transaction: ghi dữ liệu, chạy assertion rồi rollback để trả isolation cho test kế tiếp.

**Script:**

`@DataJpaTest` mặc định có transaction. Trong mô hình test-managed transaction thường, thay đổi tạo ra trong test được rollback khi test kết thúc, giúp các persistence test tập trung cách ly nhau. Vòng đời chi tiết của test-managed transaction thuộc Spring TestContext. Điểm riêng của Boot cần nhớ là JPA slice mặc định tham gia mô hình kiểm thử có transaction đó, còn test chạy server thật có ranh giới thread/transaction khác.

**Purpose:**

Làm rõ transactional default của `@DataJpaTest`, đặc biệt automatic rollback chứng minh được gì và không chứng minh được gì về production transaction behavior.

## `@JdbcTest` cấu hình gì cho kiểm thử tập trung vào JDBC?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:06–03:21`

**Visual:**

Giữ context boundary hiện tại, rồi co/giãn nó để làm lộ quyết định scope kế tiếp: replace the ORM layer with `DataSource` + `JdbcTemplate`/JDBC infrastructure inside a focused `@JdbcTest` context.

**Script:**

JPA không phải focused persistence path duy nhất; `@JdbcTest` cho thấy cùng slice idea nhưng không có ORM layer.

**Purpose:**

Đối chiếu persistence hướng JPA với JDBC slice để technology scope quyết định slice được chọn.

### Scene 1 — `@JdbcTest` cấu hình gì cho kiểm thử tập trung vào JDBC?

**Time:** `03:21–04:20`

**Visual:**

Replace the ORM layer with `DataSource` + `JdbcTemplate`/JDBC infrastructure inside a focused `@JdbcTest` context.

**Script:**

`@JdbcTest` cung cấp context tập trung cho data access hướng tới JDBC. Nó auto-configure hạ tầng kiểm thử JDBC cho component làm việc trực tiếp với `DataSource`, `JdbcTemplate` hoặc cơ chế truy cập dữ liệu quan hệ tương tự, đồng thời loại tầng ứng dụng không liên quan. Hãy chọn slice này khi hành vi persistence cần kiểm tra tập trung vào JDBC thay vì JPA. Slice nên khớp ranh giới công nghệ mà configuration và tương tác của nó cần được chứng minh.

**Purpose:**

Đối chiếu `@JdbcTest` với JPA-focused testing bằng cách chỉ ra JDBC infrastructure được giữ lại và ORM layer bị loại bỏ.

## Khi nào test database auto-configuration cần được tùy chỉnh?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:20–04:35`

**Visual:**

Giữ kết quả quan sát được từ cảnh trước và đưa nó vào cơ chế kế tiếp: compare embedded replacement with a real container database; annotate dialect, native SQL, migrations, isolation, extensions, and driver behavior as possible fidelity differences.

**Script:**

Khi slice infrastructure đã chọn xong, database replacement trở thành quyết định fidelity kế tiếp nếu default test database không còn đại diện đúng behavior mục tiêu.

**Purpose:**

Mang persistence slice đã chọn sang quyết định database fidelity thay vì xem embedded replacement là đại diện cho mọi database.

### Scene 1 — Khi nào test database auto-configuration cần được tùy chỉnh?

**Time:** `04:35–05:36`

**Visual:**

Đối chiếu embedded database replacement với database thật trong container; đánh dấu dialect, native SQL, migration, isolation, extension và driver behavior là những điểm fidelity có thể khác.

**Script:**

Embedded test database mặc định hữu ích cho test tập trung nhanh, nhưng không phải lúc nào cũng đại diện production database. Khác biệt về SQL dialect, native query, migration, transaction isolation, extension hoặc driver có thể yêu cầu database thật. Boot cung cấp cơ chế như `@AutoConfigureTestDatabase` để điều chỉnh hành vi thay thế database. Nếu cần độ sát production của database thật, service connection dùng Testcontainers thường là ranh giới tốt hơn thay vì ép mọi data slice dùng database in-memory thay thế.

**Purpose:**

Xác định lúc test-database replacement mặc định hoặc embedded-database assumption cần tùy biến để khớp persistence behavior cần chứng minh.

## Khi nào data slice nên bàn giao sang integration test dùng dịch vụ thật?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:36–05:50`

**Visual:**

Giữ pipeline dependency/client đang chạy và nối tiếp qua bước tích hợp kế tiếp: extend the persistence boundary to a real database container and show Boot service connection feeding the same application client configuration.

**Script:**

Tùy biến data source vẫn giữ test trong slice; nếu behavior phụ thuộc real service boundary thì phải mở rộng sang real-service integration.

**Purpose:**

Chỉ nâng từ database-replacement setting sang real-service boundary khi product-specific database behavior thực sự là phần rủi ro.

### Scene 1 — Khi nào data slice nên bàn giao sang integration test dùng dịch vụ thật?

**Time:** `05:50–06:47`

**Visual:**

Mở persistence boundary tới database thật trong container và cho thấy Boot service connection cấp dữ liệu cho cùng application client configuration.

**Script:**

Hãy chuyển sang dịch vụ thật khi hành vi phụ thuộc chính sản phẩm database chứ không chỉ JPA/JDBC integration. Ví dụ gồm SQL riêng của vendor, hành vi indexing, extension, khả năng tương thích migration hoặc đặc tính connection mà embedded database không tái hiện được. Test vẫn có thể tập trung vào persistence nhưng dùng dịch vụ thật. Chương Testcontainers sau sẽ giải thích cách Boot service connection cung cấp connection details mà không phải tự nối từng property.

**Purpose:**

Đưa ra boundary rule để chuyển từ in-process data slice sang real service/database khi fidelity phụ thuộc external-system behavior.

## Hỗ trợ data slice của Boot bàn giao sang cơ chế kiểm thử persistence và transaction ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:47–07:01`

**Visual:**

Giữ các ownership lane và chuyển trách nhiệm kế tiếp qua đúng boundary: use ownership swimlanes for Boot testing, Spring TestContext, JUnit/Mockito, web/data frameworks, and Testcontainers; move each responsibility into the lane that actually implements it.

**Script:**

Bước mở rộng đó làm rõ ownership split cuối cùng giữa Boot slice setup và persistence/transaction mechanics bên dưới.

**Purpose:**

Khép cơ chế hiện tại bằng cách bàn giao trách nhiệm kế tiếp cho đúng framework hoặc library sở hữu nó.

### Scene 1 — Hỗ trợ data slice của Boot bàn giao sang cơ chế kiểm thử persistence và transaction ở đâu?

**Time:** `07:01–07:58`

**Visual:**

Dùng ownership swimlane cho Boot testing, Spring TestContext, JUnit/Mockito, web/data framework và Testcontainers; chuyển từng responsibility vào đúng lane thực sự triển khai nó.

**Script:**

Ở phía Boot, phần chịu trách nhiệm là slice annotation, test auto-configuration được chọn, tích hợp test database và hỗ trợ service connection riêng của Boot. Ngữ nghĩa JPA mapping, JDBC API, chi tiết triển khai repository, ngữ nghĩa transaction của database và vòng đời transaction trong TestContext thuộc các module tương ứng. Khi chẩn đoán, hãy xác định trước Boot đã lắp đúng test context chưa; sau đó mới đi sâu sang công nghệ persistence hoặc module chịu trách nhiệm về transaction.

**Purpose:**

Tách phần Boot dựng data slice khỏi persistence framework và Spring transaction mechanics để failure được chuyển đúng owner.