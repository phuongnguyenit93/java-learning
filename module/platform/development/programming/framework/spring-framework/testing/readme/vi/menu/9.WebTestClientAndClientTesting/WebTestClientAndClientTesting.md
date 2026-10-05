<a id="back-to-top"></a>

# WebTestClient và kiểm thử HTTP phía client

## Menu
- [Mục đích và client model của WebTestClient](#webtestclient-purpose-and-model)
- [Bind WebTestClient với WebFlux target](#webflux-binding-modes)
- [Bind WebTestClient với MockMvc](#mockmvc-webtestclient-binding)
- [Bind với live HTTP server](#live-server-binding)
- [Response assertion và server-side assertion](#webtestclient-assertions)
- [Kiểm thử HTTP client collaborator bằng MockRestServiceServer](#mockrestservice-server)
- [Mock server và live integration cung cấp bằng chứng khác nhau thế nào?](#mock-server-vs-live-integration)

## <a id="webtestclient-purpose-and-model">Mục đích và client model của WebTestClient</a>

<details>
<summary>Xem chi tiết</summary>

`WebTestClient` là client kiểm thử có fluent request API và response assertion. Bên trong nó dùng hạ tầng của `WebClient`, nhưng mục tiêu là kiểm thử: cùng một cách viết phía client có thể nhắm tới ứng dụng WebFlux không cần live server, ứng dụng Spring MVC thông qua MockMvc, hoặc một HTTP server thật.

Sự linh hoạt này có ích vì API kiểm thử vẫn quen thuộc trong khi loại bằng chứng bên dưới thay đổi. Mock binding chứng minh framework request handling trong cùng tiến trình; live-server binding đi qua ranh giới mạng. Vì vậy test nên thể hiện rõ cách bind đang dùng thay vì coi mọi `WebTestClient` test có giá trị bằng chứng giống nhau.

Chương này tập trung vào mô hình kiểm thử. Cơ chế sâu của WebFlux dispatch và `WebClient` thuộc module reactive, cơ chế sâu của Spring MVC thuộc module web, còn việc chọn HTTP client cho ứng dụng thuộc module Integration HTTP.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 API — WebTestClient](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/web/reactive/server/WebTestClient.html)
- [Spring Framework 6.1 Reference — WebTestClient](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-binding-modes">Bind WebTestClient với WebFlux target</a>

<details>
<summary>Xem chi tiết</summary>

Với WebFlux, `WebTestClient` có thể bind trực tiếp vào controller instance, `RouterFunction` hoặc `ApplicationContext`. Những cách bind này dựng WebFlux server setup trong cùng tiến trình bằng hạ tầng mock request/response nên không cần kết nối TCP hay cổng lắng nghe.

`bindToController(...)` tập trung nhất khi mục tiêu là controller cùng phần WebFlux infrastructure được cấu hình rõ. `bindToRouterFunction(...)` phù hợp với functional routing. `bindToApplicationContext(...)` dùng cấu hình WebFlux từ Spring context nên cho bằng chứng mạnh hơn về wiring thật của framework.

Sau khi chọn cách bind phía server, `configureClient()` cho phép cấu hình phía client như base URL, default header, codec hoặc response timeout trước khi dựng client. Các thiết lập phía client này không thay đổi sự thật rằng mock WebFlux binding vẫn hoàn toàn trong cùng tiến trình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mockmvc-webtestclient-binding">Bind WebTestClient với MockMvc</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework cũng cho phép `WebTestClient` dùng MockMvc làm server phía sau. `MockMvcWebTestClient.bindToController(...)` tạo MVC setup tập trung, còn `bindToApplicationContext(...)` dùng `WebApplicationContext`. Một `MockMvc` instance đã cấu hình sẵn cũng có thể được nối vào `WebTestClient`.

Request vẫn không đi qua mạng. MockMvc connector chuyển client exchange thành quá trình xử lý request của MockMvc, vì vậy bằng chứng thực tế vẫn là cơ chế Servlet-based MVC của chương trước, chỉ được quan sát qua assertion API của `WebTestClient`.

Binding này hữu ích khi muốn giữ một cách viết assertion response có thể chuyển sang live server sau đó. Nó không biến Spring MVC thành WebFlux và cũng không kiểm thử `WebClient` như network client khi chạy production. Phía server vẫn là MockMvc và `DispatcherServlet`.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 API — MockMvcWebTestClient](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/web/servlet/client/MockMvcWebTestClient.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="live-server-binding">Bind với live HTTP server</a>

<details>
<summary>Xem chi tiết</summary>

`WebTestClient.bindToServer()` tạo client thực hiện HTTP exchange thật. Test thường cấu hình base URL trỏ đến server đã chạy cho kịch bản. Từ thời điểm đó request đi qua ranh giới transport và test có thể quan sát những hành vi mà binding trong cùng tiến trình không thể tái hiện.

Live binding có thể chứng minh endpoint đã deploy thực sự truy cập được và request/response serialization, cấu hình runtime của server, header hướng ra mạng cùng các hành vi quan sát được ở tầng transport hoạt động với nhau. Mức bằng chứng cụ thể vẫn phụ thuộc server và hạ tầng mà test thật sự khởi động.

Không nên đồng nhất live binding với cách kiểm thử riêng của Spring Boot. Việc khởi động embedded server bằng `@SpringBootTest`, random port và Boot test slice thuộc module Spring Boot Testing. Ở mức Spring Framework, ý chính chỉ là `WebTestClient` có thể nhắm tới bất kỳ HTTP server nào truy cập được.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webtestclient-assertions">Response assertion và server-side assertion</a>

<details>
<summary>Xem chi tiết</summary>

Sau `exchange()`, `WebTestClient` cung cấp expectation dạng fluent cho status, header, cookie và body content. Test có thể decode một body object, list, byte thô, JSON/XML content hoặc streaming element rồi áp dụng assertion có sẵn hoặc tùy chỉnh.

Các response assertion này là bằng chứng client nhìn thấy và dùng được cho cả mock-bound lẫn live target. Chúng thường nên là lựa chọn đầu tiên cho HTTP contract vì mô tả chính xác điều client có thể quan sát.

Khi server phía sau là MockMvc, Spring còn có thể chuyển `ExchangeResult` về MockMvc result actions qua `MockMvcWebTestClient.resultActionsFor(...)`. Nhờ đó test có thể kiểm tra thông tin phía server như model, view hoặc handler, những thông tin HTTP client từ xa bình thường không thể thấy. Nếu cần kiểm tra exception đã được resolve, hãy lấy `MvcResult` tương ứng rồi đọc `getResolvedException()` hoặc dùng custom `ResultMatcher`; Spring 6.1.14 vẫn không có exception matcher factory dựng sẵn cho MockMvc. Khả năng này tồn tại vì MockMvc chạy trong cùng tiến trình, không phải vì HTTP response chứa các dữ liệu nội bộ đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mockrestservice-server">Kiểm thử HTTP client collaborator bằng MockRestServiceServer</a>

<details>
<summary>Xem chi tiết</summary>

`MockRestServiceServer` giải bài toán ở phía đối diện: mã đang được kiểm thử là HTTP **client** gọi sang service khác. Mock server có thể bind vào `RestTemplate` hoặc, từ Spring Framework 6.1, vào `RestClient.Builder`. Test khai báo request dự kiến và trả stub response mà không cần mở server socket thật.

Assertion thường kiểm tra URI, HTTP method, header hoặc request body, rồi trả response được kiểm soát bằng `MockRestResponseCreators`. Sau khi mã client chạy, `verify()` xác nhận các expectation bắt buộc đã được thỏa mãn. Server cũng có thể reset expectation/request đã ghi nhận giữa các kịch bản.

Vì binding thay đường `ClientHttpRequestFactory` của client bằng hạ tầng kiểm thử, cơ chế này rất tốt để cô lập hành vi của đối tượng cộng tác nhưng yếu hơn về cấu hình transport khi chạy production. Nó chứng minh mã client dựng đúng Spring HTTP request và xử lý response đã cung cấp; nó không chứng minh DNS, socket, TLS, cấu hình proxy hay hành vi của remote service thật.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 API — MockRestServiceServer](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/web/client/MockRestServiceServer.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mock-server-vs-live-integration">Mock server và live integration cung cấp bằng chứng khác nhau thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Các loại HTTP test double loại bỏ những phần khác nhau của đường chạy production nên bằng chứng cũng khác nhau. `MockRestServiceServer` chạy trong cùng tiến trình rất phù hợp khi câu hỏi là mã client của ứng dụng có dựng đúng Spring request và phản ứng đúng với response được kiểm soát hay không. Nó cho kết quả ổn định và nhanh, nhưng không chạy network I/O thật.

Một mock web server độc lập lắng nghe trên port thật vẫn mô phỏng service phía xa, nhưng cho phép HTTP client stack dùng trong production thực hiện trao đổi qua mạng thật. Nhờ đó test có thể phát hiện vấn đề ở request factory, thiết lập kết nối, timeout, cấu hình TLS/proxy và các hiệu ứng khác ở tầng transport hoặc protocol mà `MockRestServiceServer` bỏ qua. Môi trường integration thật còn đi xa hơn khi đưa service phía xa thật hoặc hệ thống đã deploy vào kịch bản.

Hãy chọn ranh giới nhỏ nhất nhưng vẫn đủ khả năng làm hành vi cần kiểm tra thất bại khi có lỗi. Lý thuyết chọn HTTP client và cross-client trade-off thuộc module Integration HTTP; module testing này chỉ xác định mỗi ranh giới kiểm thử có thể và không thể cung cấp loại bằng chứng nào.

</details>

- [Quay lại đầu trang](#back-to-top)
