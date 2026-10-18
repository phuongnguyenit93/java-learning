<a id="back-to-top"></a>

# Production Readiness and Integration Boundaries

## Menu
- [Spring JMS Observability in Framework 6.1: Publish, Process, and Receive Boundary](#jms-observability-61)
- [Testing Conversion, Listeners, and Transactions](#jms-testing-strategy)
- [Real Provider Integration Tests](#jms-real-provider-testing)
- [Direct Spring JMS vs Spring Integration](#jms-direct-vs-integration)
- [Boundary with Spring Messaging, AMQP, and Kafka](#jms-neighboring-messaging-boundaries)
- [End-to-End Spring JMS Mental Model](#jms-end-to-end-synthesis)

## <a id="jms-observability-61">Spring JMS Observability in Framework 6.1: Publish, Process, and Receive Boundary</a>

<details>
<summary>Click for details</summary>

Spring Framework 6.1 integrates JMS with Micrometer's Jakarta JMS instrumentation when `io.micrometer:micrometer-jakarta9` is available. The framework does not record useful data merely because Micrometer is on the classpath: an `ObservationRegistry` must be configured on the JMS component that owns the operation.

There are two main observation names in this baseline:

```text
jms.message.publish
    → time spent sending a JMS message

jms.message.process
    → time spent processing a message in the application
```

Configure publication observation on `JmsTemplate`:

```java
@Bean
JmsTemplate jmsTemplate(
        ConnectionFactory connectionFactory,
        ObservationRegistry observationRegistry) {
    var template = new JmsTemplate(connectionFactory);
    template.setObservationRegistry(observationRegistry);
    return template;
}
```

Configure processing observation on the listener-container factory:

```java
factory.setObservationRegistry(observationRegistry);
```

An `@JmsListener` return value that causes Spring to send a reply can also produce publish observation activity. Trace context is propagated through JMS message headers by the Micrometer JMS instrumentation.

Spring 6.1 intentionally does **not** create `jms.message.receive` observations for blocking `MessageConsumer.receive` waiting time. Measuring time spent waiting for work is not the same as measuring application processing and would not provide the desired processing trace scope. This is a useful boundary when reading dashboards: absence of a receive timer is expected behavior, not necessarily missing instrumentation.

The default observation key values include messaging operation, destination information, message/correlation identifiers where available, and error information. Keep high-cardinality identifiers out of metric labels in your own conventions; use traces/log correlation for per-message investigation.

### References

- Spring Framework Reference — Observability Support, JMS messaging instrumentation
- Micrometer Jakarta JMS instrumentation used by Spring Framework 6.1

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-testing-strategy">Testing Conversion, Listeners, and Transactions</a>

<details>
<summary>Click for details</summary>

Testing Spring JMS is strongest when each test proves behavior at the smallest layer that owns it. A single test style cannot prove conversion, method binding, transaction rollback, redelivery, and provider interoperability at once.

A useful test pyramid is:

```text
plain unit tests
    → business listener logic and idempotency decisions

converter tests
    → Java object ↔ JMS Message representation

Spring configuration tests
    → @JmsListener registration, factory selection, method binding, validation

provider integration tests
    → real send/receive, transaction, redelivery, selector/subscription behavior
```

Keep the domain handler callable without a broker where possible:

```java
class OrderListener {
    private final OrderService service;

    @JmsListener(destination = "orders.in")
    void handle(OrderPlaced event) {
        service.apply(event);
    }
}
```

The business behavior of `service.apply` can be tested directly. Separate Spring/JMS integration tests then prove that the annotated endpoint converts and routes a real JMS message into that method.

For transaction tests, assert the observable contract instead of implementation calls. Examples: a failed listener causes the message to become eligible for redelivery under a transacted session; a successful listener commits once; an idempotency record prevents a duplicate business effect. Merely verifying that `Session.rollback()` was invoked on a mock does not prove provider redelivery semantics.

For reply endpoints, test destination precedence and conversion: incoming `JMSReplyTo`, static `@SendTo`, and dynamic `JmsResponse` represent different contracts. Validation tests should distinguish conversion failure from constraint failure.

Avoid sleeps as the main synchronization mechanism for asynchronous tests. Prefer bounded receives, latches, Awaitility-like bounded conditions, or provider/test-harness mechanisms that fail deterministically when the expected message does not arrive.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-real-provider-testing">Real Provider Integration Tests</a>

<details>
<summary>Click for details</summary>

A real provider integration test answers questions that mocks cannot: whether destinations resolve, selectors are accepted, transactions actually roll back, redelivery metadata appears, durable/shared subscriptions behave as expected, and the provider's connection-recovery behavior matches production assumptions.

Use an isolated provider instance or test namespace with deterministic setup and cleanup. The concrete mechanism can be a containerized broker, embedded/test broker supported by the provider, or an environment supplied by CI. The important property is that the test exercises the same Jakarta Messaging contract through the actual provider client library.

High-value scenarios for this module include:

- `JmsTemplate` sends a converted payload and the consumer receives the expected representation;
- a transacted listener throws and the provider redelivers according to configured policy;
- successful retry does not create a duplicate business effect;
- selectors accept/reject messages as expected;
- durable/shared subscription configuration survives the lifecycle relevant to the application;
- DMLC recovers after the provider connection is interrupted;
- request/reply uses the expected reply destination and correlation metadata.

Bound every asynchronous assertion. A test that can wait forever after a broker/configuration failure is operationally worse than a failing test because it hides the root cause behind CI timeouts.

Provider-specific redelivery count headers, dead-letter addresses, administration APIs, and connection-pool tuning belong in provider integration tests or provider documentation. Keep the Spring JMS Knowledge contract focused on what the framework configures and delegates.

Do not call a mocked `ConnectionFactory` test an integration test. Mocks are useful for narrow collaborator behavior; only a provider can demonstrate provider protocol/resource semantics.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-direct-vs-integration">Direct Spring JMS vs Spring Integration</a>

<details>
<summary>Click for details</summary>

Direct Spring JMS is appropriate when the application's integration problem is fundamentally JMS-shaped: send to a destination, receive synchronously, run a message-driven listener, convert payloads, manage JMS resources, and participate in transactions.

Spring Integration sits one level higher. It implements Enterprise Integration Patterns such as channels, routers, filters, transformers, splitters, aggregators, service activators, and protocol adapters/gateways. Its JMS inbound/outbound adapters and gateways use Spring JMS infrastructure underneath.

```text
application needs direct JMS access
    → Spring Framework JMS

application needs an integration flow composed from EIP building blocks
    → Spring Integration
        ↓ may use
      Spring JMS adapter/gateway
        ↓ uses
      JmsTemplate / listener container
```

Use direct Spring JMS when a small number of JMS endpoints map cleanly to application services. Moving to Spring Integration can be valuable when the integration topology itself is a first-class design that needs routing, transformation, aggregation, channel composition, or multiple transports.

The choice does not imply that Spring Integration replaces JMS semantics. A Spring Integration JMS adapter still relies on destinations, transactions, acknowledgements, redelivery, and provider behavior from the underlying JMS stack.

Likewise, avoid rebuilding an EIP framework inside a collection of `@JmsListener` methods. If listener code becomes mostly routing tables, fan-out, filtering, aggregation state, and transport bridges, that is a signal to evaluate the dedicated integration abstraction.

### References

- Spring Framework Reference — JMS
- Spring Integration Reference — JMS Support

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-neighboring-messaging-boundaries">Boundary with Spring Messaging, AMQP, and Kafka</a>

<details>
<summary>Click for details</summary>

Several neighboring Spring technologies use the word "message", but they own different abstraction layers.

**Spring Messaging** (`org.springframework.messaging`) provides framework-level message primitives such as `Message`, headers, handler-method argument resolution, and related abstractions. Spring JMS uses these primitives for conveniences such as `JmsMessagingTemplate`, `@Header`, and annotated method invocation. Spring Messaging does not itself define JMS queues, sessions, acknowledgement, or provider recovery.

**Spring Framework JMS** owns direct Jakarta Messaging integration: `JmsTemplate`, listener containers, `@JmsListener`, conversion/header mapping, JMS resource wrappers, and transaction integration.

**Spring Integration** composes integration flows and Enterprise Integration Patterns. Its JMS channel adapters/gateways are consumers of Spring JMS support rather than a replacement implementation of the JMS client layer.

**Spring AMQP** and **Spring for Apache Kafka** are separate Spring projects for their respective messaging systems. Their listener containers, templates, acknowledgement/offset models, retry mechanisms, and broker semantics are not interchangeable with JMS APIs merely because concepts such as producer and consumer sound similar.

```text
Spring Messaging
    → generic framework message model

Spring Framework JMS
    → Jakarta Messaging integration

Spring Integration
    → EIP / integration-flow composition, including JMS adapters

Spring AMQP / Spring Kafka
    → protocol/broker-specific Spring abstractions
```

When migrating between messaging technologies, preserve business intent but re-evaluate delivery, ordering, transactions, retry, consumer-group/subscription, and observability semantics. Copying a JMS setting name into a Kafka or AMQP design is not a portability strategy.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-end-to-end-synthesis">End-to-End Spring JMS Mental Model</a>

<details>
<summary>Click for details</summary>

The complete Spring JMS mental model can be reduced to ownership boundaries and one runtime flow.

```text
application payload
    ↓
JmsTemplate / @JmsListener
    ↓ conversion + headers
Spring JMS infrastructure
    ↓
ConnectionFactory / Session / producer or consumer
    ↓
Jakarta Messaging provider
    ↓
broker destination
```

On the producer side, `JmsTemplate` owns repetitive resource access and conversion while the provider owns actual delivery. On the consumer side, a `MessageListenerContainer` owns consumer threads, JMS resources, lifecycle, recovery, acknowledgement/transaction interaction, and invocation of the listener endpoint.

Annotation-driven listeners add a declarative layer:

```text
@EnableJms
    ↓ discovers
@JmsListener endpoint
    ↓ built by
JmsListenerContainerFactory
    ↓ managed by
JmsListenerEndpointRegistry
    ↓ runtime
MessageListenerContainer
```

Reliability comes from combining the correct mechanisms rather than from one annotation: transacted/managed consumption, provider redelivery policy, idempotent business effects, bounded retries/dead-letter handling, and observable failures. Concurrency is a throughput control with ordering/resource trade-offs. Caching is an optimization that must respect the transaction model.

Production observability in Spring Framework 6.1 records JMS publish/process operations when the appropriate `ObservationRegistry` and Micrometer Jakarta JMS instrumentation are configured; blocking receive wait time is intentionally outside that observation model.

Finally, keep ownership clear. Jakarta Messaging/provider documentation defines protocol and broker semantics. Spring Framework JMS defines the direct Spring integration layer. Spring Messaging supplies generic message abstractions. Spring Integration, AMQP, and Kafka own their separate integration/EIP or protocol-specific models.

If a learner can trace one message through these layers and explain where conversion, listener lifecycle, transaction outcome, redelivery, concurrency, and observation are decided, the module's end-to-end model is complete.

</details>

- [Back to top](#back-to-top)
