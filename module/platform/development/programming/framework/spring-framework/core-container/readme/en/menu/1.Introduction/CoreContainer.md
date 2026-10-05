<a id="back-to-top"></a>

# Spring IoC Container

## Menu
- [Why Does the Spring IoC Container Exist?](#container-purpose)
- [Managed Beans vs Plain Java Objects](#managed-beans-vs-plain-objects)
- [What Is Inversion of Control?](#inversion-of-control)
- [How Dependency Injection Implements IoC](#dependency-injection-role)
- [Core Container Boundaries in the Spring Ecosystem](#container-module-boundary)

## <a id="container-purpose">Why Does the Spring IoC Container Exist?</a>

<details>
<summary>Click for details</summary>

When an application is small, creating collaborators directly with `new` feels simple. The problem appears as the object graph grows: a service must know which concrete repository to create, the repository may need a data source, another component may need the same service, and construction policy becomes scattered through business code. Changes to wiring then spread across many classes even when the business behavior itself has not changed.

The Spring IoC Container exists to centralize that assembly responsibility. Application classes describe what they need; container configuration describes which objects exist and how they relate. At runtime, the container creates those objects, resolves their dependencies, applies lifecycle rules, and exposes a ready object graph to the application.

A useful mental model is:

```text
configuration metadata
        ↓
bean definitions
        ↓
dependency resolution
        ↓
bean creation + lifecycle
        ↓
usable application object graph
```

This separation matters because wiring policy changes for different reasons than domain behavior. A payment service should focus on payment rules; it should not also decide whether to construct a JDBC repository, an in-memory repository, or a test double. Moving that decision to the container lowers coupling and makes alternative compositions easier to test and maintain.

The container does not remove object construction. It moves construction and composition into a managed boundary where Spring can apply consistent rules. You still design classes, constructors, interfaces, and ownership boundaries; Spring manages how those pieces are assembled.

</details>

- [Back to top](#back-to-top)

---

## <a id="managed-beans-vs-plain-objects">Managed Beans vs Plain Java Objects</a>

<details>
<summary>Click for details</summary>

A plain Java object and a Spring bean can be instances of exactly the same class. The important difference is not the class declaration; it is who owns the instance and its lifecycle.

If application code creates an object directly, Spring does not automatically know about that instance:

```java
InvoiceService service = new InvoiceService(repository);
```

If the same class is registered with the container, Spring creates or obtains the instance according to a `BeanDefinition`, resolves its collaborators, applies relevant post-processing and lifecycle callbacks, and manages it according to its configured scope.

That distinction explains many common surprises. An object created manually does not automatically receive container-managed dependency injection, lifecycle callbacks, proxies, scope behavior, or other infrastructure that is applied through the bean lifecycle. Likewise, merely adding a Spring annotation to a class does not make every instance of that class a bean; only instances that enter the container through registration and creation paths are managed.

Treat "bean" as a role in the container rather than a special kind of Java object. The same class may have one managed instance and another manually-created instance, and those two objects can behave differently because only one passed through Spring's management pipeline.

</details>

- [Back to top](#back-to-top)

---

## <a id="inversion-of-control">What Is Inversion of Control?</a>

<details>
<summary>Click for details</summary>

Inversion of Control (IoC) describes a change in who controls an important part of program flow or object assembly. Instead of application code deciding when and how every collaborator is created, the framework owns that control point and calls into application code according to a defined contract.

For the Core Container, the inverted responsibility is primarily object creation and composition. Without IoC, a class or bootstrap routine might choose concrete implementations and build the entire graph itself. With Spring, configuration supplies metadata and the container decides how to realize that metadata as managed objects.

IoC is broader than Dependency Injection. A framework callback, lifecycle hook, or template-style API can also invert control. Dependency Injection is the main container mechanism Spring uses to realize IoC for object graphs: dependencies are supplied to an object instead of being discovered or constructed by that object.

This distinction is useful because it prevents two common misunderstandings. First, IoC does not mean "Spring controls all application logic"; business methods still run according to application behavior. Second, IoC is not an annotation feature. XML, Java configuration, programmatic registration, or annotation-based discovery can all describe objects managed under the same IoC principle.

</details>

- [Back to top](#back-to-top)

---

## <a id="dependency-injection-role">How Dependency Injection Implements IoC</a>

<details>
<summary>Click for details</summary>

Dependency Injection (DI) makes the IoC idea concrete by supplying collaborators from the outside. A class declares its dependency contract, normally through a constructor, setter, or field, while the container selects a matching bean and provides it.

For example:

```java
final class OrderService {
    private final OrderRepository repository;

    OrderService(OrderRepository repository) {
        this.repository = repository;
    }
}
```

`OrderService` does not create `JdbcOrderRepository`, search a global registry, or know which environment-specific implementation is active. Its dependency is explicit in the constructor. Spring's responsibility is to find an eligible `OrderRepository` bean and call the constructor with that instance.

DI therefore separates two decisions: the class decides **what capability it requires**, while configuration decides **which concrete object satisfies that requirement**. This makes object contracts easier to understand and allows the same class to be composed differently in production, tests, or another application context.

DI works best when application classes remain honest about their required collaborators. Hiding dependencies behind static access, global context lookup, or internal `new` calls weakens the benefit because the object graph becomes harder to inspect and substitute. Later chapters examine how Spring chooses candidates and how constructor, setter, and field injection differ.

</details>

- [Back to top](#back-to-top)

---

## <a id="container-module-boundary">Core Container Boundaries in the Spring Ecosystem</a>

<details>
<summary>Click for details</summary>

The Core Container is the part of Spring Framework concerned with object definitions, dependency resolution, scopes, lifecycle, environment-aware registration, and the services exposed by `ApplicationContext`. Its central abstractions include `BeanFactory`, `ApplicationContext`, `BeanDefinition`, bean scopes, lifecycle callbacks, and container extension points.

This module deliberately stops at that boundary. It introduces only enough neighboring concepts to explain how the container connects to them:

- Spring AOP may wrap managed beans with proxies, but advice and pointcut design belong to the AOP module.
- Transaction and cache abstractions often rely on managed beans and proxies, but transaction/cache semantics belong to their own modules.
- MVC, WebFlux, and Spring Messaging consume container-managed components, but request processing, reactive pipelines, STOMP, and broker behavior are separate concerns.
- Spring Boot builds on this container and adds auto-configuration, Config Data, and Boot-specific conditional annotations. Those are not Core Container semantics.

Keeping this boundary clear matters when debugging. If a failure says Spring cannot resolve a bean, scope, property, or lifecycle dependency, start with the container model. If the bean exists but a transaction, web request, or message interaction behaves incorrectly, the owning module may be the better place to investigate.

The rest of this module follows the container from metadata to a usable object graph, then adds registration, resolution, scope, lifecycle, environment services, and extension points in that order.

</details>

- [Back to top](#back-to-top)
