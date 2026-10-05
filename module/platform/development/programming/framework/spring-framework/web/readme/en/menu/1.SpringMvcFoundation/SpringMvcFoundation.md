<a id="back-to-top"></a>

# Spring MVC on the Servlet Stack

## Menu
- [Why Spring MVC Exists](#spring-mvc-purpose)
- [Servlet Request, Response, Container, and Thread Model](#servlet-request-response-model)
- [spring-web and spring-webmvc Responsibilities](#spring-web-and-spring-webmvc)
- [Spring Framework Web vs Spring Boot Web Auto-Configuration](#framework-vs-boot-web)
- [Spring Web Ownership and Neighboring-Module Boundaries](#spring-web-module-boundaries)

## <a id="spring-mvc-purpose">Why Spring MVC Exists</a>

<details>
<summary>Click for details</summary>

The Servlet API already gives Java applications access to HTTP requests and responses, but a large application needs more than a single servlet full of routing, parsing, validation, rendering, and error-handling code. Spring MVC exists to turn those recurring concerns into a composable request-processing pipeline.

Its central idea is **front-controller dispatch**: application code does not decide by hand which servlet handles every business route. A `DispatcherServlet` receives MVC requests and delegates to strategies for handler lookup, invocation, argument resolution, binding, validation, representation conversion, view rendering, and exception resolution.

That separation lets controllers focus on application-facing request semantics:

```text
HTTP request
→ framework routing/infrastructure
→ application handler
→ framework response processing
→ HTTP response
```

Spring MVC is useful because these policies remain consistent across many endpoints and can be extended independently. The goal is not "less HTTP"; it is to express HTTP application behavior without rewriting the same orchestration in every controller.

</details>

- [Back to top](#back-to-top)

---

## <a id="servlet-request-response-model">Servlet Request, Response, Container, and Thread Model</a>

<details>
<summary>Click for details</summary>

Spring MVC runs on the Servlet stack, so the Servlet container remains the owner of the low-level server request lifecycle. The container accepts a connection, creates `HttpServletRequest` and `HttpServletResponse` objects, selects the servlet/filter chain, and normally assigns a thread to process the dispatch.

Spring MVC begins **inside** that lifecycle. The `DispatcherServlet` receives the request after container-level filtering and coordinates Framework components. A normal synchronous MVC handler executes on the request-processing thread until it returns and the response is completed.

This baseline matters when reasoning about blocking work. If a controller performs a slow blocking call, the request thread stays occupied. MVC async processing can release the original request thread and resume later, but that is an extension of Servlet async processing rather than a different network runtime.

Keep these layers distinct:

```text
Servlet container
→ request/response objects, servlet dispatch, filter chain, async capability

Spring MVC
→ handler mapping/invocation, binding, rendering, errors, MVC extensions
```

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-web-and-spring-webmvc">spring-web and spring-webmvc Responsibilities</a>

<details>
<summary>Click for details</summary>

Spring Framework separates common web infrastructure from the Servlet-specific MVC framework.

`spring-web` contains broadly reusable web and HTTP abstractions: HTTP headers/status/media types, message converters, client-side HTTP support, web binding infrastructure, multipart abstractions, URI utilities, and other facilities shared across web stacks.

`spring-webmvc` builds the Servlet MVC programming model on top of those foundations. It contains `DispatcherServlet`, MVC handler mappings/adapters, annotated-controller infrastructure, view resolution, MVC interceptors, functional Servlet endpoints, and MVC configuration support.

The distinction helps explain why types such as `HttpMessageConverter` are useful both to MVC and synchronous clients while `HandlerInterceptor` is specifically part of the MVC server pipeline.

Do not treat artifact boundaries as the entire curriculum, but use them as a useful ownership signal:

```text
generic HTTP/web support → spring-web
Servlet MVC orchestration → spring-webmvc
```

</details>

- [Back to top](#back-to-top)

---

## <a id="framework-vs-boot-web">Spring Framework Web vs Spring Boot Web Auto-Configuration</a>

<details>
<summary>Click for details</summary>

Spring Framework defines the MVC contracts and infrastructure. Spring Boot decides how many of those pieces should be created and configured automatically for an application.

At Framework level, concepts such as `DispatcherServlet`, `@EnableWebMvc`, `WebMvcConfigurer`, `HttpMessageConverter`, `ViewResolver`, and handler mappings have explicit contracts. A Framework-only application is responsible for registering the Servlet and configuring the application context appropriately.

Spring Boot can detect a web application, configure an embedded Servlet container, register/configure MVC infrastructure, apply `spring.mvc.*` and related properties, and add sensible defaults when application beans do not override them.

Therefore:

```text
Spring Framework
→ capability and extension contracts

Spring Boot
→ conditional auto-configuration and application conventions
```

When learning MVC mechanics, reason from the Framework contract first. Boot convenience should explain why less configuration appears in an application, not replace the underlying MVC mental model.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-web-module-boundaries">Spring Web Ownership and Neighboring-Module Boundaries</a>

<details>
<summary>Click for details</summary>

This module owns Servlet-stack Spring MVC and synchronous/common Spring HTTP client mechanics. Several adjacent topics intentionally have different primary owners.

- reusable `DataBinder`, `Validator`, conversion, and formatting foundations → Validation/Data Binding;
- WebFlux, Reactor, reactive codecs, and deep `WebClient` mechanics → Spring Reactive;
- WebSocket, STOMP, and Spring Messaging → Messaging;
- authentication, authorization, CSRF, and `SecurityFilterChain` → Spring Security;
- `MockMvc`, TestContext, and framework-level web testing → Spring Testing;
- Boot MVC auto-configuration and embedded-server conventions → Spring Boot;
- service-to-service HTTP architecture, client selection, and resilience policy → Integration HTTP.

These boundaries keep the learning story coherent. This module may mention a neighboring concept where the MVC integration point matters, but it should not duplicate the neighbor's full mental model.

</details>

- [Back to top](#back-to-top)
