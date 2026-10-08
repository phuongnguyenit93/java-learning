<a id="back-to-top"></a>

# Nhận diện loại ứng dụng web

## Menu
- [NONE, SERVLET và REACTIVE](#web-application-types)
- [Boot suy ra WebApplicationType từ classpath như thế nào?](#classpath-type-deduction)
- [Khi tín hiệu Servlet và Reactive cùng xuất hiện](#servlet-reactive-precedence)
- [Ghi đè bằng spring.main.web-application-type](#explicit-type-override)
- [Loại ứng dụng thay đổi context và việc khởi động server ra sao?](#application-type-consequences)

## <a id="web-application-types">NONE, SERVLET và REACTIVE</a>

<details>
<summary>Xem chi tiết</summary>
`WebApplicationType` có ba giá trị: `NONE`, `SERVLET` và `REACTIVE`. `NONE` nghĩa là Boot tạo context ứng dụng không chạy web và không khởi động embedded web server. `SERVLET` chọn Servlet web runtime. `REACTIVE` chọn Reactive web runtime.

Hãy xem đây là một quyết định bootstrap rất sớm. Nó ảnh hưởng tới loại `ApplicationContext` mà Spring Boot tạo và các web-server auto-configuration nào đủ điều kiện tham gia ở các bước sau.

### Tài liệu tham khảo

- [Spring Boot 3.3 API — WebApplicationType](https://docs.spring.io/spring-boot/3.3/api/java/org/springframework/boot/WebApplicationType.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classpath-type-deduction">Boot suy ra WebApplicationType từ classpath như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Nếu không cấu hình rõ loại ứng dụng, `SpringApplication` suy ra giá trị từ classpath. Ở mức mô hình tư duy, classpath chỉ có reactive web stack dẫn tới `REACTIVE`, classpath có Servlet web stack dẫn tới `SERVLET`, còn khi thiếu các dấu hiệu cần thiết của môi trường web thì kết quả là `NONE`.

Vì vậy chỉ cần thêm hoặc bỏ starter cũng có thể làm hành vi runtime thay đổi dù `main` không đổi. Đồ thị dependency là một đầu vào cho quyết định của Boot; loại ứng dụng sau đó được các conditional auto-configuration và quá trình tạo context sử dụng.

Chi tiết class nào được Boot dùng làm tín hiệu là chi tiết triển khai. Khi thiết kế ứng dụng, hợp đồng cần nhớ là classpath quyết định mặc định và loại ứng dụng được cấu hình tường minh có thể ghi đè mặc định đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="servlet-reactive-precedence">Khi tín hiệu Servlet và Reactive cùng xuất hiện</a>

<details>
<summary>Xem chi tiết</summary>
Khi Spring MVC và Spring WebFlux cùng có trên classpath, Spring Boot mặc định chọn mô hình Servlet/MVC. Quyết định này có chủ ý vì nhiều ứng dụng MVC thêm WebFlux chỉ để dùng `WebClient` chứ không muốn chuyển toàn bộ runtime sang WebFlux.

Do đó, thấy thư viện reactive trong dependency tree chưa đủ để kết luận ứng dụng đang chạy kiểu `REACTIVE`. Nếu cả hai web stack cùng tồn tại nhưng ứng dụng thực sự cần chạy WebFlux, hãy cấu hình lựa chọn đó rõ ràng.

### Tài liệu tham khảo

- [Spring Boot 3.3 Reference — Reactive Web Applications](https://docs.spring.io/spring-boot/3.3/reference/web/reactive.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="explicit-type-override">Ghi đè bằng spring.main.web-application-type</a>

<details>
<summary>Xem chi tiết</summary>
Property `spring.main.web-application-type` cho phép ép quyết định bootstrap. Các giá trị thường dùng là `servlet`, `reactive` và `none`.

```properties
spring.main.web-application-type=reactive
```

Hãy dùng ghi đè khi classpath cố ý chứa cả hai stack hoặc khi ứng dụng có dependency web nhưng một chế độ thực thi cụ thể không nên mở server. `none` hữu ích cho chế độ dòng lệnh/batch muốn tái sử dụng dependency của ứng dụng nhưng không cung cấp HTTP endpoint.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="application-type-consequences">Loại ứng dụng thay đổi context và việc khởi động server ra sao?</a>

<details>
<summary>Xem chi tiết</summary>
Loại ứng dụng được chọn sẽ thay đổi cách context được tạo trước khi server xuất hiện. `SERVLET` dẫn Boot tới context ứng dụng web Servlet và `ServletWebServerFactory`; `REACTIVE` dẫn tới context ứng dụng web reactive và `ReactiveWebServerFactory`; `NONE` dùng context không phải web và không đi theo quá trình khởi động embedded web server.

Điều này cũng cho thấy thay server dependency khác với thay `WebApplicationType`. Đổi Tomcat sang Jetty chỉ đổi cách triển khai bên trong cùng mô hình Servlet runtime. Đổi từ `SERVLET` sang `REACTIVE` thay đổi cả mô hình web runtime và nhánh auto-configuration đủ điều kiện chạy.

</details>

- [Quay lại đầu trang](#back-to-top)
