<a id="back-to-top"></a>

# HTTP Service Interfaces and Client Adapters

## Menu
- [HTTP Interface and @HttpExchange Contract](#http-exchange-contract)
- [HttpServiceProxyFactory](#http-service-proxy-factory)
- [HttpExchangeAdapter Model](#http-exchange-adapter-model)
- [RestClientAdapter and RestTemplateAdapter](#synchronous-http-client-adapters)
- [Service Method Arguments and Return Values](#service-method-arguments-and-return-values)
- [Reactive Adapter and WebClient Boundary](#reactive-adapter-boundary)

## <a id="http-exchange-contract">HTTP Interface and @HttpExchange Contract</a>

<details>
<summary>Click for details</summary>

An HTTP Service Interface lets application code describe an HTTP contract as a Java interface. `@HttpExchange` is the core annotation. It can be declared at interface and method level, with more specific method metadata refining the shared contract. The same interface contract can also be implemented by an MVC `@Controller` for server handling; this chapter focuses on the **client-proxy** use case because that is the ownership of this roadmap milestone.

The interface expresses HTTP intent through annotations and method signatures:

```java
@HttpExchange("/users")
interface UserService {
    @GetExchange("/{id}")
    User find(@PathVariable long id);
}
```

For the client-proxy use case, Spring inspects the method, resolves request values from arguments, builds an HTTP request, delegates execution to an adapter, then converts the response to the declared return type.

Using the interface as a client proxy does not provide service discovery, retry, or RPC transparency. Network latency and HTTP failure semantics remain. Keep the interface aligned with the remote HTTP contract rather than making it look like an arbitrary in-process repository.

</details>

- [Back to top](#back-to-top)

---

## <a id="http-service-proxy-factory">HttpServiceProxyFactory</a>

<details>
<summary>Click for details</summary>

`HttpServiceProxyFactory` creates the runtime proxy that implements an HTTP Service Interface. In Spring Framework 6.1, configure it with an `HttpExchangeAdapter`, for example through `HttpServiceProxyFactory.builderFor(adapter)` or the builder's `exchangeAdapter(...)` method, then call `createClient(MyService.class)` to produce the proxy.

The `HttpExchangeAdapter` path is the 6.1 direction; the older `HttpClientAdapter`-based builder method is deprecated in 6.1. The factory delegates actual exchanges rather than owning one specific HTTP client, keeping the interface model independent from `RestClient`, `RestTemplate`, or a reactive client.

The factory also owns method-level concerns such as custom argument resolvers and conversion support. It should normally be constructed once as infrastructure and used to create stable client proxies.

Do not create a new proxy factory for every request. The proxy is metadata-driven infrastructure; per-call data belongs in service method arguments.

</details>

- [Back to top](#back-to-top)

---

## <a id="http-exchange-adapter-model">HttpExchangeAdapter Model</a>

<details>
<summary>Click for details</summary>

`HttpExchangeAdapter` is the execution abstraction between the declarative proxy and a concrete HTTP client. The proxy turns an annotated method invocation into `HttpRequestValues`; the adapter knows how to perform that request and return headers, bodies, or `ResponseEntity` forms.

That separation is what makes the same interface model usable with different client implementations:

```text
@HttpExchange interface method
→ HttpServiceProxyFactory
→ HttpRequestValues
→ HttpExchangeAdapter
→ concrete HTTP client
```

The adapter contract is deliberately lower-level than business code. Application methods should not normally manipulate it directly. It exists so proxy infrastructure can depend on a stable exchange model rather than the fluent API of one client.

</details>

- [Back to top](#back-to-top)

---

## <a id="synchronous-http-client-adapters">RestClientAdapter and RestTemplateAdapter</a>

<details>
<summary>Click for details</summary>

Spring Framework 6.1 provides synchronous `HttpExchangeAdapter` implementations for both major synchronous clients:

- `RestClientAdapter` delegates to a configured `RestClient`;
- `RestTemplateAdapter` delegates to a configured `RestTemplate`.

Both are created from an already configured client. That means the proxy inherits important client behavior such as request factory, converters, interceptors, base URI strategy, and error policy from that client.

This composition is useful during migration: an application can adopt HTTP Service Interfaces while retaining a mature `RestTemplate` setup, or use `RestClient` for the same interface model.

Avoid configuring the proxy and underlying client with contradictory policies. The adapter is not a second place to duplicate every transport option; it is the bridge between the declarative contract and the client that already owns those options.

</details>

- [Back to top](#back-to-top)

---

## <a id="service-method-arguments-and-return-values">Service Method Arguments and Return Values</a>

<details>
<summary>Click for details</summary>

HTTP Service method arguments are resolved into parts of the request. Common contracts include path variables, request parameters, headers, cookies, request bodies, URI values, and HTTP method-related metadata. The exact supported argument set comes from the registered service argument resolvers.

Return types tell the proxy how much of the response the caller wants. A method may request only the decoded body, or expose status and headers through a `ResponseEntity`-style result. Generic body types need preserved type information so conversion can decode them correctly.

Keep service methods small and transport-shaped. A method with many unrelated optional parameters often signals that the remote HTTP contract itself is difficult to understand.

Also remember that Java signature convenience does not remove HTTP semantics. A method returning `User` can still fail with a network error, an HTTP error response, or a conversion error before any `User` exists.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-adapter-boundary">Reactive Adapter and WebClient Boundary</a>

<details>
<summary>Click for details</summary>

HTTP Service Interfaces are not limited conceptually to blocking execution. Spring's `ReactorHttpExchangeAdapter` contract is the reactive specialization of `HttpExchangeAdapter`; a `WebClient` adapter can therefore support reactive return types in addition to the synchronous return shapes.

This module stops at the boundary: it explains that the declarative interface is independent from a specific client and that the adapter determines execution behavior. Deep `WebClient`, Reactor, backpressure, event-loop behavior, and reactive error composition belong to the Spring Reactive module.

Do not declare reactive return types on a synchronous adapter and assume the proxy magically becomes non-blocking. Execution capabilities come from the selected adapter and client.

The clean mental model is:

```text
HTTP Service Interface
→ declaration model shared across client styles

synchronous adapter
→ this module

WebClient/reactive execution
→ Spring Reactive
```

</details>

- [Back to top](#back-to-top)
