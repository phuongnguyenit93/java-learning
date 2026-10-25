<a id="back-to-top"></a>

# WebTestClient and Client-side HTTP Testing

## Menu
- [WebTestClient purpose and client model](#webtestclient-purpose-and-model)
- [Binding WebTestClient to WebFlux targets](#webflux-binding-modes)
- [Binding WebTestClient to MockMvc](#mockmvc-webtestclient-binding)
- [Binding to a live HTTP server](#live-server-binding)
- [Response assertions and server-side assertions](#webtestclient-assertions)
- [Testing HTTP client collaborators with MockRestServiceServer](#mockrestservice-server)
- [Mock server versus live integration evidence](#mock-server-vs-live-integration)

## <a id="webtestclient-purpose-and-model">WebTestClient purpose and client model</a>

<details>
<summary>Click for details</summary>

`WebTestClient` is a testing client with a fluent request API and response assertions. It uses the `WebClient` infrastructure internally, but it is designed for tests: the same client-facing style can target a WebFlux application without a live server, a Spring MVC application through MockMvc, or an actual HTTP server.

That flexibility is useful because the test API can remain familiar while the evidence changes underneath it. A mock binding proves framework request handling in-process; a live-server binding crosses the network boundary. The test should therefore state which binding it uses instead of treating every `WebTestClient` test as equivalent.

This chapter owns the testing model. Deep WebFlux dispatch and `WebClient` mechanics belong to the reactive module, deep Spring MVC mechanics belong to the web module, and cross-client application choice belongs to the HTTP integration owner.

### References

- [Spring Framework 6.1.14 API — WebTestClient](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/web/reactive/server/WebTestClient.html)
- [Spring Framework 6.1 Reference — WebTestClient](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-binding-modes">Binding WebTestClient to WebFlux targets</a>

<details>
<summary>Click for details</summary>

For WebFlux tests, `WebTestClient` can bind directly to controller instances, a `RouterFunction`, or an `ApplicationContext`. These modes build an in-process WebFlux server setup using mock request/response infrastructure, so no TCP connection or listening port is required.

`bindToController(...)` is the most focused option and is useful when the controller plus explicitly configured WebFlux infrastructure is the target. `bindToRouterFunction(...)` targets functional routing. `bindToApplicationContext(...)` uses WebFlux configuration from a Spring context and therefore gives stronger evidence about real framework wiring.

After selecting the server-side binding, `configureClient()` lets the test configure client-side concerns such as a base URL, default headers, codecs, or response timeout before building the client. Those client settings do not change the central fact that mock WebFlux bindings stay in-process.

</details>

- [Back to top](#back-to-top)

---

## <a id="mockmvc-webtestclient-binding">Binding WebTestClient to MockMvc</a>

<details>
<summary>Click for details</summary>

Spring Framework also lets `WebTestClient` use MockMvc as its server. `MockMvcWebTestClient.bindToController(...)` creates a focused MVC setup, while `bindToApplicationContext(...)` uses a `WebApplicationContext`. An already configured `MockMvc` instance can also be connected to a `WebTestClient`.

The request still does not travel over the network. A MockMvc connector adapts the client exchange into MockMvc request processing, so the evidence is the same Servlet-based MVC machinery discussed in the previous chapter, observed through the `WebTestClient` assertion API.

This binding is useful when a team wants one response-testing style that can later be pointed at a live server. It does not convert Spring MVC into WebFlux, and it does not exercise `WebClient` as a production network client. The server side remains MockMvc and `DispatcherServlet`.

### References

- [Spring Framework 6.1.14 API — MockMvcWebTestClient](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/web/servlet/client/MockMvcWebTestClient.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="live-server-binding">Binding to a live HTTP server</a>

<details>
<summary>Click for details</summary>

`WebTestClient.bindToServer()` creates a client that performs real HTTP exchanges. The test normally configures a base URL pointing at a server that is already running for the scenario. At that point, requests cross the transport boundary and the test can observe behavior that an in-process binding cannot reproduce.

A live binding can prove that the deployed HTTP endpoint is reachable and that request/response serialization, server runtime configuration, network-facing headers, and other transport-visible behavior work together. The exact strength of the evidence still depends on what server and infrastructure the test actually starts.

Do not confuse the live binding with a Spring Boot-specific test style. Starting embedded servers through `@SpringBootTest`, random-port support, and Boot test slices belong to the Spring Boot testing module. At Spring Framework level, the essential idea is simply that `WebTestClient` can target any reachable HTTP server.

</details>

- [Back to top](#back-to-top)

---

## <a id="webtestclient-assertions">Response assertions and server-side assertions</a>

<details>
<summary>Click for details</summary>

After `exchange()`, `WebTestClient` exposes fluent response expectations for status, headers, cookies, and body content. Tests can decode a single body, a list, raw bytes, JSON/XML content, or streaming elements and then apply built-in or custom assertions.

These response assertions are client-visible evidence and work regardless of whether the target is mock-bound or live. They should normally be the first choice for an HTTP contract because they describe what a client can actually observe.

When the backing server is MockMvc, Spring can additionally bridge an `ExchangeResult` back into MockMvc result actions through `MockMvcWebTestClient.resultActionsFor(...)`. That enables server-side assertions such as model, view, or handler checks that are impossible for an ordinary remote HTTP client. If the test needs the resolved exception, obtain the resulting `MvcResult` and inspect `getResolvedException()` or use a custom `ResultMatcher`; Spring 6.1.14 still has no built-in MockMvc exception matcher factory. Those extra checks are available because MockMvc is in-process, not because the HTTP response itself contains that information.

</details>

- [Back to top](#back-to-top)

---

## <a id="mockrestservice-server">Testing HTTP client collaborators with MockRestServiceServer</a>

<details>
<summary>Click for details</summary>

`MockRestServiceServer` solves the opposite side of the testing problem: the code under test is an HTTP **client** that calls another service. The mock server can bind to a `RestTemplate` or, in Spring Framework 6.1, a `RestClient.Builder`. The test declares expected requests and supplies stub responses without opening a real server socket.

Typical assertions verify the URI, HTTP method, headers, or request body, then return a controlled response with `MockRestResponseCreators`. After the client code runs, `verify()` confirms that all required expectations were satisfied. The server can also reset recorded expectations between scenarios.

Because the binding replaces the client's `ClientHttpRequestFactory` path with testing infrastructure, this is excellent for isolated collaborator behavior but weaker evidence for production transport configuration. It proves that the client code builds the expected Spring HTTP request and handles the supplied response; it does not prove DNS, sockets, TLS, proxy configuration, or behavior of a real remote service.

### References

- [Spring Framework 6.1.14 API — MockRestServiceServer](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/web/client/MockRestServiceServer.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="mock-server-vs-live-integration">Mock server versus live integration evidence</a>

<details>
<summary>Click for details</summary>

Different HTTP test doubles remove different parts of the production path, so they provide different evidence. An in-process `MockRestServiceServer` is ideal when the question is whether application client code constructs the right Spring request and reacts correctly to controlled responses. It is deterministic and fast, but it does not exercise real network I/O.

A standalone mock web server that listens on a real port keeps the remote behavior fake while allowing the production HTTP client stack to perform an actual network exchange. That can expose request-factory behavior, connection establishment, timeouts, TLS/proxy configuration, and other transport- or protocol-level effects that `MockRestServiceServer` bypasses. A live integration environment goes further by involving the real remote service or deployed system.

Choose the smallest boundary that can falsify the behavior you care about. Client decision/trade-off theory belongs to the HTTP integration module; this testing module only establishes what evidence each testing boundary can and cannot provide.

</details>

- [Back to top](#back-to-top)
