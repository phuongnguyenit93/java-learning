<a id="back-to-top"></a>

# Cross-cutting processing, context và error flow

## Menu
- [WebFilter và WebFilterChain](#webflux-webfilter-chain)
- [Thứ tự filter và execution boundary](#webflux-webfilter-ordering)
- [Reactor Context trong web processing](#webflux-reactor-context)
- [Reactor Context và ThreadLocal](#webflux-context-vs-threadlocal)
- [WebSession và state trong exchange](#webflux-web-session)
- [WebExceptionHandler](#webflux-webexceptionhandler)
- [Các tầng xử lý lỗi của controller, dispatch và WebHandler](#webflux-error-handling-layers)
- [Các extension point của WebFluxConfigurer](#webflux-configurer)
- [Cấu hình CORS và path matching](#webflux-cors-path-config)
- [Cấu hình thực thi controller method blocking trong Spring 6.1](#webflux-blocking-execution)
- [Boundary tích hợp Spring Security](#webflux-security-boundary)

## <a id="webflux-webfilter-chain">WebFilter và WebFilterChain</a>

<details>
<summary>Xem chi tiết</summary>

WebFilter là contract interception của WebFlux dành cho logic cross-cutting chạy quanh WebHandler chain. Phương thức filter nhận ServerWebExchange hiện tại cùng WebFilterChain và trả Mono<Void> hoàn thành khi quá trình xử lý request kết thúc.

~~~java
class CorrelationFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest()
                .getHeaders()
                .getFirst("X-Correlation-Id");

        exchange.getAttributes().put(
                "correlationId",
                correlationId == null ? "generated" : correlationId);

        return chain.filter(exchange);
    }
}
~~~

Gọi chain.filter(exchange) chuyển quyền xử lý cho các filter còn lại và cuối cùng là WebHandler đích. Filter cũng có thể short-circuit bằng cách tự ghi response rồi trả publisher completion mà không gọi chain. Vì vậy WebFilter phù hợp với correlation metadata, observation, timeout policy, CORS helper hoặc điểm tích hợp security.

WebFilter không phải Servlet Filter và cũng không phải MVC HandlerInterceptor. Contract của nó là reactive, state boundary là ServerWebExchange, và completion có thể xảy ra sau các thao tác bất đồng bộ. Mọi code trong filter vì thế vẫn phải tôn trọng non-blocking và resource lifecycle của WebFlux.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-webfilter-ordering">Thứ tự filter và execution boundary</a>

<details>
<summary>Xem chi tiết</summary>

Thứ tự filter quyết định filter nào nhìn thấy request trước và filter nào nhận completion sau cùng. Ở chiều đi vào, filter A gọi B rồi B gọi WebHandler; khi publisher hoàn thành hoặc phát lỗi, control logic quay ngược qua B rồi A.

~~~text
request
  -> filter A
     -> filter B
        -> WebHandler
     <- completion/error tại B
  <- completion/error tại A
response completion
~~~

Cấu trúc “bao quanh” này quan trọng với timing, tracing, response mutation và error handling. Filter cần thêm header phải làm việc trước khi response committed; filter đo thời gian có thể quan sát completion; filter map error có thể làm thay đổi exception mà filter ở phía ngoài nhìn thấy.

Khi WebFilter bean được gom từ ApplicationContext, ordering thực tế trở thành một phần của pipeline. Nên hạn chế dependency ngầm kiểu “filter B mặc định filter A đã đặt attribute/context”. Nếu dependency đó thực sự cần thiết, order phải rõ và được verify ở integration boundary.

HandlerFilterFunction của functional endpoint có scope hẹp hơn WebFilter. Dùng WebFilter cho logic bao quanh toàn bộ WebFlux application pipeline; dùng route filter khi logic đó chỉ thuộc một functional route tree.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-reactor-context">Reactor Context trong web processing</a>

<details>
<summary>Xem chi tiết</summary>

Reactor Context là cơ chế context gắn với reactive subscription. Trong WebFlux, nó hữu ích khi metadata theo request cần đi cùng reactive composition dù execution không gắn cố định với một Java thread.

WebFilter là nơi tự nhiên để đưa dữ liệu từ HTTP request vào context:

~~~java
public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String requestId = exchange.getRequest().getId();

    return chain.filter(exchange)
            .contextWrite(context -> context.put("requestId", requestId));
}
~~~

Code downstream có thể đọc context ở thời điểm subscription:

~~~java
return Mono.deferContextual(contextView -> {
    String requestId = contextView.get("requestId");
    return service.call(requestId);
});
~~~

Context không phải một request map mutable. Giá trị được nhìn thấy theo quy tắc propagation của reactive chain. Với component đã có ServerWebExchange, exchange attributes vẫn là lựa chọn đơn giản cho dữ liệu mutable chỉ sống trong một HTTP exchange; Reactor Context phù hợp khi dữ liệu phải đi qua API chỉ nhìn thấy publisher chain.

Lý thuyết Reactor Context tổng quát thuộc module reactive programming. Điều cần giữ ở đây là mental model WebFlux: contextual data nên theo pipeline, không nên dựa vào việc request “giữ” một thread riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-context-vs-threadlocal">Reactor Context và ThreadLocal</a>

<details>
<summary>Xem chi tiết</summary>

ThreadLocal giả định rằng context hiện tại có thể suy ra từ Java thread đang chạy. Giả định này dễ sai trong reactive web vì một nhóm event-loop thread nhỏ phục vụ nhiều request và các asynchronous boundary có thể khiến signal sau chạy trên thread khác.

Reactor Context gắn dữ liệu với subscription thay vì thread. Vì thế nó phù hợp hơn cho correlation id, observation state hoặc contextual metadata cần đi xuyên qua WebFlux pipeline.

Điều này không có nghĩa mọi thư viện dùng ThreadLocal đều không thể hoạt động. Framework hoặc integration library có thể bridge thread-local state nếu chúng hỗ trợ context propagation một cách tường minh. Quy tắc quan trọng là application logic không được phụ thuộc vào giả định một request luôn chạy trên đúng một thread từ đầu đến cuối.

Nếu dữ liệu chỉ được dùng ở nơi đã có ServerWebExchange, exchange attribute thường đơn giản hơn. Dùng Reactor Context khi dữ liệu phải tiếp tục đi qua reactive API. Không nên duy trì cùng một mutable state ở cả hai nơi nếu không có owner rõ ràng, vì hai bản sao rất dễ lệch nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-web-session">WebSession và state trong exchange</a>

<details>
<summary>Xem chi tiết</summary>

WebSession là abstraction session phía server của Spring WebFlux. ServerWebExchange.getSession() và ServerRequest.session() trả session theo reactive contract, còn bản thân WebSession chứa attributes có thể tồn tại qua nhiều HTTP request nhờ WebSessionStore.

Việc lấy một WebSession mới **không tự động làm session bắt đầu**. Theo Spring 6.1.14, session được coi là started khi gọi start() hoặc khi thêm attribute. Nếu session mới chưa started, id của nó không được gửi về client và save() về cơ bản không tạo state. Framework tự gọi save trước khi response committed.

~~~java
return exchange.getSession()
        .flatMap(session -> {
            session.getAttributes().put("cartId", cartId);
            return chain.filter(exchange);
        });
~~~

Session khác exchange-scoped state. exchange.getAttributes() chỉ tồn tại cho request hiện tại; WebSession attributes mang state qua nhiều request. Cả hai đều không nên trở thành nơi chứa body lớn hoặc domain aggregate mutable lâu dài.

Implementation của WebSessionStore ảnh hưởng lifecycle và I/O. Nếu dùng custom/external store, phải giữ flow non-blocking và xem serialization, expiry, invalidation, id rotation là các vấn đề vận hành thực sự.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-webexceptionhandler">WebExceptionHandler</a>

<details>
<summary>Xem chi tiết</summary>

WebExceptionHandler là contract xử lý lỗi ở lớp bao quanh WebHandler. Nó nhận ServerWebExchange cùng Throwable và trả Mono<Void>.

Completion semantics của contract này rất rõ: Mono hoàn thành bình thường nghĩa là exception đã được xử lý; trả lại error signal nghĩa là lỗi vẫn chưa được handle và có thể tiếp tục qua exception-handler chain.

~~~java
class ProblemHandler implements WebExceptionHandler {
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (!(ex instanceof DomainUnavailableException)) {
            return Mono.error(ex);
        }

        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return exchange.getResponse().setComplete();
    }
}
~~~

Đây là tầng xử lý lỗi mức thấp của reactive server web. Nó phù hợp cho lỗi thoát ra khỏi handler/controller hoặc dispatch processing. Tuy nhiên nếu response đã committed thì handler có thể không còn khả năng thay status, header hay body; chiến lược xử lý lỗi phải hiểu cả response lifecycle chứ không chỉ exception type.

Lỗi nghiệp vụ có ý nghĩa HTTP rõ ràng thường dễ map gần endpoint hơn. WebExceptionHandler nên giữ vai trò fallback hoặc chính sách dùng chung toàn hạ tầng khi lớp phía trong không đủ thông tin hoặc không xử lý được.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-error-handling-layers">Các tầng xử lý lỗi của controller, dispatch và WebHandler</a>

<details>
<summary>Xem chi tiết</summary>

WebFlux có nhiều tầng xử lý lỗi, và chọn đúng tầng giúp chính sách không bị dồn hết về một chỗ.

- HandlerFilterFunction có thể map lỗi cho một functional route tree.
- Annotated controller có cơ chế exception handling cục bộ hoặc advice-driven.
- DispatcherHandler cùng HandlerAdapter/HandlerResult processing tham gia vào lỗi phát sinh trong dispatch và xử lý kết quả.
- WebExceptionHandler bao quanh WebHandler pipeline và nhận lỗi còn lan ra lớp server-web.

Các tầng này liên quan nhưng không thay thế nhau. Controller-level mapping có ngữ cảnh của controller; route filter có phạm vi của functional route; WebExceptionHandler nhìn thấy exchange và lỗi ở lớp ngoài cùng.

Nên map lỗi ở tầng hẹp nhất còn đủ thông tin để tạo kết quả HTTP đúng, rồi giữ tầng ngoài cho fallback dùng chung. Nếu map mọi thứ ở WebExceptionHandler, endpoint semantics bị mất; nếu mỗi controller tự xử lý mọi lỗi hạ tầng, chính sách sẽ lặp lại.

Cũng phải tính đến response commitment. Với streaming response, status/header và một phần body có thể đã được gửi trước khi lỗi xảy ra; lúc đó server thường chỉ có thể kết thúc stream/connection thay vì thay toàn bộ response bằng error document mới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-configurer">Các extension point của WebFluxConfigurer</a>

<details>
<summary>Xem chi tiết</summary>

WebFluxConfigurer là callback contract của Spring Framework để tùy chỉnh ứng dụng dùng @EnableWebFlux mà vẫn giữ infrastructure WebFlux chuẩn dựa trên DispatcherHandler.

Spring 6.1.14 cung cấp extension point cho các nhóm cấu hình như:

- HTTP message reader/writer;
- formatter, validator và custom argument resolver của annotated controller;
- CORS mapping và path matching;
- content-type resolution;
- view resolver và static resource handler;
- cấu hình thực thi controller method blocking;
- WebSocketService integration hook; semantics của WebSocket handler/session và application messaging vẫn thuộc module messaging.

~~~java
@Configuration
@EnableWebFlux
class WebConfig implements WebFluxConfigurer {
    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        configurer.defaultCodecs().maxInMemorySize(512 * 1024);
    }
}
~~~

Đây là Framework configuration, không phải Spring Boot auto-configuration. Boot có thể xây thêm default và property-driven customization trên các cơ chế này, nhưng phần Boot-specific thuộc area Spring Boot.

Nên dùng callback nhỏ nhất phù hợp với phần cần chỉnh. Thay toàn bộ configuration support chỉ để đổi codec limit hoặc CORS mapping làm tăng trách nhiệm và khiến việc nâng phiên bản khó hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-cors-path-config">Cấu hình CORS và path matching</a>

<details>
<summary>Xem chi tiết</summary>

WebFluxConfigurer.addCorsMappings cấu hình CORS theo URL pattern ở mức global. Trong Spring 6.1.14, mapping này áp dụng cho annotated controller, functional endpoint và static resource. Annotated controller còn có thể dùng @CrossOrigin cục bộ; Spring kết hợp local và global configuration theo contract của Framework.

~~~java
@Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
            .allowedOrigins("https://app.example")
            .allowedMethods("GET", "POST");
}
~~~

Path matching configuration có phạm vi khác. configurePathMatching tùy chỉnh matching cho annotated controller và static resource. Functional WebFlux.fn tự biểu diễn route bằng RequestPredicate/RouterFunction, nên không được suy luận rằng mọi PathMatchConfigurer option cũng thay đổi functional route.

CORS là policy cross-origin của HTTP/browser; authentication và authorization thuộc Spring Security. Chúng thường gặp nhau trên cùng request path nên ordering và preflight rất quan trọng, nhưng vẫn là hai owner khác nhau.

Không nên dùng “allow everything” làm production default chỉ vì tiện khi phát triển. Hãy cấu hình origin, method và header thực sự cần, rồi kiểm tra preflight qua đầy đủ web/security chain.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-blocking-execution">Cấu hình thực thi controller method blocking trong Spring 6.1</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework 6.1 bổ sung BlockingExecutionConfigurer qua WebFluxConfigurer.configureBlockingExecution. Đây là bridge tường minh để **annotated controller method** mà Spring xác định là blocking có thể được gọi trên AsyncTaskExecutor đã cấu hình.

Hai chi tiết dễ bị hiểu sai:

1. Mặc định không có executor, vì vậy cơ chế offload này không tự hoạt động nếu ứng dụng không cấu hình.
2. Khi có executor nhưng không cung cấp custom predicate, predicate mặc định xem controller method có return type **không** được ReactiveAdapterRegistry nhận diện là ứng viên blocking.

~~~java
@Override
public void configureBlockingExecution(BlockingExecutionConfigurer configurer) {
    configurer.setExecutor(blockingExecutor);
    configurer.setControllerMethodPredicate(handlerMethod ->
            handlerMethod.hasMethodAnnotation(BlockingEndpoint.class));
}
~~~

Cơ chế này không làm cho công việc blocking tùy ý trong reactive pipeline trở nên an toàn. Nó không tự bọc functional HandlerFunction và không đi dọc operator chain để tìm JDBC, filesystem hay SDK blocking. Nó kiểm soát **nơi một số controller method được gọi**.

Dùng cơ chế này khi ứng dụng WebFlux có một blocking controller boundary hữu hạn và executor được sizing cho workload đó. Nếu phần lớn dependency stack đều blocking, câu hỏi nên chuyển sang lựa chọn MVC hay WebFlux thay vì tiếp tục thêm nhiều offload rule.

### Tài liệu tham khảo

- Spring Framework 6.1.14 API — BlockingExecutionConfigurer
- Spring Framework 6.1.14 API — WebFluxConfigurer.configureBlockingExecution

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-security-boundary">Boundary tích hợp Spring Security</a>

<details>
<summary>Xem chi tiết</summary>

Spring Security tích hợp tự nhiên với WebFlux ở ranh giới web filter, nhưng security model hoàn chỉnh thuộc các module Spring Security. Từ góc nhìn WebFlux, điều cần hiểu là cơ chế authentication/authorization tham gia vào reactive web chain và phải giữ giả định non-blocking của pipeline.

WebFlux cung cấp hạ tầng như WebFilter, CORS support, ServerWebExchange và principal access. Spring Security sở hữu SecurityWebFilterChain, authentication, authorization, SecurityContext, CSRF và chính sách bảo mật.

Ranh giới này giúp tránh dạy trùng mental model. Người học WebFlux cần biết security **nằm ở đâu** trong request path và vì sao filter ordering hoặc context propagation có thể ảnh hưởng integration. Chi tiết quy tắc access control, cơ chế đăng nhập, OAuth2 hay method security nên học ở Spring Security.

Khi troubleshoot endpoint, cần tách lỗi routing, lỗi CORS, security denial và lỗi ứng dụng. Chúng đều có thể biểu hiện thành một HTTP request thất bại nhưng phát sinh ở các tầng khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)
