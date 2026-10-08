<a id="back-to-top"></a>

# Full application context với `@SpringBootTest`

## Menu
- [Khi nào full application context của Boot là ranh giới kiểm thử phù hợp?](#full-context-purpose)
- [`@SpringBootTest` nạp những gì vào test context?](#springboottest-context-model)
- [Kiểm thử full context dùng lại cơ chế phát hiện cấu hình chính của Boot như thế nào?](#full-context-configuration-discovery)
- [Full context đem lại độ sát thực tế và chi phí khởi động nào?](#full-context-fidelity-cost)
- [Khi nào nên dùng slice tập trung thay cho `@SpringBootTest`?](#full-context-vs-slice)
- [`WebEnvironment` thay đổi mô hình full context như thế nào?](#web-environment-handoff)

## <a id="full-context-purpose">Khi nào full application context của Boot là ranh giới kiểm thử phù hợp?</a>

<details>
<summary>Xem chi tiết</summary>
Hãy dùng full Boot context khi hành vi cần kiểm tra phụ thuộc nhiều application layer hoặc phụ thuộc cấu hình Boot gần với production phối hợp với nhau. Ví dụ gồm configuration binding cùng service wiring, tích hợp liên tầng, security/messaging infrastructure hoặc điều kiện khởi động mà slice tập trung cố ý loại bỏ.

Full-context test phải là lựa chọn độ sát thực tế có chủ đích. Nó tốn thời gian khởi động hơn và kéo theo nhiều infrastructure hơn test tập trung, nên chỉ dùng để chứng minh hành vi mà context nhỏ hơn không thể thiết lập đáng tin cậy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="springboottest-context-model">`@SpringBootTest` nạp những gì vào test context?</a>

<details>
<summary>Xem chi tiết</summary>
Mặc định, `@SpringBootTest` tìm cấu hình Boot chính của application rồi dùng `SpringApplication` để tạo context. Vì vậy application configuration, component scanning, auto-configuration, externalized properties và các cơ chế khởi động khác của Boot đều có thể tham gia.

Annotation này không nhất thiết khởi động network server. Mặc định, `WebEnvironment.MOCK` dùng mock web environment khi web infrastructure có mặt. Việc khởi động server được điều khiển riêng bằng `webEnvironment`.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Testing Spring Boot Applications](https://docs.spring.io/spring-boot/3.3/reference/testing/spring-boot-applications.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="full-context-configuration-discovery">Kiểm thử full context dùng lại cơ chế phát hiện cấu hình chính của Boot như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Full-context test thường tái sử dụng chính `@SpringBootApplication` / `@SpringBootConfiguration` mà Boot tìm cho application. Điều này giảm việc lặp lại wiring giữa production và test, đồng thời làm các condition của auto-configuration chạy trên mô hình cấu hình thực tế hơn.

Nếu test thật sự cần một cấu hình cấp cao nhất khác, có thể truyền class tường minh. Hãy dùng lựa chọn này cẩn thận vì thay cấu hình chính cũng thay ý nghĩa của test và có thể làm nó ít đại diện cho luồng khởi động production.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="full-context-fidelity-cost">Full context đem lại độ sát thực tế và chi phí khởi động nào?</a>

<details>
<summary>Xem chi tiết</summary>
Full context có độ sát thực tế cao vì nhiều production bean và auto-configuration cùng xuất hiện. Đổi lại là thời gian khởi động, khả năng kéo thêm external dependency và phạm vi lỗi có thể xảy ra lớn hơn. Spring context cache có thể phân bổ chi phí này khi nhiều test dùng cùng cấu hình thực tế.

Mỗi biến thể không cần thiết về property, profile, mock hoặc imported configuration đều có thể tạo cached context khác. Vì vậy full-context test cũng cần cấu hình ổn định và có thể tái sử dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="full-context-vs-slice">Khi nào nên dùng slice tập trung thay cho `@SpringBootTest`?</a>

<details>
<summary>Xem chi tiết</summary>
Hãy dùng slice khi mục tiêu kiểm thử thuộc một phần tập trung của application và infrastructure không liên quan chỉ làm tăng chi phí hoặc nhiễu. `@WebMvcTest`, `@WebFluxTest`, `@DataJpaTest` và `@JdbcTest` cố ý giới hạn context và import test auto-configuration theo mục đích cụ thể.

Slice không “kém đúng” nếu nó khớp ranh giới. Nó chỉ không đủ khi hành vi phụ thuộc đối tượng cộng tác bị loại bỏ, wiring liên tầng hoặc ngữ nghĩa khởi động đầy đủ của Boot.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-environment-handoff">`WebEnvironment` thay đổi mô hình full context như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
`WebEnvironment` quyết định full Boot context dùng mock web environment, khởi động embedded server thật hay vô hiệu web infrastructure. `MOCK` là mặc định. `RANDOM_PORT` và `DEFINED_PORT` tạo môi trường server thật, còn `NONE` tạo non-web context qua `SpringApplication`.

Chương tiếp theo tập trung vào hệ quả vận hành của các chế độ này: lựa chọn client, port thực tế và ranh giới transaction khi request đi qua HTTP server thật.

</details>

- [Quay lại đầu trang](#back-to-top)
