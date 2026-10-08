<a id="back-to-top"></a>

# TLS, SSL bundles và chứng chỉ web server

## Menu
- [Các điểm cấu hình của Boot cho TLS phía web server](#web-server-tls-entry-points)
- [Cấu hình keystore và PEM bằng server.ssl.*](#server-ssl-keystore-pem)
- [Áp dụng SSL bundle có tên cho web server](#ssl-bundle-consumption)
- [Tùy chọn trong SSL bundle và các server.ssl property rời](#bundle-vs-discrete-ssl-properties)
- [Server Name Indication và lựa chọn chứng chỉ](#server-name-indication)
- [Tích hợp TLS của web server và ranh giới với TLS/PKI tổng quát](#tls-ownership-boundary)

## <a id="web-server-tls-entry-points">Các điểm cấu hình của Boot cho TLS phía web server</a>

<details>
<summary>Xem chi tiết</summary>
Boot có hai hướng khai báo chính để bảo vệ embedded server. Hướng thứ nhất cấu hình dữ liệu chứng chỉ/khóa trực tiếp dưới `server.ssl.*`. Hướng thứ hai định nghĩa dữ liệu có tên có thể tái sử dụng dưới `spring.ssl.bundle.*` rồi trỏ web server tới một bundle bằng `server.ssl.bundle`.

Cả hai đều cấu hình server do Boot quản lý và không thay route của ứng dụng. Khác biệt chủ yếu nằm ở cách sở hữu/tái sử dụng cấu hình. Bundle có tên phù hợp khi cùng dữ liệu TLS hoặc tùy chọn cần được dùng nhất quán bởi nhiều loại kết nối mà Boot hỗ trợ.

### Tài liệu tham khảo

- [Spring Boot 3.3 How-to — Configure SSL](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html#howto.webserver.configure-ssl)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="server-ssl-keystore-pem">Cấu hình keystore và PEM bằng server.ssl.*</a>

<details>
<summary>Xem chi tiết</summary>
Với cấu hình trực tiếp cho server, `server.ssl.*` hỗ trợ dữ liệu Java KeyStore và dữ liệu chứng chỉ/khóa mã hóa PEM. Thiết lập keystore thường khai báo vị trí/mật khẩu keystore; thiết lập PEM khai báo vị trí chứng chỉ và private key.

Khi bật SSL cho server, connector chính chuyển sang HTTPS. Boot không cung cấp một cặp property thông thường để đồng thời cấu hình một HTTP connector và một HTTPS connector; nếu cần mô hình kết nối đó thì thêm connector còn lại bằng mã.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ssl-bundle-consumption">Áp dụng SSL bundle có tên cho web server</a>

<details>
<summary>Xem chi tiết</summary>
SSL bundle được khai báo dưới `spring.ssl.bundle.jks.<name>` hoặc `spring.ssl.bundle.pem.<name>`. Sau khi bundle tồn tại, embedded server có thể dùng bundle theo tên:

```properties
spring.ssl.bundle.pem.web.keystore.certificate=classpath:server.crt
spring.ssl.bundle.pem.web.keystore.private-key=classpath:server.key
server.ssl.bundle=web
```

Lớp trừu tượng `SslBundle` / `SslBundles` dùng chung thuộc application-runtime. Web-runtime chịu trách nhiệm cho điểm tiêu thụ: `server.ssl.bundle` yêu cầu Boot áp dụng bundle có tên đó vào embedded server.

### Tài liệu tham khảo

- [Spring Boot 3.3 Reference — SSL Bundles](https://docs.spring.io/spring-boot/3.3/reference/features/ssl.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bundle-vs-discrete-ssl-properties">Tùy chọn trong SSL bundle và các server.ssl property rời</a>

<details>
<summary>Xem chi tiết</summary>
`server.ssl.bundle` không được kết hợp với các tùy chọn dữ liệu Java KeyStore/PEM rời dưới `server.ssl`. Khi dùng bundle, các property như `server.ssl.ciphers`, `server.ssl.enabled-protocols` và `server.ssl.protocol` bị bỏ qua.

Hãy đưa tùy chọn giao thức/bộ mã vào `spring.ssl.bundle.<type>.<name>.options` của bundle có tên. Cách này giữ bundle tự chứa và tránh hai mô hình cấu hình cạnh tranh cho cùng trạng thái TLS.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="server-name-indication">Server Name Indication và lựa chọn chứng chỉ</a>

<details>
<summary>Xem chi tiết</summary>
Boot 3.3 có thể ánh xạ hostname tới các SSL bundle bổ sung qua `server.ssl.server-name-bundles`. Bundle ở `server.ssl.bundle` vẫn là dữ liệu chứng chỉ mặc định, còn các mục hostname có tên chọn bundle thay thế cho máy khách hỗ trợ SNI.

Tomcat, Netty và Undertow có cấu hình SNI do Boot quản lý. Jetty khác ở chỗ ánh xạ SNI tường minh của Boot không được hỗ trợ, dù Jetty có thể tự thiết lập SNI khi được cung cấp nhiều chứng chỉ. Đây là khác biệt về khả năng server chứ không phải quy tắc TLS tổng quát.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tls-ownership-boundary">Tích hợp TLS của web server và ranh giới với TLS/PKI tổng quát</a>

<details>
<summary>Xem chi tiết</summary>
Module này cần giải thích Boot nhận dữ liệu chứng chỉ ở đâu, gắn dữ liệu đó vào embedded server như thế nào, bundle có tên được chọn ra sao và hạn chế riêng theo server xuất hiện ở đâu. Nó cũng cần nối sang trường hợp triển khai như TLS termination tại reverse proxy.

Việc cấp chứng chỉ, mô hình tin cậy CA, mật mã bắt tay TLS, thiết kế bộ mã, xác thực chuỗi chứng chỉ, giao thức ACME và vận hành PKI thuộc phần bảo mật/mạng chịu trách nhiệm. Boot chỉ tiêu thụ các khái niệm đó qua cấu hình, không định nghĩa lại chúng.

</details>

- [Quay lại đầu trang](#back-to-top)
