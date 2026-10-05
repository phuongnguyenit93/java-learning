<a id="back-to-top"></a>

# Transactions and Delivery Semantics

## Menu
- [Acknowledgement Modes at the Spring Boundary](#jms-acknowledgement-model)
- [Transacted Sessions and Listener Rollback](#jms-session-transacted)
- [JmsTransactionManager and Local Transactions](#jms-local-transaction-manager)
- [Thread-bound JMS Resources and JmsTemplate Participation](#jms-thread-bound-resources)
- [JTA/XA and External Transaction Coordination](#jms-jta-xa-boundary)
- [Redelivery, Duplicate Processing, and Idempotency](#jms-redelivery-idempotency)

## <a id="jms-acknowledgement-model">Acknowledgement Modes at the Spring Boundary</a>

<details>
<summary>Click for details</summary>

Acknowledgement answers one question: when may the provider consider a consumed message acknowledged? It is related to transactions and redelivery, but it is not the same concept as a business transaction.

Spring exposes the JMS session acknowledgement mode through its listener containers. The critical behavior depends on the container implementation. For `DefaultMessageListenerContainer` (DMLC), the default `AUTO_ACKNOWLEDGE` mode acknowledges before listener execution. A user exception therefore does not cause that message to be redelivered merely because the listener failed. `SimpleMessageListenerContainer` has different timing for automatic acknowledgement, which is one reason production reliability decisions should be based on the chosen container rather than the symbolic mode name alone.

Spring's listener-container contract documents these broad choices:

- `AUTO_ACKNOWLEDGE`: convenient, but DMLC does not provide exception-driven redelivery.
- `DUPS_OK_ACKNOWLEDGE`: lazy acknowledgement; duplicate delivery is permitted by the JMS contract and listener exceptions still do not give the same rollback semantics as a transaction.
- `CLIENT_ACKNOWLEDGE`: Spring acknowledges after successful listener execution and provides best-effort redelivery for listener failures/interruption.
- `sessionTransacted=true`: commit after successful processing and rollback on listener failure, giving the clearest local JMS redelivery semantics.

```java
@Bean
DefaultJmsListenerContainerFactory reliableFactory(ConnectionFactory cf) {
    var factory = new DefaultJmsListenerContainerFactory();
    factory.setConnectionFactory(cf);
    factory.setSessionTransacted(true);
    return factory;
}
```

For reliability-sensitive DMLC listeners, Spring recommends transacted sessions or an external transaction manager instead of relying on the default auto-acknowledge mode.

**Boundary:** acknowledgement and redelivery rules still rely on Jakarta Messaging and the provider. Spring decides how its container drives the JMS `Session`; it does not redefine provider persistence, dead-letter policy, or delivery guarantees.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-session-transacted">Transacted Sessions and Listener Rollback</a>

<details>
<summary>Click for details</summary>

A transacted JMS `Session` makes message receipt and JMS work performed in that session part of one local JMS transaction. For a listener container, `sessionTransacted=true` gives a simple failure model:

```text
receive message
    ↓
invoke listener
    ├─ success → commit JMS Session
    └─ exception → rollback JMS Session → message becomes eligible for redelivery
```

This local transaction is especially useful when the listener receives a message and sends a JMS reply or another JMS message using the same transactional context. It does not automatically include an unrelated database transaction.

For locally transacted Spring listener containers, there is an important bridge to template-based sends. `AbstractMessageListenerContainer` exposes the listener's JMS `Session` to `JmsTemplate` calls by default (`exposeListenerSession=true`). A `JmsTemplate` using the same listener `ConnectionFactory` can therefore reuse that active listener session, so a send performed during listener processing can participate in the same local JMS transaction instead of silently creating an independent JMS transaction. Turning `exposeListenerSession` off makes template access use a fresh session from the same underlying connection; sessions managed by an external transaction manager are exposed to `JmsTemplate` regardless of this flag. This is a listener-container mechanism and is distinct from the explicit thread-bound resource model of `JmsTransactionManager` covered below.

```java
@JmsListener(destination = "orders.in", containerFactory = "reliableFactory")
void handle(OrderPlaced event) {
    validate(event);
    // If this throws, the container rolls back the transacted JMS Session.
    orderService.apply(event);
}
```

The transaction boundary is the listener invocation managed by the container. If the method exits normally, Spring can commit the local JMS transaction. If processing throws, the container rolls it back. Provider redelivery policy then determines when and how the message returns and what happens after repeated failures.

Do not catch every exception and return normally unless that is truly the desired outcome. Swallowing a failure tells the container that processing succeeded, so a transacted session has no reason to roll back.

Local JMS transaction semantics are intentionally narrower than "all business work is atomic." If the listener also writes to a database with a separate local transaction, a crash can occur after one resource commits and before the other does. That is why duplicate handling and idempotency remain important even with `sessionTransacted=true`.

**Practical default:** for a standalone DMLC consumer that needs JMS redelivery and does not require distributed atomicity, a locally transacted JMS session is usually the first mechanism to consider.

### References

- Spring Framework 6.1 API — `AbstractMessageListenerContainer#setExposeListenerSession`
- Spring Framework Reference — Processing Messages Within Transactions

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-local-transaction-manager">JmsTransactionManager and Local Transactions</a>

<details>
<summary>Click for details</summary>

`JmsTransactionManager` is Spring's `PlatformTransactionManager` for **one JMS `ConnectionFactory`**. It manages local JMS resource transactions; it is not a distributed transaction manager.

At transaction begin, the manager obtains a JMS `Connection`/`Session` pair from its configured `ConnectionFactory` and binds that resource holder to the current thread. Commit commits the JMS session; rollback rolls it back. Spring's JMS access components can then join the transaction without application code passing the `Session` around.

```java
@Bean
JmsTransactionManager jmsTransactionManager(ConnectionFactory connectionFactory) {
    return new JmsTransactionManager(connectionFactory);
}

@Transactional("jmsTransactionManager")
public void publish(OrderPlaced event) {
    jmsTemplate.convertAndSend("orders.events", event);
    jmsTemplate.convertAndSend("audit.events", event);
}
```

Both sends can use the same thread-bound transactional JMS resource when the template and transaction manager refer to the same underlying `ConnectionFactory` identity expected by Spring's resource lookup.

For listener containers, Spring's own guidance usually favors `sessionTransacted=true` when the transaction does not need external management. Configure a container `transactionManager` when external transaction coordination is actually required; in practice that is commonly JTA/XA rather than `JmsTransactionManager`.

`JmsTransactionManager` does not make a database resource part of the same atomic transaction. Placing JDBC work beside JMS work while using separate local transaction managers still leaves a two-resource failure window.

Spring recommends appropriate connection/session reuse around this manager. `CachingConnectionFactory` can avoid repeatedly creating expensive JMS resources while each transaction still uses its own transactional `Session`.

### References

- Spring Framework 6.1 API — `JmsTransactionManager`
- Spring Framework Reference — JMS Transaction Management

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-thread-bound-resources">Thread-bound JMS Resources and JmsTemplate Participation</a>

<details>
<summary>Click for details</summary>

The important integration feature of `JmsTransactionManager` is not an annotation; it is Spring's thread-bound resource model. The transaction manager binds JMS resources under the configured `ConnectionFactory`, and `JmsTemplate` asks Spring's JMS resource utilities for a transactional `Session` before creating a new one.

```text
transaction starts
    ↓
Connection/Session bound to current thread
    ↓
JmsTemplate call
    ↓
detect/reuse transactional Session
    ↓
transaction manager commits or rolls back once
```

This means normal template code can remain free of manual `Session.commit()` / `Session.rollback()` calls.

```java
@Transactional("jmsTransactionManager")
public void sendBatch(List<OrderPlaced> events) {
    for (OrderPlaced event : events) {
        jmsTemplate.convertAndSend("orders.events", event);
    }
    // An exception before method completion marks/causes rollback through
    // the Spring transaction infrastructure.
}
```

Resource matching matters. If the transaction manager and the template are wired to unrelated `ConnectionFactory` instances or proxy layers with incompatible identities, the template may not find the expected bound resource. Keep one clearly owned factory chain and inject it consistently.

Native JMS code does not automatically participate merely because it runs on the same thread. Spring-managed access through `JmsTemplate` and `ConnectionFactoryUtils` is transaction-aware. Legacy/native code that must transparently join Spring transactions may require a `TransactionAwareConnectionFactoryProxy`, with that proxy placed at the outer edge of the connection-factory chain as documented by Spring.

Thread binding also explains a limitation: the transaction context is not automatically transported to arbitrary child threads or executor tasks. Do not offload transactional JMS work to another thread and assume it still uses the same session.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-jta-xa-boundary">JTA/XA and External Transaction Coordination</a>

<details>
<summary>Click for details</summary>

Local JMS transactions coordinate one JMS resource. When a single business operation must atomically include JMS plus another XA-capable resource, such as a database enlisted in the same global transaction, the relevant model is JTA/XA.

A DMLC can wrap message reception plus listener execution in an external `PlatformTransactionManager`. For true distributed JMS coordination this is typically a `JtaTransactionManager` together with an XA-aware JMS `ConnectionFactory` and corresponding XA resource configuration.

```text
JTA transaction begins
    ↓
XA JMS receive enlisted
    +
XA database work enlisted
    ↓
prepare / commit coordination
```

The JMS listener container's `transactionManager` property is therefore different from `sessionTransacted=true`. A local transacted session asks the JMS provider to commit/rollback one JMS session. JTA/XA asks an external coordinator to control all enlisted XA resources as one distributed transaction.

Spring's JMS accessor/container APIs also document an important detail: inside a managed JTA transaction, the `transacted` and acknowledgement flags passed when creating the JMS session are controlled by the managed environment/provider wrapper rather than acting as independent local-session policy.

Choose XA only when the atomicity requirement justifies the operational cost. XA adds coordinator configuration, XA-capable drivers/providers, recovery concerns, and runtime overhead. Many systems instead use local transactions plus idempotent consumers, an outbox/inbox pattern, or another consistency design owned by the application architecture.

**Do not overclaim exactly-once.** XA can atomically coordinate the resources actually enlisted in that transaction. External HTTP calls, emails, filesystem effects, or non-XA services are not made atomic merely because the listener runs under JTA.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-redelivery-idempotency">Redelivery, Duplicate Processing, and Idempotency</a>

<details>
<summary>Click for details</summary>

Redelivery is a failure-recovery mechanism, not proof that an application will observe each business event exactly once. A message can be delivered again after rollback, connection loss, broker recovery, or an unfortunate crash near a commit boundary. Application code must decide whether repeating the business effect is safe.

With a locally transacted listener, the common path is:

```text
message delivered
    ↓
business work runs
    ↓
JVM/provider/network failure before JMS commit
    ↓
broker cannot observe committed receipt
    ↓
message may be redelivered
```

The business effect may already have happened before the crash. Therefore `JMSRedelivered` is useful diagnostic information, but it cannot prove whether the earlier attempt changed an external system.

Idempotency means that repeating an operation does not create an incorrect additional effect. Typical approaches include:

- store a stable event/message business id and reject already-applied ids;
- express a database change as an upsert or state transition guarded by the current state;
- keep an inbox/deduplication table in the same database transaction as the business update;
- make downstream commands carry an idempotency key where the downstream system supports it.

```java
@Transactional
public void apply(OrderPlaced event) {
    if (!processedEventRepository.tryInsert(event.eventId())) {
        return; // already applied
    }
    orderRepository.markPlaced(event.orderId());
}
```

Do not key deduplication only on an unstable delivery attempt identifier if the producer's business event has a stable identity available. Conversely, do not keep an unbounded in-memory set and call it durable deduplication; process restarts erase it and memory grows indefinitely.

Repeated poison-message failures need a bounded operational policy, usually provider redelivery limits and a dead-letter/error destination. Spring JMS exposes listener failure and recovery hooks, while the exact dead-letter/redelivery schedule is provider-owned.

</details>

- [Back to top](#back-to-top)
