<a id="back-to-top"></a>

# Spring R2DBC Core và DatabaseClient

## Menu
- [ConnectionFactory và DatabaseClient](#r2dbc-connection-factory-database-client)
- [Mô hình thực thi SQL reactive](#r2dbc-reactive-sql-execution)
- [Named parameter và bind marker của database](#r2dbc-parameter-binding-bind-markers)
- [Ánh xạ kết quả, số lượng kết quả và ràng buộc null](#r2dbc-result-mapping-cardinality-null)
- [Giá trị sinh tự động và Statement Filter](#r2dbc-generated-values-statement-filters)

## <a id="r2dbc-connection-factory-database-client">ConnectionFactory và DatabaseClient</a>

<details>
<summary>Xem chi tiết</summary>


R2DBC định nghĩa SPI non-blocking cho truy cập relational database. Entry point tài nguyên là ConnectionFactory; vai trò gần giống DataSource của JDBC nhưng mô hình thực thi hoàn toàn khác.

Trong Spring Framework, org.springframework.r2dbc.core.DatabaseClient là client trung tâm của R2DBC core. Nó quản lý việc lấy/trả tài nguyên, thực thi statement, mapping result và chuyển lỗi R2DBC thành DataAccessException. Quyền sở hữu này thuộc **Spring Framework**, không thuộc Spring Data R2DBC.

~~~java
DatabaseClient client = DatabaseClient.create(connectionFactory);

Mono<Customer> customer = client.sql(
        "select id, name from customer where id = :id")
    .bind("id", id)
    .map((row, metadata) -> new Customer(
        row.get("id", Long.class),
        row.get("name", String.class)))
    .one();
~~~

Spring Data R2DBC xây mapping/repository abstraction ở tầng cao hơn. Dùng DatabaseClient khi explicit SQL và framework-level reactive access là mục tiêu; chỉ chuyển lên repository/entity mapping khi bài toán thật sự cần.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="r2dbc-reactive-sql-execution">Mô hình thực thi SQL reactive</a>

<details>
<summary>Xem chi tiết</summary>


Một lời gọi DatabaseClient xây dựng pipeline reactive; database I/O không diễn ra ngay lúc chuỗi fluent được tạo. Việc thực thi bắt đầu khi có subscription vào Publisher.

Điều này ảnh hưởng trực tiếp tới luồng điều khiển và vòng đời tài nguyên:

~~~text
xây SQL specification
    ↓
trả Mono/Flux
    ↓
subscription
    ↓
lấy Connection
    ↓
tạo/bind/execute Statement
    ↓
consume Result publisher
    ↓
giải phóng tài nguyên
~~~

Xử lý lỗi, hủy luồng và transaction context đều phải nằm trong cùng chuỗi reactive. DatabaseClient an toàn khi dùng đồng thời sau khi cấu hình và có thể được dùng chung; trạng thái riêng của từng thao tác nằm trong đặc tả thực thi chứ không nằm trong client dùng chung.

Không gọi block() chỉ để dùng DatabaseClient theo kiểu imperative. Việc blocking ở một ranh giới có chủ đích có thể chấp nhận được trong ứng dụng không reactive, nhưng block() bên trong luồng xử lý request reactive sẽ phá mô hình non-blocking và có thể làm cạn thread xử lý.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="r2dbc-parameter-binding-bind-markers">Named parameter và bind marker của database</a>

<details>
<summary>Xem chi tiết</summary>


R2DBC driver dùng cú pháp bind marker phụ thuộc database. PostgreSQL có thể dùng marker đánh số như $1, còn driver khác có thể dùng ? hoặc dạng native khác. DatabaseClient cho phép SQL phía ứng dụng dùng named parameter theo kiểu Spring rồi chuyển chúng thành bind marker phù hợp với ConnectionFactory.

Named-parameter expansion được bật mặc định. DatabaseClient.Builder có thể nhận BindMarkersFactory riêng hoặc tắt named parameters.

~~~java
client.sql("""
        select * from customer
        where status = :status and region in (:regions)
        """)
    .bind("status", "ACTIVE")
    // Spring mở rộng collection thành số bind marker tương ứng
    .bind("regions", List.of("EU", "APAC"));
~~~

~~~text
SQL phía ứng dụng dùng tên tham số
        ↓
Spring mở rộng / phân giải tên
        ↓
driver nhận native bind marker
~~~

Điểm cần nhớ: đây là **quá trình chuyển đổi marker của tham số**, không phải database tự hiểu :name.

Binding bảo vệ value, không bảo vệ SQL identifier. Table name, column name hoặc SQL fragment động phải được chọn từ tập giá trị do ứng dụng kiểm soát; không nối trực tiếp input không tin cậy.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="r2dbc-result-mapping-cardinality-null">Ánh xạ kết quả, số lượng kết quả và ràng buộc null</a>

<details>
<summary>Xem chi tiết</summary>


R2DBC map row theo reactive flow, nhưng bên gọi vẫn phải xác định rõ cardinality contract. RowsFetchSpec của DatabaseClient có các cách resolve như one(), first() và all().

Dùng one() khi contract là 0..1 và duplicate row phải bị xem là lỗi. Dùng all() cho luồng nhiều row. first() cố ý lấy row đầu tiên, vì vậy không nên dùng nó để che một query lẽ ra phải unique.

Reactive Streams không cho phép phần tử null. Mapping function phải trả một object non-null cho mỗi phần tử được emit. SQL NULL cần được biểu diễn **bên trong object đã map**—ví dụ field nullable hoặc domain representation rõ ràng—chứ không phải emit null từ Publisher.

~~~java
Flux<Customer> customers = client.sql(sql)
    .map((row, metadata) -> new Customer(
        row.get("id", Long.class),
        row.get("name", String.class)))
    .all();
~~~

Khi hành vi chuyển kiểu của driver không rõ ràng, hãy giữ việc chuyển kiểu SQL ở dạng tường minh. Row mapper là ranh giới giữa giá trị của driver và ứng dụng; không nên tự gán thêm ngữ nghĩa persistence cấp cao nếu trách nhiệm đó thuộc tầng mapping khác.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="r2dbc-generated-values-statement-filters">Giá trị sinh tự động và Statement Filter</a>

<details>
<summary>Xem chi tiết</summary>


Một số thao tác cần tùy chọn ở tầng Statement mà fluent API cơ bản không biểu diễn. DatabaseClient cung cấp StatementFilterFunction để tùy biến R2DBC Statement hoặc bao quanh bước thực thi mà vẫn giữ quy trình quản lý tài nguyên và lỗi của client.

Ví dụ thường gặp là yêu cầu trả về giá trị được database sinh tự động:

~~~java
Mono<Long> id = client.sql(
        "insert into customer(name) values (:name)")
    .bind("name", "Ada")
    .filter(statement -> statement.returnGeneratedValues("id"))
    .map((row, metadata) -> row.get("id", Long.class))
    .one();
~~~

Statement filter cũng có thể cấu hình tùy chọn như fetch size nếu driver hỗ trợ. Đây là điểm mở rộng ở mức thấp; chỉ dùng khi DatabaseClient API thông thường không diễn đạt đủ nhu cầu.

Hành vi trả giá trị sinh tự động vẫn phụ thuộc database và driver. Tên cột, dạng SQL được hỗ trợ và kiểu dữ liệu trả về có thể khác. Cần kiểm thử với R2DBC driver thật thay vì giả định hành vi giống generated key của JDBC.
</details>

- [Quay lại đầu trang](#back-to-top)
