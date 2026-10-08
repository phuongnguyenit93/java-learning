# Spring Boot Build Tooling và Packaging

Module này giải thích cách Spring Boot nối một build Gradle hoặc Maven thông thường với quá trình chạy lúc phát triển, một artifact ứng dụng có thể thực thi và tùy chọn OCI image. Trọng tâm là phần tích hợp riêng của Boot: trách nhiệm của plugin, căn chỉnh dependency, cấu trúc executable archive, layering, khả năng tái lập, Buildpacks và điểm artifact được bàn giao cho hạ tầng triển khai.

## Bạn sẽ học gì?

Bạn sẽ hiểu Gradle plugin và Maven plugin của Spring Boot tham gia build như thế nào, cách `spring-boot-dependencies` giữ các dependency phổ biến ở tập version tương thích, vì sao task chạy lúc phát triển khác với chạy artifact đã đóng gói, cách `bootJar`/`bootWar` và Maven `repackage` tạo executable archive, vì sao Spring Boot Loader cùng nested archive tồn tại, cách layered packaging và đóng gói có thể tái lập hỗ trợ quá trình bàn giao, cũng như cách Boot tích hợp Cloud Native Buildpacks qua các task tạo image.

## Kiến thức cần có

Bạn nên hiểu mô hình ứng dụng Spring Boot cơ bản, starter và classpath, cùng các khái niệm dependency/build thông thường trong Gradle hoặc Maven. Module này không dạy cú pháp Gradle/Maven tổng quát, phần nội bộ của Docker, vận hành registry, pipeline CI/CD hay điều phối triển khai.

## Lộ trình học

1. Xác định vì sao Boot cần tích hợp build/packaging và ranh giới trách nhiệm của phần này.
2. Hiểu trách nhiệm của Gradle/Maven plugin và tập dependency nền do Boot quản lý.
3. Chạy ứng dụng bằng task có Boot hỗ trợ lúc phát triển và so sánh với `java -jar`.
4. Tạo executable JAR/WAR và hiểu mô hình nested archive cùng Spring Boot Loader.
5. Học layered packaging và reproducible packaging như các đặc tính của artifact bàn giao.
6. Hiểu mô hình Cloud Native Buildpacks làm nền cho tích hợp tạo image của Boot.
7. Tạo OCI image qua tích hợp Buildpacks của Boot và so sánh với hướng Dockerfile mà không đi sâu vào cơ chế container tổng quát.
8. Chọn chiến lược bàn giao artifact/image và phân loại lỗi thuộc build, package, image hay deployment.

## Ranh giới module

Module này chịu trách nhiệm cho build plugin của Spring Boot, tích hợp căn chỉnh dependency, executable packaging, định hướng về Spring Boot Loader, archive layering/khả năng tái lập và việc Boot gọi Buildpacks để tạo OCI image. Vòng đời ứng dụng sau khi tiến trình được khởi chạy thuộc `application-runtime`; kiến thức Gradle/Maven tổng quát thuộc curriculum build tool; cơ chế Dockerfile/container/registry/runtime và điều phối triển khai thuộc các module hạ tầng; AOT, GraalVM, runtime hint, hành vi theo mô hình closed-world và ngữ nghĩa của native image thuộc `native-image`.
