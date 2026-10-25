<a id="back-to-top"></a>

# RestClient, RestTemplate, and Shared Client Infrastructure

## Menu
- [Spring Synchronous HTTP Client Model](#synchronous-client-model)
- [RestClient in Spring Framework 6.1](#rest-client-6-1)
- [RestTemplate](#rest-template)
- [ClientHttpRequestFactory and Underlying HTTP Libraries](#client-http-request-factory)
- [Client Request Customization: Interceptors, Initializers, and URI Handling](#client-request-customization)
- [Client-Side Message Conversion and Body Handling](#client-message-conversion-and-bodies)
- [Status and Error Handling](#status-and-error-handling)
- [Integration HTTP Client-Selection Boundary](#integration-http-selection-boundary)

## <a id="synchronous-client-model">Spring Synchronous HTTP Client Model</a>

<details>
<summary>Click for details</summary>

Spring's synchronous HTTP clients provide a blocking request/response model over an underlying HTTP library. The caller thread prepares a request, performs I/O, waits for the response, decodes it, and resumes with a value or exception. The Framework adds a consistent layer for URI expansion, headers, message conversion, interceptors, request factories, and error handling.

The two main synchronous APIs in Spring Framework 6.1 are `RestClient` and `RestTemplate`. They share much of the same lower-level infrastructure but expose different programming styles:

```text
RestClient
→ fluent request specification

RestTemplate
→ template-method operations
```

Neither model makes remote calls cheap or local. Connection pools, DNS, TLS, timeouts, server latency, and partial failures still exist below the Spring abstraction. A synchronous client is therefore a convenient API, not a resilience strategy.

This chapter owns the Framework mechanics of these clients. Choosing between synchronous and reactive clients across a distributed system, designing retries/circuit breakers, and service-to-service architecture belong to the Integration HTTP layer.

</details>

- [Back to top](#back-to-top)

---

## <a id="rest-client-6-1">RestClient in Spring Framework 6.1</a>

<details>
<summary>Click for details</summary>

`RestClient` was introduced in Spring Framework 6.1 as a fluent synchronous HTTP client. It lets application code build a request step by step and then choose how to extract the response.

A typical flow is:

```java
User user = restClient.get()
    .uri("/users/{id}", 42)
    .accept(MediaType.APPLICATION_JSON)
    .retrieve()
    .body(User.class);
```

`retrieve()` is the common high-level path. It applies status handling and then decodes the response body or creates a `ResponseEntity`. By default, 4xx and 5xx responses are converted to Spring web-client exceptions. Per-response `onStatus` handlers or builder-level default status handlers can customize that policy.

`exchange(...)` is the lower-level escape hatch for cases where response status, headers, and body must be handled together under custom logic. Status handlers registered for `retrieve()` are not applied to `exchange()`; the exchange callback owns the error/status decision itself. It should therefore not be treated as merely another spelling of `retrieve()`.

`RestClient` can be created directly or built from an existing `RestTemplate` configuration. This makes migration or configuration sharing possible without pretending the two APIs have identical call shapes.

</details>

- [Back to top](#back-to-top)

---

## <a id="rest-template">RestTemplate</a>

<details>
<summary>Click for details</summary>

`RestTemplate` is the established synchronous template-style client in Spring Framework. Its API groups common HTTP operations into methods such as `getForObject`, `postForEntity`, `exchange`, and `execute`.

The template model centralizes the repetitive mechanics:

```text
prepare request
→ apply request callbacks/interceptors
→ execute through ClientHttpRequestFactory
→ inspect status
→ decode through HttpMessageConverter
```

`RestTemplate` is **not deprecated in Spring Framework 6.1**. `RestClient` offers a more fluent synchronous API and is a natural choice for new code, but existing applications can continue to use `RestTemplate` where its semantics and integrations are well understood.

The important design concern is shared mutable configuration. Configure a `RestTemplate` before concurrent use rather than changing request factories, converters, interceptors, or error handlers while requests are in flight.

</details>

- [Back to top](#back-to-top)

---

## <a id="client-http-request-factory">ClientHttpRequestFactory and Underlying HTTP Libraries</a>

<details>
<summary>Click for details</summary>

`ClientHttpRequestFactory` is the bridge between Spring's client API and the actual HTTP implementation. It creates `ClientHttpRequest` instances that perform the network operation. Different factories can delegate to the JDK HTTP stack or other supported client libraries.

That boundary matters because transport behavior is not fully defined by `RestClient` or `RestTemplate`. Connection pooling, proxy support, TLS configuration, low-level timeouts, HTTP protocol versions, and native request options depend on the selected factory and underlying library.

Choose the factory for operational requirements, not just because a class is available on the classpath. For example, a high-throughput service normally needs deliberate connection-pool and timeout settings; a simple command-line tool may not.

Keep the abstraction boundary clear:

```text
RestClient / RestTemplate
→ Spring request/response semantics

ClientHttpRequestFactory
→ transport adapter

underlying HTTP library
→ sockets, pooling, TLS, protocol implementation
```

</details>

- [Back to top](#back-to-top)

---

## <a id="client-request-customization">Client Request Customization: Interceptors, Initializers, and URI Handling</a>

<details>
<summary>Click for details</summary>

Client requests often need cross-cutting customization such as correlation headers, authentication material, logging metadata, or common URI behavior. Spring exposes those concerns before the request reaches the transport.

`ClientHttpRequestInterceptor` wraps execution and can inspect or modify a request before delegating. Interceptors form a chain, so ordering matters. An interceptor that reads a response body for logging, for example, may change what later code can consume unless buffering is deliberately configured.

Request initializers customize the concrete request before execution, while a `UriBuilderFactory` centralizes base URLs, URI templates, and encoding behavior. `RestClient.Builder` also supports defaults such as headers, request customizers, and interceptors.

Do not put endpoint business logic into an interceptor. Good interceptor concerns are orthogonal to the operation being called. Also avoid automatically retrying non-idempotent requests inside a generic interceptor unless the application has explicitly defined retry safety.

</details>

- [Back to top](#back-to-top)

---

## <a id="client-message-conversion-and-bodies">Client-Side Message Conversion and Body Handling</a>

<details>
<summary>Click for details</summary>

Synchronous clients use `HttpMessageConverter` for body serialization and deserialization, just as Spring MVC uses converters on the server side. The converter is selected from the Java target/source type together with the request or response media type.

This gives a useful symmetry:

```text
client Java value
→ HttpMessageConverter
→ HTTP body

HTTP body
→ HttpMessageConverter
→ client Java value
```

Headers such as `Content-Type` describe the body being sent, while `Accept` expresses representations the client is willing to receive. A converter can only help when its supported media types and Java types match the exchange.

For generic response types, use APIs that preserve type information, such as `ParameterizedTypeReference<List<User>>`, rather than expecting runtime reflection to recover erased generic parameters from `List.class`.

Keep converter customization narrow. Replacing the whole converter list just to add one custom format can accidentally remove JSON, string, form, resource, or byte-array support expected elsewhere.

</details>

- [Back to top](#back-to-top)

---

## <a id="status-and-error-handling">Status and Error Handling</a>

<details>
<summary>Click for details</summary>

An HTTP response is not successful merely because bytes arrived. Client code needs an explicit policy for status codes, transport failures, decoding failures, and response bodies that carry error details.

With `RestClient.retrieve()`, 4xx responses normally raise an `HttpClientErrorException` and 5xx responses an `HttpServerErrorException`. `onStatus` can define response-specific behavior, while builder-level default status handlers provide shared policy. `RestTemplate` uses a `ResponseErrorHandler` for its equivalent error-policy hook.

These exceptions are different from low-level I/O errors: an HTTP 500 is a valid HTTP response with an error status, while a connect timeout may mean no response was received at all. Applications should preserve that distinction for logging, retry decisions, and observability.

Do not reduce all remote failures to `RuntimeException("call failed")`. Preserve status, headers, and useful response details when they are safe to retain. Conversely, do not blindly log entire error bodies because they may contain credentials or personal data.

</details>

- [Back to top](#back-to-top)

---

## <a id="integration-http-selection-boundary">Integration HTTP Client-Selection Boundary</a>

<details>
<summary>Click for details</summary>

This module teaches how Spring Framework performs a synchronous HTTP exchange. It does not own the broader architecture decision of **which client style a system should standardize on** or how remote-call resilience should be designed.

Those choices depend on workload and system context:

- blocking thread-per-request versus reactive execution;
- service discovery and load balancing;
- authentication propagation;
- retry and circuit-breaking policy;
- observability standards;
- idempotency and timeout budgets.

The Framework client APIs are building blocks used by such designs. The Integration HTTP curriculum owns cross-client comparison and service-to-service policy; Spring Reactive owns deep `WebClient` mechanics.

The practical rule is to understand the mechanics here first, then make the architecture choice at the layer that sees the whole distributed interaction.

</details>

- [Back to top](#back-to-top)
