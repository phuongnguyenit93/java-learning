<a id="back-to-top"></a>

# Failure Policy and Transaction-aware Cache Updates

## Menu
- [CacheErrorHandler and default error propagation](#cache-error-handler)
- [Cache failures versus business-method failures](#cache-vs-business-failure)
- [TransactionAwareCacheDecorator and TransactionAwareCacheManagerProxy](#transaction-aware-cache)
- [Deferring put, evict, and clear until after commit](#after-commit-cache-mutation)
- [putIfAbsent, evictIfPresent, and invalidate require immediate effect](#immediate-cache-operations)
- [Behavior when no transaction is active](#no-active-transaction)
- [Boundary with Spring Transaction Management](#transaction-module-boundary)

## <a id="cache-error-handler">CacheErrorHandler and default error propagation</a>

<details>
<summary>Click for details</summary>

`CacheErrorHandler` is the policy hook used by Spring's cache interceptor when a cache operation itself throws a runtime exception. It has separate callbacks for get, put, evict, and clear failures, so an application can decide whether a cache infrastructure problem should fail the invocation or be handled differently.

The default policy is `SimpleCacheErrorHandler`. Despite its name, it does not recover from the error: it rethrows the cache exception to the caller. That fail-fast behavior makes cache failures visible instead of silently pretending that the cache is healthy.

Applications can provide another `CacheErrorHandler` through `CachingConfigurer`. A custom handler might log and suppress selected read failures so the business method can recompute a value, while still propagating write or eviction failures when stale data would be dangerous. Such a policy must be deliberate because suppressing infrastructure failures changes consistency and observability.

### References

- Spring Framework Javadoc — [`CacheErrorHandler`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/interceptor/CacheErrorHandler.html)
- Spring Framework Javadoc — [`SimpleCacheErrorHandler`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/interceptor/SimpleCacheErrorHandler.html)
- Spring Framework Reference — [Enabling Caching Annotations](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-enable)

</details>

- [Back to top](#back-to-top)

---

## <a id="cache-vs-business-failure">Cache failures versus business-method failures</a>

<details>
<summary>Click for details</summary>

A cache failure and a business-method failure occur at different layers.

```text
cache lookup fails
  -> CacheErrorHandler policy applies

cache miss
  -> business method runs
       -> business method fails
            -> business exception propagates according to application logic
```

`CacheErrorHandler` handles exceptions raised while Spring invokes cache operations. It is not a general exception handler for the intercepted business method. If the business method throws, Spring does not reinterpret that exception as a cache-provider failure.

This distinction matters for resilience policy. A cache read outage may be treated as a recoverable optimization failure if the source of truth can still be queried. A business exception can represent a validation error, remote-system failure, or domain rule that must remain visible. Combining both into one “cache fallback” path can hide the real cause.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-aware-cache">TransactionAwareCacheDecorator and TransactionAwareCacheManagerProxy</a>

<details>
<summary>Click for details</summary>

`TransactionAwareCacheDecorator` wraps one Spring `Cache` and coordinates selected cache mutations with a Spring-managed transaction. `TransactionAwareCacheManagerProxy` applies that idea at the manager level by exposing transaction-aware cache objects from a target `CacheManager`.

The purpose is to avoid publishing a cache mutation that describes database state before the corresponding transaction has successfully committed. The decorator registers synchronization with Spring's transaction infrastructure and delays supported mutations until the after-commit phase.

```text
transaction starts
  -> database changes
  -> cache put/evict/clear requested
       -> cache mutation queued
  -> commit succeeds
       -> queued cache mutation executes
```

This is coordination, not a transactional cache store. Cache reads are not given database-style isolation, and the decorator does not make the native cache participate in two-phase commit.

### References

- Spring Framework Javadoc — [`TransactionAwareCacheDecorator`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/transaction/TransactionAwareCacheDecorator.html)
- Spring Framework Javadoc — [`TransactionAwareCacheManagerProxy`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/transaction/TransactionAwareCacheManagerProxy.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="after-commit-cache-mutation">Deferring put, evict, and clear until after commit</a>

<details>
<summary>Click for details</summary>

For a transaction-aware cache, `put`, `evict`, and `clear` can be deferred until a successful transaction reaches after-commit. If the transaction rolls back, the deferred cache mutation is not published.

This is useful when cache content mirrors committed data. Consider an update method that changes a database row and evicts the corresponding cache entry. Immediate eviction exposes the change in cache state before commit; deferring the eviction aligns the cache mutation with the successful database outcome.

There is an important visibility consequence inside the running transaction: because the mutation has not happened yet, a later cache lookup may still observe the old cached entry. Transaction-aware decoration should therefore be understood as after-commit mutation coordination, not as a transaction-local cache view.

### References

- Spring Framework Javadoc — [`TransactionAwareCacheDecorator`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/transaction/TransactionAwareCacheDecorator.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="immediate-cache-operations">putIfAbsent, evictIfPresent, and invalidate require immediate effect</a>

<details>
<summary>Click for details</summary>

Some `Cache` methods explicitly promise immediate visibility and therefore cannot be represented by the decorator's deferred after-commit queue.

- `putIfAbsent` has an atomic-if-absent contract and reports the prior mapping. Spring's out-of-the-box cache managers can perform it atomically, but a custom `Cache` that inherits the default two-step implementation may not; verify the native provider when this guarantee matters.
- `evictIfPresent` expects the key to be immediately invisible after the call and reports whether a mapping was known to exist.
- `invalidate` expects all entries to become immediately invisible and reports whether mappings were known to exist.

`TransactionAwareCacheDecorator` therefore cannot defer these operations in the same way as ordinary `put`, `evict`, and `clear`. Calling them during a transaction can change the cache before the database transaction commits, so they require extra care when cache state is meant to follow committed data.

The API distinction is intentional. Ordinary `put`/`evict`/`clear` allow asynchronous or deferred visibility; their immediate counterparts carry stronger timing expectations.

### References

- Spring Framework Javadoc — [`Cache`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/Cache.html)
- Spring Framework Javadoc — [`TransactionAwareCacheDecorator`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/transaction/TransactionAwareCacheDecorator.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="no-active-transaction">Behavior when no transaction is active</a>

<details>
<summary>Click for details</summary>

Transaction-aware decoration is conditional on an active Spring-managed transaction. When no transaction synchronization is active, the decorator performs ordinary `put`, `evict`, and `clear` operations immediately against the target cache.

That means the same cache object has timing that depends on the surrounding execution context:

```text
inside active Spring transaction
  -> supported mutation waits for after-commit

outside active Spring transaction
  -> mutation executes immediately
```

This behavior is useful, but it can surprise code that assumes a cache method always has identical visibility timing. When timing is part of correctness, tests should cover both transactional and non-transactional call paths.

### References

- Spring Framework Javadoc — [`TransactionAwareCacheDecorator`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/transaction/TransactionAwareCacheDecorator.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-module-boundary">Boundary with Spring Transaction Management</a>

<details>
<summary>Click for details</summary>

Spring Cache owns the question “when should this cache mutation become visible relative to a successful Spring-managed transaction?” It does not own the full transaction model.

Topics such as propagation, rollback rules, isolation, savepoints, transaction-manager selection, resource synchronization, and `@Transactional` proxy semantics belong to Spring Transaction Management. The cache module only needs enough of that model to understand the after-commit coordination boundary.

For production design, keep the source of truth explicit. A cache is usually a derived copy or optimization. Transaction-aware decoration can reduce stale or premature cache updates, but it does not turn the cache into the authoritative transactional record and does not solve every distributed consistency problem.

### References

- Spring Framework Reference — [Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
- Spring Framework Javadoc — [`org.springframework.cache.transaction`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/transaction/package-summary.html)

</details>

- [Back to top](#back-to-top)
