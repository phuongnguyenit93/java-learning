<a id="back-to-top"></a>

# MVC Configuration and Extension Points

## Menu
- [@EnableWebMvc and MVC Infrastructure](#enable-web-mvc)
- [WebMvcConfigurer](#web-mvc-configurer)
- [Path Matching Configuration](#path-matching-configuration)
- [Conversion and Validation Configuration](#conversion-validation-configuration)
- [Message Converter and Content Negotiation Configuration](#message-converter-content-negotiation-configuration)
- [Interceptor, CORS, Resource, and View Configuration](#interceptor-cors-resource-view-configuration)
- [Async Support Configuration](#async-support-configuration)
- [Custom MVC SPIs](#custom-mvc-spis)
- [Framework Defaults, Customization, and Spring Boot Boundary](#framework-defaults-vs-customization)

## <a id="enable-web-mvc">@EnableWebMvc and MVC Infrastructure</a>

<details>
<summary>Click for details</summary>

`@EnableWebMvc` switches on Spring MVC's Java-config infrastructure for an application context. Conceptually, it imports the configuration that registers the central MVC components: handler mappings and adapters, conversion and validation support, message conversion, exception resolvers, resource support, and other collaborators required by the `DispatcherServlet`.

This annotation is therefore not "enable controller scanning". Component scanning and bean discovery are separate container concerns. `@EnableWebMvc` establishes the MVC infrastructure that knows how to interpret controller and web configuration contracts.

Use it when configuring Spring Framework directly and you want the Framework's MVC defaults plus explicit application customization. In a Spring Boot application, adding `@EnableWebMvc` usually means opting out of Boot's MVC auto-configuration model and taking much more direct control, so it should not be added casually.

The learning model is:

```text
Spring container
→ @EnableWebMvc imports MVC configuration support
→ MVC infrastructure beans exist
→ WebMvcConfigurer callbacks customize that infrastructure
```

</details>

- [Back to top](#back-to-top)

---

## <a id="web-mvc-configurer">WebMvcConfigurer</a>

<details>
<summary>Click for details</summary>

`WebMvcConfigurer` is the preferred extension contract when the standard MVC infrastructure is correct but the application needs to tune selected policies. Its default methods let configuration classes contribute settings without subclassing the central Framework configuration.

Typical callbacks cover formatters, validators, path matching, content negotiation, message converters, argument/return-value handlers, interceptors, CORS mappings, resources, views, exception resolvers, and async processing. Multiple configurers can contribute; treat them as configuration collaborators rather than assuming there is one global mutable object.

The key distinction is **extend vs replace**. A callback such as `addFormatters` naturally adds application behavior. Some "configure" callbacks can take over a list or policy more completely. Read the specific callback contract before clearing or replacing defaults.

Prefer `WebMvcConfigurer` over copying Framework configuration into application code. Dropping to lower-level MVC beans is justified when the extension contract cannot express the requirement, not merely because a custom bean feels more explicit.

</details>

- [Back to top](#back-to-top)

---

## <a id="path-matching-configuration">Path Matching Configuration</a>

<details>
<summary>Click for details</summary>

Path matching connects an incoming request path to mappings declared by annotated controllers, functional routes, resources, and other MVC handlers. Configuration therefore affects routing semantics across the application, not just one controller.

`PathMatchConfigurer` is the central hook exposed through `WebMvcConfigurer.configurePathMatch`. Modern Spring MVC can use parsed `PathPattern` matching through `PathPatternParser`; the older string-based `PathMatcher` model exists for compatibility. Parsed patterns are designed for web routing and avoid several ambiguities of arbitrary string matching.

Path configuration is a policy boundary. Changes to trailing-slash behavior, parser options, prefixes, or matcher strategy can alter which handler wins or whether a request matches at all. Such changes should be deliberate and covered by routing tests.

Avoid encoding business versioning or tenant logic into increasingly complex route patterns when a clearer domain boundary would work better. A route that is technically matchable is not automatically a maintainable API design.

</details>

- [Back to top](#back-to-top)

---

## <a id="conversion-validation-configuration">Conversion and Validation Configuration</a>

<details>
<summary>Click for details</summary>

MVC uses conversion and validation as shared infrastructure when it binds request values to handler arguments and model objects. `WebMvcConfigurer.addFormatters` adds application converters and formatters to the MVC conversion service; validation configuration determines the validator used when controller arguments request validation.

This is an integration point, not the primary curriculum owner for `ConversionService`, `Formatter`, `Validator`, or Bean Validation. Those abstractions belong to the validation/data-binding module. Here the important question is how MVC wires them into request handling.

A global formatter is appropriate for a representation convention that should be consistent across controllers, for example a domain identifier or date format. Controller-local differences may be better handled with `@InitBinder` so one endpoint does not silently change conversion rules for the whole application.

Likewise, replacing the MVC validator changes validation behavior across controller processing. Prefer composition and explicit constraints over a global validator that embeds unrelated endpoint-specific policy.

</details>

- [Back to top](#back-to-top)

---

## <a id="message-converter-content-negotiation-configuration">Message Converter and Content Negotiation Configuration</a>

<details>
<summary>Click for details</summary>

Message converters and content negotiation jointly decide how Java values and HTTP representations meet. The configured `HttpMessageConverter` list determines which Java types/media types can be read or written; content-negotiation settings influence which representation is selected for a request.

`WebMvcConfigurer.configureMessageConverters` is a powerful hook: adding converters through this callback turns off the default converter registration that would otherwise occur. `extendMessageConverters` is often safer when the goal is to adjust or add to the already configured set rather than replace the defaults.

Content negotiation should remain predictable. Header-based negotiation through `Accept` is the web-native baseline. If an application enables other strategies, make sure clients can understand them and that the selected strategy cannot accidentally expose a representation that was not intended for a route.

Converter ordering matters when more than one converter can read or write a value. Custom "catch-all" converters should be narrow enough that they do not shadow specialized JSON, text, form, resource, or byte-array handling.

</details>

- [Back to top](#back-to-top)

---

## <a id="interceptor-cors-resource-view-configuration">Interceptor, CORS, Resource, and View Configuration</a>

<details>
<summary>Click for details</summary>

`WebMvcConfigurer` groups several application-facing configuration hooks because they all extend the same MVC request-processing graph:

- `addInterceptors` adds MVC handler interceptors;
- `addCorsMappings` contributes path-based CORS policy;
- `addResourceHandlers` maps static resource locations and resource-chain behavior;
- view-related callbacks register or configure view resolution.

These are not interchangeable. An interceptor executes around mapped MVC handlers; a CORS mapping influences cross-origin request policy; a resource handler owns resource delivery; a view resolver turns logical view outcomes into rendering implementations.

Keep each concern at the narrowest correct layer. For example, do not implement CORS by manually adding headers in an interceptor, and do not implement static resource delivery with a controller if a resource handler already models the requirement.

Central configuration is useful for policies that are truly application-wide. If a concern varies by endpoint, prefer the feature's local contract where one exists so global configuration does not become an invisible dependency.

</details>

- [Back to top](#back-to-top)

---

## <a id="async-support-configuration">Async Support Configuration</a>

<details>
<summary>Click for details</summary>

Async MVC has infrastructure-level settings in addition to controller return types. `WebMvcConfigurer.configureAsyncSupport` exposes `AsyncSupportConfigurer`, where an application can choose an `AsyncTaskExecutor`, set a default timeout, and register interceptors for callable or deferred-result processing.

This matters because returning `Callable` does not make execution capacity appear automatically. The executor is part of the production behavior: queueing, concurrency, rejection, thread naming, context propagation, and shutdown all affect the request lifecycle.

Timeout configuration is also a policy, not merely a number. A timeout should end work coherently, surface an appropriate response path, and avoid leaving external operations running without ownership. Servlet container async timeouts and Spring MVC async processing should be understood together.

Do not confuse this executor configuration with WebFlux's event-loop model. It configures asynchronous work within the Servlet-stack request lifecycle.

</details>

- [Back to top](#back-to-top)

---

## <a id="custom-mvc-spis">Custom MVC SPIs</a>

<details>
<summary>Click for details</summary>

Spring MVC exposes SPIs for cases where the standard programming model needs a new integration point. Examples include custom `HandlerMethodArgumentResolver`, `HandlerMethodReturnValueHandler`, `HandlerExceptionResolver`, `HandlerMapping`, or `HandlerAdapter` implementations.

Choose the narrowest SPI that matches the problem. A custom argument resolver is appropriate for a reusable controller parameter abstraction; replacing the handler adapter to solve the same problem would be far broader and would couple the application to more of MVC's internal orchestration.

Extension points participate in ordered chains. Ordering determines whether the custom component gets a chance to handle a request/value before or after built-in components. A resolver that claims too many parameter types or exceptions can accidentally shadow Framework behavior.

Custom SPI code should preserve MVC contracts such as nullability, binding/validation order, response commitment, async behavior, and exception propagation. Treat it as infrastructure code and test both the intended path and the "not supported, delegate to the next component" path.

</details>

- [Back to top](#back-to-top)

---

## <a id="framework-defaults-vs-customization">Framework Defaults, Customization, and Spring Boot Boundary</a>

<details>
<summary>Click for details</summary>

Spring Framework provides MVC defaults and extension contracts; Spring Boot decides how an application is auto-configured around them. Keeping those roles separate prevents a common source of confusion when documentation shows a Boot property and it is mistaken for a Framework API.

At Framework level, `@EnableWebMvc`, `WebMvcConfigurer`, MVC beans, and their callback contracts are the important mechanisms. Boot may create or customize those pieces conditionally, wire an embedded server, apply configuration properties, and register additional infrastructure.

When a Boot application merely needs to **customize** MVC, contributing a `WebMvcConfigurer` is often compatible with Boot's auto-configuration. Adding `@EnableWebMvc` is a stronger choice because it signals that the application wants direct MVC configuration rather than the normal Boot MVC auto-configuration path.

Production code should know which layer owns a behavior:

```text
Spring Framework contract
→ what MVC can do

Spring Boot auto-configuration
→ which Framework pieces are created/configured automatically

application configuration
→ intentional local policy
```

Debugging becomes much easier when those three layers are not collapsed into one mental model.

</details>

- [Back to top](#back-to-top)
