# 📂 README MODULE STRUCTURE (VI)

* **1.RuntimeModel**
    * [RuntimeModel](readme/vi/menu/1.RuntimeModel/RuntimeModel.md)
* **2.ApplicationLifecycleEvents**
    * [ApplicationLifecycleEvents](readme/vi/menu/2.ApplicationLifecycleEvents/ApplicationLifecycleEvents.md)
* **3.ArgumentsAndRunners**
    * [ArgumentsAndRunners](readme/vi/menu/3.ArgumentsAndRunners/ArgumentsAndRunners.md)
* **4.AvailabilityFailureExit**
    * [AvailabilityFailureExit](readme/vi/menu/4.AvailabilityFailureExit/AvailabilityFailureExit.md)
* **5.TaskExecutionScheduling**
    * [TaskExecutionScheduling](readme/vi/menu/5.TaskExecutionScheduling/TaskExecutionScheduling.md)
* **6.VirtualThreads**
    * [VirtualThreads](readme/vi/menu/6.VirtualThreads/VirtualThreads.md)
* **7.StartupOptimization**
    * [StartupOptimization](readme/vi/menu/7.StartupOptimization/StartupOptimization.md)
* **8.RuntimeCustomization**
    * [RuntimeCustomization](readme/vi/menu/8.RuntimeCustomization/RuntimeCustomization.md)
* **9.Logging**
    * [Logging](readme/vi/menu/9.Logging/Logging.md)
* **10.SslBundles**
    * [SslBundles](readme/vi/menu/10.SslBundles/SslBundles.md)
* **11.DockerCompose**
    * [DockerCompose](readme/vi/menu/11.DockerCompose/DockerCompose.md)
* **12.RuntimeSynthesis**
    * [RuntimeSynthesis](readme/vi/menu/12.RuntimeSynthesis/RuntimeSynthesis.md)

# Spring Boot Application Runtime

Module này theo dõi ứng dụng Spring Boot sau khi `main()` bàn giao quyền điều khiển cho `SpringApplication`. Mục tiêu là xây mô hình runtime chi tiết xoay quanh lifecycle events, startup runners, trạng thái availability và exit, task execution/scheduling do Boot quản lý, tích hợp virtual thread, tinh chỉnh startup, khởi tạo logging sớm, SSL bundle dùng lại được, tích hợp Docker Compose lúc phát triển và các điểm tùy biến runtime được hỗ trợ.

## Bạn sẽ học gì?

Bạn sẽ học cách định vị hành vi trên dòng thời gian của SpringApplication, chọn extension point theo thời điểm lifecycle, phân biệt liveness với readiness, hiểu executor/scheduler mặc định của Boot và tác động khi bật virtual thread, tối ưu startup mà không che giấu lỗi, suy luận về quá trình khởi tạo logging sớm, dùng lại SSL bundle có tên, hiểu cách Boot phối hợp dịch vụ Compose cục bộ và xác định một vấn đề runtime thuộc Boot hay một lớp framework/hạ tầng lân cận.

## Kiến thức cần có

Bạn nên nắm Spring Boot fundamentals, externalized configuration và auto-configuration. Module giả định bạn có vốn từ cơ bản về Spring container và Java concurrency nhưng không dạy lại phần nội bộ lifecycle của Spring Framework, ngữ nghĩa `@Async`/`@Scheduled`, cơ chế Java virtual thread, logging framework, TLS/PKI hay kiến thức nền Docker/Compose.

## Lộ trình học

1. Xây mô hình SpringApplication runtime từ đầu đến cuối và ranh giới trách nhiệm.
2. Theo dõi lifecycle events, application arguments và startup runners có thứ tự.
3. Kết nối tiến trình startup với availability, chẩn đoán lỗi, shutdown và lúc tiến trình thoát.
4. Hiểu task execution/scheduling do Boot quản lý và tác động khi bật virtual thread.
5. Tinh chỉnh startup và chọn điểm tùy biến runtime dựa trên bằng chứng đo được.
6. Học tích hợp logging sớm của Boot và abstraction SSL bundle dùng lại được.
7. Hiểu lifecycle Docker Compose lúc phát triển và tích hợp service connection.
8. Tổng hợp trạng thái runtime, hooks, các dịch vụ được quản lý, chẩn đoán và điểm bàn giao giữa module thành một mô hình xử lý sự cố thống nhất.

## Ranh giới module

Module này chịu trách nhiệm cho hành vi runtime chi tiết của SpringApplication và các tích hợp runtime do Boot quản lý. Lựa chọn/cấu hình web server, TLS của server và graceful shutdown thuộc `web-runtime`; việc expose production endpoint thuộc `actuator`; precedence/binding của configuration source thuộc `externalized-configuration`; authoring/diagnostics của auto-configuration thuộc `auto-configuration`; ngữ nghĩa lifecycle/concurrency tổng quát của Spring, Java concurrency và virtual thread, vận hành logging, TLS/PKI và cơ chế Docker vẫn thuộc các curriculum chuyên trách.
