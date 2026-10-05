<a id="back-to-top"></a>

# Bean Lifecycle and Startup Ordering

## Menu
- [Bean Lifecycle Overview](#bean-lifecycle-overview)
- [Instantiation and Dependency Population](#instantiation-and-population)
- [Aware Callbacks](#aware-callbacks)
- [Initialization Callbacks](#initialization-callbacks)
- [Destruction Callbacks](#destruction-callbacks)
- [Lifecycle and SmartLifecycle](#lifecycle-smartlifecycle)
- [Startup Ordering and depends-on](#startup-order-depends-on)
- [Lifecycle Failure Boundaries: Circular Dependencies, Premature Creation, and Cleanup](#lifecycle-failure-boundaries)

## <a id="bean-lifecycle-overview">Bean Lifecycle Overview</a>

<details>
<summary>Click for details</summary>

A Spring bean is not usable merely because its constructor has returned. The container still has work to do: resolve and inject dependencies, deliver container callbacks, run initialization logic, let infrastructure post-process the instance, and later participate in shutdown when the context closes.

A useful lifecycle model is:

```text
instantiate
    ↓
populate dependencies
    ↓
BeanFactory-level Aware callbacks + BeanPostProcessor.beforeInitialization(...)
(context-specific Aware callbacks and @PostConstruct may run in this stage)
    ↓
InitializingBean + custom init method
    ↓
BeanPostProcessor.afterInitialization(...)
    ↓
bean ready for normal use
    ↓
destruction callbacks when the owning context shuts down
```

That sequence explains several behaviors that otherwise look surprising. Constructor injection happens before an instance exists, setter or field injection happens after instantiation, and a proxy may only appear after post-processing has completed. The diagram also distinguishes callback mechanisms: BeanFactory-level Aware callbacks are invoked directly by the factory, while context-specific callbacks such as `ApplicationContextAware` are commonly delivered by `ApplicationContextAwareProcessor`; `@PostConstruct` is likewise commonly invoked by an initialization-aware post-processor before `InitializingBean` and a custom init method. The reference held by application code can therefore be a container-produced proxy rather than the raw object that executed its initialization callbacks.

Lifecycle is also scoped. Singleton beans are normally created once per bean definition and the container can later destroy them. Prototype beans are created and configured by the container, but the container does not track their full lifetime after handing them to the caller, so it does not automatically run prototype destruction callbacks.

Use lifecycle hooks for container-lifecycle work such as validating configuration, preparing internal state, or releasing owned resources. Long-running background activity belongs to `Lifecycle`/`SmartLifecycle`, where start and stop semantics are explicit.

### References

- Spring Framework Reference — Customizing the Nature of a Bean

</details>

- [Back to top](#back-to-top)

---

## <a id="instantiation-and-population">Instantiation and Dependency Population</a>

<details>
<summary>Click for details</summary>

The first important distinction is between **creating an object** and **making it a fully configured bean**.

With constructor injection, Spring must resolve constructor arguments before it can instantiate the object:

```java
final class BillingService {
    private final TaxPolicy taxPolicy;

    BillingService(TaxPolicy taxPolicy) {
        this.taxPolicy = taxPolicy;
    }
}
```

If `TaxPolicy` cannot be resolved, `BillingService` cannot even be constructed.

Setter and field injection happen after an instance exists. Conceptually:

```text
new BillingService(...)
        ↓
populate writable dependencies
        ↓
continue initialization
```

Annotation-driven injection such as `@Autowired` is implemented through container post-processing. For the bean author, the important contract is timing: required injected collaborators are established before normal initialization callbacks such as `@PostConstruct` run.

Do not put externally visible work in a constructor just because the object has been allocated. At constructor time, setter/field dependencies may still be missing, container callbacks have not run, and proxy-based infrastructure is not yet in its final form. Constructors should establish intrinsic object invariants; container-dependent setup belongs later.

This phase also explains why circular constructor dependencies fail naturally: each side needs the other fully resolved before either can be instantiated. Circular relationships involving mutable injection may sometimes be resolvable by the container, but that should not be treated as a design technique; it creates fragile initialization order and can expose partially initialized objects.

</details>

- [Back to top](#back-to-top)

---

## <a id="aware-callbacks">Aware Callbacks</a>

<details>
<summary>Click for details</summary>

`Aware` interfaces let a bean ask the container to inject container infrastructure into it. Examples include `BeanNameAware`, `BeanFactoryAware`, `ApplicationContextAware`, `EnvironmentAware`, and `ResourceLoaderAware`.

For example:

```java
final class ContextInspector implements ApplicationContextAware {
    private ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext context) {
        this.context = context;
    }
}
```

The callback is delivered by the container during bean initialization, before the bean's regular initialization callbacks. Some low-level callbacks such as `BeanNameAware` and `BeanFactoryAware` are invoked directly by the bean factory, while context-specific callbacks such as `ApplicationContextAware` are delivered through `ApplicationContextAwareProcessor`, which is itself a `BeanPostProcessor`. The useful guarantee for application code is that the relevant Aware contract has been supplied before regular initialization callbacks run.

The trade-off is direct framework coupling. A service that only needs `Clock`, `PricingRepository`, or another application dependency should receive that dependency directly through constructor injection. Injecting the entire `ApplicationContext` turns ordinary application code into a Service Locator and hides its real dependency contract.

`Aware` is appropriate when the infrastructure itself is the dependency: a reusable framework component may legitimately need the bean name, resource loader, event publisher, or environment abstraction. Keep the requested interface as narrow as possible. `ResourceLoaderAware` communicates much less coupling than `ApplicationContextAware` if resource loading is the only requirement.

The lifecycle point to remember is simple: Aware callbacks are part of container setup. They are not application startup events, and they are not a replacement for constructor injection of normal collaborators.

</details>

- [Back to top](#back-to-top)

---

## <a id="initialization-callbacks">Initialization Callbacks</a>

<details>
<summary>Click for details</summary>

Initialization callbacks run after dependency population and before the bean is considered fully initialized for ordinary use. Spring supports three common mechanisms:

- `@PostConstruct`
- `InitializingBean.afterPropertiesSet()`
- a custom init method configured through bean metadata such as `@Bean(initMethod = "startCache")`

If a bean uses all three with different method names, Spring invokes them in this order:

```text
@PostConstruct
    ↓
InitializingBean.afterPropertiesSet()
    ↓
custom init method
```

For application classes, `@PostConstruct` or a plain custom method usually keeps the class less coupled to Spring than implementing `InitializingBean`.

```java
final class RouteTable {
    private final List<Route> routes;
    private Map<String, Route> byCode;

    RouteTable(List<Route> routes) {
        this.routes = routes;
    }

    @PostConstruct
    void indexRoutes() {
        byCode = routes.stream()
                .collect(Collectors.toUnmodifiableMap(Route::code, r -> r));
    }
}
```

Initialization is a good place to validate configuration and derive internal state from already injected dependencies. Avoid treating it as a general application-start hook for expensive work that reaches back into many other beans. The target is still in its creation lifecycle, and proxy-based interceptors are not a reliable boundary for calls made from the target's own init method.

When work truly must happen only after the context has completed regular singleton creation, use a mechanism designed for that later point, such as a context refresh event or `SmartInitializingSingleton`, depending on the component's role.

### References

- Spring Framework Reference — Lifecycle Callbacks

</details>

- [Back to top](#back-to-top)

---

## <a id="destruction-callbacks">Destruction Callbacks</a>

<details>
<summary>Click for details</summary>

Destruction callbacks are the mirror image of initialization: they give a container-managed bean a final opportunity to release resources that the bean owns when its context is shutting down.

The common mechanisms are:

- `@PreDestroy`
- `DisposableBean.destroy()`
- a custom destroy method, for example `@Bean(destroyMethod = "close")`

When different methods are configured for all three mechanisms, the order is:

```text
@PreDestroy
    ↓
DisposableBean.destroy()
    ↓
custom destroy method
```

A typical bean should make cleanup idempotent and bounded:

```java
final class LocalWorkerPool {
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @PreDestroy
    void close() {
        executor.shutdown();
    }
}
```

Destroy callbacks depend on the container actually closing. In a standalone application that creates an `ApplicationContext` programmatically, call `close()` or register a JVM shutdown hook when appropriate. Dropping the last Java reference to the context is not equivalent to shutting it down.

Prototype scope has an important boundary: Spring creates and configures the prototype instance but does not manage its full lifetime after returning it. Consequently, configured destruction callbacks are not automatically invoked for prototype beans. Whoever requests the prototype must own any later cleanup that its resources require.

Also separate bean destruction from `Lifecycle.stop()`. A lifecycle component may need a coordinated stop phase before its destruction callback releases final resources. They solve related but distinct problems.

</details>

- [Back to top](#back-to-top)

---

## <a id="lifecycle-smartlifecycle">Lifecycle and SmartLifecycle</a>

<details>
<summary>Click for details</summary>

`Lifecycle` and `SmartLifecycle` model components that have a running state, such as a listener, consumer, scheduler-like adapter, or background connector. They are different from one-time bean initialization and destruction.

`Lifecycle` defines the basic contract:

```java
interface Lifecycle {
    void start();
    void stop();
    boolean isRunning();
}
```

An ordinary `Lifecycle` bean participates when the `ApplicationContext` receives explicit start and stop signals. Implementing `Lifecycle` alone does not mean that the component automatically starts during context refresh.

`SmartLifecycle` adds the pieces needed for coordinated application startup and shutdown:

- `isAutoStartup()` controls whether the component starts automatically when the containing `ApplicationContext` is refreshed.
- `getPhase()` participates in ordering.
- `stop(Runnable callback)` lets asynchronous shutdown tell the lifecycle processor when stopping is complete.

Phase ordering is directional:

```text
startup:   lower phase → higher phase
shutdown:  higher phase → lower phase
```

Regular `Lifecycle` beans effectively use phase `0`. A `SmartLifecycle` implementation that does not override `getPhase()` uses `Integer.MAX_VALUE` by default, so it normally starts later than phase-`0` components and stops earlier. A negative phase starts before phase-`0` components and stops after them; a positive phase does the opposite.

Use phases to express coarse lifecycle sequencing between independent infrastructure components. If component B has a real bean dependency on A, model that dependency directly; Spring also respects dependency relationships so the dependent starts after its dependency and stops before it.

A `SmartLifecycle` implementation must invoke the callback passed to `stop(Runnable)` after its asynchronous stop work completes. Failing to call it forces the lifecycle processor to wait until its shutdown timeout.

</details>

- [Back to top](#back-to-top)

---

## <a id="startup-order-depends-on">Startup Ordering and depends-on</a>

<details>
<summary>Click for details</summary>

Spring already derives much startup order from the object graph. If bean `ReportService` requires bean `DatabaseClient`, the dependency must be created before the dependent bean can be completed. You do not need an extra ordering annotation for ordinary dependency injection.

`depends-on` / `@DependsOn` exists for an ordering relationship that is real but not represented by an injected Java reference. A classic example is a component that requires another bean's side effect to have completed first:

```java
@Bean
CacheIndex cacheIndex() {
    return new CacheIndex();
}

@Bean
@DependsOn("cacheIndex")
QueryGateway queryGateway() {
    return new QueryGateway();
}
```

For singleton beans, `depends-on` also influences shutdown in the opposite direction: the dependent is destroyed before the bean it depends on. The same relationship participates in `Lifecycle` start/stop ordering.

Use `@DependsOn` sparingly. If one component actually calls another, a direct dependency communicates more information, is easier to test, and lets the container infer the relationship naturally. `@DependsOn` is most useful for indirect infrastructure dependencies such as registration, static initialization, or externally visible setup.

Do not confuse `@Order` with general bean creation order. `@Order` is interpreted by specific consumers that sort ordered objects, such as some extension-point chains or event listeners. It does not mean "construct this bean first".

Also remember that lazy beans change when creation occurs. `@DependsOn` does not make the declaring bean eager by itself. However, once that bean is created, the bean factory guarantees that its declared `depends-on` beans are initialized first, even if one of those dependencies is otherwise lazy. If the declaring bean itself remains lazy and nothing requests it, that relationship does not by itself cause the graph to be created during refresh.

</details>

- [Back to top](#back-to-top)

---

## <a id="lifecycle-failure-boundaries">Lifecycle Failure Boundaries: Circular Dependencies, Premature Creation, and Cleanup</a>

<details>
<summary>Click for details</summary>

Lifecycle bugs often come from assuming that "the bean exists" means "the bean is complete". Three failure boundaries deserve special attention.

**Circular dependencies.** Constructor cycles are unsatisfiable because neither instance can be constructed without the other. Mutable injection can make some cycles technically resolvable, but that does not make them healthy. Cycles obscure ownership and can force the container to expose an early reference before every initialization or post-processing step has finished.

**Premature creation.** Infrastructure code can accidentally request a bean while the container is still registering or creating processors. That bean may be instantiated before all normal post-processors are available, so it can miss transformations such as proxying. This is one reason extension-point code must avoid casual bean lookup during container bootstrap.

**Cleanup failures.** Acquiring a resource in initialization without a clear shutdown path leaks threads, sockets, files, or external leases. Keep resource ownership explicit: the bean that creates an owned resource should normally release it, and shutdown code should tolerate partial initialization because startup may fail after some resources have already been acquired.

A practical diagnostic sequence is:

```text
Where was the bean first requested?
        ↓
Which dependencies had to exist at that moment?
        ↓
Had all relevant post-processors been registered?
        ↓
Which initialization callback failed?
        ↓
If startup aborted, which already-created resources still require cleanup?
```

Avoid fixing lifecycle failures by adding arbitrary `@DependsOn` declarations. That can hide the symptom while keeping the underlying cycle, hidden lookup, or ownership problem intact. Prefer to repair the dependency graph or move the work to the lifecycle phase that actually owns it.

</details>

- [Back to top](#back-to-top)
