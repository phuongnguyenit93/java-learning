<a id="back-to-top"></a>

# Annotated Controllers and Handler Methods

## Menu
- [Annotated Controller Model](#annotated-controller-model)
- [Request Mapping, Conditions, and Path Patterns](#request-mapping-and-conditions)
- [Handler Method Inputs](#handler-method-inputs)
- [Model and Session State](#model-and-session-state)
- [Handler Return Values and Direct Response Handling](#handler-return-values-and-response-handling)

## <a id="annotated-controller-model">Annotated Controller Model</a>

<details>
<summary>Click for details</summary>

Annotated controllers let Java methods represent HTTP application endpoints while Spring MVC handles the surrounding request-processing mechanics. A class marked with `@Controller` participates in MVC handler discovery; `@RestController` combines controller semantics with response-body semantics for its handler methods.

The core unit is a `HandlerMethod`: a bean plus a Java method and its metadata. MVC uses mapping annotations to select it, argument resolvers to create its parameters, and return-value handlers to interpret what it returns.

This model is intentionally declarative. The method describes **what request shape it handles** and **what result it produces** rather than manually reading every value from `HttpServletRequest`.

The trade-off is that behavior is distributed across annotations and resolver chains. When a method signature becomes hard to understand without several framework-specific annotations, simplify the endpoint contract rather than adding more hidden binding rules.

</details>

- [Back to top](#back-to-top)

---

## <a id="request-mapping-and-conditions">Request Mapping, Conditions, and Path Patterns</a>

<details>
<summary>Click for details</summary>

`@RequestMapping` and composed annotations such as `@GetMapping` describe request conditions. Conditions can include path, HTTP method, parameters, headers, consumed media types, and produced media types.

Spring MVC combines type-level and method-level mappings, then uses its path-matching infrastructure to find the most specific eligible handler. Modern MVC supports parsed `PathPattern` matching, which is designed for HTTP paths and integrates with variables such as `/orders/{id}`.

Mappings should be unambiguous. If two handler methods match the same request with equal specificity, startup or request-time ambiguity is a design error rather than a routing strategy.

Use mapping conditions to express HTTP contract, not business decisions. For example, route version or media-type conditions can be valid protocol concerns; customer authorization belongs in security/application policy, not in a complicated path predicate.

</details>

- [Back to top](#back-to-top)

---

## <a id="handler-method-inputs">Handler Method Inputs</a>

<details>
<summary>Click for details</summary>

Handler method parameters are created by an ordered set of argument resolvers. This is why a controller can receive values such as:

- `@PathVariable`, `@RequestParam`, `@RequestHeader`, or `@CookieValue`;
- `@RequestBody` or `@ModelAttribute`;
- Servlet request/response types;
- `Principal`, locale-related values, model objects, and other supported MVC abstractions.

Each parameter contract answers a different question. `@RequestBody` asks message converters to decode the HTTP body; `@ModelAttribute` participates in data binding; `@PathVariable` comes from the matched path pattern.

Avoid passing `HttpServletRequest` everywhere merely because it provides universal access. Strongly typed handler arguments document the endpoint and keep parsing/conversion behavior inside reusable MVC infrastructure.

Custom `HandlerMethodArgumentResolver` implementations are appropriate for repeated application-level parameter abstractions, but they should claim a narrow type/annotation shape so they do not shadow built-in resolvers.

</details>

- [Back to top](#back-to-top)

---

## <a id="model-and-session-state">Model and Session State</a>

<details>
<summary>Click for details</summary>

The MVC `Model` is request-processing state destined primarily for view rendering. Controllers can add named attributes explicitly, and `@ModelAttribute` methods can contribute common values before handler execution.

`@SessionAttributes` is different from arbitrary direct use of `HttpSession`. It tells MVC to promote selected model attributes into session storage across a conversational flow and later remove them through `SessionStatus`.

That mechanism is useful for short-lived multi-request workflows, but it should not become a generic application cache. Session state increases lifecycle complexity, affects horizontal scaling, and can retain stale data.

Keep durable business state in the appropriate persistence/domain layer. Use the MVC model for request/view state and session-backed model attributes only when the web interaction itself genuinely spans requests.

</details>

- [Back to top](#back-to-top)

---

## <a id="handler-return-values-and-response-handling">Handler Return Values and Direct Response Handling</a>

<details>
<summary>Click for details</summary>

Controller return values are interpreted by return-value handlers, so the Java return type is part of the MVC contract.

Common outcomes include:

- a logical view name or `ModelAndView`;
- an object written through message converters with `@ResponseBody`/`@RestController`;
- `ResponseEntity` for explicit status, headers, and body;
- redirect/view-navigation results;
- async or streaming types handled by MVC's async infrastructure;
- direct access to the Servlet response when lower-level control is truly needed.

Prefer the highest-level return type that expresses the endpoint requirement. `ResponseEntity` is useful when HTTP metadata is part of the endpoint behavior; directly writing to `HttpServletResponse` bypasses more of MVC's normal return-value pipeline and should be reserved for cases that need that control.

A method returning `void` is not automatically "no response"; its meaning depends on other handler metadata and whether the response has already been handled.

</details>

- [Back to top](#back-to-top)
