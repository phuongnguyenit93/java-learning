<a id="back-to-top"></a>

# Service connection của Testcontainers và `ConnectionDetails`

## Menu
- [Vì sao Boot cung cấp service connection cho Testcontainers?](#service-connection-purpose)
- [Test dependency `spring-boot-testcontainers` kích hoạt khả năng gì?](#spring-boot-testcontainers-dependency)
- [Vì sao `ConnectionDetails` từ service connection ưu tiên hơn connection properties?](#connection-details-precedence)
- [`@ServiceConnection` hoạt động trên field container do Testcontainers quản lý như thế nào?](#service-connection-container-field)
- [Container bean trong `@TestConfiguration` tham gia service connection như thế nào?](#service-connection-container-bean)
- [Khi nào `GenericContainer` cần tên service connection được chỉ định rõ?](#service-connection-name-hints)
- [Khi nào `@DynamicPropertySource` là phương án thay thế phù hợp hơn?](#dynamic-property-source-fallback)
- [Tích hợp của Boot kết thúc ở đâu và trách nhiệm Testcontainers tổng quát bắt đầu ở đâu?](#testcontainers-ownership-boundary)

## <a id="service-connection-purpose">Vì sao Boot cung cấp service connection cho Testcontainers?</a>

<details>
<summary>Xem chi tiết</summary>
Testcontainers có thể khởi động một service thật, nhưng application vẫn cần thông tin kết nối như host, port, thông tin xác thực hoặc URL. Boot service connection nối khoảng trống đó bằng cách tạo `ConnectionDetails` có kiểu phù hợp từ container được hỗ trợ rồi cung cấp cho application auto-configuration.

Kết quả là integration test gọn hơn: field container được quản lý qua tích hợp JUnit của Testcontainers vẫn dùng vòng đời do Testcontainers quản lý, còn container được khai báo dưới dạng Spring `@Bean` đi theo vòng đời Spring application context. Trong cả hai trường hợp, Boot cấu hình client infrastructure thông thường từ service connection thay vì mỗi test tự sao chép các giá trị động của container vào properties.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Testcontainers](https://docs.spring.io/spring-boot/3.3/reference/testing/testcontainers.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-boot-testcontainers-dependency">Test dependency `spring-boot-testcontainers` kích hoạt khả năng gì?</a>

<details>
<summary>Xem chi tiết</summary>
Phần tích hợp Testcontainers của Boot nằm trong module `spring-boot-testcontainers`. Thêm module này ở test scope sẽ cung cấp `@ServiceConnection` cùng các connection-details factory có thể nhận diện loại Testcontainers được hỗ trợ hoặc container image.

Dependency này không thay thế thư viện Testcontainers hay phần tích hợp JUnit của nó. Nó bổ sung adapter riêng của Boot để biến thông tin container thành connection details mà Boot auto-configuration có thể sử dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="connection-details-precedence">Vì sao `ConnectionDetails` từ service connection ưu tiên hơn connection properties?</a>

<details>
<summary>Xem chi tiết</summary>
Khi Boot auto-configuration nhận một `ConnectionDetails` bean phù hợp, connection details đó có độ ưu tiên cao hơn các configuration property liên quan tới kết nối. Nhờ vậy test container trở thành endpoint có thẩm quyền cho test mà không phải viết lại tập property application thông thường.

Ưu tiên này có chủ ý vì service connection đại diện một dependency đang chạy thật với địa chỉ thường thay đổi. Các application property khác không liên quan vẫn theo mô hình externalized configuration thông thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="service-connection-container-field">`@ServiceConnection` hoạt động trên field container do Testcontainers quản lý như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Mẫu thường gặp là field do Testcontainers quản lý trong một test class đã bật JUnit extension của Testcontainers bằng `@Testcontainers`. Field dùng `@Container` của Testcontainers cùng `@ServiceConnection` của Boot; Boot kiểm tra loại container hoặc image để tạo `ConnectionDetails` bean tương ứng.

```java
@Testcontainers
@SpringBootTest
class PostgreSqlIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16");
}
```

Việc khởi động và dừng container vẫn thuộc Testcontainers. Boot chỉ sử dụng thông tin kết nối của container đang chạy để cấu hình application infrastructure.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="service-connection-container-bean">Container bean trong `@TestConfiguration` tham gia service connection như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Container cũng có thể được khai báo bằng phương thức `@Bean` trong `@TestConfiguration` và gắn `@ServiceConnection`. Cách này giúp cấu hình container có thể tái sử dụng và đưa bean vào test application context do Spring quản lý.

Với phương thức bean, Boot dùng kiểu trả về đã khai báo để chọn connection-details factory mà không cần gọi sớm phương thức chỉ để xem Docker image. Vì vậy kiểu trả về container cụ thể cung cấp nhiều thông tin hơn `GenericContainer`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="service-connection-name-hints">Khi nào `GenericContainer` cần tên service connection được chỉ định rõ?</a>

<details>
<summary>Xem chi tiết</summary>
`GenericContainer` không xác định service bằng Java type. Với container bean, Boot có thể cần chỉ định rõ `@ServiceConnection(name = "...")` để chọn đúng connection-details factory mà không tạo container chỉ để phát hiện image.

Hãy dùng tên mà Boot service-connection factory nhận diện cho công nghệ đó. Đây là gợi ý tích hợp dành cho Boot, không phải tên container hay khái niệm vòng đời của Testcontainers.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-property-source-fallback">Khi nào `@DynamicPropertySource` là phương án thay thế phù hợp hơn?</a>

<details>
<summary>Xem chi tiết</summary>
Hãy dùng `@DynamicPropertySource` khi Boot chưa có service-connection factory phù hợp, application dùng contract property tùy chỉnh hoặc test cần công bố giá trị mà một loại `ConnectionDetails` được hỗ trợ không biểu diễn được.

Cách này thủ công hơn: test đọc giá trị từ container rồi tự đăng ký dynamic properties. Cơ chế chung thuộc Spring TestContext; Boot service connection nên được ưu tiên khi tích hợp có kiểu đã biểu diễn đúng mục đích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testcontainers-ownership-boundary">Tích hợp của Boot kết thúc ở đâu và trách nhiệm Testcontainers tổng quát bắt đầu ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>
Boot testing sở hữu `@ServiceConnection`, các adapter `ConnectionDetails` và cách những thông tin đó đi vào Boot auto-configuration. Với container được quản lý qua annotation hoặc extension JUnit của Testcontainers, Testcontainers chịu trách nhiệm khởi động và dừng container. Container được khai báo dưới dạng Spring `@Bean` thì đi theo vòng đời Spring application context: Spring tạo và khởi động container cùng context rồi dừng nó khi context đóng. Docker image, network, wait strategy, cơ chế tái sử dụng container và kết nối Docker tổng quát vẫn thuộc Testcontainers.

Nếu container do Testcontainers quản lý không khởi động hoặc network/wait strategy hoạt động sai, hãy kiểm tra Testcontainers. Với container bean do Spring quản lý, hãy kiểm tra thêm quá trình tạo bean và vòng đời context. Nếu container đang chạy nhưng Boot không cấu hình được application client từ nó, hãy kiểm tra phần tích hợp service connection.

</details>

- [Quay lại đầu trang](#back-to-top)
