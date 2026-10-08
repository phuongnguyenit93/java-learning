<a id="back-to-top"></a>

# Xây chiến lược kiểm thử nhất quán cho Spring Boot

## Menu
- [Unit test, Boot slice, full context, server thật và dịch vụ thật kết hợp với nhau như thế nào?](#testing-strategy-spectrum)
- [Vì sao nên ưu tiên context nhỏ nhất vẫn đi qua đúng ranh giới cần kiểm thử?](#smallest-context-strategy)
- [Khi nào độ sát thực tế của server thật đáng với chi phí bổ sung?](#real-server-strategy)
- [Khi nào test nên dùng dịch vụ thật thông qua service connection?](#real-service-strategy)
- [Giữ cấu hình test context có khả năng tái sử dụng như thế nào?](#context-cache-strategy)
- [Phân loại lỗi giữa bootstrap, slice, tùy biến context và service connection như thế nào?](#testing-failure-classification)
- [Mối quan tâm kiểm thử nào thuộc Boot và phần nào thuộc module lân cận?](#testing-module-handoffs)

## <a id="testing-strategy-spectrum">Unit test, Boot slice, full context, server thật và dịch vụ thật kết hợp với nhau như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Spring Boot testing cung cấp một dải mức độ sát thực tế của context thay vì một annotation dùng cho mọi trường hợp. Unit test thuần chạy object không cần Spring. Boot slice nạp Spring context tập trung. `@SpringBootTest` nạp full application context. Chế độ chạy server thật thêm embedded HTTP server, còn service connection thêm dependency bên ngoài thật.

Hãy chọn mức phù hợp dựa vào hành vi cần chứng minh. Độ sát thực tế cao hữu ích cho ranh giới tích hợp nhưng đồng thời tăng thời gian khởi động, yêu cầu hạ tầng và số thành phần có thể gặp lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="smallest-context-strategy">Vì sao nên ưu tiên context nhỏ nhất vẫn đi qua đúng ranh giới cần kiểm thử?</a>

<details>
<summary>Xem chi tiết</summary>
Context nhỏ nhất nhưng đủ dùng cho phản hồi nhanh hơn và lỗi rõ hơn, trong khi vẫn đi qua ranh giới tích hợp quan trọng. Controller test không cần database nếu contract chỉ phụ thuộc web mapping cùng đối tượng cộng tác service được kiểm soát; repository test không cần toàn bộ web layer.

Đừng thu nhỏ context bằng cách mock mất chính tương tác mà test phải chứng minh. “Nhỏ nhất” nghĩa là tối thiểu **sau khi** giữ đúng ranh giới cần kiểm thử, không phải tối thiểu số lượng bean bằng mọi giá.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="real-server-strategy">Khi nào độ sát thực tế của server thật đáng với chi phí bổ sung?</a>

<details>
<summary>Xem chi tiết</summary>
Hãy dùng server thật khi hành vi phụ thuộc chính ranh giới server: tương tác HTTP ở cấp socket, server filter/connector, production server configuration, hành vi redirect, serialization qua luồng client/server thật hoặc web wiring end-to-end.

Nếu mock web environment chứng minh được cùng contract thì nó thường rẻ và dễ chẩn đoán hơn. Test chạy server thật nên tồn tại vì ranh giới server thực sự quan trọng, không chỉ vì trông có vẻ “integration” hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="real-service-strategy">Khi nào test nên dùng dịch vụ thật thông qua service connection?</a>

<details>
<summary>Xem chi tiết</summary>
Hãy dùng dịch vụ thật khi hành vi riêng của sản phẩm là một phần của rủi ro: database dialect, broker protocol, search-engine mapping, ngữ nghĩa caching hoặc chi tiết tích hợp mà in-memory fake không tái hiện đáng tin cậy.

Boot service connection giảm công sức cấu hình nhưng không làm hạ tầng thật trở nên miễn phí. Khởi động container, khả năng sẵn có của image, mức sử dụng tài nguyên và khởi tạo service đều làm tăng chi phí của bộ test, nên chỉ dành chúng cho test thực sự hưởng lợi từ độ sát thực tế đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-cache-strategy">Giữ cấu hình test context có khả năng tái sử dụng như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Spring context cache có thể biến một lần khởi động Boot context tốn kém thành chi phí một lần khi nhiều test dùng cùng cấu hình thực tế. Khả năng tái sử dụng giảm khi property, profile, imported configuration, định nghĩa mock/spy, dynamic context customizer hoặc đầu vào bootstrap khác nhau không cần thiết.

Hãy nhóm test quanh cấu trúc context ổn định. Ưu tiên test configuration có thể tái sử dụng thay vì nhiều biến thể riêng lẻ gần giống nhau, và đưa test chỉ cần hành vi ở cấp object ra khỏi Spring hoàn toàn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-failure-classification">Phân loại lỗi giữa bootstrap, slice, tùy biến context và service connection như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Hãy phân loại lỗi theo ranh giới sớm nhất bị sai. Nếu context không tìm được cấu hình chính hoặc gặp lỗi trước khi bean sẵn sàng, kiểm tra Boot bootstrap. Nếu context tập trung thiếu bean, kiểm tra lựa chọn slice và test auto-configuration. Nếu sai giá trị hoặc đối tượng cộng tác, kiểm tra test properties, import, mock hoặc spy.

Nếu application đã cấu hình đúng nhưng không kết nối được dependency thật, kiểm tra service connection rồi mới xuống service/container bên ngoài. Thứ tự này tránh chẩn đoán ở mức thấp trước khi test context được xác nhận đúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-module-handoffs">Mối quan tâm kiểm thử nào thuộc Boot và phần nào thuộc module lân cận?</a>

<details>
<summary>Xem chi tiết</summary>
Giữ Boot testing tập trung vào cách Boot application được lắp ráp cho test: `@SpringBootTest`, web environment, slice, test auto-configuration, giá trị ghi đè cục bộ cho cấu hình Boot, tích hợp thay bean và service connection.

Spring TestContext sở hữu vòng đời/cache context và test-managed transaction. Spring MVC/WebFlux testing sở hữu cơ chế kiểm thử request. JUnit sở hữu việc thực thi test. Mockito sở hữu hành vi mock. Testcontainers sở hữu phần tích hợp container/Docker tổng quát. Các module production web runtime, persistence, messaging và configuration sở hữu hành vi của application đang được kiểm thử.

</details>

- [Quay lại đầu trang](#back-to-top)
