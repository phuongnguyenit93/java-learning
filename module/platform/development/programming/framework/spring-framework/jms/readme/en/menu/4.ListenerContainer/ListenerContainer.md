<a id="back-to-top"></a>

# Message Listener Container Model

## Menu
- [Message-Driven POJO Model](#jms-mdp-model)
- [What the Listener Container Owns](#jms-listener-container-responsibilities)
- [DefaultMessageListenerContainer Mental Model](#jms-dmlc-model)
- [DefaultMessageListenerContainer vs SimpleMessageListenerContainer](#jms-listener-container-choices)
- [MessageListenerAdapter and POJO Delegation](#jms-message-listener-adapter)
- [JCA Message Endpoint Boundary](#jms-jca-endpoint-boundary)

## <a id="jms-mdp-model">Message-Driven POJO Model</a>

<details>
<summary>Click for details</summary>

A message-driven application should not need business objects to own an endless `MessageConsumer.receive()` loop, connection recovery, consumer recreation, and shutdown coordination. Spring's **Message-Driven POJO** model separates those runtime duties from the object that performs the business action.

The mental model is:

```text
JMS provider
    ↓
Spring MessageListenerContainer
    ↓
adapter / endpoint invocation
    ↓
application POJO
```

The POJO can therefore focus on a method such as `handle(OrderCommand command)` while framework infrastructure controls when and how that method is invoked. Depending on the configuration style, adaptation can be explicit through `MessageListenerAdapter` or provided by the annotation-driven endpoint infrastructure discussed in the next chapter.

The key benefit is lifecycle ownership. A consumer that lives for hours or days must survive application startup ordering, temporary provider failures, stop/restart operations, transaction boundaries, and concurrency changes. Those are container concerns, not responsibilities that should be reimplemented inside every message handler.

“POJO” does not imply the JMS layer disappears. Conversion can still fail, a listener can still run inside a JMS transaction, and redelivery can still occur according to the configured acknowledgement/transaction semantics. The model simply lets application code depend on a clean method contract while the integration boundary remains explicit in configuration.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-listener-container-responsibilities">What the Listener Container Owns</a>

<details>
<summary>Click for details</summary>

A `MessageListenerContainer` is a long-lived runtime component. It owns the infrastructure needed to turn provider messages into controlled application invocations.

Across Spring's JMS container implementations, the responsibilities include the core lifecycle around JMS connections/sessions/consumers, start and stop behavior, destination/listener configuration, dispatch into the configured listener, and integration with acknowledgement/transaction settings. Concrete implementations add different concurrency, polling, recovery, and caching strategies.

That ownership changes how application code should be designed. A listener method should assume the container controls the surrounding receive lifecycle. It should not close the container's `Session`, stop the consumer, or retain provider resources for later use.

The container is also the place where operational policy belongs. Concurrency, recovery timing, task execution, transaction manager selection, selectors, durable subscription options, and caching can be configured without rewriting the business handler.

This separation is what makes annotation-driven JMS understandable: `@JmsListener` is not a magical background thread. It declares an endpoint that Spring eventually realizes through a listener container with concrete lifecycle and resource rules.

When debugging, inspect both sides of the boundary. If the handler is never invoked, first ask whether the container is running, connected, subscribed to the intended destination, and able to convert/dispatch the message before treating the business method as the source of the failure.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-dmlc-model">DefaultMessageListenerContainer Mental Model</a>

<details>
<summary>Click for details</summary>

`DefaultMessageListenerContainer` (DMLC) is Spring's flexible standalone listener container for plain JMS environments. Its central mechanism is a loop of `MessageConsumer.receive()` calls executed by asynchronous invoker tasks rather than registering one provider callback and leaving the entire receive lifecycle to `setMessageListener`.

At startup it creates the configured number of consumer invokers (`concurrentConsumers`). Each invoker works with JMS resources and repeatedly receives/dispatches messages. If `maxConcurrentConsumers` is higher, DMLC can create additional invokers under load and later shrink back toward the baseline. A configured Spring `TaskExecutor` controls how those asynchronous work units run.

DMLC is also designed to recover from temporary provider unavailability. It can re-establish JMS handles after failures and supports stop/restart as a managed `SmartLifecycle` component. Those properties are why it is commonly the default mental model for a production Spring JMS consumer.

Its receive-loop design also integrates well with transactional reception. An external `PlatformTransactionManager` can wrap receive/listener execution, and the container has explicit resource-caching policy. The exact transaction semantics and cache levels belong to later chapters; at this point the important fact is that DMLC owns both **consumer lifecycle** and **receive execution**.

Do not infer that more concurrent consumers always means more throughput. Ordering requirements, topic subscription behavior, provider capacity, downstream resources, and transaction cost all constrain useful concurrency. DMLC gives the controls; it does not remove those system trade-offs.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-listener-container-choices">DefaultMessageListenerContainer vs SimpleMessageListenerContainer</a>

<details>
<summary>Click for details</summary>

Spring also provides `SimpleMessageListenerContainer` (SMLC). The difference is architectural, not just a smaller configuration surface.

SMLC uses the JMS client's `MessageConsumer.setMessageListener(...)` mechanism and creates a **fixed** number of JMS sessions/consumers. It does not dynamically scale the consumer count with runtime load. This makes it a straightforward choice when a simple provider-driven callback model and fixed concurrency are sufficient.

DMLC, by contrast, drives reception through its own repeated `receive()` loop, runs listener invokers through Spring's `TaskExecutor` abstraction, supports dynamic scaling between configured lower/upper concurrency bounds, and has stronger built-in recovery/runtime-management capabilities. Its design also supports transactional receive patterns that need control around each receive-and-invoke unit.

The selection question is therefore about runtime requirements:

```text
simple fixed consumers + provider callback
        → SimpleMessageListenerContainer

Spring-controlled receive loop + recovery/scaling/transaction flexibility
        → DefaultMessageListenerContainer
```

Avoid choosing SMLC merely because its name says “Simple,” or DMLC merely because it is the default in examples. Choose from the lifecycle and transaction model the consumer actually needs. For most robust standalone Spring JMS applications, DMLC is the more capable baseline; SMLC remains useful when its simpler fixed model is enough.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-message-listener-adapter">MessageListenerAdapter and POJO Delegation</a>

<details>
<summary>Click for details</summary>

`MessageListenerAdapter` is an explicit bridge from the JMS listener contract to an ordinary Java object. It implements the listener-facing interfaces and delegates message handling to a target object's method via reflection.

By default the delegate method name is `handleMessage`. Before invocation, the adapter uses a Spring JMS `MessageConverter`—`SimpleMessageConverter` by default—to extract message content, so the target can work with values such as `String`, `byte[]`, `Map`, or another converted payload instead of raw JMS `Message` objects.

```java
class OrderHandler {
    public void handleMessage(String payload) {
        // business handling
    }
}
```

The adapter can also support a reply when the delegate method returns a **non-null object/value**. That returned object is converted into a JMS message and sent to the incoming message's reply destination or to a configured default response destination. A non-`void` method that returns `null` produces no reply. A subtle boundary matters here: automatic response sending is available through the `SessionAwareMessageListener` entry point used by Spring listener containers; using the adapter only as a standard JMS `MessageListener` does not provide that response-generation path.

`MessageListenerAdapter` makes the Message-Driven POJO idea concrete, but it is not the only adaptation mechanism. `@JmsListener` uses richer endpoint/method-resolution infrastructure. Keep this adapter as the mental bridge: the container receives a JMS message; an adapter converts/delegates; the POJO handles business data.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-jca-endpoint-boundary">JCA Message Endpoint Boundary</a>

<details>
<summary>Click for details</summary>

Spring can also expose JMS listeners through a **JCA message endpoint** model. This is an alternative integration path for environments that use a Jakarta Connectors resource adapter, typically application-server/resource-adapter deployments.

`JmsMessageEndpointManager` extends Spring's generic JCA endpoint management and adds JMS-specific `ActivationSpec` support. A `JmsActivationSpecConfig` describes common JMS activation settings; an activation-spec factory turns that into the provider-specific JCA `ActivationSpec` used to activate the endpoint.

The ownership model is different from a standalone DMLC:

```text
standalone Spring JMS
    → Spring listener container owns receive loop/resources

JCA endpoint environment
    → ResourceAdapter + ActivationSpec drive endpoint activation
```

The Spring 6.1 contract also has a clear limit: the JCA endpoint manager supports standard JMS `MessageListener` endpoints, but it cannot support Spring's `SessionAwareMessageListener` because the JCA endpoint contract does not expose the current JMS `Session` to the listener in that way.

Do not treat JCA as a mandatory “advanced mode” every JMS application should learn to configure. It exists for a specific deployment/integration model. For ordinary standalone Spring applications using a JMS `ConnectionFactory`, DMLC and annotation-driven listener endpoints are the normal path; JCA belongs when the runtime architecture already revolves around a resource adapter and endpoint activation.

</details>

- [Back to top](#back-to-top)
