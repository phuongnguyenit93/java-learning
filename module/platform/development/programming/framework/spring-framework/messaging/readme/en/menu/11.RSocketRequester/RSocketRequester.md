<a id="back-to-top"></a>

# RSocketRequester and RSocketStrategies

## Menu
- [1. RSocketRequester role](#rsocket-requester-role)
- [2. Client requester connection setup](#rsocket-client-requester)
- [3. Server-side requester](#rsocket-server-requester)
- [4. Route, data, and response retrieval](#rsocket-route-data-retrieve)
- [5. Request metadata](#rsocket-request-metadata)
- [6. Requester lifecycle and disposal](#rsocket-requester-lifecycle)
- [7. RSocketStrategies](#rsocket-strategies)
- [8. Codecs and route matching](#rsocket-codecs-route-matching)
- [9. Metadata extraction](#rsocket-metadata-extraction)

## <a id="rsocket-requester-role">1. RSocketRequester role</a>

<details>
<summary>Click for details</summary>

`RSocketRequester` is Spring Messaging's higher-level requester API. It wraps the sending side of RSocket so application code works with Java objects, routes, metadata, `Mono`, and `Flux` instead of manually creating `Payload` buffers and choosing low-level frame methods.

Its role is intentionally narrow:

```text
application object
    -> RSocketRequester
    -> Encoder / metadata preparation
    -> RSocket interaction
    -> Decoder
    -> application object
```

The requester can be used from either endpoint. A client creates one while connecting to a server; a server can receive one for an already connected peer in an annotated responder. The API therefore models the requester role of an interaction rather than a permanent “client” role.

Interaction type is largely inferred from input and output cardinality. For example, one input plus `retrieveMono` is request-response, one input plus `retrieveFlux` is request-stream, a multi-value input selects request-channel, and `send()` is fire-and-forget.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-client-requester">2. Client requester connection setup</a>

<details>
<summary>Click for details</summary>

The client-side builder prepares an `RSocketConnector` and the connection `SETUP` frame. In Spring Framework 6.1, the simple TCP and WebSocket forms are:

```java
RSocketRequester tcp = RSocketRequester.builder()
        .tcp("localhost", 7000);

RSocketRequester websocket = RSocketRequester.builder()
        .webSocket(URI.create("wss://example.org/rsocket"));
```

These builder calls do not have to open a connection immediately. The requester uses a shared connection transparently when requests are made.

Connection-wide data can be configured before the requester is created:

```java
RSocketRequester requester = RSocketRequester.builder()
        .dataMimeType(MediaType.APPLICATION_JSON)
        .setupRoute("client.{id}", clientId)
        .setupData(clientInfo)
        .setupMetadata(token, authenticationMimeType)
        .tcp(host, port);
```

`setupData`, `setupRoute`, and `setupMetadata` belong to the initial connection setup, not to every request. Advanced RSocket Java options such as keepalive, resumption, or interceptors are configured through the `rsocketConnector(...)` callback.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-server-requester">3. Server-side requester</a>

<details>
<summary>Click for details</summary>

Because an established RSocket connection is symmetric, the server may initiate requests to the connected client. Spring makes the peer-specific requester available as an `RSocketRequester` method argument in `@ConnectMapping` and `@MessageMapping` handlers.

```java
@MessageMapping("device.register")
Mono<Void> register(Device device, RSocketRequester peer) {
    requestersByDevice.put(device.id(), peer);
    return Mono.empty();
}
```

The requester is tied to that peer connection, so a registry that keeps it for later server-initiated calls must also remove it when the connection ends and must handle races with disconnects.

`@ConnectMapping` has an extra lifecycle constraint: setup must finish before normal requests can proceed. If a server wants to start a request back to the client from setup handling, that work must be decoupled from the setup completion path rather than waiting synchronously for the response.

This model is useful for server-to-client queries or commands, but it also means the client needs a responder configured for the routes the server may call.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-route-data-retrieve">4. Route, data, and response retrieval</a>

<details>
<summary>Click for details</summary>

A request is assembled in stages: select a route or metadata, provide optional data, then declare how the response should be retrieved.

```java
Flux<Airport> airports = requester
        .route("airports.near.{code}", "SGN")
        .data(searchRequest)
        .retrieveFlux(Airport.class);
```

`route(String, Object...)` expands route variables and places routing information in metadata. `data(...)` accepts a concrete value or a producer adaptable through the configured `ReactiveAdapterRegistry`. When passing a multi-value publisher, overloads that include the element `Class` or `ParameterizedTypeReference` avoid repeating element-type inspection and encoder lookup.

Response choice determines the output cardinality:

- `retrieveMono(T.class)` — zero or one decoded response value;
- `retrieveFlux(T.class)` — zero or many response values;
- `send()` — fire-and-forget with `Mono<Void>`;
- `sendMetadata()` — connection-level metadata push.

Match those choices to the responder contract. In the fluent `RSocketRequester` API, Spring documents many-input/one-output as the invalid inferred combination. When request data is a stream, model the exchange as request-channel and consume its response with stream semantics.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-request-metadata">5. Request metadata</a>

<details>
<summary>Click for details</summary>

Request metadata carries information that is orthogonal to the application data payload: route, tracing context, authentication material, feature flags, or another typed protocol concern. With the default composite-metadata setup, multiple entries can coexist and each entry keeps its own MIME type.

```java
Mono<OrderView> result = requester
        .route("orders.find.{id}", orderId)
        .metadata(bearerToken, bearerMimeType)
        .metadata(traceContext, tracingMimeType)
        .retrieveMono(OrderView.class);
```

Each metadata value must have an encoder that supports its Java type and MIME type. The responder likewise needs a matching decoder/extractor registration before the value can appear as a Spring message header.

Do not treat metadata as an untyped map shared magically by both sides. The connection's metadata MIME type, composite-metadata conventions, encoders, decoders, and `MetadataExtractor` together define the contract. For interoperability, document custom MIME types and who owns their schema.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-requester-lifecycle">6. Requester lifecycle and disposal</a>

<details>
<summary>Click for details</summary>

`RSocketRequester` implements Reactor's `Disposable`. Calling `dispose()` delegates to the underlying `RSocketClient`, closes the transport connection, and notifies subscribers that depend on it. `isDisposed()` reports that terminal state.

Treat requester lifecycle as connection lifecycle:

- keep a long-lived requester when many logical interactions should share one connection;
- close it during application/component shutdown;
- stop retaining server-side requesters after their peer disconnects;
- expect in-flight requests to fail when the connection terminates.

Spring Framework 6.1 requester instances created around `RSocketClient` may not expose a “live” `io.rsocket.RSocket` through `rsocket()`; that method can return `null`. Use the high-level requester API, or `rsocketClient()` when low-level client access is actually required.

Reconnect and resumption are separate policies. Disposing a requester is an intentional close; it is not a request to resume the same session. Configure retry/resumption through the underlying RSocket client/connector according to the application's failure model.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-strategies">7. RSocketStrategies</a>

<details>
<summary>Click for details</summary>

`RSocketStrategies` centralizes the conversion and routing strategies shared by Spring's requester and responder infrastructure. It contains:

- encoders for turning application values into data or metadata bytes;
- decoders for turning payload bytes back into application values;
- a `RouteMatcher`;
- a `ReactiveAdapterRegistry`;
- a `DataBufferFactory`;
- a `MetadataExtractor`.

```java
RSocketStrategies strategies = RSocketStrategies.builder()
        .encoders(list -> list.add(new Jackson2JsonEncoder()))
        .decoders(list -> list.add(new Jackson2JsonDecoder()))
        .routeMatcher(new PathPatternRouteMatcher())
        .build();
```

The object is designed for reuse. When a process contains both requester and responder components, sharing compatible strategies avoids subtle mismatches where one side can encode a value that the colocated responder cannot decode or routes are interpreted with different separators/pattern rules.

Do not confuse `RSocketStrategies` with protocol-level connector configuration. Keepalive, lease, resumption, transport, and interceptors belong to RSocket Java; object conversion, route matching, and metadata extraction belong here.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-codecs-route-matching">8. Codecs and route matching</a>

<details>
<summary>Click for details</summary>

Spring's basic `RSocketStrategies` defaults cover core value types such as strings and byte-oriented buffers. Domain objects require compatible encoders and decoders, commonly from `spring-web` such as Jackson JSON/CBOR or Protobuf support.

Codec selection considers both the Java element type and MIME type. A route may match perfectly and still fail before handler invocation if no decoder can deserialize the request payload; a response may fail while encoding if no encoder supports the produced type.

Route matching is separate from payload codecs. By default Spring uses `SimpleRouteMatcher` backed by `AntPathMatcher` with `.` as the separator. `PathPatternRouteMatcher` from `spring-web` is recommended for efficient matching:

```java
RSocketStrategies strategies = RSocketStrategies.builder()
        .routeMatcher(new PathPatternRouteMatcher())
        .build();
```

RSocket routes are logical destinations, not URLs. There is no HTTP URL decoding step. Keep the same matcher strategy on components that must agree on route templates, especially when service interfaces and annotated responders share contracts.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-metadata-extraction">9. Metadata extraction</a>

<details>
<summary>Click for details</summary>

`MetadataExtractor` turns serialized RSocket metadata into named values in the Spring messaging model. Those values can then be consumed through `@Header` or `@Headers` in annotated responders.

`DefaultMetadataExtractor` understands RSocket routing metadata out of the box and exposes it under the route key. Additional metadata MIME types must be registered with the target Java type and header name, plus a decoder capable of reading that type.

```java
RSocketStrategies strategies = RSocketStrategies.builder()
        .metadataExtractorRegistry(registry ->
                registry.metadataToExtract(
                        tenantMimeType,
                        TenantContext.class,
                        "tenant"))
        .build();
```

Composite metadata is a natural fit because routing, security, tracing, and application context can stay independently typed. If a peer does not use composite metadata, extraction may instead need custom logic that decodes one metadata document and populates several output entries.

Extraction is part of the responder boundary: validate decoded metadata just like other external input. A successfully decoded header is not proof that it is trusted or authorized.

### References

- [Spring Framework — RSocketRequester and MetadataExtractor](https://docs.spring.io/spring-framework/reference/rsocket.html)
- [Spring Framework API — RSocketStrategies](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/messaging/rsocket/RSocketStrategies.html)

</details>

- [Back to top](#back-to-top)
