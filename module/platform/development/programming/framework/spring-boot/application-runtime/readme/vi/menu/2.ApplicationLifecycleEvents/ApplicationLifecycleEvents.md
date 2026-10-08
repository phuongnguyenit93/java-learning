<a id="back-to-top"></a>

# Vòng đời ứng dụng và application events

## Menu
- [Chuỗi event của SpringApplication diễn tiến như thế nào?](#springapplication-event-timeline)
- [Những event nào xảy ra trước khi ApplicationContext tồn tại?](#pre-context-events)
- [Context refresh, `ApplicationStartedEvent` và `ApplicationReadyEvent` khác nhau thế nào?](#context-started-ready-events)
- [Điều gì xảy ra trên nhánh startup thất bại?](#failed-event-path)
- [Vì sao một số listener phải được đăng ký trước khi bean được tạo?](#early-listener-registration)
- [Khi nào application event là runtime hook phù hợp?](#event-hook-selection)

## <a id="springapplication-event-timeline">Chuỗi event của SpringApplication diễn tiến như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

SpringApplication events đánh dấu các mốc có tên trên đường startup. Với Spring Boot 3.3, thứ tự chính là `ApplicationStartingEvent`, `ApplicationEnvironmentPreparedEvent`, `ApplicationContextInitializedEvent`, `ApplicationPreparedEvent`, `ApplicationStartedEvent`, một `AvailabilityChangeEvent` cho liveness, `ApplicationReadyEvent`, rồi `AvailabilityChangeEvent` cho readiness. Nếu startup gặp exception, `ApplicationFailedEvent` biểu diễn nhánh thất bại.

Thứ tự quan trọng vì mỗi mốc có bảo đảm khác nhau. Trước khi context tồn tại, listener không thể dựa vào bean. Sau refresh, context đã sẵn sàng. `ApplicationStartedEvent` vẫn xảy ra trước application/command-line runners; `ApplicationReadyEvent` xảy ra sau runners.

Hãy xem tên event như bằng chứng về giai đoạn lifecycle. Nếu listener cần trạng thái chưa tồn tại ở giai đoạn đó, thường nên chọn event muộn hơn thay vì cố dựng lại trạng thái còn thiếu.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Application Events and Listeners](https://docs.spring.io/spring-boot/3.3/reference/features/spring-application.html#features.spring-application.application-events-and-listeners)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pre-context-events">Những event nào xảy ra trước khi ApplicationContext tồn tại?</a>

<details>
<summary>Xem chi tiết</summary>

Các event sớm nhất của SpringApplication được phát khi bean graph thông thường chưa tồn tại. `ApplicationStartingEvent` xuất hiện gần đầu `run`, `ApplicationEnvironmentPreparedEvent` xuất hiện khi `Environment` đã biết nhưng context chưa được tạo, còn `ApplicationContextInitializedEvent` xuất hiện sau khi các context initializer chạy nhưng trước khi bean definitions được nạp.

Những giai đoạn này phù hợp với hạ tầng thực sự cần quan sát bootstrap rất sớm. Chúng không phù hợp cho application service thông thường vì dependency injection và bean ứng dụng chưa sẵn sàng.

Vì vậy câu hỏi nên là "công việc này cần nhìn thấy trạng thái nào?" thay vì "tôi có thể nghe event nào?" Nếu cần repository/service bình thường, hãy chờ giai đoạn muộn hơn thay vì ép bước khởi tạo nghiệp vụ vào bootstrap hook.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-started-ready-events">Context refresh, `ApplicationStartedEvent` và `ApplicationReadyEvent` khác nhau thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Ba mốc gần nhau rất dễ bị xem như một. Context refreshed nghĩa là quá trình refresh đã hoàn tất. `ApplicationStartedEvent` được phát sau refresh nhưng *trước* khi `ApplicationRunner` và `CommandLineRunner` chạy. `ApplicationReadyEvent` chỉ xuất hiện sau khi các runner đó đã hoàn tất.

Boot nối availability vào cùng dòng thời gian: liveness chuyển thành `CORRECT` sau `ApplicationStartedEvent`, còn readiness chuyển thành `ACCEPTING_TRAFFIC` sau `ApplicationReadyEvent`. Vì vậy thời gian chạy runner có ý nghĩa vận hành: runner kéo dài sẽ kéo dài readiness dù context đã đạt liveness `CORRECT`.

Khi chọn hook, hãy chọn đúng bảo đảm cần thiết. "Context tồn tại" yếu hơn "startup runners đã xong". Đây là cầu nối trực tiếp sang chương runners và availability.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="failed-event-path">Điều gì xảy ra trên nhánh startup thất bại?</a>

<details>
<summary>Xem chi tiết</summary>

Startup không phải lúc nào cũng đi đến `ApplicationReadyEvent`. Nếu exception thoát khỏi quá trình startup, Boot phát `ApplicationFailedEvent`. Listener bootstrap có thể dùng event này để ghi nhận lỗi và quan sát exception đã kết thúc startup.

Event chỉ là một phần của nhánh lỗi. Boot còn cho `FailureAnalyzer` chuyển các lỗi đã biết thành phần mô tả và hướng xử lý tập trung. Khi condition của auto-configuration có liên quan, condition evaluation report là bằng chứng bổ sung chứ không thay thế exception gốc.

Xử lý lỗi nên giữ nguyên nguyên nhân gốc. Listener có thể bổ sung telemetry hoặc dọn dẹp, nhưng không nên nuốt exception chỉ để startup trông như thành công. Chương Availability/Failure/Exit sẽ đi sâu hơn vào chẩn đoán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="early-listener-registration">Vì sao một số listener phải được đăng ký trước khi bean được tạo?</a>

<details>
<summary>Xem chi tiết</summary>

Listener chỉ tồn tại dưới dạng `@Bean` bình thường không thể quan sát event xảy ra trước khi `ApplicationContext` tạo bean đó. Vì vậy Boot cho phép đăng ký listener sớm trực tiếp bằng `SpringApplication.addListeners(...)`, `SpringApplicationBuilder.listeners(...)` hoặc cơ chế đăng ký listener tự động được hỗ trợ.

Đây là ràng buộc của lifecycle, không phải mẹo dependency injection. Chỉ đưa listener ra ngoài bean lifecycle khi event thực sự xảy ra sớm. Với event muộn, listener dạng bean thường dễ quản lý dependency và kiểm thử hơn.

Listener càng sớm thì càng nên có trách nhiệm hẹp, vì càng ít application service có thể dùng an toàn. Listener sớm phù hợp với mối quan tâm bootstrap hơn là bước khởi tạo nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="event-hook-selection">Khi nào application event là runtime hook phù hợp?</a>

<details>
<summary>Xem chi tiết</summary>

Application event phù hợp khi công việc về bản chất là *quan sát hoặc phản ứng với một chuyển trạng thái lifecycle*: môi trường đã chuẩn bị, context đã khởi động, readiness thay đổi, hoặc nhiều listener độc lập cần phản ứng mà publisher không biết trực tiếp về chúng.

Không nên đặt công việc kéo dài vào event listener đồng bộ chỉ vì event xuất hiện đúng thời điểm mong muốn. Spring application events mặc định được phát trên cùng thread, nên listener nặng có thể kéo dài hoặc chặn đường startup. Công việc phải hoàn tất trước readiness thường phù hợp runner; công việc liên tục nên chạy trên executor/scheduler.

Chọn hook dựa trên cả thời điểm và mục đích: event để quan sát lifecycle, runner cho công việc startup có thứ tự, cơ chế thực thi được quản lý cho công việc nền và API cấu hình/tùy biến để thay đổi hành vi Boot.

</details>

- [Quay lại đầu trang](#back-to-top)
