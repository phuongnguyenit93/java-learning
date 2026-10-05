<a id="back-to-top"></a>

# Servlet Web Support and Resource Delivery

## Menu
- [Multipart Request Resolution](#multipart-request-resolution)
- [Locale Resolution](#locale-resolution)
- [Static Resources, ResourceHandler, and the Resource Chain](#static-resources-and-resource-chain)
- [Conditional Requests, Cache-Control, ETag, and Last-Modified](#conditional-requests-and-http-caching)
- [Default Servlet Boundary](#default-servlet-boundary)

## <a id="multipart-request-resolution">Multipart Request Resolution</a>

<details>
<summary>Click for details</summary>

Multipart requests solve a specific transport problem: one HTTP request can carry normal form fields together with one or more binary parts. Spring MVC should not force controller code to parse MIME boundaries or manage temporary upload storage directly, so it delegates parsing to the Servlet container and exposes higher-level values such as `MultipartFile` and `Part`.

With the standard Servlet stack, `StandardServletMultipartResolver` is the normal Framework bridge. To let `DispatcherServlet` discover it, declare the resolver in the servlet application context under the conventional bean name `multipartResolver`. It detects a multipart request and wraps the original `HttpServletRequest` so MVC argument resolution and data binding can see multipart parts.

The resolver does **not** configure the Servlet container's multipart limits or storage policy. A `multipart-config` declaration or equivalent Servlet registration configuration still owns limits such as maximum file size, maximum request size, and temporary-file location. Because the resolver delegates to the container's Servlet multipart parser, exact parsing behavior can also vary across Servlet-container implementations; test the behavior required by the deployment platform.

The important separation is:

```text
Servlet container
→ parses multipart/form-data according to Servlet configuration
→ StandardServletMultipartResolver exposes parsed parts to Spring MVC
→ controller arguments consume MultipartFile / Part / ordinary form data
```

Controllers should validate size, media type, expected part names, and application-specific constraints. Treat an uploaded filename as untrusted input; do not concatenate it directly into a filesystem path. Large uploads also need operational limits because buffering or temporary-file usage can become a memory, disk, or denial-of-service problem.

Use multipart support when a single request genuinely needs mixed structured and binary data. For very large object transfer or resumable upload protocols, a dedicated storage/upload design may be a better boundary than routing everything through an MVC controller.

</details>

- [Back to top](#back-to-top)

---

## <a id="locale-resolution">Locale Resolution</a>

<details>
<summary>Click for details</summary>

Locale resolution answers a presentation question: which `Locale` should the current request use when formatting numbers, dates, messages, and other localized output? Spring MVC represents that decision through a `LocaleResolver` or the richer `LocaleContextResolver`.

Different resolver strategies encode different ownership. An accept-header strategy derives locale from the request headers; cookie- or session-based strategies keep application state chosen earlier. That difference matters because a request-derived locale is not the same thing as a mutable user preference.

The chosen locale participates in MVC infrastructure through the request context and is then available to formatters, message lookup, views, and controller code. A `LocaleChangeInterceptor` can interpret a request parameter as a locale-change request, but changing locale only makes sense when the configured resolver supports updating locale state. Do not assume every resolver is writable.

Keep locale resolution separate from business identity and authorization. A locale is normally a rendering preference, not proof of user country, legal jurisdiction, or trusted profile information.

</details>

- [Back to top](#back-to-top)

---

## <a id="static-resources-and-resource-chain">Static Resources, ResourceHandler, and the Resource Chain</a>

<details>
<summary>Click for details</summary>

Static resources such as JavaScript, CSS, images, and fonts do not need controller-method semantics, but they still benefit from Spring MVC routing, resource lookup, caching headers, and versioned URLs. Spring MVC serves them through a resource handler rather than by invoking an annotated controller.

`WebMvcConfigurer.addResourceHandlers` registers URL patterns and one or more resource locations. Requests are handled by `ResourceHttpRequestHandler`, which resolves a Spring `Resource`, checks request conditions, determines media type, and writes the resource response.

The optional resource chain adds ordered `ResourceResolver` and `ResourceTransformer` components. This is useful when delivery requires behaviors such as content-versioned filenames, encoded variants, WebJar resolution, or transformed references. A `VersionResourceResolver`, for example, can make cache-friendly versioned URLs part of the delivery contract instead of forcing application controllers to compute hashes.

The design rule is to keep **resource delivery** separate from application request handling:

```text
/api/orders/**  → application handler
/assets/**      → resource handler
```

Avoid adding controllers merely to stream classpath resources. Also avoid making every resource request pass through expensive application-specific logic unless there is a real requirement; static delivery is usually most effective when the request path, cache policy, and resource chain are predictable.

</details>

- [Back to top](#back-to-top)

---

## <a id="conditional-requests-and-http-caching">Conditional Requests, Cache-Control, ETag, and Last-Modified</a>

<details>
<summary>Click for details</summary>

HTTP caching works best when the server can tell the client both **how long a representation may be reused** and **whether an existing representation is still current**. Spring MVC exposes helpers for both concerns rather than requiring controllers to manipulate header strings manually.

`CacheControl` builds `Cache-Control` response directives. Validators such as `ETag` and `Last-Modified` support conditional requests. For conditional `GET` or `HEAD`, a matching validator can produce `304 Not Modified` without a response body. Conditional state-changing requests such as `POST`, `PUT`, or `DELETE` use precondition semantics instead and can produce `412 Precondition Failed` when the precondition is not satisfied.

Spring's web request utilities and resource handling can participate in these checks. The key lifecycle is:

```text
client precondition / validator
→ server compares current representation state
→ GET/HEAD unchanged: 304, no representation body
→ state-changing precondition failed: 412
→ otherwise: continue normal request processing
```

`Cache-Control` freshness and conditional validation are related but different. Freshness can avoid a request entirely; validators make a request cheap when revalidation is required. Do not use a long public cache lifetime for user-specific or sensitive responses, and do not treat an ETag as an authorization token.

For dynamic application responses, cache policy belongs to the HTTP representation contract. It is distinct from Spring's application Cache abstraction, which caches application data or computation and is owned by the dedicated cache module.

</details>

- [Back to top](#back-to-top)

---

## <a id="default-servlet-boundary">Default Servlet Boundary</a>

<details>
<summary>Click for details</summary>

Servlet containers normally have a "default" servlet that can serve resources the application itself does not handle. A `DispatcherServlet` mapped to `/` can otherwise become the first recipient for those paths, so Spring MVC offers a controlled handoff through `DefaultServletHttpRequestHandler`.

`WebMvcConfigurer.configureDefaultServletHandling` can enable this forwarding model. When no higher-priority MVC handler matches, the request can be forwarded to the container's named default servlet. This is a compatibility boundary, not a second MVC controller mechanism.

Use the handoff only when the deployment model actually relies on container-managed default resource handling. If Spring's own resource handlers already own static resources, adding default-servlet forwarding can make routing harder to reason about.

The important boundary is:

```text
Spring MVC handlers/resources
→ explicit Framework ownership

default servlet
→ Servlet-container ownership
```

Spring Boot may configure static resource handling and servlet registration conventions for an application, but those Boot defaults are outside this Framework-level section.

</details>

- [Back to top](#back-to-top)
