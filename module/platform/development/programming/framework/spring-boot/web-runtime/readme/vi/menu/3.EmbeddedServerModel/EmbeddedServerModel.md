<a id="back-to-top"></a>

# Mô hình embedded server

## Menu
- [Web starter và embedded server mặc định](#starter-server-defaults)
- [Các lựa chọn server được hỗ trợ cho Servlet và Reactive](#supported-server-choices)
- [Thay embedded server dependency mặc định](#replacing-default-server)
- [Vai trò của WebServerFactory](#web-server-factory-role)
- [Context ứng dụng web khởi động server như thế nào?](#web-server-context-startup)
- [Boot chọn server và ranh giới với phần nội bộ của server](#server-selection-boundary)

## <a id="starter-server-defaults">Web starter và embedded server mặc định</a>

<details>
<summary>Xem chi tiết</summary>
`spring-boot-starter-web` kéo Tomcat vào qua `spring-boot-starter-tomcat`, vì vậy Tomcat là embedded server mặc định theo quy ước cho ứng dụng Servlet. `spring-boot-starter-webflux` kéo Reactor Netty vào qua `spring-boot-starter-reactor-netty`, nên Reactor Netty là reactive server mặc định.

Đây là mặc định từ starter, không phải yêu cầu được mã hóa cứng. Boot web runtime được thiết kế để cách triển khai server có thể thay thế mà ứng dụng xung quanh vẫn giữ mô hình Boot.

### Tài liệu tham khảo

- [Spring Boot 3.3 How-to — Embedded Web Servers](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="supported-server-choices">Các lựa chọn server được hỗ trợ cho Servlet và Reactive</a>

<details>
<summary>Xem chi tiết</summary>
Với stack Servlet, Boot 3.3 hỗ trợ embedded Tomcat, Jetty và Undertow. Với stack reactive, Boot hỗ trợ Reactor Netty cùng các adapter reactive cho Tomcat, Jetty và Undertow.

Vì vậy cùng một tên server có thể xuất hiện trong hai mô hình runtime khác nhau. Ví dụ `TomcatServletWebServerFactory` và `TomcatReactiveWebServerFactory` là hai tích hợp khác nhau của Boot. Hãy chọn web stack trước, rồi mới chọn cách triển khai server cụ thể trong stack đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="replacing-default-server">Thay embedded server dependency mặc định</a>

<details>
<summary>Xem chi tiết</summary>
Thay server trước hết là một quyết định về dependency. Loại bỏ hoặc thay server mặc định do starter đưa vào và thêm starter của server mong muốn. Ví dụ ứng dụng Servlet có thể thay `spring-boot-starter-tomcat` bằng `spring-boot-starter-jetty`; ứng dụng WebFlux có thể thay Reactor Netty bằng Undertow.

Cách này giữ nguyên mô hình auto-configuration của Boot. Classpath giờ cung cấp một cách triển khai server được hỗ trợ khác nên cấu hình factory tương ứng trở thành lựa chọn phù hợp. Không cần viết mã bootstrap server chỉ để thực hiện một lần đổi dependency mà Boot đã hỗ trợ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-server-factory-role">Vai trò của WebServerFactory</a>

<details>
<summary>Xem chi tiết</summary>
`WebServerFactory` là lớp trừu tượng của Boot dùng để tạo `WebServer` lúc runtime. Ứng dụng Servlet làm việc với `ServletWebServerFactory`; ứng dụng Reactive làm việc với `ReactiveWebServerFactory`. Factory cụ thể như `TomcatServletWebServerFactory` hoặc `NettyReactiveWebServerFactory` nối lớp trừu tượng đó với cách triển khai server tương ứng.

Factory là điểm tùy biến trước khi server thực sự tồn tại. Property và `WebServerFactoryCustomizer` thay đổi factory; sau đó context ứng dụng web mới yêu cầu factory tạo server.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-server-context-startup">Context ứng dụng web khởi động server như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
`ApplicationContext` nhận biết web của Boot điều phối việc tạo server trong quá trình refresh context. Khi factory và các thành phần cần thiết đã sẵn sàng, context lấy `WebServer` từ factory và khởi động nó như một phần của vòng đời do Boot quản lý.

Cổng thực tế có thể chỉ biết sau khi server khởi tạo, đặc biệt với `server.port=0`. Boot phát `WebServerInitializedEvent` sau khi server sẵn sàng, còn `WebServerApplicationContext` cho phép truy cập server nếu cần quan sát runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="server-selection-boundary">Boot chọn server và ranh giới với phần nội bộ của server</a>

<details>
<summary>Xem chi tiết</summary>
Boot chịu trách nhiệm cho hợp đồng tích hợp: server factory nào đủ điều kiện, cấu hình nào được áp dụng, customizer tham gia ra sao và server nối vào quá trình khởi động/dừng của ứng dụng như thế nào. Boot không định nghĩa lại cách Tomcat connector, Jetty handler, Undertow worker hay Netty event loop hoạt động bên trong.

Khi bắt buộc dùng phần nội bộ riêng theo server, hãy đi qua hook tùy biến hẹp nhất của Boot đáp ứng yêu cầu rồi tiếp tục kiến thức sâu ở module sở hữu server/runtime đó. Cách này giúp ứng dụng giữ khả năng chuyển đổi tốt hơn và tránh phụ thuộc chi tiết triển khai ở những nơi không cần thiết.

</details>

- [Quay lại đầu trang](#back-to-top)
