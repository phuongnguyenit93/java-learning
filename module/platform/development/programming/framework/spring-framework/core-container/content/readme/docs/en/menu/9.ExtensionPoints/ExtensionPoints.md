<a id="back-to-top"></a>

# Spring Container Extension Points

## Menu
- [When Should the Container Be Extended?](#extension-point-purpose)
- [BeanFactoryPostProcessor](#bean-factory-post-processor)
- [BeanPostProcessor](#bean-post-processor)
- [Post-processor Ordering](#post-processor-ordering)
- [Early Bean Instantiation and Its Consequences](#early-bean-instantiation)
- [FactoryBean and the Product Bean](#factory-bean)
- [Annotation Processing as Container Infrastructure](#annotation-infrastructure)
- [Keeping Application Code Container-agnostic](#container-agnostic-design)

## <a id="extension-point-purpose">When Should the Container Be Extended?</a>

<details>
<summary>Click for details</summary>

Most application code should not extend the container. Extension points exist primarily for framework and infrastructure code that must participate in container bootstrap.

A useful first split is:

```text
configuration metadata needs to change
→ work before ordinary bean creation
→ BeanFactoryPostProcessor family

bean instances need to be inspected, initialized, wrapped, or replaced
→ work around bean initialization
→ BeanPostProcessor family
```

This distinction prevents a common design mistake: using container hooks to implement business behavior. Business services should normally remain plain beans with explicit dependencies. Reach for a container extension point only when the concern is genuinely about registration, wiring, lifecycle, annotation processing, or infrastructure.

</details>

- [Back to top](#back-to-top)

---

## <a id="bean-factory-post-processor">BeanFactoryPostProcessor</a>

<details>
<summary>Click for details</summary>

A `BeanFactoryPostProcessor` runs after bean definitions have been loaded but before ordinary beans are instantiated. Its input is therefore the container model, not the final application objects.

Typical uses include adjusting definition metadata, registering infrastructure, or resolving configuration before bean creation begins. `BeanDefinitionRegistryPostProcessor` is a specialized extension that can add more bean definitions before the regular factory-post-processing phase.

The important boundary is **do not treat it like a service layer callback**. Calling `getBean(...)` while post-processing definitions can instantiate application beans too early and bypass assumptions that later post-processors rely on.

Mental model:

```text
load bean definitions
→ factory post-processors inspect/change metadata
→ regular bean creation starts
```

### References

- Spring Framework Reference — Container Extension Points

</details>

- [Back to top](#back-to-top)

---

## <a id="bean-post-processor">BeanPostProcessor</a>

<details>
<summary>Click for details</summary>

A `BeanPostProcessor` participates in the lifecycle of bean instances. The container can invoke processors before and after initialization callbacks, allowing infrastructure to inspect a bean or return a wrapped/replacement instance.

This is the mechanism behind many features that appear annotation-driven. Injection processors, lifecycle annotation processors, and proxy-producing infrastructure all rely on the fact that bean creation has a controlled interception point.

The key mental model is:

```text
instantiate bean
→ populate dependencies
→ before-initialization processors
→ init callbacks
→ after-initialization processors
→ exposed bean reference
```

A processor applies broadly to eligible beans in its containing factory. That power is why custom processors should be small, deterministic, and infrastructure-focused.

</details>

- [Back to top](#back-to-top)

---

## <a id="post-processor-ordering">Post-processor Ordering</a>

<details>
<summary>Click for details</summary>

When several post-processors participate in bootstrap, ordering affects observable behavior. For processors autodetected as beans, Spring gives precedence to `PriorityOrdered`, then `Ordered`, then processors without an ordering contract.

Do not confuse that with programmatic registration. When post-processors are added directly to a configurable factory/context, registration order is significant and normal ordering metadata may not be consulted in the same way.

This matters when one processor expects another processor to have already registered metadata, injected dependencies, or wrapped a bean. If an extension requires a strict relative order, make that requirement explicit and keep the dependency between processors narrow.

Ordering is infrastructure coupling. If application correctness depends on a long, fragile chain of custom post-processors, the design is usually too implicit.

</details>

- [Back to top](#back-to-top)

---

## <a id="early-bean-instantiation">Early Bean Instantiation and Its Consequences</a>

<details>
<summary>Click for details</summary>

Container infrastructure is created earlier than normal application beans. If a post-processor obtains an application bean while the processor chain is still being assembled, that bean can be instantiated before every processor is available.

The result is subtle: the bean may be fully constructed yet miss later infrastructure such as auto-proxying or annotation-based processing. Spring can emit diagnostics that a bean is "not eligible for getting processed by all BeanPostProcessors" in these situations.

Practical rule:

```text
post-processor dependencies
→ prefer other infrastructure objects or lazy/provider-style access
→ avoid eagerly pulling ordinary application beans into bootstrap
```

Early creation is not merely a startup-performance concern; it can change the semantics of the final bean reference.

</details>

- [Back to top](#back-to-top)

---

## <a id="factory-bean">FactoryBean and the Product Bean</a>

<details>
<summary>Click for details</summary>

`FactoryBean<T>` lets one Spring-managed factory object expose another object as the value associated with a bean name. Looking up the normal bean name returns the product; using the `&` dereference prefix asks for the `FactoryBean` itself.

This is useful when object creation is complex enough to deserve a dedicated factory contract or when infrastructure needs to expose an object whose construction does not map cleanly to a normal bean definition.

Two lifecycle details matter:

- Spring manages the lifecycle of the `FactoryBean` instance itself, not the lifecycle of the product returned by `getObject()`. A destroy method on that product is therefore not invoked automatically; when cleanup is required, the factory must own or delegate it explicitly.
- Both `getObjectType()` and `getObject()` can be invoked early during bootstrap, even before normal post-processor setup is complete. A `FactoryBean` should not assume annotation-driven injection or the full application graph is already available when those methods run.

If a factory genuinely needs access to other beans during that early phase, use the explicit factory contract deliberately, for example through `BeanFactoryAware`, instead of depending on ordinary annotation-driven injection.

Use `FactoryBean` for infrastructure-level creation semantics, not just to hide ordinary constructor calls.

</details>

- [Back to top](#back-to-top)

---

## <a id="annotation-infrastructure">Annotation Processing as Container Infrastructure</a>

<details>
<summary>Click for details</summary>

Spring annotations work because container infrastructure interprets them; annotations are metadata, not executable magic by themselves.

Examples of the pipeline include:

```text
@Configuration / @Bean / @Import
→ configuration-class processing
→ bean definitions are registered

@Autowired / @Value
→ injection-oriented BeanPostProcessor
→ dependencies or values are applied to instances

@PostConstruct / @PreDestroy
→ lifecycle annotation processor
→ callbacks are invoked at lifecycle boundaries
```

This mental model helps debugging. If an object is created with `new` outside the container, or if the relevant infrastructure processor is absent, merely placing a Spring annotation on the class does not make the behavior happen.

The annotation is the declaration; the container extension point is the mechanism that enforces it.

</details>

- [Back to top](#back-to-top)

---

## <a id="container-agnostic-design">Keeping Application Code Container-agnostic</a>

<details>
<summary>Click for details</summary>

The strongest use of the container often produces application code that barely knows the container exists. Constructor injection lets a service express dependencies as Java types while Spring remains responsible for composition at the application boundary.

Prefer:

```java
final class CheckoutService {
    private final PricePolicy pricePolicy;

    CheckoutService(PricePolicy pricePolicy) {
        this.pricePolicy = pricePolicy;
    }
}
```

over static context holders or repeated `applicationContext.getBean(...)` calls inside business logic.

`*Aware` contracts and direct `BeanFactory` access are valid when the bean genuinely implements infrastructure behavior. They are a poor default for domain/application services because they turn an explicit dependency graph into hidden service location.

A useful design test is: **could this class be unit-tested by constructing it with ordinary collaborators?** If yes, the class is probably keeping the container at the right boundary.

</details>

- [Back to top](#back-to-top)
