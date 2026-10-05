# 📂 README MODULE STRUCTURE (VI)

* **1.Introduction**
    * [CoreContainer](readme/vi/menu/1.Introduction/CoreContainer.md)
* **2.ContainerModel**
    * [ContainerModel](readme/vi/menu/2.ContainerModel/ContainerModel.md)
* **3.BeanRegistration**
    * [BeanRegistration](readme/vi/menu/3.BeanRegistration/BeanRegistration.md)
* **4.DependencyInjection**
    * [DependencyInjection](readme/vi/menu/4.DependencyInjection/DependencyInjection.md)
* **5.BeanScope**
    * [BeanScope](readme/vi/menu/5.BeanScope/BeanScope.md)
* **6.BeanLifecycle**
    * [BeanLifecycle](readme/vi/menu/6.BeanLifecycle/BeanLifecycle.md)
* **7.EnvironmentAndSpEL**
    * [EnvironmentAndSpEL](readme/vi/menu/7.EnvironmentAndSpEL/EnvironmentAndSpEL.md)
* **8.ApplicationContextServices**
    * [ApplicationContextServices](readme/vi/menu/8.ApplicationContextServices/ApplicationContextServices.md)
* **9.ExtensionPoints**
    * [ExtensionPoints](readme/vi/menu/9.ExtensionPoints/ExtensionPoints.md)
* **10.ContainerDesignAndDiagnostics**
    * [ContainerDesignAndDiagnostics](readme/vi/menu/10.ContainerDesignAndDiagnostics/ContainerDesignAndDiagnostics.md)

# Spring Core Container

Spring Core Container là nền tảng quản lý đồ thị đối tượng của Spring Framework. Module này tập trung vào cách container tiếp nhận configuration metadata, đăng ký bean definitions, phân giải dependency, tạo bean, quản lý scope/lifecycle và cung cấp các dịch vụ nền qua `ApplicationContext`.

## Bạn sẽ học gì?

Lộ trình bắt đầu từ lý do IoC/Dependency Injection tồn tại, sau đó xây mô hình tư duy về `BeanFactory`/`ApplicationContext`, đăng ký bean, phân giải dependency, scope và lifecycle. Phần sau mở rộng sang `Environment`, `PropertySource`, profiles, SpEL, `Resource`, application events, i18n, context hierarchy và các điểm mở rộng của container như `BeanFactoryPostProcessor`, `BeanPostProcessor`, `FactoryBean`.

Các chương cuối kết nối toàn bộ cơ chế thành một luồng end-to-end để bạn có thể thiết kế đồ thị đối tượng có chủ đích và chẩn đoán lỗi startup/dependency thay vì chỉ ghi nhớ annotation.

## Kiến thức nền cần có

Bạn nên nắm Java Core: class/object, interface, annotation, reflection ở mức cơ bản, exception, generic và collections. Không cần biết Spring Boot trước; module này cố ý dạy cơ chế của Spring Framework container trước những tiện ích mà Boot cung cấp.

## Lộ trình học

1. Hiểu vì sao Spring cần IoC Container và Dependency Injection.
2. Xây mô hình tư duy về `BeanDefinition`, `BeanFactory` và `ApplicationContext`.
3. Học cách bean được đăng ký và cấu hình.
4. Hiểu dependency injection, cách chọn candidate và các lỗi phân giải dependency.
5. Học scope, lazy creation và scoped dependencies.
6. Theo dõi bean lifecycle, thứ tự startup/shutdown và callbacks.
7. Hiểu `Environment`, properties, profiles và SpEL.
8. Khám phá các dịch vụ nền của `ApplicationContext`: resources, events, i18n và hierarchy.
9. Học các điểm mở rộng của container.
10. Tổng hợp thành mô hình thiết kế và chẩn đoán end-to-end.

## Ranh giới của module

Module này không dạy Spring Boot Config Data/auto-configuration, Spring AOP chuyên sâu, transaction/cache semantics, MVC/WebFlux request processing, validation/data binding hay Spring Messaging/STOMP. Các chủ đề đó được chuyển sang những module Spring Framework/Spring Boot tương ứng.
