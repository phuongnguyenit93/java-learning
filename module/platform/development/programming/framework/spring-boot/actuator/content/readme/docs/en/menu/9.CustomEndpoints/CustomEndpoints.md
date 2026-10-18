<a id="back-to-top"></a>

# Authoring Custom Actuator Endpoints

## Menu
- [When Does an Application Need a Custom Management Endpoint?](#custom-endpoint-purpose)
- [How Do @ReadOperation, @WriteOperation, and @DeleteOperation Define Operations?](#endpoint-operation-annotations)
- [Why Prefer a Technology-Agnostic @Endpoint When Possible?](#technology-agnostic-custom-endpoints)
- [When Are Web- or JMX-specific Endpoints Appropriate?](#web-jmx-specific-endpoints)
- [When Should an Existing Endpoint Be Extended Instead?](#endpoint-extensions)
- [How Do Custom Actuator Endpoints Stay Separate from Business APIs?](#business-api-boundary)

## <a id="custom-endpoint-purpose">When Does an Application Need a Custom Management Endpoint?</a>

<details>
<summary>Click for details</summary>

A custom Actuator endpoint is appropriate when an application has an operational management need that is not covered by the built-in endpoint catalog. Good examples are inspecting a bounded piece of internal operational state, triggering a carefully controlled maintenance action, or exposing management metadata used by deployment/operations tooling.

The requirement should be operational rather than business-facing. If ordinary customers or domain clients need the capability as part of the product workflow, it belongs in the business API even if an operator might also call it. Actuator endpoints carry management assumptions about exposure, authorization, and transport mapping.

Before adding a custom endpoint, check whether an existing built-in endpoint, health contributor, metric, or info contribution already represents the need. Extending the management surface creates another privileged contract that must be documented and secured.

### References

- [Spring Boot 3.3 — Implementing Custom Endpoints](https://docs.spring.io/spring-boot/3.3/reference/actuator/endpoints.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="endpoint-operation-annotations">How Do @ReadOperation, @WriteOperation, and @DeleteOperation Define Operations?</a>

<details>
<summary>Click for details</summary>

A bean annotated with @Endpoint declares a management endpoint ID. Methods annotated with @ReadOperation, @WriteOperation, or @DeleteOperation declare the operations Actuator can expose for that endpoint. The annotations describe management intent rather than tying the method directly to one transport.

A read operation retrieves management state. A write operation changes or performs a management action. A delete operation removes management state when that semantic is meaningful. The web and JMX infrastructure map those operations to their transport-specific representations.

Operation inputs are method parameters. They are required by default and can be made optional with `@Nullable`. Technology-agnostic endpoints support simple parameter types rather than one complex DTO that receives an entire JSON body; for web writes, root JSON properties map to individual parameters. Endpoint implementations must retain Java parameter names with `-parameters`, which the Boot Gradle plugin and Maven `spring-boot-starter-parent` configure automatically.

Before invocation, Actuator converts HTTP or JMX input through `ApplicationConversionService` and any `@EndpointConverter` converters. A parameter annotated with `@Selector` becomes a path segment for web exposure, so it can select a subset of endpoint data without turning the endpoint into a controller. Keep inputs and outputs small and explicit, and make write-operation side effects and preconditions clear.

</details>

- [Back to top](#back-to-top)

---

## <a id="technology-agnostic-custom-endpoints">Why Prefer a Technology-Agnostic @Endpoint When Possible?</a>

<details>
<summary>Click for details</summary>

Prefer @Endpoint when the management capability itself does not depend on HTTP or JMX. Boot can then expose the same endpoint model through supported technologies according to configuration. This keeps the core management code reusable across web stacks and avoids importing controller concepts into an operational abstraction.

Technology independence also improves testing and maintenance. The endpoint method can focus on its input, operational service, and output while Actuator owns transport mapping. A future change from one web stack to another does not require rewriting the management capability.

Do not force technology independence when the requirement truly depends on transport behavior. A custom operation that needs Actuator web-specific status or content-type semantics can use the narrower web endpoint/extension model rather than hiding HTTP concerns inside a generic endpoint. If it must control arbitrary HTTP response headers or other framework-specific request/response behavior, use an appropriate Spring MVC/WebFlux controller or infrastructure extension point instead. Binary output alone does not require a web-specific endpoint: a generic `@Endpoint` web operation can return a `Resource`, which Actuator serves as `application/octet-stream`; Spring MVC and WebFlux also support range requests for such resources.

</details>

- [Back to top](#back-to-top)

---

## <a id="web-jmx-specific-endpoints">When Are Web- or JMX-specific Endpoints Appropriate?</a>

<details>
<summary>Click for details</summary>

Spring Boot provides @WebEndpoint and @JmxEndpoint for endpoints that should exist only on one management technology. A web-specific endpoint can model a capability that is meaningful only when exposed over HTTP, while a JMX-specific endpoint can depend on JMX representation without pretending to be transport-neutral.

Technology-specific endpoints are a deliberate trade-off. They give access to a narrower transport contract but sacrifice portability to the other management technology. Use them because the operation requires that contract, not simply because the developer is more familiar with controllers or HTTP.

If full Spring MVC/WebFlux framework behavior is required, a normal controller may be the correct implementation, but at that point it is no longer a technology-agnostic Actuator endpoint and should be designed and secured with that distinction visible.

</details>

- [Back to top](#back-to-top)

---

## <a id="endpoint-extensions">When Should an Existing Endpoint Be Extended Instead?</a>

<details>
<summary>Click for details</summary>

Sometimes the operational concept already has a built-in endpoint, but one exposure technology needs an extra operation or representation. Actuator supports endpoint extensions such as @EndpointWebExtension and @EndpointJmxExtension so technology-specific behavior can augment the existing endpoint instead of creating an unrelated duplicate ID.

An extension should preserve the meaning of the endpoint it augments. If new behavior represents an entirely different operational responsibility, a separate custom endpoint is usually clearer. Extensions are for technology-specific additions, not a way to bypass the endpoint model's ownership boundaries.

Before extending a built-in endpoint, consider upgrade cost. Built-in endpoint contracts can evolve with Boot. Keep the extension small, test it against the repository's Boot 3.3 baseline, and avoid coupling to internal implementation details that are not part of the supported extension API.

</details>

- [Back to top](#back-to-top)

---

## <a id="business-api-boundary">How Do Custom Actuator Endpoints Stay Separate from Business APIs?</a>

<details>
<summary>Click for details</summary>

Management endpoints and business APIs can both use HTTP, but their contracts serve different users. A business API models domain capabilities and is part of the product contract. A custom Actuator endpoint models application operation, diagnosis, or maintenance and belongs to the management surface.

Do not put normal CRUD or customer workflows behind @Endpoint merely to receive Actuator exposure/security conveniences. That makes endpoint discovery, authorization, versioning, and client expectations harder to understand. Conversely, a privileged maintenance operation should not be hidden among public domain controllers if it is fundamentally an operations capability.

Ask who calls the operation, why they call it, and what happens if it is exposed accidentally. Those questions usually reveal the correct surface more reliably than the fact that both surfaces can return JSON.

</details>

- [Back to top](#back-to-top)
