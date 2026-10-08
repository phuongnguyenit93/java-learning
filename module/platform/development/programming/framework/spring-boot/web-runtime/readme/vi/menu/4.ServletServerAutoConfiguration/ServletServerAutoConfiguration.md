<a id="back-to-top"></a>

# Auto-configuration cho Servlet server

## Menu
- [Khi nào auto-configuration cho Servlet web server được áp dụng?](#servlet-server-auto-config-trigger)
- [Các Servlet factory cho Tomcat, Jetty và Undertow](#servlet-server-factories)
- [Cấu hình server nằm ở đâu trong auto-configuration của Servlet factory?](#servlet-server-configuration-relation)
- [Back-off khi ứng dụng tự cung cấp Servlet WebServerFactory](#servlet-server-backoff)
- [Customizer của Boot và ứng dụng quanh Servlet factory](#servlet-server-customizer-chain)
- [Bootstrap Servlet server và ranh giới với cơ chế xử lý request của Spring MVC](#servlet-server-mvc-boundary)

## <a id="servlet-server-auto-config-trigger">Khi nào auto-configuration cho Servlet web server được áp dụng?</a>

<details>
<summary>Xem chi tiết</summary>
`ServletWebServerFactoryAutoConfiguration` tham gia khi Boot đang chạy một ứng dụng web Servlet và các class cần thiết của server có mặt. Trách nhiệm của nó là lắp ráp hạ tầng embedded Servlet server của Boot, bao gồm các property cấu hình và cơ chế xử lý customizer cho factory được chọn.

Điều kiện quan trọng là mô hình runtime đã được xác định từ trước. Chỉ có một server JAR trên classpath chưa đủ để làm Servlet server factory xuất hiện trong ứng dụng cố ý chạy không phải web hoặc reactive.

### Tài liệu tham khảo

- [Spring Boot 3.3 API — ServletWebServerFactoryAutoConfiguration](https://docs.spring.io/spring-boot/3.3/api/java/org/springframework/boot/autoconfigure/web/servlet/ServletWebServerFactoryAutoConfiguration.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="servlet-server-factories">Các Servlet factory cho Tomcat, Jetty và Undertow</a>

<details>
<summary>Xem chi tiết</summary>
Boot cung cấp các Servlet factory cụ thể cho embedded container được hỗ trợ: `TomcatServletWebServerFactory`, `JettyServletWebServerFactory` và `UndertowServletWebServerFactory`. Điều kiện classpath quyết định cấu hình dành cho cách triển khai nào đủ điều kiện.

Ứng dụng thường tiếp cận các factory này gián tiếp qua starter. Vì vậy thay dependency của server là lựa chọn đầu tiên: nó thay cách triển khai đủ điều kiện mà không thay cơ chế vòng đời/cấu hình của Boot.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="servlet-server-configuration-relation">Cấu hình server nằm ở đâu trong auto-configuration của Servlet factory?</a>

<details>
<summary>Xem chi tiết</summary>
Auto-configuration không chỉ tạo factory. Boot bind cấu hình `server.*` vào `ServerProperties` rồi cung cấp các customizer áp dụng thiết lập dùng chung và riêng theo server lên factory trước khi server được tạo.

Có thể hình dung luồng: `cấu hình bên ngoài → ServerProperties → customizer của Boot → ServletWebServerFactory → WebServer`. Precedence và relaxed binding thuộc module `externalized-configuration`; ở đây trọng tâm là cấu hình server đã được phân giải sẽ tác động lên web runtime như thế nào.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="servlet-server-backoff">Back-off khi ứng dụng tự cung cấp Servlet WebServerFactory</a>

<details>
<summary>Xem chi tiết</summary>
Các cấu hình embedded Servlet factory được thiết kế để back off khi ứng dụng tự khai báo `ServletWebServerFactory` bean. Đây là hook thay thế mạnh vì ứng dụng đã tự nhận trách nhiệm chọn và tạo factory.

Hãy dùng cơ chế này có chủ đích. Tự cung cấp factory bean can thiệp sâu hơn property hoặc customizer và làm giảm khả năng chuyển đổi giữa các server. Các `WebServerFactoryCustomizer` do Boot tự động cấu hình vẫn được áp dụng lên factory tùy chỉnh, nên thay factory không có nghĩa toàn bộ tùy biến của Boot biến mất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="servlet-server-customizer-chain">Customizer của Boot và ứng dụng quanh Servlet factory</a>

<details>
<summary>Xem chi tiết</summary>
Customizer của Boot và ứng dụng được thu thập rồi áp dụng lên `ServletWebServerFactory` trước khi factory tạo server. Các `WebServerFactoryCustomizer` do Boot tự động cấu hình dùng order `0`; customizer của người dùng không khai báo thứ tự cụ thể thường chạy sau nhóm customizer của Boot đó.

Thứ tự quan trọng khi hai customizer thay cùng một thiết lập. Hãy ưu tiên configuration property nếu đã có; chỉ dùng thứ tự tường minh khi mã thực sự cần tinh chỉnh hoặc ghi đè trạng thái factory có chủ đích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="servlet-server-mvc-boundary">Bootstrap Servlet server và ranh giới với cơ chế xử lý request của Spring MVC</a>

<details>
<summary>Xem chi tiết</summary>
Servlet server auto-configuration đưa server hỗ trợ Servlet vào trạng thái chạy và nối nó với vòng đời của Boot. Cơ chế xử lý request của Spring MVC bắt đầu ở ranh giới khác: `DispatcherServlet`, handler mapping, controller, argument resolution, message conversion, interceptor và MVC error handling thuộc Spring Framework web.

Ranh giới này hữu ích khi chẩn đoán. Nếu ứng dụng không bind được cổng, hãy kiểm tra loại ứng dụng, server dependency, factory auto-configuration và thiết lập server. Nếu server đã nhận kết nối nhưng controller mapping sai, hãy chuyển điều tra sang lớp xử lý request của MVC.

</details>

- [Quay lại đầu trang](#back-to-top)
