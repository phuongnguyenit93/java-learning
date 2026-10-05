<a id="back-to-top"></a>

# Concurrency, Cache Stampede, and Async Caching

## Menu
- [Concurrent cache misses and duplicate computation](#concurrent-cache-miss)
- [The meaning of @Cacheable(sync=true)](#cacheable-sync)
- [Restrictions of sync=true](#cacheable-sync-restrictions)
- [Async retrieval in the Cache SPI since Spring 6.1](#cache-async-retrieve)
- [Caching with CompletableFuture](#completable-future-caching)
- [Caching with Mono and Flux](#reactive-return-types)
- [Provider capabilities for async caching](#async-provider-capability)
- [Multiple caches and late-determined misses](#multi-cache-late-miss)
- [The boundary of coarse-grained reactive caching](#reactive-cache-boundary)

## <a id="concurrent-cache-miss">Concurrent cache misses and duplicate computation</a>

<details>
<summary>Click for details</summary>

Without synchronization, two or more callers can miss the same cache key at nearly the same time. Each caller may then execute the expensive method before any of them stores the result. The cache eventually contains a value, but the expensive work has already been duplicated.

```text
T1: miss -> compute -----------------> put
T2:   miss -> compute -----------------> put
T3:     miss -> compute -----------------> put
```

This pattern is often called a cache stampede or thundering herd when the duplicated work becomes large enough to stress a database, remote service, or CPU-heavy computation. The Spring cache abstraction does not add a global lock by default. Concurrency behavior ultimately depends on the chosen cache operation and provider.

The first design question is therefore whether duplicate computation is merely wasteful or actually dangerous. For cheap, idempotent work, occasional duplication may be acceptable. For expensive or rate-limited work, the application may need synchronized loading or a stronger provider-specific strategy.

### References

- Spring Framework Reference — [Synchronized Caching](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-cacheable-synchronized)

</details>

- [Back to top](#back-to-top)

---

## <a id="cacheable-sync">The meaning of @Cacheable(sync=true)</a>

<details>
<summary>Click for details</summary>

`@Cacheable(sync=true)` asks the cache provider to synchronize loading for the same key. Conceptually, the first caller performs the computation while concurrent callers for that key wait for the value to become available.

```java
@Cacheable(cacheNames = "catalog", key = "#id", sync = true)
public Product load(String id) {
    return repository.findRequired(id);
}
```

Spring routes this behavior through the cache loading contract, notably `Cache.get(key, Callable)` for synchronous access. The annotation is intentionally described as a hint because the real atomicity and locking semantics come from the selected provider. A custom `Cache` implementation must honor the contract appropriately if the application depends on this behavior.

`sync=true` coordinates callers of one key through one cache operation. It is not a general distributed lock and does not automatically coordinate unrelated code paths that bypass the same cache/provider.

### References

- Spring Framework Javadoc — [`@Cacheable.sync()`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/Cacheable.html#sync())
- Spring Framework Javadoc — [`Cache.get(Object, Callable)`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/Cache.html#get(java.lang.Object,java.util.concurrent.Callable))

</details>

- [Back to top](#back-to-top)

---

## <a id="cacheable-sync-restrictions">Restrictions of sync=true</a>

<details>
<summary>Click for details</summary>

Synchronized caching deliberately narrows the operation model. Spring defines three important restrictions for `sync=true`:

- `unless` is not supported.
- Exactly one cache may be specified.
- No other cache-related operation may be combined on the same method.

These restrictions keep one synchronized load tied to one cache/key decision. A design that needs several cache regions, post-result veto logic, or simultaneous put/evict behavior should model those concerns explicitly instead of forcing them into a synchronized `@Cacheable` method.

### References

- Spring Framework Javadoc — [`@Cacheable.sync()`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/Cacheable.html#sync())

</details>

- [Back to top](#back-to-top)

---

## <a id="cache-async-retrieve">Async retrieval in the Cache SPI since Spring 6.1</a>

<details>
<summary>Click for details</summary>

Spring Framework 6.1 added asynchronous retrieval methods to the `Cache` SPI:

```java
CompletableFuture<?> retrieve(Object key)

<T> CompletableFuture<T> retrieve(
        Object key,
        Supplier<CompletableFuture<T>> valueLoader)
```

The key property is non-blocking retrieval. A provider may know immediately that a key is absent, or it may only discover the miss after asynchronous work finishes. The API therefore distinguishes an early miss from a late-determined miss through the returned value/future shape.

This SPI expansion is what allows annotation-driven caching to adapt to `CompletableFuture` and reactive return types without forcing every cache access into a blocking `get`. Provider support matters: merely returning a future from the business method does not make a fundamentally blocking cache backend asynchronous.

### References

- Spring Framework Javadoc — [`Cache.retrieve(...)`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/Cache.html)
- Spring Framework Reference — [Caching with CompletableFuture and Reactive Return Types](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-cacheable-reactive)

</details>

- [Back to top](#back-to-top)

---

## <a id="completable-future-caching">Caching with CompletableFuture</a>

<details>
<summary>Click for details</summary>

Since Spring Framework 6.1, cache annotations understand `CompletableFuture` return values. On a miss, the method can return immediately with a future; Spring caches the object produced when that future completes successfully. On a hit, the cached value is exposed to the caller through a `CompletableFuture`.

```java
@Cacheable("books")
public CompletableFuture<Book> findBook(String isbn) {
    return client.fetchBook(isbn);
}
```

The cache stores the produced `Book` value rather than treating the `CompletableFuture` object itself as the business value to preserve indefinitely. This distinction matters for key reuse: later callers should receive a future representing the cached result, not a stale wrapper from an earlier invocation.

The cache must support the asynchronous retrieval contract for this arrangement to remain non-blocking. `sync=true` can also be combined with supported future-based caching so concurrent misses compute the value once.

### References

- Spring Framework Reference — [Caching with CompletableFuture and Reactive Return Types](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-cacheable-reactive)

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-return-types">Caching with Mono and Flux</a>

<details>
<summary>Click for details</summary>

Spring Framework 6.1 also adapts annotation-driven caching for Reactor return types. For a `Mono<T>`, Spring caches the emitted object when it becomes available and recreates a `Mono` from the cached value on a hit.

For a `Flux<T>`, Spring collects the emitted elements into a `List` and caches that list after the publisher completes. A cache hit is then adapted back into a `Flux`.

```java
@Cacheable("authors")
public Flux<Book> findByAuthor(String author) {
    return repository.findByAuthor(author);
}
```

The `Flux` rule is especially important: caching is based on the fully collected result, not on independently cached stream elements. An infinite publisher, a very large sequence, or a pipeline whose value depends on subscription-time context may therefore be a poor fit for annotation-driven caching.

### References

- Spring Framework Reference — [Caching with CompletableFuture and Reactive Return Types](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-cacheable-reactive)

</details>

- [Back to top](#back-to-top)

---

## <a id="async-provider-capability">Provider capabilities for async caching</a>

<details>
<summary>Click for details</summary>

Async-aware annotations require a cache implementation that can perform compatible retrieval. Spring's `ConcurrentMapCacheManager` adapts to the asynchronous `Cache.retrieve` style, while `CaffeineCacheManager` can use Caffeine's asynchronous cache mode when `setAsyncCacheMode(true)` is enabled.

Provider capability is broader than method signatures. A backend may expose a future yet still rely on blocking I/O internally, or it may determine cache misses only after a remote round trip. Those details affect latency, thread usage, multi-cache behavior, and whether `sync=true` provides the coordination the application expects.

When asynchronous behavior matters, verify the provider's documented semantics for retrieval, loading, null handling, cancellation, error propagation, and synchronization. The Spring abstraction defines the interaction contract; it does not erase the backend's concurrency model.

### References

- Spring Framework Reference — [Caching with CompletableFuture and Reactive Return Types](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-cacheable-reactive)
- Spring Framework Javadoc — [`ConcurrentMapCacheManager`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/concurrent/ConcurrentMapCacheManager.html)
- Spring Framework Javadoc — [`CaffeineCacheManager`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/caffeine/CaffeineCacheManager.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="multi-cache-late-miss">Multiple caches and late-determined misses</a>

<details>
<summary>Click for details</summary>

With ordinary synchronous access, multiple cache names on `@Cacheable` are consulted in declaration order for a hit, and a newly computed value is written to all participating caches. Async/reactive access adds an important provider-dependent boundary.

Some providers can report a miss immediately. Others only discover the miss after an asynchronous lookup finishes. When a miss is late-determined, Spring may no longer be able to continue consulting later caches in the list. The cache list must therefore not be treated as a provider-independent fallback chain in async mode.

This matters for designs such as “local cache, then remote cache.” Such a hierarchy needs semantics that are explicit about lookup order, miss timing, writes, invalidation, and failure handling. A multi-name `@Cacheable` declaration alone does not guarantee that architecture for asynchronous providers.

### References

- Spring Framework Javadoc — [`@Cacheable.cacheNames()`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/Cacheable.html#cacheNames())
- Spring Framework Javadoc — [`Cache.retrieve(Object)`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/Cache.html#retrieve(java.lang.Object))

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-cache-boundary">The boundary of coarse-grained reactive caching</a>

<details>
<summary>Click for details</summary>

Spring explicitly describes annotation-driven reactive caching as coarse-grained. It caches the object emitted by a `Mono`, or a fully collected `List` of objects from a `Flux`. It does not model a reactive pipeline's operators, subscriber context, demand, per-element lifetime, or backpressure semantics.

That makes it suitable when a reactive method is conceptually an asynchronous request for a stable result set. It is less suitable when the stream itself is the behavior to preserve, when values are unbounded, or when each subscriber must observe distinct execution context.

Use the Spring Cache abstraction when the thing being cached is still a method result. Use a reactive-aware data/cache design when caching must participate deeply in stream composition, backpressure, distributed invalidation, or per-element policy.

### References

- Spring Framework Reference — [Caching with CompletableFuture and Reactive Return Types](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-cacheable-reactive)

</details>

- [Back to top](#back-to-top)
