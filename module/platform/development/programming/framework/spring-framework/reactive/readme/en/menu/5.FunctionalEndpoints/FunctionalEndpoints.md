<a id="back-to-top"></a>

# Functional Endpoints with WebFlux.fn

## Menu
- [Functional endpoint programming model](#webflux-functional-model)
- [RouterFunction](#webflux-router-function)
- [HandlerFunction](#webflux-handler-function)
- [ServerRequest and ServerResponse](#webflux-server-request-response)
- [Route predicates](#webflux-route-predicates)
- [Route composition and nesting](#webflux-route-composition)
- [HandlerFilterFunction and route filters](#webflux-route-filters)
- [DispatcherHandler integration](#webflux-functional-dispatcher-integration)
- [Direct RouterFunction-to-HttpHandler adaptation](#webflux-direct-http-handler-adaptation)
- [Functional rendering and view resolution](#webflux-functional-view-rendering)
- [Choosing annotated or functional endpoints](#webflux-annotated-vs-functional)

## <a id="webflux-functional-model">Functional endpoint programming model</a>

<details>
<summary>Click for details</summary>

WebFlux.fn is the functional server programming model in Spring WebFlux. It solves the same HTTP endpoint problem as annotated controllers, but makes routing and request handling explicit values in Java code. A route decides **whether** a request matches; a handler decides **what** to do with a matched request; ServerRequest and ServerResponse provide the functional HTTP boundary.

The important mental model is that WebFlux.fn is not a separate web stack. It runs on the same reactive HTTP foundation, codecs, WebHandler infrastructure, and server adapters as annotated WebFlux. That means the same non-blocking assumptions still apply: a functional handler returning a Mono<ServerResponse> does not make blocking work safe merely because the method itself is functional.

~~~java
@Bean
RouterFunction<ServerResponse> routes(PersonHandler handler) {
    return RouterFunctions.route()
            .GET("/people/{id}", handler::findOne)
            .POST("/people", handler::create)
            .build();
}
~~~

This style is useful when a team wants routing to be visible as normal code, prefers composition over annotations, or wants a small endpoint surface that can be assembled from functions. The trade-off is that conventions normally expressed through annotations are now explicit in the route tree, so route organization and naming discipline become more important as the application grows.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-router-function">RouterFunction</a>

<details>
<summary>Click for details</summary>

RouterFunction<T extends ServerResponse> represents the routing decision. Its core method is:

~~~java
Mono<HandlerFunction<T>> route(ServerRequest request);
~~~

A matching router emits a HandlerFunction; a non-matching router completes empty. This detail explains composition: when several router functions are combined, Spring can try the next router only when the previous one produces no handler. Route order therefore affects behavior when predicates overlap.

Applications normally create router functions with the RouterFunctions.route() builder:

~~~java
RouterFunction<ServerResponse> api = RouterFunctions.route()
        .GET("/orders/{id}", handler::get)
        .DELETE("/orders/{id}", handler::delete)
        .build();
~~~

RouterFunction is an application routing model, not the network server itself. In the usual WebFlux configuration it is discovered by RouterFunctionMapping and participates in DispatcherHandler. It can also be adapted directly to an HttpHandler, which is a lower-level deployment path covered later in this chapter.

Prefer route trees whose predicates are mutually understandable. Technically valid overlapping routes can make “first match wins” behavior difficult to review, especially when multiple RouterFunction beans are ordered and combined.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-handler-function">HandlerFunction</a>

<details>
<summary>Click for details</summary>

HandlerFunction<T extends ServerResponse> is the functional equivalent of an endpoint invocation contract:

~~~java
Mono<T> handle(ServerRequest request);
~~~

A handler receives the already matched ServerRequest and returns a publisher that eventually supplies a ServerResponse. It can read path variables, query parameters, headers, body data, session state, and the underlying ServerWebExchange, then compose calls to application services.

~~~java
Mono<ServerResponse> findOne(ServerRequest request) {
    String id = request.pathVariable("id");

    return service.findById(id)
            .flatMap(person -> ServerResponse.ok().bodyValue(person))
            .switchIfEmpty(ServerResponse.notFound().build());
}
~~~

The handler should remain an HTTP boundary, just as an annotated controller should. Domain rules and reusable business operations still belong in application services. This keeps routing composition simple and makes the reactive HTTP code responsible for protocol concerns such as status, headers, body encoding, and cancellation.

A common mistake is to call a blocking repository or SDK directly inside the handler and assume the returned Mono fixes the problem. The call has already blocked whichever thread invoked it. If a dependency is fundamentally blocking, make that boundary explicit and apply the production guidance from the later chapters rather than hiding it inside a handler method.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-server-request-response">ServerRequest and ServerResponse</a>

<details>
<summary>Click for details</summary>

ServerRequest and ServerResponse are deliberately different from mutable Servlet request/response objects. ServerRequest exposes request metadata and body extraction operations, while ServerResponse is created through builders and returned as a value representing how the response should be written.

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

Use bodyToMono when the body represents one logical value and bodyToFlux when the media type and protocol genuinely represent a stream of elements. Both operations rely on configured HttpMessageReader instances. Writing a response similarly uses configured HttpMessageWriter instances through methods such as bodyValue, body(Publisher, Class), or BodyInserters.

ServerRequest also exposes form data, multipart data, session access, principal access, and conditional-request helpers. Those capabilities should be used with their HTTP semantics in mind: extracting a whole value may require aggregation, while a streaming body can preserve incremental processing.

The request and response objects are wrappers around the same ServerWebExchange used by the broader WebFlux pipeline. Functional endpoints therefore do not bypass filters, sessions, codecs, or the server runtime simply because their API surface is immutable and function-oriented.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-route-predicates">Route predicates</a>

<details>
<summary>Click for details</summary>

A request predicate expresses the conditions under which a route is eligible. Spring supplies predicates for HTTP method, path, Content-Type, Accept, headers, query parameters, and other request properties through RequestPredicates and route-builder shortcuts.

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

Predicates can be composed with and, or, and negate. Composition is useful when a group of routes shares protocol constraints, but it also makes ordering significant. If an early route matches too broadly, a more specific later route may never be considered.

Keep routing predicates focused on routing facts. Authentication and authorization policy belong to Spring Security, and business eligibility belongs in application logic. Putting policy into complicated custom predicates makes it harder to see whether a request failed because no route matched, because access was denied, or because domain validation rejected the operation.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-route-composition">Route composition and nesting</a>

<details>
<summary>Click for details</summary>

Route composition lets a large endpoint surface be built from small router functions. and, andOther, builder methods, and nested routing all preserve the core behavior that the next route is considered when the previous one does not match.

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

Nesting is especially useful for shared path prefixes and predicates. It reduces repetition while keeping the resulting route tree inspectable. It is still worth resisting deeply nested builders: once understanding a concrete path requires mentally evaluating several predicate layers, splitting the router by resource or capability is usually easier to maintain.

When multiple RouterFunction beans are used in a DispatcherHandler application, RouterFunctionMapping detects and orders them before routing. Treat ordering as part of the endpoint contract when routes can overlap. Stable, non-overlapping prefixes are usually safer than relying on subtle order dependencies.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-route-filters">HandlerFilterFunction and route filters</a>

<details>
<summary>Click for details</summary>

HandlerFilterFunction<T,R> decorates handler invocation for routes in a RouterFunction. It receives the current ServerRequest and the next handler, so it can modify the request, perform work before or after the handler, transform a response, map an error, or short-circuit the invocation.

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

This filter is scoped to the router function on which it is installed. That makes it suitable for functional-endpoint concerns that should apply only to one route tree. It is different from a WebFilter, which participates in the broader WebHandler chain and can surround annotated controllers, functional endpoints, static-resource handling, and other WebFlux processing.

Be explicit about short-circuit behavior. Returning a ServerResponse without next.handle(...) means the handler is intentionally skipped. When a filter touches a response body or other pooled data, it must also respect the same resource-consumption rules as the rest of WebFlux.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-functional-dispatcher-integration">DispatcherHandler integration</a>

<details>
<summary>Click for details</summary>

In the usual application setup, functional endpoints run through DispatcherHandler, the central WebFlux dispatcher. WebFlux configuration contributes three important pieces for this programming model:

- RouterFunctionMapping finds RouterFunction<?> beans, combines them in order, and maps a request to a HandlerFunction.
- HandlerFunctionAdapter lets DispatcherHandler invoke that handler.
- ServerResponseResultHandler writes the resulting ServerResponse with the configured strategies.

That integration means functional routes share the same dispatch lifecycle as other WebFlux handlers and can coexist with annotated controllers. Codecs, CORS configuration, view resolvers, and other WebFlux configuration can therefore apply consistently rather than being rebuilt for each router.

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

The bean itself does not call DispatcherHandler; the WebFlux infrastructure discovers it. This distinction matters when debugging: a route may be correct while configuration, ordering, codecs, or a result handler determines the final behavior.

### References

- Spring Framework 6.1.14 API — DispatcherHandler
- Spring Framework 6.1.14 API — org.springframework.web.reactive.function.server

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-direct-http-handler-adaptation">Direct RouterFunction-to-HttpHandler adaptation</a>

<details>
<summary>Click for details</summary>

RouterFunctions.toHttpHandler(...) is the lower-level way to turn a functional route tree into the minimal reactive HTTP contract. Spring 6.1.14 exposes overloads that use default HandlerStrategies or caller-supplied strategies:

~~~java
RouterFunction<ServerResponse> routes = routes(handler);
HttpHandler httpHandler = RouterFunctions.toHttpHandler(routes);
~~~

The returned HttpHandler can be installed through a server-specific adapter. This path is useful when assembling a small application explicitly or integrating with a runtime without building a full ApplicationContext-driven WebFlux configuration.

The architectural consequence is important: this direct adaptation is not DispatcherHandler discovering router beans. There is no RouterFunctionMapping lookup through the application context. The route tree and its HandlerStrategies are supplied directly to the adapter. In Spring 6.1.14, HandlerStrategies carries message readers, message writers, view resolvers, WebFilters, WebExceptionHandlers, and the locale-context resolver; surrounding WebHandler/WebHttpHandlerBuilder composition can still add server-web behavior when needed.

Do not choose direct adaptation merely to remove a few framework beans. The DispatcherHandler path gives applications a common integration point for annotated and functional endpoints plus Framework configuration extension points. Direct adaptation is most appropriate when that lower-level control is itself a design goal.

### References

- Spring Framework 6.1.14 API — RouterFunctions.toHttpHandler
- Spring Framework 6.1.14 API — HandlerStrategies

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-functional-view-rendering">Functional rendering and view resolution</a>

<details>
<summary>Click for details</summary>

Functional endpoints can render views as well as write JSON or other response bodies. ServerResponse provides rendering builders that name a view and supply a model:

~~~java
Mono<ServerResponse> profilePage(ServerRequest request) {
    return service.findById(request.pathVariable("id"))
            .flatMap(person -> ServerResponse
                    .ok()
                    .render("profile", Map.of("person", person)));
}
~~~

In a DispatcherHandler-based configuration, the configured WebFlux view resolvers are available to both annotated controllers and functional endpoints. ServerResponseResultHandler ultimately asks the response to write itself with the configured strategies, including view resolution.

In the direct RouterFunction-to-HttpHandler path, view rendering still works only if the HandlerStrategies supplied to the adapter contain suitable view resolvers. This is another example of why the two runtime paths should not be mentally collapsed into one.

Rendering remains reactive at the WebFlux boundary, but a specific template engine may have its own execution and resource behavior. Treat template-engine characteristics as a dependency concern rather than assuming every renderer is automatically non-blocking.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-annotated-vs-functional">Choosing annotated or functional endpoints</a>

<details>
<summary>Click for details</summary>

Annotated controllers and WebFlux.fn are two programming models over the same WebFlux runtime. Choose between them based on how the application should express its HTTP surface.

Functional endpoints make route composition, predicates, and handler invocation explicit in code. They work well for teams that prefer a small functional vocabulary, want routing to be assembled from values, or need route-local filters. Annotated controllers provide Spring's familiar declarative style, rich method-argument and return-value conventions, validation integration, and a structure many teams recognize immediately.

Neither model is inherently more reactive. Both can preserve a non-blocking flow, and both can be damaged by hidden blocking dependencies or unbounded buffering. Both can also coexist in one DispatcherHandler application when that improves clarity.

A practical decision rule is to optimize for consistency and reviewability. Mixing models without a reason increases the number of conventions maintainers must understand. Mixing them with a clear boundary—for example, functional routes for a small streaming API and annotated controllers for a larger CRUD surface—can be reasonable when the division stays obvious.

</details>

- [Back to top](#back-to-top)
