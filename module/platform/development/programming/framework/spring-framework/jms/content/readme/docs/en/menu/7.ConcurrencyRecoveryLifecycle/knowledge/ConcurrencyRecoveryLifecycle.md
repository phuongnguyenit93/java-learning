<a id="back-to-top"></a>

# Concurrency, Recovery, and Resource Lifecycle

## Menu
- [Listener Concurrency Ranges](#jms-listener-concurrency)
- [Parallelism and Message Ordering Trade-offs](#jms-ordering-tradeoffs)
- [Selectors, Durable, and Shared Subscription Configuration](#jms-selectors-subscriptions)
- [Listener Lifecycle, Startup/Shutdown, Recovery, BackOff, and Exception Handling](#jms-recovery-backoff)
- [SingleConnectionFactory and CachingConnectionFactory](#jms-connection-factory-wrappers)
- [DefaultMessageListenerContainer Cache Levels and External Transactions](#jms-dmlc-cache-levels)

## <a id="jms-listener-concurrency">Listener Concurrency Ranges</a>

<details>
<summary>Click for details</summary>

Concurrency decides how many consumer tasks a listener container may run in parallel. With `DefaultMessageListenerContainer`, `concurrentConsumers` is the minimum number kept scheduled and `maxConcurrentConsumers` is the upper bound to which the container may scale when traffic increases.

Spring accepts the compact `concurrency` form used by the factory and `@JmsListener`:

```text
"5-10" → minimum 5, maximum 10
"10"   → minimum 1, maximum 10
```

```java
@Bean
DefaultJmsListenerContainerFactory orderFactory(ConnectionFactory cf) {
    var factory = new DefaultJmsListenerContainerFactory();
    factory.setConnectionFactory(cf);
    factory.setSessionTransacted(true);
    factory.setConcurrency("3-12");
    return factory;
}

@JmsListener(
        destination = "orders.in",
        containerFactory = "orderFactory",
        concurrency = "5-20")
void handle(OrderPlaced order) {
}
```

The annotation-level value overrides the selected factory's concurrency for that endpoint. The underlying container still decides which concurrency features it can honor; DMLC supports the dynamic minimum/maximum model.

More consumers can improve throughput for a queue when processing is independent and the provider has work available. It does not guarantee linear scaling: database contention, provider prefetch, downstream rate limits, transaction cost, CPU, and connection/session limits can become the real bottleneck.

Treat the minimum as steady-state resource ownership and the maximum as permitted burst parallelism. A very large maximum can move overload downstream instead of solving it. Measure active consumers, processing latency, queue depth, error/redelivery rate, and downstream saturation before widening the range.

For a low-volume queue, Spring's DMLC guidance explicitly favors a single consumer unless there is a throughput reason to add more.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-ordering-tradeoffs">Parallelism and Message Ordering Trade-offs</a>

<details>
<summary>Click for details</summary>

Parallel consumption changes ordering guarantees. A single JMS consumer observes the provider's ordering rules for that consumer. Once multiple consumers process messages concurrently, completion order can differ even if the broker dispatched messages in sequence.

```text
message A → consumer 1 → slow work ─────────┐
message B → consumer 2 → fast work → commit │
message C → consumer 3 → fast work → commit │
                                             ↓
business completion order: B, C, A
```

Therefore increase DMLC concurrency only after deciding whether ordering is global, keyed, or irrelevant to the business rule. If all updates for one aggregate must be serialized, common designs route that aggregate to one ordered lane at the broker/application architecture level instead of hoping a highly concurrent listener will preserve completion order.

Queue consumption is the natural place for competing consumers: one message is delivered to one consumer according to provider/JMS semantics. Topic subscriptions need more care. Independent consumers/subscriptions can each receive a publication, so blindly increasing topic consumers may duplicate processing within one application node. JMS shared subscriptions exist specifically to let multiple consumers share a subscription; their semantics and provider constraints must be configured deliberately.

Dynamic DMLC scaling is therefore most straightforward for queues. For topics, start with one consumer per subscription unless a shared-subscription design or another explicit topology justifies parallel consumers.

**Trade-off:** concurrency improves throughput and can reduce backlog, but it increases simultaneous resource usage and makes race conditions, duplicate effects, and out-of-order business completion more visible. The listener implementation must be safe for concurrent invocation; avoid mutable shared fields unless they are intentionally synchronized.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-selectors-subscriptions">Selectors, Durable, and Shared Subscription Configuration</a>

<details>
<summary>Click for details</summary>

Selectors and subscriptions decide *which* messages a consumer is eligible to receive and *which subscription identity* a topic consumer represents. Spring exposes these controls on listener endpoints/containers but delegates their protocol semantics to Jakarta Messaging and the provider.

A JMS selector is evaluated by the provider before delivery. Use it for broker-supported metadata filtering, not as a replacement for arbitrary application logic.

```java
@JmsListener(
        destination = "orders.events",
        selector = "region = 'EU' AND priority >= 5")
void handlePriorityEuOrder(OrderPlaced event) {
}
```

For topic consumption, a durable subscription preserves the subscription across periods when the consumer is disconnected according to JMS/provider rules. Spring listener factories expose `subscriptionDurable`, while `@JmsListener.subscription` supplies the subscription name. In Spring Framework 6.1, if an annotated durable listener omits an explicit subscription name, the framework can derive a default name from the fully qualified listener method identity.

Shared subscriptions allow multiple consumers to participate in one subscription instead of creating independent copies of the same subscription stream. `subscriptionShared` is therefore conceptually different from merely increasing the number of independent topic consumers.

```java
factory.setPubSubDomain(true);
factory.setSubscriptionDurable(true);
factory.setSubscriptionShared(true);
factory.setConcurrency("3");
```

Subscription identity must remain stable enough for the intended durable/shared semantics. Changes to client identity, subscription name, selector, or destination can have provider-visible consequences; treat them as messaging-contract changes and test them against the real provider.

**Pitfall:** selectors reduce what reaches the consumer but can increase broker-side filtering work. Large collections of highly specialized selectors may be harder to operate than routing to more explicit destinations. That is an architecture decision, not something Spring JMS hides.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-recovery-backoff">Listener Lifecycle, Startup/Shutdown, Recovery, BackOff, and Exception Handling</a>

<details>
<summary>Click for details</summary>

A production listener has two failure domains: user-message processing can fail, and the listener infrastructure itself can lose or fail to establish JMS resources. DMLC handles both, but through different hooks.

Lifecycle is integrated with Spring's container model. DMLC participates in `SmartLifecycle`, so it can auto-start with the application context, stop during shutdown, and honor lifecycle phase ordering. The `JmsListenerEndpointRegistry` propagates lifecycle operations to annotation-created containers.

Infrastructure setup/recovery follows a retry policy. By default, DMLC uses a recovery interval of 5000 ms. Supplying a `BackOff` replaces that fixed interval policy; if the resulting `BackOffExecution` returns `STOP`, the container stops making further recovery attempts.

```java
var factory = new DefaultJmsListenerContainerFactory();
factory.setConnectionFactory(connectionFactory);
factory.setBackOff(new ExponentialBackOff(1_000L, 2.0));
factory.setErrorHandler(t ->
        log.error("JMS listener invocation failed", t));
```

`ErrorHandler` is the general hook for uncaught errors arising while the container processes a message; it is not limited to business exceptions. When the failure is a JMS `JMSException`, the container can notify the configured JMS `ExceptionListener` as well, and that failure can still reach the `ErrorHandler`/container logging path. Keep the failure domains distinct even though the hooks can overlap: a poison message throwing a business exception is different from a broken connection or failed consumer setup, and infrastructure recovery is controlled by the container's recovery policy rather than by the listener error hook alone.

`start()`/`stop()` control whether the container is actively consuming; they are not a retry API for individual failed messages. Message redelivery after a listener rollback is governed by transaction/acknowledgement and provider policy. Container recovery restores infrastructure after setup/connection problems.

During shutdown, allow the lifecycle contract to quiesce containers instead of terminating the JVM immediately. Abrupt termination can increase redelivery because the provider may not observe the final acknowledgement/commit.

Operationally, avoid an infinite hot retry loop against an unavailable broker. Use a meaningful `BackOff`, surface repeated recovery failures through monitoring, and decide whether `STOP` should require operator intervention for your environment.

### References

- Spring Framework 6.1 API — `DefaultMessageListenerContainer`
- Spring Framework 6.1 API — `AbstractMessageListenerContainer`

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-connection-factory-wrappers">SingleConnectionFactory and CachingConnectionFactory</a>

<details>
<summary>Click for details</summary>

JMS connections and sessions can be expensive, so Spring provides connection-factory wrappers for reuse. They solve a different problem from DMLC's own listener-resource caching.

`SingleConnectionFactory` exposes one shared JMS `Connection` and returns application handles whose normal `close()` does not immediately close that shared target connection. It is useful in standalone environments where connection creation is expensive and a single shared connection is appropriate. Its `reconnectOnException` option can rebuild the target connection after provider failure.

`CachingConnectionFactory` extends that model. It enables reconnect-on-exception by default and adds caches for JMS `Session` instances plus, by default, producers and consumers associated with those sessions.

```java
@Bean
CachingConnectionFactory cachingConnectionFactory(ConnectionFactory providerFactory) {
    var caching = new CachingConnectionFactory(providerFactory);
    caching.setSessionCacheSize(10);
    caching.setCacheProducers(true);
    caching.setCacheConsumers(false);
    return caching;
}
```

`sessionCacheSize` is a limit **per acknowledgement/session type**, not one global count. The default is 1. Cached sessions must still be logically closed by callers so the wrapper can return them to the cache.

Consumer caching has semantic consequences: a cached `MessageConsumer` can live longer than the apparent method scope, and durable subscriptions have additional close/re-registration rules. Temporary queue/topic producers and consumers are not cached.

For `JmsTemplate` and local `JmsTransactionManager` access, connection/session reuse can substantially reduce setup overhead. For a DMLC, however, Spring generally prefers the listener container's own caching because DMLC coordinates those resources with its lifecycle, concurrency, and transaction model. Stacking `CachingConnectionFactory` under DMLC without a reason can make stop/restart and dynamic-scaling behavior harder to reason about.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-dmlc-cache-levels">DefaultMessageListenerContainer Cache Levels and External Transactions</a>

<details>
<summary>Click for details</summary>

DMLC can cache resources at four conceptual levels: no cache, connection, session, or consumer. More caching reduces repeated setup cost, but cached transactional resources must still be valid for the transaction model in use.

The key Spring Framework default is:

```text
no external transaction manager
    → CACHE_CONSUMER

external transaction manager configured
    → CACHE_NONE
```

Without an external transaction manager, DMLC can safely keep its local JMS resources and normally caches through the consumer level. With an external transaction manager, Spring defaults to obtaining resources freshly so a Jakarta EE/JTA environment has the opportunity to enlist the correct connection/session in each transaction.

```java
var container = new DefaultMessageListenerContainer();
container.setConnectionFactory(xaAwareConnectionFactory);
container.setTransactionManager(jtaTransactionManager);
// Default cache level becomes CACHE_NONE with the external TM.
```

Some servers/providers can correctly enlist cached JMS resources. In those environments, explicitly increasing the cache level to `CACHE_CONNECTION` or `CACHE_SESSION` may reduce overhead. That is a provider/container integration optimization and must be verified against the actual XA environment; copying the setting from another provider is unsafe.

Do not confuse DMLC cache levels with `CachingConnectionFactory`. DMLC caching is part of the listener container's receive lifecycle. `CachingConnectionFactory` is an external wrapper that caches resources for callers in general. Combining both layers can retain consumers/resources in ways that interfere with DMLC stop/restart or dynamic scaling.

The decision sequence is:

```text
choose transaction model
    ↓
understand provider/server enlistment rules
    ↓
choose DMLC cache level
    ↓
measure setup overhead and recovery behavior
```

Optimize caching after transaction correctness. A faster consumer using a resource that is not enlisted in the intended transaction is a correctness bug, not a performance win.

</details>

- [Back to top](#back-to-top)
