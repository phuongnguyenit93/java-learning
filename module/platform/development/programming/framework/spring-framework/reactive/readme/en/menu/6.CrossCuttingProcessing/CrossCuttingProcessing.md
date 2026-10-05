<a id="back-to-top"></a>

# Cross-Cutting Processing, Context, and Errors

## Menu
- [WebFilter and WebFilterChain](#webflux-webfilter-chain)
- [Filter ordering and execution boundaries](#webflux-webfilter-ordering)
- [Reactor Context in web processing](#webflux-reactor-context)
- [Reactor Context versus ThreadLocal](#webflux-context-vs-threadlocal)
- [WebSession and exchange-scoped state](#webflux-web-session)
- [WebExceptionHandler](#webflux-webexceptionhandler)
- [Controller, dispatch, and WebHandler error layers](#webflux-error-handling-layers)
- [WebFluxConfigurer extension points](#webflux-configurer)
- [CORS and path-matching configuration](#webflux-cors-path-config)
- [Configured blocking controller-method execution in Spring 6.1](#webflux-blocking-execution)
- [Security integration boundary](#webflux-security-boundary)

## <a id="webflux-webfilter-chain">WebFilter and WebFilterChain</a>

<details>
<summary>Click for details</summary>

WebFilter is the WebFlux interception contract for cross-cutting behavior around a WebHandler chain. Its method receives the current ServerWebExchange plus a WebFilterChain and returns Mono<Void> that completes when request processing is finished.

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

Calling chain.filter(exchange) delegates to the remaining filters and eventually to the target WebHandler. A filter may also short-circuit by returning its own completion publisher after writing a response. This makes WebFilter suitable for infrastructure concerns such as correlation metadata, timeout policy, observations, CORS helpers, and security integration.

WebFilter is not a Servlet Filter and is not an MVC HandlerInterceptor. Its contract is reactive, its state boundary is ServerWebExchange, and completion can happen after asynchronous work. Code in a filter therefore must follow the same non-blocking and resource-lifecycle rules as endpoint code.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-webfilter-ordering">Filter ordering and execution boundaries</a>

<details>
<summary>Click for details</summary>

Filter order determines which concern sees the request first and which concern sees completion last. On the inbound path, filter A can delegate to B and then to the handler. When the returned publisher completes, control conceptually unwinds back through B and then A.

~~~text
request
  -> filter A
     -> filter B
        -> WebHandler
     <- filter B completion/error
  <- filter A completion/error
response completion
~~~

This “around” shape matters for timing, tracing, response mutation, and error handling. A filter that must add headers before the response is committed has a different constraint from a filter that only records completion metrics. A filter that transforms an error before delegating it outward also changes what upstream filters observe.

When filters are assembled from an ApplicationContext, their configured ordering becomes part of the effective web pipeline. Keep dependencies between filters explicit and minimal. If one filter silently assumes another has already populated an exchange attribute or Reactor Context key, ordering becomes a hidden runtime contract that should be documented and tested at the integration boundary.

Remember that a route-level HandlerFilterFunction has a narrower scope than WebFilter. Use the broad WebFilter chain for behavior that belongs around the WebFlux application pipeline, and route filters when the concern belongs to one functional route tree.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-reactor-context">Reactor Context in web processing</a>

<details>
<summary>Click for details</summary>

Reactor Context is the subscription-scoped context mechanism available to a reactive pipeline. At a WebFlux boundary it is useful for contextual metadata that must follow reactive composition even when execution is not tied to one Java thread.

A WebFilter is a natural place to attach request-derived context:

~~~java
public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String requestId = exchange.getRequest().getId();

    return chain.filter(exchange)
            .contextWrite(context -> context.put("requestId", requestId));
}
~~~

Downstream code can read it when needed:

~~~java
return Mono.deferContextual(contextView -> {
    String requestId = contextView.get("requestId");
    return service.call(requestId);
});
~~~

Context is not a mutable request map. Values are visible through the reactive subscription chain according to Reactor's context propagation rules. In WebFlux, use it when contextual data must travel with the reactive flow, while ServerWebExchange attributes remain appropriate for mutable data explicitly attached to one HTTP exchange.

General Reactor Context theory belongs to the reactive-programming module. The WebFlux-specific lesson is operational: contextual state should follow the pipeline rather than rely on accidental thread affinity.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-context-vs-threadlocal">Reactor Context versus ThreadLocal</a>

<details>
<summary>Click for details</summary>

ThreadLocal assumes that “current context” can be found from the current Java thread. That assumption is fragile for reactive web processing because a small event-loop thread set can serve many requests, and asynchronous boundaries may move later signals to another thread.

Reactor Context binds contextual values to a subscription instead of a thread. That aligns better with WebFlux request processing when values such as correlation metadata, observation state, or other request context must survive reactive composition.

This does not mean every ThreadLocal-based library immediately becomes invalid. Framework integrations may bridge thread-local state when they explicitly support context propagation. The important rule is to avoid designing application correctness around the idea that one request owns one thread from start to finish.

For data needed only by components that already have ServerWebExchange, an exchange attribute is often simpler. Use Reactor Context when data must propagate through APIs that only see the reactive chain. Do not duplicate the same mutable state into both mechanisms without a clear ownership rule, because divergence becomes hard to debug.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-web-session">WebSession and exchange-scoped state</a>

<details>
<summary>Click for details</summary>

WebSession is Spring WebFlux's server-side session abstraction. ServerWebExchange.getSession() and ServerRequest.session() expose it reactively, while the session itself holds attributes that can survive across HTTP requests through a WebSessionStore.

Creating or obtaining a WebSession does **not** automatically start a client session. Spring 6.1.14 defines a session as started when start() is called explicitly or when attributes are added. If a new session remains unstarted, its id is not sent to the client and save() is effectively a no-op. The framework invokes save automatically before the response is committed.

~~~java
return exchange.getSession()
        .flatMap(session -> {
            session.getAttributes().put("cartId", cartId);
            return chain.filter(exchange);
        });
~~~

Session access is different from exchange-scoped state. exchange.getAttributes() exists only for the current request exchange; WebSession attributes represent state across requests. Neither should become a dumping ground for large bodies or mutable domain aggregates.

The store implementation matters for lifecycle and I/O behavior. Keep the WebFlux pipeline non-blocking when a custom or external session store is involved, and treat session serialization, expiry, invalidation, and id changes as explicit operational concerns.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-webexceptionhandler">WebExceptionHandler</a>

<details>
<summary>Click for details</summary>

WebExceptionHandler is the error-handling contract around WebHandler processing. It receives the ServerWebExchange and the Throwable and returns Mono<Void>.

Its completion semantics are precise: completing the returned Mono means the exception has been handled; returning an error signal means the exception remains unhandled and can continue through the exception-handler chain.

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

This is a low-level web-server error layer. It is useful for failures that escape endpoint-specific handling or dispatch processing. Once a response has been committed, however, an exception handler may no longer be able to replace status, headers, or body, so error behavior must respect response lifecycle as well as exception type.

Use this layer deliberately. Business exceptions that have clear endpoint semantics are often easier to map closer to the handler, while infrastructure-wide fallback behavior belongs at the WebExceptionHandler boundary.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-error-handling-layers">Controller, dispatch, and WebHandler error layers</a>

<details>
<summary>Click for details</summary>

WebFlux has more than one error-handling layer, and choosing the correct one keeps error policy understandable.

- A functional HandlerFilterFunction can map errors for one route tree.
- Annotated controllers can use their controller exception mechanisms, including local or advice-driven exception handling.
- DispatcherHandler and its HandlerAdapter/HandlerResult processing can participate in mapping errors produced during dispatch and result handling.
- WebExceptionHandler surrounds the WebHandler pipeline and receives failures that propagate out to the server-web layer.

These layers are related but not interchangeable. A controller-specific mapping has access to controller semantics. A route filter has functional-route scope. A WebExceptionHandler sees the exchange and failure after deeper processing has failed to handle it.

Think in terms of ownership: map an error at the narrowest layer that has enough information to express the intended HTTP result, then keep the outer layer for shared fallback behavior. Mapping everything at the outermost layer loses endpoint context; mapping every infrastructure failure inside controllers duplicates policy.

Also account for response commitment. Streaming responses can write headers and body elements before a later failure occurs. At that point the server may only be able to terminate the connection or signal an error rather than replace the response with a clean JSON error document.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-configurer">WebFluxConfigurer extension points</a>

<details>
<summary>Click for details</summary>

WebFluxConfigurer is the Spring Framework callback contract for customizing applications enabled with @EnableWebFlux. It adjusts the Framework's default WebFlux infrastructure while keeping the standard DispatcherHandler-based configuration.

Spring 6.1.14 exposes extension points for concerns such as:

- HTTP message readers and writers;
- formatters, validators, and custom annotated-controller argument resolvers;
- CORS mappings and path matching;
- content-type resolution;
- view resolvers and static resource handlers;
- configured blocking controller-method execution;
- a WebSocketService integration hook; WebSocket handler/session lifecycle and application messaging semantics remain with the messaging module.

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

This interface is Framework configuration, not Spring Boot auto-configuration. A Boot application may supply and customize WebFlux infrastructure on top of Framework mechanisms, but Boot-specific defaults and property binding belong to the Spring Boot learning area.

Use the narrow callback that owns the concern. Replacing the whole configuration support class simply to change one codec limit or CORS mapping creates unnecessary responsibility and makes framework upgrades harder.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-cors-path-config">CORS and path-matching configuration</a>

<details>
<summary>Click for details</summary>

WebFluxConfigurer.addCorsMappings configures global URL-pattern-based CORS processing. In Spring 6.1.14 those mappings apply to annotated controllers, functional endpoints, and static resources. Annotated controllers may add local @CrossOrigin configuration, which Spring combines with the global mapping.

~~~java
@Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
            .allowedOrigins("https://app.example")
            .allowedMethods("GET", "POST");
}
~~~

Path matching configuration has a narrower contract. configurePathMatching customizes path matching for annotated controllers and static resources. Functional WebFlux.fn routes express their matching through RequestPredicate and RouterFunction composition, so do not assume every PathMatchConfigurer option rewrites functional-route behavior.

CORS is an HTTP cross-origin policy mechanism, while authentication and authorization belong to Spring Security. They frequently meet at the same request boundary, so ordering and preflight handling matter, but the policy owners remain distinct.

Be conservative with broad CORS rules. “Allow everything” can make local development convenient while silently weakening the browser boundary in production. Configure the actual origins, methods, and headers required by the application and verify preflight behavior through the complete web/security chain.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-blocking-execution">Configured blocking controller-method execution in Spring 6.1</a>

<details>
<summary>Click for details</summary>

Spring Framework 6.1 added BlockingExecutionConfigurer through WebFluxConfigurer.configureBlockingExecution. It is an explicit bridge for **annotated controller methods** that Spring determines should execute on a configured AsyncTaskExecutor.

Two details prevent a common misunderstanding:

1. No executor is configured by default, so controller methods run without this offload mechanism unless the application supplies one.
2. If an executor is configured and no custom predicate is supplied, the default predicate treats controller methods whose return type is not recognized by ReactiveAdapterRegistry as blocking candidates.

~~~java
@Override
public void configureBlockingExecution(BlockingExecutionConfigurer configurer) {
    configurer.setExecutor(blockingExecutor);
    configurer.setControllerMethodPredicate(handlerMethod ->
            handlerMethod.hasMethodAnnotation(BlockingEndpoint.class));
}
~~~

This feature does not make arbitrary blocking work safe throughout a reactive pipeline. It does not automatically wrap functional HandlerFunction code, and it does not follow later operators to find hidden JDBC, filesystem, or SDK calls. It controls where selected controller methods are invoked.

Use it when a WebFlux application intentionally has a bounded blocking controller boundary and an executor sized for that workload. If most of the dependency stack is blocking, the broader MVC-versus-WebFlux decision may be more important than adding more offload rules.

### References

- Spring Framework 6.1.14 API — BlockingExecutionConfigurer
- Spring Framework 6.1.14 API — WebFluxConfigurer.configureBlockingExecution

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-security-boundary">Security integration boundary</a>

<details>
<summary>Click for details</summary>

Spring Security integrates naturally with WebFlux at the web-filter boundary, but the security model itself belongs to the Spring Security modules. From the WebFlux perspective, the key fact is that authentication and authorization concerns participate in the reactive web chain and must preserve its non-blocking execution assumptions.

WebFlux provides protocol and pipeline infrastructure such as WebFilter, CORS support, ServerWebExchange, and principal access. Spring Security owns concepts such as SecurityWebFilterChain, authentication, authorization, SecurityContext, CSRF protection, and security policy.

This ownership boundary prevents duplicate mental models. A WebFlux learner should understand **where** security fits in the request path and why filter ordering or Reactor Context propagation can affect integration. Detailed access-control rules, login mechanisms, OAuth2, and method security should be learned from Spring Security.

When troubleshooting an endpoint, separate routing failures, CORS failures, security denials, and application errors. They may all appear as an unsuccessful HTTP request, but they originate in different layers and require different evidence.

</details>

- [Back to top](#back-to-top)
