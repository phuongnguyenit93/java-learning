<a id="back-to-top"></a>

# Spring Boot DevTools

## Menu
- [Vì sao Spring Boot DevTools tồn tại?](#devtools-purpose)
- [Automatic Restart dùng hai ClassLoader như thế nào?](#restart-classloader-model)
- [LiveReload và các mặc định dành cho phát triển hỗ trợ ra sao?](#livereload-property-defaults)
- [Thêm DevTools và điều chỉnh phạm vi restart như thế nào?](#devtools-setup-restart-scope)
- [Remote DevTools là gì và vì sao nhạy cảm về bảo mật?](#remote-devtools-security)
- [DevTools khác công cụ vận hành thực tế như thế nào?](#devtools-production-boundary)

## <a id="devtools-purpose">Vì sao Spring Boot DevTools tồn tại?</a>

<details>
<summary>Xem chi tiết</summary>

`spring-boot-devtools` là module tùy chọn dành cho thời gian phát triển, giúp rút ngắn vòng lặp sửa code → build lại → quan sát kết quả. Nó không thêm khả năng nghiệp vụ cho ứng dụng; thay vào đó, DevTools hỗ trợ automatic restart, LiveReload và các property mặc định phù hợp khi phát triển.

Ranh giới quan trọng nằm ở môi trường sử dụng. DevTools được thiết kế cho quá trình phát triển. Với luồng thông thường, các công cụ dành cho lập trình viên tự bị vô hiệu hóa khi chạy ứng dụng đã đóng gói đầy đủ, và tích hợp build của Boot mặc định không đưa DevTools vào artifact dùng cho môi trường vận hành thực tế sau khi repackage.

Vì vậy nên xem DevTools là **công cụ hỗ trợ vòng phản hồi của lập trình viên**, không phải yêu cầu bắt buộc của vòng đời ứng dụng. Bỏ DevTools ra không nên làm mất hành vi nghiệp vụ của ứng dụng.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Developer Tools](https://docs.spring.io/spring-boot/3.3/reference/using/devtools.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="restart-classloader-model">Automatic Restart dùng hai ClassLoader như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Automatic Restart nhanh hơn việc restart toàn bộ JVM vì DevTools sử dụng hai ClassLoader. Những class ít thay đổi, thường là third-party JAR thông thường, được nạp bởi **base ClassLoader**. Các class đang được phát triển được nạp bởi **restart ClassLoader**.

```text
base ClassLoader
└── dependency JAR ổn định

restart ClassLoader
└── application classes đang phát triển
```

Khi DevTools phát hiện một cập nhật classpath phù hợp, nó bỏ restart ClassLoader cũ và tạo lại loader này, trong khi base ClassLoader đã được nạp vẫn giữ nguyên. `ApplicationContext` được restart với restart loader mới, nên vòng phản hồi thường nhanh hơn restart toàn bộ tiến trình từ đầu.

Cơ chế này cũng giải thích một dạng lỗi thực tế: đối tượng hoặc thư viện giả định định danh classloader theo cách cố định có thể hoạt động khác khi DevTools bật. Dự án nhiều module càng cần chú ý khi module dùng chung bị nạp vào loader không mong muốn. Nếu tắt restart làm lỗi biến mất, vị trí classloader là hướng chẩn đoán hợp lý tiếp theo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="livereload-property-defaults">LiveReload và các mặc định dành cho phát triển hỗ trợ ra sao?</a>

<details>
<summary>Xem chi tiết</summary>

LiveReload giải quyết bài toán khác với restart. DevTools có thể chạy embedded LiveReload server để báo cho tiện ích trình duyệt tương thích khi tài nguyên thay đổi, từ đó trình duyệt tự refresh. Tài nguyên tĩnh và template thường có thể được xử lý mà không cần restart toàn bộ ứng dụng.

DevTools còn áp dụng các property mặc định dành cho quá trình phát triển. Cache rất có ích khi vận hành thực tế nhưng có thể che mất thay đổi lúc phát triển, nên DevTools tắt hoặc điều chỉnh cache ở các tích hợp được hỗ trợ. Ví dụ, tập mặc định có `spring.thymeleaf.cache=false` khi các DevTools property đang có hiệu lực.

Các tính năng này cùng phục vụ một mục tiêu: giảm thời gian phản hồi. Restart làm mới class của ứng dụng và context khi classpath thay đổi; LiveReload làm mới trình duyệt khi tài nguyên phù hợp thay đổi; property mặc định khiến thay đổi dễ quan sát hơn.

Các mặc định vẫn có thể kiểm soát. `spring.devtools.add-properties=false` tắt nhóm property bổ sung của DevTools, còn `spring.devtools.livereload.enabled=false` tắt LiveReload server.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="devtools-setup-restart-scope">Thêm DevTools và điều chỉnh phạm vi restart như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Với Gradle, nên giữ dependency trong configuration dành cho quá trình phát triển:

```groovy
dependencies {
    developmentOnly 'org.springframework.boot:spring-boot-devtools'
}
```

DevTools theo dõi các thư mục classpath, nên việc lưu tệp nguồn không phải lúc nào cũng đủ. Source đã sửa phải được biên dịch hoặc sao chép để runtime classpath thực sự thay đổi. Trong IntelliJ IDEA, thao tác build project sẽ tạo cập nhật classpath đó; tính năng auto-build của IDE có thể tự động hóa bước này khi được cấu hình phù hợp.

Với dự án nhiều module hoặc layout khác thường, có thể mở rộng đường dẫn được theo dõi:

```properties
spring.devtools.restart.additional-paths=../infrastructure/src/main/java
```

Bạn cũng có thể điều chỉnh tài nguyên nào gây restart bằng `spring.devtools.restart.exclude` / `additional-exclude`, và tinh chỉnh vị trí classloader qua `META-INF/spring-devtools.properties` với các mục `restart.include.*` và `restart.exclude.*`.

Bằng chứng cần kiểm tra rất trực tiếp: tạo một thay đổi có mặt trên classpath rồi xác nhận ứng dụng báo restart. Nếu thay đổi chưa bao giờ tới runtime classpath, đổi DevTools property không thể bù cho bước build/IDE vốn chưa tạo class mới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="remote-devtools-security">Remote DevTools là gì và vì sao nhạy cảm về bảo mật?</a>

<details>
<summary>Xem chi tiết</summary>

Remote DevTools hỗ trợ một quy trình phát triển trong đó client trên máy phát triển giao tiếp với thành phần hỗ trợ DevTools nằm trong ứng dụng đang chạy từ xa. Tính năng này phải được bật chủ động; phía ứng dụng từ xa phải cố ý đóng gói DevTools và cấu hình `spring.devtools.remote.secret`.

Ranh giới bảo mật quan trọng hơn sự tiện lợi. Spring Boot cảnh báo rõ hỗ trợ từ xa có thể tạo rủi ro bảo mật, chỉ nên dùng trên mạng tin cậy hoặc được bảo vệ bằng SSL, và **không bao giờ nên bật trên bản triển khai dùng để vận hành thực tế**. Đây cũng không phải giao thức triển khai chung; mục tiêu của nó là hỗ trợ vòng phản hồi trong quá trình phát triển.

Hỗ trợ từ xa vì thế là ngoại lệ so với mô hình thông thường "DevTools không nằm trong artifact đã đóng gói". Muốn bật tính năng từ xa, bạn phải chủ động đưa DevTools vào ứng dụng đã repackage. Bước bổ sung này cần làm rủi ro trở nên rõ ràng, thay vì biến DevTools thành dependency mặc định cho môi trường vận hành thực tế.

Trong Spring Boot 3.3, Remote DevTools không được hỗ trợ cho ứng dụng Spring WebFlux.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="devtools-production-boundary">DevTools khác công cụ vận hành thực tế như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

DevTools và Actuator đều tạo ra hành vi mà lập trình viên có thể quan sát, nhưng phục vụ các giai đoạn khác nhau của vòng đời phần mềm.

| DevTools | Actuator |
| --- | --- |
| phản hồi nhanh khi phát triển | bề mặt vận hành cho ứng dụng đang chạy thực tế |
| restart, LiveReload, property mặc định khi phát triển | health, metrics integration, loggers, diagnostic/management endpoints |
| bình thường bị loại/vô hiệu hóa khi đóng gói để vận hành thực tế | được thiết kế cho ứng dụng đang chạy, kèm quyết định exposure/bảo mật |

Phân biệt này bảo vệ mô hình tư duy: restart nhanh trên máy phát triển không phải giám sát health, và LiveReload server không phải management endpoint dùng cho vận hành thực tế. Nếu câu hỏi là "làm sao thấy kết quả chỉnh sửa nhanh hơn?", DevTools có thể giúp. Nếu câu hỏi là "dịch vụ đang chạy hiện ở trạng thái gì?", nội dung học chuyển sang `actuator`.

</details>

- [Quay lại đầu trang](#back-to-top)
