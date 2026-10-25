<a id="back-to-top"></a>

# Auto-configuration cho Reactive server

## Menu
- [Khi nào auto-configuration cho Reactive web server được áp dụng?](#reactive-server-auto-config-trigger)
- [Vì sao Reactor Netty là Reactive server mặc định?](#reactor-netty-default)
- [Các Reactive factory cho Netty, Tomcat, Jetty và Undertow](#reactive-server-factories)
- [Cấu hình server nằm ở đâu trong auto-configuration của Reactive factory?](#reactive-server-configuration-relation)
- [Back-off khi ứng dụng tự cung cấp Reactive WebServerFactory](#reactive-server-backoff)
- [Bootstrap Reactive server và ranh giới với cơ chế xử lý request của Spring WebFlux](#reactive-server-webflux-boundary)

## <a id="reactive-server-auto-config-trigger">Khi nào auto-configuration cho Reactive web server được áp dụng?</a>

<details>
<summary>Xem chi tiết</summary>
`ReactiveWebServerFactoryAutoConfiguration` là nhánh tương ứng của Reactive runtime. Nó tham gia với ứng dụng web reactive khi các class server/runtime cần thiết có mặt, cấu hình hạ tầng reactive factory và đăng ký cơ chế xử lý customizer trước khi server khởi động.

Do đó loại ứng dụng `REACTIVE` là đầu vào của bootstrap/lựa chọn server, không phải một nhãn được gắn sau khi server đã chạy.

### Tài liệu tham khảo

- [Spring Boot 3.3 API — ReactiveWebServerFactoryAutoConfiguration](https://docs.spring.io/spring-boot/3.3/api/java/org/springframework/boot/autoconfigure/web/reactive/ReactiveWebServerFactoryAutoConfiguration.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactor-netty-default">Vì sao Reactor Netty là Reactive server mặc định?</a>

<details>
<summary>Xem chi tiết</summary>
Reactor Netty là mặc định vì `spring-boot-starter-webflux` bao gồm `spring-boot-starter-reactor-netty`. Một ứng dụng WebFlux thông thường vì thế có sẵn class cần thiết để Boot tự động cấu hình `NettyReactiveWebServerFactory` mà không cần chọn thêm server.

“Mặc định” ở đây là quy ước của dependency. Nó không có nghĩa WebFlux bắt buộc Reactor Netty: Boot 3.3 cũng có thể chạy stack reactive trên các tích hợp được hỗ trợ của Tomcat, Jetty hoặc Undertow.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-server-factories">Các Reactive factory cho Netty, Tomcat, Jetty và Undertow</a>

<details>
<summary>Xem chi tiết</summary>
Boot cung cấp `NettyReactiveWebServerFactory`, `TomcatReactiveWebServerFactory`, `JettyReactiveWebServerFactory` và `UndertowReactiveWebServerFactory`. Cách triển khai được chọn phụ thuộc mô hình ứng dụng reactive và các class server tương thích có trên classpath.

Vì vậy chỉ thấy “Tomcat” chưa đủ để kết luận ứng dụng đang chạy MVC. Tomcat có thể chạy ứng dụng Servlet hoặc tham gia tích hợp reactive server; loại ứng dụng web và factory tương ứng mới cho biết Boot đang đi theo nhánh nào.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-server-configuration-relation">Cấu hình server nằm ở đâu trong auto-configuration của Reactive factory?</a>

<details>
<summary>Xem chi tiết</summary>
`ServerProperties` cũng cấp dữ liệu cho đường reactive server. Boot cung cấp `ReactiveWebServerFactoryCustomizer` cho thiết lập server dùng chung và các customizer riêng theo server bổ sung cho namespace của cách triển khai khi phù hợp.

Mô hình vẫn giống phía Servlet: cấu hình được phân giải trước, customizer áp dụng lên factory đã chọn, rồi context ứng dụng web reactive mới tạo server. Nhờ mô hình chung này, các chương sau có thể nói về `server.*` một lần thay vì lặp lại cho từng stack.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-server-backoff">Back-off khi ứng dụng tự cung cấp Reactive WebServerFactory</a>

<details>
<summary>Xem chi tiết</summary>
Cấu hình reactive server factory back off khi ứng dụng tự cung cấp `ReactiveWebServerFactory` bean. Khi đó ứng dụng thay lựa chọn factory của Boot, vì vậy chỉ nên làm khi property và customizer có mục tiêu cụ thể không thể biểu diễn yêu cầu.

Các factory customizer do Boot tự động cấu hình vẫn áp dụng lên factory tùy chỉnh. Nếu factory tùy chỉnh có vẻ bỏ qua hoặc ghi đè thiết lập ứng dụng, hãy kiểm tra cả trạng thái ban đầu của factory lẫn chuỗi customizer đã sắp thứ tự trước khi kết luận auto-configuration đã bị vô hiệu hoàn toàn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-server-webflux-boundary">Bootstrap Reactive server và ranh giới với cơ chế xử lý request của Spring WebFlux</a>

<details>
<summary>Xem chi tiết</summary>
Reactive server auto-configuration chịu trách nhiệm cho bootstrap server và tích hợp vòng đời. Spring WebFlux sở hữu mô hình xử lý HTTP phía trên: `HttpHandler`, routing, annotated controller, codec, filter, cách kết hợp luồng reactive và ngữ nghĩa back-pressure.

Giữ ranh giới này rõ khi tinh chỉnh/chẩn đoán. Vấn đề cổng, TLS, nén hoặc tài nguyên server trước hết thuộc Boot/server runtime. Vấn đề route, codec hoặc chuỗi xử lý reactive thuộc WebFlux và Reactor.

</details>

- [Quay lại đầu trang](#back-to-top)
