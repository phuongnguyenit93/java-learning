<a id="back-to-top"></a>

# Bật/tắt, tính khả dụng và exposure của endpoint

## Menu
- [Bật hoặc tắt một endpoint thay đổi điều gì?](#endpoint-enablement)
- [Exposure khác việc bật endpoint như thế nào?](#endpoint-exposure)
- [Khi nào endpoint thực sự khả dụng qua một công nghệ quản trị?](#endpoint-availability)
- [Endpoint được expose qua HTTP như thế nào?](#web-endpoint-surface)
- [Endpoint được expose qua JMX như thế nào?](#jmx-endpoint-surface)
- [Base path web mặc định /actuator nằm ở đâu trong mô hình endpoint?](#actuator-base-path)
- [Vì sao việc expose endpoint từ xa phải là một quyết định có chủ đích?](#exposure-defaults-and-risk)

## <a id="endpoint-enablement">Bật hoặc tắt một endpoint thay đổi điều gì?</a>

<details>
<summary>Xem chi tiết</summary>

Enablement trả lời câu hỏi khả năng quản trị của endpoint có tồn tại trong ứng dụng hay không. Trong Spring Boot 3.3, phần lớn endpoint tích hợp sẵn được bật mặc định, còn operation có tác động lớn như shutdown bị tắt mặc định. Có thể điều khiển từng endpoint bằng `management.endpoint.<id>.enabled`, hoặc đảo mặc định cho toàn bộ bằng `management.endpoints.enabled-by-default`.

Khi endpoint bị tắt, Boot loại endpoint đó khỏi application context chứ không chỉ ẩn URL. Điều này quan trọng vì mã/auto-configuration không còn có thể giả định endpoint bean tồn tại. Một chiến lược thu hẹp khả năng là tắt theo mặc định rồi chỉ bật các endpoint mà môi trường triển khai thực sự cần.

Vì vậy enablement nên trả lời “khả năng quản trị này có tồn tại không?”. Nó không thay cho quyết định endpoint được expose qua kênh nào hoặc bên gọi nào được phép dùng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="endpoint-exposure">Exposure khác việc bật endpoint như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Exposure trả lời câu hỏi khác: endpoint đã bật có thể được truy cập qua công nghệ quản trị nào? Spring Boot 3.3 có các property include/exclude riêng cho web và JMX. Ví dụ `management.endpoints.web.exposure.include` chọn endpoint ID được expose qua HTTP, còn exclude có thể loại ID ra và có độ ưu tiên cao hơn include.

Mặc định chỉ health endpoint được expose qua HTTP và JMX. Vì vậy mở rộng danh sách include là một quyết định production rõ ràng. Wildcard có thể chọn tất cả endpoint, nhưng điều đó phải kéo theo việc rà soát dữ liệu nhạy cảm và endpoint có write operation, thay vì trở thành lối tắt mặc định.

Endpoint có thể được bật nhưng không expose. Trạng thái này hoàn toàn hợp lệ khi khả năng chỉ được dùng nội bộ hoặc một kênh cụ thể không nên công khai.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Exposing Endpoints](https://docs.spring.io/spring-boot/3.3/reference/actuator/endpoints.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="endpoint-availability">Khi nào endpoint thực sự khả dụng qua một công nghệ quản trị?</a>

<details>
<summary>Xem chi tiết</summary>

Trong mô hình endpoint của Boot 3.3, endpoint khả dụng qua một công nghệ quản trị khi nó vừa được bật vừa được expose qua công nghệ đó. Auto-configuration của endpoint tích hợp sẵn phụ thuộc availability, nên endpoint bị tắt hoặc không expose có thể không tạo ra thành phần quản trị mà người học đang tìm.

Availability còn phụ thuộc kênh. health có thể được expose qua HTTP nhưng không qua JMX hoặc ngược lại. JMX có thêm điều kiện tiên quyết ở mức nền tảng vì hỗ trợ Spring JMX phải được bật thì việc expose endpoint qua JMX mới có ý nghĩa.

Khi endpoint “biến mất”, hãy kiểm tra theo thứ tự: endpoint có được bật không, kênh quản trị có hoạt động không, endpoint ID có nằm trong include và không bị exclude không; sau đó mới xét định tuyến mạng hoặc authorization. Trình tự này tránh xem mọi 404/MBean bị thiếu là lỗi bảo mật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-endpoint-surface">Endpoint được expose qua HTTP như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Với ứng dụng web, Actuator có thể expose endpoint qua Spring MVC, Spring WebFlux hoặc Jersey. Nếu đồng thời có Jersey và Spring MVC thì Spring MVC được dùng. Lớp web ánh xạ endpoint ID/operation thành route HTTP và cách biểu diễn; Jackson cần có để nhận đúng biểu diễn JSON như tài liệu REST API mô tả.

HTTP exposure được cấu hình độc lập với bề mặt controller nghiệp vụ. Endpoint nằm trong `management.endpoints.web.exposure.include` trở thành một phần của bề mặt quản trị web khi endpoint đã bật và các điều kiện tiên quyết được thỏa mãn. Operation kiểu read/write/delete được hạ tầng web của Actuator chuyển thành HTTP method phù hợp.

Chương này chỉ sở hữu ánh xạ quản trị. Cơ chế xử lý request bên trong Dispatcher/controller/reactive vẫn thuộc curriculum Web/Reactive của Spring Framework.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jmx-endpoint-surface">Endpoint được expose qua JMX như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

JMX là một cách biểu diễn quản trị khác. Hỗ trợ Spring JMX mặc định bị tắt và có thể bật bằng `spring.jmx.enabled=true`. Khi hoạt động, Actuator endpoint được chọn bởi cấu hình exposure của JMX sẽ được công bố thành MBean, mặc định dưới domain `org.springframework.boot`.

Endpoint ID tham gia định danh MBean, còn endpoint operation trở thành operation quản trị thay vì HTTP request. Điều này cho thấy lợi ích của mô hình endpoint độc lập công nghệ: cùng một khả năng logic có thể xuất hiện qua kênh quản trị không phải web.

JMX không tự động “an toàn hơn” chỉ vì nó không phải HTTP. Kết nối JMX từ xa, credential, chính sách mạng và cấu hình nền tảng vẫn cần được thiết kế. Cơ chế JMX tổng quát nằm ngoài curriculum Actuator này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="actuator-base-path">Base path web mặc định /actuator nằm ở đâu trong mô hình endpoint?</a>

<details>
<summary>Xem chi tiết</summary>

Với quản trị qua HTTP, quy ước mặc định đặt route dưới `/actuator`. Endpoint có ID health thường nằm ở `/actuator/health` khi được expose. `management.endpoints.web.base-path` đổi tiền tố chung, còn `management.endpoints.web.path-mapping` có thể đổi path của từng endpoint.

Base path là vấn đề định tuyến, không phải định danh endpoint. Đổi `/actuator` thành `/manage` không đổi tên health endpoint và cũng không đổi các property vẫn dùng ID health. Tách biệt này hữu ích khi reverse proxy hoặc quy ước định tuyến của tổ chức yêu cầu bố cục URL khác.

Khi endpoint quản trị dùng cùng cổng với server chính, base path được tính tương đối với web context/base path của ứng dụng. Nếu `management.server.port` tạo server quản trị riêng, `management.endpoints.web.base-path` được tính tương đối với `management.server.base-path` trên server quản trị đó.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Monitoring and Management over HTTP](https://docs.spring.io/spring-boot/3.3/reference/actuator/monitoring.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exposure-defaults-and-risk">Vì sao việc expose endpoint từ xa phải là một quyết định có chủ đích?</a>

<details>
<summary>Xem chi tiết</summary>

Actuator endpoint có thể tiết lộ cấu hình nội bộ, cấu trúc bean, runtime metrics, điều khiển logging, trạng thái thread hoặc nội dung heap dạng binary. Chính vì chúng hữu ích khi có sự cố nên exposure rộng cũng tạo rủi ro. Mặc định exposure hẹp của Boot buộc chủ sở hữu ứng dụng chủ động chọn khả năng nào cần truy cập từ xa.

Nên dùng danh sách include phản ánh nhu cầu vận hành thay vì “expose hết rồi bảo vệ sau”. Exclude vẫn hữu ích như lớp loại trừ an toàn, nhưng tập cho phép nhỏ sẽ dễ rà soát hơn. Operation có khả năng ghi và endpoint dump/cấu hình cần được cân nhắc đặc biệt.

Exposure chỉ quyết định khả năng tiếp cận. Endpoint được expose vẫn có thể cần cô lập mạng/authorization; endpoint không expose vẫn có thể tồn tại trong application context. Chương ManagementAccess sẽ ghép các chiều này lại.

</details>

- [Quay lại đầu trang](#back-to-top)
