<a id="back-to-top"></a>

# Declarative Caching with Spring Annotations

## Menu
- [Enabling cache annotation infrastructure with @EnableCaching](#enable-caching)
- [Cache lookup and population with @Cacheable](#cacheable)
- [Updating cache state with @CachePut](#cache-put)
- [Invalidation with @CacheEvict](#cache-evict)
- [Combining cache operations with @Caching](#caching-composition)
- [Sharing configuration with @CacheConfig](#cache-config)
- [Global cache infrastructure defaults with CachingConfigurer](#caching-configurer)
- [Cache-operation configuration precedence](#cache-operation-configuration-precedence)

## <a id="enable-caching">Enabling cache annotation infrastructure with @EnableCaching</a>

<details>
<summary>Click for details</summary>

`@EnableCaching` turns on Spring's annotation-driven cache management for the application context. It imports the infrastructure that discovers cache annotations and applies cache operations around eligible method invocations.

The annotation itself does not create a cache store. The application still needs cache infrastructure such as a `CacheManager` or a `CacheResolver` capable of resolving the logical cache names used by operations.

In the default proxy mode, Spring applies caching through an interceptor on a proxy around the target bean. That means annotation metadata only affects invocations that pass through the configured interception path. The exact proxy/self-invocation boundary is covered in the dedicated interception chapter; here the key point is that enabling annotation support and providing a cache provider are separate responsibilities.

### References

- [Spring Framework 6.1 Javadoc — `EnableCaching`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/EnableCaching.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="cacheable">Cache lookup and population with @Cacheable</a>

<details>
<summary>Click for details</summary>

`@Cacheable` models the classic “reuse this method result” operation. Before invoking the target method, Spring computes the cache key and checks the resolved cache or caches. If a suitable cached value is found, that value is returned and the target method is skipped. On a miss, Spring invokes the method and stores the resulting value according to the operation's policy.

For example:

```java
@Cacheable(cacheNames = "products", key = "#id")
public Product findProduct(long id) {
    return repository.findById(id).orElseThrow();
}
```

The annotation expresses policy around the method; the business method still describes how to obtain the value when the cache cannot satisfy the request.

`@Cacheable` supports operation-level choices such as `cacheNames`, `key`, `keyGenerator`, `condition`, `unless`, `cacheManager`, `cacheResolver`, and `sync`. Those attributes are related but solve different problems. Key and resolution rules are covered in the next chapter, while `sync=true` restrictions belong to the concurrency chapter.

### References

- [Spring Framework 6.1 Javadoc — `Cacheable`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/Cacheable.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="cache-put">Updating cache state with @CachePut</a>

<details>
<summary>Click for details</summary>

`@CachePut` updates cache state without skipping the target method. The method is always invoked, and its result is then associated with the computed cache key when the cache operation applies.

This makes `@CachePut` useful when the method itself performs meaningful work—such as updating authoritative data—and the returned value should refresh the corresponding cache entry:

```java
@CachePut(cacheNames = "products", key = "#result.id")
public Product update(Product command) {
    return repository.save(command);
}
```

The important contrast with `@Cacheable` is execution behavior. `@Cacheable` may bypass the method on a hit; `@CachePut` is designed to run the method and then put a value.

`@CachePut` also supports conditional behavior, but its timing differs from `@Cacheable`: because a put operation always invokes the method, both `condition` and `unless` are evaluated after invocation and can refer to `#result`. The cache write is still secondary to the method's primary business effect.

### References

- [Spring Framework 6.1 Javadoc — `CachePut`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/CachePut.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="cache-evict">Invalidation with @CacheEvict</a>

<details>
<summary>Click for details</summary>

`@CacheEvict` expresses invalidation: remove cached state because it may no longer be valid. By default, Spring evicts the entry associated with the operation's key after the target method completes successfully.

The annotation can also clear an entire cache with `allEntries=true`, and `beforeInvocation=true` moves eviction before the method call. These options are not interchangeable: they change both the scope and the failure semantics of invalidation.

Typical use cases include deleting an entity, changing data that invalidates a previously cached projection, or performing a bulk update that makes many entries stale.

The deeper correctness question is not “can this method evict?” but “which cached representations become stale when this method succeeds or fails?” Chapter 5 develops that timing and consistency model.

### References

- [Spring Framework 6.1 Javadoc — `CacheEvict`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/CacheEvict.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="caching-composition">Combining cache operations with @Caching</a>

<details>
<summary>Click for details</summary>

`@Caching` is a grouping annotation for declaring multiple cache operations on the same method. It can contain several `@Cacheable`, `@CachePut`, or `@CacheEvict` declarations when one operation cannot express the required policy.

For example, a method might invalidate two differently keyed cache regions after one update. Grouping keeps those operations attached to the method while allowing each operation to keep its own cache names, keys, and conditions.

Composition should be intentional. Putting contradictory operations on the same invocation can make execution hard to reason about. In particular, combining `@Cacheable` and `@CachePut` for the same logical result is usually suspicious because one operation is allowed to skip the method while the other requires method execution. Spring can validate some incompatible combinations, but semantic clarity remains an application responsibility.

Use `@Caching` when the business event truly affects multiple cache entries or regions, not merely to collect unrelated cache annotations in one place.

</details>

- [Back to top](#back-to-top)

---

## <a id="cache-config">Sharing configuration with @CacheConfig</a>

<details>
<summary>Click for details</summary>

`@CacheConfig` provides class-level defaults for cache operations declared on that class. It reduces repetition when several methods use the same cache names or cache infrastructure.

It can define defaults for:

- `cacheNames`;
- `keyGenerator`;
- `cacheManager`;
- `cacheResolver`.

For example, a service whose methods all target the `products` cache can declare that cache name once at class level and let individual operations override it only when necessary.

`@CacheConfig` does not enable caching and does not itself define a cache operation. It supplies defaults to annotations such as `@Cacheable`, `@CachePut`, and `@CacheEvict`. Operation-level settings remain more specific and therefore take precedence when present.

### References

- [Spring Framework 6.1 Javadoc — `CacheConfig`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/CacheConfig.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="caching-configurer">Global cache infrastructure defaults with CachingConfigurer</a>

<details>
<summary>Click for details</summary>

`CachingConfigurer` is the global extension point for annotation-driven cache infrastructure in an `@EnableCaching` configuration class. It answers application-wide questions that are broader than one cache operation or one service class.

An implementation can supply defaults for:

- the `CacheManager` used by annotation-driven caching;
- a `CacheResolver` for more flexible cache selection;
- the default `KeyGenerator`;
- the `CacheErrorHandler`.

If both `cacheManager()` and `cacheResolver()` are supplied, the resolver wins and the cache manager is ignored for regular annotation-driven resolution. When overriding `cacheManager()` or `cacheResolver()`, the implementation must explicitly declare that method as `@Bean` so the returned object participates in the application-context lifecycle. If no custom key generator is supplied, Spring uses `SimpleKeyGenerator`. If no custom error handler is supplied, the default `SimpleCacheErrorHandler` propagates cache exceptions to the caller.

`CachingConfigurer` is initialized early in the application context, so its dependencies should be designed accordingly. In Spring 6.1, implement `CachingConfigurer` directly; `CachingConfigurerSupport` is deprecated since Spring 6.0.

### References

- [Spring Framework 6.1 Javadoc — `CachingConfigurer`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/CachingConfigurer.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="cache-operation-configuration-precedence">Cache-operation configuration precedence</a>

<details>
<summary>Click for details</summary>

Cache configuration exists at several levels. Reading it from most specific to most general keeps the model predictable:

```text
operation annotation
    ↓ overrides when explicitly set
@CacheConfig class defaults
    ↓ falls back to
global annotation infrastructure defaults
    ↓ backed by
Spring framework defaults
```

An operation-level `key`, `keyGenerator`, `cacheManager`, or `cacheResolver` can specialize one method. `@CacheConfig` supplies class-level defaults when the operation does not set them. `CachingConfigurer` supplies application-wide defaults for key generation, cache resolution, and error handling.

Not every setting exists at every level. For example, `CachingConfigurer` does not define global cache names, while `@CacheConfig` can. Likewise, `key` and `keyGenerator` are alternative strategies for a cache operation rather than values to combine.

This hierarchy is useful because it lets common policy stay centralized while allowing exceptional methods to opt into a more specific rule. Prefer the highest level that accurately describes the policy; excessive per-method overrides make caching behavior harder to audit.

</details>

- [Back to top](#back-to-top)
