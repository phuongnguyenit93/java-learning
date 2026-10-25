<a id="back-to-top"></a>

# Executable archive và Spring Boot Loader

## Menu
- [Vì sao Spring Boot dùng nested archive?](#nested-archive-model)
- [`BOOT-INF` và `WEB-INF` được tổ chức như thế nào?](#boot-archive-layout)
- [Vì sao cần Spring Boot Loader?](#spring-boot-loader-purpose)
- [Classpath index và layer index mô tả điều gì?](#archive-indexes)
- [Khi nào việc giải nén executable archive trở nên quan trọng?](#archive-extraction-boundary)

## <a id="nested-archive-model">Vì sao Spring Boot dùng nested archive?</a>

<details>
<summary>Xem chi tiết</summary>

Ứng dụng thường phụ thuộc hàng chục JAR có sẵn. Nếu gộp phẳng toàn bộ nội dung của chúng vào một archive lớn, ranh giới artifact bị mất và resource/metadata dễ va chạm. Boot giữ các dependency JAR ở dạng nested artifact trong executable archive.

```text
application classes
dependency-a.jar
dependency-b.jar
dependency-c.jar
        ↓
một executable Boot archive
nhưng các dependency JAR vẫn là artifact riêng bên trong
```

Kết quả vẫn là một file thuận tiện để bàn giao nhưng còn cấu trúc dependency rõ ràng. Spring Boot Loader làm cho cấu trúc nested này có thể chạy được.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Executable Jar Format](https://docs.spring.io/spring-boot/3.3/specification/executable-jar/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boot-archive-layout">`BOOT-INF` và `WEB-INF` được tổ chức như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Executable JAR đặt application classes/resources trong `BOOT-INF/classes` và dependency JAR trong `BOOT-INF/lib`. Layout này tách content của application khỏi runtime dependencies.

Executable WAR dùng cấu trúc servlet-oriented `WEB-INF`: application classes và thư viện thường nằm trong các vị trí `WEB-INF` tương ứng, còn dependency dự kiến do external container cung cấp được tách khỏi nhóm runtime library thông thường.

Thông thường không cần sửa các thư mục này bằng tay. Chúng hữu ích khi chẩn đoán: inspect archive có thể cho biết class/dependency mong đợi bị đóng gói sai vị trí hay không có trong artifact.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-boot-loader-purpose">Vì sao cần Spring Boot Loader?</a>

<details>
<summary>Xem chi tiết</summary>

Mô hình `java -jar` thông thường của JVM không tự coi mọi nested dependency JAR là classpath chuẩn của application. Spring Boot Loader hiểu executable archive layout, xây effective classpath từ các vị trí đã đóng gói rồi gọi main class của application.

Loader là cơ chế packaging/khởi chạy, không phải Spring container. Nó chạy **trước** khi `SpringApplication` tạo `ApplicationContext`. Nếu loader không tìm thấy class đã đóng gói thì lỗi vẫn nằm ở artifact bàn giao; quá trình tạo bean và auto-configuration chưa bắt đầu.

Hành vi của Loader là một phần của hợp đồng định dạng thực thi. Nếu startup lỗi trước khi `Start-Class` được gọi, hãy kiểm tra manifest, bố cục archive, thư viện lồng nhau và cấu hình Loader trước khi điều tra Spring bean. Ranh giới này tạo điểm kiểm tra rõ giữa “artifact không khởi chạy được” và “ứng dụng Boot đã khởi chạy nhưng gặp lỗi”.

Một số library không hoạt động đúng khi vẫn nằm nested. Boot packaging có cơ chế requires-unpack cho các dependency ngoại lệ đó để loader giải nén chúng ra vị trí tạm lúc runtime. Chỉ nên dùng khi library thật sự cần filesystem access, không phải tối ưu hiệu năng chung.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="archive-indexes">Classpath index và layer index mô tả điều gì?</a>

<details>
<summary>Xem chi tiết</summary>

Boot archive có thể chứa index metadata mô tả nội dung đã đóng gói. Classpath index cung cấp thứ tự rõ ràng cho nested classpath entries; layers index mô tả file nào thuộc layer đóng gói logic nào.

```text
classpath index
→ thứ tự packaged classpath entries

layers index
→ packaged file thuộc extraction/image layer nào
```

Đây là packaging metadata, không thay thế dependency declaration của Gradle/Maven và cũng không định nghĩa container runtime.

Classpath index là metadata khởi chạy tùy chọn, còn `layers.idx` là metadata packaging mô tả file thuộc layer nào. Không file nào thay đổi dependency graph Gradle/Maven; chúng chỉ mô tả artifact đã được đóng gói. Nếu index sai, hãy truy ngược đầu vào packaging thay vì sửa việc phân giải dependency bằng cách chỉnh index thủ công.

Nên xem các index được sinh ra là đầu ra build. Chỉnh tay sau packaging làm artifact khó tái lập và tạo đơn vị bàn giao không còn tương ứng với cấu hình build trong hệ quản lý mã nguồn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="archive-extraction-boundary">Khi nào việc giải nén executable archive trở nên quan trọng?</a>

<details>
<summary>Xem chi tiết</summary>

`java -jar` là mô hình bàn giao đơn giản nhất, nhưng một số môi trường production thích dạng đã extract. Spring Boot 3.3 hỗ trợ extraction qua tools jar mode để dependency có thể nằm ngoài JAR ứng dụng và bố cục kết quả vẫn được khởi chạy theo cách được hỗ trợ.

Việc giải nén có thể phù hợp với platform muốn cache/manage application files riêng hoặc muốn tránh một phần chi phí đọc nested archive lúc khởi động.

Extraction thay đổi **bố cục bàn giao**, không thay đổi ngữ nghĩa của ứng dụng. Khi tiến trình đã chạy, mô hình runtime của Boot vẫn như cũ.

Chế độ tools jar của Boot 3.3 có thể extract ứng dụng thực thi bằng `java -Djarmode=tools -jar app.jar extract`. Bố cục mặc định tách các thư viện vào thư mục `lib` và giữ JAR ứng dụng có manifest tham chiếu các thư viện đó. Môi trường production sau đó khởi chạy JAR ứng dụng đã extract theo cách bình thường.

Extraction có thể giảm một phần startup cost nhỏ của việc đọc classes từ nested jars và phù hợp hơn với platform cache application files riêng. Sau khi application đã start, extraction không tạo Spring runtime model khác; nó chỉ là lựa chọn delivery layout.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Efficient Deployments](https://docs.spring.io/spring-boot/3.3/reference/packaging/efficient.html)

</details>

- [Quay lại đầu trang](#back-to-top)
