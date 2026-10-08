<a id="back-to-top"></a>

# Chạy ứng dụng bằng công cụ build

## Menu
- [Một lần chạy ứng dụng lúc phát triển qua công cụ build của Boot là gì?](#boot-aware-development-run)
- [Gradle `bootRun` khởi chạy ứng dụng như thế nào?](#gradle-bootrun)
- [Maven `spring-boot:run` khởi chạy ứng dụng như thế nào?](#maven-spring-boot-run)
- [Khi nào nên chạy qua công cụ build thay vì dùng `java -jar`?](#build-tool-run-vs-packaged-run)
- [Run task cấu hình phần nào và phần nào vẫn thuộc `SpringApplication`?](#run-task-runtime-boundary)

## <a id="boot-aware-development-run">Một lần chạy ứng dụng lúc phát triển qua công cụ build của Boot là gì?</a>

<details>
<summary>Xem chi tiết</summary>

Cách chạy phát triển có nhận biết Boot khởi chạy ứng dụng trực tiếp từ đầu ra đã biên dịch và runtime classpath của build. Mục tiêu là vòng lặp chỉnh sửa-build-chạy nhanh: không cần tạo executable archive cuối cùng trước mỗi lần chạy trên máy cục bộ.

Điều này khác với việc kiểm chứng packaged artifact. Development run chứng minh application có thể khởi động với classpath hiện tại; `java -jar` sau đó mới chứng minh **packaged layout và launcher** cũng hoạt động đúng.

Hệ quả thực tế là lần chạy lúc phát triển thành công chỉ là bằng chứng cho đầu ra source set và việc phân giải runtime dependency, không chứng minh manifest của archive cuối hay bố cục thư viện lồng nhau là đúng. Hãy giữ bằng chứng đúng phạm vi: nó chứng minh ứng dụng khởi chạy được từ mô hình build, còn packaging là ranh giới khác cần xác minh riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="gradle-bootrun">Gradle `bootRun` khởi chạy ứng dụng như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot Gradle plugin cung cấp `bootRun`, một task thực thi Java cấu hình quanh main source set của ứng dụng. Nó dùng runtime classpath dành cho phát triển, xác định main class và cho phép build cấu hình đối số JVM, đối số ứng dụng, environment, system property cùng các đầu vào khởi chạy liên quan.

```bash
./gradlew bootRun
```

Task này thuận tiện cho development nhưng không phải bài kiểm tra executable archive, vì application không cần khởi động qua packaged Boot loader layout.

Trong Boot 3.3, BootRun là lớp con của JavaExec. Vì vậy các cơ chế kiểm soát khởi chạy quen thuộc của JavaExec vẫn dùng được, còn Boot cung cấp quy ước như main runtime classpath và tự động tìm main class. Boot cũng bật cơ chế khởi chạy tối ưu cho phát triển theo mặc định; có thể tắt khi chẩn đoán cần so sánh với cách khởi chạy JVM thông thường.

Application arguments và JVM/system properties là các input khác nhau. Ví dụ --args truyền application arguments, trong khi JavaExec configuration điều khiển JVM arguments và system properties. Trộn lẫn các channel này là nguyên nhân phổ biến khiến một property tưởng như bị bỏ qua.

### Tài liệu tham khảo

- [Spring Boot 3.3 Gradle Plugin — Running your Application](https://docs.spring.io/spring-boot/3.3/gradle-plugin/running.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="maven-spring-boot-run">Maven `spring-boot:run` khởi chạy ứng dụng như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Goal `spring-boot:run` của Maven plugin cung cấp đường khởi chạy tương ứng cho Maven project. Plugin ghép classpath ứng dụng và khởi động Boot main class đã cấu hình, đồng thời hỗ trợ truyền đối số và các đầu vào môi trường phù hợp.

```bash
./mvnw spring-boot:run
```

Maven vẫn chịu trách nhiệm phân giải dependency và vòng đời build. Khi tiến trình đã gọi `SpringApplication.run`, vòng đời runtime của Boot mới chịu trách nhiệm cho hành vi ứng dụng.

Maven run goal cũng có các kênh riêng cho đối số ứng dụng, đối số JVM, biến môi trường, system property và profile đang hoạt động. Nên dùng các đầu vào plugin này thay vì nhúng giả định của một máy cục bộ vào POM. Nhờ đó việc khởi chạy lúc phát triển dễ lặp lại trên máy làm việc khác.

Nếu lỗi chỉ xuất hiện sau khi ứng dụng được package đúng dạng artifact bàn giao, run goal không phải bằng chứng phù hợp. Hãy chuyển sang package rồi `java -jar` hoặc đường image để thật sự đi qua ranh giới packaging đang nghi ngờ.

### Tài liệu tham khảo

- [Spring Boot 3.3 Maven Plugin — Running your Application](https://docs.spring.io/spring-boot/3.3/maven-plugin/run.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="build-tool-run-vs-packaged-run">Khi nào nên chạy qua công cụ build thay vì dùng `java -jar`?</a>

<details>
<summary>Xem chi tiết</summary>

Dùng `bootRun` hoặc `spring-boot:run` khi ưu tiên feedback nhanh trong development. Dùng `java -jar` khi cần bằng chứng về chính artifact sẽ được bàn giao.

```text
chỉnh sửa/debug trên máy cục bộ
→ build-tool run

kiểm tra manifest/main class/BOOT-INF/loader/dependency packaging
→ build executable archive
→ java -jar artifact
```

Một project có thể chạy development tốt nhưng vẫn đóng gói sai. Vì vậy quy trình bàn giao nên kiểm tra dạng đã đóng gói thay vì suy luận tính đúng đắn của đóng gói từ `bootRun`.

Hai đường khởi chạy còn có thể dùng tập dependency khác nhau. Dependency chỉ dành cho phát triển có thể tham gia BootRun nhưng bị loại khỏi archive production theo chủ đích. Điều đó hữu ích cho devtools, nhưng cũng có nghĩa lần chạy trên máy cục bộ có thể thành công nhờ thành phần chỉ có lúc phát triển trong khi runtime đã đóng gói không chứa nó.

Kiểm tra bản phát hành đáng tin cậy vì vậy phải xem artifact đã đóng gói như một đối tượng kiểm thử riêng. Khi cần hãy kiểm tra nội dung, khởi chạy đúng JAR/image sẽ bàn giao và không dùng việc chạy thành công từ IDE/công cụ build thay cho bằng chứng rằng đóng gói bản phát hành là đúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="run-task-runtime-boundary">Run task cấu hình phần nào và phần nào vẫn thuộc `SpringApplication`?</a>

<details>
<summary>Xem chi tiết</summary>

Build plugin kiểm soát **cách tiến trình JVM được khởi chạy**: classpath, main class, đối số JVM/ứng dụng, environment và cấu hình khởi chạy của công cụ build. Khi `main` gọi `SpringApplication.run`, mô hình runtime tiếp quản.

Events, tạo context, runners, availability, phân tích lỗi, task execution và shutdown không phải ngữ nghĩa của `bootRun`. Nếu cùng một lỗi runtime cũng xảy ra với `java -jar`, hãy điều tra `application-runtime` hoặc phần runtime chịu trách nhiệm tương ứng thay vì tiếp tục chỉnh build task.

</details>

- [Quay lại đầu trang](#back-to-top)
