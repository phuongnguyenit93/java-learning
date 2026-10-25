<a id="back-to-top"></a>

# Annotation-driven Listener Endpoints

## Menu
- [Enabling Annotation-driven JMS with @EnableJms](#jms-enable-jms)
- [JmsListenerContainerFactory and Container Creation](#jms-listener-container-factory)
- [JmsListenerEndpointRegistry and Endpoint Lifecycle](#jms-listener-endpoint-registry)
- [@JmsListener Method Signatures](#jms-listener-method-signature)
- [Payload Conversion, Headers, and Validation Integration](#jms-listener-conversion-validation)
- [Reply Handling with JMSReplyTo, @SendTo, and JmsResponse](#jms-listener-replies)
- [Programmatic Endpoint Registration](#jms-programmatic-endpoints)

## <a id="jms-enable-jms">Enabling Annotation-driven JMS with @EnableJms</a>

<details>
<summary>Click for details</summary>

`@EnableJms` turns annotated listener methods into runtime JMS endpoints. The annotation imports Spring's JMS bootstrap configuration, which registers the infrastructure that scans Spring-managed beans for `@JmsListener`. It does not create a broker, a `ConnectionFactory`, or a destination by itself; those remain application/provider configuration.

The useful mental model is:

```text
Spring-managed bean method annotated with @JmsListener
        ↓ detected by JmsListenerAnnotationBeanPostProcessor
JmsListenerEndpoint metadata
        ↓ passed to a JmsListenerContainerFactory
MessageListenerContainer
        ↓ receives JMS messages
method invocation
```

For the conventional case, define a `JmsListenerContainerFactory` bean named `jmsListenerContainerFactory`. An endpoint can select another factory with the annotation's `containerFactory` attribute, and `JmsListenerConfigurer` can establish an explicit default for the registrar.

```java
@Configuration
@EnableJms
class JmsConfiguration {

    @Bean
    DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        var factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setSessionTransacted(true);
        return factory;
    }
}

@Component
class OrderListener {

    @JmsListener(destination = "orders.in")
    void handle(OrderPlaced order) {
        // business processing
    }
}
```

Only Spring-managed beans participate in this annotation processing. Constructing `OrderListener` manually with `new` bypasses the container and therefore bypasses `@JmsListener` discovery.

**Practical rule:** treat `@EnableJms` as the switch that enables endpoint discovery. Reliability, concurrency, transactions, destination resolution, conversion, recovery, and observability are properties of the listener container/factory created for those endpoints.

### References

- Spring Framework 6.1 API — `EnableJms`
- Spring Framework Reference — JMS annotation-driven listener endpoints

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-listener-container-factory">JmsListenerContainerFactory and Container Creation</a>

<details>
<summary>Click for details</summary>

A `JmsListenerContainerFactory` is a builder for the runtime container that serves one endpoint. The endpoint describes *what* to listen to; the factory supplies reusable runtime policy such as the `ConnectionFactory`, destination resolver, transaction mode, acknowledgement mode, converter, concurrency, error handling, recovery, and observation registry.

`DefaultJmsListenerContainerFactory` creates a `DefaultMessageListenerContainer` (DMLC). This is the usual standalone Spring JMS choice because DMLC owns polling consumers, recovery, dynamic concurrency, caching, and optional external transaction coordination.

```java
@Bean
DefaultJmsListenerContainerFactory reliableJmsFactory(
        ConnectionFactory connectionFactory,
        MessageConverter messageConverter) {

    var factory = new DefaultJmsListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(messageConverter);
    factory.setSessionTransacted(true);
    factory.setConcurrency("3-10");
    factory.setRecoveryInterval(5_000L);
    return factory;
}

@JmsListener(
        destination = "orders.in",
        containerFactory = "reliableJmsFactory",
        concurrency = "5-12")
void handle(OrderPlaced order) {
}
```

The endpoint-level `concurrency` attribute overrides the concurrency configured by the selected factory for that listener. Other annotation attributes such as `selector` and `subscription` become endpoint-specific container configuration.

The factory is deliberately separate from the listener bean. That separation lets several listeners share one operational policy while allowing exceptional endpoints to choose a different factory. For example, a slow billing listener can use a factory with a smaller concurrency range and different transaction/recovery settings without changing unrelated listeners.

Do not treat the factory as the running consumer. It produces the actual container for each endpoint; lifecycle management of those created containers belongs to the endpoint registry.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-listener-endpoint-registry">JmsListenerEndpointRegistry and Endpoint Lifecycle</a>

<details>
<summary>Click for details</summary>

`JmsListenerEndpointRegistry` owns the `MessageListenerContainer` instances created for annotation-driven endpoints and coordinates their lifecycle with the application context. This is why an `@JmsListener` method can start consuming without application code calling `start()` manually.

The created listener containers are managed by the registry rather than registered as ordinary application-context beans. Consequently, injecting a particular generated container by type is not the management model. Give important endpoints stable ids and ask the registry for the container instead.

```java
@JmsListener(id = "order-consumer", destination = "orders.in")
void handle(OrderPlaced order) {
}

@Component
class JmsListenerControl {
    private final JmsListenerEndpointRegistry registry;

    JmsListenerControl(JmsListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    void pauseOrders() {
        MessageListenerContainer container =
                registry.getListenerContainer("order-consumer");
        if (container != null) {
            container.stop();
        }
    }
}
```

The registry implements Spring lifecycle contracts and propagates start/stop operations to its containers. This is useful for controlled operational actions such as pausing a consumer during maintenance, but application code should avoid repeatedly toggling consumers as a substitute for proper back-pressure, retry, or broker-side flow control.

Programmatically registered endpoints use the same registry. Therefore annotation-driven and programmatic listener definitions converge on the same runtime lifecycle model instead of forming two independent consumer systems.

**Pitfall:** the registry manages container lifecycle; it does not make arbitrary listener business objects lifecycle-safe. If listener code starts its own unmanaged threads or keeps mutable shared state, those concerns still belong to application design.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-listener-method-signature">@JmsListener Method Signatures</a>

<details>
<summary>Click for details</summary>

An `@JmsListener` method is adapted through Spring's messaging method-invocation infrastructure. The method can stay focused on business input instead of manually unpacking every `jakarta.jms.Message`, while still allowing raw JMS access when required.

Common argument shapes include:

- a converted payload such as `OrderPlaced` or `String`;
- `@Payload` for an explicit payload argument, including validation integration;
- `@Header` / `@Headers` for Spring Messaging headers, including JMS headers exposed through the mapping layer;
- `org.springframework.messaging.Message<T>` when payload and headers are both relevant;
- `jakarta.jms.Message` when provider/JMS-specific message details are required;
- `jakarta.jms.Session` when the listener needs the current JMS session, for example for a session-aware response.

```java
@JmsListener(destination = "orders.in")
void handle(
        OrderPlaced order,
        @Header(JmsHeaders.CORRELATION_ID) String correlationId,
        jakarta.jms.Message rawMessage) throws JMSException {

    log.info("order={}, correlation={}, redelivered={}",
            order.id(), correlationId, rawMessage.getJMSRedelivered());
}
```

Choose the narrowest signature that represents the learning/application need. A domain payload keeps business code independent from JMS. Adding the raw JMS message or `Session` is appropriate when JMS metadata or session-level behavior is genuinely part of the use case.

`@JmsListener` is repeatable, so one method can declare multiple listener endpoints. That creates multiple runtime containers/endpoints pointing at the same method; it does not merge destinations into one consumer.

Return values are meaningful: a non-void return can become a reply message. Reply destination selection is discussed separately because the incoming `JMSReplyTo`, `@SendTo`, and `JmsResponse` have different roles.

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-listener-conversion-validation">Payload Conversion, Headers, and Validation Integration</a>

<details>
<summary>Click for details</summary>

There are two related conversion steps around an annotated listener. First, Spring JMS converts the provider `jakarta.jms.Message` to an application object through the JMS `MessageConverter` configured on the listener container factory. Second, Spring's handler-method infrastructure resolves the converted message into method arguments and can apply validation to a payload parameter when a `Validator` has been configured for that handler-method layer.

For simple JMS message types, Spring's default converter can handle common text, byte, map, and serializable object cases. For JSON-oriented contracts, configure a converter such as `MappingJackson2MessageConverter` explicitly so the wire representation and target type policy are intentional.

```java
@Bean
MappingJackson2MessageConverter orderConverter() {
    var converter = new MappingJackson2MessageConverter();
    converter.setTargetType(MessageType.TEXT);
    converter.setTypeIdPropertyName("_type");
    return converter;
}

@JmsListener(destination = "orders.in")
void handle(@Valid @Payload OrderCommand command) {
}
```

`@Valid` marks the payload for validation, but pure Spring Framework does not make that validation effective merely because the annotation is present. The default `DefaultMessageHandlerMethodFactory` delegates validation to a no-op unless a `Validator` is supplied. Configure a `DefaultMessageHandlerMethodFactory` with the required Spring/Jakarta validation adapter and register it through `JmsListenerConfigurer` / `JmsListenerEndpointRegistrar#setMessageHandlerMethodFactory(...)` when listener payload validation is required.

Validation belongs after successful conversion: malformed JSON and an invalid domain object are different failures. A conversion failure means Spring could not create the requested payload; a validation failure means an object was created but violates the configured constraints. Keep those failure modes distinguishable in logs and tests.

Headers are not a replacement for payload schema. Use headers for metadata such as correlation, content type, routing hints, and tracing context. Keep domain state in the payload unless the external messaging contract explicitly defines otherwise.

**Pitfall:** a Java class name carried as a type-id header is part of the message contract. Treat type mappings and trusted input deliberately instead of assuming any producer may choose arbitrary application classes.

### References

- Spring Framework 6.1 API — `JmsListener` validation-capable payload signatures
- Spring Framework 6.1 API — `DefaultMessageHandlerMethodFactory#setValidator`
- Spring Framework 6.1 API — `EnableJms` handler-method factory customization

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-listener-replies">Reply Handling with JMSReplyTo, @SendTo, and JmsResponse</a>

<details>
<summary>Click for details</summary>

A listener return value can be converted and sent as a JMS reply. Destination selection follows the request/reply contract rather than being an arbitrary send after the listener returns.

Reply-destination precedence is explicit: a destination carried by `JmsResponse` wins first; otherwise Spring uses the incoming request's `JMSReplyTo`; only when neither is present does it fall back to the configured/default response destination such as `@SendTo`. In short: `JmsResponse destination > JMSReplyTo > @SendTo/default`. `@SendTo` is useful when the fallback reply destination is known declaratively, while `JmsResponse<T>` is useful when application logic must choose the destination at runtime.

```java
@JmsListener(destination = "quote.requests")
@SendTo("quote.replies")
Quote reply(QuoteRequest request) {
    return pricing.quote(request);
}
```

If the caller supplies `JMSReplyTo`, Spring can send the return value there instead of the static `@SendTo` destination. This lets the same listener support request-driven temporary reply destinations while still having a configured fallback.

For a dynamic destination, return `JmsResponse`:

```java
@JmsListener(destination = "routing.requests")
JmsResponse<Result> route(RoutingRequest request) {
    Result result = service.process(request);
    return request.priority()
            ? JmsResponse.forQueue(result, "priority.results")
            : JmsResponse.forQueue(result, "standard.results");
}
```

`JmsResponse` carries both the response value and the actual destination. It can target a queue name, topic name, or concrete `Destination`. If the destination is static, prefer `@SendTo`; the annotation makes the contract easier to discover and test.

Reply production still uses normal JMS concerns: conversion can fail, the send can participate in a local or external transaction, and observation of the reply send depends on the configured observation infrastructure. Do not interpret a successful listener method return as proof that the broker has durably committed the reply; the active transaction and provider delivery contract decide that.

### References

- Spring Framework 6.1 API — `JmsResponse`
- Spring Framework 6.1 API — `@JmsListener`

</details>

- [Back to top](#back-to-top)

---

## <a id="jms-programmatic-endpoints">Programmatic Endpoint Registration</a>

<details>
<summary>Click for details</summary>

Annotations are convenient when endpoint metadata is static and colocated with the handler method. Programmatic registration is useful when endpoints must be assembled from configuration, tenancy, deployment topology, or another runtime model that does not map cleanly to fixed annotations.

Implement `JmsListenerConfigurer` to access the `JmsListenerEndpointRegistrar`, then register a `JmsListenerEndpoint` with a chosen factory.

```java
@Configuration
@EnableJms
class DynamicJmsConfiguration implements JmsListenerConfigurer {

    private final JmsListenerContainerFactory<?> factory;

    DynamicJmsConfiguration(JmsListenerContainerFactory<?> factory) {
        this.factory = factory;
    }

    @Override
    public void configureJmsListeners(JmsListenerEndpointRegistrar registrar) {
        var endpoint = new SimpleJmsListenerEndpoint();
        endpoint.setId("audit-listener");
        endpoint.setDestination("audit.events");
        endpoint.setMessageListener(message -> handleAudit(message));
        registrar.registerEndpoint(endpoint, factory);
    }

    private void handleAudit(jakarta.jms.Message message) {
        // focused message handling
    }
}
```

`MethodJmsListenerEndpoint` is the model used for method-oriented endpoints; `SimpleJmsListenerEndpoint` accepts a JMS `MessageListener` directly. In both cases the endpoint still flows through a `JmsListenerContainerFactory` and `JmsListenerEndpointRegistry`, so transaction, recovery, lifecycle, and observation rules remain container rules.

Prefer annotations for ordinary application listeners because they expose the destination next to the handler and reuse Spring's method argument resolution. Choose programmatic registration when dynamic endpoint definition is itself a requirement, not merely to avoid using annotations.

**Design rule:** dynamic registration should not become broker administration. Creating queues/topics, retention policies, permissions, partitions, or provider topology remains provider-specific infrastructure outside Spring Framework JMS endpoint registration.

</details>

- [Back to top](#back-to-top)
