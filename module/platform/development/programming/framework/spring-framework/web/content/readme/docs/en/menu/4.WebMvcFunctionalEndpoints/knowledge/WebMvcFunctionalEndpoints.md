<a id="back-to-top"></a>

# WebMvc.fn Functional Endpoints

## Menu
- [WebMvc.fn Mental Model](#webmvc-fn-mental-model)
- [Router Functions and Request Predicates](#router-functions-and-request-predicates)
- [Handler Functions with ServerRequest and ServerResponse](#handler-functions-request-response)
- [Handler Filters and Route Composition](#handler-filter-functions)
- [Annotated and Functional Endpoints Side by Side](#annotated-and-functional-coexistence)

## <a id="webmvc-fn-mental-model">WebMvc.fn Mental Model</a>

<details>
<summary>Click for details</summary>

WebMvc.fn is Spring MVC's functional programming model for Servlet environments. Instead of mapping annotations on controller methods, routing and handling are represented explicitly as Java functions and immutable-style request/response abstractions.

The core relationship is:

```text
RequestPredicate
→ RouterFunction
→ HandlerFunction
→ ServerResponse
```

`RouterFunction` decides which `HandlerFunction` should handle a `ServerRequest`; the handler returns a `ServerResponse`. Spring MVC connects this model to `DispatcherServlet` through functional handler mapping/adapter infrastructure.

This model is still Spring MVC. It uses Servlet request processing and can coexist with annotated controllers. "Functional" describes how routes and handlers are expressed, not a switch to WebFlux.

</details>

- [Back to top](#back-to-top)

---

## <a id="router-functions-and-request-predicates">Router Functions and Request Predicates</a>

<details>
<summary>Click for details</summary>

A `RequestPredicate` is a reusable test over a `ServerRequest`: HTTP method, path, headers, accepted content, and other request properties can participate. A `RouterFunction` evaluates predicates and returns a handler when a route matches.

Routes can be composed and nested, which makes shared path prefixes or request conditions explicit in code:

```java
RouterFunction<ServerResponse> routes() {
    return route()
        .GET("/users/{id}", this::findUser)
        .POST("/users", this::createUser)
        .build();
}
```

Ordering still matters. Broad predicates placed too early can shadow more specific routes. Keep route composition readable enough that a maintainer can answer "which handler wins?" without mentally executing a large functional expression.

</details>

- [Back to top](#back-to-top)

---

## <a id="handler-functions-request-response">Handler Functions with ServerRequest and ServerResponse</a>

<details>
<summary>Click for details</summary>

`HandlerFunction` is the functional equivalent of the application handler. It receives a `ServerRequest`, extracts the values it needs, invokes application logic, and returns a `ServerResponse`.

`ServerRequest` provides access to path variables, query parameters, headers, cookies, attributes, session, and body extraction. `ServerResponse` uses a builder-style API to express status, headers, content type, body, or rendering information.

Compared with annotated methods, parameter extraction is more explicit:

```text
annotated controller
→ resolver infers parameters from method signature

HandlerFunction
→ handler explicitly reads from ServerRequest
```

That explicitness can make route flow easier to compose, but it also means handlers should avoid repeating parsing logic that belongs in shared helpers or application services.

</details>

- [Back to top](#back-to-top)

---

## <a id="handler-filter-functions">Handler Filters and Route Composition</a>

<details>
<summary>Click for details</summary>

`HandlerFilterFunction` wraps a functional handler and can perform logic before and/or after that handler. It is useful for concerns scoped specifically to a functional route tree.

Filters can be composed with router functions, so a nested group of routes can share behavior without introducing a Servlet-wide filter or MVC-wide interceptor.

Choose the layer carefully:

```text
Servlet Filter
→ whole Servlet dispatch

HandlerInterceptor
→ mapped MVC handler lifecycle

HandlerFilterFunction
→ functional route/handler composition
```

Do not rebuild authentication/authorization policy in ad-hoc handler filters when Spring Security owns that concern. Route filters are best for local functional-handler behavior rather than a replacement for platform-wide infrastructure.

</details>

- [Back to top](#back-to-top)

---

## <a id="annotated-and-functional-coexistence">Annotated and Functional Endpoints Side by Side</a>

<details>
<summary>Click for details</summary>

Annotated controllers and WebMvc.fn can run side by side because each programming model has its own mapping/adapter support under the same `DispatcherServlet`.

This makes incremental adoption possible. A team can use annotated controllers for most HTTP APIs and functional routes for a compositional subsystem without migrating everything at once.

However, coexistence introduces route-ownership questions. Do not intentionally map the same HTTP contract in both models; ordering between handler mappings should not decide business behavior.

The choice is about expression and composition, not capability parity. Annotated MVC has especially rich method-argument/binding integration; functional MVC makes request extraction and route composition more explicit. Use the model whose trade-offs are clearest for the feature.

</details>

- [Back to top](#back-to-top)
