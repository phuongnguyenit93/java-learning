<a id="back-to-top"></a>

# Annotated Reactive Controllers

## Menu
- [Annotated controller programming model](#webflux-annotated-controller-model)
- [Request mapping and handler selection](#webflux-request-mapping)
- [Controller method arguments](#webflux-controller-arguments)
- [Reactive request bodies](#webflux-reactive-request-body)
- [Controller binding and validation boundary](#webflux-controller-binding-validation)
- [Reactive return values and completion](#webflux-reactive-return-values)
- [ResponseEntity and streaming responses](#webflux-response-entity-streaming)
- [Model-and-view rendering from controllers](#webflux-controller-view-rendering)
- [Controller-scoped exception handling](#webflux-controller-error-handling)
- [Similar annotations, different runtime assumptions](#webflux-annotation-mvc-contrast)

## <a id="webflux-annotated-controller-model">Annotated controller programming model</a>

<details>
<summary>Click for details</summary>

Annotated WebFlux controllers let application code describe HTTP endpoints with `@Controller`, `@RestController`, `@RequestMapping`, and related annotations while the framework handles mapping, argument resolution, invocation, and result processing. Controller classes do not need to extend a Spring base class or implement a special interface.

The annotation layer sits **above** the WebFlux dispatch architecture. `RequestMappingHandlerMapping` turns mapping metadata into a selected `HandlerMethod`; `RequestMappingHandlerAdapter` resolves arguments and invokes that method; then the returned value goes through the normal `HandlerResultHandler` chain.

`@RestController` is effectively `@Controller` plus class-level `@ResponseBody`, so returned application values are normally encoded to the response body. A plain `@Controller` can instead participate in model-and-view rendering, while individual methods can opt into `@ResponseBody` when needed.

The annotations may look similar to Spring MVC, but their implementation uses WebFlux contracts, message readers/writers, and reactive completion. Learning the annotations without the runtime model would miss the most important WebFlux behavior.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-request-mapping">Request mapping and handler selection</a>

<details>
<summary>Click for details</summary>

`@RequestMapping` and composed variants such as `@GetMapping` or `@PostMapping` define the conditions under which a controller method can handle a request. Conditions can include path patterns, HTTP methods, parameters, headers, request content type (`consumes`), and acceptable response media types (`produces`).

`RequestMappingHandlerMapping` discovers those methods and evaluates their conditions for each exchange. A mapping first narrows the candidate set by URL and other request properties; Spring then chooses the most specific compatible mapping. Ambiguous mappings are configuration or runtime mapping errors rather than a reason for Spring to pick an arbitrary method.

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

Mapping only chooses a handler. It does not decode the request body or execute business logic; those steps happen later through argument resolvers and the handler adapter. Keeping that separation clear helps diagnose a `404`/mapping problem differently from a codec or controller failure.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-controller-arguments">Controller method arguments</a>

<details>
<summary>Click for details</summary>

WebFlux controller methods can request high-level HTTP values instead of parsing `ServerWebExchange` manually. Common arguments include `@PathVariable`, `@RequestParam`, `@RequestHeader`, `@CookieValue`, `@RequestBody`, `HttpEntity`, `@RequestPart`, `@ModelAttribute`, `Model`, `BindingResult`, `WebSession`, `Principal`, `ServerWebExchange`, and the reactive server request/response abstractions.

Spring applies conversion to string-based inputs such as path variables, request parameters, headers, and cookies. Complex unmatched arguments are generally treated as model attributes, while simple unmatched values are generally treated like request parameters; explicit annotations are clearer when the contract matters.

Reactive wrappers are useful for arguments whose resolution is asynchronous, especially request bodies, parts, session/principal lookup, or other supported async values. They are not a universal replacement for ordinary method parameters. A path variable that is already available in the request does not become more non-blocking when wrapped in `Mono`.

Argument resolution happens before the controller method is invoked unless the declared argument itself intentionally carries a reactive value. That distinction determines whether decoding, binding, or validation errors surface before invocation or inside the reactive pipeline exposed to the method.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-reactive-request-body">Reactive request bodies</a>

<details>
<summary>Click for details</summary>

`@RequestBody` delegates body decoding to configured `HttpMessageReader` instances. WebFlux can resolve a concrete value for a bounded body or expose supported reactive types such as `Mono<T>` and `Flux<T>` so decoding participates in the endpoint's asynchronous flow.

```java
@PostMapping("/events")
Mono<Void> ingest(@RequestBody Flux<Event> events) {
    return eventService.store(events);
}
```

Using `Flux<T>` is meaningful only when the reader and media type can represent a sequence and the application processes it incrementally. Collecting it immediately with `collectList()` simply moves aggregation into application code. A `Mono<T>` is the natural shape for a single decoded document.

`@Valid` or `@Validated` can trigger validation of decoded body values. With a reactive wrapper, validation failures are delivered through that asynchronous value so controller code can compose error operators. With an eagerly resolved body plus an adjacent `Errors`/`BindingResult`, the method can inspect validation errors directly.

Treat the request body as a consumable stream. Do not add a filter or controller helper that independently subscribes to it and expect the normal argument resolver to read the same bytes again unless an explicit caching mechanism owns that behavior.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-controller-binding-validation">Controller binding and validation boundary</a>

<details>
<summary>Click for details</summary>

Controller binding is the WebFlux-facing use of Spring's reusable data-binding infrastructure. `@ModelAttribute` can obtain or create an object, bind request values through `WebDataBinder`, apply conversion/formatting, and then validate it. `@InitBinder` methods can customize binder behavior for a controller or controller advice.

For ordinary object validation, `@Valid` or `@Validated` on `@ModelAttribute`, `@RequestBody`, or `@RequestPart` can lead to `WebExchangeBindException` when the argument resolver performs validation and no local `BindingResult` path consumes the errors.

Spring Framework 6.1 also added built-in method validation for WebFlux controller methods. Direct constraint annotations on parameters, or constraint annotations on the method for its return value, make the handler method a candidate for method validation. Violations are reported through `HandlerMethodValidationException`; once method validation applies, it covers applicable parameter/return-value constraints and nested constraints reached through `@Valid`.

`@Valid` alone is a cascade marker rather than a constraint, so by itself it does not force method validation when argument-level validation already applies. Also note that class-level `@Validated` uses the older AOP method-validation path; to use the 6.1 built-in controller support, remove class-level `@Validated` from the controller.

The generic rules of `DataBinder`, `ConversionService`, `Validator`, and Bean Validation remain owned by the validation-data-binding module. Here the key concern is when those mechanisms run in the WebFlux request lifecycle and how their failures become HTTP-facing errors.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-reactive-return-values">Reactive return values and completion</a>

<details>
<summary>Click for details</summary>

WebFlux can adapt supported reactive/asynchronous return types through `ReactiveAdapterRegistry`, then route the resulting logical value to the appropriate result handler. A `Mono<T>` represents an asynchronous zero-or-one result; a `Flux<T>` represents a multi-value result; `Mono<Void>` represents asynchronous completion without a value.

Completion is part of the endpoint contract. Returning a publisher means the controller method call itself can finish before response processing is complete. Errors emitted later are still request-processing failures and can be handled by the dispatch exception mechanism when the response is not already committed.

For `@ResponseBody` and `@RestController`, produced values are encoded with `HttpMessageWriter` instances. Multi-value results can be written incrementally, with flushing behavior determined by the media type and writer. For an HTML controller, the same reactive adaptation can eventually produce model data or a view-oriented result instead.

A `void`/`Mono<Void>` return has context-sensitive meaning. It can indicate that the method fully handled the response when it has direct response/exchange access or an explicit response status; in REST-style response-body handling it can mean no body; in view-oriented handling it may allow default view-name selection. Prefer a return type that makes ownership of the response obvious.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-response-entity-streaming">ResponseEntity and streaming responses</a>

<details>
<summary>Click for details</summary>

`ResponseEntity` is the annotated-controller return type to control HTTP status, headers, and body together. WebFlux can make either the entity itself, its body, or both asynchronous.

The placement of the reactive wrapper changes what becomes known when:

- `ResponseEntity<Mono<T>>` or `ResponseEntity<Flux<T>>`: status and headers are available immediately; the body arrives asynchronously.
- `Mono<ResponseEntity<T>>`: status, headers, and body decision all become available asynchronously.
- `Mono<ResponseEntity<Flux<T>>>`: the outer publisher decides status and headers first, then the body continues as a stream.

```java
@GetMapping("/reports/{id}")
Mono<ResponseEntity<Report>> report(@PathVariable String id) {
    return service.find(id)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
}
```

For streaming bodies, choose a media type whose semantics match the stream, such as server-sent events or a streaming JSON representation when appropriate. Once a response is committed and elements have been written, a later error cannot reliably replace the status and body with a conventional error document, so streaming APIs need an error model that accounts for partial output.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-controller-view-rendering">Model-and-view rendering from controllers</a>

<details>
<summary>Click for details</summary>

WebFlux annotated controllers can render server-side views as well as write response bodies. A plain `@Controller` can return a view name, `View`, `Rendering`, model-oriented values, or other supported results. `ViewResolutionResultHandler` combines the logical result with configured `ViewResolver` instances and the model, then asks the selected `View` to render to the reactive HTTP response.

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

WebFlux models may contain reactive attributes. Before rendering, reactive wrappers in the model are resolved to their actual values and the model is updated, so template rendering sees materialized model attributes rather than an unexplained `Mono` object.

View resolution is configured through WebFlux Framework configuration and shares the same dispatcher with REST endpoints. A response-body method bypasses view resolution because `ResponseBodyResultHandler` claims it earlier; a view-oriented controller leaves the logical result for `ViewResolutionResultHandler`.

Template engines may have their own execution or blocking characteristics. Using WebFlux view infrastructure does not automatically make a blocking template or data source non-blocking.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-controller-error-handling">Controller-scoped exception handling</a>

<details>
<summary>Click for details</summary>

An annotated controller can declare `@ExceptionHandler` methods for failures from its request-handling methods. The WebFlux `RequestMappingHandlerAdapter` owns that controller-method exception mechanism and can turn a matched exception into another `HandlerResult`, which then follows the normal result-handling path.

```java
@ExceptionHandler(OrderNotFoundException.class)
ResponseEntity<ProblemDetail> notFound(OrderNotFoundException ex) {
    ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    detail.setDetail(ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
}
```

An exception can occur synchronously while invoking the method or later as an error signal from its asynchronous result. WebFlux preserves controller exception handling for deferred failures through the dispatch exception contract associated with the `HandlerResult`.

`@ControllerAdvice` can provide the same annotated mechanism across controllers, and Spring's broader WebFlux exception chain handles failures outside controller invocation. Keep those scopes distinct: an `@ExceptionHandler` is application endpoint policy, while `WebExceptionHandler` works around the wider `WebHandler` chain.

An exception handler supports most of the same argument and return-value styles as a request-mapping method, but request-body/model-attribute resolution is restricted because the body may already have been consumed and the original binding phase has already happened.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-annotation-mvc-contrast">Similar annotations, different runtime assumptions</a>

<details>
<summary>Click for details</summary>

Spring MVC and WebFlux intentionally reuse the annotation vocabulary so the application-facing model feels familiar. The same-looking method can still run through different infrastructure and under different concurrency assumptions.

In WebFlux, controller invocation is driven by `DispatcherHandler`, reactive argument resolvers, `HttpMessageReader`/`HttpMessageWriter`, and `ServerWebExchange`. In MVC, the equivalent path is built around `DispatcherServlet`, Servlet request/response objects, and `HttpMessageConverter` infrastructure. WebFlux can adapt reactive return values as part of its native processing model; MVC supports asynchronous and reactive return types while retaining a Servlet-stack execution model.

The biggest practical difference is blocking. MVC assumes request code may block and sizes request-thread pools accordingly. WebFlux assumes request-processing threads should stay non-blocking. Copying an MVC controller into WebFlux while leaving synchronous database and remote calls unchanged preserves the blocking behavior even if the return type is changed to `Mono`.

Use the shared annotations as a productivity feature, not as evidence that the runtimes are interchangeable. When moving code between stacks, re-check body handling, direct server APIs, filters/interceptors, blocking dependencies, thread-local assumptions, streaming behavior, and error handling.

</details>

- [Back to top](#back-to-top)
