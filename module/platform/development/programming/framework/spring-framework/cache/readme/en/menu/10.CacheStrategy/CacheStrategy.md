<a id="back-to-top"></a>

# End-to-end Cache Strategy Design

## Menu
- [Selecting data and computations to cache](#select-cache-candidate)
- [Designing cache names and ownership](#cache-naming-strategy)
- [Designing the key strategy](#key-strategy)
- [Designing population and invalidation strategy](#population-invalidation-strategy)
- [Freshness and consistency trade-offs](#freshness-consistency-tradeoff)
- [Handling cache stampede at the right abstraction layer](#stampede-strategy)
- [Evaluating cache and transaction interaction](#transaction-interaction)
- [Selecting a provider from required capabilities](#provider-selection)
- [Production cache-strategy checklist](#cache-strategy-checklist)

## <a id="select-cache-candidate">Selecting data and computations to cache</a>

<details>
<summary>Click for details</summary>

Not every expensive method is a good cache candidate. Start by asking what cost the cache removes and what correctness risk it introduces.

A strong candidate usually has several of these properties:

- the same logical result is requested repeatedly;
- computing or loading it is expensive relative to a cache lookup;
- the result changes less frequently than it is read;
- callers can tolerate a defined amount of staleness;
- a stable cache key can identify the result;
- invalidation can be tied to known business events or an acceptable expiry policy.

Poor candidates include highly volatile data, results that depend on hidden request/session context, one-off computations, or values whose stale form would violate correctness or security expectations.

Treat cache as a derived optimization layer. The source of truth should remain clear, and the application must still have a defined behavior for cache miss, cache outage, or stale data.

</details>

- [Back to top](#back-to-top)

---

## <a id="cache-naming-strategy">Designing cache names and ownership</a>

<details>
<summary>Click for details</summary>

Cache names should describe a stable ownership boundary, not an implementation accident. A name such as `product-by-id` or `pricing-rules` tells maintainers what data is stored and who is responsible for invalidating it.

Useful questions include:

- Which service or bounded component owns writes to this cache?
- Which operations are allowed to populate it?
- Which business events must invalidate or refresh it?
- Can several methods safely share the same cache region and key space?
- Is the name expected to stay stable if the provider changes?

Avoid reusing one cache name for unrelated value types merely because they currently use the same provider. Doing so couples key design, invalidation, retention, and observability in ways that make later changes difficult.

Spring resolves cache names through the configured `CacheManager`/`CacheResolver`; provider-specific region creation or configuration should map cleanly to the application-level ownership model.

</details>

- [Back to top](#back-to-top)

---

## <a id="key-strategy">Designing the key strategy</a>

<details>
<summary>Click for details</summary>

A cache key must uniquely identify the business result being reused. The key strategy should be explicit about every input that can change that result.

For example, caching a product quote only by `productId` is incorrect if the result also depends on currency, customer tier, locale, or effective date. The cache would collapse distinct business results into one entry.

Good keys are:

- deterministic for equivalent requests;
- stable across repeated invocations;
- compact enough for the provider;
- based on business identity rather than mutable object identity;
- compatible with serialization if the cache is remote;
- versioned or namespaced when incompatible value shapes can coexist during deployment.

Spring's default key generation is convenient for simple cases, but explicit SpEL keys or a custom `KeyGenerator` are often clearer when business identity is more complex.

### References

- Spring Framework Reference — [Default Key Generation](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-cacheable-default-key)
- Spring Framework Reference — [Custom Key Generation](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-cacheable-key)

</details>

- [Back to top](#back-to-top)

---

## <a id="population-invalidation-strategy">Designing population and invalidation strategy</a>

<details>
<summary>Click for details</summary>

Population and invalidation should be designed together. A cache is easy to populate and difficult to keep correct if nobody owns the events that make entries stale.

A common cache-aside-style miss/load/populate flow is:

```text
read request
  -> cache hit -> return
  -> cache miss -> load source of truth -> cache result -> return
```

Mutating operations then need a matching policy:

- evict affected keys after successful updates;
- update cache entries explicitly with `@CachePut` when the new value is already known;
- clear a broader region only when targeted invalidation is not reliable;
- combine event-driven invalidation with provider expiry as a safety bound when appropriate.

Prefer invalidation rules that are derived from the same business identity used by the key strategy. If writes cannot reliably determine which entries became stale, the cache model is probably too broad or the key space is missing an important dimension.

</details>

- [Back to top](#back-to-top)

---

## <a id="freshness-consistency-tradeoff">Freshness and consistency trade-offs</a>

<details>
<summary>Click for details</summary>

Caching creates a freshness window. The design must state how stale a value may become and what happens when updates race with reads.

The acceptable trade-off depends on the domain:

- product descriptions may tolerate minutes of staleness;
- authorization data may require much tighter guarantees;
- inventory or financial values may need explicit versioning or direct source-of-truth reads for critical decisions.

Provider TTL can bound how long an entry survives, but expiry alone does not define application consistency. Two nodes with independent local caches can still expire at different times. A distributed cache can still have replication or failure semantics that affect visibility.

Choose the consistency expectation first, then select invalidation, expiry, topology, and transaction coordination that are strong enough for that expectation.

</details>

- [Back to top](#back-to-top)

---

## <a id="stampede-strategy">Handling cache stampede at the right abstraction layer</a>

<details>
<summary>Click for details</summary>

Cache stampede should be solved at the narrowest layer that can actually provide the required coordination.

For one JVM and one Spring cache operation, `@Cacheable(sync=true)` may be enough when the provider supports synchronized loading. For multiple application instances, a local synchronization mechanism cannot stop every node from computing the same missing value.

Larger systems may need provider features, distributed coordination, request coalescing, pre-warming, probabilistic early refresh, or a queue-based refresh workflow. Those mechanisms have different failure and latency characteristics.

Do not add distributed locking by default. First estimate the cost of duplicate work, expected concurrency per key, and failure impact. Sometimes duplicate computation is cheaper and safer than introducing a global coordination dependency.

### References

- Spring Framework Reference — [Synchronized Caching](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-cacheable-synchronized)

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-interaction">Evaluating cache and transaction interaction</a>

<details>
<summary>Click for details</summary>

If cached values mirror transactional data, decide when cache changes are allowed to become visible relative to commit.

Questions to answer include:

- Can a cache put or eviction happen before the database transaction commits?
- What happens if the transaction later rolls back?
- Does the code read the cache again inside the same transaction?
- Are any immediate operations such as `putIfAbsent`, `evictIfPresent`, or `invalidate` used?
- Is the provider shared across services that do not participate in the same transaction?

Spring's transaction-aware cache decoration can defer ordinary `put`, `evict`, and `clear` operations until after a successful commit. That reduces one common inconsistency window but does not create distributed transaction semantics.

When the source of truth and cache live in different systems, design for partial failure explicitly. A successful database commit followed by a failed cache invalidation is still possible and needs monitoring, retry, expiry, or reconciliation depending on the business risk.

</details>

- [Back to top](#back-to-top)

---

## <a id="provider-selection">Selecting a provider from required capabilities</a>

<details>
<summary>Click for details</summary>

Choose a provider from required behavior rather than popularity.

Evaluate capabilities such as:

- local versus distributed topology;
- latency and throughput targets;
- TTL/TTI and eviction policy;
- maximum size or memory control;
- atomic loading and `sync=true` behavior;
- asynchronous retrieval support for Spring 6.1;
- serialization and schema evolution;
- replication and failure behavior;
- observability and operational tooling;
- transaction-aware integration requirements.

`ConcurrentMapCache` is intentionally simple. Caffeine is a strong local in-memory option with richer policies. JCache provides a standardized integration surface across compatible providers. Remote stores such as Redis introduce networked/shared-state semantics and should be evaluated with their provider-specific capabilities.

The Spring abstraction helps keep application policy stable, but provider selection remains an architecture decision because performance and consistency guarantees come from the provider.

</details>

- [Back to top](#back-to-top)

---

## <a id="cache-strategy-checklist">Production cache-strategy checklist</a>

<details>
<summary>Click for details</summary>

Before treating a cache design as production-ready, verify the complete policy rather than only checking that `@Cacheable` returns hits.

Use this checklist:

- **Candidate** — Is the value expensive/reused enough to justify caching?
- **Source of truth** — Is authoritative data clearly identified?
- **Name/ownership** — Does each cache region have an owner and stable purpose?
- **Key** — Does the key include every input that changes the result?
- **Population** — Is the miss path bounded and observable?
- **Invalidation** — Which writes make which keys stale?
- **Freshness** — What maximum staleness is acceptable?
- **Concurrency** — What happens on simultaneous misses for one key?
- **Async/reactive** — Does the provider support the required non-blocking retrieval semantics?
- **Transactions** — Are cache mutations aligned with commit where necessary?
- **Provider policy** — Are TTL, size, eviction, serialization, and topology configured intentionally?
- **Failure** — What happens when cache read/write/eviction fails?
- **Observability** — Can operators see hit rate, miss rate, load latency, errors, evictions, and capacity pressure?
- **Recovery** — Can the application rebuild or invalidate the cache safely?

A cache strategy is complete only when those answers fit together. Spring Cache provides the application-facing abstraction; AOP determines interception, transaction support coordinates commit timing, and the provider defines storage/concurrency/topology behavior.

### References

- Spring Framework Reference — [Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)

</details>

- [Back to top](#back-to-top)
