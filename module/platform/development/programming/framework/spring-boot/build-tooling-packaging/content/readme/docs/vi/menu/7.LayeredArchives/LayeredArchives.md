<a id="back-to-top"></a>

# Layered JAR và WAR

## Menu
- [Layered archive giải quyết vấn đề gì?](#layered-archive-purpose)
- [Các layer mặc định của Spring Boot archive là gì?](#default-layer-model)
- [Vì sao thứ tự layer ảnh hưởng khả năng tái sử dụng cache?](#layer-order-and-cache)
- [Khi nào nên tùy chỉnh layering?](#custom-layering)
- [Archive layering không định nghĩa điều gì về container runtime?](#archive-layer-vs-container-runtime)

## <a id="layered-archive-purpose">Layered archive giải quyết vấn đề gì?</a>

<details>
<summary>Xem chi tiết</summary>

Mã ứng dụng thường thay đổi thường xuyên hơn third-party dependencies. Nếu mọi thay đổi được coi là một khối duy nhất, quá trình dựng image phía sau có thể phải sao chép/cache lại nhiều nội dung không cần thiết. Layered Boot archive ghi logical groups để tách nội dung ổn định khỏi nội dung ứng dụng thay đổi thường xuyên.

Layering không đổi ngữ nghĩa dependency Java. Đây là metadata packaging giúp extraction và quá trình dựng image thân thiện với cache hơn.

Layers index vì vậy là mô tả cho công cụ phía sau chứ không phải bản sao thứ hai của ứng dụng. Class và thư viện vẫn là cùng nội dung; index chỉ bổ sung cách nhóm để extractor/image builder sử dụng. Đổi hoặc bỏ metadata layer không nên đổi hành vi nghiệp vụ nhưng có thể thay đổi đáng kể hiệu quả build lại và truyền dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="default-layer-model">Các layer mặc định của Spring Boot archive là gì?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot mặc định chia layered archive thành bốn nhóm:

```text
dependencies
spring-boot-loader
snapshot-dependencies
application
```

Dependency bên thứ ba đã phát hành thường ít thay đổi nhất; Boot loader ổn định trong cùng baseline; dependency SNAPSHOT thay đổi thường hơn; class/resource của ứng dụng thường thay đổi nhiều nhất. Đây là mặc định hữu ích để project không phải tự tạo cách phân nhóm từ đầu.

Cách nhóm mặc định còn dựa trên tần suất thay đổi chứ không dựa vào kiến trúc nghiệp vụ. Dependency module cục bộ thuộc application layer, dependency ngoài project đã phát hành vào `dependencies`, còn dependency SNAPSHOT có layer riêng. Vì vậy một build multi-module có thể được chia layer khác với build chỉ sử dụng cùng thư viện nội bộ sau khi thư viện đã được publish lên repository.

Đây là quyết định packaging dựa trên những gì Boot quan sát được trong build hiện tại. Nếu quy trình phát hành nội bộ thay đổi cách dependency của project được phân giải, nên kiểm tra lại layers index được sinh ra thay vì giả định cùng file luôn nằm cùng layer.

### Tài liệu tham khảo

- [Spring Boot 3.3 Gradle Plugin — Layered Jars and Wars](https://docs.spring.io/spring-boot/3.3/gradle-plugin/packaging.html#packaging-executable.configuring.layered-archives)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="layer-order-and-cache">Vì sao thứ tự layer ảnh hưởng khả năng tái sử dụng cache?</a>

<details>
<summary>Xem chi tiết</summary>

Image cache hiệu quả nhất khi các layer ổn định ở trước không thay đổi. Nếu dependency được tách khỏi application classes, một lần chỉnh mã nguồn chỉ cần invalidate application layer thay vì khiến toàn bộ dependency content bị build/transfer lại.

```text
ít thay đổi
→ dependencies
→ loader
→ snapshot dependencies
→ application
nhiều thay đổi
```

Chi tiết cách triển khai cache thuộc container tooling; Boot chỉ cung cấp packaging information để các công cụ đó tận dụng.

Lợi ích cache chỉ xuất hiện khi đường dựng image thực sự giữ các ranh giới đó. Nếu Dockerfile copy toàn bộ archive như một file nguyên khối vào một image layer, archive dù có logical layers thì cache image vẫn không thể tái sử dụng độc lập từng nhóm bên trong. Packaging metadata và công thức dựng image vì vậy phải thống nhất cách sử dụng layer.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-layering">Khi nào nên tùy chỉnh layering?</a>

<details>
<summary>Xem chi tiết</summary>

Chỉ nên customize khi giả định change-frequency mặc định không phù hợp project. Ví dụ multi-module application lớn có thể muốn tách internal library ổn định khỏi application code thay đổi nhanh.

Layer tùy chỉnh nên dựa trên bằng chứng từ build/cache thực tế. Quá nhiều layer tạo chính sách phải bảo trì nhưng chưa chắc tăng khả năng tái sử dụng cache. Bắt đầu từ mặc định của Boot, đo pipeline rồi mới thêm quy tắc khi có mẫu rõ ràng.

Quy tắc tùy chỉnh có hai trách nhiệm: nhận nội dung vào layer có tên và định nghĩa đầy đủ layer order. Nếu một file khớp nhiều mẫu thì thứ tự quy tắc quyết định layer nhận file; nội dung chưa được nhận vẫn phải có đích. Một scheme tham chiếu layer nhưng không đưa layer đó vào thứ tự cuối là nợ cấu hình chứ không phải optimization.

Tên layer nên phản ánh hành vi bàn giao. Tạo một layer cho từng package nội bộ có vẻ chi tiết nhưng làm chính sách cache phụ thuộc cách tổ chức source. Nên ưu tiên các nhóm như thư viện nội bộ ổn định và nội dung ứng dụng thay đổi thường xuyên khi chúng phản ánh đúng mẫu thay đổi thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="archive-layer-vs-container-runtime">Archive layering không định nghĩa điều gì về container runtime?</a>

<details>
<summary>Xem chi tiết</summary>

Archive layer chỉ mô tả cách nhóm file phục vụ packaging/extraction. Nó không định nghĩa networking, volume, cách cô lập tiến trình, giới hạn CPU/bộ nhớ, orchestration, registry hay chiến lược rollout.

```text
Boot archive layers
→ ranh giới đóng gói/cache

container runtime / orchestrator
→ ranh giới tiến trình và triển khai
```

Boot layers index có thể giúp tạo image layers, nhưng bản thân nó không phải container runtime specification.

Ranh giới này rất hữu ích khi chẩn đoán. Dependency vào sai logical Boot layer thì kiểm tra quy tắc packaging. Nếu image layer đã đúng nhưng filesystem mount lúc runtime hoặc giới hạn container hành xử sai, artifact đã đi sang phạm vi trách nhiệm của container runtime. Cùng dùng từ “layer” không có nghĩa cùng miền lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)
