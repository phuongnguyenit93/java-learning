<a id="back-to-top"></a>

# Asynchronous MVC and Streaming

## Menu
- [Servlet Async Request Lifecycle](#servlet-async-lifecycle)
- [Single-Result Async Handling with Callable, DeferredResult, and WebAsyncTask](#deferred-single-result)
- [Streaming with ResponseBodyEmitter, SseEmitter, and StreamingResponseBody](#servlet-streaming)
- [Reactive Return Values Adapted in Spring MVC](#reactive-return-values-in-mvc)
- [AsyncTaskExecutor, Timeouts, Redispatch, and ASYNC Filter Dispatch](#async-timeouts-executor-and-dispatch)
- [Async Spring MVC vs WebFlux Boundary](#async-mvc-vs-webflux)

## <a id="servlet-async-lifecycle">Servlet Async Request Lifecycle</a>

<details>
<summary>Click for details</summary>

Servlet async processing lets a request leave the original container thread without completing the HTTP response immediately. Spring MVC builds its async support on top of that Servlet capability.

For deferred single-result processing, the high-level lifecycle is:

```text
initial REQUEST dispatch
→ MVC starts async processing
→ original request thread returns to container
→ result becomes available later
→ ASYNC dispatch back into container/MVC
→ MVC resumes return-value processing
```

The request/response objects remain associated with the async request; this is not a new HTTP request from the client.

Async lifecycle introduces new failure points: timeouts, executor rejection, client disconnect, and errors that occur after the original thread has left. Code should not assume thread-local context from the initial dispatch is automatically available later.

</details>

- [Back to top](#back-to-top)

---

## <a id="deferred-single-result">Single-Result Async Handling with Callable, DeferredResult, and WebAsyncTask</a>

<details>
<summary>Click for details</summary>

`Callable`, `DeferredResult`, and `WebAsyncTask` all represent one eventual MVC result, but they differ in who produces it.

`Callable` asks Spring MVC to run the computation on the configured async executor. `DeferredResult` is completed externally by application code, an event, or another asynchronous operation. `WebAsyncTask` wraps a callable with additional timeout/executor/callback configuration.

After the result is set, MVC performs an async redispatch and processes the value as if the controller had returned it during the resumed lifecycle.

Use these types when the application eventually has **one** result. They are not streaming APIs and should not be used to push an unbounded sequence of values.

Cancellation/timeout ownership must be explicit: an HTTP timeout does not automatically guarantee that every external operation has stopped.

</details>

- [Back to top](#back-to-top)

---

## <a id="servlet-streaming">Streaming with ResponseBodyEmitter, SseEmitter, and StreamingResponseBody</a>

<details>
<summary>Click for details</summary>

Streaming keeps the HTTP response open while data is produced incrementally.

`ResponseBodyEmitter` lets application code send multiple objects; MVC writes each through suitable message converters. `SseEmitter` specializes that model for Server-Sent Events. `StreamingResponseBody` gives direct access to the response `OutputStream` for byte-oriented streaming.

These APIs have different abstraction levels:

```text
ResponseBodyEmitter / SseEmitter
→ object/event streaming through MVC

StreamingResponseBody
→ direct byte stream callback
```

Streaming needs backpressure/slow-client thinking even though Servlet MVC is not Reactive Streams. A slow or disconnected client can make writes block or fail. Applications should bound producer work and release resources on completion/error.

Once bytes are committed, error handling cannot safely replace the response with a completely different normal error body.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-return-values-in-mvc">Reactive Return Values Adapted in Spring MVC</a>

<details>
<summary>Click for details</summary>

Spring MVC can adapt certain reactive return types through `ReactiveAdapterRegistry`. This is useful when a controller calls a reactive API but the application still runs on the Servlet MVC stack.

Single-value reactive types can be adapted to deferred async completion. Multi-value publishers may be streamed for supported streaming media types or otherwise adapted according to MVC's return-value handling.

The crucial boundary is that reactive **controller values** do not make the entire server runtime reactive. MVC is still based on Servlet async processing and Servlet response I/O.

Use this bridge when it reduces impedance between an MVC application and a reactive dependency. If the system needs end-to-end non-blocking request processing, reactive codecs, and Reactive Streams semantics, WebFlux is the correct owner.

</details>

- [Back to top](#back-to-top)

---

## <a id="async-timeouts-executor-and-dispatch">AsyncTaskExecutor, Timeouts, Redispatch, and ASYNC Filter Dispatch</a>

<details>
<summary>Click for details</summary>

Async MVC requires production configuration beyond choosing a return type.

`AsyncSupportConfigurer` can set the `AsyncTaskExecutor`, default timeout, and async interceptors. `Callable` and `StreamingResponseBody` need executor capacity appropriate for blocking work; the simple default executor is not a production sizing strategy under sustained load.

Servlet/filter registration must permit async dispatch where required. A request can pass through a `REQUEST` dispatch, leave the thread, then later return through an `ASYNC` dispatch. Filters need deliberate dispatcher-type behavior.

Timeouts should align across MVC, the Servlet container, remote clients, and application work. A web timeout shorter than an uncancelled downstream call can leave wasted work running after the client has given up.

Thread-local context such as logging MDC does not automatically jump executors; propagate only the context the application intentionally owns.

</details>

- [Back to top](#back-to-top)

---

## <a id="async-mvc-vs-webflux">Async Spring MVC vs WebFlux Boundary</a>

<details>
<summary>Click for details</summary>

Async Spring MVC and WebFlux both support asynchronous applications, but their foundations differ.

Spring MVC:
- Servlet API;
- blocking I/O model is normal;
- async requests extend the Servlet lifecycle;
- can adapt reactive return values.

WebFlux:
- reactive web runtime;
- non-blocking processing is a first-class model;
- Reactive Streams/backpressure participate throughout the stack;
- deep `WebClient` and reactive codec behavior belong there.

Do not choose WebFlux merely because one endpoint returns `CompletableFuture`/a publisher, and do not assume MVC becomes non-blocking because the initial request thread was released.

Choose based on the full dependency chain and operational model.

</details>

- [Back to top](#back-to-top)
