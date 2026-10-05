<a id="back-to-top"></a>

# HTTP Message Conversion and Representation

## Menu
- [HttpMessageConverter Model](#http-message-converters)
- [Request-Body Reading and Response-Body Writing](#request-response-body-conversion)
- [Content Negotiation and Media Types](#content-negotiation-and-media-types)
- [ResponseEntity, Status, and Headers](#response-entity-status-and-headers)
- [RequestBodyAdvice and ResponseBodyAdvice](#request-response-body-advice)

## <a id="http-message-converters">HttpMessageConverter Model</a>

<details>
<summary>Click for details</summary>

`HttpMessageConverter` is Spring's strategy for converting between Java values and HTTP message bodies. A converter declares whether it can read or write a Java type for a given media type, then performs the actual serialization or deserialization.

Spring MVC uses converters from several places, including annotated controller body arguments/return values and functional endpoint infrastructure. The same abstraction is also reused by synchronous Spring HTTP clients.

```text
HTTP body + media type
→ matching HttpMessageConverter
→ Java value

Java value + selected media type
→ matching HttpMessageConverter
→ HTTP body
```

Converters are representation infrastructure, not business mappers. A JSON converter should turn JSON into an input DTO; deciding whether that DTO may change an order belongs to application logic.

Selection depends on both type and media type. Registering a converter that claims overly broad support can shadow more specific converters and produce surprising representations.

</details>

- [Back to top](#back-to-top)

---

## <a id="request-response-body-conversion">Request-Body Reading and Response-Body Writing</a>

<details>
<summary>Click for details</summary>

`@RequestBody` tells MVC to read the request body through message conversion and supply the decoded value as a handler argument. `@ResponseBody` tells MVC to take a handler result and write it through message conversion rather than treating it as a view name. `@RestController` applies response-body semantics to its handler methods.

Reading and writing are separate decisions:

```text
request Content-Type
→ which converter may read the body

response type + negotiated media type
→ which converter may write the body
```

Validation can run after a request body is decoded when the argument requests validation. A conversion failure is not the same as a validation failure: malformed JSON may fail before a Java object exists.

Avoid mixing direct Servlet response writes with normal response-body conversion unless the endpoint intentionally takes full control. Once a response is committed, later MVC stages cannot safely replace status or headers.

</details>

- [Back to top](#back-to-top)

---

## <a id="content-negotiation-and-media-types">Content Negotiation and Media Types</a>

<details>
<summary>Click for details</summary>

Media types describe representation formats. On an incoming request, `Content-Type` identifies the body being sent. On an outgoing response, the client's `Accept` header and the handler's producible media types help MVC choose a representation.

Request mappings can constrain what they consume or produce. This makes representation support part of routing as well as converter selection.

Typical failure modes are:

```text
body media type cannot be consumed
→ 415 Unsupported Media Type

no acceptable representation can be produced
→ 406 Not Acceptable
```

Content negotiation should be deterministic. Do not assume every object becomes JSON; JSON support depends on available converters and media-type selection.

If multiple representations are supported, treat each as a public contract. Adding a converter can change what becomes negotiable, so converter and negotiation configuration should be reviewed together.

</details>

- [Back to top](#back-to-top)

---

## <a id="response-entity-status-and-headers">ResponseEntity, Status, and Headers</a>

<details>
<summary>Click for details</summary>

`ResponseEntity<T>` represents an HTTP response with explicit status, headers, and an optional body. It is useful when the endpoint's result includes HTTP metadata that cannot be expressed by body value alone.

```java
return ResponseEntity
    .created(location)
    .body(createdOrder);
```

MVC still applies message conversion to the body. `ResponseEntity` does not bypass converters; it supplies status/header metadata while the body follows normal representation processing.

Use it when explicit HTTP control improves the contract: `201 Created` plus `Location`, conditional headers, a deliberate `204`, or response-specific caching metadata.

Do not return `ResponseEntity` mechanically from every controller just to look "RESTful". If the endpoint simply returns a normal body with default success status, a direct value can be clearer.

</details>

- [Back to top](#back-to-top)

---

## <a id="request-response-body-advice">RequestBodyAdvice and ResponseBodyAdvice</a>

<details>
<summary>Click for details</summary>

`RequestBodyAdvice` and `ResponseBodyAdvice` are MVC extension points around message conversion. They let infrastructure inspect or transform body-processing behavior without duplicating code in every controller.

`RequestBodyAdvice` participates around reading a request body, while `ResponseBodyAdvice` can adjust the value/headers before the selected `HttpMessageConverter` writes the response. Implementations may be discovered through controller-advice infrastructure.

Good uses are representation-level concerns that truly apply across endpoints. Poor uses hide business transformations, silently change unrelated DTOs, or perform expensive remote work during every body conversion.

Advice should implement a precise `supports` decision. An advice that claims every controller/type creates invisible global behavior and can make content negotiation or error responses difficult to debug.

These advice contracts are MVC body-processing hooks, not Spring AOP advice and not Servlet filters.

</details>

- [Back to top](#back-to-top)
