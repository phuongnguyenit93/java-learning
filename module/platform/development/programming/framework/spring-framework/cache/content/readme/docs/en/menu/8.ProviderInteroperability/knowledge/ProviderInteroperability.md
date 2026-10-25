<a id="back-to-top"></a>

# Cache Providers and the Interoperability Boundary

## Menu
- [ConcurrentMap-backed caching](#concurrent-map-provider)
- [Caffeine integration at the Spring Cache layer](#caffeine-provider)
- [JCache integration](#jcache-integration)
- [Spring annotations versus JCache annotations](#spring-vs-jcache-annotations)
- [Custom Cache and CacheManager adapters](#custom-cache-adapter)
- [TTL, TTI, eviction, and size policies belong to the provider](#provider-policy-boundary)
- [Serialization, concurrency, and consistency are not guaranteed by the abstraction](#serialization-and-consistency)
- [Local versus distributed caching at the provider boundary](#local-vs-distributed-cache)

## <a id="concurrent-map-provider">ConcurrentMap-backed caching</a>

<details>
<summary>Click for details</summary>

`ConcurrentMapCache` is Spring's simple in-process `Cache` implementation backed by JDK concurrent collections. `ConcurrentMapCacheManager` can create those caches lazily by name or operate with a fixed set of cache names.

Its strength is simplicity: no external service is required, lookup is local to the JVM, and it is useful for tests or straightforward application caching. Its limitation is the same simplicity. The manager does not provide sophisticated cache configuration such as provider-managed expiration, size-based eviction, persistence, or cross-node coordination.

`ConcurrentMapCache` can adapt `null` values because the default `ConcurrentHashMap` cannot store Java `null` directly. In Spring 6.1 it also supports the asynchronous `Cache.retrieve` contract in a best-effort style. Those conveniences do not turn it into a distributed or policy-rich cache.

Use it when the desired cache semantics are process-local and deliberately simple, or when a lightweight implementation is useful during testing.

### References

- Spring Framework Reference — [JDK `ConcurrentMap`-based Cache](https://docs.spring.io/spring-framework/reference/integration/cache/store-configuration.html#cache-store-configuration-jdk-concurrentmap)
- Spring Framework Javadoc — [`ConcurrentMapCacheManager`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/concurrent/ConcurrentMapCacheManager.html)
- Spring Framework Javadoc — [`ConcurrentMapCache`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/concurrent/ConcurrentMapCache.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="caffeine-provider">Caffeine integration at the Spring Cache layer</a>

<details>
<summary>Click for details</summary>

Spring integrates Caffeine through `CaffeineCache` and `CaffeineCacheManager`. The Spring types adapt Caffeine to the common `Cache`/`CacheManager` contracts while leaving Caffeine-specific tuning on the provider side.

A `CaffeineCacheManager` can create caches dynamically and can be configured with a Caffeine builder or specification. Policies such as maximum size, expiration, refresh behavior, statistics, and loader behavior are Caffeine concerns rather than portable Spring Cache annotation semantics.

For Spring Framework 6.1 asynchronous caching, `CaffeineCacheManager` can expose Caffeine asynchronous caches by enabling `setAsyncCacheMode(true)`. That capability is important when methods return `CompletableFuture`, `Mono`, or `Flux`.

The application should therefore separate two decisions: use Spring Cache annotations and key/invalidation policy for application-level caching intent, and use Caffeine configuration for local-cache mechanics.

### References

- Spring Framework Reference — [Caffeine Cache](https://docs.spring.io/spring-framework/reference/integration/cache/store-configuration.html#cache-store-configuration-caffeine)
- Spring Framework Javadoc — [`CaffeineCacheManager`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/caffeine/CaffeineCacheManager.html)
- Spring Framework Javadoc — [`CaffeineCache`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/caffeine/CaffeineCache.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="jcache-integration">JCache integration</a>

<details>
<summary>Click for details</summary>

JCache, standardized as JSR-107, defines a Java caching API that can be implemented by different cache vendors. Spring can adapt a JCache `javax.cache.CacheManager` through `JCacheCacheManager`, exposing the resulting caches through Spring's own `CacheManager` abstraction.

```text
application cache policy
        |
Spring Cache abstraction
        |
JCacheCacheManager / JCacheCache
        |
JSR-107 provider
```

This makes JCache an interoperability option. Spring-managed code can keep using Spring's cache abstraction while the backing implementation follows JSR-107. Provider configuration still decides storage behavior such as expiry, serialization, topology, and vendor-specific capabilities.

JCache integration should not be interpreted as making every JCache provider behavior identical. The standard creates a common API surface; concrete providers still differ in operational characteristics.

### References

- Spring Framework Reference — [JSR-107 Cache](https://docs.spring.io/spring-framework/reference/integration/cache/store-configuration.html#cache-store-configuration-jsr107)
- Spring Framework Javadoc — [`JCacheCacheManager`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/jcache/JCacheCacheManager.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-vs-jcache-annotations">Spring annotations versus JCache annotations</a>

<details>
<summary>Click for details</summary>

Spring supports both its native cache annotations and the JCache annotation model. They solve similar problems but are not interchangeable vocabulary with perfectly identical semantics.

For example:

- Spring `@Cacheable` broadly corresponds to JCache `@CacheResult`, but JCache adds its own exception-caching and invocation controls.
- Spring `@CachePut` caches the method result, while JCache `@CachePut` identifies the value to cache through a parameter marked with `@CacheValue`.
- Spring `@CacheEvict` corresponds to JCache `@CacheRemove`, and cache-wide eviction corresponds to `@CacheRemoveAll`.
- The two models have different resolver/key-generation types and configuration annotations.

Spring's JCache support routes standard annotations through Spring infrastructure, so adopting JCache annotations does not require the application to abandon Spring-managed caching. The annotation model should be selected consistently based on portability and feature needs rather than mixed casually on the same operation.

### References

- Spring Framework Reference — [JCache (JSR-107) Annotations](https://docs.spring.io/spring-framework/reference/integration/cache/jsr-107.html)
- Spring Framework Javadoc — [JCache declarative configuration package](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/jcache/config/package-summary.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="custom-cache-adapter">Custom Cache and CacheManager adapters</a>

<details>
<summary>Click for details</summary>

Spring's provider boundary is intentionally small. A custom provider can integrate by implementing `Cache` and exposing those instances through a `CacheManager`. The interceptor continues to work with the common Spring contracts while the adapter translates operations to the native store.

A custom `Cache` must define the important semantics honestly: how misses and nullable values are represented, whether `get(key, Callable)` provides atomic loading, whether `putIfAbsent` is atomic, whether immediate operations really become visible immediately, and whether the Spring 6.1 `retrieve` methods are truly non-blocking.

A custom `CacheManager` decides how cache names map to cache instances and whether unknown names can be created dynamically. If an adapter hides provider limitations or reports stronger guarantees than the native backend actually provides, higher-level annotations become misleading.

The adapter should therefore translate capabilities, not invent them.

### References

- Spring Framework Javadoc — [`Cache`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/Cache.html)
- Spring Framework Javadoc — [`CacheManager`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/CacheManager.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="provider-policy-boundary">TTL, TTI, eviction, and size policies belong to the provider</a>

<details>
<summary>Click for details</summary>

Spring Cache defines when application code wants to look up, populate, or evict a value. It does not define a portable annotation for every storage-retention policy.

Policies such as these belong to the provider configuration:

- time to live (TTL) or time-based expiry;
- time to idle (TTI), when supported;
- maximum entry count or weight;
- size/weight-based eviction;
- refresh policies;
- persistence and disk tiers;
- replication or distributed topology.

This separation is useful. Application code can keep stable cache names, keys, and invalidation rules while provider settings change for a deployment environment. It also means switching providers requires a deliberate review of policy equivalence; matching the same `@Cacheable` annotations does not prove that expiration or eviction behavior is equivalent.

For Caffeine, configure these concerns through the Caffeine builder/specification. For JCache providers or distributed stores, use the provider's configuration model.

### References

- Spring Framework Reference — [Configuring the Cache Storage](https://docs.spring.io/spring-framework/reference/integration/cache/store-configuration.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="serialization-and-consistency">Serialization, concurrency, and consistency are not guaranteed by the abstraction</a>

<details>
<summary>Click for details</summary>

The Spring `Cache` abstraction normalizes a useful set of operations, not the full consistency model of every backing store. It does not promise that two providers have the same serialization format, locking granularity, atomicity, replication delay, failure mode, or visibility guarantees across nodes.

Examples of provider-sensitive questions include:

- Are keys and values stored by reference in the local JVM or serialized?
- Can a value be read by another application instance immediately after a put?
- Is loading for one key atomic across threads, processes, or only inside one cache instance?
- Are invalidations propagated synchronously, asynchronously, or eventually?
- What happens during a network partition or partial provider outage?

Spring's contracts document requirements for particular operations, such as the atomic intent of `putIfAbsent` and the immediate-visibility expectation of `evictIfPresent`/`invalidate`. A provider may still have implementation-specific details around those contracts, so production design must read the native provider documentation.

### References

- Spring Framework Javadoc — [`Cache`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/Cache.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="local-vs-distributed-cache">Local versus distributed caching at the provider boundary</a>

<details>
<summary>Click for details</summary>

A local cache keeps data inside one application process. It can provide very low latency and avoids a network hop, but each process owns its own entries. With several application instances, the same key may have several independent cached copies and invalidation must account for that topology.

A distributed cache moves shared cache state behind a networked provider. Multiple application instances can consult a common data set, but each access now inherits network latency, serialization cost, remote failure modes, and the consistency model of that provider.

Neither option is universally stronger. A local cache is often appropriate for process-local reference data or computations that tolerate per-instance staleness. A distributed cache is useful when sharing cache state across instances matters enough to justify the extra operational cost.

Spring Cache lets both options implement the same application-facing `Cache` contract, but the architecture must still reason about topology explicitly. Redis-specific serialization, TTL, data structures, clustering, and operational behavior belong to the Spring Data Redis/provider curriculum rather than this abstraction chapter.

### References

- Spring Framework Reference — [Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)

</details>

- [Back to top](#back-to-top)
