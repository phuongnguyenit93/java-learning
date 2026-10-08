<a id="back-to-top"></a>

# Chọn Boot test slice nhỏ nhất nhưng đủ dùng

## Menu
- [Boot test slice giải quyết vấn đề gì?](#test-slice-purpose)
- [Slice giới hạn component scanning và phạm vi context như thế nào?](#test-slice-context-boundary)
- [Slice chọn test auto-configuration theo mục tiêu như thế nào?](#test-slice-auto-configuration-model)
- [Vì sao một test nên bắt đầu từ một annotation `@...Test` duy nhất?](#single-slice-rule)
- [Chọn slice nhỏ nhất nhưng vẫn chứng minh được hành vi như thế nào?](#smallest-slice-decision)
- [Khi nào slice cần bàn giao sang full context?](#slice-full-context-handoff)

## <a id="test-slice-purpose">Boot test slice giải quyết vấn đề gì?</a>

<details>
<summary>Xem chi tiết</summary>
Boot test slice nạp một application context được giới hạn có chủ đích cho một loại hành vi. Thay vì khởi động mọi application bean và mọi auto-configuration phù hợp, slice chọn component cùng test infrastructure liên quan tới layer tập trung như MVC, WebFlux, JPA hoặc JDBC.

Lợi ích không chỉ là tốc độ. Context nhỏ làm ranh giới cần kiểm thử rõ hơn và giảm lỗi không liên quan. Đánh đổi là hành vi phụ thuộc layer bị loại bỏ sẽ không thể được slice đó chứng minh.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Test Slices](https://docs.spring.io/spring-boot/3.3/appendix/test-auto-configuration/slices.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="test-slice-context-boundary">Slice giới hạn component scanning và phạm vi context như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Slice annotation dùng type-exclusion filter và quy tắc scanning tập trung để chỉ phát hiện application component phù hợp. Web slice chẳng hạn chọn infrastructure hướng tới controller thay vì mọi service/repository trong application.

Giới hạn này là chủ đích. Khi thiếu đối tượng cộng tác, hãy hỏi trước liệu slice có được thiết kế để loại component đó không, thay vì lập tức coi bean bị thiếu là lỗi của application.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="test-slice-auto-configuration-model">Slice chọn test auto-configuration theo mục tiêu như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Mỗi slice import một tập test/application auto-configuration được chọn lọc cho mục tiêu của nó. Danh sách chính xác khác nhau theo annotation và được Boot ghi rõ trong phụ lục test auto-configuration.

Vì vậy slice vẫn nhận biết Boot dù không phải full `@SpringBootTest` context: phần Boot configuration phù hợp vẫn được kích hoạt, còn auto-configuration không liên quan bị loại có chủ đích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="single-slice-rule">Vì sao một test nên bắt đầu từ một annotation `@...Test` duy nhất?</a>

<details>
<summary>Xem chi tiết</summary>
Boot không hỗ trợ dùng nhiều annotation slice `@...Test` trên cùng một test. Hãy chọn một slice làm ranh giới kiểm thử chính thay vì chồng nhiều slice với nhau.

Hãy chọn slice phù hợp nhất với hành vi cần kiểm tra, rồi bổ sung hỗ trợ từ slice khác thông qua annotation `@AutoConfigure...` tương ứng khi Boot cung cấp. Chỉ dùng `@ImportAutoConfiguration`, `@Import` cho user configuration hoặc mock/test bean khi test thực sự cần phần hỗ trợ bổ sung đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="smallest-slice-decision">Chọn slice nhỏ nhất nhưng vẫn chứng minh được hành vi như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Hãy bắt đầu từ hành vi quan sát được thay vì cấu trúc package production. Nếu cần chứng minh controller mapping và serialization, web slice thường đủ. Nếu cần JPA mapping/query, data slice đúng ranh giới hơn. Nếu cần khởi động hoặc wiring liên tầng, dùng full context.

Slice nhỏ nhất hữu ích là context nhỏ nhất vẫn chứa mọi ranh giới mà assertion phụ thuộc. Thu nhỏ hơn mức đó chỉ tạo cảm giác an toàn sai hoặc buộc phải mock quá nhiều.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="slice-full-context-handoff">Khi nào slice cần bàn giao sang full context?</a>

<details>
<summary>Xem chi tiết</summary>
Chuyển sang `@SpringBootTest` khi hành vi vốn đã vượt qua ranh giới của slice, phụ thuộc auto-configuration rộng hơn, cần quá trình khởi động gần production hoặc cần web server thật. Tránh liên tục import production configuration vào slice cho tới khi nó âm thầm giống full application.

Đây là quyết định thiết kế: slice chứng minh tích hợp tập trung; full context chứng minh sự phối hợp trên ranh giới Boot application lớn hơn.

</details>

- [Quay lại đầu trang](#back-to-top)
