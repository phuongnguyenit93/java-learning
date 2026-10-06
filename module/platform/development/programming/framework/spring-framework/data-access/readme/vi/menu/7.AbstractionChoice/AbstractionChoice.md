<a id="back-to-top"></a>

# Chọn abstraction truy cập dữ liệu

## Menu
- [Chọn tầng client của Spring JDBC](#choose-jdbc-client-layer)
- [Chọn JDBC hay R2DBC](#choose-jdbc-or-r2dbc)
- [Chọn Spring Data, ORM hay truy cập ở tầng Spring Framework](#choose-persistence-abstraction-level)
- [Tránh JDBC blocking trong luồng reactive](#blocking-jdbc-in-reactive-paths)
- [Mô hình tư duy Spring Data Access end-to-end](#data-access-end-to-end-model)

## <a id="choose-jdbc-client-layer">Chọn tầng client của Spring JDBC</a>

<details>
<summary>Xem chi tiết</summary>

Trong hệ Spring JDBC blocking, hãy chọn API nhỏ nhất nhưng diễn đạt ý định rõ nhất.

Dùng JdbcClient cho query/update có tham số thông thường. Nó có giao diện fluent gọn và thống nhất positional/named parameter.

Dùng JdbcTemplate hoặc NamedParameterJdbcTemplate khi cần callback ở tầng thấp, cấu hình template, batch API hoặc khả năng đặc thù.

Dùng SimpleJdbcInsert/SimpleJdbcCall khi insert/procedure dựa trên metadata chính là abstraction phù hợp.

~~~text
query/update thông thường       → JdbcClient
JDBC quy trình nâng cao          → template API
insert/procedure theo metadata  → SimpleJdbcInsert / SimpleJdbcCall
~~~

Các API này là nhiều tầng trong cùng Spring JDBC hệ sinh thái, không phải các thế hệ loại trừ nhau. Codebase có thể có quy ước mặc định nhưng vẫn dùng tầng thấp hơn khi thao tác cần.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="choose-jdbc-or-r2dbc">Chọn JDBC hay R2DBC</a>

<details>
<summary>Xem chi tiết</summary>

Chọn JDBC hay R2DBC dựa trên **mô hình thực thi xuyên suốt (end-to-end)**, mức hỗ trợ của driver và nhu cầu vận hành.

JDBC phù hợp khi ứng dụng chạy theo mô hình imperative/blocking, hệ sinh thái phụ thuộc thư viện chỉ hỗ trợ JDBC hoặc driver và công cụ của database trưởng thành nhất ở JDBC. Các connection pool trưởng thành cùng mức hỗ trợ rộng từ nhiều nhà cung cấp khiến JDBC vẫn là lựa chọn mặc định của nhiều ứng dụng.

R2DBC phù hợp khi hệ thống thật sự reactive cần truy cập database theo kiểu non-blocking và database có R2DBC driver đủ chất lượng cho production. Nó giữ lời gọi database trong luồng Publisher thay vì tạo cầu nối blocking.

R2DBC không phải "JDBC nhanh hơn". Non-blocking I/O có thể cải thiện mức sử dụng tài nguyên khi có nhiều tác vụ đồng thời, nhưng chất lượng SQL, cơ chế lock, năng lực database, độ trễ mạng và cấu hình pool vẫn chi phối nhiều loại tải.

Đừng chọn chỉ vì framework có hỗ trợ. Mô hình nên nhất quán từ xử lý request, transaction và data access cho tới driver.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="choose-persistence-abstraction-level">Chọn Spring Data, ORM hay truy cập ở tầng Spring Framework</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework Data Access phù hợp khi SQL tường minh và khả năng kiểm soát relational trực tiếp là điều quan trọng. Abstraction cao hơn giải quyết bài toán khác.

- Spring Data JDBC thêm aggregate mapping, repository và query abstraction trên relational blocking stack.
- Spring Data R2DBC thêm mapping/repository abstraction cho R2DBC.
- JPA/Hibernate thêm entity identity, persistence context, lazy association, dirty checking và ORM lifecycle.

Abstraction cao hơn không mặc định tốt hơn. Nó đổi mức độ nhìn thấy trực tiếp SQL lấy mapping/repository convention và nhiều hành vi persistence hơn.

~~~text
cần SQL tường minh + một lớp framework mỏng
    → Spring JDBC / Spring R2DBC

cần aggregate/repository mapping
    → Spring Data JDBC / R2DBC

cần ORM entity lifecycle/relationship model
    → JPA / Hibernate
~~~

Hãy chọn theo vấn đề chi phối. Không cần thêm repository hoặc ORM layer chỉ để che một lượng SQL vốn đã rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="blocking-jdbc-in-reactive-paths">Tránh JDBC blocking trong luồng reactive</a>

<details>
<summary>Xem chi tiết</summary>

Reactive method signature không biến JDBC blocking thành non-blocking. Nếu WebFlux/Reactor path gọi JdbcClient hoặc JdbcTemplate trên event-loop thread, thread đó vẫn có thể bị giữ trong toàn bộ thời gian database I/O.

Khi buộc phải kết hợp JDBC với reactive ứng dụng, cần cô lập công việc blocking trên bounded scheduler phù hợp và tính tới giới hạn connection pool. Đây là chiến lược tương thích hợp lệ nhưng vẫn tốn thread.

~~~java
Mono<Customer> customer = Mono.fromCallable(
        () -> jdbcClient.sql(sql)
            .param("id", id)
            .query(Customer.class)
            .single())
    .subscribeOn(Schedulers.boundedElastic());
~~~

Mẫu này bảo vệ event-loop thread, nhưng **không tạo non-blocking database I/O**.

Nếu ứng dụng cần luồng dữ liệu reactive end-to-end và database có driver phù hợp, dùng R2DBC. Nếu phần lớn ứng dụng imperative, ép R2DBC vào có thể tăng độ phức tạp mà không có lợi ích tương xứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="data-access-end-to-end-model">Mô hình tư duy Spring Data Access end-to-end</a>

<details>
<summary>Xem chi tiết</summary>

Có thể tóm tắt toàn module như một chuỗi quyết định thay vì danh sách class.

~~~text
1. Chọn mô hình thực thi
   imperative JDBC | reactive R2DBC

2. Cung cấp factory tài nguyên
   DataSource | ConnectionFactory

3. Chọn tầng truy cập Spring
   JdbcClient/Template | DatabaseClient

4. Bind giá trị và thực thi SQL tường minh

5. Map row với contract cardinality rõ ràng

6. Để Spring chuyển lỗi gốc
   → DataAccessException

7. Để tiện ích quản lý tài nguyên tham gia
   transaction được quản lý nếu có

8. Chỉ nâng abstraction khi thật sự cần
   → Spring Data / ORM / chính sách transaction
~~~

Đánh đổi cốt lõi nằm giữa mức độ kiểm soát và mức độ trừu tượng hóa. Spring Framework Data Access loại bỏ phần mã hạ tầng lặp lại nhưng vẫn giữ các thao tác quan hệ đủ rõ để người đọc suy luận.

Một DAO trưởng thành phải giúp quyền sở hữu tài nguyên, ý định SQL, kỳ vọng kết quả và ý nghĩa lỗi dễ suy luận hơn, không phải che chúng dưới thêm nhiều tầng không cần thiết.

</details>

- [Quay lại đầu trang](#back-to-top)
