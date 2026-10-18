<a id="back-to-top"></a>

# Health endpoint và các HealthContributor

## Menu
- [Health endpoint biểu diễn điều gì?](#health-endpoint-purpose)
- [HealthContributor và HealthIndicator liên hệ với nhau như thế nào?](#health-contributor-model)
- [Composite HealthContributor tạo cây health như thế nào?](#composite-health-contributors)
- [Trạng thái health tổng thể được tổng hợp như thế nào?](#health-status-aggregation)
- [Nên công khai component và thông tin chi tiết của health như thế nào?](#health-detail-visibility)
- [Khi nào nên bổ sung HealthIndicator tùy chỉnh?](#custom-health-indicators)

## <a id="health-endpoint-purpose">Health endpoint biểu diễn điều gì?</a>

<details>
<summary>Xem chi tiết</summary>

Health endpoint tóm tắt trạng thái vận hành của ứng dụng và các thành phần liên quan. Hệ thống monitoring, nền tảng triển khai và người vận hành thường dùng nó để biết ứng dụng có đang ở trạng thái sử dụng được hay không. Đây là góc nhìn quản trị từ các bằng chứng health, không phải lời đảm bảo rằng mọi giao dịch nghiệp vụ đều thành công.

Spring Boot tập hợp thông tin health từ các `HealthContributor` đã đăng ký. Auto-configuration bổ sung indicator cho công nghệ được hỗ trợ trên classpath, còn ứng dụng có thể thêm indicator riêng cho miền nghiệp vụ khi tồn tại điều kiện vận hành mà Boot không tự suy ra được.

Endpoint có thể chỉ trả trạng thái tổng thể hoặc trả thông tin component/detail phong phú hơn tùy cấu hình và authorization. Nhờ đó probe công khai có thể giữ phản hồi nhỏ, trong khi người vận hành đã xác thực nhận được bằng chứng sâu hơn.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Health Information](https://docs.spring.io/spring-boot/3.3/reference/actuator/endpoints.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="health-contributor-model">HealthContributor và HealthIndicator liên hệ với nhau như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

`HealthContributor` là hợp đồng chung để Actuator xây cây health. `HealthIndicator` là contributor ở lá, tính ra một kết quả `Health`, thường gồm `Status` và detail tùy chọn. Ứng dụng reactive cũng có reactive health contributor, nhưng mô hình endpoint cuối cùng vẫn là cây contributor được Actuator tổng hợp.

Một custom `HealthIndicator` tốt kiểm tra một điều kiện vận hành có giới hạn rõ ràng và trả `UP` khi điều kiện sử dụng được, hoặc trạng thái khác khi không sử dụng được. Detail có thể chứa bằng chứng như độ sâu queue hay thời điểm kiểm tra gần nhất, nhưng không nên chứa secret hoặc payload chẩn đoán quá lớn.

Điểm quan trọng là trách nhiệm: indicator báo bằng chứng về thành phần; nó không nên tự sửa thành phần, khởi động lại ứng dụng hoặc nhúng toàn bộ chính sách monitoring vào một health check.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="composite-health-contributors">Composite HealthContributor tạo cây health như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

`CompositeHealthContributor` nhóm nhiều contributor con dưới một node, tạo hệ phân cấp thay vì danh sách kiểm tra phẳng. Mô hình này phù hợp khi một phân hệ logic có nhiều phần có thể quan sát độc lập, ví dụ nhiều data source hoặc nhiều phụ thuộc từ xa.

Health endpoint có thể trả kết quả tổng hợp và, khi mức hiển thị component cho phép, cho bên gọi đi sâu vào path của component/nested component. Cây này giúp người vận hành chuyển từ câu “health của hệ thống đang DOWN” sang câu chính xác hơn “contributor nào kéo kết quả tổng hợp xuống”.

Không nên tạo cây health sâu chỉ để phản chiếu cấu trúc package. Chỉ nhóm khi hệ phân cấp có ý nghĩa vận hành và hỗ trợ suy luận khi xử lý sự cố.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="health-status-aggregation">Trạng thái health tổng thể được tổng hợp như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Mỗi contributor ở lá trả một `Status`. `StatusAggregator` của Actuator kết hợp các trạng thái thành trạng thái của node cha hoặc health tổng thể. Boot cung cấp thứ tự mặc định để trạng thái nghiêm trọng như `DOWN` và `OUT_OF_SERVICE` có độ ưu tiên cao hơn `UP`; ứng dụng có thể tùy chỉnh thứ tự khi thêm trạng thái riêng.

Khi expose qua HTTP, HttpCodeStatusMapper chuyển trạng thái health tổng hợp thành mã trạng thái HTTP. Mặc định DOWN và OUT_OF_SERVICE được ánh xạ thành 503 Service Unavailable, còn UP và UNKNOWN trả 200. Khi cấu hình bất kỳ mục `management.endpoint.health.status.http-mapping.*` tùy chỉnh nào, Boot không còn áp dụng mặc định 503 cho DOWN và OUT_OF_SERVICE; nếu vẫn muốn giữ hành vi đó, phải khai báo rõ hai ánh xạ này cùng các trạng thái tùy chỉnh.

Vì cơ chế tổng hợp cho phép một contributor ảnh hưởng kết quả tổng thể, việc chọn phép kiểm tra rất quan trọng. Indicator nhiều nhiễu, chậm hoặc phụ thuộc một hệ thống bên ngoài không thiết yếu có thể khiến góc nhìn health kém tin cậy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="health-detail-visibility">Nên công khai component và thông tin chi tiết của health như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Trạng thái health và mức chi tiết được công khai là hai quyết định khác nhau. `management.endpoint.health.show-details` và `management.endpoint.health.show-components` điều khiển lượng thông tin contributor được trả về. Boot hỗ trợ `never`, `when-authorized` và `always`; mặc định bảo thủ là không hiển thị detail.

Với `when-authorized`, `management.endpoint.health.roles` có thể chỉ ra role nào được xem chi tiết. Authentication và việc người dùng thực sự có role nào vẫn thuộc cấu hình bảo mật; Actuator chỉ áp dụng quy tắc hiển thị lên biểu diễn health.

Hãy expose lượng thông tin tối thiểu đúng đối tượng sử dụng. Load balancer thường chỉ cần trạng thái; người vận hành có thể cần tên component/detail. Không nên đặt credential, token, chuỗi kết nối đầy đủ hoặc exception dump lớn vào health detail dù endpoint đã được bảo vệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-health-indicators">Khi nào nên bổ sung HealthIndicator tùy chỉnh?</a>

<details>
<summary>Xem chi tiết</summary>

Nên thêm custom `HealthIndicator` khi ứng dụng có một điều kiện vận hành thực sự ảnh hưởng khả năng hoạt động và điều kiện đó chưa được contributor do auto-configuration cung cấp biểu diễn. Ví dụ có thể là queue nội bộ đi vào trạng thái không phục hồi hoặc một tài nguyên nghiệp vụ bắt buộc mà service không thể thực hiện nhiệm vụ khi tài nguyên hỏng.

Phép kiểm tra nên nhanh, có giới hạn rõ và không có tác động phụ. Health endpoint có thể bị nền tảng/monitoring gọi thường xuyên; indicator không nên chạy tác vụ sửa chữa nặng hoặc tạo thêm tải làm sự cố tệ hơn. Nếu cần gọi phụ thuộc từ xa, phải có timeout và cân nhắc phụ thuộc đó nên nằm trong health tổng thể hay chỉ trong một group chuyên biệt.

Custom health là tín hiệu vận hành, không phải API nghiệp vụ. Nếu bên gọi cần trạng thái luồng công việc/miền nghiệp vụ phong phú, hợp đồng đó thường thuộc bề mặt nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)
