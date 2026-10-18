<a id="back-to-top"></a>

# Spring MVC trên Servlet Stack

## Menu
- [Vì sao Spring MVC tồn tại?](#spring-mvc-purpose)
- [Mô hình request, response, container và thread của Servlet](#servlet-request-response-model)
- [Trách nhiệm của spring-web và spring-webmvc](#spring-web-and-spring-webmvc)
- [Spring Framework Web và Spring Boot Web Auto-Configuration](#framework-vs-boot-web)
- [Ownership Spring Web và ranh giới module lân cận](#spring-web-module-boundaries)

## <a id="spring-mvc-purpose">Vì sao Spring MVC tồn tại?</a>

<details>
<summary>Xem chi tiết</summary>

Servlet API đã cho ứng dụng Java truy cập HTTP request/response, nhưng ứng dụng lớn cần nhiều hơn một servlet tự làm routing, parsing, validation, rendering và error handling. Spring MVC tồn tại để biến các concern lặp lại đó thành một request-processing pipeline có thể cấu hình và mở rộng.

Ý tưởng trung tâm là **front controller**: application code không tự quyết định bằng tay servlet nào xử lý từng business route. `DispatcherServlet` nhận request MVC rồi delegate cho các strategy chịu trách nhiệm tìm handler, gọi handler, resolve argument, binding, validation, representation conversion, view rendering và exception resolution.

```text
HTTP request
→ routing/infrastructure của framework
→ application handler
→ response processing của framework
→ HTTP response
```

Giá trị của Spring MVC là các policy này nhất quán giữa nhiều endpoint và có thể mở rộng độc lập. Mục tiêu không phải "che giấu HTTP", mà là diễn đạt hành vi HTTP của ứng dụng mà không viết lại orchestration giống nhau trong mọi controller.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="servlet-request-response-model">Mô hình request, response, container và thread của Servlet</a>

<details>
<summary>Xem chi tiết</summary>

Spring MVC chạy trên Servlet stack nên Servlet container vẫn sở hữu vòng đời server request mức thấp. Container nhận connection, tạo `HttpServletRequest`/`HttpServletResponse`, chọn servlet/filter chain và thông thường cấp một thread để xử lý dispatch.

Spring MVC bắt đầu **bên trong** lifecycle đó. `DispatcherServlet` nhận request sau container-level filtering rồi phối hợp các component của Framework. Với xử lý synchronous bình thường, MVC handler chạy trên request-processing thread cho đến khi trả kết quả và response hoàn tất.

Baseline này quan trọng khi phân tích blocking work. Controller gọi một operation blocking chậm sẽ giữ request thread. MVC async có thể nhả thread ban đầu và resume về sau, nhưng đó vẫn là extension của Servlet async processing chứ không phải một network runtime khác.

```text
Servlet container
→ request/response object, servlet dispatch, filter chain, async capability

Spring MVC
→ handler mapping/invocation, binding, rendering, error, MVC extension
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-web-and-spring-webmvc">Trách nhiệm của spring-web và spring-webmvc</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework tách hạ tầng web dùng chung khỏi MVC framework dành riêng cho Servlet.

`spring-web` chứa các abstraction web/HTTP dùng rộng hơn: HTTP header/status/media type, message converter, client-side HTTP support, web binding infrastructure, multipart abstraction, URI utility và các facility dùng chung giữa nhiều web stack.

`spring-webmvc` xây Servlet MVC programming model trên các nền tảng đó. Nó chứa `DispatcherServlet`, MVC handler mapping/adapter, annotated-controller infrastructure, view resolution, MVC interceptor, functional Servlet endpoint và MVC configuration support.

Ranh giới này giải thích vì sao `HttpMessageConverter` hữu ích cho cả MVC và synchronous client, trong khi `HandlerInterceptor` là một phần cụ thể của server pipeline MVC.

```text
HTTP/web support dùng chung → spring-web
Servlet MVC orchestration   → spring-webmvc
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="framework-vs-boot-web">Spring Framework Web và Spring Boot Web Auto-Configuration</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework định nghĩa contract và infrastructure của MVC. Spring Boot quyết định những thành phần nào nên được tạo và cấu hình tự động cho một application.

Ở tầng Framework, `DispatcherServlet`, `@EnableWebMvc`, `WebMvcConfigurer`, `HttpMessageConverter`, `ViewResolver` và handler mapping đều có contract rõ. Ứng dụng chỉ dùng Framework phải tự đăng ký servlet và cấu hình application context phù hợp.

Spring Boot có thể nhận biết web application, cấu hình embedded Servlet container, đăng ký/configure MVC infrastructure, áp dụng các property như `spring.mvc.*` và cung cấp default khi application không override.

```text
Spring Framework
→ capability và extension contract

Spring Boot
→ conditional auto-configuration và application convention
```

Khi học MVC mechanics, nên bắt đầu từ Framework contract. Boot convenience giải thích vì sao application cần ít cấu hình hơn, nhưng không thay thế mental model MVC bên dưới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-web-module-boundaries">Ownership Spring Web và ranh giới module lân cận</a>

<details>
<summary>Xem chi tiết</summary>

Module này sở hữu Spring MVC trên Servlet stack và mechanics chung/đồng bộ của Spring HTTP client. Một số topic lân cận chủ động có owner khác.

- nền tảng `DataBinder`, `Validator`, conversion và formatting → Validation/Data Binding;
- WebFlux, Reactor, reactive codec và deep mechanics của `WebClient` → Spring Reactive;
- WebSocket, STOMP và Spring Messaging → Messaging;
- authentication, authorization, CSRF và `SecurityFilterChain` → Spring Security;
- `MockMvc`, TestContext và framework-level web testing → Spring Testing;
- Boot MVC auto-configuration và embedded-server convention → Spring Boot;
- kiến trúc service-to-service HTTP, client selection và resilience policy → Integration HTTP.

Các boundary này giữ learning story nhất quán. Module web có thể nhắc concept lân cận tại integration point của MVC nhưng không lặp lại toàn bộ mental model của owner khác.

</details>

- [Quay lại đầu trang](#back-to-top)
