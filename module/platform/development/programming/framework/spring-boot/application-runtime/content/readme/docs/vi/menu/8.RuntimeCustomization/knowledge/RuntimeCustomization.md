<a id="back-to-top"></a>

# Ranh giới của tùy biến runtime

## Menu
- [Khi nào việc tùy biến runtime thực sự cần thiết?](#runtime-customization-decision)
- [Vì sao nên ưu tiên configuration property được hỗ trợ trước khi tùy biến bằng code?](#property-first-customization)
- [Có thể tùy biến điều gì qua `SpringApplication`?](#springapplication-customization)
- [Khi nào `SpringApplicationBuilder` hữu ích?](#springapplicationbuilder-customization)
- [Chọn extension point runtime theo thời điểm lifecycle như thế nào?](#runtime-extension-point-selection)
- [Tùy biến nào thực ra thuộc configuration, auto-configuration, container hoặc web runtime thay vì module này?](#customization-ownership-boundaries)

## <a id="runtime-customization-decision">Khi nào việc tùy biến runtime thực sự cần thiết?</a>

<details>
<summary>Xem chi tiết</summary>

Runtime customization chỉ nên xuất hiện khi hành vi ứng dụng cần khác giá trị mặc định được Boot hỗ trợ và không có bề mặt cấu hình đơn giản hơn để biểu diễn yêu cầu đó. Hãy bắt đầu từ hành vi cụ thể như chế độ startup, đăng ký listener, hình dạng ứng dụng hoặc chính sách khởi tạo, thay vì từ nhu cầu chung chung "muốn tự kiểm soát Boot".

Mỗi tùy biến bằng code đưa một phần hợp đồng runtime vào code ứng dụng. Điều đó đôi khi cần thiết nhưng khó phát hiện hơn property và có thể tương tác với auto-configuration hoặc quy ước framework.

Trước khi tùy biến, hãy xác định bên sở hữu và giai đoạn lifecycle. Nhiều vấn đề tưởng là `SpringApplication` thực ra thuộc externalized configuration, auto-configuration, container, executor hoặc cấu hình web server.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="property-first-customization">Vì sao nên ưu tiên configuration property được hỗ trợ trước khi tùy biến bằng code?</a>

<details>
<summary>Xem chi tiết</summary>

Nếu Boot đã có configuration property diễn tả đúng hành vi mong muốn, đó thường là lựa chọn đầu tiên. Property hiện diện trong configuration metadata, tham gia mô hình externalized configuration và thường giữ đúng hành vi back-off/lifecycle mà Boot thiết kế.

Tùy biến bằng code phù hợp khi bề mặt property không đủ hoặc ứng dụng phải lắp ráp `SpringApplication` trước khi context bình thường tồn tại. Không nên tự parse/bind property trong `main()` chỉ để cấu hình trông "tường minh" hơn.

Thứ tự ưu tiên, profiles và cơ chế `@ConfigurationProperties` thuộc module `externalized-configuration`. Ở đây trọng tâm là chọn bề mặt runtime được hỗ trợ trước khi hạ xuống hook thấp hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="springapplication-customization">Có thể tùy biến điều gì qua `SpringApplication`?</a>

<details>
<summary>Xem chi tiết</summary>

Thay vì dùng lối tắt static `SpringApplication.run(...)`, ứng dụng có thể tạo `SpringApplication`, thay đổi thiết lập được hỗ trợ rồi gọi `run(args)`. Cách này phù hợp với tùy chọn phải tồn tại ở thời điểm bootstrap như banner mode, listeners, initializers, application type hoặc lazy initialization.

```java
SpringApplication app = new SpringApplication(MyApplication.class);
app.setBannerMode(Banner.Mode.OFF);
app.addListeners(new BootstrapListener());
app.run(args);
```

Hãy giữ code bootstrap nhỏ và mang tính khai báo. Nếu đoạn code bắt đầu đăng ký business service hoặc tái tạo cấu hình container, trách nhiệm đã vượt khỏi phần thiết lập runtime của Boot sang Spring configuration/auto-configuration.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Customizing SpringApplication](https://docs.spring.io/spring-boot/3.3/reference/features/spring-application.html#features.spring-application.customizing-spring-application)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="springapplicationbuilder-customization">Khi nào `SpringApplicationBuilder` hữu ích?</a>

<details>
<summary>Xem chi tiết</summary>

`SpringApplicationBuilder` cung cấp fluent API để cấu hình và chạy `SpringApplication`. Trường hợp sử dụng nổi bật là tạo phân cấp `ApplicationContext` theo quan hệ parent/child; ngoài ra builder cũng giúp ghép các tùy chọn bootstrap theo phong cách fluent.

Hệ phân cấp này kéo theo ràng buộc: parent/child dùng chung `Environment`, và thành phần web phải nằm ở child context theo giới hạn mà Boot mô tả. Vì vậy hệ phân cấp là một lựa chọn kiến trúc chứ không phải cách viết đẹp hơn cho một context thông thường.

Chỉ dùng builder khi hệ phân cấp hoặc cách lắp ráp fluent giải quyết yêu cầu thật sự. Với ứng dụng một context thông thường, static `run` hoặc một `SpringApplication` tùy biến nhỏ thường dễ hiểu hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-extension-point-selection">Chọn extension point runtime theo thời điểm lifecycle như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Chọn runtime extension point theo giai đoạn sớm nhất thỏa điều kiện tiên quyết và theo mục đích của công việc.

| Nhu cầu | Cơ chế nên ưu tiên |
| --- | --- |
| Quan sát chuyển trạng thái lifecycle | `ApplicationListener` / event listener |
| Quan sát event trước khi bean tồn tại | đăng ký listener sớm |
| Công việc startup cần bean bình thường | `ApplicationRunner` / `CommandLineRunner` |
| Công việc nền liên tục | executor được quản lý hoặc scheduler |
| Thay thiết lập bootstrap được Boot hỗ trợ | property, `SpringApplication` hoặc builder |

Hook càng sớm càng có ít bảo đảm. Thường nên chọn *giai đoạn muộn nhất nhưng vẫn đáp ứng yêu cầu* để ứng dụng tận dụng hạ tầng được quản lý và dependency injection bình thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="customization-ownership-boundaries">Tùy biến nào thực ra thuộc configuration, auto-configuration, container hoặc web runtime thay vì module này?</a>

<details>
<summary>Xem chi tiết</summary>

Một tùy biến có thể thực hiện về mặt kỹ thuật ở nhiều lớp, nhưng quyền sở hữu quyết định nơi nào dễ hiểu và bảo trì. Giá trị cấu hình/profile thuộc `externalized-configuration`. Tạo bean theo điều kiện, back-off và các giá trị mặc định dùng lại thuộc `auto-configuration`. Lifecycle bean và phần nội bộ context tổng quát thuộc Spring Framework.

Web-server factory, server property, connector, TLS consumption, forwarded header và graceful shutdown thuộc `web-runtime`. Chẩn đoán production thuộc Actuator. Hành vi build/package thuộc `build-tooling-packaging`.

Module này giữ phần tùy biến `SpringApplication` runtime mang tính xuyên suốt cùng mô hình quyết định để chọn hook. Nếu một tùy biến có thể gọi tên chính xác bằng một bên sở hữu khác, hãy bàn giao cho bên đó thay vì mở rộng một nhóm "runtime customization" vô hạn.

</details>

- [Quay lại đầu trang](#back-to-top)
