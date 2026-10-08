<a id="back-to-top"></a>

# Đánh đổi packaging và bàn giao sang deployment

## Menu
- [Chọn executable JAR, WAR, unpacked archive hay OCI image như thế nào?](#artifact-strategy)
- [Môi trường bàn giao nên ảnh hưởng lựa chọn packaging ra sao?](#delivery-environment-fit)
- [Ranh giới giữa Boot packaging và vận hành deployment nằm ở đâu?](#deployment-ownership-boundary)
- [Packaging JVM thông thường bàn giao sang native-image ở đâu?](#normal-jvm-vs-native-handoff)
- [Toàn bộ luồng build và packaging của Boot kết nối với nhau thế nào?](#end-to-end-build-flow)
- [Phân loại lỗi giữa plugin, packaging, image và deployment như thế nào?](#build-failure-classification)

## <a id="artifact-strategy">Chọn executable JAR, WAR, unpacked archive hay OCI image như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Chọn delivery artifact theo execution contract của môi trường:

```text
tiến trình JVM độc lập
→ executable JAR

external servlet container truyền thống
→ WAR

platform hưởng lợi từ extracted dependencies/files
→ unpacked/extracted archive

container-based delivery
→ OCI image
```

Đây không phải các “cấp độ trưởng thành”. JAR hoàn toàn có thể là production artifact; OCI image phù hợp khi platform dùng container; WAR vẫn đúng nếu external-container deployment là yêu cầu thật.

Cũng cần xét artifact cần gì từ môi trường đích. Executable JAR yêu cầu JVM tương thích bên ngoài artifact; image từ Buildpacks thường mang runtime đã chọn bên trong image; WAR dựa vào hợp đồng với servlet container bên ngoài. Lựa chọn packaging vì vậy dịch chuyển trách nhiệm giữa artifact và platform dù mã ứng dụng không đổi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="delivery-environment-fit">Môi trường bàn giao nên ảnh hưởng lựa chọn packaging ra sao?</a>

<details>
<summary>Xem chi tiết</summary>

Bắt đầu từ cách artifact sẽ được khởi chạy, cập nhật, cache, quan sát và bảo vệ. VM service manager có thể chỉ cần JAR + JDK. PaaS có thể tự giải nén ứng dụng. Kubernetes thường nhận container image. Môi trường servlet của tổ chức có thể bắt buộc WAR.

Không nên đưa container vào chỉ vì Boot có thể build image, cũng không nên ép dùng JAR thô khi đơn vị triển khai tự nhiên của platform là OCI image. Packaging tốt giảm phần chuyển đổi giữa đầu ra build và nền tảng runtime đồng thời giữ phạm vi trách nhiệm rõ ràng.

Định dạng phù hợp nên giảm chuyển đổi ngoài chủ đích sau build. Nếu platform luôn chuyển JAR thành image, cần quyết định việc chuyển đổi đó thuộc nền tảng trung tâm hay Boot nên tạo image cuối sớm hơn. Mỗi bước repackaging thêm vào tạo thêm nơi định danh, provenance, cấu hình hoặc nội dung dependency có thể lệch dần.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="deployment-ownership-boundary">Ranh giới giữa Boot packaging và vận hành deployment nằm ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Boot packaging tạo đơn vị bàn giao. Vận hành triển khai quyết định **nó chạy ở đâu, khi nào và theo chính sách nào**.

```text
công cụ build của Boot
→ tạo JAR/WAR/image

hạ tầng triển khai
→ lưu/promote artifact
→ đưa cấu hình/secret vào
→ lên lịch tiến trình
→ định tuyến traffic
→ rollout / rollback / scale
```

`BOOT-INF` sai là lỗi packaging; image pull bị chặn bởi chính sách registry là lỗi triển khai; artifact khởi chạy được nhưng tạo bean thất bại là lỗi runtime/cấu hình ứng dụng.

Bàn giao bất biến làm phạm vi trách nhiệm rõ hơn. Giai đoạn build nên xác định chính xác checksum của JAR hoặc digest của image đã tạo; deployment đưa chính đơn vị bàn giao đó qua các môi trường thay vì tự build lại source. Build lại trong deployment trộn trách nhiệm build và rollout, khiến artifact production khó truy vết về bằng chứng build đã được xác minh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="normal-jvm-vs-native-handoff">Packaging JVM thông thường bàn giao sang native-image ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Đường mặc định của module này giả định application chạy trên JVM: executable JAR/WAR hoặc container image có JVM runtime. Native image thay đổi mô hình vì application analysis và AOT processing tham gia tạo platform-specific native executable.

Build plugin có thể cung cấp command/điểm vào Buildpacks để kích hoạt native build, nhưng hành vi closed-world, runtime hints, artifact do AOT sinh ra, khả năng tương thích native và native test thuộc module `native-image`.

Dạng đầu ra cho thấy điểm bàn giao xảy ra ở đâu. JVM image vẫn chứa JVM và chạy bytecode được đóng gói theo cách thông thường; native image chứa executable theo từng platform được tạo sau phân tích AOT/native. Việc cả hai cuối cùng đều có thể nằm trong OCI image không làm ngữ nghĩa build của chúng trở nên tương đương.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="end-to-end-build-flow">Toàn bộ luồng build và packaging của Boot kết nối với nhau thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Luồng JVM-oriented đầy đủ:

```text
khai báo application dependencies
→ căn chỉnh version theo Boot baseline
→ dùng Boot plugin integration
→ bootRun / spring-boot:run cho development
→ bootJar/bootWar/repackage tạo executable artifact
→ tùy chọn layered/extracted packaging
→ tùy chọn Buildpacks tạo OCI image
→ bàn giao artifact/image cho hạ tầng triển khai
```

Mỗi stage trả lời một câu hỏi khác nhau. Tách chúng giúp khoanh vùng lỗi và tránh biến development convenience task thành production contract ngoài ý muốn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="build-failure-classification">Phân loại lỗi giữa plugin, packaging, image và deployment như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Dùng boundary thành công cuối cùng làm bằng chứng:

```text
phân giải dependency / áp dụng plugin bị lỗi
→ thiết lập công cụ build hoặc Boot plugin

biên dịch được, bootJar/repackage bị lỗi
→ Boot packaging/main-class/archive config

JAR chạy được, bootBuildImage bị lỗi
→ Buildpacks/builder/container-engine/image config

dựng image trên máy cục bộ thành công nhưng publish/pull lỗi
→ registry/credentials/hạ tầng triển khai

artifact khởi chạy được, `ApplicationContext` gặp lỗi
→ runtime/cấu hình/auto-configuration của ứng dụng
```

Phân loại theo phân hệ chưa hoàn thành trách nhiệm hữu ích hơn gọi mọi lỗi trong `./gradlew` hoặc `mvn` là cùng một “lỗi build”.

Nên thêm một điểm kiểm tra trước khi quy lỗi cho deployment: xác minh đúng đầu ra bất biến đã đi qua bước bàn giao. Với JAR, ghi checksum rồi khởi chạy chính file đó. Với image, ghi digest được build và digest runtime thực sự kéo về. Cùng một tag có thể thay đổi là bằng chứng yếu hơn cùng một digest.

Cách này biến việc phân loại lỗi thành chuỗi artifact có thể xác minh thay vì chuỗi giả định. Nếu digest do bản build từ CI chạy tốt trên máy cục bộ nhưng cluster kéo digest khác, quá trình build image của Boot có thể hoàn toàn đúng và đường đưa/tag artifact khi triển khai mới là phần chịu trách nhiệm.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Container Images](https://docs.spring.io/spring-boot/3.3/reference/packaging/container-images/)
- [Spring Boot 3.3 — Efficient Deployments](https://docs.spring.io/spring-boot/3.3/reference/packaging/efficient.html)

</details>

- [Quay lại đầu trang](#back-to-top)
