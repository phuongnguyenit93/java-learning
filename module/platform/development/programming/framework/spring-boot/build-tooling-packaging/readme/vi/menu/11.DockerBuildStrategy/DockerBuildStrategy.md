<a id="back-to-top"></a>

# Dockerfile, Buildpacks, layering và cache strategy

## Menu
- [Khi nào nên chọn Dockerfile hoặc Cloud Native Buildpacks?](#dockerfile-vs-buildpacks)
- [Archive layering liên hệ thế nào với image layering?](#archive-vs-image-layering)
- [Mức kiểm soát, tính chuẩn hóa và trách nhiệm bảo trì đánh đổi với nhau thế nào?](#control-standardization-maintenance)
- [Quyết định cache nào thuộc tích hợp Boot và quyết định nào thuộc công cụ container?](#image-cache-strategy)

## <a id="dockerfile-vs-buildpacks">Khi nào nên chọn Dockerfile hoặc Cloud Native Buildpacks?</a>

<details>
<summary>Xem chi tiết</summary>

Chọn Buildpacks khi cần đường application-to-image chuẩn hóa, được hỗ trợ, có quy ước Java/Boot hợp lý và ít phải bảo trì công thức container. Chọn Dockerfile khi image cần các bước, chính sách base image, bố cục filesystem, gói OS hay thiết lập tiến trình mà công thức tường minh diễn đạt tốt hơn.

```text
Buildpacks
→ mức trừu tượng cao, quản lý vòng đời/layer, ít mã chỉ dẫn

Dockerfile
→ kiểm soát thấp tầng rõ hơn, nhưng team nhận thêm trách nhiệm image construction
```

Không có lựa chọn đúng cho mọi project; quyết định dựa trên mức kiểm soát cần thiết và platform standard.

So sánh nên tính cả hành vi khi nâng cấp. Với Buildpacks, cập nhật builder được quản lý có thể đưa bộ buildpack, bản phân phối JDK và run-image base lên phiên bản mới theo chính sách platform. Với Dockerfile, repository ghim và cập nhật tường minh các quyết định đó. Một hướng tập trung việc bảo trì nhiều hơn, hướng còn lại giữ quyền kiểm soát cục bộ nhiều hơn.

Cũng cần tách “cần thêm gói OS” khỏi “bắt buộc dùng Dockerfile”. Builder/buildpack tùy chỉnh đôi khi đáp ứng yêu cầu dùng chung toàn tổ chức mà không cần mỗi ứng dụng tự có Dockerfile. Hãy chọn sau khi xác định bên nào nên chịu trách nhiệm cho tùy biến đó trên nhiều service.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Container Images](https://docs.spring.io/spring-boot/3.3/reference/packaging/container-images/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="archive-vs-image-layering">Archive layering liên hệ thế nào với image layering?</a>

<details>
<summary>Xem chi tiết</summary>

Boot archive layering phân loại **file bên trong artifact ứng dụng**. Layering của image ghi các thay đổi filesystem trong OCI image. Dockerfile/công cụ image có thể dùng cách phân loại archive để đưa dependency và nội dung ứng dụng vào các layer image khác nhau.

```text
Boot layers.idx
→ logical grouping trong archive
        ↓ image build tiêu thụ
OCI image layers
→ filesystem layer history của image format
```

Hai khái niệm liên quan nhưng không đồng nhất. Buildpacks có thể tạo layer theo lifecycle của nó thay vì mô phỏng đúng một chuỗi Dockerfile extraction.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="control-standardization-maintenance">Mức kiểm soát, tính chuẩn hóa và trách nhiệm bảo trì đánh đổi với nhau thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Kiểm soát càng rõ ràng thường càng có nhiều cấu hình phải duy trì. Dockerfile tùy chỉnh làm base image và command rất rõ, nhưng team phải vá lỗi và giữ tính nhất quán. Buildpacks chuyển nhiều quyết định sang hợp đồng builder/platform nên repository đơn giản hơn, đổi lại bớt tự do ở mức thấp.

Nhóm platform có thể chuẩn hóa builder cho hàng trăm service; tổ chức khác lại chuẩn hóa Dockerfile base đã được harden. Spring Boot hỗ trợ cả hai. Điều quan trọng là trách nhiệm bảo trì phải rõ.

Hãy xem ai chịu trách nhiệm khi base image hoặc JDK có lỗ hổng. Với platform Buildpacks, cập nhật builder/run image được quản lý tập trung có thể khắc phục cho nhiều ứng dụng. Với Dockerfile theo từng service, từng repository có thể phải đổi base image và build lại. Đánh đổi vì vậy liên quan đến phân chia trách nhiệm trong tổ chức nhiều không kém cú pháp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="image-cache-strategy">Quyết định cache nào thuộc tích hợp Boot và quyết định nào thuộc công cụ container?</a>

<details>
<summary>Xem chi tiết</summary>

Boot sở hữu archive layering và cache/builder option mà `bootBuildImage` hoặc Maven plugin expose. Docker/BuildKit cache rules, registry-backed cache, daemon storage, pruning và CI cache transport thuộc container tooling.

```text
file bị xếp sai Boot archive layer
→ Boot packaging

bootBuildImage cấu hình cache sai
→ Boot Buildpacks integration

Dockerfile bị trượt cache BuildKit / chính sách cache của registry
→ container tooling
```

Phân loại đúng giúp tránh chỉnh Boot config để giải quyết một vấn đề thực tế nằm ở downstream image builder.

Buildpacks qua Boot cung cấp các build/launch cache có tên cùng vùng làm việc build tạm thời. Các cache này nằm ở ranh giới container engine và thường được suy ra từ cấu hình image/build. Xóa cache có thể hữu ích khi chẩn đoán, nhưng xóa thường xuyên sẽ loại bỏ một lợi ích lớn của quy trình buildpack.

Nên đo hiệu quả cache bằng việc quan sát buildpack layer nào được tái sử dụng và thay đổi đầu vào nào gây việc làm mất hiệu lực. Không nên tối ưu chỉ theo số lượng image layer; ít layer nhưng tách đúng nội dung ổn định/biến động có thể tốt hơn layout quá phân mảnh.

</details>

- [Quay lại đầu trang](#back-to-top)
