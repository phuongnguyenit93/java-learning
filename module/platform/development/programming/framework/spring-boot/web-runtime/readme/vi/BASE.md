# Web Runtime trong Spring Boot

Module này giải thích tầng Spring Boot biến một ứng dụng thành một tiến trình web đang chạy: Boot xác định loại ứng dụng web như thế nào, chọn và khởi động embedded server ra sao, áp dụng cấu hình server, cung cấp các khả năng HTTP ở phía server, sử dụng cấu hình TLS, thích nghi khi chạy sau reverse proxy và dừng server có kiểm soát như thế nào.

Người học nên đã nắm các module Spring Boot fundamentals, externalized configuration, auto-configuration và application-runtime. Cơ chế xử lý request của Spring MVC và Spring WebFlux vẫn thuộc phần học web/reactive của Spring Framework; module này tập trung vào runtime do Boot quản lý bao quanh các web stack đó.

Lộ trình học bắt đầu từ ranh giới của web runtime và `WebApplicationType`, sau đó đi từ lựa chọn embedded server tới auto-configuration cho Servlet server và Reactive server. Tiếp theo là cấu hình `server.*`, tùy biến bằng code, các khả năng HTTP ở tầng server, TLS phía server, triển khai sau proxy/forwarded headers, graceful shutdown và phần tổng hợp cuối về web defaults của Boot cùng các điểm bàn giao trách nhiệm.

Ưu tiên dùng configuration properties khi Boot đã cung cấp cách biểu diễn hành vi cần thiết. Chỉ chuyển sang `WebServerFactoryCustomizer` hoặc tự cung cấp web-server factory khi mô hình cấu hình bằng property chưa đủ, đồng thời giữ các thiết lập riêng gắn với đúng server đã chọn.

Sau module này, người học cần có thể suy luận từ loại ứng dụng và classpath tới server mà Boot khởi động, hiểu tầng Boot nào cấu hình server đó, chọn đúng cơ chế cấu hình/tùy biến và nhận biết khi một câu hỏi thực chất thuộc Spring MVC/WebFlux, servlet container hoặc hạ tầng HTTP/TLS/proxy tổng quát.
