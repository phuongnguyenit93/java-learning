<a id="back-to-top"></a>

# Khả năng HTTP, nén và thiết lập kết nối

## Menu
- [Nén HTTP response](#http-response-compression)
- [Bật HTTP/2 và khả năng hỗ trợ theo server](#http2-support)
- [Giới hạn kích thước request header](#request-header-limits)
- [Các thiết lập HTTP ở tầng kết nối và server](#connection-server-settings)
- [Access log của embedded server](#access-logging)
- [Điều khiển HTTP dùng chung và khả năng riêng theo server](#http-feature-portability)

## <a id="http-response-compression">Nén HTTP response</a>

<details>
<summary>Xem chi tiết</summary>
Boot cung cấp nén response qua `server.compression.*`. Tính năng này mặc định tắt; `server.compression.enabled=true` bật nén trên embedded server được hỗ trợ. Boot 3.3 hỗ trợ nén response với Jetty, Tomcat, Reactor Netty và Undertow.

Nén còn có điều kiện áp dụng. Kích thước response tối thiểu mặc định là `2KB`, và chỉ các MIME type được cấu hình mới được nén. Dùng `server.compression.min-response-size` và `server.compression.mime-types` để điều chỉnh thay vì giả định mọi response đều được nén.

### Tài liệu tham khảo

- [Spring Boot 3.3 How-to — Enable HTTP Response Compression](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html#howto.webserver.enable-response-compression)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http2-support">Bật HTTP/2 và khả năng hỗ trợ theo server</a>

<details>
<summary>Xem chi tiết</summary>
`server.http2.enabled=true` là công tắc dùng chung của Boot cho HTTP/2. Khi SSL bật, server dùng HTTP/2 qua TLS (`h2`); khi SSL không bật, server được hỗ trợ dùng HTTP/2 không mã hóa (`h2c`). Đây là khả năng của server, không phải tính năng request mapping của MVC hay WebFlux.

Chi tiết hỗ trợ vẫn khác theo server. Tomcat 10.1 dùng trong Boot 3.3 hỗ trợ `h2` và `h2c` sẵn, còn Jetty cần thêm HTTP/2 server dependency. Nếu công tắc dùng chung không cho hành vi mong đợi, hãy kiểm tra điều kiện tiên quyết của chính server đang dùng.

### Tài liệu tham khảo

- [Spring Boot 3.3 How-to — Configure HTTP/2](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html#howto.webserver.configure-http2)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="request-header-limits">Giới hạn kích thước request header</a>

<details>
<summary>Xem chi tiết</summary>
`server.max-http-request-header-size` biểu diễn giới hạn chung cho HTTP request header. Nó hữu ích để tránh metadata của request quá lớn và để đồng bộ giới hạn của ứng dụng với reverse proxy/load balancer ở phía trước.

Không nên suy ra một property dùng chung sẽ bao phủ mọi giới hạn liên quan header trên mọi server. Giới hạn response header hoặc điều khiển parser/connector sâu hơn có thể cần property/API riêng theo server. Hãy bắt đầu từ giới hạn request dùng chung của Boot và chỉ xuống sâu hơn khi môi trường triển khai thực sự cần.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="connection-server-settings">Các thiết lập HTTP ở tầng kết nối và server</a>

<details>
<summary>Xem chi tiết</summary>
Hàng đợi kết nối, hành vi khi nhàn rỗi, tài nguyên worker/thread, tùy chọn giao thức mức thấp và các điều khiển tương tự thường phụ thuộc server vì từng cách triển khai có mô hình runtime khác nhau. Boot cung cấp nhiều điều khiển trong namespace tương ứng và để trường hợp hiếm cho factory customizer.

Chỉ tinh chỉnh sau khi đã xác định server cụ thể và triệu chứng vận hành cụ thể. Một property dành cho Tomcat không có ý nghĩa dùng chung trên Reactor Netty, và thiết lập event loop của Netty cũng không nên được trình bày như quy tắc chung của Spring Boot.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-logging">Access log của embedded server</a>

<details>
<summary>Xem chi tiết</summary>
Access log của embedded server ghi lại quan sát ở tầng server như địa chỉ remote, request line, status, thời gian xử lý và số byte. Nó khác logging của ứng dụng được ghi trong controller, filter hoặc mã nghiệp vụ.

Access log được cấu hình theo server. Hướng dẫn Spring Boot 3.3 đưa ví dụ cho Tomcat dưới `server.tomcat.accesslog.*`, Undertow dưới `server.undertow.accesslog.*` và Jetty dưới `server.jetty.accesslog.*`. Token định dạng, vị trí file, cơ chế xoay log và trường cụ thể là mối quan tâm của cách triển khai server đã chọn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-feature-portability">Điều khiển HTTP dùng chung và khả năng riêng theo server</a>

<details>
<summary>Xem chi tiết</summary>
Một property dùng chung của Boot có nghĩa “một ý định cấu hình cho nhiều tích hợp được hỗ trợ”. Nó không đảm bảo các server có cùng cách triển khai, cùng mặc định bên dưới Boot hoặc cùng hành vi ở trường hợp biên. Nén, HTTP/2, giới hạn request header và graceful shutdown đều thể hiện mẫu này.

Hãy dùng điều khiển dùng chung để diễn đạt ý định của ứng dụng, rồi dùng thiết lập riêng theo server cho yêu cầu phụ thuộc cách triển khai. Nếu quyết định ở môi trường production cần đi sâu vào giao thức HTTP, hành vi proxy, tinh chỉnh kernel/mạng hoặc phần nội bộ container thì nên bàn giao sang phần học hạ tầng tương ứng.

</details>

- [Quay lại đầu trang](#back-to-top)
