<a id="back-to-top"></a>

# Mô hình runtime của ứng dụng Spring Boot

## Menu
- [Spring Boot chịu trách nhiệm phần nào trong application runtime?](#application-runtime-role)
- [Vì sao Spring Boot cần một lớp điều phối runtime?](#application-runtime-problem)
- [Những đầu vào nào định hình runtime trước khi context sẵn sàng?](#runtime-inputs-and-context)
- [Các giai đoạn chính từ `SpringApplication.run` đến trạng thái sẵn sàng là gì?](#runtime-phase-model)
- [Điều gì thay đổi khi ứng dụng bước vào giai đoạn chạy ổn định?](#steady-state-runtime)
- [Application runtime bàn giao trách nhiệm sang các module lân cận ở đâu?](#runtime-ownership-handoffs)

## <a id="application-runtime-role">Spring Boot chịu trách nhiệm phần nào trong application runtime?</a>

<details>
<summary>Xem chi tiết</summary>

Application runtime của Spring Boot là lớp điều phối biến một `SpringApplication` đã nhận đủ đầu vào cấu hình thành một tiến trình đang chạy với `ApplicationContext` được quản lý. Ở module fundamentals, người học đã biết mô hình bootstrap ở mức tổng quan; tại đây trọng tâm chuyển sang *Boot thực hiện việc gì ở thời điểm nào* và *runtime hook nào chịu trách nhiệm cho việc đó*.

Boot điều phối quá trình chuẩn bị môi trường và context, phát lifecycle event, gọi startup runner, cập nhật application availability, cung cấp một số hạ tầng runtime qua auto-configuration và đóng context khi JVM shutdown. Bean trong ứng dụng vẫn là Spring bean bình thường và code vẫn chạy trên JVM; Boot bổ sung các quy ước và lớp tích hợp quanh chúng.

Phân biệt này rất hữu ích khi chẩn đoán: trước tiên xác định triệu chứng thuộc lớp điều phối runtime của Boot, cơ chế Spring Framework hay code ứng dụng. Các chương tiếp theo sẽ gắn từng loại vấn đề vào giai đoạn lifecycle cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="application-runtime-problem">Vì sao Spring Boot cần một lớp điều phối runtime?</a>

<details>
<summary>Xem chi tiết</summary>

Một ứng dụng thực tế có nhiều loại công việc phải diễn ra ở các thời điểm khác nhau. Cấu hình phải có trước khi bean được tạo, một số thành phần quan sát cần nhìn thấy startup rất sớm, bước khởi tạo có thể cần context đã refresh, còn lưu lượng chỉ nên được nhận khi công việc startup bắt buộc đã hoàn tất. Nếu xem mọi callback là tương đương, ứng dụng rất dễ gặp lỗi thứ tự và lỗi khó giải thích.

`SpringApplication` tạo ra một dòng thời gian chung cho các giai đoạn này. Nhờ đó Boot có thể gắn events, runners, chuyển trạng thái availability, phân tích lỗi, khởi tạo logging, executor được quản lý và dịch vụ phát triển vào những giai đoạn đã biết.

Giá trị chính không phải là có thêm callback, mà là có một ngôn ngữ runtime thống nhất để đặt công việc vào đúng giai đoạn nơi các điều kiện tiên quyết của nó thực sự tồn tại.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-inputs-and-context">Những đầu vào nào định hình runtime trước khi context sẵn sàng?</a>

<details>
<summary>Xem chi tiết</summary>

Trước khi công việc startup riêng của ứng dụng chạy, runtime đã được định hình bởi các đầu vào do những module Spring Boot trước đó sở hữu. Đối số dòng lệnh tham gia vào đầu vào runtime, externalized configuration chuẩn bị giá trị/profile, classpath ảnh hưởng auto-configuration nào có thể khớp và auto-configuration đóng góp bean vào context.

Điểm cần nhớ là các hook không nhìn thấy cùng một thế giới. Event ở giai đoạn môi trường có thể chạy khi `Environment` đã tồn tại nhưng `ApplicationContext` chưa có. Event sau refresh nhìn thấy context hoàn chỉnh hơn. Runner chạy sau refresh nên có thể dùng các bean ứng dụng thông thường.

Module này chỉ tiêu thụ những đầu vào đó. Thứ tự ưu tiên, binding, profiles và đánh giá condition vẫn thuộc `externalized-configuration` và `auto-configuration`. Khi chẩn đoán, hãy xác định giai đoạn trước rồi mới quay về module sở hữu đầu vào nếu cần.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-phase-model">Các giai đoạn chính từ `SpringApplication.run` đến trạng thái sẵn sàng là gì?</a>

<details>
<summary>Xem chi tiết</summary>

Có thể hình dung runtime như một chuỗi trạng thái ngày càng hoàn chỉnh:

```text
run bắt đầu
  -> Environment được chuẩn bị
  -> ApplicationContext được tạo và khởi tạo
  -> bean definitions được nạp
  -> context refresh hoàn tất
  -> ApplicationStartedEvent
  -> liveness = CORRECT
  -> ApplicationRunner / CommandLineRunner chạy
  -> ApplicationReadyEvent
  -> readiness = ACCEPTING_TRAFFIC
```

Nếu startup thất bại, Boot phát `ApplicationFailedEvent`. Ứng dụng web còn có các event liên quan web server trong khoảng giữa bước chuẩn bị context và `ApplicationStartedEvent`, nhưng cơ chế server thuộc `web-runtime`.

Hãy dùng dòng thời gian này như công cụ chọn hook. Code cần bean thì pre-context hook là quá sớm. Công việc bắt buộc phải xong trước readiness thường phù hợp runner hơn event phát trước runner.

### Tài liệu tham khảo

- [Spring Boot 3.3 — SpringApplication: Application Events and Listeners](https://docs.spring.io/spring-boot/3.3/reference/features/spring-application.html#features.spring-application.application-events-and-listeners)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="steady-state-runtime">Điều gì thay đổi khi ứng dụng bước vào giai đoạn chạy ổn định?</a>

<details>
<summary>Xem chi tiết</summary>

Khi ứng dụng đã sẵn sàng, câu hỏi chuyển từ "khởi động thế nào?" sang "Boot tiếp tục điều phối những dịch vụ và trạng thái runtime nào?" Công việc nền có thể dùng hạ tầng task do Boot quản lý, logging tiếp tục chịu cấu hình của lớp tích hợp Boot, các thành phần có thể đọc hoặc phát trạng thái availability và các client được hỗ trợ có thể dùng SSL bundle có tên. Trong môi trường phát triển, Boot cũng có thể quản lý lifecycle của các dịch vụ Docker Compose.

Trạng thái chạy ổn định không có nghĩa các mối quan tâm startup biến mất hoàn toàn. Executor có thể bị nghẽn, availability có thể đổi, log có thể phơi bày lỗi runtime và tiến trình vẫn cần shutdown/exit rõ ràng.

Boot cung cấp các điểm tích hợp cho những vấn đề này; chính sách concurrency tổng quát, tổng hợp log, thiết kế TLS và vận hành container vẫn thuộc các phần kiến thức chuyên trách.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-ownership-handoffs">Application runtime bàn giao trách nhiệm sang các module lân cận ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Mô hình runtime chỉ thực sự hữu ích khi người học biết điểm dừng. Thứ tự ưu tiên của property source và binding thuộc `externalized-configuration`; tạo bean theo điều kiện và custom auto-configuration thuộc `auto-configuration`. Lựa chọn/cấu hình web server, server TLS, proxy và graceful shutdown của server thuộc `web-runtime`.

Actuator cung cấp health/diagnostic endpoint có thể tiêu thụ trạng thái runtime, nhưng availability lifecycle bên dưới thuộc module này. Java concurrency sở hữu ngữ nghĩa của virtual thread; Spring Framework concurrency sở hữu `@Async`/`@Scheduled`. Observability/logging sở hữu pipeline/backend; security/network sở hữu TLS/PKI; containerization sở hữu cơ chế Docker/Compose.

Giữ rõ các điểm bàn giao giúp module đi sâu vào lớp tích hợp của Boot mà không trở thành bản sao của mọi công nghệ Boot có thể kết nối.

</details>

- [Quay lại đầu trang](#back-to-top)
