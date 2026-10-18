<a id="back-to-top"></a>

# Actuator trong kiến trúc observability production

## Menu
- [Luồng vận hành Actuator từ đầu đến cuối kết nối với nhau như thế nào?](#actuator-production-flow)
- [Khi nào nên dùng Actuator endpoint và khi nào nên dùng công cụ telemetry bên ngoài?](#direct-management-vs-telemetry)
- [Health, metrics và chẩn đoán logging liên hệ với nhau thế nào mà không trở thành một hệ thống duy nhất?](#health-metrics-logging-relation)
- [Actuator bàn giao sang hạ tầng observability bên ngoài ở đâu?](#external-observability-handoff)
- [Phân loại lỗi quản trị Actuator như thế nào trước khi chọn cách sửa?](#management-troubleshooting-model)
- [Module hoặc miền nào sở hữu lớp chi tiết tiếp theo?](#actuator-end-to-end-boundaries)

## <a id="actuator-production-flow">Luồng vận hành Actuator từ đầu đến cuối kết nối với nhau như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Luồng Actuator từ đầu đến cuối bắt đầu từ trạng thái vận hành và khả năng quản trị bên trong ứng dụng. Các thành phần Boot/ứng dụng tạo bằng chứng health, meter, trạng thái logging, góc nhìn cấu hình, trạng thái availability hoặc dữ liệu quản trị tùy chỉnh. Actuator endpoint chiếu phần được chọn thành mô hình endpoint.

Enablement quyết định khả năng có tồn tại. Exposure chọn khả năng tiếp cận qua HTTP/JMX. Vị trí mạng và Spring Security quyết định ai có thể tới/gọi bề mặt đó. Hệ thống bên ngoài sau đó dùng endpoint trực tiếp hoặc nhận telemetry được export qua tích hợp Micrometer.

Khi sự cố xảy ra, hãy đi theo các lớp đó: trạng thái nền có tồn tại không, endpoint có khả dụng/được expose không, định tuyến/bảo mật có cho phép truy cập không, rồi bằng chứng trả về đang chỉ sang phân hệ nào.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="direct-management-vs-telemetry">Khi nào nên dùng Actuator endpoint và khi nào nên dùng công cụ telemetry bên ngoài?</a>

<details>
<summary>Xem chi tiết</summary>

Dùng Actuator endpoint trực tiếp khi nhiệm vụ là kiểm tra/quản trị một ứng dụng đang chạy ngay lúc này: xem health contributor, kiểm tra meter/tag, đổi logger level, lấy thread dump hoặc gọi custom operation bảo trì.

Dùng hạ tầng telemetry khi bài toán kéo dài theo thời gian hoặc qua nhiều instance: lưu metrics, tổng hợp replica, truy vấn log tập trung, liên kết trace, cảnh báo theo xu hướng, dựng dashboard hoặc phân tích năng lực. Hệ thống đó liên tục thu thập/lưu dữ liệu để người vận hành không phải polling từng ứng dụng.

Hai cách bổ sung nhau. Dashboard có thể phát hiện latency bất thường; sau đó người vận hành dùng Actuator kiểm tra một instance cụ thể. Actuator cung cấp bằng chứng/điều khiển cục bộ, còn hạ tầng observability cung cấp lịch sử và phân tích toàn hệ thống.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="health-metrics-logging-relation">Health, metrics và chẩn đoán logging liên hệ với nhau thế nào mà không trở thành một hệ thống duy nhất?</a>

<details>
<summary>Xem chi tiết</summary>

Health, metrics và chẩn đoán logging mô tả các mặt khác nhau của cùng một hệ thống đang chạy. Health nén các điều kiện được chọn thành trạng thái vận hành. Metrics biểu diễn measurement dạng số theo thời gian hoặc trạng thái instrument. Log ghi các event/ngữ cảnh rời rạc. Thread/heap dump cung cấp bằng chứng JVM tại một thời điểm với độ sâu lớn hơn.

Các tín hiệu nên hỗ trợ kiểm chứng lẫn nhau nhưng không bị ép thành một mô hình. Readiness có thể lỗi vì tài nguyên bắt buộc không khả dụng; metrics cho thấy error tăng; log chứa exception nguyên nhân. Mỗi bề mặt có chi phí, cardinality và đối tượng sử dụng riêng.

Không nên nhét toàn bộ chiến lược monitoring vào detail của `HealthIndicator` hoặc dùng lượng log thay metrics. Giữ đúng vai trò giúp suy luận khi xử lý sự cố và tích hợp observability bên ngoài rõ hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="external-observability-handoff">Actuator bàn giao sang hạ tầng observability bên ngoài ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot Actuator tích hợp ứng dụng với hạ tầng metrics/observation của Micrometer, endpoint quản trị và các bề mặt chẩn đoán được chọn. Hạ tầng observability bên ngoài sở hữu collection/exporter, lưu trữ bền vững, truy vấn qua nhiều instance, dashboard, alerting, trace backend, lập chỉ mục log, retention và luồng xử lý sự cố.

Boot có thể tự động cấu hình registry hoặc tracing bridge khi dependency phù hợp có mặt, nhưng Actuator không vì thế sở hữu kiến trúc Prometheus, thiết kế triển khai OpenTelemetry, luồng log hoặc ngữ nghĩa tracing. Các hệ thống đó có vấn đề riêng về mở rộng, bảo mật và độ tin cậy.

Điểm bàn giao vì vậy rất rõ: Actuator expose/tích hợp trạng thái vận hành cục bộ của ứng dụng; các hệ thống observability vận chuyển, lưu giữ, liên kết và phân tích telemetry trên toàn môi trường.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Observability](https://docs.spring.io/spring-boot/3.3/reference/actuator/observability.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="management-troubleshooting-model">Phân loại lỗi quản trị Actuator như thế nào trước khi chọn cách sửa?</a>

<details>
<summary>Xem chi tiết</summary>

Trước khi đổi cấu hình, hãy phân loại lỗi quản trị. URL bị thiếu thì kiểm tra endpoint enablement, exposure, base path và điều kiện tiên quyết. Endpoint tồn tại nhưng bị từ chối truy cập thì kiểm tra khả năng tiếp cận qua mạng và Spring Security. Phản hồi thiếu detail thì kiểm tra mức hiển thị riêng của endpoint hoặc sanitization.

Nếu endpoint trả phản hồi hợp lệ nhưng trạng thái không healthy, hãy rời lớp quản trị và điều tra contributor/phân hệ tạo trạng thái đó. Nếu metrics bị thiếu, hãy xác định instrumentation/registry binding có tạo meter chưa trước khi chỉnh exposure. Nếu dump tải thành công, phần phân tích chuyển sang công cụ JVM.

Cách phân loại này tránh thay đổi cấu hình liên tục. Cách sửa đúng phụ thuộc vấn đề nằm ở việc tạo endpoint, exposure, authorization, biểu diễn, trạng thái runtime nền hay việc tiêu thụ dữ liệu của hệ thống observability bên ngoài.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="actuator-end-to-end-boundaries">Module hoặc miền nào sở hữu lớp chi tiết tiếp theo?</a>

<details>
<summary>Xem chi tiết</summary>

Actuator sở hữu mô hình endpoint quản trị production của Boot, tích hợp enablement/exposure, mô hình health, health group cho availability, góc nhìn quản trị info/environment, metrics endpoint, điều khiển logger, việc cung cấp endpoint chẩn đoán, cách viết custom endpoint và tích hợp quyền truy cập bề mặt quản trị.

`application-runtime` sở hữu lifecycle event, chuyển đổi readiness/liveness, quá trình khởi tạo logging của Boot, thực thi task và shutdown. `externalized-configuration` sở hữu phân giải/binding property. Spring Security sở hữu cơ chế authentication/authorization. Micrometer và miền observability sở hữu thiết kế metric/observation, luồng telemetry, lưu trữ, dashboard, alerting và hệ thống tracing.

Miền phân tích JVM/runtime xử lý việc diễn giải thread/heap, còn `web-runtime` sở hữu hành vi embedded server. Biết các điểm bàn giao này là một phần của việc hiểu Actuator: endpoint thường cho biết nên nhìn tiếp ở đâu nhưng không hấp thụ trách nhiệm của phân hệ nó quan sát.

</details>

- [Quay lại đầu trang](#back-to-top)
