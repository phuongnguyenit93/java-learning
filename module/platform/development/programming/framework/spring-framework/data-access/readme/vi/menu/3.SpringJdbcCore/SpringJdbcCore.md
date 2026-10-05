<a id="back-to-top"></a>

# Spring JDBC Core

## Menu
- [DataSource và quy trình Spring JDBC](#jdbc-datasource-workflow)
- [JdbcClient và tầng Template](#jdbc-client-template-stack)
- [Binding tham số SQL theo vị trí và theo tên](#jdbc-parameter-binding)
- [Thực thi query, update và xử lý bằng callback](#jdbc-query-update-execution)
- [Row mapping và số lượng kết quả kỳ vọng](#jdbc-row-mapping-cardinality)
- [Chọn JdbcClient hay Template API](#jdbc-client-vs-template)

## <a id="jdbc-datasource-workflow">DataSource và quy trình Spring JDBC</a>

<details>
<summary>Xem chi tiết</summary>


DataSource là điểm vào chuẩn của JDBC để cung cấp Connection. Spring JDBC chủ động xây trên contract đó thay vì tạo một connection API riêng. Trong production, DataSource thường được cung cấp bởi một connection pool; việc sizing, validation và tuning pool/driver không thuộc phạm vi trách nhiệm của Spring JDBC.

JdbcTemplate tổ chức JDBC quy trình lặp lại quanh DataSource:

~~~text
lấy Connection
    ↓
chuẩn bị statement / bind tham số
    ↓
thực thi
    ↓
đọc row hoặc update count
    ↓
translate SQLException
    ↓
giải phóng Connection đúng cách
~~~

"Đúng cách" là điểm quan trọng. Khi transaction synchronization đang hoạt động, Connection có thể thuộc transaction hiện tại thay vì bị đóng vật lý sau từng lời gọi template. JdbcTemplate dùng tiện ích quản lý tài nguyên của Spring bên dưới để bên gọi không phải tự viết logic transaction-aware.

Ứng dụng vẫn sở hữu SQL và quyết định mapping. Spring sở hữu quy trình giúp các quyết định đó an toàn và lặp lại được.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdbc-client-template-stack">JdbcClient và tầng Template</a>

<details>
<summary>Xem chi tiết</summary>


Spring JDBC có nhiều tầng API vì thao tác phổ biến và extension point ở mức thấp cần độ chi tiết khác nhau.

- JdbcTemplate là delegate trung tâm cho JDBC imperative, có query/update/execute cùng callback contracts.
- NamedParameterJdbcTemplate wrap JdbcTemplate và chuyển Spring-style named parameter thành JDBC positional placeholder trước khi thực thi.
- JdbcClient, được thêm từ Spring Framework 6.1, cung cấp fluent facade thống nhất cho query/update phổ biến với positional hoặc named parameter.

JdbcClient không thay thế tầng thấp. Nó delegate thực thi xuống JdbcTemplate và NamedParameterJdbcTemplate.

~~~java
JdbcClient client = JdbcClient.create(dataSource);

Customer customer = client.sql(
        "select id, name from customer where id = :id")
    .param("id", id)
    .query(Customer.class)
    .single();
~~~

Dùng facade khi nó diễn đạt thao tác rõ ràng. Chuyển xuống template callback hoặc tiện ích chuyên biệt khi thao tác cần capability nằm ngoài phạm vi query/update phổ biến của JdbcClient.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdbc-parameter-binding">Binding tham số SQL theo vị trí và theo tên</a>

<details>
<summary>Xem chi tiết</summary>


Parameter binding tách cấu trúc SQL khỏi giá trị runtime. Cách này an toàn và dễ đọc hơn việc nối trực tiếp giá trị vào SQL string.

Với JDBC-style placeholder, tham số theo vị trí:

~~~java
jdbcClient.sql("select * from customer where status = ? and region = ?")
    .params("ACTIVE", "APAC")
    .query(Customer.class)
    .list();
~~~

Với Spring-style named parameter, tên tham số thể hiện vai trò của giá trị:

~~~java
jdbcClient.sql("""
    select * from customer
    where status = :status and region = :region
    """)
    .param("status", "ACTIVE")
    .param("region", "APAC")
    .query(Customer.class)
    .list();
~~~

NamedParameterJdbcTemplate và JdbcClient expand named parameter trước khi JDBC statement chạy. Đây không phải database protocol mới; driver cuối cùng vẫn nhận bind position tương thích JDBC.

Binding chỉ bảo vệ **giá trị**. Table name, column name, sort direction và SQL fragment thường không thể truyền như bind value; nếu cần dynamic identifier, hãy chọn từ tập giá trị ứng dụng kiểm soát thay vì nối input không tin cậy.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdbc-query-update-execution">Thực thi query, update và xử lý bằng callback</a>

<details>
<summary>Xem chi tiết</summary>


Spring JDBC phân biệt thao tác theo hình dạng kết quả thay vì nhét mọi thứ vào một API chung.

**Query** trả row để ứng dụng map hoặc extract. **Update** trả số row bị ảnh hưởng. Khi query/update thông thường không đủ, callback cho phép truy cập sâu hơn vào JDBC object nhưng vẫn giữ tài nguyên/exception quy trình của Spring.

Các callback quan trọng gồm PreparedStatementCreator, PreparedStatementSetter, ResultSetExtractor, RowCallbackHandler và PreparedStatementCallback.

~~~java
int updated = jdbcClient.sql(
        "update customer set status = :status where id = :id")
    .param("status", "SUSPENDED")
    .param("id", id)
    .update();
~~~

Ví dụ query:

~~~java
List<Customer> customers = jdbcTemplate.query(
    "select id, name from customer where active = ?",
    customerRowMapper,
    true);
~~~

Hãy chọn abstraction nhỏ nhất diễn đạt đúng thao tác. Callback là extension point cho hành vi JDBC ít phổ biến, không phải style mặc định cho đọc/ghi đơn giản.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdbc-row-mapping-cardinality">Row mapping và số lượng kết quả kỳ vọng</a>

<details>
<summary>Xem chi tiết</summary>


Query có hai quyết định độc lập: **mỗi row được chuyển thành giá trị nào** và **bên gọi kỳ vọng bao nhiêu giá trị**.

RowMapper phù hợp khi từng row độc lập tạo một result element. ResultSetExtractor phù hợp khi phải nhìn toàn ResultSet, ví dụ gom nhiều row thành một cấu trúc. RowCallbackHandler xử lý từng row theo side effect mà không cần tạo list trả về.

JdbcClient cũng có các cách ánh xạ tiện dụng theo target class cho record đơn giản hoặc object kiểu JavaBean, đồng thời vẫn cho phép dùng RowMapper tường minh khi cần kiểm soát chi tiết.

Cardinality phải là một phần của contract:

- list() nghĩa là 0..n row;
- optional() nghĩa là 0..1 row;
- single() nghĩa là chính xác 1 row.

Nếu single-result API nhận số row không đúng, Spring ném DataAccessException subtype như IncorrectResultSizeDataAccessException thay vì âm thầm lấy một row.

Không chọn single() chỉ vì test data hiện có một record. SQL predicate và constraint của database phải thực sự bảo đảm expectation đó.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdbc-client-vs-template">Chọn JdbcClient hay Template API</a>

<details>
<summary>Xem chi tiết</summary>


Ưu tiên JdbcClient cho query/update có bind parameter thông thường khi fluent API giúp SQL, binding, mapping và cardinality nằm gần nhau. Trong Spring 6.1, đây là facade thuận tiện cho những thao tác trước đây thường phải chuyển qua lại giữa JdbcTemplate và NamedParameterJdbcTemplate.

JdbcClient tự nó đã hỗ trợ RowMapper, ResultSetExtractor, RowCallbackHandler và các biến thể update lấy generated key, nên chỉ các nhu cầu đó chưa phải lý do để hạ xuống template. Ưu tiên JdbcTemplate hoặc NamedParameterJdbcTemplate khi cần batch API, quyền kiểm soát kiểu PreparedStatementCreator/PreparedStatementCallback, các thao tác execute, cấu hình riêng của template hoặc phải tích hợp với mã hiện có dựa trên các type đó. Dùng SimpleJdbcInsert/SimpleJdbcCall khi insert/procedure dựa trên metadata mới là abstraction phù hợp.

~~~text
query/update phổ biến
    → ưu tiên JdbcClient

batch / tạo statement / execute ở mức thấp
    → template API

insert/procedure dựa trên metadata
    → SimpleJdbcInsert / SimpleJdbcCall
~~~

Đây không phải lựa chọn "API mới thay API cũ". JdbcClient cố ý là facade trên hạ tầng template, nên cùng một codebase có thể dùng nhiều tầng nếu mỗi thao tác trở nên rõ ràng hơn.
</details>

- [Quay lại đầu trang](#back-to-top)
