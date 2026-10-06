# 📂 README MODULE STRUCTURE (VI)

* **1.SpringBootPurpose**
    * [SpringBootPurpose](readme/vi/menu/1.SpringBootPurpose/SpringBootPurpose.md)
* **2.ApplicationBootstrap**
    * [ApplicationBootstrap](readme/vi/menu/2.ApplicationBootstrap/ApplicationBootstrap.md)
* **3.PrimaryConfiguration**
    * [PrimaryConfiguration](readme/vi/menu/3.PrimaryConfiguration/PrimaryConfiguration.md)
* **4.StartersAndClasspath**
    * [StartersAndClasspath](readme/vi/menu/4.StartersAndClasspath/StartersAndClasspath.md)
* **5.StartupObservation**
    * [StartupObservation](readme/vi/menu/5.StartupObservation/StartupObservation.md)
* **6.ApplicationShapes**
    * [ApplicationShapes](readme/vi/menu/6.ApplicationShapes/ApplicationShapes.md)
* **7.DevTools**
    * [DevTools](readme/vi/menu/7.DevTools/DevTools.md)
* **8.PackagingMentalModel**
    * [PackagingMentalModel](readme/vi/menu/8.PackagingMentalModel/PackagingMentalModel.md)
* **9.FundamentalsSynthesis**
    * [FundamentalsSynthesis](readme/vi/menu/9.FundamentalsSynthesis/FundamentalsSynthesis.md)

# Spring Boot Fundamentals

Spring Boot Fundamentals xây mô hình tư duy giúp learner hiểu các module Spring Boot phía sau. Module giải thích vì sao Boot tồn tại, cách Java `main` giao công việc khởi động cho `SpringApplication`, vai trò của `@SpringBootApplication`, ảnh hưởng của starter và classpath, cũng như cách đọc đầu ra khởi động thay vì xem Boot là cơ chế tự động không thể giải thích.

## Bạn sẽ học gì?

Bạn sẽ liên hệ Spring Framework với các quy ước của Boot, theo dõi một ứng dụng từ lúc khởi động đến khi `ApplicationContext` chạy và dừng có trật tự, hiểu vai trò của lớp cấu hình chính, phân biệt starter với auto-configuration, nhận biết dạng non-web/Servlet/reactive, dùng DevTools đúng mục đích development và xây mental model cấp cao về packaging.

## Kiến thức cần có

Bạn nên hiểu ứng dụng Java cơ bản, class, annotation, dependency và Spring Framework container ở mức nhập môn. Không cần biết sâu về auto-configuration, Config Data, embedded-server internals, Actuator, testing hay build plugin; những nội dung đó thuộc các module Spring Boot tiếp theo.

## Lộ trình học

1. Hiểu Spring Boot là gì, vì sao tồn tại và liên hệ thế nào với Spring Framework.
2. Theo dõi `SpringApplication` từ `main` đến `ApplicationContext` đang chạy và mô hình vòng đời start/run/stop cấp cao.
3. Hiểu lớp cấu hình chính và `@SpringBootApplication`.
4. Học cách starter, dependency được quản lý và classpath liên hệ với hành vi Boot.
5. Đọc đầu ra khởi động như bằng chứng về những gì Boot thực sự tạo và lựa chọn.
6. Phân biệt dạng non-web, Servlet, reactive và xây mô hình embedded server.
7. Hiểu vai trò cùng ranh giới production của DevTools.
8. Xây mô hình về executable Boot packaging mà chưa đi sâu vào build plugin.
9. Tổng hợp mô hình và chuyển từng mảng chuyên sâu sang module Spring Boot chịu trách nhiệm.

## Ranh giới module

Module này chủ ý dừng ở mức fundamentals. Config Data/thứ tự ưu tiên property/binding thuộc `externalized-configuration`; quyết định có điều kiện và custom auto-configuration thuộc `auto-configuration`; vòng đời runtime chi tiết, logging, task execution và Docker Compose integration thuộc `application-runtime`; cấu hình server/TLS/proxy/graceful shutdown thuộc `web-runtime`; build plugin, Boot Loader chi tiết, layer và image thuộc `build-tooling-packaging`; production endpoints, chiến lược testing và AOT/native execution thuộc các module chuyên trách.
