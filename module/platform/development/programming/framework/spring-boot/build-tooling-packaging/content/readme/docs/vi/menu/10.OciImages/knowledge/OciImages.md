<a id="back-to-top"></a>

# Tạo OCI image bằng Spring Boot

## Menu
- [`bootBuildImage` hoặc Maven `build-image` tạo ra điều gì?](#boot-build-image)
- [Ứng dụng Boot trở thành OCI image như thế nào?](#oci-image-output)
- [Vì sao quá trình tạo image cần truy cập Docker daemon hoặc container engine tương thích được hỗ trợ?](#image-builder-connection)
- [Tên image, tag và việc publish được cấu hình thế nào ở ranh giới tích hợp của Boot?](#image-publication)

## <a id="boot-build-image">`bootBuildImage` hoặc Maven `build-image` tạo ra điều gì?</a>

<details>
<summary>Xem chi tiết</summary>

Gradle `bootBuildImage` và Maven `build-image` dùng Cloud Native Buildpacks để tạo **image ứng dụng tương thích OCI**. Đầu ra chứa ứng dụng, các layer runtime do builder/buildpacks chọn và cấu hình OCI image cần cho container runtime.

Điều này khác `bootJar`: archive là JVM delivery artifact; image là container delivery artifact. Quá trình dựng image có thể dùng archive làm đầu vào bên trong, nhưng đầu ra bàn giao cuối cùng là image name/reference thay vì chỉ một JAR path.

Task tạo image đi qua vòng đời CNB chứ không chuyển đổi Dockerfile. Khác biệt này ảnh hưởng cách chẩn đoán: lỗi nhắc tới bước phát hiện của builder, giai đoạn buildpack hoặc metadata khởi chạy thuộc đường Buildpacks; lỗi instruction của Dockerfile thì không. Cả hai có thể tạo OCI image nhưng cơ chế build khác nhau.

### Tài liệu tham khảo

- [Spring Boot 3.3 Gradle Plugin — Packaging OCI Images](https://docs.spring.io/spring-boot/3.3/gradle-plugin/packaging-oci-image.html)
- [Spring Boot 3.3 Maven Plugin — Packaging OCI Images](https://docs.spring.io/spring-boot/3.3/maven-plugin/build-image.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oci-image-output">Ứng dụng Boot trở thành OCI image như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Ở mức người học, đường đi là:

```text
đầu vào build ứng dụng Boot
        ↓
builder + các buildpack đã chọn
        ↓
phát hiện hỗ trợ runtime/build
        ↓
tạo reusable layers
        ↓
kết hợp run image
        ↓
OCI application image
```

Image đã có metadata về tiến trình do buildpack chuẩn bị nên nhóm ứng dụng không phải tự tái tạo toàn bộ lệnh classpath trong Dockerfile nếu chọn Buildpacks. Build log là bằng chứng quan trọng để biết buildpack nào tham gia và runtime/layer nào được chọn.

Image kết quả chứa metadata về tiến trình do buildpack chọn để container runtime có thể khởi động ứng dụng bằng tiến trình được đóng góp trong build. Đây là một lý do Buildpacks tạo được đường application-to-image chuẩn hóa: repository không phải lặp lại toàn bộ lệnh khởi chạy Java và logic lắp ghép runtime layer.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="image-builder-connection">Vì sao quá trình tạo image cần truy cập Docker daemon hoặc container engine tương thích được hỗ trợ?</a>

<details>
<summary>Xem chi tiết</summary>

Boot plugin điều phối quá trình dựng image nhưng image vẫn phải được tạo, load và có thể publish qua container-engine connection được hỗ trợ. Mặc định thường là Docker daemon mà môi trường chạy Gradle/Maven truy cập được; kết nối từ xa/thay thế phải theo cấu hình chính thức của plugin.

```text
Boot application build bình thường
nhưng bootBuildImage không connect được
→ kết nối/cấu hình container engine
→ không phải SpringApplication startup
```

CI runner vì vậy cần quyền truy cập và thông tin xác thực rõ ràng; Docker Desktop socket trên máy lập trình viên không tự xuất hiện trong CI.

Boot 3.3 yêu cầu rõ Docker daemon cho Gradle bootBuildImage và Maven build-image. Task/goal trước tiên kiểm tra cấu hình/context của Docker CLI rồi có thể dùng DOCKER_HOST, DOCKER_CONTEXT, các biến môi trường liên quan TLS hoặc docker connection settings của plugin. Vì vậy kết nối daemon là điều kiện tiên quyết quan trọng của build.

Khi diagnose, nên tách ba connection: công cụ build tới Docker daemon, builder container tới external dependency sources và task tới registry để pull/publish. Proxy hoặc credential fix cho một path có thể không ảnh hưởng hai path còn lại.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="image-publication">Tên image, tag và việc publish được cấu hình thế nào ở ranh giới tích hợp của Boot?</a>

<details>
<summary>Xem chi tiết</summary>

Boot plugin cho phép cấu hình tên/tag của image đích và yêu cầu publish bằng thông tin xác thực registry. Tham chiếu image nên được coi là một phần hợp đồng bàn giao để CI biết artifact nào cần được đưa lên môi trường tiếp theo.

Boot chỉ truyền cấu hình đặt tên/publish vào quá trình tạo image. Chính sách lưu giữ của registry, quyền truy cập, chính sách lỗ hổng, tính bất biến của tag, ký artifact và đưa qua các môi trường vẫn thuộc registry/DevOps.

Không nên dùng mutable tag như `latest` làm identity duy nhất của release. Delivery system nên lưu version/digest bất biến để truy vết ngay cả khi có convenience tag.

Gradle task có thể suy ra tham chiếu image mặc định từ tên/version của project và nhận thêm tag, còn publish là tùy chọn phải bật rõ ràng. Tham chiếu image đầy đủ có thể chứa registry host, repository path, tag và dạng digest; lựa chọn cách đặt tên vì vậy trở thành hợp đồng bàn giao dùng chung với CI/hệ thống triển khai.

Boot có thể truyền thông tin xác thực registry cho việc tạo/phát hành image, nhưng việc lưu trữ/luân chuyển secret nên đến từ môi trường build hoặc cơ chế secret của CI thay vì source-controlled build file. Plugin cần credentials nhưng không nên trở thành nơi quản lý thông tin xác thực.

</details>

- [Quay lại đầu trang](#back-to-top)
