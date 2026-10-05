<a id="back-to-top"></a>

# MockMvc và kiểm thử Servlet web

## Menu
- [Mục đích và execution model của MockMvc](#mockmvc-purpose-and-model)
- [Servlet API mock object](#servlet-api-mocks)
- [Standalone setup và WebApplicationContext setup](#mockmvc-setup-strategies)
- [Tạo request và dispatch qua Spring MVC](#request-building-and-dispatch)
- [Response assertion và result handling](#response-assertions)
- [Binding, validation, filter và exception handling](#mvc-behavior-under-test)
- [Kiểm thử asynchronous MVC request](#async-request-testing)
- [Giới hạn của MockMvc và khi nào cần live server](#mockmvc-limitations-and-live-server)

## <a id="mockmvc-purpose-and-model">Mục đích và execution model của MockMvc</a>

<details>
<summary>Xem chi tiết</summary>

`MockMvc` là điểm vào kiểm thử phía server của Spring Framework dành cho Spring MVC trên Servlet stack. Nó chạy cùng cơ chế xử lý request của `DispatcherServlet` mà ứng dụng MVC sử dụng, nhưng đưa vào Servlet request/response dạng mock thay vì khởi động Servlet container và mở network port.

Vị trí này làm MockMvc mạnh hơn việc gọi trực tiếp controller method. Gọi method trực tiếp chủ yếu chứng minh Java logic của controller; MockMvc còn có thể đi qua request mapping, argument resolution, data binding, conversion, validation, message conversion, interceptor, exception handling, model/view processing và các thành phần MVC khác có trong đường xử lý request đã cấu hình.

MockMvc vẫn là bộ khung kiểm thử quanh Spring MVC. Cơ chế sâu của `DispatcherServlet`, handler mapping, binding, filter và MVC exception resolution thuộc module spring-framework/web; chương này tập trung vào cách hạ tầng kiểm thử gọi và quan sát những cơ chế đó.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 API — MockMvc](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/web/servlet/MockMvc.html)
- [Spring Framework 6.1 Reference — MockMvc](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="servlet-api-mocks">Servlet API mock object</a>

<details>
<summary>Xem chi tiết</summary>

Module `spring-test` cung cấp các cách triển khai mock cho những Servlet API type quan trọng, gồm request, response, session và servlet context. MockMvc xây execution model trên các đối tượng này, vì vậy mã Spring MVC vẫn nhìn thấy Servlet contract quen thuộc dù test chạy hoàn toàn trong cùng tiến trình.

Các mock object cũng hữu ích ngoài MockMvc khi cần test tập trung một đoạn code phụ thuộc trực tiếp Servlet API. Test có thể tạo header, parameter, attribute, session, cookie và trạng thái response mà không cần khởi động container thật.

Cần hiểu chúng là test double có thể kiểm soát, không phải Servlet container hoàn chỉnh. Quá trình khởi động container, hành vi connector, TLS, network I/O thật, deployment descriptor, giá trị mặc định riêng của container và xử lý protocol ở mức thấp nằm ngoài bằng chứng mà mock object cung cấp.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 API — org.springframework.mock.web](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/mock/web/package-summary.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mockmvc-setup-strategies">Standalone setup và WebApplicationContext setup</a>

<details>
<summary>Xem chi tiết</summary>

MockMvc có hai cách setup chính. `standaloneSetup(...)` bắt đầu từ một hoặc nhiều controller instance rồi dựng đủ MVC infrastructure quanh chúng cho test tập trung. Dependency có thể inject trực tiếp vào controller, còn controller advice, validator, conversion hoặc thành phần MVC khác được đăng ký rõ ràng khi kịch bản cần.

`webAppContextSetup(...)` bắt đầu từ `WebApplicationContext` do TestContext framework tải. MockMvc khi đó dùng cấu hình Spring MVC thật nằm trong context, nên test cho bằng chứng mạnh hơn về component scanning, MVC configuration, advice, converter, interceptor, filter được đưa vào builder và wiring ở cấp context.

Việc chọn setup phụ thuộc vào bằng chứng cần có. Standalone setup nhanh và rõ khi mục tiêu là contract của một controller. WebApplicationContext setup phù hợp hơn khi cần chứng minh cấu hình MVC thật và bean wiring hoạt động cùng nhau. Context caching giúp dạng integration test này vẫn hiệu quả khi nhiều test dùng chung cấu hình.

### Tài liệu tham khảo

- [Spring Framework 6.1 Reference — MockMvc Setup Options](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="request-building-and-dispatch">Tạo request và dispatch qua Spring MVC</a>

<details>
<summary>Xem chi tiết</summary>

`MockMvcRequestBuilders` tạo các request như GET, POST, PUT, DELETE, multipart và generic HTTP request. Builder có thể đặt URI, query/form parameter, header, cookie, trạng thái session, request attribute, content type, locale, body content và các attribute khác mà MVC stack đã cấu hình để hiểu.

`mockMvc.perform(...)` đưa request đã dựng vào MockMvc. Từ đó request đi qua đường dispatch của Spring MVC và tạo ra `MvcResult` chứa mock request/response cùng model, view, exception, interceptor và trạng thái async nếu có.

Cách tạo request phải sát với contract cần kiểm thử. Thêm form parameter không chứng minh cùng điều như gửi JSON body để `HttpMessageConverter` decode. Tương tự, đặt sẵn Java object vào request attribute không chứng minh quy trình HTTP binding thông thường có thể tạo object đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="response-assertions">Response assertion và result handling</a>

<details>
<summary>Xem chi tiết</summary>

Sau `perform(...)`, MockMvc cung cấp result actions dạng fluent. Các `MockMvcResultMatchers` có sẵn có thể assert status, header, cookie, body content, JSON/XML path, model value, view, flash attribute, trạng thái request và forwarded/redirected URL.

Nếu kịch bản cần assert exception mà Spring MVC đã resolve, hãy lấy `MvcResult.getResolvedException()` sau `andReturn()` hoặc dùng custom `ResultMatcher`/lambda. Spring Framework 6.1.14 không có exception matcher factory dựng sẵn trong `MockMvcResultMatchers`.

`andExpect(...)` thêm assertion cho result hiện tại, còn `andExpectAll(...)` gom nhiều expectation để báo lỗi cùng nhau. `andDo(...)` gắn result handler như in thông tin chẩn đoán, và `andReturn()` trả `MvcResult` khi bước sau cần truy cập trực tiếp. Builder cũng có thể khai báo expectation hoặc handler áp dụng cho mọi request.

Nên ưu tiên assertion diễn đạt hành vi công khai mà server hứa với client. Chi tiết model hoặc handler có giá trị khi chúng thật sự là contract phía server; assertion quá sát chi tiết cài đặt nội bộ thường làm test dễ vỡ mà không tăng bằng chứng hữu ích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-behavior-under-test">Binding, validation, filter và exception handling</a>

<details>
<summary>Xem chi tiết</summary>

Vì MockMvc dispatch qua Spring MVC, nó có thể kiểm tra những tương tác mà việc gọi trực tiếp controller bỏ qua. Request có thể được map đúng handler, convert thành method argument, bind vào command object, validate, đi qua interceptor/filter đã cấu hình, được xử lý bằng `@ExceptionHandler` hoặc `@ControllerAdvice`, rồi render hoặc serialize thành response cuối.

Cách setup quyết định lượng hạ tầng thật mà test sử dụng. Với `webAppContextSetup`, test nhìn thấy MVC infrastructure và application bean đã tải trong `WebApplicationContext`. Với standalone setup, người viết test phải đăng ký những thành phần kịch bản cần; quên custom validator hoặc advice có thể làm test pass trên cấu hình khác production.

Filter có thể được thêm vào MockMvc để kiểm thử quanh dispatcher, nhưng semantics của subsystem khác vẫn thuộc module tương ứng. Chẳng hạn authentication/authorization filter chain của Spring Security thuộc Spring Security dù MockMvc có thể tích hợp với nó. Module này chỉ trình bày ranh giới kiểm thử của MockMvc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-request-testing">Kiểm thử asynchronous MVC request</a>

<details>
<summary>Xem chi tiết</summary>

Spring MVC có thể giải phóng Servlet thread ban đầu rồi hoàn tất request sau thông qua async return type và Servlet async processing. MockMvc biểu diễn luồng này thành hai giai đoạn thay vì giả định response cuối đã có sẵn ngay.

Lần `perform(...)` đầu có thể assert `request().asyncStarted()` rồi lấy `MvcResult`. Khi asynchronous result đã sẵn sàng, test gọi `asyncDispatch(mvcResult)` và tiếp tục assertion status, header, body hoặc model trên lần dispatch được tiếp tục. Nếu dispatch tiếp theo resolve exception, hãy kiểm tra `MvcResult.getResolvedException()` hoặc dùng custom `ResultMatcher`, giống luồng MockMvc đồng bộ.

```java
MvcResult result = mockMvc.perform(get("/report"))
        .andExpect(request().asyncStarted())
        .andReturn();

mockMvc.perform(asyncDispatch(result))
        .andExpect(status().isOk());
```

Cách này chứng minh async dispatch contract của Spring MVC trong môi trường Servlet mock. Kích thước thread pool, connector thật của container, việc ngắt kết nối mạng và đặc tính tải khi chạy production cần loại bằng chứng khác ngoài MockMvc.

### Tài liệu tham khảo

- [Spring Framework 6.1 Reference — MockMvc Async Requests](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mockmvc-limitations-and-live-server">Giới hạn của MockMvc và khi nào cần live server</a>

<details>
<summary>Xem chi tiết</summary>

MockMvc cho bằng chứng mạnh về Spring MVC request handling mà không cần server, nhưng chính việc không có server thật xác định giới hạn của nó. MockMvc không mở socket và không kiểm tra Servlet container connector thật, TCP/TLS, HTTP framing thật, runtime port/deployment configuration, quá trình khởi động riêng của container hay hành vi chỉ tồn tại ngoài đường xử lý của Spring MVC dispatcher.

Hãy dùng live-server test khi hành vi cần kiểm tra phụ thuộc vào ranh giới còn thiếu đó: giao tiếp HTTP client/server thật, cấu hình server khi chạy production, header/encoding ở tầng transport, container integration, hành vi xuyên tiến trình hoặc hạ tầng chỉ được cài khi runtime được deploy.

Không cần nâng mọi MVC test lên live server. Mapping, binding, validation, controller advice, response serialization và phần lớn hành vi của dispatcher thường rẻ hơn và dễ chẩn đoán hơn bằng MockMvc. Một suite tốt dùng MockMvc cho bằng chứng về MVC và dành live-server test cho hành vi mà mock environment không thể chứng minh.

</details>

- [Quay lại đầu trang](#back-to-top)
