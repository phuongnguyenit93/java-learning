# Spring Boot Actuator

Spring Boot Actuator bổ sung một bề mặt quản trị hướng tới môi trường production cho ứng dụng Boot đang chạy. Module này giải thích cách Actuator đưa một phần trạng thái vận hành ra ngoài qua endpoint, cách việc bật endpoint và exposure quyết định endpoint có khả dụng hay không, đồng thời kết nối health, metrics, chẩn đoán logging và thao tác quản trị tùy chỉnh mà không biến Actuator thành một nền tảng observability.

## Bạn sẽ học gì?

Bạn sẽ học mô hình endpoint của Actuator cùng cách expose qua Web/JMX, suy luận về HealthContributor và trạng thái health tổng hợp, kết nối các nhóm health liveness/readiness với trạng thái availability của ứng dụng, quan sát dữ liệu info và environment một cách an toàn, dùng metrics endpoint ở đúng ranh giới tích hợp Micrometer, làm việc với loggers và các endpoint chẩn đoán hướng tới JVM, tạo endpoint quản trị tùy chỉnh, đồng thời phân biệt exposure endpoint với phân quyền và vị trí mạng của bề mặt quản trị.

## Kiến thức cần có

Bạn nên nắm kiến thức nền Spring Boot, externalized configuration và mô hình `application-runtime`. Kiến thức cơ bản về HTTP, JMX, logging, metrics và bảo mật sẽ hữu ích, nhưng module này không dạy lại lý thuyết observability tổng quát, cơ chế Spring Security, kỹ thuật phân tích logging, phân tích JVM dump hay phần nội bộ của vòng đời ứng dụng.

## Lộ trình học

1. Xác định vì sao Actuator tồn tại và bề mặt quản trị dành cho production chịu trách nhiệm phần nào.
2. Học mô hình endpoint trước khi đi vào bật/tắt, tính khả dụng, exposure qua HTTP/JMX và bề mặt web `/actuator`.
3. Xây mô hình health từ các contributor và cơ chế tổng hợp trạng thái, sau đó nối các nhóm health với trạng thái liveness/readiness của Boot.
4. Quan sát các endpoint info/environment và xem dữ liệu nhạy cảm như một vấn đề về quyền truy cập.
5. Dùng metrics endpoint nhưng giữ thiết kế meter, xuất telemetry và hệ thống monitoring phía sau ngoài phạm vi sở hữu của Actuator.
6. Dùng loggers, thread dump và heap dump endpoint như các bề mặt quản trị/chẩn đoán, trong khi vận hành logging và phân tích JVM vẫn thuộc các miền chuyên trách.
7. Chỉ tạo endpoint tùy chỉnh cho nhu cầu quản trị vận hành, sau đó đặt bề mặt quản trị có chủ đích với tập endpoint exposure tối thiểu và ranh giới phân quyền rõ ràng.
8. Tổng hợp Actuator với trạng thái của `application-runtime` và hạ tầng observability bên ngoài thành một mô hình xử lý sự cố thống nhất.

## Ranh giới module

Module này sở hữu mô hình endpoint dành cho production của Spring Boot, tích hợp bật endpoint/exposure, mô hình health endpoint, góc nhìn liveness/readiness phía Actuator, các endpoint vận hành hướng tới info/environment, tích hợp metrics endpoint, bề mặt loggers và endpoint chẩn đoán, cơ chế tạo endpoint tùy chỉnh cùng ranh giới truy cập bề mặt quản trị. Vòng đời ứng dụng và quá trình chuyển trạng thái availability vẫn thuộc `application-runtime`; thứ tự ưu tiên và binding cấu hình thuộc `externalized-configuration`; thiết kế authentication/authorization thuộc Spring Security; thiết kế metrics, luồng telemetry, alerting, dashboard, vận hành logging và hệ thống observability phía sau thuộc các miền observability; phân tích thread/heap thuộc chẩn đoán JVM/runtime.
