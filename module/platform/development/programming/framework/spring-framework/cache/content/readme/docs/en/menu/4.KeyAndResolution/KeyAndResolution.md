<a id="back-to-top"></a>

# Cache Keys, Conditions, and Cache Resolution

## Menu
- [Default key generation and SimpleKey](#default-key-generation)
- [Custom KeyGenerator](#custom-key-generator)
- [SpEL keys and the cache evaluation context](#spel-key-context)
- [How do condition and unless differ?](#condition-vs-unless)
- [Resolving caches through CacheManager](#cache-manager-resolution)
- [Dynamic cache resolution with CacheResolver](#cache-resolver)
- [Multiple cache names: lookup order and update semantics](#multiple-cache-names)
- [Common cache-key design pitfalls](#cache-key-design-pitfalls)

## <a id="default-key-generation">Default key generation and SimpleKey</a>

<details>
<summary>Click for details</summary>

When no explicit `key` expression or custom `KeyGenerator` is configured, Spring uses `SimpleKeyGenerator`. Its goal is to derive a stable cache key from the method parameters without requiring application code to define one for every operation.

The Spring 6.1 behavior is:

```text
no parameters            → SimpleKey.EMPTY
one non-null parameter   → that parameter itself
otherwise                → new SimpleKey(parameters)
```

`SimpleKey` implements equality and hashing over its elements, so it avoids the collision-prone “hash only” strategy used by much older Spring versions. A generated key is safe for `ConcurrentMapCache`, although another provider may impose different key representation or serialization requirements.

The default is convenient only when **all method parameters belong to the cache identity**. If a parameter affects execution but not the result identity—or the result identity needs normalization—a custom strategy is clearer.

### References

- [Spring Framework 6.1 Javadoc — `SimpleKeyGenerator`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/interceptor/SimpleKeyGenerator.html)
- [Spring Framework 6.1 Javadoc — `SimpleKey`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/interceptor/SimpleKey.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="custom-key-generator">Custom KeyGenerator</a>

<details>
<summary>Click for details</summary>

`KeyGenerator` is Spring's programmatic extension point for computing a cache key from the target object, invoked `Method`, and arguments:

```java
@FunctionalInterface
public interface KeyGenerator {
    Object generate(Object target, Method method, Object... params);
}
```

A custom generator is useful when many operations share the same key convention—for example, canonicalizing a composite business identifier or deliberately ignoring a transport-only argument.

It can be selected at several levels: globally through `CachingConfigurer`, at class level through `@CacheConfig(keyGenerator = ...)`, or on an individual cache operation through its `keyGenerator` attribute.

An operation should use either an explicit SpEL `key` or a named `keyGenerator`; these are alternative key strategies. A custom generator should also return values whose `equals`/`hashCode` behavior and provider representation remain stable for the lifetime of the cache entry.

### References

- [Spring Framework 6.1 Javadoc — `KeyGenerator`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/interceptor/KeyGenerator.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="spel-key-context">SpEL keys and the cache evaluation context</a>

<details>
<summary>Click for details</summary>

Cache annotations can compute keys with Spring Expression Language (SpEL). This is useful when the cache identity is only part of the method arguments or needs a small projection.

For example:

```java
@Cacheable(cacheNames = "books", key = "#isbn.rawNumber + ':' + #includeReviews")
public Book findBook(Isbn isbn, boolean includeReviews) { ... }
```

Both `isbn` and `includeReviews` shape the returned `Book`, so both participate in the cache identity. Omitting either input could make two logically different calls share one cached value.

The evaluation context exposes cache-operation metadata such as `#root.method`, `#root.methodName`, `#root.target`, `#root.targetClass`, `#root.args`, and `#root.caches`. Method arguments can be referenced by name when parameter-name information is available, or by indexed aliases such as `#p0` and `#a0`.

Some expressions evaluated after method invocation can also use `#result`; `unless` is the common example. Do not assume `#result` exists for an expression that must run before the target method.

SpEL keeps simple key policy close to the annotation. Once the expression becomes complex business logic, a `KeyGenerator` or a dedicated value object usually gives better reuse and testability.

### References

- [Spring Framework 6.1 Javadoc — `Cacheable.key`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/Cacheable.html#key())

</details>

- [Back to top](#back-to-top)

---

## <a id="condition-vs-unless">How do condition and unless differ?</a>

<details>
<summary>Click for details</summary>

`condition` and `unless` are operation-specific controls. For `@Cacheable`, they run at different times.

`condition` is evaluated **before** the method invocation. If it evaluates to `false`, the cache operation is bypassed for that call. Because the method has not run yet, the expression cannot depend on its result.

`unless` is evaluated **after** the method returns and vetoes storing the result when it evaluates to `true`. That timing allows `unless` to inspect `#result`.

```java
@Cacheable(
    cacheNames = "products",
    condition = "#id > 0",
    unless = "#result.discontinued"
)
```

In this example, invalid identifiers skip caching up front; a successfully loaded discontinued product is returned to the caller but is not stored.

For `@Cacheable(sync=true)`, `unless` is not supported. That restriction belongs to the synchronized-loading contract and is covered with the rest of the `sync=true` rules in the concurrency chapter.

Do not generalize this timing to every cache annotation. In particular, `@CachePut.condition` is evaluated after method invocation and may refer to `#result`, because a put operation always invokes the method before deciding whether to update the cache.

</details>

- [Back to top](#back-to-top)

---

## <a id="cache-manager-resolution">Resolving caches through CacheManager</a>

<details>
<summary>Click for details</summary>

Cache annotations usually start with logical cache names. Those names still need to become actual `Cache` instances before a lookup or write can happen.

With the standard arrangement, Spring uses a cache resolver backed by the selected `CacheManager`. Each declared cache name is passed to `CacheManager.getCache(name)`, producing the `Cache` objects used by the operation.

`CacheManager.getCache(name)` is nullable: a manager may return `null` when the named cache does not exist and cannot be created. In the standard resolution path, every declared cache name therefore needs to resolve successfully; an unresolved name is an error rather than an implicit no-op cache.

Which manager is selected follows the configuration hierarchy introduced in Chapter 3: an operation can name a `cacheManager`, `@CacheConfig` can provide a class default, and `CachingConfigurer` can provide the global annotation-infrastructure default.

This separation is useful because the annotation can stay focused on logical cache names while manager configuration owns provider construction and lifecycle. If cache selection itself must depend on runtime invocation context, use a `CacheResolver` rather than embedding provider choice into the cache name.

</details>

- [Back to top](#back-to-top)

---

## <a id="cache-resolver">Dynamic cache resolution with CacheResolver</a>

<details>
<summary>Click for details</summary>

`CacheResolver` is the strategy interface used when cache selection needs more context than a fixed list of names. It receives a `CacheOperationInvocationContext`, which exposes the cache operation, target, method, and arguments, and returns the `Cache` instances for that invocation.

This can support policies such as routing to a cache based on tenant, argument category, or another application-level rule while keeping the method annotation declarative.

An operation can select a resolver by bean name with `cacheResolver`. `@CacheConfig` can provide a class-level resolver, and `CachingConfigurer` can provide the global resolver. A custom resolver is an alternative to resolving names through a chosen `CacheManager`; operation-level `cacheResolver` and `cacheManager` should not be configured as competing strategies.

Use dynamic resolution for **which cache participates**, not for hiding provider-specific business semantics. If the application depends on native topology or data structures, that concern belongs at the provider integration boundary.

### References

- [Spring Framework 6.1 Javadoc — `CacheResolver`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/interceptor/CacheResolver.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="multiple-cache-names">Multiple cache names: lookup order and update semantics</a>

<details>
<summary>Click for details</summary>

One cache operation can declare multiple cache names. For `@Cacheable`, Spring consults them in declaration order for a hit. If one cache already contains the value, the other participating caches are updated with that value; on a complete miss, the newly computed result is stored in the participating caches.

This is useful when one method intentionally keeps the same logical result in more than one cache region, but it should not be mistaken for a provider-independent tiered-cache architecture. Spring defines the operation across the resolved `Cache` objects; it does not define native promotion, replication, or consistency rules between independent cache products.

There is also an important Spring 6.1 async/reactive caveat: a provider may determine a miss only after an asynchronous lookup completes. In that late-miss case, later caches may no longer be consulted. The concurrency/async chapter explains that limitation in detail.

For ordinary synchronous access, keep the declaration order intentional and assume every named cache is part of the same application-level caching policy.

### References

- [Spring Framework 6.1 Javadoc — `Cacheable.cacheNames`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/Cacheable.html#cacheNames())

</details>

- [Back to top](#back-to-top)

---

## <a id="cache-key-design-pitfalls">Common cache-key design pitfalls</a>

<details>
<summary>Click for details</summary>

A cache can return the wrong result even when the provider works perfectly if the application chooses the wrong key.

Common failures include:

- **Missing dimensions:** locale, tenant, authorization scope, version, or another input changes the result but is absent from the key.
- **Mutable key objects:** fields used by `equals`/`hashCode` change after insertion, making lookup behavior unstable.
- **Unstable representations:** a key depends on `toString()`, object identity, or another representation not designed as a durable identifier.
- **Cross-method collisions:** two methods share the same cache region and generate equal keys even though their value contracts differ. The default generator uses parameters, not the method name, as the key identity.
- **Provider mismatch:** a key works in a local Java map but is unsuitable for a remote provider's serialization or interoperability requirements.

A good cache key is deterministic, contains exactly the dimensions that define the cached value, has stable equality semantics, and can be represented safely by the chosen provider.

When key policy becomes important enough to explain in prose, it is often worth representing it explicitly with a small value object or a reusable `KeyGenerator` instead of relying on a fragile string expression.

</details>

- [Back to top](#back-to-top)
