# Spring Framework Web

This module teaches the **Servlet-stack web capabilities of Spring Framework**, centered on Spring MVC, together with Spring's synchronous HTTP client infrastructure.

The goal is not to memorize controller annotations. The module builds an end-to-end mental model for:

```text
Servlet request
→ DispatcherServlet
→ handler selection and invocation
→ binding / validation integration
→ representation or view rendering
→ exception resolution
→ response completion
```

and then connects that server-side model with Spring's synchronous outbound HTTP clients and declarative HTTP service interfaces.

## Prerequisites

Learners should already understand:

- Java classes, interfaces, annotations, exceptions, and generics;
- basic HTTP request/response concepts;
- the Spring IoC container and ApplicationContext model;
- basic awareness of the Servlet container and its request/response model.

The reusable foundations of `Validator`, `DataBinder`, `ConversionService`, and formatting belong to the dedicated validation/data-binding module. This module focuses on how Spring MVC **uses** those facilities.

## Learning flow

```text
Spring MVC on the Servlet stack
        ↓
DispatcherServlet request lifecycle
        ↓
Annotated controllers + WebMvc.fn
        ↓
MVC binding and validation integration
        ↓
HTTP message conversion + view rendering
        ↓
Exception resolution + problem responses
        ↓
Async MVC + streaming
        ↓
Filters / interceptors / CORS / web support
        ↓
MVC configuration + extension points
        ↓
RestClient / RestTemplate
        ↓
HTTP Service Interfaces
        ↓
Spring Web synthesis and boundaries
```

## Module boundaries

This module owns deep Spring Framework mechanics for:

- Spring MVC and the `DispatcherServlet` pipeline;
- annotated and functional Servlet endpoints;
- MVC-specific binding and validation integration;
- `HttpMessageConverter`, content negotiation, views, redirects, and resource delivery;
- MVC exception handling and problem responses;
- Servlet async processing and streaming;
- filters, interceptors, CORS, forwarded headers, multipart, locale, and HTTP caching integration;
- MVC configuration and extension points;
- `RestClient`, `RestTemplate`, and HTTP Service Interfaces at the Framework mechanics level.

It intentionally hands off:

- WebFlux and deep `WebClient` mechanics → Spring Reactive;
- WebSocket, STOMP, and Spring Messaging → Spring Messaging;
- reusable validation/data-binding foundations → Validation/Data Binding;
- MockMvc, TestContext, and framework-level web testing → Spring Testing;
- authentication, authorization, SecurityFilterChain, CSRF, and security policy → Spring Security;
- MVC auto-configuration and embedded-server behavior → Spring Boot;
- HTTP protocol theory, cross-client selection, resilience, and service-to-service architecture → Integration HTTP.

## Expected outcome

After completing the module, the learner should be able to trace Spring MVC requests end to end, reason about where each extension point participates, distinguish synchronous and asynchronous Servlet processing from WebFlux, and use Spring's synchronous HTTP client abstractions without confusing Framework mechanics with neighboring module responsibilities.
