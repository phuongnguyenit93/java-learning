<a id="back-to-top"></a>

# Tổng hợp Spring Web Servlet Stack và các ranh giới

## Menu
- [Spring MVC request end-to-end](#mvc-request-end-to-end)
- [Annotated và Functional Endpoint](#annotated-vs-functional-endpoints)
- [Xử lý Servlet đồng bộ và bất đồng bộ](#synchronous-vs-async-servlet-processing)
- [Vị trí Filter, Interceptor và Advice](#filter-interceptor-and-advice-placement)
- [Ranh giới Spring MVC và WebFlux](#mvc-vs-webflux-boundary)
- [MVC inbound và HTTP client outbound](#inbound-vs-outbound-http-mechanics)
- [Handoff sang các module lân cận](#neighboring-module-handoffs)
- [Mental model cuối cùng của Spring Web](#spring-web-final-mental-model)

## <a id="mvc-request-end-to-end">Spring MVC request end-to-end</a>

<details>
<summary>Xem chi tiết</summary>

Một Spring MVC request hoàn chỉnh đi qua nhiều layer, mỗi layer có trách nhiệm riêng:

```text
Servlet container
→ filter
→ DispatcherServlet
→ HandlerMapping
→ HandlerExecutionChain / interceptor
→ HandlerAdapter
→ argument resolution + binding/validation
→ controller hoặc functional handler
→ return-value processing
→ message conversion hoặc view rendering
→ exception resolution khi cần
→ response
```

Mental model này đặc biệt hữu ích khi debug. 404 có thể nghĩa là không HandlerMapping nào match; 400 có thể xuất phát từ binding/validation trước khi controller logic chạy; 406/415 thường liên quan representation negotiation hoặc converter; 500 có thể đi qua exception-resolver chain.

Không nên debug mọi lỗi web bên trong controller. Trước tiên hãy xác định lifecycle stage nào sở hữu behavior đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="annotated-vs-functional-endpoints">Annotated và Functional Endpoint</a>

<details>
<summary>Xem chi tiết</summary>

Annotated controller và WebMvc.fn là hai programming model trên cùng hạ tầng Spring MVC Servlet stack. Khác biệt chính là cách application biểu diễn routing và handler contract.

Annotated controller tích hợp tự nhiên với annotation, method argument resolver, binding, validation và controller advice quen thuộc. Functional endpoint làm routing và composition của handler explicit hơn bằng Java value/function.

Không style nào mặc định nhanh hơn, sạch hơn hay "hiện đại hơn". Chọn theo convention của team, nhu cầu composition, testability và hình dạng ứng dụng. Cả hai vẫn dùng MVC request/response infrastructure và có thể cùng tồn tại.

Không nên định nghĩa cùng một route ở cả hai model; khi đó mapping order/precedence vô tình trở thành một phần của behavior ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronous-vs-async-servlet-processing">Xử lý Servlet đồng bộ và bất đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

Spring MVC thông thường xử lý synchronous từ góc nhìn controller: Servlet request thread giữ trách nhiệm cho tới khi handler hoàn tất và response được tạo. Async MVC cho phép request thread ban đầu rời đi trong khi completion xảy ra về sau.

Các single-result async type như `Callable`, `DeferredResult` và `WebAsyncTask` cuối cùng quay lại MVC processing qua async dispatch. Các streaming type như `ResponseBodyEmitter`, `SseEmitter` và `StreamingResponseBody` giữ response mở trong khi dữ liệu được phát ra.

Async MVC hữu ích khi request phải chờ task khác hoặc cần tạo incremental output, nhưng nó bổ sung concern về executor capacity, timeout, client disconnect, error redispatch và context propagation.

Đây vẫn là Servlet-stack MVC. Adapt reactive return value không biến Servlet response write blocking thành pipeline WebFlux non-blocking hoàn toàn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="filter-interceptor-and-advice-placement">Vị trí Filter, Interceptor và Advice</a>

<details>
<summary>Xem chi tiết</summary>

Cross-cutting behavior có thể chạy ở nhiều layer:

- Servlet `Filter` bao quanh Servlet dispatch và có thể chạy trước Spring MVC;
- `HandlerInterceptor` bao quanh mapped MVC handler;
- controller advice tham gia các concern MVC như exception handling, binding initialization hoặc model contribution tùy contract.

Chọn layer dựa trên object/context mà concern cần. Nếu phải áp dụng cho mọi Servlet dispatch, kể cả ngoài MVC, filter là boundary tự nhiên. Nếu cần biết handler MVC nào đã được chọn, interceptor phù hợp hơn. Nếu thay đổi semantics của controller thì MVC advice thường hẹp và đúng hơn.

Đặt concern quá cao sẽ mất MVC context; đặt quá thấp sẽ làm logic lặp giữa controller. Security là trường hợp riêng có Spring Security filter infrastructure và không nên được xây lại bằng MVC interceptor tùy ý.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-vs-webflux-boundary">Ranh giới Spring MVC và WebFlux</a>

<details>
<summary>Xem chi tiết</summary>

Spring MVC và WebFlux giải quyết nhiều bài toán web giống nhau nhưng dựa trên runtime model khác nhau. Spring MVC xây trên Servlet API và phù hợp tự nhiên với blocking library cùng thread-per-request application code. WebFlux hướng tới reactive, non-blocking request processing và tích hợp sâu với Reactive Streams.

Async MVC không xóa khác biệt đó. MVC có thể nhả request thread và adapt reactive publisher, nhưng Servlet response write cùng phần lớn surrounding ecosystem vẫn dựa trên Servlet model.

Chọn stack theo end-to-end dependency. WebFlux controller gọi blocking persistence trực tiếp không tự tạo ra hệ thống non-blocking; MVC application cũng không cần WebFlux chỉ vì một operation chạy bất đồng bộ.

Reactor, backpressure, `WebClient`, reactive codec và WebFlux dispatch chuyên sâu thuộc Spring Reactive.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="inbound-vs-outbound-http-mechanics">MVC inbound và HTTP client outbound</a>

<details>
<summary>Xem chi tiết</summary>

Module này chứa cả HTTP mechanics **inbound** và **outbound**, nhưng hai hướng có ownership lifecycle khác nhau.

```text
Inbound
client → DispatcherServlet → application handler

Outbound
application code → RestClient / RestTemplate / HTTP Service proxy → remote server
```

Hai hướng dùng chung khái niệm header, media type, message converter, status code và URI. Tuy nhiên inbound MVC sở hữu server dispatch; outbound client sở hữu remote request construction và response extraction.

Phân biệt hai hướng giúp tránh việc dùng MVC interceptor để giải quyết retry client, hoặc cố cấu hình controller content negotiation lên `RestClient`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="neighboring-module-handoffs">Handoff sang các module lân cận</a>

<details>
<summary>Xem chi tiết</summary>

Spring Web chủ động handoff một số concern cho module lân cận:

- nền tảng `Validator`, `DataBinder`, conversion và formatting → Validation/Data Binding;
- WebFlux, Reactor, reactive codec và deep mechanics của `WebClient` → Spring Reactive;
- WebSocket, STOMP và Spring Messaging → Messaging;
- authentication, authorization, CSRF và `SecurityFilterChain` → Spring Security;
- `MockMvc`, TestContext và framework-level web testing → Spring Testing;
- MVC auto-configuration và embedded-server convention → Spring Boot;
- client selection, resilience và service-to-service HTTP architecture → Integration HTTP.

Đây không phải content bị thiếu. Handoff giữ một primary owner cho mỗi mental model để learner không phải học nhiều phiên bản mâu thuẫn của cùng khái niệm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-web-final-mental-model">Mental model cuối cùng của Spring Web</a>

<details>
<summary>Xem chi tiết</summary>

Mental model cuối cùng của Spring Web là các pipeline phối hợp với nhau, không phải một túi annotation:

```text
SERVER SIDE
Servlet container
→ DispatcherServlet
→ mapping / invocation
→ binding / validation
→ representation / view
→ error handling / async lifecycle

CLIENT SIDE
Java call
→ RestClient / RestTemplate / HTTP Service proxy
→ request factory + converter
→ remote HTTP exchange
→ status/error handling + body conversion
```

Configuration cung cấp policy cho pipeline; extension point chỉ tham gia ở nơi contract của nó cho phép.

Khi thiết kế hoặc debug một feature, hãy hỏi:

1. Đây là inbound server processing hay outbound client processing?
2. Lifecycle stage nào sở hữu behavior?
3. Concern thuộc Spring Web hay module lân cận?

Ba câu hỏi này thường dẫn đến đúng API và giúp tránh việc infrastructure logic rò rỉ vào controller hoặc business service.

</details>

- [Quay lại đầu trang](#back-to-top)
