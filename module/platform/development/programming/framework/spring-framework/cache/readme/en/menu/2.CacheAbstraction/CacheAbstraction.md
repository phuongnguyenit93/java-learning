<a id="back-to-top"></a>

# Cache, CacheManager, and Cache Regions

## Menu
- [The role of the Cache interface](#cache-interface)
- [The role of CacheManager](#cache-manager)
- [Cache names and the cache-region model](#named-cache-region)
- [Cache hits, misses, and lookup results](#cache-hit-miss)
- [Null values and ValueWrapper](#cached-null-value)
- [Native cache access and the adapter boundary](#native-cache-access)
- [Programmatic Cache API versus declarative caching](#programmatic-vs-declarative)

## <a id="cache-interface">The role of the Cache interface</a>

<details>
<summary>Click for details</summary>

`org.springframework.cache.Cache` is Spring's per-cache abstraction. One `Cache` instance represents one named logical cache and exposes the operations that the framework needs for lookup, population, and invalidation.

The core shape is intentionally small: retrieve a value with `get`, store one with `put`, remove an entry with `evict`, clear the region with `clear`, and expose the provider object through `getNativeCache`. The interface also supports loading a missing value with `get(key, Callable)`. Spring Framework 6.1 adds asynchronous `retrieve` methods; those are explored later in the concurrency/async chapter.

The important mental model is that `Cache` is an adapter contract, not the backing data structure itself. An implementation may wrap a `ConcurrentMap`, Caffeine, JCache, or another provider. Code written to the Spring abstraction therefore sees a common set of operations while provider-specific features remain outside this interface.

### References

- [Spring Framework 6.1 Javadoc — `Cache`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/Cache.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="cache-manager">The role of CacheManager</a>

<details>
<summary>Click for details</summary>

`CacheManager` is the registry/factory boundary that resolves a logical cache name to a Spring `Cache` instance. Its main operation is `getCache(String name)`; it can also expose the names it currently knows through `getCacheNames()`.

This separates **which cache the application wants** from **how that cache is constructed or obtained**. Annotation metadata can name a cache such as `products`, while the configured `CacheManager` decides whether that name maps to an in-memory cache, a Caffeine cache, a JCache cache, or another implementation.

An application can have more than one `CacheManager`, but then the caching operation or a `CacheResolver` must make the selection unambiguous. Spring Cache does not merge independent managers into one universal namespace automatically.

### References

- [Spring Framework 6.1 Javadoc — `CacheManager`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/CacheManager.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="named-cache-region">Cache names and the cache-region model</a>

<details>
<summary>Click for details</summary>

Spring refers to caches by logical name. A name such as `products`, `catalogById`, or `exchangeRates` identifies a cache region whose entries share one application purpose and key space.

The name is part of application policy. It does not imply a particular physical representation. One provider may create a local map for the name; another may map it to a remote region or native cache configuration. Keeping the logical name stable lets application code remain independent of those provider details.

Good cache names describe **what is cached**, not the technology currently storing it. `productsById` communicates intent better than `redisCache1`, because the application may later change providers without changing the conceptual cache.

A cache region should also have a coherent key/value contract. Reusing one region for unrelated result shapes makes invalidation and operations harder to reason about even if the provider technically allows it.

</details>

- [Back to top](#back-to-top)

---

## <a id="cache-hit-miss">Cache hits, misses, and lookup results</a>

<details>
<summary>Click for details</summary>

A **cache hit** means the cache has a mapping for the requested key. A **cache miss** means it does not, so the application or caching interceptor must decide whether to load the value.

The basic `Cache.get(key)` method returns a `Cache.ValueWrapper` on a hit and `null` when no mapping is found. This wrapper matters because the cached value itself may be `null`: the framework needs to distinguish “no entry exists” from “an entry exists whose logical value is null.”

Typed `get(key, type)` is a convenience when the caller expects a particular type, while `get(key, Callable)` combines lookup and value loading under the cache implementation's loading contract. The details of synchronization during loading can vary by provider.

At this level, a hit/miss is only a lookup result. Whether a hit is fresh enough, whether a miss should trigger computation, and whether failures should fall back to the source are separate policy decisions.

</details>

- [Back to top](#back-to-top)

---

## <a id="cached-null-value">Null values and ValueWrapper</a>

<details>
<summary>Click for details</summary>

Caching `null` creates an ambiguity: a plain `null` from a lookup could mean either “the key is absent” or “the key is present and the cached value is null.” Spring's `Cache.ValueWrapper` gives the abstraction a way to represent a present mapping whose value may itself be `null`.

Whether null values are actually supported depends on the concrete cache implementation. Some Spring adapters can translate user-level `null` to an internal sentinel because the native provider cannot store null directly. Other providers may reject null caching or expose different constraints.

From a design perspective, caching a negative result can be useful—for example, remembering that an identifier was not found so repeated misses do not hit the database. But it also means “not found” can become stale if the entity is created shortly afterward. Negative caching therefore needs the same freshness and invalidation thinking as positive values.

### References

- [Spring Framework 6.1 Javadoc — `Cache.ValueWrapper`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/Cache.ValueWrapper.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="native-cache-access">Native cache access and the adapter boundary</a>

<details>
<summary>Click for details</summary>

`Cache.getNativeCache()` exposes the provider object wrapped by the Spring adapter. This is an escape hatch for capabilities that the common Spring contract does not model.

Using the native cache can be legitimate when the application needs provider-specific diagnostics or a capability with no Spring abstraction. The trade-off is coupling: once business code calls native APIs, changing providers is no longer just a configuration concern.

A useful rule is to keep native access at an integration boundary rather than spreading it through domain/service code. If a provider feature is central to the application's correctness—such as a specific distributed locking or atomic data-structure semantic—then that behavior should be designed and tested as provider-specific behavior, not assumed to be portable through Spring Cache.

This module teaches the boundary so learners can recognize when they have left the portable Spring caching contract.

</details>

- [Back to top](#back-to-top)

---

## <a id="programmatic-vs-declarative">Programmatic Cache API versus declarative caching</a>

<details>
<summary>Click for details</summary>

Application code can use `Cache` directly or let Spring apply cache policy declaratively around a method invocation.

Programmatic access is explicit:

```java
Cache cache = cacheManager.getCache("products");
if (cache == null) {
    throw new IllegalStateException("Required cache 'products' is not configured");
}
Cache.ValueWrapper hit = cache.get(productId);
```

`CacheManager.getCache(name)` may return `null` when the named cache does not exist and cannot be created, so programmatic code must handle that resolution failure explicitly. Direct access can be appropriate when caching is part of an algorithm whose control flow cannot be expressed cleanly as a method-level rule. The cost is that lookup, population, and invalidation logic becomes mixed into application code.

Declarative caching moves that policy to annotations such as `@Cacheable`. A service method describes the business operation while Spring's cache interceptor performs the surrounding lookup/store work. This reduces boilerplate and makes cross-cutting policy easier to centralize, but it also introduces interception boundaries that must be understood.

Both styles use the same underlying Spring cache abstraction. Declarative caching is not a different storage system; it is infrastructure that orchestrates `Cache` operations around method execution.

</details>

- [Back to top](#back-to-top)
