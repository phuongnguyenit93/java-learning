<a id="back-to-top"></a>

# Destinations, Conversion, and Message Metadata

## Menu
- [Queue and Topic at the Spring Boundary](#jms-destination-model)
- [DestinationResolver and Logical Names](#jms-destination-resolver)
- [MessageConverter Strategy](#jms-message-converter)
- [Payload and JSON-oriented Conversion](#jms-payload-conversion)
- [JMS Headers, Properties, and Spring Messaging Headers](#jms-headers-properties)
- [Correlation IDs and Reply Metadata](#jms-correlation-reply-metadata)

## <a id="jms-destination-model">Queue and Topic at the Spring Boundary</a>

<details>
<summary>Click for details</summary>

At the Spring JMS boundary, a destination answers a simple routing question: **where should this JMS operation send to or receive from?** Application code can work with a concrete JMS `Destination`, but Spring also lets it work with a logical name and defer the conversion to an actual queue/topic object.

That distinction keeps application services from depending directly on provider-specific destination objects. A template call such as:

```java
jmsTemplate.convertAndSend("orders", command);
```

does not mean Spring treats `"orders"` as a universal network address. The configured `DestinationResolver` interprets the name for the current JMS environment.

Spring still needs to know which messaging domain is intended when it must dynamically create/resolve a destination through the JMS `Session`. In `JmsTemplate`, `pubSubDomain=false` represents the point-to-point/queue side and is the default; `pubSubDomain=true` selects the publish-subscribe/topic side for operations where that distinction is required.

This chapter deliberately stops at the Spring integration boundary. Queue/topic delivery semantics, subscription rules, persistence guarantees, and provider administration belong to Jakarta Messaging or the broker. The Spring concern is how a logical application destination becomes the `Destination` used by template or listener infrastructure.

A practical rule is to make the logical destination name part of application configuration, not hard-coded provider topology. That makes environment changes easier and keeps broker naming policy outside business code.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-destination-resolver">DestinationResolver and Logical Names</a>

<details>
<summary>Click for details</summary>

`DestinationResolver` is the strategy interface that turns a destination name into a JMS `Destination`. Its contract receives the current `Session`, the logical name, and a `pubSubDomain` flag, then returns the queue/topic object that Spring should use.

`JmsTemplate` uses `DynamicDestinationResolver` by default. That strategy calls the active JMS `Session`'s `createQueue(...)` or `createTopic(...)` method for the supplied name. Despite those JMS method names, Spring is asking the provider for a destination object; whether that call merely resolves an existing physical destination or also causes provider-specific dynamic destination creation is provider behavior, not a portable Spring guarantee. The strategy is intentionally simple and works well when that dynamic-resolution contract fits the target provider.

Other environments need a different lookup model. `JndiDestinationResolver` is available when destinations are administered and exposed through JNDI, and a custom resolver can encode an application's own naming/lookup policy. The key design point is that the producer/consumer call site does not change:

```text
"orders"
   ↓
DestinationResolver
   ↓
provider/JNDI-specific Destination
```

Resolution failures are infrastructure failures. They should surface clearly rather than silently falling back to a different destination. A typo in a logical name can otherwise become a production routing bug that is much harder to detect than a failed startup/send.

Keep resolution policy centralized. If different call sites translate the same logical name differently, it becomes impossible to reason about where messages actually flow. Prefer one deliberate resolver strategy per integration policy and override it only when the runtime environment genuinely requires another lookup mechanism.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-message-converter">MessageConverter Strategy</a>

<details>
<summary>Click for details</summary>

Application code usually wants to send domain data, while JMS transports `jakarta.jms.Message` objects. `MessageConverter` is the Spring strategy that owns this representation boundary.

Its contract is bidirectional:

```text
Java object --toMessage--> JMS Message
Java object <--fromMessage-- JMS Message
```

`JmsTemplate` uses `SimpleMessageConverter` by default. In Spring 6.1 it supports the standard simple mappings: `String` ↔ `TextMessage`, `byte[]` ↔ `BytesMessage`, `Map` ↔ `MapMessage`, and `Serializable` ↔ `ObjectMessage`. Unknown inbound message types are left as the raw JMS `Message` rather than guessed into an arbitrary domain type.

The `Serializable`/`ObjectMessage` mapping is convenient but creates a strong wire-level dependency on Java serialization and compatible classes on both sides. Treat it as a compatibility-sensitive option, not the preferred default for independent services. Text/bytes plus an explicit cross-service representation such as JSON usually gives a clearer integration contract.

The converter is invoked by operations such as `convertAndSend` and `receiveAndConvert`, and related listener adapters can also use a converter before invoking application code. This means conversion is a shared contract across producer and consumer paths; changing it is an integration change, not merely a local serialization refactor.

A converter should have a narrow responsibility: represent an application payload as a JMS message and reconstruct the expected payload. Business validation, database lookup, authorization, and orchestration do not belong inside conversion logic.

When conversion fails, Spring raises a `MessageConversionException`. Treat that as evidence of an incompatible payload/configuration contract. Retrying the same malformed representation against the same converter usually does not solve the problem.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-payload-conversion">Payload and JSON-oriented Conversion</a>

<details>
<summary>Click for details</summary>

Real applications often prefer a language-neutral representation such as JSON instead of Java serialization. Spring's `MappingJackson2MessageConverter` provides a JSON-oriented `MessageConverter` based on Jackson.

In Spring Framework 6.1, it can write JSON to a `BytesMessage` by default or to a `TextMessage` when configured with `MessageType.TEXT`. For inbound JMS conversion, the converter needs enough information to decide which Java type should be created. Its default mechanism is a configured **type-id message property**, and `setTypeIdPropertyName(...)` must be set to convert an incoming JMS message to a Java object. Optional type-id mappings let an application use stable synthetic ids rather than exposing raw Java class names. A custom subclass can override `getJavaTypeForMessage(...)` to implement a different type-resolution strategy, but a target class requested later by a higher-level receive API does not by itself remove the JMS converter's inbound type-id requirement.

A focused configuration can therefore look conceptually like:

```java
MappingJackson2MessageConverter converter =
        new MappingJackson2MessageConverter();
converter.setTargetType(MessageType.TEXT);
converter.setTypeIdPropertyName("message_type");
converter.setTypeIdMappings(Map.of("order", OrderCommand.class));
```

The important contract is not “JSON is automatic.” Producer and consumer must agree on payload representation, encoding, and type identification. If one side sends a JSON document without the metadata expected by the receiving converter, deserialization cannot reliably infer the domain type.

Avoid coupling a cross-service message contract to arbitrary Java class names when a stable logical type id is available. Logical ids make refactoring package/class names less likely to break integration and keep wire metadata focused on the message contract rather than implementation structure.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-headers-properties">JMS Headers, Properties, and Spring Messaging Headers</a>

<details>
<summary>Click for details</summary>

A JMS message has more than a payload. It also carries standard JMS headers and application properties used for routing, correlation, expiration, reply handling, and other transport-level concerns. Spring Messaging has its own `MessageHeaders` model, so crossing from `org.springframework.messaging.Message<?>` into JMS requires a mapping layer.

`MessagingMessageConverter` performs that bridge. It delegates payload conversion to an underlying Spring JMS `MessageConverter` and delegates header mapping to a `JmsHeaderMapper`. The default mapper implementation is `SimpleJmsHeaderMapper`.

Conceptually:

```text
Spring Message
  payload ──> payload MessageConverter ──> JMS message body
  headers ──> JmsHeaderMapper ──────────> JMS headers/properties
```

This is why a header map passed through `JmsMessagingTemplate` is not simply copied byte-for-byte into the JMS message. Header names and values must be legal and meaningful in the JMS representation, and some JMS-defined fields have dedicated semantics rather than ordinary property semantics.

Keep application metadata intentional. A header that another service depends on is part of the integration contract and should have a stable name/type. Avoid dumping arbitrary framework or internal object state into JMS properties just because a header mapper exists.

The generic Spring Messaging header model is owned by the neighboring messaging module. The JMS module's responsibility is the boundary: how those headers are represented when a Spring message enters or leaves JMS.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-correlation-reply-metadata">Correlation IDs and Reply Metadata</a>

<details>
<summary>Click for details</summary>

Request-reply flows need metadata that connects an outbound request to the response path. Two pieces are especially important at the Spring JMS boundary: **reply destination** and **correlation identity**.

JMS provides `JMSReplyTo` to tell a responder where a reply should be sent and `JMSCorrelationID` to help associate related messages. Spring's template and listener infrastructure preserves and uses these concepts rather than inventing a separate request-reply protocol.

For `JmsTemplate.sendAndReceive`, Spring creates a temporary reply queue for the operation, sends the request, and waits for the corresponding reply. In listener-style request-reply, `MessageListenerAdapter` or annotation-driven listener infrastructure can send a method return value to the incoming message's reply destination or to an explicitly configured/default response destination.

Do not overload correlation metadata with business identity. A correlation id answers “which exchange/message does this relate to?” while an order id, payment id, or idempotency key answers a domain question. They can sometimes share a value by design, but treating them as automatically equivalent creates brittle coupling.

Likewise, a reply destination is routing metadata, not an availability guarantee. The requester still needs a timeout and failure policy, and the responder must use compatible conversion/header conventions. Correct metadata makes the exchange addressable; it does not make the distributed workflow atomic or failure-free.

</details>

- [Back to top](#back-to-top)
