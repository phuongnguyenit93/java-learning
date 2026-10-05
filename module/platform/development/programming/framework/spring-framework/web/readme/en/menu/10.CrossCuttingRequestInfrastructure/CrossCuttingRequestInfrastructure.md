<a id="back-to-top"></a>

# Filters, Interceptors, CORS, and Request Infrastructure

## Menu
- [Servlet Filters, HandlerInterceptors, and Their Placement](#filters-interceptors-and-placement)
- [Spring MVC CORS Processing](#cors-processing)
- [Forwarded Headers](#forwarded-headers)
- [Cross-Cutting Components Across Async Dispatch](#async-dispatch-cross-cutting)
- [Spring Security Boundary](#security-boundary)

## <a id="filters-interceptors-and-placement">Servlet Filters, HandlerInterceptors, and Their Placement</a>

<details>
<summary>Click for details</summary>

Servlet `Filter` and Spring MVC `HandlerInterceptor` both wrap request processing, but they operate at different layers.

A filter belongs to the Servlet chain. It can act before the `DispatcherServlet`, wrap request/response objects, and participate in dispatches that are not MVC controller invocations.

An interceptor belongs to MVC after a handler has been mapped. It can inspect the selected handler and run before/after handler execution.

```text
Filter
→ Servlet-level concern

HandlerInterceptor
→ MVC-handler-level concern
```

Choose the lowest layer that still has the context you need. Request encoding or generic servlet wrapping fits filters; handler-specific timing or locale changes can fit interceptors.

Interceptors are not the right replacement for a security framework because path matching and lifecycle coverage differ from dedicated security infrastructure.

</details>

- [Back to top](#back-to-top)

---

## <a id="cors-processing">Spring MVC CORS Processing</a>

<details>
<summary>Click for details</summary>

CORS controls whether browser code from one origin may make a cross-origin request to another origin. Browsers may send a preflight `OPTIONS` request before the actual request when the operation is not a simple CORS request.

Spring MVC supports local `@CrossOrigin` declarations and global CORS mappings. Handler mappings combine applicable CORS configuration and participate in preflight/actual request handling.

CORS policy should explicitly define allowed origins, methods, headers, exposed headers, credentials, and cache duration as needed. A permissive wildcard policy is not a substitute for understanding which browser origins should call the API.

CORS is a browser security interaction, not authentication. A request allowed by CORS can still be unauthorized, and a non-browser client is not constrained by the browser's CORS enforcement.

When Spring Security is present, CORS processing must integrate with its filter chain rather than being reimplemented in MVC code.

</details>

- [Back to top](#back-to-top)

---

## <a id="forwarded-headers">Forwarded Headers</a>

<details>
<summary>Click for details</summary>

Applications behind reverse proxies often receive an internal scheme/host/port while the public client used different values. Standard `Forwarded` and common `X-Forwarded-*` headers can describe the client-facing request.

`ForwardedHeaderFilter` can adapt the Servlet request so downstream code sees forwarded scheme, host, port, and related values, or it can remove such headers.

This is a **trust boundary**. An internet client can forge forwarding headers unless the edge proxy removes untrusted values and writes trusted ones. Application code should not blindly accept them merely because a header exists.

Correct forwarded-header handling matters for redirects, absolute URI generation, security decisions based on scheme, and links returned to clients.

Prefer one clearly owned proxy/header strategy across the deployment instead of mixing container-level and application-level rewriting without understanding precedence.

</details>

- [Back to top](#back-to-top)

---

## <a id="async-dispatch-cross-cutting">Cross-Cutting Components Across Async Dispatch</a>

<details>
<summary>Click for details</summary>

Async MVC means one logical request may pass through more than one Servlet dispatch. Filters and interceptors therefore need lifecycle-aware behavior.

A filter can be mapped for `REQUEST`, `ASYNC`, `ERROR`, and other dispatcher types. `OncePerRequestFilter` provides hooks for deciding whether async or error redispatches should be filtered.

MVC interceptors can participate again when an async request is redispatched. `AsyncHandlerInterceptor` adds a callback for the moment concurrent handling starts, before the original request thread exits.

Code that records timing, opens resources, or stores thread-local state must account for this split lifecycle. "after the original handler thread returns" is not necessarily "the HTTP request is complete".

For correlation/logging context, propagate only what later threads need and ensure cleanup happens for every completion/error path.

</details>

- [Back to top](#back-to-top)

---

## <a id="security-boundary">Spring Security Boundary</a>

<details>
<summary>Click for details</summary>

Authentication, authorization, CSRF protection, security context persistence, and request firewalling belong to Spring Security, which primarily integrates through the Servlet filter chain.

An MVC interceptor sees a mapped handler and may be useful for application-specific, non-security metadata, but it is not a complete security boundary. Static resources, error dispatches, path normalization, and handler-mapping differences can create gaps if authorization is improvised in interceptor code.

Likewise, CORS and security interact but solve different problems. CORS tells browsers which cross-origin requests are permitted; Spring Security decides whether the request is authenticated/authorized and applies protections such as CSRF where relevant.

Keep MVC aware of the authenticated principal when needed, but keep security policy in the Spring Security owner rather than duplicating it in controllers/interceptors.

</details>

- [Back to top](#back-to-top)
