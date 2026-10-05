<a id="back-to-top"></a>

# ApplicationContext Platform Services

## Menu
- [The Resource Abstraction](#resource-abstraction)
- [Resource Loading and Pattern Resolution](#resource-loading-and-patterns)
- [Application Events in the Context](#application-events)
- [ApplicationListener and @EventListener](#event-listeners)
- [MessageSource and Internationalization](#message-source-i18n)
- [Parent-child ApplicationContext Hierarchies](#context-hierarchy)
- [Boundary with Spring Messaging and Web Stacks](#context-service-boundaries)

## <a id="resource-abstraction">The Resource Abstraction</a>

<details>
<summary>Click for details</summary>

Spring's `Resource` abstraction lets code describe and access a resource without assuming that it is a normal operating-system file. The same consumer can work with classpath content, filesystem content, URLs, or another resource implementation through one contract.

```java
final class TemplateReader {
    String read(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

The important mental model is:

```text
Resource
→ handle describing a location and how to access it

not

Resource
→ guaranteed java.io.File
```

That distinction matters when an application is packaged. A classpath resource may live inside a JAR and still provide an `InputStream`, while `getFile()` cannot turn an entry inside that JAR into a normal filesystem file. Code that only needs to read bytes should prefer stream-based access instead of forcing every resource through `File`.

Common implementations such as `ClassPathResource`, `FileSystemResource`, and `UrlResource` express different location semantics behind the same interface. Application code should usually receive a `Resource` or `ResourceLoader` when resource location is configurable rather than constructing one implementation everywhere.

`Resource` is infrastructure for locating data. It does not decide the higher-level meaning of that data, such as MVC static-resource handling, message bundles, or configuration loading.

### References

- Spring Framework Reference — Resources

</details>

- [Back to top](#back-to-top)

---

## <a id="resource-loading-and-patterns">Resource Loading and Pattern Resolution</a>

<details>
<summary>Click for details</summary>

`ResourceLoader` turns a location string into one `Resource`. Prefixes make the intended lookup strategy explicit:

```java
Resource classpath = resourceLoader.getResource("classpath:catalog/default.json");
Resource file = resourceLoader.getResource("file:/opt/app/catalog.json");
Resource remote = resourceLoader.getResource("https://example.test/catalog.json");
```

Without a prefix, the concrete `ResourceLoader` decides how to interpret the path. That is why an unqualified location should be used only when context-specific behavior is intentional.

`ResourcePatternResolver` extends the model from one location to a set of matching resources. A standard `ApplicationContext` implements this contract:

```java
Resource[] mappings =
        context.getResources("classpath*:/META-INF/acme/*.json");
```

`classpath:` normally addresses a single classpath resource location. `classpath*:` asks for all matching resources across the classpath and is especially useful when multiple JARs contribute files under the same path. Pattern resolution can also use supported Ant-style path patterns.

Do not design code around a specific physical filesystem layout merely because development runs from exploded class directories. Packaged JARs change what can be represented as a `File`, while the `Resource`/stream contract continues to work.

Also separate lookup from parsing. `ResourceLoader` answers "where and how can I access these bytes?"; JSON, XML, properties, templates, and other formats should be interpreted by the component that owns that format.

</details>

- [Back to top](#back-to-top)

---

## <a id="application-events">Application Events in the Context</a>

<details>
<summary>Click for details</summary>

`ApplicationContext` also acts as an `ApplicationEventPublisher`. Application events provide a lightweight observer mechanism for communication between Spring-managed components through the context's event infrastructure.

An event can be an ordinary application object:

```java
record CatalogRefreshed(String catalogId) {}

@Component
final class CatalogService {
    private final ApplicationEventPublisher events;

    CatalogService(ApplicationEventPublisher events) {
        this.events = events;
    }

    void refresh(String id) {
        // update the catalog...
        events.publishEvent(new CatalogRefreshed(id));
    }
}
```

Publishing means handing the event to Spring's event multicaster. The publisher API itself does not promise durability or cross-process delivery. With the default `SimpleApplicationEventMulticaster`, matching listeners are invoked synchronously in the publishing thread, so `publishEvent(...)` does not return until those listeners finish.

That default is useful because listener code naturally sees the publisher thread's execution context, including an active transaction when one is present. It also means listener latency and failure are part of the publishing call unless the multicaster/error strategy is deliberately changed.

Use application events to decouple in-process reactions where the publisher should not know every listener. Do not use them when the requirement is durable messaging, broker delivery, retries across process failure, or communication with another service.

Spring also publishes context lifecycle events such as `ContextRefreshedEvent` and `ContextClosedEvent`. Those events describe context state; they are distinct from bean initialization callbacks discussed in the lifecycle chapter.

### References

- Spring Framework Reference — Standard and Custom Events

</details>

- [Back to top](#back-to-top)

---

## <a id="event-listeners">ApplicationListener and @EventListener</a>

<details>
<summary>Click for details</summary>

There are two common listener styles. The interface style makes the event type part of the Java type:

```java
@Component
final class ContextMetrics
        implements ApplicationListener<ContextRefreshedEvent> {

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // record that this context completed a refresh
    }
}
```

The annotation style keeps the listener as an ordinary method:

```java
@Component
final class SearchIndexListener {

    @EventListener
    void onCatalogRefreshed(CatalogRefreshed event) {
        // rebuild the relevant index
    }
}
```

`ApplicationListener<E>` is the classic typed contract for `ApplicationEvent` types. `@EventListener` infers the event type from the method parameter in the common case and also works naturally with arbitrary objects published as payload events, such as the `CatalogRefreshed` record above. It supports filtering and ordering features without forcing the class to implement a listener interface.

If listener order actually matters, `@Order` can influence the order in which matching listeners are invoked by the normal multicaster. Treat ordering as a last-mile coordination tool, not as a substitute for a missing dependency. If listener B cannot be correct unless A has completed, an explicit service workflow may describe that requirement better than two loosely coupled observers.

Keep default synchronous behavior in mind. A slow listener slows the publisher. Moving event work to another executor changes thread-local, transaction, exception, and completion semantics; those concurrency choices should be made deliberately and belong to the Spring concurrency learning area in depth.

An event listener should also be prepared for the semantic level of the event. Prefer facts that have already happened, such as `CatalogRefreshed`, over command-like events where the publisher secretly depends on exactly one listener to perform mandatory business work.

</details>

- [Back to top](#back-to-top)

---

## <a id="message-source-i18n">MessageSource and Internationalization</a>

<details>
<summary>Click for details</summary>

`MessageSource` is Spring Framework's abstraction for resolving a message by code, arguments, and `Locale`. `ApplicationContext` extends `MessageSource`, so message resolution is a built-in context service rather than a web-only feature.

```java
String text = messageSource.getMessage(
        "order.not-found",
        new Object[] { orderId },
        Locale.ENGLISH);
```

A bundle might contain:

```properties
order.not-found=Order {0} was not found
```

The message code is stable application metadata; the concrete text can vary by locale. Arguments are formatted into the resolved message using the underlying message-formatting rules.

When an `ApplicationContext` starts, it looks for a bean named exactly `messageSource`. If it finds one, context message calls delegate to it. If the current context has no such source, a parent context can provide one; if none is available, the context still exposes the `MessageSource` contract through an empty delegating implementation.

Common implementations include `ResourceBundleMessageSource`, based on the JDK resource-bundle model, and `ReloadableResourceBundleMessageSource`, which supports Spring resource locations and configurable caching/reloading behavior.

Use `MessageSource` for resolvable human-facing or domain messages whose wording varies by locale. Locale selection for an HTTP request, validation error transport, or view rendering belongs to the owning web/validation modules; this section owns the core resolution service itself.

### References

- Spring Framework Reference — Internationalization using MessageSource

</details>

- [Back to top](#back-to-top)

---

## <a id="context-hierarchy">Parent-child ApplicationContext Hierarchies</a>

<details>
<summary>Click for details</summary>

An `ApplicationContext` can have a parent. The child context can resolve beans from its own bean factory and, when needed, fall back to the parent. The parent does not gain visibility into beans defined only in the child.

```text
parent context
  sharedRepository
  auditService
        ↑ visible to child
        │
child context
  featureController
  localFormatter
```

If a child defines a bean with the same name as a parent bean, the child definition takes priority for lookup from that child. This allows a shared parent to provide defaults or common infrastructure while a child has a more specialized local graph.

Hierarchy is different from bean-definition inheritance. A parent `ApplicationContext` is a live container with its own beans and lifecycle; it is not merely a template from which child bean definitions copy metadata.

Context services can also have hierarchy-aware behavior. `MessageSource`, for example, can delegate unresolved message codes to a parent message source. Event publication has its own hierarchy rule as well: with the standard `AbstractApplicationContext` behavior, an event published in a child is propagated upward to its parent, while an event published in the parent is not automatically broadcast down to children. Bean lookup still follows its own visibility direction: child-to-parent lookup is available; parent-to-child lookup is not.

Use a hierarchy when there is a real container boundary with shared parent infrastructure and independently configured child concerns. Do not create extra contexts merely to organize packages. Multiple contexts introduce separate bean factories, lifecycle ownership, event multicasting, and local infrastructure that can make debugging more difficult.

Web applications are a common place where context hierarchies appear, but the Servlet/MVC request-processing model and its context setup belong to the web module.

</details>

- [Back to top](#back-to-top)

---

## <a id="context-service-boundaries">Boundary with Spring Messaging and Web Stacks</a>

<details>
<summary>Click for details</summary>

The services in this chapter are **ApplicationContext infrastructure**:

```text
Resource / ResourceLoader
ApplicationEventPublisher + context listeners
MessageSource
parent-child context relationships
```

They are reused by higher Spring layers, but reuse does not transfer ownership of their deep semantics.

Application events are in-process context events. Spring Messaging's `Message`, `MessageChannel`, broker-oriented flows, WebSocket/STOMP, and RSocket programming models solve a different class of communication problems and belong to the messaging module. Do not assume that `publishEvent(...)` provides broker semantics, persistence, acknowledgement, or remote delivery.

Likewise, web applications use resources, messages, events, and sometimes context hierarchies, but MVC/WebFlux own request dispatch, controller invocation, web binding, request-scoped behavior, HTTP locale resolution, static-resource serving, and web-specific lifecycle details.

A useful ownership test is:

```text
Can the concept be explained for a plain ApplicationContext
without an HTTP request or broker?
    → core-container owns the foundation

Does the behavior depend on MVC/WebFlux dispatch
or Spring Messaging transport/channel semantics?
    → hand off to that module
```

Keeping this boundary prevents `ApplicationContext` from becoming a catch-all chapter for every framework feature that happens to consume the context.

</details>

- [Back to top](#back-to-top)
