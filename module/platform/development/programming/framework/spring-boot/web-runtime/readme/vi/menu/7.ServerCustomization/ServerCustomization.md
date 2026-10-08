<a id="back-to-top"></a>

# Tùy biến server bằng mã

## Menu
- [Ưu tiên property, sau đó customizer, cuối cùng factory bean](#server-customization-decision-order)
- [WebServerFactoryCustomizer là điểm mở rộng chính bằng mã](#web-server-factory-customizer)
- [Factory dùng chung và factory riêng theo từng server](#generic-vs-specific-factory-targets)
- [Customizer của Boot và ứng dụng được sắp thứ tự như thế nào?](#customizer-ordering)
- [Tự cung cấp WebServerFactory bean](#custom-web-server-factory)
- [Thêm connector, listener và các mô hình server nâng cao](#advanced-server-topology)

## <a id="server-customization-decision-order">Ưu tiên property, sau đó customizer, cuối cùng factory bean</a>

<details>
<summary>Xem chi tiết</summary>
Hãy dùng cơ chế tùy biến ít can thiệp nhất nhưng vẫn biểu diễn được yêu cầu. Bắt đầu với property `server.*` dùng chung. Nếu thiết lập phụ thuộc cách triển khai, kiểm tra `server.tomcat.*`, `server.jetty.*`, `server.undertow.*` hoặc `server.netty.*`. Chỉ chuyển sang mã khi mô hình property chưa đủ.

Thứ tự tăng mức can thiệp nên là:

```text
property dùng chung
    ↓
property riêng theo server
    ↓
WebServerFactoryCustomizer
    ↓
bean WebServerFactory tùy chỉnh
```

Càng đi xuống, mã càng phụ thuộc vào server API và chi phí đổi server/nâng cấp càng cao. Vì vậy mức tùy biến nên do yêu cầu thực tế quyết định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-server-factory-customizer">WebServerFactoryCustomizer là điểm mở rộng chính bằng mã</a>

<details>
<summary>Xem chi tiết</summary>
`WebServerFactoryCustomizer<T>` cho phép ứng dụng chỉnh factory do Boot chọn trước khi factory tạo server. Kiểu generic giới hạn customizer vào đúng loại factory, nhờ đó customizer riêng cho Tomcat không vô tình chạy với Jetty.

```java
@Bean
WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
    return factory -> factory.setBackgroundProcessorDelay(10);
}
```

Server API cụ thể bên trong callback thuộc cách triển khai. Phần Boot cần hiểu là hook vòng đời: ứng dụng tùy biến factory, còn Boot tiếp tục chịu trách nhiệm tạo và khởi động web server.

### Tài liệu tham khảo

- [Spring Boot 3.3 API — WebServerFactoryCustomizer](https://docs.spring.io/spring-boot/3.3/api/java/org/springframework/boot/web/server/WebServerFactoryCustomizer.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-vs-specific-factory-targets">Factory dùng chung và factory riêng theo từng server</a>

<details>
<summary>Xem chi tiết</summary>
Hãy nhắm tới loại factory rộng nhất nhưng vẫn cung cấp khả năng cần dùng. Customizer cho `ConfigurableWebServerFactory` có thể xử lý các mối quan tâm có trên lớp trừu tượng chung. Customizer cho `TomcatServletWebServerFactory` hoặc `NettyReactiveWebServerFactory` là lựa chọn có chủ đích để phụ thuộc vào một cách triển khai server và web stack cụ thể.

Phân biệt này quan trọng khi bảo trì. Customizer dùng chung thường vẫn dùng được sau khi đổi server; customizer riêng theo server phải nằm trong danh sách kiểm tra khi chuyển đổi vì API và hành vi của nó gắn với cách triển khai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="customizer-ordering">Customizer của Boot và ứng dụng được sắp thứ tự như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Nhiều customizer có thể cùng nhắm tới một factory. Quy tắc ordering của Spring quyết định thứ tự áp dụng, còn `WebServerFactoryCustomizer` do Boot tự động cấu hình dùng order `0`. Customizer của người dùng có thể triển khai `Ordered` hoặc dùng `@Order` khi cần chạy trước/sau một tùy biến khác.

Không nên dựa vào thứ tự phát hiện bean tình cờ. Nếu hai customizer cùng thay một trường, hãy thể hiện thứ tự rõ hoặc gom trách nhiệm để trạng thái factory cuối có thể dự đoán được.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-web-server-factory">Tự cung cấp WebServerFactory bean</a>

<details>
<summary>Xem chi tiết</summary>
Khai báo riêng bean `ServletWebServerFactory` hoặc `ReactiveWebServerFactory` sẽ làm auto-configuration factory riêng theo cách triển khai của Boot back off. Ứng dụng lúc này tự quyết định cách xây dựng factory.

Điều đó **không** bỏ toàn bộ chuỗi tùy biến của Boot. Các `WebServerFactoryCustomizer` được tự động cấu hình vẫn áp dụng lên factory tùy chỉnh. Vì vậy factory bean nên là lựa chọn cuối khi chính cách xây dựng factory phải thay đổi, và cần kiểm tra sự tương tác giữa trạng thái ban đầu tự cung cấp với customizer của Boot.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-server-topology">Thêm connector, listener và các mô hình server nâng cao</a>

<details>
<summary>Xem chi tiết</summary>
Một số mô hình kết nối cần mã phụ thuộc server. Ví dụ phổ biến là cấu hình HTTPS bằng `server.ssl.*` nhưng vẫn muốn thêm một HTTP connector không mã hóa. Spring Boot không biểu diễn đồng thời cặp HTTP + HTTPS connector chỉ bằng các property thông thường, nên connector bổ sung phải được thêm bằng customizer riêng theo server.

Nguyên tắc tương tự áp dụng cho listener tùy chỉnh, tài nguyên connector, protocol handler hoặc đối tượng server ngoài mô hình property dùng chung. Hãy giữ những API này tập trung quanh factory thay vì để mã riêng theo container lan vào mã nghiệp vụ.

### Tài liệu tham khảo

- [Spring Boot 3.3 How-to — Configure the Web Server](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html#howto.webserver.configure)

</details>

- [Quay lại đầu trang](#back-to-top)
