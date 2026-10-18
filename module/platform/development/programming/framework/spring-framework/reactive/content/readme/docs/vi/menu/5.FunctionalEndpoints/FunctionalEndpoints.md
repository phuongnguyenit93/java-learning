<a id="back-to-top"></a>

# Functional Endpoint với WebFlux.fn

## Menu
- [Programming model của functional endpoint](#webflux-functional-model)
- [RouterFunction](#webflux-router-function)
- [HandlerFunction](#webflux-handler-function)
- [ServerRequest và ServerResponse](#webflux-server-request-response)
- [Route predicate](#webflux-route-predicates)
- [Compose và nest route](#webflux-route-composition)
- [HandlerFilterFunction và route filter](#webflux-route-filters)
- [Tích hợp với DispatcherHandler](#webflux-functional-dispatcher-integration)
- [Adapt trực tiếp RouterFunction thành HttpHandler](#webflux-direct-http-handler-adaptation)
- [Functional rendering và view resolution](#webflux-functional-view-rendering)
- [Chọn annotated hay functional endpoint](#webflux-annotated-vs-functional)

## <a id="webflux-functional-model">Programming model của functional endpoint</a>

<details>
<summary>Xem chi tiết</summary>

WebFlux.fn là programming model phía server theo phong cách functional của Spring WebFlux. Nó giải quyết cùng bài toán HTTP endpoint như annotated controller, nhưng route và handler được biểu diễn tường minh bằng các giá trị/hàm Java. Route quyết định **request có khớp hay không**; handler quyết định **xử lý request đã khớp như thế nào**; ServerRequest và ServerResponse tạo thành ranh giới HTTP của model này.

Điểm quan trọng là WebFlux.fn không phải một web stack riêng. Nó chạy trên cùng nền reactive HTTP, codec, hạ tầng WebHandler và server adapter với annotated WebFlux. Vì vậy mọi giả định non-blocking vẫn giữ nguyên: việc một handler trả về Mono<ServerResponse> không biến một lời gọi blocking thành non-blocking.

~~~java
@Bean
RouterFunction<ServerResponse> routes(PersonHandler handler) {
    return RouterFunctions.route()
            .GET("/people/{id}", handler::findOne)
            .POST("/people", handler::create)
            .build();
}
~~~

Cách viết này phù hợp khi team muốn nhìn thấy routing ngay trong code, thích composition hơn annotation, hoặc muốn ghép một tập endpoint nhỏ từ các function. Đổi lại, các quy ước vốn được annotation biểu diễn giờ nằm trực tiếp trong cây route, nên cách tổ chức route và đặt tên cần nhất quán khi ứng dụng lớn dần.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-router-function">RouterFunction</a>

<details>
<summary>Xem chi tiết</summary>

RouterFunction<T extends ServerResponse> biểu diễn quyết định routing. Contract cốt lõi của nó là:

~~~java
Mono<HandlerFunction<T>> route(ServerRequest request);
~~~

Khi request khớp, router phát ra một HandlerFunction; khi không khớp, Mono hoàn thành rỗng. Chi tiết này giải thích cách composition hoạt động: với nhiều router nối tiếp nhau, Spring chỉ thử router kế tiếp khi router trước không tìm thấy handler. Vì vậy thứ tự route có ý nghĩa nếu predicate bị chồng lấp.

Thông thường ứng dụng tạo RouterFunction qua builder RouterFunctions.route():

~~~java
RouterFunction<ServerResponse> api = RouterFunctions.route()
        .GET("/orders/{id}", handler::get)
        .DELETE("/orders/{id}", handler::delete)
        .build();
~~~

RouterFunction là model routing của ứng dụng, không phải HTTP server. Trong cấu hình WebFlux thông thường, RouterFunctionMapping phát hiện các router bean và đưa chúng vào luồng DispatcherHandler. Ngoài ra RouterFunction còn có thể được adapt trực tiếp thành HttpHandler ở mức thấp hơn, một đường triển khai khác được giải thích ở phần sau.

Nên thiết kế route sao cho predicate dễ phân biệt. Các route chồng lấp vẫn có thể hợp lệ về kỹ thuật, nhưng khi kết quả phụ thuộc vào “route nào khớp trước” thì việc review và bảo trì sẽ khó hơn, nhất là khi có nhiều RouterFunction bean được sắp thứ tự.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-handler-function">HandlerFunction</a>

<details>
<summary>Xem chi tiết</summary>

HandlerFunction<T extends ServerResponse> là contract thực thi endpoint của programming model functional:

~~~java
Mono<T> handle(ServerRequest request);
~~~

Handler nhận ServerRequest đã được route match và trả về publisher cuối cùng cung cấp ServerResponse. Từ request, handler có thể đọc path variable, query parameter, header, body, session, principal hoặc ServerWebExchange rồi compose với application service.

~~~java
Mono<ServerResponse> findOne(ServerRequest request) {
    String id = request.pathVariable("id");

    return service.findById(id)
            .flatMap(person -> ServerResponse.ok().bodyValue(person))
            .switchIfEmpty(ServerResponse.notFound().build());
}
~~~

Handler nên giữ vai trò HTTP boundary giống controller. Business rule và thao tác dùng lại nên nằm trong service; handler tập trung vào status, header, body encoding, request data và completion/cancellation của HTTP flow.

Một lỗi thường gặp là gọi repository hoặc SDK blocking ngay trong handler rồi bọc kết quả bằng Mono. Lời gọi blocking đã xảy ra trước khi Mono được tạo, nên thread đang chạy handler vẫn bị giữ. Nếu dependency là blocking, cần coi đó là boundary tường minh và xử lý theo chiến lược production ở các chương sau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-server-request-response">ServerRequest và ServerResponse</a>

<details>
<summary>Xem chi tiết</summary>

ServerRequest và ServerResponse cố ý khác model request/response mutable của Servlet. ServerRequest cung cấp metadata và body extraction; ServerResponse được tạo qua builder và đại diện cho cách response sẽ được ghi ra.

~~~java
Mono<ServerResponse> create(ServerRequest request) {
    return request.bodyToMono(CreatePersonRequest.class)
            .flatMap(service::create)
            .flatMap(person -> ServerResponse
                    .created(URI.create("/people/" + person.id()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(person));
}
~~~

Dùng bodyToMono khi body là một giá trị logic duy nhất; dùng bodyToFlux khi media type và protocol thực sự biểu diễn một chuỗi phần tử. Việc đọc dựa trên HttpMessageReader đã cấu hình. Khi ghi response, các phương thức như bodyValue, body(Publisher, Class) hoặc BodyInserters sử dụng HttpMessageWriter tương ứng.

ServerRequest cũng cung cấp form data, multipart data, session, principal và helper cho conditional request. Cần hiểu semantics HTTP của từng API: đọc một object hoàn chỉnh có thể phải aggregate dữ liệu, trong khi streaming body cho phép xử lý dần nếu codec và media type hỗ trợ.

Hai wrapper này vẫn dựa trên cùng ServerWebExchange của WebFlux pipeline. Functional endpoint vì thế không bỏ qua filter, session, codec hay server runtime chỉ vì API của nó theo phong cách functional.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-route-predicates">Route predicate</a>

<details>
<summary>Xem chi tiết</summary>

Route predicate mô tả điều kiện để một request được route nhận. Spring cung cấp predicate cho HTTP method, path, Content-Type, Accept, header, query parameter và các thuộc tính request khác qua RequestPredicates và các shortcut trên route builder.

~~~java
RouterFunction<ServerResponse> routes = RouterFunctions.route()
        .GET("/reports/{id}",
                RequestPredicates.accept(MediaType.APPLICATION_JSON),
                handler::jsonReport)
        .POST("/reports",
                RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                handler::createReport)
        .build();
~~~

Predicate có thể compose bằng and, or và negate. Cách này hữu ích khi nhiều route dùng chung điều kiện protocol, nhưng cũng làm thứ tự route trở nên quan trọng. Một route quá rộng đặt trước có thể “nuốt” request vốn dành cho route cụ thể phía sau.

Nên giữ predicate cho đúng nhiệm vụ routing. Authentication/authorization policy thuộc Spring Security; điều kiện nghiệp vụ thuộc application logic. Nếu nhét các policy này vào custom predicate phức tạp, sẽ khó phân biệt request thất bại vì không có route, vì bị từ chối quyền, hay vì domain rule.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-route-composition">Compose và nest route</a>

<details>
<summary>Xem chi tiết</summary>

Route composition cho phép xây một bề mặt endpoint lớn từ các RouterFunction nhỏ. Các phép and, andOther, route builder và nested route đều dựa trên nguyên tắc: khi route hiện tại không match thì router kế tiếp mới được xét.

~~~java
RouterFunction<ServerResponse> api = RouterFunctions.route()
        .path("/api", builder -> builder
                .nest(RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        nested -> nested
                                .GET("/people/{id}", handler::findOne)
                                .GET("/people", handler::findAll))
                .POST("/people", handler::create))
        .build();
~~~

Nesting đặc biệt hữu ích cho path prefix hoặc predicate dùng chung. Nó giảm lặp mà vẫn giữ cây route dễ đọc. Tuy nhiên nesting quá sâu lại khiến người đọc phải “chạy” nhiều tầng predicate trong đầu mới biết một URL cụ thể đi đâu; lúc đó tách router theo resource hoặc capability thường rõ hơn.

Khi ứng dụng DispatcherHandler có nhiều RouterFunction bean, RouterFunctionMapping phát hiện và sắp thứ tự chúng trước khi route request. Nếu route có thể chồng lấp, ordering trở thành một phần của contract endpoint. Prefix rõ ràng, ít chồng lấp thường an toàn hơn phụ thuộc vào order tinh vi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-route-filters">HandlerFilterFunction và route filter</a>

<details>
<summary>Xem chi tiết</summary>

HandlerFilterFunction<T,R> bao quanh việc gọi handler trong một RouterFunction. Nó nhận ServerRequest hiện tại và “next” handler, vì vậy có thể sửa request, chạy logic trước/sau handler, biến đổi response, map error hoặc dừng flow trước khi handler chạy.

~~~java
HandlerFilterFunction<ServerResponse, ServerResponse> correlation =
        (request, next) -> {
            String id = request.headers().firstHeader("X-Correlation-Id");
            ServerRequest updated = ServerRequest.from(request)
                    .attribute("correlationId", id == null ? "generated" : id)
                    .build();

            return next.handle(updated);
        };

RouterFunction<ServerResponse> routes =
        personRoutes(handler).filter(correlation);
~~~

Filter này chỉ áp dụng cho route tree nơi nó được gắn. Vì vậy nó phù hợp cho logic cục bộ của functional endpoint. WebFilter có scope rộng hơn: nó nằm quanh WebHandler chain và có thể bao cả annotated controller, functional endpoint, static resource và các xử lý WebFlux khác.

Nếu filter trả ServerResponse mà không gọi next.handle(...), handler phía sau bị bỏ qua có chủ đích. Khi filter đọc hoặc thay body hay chạm DataBuffer, nó cũng phải tuân thủ resource-consumption rules của WebFlux; route-local không có nghĩa là resource lifecycle được nới lỏng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-functional-dispatcher-integration">Tích hợp với DispatcherHandler</a>

<details>
<summary>Xem chi tiết</summary>

Trong cấu hình ứng dụng thông thường, functional endpoint chạy qua DispatcherHandler, dispatcher trung tâm của WebFlux. Infrastructure cung cấp ba mắt xích chính:

- RouterFunctionMapping tìm các RouterFunction<?> bean, kết hợp chúng theo order và map request thành HandlerFunction.
- HandlerFunctionAdapter cho DispatcherHandler cách invoke HandlerFunction đó.
- ServerResponseResultHandler ghi ServerResponse bằng các strategy đã cấu hình.

Nhờ đó functional route dùng chung dispatch lifecycle với các WebFlux handler khác và có thể tồn tại cùng annotated controller. Codec, CORS, view resolver và các cấu hình WebFlux khác được dùng nhất quán thay vì mỗi router tự dựng lại.

~~~java
@Configuration
@EnableWebFlux
class WebConfig {
    @Bean
    RouterFunction<ServerResponse> personRoutes(PersonHandler handler) {
        return RouterFunctions.route()
                .GET("/people/{id}", handler::findOne)
                .build();
    }
}
~~~

Router bean không tự gọi DispatcherHandler; infrastructure phát hiện nó. Khi debug, cần phân biệt route match đúng với các bước sau như adapter, codec, ordering hay result handling.

### Tài liệu tham khảo

- Spring Framework 6.1.14 API — DispatcherHandler
- Spring Framework 6.1.14 API — org.springframework.web.reactive.function.server

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-direct-http-handler-adaptation">Adapt trực tiếp RouterFunction thành HttpHandler</a>

<details>
<summary>Xem chi tiết</summary>

RouterFunctions.toHttpHandler(...) là đường mức thấp hơn để biến một functional route tree thành contract reactive HTTP tối thiểu. Spring 6.1.14 có overload dùng HandlerStrategies mặc định hoặc strategy do ứng dụng cung cấp:

~~~java
RouterFunction<ServerResponse> routes = routes(handler);
HttpHandler httpHandler = RouterFunctions.toHttpHandler(routes);
~~~

HttpHandler kết quả có thể được gắn vào adapter của server cụ thể. Đường này hữu ích khi muốn lắp ghép ứng dụng nhỏ một cách tường minh hoặc tích hợp runtime mà không cần đầy đủ cấu hình WebFlux dựa trên ApplicationContext.

Điểm kiến trúc cần nhớ: đây không phải DispatcherHandler đi tìm RouterFunction bean. Không có bước RouterFunctionMapping lookup qua application context. Route tree và HandlerStrategies được cung cấp trực tiếp cho adapter. Trong Spring 6.1.14, HandlerStrategies bao gồm message reader/writer, view resolver, WebFilter, WebExceptionHandler và locale context resolver; các lớp WebHandler/WebHttpHandlerBuilder xung quanh vẫn có thể bổ sung composition ở mức server-web khi ứng dụng cần.

Không nên chọn direct adaptation chỉ để bớt vài bean. DispatcherHandler đem lại integration point chung cho annotated và functional endpoint cùng các Framework extension point. Direct adaptation hợp lý khi kiểm soát mức thấp đó là mục tiêu thiết kế thực sự.

### Tài liệu tham khảo

- Spring Framework 6.1.14 API — RouterFunctions.toHttpHandler
- Spring Framework 6.1.14 API — HandlerStrategies

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-functional-view-rendering">Functional rendering và view resolution</a>

<details>
<summary>Xem chi tiết</summary>

Functional endpoint không chỉ dùng để trả JSON. ServerResponse có builder cho rendering: ứng dụng cung cấp tên view và model, sau đó view resolver phù hợp sẽ tạo response.

~~~java
Mono<ServerResponse> profilePage(ServerRequest request) {
    return service.findById(request.pathVariable("id"))
            .flatMap(person -> ServerResponse
                    .ok()
                    .render("profile", Map.of("person", person)));
}
~~~

Trong cấu hình dựa trên DispatcherHandler, các WebFlux view resolver đã cấu hình có thể phục vụ cả annotated controller và functional endpoint. ServerResponseResultHandler chuyển ServerResponse sang giai đoạn ghi response bằng các strategy tương ứng.

Ở đường RouterFunction-to-HttpHandler trực tiếp, rendering chỉ hoạt động khi HandlerStrategies được dùng để adapt có view resolver phù hợp. Đây là một lý do không nên gộp hai runtime path thành cùng một mental model.

Boundary WebFlux vẫn là reactive, nhưng template engine cụ thể có thể có đặc tính I/O hoặc execution riêng. Không nên suy luận rằng mọi renderer tự động non-blocking chỉ vì được gọi từ functional endpoint.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-annotated-vs-functional">Chọn annotated hay functional endpoint</a>

<details>
<summary>Xem chi tiết</summary>

Annotated controller và WebFlux.fn là hai programming model chạy trên cùng WebFlux runtime. Lựa chọn giữa chúng nên dựa vào cách team muốn biểu diễn bề mặt HTTP.

Functional endpoint làm route composition, predicate và handler invocation hiện rõ trong Java code. Nó phù hợp với team thích bộ khái niệm nhỏ, muốn ghép route từ function hoặc cần route-local filter. Annotated controller đem lại phong cách declarative quen thuộc, hệ thống argument/return-value phong phú, tích hợp với validation và cấu trúc dễ nhận ra với nhiều team Spring.

Không mô hình nào “reactive hơn” mô hình kia. Cả hai có thể giữ flow non-blocking, và cả hai đều có thể bị phá bởi dependency blocking hoặc buffering không giới hạn. Chúng cũng có thể cùng tồn tại trong một ứng dụng DispatcherHandler.

Quy tắc thực tế là ưu tiên tính nhất quán và dễ review. Trộn hai mô hình vô mục đích làm tăng số quy ước phải nhớ. Nếu có ranh giới rõ, ví dụ functional route cho một API streaming nhỏ còn annotated controller cho phần CRUD lớn, việc dùng song song có thể hợp lý.

</details>

- [Quay lại đầu trang](#back-to-top)
