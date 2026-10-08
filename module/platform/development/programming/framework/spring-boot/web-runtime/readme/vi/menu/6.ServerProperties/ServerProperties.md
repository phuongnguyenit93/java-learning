<a id="back-to-top"></a>

# Server properties và mô hình cấu hình

## Menu
- [ServerProperties là mô hình cấu hình server của Boot](#server-properties-model)
- [Các điều khiển dùng chung trong namespace server.*](#common-server-namespace)
- [Cổng, địa chỉ bind và các điều khiển HTTP endpoint](#port-address-endpoint-controls)
- [server.tomcat.*, server.jetty.*, server.undertow.* và server.netty.*](#server-specific-namespaces)
- [Chọn property dùng chung hay property riêng của từng server](#portable-vs-server-specific-properties)
- [Precedence và binding thuộc Externalized Configuration](#external-configuration-handoff)

## <a id="server-properties-model">ServerProperties là mô hình cấu hình server của Boot</a>

<details>
<summary>Xem chi tiết</summary>
`ServerProperties` là mô hình property cấu hình của Boot dành cho embedded server. Nó cung cấp cho server customizer được tự động cấu hình một biểu diễn có cấu trúc của các giá trị dưới `server.*`, gồm cả thiết lập dùng chung và nhóm riêng theo cách triển khai.

Đây là ranh giới hữu ích: cấu hình bên ngoài mô tả hành vi server mong muốn, `ServerProperties` biểu diễn mô hình Boot đã bind, còn customizer chuyển mô hình đó thành cấu hình của factory đã chọn. Mã ứng dụng thường không cần inject `ServerProperties` chỉ để đổi một thiết lập vốn đã có thể khai báo ngoài mã.

### Tài liệu tham khảo

- [Spring Boot 3.3 Common Application Properties — Server Properties](https://docs.spring.io/spring-boot/3.3/appendix/application-properties/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-server-namespace">Các điều khiển dùng chung trong namespace server.*</a>

<details>
<summary>Xem chi tiết</summary>
Các thiết lập dùng chung nằm trực tiếp dưới `server.*` khi Boot có thể biểu diễn cùng một ý định trên các server được hỗ trợ. Ví dụ gồm `server.port`, `server.address`, `server.compression.*`, `server.http2.enabled`, `server.max-http-request-header-size`, `server.shutdown`, `server.forward-headers-strategy` và `server.ssl.*`.

“Dùng chung” không có nghĩa mọi server triển khai tính năng giống hệt nhau. Nó có nghĩa Boot cung cấp một ý định cấu hình chung và adapter của từng server hiện thực ý định đó trong phạm vi server hỗ trợ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="port-address-endpoint-controls">Cổng, địa chỉ bind và các điều khiển HTTP endpoint</a>

<details>
<summary>Xem chi tiết</summary>
Cổng HTTP chính mặc định là `8080` với ứng dụng web độc lập. Đặt `server.port` thành một giá trị cố định, `0` để hệ điều hành cấp một cổng còn trống hoặc `-1` để vẫn tạo context ứng dụng web nhưng không mở HTTP endpoint. `server.address` điều khiển địa chỉ mạng mà server bind.

Đây là điều khiển runtime của server, không phải điều khiển routing. Thay cổng hoặc địa chỉ bind chỉ thay nơi server lắng nghe; nó không thay route mapping của Spring MVC hay WebFlux.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="server-specific-namespaces">server.tomcat.*, server.jetty.*, server.undertow.* và server.netty.*</a>

<details>
<summary>Xem chi tiết</summary>
Namespace riêng theo server cung cấp những khả năng khó biểu diễn bằng một mô hình property dùng chung duy nhất. Boot 3.3 có các nhóm như `server.tomcat.*`, `server.jetty.*`, `server.undertow.*` và `server.netty.*`.

Ví dụ gồm hàng đợi kết nối và thiết lập remote IP của Tomcat, thiết lập access log của Jetty, worker/options của Undertow hoặc thiết lập kết nối/tài nguyên của Netty. Khi dùng các namespace này, ứng dụng đang chủ động làm cấu hình phụ thuộc vào một họ embedded server cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="portable-vs-server-specific-properties">Chọn property dùng chung hay property riêng của từng server</a>

<details>
<summary>Xem chi tiết</summary>
Hãy bắt đầu bằng property `server.*` dùng chung nếu nó mô tả được yêu cầu. Cách này giúp ứng dụng dễ đổi server hơn và làm ý định rõ mà không cần hiểu server API. Chỉ dùng property riêng theo server khi chính yêu cầu phụ thuộc cách triển khai hoặc mô hình chung chưa cung cấp quyền kiểm soát cần thiết.

Nếu cả hai mức property đều không đủ, mới chuyển sang `WebServerFactoryCustomizer`. Chuỗi quyết định này giữ tùy biến ở dạng khai báo và có khả năng chuyển đổi tối đa trong phạm vi yêu cầu cho phép.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="external-configuration-handoff">Precedence và binding thuộc Externalized Configuration</a>

<details>
<summary>Xem chi tiết</summary>
Module này tiêu thụ kết quả của hệ thống externalized configuration của Boot; nó không định nghĩa lại configuration precedence, kích hoạt profile, relaxed binding, biến môi trường hay thứ tự property source. Các quy tắc đó quyết định **giá trị nào thắng** trước khi phần tùy biến server sử dụng nó.

Ví dụ `SERVER_PORT` có thể bind vào `server.port`, nhưng lý do biến môi trường ghi đè hoặc bị nguồn khác ghi đè thuộc module `externalized-configuration`. Ở đây câu hỏi cần học là giá trị `server.port` đã được phân giải sẽ thay đổi embedded server ra sao.

</details>

- [Quay lại đầu trang](#back-to-top)
