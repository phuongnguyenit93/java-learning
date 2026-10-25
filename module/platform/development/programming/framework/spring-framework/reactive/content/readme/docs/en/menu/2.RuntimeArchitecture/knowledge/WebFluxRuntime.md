<a id="back-to-top"></a>

# Reactive HTTP Runtime and Dispatch Model

## Menu
- [Reactive HTTP server adapter layer](#webflux-reactive-http-adapter)
- [HttpHandler and WebHandler contracts](#webflux-http-handler-web-handler)
- [ServerWebExchange as the request-response exchange](#webflux-server-web-exchange)
- [WebHandler processing chain](#webflux-web-handler-chain)
- [DispatcherHandler as the central dispatcher](#webflux-dispatcher-handler)
- [HandlerMapping, HandlerAdapter, and HandlerResultHandler](#webflux-dispatch-strategies)
- [Response body, ServerResponse, and view result paths](#webflux-result-handling-paths)
- [ReactiveAdapterRegistry and supported asynchronous types](#webflux-reactive-adapter-registry)
- [Reactor Netty and Servlet-container runtime options](#webflux-server-runtime-options)
- [Runtime-specific threading and event-loop assumptions](#webflux-runtime-threading)
- [WebFlux configuration foundation](#webflux-config-foundation)

## <a id="webflux-reactive-http-adapter">Reactive HTTP server adapter layer</a>

<details>
<summary>Click for details</summary>

Spring separates the web programming model from the concrete server API. At the lowest WebFlux server boundary, an `HttpHandler` receives Spring's `ServerHttpRequest` and `ServerHttpResponse` abstractions and returns `Mono<Void>` for completion. Server-specific adapters translate Reactor Netty or Servlet non-blocking I/O events into that common contract.

This adapter layer is why the higher WebFlux stack does not need separate controller implementations for Netty, Tomcat, or Jetty. The server remains responsible for sockets and I/O readiness; Spring adapts those signals into reactive request and response streams. Above that boundary, WebFlux can use the same codecs, `WebHandler` chain, and endpoint models.

`HttpHandler` is intentionally small. It is a portability boundary rather than the place where applications normally implement sessions, controller dispatch, exception advice, or content negotiation. Those web concerns are added by the higher `WebHandler` layer and WebFlux configuration.

Keeping the layers distinct makes troubleshooting easier: server-adapter problems concern transport integration, while mapping, controller, codec, and rendering problems live higher in the WebFlux pipeline.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-http-handler-web-handler">HttpHandler and WebHandler contracts</a>

<details>
<summary>Click for details</summary>

`HttpHandler` and `WebHandler` represent two abstraction levels in the same server stack.

`HttpHandler` is the minimal transport-facing contract. It works directly with `ServerHttpRequest` and `ServerHttpResponse` and signals completion with `Mono<Void>`. A runtime adapter can host this contract without knowing anything about Spring controllers.

`WebHandler` is the general web-facing contract. Its single method accepts a `ServerWebExchange`, which combines request, response, attributes, session access, locale/principal access, form and multipart helpers, and conditional-request support. Concrete programming models such as annotated controllers and functional endpoints are built on this level.

`WebHttpHandlerBuilder` bridges the two levels. It assembles a target `WebHandler`, decorates it with `WebFilter` and `WebExceptionHandler` components, and adapts the resulting chain to an `HttpHandler` through `HttpWebHandlerAdapter`. In a typical annotated application the target `WebHandler` is `DispatcherHandler`.

The distinction is useful when choosing an extension point: use the low-level contract for server adaptation or intentionally minimal setups, and the `WebHandler` infrastructure for normal web application behavior.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-server-web-exchange">ServerWebExchange as the request-response exchange</a>

<details>
<summary>Click for details</summary>

`ServerWebExchange` is the per-request WebFlux container that travels through the `WebHandler` chain. It holds the current `ServerHttpRequest` and `ServerHttpResponse` and exposes web-level state that should follow the exchange rather than a thread.

Besides request and response access, the exchange provides request attributes, `WebSession`, locale context, principal lookup, parsed form data, multipart data, and `checkNotModified` methods for `ETag` and `Last-Modified` conditions. Some values are asynchronous, so APIs such as session or principal access can themselves return reactive types.

```java
public Mono<Void> handle(ServerWebExchange exchange) {
    String requestId = exchange.getRequest().getId();
    exchange.getAttributes().put("requestId", requestId);
    exchange.getResponse().getHeaders().add("X-Request-Id", requestId);
    return exchange.getResponse().setComplete();
}
```

The exchange is mutable in controlled ways because response state and attributes evolve during processing, but application code should avoid treating it as arbitrary global storage. Request-scoped values that must survive reactive thread switches belong in exchange attributes or, for reactive subscriber context, Reactor `Context` as appropriate.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-web-handler-chain">WebHandler processing chain</a>

<details>
<summary>Click for details</summary>

The WebFlux server pipeline is assembled around one target `WebHandler`. `WebHttpHandlerBuilder` can discover infrastructure from an `ApplicationContext` or accept it programmatically, then produces an `HttpHandler` ready for a supported server adapter.

Conceptually the request travels through this structure:

```text
server adapter
    -> HttpWebHandlerAdapter
        -> WebExceptionHandler chain
            -> WebFilter chain
                -> target WebHandler
```

`WebFilter` components can perform work before and after the rest of the chain by composing the returned `Mono<Void>`. `WebExceptionHandler` components operate at a wider level and can handle failures escaping filters or the target handler. Session management, codec configuration for form/multipart parsing, locale resolution, and forwarded-header transformation are additional facilities that can be detected by the builder.

The chain is reactive: returning from a Java method does not necessarily mean the response is finished. Completion means the composed publisher terminates successfully after all asynchronous work, response writing, and post-processing have completed.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-dispatcher-handler">DispatcherHandler as the central dispatcher</a>

<details>
<summary>Click for details</summary>

`DispatcherHandler` is WebFlux's central dispatcher and the normal target `WebHandler` for the full Spring programming model. It follows the front-controller pattern: one shared component coordinates request mapping, handler invocation, and result processing while delegating the details to strategy beans.

When declared with the bean name `webHandler`, it can be discovered by `WebHttpHandlerBuilder` and inserted into the wider filter/exception chain. `DispatcherHandler` is also `ApplicationContextAware`; on initialization it discovers WebFlux strategy beans such as `HandlerMapping`, `HandlerAdapter`, and `HandlerResultHandler`.

Its request algorithm is intentionally small:

```text
ServerWebExchange
    -> first HandlerMapping that finds a handler
    -> matching HandlerAdapter invokes that handler
    -> HandlerResult
    -> matching HandlerResultHandler completes the response
```

This design lets annotated controllers, functional routes, simple `WebHandler` mappings, response bodies, `ResponseEntity`, `ServerResponse`, and rendered views coexist without putting every programming-model rule into the dispatcher itself.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-dispatch-strategies">HandlerMapping, HandlerAdapter, and HandlerResultHandler</a>

<details>
<summary>Click for details</summary>

The three main dispatch strategy types answer three separate questions.

`HandlerMapping` asks **which handler owns this exchange?** Implementations include `RequestMappingHandlerMapping` for annotated controller methods, `RouterFunctionMapping` for functional routes, and mappings for directly registered handlers. Mappings are ordered; the first matching handler is used.

`HandlerAdapter` asks **how is this kind of handler invoked?** An annotated `HandlerMethod`, a `HandlerFunction`, and a raw `WebHandler` need different invocation mechanics. The adapter hides those details from `DispatcherHandler` and returns a `HandlerResult` when there is a logical return value to process.

`HandlerResultHandler` asks **how should this result complete the HTTP response?** It may encode a response body, apply status and headers, write a functional `ServerResponse`, or resolve and render a view.

These are extension contracts, but application code rarely needs custom implementations. Most customization belongs in the higher configuration callbacks, argument resolvers, codecs, view resolvers, filters, or endpoint code. Replacing a core strategy is appropriate only when the application truly introduces a new mapping, invocation, or result model.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-result-handling-paths">Response body, ServerResponse, and view result paths</a>

<details>
<summary>Click for details</summary>

WebFlux result handling is broader than JSON REST responses. After a handler is invoked, its logical result is wrapped in `HandlerResult` and offered to ordered `HandlerResultHandler` implementations.

The default WebFlux configuration includes several important paths:

- `ResponseEntityResultHandler` handles `ResponseEntity` and related HTTP-entity results, applying status and headers before writing a body when present.
- `ServerResponseResultHandler` writes functional-endpoint `ServerResponse` values.
- `ResponseBodyResultHandler` handles `@ResponseBody` methods and `@RestController`, selecting an `HttpMessageWriter` for the negotiated media type.
- `ViewResolutionResultHandler` handles view-oriented return values such as view names, `View`, `Model`, `Map`, `Rendering`, and otherwise eligible model attributes.

Ordering matters because several handlers can conceptually understand broad result shapes. For example, response-entity and functional-response handlers have high precedence, response-body handling follows, and view resolution is a broad fallback with very low precedence.

The practical consequence is that the return contract selected by the endpoint decides the next processing path. A WebFlux application can serve APIs and server-rendered HTML through the same dispatcher without pretending those responses are the same operation.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-reactive-adapter-registry">ReactiveAdapterRegistry and supported asynchronous types</a>

<details>
<summary>Click for details</summary>

Spring Framework accepts more asynchronous/reactive types than its internal WebFlux pipeline wants to special-case. `ReactiveAdapterRegistry` is the adaptation layer that records how supported types can be converted to and from a Reactive Streams `Publisher` together with metadata such as whether the type represents zero, one, or many values.

Reactor `Mono` and `Flux` are native choices in WebFlux, while the registry can adapt other supported libraries or asynchronous types when the corresponding support is present. Framework components use that registry when resolving controller arguments and return values, model attributes, and other extension points that accept asynchronous values.

The registry does **not** make a blocking API reactive and does not choose a scheduler. Adapting an asynchronous container only changes the representation used by Spring. If producing the value blocks, that work still needs an appropriate execution strategy.

This is also why endpoint contracts should describe cardinality honestly. A single-value asynchronous result and a multi-value stream have different completion and response-writing behavior even when both can be adapted through the same registry.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-server-runtime-options">Reactor Netty and Servlet-container runtime options</a>

<details>
<summary>Click for details</summary>

WebFlux can run on non-Servlet runtimes and on Servlet containers because the server-specific details stop at the reactive HTTP adapter layer. Reactor Netty is a common non-Servlet choice, and Spring also provides an Undertow HTTP-handler adapter. Tomcat and Jetty can host WebFlux through Servlet non-blocking I/O, with Spring bridging that API to reactive request and response streams.

The higher WebFlux programming model remains the same across those choices. Controllers still receive WebFlux abstractions, `DispatcherHandler` still coordinates strategies, and message readers/writers still process bodies. Using Tomcat for WebFlux therefore does not expose the Servlet programming model to application code.

Spring Framework itself does not own the lifecycle of the concrete HTTP server. A standalone application can assemble an `HttpHandler` and attach it to a server through server-specific APIs; Spring Boot can automate server creation and configuration, but that Boot convenience is outside the Framework mechanics taught here.

Runtime selection still matters operationally because connection handling, native transport options, thread naming, buffer implementation, tuning, and lifecycle controls come from the chosen server.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-runtime-threading">Runtime-specific threading and event-loop assumptions</a>

<details>
<summary>Click for details</summary>

WebFlux's application-level assumption is "do not block the current processing thread," but the exact thread profile belongs to the chosen runtime. On a typical Reactor Netty server, a small fixed set of event-loop workers handles many connections. Application code that blocks one of those workers delays every exchange assigned to it.

Servlet containers can also run WebFlux through non-blocking I/O, but they may create more server threads because the container supports both traditional Servlet workloads and Servlet non-blocking I/O. The important contract is therefore non-blocking processing, not a promise about an exact number or name of threads.

`WebClient` also uses event-loop style connectors. When Reactor Netty backs both server and client, they share Reactor Netty event-loop resources by default. Explicit scheduler or executor boundaries can move selected work elsewhere, but a reactive pipeline does not automatically switch threads for every operator.

Avoid designing request state around `ThreadLocal` continuity. A single exchange can cross asynchronous boundaries, while one event-loop thread can process work for many exchanges over time. Use request/exchange state and Reactor `Context` for data whose lifetime is tied to the reactive flow.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-config-foundation">WebFlux configuration foundation</a>

<details>
<summary>Click for details</summary>

Spring Framework can register the standard WebFlux infrastructure through Java configuration. `@EnableWebFlux` imports the configuration that creates the dispatcher strategies, codec support, argument and result handling, and related web components. Applications commonly implement `WebFluxConfigurer` to customize selected parts without rebuilding that infrastructure by hand.

`WebFluxConfigurer` exposes focused callbacks for message codecs, content-type resolution, formatters, validation, custom controller argument resolvers, CORS, path matching, static resources, and view resolution. In Spring Framework 6.1 it also exposes blocking-execution configuration. When an executor is configured, controller methods whose return type is not recognized by the configured `ReactiveAdapterRegistry` are considered blocking by default; `BlockingExecutionConfigurer` can replace that heuristic with a custom controller-method predicate. The selected methods run on the configured executor, which isolates blocking work but does not make it non-blocking.

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

This layer is Framework configuration. Spring Boot builds on it and can contribute auto-configuration and external properties, but Boot behavior should not be mistaken for a `DispatcherHandler` or `WebFluxConfigurer` rule. When debugging, first identify whether a setting comes from core WebFlux configuration, the server runtime, or Boot integration.

</details>

- [Back to top](#back-to-top)
