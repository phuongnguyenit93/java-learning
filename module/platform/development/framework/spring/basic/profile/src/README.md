1️⃣ Vì sao @TestFactory không dùng được với @SpringBootTest?
@TestFactory tạo Dynamic Tests.

Dynamic Test không chạy theo vòng đời thông thường của JUnit 5 (@BeforeAll, @BeforeEach, @AfterEach, @AfterAll không được áp dụng).

Spring Boot Test (@SpringBootTest) phụ thuộc vào cơ chế test instance lifecycle để khởi động ApplicationContext trước khi chạy test.

Với @TestFactory, JUnit chỉ gọi method để lấy danh sách test, chứ không inject Spring context hoặc autowire bean vào instance này trước.

Kết quả: Bean không được inject (null), context chưa load → lỗi.

2️⃣ Vì sao @ParameterizedTest thường không dùng chung với @SpringBootTest?
@ParameterizedTest tạo ra nhiều instance của test method (mỗi bộ dữ liệu chạy một lần).

Mỗi instance sẽ chạy độc lập và không chia sẻ ApplicationContext giống như @SpringBootTest mong muốn.

Điều này có thể gây:

Load context nhiều lần (rất chậm)

Hoặc JUnit bỏ qua injection vì lifecycle không khớp.

Ngoài ra, JUnit 5 không cho phép kết hợp @ParameterizedTest với @Test hoặc @TestFactory trên cùng method, nên việc tích hợp Spring context phức tạp hơn.

