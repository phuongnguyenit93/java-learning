<a id="back-to-top"></a>

# Quyền truy cập bề mặt quản trị và ranh giới bảo mật

## Menu
- [Vì sao tập endpoint được công khai từ xa nên được giữ tối thiểu?](#minimal-management-exposure)
- [Có thể tách web base path quản trị khỏi bề mặt ứng dụng như thế nào?](#management-base-path)
- [Cổng và địa chỉ quản trị riêng thay đổi vị trí mạng như thế nào?](#management-port-address)
- [Vì sao exposure endpoint và phân quyền là hai quyết định khác nhau?](#exposure-vs-authorization)
- [Boot cung cấp hành vi bảo mật nào khi có Spring Security?](#actuator-security-auto-configuration)
- [Tích hợp bảo mật của Actuator bàn giao sang Spring Security ở đâu?](#spring-security-handoff)

## <a id="minimal-management-exposure">Vì sao tập endpoint được công khai từ xa nên được giữ tối thiểu?</a>

<details>
<summary>Xem chi tiết</summary>

Endpoint quản trị an toàn nhất là endpoint mà môi trường triển khai không expose nếu không có lý do vận hành rõ ràng. Mặc định Boot 3.3 chỉ expose health qua HTTP/JMX theo định hướng đó. Mỗi endpoint thêm vào cần gắn với bên sử dụng, luồng xử lý sự cố hoặc yêu cầu tự động hóa.

Exposure tối thiểu giảm nguy cơ lộ thông tin và số operation đặc quyền cần rà soát bảo mật. Chính sách mạng cũng dễ hiểu hơn khi tập URL quản trị nhỏ, có mục đích rõ thay vì wildcard toàn bộ `/actuator`.

Cần rà soát exposure khi khả năng thay đổi. Thêm registry, repository, tính năng logging hoặc custom endpoint có thể làm dữ liệu endpoint thay đổi. Wildcard include tồn tại từ trước không nên khiến endpoint chẩn đoán mới tự động truy cập được từ xa sau khi nâng cấp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="management-base-path">Có thể tách web base path quản trị khỏi bề mặt ứng dụng như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

`management.endpoints.web.base-path` điều khiển tiền tố HTTP chung cho các Actuator web endpoint. Mặc định là `/actuator`, nhưng ứng dụng có thể chuyển bề mặt quản trị sang `/manage`; path từng endpoint cũng có thể ánh xạ lại khi quy ước hạ tầng yêu cầu. Khi `management.server.port` tạo server quản trị riêng, base path của endpoint này được tính tương đối với `management.server.base-path`.

Đổi path hữu ích cho định tuyến/tổ chức nhưng không phải ranh giới bảo mật. Kẻ tấn công không mất quyền tiếp cận chỉ vì `/actuator` đổi thành `/manage`. Authorization và kiểm soát mạng vẫn phải bảo vệ bề mặt này.

Path cũng độc lập với endpoint ID. health vẫn là health sau khi URL đổi. Cấu hình, phép kiểm tra monitoring, route của reverse proxy và tài liệu cần đồng bộ để người vận hành không nhầm path không khớp với lỗi endpoint.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Monitoring and Management over HTTP](https://docs.spring.io/spring-boot/3.3/reference/actuator/monitoring.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="management-port-address">Cổng và địa chỉ quản trị riêng thay đổi vị trí mạng như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

`management.server.port` có thể đặt các endpoint quản trị HTTP trên cổng khác server chính. Điều này tạo web server context quản trị riêng và cho hạ tầng định tuyến/tường lửa xử lý traffic vận hành độc lập với traffic nghiệp vụ. Trên server riêng đó, `management.server.base-path` xác định base path của server quản trị và `management.endpoints.web.base-path` được tính tương đối với nó.

Khi cổng quản trị khác, `management.server.address` có thể bind server đó vào interface cụ thể như localhost hoặc mạng vận hành nội bộ. Boot chỉ hỗ trợ chọn địa chỉ quản trị khác khi cổng quản trị khác cổng server chính.

Việc đặt riêng cũng thay đổi miền lỗi. Server quản trị hoạt động tốt không chứng minh connector của ứng dụng chính hoạt động tốt. Probe cần kiểm tra đường request chính có thể cần thêm path liveness/readiness trên cổng chính thay vì chỉ dựa vào listener quản trị.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exposure-vs-authorization">Vì sao exposure endpoint và phân quyền là hai quyết định khác nhau?</a>

<details>
<summary>Xem chi tiết</summary>

Exposure và authorization trả lời hai câu hỏi khác nhau. Exposure quyết định endpoint có thể truy cập qua công nghệ quản trị hay không. Authorization quyết định bên gọi đã tới được bề mặt đó có được gọi operation hay không. Endpoint có thể được expose nhưng bên gọi bị từ chối, hoặc được bật nhưng hoàn toàn không expose.

Vị trí mạng là một lớp độc lập khác. Cổng quản trị có thể chỉ truy cập được từ subnet vận hành trước khi lớp bảo mật của ứng dụng chạy. Sanitization và mức hiển thị health detail tiếp tục giảm dữ liệu bên gọi nhìn thấy.

Hãy thiết kế các lớp theo nguyên tắc phòng vệ nhiều lớp. Đổi path không phải authorization; có authentication không có nghĩa nên expose mọi endpoint ra Internet công khai; loại endpoint khỏi HTTP cũng không đồng nghĩa endpoint bị tắt khỏi ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="actuator-security-auto-configuration">Boot cung cấp hành vi bảo mật nào khi có Spring Security?</a>

<details>
<summary>Xem chi tiết</summary>

Khi Spring Security có trên classpath và ứng dụng chưa định nghĩa `SecurityFilterChain` riêng, Spring Boot cung cấp security auto-configuration cho Actuator. Trong Boot 3.3, các Actuator endpoint ngoài health được bảo vệ theo cấu hình bảo mật mặc định, còn health có thể tiếp tục phục vụ phép kiểm tra vận hành cơ bản.

Nếu ứng dụng định nghĩa bean `SecurityFilterChain` tùy chỉnh, Boot lùi khỏi bảo mật Actuator mặc định và ứng dụng chịu trách nhiệm về các quy tắc. Boot cung cấp request matcher hiểu Actuator như `EndpointRequest` để cấu hình Spring Security nhắm tới các endpoint quản trị.

Hệ quả vận hành rất quan trọng: khi nhóm thêm `SecurityFilterChain` riêng, cần chính sách rõ ràng cho Actuator; không nên giả định các hạn chế mặc định trước đó vẫn tồn tại.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Actuator Security](https://docs.spring.io/spring-boot/3.3/reference/actuator/endpoints.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-security-handoff">Tích hợp bảo mật của Actuator bàn giao sang Spring Security ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Actuator sở hữu khả năng endpoint, kênh exposure và điểm tích hợp Boot để cấu hình bảo mật có thể nhận diện request quản trị. Spring Security sở hữu cách định danh được authenticate và quyết định authorization được thực thi trong filter chain/quy tắc request.

Module này có thể giải thích health được cho phép rộng hơn trong một môi trường triển khai, còn loggers/env/heapdump/custom write operation cần bảo vệ mạnh hơn. Nó không dạy lại user, role, authentication provider, CSRF, chính sách session, OAuth2 resource server, thứ tự matcher hay method security.

Khi request trả 401/403 sau khi endpoint availability/exposure đã được xác nhận, việc điều tra đã đi vào chính sách Spring Security. Hãy theo `SecurityFilterChain` đang hoạt động và authentication context thay vì mở rộng Actuator exposure để “sửa” lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)
