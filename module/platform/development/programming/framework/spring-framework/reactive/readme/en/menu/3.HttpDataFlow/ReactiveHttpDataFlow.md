<a id="back-to-top"></a>

# Reactive Request, Response, and Codec Flow

## Menu
- [Reactive request and response body model](#webflux-reactive-body-model)
- [HttpMessageReader and HttpMessageWriter](#webflux-message-readers-writers)
- [Content negotiation in WebFlux](#webflux-content-negotiation)
- [Streaming versus aggregation](#webflux-streaming-vs-aggregation)
- [DataBuffer lifecycle and memory boundaries](#webflux-data-buffer-lifecycle)
- [Form data processing](#webflux-form-data)
- [Multipart data processing](#webflux-multipart-data)
- [Streaming multipart with large payloads](#webflux-multipart-streaming)
- [Binding and validation integration boundary](#webflux-binding-validation-integration)
- [Cache-Control and conditional requests](#webflux-http-cache-conditional)
- [Decoding, validation, and response-write failures](#webflux-http-data-failures)

## <a id="webflux-reactive-body-model">Reactive request and response body model</a>

<details>
<summary>Click for details</summary>

WebFlux treats an HTTP body as data that can become available over time. At the transport boundary, request content arrives as a publisher of `DataBuffer` values. Higher layers normally do not manipulate those buffers directly; `HttpMessageReader` implementations decode them into application values such as `Mono<Order>` or `Flux<Event>`.

The same idea applies in the other direction. A controller or functional handler can produce a single value, no value, or a multi-value publisher, and an `HttpMessageWriter` encodes those values into response buffers as demand and I/O progress allow. The logical response is therefore not required to exist eagerly in memory before writing begins.

This model enables cancellation and demand signals to flow through participating reactive components, but it should not be described as an HTTP transport guarantee in isolation. The actual socket, server adapter, codec, and application pipeline all participate in how much data is read, buffered, and written.

For application code the useful rule is simple: keep body processing asynchronous, avoid unbounded collection when a stream can remain a stream, and choose endpoint types whose cardinality matches the data you actually intend to consume or produce.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-message-readers-writers">HttpMessageReader and HttpMessageWriter</a>

<details>
<summary>Click for details</summary>

`HttpMessageReader` and `HttpMessageWriter` are WebFlux's strategy contracts for converting between HTTP body buffers and higher-level Java values. Readers inspect the target type and request media type to decide whether they can decode input; writers inspect the value type and candidate response media type to decide whether they can encode output.

Most applications use the default set provided by `ServerCodecConfigurer`. Depending on the classpath, that includes support for common byte, text, resource, form, multipart, JSON, XML, and event-stream use cases. `WebFluxConfigurer.configureHttpMessageCodecs` can customize defaults, add custom readers/writers, or replace the defaults when there is a real protocol need.

Codecs are shared infrastructure across annotated controllers and functional endpoints. For example, `@RequestBody Mono<Person>`, `ServerRequest.bodyToMono(Person.class)`, `@ResponseBody Flux<Person>`, and a functional `ServerResponse` can all ultimately rely on configured readers or writers.

Do not put domain decisions into a codec merely because it sees the wire format. A codec should translate representation and type; authorization, business validation, persistence, and workflow decisions belong at higher application layers.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-content-negotiation">Content negotiation in WebFlux</a>

<details>
<summary>Click for details</summary>

Content negotiation decides which representation is acceptable for a response. In annotated WebFlux, the requested media types are resolved from the request, normally from the `Accept` header unless configuration says otherwise, and are compared with what the selected handler can produce and what registered `HttpMessageWriter` instances can actually encode.

Mapping conditions such as `produces = "application/json"` narrow the handler's declared representations. A response-body result still needs a compatible writer for the Java type and selected media type. If those pieces cannot agree, the request fails rather than silently choosing an unrelated format.

Request consumption is a related but separate decision. The request `Content-Type`, mapping `consumes` conditions, target argument type, and available `HttpMessageReader` determine how an incoming body is decoded.

`WebFluxConfigurer.configureContentTypeResolver` customizes requested-content-type resolution. Keep that policy explicit: URL or query-parameter based negotiation can create surprising cache and routing behavior, while header-based negotiation keeps representation choice in the HTTP metadata where clients normally express it.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-streaming-vs-aggregation">Streaming versus aggregation</a>

<details>
<summary>Click for details</summary>

Streaming preserves a sequence as values become available; aggregation waits for some or all input and represents it as one value. The distinction affects latency, memory, cancellation, and the usefulness of demand-aware processing.

A controller parameter such as `Mono<byte[]>` or a decoded object requires enough input to construct that single value. Form data is also parsed into an aggregate structure. By contrast, a `Flux<DataBuffer>`, a stream of decoded elements, or `Flux<PartEvent>` can be processed incrementally when the selected reader supports that shape.

On output, multi-value reactive return types can be encoded element by element. Media types designed for streaming, such as `text/event-stream` or newline-delimited/streaming JSON variants, make flushing boundaries explicit so clients can observe elements promptly. With other media types, encoding and flushing behavior can differ, so "returns Flux" by itself is not a promise that every element becomes a separately flushed network message.

Aggregation is often simpler and is correct for bounded payloads. Streaming is valuable for large, long-lived, or latency-sensitive bodies, but only when downstream processing also avoids collecting the stream back into an unbounded in-memory container.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-data-buffer-lifecycle">DataBuffer lifecycle and memory boundaries</a>

<details>
<summary>Click for details</summary>

`DataBuffer` is Spring's byte-buffer abstraction over runtime-specific buffer implementations. With Reactor Netty, buffers can wrap pooled Netty memory; on other runtimes a different `DataBufferFactory` may be used. That portability is useful, but pooled buffers make ownership and release rules significant.

Application code normally stays above this level and lets codecs and the HTTP writer manage buffers. Code that directly consumes `DataBuffer` or `PartEvent.content()` must ensure each buffer is completely consumed, relayed to an owner that will consume it, or released. Dropping pooled buffers through custom operators without release can produce off-heap memory leaks.

`DataBufferUtils` provides safe utilities for retaining, releasing, joining, and bridging buffer streams. Cancellation and filtering deserve special attention because a buffer can be discarded before ordinary success-path cleanup runs.

Some readers must buffer data to create one object or to delimit one streaming element. Spring codecs expose memory limits for those cases; `ServerCodecConfigurer` can apply a common maximum in-memory size to default codecs. Crossing such a limit is a protection mechanism, not a signal to raise it blindly. Decide whether the endpoint should stream, reject oversized input, spool data, or use a deliberately larger bounded limit.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-form-data">Form data processing</a>

<details>
<summary>Click for details</summary>

For `application/x-www-form-urlencoded` requests, WebFlux parses the encoded body into form fields and exposes them through web-level APIs such as `ServerWebExchange.getFormData()`. In annotated WebFlux, `@RequestParam` binds **query parameters only**; form fields can instead participate in data binding onto a `@ModelAttribute` command object.

Form semantics are naturally aggregate: the framework needs the name/value pairs as a form structure rather than treating each raw network buffer as an application event. That means form parsing is one of the places where configured codec memory limits matter.

```java
public Mono<Void> handle(ServerWebExchange exchange) {
    return exchange.getFormData().flatMap(form -> {
        String email = form.getFirst("email");
        // use the parsed value...
        return exchange.getResponse().setComplete();
    });
}
```

Do not read the raw request body independently and then expect form parsing to replay it. Request content is a stream, and the WebFlux infrastructure intentionally centralizes form parsing and caching at the exchange level for components that need form data.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-multipart-data">Multipart data processing</a>

<details>
<summary>Click for details</summary>

Multipart requests contain multiple named parts, each with headers and content. WebFlux represents ordinary parsed multipart input with `Part` values; uploaded files are exposed as `FilePart`. Annotated controllers can request a specific part with `@RequestPart`, bind multipart fields to a command object, or obtain all parts as a `MultiValueMap<String, Part>`.

```java
@PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
Mono<Void> upload(@RequestPart("metadata") Mono<Metadata> metadata,
                  @RequestPart("file") FilePart file) {
    return metadata.flatMap(meta -> file.transferTo(targetPath(meta)));
}
```

Readers can decode a structured part, such as JSON metadata, using the part's own content type and normal codec infrastructure. Validation can then apply to that decoded argument at the controller boundary.

The convenient `Part` model is not the same as "everything is loaded into heap memory." Multipart parsing has configurable storage and size policies and file content remains reactive. Still, obtaining the complete part map requires parsing the multipart message structure before the aggregate view is available, so very large sequential uploads may benefit from the streaming `PartEvent` model instead.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-multipart-streaming">Streaming multipart with large payloads</a>

<details>
<summary>Click for details</summary>

For large or sequential multipart payloads, WebFlux can expose `@RequestBody Flux<PartEvent>`. Each form field produces a `FormPartEvent`; a file can produce multiple `FilePartEvent` instances as its content is split across buffers. `PartEvent.isLast()` marks the final event of the current part.

A common pattern is to window the stream by that marker, inspect the first event to identify the part, and then process the remaining content sequentially:

```java
@PostMapping("/stream-upload")
Mono<Void> upload(@RequestBody Flux<PartEvent> events) {
    return events
            .windowUntil(PartEvent::isLast)
            .concatMap(this::handlePart)
            .then();
}
```

The critical ownership rule is that every `DataBuffer` carried by received part events must be consumed, relayed, or released. Abandoning a window after looking only at its first event can leak pooled buffers.

Streaming multipart is useful when the application can process or forward large content incrementally. If business logic ultimately collects every file into memory before doing any work, the extra streaming complexity brings little benefit and may hide an unbounded buffer elsewhere in the pipeline.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-binding-validation-integration">Binding and validation integration boundary</a>

<details>
<summary>Click for details</summary>

WebFlux owns the **web integration point** for binding and validation: it decides which controller arguments are created from request data, invokes configured conversion/binding infrastructure, and turns binding or validation failures into web-level outcomes. The reusable mechanics of `DataBinder`, `ConversionService`, `Formatter`, `Validator`, and Jakarta Bean Validation belong to the validation-data-binding module.

At the controller boundary, `@ModelAttribute` binds request values to an object, while `@RequestBody` and `@RequestPart` use message readers and can trigger validation on decoded objects. `@Valid` or `@Validated` can request object validation. Spring Framework 6.1 also has built-in controller method validation when constraints are declared directly on method parameters or the return value.

This separation matters because a reactive body and a form-bound model fail at different stages. Decoding can fail before an object exists; binding can fail while converting fields; object validation can reject a constructed value; method validation can reject parameter or return-value constraints.

Controller code should handle errors at the level where it has enough context to act. Keep custom validation policy in reusable validators and domain rules rather than duplicating it in reactive operators solely because the endpoint is WebFlux.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-http-cache-conditional">Cache-Control and conditional requests</a>

<details>
<summary>Click for details</summary>

HTTP caching in WebFlux is based on HTTP validators and cache directives, not Spring's application-level `Cache` abstraction. `Cache-Control` tells clients and intermediaries how a representation may be reused. `ETag` and `Last-Modified` let a later request ask whether the representation has changed.

Controllers can set those values through `ResponseEntity`, or can evaluate the request explicitly through `ServerWebExchange.checkNotModified(...)`. If a conditional `GET` or `HEAD` proves the representation is unchanged, WebFlux can complete with `304 NOT_MODIFIED` and no body. For conditional state-changing requests such as `PUT`, `POST`, or `DELETE`, a failed precondition can produce `412 PRECONDITION_FAILED`.

```java
@GetMapping("/catalog/{id}")
Mono<ResponseEntity<Item>> item(@PathVariable String id) {
    return service.find(id)
            .map(item -> ResponseEntity.ok()
                    .eTag("\"" + item.version() + "\"")
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)))
                    .body(item));
}
```

The largest saving occurs when the validator can be obtained cheaply enough to avoid expensive representation generation. If the application must perform all expensive work before computing the ETag, a `304` still saves response bandwidth but may save little server CPU or downstream I/O.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-http-data-failures">Decoding, validation, and response-write failures</a>

<details>
<summary>Click for details</summary>

Failures in the HTTP data path are easier to reason about when they are classified by stage.

- **Media-type or decoding failure:** no compatible reader exists, the payload is malformed, or a codec cannot construct the target value.
- **Binding/conversion failure:** request values cannot be converted or bound to the target object.
- **Validation failure:** a decoded or bound object, method parameter, or return value violates configured constraints.
- **Application failure:** endpoint logic returns an error signal or throws before successful completion.
- **Encoding/write failure:** a result cannot be encoded, the connection fails, or an error happens after response writing has begun.

Early failures can usually be translated by controller exception handling or the wider WebFlux exception chain before the response is committed. Late streaming failures are different: once status and headers have been committed and body bytes have reached the client, an error handler may no longer be able to replace the response with a clean error document or different status.

This is why streaming endpoints need error semantics that make sense after partial output, and why logs and metrics should preserve the stage where a failure occurred instead of reporting every problem as a generic controller error.

</details>

- [Back to top](#back-to-top)
