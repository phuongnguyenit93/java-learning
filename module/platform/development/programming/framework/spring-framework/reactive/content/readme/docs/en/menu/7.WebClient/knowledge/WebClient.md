<a id="back-to-top"></a>

# WebClient and Outbound Reactive HTTP

## Menu
- [Why WebClient exists](#webclient-purpose)
- [Builder model and immutable client instances](#webclient-builder-immutability)
- [HTTP client connectors](#webclient-client-connectors)
- [Request construction and body insertion](#webclient-request-and-body)
- [Response retrieval](#webclient-response-retrieval)
- [Lower-level exchange control](#webclient-exchange-control)
- [Client-side error handling](#webclient-error-handling)
- [Streaming response bodies](#webclient-streaming-responses)
- [Filters, attributes, and Reactor Context](#webclient-filters-attributes-context)
- [Codec infrastructure shared with WebFlux](#webclient-codec-infrastructure)
- [Connector-owned connection and event-loop resources](#webclient-connector-resources)
- [Reactor Netty shared-resource model](#webclient-reactor-netty-shared-resources)
- [Using WebClient from Spring MVC applications](#webclient-in-mvc)

## <a id="webclient-purpose">Why WebClient exists</a>

<details>
<summary>Click for details</summary>

WebClient is Spring Framework's high-level reactive HTTP client. It exists so outbound HTTP can participate in the same non-blocking composition model as a WebFlux server request instead of forcing a blocking call in the middle of the pipeline.

The client exposes a fluent request API, delegates actual network I/O to a ClientHttpConnector, and uses the same codec infrastructure family that WebFlux uses on the server side. The result is a publisher: no request is completed merely because the fluent chain was assembled.

~~~java
Mono<Account> account = webClient.get()
        .uri("/accounts/{id}", id)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .bodyToMono(Account.class);
~~~

WebClient is useful for request/response calls and streaming exchanges, but it does not remove normal HTTP concerns. Timeouts, connection pooling, TLS, DNS, status handling, body limits, cancellation, and connector lifecycle still matter. The reactive API changes how those concerns compose; it does not make them disappear.

This module owns WebClient's Framework mechanics. Broader application-to-application client selection and integration architecture belong to the repository's HTTP integration area.

</details>

- [Back to top](#back-to-top)

---

## <a id="webclient-builder-immutability">Builder model and immutable client instances</a>

<details>
<summary>Click for details</summary>

WebClient.Builder is the mutable configuration phase; the WebClient produced by build() is intended to be reused. Configure stable concerns such as base URL, default headers, filters, connector, codecs, and URI handling once, then issue many independent requests from the built client.

~~~java
WebClient client = WebClient.builder()
        .baseUrl("https://inventory.internal")
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .filter(correlationFilter())
        .build();
~~~

The client itself exposes mutate(), which creates a builder initialized from the current client's configuration:

~~~java
WebClient adminClient = client.mutate()
        .defaultHeader("X-Client-Role", "admin")
        .build();
~~~

This is preferable to rebuilding a full configuration graph for every request. Per-request state belongs on the request specification; stable policy belongs on the client.

Avoid using a shared builder as ad hoc mutable global state after application startup. Even when an API allows mutation, predictable client instances are easier to reason about when filters, headers, codecs, and connector ownership remain stable.

</details>

- [Back to top](#back-to-top)

---

## <a id="webclient-client-connectors">HTTP client connectors</a>

<details>
<summary>Click for details</summary>

WebClient does not perform socket I/O by itself. ClientHttpConnector is the boundary between the WebClient API and an underlying HTTP client implementation.

Spring Framework supports connectors for implementations such as Reactor Netty, JDK HttpClient, Jetty's reactive client, and Apache HttpComponents. WebClient.create() uses Reactor Netty by default when that standard setup is available, while WebClient.builder().clientConnector(...) lets an application choose and configure the transport explicitly.

~~~java
HttpClient httpClient = HttpClient.create()
        .responseTimeout(Duration.ofSeconds(3));

WebClient client = WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
~~~

Connector choice affects connection pooling, event-loop or worker resources, TLS features, proxy support, timeout options, and lifecycle. Keep those concerns at the connector boundary rather than scattering native-client access across application code.

Do not infer “WebClient behavior” from one connector implementation. The WebClient contract is stable at the Framework layer, while detailed transport characteristics belong to the selected HTTP client.

</details>

- [Back to top](#back-to-top)

---

## <a id="webclient-request-and-body">Request construction and body insertion</a>

<details>
<summary>Click for details</summary>

Request construction proceeds from method and URI to headers, cookies, attributes, and an optional body. Use bodyValue for one already-available value and body(Publisher, Class) or a BodyInserter when the request body is produced reactively.

~~~java
Mono<OrderResult> result = client.post()
        .uri("/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new CreateOrder(customerId, items))
        .retrieve()
        .bodyToMono(OrderResult.class);
~~~

For a stream:

~~~java
Flux<Ack> acknowledgements = client.post()
        .uri("/events")
        .contentType(MediaType.APPLICATION_NDJSON)
        .body(events, Event.class)
        .retrieve()
        .bodyToFlux(Ack.class);
~~~

The declared Content-Type tells the selected HttpMessageWriter how the body is represented. Supplying a Publisher does not guarantee wire-level streaming by itself; the selected media type, codec, connector, and remote protocol behavior must support incremental writes.

Keep request construction free of blocking preparatory work. Reading a large file synchronously or calling a blocking SDK before passing a value to bodyValue has already broken the intended execution model.

</details>

- [Back to top](#back-to-top)

---

## <a id="webclient-response-retrieval">Response retrieval</a>

<details>
<summary>Click for details</summary>

retrieve() is the convenient response path. It returns a WebClient.ResponseSpec from which the application can decode the body or obtain a ResponseEntity.

~~~java
Mono<ResponseEntity<Account>> response = client.get()
        .uri("/accounts/{id}", id)
        .retrieve()
        .toEntity(Account.class);
~~~

By default, 4xx and 5xx responses become WebClientResponseException signals. onStatus(...) lets the application map selected HTTP statuses to another exception or handling policy before body decoding.

~~~java
Mono<Account> account = client.get()
        .uri("/accounts/{id}", id)
        .retrieve()
        .onStatus(
                status -> status.value() == 404,
                response -> Mono.error(new AccountMissing(id)))
        .bodyToMono(Account.class);
~~~

Use retrieve() when the status policy is straightforward and the main goal is to decode the response. For ordinary body-decoding methods and bounded ResponseEntity variants, WebClient keeps the common body lifecycle path compact. If you use toEntityFlux(...), however, the returned ResponseEntity<Flux<T>> transfers responsibility for subscribing to and consuming the body Flux to the caller; leaving that body unsubscribed can keep associated response resources from being released.

An HTTP error status is still a valid HTTP response. Distinguish it from transport failures such as connection refusal, TLS failure, or timeout, which occur before a usable HTTP response exists.

</details>

- [Back to top](#back-to-top)

---

## <a id="webclient-exchange-control">Lower-level exchange control</a>

<details>
<summary>Click for details</summary>

exchangeToMono(...) and exchangeToFlux(...) are the lower-level response APIs for cases where decoding depends on status, headers, or other ClientResponse details.

~~~java
Mono<Account> account = client.get()
        .uri("/accounts/{id}", id)
        .exchangeToMono(response -> {
            if (response.statusCode().is2xxSuccessful()) {
                return response.bodyToMono(Account.class);
            }
            if (response.statusCode().value() == 404) {
                return Mono.empty();
            }
            return response.createError();
        });
~~~

Spring 6.1.14 guarantees an important lifecycle behavior: after the publisher returned by the response handler completes, an unconsumed response body is released. If the body is needed, the handler must declare how it will be decoded before that completion.

The older exchange() method is deprecated because handing raw ClientResponse ownership to arbitrary downstream code made it easy to leak memory or connections when the body was not consumed. Prefer retrieve() for the common case and exchangeToMono/exchangeToFlux when full response-dependent branching is genuinely necessary.

This API is “lower level” because the application owns more of the status/body decision, not because it bypasses codecs or the connector.

### References

- Spring Framework 6.1.14 API — WebClient.RequestHeadersSpec
- Spring Framework Reference — WebClient Exchange

</details>

- [Back to top](#back-to-top)

---

## <a id="webclient-error-handling">Client-side error handling</a>

<details>
<summary>Click for details</summary>

Client-side failure handling has at least three distinct categories:

1. **HTTP status policy** — the server returned a response such as 404 or 503.
2. **Body processing failure** — a codec cannot decode the response, a configured memory limit is exceeded, or the body is otherwise invalid.
3. **Transport failure** — connection, DNS, TLS, timeout, or connector-level I/O failed.

retrieve().onStatus(...) addresses the first category. Reactive error operators can handle the resulting exception signals, but broad onErrorResume clauses should not collapse unrelated failures into one fallback.

~~~java
return client.get()
        .uri("/catalog/{id}", id)
        .retrieve()
        .onStatus(HttpStatusCode::is5xxServerError,
                response -> response.createException())
        .bodyToMono(CatalogItem.class)
        .timeout(Duration.ofSeconds(2));
~~~

Choose retry policy with HTTP semantics and operation idempotency in mind. Retrying every error can duplicate writes, amplify an outage, or hold connections longer under pressure. Retry/backoff theory belongs to the broader reactive/resilience layers, while WebClient's responsibility is to expose failures accurately enough for that policy to make a sound decision.

When a filter or exchange callback consumes a ClientResponse directly, preserve the body lifecycle. A response that is neither consumed nor released can prevent connection reuse and create memory/resource leaks.

</details>

- [Back to top](#back-to-top)

---

## <a id="webclient-streaming-responses">Streaming response bodies</a>

<details>
<summary>Click for details</summary>

WebClient can expose a response body as Flux<T> so elements are decoded incrementally when the wire format and codec support streaming. Common examples include Server-Sent Events and newline-delimited JSON.

~~~java
Flux<PriceTick> ticks = client.get()
        .uri("/prices/stream")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .retrieve()
        .bodyToFlux(PriceTick.class);
~~~

The main benefit is that the application does not need the entire response in memory before processing starts. Demand and cancellation can propagate through the reactive client boundary, while the connector controls the underlying network read.

Streaming also extends resource lifetime. A connection used for a long-lived stream may remain occupied for minutes or hours. Cancellation, remote disconnects, idle timeouts, and slow consumers therefore become normal lifecycle events rather than exceptional edge cases.

Do not convert a streaming endpoint back into unbounded aggregation with collectList() unless the data is known to be bounded. Doing so discards the memory and latency benefits that motivated streaming in the first place.

</details>

- [Back to top](#back-to-top)

---

## <a id="webclient-filters-attributes-context">Filters, attributes, and Reactor Context</a>

<details>
<summary>Click for details</summary>

ExchangeFilterFunction is WebClient's interception mechanism. A filter receives a ClientRequest and an ExchangeFunction for the next step, so it can add headers, inspect responses, record observations, or implement client-wide policy.

~~~java
ExchangeFilterFunction addRequestId = (request, next) ->
        Mono.deferContextual(contextView -> {
            String requestId = contextView.getOrDefault("requestId", "unknown");
            ClientRequest filtered = ClientRequest.from(request)
                    .header("X-Request-Id", requestId)
                    .build();
            return next.exchange(filtered);
        });
~~~

Request attributes are local metadata for the current WebClient request and are convenient for communication with filters. They do not automatically propagate to nested or later requests.

Reactor Context is the better fit for contextual data that must flow through reactive composition and become visible to nested WebClient calls. Attach it with Reactor contextWrite on the composed chain. RequestHeadersSpec.context(...) is deprecated in Spring 6.1.14 because it cannot provide context to downstream nested or subsequent requests.

Filters that directly inspect or replace a ClientResponse must preserve body ownership. Either propagate the response so later WebClient stages can consume it or explicitly consume/release it before issuing a replacement request.

</details>

- [Back to top](#back-to-top)

---

## <a id="webclient-codec-infrastructure">Codec infrastructure shared with WebFlux</a>

<details>
<summary>Click for details</summary>

WebClient and WebFlux server processing use the same HTTP codec model: HttpMessageReader and HttpMessageWriter implementations convert between byte buffers and application values. On the client side those strategies are configured through ExchangeStrategies and WebClient.Builder.codecs(...).

~~~java
WebClient client = WebClient.builder()
        .codecs(configurer ->
                configurer.defaultCodecs().maxInMemorySize(512 * 1024))
        .build();
~~~

The shared model gives consistent JSON, form, multipart, and other message conversion concepts across inbound and outbound HTTP. It does not mean server and client configuration objects are literally the same instance; each side has its own configured strategy set.

Memory limits are especially important for codecs that aggregate data before decoding. A non-blocking pipeline can still run out of memory if it eagerly buffers many large bodies. For genuinely streaming formats, prefer incremental decoding and processing instead of raising limits until the symptom disappears.

Custom codecs should preserve the same DataBuffer ownership rules as built-in infrastructure. Retaining pooled buffers past their intended lifecycle or forgetting to release discarded buffers can cause leaks even when application-level objects look correct.

</details>

- [Back to top](#back-to-top)

---

## <a id="webclient-connector-resources">Connector-owned connection and event-loop resources</a>

<details>
<summary>Click for details</summary>

Connection pools and event-loop threads belong to the selected ClientHttpConnector and its underlying HTTP client, not to the WebClient façade itself. Creating many WebClient objects does not create a sound resource model if each one is wired to independently managed transport resources.

For Reactor Netty, Spring Framework 6.1 provides org.springframework.http.client.ReactorResourceFactory to manage LoopResources and ConnectionProvider within an ApplicationContext lifecycle. An application can use global Reactor Netty resources or configure independently managed resources.

~~~java
@Bean
ReactorResourceFactory reactorResources() {
    ReactorResourceFactory factory = new ReactorResourceFactory();
    factory.setUseGlobalResources(false);
    return factory;
}

@Bean
WebClient webClient(ReactorResourceFactory resources) {
    ReactorClientHttpConnector connector =
            new ReactorClientHttpConnector(resources, client -> client);

    return WebClient.builder()
            .clientConnector(connector)
            .build();
}
~~~

Declaring the factory does not make an arbitrary WebClient discover it automatically. Plain Framework code must wire the factory into the ReactorClientHttpConnector (or otherwise configure the underlying Reactor Netty client/server) that should use those resources. When resources are externally managed, ownership must be explicit: the component that creates the pool and event loops must also have a well-defined shutdown lifecycle. Reusing a WebClient while accidentally recreating connectors or pools defeats connection reuse and can produce thread/resource growth.

Other connector implementations have their own resource models. Keep WebClient-level configuration separate from connector-specific tuning so transport changes remain localized.

</details>

- [Back to top](#back-to-top)

---

## <a id="webclient-reactor-netty-shared-resources">Reactor Netty shared-resource model</a>

<details>
<summary>Click for details</summary>

Reactor Netty's default model uses shared global HttpResources for event-loop threads and the connection pool. Fixed shared resources are the preferred event-loop model because the goal is to multiplex many non-blocking connections over a small stable set of threads rather than create a thread per request.

When Reactor Netty backs both a WebFlux server and WebClient in the same process, client and server share event-loop resources by default. This reduces thread count, but it also makes blocking work more damaging: one accidental blocking call can occupy a resource that serves many unrelated connections.

For an application whose server lives for the entire JVM process, global resources can naturally live until process exit. For applications that start and stop an ApplicationContext inside a longer-lived JVM, a Spring-managed ReactorResourceFactory can tie global resource initialization and shutdown to the context lifecycle.

If useGlobalResources is disabled, the application assumes responsibility for consistently wiring the custom LoopResources and ConnectionProvider into the Reactor Netty clients and servers that should share them. Partial customization can create multiple pools and event-loop groups with surprising lifecycle behavior.

Resource sharing is a transport concern. It should inform capacity planning and diagnostics, but application code should continue depending on WebClient rather than directly coordinating Netty event loops.

### References

- Spring Framework 6.1.14 API — org.springframework.http.client.ReactorResourceFactory
- Spring Framework Reference — WebFlux concurrency model and WebClient Reactor Netty resources

</details>

- [Back to top](#back-to-top)

---

## <a id="webclient-in-mvc">Using WebClient from Spring MVC applications</a>

<details>
<summary>Click for details</summary>

WebClient is not restricted to WebFlux server applications. A Spring MVC application can use it when a reactive, streaming, or non-blocking outbound client is useful even though inbound request handling uses the Servlet stack.

The architectural question is what the MVC application does with the publisher. If code immediately calls block(), the caller chooses synchronous waiting at that boundary and must size Servlet threads and configure timeouts accordingly. If MVC integrates the reactive return type asynchronously, the Servlet request still belongs to the MVC execution model while WebClient performs outbound work through its connector.

~~~java
Account account = webClient.get()
        .uri("/accounts/{id}", id)
        .retrieve()
        .bodyToMono(Account.class)
        .block(timeout);
~~~

That example can be a deliberate choice in a blocking MVC service; it should not be copied into WebFlux request threads. The same client API can participate in different server architectures, so “uses WebClient” is not evidence that the whole application is reactive.

Keep connector resources lifecycle-managed even in MVC. A blocking caller may wait on the result, but the actual WebClient transport still uses its own connection and execution resources underneath.

</details>

- [Back to top](#back-to-top)
