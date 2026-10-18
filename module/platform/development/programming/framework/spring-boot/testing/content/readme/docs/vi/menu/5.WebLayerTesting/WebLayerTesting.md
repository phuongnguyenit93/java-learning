<a id="back-to-top"></a>

# Kiểm thử web tập trung với `@WebMvcTest` và `@WebFluxTest`

## Menu
- [`@WebMvcTest` chọn những thành phần nào cho kiểm thử MVC?](#webmvc-test-purpose)
- [Cung cấp đối tượng cộng tác của controller cho `@WebMvcTest` như thế nào?](#webmvc-test-collaborators)
- [`@WebFluxTest` chọn những thành phần nào cho kiểm thử web reactive?](#webflux-test-purpose)
- [Web slice auto-configure những test client nào?](#web-slice-auto-configured-clients)
- [Khi nào web slice là đủ và khi nào cần server thật?](#mock-web-vs-real-server)
- [Cấu hình web slice của Boot bàn giao sang cơ chế test MVC hoặc WebFlux ở đâu?](#web-testing-framework-boundary)

## <a id="webmvc-test-purpose">`@WebMvcTest` chọn những thành phần nào cho kiểm thử MVC?</a>

<details>
<summary>Xem chi tiết</summary>
`@WebMvcTest` tạo một Spring MVC test slice tập trung. Nó chọn controller cùng web infrastructure liên quan và loại phần lớn service, repository cùng auto-configuration không liên quan.

Slice này phù hợp để kiểm tra request mapping, validation integration, serialization, controller advice, filter nằm trong phạm vi web slice và MVC configuration ở tầng Boot integration mà không cần khởi động toàn bộ application.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Testing Spring Boot Applications](https://docs.spring.io/spring-boot/3.3/reference/testing/spring-boot-applications.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webmvc-test-collaborators">Cung cấp đối tượng cộng tác của controller cho `@WebMvcTest` như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Vì service và repository thường nằm ngoài slice, đối tượng cộng tác của controller phải được cung cấp rõ ràng. Một lựa chọn riêng của Boot thường dùng là `@MockBean`, dùng để thêm hoặc thay bean trong test context để controller vẫn nhận dependency qua dependency injection thông thường.

Nếu phải import ngày càng nhiều đối tượng cộng tác production chỉ để web slice chạy được, hãy xem lại ranh giới. Hành vi đó có thể cần integration context lớn hơn thay vì một web slice được “xây lại” quá nhiều.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-test-purpose">`@WebFluxTest` chọn những thành phần nào cho kiểm thử web reactive?</a>

<details>
<summary>Xem chi tiết</summary>
`@WebFluxTest` là phiên bản tập trung tương ứng cho reactive web. Nó nạp infrastructure hướng tới WebFlux và các component web reactive được chọn nhưng loại các application layer không liên quan.

Hãy dùng khi mục tiêu là hành vi routing/controller, codec, validation integration, xử lý exception hoặc reactive web configuration ở ranh giới framework. Ngữ nghĩa của reactive pipeline và hành vi Reactor tự thân vẫn nằm ngoài phạm vi trách nhiệm của Boot testing.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-slice-auto-configured-clients">Web slice auto-configure những test client nào?</a>

<details>
<summary>Xem chi tiết</summary>
`@WebMvcTest` auto-configure `MockMvc`, cho phép chạy MVC request processing mà không khởi động HTTP server thật. `@WebFluxTest` có thể auto-configure `WebTestClient` cho reactive web slice.

Boot chịu trách nhiệm làm các client này có mặt trong test context được chọn. Request builder, exchange, expectation và assertion API chi tiết thuộc phần hỗ trợ kiểm thử web tương ứng của Spring.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mock-web-vs-real-server">Khi nào web slice là đủ và khi nào cần server thật?</a>

<details>
<summary>Xem chi tiết</summary>
Web slice là đủ khi hành vi có thể được chứng minh hoàn toàn trong ranh giới request processing của framework: mapping, validation, serialization, advice, security integration đã cấu hình cho slice hoặc sự cộng tác của controller.

Hãy dùng `@SpringBootTest` chạy server thật khi assertion phụ thuộc hành vi của embedded server, ranh giới network thật, server configuration, tương tác HTTP client/server hoặc wiring liên tầng mà slice cố ý loại bỏ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-testing-framework-boundary">Cấu hình web slice của Boot bàn giao sang cơ chế test MVC hoặc WebFlux ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>
Boot sở hữu việc chọn component/auto-configuration cho `@WebMvcTest` hoặc `@WebFluxTest` và auto-configure test client tương ứng. Spring Framework sở hữu cách `MockMvc` và `WebTestClient` thực thi request và assertion.

Production MVC/WebFlux request pipeline thuộc các Spring Framework web module. Boot testing chỉ tạo môi trường tập trung để pipeline đó được kiểm thử.

</details>

- [Quay lại đầu trang](#back-to-top)
