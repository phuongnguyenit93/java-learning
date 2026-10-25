<a id="back-to-top"></a>

# `WebEnvironment` và ranh giới kiểm thử với server thật

## Menu
- [`WebEnvironment` điều khiển điều gì?](#web-environment-model)
- [`WebEnvironment.MOCK` cung cấp gì khi không khởi động server?](#web-environment-mock)
- [`RANDOM_PORT` và `DEFINED_PORT` khởi động embedded server thật như thế nào?](#web-environment-real-server)
- [Khi nào `WebEnvironment.NONE` phù hợp?](#web-environment-none)
- [Các test client do Boot hỗ trợ nằm ở đâu trong kiểm thử chạy server thật?](#real-server-test-clients)
- [Vì sao kiểm thử chạy server thật thay đổi kỳ vọng về rollback transaction?](#real-server-transaction-boundary)
- [Phạm vi kiểm thử bàn giao sang web runtime và Spring web testing ở đâu?](#web-runtime-testing-handoff)

## <a id="web-environment-model">`WebEnvironment` điều khiển điều gì?</a>

<details>
<summary>Xem chi tiết</summary>
`SpringBootTest.WebEnvironment` điều khiển loại web context mà full Boot test sử dụng và embedded server có được khởi động hay không. Bốn giá trị là `MOCK`, `RANDOM_PORT`, `DEFINED_PORT` và `NONE`.

Đây là quyết định test bootstrap, không phải web-framework API. Nó quyết định Boot thiết lập environment nào để MVC hoặc WebFlux infrastructure được kiểm thử bên trong.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Testing Spring Boot Applications](https://docs.spring.io/spring-boot/3.3/reference/testing/spring-boot-applications.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-environment-mock">`WebEnvironment.MOCK` cung cấp gì khi không khởi động server?</a>

<details>
<summary>Xem chi tiết</summary>
`MOCK` là mặc định. Khi có web stack được hỗ trợ, Boot nạp web `ApplicationContext` với mock web environment nhưng không khởi động embedded server. Nếu classpath không có web environment, Boot chuyển về context non-web thông thường.

Với ứng dụng MVC, hãy ghép chế độ này với `MockMvc` để kiểm thử request theo mô hình mock. Với ứng dụng WebFlux, Boot có thể auto-configure `WebTestClient` cho reactive web application dạng mock. Trong Spring Boot 3.3, `WebTestClient` ở mock environment là đường dành cho WebFlux; không nên trình bày nó như lựa chọn tương đương `MockMvc` cho MVC.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-environment-real-server">`RANDOM_PORT` và `DEFINED_PORT` khởi động embedded server thật như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Cả `RANDOM_PORT` và `DEFINED_PORT` đều nạp `WebServerApplicationContext` và khởi động embedded web server. `RANDOM_PORT` yêu cầu server bind một port còn trống; `DEFINED_PORT` dùng application port đã cấu hình hoặc mặc định thông thường.

`RANDOM_PORT` thường an toàn hơn cho bộ test tự động vì các lần chạy song song không tranh cùng một port cố định. Nếu client tùy chỉnh cần biết port thực tế, có thể inject bằng `@LocalServerPort`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-environment-none">Khi nào `WebEnvironment.NONE` phù hợp?</a>

<details>
<summary>Xem chi tiết</summary>
`NONE` vẫn boot application qua `SpringApplication` nhưng không cấu hình web environment. Nó phù hợp khi test cần full Boot configuration/auto-configuration nhưng cố ý loại các mối quan tâm của Servlet hoặc Reactive web runtime.

Ví dụ command-line application, scheduler, component xử lý batch hoặc configuration integration test có thể cần độ sát thực tế của quá trình khởi động Boot mà không cần mock web infrastructure hay server.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="real-server-test-clients">Các test client do Boot hỗ trợ nằm ở đâu trong kiểm thử chạy server thật?</a>

<details>
<summary>Xem chi tiết</summary>
Với test chạy server thật, Boot có thể cung cấp `WebTestClient` tự phân giải URL tương đối theo server đang chạy. Nếu WebFlux không có hoặc không muốn thêm chỉ để làm client, Boot cũng cung cấp `TestRestTemplate` cho lời gọi kiểu REST.

Các client này là tiện ích quanh web environment đã chọn. Chúng không quyết định server có tồn tại hay không; `WebEnvironment` mới quyết định điều đó. API gửi request và kiểm tra kết quả của client thuộc phần hỗ trợ kiểm thử web tương ứng của Spring.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="real-server-transaction-boundary">Vì sao kiểm thử chạy server thật thay đổi kỳ vọng về rollback transaction?</a>

<details>
<summary>Xem chi tiết</summary>
Với `RANDOM_PORT` hoặc `DEFINED_PORT`, test client và server xử lý request trên các thread khác nhau. Vì vậy test-managed `@Transactional` transaction không tự động bao quanh transaction được application code mở ở server side.

Transaction của test method vẫn có thể rollback phần việc của chính nó, nhưng thay đổi do server commit có thể còn lại. Ngữ nghĩa chi tiết của test-managed transaction thuộc Spring TestContext; bài học riêng của Boot là server thật đã vượt qua ranh giới transaction đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-runtime-testing-handoff">Phạm vi kiểm thử bàn giao sang web runtime và Spring web testing ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>
Boot testing sở hữu lựa chọn `WebEnvironment`, các client cho server đang chạy do Boot cung cấp và cách các tiện ích đó tích hợp với Boot test context. Việc chọn production server, port, TLS, forwarded headers và các hành vi runtime khác của server thuộc module Spring Boot web-runtime.

`MockMvc`, API test của Spring MVC, cơ chế gửi request/kiểm tra kết quả của `WebTestClient` và hành vi web-test tổng quát thuộc Spring Framework testing. Module này sử dụng các công cụ đó nhưng không định nghĩa lại chúng.

</details>

- [Quay lại đầu trang](#back-to-top)
