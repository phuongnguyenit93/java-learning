<a id="back-to-top"></a>

# Proxy Interception and Execution Boundaries

## Menu
- [Cache interceptor infrastructure behind @EnableCaching](#cache-interceptor-infrastructure)
- [Proxy mode and external method invocation](#proxy-mode)
- [Self-invocation and cache-advice bypass](#self-invocation)
- [The public-method boundary in proxy mode](#public-method-boundary)
- [Why should caching not be relied on during initialization?](#initialization-boundary)
- [Cache advice ordering with other interceptors](#cache-advice-ordering)
- [AspectJ mode and the boundary with proxy-based caching](#aspectj-mode)

## <a id="cache-interceptor-infrastructure">Cache interceptor infrastructure behind @EnableCaching</a>

<details>
<summary>Click for details</summary>

Cache annotations are metadata. They do not execute cache operations by themselves. `@EnableCaching` imports Spring's cache-management configuration, which creates the advisor/interceptor infrastructure that looks for cache operation metadata and applies it to eligible Spring beans.

At runtime, the cache interceptor resolves the declared cache operation, determines the key and target cache or caches, and decides whether to invoke the business method. For a cache hit on `@Cacheable`, the interceptor can return the cached value without invoking the method. For a miss, it invokes the method and stores the produced value according to the operation's policy.

This is why merely adding `@Cacheable`, `@CachePut`, or `@CacheEvict` to an arbitrary object does not make caching happen. The object must participate in the Spring-managed interception path created by the caching configuration.

### References

- Spring Framework Reference — [Enabling Caching Annotations](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-enable)
- Spring Framework Javadoc — [`@EnableCaching`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/EnableCaching.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="proxy-mode">Proxy mode and external method invocation</a>

<details>
<summary>Click for details</summary>

The default caching mode is proxy-based interception. Spring exposes a proxy in front of the target bean, and cache advice runs when a caller invokes an eligible method through that proxy.

The useful mental model is:

```text
caller
  -> Spring proxy
       -> cache advice
            -> cache hit: return cached value
            -> cache miss: call target method, then cache result
```

The important boundary is the proxy entry point. A method can carry correct cache metadata and still bypass caching when the invocation reaches the target object without going through the Spring proxy. This is an AOP execution boundary, not a property of the Java method alone.

### References

- Spring Framework Reference — [Declarative Annotation-based Caching](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="self-invocation">Self-invocation and cache-advice bypass</a>

<details>
<summary>Click for details</summary>

Self-invocation occurs when one method on the target object calls another method on the same object, for example through `this.loadBook(id)`. In proxy mode, that call stays inside the target instance. It does not leave the object and re-enter through the proxy, so the cache interceptor never sees the second call.

```java
@Service
class BookService {

    @Cacheable("books")
    public Book find(String id) {
        return load(id);
    }

    @Cacheable("books")
    public Book load(String id) {
        return repository.load(id);
    }
}
```

The `find()` invocation may be intercepted when called through the Spring bean reference, but its internal call to `load()` does not trigger a second cache interception. Treat self-invocation as part of the ordinary proxy semantics used by Spring AOP.

If a design requires interception for internal calls, restructure the collaboration so the call crosses a Spring bean boundary or deliberately choose AspectJ mode. The deeper proxy mechanics belong to the Spring AOP module.

### References

- Spring Framework Reference — [Proxy mode and self-invocation](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-enable)

</details>

- [Back to top](#back-to-top)

---

## <a id="public-method-boundary">The public-method boundary in proxy mode</a>

<details>
<summary>Click for details</summary>

With proxy-based caching, Spring recommends placing cache annotations on public methods. Non-public annotated methods do not produce the configured caching behavior in the standard proxy mode even though Spring does not necessarily report an error for the annotation itself.

That recommendation gives the cache boundary a clear application meaning: caching is normally attached to service operations that callers reach through the Spring-managed bean contract. It also avoids designs that depend on implementation-only methods being intercepted accidentally.

This rule is about proxy-mode cache annotations. AspectJ weaving has a different interception model because it modifies class bytecode rather than relying on calls entering through a proxy.

### References

- Spring Framework Reference — [Method visibility and cache annotations](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-enable)

</details>

- [Back to top](#back-to-top)

---

## <a id="initialization-boundary">Why should caching not be relied on during initialization?</a>

<details>
<summary>Click for details</summary>

Proxy-based caching should not be treated as available during bean initialization. The Spring reference documentation calls out initialization code such as `@PostConstruct`: at that point, relying on a fully initialized proxy path can produce behavior that differs from ordinary runtime calls.

A common mistake is to call a cache-annotated method from the same bean's initialization method and expect the cache to be populated. That has two problems at once: initialization may occur before the expected proxy path is usable, and a direct internal call is self-invocation anyway.

Initialize required state explicitly. Let normal application traffic, an application lifecycle component that holds the proxied bean reference, or a deliberate warm-up mechanism invoke cacheable operations after the application context is ready.

### References

- Spring Framework Reference — [Proxy initialization boundary for caching](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-enable)

</details>

- [Back to top](#back-to-top)

---

## <a id="cache-advice-ordering">Cache advice ordering with other interceptors</a>

<details>
<summary>Click for details</summary>

A method can be advised by several Spring concerns, such as caching, transactions, security, or custom AOP interceptors. Their relative order affects what each concern observes. For example, whether a cache mutation happens inside or outside a transaction boundary can change when failures become visible and whether a cache update is coordinated with commit.

`@EnableCaching` exposes an `order` setting for the cache advisor. Its default is `Ordered.LOWEST_PRECEDENCE`. That default does not establish a universal semantic order against every other advisor; when multiple advices have the same effective precedence, application configuration and AOP ordering rules determine the resulting chain.

Choose an explicit order only when the application has a concrete semantic requirement and test the resulting behavior. Ordering is an AOP composition concern, so detailed advisor precedence rules belong to the Spring AOP module.

### References

- Spring Framework Javadoc — [`@EnableCaching.order()`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/EnableCaching.html#order())
- Spring Framework Reference — [Advice Ordering](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html#aop-ataspectj-advice-ordering)

</details>

- [Back to top](#back-to-top)

---

## <a id="aspectj-mode">AspectJ mode and the boundary with proxy-based caching</a>

<details>
<summary>Click for details</summary>

`AdviceMode.ASPECTJ` changes the interception mechanism. Instead of depending on calls entering through a Spring proxy, Spring's caching aspect is woven into the affected class, so internal calls can be intercepted as well.

AspectJ mode therefore removes the specific proxy-entry limitation behind self-invocation, but it introduces a different operational model: `spring-aspects` must be available and compile-time or load-time weaving must be configured. It is a deliberate architecture choice rather than a switch to use casually for one inconvenient call path.

Spring Cache owns the cache operation semantics; the details of weaving, join points, proxy implementation, and general advice composition belong to Spring AOP/AspectJ. Keep that boundary clear when diagnosing a cache problem: first ask whether cache metadata is correct, then ask whether the invocation actually crosses the configured interception mechanism.

### References

- Spring Framework Reference — [Caching annotation mode: proxy versus AspectJ](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-enable)
- Spring Framework Javadoc — [`@EnableCaching.mode()`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/EnableCaching.html#mode())

</details>

- [Back to top](#back-to-top)
