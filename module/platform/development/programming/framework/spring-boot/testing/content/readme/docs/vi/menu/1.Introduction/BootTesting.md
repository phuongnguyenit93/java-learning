<a id="back-to-top"></a>

# Bootstrap test và phát hiện cấu hình trong Spring Boot

## Menu
- [Spring Boot bổ sung gì cho kiểm thử có application context?](#boot-testing-role)
- [Vì sao cần bootstrap test theo mô hình của Boot?](#boot-testing-problem)
- [`spring-boot-test`, `spring-boot-test-autoconfigure` và `spring-boot-starter-test` liên hệ với nhau thế nào?](#boot-test-support-modules)
- [`@SpringBootTest` bootstrap qua `SpringApplication` như thế nào?](#springapplication-test-bootstrap)
- [Boot tìm `@SpringBootConfiguration` chính của ứng dụng như thế nào?](#primary-test-configuration-discovery)
- [Phạm vi kiểm thử của Boot bàn giao sang Spring TestContext và các thư viện test ở đâu?](#testing-ownership-boundary)
- [Bước lựa chọn tiếp theo khi kiểm thử với Boot là gì?](#testing-learning-path)

## <a id="boot-testing-role">Spring Boot bổ sung gì cho kiểm thử có application context?</a>

<details>
<summary>Xem chi tiết</summary>
Spring Framework đã cung cấp TestContext Framework để nạp và tái sử dụng application context trong test. Spring Boot bổ sung một tầng nhận biết Boot phía trên nền tảng đó: test có thể khởi động context qua `SpringApplication`, tìm cấu hình Boot chính, áp dụng external configuration và auto-configuration của Boot, đồng thời dùng các annotation tập trung cho từng phần của application.

Câu hỏi trọng tâm của module này vì thế là “khi test cần hạ tầng application thì Boot nên tham gia ở mức nào?”. Cơ chế thực thi của JUnit, thư viện assertion, ngữ nghĩa stubbing của Mockito và vòng đời chung của TestContext vẫn thuộc các module chuyên trách.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Testing](https://docs.spring.io/spring-boot/3.3/reference/testing/index.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boot-testing-problem">Vì sao cần bootstrap test theo mô hình của Boot?</a>

<details>
<summary>Xem chi tiết</summary>
Một Boot application production thường phụ thuộc nhiều hơn việc đăng ký bean thông thường. Nó có thể dựa vào externalized configuration, auto-configuration, environment detection, configuration-properties binding và conditional infrastructure. Nếu chỉ nạp một Spring context tổng quát, test có thể bỏ qua chính các hành vi Boot mà application production sử dụng.

Bootstrap theo mô hình Boot giúp test tái hiện phần luồng khởi động cần thiết mà không phải tự lắp ráp lại bằng tay. Mức độ sát thực tế vẫn phải theo mục tiêu: có test cần full Boot context, có test nên cố ý dùng slice nhỏ hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boot-test-support-modules">`spring-boot-test`, `spring-boot-test-autoconfigure` và `spring-boot-starter-test` liên hệ với nhau thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
`spring-boot-test` chứa hỗ trợ kiểm thử cốt lõi của Boot như `@SpringBootTest` và các tiện ích test riêng của Boot. `spring-boot-test-autoconfigure` chứa slice annotation cùng test auto-configuration cho context tập trung.

Phần lớn dự án dùng `spring-boot-starter-test` thay vì chọn từng module. Starter kéo vào cả hai module test của Boot cùng các thư viện thường dùng như JUnit Jupiter, AssertJ, Hamcrest và Mockito. Các thư viện này vẫn có phạm vi trách nhiệm riêng dù starter giúp dùng chúng cùng nhau thuận tiện.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="springapplication-test-bootstrap">`@SpringBootTest` bootstrap qua `SpringApplication` như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
`@SpringBootTest` tạo test `ApplicationContext` thông qua `SpringApplication` thay vì coi nó như Spring context thuần. Nhờ đó các tính năng Boot như external properties, logging configuration, environment processing và auto-configuration có thể tham gia quá trình khởi động test.

Điều này làm full-context test có độ sát thực tế cao ở tầng tích hợp Boot. Nó cũng có nghĩa lỗi trong quá trình khởi động test nên được phân tích như lỗi khởi động Boot: phát hiện cấu hình, condition, property và infrastructure đều có thể gặp vấn đề trước khi test method đầu tiên chạy.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Testing Spring Boot Applications](https://docs.spring.io/spring-boot/3.3/reference/testing/spring-boot-applications.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="primary-test-configuration-discovery">Boot tìm `@SpringBootConfiguration` chính của ứng dụng như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Khi Boot test annotation không chỉ định configuration class rõ ràng, Boot tìm ngược lên từ package chứa test cho tới khi thấy class có `@SpringBootApplication` hoặc `@SpringBootConfiguration`. Với cấu trúc package theo quy ước, cấu hình chính của application thường được tìm thấy tự động.

Vì vậy vị trí package của test ảnh hưởng bootstrap. Test nằm ngoài hệ phân cấp package của application có thể không tìm thấy cấu hình chính nếu không truyền configuration class tường minh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-ownership-boundary">Phạm vi kiểm thử của Boot bàn giao sang Spring TestContext và các thư viện test ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>
Boot testing sở hữu bootstrap context riêng của Boot, lựa chọn slice, test auto-configuration, các annotation test của Boot, tích hợp thay bean bằng mock/spy và hỗ trợ service connection. Bên dưới, Spring TestContext sở hữu vòng đời context, cache, test-managed transaction, test property source và cơ chế tích hợp thực thi test chung.

JUnit sở hữu cơ chế phát hiện và thực thi test. Mockito sở hữu hành vi mock và việc xác minh lời gọi. AssertJ/Hamcrest sở hữu API assertion. Testcontainers sở hữu định nghĩa container và vòng đời của container do JUnit/Testcontainers quản lý, cùng tương tác với Docker, image, network, wait strategy và các cơ chế container tổng quát. Khi container được khai báo như một Spring bean, vòng đời của nó đi theo Spring application context. Giữ ranh giới này rõ giúp không nhầm annotation tiện ích của Boot với phần triển khai của công cụ bên dưới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-learning-path">Bước lựa chọn tiếp theo khi kiểm thử với Boot là gì?</a>

<details>
<summary>Xem chi tiết</summary>
Hãy bắt đầu bằng việc xác định **ranh giới mà test phải chứng minh**. Dùng full `@SpringBootTest` khi tích hợp trên toàn application Boot là điều cần kiểm tra. Chọn `WebEnvironment` phù hợp khi cần độ sát thực tế của HTTP server. Dùng slice tập trung khi chỉ web hoặc data layer cần được kiểm thử.

Sau khi chốt phạm vi context, chỉ thêm tùy biến mà kịch bản thực sự cần: test auto-configuration, property cục bộ, cấu hình chỉ dành cho test, thay thế mock/spy hoặc service connection tới dependency thật. Chương cuối sẽ kết nối các lựa chọn này thành một chiến lược nhất quán.

</details>

- [Quay lại đầu trang](#back-to-top)
