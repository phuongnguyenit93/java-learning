<a id="back-to-top"></a>

# Vòng đời tài nguyên và tham gia transaction

## Menu
- [Lấy và giải phóng tài nguyên](#resource-acquisition-release)
- [DataSourceUtils và việc JDBC tham gia transaction](#datasource-utils-transaction-participation)
- [ConnectionFactoryUtils và việc R2DBC tham gia transaction](#connection-factory-utils-transaction-participation)
- [Proxy DataSource và ConnectionFactory có nhận biết transaction](#transaction-aware-resource-proxies)
- [Tài nguyên gắn với thread và subscriber context](#thread-vs-subscriber-context-resources)
- [Ranh giới với Spring Transaction Management](#transaction-management-boundary)

## <a id="resource-acquisition-release">Lấy và giải phóng tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>


Connection là tài nguyên có giới hạn. Data access đúng phải luôn ghép việc lấy tài nguyên với việc release, đồng thời vẫn cho phép một transaction tái sử dụng cùng một tài nguyên logic qua nhiều thao tác.

Spring client/template che phần thao tác cơ học này, nhưng mô hình tư duy vẫn quan trọng:

- ngoài transaction do Spring quản lý, thao tác lấy connection và release khi hoàn thành;
- trong transaction có synchronization, Spring có thể tái sử dụng tài nguyên gắn với transaction đó;
- ứng dụng không nên tự đóng tài nguyên mà Spring đang sở hữu cho transaction.

Các utility tồn tại cho code ở mức thấp cần tham gia cùng lifecycle. DataSourceUtils dành cho JDBC; ConnectionFactoryUtils dành cho R2DBC.

Quy tắc thực tế: **ưu tiên abstraction Spring cao nhất phù hợp**. Chỉ dùng tiện ích quản lý tài nguyên trực tiếp khi tích hợp mã ở tầng thấp; với JdbcTemplate/JdbcClient/DatabaseClient, hãy để framework quản lý việc lấy/giải phóng tài nguyên.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="datasource-utils-transaction-participation">DataSourceUtils và việc JDBC tham gia transaction</a>

<details>
<summary>Xem chi tiết</summary>


DataSourceUtils cung cấp các phương thức static để lấy JDBC Connection có nhận biết cơ chế synchronization của Spring transaction. getConnection(dataSource) trả Connection gắn với transaction hiện tại nếu có; nếu không, nó lấy Connection mới và có thể gắn Connection đó với thread khi transaction synchronization đang hoạt động.

Nhờ vậy mã JDBC tùy biến có thể tham gia cùng transaction với JdbcTemplate thay vì vô tình mở một Connection độc lập.

~~~java
Connection con = DataSourceUtils.getConnection(dataSource);
try {
    // custom JDBC thao tác
}
finally {
    DataSourceUtils.releaseConnection(con, dataSource);
}
~~~

Hãy dùng releaseConnection thay vì close() vô điều kiện. Tiện ích này biết Connection có đang gắn với transaction hay không và quyết định khi nào nên đóng kết nối vật lý.

Phần lớn mã ứng dụng không cần gọi trực tiếp vì JdbcTemplate và các đối tượng thao tác của Spring JDBC đã dùng DataSourceUtils bên dưới. Việc dùng trực tiếp thuộc ranh giới tích hợp ở tầng thấp.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="connection-factory-utils-transaction-participation">ConnectionFactoryUtils và việc R2DBC tham gia transaction</a>

<details>
<summary>Xem chi tiết</summary>


ConnectionFactoryUtils giữ vai trò tương ứng cho R2DBC. Nó lấy Connection từ ConnectionFactory, chuyển lỗi khi lấy kết nối thành DataAccessException và nhận biết Connection được gắn với cơ chế synchronization của transaction reactive.

Khác biệt quan trọng nằm ở **context**. Một chuỗi reactive có thể tiếp tục trên thread khác, vì vậy không thể mô hình tài nguyên transaction bằng ThreadLocal thông thường. Hạ tầng transaction reactive của Spring gắn trạng thái với subscriber/Reactor Context.

~~~java
Mono<Connection> connection =
    ConnectionFactoryUtils.getConnection(connectionFactory);
~~~

DatabaseClient đã dùng tiện ích này bên dưới, nên việc gọi trực tiếp thường chỉ cần khi mã R2DBC tùy biến cần Connection trực tiếp nhưng vẫn phải tham gia vòng đời reactive do Spring quản lý.

Không lấy Connection ra khỏi Publisher rồi cất vào trạng thái dùng chung theo kiểu imperative. Cách đó phá vòng đời tài nguyên và transaction context mà Spring đang quản lý.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-aware-resource-proxies">Proxy DataSource và ConnectionFactory có nhận biết transaction</a>

<details>
<summary>Xem chi tiết</summary>


TransactionAwareDataSourceProxy và TransactionAwareConnectionFactoryProxy là adapter dành cho **mã không biết về Spring transaction**.

TransactionAwareDataSourceProxy triển khai DataSource và đưa getConnection() qua cơ chế tra cứu tài nguyên có nhận biết Spring transaction. Nó hữu ích nhất khi mã cũ hoặc thư viện bên thứ ba bắt buộc nhận một DataSource chuẩn rồi tự gọi Connection.close().

Proxy R2DBC có vai trò tương tự cho mã làm việc trực tiếp với ConnectionFactory.

Đây không phải API ưu tiên cho mã Spring mới. JdbcTemplate, JdbcClient, DatabaseClient, DataSourceUtils và ConnectionFactoryUtils vốn đã hiểu tài nguyên do Spring quản lý.

~~~text
mã mới đã tích hợp với Spring
    → dùng trực tiếp client/tiện ích của Spring

mã cũ bắt buộc dùng DataSource/ConnectionFactory chuẩn
    → cân nhắc transaction-aware proxy
~~~

Hãy xem proxy như adapter tại ranh giới tích hợp. Thêm proxy khắp nơi chỉ làm quyền sở hữu của tài nguyên khó suy luận hơn.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-vs-subscriber-context-resources">Tài nguyên gắn với thread và subscriber context</a>

<details>
<summary>Xem chi tiết</summary>


Với JDBC imperative, việc tham gia transaction thường gắn với thread: cơ chế transaction synchronization theo kiểu imperative của Spring liên kết ConnectionHolder với thread hiện tại trong phạm vi transaction.

R2DBC theo mô hình reactive không thể dựa vào giả định đó. Một chuỗi reactive có thể chạy qua nhiều thread nhưng vẫn thuộc cùng một subscription logic. Vì vậy Spring dùng Reactor Context của subscriber để mang trạng thái transaction và tài nguyên liên quan.

~~~text
imperative JDBC
thread
    → transaction context
    → JDBC Connection

reactive R2DBC
subscriber chain
    → Reactor Context
    → R2DBC Connection
~~~

Điểm khác biệt này giải thích vì sao giả định dựa trên ThreadLocal không phù hợp với trạng thái của transaction reactive. Việc chuyển thread không quyết định một thao tác có tham gia transaction hay không: Spring gắn trạng thái transaction reactive với Reactor Context đi cùng subscription, không phải với thread tình cờ xử lý tín hiệu. Vì vậy, tự cất R2DBC Connection vào ThreadLocal sẽ đi ngược mô hình đó.

Chi tiết ngữ nghĩa transaction sâu hơn thuộc Transaction Management; phần này chỉ giải thích vì sao cơ chế tra cứu tài nguyên của JDBC và R2DBC khác nhau.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-management-boundary">Ranh giới với Spring Transaction Management</a>

<details>
<summary>Xem chi tiết</summary>


Data Access sở hữu câu hỏi **tài nguyên JDBC/R2DBC tham gia lifecycle của Spring như thế nào**. Transaction Management sở hữu câu hỏi **transaction bắt đầu/kết thúc khi nào và dùng chính sách nào**.

Vì vậy chương này dừng trước:

- @Transactional interception và ngữ nghĩa proxy;
- propagation như REQUIRED hoặc REQUIRES_NEW;
- isolation level;
- rollback rules và hành vi rollback-only;
- TransactionTemplate / TransactionalOperator;
- ngữ nghĩa transaction-bound event.

Data Access chỉ cần biết thao tác có thể dùng tài nguyên gắn với current transaction. Nó không tự quyết định chính sách transaction.

~~~text
Data Access
    → "thao tác này dùng tài nguyên gắn với transaction"

Transaction Management
    → "lời gọi này chạy trong transaction nào, chính sách nào"
~~~

Tách rõ hai quyền sở hữu giúp người học không nhầm tiện ích quản lý tài nguyên với transaction manager.
</details>

- [Quay lại đầu trang](#back-to-top)
