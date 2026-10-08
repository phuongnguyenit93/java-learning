<a id="back-to-top"></a>

# Tạo Actuator endpoint tùy chỉnh

## Menu
- [Khi nào ứng dụng cần endpoint quản trị tùy chỉnh?](#custom-endpoint-purpose)
- [@ReadOperation, @WriteOperation và @DeleteOperation định nghĩa operation như thế nào?](#endpoint-operation-annotations)
- [Vì sao nên ưu tiên @Endpoint độc lập công nghệ khi có thể?](#technology-agnostic-custom-endpoints)
- [Khi nào endpoint riêng cho Web hoặc JMX là phù hợp?](#web-jmx-specific-endpoints)
- [Khi nào nên mở rộng một endpoint có sẵn thay vì tạo endpoint mới?](#endpoint-extensions)
- [Actuator endpoint tùy chỉnh tách khỏi API nghiệp vụ như thế nào?](#business-api-boundary)

## <a id="custom-endpoint-purpose">Khi nào ứng dụng cần endpoint quản trị tùy chỉnh?</a>

<details>
<summary>Xem chi tiết</summary>

Custom Actuator endpoint phù hợp khi ứng dụng có nhu cầu quản trị vận hành chưa được danh mục endpoint tích hợp sẵn đáp ứng. Ví dụ tốt gồm kiểm tra một phần trạng thái nội bộ có giới hạn rõ, kích hoạt tác vụ bảo trì được kiểm soát chặt hoặc expose metadata quản trị cho công cụ triển khai/vận hành.

Yêu cầu phải mang tính vận hành hơn là hướng tới nghiệp vụ. Nếu khách hàng/client nghiệp vụ cần khả năng đó như một phần luồng của sản phẩm, nó thuộc API nghiệp vụ dù người vận hành đôi khi cũng gọi. Actuator endpoint mang các giả định riêng về exposure, authorization và ánh xạ kênh truyền.

Trước khi thêm endpoint mới, hãy kiểm tra endpoint tích hợp sẵn, `HealthContributor`, metric hoặc `InfoContributor` đã biểu diễn nhu cầu chưa. Mỗi hợp đồng quản trị tùy chỉnh mới đều cần tài liệu và rà soát bảo mật.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Implementing Custom Endpoints](https://docs.spring.io/spring-boot/3.3/reference/actuator/endpoints.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="endpoint-operation-annotations">@ReadOperation, @WriteOperation và @DeleteOperation định nghĩa operation như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Bean gắn `@Endpoint` khai báo ID của endpoint quản trị. Method gắn `@ReadOperation`, `@WriteOperation` hoặc `@DeleteOperation` khai báo operation mà Actuator có thể expose. Các annotation mô tả ý định quản trị thay vì gắn method trực tiếp với một kênh truyền.

Read operation lấy trạng thái quản trị; write operation thay đổi/thực hiện thao tác quản trị; delete operation xóa trạng thái quản trị khi ngữ nghĩa đó hợp lý. Hạ tầng Web/JMX ánh xạ các operation sang cách biểu diễn của từng kênh.

Đầu vào của operation là các tham số method. Mặc định chúng là bắt buộc và có thể chuyển thành tùy chọn bằng `@Nullable`. Endpoint độc lập công nghệ chỉ hỗ trợ các kiểu tham số đơn giản thay vì một DTO phức tạp nhận toàn bộ JSON body; với write operation qua web, từng thuộc tính gốc của JSON được ánh xạ vào từng tham số. Mã Java triển khai endpoint phải giữ tên tham số bằng `-parameters`; Boot Gradle plugin và Maven `spring-boot-starter-parent` tự cấu hình việc này.

Trước khi gọi operation, Actuator chuyển đổi đầu vào HTTP hoặc JMX bằng `ApplicationConversionService` cùng các converter có `@EndpointConverter`. Tham số gắn `@Selector` trở thành một đoạn path khi expose qua web, nhờ đó operation có thể chọn một phần dữ liệu endpoint mà không cần biến endpoint thành controller. Giữ đầu vào/đầu ra nhỏ, rõ ràng; write operation cần nêu rõ tác động phụ và điều kiện tiên quyết.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="technology-agnostic-custom-endpoints">Vì sao nên ưu tiên @Endpoint độc lập công nghệ khi có thể?</a>

<details>
<summary>Xem chi tiết</summary>

Ưu tiên `@Endpoint` khi khả năng quản trị không phụ thuộc HTTP/JMX. Boot có thể expose cùng mô hình endpoint qua các công nghệ được hỗ trợ theo cấu hình. Mã quản trị cốt lõi nhờ đó dùng lại được giữa nhiều web stack và không kéo khái niệm controller vào abstraction vận hành.

Tính độc lập công nghệ cũng giúp test/bảo trì: method của endpoint tập trung vào đầu vào, service vận hành và đầu ra, còn Actuator sở hữu ánh xạ kênh truyền. Đổi web stack không buộc viết lại khả năng quản trị.

Không nên ép tính độc lập kênh khi yêu cầu thật sự cần hành vi của kênh. Operation cần ngữ nghĩa status hoặc content type riêng của Actuator trên web có thể dùng mô hình web endpoint/extension phù hợp. Nếu cần điều khiển HTTP response header tùy ý hoặc hành vi request/response đặc thù của framework, hãy dùng controller hay extension point hạ tầng phù hợp của Spring MVC/WebFlux. Chỉ cần trả dữ liệu nhị phân chưa phải lý do để chuyển sang endpoint riêng cho web: operation của `@Endpoint` tổng quát có thể trả `Resource`, được Actuator phục vụ dưới dạng `application/octet-stream`; Spring MVC và WebFlux còn hỗ trợ range request cho loại tài nguyên này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-jmx-specific-endpoints">Khi nào endpoint riêng cho Web hoặc JMX là phù hợp?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot có `@WebEndpoint` và `@JmxEndpoint` cho endpoint chỉ tồn tại trên một công nghệ quản trị. Endpoint riêng cho Web phù hợp khi khả năng chỉ có ý nghĩa qua HTTP; endpoint riêng cho JMX có thể phụ thuộc cách biểu diễn JMX mà không giả vờ độc lập kênh.

Endpoint riêng theo công nghệ là một đánh đổi có chủ đích: có hợp đồng hẹp hơn nhưng mất khả năng dùng lại trên công nghệ quản trị khác. Chỉ chọn vì yêu cầu cần kênh đó, không phải vì lập trình viên quen controller/HTTP hơn.

Nếu cần đầy đủ hành vi của Spring MVC/WebFlux, controller thông thường có thể đúng hơn; khi đó khả năng không còn là Actuator endpoint độc lập công nghệ và phải được thiết kế/bảo vệ với ranh giới này rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="endpoint-extensions">Khi nào nên mở rộng một endpoint có sẵn thay vì tạo endpoint mới?</a>

<details>
<summary>Xem chi tiết</summary>

Đôi khi khái niệm vận hành đã có endpoint tích hợp sẵn nhưng một công nghệ exposure cần thêm operation/cách biểu diễn. Actuator hỗ trợ `@EndpointWebExtension` và `@EndpointJmxExtension` để hành vi riêng theo công nghệ bổ sung endpoint hiện có thay vì tạo một ID trùng ý nghĩa.

Extension nên giữ nguyên ý nghĩa của endpoint được mở rộng. Nếu hành vi mới là trách nhiệm vận hành hoàn toàn khác, một custom endpoint riêng thường rõ hơn. Extension dành cho phần bổ sung riêng theo công nghệ, không phải cách vượt ranh giới sở hữu của endpoint.

Khi mở rộng endpoint tích hợp sẵn, cần nghĩ đến chi phí nâng cấp. Giữ extension nhỏ, test với baseline Boot 3.3 của repo và tránh phụ thuộc chặt vào phần triển khai nội bộ không thuộc extension API được hỗ trợ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="business-api-boundary">Actuator endpoint tùy chỉnh tách khỏi API nghiệp vụ như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Endpoint quản trị và API nghiệp vụ đều có thể dùng HTTP nhưng phục vụ người dùng khác nhau. API nghiệp vụ mô hình hóa khả năng của miền nghiệp vụ và là hợp đồng sản phẩm. Custom Actuator endpoint mô hình hóa thao tác, chẩn đoán hoặc bảo trì ứng dụng và thuộc bề mặt quản trị.

Không nên đưa CRUD/luồng công việc khách hàng vào `@Endpoint` chỉ để hưởng tiện ích exposure/bảo mật của Actuator. Điều đó làm việc khám phá endpoint, authorization, versioning và kỳ vọng của client khó hiểu. Ngược lại, operation bảo trì đặc quyền cũng không nên giấu giữa các controller nghiệp vụ công khai nếu bản chất là khả năng vận hành.

Hãy hỏi ai gọi operation, vì sao họ gọi và hậu quả nếu endpoint bị expose nhầm. Các câu hỏi đó thường xác định đúng bề mặt tốt hơn việc chỉ dựa vào chuyện cả hai đều trả JSON.

</details>

- [Quay lại đầu trang](#back-to-top)
