<a id="back-to-top"></a>

# Liveness, readiness và health probe

## Menu
- [Actuator sử dụng application availability của Boot như thế nào?](#availability-health-handoff)
- [Liveness và readiness trở thành health group như thế nào?](#liveness-readiness-health-groups)
- [Các endpoint probe liveness và readiness cung cấp góc nhìn vận hành nào?](#probe-endpoint-paths)
- [Vì sao phải thận trọng khi đưa phụ thuộc bên ngoài vào probe?](#probe-external-dependency-boundary)
- [Actuator bàn giao quyền sở hữu availability state về application-runtime ở đâu?](#application-runtime-availability-ownership)

## <a id="availability-health-handoff">Actuator sử dụng application availability của Boot như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Mô hình `application-runtime` của Spring Boot sở hữu `ApplicationAvailability` cùng các chuyển đổi của `LivenessState` và `ReadinessState`. Actuator chỉ tiêu thụ các trạng thái đó qua health indicator chuyên biệt để client quản trị quan sát được mà không trở thành một phần của cơ chế vòng đời sinh ra trạng thái.

`LivenessStateHealthIndicator` chuyển trạng thái liveness hiện tại thành trạng thái health; `ReadinessStateHealthIndicator` làm tương tự cho readiness. Kết quả tham gia mô hình `HealthContributor` bình thường, nên availability có thể xuất hiện qua health endpoint và được nhóm như các contributor khác.

Điểm bàn giao này cố ý đi từ quyền sở hữu trạng thái sang góc nhìn quản trị. Probe request đọc trạng thái mà application runtime đã đạt tới; probe endpoint không quyết định runner khi nào xong, readiness khi nào đổi hoặc shutdown khi nào bắt đầu.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Kubernetes Probes](https://docs.spring.io/spring-boot/3.3/reference/actuator/endpoints.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="liveness-readiness-health-groups">Liveness và readiness trở thành health group như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Actuator biểu diễn liveness/readiness dưới dạng health group. Khi hỗ trợ probe hoạt động, hai group mang tên liveness và readiness và dùng các health indicator của trạng thái availability tương ứng. Trong môi trường Kubernetes, Boot tự bật các probe group; môi trường khác có thể bật rõ ràng bằng `management.endpoint.health.probes.enabled`.

Vì đây là health group nên toàn bộ mô hình cấu hình health group vẫn áp dụng: contributor được include, mức hiển thị detail và thiết lập trạng thái có thể điều chỉnh theo group. Boot cố ý không tự đưa mọi `HealthIndicator` bên ngoài vào group liveness/readiness.

Hai group là hai hợp đồng vận hành khác nhau. Liveness trả lời tiến trình có cần khởi động lại không; readiness trả lời traffic có nên tiếp tục được định tuyến đến tiến trình không. Đưa cùng các phép kiểm tra phụ thuộc vào cả hai sẽ làm mất khác biệt này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="probe-endpoint-paths">Các endpoint probe liveness và readiness cung cấp góc nhìn vận hành nào?</a>

<details>
<summary>Xem chi tiết</summary>

Với web base path quản trị mặc định, khi các probe group được bật và health được expose thì liveness/readiness nằm ở `/actuator/health/liveness` và `/actuator/health/readiness`. Nền tảng triển khai có thể dùng các path đó làm HTTP probe.

Nếu Actuator chạy trên cổng quản trị riêng, probe có thể vẫn hoạt động tốt trong khi cổng ứng dụng chính hoặc hạ tầng web không nhận request được. Vì vậy Boot hỗ trợ `management.endpoint.health.probes.add-additional-paths` để expose thêm `/livez` và `/readyz` trên server chính.

Chọn path dựa trên miền lỗi mà nền tảng thực sự cần kiểm tra. Cổng quản trị riêng hữu ích cho việc cô lập, nhưng không nên vô tình che lỗi của đường request chính khỏi phép kiểm tra readiness.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="probe-external-dependency-boundary">Vì sao phải thận trọng khi đưa phụ thuộc bên ngoài vào probe?</a>

<details>
<summary>Xem chi tiết</summary>

Liveness thường không nên phụ thuộc hệ thống bên ngoài như database, API từ xa hoặc cache. Nếu database dùng chung hỏng và mọi instance đồng loạt báo liveness lỗi, orchestrator có thể khởi động lại toàn bộ instance dù việc khởi động lại không sửa được database, từ đó tạo lỗi dây chuyền.

Readiness có thể cân nhắc điều kiện bên ngoài nếu mất phụ thuộc đồng nghĩa instance không nên nhận traffic, nhưng quyết định này phụ thuộc ứng dụng. Boot cố ý không tự thêm các health check bổ sung vào readiness group. Nhóm phát triển phải cân nhắc việc lấy tất cả instance ra khỏi load balancer có tốt hơn để service chạy ở chế độ suy giảm hay không.

Nếu thêm phép kiểm tra phụ thuộc, phép kiểm tra cần có giới hạn và đáng tin: timeout, ngữ nghĩa khi lỗi và ảnh hưởng lên định tuyến quan trọng hơn việc tái sử dụng mọi `HealthIndicator`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="application-runtime-availability-ownership">Actuator bàn giao quyền sở hữu availability state về application-runtime ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Actuator sở hữu cách biểu diễn health của availability; `application-runtime` sở hữu trạng thái thật và thời điểm chuyển đổi trong vòng đời. Khi khởi động, liveness có thể chuyển `CORRECT` trước readiness `ACCEPTING_TRAFFIC` vì các startup runner vẫn phải hoàn tất trước khi ứng dụng sẵn sàng.

Khi shutdown, `application-runtime` đổi readiness theo vòng đời và Actuator chỉ phản ánh trạng thái kết quả. Nếu probe bất ngờ báo `REFUSING_TRAFFIC`, hãy tìm vì sao trạng thái runtime đổi trước. Chỉnh health group chỉ thay cách biểu diễn, không sửa chuyển đổi đã tạo tín hiệu.

Lifecycle event, runner, `AvailabilityChangeEvent` và việc phát trạng thái thủ công thuộc `application-runtime`. Health group, probe path và cách trạng thái được trình bày cho client quản trị thuộc Actuator.

</details>

- [Quay lại đầu trang](#back-to-top)
