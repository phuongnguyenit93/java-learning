<a id="back-to-top"></a>

# Các mặc định web của Boot và ranh giới sở hữu với Spring Framework

## Menu
- [Các mặc định web của Boot cần nhận biết](#boot-web-defaults-map)
- [Quy ước và kiểm soát tường minh trong web runtime](#convention-vs-explicit-web-control)
- [Bàn giao trách nhiệm sang Spring MVC và Spring WebFlux](#spring-framework-web-handoff)
- [Bàn giao trách nhiệm sang phần nội bộ của Servlet và Reactive server](#container-internals-handoff)
- [Bàn giao trách nhiệm sang hạ tầng HTTP, TLS và reverse proxy](#network-security-handoff)
- [Mô hình quyết định đầu-cuối của Boot web runtime](#web-runtime-end-to-end-model)

## <a id="boot-web-defaults-map">Các mặc định web của Boot cần nhận biết</a>

<details>
<summary>Xem chi tiết</summary>
Một số quy ước giải thích vì sao ứng dụng web Boot chạy được ngay từ đầu: classpath quyết định `WebApplicationType` nếu không ghi đè; `spring-boot-starter-web` chọn Servlet/MVC với Tomcat mặc định; `spring-boot-starter-webflux` chọn mô hình reactive với Reactor Netty khi MVC không có; cổng HTTP chính mặc định là `8080`.

Một số mặc định ở môi trường production khác cũng cần nhớ: nén response mặc định tắt, `server.shutdown` là `immediate` trong Boot 3.3, còn xử lý forwarded header thường là `NONE` ngoại trừ nền tảng đám mây được hỗ trợ nơi Boot mặc định `NATIVE`. Đây là quy ước cần nhận diện, không phải giả định nên giấu khỏi cấu hình triển khai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="convention-vs-explicit-web-control">Quy ước và kiểm soát tường minh trong web runtime</a>

<details>
<summary>Xem chi tiết</summary>
Boot web runtime hoạt động tốt nhất khi quy ước xử lý trường hợp phổ biến còn cấu hình tường minh thể hiện quyết định triển khai. Dependency chọn server được hỗ trợ, property `server.*` diễn đạt hành vi chung, namespace riêng theo server xử lý chi tiết theo cách triển khai, còn customizer lấp khoảng trống của mô hình property.

Chuyển sang kiểm soát tường minh khi có lý do thật: classpath trộn MVC/WebFlux, server không mặc định, địa chỉ bind cố định, dữ liệu TLS, proxy forwarding, connector bổ sung hoặc graceful shutdown. Giữ quyết định ở lớp trừu tượng cao nhất có thể giúp nâng cấp và chuyển server dễ suy luận hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-framework-web-handoff">Bàn giao trách nhiệm sang Spring MVC và Spring WebFlux</a>

<details>
<summary>Xem chi tiết</summary>
Sau khi Boot đã chọn web runtime, khởi động server và nối nó với context ứng dụng, ngữ nghĩa xử lý request thuộc Spring Framework. MVC sở hữu `DispatcherServlet`, controller mapping, converter, interceptor và mô hình Servlet web framework. WebFlux sở hữu route/handler reactive, codec, filter và mô hình xử lý reactive.

Boot thêm auto-configuration và các mặc định hợp lý quanh các framework đó, nhưng module này dừng ở ranh giới tích hợp runtime/server. Một server đang lắng nghe bình thường vẫn có thể đi cùng một handler mapping sai; hai lỗi thuộc hai lớp khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="container-internals-handoff">Bàn giao trách nhiệm sang phần nội bộ của Servlet và Reactive server</a>

<details>
<summary>Xem chi tiết</summary>
Tomcat, Jetty, Undertow và Reactor Netty có connector, handler, worker, event loop, queue, cách triển khai giao thức và mô hình tinh chỉnh riêng. Boot cung cấp property, factory và hook customizer để chạm vào các hệ thống đó nhưng không làm phần nội bộ của chúng trở thành di động giữa các server.

Hãy học đủ API riêng theo server để cấu hình yêu cầu qua Boot, rồi bàn giao phần suy luận sâu về container/runtime cho phần chịu trách nhiệm tương ứng. Cách này giữ nội dung học Boot tập trung vào lựa chọn, cấu hình, vòng đời và tích hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="network-security-handoff">Bàn giao trách nhiệm sang hạ tầng HTTP, TLS và reverse proxy</a>

<details>
<summary>Xem chi tiết</summary>
Boot có thể bật nén/HTTP/2, gắn dữ liệu chứng chỉ, tiêu thụ SSL bundle, xử lý forwarded headers và cung cấp các thiết lập proxy riêng theo server. Đây là các điểm tích hợp với những miền kiến thức lớn hơn.

Cơ chế giao thức HTTP, lý thuyết TLS/PKI, vận hành chứng chỉ, định tuyến reverse proxy, hành vi load balancer, độ tin cậy mạng và tinh chỉnh socket hệ điều hành vẫn thuộc hạ tầng/bảo mật. Người học Boot cần biết property/hook nào nối sang chúng và từ đâu trách nhiệm chuyên sâu bắt đầu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-runtime-end-to-end-model">Mô hình quyết định đầu-cuối của Boot web runtime</a>

<details>
<summary>Xem chi tiết</summary>
Khi thiết kế hoặc chẩn đoán Boot web runtime, có thể đi theo chuỗi quyết định:

```text
1. Tiến trình này cần `WebApplicationType` nào?
        ↓
2. Embedded server được hỗ trợ nào có trên classpath?
        ↓
3. Servlet/Reactive factory auto-configuration tương ứng có được áp dụng?
        ↓
4. server.* hoặc property riêng theo server có diễn đạt được yêu cầu?
        ↓
5. Nếu chưa, WebServerFactoryCustomizer nào là hook hẹp nhất?
        ↓
6. Môi trường triển khai có cần HTTP/2, TLS/SNI hoặc forwarded headers?
        ↓
7. Server sẽ dừng ra sao, có cần graceful shutdown?
        ↓
8. Vấn đề còn lại thực sự thuộc MVC/WebFlux, phần nội bộ server
   hay hạ tầng mạng/bảo mật?
```

Mô hình này nối các quyết định đặc trưng của Boot từ lúc khởi động tới lúc shutdown. Nó cũng tạo ranh giới chẩn đoán: xác định giai đoạn cuối cùng đã chắc chắn hoạt động, rồi tiếp tục ở phần chịu trách nhiệm tương ứng thay vì xem mọi lỗi web là lỗi controller hoặc lỗi server.

### Tài liệu tham khảo

- [Spring Boot 3.3 Reference — Web](https://docs.spring.io/spring-boot/3.3/reference/web/)
- [Spring Boot 3.3 How-to — Embedded Web Servers](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html)

</details>

- [Quay lại đầu trang](#back-to-top)
