<a id="back-to-top"></a>

# Hiểu và tùy chỉnh test auto-configuration

## Menu
- [Vì sao Boot cung cấp auto-configuration riêng cho test?](#test-auto-configuration-purpose)
- [Các slice annotation chọn tập auto-configuration được import như thế nào?](#slice-imported-auto-configuration)
- [Các annotation `@AutoConfigure...` bổ sung hoặc tinh chỉnh cơ chế hỗ trợ test như thế nào?](#auto-configure-annotations)
- [Loại một auto-configuration khỏi Boot test như thế nào?](#exclude-test-auto-configuration)
- [Khi nào nên dùng `@ImportAutoConfiguration` để bổ sung hạ tầng test?](#import-auto-configuration)
- [Chẩn đoán bean bị thiếu trong test context tập trung như thế nào?](#test-context-missing-bean-diagnosis)
- [Test auto-configuration bàn giao sang mô hình auto-configuration tổng quát của Boot ở đâu?](#test-auto-config-ownership-boundary)

## <a id="test-auto-configuration-purpose">Vì sao Boot cung cấp auto-configuration riêng cho test?</a>

<details>
<summary>Xem chi tiết</summary>
Production auto-configuration được thiết kế để lắp ráp application đang chạy. Test thường cần một tập bean hỗ trợ khác như mock client, embedded test infrastructure, cơ chế thay test database, JSON tester hoặc các tiện ích không nên trở thành component của quá trình khởi động production.

Boot test auto-configuration chỉ cung cấp các cơ chế hỗ trợ này trong test context và cho phép slice annotation import tập đã được chọn lọc. Nhờ đó test infrastructure vẫn được cấu hình theo kiểu khai báo mà không bị coi là application configuration thông thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="slice-imported-auto-configuration">Các slice annotation chọn tập auto-configuration được import như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Mỗi Boot slice gắn với một tập auto-configuration import xác định. Danh sách theo mục tiêu: web slice import hỗ trợ kiểm thử web, data slice import hỗ trợ hướng tới persistence và các slice khác chọn infrastructure phù hợp với công nghệ của chúng.

Khi cần biết chính xác tập import, hãy xem test-slice appendix của Boot. Đừng giả định một bean phải tồn tại chỉ vì production auto-configuration tương ứng có trong application.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Test Slices](https://docs.spring.io/spring-boot/3.3/appendix/test-auto-configuration/slices.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="auto-configure-annotations">Các annotation `@AutoConfigure...` bổ sung hoặc tinh chỉnh cơ chế hỗ trợ test như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Annotation như `@AutoConfigureMockMvc`, `@AutoConfigureWebTestClient` và `@AutoConfigureTestDatabase` thêm hoặc tinh chỉnh một cơ chế hỗ trợ kiểm thử tập trung quanh test context hiện tại. Chúng hữu ích khi lựa chọn context chính đã đúng nhưng một khả năng hỗ trợ cần được cấu hình rõ.

Đây không phải là chọn một slice khác. Annotation chính quyết định ranh giới context; `@AutoConfigure...` tinh chỉnh cơ chế hỗ trợ bên trong ranh giới đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exclude-test-auto-configuration">Loại một auto-configuration khỏi Boot test như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Khi một auto-configuration không phù hợp với kịch bản test, annotation test và cơ chế điều khiển auto-configuration của Boot cho phép loại trừ rõ ràng. Việc loại trừ nên nhắm tới một configuration đã biết gây ra infrastructure không mong muốn hoặc xung đột.

Trước khi loại trừ, hãy kiểm tra tại sao configuration đó thỏa điều kiện. Dependency bị thiếu, property sai hoặc hiểu sai ranh giới slice thường là nguyên nhân gốc cần sửa thay vì loại vĩnh viễn một auto-configuration hữu ích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="import-auto-configuration">Khi nào nên dùng `@ImportAutoConfiguration` để bổ sung hạ tầng test?</a>

<details>
<summary>Xem chi tiết</summary>
Hãy dùng `@ImportAutoConfiguration` khi test tập trung cần một auto-configuration cụ thể chưa nằm trong tập mặc định của slice. Boot xử lý auto-configuration import theo cơ chế riêng, bao gồm mô hình condition và order.

Không nên dùng `@Import` thông thường để import class auto-configuration như user configuration. `@Import` vẫn phù hợp cho class application/test configuration thông thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="test-context-missing-bean-diagnosis">Chẩn đoán bean bị thiếu trong test context tập trung như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Trước hết xác định ranh giới test đã chọn. Hỏi type bị thiếu có đáng lẽ được quy tắc scanning của slice chọn, được auto-configuration đã import tạo hay cần cung cấp tường minh như một đối tượng cộng tác. Sau đó mới kiểm tra condition và exclusion của auto-configuration nếu bean đáng lẽ được tạo tự động.

Thứ tự này tránh “sửa” slice bằng cách import production layer không liên quan. Ví dụ service bị thiếu trong `@WebMvcTest` thường là hành vi được mong đợi và nên được cung cấp như một đối tượng cộng tác tập trung.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="test-auto-config-ownership-boundary">Test auto-configuration bàn giao sang mô hình auto-configuration tổng quát của Boot ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>
Module này sở hữu cách Boot test annotations chọn, thêm hoặc exclude auto-configuration trong test context. Quy tắc tổng quát của `@AutoConfiguration`, conditions, ordering, back-off, exclusion và condition diagnostics thuộc Spring Boot auto-configuration module.

Khi lỗi phụ thuộc vào lý do một condition thỏa hoặc thứ tự giữa nhiều auto-configuration, hãy tiếp tục ở mô hình auto-configuration tổng quát thay vì lặp lại tại đây.

</details>

- [Quay lại đầu trang](#back-to-top)
