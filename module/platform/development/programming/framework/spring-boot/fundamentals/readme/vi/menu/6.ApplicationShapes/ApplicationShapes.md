<a id="back-to-top"></a>

# Các dạng ứng dụng và mô hình embedded server

## Menu
- [Spring Boot có thể khởi động những dạng ứng dụng nào?](#application-shapes)
- [Non-web, Servlet và Reactive khác nhau thế nào ở mức tổng quan?](#non-web-servlet-reactive)
- [Embedded server có ý nghĩa gì trong một ứng dụng Boot?](#embedded-server-mental-model)
- [Web starter có thể thay đổi dạng runtime như thế nào?](#classpath-changes-runtime-shape)
- [Tổng quan embedded server chuyển sang module nào?](#web-runtime-handoff)

## <a id="application-shapes">Spring Boot có thể khởi động những dạng ứng dụng nào?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot có thể khởi động nhiều dạng ứng dụng. Cùng lớp bootstrap `SpringApplication` có thể tạo non-web context, Servlet web application hoặc reactive web application tùy vào classpath đang có và thiết lập ứng dụng tường minh.

Điều này sửa một cách hiểu tắt dễ gây nhầm: "Spring Boot nghĩa là có web server". Boot là lớp bootstrap và tích hợp cho ứng dụng; web server chỉ xuất hiện khi ứng dụng có dạng runtime web.

Ở mức Fundamentals, hãy xem dạng ứng dụng như một lựa chọn runtime cấp cao:

```text
non-web      → ApplicationContext không có web server
Servlet web  → Servlet web ApplicationContext + embedded Servlet server integration
reactive web → reactive web ApplicationContext + reactive server integration
```

Module `web-runtime` sở hữu quy tắc nhận diện và cấu hình server chi tiết.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="non-web-servlet-reactive">Non-web, Servlet và Reactive khác nhau thế nào ở mức tổng quan?</a>

<details>
<summary>Xem chi tiết</summary>

Ứng dụng **non-web** vẫn có Boot bootstrap, cấu hình, dependency injection và các tích hợp Spring/Boot khác, nhưng không cần lắng nghe HTTP request. Tiến trình batch, ứng dụng command và background worker có thể nằm trong dạng này.

Ứng dụng **Servlet** dùng Servlet web stack của Spring, thường là Spring MVC, và chạy với Servlet-capable web application context. Ứng dụng **reactive** dùng reactive web stack, thường là Spring WebFlux, cùng reactive web application context.

Khi Boot suy luận type từ classpath, Spring MVC được ưu tiên nếu cả MVC và WebFlux cùng hiện diện. Ứng dụng có thể tường minh chọn `WebApplicationType` khác khi đó là chủ ý.

Các nhãn này mô tả application context và dạng runtime của Boot. Chúng không thay thế nội dung Spring Framework về MVC request handling, WebFlux programming, controller, codec hay reactive semantics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="embedded-server-mental-model">Embedded server có ý nghĩa gì trong một ứng dụng Boot?</a>

<details>
<summary>Xem chi tiết</summary>

Embedded server nghĩa là HTTP server được khởi động và quản lý như một phần của tiến trình ứng dụng, thay vì bắt buộc ứng dụng phải được copy vào một external server vận hành tách biệt như mô hình triển khai duy nhất.

Với executable Servlet application điển hình, có thể hình dung:

```text
java process
└── Boot application
    ├── Spring ApplicationContext
    └── embedded Servlet web server
```

Mô hình này giúp web application có thể chạy bằng command hướng ứng dụng giống các chương trình JVM khác, chẳng hạn `java -jar ...`. Boot điều phối tích hợp server cùng lifecycle của context.

Embedded không có nghĩa "Boot tự viết server từ đầu". Boot tích hợp các server implementation được hỗ trợ. Lựa chọn server, port, connector, TLS, hành vi proxy và graceful shutdown thuộc module `web-runtime`.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Embedded Web Servers](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classpath-changes-runtime-shape">Web starter có thể thay đổi dạng runtime như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Thêm web starter làm classpath thay đổi, mà classpath là một trong các tín hiệu Boot dùng khi chọn dạng ứng dụng. Project trước đó không có web stack có thể trở nên đủ điều kiện cho web application context và embedded-server auto-configuration sau khi thêm web starter.

Đây là ví dụ cụ thể cho mối quan hệ đã học:

```text
thêm starter
   ↓
thư viện/class mới trở nên sẵn có
   ↓
Boot phát hiện khả năng ứng dụng khác
   ↓
dạng runtime và automatic configuration có thể thay đổi
```

Chiều ngược lại cũng hữu ích khi debug. Nếu một ứng dụng đáng lẽ non-web lại tự khởi động server, hãy kiểm tra dependency tree và classpath xem có web stack hay không trước khi giả định server property là nguyên nhân gốc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-runtime-handoff">Tổng quan embedded server chuyển sang module nào?</a>

<details>
<summary>Xem chi tiết</summary>

Các chi tiết về nhận diện loại web application, auto-configuration của embedded server, tùy biến server, TLS, proxy và graceful shutdown thuộc module `web-runtime`.

Module đó trả lời các câu hỏi như server implementation nào được chọn, `server.*` property ảnh hưởng nó ra sao, SSL/TLS được kết nối thế nào, forwarded header được xử lý thế nào khi đứng sau proxy, và server tham gia graceful shutdown ra sao.

Fundamentals chỉ giữ mô hình tư duy có thể tái sử dụng: ứng dụng Boot có thể non-web hoặc web; web runtime có thể nằm trong cùng tiến trình; lựa chọn dependency/classpath có thể ảnh hưởng dạng nào sẵn có.

</details>

- [Quay lại đầu trang](#back-to-top)
