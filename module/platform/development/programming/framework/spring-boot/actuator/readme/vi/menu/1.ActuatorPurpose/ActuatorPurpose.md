<a id="back-to-top"></a>

# Vì sao Spring Boot Actuator tồn tại

## Menu
- [Actuator đóng vai trò gì trong một ứng dụng Boot đang chạy?](#actuator-role)
- [Actuator giải quyết bài toán vận hành production nào?](#actuator-problem)
- [Bề mặt quản trị dành cho production là gì?](#production-management-surface)
- [Actuator liên hệ với trạng thái runtime thế nào mà không sở hữu vòng đời ứng dụng?](#runtime-state-vs-actuator-view)
- [Những phần nào không thuộc trách nhiệm của Actuator?](#actuator-boundaries)
- [Lộ trình học Actuator kết nối với nhau như thế nào?](#actuator-learning-path)

## <a id="actuator-role">Actuator đóng vai trò gì trong một ứng dụng Boot đang chạy?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot Actuator là lớp quản trị cho môi trường production, giúp người vận hành và công cụ bên ngoài quan sát hoặc tương tác với một ứng dụng Boot đang chạy qua một mô hình quản trị thống nhất. Cách thông dụng để bật khả năng này là thêm `spring-boot-starter-actuator`; Boot sau đó tự động cấu hình hạ tầng Actuator và các endpoint phù hợp với classpath và cấu hình hiện tại.

Actuator endpoint không phải API nghiệp vụ của ứng dụng. Ứng dụng vẫn xử lý request, job, message hay ca sử dụng nghiệp vụ qua các điểm vào bình thường. Actuator bổ sung một góc nhìn vận hành bên cạnh chúng: health, thông tin runtime, metrics, điều khiển logger, dữ liệu chẩn đoán và các thao tác quản trị riêng của ứng dụng.

Đó cũng là trọng tâm của module này. Actuator sở hữu cách Boot đưa trạng thái vận hành thành endpoint quản trị và cách endpoint được expose qua các công nghệ quản trị. Nó không thay thế hệ thống monitoring phía sau, Spring Security, vòng đời ứng dụng hay công cụ phân tích JVM.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Production-ready Features](https://docs.spring.io/spring-boot/3.3/reference/actuator/index.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="actuator-problem">Actuator giải quyết bài toán vận hành production nào?</a>

<details>
<summary>Xem chi tiết</summary>

Một tiến trình có thể vẫn chạy nhưng rất khó vận hành. Nếu không có bề mặt quản trị, người vận hành có thể biết cổng vẫn nhận kết nối nhưng không biết phụ thuộc có hoạt động tốt không, ứng dụng đã ghi meter nào, logger level nào đang có hiệu lực, hay thông tin cấu hình nào hữu ích cho sự cố hiện tại.

Một cách tự phát là tạo các controller nghiệp vụ như `/debug`, `/status` hoặc `/admin`. Cách đó khiến mỗi ứng dụng có hợp đồng phản hồi và quy tắc exposure khác nhau, đồng thời rất dễ trộn quyền quản trị với API nghiệp vụ. Mỗi nhóm cũng phải tự xây cơ chế tổng hợp health và quyền truy cập chẩn đoán.

Actuator chuẩn hóa phần việc thuộc Spring Boot bằng mô hình endpoint và tích hợp với trạng thái runtime/hạ tầng của Boot. Kết quả là một hợp đồng vận hành dễ dự đoán: công cụ hiểu các khái niệm quản trị quen thuộc, còn chủ sở hữu ứng dụng quyết định khả năng nào được bật, expose và phân quyền.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="production-management-surface">Bề mặt quản trị dành cho production là gì?</a>

<details>
<summary>Xem chi tiết</summary>

Bề mặt quản trị production (production management surface) là tập giao diện phục vụ vận hành ứng dụng thay vì thực hiện giao dịch nghiệp vụ. Với Actuator, bề mặt này có thể gồm góc nhìn chỉ đọc như health/metrics và operation có tác động như thay đổi logger level. Tùy endpoint, nó có thể được expose qua HTTP, JMX hoặc công nghệ quản trị được hỗ trợ khác.

Cần tách ba quyết định. Thứ nhất, khả năng của endpoint phải tồn tại. Thứ hai, endpoint phải được expose qua một công nghệ quản trị thì client từ xa mới tiếp cận được. Thứ ba, quyền truy cập qua mạng/JMX vẫn phải có chính sách phân quyền phù hợp. “Endpoint tồn tại” vì vậy không đồng nghĩa “mọi người đều được gọi”.

Bề mặt quản trị cũng có đối tượng sử dụng khác API nghiệp vụ: người vận hành, nền tảng triển khai, công cụ monitoring và người xử lý sự cố. Đối tượng này quyết định dữ liệu nào hữu ích, dữ liệu nào nhạy cảm và write operation nào cần kiểm soát chặt.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-state-vs-actuator-view">Actuator liên hệ với trạng thái runtime thế nào mà không sở hữu vòng đời ứng dụng?</a>

<details>
<summary>Xem chi tiết</summary>

Actuator thường expose dữ liệu mà nguồn trạng thái thực sự thuộc phân hệ khác. Ví dụ rõ nhất là application availability: `application-runtime` sở hữu thời điểm liveness/readiness chuyển trạng thái, còn Actuator chuyển các trạng thái đó thành health contributor và góc nhìn HTTP phục vụ probe.

Mẫu này xuất hiện ở nhiều nơi. Tích hợp logging của Boot quyết định cách logging được khởi tạo/cấu hình; loggers endpoint đọc và thay đổi cấu hình logger trong lúc chạy. Micrometer giữ trạng thái meter; metrics endpoint cung cấp góc nhìn chẩn đoán trên các meter đó. `ApplicationStartup` có thể thu các bước khởi động; startup endpoint chỉ expose dữ liệu khi ứng dụng đã được cấu hình để thu thập.

Mô hình tư duy “Actuator là lớp chiếu quản trị” rất hữu ích khi xử lý sự cố. Nếu endpoint đang phản ánh đúng `REFUSING_TRAFFIC`, chỉnh Actuator không sửa được nguyên nhân khiến ứng dụng chưa sẵn sàng; phải quay về phân hệ sở hữu trạng thái đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="actuator-boundaries">Những phần nào không thuộc trách nhiệm của Actuator?</a>

<details>
<summary>Xem chi tiết</summary>

Actuator nằm ở nhiều ranh giới nhưng không sở hữu toàn bộ miền bên dưới. Nó expose health và metrics, trong khi chiến lược đặt tên metrics, dashboard, quy tắc cảnh báo, lưu trữ dài hạn, kiến trúc tracing và luồng telemetry thuộc observability. Nó expose/thay đổi logger level, nhưng quá trình khởi tạo logging của Boot thuộc `application-runtime` và vận hành logging tập trung thuộc hạ tầng logging.

Khi Spring Security có mặt, Boot có thể tự động cấu hình bảo mật cho bề mặt quản trị HTTP; tuy nhiên authentication, quy tắc authorization, filter chain, định danh và thiết kế chính sách truy cập vẫn thuộc Spring Security. Actuator có thể trả thread/heap dump, nhưng phân tích deadlock, áp lực cấp phát, object retention hoặc hành vi GC thuộc chẩn đoán JVM/runtime.

Actuator cũng không sở hữu vòng đời ứng dụng. Health/probe có thể phản ánh `ApplicationAvailability`, nhưng lifecycle event, runner, chuyển đổi readiness, xử lý lỗi và ngữ nghĩa shutdown vẫn thuộc `application-runtime`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="actuator-learning-path">Lộ trình học Actuator kết nối với nhau như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Lộ trình bắt đầu bằng abstraction của endpoint vì các phần phía sau sẽ dễ hiểu hơn khi người học tách được “khả năng endpoint”, “công nghệ exposure” và “quyền truy cập từ xa”. Chương kế tiếp biến abstraction đó thành các quy tắc cụ thể về enablement, availability và exposure qua HTTP/JMX.

Health được học sớm vì đây là hợp đồng vận hành phổ biến nhất và nhiều hành vi cao hơn của Boot dựa trên health contributor/group. Liveness/readiness tiếp theo đóng vai trò cầu nối với trạng thái availability đã học ở `application-runtime`. Info/environment cho thấy dữ liệu quản trị vừa hữu ích vừa có thể nhạy cảm.

Metrics, điều khiển logger và các dump endpoint minh họa ranh giới giữa việc kiểm tra trực tiếp ứng dụng với phân tích observability/JVM chuyên sâu. Custom endpoint dạy cách mở rộng mô hình quản trị; chương về truy cập/bảo mật đặt bề mặt đó vào đúng ranh giới mạng và bảo mật; cuối cùng phần tổng hợp nối tất cả thành một mô hình xử lý sự cố production.

</details>

- [Quay lại đầu trang](#back-to-top)
