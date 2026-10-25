<a id="back-to-top"></a>

# Annotated Controller trong WebFlux

## Menu
- [Programming model của annotated controller](#webflux-annotated-controller-model)
- [Request mapping và chọn handler](#webflux-request-mapping)
- [Controller method arguments](#webflux-controller-arguments)
- [Reactive request body](#webflux-reactive-request-body)
- [Boundary binding và validation trong controller](#webflux-controller-binding-validation)
- [Reactive return value và completion](#webflux-reactive-return-values)
- [ResponseEntity và streaming response](#webflux-response-entity-streaming)
- [Render model-and-view từ controller](#webflux-controller-view-rendering)
- [Xử lý exception trong phạm vi controller](#webflux-controller-error-handling)
- [Annotation tương tự nhưng runtime assumption khác](#webflux-annotation-mvc-contrast)

## <a id="webflux-annotated-controller-model">Programming model của annotated controller</a>

<details>
<summary>Xem chi tiết</summary>

Annotated controller trong WebFlux cho phép application mô tả HTTP endpoint bằng `@Controller`, `@RestController`, `@RequestMapping` và các annotation liên quan, còn framework chịu trách nhiệm mapping, resolve argument, gọi method và xử lý result. Controller không cần extends base class hay implement interface đặc biệt của Spring.

Annotation layer nằm **phía trên** dispatch architecture của WebFlux. `RequestMappingHandlerMapping` biến mapping metadata thành `HandlerMethod` được chọn; `RequestMappingHandlerAdapter` resolve argument rồi invoke method; return value sau đó đi qua `HandlerResultHandler` chain như các handler type khác.

`@RestController` về bản chất là `@Controller` kết hợp class-level `@ResponseBody`, nên return value thường được encode thẳng vào response body. `@Controller` thông thường có thể đi theo model-and-view flow; từng method vẫn có thể dùng `@ResponseBody` khi cần trả body trực tiếp.

Annotation có thể rất giống Spring MVC, nhưng implementation phía dưới dùng WebFlux contract, message reader/writer và reactive completion. Chỉ học annotation mà bỏ qua runtime model sẽ bỏ lỡ phần quan trọng nhất của WebFlux.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-request-mapping">Request mapping và chọn handler</a>

<details>
<summary>Xem chi tiết</summary>

`@RequestMapping` và các composed annotation như `@GetMapping`, `@PostMapping` mô tả điều kiện để controller method nhận một request. Điều kiện có thể gồm path pattern, HTTP method, parameter, header, request content type (`consumes`) và response media type chấp nhận được (`produces`).

`RequestMappingHandlerMapping` tìm các method đó khi khởi tạo và đánh giá condition cho từng exchange. Sau khi thu hẹp candidate theo URL và metadata request, Spring chọn mapping cụ thể nhất còn tương thích. Mapping ambiguous là lỗi cấu hình hoặc lỗi chọn handler; framework không tự chọn ngẫu nhiên một method.

```java
@RestController
@RequestMapping("/orders")
class OrderController {
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<OrderView> get(@PathVariable long id) {
        return service.find(id);
    }
}
```

Mapping chỉ quyết định **handler nào** được dùng. Decode request body và business logic xảy ra ở stage sau qua argument resolver và handler adapter. Phân biệt hai bước này giúp debug `404`/mapping problem khác với codec hoặc controller failure.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-controller-arguments">Controller method arguments</a>

<details>
<summary>Xem chi tiết</summary>

WebFlux controller method có thể yêu cầu trực tiếp các giá trị HTTP cấp cao thay vì tự parse `ServerWebExchange`. Những argument phổ biến gồm `@PathVariable`, `@RequestParam`, `@RequestHeader`, `@CookieValue`, `@RequestBody`, `HttpEntity`, `@RequestPart`, `@ModelAttribute`, `Model`, `BindingResult`, `WebSession`, `Principal`, `ServerWebExchange` và các reactive server request/response abstraction.

Spring tự áp dụng type conversion cho input dạng chuỗi như path variable, request parameter, header và cookie. Argument phức tạp chưa được resolver nào xử lý thường được xem như model attribute; simple type thường đi theo request-parameter semantics. Khi contract quan trọng, annotation tường minh giúp code dễ đọc hơn.

Reactive wrapper hữu ích với argument có bước resolve bất đồng bộ, đặc biệt request body, part, session/principal hoặc async value được hỗ trợ. Nó không phải wrapper mặc định cho mọi argument. Một path variable vốn đã có sẵn trong request không trở nên "non-blocking hơn" chỉ vì đổi thành `Mono<String>`.

Argument resolution thường hoàn tất trước khi controller method được gọi, trừ khi chính argument được khai báo như một reactive value. Khác biệt đó quyết định decoding/binding/validation error xuất hiện trước invocation hay được phát qua pipeline mà method nhận vào.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-reactive-request-body">Reactive request body</a>

<details>
<summary>Xem chi tiết</summary>

`@RequestBody` giao việc decode body cho các `HttpMessageReader` đã cấu hình. WebFlux có thể resolve một concrete value cho bounded body hoặc expose reactive type như `Mono<T>` và `Flux<T>` để decoding tham gia trực tiếp vào asynchronous flow của endpoint.

```java
@PostMapping("/events")
Mono<Void> ingest(@RequestBody Flux<Event> events) {
    return eventService.store(events);
}
```

Dùng `Flux<T>` chỉ có ý nghĩa khi reader/media type biểu diễn được sequence và application xử lý incrementally. Nếu controller lập tức gọi `collectList()`, aggregation chỉ bị chuyển từ framework xuống application code. Với một document đơn lẻ, `Mono<T>` thường phản ánh cardinality tự nhiên hơn.

`@Valid` hoặc `@Validated` có thể kích hoạt validation trên body đã decode. Với reactive wrapper, validation failure đi qua async value để method có thể compose error operator. Với body được resolve eagerly và có `Errors`/`BindingResult` ngay sau argument, method có thể tự kiểm tra validation error.

Request body là stream có owner. Không nên để filter/helper tự subscribe đọc body rồi vẫn kỳ vọng controller argument resolver đọc lại cùng byte, trừ khi một cơ chế caching rõ ràng chịu trách nhiệm việc replay đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-controller-binding-validation">Boundary binding và validation trong controller</a>

<details>
<summary>Xem chi tiết</summary>

Binding trong controller là điểm WebFlux sử dụng hạ tầng data binding dùng chung của Spring. `@ModelAttribute` có thể lấy hoặc tạo object, bind request value qua `WebDataBinder`, áp dụng conversion/formatting rồi validate. `@InitBinder` cho phép controller hoặc controller advice tùy chỉnh cách binder hoạt động.

Với object validation thông thường, `@Valid` hoặc `@Validated` trên `@ModelAttribute`, `@RequestBody` hay `@RequestPart` có thể dẫn tới `WebExchangeBindException` khi argument resolver thực hiện validation và không có `BindingResult` local để nhận lỗi.

Spring Framework 6.1 bổ sung built-in method validation cho WebFlux controller. Constraint annotation đặt trực tiếp trên parameter, hoặc constraint annotation đặt trên method để kiểm tra return value, khiến handler method trở thành đối tượng áp dụng method validation. Vi phạm được báo qua `HandlerMethodValidationException`; khi method validation đã áp dụng, nó bao quát constraint phù hợp trên parameter/return value và các nested constraint được đi vào qua `@Valid`.

`@Valid` tự nó là cascade marker chứ không phải constraint, nên nếu chỉ có `@Valid` thì argument-level validation vẫn có thể xử lý mà không kích hoạt method validation. Ngoài ra class-level `@Validated` dùng cơ chế AOP method validation cũ; muốn dùng built-in support của 6.1 thì nên bỏ class-level `@Validated` khỏi controller.

Quy tắc tổng quát của `DataBinder`, `ConversionService`, `Validator` và Bean Validation thuộc module validation-data-binding. Ở đây trọng tâm là chúng chạy ở đâu trong WebFlux request lifecycle và lỗi được chuyển thành lỗi HTTP-facing thế nào.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-reactive-return-values">Reactive return value và completion</a>

<details>
<summary>Xem chi tiết</summary>

WebFlux adapt các reactive/asynchronous return type được hỗ trợ thông qua `ReactiveAdapterRegistry`, rồi chuyển kết quả logic tới `HandlerResultHandler` phù hợp. `Mono<T>` biểu diễn kết quả bất đồng bộ có 0..1 value; `Flux<T>` biểu diễn kết quả nhiều value; `Mono<Void>` biểu diễn asynchronous completion không có value.

Completion là một phần của endpoint contract. Controller method có thể return ngay ở cấp lời gọi Java trong khi response vẫn đang xử lý. Lỗi phát ra sau đó vẫn là lỗi request-processing và có thể đi qua dispatch exception mechanism nếu response chưa committed.

Với `@ResponseBody` hoặc `@RestController`, value được encode qua `HttpMessageWriter`. Kết quả nhiều value có thể được ghi incrementally; cách flush phụ thuộc media type và writer. Với HTML controller, reactive adaptation cũng có thể cuối cùng tạo model data hoặc kết quả hướng view.

`void`/`Mono<Void>` có ý nghĩa tùy ngữ cảnh. Nó có thể cho biết method tự hoàn tất response nếu method có access trực tiếp tới response/exchange hoặc có response status rõ ràng; trong REST flow nó có thể đơn giản là không có body; trong view-oriented flow nó có thể cho phép chọn tên view mặc định. Nên chọn return type thể hiện rõ ai sở hữu response.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-response-entity-streaming">ResponseEntity và streaming response</a>

<details>
<summary>Xem chi tiết</summary>

`ResponseEntity` là return type của annotated controller khi cần điều khiển đồng thời HTTP status, header và body. Trong WebFlux, entity, body hoặc cả hai có thể được tạo bất đồng bộ.

Vị trí reactive wrapper thay đổi thời điểm các phần của response được biết:

- `ResponseEntity<Mono<T>>` hoặc `ResponseEntity<Flux<T>>`: status/header có ngay, body xuất hiện bất đồng bộ.
- `Mono<ResponseEntity<T>>`: status, header và body decision cùng được quyết định bất đồng bộ.
- `Mono<ResponseEntity<Flux<T>>>`: outer publisher quyết định status/header trước, rồi body tiếp tục dưới dạng stream.

```java
@GetMapping("/reports/{id}")
Mono<ResponseEntity<Report>> report(@PathVariable String id) {
    return service.find(id)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
}
```

Với streaming body, media type phải phản ánh đúng semantics của stream, ví dụ server-sent event hoặc streaming JSON khi phù hợp. Khi response đã committed và một phần body đã gửi, error xảy ra sau đó thường không thể thay status/body bằng error document chuẩn, nên streaming API cần error model chấp nhận khả năng partial output.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-controller-view-rendering">Render model-and-view từ controller</a>

<details>
<summary>Xem chi tiết</summary>

Annotated controller trong WebFlux có thể render server-side view chứ không chỉ ghi response body. Một `@Controller` thông thường có thể trả view name, `View`, `Rendering`, model-oriented value hoặc result phù hợp khác. `ViewResolutionResultHandler` kết hợp logical result với các `ViewResolver` đã cấu hình và model, rồi yêu cầu `View` được chọn render vào reactive HTTP response.

```java
@Controller
class CatalogPageController {
    @GetMapping("/catalog")
    Mono<String> page(Model model) {
        model.addAttribute("items", catalogService.findAll().collectList());
        return Mono.just("catalog/index");
    }
}
```

Model của WebFlux có thể chứa reactive attribute. Ngay trước khi render, reactive wrapper trong model được resolve thành actual value và model được cập nhật, nên template nhìn thấy dữ liệu đã materialize thay vì một object `Mono` chưa được xử lý.

View resolution được cấu hình ở WebFlux Framework layer và dùng chung dispatcher với REST endpoint. Method có `@ResponseBody` đi vào response-body result handler sớm hơn; controller theo view flow để logical result tiếp tục tới `ViewResolutionResultHandler`.

Template engine vẫn có execution characteristic riêng. Dùng view infrastructure của WebFlux không tự động biến template engine hoặc data source blocking thành non-blocking.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-controller-error-handling">Xử lý exception trong phạm vi controller</a>

<details>
<summary>Xem chi tiết</summary>

Annotated controller có thể khai báo `@ExceptionHandler` để xử lý lỗi từ request-handling method của chính nó. `RequestMappingHandlerAdapter` của WebFlux sở hữu cơ chế controller-method exception này và có thể biến exception match được thành một `HandlerResult` khác, sau đó result tiếp tục đi qua luồng xử lý bình thường.

```java
@ExceptionHandler(OrderNotFoundException.class)
ResponseEntity<ProblemDetail> notFound(OrderNotFoundException ex) {
    ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    detail.setDetail(ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
}
```

Exception có thể xảy ra synchronous ngay lúc invoke method hoặc xuất hiện muộn dưới dạng error signal từ async return value. WebFlux giữ controller exception handling cho lỗi phát sinh muộn thông qua dispatch-exception contract gắn với `HandlerResult`.

`@ControllerAdvice` có thể cung cấp cùng annotated mechanism cho nhiều controller, còn WebFlux exception chain rộng hơn xử lý lỗi ngoài controller invocation. Cần phân biệt phạm vi: `@ExceptionHandler` thể hiện chính sách endpoint/application; `WebExceptionHandler` bao quanh `WebHandler` chain ở mức thấp hơn và rộng hơn.

Exception handler hỗ trợ phần lớn argument/return style giống request-mapping method, nhưng request-body và model-attribute argument bị hạn chế vì body có thể đã được consume và binding phase ban đầu đã xảy ra.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-annotation-mvc-contrast">Annotation tương tự nhưng runtime assumption khác</a>

<details>
<summary>Xem chi tiết</summary>

Spring MVC và WebFlux cố ý dùng chung vocabulary annotation để application-facing model quen thuộc. Tuy nhiên cùng một method nhìn giống nhau vẫn chạy qua infrastructure khác và dưới giả định concurrency khác.

Trong WebFlux, controller invocation đi qua `DispatcherHandler`, reactive argument resolver, `HttpMessageReader`/`HttpMessageWriter` và `ServerWebExchange`. Trong MVC, flow tương ứng dựa trên `DispatcherServlet`, Servlet request/response và `HttpMessageConverter`. WebFlux chuyển đổi trực tiếp reactive return value ngay trong request-processing model; MVC cũng hỗ trợ async/reactive return type nhưng vẫn nằm trên Servlet stack.

Khác biệt thực tế lớn nhất là giả định về blocking. MVC giả định request code có thể block và dùng request-thread pool phù hợp. WebFlux giả định processing thread không nên bị block. Copy một MVC controller sang WebFlux trong khi giữ nguyên database call và remote call synchronous vẫn giữ nguyên tính chất blocking dù return type được đổi thành `Mono`.

Vì vậy annotation giống nhau là lợi ích về API ergonomics, không phải bằng chứng hai runtime tương đương. Khi chuyển code giữa hai stack, cần kiểm tra lại body handling, direct server API, filter/interceptor, blocking dependency, giả định về thread-local, streaming và error handling.

</details>

- [Quay lại đầu trang](#back-to-top)
