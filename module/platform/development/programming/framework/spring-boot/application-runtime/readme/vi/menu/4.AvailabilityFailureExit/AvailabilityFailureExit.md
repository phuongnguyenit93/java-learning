<a id="back-to-top"></a>

# Trạng thái availability, phân tích lỗi và xử lý khi thoát

## Menu
- [Liveness và readiness có ý nghĩa gì trong Boot runtime?](#liveness-readiness-model)
- [Trạng thái availability thay đổi ở những thời điểm nào trong quá trình khởi động?](#availability-state-transitions)
- [Readiness thay đổi thế nào khi quá trình shutdown bắt đầu?](#availability-shutdown-transition)
- [`ApplicationAvailability` và `AvailabilityChangeEvent` biểu diễn trạng thái runtime như thế nào?](#availability-api)
- [Boot biến lỗi khởi động thành thông tin chẩn đoán có thể hành động như thế nào?](#startup-failure-analysis)
- [Shutdown hook của SpringApplication chịu trách nhiệm phần nào?](#shutdown-hook)
- [`ExitCodeGenerator` và `SpringApplication.exit` truyền trạng thái của tiến trình như thế nào?](#application-exit-codes)
- [Shutdown runtime tổng quát và graceful shutdown của web server tách nhau ở đâu?](#shutdown-boundaries)

## <a id="liveness-readiness-model">Liveness và readiness có ý nghĩa gì trong Boot runtime?</a>

<details>
<summary>Xem chi tiết</summary>

Liveness và readiness trả lời hai câu hỏi vận hành khác nhau. Liveness cho biết trạng thái nội bộ của ứng dụng còn đủ đúng để tiếp tục hoặc tự phục hồi hay không. Readiness cho biết ứng dụng có nên nhận lưu lượng ở thời điểm hiện tại hay không.

Khác biệt này ảnh hưởng trực tiếp cách dependency bên ngoài được dùng. Database tạm thời không sẵn sàng có thể khiến một số request thất bại, nhưng nếu coi đó là lỗi liveness thì nền tảng có thể khởi động lại hàng loạt instance và làm sự cố nặng hơn. Readiness có thể thận trọng hơn vì từ chối lưu lượng không đồng nghĩa kết thúc tiến trình.

Boot sở hữu các trạng thái availability này như tín hiệu runtime. Actuator có thể cung cấp chúng qua health groups, nhưng cấu hình endpoint/probe thuộc module Actuator.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="availability-state-transitions">Trạng thái availability thay đổi ở những thời điểm nào trong quá trình khởi động?</a>

<details>
<summary>Xem chi tiết</summary>

Boot căn availability mặc định theo dòng thời gian startup. Sau khi context refresh và `ApplicationStartedEvent` được phát, Boot phát thay đổi liveness sang `LivenessState.CORRECT`. Runners vẫn chạy sau mốc này. Khi runners hoàn tất, Boot phát `ApplicationReadyEvent`, rồi readiness chuyển sang `ReadinessState.ACCEPTING_TRAFFIC`.

```text
context refreshed
  -> ApplicationStartedEvent
  -> LIVE / CORRECT
  -> runners
  -> ApplicationReadyEvent
  -> READINESS / ACCEPTING_TRAFFIC
```

Chuỗi này giải thích vì sao đạt liveness không đồng nghĩa đã đạt readiness. Tiến trình có context hợp lệ trong khi công việc startup bắt buộc vẫn đang chạy. Khi triển khai chậm, hãy xác định ứng dụng đang mắc ở mốc nào trước khi sửa probe timeout.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="availability-shutdown-transition">Readiness thay đổi thế nào khi quá trình shutdown bắt đầu?</a>

<details>
<summary>Xem chi tiết</summary>

Availability cũng hữu ích khi ứng dụng rời trạng thái chạy ổn định. Khi shutdown bắt đầu, readiness cần chuyển khỏi trạng thái nhận lưu lượng để hạ tầng ngừng gửi công việc mới trong khi lifecycle shutdown tiếp tục.

Ở lớp Boot tổng quát, hãy nghĩ theo chuyển trạng thái và việc đóng context. Việc embedded HTTP server ngừng nhận request mới, chờ request đang xử lý bao lâu và server nào hỗ trợ hành vi nào thuộc `web-runtime` cùng cấu hình graceful shutdown.

Tách hai lớp này giúp tránh nhầm *tuyên bố ứng dụng không còn sẵn sàng nhận lưu lượng* với *cơ chế protocol/server cụ thể để hoàn tất request đang xử lý*. Chúng phối hợp nhưng không phải cùng một cơ chế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="availability-api">`ApplicationAvailability` và `AvailabilityChangeEvent` biểu diễn trạng thái runtime như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

`ApplicationAvailability` là phía đọc của mô hình availability: bean có thể inject nó để lấy liveness/readiness hiện tại. `AvailabilityChangeEvent` là phía thay đổi/quan sát: thành phần có thể lắng nghe chuyển trạng thái hoặc phát trạng thái mới khi ứng dụng có đủ bằng chứng nghiệp vụ để quyết định.

```java
AvailabilityChangeEvent.publish(
        eventPublisher,
        this,
        ReadinessState.REFUSING_TRAFFIC);
```

Hãy phát trạng thái có chủ đích. Một lỗi bên ngoài ngắn hạn không nên tự động biến thành `LivenessState.BROKEN`; Boot khuyến nghị liveness tập trung vào trạng thái nội bộ không thể tự phục hồi. API truyền đạt trạng thái, không tự quyết định chính sách vận hành.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Application Availability](https://docs.spring.io/spring-boot/3.3/reference/features/spring-application.html#features.spring-application.application-availability)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="startup-failure-analysis">Boot biến lỗi khởi động thành thông tin chẩn đoán có thể hành động như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Khi startup thất bại, Boot giữ nguyên đường exception rồi cho các `FailureAnalyzer` đã đăng ký cơ hội biến lỗi quen thuộc thành phần mô tả ngắn gọn cùng hướng xử lý cụ thể. Khối `APPLICATION FAILED TO START` vì thế là lớp chẩn đoán đặt trên exception gốc, không phải thứ thay thế stack trace/nguyên nhân gốc.

Nếu analyzer không giải thích được lỗi, hoặc quyết định auto-configuration có liên quan, có thể bật `debug` hoặc logging cho `ConditionEvaluationReportLoggingListener` để xem conditions report. Chi tiết condition matching vẫn thuộc module auto-configuration.

Thứ tự điều tra nên là: xác định exception gốc, đọc phân tích lỗi nếu có, rồi kiểm tra bằng chứng configuration/condition liên quan. Tránh đổi property ngẫu nhiên cho đến khi startup chạy.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Startup Failure](https://docs.spring.io/spring-boot/3.3/reference/features/spring-application.html#features.spring-application.startup-failure)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="shutdown-hook">Shutdown hook của SpringApplication chịu trách nhiệm phần nào?</a>

<details>
<summary>Xem chi tiết</summary>

`SpringApplication` mặc định đăng ký JVM shutdown hook để `ApplicationContext` được đóng khi JVM thoát theo đường bình thường. Việc đóng context cho phép destruction callback và thành phần lifecycle do Spring quản lý tham gia shutdown thay vì tài nguyên bị bỏ lại đột ngột.

Hook này là hành vi application-runtime tổng quát. Nó không quy định cách mọi protocol hoặc tài nguyên bên ngoài hoàn tất công việc. Từng công nghệ có lớp tích hợp lifecycle riêng; graceful shutdown của embedded web server thuộc `web-runtime`.

Trong thực tế nên giữ tài nguyên sống lâu trong lifecycle được quản lý khi có thể. Nếu ứng dụng tự tạo thread/tài nguyên không được quản lý ngoài context, việc Boot có shutdown hook không tự động khiến chúng được đóng theo thứ tự.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="application-exit-codes">`ExitCodeGenerator` và `SpringApplication.exit` truyền trạng thái của tiến trình như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Một số ứng dụng cần trả kết quả tiến trình cho hệ điều hành hoặc script điều phối. Boot hỗ trợ `ExitCodeGenerator` bean và `ExitCodeExceptionMapper` để chuyển kết quả ứng dụng thành exit code. `SpringApplication.exit(context)` thu thập generator và trả về code kết quả.

Việc gọi `SpringApplication.exit` không tự kết thúc JVM; ứng dụng dạng lệnh thường truyền code đó cho `System.exit(...)` khi muốn tiến trình dừng thật sự.

Cơ chế này hữu ích cho batch/CLI khi "hoàn tất với lỗi nghiệp vụ" cần biểu diễn để máy đọc được. Hãy giữ ánh xạ nhỏ, ổn định và có tài liệu vì exit code là hợp đồng với bên gọi tiến trình, không phải thay thế xử lý exception hay log.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="shutdown-boundaries">Shutdown runtime tổng quát và graceful shutdown của web server tách nhau ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Shutdown runtime tổng quát sở hữu câu chuyện tiến trình/context: readiness ngừng nhận công việc, JVM shutdown hook đóng `ApplicationContext`, lifecycle được quản lý chạy và tiến trình có thể trả exit code. Mô hình này áp dụng cho web, command-line và các dạng ứng dụng Boot khác.

Graceful shutdown của web server thêm một lớp riêng cho protocol: server ngừng nhận request mới và cho request đang xử lý cơ hội hoàn tất theo chính sách. Lựa chọn server, timeout và hành vi cụ thể thuộc `web-runtime`.

Khi chẩn đoán shutdown, hãy tách lớp. Context không đóng là vấn đề application-runtime; HTTP request không hoàn tất như mong muốn là web-runtime; thread không được quản lý giữ JVM sống lại là vấn đề Java/application lifecycle.

</details>

- [Quay lại đầu trang](#back-to-top)
