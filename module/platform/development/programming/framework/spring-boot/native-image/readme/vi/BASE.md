# Spring Boot Native Image

Module này giải thích con đường ở tầng ứng dụng Spring Boot từ mô hình runtime JVM động thông thường sang native executable đã được chuẩn bị bằng AOT. Trọng tâm không phải chi tiết nội bộ của GraalVM compiler; mục tiêu là xây mô hình tư duy cho lập trình viên Boot về quá trình AOT, hệ quả closed-world, runtime hints, các đường build native được hỗ trợ, kiểm thử riêng cho native, khả năng tương thích của hệ sinh thái và quyết định triển khai dựa trên bằng chứng.

## Bạn sẽ học gì?

Bạn sẽ hiểu vì sao Spring AOT cần chạy trước biên dịch native, pipeline AOT tạo ra những đầu ra nào, giới hạn closed-world thay đổi các giả định vốn thường được trì hoãn đến runtime ra sao, cách `RuntimeHints` mô tả truy cập động mà phân tích tĩnh không thể tự suy ra, cách Boot tích hợp công cụ build native của Maven/Gradle và Buildpacks, cách kiểm thử có chủ đích những hành vi nhạy cảm với native và cách quyết định triển khai native có xứng đáng với chi phí build và khả năng tương thích hay không.

## Kiến thức cần có

Bạn nên hiểu runtime ứng dụng thông thường của Spring Boot, auto-configuration, công cụ build/đóng gói và mô hình kiểm thử Boot. Kiến thức cơ bản về reflection, proxy, resource và triển khai JVM sẽ hữu ích. Module này không dạy chi tiết nội bộ compiler/runtime của GraalVM, cơ chế Maven/Gradle tổng quát, phần nội bộ Docker/Buildpacks hay toàn bộ mô hình reflection/proxy của Java.

## Lộ trình học

1. Bắt đầu từ vấn đề biên dịch native tạo ra cho một ứng dụng Boot động và các lợi ích khiến AOT đáng cân nhắc.
2. Theo dõi Spring AOT từ mô hình ứng dụng đến mã nguồn, bytecode và hint metadata được tạo.
3. Hiểu hệ quả closed-world đối với classpath, bean graph, profile, property và các hành vi động khác.
4. Học cách runtime hints và reachability metadata của bên thứ ba mô tả truy cập động mà công cụ native-image không thể tự suy ra an toàn.
5. Build và chạy native executable hoặc native OCI image qua các đường build được Boot hỗ trợ.
6. Kiểm thử có chọn lọc hành vi nhạy cảm với AOT/native thay vì chuyển toàn bộ vòng phản hồi khỏi JVM.
7. Đánh giá lợi ích runtime cùng chi phí build, chất lượng metadata, mức hỗ trợ của dependency và rủi ro tương thích.
8. Tổng hợp bằng chứng về mức độ sẵn sàng và chọn giữa native executable, native container image hoặc triển khai JVM thông thường.

## Ranh giới module

Module này chịu trách nhiệm cho mô hình AOT/native ở tầng ứng dụng Spring Boot, runtime hints, suy luận về mức độ sẵn sàng cho native, các điểm bàn giao sang quá trình build native được Boot hỗ trợ và chiến lược xác thực riêng cho native. Boot plugin tổng quát, đóng gói executable, Buildpacks và cơ chế bàn giao image thuộc `build-tooling-packaging`; lifecycle runtime thông thường thuộc `application-runtime`; phần kiểm thử Boot rộng hơn thuộc `testing`; ngữ nghĩa của conditional auto-configuration thuộc `auto-configuration`; cơ chế reflection/proxy/class loading tổng quát của Java và chi tiết nội bộ compiler/runtime của GraalVM vẫn thuộc các module kiến thức chuyên trách.
