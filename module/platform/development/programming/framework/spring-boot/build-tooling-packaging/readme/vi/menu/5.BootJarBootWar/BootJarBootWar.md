<a id="back-to-top"></a>

# `bootJar`, `bootWar` và repackaging

## Menu
- [`bootJar`, `bootWar` và Maven `repackage` tạo Boot archive như thế nào?](#bootjar-bootwar-repackage)
- [Boot executable archive khác library artifact thông thường thế nào?](#executable-vs-plain-archive)
- [Khi nào nên đóng gói executable JAR hoặc WAR?](#jar-vs-war-packaging)
- [Archive đã đóng gói xác định entry point của ứng dụng như thế nào?](#packaging-main-class)

## <a id="bootjar-bootwar-repackage">`bootJar`, `bootWar` và Maven `repackage` tạo Boot archive như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Boot Gradle plugin cung cấp `bootJar` cho executable JAR và, khi dùng War plugin, `bootWar` cho executable WAR. Maven đạt cùng Boot archive model qua goal `repackage`: lấy archive do package lifecycle bình thường tạo ra rồi chuyển nó thành executable layout của Boot.

```text
application classes + runtime dependencies
        ↓
bootJar / bootWar / repackage
        ↓
Boot executable archive
        ↓
Spring Boot Loader khởi chạy nội dung lồng bên trong
```

Công cụ build vẫn biên dịch project và chuẩn bị đầu vào; Boot packaging chịu trách nhiệm cho executable layout và launcher metadata.

Với Gradle, BootJar và BootWar là các task Jar/War chuyên biệt nên cấu hình archive thông thường vẫn áp dụng cùng tính năng riêng của Boot. Với Maven, `repackage` biến đổi archive được vòng đời package tạo ra; mặc định artifact gốc không thực thi được giữ dưới dạng file `.original` nếu không dùng chiến lược classifier.

Khác biệt này quan trọng khi repository publish nhiều artifact. Cần nói rõ file nào là executable application và file nào, nếu có, vẫn là plain artifact cho consumer khác. Artifact coordinates chưa chắc đủ để phân biệt khi classifier tham gia.

### Tài liệu tham khảo

- [Spring Boot 3.3 Gradle Plugin — Packaging Executable Archives](https://docs.spring.io/spring-boot/3.3/gradle-plugin/packaging.html)
- [Spring Boot 3.3 Maven Plugin — Packaging Executable Archives](https://docs.spring.io/spring-boot/3.3/maven-plugin/packaging.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executable-vs-plain-archive">Boot executable archive khác library artifact thông thường thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Library JAR thông thường chủ yếu được đặt **trên classpath của ứng dụng khác**. Boot executable archive là đơn vị bàn giao của chính application: chứa application classes, runtime dependency JAR theo nested layout và launcher metadata/code cần cho `java -jar`.

Sự khác biệt quan trọng khi project vừa có nhu cầu publish library vừa chạy application. Gradle có `jar` và `bootJar` là hai archive task khác nhau; build phải làm rõ artifact nào để tái sử dụng như library và artifact nào là đầu ra ứng dụng.

Gradle làm sự phân tách này khá rõ theo quy ước: khi bootJar hoặc bootWar được cấu hình, jar/war thông thường nhận classifier plain để cả hai đầu ra cùng tồn tại. Có thể tắt task plain nếu project thật sự không cần, nhưng phải xét tooling khác; ví dụ native-image workflow có thể cần ordinary jar.

Không nên publish hai artifact dưới cách đặt tên mơ hồ. Bên sử dụng thư viện không nên vô tình phụ thuộc vào executable Boot archive vì bố cục dependency lồng nhau của nó được thiết kế để khởi chạy chứ không phải làm classpath thư viện thông thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jar-vs-war-packaging">Khi nào nên đóng gói executable JAR hoặc WAR?</a>

<details>
<summary>Xem chi tiết</summary>

Executable JAR là lựa chọn thông thường khi application tự sở hữu Boot-managed runtime và được khởi động bằng `java -jar`. Embedded server, nếu có, nằm trong dependency model của application.

WAR phù hợp khi môi trường yêu cầu deploy vào servlet container bên ngoài. Boot cũng có thể tạo executable WAR, nhưng WAR dùng để deploy phải mô hình đúng các container-provided dependencies để tránh đóng gói server library như dependency ứng dụng thông thường.

```text
tiến trình Boot tự chứa
→ ưu tiên executable JAR

yêu cầu deploy vào external servlet container
→ cân nhắc WAR
```

Đây là deployment contract, không phải phân loại “WAR cho production, JAR cho development”.

Với executable WAR vừa phải tự chạy vừa phải deploy vào external servlet container, Boot giữ container-provided libraries dưới WEB-INF/lib-provided thay vì trộn với nội dung WEB-INF/lib thông thường. Trong Gradle, providedRuntime là model phù hợp hơn cho nhóm dependency này vì chúng vẫn có mặt trên test runtime.

Bố cục đó phản ánh hai hợp đồng cùng lúc: archive có thể tự khởi chạy nhưng vẫn tránh xung đột với thư viện server khi container bên ngoài chịu trách nhiệm cho chúng. Nếu tổ chức không có yêu cầu chạy trên container bên ngoài, executable JAR thường đơn giản hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="packaging-main-class">Archive đã đóng gói xác định entry point của ứng dụng như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Executable Boot archive tách **launcher entry point** khỏi main class của application. Manifest đưa `java -jar` vào Spring Boot Loader và lưu application class mà launcher sẽ gọi sau đó.

Build plugin thường tự tìm được một main class phù hợp. Nếu project có nhiều candidate hoặc discovery mơ hồ, nên cấu hình main class rõ ràng thay vì phụ thuộc thứ tự tình cờ.

Vì vậy lỗi “không xác định được main class” khi packaging thuộc build configuration; exception xảy ra sau khi application `main` bắt đầu lại thuộc runtime startup.

Manifest thường dùng một Boot loader class làm Main-Class và lưu application entry point dưới Start-Class. Maven repackage quản lý các entry đó; khi việc phát hiện tự động không đủ, nên cấu hình application main class qua Boot plugin thay vì tự chỉnh manifest bằng jar plugin.

Đây cũng là lý do đổi `Main-Class` trực tiếp thành class ứng dụng có thể phá mô hình khởi chạy nested archive: cách đó bỏ qua loader vốn hiểu cách dựng classpath từ `BOOT-INF` hoặc `WEB-INF`.

</details>

- [Quay lại đầu trang](#back-to-top)
