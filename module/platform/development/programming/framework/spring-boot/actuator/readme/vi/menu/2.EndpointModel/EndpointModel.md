<a id="back-to-top"></a>

# Mô hình endpoint của Actuator

## Menu
- [Actuator endpoint là gì?](#actuator-endpoint-abstraction)
- [Endpoint ID và operation tổ chức mô hình như thế nào?](#endpoint-id-and-operations)
- [Nên tư duy thế nào về tập endpoint có sẵn?](#built-in-endpoint-catalog)
- [Vì sao Actuator endpoint mặc định độc lập với công nghệ expose?](#technology-agnostic-endpoints)
- [Thiết lập endpoint nào thuộc Actuator và phần nào thuộc externalized configuration?](#endpoint-configuration-boundary)

## <a id="actuator-endpoint-abstraction">Actuator endpoint là gì?</a>

<details>
<summary>Xem chi tiết</summary>

Actuator endpoint là một khả năng quản trị có endpoint ID và một hoặc nhiều operation. Mô hình endpoint cốt lõi mô tả việc quản trị nào tồn tại; lớp adapter theo công nghệ mới quyết định operation đó được biểu diễn qua HTTP hoặc JMX như thế nào.

Vì vậy health, info, metrics và loggers nên được hiểu trước hết là endpoint ID, không phải đường dẫn controller gắn cứng. Trong ứng dụng web, health thường xuất hiện ở `/actuator/health`, nhưng tiền tố `/actuator` có thể thay đổi và cùng mô hình endpoint còn có thể được expose qua JMX.

Abstraction này cho Boot một mô hình quản trị dùng lại được giữa nhiều kênh truyền và custom endpoint. Nó cũng tạo mục tiêu ổn định cho cấu hình: property có thể bật endpoint hoặc include/exclude endpoint ID khỏi một công nghệ exposure mà mã ứng dụng không phải tự đăng ký route/MBean.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Endpoints](https://docs.spring.io/spring-boot/3.3/reference/actuator/endpoints.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="endpoint-id-and-operations">Endpoint ID và operation tổ chức mô hình như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Endpoint ID là định danh quản trị ổn định như health, metrics hoặc loggers. Một endpoint có thể cung cấp operation kiểu read, write hoặc delete. Với custom endpoint, các loại operation này được biểu diễn bằng `@ReadOperation`, `@WriteOperation` và `@DeleteOperation`; endpoint tích hợp sẵn cũng tuân theo cùng tư duy hướng operation.

Operation cố ý trừu tượng hơn HTTP verb. Khi expose qua HTTP, Actuator ánh xạ operation sang method/path của request phù hợp. Khi expose qua JMX, cùng ý định quản trị trở thành MBean operation. Nhờ đó người viết endpoint mô tả hành vi một lần thay vì bắt đầu từ hợp đồng controller.

Mô hình tư duy hữu ích là endpoint ID -> operations -> exposure adapter. URL hay tên MBean chỉ là cách biểu diễn của mô hình đó, không phải định danh gốc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="built-in-endpoint-catalog">Nên tư duy thế nào về tập endpoint có sẵn?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot cung cấp nhiều endpoint tích hợp sẵn cho các câu hỏi vận hành khác nhau. health báo health, info báo metadata ứng dụng, metrics cho phép xem meter, loggers đọc/thay đổi cấu hình logger; env và configprops đưa ra góc nhìn cấu hình; beans, mappings, scheduledtasks, conditions và các endpoint tương tự cho thấy cấu trúc framework/runtime.

Một số endpoint chẩn đoán có điều kiện tiên quyết hoặc rủi ro riêng. startup cần dữ liệu từ `BufferingApplicationStartup`. httpexchanges cần `HttpExchangeRepository`. Ứng dụng web có thể expose heapdump dạng binary, logfile khi logging ra file được cấu hình, và metrics định dạng Prometheus khi có dependency của Prometheus registry.

Không nên học danh mục như danh sách phải thuộc lòng. Hãy nhóm endpoint theo câu hỏi vận hành, rồi kiểm tra bảng endpoint chính thức của Boot 3.3 để biết điều kiện tiên quyết, enablement mặc định và endpoint nào chỉ có trên web.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="technology-agnostic-endpoints">Vì sao Actuator endpoint mặc định độc lập với công nghệ expose?</a>

<details>
<summary>Xem chi tiết</summary>

Mô hình `@Endpoint` tổng quát được thiết kế độc lập với Spring MVC, WebFlux, Jersey hay JMX. Boot phát hiện endpoint/operation trước; hạ tầng theo từng công nghệ mới expose chúng khi công nghệ đó khả dụng và endpoint ID được include trong cấu hình exposure.

Tách lớp này đặc biệt có ích cho thư viện hoặc thành phần quản trị dùng lại được. Một custom `@Endpoint` có thể hoạt động qua cả HTTP và JMX mà không cần annotation của controller hoặc phụ thuộc trực tiếp vào một web stack. Nếu operation thật sự cần khái niệm request/response của HTTP hoặc hành vi riêng của JMX, Boot có extension point hẹp hơn cho trường hợp đó.

Vì endpoint không gắn cứng với kênh truyền, payload nên ưu tiên dữ liệu quản trị và ngữ nghĩa vận hành. Chi tiết của kênh truyền chỉ nên xuất hiện khi yêu cầu thực sự phụ thuộc công nghệ expose.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="endpoint-configuration-boundary">Thiết lập endpoint nào thuộc Actuator và phần nào thuộc externalized configuration?</a>

<details>
<summary>Xem chi tiết</summary>

Actuator sở hữu ngữ nghĩa cấu hình endpoint: endpoint ID nào được bật, ID nào được web/JMX expose, web base path ánh xạ endpoint ID ra sao và thiết lập riêng như mức hiển thị health detail có ý nghĩa gì. Những giá trị đó được biểu diễn bằng các property của Boot trong namespace như `management.endpoint.*` và `management.endpoints.*`.

Nguồn và thứ tự ưu tiên của property vẫn thuộc `externalized-configuration`. Giá trị có thể đến từ file cấu hình, biến môi trường, đối số dòng lệnh, giá trị ghi đè trong test hoặc property source khác; Actuator không định nghĩa lại cách Boot chọn giá trị cuối.

Khi debug, hãy tách hai câu hỏi. “`management.endpoints.web.exposure.include` có ngữ nghĩa gì?” thuộc module này. “Vì sao biến môi trường ghi đè YAML?” thuộc externalized configuration. Cách tách này tránh lặp lại nội dung cấu hình trong mọi tính năng Actuator.

</details>

- [Quay lại đầu trang](#back-to-top)
