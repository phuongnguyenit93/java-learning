<a id="back-to-top"></a>

# Cache Purpose and the Spring Cache Boundary

## Menu
- [What problem does caching solve?](#cache-purpose)
- [How is a cache different from the source of truth?](#cache-vs-source-of-truth)
- [How is a cache different from a buffer and persistent storage?](#cache-vs-buffer-and-storage)
- [When is a result a good caching candidate?](#cache-candidate-criteria)
- [What does Spring Cache abstract?](#spring-cache-abstraction-boundary)
- [Which concerns remain the cache provider's responsibility?](#provider-owned-concerns)

## <a id="cache-purpose">What problem does caching solve?</a>

<details>
<summary>Click for details</summary>

Caching avoids repeating work when the same logical result is requested again. Instead of always performing an expensive calculation, database query, remote call, or file read, an application can reuse a previously computed value associated with a key.

The basic flow is:

```text
request
  ↓
look up key in cache
  ├─ hit  → return cached value
  └─ miss → compute/load value → cache it → return it
```

This can reduce latency and load on downstream systems, but it introduces a second copy of data that can become stale. Caching therefore is not only a performance technique; it is also a consistency decision. A useful cache design answers both questions: **what work do we want to avoid?** and **how stale may the reused result become?**

Spring Cache focuses on the application-level caching contract around method invocations. It lets application code express that a result may be reused without requiring every service method to implement the lookup/store pattern manually.

</details>

- [Back to top](#back-to-top)

---

## <a id="cache-vs-source-of-truth">How is a cache different from the source of truth?</a>

<details>
<summary>Click for details</summary>

A source of truth is the authoritative state that the application trusts when correctness matters. A cache is normally a derived, replaceable copy optimized for faster access.

That distinction changes how failures and eviction should be treated. If a cache entry disappears, the application should usually be able to reconstruct it from the authoritative source. If the authoritative data disappears, the cache should not be treated as a reliable recovery mechanism unless the system was explicitly designed that way.

For example, if product details live in a database, a cached `ProductView` may be discarded and rebuilt. The database remains authoritative even when the cache contains a newer-looking timestamp or a value that has not yet expired.

This also explains why write flows need a clear policy. Updating the cache while failing to update the authoritative store can make the application appear successful while durable state is still wrong. Later chapters cover invalidation and update strategies, but the mental model starts here: **cache state is usually subordinate to authoritative state**.

</details>

- [Back to top](#back-to-top)

---

## <a id="cache-vs-buffer-and-storage">How is a cache different from a buffer and persistent storage?</a>

<details>
<summary>Click for details</summary>

These mechanisms can all hold data temporarily, but they solve different problems.

| Mechanism | Primary purpose | Typical property |
| --- | --- | --- |
| Cache | Reuse data or computation | Entries may be discarded and reconstructed |
| Buffer | Smooth or decouple data flow | Data is usually waiting to be consumed or transferred |
| Persistent storage | Preserve authoritative state | Data is expected to survive process restarts and normal cache eviction |

A queue buffer, for example, may exist because a producer is faster than a consumer. A cache exists because repeating the same load or computation is wasteful. A database exists because the state itself must be retained.

One technology can sometimes provide more than one role, but the design intent still matters. A Redis instance can be used as a cache, a durable data structure store, or messaging infrastructure. Spring Cache does not redefine those native roles; it models only the caching view exposed through the Spring cache abstraction.

</details>

- [Back to top](#back-to-top)

---

## <a id="cache-candidate-criteria">When is a result a good caching candidate?</a>

<details>
<summary>Click for details</summary>

A result is a strong caching candidate when it is expensive enough to obtain, requested repeatedly, and stable enough that temporary reuse is acceptable.

Useful questions include:

- **Cost:** Is the original computation, database access, or remote call meaningfully expensive?
- **Reuse:** Will the same logical key be requested often enough to produce hits?
- **Key completeness:** Does the key include every input dimension that changes the result?
- **Freshness:** Can callers tolerate the result until it is refreshed or invalidated?
- **Size:** Is the cached value small enough that retaining many entries is practical?
- **Failure behavior:** If the cache is unavailable, can the application still load the value from its source?

Caching is usually a poor fit when values are almost never reused, each request is unique, correctness requires the latest committed state on every read, or the result depends on hidden context that is not represented in the key. Personalized or security-sensitive results also require particular care because an incomplete key can return one user's data to another caller.

The goal is not to maximize cache coverage. The goal is to cache where reuse creates enough value to justify the additional invalidation and consistency complexity.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-cache-abstraction-boundary">What does Spring Cache abstract?</a>

<details>
<summary>Click for details</summary>

Spring Cache provides a common application-facing abstraction for storing and retrieving cached values. Its central interfaces are `Cache` and `CacheManager`, while annotation-driven caching adds method-level policies such as `@Cacheable`, `@CachePut`, and `@CacheEvict`.

The abstraction answers questions such as:

- Which logical cache should an operation use?
- Which key identifies the cached result?
- Should the method run, or can a cached value be returned?
- Should a result be stored, updated, or invalidated?

It deliberately does **not** define a universal storage engine. A Spring `Cache` is an adapter over an underlying cache implementation. The same application policy can therefore be expressed against different providers while still acknowledging that provider capabilities differ.

This is why the Spring abstraction is valuable at the service boundary: business code can state caching intent while provider-specific configuration remains outside the method. Later chapters explain the exact `Cache`/`CacheManager` model and the annotation infrastructure that applies this policy.

The rest of the module follows that responsibility chain:

```text
Cache / CacheManager
    ↓ provide the common abstraction
cache annotations
    ↓ declare method-level policy
key + cache resolution
    ↓ identify what and where to cache
invalidation + consistency
    ↓ keep reused results trustworthy
proxy + concurrency mechanics
    ↓ explain how policy executes at runtime
provider + transaction boundaries
    ↓ show where Spring's abstraction stops
```

By the end, the goal is not merely to recognize cache annotations. It is to be able to design a cache policy whose key, invalidation, concurrency, failure, provider, and transaction assumptions fit together.

### References

- [Spring Framework 6.1 Javadoc — `org.springframework.cache`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/package-summary.html)
- [Spring Framework Reference — Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="provider-owned-concerns">Which concerns remain the cache provider's responsibility?</a>

<details>
<summary>Click for details</summary>

Spring Cache standardizes a small caching contract; it does not make different cache products behave identically. Important runtime policies remain provider concerns, including:

- time-to-live (TTL), time-to-idle (TTI), and native expiry behavior;
- maximum size, eviction algorithms, and memory management;
- local versus distributed topology;
- serialization format and cross-process compatibility;
- replication, partitioning, durability, and distributed consistency;
- provider-specific locking, loading, and asynchronous retrieval capabilities;
- metrics, operational tooling, and cluster failure behavior.

Spring may expose an adapter for a provider, but the adapter does not erase these differences. A configuration that is safe for an in-memory `ConcurrentMap` cache can have very different latency and consistency characteristics when the backing cache is remote or distributed.

This boundary is important throughout the module. Spring Cache owns the method-level abstraction and policy. Provider documentation owns native storage behavior. Redis-specific data structures, TTL configuration, serialization, and topology belong to the Spring Data Redis/provider curriculum rather than this module.

</details>

- [Back to top](#back-to-top)
