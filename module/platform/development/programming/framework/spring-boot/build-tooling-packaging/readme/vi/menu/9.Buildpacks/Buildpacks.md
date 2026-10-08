<a id="back-to-top"></a>

# Cloud Native Buildpacks

## Menu
- [Builder, buildpack và run image là gì?](#buildpack-model)
- [Người dùng Boot cần hiểu phần nào của vòng đời Buildpack?](#buildpack-lifecycle-boundary)
- [Buildpack layer và cache hỗ trợ các lần build lặp lại như thế nào?](#buildpack-layers-and-caches)
- [Spring Boot cho phép tùy chỉnh những đầu vào Buildpack nào?](#buildpack-customization)
- [Tích hợp Buildpack kết thúc ở đâu và ngữ nghĩa của native image bắt đầu ở đâu?](#buildpack-native-boundary)

## <a id="buildpack-model">Builder, buildpack và run image là gì?</a>

<details>
<summary>Xem chi tiết</summary>

Cloud Native Buildpacks chuyển đầu vào ứng dụng thành OCI image mà nhóm ứng dụng không cần tự viết toàn bộ Dockerfile. Cần tách ba vai trò:

```text
builder image
→ môi trường build + lifecycle + tập buildpack khả dụng

buildpack
→ phát hiện nhu cầu ứng dụng và đóng góp runtime/build layer

run image
→ base runtime image của application image đầu ra
```

Spring Boot build plugin nối mô hình này vào Gradle/Maven, cung cấp mặc định và truyền ứng dụng/cấu hình vào vòng đời. Đặc tả và cách triển khai Buildpacks tổng quát vẫn thuộc hệ sinh thái Buildpacks.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Packaging OCI Images](https://docs.spring.io/spring-boot/3.3/gradle-plugin/packaging-oci-image.html)
- [Cloud Native Buildpacks Documentation](https://buildpacks.io/docs/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="buildpack-lifecycle-boundary">Người dùng Boot cần hiểu phần nào của vòng đời Buildpack?</a>

<details>
<summary>Xem chi tiết</summary>

Lập trình viên Boot không cần tự triển khai vòng đời nhưng cần hiểu đủ để phân loại lỗi:

```text
đầu vào ứng dụng
→ analyze trạng thái image trước đó / registry
→ phát hiện buildpack phù hợp
→ restore layer/trạng thái cache có thể tái sử dụng
→ build layer ứng dụng/runtime
→ export OCI image
```

Bước phát hiện thất bại thì kiểm tra builder/buildpack và đầu vào ứng dụng. Giai đoạn build thất bại thì xem log buildpack phụ trách. Lỗi export/publish có thể thuộc daemon, registry hoặc credentials. Chi tiết thuật toán lifecycle sâu hơn thuộc phần kiến thức Buildpacks.

Task Boot chỉ điều phối vòng đời này chứ không triển khai từng giai đoạn. Vì vậy nhật ký build là nguồn bằng chứng đầu tiên: chúng cho biết builder image, buildpack tham gia, loại ứng dụng được phát hiện, layer được đóng góp và kết quả export. Khi bước phát hiện lỗi, đổi cấu hình runtime của ứng dụng thường không giúp vì ứng dụng còn chưa được khởi chạy.

Chỉ cần giữ cách suy luận về vòng đời ở mức khái quát nhưng đúng thứ tự. Analyze đọc/xác thực trạng thái image trước đó và quyền truy cập registry; detect quyết định buildpack áp dụng; restore khôi phục layer có thể tái sử dụng; build đóng góp nội dung ứng dụng/runtime; export lắp ghép kết quả OCI. Cách triển khai chi tiết của vòng đời CNB nằm ngoài module.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="buildpack-layers-and-caches">Buildpack layer và cache hỗ trợ các lần build lặp lại như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Buildpacks tách thành phần runtime và nội dung ứng dụng thành các layer có thể tái sử dụng, đồng thời dùng cache build để giữ trạng thái tốn kém nếu đầu vào liên quan không đổi.

Một thay đổi application class không nhất thiết phải dựng lại mọi layer runtime/dependency. Mức tái sử dụng phụ thuộc builder/buildpack và cấu hình cache, nhưng mô hình tư duy giống cách phân layer archive: tách nội dung theo nhịp thay đổi để phần ổn định tồn tại qua nhiều lần chỉnh mã nguồn.

Cache không bảo đảm luôn tái sử dụng được dữ liệu. Builder, dependency, phiên bản buildpack, environment hoặc việc xóa cache rõ ràng đều có thể làm trạng thái mất hiệu lực.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="buildpack-customization">Spring Boot cho phép tùy chỉnh những đầu vào Buildpack nào?</a>

<details>
<summary>Xem chi tiết</summary>

Tích hợp Gradle/Maven của Boot cho phép cấu hình các đầu vào thường cần như tên image, builder/run image, biến môi trường mà buildpack hiểu, buildpack bổ sung, binding, cache, một số thiết lập build liên quan network và thông tin xác thực publish/registry.

Nên dùng mức trừu tượng cao nhất mà công cụ hỗ trợ. Nếu buildpack đã định nghĩa biến môi trường cho một lựa chọn JVM, hãy truyền đầu vào đó thay vì thay cả builder chỉ để đổi một chi tiết.

Cấu hình cũng phải tái lập được trong CI; đừng phụ thuộc vào trạng thái daemon riêng trên máy một lập trình viên.

Boot 3.3 cung cấp tùy biến ở nhiều mức: chọn builder hoặc run image, truyền biến môi trường cho buildpack, thay/đổi thứ tự buildpack, gắn binding, chọn mạng cho builder, cấu hình clean-cache và kiểm soát build/launch cache. Đây là các đầu vào tích hợp truyền vào builder, không phải lệnh Dockerfile tùy ý.

Trong quy trình cần lặp lại được, nên ưu tiên tham chiếu builder bất biến hoặc quy tắc nâng cấp do tổ chức quản lý. Tag builder dạng biến động có thể làm đổi hành vi JDK/buildpack dù mã nguồn không thay đổi, khiến lỗi khó tái hiện.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="buildpack-native-boundary">Tích hợp Buildpack kết thúc ở đâu và ngữ nghĩa của native image bắt đầu ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Buildpacks có thể tạo image ứng dụng JVM và, với đường native build phù hợp, image chứa native executable. Module này chỉ chịu trách nhiệm cho **cách Boot gọi image build**. AOT, ràng buộc closed-world, runtime hints, khả năng tương thích GraalVM và native testing thuộc `native-image`.

```text
bootBuildImage / build-image
→ build-tooling-packaging

lời gọi Buildpack theo hướng native
→ vẫn có thể đi qua Buildpacks

AOT + hints + closed world + compatibility
→ native-image
```

Ranh giới này tránh việc packaging module dạy lặp native-image curriculum.

Khi GraalVM Native Image Gradle plugin được áp dụng, Boot điều chỉnh tích hợp image build để đường builder mặc định và environment có thể yêu cầu native image. Cầu nối tự động này cho thấy công cụ build tham gia quy trình native, nhưng không chuyển quyền sở hữu ngữ nghĩa sang module này.

Nếu lỗi liên quan reflection hint bị thiếu, hành vi động không phù hợp phân tích closed-world hoặc mã do AOT sinh ra thay đổi runtime ra sao, hãy dừng chẩn đoán Buildpacks và chuyển sang phần kiến thức native-image. Lệnh đóng gói chỉ là điểm vào.

</details>

- [Quay lại đầu trang](#back-to-top)
