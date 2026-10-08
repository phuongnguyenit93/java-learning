<a id="back-to-top"></a>

# Các endpoint info và thông tin môi trường

## Menu
- [Info endpoint dùng để làm gì?](#info-endpoint-purpose)
- [Các contribution kiểu InfoContributor tạo metadata vận hành như thế nào?](#info-contributors)
- [Endpoint hướng tới environment có thể cho biết gì về ứng dụng đang chạy?](#environment-facing-endpoints)
- [Các giá trị có khả năng nhạy cảm được xử lý như thế nào?](#sensitive-value-sanitization)
- [Vì sao các góc nhìn info và environment là một quyết định về quyền truy cập?](#info-env-access-boundary)
- [Việc quan sát qua endpoint bàn giao sang externalized configuration ở đâu?](#externalized-config-handoff)

## <a id="info-endpoint-purpose">Info endpoint dùng để làm gì?</a>

<details>
<summary>Xem chi tiết</summary>

Info endpoint là nơi cung cấp thông tin ứng dụng ngắn gọn hữu ích cho người vận hành và tự động hóa: định danh bản build, phiên bản, revision mã nguồn hoặc metadata không thuộc giao dịch nghiệp vụ. Nội dung được tập hợp từ các bean `InfoContributor` thay vì một object thông tin ứng dụng cố định.

Info không phải một debug dump tổng quát. Dữ liệu tốt nên đủ ổn định để nhận diện thứ đang chạy và đủ nhỏ để trả theo request. Dữ liệu biến đổi liên tục phù hợp với metrics/telemetry hơn; secret và cấu hình chi tiết cũng không nên được đưa vào info chỉ vì đôi lúc người vận hành cần xem.

Giống các endpoint khác, info phải được expose mới có thể truy cập từ xa. Exposure mặc định của Boot 3.3 vẫn hẹp, nên việc tạo contribution không đồng nghĩa dữ liệu tự động được công khai.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Application Information](https://docs.spring.io/spring-boot/3.3/reference/actuator/endpoints.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="info-contributors">Các contribution kiểu InfoContributor tạo metadata vận hành như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

InfoContributor là điểm mở rộng để thêm một nhóm thông tin có tên vào phản hồi của info. Trong Boot 3.3, các contributor `env`, `java`, `os` và `process` mặc định bị tắt; `build` và `git` mặc định được bật khi dữ liệu tiên quyết tương ứng `META-INF/build-info.properties` hoặc `git.properties` tồn tại.

Ứng dụng có thể đăng ký `InfoContributor` riêng khi có metadata vận hành hữu ích chưa thuộc contributor tích hợp sẵn. Custom contributor nên thêm giá trị có cấu trúc nhỏ, rõ nghĩa như kiểu triển khai hoặc phiên bản model thay vì chạy truy vấn tốn kém mỗi lần `/info` được gọi.

Từng contributor tích hợp sẵn được điều khiển bằng `management.info.<id>.enabled`; `management.info.defaults.enabled=false` có thể tắt các contributor vốn được bật mặc định. Các mặc định này độc lập với việc endpoint info có được expose qua HTTP hay JMX hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="environment-facing-endpoints">Endpoint hướng tới environment có thể cho biết gì về ứng dụng đang chạy?</a>

<details>
<summary>Xem chi tiết</summary>

Các endpoint hướng tới environment trả lời câu hỏi chẩn đoán về cấu hình. env biểu diễn thông tin từ Spring `ConfigurableEnvironment` và các property source, còn configprops cho thấy giá trị đã bind vào bean `ConfigurationProperties`. Hai góc nhìn giúp hiểu tiến trình đang thấy cấu hình nào và object cấu hình có cấu trúc chứa gì.

Chúng chỉ là lớp chiếu chẩn đoán, không phải nguồn chân lý của cấu hình. Actuator không thay đổi `Environment`, không quyết định thứ tự ưu tiên và không dạy Config Data, profile, biến môi trường hay đối số dòng lệnh được hợp nhất thế nào. Các cơ chế đó thuộc `externalized-configuration`.

Ngay cả khi giá trị bị ẩn, endpoint vẫn có thể tiết lộ tên property, cấu trúc nguồn và ngữ cảnh vận hành. Vì vậy nên xem đây là bề mặt chẩn đoán đặc quyền.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sensitive-value-sanitization">Các giá trị có khả năng nhạy cảm được xử lý như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot che các giá trị do những endpoint cấu hình nhạy cảm trả về. env/configprops có thể thay giá trị bằng dạng đã che, còn chính sách `show-values` quyết định khi nào giá trị gốc được trả. Với HTTP, `when-authorized` yêu cầu người dùng đã xác thực có một trong các role cấu hình cho endpoint; nếu không cấu hình role thì mọi người dùng đã xác thực đều được xem là có quyền. Với JMX, Actuator xem mọi người dùng là có quyền đối với quyết định `show-values` này.

Sanitization giảm nguy cơ vô tình lộ dữ liệu nhưng không phải lý do để expose rộng. Tên property, tên nguồn, cấu trúc object và các giá trị không bị nhận diện là nhạy cảm vẫn có thể hữu ích cho kẻ tấn công. Ứng dụng còn có thể mở rộng sanitization khi tổ chức có quy ước đặt tên secret riêng.

Biện pháp mạnh nhất vẫn là giới hạn exposure và quyền truy cập. Quy tắc “mọi người dùng đều được xem là có quyền” ở trên không bảo vệ chính JMX: việc expose JMX, kết nối từ xa, xác thực/kiểm soát truy cập và vị trí mạng vẫn phải được bảo vệ có chủ đích. Sanitization chỉ là một lớp phòng vệ bổ sung cho biểu diễn quản trị, không phải hệ thống quản lý secret.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="info-env-access-boundary">Vì sao các góc nhìn info và environment là một quyết định về quyền truy cập?</a>

<details>
<summary>Xem chi tiết</summary>

Các góc nhìn info và environment có mức rủi ro khác nhau. Một phản hồi info được chọn lọc tốt có thể phù hợp với đối tượng sử dụng rộng hơn, trong khi env/configprops thường tiết lộ đủ cấu trúc để cần hạn chế quyền truy cập. Chính sách đúng phụ thuộc môi trường triển khai và dữ liệu mà ứng dụng đóng góp.

Trước khi expose, cần rà soát cả giá trị lẫn metadata xung quanh: bên gọi chưa xác thực có thật sự cần biết không, người vận hành có kênh nội bộ an toàn hơn không, endpoint có chỉ truy cập được từ mạng quản trị không.

Cấu hình exposure, vị trí mạng, authorization của lớp bảo mật và sanitization nên hỗ trợ lẫn nhau. Không một lớp nào nên được xem là biện pháp bảo vệ hoàn chỉnh cho dữ liệu quản trị nhạy cảm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="externalized-config-handoff">Việc quan sát qua endpoint bàn giao sang externalized configuration ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Actuator giúp trả lời “ứng dụng đang chạy có thể báo trạng thái nào liên quan đến cấu hình?”. Externalized configuration trả lời “Boot đã phân giải trạng thái đó từ property source, Config Data, profile, đối số dòng lệnh, biến môi trường và quy tắc binding ra sao?”.

Ví dụ, env có thể cho biết property xuất hiện trong một property source và configprops cho thấy giá trị đã bind vào object `ConfigurationProperties`. Nếu giá trị bất ngờ, việc điều tra rời Actuator và quay về thứ tự ưu tiên/quy tắc binding của `externalized-configuration`.

Điểm bàn giao này cần rõ trong cả nội dung học lẫn xử lý sự cố. Actuator là bề mặt kiểm tra runtime; nó không trở thành curriculum thứ hai cho Config Data import, relaxed binding, kích hoạt profile, phân giải placeholder hay validation của configuration property.

</details>

- [Quay lại đầu trang](#back-to-top)
