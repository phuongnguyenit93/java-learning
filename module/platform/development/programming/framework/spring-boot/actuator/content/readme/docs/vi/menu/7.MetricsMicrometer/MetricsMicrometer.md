<a id="back-to-top"></a>

# Metrics endpoint và ranh giới với Micrometer

## Menu
- [Boot tích hợp hạ tầng Micrometer MeterRegistry như thế nào?](#micrometer-meter-registry-boundary)
- [Metrics endpoint của Actuator dùng để làm gì?](#metrics-endpoint-purpose)
- [Tên meter và measurement xuất hiện trong metrics endpoint như thế nào?](#meter-name-measurements)
- [Tag có thể thu hẹp việc xem metrics như thế nào?](#tag-filtered-metrics)
- [Xem metrics trực tiếp khác export metrics như thế nào?](#metrics-export-boundary)
- [Những vấn đề thiết kế metrics và backend nào nằm ngoài Actuator?](#metric-design-boundary)

## <a id="micrometer-meter-registry-boundary">Boot tích hợp hạ tầng Micrometer MeterRegistry như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot Actuator tích hợp Micrometer như facade metrics mà Boot dùng cho auto-configuration metrics. Khi một triển khai registry được hỗ trợ có mặt, Boot tạo/cấu hình hạ tầng `MeterRegistry` và bind nhiều meter của framework, JVM, tiến trình hoặc công nghệ cụ thể. Mã ứng dụng cũng có thể đăng ký meter qua registry do Spring quản lý.

Ranh giới cần nhớ là: Micrometer sở hữu mô hình meter và API registry; Spring Boot sở hữu auto-configuration ghép chúng vào ứng dụng Boot; Actuator cung cấp endpoint quản trị để kiểm tra hoặc expose một phần kết quả.

Vì vậy có Actuator không có nghĩa mọi metric đều do Actuator tạo. Nhiều meter đến từ Micrometer binder hoặc instrumentation của framework. Module này chỉ dạy tích hợp Boot và bề mặt quản trị.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Metrics](https://docs.spring.io/spring-boot/3.3/reference/actuator/metrics.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="metrics-endpoint-purpose">Metrics endpoint của Actuator dùng để làm gì?</a>

<details>
<summary>Xem chi tiết</summary>

Metrics endpoint là bề mặt kiểm tra chẩn đoán trên các meter hiện có trong `MeterRegistry` của ứng dụng. `/actuator/metrics` liệt kê tên meter; `/actuator/metrics/{name}` đi sâu vào một tên meter khi endpoint đã được expose.

Endpoint hữu ích trong quá trình phát triển hoặc xử lý sự cố để trả lời “meter này có được đăng ký không?”, “measurement nào đang có?” và “giá trị tag nào tồn tại?”. Nó không phải kênh vận chuyển metrics dài hạn hay công cụ truy vấn time series.

Vì Boot 3.3 mặc định chỉ expose health từ xa, metrics cần được include rõ ràng trong công nghệ exposure mong muốn trước khi bên gọi từ xa có thể kiểm tra. Điều đó độc lập với việc khả năng của metrics endpoint có đang được bật hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="meter-name-measurements">Tên meter và measurement xuất hiện trong metrics endpoint như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Selector của metrics endpoint dùng tên meter như trong mô hình Micrometer của ứng dụng. Hệ thống monitoring phía sau có thể chuẩn hóa tên theo quy ước riêng, nhưng Actuator vẫn dùng tên meter gốc của ứng dụng. Vì vậy tên đã đổi khi export sang backend không phải selector cần dùng ở `/actuator/metrics`.

Endpoint trả measurement phù hợp với loại meter như `COUNT`, `TOTAL_TIME`, `VALUE` hoặc thống kê khác. Khi nhiều meter có cùng tên nhưng khác tag, measurement trả về có thể được tổng hợp trên các series khớp điều kiện.

Phản hồi còn đưa ra các chiều/giá trị tag khả dụng để bên gọi đi sâu hơn. Cần hiểu một tên meter có thể đại diện cho nhiều định danh meter gắn tag, chứ không phải một giá trị vô hướng duy nhất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tag-filtered-metrics">Tag có thể thu hẹp việc xem metrics như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Metrics endpoint nhận query parameter dạng `tag=KEY:VALUE` để thu hẹp việc kiểm tra theo chiều tag. Có thể dùng nhiều tham số tag để lọc thêm. Cách này hữu ích khi một tên meter có nhiều region, mẫu URI, outcome, vùng nhớ hoặc chiều khác.

Việc lọc không tạo metric mới; nó chỉ chọn trong các định danh meter đã đăng ký. Nếu vẫn còn nhiều định danh khớp, Actuator tổng hợp measurement trên các mục khớp đó. `availableTags` giúp bên gọi biết chiều/giá trị nào có thể dùng.

Lọc theo tag phục vụ chẩn đoán chứ không thay thế thiết kế cardinality của metric. Meter chứa user ID/request ID không giới hạn vẫn là vấn đề observability dù endpoint có thể lọc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="metrics-export-boundary">Xem metrics trực tiếp khác export metrics như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Kiểm tra `/actuator/metrics` và export metrics sang hệ thống monitoring là hai luồng khác nhau. Metrics endpoint trả lời request quản trị theo yêu cầu. Registry/exporter tích hợp với backend và publish/expose telemetry theo định dạng/nhịp mà backend yêu cầu.

Prometheus cho thấy rõ khác biệt. Khi có dependency của Prometheus registry, Boot có endpoint prometheus chuyên dụng chứa dữ liệu theo định dạng scrape. Endpoint đó dành cho Prometheus server; metrics endpoint tổng quát dành cho việc chẩn đoán các meter của Micrometer.

Registry khác có thể đẩy dữ liệu hoặc expose theo cơ chế riêng. Actuator/Boot chỉ sở hữu phần tích hợp. Retention, truy vấn, dashboard, alerting và availability của backend thuộc các hệ thống observability bên ngoài.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="metric-design-boundary">Những vấn đề thiết kế metrics và backend nào nằm ngoài Actuator?</a>

<details>
<summary>Xem chi tiết</summary>

Actuator có thể cho thấy meter nào tồn tại nhưng không quyết định mô hình metric của ứng dụng. Chiến lược đặt tên, các chiều low-cardinality, counter/gauge/timer, cấu hình histogram/SLO và ý nghĩa nghiệp vụ của measurement thuộc thiết kế Micrometer/observability.

Thiết kế metric kém vẫn tạo chi phí backend hoặc dashboard sai dù cấu hình Boot đúng về kỹ thuật. Đặc biệt, tag không giới hạn có thể làm số time series tăng nhanh. Actuator có thể giúp nhìn thấy dấu hiệu qua giá trị tag, nhưng cách sửa nằm ở thiết kế instrumentation.

Truy vấn riêng theo backend, tổng hợp nhiều instance, ngưỡng cảnh báo, recording rule, dashboard, retention và hoạch định năng lực đều nằm ngoài module này. Người học cần kết thúc chương với ranh giới đó thật rõ.

</details>

- [Quay lại đầu trang](#back-to-top)
