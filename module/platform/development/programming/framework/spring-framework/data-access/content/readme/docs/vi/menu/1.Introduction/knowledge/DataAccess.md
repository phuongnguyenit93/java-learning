<a id="back-to-top"></a>

# Spring Framework Data Access

## Menu
- [Spring Data Access là gì và vì sao tồn tại?](#data-access-purpose)
- [Điều gì trở nên lặp lại khi không có Spring Data Access?](#data-access-problem-without-spring)
- [Trách nhiệm và ranh giới của Spring Framework Data Access](#data-access-responsibility-boundary)
- [Hai hướng truy cập: JDBC blocking và R2DBC reactive](#jdbc-vs-r2dbc-paths)

## <a id="data-access-purpose">Spring Data Access là gì và vì sao tồn tại?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Data Access là lớp hạ tầng của Spring Framework giúp ứng dụng làm việc với công nghệ lưu trữ dữ liệu mà không phải lặp lại toàn bộ việc quản lý tài nguyên và xử lý lỗi đặc thù của từng driver. Trong module này, hai hướng chính là Spring JDBC và Spring R2DBC.

Mô hình tư duy quan trọng là **ý định của ứng dụng ở phía trên, quy trình tài nguyên ở phía dưới**. Mã ứng dụng nên tập trung vào SQL, giá trị tham số và cách biến dữ liệu trả về thành giá trị hữu ích. Spring chịu trách nhiệm cho phần lặp lại như lấy connection, gọi API của driver, chuyển đổi lỗi và giải phóng tài nguyên đúng cách.

Tầng này thấp hơn Spring Data repository hay ORM mapping. Nó không quyết định ranh giới aggregate, vòng đời entity hay derived query. Nó phù hợp khi ứng dụng muốn giữ SQL minh bạch và vẫn cần một quy trình Java nhất quán.

~~~text
nghiệp vụ
    ↓
DAO / component truy cập dữ liệu
    ↓
JdbcClient / JdbcTemplate / DatabaseClient
    ↓
JDBC DataSource hoặc R2DBC ConnectionFactory
    ↓
driver và database
~~~

Giá trị chính của Spring Data Access là giảm phần mã hạ tầng lặp lại ngoài ý muốn mà không che mất hành vi thật của database.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="data-access-problem-without-spring">Điều gì trở nên lặp lại khi không có Spring Data Access?</a>

<details>
<summary>Xem chi tiết</summary>

API database thuần hoàn toàn dùng được, nhưng mỗi thao tác đều kéo theo nhiều bước cơ học. Với JDBC, mã phải lấy Connection, tạo và bind statement, thực thi, đọc ResultSet, xử lý SQLException và đóng tài nguyên đúng thứ tự. R2DBC bỏ mô hình I/O blocking khỏi API, nhưng vẫn cần quản lý connection, statement, result và lỗi cẩn thận.

Nếu mỗi DAO tự làm toàn bộ quy trình này, một số vấn đề thường xuất hiện:

- code dọn dẹp tài nguyên che khuất SQL và logic mapping;
- quên đóng connection có thể làm cạn pool;
- mỗi DAO tự phân loại lỗi vendor theo cách khác nhau;
- code dễ vô tình lấy connection ngoài transaction đang hoạt động;
- test phải quan tâm quá nhiều đến mã hạ tầng lặp lại thay vì quyết định data access.

Spring không làm mất các giới hạn của database. SQL vẫn có thể chậm, unique constraint vẫn có thể vi phạm, lock vẫn có thể timeout và network vẫn có thể lỗi. Spring chỉ làm cho **hợp đồng phía Java quanh các tình huống đó nhất quán hơn**.

Mục tiêu không phải là "ít SQL hơn", mà là ít mã quản lý tài nguyên và exception không cần thiết quanh SQL có chủ đích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="data-access-responsibility-boundary">Trách nhiệm và ranh giới của Spring Framework Data Access</a>

<details>
<summary>Xem chi tiết</summary>

Module này sở hữu các cơ chế Spring Framework nằm trực tiếp trên JDBC và R2DBC: hệ exception org.springframework.dao, Spring JDBC template và JdbcClient, Spring R2DBC DatabaseClient, các utility quản lý connection/tài nguyên và ranh giới để tài nguyên đó tham gia Spring-managed transaction.

Các phạm vi lân cận có module sở hữu khác:

- **JDBC thuần** sở hữu ngữ nghĩa của Connection, PreparedStatement, transaction method và driver.
- **Spring Transaction Management** sở hữu transaction demarcation, propagation, isolation, rollback rule và chính sách transaction manager.
- **Spring Data JDBC/R2DBC** sở hữu repository, aggregate mapping và query abstraction.
- **JPA/Hibernate** sở hữu entity mapping, persistence context, dirty checking và ORM lifecycle.
- **Reactive Programming** sở hữu Reactive Streams, backpressure và nền tảng Reactor.

Ranh giới này giúp tránh nhầm một client API với cả kiến trúc persistence. JdbcClient có thể map row sang object nhưng không vì thế mà trở thành aggregate mapper. DatabaseClient trả Publisher nhưng module này không sở hữu toàn bộ ngữ nghĩa Reactor.

Khi một khái niệm chỉ dùng để so sánh hoặc chuyển tiếp, Knowledge chỉ giải thích đủ để người học hiểu ranh giới rồi chuyển phần sâu hơn sang module sở hữu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdbc-vs-r2dbc-paths">Hai hướng truy cập: JDBC blocking và R2DBC reactive</a>

<details>
<summary>Xem chi tiết</summary>

Spring có hai hướng relational access với mô hình thực thi khác nhau.

**Spring JDBC** xây trên JDBC blocking. Trong lúc driver chờ I/O, thread gọi thường bị giữ lại. Tài nguyên trung tâm là JDBC Connection lấy từ DataSource; trong transaction imperative, tài nguyên thường được gắn với context của thread đang thực thi.

**Spring R2DBC** xây trên R2DBC SPI non-blocking. ConnectionFactory cung cấp reactive connection và DatabaseClient biểu diễn thao tác dưới dạng Publisher. Công việc diễn ra khi có subscription; context của transaction/tài nguyên được truyền qua Reactor context thay vì dựa vào thread affinity thông thường.

Hai stack cùng giải quyết các bài toán như parameterized SQL, mapping, dọn dẹp tài nguyên và DataAccessException, nhưng không phải wrapper thay thế trực tiếp cho nhau.

~~~text
JDBC                            R2DBC
DataSource                      ConnectionFactory
Connection                      io.r2dbc.spi.Connection
JdbcClient / JdbcTemplate       DatabaseClient
chuỗi gọi blocking              thực thi theo Publisher
context theo thread             context theo subscriber/Reactor
~~~

Hãy chọn theo mô hình thực thi end-to-end. Bọc một lời gọi JDBC blocking trong Mono không biến driver thành non-blocking I/O.

</details>

- [Quay lại đầu trang](#back-to-top)
