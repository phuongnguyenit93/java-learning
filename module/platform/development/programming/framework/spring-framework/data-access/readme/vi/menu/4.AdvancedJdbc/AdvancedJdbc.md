<a id="back-to-top"></a>

# Các quy trình Spring JDBC nâng cao

## Menu
- [Thao tác batch với JDBC](#jdbc-batch-operations)
- [Generated key](#jdbc-generated-keys)
- [Tùy biến trích xuất kết quả và xử lý callback](#jdbc-custom-extraction-callbacks)
- [SimpleJdbcInsert và SimpleJdbcCall](#simple-jdbc-insert-call)
- [Embedded database và khởi tạo bằng SQL script](#embedded-database-sql-initialization)

## <a id="jdbc-batch-operations">Thao tác batch với JDBC</a>

<details>
<summary>Xem chi tiết</summary>


Batch giúp giảm số lần round trip lặp lại bằng cách gửi nhiều bộ tham số qua JDBC batch facility. Nó hữu ích khi ghi nhiều row cùng kiểu, nhưng **batch không đồng nghĩa transaction** và cũng không bảo đảm database thực thi mọi row thành một thao tác vật lý duy nhất.

JdbcTemplate có các batchUpdate variant cho parameter set cố định hoặc thay đổi. Driver và database quyết định giới hạn batch, hành vi update count và nhiều chi tiết generated-key.

~~~java
jdbcTemplate.batchUpdate(
    "insert into customer(id, name) values (?, ?)",
    customers,
    100,
    (ps, customer) -> {
        ps.setLong(1, customer.id());
        ps.setString(2, customer.name());
    });
~~~

Cần tách ba khái niệm:

- **batch**: cách gửi statement;
- **transaction**: commit/rollback ranh giới;
- **chunking**: lượng công việc ứng dụng gom mỗi lần.

Batch quá lớn có thể tăng memory pressure hoặc thời gian giữ lock. Hãy đo với driver/database thật, dùng kích thước có giới hạn và không coi batching là cơ chế transaction hay khôi phục sau lỗi.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdbc-generated-keys">Generated key</a>

<details>
<summary>Xem chi tiết</summary>


Database thường sinh id hoặc giá trị khác trong lúc INSERT. Spring JDBC có thể yêu cầu các generated value đó qua JDBC generated-key support, thường với KeyHolder/GeneratedKeyHolder hoặc API phù hợp ở tầng client.

Pattern template cổ điển:

~~~java
KeyHolder keyHolder = new GeneratedKeyHolder();

jdbcTemplate.update(connection -> {
    PreparedStatement ps = connection.prepareStatement(
        "insert into customer(name) values (?)",
        Statement.RETURN_GENERATED_KEYS);
    ps.setString(1, name);
    return ps;
}, keyHolder);
~~~

Khả năng trả key phụ thuộc driver và database. Có hệ cần chỉ rõ tên column sinh tự động; có driver trả nhiều column hoặc numeric type đặc thù. Đừng mặc định mọi generated key đều là Long.

Nếu việc lấy generated key phức tạp, hãy khai báo rõ các cột bắt buộc và giữ giả định đặc thù database gần DAO. Tiện ích của Spring giảm mã hạ tầng lặp lại chứ không xóa khác biệt giữa các database.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdbc-custom-extraction-callbacks">Tùy biến trích xuất kết quả và xử lý callback</a>

<details>
<summary>Xem chi tiết</summary>


Row mapping tiện lợi phù hợp khi mỗi row độc lập tạo một object. Một số query không có hình dạng đó: cần gom nhiều row chung parent, aggregate qua nhiều row, đẩy dữ liệu sang sink khác hoặc tùy biến statement sâu hơn.

Callback của Spring cho phép ứng dụng sở hữu logic đặc biệt nhưng JdbcTemplate vẫn quản lý JDBC quy trình bao quanh.

Dùng:

- RowMapper khi từng row độc lập tạo một element;
- ResultSetExtractor khi cần xử lý toàn ResultSet;
- RowCallbackHandler khi xử lý từng row theo side effect;
- PreparedStatementCallback và callback liên quan khi cần control execution thấp hơn.

~~~java
Map<Long, OrderView> orders = jdbcTemplate.query(sql, rs -> {
    Map<Long, OrderView> result = new LinkedHashMap<>();
    while (rs.next()) {
        // gộp các row lặp về cùng order
    }
    return result;
});
~~~

Callback là extension point có contract, không phải lý do để tự quản lý lại toàn bộ tài nguyên. Không tự đóng Connection/ResultSet do Spring cấp nếu callback contract không giao quyền sở hữu đó cho bạn.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="simple-jdbc-insert-call">SimpleJdbcInsert và SimpleJdbcCall</a>

<details>
<summary>Xem chi tiết</summary>


SimpleJdbcInsert và SimpleJdbcCall là các tiện ích tái sử dụng cho những thao tác mà database metadata có thể giảm cấu hình lặp lại.

SimpleJdbcInsert có thể đọc table columns qua DatabaseMetaData rồi tạo INSERT từ Map hoặc parameter source. Có thể cấu hình explicit columns và generated-key columns.

SimpleJdbcCall dùng JDBC metadata để đơn giản hóa stored procedure/function call. Chất lượng tự phát hiện phụ thuộc database và driver. Nếu metadata thiếu hoặc không đáng tin, hãy declare parameters tường minh hoặc tắt metadata lookup.

~~~java
SimpleJdbcInsert insert = new SimpleJdbcInsert(dataSource)
    .withTableName("customer")
    .usingGeneratedKeyColumns("id");

Number id = insert.executeAndReturnKey(
    Map.of("name", "Ada", "status", "ACTIVE"));
~~~

Các tiện ích này không phải ORM. Chúng vẫn làm việc trực tiếp với table/procedure và dùng JdbcTemplate bên dưới. Metadata chỉ là tiện ích; cấu hình tường minh thường tốt hơn khi cần khả năng chuyển đổi giữa database, schema lạ hoặc stored-procedure signature khiến việc tự phát hiện trở nên mơ hồ.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="embedded-database-sql-initialization">Embedded database và khởi tạo bằng SQL script</a>

<details>
<summary>Xem chi tiết</summary>


Spring JDBC hỗ trợ tạo embedded database và khởi tạo database từ SQL script. Các tiện ích này hữu ích cho kiểm thử, ví dụ minh họa, khởi động môi trường cục bộ và những luồng khởi tạo ứng dụng có kiểm soát.

EmbeddedDatabaseBuilder có thể cấu hình các database chạy trong cùng process như H2, HSQL hoặc Derby khi dependency tương ứng có mặt. Hạ tầng datasource.init có thể chạy script tạo schema hoặc dữ liệu với cấu hình về dấu phân cách, comment, encoding và cách xử lý lỗi.

~~~java
DataSource dataSource = new EmbeddedDatabaseBuilder()
    .setType(EmbeddedDatabaseType.H2)
    .addScript("classpath:schema.sql")
    .addScript("classpath:test-data.sql")
    .build();
~~~

Ranh giới quan trọng: khởi tạo bằng SQL script **không tự trở thành chiến lược migration schema cho production**. Việc phát triển schema trong production thường cần thứ tự phiên bản, lịch sử migration, khả năng chạy lặp có kiểm soát, kỷ luật rollout và quản lý tương thích; đó là bài toán phù hợp hơn với các công cụ như Flyway hoặc Liquibase.

Cũng không nên giả định database nhúng mô phỏng hoàn toàn database production. SQL dialect, kiểu dữ liệu, cơ chế lock và hành vi của optimizer có thể khác đáng kể.
</details>

- [Quay lại đầu trang](#back-to-top)
