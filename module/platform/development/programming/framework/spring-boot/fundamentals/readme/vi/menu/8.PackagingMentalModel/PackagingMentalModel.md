<a id="back-to-top"></a>

# Mô hình đóng gói ứng dụng Spring Boot

## Menu
- [Vì sao Spring Boot cung cấp mô hình đóng gói thực thi được?](#packaging-purpose)
- [Artifact ứng dụng Boot thực thi được là gì?](#executable-artifact)
- [Application classes và dependencies được giữ cùng nhau để chạy như thế nào?](#boot-loader-layout-mental-model)
- [Ứng dụng đã đóng gói liên hệ với java -jar ra sao?](#run-packaged-application)
- [Chi tiết packaging chuyển sang module nào?](#packaging-tooling-handoff)

## <a id="packaging-purpose">Vì sao Spring Boot cung cấp mô hình đóng gói thực thi được?</a>

<details>
<summary>Xem chi tiết</summary>

Ứng dụng Boot chỉ thực sự hữu ích khi code và runtime dependencies có thể được bàn giao dưới dạng khởi động ổn định bên ngoài IDE của lập trình viên. Mô hình đóng gói thực thi của Boot cung cấp cho ứng dụng JVM phổ biến một artifact tự chứa, biết cách truy cập application classes cùng dependencies đã đóng gói.

Giá trị ở mức nhập môn là sự đơn giản khi vận hành:

```text
compiled application + runtime dependencies
        ↓ packaging
một executable application archive
        ↓
java -jar application.jar
```

Điều này không có nghĩa Boot phát minh JAR hay cơ chế launch của JVM. Build tooling và loader của Boot sắp xếp JAR/war layout để giữ nested dependency JAR và gọi được `main` thật của ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executable-artifact">Artifact ứng dụng Boot thực thi được là gì?</a>

<details>
<summary>Xem chi tiết</summary>

Executable Boot artifact là application archive đã được chuẩn bị để có thể launch trực tiếp. Với executable JAR phổ biến, archive chứa application classes/resources, dependency JAR cùng Boot loader classes/metadata cần để thiết lập runtime classpath và gọi điểm vào của ứng dụng.

Điểm khác biệt cần hiểu là thin JAR thông thường chỉ chứa class đã biên dịch của bạn và kỳ vọng dependencies được ghép riêng trên command line. Executable archive của Boot có thể mang các dependencies đó bên trong artifact ứng dụng.

Điều này làm deployment dễ lý luận hơn: artifact đại diện cho ứng dụng cùng tập dependency mà nó được build với. Chi tiết `bootJar`/`repackage` task thuộc `build-tooling-packaging`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boot-loader-layout-mental-model">Application classes và dependencies được giữ cùng nhau để chạy như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Trong layout executable JAR chuẩn, application classes nằm dưới `BOOT-INF/classes` và dependency JAR nằm dưới `BOOT-INF/lib`. Boot launcher hiểu nested layout này và tạo classpath cần thiết để gọi main class thật của ứng dụng.

```text
application.jar
├── BOOT-INF/classes/    ← application classes/resources
├── BOOT-INF/lib/        ← dependency jars
└── Boot launcher classes/metadata
```

Đây là mô hình tư duy, không phải yêu cầu học thuộc nội bộ archive. Bằng chứng cần nhớ là dependency vẫn có thể nằm dưới dạng nested JAR trong archive trong khi ứng dụng vẫn launch trực tiếp được.

`JarLauncher` trong Spring Boot 3.3 kỳ vọng application classes tại `BOOT-INF/classes` và dependencies tại `BOOT-INF/lib`. Chi tiết triển khai loader sâu hơn, layout thay thế, layering và cấu hình build task thuộc `build-tooling-packaging`.

### Tài liệu tham khảo

- [Spring Boot 3.3 — JarLauncher](https://docs.spring.io/spring-boot/3.3/api/java/org/springframework/boot/loader/launch/JarLauncher.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="run-packaged-application">Ứng dụng đã đóng gói liên hệ với java -jar ra sao?</a>

<details>
<summary>Xem chi tiết</summary>

Khi archive manifest trỏ tới Boot launcher, `java -jar application.jar` khởi động launcher trước. Launcher thiết lập truy cập tới nested application classes và dependencies, rồi gọi start class đã cấu hình của ứng dụng, nơi chứa `main` quen thuộc.

```text
java -jar application.jar
        ↓
Boot launcher
        ↓
application + nested dependency classpath
        ↓
YourApplication.main(...)
        ↓
SpringApplication.run(...)
```

Điều này nối packaging trở lại bootstrap: packaging thay đổi cách JVM tìm tới ứng dụng cùng dependencies; khi `main` của ứng dụng đã chạy, mô hình tư duy `SpringApplication` ở Chương 2 vẫn giữ nguyên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="packaging-tooling-handoff">Chi tiết packaging chuyển sang module nào?</a>

<details>
<summary>Xem chi tiết</summary>

Các chi tiết về build plugin, executable archive, layered packaging, OCI image và Buildpacks thuộc module `build-tooling-packaging`.

Module đó giải thích `bootRun`, `bootJar`/`bootWar`, hành vi Maven repackage, Boot Loader chi tiết, reproducible archive, layer metadata, `bootBuildImage` và Cloud Native Buildpacks. Đây là cơ chế build/bàn giao, không phải kiến thức tiên quyết để hiểu Spring Boot là gì.

Fundamentals chỉ cần giữ mối quan hệ ổn định: source cùng dependencies trở thành artifact chạy được; Boot launcher nối layout đã đóng gói tới main class thật của ứng dụng; và build tooling chịu trách nhiệm tạo artifact đó đúng cách.

</details>

- [Quay lại đầu trang](#back-to-top)
