<a id="back-to-top"></a>

# Kiểm thử transaction và database

## Menu
- [Test-managed transaction](#test-managed-transactions)
- [Resolve transaction manager cho test](#transaction-manager-resolution)
- [Semantics của transactional test và các attribute được hỗ trợ](#transactional-test-semantics)
- [Default rollback, commit và rollback override](#rollback-and-commit)
- [Điều khiển transaction bằng TestTransaction](#testtransaction-programmatic-control)
- [BeforeTransaction và AfterTransaction callback](#transaction-lifecycle-callbacks)
- [Chuẩn bị database fixture bằng @Sql](#sql-test-fixtures)
- [JDBC testing utility](#jdbc-test-utilities)
- [Pitfall và false positive trong transactional testing](#transaction-testing-pitfalls)

## <a id="test-managed-transactions">Test-managed transaction</a>

<details>
<summary>Xem chi tiết</summary>

TestContext framework có thể bọc một test method trong **test-managed transaction**. `TransactionalTestExecutionListener`, thường được bật sẵn trong bộ listener mặc định, phát hiện `@Transactional` của Spring trên test class hoặc test method, bắt đầu transaction trước khi test method chạy và kết thúc transaction sau khi test hoàn tất. Hành vi mặc định khi kết thúc là rollback.

Transaction này thuộc hạ tầng kiểm thử. Nó khác với Spring-managed transaction do mã ứng dụng mở và cũng khác transaction mà ứng dụng tự quản lý bằng API. Mã được kiểm thử gọi vào vẫn có thể tham gia test-managed transaction nếu propagation bình thường của nó cho phép, vì transaction được gắn với test thread thông qua hạ tầng transaction của Spring.

Phân biệt này giúp đọc test đúng hơn. Một repository method có `@Transactional` đang thực thi semantics transaction của ứng dụng do module transaction-management sở hữu. Module testing chỉ sở hữu ranh giới transaction bên ngoài của test, chính sách rollback/commit của test và các utility để test tương tác với ranh giới đó.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 API — TransactionalTestExecutionListener](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/transaction/TransactionalTestExecutionListener.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-manager-resolution">Resolve transaction manager cho test</a>

<details>
<summary>Xem chi tiết</summary>

Transactional test cần một `PlatformTransactionManager` từ `ApplicationContext` của test. Khi `@Transactional` chỉ định manager qua `value` hoặc `transactionManager`, listener dùng qualifier/tên đó. Đây là lựa chọn rõ ràng nhất khi context có nhiều transaction manager phục vụ các tài nguyên khác nhau.

Nếu không chỉ định qualifier, Spring dùng quy ước của TestContext để tìm transaction manager. Framework có thể lấy manager do `TransactionManagementConfigurer` chọn, tìm một bean duy nhất theo type, ưu tiên primary candidate, rồi mới fallback về conventional bean name `transactionManager`. Nếu context thật sự có nhiều lựa chọn mơ hồ, nên cấu hình rõ ràng thay vì phụ thuộc vào thứ tự bean một cách ngẫu nhiên.

Test transaction phải dùng manager phối hợp đúng tài nguyên mà test tác động. Việc chọn manager là mối quan tâm của hạ tầng kiểm thử trong chương này; propagation, isolation, synchronization và hành vi sâu hơn của manager thuộc module transaction-management.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 API — TestContextTransactionUtils](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/transaction/TestContextTransactionUtils.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transactional-test-semantics">Semantics của transactional test và các attribute được hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

`@Transactional` trên test không mang toàn bộ contract của transaction trong ứng dụng vào test-managed transaction. `value`/`transactionManager` được hỗ trợ để chọn manager. Hai giá trị propagation có ý nghĩa đặc biệt được hỗ trợ là `NOT_SUPPORTED` và `NEVER`; chúng làm test chạy mà không có test-managed transaction.

Các attribute như `isolation`, `timeout`, `readOnly`, `rollbackFor`, `rollbackForClassName`, `noRollbackFor` và `noRollbackForClassName` không được dùng để cấu hình test-managed transaction. Với quyết định commit/rollback của test, dùng `@Rollback`, `@Commit` hoặc `TestTransaction`. Nếu mã ứng dụng tự khai báo các attribute đó thì đó là chính sách transaction riêng của ứng dụng.

Lifecycle callback ở mức method như JUnit Jupiter `@BeforeEach` và `@AfterEach` chạy bên trong test-managed transaction. Callback cấp class/suite như `@BeforeAll` và `@AfterAll` không nằm trong transaction này. Vì vậy `@Transactional` dành cho test class/test method, không phải để gắn semantics transaction cho các lifecycle method đó.

### Tài liệu tham khảo

- [Spring Framework 6.1 Reference — Transaction Management in the TestContext Framework](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rollback-and-commit">Default rollback, commit và rollback override</a>

<details>
<summary>Xem chi tiết</summary>

Khi TestContext framework bắt đầu transaction cho test, transaction đó mặc định được đánh dấu rollback. Đây là đặc tính cô lập rất hữu ích cho database integration test: dữ liệu do test ghi thường bị loại bỏ khi test-managed transaction kết thúc.

`@Rollback(false)` đổi chính sách sang commit, còn `@Commit` là cách diễn đạt rõ ý định commit. Có thể đặt chính sách mặc định ở class rồi override hẹp hơn tại method. Commit cần được thể hiện có chủ đích vì trạng thái bền vững được tạo ra có thể ảnh hưởng test chạy sau.

Rollback mặc định không có nghĩa mọi side effect đều được cô lập. Công việc chạy ở thread khác, tiến trình khác hoặc isolated transaction có thể commit độc lập với test-managed transaction. Vì vậy assertion phải bám đúng ranh giới transaction thực tế thay vì giả định annotation sẽ tự dọn tất cả tài nguyên mà kịch bản đã chạm tới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testtransaction-programmatic-control">Điều khiển transaction bằng TestTransaction</a>

<details>
<summary>Xem chi tiết</summary>

`TestTransaction` cho test điều khiển trực tiếp **test-managed transaction** hiện tại mà không thay thế transaction API của application. `isActive()` cho biết test transaction có đang tồn tại hay không. `flagForRollback()` và `flagForCommit()` đổi cách transaction hiện tại sẽ kết thúc.

`end()` kết thúc transaction đang active ngay tại vị trí gọi theo rollback/commit flag hiện tại. Cơ chế này hữu ích khi test cần chứng minh hành vi sau một lần commit hoặc rollback thật trước khi test method kết thúc. Sau đó `start()` có thể mở một test-managed transaction mới nếu test đã được cấu hình transactional và hiện không còn transaction active.

Ví dụ, test có thể ghi dữ liệu, gọi `flagForCommit()`, `end()`, kiểm tra hành vi chỉ xuất hiện sau commit, rồi `start()` một transaction mới để tiếp tục assertion hoặc dọn dẹp. Nên giữ luồng này tập trung vì việc điều phối transaction quá phức tạp trong test dễ che mất hành vi ứng dụng cần kiểm chứng.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 API — TestTransaction](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/transaction/TestTransaction.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-lifecycle-callbacks">BeforeTransaction và AfterTransaction callback</a>

<details>
<summary>Xem chi tiết</summary>

`@BeforeTransaction` và `@AfterTransaction` là callback nằm quanh **test-managed transaction boundary**. Với transactional test, method có `@BeforeTransaction` chạy trước khi Spring mở test transaction; method có `@AfterTransaction` chạy sau khi Spring đã kết thúc transaction đó. Vì thế đây là vị trí phù hợp cho bước chuẩn bị hoặc assertion cần quan sát trạng thái database bên ngoài transaction sẽ rollback cùng test.

Điểm này khác lifecycle method thông thường. JUnit Jupiter `@BeforeEach` và `@AfterEach` nằm bên trong test-managed transaction, còn `@BeforeTransaction` và `@AfterTransaction` cố ý nằm bên ngoài. Nếu test không được cấu hình transactional thì các transaction callback này không được gọi cho test đó.

Từ Spring Framework 6.1, khi dùng `SpringExtension` với JUnit Jupiter, callback có thể nhận parameter do các JUnit `ParameterResolver` đã đăng ký resolve, bao gồm parameter quen thuộc của Jupiter và dependency do Spring resolve. Với inheritance, `@BeforeTransaction` ở superclass/interface chạy trước callback của class hiện tại; `@AfterTransaction` chạy ngược lại khi kết thúc.

Các từ khóa cần ghi nhớ là `@BeforeTransaction`, `@AfterTransaction`, ranh giới transactional test và hành vi rollback/commit cuối cùng. Hãy dùng chúng khi assertion thật sự cần đi qua ranh giới đó.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 API — BeforeTransaction](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/transaction/BeforeTransaction.html)
- [Spring Framework 6.1.14 API — AfterTransaction](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/transaction/AfterTransaction.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sql-test-fixtures">Chuẩn bị database fixture bằng @Sql</a>

<details>
<summary>Xem chi tiết</summary>

`@Sql` cho TestContext-based integration test chạy SQL script hoặc inline statement như một phần của bước chuẩn bị/dọn dẹp. `SqlScriptsTestExecutionListener`, có trong bộ listener mặc định, thực thi các khai báo này. Mặc định script gắn với test method chạy trước method; `executionPhase` cũng có thể chuyển chúng sang sau method.

Khai báo `@Sql` ở cấp class và cấp method có thể kết hợp có chủ đích. Với các giai đoạn chạy quanh từng test method thông thường, `@Sql` ở method mặc định ghi đè khai báo ở class; `@SqlMergeMode` cho phép yêu cầu gộp. `@SqlConfig` điều khiển cách phân tích script, chọn data source/transaction manager, xử lý lỗi và transaction mode. Với `INFERRED`, script có thể tham gia transaction hiện có; với `ISOLATED`, script chạy trong transaction mới và được commit ngay.

Spring Framework 6.1 bổ sung hai giai đoạn cấp class là `BEFORE_TEST_CLASS` và `AFTER_TEST_CLASS`. Các khai báo ở hai giai đoạn này luôn chạy bổ sung cho `@Sql` ở method và không thể bị khai báo method ghi đè. `BEFORE_TEST_CLASS` chạy trước callback lifecycle cấp class của test framework như JUnit Jupiter `@BeforeAll` và sẽ làm test `ApplicationContext` được tải sớm khi cần; `AFTER_TEST_CLASS` chạy sau callback như `@AfterAll`. Chúng hữu ích cho việc chuẩn bị hoặc dọn schema tốn kém chỉ cần chạy một lần mỗi class, nhưng trạng thái được tạo ở đó nằm ngoài chu kỳ rollback mặc định của từng test method.

Nếu không chỉ định script, Spring hỗ trợ conventional default script detection theo tên test class hoặc test method. Trong suite lớn, đường dẫn được khai báo rõ thường dễ rà soát hơn vì dependency vào fixture hiện rõ ngay tại annotation.

### Tài liệu tham khảo

- [Spring Framework 6.1 Reference — Executing SQL Scripts](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdbc-test-utilities">JDBC testing utility</a>

<details>
<summary>Xem chi tiết</summary>

`JdbcTestUtils` cung cấp các helper nhỏ cho assertion và việc dọn dẹp database thường gặp. Nó có thể đếm toàn bộ row trong table, đếm row theo `WHERE`, xóa row ở một hay nhiều table, xóa theo điều kiện và drop table. Các thao tác làm việc với Spring JDBC access object để test diễn đạt ý định mà không lặp lại code đếm/xóa cấp thấp.

Các transactional support base class dành cho JUnit 4 và TestNG có convenience method delegate vào utility này. Test mới không cần kế thừa các base class chỉ để dùng helper; gọi `JdbcTestUtils` trực tiếp thường làm dependency rõ ràng hơn.

Utility này thuộc chương testing vì mục tiêu là chuẩn bị và kiểm tra trạng thái database. Cơ chế của `JdbcClient`, `JdbcTemplate`, SQL exception translation và Spring data access nói chung thuộc module data-access. Chương này chỉ dùng chúng ở mức cần thiết để chuẩn bị hoặc assert fixture.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 API — JdbcTestUtils](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/jdbc/JdbcTestUtils.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-testing-pitfalls">Pitfall và false positive trong transactional testing</a>

<details>
<summary>Xem chi tiết</summary>

Rollback mặc định rất tiện, nhưng test vẫn có thể cho kết quả dương tính giả nếu thao tác persistence chưa thật sự chạm database. Với ORM, thay đổi có thể còn nằm trong persistence context. Test chỉ assert trạng thái trong memory có thể pass dù lệnh flush lúc commit sẽ vi phạm constraint. Khi kịch bản phụ thuộc database constraint hoặc SQL sinh ra, cần chủ động flush bằng persistence technology tương ứng trước assertion quyết định.

Ranh giới thread là một bẫy quan trọng khác. Test-managed transaction được gắn với test thread. Preemptive timeout chạy test body trên thread khác có thể khiến thao tác database thoát khỏi transaction và commit, trong khi Spring sau đó rollback transaction ở thread gốc. Request gửi tới live server cũng được xử lý ở server thread nên không tự nằm trong test-managed transaction của bên gọi.

Cũng cần để ý fixture transaction mode. `@Sql(transactionMode = ISOLATED)` commit script độc lập; `TestTransaction.end()` có thể commit khi đã được flag; mã ứng dụng cũng có thể chủ động mở transaction độc lập. Nếu các test chạy song song cùng sửa những database row giống nhau thì hiện tượng can nhiễu càng dễ xảy ra.

Một transactional test đáng tin cậy luôn trả lời được hai câu: thread/resource manager nào sở hữu từng thao tác ghi, và transaction nào thật sự kết thúc thao tác ghi đó? Rollback mặc định chỉ bảo vệ phần công việc thực sự tham gia test-managed transaction.

### Tài liệu tham khảo

- [Spring Framework 6.1 Reference — Transaction Management in Tests](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Quay lại đầu trang](#back-to-top)
