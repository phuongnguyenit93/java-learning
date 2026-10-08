<a id="back-to-top"></a>

# Vì sao Spring Boot Native Image cần AOT

## Menu
- [Spring Boot Native Image là gì?](#native-image-purpose)
- [Vì sao cách khởi động Boot động thông thường chưa đủ cho biên dịch native?](#native-image-problem)
- [Native executable khác triển khai JVM thông thường như thế nào?](#jvm-vs-native-runtime)
- [Native Image có thể mang lại lợi ích runtime nào?](#native-image-benefits)
- [Native Image làm tăng chi phí build và khả năng tương thích nào?](#native-image-cost-model)
- [Module này sở hữu phần nào, và phần nào thuộc GraalVM hoặc công cụ build?](#native-image-ownership-boundary)

## <a id="native-image-purpose">Spring Boot Native Image là gì?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot native image là executable theo từng nền tảng được GraalVM Native Image tạo ra sau khi Spring chuẩn bị ứng dụng bằng ahead-of-time processing (AOT). Khi chạy, nó không khởi động một JVM thông thường rồi mới nạp Boot JAR; một phần lớn mô hình ứng dụng đã được phân tích và biên dịch vào executable.

Mô hình cần nhớ:

```text
ứng dụng Spring Boot
→ Spring AOT analysis
→ code/bytecode được sinh + runtime-hint metadata
→ phân tích/biên dịch bằng GraalVM native-image
→ native executable
```

Vì vậy Native Image là **một dạng triển khai khác**, không chỉ là một lệnh khác để bọc `java -jar`.

Phần còn lại của module đi theo đúng chuỗi phụ thuộc mà một quá trình chuyển sang native thực tế sẽ bộc lộ:

```text
vì sao native cần AOT
→ pipeline Spring AOT
→ hệ quả closed-world
→ Runtime Hints cho truy cập động
→ build và chạy native
→ kiểm thử riêng cho native
→ đánh đổi về khả năng tương thích và chi phí
→ quyết định mức sẵn sàng end-to-end
```

Sau khi học xong, bạn nên có thể xác định ranh giới sớm nhất đang lỗi trong chuỗi này và quyết định ứng dụng phù hợp hơn với native executable, native OCI image hay triển khai JVM thông thường.

### Tài liệu tham khảo

- [Spring Boot 3.3 — GraalVM Native Images](https://docs.spring.io/spring-boot/3.3/reference/packaging/native-image/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-image-problem">Vì sao cách khởi động Boot động thông thường chưa đủ cho biên dịch native?</a>

<details>
<summary>Xem chi tiết</summary>

Ứng dụng Spring chạy JVM có thể khám phá nhiều thứ khi tiến trình đang chạy: quét classpath, cấu hình có điều kiện, reflection, tra cứu resource và proxy được sinh. JVM vẫn giữ classpath đầy đủ và khả năng nạp lớp động nên những quyết định này có thể xảy ra muộn.

Native Image lại phân tích theo closed-world. Nó cần biết ở thời điểm build code nào và khả năng động nào phải tồn tại lúc chạy. Spring AOT nối khoảng cách đó bằng cách đánh giá mô hình ứng dụng sớm, sinh cấu trúc runtime rõ ràng hơn và tạo metadata cho những hành vi mà phân tích tĩnh không thể tự suy ra an toàn.

Vấn đề không phải “Spring quá động nên không biên dịch được”, mà là **khám phá lúc chạy phải trở thành bằng chứng tại thời điểm build** khi native compiler yêu cầu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-vs-native-runtime">Native executable khác triển khai JVM thông thường như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Triển khai JVM bàn giao bytecode và cần JVM ở môi trường đích. Class loading, JIT compilation, reflection và nhiều quyết định của framework vẫn xảy ra lúc chạy. Native executable được build cho OS/architecture cụ thể và chứa ứng dụng đã biên dịch cùng phần runtime hỗ trợ được chọn trong quá trình tạo native image.

```text
JVM
JAR + JVM → load/initialize/optimize lúc chạy

native
phân tích lúc build + biên dịch native → executable theo nền tảng
```

Cùng một ứng dụng nghiệp vụ có thể hỗ trợ cả hai dạng, nhưng các giả định khi build không giống nhau. Dependency/cấu hình chạy tốt trên JVM vẫn có thể lộ reachability metadata bị thiếu hoặc hành vi động không tương thích khi build native.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-image-benefits">Native Image có thể mang lại lợi ích runtime nào?</a>

<details>
<summary>Xem chi tiết</summary>

Native executable hấp dẫn khi **độ trễ khởi động và mức sử dụng bộ nhớ** quan trọng. Do nhiều bước phân tích framework và biên dịch được chuyển sang thời điểm build, tiến trình native thường khởi động nhanh hơn đáng kể so với JVM khởi động nguội và thường dùng ít bộ nhớ hơn với khối lượng công việc tương đương.

Đặc tính này hữu ích cho ứng dụng dòng lệnh, dịch vụ scale-to-zero, worker ngắn hạn hoặc môi trường triển khai cần mật độ cao. Tuy nhiên phải đo trên khối lượng công việc thật; native image không bảo đảm cải thiện mọi chỉ số throughput, latency hay chi phí.

JVM vẫn rất mạnh cho khối lượng công việc chạy dài, nơi JIT warm-up, công cụ trưởng thành và phản hồi build/test nhanh có giá trị hơn khởi động nguội.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-image-cost-model">Native Image làm tăng chi phí build và khả năng tương thích nào?</a>

<details>
<summary>Xem chi tiết</summary>

Biên dịch native chuyển một phần chi phí khởi động sang quy trình build. Native build lâu hơn, dùng nhiều CPU/bộ nhớ, tạo binary theo nền tảng và thêm một lớp yêu cầu tương thích quanh reflection, resource, proxy, serialization, JNI, nạp lớp động và thư viện bên thứ ba.

```text
lợi ích runtime
− phản hồi build lâu hơn
− tài nguyên CI cho native build
− bảo trì hint/metadata
− khả năng tương thích của dependency
− kiểm chứng riêng cho native
= giá trị thật đối với ứng dụng
```

Chỉ nên chọn native image khi lợi ích runtime đủ bù các chi phí này, không phải vì xem nó là bước hiện đại hóa bắt buộc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-image-ownership-boundary">Module này sở hữu phần nào, và phần nào thuộc GraalVM hoặc công cụ build?</a>

<details>
<summary>Xem chi tiết</summary>

Module này sở hữu **tích hợp của Spring Boot với thực thi AOT/native**: pipeline Spring AOT, hệ quả closed-world, Runtime Hints, quy trình build/test native được hỗ trợ, chẩn đoán khả năng tương thích và quyết định có dùng native hay không.

```text
chi tiết nội bộ GraalVM compiler/runtime
→ module sở hữu GraalVM/runtime

Boot Gradle/Maven/Buildpacks tổng quát
→ build-tooling-packaging

Boot testing tổng quát
→ testing

lifecycle ứng dụng thông thường
→ application-runtime

ngữ nghĩa reflection/proxy/class loading
→ các module sở hữu Java/Spring Framework
```

Mức độ sẵn sàng cho native chạm vào các chủ đề này nhưng không giành quyền sở hữu của chúng.

</details>

- [Quay lại đầu trang](#back-to-top)
