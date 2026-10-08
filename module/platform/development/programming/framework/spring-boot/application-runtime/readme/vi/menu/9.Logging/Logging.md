<a id="back-to-top"></a>

# Hệ thống logging và runtime logging của Spring Boot

## Menu
- [Vì sao Boot khởi tạo logging trước ApplicationContext?](#early-logging-bootstrap)
- [`LoggingSystem` đóng vai trò gì?](#logging-system-abstraction)
- [Các property của Boot có thể cấu hình những khía cạnh logging runtime nào?](#boot-logging-properties)
- [Vì sao các file cấu hình logging dạng `-spring` quan trọng?](#spring-logging-config-files)
- [Logger groups, đầu ra và rotation nằm ở đâu trong lớp tích hợp Boot?](#logging-groups-output-rotation)
- [Tích hợp logging của Boot kết thúc ở đâu và vận hành logging bắt đầu ở đâu?](#logging-observability-handoff)

## <a id="early-logging-bootstrap">Vì sao Boot khởi tạo logging trước ApplicationContext?</a>

<details>
<summary>Xem chi tiết</summary>

Logging phải hoạt động ngay khi ứng dụng còn đang khởi động, kể cả khi lỗi xảy ra trước lúc bean bình thường tồn tại. Vì vậy Spring Boot khởi tạo logging system trước khi `ApplicationContext` được tạo. Thời điểm sớm này khiến logging trở thành hạ tầng bootstrap chứ không phải một bean ứng dụng thông thường.

Một hệ quả là `@PropertySources` khai báo trong Spring `@Configuration` quá muộn để điều khiển bước khởi tạo logging. Boot có thể dùng các logging property được hỗ trợ từ môi trường chuẩn bị sớm, nhưng cấu hình bean không thể quay ngược thời gian để đổi logging system đã xử lý các thông điệp startup.

Khi log sớm và log runtime khác kỳ vọng, hãy kiểm tra *cấu hình có sẵn từ giai đoạn nào*. Nhiều vấn đề logging startup thực chất là vấn đề về thời điểm trong lifecycle.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Logging](https://docs.spring.io/spring-boot/3.3/reference/features/logging.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="logging-system-abstraction">`LoggingSystem` đóng vai trò gì?</a>

<details>
<summary>Xem chi tiết</summary>

`LoggingSystem` là abstraction của Spring Boot trên các cách triển khai logging được hỗ trợ. Boot phát hiện cách triển khai từ classpath rồi dùng abstraction này để khởi tạo/cấu hình logging trước khi application context sẵn sàng. Code ứng dụng thông thường vẫn log qua SLF4J hoặc logging API; không cần gọi `LoggingSystem` cho hoạt động logging hằng ngày.

Abstraction này giải thích vì sao cùng một tập Boot properties có thể điều khiển hành vi chung trong khi file cấu hình native vẫn phụ thuộc framework cụ thể. Boot cũng có lối tùy biến ở cấp bootstrap để chọn hoặc tắt logging system qua system property `org.springframework.boot.logging.LoggingSystem` được tài liệu hóa.

Kiến trúc appender, vận chuyển log từ xa, lưu giữ, indexing và phân tích log thuộc logging/observability; `LoggingSystem` không biến Boot thành logging backend.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boot-logging-properties">Các property của Boot có thể cấu hình những khía cạnh logging runtime nào?</a>

<details>
<summary>Xem chi tiết</summary>

Boot cung cấp các điều khiển logging runtime phổ biến qua configuration properties. `logging.level.<logger-name>` đổi level của logger, `logging.level.root` điều khiển root logger. `logging.file.name` hoặc `logging.file.path` bật đầu ra file bên cạnh console, còn property pattern/charset được hỗ trợ tác động lên cấu hình mặc định của Boot.

```yaml
logging:
  level:
    root: INFO
    com.example.orders: DEBUG
  file:
    name: logs/application.log
```

Hãy dùng property khi yêu cầu nằm trong abstraction chung của Boot. Nếu cần appender, filter, encoder hoặc routing riêng của cách triển khai, hãy chuyển sang cấu hình logging native thay vì kỳ vọng các property tổng quát của Boot mô hình hóa toàn bộ logging framework.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-logging-config-files">Vì sao các file cấu hình logging dạng `-spring` quan trọng?</a>

<details>
<summary>Xem chi tiết</summary>

Boot hỗ trợ các file cấu hình native bình thường nhưng khuyến nghị biến thể `-spring` khi có thể, ví dụ `logback-spring.xml` hoặc `log4j2-spring.xml`. Lý do là thời điểm lifecycle: file chuẩn như `logback.xml` có thể được cách triển khai logging nạp quá sớm khiến Boot không còn kiểm soát đầy đủ bước khởi tạo.

Với `logback-spring.xml`, Boot có thể tham gia cấu hình và dùng các extension hỗ trợ profile/environment ở giai đoạn phù hợp. `logging.config` cũng có thể trỏ tới một vị trí rõ ràng.

File native phù hợp khi yêu cầu thật sự phụ thuộc cách triển khai. Cần giữ lựa chọn này rõ ràng vì từ đây quyền sở hữu hành vi nằm nhiều hơn ở logging framework cụ thể, không còn chỉ là Boot property.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="logging-groups-output-rotation">Logger groups, đầu ra và rotation nằm ở đâu trong lớp tích hợp Boot?</a>

<details>
<summary>Xem chi tiết</summary>

Logger group cho phép gom nhiều logger category dưới một tên logic rồi điều khiển bằng `logging.level.<group>`. Cách này tiện khi một subsystem trải qua nhiều package và lập trình viên cần một công tắc runtime thay vì nhiều entry rời rạc.

Boot cũng cung cấp các điều khiển đầu ra thuận tiện. Console logging có sẵn mặc định; đầu ra file bật qua `logging.file.name` hoặc `logging.file.path`. Với tích hợp Logback mặc định, Boot cung cấp property cho file rotation như kích thước file tối đa, lịch sử và tổng dung lượng giới hạn.

Đây là tính năng cấu hình runtime cục bộ. Việc vận chuyển, phân tích cú pháp, lưu giữ, correlation, indexing, dashboard và cảnh báo log thuộc hạ tầng observability/logging.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="logging-observability-handoff">Tích hợp logging của Boot kết thúc ở đâu và vận hành logging bắt đầu ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Chương này sở hữu logging bootstrap của Boot, `LoggingSystem`, các logging property phổ biến và quan hệ giữa Boot với cấu hình native. Trách nhiệm dừng khi ứng dụng đã phát log event/đầu ra đúng cấu hình.

Agent thu thập log, backend tập trung, schema có cấu trúc, retention, indexing, dashboard, cảnh báo và trace/log correlation thuộc observability. Actuator logger endpoint cũng là bề mặt quản lý production của module Actuator dù nó có thể đổi log level lúc runtime.

Khi có lỗi logging, hãy phân loại trước: "Boot không áp dụng startup property này" thuộc chương hiện tại; "collector không gửi file" hoặc "backend không truy vấn được field" thuộc hạ tầng observability.

</details>

- [Quay lại đầu trang](#back-to-top)
