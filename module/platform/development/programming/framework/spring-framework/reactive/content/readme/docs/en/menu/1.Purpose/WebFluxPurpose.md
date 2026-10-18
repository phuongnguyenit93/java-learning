<a id="back-to-top"></a>

# Why Spring WebFlux Exists

## Menu
- [What Spring WebFlux is and why it exists](#webflux-purpose)
- [The scaling problem behind blocking web stacks](#webflux-problem-with-blocking)
- [The non-blocking reactive web model](#webflux-nonblocking-model)
- [Throughput, concurrency, and latency are different concerns](#webflux-throughput-vs-latency)
- [Spring MVC and WebFlux runtime assumptions](#webflux-mvc-runtime-contrast)
- [How blocking dependencies affect WebFlux](#webflux-blocking-dependency-impact)
- [Workloads that fit WebFlux well](#webflux-good-fit)
- [When WebFlux is the wrong choice](#webflux-poor-fit)
- [Annotated controllers and functional endpoints](#webflux-programming-models)

## <a id="webflux-purpose">What Spring WebFlux is and why it exists</a>

<details>
<summary>Click for details</summary>

Spring WebFlux is Spring Framework's reactive web stack. It exists so an application can process HTTP traffic with non-blocking I/O and compose request handling around asynchronous values and streams. The server side is implemented in the `spring-webflux` module and can be used with either annotated controllers or functional endpoints.

The important starting point is the runtime contract, not the presence of `Mono` or `Flux` in a method signature. WebFlux assumes that request processing can make progress without parking a request thread while waiting for network I/O. That assumption lets a server handle many concurrent exchanges with a relatively small set of processing threads when the rest of the dependency chain follows the same model.

WebFlux applies Reactive Streams ideas at the web boundary, especially asynchronous publication, cancellation, and demand-aware body processing. The generic Reactive Streams and Reactor programming model belongs to the reactive-programming prerequisite; this module focuses on how Spring uses those ideas for HTTP dispatch, codecs, controllers, functional endpoints, filters, errors, and `WebClient`.

Choosing WebFlux is therefore an architecture decision about the whole I/O path. It is most valuable when the application has meaningful concurrency and latency in external I/O and can keep those operations non-blocking end to end.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-problem-with-blocking">The scaling problem behind blocking web stacks</a>

<details>
<summary>Click for details</summary>

A traditional blocking web stack commonly assigns request work to a thread and allows that thread to wait while downstream I/O completes. The programming model is straightforward, but every long wait still occupies a thread. Servlet containers compensate with a comparatively large request-thread pool so other requests can continue while some threads are blocked.

That approach is often perfectly adequate, but it becomes expensive when a service holds many concurrent requests that mostly wait on remote systems. More concurrency can mean more threads, more stack memory, more context switching, and eventually a queue once the pool is saturated. The limiting resource can become the thread pool before CPU or network capacity is exhausted.

WebFlux addresses that specific scaling shape. With non-blocking server and client I/O, a waiting exchange does not need to reserve a processing thread. The runtime can resume the pipeline when data or completion becomes available. This does not remove latency from a database or remote service; it changes how application resources are occupied while that latency exists.

The useful question is therefore not "Are threads bad?" It is whether blocking waits dominate the workload enough that tying one processing thread to each in-flight request becomes a scaling constraint.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-nonblocking-model">The non-blocking reactive web model</a>

<details>
<summary>Click for details</summary>

In the WebFlux model, request input, endpoint results, and response output can arrive asynchronously. HTTP body data is exposed and consumed through Reactive Streams publishers, and framework components compose completion instead of assuming that an entire body or downstream result is already available.

The server runtime signals I/O readiness, Spring advances the corresponding reactive pipeline, and processing yields again when it reaches another asynchronous boundary. On runtimes such as Reactor Netty this commonly happens on event-loop threads, so application code must return control quickly instead of blocking those threads.

Backpressure matters mainly for streams of data. A consumer can signal how much data it is ready to process, allowing supported codecs and transports to avoid eagerly pulling an unbounded body into memory. Cancellation is equally significant: if the client disconnects or a pipeline is cancelled, downstream reactive work can be cancelled too when the participating components honor that signal.

This model does not imply that one request stays on one thread. Reactive execution can continue on the same event-loop thread or move according to runtime and scheduler boundaries. Correct code should depend on the reactive contract and request-scoped data, not thread affinity.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-throughput-vs-latency">Throughput, concurrency, and latency are different concerns</a>

<details>
<summary>Click for details</summary>

Three performance terms answer different questions. **Latency** is how long one operation takes. **Throughput** is how much work the system completes per unit of time. **Concurrency** is how much work is in flight at once. WebFlux primarily changes the resource cost of maintaining concurrency while work waits on I/O.

Reactive and non-blocking code is not automatically faster for an individual request. There is still protocol, serialization, application, and downstream latency, plus some orchestration overhead. For short CPU-only work, changing to a reactive API may produce no latency benefit at all.

The stronger WebFlux case is a service with many simultaneous requests and significant slow or unpredictable I/O. A small number of non-blocking processing threads can keep serving other exchanges while earlier ones wait, which can improve resource usage and resilience under load. In some flows, concurrent outbound calls through `WebClient` can also reduce end-to-end latency because independent waits overlap, but that is a property of the composition, not a blanket property of WebFlux.

Measure the bottleneck that matters. A system that is CPU-bound, connection-limited, database-limited, or constrained by a blocking dependency will not be fixed merely by exposing reactive return types.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-mvc-runtime-contrast">Spring MVC and WebFlux runtime assumptions</a>

<details>
<summary>Click for details</summary>

Spring MVC and Spring WebFlux deliberately share many programming concepts, including `@Controller`, `@RequestMapping`, `@RequestBody`, `ResponseEntity`, validation integration, and view rendering. Their default runtime assumptions are nevertheless different.

Spring MVC is built around the Servlet stack and assumes that application code may block the request thread. Servlet containers therefore maintain a request-thread pool sized to absorb blocking work. Spring WebFlux assumes application code does not block during request processing and can therefore operate with a much smaller set of processing threads on non-blocking runtimes.

WebFlux can run on Reactor Netty and also on Servlet containers such as Tomcat or Jetty through Servlet non-blocking I/O adapters. Running WebFlux on a Servlet container does **not** turn it into Spring MVC: WebFlux still exposes its reactive HTTP and `WebHandler` abstractions, and application code should not depend on direct Servlet API access or Servlet filters.

The shared annotations make migration look syntactically easy, but the real compatibility question is behavioral: whether the endpoint, filters, libraries, persistence layer, and outbound clients respect the non-blocking execution model.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-blocking-dependency-impact">How blocking dependencies affect WebFlux</a>

<details>
<summary>Click for details</summary>

A WebFlux endpoint can still call a blocking API, but doing so on a non-blocking processing thread occupies a scarce worker while the call waits. Enough such calls can starve the event loop and make unrelated requests stall, which defeats the main concurrency benefit of WebFlux.

Typical sources are blocking database drivers, filesystem operations, legacy SDKs, synchronous HTTP clients, and libraries that hide blocking work. A reactive wrapper does not change the behavior underneath. For example, putting a JDBC call inside `Mono.fromCallable(...)` is still blocking work until it is explicitly moved to a suitable bounded worker pool.

Offloading can be a pragmatic bridge. Reactor provides scheduler switching, and Spring Framework 6.1 also has WebFlux configuration for identifying blocking controller methods and running them on a configured executor. Those mechanisms isolate blocking work; they do not make it non-blocking. Capacity, queueing, timeouts, and cancellation behavior of that worker pool still matter.

If the dominant dependency path is blocking, Spring MVC is often the simpler and more predictable choice. WebFlux becomes strongest when blocking islands are exceptional and intentionally contained rather than the normal execution path.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-good-fit">Workloads that fit WebFlux well</a>

<details>
<summary>Click for details</summary>

WebFlux is a good fit when the application has many concurrent HTTP exchanges that spend substantial time waiting for non-blocking I/O. Examples include API gateways, aggregation services that call several remote services, streaming endpoints, server-sent events, and services whose persistence and outbound-client stack is already reactive.

The fit becomes better when several conditions line up:

- inbound server I/O is non-blocking;
- downstream clients and persistence APIs can remain non-blocking;
- response bodies may be streamed instead of fully buffered;
- concurrency under slow or bursty I/O is a real operational concern;
- the team is comfortable composing asynchronous pipelines and observing them in production.

WebFlux can also be useful when a service naturally exposes a reactive domain API and wants cancellation and streaming to propagate through the HTTP boundary. The benefit comes from preserving that model across layers, not from changing only the controller signature.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-poor-fit">When WebFlux is the wrong choice</a>

<details>
<summary>Click for details</summary>

WebFlux is usually a poor trade when most of the application is built on blocking dependencies and there is no realistic plan to isolate or replace them. In that case the service still needs enough worker threads for blocking calls while also carrying the additional reactive programming model.

It is also often unnecessary for simple CRUD applications with modest concurrency, for CPU-bound workloads where non-blocking I/O does not address the bottleneck, or for teams that would gain more reliability from the simpler imperative request model. Spring MVC remains a first-class Spring Framework stack and supports asynchronous features where needed.

Do not choose WebFlux because reactive APIs look modern or because a benchmark shows one isolated path winning. Consider the whole request path, operational tooling, debugging cost, library compatibility, team fluency, and measured load profile. A smaller thread count is only an advantage when code actually avoids long blocking sections on those threads.

The architecture can also be mixed at system boundaries: an MVC application may use `WebClient`, and a WebFlux application may temporarily offload isolated blocking work. What matters is that each boundary has an explicit execution model and capacity plan.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-programming-models">Annotated controllers and functional endpoints</a>

<details>
<summary>Click for details</summary>

WebFlux offers two server programming models over the same reactive HTTP foundation.

**Annotated controllers** use `@Controller` or `@RestController` with mapping and argument annotations. Spring discovers handler methods, resolves method arguments, invokes the method, and routes the result through the WebFlux result-handling infrastructure. This style is familiar to Spring MVC users and integrates naturally with controller advice, binding, validation, and view rendering.

**Functional endpoints** use `RouterFunction`, `HandlerFunction`, `ServerRequest`, and `ServerResponse`. Routes and handling logic are expressed as Java functions with immutable request/response contracts. This can make routing and composition more explicit and keeps endpoint behavior close to ordinary function composition.

In standard WebFlux configuration both models participate in the same dispatch architecture and use the same underlying codec and server infrastructure. Functional routing can also be adapted directly to a low-level `HttpHandler` in specialized setups. Neither model is inherently more reactive than the other; the choice is mostly about application structure, discoverability, explicitness, and team conventions.

</details>

- [Back to top](#back-to-top)
