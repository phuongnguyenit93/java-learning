<a id="back-to-top"></a>

# Tích hợp Docker Compose cho môi trường phát triển

## Menu
- [Vì sao Boot tích hợp Docker Compose cho quá trình phát triển?](#compose-development-purpose)
- [Boot phát hiện và quản lý dự án Compose như thế nào?](#compose-discovery-lifecycle)
- [Khi nào Boot tạo service connection details?](#compose-service-connections)
- [Cổng đã ánh xạ và thứ tự ưu tiên của connection details ảnh hưởng ứng dụng ra sao?](#compose-mapped-ports-precedence)
- [Boot xác định dịch vụ Compose đã sẵn sàng như thế nào?](#compose-readiness)
- [Việc chọn file, profile, cơ chế bỏ qua và chính sách lifecycle thay đổi tích hợp ra sao?](#compose-runtime-controls)
- [Hỗ trợ Docker Compose lúc phát triển bàn giao sang kiểm thử với Testcontainers ở đâu?](#compose-testing-boundary)
- [Phần nào vẫn thuộc trách nhiệm của Docker và Compose tổng quát?](#compose-docker-boundary)

## <a id="compose-development-purpose">Vì sao Boot tích hợp Docker Compose cho quá trình phát triển?</a>

<details>
<summary>Xem chi tiết</summary>

Lập trình viên thường cần database, broker, cache hoặc dịch vụ bên ngoài chỉ để chạy ứng dụng cục bộ. Hỗ trợ Docker Compose của Spring Boot giảm phần ghép nối thủ công giữa "khởi động các dependency" và "khởi động ứng dụng". Dependency `spring-boot-docker-compose` dành cho môi trường phát triển có thể phát hiện Compose file, khởi động dịch vụ khi cần và tạo connection details để Boot auto-configuration sử dụng.

Đây là tích hợp dành cho môi trường phát triển, không phải bộ điều phối production. Mục tiêu là làm runtime ứng dụng cục bộ và các dịch vụ phụ thuộc khởi động phối hợp với nhau.

Module này vì vậy dạy cơ chế phát hiện, lifecycle, readiness và hành vi service connection của Boot. Docker image, network, volume, cú pháp Compose và vận hành container vẫn thuộc phần kiến thức containerization.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compose-discovery-lifecycle">Boot phát hiện và quản lý dự án Compose như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Khi hỗ trợ Docker Compose có trên classpath, Boot tìm `compose.yml` và những tên file Compose phổ biến trong thư mục làm việc. Nếu project chưa chạy và chính sách lifecycle cho phép, Boot gọi Docker Compose để khởi động project. Khi ứng dụng shutdown bình thường, chính sách mặc định dừng các dịch vụ mà Boot đã khởi động.

Nếu Boot phát hiện các dịch vụ Compose đã chạy từ trước, nó chỉ tạo các service connection được hỗ trợ; Boot không gọi `docker compose up` thêm và không nhận trách nhiệm dừng project đã được tiến trình/lập trình viên khác khởi động.

Quy tắc này tránh việc ứng dụng vô tình tắt hạ tầng không do nó sở hữu. Khi lifecycle gây bất ngờ, hãy kiểm tra Boot có phải bên đã khởi động project không và `spring.docker.compose.lifecycle-management` đang là gì.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compose-service-connections">Khi nào Boot tạo service connection details?</a>

<details>
<summary>Xem chi tiết</summary>

Với dịch vụ Compose được nhận diện, Boot tạo các bean connection details mà auto-configuration tương ứng có thể sử dụng. Service connection mô tả cách ứng dụng kết nối dịch vụ từ xa, thay vì buộc mỗi auto-configuration tự tìm lại host/port/credential từ nhiều property.

Việc nhận diện thường dựa trên tên container image. Image tùy biến có thể dùng label `org.springframework.boot.service-connection` được Boot tài liệu hóa để chỉ ra loại dịch vụ; container cũng có thể được bỏ qua bằng label ignore được hỗ trợ.

Service connection là hợp đồng tích hợp chứ không thay thế cấu hình Compose tổng quát. Boot chỉ tạo connection details cho công nghệ nó hỗ trợ; container khác vẫn có thể chạy trong cùng Compose project mà không trở thành service connection do Boot quản lý.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compose-mapped-ports-precedence">Cổng đã ánh xạ và thứ tự ưu tiên của connection details ảnh hưởng ứng dụng ra sao?</a>

<details>
<summary>Xem chi tiết</summary>

Compose thường ánh xạ một container port cố định sang host port khác hoặc được cấp động. Docker Compose service connection của Boot dùng *mapped host port* để JVM cục bộ kết nối đúng địa chỉ thực tế có thể truy cập.

Khi service connection tồn tại, connection details của nó có độ ưu tiên cao hơn các configuration property kết nối thông thường cho auto-configuration tương ứng. Điều này có chủ đích vì host port động không thể được cấu hình tĩnh của ứng dụng biết trước.

Nếu ứng dụng kết nối tới port bất ngờ, hãy xem service connection và ánh xạ cổng hiện tại trước khi sửa datasource/client properties. Nếu không, bạn có thể đang chỉnh property có độ ưu tiên thấp hơn và không thay đổi hành vi runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compose-readiness">Boot xác định dịch vụ Compose đã sẵn sàng như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Tiến trình container đã khởi động chưa chắc dịch vụ bên trong đã sẵn sàng. Boot vì vậy chờ dịch vụ Compose đạt readiness trước khi tích hợp phát triển được xem là sẵn sàng. Tín hiệu nên ưu tiên là Compose `healthcheck`; nếu không có, Boot có thể dùng phương án dự phòng là thử kết nối TCP tới mapped port.

Kiểm tra readiness bằng TCP có thể tắt theo container bằng label được Boot hỗ trợ; timeout kết nối/đọc và timeout readiness tổng thể cũng có property riêng.

Những điều khiển này phối hợp startup, không định nghĩa health ở mức nghiệp vụ của dịch vụ. Nếu dependency cần điều kiện mạnh hơn "TCP port nhận kết nối", hãy mô tả điều kiện trong Compose healthcheck của dịch vụ thay vì biến Boot thành nơi hiểu nội bộ dịch vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compose-runtime-controls">Việc chọn file, profile, cơ chế bỏ qua và chính sách lifecycle thay đổi tích hợp ra sao?</a>

<details>
<summary>Xem chi tiết</summary>

Boot cung cấp một số điều khiển khi quy trình Compose mặc định không phù hợp. `spring.docker.compose.file` chọn file khác chuẩn, `spring.docker.compose.profiles.active` kích hoạt Compose profiles và `spring.docker.compose.lifecycle-management` quyết định Boot có khởi động/dừng project hay không.

Trong Boot 3.3, các giá trị lifecycle gồm `none`, `start-only` và `start-and-stop`. Tùy chọn lệnh start/stop cùng timeout tiếp tục điều chỉnh cách Boot gọi Compose CLI. Khi kiểm thử, hỗ trợ Docker Compose bị bỏ qua mặc định trừ khi được bật rõ ràng.

Các thiết lập này mô tả *quan hệ của Boot với Compose project*. Network, volume, build context, image healthcheck và topology triển khai vẫn thuộc trách nhiệm Docker/Compose.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Development-time Services: Docker Compose](https://docs.spring.io/spring-boot/3.3/reference/features/dev-services.html#features.dev-services.docker-compose)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compose-testing-boundary">Hỗ trợ Docker Compose lúc phát triển bàn giao sang kiểm thử với Testcontainers ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot có thể tích hợp Docker Compose trong môi trường phát triển và Testcontainers trong kiểm thử, nhưng bên sở hữu lifecycle khác nhau. Chương này sở hữu Compose project gắn với runtime ứng dụng cục bộ của lập trình viên.

Boot mặc định tắt hỗ trợ Docker Compose khi chạy test. Có thể bật tường minh, nhưng module `testing` sở hữu Testcontainers service connections và thiết kế integration test tự động có lifecycle lặp lại được.

Ngay cả khi cả hai chạy cùng database image, mục tiêu vẫn khác: Compose trong môi trường phát triển tối ưu quy trình cục bộ dài hơn của lập trình viên; Testcontainers testing tối ưu dependency do test kiểm soát cùng tính cô lập/khả năng tái lập. Chọn theo bên sở hữu lifecycle chứ không chỉ theo công nghệ container.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compose-docker-boundary">Phần nào vẫn thuộc trách nhiệm của Docker và Compose tổng quát?</a>

<details>
<summary>Xem chi tiết</summary>

Boot gọi Docker Compose và diễn giải metadata dịch vụ được hỗ trợ, nhưng Docker vẫn là runtime bên ngoài. Xây dựng image, Dockerfile, layers, registry, network, volume, ngữ nghĩa merge của Compose, giới hạn tài nguyên, bảo mật container và điều phối production là các mối quan tâm container tổng quát.

Ranh giới này cũng tách hỗ trợ Compose lúc phát triển khỏi việc tạo image của Boot. `bootBuildImage`/Cloud Native Buildpacks thuộc `build-tooling-packaging`; việc viết Dockerfile tổng quát thuộc containerization.

Nếu Compose CLI tự lỗi, hãy điều tra Docker/Compose trước. Nếu Compose chạy đúng nhưng Boot không tạo connection details hoặc hành vi lifecycle mong muốn, quay lại lớp tích hợp application-runtime này.

</details>

- [Quay lại đầu trang](#back-to-top)
