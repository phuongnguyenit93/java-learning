<a id="back-to-top"></a>

# Context Configuration

## Menu
- [Test context configuration model](#context-configuration-model)
- [Configuration classes and resource locations](#configuration-classes-and-locations)
- [Context loader selection and defaults](#context-loader-selection)
- [Configuration inheritance and nested tests](#configuration-inheritance)
- [Context hierarchies](#context-hierarchy)
- [WebApplicationContext configuration](#web-application-context-configuration)

## <a id="context-configuration-model">Test context configuration model</a>

<details>
<summary>Click for details</summary>

`@ContextConfiguration` describes how the TestContext Framework should build the test `ApplicationContext`. It can point to configuration classes, resource locations, context initializers, and an explicit loader.

The annotation itself does not eagerly create a context. Its metadata is merged across the test hierarchy into a `MergedContextConfiguration`. That merged model is later used both to load the context and to identify reusable entries in the context cache.

A useful distinction is:

```text
test annotation metadata
        ↓
MergedContextConfiguration
        ↓
cache lookup
        ↓ miss
SmartContextLoader creates ApplicationContext
```

That flow explains why seemingly small configuration differences can prevent cache reuse later.

</details>

- [Back to top](#back-to-top)

---

## <a id="configuration-classes-and-locations">Configuration classes and resource locations</a>

<details>
<summary>Click for details</summary>

Tests commonly configure a context with Java configuration classes:

```java
@ContextConfiguration(classes = TestConfig.class)
class PricingIntegrationTests {
}
```

or with resource locations such as XML/Groovy configuration where that style is used:

```java
@ContextConfiguration("classpath:/pricing-test.xml")
class PricingIntegrationTests {
}
```

When configuration is omitted, the selected loader may perform default detection, for example looking for conventional resources or suitable nested configuration classes. This is **TestContext default detection**, not Spring Boot auto-configuration.

Do not assume every loader can freely mix arbitrary classes and locations. The configured `SmartContextLoader` defines which forms it supports.

</details>

- [Back to top](#back-to-top)

---

## <a id="context-loader-selection">Context loader selection and defaults</a>

<details>
<summary>Click for details</summary>

Normally you let the TestContext bootstrapper choose the loader. For non-web tests this is typically a delegating smart loader that selects an annotation-config or resource-based path from the metadata. Web tests use the corresponding web-aware strategy.

An explicit loader is available through `@ContextConfiguration(loader = ...)`, but it is a low-level choice. Use it only when the default strategy cannot express the required configuration model.

`SmartContextLoader` receives the merged test configuration, including active profiles, property sources, initializers, parent configuration, and context customizers. The loader's job is to create a fully configured context; the cache-aware delegate decides whether loading is necessary at all.

Custom loaders are framework-extension points. Application tests usually become easier to understand when they stay with normal configuration annotations.

</details>

- [Back to top](#back-to-top)

---

## <a id="configuration-inheritance">Configuration inheritance and nested tests</a>

<details>
<summary>Click for details</summary>

Context configuration is inherited by default. A subclass can extend configuration declared on a superclass instead of repeating it. The key `@ContextConfiguration` controls are `inheritLocations` and `inheritInitializers`, both enabled by default.

Setting an inheritance flag to `false` changes the model from “extend” to “shadow” for that configuration dimension. This matters for both the resulting bean graph and the context cache key.

Nested test classes also inherit enclosing TestContext configuration by default. `@NestedTestConfiguration` can switch that behavior to `OVERRIDE` when a nested test needs an independent Spring test configuration.

Prefer inheritance when subclasses genuinely test the same application slice with small additions. Prefer override when inherited configuration would make the child test's evidence ambiguous.

</details>

- [Back to top](#back-to-top)

---

## <a id="context-hierarchy">Context hierarchies</a>

<details>
<summary>Click for details</summary>

`@ContextHierarchy` models parent/child `ApplicationContext` relationships in tests. It is useful when production itself has layered contexts, for example a shared root context with a web child context.

Each hierarchy level can have a `name`. When a subclass declares a hierarchy with matching level names, configuration can be merged at the corresponding level; a level can also be overridden instead of extended.

Parent/child relationships are part of the merged configuration and therefore matter to context caching. A child context can see beans from its parent; the parent cannot see beans defined only in the child.

Do not introduce a hierarchy merely to organize test configuration files. It should represent a real application-context relationship worth verifying.

One important Spring 6.1 boundary: TestContext AOT processing does not support `@ContextHierarchy`. That limitation becomes relevant in the later AOT section.

</details>

- [Back to top](#back-to-top)

---

## <a id="web-application-context-configuration">WebApplicationContext configuration</a>

<details>
<summary>Click for details</summary>

`@WebAppConfiguration` tells TestContext that the integration test needs a `WebApplicationContext`, not only a generic `ApplicationContext`.

It must be used together with `@ContextConfiguration` somewhere in the test hierarchy. The default web resource base path is `src/main/webapp`; an explicit path can be supplied when the application uses another layout.

Spring creates servlet test infrastructure rather than starting a real servlet container. The resulting context is backed by a mock `ServletContext`, which is sufficient for `MockMvc` and web-scoped fixture testing.

The web resource base path participates in context identity. Two otherwise identical test configurations with different `@WebAppConfiguration` resource paths do not represent the same cached context.

### References

- [Spring Framework 6.1.14 API — @WebAppConfiguration](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/web/WebAppConfiguration.html)

</details>

- [Back to top](#back-to-top)
