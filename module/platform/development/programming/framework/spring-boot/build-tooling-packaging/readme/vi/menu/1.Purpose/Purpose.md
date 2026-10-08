<a id="back-to-top"></a>

# Vì sao Spring Boot cần công cụ build riêng

## Menu
- [Build tooling của Spring Boot là gì và vì sao tồn tại?](#build-tooling-purpose)
- [Boot giải quyết bài toán build và packaging nào?](#build-tooling-problem)
- [Trách nhiệm build-time kết thúc ở đâu và runtime bắt đầu ở đâu?](#build-time-runtime-boundary)
- [Build tooling của Boot bàn giao cho hạ tầng deployment ở đâu?](#build-deployment-handoff)

## <a id="build-tooling-purpose">Build tooling của Spring Boot là gì và vì sao tồn tại?</a>

<details>
<summary>Xem chi tiết</summary>

Build tooling của Spring Boot là lớp tích hợp giúp Gradle hoặc Maven thực hiện các công việc **đặc thù của ứng dụng Boot**. Gradle/Maven vẫn sở hữu compilation, dependency resolution, task/lifecycle và project model; Boot bổ sung convention cùng task/goal để chạy ứng dụng, tạo executable archive và tạo OCI image.

Mô hình nên nhớ:

```text
project Gradle/Maven thông thường
        ↓
Spring Boot plugin
        ↓
run/package/image theo mô hình Boot
        ↓
artifact chạy được hoặc OCI image
```

Lớp này tồn tại vì ứng dụng Boot không chỉ là một thư mục chứa `.class`. Artifact bàn giao thường cần entry point rõ ràng, layout dependency, loader phù hợp và đôi khi cần tích hợp image build. Boot chuẩn hóa các mối quan tâm đó nhưng không thay thế build system nền.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Gradle Plugin Reference](https://docs.spring.io/spring-boot/3.3/gradle-plugin/)
- [Spring Boot 3.3 — Maven Plugin Reference](https://docs.spring.io/spring-boot/3.3/maven-plugin/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="build-tooling-problem">Boot giải quyết bài toán build và packaging nào?</a>

<details>
<summary>Xem chi tiết</summary>

Nếu không có tích hợp của Boot, mỗi đội phải tự quyết định cách gom dependency, xác định main class, làm cho artifact có thể chạy, giữ các phiên bản dependency tương thích với Boot và chuyển build output thành container image. Gradle/Maven thuần vẫn làm được, nhưng việc tự lặp lại các quyết định này dễ tạo layout không đồng nhất và tăng chi phí bảo trì.

Boot thu hẹp bài toán thành một đường đi được hỗ trợ:

```text
căn chỉnh dependency
→ chạy lúc phát triển
→ executable JAR/WAR
→ tùy chọn archive layering
→ tùy chọn OCI image
```

Phạm vi rất quan trọng: Boot giải quyết phần tích hợp quanh **ứng dụng Spring Boot**. Kiến thức Gradle tổng quát, Maven lifecycle, Docker networking, registry hoặc CI/CD vẫn thuộc các chủ sở hữu khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="build-time-runtime-boundary">Trách nhiệm build-time kết thúc ở đâu và runtime bắt đầu ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Build-time chuẩn bị thứ có thể được khởi chạy: phân giải đầu vào, biên dịch mã, đóng gói class và dependency, ghi metadata của archive và có thể tạo image. Runtime bắt đầu khi tiến trình ứng dụng được khởi động và `SpringApplication` bắt đầu bootstrap.

```text
bootJar / repackage
→ build-tooling-packaging sở hữu artifact

java -jar app.jar
→ JVM khởi động tiến trình
→ SpringApplication lifecycle bắt đầu
→ application-runtime sở hữu hành vi runtime
```

`bootRun` và `spring-boot:run` đi qua ranh giới này chỉ bằng một lệnh dành cho phát triển: plugin chuẩn bị classpath rồi khởi chạy ứng dụng, nhưng events, runners, availability, shutdown và ngữ nghĩa runtime vẫn thuộc `application-runtime`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="build-deployment-handoff">Build tooling của Boot bàn giao cho hạ tầng deployment ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Trách nhiệm của công cụ build của Boot kết thúc khi nó tạo ra, và nếu được cấu hình thì publish, một delivery artifact như executable JAR/WAR hoặc OCI image. Việc đưa artifact đó tới production là trách nhiệm deployment.

```text
Boot plugin
→ JAR/WAR/image
        ↓ bàn giao
artifact repository / image registry
→ CI/CD promotion
→ Kubernetes, VM, PaaS hoặc nền tảng runtime khác
```

Boot có thể cung cấp cấu hình về tên image, builder, environment hay publish action, nhưng không sở hữu rollout strategy, registry governance, cluster scheduling, secret distribution, autoscaling hay production topology.

</details>

- [Quay lại đầu trang](#back-to-top)
