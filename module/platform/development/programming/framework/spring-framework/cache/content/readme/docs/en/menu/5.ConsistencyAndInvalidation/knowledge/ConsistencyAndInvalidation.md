<a id="back-to-top"></a>

# Updates, Invalidation, and Consistency

## Menu
- [Population, put, and eviction are different operations](#population-put-eviction)
- [beforeInvocation and invalidation timing](#evict-before-invocation)
- [Whole-cache eviction with allEntries](#whole-cache-eviction)
- [How do exceptions affect cache mutation?](#exception-and-cache-mutation)
- [Stale data and consistency gaps](#stale-data)
- [Cache-aside and the source-of-truth gap](#cache-aside-consistency)
- [@Cacheable and @CachePut on the same invocation path](#cacheable-cacheput-conflict)

## <a id="population-put-eviction">Population, put, and eviction are different operations</a>

<details>
<summary>Click for details</summary>

Caching correctness depends on distinguishing three different state transitions.

- **Population** stores a value because a lookup missed and the application loaded or computed the result. `@Cacheable` commonly drives this flow.
- **Put** writes a value because the method executed and its result should refresh cache state. `@CachePut` models this explicitly.
- **Eviction** removes cached state because the application can no longer trust it. `@CacheEvict` expresses that invalidation policy.

These operations answer different questions. Population asks “what should be reused after a miss?” Put asks “what fresh value should replace the cached one?” Eviction asks “which value should no longer be reused?”

Keeping those roles separate makes update flows easier to reason about. A write to the source of truth does not automatically imply that every related cache should receive the same object. Sometimes the correct action is to update one cache entry; sometimes it is safer to evict and allow the next read to rebuild a projection from authoritative data.

</details>

- [Back to top](#back-to-top)

---

## <a id="evict-before-invocation">beforeInvocation and invalidation timing</a>

<details>
<summary>Click for details</summary>

`@CacheEvict` normally performs eviction **after** the advised method completes successfully. This default preserves the existing cache entry when the business operation throws an exception.

Setting `beforeInvocation=true` changes the ordering:

```text
default
method succeeds → evict
method throws   → keep existing cache entry

beforeInvocation=true
evict → invoke method
       └─ even if the method later throws, the eviction already happened
```

The choice should follow business semantics. After-success eviction is appropriate when cached state only becomes invalid after a successful change. Before-invocation eviction can be useful when the cached value must never survive an attempted operation, but it accepts the possibility that the method fails and the cache is still empty afterward.

### References

- [Spring Framework 6.1 Javadoc — `CacheEvict.beforeInvocation`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/CacheEvict.html#beforeInvocation())

</details>

- [Back to top](#back-to-top)

---

## <a id="whole-cache-eviction">Whole-cache eviction with allEntries</a>

<details>
<summary>Click for details</summary>

Sometimes one business operation invalidates many keys and computing every affected key is impractical. `@CacheEvict(allEntries=true)` removes all entries from the resolved cache or caches instead of targeting one key.

Typical examples include replacing a complete reference dataset, applying a bulk import, or changing configuration that affects every cached projection in a region.

Whole-cache eviction is intentionally coarse. It trades precision for a simpler correctness guarantee and may create a temporary burst of cache misses while entries are rebuilt.

When `allEntries=true`, specifying a `key` is not valid because the operation no longer targets an individual entry. The important design question is whether the cache region is scoped narrowly enough that clearing it does not invalidate unrelated data.

### References

- [Spring Framework 6.1 Javadoc — `CacheEvict.allEntries`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/CacheEvict.html#allEntries())

</details>

- [Back to top](#back-to-top)

---

## <a id="exception-and-cache-mutation">How do exceptions affect cache mutation?</a>

<details>
<summary>Click for details</summary>

Method outcome matters because most cache mutations happen only after a value or successful business effect exists.

With the usual annotation semantics:

- a `@Cacheable` miss that ends in an exception has no successful result to populate;
- a `@CachePut` method that throws does not produce the normal result used for the put;
- `@CacheEvict` with the default `beforeInvocation=false` evicts only after successful invocation;
- `@CacheEvict(beforeInvocation=true)` evicts first, so the cache change remains even when the method later fails.

This describes the relationship between **business-method outcome** and cache mutation. It is separate from a cache infrastructure failure such as a provider throwing during `get` or `put`; cache-error policy belongs to the later failure-handling chapter.

Do not infer transaction rollback guarantees from these annotation rules. Coordinating cache mutation with transaction commit requires transaction-aware cache infrastructure and is covered at that boundary later in the module.

</details>

- [Back to top](#back-to-top)

---

## <a id="stale-data">Stale data and consistency gaps</a>

<details>
<summary>Click for details</summary>

A cached value becomes **stale** when the authoritative data has changed but the cache still contains the older result. This is a normal risk of caching because the cache and source of truth are separate state holders.

Staleness can appear through several paths:

- a write changes source data but forgets to update or evict a related cache entry;
- two concurrent operations update source and cache in different orders;
- a TTL permits an old value to remain valid longer than the business can tolerate;
- one application instance invalidates local state while another instance still holds its own local copy;
- one cached projection depends on data changed by a different write path.

TTL is therefore a freshness bound, not an understanding of business dependencies. A provider can expire an entry after five minutes, but it does not know that a database row changed one second after the entry was cached unless the application or integration layer communicates that change.

Good cache design identifies the acceptable stale window and the events that should invalidate or refresh each cached representation.

</details>

- [Back to top](#back-to-top)

---

## <a id="cache-aside-consistency">Cache-aside and the source-of-truth gap</a>

<details>
<summary>Click for details</summary>

Spring's common `@Cacheable` flow resembles cache-aside behavior: look in the cache first, load from the authoritative source on a miss, then store the loaded result.

```text
read
  ↓
cache miss
  ↓
load source of truth
  ↓
put result in cache
```

The consistency gap appears because source and cache are not one atomic state machine. Consider a read that loads old source data while another request updates the database and evicts the cache. If the first request writes its old result into the cache after the eviction, stale data can be reintroduced.

Spring Cache gives the application tools to express lookup, put, and eviction, but it does not make database and cache changes globally atomic. Applications reduce the gap with domain-appropriate strategies: carefully ordered invalidation, short freshness windows, versioned keys, transaction-aware mutation where applicable, or provider-specific coordination.

The right strategy depends on how much staleness the business can tolerate. Stronger consistency usually costs more coordination and can reduce the performance benefit that motivated caching.

</details>

- [Back to top](#back-to-top)

---

## <a id="cacheable-cacheput-conflict">@Cacheable and @CachePut on the same invocation path</a>

<details>
<summary>Click for details</summary>

`@Cacheable` and `@CachePut` have opposing execution goals. `@Cacheable` may return a cached value **without invoking the method**. `@CachePut` is intended to **invoke the method and update the cache with its result**.

For that reason, putting both operations on the same method for the same invocation path is generally a design smell and is strongly discouraged by Spring's caching guidance. The combined behavior can be surprising because one operation wants to skip execution while the other depends on execution.

There are narrow cases where mutually exclusive conditions make the operations apply to different calls, but those conditions must be determinable before invocation; they should not depend on `#result` to prove that the two operations exclude each other.

Prefer separate methods or a clearer cache policy when the intent is “read with reuse” versus “perform work and refresh cache.” The distinction keeps the execution model visible to maintainers and avoids relying on subtle interceptor interactions.

### References

- [Spring Framework Reference — Declarative Annotation-based Caching](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html)

</details>

- [Back to top](#back-to-top)
