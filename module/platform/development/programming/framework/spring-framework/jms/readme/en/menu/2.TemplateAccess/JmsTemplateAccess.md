<a id="back-to-top"></a>

# Template-based JMS Access

## Menu
- [ConnectionFactory, JmsOperations, and JmsTemplate](#jms-template-core-roles)
- [Sending Messages with JmsTemplate](#jms-template-send)
- [Synchronous Receive and Timeouts](#jms-template-sync-receive)
- [Request-Reply with sendAndReceive](#jms-template-request-reply)
- [Session and Producer Callbacks](#jms-template-callbacks)
- [Quality-of-Service Settings](#jms-template-qos)
- [Unchecked JMS Exception Translation](#jms-template-exception-translation)
- [JmsMessagingTemplate and the Spring Messaging Bridge](#jms-messaging-template-bridge)

## <a id="jms-template-core-roles">ConnectionFactory, JmsOperations, and JmsTemplate</a>

<details>
<summary>Click for details</summary>

`ConnectionFactory`, `JmsOperations`, and `JmsTemplate` sit at different levels of the access model.

The `ConnectionFactory` is the provider-facing factory for JMS connections. Spring needs one, but application services normally should not open and close JMS resources manually for every operation. `JmsOperations` is the Spring contract that describes common JMS access operations, and `JmsTemplate` is its standard implementation.

`JmsTemplate` applies the template pattern: application code supplies the variable part—destination, payload, selector, callback—while the template owns the repeated infrastructure sequence around it. Conceptually:

```text
obtain/use JMS resources
        ↓
perform caller operation
        ↓
translate JMS access failures
        ↓
release operation-scoped resources
```

The template is designed to be configured once and reused as a Spring bean. A `JmsTemplate` is thread-safe **once configured**, so one configured instance can be injected into multiple collaborators. Typical configuration sets a `ConnectionFactory`, optionally a default destination or destination name, a `MessageConverter`, receive timeout, and selected QoS options. Do not create a new `JmsTemplate` per message just to obtain isolation; the template itself is configuration-oriented, while JMS resources are acquired for operations according to Spring's resource-management rules. The other side of that contract is equally important: finish configuring a shared instance before concurrent use instead of mutating its settings around individual calls.

The quality of the supplied `ConnectionFactory` still matters. The 6.1 Javadoc explicitly warns that ad-hoc operations can suffer if connections, sessions, and producers are not appropriately pooled or shared. Later chapters separate provider pooling, Spring connection-factory wrappers, and listener-container caching because they solve different lifecycle problems.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-template-send">Sending Messages with JmsTemplate</a>

<details>
<summary>Click for details</summary>

Sending can start either from an already constructed JMS `Message` or from an application object that Spring converts.

The lower-level `send` family accepts a `MessageCreator`. Spring gives the callback the active JMS `Session`, and the callback creates the message:

```java
jmsTemplate.send("orders", session ->
        session.createTextMessage("order-42"));
```

This form is useful when message construction needs JMS-specific control. For ordinary application payloads, `convertAndSend` is usually clearer:

```java
jmsTemplate.convertAndSend("orders", orderCommand);
```

Here the configured `MessageConverter` creates the JMS message. An overload can apply a `MessagePostProcessor` after conversion, which is a focused place to add message-specific properties without embedding JMS construction into domain code.

Destination selection follows the same principle. The call may pass a `Destination`, a logical destination name, or rely on a configured default destination. A name is resolved by the template's `DestinationResolver`; it is not intrinsically a broker address string understood by Spring itself.

Treat successful return from `send` as completion of this send operation, not proof that a downstream consumer processed the business event. Likewise, do not use a producer-side callback to smuggle unrelated business logic into the JMS resource scope. Keep callbacks focused on message creation or producer interaction so lifecycle and transaction boundaries remain understandable.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-template-sync-receive">Synchronous Receive and Timeouts</a>

<details>
<summary>Click for details</summary>

`JmsTemplate.receive...` operations perform **synchronous** consumption. The caller waits while Spring obtains a consumer and asks the provider for a message.

The central control is `receiveTimeout`, expressed in milliseconds. By default, `JmsTemplate` waits indefinitely. `RECEIVE_TIMEOUT_INDEFINITE_WAIT` represents that behavior, while `RECEIVE_TIMEOUT_NO_WAIT` (and negative timeout values) select no-wait reception; a positive timeout bounds how long a receive call can wait. When no message arrives within the configured timeout, receive-style methods return `null` rather than inventing an application-level error.

That makes timeout handling part of correct business control flow:

```java
Message message = jmsTemplate.receive("reply.queue");
if (message == null) {
    // no message arrived within the configured receive window
}
```

`receiveAndConvert` adds conversion after reception, and selected variants allow a JMS selector. Those conveniences do not remove the blocking nature of the receive call.

An indefinite wait can be appropriate for a dedicated worker whose entire job is to wait, but it is dangerous on request threads or other bounded execution pools because the caller can be held forever. A very short timeout avoids long blocking but can turn normal latency into repeated empty polls. Choose the timeout from the calling workflow's latency budget rather than copying a global constant.

For continuously running background consumption, a listener container is usually the better Spring abstraction: it owns the receive lifecycle and recovery instead of forcing application code to build a polling loop around `JmsTemplate`.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-template-request-reply">Request-Reply with sendAndReceive</a>

<details>
<summary>Click for details</summary>

`sendAndReceive` implements a synchronous request-reply pattern on top of JMS. In Spring Framework 6.1, `JmsTemplate` sends the request and waits for the response on a temporary queue created for that exchange.

Conceptually:

```text
caller
  │ send request
  ▼
request destination
  │
  │ responder sends reply
  ▼
temporary reply destination
  │
  └──> waiting caller
```

The operation is convenient when a workflow truly requires a reply before continuing, but it changes the latency model: the calling thread now waits for another component to receive, process, and answer. If the responder is slow or unavailable, the request-reply path becomes slow or unavailable too.

One Spring 6.1 transaction boundary is easy to miss: `sendAndReceive` uses `JmsTemplate`'s local execution path for this exchange, with a local non-transacted session rather than simply reusing a caller's thread-bound transactional session. Do not assume that wrapping the caller in a Spring JMS transaction automatically makes this temporary-queue request/reply exchange part of that same local JMS transaction.

For raw JMS messages, use `sendAndReceive`. `JmsMessagingTemplate` additionally offers convert-and-send/request-reply forms that work with Spring Messaging payloads and target classes. In either case, conversion and correlation metadata must be compatible on both sides.

Do not turn every asynchronous business flow into request-reply simply because the API exists. A one-way command/event often scales and fails more independently. Request-reply is appropriate when the requester has a real dependency on the answer and can define a bounded wait/failure policy.

Also distinguish protocol-level reply correlation from application idempotency. Matching a reply to a request does not by itself make repeated requests safe; duplicate-processing policy remains an application and delivery-semantics concern.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-template-callbacks">Session and Producer Callbacks</a>

<details>
<summary>Click for details</summary>

Templates intentionally cover common operations, but Spring still exposes controlled escape hatches when an application needs direct JMS API access.

`SessionCallback<T>` runs with a Spring-managed JMS `Session`:

```java
String result = jmsTemplate.execute(session -> {
    // use the active Session for a focused operation
    return "done";
});
```

`ProducerCallback<T>` similarly gives access to a `Session` and `MessageProducer` associated with a destination. These callbacks are useful for operations that are awkward to express through the regular send/receive methods while still letting the template own resource acquisition and cleanup.

The mental model is **borrow, do the focused JMS work, return**. The callback is not a hand-off of resource ownership. Do not keep the supplied `Session`, producer, consumer, or objects tied to them and use them after the callback finishes; their lifecycle belongs to the template/transaction context.

Callbacks should also stay infrastructure-focused. If a callback starts orchestrating unrelated domain operations, the code becomes hard to reason about because business sequencing is now coupled to a low-level JMS resource scope.

Use the highest-level operation that expresses the requirement. Prefer `convertAndSend` for ordinary payload sending, `send` when constructing a JMS message is necessary, and callbacks when the standard surface genuinely cannot express the required JMS interaction. That keeps the exceptional low-level path visible in code review.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-template-qos">Quality-of-Service Settings</a>

<details>
<summary>Click for details</summary>

JMS producers can apply quality-of-service settings such as delivery mode, priority, time-to-live, and delivery delay. `JmsTemplate` exposes these as configuration so applications do not need to configure every producer manually.

One subtle rule in Spring 6.1 is that `deliveryMode`, `priority`, and `timeToLive` are used by the template only when **explicit QoS is enabled**. Calling setters for those values without enabling explicit QoS does not mean the template will necessarily override provider defaults on each send. Supplying `QosSettings` through `setQosSettings(...)` groups the core QoS values and also enables explicit QoS.

`deliveryDelay` is a separate setting for delayed delivery where supported by the JMS/provider stack. Message IDs and timestamps can also be enabled or disabled through template configuration.

These options are transport metadata, not a substitute for application workflow design. A priority value does not guarantee a global business ordering policy, and a time-to-live does not replace an application deadline or compensation strategy. Provider behavior and Jakarta Messaging semantics still determine how those fields are enforced.

Prefer a small number of deliberate producer policies instead of per-call QoS mutation. A shared `JmsTemplate` is configuration-oriented; repeatedly changing mutable configuration around concurrent calls is difficult to reason about. If two traffic classes require materially different policies, separate configured template beans are often clearer than changing one template dynamically.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-template-exception-translation">Unchecked JMS Exception Translation</a>

<details>
<summary>Click for details</summary>

The Jakarta Messaging API exposes checked `JMSException` failures. Requiring every Spring application service to catch and translate those exceptions would leak low-level infrastructure ceremony through the application layer.

Spring JMS therefore translates JMS access failures into its unchecked `org.springframework.jms.JmsException` hierarchy. `JmsTemplate` and related access infrastructure perform this translation around template operations.

The benefit is architectural rather than cosmetic: application code can let infrastructure failures propagate to a boundary that owns retry, rollback, error mapping, or logging policy. Callers do not need meaningless `catch (JMSException)` blocks that merely wrap and rethrow the same failure.

Unchecked does **not** mean ignorable. A send can still fail because a connection cannot be obtained, a destination cannot be resolved, conversion fails, or the provider rejects an operation. The translated exception preserves the fact that an infrastructure operation failed while integrating with Spring's runtime exception model.

Catch a Spring JMS exception only where the code can make a real decision—such as converting it to an application boundary error, triggering a specific fallback, or attaching context before rethrowing. Do not catch broad runtime exceptions around messaging and silently continue, because doing so can hide failed sends or receives and break transaction/error-handling semantics.

Conversion failures also deserve separate attention: Spring's `MessageConversionException` indicates that the application object/message representation could not be converted, which is usually a contract/configuration problem rather than a transient broker outage.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-messaging-template-bridge">JmsMessagingTemplate and the Spring Messaging Bridge</a>

<details>
<summary>Click for details</summary>

`JmsMessagingTemplate` is the bridge between Spring's generic messaging model and Spring JMS. It implements `JmsMessageOperations` plus the generic sending, receiving, and request-reply operation contracts for JMS destinations.

Its core design is composition: a `JmsMessagingTemplate` uses an underlying `JmsTemplate`. Constructing it with a `ConnectionFactory` implicitly creates that JMS template; constructing it with an existing `JmsTemplate` reuses the JMS configuration already established there.

This allows application code to work with Spring Messaging `Message<?>` objects, payloads, and header maps:

```java
jmsMessagingTemplate.convertAndSend(
        "orders",
        orderCommand,
        Map.of("tenant", "acme"));
```

The bridge then maps that model into JMS through its messaging converter and the delegated `JmsTemplate`. This is useful when a codebase already uses Spring Messaging conventions and wants a consistent payload/header programming style across transports.

Do not confuse this bridge with ownership of the generic messaging abstractions themselves. `Message`, `MessageHeaders`, channels, and the broader Spring Messaging model belong to the neighboring messaging curriculum. This section only explains how JMS participates in that model.

Also do not treat `JmsMessagingTemplate` as a replacement generation for `JmsTemplate`. In Spring 6.1 they coexist: use `JmsTemplate` for direct JMS-centric access and `JmsMessagingTemplate` when Spring Messaging-style messages, payloads, headers, or typed request-reply operations make the application interface clearer.

</details>

- [Back to top](#back-to-top)
