<a id="back-to-top"></a>

# Model, View Rendering, and Navigation

## Menu
- [Model and View](#model-and-view)
- [View Resolution and Logical View Names](#view-resolution-and-logical-names)
- [Redirects and Flash Attributes](#redirects-and-flash-attributes)
- [URI Building](#uri-building)
- [View Technology Boundary](#view-technology-boundary)

## <a id="model-and-view">Model and View</a>

<details>
<summary>Click for details</summary>

In server-side MVC rendering, the controller usually prepares **data** while a `View` turns that data into the HTTP response. The `Model` is a map-like collection of named values exposed to that view.

A handler can return a logical view name while populating a `Model`, or return `ModelAndView` when it needs to carry both together.

```text
controller
→ model attributes + logical view outcome
→ ViewResolver
→ View
→ rendered response
```

Keep business computation out of the view layer. A view should format/present already-prepared data rather than trigger repositories or mutate domain state.

This path differs from `@ResponseBody`: response-body handling serializes a representation directly and does not use a normal MVC view.

</details>

- [Back to top](#back-to-top)

---

## <a id="view-resolution-and-logical-names">View Resolution and Logical View Names</a>

<details>
<summary>Click for details</summary>

A logical view name decouples controller code from a concrete rendering implementation. `ViewResolver` implementations translate names such as `"orders/detail"` into `View` objects.

Multiple view resolvers can form an ordered chain. A resolver should either resolve the view it owns or allow the chain to continue; a broad resolver that always returns something can prevent later resolvers from participating.

The resolved `View` then renders the model with the request/response. The exact rendering technology may use templates, JSP integration, feeds, documents, or other mechanisms supported by the configured view stack.

Controllers should normally return semantic/logical names, not filesystem paths to templates. That keeps layout and rendering technology configurable outside application behavior.

</details>

- [Back to top](#back-to-top)

---

## <a id="redirects-and-flash-attributes">Redirects and Flash Attributes</a>

<details>
<summary>Click for details</summary>

A redirect tells the client to issue a new request to another URI. In MVC, a `redirect:` view-name prefix or a `RedirectView` can express this flow.

Because the next request is a new HTTP request, ordinary model attributes are not automatically the same request state. `RedirectAttributes` lets a controller choose redirect-model values that `RedirectView` can use for URI-template expansion; eligible remaining simple values can be appended as query parameters. It separately supports **flash attributes** that are not encoded into the redirect URL.

Flash attributes are short-lived server-side values managed through `FlashMap`/`FlashMapManager` and are typically stored temporarily in the HTTP session. For redirects, Spring stamps the output `FlashMap` with the target path and query parameters so the default manager can match incoming requests more precisely. This greatly reduces accidental consumption by another request, but it is not an absolute delivery guarantee under concurrent requests.

This supports the Post/Redirect/Get pattern:

```text
POST succeeds
→ store short-lived success message
→ redirect
→ matching GET normally receives flash attribute
```

Flash state is for transition data, not durable business state or a general session cache.

</details>

- [Back to top](#back-to-top)

---

## <a id="uri-building">URI Building</a>

<details>
<summary>Click for details</summary>

URI building is safer when application code treats paths, variables, query parameters, and encoding as structured components rather than string concatenation.

`UriComponentsBuilder` provides the generic builder model. Servlet-aware builders and MVC utilities can start from the current request or controller mappings when a server-side application needs links related to its own routes.

```java
URI uri = UriComponentsBuilder
    .fromPath("/orders/{id}")
    .build(42);
```

Structured building prevents common mistakes around separators, encoding, and template expansion. It also keeps link generation closer to route semantics.

When an application runs behind a reverse proxy, externally visible scheme/host/port may differ from the container's direct connection. Forwarded-header processing is therefore a trust boundary; do not blindly honor client-supplied forwarding headers.

</details>

- [Back to top](#back-to-top)

---

## <a id="view-technology-boundary">View Technology Boundary</a>

<details>
<summary>Click for details</summary>

Spring MVC defines the `View` and `ViewResolver` contracts, but it does not own every template language or rendering engine. Technologies such as JSP or third-party template engines bring their own syntax, caching model, escaping behavior, and operational constraints.

The Framework-level knowledge is:

```text
controller outcome
→ logical view resolution
→ View contract
→ rendering technology
```

Choose a rendering technology based on application requirements and understand its escaping/security defaults, but keep technology-specific templating curriculum with that technology.

For APIs whose public contract is JSON/XML/etc., view rendering may not be used at all; message conversion is the more appropriate response path.

</details>

- [Back to top](#back-to-top)
