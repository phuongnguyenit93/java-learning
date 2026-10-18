# Spring Cache

This module teaches the **Spring Framework Cache Abstraction** at the framework-mechanics level: how Spring expresses caching policy around method invocation without coupling application code directly to one cache provider.

The goal is not to teach Redis, Caffeine, or a specific distributed cache product. The focus is understanding:

- when a result should or should not be cached;
- the roles of `Cache` and `CacheManager`;
- how `@Cacheable`, `@CachePut`, `@CacheEvict`, `@Caching`, and `@CacheConfig` work together;
- cache keys, conditions, `unless`, `CacheResolver`, global defaults through `CachingConfigurer`, and multi-cache semantics;
- invalidation, stale data, and consistency gaps;
- the proxy and self-invocation boundary of declarative caching;
- concurrency, `sync=true`, `CompletableFuture`, `Mono`, and `Flux` in Spring Framework 6.1;
- provider boundaries, JCache interoperability, and transaction-aware cache updates.

## Prerequisites

Learners should already understand:

- Spring IoC/container and bean lifecycle;
- the basic method-invocation and proxy mental model from Spring AOP;
- Java equality/hash semantics for objects used as cache keys;
- basic transaction concepts before reaching transaction-aware caching.

Redis or another cache provider is not a prerequisite for this module.

## Learning flow

```text
Cache purpose and boundary
        ↓
Cache / CacheManager abstraction
        ↓
Declarative caching annotations
        ↓
Key / condition / cache resolution
        ↓
Update / invalidation / consistency
        ↓
Proxy interception boundary
        ↓
Concurrency / sync / async / reactive
        ↓
Provider and JCache interoperability
        ↓
Failure / transaction-aware updates
        ↓
End-to-end cache strategy
```

## Chapter groups

1. **Foundation** — cache purpose, source-of-truth boundary, `Cache`, and `CacheManager`.
2. **Policy declaration** — annotations, key generation, conditions, and cache resolution.
3. **Correctness** — updates, eviction, invalidation, stale data, and consistency.
4. **Runtime mechanics** — proxy interception, self-invocation, concurrency, and async/reactive return types.
5. **Integration boundary** — provider capabilities, JCache, failure policy, and transaction-aware mutation.
6. **Synthesis** — designing a production-oriented cache strategy without confusing Spring abstraction with provider semantics.

## Module boundary

This module **owns** the Spring Cache abstraction and declarative caching semantics.

The following concerns are handed off:

- deep proxy/advice mechanics → **Spring AOP**;
- transaction propagation, rollback, and resource synchronization → **Transaction Management**;
- Redis TTL, serialization, topology, and data structures → **Spring Data Redis**;
- provider-specific eviction algorithms, storage topology, and distributed consistency → the corresponding cache-provider documentation.

Spring Cache lets an application express caching policy consistently. It does not make different providers share one runtime model, and it does not provide the cache store itself.
