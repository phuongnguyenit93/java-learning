<a id="back-to-top"></a>

# Production Boundaries, Trade-offs, and End-to-End Design

## Menu
- [Blocking calls and thread starvation](#webflux-blocking-thread-starvation)
- [Unbounded buffering and scheduler misuse](#webflux-buffering-scheduler-pitfalls)
- [Backpressure and cancellation at the WebFlux boundary](#webflux-backpressure-cancellation)
- [Connection lifetime and streaming responses](#webflux-connection-lifetime)
- [Resource limits and production pressure](#webflux-resource-limits)
- [Choosing Spring MVC or WebFlux](#webflux-mvc-decision)
- [Dependency-stack compatibility](#webflux-dependency-stack-fit)
- [Team and operational complexity](#webflux-team-complexity)
- [Neighboring-module handoffs](#webflux-neighboring-module-handoffs)
- [End-to-end WebFlux request flow](#webflux-end-to-end-flow)

## <a id="webflux-blocking-thread-starvation">Blocking calls and thread starvation</a>

<details>
<summary>Click for details</summary>

WebFlux is designed so a relatively small number of runtime threads can keep many I/O operations in flight. That model works only while those threads remain available to process signals and network events. A blocking call occupies a thread while no useful reactive work can proceed on that thread.

The failure mode is therefore broader than “one slow request.” If an event-loop thread blocks on JDBC, a synchronous SDK, filesystem access, or a lock, every connection assigned to that loop can experience added latency. Enough blocked loops can make the server appear stalled even when CPU usage is modest.

~~~java
@GetMapping("/profile/{id}")
Mono<Profile> profile(@PathVariable String id) {
    Profile value = blockingClient.load(id); // blocks before Mono exists
    return Mono.just(value);
}
~~~

Wrapping the result in Mono does not move the blocking operation. The call already happened on the invoking thread. Spring Framework 6.1 can explicitly invoke selected **annotated controller methods** on a configured executor, but that is a bounded interoperability tool rather than a general cure for blocking dependencies throughout the pipeline.

In production, investigate thread dumps, request latency, event-loop utilization, downstream latency, and pool saturation together. If the application is dominated by blocking dependencies, using Spring MVC can be simpler and more predictable than continually moving blocking work away from WebFlux event loops.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-buffering-scheduler-pitfalls">Unbounded buffering and scheduler misuse</a>

<details>
<summary>Click for details</summary>

A reactive pipeline can avoid blocking and still fail under load if it accumulates data without a useful bound. Aggregating a large request or response, collecting an open-ended Flux into a List, or queueing work faster than a downstream dependency can process it converts concurrency into memory pressure.

Codec limits such as maxInMemorySize are guardrails for operations that must aggregate data. Raising a limit can be correct for a known payload contract, but repeatedly increasing it to accommodate uncontrolled payload growth merely moves the failure point.

Scheduler switching has a similar trap. Moving work to another scheduler or executor changes **where** the work executes; it does not reduce how much work exists, make an unbounded queue bounded, or turn a blocking dependency into a scalable non-blocking one. Each offload boundary needs a reason, a capacity model, and failure behavior when that capacity is exhausted.

Keep the WebFlux-specific rule simple: preserve streaming when the protocol is streaming, bound aggregation when whole values are required, and make blocking interoperability explicit. Detailed Reactor scheduler/operator mechanics belong to the reactive-programming module.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-backpressure-cancellation">Backpressure and cancellation at the WebFlux boundary</a>

<details>
<summary>Click for details</summary>

At the WebFlux boundary, Reactive Streams demand helps connect application consumption with asynchronous HTTP reads and writes. The practical consequence is that WebFlux can avoid reading or producing an unlimited number of elements merely because a connection exists.

Backpressure is not a promise that every network or downstream system has identical demand semantics. Buffers still exist in codecs, connectors, TCP stacks, proxies, and application operators. The goal is to keep those buffers bounded and let demand propagate as far as the participating components support it.

Cancellation is equally important. A browser can close a connection, a timeout can cancel a request, or a caller can stop consuming a WebClient response. That cancellation can propagate through the publisher chain so upstream work has an opportunity to stop.

Application code should avoid hiding cancellation behind detached side effects that continue expensive work after the HTTP exchange is gone unless that work is intentionally independent. Database and transaction cancellation semantics depend on their own reactive integrations and belong to the corresponding data-access and transaction modules.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-connection-lifetime">Connection lifetime and streaming responses</a>

<details>
<summary>Click for details</summary>

A normal request often holds a connection only until one bounded response is written. Streaming changes that lifecycle: Server-Sent Events, newline-delimited streams, or other long-lived responses can keep a connection active for a long time while elements arrive incrementally.

Long-lived connections need an explicit operational model. Consider server and proxy idle timeouts, client cancellation, keep-alive behavior, maximum concurrent streams, downstream production rate, and what happens during deployment or shutdown. A non-blocking connection consumes fewer threads than a thread-per-connection model, but it still consumes sockets, buffers, bookkeeping, and application capacity.

Response commitment also changes error handling. Once status and headers have been committed and body elements have been sent, a later failure usually cannot be replaced with a fresh structured error response. The observable outcome may instead be an interrupted stream or closed connection.

Design clients to treat stream termination as part of the protocol. Decide whether reconnection, replay, resume tokens, or at-most-once behavior is required at the application layer rather than assuming the framework can reconstruct a partially delivered stream.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-resource-limits">Resource limits and production pressure</a>

<details>
<summary>Click for details</summary>

Non-blocking I/O increases the number of concurrent operations a process can coordinate efficiently; it does not make capacity infinite. Production pressure appears at several separate limits:

- server connections and operating-system file descriptors;
- event-loop and connector resources;
- outbound connection pools and pending acquisition queues;
- codec aggregation limits and DataBuffer memory;
- application queues, concurrency limits, and retained objects;
- downstream services, databases, and external APIs.

The smallest saturated dependency often determines system behavior. Allowing the WebFlux server to accept far more concurrent work than a downstream pool can service can simply move the queue into memory and increase timeout rates.

Set limits where the application has a meaningful capacity boundary, then observe rejection, timeout, cancellation, memory, and latency behavior under realistic load. Connection limits, body limits, and timeouts are part of the application's production contract, not emergency settings to discover only after saturation.

Also distinguish steady-state capacity from failure capacity. A healthy downstream service may handle normal concurrency while a degraded one holds requests open much longer, consuming connections and buffers until an otherwise reasonable limit is exhausted.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-mvc-decision">Choosing Spring MVC or WebFlux</a>

<details>
<summary>Click for details</summary>

Spring MVC and Spring WebFlux are both production Spring web stacks. The choice should follow workload and dependency characteristics rather than the idea that one is universally newer or faster.

WebFlux is a strong fit when the application has substantial asynchronous/non-blocking I/O, needs streaming, coordinates many concurrent slow I/O operations, or must compose naturally with reactive dependencies. Its value is efficient concurrency and composition; it does not make the service time of an individual remote call inherently shorter.

Spring MVC is often the simpler fit when most dependencies are blocking, request handling is naturally imperative, and the expected concurrency is well served by the Servlet execution model. Java virtual threads can further change the cost profile of blocking MVC applications, so architecture decisions should be based on measured needs rather than thread-count folklore.

Do not choose WebFlux merely because one library returns Mono or because WebClient is present. Likewise, do not reject WebFlux because a small bounded blocking boundary exists. Evaluate the dominant data path, streaming needs, concurrency profile, operational experience, and compatibility of critical dependencies.

If both stacks can meet the requirement, consistency and team comprehension are valid decision factors. A simpler execution model that meets capacity goals is usually easier to operate.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-dependency-stack-fit">Dependency-stack compatibility</a>

<details>
<summary>Click for details</summary>

WebFlux delivers its largest architectural benefit when the important I/O path is compatible with asynchronous, non-blocking composition. One blocking dependency can be isolated; a stack made mostly of blocking dependencies changes the economics of the whole design.

Review the path end to end:

~~~text
HTTP request
  -> WebFlux handler
  -> application service
  -> persistence / cache / remote HTTP / SDK
  -> response
~~~

For outbound HTTP, WebClient can preserve the reactive path. Reactive data-access infrastructure can do the same for supported drivers, but repository/query/transaction semantics belong to the data-access and transaction-management modules. JDBC and JPA are fundamentally blocking APIs even if their results are wrapped in publishers.

When a required library is blocking, decide whether it is small and bounded enough to isolate on an appropriate executor. Include queue size, concurrency, timeout, and saturation behavior in that decision. If blocking calls dominate normal request execution, MVC may provide a clearer and cheaper operational model.

Compatibility also includes hidden behavior: DNS resolution, template rendering, serialization extensions, third-party callbacks, and logging appenders can introduce blocking or large allocations. Verify critical dependencies rather than assuming “reactive-compatible” from an API shape alone.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-team-complexity">Team and operational complexity</a>

<details>
<summary>Click for details</summary>

Reactive web applications introduce operational concepts the team must be prepared to debug: event-loop starvation, publisher cancellation, contextual logging, long-lived connections, asynchronous stack traces, connector pools, and response-commit timing.

That complexity is justified when it solves a real workload problem. It is unnecessary cost when the application is a small blocking CRUD service whose throughput target is already easy to meet.

Teams should make the execution model visible in code review and observability. Useful evidence includes request and downstream latency, connection-pool saturation, event-loop behavior, memory under streaming load, timeout/cancellation rates, and correlation identifiers that survive reactive execution.

Load testing should exercise realistic slow downstreams and cancellation, not only fast happy-path requests. A WebFlux application can look excellent when every dependency responds immediately and degrade sharply when connection lifetimes grow during a partial outage.

Consistency matters as well. Establish conventions for error mapping, context propagation, bounded blocking boundaries, WebClient configuration, and streaming endpoints so every team member does not invent a different reactive architecture.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-neighboring-module-handoffs">Neighboring-module handoffs</a>

<details>
<summary>Click for details</summary>

This module owns the Spring Framework WebFlux runtime, server programming models, cross-cutting web pipeline, and WebClient mechanics. Several concerns intentionally stop at neighboring module boundaries:

- **Reactive programming** owns Reactive Streams theory, Reactor operators, schedulers, and general backpressure mechanics.
- **Validation and data binding** owns Validator, DataBinder, ConversionService, and Formatter semantics that WebFlux consumes at the HTTP boundary.
- **Data access** owns Spring Framework JDBC/R2DBC access mechanics; reactive persistence repository semantics live with the relevant Spring Data modules.
- **Transaction management** owns Spring transaction policy and reactive transaction semantics.
- **Testing** owns WebTestClient and Framework test infrastructure.
- **Messaging** owns Spring WebSocket client/server and handler/session lifecycle, WebSocket/STOMP application messaging, and Spring RSocket programming.
- **Spring Security** owns authentication, authorization, SecurityContext, CSRF, and security policy even though it integrates through the WebFlux filter chain.
- **Spring Web** owns the Servlet-stack MVC model used for the main architectural comparison.

These handoffs are part of the design, not missing WebFlux features. Learn enough here to recognize where each concern attaches to the HTTP pipeline, then follow the owning module for its complete model.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-end-to-end-flow">End-to-end WebFlux request flow</a>

<details>
<summary>Click for details</summary>

A typical DispatcherHandler-based request can now be followed as one continuous path:

~~~text
reactive HTTP server
  -> HttpHandler adapter
  -> WebFilter chain / WebExceptionHandler boundary
  -> DispatcherHandler
  -> HandlerMapping
  -> HandlerAdapter
  -> annotated controller or functional HandlerFunction
  -> application service
  -> optional outbound WebClient / other non-blocking I/O
  -> HandlerResult / ServerResponse
  -> HandlerResultHandler + codecs or view rendering
  -> response write and completion
~~~

ServerWebExchange carries request/response state through the server-web layer, while Reactor Context can carry contextual values through reactive composition. Cancellation or an error can travel back through the same chain; outer filters and exception handlers observe it according to their ordering and whether the response has already been committed.

Functional endpoints have one additional deployment option: a RouterFunction can be adapted directly to HttpHandler with HandlerStrategies. That lower-level path does not discover the route through DispatcherHandler/RouterFunctionMapping, although it still uses the functional request/response strategies configured for the adapter.

If Spring 6.1 blocking controller execution is configured, the selected annotated controller method may be invoked on the configured executor. That is a specific execution boundary inside the broader flow; downstream reactive work still follows its own publisher execution semantics.

The production objective is continuity of ownership: each stage should know whether it is routing, decoding, invoking application logic, calling outbound I/O, mapping errors, or writing a response. When a latency or resource problem appears, tracing this flow identifies the layer that owns the evidence instead of treating “WebFlux” as one opaque component.

### References

- Spring Framework 6.1.14 API — DispatcherHandler
- Spring Framework 6.1.14 API — org.springframework.web.server
- Spring Framework 6.1.14 API — WebClient

</details>

- [Back to top](#back-to-top)
