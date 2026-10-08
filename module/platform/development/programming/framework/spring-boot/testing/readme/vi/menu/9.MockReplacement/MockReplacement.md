<a id="back-to-top"></a>

# Thay thế bean bằng mock và bọc bean bằng spy trong kiểm thử Spring Boot

## Menu
- [Khi nào nên dùng `@MockBean` để thêm hoặc thay thế bean trong test context?](#mockbean-purpose)
- [Khi nào nên dùng `@SpyBean` để bọc bean hiện có?](#spybean-purpose)
- [Việc thay thế bean làm Boot test context thay đổi như thế nào?](#mock-replacement-context-boundary)
- [Vì sao `@MockBean` không thể cấu hình lại hành vi cần dùng ngay trong lúc context refresh?](#mock-refresh-time-limit)
- [Định nghĩa mock và spy ảnh hưởng cache key của test context như thế nào?](#mock-spy-cache-impact)
- [Phạm vi thay thế bean của Boot kết thúc ở đâu và hành vi Mockito bắt đầu ở đâu?](#mockito-testing-boundary)

## <a id="mockbean-purpose">Khi nào nên dùng `@MockBean` để thêm hoặc thay thế bean trong test context?</a>

<details>
<summary>Xem chi tiết</summary>
`@MockBean` tích hợp Mockito mock vào Spring Boot test `ApplicationContext`. Nó có thể thêm bean mới khi chưa có bean phù hợp hoặc thay một bean hiện có để Boot-managed component nhận mock qua dependency injection thông thường.

Hãy dùng khi ranh giới test có Spring context nhưng một đối tượng cộng tác cần được kiểm soát thay vì khởi động thật. Web slice thường thay đối tượng cộng tác service theo cách này. Nếu test chỉ là unit test thuần và không cần Spring context, hãy dùng Mockito trực tiếp.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Testing Spring Boot Applications](https://docs.spring.io/spring-boot/3.3/reference/testing/spring-boot-applications.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spybean-purpose">Khi nào nên dùng `@SpyBean` để bọc bean hiện có?</a>

<details>
<summary>Xem chi tiết</summary>
`@SpyBean` giữ bean thật trong Boot context nhưng bọc nó bằng Mockito spy. Cách này hữu ích khi phần lớn hành vi production vẫn nên chạy bình thường nhưng test cần quan sát tương tác hoặc ghi đè một phần nhỏ hành vi.

Vì bean thật và dependency của nó vẫn tồn tại, spy không phải một mock rẻ hơn. Đây là lựa chọn thiên về integration và có thể tương tác với Spring proxy hoặc caching infrastructure; chi tiết về Mockito stubbing và proxy unwrapping thuộc các module chuyên trách tương ứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mock-replacement-context-boundary">Việc thay thế bean làm Boot test context thay đổi như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Các định nghĩa mock và spy là context customizer. Chúng thay đổi đồ thị bean thực tế mà Spring tạo cho test và vì vậy ảnh hưởng đến định danh của cached test context.

Điều này hữu ích vì bean thay thế tham gia autowiring thông thường, nhưng cũng có nghĩa hai test gần như giống nhau mà có bộ mock/spy khác nhau có thể không tái sử dụng cùng context. Hãy xem việc thay dependency như một phần của thiết kế test context.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mock-refresh-time-limit">Vì sao `@MockBean` không thể cấu hình lại hành vi cần dùng ngay trong lúc context refresh?</a>

<details>
<summary>Xem chi tiết</summary>
`@MockBean` tạo mock trước context refresh, nhưng stubbing trong test method chỉ diễn ra sau khi context đã refresh xong. Nếu một bean khác cần một giá trị trả về cụ thể từ mock **ngay trong lúc chính nó khởi tạo**, cấu hình hành vi ở test method là quá muộn.

Boot khuyến nghị tạo và cấu hình mock đó bằng `@Bean` trong test configuration. Khi đó hành vi cần thiết đã tồn tại trước khi bean phụ thuộc khởi tạo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mock-spy-cache-impact">Định nghĩa mock và spy ảnh hưởng cache key của test context như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Spring context cache tính cả các context customizer thực tế, trong đó có định nghĩa mock/spy do Boot quản lý. Vì vậy các bộ thay thế khác nhau có thể tạo cache key khác nhau và làm phát sinh thêm lần khởi động context.

Một bộ test có nhiều test, mỗi test khai báo mock hơi khác nhau, có thể chậm đáng kể dù từng test nhỏ. Hãy tái sử dụng cấu trúc context ổn định khi hợp lý và dùng unit test thuần nếu tích hợp Spring không phải hành vi cần chứng minh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mockito-testing-boundary">Phạm vi thay thế bean của Boot kết thúc ở đâu và hành vi Mockito bắt đầu ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>
Boot sở hữu phần tích hợp dùng để đăng ký Mockito mock/spy thành bean, inject chúng vào test và reset mock theo hỗ trợ test của Boot. Mockito sở hữu cách mock được tạo, stub, verify, match argument và spy.

Các câu hỏi như `when` so với `doReturn`, argument matcher, strictness, verification mode hay ngữ nghĩa spy thuộc module Mockito. Boot testing chỉ giải thích cách test double đi vào Spring application context.

</details>

- [Quay lại đầu trang](#back-to-top)
