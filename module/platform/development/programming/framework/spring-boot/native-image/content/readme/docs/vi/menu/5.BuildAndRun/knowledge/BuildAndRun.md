<a id="back-to-top"></a>

# Build và chạy ứng dụng Spring Boot Native

## Menu
- [Quy trình build native được Boot hỗ trợ là gì?](#native-build-workflow)
- [Maven và Gradle tích hợp GraalVM Native Build Tools như thế nào?](#graalvm-native-build-tools)
- [Đầu ra AOT đi vào quá trình biên dịch native như thế nào?](#aot-to-native-compile)
- [Native executable theo từng nền tảng chứa những gì?](#native-executable-output)
- [Tích hợp Buildpacks của Spring Boot tạo OCI image chứa native executable như thế nào?](#native-buildpack-image)
- [Chạy native executable khác `java -jar` như thế nào?](#native-run-comparison)
- [Trách nhiệm của native-image bàn giao sang công cụ build và đóng gói ở đâu?](#native-build-tooling-boundary)

## <a id="native-build-workflow">Quy trình build native được Boot hỗ trợ là gì?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot hỗ trợ hai đường build native cho môi trường vận hành: GraalVM Native Build Tools qua Maven/Gradle và Cloud Native Buildpacks được cấu hình để tạo image ứng dụng native. Cả hai đều thực hiện Spring AOT trước khi biên dịch native image.

```text
ứng dụng + môi trường tại thời điểm build
→ Spring AOT
→ code/hints được sinh
→ GraalVM Native Image
→ native executable
→ tùy chọn OCI image
```

Native build phải có thể tái lập từ cấu hình build; không nên phụ thuộc vào file được sinh rồi sửa tay hoặc trạng thái chỉ có trên máy một lập trình viên.

### Tài liệu tham khảo

- [Spring Boot 3.3 — GraalVM Native Images](https://docs.spring.io/spring-boot/3.3/reference/packaging/native-image/)
- [Spring Boot 3.3 — Developing Your First GraalVM Native Application](https://docs.spring.io/spring-boot/3.3/how-to/native-image/developing-your-first-application.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="graalvm-native-build-tools">Maven và Gradle tích hợp GraalVM Native Build Tools như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Với dự án Maven kế thừa `spring-boot-starter-parent`, Boot cung cấp profile `native` để làm việc cùng GraalVM Native Build Tools. Profile này sắp xếp `process-aot` và các mặc định của native plugin để ứng dụng đã chuẩn bị trở thành đầu vào cho bước biên dịch native.

Với Gradle, GraalVM Native Build Tools plugin kết hợp Spring Boot tạo các native task và nối chúng với Boot/Spring AOT. Mô hình cần nhớ là thứ tự: công cụ build chuẩn bị đầu ra AOT trước rồi native plugin biên dịch đầu ra đó.

Khi Maven dùng `spring-boot-starter-parent`, điểm vào để tạo executable trực tiếp làm thứ tự này rõ ràng hơn; Gradle có task native tương ứng khi GraalVM Native Build Tools plugin được áp dụng:

```text
mvn -Pnative native:compile
gradle nativeCompile
```

Với Maven, profile `native` nối quá trình AOT của Boot vào build **khi profile đó được kế thừa từ `spring-boot-starter-parent`**. Dự án Maven không dùng parent này phải tự cấu hình các execution tương đương cho Boot `process-aot` và GraalVM Native Build Tools thay vì giả định `-Pnative` luôn tồn tại. Với Gradle, Spring Boot plugin cấu hình quan hệ phụ thuộc giữa các AOT task khi `org.graalvm.buildtools.native` được áp dụng.

Chi tiết lifecycle/task tổng quát thuộc module build-tooling; module này giải thích ý nghĩa của các bước đối với ứng dụng Boot native.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aot-to-native-compile">Đầu ra AOT đi vào quá trình biên dịch native như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Mã nguồn Java do AOT sinh được biên dịch, bytecode được sinh được thêm vào đầu vào và cấu hình/hints cho native-image được đặt dưới `META-INF/native-image` phù hợp. GraalVM phân tích classpath ứng dụng đã được bổ sung các đầu ra này.

Thứ tự rất quan trọng. Chạy native-image trên Boot artifact thông thường nhưng thiếu các đầu ra AOT không phải pipeline native được Spring hỗ trợ. Spring Boot 3.3 có thể chuyển executable JAR nếu JAR đã chứa các đầu ra AOT cần thiết; bước chuẩn bị AOT vẫn không được bỏ qua.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Advanced Native Images Topics](https://docs.spring.io/spring-boot/3.3/reference/packaging/native-image/advanced-topics.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-executable-output">Native executable theo từng nền tảng chứa những gì?</a>

<details>
<summary>Xem chi tiết</summary>

Đầu ra chứa mã máy, phần runtime hỗ trợ được phân tích native-image chọn và resource/metadata của ứng dụng được cấu hình reachability giữ lại. Nó được build cho nền tảng mục tiêu thay vì giữ dạng JVM bytecode độc lập nền tảng.

Do đó quy trình phát hành cần chiến lược cho từng OS/architecture cần hỗ trợ. Native Image nói chung không biến một binary thành artifact đa nền tảng; build thường chạy trên nền tảng mục tiêu hoặc môi trường build tương ứng.

Từ góc nhìn người học, executable vẫn là ứng dụng Spring Boot với cấu hình, beans, tích hợp web/server và mã nghiệp vụ đã được AOT chuẩn bị.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-buildpack-image">Tích hợp Buildpacks của Spring Boot tạo OCI image chứa native executable như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

`bootBuildImage` / Maven `build-image` có thể chọn đường build native khi được cấu hình phù hợp. Paketo buildpacks nhận diện ứng dụng, chạy AOT/biên dịch native trong môi trường builder rồi đặt native executable vào image ứng dụng thay vì dạng khởi chạy JVM thông thường.

Với Maven kế thừa `spring-boot-starter-parent`, và với Gradle đã áp dụng GraalVM Native Build Tools, các điểm vào tương ứng là:

```text
mvn -Pnative spring-boot:build-image
gradle bootBuildImage
```

Lệnh Maven dùng `-Pnative` dựa vào profile do parent cung cấp; Maven không dùng parent phải cấu hình rõ các execution AOT/native tương đương. Lệnh Gradle tạo native image khi GraalVM Native Build Tools plugin được áp dụng; nếu không, `bootBuildImage` đi theo đường tạo JVM image thông thường.

Cách này phù hợp khi nền tảng triển khai đã dùng OCI image và muốn builder cung cấp GraalVM/native toolchain. Các khái niệm tổng quát về builder, run image, cache, phát hành và thông tin xác thực registry vẫn thuộc `build-tooling-packaging`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-run-comparison">Chạy native executable khác `java -jar` như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

`java -jar` khởi động JVM rồi Boot chạy trên bytecode/classpath. Native binary khởi động trực tiếp như một tiến trình OS; không truyền nó cho `java` mà chạy binary hoặc khởi động OCI image chứa nó.

Biến môi trường và application properties bên ngoài vẫn quan trọng, nhưng đầu vào runtime không thể phủ nhận quyết định cấu trúc đã được cố định trong mô hình AOT.

Khi đo khởi động/bộ nhớ cần so cùng cấu hình và khối lượng công việc. Native và JVM khác về warm-up, chẩn đoán và hành vi bộ nhớ; một số đo khi tiến trình nhàn rỗi trên máy cục bộ chưa đủ để quyết định cho môi trường vận hành.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-build-tooling-boundary">Trách nhiệm của native-image bàn giao sang công cụ build và đóng gói ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Native-image sở hữu ngữ nghĩa AOT/closed-world làm executable khả thi. `build-tooling-packaging` sở hữu mô hình Boot plugin tổng quát, đồng bộ dependency, các khái niệm Buildpacks, đặt tên/phát hành OCI, lựa chọn bàn giao archive/image và các lệnh công cụ build tổng quát.

Module này có thể chỉ lệnh kích hoạt đường native vì đó là bằng chứng cần thiết, nhưng không dạy lại toàn bộ chương trình Gradle/Maven/Buildpacks. Ngược lại `build-tooling-packaging` chỉ bàn giao sang native chứ không dạy lại Runtime Hints hay closed-world.

</details>

- [Quay lại đầu trang](#back-to-top)
