<a id="back-to-top"></a>

# Kiểm thử data tập trung với `@DataJpaTest` và `@JdbcTest`

## Menu
- [Boot data slice cô lập phần nào của ứng dụng?](#data-slice-purpose)
- [`@DataJpaTest` cấu hình gì cho kiểm thử tập trung vào JPA?](#datajpa-test-model)
- [Mặc định transactional của `@DataJpaTest` ảnh hưởng test như thế nào?](#datajpa-transaction-model)
- [`@JdbcTest` cấu hình gì cho kiểm thử tập trung vào JDBC?](#jdbc-test-model)
- [Khi nào test database auto-configuration cần được tùy chỉnh?](#data-test-database-customization)
- [Khi nào data slice nên bàn giao sang integration test dùng dịch vụ thật?](#data-slice-integration-handoff)
- [Hỗ trợ data slice của Boot bàn giao sang cơ chế kiểm thử persistence và transaction ở đâu?](#data-testing-framework-boundary)

## <a id="data-slice-purpose">Boot data slice cô lập phần nào của ứng dụng?</a>

<details>
<summary>Xem chi tiết</summary>
Data slice tập trung test context vào persistence infrastructure và các component cần để kiểm tra hành vi data access. Nó cố ý không nạp web, messaging hoặc service-layer infrastructure không liên quan.

Mục tiêu là context đủ lớn để chạy Boot persistence integration thực tế nhưng vẫn nhỏ và dễ suy luận hơn full application context.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Test Slices](https://docs.spring.io/spring-boot/3.3/appendix/test-auto-configuration/slices.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="datajpa-test-model">`@DataJpaTest` cấu hình gì cho kiểm thử tập trung vào JPA?</a>

<details>
<summary>Xem chi tiết</summary>
`@DataJpaTest` tập trung vào JPA component như entity và repository, đồng thời import test auto-configuration cần cho kiểm thử persistence hướng tới JPA. Nó phù hợp để kiểm tra mapping, hành vi repository, query và persistence integration mà không nạp toàn bộ application.

Mặc định Boot cũng tham gia test database configuration, có thể thay database thông thường bằng embedded test database khi có sẵn. Hành vi đó có thể được tùy chỉnh khi test cần cách bố trí database khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="datajpa-transaction-model">Mặc định transactional của `@DataJpaTest` ảnh hưởng test như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
`@DataJpaTest` mặc định có transaction. Trong mô hình test-managed transaction thông thường, thay đổi tạo ra trong test được rollback khi test kết thúc, giúp các persistence test tập trung cách ly nhau.

Vòng đời chi tiết của test-managed transaction thuộc Spring TestContext. Điểm riêng của Boot cần nhớ là JPA slice mặc định tham gia mô hình kiểm thử có transaction đó, còn test chạy server thật có ranh giới thread/transaction khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdbc-test-model">`@JdbcTest` cấu hình gì cho kiểm thử tập trung vào JDBC?</a>

<details>
<summary>Xem chi tiết</summary>
`@JdbcTest` cung cấp context tập trung cho data access hướng tới JDBC. Nó auto-configure hạ tầng kiểm thử JDBC cho component làm việc trực tiếp với `DataSource`, `JdbcTemplate` hoặc cơ chế truy cập dữ liệu quan hệ tương tự, đồng thời loại application layer không liên quan.

Hãy chọn slice này khi hành vi persistence cần kiểm tra tập trung vào JDBC thay vì JPA. Slice nên khớp ranh giới công nghệ mà configuration và tương tác của nó cần được chứng minh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="data-test-database-customization">Khi nào test database auto-configuration cần được tùy chỉnh?</a>

<details>
<summary>Xem chi tiết</summary>
Embedded test database mặc định hữu ích cho test tập trung nhanh, nhưng không phải lúc nào cũng đại diện production database. Khác biệt về SQL dialect, native query, migration, transaction isolation, extension hoặc driver có thể yêu cầu database thật.

Boot cung cấp cơ chế như `@AutoConfigureTestDatabase` để điều chỉnh hành vi thay thế database. Nếu cần độ sát production của database thật, service connection dùng Testcontainers thường là ranh giới tốt hơn thay vì ép mọi data slice dùng database in-memory thay thế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="data-slice-integration-handoff">Khi nào data slice nên bàn giao sang integration test dùng dịch vụ thật?</a>

<details>
<summary>Xem chi tiết</summary>
Hãy chuyển sang dịch vụ thật khi hành vi phụ thuộc chính sản phẩm database chứ không chỉ JPA/JDBC integration. Ví dụ gồm SQL riêng của vendor, hành vi indexing, extension, khả năng tương thích migration hoặc đặc tính connection mà embedded database không tái hiện được.

Test vẫn có thể tập trung vào persistence nhưng dùng dịch vụ thật. Chương Testcontainers sau sẽ giải thích cách Boot service connection cung cấp connection details mà không phải tự nối từng property.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="data-testing-framework-boundary">Hỗ trợ data slice của Boot bàn giao sang cơ chế kiểm thử persistence và transaction ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>
Boot sở hữu slice annotation, test auto-configuration được chọn, tích hợp test database và hỗ trợ service connection riêng của Boot. Ngữ nghĩa JPA mapping, JDBC API, chi tiết triển khai repository, ngữ nghĩa transaction của database và vòng đời transaction trong TestContext thuộc các module tương ứng.

Khi chẩn đoán, hãy xác định trước Boot đã lắp đúng test context chưa; sau đó mới đi sâu sang công nghệ persistence hoặc module chịu trách nhiệm về transaction.

</details>

- [Quay lại đầu trang](#back-to-top)
