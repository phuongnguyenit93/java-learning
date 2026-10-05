<a id="back-to-top"></a>

# Runtime reactive HTTP và mô hình dispatch

## Menu
- [Lớp reactive HTTP server adapter](#webflux-reactive-http-adapter)
- [Contract HttpHandler và WebHandler](#webflux-http-handler-web-handler)
- [ServerWebExchange và request-response exchange](#webflux-server-web-exchange)
- [WebHandler processing chain](#webflux-web-handler-chain)
- [DispatcherHandler như central dispatcher](#webflux-dispatcher-handler)
- [HandlerMapping, HandlerAdapter và HandlerResultHandler](#webflux-dispatch-strategies)
- [Các nhánh xử lý response body, ServerResponse và view](#webflux-result-handling-paths)
- [ReactiveAdapterRegistry và các asynchronous type được hỗ trợ](#webflux-reactive-adapter-registry)
- [Runtime Reactor Netty và Servlet container](#webflux-server-runtime-options)
- [Threading theo từng runtime và giả định event-loop](#webflux-runtime-threading)
- [Nền tảng cấu hình WebFlux](#webflux-config-foundation)

## <a id="webflux-reactive-http-adapter">Lớp reactive HTTP server adapter</a>

<details>
<summary>Xem chi tiết</summary>

Spring tách web programming model khỏi API cụ thể của HTTP server. Ở boundary thấp nhất phía server, `HttpHandler` nhận `ServerHttpRequest` và `ServerHttpResponse` của Spring rồi trả về `Mono<Void>` để biểu diễn completion. Adapter theo từng server sẽ chuyển event từ Reactor Netty hoặc Servlet non-blocking I/O vào contract chung này.

Nhờ lớp adapter đó, phần WebFlux phía trên không cần một implementation controller riêng cho Netty, Tomcat hay Jetty. Server vẫn chịu trách nhiệm socket và I/O readiness; Spring chuyển các signal đó thành stream request/response reactive. Phía trên boundary này, cùng một hạ tầng codec, `WebHandler` chain và endpoint model có thể chạy trên nhiều runtime.

`HttpHandler` được thiết kế rất nhỏ. Nó là portability boundary chứ không phải nơi application thường tự cài session, controller dispatch, exception advice hay content negotiation. Các xử lý cấp web đó được bổ sung ở tầng `WebHandler` và WebFlux configuration.

Phân biệt đúng hai tầng giúp debug dễ hơn: lỗi server adapter liên quan transport integration, còn mapping, controller, codec và rendering nằm ở phần WebFlux cao hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-http-handler-web-handler">Contract HttpHandler và WebHandler</a>

<details>
<summary>Xem chi tiết</summary>

`HttpHandler` và `WebHandler` là hai mức abstraction trong cùng server stack.

`HttpHandler` là contract tối thiểu hướng về transport. Nó làm việc trực tiếp với `ServerHttpRequest`, `ServerHttpResponse` và trả `Mono<Void>` để báo completion. Server adapter có thể host contract này mà không cần biết controller là gì.

`WebHandler` là contract web-level tổng quát hơn. Method của nó nhận `ServerWebExchange`, nơi gom request, response, attribute, session, locale/principal, form/multipart và conditional-request support. Annotated controller lẫn functional endpoint đều được xây trên tầng này.

`WebHttpHandlerBuilder` nối hai tầng lại với nhau. Nó lắp target `WebHandler`, bọc quanh target bằng `WebFilter` và `WebExceptionHandler`, rồi adapt toàn bộ chain thành `HttpHandler` thông qua `HttpWebHandlerAdapter`. Trong ứng dụng annotated thông thường, target `WebHandler` chính là `DispatcherHandler`.

Khi chọn extension point, hãy dùng low-level contract cho server adaptation hoặc setup thật tối giản; còn logic web thông thường nên đi qua hạ tầng `WebHandler`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-server-web-exchange">ServerWebExchange và request-response exchange</a>

<details>
<summary>Xem chi tiết</summary>

`ServerWebExchange` là container theo từng request đi xuyên qua `WebHandler` chain. Nó giữ `ServerHttpRequest` và `ServerHttpResponse` hiện tại, đồng thời cung cấp web-level state gắn với exchange thay vì gắn với một thread cụ thể.

Ngoài request và response, exchange còn cung cấp request attribute, `WebSession`, locale context, principal, form data, multipart data và các method `checkNotModified` cho `ETag`/`Last-Modified`. Một số giá trị bản thân cũng bất đồng bộ, vì vậy session hoặc principal có thể được truy cập qua reactive type.

```java
public Mono<Void> handle(ServerWebExchange exchange) {
    String requestId = exchange.getRequest().getId();
    exchange.getAttributes().put("requestId", requestId);
    exchange.getResponse().getHeaders().add("X-Request-Id", requestId);
    return exchange.getResponse().setComplete();
}
```

Exchange có một số trạng thái mutable có kiểm soát vì response và attribute phải thay đổi trong quá trình xử lý. Tuy vậy, không nên dùng nó như kho global tùy ý. Dữ liệu theo request nên nằm ở exchange attribute; dữ liệu cần đi theo subscriber context qua reactive boundary có thể dùng Reactor `Context` khi phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-web-handler-chain">WebHandler processing chain</a>

<details>
<summary>Xem chi tiết</summary>

WebFlux server pipeline được lắp quanh một target `WebHandler`. `WebHttpHandlerBuilder` có thể tự tìm các bean hạ tầng trong `ApplicationContext` hoặc nhận chúng theo cách programmatic, sau đó tạo ra `HttpHandler` để gắn vào server adapter.

Có thể hình dung luồng xử lý như sau:

```text
server adapter
    -> HttpWebHandlerAdapter
        -> WebExceptionHandler chain
            -> WebFilter chain
                -> target WebHandler
```

`WebFilter` có thể chạy logic trước và sau phần chain còn lại bằng cách compose `Mono<Void>`. `WebExceptionHandler` nằm ở phạm vi rộng hơn và có thể xử lý lỗi thoát ra từ filter hoặc target handler. Session management, codec cho form/multipart, locale resolution và forwarded-header transformation là các hạ tầng hỗ trợ khác mà builder có thể phát hiện.

Đây là chain reactive: Java method trả về chưa có nghĩa response đã xong. Request chỉ hoàn tất khi publisher đã kết thúc thành công sau toàn bộ công việc async, việc ghi response và post-processing.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-dispatcher-handler">DispatcherHandler như central dispatcher</a>

<details>
<summary>Xem chi tiết</summary>

`DispatcherHandler` là central dispatcher của WebFlux và thường là target `WebHandler` trong full Spring programming model. Nó áp dụng front-controller pattern: một component chung điều phối mapping, invocation và result processing, còn chi tiết được giao cho các strategy bean.

Khi bean này có tên `webHandler`, `WebHttpHandlerBuilder` có thể tự phát hiện và đặt nó vào filter/exception chain rộng hơn. `DispatcherHandler` cũng là `ApplicationContextAware`; trong quá trình khởi tạo nó tìm các strategy như `HandlerMapping`, `HandlerAdapter` và `HandlerResultHandler`.

Thuật toán chính có thể rút gọn thành:

```text
ServerWebExchange
    -> HandlerMapping đầu tiên tìm được handler
    -> HandlerAdapter phù hợp gọi handler đó
    -> HandlerResult
    -> HandlerResultHandler phù hợp hoàn tất response
```

Thiết kế này cho phép annotated controller, functional route, `WebHandler` mapping trực tiếp, response body, `ResponseEntity`, `ServerResponse` và rendered view cùng tồn tại mà `DispatcherHandler` không phải chứa mọi rule của từng programming model.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-dispatch-strategies">HandlerMapping, HandlerAdapter và HandlerResultHandler</a>

<details>
<summary>Xem chi tiết</summary>

Ba nhóm strategy của dispatch trả lời ba câu hỏi tách biệt.

`HandlerMapping` trả lời **exchange này thuộc handler nào?** `RequestMappingHandlerMapping` phục vụ annotated controller, `RouterFunctionMapping` phục vụ functional route, còn các mapping khác có thể ánh xạ trực tiếp tới handler. Các mapping có thứ tự; match đầu tiên theo thứ tự được dùng.

`HandlerAdapter` trả lời **handler kiểu này được gọi như thế nào?** Một annotated `HandlerMethod`, một `HandlerFunction` và một raw `WebHandler` có cơ chế gọi khác nhau. Adapter che chi tiết đó khỏi `DispatcherHandler` và trả `HandlerResult` khi có logical return value cần xử lý.

`HandlerResultHandler` trả lời **result này hoàn tất HTTP response bằng cách nào?** Nó có thể encode response body, áp dụng status/header, ghi functional `ServerResponse` hoặc resolve và render view.

Đây là extension contract nhưng application hiếm khi cần tự viết implementation mới. Phần lớn customization phù hợp hơn với argument resolver, codec, view resolver, filter hoặc callback của WebFlux configuration.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-result-handling-paths">Các nhánh xử lý response body, ServerResponse và view</a>

<details>
<summary>Xem chi tiết</summary>

Result handling trong WebFlux không chỉ dành cho JSON REST response. Sau khi handler được gọi, logical result được bọc trong `HandlerResult` và lần lượt đưa cho các `HandlerResultHandler` theo thứ tự.

WebFlux config mặc định có những nhánh quan trọng sau:

- `ResponseEntityResultHandler` xử lý `ResponseEntity` và HTTP-entity style result, áp dụng status/header trước khi ghi body nếu có.
- `ServerResponseResultHandler` ghi `ServerResponse` của functional endpoint.
- `ResponseBodyResultHandler` xử lý method có `@ResponseBody` và class `@RestController`, chọn `HttpMessageWriter` theo media type đã negotiate.
- `ViewResolutionResultHandler` xử lý view name, `View`, model, `Map`, `Rendering` và các result phù hợp với model/view flow.

Ordering rất quan trọng vì một số handler hiểu được result shape khá rộng. Response entity và functional response được ưu tiên cao, response body theo sau, còn view resolution là nhánh fallback rất rộng ở thứ tự thấp.

Do đó return contract của endpoint quyết định nhánh tiếp theo. Một ứng dụng WebFlux có thể vừa phục vụ API vừa render HTML server-side trên cùng dispatcher mà không đánh đồng hai kiểu response này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-reactive-adapter-registry">ReactiveAdapterRegistry và các asynchronous type được hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework hỗ trợ nhiều reactive/asynchronous type hơn số loại mà WebFlux muốn hard-code trong pipeline nội bộ. `ReactiveAdapterRegistry` lưu cách chuyển các type được hỗ trợ sang hoặc từ Reactive Streams `Publisher`, cùng descriptor về cardinality như zero-value, single-value hay multi-value.

`Mono` và `Flux` là lựa chọn tự nhiên trong WebFlux, còn registry có thể adapt thêm các type từ library/asynchronous API được hỗ trợ khi dependency tương ứng có mặt. Framework dùng registry này khi resolve controller argument, return value, model attribute và các extension point khác có thể nhận giá trị bất đồng bộ.

Registry **không** biến blocking API thành reactive và cũng không chọn scheduler. Nó chỉ đổi representation mà Spring dùng để compose. Nếu bước tạo ra giá trị vẫn block thì blocking work vẫn cần execution strategy riêng.

Vì vậy cardinality của endpoint nên phản ánh dữ liệu thật. Single-value async result và multi-value stream có cách completion/response vận hành khác nhau dù cả hai đều có thể đi qua cùng registry.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-server-runtime-options">Runtime Reactor Netty và Servlet container</a>

<details>
<summary>Xem chi tiết</summary>

WebFlux chạy được cả trên runtime non-Servlet lẫn Servlet container vì chi tiết server dừng lại ở reactive HTTP adapter layer. Reactor Netty là lựa chọn non-Servlet phổ biến, và Spring cũng cung cấp HTTP-handler adapter cho Undertow. Tomcat và Jetty có thể host WebFlux thông qua Servlet non-blocking I/O rồi để Spring bridge API đó sang reactive request/response stream.

Programming model WebFlux phía trên vẫn giữ nguyên. Controller vẫn nhận WebFlux abstraction, `DispatcherHandler` vẫn điều phối strategy, message reader/writer vẫn xử lý body. Vì vậy chạy WebFlux trên Tomcat không có nghĩa application nên thao tác trực tiếp Servlet API.

Spring Framework không chịu trách nhiệm khởi động/dừng HTTP server cụ thể. Ứng dụng standalone có thể tạo `HttpHandler` rồi gắn nó vào server bằng API riêng của runtime; Spring Boot có thể tự động hóa phần đó, nhưng Boot auto-configuration không phải cơ chế của WebFlux Framework.

Runtime vẫn ảnh hưởng operation: connection handling, native transport, tên/số thread, buffer implementation, tuning và lifecycle đều phụ thuộc server được chọn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-runtime-threading">Threading theo từng runtime và giả định event-loop</a>

<details>
<summary>Xem chi tiết</summary>

Giả định ở mức application của WebFlux là không block processing thread hiện tại, nhưng thread profile cụ thể thuộc về runtime. Với Reactor Netty, một tập event-loop worker nhỏ thường xử lý rất nhiều connection. Nếu application block một worker, mọi exchange được giao cho worker đó có thể bị trì hoãn.

Servlet container cũng có thể chạy WebFlux bằng non-blocking I/O, nhưng container có thể khởi tạo nhiều server thread hơn vì nó đồng thời hỗ trợ cả Servlet blocking và Servlet non-blocking I/O. Vì vậy contract quan trọng là **không block request processing**, không phải một con số thread cố định.

`WebClient` cũng dùng connector theo event-loop style. Khi cả server và client đều dùng Reactor Netty, hai phía mặc định chia sẻ Reactor Netty event-loop resources. Chỉ khi code tạo scheduler/executor boundary rõ ràng thì công việc mới được chuyển sang pool khác; reactive pipeline không tự động đổi thread ở mọi operator.

Không nên thiết kế request state dựa trên `ThreadLocal` continuity. Một exchange có thể đi qua asynchronous boundary, còn một event-loop thread lần lượt phục vụ rất nhiều exchange. Hãy dùng request/exchange state và Reactor `Context` cho dữ liệu cần đi theo reactive flow.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-config-foundation">Nền tảng cấu hình WebFlux</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework có thể đăng ký chuẩn toàn bộ hạ tầng WebFlux bằng Java configuration. `@EnableWebFlux` import configuration tạo dispatcher strategy, codec support, xử lý argument/kết quả và các web component liên quan. Ứng dụng thường implement `WebFluxConfigurer` để tùy chỉnh từng phần thay vì tự lắp lại mọi bean hạ tầng.

`WebFluxConfigurer` có callback cho message codec, content-type resolution, formatter, validator, custom controller argument resolver, CORS, path matching, tài nguyên tĩnh và view resolution. Từ Spring Framework 6.1, nó còn có blocking-execution configuration. Khi đã cấu hình executor, mặc định các controller method có return type không được `ReactiveAdapterRegistry` hiện tại nhận diện sẽ được xem là blocking; `BlockingExecutionConfigurer` cho phép thay heuristic đó bằng custom controller-method predicate. Các method được chọn sẽ chạy trên executor đã cấu hình, nhờ đó blocking work được cô lập nhưng bản thân thao tác không trở thành non-blocking.

```java
@Configuration
@EnableWebFlux
class WebConfig implements WebFluxConfigurer {
    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        configurer.defaultCodecs().maxInMemorySize(512 * 1024);
    }
}
```

Đây là Framework configuration. Spring Boot xây thêm auto-configuration và external property phía trên, nhưng hành vi của Boot không nên bị nhầm với quy tắc của `DispatcherHandler` hoặc `WebFluxConfigurer`. Khi debug, cần xác định cấu hình đến từ core WebFlux, server runtime hay Boot integration.

</details>

- [Quay lại đầu trang](#back-to-top)
