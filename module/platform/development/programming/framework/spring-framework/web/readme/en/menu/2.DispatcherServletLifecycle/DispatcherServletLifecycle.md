<a id="back-to-top"></a>

# DispatcherServlet and MVC Request Lifecycle

## Menu
- [DispatcherServlet as the Front Controller](#dispatcher-servlet-front-controller)
- [DispatcherServlet Bootstrap and WebApplicationContext Hierarchy](#dispatcher-bootstrap-and-context-hierarchy)
- [HandlerMapping and HandlerExecutionChain](#handler-mapping-and-execution-chain)
- [HandlerAdapter, Handler Invocation, and Return-Value Processing](#handler-invocation-and-return-value-processing)
- [Exception Resolution in the Dispatch Flow](#exception-resolution-position)
- [DispatcherServlet Strategy Model](#dispatcher-strategies-overview)

## <a id="dispatcher-servlet-front-controller">DispatcherServlet as the Front Controller</a>

<details>
<summary>Click for details</summary>

`DispatcherServlet` is Spring MVC's front controller. Instead of assigning one servlet per application feature, the container routes MVC requests to this servlet and the servlet delegates each stage to specialized strategies.

Its job is coordination, not business logic. For each dispatch it discovers a handler, chooses an adapter that knows how to invoke that handler, exposes request attributes used by MVC, processes the handler result, resolves exceptions, and renders or writes the response.

The front-controller model creates one predictable place for cross-cutting web policies:

```text
request
→ DispatcherServlet
→ mapping
→ invocation
→ result/error processing
→ response
```

Controllers therefore do not call `DispatcherServlet`; they are called by infrastructure selected during dispatch.

</details>

- [Back to top](#back-to-top)

---

## <a id="dispatcher-bootstrap-and-context-hierarchy">DispatcherServlet Bootstrap and WebApplicationContext Hierarchy</a>

<details>
<summary>Click for details</summary>

A `DispatcherServlet` runs with a `WebApplicationContext`. In a classic Servlet deployment, the servlet can create its own child web context. An application may also have an optional root `WebApplicationContext` that becomes the parent.

The parent-child relationship is about bean visibility:

```text
root context (optional)
    ↓ visible to
DispatcherServlet child context
```

Beans in the child can reference parent beans; the parent does not see child-only MVC beans. This makes it possible to keep shared services in a root context and servlet-specific MVC infrastructure/controllers in a child context.

Modern applications, especially with Spring Boot, often hide the registration ceremony, but the visibility rule remains useful when diagnosing duplicate beans, missing MVC infrastructure, or multi-servlet applications.

Do not assume a root context is mandatory. A `DispatcherServlet` can operate with its own context when that is the application's chosen structure.

</details>

- [Back to top](#back-to-top)

---

## <a id="handler-mapping-and-execution-chain">HandlerMapping and HandlerExecutionChain</a>

<details>
<summary>Click for details</summary>

`HandlerMapping` answers the first MVC routing question: **what should handle this request?** Multiple mappings may exist, each responsible for a programming model or handler family.

For annotated controllers, `RequestMappingHandlerMapping` matches request mappings and returns a `HandlerMethod`. Functional MVC uses a router-function mapping. Resource handling and other handler types can have their own mappings.

The selected result is wrapped in a `HandlerExecutionChain`, which contains the handler plus MVC interceptors that apply to that request. Interceptor `preHandle` callbacks run before the handler; post/after callbacks participate later according to the MVC lifecycle.

Ordering matters when several mappings could potentially handle a request. A mapping should claim only the requests it understands; broad custom mappings can otherwise shadow built-in MVC behavior.

</details>

- [Back to top](#back-to-top)

---

## <a id="handler-invocation-and-return-value-processing">HandlerAdapter, Handler Invocation, and Return-Value Processing</a>

<details>
<summary>Click for details</summary>

Finding a handler is not enough because MVC supports different handler shapes. `HandlerAdapter` decouples the `DispatcherServlet` from the details of invoking each handler type.

`RequestMappingHandlerAdapter` invokes annotated `HandlerMethod` instances. It coordinates argument resolvers, data binding, validation, controller invocation, and return-value handlers. Functional endpoints use their own handler-function adapter.

For an annotated method, the flow is conceptually:

```text
HandlerMethod
→ resolve method arguments
→ create/bind/validate model values when needed
→ invoke controller method
→ select return-value handler
→ produce ModelAndView or response-body outcome
```

This explains why adding a custom argument resolver or return-value handler changes MVC invocation without requiring a custom `DispatcherServlet`.

</details>

- [Back to top](#back-to-top)

---

## <a id="exception-resolution-position">Exception Resolution in the Dispatch Flow</a>

<details>
<summary>Click for details</summary>

Exceptions can occur during handler mapping and throughout handler execution, including argument resolution, binding, controller invocation, and return-value processing. For failures in that part of request processing, `DispatcherServlet` gives configured `HandlerExceptionResolver` implementations an ordered opportunity to translate the exception into an MVC response outcome.

Resolution is part of the same dispatch lifecycle, not an unrelated global `try/catch`. Annotation-based `@ExceptionHandler`, status-based mapping, and Framework default exception translation participate through resolver implementations.

If an exception is resolved, MVC can render the resulting model/view or write an error response. If no resolver handles it, the exception continues back to the Servlet container. An exception that occurs later while a view itself is being rendered is outside this handler-exception-resolution step and propagates through the remaining Servlet error path instead of being sent back through the resolver chain.

The practical debugging question is therefore: did the failure happen before a handler was selected, during controller processing, or after a response had begun? The answer affects what a resolver can still change.

</details>

- [Back to top](#back-to-top)

---

## <a id="dispatcher-strategies-overview">DispatcherServlet Strategy Model</a>

<details>
<summary>Click for details</summary>

`DispatcherServlet` is designed around strategy interfaces rather than one hard-coded pipeline. Core strategy families include handler mappings, handler adapters, exception resolvers, view resolvers, locale resolution, multipart resolution, flash-map management, and related request-processing collaborators.

The servlet initializes configured beans of these strategy types and falls back to Framework defaults where appropriate. This lets applications replace or extend one part without rewriting dispatch itself.

The strategy model has two important rules:

1. a strategy should own one well-defined stage;
2. ordering is part of behavior when a strategy family is a chain.

Avoid replacing low-level strategies when a higher-level extension point such as `WebMvcConfigurer`, an argument resolver, or an interceptor already solves the problem. Smaller extensions preserve more Framework defaults and are easier to upgrade.

</details>

- [Back to top](#back-to-top)
