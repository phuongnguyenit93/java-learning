<a id="back-to-top"></a>

# Spring Boot web runtime

## Menu
- [Spring Boot sở hữu phần nào của web runtime?](#web-runtime-role)
- [Vì sao Boot cần một tầng web runtime?](#web-runtime-problem)
- [Từ SpringApplication tới embedded server đang chạy](#boot-to-server-flow)
- [Servlet và Reactive như hai mô hình runtime](#servlet-reactive-runtime-model)
- [Ranh giới của Boot web runtime nằm ở đâu?](#web-runtime-boundaries)
- [Các chương web runtime kết nối với nhau như thế nào?](#web-runtime-learning-path)

## <a id="web-runtime-role">Spring Boot sở hữu phần nào của web runtime?</a>

<details>
<summary>Xem chi tiết</summary>
Web runtime của Spring Boot là tầng tích hợp biến một ứng dụng Boot thông thường thành một tiến trình có thể sở hữu và khởi động HTTP server. Boot xác định ứng dụng có phải ứng dụng web hay không, tạo `ApplicationContext` phù hợp với mô hình web, tự động cấu hình embedded server factory, áp dụng cấu hình và customizer, rồi quản lý server đó trong suốt quá trình khởi động và shutdown.

Phạm vi này hẹp hơn khái niệm “mọi thứ liên quan đến web”. Boot nối bootstrap, cấu hình, auto-configuration và một cách triển khai server được hỗ trợ. Request mapping, gọi controller, codec, filter trong chuỗi xử lý request, reactive operator và cơ chế xử lý request chi tiết thuộc Spring MVC, Spring WebFlux hoặc chính server.

### Tài liệu tham khảo

- [Spring Boot 3.3 Reference — Web](https://docs.spring.io/spring-boot/3.3/reference/web/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-runtime-problem">Vì sao Boot cần một tầng web runtime?</a>

<details>
<summary>Xem chi tiết</summary>
Nếu không có tầng web runtime của Boot, ứng dụng phải tự ghép nhiều quyết định hạ tầng: dùng cách triển khai server nào, tạo server ra sao, cấu hình đi vào server bằng cách nào, server khởi động ở thời điểm nào so với Spring context và dừng cùng ứng dụng như thế nào. Boot biến phần lớn các quyết định đó thành quy ước có thể thay đổi bằng dependency và cấu hình thay vì phải viết mã bootstrap riêng.

Điểm quan trọng là **web framework** và **web server** là hai trách nhiệm khác nhau. Spring MVC hoặc WebFlux mô tả cách request được framework xử lý; Boot làm cho ứng dụng có thể chạy độc lập bằng cách gắn framework đó với vòng đời của embedded server.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boot-to-server-flow">Từ SpringApplication tới embedded server đang chạy</a>

<details>
<summary>Xem chi tiết</summary>
Có thể hình dung luồng chính như sau:

```text
SpringApplication.run(...)
        ↓
suy ra hoặc dùng WebApplicationType đã cấu hình
        ↓
tạo Servlet hoặc Reactive web ApplicationContext
        ↓
áp dụng web-server auto-configuration
        ↓
lấy WebServerFactory
        ↓
áp dụng ServerProperties + WebServerFactoryCustomizer
        ↓
tạo và khởi động WebServer trong quá trình refresh context
```

Mô hình tư duy cần giữ là embedded server nằm trong vòng đời do Boot quản lý. Thông thường bạn không viết một `main` riêng để khởi động Tomcat, Jetty, Undertow hay Reactor Netty; context ứng dụng web phù hợp sẽ điều phối server đó cùng Spring context.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="servlet-reactive-runtime-model">Servlet và Reactive như hai mô hình runtime</a>

<details>
<summary>Xem chi tiết</summary>
Boot hỗ trợ hai mô hình web runtime chính. Ứng dụng kiểu **Servlet** dùng server có khả năng Servlet và `ServletWebServerApplicationContext`. Ứng dụng kiểu **Reactive** dùng reactive web server và `ReactiveWebServerApplicationContext`. Lựa chọn này ảnh hưởng tới server factory, auto-configuration và cách Boot tích hợp server.

Nó không có nghĩa module này sở hữu ngữ nghĩa của Servlet API hay cơ chế xử lý request theo reactive. Web runtime trả lời câu hỏi “Boot phải khởi động loại ứng dụng web nào?”; Spring MVC và Spring WebFlux trả lời “framework xử lý request như thế nào sau khi runtime đã sẵn sàng?”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-runtime-boundaries">Ranh giới của Boot web runtime nằm ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>
Module này sở hữu các quyết định đặc trưng của Boot quanh loại ứng dụng web, lựa chọn embedded server, server auto-configuration, các property `server.*`, tùy biến bằng mã, khả năng HTTP ở tầng server, cách web server dùng TLS/SSL bundle, forwarded headers và graceful shutdown.

Khi câu hỏi chuyển sang handler mapping, controller, chuỗi filter của framework, codec, reactive operator, cơ chế luồng nội bộ của Servlet container, lý thuyết HTTP, lý thuyết chuỗi chứng chỉ hoặc cơ chế hoạt động chi tiết của reverse proxy thì phải bàn giao sang phần học sở hữu nội dung đó. Ranh giới này giúp web-runtime không trở thành một phiên bản lặp lại của MVC, WebFlux, hạ tầng mạng hay phần nội bộ của container.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-runtime-learning-path">Các chương web runtime kết nối với nhau như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Thứ tự các chương đi theo chuỗi quyết định thường gặp trong thực tế. Trước hết hiểu loại ứng dụng và server được chọn từ classpath. Sau đó tìm hiểu Boot tạo server factory tương ứng như thế nào, property/customizer thay đổi factory ra sao và những khả năng HTTP nào được Boot cung cấp theo mô hình dùng chung.

Khi mô hình server cục bộ đã rõ, mới thêm các yếu tố triển khai: TLS và SSL bundle, forwarded headers sau proxy và graceful shutdown. Chương cuối gom các quyết định này thành một mô hình đầu-cuối và chỉ rõ điểm nào cần tiếp tục sang Spring Framework hoặc hạ tầng.

</details>

- [Quay lại đầu trang](#back-to-top)
