<a id="back-to-top"></a>

# Spring Web Servlet-Stack Synthesis and Boundaries

## Menu
- [Spring MVC Request End-to-End](#mvc-request-end-to-end)
- [Annotated vs Functional Endpoints](#annotated-vs-functional-endpoints)
- [Synchronous vs Asynchronous Servlet Processing](#synchronous-vs-async-servlet-processing)
- [Filter, Interceptor, and Advice Placement](#filter-interceptor-and-advice-placement)
- [Spring MVC vs WebFlux Boundary](#mvc-vs-webflux-boundary)
- [Inbound MVC vs Outbound HTTP Client Mechanics](#inbound-vs-outbound-http-mechanics)
- [Neighboring-Module Handoffs](#neighboring-module-handoffs)
- [Final Spring Web Mental Model](#spring-web-final-mental-model)

## <a id="mvc-request-end-to-end">Spring MVC Request End-to-End</a>

<details>
<summary>Click for details</summary>

A complete Spring MVC request crosses several layers, each with a distinct responsibility:

```text
Servlet container
→ filters
→ DispatcherServlet
→ HandlerMapping
→ HandlerExecutionChain / interceptors
→ HandlerAdapter
→ argument resolution + binding/validation
→ controller or functional handler
→ return-value processing
→ message conversion or view rendering
→ exception resolution when needed
→ response
```

The value of this model is diagnostic. A 404 can mean "no handler mapping matched"; a 400 may come from binding or validation before controller logic; a 406/415 can point to representation negotiation or converters; a 500 may move through the exception-resolver chain.

Do not debug every web failure inside the controller. First locate the stage that owns the behavior.

</details>

- [Back to top](#back-to-top)

---

## <a id="annotated-vs-functional-endpoints">Annotated vs Functional Endpoints</a>

<details>
<summary>Click for details</summary>

Annotated controllers and WebMvc.fn are two programming models over the same Servlet-stack Spring MVC infrastructure. The choice is primarily about how application routing and handler contracts are expressed.

Annotated controllers integrate naturally with familiar annotations, method argument resolvers, binding, validation, and controller advice. Functional endpoints make routing and handler composition explicit in Java values and functions.

Neither style is universally faster, cleaner, or more "modern". Choose based on team conventions, composition needs, testability, and the shape of the application. Both still use Spring MVC request/response infrastructure and can coexist in one application.

Avoid duplicating the same route in both models; handler ordering and mapping precedence then become an accidental part of application behavior.

</details>

- [Back to top](#back-to-top)

---

## <a id="synchronous-vs-async-servlet-processing">Synchronous vs Asynchronous Servlet Processing</a>

<details>
<summary>Click for details</summary>

Normal Spring MVC processing is synchronous from the controller's perspective: the Servlet request thread remains responsible until the handler completes and a response is produced. Async MVC lets that original request thread leave while completion happens later.

Single-result async types such as `Callable`, `DeferredResult`, and `WebAsyncTask` eventually resume MVC processing through async dispatch. Streaming types such as `ResponseBodyEmitter`, `SseEmitter`, and `StreamingResponseBody` keep the response open while data is emitted.

Async MVC is useful when request work must wait on another task or produce incremental output, but it adds lifecycle concerns: executor capacity, timeout, client disconnect, error redispatch, and context propagation.

It is still Servlet-stack MVC. Reactive return-value adaptation does not turn blocking Servlet response writes into a fully non-blocking WebFlux pipeline.

</details>

- [Back to top](#back-to-top)

---

## <a id="filter-interceptor-and-advice-placement">Filter, Interceptor, and Advice Placement</a>

<details>
<summary>Click for details</summary>

Cross-cutting web behavior can execute at different layers:

- a Servlet `Filter` surrounds the Servlet dispatch and can act before Spring MVC;
- a `HandlerInterceptor` surrounds mapped MVC handler execution;
- controller advice participates in MVC concerns such as exception handling, binding initialization, or model contributions depending on the advice contract.

Choose the layer by the object you need to reason about. If the concern must apply to every Servlet dispatch, including non-MVC resources, a filter is the natural boundary. If it depends on the selected MVC handler, an interceptor is more appropriate. If it changes controller semantics, MVC advice is often narrower.

Putting a concern too high loses MVC context; putting it too low may duplicate logic across controllers. Security is a special case with its own Spring Security filter infrastructure and should not be rebuilt from ad-hoc MVC interceptors.

</details>

- [Back to top](#back-to-top)

---

## <a id="mvc-vs-webflux-boundary">Spring MVC vs WebFlux Boundary</a>

<details>
<summary>Click for details</summary>

Spring MVC and WebFlux solve overlapping web application problems with different runtime models. Spring MVC is built on the Servlet API and is a natural fit for blocking libraries and thread-per-request application code. WebFlux is built for reactive, non-blocking request processing and integrates deeply with Reactive Streams.

The existence of async MVC does not erase that distinction. MVC can release the request thread and adapt reactive publishers, but Servlet response writes and much of the surrounding ecosystem remain based on the Servlet model.

Choose the stack according to end-to-end dependencies. A WebFlux controller that immediately calls blocking persistence without isolation does not gain a non-blocking system; an MVC application does not need WebFlux merely because one operation is asynchronous.

Deep Reactor, backpressure, `WebClient`, reactive codecs, and WebFlux dispatch belong to the Spring Reactive module.

</details>

- [Back to top](#back-to-top)

---

## <a id="inbound-vs-outbound-http-mechanics">Inbound MVC vs Outbound HTTP Client Mechanics</a>

<details>
<summary>Click for details</summary>

This module contains both **inbound** and **outbound** HTTP mechanics, but they face opposite directions.

```text
Inbound
client → DispatcherServlet → application handler

Outbound
application code → RestClient / RestTemplate / HTTP Service proxy → remote server
```

They share concepts such as headers, media types, message converters, status codes, and URI handling. They do not share lifecycle ownership: inbound MVC controls server request dispatch, while outbound clients control remote request construction and response extraction.

Keeping the directions distinct prevents errors such as trying to solve client retry policy with MVC interceptors or trying to configure controller content negotiation on a `RestClient`.

</details>

- [Back to top](#back-to-top)

---

## <a id="neighboring-module-handoffs">Neighboring-Module Handoffs</a>

<details>
<summary>Click for details</summary>

Spring Web deliberately hands several concerns to neighboring modules:

- reusable `Validator`, `DataBinder`, conversion, and formatting foundations → Validation/Data Binding;
- WebFlux, Reactor, reactive codecs, and deep `WebClient` mechanics → Spring Reactive;
- WebSocket, STOMP, and Spring Messaging → Messaging;
- authentication, authorization, CSRF, and `SecurityFilterChain` → Spring Security;
- `MockMvc`, TestContext, and framework-level web testing → Spring Testing;
- MVC auto-configuration and embedded-server conventions → Spring Boot;
- client selection, resilience, and service-to-service HTTP architecture → Integration HTTP.

These handoffs are not missing content. They preserve one primary owner for each mental model so learners do not receive several conflicting versions of the same concept.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-web-final-mental-model">Final Spring Web Mental Model</a>

<details>
<summary>Click for details</summary>

The final Spring Web mental model is a set of cooperating pipelines, not a bag of annotations:

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
→ request factory + converters
→ remote HTTP exchange
→ status/error handling + body conversion
```

Configuration supplies policies to those pipelines; extension points participate only where their contracts apply.

When debugging or designing a feature, ask three questions:

1. Is this inbound server processing or outbound client processing?
2. Which lifecycle stage owns the behavior?
3. Does the concern belong to Spring Web or to a neighboring module?

Answering those questions usually identifies the correct API and prevents infrastructure logic from leaking into controllers or business services.

</details>

- [Back to top](#back-to-top)
