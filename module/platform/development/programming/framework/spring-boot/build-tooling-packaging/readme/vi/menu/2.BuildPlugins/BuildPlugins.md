<a id="back-to-top"></a>

# Gradle plugin và Maven plugin của Spring Boot

## Menu
- [Gradle plugin và Maven plugin của Spring Boot bổ sung điều gì?](#boot-build-plugins)
- [Những task chạy, đóng gói và tạo image nào thuộc plugin của Boot?](#plugin-responsibilities)
- [Phần nào vẫn là trách nhiệm của Gradle hoặc Maven?](#plugin-vs-build-tool)
- [Nên tư duy thế nào về hai đường tích hợp Gradle và Maven?](#plugin-choice-boundary)

## <a id="boot-build-plugins">Gradle plugin và Maven plugin của Spring Boot bổ sung điều gì?</a>

<details>
<summary>Xem chi tiết</summary>

Gradle plugin `org.springframework.boot` và Maven `spring-boot-maven-plugin` nối mô hình ứng dụng của Boot vào hệ thống build tương ứng. Chúng không tạo ra một bộ máy build thứ hai; chúng thêm các thao tác hiểu Spring Boot vào đồ thị task của Gradle hoặc lifecycle/goal của Maven.

Với Gradle, Boot plugin kết hợp cùng các plugin liên quan Java để tạo/cấu hình các task như `bootRun`, `bootJar`, `bootWar`, `bootBuildImage`. Với Maven, plugin cung cấp các goal như `run`, `repackage`, `build-image`. Hai đường tích hợp khác cú pháp nhưng cùng phục vụ các mục tiêu: khởi chạy ứng dụng, tạo executable archive theo bố cục Boot, hoặc tạo image qua tích hợp được hỗ trợ.

Gradle plugin còn phản ứng với các plugin đã được áp dụng trong project. Khi Java plugin có mặt, Boot 3.3 đăng ký các task BootJar, BootRun và BootBuildImage, nối executable archive vào assemble, đặt classifier plain cho jar thông thường theo convention và tạo các cấu hình phục vụ đóng gói lúc phát triển/runtime. Vì vậy tích hợp của Boot trở thành một phần của Gradle task model chứ không phải lớp bọc lệnh tách rời.

Maven plugin tích hợp qua goal và lifecycle execution thay vì đăng ký Gradle task. Khi project kế thừa spring-boot-starter-parent, parent còn có thể cấu hình sẵn repackage execution và một số mặc định build. Hãy xem đây là hai adapter khác nhau cùng dẫn tới các kết quả của Boot, không phải hai cách triển khai có cấu trúc giống hệt nhau.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Gradle Plugin Reference](https://docs.spring.io/spring-boot/3.3/gradle-plugin/)
- [Spring Boot 3.3 — Maven Plugin Reference](https://docs.spring.io/spring-boot/3.3/maven-plugin/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-responsibilities">Những task chạy, đóng gói và tạo image nào thuộc plugin của Boot?</a>

<details>
<summary>Xem chi tiết</summary>

Nên nhớ trách nhiệm theo đầu ra thay vì học thuộc tên task:

```text
tiến trình lúc phát triển
→ Gradle bootRun / Maven spring-boot:run

executable archive
→ Gradle bootJar hoặc bootWar / Maven repackage

OCI image bằng Buildpacks
→ Gradle bootBuildImage / Maven build-image
```

Plugin còn cho phép cấu hình main class, bố cục archive, layer, builder/run image, môi trường, phát hành và các hành vi đặc thù Boot khác. Đây là cấu hình của lớp tích hợp Boot chứ không thay đổi ngữ nghĩa tổng quát của Gradle task hay Maven phase.

Các task riêng của Boot vẫn tiêu thụ đầu vào do công cụ build thông thường tạo ra. BootRun dùng đầu ra source set đã biên dịch và runtime classpath; BootJar đóng gói runtime dependency mà Gradle đã phân giải; Maven repackage bắt đầu từ archive được vòng đời package thông thường tạo ra. Nếu đầu vào sai, thao tác của Boot chỉ tiếp tục khởi chạy hoặc đóng gói chính đầu vào sai đó.

Đây là hướng phụ thuộc rất hữu ích khi chẩn đoán: trước tiên chứng minh công cụ build đã tạo đúng class và đồ thị dependency, sau đó mới kiểm tra Boot task xử lý chúng thế nào. Boot plugin không thể bù cho repository thiếu, dependency không phân giải được hoặc mã nguồn chưa biên dịch.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-vs-build-tool">Phần nào vẫn là trách nhiệm của Gradle hoặc Maven?</a>

<details>
<summary>Xem chi tiết</summary>

Gradle/Maven vẫn sở hữu biên dịch, phân giải dependency, khai báo repository, lifecycle/task ordering và mô hình multi-project. Boot chỉ sử dụng các capability đó để bổ sung hành vi riêng của ứng dụng Boot.

Một câu hỏi chẩn đoán hữu ích là: **vấn đề này có tồn tại trong một Java project không dùng Boot không?** Nếu có—ví dụ repository authentication, cấu hình compiler, liên kết task Gradle hoặc Maven lifecycle—hãy bắt đầu ở công cụ build. Nếu vấn đề liên quan `BOOT-INF`, `bootJar`, `repackage` hay `bootBuildImage`, lớp tích hợp Boot mới là nơi phù hợp hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-choice-boundary">Nên tư duy thế nào về hai đường tích hợp Gradle và Maven?</a>

<details>
<summary>Xem chi tiết</summary>

Gradle và Maven có mô hình cấu hình khác nhau, vì vậy không nên ép cú pháp của chúng thành ánh xạ một-một. Hãy học **mục đích của Boot** trước rồi mới học cơ chế của công cụ build đang dùng.

Ví dụ, cả hai đều tạo executable archive. Gradle biểu diễn việc đó bằng task như `bootJar`; Maven thường gắn `repackage` vào package lifecycle. Kết quả học tập giống nhau nhưng cách tích hợp với build system khác nhau.

Project nên chọn Gradle/Maven theo hệ sinh thái của chính hệ thống build, không phải vì Spring Boot bắt buộc một công cụ cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)
