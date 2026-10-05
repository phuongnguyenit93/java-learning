<a id="back-to-top"></a>

# Bean Scope and Creation Policy

## Menu
- [Spring Singleton Scope](#spring-singleton-scope)
- [Prototype Scope](#prototype-scope)
- [Request, Session, Application, and WebSocket Scopes](#web-aware-scopes)
- [Eager vs Lazy Initialization](#eager-vs-lazy-initialization)
- [Injecting Shorter-lived Dependencies into Singletons](#scoped-dependency-mismatch)
- [Deferred and Dynamic Dependency Lookup](#dynamic-dependency-lookup)
- [Custom Scopes](#custom-scopes)

## <a id="spring-singleton-scope">Spring Singleton Scope</a>

<details>
<summary>Click for details</summary>

Scope answers a different question from dependency type: **how long does a bean instance live, and when does the container create another one from the same definition?** The default Spring scope is `singleton`.

Spring singleton means one shared instance **per bean definition, per container**. It is not the GoF singleton pattern and it does not imply one instance per JVM.

```text
ApplicationContext A
  bean definition "catalogService" → one shared instance

ApplicationContext B
  bean definition "catalogService" → another shared instance
```

For an `ApplicationContext`, non-lazy singleton beans are normally created during context refresh. Once created, the singleton instance is cached by the bean factory and returned to every dependency or lookup that resolves that definition.

Singleton scope makes sharing explicit, but it does not make mutable state thread-safe. A singleton service used concurrently by many threads must still be designed for concurrency. Stateless services or objects with immutable shared state are usually easier to manage than beans carrying request-specific mutable fields.

The practical advantage of singleton scope is stable identity and amortized construction cost for collaborators that naturally belong to the whole container. Use a shorter scope when state truly belongs to a request, session, task, or other narrower context rather than storing that state in a singleton.

</details>

- [Back to top](#back-to-top)

---

## <a id="prototype-scope">Prototype Scope</a>

<details>
<summary>Click for details</summary>

Prototype scope tells Spring to create a new bean instance each time that bean is requested from the container. The definition is shared; the resulting objects are not.

```java
@Bean
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
Command command() {
    return new Command();
}
```

Each `getBean(Command.class)` call can therefore produce a new `Command`. Likewise, each time the container resolves that prototype definition for a new dependency injection event, it creates another instance.

There is an important ownership boundary: Spring creates, configures, and applies initialization processing to a prototype, then hands it to the caller. The container does **not** track the full lifetime afterward and does not automatically invoke destruction callbacks for prototype instances. If a prototype owns resources that need closing, the consumer or another explicit lifecycle mechanism must take responsibility.

Prototype scope also does not automatically mean "new instance every method call." If one prototype is injected directly into a singleton during singleton creation, that singleton receives one prototype instance and keeps that reference. Getting a fresh instance repeatedly requires deferred lookup, a scoped proxy where appropriate, or another design that performs resolution at the time of use.

Use prototype when each retrieval genuinely represents a new stateful object. Do not use it merely to avoid thinking about shared state; ordinary domain objects often do not need to be Spring beans at all.

</details>

- [Back to top](#back-to-top)

---

## <a id="web-aware-scopes">Request, Session, Application, and WebSocket Scopes</a>

<details>
<summary>Click for details</summary>

Web-aware scopes bind bean lifetime to web infrastructure rather than to the whole application context. Spring Framework defines `request`, `session`, `application`, and `websocket` scopes for use with a web-aware `ApplicationContext`.

- **request** creates one instance for an HTTP request.
- **session** creates one instance for an HTTP session.
- **application** associates one instance with the `ServletContext` for the web application.
- **websocket** associates bean state with a WebSocket session in Spring's STOMP-over-WebSocket support.

These scopes only make sense when the corresponding context is active. Trying to resolve a request-scoped bean on a thread with no bound request can fail with an inactive-scope error.

`application` scope is close to singleton in lifetime but not identical in definition. A Spring singleton is one instance per bean definition per Spring container. An application-scoped bean is scoped to the `ServletContext`, so its visibility follows the web application's servlet context rather than an individual Spring container.

Web scopes are useful for contextual state that truly belongs to those lifetimes, but they should not become a place to store arbitrary mutable application state. Request/session lifecycle, concurrency, serialization, and clustering concerns belong to the owning web architecture and should be considered before putting substantial state into scoped beans. Deeper STOMP/WebSocket messaging behavior belongs to the Spring Messaging/web boundary rather than this Core Container section.

</details>

- [Back to top](#back-to-top)

---

## <a id="eager-vs-lazy-initialization">Eager vs Lazy Initialization</a>

<details>
<summary>Click for details</summary>

Creation timing and scope are related but distinct. A bean may be singleton-scoped yet created eagerly or lazily.

By default, `ApplicationContext` pre-instantiates non-lazy singleton beans during refresh. This behavior has an important benefit: constructor, dependency-resolution, and initialization failures are discovered at startup instead of during a later request.

Marking a bean `@Lazy` tells the container to postpone creation until the bean is first needed:

```java
@Bean
@Lazy
ExpensiveIndex expensiveIndex() {
    return new ExpensiveIndex();
}
```

Lazy creation can reduce startup work for rarely used features or break unnecessary eager chains, but it also moves failure detection to runtime. A bean with invalid configuration may allow the context to start and fail only when the feature is first used.

Also note that a lazy bean can still be created during startup if a non-lazy singleton requires it immediately. The dependency forces resolution. By contrast, `@Lazy` on an injection point can inject a lazy-resolution proxy, delaying target resolution until that dependency is actually invoked.

Use laziness to express real deferred demand, not as a general cure for startup failures. If a required core dependency is broken, eager failure is often the safer behavior.

</details>

- [Back to top](#back-to-top)

---

## <a id="scoped-dependency-mismatch">Injecting Shorter-lived Dependencies into Singletons</a>

<details>
<summary>Click for details</summary>

A scope mismatch occurs when a long-lived object directly captures a shorter-lived dependency. The most common example is a singleton that needs a prototype or request-scoped collaborator.

With a prototype, direct constructor injection happens while the singleton is being created:

```text
singleton created once
      ↓
prototype resolved once
      ↓
singleton keeps that same prototype reference
```

The prototype definition is capable of creating many instances, but the singleton's field is still just one Java reference. Nothing automatically re-injects that field on every method call.

The same lifetime mismatch is even more obvious with request/session scope. An eager singleton may be created when no request is active, so resolving the shorter-scoped target directly can fail. What the singleton usually needs is an indirection that resolves the current scoped target later.

Common solutions are a scoped proxy or deferred lookup through `ObjectProvider`/`ObjectFactory`. These preserve the singleton consumer while moving target resolution to the appropriate moment. Another option is to redesign the boundary so request-specific data is passed as method data instead of hidden in a scoped dependency.

Choose the solution based on semantics. If the dependency represents contextual state, a scope-aware indirection can be appropriate. If the consumer merely needs a few values from the current request, passing those values explicitly may be simpler and less coupled to the container.

</details>

- [Back to top](#back-to-top)

---

## <a id="dynamic-dependency-lookup">Deferred and Dynamic Dependency Lookup</a>

<details>
<summary>Click for details</summary>

Deferred lookup solves cases where a bean cannot or should not receive one fixed target during its own creation. Spring provides several mechanisms with different coupling and runtime behavior.

`ObjectProvider<T>` is the most flexible general-purpose option. It can resolve a target on demand, return it only if available or unique, and expose streams of matching candidates:

```java
final class JobRunner {
    private final ObjectProvider<JobContext> contexts;

    JobRunner(ObjectProvider<JobContext> contexts) {
        this.contexts = contexts;
    }

    void run() {
        JobContext context = contexts.getObject();
        // use the instance resolved for this invocation
    }
}
```

`ObjectFactory<T>` is a smaller factory-style contract when only `getObject()` is needed. Both approaches make the lookup visible in the consumer's API and couple that consumer to a Spring container abstraction.

A **scoped proxy** takes a different approach: the consumer receives a stable proxy, and each invocation is delegated to the target instance associated with the current scope. This keeps normal injection syntax but introduces proxy semantics and therefore requires awareness of proxyable types and method calls.

`@Lookup` provides method injection. Spring overrides an annotated method in a container-created subclass so calling the method performs a bean lookup. Because this relies on runtime subclassing, the class/method must be overridable, and lookup-method injection does not work for arbitrary instances returned by `@Bean` factory methods: Spring is not constructing those instances through the subclassing path.

Prefer the mechanism that makes lifetime behavior easiest to see. `ObjectProvider` is explicit and easy to reason about; scoped proxies are convenient when scope semantics should remain transparent to most callers; `@Lookup` is specialized and should be used when subclass-based method injection is truly the best fit.

</details>

- [Back to top](#back-to-top)

---

## <a id="custom-scopes">Custom Scopes</a>

<details>
<summary>Click for details</summary>

Spring's built-in scopes cover common lifetimes, but infrastructure sometimes needs a domain-specific context such as one bean instance per tenant, conversation, job, or workflow. The `Scope` SPI allows that policy to be added to a bean factory.

A custom `Scope` decides how an object is stored and retrieved for the current contextual key. Its contract includes obtaining an object from an `ObjectFactory`, removing an object, registering destruction callbacks, resolving contextual objects, and exposing a conversation identifier where meaningful.

After the scope is registered with a configurable bean factory, bean definitions can refer to its scope name just like built-in scopes. `CustomScopeConfigurer` is a common configuration-time mechanism for installing custom scopes without application beans manually manipulating the factory.

A good custom scope must answer lifecycle questions explicitly:

- What identifies the current context?
- Where are scoped instances stored?
- When does that context end?
- Who runs registered destruction callbacks?
- How is context propagated across threads or asynchronous work, if at all?

Do not introduce a custom scope merely to obtain a map of reusable objects. A scope changes object lifetime semantics throughout dependency resolution, so it belongs in infrastructure with clear activation and cleanup rules. If the lifetime can be represented with ordinary method parameters or an application-owned cache, those simpler mechanisms may be easier to operate.

</details>

- [Back to top](#back-to-top)
