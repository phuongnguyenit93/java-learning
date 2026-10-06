<a id="back-to-top"></a>

# RSocket Responders and Service Interfaces

## Menu
- [RSocketMessageHandler](#rsocket-message-handler)
- [@MessageMapping responders](#rsocket-message-mapping)
- [@ConnectMapping for SETUP and metadata push](#rsocket-connect-mapping)
- [Interaction model from input and output cardinality](#rsocket-interaction-cardinality)
- [RSocket service interfaces](#rsocket-service-interface)
- [@RSocketExchange](#rsocket-exchange)
- [RSocketServiceProxyFactory](#rsocket-service-proxy)
- [Service method parameters and return values](#rsocket-service-method-contract)
- [Client and server annotated responders](#rsocket-responder-symmetry)
- [Boot, Security, and Spring Integration boundaries](#rsocket-neighbor-boundaries)

## <a id="rsocket-message-handler">RSocketMessageHandler</a>

<details>
<summary>Click for details</summary>

`RSocketMessageHandler` is Spring Messaging's bridge from RSocket frames to annotated Java handler methods. It extends the reactive message-handling infrastructure, extracts routing and other metadata, decodes request data, selects a handler, invokes it, and encodes returned values into the RSocket response stream.

On a server it is commonly declared as a Spring bean so it can detect controller methods, then its `responder()` is supplied as the RSocket Java `SocketAcceptor`:

```java
@Bean
RSocketMessageHandler rsocketMessageHandler(RSocketStrategies strategies) {
    RSocketMessageHandler handler = new RSocketMessageHandler();
    handler.setRSocketStrategies(strategies);
    return handler;
}
```

The same class also supports client-side responders. The static `RSocketMessageHandler.responder(strategies, handlers...)` factory creates a responder from explicit handler objects without requiring `@Controller`, which helps when the same application contains server controllers and client responders.

The handler is framework infrastructure, not business logic. Keep codecs, route matching, metadata extraction, and reactive adaptation in its strategies; keep application behavior in the mapped methods it invokes.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-message-mapping">@MessageMapping responders</a>

<details>
<summary>Click for details</summary>

`@MessageMapping` maps an individual RSocket request to a responder method. Routes can be patterns and can be declared at type and method level. The handler may receive the request payload, destination variables, extracted metadata headers, and an `RSocketRequester` for calling the peer.

```java
@Controller
class RadarController {

    @MessageMapping("radar.find.{id}")
    Mono<Radar> find(
            @DestinationVariable String id,
            @Header("tenant") String tenant) {
        return radarService.find(tenant, id);
    }
}
```

The payload annotation is optional when a non-simple argument is otherwise identifiable as the payload. Reactive payload arguments such as `Mono<T>` and `Flux<T>` are also supported.

The method's input and output cardinality determine the RSocket interaction. That means a route is not intrinsically “request-response” or “stream”; its Java signature participates in the protocol contract. Keep that signature stable and test both route resolution and cardinality because changing `Mono<T>` to `Flux<T>`, for example, changes the interaction model visible on the wire.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-connect-mapping">@ConnectMapping for SETUP and metadata push</a>

<details>
<summary>Click for details</summary>

`@ConnectMapping` handles connection-level frames rather than ordinary request streams. It is invoked for the initial RSocket `SETUP` frame and for later `METADATA_PUSH` frames. A mapping pattern can narrow the handler to setup/push metadata with a matching route; with no pattern, it can match all such connection events.

The method can use the same general argument categories as `@MessageMapping`, but values come from setup or metadata-push content. It cannot return application data; use `void` or `Mono<Void>`.

```java
@ConnectMapping("client.{tenant}")
Mono<Void> connect(
        @DestinationVariable String tenant,
        RSocketRequester peer) {
    return registry.register(tenant, peer);
}
```

On the server, an error from asynchronous handling of the initial setup rejects the new connection. Do not wait inside setup handling for a request made back through the same peer requester, because normal requests depend on setup completing first. Start such peer calls independently from the setup completion path.

On a client responder, `@ConnectMapping` is a callback and does not control whether the underlying connection is accepted by the server. Keep that lifecycle difference in mind when sharing responder code between endpoints.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-interaction-cardinality">Interaction model from input and output cardinality</a>

<details>
<summary>Click for details</summary>

Spring determines the interaction model from the number of request values and response values represented by the handler signature:

| Input cardinality | Output cardinality | Interaction |
| --- | --- | --- |
| 0 or 1 | 0 | Fire-and-forget or request-response with no value |
| 0 or 1 | 1 | Request-response |
| 0 or 1 | many | Request-stream |
| many | 0, 1, or many | Request-channel |

`1` means a concrete value or a single-value reactive type such as `Mono<T>`. `many` means a multi-value type such as `Flux<T>`. `0` means no payload on input or no value on output (`void` or `Mono<Void>`).

The subtle case is an output cardinality of zero with zero/one input: both fire-and-forget and request-response can reach such a handler. The requester's operation decides whether a response stream exists.

This cardinality model prevents method names from lying about protocol behavior. Review the signature whenever an API changes from one value to a stream or vice versa, and align requester and responder expectations. A mismatch is a protocol-contract bug even if both Java sides compile independently.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-service-interface">RSocket service interfaces</a>

<details>
<summary>Click for details</summary>

An RSocket service interface gives requester and responder code one Java contract. Methods are annotated with `@RSocketExchange`; the interface can be passed to `RSocketServiceProxyFactory` to create a requester proxy, and a responder can implement the same interface.

```java
@RSocketExchange("inventory")
interface InventoryRSocketService {

    @RSocketExchange("find.{sku}")
    Mono<ItemView> find(@DestinationVariable String sku);

    @RSocketExchange("watch")
    Flux<ItemEvent> watch(@Payload WatchRequest request);
}
```

This reduces duplication of route strings and Java payload/response types. It does not remove the network boundary: both peers still need compatible serialization, metadata conventions, interaction cardinality, and error semantics.

Use the interface for a stable RSocket contract that both sides intentionally share. Use independent `@MessageMapping` handlers when flexible route patterns, responder-specific parameters, or looser coupling are more valuable than a shared Java interface.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-exchange">@RSocketExchange</a>

<details>
<summary>Click for details</summary>

`@RSocketExchange` declares an endpoint route for an RSocket service interface. It can appear at type level for a common route prefix and at method level for the operation-specific route.

It is deliberately more constrained than `@MessageMapping` because the same declaration must work for requester proxies and responders. An exchange method has one concrete route declaration; `@MessageMapping` may map several routes and use responder-oriented route patterns.

Supported exchange arguments include:

- `@DestinationVariable` for values expanded into the route;
- `@Payload` for request data;
- a metadata value followed by its `MimeType` argument.

```java
@RSocketExchange("orders")
interface OrderService {

    @RSocketExchange("submit.{region}")
    Mono<OrderResult> submit(
            @DestinationVariable String region,
            @Payload OrderCommand command,
            String bearerToken,
            MimeType bearerMimeType);
}
```

Because metadata arguments are positional value/MIME-type pairs, keep interface signatures readable and document custom metadata. If a contract needs arbitrary responder headers or several pattern alternatives, `@MessageMapping` may communicate the server-side model better.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-service-proxy">RSocketServiceProxyFactory</a>

<details>
<summary>Click for details</summary>

`RSocketServiceProxyFactory` turns an annotated service interface into a client-side proxy backed by an `RSocketRequester`.

```java
RSocketRequester requester = ...;

RSocketServiceProxyFactory factory =
        RSocketServiceProxyFactory.builder(requester).build();

InventoryRSocketService inventory =
        factory.createClient(InventoryRSocketService.class);
```

Calling a proxy method resolves its `@RSocketExchange` route and arguments into requester values, performs the RSocket interaction, and adapts the response to the declared Java return type. The proxy does not host a responder; an implementation of the interface registered with `RSocketMessageHandler` handles that side.

For synchronous service methods, the proxy must block for a response. `RSocketServiceProxyFactory.Builder.blockTimeout(...)` can cap that blocking time, although Spring recommends configuring timeouts at the RSocket transport/protocol level when more precise connection behavior is required. Reactive signatures avoid forcing a blocking boundary into the service contract.

Treat the factory as an adapter from a Java contract to an existing requester. Connection lifecycle, retry/resumption, codecs, and metadata strategy remain responsibilities of the underlying requester and RSocket configuration.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-service-method-contract">Service method parameters and return values</a>

<details>
<summary>Click for details</summary>

Service-interface signatures encode the same request cardinality rules as the fluent requester API. A payload can be a concrete value or a producer adaptable to a Reactive Streams `Publisher`; return values can likewise be concrete or adaptable reactive values.

For request input, `@Payload` identifies the data argument. A payload is required by default, although the annotation can mark it optional. `@DestinationVariable` supplies route template values. Additional metadata is represented as an object immediately followed by its `MimeType`.

For output:

- `Mono<T>` or another single-value reactive type models zero/one response;
- `Flux<T>` or another multi-value type models a response stream;
- `Mono<Void>` models completion without a response value;
- a synchronous value causes the proxy side to block until a value is available.

Prefer reactive signatures for streaming and for code that should preserve non-blocking composition. If a synchronous method is chosen intentionally, define timeout behavior and never call it from a non-blocking event-loop thread.

Changing a parameter from one value to a multi-value publisher or changing a return type from one to many changes the RSocket interaction contract. Treat such signature changes as wire-level API changes, not local refactoring.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-responder-symmetry">Client and server annotated responders</a>

<details>
<summary>Click for details</summary>

Annotated responders are symmetric. The same `RSocketMessageHandler` infrastructure can dispatch requests received by a server or requests received by a client. What differs is how handlers are discovered and attached to the RSocket connection.

On the server, a Spring-managed `RSocketMessageHandler` normally detects `@Controller` beans and its `responder()` is registered with `RSocketServer`. On the client, handlers can be supplied programmatically:

```java
SocketAcceptor clientResponder =
        RSocketMessageHandler.responder(strategies, new ClientCallbacks());

RSocketRequester requester = RSocketRequester.builder()
        .rsocketConnector(connector -> connector.acceptor(clientResponder))
        .tcp(host, port);
```

If one application hosts both kinds of responders through Spring configuration, a custom handler predicate can distinguish which components belong to which role and prevent accidental double registration.

Symmetry is powerful but should be intentional. A server-initiated route is part of the client-facing API and needs authorization, timeout, lifecycle, and compatibility rules just like a client-initiated route.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-neighbor-boundaries">Boot, Security, and Spring Integration boundaries</a>

<details>
<summary>Click for details</summary>

This chapter owns the Spring Framework messaging programming model: `RSocketRequester`, `RSocketStrategies`, `RSocketMessageHandler`, and the RSocket annotations/service interfaces. Neighboring Spring projects build on that model but solve different concerns.

Spring Boot can create and configure RSocket infrastructure from application configuration and provides auto-configuration conveniences. Those Boot lifecycle and property conventions belong to the Boot curriculum; the Framework contracts here remain valid without Boot.

Spring Security adds authentication and authorization for RSocket. Metadata extraction can make credentials or claims available, but decoding metadata alone does not authenticate a peer or authorize a route.

Spring Integration offers RSocket inbound/outbound gateways and connects RSocket traffic to Integration message flows. Use it when the architectural problem is enterprise integration, adapters, routing, or composed message flows rather than a controller/service-style responder.

Keep these boundaries explicit in production designs:

```text
Spring Messaging RSocket  -> protocol mapping and handler/requester model
Spring Boot               -> application setup and auto-configuration
Spring Security           -> authentication and authorization
Spring Integration        -> integration flows and gateways
```

### References

- [Spring Framework — Annotated RSocket responders and service interfaces](https://docs.spring.io/spring-framework/reference/rsocket.html)
- [Spring Framework API — RSocketMessageHandler](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/messaging/rsocket/annotation/support/RSocketMessageHandler.html)

</details>

- [Back to top](#back-to-top)
