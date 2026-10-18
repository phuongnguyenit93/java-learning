<a id="back-to-top"></a>

# The Spring Container Model

## Menu
- [BeanDefinition and Configuration Metadata](#bean-definition-metadata)
- [BeanFactory and the Foundation of the Container](#bean-factory-role)
- [How ApplicationContext Extends BeanFactory](#application-context-role)
- [How the Container Assembles the Dependency Graph](#object-graph-assembly)
- [Bootstrap, Registration, and Context Refresh](#container-bootstrap-refresh)

## <a id="bean-definition-metadata">BeanDefinition and Configuration Metadata</a>

<details>
<summary>Click for details</summary>

Spring does not begin by creating arbitrary objects. It first works with metadata that describes **what should exist** and **how it should be created**. The main internal representation of that metadata is `BeanDefinition`.

A `BeanDefinition` is closer to a recipe than to the final object. Depending on how the bean was registered, it may describe a bean class, factory method, scope, constructor arguments, property values, initialization callbacks, lazy behavior, dependency metadata, and other container instructions. The actual instance may not exist yet.

This distinction is foundational:

```text
BeanDefinition = description / recipe
bean instance   = object created from that description
```

Different configuration styles eventually contribute metadata to the same container model. `@Bean`, component scanning, XML definitions, and programmatic registration look different to application code, but the container ultimately needs definitions it can reason about before it instantiates objects.

Thinking in terms of definitions explains why Spring can inspect and modify configuration before bean creation. Infrastructure such as `BeanFactoryPostProcessor` can operate on bean-definition metadata, while later mechanisms such as `BeanPostProcessor` operate on actual bean instances. That separation becomes important in the lifecycle and extension-point chapters.

Avoid treating `BeanDefinition` as an application-domain abstraction. Most application code should not manipulate it directly. It belongs to the container model and is most useful when understanding bootstrap, infrastructure extensions, and debugging registration problems.

</details>

- [Back to top](#back-to-top)

---

## <a id="bean-factory-role">BeanFactory and the Foundation of the Container</a>

<details>
<summary>Click for details</summary>

`BeanFactory` is the foundational Spring contract for obtaining and creating managed beans. Full container implementations typically back that contract with bean-definition metadata, creating and wiring instances when necessary according to that metadata and scope.

At its simplest, application code can ask a `BeanFactory` for a bean by name or type. Internally, however, the important behavior is broader: the factory tracks definitions, resolves dependencies, manages singleton instances, coordinates creation, and participates in lifecycle processing through infrastructure installed into the factory.

One useful distinction is between the **container contract** and a concrete implementation. `DefaultListableBeanFactory` is a common implementation used under `ApplicationContext`, but application code usually programs against higher-level abstractions instead of constructing and managing the factory directly.

`BeanFactory` also helps explain lazy behavior. Merely registering a definition does not necessarily create its object. Depending on the container and configuration, a bean may be instantiated only when requested. `ApplicationContext` changes the normal application experience because it eagerly creates non-lazy singleton beans during refresh by default.

In ordinary Spring applications, `BeanFactory` is mainly the conceptual and infrastructure foundation. `ApplicationContext` is preferred for application-level use because it includes the same bean-factory capabilities plus additional framework services and lifecycle orchestration.

</details>

- [Back to top](#back-to-top)

---

## <a id="application-context-role">How ApplicationContext Extends BeanFactory</a>

<details>
<summary>Click for details</summary>

`ApplicationContext` extends the bean-container model into an application framework context. It is still a `BeanFactory`, so it can resolve and provide beans, but it adds services that applications and Spring infrastructure commonly need around those beans.

Major additions include resource loading, application events, message resolution for internationalization, environment/property access, and parent-child context relationships. It also coordinates the refresh lifecycle that prepares the bean factory, invokes container post-processors, registers bean post-processors, initializes infrastructure, and creates eager singleton beans.

That is why application code normally starts from a context implementation such as `AnnotationConfigApplicationContext` rather than from a bare `DefaultListableBeanFactory`:

```java
try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
    OrderService service = context.getBean(OrderService.class);
}
```

The example is intentionally small, but the key point is ownership: the context controls bootstrap and shutdown, while the application consumes the resulting graph.

Do not interpret `ApplicationContext` as a global service locator that business code should query everywhere. Although lookup APIs exist, dependency injection is the normal way application beans receive collaborators. Direct context access is more appropriate for framework integration, bootstrap code, diagnostics, or genuinely dynamic lookup scenarios.

</details>

- [Back to top](#back-to-top)

---

## <a id="object-graph-assembly">How the Container Assembles the Dependency Graph</a>

<details>
<summary>Click for details</summary>

The container's central job is to turn independent definitions into a connected object graph. It does this by repeatedly answering a question: **for this dependency point, which managed object is eligible and how should it be obtained?**

Suppose `CheckoutService` requires `PaymentGateway`, and `StripeGateway` itself requires `HttpClient`. Conceptually, the graph looks like:

```text
CheckoutService
      ↓
PaymentGateway
      ↓
HttpClient
```

Spring starts from registered definitions, determines the dependencies needed to create each bean, selects candidate definitions, creates missing collaborators as required, and injects the resulting instances. For singleton beans, those instances are retained by the container and reused according to singleton scope.

The graph is not necessarily created in a simple file order. Dependency relationships drive creation. If bean A needs bean B, B may be created first even if A was registered earlier. Explicit ordering mechanisms such as `depends-on` can add constraints, but normal dependency edges already influence creation order.

This graph model also explains common failures. A missing edge target produces an unsatisfied dependency. Multiple equally eligible targets create ambiguity. A cycle can make construction impossible or force Spring into special early-reference behavior depending on the injection style. Those cases are easier to reason about when you picture the container as a graph assembler rather than an annotation scanner.

</details>

- [Back to top](#back-to-top)

---

## <a id="container-bootstrap-refresh">Bootstrap, Registration, and Context Refresh</a>

<details>
<summary>Click for details</summary>

Container startup has two conceptually different phases: **registration** and **realization**. First, Spring gathers configuration metadata and registers bean definitions. Then the application context is refreshed, which turns that metadata into a functioning container.

For an annotation-based context, a simplified sequence is:

```text
create context
   ↓
register configuration classes / scan packages
   ↓
refresh context
   ↓
process bean definitions
   ↓
install bean post-processors and context infrastructure
   ↓
create non-lazy singleton beans
   ↓
publish a ready context
```

`refresh()` is therefore much more than "create beans". It establishes the bean factory state, invokes `BeanFactoryPostProcessor` implementations before ordinary bean instantiation, registers `BeanPostProcessor` implementations, initializes context services, and pre-instantiates non-lazy singletons. A failure in any of these stages can abort startup.

The practical implication is that many configuration errors are intentionally discovered during context refresh rather than later on first business use. Missing required dependencies, invalid bean creation logic, or initialization failures often cause startup to fail fast.

Some context implementations refresh automatically during construction, while others expose explicit registration and refresh steps. Regardless of API shape, keep the mental model separate: metadata must be registered before it can be processed and materialized into managed instances.

</details>

- [Back to top](#back-to-top)
