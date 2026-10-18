<a id="back-to-top"></a>

# MockMvc and Servlet Web Testing

## Menu
- [MockMvc purpose and execution model](#mockmvc-purpose-and-model)
- [Servlet API mock objects](#servlet-api-mocks)
- [Standalone versus WebApplicationContext setup](#mockmvc-setup-strategies)
- [Building requests and dispatching through Spring MVC](#request-building-and-dispatch)
- [Response assertions and result handling](#response-assertions)
- [Binding, validation, filters, and exception handling](#mvc-behavior-under-test)
- [Testing asynchronous MVC requests](#async-request-testing)
- [MockMvc limits and when a live server is required](#mockmvc-limitations-and-live-server)

## <a id="mockmvc-purpose-and-model">MockMvc purpose and execution model</a>

<details>
<summary>Click for details</summary>

`MockMvc` is Spring Framework's server-side test entry point for the Servlet-based Spring MVC stack. It drives the same `DispatcherServlet` request-processing machinery that an MVC application uses, but supplies mock Servlet request/response objects instead of starting a Servlet container and opening a network port.

That position makes MockMvc stronger than calling a controller method directly. A direct call can verify controller Java logic, while MockMvc can also exercise request mapping, argument resolution, data binding, conversion, validation, message conversion, interceptors, exception handling, view/model processing, and other MVC infrastructure that participates in the configured request path.

MockMvc still remains a test harness around Spring MVC. Deep `DispatcherServlet`, handler-mapping, binding, filter, and MVC exception semantics belong to the spring-framework/web module; this chapter focuses on how testing infrastructure invokes and observes them.

### References

- [Spring Framework 6.1.14 API — MockMvc](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/web/servlet/MockMvc.html)
- [Spring Framework 6.1 Reference — MockMvc](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="servlet-api-mocks">Servlet API mock objects</a>

<details>
<summary>Click for details</summary>

The `spring-test` module provides mock implementations of important Servlet API types, including request, response, session, and servlet-context objects. MockMvc builds its request-processing model on these objects, so Spring MVC code sees familiar Servlet contracts while the test remains entirely in-process.

The mocks are also useful outside MockMvc for focused tests of code that depends directly on Servlet APIs. They let a test construct headers, parameters, attributes, sessions, cookies, and response state without booting a real container.

They should be read as controllable test doubles, not as a complete Servlet container. Container startup, connector behavior, TLS, real network I/O, deployment descriptors, container-specific defaults, and low-level protocol handling are outside what the mocks prove.

### References

- [Spring Framework 6.1.14 API — org.springframework.mock.web](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/mock/web/package-summary.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="mockmvc-setup-strategies">Standalone versus WebApplicationContext setup</a>

<details>
<summary>Click for details</summary>

MockMvc supports two main setup styles. `standaloneSetup(...)` starts from one or more controller instances and builds enough MVC infrastructure around them for focused tests. Dependencies can be injected directly into the controller, while controller advice, validators, conversion, or other MVC components are registered explicitly when the scenario needs them.

`webAppContextSetup(...)` starts from a `WebApplicationContext` loaded by the TestContext framework. MockMvc then exercises the application's actual Spring MVC configuration from that context. This gives stronger evidence for component scanning, MVC configuration, configured advice, converters, interceptors, filters registered with the builder, and other context-level wiring.

The choice is about evidence. Standalone setup is fast and explicit when the target is one controller contract. WebApplicationContext setup is better when the test must prove that real MVC configuration and bean wiring work together. Context caching can keep the latter practical across many tests that share the same configuration.

### References

- [Spring Framework 6.1 Reference — MockMvc Setup Options](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="request-building-and-dispatch">Building requests and dispatching through Spring MVC</a>

<details>
<summary>Click for details</summary>

`MockMvcRequestBuilders` creates requests such as GET, POST, PUT, DELETE, multipart, and generic HTTP requests. The builder can set the URI, query parameters, form parameters, headers, cookies, session state, request attributes, content type, locale, body content, and security- or application-specific attributes that the configured MVC stack understands.

`mockMvc.perform(...)` passes the built request into MockMvc. From there the request goes through Spring MVC's dispatch path and produces an `MvcResult` containing the mock request/response plus model, view, exception, interceptor, and asynchronous state when applicable.

Keep request construction faithful to the contract being tested. A parameter added as a form parameter is not the same evidence as a JSON body decoded by an `HttpMessageConverter`. Likewise, manually placing a Java object in a request attribute does not prove that normal HTTP binding can construct that object.

</details>

- [Back to top](#back-to-top)

---

## <a id="response-assertions">Response assertions and result handling</a>

<details>
<summary>Click for details</summary>

After `perform(...)`, MockMvc exposes fluent result actions. Built-in `MockMvcResultMatchers` can assert status, headers, cookies, body content, JSON or XML paths, model values, views, flash attributes, request state, and forwarded/redirected URLs.

If the scenario needs to assert the exception resolved by Spring MVC, inspect `MvcResult.getResolvedException()` after `andReturn()` or use a custom `ResultMatcher`/lambda. Spring Framework 6.1.14 does not provide a built-in `MockMvcResultMatchers` exception matcher factory.

`andExpect(...)` adds assertions to the current result, while `andExpectAll(...)` groups several expectations and reports them together. `andDo(...)` attaches result handlers such as printing diagnostics, and `andReturn()` exposes the `MvcResult` when later processing needs direct access. Builders can also define expectations or handlers that apply to every request.

Prefer assertions that express the public behavior the test promises to clients. Model or handler details are useful when they are themselves part of the server-side contract, but overly internal assertions can make tests fragile without proving more useful behavior.

</details>

- [Back to top](#back-to-top)

---

## <a id="mvc-behavior-under-test">Binding, validation, filters, and exception handling</a>

<details>
<summary>Click for details</summary>

Because MockMvc dispatches through Spring MVC, it can verify interactions that a direct controller call skips. A request can be matched to the correct handler, converted into method arguments, bound into command objects, validated, passed through configured interceptors/filters, handled by `@ExceptionHandler` or `@ControllerAdvice`, and rendered or serialized into the final response.

The setup style determines how much of that infrastructure is real. With `webAppContextSetup`, the test sees the MVC infrastructure and application beans loaded in the `WebApplicationContext`. With standalone setup, the test author must register the pieces needed by the scenario; forgetting a custom validator or advice can make the test pass against a configuration that differs from production.

Filters can be added to MockMvc and tested around the dispatcher, but filter semantics owned by another subsystem stay with that subsystem. For example, Spring Security's authentication/authorization filter-chain behavior belongs to Spring Security even though MockMvc can integrate with it. This module teaches the MockMvc testing boundary.

</details>

- [Back to top](#back-to-top)

---

## <a id="async-request-testing">Testing asynchronous MVC requests</a>

<details>
<summary>Click for details</summary>

Spring MVC can release the original Servlet thread and complete request processing later through asynchronous return types and Servlet async processing. MockMvc represents that as two phases instead of pretending the final response exists immediately.

The first `perform(...)` can assert `request().asyncStarted()` and return an `MvcResult`. Once the asynchronous result is available, the test calls `asyncDispatch(mvcResult)` and performs normal status, header, body, or model assertions against the resumed dispatch. If the resumed dispatch resolves an exception, inspect `MvcResult.getResolvedException()` or use a custom `ResultMatcher`, just as in a synchronous MockMvc exchange.

```java
MvcResult result = mockMvc.perform(get("/report"))
        .andExpect(request().asyncStarted())
        .andReturn();

mockMvc.perform(asyncDispatch(result))
        .andExpect(status().isOk());
```

This verifies Spring MVC's async dispatch contract inside the mock Servlet environment. Thread-pool sizing, real container connector behavior, network disconnects, and production load characteristics require evidence outside MockMvc.

### References

- [Spring Framework 6.1 Reference — MockMvc Async Requests](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="mockmvc-limitations-and-live-server">MockMvc limits and when a live server is required</a>

<details>
<summary>Click for details</summary>

MockMvc gives strong evidence for Spring MVC request handling without a server, but the absence of a real server defines its limit. It does not open a socket or exercise an actual Servlet container connector, TCP/TLS behavior, real HTTP framing, deployment/runtime port configuration, container-specific startup, or behavior that exists only outside the Spring MVC dispatcher path.

Use a live-server test when the behavior under test depends on that missing boundary: actual HTTP client/server communication, production server configuration, transport-level headers or encoding, container integration, cross-process behavior, or infrastructure that is installed only in the deployed runtime.

Do not escalate every MVC test to a live server. Mapping, binding, validation, controller advice, response serialization, and most dispatcher behavior are usually cheaper and easier to diagnose with MockMvc. A healthy suite uses MockMvc for MVC evidence and reserves live-server tests for behavior that the mock environment cannot prove.

</details>

- [Back to top](#back-to-top)
